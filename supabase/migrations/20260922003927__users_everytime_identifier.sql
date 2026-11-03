-- 프로필에 에브리타임 시간표 공유 링크 식별자를 저장 (#306).
--
-- 정책:
-- - 유저는 자기 에브리타임 시간표 공유 URL (`https://everytime.kr/@<id>`) 의 `<id>` 부분만 저장.
--   URL 전체를 저장하지 않는 이유:
--     * 도메인 · 프로토콜은 상수 — 앱이 조립하는 쪽이 자연스럽고 마이그레이션에도 편함.
--     * DB 에는 요청에 넘길 payload 최소 단위 (identifier) 만 두는 게 표준.
-- - Everytime 은 identifier 로 20 자 안팎의 base62 문자열을 쓰지만 향후 여유 있게 32 자 상한.
-- - RLS 는 기존 `users_select_self_or_partner` (같은 커플이면 SELECT) 가 그대로 커버 →
--   파트너가 상대의 identifier 를 읽어 자기 앱에서 시간표를 조회할 수 있다. UPDATE 는 `users_update_self`
--   가 이미 본인만 허용.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS everytime_identifier VARCHAR(32);

-- 형식 가드: 앱이 사전 검증하지만, 빈 문자열이 들어오지 않도록 서버측 방어.
ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_everytime_identifier_format;
ALTER TABLE public.users
    ADD CONSTRAINT users_everytime_identifier_format
    CHECK (everytime_identifier IS NULL OR everytime_identifier ~ '^[A-Za-z0-9]{4,32}$');
