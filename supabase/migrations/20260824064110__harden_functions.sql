-- Supabase advisor 경고 정리 (#35)
--
-- schema.sql 최초 적용 후 security advisor 가 지적한 3건:
--   1) touch_updated_at: search_path 미고정 → 스키마 하이재킹 가능
--   2) handle_new_auth_user: SECURITY DEFINER 인데 anon/authenticated 가 RPC 로 호출 가능
--   3) my_couple_ids: 위와 동일
--
-- 두 함수 모두 트리거 · RLS 정책 내부에서만 쓰이므로 REST 노출은 실수. EXECUTE 회수.

ALTER FUNCTION public.touch_updated_at() SET search_path = '';

REVOKE EXECUTE ON FUNCTION public.handle_new_auth_user() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.handle_new_auth_user() FROM anon, authenticated;

REVOKE EXECUTE ON FUNCTION public.my_couple_ids() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.my_couple_ids() FROM anon, authenticated;
