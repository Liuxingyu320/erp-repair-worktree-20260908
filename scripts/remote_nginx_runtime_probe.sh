#!/usr/bin/env bash
set -euo pipefail

echo "-- nginx binaries --"
command -v nginx || true
ls -l "$(command -v nginx)" 2>/dev/null || true
ls -l /www/server/nginx/sbin/nginx 2>/dev/null || true

echo "-- nginx processes --"
ps -eo pid,ppid,comm,args | grep nginx | grep -v grep || true

echo "-- listening 80 --"
ss -ltnp 2>/dev/null | grep ':80 ' || true

echo "-- pid files --"
for f in /run/nginx.pid /var/run/nginx.pid /www/server/nginx/logs/nginx.pid; do
  [ -f "$f" ] || continue
  printf '%s ' "$f"
  cat "$f"
done

echo "-- direct curl old path --"
for host in 8.152.199.39 _ localhost; do
  echo "## host=$host"
  curl -sS --max-time 10 -D - -H "Host: $host" http://127.0.0.1/prod-api/captchaImage -o /tmp/nginx-runtime-captcha.json | sed -n '1,18p'
  head -c 220 /tmp/nginx-runtime-captcha.json; echo
done
