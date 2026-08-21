# Cloud Run 배포 가이드 (v0)

Google Cloud Run 에 Ktor 서버(`:server` 모듈) 를 배포하는 최소 절차. 자동화(GitHub Actions/Cloud Build) 는 후속.

## 1. 사전 준비 (1회)

```bash
# 프로젝트 · 리전 지정
export PROJECT_ID=your-gcp-project
export REGION=asia-northeast3   # 서울
export SERVICE=linker-api
export REPO=linker-images        # Artifact Registry repo 이름

gcloud config set project $PROJECT_ID

# API 활성화
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com

# Artifact Registry 저장소 (컨테이너 이미지 저장소)
gcloud artifacts repositories create $REPO \
  --repository-format=docker \
  --location=$REGION

# 도커 인증 헬퍼
gcloud auth configure-docker $REGION-docker.pkg.dev
```

## 2. 시크릿 등록 (Secret Manager)

```bash
echo -n "$(openssl rand -hex 32)" | gcloud secrets create JWT_SECRET --data-file=-
echo -n "실제 카카오 REST API Key" | gcloud secrets create KAKAO_REST_API_KEY --data-file=-
echo -n "postgres://user:pass@…"  | gcloud secrets create DB_URL          --data-file=-
echo -n "linker"                    | gcloud secrets create DB_USER         --data-file=-
echo -n "…"                         | gcloud secrets create DB_PASSWORD     --data-file=-
```

## 3. Cloud SQL Postgres

```bash
gcloud sql instances create linker-pg \
  --database-version=POSTGRES_16 \
  --region=$REGION \
  --tier=db-f1-micro \
  --edition=ENTERPRISE

gcloud sql databases create linker  --instance=linker-pg
gcloud sql users create linker      --instance=linker-pg --password=…
```

- Cloud Run 에서 붙일 때는 **Cloud SQL Proxy connector** 또는 Private IP + Serverless VPC Connector 를 사용. 최초에는 public IP + password 로 시작해도 무방 (개발 초기).

## 4. 이미지 빌드 & 푸시

```bash
# 1) fat jar 생성
./gradlew :server:buildFatJar

# 2) 도커 이미지 빌드
IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/$SERVICE:$(git rev-parse --short HEAD)
docker build -t $IMAGE -f server/Dockerfile server

# 3) 푸시
docker push $IMAGE
```

## 5. Cloud Run 배포

```bash
gcloud run deploy $SERVICE \
  --image=$IMAGE \
  --region=$REGION \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --min-instances=0 \
  --max-instances=5 \
  --cpu=1 --memory=512Mi \
  --set-secrets=JWT_SECRET=JWT_SECRET:latest,KAKAO_REST_API_KEY=KAKAO_REST_API_KEY:latest,DB_URL=DB_URL:latest,DB_USER=DB_USER:latest,DB_PASSWORD=DB_PASSWORD:latest
```

- `--min-instances=1` 로 두면 cold start 사라지지만 요금 발생. 초기에는 0.
- Cloud SQL 연결을 add-cloudsql-instances 옵션으로 붙이려면 커넥션 이름 필요:
  ```bash
  --add-cloudsql-instances=$PROJECT_ID:$REGION:linker-pg
  ```

## 6. 헬스체크 확인

```bash
URL=$(gcloud run services describe $SERVICE --region=$REGION --format='value(status.url)')
curl "$URL/health"
# → {"status":"ok","db":"ok"}
```

## 7. 롤백

```bash
gcloud run services update-traffic $SERVICE --region=$REGION --to-revisions=<previous-revision>=100
```

## 8. (선택) Firebase Hosting 프록시

`api.linker.app/**` → Cloud Run 로 rewrite. `firebase.json`:

```json
{
  "hosting": {
    "public": "public",
    "rewrites": [
      {
        "source": "/api/**",
        "run": { "serviceId": "linker-api", "region": "asia-northeast3" }
      }
    ]
  }
}
```

## 9. 다음 스텝

- GitHub Actions workflow (`.github/workflows/deploy-server.yml`) 로 자동화 — main 태그 push 시 위 4~5 단계 자동 실행.
- Cloud SQL private IP + VPC Connector 로 전환.
- `docker-compose.yaml` 로 로컬 postgres + 서버 함께 띄우기.
