# linker 서버 컨테이너 (Cloud Run 배포용)
# 빌드는 CI/CD 에서 `./gradlew :server:buildFatJar` 를 먼저 실행한 후 이 이미지에 복사한다.
# 필요 시 multi-stage 빌드로 확장 가능 (아래 주석 참고).

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# ktor 플러그인이 :server:buildFatJar 로 생성하는 이름. build.gradle.kts 참고.
COPY build/libs/linker-server.jar app.jar

# Cloud Run 이 주입하는 $PORT 를 EngineMain 이 자동으로 읽는다. 기본 8080.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

# ── multi-stage 대안 (원하면 위 두 블록을 대체) ────────────────────────
# FROM eclipse-temurin:21-jdk AS build
# WORKDIR /workspace
# COPY . .
# RUN ./gradlew :server:buildFatJar --no-daemon
#
# FROM eclipse-temurin:21-jre AS runtime
# WORKDIR /app
# COPY --from=build /workspace/server/build/libs/linker-server.jar app.jar
# ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
