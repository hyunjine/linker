-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260917083107, name admin_list_accounts_rpc).

create table if not exists public.admin_users (
    uid uuid primary key references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    note text
);

alter table public.admin_users enable row level security;

drop policy if exists "admin_users_self_read" on public.admin_users;
create policy "admin_users_self_read" on public.admin_users
    for select
    to authenticated
    using (uid = auth.uid());

insert into public.admin_users (uid, note)
values ('9a57c2a7-899f-413f-8ff7-f2e8060d6299', '관리자 콘솔 최초 admin')
on conflict (uid) do nothing;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (select 1 from public.admin_users where uid = auth.uid());
$$;

grant execute on function public.is_admin() to authenticated;

create or replace function public.admin_list_accounts()
returns table (
    id uuid,
    nickname text,
    profile_image_url text,
    user_devices jsonb
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
begin
    if not public.is_admin() then
        raise exception 'not_admin' using errcode = '42501';
    end if;

    return query
    select
        u.id,
        u.nickname,
        u.profile_image_url,
        (
            select jsonb_agg(jsonb_build_object('platform', ud.platform))
            from public.user_devices ud
            where ud.user_id = u.id
        ) as user_devices
    from public.users u
    where exists (select 1 from public.user_devices ud2 where ud2.user_id = u.id)
    order by u.nickname asc nulls last;
end;
$$;

grant execute on function public.admin_list_accounts() to authenticated;
