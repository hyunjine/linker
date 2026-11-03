-- 알림 내역 (#303).
--
-- 목적:
--   - 푸시 알림은 발송만 하고 기록이 없어 사용자가 지난 알림을 다시 볼 수 없었다.
--   - 푸시를 보내는 edge function (send-schedule-push · send-announcement · broadcast-release-note)
--     이 수신자 (user) 단위로 이 테이블에 한 줄씩 남기고, 앱의 알림 내역 화면이 읽는다.
--   - device 가 아닌 user 단위 — 푸시 토큰이 없는 사용자도 내역은 쌓인다.
--
-- 접근 정책:
--   - 쓰기는 edge function 의 SERVICE_ROLE 만 (RLS 우회). authenticated 는 INSERT/UPDATE/DELETE 불가.
--   - 읽기는 본인 row 만 (`user_id = auth.uid()`).
--
-- 보관: 30일. 앱은 30일 이내만 조회하고, 매일 pg_cron 이 그보다 오래된 row 를 지운다.

create table if not exists public.notifications (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references public.users (id) on delete cascade,
    kind        text not null check (kind in ('partner', 'reminder', 'announcement', 'update')),
    title       text not null,
    body        text not null,
    -- partner · reminder 알림의 원본 스케줄. 스케줄이 지워져도 내역은 남아야 하므로 FK 없음.
    schedule_id uuid,
    -- 같은 알림이 재시도로 두 번 기록되지 않게 하는 키 (예: 'release:1.5.0'). NULL 이면 중복 체크 안 함.
    dedupe_key  text,
    created_at  timestamptz not null default now()
);

comment on table  public.notifications is '사용자별 푸시 알림 내역 (#303). 30일 보관.';
comment on column public.notifications.kind is
'partner (상대방 일정·할 일 변경) · reminder (시작 리마인더) · announcement (관리자 공지) · update (새 버전 안내).';
comment on column public.notifications.dedupe_key is
'재시도 중복 방지 키. (user_id, dedupe_key) unique — NULL 끼리는 중복으로 보지 않음.';

-- 알림 내역 화면 조회: 내 알림을 최신순으로.
create index if not exists ix_notifications_user_created
    on public.notifications (user_id, created_at desc);

-- 보관 기간 정리용.
create index if not exists ix_notifications_created
    on public.notifications (created_at);

-- partial index (where dedupe_key is not null) 로 두면 PostgREST upsert 의 ON CONFLICT 가 인덱스를
-- 추론하지 못해 42P10. 일반 unique 로 두되 NULL 끼리는 서로 다른 값이라 dedupe_key 없는 row 는 제약 무관.
alter table public.notifications drop constraint if exists notifications_user_dedupe_key;
alter table public.notifications
    add constraint notifications_user_dedupe_key unique (user_id, dedupe_key);

alter table public.notifications enable row level security;

revoke all on table public.notifications from public;
revoke all on table public.notifications from anon, authenticated;
grant select on table public.notifications to authenticated;

drop policy if exists notifications_select_own on public.notifications;
create policy notifications_select_own
    on public.notifications
    for select
    to authenticated
    using (user_id = (select auth.uid()));

-- ============================================================================
-- 30일 보관 정리 — 매일 KST 04:00 (UTC 19:00).
-- ============================================================================
create extension if not exists pg_cron;

create or replace function public.purge_old_notifications()
returns void
language sql
security definer
set search_path = ''
as $$
    delete from public.notifications where created_at < now() - interval '30 days';
$$;

revoke all on function public.purge_old_notifications() from public, anon, authenticated;

do $$
declare
    v_jobid bigint;
begin
    select jobid into v_jobid from cron.job where jobname = 'purge-old-notifications-daily';
    if v_jobid is not null then
        perform cron.unschedule(v_jobid);
    end if;
    perform cron.schedule(
        'purge-old-notifications-daily',
        '0 19 * * *',
        'select public.purge_old_notifications()'
    );
end $$;
