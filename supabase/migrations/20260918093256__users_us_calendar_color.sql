-- 유저별 "공동(Us) 캘린더" 색상 preference (#245).
--
-- 배경: 스케줄 owner_kind='us' (공동 일정) 은 지금까지 CalendarPurple 로 하드코딩돼 있었다.
--   내 색·상대 색은 각자 프로필에서 자유롭게 고를 수 있는데 공동 색만 못 골랐던 불균형 해소.
--
-- 저장 규칙: per-user preference (my_calendar_color 와 동일 패턴). 각 유저가 자기 캘린더에서
--   공동 일정을 어떤 색으로 보고 싶은지 각자 고른다. 커플 양쪽에서 서로 다르게 보여도 OK
--   (칼로리·타임존처럼 개인 취향 축).
--
-- NULL 을 허용 → 클라이언트가 null 이면 CalendarPurple fallback (기존 하드코딩 동작 유지).
-- default '#AF52DE' 로 심어놓지 않는 이유: 프리셋 id ('purple') 로 마이그레이션 없이 fallback 을
-- 계속 코드에서 컨트롤할 수 있게 하기 위함 · 팔레트 변경 시 DB 재수정 불필요.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS us_calendar_color VARCHAR(16);
