# Supabase Edge Functions

## send-schedule-push

파트너가 스케줄을 등록하면 FCM 푸시로 알려주는 함수.

### 배포

```
supabase functions deploy send-schedule-push
```

### 필요한 Secrets

Dashboard → Project Settings → Edge Functions → Secrets:

- `FCM_PROJECT_ID` — Firebase 프로젝트 ID (예: `linker-xxxx`)
- `FCM_SERVICE_ACCOUNT_JSON` — Firebase Admin 서비스 계정 JSON 전체를 한 줄 문자열로

Firebase Admin 서비스 계정 만들기:
1. https://console.firebase.google.com → 프로젝트 → Project Settings
2. **Service accounts** 탭 → **Generate new private key** → JSON 다운로드
3. 다운받은 JSON 내용을 그대로 Supabase secret 에 붙여넣기

### 트리거 등록

`send-schedule-push` 함수 URL 을 얻은 뒤, Supabase Dashboard 또는 SQL 로
`schedules` INSERT 시 이 함수를 호출하도록 `pg_net` 기반 trigger 를 걸어야 함.

Phase A 스켈레톤은 발송 로직 미구현. Phase B 에서 채운다.

## send-announcement

관리자 콘솔(#261) 에서 호출하는 공지 푸시 발송 함수. 요청 JWT 를 검증하고
`ADMIN_UIDS` 화이트리스트를 다시 확인한 뒤 대상 유저의 모든 `user_devices` 로 병렬 발송.

### 배포

```
supabase functions deploy send-announcement
```

### 요청 · 응답

- Request: `{ "title": string, "body": string, "userIds": string[] | "all" }`
- Response (200): `{ "sent": <n>, "failed": <n>, "errors": [{ "userId": "...", "reason": "..." }] }`
- 401 · 403 · 400 은 각각 JWT 없음/무효 · 관리자 아님 · 유효성 실패.

### 필요한 Secrets

- `FCM_PROJECT_ID` · `FCM_SERVICE_ACCOUNT_JSON` — send-schedule-push 와 동일
- `ADMIN_UIDS` — 관리자 auth uid 콤마 구분 리스트 (예: `uuid1,uuid2`)
- `SUPABASE_URL` · `SUPABASE_ANON_KEY` · `SUPABASE_SERVICE_ROLE_KEY` — 기본 제공

## asc-webhook

App Store Connect 웹훅 수신 (#395). TestFlight 빌드 처리 완료 (`BUILD_UPLOAD_STATE_UPDATED` →
`COMPLETE`) 시 `buildUploads/{id}` 로 버전을 조회해 `broadcast-release-note` 를 호출한다.
`verify_jwt = false` (config.toml) — 대신 `x-apple-signature` (HMAC-SHA256) 로 검증.

### 필요한 Secrets

- `ASC_WEBHOOK_SECRET` — 웹훅 등록 (`POST /v1/webhooks`) 시 넣은 secret
- `ASC_API_KEY_ID` · `ASC_API_ISSUER_ID` · `ASC_API_PRIVATE_KEY` (.p8 원문) — App Store Connect API 키
- `RELEASE_BROADCAST_SECRET` — broadcast-release-note 와 동일

### 웹훅 등록

함수 배포 후 App Store Connect API 로 `POST /v1/webhooks` (eventTypes `BUILD_UPLOAD_STATE_UPDATED`,
url `https://<project>.supabase.co/functions/v1/asc-webhook`) 등록 → `POST /v1/webhookPings` 로 확인.
전달 이력은 `GET /v1/webhooks/{id}/deliveries`, 재전송은 `POST /v1/webhookDeliveries`.

## send-outlook-sync-push

Outlook 재택 미러 주기 동기화 (#420). pg_cron (`send-outlook-sync-push`, 30분마다 · KST 01:00~05:59 제외) 이
호출하면 `users.outlook_connected_at` 이 있는 유저의 모든 기기에 `data.type = "outlook_sync"` silent / data push 를
보낸다. 앱이 깨어나 `OutlookBackgroundSync` 로 기존 sync 를 실행한다 (Graph 변경 알림은 delegated 권한으로
공유 캘린더를 구독할 수 없어 polling).
`verify_jwt = false` (config.toml) — 대신 `x-outlook-sync-secret` 헤더로 검증.

### 필요한 Secrets

- `FCM_PROJECT_ID` · `FCM_SERVICE_ACCOUNT_JSON` — send-schedule-push 와 동일
- `OUTLOOK_SYNC_PUSH_SECRET` — cron → 함수 인증용. 같은 값을 Vault 에도 저장:
  `select vault.create_secret('<값>', 'outlook_sync_push_secret');`
