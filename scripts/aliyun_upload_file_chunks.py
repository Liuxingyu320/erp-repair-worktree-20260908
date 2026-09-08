#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import hashlib
import shlex
from pathlib import Path

from compare_local_remote_depts import decode_output, read_credentials, run_command


def execute(command: str, *, region: str, instance_id: str, name: str, timeout: int) -> str:
    result = run_command(read_credentials(), region, instance_id, command, name, timeout)
    output = decode_output(result)
    if result.get("ExitCode") not in (0, "0"):
        raise RuntimeError(output)
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("local_file")
    parser.add_argument("remote_file")
    parser.add_argument("--region", default="cn-beijing")
    parser.add_argument("--instance-id", default="i-2ze2pb4mhep0g9rmecpw")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--chunk-size", type=int, default=12000)
    args = parser.parse_args()

    source = Path(args.local_file).read_bytes()
    expected_hash = hashlib.sha256(source).hexdigest()
    encoded = base64.b64encode(source).decode("ascii")
    remote_file = shlex.quote(args.remote_file)
    remote_encoded = shlex.quote(args.remote_file + ".b64")

    execute(
        f"set -euo pipefail; umask 077; : > {remote_encoded}",
        region=args.region,
        instance_id=args.instance_id,
        name="erp-upload-init",
        timeout=args.timeout,
    )
    for index in range(0, len(encoded), args.chunk_size):
        chunk = encoded[index:index + args.chunk_size]
        execute(
            f"set -euo pipefail; printf '%s' {shlex.quote(chunk)} >> {remote_encoded}",
            region=args.region,
            instance_id=args.instance_id,
            name=f"erp-upload-{index // args.chunk_size + 1}",
            timeout=args.timeout,
        )
    output = execute(
        "set -euo pipefail; "
        f"base64 -d {remote_encoded} > {remote_file}; "
        f"rm -f {remote_encoded}; "
        f"actual=$(sha256sum {remote_file} | awk '{{print $1}}'); "
        f"test \"$actual\" = {shlex.quote(expected_hash)}; "
        f"chmod 600 {remote_file}; "
        f"echo UPLOAD_OK sha256=$actual bytes=$(wc -c < {remote_file})",
        region=args.region,
        instance_id=args.instance_id,
        name="erp-upload-finalize",
        timeout=args.timeout,
    )
    print(output)


if __name__ == "__main__":
    main()
