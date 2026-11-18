-- Supabase 는 public 스키마의 모든 함수에 대해 default 로 anon · authenticated · service_role
-- 세 role 에 EXECUTE 를 부여한다. `REVOKE FROM PUBLIC` 은 Postgres 표준 PUBLIC 만 지워서
-- Supabase 의 명시적 GRANT 는 그대로 남는다. anon 을 명시적으로 revoke 해 advisor 경고 정리.

REVOKE EXECUTE ON FUNCTION public.create_my_couple()              FROM anon;
REVOKE EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT)     FROM anon;
