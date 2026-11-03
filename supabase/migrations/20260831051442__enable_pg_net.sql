-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260831051442, name enable_pg_net).

-- pg_net: DB trigger 안에서 HTTP 호출 가능. Edge Function 호출용.
create extension if not exists pg_net with schema extensions;
