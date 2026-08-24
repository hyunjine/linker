# linker server

Ktor 서버 모듈.
- **DB**: Neon Serverless Postgres → `docs/neon-setup.md`
- **배포**: Fly.io (#29) → 후속 이슈에서 문서 추가
- **아키텍처**: `docs/server-architecture.md`

## 로컬 실행

### 1. Neon Postgres 준비 (한 번만)

`docs/neon-setup.md` §2~§3 을 따라 Neon 프로젝트 생성 후 connection string 확보.

### 2. `local.properties` 에 접속 정보

프로젝트 루트 `local.properties` (gitignore 됨) 에 아래 3줄 추가:

```properties
db.url=jdbc:postgresql://ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
db.user=neondb_owner
db.password=<pw>
```

### 3. 서버 실행

```bash
./gradlew :server:run
```

첫 실행 시 Flyway 가 `V1__init.sql` 을 Neon 에 자동 적용 → users/couples/schedules 등 테이블 생성.

### 4. 확인

```bash
curl http://localhost:8080/health
# → {"status":"ok","db":"ok"}
```

`db:"down"` 이면 `local.properties` 값 재확인 or Neon 콘솔에서 endpoint 활성 상태 확인.

## 환경변수 override

| env | 기본 (application.yaml) | 설명 |
|---|---|---|
| `PORT` | 8080 | 서버 포트. Fly.io 는 자동 주입 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/linker` | JDBC URL. 로컬은 gradle 이 `local.properties` 에서 자동 주입 |
| `DB_USER` / `DB_PASSWORD` | linker/linker | DB 인증 |
| `DB_MAX_POOL_SIZE` | 5 | HikariCP 최대 커넥션 |
| `JWT_SECRET` | dev-only-secret-change-me | JWT 서명 키 (배포 필수 override) |
| `KAKAO_REST_API_KEY` | (빈 값) | 카카오 서버 검증용 (네이티브 SDK 만 쓰면 필요 없음) |

## 트러블슈팅

- `password authentication failed` — Neon 콘솔 Roles → `neondb_owner` → Reset password 후 `local.properties` 갱신
- `SSLException` — `sslmode=require` 누락. URL 끝에 반드시 붙임
- 첫 요청 지연 (1~2초) — Neon auto-suspend wake-up. 이후 정상 속도
- Flyway `PostgreSQL 18.6 is newer` WARN — 무시 가능
