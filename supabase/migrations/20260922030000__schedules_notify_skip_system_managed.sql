-- 시스템 자동 관리 스케줄은 파트너 푸시 알림 대상에서 제외 (#342).
--
-- 프로필 편집에서 닉네임/생일을 바꾸면 `sync_birthday_schedule` 이 기존 생일 로우를
-- DELETE 후 50개 새 로우 INSERT → 각 로우마다 notify_delete · notify_insert 가 fire →
-- 파트너 폰에 "○○ 님이 반복 일정을 설정했어요/삭제했어요 · 생일" 이 수십 개 폭탄.
-- 디데이 milestone (source='dday_milestone') 도 anchor 저장 시 30개 로우가 replace 되어
-- 같은 종류의 폭탄이 나올 수 있는데, INSERT 만 스킵 로직이 있고 (#329) DELETE/UPDATE 는 없음.
--
-- 세 트리거 모두 두 조건 (source='dday_milestone' OR birthday_uid IS NOT NULL) 을 스킵:
-- 이 로우들은 사용자가 만든 일정이 아니라 서버가 자동 재생성하는 시스템 이벤트이므로
-- 파트너에게 개별 변경 알림이 갈 이유 자체가 없음. 실제 이벤트 도래 시 알림은
-- pg_cron start_reminder 가 별도로 처리.

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
           AND source IS DISTINCT FROM 'dday_milestone'
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
    IF old.source = 'dday_milestone' OR old.birthday_uid IS NOT NULL THEN
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
    IF new.source = 'dday_milestone' OR new.birthday_uid IS NOT NULL THEN
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
