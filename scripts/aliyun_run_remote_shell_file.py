#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from compare_local_remote_depts import decode_output, read_credentials, run_command


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("script")
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--name", default="erp-remote-shell-file")
    args = parser.parse_args()

    command = Path(args.script).read_text()
    result = run_command(read_credentials(), args.region, args.instance_id, command, args.name, args.timeout)
    print(decode_output(result))
    if result.get("ExitCode") not in (0, "0"):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
