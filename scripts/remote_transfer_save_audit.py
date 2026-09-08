#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
DB=BossERP_NEW
MYSQL_ROOT_PASS="$(cat /root/.erp-mysql-root-pass)"

echo "-- docker ps --"
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' || true

echo "-- recent transfer rows --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL' || true
select transfer_id, order_no, from_dept_id, from_dept_name, from_warehouse_id,
       to_dept_id, to_dept_name, to_warehouse_id, status, total_quantity, total_amount,
       create_by, create_time, update_by, update_time
from inv_transfer_order
order by transfer_id desc
limit 10;
SQL

echo "-- recent transfer details --"
mysql --batch --raw -uroot -p"$MYSQL_ROOT_PASS" "$DB" <<'SQL' || true
select d.detail_id,d.transfer_id,o.order_no,d.product_id,d.product_name,d.quantity,d.cost_price,d.amount
from inv_transfer_detail d
left join inv_transfer_order o on o.transfer_id=d.transfer_id
order by d.detail_id desc
limit 20;
SQL

echo "-- recent backend log errors --"
for c in $(docker ps --format '{{.Names}}' | grep -E 'inventory|gateway|nginx|auth|system' || true); do
  echo "## container=$c"
  docker logs --since 90m "$c" 2>&1 | grep -Ei 'transfer|调拨|Exception|ERROR|Bad SQL|ServiceException|SQLIntegrityConstraint|Duplicate|Cannot|Null|保存|save' | tail -n 200 || true
done
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-transfer-save-audit", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
