#!/bin/sh

# Xcode Cloud post-clone hook — TestFlight 빌드 번호를 Xcode Cloud 빌드 번호로 맞춘다 (#433).
#
# 왜 여기서 하나?
#   빌드 번호 (CFBundleVersion) 는 저장소에서 관리하지 않는다. 예전엔 릴리즈마다 Config.xcconfig 의
#   CURRENT_PROJECT_VERSION 을 손으로 +1 했는데, 올리는 걸 잊거나 archive 재시도 때 번호가 겹쳐
#   업로드가 거절되곤 했다. Xcode Cloud 의 CI_BUILD_NUMBER 는 빌드마다 1 씩 늘어나는 유일한 값이라
#   그대로 쓰면 같은 버전을 여러 번 올려도 겹치지 않는다.
#
# 하는 일:
#   clone 직후 (xcodebuild 전) Config.xcconfig 의 CURRENT_PROJECT_VERSION 을 CI_BUILD_NUMBER 로 바꾼다.
#   앱 · 위젯 extension 모두 이 xcconfig 값을 CFBundleVersion 으로 쓰므로 두 번들 번호가 같이 맞는다.
#   작업 트리만 고치고 커밋하지 않는다.
#
# 실패 정책:
#   번호를 못 바꾸면 빌드를 실패시킨다 — 그대로 두면 로컬용 고정값 (1) 으로 archive 돼
#   이미 올라간 번호보다 작아 업로드 단계에서 거절된다. 일찍 실패하는 편이 원인 찾기 쉽다.
#
# 번호가 기존 TestFlight 빌드보다 작아질 일이 생기면 (워크플로를 새로 만든 경우 등)
#   App Store Connect → Xcode Cloud → 설정 → Build Number 에서 다음 빌드 번호를 올린다.
#
# 위치 (#372):
#   Xcode Cloud 는 ci_scripts 폴더를 .xcodeproj 와 같은 디렉터리에서만 찾는다 → iosApp/ci_scripts/.

set -eu

log() { echo "[post-clone] $*"; }

XCCONFIG="${CI_PRIMARY_REPOSITORY_PATH:-$(cd "$(dirname "$0")/../.." && pwd)}/iosApp/Configuration/Config.xcconfig"

case "${CI_BUILD_NUMBER:-}" in
  ''|*[!0-9]*) log "error: CI_BUILD_NUMBER 가 숫자가 아니다 ('${CI_BUILD_NUMBER:-}')"; exit 1 ;;
esac
[ -f "$XCCONFIG" ] || { log "error: $XCCONFIG 가 없다"; exit 1; }

sed -i '' "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=${CI_BUILD_NUMBER}/" "$XCCONFIG"

ACTUAL=$(sed -n 's/^CURRENT_PROJECT_VERSION=//p' "$XCCONFIG")
[ "$ACTUAL" = "$CI_BUILD_NUMBER" ] || { log "error: CURRENT_PROJECT_VERSION 을 바꾸지 못했다 (지금 값: '$ACTUAL')"; exit 1; }

VERSION=$(sed -n 's/^MARKETING_VERSION=//p' "$XCCONFIG")
log "version $VERSION build $CI_BUILD_NUMBER (branch ${CI_BRANCH:-?})"
