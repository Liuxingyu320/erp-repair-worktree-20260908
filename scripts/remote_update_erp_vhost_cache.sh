#!/usr/bin/env bash
set -euo pipefail
CONF=/www/server/panel/vhost/nginx/erp-new-ip-80.conf
BACKUP="${CONF}.bak.$(date +%Y%m%d%H%M%S)"
cp -a "$CONF" "$BACKUP"
python3 - <<'PY'
from pathlib import Path

path = Path("/www/server/panel/vhost/nginx/erp-new-ip-80.conf")
text = path.read_text()
if "location = /index.html" not in text:
    old = """    location / {
        try_files $uri $uri/ /index.html;
    }
"""
    new = """    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files /index.html =404;
    }

    location ~* \\\\.(?:css|js|mjs|png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|eot)$ {
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        try_files $uri =404;
    }

    location / {
        add_header Cache-Control "no-cache" always;
        try_files $uri $uri/ /index.html;
    }
"""
    if old not in text:
        raise SystemExit("expected location / block not found")
    text = text.replace(old, new)
    path.write_text(text)
PY
nginx -t
systemctl reload nginx
echo "backup=$BACKUP"
curl -sS --max-time 10 -D - -H 'Host: 8.152.199.39' http://127.0.0.1/index.html -o /tmp/index.html | sed -n '1,20p'
curl -sS --max-time 10 -D - -H 'Host: 8.152.199.39' http://127.0.0.1/static/css/chunk-72e01a41.58cb5ec9.css -o /tmp/chunk.css | sed -n '1,20p'
