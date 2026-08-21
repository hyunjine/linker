#!/usr/bin/env bash
# infra/setup-cloudsql.sh
# Cloud SQL Postgres 인스턴스 + DB + 사용자 생성.
# 인스턴스 생성은 ~5분 소요, 비용 발생 (db-f1-micro 기준 월 $8~15).
#
# 실행 후 DB 비밀번호는 표준출력으로 뜬다 → 곧바로 setup-secrets.sh 로 SM 에 넣을 것.
set -euo pipefail

: "${PROJECT_ID:?}" "${REGION:?}" "${SQL_INSTANCE:?}" "${SQL_DB:?}" "${SQL_USER:?}"
: "${SQL_TIER:=db-f1-micro}" "${SQL_DB_VERSION:=POSTGRES_16}"

gcloud config set project "$PROJECT_ID"

echo "── Cloud SQL instance: $SQL_INSTANCE ($REGION, $SQL_TIER, $SQL_DB_VERSION) ──"
if ! gcloud sql instances describe "$SQL_INSTANCE" >/dev/null 2>&1; then
    echo "  ⏳ creating (약 5분 소요) ..."
    gcloud sql instances create "$SQL_INSTANCE" \
        --database-version="$SQL_DB_VERSION" \
        --region="$REGION" \
        --tier="$SQL_TIER" \
        --edition=ENTERPRISE \
        --backup \
        --backup-start-time=17:00 \
        --retained-backups-count=7 \
        --database-flags=cloudsql.iam_authentication=off
else
    echo "  → already exists, skip"
fi

echo "── Database: $SQL_DB ──"
if ! gcloud sql databases describe "$SQL_DB" --instance="$SQL_INSTANCE" >/dev/null 2>&1; then
    gcloud sql databases create "$SQL_DB" --instance="$SQL_INSTANCE"
else
    echo "  → already exists, skip"
fi

echo "── DB user: $SQL_USER ──"
SQL_PASSWORD="$(openssl rand -base64 24 | tr -d '=+/')"
if ! gcloud sql users list --instance="$SQL_INSTANCE" --format='value(name)' | grep -qx "$SQL_USER"; then
    gcloud sql users create "$SQL_USER" --instance="$SQL_INSTANCE" --password="$SQL_PASSWORD"
    echo ""
    echo "★ 생성된 DB 비밀번호 (한 번만 표시됨):"
    echo "  SQL_PASSWORD=$SQL_PASSWORD"
    echo ""
    echo "  → 다음 명령으로 Secret Manager 에 넣기:"
    echo "    export DB_PASSWORD='$SQL_PASSWORD' && bash infra/setup-secrets.sh"
else
    echo "  → user already exists. 비밀번호를 재발급하려면:"
    echo "    gcloud sql users set-password $SQL_USER --instance=$SQL_INSTANCE --password=<new>"
fi

CONNECTION_NAME="$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(connectionName)')"
echo ""
echo "★ Cloud SQL connection name (Cloud Run 배포 시 --add-cloudsql-instances 로 지정):"
echo "  $CONNECTION_NAME"

echo "✓ setup-cloudsql.sh done"
