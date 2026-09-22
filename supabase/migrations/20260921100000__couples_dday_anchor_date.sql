-- 디데이 (D-day) 앵커 날짜 (#329).
--
-- 커플 단위 설정. 앱의 "디데이" 화면은 이 날짜를 시작점으로 D+N (경과 일수) 과 milestone
-- (100일 · 200일 · 300일 · ... · 1주년 · 2주년 · ...) 을 계산해 보여준다. 커플 양쪽이 공유하는
-- 값이라 users 가 아닌 couples 에 둔다.
--
-- NULL 이면 "아직 설정 안 됨" 상태 → 클라이언트가 empty state UI 로 폴백.

ALTER TABLE public.couples
    ADD COLUMN IF NOT EXISTS dday_anchor_date DATE;
