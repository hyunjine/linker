-- 사진첩: 커플 사진을 날짜에 붙여 저장.
--
-- 구성
--   public.couple_photos          사진 메타 (couple_id · uploaded_by · taken_on · storage 경로)
--   storage bucket 'couple-photos' 원본 + 썸네일 파일 (private — signed URL 로만 접근)
--
-- 파일 경로 규칙: {couple_id}/{photo_id}.jpg · {couple_id}/{photo_id}_thumb.jpg
--   → storage.objects 정책이 경로 첫 세그먼트(= couple_id)로 소유 커플을 판정한다.
--   avatars 버킷이 {user_id}/ 로 본인 폴더를 가르는 것과 같은 방식.
--
-- 정책 요약: 같은 커플만 select, 본인만 insert, 올린 사람만 delete.
-- update 정책은 의도적으로 없음 — 최초 버전은 수정 미지원 (날짜가 틀리면 삭제 후 재업로드).

create table public.couple_photos (
  id           uuid primary key default gen_random_uuid(),
  couple_id    uuid not null references public.couples(id) on delete cascade,
  uploaded_by  uuid not null references public.users(id)   on delete cascade,
  taken_on     date not null,
  storage_path text not null,
  thumb_path   text not null,
  created_at   timestamptz not null default now()
);

-- 사진첩 그리드 · 날짜 상세 둘 다 (couple_id, taken_on desc) 로 조회한다.
create index ix_couple_photos_couple_date
  on public.couple_photos(couple_id, taken_on desc, created_at desc);

alter table public.couple_photos enable row level security;

create policy couple_photos_select on public.couple_photos
  for select to authenticated
  using (couple_id in (select private.my_couple_ids()));

create policy couple_photos_insert on public.couple_photos
  for insert to authenticated
  with check (
    couple_id in (select private.my_couple_ids())
    and uploaded_by = auth.uid()
  );

create policy couple_photos_delete on public.couple_photos
  for delete to authenticated
  using (uploaded_by = auth.uid());

-- 상대방 화면 즉시 반영 (CoupleRealtime 의 couple_photos 구독).
alter publication supabase_realtime add table public.couple_photos;

-- ────────── Storage ──────────

-- private 버킷 (public = false) — URL 이 공개되지 않고 signed URL 로만 접근.
-- 파일당 5MB · image/jpeg 만 허용 (클라이언트도 JPEG 품질 85 / 긴 변 2048px 로 인코딩).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('couple-photos', 'couple-photos', false, 5242880, array['image/jpeg'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "couple_photos_obj_select" on storage.objects;
create policy "couple_photos_obj_select" on storage.objects
  for select to authenticated
  using (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] in (
      select c::text from private.my_couple_ids() as c
    )
  );

drop policy if exists "couple_photos_obj_insert" on storage.objects;
create policy "couple_photos_obj_insert" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] in (
      select c::text from private.my_couple_ids() as c
    )
  );

drop policy if exists "couple_photos_obj_delete" on storage.objects;
create policy "couple_photos_obj_delete" on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'couple-photos'
    and (storage.foldername(name))[1] in (
      select c::text from private.my_couple_ids() as c
    )
  );

-- ────────── 빈 커플 정리 시 Storage 파일도 함께 삭제 ──────────
--
-- 20260825133645__orphan_couple_cleanup 의 private.delete_empty_couple() 은 couples row 만 지운다.
-- couple_photos row 는 FK cascade 로 같이 사라지지만 Storage 파일은 남아 과금되는 orphan 이 된다.
-- 같은 트리거 함수를 교체해 해당 커플 폴더의 오브젝트까지 지운다.
create or replace function private.delete_empty_couple()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    if not exists (
        select 1 from public.couple_members where couple_id = old.couple_id
    ) then
        -- 사진 파일 먼저 (couples 삭제 후에는 couple_id 로 경로를 찾을 수 없다).
        delete from storage.objects
         where bucket_id = 'couple-photos'
           and (storage.foldername(name))[1] = old.couple_id::text;
        delete from public.couples where id = old.couple_id;
    end if;
    return old;
end;
$$;

revoke execute on function private.delete_empty_couple() from public;
