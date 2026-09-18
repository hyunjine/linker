-- 스케줄 푸시 알림 정책 v2 (#301).
--
-- 정책 (사용자 확정):
--   내 알림 (creator=me): 개인 일정 생성/수정/삭제 X · 시각 리마인더 O
--   상대방 알림 (partner):  개인 일정 생성/수정/삭제 O · 비공개면 X · 시각 리마인더 X
--   공동 일정 (owner_kind='us'): 생성/수정/삭제 + 시각 리마인더 모두 양쪽 다.
--                                 단 action 발생시킨 actor 는 자기 액션을 다시 받지 않음.
--   partner UI 상 생성 불가 → owner_kind='partner' 신규 저장 케이스 없음 (기존 row 만 유지).
--
-- 이 마이그레이션이 하는 것:
--   1. `owner_kind='us' AND is_private=true` 조합을 DB CHECK 로 거부 (defense-in-depth).
--   2. INSERT/UPDATE/DELETE 트리거 payload 에 `actor_id` (auth.uid()), `owner_kind`,
--      `is_private` 를 실어 edge function 이 정책대로 필터할 수 있게 함.
--   3. INSERT/UPDATE/DELETE 트리거에 `is_private` 조기 반환 유지 · 강화 (HTTP call 자체 스킵).
--   4. UPDATE/DELETE 트리거는 로컬 마이그레이션엔 없고 prod 에만 있던 상태였음 (Studio 로 만든 흔적).
--      이번에 재등록해 로컬-원격 정합을 맞춤. STATEMENT-level dedupe 는 INSERT 만 필요 → 그대로.

-- ============================================================================
-- 1. CHECK constraint: us + private 금지
-- ============================================================================
ALTER TABLE public.schedules DROP CONSTRAINT IF EXISTS schedules_us_not_private;
ALTER TABLE public.schedules
    ADD CONSTRAINT schedules_us_not_private
    CHECK (NOT (owner_kind = 'us' AND is_private = true));

-- ============================================================================
-- 2. INSERT trigger — statement-level (series dedup 유지)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.tg_schedules_notify_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    -- INSERT 는 RLS WITH CHECK (created_by = auth.uid()) 로 actor = creator 강제.
    -- 다만 방어적으로 auth.uid() 를 직접 payload 에 실어 edge function 이 exclusion 판단에 사용.
    actor uuid := auth.uid();
    r record;
BEGIN
    FOR r IN
        -- 시리즈 (series_id 있음) 는 그룹당 최초 (start_date, id) 1건만.
        -- Standalone (series_id NULL) 은 각 row 가 자기 자신을 대표 → 그대로 개별 발송.
        -- 비공개 개인 일정은 누구도 알림 안 받음 → HTTP call 자체 스킵.
        SELECT DISTINCT ON (COALESCE(series_id::text, id::text))
               id, couple_id, created_by, title, start_date, type, series_id,
               owner_kind, is_private
          FROM new_rows
         WHERE is_private = false
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

-- (INSERT 트리거 바인딩은 #263 에서 statement-level 로 이미 등록됨 → 재바인딩 불필요.)

-- ============================================================================
-- 3. UPDATE trigger — row-level, 컨텐츠 변경 시만 발송
-- ============================================================================
CREATE OR REPLACE FUNCTION public.tg_schedules_notify_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    actor uuid := auth.uid();
    content_changed boolean;
BEGIN
    -- 비공개 로 바뀐 상태면 어차피 아무도 알림 대상 아님 → 스킵.
    IF new.is_private THEN
        RETURN null;
    END IF;

    -- 컨텐츠 필드가 실제로 바뀌었을 때만 발송 (동일 값 UPDATE 는 무시).
    -- owner_kind · is_private 자체 변경도 "일정이 달라졌다" 로 취급.
    content_changed :=
        old.title       IS DISTINCT FROM new.title       OR
        old.start_date  IS DISTINCT FROM new.start_date  OR
        old.end_date    IS DISTINCT FROM new.end_date    OR
        old.start_time  IS DISTINCT FROM new.start_time  OR
        old.end_time    IS DISTINCT FROM new.end_time    OR
        old.all_day     IS DISTINCT FROM new.all_day     OR
        old.type        IS DISTINCT FROM new.type        OR
        old.owner_kind  IS DISTINCT FROM new.owner_kind  OR
        old.is_private  IS DISTINCT FROM new.is_private;

    IF NOT content_changed THEN
        RETURN null;
    END IF;

    PERFORM net.http_post(
        url     := fn_url,
        body    := jsonb_build_object(
            'type', 'UPDATE',
            'table', 'schedules',
            'record', jsonb_build_object(
                'id',          new.id,
                'couple_id',   new.couple_id,
                'created_by',  new.created_by,
                'actor_id',    actor,
                'title',       new.title,
                'start_date',  new.start_date,
                'type',        new.type,
                'owner_kind',  new.owner_kind,
                'is_private',  new.is_private
            )
        ),
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
    RETURN null;
END;
$$;

DROP TRIGGER IF EXISTS tg_schedules_notify_update ON public.schedules;
CREATE TRIGGER tg_schedules_notify_update
    AFTER UPDATE ON public.schedules
    FOR EACH ROW EXECUTE FUNCTION public.tg_schedules_notify_update();

-- ============================================================================
-- 4. DELETE trigger — row-level
-- ============================================================================
CREATE OR REPLACE FUNCTION public.tg_schedules_notify_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    actor uuid := auth.uid();
BEGIN
    -- 비공개였다면 애초에 파트너는 못 봤음 → 삭제 알림도 의미 없음.
    IF old.is_private THEN
        RETURN null;
    END IF;

    PERFORM net.http_post(
        url     := fn_url,
        body    := jsonb_build_object(
            'type', 'DELETE',
            'table', 'schedules',
            'record', jsonb_build_object(
                'id',          old.id,
                'couple_id',   old.couple_id,
                'created_by',  old.created_by,
                'actor_id',    actor,
                'title',       old.title,
                'start_date',  old.start_date,
                'type',        old.type,
                'owner_kind',  old.owner_kind,
                'is_private',  old.is_private
            )
        ),
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
    RETURN null;
END;
$$;

DROP TRIGGER IF EXISTS tg_schedules_notify_delete ON public.schedules;
CREATE TRIGGER tg_schedules_notify_delete
    AFTER DELETE ON public.schedules
    FOR EACH ROW EXECUTE FUNCTION public.tg_schedules_notify_delete();
