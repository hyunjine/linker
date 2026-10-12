-- Outlook 재택 미러는 파트너 푸시 알림 대상에서 제외 (#242).
--
-- Outlook 연동 시 회사 캘린더의 재택 일정이 Graph 동기화로 schedules (source='outlook') 에 INSERT ·
-- UPDATE · DELETE 된다. 첫 연결엔 수개월치가 한 번에 들어와 파트너에게 푸시가 몰리고, 이후에도
-- 앱을 열 때마다 동기화 결과가 알림으로 가는 건 의도가 아니라서 #342 의 시스템 관리 로우
-- (dday_milestone · birthday) 와 같은 방식으로 세 트리거 모두 스킵한다. 재택 일정은 캘린더에
-- 조용히 나타나기만 하고, 시작 알림 (pg_cron start_reminder) 은 그대로 동작.
--
-- 함수 본문은 20260922030000__schedules_notify_skip_system_managed.sql 과 동일 + outlook 조건만 추가.

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
           -- 시스템 관리 로우 (#342): dday milestone · birthday 자동 등록은 파트너 알림 스킵.
           -- Outlook 재택 미러 (#242) 도 동기화로 들어오는 로우라 스킵.
           AND source IS DISTINCT FROM 'dday_milestone'
           AND source IS DISTINCT FROM 'outlook'
           AND birthday_uid IS NULL
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
    IF old.is_private THEN
        RETURN null;
    END IF;
    -- 시스템 관리 로우 (#342): dday milestone · birthday 자동 등록 삭제는 파트너에게 알림 안 감.
    -- Outlook 재택 미러 (#242) 도 동기화 · 연결 해제로 지워지므로 스킵.
    IF old.source IN ('dday_milestone', 'outlook') OR old.birthday_uid IS NOT NULL THEN
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
    IF new.is_private THEN
        RETURN null;
    END IF;
    -- 시스템 관리 로우 (#342): dday milestone · birthday 자동 등록 변경은 파트너에게 알림 안 감.
    -- Outlook 재택 미러 (#242) 도 동기화로 바뀌므로 스킵.
    IF new.source IN ('dday_milestone', 'outlook') OR new.birthday_uid IS NOT NULL THEN
        RETURN null;
    END IF;

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
