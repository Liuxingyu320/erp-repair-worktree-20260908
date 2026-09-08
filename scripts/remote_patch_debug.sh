#!/usr/bin/env bash
set -u
echo "current=$(readlink -f /opt/erp-new)"
echo "date=$(date '+%F %T %z')"
echo "-- releases --"
ls -ltd /opt/erp-new-* 2>/dev/null | head -8
latest=$(ls -td /opt/erp-new-* 2>/dev/null | head -1)
echo "latest=$latest"
if [ -n "${latest:-}" ] && [ -d "$latest/docker" ]; then
  echo "-- jar files --"
  find "$latest/docker/erp" -path '*/jar/*.jar' -maxdepth 8 -type f -exec file {} \; | sed -n '1,80p'
  echo "-- jar integrity --"
  find "$latest/docker/erp" -path '*/jar/*.jar' -type f -exec sh -c 'for f; do unzip -t "$f" >/dev/null 2>&1 || echo "BAD_JAR:$f"; done' sh {} +
  echo "-- patch dirs --"
  find "$latest/patch" -maxdepth 8 \( -name '*.jar.patch' -o -name '._*' \) -print 2>/dev/null | sed -n '1,120p'
  echo "-- app logs latest release --"
  find "$latest/docker/logs" -maxdepth 3 -type f 2>/dev/null | sort | sed -n '1,120p'
  for f in $(find "$latest/docker/logs" -maxdepth 3 -type f 2>/dev/null | sort | head -20); do
    echo "### tail $f"
    tail -n 80 "$f" 2>/dev/null || true
  done
fi
echo "-- service active --"
for svc in gateway auth system gen job oa inventory file; do
  printf "%s=" "$svc"
  systemctl is-active "erp-new@$svc.service" 2>/dev/null || true
done
echo "-- service status --"
systemctl --no-pager --full status \
  erp-new@gateway.service erp-new@auth.service erp-new@system.service \
  erp-new@job.service erp-new@oa.service erp-new@inventory.service erp-new@file.service \
  | sed -n '1,260p' || true
echo "-- journal recent --"
journalctl --no-pager -n 240 \
  -u erp-new@gateway.service -u erp-new@auth.service -u erp-new@system.service \
  -u erp-new@job.service -u erp-new@oa.service -u erp-new@inventory.service -u erp-new@file.service \
  | sed -n '1,260p' || true
echo "-- ports --"
ss -ltnp | grep -E ':(80|8080|9200|9201|9203|9204|9205|9300)\b' || true
echo "-- nginx --"
nginx -t 2>&1 || true
curl -fsS --max-time 10 -I http://127.0.0.1/ 2>&1 || true
curl -fsS --max-time 10 -I http://127.0.0.1/prod-api/code 2>&1 || true
echo "-- db migration fields --"
if [ -f /root/.erp-mysql-root-pass ]; then
  MYSQL_ROOT_PASS=$(cat /root/.erp-mysql-root-pass)
  mysql -uroot -p"$MYSQL_ROOT_PASS" -N -B BossERP_NEW -e "SHOW COLUMNS FROM inv_transfer_order LIKE 'total_amount'; SHOW COLUMNS FROM inv_transfer_order LIKE 'reference_amount'; SHOW COLUMNS FROM inv_transfer_detail LIKE 'cost_price'; SHOW COLUMNS FROM inv_transfer_detail LIKE 'amount';" 2>&1 || true
fi
