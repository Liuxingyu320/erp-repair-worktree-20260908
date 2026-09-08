#!/usr/bin/env python3
"""Copy reviewed v8 candidates into an immutable, local-only QA release set.

This builder consumes the disabled candidate manifest produced by
``prepare_onboard_templates_20260718.py``.  It never changes a source DOCX,
never uploads or registers a template, and refuses to overwrite any artifact.
The output keeps signature/seal fields in their original document positions;
the fixed one-page electronic evidence placeholder belongs to the PDF layer.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path
from typing import Final
from zipfile import ZipFile

from docx import Document


REPO_ROOT: Final[Path] = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE_DIR: Final[Path] = (
    REPO_ROOT / "output/contract-template-review/20260721-v7/candidates"
)
DEFAULT_CANDIDATE_MANIFEST: Final[Path] = (
    REPO_ROOT / "output/contract-template-review/20260721-v7/candidate-manifest.json"
)
DEFAULT_OUTPUT_DIR: Final[Path] = (
    REPO_ROOT / "output/sign-release/20260721-v7/templates"
)
DEFAULT_QA_DIR: Final[Path] = REPO_ROOT / "output/sign-release/20260721-v7/qa/docx"
DEFAULT_MANIFEST: Final[Path] = (
    REPO_ROOT / "output/sign-release/20260721-v7/manifest-v7-dynamic-term-evidence.json"
)
EXPECTED_CANDIDATE_VERSION: Final[str] = "20260724-v8-draft"
RELEASE_VERSION: Final[str] = "v8-service-selection-evidence"
ATTACHMENT_MARKERS: Final[tuple[str, ...]] = (
    "attachmentDormitoryMark",
    "attachmentDutyMark",
    "attachmentHandbookMark",
    "attachmentSalaryMark",
    "attachmentServiceReceiptMark",
)
SELECTION_MARKERS: Final[tuple[str, ...]] = (
    "serviceStudentMark",
    "serviceRetiredMark",
    "insuranceCommercialAccidentMark",
    "insuranceEmployerLiabilityMark",
)


def fixed_width(seed: str, width: int) -> str:
    """Repeat and truncate readable content to an exact character width."""
    if not seed or width < 1:
        raise ValueError("seed and width must be non-empty/positive")
    return (seed * ((width // len(seed)) + 1))[:width]


COMMON_MAX: Final[dict[str, str]] = {
    "employeeName": fixed_width("欧阳压力测试姓名", 64),
    "employeePhone": "1" * 32,
    "employeeIdCard": "9" * 32,
    "employeeAddress": fixed_width("北京市朝阳区压力测试送达地址", 200),
    "companyName": fixed_width("压力测试法律主体公司名称", 160),
    "companyAddress": fixed_width("压力测试法律主体注册地址", 200),
    "companyLegalRepresentative": fixed_width("欧阳压力测试法定代表人", 64),
    "postName": fixed_width("压力测试岗位", 64),
    "postLevel": "99",
    "servicePersonType": fixed_width("压力测试劳务人员类型", 32),
    "insuranceType": fixed_width("压力测试商业保险类型", 64),
    "contractStartDate": "9999-12-31",
    "contractEndDate": "9999-12-31",
    "probationStartDate": "9999-12-31",
    "probationEndDate": "9999-12-31",
    "contractTermSelection": "A",
    "contractTermFixedMark": "☑",
    "contractTermOpenEndedMark": "□",
    "incomeStartYearMonth": "9999年12月",
    "signDate": "9999-12-31",
    "baseSalary": "99999999999999.99",
    "postSalary": "99999999999999.99",
    "fieldAllowance": "99999999999999.99",
    "performanceSalary": "99999999999999.99",
    "salaryTotal": "99999999999999.99",
    **{marker: "☑" for marker in ATTACHMENT_MARKERS},
    "serviceStudentMark": "☑",
    "serviceRetiredMark": "□",
    "insuranceCommercialAccidentMark": "☑",
    "insuranceEmployerLiabilityMark": "□",
}


PROFILE_VALUES: Final[dict[str, dict[str, str]]] = {
    "normal": {
        "employeeName": "段继康",
        "employeePhone": "16657049808",
        "employeeIdCard": "410381198909272516",
        "employeeAddress": "杭州市余杭区铭雅苑东区",
        "companyName": "杭州翕然茶业有限公司",
        "companyAddress": "浙江省杭州市西湖区转塘街道珊瑚沙路369号2号楼西玥酒店一层",
        "companyLegalRepresentative": "王庆旭",
        "postName": "行政经理",
        "postLevel": "7",
        "servicePersonType": "退休返聘",
        "insuranceType": "商业意外保险",
        "contractStartDate": "2026-06-16",
        "contractEndDate": "2029-06-16",
        "probationStartDate": "2026-06-16",
        "probationEndDate": "2026-09-15",
        "contractTermSelection": "A",
        "contractTermFixedMark": "☑",
        "contractTermOpenEndedMark": "□",
        "incomeStartYearMonth": "2025年06月",
        "signDate": "2026-07-20",
        "baseSalary": "3300",
        "postSalary": "1700",
        "fieldAllowance": "2500",
        "performanceSalary": "5500",
        "salaryTotal": "13000",
        **{marker: "☑" for marker in ATTACHMENT_MARKERS},
        "serviceStudentMark": "□",
        "serviceRetiredMark": "☑",
        "insuranceCommercialAccidentMark": "☑",
        "insuranceEmployerLiabilityMark": "□",
    },
    "database-max": COMMON_MAX,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def portable_path(path: Path, base_dir: Path) -> str:
    """Return a POSIX path relative to the manifest directory.

    Release manifests are copied between workstations and build roots.  Absolute
    paths would make an otherwise valid immutable artifact set unverifiable as
    soon as the checkout location changes.
    """
    return Path(os.path.relpath(path.resolve(), base_dir.resolve())).as_posix()


def ensure_new(paths: list[Path]) -> None:
    existing = [str(path) for path in paths if path.exists()]
    if existing:
        raise RuntimeError("refusing to overwrite:\n  " + "\n  ".join(existing))


def output_name(candidate_name: str) -> str:
    path = Path(candidate_name)
    return f"{path.stem}_{RELEASE_VERSION}{path.suffix}"


def _visit_table(table, visit_paragraph) -> None:
    for row in table.rows:
        for cell in row.cells:
            for paragraph in cell.paragraphs:
                visit_paragraph(paragraph)
            for nested in cell.tables:
                _visit_table(nested, visit_paragraph)


def replace_values(source: Path, target: Path, values: dict[str, str]) -> dict[str, int]:
    """Create one fully populated QA DOCX while retaining run-level formatting."""
    document = Document(source)
    counts = {key: 0 for key in values}

    def replace_paragraph(paragraph) -> None:
        for run in paragraph.runs:
            text = run.text
            for key, value in values.items():
                placeholder = "${" + key + "}"
                count = text.count(placeholder)
                if count:
                    text = text.replace(placeholder, value)
                    counts[key] += count
            run.text = text

    for paragraph in document.paragraphs:
        replace_paragraph(paragraph)
    for table in document.tables:
        _visit_table(table, replace_paragraph)
    for section in document.sections:
        for container in (section.header, section.footer):
            for paragraph in container.paragraphs:
                replace_paragraph(paragraph)
            for table in container.tables:
                _visit_table(table, replace_paragraph)

    target.parent.mkdir(parents=True, exist_ok=True)
    document.save(target)
    unresolved: list[str] = []
    with ZipFile(target) as archive:
        for name in archive.namelist():
            if (
                name.startswith("word/")
                and name.endswith(".xml")
                and b"${" in archive.read(name)
            ):
                unresolved.append(name)
    if unresolved:
        raise RuntimeError(f"unresolved placeholders in {target}: {unresolved}")
    return {key: count for key, count in counts.items() if count}


def load_candidate_manifest(path: Path) -> dict[str, object]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("version") != EXPECTED_CANDIDATE_VERSION:
        raise RuntimeError(
            f"candidate version must be {EXPECTED_CANDIDATE_VERSION}, "
            f"got {payload.get('version')!r}"
        )
    if payload.get("registrationPayloadGenerated") is not False:
        raise RuntimeError("candidate manifest must remain review-only")
    labor_bindings = payload.get("packageBindingRules", {}).get("labor", [])
    if "ONBOARD_HANDBOOK" not in labor_bindings:
        raise RuntimeError("labor package must require ONBOARD_HANDBOOK")
    evidence = payload.get("evidencePagePolicy", {})
    if evidence.get("pageCount") != 1 or evidence.get("docxAddsBlankPage") is not False:
        raise RuntimeError("candidate evidence policy must be one PDF-layer page")
    page_numbers = payload.get("pageNumberPolicy", {})
    if (page_numbers.get("mode") != "PDF_LAYER_DYNAMIC"
            or page_numbers.get("templateTypes") != ["ONBOARD_LABOR_CONTRACT"]):
        raise RuntimeError("candidate labor-contract page numbers must use the PDF layer")
    return payload


def build(
    source_dir: Path,
    candidate_manifest_path: Path,
    output_dir: Path,
    qa_dir: Path,
    manifest_path: Path,
) -> Path:
    source_dir = source_dir.resolve()
    candidate_manifest_path = candidate_manifest_path.resolve()
    output_dir = output_dir.resolve()
    qa_dir = qa_dir.resolve()
    manifest_path = manifest_path.resolve()
    if not source_dir.is_dir():
        raise NotADirectoryError(source_dir)
    if not candidate_manifest_path.is_file():
        raise FileNotFoundError(candidate_manifest_path)

    candidate_manifest = load_candidate_manifest(candidate_manifest_path)
    manifest_dir = manifest_path.parent
    candidate_items = candidate_manifest.get("artifacts", [])
    if not isinstance(candidate_items, list) or len(candidate_items) != 9:
        raise RuntimeError("candidate manifest must contain exactly nine artifacts")

    planned: list[tuple[dict[str, object], Path, Path]] = []
    for item in candidate_items:
        candidate = item.get("candidate", {})
        candidate_name = candidate.get("file")
        if not isinstance(candidate_name, str):
            raise RuntimeError("candidate artifact is missing its file name")
        source = source_dir / candidate_name
        if not source.is_file():
            raise FileNotFoundError(source)
        if sha256(source) != candidate.get("sha256"):
            raise RuntimeError(f"candidate hash mismatch: {source}")
        target = output_dir / output_name(candidate_name)
        planned.append((item, source, target))

    qa_paths = [
        qa_dir / profile / target.name.replace(".docx", f"_{profile}.docx")
        for profile in PROFILE_VALUES
        for _, _, target in planned
    ]
    ensure_new([target for _, _, target in planned] + qa_paths + [manifest_path])

    source_before = {source.name: sha256(source) for _, source, _ in planned}
    artifacts: list[dict[str, object]] = []
    for item, source, target in planned:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        artifacts.append(
            {
                "file": portable_path(target, manifest_dir),
                "source": portable_path(source, manifest_dir),
                "sourceSha256": source_before[source.name],
                "sha256": sha256(target),
                "size": target.stat().st_size,
                "templateType": item.get("type"),
                "candidateVersion": item.get("version"),
                "releaseVersion": RELEASE_VERSION,
                "status": "disabled",
                "signaturePlacementMode": "INLINE_ORIGINAL_POSITION",
                "sealPlacementMode": (
                    "INLINE_ORIGINAL_POSITION"
                    if item.get("sealPolicy", {}).get("recommended")
                    else None
                ),
                "evidencePage": {
                    "mode": "FIXED_PDF_LAYER_PLACEHOLDER",
                    "count": 1,
                    "docxAddsBlankPage": False,
                },
            }
        )

    qa_variants: list[dict[str, object]] = []
    for profile, values in PROFILE_VALUES.items():
        for item, _, source in planned:
            target = qa_dir / profile / source.name.replace(
                ".docx", f"_{profile}.docx"
            )
            counts = replace_values(source, target, values)
            qa_variants.append(
                {
                    "profile": profile,
                    "templateType": item.get("type"),
                    "file": portable_path(target, manifest_dir),
                    "sha256": sha256(target),
                    "size": target.stat().st_size,
                    "replacements": counts,
                    "fieldLengths": {key: len(value) for key, value in values.items()},
                }
            )

    source_after = {source.name: sha256(source) for _, source, _ in planned}
    if source_before != source_after:
        raise RuntimeError("v8 candidate sources changed during build")

    manifest = {
        "manifestVersion": 1,
        "release": "onboard-sign-20260724-v8-service-selection-evidence",
        "productionMutation": False,
        "registrationPayloadGenerated": False,
        "sourceFilesUnchanged": True,
        "pathBase": "manifest-directory",
        "sourceCandidateManifest": portable_path(
            candidate_manifest_path, manifest_dir
        ),
        "sourceCandidateManifestSha256": sha256(candidate_manifest_path),
        "sourceSha256": source_after,
        "releaseBlockers": candidate_manifest.get("releaseBlockers", []),
        "bindingConstraints": candidate_manifest.get("bindingConstraints", []),
        "packageBindingRules": candidate_manifest.get("packageBindingRules", {}),
        "attachmentChecklistPolicy": candidate_manifest.get(
            "attachmentChecklistPolicy", {}
        ),
        "evidencePagePolicy": candidate_manifest.get("evidencePagePolicy", {}),
        "pageNumberPolicy": candidate_manifest.get("pageNumberPolicy", {}),
        "artifacts": artifacts,
        "qaProfiles": list(PROFILE_VALUES),
        "qaVariants": qa_variants,
        "renderQa": None,
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return manifest_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument(
        "--candidate-manifest", type=Path, default=DEFAULT_CANDIDATE_MANIFEST
    )
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--qa-dir", type=Path, default=DEFAULT_QA_DIR)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = build(
            args.source_dir,
            args.candidate_manifest,
            args.output_dir,
            args.qa_dir,
            args.manifest,
        )
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
