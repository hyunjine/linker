-- Outlook 캘린더 ICS URL 구독 (#242).
--
-- 사용자가 OWA (Outlook Web) 에서 "캘린더 게시 → ICS URL" 로 발급한 공개 URL 을 저장.
-- pg_cron 이 주기적으로 sync-outlook-ics edge function 을 호출 → 각 subscription URL 을 fetch
-- 하고 ICS 파싱해서 schedules 테이블에 미러 (source='outlook', external_id=VEVENT UID).
--
-- 읽기 전용 · 30초~30분 지연 감수. 회사 관리자 승인 필요 없이 사용자 셀프 서비스.
create table public.outlook_ics_subscriptions (
    user_id uuid primary key references public.users(id) on delete cascade,
    ics_url text not null,
    last_synced_at timestamptz,
    last_error text,
    updated_at timestamptz default now()
);

alter table public.outlook_ics_subscriptions enable row level security;

-- 본인 것만 select · upsert · delete. 다른 유저의 ICS URL 은 접근 불가.
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
