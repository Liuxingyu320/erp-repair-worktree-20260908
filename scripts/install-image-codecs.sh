#!/usr/bin/env bash
set -euo pipefail
umask 027
ERP_CODEC_BOOTSTRAP_PYTHON="${1:?Pass an absolute Python 3.10+ executable}"
ERP_CODEC_VENV=/data/erp-new-runtime/image-codecs
ERP_CODEC_CACHE=/data/erp-new-data/cache/image-codec-install
ERP_CODEC_TMP=/data/erp-new-data/tmp/image-codec-install
SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ "$ERP_CODEC_BOOTSTRAP_PYTHON" == /* && -x "$ERP_CODEC_BOOTSTRAP_PYTHON" ]]
[[ "$(findmnt -n -o TARGET -T /data)" == /data ]] || { echo '/data mount is required' >&2; exit 73; }
for path in /data/erp-new-runtime /data/erp-new-data/cache /data/erp-new-data/tmp; do
  [[ -d "$path" && -w "$path" && "$(findmnt -n -o TARGET -T "$path")" == /data ]]
done
"$ERP_CODEC_BOOTSTRAP_PYTHON" -c 'import sys; assert sys.version_info >= (3, 10)'
[[ ! -e "$ERP_CODEC_VENV" ]] || { echo 'Refusing to replace an existing codec runtime' >&2; exit 73; }
mkdir -p "$ERP_CODEC_CACHE" "$ERP_CODEC_TMP"
export PIP_CACHE_DIR="$ERP_CODEC_CACHE" TMPDIR="$ERP_CODEC_TMP"
"$ERP_CODEC_BOOTSTRAP_PYTHON" -m venv "$ERP_CODEC_VENV"
"$ERP_CODEC_VENV/bin/python" -m pip install --only-binary=:all: -r "$SCRIPT_ROOT/image-codecs-requirements.txt"
"$ERP_CODEC_VENV/bin/python" -I -c 'from PIL import features; import pillow_heif; assert features.check("webp"); assert pillow_heif.libheif_info()["HEIF"]; print(pillow_heif.libheif_info())'
printf 'ERP_IMAGE_PYTHON=%s/bin/python\n' "$ERP_CODEC_VENV"
