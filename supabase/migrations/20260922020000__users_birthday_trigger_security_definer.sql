-- #326 실제 원인 해결: users UPDATE 시 tg_users_sync_birthday_schedule 트리거가 fire 하는데,
-- 이 트리거는 SECURITY INVOKER 로 만들어져 있었음 → authenticated 세션이 nickname/birth_date 를
-- 건드리면 트리거 안에서 `private.sync_birthday_schedule(...)` 를 PERFORM 하지만 `authenticated`
-- 에겐 EXECUTE 없음 (의도적으로 REVOKE 됨) → 42501 permission denied → PostgREST 가 403 반환 →
-- 클라이언트 runCatching 이 삼키고 저장/네비 실패.
--
-- 자매 트리거 tg_couple_members_backfill_birthday 는 이미 SECURITY DEFINER 여서 정상 동작.
-- 원래부터 SECURITY DEFINER 여야 했는데 실수로 빠진 것으로 보임 — 이제 맞춘다.
--
-- SECURITY DEFINER + search_path='' 조합은 함수 owner (postgres) 로 실행하며 임의 스키마
-- lookup 을 막는 표준 패턴.

CREATE OR REPLACE FUNCTION public.tg_users_sync_birthday_schedule()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.birth_date IS NOT DISTINCT FROM NEW.birth_date
       AND OLD.nickname   IS NOT DISTINCT FROM NEW.nickname
    THEN
        RETURN NEW;
    END IF;

    SELECT cm.couple_id INTO v_couple_id
      FROM public.couple_members cm
     WHERE cm.user_id = NEW.id
     LIMIT 1;
    IF v_couple_id IS NULL THEN
        RETURN NEW;
    END IF;

    PERFORM private.sync_birthday_schedule(NEW.id, v_couple_id);
    RETURN NEW;
END;
$$;
