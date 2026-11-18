-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260910053654, name add_schedule_source_and_external_id).

create type public.schedule_source as enum ('internal', 'outlook');

alter table public.schedules
  add column source public.schedule_source not null default 'internal',
  add column external_id text null;

comment on column public.schedules.source is
  '스케줄 origin. internal=앱에서 직접 생성, outlook=Microsoft Graph mirror. Google 등 추가 시 enum 확장.';
comment on column public.schedules.external_id is
  '외부 provider 의 이벤트 id (예: Graph event.id). source=internal 이면 NULL. mirror upsert 시 (created_by, external_id) 로 매칭.';

create unique index if not exists schedules_outlook_external_uniq
  on public.schedules(created_by, external_id)
  where source = 'outlook' and external_id is not null;

create index if not exists schedules_source_idx on public.schedules(source);
