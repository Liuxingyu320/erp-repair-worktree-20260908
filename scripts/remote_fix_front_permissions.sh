#!/usr/bin/env bash
set -euo pipefail
current=$(readlink -f /opt/erp-new)
release=$(dirname "$current")
chmod 755 "$release" "$current"
chmod -R a+rX "$current/nginx/html"
nginx -t
systemctl reload nginx
echo "release=$release"
namei -l /opt/erp-new/nginx/html/dist/index.html
curl -fsS --max-time 10 -I -H 'Host: 8.152.199.39' http://127.0.0.1/
