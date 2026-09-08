#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
select dept_type, count(*) cnt
from sys_dept
where del_flag='0'
group by dept_type
order by dept_type;

select 'sys_user_shop' as table_name, count(*) cnt from sys_user_shop
union all select 'sys_user_role', count(*) from sys_user_role
union all select 'sys_user_post', count(*) from sys_user_post;

select r.role_id, r.role_name, count(rm.menu_id) menu_count
from sys_role r
left join sys_role_menu rm on rm.role_id=r.role_id
where r.role_id in (105,106,107)
group by r.role_id, r.role_name
order by r.role_id;

select u.user_id,u.nick_name,u.phonenumber,u.dept_id,d.dept_name
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id
where u.user_id in (1043,1050,1059,1077,1086,1091,1092,1093)
order by u.user_id;

select us.user_id,u.nick_name,us.dept_id,d.dept_name,us.is_default
from sys_user_shop us
join sys_user u on u.user_id=us.user_id
left join sys_dept d on d.dept_id=us.dept_id
where us.user_id in (1043,1059,1077,1086,1092,1093)
order by us.user_id,us.dept_id;

select 'orphan_user_dept' check_name,count(*) cnt
from sys_user u left join sys_dept d on d.dept_id=u.dept_id
where u.del_flag='0' and (d.dept_id is null or d.del_flag <> '0')
union all
select 'orphan_user_shop', count(*)
from sys_user_shop us left join sys_dept d on d.dept_id=us.dept_id
where d.dept_id is null or d.del_flag <> '0'
union all
select 'orphan_user_role', count(*)
from sys_user_role ur left join sys_role r on r.role_id=ur.role_id
where r.role_id is null or r.del_flag <> '0'
union all
select 'orphan_user_post', count(*)
from sys_user_post up left join sys_post p on p.post_id=up.post_id
where p.post_id is null;
SQL
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-verify-dept-user-auth", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
