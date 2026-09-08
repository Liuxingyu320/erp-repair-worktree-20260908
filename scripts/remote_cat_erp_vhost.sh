#!/usr/bin/env bash
set -u
for f in /www/server/panel/vhost/nginx/erp-new-ip-80.conf /www/server/nginx/conf/vhost/checkers-nginx.conf; do
  [ -f "$f" ] || continue
  echo "### $f"
  nl -ba "$f" | sed -n '1,180p'
done
