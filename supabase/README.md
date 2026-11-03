# supabase/

Supabase 프로젝트 (linker · Northeast Asia · Seoul · Free) 스키마 · 정책 소스.

## 파일

- `schema.sql` — 초기 스키마 (테이블 + enum + 트리거 + RLS 정책) 스냅샷. 원격에 적용된 원본은 `migrations/20260824063815__init_schema.sql` (#430 에서 ledger 로부터 복원).
- `migrations/` — 프로덕션 ledger (`supabase_migrations.schema_migrations`) 와 **1:1** 대응하는 마이그레이션 전체. 파일명 `<version>__<이름>.sql` 의 version 이 ledger version 과 같아야 한다. 처음부터 순서대로 적용하면 스키마 재구성 가능.

## 최초 세팅 (이력)

`docs/supabase-setup.md` 참고. 요약:
1. Supabase 프로젝트 생성 (완료: `guxpohhhacljwhyiskdk`)
2. Kakao provider 활성화 (완료)
3. `schema.sql` 적용 — 당시엔 Supabase MCP (`apply_migration`) 로 처리 (#430 이후로는 CI 로만 적용)
4. 7개 테이블 · RLS 활성화 확인 (users, couples, couple_members, schedules, schedule_repeat_rules, user_preferences, couple_anniversaries)
5. Security advisor 지적 정리 (아래 `migrations/` 로그 참조)

## 현재 상태

- 스키마: `public` 7개 테이블 + `private` 스키마 1개 (헬퍼 함수 격리 목적)
- Helper 함수 (`private.my_couple_ids`, `private.handle_new_auth_user`) 는 PostgREST 노출 스키마 밖에 있어 `/rest/v1/rpc/...` 로 호출 불가
- `get_advisors(security)` 무경고

## 마이그레이션 적용 흐름 (CI · #430)

프로덕션 DB 에 마이그레이션을 적용하는 경로는 **`.github/workflows/supabase-migrate.yml` 하나뿐**이다.
Supabase MCP `apply_migration` · 대시보드 SQL Editor · 로컬 `supabase db push` 로 프로덕션에 직접 적용 **금지**.
(직접 적용하면 ledger 에 repo 에 없는 version 이 생겨 다음 CI 실행이 실패한다 — 과거 MCP 적용분이 그렇게 어긋났었음.)

1. **파일 작성** — 새 timestamp 로 파일 생성. `supabase migration new <설명>` 을 쓰거나 직접
   `migrations/<yyyyMMddHHmmss>__<설명>.sql` (UTC, 기존 최신 version 보다 커야 함) 로 만든다.
   기존 마이그레이션 파일 · `schema.sql` 은 **수정 금지** (이미 적용된 이력).
2. **PR** — `dev` 대상 PR. 리뷰 시 SQL 이 아래 안전 규칙을 지키는지 확인.
   로컬에서 `supabase db push --dry-run --linked` 로 "이 파일 하나만 적용 대상" 인지 확인 가능 (읽기 전용).
3. **dev 머지** — `supabase/migrations/**` 변경이 push 되면 `Supabase Migrate` 워크플로가 대기 상태로 뜬다.
4. **승인** — GitHub Actions → 해당 run → *Review deployments* → `production` 승인 (required reviewer: 오너).
   승인 전엔 job 이 시작되지 않고 시크릿도 노출되지 않는다.
5. **CI 적용** — `supabase migration list` → `db push --dry-run` → `db push` → `migration list` 순으로 실행. 로그로 결과 확인.

수동 재시도는 Actions → *Supabase Migrate* → *Run workflow* (`dev` 브랜치에서만 가능, 역시 승인 필요).
Edge Function 배포 (`supabase-deploy.yml`) 도 같은 `production` 승인 게이트를 거친다.

### 안전 규칙

- **additive · 재실행 안전하게** — `create ... if not exists`, `add column if not exists`, `create or replace function`, `drop ... if exists` 위주.
  컬럼 삭제 · 타입 축소 · rename 은 구버전 앱이 깨지므로 별도 단계로 나눈다 (앱이 더 이상 안 쓰는 게 확인된 뒤 제거).
- **앱 릴리스 순서** — 새 컬럼 · 테이블 추가 (additive) 는 앱보다 먼저 적용해도 된다.
  반대로 스키마 **강화** (NOT NULL · unique · check constraint 추가, `onConflict` 대상 변경 등) 는
  **그걸 전제로 하는 앱이 배포된 뒤** 별도 마이그레이션 PR 로 적용. 순서가 뒤집히면 예전 앱이 `42P10` 등으로 실패한다.
- 한 마이그레이션 = 한 목적. 데이터 backfill 이 크면 별도 파일로 분리.
- RLS · `security definer` 함수는 `search_path` 고정 · `revoke ... from public` 까지 같은 파일에 포함.
- 마이그레이션이 실패하면 `db push` 는 그 파일에서 멈추고 ledger 에 기록하지 않는다. 원인 수정 PR 후 다시 승인 · 적용.
  (이미 ledger 에 기록된 = 성공한 파일은 절대 수정하지 말고 새 파일로 보정.)

## 참고

- 원본 Ktor + Flyway 스키마: `docs/schema/V1__init.sql` (역사 스냅샷, 실행 대상 아님)
- 스키마 차이 · 설계 근거: `docs/db-design.md`
