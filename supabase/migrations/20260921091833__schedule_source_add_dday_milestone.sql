-- 디데이 milestone 자동 반영 (#329) 용 schedule_source enum 값 추가.
--
-- 원래 `internal`, `outlook` 두 값만 허용하는 enum 이라 클라이언트가 milestone 배치 insert
-- 시 `source='dday_milestone'` 를 넣으면 서버가 22P02 로 거부. runCatching 이 삼켜서 UI 상
-- 조용히 실패했음. 값 추가로 정상 저장 가능.

ALTER TYPE public.schedule_source ADD VALUE IF NOT EXISTS 'dday_milestone';
