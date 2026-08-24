# schema/

Ktor + Flyway 시절 스키마 스냅샷 (역사 보관용).

- `V1__init.sql` — 원본 Flyway 마이그레이션. **실행 대상 아님**. 현재 활성 스키마는 `supabase/schema.sql` 로 이관 완료 (#35).
- 참고용으로만 유지. 실제 스키마 변경은 `supabase/schema.sql` · `supabase/migrations/` 에서 관리.
