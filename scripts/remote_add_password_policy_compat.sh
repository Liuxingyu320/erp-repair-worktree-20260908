#!/usr/bin/env bash
set -euo pipefail

CONF=/www/server/panel/vhost/nginx/erp-new-ip-80.conf
BACKUP="${CONF}.bak.password-policy.$(date +%Y%m%d%H%M%S)"

echo "-- auth service direct probe before --"
curl -sS --max-time 10 -D - http://127.0.0.1:9200/passwordPolicy -o /tmp/auth-password-policy.json | sed -n '1,20p' || true
head -c 180 /tmp/auth-password-policy.json 2>/dev/null || true; echo

cp -a "$CONF" "$BACKUP"

python3 - <<'PY'
from pathlib import Path

path = Path("/www/server/panel/vhost/nginx/erp-new-ip-80.conf")
text = path.read_text()
marker = "    location /prod-api/ {\n"
block = """    # Anonymous login page helper; gateway currently does not whitelist this route.
    location = /prod-api/auth/passwordPolicy {
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_pass http://127.0.0.1:9200/passwordPolicy;
    }

"""

if "location = /prod-api/auth/passwordPolicy" not in text:
    if marker not in text:
        raise SystemExit("expected /prod-api/ location not found")
    text = text.replace(marker, block + marker, 1)
    path.write_text(text)
PY

nginx -t
nginx -s reload
sleep 1

echo "backup=$BACKUP"
echo "-- public nginx route after --"
curl -sS --max-time 10 -D - http://127.0.0.1/prod-api/auth/passwordPolicy -H 'Host: 8.152.199.39' -o /tmp/nginx-password-policy.json | sed -n '1,20p'
head -c 180 /tmp/nginx-password-policy.json; echo
