-- 마지막 멤버가 leave 하면 그 couple 자체를 자동 삭제.
-- 옵션 B (트리거) — join 흐름뿐 아니라 모든 leave 케이스 (직접 DELETE, cascade, 향후 로그아웃 정리 등) 를 커버.
-- 함수는 private 스키마에 두어 PostgREST 노출 차단 (기존 helper 함수와 동일 패턴).

CREATE OR REPLACE FUNCTION private.delete_empty_couple()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.couple_members WHERE couple_id = OLD.couple_id
    ) THEN
        DELETE FROM public.couples WHERE id = OLD.couple_id;
    END IF;
    RETURN OLD;
END;
$$;

REVOKE EXECUTE ON FUNCTION private.delete_empty_couple() FROM PUBLIC;

DROP TRIGGER IF EXISTS trg_couple_members_after_delete ON public.couple_members;
CREATE TRIGGER trg_couple_members_after_delete
AFTER DELETE ON public.couple_members
FOR EACH ROW EXECUTE FUNCTION private.delete_empty_couple();

-- 이미 쌓여있는 orphan (멤버 0) 도 이 시점에 한 번 청소.
DELETE FROM public.couples
 WHERE id NOT IN (SELECT DISTINCT couple_id FROM public.couple_members);
