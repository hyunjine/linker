-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260916082837, name schedules_outlook_external_id_unique).

-- Outlook ICS 미러 upsert 용 unique constraint (#242).
-- source='outlook' 인 row 는 (created_by, external_id) 조합이 유일해야 함 (동일 이벤트 중복 미러 방지).
-- Partial index 로 걸어 internal 스케줄은 제약 밖.
create unique index if not exists schedules_created_by_external_id_uidx
    on public.schedules (created_by, external_id)
 where external_id is not null;
