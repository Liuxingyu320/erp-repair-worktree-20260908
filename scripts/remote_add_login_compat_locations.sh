#!/usr/bin/env bash
set -euo pipefail

CONF=/www/server/panel/vhost/nginx/erp-new-ip-80.conf
BACKUP="${CONF}.bak.login-compat.$(date +%Y%m%d%H%M%S)"

cp -a "$CONF" "$BACKUP"

python3 - <<'PY'
from pathlib import Path

path = Path("/www/server/panel/vhost/nginx/erp-new-ip-80.conf")
text = path.read_text()
marker = "    location /prod-api/ {\n"
block = """    # Compatibility for old cached frontend API paths.
    location = /prod-api/captchaImage {
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_pass http://127.0.0.1:8080/code;
    }

    location = /prod-api/login {
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_pass http://127.0.0.1:8080/auth/login;
    }

    location = /prod-api/getInfo {
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_pass http://127.0.0.1:8080/system/user/getInfo;
    }

    location = /prod-api/getRouters {
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header REMOTE-HOST $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_pass http://127.0.0.1:8080/system/menu/getRouters;
    }

"""

if "location = /prod-api/captchaImage" not in text:
    if marker not in text:
        raise SystemExit("expected /prod-api/ location not found")
    text = text.replace(marker, block + marker, 1)
    path.write_text(text)
PY

nginx -t
nginx -s reload

echo "backup=$BACKUP"
echo "-- compat endpoints --"
curl -sS --max-time 10 -D - http://127.0.0.1/prod-api/captchaImage -H 'Host: 8.152.199.39' -o /tmp/compat-captcha.json | sed -n '1,20p'
head -c 180 /tmp/compat-captcha.json; echo
curl -sS --max-time 10 -D - http://127.0.0.1/prod-api/login -H 'Host: 8.152.199.39' -H 'Content-Type: application/json' --data '{"username":"__probe__","password":"x","code":"","uuid":""}' -o /tmp/compat-login.json | sed -n '1,20p'
head -c 180 /tmp/compat-login.json; echo
