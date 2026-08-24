# supabase/

Supabase 프로젝트 (linker · Northeast Asia · Seoul · Free) 스키마 · 정책 소스.

## 파일

- `schema.sql` — 초기 스키마 (테이블 + enum + 트리거 + RLS 정책). 원격 프로젝트에 이미 `init_schema` 마이그레이션으로 적용 완료.
- `migrations/` — schema.sql 이후 변경분. 파일명 = 원격에 기록되는 마이그레이션 이름과 1:1 대응 (`<yyyyMMddHHmmss>__<이름>.sql`, **UTC**).

## 최초 세팅 (이력)

`docs/supabase-setup.md` 참고. 요약:
1. Supabase 프로젝트 생성 (완료: `guxpohhhacljwhyiskdk`)
2. Kakao provider 활성화 (완료)
3. `schema.sql` 적용 — 이번 세팅은 Supabase MCP (`apply_migration`) 로 처리. SQL Editor 붙여넣기도 동일한 효과
4. 7개 테이블 · RLS 활성화 확인 (users, couples, couple_members, schedules, schedule_repeat_rules, user_preferences, couple_anniversaries)
5. Security advisor 지적 정리 (아래 `migrations/` 로그 참조)

## 현재 상태

- 스키마: `public` 7개 테이블 + `private` 스키마 1개 (헬퍼 함수 격리 목적)
- Helper 함수 (`private.my_couple_ids`, `private.handle_new_auth_user`) 는 PostgREST 노출 스키마 밖에 있어 `/rest/v1/rpc/...` 로 호출 불가
- `get_advisors(security)` 무경고

## 후속 마이그레이션 규칙

- `migrations/<yyyyMMddHHmmss>__<설명>.sql` 형태로 새 파일 추가 (UTC 기준 · 원격 레코드와 일치)
- 기존 `schema.sql` · 이전 마이그레이션 파일은 **수정 금지** (Ktor Flyway 시절 정책 계승)
- 적용은 (지금) MCP `apply_migration` 또는 대시보드 SQL Editor. Supabase CLI 도입 시 `supabase db push` 로 자동화

## 참고

- 원본 Ktor + Flyway 스키마: `docs/schema/V1__init.sql` (역사 스냅샷, 실행 대상 아님)
- 스키마 차이 · 설계 근거: `docs/db-design.md`
