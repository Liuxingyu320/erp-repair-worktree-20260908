#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

PACKAGE="/opt/erp-new-packages/erp-aliyun-deploy-20260722-100205-current-ecs-host.tar.gz"
EXPECTED_SIZE="1054039307"
EXPECTED_SHA256="d0d3392fbe8654e5f86ca9b623c8bff0b3cefadd614e0afe55527591ffc70108"

test -f "$PACKAGE"
test ! -L "$PACKAGE"

ACTUAL_SIZE="$(stat -c %s "$PACKAGE")"
ACTUAL_SHA256="$(sha256sum "$PACKAGE" | awk '{print $1}')"

printf 'package=%s\n' "$PACKAGE"
printf 'size=%s expected_size=%s\n' "$ACTUAL_SIZE" "$EXPECTED_SIZE"
printf 'sha256=%s expected_sha256=%s\n' "$ACTUAL_SHA256" "$EXPECTED_SHA256"
df -Pk /
du -sk /opt/erp-new-packages /opt/erp-new-data-backups 2>/dev/null || true

test "$ACTUAL_SIZE" = "$EXPECTED_SIZE"
test "$ACTUAL_SHA256" = "$EXPECTED_SHA256"

echo "UPLOAD_PACKAGE_OK path=$PACKAGE size=$ACTUAL_SIZE sha256=$ACTUAL_SHA256"
