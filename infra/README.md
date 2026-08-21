# infra — GCP 인프라 세팅 스크립트

이 폴더의 스크립트는 **한 번에 프로덕션 인프라를 재현 가능하게 세팅** 하기 위한 gcloud wrapper.
`docs/deploy-cloudrun.md` 의 수동 절차를 그대로 자동화. Terraform 전환은 나중.

## 0. 사전 준비 (사람 손)

1. **GCP 프로젝트 생성** — Google Cloud Console → 새 프로젝트 (`linker-prod` 권장). 결제 계정 연결 필수.
2. **gcloud CLI 설치**
   ```bash
   brew install --cask google-cloud-sdk
   gcloud auth login
   gcloud auth application-default login
   ```
3. **Docker 설치** — `deploy.sh` 실행에 필요. OrbStack 권장 (`brew install --cask orbstack`).
4. **카카오 개발자 콘솔 앱 등록** — https://developers.kakao.com/console/app
   - 플랫폼 등록 (Android package: `com.hyunjine.linker`, iOS bundle: TBD)
   - REST API Key 확보 → `infra/.env` 의 `KAKAO_REST_API_KEY` 에 붙여넣음

## 1. `.env` 준비

```bash
cp infra/.env.example infra/.env
# 편집해서 PROJECT_ID, KAKAO_REST_API_KEY 등 채움
source infra/.env
```

`.env` 는 `.gitignore` 로 이미 제외됨 (아래 gitignore 항목 참고).

## 2. 실행 순서

### 2.1 프로젝트 준비 (1회, ~30초)

```bash
bash infra/setup-gcp.sh
```

- API 활성화 (run, artifactregistry, sqladmin, secretmanager, iam, cloudbuild)
- Artifact Registry `linker-images` 저장소
- Cloud Run 실행용 서비스 계정 `linker-run@…` + IAM (`cloudsql.client`, `secretmanager.secretAccessor`)

### 2.2 Cloud SQL (1회, ~5분, **비용 발생**)

```bash
bash infra/setup-cloudsql.sh
```

- Postgres 16 인스턴스 `linker-pg` (db-f1-micro 기준 월 $8~15)
- DB `linker`, 사용자 `linker` (비밀번호 자동 생성)
- 백업 자동 (7일 보관)
- 출력되는 `SQL_PASSWORD` · `connection name` 을 잘 보관 (한 번만 표시).

### 2.3 Secret Manager (매번 시크릿 변경 시)

```bash
# setup-cloudsql.sh 가 출력한 값을 아래 두 env 로 전달
export DB_PASSWORD="…setup-cloudsql.sh 출력한 값…"
# infra/.env 의 KAKAO_REST_API_KEY 는 이미 채워둔 상태여야 함
bash infra/setup-secrets.sh
```

- `JWT_SECRET`, `KAKAO_REST_API_KEY`, `DB_URL` (Cloud SQL socketFactory JDBC), `DB_USER`, `DB_PASSWORD` 5개를 생성/신규 버전 추가.
- `JWT_SECRET` 은 비워두면 스크립트가 `openssl rand -hex 32` 로 자동 생성.

### 2.4 서버 배포 (매 릴리스마다)

```bash
bash infra/deploy.sh
```

- `:server:buildFatJar` → Docker 빌드 → Artifact Registry push
- Cloud Run `linker-api` 신규 revision 배포 (100% 트래픽)
- `/health` 자동 확인, 실패 시 롤백 명령 안내

## 3. 삭제 / 초기화

```bash
# 서비스만 삭제 (Cloud SQL/시크릿 유지)
gcloud run services delete linker-api --region=$REGION

# 완전 삭제 (Cloud SQL 데이터도 날아감, 되돌릴 수 없음)
gcloud sql instances delete $SQL_INSTANCE
gcloud secrets delete JWT_SECRET
gcloud secrets delete KAKAO_REST_API_KEY
gcloud secrets delete DB_URL
gcloud secrets delete DB_USER
gcloud secrets delete DB_PASSWORD
gcloud artifacts repositories delete $AR_REPO --location=$REGION
```

## 4. 트러블슈팅

- **`Permission denied`** — `gcloud auth login` 계정이 프로젝트 Owner/Editor 인지 확인.
- **Cloud SQL 생성 실패 (`FAILED_PRECONDITION`)** — 프로젝트에 결제 계정이 연결됐는지 확인.
- **배포 후 `/health` 가 `db:"down"`** — Cloud Run 로그 (`gcloud run services logs read linker-api --region=$REGION --limit=50`) 에서 Flyway/JDBC 예외 확인. 흔한 원인: `DB_URL` 시크릿의 `cloudSqlInstance` 값 오타.
- **`the user does not have iam.serviceAccounts.actAs`** — Cloud Run 배포 시 `RUN_SA_EMAIL` 로 배포하려면 배포자 계정에 `roles/iam.serviceAccountUser` 필요.
  ```bash
  gcloud iam service-accounts add-iam-policy-binding $RUN_SA_EMAIL \
    --member="user:$(gcloud config get-value account)" \
    --role=roles/iam.serviceAccountUser
  ```

## 5. 후속

- **GitHub Actions 자동 배포** — #23. 위 `deploy.sh` 을 workflow 로 옮김.
- **Terraform 이관** — 규모 커지면 shell → Terraform + GCS backend.
- **Cloud SQL private IP** — VPC connector 붙여서 public IP 제거.
- **모니터링** — Cloud Monitoring uptime check, 에러 알림 Slack.
