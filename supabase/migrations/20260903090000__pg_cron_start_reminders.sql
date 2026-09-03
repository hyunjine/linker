-- 스케줄 시작 시각 (분 단위) 도래 시 FCM 알림 발송.
--
-- 접근:
--   1) pg_cron 매 분 실행 → 이번 분 (kst) 에 시작하는 시간 있는 스케줄을 찾음
--   2) 각 스케줄에 대해 pg_net 으로 send-schedule-push edge function 을 호출
--      (type='START_REMINDER' 로 구분 — 기존 INSERT push 와 다른 발송 경로)
--
-- TZ: 이 앱은 한국 사용자만 대상 (MVP). now() 는 UTC 로 오므로 Asia/Seoul 로 변환해서 비교.
-- 다국가 확장 시 users.timezone 컬럼 추가 후 per-user 계산으로 리팩터 필요.
--
-- 중복 발송 방지: date_trunc('minute', ...) 로 minute-bucket 매칭 → cron 이 몇 초 늦게 fire 해도
-- 같은 분 안에 있으면 정확히 1회만 매칭. cron 이 아예 한 분을 건너뛰면 그 분 알림은 유실 —
-- 실무 관측 후 필요하면 last_fired_at 컬럼으로 catch-up 로직 추가.

create extension if not exists pg_cron;

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
end;
$$;

-- pg_cron 은 매 분 fire 가 최소 단위. `* * * * *` = every minute.
-- schedule 이 이미 있으면 unschedule 후 재등록 (idempotent).
do $$
declare
    v_jobid bigint;
begin
    select jobid into v_jobid from cron.job where jobname = 'send-start-reminders';
    if v_jobid is not null then
        perform cron.unschedule(v_jobid);
    end if;
    perform cron.schedule(
        'send-start-reminders',
        '* * * * *',
        'select public.send_schedule_start_reminders()'
    );
end $$;
