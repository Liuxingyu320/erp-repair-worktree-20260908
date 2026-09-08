#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Read-only geometry QA for company-seal placement. This script copies the
# production DOCX files into /tmp, renders them there, and draws a synthetic
# red test ring on rasterized target pages. It never reads a seal asset and
# never touches the database, application API, or source templates.

SOURCE_DIR="/opt/erp-new-data/uploadPath/sign-template/onboard-20260714-v3-draft"
WORK_DIR="$(mktemp -d /tmp/sign-seal-coordinate-preview.XXXXXX)"

cleanup() {
  case "$WORK_DIR" in
    /tmp/sign-seal-coordinate-preview.*)
      rm -rf -- "$WORK_DIR"
      echo "CLEANUP_OK path_pattern=/tmp/sign-seal-coordinate-preview.XXXXXX"
      ;;
    *) echo "CLEANUP_REFUSED work_dir=$WORK_DIR" >&2 ;;
  esac
}
trap cleanup EXIT

for tool in libreoffice pdfinfo pdftoppm pdftotext python3 sha256sum; do
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
  source_file="$SOURCE_DIR/$name"
  [[ -f "$source_file" && -r "$source_file" ]] || {
    echo "SOURCE_TEMPLATE_MISSING name=$name" >&2
    exit 20
  }
  cp -- "$source_file" "$WORK_DIR/input/$name"
  profile="$WORK_DIR/profile-${name%.docx}"
  mkdir -m 700 "$profile"
  HOME="$WORK_DIR/home" libreoffice --headless \
    "-env:UserInstallation=file://$profile" \
    --convert-to pdf --outdir "$WORK_DIR/output" "$WORK_DIR/input/$name" \
    >/dev/null
  pdf="$WORK_DIR/output/${name%.docx}.pdf"
  [[ -s "$pdf" ]] || {
    echo "PDF_RENDER_FAILED name=$name" >&2
    exit 21
  }
  echo "RENDERED|$name|sha256=$(sha256sum "$pdf" | awk '{print $1}')"
done

# Candidate rectangles are intentionally conservative. They cover the blank
# company-seal line while staying inside the left signature column. All PDF
# coordinates below use the bottom-left origin and point units, matching
# PDFBox PDPageContentStream.drawImage().
#
# Fields:
#   file page x y width height
#   right_text_x
#   lower_protected_y_max upper_protected_y_min
cases=(
  "09_ONBOARD_LABOR_CONTRACT.docx|12|177|348|78|78|278.10|344.27|468.23"
  "13_ONBOARD_SERVICE_CONTRACT.docx|9|177|371|78|78|278.10|367.87|451.93"
)

for spec in "${cases[@]}"; do
  IFS='|' read -r name page x y width height right_text_x lower_y_max upper_y_min <<<"$spec"
  stem="${name%.docx}"
  pdf="$WORK_DIR/output/$stem.pdf"
  pages="$(pdfinfo "$pdf" | awk -F: '/^Pages:/ {gsub(/[[:space:]]/, "", $2); print $2}')"
  [[ "$pages" =~ ^[1-9][0-9]*$ && "$page" -le "$pages" ]] || {
    echo "TARGET_PAGE_MISSING name=$name target=$page pages=$pages" >&2
    exit 30
  }

  page_size="$(pdfinfo -f "$page" -l "$page" "$pdf" \
    | awk -F: -v p="$page" \
      '$1 ~ ("Page[[:space:]]+" p "[[:space:]]+size") {gsub(/^[[:space:]]+/, "", $2); print $2}')"
  echo "PAGE_INFO|$name|page=$page/$pages|size=${page_size:-UNKNOWN}"

  prefix="$WORK_DIR/output/$stem-target"
  pdftoppm -f "$page" -l "$page" -r 216 "$pdf" "$prefix" >/dev/null 2>&1
  raster="$(find "$WORK_DIR/output" -maxdepth 1 -type f \
    -name "$stem-target-*.ppm" -print -quit)"
  [[ -n "$raster" && -s "$raster" ]] || {
    echo "RASTER_RENDER_FAILED name=$name page=$page" >&2
    exit 31
  }

  python3 - "$name" "$page" "$x" "$y" "$width" "$height" \
    "$right_text_x" "$lower_y_max" "$upper_y_min" "$raster" <<'PY'
import hashlib
import math
import sys
from pathlib import Path

(
    name,
    page_raw,
    x_raw,
    y_raw,
    width_raw,
    height_raw,
    right_text_x_raw,
    lower_y_max_raw,
    upper_y_min_raw,
    raster_raw,
) = sys.argv[1:]

page = int(page_raw)
x, y, width, height = map(float, (x_raw, y_raw, width_raw, height_raw))
right_text_x = float(right_text_x_raw)
lower_y_max = float(lower_y_max_raw)
upper_y_min = float(upper_y_min_raw)
raster = Path(raster_raw)

def token(handle):
    value = bytearray()
    while True:
        byte = handle.read(1)
        if not byte:
            raise ValueError("unexpected EOF in PPM header")
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

with raster.open("rb") as handle:
    magic = token(handle)
    pixel_width = int(token(handle))
    pixel_height = int(token(handle))
    maximum = int(token(handle))
    pixels = bytearray(handle.read())

if magic != b"P6" or maximum != 255 or len(pixels) != pixel_width * pixel_height * 3:
    raise SystemExit(f"UNSUPPORTED_RASTER name={name}")

# A4 production render is 595.30 x 841.89 pt. Derive independent X/Y scales
# from the actual raster to retain sub-point rounding accuracy.
page_width_pt = 595.30
page_height_pt = 841.89
scale_x = pixel_width / page_width_pt
scale_y = pixel_height / page_height_pt

left = int(round(x * scale_x))
right = int(round((x + width) * scale_x))
top = int(round((page_height_pt - (y + height)) * scale_y))
bottom = int(round((page_height_pt - y) * scale_y))

if not (0 <= left < right <= pixel_width and 0 <= top < bottom <= pixel_height):
    raise SystemExit(f"CANDIDATE_OUT_OF_RASTER name={name}")

original = bytes(pixels)
center_x = (left + right - 1) / 2.0
center_y = (top + bottom - 1) / 2.0
radius_x = max((right - left - 1) / 2.0, 1.0)
radius_y = max((bottom - top - 1) / 2.0, 1.0)
ring_pixels = 0
overwritten_dark_pixels = 0

for py in range(top, bottom):
    for px in range(left, right):
        nx = (px - center_x) / radius_x
        ny = (py - center_y) / radius_y
        distance = math.sqrt(nx * nx + ny * ny)
        # Double-ring synthetic seal plus a small center cross. No real seal
        # artwork is used, so the preview cannot be mistaken for a signed PDF.
        ring = 0.915 <= distance <= 1.0 or 0.78 <= distance <= 0.81
        cross = (abs(px - center_x) <= 1.5 and abs(ny) <= 0.30) or (
            abs(py - center_y) <= 1.5 and abs(nx) <= 0.30
        )
        if not (ring or cross):
            continue
        offset = (py * pixel_width + px) * 3
        if min(original[offset:offset + 3]) < 180:
            overwritten_dark_pixels += 1
        # Opaque red makes the exact synthetic overlay footprint measurable.
        pixels[offset:offset + 3] = bytes((220, 0, 0))
        ring_pixels += 1

preview = raster.with_name(raster.stem + "-synthetic-seal.ppm")
with preview.open("wb") as handle:
    handle.write(f"P6\n{pixel_width} {pixel_height}\n255\n".encode("ascii"))
    handle.write(pixels)

right_clearance = right_text_x - (x + width)
lower_clearance = y - lower_y_max
upper_clearance = upper_y_min - (y + height)
safe = min(right_clearance, lower_clearance, upper_clearance) > 0

print(
    f"PLACEMENT|{name}|page={page}|x={x:.2f}|y={y:.2f}|"
    f"width={width:.2f}|height={height:.2f}|origin=BOTTOM_LEFT|unit=POINT"
)
print(
    f"CLEARANCE|{name}|right_employee_text={right_clearance:.2f}|"
    f"lower_authorized_row={lower_clearance:.2f}|"
    f"upper_clause={upper_clearance:.2f}|assessment={'PASS' if safe else 'FAIL'}"
)
print(
    f"SYNTHETIC_OVERLAY|{name}|raster={pixel_width}x{pixel_height}|"
    f"rect_px={left},{top},{right},{bottom}|ring_pixels={ring_pixels}|"
    f"ring_over_original_dark_pixels={overwritten_dark_pixels}|"
    f"preview_sha256={hashlib.sha256(preview.read_bytes()).hexdigest()}"
)
PY
done

echo "NO_PRODUCTION_MUTATION source_templates_copied=yes database_access=no api_access=no real_seal_read=no"
echo "CLEANUP_PENDING path_pattern=/tmp/sign-seal-coordinate-preview.XXXXXX"
echo "SIGN_SEAL_COORDINATE_PREVIEW_OK cases=${#cases[@]}"
