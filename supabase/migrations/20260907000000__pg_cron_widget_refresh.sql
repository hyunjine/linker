-- 매일 KST 00:00 에 send-widget-refresh edge function 을 호출해 활성 iOS 디바이스에
-- Silent Push 를 보낸다. 앱이 백그라운드에서 receive → WidgetSync.refresh() 로 위젯
-- payload 갱신 → 사용자가 앱을 켜지 않아도 위젯이 다음날 데이터로 자동 전환.
--
-- pg_cron 스케줄은 UTC. KST 00:00 = UTC 15:00.
-- pg_net 은 이미 send_schedule_start_reminders 에서도 사용 중이라 별도 install 불필요.

create extension if not exists pg_cron;

create or replace function public.send_widget_refresh()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-widget-refresh';
begin
    perform net.http_post(
        url     := fn_url,
        body    := '{}'::jsonb,
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 10000
    );
end;
$$;

-- schedule 이 이미 있으면 unschedule 후 재등록 (idempotent).
do $$
declare
    v_jobid bigint;
begin
    select jobid into v_jobid from cron.job where jobname = 'send-widget-refresh-daily';
    if v_jobid is not null then
        perform cron.unschedule(v_jobid);
    end if;
    -- '0 15 * * *' = 매일 UTC 15:00 = KST 00:00.
    perform cron.schedule(
        'send-widget-refresh-daily',
        '0 15 * * *',
        'select public.send_widget_refresh()'
    );
end $$;
