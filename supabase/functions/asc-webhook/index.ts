// Supabase Edge Function: asc-webhook
//
// App Store Connect 웹훅 수신 (#395). TestFlight 빌드 처리가 끝나면
// (`BUILD_UPLOAD_STATE_UPDATED` 이벤트, newState = COMPLETE) `broadcast-release-note` 를
// 호출해 배포 완료 push 를 보낸다.
//
// 왜 이 이벤트인가:
//   Linker 는 App Store 출시 없이 TestFlight 내부 그룹으로만 배포한다. 내부 그룹이
//   `hasAccessToAllBuilds` 라 빌드 처리 완료 = 테스터 설치 가능 시점이다.
//   Xcode Cloud archive 직후 (#356) · release 브랜치 push 직후 (#300) 는 처리 완료 전이라
//   알림을 눌러도 새 빌드가 없었다. 수동 폴백: GitHub Actions `release-broadcast.yml` (#380).
//
// 인증:
//   Apple 은 Authorization 헤더를 보내지 않으므로 verify_jwt = false (config.toml).
//   대신 `x-apple-signature: hmacsha256=<hex>` 를 요청 본문 원문 · `ASC_WEBHOOK_SECRET` 으로
//   HMAC-SHA256 검증한다.
//
// 페이로드 (Apple 문서 예시):
//   { "data": { "type": "buildUploadStateUpdated", "id": "...", "version": 1,
//       "attributes": { "newState": "COMPLETE" },
//       "relationships": { "instance": { "data": { "type": "buildUploads", "id": "..." } } } } }
//   버전 문자열이 없어서 `GET /v1/buildUploads/{id}` 로 cfBundleShortVersionString · platform 을 조회한다.
//
// 응답 정책:
//   - 서명 불일치 401. 그 외 처리 대상이 아닌 이벤트 (ping · 다른 상태 · 다른 플랫폼) 는 200 ignored.
//   - 같은 버전의 빌드가 여러 번 올라와도 첫 COMPLETE 에서만 발송된다 (version PK).
//   - ASC 조회 · 브로드캐스트 실패는 502 → App Store Connect deliveries 에 실패로 남아
//     `POST /v1/webhookDeliveries` 로 재전송 가능. 재전송돼도 broadcast-release-note 가
//     version PK 로 중복 발송을 막는다.
//
// 필요한 Supabase Secrets:
//   ASC_WEBHOOK_SECRET        — 웹훅 등록 시 넣은 secret
//   ASC_API_KEY_ID            — App Store Connect API 키 ID
//   ASC_API_ISSUER_ID         — App Store Connect API Issuer ID
//   ASC_API_PRIVATE_KEY       — API 키 .p8 파일 원문 (PEM)
//   RELEASE_BROADCAST_SECRET  — broadcast-release-note 호출용 (기존)
//   SUPABASE_URL · SUPABASE_SERVICE_ROLE_KEY — 플랫폼 자동 주입

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const EVENT_TYPE = "buildUploadStateUpdated";
const COMPLETE_STATE = "COMPLETE";
const ASC_API = "https://api.appstoreconnect.apple.com/v1";
const SIGNATURE_PREFIX = "hmacsha256=";

interface WebhookPayload {
  data?: {
    type?: string;
    id?: string;
    attributes?: { newState?: string; oldState?: string; timestamp?: string };
    relationships?: { instance?: { data?: { type?: string; id?: string } } };
  };
}

interface BuildUpload {
  version: string;
  platform: string;
  state: string;
}

function log(rid: string, stage: string, msg = "") {
  console.log(`[asc-webhook][${stage}] rid=${rid}${msg ? ` ${msg}` : ""}`);
}
function warn(rid: string, stage: string, msg = "") {
  console.warn(`[asc-webhook][${stage}] rid=${rid}${msg ? ` ${msg}` : ""}`);
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * 타이밍 공격 방지 문자열 비교. 길이 다르면 즉시 false.
 * @param a 사용자 입력 (헤더 값)
 * @param b 기대값
 */
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

/**
 * 요청 본문 원문의 HMAC-SHA256 hex digest.
 * @param secret 웹훅 등록 시 넣은 secret
 * @param body 요청 본문 원문 (파싱 전)
 * @return 소문자 hex 문자열
 */
async function hmacSha256Hex(secret: string, body: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(body));
  return Array.from(new Uint8Array(sig), (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * `x-apple-signature` 헤더 검증.
 * @param header 헤더 값 (`hmacsha256=<hex>`)
 * @param secret 웹훅 secret
 * @param body 요청 본문 원문
 */
async function verifySignature(header: string, secret: string, body: string): Promise<boolean> {
  if (!header.startsWith(SIGNATURE_PREFIX)) return false;
  const provided = header.slice(SIGNATURE_PREFIX.length).trim().toLowerCase();
  const expected = await hmacSha256Hex(secret, body);
  return constantTimeEqual(provided, expected);
}

/**
 * App Store Connect API 용 ES256 JWT 발급 (유효 10분).
 * @param keyId API 키 ID
 * @param issuerId Issuer ID
 * @param pem .p8 파일 원문
 */
async function createAscToken(keyId: string, issuerId: string, pem: string): Promise<string> {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  return jwtCreate(
    { alg: "ES256", typ: "JWT", kid: keyId },
    {
      iss: issuerId,
      iat: getNumericDate(0),
      exp: getNumericDate(10 * 60),
      aud: "appstoreconnect-v1",
    },
    key,
  );
}

/**
 * buildUploads 리소스에서 버전 문자열 · 플랫폼 · 처리 상태 조회.
 * @param id 웹훅 relationships.instance.data.id
 * @param token ASC JWT
 */
async function fetchBuildUpload(id: string, token: string): Promise<BuildUpload> {
  const url = `${ASC_API}/buildUploads/${encodeURIComponent(id)}` +
    "?fields[buildUploads]=cfBundleShortVersionString,platform,state";
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) {
    throw new Error(`buildUploads 조회 실패 ${res.status}: ${await res.text()}`);
  }
  const json = (await res.json()) as {
    data?: {
      attributes?: {
        cfBundleShortVersionString?: string;
        platform?: string;
        state?: { state?: string };
      };
    };
  };
  const attrs = json.data?.attributes ?? {};
  if (!attrs.cfBundleShortVersionString || !attrs.platform) {
    throw new Error("buildUploads 응답에 cfBundleShortVersionString · platform 없음");
  }
  return {
    version: attrs.cfBundleShortVersionString,
    platform: attrs.platform,
    state: attrs.state?.state ?? "",
  };
}

serve(async (req) => {
  const rid = crypto.randomUUID();

  if (req.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed", requestId: rid });
  }

  const webhookSecret = Deno.env.get("ASC_WEBHOOK_SECRET") ?? "";
  if (!webhookSecret) {
    warn(rid, "SECRET_ENV_MISSING", "ASC_WEBHOOK_SECRET");
    return jsonResponse(500, { error: "secret_not_configured", requestId: rid });
  }

  // 서명은 파싱 전 원문 기준.
  const raw = await req.text();
  const signature = req.headers.get("x-apple-signature") ?? "";
  if (!(await verifySignature(signature, webhookSecret, raw))) {
    warn(rid, "SIGNATURE_INVALID", `header_len=${signature.length}`);
    return jsonResponse(401, { error: "invalid_signature", requestId: rid });
  }

  let payload: WebhookPayload;
  try {
    payload = JSON.parse(raw) as WebhookPayload;
  } catch {
    warn(rid, "PAYLOAD_JSON_INVALID");
    return jsonResponse(400, { error: "invalid_json", requestId: rid });
  }

  const type = payload.data?.type ?? "";
  const newState = payload.data?.attributes?.newState ?? "";
  const uploadId = payload.data?.relationships?.instance?.data?.id ?? "";
  log(rid, "EVENT", `type=${type} new=${newState} uploadId=${uploadId}`);

  // ping · 다른 이벤트 · 처리 중 · 실패 상태는 수신만 확인.
  if (type !== EVENT_TYPE || newState !== COMPLETE_STATE) {
    return jsonResponse(200, { ignored: true, requestId: rid });
  }
  if (!uploadId) {
    warn(rid, "UPLOAD_ID_MISSING");
    return jsonResponse(400, { error: "missing_upload_id", requestId: rid });
  }

  try {
    const token = await createAscToken(
      Deno.env.get("ASC_API_KEY_ID") ?? "",
      Deno.env.get("ASC_API_ISSUER_ID") ?? "",
      Deno.env.get("ASC_API_PRIVATE_KEY") ?? "",
    );
    const upload = await fetchBuildUpload(uploadId, token);
    log(rid, "UPLOAD_FETCHED", `version=${upload.version} platform=${upload.platform} state=${upload.state}`);

    // iOS 빌드만 알림 대상. 이벤트와 실제 상태가 어긋나면 (재전송 등) 발송하지 않는다.
    if (upload.platform !== "IOS" || upload.state !== COMPLETE_STATE) {
      return jsonResponse(200, { ignored: true, requestId: rid, platform: upload.platform, state: upload.state });
    }

    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/broadcast-release-note`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
        "x-release-broadcast-secret": Deno.env.get("RELEASE_BROADCAST_SECRET") ?? "",
      },
      body: JSON.stringify({ version: upload.version, source: "asc-webhook" }),
    });
    const text = await res.text();
    log(rid, "BROADCAST_DONE", `http=${res.status} body=${text}`);
    if (!res.ok) {
      return jsonResponse(502, { error: "broadcast_failed", status: res.status, requestId: rid });
    }
    return jsonResponse(200, { broadcast: JSON.parse(text), requestId: rid });
  } catch (e) {
    warn(rid, "FAILED", String(e));
    return jsonResponse(502, { error: "internal", message: String(e), requestId: rid });
  }
});
