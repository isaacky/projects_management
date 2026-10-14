#!/bin/sh
# Nightly copy of the documents bucket (the current version of every file) to /backups.
# Files deleted from the bucket stay in the copy; the bucket itself keeps older versions.
set -u
HOUR="${BACKUP_HOUR:-1}"
S3="aws --endpoint-url ${S3_ENDPOINT}"

echo "Document backups daily at ${HOUR}:30"
while true; do
  now_h=$(date +%H | sed 's/^0//'); now_m=$(date +%M | sed 's/^0//')
  wait_min=$(( ((HOUR - ${now_h:-0} + 24) % 24) * 60 + 30 - ${now_m:-0} ))
  [ "$wait_min" -le 0 ] && wait_min=$((wait_min + 1440))
  sleep $((wait_min * 60))
  if $S3 s3 sync "s3://${S3_BUCKET}" /backups/current --only-show-errors; then
    date -Iseconds > /backups/LAST_SUCCESS
    echo "$(date -Iseconds) documents backed up ($(du -sh /backups/current | cut -f1))"
  else
    echo "$(date -Iseconds) DOCUMENT BACKUP FAILED" >&2
  fi
done
