// Supabase Edge Function: send-schedule-push
//
// public.schedules INSERT 시 DB trigger 가 pg_net.http_post 로 이 함수를 호출.
// 파트너들의 FCM device 토큰을 조회해 FCM HTTP v1 API 로 알림 발송.
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
    // START_REMINDER 에서만 채워짐 (pg_cron send_schedule_start_reminders 가 payload 조립).
    start_time?: string;   // 'HH:MM:SS'
    is_private?: boolean;
    owner_kind?: string;   // 'me' | 'partner' | 'us'
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

  // 1. Service account 로 JWT 서명 (RS256).
  const pkcs8 = await importPkcs8(serviceAccount.private_key);
  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: getNumericDate(0),
    exp: getNumericDate(60 * 60), // 1h
  };
  const jwt = await jwtCreate({ alg: "RS256", typ: "JWT" }, claims, pkcs8);

  // 2. Google OAuth2 token endpoint 에서 access token 교환.
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

// PEM PKCS#8 문자열을 WebCrypto CryptoKey (RS256) 로 파싱.
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
  if (!res.ok) {
    const text = await res.text();
    console.error(`[push] FCM send 실패 token=${token.substring(0, 12)}… ${res.status}: ${text}`);
  } else {
    console.log(`[push] FCM 발송 성공 token=${token.substring(0, 12)}…`);
  }
}

/**
 * pg_cron 이 매 분 fire — 이번 분 (Asia/Seoul) 에 시작하는 시간 있는 스케줄에 대해 이 함수가 호출된다.
 *
 * 발송 대상:
 *  - 항상 creator (본인 예약)
 *  - 비공개 (is_private=true) 가 아니면 같은 커플 파트너들도 함께 (owner=us · me · partner 모두 동일 규칙)
 *  이 규칙은 RLS 가 "볼 수 있는 사람" 을 결정하는 규칙과 정확히 일치.
 */
async function handleStartReminder(
  payload: Payload,
  supabase: ReturnType<typeof createClient>,
): Promise<Response> {
  const rec = payload.record;
  const targetIds: string[] = [rec.created_by];

  if (!rec.is_private) {
    const { data: partners, error } = await supabase
      .from("couple_members")
      .select("user_id")
      .eq("couple_id", rec.couple_id)
      .neq("user_id", rec.created_by);
    if (error) throw error;
    (partners ?? []).forEach((p: { user_id: string }) => targetIds.push(p.user_id));
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

  const timeLabel = formatKoreanClock(rec.start_time ?? "");
  const title = (rec.title || "일정").trim();
  const body = timeLabel ? `${timeLabel} 시작` : "곧 시작";

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
    );
  }
  return new Response(
    JSON.stringify({ sent: devices.length, reason: "start_reminder" }),
    { headers: { "Content-Type": "application/json" }, status: 200 },
  );
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

serve(async (req) => {
  try {
    const payload: Payload = await req.json();
    if (payload.type !== "INSERT" && payload.type !== "START_REMINDER") {
      return new Response(`skipped (${payload.type})`, { status: 200 });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    if (payload.type === "START_REMINDER") {
      return await handleStartReminder(payload, supabase);
    }

    // 1. 같은 커플 · 창작자 아닌 유저 조회.
    const { data: partnerUsers, error: pErr } = await supabase
      .from("couple_members")
      .select("user_id")
      .eq("couple_id", payload.record.couple_id)
      .neq("user_id", payload.record.created_by);
    if (pErr) throw pErr;
    if (!partnerUsers || partnerUsers.length === 0) {
      return new Response("no partners", { status: 200 });
    }

    const partnerIds = partnerUsers.map((r: { user_id: string }) => r.user_id);
    const { data: devices, error: dErr } = await supabase
      .from("user_devices")
      .select("fcm_token, platform")
      .in("user_id", partnerIds);
    if (dErr) throw dErr;
    if (!devices || devices.length === 0) {
      return new Response("no devices", { status: 200 });
    }

    // 2. 창작자 이름 조회 (알림 본문에 "OO 님이 …" 로 사용).
    const { data: creator } = await supabase
      .from("users")
      .select("nickname")
      .eq("id", payload.record.created_by)
      .single();
    const creatorName = creator?.nickname ?? "파트너";

    // 3. FCM 발송.
    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
    const sa = JSON.parse(saJson) as ServiceAccount;
    const accessToken = await getFcmAccessToken(sa);

    const kindLabel = payload.record.type === "task" ? "할 일" : "일정";
    const title = `${creatorName} 님이 ${kindLabel}을 추가했어요`;
    const body = `${payload.record.title} · ${payload.record.start_date}`;

    for (const dev of devices) {
      await sendFcmMessage(
        accessToken,
        projectId,
        (dev as { fcm_token: string }).fcm_token,
        title,
        body,
        {
          schedule_id: payload.record.id,
          couple_id: payload.record.couple_id,
        },
      );
    }

    return new Response(
      JSON.stringify({ sent: devices.length }),
      { headers: { "Content-Type": "application/json" }, status: 200 },
    );
  } catch (e) {
    console.error("[push] error", e);
    return new Response(String(e), { status: 500 });
  }
});
