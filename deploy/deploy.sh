#!/bin/sh
# Deploys or upgrades the stack: ./deploy.sh   (reads ./pmp.env)
set -eu
cd "$(dirname "$0")"
[ -f pmp.env ] || { echo "Copy pmp.env.example to pmp.env and fill it in first." >&2; exit 1; }
set -a
. ./pmp.env
set +a

missing=0
for v in PMP_HOST PMP_REGISTRY PMP_VERSION PMP_TENANT_ID PMP_TENANT_NAME PMP_ADMIN_EMAIL PMP_ADMIN_ALLOW_CIDR; do
  eval "val=\${$v:-}"
  [ -n "$val" ] || { echo "pmp.env: $v is empty" >&2; missing=1; }
done
echo "$PMP_TENANT_ID" | grep -Eq '^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$' \
  || { echo "pmp.env: PMP_TENANT_ID must be a UUID (uuidgen)" >&2; missing=1; }
for s in PMP_DB_MIGRATION_PASSWORD PMP_DB_APP_PASSWORD PMP_DB_ANALYTICS_PASSWORD PMP_DATABASE_URL \
         PMP_SEALING_MASTER_KEY PMP_S3_ACCESS_KEY PMP_S3_SECRET_KEY PMP_S3_CONFIG KC_DB_PASSWORD KC_ADMIN_PASSWORD; do
  docker secret inspect "$s" >/dev/null 2>&1 || { echo "secret $s missing: run ./create-secrets.sh" >&2; missing=1; }
done
[ "$missing" = 0 ] || exit 1
mkdir -p "${PMP_TLS_DIR:-/srv/pmp-tls}"

docker stack deploy --with-registry-auth --prune -c stack.yml pmp
echo "Deployed pmp ${PMP_VERSION}. Watch it come up: docker stack ps pmp"
