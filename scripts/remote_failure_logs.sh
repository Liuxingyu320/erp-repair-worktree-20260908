#!/usr/bin/env bash
set -u
latest=$(ls -td /opt/erp-new-20260701* 2>/dev/null | head -1)
echo "latest=$latest"
echo "current=$(readlink -f /opt/erp-new)"
echo "-- run script --"
sed -n '1,180p' /opt/erp-new/run-erp-service.sh 2>/dev/null || true
echo "-- latest release Java errors --"
if [ -n "${latest:-}" ] && [ -d "$latest/docker/logs" ]; then
  for f in "$latest"/docker/logs/*.err.log "$latest"/docker/logs/*.log; do
    [ -f "$f" ] || continue
    echo "### $f"
    grep -Ein "Exception|ERROR|compressed|nested|Invalid|corrupt|Unable|Failed|Caused by" "$f" | tail -n 40 || true
  done
fi
echo "-- latest jar nested methods --"
if [ -n "${latest:-}" ] && [ -f "$latest/docker/erp/gateway/jar/erp-gateway.jar" ]; then
  zipinfo -l "$latest/docker/erp/gateway/jar/erp-gateway.jar" 'BOOT-INF/lib/erp-*.jar' 2>/dev/null || true
fi
if [ -n "${latest:-}" ] && [ -f "$latest/docker/erp/auth/jar/erp-auth.jar" ]; then
  zipinfo -l "$latest/docker/erp/auth/jar/erp-auth.jar" 'BOOT-INF/lib/erp-*.jar' 2>/dev/null || true
fi
