#!/usr/bin/env bash
# infra/setup-secrets.sh
# Secret Manager 에 서버 실행에 필요한 시크릿 5종을 create-or-update-version 한다.
#
# 사전조건:
#   - infra/.env 채워짐 (JWT_SECRET 은 비워두면 자동 생성, KAKAO_REST_API_KEY 는 필수)
#   - setup-cloudsql.sh 실행되어 SQL_INSTANCE 존재
#   - DB_PASSWORD env 제공 (setup-cloudsql.sh 가 최초 생성 시 출력한 값)
set -euo pipefail

: "${PROJECT_ID:?}" "${REGION:?}" "${SQL_INSTANCE:?}" "${SQL_DB:?}" "${SQL_USER:?}"
: "${DB_PASSWORD:?DB_PASSWORD env required (setup-cloudsql.sh 출력에서 복사)}"
: "${KAKAO_REST_API_KEY:?KAKAO_REST_API_KEY env required (카카오 콘솔에서 발급)}"

gcloud config set project "$PROJECT_ID"

# JWT 서명 키가 없으면 자동 생성
if [[ -z "${JWT_SECRET:-}" ]]; then
    JWT_SECRET="$(openssl rand -hex 32)"
    echo "★ JWT_SECRET auto-generated (한 번만 표시됨): $JWT_SECRET"
fi

# Cloud SQL 프록시 방식 JDBC URL. Cloud Run 이 unix socket 으로 붙는다.
CONNECTION_NAME="$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(connectionName)')"
DB_URL="jdbc:postgresql:///$SQL_DB?cloudSqlInstance=$CONNECTION_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory&user=$SQL_USER&password=$DB_PASSWORD"

put_secret() {
    local name="$1" value="$2"
    if gcloud secrets describe "$name" >/dev/null 2>&1; then
        printf '%s' "$value" | gcloud secrets versions add "$name" --data-file=- >/dev/null
        echo "  ↻ $name updated (new version)"
    else
        printf '%s' "$value" | gcloud secrets create "$name" --data-file=- --replication-policy=automatic >/dev/null
        echo "  + $name created"
    fi
}

echo "── Writing secrets to Secret Manager ──"
put_secret JWT_SECRET          "$JWT_SECRET"
put_secret KAKAO_REST_API_KEY  "$KAKAO_REST_API_KEY"
put_secret DB_URL              "$DB_URL"
put_secret DB_USER             "$SQL_USER"
put_secret DB_PASSWORD         "$DB_PASSWORD"

echo "✓ setup-secrets.sh done"
