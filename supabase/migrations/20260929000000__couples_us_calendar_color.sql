-- 공동(Us) 캘린더 색을 커플 단위로 공유 (#392).
--
-- 배경: #245 에서 공동 색을 per-user preference (users.us_calendar_color) 로 설계했더니
--   한쪽이 색을 바꿔도 상대 화면 · 위젯에는 반영되지 않았다 ("커플 컬러 동기화 안 됨").
--   공동 일정은 커플이 함께 쓰는 캘린더라 색도 커플 공유값이어야 자연스럽다
--   (dday_anchor_date 와 같은 축, 20260921100000__couples_dday_anchor_date.sql 참고).
--
-- 값 형식: users.us_calendar_color 와 동일 — 프리셋 id ('purple' 등 소문자) 또는 커스텀
--   '#RRGGBB' (클라이언트 파서가 받는 '#RRGGBBAA' 도 허용). NULL = 미설정 → 클라이언트가
--   users.us_calendar_color → CalendarPurple 순으로 fallback.
--
-- 호환: users.us_calendar_color 는 예전 앱이 읽고 쓰므로 삭제 · 변경하지 않는다.
--   새 RPC 는 couples 값과 함께 두 멤버의 users.us_calendar_color 도 같은 값으로 맞춰
--   아직 업데이트 안 한 파트너(예전 앱) 화면에도 다음 프로필 새로고침 때 반영되게 한다.
--
-- 적용 순서: 이 migration 은 순수 추가 (컬럼 · 함수) 라 앱 배포 전에 먼저 적용해도
--   예전 앱에 영향 없음. 새 앱은 RPC 가 없으면 저장 실패하므로 반드시 migration → 앱 순.

-- ─────────────────────────────────────────────────────────────
-- 1. 컬럼 + 형식 CHECK
-- ─────────────────────────────────────────────────────────────
-- couples_update_member 정책이 멤버의 직접 UPDATE 를 허용하므로 (dday_anchor_date 저장 경로),
-- RPC 를 우회한 쓰기에도 형식이 보장되도록 CHECK 로 한 번 더 막는다.
ALTER TABLE public.couples
    ADD COLUMN IF NOT EXISTS us_calendar_color VARCHAR(16);

ALTER TABLE public.couples
    DROP CONSTRAINT IF EXISTS couples_us_calendar_color_format;
ALTER TABLE public.couples
    ADD CONSTRAINT couples_us_calendar_color_format
    CHECK (
        us_calendar_color IS NULL
        OR us_calendar_color ~ '^([a-z]{1,16}|#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8})$'
    );

-- ─────────────────────────────────────────────────────────────
-- 2. 백필
-- ─────────────────────────────────────────────────────────────
-- 커플 멤버 중 users.us_calendar_color 가 non-null 인 값 하나를 복사.
-- 둘 다 설정돼 있으면 **먼저 합류한 멤버 (couple_members.joined_at 이 이른 쪽 = 보통 초대코드를
-- 만든 쪽)** 의 값을 쓰고, joined_at 이 같으면 user_id 오름차순으로 결정적으로 고른다.
-- 형식에 안 맞는 레거시 값은 CHECK 위반 대신 건너뛴다 (해당 커플은 NULL → 클라이언트 fallback).
UPDATE public.couples c
   SET us_calendar_color = picked.us_calendar_color
  FROM (
        SELECT DISTINCT ON (cm.couple_id)
               cm.couple_id,
               u.us_calendar_color
          FROM public.couple_members cm
          JOIN public.users u ON u.id = cm.user_id
         WHERE u.us_calendar_color IS NOT NULL
           AND u.us_calendar_color ~ '^([a-z]{1,16}|#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8})$'
         ORDER BY cm.couple_id, cm.joined_at ASC, cm.user_id ASC
       ) AS picked
 WHERE c.id = picked.couple_id
   AND c.us_calendar_color IS NULL;

-- ─────────────────────────────────────────────────────────────
-- 3. 쓰기 RPC
-- ─────────────────────────────────────────────────────────────
-- 호출자가 속한 커플의 공동 색만 갱신. couples 의 다른 컬럼 (invite_code 등) 은 건드릴 수 없다.
-- 두 멤버의 users.us_calendar_color 도 같은 값으로 맞춘다 (예전 앱 호환, 위 주석 참고).
CREATE OR REPLACE FUNCTION public.set_couple_us_calendar_color(p_color TEXT)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
    v_color     TEXT := btrim(p_color);
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'not authenticated' USING ERRCODE = '42501';
    END IF;

    IF v_color IS NULL
       OR length(v_color) > 16
       OR v_color !~ '^([a-z]{1,16}|#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8})$' THEN
        RAISE EXCEPTION 'invalid us calendar color: %', p_color USING ERRCODE = '22023';
    END IF;

    SELECT couple_id INTO v_couple_id
      FROM public.couple_members
     WHERE user_id = auth.uid()
     LIMIT 1;

    IF v_couple_id IS NULL THEN
        RAISE EXCEPTION 'no couple' USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.couples
       SET us_calendar_color = v_color
     WHERE id = v_couple_id;

    UPDATE public.users
       SET us_calendar_color = v_color
     WHERE id IN (
            SELECT user_id FROM public.couple_members WHERE couple_id = v_couple_id
           );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.set_couple_us_calendar_color(TEXT) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.set_couple_us_calendar_color(TEXT) FROM anon;
GRANT  EXECUTE ON FUNCTION public.set_couple_us_calendar_color(TEXT) TO authenticated;
