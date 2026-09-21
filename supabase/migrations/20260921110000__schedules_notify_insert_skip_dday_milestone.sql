-- 디데이 milestone 자동 반영 시 알림 폭탄 방지 (#329).
--
-- DdayViewModel.saveAnchor 가 milestone 30개 (100·200·...·10000일 + 1~30주년) 을 한번에
-- INSERT 하면 tg_schedules_notify_insert 가 각각 push 알림을 fire → 파트너 폰이 알림 30개
-- 로 도배됨. source='dday_milestone' 태그된 row 는 INSERT 알림 전송을 스킵하도록 필터 추가.
--
-- 별도 이벤트 (milestone 자체가 도래하는 순간 = start_reminder) 는 pg_cron 이 여전히 처리해
-- 사용자는 그날 09:00 KST 에 알림 1회 수신 (all_day + reminder_time 09:00 기본값 적용).

CREATE OR REPLACE FUNCTION public.tg_schedules_notify_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    actor uuid := auth.uid();
    r record;
BEGIN
    FOR r IN
        SELECT DISTINCT ON (COALESCE(series_id::text, id::text))
               id, couple_id, created_by, title, start_date, type, series_id,
               owner_kind, is_private
          FROM new_rows
         WHERE is_private = false
           -- #329: 디데이 milestone 자동 반영 row 는 INSERT 알림 스킵 (start_reminder 는 정상 fire).
           AND source IS DISTINCT FROM 'dday_milestone'
         ORDER BY COALESCE(series_id::text, id::text), start_date ASC, id ASC
    LOOP
        PERFORM net.http_post(
            url     := fn_url,
            body    := jsonb_build_object(
                'type', 'INSERT',
                'table', 'schedules',
                'record', jsonb_build_object(
                    'id',          r.id,
                    'couple_id',   r.couple_id,
                    'created_by',  r.created_by,
                    'actor_id',    actor,
                    'title',       r.title,
                    'start_date',  r.start_date,
                    'type',        r.type,
                    'series_id',   r.series_id,
                    'owner_kind',  r.owner_kind,
                    'is_private',  r.is_private
                )
            ),
            headers := jsonb_build_object('Content-Type', 'application/json'),
            timeout_milliseconds := 5000
        );
    END LOOP;
    RETURN null;
END;
$$;
