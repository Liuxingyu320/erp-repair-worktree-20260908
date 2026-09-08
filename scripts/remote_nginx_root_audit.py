#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
echo "-- nginx config --"
cat /www/server/nginx/conf/vhost/checkers-nginx.conf || true
echo "-- nginx -T relevant server blocks --"
/www/server/nginx/sbin/nginx -T -c /www/server/nginx/conf/nginx.conf 2>&1 \
  | awk 'BEGIN{show=0} /server[[:space:]]*\{/{buf=$0"\n"; show=1; next} show{buf=buf $0 "\n"} show && /\}/{ if (buf ~ /listen[[:space:]]+80|server_name[[:space:]]+8\.152\.199\.39|prod-api|erp-new|BossERP|企业管理系统/) print buf "\n---"; show=0; buf="" }' \
  | head -n 300 || true
echo "-- all static roots with index --"
find /www /opt -maxdepth 7 -type f -name index.html -printf '%TY-%Tm-%Td %TH:%TM %s %p\n' 2>/dev/null | sort | tail -n 120
echo "-- served index js refs --"
curl -s http://127.0.0.1/ | grep -o 'static/js/[^"[:space:]]*' | head -n 20 || true
echo "-- served asset endpoint strings --"
for asset in $(curl -s http://127.0.0.1/ | grep -o 'static/js/[^"[:space:]]*' | head -n 20); do
  echo "## asset=$asset"
  curl -s "http://127.0.0.1/$asset" | grep -o "captchaImage\|/code\|/auth/login\|passwordPolicy" | sort | uniq -c || true
done
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-nginx-root-audit", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
