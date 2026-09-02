-- 드로워 "공동 캘린더" 토글용 컬럼. 기본값 true — 신규 · 기존 유저 모두 공동 일정은 기본 노출.
-- '내 · 상대방 · 공동' 세 필터가 각각 독립적으로 동작한다.

ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS show_shared_calendar BOOLEAN NOT NULL DEFAULT TRUE;
