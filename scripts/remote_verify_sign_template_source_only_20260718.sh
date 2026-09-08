#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
templates=(
  "09_ONBOARD_LABOR_CONTRACT.docx"
  "13_ONBOARD_SERVICE_CONTRACT.docx"
  "14_ONBOARD_SERVICE_RECEIPT.docx"
)

echo "SOURCE_AUDIT_BEGIN"
for name in "${templates[@]}"; do
  path="$SOURCE_DIR/$name"
  [[ -f "$path" && -r "$path" ]] || { echo "SOURCE_MISSING|$name" >&2; exit 20; }
  echo "SOURCE|$name|bytes=$(stat -c '%s' "$path")|mtime=$(stat -c '%y' "$path")|sha256=$(sha256sum "$path" | awk '{print $1}')"
done

python3 - "$SOURCE_DIR" "${templates[@]}" <<'PY'
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

root = Path(sys.argv[1])
cjk_re = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
placeholder_re = re.compile(r"\$\{[^{}]+\}")

for filename in sys.argv[2:]:
    chunks = []
    with zipfile.ZipFile(root / filename) as archive:
        members = [
            member for member in archive.namelist()
            if member == "word/document.xml"
            or member.startswith("word/header") and member.endswith(".xml")
            or member.startswith("word/footer") and member.endswith(".xml")
        ]
        for member in members:
            chunks.extend(ElementTree.fromstring(archive.read(member)).itertext())
    text = "".join(chunks)
    cjk = cjk_re.findall(text)
    placeholders = sorted(set(placeholder_re.findall(text)))
    nonspace = re.sub(r"\s+", "", text)
    print(
        f"DOCX_TEXT|{filename}|nonspace={len(nonspace)}|"
        f"cjk={len(cjk)}|unique_cjk={len(set(cjk))}|placeholders={len(placeholders)}"
    )
PY

residue_count="$(find /tmp -mindepth 1 -maxdepth 1 -type d -name 'sign-template-render-verify.*' -print 2>/dev/null | wc -l | tr -d ' ')"
echo "TEMP_RESIDUE|pattern=/tmp/sign-template-render-verify.*|count=$residue_count"
[[ "$residue_count" == "0" ]] || { echo "TEMP_RESIDUE_DETECTED" >&2; exit 30; }
echo "SOURCE_AUDIT_OK templates=${#templates[@]}"
