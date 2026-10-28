// Supabase Edge Function: send-outlook-sync-push (#420)
//
// pg_cron 이 30분마다 (KST 01:00~05:59 제외) 이 함수를 호출 (migration `pg_cron_outlook_sync_push` 참조).
// Outlook 연동한 유저 (`users.outlook_connected_at IS NOT NULL`) 의 기기에 silent / data push 를 보내
// 앱을 백그라운드에서 잠깐 깨움 → 앱이 기존 Outlook 재택 sync (`OutlookBackgroundSync`) 를 실행.
//
// 왜 서버가 직접 Outlook 을 읽지 않나:
//  - 재택 일정은 다른 사람 (팀) 소유의 공유 캘린더 `TeamAIVN` 에 있다. Microsoft Graph 변경 알림
//    (subscription) 은 delegated 권한으로 남의 공유 캘린더를 구독할 수 없고, application 권한
//    (Calendars.Read 전사) 은 받을 수 없다. MSAL refresh_token 도 기기 Keychain · 암호화 저장소에만 있다.
//  - 그래서 서버는 "깨우기" 만 하고, 실제 Graph 조회 · upsert 는 기기의 기존 sync 가 한다.
//
// 메시지 형태 (data: { type: "outlook_sync" }):
//  - iOS: silent push — `apns-push-type: background` · `apns-priority: 5` · `content-available: 1`,
//    notification 없음 (send-widget-refresh 와 동일). 30분 뒤엔 다음 push 가 오므로 25분 지나면 만료.
//  - Android: data-only 메시지 (notification 없음) · priority normal · ttl 25분. high 는 표시 알림
//    없이 반복되면 FCM 이 강등하므로 쓰지 않는다. Doze 중이면 다음 maintenance window 에 도착.
//
// 인증: verify_jwt = false (config.toml). 대신 shared secret 헤더 `x-outlook-sync-secret` 이
//   Supabase Secret `OUTLOOK_SYNC_PUSH_SECRET` 과 일치해야 통과. cron 은 Vault 의
//   `outlook_sync_push_secret` 를 읽어 헤더로 보낸다.
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)
//   OUTLOOK_SYNC_PUSH_SECRET    — cron → 함수 인증용 shared secret (Vault `outlook_sync_push_secret` 와 같은 값)
//
// 응답:
//   200 { users, sent, failed, total }
//   401 secret 헤더 없음/불일치
//   500 secret env 미설정 · DB 예외 등
//
// 로그에는 카운트만 남긴다. FCM 토큰은 앞부분조차 남기지 않는다.

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const LOG = "[outlook-sync-push]";

/** 다음 cron (30분 뒤) 전에 만료 — 오래 밀린 push 가 한꺼번에 몰려 오지 않도록. */
const MESSAGE_TTL_SECONDS = 25 * 60;

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

interface DeviceRow {
  fcm_token: string;
  platform: string;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

// ────────── FCM OAuth (send-widget-refresh 와 동일) ──────────

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
  const bin = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    bin,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

// ────────── 발송 ──────────

/**
 * 플랫폼별 깨우기 메시지. notification 필드를 채우지 않아 iOS · Android 모두 사용자에게 보이지 않는다.
 *
 * @param token 대상 FCM 토큰.
 * @param platform `user_devices.platform` (`ios` | `android`).
 */
function buildMessage(token: string, platform: string): Record<string, unknown> {
  const data = { type: "outlook_sync" };
  if (platform === "ios") {
    const expiration = Math.floor(Date.now() / 1000) + MESSAGE_TTL_SECONDS;
    return {
      token,
      data,
      apns: {
        headers: {
          "apns-push-type": "background",
          "apns-priority": "5",
          "apns-expiration": String(expiration),
          "apns-collapse-id": "outlook_sync",
        },
        payload: { aps: { "content-available": 1 } },
      },
    };
  }
  return {
    token,
    data,
    android: {
      priority: "normal",
      ttl: `${MESSAGE_TTL_SECONDS}s`,
      collapse_key: "outlook_sync",
    },
  };
}

/**
 * 하나의 기기에 깨우기 push 발송. 응답이 UNREGISTERED / INVALID_ARGUMENT 면 stale 토큰으로 보고
 * user_devices 에서 정리 (다음 앱 실행 시 새 토큰 upsert).
 *
 * @return 발송 성공 여부.
 */
async function sendWakeUp(
  accessToken: string,
  projectId: string,
  device: DeviceRow,
  // deno-lint-ignore no-explicit-any
  supabase: SupabaseClient<any, any, any>,
): Promise<boolean> {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ message: buildMessage(device.fcm_token, device.platform) }),
    },
  );
  if (res.ok) return true;
  const text = await res.text();
  console.error(`${LOG} 발송 실패 platform=${device.platform} ${res.status}: ${text}`);
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", device.fcm_token);
    if (error) {
      console.error(`${LOG} 스테일 토큰 삭제 실패: ${error.message}`);
    } else {
      console.log(`${LOG} 스테일 토큰 삭제 완료 platform=${device.platform}`);
    }
  }
  return false;
}

function isStaleTokenError(status: number, body: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT");
}

/** 길이가 달라도 바로 false 를 내지 않는 문자열 비교 (timing 차이 최소화). */
function safeEqual(a: string, b: string): boolean {
  const max = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let i = 0; i < max; i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

serve(async (req) => {
  try {
    // 1. Shared secret 인증. cron (pg_net) 만 호출하므로 JWT 없음.
    const expected = Deno.env.get("OUTLOOK_SYNC_PUSH_SECRET") ?? "";
    if (!expected) {
      console.error(`${LOG} OUTLOOK_SYNC_PUSH_SECRET 미설정`);
      return jsonResponse(500, { error: "secret_not_configured" });
    }
    const provided = req.headers.get("x-outlook-sync-secret") ?? "";
    if (!safeEqual(provided, expected)) {
      console.warn(`${LOG} secret 불일치 provided_len=${provided.length}`);
      return jsonResponse(401, { error: "invalid_secret" });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 2. Outlook 연동한 유저 → 그 유저들의 기기.
    const { data: users, error: uErr } = await supabase
      .from("users")
      .select("id")
      .not("outlook_connected_at", "is", null);
    if (uErr) throw uErr;
    const userIds = (users ?? []).map((u) => (u as { id: string }).id);
    if (userIds.length === 0) {
      console.log(`${LOG} 연동 유저 없음`);
      return jsonResponse(200, { users: 0, sent: 0, failed: 0, total: 0 });
    }

    const { data: devices, error: dErr } = await supabase
      .from("user_devices")
      .select("fcm_token, platform")
      .in("user_id", userIds);
    if (dErr) throw dErr;
    const targets = (devices ?? []) as DeviceRow[];
    if (targets.length === 0) {
      console.log(`${LOG} users=${userIds.length} 기기 없음`);
      return jsonResponse(200, { users: userIds.length, sent: 0, failed: 0, total: 0 });
    }

    // 3. 발송.
    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!) as ServiceAccount;
    const accessToken = await getFcmAccessToken(sa);

    const results = await Promise.all(
      targets.map((d) => sendWakeUp(accessToken, projectId, d, supabase)),
    );
    const sent = results.filter(Boolean).length;
    const failed = results.length - sent;
    const ios = targets.filter((d) => d.platform === "ios").length;
    console.log(
      `${LOG} users=${userIds.length} devices=${targets.length} (ios=${ios}, android=${targets.length - ios}) sent=${sent} failed=${failed}`,
    );

    return jsonResponse(200, { users: userIds.length, sent, failed, total: targets.length });
  } catch (e) {
    console.error(`${LOG} error`, e);
    return jsonResponse(500, { error: String(e) });
  }
});
