#!/usr/bin/env bash
set -euo pipefail
umask 077

asset="$(find /opt -xdev -type f \
  -name '图片1_20260706163459A002.png' -print -quit 2>/dev/null || true)"
[[ -n "$asset" && -f "$asset" && -r "$asset" ]] || {
  echo "SEAL_ASSET_MISSING" >&2
  exit 2
}

echo "seal_asset_bytes=$(stat -c '%s' "$asset")"
echo "seal_asset_sha256=$(sha256sum "$asset" | awk '{print $1}')"
file "$asset"
if command -v identify >/dev/null 2>&1; then
  identify -format 'seal_asset_geometry=%wx%h\n' "$asset"
else
  python3 - "$asset" <<'PY'
import struct
import sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()[:24]
if len(data) != 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
    raise SystemExit("unsupported image format")
width, height = struct.unpack(">II", data[16:24])
print(f"seal_asset_geometry={width}x{height}")
PY
fi

echo "SEAL_ASSET_PREFLIGHT_OK"
