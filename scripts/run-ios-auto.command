#!/bin/bash
# 옵션 B — Xcode UI 없이 완전 자동:
#   1) git pull
#   2) xcodebuild 로 device build (자동 provisioning 갱신)
#   3) xcrun devicectl 로 기기 install + launch
#
# 사전 준비 (한 번만):
#   - Xcode 설치 & Apple ID 로그인 (Xcode → Settings → Accounts → 팀 fetch)
#   - iosApp/Configuration/Config.xcconfig 존재 (KAKAO_NATIVE_APP_KEY · TEAM_ID)
#   - local.properties 존재 (supabase · kakao · holiday 키)
#   - iPhone 케이블 연결 + Trust This Computer 승인
#   - iPhone: 설정 → 개인정보 보호 및 보안 → 개발자 모드 ON
#
# 최초 1회는 Xcode 로 실행해 signing · provisioning profile 을 자동 생성해 두는 게 안전.
# 이후로는 이 스크립트로 재실행 가능.

set -euo pipefail
cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

echo "───────────────────────────────────────────"
echo " Linker — iOS 완전 자동 실행"
echo " 위치: $REPO_ROOT"
echo "───────────────────────────────────────────"

# 0. 필수 파일. 없으면 .example 로부터 자동 복사 후 사용자에게 값 채우도록 안내.
copied=0
if [[ ! -f "iosApp/Configuration/Config.xcconfig" ]]; then
  cp iosApp/Configuration/Config.xcconfig.example iosApp/Configuration/Config.xcconfig
  echo "  ▸ Config.xcconfig 생성 — TEAM_ID · KAKAO_NATIVE_APP_KEY 채워주세요."
  copied=1
fi
if [[ ! -f "local.properties" ]]; then
  cp local.properties.example local.properties
  echo "  ▸ local.properties 생성 — supabase / kakao / holiday 키 채워주세요."
  copied=1
fi
if (( copied == 1 )); then
  echo ""
  echo "  ↑ 값 채운 후 다시 실행하세요."
  read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
  exit 1
fi

# 1. git pull (앞선 커밋 미푸시면 스킵).
echo ""
echo "==> git pull"
git fetch --all --prune
current_branch=$(git rev-parse --abbrev-ref HEAD)
echo "    브랜치: $current_branch"
if git merge-base --is-ancestor HEAD "origin/$current_branch" 2>/dev/null; then
  git pull --ff-only
else
  echo "  ⚠  로컬이 원격보다 앞서있음. pull 스킵."
fi

# 2. 연결된 기기 UDID 자동 감지 (첫 번째 iPhone).
echo ""
echo "==> 연결된 iPhone 감지"
DEVICES_JSON=$(mktemp)
xcrun devicectl list devices --json-output "$DEVICES_JSON" > /dev/null 2>&1 || {
  echo "  ✘ xcrun devicectl 실패. Xcode 16+ 필요. 기기 신뢰 · 개발자 모드 확인."
  read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
  exit 1
}

UDID=$(python3 - "$DEVICES_JSON" <<'EOF'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
for dev in data.get("result", {}).get("devices", []):
    dtype = dev.get("deviceProperties", {}).get("bootedState", "")
    conn = dev.get("connectionProperties", {}).get("tunnelState", "")
    plat = dev.get("hardwareProperties", {}).get("platform", "")
    # 연결되어 있고 iOS 인 기기
    if plat.lower().startswith("ios") and conn in ("connected", "unavailable"):
        print(dev["hardwareProperties"]["udid"])
        break
EOF
)

if [[ -z "$UDID" ]]; then
  echo "  ✘ 연결된 iPhone 을 찾지 못함. 케이블 · 신뢰 · 개발자 모드 확인."
  read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
  exit 1
fi
echo "    UDID: $UDID"

# 3. 기기용 build. 자동 provisioning 갱신 (-allowProvisioningUpdates).
echo ""
echo "==> xcodebuild (device build)"
DERIVED="$REPO_ROOT/build/derived-data-oneclick"
xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Debug \
    -destination "id=$UDID" \
    -derivedDataPath "$DERIVED" \
    -allowProvisioningUpdates \
    build \
    | grep -E "^(===|error|warning|BUILD)" || true

APP=$(find "$DERIVED/Build/Products/Debug-iphoneos" -maxdepth 1 -name "*.app" -not -name "*.appex.app" | head -1)
if [[ -z "$APP" || ! -d "$APP" ]]; then
  echo "  ✘ 빌드 산출물을 찾지 못함. 시그닝 · Team 설정 확인."
  read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
  exit 1
fi
echo "    산출물: $APP"

# 4. 기기에 install.
echo ""
echo "==> 기기에 install"
xcrun devicectl device install app --device "$UDID" "$APP"

# 5. launch. Bundle ID 는 앱 target 의 PRODUCT_BUNDLE_IDENTIFIER.
BUNDLE_ID="com.hyunjine.linker"
echo ""
echo "==> $BUNDLE_ID launch"
xcrun devicectl device process launch --device "$UDID" "$BUNDLE_ID"

echo ""
echo "  ✓ 완료. iPhone 홈에서 Linker 앱이 자동 실행됐어요."
read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
