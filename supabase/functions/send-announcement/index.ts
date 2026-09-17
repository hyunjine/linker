// Supabase Edge Function: send-announcement
//
// 관리자 콘솔(#261) 에서 호출하는 공지 푸시 발송 함수.
// 요청 유저의 JWT 를 검증하고, 함수 안에서도 admin uid 화이트리스트를 다시 확인한 뒤
// (브라우저 우회 방지) user_devices 에 등록된 FCM 토큰 전체에 병렬로 v1 push 를 쏜다.
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)
//   ADMIN_UIDS                  — 관리자 auth uid 콤마 구분 리스트 (예: "uuid1,uuid2")
//
// Request body:
//   { "title": string, "body": string, "userIds": string[] | "all" }
//
// Response:
//   200 { "sent": <n>, "failed": <n>, "errors": [{ "userId": "...", "reason": "..." }] }
//   400 유효성 실패
//   401 세션 없음 · JWT 무효
//   403 관리자 아님
//   500 예기치 못한 서버 오류

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

interface RequestBody {
  title?: string;
  body?: string;
  userIds?: string[] | "all";
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

interface SendError {
  userId: string;
  reason: string;
}

// 브라우저 관리자 콘솔(gh-pages) + 로컬 개발 도메인 허용. 다른 sibling 함수들은 브라우저에서
// 직접 호출되지 않지만 이 함수는 웹앱이 호출하므로 CORS 프리플라이트가 필요.
const ALLOWED_ORIGINS = new Set<string>([
  "https://hyunjine.github.io",
  "http://localhost:8080",
  "http://localhost:3000",
  "http://127.0.0.1:8080",
  "http://127.0.0.1:3000",
]);

/** 요청 Origin 이 화이트리스트에 있으면 그대로, 아니면 첫 번째 허용치를 반환. */
function corsHeaders(req: Request): HeadersInit {
  const origin = req.headers.get("origin") ?? "";
  const allowOrigin = ALLOWED_ORIGINS.has(origin) ? origin : "https://hyunjine.github.io";
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
  };
}

/** JSON 응답 헬퍼 — CORS 헤더 자동 부착. */
function jsonResponse(req: Request, status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders(req) },
  });
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

/**
 * FCM v1 발급용 access token 을 조회 (1시간 캐시).
 * @param serviceAccount Firebase Admin 서비스 계정 정보
 * @return OAuth2 access token 문자열
 */
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

/**
 * PEM PKCS#8 문자열을 WebCrypto CryptoKey (RS256) 로 파싱.
 * @param pem 서비스 계정 private_key 필드 원문
 * @return 서명용 CryptoKey
 */
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

/**
 * 하나의 FCM 토큰에 공지 push 를 발송.
 * @param accessToken OAuth2 access token
 * @param projectId Firebase 프로젝트 ID
 * @param token 대상 FCM 등록 토큰
 * @param title 알림 제목
 * @param body 알림 본문
 * @param supabase user_devices stale token 정리용 클라이언트
 * @return 발송 성공 여부와 실패 사유
 */
async function sendFcmMessage(
  accessToken: string,
  projectId: string,
  token: string,
  title: string,
  body: string,
  supabase: ReturnType<typeof createClient>,
): Promise<{ ok: true } | { ok: false; reason: string }> {
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
          data: { reason: "announcement" },
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
    console.log(`[announcement] FCM 성공 token=${token.substring(0, 12)}…`);
    return { ok: true };
  }
  const text = await res.text();
  console.error(
    `[announcement] FCM 실패 token=${token.substring(0, 12)}… ${res.status}: ${text}`,
  );
  // 스테일 토큰(앱 삭제·데이터 초기화·rotate) 은 다음 발송 때 또 실패하지 않도록 즉시 정리.
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", token);
    if (error) {
      console.error(`[announcement] 스테일 토큰 삭제 실패: ${error.message}`);
    } else {
      console.log(`[announcement] 스테일 토큰 삭제 완료 token=${token.substring(0, 12)}…`);
    }
  }
  return { ok: false, reason: extractReason(res.status, text) };
}

function isStaleTokenError(status: number, body: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT");
}

/** FCM 응답 본문에서 대표 에러 코드를 뽑아낸다. 파싱 실패면 status 만 반환. */
function extractReason(status: number, body: string): string {
  try {
    const parsed = JSON.parse(body) as {
      error?: { status?: string; message?: string };
    };
    const code = parsed.error?.status ?? "";
    const msg = parsed.error?.message ?? "";
    if (code || msg) return `${code || `http_${status}`}${msg ? `: ${msg}` : ""}`;
  } catch {
    // JSON 아님 → 아래 폴백.
  }
  return `http_${status}`;
}

/** ADMIN_UIDS env 를 파싱해 Set 으로 반환. 공백 · 빈 항목 제거. */
function loadAdminUids(): Set<string> {
  const raw = Deno.env.get("ADMIN_UIDS") ?? "";
  return new Set(
    raw
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0),
  );
}

serve(async (req) => {
  // CORS preflight.
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(req) });
  }
  if (req.method !== "POST") {
    return jsonResponse(req, 405, { error: "method_not_allowed" });
  }

  try {
    // 1. 요청자 JWT 검증. anon key 로 클라이언트를 만들고 Authorization 헤더로 getUser 조회.
    const authHeader = req.headers.get("authorization") ?? "";
    const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
    if (!jwt) {
      return jsonResponse(req, 401, { error: "missing_authorization" });
    }
    const authClient = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: `Bearer ${jwt}` } } },
    );
    const { data: userData, error: userErr } = await authClient.auth.getUser(jwt);
    if (userErr || !userData?.user) {
      console.error("[announcement] JWT 검증 실패", userErr?.message);
      return jsonResponse(req, 401, { error: "invalid_jwt" });
    }
    const callerUid = userData.user.id;

    // 2. 관리자 화이트리스트 재검증 (브라우저 우회 방지).
    const adminUids = loadAdminUids();
    if (adminUids.size === 0) {
      console.error("[announcement] ADMIN_UIDS env 가 비어있음 — 배포 설정 확인");
      return jsonResponse(req, 403, { error: "admin_not_configured" });
    }
    if (!adminUids.has(callerUid)) {
      console.warn(`[announcement] 관리자 아님 uid=${callerUid}`);
      return jsonResponse(req, 403, { error: "not_admin" });
    }

    // 3. 요청 파싱 · 유효성.
    let payload: RequestBody;
    try {
      payload = (await req.json()) as RequestBody;
    } catch {
      return jsonResponse(req, 400, { error: "invalid_json" });
    }
    const title = (payload.title ?? "").trim();
    const body = (payload.body ?? "").trim();
    if (!title || !body) {
      return jsonResponse(req, 400, { error: "title_or_body_empty" });
    }
    const userIds = payload.userIds;
    if (userIds !== "all" && !Array.isArray(userIds)) {
      return jsonResponse(req, 400, { error: "invalid_user_ids" });
    }
    if (Array.isArray(userIds) && userIds.length === 0) {
      return jsonResponse(req, 400, { error: "empty_user_ids" });
    }

    // 4. 대상 device 조회 (service role 로 RLS 우회).
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const query = supabase.from("user_devices").select("user_id, fcm_token, platform");
    const { data: devices, error: devErr } = userIds === "all"
      ? await query
      : await query.in("user_id", userIds);
    if (devErr) throw devErr;

    if (!devices || devices.length === 0) {
      return jsonResponse(req, 200, { sent: 0, failed: 0, errors: [] });
    }

    // 5. FCM access token 발급.
    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
    const sa = JSON.parse(saJson) as ServiceAccount;
    const accessToken = await getFcmAccessToken(sa);

    // 6. 병렬 발송. per-device 실패는 throw 하지 않고 errors[] 에 축적.
    type DeviceRow = { user_id: string; fcm_token: string; platform: string };
    const rows = devices as DeviceRow[];
    const results = await Promise.allSettled(
      rows.map((dev) =>
        sendFcmMessage(accessToken, projectId, dev.fcm_token, title, body, supabase)
          .then((r) => ({ userId: dev.user_id, result: r })),
      ),
    );

    let sent = 0;
    let failed = 0;
    const errors: SendError[] = [];
    for (let i = 0; i < results.length; i++) {
      const r = results[i];
      const row = rows[i];
      if (r.status === "fulfilled") {
        if (r.value.result.ok) {
          sent++;
        } else {
          failed++;
          errors.push({ userId: row.user_id, reason: r.value.result.reason });
        }
      } else {
        failed++;
        const reason = r.reason instanceof Error ? r.reason.message : String(r.reason);
        console.error(`[announcement] 예외 user_id=${row.user_id} ${reason}`);
        errors.push({ userId: row.user_id, reason });
      }
    }

    console.log(
      `[announcement] 완료 sent=${sent} failed=${failed} total=${rows.length} admin=${callerUid}`,
    );
    return jsonResponse(req, 200, { sent, failed, errors });
  } catch (e) {
    console.error("[announcement] error", e);
    return jsonResponse(req, 500, { error: String(e) });
  }
});
