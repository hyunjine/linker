-- Outlook ICS 동기화 cron 중단 (#242).
--
-- 배경: #242 초안은 회사 관리자 승인 (admin consent) 을 피하려고 ICS 게시 URL 을 서버가 주기적으로
--   fetch 하는 방식 (Edge Function `sync-outlook-ics` + pg_cron `sync-outlook-ics`, 테스트용 10초 주기) 이었다.
--   관리자 승인을 받아 Microsoft Graph (MSAL) 로 앱에서 동기화하는 방식으로 전환하면서 ICS 경로는 폐기.
--
-- 이 cron 을 남겨두면 안 되는 이유: ICS 함수는 매 실행마다 "이번 ICS 에 없는 source='outlook' 미러" 를
--   삭제한다. Graph 미러는 external_id (Graph event id) 가 ICS UID 와 달라 10초마다 지워진다.
--
-- 범위: cron 만 해제. `outlook_ics_subscriptions` 테이블 · Edge Function 은 남겨둔다 (데이터 보존 ·
--   롤백 여지). 정리는 후속.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'sync-outlook-ics') THEN
        PERFORM cron.unschedule('sync-outlook-ics');
    END IF;
END;
$$;
