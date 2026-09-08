#!/usr/bin/env bash
set -euo pipefail
CONF=/www/server/panel/vhost/nginx/erp-new-ip-80.conf
python3 - <<'PY'
from pathlib import Path
path = Path("/www/server/panel/vhost/nginx/erp-new-ip-80.conf")
text = path.read_text()
text = text.replace(r"\\\\.(?:css|js|mjs|png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|eot)$", r"\\.(?:css|js|mjs|png|jpg|jpeg|gif|ico|svg|webp|woff2?|ttf|eot)$")
path.write_text(text)
PY
nginx -t
nginx -s reload
sleep 1
echo "-- headers index --"
curl -sS --max-time 10 -D - -H 'Host: 8.152.199.39' http://127.0.0.1/index.html -o /tmp/index.html | sed -n '1,24p'
echo "-- headers chunk --"
curl -sS --max-time 10 -D - -H 'Host: 8.152.199.39' http://127.0.0.1/static/css/chunk-72e01a41.58cb5ec9.css -o /tmp/chunk.css | sed -n '1,24p'
echo "-- config --"
nl -ba "$CONF" | sed -n '1,36p'
