-- 커플 온보딩용 RPC 함수. RLS 로는 초대코드 lookup · 원자적 join 이 불가능해
-- SECURITY DEFINER 함수로 우회. authenticated 만 EXECUTE.

-- ─────────────────────────────────────────────────────────────
-- create_my_couple
--   내가 이미 커플에 속해있으면 그걸 반환. 없으면 새 couple 을 만들고 나를 join.
--   반환값: (id, invite_code)
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.create_my_couple()
RETURNS TABLE(id UUID, invite_code TEXT)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
    v_code TEXT;
BEGIN
    -- 이미 소속된 커플이 있으면 그대로 반환 (재진입 idempotent)
    SELECT c.id, c.invite_code
      INTO v_couple_id, v_code
      FROM public.couples c
      JOIN public.couple_members cm ON cm.couple_id = c.id
     WHERE cm.user_id = auth.uid()
     LIMIT 1;

    IF v_couple_id IS NOT NULL THEN
        id := v_couple_id;
        invite_code := v_code;
        RETURN NEXT;
        RETURN;
    END IF;

    -- 6자 대문자 코드 생성 · UNIQUE 충돌 시 최대 5회 재시도
    FOR i IN 1..5 LOOP
        v_code := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
        BEGIN
            INSERT INTO public.couples (invite_code)
                 VALUES (v_code)
              RETURNING public.couples.id INTO v_couple_id;
            EXIT;
        EXCEPTION WHEN unique_violation THEN
            IF i = 5 THEN RAISE; END IF;
        END;
    END LOOP;

    INSERT INTO public.couple_members (couple_id, user_id)
         VALUES (v_couple_id, auth.uid());

    id := v_couple_id;
    invite_code := v_code;
    RETURN NEXT;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.create_my_couple() FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.create_my_couple() TO authenticated;

-- ─────────────────────────────────────────────────────────────
-- join_couple_by_invite(p_code)
--   초대코드로 커플을 찾아 내 소속을 그 커플로 옮긴다. 기존 소속은 자동 leave.
--   반환값: 합류한 couple_id
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.join_couple_by_invite(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
    v_normalized TEXT := upper(trim(p_code));
BEGIN
    SELECT c.id INTO v_couple_id
      FROM public.couples c
     WHERE c.invite_code = v_normalized;

    IF v_couple_id IS NULL THEN
        RAISE EXCEPTION 'invite_code not found: %', v_normalized;
    END IF;

    -- 이미 이 커플에 속해있으면 no-op
    IF EXISTS (
        SELECT 1 FROM public.couple_members
         WHERE couple_id = v_couple_id AND user_id = auth.uid()
    ) THEN
        RETURN v_couple_id;
    END IF;

    -- 기존 소속 커플에서 자동 leave (혼자였다면 orphan couple 이 남지만 이번 스코프 밖)
    DELETE FROM public.couple_members WHERE user_id = auth.uid();

    INSERT INTO public.couple_members (couple_id, user_id)
         VALUES (v_couple_id, auth.uid());

    UPDATE public.couples SET linked_at = now() WHERE id = v_couple_id;

    RETURN v_couple_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT) TO authenticated;
