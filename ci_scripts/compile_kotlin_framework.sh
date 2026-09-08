#!/bin/sh

# `Compile Kotlin Framework` Xcode Run Script phase 의 몸체.
# pbxproj 안에 스크립트 로직을 JSON 이스케이프로 박아 놓으면 유지보수가 어려워
# 별도 shell 파일로 분리. pbxproj 는 이 파일을 호출만 한다.
#
# 흐름:
#   1. `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES` 면 IDE 인덱싱용 skip (기본 IntelliJ/Xcode 통합).
#   2. JAVA_HOME 결정 순서: 기존 env → 로컬 흔한 경로 (homebrew/JVM) → 마지막 fallback 으로
#      Adoptium Temurin JDK 21 자동 다운로드. ci_post_clone.sh 가 안 돌아도 이 스텝이
#      혼자 자립하도록 하기 위함 (Xcode Cloud 에서 ci_post_clone.sh 미실행 사례 대응).
#   3. `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 로 KMP 프레임워크 빌드/서명.

if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES"
  exit 0
fi

# ── JAVA_HOME 확보 ──────────────────────────────────────────
resolve_java_home() {
  # 이미 유효한 JAVA_HOME 이 넘어와 있으면 그대로.
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "[Compile Kotlin Framework] use inherited JAVA_HOME=$JAVA_HOME"
    return 0
  fi

  # 로컬 개발 환경 후보. 순서대로 확인.
  for candidate in \
    "/Volumes/workspace/DerivedData/JDK/Home" \
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home" \
    ; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      echo "[Compile Kotlin Framework] found local JAVA_HOME=$JAVA_HOME"
      return 0
    fi
  done
  return 1
}

install_temurin_21() {
  # Adoptium Temurin GA 21 을 CI 러너 (Xcode Cloud/GH Actions) 나 로컬 첫 setup 시
  # 자동으로 받아 설치. 캐시 위치는 우선순위대로 시도 (persistent 우선).
  TARGET_DIR="${CI_DERIVED_DATA_PATH:-$HOME/.cache/linker}/JDK"
  JDK_HOME="$TARGET_DIR/Home"
  MARKER="$TARGET_DIR/.installed.21"

  if [ -f "$MARKER" ] && [ -x "$JDK_HOME/bin/java" ]; then
    export JAVA_HOME="$JDK_HOME"
    echo "[Compile Kotlin Framework] cached JAVA_HOME=$JAVA_HOME"
    return 0
  fi

  if [ "$(uname -m)" = "arm64" ]; then ARCH="aarch64"; else ARCH="x64"; fi
  URL="https://api.adoptium.net/v3/binary/latest/21/ga/mac/${ARCH}/jdk/hotspot/normal/eclipse"
  TMP_DIR=$(mktemp -d)
  echo "[Compile Kotlin Framework] Downloading Adoptium Temurin JDK 21 ($ARCH) → $TARGET_DIR"

  if ! curl -fsSL --retry 3 --retry-delay 2 "$URL" -o "$TMP_DIR/jdk.tar.gz"; then
    echo "error: Adoptium 다운로드 실패 ($URL)"
    rm -rf "$TMP_DIR"
    return 1
  fi
  if ! tar xzf "$TMP_DIR/jdk.tar.gz" -C "$TMP_DIR"; then
    echo "error: JDK tarball 압축 해제 실패"
    rm -rf "$TMP_DIR"
    return 1
  fi

  EXTRACTED_HOME=$(find "$TMP_DIR" -maxdepth 4 -type d -name Home -path '*/Contents/Home' | head -n 1)
  if [ -z "$EXTRACTED_HOME" ]; then
    echo "error: JDK tarball 안에서 Contents/Home 을 찾지 못함"
    rm -rf "$TMP_DIR"
    return 1
  fi

  rm -rf "$TARGET_DIR" && mkdir -p "$TARGET_DIR"
  mv "$EXTRACTED_HOME" "$JDK_HOME"
  touch "$MARKER"
  rm -rf "$TMP_DIR"

  export JAVA_HOME="$JDK_HOME"
  echo "[Compile Kotlin Framework] installed JAVA_HOME=$JAVA_HOME"
}

if ! resolve_java_home; then
  echo "[Compile Kotlin Framework] JDK not found locally → Adoptium fallback"
  install_temurin_21 || {
    echo "::error::JDK 확보 실패. 로컬은 brew install openjdk@21, CI 는 네트워크 확인 필요."
    exit 1
  }
fi

export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

# ── Gradle build ────────────────────────────────────────────
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode --stacktrace
