-- schedules INSERT push payload 에 owner_kind 포함 (#271).
--
-- 배경: 스케줄 push 는 "수신자 관점의 owner 가 me · us 인 경우에만" 발송되어야 하는데, 기존 INSERT
-- 트리거 (#263 로 dedup 개편) payload 에 owner_kind 가 없어서 edge function 이 걸러낼 방법이 없었음.
-- START_REMINDER payload 는 이미 owner_kind 포함 (20260903100000__reminders_include_allday_task.sql).
--
-- DB 에는 생성자 관점으로 owner_kind 가 저장 (me = 생성자 소유, partner = 상대에게 할당, us = 공동).
-- 관점 반전은 edge function 쪽에서 처리.

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
               id, couple_id, created_by, title, start_date, type, series_id, owner_kind
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
                    'series_id',  r.series_id,
                    'owner_kind', r.owner_kind
                )
            ),
            headers := jsonb_build_object('Content-Type', 'application/json'),
            timeout_milliseconds := 5000
        );
    end loop;
    return null;
end;
$$;
