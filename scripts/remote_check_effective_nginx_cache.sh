#!/usr/bin/env bash
set -u
echo "-- file --"
nl -ba /www/server/panel/vhost/nginx/erp-new-ip-80.conf | sed -n '1,120p'
echo "-- effective around erp --"
nginx -T 2>&1 | grep -n -A80 -B5 'server_name 8.152.199.39' | sed -n '1,220p'
echo "-- all erp conf copies --"
find /www/server -name '*erp-new-ip-80*.conf' -print -exec nl -ba {} \; 2>/dev/null | sed -n '1,260p'
