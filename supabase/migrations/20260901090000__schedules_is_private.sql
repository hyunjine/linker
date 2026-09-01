-- 비공개 (나만 보이는) 일정 · 할일 지원.
-- is_private = true 인 row 는 파트너에게 SELECT 자체가 안 되도록 RLS 에서 창발적으로 감춘다.
-- 정책 · edge-function trigger 도 함께 갱신해 push 알림도 파트너에게 새어나가지 않게 한다.

ALTER TABLE public.schedules
    ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE;

-- schedules RLS: 커플 소속 + (공개 or 내가 만든 것). UPDATE / DELETE 도 USING 을 타므로
-- 파트너가 상대의 비공개 row 를 수정 · 삭제하는 것도 자동으로 차단된다.
-- WITH CHECK 는 기존과 동일 (created_by = auth.uid()) — 새 비공개 row 는 만든 사람만 저장 가능.
DROP POLICY IF EXISTS schedules_all_in_my_couple ON public.schedules;
CREATE POLICY schedules_all_in_my_couple ON public.schedules
    FOR ALL TO authenticated
    USING (
        couple_id IN (SELECT public.my_couple_ids())
        AND (NOT is_private OR created_by = auth.uid())
    )
    WITH CHECK (
        couple_id IN (SELECT public.my_couple_ids())
        AND created_by = auth.uid()
    );

-- schedule_repeat_rules 도 부모 schedule 이 파트너의 비공개인 경우 조회 · 수정 차단.
DROP POLICY IF EXISTS repeat_rules_all_in_my_couple ON public.schedule_repeat_rules;
CREATE POLICY repeat_rules_all_in_my_couple ON public.schedule_repeat_rules
    FOR ALL TO authenticated
    USING (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT public.my_couple_ids())
              AND (NOT s.is_private OR s.created_by = auth.uid())
        )
    )
    WITH CHECK (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT public.my_couple_ids())
              AND (NOT s.is_private OR s.created_by = auth.uid())
        )
    );

-- Push trigger: 비공개 스케줄은 파트너에게 알림 자체를 보내지 않는다.
-- (edge function 이 파트너 device 만 골라 보내지만, 애초에 request 를 생략해 IO · 로그 노이즈 감소.)
CREATE OR REPLACE FUNCTION public.tg_schedules_notify_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url  text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    payload jsonb;
BEGIN
    IF new.is_private THEN
        RETURN null;
    END IF;

    payload := jsonb_build_object(
        'type', 'INSERT',
        'table', 'schedules',
        'record', jsonb_build_object(
            'id',          new.id,
            'couple_id',   new.couple_id,
            'created_by',  new.created_by,
            'title',       new.title,
            'start_date',  new.start_date,
            'type',        new.type
        )
    );

    perform net.http_post(
        url     := fn_url,
        body    := payload,
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
    RETURN null;
END;
$$;
