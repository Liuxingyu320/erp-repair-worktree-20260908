#!/usr/bin/env bash
set -euo pipefail

echo "CONTAINERS"
docker ps --format '{{.Names}}\t{{.Image}}' | sed -n '1,80p'

echo "LISTENERS"
ss -lntp | awk 'NR == 1 || /:80 |:443 |:6379 |:8080 |:9200 /'

echo "REDIS_CONFIG_HINTS"
for file in \
  /opt/erp-new/docker-compose.yml \
  /opt/erp-new/docker-compose.yaml \
  /opt/erp-new/.env \
  /opt/ERP-NEW/docker-compose.yml \
  /opt/ERP-NEW/docker-compose.yaml \
  /opt/ERP-NEW/.env
do
  if [[ -r "$file" ]]; then
    echo "FILE=$file"
    grep -Ei 'redis|6379|password' "$file" | sed -E 's/([Pp][Aa][Ss][Ss][Ww][Oo][Rr][Dd][^:=]*[:=])[[:space:]]*[^[:space:]]+/\1<redacted>/g' | sed -n '1,40p'
  fi
done
