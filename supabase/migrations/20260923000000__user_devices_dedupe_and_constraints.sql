-- #355: user_devices 중복 iOS 토큰 정리 · 갱신 정책 강화
--
-- 배경: v1.4.1 배포 push 가 한 유저에게 2번 도착. 원인은 앱 재설치 · 재로그인 · 시뮬레이터 스왑
-- 시 새 FCM 토큰이 insert 될 뿐 이전 row 가 삭제되지 않아 (user_id, fcm_token) 단위 dedupe 만
-- 있던 스키마 하에서 무한 누적되던 것. broadcast fanout 이 user_id 로 모든 row 를 select 해
-- 발송하기 때문에 잔재 토큰 개수만큼 중복 발송.
--
-- 조치:
--  1. 기존 데이터 dedupe — (user_id, platform) 별 최신 1개, (fcm_token) 별 최신 1개만 유지.
--  2. unique(user_id, platform) 추가 — 유저 · 플랫폼당 활성 토큰 1개 강제.
--  3. unique(fcm_token) 추가 — 같은 물리 디바이스가 여러 계정에 잔재로 걸리지 않게 강제.
--  4. 기존 unique(user_id, fcm_token) 는 위 두 제약에 흡수되므로 제거.

-- 1. Dedupe per (user_id, platform)
with ranked as (
    select id,
           row_number() over (
               partition by user_id, platform
               order by updated_at desc, created_at desc, id desc
           ) as rn
    from public.user_devices
)
delete from public.user_devices
where id in (select id from ranked where rn > 1);

-- 2. Dedupe per fcm_token (계정 전환 잔재)
with ranked as (
    select id,
           row_number() over (
               partition by fcm_token
               order by updated_at desc, created_at desc, id desc
           ) as rn
    from public.user_devices
)
delete from public.user_devices
where id in (select id from ranked where rn > 1);

-- 3. Drop old constraint
alter table public.user_devices
    drop constraint if exists user_devices_user_id_fcm_token_key;

-- 4. Add new constraints
alter table public.user_devices
    add constraint user_devices_user_id_platform_key unique (user_id, platform);

alter table public.user_devices
    add constraint user_devices_fcm_token_key unique (fcm_token);
