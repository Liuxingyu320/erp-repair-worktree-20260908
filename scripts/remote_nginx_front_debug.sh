#!/usr/bin/env bash
set -u
echo "current=$(readlink -f /opt/erp-new)"
namei -l /opt/erp-new/nginx/html/dist/index.html 2>&1 || true
echo "-- curl host --"
curl -sS --max-time 10 -I -H 'Host: 8.152.199.39' http://127.0.0.1/ 2>&1 || true
echo "-- nginx errors --"
tail -n 120 /www/wwwlogs/*.error.log /www/server/nginx/logs/error.log 2>/dev/null | sed -n '1,180p' || true
