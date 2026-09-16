// Outlook 캘린더 ICS 구독 → schedules 미러 (#242).
//
// 흐름:
// 1) outlook_ics_subscriptions 전체 순회
// 2) 각 subscription 의 ics_url 을 fetch
// 3) ICS 본문에서 VEVENT 파싱 (RRULE 은 TODO — 우선 단발 이벤트만 지원)
// 4) 사용자의 couple_id 찾기 (couple_members)
// 5) schedules 에 upsert (onConflict: created_by+external_id)
// 6) 이번 fetch 에 없는 기존 mirror row (source='outlook', created_by=user) 삭제
// 7) last_synced_at / last_error 기록
//
// pg_cron 이 net.http_post 로 이 endpoint 를 주기적으로 호출.
// verify_jwt=false — 트리거는 pg_cron 이 service role key 없이 net.http_post 로 부르기 때문.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

interface Subscription {
    user_id: string;
    ics_url: string;
}

interface ParsedEvent {
    /** external_id 로 쓸 안정 ID — CATEGORIES 에 `TeamAIVN:xxx` 있으면 xxx, 없으면 VEVENT UID */
    uid: string;
    /** SUMMARY */
    title: string;
    /** DTSTART date part (yyyy-MM-dd) */
    startDate: string;
    /** DTEND date part (yyyy-MM-dd) — exclusive per iCal spec */
    endDate: string;
    /** DTSTART time (HH:MM:SS) — null 이면 종일 */
    startTime: string | null;
    /** DTEND time (HH:MM:SS) — null 이면 종일 */
    endTime: string | null;
    /** DTSTART VALUE=DATE (종일 여부) */
    allDay: boolean;
    /** true 면 mirror 대상 — CATEGORIES 에 TeamAIVN 태그 발견됐음을 의미 (#242). */
    fromTeamAIVN: boolean;
}

/**
 * CATEGORIES 리스트에서 `TeamAIVN:xxx` 태그 찾아 xxx 부분 (원본 TeamAIVN 이벤트 ID) 반환.
 * Power Automate 스케줄 flow 가 TeamAIVN → 개인 일정 복제 시 이 카테고리를 심음. 매 30분마다
 * 개인 일정을 wipe/recreate 해도 이 ID 는 안 바뀌므로 supabase 의 external_id 로 사용 → row
 * 안정성 확보 (같은 이벤트 = 같은 external_id) → trigger 의 content_changed 체크로 실제 변경만
 * 파트너 push.
 */
function extractTeamAIVNId(categoriesLine: string): string | null {
    // 예: `CATEGORIES:TeamAIVN:AAMkAG...`, `CATEGORIES:foo,TeamAIVN:AAMkAG...,bar`
    const idx = categoriesLine.indexOf(":");
    if (idx < 0) return null;
    const raw = categoriesLine.substring(idx + 1);
    for (const cat of raw.split(",")) {
        const trimmed = cat.trim();
        if (trimmed.startsWith("TeamAIVN:")) {
            return trimmed.substring("TeamAIVN:".length);
        }
    }
    return null;
}

/** VEVENT 하나 파싱. RRULE · RECURRENCE-ID · exception 은 우선 무시 (단발 이벤트만). */
function parseVEvent(block: string): ParsedEvent | null {
    // ICS 는 CRLF 로 접힘 (line folding) — 다음 줄이 공백/탭으로 시작하면 이전 줄에 이어붙임.
    const unfolded = block.replace(/\r?\n[ \t]/g, "");
    const lines = unfolded.split(/\r?\n/);

    let uid = "";
    let recurrenceId = "";
    let summary = "";
    let dtStartLine = "";
    let dtEndLine = "";
    let teamAivnId: string | null = null;

    for (const line of lines) {
        if (line.startsWith("UID:")) uid = line.substring(4).trim();
        else if (line.startsWith("RECURRENCE-ID")) {
            const idx = line.indexOf(":");
            if (idx > 0) recurrenceId = line.substring(idx + 1).trim();
        }
        else if (line.startsWith("SUMMARY:")) summary = unescapeText(line.substring(8).trim());
        else if (line.startsWith("DTSTART")) dtStartLine = line;
        else if (line.startsWith("DTEND")) dtEndLine = line;
        else if (line.startsWith("CATEGORIES:")) teamAivnId = extractTeamAIVNId(line);
    }

    if (!uid || !dtStartLine) return null;
    // external_id 우선순위: TeamAIVN 카테고리 태그 > VEVENT UID + RECURRENCE-ID.
    // PA wipe/recreate 시 UID 는 바뀌지만 카테고리 태그는 유지 → row 안정.
    const externalId = teamAivnId
        ?? (recurrenceId ? `${uid}::${recurrenceId}` : uid);

    const start = parseDateLine(dtStartLine);
    if (!start) return null;
    const end = dtEndLine ? parseDateLine(dtEndLine) : null;

    // DTEND 없는 경우: 종일이면 DTSTART 다음 날, 시각이면 DTSTART + 1시간 (안전한 fallback)
    let endDate = end?.date;
    let endTime = end?.time ?? null;
    if (!endDate) {
        if (start.allDay) {
            endDate = addDays(start.date, 1);
        } else {
            endDate = start.date;
            endTime = start.time;
        }
    }

    return {
        uid: externalId,
        title: summary || "(제목 없음)",
        startDate: start.date,
        endDate,
        startTime: start.allDay ? null : start.time,
        endTime: start.allDay ? null : endTime,
        allDay: start.allDay,
        fromTeamAIVN: teamAivnId !== null,
    };
}

/**
 * DTSTART / DTEND 라인 파싱. 지원 포맷:
 *   DTSTART;VALUE=DATE:20260916
 *   DTSTART:20260916T090000Z
 *   DTSTART;TZID=Asia/Seoul:20260916T090000
 */
function parseDateLine(line: string): { date: string; time: string; allDay: boolean } | null {
    const colonIdx = line.indexOf(":");
    if (colonIdx < 0) return null;
    const prefix = line.substring(0, colonIdx);
    const value = line.substring(colonIdx + 1).trim();

    const allDay = prefix.includes("VALUE=DATE") && !value.includes("T");

    if (allDay) {
        // yyyyMMdd → yyyy-MM-dd
        if (value.length !== 8) return null;
        const date = `${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}`;
        return { date, time: "00:00:00", allDay: true };
    }

    // yyyyMMddTHHmmss(Z)? — 우선 로컬 시각으로 취급 (TZID 는 무시).
    // 정식 처리하려면 TZID 파싱 · UTC 변환 필요. 지금은 시각 그대로 사용.
    const m = value.match(/^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z?$/);
    if (!m) return null;
    return {
        date: `${m[1]}-${m[2]}-${m[3]}`,
        time: `${m[4]}:${m[5]}:${m[6]}`,
        allDay: false,
    };
}

/** ICS 텍스트 이스케이프 해제 (`\\n` → newline, `\\,` → `,` 등) */
function unescapeText(s: string): string {
    return s.replace(/\\n/g, "\n").replace(/\\,/g, ",").replace(/\\;/g, ";").replace(/\\\\/g, "\\");
}

function addDays(dateStr: string, days: number): string {
    const d = new Date(dateStr + "T00:00:00Z");
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().substring(0, 10);
}

/**
 * ICS 본문에서 모든 VEVENT 블록을 뽑아 파싱. 같은 external_id 가 여러 번 나오면 (드문 케이스)
 * 마지막 것만 사용 — upsert 의 ON CONFLICT 가 같은 커맨드에서 동일 행을 두 번 건드릴 수 없음.
 */
function parseIcs(ics: string): ParsedEvent[] {
    const byId = new Map<string, ParsedEvent>();
    const regex = /BEGIN:VEVENT([\s\S]*?)END:VEVENT/g;
    let m: RegExpExecArray | null;
    while ((m = regex.exec(ics)) !== null) {
        const parsed = parseVEvent(m[1]);
        if (parsed) byId.set(parsed.uid, parsed);
    }
    return Array.from(byId.values());
}

Deno.serve(async () => {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE, {
        auth: { persistSession: false },
    });

    const { data: subs, error: subsErr } = await supabase
        .from("outlook_ics_subscriptions")
        .select("user_id, ics_url");

    if (subsErr) {
        console.error("[sync-outlook-ics] fetch subscriptions failed:", subsErr);
        return new Response(JSON.stringify({ error: subsErr.message }), { status: 500 });
    }

    const results: Record<string, { synced?: number; error?: string }> = {};

    for (const sub of (subs ?? []) as Subscription[]) {
        try {
            // 1. couple_id 조회 (스케줄엔 필수)
            const { data: member } = await supabase
                .from("couple_members")
                .select("couple_id")
                .eq("user_id", sub.user_id)
                .maybeSingle();

            if (!member) {
                results[sub.user_id] = { error: "no couple_id (커플 미가입)" };
                await supabase
                    .from("outlook_ics_subscriptions")
                    .update({ last_error: "커플에 가입되지 않아 스케줄 미러 불가", updated_at: new Date().toISOString() })
                    .eq("user_id", sub.user_id);
                continue;
            }

            // 2. ICS fetch
            const res = await fetch(sub.ics_url, { redirect: "follow" });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const icsText = await res.text();

            // 3. 파싱
            const events = parseIcs(icsText);

            // 4. TeamAIVN 카테고리 태그 붙은 이벤트만 미러 (#242).
            //    Power Automate 예약 flow 가 TeamAIVN → 개인 일정으로 복제 시 CATEGORIES 에
            //    `TeamAIVN:{원본_이벤트_id}` 를 심어놨음. 그 값이 external_id 로 잡혀 wipe/recreate
            //    사이 row 안정성 보장 → trigger 는 실질 변경만 감지해 파트너 push.
            const rows = events
                .filter((e) => e.fromTeamAIVN)
                .map((e) => ({
                    couple_id: member.couple_id,
                    created_by: sub.user_id,
                    type: "schedule" as const,
                    owner_kind: "me" as const,
                    title: e.title.substring(0, 200),
                    start_date: e.startDate,
                    end_date: e.allDay ? addDays(e.endDate, -1) : e.endDate,
                    all_day: e.allDay,
                    start_time: e.allDay ? null : e.startTime,
                    end_time: e.allDay ? null : e.endTime,
                    source: "outlook" as const,
                    external_id: e.uid,
                }));

            if (rows.length > 0) {
                const { error: upsertErr } = await supabase
                    .from("schedules")
                    .upsert(rows, { onConflict: "created_by,external_id" });

                if (upsertErr) throw upsertErr;
            }

            // 5. 이번 fetch 에서 매칭된 것 (재택) 외의 기존 outlook mirror 삭제.
            //    Outlook 에서 이벤트 삭제되거나 재택 필터 매칭 안 되면 자연스레 제거.
            const fetchedUids = rows.map((r) => r.external_id);
            let deleteQuery = supabase
                .from("schedules")
                .delete()
                .eq("created_by", sub.user_id)
                .eq("source", "outlook");
            if (fetchedUids.length > 0) {
                deleteQuery = deleteQuery.not("external_id", "in", `(${fetchedUids.map((u) => `"${u}"`).join(",")})`);
            }
            await deleteQuery;

            // 6. 성공 기록
            await supabase
                .from("outlook_ics_subscriptions")
                .update({
                    last_synced_at: new Date().toISOString(),
                    last_error: null,
                    updated_at: new Date().toISOString(),
                })
                .eq("user_id", sub.user_id);

            results[sub.user_id] = { synced: rows.length };
        } catch (e) {
            const errMsg =
                e instanceof Error
                    ? e.message
                    : typeof e === "object" && e !== null
                        ? JSON.stringify(e)
                        : String(e);
            console.error(`[sync-outlook-ics] user=${sub.user_id} failed:`, errMsg, e);
            await supabase
                .from("outlook_ics_subscriptions")
                .update({
                    last_error: errMsg,
                    updated_at: new Date().toISOString(),
                })
                .eq("user_id", sub.user_id);
            results[sub.user_id] = { error: errMsg };
        }
    }

    return new Response(JSON.stringify({ results }), {
        headers: { "Content-Type": "application/json" },
    });
});
