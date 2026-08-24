# Supabase 세팅 가이드 (v0)

`#32` Supabase pivot · `#35` 프로젝트 세팅. Ktor + Neon + Render 폐기 이후 관리형 백엔드 온보딩 절차.

## 1. 왜 Supabase

- **관리형 Postgres** — Ktor 서버 · Cloud Run 배포 불필요, Neon 대체
- **PostgREST 자동 API** — 테이블마다 REST 엔드포인트 자동 생성 (`/rest/v1/schedules?...`). 서버측 CRUD 라우트 코드 폐기
- **Auth 카카오 provider 내장** — 서버측 카카오 토큰 검증 코드 폐기
- **RLS (Row Level Security)** — 권한 검증을 정책으로 (`auth.uid() = user_id`). 라우트별 JWT 미들웨어 대체
- **Realtime** — Postgres CDC → WebSocket 내장 (커플 캘린더 실시간 sync 이득)
- **Free tier** — 500MB · 5GB 대역폭 · 50K Auth MAU · 카드 등록 불필요. 우리 스케일 100% 커버
- **7일 무활동 시 pause** — Render Free 15분 sleep 대비 실질 문제 없음. 재활성화는 대시보드 클릭

## 2. 프로젝트 좌표 (완료)

| 항목 | 값 |
|---|---|
| Project ref | `guxpohhhacljwhyiskdk` |
| Project URL | `https://guxpohhhacljwhyiskdk.supabase.co` |
| Region | Northeast Asia (Seoul) |
| Plan | Free |
| Publishable key | `local.properties` 의 `supabase.publishableKey` (공개 · 클라이언트 임베드) |
| Auth callback | `https://guxpohhhacljwhyiskdk.supabase.co/auth/v1/callback` |

## 3. 스키마 이관 (완료)

이번 세팅은 Supabase MCP (`apply_migration`) 로 적용. 대시보드 SQL Editor 붙여넣기도 결과는 같음.

1. `supabase/schema.sql` → `init_schema` 마이그레이션으로 적용
2. 첫 `get_advisors(security)` 에서 3건 지적 → 아래 후속 마이그레이션 3개로 정리:
   - `harden_functions` — `touch_updated_at` search_path 고정, helper 함수 EXECUTE 회수 (**정책 오작동 유발**)
   - `restore_function_execute` — 위 회수로 RLS 가 마비돼 즉시 원복
   - `move_helpers_to_private` — 정석 fix. `private` 스키마를 만들어 helper 함수 이동 · 모든 정책의 참조 갱신 · 옛 `public.*` helper 제거
3. 재검사 결과 advisor 무경고, `SET LOCAL ROLE authenticated` 상태에서 정책 정상 동작
4. **Authentication → Users** 는 비어있어야 정상 (아직 로그인 안 함)

### 스키마 특이사항

- `users.id` 는 `auth.users.id` FK. 유저 계정 자체는 Supabase Auth 가 관리
- 신규 로그인 시 `private.handle_new_auth_user()` 트리거가 자동으로 `public.users` 껍데기 row 생성
- RLS 정책은 `private.my_couple_ids()` 함수 (`SECURITY DEFINER`) 로 재귀 회피. `private` 스키마는 PostgREST 노출 스키마 밖이라 `/rest/v1/rpc/...` 호출 불가
- `kakao_id` 는 별도 컬럼 없음. 필요 시 `auth.users.raw_user_meta_data->>'provider_id'` 조회

## 4. Kakao 카카오 로그인 provider (완료)

### Kakao Developers 콘솔

- REST API 키 (Linker 앱): `f333f96e63c3ca850e82430d7f7f6344`
- 카카오 로그인 → **활성화 ON**
- **Redirect URI**: `https://guxpohhhacljwhyiskdk.supabase.co/auth/v1/callback`
- **Client Secret**: 발급 · 활성화 완료 (Supabase 대시보드에만 존재)
- **동의항목**: 닉네임 (필수), 프로필 사진 (선택)

### Supabase 대시보드

- **Authentication → Providers → Kakao** 활성화
- Kakao Client ID: `f333f96e63c3ca850e82430d7f7f6344`
- Kakao Client Secret: (Kakao 콘솔에서 발급한 값)

## 5. 로컬 개발 세팅

`local.properties` (gitignored) 에 아래 2줄 추가:

```properties
supabase.url=https://guxpohhhacljwhyiskdk.supabase.co
supabase.publishableKey=sb_publishable_1O5ktPM0NZNlVg8lFWeA4w_Mae55UqH
```

`shared/build.gradle.kts` 가 이 값을 BuildConfig 로 주입해 클라이언트 초기화 코드에서 참조 (후속 이슈에서 세팅).

## 6. 후속 이슈 (`#32` 하위)

- **[다음]** supabase-kt 클라이언트 통합 — `shared` 모듈에 dependency + `SupabaseClient` 초기화
- 카카오 로그인 → `signInWith(Kakao)` (A안) 로 재작성 · 네이티브 SDK 코드 폐기
- 유저 프로필 CRUD, 커플 CRUD, 스케줄 CRUD, 기념일 CRUD (기존 #19~22 재정의)
- (선택) Realtime 구독 — 파트너 편집 즉시 반영

## 7. 트러블슈팅

| 증상 | 원인 · 해결 |
|---|---|
| SQL Editor 에서 `permission denied for table auth.users` | 자연스러움. RLS 정책은 정상. Table Editor 로 확인 |
| 첫 로그인 후 `public.users` 에 row 없음 | `handle_new_auth_user()` 트리거 실패. Supabase 대시보드 Database → Functions → Logs 확인 |
| 클라이언트에서 `permission denied` | 정책이 너무 타이트하거나 auth 세션 없음. supabase-kt 세션 상태 로그 확인 |
| 7일 pause 후 첫 요청 지연 | 정상. Supabase 콘솔 방문해서 unpause 하거나 첫 요청 시 자동 wake (~1분) |

## 8. 참고 링크

- [Supabase Auth Kakao provider](https://supabase.com/docs/guides/auth/social-login/auth-kakao)
- [supabase-kt (Kotlin Multiplatform)](https://github.com/supabase-community/supabase-kt)
- [PostgREST 쿼리 문법](https://postgrest.org/en/stable/references/api/tables_views.html)
- [Postgres RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)
