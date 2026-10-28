-- Outlook 재택 연동 여부 (#420).
--
-- 배경: Outlook 재택 미러는 앱 (MSAL delegated 토큰) 만 동기화할 수 있다. 서버는 Graph 변경 알림을
--   받을 수 없어서 (delegated 권한으로 남의 공유 캘린더 구독 불가 · application 권한 없음) 30분마다
--   연동 유저 기기를 silent push 로 깨워 앱이 sync 하게 한다 (Edge Function `send-outlook-sync-push`).
--   이 컬럼은 그 push 대상을 고르는 용도.
--
-- 값: 앱이 Outlook sync 에 성공하면 now() 로 세팅, 연동 해제 시 NULL. NULL = push 대상 아님.
--   MSAL 계정 · 팀 이름은 기기별 (로컬) 이라 정확한 판정은 깨어난 기기에서 다시 한다 — 이 컬럼은 대략적인 필터.
--
-- 권한: 기존 RLS `users_update_self` (id = auth.uid()) 로 본인 row 만 갱신 가능. 추가 정책 불필요.
-- Additive only — 예전 앱은 이 컬럼을 모르고, 읽지도 쓰지도 않는다.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS outlook_connected_at TIMESTAMPTZ;

COMMENT ON COLUMN public.users.outlook_connected_at IS
    'Outlook 재택 sync 를 마지막으로 켠 시각 (#420). NULL 이면 send-outlook-sync-push 대상 아님.';
