#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
echo "-- nginx erp site config candidates --"
grep -R "8.152.199.39\|prod-api\|try_files\|root .*erp\|alias .*erp" -n /www/server/nginx/conf /etc/nginx 2>/dev/null | head -n 200 || true

echo "-- frontend roots --"
for d in /opt/erp-new/html /opt/erp-new/erp/html /www/wwwroot/erp-new /www/wwwroot/default /www/javademo/tea-java-erp/html/ZYX-admin-ui0.0.1/dist /www/javademo/tea-java-erp/html/ZYX-admin-ui0.0.1; do
  [ -d "$d" ] || continue
  echo "## dir=$d"
  find "$d" -maxdepth 3 -type f \( -name 'index.html' -o -name '*.js' \) -printf '%TY-%Tm-%Td %TH:%TM %s %p\n' | sort | tail -n 30
  echo "## login endpoint strings"
  grep -R --include='*.js' -n "captchaImage\|/code\|/auth/login\|passwordPolicy" "$d" 2>/dev/null | head -n 40 || true
done
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-frontend-login-asset-audit", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
