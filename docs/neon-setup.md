# Neon Postgres 세팅 (v0)

관리형 Serverless Postgres. #30 에서 도입. GCP Cloud SQL (`#24`) 폐기의 대체.

## 1. 왜 Neon

- **무료 티어**: 0.5GB storage · 100 compute hour/월 · auto-suspend 5분
- **auto-suspend**: 유휴 시 컴퓨트 정지 (요금 0) → 첫 요청 시 ~1초 wake-up
- **Postgres 표준**: JDBC + Flyway 그대로 동작 (스키마 이식성 100%)
- **Branching**: 프로덕션 DB 를 clone 해서 개발용 branch 생성 (Neon 특유)
- **한국 근접 리전**: Singapore (`ap-southeast-1`) — Tokyo/Seoul 은 아직 없음

## 2. 프로젝트 생성 (콘솔)

1. https://console.neon.tech → GitHub 로그인
2. **New Project**
   - Project name: `linker`
   - Postgres version: `18` (최신)
   - Region: `AWS Asia Pacific 1 (Singapore)`
   - Neon Auth: **OFF** (우리는 카카오 로그인 + 자체 JWT 사용)
3. **Create Project** → connection string 자동 발급

## 3. Connection String 확보

프로젝트 생성 완료 화면의 **Connect your app manually** 섹션에서:
- **Connection string** 탭
- **Show password** 클릭 → 실제 password 노출
- **Copy snippet** 클릭

형태:
```
postgresql://neondb_owner:<pw>@ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

- **user**: `neondb_owner` (자동 생성)
- **database**: `neondb` (자동 생성)
- **sslmode=require** 필수 — Neon 은 TLS 강제

## 4. 로컬 개발 (Ktor `:server:run`)

### 4.1 local.properties 에 등록

프로젝트 루트 `local.properties` (`.gitignore` 대상) 에 아래 3줄 추가:

```properties
db.url=jdbc:postgresql://ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
db.user=neondb_owner
db.password=<pw>
```

- `postgresql://` 를 `jdbc:postgresql://` 로 바꿈 (JDBC 스킴)
- `user:password@` 부분은 제거 (별도 `db.user` / `db.password` 로 분리)

### 4.2 실행

```bash
./gradlew :server:run
```

`server/build.gradle.kts` 의 `tasks.named("run")` 이 `local.properties` 를 읽어 `DB_URL`/`DB_USER`/`DB_PASSWORD` 환경변수로 자동 주입 → `application.yaml` 이 이 env 를 읽어 HikariCP 에 전달.

### 4.3 검증

```bash
curl http://localhost:8080/health
# → {"status":"ok","db":"ok"}
```

첫 실행 시 Flyway 가 `server/src/main/resources/db/migration/V1__init.sql` 을 자동 적용 → Neon 대시보드 **Tables** 에서 `users`/`couples`/`schedules` 등 확인 가능.

## 5. 배포 (Fly.io — #29)

Fly.io 서버는 아래 명령으로 시크릿 주입 (배포 시 별도 이슈):

```bash
fly secrets set DB_URL="jdbc:postgresql://ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require"
fly secrets set DB_USER="neondb_owner"
fly secrets set DB_PASSWORD="<pw>"
```

## 6. 마이그레이션 정책

- **파일 추가만** 허용. `V1__init.sql` 등 기존 파일 절대 수정 X.
- 스키마 변경은 `V2__<설명>.sql`, `V3__…` 로 순차 추가.
- 서버 부팅 시 Flyway 가 자동으로 새 버전 적용. 롤백 시나리오는 별도 정책 필요.

## 7. 트러블슈팅

| 증상 | 원인 · 해결 |
|---|---|
| `SSLException`, `SSL required` | `sslmode=require` 누락. URL 끝에 붙임 |
| `password authentication failed` | password 재발급 (Neon 콘솔 → Roles → `neondb_owner` → Reset password) 후 `local.properties` 갱신 |
| `Endpoint is disabled` | 유휴 auto-suspend 상태. 다음 요청 시 자동 wake — 첫 응답 지연 ~1초 |
| Flyway `PostgreSQL 18.6 is newer` WARN | 무시 가능. Flyway 10.20 이 Postgres 18 을 아직 공식 지원 표기 안 함. 실제 동작엔 문제 없음 |

## 8. 다음 스텝

- **#29** Fly.io 배포에서 위 connection string 을 `fly secrets` 로 주입 → 실 서버 완성
- Neon branching 활용 (프로덕션 clone 으로 CI/스테이징 DB 생성) — 후속 이슈
