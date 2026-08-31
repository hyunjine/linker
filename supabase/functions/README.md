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
