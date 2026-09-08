#!/usr/bin/env bash
set -u

echo "date=$(date '+%F %T %z')"
echo "current=$(readlink -f /opt/erp-new 2>/dev/null || true)"

echo "-- services --"
failed=0
for svc in gateway auth system gen job oa inventory file; do
  state=$(systemctl is-active "erp-new@$svc.service" 2>/dev/null || true)
  echo "$svc=$state"
  [ "$state" = active ] || failed=1
done

echo "-- local http probes --"
for url in \
  http://127.0.0.1/ \
  http://127.0.0.1/mobile/store \
  http://127.0.0.1/prod-api/code \
  http://127.0.0.1/prod-api/auth/passwordPolicy \
  http://127.0.0.1/prod-api/captchaImage; do
  body="/tmp/erp-probe-$(echo "$url" | tr -c 'A-Za-z0-9' '_')"
  code=$(curl -sS -H 'Host: 8.152.199.39' -o "$body" -w '%{http_code}' --max-time 12 "$url" || true)
  bytes=$(wc -c < "$body" 2>/dev/null || echo 0)
  echo "$code bytes=$bytes $url"
  head -c 180 "$body" 2>/dev/null | tr '\n' ' '
  echo
done

echo "-- nginx prod-api config --"
nginx -T 2>/dev/null | grep -En 'location = /prod-api|location /prod-api|proxy_pass|root ' | sed -n '1,140p' || true

echo "-- db permission probes --"
if [ -f /root/.erp-mysql-root-pass ]; then
  MYSQL_ROOT_PASS=$(cat /root/.erp-mysql-root-pass)
  mysql -uroot -p"$MYSQL_ROOT_PASS" -N -B BossERP_NEW <<'SQL' 2>&1 || failed=1
SELECT 'oa_sign_tables', COUNT(*) FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('oa_sign_template','oa_sign_package','oa_sign_package_document','oa_sign_event');
SELECT 'dz_transfer_receive', COUNT(*)
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key = 'dz' AND r.del_flag = '0' AND m.perms = 'inv:transfer:receive';
SELECT 'purchase_role_menu', COUNT(*)
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key IN ('admin','yyzj','zjl','qyyyzzj','yyjl','zdjl','dz')
  AND m.perms IN ('oa:purchase:list','oa:purchase:query','oa:purchase:add','oa:purchase:export');
SELECT 'qyyyzzj_purchase', COUNT(*)
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key = 'qyyyzzj'
  AND m.perms IN ('oa:purchase:list','oa:purchase:query','oa:purchase:add','oa:purchase:export');
SQL
fi

echo "-- recent gateway/auth logs --"
journalctl --no-pager -u erp-new@gateway.service -u erp-new@auth.service -n 120 \
  | grep -Ei 'error|warn|exception|passwordPolicy|captchaImage|/code|Bad Gateway|502' || true

exit "$failed"
