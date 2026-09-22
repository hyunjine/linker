// Supabase Edge Function: send-schedule-push
//
// public.schedules INSERT/UPDATE/DELETE trigger 와 pg_cron send_schedule_start_reminders 가
// pg_net.http_post 로 이 함수를 호출. 정책대로 recipients 를 골라 FCM v1 API 로 발송.
//
// 정책 (#301 v2):
//   INSERT/UPDATE/DELETE → recipients = couple_members - actor_id.
//     (is_private=true 는 트리거에서 이미 스킵되어 여기까지 오지 않음.)
//   START_REMINDER:
//     - owner_kind='us'      → 커플 양쪽 모두
//     - owner_kind='me'      → creator 만
//     - owner_kind='partner' → 파트너 (수신자 관점 me) 만  ※ UI 상 신규 저장 불가, 기존 row 만
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

interface Payload {
  type: "INSERT" | "UPDATE" | "DELETE" | "START_REMINDER";
  table: string;
  record: {
    id: string;
    couple_id: string;
    created_by: string;
    title: string;
    start_date: string;
    type: string; // 'task' | 'schedule'
    // 트리거가 실어주는 필드 (INSERT/UPDATE/DELETE).
    actor_id?: string;       // auth.uid() — 이 action 을 일으킨 사용자. 알림에서 제외.
    series_id?: string | null;
    owner_kind?: string;     // 'me' | 'partner' | 'us'
    is_private?: boolean;
    // START_REMINDER 에서만 채워짐 (pg_cron send_schedule_start_reminders).
    start_time?: string;     // 'HH:MM:SS'
    all_day?: boolean;
  };
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

async function getFcmAccessToken(serviceAccount: ServiceAccount): Promise<string> {
  const now = Date.now();
  if (cachedAccessToken && cachedAccessToken.expiresAt > now + 60_000) {
    return cachedAccessToken.token;
  }

  const pkcs8 = await importPkcs8(serviceAccount.private_key);
  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: getNumericDate(0),
    exp: getNumericDate(60 * 60),
  };
  const jwt = await jwtCreate({ alg: "RS256", typ: "JWT" }, claims, pkcs8);

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) {
    throw new Error(`OAuth2 token 교환 실패 ${res.status}: ${await res.text()}`);
  }
  const data = (await res.json()) as { access_token: string; expires_in: number };
  cachedAccessToken = {
    token: data.access_token,
    expiresAt: now + data.expires_in * 1000,
  };
  return data.access_token;
}

async function importPkcs8(pem: string): Promise<CryptoKey> {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function sendFcmMessage(
  accessToken: string,
  projectId: string,
  token: string,
  title: string,
  body: string,
  data: Record<string, string>,
  supabase: ReturnType<typeof createClient>,
): Promise<void> {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          notification: { title, body },
          data,
          apns: {
            payload: {
              aps: { sound: "default", "content-available": 1 },
            },
          },
          android: { priority: "HIGH", notification: { sound: "default" } },
        },
      }),
    },
  );
  if (res.ok) {
    console.log(`[push] FCM 발송 성공 token=${token.substring(0, 12)}…`);
    return;
  }
  const text = await res.text();
  console.error(`[push] FCM send 실패 token=${token.substring(0, 12)}… ${res.status}: ${text}`);
  // 스테일 토큰 정리 (UNREGISTERED · INVALID_ARGUMENT).
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", token);
    if (error) {
      console.error(`[push] 스테일 토큰 삭제 실패 token=${token.substring(0, 12)}… ${error.message}`);
    } else {
      console.log(`[push] 스테일 토큰 삭제 완료 token=${token.substring(0, 12)}…`);
    }
  }
}

function isStaleTokenError(status: number, body: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT");
}

/** "HH:MM:SS" → "오전 10:00" 스타일. 파싱 실패면 빈 문자열. */
function formatKoreanClock(hhmmss: string): string {
  const parts = hhmmss.split(":");
  if (parts.length < 2) return "";
  const h = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10);
  if (Number.isNaN(h) || Number.isNaN(m)) return "";
  const ampm = h < 12 ? "오전" : "오후";
  const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  return `${ampm} ${h12}:${String(m).padStart(2, "0")}`;
}

/**
 * INSERT / UPDATE / DELETE 알림.
 *
 * 규칙 (수신자 관점 정책, #301):
 *   - is_private=true 는 트리거에서 이미 걸러져 여기까지 안 옴 (defense-in-depth 로 재확인).
 *   - recipients = couple_members - actor_id.
 *     · owner_kind='me': actor=creator=본인이라 자연히 파트너만 남음.
 *     · owner_kind='us': actor 가 어느 쪽이든 반대편만 수신 (자기 액션을 자기 알림으로 다시 받지 않음).
 *     · owner_kind='partner' (legacy): 파트너가 actor 인 케이스가 있어도 동일 규칙.
 *   - actor_id 가 payload 에 없으면 (구 payload 호환) created_by 로 폴백.
 */
async function handleWriteAction(
  payload: Payload,
  supabase: ReturnType<typeof createClient>,
): Promise<Response> {
  const rec = payload.record;
  if (rec.is_private) {
    return new Response("skip (is_private, no recipients)", { status: 200 });
  }
  const actorId = rec.actor_id ?? rec.created_by;

  const { data: recipients, error: rErr } = await supabase
    .from("couple_members")
    .select("user_id")
    .eq("couple_id", rec.couple_id)
    .neq("user_id", actorId);
  if (rErr) throw rErr;
  if (!recipients || recipients.length === 0) {
    return new Response("no recipients", { status: 200 });
  }

  const targetIds = recipients.map((r: { user_id: string }) => r.user_id);
  const { data: devices, error: dErr } = await supabase
    .from("user_devices")
    .select("fcm_token")
    .in("user_id", targetIds);
  if (dErr) throw dErr;
  if (!devices || devices.length === 0) {
    return new Response("no devices", { status: 200 });
  }

  // Actor 닉네임 조회 — 알림 문구 "OO 님이 …" 에 사용. Actor = 이 액션을 일으킨 사람.
  const { data: actorUser } = await supabase
    .from("users")
    .select("nickname")
    .eq("id", actorId)
    .single();
  const actorName = actorUser?.nickname ?? "파트너";

  const projectId = Deno.env.get("FCM_PROJECT_ID")!;
  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
  const sa = JSON.parse(saJson) as ServiceAccount;
  const accessToken = await getFcmAccessToken(sa);

  const kindLabel = rec.type === "task" ? "할 일" : "일정";
  const actionLabel =
    payload.type === "INSERT" ? "추가" :
    payload.type === "UPDATE" ? "수정" : "삭제";

  // 반복 시리즈 (INSERT 에서만 series_id 실려옴). "OO 님이 반복 일정을 설정했어요" 톤 + 특정 날짜 생략.
  const isRepeatInsert = payload.type === "INSERT" && rec.series_id != null;
  const title = isRepeatInsert
    ? `${actorName} 님이 반복 ${kindLabel}을 설정했어요`
    : `${actorName} 님이 ${kindLabel}을 ${actionLabel}했어요`;
  const body = isRepeatInsert ? rec.title : `${rec.title} · ${rec.start_date}`;

  for (const dev of devices) {
    await sendFcmMessage(
      accessToken,
      projectId,
      (dev as { fcm_token: string }).fcm_token,
      title,
      body,
      {
        schedule_id: rec.id,
        couple_id: rec.couple_id,
        action: payload.type.toLowerCase(),
      },
      supabase,
    );
  }

  return new Response(
    JSON.stringify({ sent: devices.length, action: payload.type }),
    { headers: { "Content-Type": "application/json" }, status: 200 },
  );
}

/**
 * START_REMINDER — pg_cron 매 분 fire.
 *
 * 규칙:
 *   - owner_kind='us'      → 커플 양쪽 모두 (공동 일정)
 *   - owner_kind='me'      → creator 만 (본인 개인 일정)
 *   - owner_kind='partner' → 파트너 (수신자 관점 me) 만 — legacy, UI 상 신규 저장 불가
 *   - is_private 는 recipient 선택에 영향 없음 (visibility 는 RLS 가 이미 강제).
 */
async function handleStartReminder(
  payload: Payload,
  supabase: ReturnType<typeof createClient>,
): Promise<Response> {
  const rec = payload.record;
  const owner = rec.owner_kind ?? "me";
  const targetIds: string[] = [];

  if (owner === "us") {
    const { data: members, error } = await supabase
      .from("couple_members")
      .select("user_id")
      .eq("couple_id", rec.couple_id);
    if (error) throw error;
    (members ?? []).forEach((m: { user_id: string }) => targetIds.push(m.user_id));
  } else if (owner === "me") {
    targetIds.push(rec.created_by);
  } else if (owner === "partner") {
    const { data: partners, error } = await supabase
      .from("couple_members")
      .select("user_id")
      .eq("couple_id", rec.couple_id)
      .neq("user_id", rec.created_by);
    if (error) throw error;
    (partners ?? []).forEach((p: { user_id: string }) => targetIds.push(p.user_id));
  }

  if (targetIds.length === 0) {
    return new Response("no recipients (owner filter)", { status: 200 });
  }

  const { data: devices, error: dErr } = await supabase
    .from("user_devices")
    .select("fcm_token")
    .in("user_id", targetIds);
  if (dErr) throw dErr;
  if (!devices || devices.length === 0) {
    return new Response("no devices for reminder", { status: 200 });
  }

  const projectId = Deno.env.get("FCM_PROJECT_ID")!;
  const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
  const sa = JSON.parse(saJson) as ServiceAccount;
  const accessToken = await getFcmAccessToken(sa);

  const title = (rec.title || "일정").trim();
  let body: string;
  if (rec.type === "task") {
    body = "오늘의 할 일";
  } else if (rec.all_day) {
    body = "오늘 종일";
  } else {
    const timeLabel = formatKoreanClock(rec.start_time ?? "");
    body = timeLabel ? `${timeLabel} 시작` : "곧 시작";
  }

  for (const dev of devices) {
    await sendFcmMessage(
      accessToken,
      projectId,
      (dev as { fcm_token: string }).fcm_token,
      title,
      body,
      {
        schedule_id: rec.id,
        couple_id: rec.couple_id,
        reason: "start_reminder",
      },
      supabase,
    );
  }
  return new Response(
    JSON.stringify({ sent: devices.length, reason: "start_reminder" }),
    { headers: { "Content-Type": "application/json" }, status: 200 },
  );
}

serve(async (req) => {
  try {
    const payload: Payload = await req.json();
    if (
      payload.type !== "INSERT" &&
      payload.type !== "UPDATE" &&
      payload.type !== "DELETE" &&
      payload.type !== "START_REMINDER"
    ) {
      return new Response(`skipped (${payload.type})`, { status: 200 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    if (payload.type === "START_REMINDER") {
      return await handleStartReminder(payload, supabase);
    }
    return await handleWriteAction(payload, supabase);
  } catch (e) {
    console.error("[push] error", e);
    return new Response(String(e), { status: 500 });
  }
});
