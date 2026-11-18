-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260831051552, name fix_schedules_push_trigger_net_schema).

-- 이전 마이그레이션에서 잘못 참조한 extensions.http_post → net.http_post 로 수정.
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
