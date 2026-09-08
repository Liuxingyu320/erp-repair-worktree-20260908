#!/usr/bin/env python3

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class NewBusinessReleaseBuildContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gate = (ROOT / "scripts/verify-new-business-release.sh").read_text(
            encoding="utf-8"
        )
        cls.runbook = (ROOT / "docs/ALIYUN_ECS_DEPLOYMENT_RUNBOOK.md").read_text(
            encoding="utf-8"
        )
        cls.archive = (ROOT / "scripts/verify_new_business_release_archive.py").read_text(
            encoding="utf-8"
        )

    def test_gate_pins_supported_toolchains(self):
        self.assertIn('MAVEN="$ROOT_DIR/mvnw"', self.gate)
        self.assertIn("repository Maven 3.9.16 is required", self.gate)
        self.assertIn('CURRENT_NODE_VERSION" == "$PINNED_NODE_VERSION', self.gate)
        self.assertIn('CURRENT_NPM_VERSION" == "$PINNED_NPM_VERSION', self.gate)
        self.assertIn("Maven must run on Java 17", self.gate)
        self.assertNotIn("command -v mvn", self.gate)
        self.assertNotIn("mvn -f", self.gate)

    def test_gate_builds_and_records_every_backend_service(self):
        self.assertIn("complete backend regression and candidate jar build", self.gate)
        self.assertIn('"$MAVEN" -T 1C -f "$ROOT_DIR/pom.xml" clean verify', self.gate)
        self.assertIn("erp-modules/erp-approval/target/erp-modules-approval.jar", self.gate)
        self.assertIn("REQUIRED_BACKEND_JARS", self.archive)
        self.assertIn("erp-modules-approval.jar", self.archive)

    def test_gate_injects_one_commit_into_backend_and_frontend(self):
        self.assertIn('-Dbuild.commit="$BUILD_COMMIT"', self.gate)
        self.assertIn('VUE_APP_BUILD_COMMIT="$BUILD_COMMIT"', self.gate)
        self.assertIn('VUE_APP_BUILD_TIME="$BUILD_TIME"', self.gate)

    def test_full_gate_requires_explicit_build_time(self):
        self.assertIn("ERP_NEW_BUSINESS_BUILD_TIME is required", self.gate)
        self.assertIn('"build": {', self.gate)

    def test_runbook_matches_the_release_gate(self):
        self.assertIn('export BUILD_COMMIT="$(git rev-parse HEAD)"', self.runbook)
        self.assertIn('./mvnw clean verify -Dbuild.commit="$BUILD_COMMIT"', self.runbook)
        self.assertIn('process.versions.node !== "22.23.1"', self.runbook)
        self.assertIn('test "$(npm --version)" = "10.9.8"', self.runbook)
        self.assertIn('VUE_APP_BUILD_TIME="$BUILD_TIME"', self.runbook)


if __name__ == "__main__":
    unittest.main()
