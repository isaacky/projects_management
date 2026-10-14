#!/bin/sh
# Nightly logical backups of the platform and Keycloak databases (custom format, for pg_restore).
# Runs forever: sleeps until BACKUP_HOUR (local time), dumps, prunes dumps older than KEEP_DAYS.
set -u
export PGPASSWORD="$(tr -d '\r\n' < /run/secrets/PMP_DB_MIGRATION_PASSWORD)"
HOUR="${BACKUP_HOUR:-1}"
KEEP="${KEEP_DAYS:-14}"

backup() {
  day="$(date +%Y-%m-%d_%H%M)"
  dir="/backups/$day"
  mkdir -p "$dir"
  ok=1
  for db in pmp keycloak; do
    if pg_dump --format=custom --file="$dir/$db.dump" "$db"; then
      echo "$(date -Iseconds) backed up $db ($(du -h "$dir/$db.dump" | cut -f1))"
    else
      echo "$(date -Iseconds) BACKUP FAILED for $db" >&2
      ok=0
    fi
  done
  pg_dumpall --globals-only --no-role-passwords > "$dir/roles.sql" || ok=0
  [ "$ok" = 1 ] && touch "$dir/COMPLETE"
  find /backups -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP" -exec rm -rf {} +
}

echo "Database backups daily at ${HOUR}:00, kept ${KEEP} days"
while true; do
  now_h=$(date +%H | sed 's/^0//'); now_m=$(date +%M | sed 's/^0//')
  wait_min=$(( ((HOUR - ${now_h:-0} + 24) % 24) * 60 - ${now_m:-0} ))
  [ "$wait_min" -le 0 ] && wait_min=$((wait_min + 1440))
  sleep $((wait_min * 60))
  backup
done
