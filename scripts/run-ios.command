#!/bin/bash
# 다른 맥북에서 이 파일을 Finder 에서 더블클릭하면:
#   1) git pull (main 최신화)
#   2) shared 프레임워크 미리 빌드 (첫 Xcode Run 대기 시간 축소)
#   3) Xcode 프로젝트 열기 → 사용자가 ⌘R 로 실행
#
# 사전 준비 (한 번만):
#   - Xcode 설치 및 Apple ID 로그인 (Xcode → Settings → Accounts)
#   - iosApp/Configuration/Config.xcconfig 존재 (KAKAO_NATIVE_APP_KEY · TEAM_ID)
#   - local.properties 존재 (supabase.url · supabase.publishableKey · kakao.native.app.key)
#   - 실기기 실행이면 케이블 연결 + 신뢰 승인 + 개발자 모드 ON

set -euo pipefail
cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

echo "───────────────────────────────────────────"
echo " Linker — iOS 실행 준비"
echo " 위치: $REPO_ROOT"
echo "───────────────────────────────────────────"

# 0. 필수 파일 존재 확인. 없으면 .example 을 자동 복사해 두고 사용자에게 채우도록 안내.
copied=0
if [[ ! -f "iosApp/Configuration/Config.xcconfig" ]]; then
  cp iosApp/Configuration/Config.xcconfig.example iosApp/Configuration/Config.xcconfig
  echo "  ▸ iosApp/Configuration/Config.xcconfig 를 .example 로부터 새로 생성했어요."
  echo "    → TEAM_ID · KAKAO_NATIVE_APP_KEY 를 실제 값으로 채워주세요."
  copied=1
fi
if [[ ! -f "local.properties" ]]; then
  cp local.properties.example local.properties
  echo "  ▸ local.properties 를 .example 로부터 새로 생성했어요."
  echo "    → supabase / kakao / holiday 키를 실제 값으로 채워주세요."
  copied=1
fi
if (( copied == 1 )); then
  echo ""
  echo "  ↑ 위 파일들에 실제 값 채운 후 이 스크립트를 다시 실행하세요."
  read -n 1 -s -r -p "  아무 키나 눌러 창을 닫으세요..."
  exit 1
fi

# 1. git pull.
echo ""
echo "==> git pull"
git fetch --all --prune
current_branch=$(git rev-parse --abbrev-ref HEAD)
echo "    현재 브랜치: $current_branch"
if git merge-base --is-ancestor HEAD "origin/$current_branch" 2>/dev/null; then
  git pull --ff-only
else
  echo "  ⚠  로컬 브랜치가 원격보다 앞서있음 · 커밋 미푸시 상태일 수 있음. pull 스킵."
fi

# 2. shared 프레임워크 미리 빌드 (Xcode Run 첫 시도 대기시간 절감).
#    실기기 · 시뮬레이터 모두에서 재사용될 수 있도록 두 아키텍처 다 링크.
echo ""
echo "==> Kotlin shared 프레임워크 컴파일"
./gradlew :shared:linkDebugFrameworkIosArm64 -q
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -q
echo "    완료."

# 3. Xcode 프로젝트 열기. 사용자는 상단 device 선택 후 ⌘R.
echo ""
echo "==> Xcode 프로젝트 열기"
open iosApp/iosApp.xcodeproj

echo ""
echo "  ✓ 준비 완료. Xcode 창에서:"
echo "    · 좌상단 device 를 연결된 iPhone 으로 선택"
echo "    · ⌘R 로 실행"
echo ""
read -n 1 -s -r -p "  이 창은 아무 키나 누르면 닫힙니다..."
