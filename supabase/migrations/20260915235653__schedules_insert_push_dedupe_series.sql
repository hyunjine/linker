-- schedules INSERT push trigger 를 ROW-LEVEL → STATEMENT-LEVEL 로 변경 (#263).
-- 반복 시리즈는 batch INSERT (한 statement) 로 N 개 인스턴스가 함께 들어오는데, 기존 트리거는
-- FOR EACH ROW 라 N 번 fire → 파트너에게 알림 N 번 쏟아짐.
--
-- 수정: FOR EACH STATEMENT + `REFERENCING NEW TABLE AS new_rows` (Postgres 10+) 로 한 번만 fire,
-- 함수 안에서 시리즈당 최초 (start_date, id) 1건만 push payload 로 발송. Standalone (series_id NULL)
-- 은 각자 개별 push (기존 동작 유지).
--
-- 발송 payload 에 `series_id` 추가 — edge function 이 이 값을 보고 반복 여부 판단 후 본문 톤 조정.

create or replace function public.tg_schedules_notify_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    fn_url text := 'https://guxpohhhacljwhyiskdk.supabase.co/functions/v1/send-schedule-push';
    r record;
begin
    for r in
        -- 시리즈 (series_id 있음) 는 그룹당 최초 (start_date, id) 1건만.
        -- Standalone (series_id NULL) 은 각 row 가 자기 자신을 대표 → 그대로 개별 발송.
        select distinct on (coalesce(series_id::text, id::text))
               id, couple_id, created_by, title, start_date, type, series_id
          from new_rows
         order by coalesce(series_id::text, id::text), start_date asc, id asc
    loop
        perform net.http_post(
            url     := fn_url,
            body    := jsonb_build_object(
                'type', 'INSERT',
                'table', 'schedules',
                'record', jsonb_build_object(
                    'id',         r.id,
                    'couple_id',  r.couple_id,
                    'created_by', r.created_by,
                    'title',      r.title,
                    'start_date', r.start_date,
                    'type',       r.type,
                    'series_id',  r.series_id
                )
            ),
            headers := jsonb_build_object('Content-Type', 'application/json'),
            timeout_milliseconds := 5000
        );
    end loop;
    return null;
end;
$$;

-- 기존 ROW-LEVEL 트리거 제거 후 STATEMENT-LEVEL 로 재등록.
drop trigger if exists tg_schedules_notify_insert on public.schedules;
create trigger tg_schedules_notify_insert
    after insert on public.schedules
    referencing new table as new_rows
    for each statement execute procedure public.tg_schedules_notify_insert();
