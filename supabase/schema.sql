-- Linker Supabase 초기 스키마 (#32 pivot · #35 세팅)
--
-- Supabase SQL Editor 에 통째로 붙여넣기 → 실행.
-- Ktor + Flyway 시절 원본은 docs/schema/V1__init.sql. 주요 차이:
--   1) users 는 auth.users 확장 (Supabase Auth 가 계정 자체는 관리, 우리는 프로필만)
--   2) kakao_id 제거 → auth.users.raw_user_meta_data->>'provider_id' 에서 조회
--   3) user_auth_tokens 제거 → Supabase Auth 가 auth.sessions 에 내장 관리
--   4) 모든 테이블 RLS enable + 정책 (본인 · 파트너 · 커플 멤버 접근 제어)
--   5) updated_at 트리거로 갱신 자동화

-- ─────────────────────────────────────────────────────────────
-- 0. 확장 · 공용 함수
-- ─────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- updated_at 자동 갱신 트리거 함수. UPDATE 시마다 now() 로 세팅.
CREATE OR REPLACE FUNCTION public.touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- 1. users (auth.users 프로필 확장)
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.users (
    id                    UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nickname              VARCHAR(30),
    birth_date            DATE,
    profile_image_url     TEXT,
    calendar_color        VARCHAR(16) NOT NULL DEFAULT 'blue',
    profile_completed_at  TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_users_updated_at ON public.users;
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON public.users
FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

-- auth.users 신규 가입 시 자동으로 public.users 껍데기 생성.
-- SECURITY DEFINER 로 RLS 우회 (트리거 실행 컨텍스트는 세션 유저가 아니라 postgres).
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public AS $$
BEGIN
    INSERT INTO public.users (id) VALUES (NEW.id)
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_on_auth_user_created ON auth.users;
CREATE TRIGGER trg_on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- ─────────────────────────────────────────────────────────────
-- 2. couples · couple_members
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.couples (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invite_code   VARCHAR(12) UNIQUE NOT NULL,
    display_name  VARCHAR(60),
    start_date    DATE,
    linked_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_couples_updated_at ON public.couples;
CREATE TRIGGER trg_couples_updated_at
BEFORE UPDATE ON public.couples
FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

CREATE TABLE IF NOT EXISTS public.couple_members (
    couple_id  UUID NOT NULL REFERENCES public.couples(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES public.users(id)   ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL DEFAULT 'member',
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (couple_id, user_id)
);

-- 한 유저는 활성 커플 1개만
CREATE UNIQUE INDEX IF NOT EXISTS ux_couple_members_user ON public.couple_members(user_id);

-- RLS 정책들이 "내가 속한 couple_id 인가?" 를 자주 물어야 함.
-- 정책 안에서 couple_members 를 직접 조회하면 RLS 재귀. SECURITY DEFINER 로 우회.
CREATE OR REPLACE FUNCTION public.my_couple_ids()
RETURNS SETOF UUID
LANGUAGE sql SECURITY DEFINER STABLE
SET search_path = public AS $$
    SELECT couple_id FROM public.couple_members WHERE user_id = auth.uid()
$$;

-- ─────────────────────────────────────────────────────────────
-- 3. schedules · schedule_repeat_rules (enum + table)
-- ─────────────────────────────────────────────────────────────

DO $$ BEGIN
    CREATE TYPE public.schedule_type  AS ENUM ('task', 'schedule');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.schedule_owner AS ENUM ('me', 'partner', 'us');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   UUID NOT NULL REFERENCES public.couples(id) ON DELETE CASCADE,
    created_by  UUID NOT NULL REFERENCES public.users(id),
    type        public.schedule_type   NOT NULL,
    owner_kind  public.schedule_owner  NOT NULL,
    title       VARCHAR(200) NOT NULL,

    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    all_day     BOOLEAN NOT NULL DEFAULT FALSE,
    start_time  TIME,
    end_time    TIME,

    is_done     BOOLEAN NOT NULL DEFAULT FALSE,
    -- 반복 시리즈 그룹핑. 같은 반복 규칙으로 생성된 인스턴스들은 동일 값을 공유.
    -- 편집 · 삭제 시 series_id 로 batch 처리 → 시리즈 전체 일괄 적용.
    series_id   UUID,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (start_date <= end_date),
    CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL)
    )
);

DROP TRIGGER IF EXISTS trg_schedules_updated_at ON public.schedules;
CREATE TRIGGER trg_schedules_updated_at
BEFORE UPDATE ON public.schedules
FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

CREATE INDEX IF NOT EXISTS ix_schedules_couple_range ON public.schedules(couple_id, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_schedules_couple_type  ON public.schedules(couple_id, type);
CREATE INDEX IF NOT EXISTS ix_schedules_couple_series ON public.schedules(couple_id, series_id) WHERE series_id IS NOT NULL;

DO $$ BEGIN
    CREATE TYPE public.repeat_kind AS ENUM ('daily', 'weekly', 'monthly', 'yearly', 'custom');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.schedule_repeat_rules (
    schedule_id   UUID PRIMARY KEY REFERENCES public.schedules(id) ON DELETE CASCADE,
    kind          public.repeat_kind NOT NULL,
    weekly_days   SMALLINT,
    monthly_day   SMALLINT,
    yearly_month  SMALLINT,
    yearly_day    SMALLINT,
    custom_rule   TEXT,
    ends_at       DATE,
    max_count     INTEGER,

    CHECK (
        (kind = 'daily')
        OR (kind = 'weekly'  AND weekly_days IS NOT NULL)
        OR (kind = 'monthly' AND monthly_day BETWEEN 1 AND 31)
        OR (kind = 'yearly'  AND yearly_month BETWEEN 1 AND 12
                              AND yearly_day BETWEEN 1 AND 31)
        OR (kind = 'custom'  AND custom_rule IS NOT NULL)
    )
);

-- ─────────────────────────────────────────────────────────────
-- 4. user_preferences · couple_anniversaries
-- ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.user_preferences (
    user_id                UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    show_my_calendar       BOOLEAN NOT NULL DEFAULT TRUE,
    show_partner_calendar  BOOLEAN NOT NULL DEFAULT TRUE,
    show_holidays          BOOLEAN NOT NULL DEFAULT TRUE,
    show_solar_terms       BOOLEAN NOT NULL DEFAULT TRUE,
    show_lunar             BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_user_preferences_updated_at ON public.user_preferences;
CREATE TRIGGER trg_user_preferences_updated_at
BEFORE UPDATE ON public.user_preferences
FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

CREATE TABLE IF NOT EXISTS public.couple_anniversaries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id      UUID NOT NULL REFERENCES public.couples(id) ON DELETE CASCADE,
    title          VARCHAR(60) NOT NULL,
    date           DATE NOT NULL,
    repeat_yearly  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_couple_anniversaries_updated_at ON public.couple_anniversaries;
CREATE TRIGGER trg_couple_anniversaries_updated_at
BEFORE UPDATE ON public.couple_anniversaries
FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();

CREATE INDEX IF NOT EXISTS ix_anniversaries_couple ON public.couple_anniversaries(couple_id);

-- ─────────────────────────────────────────────────────────────
-- 5. RLS enable
-- ─────────────────────────────────────────────────────────────

ALTER TABLE public.users                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couples                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couple_members         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedules              ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.schedule_repeat_rules  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.couple_anniversaries   ENABLE ROW LEVEL SECURITY;

-- ─────────────────────────────────────────────────────────────
-- 6. RLS 정책
--   원칙: 로그인한 유저 (auth.uid()) 만 자기 · 파트너 · 소속 커플 데이터 접근.
--   my_couple_ids() 로 재귀 회피. 정책은 idempotent 하게 DROP → CREATE.
-- ─────────────────────────────────────────────────────────────

-- users: 본인 select/update. 파트너 (같은 couple 소속) select 만 허용.
DROP POLICY IF EXISTS users_select_self_or_partner ON public.users;
CREATE POLICY users_select_self_or_partner ON public.users
    FOR SELECT TO authenticated
    USING (
        id = auth.uid()
        OR id IN (
            SELECT cm.user_id FROM public.couple_members cm
            WHERE cm.couple_id IN (SELECT public.my_couple_ids())
        )
    );

DROP POLICY IF EXISTS users_update_self ON public.users;
CREATE POLICY users_update_self ON public.users
    FOR UPDATE TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- couples: 내가 속한 커플만 SELECT · UPDATE · DELETE. INSERT 는 로그인 유저 누구나 (커플 생성).
DROP POLICY IF EXISTS couples_select_member ON public.couples;
CREATE POLICY couples_select_member ON public.couples
    FOR SELECT TO authenticated
    USING (id IN (SELECT public.my_couple_ids()));

DROP POLICY IF EXISTS couples_insert_any ON public.couples;
CREATE POLICY couples_insert_any ON public.couples
    FOR INSERT TO authenticated
    WITH CHECK (true);

DROP POLICY IF EXISTS couples_update_member ON public.couples;
CREATE POLICY couples_update_member ON public.couples
    FOR UPDATE TO authenticated
    USING (id IN (SELECT public.my_couple_ids()))
    WITH CHECK (id IN (SELECT public.my_couple_ids()));

DROP POLICY IF EXISTS couples_delete_member ON public.couples;
CREATE POLICY couples_delete_member ON public.couples
    FOR DELETE TO authenticated
    USING (id IN (SELECT public.my_couple_ids()));

-- couple_members: 내가 속한 커플의 멤버 목록 조회 · 나 자신 join/leave.
DROP POLICY IF EXISTS couple_members_select_in_my_couple ON public.couple_members;
CREATE POLICY couple_members_select_in_my_couple ON public.couple_members
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR couple_id IN (SELECT public.my_couple_ids())
    );

DROP POLICY IF EXISTS couple_members_insert_self ON public.couple_members;
CREATE POLICY couple_members_insert_self ON public.couple_members
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS couple_members_delete_self ON public.couple_members;
CREATE POLICY couple_members_delete_self ON public.couple_members
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- schedules: 내가 속한 커플의 스케줄만 접근. INSERT 는 created_by 가 나여야 함.
DROP POLICY IF EXISTS schedules_all_in_my_couple ON public.schedules;
CREATE POLICY schedules_all_in_my_couple ON public.schedules
    FOR ALL TO authenticated
    USING (couple_id IN (SELECT public.my_couple_ids()))
    WITH CHECK (
        couple_id IN (SELECT public.my_couple_ids())
        AND created_by = auth.uid()
    );

-- schedule_repeat_rules: 스케줄 owner 와 동일 접근 규칙 (join 으로 검증).
DROP POLICY IF EXISTS repeat_rules_all_in_my_couple ON public.schedule_repeat_rules;
CREATE POLICY repeat_rules_all_in_my_couple ON public.schedule_repeat_rules
    FOR ALL TO authenticated
    USING (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT public.my_couple_ids())
        )
    )
    WITH CHECK (
        schedule_id IN (
            SELECT s.id FROM public.schedules s
            WHERE s.couple_id IN (SELECT public.my_couple_ids())
        )
    );

-- user_preferences: 본인 것만.
DROP POLICY IF EXISTS user_preferences_all_self ON public.user_preferences;
CREATE POLICY user_preferences_all_self ON public.user_preferences
    FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- couple_anniversaries: 내가 속한 커플만.
DROP POLICY IF EXISTS anniversaries_all_in_my_couple ON public.couple_anniversaries;
CREATE POLICY anniversaries_all_in_my_couple ON public.couple_anniversaries
    FOR ALL TO authenticated
    USING (couple_id IN (SELECT public.my_couple_ids()))
    WITH CHECK (couple_id IN (SELECT public.my_couple_ids()));
