-- 커플 연결 해제 RPC.
-- 호출자는 현재 커플에서 leave 하고 새 solo 커플로 옮겨간다.
-- 내가 만든 스케줄만 새 couple 로 이관 (join_couple_by_invite 와 동일 패턴).
-- 파트너가 만든 스케줄은 옛 couple 에 그대로 남아 파트너 관점에서 계속 보인다.
--
-- 반환값: 새 solo couple id (클라이언트가 realtime 채널 재구성 · chip refetch 에 사용).

CREATE OR REPLACE FUNCTION public.unlink_couple()
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_old_couple_id UUID;
    v_new_couple_id UUID;
    v_new_code      TEXT;
BEGIN
    SELECT couple_id INTO v_old_couple_id
      FROM public.couple_members
     WHERE user_id = auth.uid()
     LIMIT 1;

    IF v_old_couple_id IS NULL THEN
        RAISE EXCEPTION 'no couple to unlink';
    END IF;

    -- 새 solo couple 생성. invite_code 는 6자 대문자, UNIQUE 충돌 최대 5회 재시도.
    FOR i IN 1..5 LOOP
        v_new_code := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 6));
        BEGIN
            INSERT INTO public.couples (invite_code)
                 VALUES (v_new_code)
              RETURNING public.couples.id INTO v_new_couple_id;
            EXIT;
        EXCEPTION WHEN unique_violation THEN
            IF i = 5 THEN RAISE; END IF;
        END;
    END LOOP;

    -- 내가 만든 스케줄만 새 couple 로 이관. 파트너 스케줄은 옛 couple 그대로.
    UPDATE public.schedules
       SET couple_id = v_new_couple_id
     WHERE created_by = auth.uid()
       AND couple_id = v_old_couple_id;

    -- 옛 couple 에서 leave, 새 couple 로 join.
    DELETE FROM public.couple_members
     WHERE user_id = auth.uid() AND couple_id = v_old_couple_id;

    INSERT INTO public.couple_members (couple_id, user_id)
         VALUES (v_new_couple_id, auth.uid());

    RETURN v_new_couple_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.unlink_couple() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.unlink_couple() FROM anon;
GRANT  EXECUTE ON FUNCTION public.unlink_couple() TO authenticated;
