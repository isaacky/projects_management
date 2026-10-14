#!/bin/sh
# Generates every secret the stack needs and registers it with Docker Swarm. Run once, on a manager.
# Values are also written to ./secrets/ (owner-only) so you can copy them to the offline backup:
# Swarm never shows a secret again, and without PMP_SEALING_MASTER_KEY sealed bids cannot be opened.
set -eu
cd "$(dirname "$0")"
umask 077
mkdir -p secrets

rand() { openssl rand -base64 "$1" | tr -d '\n=+/' | cut -c1-"$2"; }

put() { # name value
  name="$1"; value="$2"
  if docker secret inspect "$name" >/dev/null 2>&1; then
    echo "exists:  $name (left unchanged)"
    return
  fi
  printf '%s' "$value" > "secrets/$name"
  printf '%s' "$value" | docker secret create "$name" - >/dev/null
  echo "created: $name"
}

value_of() { # an existing value from ./secrets, or a new one
  if [ -s "secrets/$1" ]; then cat "secrets/$1"; else printf '%s' "$2"; fi
}

APP_PW=$(value_of PMP_DB_APP_PASSWORD "$(rand 32 32)")
ANALYTICS_PW=$(value_of PMP_DB_ANALYTICS_PASSWORD "$(rand 32 32)")
S3_KEY=$(value_of PMP_S3_ACCESS_KEY "pmp$(rand 12 12)")
S3_SECRET=$(value_of PMP_S3_SECRET_KEY "$(rand 40 40)")

put PMP_DB_MIGRATION_PASSWORD "$(value_of PMP_DB_MIGRATION_PASSWORD "$(rand 32 32)")"
put PMP_DB_APP_PASSWORD "$APP_PW"
put PMP_DB_ANALYTICS_PASSWORD "$ANALYTICS_PW"
put PMP_DATABASE_URL "postgresql+psycopg://pmp_analytics:${ANALYTICS_PW}@postgres:5432/pmp"
put PMP_SEALING_MASTER_KEY "$(value_of PMP_SEALING_MASTER_KEY "$(openssl rand -base64 32)")"
put PMP_S3_ACCESS_KEY "$S3_KEY"
put PMP_S3_SECRET_KEY "$S3_SECRET"
put PMP_S3_CONFIG "{\"identities\":[{\"name\":\"pmp-app\",\"credentials\":[{\"accessKey\":\"${S3_KEY}\",\"secretKey\":\"${S3_SECRET}\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\",\"Tagging\"]}]}"
put KC_DB_PASSWORD "$(value_of KC_DB_PASSWORD "$(rand 32 32)")"
put KC_ADMIN_PASSWORD "$(value_of KC_ADMIN_PASSWORD "$(rand 24 24)")"

echo
echo "Copies are in $(pwd)/secrets. Store them offline (at least PMP_SEALING_MASTER_KEY and"
echo "PMP_DB_MIGRATION_PASSWORD), then remove the folder from the server: shred -u secrets/*"
