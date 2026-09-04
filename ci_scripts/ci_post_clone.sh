#!/bin/sh

# Xcode Cloud 훅. 저장소가 fresh clone 된 직후 실행됨.
#
# 두 가지 담당:
#   (1) 루트 VERSION 파일 첫 줄 → iosApp/Configuration/Config.xcconfig 재생성
#       (`Config.xcconfig` 는 KAKAO 시크릿 포함해 .gitignore, fresh clone 마다 필요)
#   (2) KMP 빌드를 위해 JDK 21 을 다운로드해 $CI_DERIVED_DATA_PATH/JDK/Home 에 배치
#       (`Compile Kotlin Framework` Run Script 가 그 경로에서 JAVA_HOME 을 찾음)
#
# App Store Connect → Xcode Cloud → Environment Variables 에 등록 필요:
#   - KAKAO_NATIVE_APP_KEY  (Secret) 카카오 네이티브 앱 키
#   - JAVA_HOME             값: /Volumes/workspace/DerivedData/JDK/Home
#       (Xcode Cloud 는 $CI_DERIVED_DATA_PATH=/Volumes/workspace/DerivedData 고정)
#       이 변수가 있어야 Run Script phase 가 JAVA_HOME 을 상속받음. 스크립트에서
#       export 한 값은 Xcode 의 별도 shell 로 넘어가지 않기 때문.
#
# Xcode Cloud 가 자동 주입하는 변수:
#   - CI_PRIMARY_REPOSITORY_PATH : 저장소 루트 경로
#   - CI_DERIVED_DATA_PATH       : DerivedData (JDK · 캐시 위치)
#   - CI_BUILD_NUMBER            : 자동 증가하는 아카이브 빌드 번호

set -eu

: "${CI_PRIMARY_REPOSITORY_PATH:?CI_PRIMARY_REPOSITORY_PATH 미설정 — Xcode Cloud 외부 실행 아님?}"
: "${CI_DERIVED_DATA_PATH:?CI_DERIVED_DATA_PATH 미설정 — Xcode Cloud 외부 실행 아님?}"
: "${KAKAO_NATIVE_APP_KEY:?KAKAO_NATIVE_APP_KEY 미설정 — App Store Connect Xcode Cloud 환경변수에 secret 으로 등록 필요}"

# ─────────── JDK 21 (Adoptium Temurin) 다운로드 ───────────
# Homebrew 방식은 러너마다 안정성이 달라 검증된 방식으로 전환.
# Adoptium API 는 latest GA 릴리즈를 안정 URL 로 제공.
JDK_HOME="${CI_DERIVED_DATA_PATH}/JDK/Home"
JDK_MARKER="${CI_DERIVED_DATA_PATH}/JDK/.installed.21"

if [ ! -f "$JDK_MARKER" ] || [ ! -x "$JDK_HOME/bin/java" ]; then
  if [ "$(uname -m)" = "arm64" ]; then
    ARCH="aarch64"
  else
    ARCH="x64"
  fi
  URL="https://api.adoptium.net/v3/binary/latest/21/ga/mac/${ARCH}/jdk/hotspot/normal/eclipse"

  echo "[ci_post_clone] JDK 21 ($ARCH) 다운로드: $URL"
  TMP_DIR="${CI_DERIVED_DATA_PATH}/_jdk_dl"
  rm -rf "$TMP_DIR" && mkdir -p "$TMP_DIR"

  curl -fsSL "$URL" -o "$TMP_DIR/jdk.tar.gz"
  tar xzf "$TMP_DIR/jdk.tar.gz" -C "$TMP_DIR"

  # 압축 안엔 `jdk-21.X.X.jdk/Contents/Home` 형태. Home 폴더만 뽑아 옮김.
  EXTRACTED_HOME=$(find "$TMP_DIR" -maxdepth 4 -type d -name Home -path '*/Contents/Home' | head -n 1)
  if [ -z "$EXTRACTED_HOME" ]; then
    echo "::error::JDK tarball 안에서 Contents/Home 을 찾지 못함"
    ls -la "$TMP_DIR"
    exit 1
  fi

  rm -rf "${CI_DERIVED_DATA_PATH}/JDK"
  mkdir -p "${CI_DERIVED_DATA_PATH}/JDK"
  mv "$EXTRACTED_HOME" "$JDK_HOME"
  rm -rf "$TMP_DIR"
  touch "$JDK_MARKER"
fi

if [ ! -x "$JDK_HOME/bin/java" ]; then
  echo "::error::JDK 설치 후에도 $JDK_HOME/bin/java 없음"
  ls -la "${CI_DERIVED_DATA_PATH}/JDK" 2>&1 | head -20
  exit 1
fi

echo "[ci_post_clone] JAVA_HOME 준비 완료: $JDK_HOME"
"$JDK_HOME/bin/java" -version 2>&1 | head -3

# ─────────── Config.xcconfig 재생성 ───────────
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
