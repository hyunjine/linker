// Supabase Edge Function: send-widget-refresh
//
// pg_cron 이 매일 KST 자정에 이 함수를 호출 (migration `pg_cron_widget_refresh` 참조).
// 활성 iOS 디바이스에 Silent Push (content-available: 1, no alert) 를 보내 앱을
// 백그라운드에서 잠깐 깨움 → iOS 가 `didReceiveRemoteNotification` 콜하고,
// LinkerPushBridge 가 WidgetSync.refresh() 실행 → App Group payload 갱신 → 위젯 refresh.
//
// Silent Push 조건 (iOS 13+):
//  - `apns-push-type: background` 헤더 필수
//  - `apns-priority: 5` (background 는 5 만 허용)
//  - APNs payload 에 alert · sound · badge 없음, `content-available: 1` 있음
//  - Firebase v1 API 에선 `message.notification` 자체를 안 채우면 silent 로 취급
//
// Android 는 위젯 refresh 흐름이 iOS 와 달라 이 함수는 iOS 만 대상. Android 는
// WorkManager 기반 별건 이슈.
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

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
  const bin = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    bin,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/**
 * 하나의 iOS 토큰에 silent push 발송. 응답이 UNREGISTERED / INVALID_ARGUMENT 면
 * stale 토큰으로 판단하고 user_devices 에서 정리 (다음 앱 실행 시 새 토큰 upsert).
 */
async function sendSilentToIos(
  accessToken: string,
  projectId: string,
  token: string,
  supabase: ReturnType<typeof createClient>,
): Promise<boolean> {
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
          // Silent 이므로 notification 필드 없음. data 로만 앱에 이유 전달.
          data: { reason: "widget_refresh" },
          apns: {
            headers: {
              "apns-push-type": "background",
              "apns-priority": "5",
            },
            payload: { aps: { "content-available": 1 } },
          },
        },
      }),
    },
  );
  if (res.ok) {
    console.log(`[widget-refresh] 성공 token=${token.substring(0, 12)}…`);
    return true;
  }
  const text = await res.text();
  console.error(
    `[widget-refresh] 실패 token=${token.substring(0, 12)}… ${res.status}: ${text}`,
  );
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", token);
    if (error) {
      console.error(`[widget-refresh] 스테일 토큰 삭제 실패: ${error.message}`);
    } else {
      console.log(`[widget-refresh] 스테일 토큰 삭제 완료 token=${token.substring(0, 12)}…`);
    }
  }
  return false;
}

function isStaleTokenError(status: number, body: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT");
}

serve(async (_req) => {
  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 활성 iOS 디바이스 전부 조회. Android 는 위젯 refresh 흐름이 달라 이 함수에선 skip.
    const { data: devices, error } = await supabase
      .from("user_devices")
      .select("fcm_token")
      .eq("platform", "ios");
    if (error) throw error;
    if (!devices || devices.length === 0) {
      return new Response("no ios devices", { status: 200 });
    }

    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
    const sa = JSON.parse(saJson) as ServiceAccount;
    const accessToken = await getFcmAccessToken(sa);

    let sent = 0;
    for (const dev of devices) {
      const ok = await sendSilentToIos(
        accessToken,
        projectId,
        (dev as { fcm_token: string }).fcm_token,
        supabase,
      );
      if (ok) sent++;
    }

    return new Response(
      JSON.stringify({ sent, total: devices.length }),
      { headers: { "Content-Type": "application/json" }, status: 200 },
    );
  } catch (e) {
    console.error("[widget-refresh] error", e);
    return new Response(String(e), { status: 500 });
  }
});
