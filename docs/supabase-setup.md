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
- **Authentication → URL Configuration → Additional Redirect URLs** 에 아래 두 개 추가 (앱 딥링크):
  - `com.hyunjine.linker://auth-callback`
  - `com.hyunjine.linker://auth-callback/**` (와일드카드가 필요한 경우 대비)

## 5. 로컬 개발 세팅

`local.properties` (gitignored) 에 아래 2줄 추가:

```properties
supabase.url=https://guxpohhhacljwhyiskdk.supabase.co
supabase.publishableKey=sb_publishable_1O5ktPM0NZNlVg8lFWeA4w_Mae55UqH
```

`shared/build.gradle.kts` 가 이 값을 BuildConfig 로 주입해 클라이언트 초기화 코드에서 참조 (후속 이슈에서 세팅).

## 6. 후속 이슈 (`#32` 하위)

- ✅ `#37` supabase-kt 클라이언트 통합 완료 (`shared` 모듈)
- ✅ `#39` 카카오 로그인 A안 재작성 완료 (`signInWith(Kakao)` · 네이티브 SDK 폐기)
- 유저 프로필 CRUD, 커플 CRUD, 스케줄 CRUD, 기념일 CRUD (기존 #19~22 재정의)
- (선택) Realtime 구독 — 파트너 편집 즉시 반영

## 7. Keep-alive — 슬립 방지 크론 (`#72`)

Free tier 는 **7일간 요청이 없으면 프로젝트가 자동 pause**. 로그인·데이터 API 가 갑자기 죽는 걸 방지하려고 [cron-job.org](https://cron-job.org) 로 3일마다 REST 엔드포인트에 GET 을 날린다.

**왜 GitHub Actions 아닌 cron-job.org?**
공개 저장소의 스케줄 워크플로는 **60일간 커밋 없으면 자동 비활성화**. 유지보수 모드로 넘어가면 슬립 방지 자체가 슬립됨. cron-job.org 는 리포 활동과 무관.

### 세팅 절차

1. https://cron-job.org 가입 (구글/깃허브 OAuth)
2. **Create cronjob** →
   - **Title**: `Linker Supabase keep-alive`
   - **URL**: `https://<project>.supabase.co/auth/v1/settings`
   - **Schedule**: Every 3 days (Custom → 매 3일 0시)
   - **Request method**: GET
   - **Advanced → Headers**:
     - `apikey: <local.properties 의 supabase.publishableKey>`
   - **Notifications**: 실패 시 이메일 알림 ON
3. Save → **Test run** 으로 200 응답 확인 (활성 auth provider 목록 JSON 이 나오면 정상)
4. 결과 로그를 `#72` 이슈에 코멘트로 남겨 팀 공유

### 왜 3일 · 왜 `/auth/v1/settings`

- 3일 = 7일 pause 임계값 아래 안전 마진. 하나 걸러도 죽지 않음
- `/auth/v1/settings` 는 publishable key 만으로 200 나오는 가장 가벼운 엔드포인트
  - `/rest/v1/` 루트는 secret API key 를 요구해서 publishable 로는 401 — 크론에 secret 을 주는 건 위험하니 피함

## 8. 트러블슈팅

| 증상 | 원인 · 해결 |
|---|---|
| SQL Editor 에서 `permission denied for table auth.users` | 자연스러움. RLS 정책은 정상. Table Editor 로 확인 |
| 첫 로그인 후 `public.users` 에 row 없음 | `handle_new_auth_user()` 트리거 실패. Supabase 대시보드 Database → Functions → Logs 확인 |
| 클라이언트에서 `permission denied` | 정책이 너무 타이트하거나 auth 세션 없음. supabase-kt 세션 상태 로그 확인 |
| 7일 pause 후 첫 요청 지연 | 정상. Supabase 콘솔 방문해서 unpause 하거나 첫 요청 시 자동 wake (~1분) |

## 9. 참고 링크

- [Supabase Auth Kakao provider](https://supabase.com/docs/guides/auth/social-login/auth-kakao)
- [supabase-kt (Kotlin Multiplatform)](https://github.com/supabase-community/supabase-kt)
- [PostgREST 쿼리 문법](https://postgrest.org/en/stable/references/api/tables_views.html)
- [Postgres RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)
