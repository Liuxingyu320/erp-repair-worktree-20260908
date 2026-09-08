#!/usr/bin/env bash
set -euo pipefail

services=(gateway auth monitor system gen job oa inventory file approval)
for service in "${services[@]}"; do
  state="$(systemctl is-active "erp-new@$service.service")"
  echo "service=$service state=$state"
  [[ "$state" == "active" ]]
done

health_endpoints=(
  "8080:actuator/health"
  "9200:actuator/health"
  "9201:actuator/health"
  "9202:actuator/health"
  "9203:actuator/health"
  "9204:actuator/health"
  "9205:actuator/health"
  "9300:actuator/health"
  "9206:actuator/health"
)
for entry in "${health_endpoints[@]}"; do
  port="${entry%%:*}"
  path="${entry#*:}"
  body="$(curl -fsS --max-time 8 "http://127.0.0.1:$port/$path")"
  echo "health_port=$port up=$(printf '%s' "$body" | grep -c '"status":"UP"')"
  printf '%s' "$body" | grep -Fq '"status":"UP"'
done

curl -fsS --max-time 10 -H 'Host: 8.152.199.39' http://127.0.0.1/ >/dev/null
curl -fsS --max-time 10 -H 'Host: 8.152.199.39' http://127.0.0.1/prod-api/code >/dev/null

rpm -q libreoffice-writer google-noto-sans-cjk-ttc-fonts poppler-utils
libreoffice --version | head -n 1
fc-match -f 'font=%{family};file=%{file}\n' 'Noto Sans CJK SC' | head -n 1
echo "render_temp_residue=$(find /tmp -maxdepth 1 -type d \
  \( -name 'sign-template-render-verify.*' -o -name 'sign-template-marker-locate.*' \) \
  | wc -l | tr -d ' ')"
echo "root_available_bytes=$(df -B1 --output=avail / | tail -n 1 | tr -d ' ')"
echo "SIGN_RENDER_RUNTIME_POSTCHECK_OK"
