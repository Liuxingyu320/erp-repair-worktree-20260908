#!/usr/bin/env python3
"""Stamp actual page/total footers onto a converted labor-contract PDF.

This is the render-QA counterpart of ``OaPdfPageNumberService``.  It uses the
same format, 9-point CJK font, centered crop-box geometry, and a baseline 36
points above the bottom edge.  It mutates only the explicitly supplied PDF.
"""

from __future__ import annotations

import argparse
import io
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Final

from pypdf import PdfReader, PdfWriter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


FONT_SIZE: Final[float] = 9.0
BASELINE_FROM_BOTTOM: Final[float] = 36.0
FONT_NAME: Final[str] = "ContractPageNumberCJK"
FONT_CANDIDATES: Final[tuple[str, ...]] = (
    "NotoSansCJKsc-Regular",
    "NotoSansSC-Regular",
    "SourceHanSansSC-Regular",
    "MicrosoftYaHei",
    "WenQuanYiZenHei",
    "STHeitiSC-Medium",
    "STHeitiSC-Light",
    "STHeiti",
    "Heiti SC",
    "SimSun",
    "ArialUnicodeMS",
)


def footer_text(page: int, pages: int) -> str:
    return f"第 {page} 页 共 {pages} 页"


def supports_font(path: Path, required: str) -> bool:
    try:
        font = TTFont(FONT_NAME, str(path), subfontIndex=0)
        cmap = font.face.charToGlyph
        if any(
            not cmap.get(ord(character), 0)
            for character in required
            if not character.isspace()
        ):
            return False
        pdfmetrics.registerFont(font)
        # ReportLab raises for an unusable font; checking the width also makes
        # sure the registered face can encode the required Chinese footer.
        return pdfmetrics.stringWidth(required, FONT_NAME, FONT_SIZE) > 0
    except Exception:
        return False


def resolve_font(required: str) -> Path:
    configured = os.environ.get("SIGN_PACKAGE_PDF_FONT_PATH", "").strip()
    if configured:
        path = Path(configured).expanduser().resolve()
        if not path.is_file() or not supports_font(path, required):
            raise RuntimeError("configured signing PDF font is missing or unusable")
        return path
    fc_match = shutil.which("fc-match")
    if fc_match:
        for candidate in FONT_CANDIDATES:
            completed = subprocess.run(
                [fc_match, "-f", "%{file}\\n", candidate],
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
            )
            first = completed.stdout.splitlines()[0].strip() if completed.stdout else ""
            if first:
                path = Path(first).resolve()
                if path.is_file() and supports_font(path, required):
                    return path
    raise RuntimeError("no usable CJK font found for labor-contract page numbers")


def stamp(path: Path) -> None:
    path = path.resolve()
    if not path.is_file():
        raise FileNotFoundError(path)
    reader = PdfReader(str(path))
    pages = len(reader.pages)
    if pages < 1:
        raise RuntimeError("PDF must contain at least one page")
    resolve_font(footer_text(pages, pages))
    writer = PdfWriter()
    for index, page in enumerate(reader.pages, start=1):
        if int(page.get("/Rotate", 0) or 0) % 360:
            raise RuntimeError("rotated labor-contract pages are not supported")
        box = page.cropbox
        lower_x = float(box.left)
        lower_y = float(box.bottom)
        width = float(box.width)
        height = float(box.height)
        footer = footer_text(index, pages)
        text_width = pdfmetrics.stringWidth(footer, FONT_NAME, FONT_SIZE)
        x = lower_x + max(0.0, (width - text_width) / 2.0)
        y = lower_y + BASELINE_FROM_BOTTOM
        overlay_bytes = io.BytesIO()
        overlay_canvas = canvas.Canvas(
            overlay_bytes,
            pagesize=(width, height),
            pageCompression=0,
            invariant=1,
        )
        overlay_canvas.setFont(FONT_NAME, FONT_SIZE)
        overlay_canvas.drawString(x - lower_x, y - lower_y, footer)
        overlay_canvas.save()
        overlay = PdfReader(io.BytesIO(overlay_bytes.getvalue())).pages[0]
        page.merge_page(overlay)
        writer.add_page(page)
    if reader.metadata:
        writer.add_metadata({
            str(key): str(value)
            for key, value in reader.metadata.items()
            if value is not None
        })
    with tempfile.NamedTemporaryFile(
        prefix=path.name + ".page-numbered-",
        suffix=".tmp",
        dir=path.parent,
        delete=False,
    ) as stream:
        temporary = Path(stream.name)
        writer.write(stream)
    os.replace(temporary, path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pdf", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        stamp(args.pdf)
    except Exception as exc:
        print(f"ERROR: {exc}")
        return 1
    print(args.pdf.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
