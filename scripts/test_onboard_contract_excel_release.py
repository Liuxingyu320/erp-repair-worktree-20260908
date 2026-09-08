#!/usr/bin/env python3
"""Fail-closed source contract for the 20260718 onboarding Excel release."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import unittest
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/onboard-contract-excel-release-20260718.json"
MIGRATIONS = ROOT / "scripts/onboard-contract-excel-migrations-20260718.list"
FILES = ROOT / "scripts/onboard-contract-excel-release-files-20260718.list"
DELETED = ROOT / "scripts/onboard-contract-excel-deleted-paths-20260718.list"
MIXED = ROOT / "scripts/onboard-contract-excel-mixed-paths-20260718.list"
EVIDENCE = ROOT / "docs/releases/evidence/20260718-onboard-contract-excel-evidence.json"
OA_MIGRATION = "erp_oa_sign_onboard_import_20260718.sql"
SYSTEM_MIGRATION = "erp_system_sign_profile_supplement_20260718.sql"
EXPECTED_MIGRATIONS = [OA_MIGRATION, SYSTEM_MIGRATION]
EXPECTED_EXTERNAL_GATES = {
    "nativeMySqlRehearsal",
    "templateHrLegalApproval",
    "templateRegistrationAndPlanPublish",
    "isolatedThreePersonUat",
    "selected27WorkbookUat",
    "finalPdfHashAuditEvidence",
    "oneScopeGrayApproval",
}

FORBIDDEN_PREFIXES = (
    "erp-modules/erp-file/",
    "erp-modules/erp-inventory/",
    "erp-ui/src/views/inventory/",
    "erp-ui/src/components/ImageUpload/",
    "output/",
    "target/",
)
FORBIDDEN_PARTS = {"target", "dist", "node_modules", "uploadPath", ".git"}
FORBIDDEN_SUFFIXES = (
    ".7z", ".docx", ".jks", ".key", ".p12", ".pem", ".pfx",
    ".tar", ".tar.gz", ".tgz", ".xls", ".xlsx", ".zip",
)


def ordered_list(source: Path) -> list[str]:
    return [
        line.strip()
        for line in source.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def sha256(source: Path) -> str:
    return hashlib.sha256(source.read_bytes()).hexdigest()


class OnboardContractExcelReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def assert_immutable_source_contract(self, manifest: dict) -> None:
        self.assertEqual(manifest["status"], "development")
        gates = manifest["externalGates"]
        self.assertEqual(set(gates), EXPECTED_EXTERNAL_GATES)
        self.assertTrue(all(value == "external-pending" for value in gates.values()))

        source_candidate = manifest["sourceCandidate"]
        self.assertEqual(
            source_candidate["baselineReleaseId"],
            "contract-signing-release-a-20260716",
        )
        self.assertTrue(source_candidate["worktreeMustBeClean"])
        self.assertTrue(source_candidate["diffMustStayWithinReleaseAndDeletedPaths"])
        self.assertTrue(source_candidate["candidateHeadMustBeVerifiedAtRuntime"])
        self.assertEqual(
            source_candidate["approvedPatchExcludes"],
            [],
        )
        required_changed = source_candidate["requiredChangedPaths"]
        self.assertEqual(required_changed, sorted(required_changed))
        self.assertEqual(len(required_changed), len(set(required_changed)))
        self.assertGreaterEqual(len(required_changed), 10)
        self.assertTrue(set(required_changed).issubset(ordered_list(FILES)))
        prerequisite = manifest["prerequisiteRelease"]
        self.assertEqual(
            prerequisite["releaseId"], "contract-signing-release-a-20260716"
        )
        self.assertEqual(
            prerequisite["manifestPath"],
            "scripts/contract-signing-release-20260716.json",
        )
        self.assertEqual(
            prerequisite["releaseRef"],
            "refs/tags/contract-signing-release-a-20260716",
        )
        self.assertEqual(
            manifest["approvalAnchors"],
            {
                "sourceApprovalEnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL",
                "sourceApprovalSignatureEnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL_SIGNATURE",
                "buildAttestationEnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION",
                "buildAttestationSignatureEnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION_SIGNATURE",
                "trustedSignerPublicKeyEnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_PUBLIC_KEY",
                "trustedSignerSha256EnvironmentVariable":
                    "ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_SHA256",
            },
        )
        external_evidence = manifest["externalGateEvidence"]
        self.assertEqual(
            external_evidence["path"],
            "docs/releases/evidence/20260718-onboard-contract-excel-evidence.json",
        )
        artifacts = manifest["artifactEvidence"]
        self.assertEqual(
            set(artifacts), {"status", "oaJar", "systemJar", "frontendDist"}
        )
        self.assertEqual(
            artifacts["oaJar"]["path"],
            "docker/erp/modules/oa/jar/erp-modules-oa.jar",
        )
        self.assertEqual(
            artifacts["systemJar"]["path"],
            "docker/erp/modules/system/jar/erp-modules-system.jar",
        )
        self.assertEqual(
            artifacts["frontendDist"]["path"], "docker/nginx/html/dist"
        )
        self.assertEqual(prerequisite["status"], "verification-pending")
        self.assertIsNone(prerequisite["commit"])
        self.assertIsNone(prerequisite["manifestSha256"])
        self.assertEqual(source_candidate["status"], "reassembly-pending")
        for key in ("baselineCommit", "approvedPatchSha256"):
            self.assertIsNone(source_candidate[key])
        self.assertEqual(artifacts["status"], "build-pending")
        self.assertIsNone(artifacts["oaJar"]["sha256"])
        self.assertIsNone(artifacts["systemJar"]["sha256"])
        self.assertIsNone(artifacts["frontendDist"]["treeSha256"])
        self.assertIsNone(external_evidence["sha256"])

    def test_manifest_is_immutable_pending_source_contract(self) -> None:
        manifest = self.manifest
        self.assertEqual(manifest["schemaVersion"], 1)
        self.assertEqual(manifest["releaseId"], "onboard-contract-excel-20260718")
        self.assertEqual(manifest["executionPolicy"], "manual-phased")
        self.assertEqual(
            manifest["requiresReleaseIds"], ["contract-signing-release-a-20260716"]
        )
        self.assertEqual(
            manifest["supersedesReleaseIds"],
            ["existing-employee-onboard-contract-20260717"],
        )
        self.assertTrue(manifest["dockerBootstrapIncluded"])
        self.assertEqual(manifest["sourceBoundaryMode"],
                         "feature-hunks-reassembled-on-clean-candidate")
        self.assertEqual(manifest["releaseFileCount"], len(ordered_list(FILES)))
        self.assertEqual(manifest["deletedPathCount"], len(ordered_list(DELETED)))
        self.assertEqual(manifest["mixedPathCount"], len(ordered_list(MIXED)))
        build = manifest["buildPolicy"]
        self.assertTrue(build["cleanCandidateRequired"])
        self.assertTrue(build["fullImmutableJarAndDistRequired"])
        self.assertTrue(build["incrementalClassOverlayForbidden"])
        self.assertTrue(build["dirtyWorkspaceArchiveForbidden"])
        self.assertTrue(build["externalSignedAttestationsRequired"])
        self.assertTrue(build["frontendExcelEntryCompiledEnabledRequired"])
        self.assertFalse(build["piiWorkbookIncluded"])
        self.assert_immutable_source_contract(manifest)
        rollback = manifest["rollbackPolicy"]
        self.assertFalse(rollback["deleteBusinessEvidence"])
        self.assertTrue(rollback["preserveGeneratedTasks"])
        self.assertTrue(rollback["preserveImportBatches"])
        self.assertTrue(rollback["preserveDocumentsSignaturesHashesAndAudit"])

    def test_migration_ledger_is_ordered_hash_pinned_and_mirrored(self) -> None:
        manifest = self.manifest
        self.assertEqual(ordered_list(MIGRATIONS), EXPECTED_MIGRATIONS)
        self.assertEqual(manifest["migrationCount"], 2)
        self.assertEqual(
            [item["file"] for item in manifest["migrations"]], EXPECTED_MIGRATIONS
        )
        for migration in manifest["migrations"]:
            canonical = ROOT / "sql" / migration["file"]
            self.assertEqual(migration["sha256"], sha256(canonical))
            self.assertEqual(migration["applyMode"], "manual-native-mysql")
            for key in ("creates", "backupTables", "requiresTables", "requiresColumns"):
                self.assertTrue(migration[key], f"{migration['file']}:{key}")
        for migration_name, relatives in manifest["sqlCopies"].items():
            copies = [ROOT / relative for relative in relatives]
            self.assertGreaterEqual(len(copies), 2)
            self.assertTrue(all(copy.is_file() for copy in copies))
            contents = [copy.read_bytes() for copy in copies]
            self.assertTrue(all(content == contents[0] for content in contents[1:]))
            item = next(value for value in manifest["migrations"]
                        if value["file"] == migration_name)
            self.assertEqual(item["sha256"], hashlib.sha256(contents[0]).hexdigest())

    def test_release_file_allowlist_is_sorted_unique_safe_and_existing(self) -> None:
        files = ordered_list(FILES)
        self.assertEqual(files, sorted(files))
        self.assertEqual(len(files), len(set(files)))
        self.assertGreater(len(files), 100)
        root = ROOT.resolve()
        for relative in files:
            pure = PurePosixPath(relative)
            self.assertFalse(pure.is_absolute(), relative)
            self.assertNotIn("..", pure.parts, relative)
            self.assertFalse(any(part in FORBIDDEN_PARTS for part in pure.parts), relative)
            self.assertFalse(any(relative.startswith(prefix) for prefix in FORBIDDEN_PREFIXES), relative)
            self.assertFalse(relative.endswith(FORBIDDEN_SUFFIXES), relative)
            self.assertNotIn("*", relative)
            resolved = (ROOT / relative).resolve()
            self.assertTrue(resolved.is_relative_to(root), relative)
            self.assertTrue(resolved.is_file(), relative)
            self.assertFalse((ROOT / relative).is_symlink(), relative)
        forbidden_exact = {
            "erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteFileService.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaOnboardPackageFact.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java",
            "erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysUserListVo.java",
            "erp-modules/erp-system/src/test/java/com/erp/system/domain/vo/SysUserListVoJsonTest.java",
            "erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue",
            "erp-ui/src/utils/signDictionary.js",
            "sql/erp_oa_sign_onboard_open_guard_20260717.sql",
            "sql/erp_system_user_push_delivery_20260717.sql",
        }
        self.assertTrue(forbidden_exact.isdisjoint(files))
        self.assertFalse(any("SysUserPushDelivery" in relative for relative in files))
        self.assertFalse(any("aliyun" in relative.lower() for relative in files))

    def test_required_cross_module_boundary_is_present(self) -> None:
        files = set(ordered_list(FILES))
        required = {
            "docker/.env.example",
            "docker/docker-compose.ecs-host.yml",
            "docker/docker-compose.yml",
            "docker/mysql/bootstrap-files.list",
            "docs/releases/evidence/20260718-onboard-contract-excel-evidence.json",
            "erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteUserService.java",
            "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/ReviewedSignProfileSupplement.java",
            "erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/SignScopeHeaderUtils.java",
            "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java",
            "erp-common/erp-common-security/src/test/java/com/erp/common/security/aspect/IdempotentSubmitAspectTest.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignOnboardImportController.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardDataRequestService.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardExcelParser.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardGenerationService.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOnboardImportService.java",
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskBatchSendService.java",
            "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignOnboardImportControllerTest.java",
            "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java",
            "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaOnboardSignEventFactoryTest.java",
            "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskBatchSendServiceTest.java",
            "erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSigningProfileSupplementController.java",
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSigningProfileSupplementServiceImpl.java",
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java",
            "erp-ui/package.json",
            "erp-ui/scripts/validate-onboard-release-build.cjs",
            "erp-ui/scripts/write-onboard-release-provenance.cjs",
            "erp-ui/src/settings.js",
            "erp-ui/src/views/hr/components/HrSignDataImportDialog.vue",
            "erp-ui/src/views/mobile/onboardData/index.vue",
            "erp-ui/test/onboardReleaseProvenance.test.js",
            "pom.xml",
            "scripts/contract/prepare_onboard_templates_20260718.py",
            "scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh",
            "scripts/remote_publish_beijing_sign_templates_plans_20260718.sh",
            "scripts/test_verify_onboard_contract_excel_artifacts.py",
            "scripts/test_verify_onboard_contract_excel_attestations.py",
            "scripts/test_verify_onboard_contract_excel_gate_evidence.py",
            "scripts/test_verify_onboard_contract_excel_readiness.py",
            "scripts/verify_onboard_contract_excel_artifacts.py",
            "scripts/verify_onboard_contract_excel_attestations.py",
            "scripts/verify_onboard_contract_excel_gate_evidence.py",
            "scripts/verify_onboard_contract_excel_readiness.py",
            "sql/erp_oa_sign_onboard_import_20260718.sql",
            "sql/erp_system_sign_profile_supplement_20260718.sql",
        }
        self.assertTrue(required.issubset(files), sorted(required - files))

    def test_release_shell_uses_one_readiness_orchestrator_and_all_static_contracts(self) -> None:
        source = (
            ROOT / "scripts/verify-onboard-contract-excel-release.sh"
        ).read_text(encoding="utf-8")
        for static_contract in (
            "test_verify_onboard_contract_excel_artifacts.py",
            "test_verify_onboard_contract_excel_attestations.py",
            "test_verify_onboard_contract_excel_gate_evidence.py",
            "test_verify_onboard_contract_excel_readiness.py",
            "onboardReleaseProvenance.test.js",
        ):
            self.assertIn(static_contract, source)
        readiness = source.split("if [[ \"$MODE\" == '--readiness' ]]; then", 1)[1]
        self.assertEqual(readiness.count("verify_onboard_contract_excel_readiness.py"), 1)
        self.assertNotIn("verify_onboard_contract_excel_artifacts.py", readiness)
        self.assertNotIn("verify_onboard_contract_excel_attestations.py", readiness)

    def test_deleted_old_entry_boundary_is_exact_and_absent(self) -> None:
        deleted = ordered_list(DELETED)
        self.assertEqual(deleted, sorted(deleted))
        self.assertEqual(len(deleted), len(set(deleted)))
        self.assertEqual(len(deleted), 10)
        self.assertIn(
            "erp-ui/src/views/hr/components/HrOnboardContractBatchDialog.vue", deleted
        )
        self.assertIn(
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/"
            "OaExistingEmployeeOnboardService.java",
            deleted,
        )
        for relative in deleted:
            self.assertFalse((ROOT / relative).exists(), relative)
        self.assertTrue(set(deleted).isdisjoint(ordered_list(FILES)))

    def test_mixed_tracked_paths_require_hunk_reassembly(self) -> None:
        mixed = ordered_list(MIXED)
        files = set(ordered_list(FILES))
        self.assertEqual(mixed, sorted(mixed))
        self.assertEqual(len(mixed), len(set(mixed)))
        self.assertGreater(len(mixed), 50)
        self.assertTrue(set(mixed).issubset(files))
        self.assertIn("docker/mysql/bootstrap-files.list", mixed)
        self.assertIn(
            "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/"
            "OaSignPackageServiceImpl.java",
            mixed,
        )
        self.assertIn("erp-ui/src/views/hr/components/HrEmployeeList.vue", mixed)
        self.assertIn(
            "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/"
            "IdempotentSubmitAspect.java",
            mixed,
        )
        self.assertIn(
            "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/"
            "OaTodoMapperBindingTest.java",
            mixed,
        )

    def test_old_preview_initiate_are_absent_but_batch_send_is_preserved(self) -> None:
        controller = (
            ROOT / "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/"
            "OaSignTaskController.java"
        ).read_text(encoding="utf-8")
        onboard_controller = (
            ROOT / "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/"
            "OaSignOnboardImportController.java"
        ).read_text(encoding="utf-8")
        frontend_api = (ROOT / "erp-ui/src/api/oa/signTask.js").read_text(encoding="utf-8")
        source = controller + onboard_controller + frontend_api
        self.assertNotIn("onboard/batch/preview", source)
        self.assertNotIn("onboard/batch/initiate", source)
        self.assertNotIn("previewOnboardSignTaskBatch", frontend_api)
        self.assertNotIn("initiateOnboardSignTaskBatch", frontend_api)
        self.assertIn('@PostMapping("/batch/send")', controller)
        self.assertIn("/oa/signTask/batch/send", frontend_api)

    def test_oa_migration_installs_fail_closed_open_task_guard(self) -> None:
        source = (ROOT / "sql" / OA_MIGRATION).read_text(encoding="utf-8")
        normalized = " ".join(source.split()).upper()
        duplicate_error = "DUPLICATE OPEN ONBOARD TASKS MUST BE RESOLVED"
        add_column = "ADD COLUMN OPEN_ONBOARD_EMPLOYEE_ID"
        self.assertIn(duplicate_error, normalized)
        self.assertIn("SIGNAL SQLSTATE '45000'", normalized)
        self.assertIn(add_column, normalized)
        self.assertLess(normalized.index(duplicate_error), normalized.index(add_column))
        self.assertIn("GENERATED ALWAYS AS", normalized)
        self.assertIn("ADD UNIQUE INDEX UK_OA_SIGN_TASK_OPEN_ONBOARD_EMPLOYEE", normalized)
        self.assertNotIn("UPDATE OA_SIGN_TASK", normalized)
        self.assertNotIn("DELETE FROM OA_SIGN_TASK", normalized)

    def test_bootstrap_contains_once_and_respects_new_dependencies(self) -> None:
        bootstrap = ordered_list(ROOT / "docker/mysql/bootstrap-files.list")
        self.assertEqual(bootstrap.count(OA_MIGRATION), 1)
        self.assertEqual(bootstrap.count(SYSTEM_MIGRATION), 1)
        self.assertLess(bootstrap.index("erp_oa_sign_task_center_20260711.sql"),
                        bootstrap.index(OA_MIGRATION))
        self.assertLess(bootstrap.index("erp_oa_sign_package_20260702.sql"),
                        bootstrap.index(OA_MIGRATION))
        self.assertLess(bootstrap.index("erp_oa_sign_plan_20260706.sql"),
                        bootstrap.index(OA_MIGRATION))
        self.assertLess(bootstrap.index("erp_user_employee_profile_20260706.sql"),
                        bootstrap.index(SYSTEM_MIGRATION))
        verifier = (ROOT / "scripts/verify-docker-mysql-bootstrap.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "require_before erp_user_employee_profile_20260706.sql "
            "erp_system_sign_profile_supplement_20260718.sql",
            verifier,
        )
        self.assertIn(
            "require_before erp_oa_sign_plan_20260706.sql "
            "erp_oa_sign_onboard_import_20260718.sql",
            verifier,
        )

    def test_historical_approval_manifest_keeps_original_feature_defaults(self) -> None:
        self.assertTrue(all(value is False for value in self.manifest["featureDefaults"].values()))

    def test_excel_import_stays_enabled_while_automation_remains_opt_in(self) -> None:
        oa = (ROOT / "erp-modules/erp-oa/src/main/resources/bootstrap.yml").read_text(
            encoding="utf-8"
        )
        system = (
            ROOT / "erp-modules/erp-system/src/main/resources/bootstrap.yml"
        ).read_text(encoding="utf-8")
        self.assertRegex(
            oa,
            r"(?s)excel-import:\s*\n\s*enabled:\s*\$\{OA_SIGN_EXCEL_IMPORT_ENABLED:true\}",
        )
        for name in ("emergency-create", "expiry", "reminder"):
            self.assertRegex(
                oa,
                rf"(?s){re.escape(name)}:\s*\n\s*enabled:\s*\$\{{[A-Z_]+:false\}}",
            )
        self.assertRegex(
            system,
            r"(?s)lifecycle-automation:\s*\n\s*enabled:\s*"
            r"\$\{HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED:false\}",
        )
        def dotenv_values(path: Path, key: str) -> list[str]:
            values = []
            for raw in path.read_text(encoding="utf-8").splitlines():
                line = raw.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                name, value = line.split("=", 1)
                if name.strip() == key:
                    values.append(value.strip())
            return values

        for environment in ("development", "staging", "production"):
            path = ROOT / f"erp-ui/.env.{environment}"
            self.assertEqual(
                dotenv_values(path, "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED"), ["true"]
            )
        docker_env = ROOT / "docker/.env.example"
        self.assertEqual(
            dotenv_values(docker_env, "OA_SIGN_EXCEL_IMPORT_ENABLED"), ["true"]
        )
        self.assertEqual(
            dotenv_values(docker_env, "HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED"),
            ["false"],
        )
        expected_oa = "OA_SIGN_EXCEL_IMPORT_ENABLED: " + "$" + "{OA_SIGN_EXCEL_IMPORT_ENABLED:-true}"
        expected_lifecycle = "HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED: " + "$" + "{HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED:-false}"
        for relative in ("docker/docker-compose.yml", "docker/docker-compose.ecs-host.yml"):
            compose = (ROOT / relative).read_text(encoding="utf-8")
            oa_section = compose.split("\n  erp-modules-oa:\n", 1)[1].split("\n  erp-modules-", 1)[0]
            system_section = compose.split("\n  erp-modules-system:\n", 1)[1].split("\n  erp-modules-", 1)[0]
            self.assertEqual(compose.count(expected_oa), 1)
            self.assertEqual(compose.count(expected_lifecycle), 1)
            self.assertIn(expected_oa, oa_section)
            self.assertIn(expected_lifecycle, system_section)
            rendered = subprocess.run(
                [
                    "docker", "compose", "--env-file", str(docker_env),
                    "-f", str(ROOT / relative), "config", "--format", "json",
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            services = json.loads(rendered.stdout)["services"]
            oa_effective = [
                (name, service.get("environment", {}).get("OA_SIGN_EXCEL_IMPORT_ENABLED"))
                for name, service in services.items()
                if "OA_SIGN_EXCEL_IMPORT_ENABLED" in service.get("environment", {})
            ]
            lifecycle_effective = [
                (
                    name,
                    service.get("environment", {}).get(
                        "HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED"
                    ),
                )
                for name, service in services.items()
                if "HR_SIGN_LIFECYCLE_AUTOMATION_ENABLED" in service.get("environment", {})
            ]
            self.assertEqual(oa_effective, [("erp-modules-oa", "true")])
            self.assertEqual(
                lifecycle_effective, [("erp-modules-system", "false")]
            )

        release_builder = (ROOT / "scripts/release/build-release.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true \\", release_builder)

    def test_template_candidates_are_external_disabled_and_hash_pinned(self) -> None:
        candidates = self.manifest["templateCandidates"]
        self.assertEqual(candidates["count"], 9)
        self.assertFalse(candidates["applicationArtifactIncluded"])
        self.assertFalse(candidates["registrationPayloadGenerated"])
        self.assertEqual(candidates["activationStatus"], "disabled")
        items = candidates["items"]
        self.assertEqual(len(items), 9)
        self.assertEqual(len({item["file"] for item in items}), 9)
        self.assertTrue(
            all(re.fullmatch(r"[0-9a-f]{64}", item["sha256"]) for item in items)
        )
        candidate_root = ROOT / "output/contract-template-review/20260718-v5/candidates"
        if candidate_root.is_dir():
            for item in items:
                source = candidate_root / item["file"]
                self.assertTrue(source.is_file(), source)
                self.assertEqual(item["size"], source.stat().st_size)
                self.assertEqual(item["sha256"], sha256(source))

    def test_external_gate_evidence_index_is_exact_and_redacted(self) -> None:
        evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
        self.assertEqual(evidence["schemaVersion"], 2)
        self.assertEqual(evidence["releaseId"], "onboard-contract-excel-20260718")
        self.assertFalse(evidence["containsPii"])
        self.assertEqual(
            set(evidence),
            {
                "schemaVersion", "releaseId", "status", "containsPii",
                "binding", "gates",
            },
        )
        self.assertEqual(
            set(evidence["binding"]),
            {
                "candidateCommit", "approvedPatchSha256",
                "approvedSourceManifestSha256", "sourceApprovalSha256",
                "oaJarSha256", "systemJarSha256", "frontendTreeSha256",
            },
        )
        self.assertTrue(all(value is None for value in evidence["binding"].values()))
        self.assertEqual(set(evidence["gates"]), EXPECTED_EXTERNAL_GATES)
        self.assertEqual(evidence["status"], "evidence-pending")
        for item in evidence["gates"].values():
            self.assertEqual(
                set(item),
                {
                    "status", "evidenceId", "evidenceSha256", "subject",
                    "approvedBy", "approvedRole", "approvedAt", "environment",
                },
            )
            self.assertEqual(item["status"], "pending")
            self.assertTrue(all(
                item[key] is None
                for key in (
                    "evidenceId", "evidenceSha256", "subject", "approvedBy",
                    "approvedRole", "approvedAt", "environment",
                )
            ))

    def test_new_migrations_are_not_written_back_to_old_release_ledgers(self) -> None:
        for relative in (
            "scripts/contract-signing-migrations-20260716.list",
            "scripts/contract-signing-release-files-20260716.list",
            "scripts/contract-signing-release-20260716.json",
        ):
            source = (ROOT / relative).read_text(encoding="utf-8")
            self.assertNotIn(OA_MIGRATION, source)
            self.assertNotIn(SYSTEM_MIGRATION, source)
        self.assertNotIn(
            "erp_oa_sign_onboard_open_guard_20260717.sql", ordered_list(MIGRATIONS)
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
