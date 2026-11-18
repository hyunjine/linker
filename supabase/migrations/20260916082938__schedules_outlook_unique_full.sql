-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260916082938, name schedules_outlook_unique_full).

-- Partial index 는 PostgREST upsert 의 ON CONFLICT 스펙과 매칭 안 됨.
-- 전체 unique 로 교체 (NULL 은 서로 distinct 라 internal 스케줄 여러 개 문제없음).
drop index if exists public.schedules_created_by_external_id_uidx;

alter table public.schedules
    add constraint schedules_created_by_external_id_key
    unique (created_by, external_id);
