-- user_devices: FCM 푸시 발송 대상 기기 토큰 저장.
-- 한 유저가 여러 기기 (iOS + Android + 다른 폰) 를 가질 수 있어 (user_id, fcm_token) 유니크.
-- 로그아웃 · 앱 삭제 · 토큰 rotation 시 클라이언트가 upsert.
create table if not exists public.user_devices (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.users(id) on delete cascade,
  fcm_token     text not null,
  platform      text not null check (platform in ('ios', 'android')),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (user_id, fcm_token)
);

create index if not exists user_devices_user_id_idx on public.user_devices (user_id);

alter table public.user_devices enable row level security;

-- 본인 device 만 read/write. Edge Function 은 service_role 로 우회.
drop policy if exists "user_devices_self" on public.user_devices;
create policy "user_devices_self" on public.user_devices
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create or replace function public.tg_user_devices_touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists tg_user_devices_touch on public.user_devices;
create trigger tg_user_devices_touch
  before update on public.user_devices
  for each row execute procedure public.tg_user_devices_touch_updated_at();
