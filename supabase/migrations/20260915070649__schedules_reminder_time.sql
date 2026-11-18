-- 종일 일정 · 할 일의 알림 시각 컬럼 추가 (#251 2차).
--
-- 하드코딩 09:00 → 사용자별 · 스케줄별 커스터마이즈. TIME 컬럼 (HH:MM:SS) 로 저장.
-- Kotlin 은 "HH:MM" 문자열로 다루고 Repository 가 저장/조회 시 :SS 를 붙임/떼어냄.
-- 시간 있는 일정에는 무시 (reminder_minutes_before 사용).

alter table public.schedules
    add column if not exists reminder_time time not null default '09:00:00';

-- send_schedule_start_reminders REPLACE — 종일 · 할 일 매칭에 per-row reminder_time 반영.
-- 매 분 (pg_cron 최소 단위) 도는 함수라 09:00 이 아닌 모든 분에도 매칭될 수 있게 조건 변경.
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

    -- (1) 시간 있는 일정: start_time - offset 이 이번 분과 일치. offset=0 (정각) 이면 start_time 자체.
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

    -- (2) 종일 일정 · 할 일: reminder_time 이 이번 분과 일치.
    for r in
        select id, couple_id, created_by, title, start_date, type, start_time,
               all_day, is_private, owner_kind, reminder_time
          from public.schedules
         where start_date = today_kst
           and (type = 'task' or (type = 'schedule' and all_day = true))
           and reminder_time = current_hhmm
    loop
        payload := jsonb_build_object(
            'type', 'START_REMINDER',
            'table', 'schedules',
            'record', jsonb_build_object(
                'id',           r.id,
                'couple_id',    r.couple_id,
                'created_by',   r.created_by,
                'title',        r.title,
                'start_date',   r.start_date,
                'type',         r.type,
                'start_time',   r.start_time,
                'all_day',      r.all_day,
                'is_private',   r.is_private,
                'owner_kind',   r.owner_kind,
                'reminder_time', r.reminder_time
            )
        );
        perform net.http_post(
            url     := fn_url,
            body    := payload,
            headers := jsonb_build_object('Content-Type', 'application/json'),
            timeout_milliseconds := 5000
        );
    end loop;
end;
$$;
