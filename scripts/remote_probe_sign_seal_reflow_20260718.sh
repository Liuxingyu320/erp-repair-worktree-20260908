#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Read-only worst-case reflow probe for fixed company-seal coordinates. All
# substitutions are synthetic and are applied only to /tmp DOCX copies.

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
WORK_DIR="$(mktemp -d /tmp/sign-seal-reflow-probe.XXXXXX)"

cleanup() {
  case "$WORK_DIR" in
    /tmp/sign-seal-reflow-probe.*)
      rm -rf -- "$WORK_DIR"
      echo "CLEANUP_OK path_pattern=/tmp/sign-seal-reflow-probe.XXXXXX"
      ;;
    *) echo "CLEANUP_REFUSED work_dir=$WORK_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

for tool in libreoffice pdftotext pdfinfo python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "REQUIRED_TOOL_MISSING tool=$tool" >&2
    exit 10
  }
done

mkdir -m 700 "$WORK_DIR/input" "$WORK_DIR/output" "$WORK_DIR/home"

templates=(
  "09_ONBOARD_LABOR_CONTRACT.docx"
  "13_ONBOARD_SERVICE_CONTRACT.docx"
)

for name in "${templates[@]}"; do
  cp -- "$SOURCE_DIR/$name" "$WORK_DIR/input/$name"
done

python3 - "$WORK_DIR/input" "${templates[@]}" <<'PY'
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
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
    "${contractStartDate}": "2026年07月18日",
    "${contractEndDate}": "2029年07月17日",
    "${probationStartDate}": "2026年07月18日",
    "${probationEndDate}": "2026年10月17日",
    "${signDate}": "2026年07月18日",
    "${servicePersonType}": "退休返聘人员",
}

for filename in sys.argv[2:]:
    source = root / filename
    target = root / f"{source.stem}-synthetic-long-fields.docx"
    counts = {key: 0 for key in replacements}
    with zipfile.ZipFile(source, "r") as src, zipfile.ZipFile(target, "w") as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename.startswith("word/") and info.filename.endswith(".xml"):
                text = data.decode("utf-8")
                for old, new in replacements.items():
                    count = text.count(old)
                    if count:
                        counts[old] += count
                        text = text.replace(old, new)
                data = text.encode("utf-8")
            dst.writestr(info, data)
    unresolved = [key for key, count in counts.items() if count == 0 and key not in {
        "${probationStartDate}", "${probationEndDate}", "${servicePersonType}"
    }]
    if unresolved:
        raise SystemExit(f"UNRESOLVED_REQUIRED_PLACEHOLDERS file={filename} fields={unresolved}")
    print(
        f"SYNTHETIC_DOCX|{filename}|address_chars={len(replacements['${employeeAddress}'])}|"
        f"company_chars={len(replacements['${companyName}'])}"
    )
PY

for name in "${templates[@]}"; do
  variant="${name%.docx}-synthetic-long-fields.docx"
  profile="$WORK_DIR/profile-${name%.docx}"
  mkdir -m 700 "$profile"
  HOME="$WORK_DIR/home" libreoffice --headless \
    "-env:UserInstallation=file://$profile" \
    --convert-to pdf --outdir "$WORK_DIR/output" "$WORK_DIR/input/$variant" \
    >/dev/null
  pdf="$WORK_DIR/output/${variant%.docx}.pdf"
  [[ -s "$pdf" ]] || {
    echo "PDF_RENDER_FAILED name=$name" >&2
    exit 20
  }
  pages="$(pdfinfo "$pdf" | awk -F: '/^Pages:/ {gsub(/[[:space:]]/, "", $2); print $2}')"
  echo "SYNTHETIC_RENDER|$name|pages=$pages"
  pdftotext -bbox-layout "$pdf" "$WORK_DIR/output/${name%.docx}.html"
done

python3 - "$WORK_DIR/output" "${templates[@]}" <<'PY'
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
    "乙方地址",
)

def number(node, name):
    return float(node.attrib[name])

for filename in sys.argv[2:]:
    path = root_dir / (Path(filename).stem + ".html")
    root = ET.parse(path).getroot()
    pages = [node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "page"]
    matches = 0
    for page_number, page in enumerate(pages, 1):
        height = number(page, "height")
        for line in (node for node in page.iter() if node.tag.rsplit("}", 1)[-1] == "line"):
            words = [node for node in line.iter() if node.tag.rsplit("}", 1)[-1] == "word"]
            text = "".join("".join(word.itertext()) for word in words)
            compact = re.sub(r"\s+", "", text)
            if not any(marker in compact for marker in markers):
                continue
            matches += 1
            print(
                f"REFLOW_MARKER|{filename}|page={page_number}/{len(pages)}|"
                f"x={number(line, 'xMin'):.2f}..{number(line, 'xMax'):.2f}|"
                f"pdf_y={height - number(line, 'yMax'):.2f}.."
                f"{height - number(line, 'yMin'):.2f}|text={compact}"
            )
    if matches < 4:
        raise SystemExit(f"EXPECTED_REFLOW_MARKERS_MISSING file={filename} matches={matches}")
PY

echo "NO_PRODUCTION_MUTATION synthetic_data=yes source_templates_copied=yes database_access=no api_access=no"
echo "SIGN_SEAL_REFLOW_PROBE_OK templates=${#templates[@]}"
