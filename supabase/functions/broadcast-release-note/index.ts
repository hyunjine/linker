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
// 관측 · idempotency (#300 후속):
//   - 진입 즉시 request_id (uuid) 발급 → 모든 로그 라인에 `rid=<uuid>` 로 붙여
//     워크플로 stdout · 함수 로그 · public.release_broadcasts 감사 테이블을 삼중 상관.
//   - `public.release_broadcasts.version` 이 PK 라 같은 버전 재호출은 기존 결과 idempotent 반환.
//     `completed_at IS NULL` (in-flight · 이전 실패) 이면 재시도로 간주하고 발송 재실행.
//   - 각 stage 진입 · 종료마다 `[release-note][STAGE] …` prefix 로그 → Supabase Logs Explorer 에서
//     stage 별 필터가 그대로 통한다.
//
// 필요한 Supabase Secrets:
//   FCM_PROJECT_ID              — Firebase 프로젝트 ID
//   FCM_SERVICE_ACCOUNT_JSON    — Firebase Admin 서비스 계정 JSON (문자열)
//   RELEASE_BROADCAST_SECRET    — 워크플로 → 함수 인증용 shared secret
//   SUPABASE_URL                — 감사 테이블 접근용 (플랫폼이 자동 주입)
//   SUPABASE_SERVICE_ROLE_KEY   — 감사 테이블 · user_devices 접근용 (플랫폼 자동 주입)
//
// Request body:
//   { "version": string, "source"?: "workflow" | "manual" }   // 예: "1.4.2"
//
// Response:
//   200 { requestId, version, sent, failed, devicesTotal, alreadyBroadcasted, errors[] }
//   400 유효성 실패 (invalid_json · invalid_version)
//   401 secret 헤더 없음/불일치
//   500 예기치 못한 서버 오류 (secret env 미설정 · DB 예외 등)

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create as jwtCreate, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

interface RequestBody {
  version?: string;
  source?: "workflow" | "manual";
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

interface AuditRow {
  version: string;
  request_id: string;
  started_at?: string;
  completed_at?: string | null;
  devices_total?: number | null;
  sent?: number | null;
  failed?: number | null;
  errors?: SendError[] | null;
  source?: string;
}

const NOTIFICATION_TITLE = "🚨긴급🚨";
const AUDIT_TABLE = "release_broadcasts";

// 짧은 stage 별 prefix 로그. Supabase Logs Explorer 에서 `[release-note][STAGE]` grep 이 통한다.
function stageLog(rid: string, stage: string, msg = "") {
  console.log(`[release-note][${stage}] rid=${rid}${msg ? ` ${msg}` : ""}`);
}
function stageWarn(rid: string, stage: string, msg = "") {
  console.warn(`[release-note][${stage}] rid=${rid}${msg ? ` ${msg}` : ""}`);
}
function stageErr(rid: string, stage: string, msg = "") {
  console.error(`[release-note][${stage}] rid=${rid}${msg ? ` ${msg}` : ""}`);
}

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
 * @param rid 이번 함수 호출의 request_id (로그 상관용)
 * @param accessToken OAuth2 access token
 * @param projectId Firebase 프로젝트 ID
 * @param token 대상 FCM 등록 토큰
 * @param title 알림 제목
 * @param body 알림 본문
 * @param version 배포 버전 (data payload 로 클라이언트에 전달)
 * @param supabase user_devices stale token 정리용 클라이언트
 */
async function sendFcmMessage(
  rid: string,
  accessToken: string,
  projectId: string,
  token: string,
  title: string,
  body: string,
  version: string,
  supabase: ReturnType<typeof createClient>,
): Promise<{ ok: true } | { ok: false; reason: string }> {
  const short = token.substring(0, 12);
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
    stageLog(rid, "FCM_OK", `token=${short}…`);
    return { ok: true };
  }
  const text = await res.text();
  stageErr(rid, "FCM_FAIL", `token=${short}… status=${res.status} body=${text}`);
  if (isStaleTokenError(res.status, text)) {
    const { error } = await supabase.from("user_devices").delete().eq("fcm_token", token);
    if (error) {
      stageErr(rid, "STALE_DELETE_FAIL", `token=${short}… err=${error.message}`);
    } else {
      stageLog(rid, "STALE_DELETE_OK", `token=${short}…`);
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


/** 알림 내역 (#303) 한 줄. `public.notifications` 컬럼과 1:1. */
interface NotificationRecord {
  kind: "partner" | "reminder" | "announcement" | "update";
  title: string;
  body: string;
  scheduleId?: string | null;
  /** 재시도 중복 방지 키. 있으면 (user_id, dedupe_key) 충돌 시 무시. */
  dedupeKey?: string | null;
}

/**
 * 수신자별로 알림 내역을 남긴다 (#303). 기록 실패는 로그만 남기고 삼킨다 — 푸시 발송을 막지 않기 위함.
 *
 * @param supabase service role 클라이언트.
 * @param userIds 수신자 user id 목록 (device 가 없어도 기록).
 * @param record 기록할 내용.
 */
async function recordNotifications(
  supabase: ReturnType<typeof createClient>,
  userIds: string[],
  record: NotificationRecord,
): Promise<void> {
  const uniqueIds = [...new Set(userIds)];
  if (uniqueIds.length === 0) return;
  const rows = uniqueIds.map((userId) => ({
    user_id: userId,
    kind: record.kind,
    title: record.title,
    body: record.body,
    schedule_id: record.scheduleId ?? null,
    dedupe_key: record.dedupeKey ?? null,
  }));
  try {
    const { error } = record.dedupeKey
      ? await supabase
        .from("notifications")
        .upsert(rows, { onConflict: "user_id,dedupe_key", ignoreDuplicates: true })
      : await supabase.from("notifications").insert(rows);
    if (error) {
      console.error(`[notifications] 기록 실패 kind=${record.kind} users=${uniqueIds.length} ${error.message}`);
    }
  } catch (e) {
    console.error(`[notifications] 기록 예외 kind=${record.kind}`, e);
  }
}

serve(async (req) => {
  // 진입 즉시 request_id 를 뽑아 모든 stage 로그 · 감사 row 에 사용.
  const rid = crypto.randomUUID();

  if (req.method !== "POST") {
    stageWarn(rid, "METHOD_REJECT", `method=${req.method}`);
    return jsonResponse(405, { error: "method_not_allowed", requestId: rid });
  }

  stageLog(rid, "INVOKE");

  try {
    // 1. Shared secret 인증. 워크플로만 호출하므로 CORS/JWT 없음.
    const expected = Deno.env.get("RELEASE_BROADCAST_SECRET") ?? "";
    if (!expected) {
      stageErr(rid, "SECRET_ENV_MISSING");
      return jsonResponse(500, { error: "secret_not_configured", requestId: rid });
    }
    const provided = req.headers.get("x-release-broadcast-secret") ?? "";
    if (!provided || !constantTimeEqual(provided, expected)) {
      stageWarn(rid, "SECRET_INVALID", `provided_len=${provided.length}`);
      return jsonResponse(401, { error: "invalid_secret", requestId: rid });
    }
    stageLog(rid, "SECRET_OK");

    // 2. 요청 파싱 · 유효성.
    let payload: RequestBody;
    try {
      payload = (await req.json()) as RequestBody;
    } catch {
      stageWarn(rid, "PAYLOAD_JSON_INVALID");
      return jsonResponse(400, { error: "invalid_json", requestId: rid });
    }
    const version = (payload.version ?? "").trim();
    if (!version || !VERSION_REGEX.test(version)) {
      stageWarn(rid, "PAYLOAD_VERSION_INVALID", `version=${version || "(empty)"}`);
      return jsonResponse(400, { error: "invalid_version", requestId: rid });
    }
    const source: "workflow" | "manual" =
      payload.source === "manual" ? "manual" : "workflow";
    stageLog(rid, "PAYLOAD_OK", `version=${version} source=${source}`);

    // 3. 감사 테이블 접근용 클라이언트. service role 로 RLS 우회.
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 3a. Idempotency 체크 · in-flight 표식.
    //   - 완료 (completed_at != null) 면 재발송 없이 기존 결과 반환.
    //   - 진행 중 (completed_at == null) 이면 이전 시도가 도중 실패 → 재시도로 진행하되
    //     기존 row 의 request_id 를 그대로 유지 (감사 연속성).
    //   - 없으면 새로 insert.
    const audit = await supabase
      .from(AUDIT_TABLE)
      .select("*")
      .eq("version", version)
      .maybeSingle<AuditRow>();
    if (audit.error) {
      stageErr(rid, "AUDIT_SELECT_FAIL", `err=${audit.error.message}`);
      // 감사 조회 실패는 fatal 로 취급 — 발송 자체는 가능해 보이지만 idempotency 를 보장 못 하면
      // 워크플로 재실행 시 중복 발송 리스크가 큼.
      return jsonResponse(500, {
        error: "audit_lookup_failed",
        requestId: rid,
        detail: audit.error.message,
      });
    }

    if (audit.data?.completed_at) {
      stageLog(
        rid,
        "IDEMPOTENT_SKIP",
        `version=${version} originalRid=${audit.data.request_id} completedAt=${audit.data.completed_at}`,
      );
      return jsonResponse(200, {
        requestId: rid,
        originalRequestId: audit.data.request_id,
        version,
        sent: audit.data.sent ?? 0,
        failed: audit.data.failed ?? 0,
        devicesTotal: audit.data.devices_total ?? 0,
        alreadyBroadcasted: true,
        errors: audit.data.errors ?? [],
      });
    }

    // in-flight row 없으면 새로 insert (request_id 유지 목적).
    // conflict 시 기존 row 유지 · 이번 rid 는 로그만 사용.
    const auditRid = audit.data?.request_id ?? rid;
    if (!audit.data) {
      const ins = await supabase
        .from(AUDIT_TABLE)
        .insert({
          version,
          request_id: rid,
          source,
        });
      if (ins.error) {
        stageErr(rid, "AUDIT_INSERT_FAIL", `err=${ins.error.message}`);
        return jsonResponse(500, {
          error: "audit_insert_failed",
          requestId: rid,
          detail: ins.error.message,
        });
      }
      stageLog(rid, "AUDIT_INSERT_OK", `version=${version}`);
    } else {
      stageWarn(
        rid,
        "AUDIT_RETRY",
        `version=${version} originalRid=${auditRid} startedAt=${audit.data.started_at}`,
      );
    }

    const title = NOTIFICATION_TITLE;
    const body = buildBody(version);

    // 3b. 알림 내역 (#303) — 전체 유저에게 기록. 푸시 제목 ("🚨긴급🚨") 대신 내역에선 버전을 제목으로.
    //     재시도 (in-flight) 로 여기가 다시 실행돼도 dedupe_key 로 한 번만 남는다.
    {
      const { data: users, error: uErr } = await supabase.from("users").select("id");
      if (uErr) {
        stageErr(rid, "HISTORY_USERS_FAIL", `err=${uErr.message}`);
      } else {
        await recordNotifications(
          supabase,
          (users ?? []).map((u: { id: string }) => u.id),
          {
            kind: "update",
            title: `v${version} 업데이트`,
            body: "새 버전이 출시되었어요. 업데이트하고 출시 노트를 확인해 보세요!",
            dedupeKey: `release:${version}`,
          },
        );
        stageLog(rid, "HISTORY_RECORDED", `users=${users?.length ?? 0}`);
      }
    }

    // 4. 전체 user_devices 조회 (service role 로 RLS 우회).
    const devQuery = await supabase
      .from("user_devices")
      .select("user_id, fcm_token, platform");
    if (devQuery.error) {
      stageErr(rid, "DEVICES_QUERY_FAIL", `err=${devQuery.error.message}`);
      throw devQuery.error;
    }
    const devices = devQuery.data ?? [];
    stageLog(rid, "DEVICES_FETCHED", `count=${devices.length}`);

    if (devices.length === 0) {
      stageWarn(rid, "DEVICES_EMPTY");
      const emptyPatch = await supabase
        .from(AUDIT_TABLE)
        .update({
          completed_at: new Date().toISOString(),
          devices_total: 0,
          sent: 0,
          failed: 0,
          errors: [],
        })
        .eq("version", version);
      if (emptyPatch.error) {
        stageErr(rid, "AUDIT_PATCH_EMPTY_FAIL", `err=${emptyPatch.error.message}`);
      }
      return jsonResponse(200, {
        requestId: rid,
        version,
        sent: 0,
        failed: 0,
        devicesTotal: 0,
        alreadyBroadcasted: false,
        errors: [],
      });
    }

    // 5. FCM access token 발급.
    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const saJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON")!;
    const sa = JSON.parse(saJson) as ServiceAccount;
    let accessToken: string;
    try {
      accessToken = await getFcmAccessToken(sa);
      stageLog(rid, "FCM_TOKEN_OK");
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      stageErr(rid, "FCM_TOKEN_FAIL", `err=${msg}`);
      // 토큰 획득 실패는 발송 전면 실패. 감사 row 는 completed_at 없이 두어 재시도 여지 유지.
      throw e;
    }

    // 6. 병렬 발송. per-device 실패는 throw 하지 않고 errors[] 축적.
    stageLog(rid, "FCM_SEND_START", `devices=${devices.length}`);
    type DeviceRow = { user_id: string; fcm_token: string; platform: string };
    const rows = devices as DeviceRow[];
    const results = await Promise.allSettled(
      rows.map((dev) =>
        sendFcmMessage(rid, accessToken, projectId, dev.fcm_token, title, body, version, supabase)
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
        stageErr(rid, "FCM_SEND_EXCEPTION", `user_id=${row.user_id} err=${reason}`);
        errors.push({ userId: row.user_id, reason });
      }
    }

    stageLog(
      rid,
      "FCM_SEND_DONE",
      `sent=${sent} failed=${failed} total=${rows.length}`,
    );

    // 7. 감사 row 완료 기록.
    const patch = await supabase
      .from(AUDIT_TABLE)
      .update({
        completed_at: new Date().toISOString(),
        devices_total: rows.length,
        sent,
        failed,
        errors,
      })
      .eq("version", version);
    if (patch.error) {
      stageErr(rid, "AUDIT_PATCH_FAIL", `err=${patch.error.message}`);
      // 발송은 성공했지만 감사 갱신 실패 — 응답에는 실제 결과 담아 200 반환 (idempotency 는
      // 완료 판정이 없어 다음 재시도 시 재발송이 될 수 있음. 이 편이 조용히 누락되는 것보다 안전).
    } else {
      stageLog(rid, "AUDIT_PATCH_OK");
    }

    stageLog(
      rid,
      "COMPLETE",
      `version=${version} sent=${sent} failed=${failed} total=${rows.length}`,
    );
    return jsonResponse(200, {
      requestId: rid,
      version,
      sent,
      failed,
      devicesTotal: rows.length,
      alreadyBroadcasted: false,
      errors,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    stageErr(rid, "UNCAUGHT", msg);
    return jsonResponse(500, { error: msg, requestId: rid });
  }
});
