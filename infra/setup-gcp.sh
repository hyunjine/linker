#!/usr/bin/env bash
# infra/setup-gcp.sh
# 1회성 프로젝트 준비: API 활성화 + Artifact Registry + Cloud Run 서비스 계정 + IAM.
# 이 스크립트만 실행해도 파괴적 변경은 없다 (모두 create-if-not-exists).
#
# 사전조건:
#   - gcloud CLI 설치 & `gcloud auth login`
#   - infra/.env 채워짐
#   - GCP 프로젝트가 이미 존재하고 결제 계정 연결됨
set -euo pipefail

: "${PROJECT_ID:?PROJECT_ID env required}"
: "${REGION:?REGION env required}"
: "${AR_REPO:?AR_REPO env required}"
: "${RUN_SA_NAME:?RUN_SA_NAME env required}"
: "${RUN_SA_EMAIL:?RUN_SA_EMAIL env required}"

gcloud config set project "$PROJECT_ID"

echo "── Enabling required APIs ──"
gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    sqladmin.googleapis.com \
    secretmanager.googleapis.com \
    iam.googleapis.com \
    cloudbuild.googleapis.com

echo "── Artifact Registry: $AR_REPO ($REGION) ──"
if ! gcloud artifacts repositories describe "$AR_REPO" --location="$REGION" >/dev/null 2>&1; then
    gcloud artifacts repositories create "$AR_REPO" \
        --repository-format=docker \
        --location="$REGION" \
        --description="linker container images"
else
    echo "  → already exists, skip"
fi

echo "── Configuring docker credential helper ──"
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

echo "── Cloud Run service account: $RUN_SA_EMAIL ──"
if ! gcloud iam service-accounts describe "$RUN_SA_EMAIL" >/dev/null 2>&1; then
    gcloud iam service-accounts create "$RUN_SA_NAME" \
        --display-name="Linker Cloud Run runtime"
else
    echo "  → already exists, skip"
fi

echo "── Granting IAM roles to $RUN_SA_EMAIL ──"
# Cloud SQL client: Cloud Run 에서 Cloud SQL 커넥터로 붙기 위함
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$RUN_SA_EMAIL" \
    --role="roles/cloudsql.client" \
    --condition=None >/dev/null
# Secret Manager: 배포 시 시크릿 주입
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$RUN_SA_EMAIL" \
    --role="roles/secretmanager.secretAccessor" \
    --condition=None >/dev/null

echo "✓ setup-gcp.sh done"
