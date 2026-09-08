#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail

echo "-- nginx version --"
nginx -v 2>&1 || true

echo "-- config files containing dist/prod-api --"
grep -R "root /opt/erp-new/nginx/html/dist\|prod-api\|server_name 8.152.199.39" -n /www/server/nginx/conf /etc/nginx 2>/dev/null | head -n 240 || true

echo "-- nginx -T focused --"
nginx -T 2>/dev/null | awk '
  /server_name 8\.152\.199\.39/ ||
  /root \/opt\/erp-new\/nginx\/html\/dist/ ||
  /location .*prod-api/ ||
  /proxy_pass http:\/\/127\.0\.0\.1:8080/ ||
  /Cache-Control/ {
    print NR ":" $0
  }
' | head -n 260 || true
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(
        read_credentials(),
        args.region,
        args.instance_id,
        REMOTE_SCRIPT,
        "erp-nginx-login-compat-audit",
        args.timeout,
    )
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
