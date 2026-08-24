# schema/

Supabase 로 pivot (#32) 하기 전 Ktor + Flyway 시절의 마지막 스키마 스냅샷을 보관한다.

- `V1__init.sql` — 원본 Flyway 마이그레이션. 다음 이슈에서 이 파일을 기반으로 Supabase SQL Editor 에 이관 (+ RLS 정책 추가) 예정.
- 이관 완료 후에도 참고용으로 유지. Supabase 쪽 마이그레이션은 별도 위치 (예: `supabase/migrations/`) 에 관리.
