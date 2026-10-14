#!/bin/sh
# Creates the documents bucket on the S3 endpoint. Safe to run repeatedly.
set -u
S3="aws --endpoint-url ${S3_ENDPOINT}"

echo "Waiting for S3 at ${S3_ENDPOINT} ..."
i=0
until $S3 s3api list-buckets >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "S3 endpoint not reachable after 60 tries" >&2
    exit 1
  fi
  sleep 2
done

if $S3 s3api head-bucket --bucket "${S3_BUCKET}" >/dev/null 2>&1; then
  echo "Bucket ${S3_BUCKET} already exists"
else
  # Object lock (WORM) protects tender and contract records. Fall back to a plain
  # bucket if this storage version does not support it, and say so loudly.
  if $S3 s3api create-bucket --bucket "${S3_BUCKET}" --object-lock-enabled-for-bucket >/dev/null 2>&1; then
    echo "Created ${S3_BUCKET} with object lock"
  else
    $S3 s3api create-bucket --bucket "${S3_BUCKET}" >/dev/null
    echo "WARNING: created ${S3_BUCKET} WITHOUT object lock (not supported by this storage version)"
  fi
fi

if $S3 s3api put-bucket-versioning --bucket "${S3_BUCKET}" \
     --versioning-configuration Status=Enabled >/dev/null 2>&1; then
  echo "Versioning enabled on ${S3_BUCKET}"
else
  echo "WARNING: could not enable versioning on ${S3_BUCKET}"
fi
