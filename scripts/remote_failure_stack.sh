#!/usr/bin/env bash
set -u
latest=$(ls -td /opt/erp-new-20260701* 2>/dev/null | head -1)
echo "latest=$latest"
for f in \
  "$latest/docker/logs/gateway.err.log" \
  "$latest/docker/logs/auth.err.log" \
  "$latest/docker/logs/system.err.log" \
  "$latest/docker/logs/inventory.err.log"; do
  [ -f "$f" ] || continue
  echo "### $f"
  tail -n 80 "$f" || true
done
echo "-- manifest/index entries --"
for jar in "$latest/docker/erp/gateway/jar/erp-gateway.jar" "$(readlink -f /opt/erp-new)/erp/gateway/jar/erp-gateway.jar"; do
  echo "### $jar"
  unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null || true
  echo
  unzip -l "$jar" 'BOOT-INF/classpath.idx' 'BOOT-INF/layers.idx' 2>/dev/null || true
done
