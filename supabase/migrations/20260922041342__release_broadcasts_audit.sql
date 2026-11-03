-- release note 브로드캐스트 감사 로그 · idempotency 키 (#300).
--
-- 목적:
--   - 배포마다 어떤 version 이 · 언제 · 몇 개 device 로 · 몇 건 성공/실패했는지 영구 기록.
--   - version PK 로 idempotency — 같은 버전에 대해 워크플로 재실행이 발생해도 이 테이블에
--     이미 completed_at 이 있으면 함수가 재발송 대신 기존 결과를 반환.
--   - request_id 를 발급해 워크플로 · 함수 로그 · 이 테이블을 삼중 상관 (grep 한 방).
--
-- 접근 정책:
--   - Edge Function 은 SERVICE_ROLE 로 삽입/갱신 (RLS 우회).
--   - anon · authenticated 는 접근 불가. 관리자도 이 테이블은 대시보드로만 확인.

create table if not exists public.release_broadcasts (
    version         text primary key,
    request_id      uuid not null,
    started_at      timestamptz not null default now(),
    completed_at    timestamptz,
    devices_total   int,
    sent            int,
    failed          int,
    errors          jsonb,
    source          text not null default 'workflow'
);

comment on table  public.release_broadcasts is
'release note 브로드캐스트 감사 로그 · idempotency (#300).';
comment on column public.release_broadcasts.version is
'semver 문자열. PK 로 같은 버전 중복 발송 차단.';
comment on column public.release_broadcasts.request_id is
'최초 시도의 request id. 재시도 진입해도 유지되도록 upsert 시 do nothing.';
comment on column public.release_broadcasts.started_at is
'함수 진입 시각. NOT NULL default now().';
comment on column public.release_broadcasts.completed_at is
'FCM 발송 루프 종료 시각. NULL 이면 in-flight 또는 실패 (부분 완료 이후 예외 등).';
comment on column public.release_broadcasts.devices_total is
'user_devices 조회 결과 개수. sent + failed 와 일치해야 정상.';
comment on column public.release_broadcasts.source is
'``workflow`` 또는 ``manual`` — 트리거 출처 태그.';

-- 관측용 인덱스. started_at 역순 조회가 가장 잦다.
create index if not exists ix_release_broadcasts_started_at
    on public.release_broadcasts (started_at desc);

-- 권한 정리. service role 은 supabase 가 자동 RLS 우회.
alter table public.release_broadcasts enable row level security;

revoke all on table public.release_broadcasts from public;
revoke all on table public.release_broadcasts from anon, authenticated;

-- SERVICE_ROLE 은 GRANT 명시 없이도 postgres · supabase_admin 롤 상속으로 접근 가능.
-- 정책 자체는 두지 않는다 (anon/authenticated 는 어차피 GRANT 없음).
