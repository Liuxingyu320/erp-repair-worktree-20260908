#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="${1:-$ROOT_DIR/output/new-business-release/erp-new-business-20260714-ecs-host.tar.gz}"

"$ROOT_DIR/scripts/verify-new-business-release-scope.sh" --candidate
"$ROOT_DIR/scripts/verify-new-business-release-payload.sh" "$ROOT_DIR/docker"

mkdir -p "$(dirname "$OUTPUT")"
tar \
  --exclude='docker/.env' \
  --exclude='docker/mysql/data' \
  --exclude='docker/mysql/logs' \
  --exclude='docker/mysql/seed' \
  --exclude='docker/redis/data' \
  --exclude='docker/nacos/logs' \
  --exclude='docker/nginx/logs' \
  --exclude='docker/erp/uploadPath' \
  --exclude='*/backup/*' \
  --exclude='*/backups/*' \
  --exclude='*/dump/*' \
  --exclude='*/dumps/*' \
  --exclude='*/runtime/*' \
  --exclude='* 2.*' \
  --exclude='*.xlsx' \
  --exclude='*.xls' \
  --exclude='*.csv' \
  --exclude='*.tsv' \
  --exclude='*.dump' \
  --exclude='*.bak' \
  --exclude='*.backup' \
  --exclude='*.sql.gz' \
  --exclude='*.pem' \
  --exclude='*.key' \
  --exclude='*.p12' \
  --exclude='*.pfx' \
  --exclude='.DS_Store' \
  --exclude='._*' \
  -czf "$OUTPUT" -C "$ROOT_DIR" docker

python3 "$ROOT_DIR/scripts/verify_new_business_release_archive.py" "$OUTPUT" \
  --json-output "$OUTPUT.verify.json"
shasum -a 256 "$OUTPUT" > "$OUTPUT.sha256"
printf '[PASS] release package created: %s\n' "$OUTPUT"
