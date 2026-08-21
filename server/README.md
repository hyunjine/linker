# linker server

Ktor 서버 모듈. 배포는 `docs/deploy-cloudrun.md`, 아키텍처는 `docs/server-architecture.md`.

## 로컬 실행

### 1. 컨테이너 런타임 (한 번만)

Postgres 를 컨테이너로 띄우기 위해 아래 중 하나가 필요.

- **Docker Desktop** — `brew install --cask docker` 후 앱 실행. 회사에선 라이선스 확인 필요.
- **OrbStack** — `brew install --cask orbstack`. Docker Desktop 대비 훨씬 가볍고 빠름 (권장).
- **Colima** — `brew install colima docker docker-compose && colima start`. 완전 무료·CLI 전용.

설치 후 `docker --version` 이 동작해야 함.

### 2. Postgres 띄우기

```bash
docker compose -f server/docker-compose.yaml up -d
docker compose -f server/docker-compose.yaml ps
```

- Postgres 는 `localhost:5432` (user/password/db = `linker`/`linker`/`linker`)
- Adminer(웹 DB 뷰어): http://localhost:8081 — System=PostgreSQL, Server=`postgres`, User=`linker`, Password=`linker`, DB=`linker`
- 데이터 볼륨: `linker-pg-data` (compose down -v 로 초기화)

### 3. 서버 실행

```bash
./gradlew :server:run
```

- 부팅 시 자동으로 Flyway 가 `V1__init.sql` 을 적용해서 스키마를 만든다.
- 기본 포트 `8080`.

### 4. 확인

```bash
curl http://localhost:8080/health
# → {"status":"ok","db":"ok"}
```

`db:"down"` 이면 postgres 컨테이너가 죽었거나 접속 정보가 안 맞는 것. `docker compose logs postgres` 로 확인.

### 5. 종료 · 초기화

```bash
# 서버 종료: Ctrl+C
# postgres 종료 (데이터 유지)
docker compose -f server/docker-compose.yaml down
# postgres 종료 + 데이터 삭제 (스키마 완전 리셋)
docker compose -f server/docker-compose.yaml down -v
```

## 환경변수 override

`server/src/main/resources/application.yaml` 의 값은 아래 환경변수로 덮어쓸 수 있음.

| env | 기본 | 설명 |
|---|---|---|
| `PORT` | 8080 | 서버 포트. Cloud Run 은 자동 주입 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/linker` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | linker/linker | DB 인증 |
| `DB_MAX_POOL_SIZE` | 5 | HikariCP 최대 커넥션 |
| `JWT_SECRET` | dev-only-secret-change-me | JWT 서명 키 (배포 필수 override) |
| `KAKAO_REST_API_KEY` | (빈 값) | 카카오 서버 검증용 |

## 트러블슈팅

- `Port 5432 already in use` — 기존 postgres 인스턴스 실행 중. `docker compose down` 또는 다른 포트로 변경.
- `Flyway migration failed` — 이전 스키마 잔재. `docker compose down -v` 로 볼륨 삭제 후 재시작.
- 서버가 뜨긴 하는데 `/health` 가 `db:"down"` — postgres 컨테이너 healthy 상태인지 `docker compose ps` 로 확인.
