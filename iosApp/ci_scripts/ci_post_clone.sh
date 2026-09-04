#!/bin/sh

# Xcode Cloud 훅. 저장소가 fresh clone 된 직후 실행됨.
#
# 목적: `iosApp/Configuration/Config.xcconfig` 재생성.
#   - 이 파일은 KAKAO 네이티브 앱 키 등 시크릿을 포함해 .gitignore 되어 있음
#   - Xcode Cloud 는 매 빌드마다 fresh clone → 파일이 없어 iosApp 타겟의
#     PRODUCT_NAME / PRODUCT_BUNDLE_IDENTIFIER 등이 empty → 아카이브 시
#     `Multiple commands produce '.../Applications/.app'` 로 실패
#
# App Store Connect → Xcode Cloud → Environment Variables 에 등록 필요:
#   - KAKAO_NATIVE_APP_KEY  (Secret 로 등록)  카카오 네이티브 앱 키
#
# Xcode Cloud 가 자동 주입하는 변수:
#   - CI_PRIMARY_REPOSITORY_PATH : 저장소 루트 경로
#   - CI_BUILD_NUMBER            : 자동 증가하는 아카이브 빌드 번호

set -eu

: "${CI_PRIMARY_REPOSITORY_PATH:?CI_PRIMARY_REPOSITORY_PATH 미설정 — Xcode Cloud 외부 실행 아님?}"
: "${KAKAO_NATIVE_APP_KEY:?KAKAO_NATIVE_APP_KEY 미설정 — App Store Connect Xcode Cloud 환경변수에 secret 으로 등록 필요}"

# Xcode Cloud 러너에는 Java 가 기본으로 없어 `Compile Kotlin Framework` Run Script
# (JAVA_HOME 자동 탐색) 가 실패한다. Homebrew 는 Xcode Cloud 에 pre-installed
# 되어 있으므로 openjdk@21 만 설치하면 됨. 이후 Run Script 가
# /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home 를 자동 발견.
if ! [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
  echo "[ci_post_clone] openjdk@21 설치 중..."
  brew install openjdk@21
fi

XCCONFIG_DIR="$CI_PRIMARY_REPOSITORY_PATH/iosApp/Configuration"
XCCONFIG_PATH="$XCCONFIG_DIR/Config.xcconfig"
VERSION_FILE="$CI_PRIMARY_REPOSITORY_PATH/VERSION"

mkdir -p "$XCCONFIG_DIR"

# 앱 marketing version 은 루트 VERSION 파일 (단일 소스). 파일 첫 줄이 semver,
# 그 이하는 릴리즈 노트 본문 (GitHub Actions #184 가 사용). 여기선 첫 줄만 읽음.
# Android build.gradle.kts 도 동일 파일 첫 줄을 읽는다.
if [ ! -f "$VERSION_FILE" ]; then
  echo "error: $VERSION_FILE 없음"
  exit 1
fi
APP_VERSION=$(grep -v '^\s*$' "$VERSION_FILE" | head -n 1 | tr -d '[:space:]')
if [ -z "$APP_VERSION" ]; then
  echo "error: VERSION 파일 첫 줄이 비어있음"
  exit 1
fi

# 하드코딩 값은 저장소에 노출되어도 무해한 공개 식별자 (TEAM_ID, Bundle ID, 앱 이름).
# 시크릿은 오직 KAKAO_NATIVE_APP_KEY 뿐이며 환경변수에서 주입.
cat > "$XCCONFIG_PATH" <<EOF
TEAM_ID=YCCX589JXZ

PRODUCT_NAME=Linker
PRODUCT_BUNDLE_IDENTIFIER=com.hyunjine.linker

CURRENT_PROJECT_VERSION=${CI_BUILD_NUMBER:-1}
MARKETING_VERSION=$APP_VERSION

KAKAO_NATIVE_APP_KEY=$KAKAO_NATIVE_APP_KEY
EOF

echo "[ci_post_clone] Config.xcconfig 생성 완료 (version=$APP_VERSION build=${CI_BUILD_NUMBER:-1})"
