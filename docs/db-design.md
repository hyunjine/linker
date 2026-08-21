# Linker DB 설계 (v0)

> 서버 작업 착수 전 초안. 현재 클라이언트(공유 KMP + iOS/Android)에서 사용/저장되고 있는 도메인 모델을 정리하고, 이를 서버 관계형 DB(PostgreSQL 기준)로 매핑한 스키마.
>
> 확정본 아님. 이슈 논의 후 컬럼/타입 조정 → 마이그레이션.

---

## 1. 개요

**앱 성격**: 커플 2인이 일정과 할 일을 공유·편집·조회하는 캘린더 앱.

**핵심 사용 흐름**
1. 카카오 로그인 → 서버가 유저 세션 발급
2. 프로필 편집 (닉네임, 생년월일, 프로필 사진, 캘린더 색상)
3. 커플 연결 (상대 코드 입력) → 커플 그룹 생성
4. 메인 캘린더에서 월별 조회, 셀 탭 시 그날 상세, 롱프레스/chip 탭 시 일정 생성
5. 일정 = 할 일(Task) · 종일 일정 · 시간 일정 · 반복 규칙

**핵심 개념**
- `User` : 로그인 계정 하나 = 유저 하나
- `Couple` : 두 유저를 묶는 그룹 (본인 · 상대방 · 공동)
- `Schedule` : 할 일과 일정의 공통 엔티티 (`type` 으로 구분)
- `RepeatRule` : `Schedule` 하위, 다형성 (없음/매일/매주/매월/매년/커스텀)
- `SpecialDay` : 공휴일 · 24절기 (외부 API 캐시)

---

## 2. 도메인 모델 (클라이언트 기준)

`shared/src/commonMain/kotlin/com/hyunjine/linker/**` 에서 발췌.

| 클라이언트 타입 | 위치 | 서버 매핑 |
|---|---|---|
| `ScheduleType {Task, Schedule}` | `ui/schedule/ScheduleModels.kt` | `schedules.type` enum |
| `ScheduleOwner {Me, Partner, Us}` | 동일 | `schedules.owner_kind` enum |
| `RepeatRule` (sealed) | 동일 | `schedule_repeat_rules` 테이블 |
| `ScheduleDraft` | 동일 | `schedules` insert payload |
| `DayTask` / `TimedSchedule` / `AllDaySchedule` / `DayDetail` | `ui/main/DayDetailSheet.kt` | `schedules` 뷰 파생 |
| `DayOwner {Me, Partner, Us}` | 동일 | `ScheduleOwner` 와 동일 소스 |
| `CalendarColorOption` | `ui/profile/ProfileSetupScreen.kt` | `users.calendar_color` |
| `SpecialDayKind {Holiday, SolarTerm}` | `data/specialday/*` | `special_days.kind` |
| `DrawerDisplayState` (showMyCalendar/showPartnerCalendar/showHolidays/showSolarTerms) | `ui/main/MainDrawer.kt` | `user_preferences` |

---

## 3. 서버 저장 대상 (스토리지 결정)

| 데이터 | 저장 위치 | 사유 |
|---|---|---|
| 유저 프로필 · 커플 링크 · 일정 · 반복 규칙 · 사용자 표시 옵션 | **RDB (PostgreSQL)** | 관계형 · 트랜잭션 · 커플 단위 권한 검증이 잦음 |
| 프로필 사진 · 향후 일정 첨부 | **오브젝트 스토리지 (S3 계열)** + RDB에 URL만 | 바이너리 대용량 |
| 카카오 access/refresh 토큰 | **RDB (암호화)** or **KMS 보관 필드** | 재로그인/재발급용 |
| 공휴일·절기 데이터 | **클라이언트 캐시 + (선택) 서버 캐시 테이블** | 외부 API(data.go.kr) 원본, 서버가 프록시할지 결정 필요 |
| 앱 로그 · 세션 이벤트 | 별도 로깅 스택 (BigQuery/CloudWatch 등) | RDB 오염 방지 |

---

## 4. ERD (요약)

```
users ─────────< couple_members >───── couples
  │                                       │
  │                                       │
  └─< user_preferences                    ├─< schedules ─< schedule_repeat_rules
  │                                       │
  └─< user_auth_tokens                    └─< couple_anniversaries (미래)

special_days              (외부 API 캐시, 소유자 없음)
attachments ─┐
             └─> schedules (일정 첨부, 미래)
```

핵심 관계
- `users` ↔ `couples` : `couple_members` join. 한 유저는 최대 1개 활성 커플.
- `couples` → `schedules` : 모든 일정은 커플 단위. 단독 사용자여도 "혼자 커플(1인)" 로 시작할지, 커플 성립 이후에만 일정 저장할지 정책 결정 필요.
- `schedules` → `schedule_repeat_rules` : 1:1 옵셔널. 반복 없는 일정은 로우 없음.
- `schedules.owner_kind` : `me` / `partner` / `us` — "누구 소유의 일정인가" (수정·삭제 권한 계산에 사용).

---

## 5. 테이블 스키마 (PostgreSQL DDL 초안)

### 5.1 users

```sql
CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kakao_id          BIGINT UNIQUE NOT NULL,          -- 카카오 소셜 ID
    nickname          VARCHAR(30) NOT NULL,
    birth_date        DATE,                            -- 미입력 허용
    profile_image_url TEXT,                            -- S3 URL 또는 null
    calendar_color    VARCHAR(16) NOT NULL DEFAULT 'blue',
                                                       -- blue|mint|green|yellow|orange|pink|purple|gray
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.2 couples

```sql
CREATE TABLE couples (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invite_code  VARCHAR(12) UNIQUE NOT NULL,           -- 상대에게 공유할 초대 코드
    linked_at    TIMESTAMPTZ,                           -- 두 번째 멤버가 합류한 시각
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.3 couple_members

```sql
CREATE TABLE couple_members (
    couple_id   UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL DEFAULT 'member', -- 확장 여지: owner/member
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (couple_id, user_id)
);

-- 한 유저는 활성 커플 1개만
CREATE UNIQUE INDEX ux_couple_members_user ON couple_members(user_id);
```

### 5.4 schedules

```sql
CREATE TYPE schedule_type   AS ENUM ('task', 'schedule');
CREATE TYPE schedule_owner  AS ENUM ('me', 'partner', 'us');

CREATE TABLE schedules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id     UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    created_by    UUID NOT NULL REFERENCES users(id),
    type          schedule_type   NOT NULL,
    owner_kind    schedule_owner  NOT NULL,
    title         VARCHAR(200) NOT NULL,

    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,                       -- 종일/다일 지원
    all_day       BOOLEAN NOT NULL DEFAULT false,
    start_time    TIME,                                -- 5분 스텝. all_day 이거나 type=task 면 null
    end_time      TIME,

    is_done       BOOLEAN NOT NULL DEFAULT false,      -- type=task 일 때만 의미
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (start_date <= end_date),
    CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL)
    )
);

CREATE INDEX ix_schedules_couple_range ON schedules(couple_id, start_date, end_date);
CREATE INDEX ix_schedules_couple_type  ON schedules(couple_id, type);
```

> **owner_kind vs created_by**: `created_by` 는 감사(audit) 용, `owner_kind` 는 UI/권한용. `me/partner` 는 각 사용자 시점에 따라 뒤바뀌지 않고, 항상 "커플 내 어느 쪽인지" 를 뜻하도록 저장 (예: `owner_user_id` 로 두고 뷰 계산 시 요청자 관점에서 me/partner 를 도출하는 방안이 더 견고 — v1 대체안).

**대체안 (권장)**: `owner_kind` 컬럼을 `owner_user_id UUID NULL` (null = 공동) 로 대체하면 커플 내 관점 뒤집힘 없이 처리 가능. 초안에서는 클라 enum과 1:1 매핑을 위해 문자열로 두었으나, 서버 구현 시 이 방향으로 확정 권장.

### 5.5 schedule_repeat_rules

```sql
CREATE TYPE repeat_kind AS ENUM ('daily', 'weekly', 'monthly', 'yearly', 'custom');

CREATE TABLE schedule_repeat_rules (
    schedule_id   UUID PRIMARY KEY REFERENCES schedules(id) ON DELETE CASCADE,
    kind          repeat_kind NOT NULL,

    -- weekly: 요일 비트마스크 (0=일 … 6=토). 예: 월+수+금 = 0b0101010 = 42
    weekly_days   SMALLINT,

    -- monthly: 매월 N일
    monthly_day   SMALLINT,

    -- yearly: 매년 M월 D일
    yearly_month  SMALLINT,
    yearly_day    SMALLINT,

    -- custom: 아직 미정. 후속 이슈에서 rrule 문자열 등으로 확장
    custom_rule   TEXT,

    -- 반복 종료 조건 (미래)
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
```

### 5.6 user_preferences

`MainDrawer.DrawerDisplayState` 를 그대로 서버화. 다른 UX 세팅도 여기에 계속 붙임.

```sql
CREATE TABLE user_preferences (
    user_id                UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    show_my_calendar       BOOLEAN NOT NULL DEFAULT true,
    show_partner_calendar  BOOLEAN NOT NULL DEFAULT true,
    show_holidays          BOOLEAN NOT NULL DEFAULT true,
    show_solar_terms       BOOLEAN NOT NULL DEFAULT true,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.7 user_auth_tokens

```sql
CREATE TABLE user_auth_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(16) NOT NULL,             -- 'kakao'
    access_token_enc  BYTEA NOT NULL,                   -- KMS 로 암호화된 값 저장
    refresh_token_enc BYTEA,
    access_expires_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_user_auth_tokens_user ON user_auth_tokens(user_id, provider);
```

> 서비스 자체 세션은 JWT/Opaque token 어느 쪽으로 갈지 API 설계 시 확정. 위 테이블은 카카오 원본 토큰 저장용.

### 5.8 special_days (선택)

외부 API(data.go.kr) 를 서버가 프록시할 경우에만 도입. 프록시 안 하면 이 테이블 생략하고 클라 캐시만 유지.

```sql
CREATE TYPE special_day_kind AS ENUM ('holiday', 'solar_term');

CREATE TABLE special_days (
    date        DATE NOT NULL,
    kind        special_day_kind NOT NULL,
    label       VARCHAR(60) NOT NULL,                    -- "광복절", "입추" 등
    source      VARCHAR(32) NOT NULL DEFAULT 'data.go.kr',
    fetched_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (date, kind, label)
);

CREATE INDEX ix_special_days_year ON special_days(EXTRACT(YEAR FROM date));
```

### 5.9 attachments (미래)

```sql
CREATE TABLE attachments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    kind         VARCHAR(24) NOT NULL,        -- 'profile'|'schedule'
    url          TEXT NOT NULL,               -- S3 URL
    mime_type    VARCHAR(64),
    byte_size    BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE schedule_attachments (
    schedule_id   UUID NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    attachment_id UUID NOT NULL REFERENCES attachments(id) ON DELETE CASCADE,
    PRIMARY KEY (schedule_id, attachment_id)
);
```

### 5.10 couple_anniversaries (미래)

DayDetailSheet 스펙에 나오는 "결혼기념일" 등 커플 단위 반복 이벤트. 사실상 `schedules` 로 흡수 가능하지만, 별도 취급이 UX 상 낫다고 판단되면 분리.

---

## 6. 인덱스 · 제약 · 데이터 무결성 체크리스트

- `schedules` 는 **커플 단위 조회가 압도적** → `(couple_id, start_date, end_date)` 복합 인덱스 필수.
- 반복 일정의 실제 발생일 계산은 서버가 조회 시점에 rrule 로 확장. 발생일을 별도 테이블로 미리 굽는 방식은 v1에서는 지양 (데이터 뻥튀김).
- `couple_members` 는 한 유저당 활성 커플 1개 정책 → 유니크 인덱스로 보장.
- 커플 해제 흐름 (`unlink`) 시 스케줄 소유권 처리: 남길지 삭제할지 정책 필요 (초안: 커플 해제 시 커플 삭제 + `ON DELETE CASCADE` 로 일정 전부 삭제).
- 삭제된 항목 소프트 삭제 필요 여부 결정. (초안: 삭제 = 물리 삭제)

---

## 7. 클라이언트 ↔ 서버 매핑 예시

`ScheduleDraft` → `POST /schedules`

```json
{
  "type": "schedule",
  "owner_kind": "us",
  "title": "브런치 데이트",
  "start_date": "2026-08-04",
  "end_date":   "2026-08-04",
  "all_day":    false,
  "start_time": "10:00",
  "end_time":   "12:00",
  "repeat": {
    "kind": "weekly",
    "weekly_days": [1, 3, 5]
  }
}
```

`GET /couples/{id}/schedules?from=2026-08-01&to=2026-08-31`
→ 서버가 반복 규칙을 조회 범위 안에서 확장한 `TimedSchedule`/`AllDaySchedule`/`DayTask` 리스트를 날짜별로 그룹핑해 응답.

---

## 8. 아직 결정 못 한 것 (Open Questions)

1. `owner_kind` 를 enum 문자열로 둘지, `owner_user_id UUID NULL` (null=공동) 로 정규화할지 → **후자 권장**, 확정 필요.
2. 단일 사용자(커플 미연결) 상태에서 일정 저장 허용 여부. 허용한다면 "혼자 커플" 을 자동 생성해서 스키마 통일할지, 별도 `personal_schedules` 를 둘지.
3. 반복 일정 예외 처리 (특정 발생일만 수정/삭제) → v1 범위 여부.
4. 알림/푸시 스펙 (FCM/APNs) → 어느 시점의 일정에 대해 어떤 규칙으로 발송할지.
5. 공휴일/절기 서버 프록시 여부 (`special_days` 테이블 도입 여부).
6. 소프트 삭제 정책.
7. 파일 업로드 스토리지 (S3 vs 다른 CDN).

---

## 9. 다음 스텝

1. 위 Open Questions 1·2·5 를 먼저 확정 (스키마 골격에 영향).
2. 확정본으로 Flyway/Prisma 등 마이그레이션 스크립트 작성.
3. API 스펙 (REST or GraphQL) 확정 → `docs/api-design.md` 로 분리.
4. 인증/세션 흐름 확정 → `docs/auth-design.md` 로 분리.
