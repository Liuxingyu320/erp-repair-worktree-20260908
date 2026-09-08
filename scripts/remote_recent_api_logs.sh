#!/usr/bin/env bash
set -u
echo "date=$(date '+%F %T %z')"
echo "-- nginx recent prod-api --"
for f in /www/wwwlogs/erp-new-ip-80.log /www/wwwlogs/erp-new-18081.log; do
  [ -f "$f" ] || continue
  echo "### $f"
  tail -n 500 "$f" | grep '/prod-api' | tail -n 120 || true
done
echo "-- gateway recent warnings/errors --"
journalctl --no-pager -u erp-new@gateway.service -n 120 | grep -Ei 'error|warn|exception|/prod-api|login|getInfo|getRouters|route|502|Bad Gateway' || true
echo "-- auth/system recent warnings/errors --"
journalctl --no-pager -u erp-new@auth.service -u erp-new@system.service -n 160 | grep -Ei 'error|warn|exception|login|getInfo|getRouters|unauthorized|认证|token' || true
