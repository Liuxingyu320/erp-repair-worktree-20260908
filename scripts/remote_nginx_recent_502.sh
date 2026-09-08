#!/usr/bin/env bash
set -u
echo "date=$(date '+%F %T %z')"
echo "current=$(readlink -f /opt/erp-new)"
echo "-- recent 502/permission/static errors --"
for f in /www/wwwlogs/*access*.log /www/wwwlogs/*.log /www/server/nginx/logs/*.log; do
  [ -f "$f" ] || continue
  echo "### $f"
  tail -n 300 "$f" 2>/dev/null | grep -E ' 502 |chunk-72e01a41|Permission denied|/static/css|/static/js' | tail -n 60 || true
done
echo "-- resource status from server --"
curl -sS --max-time 10 -D - -H 'Accept-Encoding: gzip, deflate' -H 'Host: 8.152.199.39' http://127.0.0.1/static/css/chunk-72e01a41.58cb5ec9.css -o /tmp/chunk.css.gz | sed -n '1,14p'
wc -c /tmp/chunk.css.gz
