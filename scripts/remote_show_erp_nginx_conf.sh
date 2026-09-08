#!/usr/bin/env bash
set -u
nginx -T 2>/dev/null | awk '
  /server_name 8\\.152\\.199\\.39/ {show=1}
  show {print}
  show && /^}/ {show=0}
' | sed -n '1,220p'
echo "-- config files --"
grep -Rnl 'server_name 8.152.199.39' /www/server/nginx/conf /www/server/panel/vhost/nginx 2>/dev/null || true
