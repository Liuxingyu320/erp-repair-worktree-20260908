#!/usr/bin/env python3
from __future__ import annotations

import argparse

from compare_local_remote_depts import decode_output, read_credentials, run_command


REMOTE_SCRIPT = r"""#!/usr/bin/env bash
set -euo pipefail
ROOT=/opt/erp-new/nginx/html/dist
echo "-- root listing --"
find "$ROOT" -maxdepth 3 -type f -printf '%TY-%Tm-%Td %TH:%TM %s %p\n' | sort | tail -n 80
echo "-- index snippets --"
grep -o 'src=[^ >]*\|href=[^ >]*' "$ROOT/index.html" | head -n 80 || true
echo "-- endpoint strings in built js --"
for p in captchaImage '/code' '/auth/login' passwordPolicy '/login' '/getInfo' '/getRouters'; do
  echo "## $p"
  grep -R --include='*.js' -l -- "$p" "$ROOT/static/js" 2>/dev/null | head -n 20 || true
done
echo "-- endpoint strings count --"
for p in captchaImage '/code' '/auth/login' passwordPolicy '/login' '/getInfo' '/getRouters'; do
  count="$(grep -R --include='*.js' -o -- "$p" "$ROOT/static/js" 2>/dev/null | wc -l || true)"
  printf '%s %s\n' "$p" "$count"
done
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    result = run_command(read_credentials(), args.region, args.instance_id, REMOTE_SCRIPT, "erp-served-asset-strings", args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
