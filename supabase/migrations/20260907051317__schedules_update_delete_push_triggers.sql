-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260907051317, name schedules_update_delete_push_triggers).

CREATE OR REPLACE FUNCTION public.tg_schedules_notify_update()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    fn_url  text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    payload jsonb;
    content_changed boolean;
BEGIN
    IF new.is_private THEN
        RETURN null;
    END IF;

    content_changed :=
        old.title       IS DISTINCT FROM new.title       OR
        old.start_date  IS DISTINCT FROM new.start_date  OR
        old.end_date    IS DISTINCT FROM new.end_date    OR
        old.start_time  IS DISTINCT FROM new.start_time  OR
        old.end_time    IS DISTINCT FROM new.end_time    OR
        old.all_day     IS DISTINCT FROM new.all_day     OR
        old.type        IS DISTINCT FROM new.type;

    IF NOT content_changed AND old.is_private = false THEN
        RETURN null;
    END IF;

    payload := jsonb_build_object(
        'type', 'UPDATE',
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

    PERFORM net.http_post(
        url     := fn_url,
        body    := payload,
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
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
    fn_url  text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    payload jsonb;
BEGIN
    IF old.is_private THEN
        RETURN null;
    END IF;

    payload := jsonb_build_object(
        'type', 'DELETE',
        'table', 'schedules',
        'record', jsonb_build_object(
            'id',          old.id,
            'couple_id',   old.couple_id,
            'created_by',  old.created_by,
            'title',       old.title,
            'start_date',  old.start_date,
            'type',        old.type
        )
    );

    PERFORM net.http_post(
        url     := fn_url,
        body    := payload,
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 5000
    );
    RETURN null;
END;
$$;

DROP TRIGGER IF EXISTS tg_schedules_notify_update ON public.schedules;
CREATE TRIGGER tg_schedules_notify_update
    AFTER UPDATE ON public.schedules
    FOR EACH ROW EXECUTE PROCEDURE public.tg_schedules_notify_update();

DROP TRIGGER IF EXISTS tg_schedules_notify_delete ON public.schedules;
CREATE TRIGGER tg_schedules_notify_delete
    AFTER DELETE ON public.schedules
    FOR EACH ROW EXECUTE PROCEDURE public.tg_schedules_notify_delete();
