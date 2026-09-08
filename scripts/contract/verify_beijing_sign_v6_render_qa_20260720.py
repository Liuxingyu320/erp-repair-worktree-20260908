#!/usr/bin/env python3
"""Build or verify portable render-QA evidence for the disabled v6 templates.

The write mode is local and read-only with respect to ERP services and databases.
It inspects the already rendered PDFs, writes a hash-pinned evidence document, and
links that document from the v6 manifest.  It never registers or publishes a
template.  Check mode needs no PDF tooling and revalidates the pinned files.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Final


REPO_ROOT: Final[Path] = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST: Final[Path] = (
    REPO_ROOT
    / "output/sign-release/20260720-v6/manifest-v6-inline-fixed-evidence.json"
)
DEFAULT_EVIDENCE: Final[Path] = (
    REPO_ROOT
    / "docs/releases/evidence/20260720-contract-signing-v6-render-qa.json"
)
DEFAULT_SOURCE_MANIFEST: Final[Path] = (
    REPO_ROOT / "output/contract-template-review/20260720-v6/candidate-manifest.json"
)
DEFAULT_SOURCE_DIR: Final[Path] = (
    REPO_ROOT / "output/contract-template-review/20260720-v6/candidates"
)
DEFAULT_TEMPLATE_DIR: Final[Path] = (
    REPO_ROOT / "output/sign-release/20260720-v6/templates"
)
DEFAULT_QA_DOCX_DIR: Final[Path] = (
    REPO_ROOT / "output/sign-release/20260720-v6/qa/docx"
)
DEFAULT_RENDER_DIR: Final[Path] = (
    REPO_ROOT / "output/sign-release/20260720-v6/qa/rendered"
)
EXPECTED_PDF_COUNT: Final[int] = 18
EXPECTED_PAGE_COUNT: Final[int] = 112
A4_WIDTH_POINTS: Final[float] = 595.28
A4_HEIGHT_POINTS: Final[float] = 841.89
A4_TOLERANCE_POINTS: Final[float] = 1.0
FORBIDDEN_TEXT: Final[tuple[str, ...]] = ("${", "待 HR 确认")
PAGE_FOOTER_TEMPLATE_TYPES: Final[tuple[str, ...]] = ("ONBOARD_LABOR_CONTRACT",)
PAGE_FOOTER_PATTERN: Final[re.Pattern[str]] = re.compile(
    r"第(\d+)页共(\d+)页"
)
PAGE_FOOTER_FONT_SIZE: Final[int] = 9
PAGE_FOOTER_BASELINE: Final[int] = 36


def validate_page_number_policy(manifest: dict[str, object]) -> None:
    expected = {
        "mode": "PDF_LAYER_DYNAMIC",
        "templateTypes": list(PAGE_FOOTER_TEMPLATE_TYPES),
        "format": "第 {page} 页 共 {pages} 页",
        "fontSizePoints": PAGE_FOOTER_FONT_SIZE,
        "baselinePoints": PAGE_FOOTER_BASELINE,
        "docxFooter": "RESERVED_EMPTY",
    }
    if manifest.get("pageNumberPolicy") != expected:
        raise RuntimeError("v6 page-number policy does not match runtime PDF stamping")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def portable_path(path: Path, base_dir: Path) -> str:
    return Path(os.path.relpath(path.resolve(), base_dir.resolve())).as_posix()


def resolve_portable(value: str, base_dir: Path) -> Path:
    candidate = Path(value)
    if candidate.is_absolute():
        raise RuntimeError(f"absolute path is not portable: {value}")
    return (base_dir / candidate).resolve()


def load_json(path: Path) -> dict[str, object]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"JSON object required: {path}")
    return payload


def write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def assert_file(path: Path, expected_hash: str, expected_size: int | None) -> None:
    if not path.is_file():
        raise FileNotFoundError(path)
    if expected_size is not None and path.stat().st_size != expected_size:
        raise RuntimeError(f"size mismatch: {path}")
    if sha256(path) != expected_hash:
        raise RuntimeError(f"hash mismatch: {path}")


def repair_manifest_paths(manifest_path: Path) -> dict[str, object]:
    """Replace stale absolute rebuild paths using immutable hashes and names."""
    payload = load_json(manifest_path)
    base_dir = manifest_path.parent
    source_manifest_hash = str(payload.get("sourceCandidateManifestSha256", ""))
    assert_file(DEFAULT_SOURCE_MANIFEST, source_manifest_hash, None)
    payload["pathBase"] = "manifest-directory"
    payload["sourceCandidateManifest"] = portable_path(
        DEFAULT_SOURCE_MANIFEST, base_dir
    )

    artifacts = payload.get("artifacts")
    if not isinstance(artifacts, list):
        raise RuntimeError("manifest artifacts must be a list")
    for item in artifacts:
        if not isinstance(item, dict):
            raise RuntimeError("manifest artifact must be an object")
        target = DEFAULT_TEMPLATE_DIR / Path(str(item["file"])).name
        source = DEFAULT_SOURCE_DIR / Path(str(item["source"])).name
        assert_file(target, str(item["sha256"]), int(item["size"]))
        assert_file(source, str(item["sourceSha256"]), None)
        item["file"] = portable_path(target, base_dir)
        item["source"] = portable_path(source, base_dir)

    variants = payload.get("qaVariants")
    if not isinstance(variants, list):
        raise RuntimeError("manifest qaVariants must be a list")
    for item in variants:
        if not isinstance(item, dict):
            raise RuntimeError("manifest QA variant must be an object")
        target = (
            DEFAULT_QA_DOCX_DIR
            / str(item["profile"])
            / Path(str(item["file"])).name
        )
        assert_file(target, str(item["sha256"]), int(item["size"]))
        item["file"] = portable_path(target, base_dir)

    write_json(manifest_path, payload)
    return payload


def validate_manifest_files(
    manifest_path: Path, payload: dict[str, object]
) -> list[dict[str, object]]:
    if payload.get("pathBase") != "manifest-directory":
        raise RuntimeError("v6 manifest pathBase must be manifest-directory")
    base_dir = manifest_path.parent
    source_manifest = resolve_portable(
        str(payload["sourceCandidateManifest"]), base_dir
    )
    assert_file(
        source_manifest, str(payload["sourceCandidateManifestSha256"]), None
    )

    artifacts = payload.get("artifacts")
    variants = payload.get("qaVariants")
    if not isinstance(artifacts, list) or len(artifacts) != 9:
        raise RuntimeError("v6 manifest must contain nine artifacts")
    if not isinstance(variants, list) or len(variants) != EXPECTED_PDF_COUNT:
        raise RuntimeError("v6 manifest must contain eighteen QA variants")
    for item in artifacts:
        target = resolve_portable(str(item["file"]), base_dir)
        source = resolve_portable(str(item["source"]), base_dir)
        assert_file(target, str(item["sha256"]), int(item["size"]))
        assert_file(source, str(item["sourceSha256"]), None)
    for item in variants:
        target = resolve_portable(str(item["file"]), base_dir)
        assert_file(target, str(item["sha256"]), int(item["size"]))
    return variants


def command_version(command: str) -> str:
    completed = subprocess.run(
        [command, "-v"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return completed.stdout.splitlines()[0].strip()


def pdf_page_sizes(pdfinfo: str, pdf: Path) -> list[tuple[float, float]]:
    summary = subprocess.run(
        [pdfinfo, str(pdf)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout
    match = re.search(r"^Pages:\s+(\d+)\s*$", summary, flags=re.MULTILINE)
    if not match:
        raise RuntimeError(f"pdfinfo did not report page count: {pdf}")
    page_count = int(match.group(1))
    details = subprocess.run(
        [pdfinfo, "-f", "1", "-l", str(page_count), "-box", str(pdf)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout
    sizes = {
        int(page): (float(width), float(height))
        for page, width, height in re.findall(
            r"^Page\s+(\d+)\s+size:\s+([0-9.]+)\s+x\s+([0-9.]+)\s+pts",
            details,
            flags=re.MULTILINE,
        )
    }
    if sorted(sizes) != list(range(1, page_count + 1)):
        raise RuntimeError(f"pdfinfo page-size coverage is incomplete: {pdf}")
    return [sizes[index] for index in range(1, page_count + 1)]


def extract_page_text(pdftotext: str, pdf: Path, page: int) -> str:
    return subprocess.run(
        [pdftotext, "-f", str(page), "-l", str(page), str(pdf), "-"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        errors="replace",
    ).stdout


def is_a4(size: tuple[float, float]) -> bool:
    width, height = size
    return (
        abs(width - A4_WIDTH_POINTS) <= A4_TOLERANCE_POINTS
        and abs(height - A4_HEIGHT_POINTS) <= A4_TOLERANCE_POINTS
    )


def build_evidence(
    manifest_path: Path,
    evidence_path: Path,
    render_dir: Path,
    pdfinfo: str,
    pdftotext: str,
) -> dict[str, object]:
    manifest = load_json(manifest_path)
    variants = validate_manifest_files(manifest_path, manifest)
    validate_page_number_policy(manifest)
    documents: list[dict[str, object]] = []
    total_pages = 0
    blank_page_count = 0
    non_a4_page_count = 0
    forbidden_hit_count = 0
    page_footer_error_count = 0

    for item in variants:
        profile = str(item["profile"])
        qa_docx = resolve_portable(str(item["file"]), manifest_path.parent)
        stem = qa_docx.stem
        pdf = render_dir / profile / stem / f"{stem}.pdf"
        if not pdf.is_file():
            raise FileNotFoundError(pdf)
        sizes = pdf_page_sizes(pdfinfo, pdf)
        blank_pages: list[int] = []
        non_a4_pages: list[int] = []
        forbidden_hits: list[dict[str, object]] = []
        page_footer_errors: list[dict[str, object]] = []
        for page, size in enumerate(sizes, start=1):
            text = extract_page_text(pdftotext, pdf, page)
            compact = re.sub(r"\s+", "", text)
            if not compact:
                blank_pages.append(page)
            if not is_a4(size):
                non_a4_pages.append(page)
            normalized_text = re.sub(r"\s+", " ", text)
            for token in FORBIDDEN_TEXT:
                if token in normalized_text or token.replace(" ", "") in compact:
                    forbidden_hits.append({"page": page, "token": token})
            if str(item["templateType"]) in PAGE_FOOTER_TEMPLATE_TYPES:
                footer = PAGE_FOOTER_PATTERN.search(compact)
                actual = None if footer is None else (
                    int(footer.group(1)), int(footer.group(2))
                )
                expected = (page, len(sizes))
                if actual != expected:
                    page_footer_errors.append(
                        {"page": page, "expected": list(expected), "actual": actual}
                    )

        total_pages += len(sizes)
        blank_page_count += len(blank_pages)
        non_a4_page_count += len(non_a4_pages)
        forbidden_hit_count += len(forbidden_hits)
        page_footer_error_count += len(page_footer_errors)
        unique_sizes = sorted({(round(w, 3), round(h, 3)) for w, h in sizes})
        documents.append(
            {
                "file": portable_path(pdf, evidence_path.parent),
                "profile": profile,
                "templateType": item["templateType"],
                "sha256": sha256(pdf),
                "size": pdf.stat().st_size,
                "pageCount": len(sizes),
                "pageSizePoints": [list(size) for size in unique_sizes],
                "blankPages": blank_pages,
                "nonA4Pages": non_a4_pages,
                "forbiddenTextHits": forbidden_hits,
                "pageFooterErrors": page_footer_errors,
            }
        )

    if len(documents) != EXPECTED_PDF_COUNT:
        raise RuntimeError(f"expected {EXPECTED_PDF_COUNT} PDFs")
    if total_pages != EXPECTED_PAGE_COUNT:
        raise RuntimeError(f"expected {EXPECTED_PAGE_COUNT} pages, got {total_pages}")
    if (blank_page_count or non_a4_page_count or forbidden_hit_count
            or page_footer_error_count):
        raise RuntimeError(
            "render QA failed: "
            f"blank={blank_page_count}, nonA4={non_a4_page_count}, "
            f"forbidden={forbidden_hit_count}, "
            f"pageFooter={page_footer_error_count}"
        )

    evidence: dict[str, object] = {
        "schemaVersion": 1,
        "release": manifest["release"],
        "status": "passed",
        "productionMutation": False,
        "pathBase": "evidence-directory",
        "sourceManifest": portable_path(manifest_path, evidence_path.parent),
        "generatedBy": (
            "scripts/contract/verify_beijing_sign_v6_render_qa_20260720.py"
        ),
        "tools": {
            "pdfinfo": command_version(pdfinfo),
            "pdftotext": command_version(pdftotext),
        },
        "checks": {
            "expectedPdfCount": EXPECTED_PDF_COUNT,
            "expectedPageCount": EXPECTED_PAGE_COUNT,
            "a4TolerancePoints": A4_TOLERANCE_POINTS,
            "blankPageDefinition": "page text is empty after whitespace removal",
            "forbiddenText": list(FORBIDDEN_TEXT),
            "pageFooterTemplateTypes": list(PAGE_FOOTER_TEMPLATE_TYPES),
            "pageFooterPattern": PAGE_FOOTER_PATTERN.pattern,
            "pageFooterFontSizePoints": PAGE_FOOTER_FONT_SIZE,
            "pageFooterBaselinePoints": PAGE_FOOTER_BASELINE,
        },
        "summary": {
            "pdfCount": len(documents),
            "pageCount": total_pages,
            "blankPageCount": blank_page_count,
            "nonA4PageCount": non_a4_page_count,
            "forbiddenTextHitCount": forbidden_hit_count,
            "pageFooterErrorCount": page_footer_error_count,
        },
        "scopeExclusions": {
            "employeeHandbookBody": (
                "user-excluded; authoritative body remains an explicit retained "
                "risk and was not created or edited"
            ),
            "deliveryAddress": (
                "user-excluded; existing contract behavior was retained and was "
                "not edited"
            ),
        },
        "manualVisualApproval": "not-claimed",
        "documents": documents,
    }
    write_json(evidence_path, evidence)

    manifest["renderQa"] = {
        "status": "passed",
        "evidence": portable_path(evidence_path, manifest_path.parent),
        "evidenceSha256": sha256(evidence_path),
        **evidence["summary"],
        "manualVisualApproval": "not-claimed",
    }
    write_json(manifest_path, manifest)
    return evidence


def check_evidence(manifest_path: Path, evidence_path: Path) -> None:
    manifest = load_json(manifest_path)
    validate_manifest_files(manifest_path, manifest)
    validate_page_number_policy(manifest)
    evidence = load_json(evidence_path)
    if evidence.get("status") != "passed":
        raise RuntimeError("render QA evidence must be passed")
    if evidence.get("pathBase") != "evidence-directory":
        raise RuntimeError("render QA evidence path base is not portable")
    if evidence.get("productionMutation") is not False:
        raise RuntimeError("render QA must not claim a production mutation")
    checks = evidence.get("checks", {})
    if (checks.get("pageFooterTemplateTypes") != list(PAGE_FOOTER_TEMPLATE_TYPES)
            or checks.get("pageFooterPattern") != PAGE_FOOTER_PATTERN.pattern
            or checks.get("pageFooterFontSizePoints") != PAGE_FOOTER_FONT_SIZE
            or checks.get("pageFooterBaselinePoints") != PAGE_FOOTER_BASELINE):
        raise RuntimeError("render QA page-footer checks do not match runtime policy")
    summary = evidence.get("summary")
    expected_summary = {
        "pdfCount": EXPECTED_PDF_COUNT,
        "pageCount": EXPECTED_PAGE_COUNT,
        "blankPageCount": 0,
        "nonA4PageCount": 0,
        "forbiddenTextHitCount": 0,
        "pageFooterErrorCount": 0,
    }
    if summary != expected_summary:
        raise RuntimeError(f"unexpected render QA summary: {summary}")
    documents = evidence.get("documents")
    if not isinstance(documents, list) or len(documents) != EXPECTED_PDF_COUNT:
        raise RuntimeError("render QA evidence must pin eighteen PDFs")
    if sum(int(item["pageCount"]) for item in documents) != EXPECTED_PAGE_COUNT:
        raise RuntimeError("render QA evidence page count mismatch")
    for item in documents:
        pdf = resolve_portable(str(item["file"]), evidence_path.parent)
        assert_file(pdf, str(item["sha256"]), int(item["size"]))
        if (item["blankPages"] or item["nonA4Pages"]
                or item["forbiddenTextHits"] or item["pageFooterErrors"]):
            raise RuntimeError(f"failed per-document render QA: {pdf}")

    render_qa = manifest.get("renderQa")
    if not isinstance(render_qa, dict) or render_qa.get("status") != "passed":
        raise RuntimeError("v6 manifest is not linked to passed render QA")
    linked = resolve_portable(str(render_qa["evidence"]), manifest_path.parent)
    if linked != evidence_path.resolve():
        raise RuntimeError("v6 manifest links a different render QA evidence file")
    if render_qa.get("evidenceSha256") != sha256(evidence_path):
        raise RuntimeError("v6 manifest render QA evidence hash mismatch")
    for key, value in expected_summary.items():
        if render_qa.get(key) != value:
            raise RuntimeError(f"v6 manifest render QA summary mismatch: {key}")


def find_command(explicit: str | None, name: str) -> str:
    command = explicit or shutil.which(name)
    if not command:
        raise RuntimeError(
            f"{name} is required for --write; pass --{name}-bin explicitly"
        )
    return command


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--repair-manifest-paths", action="store_true")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--evidence", type=Path, default=DEFAULT_EVIDENCE)
    parser.add_argument("--render-dir", type=Path, default=DEFAULT_RENDER_DIR)
    parser.add_argument("--pdfinfo-bin")
    parser.add_argument("--pdftotext-bin")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest = args.manifest.resolve()
    evidence = args.evidence.resolve()
    try:
        if args.repair_manifest_paths and not args.write:
            raise RuntimeError("--repair-manifest-paths is only valid with --write")
        if args.repair_manifest_paths:
            repair_manifest_paths(manifest)
        if args.write:
            pdfinfo = find_command(args.pdfinfo_bin, "pdfinfo")
            pdftotext = find_command(args.pdftotext_bin, "pdftotext")
            result = build_evidence(
                manifest,
                evidence,
                args.render_dir.resolve(),
                pdfinfo,
                pdftotext,
            )
            print(
                "V6_RENDER_QA_OK "
                f"pdfs={result['summary']['pdfCount']} "
                f"pages={result['summary']['pageCount']} evidence={evidence}"
            )
        else:
            check_evidence(manifest, evidence)
            print(f"V6_RENDER_QA_EVIDENCE_OK evidence={evidence}")
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
