#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
WORK_DIR="$(mktemp -d /tmp/sign-template-render-verify.XXXXXX)"

cleanup() {
  case "$WORK_DIR" in
    /tmp/sign-template-render-verify.*)
      rm -rf -- "$WORK_DIR"
      ;;
    *)
      echo "CLEANUP_REFUSED unexpected_work_dir=$WORK_DIR" >&2
      ;;
  esac
}
trap cleanup EXIT

mkdir -m 700 "$WORK_DIR/input" "$WORK_DIR/output" "$WORK_DIR/home"

templates=(
  "09_ONBOARD_LABOR_CONTRACT.docx"
  "13_ONBOARD_SERVICE_CONTRACT.docx"
  "14_ONBOARD_SERVICE_RECEIPT.docx"
)

echo "ENVIRONMENT_BEGIN"
echo "host=$(hostname)"
echo "work_dir_pattern=/tmp/sign-template-render-verify.XXXXXX"
echo "source_dir=$SOURCE_DIR"
echo "fontconfig_version=$(fc-match --version 2>&1 | head -n 1 || true)"

for tool in python3 sha256sum fc-match libreoffice soffice pdfinfo pdffonts pdftotext pdftoppm; do
  tool_path="$(command -v "$tool" 2>/dev/null || true)"
  echo "tool=$tool path=${tool_path:-MISSING}"
done

LO_BIN="$(command -v libreoffice 2>/dev/null || command -v soffice 2>/dev/null || true)"
[[ -n "$LO_BIN" ]] || { echo "LIBREOFFICE_MISSING" >&2; exit 10; }

for tool in python3 sha256sum fc-match pdfinfo pdffonts pdftotext pdftoppm; do
  command -v "$tool" >/dev/null 2>&1 || { echo "REQUIRED_TOOL_MISSING tool=$tool" >&2; exit 11; }
done

echo "libreoffice_version=$($LO_BIN --version 2>&1 | head -n 1)"
echo "noto_cjk_match=$(fc-match -f 'family=%{family};style=%{style};file=%{file}\n' 'Noto Sans CJK SC' | head -n 1)"
echo "noto_cjk_regular_match=$(fc-match -f 'family=%{family};style=%{style};file=%{file}\n' 'Noto Sans CJK SC:style=Regular' | head -n 1)"
echo "noto_cjk_bold_match=$(fc-match -f 'family=%{family};style=%{style};file=%{file}\n' 'Noto Sans CJK SC:style=Bold' | head -n 1)"
echo "ENVIRONMENT_END"

for name in "${templates[@]}"; do
  source_file="$SOURCE_DIR/$name"
  [[ -f "$source_file" && -r "$source_file" ]] || {
    echo "SOURCE_TEMPLATE_MISSING name=$name path=$source_file" >&2
    exit 20
  }
  cp -- "$source_file" "$WORK_DIR/input/$name"
  echo "SOURCE|$name|bytes=$(stat -c '%s' "$source_file")|sha256=$(sha256sum "$source_file" | awk '{print $1}')"
done

python3 - "$WORK_DIR/input" "${templates[@]}" <<'PY'
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

root = Path(sys.argv[1])
cjk_re = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")

for filename in sys.argv[2:]:
    path = root / filename
    chunks = []
    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if not member.startswith("word/") or not member.endswith(".xml"):
                continue
            if not (
                member == "word/document.xml"
                or member.startswith("word/header")
                or member.startswith("word/footer")
            ):
                continue
            try:
                chunks.extend(ElementTree.fromstring(archive.read(member)).itertext())
            except ElementTree.ParseError:
                continue
    text = "".join(chunks)
    cjk = cjk_re.findall(text)
    nonspace = re.sub(r"\s+", "", text)
    print(
        f"DOCX_TEXT|{filename}|nonspace={len(nonspace)}|"
        f"cjk={len(cjk)}|unique_cjk={len(set(cjk))}"
    )
PY

for name in "${templates[@]}"; do
  stem="${name%.docx}"
  profile="$WORK_DIR/profile-$stem"
  mkdir -m 700 "$profile"
  echo "CONVERT_BEGIN|$name"
  HOME="$WORK_DIR/home" "$LO_BIN" --headless \
    "-env:UserInstallation=file://$profile" \
    --convert-to pdf --outdir "$WORK_DIR/output" "$WORK_DIR/input/$name" \
    2>&1 | sed "s/^/LIBREOFFICE|$name|/"
  pdf="$WORK_DIR/output/$stem.pdf"
  [[ -s "$pdf" ]] || { echo "PDF_MISSING_OR_EMPTY name=$name" >&2; exit 30; }
  echo "CONVERT_END|$name|pdf_bytes=$(stat -c '%s' "$pdf")|pdf_sha256=$(sha256sum "$pdf" | awk '{print $1}')"
done

for name in "${templates[@]}"; do
  stem="${name%.docx}"
  pdf="$WORK_DIR/output/$stem.pdf"
  text_file="$WORK_DIR/output/$stem.txt"
  pages="$(pdfinfo "$pdf" | awk -F: '/^Pages:/ {gsub(/[[:space:]]/, "", $2); print $2}')"
  [[ "$pages" =~ ^[1-9][0-9]*$ ]] || { echo "INVALID_PAGE_COUNT name=$name pages=$pages" >&2; exit 40; }

  echo "PDF_INFO_BEGIN|$name"
  pdfinfo "$pdf" | awk -F: '/^(Pages|Page size|PDF version|Tagged|Encrypted):/ {key=$1; sub(/^[^:]*:[[:space:]]*/, "", $0); gsub(/[[:space:]]+$/, "", $0); print "PDF_INFO|" key "=" $0}'
  echo "PDF_INFO_END|$name"

  echo "PDF_FONTS_BEGIN|$name"
  pdffonts "$pdf" | awk -v file="$name" 'NR == 1 || NR == 2 || NR > 2 {print "PDF_FONT|" file "|" $0}'
  echo "PDF_FONTS_END|$name"

  pdftotext -enc UTF-8 -layout "$pdf" "$text_file"
  python3 - "$name" "$text_file" <<'PY'
import re
import sys
from pathlib import Path

name, filename = sys.argv[1:]
text = Path(filename).read_text(encoding="utf-8", errors="replace")
cjk = re.findall(r"[\u3400-\u4dbf\u4e00-\u9fff]", text)
nonspace = re.sub(r"\s+", "", text)
replacement = text.count("\ufffd")
print(
    f"PDF_TEXT|{name}|nonspace={len(nonspace)}|cjk={len(cjk)}|"
    f"unique_cjk={len(set(cjk))}|replacement_chars={replacement}"
)
PY

  for ((page = 1; page <= pages; page++)); do
    page_text="$WORK_DIR/output/$stem-page-$page.txt"
    pdftotext -enc UTF-8 -layout -f "$page" -l "$page" "$pdf" "$page_text"
    python3 - "$name" "$page" "$page_text" <<'PY'
import re
import sys
from pathlib import Path

name, page, filename = sys.argv[1:]
text = Path(filename).read_text(encoding="utf-8", errors="replace")
cjk = re.findall(r"[\u3400-\u4dbf\u4e00-\u9fff]", text)
nonspace = re.sub(r"\s+", "", text)
nonempty_lines = sum(1 for line in text.splitlines() if line.strip())
print(
    f"PAGE_TEXT|{name}|page={page}|nonspace={len(nonspace)}|"
    f"cjk={len(cjk)}|unique_cjk={len(set(cjk))}|nonempty_lines={nonempty_lines}"
)
PY
  done

  raster_prefix="$WORK_DIR/output/$stem-raster"
  # PPM is pdftoppm's default output. Older Poppler versions (including the
  # production 20.11 build) do not accept a separate `-ppm` switch.
  pdftoppm -r 72 "$pdf" "$raster_prefix" >/dev/null 2>&1
  python3 - "$name" "$pages" "$raster_prefix" <<'PY'
import re
import statistics
import sys
from pathlib import Path

name, pages_raw, prefix_raw = sys.argv[1:]
pages = int(pages_raw)
prefix = Path(prefix_raw)

def read_token(handle):
    token = bytearray()
    while True:
        byte = handle.read(1)
        if not byte:
            raise ValueError("unexpected EOF in PPM header")
        if byte == b"#":
            handle.readline()
            continue
        if not byte.isspace():
            token.extend(byte)
            break
    while True:
        byte = handle.read(1)
        if not byte or byte.isspace():
            return bytes(token)
        token.extend(byte)

def ppm_metric(path):
    with path.open("rb") as handle:
        magic = read_token(handle)
        width = int(read_token(handle))
        height = int(read_token(handle))
        maximum = int(read_token(handle))
        data = handle.read()
    if magic != b"P6" or maximum != 255 or len(data) != width * height * 3:
        raise ValueError(f"unsupported PPM layout: {path}")
    ink = 0
    min_x, min_y = width, height
    max_x = max_y = -1
    for pixel_index in range(width * height):
        start = pixel_index * 3
        r, g, b = data[start:start + 3]
        if min(r, g, b) < 245:
            ink += 1
            x = pixel_index % width
            y = pixel_index // width
            min_x = min(min_x, x)
            max_x = max(max_x, x)
            min_y = min(min_y, y)
            max_y = max(max_y, y)
    density = ink * 100.0 / (width * height)
    bbox = "NONE" if ink == 0 else f"{min_x},{min_y},{max_x},{max_y}"
    return density, bbox

density_values = []
for page in range(1, pages + 1):
    candidates = [
        Path(f"{prefix}-{page}.ppm"),
        Path(f"{prefix}-{page:02d}.ppm"),
        Path(f"{prefix}-{page:03d}.ppm"),
    ]
    raster = next((path for path in candidates if path.exists()), None)
    if raster is None:
        matches = sorted(prefix.parent.glob(prefix.name + "-*.ppm"))
        if len(matches) == pages:
            raster = matches[page - 1]
        else:
            raise SystemExit(f"RASTER_PAGE_MISSING name={name} page={page}")
    density, bbox = ppm_metric(raster)
    density_values.append(density)
    print(f"PAGE_RASTER|{name}|page={page}|ink_pct={density:.4f}|ink_bbox={bbox}")

tail = density_values[-1]
baseline = statistics.median(density_values[:-1]) if pages > 1 else tail
relative = tail / baseline if baseline else 0.0
if tail < 0.20 or (pages > 1 and tail < 0.65 and relative < 0.20):
    assessment = "LIKELY_NEAR_BLANK"
elif pages > 1 and tail < 1.00 and relative < 0.45:
    assessment = "SPARSE_TAIL"
else:
    assessment = "CONTENTFUL_OR_UNCERTAIN"
print(
    f"TAIL_RASTER|{name}|page={pages}|ink_pct={tail:.4f}|"
    f"prior_median_ink_pct={baseline:.4f}|relative={relative:.4f}|assessment={assessment}"
)
PY
done

echo "CLEANUP_PENDING path_pattern=/tmp/sign-template-render-verify.XXXXXX"
echo "RENDER_VERIFY_OK templates=${#templates[@]}"
