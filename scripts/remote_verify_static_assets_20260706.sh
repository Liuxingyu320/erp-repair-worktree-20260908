#!/usr/bin/env bash
set -u

ROOT=/opt/erp-new/nginx/html/dist
failed=0
total=0
rm -f /tmp/erp-static-asset-check-counts

find "$ROOT/static" -type f \( -name '*.css' -o -name '*.js' \) | sort | while read -r file; do
  path="/${file#$ROOT/}"
  total=$((total + 1))
  code=$(curl -sS -H 'Host: 8.152.199.39' -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1$path" || true)
  if [ "$code" != "200" ]; then
    failed=$((failed + 1))
    echo "$code $path"
  fi
  echo "$total $failed" > /tmp/erp-static-asset-check-counts
done

if [ -f /tmp/erp-static-asset-check-counts ]; then
  read -r total failed < /tmp/erp-static-asset-check-counts
fi
echo "checked=$total failed=$failed"
[ "$failed" = 0 ]
