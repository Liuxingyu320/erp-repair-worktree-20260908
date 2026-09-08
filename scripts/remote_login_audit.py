#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

echo "-- app processes --"
ps -eo pid,ppid,comm,args | grep -E 'java|nginx|ruoyi|erp|gateway|auth|system|inventory' | grep -v grep || true

echo "-- listening ports --"
ss -ltnp 2>/dev/null | grep -E ':80|:8080|:9200|:9201|:9203|:9300|:3306' || true

echo "-- login related tables --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
show tables like '%login%';
show tables like '%log%';
SQL

echo "-- current active users summary --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select count(*) all_users,
       sum(del_flag='0') active_users,
       sum(del_flag='0' and status='0') enabled_active_users,
       sum(del_flag='0' and (password is null or password='')) active_blank_passwords
from sys_user;
SQL

echo "-- password diffs vs pre-sync backup --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select count(*) same_user_rows,
       sum(coalesce(c.password,'') <> coalesce(b.password,'')) password_changed,
       sum(coalesce(c.status,'') <> coalesce(b.status,'')) status_changed,
       sum(coalesce(c.del_flag,'') <> coalesce(b.del_flag,'')) del_flag_changed,
       sum(coalesce(c.user_name,'') <> coalesce(b.user_name,'')) username_changed,
       sum(coalesce(c.phonenumber,'') <> coalesce(b.phonenumber,'')) phone_changed
from sys_user c
join backup_dept_user_auth_sync_sys_user_20260701 b on b.user_id=c.user_id;

select c.user_id,
       b.user_name backup_user_name, c.user_name current_user_name,
       b.nick_name backup_nick_name, c.nick_name current_nick_name,
       b.phonenumber backup_phone, c.phonenumber current_phone,
       b.status backup_status, c.status current_status,
       b.del_flag backup_del_flag, c.del_flag current_del_flag,
       length(b.password) backup_pw_len, length(c.password) current_pw_len,
       left(b.password, 12) backup_pw_prefix, left(c.password, 12) current_pw_prefix,
       (coalesce(c.password,'') <> coalesce(b.password,'')) password_changed
from sys_user c
left join backup_dept_user_auth_sync_sys_user_20260701 b on b.user_id=c.user_id
where c.user_id in (1,938,939,940,941,942,943,1049,1091,1092,1093)
   or coalesce(c.user_name,'') in ('admin','123','18996165827')
   or coalesce(b.user_name,'') in ('admin','123','18996165827')
order by c.user_id;
SQL

echo "-- recent login log rows if table exists --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select table_name
from information_schema.tables
where table_schema='BossERP_NEW'
  and table_name in ('sys_logininfor','sys_login_infor','sys_oper_log')
order by table_name;
SQL
if mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" -N -e "show tables like 'sys_logininfor'" | grep -q sys_logininfor; then
  mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" -e "show columns from sys_logininfor;" || true
  mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select *
from sys_logininfor
order by info_id desc
limit 30;
SQL
fi

echo "-- recent service log lines --"
if command -v journalctl >/dev/null 2>&1; then
  journalctl --since "45 minutes ago" --no-pager 2>/dev/null | grep -Ei 'login|登录|auth|password|Bad credentials|用户|Exception|ERROR|sys_user' | tail -n 200 || true
fi
for f in /opt/erp*/logs/*.log /opt/erp*/logs/*/*.log /var/log/nginx/error.log; do
  [ -f "$f" ] || continue
  echo "## log=$f"
  tail -n 300 "$f" | grep -Ei 'login|登录|auth|password|Bad credentials|用户|Exception|ERROR|sys_user' | tail -n 120 || true
done
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-login-audit", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
