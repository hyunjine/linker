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
#   2) CI_XCODEBUILD_EXIT_CODE == 0      — 아카이브 실패 빌드에서 "새 버전 출시" 알림 방지 (#376)
#   3) CI_BRANCH == release              — dev · 다른 브랜치 아카이브 제외
#   4) 필수 env 세 개 (RELEASE_BROADCAST_SECRET · SUPABASE_URL · SUPABASE_ANON_KEY) 존재
#
# 실패 정책 (#376):
#   알림은 부가 기능이라 발송 실패 (env 누락 · 네트워크 · HTTP 오류) 가 빌드 결과를 실패로 만들지
#   않도록 warn 로그만 남기고 exit 0. 빌드가 실패로 표시되면 TestFlight 배포 등 후속 단계가
#   막힐 수 있어서. 누락된 알림은 로그의 `[post-xcodebuild][warn]` 로 확인 후 수동 재발송.
#
# 위치 (#372):
#   Xcode Cloud 는 ci_scripts 폴더를 워크플로가 쓰는 .xcodeproj 와 같은 디렉터리에서만 찾는다.
#   프로젝트가 iosApp/iosApp.xcodeproj 라 이 파일은 반드시 iosApp/ci_scripts/ 에 있어야 한다.
#   (저장소 루트 ci_scripts/ 에 두었을 땐 실행되지 않아 v1.5.0 배포 알림이 누락됐다.)
#   루트 ci_scripts/compile_kotlin_framework.sh 는 Xcode 빌드 단계가 경로를 직접 지정해 부르므로 별개.
#
# 아이덤포턴시:
#   함수 자체가 `release_broadcasts.version` PK 로 재발송 차단. archive 재실행돼도
#   "alreadyBroadcasted": true 리턴만 오고 중복 push 없음.

set -eu

log()  { echo "[post-xcodebuild] $*"; }
warn() { echo "[post-xcodebuild][warn] $*" >&2; }
# 알림 실패는 빌드를 실패시키지 않는다 — 경고만 남기고 정상 종료.
skip() { warn "$* → 브로드캐스트 건너뜀"; exit 0; }

# ── 조건 게이트 ────────────────────────────────────────────────────────────
ACTION="${CI_XCODEBUILD_ACTION:-}"
BRANCH="${CI_BRANCH:-}"
EXIT_CODE="${CI_XCODEBUILD_EXIT_CODE:-}"

log "CI_XCODEBUILD_ACTION=${ACTION}  CI_XCODEBUILD_EXIT_CODE=${EXIT_CODE}  CI_BRANCH=${BRANCH}"

if [ "${ACTION}" != "archive" ]; then
  log "archive 액션 아님 → 브로드캐스트 스킵"
  exit 0
fi

if [ "${EXIT_CODE}" != "0" ]; then
  log "아카이브 실패 (exit=${EXIT_CODE:-unknown}) → 브로드캐스트 스킵"
  exit 0
fi

if [ "${BRANCH}" != "release" ]; then
  log "release 브랜치 아님 → 브로드캐스트 스킵"
  exit 0
fi

# ── MARKETING_VERSION 파싱 ────────────────────────────────────────────────
# Xcode Cloud 는 CI_PRIMARY_REPOSITORY_PATH 에 체크아웃 경로를 넣어준다.
# 로컬 수동 실행 시 fallback: 이 파일 기준 두 단계 위 (iosApp/ci_scripts → 저장소 루트).
REPO_ROOT="${CI_PRIMARY_REPOSITORY_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}"
XCCONFIG="${REPO_ROOT}/iosApp/Configuration/Config.xcconfig"

if [ ! -f "$XCCONFIG" ]; then
  skip "Config.xcconfig 없음: $XCCONFIG"
fi

VERSION=$(grep -m 1 '^MARKETING_VERSION=' "$XCCONFIG" | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$VERSION" ]; then
  skip "MARKETING_VERSION 파싱 실패"
fi
log "version=${VERSION}"

# ── 시크릿 존재 확인 ──────────────────────────────────────────────────────
missing=""
[ -n "${RELEASE_BROADCAST_SECRET:-}" ] || missing="${missing} RELEASE_BROADCAST_SECRET"
[ -n "${SUPABASE_URL:-}" ]             || missing="${missing} SUPABASE_URL"
[ -n "${SUPABASE_ANON_KEY:-}" ]        || missing="${missing} SUPABASE_ANON_KEY"

if [ -n "$missing" ]; then
  skip "필수 env 누락:${missing} (Xcode Cloud → Workflow Environment Variables 확인)"
fi

# ── 함수 호출 ────────────────────────────────────────────────────────────
RESP_FILE=$(mktemp)
trap 'rm -f "$RESP_FILE"' EXIT

log "invoking broadcast-release-note host=$(echo "$SUPABASE_URL" | sed -E 's#^https?://##; s#/.*##')"
# 연결은 15초에 끊고 최대 3회 재시도 (러너 네트워크 일시 오류 대비, #376). 함수가 version PK 로
# 중복 발송을 막으므로 재시도해도 push 는 한 번만 나간다.
HTTP_STATUS=$(curl -sS -o "$RESP_FILE" -w "%{http_code}" \
  --connect-timeout 15 \
  --max-time 60 \
  --retry 3 \
  --retry-delay 10 \
  --retry-all-errors \
  -X POST "${SUPABASE_URL}/functions/v1/broadcast-release-note" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${SUPABASE_ANON_KEY}" \
  -H "x-release-broadcast-secret: ${RELEASE_BROADCAST_SECRET}" \
  -d "{\"version\":\"${VERSION}\",\"source\":\"xcode-cloud\"}") || {
    skip "curl 자체 실패 (network · dns · timeout, 재시도 소진)"
  }

log "response HTTP=${HTTP_STATUS}"
cat "$RESP_FILE" || true
echo

if [ "$HTTP_STATUS" != "200" ]; then
  skip "broadcast HTTP=${HTTP_STATUS}"
fi

# 간단한 결과 요약. jq 는 Xcode Cloud 이미지에 없을 수 있어 grep 폴백.
SENT=$(grep -o '"sent":[[:space:]]*[0-9]*' "$RESP_FILE" | head -1 | grep -o '[0-9]*$' || echo "?")
TOTAL=$(grep -o '"devicesTotal":[[:space:]]*[0-9]*' "$RESP_FILE" | head -1 | grep -o '[0-9]*$' || echo "?")
IDEMPO=$(grep -o '"alreadyBroadcasted":[[:space:]]*\(true\|false\)' "$RESP_FILE" | head -1 | awk -F: '{print $2}' | tr -d '[:space:]' || echo "?")
log "summary sent=${SENT} total=${TOTAL} alreadyBroadcasted=${IDEMPO}"
