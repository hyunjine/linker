-- Helper 함수 (SECURITY DEFINER) 를 private 스키마로 이동해 PostgREST 노출 차단.
--
-- 배경: schema.sql 은 helper 를 public 에 뒀고, 첫 advisor 가
-- "anon/authenticated 가 /rest/v1/rpc/<fn> 로 호출 가능" 이라고 경고했음.
-- REVOKE 만 해서는 정책 내부 호출까지 함께 죽어 RLS 가 마비됨(직전 두 마이그레이션 참고).
-- 정석 fix: PostgREST 가 노출하지 않는 스키마(=public/graphql_public 외)에 두고,
--          authenticated 에는 EXECUTE 를 유지해 정책 · 트리거는 그대로 작동.

CREATE SCHEMA IF NOT EXISTS private;
GRANT USAGE ON SCHEMA private TO authenticated;

-- my_couple_ids 재생성 (private)
CREATE OR REPLACE FUNCTION private.my_couple_ids()
RETURNS SETOF UUID
LANGUAGE sql SECURITY DEFINER STABLE
SET search_path = ''
AS $$
    SELECT couple_id FROM public.couple_members WHERE user_id = auth.uid()
$$;
REVOKE EXECUTE ON FUNCTION private.my_couple_ids() FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION private.my_couple_ids() TO authenticated;

-- handle_new_auth_user 재생성 (private)
CREATE OR REPLACE FUNCTION private.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.users (id) VALUES (NEW.id)
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;
REVOKE EXECUTE ON FUNCTION private.handle_new_auth_user() FROM PUBLIC;

-- auth.users 트리거를 private 함수로 재연결
DROP TRIGGER IF EXISTS trg_on_auth_user_created ON auth.users;
CREATE TRIGGER trg_on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION private.handle_new_auth_user();

-- 모든 정책이 public.my_couple_ids → private.my_couple_ids 를 참조하도록 재생성
DROP POLICY IF EXISTS users_select_self_or_partner ON public.users;
CREATE POLICY users_select_self_or_partner ON public.users
    FOR SELECT TO authenticated
    USING (
        id = auth.uid()
        OR id IN (
            SELECT cm.user_id FROM public.couple_members cm
            WHERE cm.couple_id IN (SELECT private.my_couple_ids())
        )
    );

DROP POLICY IF EXISTS couples_select_member ON public.couples;
CREATE POLICY couples_select_member ON public.couples
    FOR SELECT TO authenticated
    USING (id IN (SELECT private.my_couple_ids()));

DROP POLICY IF EXISTS couples_update_member ON public.couples;
CREATE POLICY couples_update_member ON public.couples
    FOR UPDATE TO authenticated
    USING (id IN (SELECT private.my_couple_ids()))
    WITH CHECK (id IN (SELECT private.my_couple_ids()));

DROP POLICY IF EXISTS couples_delete_member ON public.couples;
CREATE POLICY couples_delete_member ON public.couples
    FOR DELETE TO authenticated
    USING (id IN (SELECT private.my_couple_ids()));

DROP POLICY IF EXISTS couple_members_select_in_my_couple ON public.couple_members;
CREATE POLICY couple_members_select_in_my_couple ON public.couple_members
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR couple_id IN (SELECT private.my_couple_ids())
    );

DROP POLICY IF EXISTS schedules_all_in_my_couple ON public.schedules;
CREATE POLICY schedules_all_in_my_couple ON public.schedules
    FOR ALL TO authenticated
    USING (couple_id IN (SELECT private.my_couple_ids()))
    WITH CHECK (
        couple_id IN (SELECT private.my_couple_ids())
        AND created_by = auth.uid()
    );

DROP POLICY IF EXISTS repeat_rules_all_in_my_couple ON public.schedule_repeat_rules;
CREATE POLICY repeat_rules_all_in_my_couple ON public.schedule_repeat_rules
    FOR ALL TO authenticated
    USING (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT private.my_couple_ids())
        )
    )
    WITH CHECK (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT private.my_couple_ids())
        )
    );

DROP POLICY IF EXISTS anniversaries_all_in_my_couple ON public.couple_anniversaries;
CREATE POLICY anniversaries_all_in_my_couple ON public.couple_anniversaries
    FOR ALL TO authenticated
    USING (couple_id IN (SELECT private.my_couple_ids()))
    WITH CHECK (couple_id IN (SELECT private.my_couple_ids()));

-- 옛 public 헬퍼 제거
DROP FUNCTION public.my_couple_ids();
DROP FUNCTION public.handle_new_auth_user();
