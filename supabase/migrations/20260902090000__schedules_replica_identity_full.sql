-- schedules · schedule_repeat_rules 의 REPLICA IDENTITY 를 FULL 로 승격.
--
-- 기본값 (pk only) 상태에서는 DELETE WAL 이벤트에 primary key 만 실린다.
-- Supabase Realtime 은 RLS `USING` 을 OLD row 로 판정하는데, OLD 에 couple_id · created_by
-- 같은 정책 필드가 없으면 판정 자체가 실패해 이벤트가 필터-아웃된다.
--
-- 결과 (이번 이슈): 파트너 클라이언트가 DELETE 이벤트를 못 받아 로컬 캐시에 stale row 가 남음.
-- FULL 로 바꾸면 OLD row 전체가 실려서 RLS 판정이 정상 동작 → 파트너 refetch → 캐시 정합.
--
-- 비용: DELETE / UPDATE WAL 크기가 늘어남. schedules 는 소량 트래픽이라 무시할 수준.
ALTER TABLE public.schedules              REPLICA IDENTITY FULL;
ALTER TABLE public.schedule_repeat_rules  REPLICA IDENTITY FULL;
