-- 30분마다 send-outlook-sync-push edge function 호출 (#420).
--
-- 함수가 Outlook 연동 유저 (users.outlook_connected_at IS NOT NULL) 기기에 silent / data push 를 보내고,
-- 앱이 깨어나 기존 Outlook 재택 sync 를 실행한다. 서버는 Outlook 을 직접 읽지 않는다
-- (delegated 권한으로 공유 캘린더 변경 알림 구독 불가 · application 권한 없음).
--
-- 야간 제외: KST 01:00 ~ 05:59 는 보내지 않는다. 재택 일정이 바뀔 일이 거의 없는 시간에 기기를
--   깨우면 배터리만 쓰고, iOS 는 쓸모없는 silent push 가 쌓이면 이후 push 를 throttle 한다.
--   pg_cron 은 UTC. KST = UTC+9 →
--     KST 06:00 ~ 08:30 = UTC 21 ~ 23시
--     KST 09:00 ~ 00:30 = UTC 00 ~ 15시
--   → '*/30 0-15,21-23 * * *' (KST 06:00 첫 실행, 00:30 마지막 실행).
--
-- 인증: 함수는 verify_jwt = false 이고 x-outlook-sync-secret 헤더로 검증한다. 헤더 값은 Vault 의
--   `outlook_sync_push_secret` 에서 읽는다 (마이그레이션에 secret 을 넣지 않기 위해). 적용 전 1회:
--     select vault.create_secret('<OUTLOOK_SYNC_PUSH_SECRET 과 같은 값>', 'outlook_sync_push_secret');
--   Vault 에 없으면 호출하지 않고 경고만 남긴다 (401 을 30분마다 쌓지 않도록).

create extension if not exists pg_cron;

create or replace function public.send_outlook_sync_push()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-outlook-sync-push';
    v_secret text;
begin
    select decrypted_secret into v_secret
      from vault.decrypted_secrets
     where name = 'outlook_sync_push_secret'
     limit 1;
    if v_secret is null or v_secret = '' then
        raise warning 'send_outlook_sync_push: vault secret outlook_sync_push_secret 없음 — skip';
        return;
    end if;

    perform net.http_post(
        url     := fn_url,
        body    := '{}'::jsonb,
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-outlook-sync-secret', v_secret
        ),
        timeout_milliseconds := 10000
    );
end;
$$;

-- security definer 라 Vault 를 읽을 수 있으므로 API 역할 (anon · authenticated) 에서는 실행 못 하게.
revoke all on function public.send_outlook_sync_push() from public;
revoke all on function public.send_outlook_sync_push() from anon, authenticated;

-- schedule 이 이미 있으면 unschedule 후 재등록 (idempotent).
do $$
declare
    v_jobid bigint;
begin
    select jobid into v_jobid from cron.job where jobname = 'send-outlook-sync-push';
    if v_jobid is not null then
        perform cron.unschedule(v_jobid);
    end if;
    perform cron.schedule(
        'send-outlook-sync-push',
        '*/30 0-15,21-23 * * *',
        'select public.send_outlook_sync_push()'
    );
end $$;
