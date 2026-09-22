#!/bin/sh

# Xcode Cloud post-xcodebuild hook — release archive 성공 후에 배포 알림 push 를 발사한다 (#356).
#
# 왜 여기서 fire 하나?
#   이전엔 .github/workflows/release.yml (release 브랜치 push 트리거) 이 broadcast-release-note
#   함수를 호출했지만, 실제 IPA 는 Xcode Cloud 아카이브가 끝나야 준비된다. GitHub Actions 트리거
#   순간엔 스토어에 새 버전이 없는데 사용자에겐 "새 버전 출시!" 알림이 이미 갔음 → 알림 눌러
#   앱스토어 가면 아직 이전 버전. 트리거를 archive 성공 직후로 이동.
#
# 발사 조건 (모두 true 여야 fire):
#   1) CI_XCODEBUILD_ACTION == archive   — 테스트 · PR 체크 · 시뮬 빌드 제외
#   2) CI_BRANCH == release              — dev · 다른 브랜치 아카이브 제외
#   3) 필수 env 세 개 (RELEASE_BROADCAST_SECRET · SUPABASE_URL · SUPABASE_ANON_KEY) 존재
#
# 아이덤포턴시:
#   함수 자체가 `release_broadcasts.version` PK 로 재발송 차단. archive 재실행돼도
#   "alreadyBroadcasted": true 리턴만 오고 중복 push 없음.

set -eu

log()  { echo "[post-xcodebuild] $*"; }
warn() { echo "[post-xcodebuild][warn] $*" >&2; }
fail() { echo "[post-xcodebuild][fail] $*" >&2; exit 1; }

# ── 조건 게이트 ────────────────────────────────────────────────────────────
ACTION="${CI_XCODEBUILD_ACTION:-}"
BRANCH="${CI_BRANCH:-}"

log "CI_XCODEBUILD_ACTION=${ACTION}  CI_BRANCH=${BRANCH}"

if [ "${ACTION}" != "archive" ]; then
  log "archive 액션 아님 → 브로드캐스트 스킵"
  exit 0
fi

if [ "${BRANCH}" != "release" ]; then
  log "release 브랜치 아님 → 브로드캐스트 스킵"
  exit 0
fi

# ── MARKETING_VERSION 파싱 ────────────────────────────────────────────────
# Xcode Cloud 는 CI_PRIMARY_REPOSITORY_PATH 에 체크아웃 경로를 넣어준다.
REPO_ROOT="${CI_PRIMARY_REPOSITORY_PATH:-$(cd "$(dirname "$0")/.." && pwd)}"
XCCONFIG="${REPO_ROOT}/iosApp/Configuration/Config.xcconfig"

if [ ! -f "$XCCONFIG" ]; then
  fail "Config.xcconfig 없음: $XCCONFIG"
fi

VERSION=$(grep -m 1 '^MARKETING_VERSION=' "$XCCONFIG" | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$VERSION" ]; then
  fail "MARKETING_VERSION 파싱 실패"
fi
log "version=${VERSION}"

# ── 시크릿 존재 확인 ──────────────────────────────────────────────────────
missing=""
[ -n "${RELEASE_BROADCAST_SECRET:-}" ] || missing="${missing} RELEASE_BROADCAST_SECRET"
[ -n "${SUPABASE_URL:-}" ]             || missing="${missing} SUPABASE_URL"
[ -n "${SUPABASE_ANON_KEY:-}" ]        || missing="${missing} SUPABASE_ANON_KEY"

if [ -n "$missing" ]; then
  fail "필수 env 누락:${missing} (Xcode Cloud → Workflow Environment Variables 확인)"
fi

# ── 함수 호출 ────────────────────────────────────────────────────────────
RESP_FILE=$(mktemp)
trap 'rm -f "$RESP_FILE"' EXIT

log "invoking broadcast-release-note"
HTTP_STATUS=$(curl -sS -o "$RESP_FILE" -w "%{http_code}" \
  --max-time 60 \
  -X POST "${SUPABASE_URL}/functions/v1/broadcast-release-note" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${SUPABASE_ANON_KEY}" \
  -H "x-release-broadcast-secret: ${RELEASE_BROADCAST_SECRET}" \
  -d "{\"version\":\"${VERSION}\",\"source\":\"xcode-cloud\"}") || {
    fail "curl 자체 실패 (network · dns · timeout)"
  }

log "response HTTP=${HTTP_STATUS}"
cat "$RESP_FILE" || true
echo

if [ "$HTTP_STATUS" != "200" ]; then
  fail "broadcast HTTP=${HTTP_STATUS}"
fi

# 간단한 결과 요약. jq 는 Xcode Cloud 이미지에 없을 수 있어 grep 폴백.
SENT=$(grep -o '"sent":[[:space:]]*[0-9]*' "$RESP_FILE" | head -1 | grep -o '[0-9]*$' || echo "?")
TOTAL=$(grep -o '"devicesTotal":[[:space:]]*[0-9]*' "$RESP_FILE" | head -1 | grep -o '[0-9]*$' || echo "?")
IDEMPO=$(grep -o '"alreadyBroadcasted":[[:space:]]*\(true\|false\)' "$RESP_FILE" | head -1 | awk -F: '{print $2}' | tr -d '[:space:]' || echo "?")
log "summary sent=${SENT} total=${TOTAL} alreadyBroadcasted=${IDEMPO}"
