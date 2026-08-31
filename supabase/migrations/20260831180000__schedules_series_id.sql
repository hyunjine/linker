-- 반복 일정 시리즈 그룹핑용 컬럼.
-- 한 번의 반복 규칙 (예: "매주 월/수/금") 으로 만들어진 인스턴스들은 동일한 series_id 를 공유.
-- 편집 · 삭제 시 series_id 로 batch update / delete 하면 시리즈 전체에 일괄 적용된다.
--
-- 종료 날짜는 기존 schedule_repeat_rules.ends_at 을 재사용 (이미 DATE NULL 컬럼 존재).
-- 각 materialized row 는 자기 자신의 schedule_repeat_rules 를 복제 소유 → 편집 UI 에서 규칙 복원 용이.

ALTER TABLE public.schedules
    ADD COLUMN IF NOT EXISTS series_id UUID;

CREATE INDEX IF NOT EXISTS ix_schedules_couple_series
    ON public.schedules(couple_id, series_id)
    WHERE series_id IS NOT NULL;
