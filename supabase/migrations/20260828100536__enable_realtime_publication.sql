-- Realtime postgres_changes 를 위해 supabase_realtime publication 에 대상 테이블 추가.
-- 앱은 이 publication 을 구독해 파트너 CRUD 를 즉시 수신한다.
--   schedules · schedule_repeat_rules · couple_anniversaries: 스케줄 · 기념일 sync
--   users: 파트너 프로필 (닉네임 · 아바타 · 캘린더 색) 변경 sync
--   couple_members: 파트너 join · 이탈 sync
ALTER PUBLICATION supabase_realtime ADD TABLE public.schedules;
ALTER PUBLICATION supabase_realtime ADD TABLE public.schedule_repeat_rules;
ALTER PUBLICATION supabase_realtime ADD TABLE public.couple_anniversaries;
ALTER PUBLICATION supabase_realtime ADD TABLE public.users;
ALTER PUBLICATION supabase_realtime ADD TABLE public.couple_members;
