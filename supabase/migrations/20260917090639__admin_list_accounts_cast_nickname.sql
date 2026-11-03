-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260917090639, name admin_list_accounts_cast_nickname).

-- #293: nickname 컬럼이 varchar(30) 이라 함수 return type (text) 와 mismatch → 42804.
-- 명시 캐스팅으로 해결. is_admin() 및 admin_users 테이블은 재생성하지 않는다.

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
        u.nickname::text,
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
