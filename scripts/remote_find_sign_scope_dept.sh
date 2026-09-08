#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"
DB="$(
  mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASS" information_schema \
    -e "SELECT table_schema
        FROM tables
        WHERE table_name = 'oa_sign_template'
          AND table_schema NOT IN ('mysql','information_schema','performance_schema','sys')
        ORDER BY table_schema
        LIMIT 1"
)"
test -n "$DB"

mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL'
SELECT dept_id, parent_id, ancestors, dept_name, dept_type, status
FROM sys_dept
WHERE dept_name LIKE '%金英灵韵%'
   OR dept_name LIKE '%洲至%'
   OR dept_name LIKE '%舟山茗汇%'
ORDER BY dept_id;
SQL
