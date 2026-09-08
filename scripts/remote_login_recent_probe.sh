#!/usr/bin/env bash
set -euo pipefail

DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

echo "-- sys_logininfor columns --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" -e "show columns from sys_logininfor;"

echo "-- latest login logs --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select *
from sys_logininfor
order by info_id desc
limit 20;
SQL

echo "-- user/password diff summary vs pre-sync backup --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select count(*) same_user_rows,
       sum(coalesce(c.password,'') <> coalesce(b.password,'')) password_changed,
       sum(coalesce(c.status,'') <> coalesce(b.status,'')) status_changed,
       sum(coalesce(c.del_flag,'') <> coalesce(b.del_flag,'')) del_flag_changed,
       sum(coalesce(c.user_name,'') <> coalesce(b.user_name,'')) username_changed
from sys_user c
join backup_dept_user_auth_sync_sys_user_20260701 b on b.user_id=c.user_id;

select c.user_id,
       c.user_name,
       c.nick_name,
       c.phonenumber,
       c.status,
       c.del_flag,
       case when b.user_id is null then 'inserted'
            when coalesce(c.password,'') <> coalesce(b.password,'') then 'password_changed'
            else 'password_same' end as password_state,
       b.user_name as backup_user_name,
       b.nick_name as backup_nick_name,
       b.status as backup_status,
       b.del_flag as backup_del_flag
from sys_user c
left join backup_dept_user_auth_sync_sys_user_20260701 b on b.user_id=c.user_id
where c.user_id in (1,943,1049,1091,1092,1093)
   or c.user_name in ('admin','18210107086','18996165827','123')
   or b.user_name in ('admin','18210107086','18996165827','123')
order by c.user_id;
SQL
