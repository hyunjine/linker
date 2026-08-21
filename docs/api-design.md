# Linker API 명세서 (v0)

> `docs/db-design.md` 초안 기반. 서버 착수 전 정리용. REST + JSON. 인증은 서비스 세션(Bearer) 을 표준으로 가정.
>
> 확정본 아님. Open Question(§8) 은 논의 후 확정.

---

## 1. 공통 규약

### 1.1 Base

- **Base URL** (예): `https://api.linker.app/v1`
- **Content-Type**: `application/json; charset=utf-8`
- **인코딩**: UTF-8
- **시간대**: 모든 timestamp 는 ISO-8601 UTC (`2026-08-21T05:12:34Z`), 날짜는 `YYYY-MM-DD`, 시각은 `HH:MM` (24h, 5분 스텝).
- **버저닝**: URL prefix `/v1`. 파괴적 변경은 `/v2` 로 신설.

### 1.2 인증

- 로그인 이후 발급된 **서비스 세션 토큰** (JWT or opaque) 을 `Authorization` 헤더로 전달.
  ```
  Authorization: Bearer <session_token>
  ```
- 카카오 access token 은 **직접 API 인증에 사용하지 않음** — 오직 `POST /auth/kakao` 교환에서만 사용.

### 1.3 페이지네이션 (필요 시)

- 커서 기반 표준.
  - 요청: `?cursor=<opaque>&limit=<n>`
  - 응답: `{ "items": [...], "next_cursor": "..." | null }`

### 1.4 에러 응답

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "start_date must be <= end_date",
    "details": { "field": "start_date" }
  }
}
```

| HTTP | code (예) | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 payload 검증 실패 |
| 401 | `UNAUTHENTICATED` | 세션 없음/만료 |
| 403 | `FORBIDDEN` | 리소스 접근 권한 없음 (커플 소속 아님 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 상태 충돌 (이미 커플 연결됨, 초대 코드 중복 등) |
| 422 | `UNPROCESSABLE` | 비즈니스 규칙 위반 |
| 429 | `RATE_LIMITED` | 스로틀 |
| 500 | `INTERNAL` | 서버 오류 |

### 1.5 리소스 표기 규칙

- 모든 ID 는 UUID (`3c3f7aa9-e737-...`)
- Enum snake_case (`task` / `schedule`, `me` / `partner` / `us`)
- 필드명 snake_case
- 컬렉션은 복수형 (`/schedules`, `/couples`)

### 1.6 표준 필드

- `created_at` / `updated_at` : 응답에만 등장. 요청 payload 로 받지 않음.
- 서버 계산 필드 (`is_owner`, `is_editable`) 는 요청자 관점에서 응답에 포함.

---

## 2. 엔드포인트 요약

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST   | `/auth/kakao` | ❌ | 카카오 access token → 서비스 세션 교환 (미가입 시 유저 shell 자동 생성) |
| POST   | `/auth/refresh` | ✅ (refresh) | 세션 갱신 |
| POST   | `/auth/logout` | ✅ | 서버 세션 무효화 |
| GET    | `/users/me` | ✅ | 내 프로필 |
| POST   | `/users/me/profile` | ✅ | **최초 프로필 완성 (회원가입 마무리)** |
| PATCH  | `/users/me` | ✅ | 프로필 수정 (완성 이후) |
| GET    | `/users/me/preferences` | ✅ | 표시 옵션 조회 |
| PATCH  | `/users/me/preferences` | ✅ | 표시 옵션 수정 |
| POST   | `/uploads/profile-image` | ✅ | 프로필 이미지 업로드 (multipart) |
| POST   | `/couples` | ✅ | 커플 생성 (내 쪽) + 초대 코드 발급 |
| POST   | `/couples/join` | ✅ | 파트너 초대 코드로 합류 |
| GET    | `/couples/me` | ✅ | 내 활성 커플 조회 |
| PATCH  | `/couples/me` | ✅ | 커플 표시 이름 · 시작일 수정 |
| DELETE | `/couples/me` | ✅ | 커플 해제 |
| GET    | `/couples/me/anniversaries` | ✅ | 기념일 목록 |
| POST   | `/couples/me/anniversaries` | ✅ | 기념일 등록 |
| PATCH  | `/anniversaries/{id}` | ✅ | 기념일 수정 |
| DELETE | `/anniversaries/{id}` | ✅ | 기념일 삭제 |
| GET    | `/couples/me/schedules` | ✅ | 기간 조회 (반복 확장) |
| POST   | `/couples/me/schedules` | ✅ | 일정 생성 |
| GET    | `/schedules/{id}` | ✅ | 일정 단건 조회 |
| PATCH  | `/schedules/{id}` | ✅ | 일정 수정 |
| DELETE | `/schedules/{id}` | ✅ | 일정 삭제 |
| PATCH  | `/schedules/{id}/done` | ✅ | 할 일 완료 토글 |
| GET    | `/special-days` | ✅ | 공휴일/절기 (선택: 서버 프록시 시) |

---

## 3. Auth

### 3.1 카카오 로그인 교환

`POST /auth/kakao`

카카오 SDK 로 로그인 후 획득한 카카오 access token 을 서비스 세션으로 교환.

- **미가입**: 유저 **shell** 을 자동 생성 (kakao_id 만 채워진 최소 레코드, `is_profile_complete=false`).
- **가입은 되어있지만 프로필 미완성**: shell 유지, `is_profile_complete=false`.
- **가입 + 프로필 완성**: 정상 세션 발급, `is_profile_complete=true`.

클라는 응답의 `is_profile_complete` / `couple` 유무를 보고 다음 화면을 결정한다 (§3.4 흐름 요약 참고).

**Request**
```json
{
  "kakao_access_token": "<카카오에서 받은 토큰>"
}
```

**Response 200**
```json
{
  "session": {
    "access_token": "<서비스 세션 토큰>",
    "refresh_token": "<서비스 리프레시 토큰>",
    "access_expires_at": "2026-08-21T06:12:34Z"
  },
  "user": {
    "id": "3c3f7aa9-...",
    "kakao_id": 123456789,
    "nickname": "현진",
    "birth_date": null,
    "profile_image_url": null,
    "calendar_color": "blue",
    "created_at": "2026-08-21T05:12:34Z",
    "updated_at": "2026-08-21T05:12:34Z",
    "is_new": true,
    "is_profile_complete": false
  },
  "couple": null
}
```

- `user.is_new = true` : 이번 요청에서 shell 이 새로 생성됨.
- `user.is_profile_complete = false` : 프로필 필수값 미충족 → 클라는 프로필 설정 화면(`POST /users/me/profile`) 으로 유도.
- `couple = null` : 커플 미연결 → 클라는 커플 링크 화면으로 유도.

**에러**
- 401 `INVALID_KAKAO_TOKEN` : 카카오 토큰 검증 실패
- 500 `INTERNAL` : 카카오 API 장애

### 3.2 세션 갱신

`POST /auth/refresh`

**Request**
```json
{ "refresh_token": "..." }
```

**Response 200**
```json
{
  "access_token": "...",
  "refresh_token": "...",
  "access_expires_at": "2026-08-21T07:12:34Z"
}
```

### 3.3 로그아웃

`POST /auth/logout` → `204 No Content`. 서비스 세션 무효화. 카카오 unlink 는 별도 이슈.

### 3.4 온보딩 라우팅 흐름 (요약)

카카오 로그인 응답을 기반으로 클라가 시작 라우트를 결정.

| `is_profile_complete` | `couple` | 다음 화면 |
|---|---|---|
| `false` | `null` | `ProfileSetupRoute` |
| `true`  | `null` | `CoupleLinkRoute` |
| `true`  | 존재    | `MainRoute` |
| `false` | 존재    | (비정상) `ProfileSetupRoute` 로 강제 유도 |

---

## 4. Users

### 4.1 내 프로필 조회

`GET /users/me` → 200

```json
{
  "id": "3c3f7aa9-...",
  "kakao_id": 123456789,
  "nickname": "현진",
  "birth_date": "1998-05-24",
  "profile_image_url": "https://cdn.linker.app/u/…jpg",
  "calendar_color": "blue",
  "is_profile_complete": true,
  "profile_completed_at": "2026-08-21T05:20:00Z",
  "created_at": "...",
  "updated_at": "..."
}
```

- `is_profile_complete` : `nickname`·`birth_date`·`calendar_color` 필수값이 모두 채워졌는지 서버 계산.
- `profile_completed_at` : `POST /users/me/profile` 로 최초 완성된 시각. 완성 전이면 `null`.

### 4.2 최초 프로필 완성 (회원가입 마무리)

`POST /users/me/profile`

카카오 로그인 후 shell 만 있는 상태의 유저가 필수값을 채워 **회원가입을 완결짓는 전용 엔드포인트**. 클라의 `ProfileSetupScreen` 저장 버튼과 매핑.

**Request** — 모든 필드 **필수**.
```json
{
  "nickname": "현진",
  "birth_date": "1998-05-24",
  "calendar_color": "blue",
  "profile_image_url": "https://cdn.linker.app/u/…jpg"
}
```

- `profile_image_url` 은 §4.5 업로드 엔드포인트에서 반환된 URL. 프로필 이미지 미설정 상태로 완성하려면 `null` 허용 여부는 §8 결정.

**Response 201** — 완성된 프로필 전체 (`is_profile_complete=true`, `profile_completed_at` 세팅).

**에러**
- 400 `VALIDATION_FAILED` : 필수값 누락 or 포맷 오류 (닉네임 길이, 미래 생년월일 등)
- 409 `ALREADY_COMPLETED` : 이미 완성된 프로필이라 재호출 불가. 수정은 `PATCH /users/me`.

### 4.3 프로필 수정

`PATCH /users/me`

**완성 이후** 사용자가 프로필 편집 화면에서 값을 바꿀 때 호출. 부분 업데이트.

```json
{
  "nickname": "현진",
  "birth_date": "1998-05-24",
  "calendar_color": "mint",
  "profile_image_url": "https://cdn.linker.app/u/…jpg"
}
```

**Response 200**: 수정 후 프로필 전체.

**에러**
- 409 `PROFILE_NOT_COMPLETED` : 아직 최초 완성 안 됨 → `POST /users/me/profile` 을 먼저 호출해야 함.

### 4.4 표시 옵션 조회 / 수정

`GET /users/me/preferences` → 200

```json
{
  "show_my_calendar": true,
  "show_partner_calendar": true,
  "show_holidays": true,
  "show_solar_terms": true,
  "show_lunar": false,
  "updated_at": "..."
}
```

`PATCH /users/me/preferences` — 위 필드 부분 업데이트.

### 4.5 프로필 이미지 업로드

`POST /uploads/profile-image`

`multipart/form-data`, 필드명 `image` (jpg/png, 최대 5MB).

**Response 201**
```json
{
  "url": "https://cdn.linker.app/u/3c3f7aa9-abc.jpg",
  "byte_size": 148231,
  "mime_type": "image/jpeg"
}
```

- 반환된 `url` 을 `POST /users/me/profile` 또는 `PATCH /users/me` payload 의 `profile_image_url` 에 넣어 최종 커밋.
- 대안: `GET /uploads/profile-image/presigned` 로 pre-signed URL 을 발급받아 클라가 S3/GCS 에 직접 PUT 하는 방식 — §8 결정.

**에러**
- 400 `INVALID_MIME` : 지원하지 않는 mime
- 413 `PAYLOAD_TOO_LARGE` : 5MB 초과

---

## 5. Couples

### 5.1 커플 생성 (초대 코드 발급)

`POST /couples` — 아직 커플에 소속되지 않은 유저가 호출. 초대 코드를 만들고 나 혼자만 있는 커플 셀 생성.

**Request**: `{}` (payload 없음)

**Response 201**
```json
{
  "id": "cpl-...",
  "invite_code": "ABC-123",
  "display_name": "현진이와 민교",
  "start_date": null,
  "linked_at": null,
  "members": [
    { "user_id": "3c3f7aa9-...", "joined_at": "...", "role": "owner" }
  ],
  "created_at": "..."
}
```

- `display_name` : 로그인 화면·드로워 등에서 표시하는 커플 이름 (예: "현진이와 민교"). 서버가 두 멤버 닉네임으로 자동 생성. 편집은 §5.5.
- `start_date` : 커플 사귄 날짜 (기념일 계산의 기준). 초기 `null`, `PATCH /couples/me` 로 세팅.

**에러**: 409 `ALREADY_IN_COUPLE`

### 5.2 커플 합류

`POST /couples/join`

```json
{ "invite_code": "ABC-123" }
```

**Response 200**: 합류된 커플 전체.
```json
{
  "id": "cpl-...",
  "invite_code": "ABC-123",
  "display_name": "현진이와 민교",
  "start_date": null,
  "linked_at": "2026-08-21T05:20:00Z",
  "members": [
    { "user_id": "u1-...", "joined_at": "..." },
    { "user_id": "u2-...", "joined_at": "2026-08-21T05:20:00Z" }
  ]
}
```

**에러**
- 404 `INVITE_NOT_FOUND`
- 409 `ALREADY_IN_COUPLE`
- 409 `COUPLE_FULL`

### 5.3 내 커플 조회

`GET /couples/me` → 200 or 404. 응답 스키마는 §5.2 와 동일.

### 5.4 커플 해제

`DELETE /couples/me` → 204.

- 커플 하위 모든 일정·기념일은 정책상 **삭제** (스키마 `ON DELETE CASCADE`). soft-delete 정책 도입 시 변경.

### 5.5 커플 메타 수정

`PATCH /couples/me`

`display_name` · `start_date` 부분 업데이트.

```json
{
  "display_name": "현진 ❤ 민교",
  "start_date": "2024-02-14"
}
```

**Response 200**: 수정된 커플 전체.

**에러**
- 400 `VALIDATION_FAILED` : `start_date` 가 미래 등
- 403 `FORBIDDEN` : 커플 소속 아님

### 5.6 커플 기념일 관리

`GET /couples/me/anniversaries` → 200

```json
{
  "items": [
    {
      "id": "ann-...",
      "title": "결혼기념일",
      "date": "2024-05-24",
      "repeat_yearly": true,
      "created_at": "..."
    },
    {
      "id": "ann-...",
      "title": "100일",
      "date": "2024-05-24",
      "repeat_yearly": false,
      "created_at": "..."
    }
  ]
}
```

`POST /couples/me/anniversaries`

```json
{
  "title": "결혼기념일",
  "date": "2024-05-24",
  "repeat_yearly": true
}
```

**Response 201**: 생성된 기념일.

`PATCH /anniversaries/{id}` — 부분 업데이트.
`DELETE /anniversaries/{id}` → 204.

- 캘린더 조회 응답(`GET /couples/me/schedules`) 에는 이 기념일이 **`type=schedule`, `all_day=true` 로 통합돼서 내려올지**, 별도 배열로 내려올지 §8 결정.

---

## 6. Schedules

일정은 **커플 단위** 로 소유. 커플 미연결 유저의 일정 저장 정책은 §8 참고.

### 6.1 기간 조회 (반복 확장)

`GET /couples/me/schedules?from=YYYY-MM-DD&to=YYYY-MM-DD`

- `from` ~ `to` 범위 내에서 반복 규칙을 서버가 확장해서 실제 발생일별로 리턴.
- 최대 조회 범위: 12개월 (초과 시 400).

**Response 200**
```json
{
  "range": { "from": "2026-08-01", "to": "2026-08-31" },
  "items": [
    {
      "id": "sch-...",
      "occurrence_date": "2026-08-04",
      "series_id": "sch-...",
      "type": "schedule",
      "owner_kind": "us",
      "title": "브런치 데이트",
      "start_date": "2026-08-04",
      "end_date": "2026-08-04",
      "all_day": false,
      "start_time": "10:00",
      "end_time": "12:00",
      "is_done": false,
      "repeat": null,
      "created_by": "u1-...",
      "is_editable": true,
      "created_at": "...",
      "updated_at": "..."
    },
    {
      "id": "sch-abc",
      "occurrence_date": "2026-08-05",
      "series_id": "sch-abc",
      "type": "task",
      "owner_kind": "me",
      "title": "항공권 예약하기",
      "start_date": "2026-08-05",
      "end_date": "2026-08-05",
      "all_day": true,
      "start_time": null,
      "end_time": null,
      "is_done": false,
      "repeat": null,
      "is_editable": true,
      "created_at": "...",
      "updated_at": "..."
    }
  ]
}
```

- `series_id` : 반복 원본 스케줄 id. 단일(비반복) 은 `id == series_id`.
- `occurrence_date` : 반복 확장으로 계산된 발생일. 비반복은 `start_date` 와 동일.
- `is_editable` : 요청자 관점 편집 가능 여부 (`owner_kind=partner` 이면 false).

### 6.2 일정 생성

`POST /couples/me/schedules`

**Request** (클라 `ScheduleDraft` 매핑)
```json
{
  "type": "schedule",
  "owner_kind": "us",
  "title": "브런치 데이트",
  "start_date": "2026-08-04",
  "end_date": "2026-08-04",
  "all_day": false,
  "start_time": "10:00",
  "end_time": "12:00",
  "repeat": {
    "kind": "weekly",
    "weekly_days": [1, 3, 5]
  }
}
```

**`repeat` 스키마**
```jsonc
// 없음
null
// 매일
{ "kind": "daily" }
// 매주 (0=일 … 6=토)
{ "kind": "weekly",  "weekly_days": [1,3,5] }
// 매월 N일
{ "kind": "monthly", "monthly_day": 24 }
// 매년 M월 D일
{ "kind": "yearly",  "yearly_month": 5, "yearly_day": 24 }
// 커스텀 (rrule 문자열; v1 후속)
{ "kind": "custom",  "custom_rule": "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE" }
```

- 종료 조건 (`ends_at`, `max_count`) 는 v1 후속.

**Response 201** — 생성된 스케줄 (반복 원본, `occurrence_date` 없음).

**에러**
- 400 `VALIDATION_FAILED` : `start_date > end_date`, `type=task` 인데 `start_time` 지정 등
- 403 `FORBIDDEN` : 커플 소속 아님

### 6.3 단건 조회

`GET /schedules/{id}` → 200. 반복 원본 리소스.

### 6.4 수정

`PATCH /schedules/{id}` — 부분 업데이트.

- 반복 규칙만 변경, 시각만 변경 등 모두 개별 필드로 처리.
- 반복 원본 수정 시 이후 모든 발생일에 반영. **특정 발생일만 수정** 은 v1 후속 (`occurrence exception` 테이블 필요).

**Response 200**: 수정 후 스케줄.

### 6.5 삭제

`DELETE /schedules/{id}` → 204.

- 반복 원본 삭제 시 이후 모든 발생일 함께 사라짐. 특정 발생일만 삭제는 v1 후속.

### 6.6 할 일 완료 토글

`PATCH /schedules/{id}/done`

```json
{ "is_done": true }
```

- `type=task` 인 스케줄에만 유효. 아니면 422 `NOT_A_TASK`.

**Response 200**: 수정 후 스케줄.

---

## 7. Special Days (선택)

서버가 공휴일/절기 API 를 프록시할 경우.

`GET /special-days?year=2026&kinds=holiday,solar_term`

**Response 200**
```json
{
  "items": [
    { "date": "2026-08-15", "kind": "holiday",     "label": "광복절" },
    { "date": "2026-08-23", "kind": "solar_term",  "label": "처서" }
  ],
  "source": "data.go.kr",
  "fetched_at": "2026-08-01T00:00:00Z"
}
```

- 프록시 안 하기로 결정하면 이 엔드포인트 삭제, 클라가 직접 API 호출 유지 (현 구조).

---

## 8. Open Questions

1. **커플 미연결 상태의 일정 저장 허용 여부** — 허용 시 "혼자 커플(1인)" 을 자동 생성해 `/couples/me/schedules` 로 통일할지, 별도 `/users/me/schedules` 를 둘지.
2. **`owner_kind` vs `owner_user_id`** — DB 초안대로 enum 유지할지, 정규화(`owner_user_id NULL = us`) 후 응답에서 요청자 관점의 `owner_kind` 를 계산해 내려줄지. → 후자 권장.
3. **반복 예외** (특정 발생일만 수정/삭제) v1 포함 여부.
4. **알림/푸시** 스펙 — 별도 문서 `docs/notification-design.md` 로.
5. **프로필 이미지 업로드 방식** — 서버 경유 multipart (§4.5) vs pre-signed URL 로 클라 직접 PUT. 후자가 서버 부하·비용 유리.
6. **프로필 완성 필수값** — `profile_image_url` 을 필수로 강제할지 vs 옵션.
7. **세션 토큰 형태** — JWT (stateless) vs opaque + Redis (revoke 쉬움).
8. **레이트리밋 정책** — 인증/조회/쓰기 각각.
9. **공휴일/절기 서버 프록시** 여부.
10. **기념일 vs 스케줄 통합** — `anniversaries` 를 별도 리소스로 유지할지, `schedules(type='schedule', all_day=true, repeat=yearly)` 로 흡수할지. 캘린더 조회 시 어떤 필드로 내려줄지.
11. **커플 `display_name` 자동 생성 규칙** — "A와 B" vs "A ❤ B" vs 사용자 첫 입력 유도.

---

## 9. 다음 스텝

1. §8 확정 → 스키마·엔드포인트 finalize
2. OpenAPI 3.1 스펙 파일 (`docs/openapi.yaml`) 생성 → 서버·클라 codegen
3. 인증 흐름 상세 → `docs/auth-design.md`
4. 파일 업로드 스펙 → `docs/uploads-design.md`
5. 푸시 알림 스펙 → `docs/notification-design.md`
