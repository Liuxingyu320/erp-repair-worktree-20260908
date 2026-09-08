#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
WORK_DIR="$(mktemp -d /tmp/sign-template-marker-locate.XXXXXX)"
cleanup() {
  case "$WORK_DIR" in
    /tmp/sign-template-marker-locate.*) rm -rf -- "$WORK_DIR" ;;
    *) echo "CLEANUP_REFUSED work_dir=$WORK_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

templates=(
  "09_ONBOARD_LABOR_CONTRACT.docx"
  "13_ONBOARD_SERVICE_CONTRACT.docx"
  "14_ONBOARD_SERVICE_RECEIPT.docx"
)

mkdir -m 700 "$WORK_DIR/input" "$WORK_DIR/output" "$WORK_DIR/home"
for name in "${templates[@]}"; do
  cp -- "$SOURCE_DIR/$name" "$WORK_DIR/input/$name"
  profile="$WORK_DIR/profile-${name%.docx}"
  mkdir -m 700 "$profile"
  HOME="$WORK_DIR/home" libreoffice --headless \
    "-env:UserInstallation=file://$profile" \
    --convert-to pdf --outdir "$WORK_DIR/output" "$WORK_DIR/input/$name" \
    >/dev/null
  pdf="$WORK_DIR/output/${name%.docx}.pdf"
  [[ -s "$pdf" ]]
  pdftotext -bbox-layout "$pdf" "$WORK_DIR/output/${name%.docx}.html"
done

python3 - "$WORK_DIR/output" "${templates[@]}" <<'PY'
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

root_dir = Path(sys.argv[1])
markers = (
    "甲方", "盖章", "乙方", "签字", "签名", "签收人", "劳务报酬",
)

def number(node, name):
    return float(node.attrib[name])

for filename in sys.argv[2:]:
    path = root_dir / (Path(filename).stem + ".html")
    root = ET.parse(path).getroot()
    pages = [node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "page"]
    for page_number, page in enumerate(pages, 1):
        height = number(page, "height")
        width = number(page, "width")
        for line in (node for node in page.iter()
                     if node.tag.rsplit("}", 1)[-1] == "line"):
            words = [node for node in line.iter()
                     if node.tag.rsplit("}", 1)[-1] == "word"]
            text = "".join("".join(word.itertext()) for word in words)
            compact = re.sub(r"\s+", "", text)
            if not compact or not any(marker in compact for marker in markers):
                continue
            x_min = number(line, "xMin")
            x_max = number(line, "xMax")
            top_y_min = number(line, "yMin")
            top_y_max = number(line, "yMax")
            print(
                f"MARKER|{filename}|page={page_number}/{len(pages)}|"
                f"page_size={width:.2f}x{height:.2f}|"
                f"x={x_min:.2f}..{x_max:.2f}|"
                f"top_y={top_y_min:.2f}..{top_y_max:.2f}|"
                f"pdf_y={height - top_y_max:.2f}..{height - top_y_min:.2f}|"
                f"text={compact}"
            )
PY

echo "SIGN_TEMPLATE_MARKER_LOCATE_OK"
