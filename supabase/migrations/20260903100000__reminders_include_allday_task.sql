-- 시간 있는 일정 외에도 종일 일정 · 할 일 을 매일 KST 09:00 에 알림 발송.
--
-- 확장 방식: 기존 send_schedule_start_reminders() 를 CREATE OR REPLACE.
--  - 시간 있는 일정: 기존 그대로 (매 분 매칭)
--  - 종일 일정 · 할 일: current_hhmm = '09:00:00' 일 때 별도 SELECT 로 이번 분에 발송
--
-- 09:00 은 카카오/구글 캘린더 관습에 맞춘 하드코딩 기본값. 유저별 커스터마이즈는 후속 이슈에서
-- user_preferences 에 daily_reminder_time TIME 을 두고 per-user 계산하도록 확장.
--
-- payload 에 all_day 를 함께 실어 edge function 이 알림 본문을 케이스별로 다르게 쓸 수 있게 함.

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

    -- (1) 시간 있는 일정: start_time 이 이번 분과 일치.
    for r in
        select id, couple_id, created_by, title, start_date, type, start_time,
               all_day, is_private, owner_kind
          from public.schedules
         where type = 'schedule'
           and all_day = false
           and start_time is not null
           and start_date = today_kst
           and date_trunc('minute', start_time)::time = current_hhmm
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

    -- (2) 종일 일정 · 할 일: 매일 KST 09:00 에 발송. 이번 bucket 이 09:00 일 때만.
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
