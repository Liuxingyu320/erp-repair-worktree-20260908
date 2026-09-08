#!/usr/bin/env bash
set -euo pipefail

nginx -T 2>&1 | sed -n '240,410p'

echo "-- files named erp/checkers --"
find /www/server -path '*nginx*' -type f \( -name '*erp*' -o -name '*checker*' -o -name '*checkers*' \) -printf '%p\n' 2>/dev/null | sort

echo "-- locations in all nginx configs --"
grep -R "location = /prod-api/captchaImage\|root /opt/erp-new/nginx/html/dist\|location /prod-api/" -n /www/server 2>/dev/null | head -n 200 || true
