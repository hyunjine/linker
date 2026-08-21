#!/usr/bin/env bash
# infra/deploy.sh
# 서버 배포 (수동). CI/CD 파이프라인이 완성되기 전 임시 사용.
# 순서:
#   1) :server:buildFatJar 로 fat jar 생성
#   2) Docker 이미지 빌드 & Artifact Registry 로 push
#   3) Cloud Run 서비스 배포 (신규 revision, 100% 트래픽)
#   4) /health 확인
set -euo pipefail

: "${PROJECT_ID:?}" "${REGION:?}" "${AR_REPO:?}" "${SERVICE_NAME:?}"
: "${SQL_INSTANCE:?}" "${RUN_SA_EMAIL:?}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GIT_SHA="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$AR_REPO/$SERVICE_NAME:$GIT_SHA"
CONNECTION_NAME="$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(connectionName)')"

echo "── (1/4) Build fat jar ──"
(cd "$REPO_ROOT" && ./gradlew :server:buildFatJar)

echo "── (2/4) Docker build & push: $IMAGE ──"
docker build -t "$IMAGE" -f "$REPO_ROOT/server/Dockerfile" "$REPO_ROOT/server"
docker push "$IMAGE"

echo "── (3/4) Cloud Run deploy: $SERVICE_NAME ($REGION) ──"
gcloud run deploy "$SERVICE_NAME" \
    --image="$IMAGE" \
    --region="$REGION" \
    --platform=managed \
    --allow-unauthenticated \
    --service-account="$RUN_SA_EMAIL" \
    --port=8080 \
    --cpu=1 --memory=512Mi \
    --min-instances=0 --max-instances=5 \
    --add-cloudsql-instances="$CONNECTION_NAME" \
    --set-secrets="JWT_SECRET=JWT_SECRET:latest,KAKAO_REST_API_KEY=KAKAO_REST_API_KEY:latest,DB_URL=DB_URL:latest,DB_USER=DB_USER:latest,DB_PASSWORD=DB_PASSWORD:latest"

URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
echo "── (4/4) Health check ──"
sleep 3
if curl -sSf "$URL/health" | tee /dev/stderr | grep -q '"status":"ok"'; then
    echo ""
    echo "✓ Deployed. Service URL: $URL"
else
    echo ""
    echo "✗ Health check failed. 최근 revision 확인 후 롤백:"
    echo "  gcloud run revisions list --service=$SERVICE_NAME --region=$REGION"
    echo "  gcloud run services update-traffic $SERVICE_NAME --region=$REGION --to-revisions=<prev>=100"
    exit 1
fi
