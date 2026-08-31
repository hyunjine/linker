-- schedules INSERT 시 send-schedule-push edge function 을 호출해 파트너에게 FCM 발송.
-- pg_net.net.http_post 로 비동기 POST. edge function 은 verify_jwt=false 로 배포 (config.toml).
-- payload 자체는 민감 데이터 없고, 실제 device token 조회는 함수 내부에서 service_role 로.
--
-- URL 은 Supabase Project URL 기반. 프로젝트 이관 시 이 값만 갱신.

create extension if not exists pg_net with schema extensions;

create or replace function public.tg_schedules_notify_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
  payload jsonb;
begin
  payload := jsonb_build_object(
    'type', 'INSERT',
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

  perform net.http_post(
    url     := fn_url,
    body    := payload,
    headers := jsonb_build_object('Content-Type', 'application/json'),
    timeout_milliseconds := 5000
  );
  return null;
end;
$$;

drop trigger if exists tg_schedules_notify_insert on public.schedules;
create trigger tg_schedules_notify_insert
  after insert on public.schedules
  for each row execute procedure public.tg_schedules_notify_insert();
