-- 프로덕션 마이그레이션 ledger (supabase_migrations.schema_migrations) 에서 복원 (#430).
-- 원래 Supabase MCP 로 직접 적용돼 repo 에 파일이 없던 마이그레이션. 이미 프로덕션에 적용됨 —
-- 내용 수정 금지 (version 20260916082149, name outlook_ics_sync_cron).

-- 10초마다 sync-outlook-ics edge function 호출 (#242 테스트용).
-- pg_cron 1.5+ 는 'X seconds' 문법 지원. 나중에 '*/30 * * * *' 같은 표준 cron 으로 변경 예정.
select cron.schedule(
    'sync-outlook-ics',
    '10 seconds',
    $$
    select net.http_post(
        url := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/sync-outlook-ics',
        headers := jsonb_build_object('Content-Type', 'application/json'),
        timeout_milliseconds := 8000
    );
    $$
);
