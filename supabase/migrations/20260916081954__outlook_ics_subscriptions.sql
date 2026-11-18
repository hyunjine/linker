-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260916081954, name outlook_ics_subscriptions).

-- Outlook 캘린더 ICS URL 구독 (#242).
create table public.outlook_ics_subscriptions (
    user_id uuid primary key references public.users(id) on delete cascade,
    ics_url text not null,
    last_synced_at timestamptz,
    last_error text,
    updated_at timestamptz default now()
);

alter table public.outlook_ics_subscriptions enable row level security;

create policy "select own"
    on public.outlook_ics_subscriptions for select
    using (auth.uid() = user_id);

create policy "insert own"
    on public.outlook_ics_subscriptions for insert
    with check (auth.uid() = user_id);

create policy "update own"
    on public.outlook_ics_subscriptions for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

create policy "delete own"
    on public.outlook_ics_subscriptions for delete
    using (auth.uid() = user_id);

comment on table public.outlook_ics_subscriptions is
    'Outlook 캘린더 ICS URL 구독 (#242). 유저당 1개. edge function 이 주기적으로 fetch → mirror.';
