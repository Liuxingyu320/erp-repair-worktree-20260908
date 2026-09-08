#!/usr/bin/env python3
"""Fail-closed static contract for contract-signing-consistency-20260720."""

from __future__ import annotations

import hashlib
import json
import struct
import subprocess
import unittest
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = (
    ROOT / "scripts/contract-signing-consistency-release-20260720.json"
)
MIGRATION_LIST_PATH = (
    ROOT / "scripts/contract-signing-consistency-migrations-20260720.list"
)
FILE_LIST_PATH = (
    ROOT / "scripts/contract-signing-consistency-release-files-20260720.list"
)
SCOPE_PATH = (
    ROOT / "docs/releases/20260720-contract-signing-consistency-release-scope.md"
)
RELEASE_DOC_PATH = (
    ROOT / "docs/releases/20260720-contract-signing-consistency-release-manifest.md"
)
V6_MANIFEST_PATH = (
    ROOT
    / "output/sign-release/20260720-v6/manifest-v6-inline-fixed-evidence.json"
)
V6_EVIDENCE_PATH = (
    ROOT
    / "docs/releases/evidence/20260720-contract-signing-v6-render-qa.json"
)
SYNTHETIC_BROWSER_UAT_PATH = (
    ROOT
    / "docs/releases/evidence/20260720-contract-signing-synthetic-browser-uat.json"
)

EXPECTED_MIGRATIONS = [
    "erp_oa_sign_salary_social_mapping_20260719.sql",
    "erp_system_sign_candidate_phone_index_20260719.sql",
    "erp_oa_sign_company_salary_policy_20260720.sql",
    "erp_oa_sign_dual_sequence_evidence_20260720.sql",
    "erp_oa_sign_onboard_send_idempotency_20260720.sql",
    "erp_oa_sign_task_batch_finalize_20260720.sql",
    "erp_oa_sign_file_cleanup_20260720.sql",
    "erp_oa_sign_task_hard_delete_idempotency_20260720.sql",
]
MIGRATION_MODULE = {
    "erp_system_sign_candidate_phone_index_20260719.sql": "erp-system",
    **{
        name: "erp-oa"
        for name in EXPECTED_MIGRATIONS
        if not name.startswith("erp_system_")
    },
}
OLD_LEDGER_PATHS = {
    "scripts/contract-signing-migrations-20260716.list",
    "scripts/contract-signing-release-20260716.json",
    "scripts/contract-signing-release-files-20260716.list",
    "scripts/onboard-contract-excel-migrations-20260718.list",
    "scripts/onboard-contract-excel-release-20260718.json",
    "scripts/onboard-contract-excel-release-files-20260718.list",
    "docs/releases/20260716-contract-signing-release-manifest.md",
    "docs/releases/20260716-contract-signing-release-scope.md",
    "docs/releases/20260718-onboard-contract-excel-release.md",
}


def read_list(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    body = path.read_bytes()
    if body[:8] != b"\x89PNG\r\n\x1a\n" or body[12:16] != b"IHDR":
        raise AssertionError(f"not a PNG with an IHDR header: {path}")
    return struct.unpack(">II", body[16:24])


def resolve_relative(base: Path, value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        raise AssertionError(f"absolute path is not portable: {value}")
    return (base / path).resolve()


class ContractSigningConsistencyReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        cls.files = read_list(FILE_LIST_PATH)

    def test_manifest_identifies_a_new_forward_only_candidate(self) -> None:
        manifest = self.manifest
        self.assertEqual(manifest["schemaVersion"], 1)
        self.assertEqual(
            manifest["releaseId"], "contract-signing-consistency-20260720"
        )
        self.assertEqual(manifest["status"], "candidate")
        self.assertEqual(manifest["executionPolicy"], "manual-phased-forward-only")
        self.assertFalse(manifest["productionMutationPerformed"])
        self.assertFalse(manifest["productionReleaseReady"])
        self.assertEqual(manifest["migrationCount"], len(EXPECTED_MIGRATIONS))
        self.assertEqual(manifest["sourceFileCount"], len(self.files))
        self.assertEqual(
            manifest["sourceFileList"],
            "scripts/contract-signing-consistency-release-files-20260720.list",
        )
        self.assertEqual(
            manifest["migrationList"],
            "scripts/contract-signing-consistency-migrations-20260720.list",
        )
        self.assertEqual(
            manifest["prerequisiteLedgers"],
            [
                "contract-signing-release-a-20260716",
                "onboard-contract-excel-20260718",
            ],
        )
        self.assertEqual(
            manifest["supersedesReleaseLedgers"], []
        )

    def test_migrations_are_ordered_hash_pinned_and_triply_mirrored(self) -> None:
        self.assertEqual(read_list(MIGRATION_LIST_PATH), EXPECTED_MIGRATIONS)
        migrations = self.manifest["migrations"]
        self.assertEqual(
            [item["file"] for item in migrations], EXPECTED_MIGRATIONS
        )
        self.assertEqual(
            [item["sequence"] for item in migrations],
            list(range(1, len(EXPECTED_MIGRATIONS) + 1)),
        )
        for item in migrations:
            name = item["file"]
            module = MIGRATION_MODULE[name]
            expected_copies = [
                f"sql/{name}",
                f"docker/mysql/db/{name}",
                f"erp-modules/{module}/src/main/resources/db/migration/{name}",
            ]
            self.assertEqual(item["copies"], expected_copies)
            copies = [ROOT / relative for relative in expected_copies]
            self.assertTrue(all(path.is_file() for path in copies), name)
            bodies = [path.read_bytes() for path in copies]
            self.assertTrue(all(body == bodies[0] for body in bodies[1:]), name)
            self.assertEqual(item["sha256"], hashlib.sha256(bodies[0]).hexdigest())
            self.assertEqual(item["applyMode"], "manual-native-mysql")
            self.assertTrue(item["forwardOnly"])
            self.assertTrue(item["rerunnable"])
            self.assertTrue(item["repeatExecutionProducesNoBusinessDuplication"])

        bootstrap = read_list(ROOT / "docker/mysql/bootstrap-files.list")
        for name in EXPECTED_MIGRATIONS:
            self.assertEqual(bootstrap.count(name), 1)

        send_ledger = (
            ROOT / "sql/erp_oa_sign_onboard_send_idempotency_20260720.sql"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "CREATE TABLE IF NOT EXISTS oa_sign_onboard_send_request",
            send_ledger,
        )
        self.assertIn(
            "UNIQUE KEY uk_oa_sign_onboard_send_request (request_id)",
            send_ledger,
        )
        self.assertIn("payload_hash char(64) NOT NULL", send_ledger)
        self.assertIn("claim_token varchar(64) NOT NULL", send_ledger)
        self.assertNotRegex(
            send_ledger.upper(),
            r"(?m)^\s*(?:CONSTRAINT\b.*\s+)?FOREIGN KEY\b",
        )

        hard_delete_ledger = (
            ROOT
            / "sql/erp_oa_sign_task_hard_delete_idempotency_20260720.sql"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "CREATE TABLE IF NOT EXISTS oa_sign_task_hard_delete_operation",
            hard_delete_ledger,
        )
        self.assertIn(
            "UNIQUE KEY uk_oa_sign_task_hard_delete_request (request_id)",
            hard_delete_ledger,
        )
        for contract in (
            "administrator_user_id bigint NOT NULL",
            "payload_hash char(64) NOT NULL",
            "claim_token varchar(64) DEFAULT NULL",
            "lease_expires_time datetime DEFAULT NULL",
            "processed_count int NOT NULL DEFAULT 0",
            "result_json json NOT NULL",
            "version bigint NOT NULL DEFAULT 0",
        ):
            self.assertIn(contract, hard_delete_ledger)
        self.assertNotRegex(
            hard_delete_ledger.upper(),
            r"(?m)^\s*(?:CONSTRAINT\b.*\s+)?FOREIGN KEY\b",
        )

    def test_release_file_list_is_sorted_unique_safe_and_existing(self) -> None:
        files = self.files
        self.assertEqual(files, sorted(files))
        self.assertEqual(len(files), len(set(files)))
        self.assertGreaterEqual(len(files), 100)
        self.assertTrue(OLD_LEDGER_PATHS.isdisjoint(files))
        root = ROOT.resolve()
        for relative in files:
            pure = PurePosixPath(relative)
            self.assertFalse(pure.is_absolute(), relative)
            self.assertNotIn("..", pure.parts, relative)
            self.assertNotIn("*", relative)
            self.assertFalse(relative.startswith("output/"), relative)
            resolved = (ROOT / relative).resolve()
            self.assertTrue(resolved.is_relative_to(root), relative)
            self.assertTrue(resolved.is_file(), relative)
            self.assertFalse((ROOT / relative).is_symlink(), relative)

        required_contract_files = {
            "docs/releases/20260720-contract-signing-consistency-release-manifest.md",
            "docs/releases/20260720-contract-signing-consistency-release-scope.md",
            "docs/releases/evidence/20260720-contract-signing-synthetic-browser-uat.json",
            "docs/releases/evidence/20260720-contract-signing-v6-manual-visual-qa.md",
            "docs/releases/evidence/20260720-contract-signing-v6-render-qa.json",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPdfPageNumberService.java",
            "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaPdfPageNumberServiceTest.java",
            "scripts/contract-signing-consistency-migrations-20260720.list",
            "scripts/contract-signing-consistency-release-20260720.json",
            "scripts/contract-signing-consistency-release-files-20260720.list",
            "scripts/contract/stamp_pdf_page_numbers_20260720.py",
            "scripts/contract/verify_beijing_sign_v6_render_qa_20260720.py",
            "scripts/test_contract_signing_consistency_release_20260720.py",
            "scripts/verify-contract-signing-consistency-release.sh",
        }
        self.assertTrue(required_contract_files.issubset(files))
        for item in self.manifest["migrations"]:
            self.assertTrue(set(item["copies"]).issubset(files))

    def test_v6_manifest_and_render_evidence_are_portable_and_pinned(self) -> None:
        v6 = json.loads(V6_MANIFEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual(v6["pathBase"], "manifest-directory")
        self.assertFalse(v6["productionMutation"])
        self.assertFalse(v6["registrationPayloadGenerated"])
        portable_values = [
            v6["sourceCandidateManifest"],
            *(value for item in v6["artifacts"]
              for value in (item["file"], item["source"])),
            *(item["file"] for item in v6["qaVariants"]),
        ]
        self.assertTrue(all(not Path(value).is_absolute()
                            for value in portable_values))
        for item in v6["artifacts"]:
            target = resolve_relative(V6_MANIFEST_PATH.parent, item["file"])
            source = resolve_relative(V6_MANIFEST_PATH.parent, item["source"])
            self.assertEqual(sha256(target), item["sha256"])
            self.assertEqual(sha256(source), item["sourceSha256"])
        for item in v6["qaVariants"]:
            target = resolve_relative(V6_MANIFEST_PATH.parent, item["file"])
            self.assertEqual(sha256(target), item["sha256"])

        evidence = json.loads(V6_EVIDENCE_PATH.read_text(encoding="utf-8"))
        self.assertEqual(evidence["pathBase"], "evidence-directory")
        self.assertEqual(evidence["status"], "passed")
        self.assertFalse(evidence["productionMutation"])
        self.assertEqual(evidence["manualVisualApproval"], "not-claimed")
        self.assertEqual(
            evidence["summary"],
            {
                "pdfCount": 18,
                "pageCount": 112,
                "blankPageCount": 0,
                "nonA4PageCount": 0,
                "forbiddenTextHitCount": 0,
                "pageFooterErrorCount": 0,
            },
        )
        self.assertEqual(len(evidence["documents"]), 18)
        allowed_document_keys = {
            "file", "profile", "templateType", "sha256", "size",
            "pageCount", "pageSizePoints", "blankPages", "nonA4Pages",
            "forbiddenTextHits", "pageFooterErrors",
        }
        for item in evidence["documents"]:
            self.assertEqual(set(item), allowed_document_keys)
            self.assertFalse(Path(item["file"]).is_absolute())
            target = resolve_relative(V6_EVIDENCE_PATH.parent, item["file"])
            self.assertEqual(sha256(target), item["sha256"])
            self.assertEqual(target.stat().st_size, item["size"])
        linked = v6["renderQa"]
        self.assertEqual(linked["status"], "passed")
        self.assertEqual(linked["evidenceSha256"], sha256(V6_EVIDENCE_PATH))
        self.assertEqual(
            resolve_relative(V6_MANIFEST_PATH.parent, linked["evidence"]),
            V6_EVIDENCE_PATH.resolve(),
        )
        candidate = self.manifest["templateCandidate"]
        self.assertEqual(
            (ROOT / candidate["manifestPath"]).resolve(),
            V6_MANIFEST_PATH.resolve(),
        )
        self.assertEqual(candidate["manifestSha256"], sha256(V6_MANIFEST_PATH))
        self.assertEqual(
            (ROOT / candidate["renderEvidencePath"]).resolve(),
            V6_EVIDENCE_PATH.resolve(),
        )
        self.assertEqual(
            candidate["renderEvidenceSha256"], sha256(V6_EVIDENCE_PATH)
        )

    def test_synthetic_browser_uat_is_pinned_but_real_uat_stays_pending(self) -> None:
        evidence = json.loads(
            SYNTHETIC_BROWSER_UAT_PATH.read_text(encoding="utf-8")
        )
        linked = self.manifest["syntheticBrowserUat"]
        self.assertEqual(linked["status"], "passed")
        self.assertEqual(linked["scenarioCount"], 4)
        self.assertEqual(linked["browserConsoleErrorCount"], 0)
        self.assertEqual(linked["criticalApiNon200Count"], 0)
        self.assertFalse(linked["productionMutation"])
        self.assertFalse(linked["realIdentityUsed"])
        self.assertFalse(linked["realDualFlowSignedUatSatisfied"])
        self.assertEqual(
            linked["evidencePath"],
            "docs/releases/evidence/20260720-contract-signing-synthetic-browser-uat.json",
        )
        self.assertEqual(
            linked["evidenceSha256"], sha256(SYNTHETIC_BROWSER_UAT_PATH)
        )

        self.assertEqual(evidence["status"], "passed")
        self.assertEqual(evidence["pathBase"], "repository-root")
        self.assertFalse(evidence["productionMutation"])
        self.assertFalse(evidence["realIdentityUsed"])
        self.assertFalse(evidence["realSignaturePerformed"])
        self.assertFalse(evidence["realDualFlowSignedUatSatisfied"])
        self.assertEqual(evidence["manualVisualApproval"], "not-claimed")
        self.assertEqual(
            evidence["runnerObservation"],
            {
                "browserConsoleErrorCount": 0,
                "criticalApiNon200Count": 0,
                "preConfirmationOriginalPdfRequestCount": 0,
                "completedFlowFinalArchiveDownloadCount": 2,
                "hardDeleteExpectedVersionObserved": True,
                "signedTaskDeleteProtectionObserved": True,
                "criticalApiObservedStatusCodes": [200],
            },
        )
        self.assertEqual(len(evidence["scenarios"]), 4)
        self.assertEqual(
            {item["id"] for item in evidence["scenarios"]},
            {
                "company-first-signed",
                "signature-first-pending-company",
                "signature-first-final-confirmed",
                "batch-delete-protected-signed",
            },
        )
        for item in evidence["scenarios"]:
            target = resolve_relative(ROOT, item["file"])
            self.assertTrue(target.is_file(), item["file"])
            self.assertEqual(sha256(target), item["sha256"])
            self.assertEqual(target.stat().st_size, item["size"])
            self.assertEqual(
                png_dimensions(target),
                (item["pixelWidth"], item["pixelHeight"]),
            )

        gates = self.manifest["externalGates"]
        self.assertEqual(gates["realDualFlowSignedUat"], "pending")
        self.assertEqual(
            gates["nativeMySql57Rehearsal"],
            "not-applicable-version-verified",
        )
        database = self.manifest["databaseValidation"]
        self.assertEqual(
            database["productionVersionEvidence"]["version"], "8.0.24"
        )
        self.assertEqual(
            database["nativeMySql57Rehearsal"],
            {
                "status": "not-applicable-version-verified",
                "preReleaseReadOnlyVersionRecheck": "required",
            },
        )
        local = database["localSendIdempotencyRehearsal"]
        self.assertEqual(local["status"], "passed")
        self.assertEqual(local["version"], "8.0.45")
        self.assertEqual(local["database"], "BossERP_NEW")
        self.assertEqual(local["consecutiveRunCount"], 2)
        self.assertEqual(local["resultingColumnCount"], 9)
        self.assertEqual(local["resultingUniqueIndexCount"], 1)
        self.assertFalse(local["productionMutation"])
        hard_delete = database["localHardDeleteIdempotencyRehearsal"]
        self.assertEqual(hard_delete["status"], "passed")
        self.assertEqual(hard_delete["version"], "8.0.45")
        self.assertEqual(hard_delete["database"], "BossERP_NEW")
        self.assertEqual(hard_delete["consecutiveRunCount"], 2)
        self.assertEqual(hard_delete["resultingColumnCount"], 16)
        self.assertEqual(hard_delete["resultingUniqueIndexCount"], 1)
        self.assertFalse(hard_delete["productionMutation"])

    def test_user_exclusions_are_not_false_fixes_or_local_blockers(self) -> None:
        exclusions = self.manifest["userExcludedRetainedRisks"]
        self.assertEqual(
            set(exclusions), {"employeeHandbookBody", "deliveryAddress"}
        )
        self.assertFalse(exclusions["employeeHandbookBody"]["modified"])
        self.assertFalse(exclusions["employeeHandbookBody"]["resolved"])
        self.assertFalse(exclusions["deliveryAddress"]["modified"])
        self.assertFalse(exclusions["deliveryAddress"]["resolved"])
        self.assertEqual(
            self.manifest["templateCandidate"]["activationStatus"], "disabled"
        )
        self.assertFalse(
            self.manifest["templateCandidate"]["registrationPayloadGenerated"]
        )
        text = SCOPE_PATH.read_text(encoding="utf-8")
        release_text = RELEASE_DOC_PATH.read_text(encoding="utf-8")
        for marker in (
            "员工手册正文",
            "送达地址",
            "明确排除",
            "不纳入本轮本地完成判定",
            "未发布生产",
            "未代签",
        ):
            self.assertIn(marker, text + release_text)

    def test_external_human_and_production_gates_remain_pending(self) -> None:
        gates = self.manifest["externalGates"]
        self.assertEqual(
            set(gates),
            {
                "nativeMySql57Rehearsal",
                "authorizedProductionBackup",
                "authorizedProductionMigration",
                "templateRegistrationAndPlanPublish",
                "realDualFlowSignedUat",
                "hrBusinessLegalApproval",
                "grayRolloutApproval",
            },
        )
        self.assertEqual(
            gates["nativeMySql57Rehearsal"],
            "not-applicable-version-verified",
        )
        unresolved = {
            key: value
            for key, value in gates.items()
            if key != "nativeMySql57Rehearsal"
        }
        self.assertTrue(all(value in {"pending", "approval-required"}
                            for value in unresolved.values()))
        non_claims = self.manifest["nonClaims"]
        self.assertFalse(non_claims["productionDeployed"])
        self.assertFalse(non_claims["realEmployeeContractSigned"])
        self.assertFalse(non_claims["realTaskHardDeleted"])
        self.assertFalse(non_claims["externalApprovalFabricated"])

    def test_render_evidence_checker_passes_without_mutation(self) -> None:
        completed = subprocess.run(
            [
                "python3",
                str(
                    ROOT
                    / "scripts/contract/verify_beijing_sign_v6_render_qa_20260720.py"
                ),
                "--check",
            ],
            cwd=ROOT,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        self.assertEqual(completed.returncode, 0, completed.stdout)
        self.assertIn("V6_RENDER_QA_EVIDENCE_OK", completed.stdout)


if __name__ == "__main__":
    unittest.main()
