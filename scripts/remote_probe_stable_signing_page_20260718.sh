#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Production-engine QA for a proposed DOCX-only fix. The fix and all synthetic
# data substitutions are applied to /tmp copies; repository and production
# template files are never modified.

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
WORK_DIR="$(mktemp -d /tmp/stable-signing-page-probe.XXXXXX)"

cleanup() {
  case "$WORK_DIR" in
    /tmp/stable-signing-page-probe.*)
      rm -rf -- "$WORK_DIR"
      echo "CLEANUP_OK path_pattern=/tmp/stable-signing-page-probe.XXXXXX"
      ;;
    *) echo "CLEANUP_REFUSED work_dir=$WORK_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

for tool in libreoffice pdfinfo pdftotext pdftoppm python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "REQUIRED_TOOL_MISSING tool=$tool" >&2
    exit 10
  }
done

mkdir -m 700 "$WORK_DIR/input" "$WORK_DIR/output" "$WORK_DIR/home"

templates=(
  "09_ONBOARD_LABOR_CONTRACT.docx"
  "13_ONBOARD_SERVICE_CONTRACT.docx"
  "14_ONBOARD_SERVICE_RECEIPT.docx"
)

for name in "${templates[@]}"; do
  cp -- "$SOURCE_DIR/$name" "$WORK_DIR/input/$name"
done

python3 - "$WORK_DIR/input" "${templates[@]}" <<'PY'
import re
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
final_heading = {
    "09_ONBOARD_LABOR_CONTRACT.docx": "第十六条 附则",
    "13_ONBOARD_SERVICE_CONTRACT.docx": "第十七条 附则",
}
replacements = {
    "${companyName}": "测试企业名称有限公司北京业务管理分公司",
    "${employeeName}": "测试员工甲",
    "${employeeIdCard}": "110101199001010011",
    "${employeePhone}": "13800000000",
    "${employeeAddress}": (
        "北京市朝阳区测试街道测试社区一百二十三号测试花园十八栋"
        "二单元一千零一室转交测试员工本人签收"
    ),
    "${postName}": "高级茶艺服务专员",
    "${baseSalary}": "8000.00",
    "${postSalary}": "1200.00",
    "${fieldAllowance}": "500.00",
    "${performanceSalary}": "1000.00",
    "${salaryTotal}": "10700.00",
    "${insuranceType}": "雇主责任险",
    "${contractStartDate}": "2026年07月18日",
    "${contractEndDate}": "2029年07月17日",
    "${probationStartDate}": "2026年07月18日",
    "${probationEndDate}": "2026年10月17日",
    "${signDate}": "2026年07月18日",
    "${servicePersonType}": "退休返聘人员",
}
# Exercise the actual UI/database upper bound for the most layout-sensitive
# field, plus conservative long values for other free-text identity fields.
replacements["${employeeAddress}"] = (
    "北京市朝阳区测试街道测试社区测试花园测试楼栋测试单元"
    "测试房间转交测试员工本人签收"
    * 5
)[:100]
replacements["${companyName}"] = ("测试企业管理有限公司北京业务分公司" * 5)[:50]
replacements["${employeeName}"] = ("测试员工" * 8)[:10]
replacements["${postName}"] = ("高级茶艺服务专员" * 6)[:20]
print(
    "SYNTHETIC_LIMITS|"
    f"address={len(replacements['${employeeAddress}'])}|"
    f"company={len(replacements['${companyName}'])}|"
    f"employee={len(replacements['${employeeName}'])}|"
    f"post={len(replacements['${postName}'])}"
)

paragraph_re = re.compile(rb"<w:p(?:\s[^>]*)?>.*?</w:p>")
page_break_re = re.compile(rb"<w:pageBreakBefore(?:\s[^>]*)?\s*/>")

def add_active_page_break(data: bytes, marker: str) -> bytes:
    marker_bytes = marker.encode("utf-8")
    matches = [match for match in paragraph_re.finditer(data) if marker_bytes in match.group(0)]
    if len(matches) != 1:
        raise SystemExit(f"FINAL_HEADING_MATCH_COUNT marker={marker} count={len(matches)}")
    match = matches[0]
    paragraph = page_break_re.sub(b"", match.group(0))
    if b"<w:pPr>" in paragraph:
        paragraph = paragraph.replace(
            b"</w:pPr>", b"<w:pageBreakBefore/></w:pPr>", 1
        )
    else:
        open_end = paragraph.find(b">") + 1
        paragraph = (
            paragraph[:open_end]
            + b"<w:pPr><w:pageBreakBefore/></w:pPr>"
            + paragraph[open_end:]
        )
    return data[:match.start()] + paragraph + data[match.end():]

def write_variant(source: Path, target: Path, *, stable: bool, long_fields: bool) -> None:
    with zipfile.ZipFile(source, "r") as src, zipfile.ZipFile(target, "w") as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == "word/document.xml":
                if stable:
                    data = add_active_page_break(data, final_heading[source.name])
                if long_fields:
                    text = data.decode("utf-8")
                    for old, new in replacements.items():
                        text = text.replace(old, new)
                    data = text.encode("utf-8")
            dst.writestr(info, data)

for filename in sys.argv[2:]:
    source = root / filename
    if filename in final_heading:
        for long_fields in (False, True):
            suffix = "stable-long" if long_fields else "stable-raw"
            write_variant(
                source,
                root / f"{source.stem}-{suffix}.docx",
                stable=True,
                long_fields=long_fields,
            )
    else:
        # The receipt has no company-seal placement. Probe its current raw and
        # fully substituted layouts to verify its final page is substantive.
        for long_fields in (False, True):
            suffix = "receipt-long" if long_fields else "receipt-raw"
            write_variant(
                source,
                root / f"{source.stem}-{suffix}.docx",
                stable=False,
                long_fields=long_fields,
            )
PY

variants=(
  "09_ONBOARD_LABOR_CONTRACT-stable-raw.docx"
  "09_ONBOARD_LABOR_CONTRACT-stable-long.docx"
  "13_ONBOARD_SERVICE_CONTRACT-stable-raw.docx"
  "13_ONBOARD_SERVICE_CONTRACT-stable-long.docx"
  "14_ONBOARD_SERVICE_RECEIPT-receipt-raw.docx"
  "14_ONBOARD_SERVICE_RECEIPT-receipt-long.docx"
)

for variant in "${variants[@]}"; do
  profile="$WORK_DIR/profile-${variant%.docx}"
  mkdir -m 700 "$profile"
  HOME="$WORK_DIR/home" libreoffice --headless \
    "-env:UserInstallation=file://$profile" \
    --convert-to pdf --outdir "$WORK_DIR/output" "$WORK_DIR/input/$variant" \
    >/dev/null
  pdf="$WORK_DIR/output/${variant%.docx}.pdf"
  [[ -s "$pdf" ]] || {
    echo "PDF_RENDER_FAILED variant=$variant" >&2
    exit 20
  }
  pages="$(pdfinfo "$pdf" | awk -F: '/^Pages:/ {gsub(/[[:space:]]/, "", $2); print $2}')"
  echo "VARIANT_RENDER|$variant|pages=$pages"
  pdftotext -bbox-layout "$pdf" "$WORK_DIR/output/${variant%.docx}.html"
  pdftotext -enc UTF-8 -layout -f "$pages" -l "$pages" \
    "$pdf" "$WORK_DIR/output/${variant%.docx}-tail.txt"
  pdftoppm -f "$pages" -l "$pages" -r 72 "$pdf" \
    "$WORK_DIR/output/${variant%.docx}-tail" >/dev/null 2>&1
done

python3 - "$WORK_DIR/output" "${variants[@]}" <<'PY'
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

root_dir = Path(sys.argv[1])
markers = (
    "本合同自双方签字或盖章之日起生效",
    "本协议自双方签字或盖章之日起生效",
    "甲方（盖章）",
    "乙方姓名（系统预填）",
    "授权代表（签字）",
    "甲方代表签字",
    "乙方实际签署时间以签约系统记录为准",
)

def number(node, name):
    return float(node.attrib[name])

def read_token(handle):
    value = bytearray()
    while True:
        byte = handle.read(1)
        if not byte:
            raise ValueError("unexpected EOF")
        if byte == b"#":
            handle.readline()
            continue
        if not byte.isspace():
            value.extend(byte)
            break
    while True:
        byte = handle.read(1)
        if not byte or byte.isspace():
            return bytes(value)
        value.extend(byte)

for variant in sys.argv[2:]:
    stem = Path(variant).stem
    xml_path = root_dir / f"{stem}.html"
    root = ET.parse(xml_path).getroot()
    pages = [node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "page"]
    for page_number, page in enumerate(pages, 1):
        height = number(page, "height")
        for line in (node for node in page.iter() if node.tag.rsplit("}", 1)[-1] == "line"):
            words = [node for node in line.iter() if node.tag.rsplit("}", 1)[-1] == "word"]
            compact = re.sub(r"\s+", "", "".join(
                "".join(word.itertext()) for word in words
            ))
            if not any(marker in compact for marker in markers):
                continue
            print(
                f"STABLE_MARKER|{variant}|page={page_number}/{len(pages)}|"
                f"x={number(line, 'xMin'):.2f}..{number(line, 'xMax'):.2f}|"
                f"pdf_y={height - number(line, 'yMax'):.2f}.."
                f"{height - number(line, 'yMin'):.2f}|text={compact}"
            )

    tail_text = (root_dir / f"{stem}-tail.txt").read_text(
        encoding="utf-8", errors="replace"
    )
    nonspace = len(re.sub(r"\s+", "", tail_text))
    nonempty_lines = sum(1 for line in tail_text.splitlines() if line.strip())
    raster_candidates = sorted(root_dir.glob(f"{stem}-tail-*.ppm"))
    if len(raster_candidates) != 1:
        raise SystemExit(f"TAIL_RASTER_COUNT variant={variant} count={len(raster_candidates)}")
    with raster_candidates[0].open("rb") as handle:
        magic = read_token(handle)
        width = int(read_token(handle))
        height = int(read_token(handle))
        maximum = int(read_token(handle))
        pixels = handle.read()
    if magic != b"P6" or maximum != 255 or len(pixels) != width * height * 3:
        raise SystemExit(f"TAIL_RASTER_INVALID variant={variant}")
    ink = sum(
        1 for offset in range(0, len(pixels), 3)
        if min(pixels[offset:offset + 3]) < 245
    )
    ink_pct = ink * 100.0 / (width * height)
    assessment = "CONTENTFUL" if nonspace >= 40 and ink_pct >= 0.20 else "NEAR_BLANK"
    print(
        f"TAIL_PAGE|{variant}|page={len(pages)}|nonspace={nonspace}|"
        f"nonempty_lines={nonempty_lines}|ink_pct={ink_pct:.4f}|assessment={assessment}"
    )
PY

echo "NO_PRODUCTION_MUTATION source_templates_copied=yes database_access=no api_access=no"
echo "STABLE_SIGNING_PAGE_PROBE_OK variants=${#variants[@]}"
