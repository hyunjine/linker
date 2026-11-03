-- 프로필 birth_date · 커플 링킹에 따라 생일 스케줄을 자동 생성 / 동기화 (#307).
--
-- 정책:
-- - `public.schedules` 에 `birthday_uid` 컬럼을 추가해 "이 로우는 X 유저의 생일 이벤트" 라는 표식을 남긴다.
--   같은 유저의 생일 스케줄은 (birthday_uid, couple_id) 별로 유일. 시리즈(series_id) 는 정상 반복 시리즈와 동일.
-- - 매년 반복되는 종일 이벤트 (`type='schedule', all_day=true, owner_kind='me', is_private=false`) 로 저장.
--   `owner_kind='me'` 는 "생성자 관점" 이므로 파트너가 보면 상대편(파트너) 톤 컬러로 렌더 (기존 관례).
-- - materialize 범위는 50 년치. 앱의 `MAX_SERIES_INSTANCES = 1000` 안에 안전히 들어오고, 실사용상 충분.
-- - 트리거 두 개로 커버:
--     (a) `users` 의 birth_date · nickname 이 바뀔 때 → 생성자(=본인) 권한으로 기존 시리즈 지우고 재생성
--     (b) `couple_members` 에 유저가 새로 들어올 때 → 그 커플에 해당 유저의 생일이 없으면 backfill
--   (a) 는 일반 트리거 (호출자 = 본인 = RLS 통과), (b) 는 SECURITY DEFINER
--   (join_couple_by_invite / create_my_couple 이 SECURITY DEFINER 안에서 INSERT 하기 때문).
-- - 앞서 도입된 series INSERT 트리거 (#263 dedupe) 가 batch 로 들어와도 시리즈당 1건만 push 하므로,
--   birthday 50건 insert 는 한 번의 푸시 대상이 된다.

-- ─────────────────────────────────────────────────────────────
-- 1) schedules 에 birthday 표식 컬럼 · 인덱스
-- ─────────────────────────────────────────────────────────────
ALTER TABLE public.schedules
    ADD COLUMN IF NOT EXISTS birthday_uid UUID REFERENCES auth.users(id) ON DELETE CASCADE;

COMMENT ON COLUMN public.schedules.birthday_uid IS
'해당 로우가 birthday_uid 유저의 생일 자동 이벤트임을 표시. NULL 이면 일반 스케줄. #307';

-- 특정 유저의 생일이 어느 커플에 있는지 빠르게 조회하기 위한 부분 인덱스.
CREATE INDEX IF NOT EXISTS ix_schedules_birthday_uid_couple
    ON public.schedules(birthday_uid, couple_id)
    WHERE birthday_uid IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 2) 특정 (target_uid, couple_id) 쌍에 대해 생일 시리즈를 재생성하는 헬퍼.
--    두 트리거가 공유. 이 함수는 SECURITY INVOKER 로 두고, 호출부에서 필요하면 SECURITY DEFINER 로 감싸 준다.
--    (users 트리거는 호출자 = 본인이라 INVOKER 로 충분, couple_members 트리거는 감싸 준다.)
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION private.sync_birthday_schedule(
    p_user_id  UUID,
    p_couple_id UUID
)
RETURNS VOID
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    v_user public.users%ROWTYPE;
    v_month INT;
    v_day INT;
    v_year INT;
    v_series UUID := gen_random_uuid();
    v_title TEXT;
    v_nick TEXT;
    v_start DATE;
    v_schedule_id UUID;
    v_i INT;
    v_years CONSTANT INT := 50;
BEGIN
    -- 이 커플에 있는 이 유저의 기존 생일 시리즈는 모두 정리. 파트너 커플에 있는 건 그대로 둠.
    DELETE FROM public.schedules
     WHERE birthday_uid = p_user_id
       AND couple_id = p_couple_id;

    SELECT * INTO v_user FROM public.users WHERE id = p_user_id;
    IF NOT FOUND OR v_user.birth_date IS NULL THEN
        RETURN;
    END IF;

    v_month := EXTRACT(MONTH FROM v_user.birth_date)::INT;
    v_day := EXTRACT(DAY FROM v_user.birth_date)::INT;
    v_nick := COALESCE(NULLIF(TRIM(v_user.nickname), ''), '내');
    v_title := v_nick || ' 생일';

    v_year := EXTRACT(YEAR FROM current_date)::INT;
    -- 올해 생일이 이미 지났으면 내년부터 시작.
    IF make_date(v_year, v_month, v_day) < current_date THEN
        v_year := v_year + 1;
    END IF;

    FOR v_i IN 0..(v_years - 1) LOOP
        v_start := make_date(v_year + v_i, v_month, v_day);

        INSERT INTO public.schedules (
            couple_id, created_by, type, owner_kind, title,
            start_date, end_date, all_day, is_private,
            series_id, birthday_uid, reminder_time
        )
        VALUES (
            p_couple_id, p_user_id, 'schedule', 'me', v_title,
            v_start, v_start, TRUE, FALSE,
            v_series, p_user_id, '09:00:00'
        )
        RETURNING id INTO v_schedule_id;

        INSERT INTO public.schedule_repeat_rules (
            schedule_id, kind, yearly_month, yearly_day, ends_at
        )
        VALUES (
            v_schedule_id, 'yearly', v_month::SMALLINT, v_day::SMALLINT, NULL
        );
    END LOOP;
END;
$$;

-- 이 헬퍼는 직접 호출용이 아니라 트리거 내부용. authenticated 에게 EXECUTE 부여 X.
REVOKE ALL ON FUNCTION private.sync_birthday_schedule(UUID, UUID) FROM PUBLIC;

-- ─────────────────────────────────────────────────────────────
-- 3) users 의 birth_date · nickname 변화에 반응하는 트리거.
--    호출자 = 본인 → RLS 통과 (INSERT WITH CHECK: created_by = auth.uid()).
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.tg_users_sync_birthday_schedule()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    v_couple_id UUID;
BEGIN
    -- birth_date 도 nickname 도 실제로 안 바뀌었다면 스킵.
    IF TG_OP = 'UPDATE'
       AND OLD.birth_date IS NOT DISTINCT FROM NEW.birth_date
       AND OLD.nickname   IS NOT DISTINCT FROM NEW.nickname
    THEN
        RETURN NEW;
    END IF;

    -- 아직 어떤 커플에도 안 붙어 있으면 스케줄 붙일 곳이 없으므로 스킵.
    -- 이후 create_my_couple / join_couple_by_invite 로 couple_members 에 들어올 때
    -- couple_members 트리거가 backfill 해준다.
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

DROP TRIGGER IF EXISTS trg_users_sync_birthday_schedule ON public.users;
CREATE TRIGGER trg_users_sync_birthday_schedule
    AFTER INSERT OR UPDATE OF birth_date, nickname ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION public.tg_users_sync_birthday_schedule();

-- ─────────────────────────────────────────────────────────────
-- 4) couple_members INSERT 시 백필 트리거.
--    - create_my_couple / join_couple_by_invite / unlink_couple 모두 여기로 흘러옴.
--    - 이미 이 커플에 이 유저의 생일이 있으면 (join_couple_by_invite 로 자동 이관된 경우) skip.
--    - SECURITY DEFINER — 부모 RPC 가 SECURITY DEFINER 라 세션 auth.uid() 는 있으나
--      RLS 우회가 안전한 편이 재현성이 높다.
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.tg_couple_members_backfill_birthday()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.schedules
         WHERE birthday_uid = NEW.user_id
           AND couple_id = NEW.couple_id
    ) THEN
        RETURN NEW;
    END IF;

    PERFORM private.sync_birthday_schedule(NEW.user_id, NEW.couple_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_couple_members_backfill_birthday ON public.couple_members;
CREATE TRIGGER trg_couple_members_backfill_birthday
    AFTER INSERT ON public.couple_members
    FOR EACH ROW
    EXECUTE FUNCTION public.tg_couple_members_backfill_birthday();

-- ─────────────────────────────────────────────────────────────
-- 5) 이미 기존 유저 · 커플 조합에 대해 최초 1회 backfill.
--    birth_date 가 있는 유저의 소속 커플마다 생일 시리즈를 만든다.
--    이후 잘못돼서 다시 돌려도 안전하게 재생성.
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT u.id AS user_id, cm.couple_id
          FROM public.users u
          JOIN public.couple_members cm ON cm.user_id = u.id
         WHERE u.birth_date IS NOT NULL
    LOOP
        PERFORM private.sync_birthday_schedule(r.user_id, r.couple_id);
    END LOOP;
END;
$$;
