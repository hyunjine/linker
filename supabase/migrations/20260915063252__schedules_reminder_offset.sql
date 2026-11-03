-- 스케줄 시작 시각 대비 알림 offset (분) 컬럼 추가 (#251).
--
-- 시간 있는 일정 (`type = 'schedule' AND all_day = false AND start_time IS NOT NULL`) 에만 의미.
-- pg_cron 은 `start_time - (reminder_minutes_before * interval '1 minute')` 시각에 매칭.
-- 종일 · 할 일은 기존 09:00 KST 발송 로직을 유지 (offset 무시).
--
-- Kotlin 쪽 enum (ReminderOffset) 이 정의하는 유효 값: 0 (정각) · 5 · 10 · 15 · 30 · 60.
-- DB CHECK 는 걸지 않음 — 앱 로직에서 검증하고, DB 는 미래 옵션 확장에 열려있는 상태로 둠.

alter table public.schedules
    add column if not exists reminder_minutes_before integer not null default 5;

-- 기존 send_schedule_start_reminders 를 REPLACE — 시간 있는 일정 매칭 조건에 offset 반영.
-- 종일 · 할 일은 여전히 09:00 (offset 미적용).
create or replace function public.send_schedule_start_reminders()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    r record;
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    payload jsonb;
    now_bucket timestamp;
    today_kst date;
    current_hhmm time;
begin
    now_bucket := date_trunc('minute', (now() at time zone 'Asia/Seoul'));
    today_kst := now_bucket::date;
    current_hhmm := now_bucket::time;

    -- (1) 시간 있는 일정: (start_time - reminder_minutes_before * 1 minute) 이 이번 분과 일치.
    -- reminder_minutes_before = 0 (정각) 이면 start_time 자체와 매칭.
    for r in
        select id, couple_id, created_by, title, start_date, type, start_time,
               all_day, is_private, owner_kind, reminder_minutes_before
          from public.schedules
         where type = 'schedule'
           and all_day = false
           and start_time is not null
           and start_date = today_kst
           and date_trunc(
                   'minute',
                   start_time - (reminder_minutes_before * interval '1 minute')
               )::time = current_hhmm
    loop
        payload := jsonb_build_object(
            'type', 'START_REMINDER',
            'table', 'schedules',
            'record', jsonb_build_object(
                'id',                        r.id,
                'couple_id',                 r.couple_id,
                'created_by',                r.created_by,
                'title',                     r.title,
                'start_date',                r.start_date,
                'type',                      r.type,
                'start_time',                r.start_time,
                'all_day',                   r.all_day,
                'is_private',                r.is_private,
                'owner_kind',                r.owner_kind,
                'reminder_minutes_before',   r.reminder_minutes_before
            )
        );
        perform net.http_post(
            url     := fn_url,
            body    := payload,
            headers := jsonb_build_object('Content-Type', 'application/json'),
            timeout_milliseconds := 5000
        );
    end loop;

    -- (2) 종일 일정 · 할 일: 매일 KST 09:00 에 발송 (offset 미적용).
    if current_hhmm = time '09:00:00' then
        for r in
            select id, couple_id, created_by, title, start_date, type, start_time,
                   all_day, is_private, owner_kind
              from public.schedules
             where start_date = today_kst
               and (type = 'task' or (type = 'schedule' and all_day = true))
        loop
            payload := jsonb_build_object(
                'type', 'START_REMINDER',
                'table', 'schedules',
                'record', jsonb_build_object(
                    'id',          r.id,
                    'couple_id',   r.couple_id,
                    'created_by',  r.created_by,
                    'title',       r.title,
                    'start_date',  r.start_date,
                    'type',        r.type,
                    'start_time',  r.start_time,
                    'all_day',     r.all_day,
                    'is_private',  r.is_private,
                    'owner_kind',  r.owner_kind
                )
            );
            perform net.http_post(
                url     := fn_url,
                body    := payload,
                headers := jsonb_build_object('Content-Type', 'application/json'),
                timeout_milliseconds := 5000
            );
        end loop;
    end if;
end;
$$;
