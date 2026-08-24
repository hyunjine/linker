-- 20260824153000 __harden_functions 에서 EXECUTE 를 잘못 회수한 결과
-- 정책 내부의 my_couple_ids() 호출까지 막혀 모든 RLS 쿼리가 permission denied.
-- 즉시 원복. (제대로 된 하드닝은 다음 마이그레이션에서 스키마 이동으로 처리)
GRANT EXECUTE ON FUNCTION public.my_couple_ids()        TO authenticated;
GRANT EXECUTE ON FUNCTION public.handle_new_auth_user() TO authenticated;
