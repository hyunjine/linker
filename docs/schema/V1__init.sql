-- v1 초기 스키마. docs/db-design.md §5 초안 기반.
-- 이후 스키마 변경은 V2__*.sql 등으로 추가만 하고 기존 파일은 수정하지 않는다.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─────────────── users ───────────────
CREATE TABLE IF NOT EXISTS users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kakao_id              BIGINT UNIQUE NOT NULL,
    nickname              VARCHAR(30),
    birth_date            DATE,
    profile_image_url     TEXT,
    calendar_color        VARCHAR(16) NOT NULL DEFAULT 'blue',
    profile_completed_at  TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────── couples ───────────────
CREATE TABLE IF NOT EXISTS couples (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invite_code   VARCHAR(12) UNIQUE NOT NULL,
    display_name  VARCHAR(60),
    start_date    DATE,
    linked_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS couple_members (
    couple_id  UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL DEFAULT 'member',
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (couple_id, user_id)
);

-- 한 유저는 활성 커플 1개만
CREATE UNIQUE INDEX IF NOT EXISTS ux_couple_members_user ON couple_members(user_id);

-- ─────────────── schedules ───────────────
DO $$ BEGIN
    CREATE TYPE schedule_type  AS ENUM ('task', 'schedule');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE schedule_owner AS ENUM ('me', 'partner', 'us');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    created_by  UUID NOT NULL REFERENCES users(id),
    type        schedule_type   NOT NULL,
    owner_kind  schedule_owner  NOT NULL,
    title       VARCHAR(200) NOT NULL,

    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    all_day     BOOLEAN NOT NULL DEFAULT FALSE,
    start_time  TIME,
    end_time    TIME,

    is_done     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (start_date <= end_date),
    CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_schedules_couple_range ON schedules(couple_id, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_schedules_couple_type  ON schedules(couple_id, type);

-- ─────────────── schedule_repeat_rules ───────────────
DO $$ BEGIN
    CREATE TYPE repeat_kind AS ENUM ('daily', 'weekly', 'monthly', 'yearly', 'custom');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS schedule_repeat_rules (
    schedule_id   UUID PRIMARY KEY REFERENCES schedules(id) ON DELETE CASCADE,
    kind          repeat_kind NOT NULL,
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

-- ─────────────── user_preferences ───────────────
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id                UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    show_my_calendar       BOOLEAN NOT NULL DEFAULT TRUE,
    show_partner_calendar  BOOLEAN NOT NULL DEFAULT TRUE,
    show_holidays          BOOLEAN NOT NULL DEFAULT TRUE,
    show_solar_terms       BOOLEAN NOT NULL DEFAULT TRUE,
    show_lunar             BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─────────────── user_auth_tokens ───────────────
CREATE TABLE IF NOT EXISTS user_auth_tokens (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider           VARCHAR(16) NOT NULL,
    access_token_enc   BYTEA NOT NULL,
    refresh_token_enc  BYTEA,
    access_expires_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_user_auth_tokens_user ON user_auth_tokens(user_id, provider);

-- ─────────────── couple_anniversaries ───────────────
CREATE TABLE IF NOT EXISTS couple_anniversaries (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id      UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    title          VARCHAR(60) NOT NULL,
    date           DATE NOT NULL,
    repeat_yearly  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_anniversaries_couple ON couple_anniversaries(couple_id);
