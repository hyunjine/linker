// Supabase Edge Function: broadcast-release-note
//
// GitHub Actions 의 release 워크플로 (#300) 에서 배포 완료 후 자동 호출.
// 전체 유저의 user_devices.fcm_token 을 조회해 "🚨긴급🚨 / 새 버전 v{version} 이 …" 문구로
// FCM v1 push 를 병렬 발송한다.
//
// 인증 정책 (send-announcement 와 다름):
//   - 관리자 JWT 대신 shared secret 헤더 (`x-release-broadcast-secret`) 로 인증.
//     워크플로는 관리자 세션이 없고, 브라우저에서 호출되지도 않으므로 CORS 불필요.
//   - 헤더 값은 Supabase Secret `RELEASE_BROADCAST_SECRET` 과 일치해야 통과.
//     타이밍 공격 방지를 위해 문자열 길이 · 각 바이트를 상수 시간으로 비교.
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)
//   RELEASE_BROADCAST_SECRET    — 워크플로 → 함수 인증용 shared secret
//
// Request body:
//   { "version": string }   // 예: "1.4.2"
//
// Response:
//   200 { "sent": <n>, "failed": <n>, "errors": [{ "userId": "...", "reason": "..." }] }
//   400 유효성 실패
//   401 secret 헤더 없음/불일치
//   500 예기치 못한 서버 오류

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

interface RequestBody {
  version?: string;
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

const NOTIFICATION_TITLE = "🚨긴급🚨";

/**
 * 배포된 marketing version 을 받아 push 본문을 만든다.
 * @param version 예: "1.4.2"
 */
function buildBody(version: string): string {
  return `새 버전 v ${version}이 출시되었어요! 업데이트하고 출시 노트를 확인해 보세요!`;
}

/**
 * 타이밍 공격 방지 문자열 비교. 길이 다르면 즉시 false.
 * @param a 사용자 입력 (헤더 값)
 * @param b 기대값 (env)
 */
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

/**
 * FCM v1 발급용 access token 조회 (1시간 캐시).
 * @param serviceAccount Firebase Admin 서비스 계정
 * @return OAuth2 access token
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
 * FCM 응답 본문에서 대표 에러 코드 추출. 파싱 실패면 status.
 */
function extractReason(status: number, body: string): string {
  try {
    const parsed = JSON.parse(body) as {
      error?: { status?: string; message?: string };
    };
    const code = parsed.error?.status ?? "";
    const msg = parsed.error?.message ?? "";
    if (code || msg) return `${code || `http_${status}`}${msg ? `: ${msg}` : ""}`;
  } catch {
    // JSON 아님 → 폴백.
  }
  return `http_${status}`;
}

function isStaleTokenError(status: number, body: string): boolean {
  if (status !== 404 && status !== 400) return false;
  return body.includes("UNREGISTERED") || body.includes("INVALID_ARGUMENT");
}

/**
 * 하나의 FCM 토큰에 릴리즈 노트 push 를 발송. 스테일 토큰은 즉시 정리.
 * @param accessToken OAuth2 access token
 * @param projectId Firebase 프로젝트 ID
 * @param token 대상 FCM 등록 토큰
 * @param title 알림 제목
 * @param body 알림 본문
 * @param version 배포 버전 (data payload 로 클라이언트에 전달)
 * @param supabase user_devices stale token 정리용 클라이언트
 */
async function sendFcmMessage(
  accessToken: string,
  projectId: string,
  token: string,
  title: string,
  body: string,
  version: string,
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
          data: { reason: "release_note", version },
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
    console.log(`[release-note] FCM 성공 token=${token.substring(0, 12)}…`);
    return { ok: true };
  }
  const text = await res.text();
  console.error(
    `[release-note] FCM 실패 token=${token.substring(0, 12)}… ${res.status}: ${text}`,
  );
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", token);
    if (error) {
      console.error(`[release-note] 스테일 토큰 삭제 실패: ${error.message}`);
    } else {
      console.log(`[release-note] 스테일 토큰 삭제 완료 token=${token.substring(0, 12)}…`);
    }
  }
  return { ok: false, reason: extractReason(res.status, text) };
}

/** JSON 응답 헬퍼. */
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// semver X.Y.Z (선택적으로 pre-release/build) — 워크플로가 넘기는 값 형식과 맞춘다.
// release.yml 은 순수 semver 만 강제하지만 함수도 방어적으로 재검증.
const VERSION_REGEX = /^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.\-]+)?$/;

serve(async (req) => {
  if (req.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed" });
  }

  try {
    // 1. Shared secret 인증. 워크플로만 호출하므로 CORS/JWT 없음.
    const expected = Deno.env.get("RELEASE_BROADCAST_SECRET") ?? "";
    if (!expected) {
      console.error("[release-note] RELEASE_BROADCAST_SECRET env 미설정");
      return jsonResponse(500, { error: "secret_not_configured" });
    }
    const provided = req.headers.get("x-release-broadcast-secret") ?? "";
    if (!provided || !constantTimeEqual(provided, expected)) {
      console.warn("[release-note] secret 헤더 검증 실패");
      return jsonResponse(401, { error: "invalid_secret" });
    }

    // 2. 요청 파싱 · 유효성.
    let payload: RequestBody;
    try {
      payload = (await req.json()) as RequestBody;
    } catch {
      return jsonResponse(400, { error: "invalid_json" });
    }
    const version = (payload.version ?? "").trim();
    if (!version || !VERSION_REGEX.test(version)) {
      return jsonResponse(400, { error: "invalid_version" });
    }

    const title = NOTIFICATION_TITLE;
    const body = buildBody(version);

    // 3. 전체 user_devices 조회 (service role 로 RLS 우회).
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const { data: devices, error: devErr } = await supabase
      .from("user_devices")
      .select("user_id, fcm_token, platform");
    if (devErr) throw devErr;

    if (!devices || devices.length === 0) {
      console.log("[release-note] 대상 device 없음");
      return jsonResponse(200, { sent: 0, failed: 0, errors: [] });
    }

    // 4. FCM access token 발급.
    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
    const sa = JSON.parse(saJson) as ServiceAccount;
    const accessToken = await getFcmAccessToken(sa);

    // 5. 병렬 발송. per-device 실패는 throw 하지 않고 errors[] 축적.
    type DeviceRow = { user_id: string; fcm_token: string; platform: string };
    const rows = devices as DeviceRow[];
    const results = await Promise.allSettled(
      rows.map((dev) =>
        sendFcmMessage(accessToken, projectId, dev.fcm_token, title, body, version, supabase)
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
        console.error(`[release-note] 예외 user_id=${row.user_id} ${reason}`);
        errors.push({ userId: row.user_id, reason });
      }
    }

    console.log(
      `[release-note] 완료 version=${version} sent=${sent} failed=${failed} total=${rows.length}`,
    );
    return jsonResponse(200, { sent, failed, errors });
  } catch (e) {
    console.error("[release-note] error", e);
    return jsonResponse(500, { error: String(e) });
  }
});
