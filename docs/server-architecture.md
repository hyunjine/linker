# Linker 서버 아키텍처 (v0)

## 1. 모듈

```
linker/
├── androidApp/
├── iosApp/
├── shared/               # Compose Multiplatform UI + 앱 도메인 (KMP)
└── server/               # ⬅ 신규. Ktor JVM 서버
    ├── build.gradle.kts
    ├── Dockerfile
    └── src/
        ├── main/kotlin/com/hyunjine/linker/server/
        │   ├── Application.kt          # Ktor Netty 진입점
        │   ├── common/Plugins.kt       # CORS · CallLogging · StatusPages · ApiException
        │   ├── db/Database.kt          # HikariCP + Flyway + Exposed 초기화
        │   ├── auth/                   # /auth/kakao 등
        │   ├── users/                  # /users/me 등
        │   ├── couples/                # /couples/*
        │   └── schedules/              # /schedules/*, /couples/me/schedules
        └── main/resources/
            ├── application.yaml        # 포트 / DB / JWT / Kakao 설정
            ├── logback.xml
            └── db/migration/V1__init.sql
```

- 서버는 **JVM 전용 gradle 서브모듈** (`kotlin("jvm")`) 로 KMP 리포에 함께 관리.
- 향후 `shared-domain` KMP 모듈로 도메인/DTO 를 분리해 서버·앱이 같은 클래스를 공유할 수 있음 (지금은 서버 내부 DTO 만).

## 2. 스택 결정

| 관심사 | 선택 | 이유 |
|---|---|---|
| HTTP 서버 | **Ktor 3 + Netty** | KMP 리포와 동일한 코틀린/버전, coroutine 친화 |
| 직렬화 | **kotlinx.serialization + JSON** | 앱과 동일 스택, 도메인 클래스 공유 여지 |
| 인증 | **JWT (ktor-server-auth-jwt)** | stateless, Cloud Run 다중 인스턴스 친화 |
| DB | **PostgreSQL** | Cloud SQL 표준, 관계형 · 트랜잭션 필요 |
| ORM | **Exposed** (Table DSL + DAO) | 코틀린 네이티브, 얇은 추상화 |
| 커넥션 풀 | **HikariCP** | 사실상 표준 |
| 마이그레이션 | **Flyway 10** | 파일 기반, versioned, 롤백 정책 명확 |
| 로그 | **Logback + STDOUT** | Cloud Run 로그 수집이 STDOUT 을 그대로 잡음 |
| 배포 | **Docker → GCP Cloud Run** | auto-scale, min-instances 조절 가능 |
| 시크릿 | **Google Secret Manager** | Cloud Run env 로 mount |
| 스토리지 | **GCS (Firebase Storage)** | 프로필 사진 등 바이너리 |

## 3. 요청 처리 파이프라인

```
Netty → CallLogging → CORS → ContentNegotiation(JSON)
      → Authentication(JWT, 라우트별 opt-in)
      → RequestValidation → Routing → Handler
      → StatusPages (ApiException → ErrorBody)
```

- `common/Plugins.kt` 에서 모든 전역 플러그인 설치.
- 도메인 코드는 `ApiException(status, code, message)` 만 던지고, StatusPages 가 표준 `ErrorBody` 로 직렬화.

## 4. DB 접근 규약

- 모든 쿼리는 `Database.transaction { … }` (Exposed) 안에서 실행.
- 마이그레이션은 **파일 추가만** 허용. 기존 `V*.sql` 은 수정 금지.
- 서버 부팅 시 Flyway 마이그레이션 자동 실행 (`Database.init`).
- 헬스체크 `/health` 에서 `SELECT 1` 성공 여부를 함께 리턴 → Cloud Run readiness gate.

## 5. 설정 소스

`application.yaml` 이 단일 진입. 모든 값은 환경변수 override 지원.

| 키 | 로컬 기본 | 배포 소스 |
|---|---|---|
| `PORT` | 8080 | Cloud Run 자동 주입 |
| `JWT_SECRET` | `dev-only-secret-change-me` | Secret Manager |
| `KAKAO_REST_API_KEY` | (빈 값) | Secret Manager |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | 로컬 postgres | Cloud SQL + Secret Manager |
| `DB_MAX_POOL_SIZE` | 5 | Cloud Run 인스턴스 CPU 에 맞춰 조정 |

## 6. 로컬 실행

```bash
# 1) Postgres 를 로컬에 띄운다 (docker-compose 는 후속 추가 예정)
docker run -d --name linker-pg \
  -e POSTGRES_USER=linker -e POSTGRES_PASSWORD=linker -e POSTGRES_DB=linker \
  -p 5432:5432 postgres:16

# 2) 서버 실행
./gradlew :server:run

# 3) 헬스체크
curl http://localhost:8080/health
```

## 7. 다음 스텝

- **#17 카카오 로그인** — `/auth/kakao` 완성 (카카오 서버 검증 + 유저 shell upsert + JWT 발급)
- 후속 CRUD 이슈들 — 유저 프로필 완성, 커플 링크, 스케줄, 반복 확장, 기념일
- 인프라 이슈 — Cloud SQL 인스턴스, VPC Connector, Secret Manager entries, GitHub Actions 배포 파이프라인
