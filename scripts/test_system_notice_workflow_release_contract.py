#!/usr/bin/env python3

import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/system-notice-workflow-release-20260714.json"
FILE_LIST = ROOT / "scripts/system-notice-workflow-release-files-20260714.list"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class SystemNoticeWorkflowReleaseContractTest(unittest.TestCase):
    def test_manifest_is_fail_closed_and_packaged_files_are_exact(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("system-notice-workflow-20260714", manifest["releaseId"])
        self.assertEqual("development", manifest["status"])
        self.assertEqual("automatic", manifest["executionPolicy"])
        self.assertEqual(
            ["system-management-ux-v2-20260714"],
            manifest["prerequisiteReleaseIds"],
        )
        self.assertEqual(False, manifest["featureFlag"]["default"])
        self.assertEqual(
            "feature.system.notice-workflow.enabled", manifest["featureFlag"]["key"]
        )

        migration = manifest["migrations"][0]
        source = ROOT / manifest["sourceDirectory"] / migration["file"]
        packaged = ROOT / manifest["deployDirectory"] / migration["file"]
        self.assertEqual(migration["sha256"], sha256(source))
        self.assertEqual(migration["sha256"], sha256(packaged))

        rollback = manifest["rollback"]
        rollback_source = ROOT / "sql" / rollback["file"]
        rollback_packaged = ROOT / manifest["deployDirectory"] / rollback["file"]
        self.assertEqual(rollback["sha256"], sha256(rollback_source))
        self.assertEqual(rollback["sha256"], sha256(rollback_packaged))

        self.assertEqual(
            MANIFEST.read_bytes(),
            (ROOT / "docker/release" / MANIFEST.name).read_bytes(),
        )
        migration_list = ROOT / manifest["migrationList"]
        self.assertEqual(
            migration_list.read_bytes(),
            (ROOT / "docker/release" / migration_list.name).read_bytes(),
        )

    def test_forward_and_rollback_sql_enforce_workflow_boundaries(self):
        forward = (ROOT / "sql/erp_system_notice_workflow_20260714.sql").read_text(
            encoding="utf-8"
        ).lower()
        rollback = (
            ROOT / "sql/erp_system_notice_workflow_rollback_20260714.sql"
        ).read_text(encoding="utf-8").lower()

        for column in (
            "lifecycle_status",
            "audience_type",
            "scheduled_publish_time",
            "published_time",
            "expire_time",
            "version",
            "previous_notice_id",
        ):
            self.assertIn(column, forward)
        self.assertIn("create table if not exists sys_notice_audience", forward)
        self.assertIn("create table if not exists sys_notice_recipient", forward)
        self.assertIn("where lifecycle_status is null", forward)
        self.assertIn("'legacy_migration'", forward)
        self.assertIn("feature.system.notice-workflow.enabled", forward)
        self.assertIn("'false'", forward)
        self.assertIn("system:notice:publish", forward)
        self.assertNotIn("insert into sys_role_menu", forward)

        self.assertIn("recipient_source <> 'legacy_migration'", rollback)
        self.assertIn("lifecycle_status in ('draft', 'scheduled')", rollback)
        self.assertIn("if new_workflow_rows > 0 then", rollback)
        self.assertIn("signal sqlstate '45000'", rollback)
        self.assertIn("disable the feature flag instead", rollback)

    def test_release_file_list_is_complete_unique_and_safe(self):
        entries = [
            line.strip()
            for line in FILE_LIST.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]
        self.assertEqual(len(entries), len(set(entries)))
        self.assertTrue(entries)
        self.assertFalse(
            [entry for entry in entries if Path(entry).is_absolute() or ".." in Path(entry).parts]
        )
        self.assertFalse([entry for entry in entries if not (ROOT / entry).is_file()])
        required = {
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java",
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticePublishScheduler.java",
            "erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeRecipientMapper.xml",
            "erp-ui/src/views/system/notice/index.vue",
            "erp-ui/src/layout/components/HeaderNotice/DetailView.vue",
            "erp-ui/test/systemNoticeWorkflowUx.test.js",
            "scripts/verify-system-notice-workflow-native-mysql.sh",
            "scripts/verify-system-management-browser.sh",
            "sql/erp_system_notice_workflow_20260714.sql",
            "sql/erp_system_notice_workflow_rollback_20260714.sql",
        }
        self.assertTrue(required.issubset(entries))

    def test_runtime_gates_are_native_only_and_diagnostics_are_removed(self):
        native_gate = (
            ROOT / "scripts/verify-system-notice-workflow-native-mysql.sh"
        ).read_text(encoding="utf-8")
        browser_gate = (ROOT / "scripts/verify-system-management-browser.sh").read_text(
            encoding="utf-8"
        )
        combined = (native_gate + browser_gate).lower()
        self.assertNotIn("testcontainers", combined)
        self.assertNotIn("docker run", combined)
        self.assertNotIn("notice_save_debug", combined)
        self.assertNotIn("notice_focus_trace", combined)
        self.assertNotIn("notice_publish_debug", combined)
        self.assertIn("--browser chrome", browser_gate)
        self.assertIn("ERP_SYSTEM_BROWSER_PHASE", browser_gate)


if __name__ == "__main__":
    unittest.main()
