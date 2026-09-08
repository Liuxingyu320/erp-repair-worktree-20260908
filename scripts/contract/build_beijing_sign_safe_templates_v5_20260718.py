#!/usr/bin/env python3
"""Build isolated v5 signing-block-last-page templates and QA variants.

The two contracts receive exactly one active pageBreakBefore on the paragraph
that starts with the company-seal label. The v3 sources and v4 artifacts are
never overwritten, and this script performs no upload or production write.
"""

from __future__ import annotations

import hashlib
import json
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Final
from zipfile import ZipFile

from docx import Document


REPO_ROOT: Final[Path] = Path(__file__).resolve().parents[2]
SOURCE_DIR: Final[Path] = REPO_ROOT / "uploadPath/sign-template/onboard-20260714-v3-draft"
OUTPUT_DIR: Final[Path] = REPO_ROOT / "output/sign-release/20260718/templates"
QA_DIR: Final[Path] = REPO_ROOT / "output/sign-release/20260718/qa/v5"
MANIFEST_PATH: Final[Path] = REPO_ROOT / "output/sign-release/20260718/manifest-v5-sign-block-last-page.json"
SEAL_PREFIX: Final[str] = "甲方（盖章）：____________"


def fixed_width(seed: str, width: int) -> str:
    if not seed or width < 1:
        raise ValueError("seed and width must be non-empty/positive")
    return (seed * ((width // len(seed)) + 1))[:width]


@dataclass(frozen=True)
class TemplateSpec:
    source_name: str
    output_name: str
    template_type: str
    appendix_marker: str | None
    company_seal_required: bool


SPECS: Final[tuple[TemplateSpec, ...]] = (
    TemplateSpec(
        "09_ONBOARD_LABOR_CONTRACT.docx",
        "09_ONBOARD_LABOR_CONTRACT_v5-sign-block-last-page.docx",
        "ONBOARD_LABOR_CONTRACT",
        "第十六条 附则",
        True,
    ),
    TemplateSpec(
        "13_ONBOARD_SERVICE_CONTRACT.docx",
        "13_ONBOARD_SERVICE_CONTRACT_v5-sign-block-last-page.docx",
        "ONBOARD_SERVICE_CONTRACT",
        "第十七条 附则",
        True,
    ),
    TemplateSpec(
        "14_ONBOARD_SERVICE_RECEIPT.docx",
        "14_ONBOARD_SERVICE_RECEIPT_v5-sign-block-last-page.docx",
        "ONBOARD_SERVICE_RECEIPT",
        None,
        False,
    ),
)


COMMON_MAX: Final[dict[str, str]] = {
    "employeePhone": "1" * 32,
    "employeeIdCard": "9" * 32,
    "employeeAddress": fixed_width("北京市朝阳区压力测试地址", 200),
    "postName": fixed_width("压力测试岗位", 64),
    "contractStartDate": "9999-12-31",
    "contractEndDate": "9999-12-31",
    "probationStartDate": "9999-12-31",
    "probationEndDate": "9999-12-31",
    "signDate": "9999-12-31",
    "baseSalary": "99999999999999.99",
    "postSalary": "99999999999999.99",
    "fieldAllowance": "99999999999999.99",
    "performanceSalary": "99999999999999.99",
    "salaryTotal": "99999999999999.99",
}


PROFILE_VALUES: Final[dict[str, dict[str, str]]] = {
    # Persisted widths in erp_oa_sign_package_20260702.sql and legal entity DDL.
    "database-max": {
        **COMMON_MAX,
        "employeeName": fixed_width("欧阳压力测试姓名", 64),
        "companyName": fixed_width("压力测试法律主体公司名称", 160),
        "servicePersonType": fixed_width("压力测试劳务人员类型", 32),
        "insuranceType": fixed_width("压力测试商业保险类型", 64),
    },
    # UI constraints/options: account nickname 30, company 160, address 200,
    # phone/ID 32, and the longest enumerated service/insurance values.
    "ui-max": {
        **COMMON_MAX,
        "employeeName": fixed_width("欧阳压力测试姓名", 30),
        "companyName": fixed_width("压力测试法律主体公司名称", 160),
        "servicePersonType": "其他劳务人员",
        "insuranceType": "商业意外保险",
    },
    # Known Beijing release values. Address and insurance are not yet supplied
    # by the employees/HR, so the longest current UI options are used there.
    "beijing-known": {
        "employeeName": "苏余玉",
        "employeePhone": "13800000000",
        "employeeIdCard": "110101199001010011",
        "employeeAddress": fixed_width("北京市朝阳区员工登录后自行填写现住址", 80),
        "companyName": "舟山茗汇文化传播有限公司",
        "postName": "门店服务岗位",
        "servicePersonType": "在校实习生",
        "insuranceType": "商业意外保险",
        "contractStartDate": "2026-07-18",
        "contractEndDate": "2027-07-17",
        "probationStartDate": "2026-07-18",
        "probationEndDate": "2026-10-17",
        "signDate": "2026-07-18",
        "baseSalary": "13000",
        "postSalary": "3300",
        "fieldAllowance": "2500",
        "performanceSalary": "5700",
        "salaryTotal": "13000",
    },
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ensure_new(paths: list[Path]) -> None:
    existing = [str(path) for path in paths if path.exists()]
    if existing:
        raise RuntimeError("refusing to overwrite:\n  " + "\n  ".join(existing))


def build_contract(source: Path, target: Path, appendix_marker: str) -> None:
    document = Document(source)
    appendix = [p for p in document.paragraphs if p.text.strip() == appendix_marker]
    if len(appendix) != 1 or appendix[0].paragraph_format.page_break_before is True:
        raise RuntimeError(f"appendix marker must remain without active page break: {source}")
    seal_paragraphs = [p for p in document.paragraphs if p.text.startswith(SEAL_PREFIX)]
    if len(seal_paragraphs) != 1:
        raise RuntimeError(f"expected one seal paragraph in {source}, found {len(seal_paragraphs)}")
    seal_paragraphs[0].paragraph_format.page_break_before = True
    target.parent.mkdir(parents=True, exist_ok=True)
    document.save(target)

    check = Document(target)
    active_seal = [
        p for p in check.paragraphs
        if p.text.startswith(SEAL_PREFIX) and p.paragraph_format.page_break_before is True
    ]
    active_appendix = [
        p for p in check.paragraphs
        if p.text.strip() == appendix_marker and p.paragraph_format.page_break_before is True
    ]
    if len(active_seal) != 1 or active_appendix:
        raise RuntimeError(f"v5 page-break verification failed: {target}")


def replace_values(source: Path, target: Path, values: dict[str, str]) -> dict[str, int]:
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

    def replace_table(table) -> None:
        for row in table.rows:
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    replace_paragraph(paragraph)
                for nested in cell.tables:
                    replace_table(nested)

    for paragraph in document.paragraphs:
        replace_paragraph(paragraph)
    for table in document.tables:
        replace_table(table)
    for section in document.sections:
        for container in (section.header, section.footer):
            for paragraph in container.paragraphs:
                replace_paragraph(paragraph)
            for table in container.tables:
                replace_table(table)
    target.parent.mkdir(parents=True, exist_ok=True)
    document.save(target)

    unresolved_parts = []
    with ZipFile(target) as archive:
        for name in archive.namelist():
            if name.startswith("word/") and name.endswith(".xml") and b"${" in archive.read(name):
                unresolved_parts.append(name)
    if unresolved_parts:
        raise RuntimeError(f"unresolved placeholders in {target}: {unresolved_parts}")
    return {key: value for key, value in counts.items() if value}


def main() -> int:
    try:
        outputs = [OUTPUT_DIR / spec.output_name for spec in SPECS]
        variants = [
            QA_DIR / profile / spec.output_name.replace(".docx", f"_{profile}.docx")
            for profile in PROFILE_VALUES
            for spec in SPECS
        ]
        ensure_new(outputs + variants + [MANIFEST_PATH])
        source_before = {spec.source_name: sha256(SOURCE_DIR / spec.source_name) for spec in SPECS}

        artifacts = []
        for spec, target in zip(SPECS, outputs):
            source = SOURCE_DIR / spec.source_name
            if spec.company_seal_required:
                build_contract(source, target, spec.appendix_marker or "")
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
            artifacts.append({
                "file": str(target.resolve()),
                "source": str(source.resolve()),
                "templateType": spec.template_type,
                "version": "v5-sign-block-last-page",
                "sha256": sha256(target),
                "size": target.stat().st_size,
                "pageBreakBefore": SEAL_PREFIX if spec.company_seal_required else None,
                "companySealRequired": spec.company_seal_required,
                "sealPlacementMode": "LAST_PAGE" if spec.company_seal_required else None,
                "recommendedSealPosition": None,
            })

        qa_variants = []
        for profile, values in PROFILE_VALUES.items():
            for spec, source in zip(SPECS, outputs):
                target = QA_DIR / profile / spec.output_name.replace(".docx", f"_{profile}.docx")
                counts = replace_values(source, target, values)
                qa_variants.append({
                    "profile": profile,
                    "templateType": spec.template_type,
                    "file": str(target.resolve()),
                    "sha256": sha256(target),
                    "size": target.stat().st_size,
                    "replacements": counts,
                    "fieldLengths": {key: len(value) for key, value in values.items()},
                })

        source_after = {spec.source_name: sha256(SOURCE_DIR / spec.source_name) for spec in SPECS}
        if source_before != source_after:
            raise RuntimeError("v3 sources changed during build")
        manifest = {
            "release": "beijing-onboard-sign-20260718-v5-sign-block-last-page",
            "productionMutation": False,
            "sourceFilesUnchanged": True,
            "sourceSha256": source_after,
            "artifacts": artifacts,
            "qaVariants": qa_variants,
            "renderQa": None,
        }
        MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(MANIFEST_PATH)
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
