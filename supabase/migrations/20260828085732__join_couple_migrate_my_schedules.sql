-- join_couple_by_invite 개선: 새 couple 로 넘어갈 때 내가 만든 schedules 를 함께 이관.
--
-- 기존 함수는 couple_members 에서 나를 삭제 · 새 couple 에 삽입만 하고 schedules 는 옛
-- couple_id 그대로 남겼음. 결과적으로 RLS 로 인해 옛 스케줄이 안 보이는 데이터 유실처럼 보이는
-- UX 버그가 있었다. 이 마이그레이션은 함수 안에서 내가 만든 스케줄만 (파트너 스케줄은 옛 couple
-- 그대로 유지) 새 couple 로 UPDATE 한다.

CREATE OR REPLACE FUNCTION public.join_couple_by_invite(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
    v_old_couple_id UUID;
    v_normalized TEXT := upper(trim(p_code));
BEGIN
    SELECT c.id INTO v_couple_id
      FROM public.couples c
     WHERE c.invite_code = v_normalized;

    IF v_couple_id IS NULL THEN
        RAISE EXCEPTION 'invite_code not found: %', v_normalized;
    END IF;

    -- 이미 이 couple 소속이면 no-op.
    IF EXISTS (
        SELECT 1 FROM public.couple_members
         WHERE couple_id = v_couple_id AND user_id = auth.uid()
    ) THEN
        RETURN v_couple_id;
    END IF;

    -- 기존 couple_id 캡처 (다음 DELETE 전에 미리 확보).
    SELECT couple_id INTO v_old_couple_id
      FROM public.couple_members WHERE user_id = auth.uid() LIMIT 1;

    -- 내가 만든 스케줄만 새 couple 로 이관. 파트너가 만든 것은 그대로 (해당 커플에 여전히 남는 파트너 관점 유지).
    IF v_old_couple_id IS NOT NULL AND v_old_couple_id <> v_couple_id THEN
        UPDATE public.schedules
           SET couple_id = v_couple_id
         WHERE created_by = auth.uid()
           AND couple_id = v_old_couple_id;
    END IF;

    -- 기존 소속 couple 에서 leave 후 새 couple 에 join.
    DELETE FROM public.couple_members WHERE user_id = auth.uid();
    INSERT INTO public.couple_members (couple_id, user_id) VALUES (v_couple_id, auth.uid());
    UPDATE public.couples SET linked_at = now() WHERE id = v_couple_id;
    RETURN v_couple_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT) FROM anon;
GRANT  EXECUTE ON FUNCTION public.join_couple_by_invite(TEXT) TO authenticated;
