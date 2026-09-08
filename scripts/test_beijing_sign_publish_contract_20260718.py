import re
import subprocess
import textwrap
import unittest
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / "scripts/remote_audit_beijing_sign_publish_readonly_20260718.sh"
PUBLISH = ROOT / "scripts/remote_publish_beijing_sign_templates_plans_20260718.sh"
RUNBOOK = ROOT / "docs/runbooks/beijing-sign-template-plan-ui-publish-20260718.md"


class BeijingSignPublishContractTest(unittest.TestCase):
    def test_audit_discovers_reviewed_templates_without_guessing_ids(self):
        source = AUDIT.read_text(encoding="utf-8")

        self.assertIn("template_version='20260718-v5-draft'", source)
        self.assertIn("def template_key", source)
        self.assertNotIn("template_id IN (7,11,12)", source)
        for template_type in (
            "ONBOARD_COMMITMENT",
            "ONBOARD_LABOR_CONTRACT",
            "ONBOARD_HANDBOOK_RECEIPT",
            "ONBOARD_SALARY_CONFIRM",
            "ONBOARD_SERVICE_CONTRACT",
            "ONBOARD_SERVICE_RECEIPT",
            "ONBOARD_CONFIDENTIAL_NONCOMPETE",
            "ONBOARD_MINOR_NONSTUDENT_DECLARATION",
        ):
            self.assertIn(template_type, source)

    def test_audit_enforces_runtime_package_gate(self):
        source = AUDIT.read_text(encoding="utf-8")

        self.assertRegex(
            source,
            r'(?s)"B3".*?\("ONBOARD_COMMITMENT","ONBOARD_SERVICE_CONTRACT",'
            r'"ONBOARD_SERVICE_RECEIPT",\s*"ONBOARD_CONFIDENTIAL_NONCOMPETE",'
            r'"ONBOARD_MINOR_NONSTUDENT_DECLARATION"\)',
        )
        self.assertGreaterEqual(
            source.count(
                '("ONBOARD_COMMITMENT","ONBOARD_LABOR_CONTRACT",'
                '"ONBOARD_HANDBOOK_RECEIPT",'
            ),
            3,
        )
        self.assertIn(
            '("ONBOARD_COMMITMENT","ONBOARD_SERVICE_CONTRACT",'
            '"ONBOARD_SERVICE_RECEIPT",',
            source,
        )
        self.assertIn(
            'snapshot_condition.get("postLevelScope") != "7级及以上"',
            source,
        )

    def test_release_plans_are_global_and_cover_the_real_five_routes(self):
        source = AUDIT.read_text(encoding="utf-8")
        runbook = RUNBOOK.read_text(encoding="utf-8")

        self.assertNotIn("shop_dept_id IN (1171,1176,1157)", source)
        for metric in (
            "productionActiveTargetRouteCount",
            "productionTargetRouteConflictCount",
            "productionNonGlobalTargetPlanCount",
            "isolatedUatPlanInProductionCount",
            "productionUnexpectedActiveRouteCount",
            "productionInvalidActiveRuleCount",
        ):
            self.assertIn(metric, source)
        for route, name in (
            ("A4", "入职-A4-劳动合同-无社保-2至4级"),
            ("A5", "入职-A5-劳动合同-无社保-5至6级"),
            ("A1", "入职-A1-劳动合同-有社保-2至4级"),
            ("B1", "入职-B1-在校实习生劳务合同-2至4级"),
            ("B3", "入职-B3-退休返聘劳务合同-7至9级"),
        ):
            with self.subTest(route=route):
                self.assertIn(name, source)
                self.assertIn(name, runbook)
                self.assertRegex(source, re.escape(f'("{name}",0,"{route}"'))
        self.assertIn("A4=19、A5=5、A1=1、B1=1、B3=1", runbook)
        self.assertIn("107 +", runbook)
        self.assertIn("A3 仅用于独立 `isolated-uat` 环境", runbook)
        self.assertNotIn("北京柏悦-A4", source)
        self.assertNotIn("万达文华-A4", source)

    def test_service_fact_inputs_are_role_based_and_do_not_name_employees(self):
        source = AUDIT.read_text(encoding="utf-8")
        runbook = RUNBOOK.read_text(encoding="utf-8")

        for variable in ("B1_INSURANCE_TYPE_CODE", "B3_INSURANCE_TYPE_CODE"):
            self.assertIn(variable, source)
            self.assertIn(variable, runbook)
        for legacy in ("LI_MAN_INSURANCE_TYPE_CODE", "SU_YUYU_INSURANCE_TYPE_CODE"):
            self.assertNotIn(legacy, source)
            self.assertNotIn(legacy, runbook)

    def test_audit_route_derivation_rejects_explicit_and_legacy_conflicts(self):
        source = AUDIT.read_text(encoding="utf-8")
        match = re.search(
            r"\n    def derive_route\(.*?(?=\n    plan_rows =)",
            source,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(match)
        namespace: dict[str, Any] = {"Any": Any}
        exec(textwrap.dedent(match.group(0)), namespace)
        derive_route = namespace["derive_route"]

        self.assertEqual(
            derive_route(
                {
                    "routeCode": "A3",
                    "contractTypeCode": "LABOR_CONTRACT",
                    "socialTypeCode": "SOCIAL_INSURED",
                    "jobGradeBand": "7-8",
                    "employmentType": "劳动合同",
                    "socialType": "有社保",
                    "postLevel": "7",
                }
            ),
            "A3",
        )
        self.assertIsNone(
            derive_route(
                {
                    "routeCode": "A4",
                    "contractTypeCode": "LABOR_CONTRACT",
                    "socialTypeCode": "SOCIAL_INSURED",
                    "jobGradeBand": "2-4",
                }
            )
        )
        self.assertIsNone(
            derive_route(
                {
                    "routeCode": "Z9",
                    "employmentType": "劳动合同",
                    "socialType": "有社保",
                    "postLevel": "2",
                }
            )
        )
        self.assertIsNone(derive_route({"routeCode": 123}))
        valid_a4 = {
            "routeCode": "A4",
            "contractTypeCode": "LABOR_CONTRACT",
            "socialTypeCode": "SOCIAL_UNINSURED",
            "jobGradeBand": "2-4",
        }
        self.assertIsNone(derive_route({**valid_a4, "servicePersonType": 123}))
        self.assertIsNone(derive_route({**valid_a4, "insuranceType": 123}))

        release_metrics = namespace["active_plan_release_metrics"]

        def version(version_id, shop_dept_id, rule):
            return {
                "versionId": version_id,
                "shopDeptId": shop_dept_id,
                "publishStatus": "PUBLISHED",
                "matchingStatus": "ENABLED",
                "ruleJson": rule,
                "planName": f"arbitrary-{version_id}",
            }

        metrics = release_metrics(
            [
                version(
                    1,
                    0,
                    {
                        "routeCode": "A3",
                        "contractTypeCode": "LABOR_CONTRACT",
                        "socialTypeCode": "SOCIAL_INSURED",
                        "jobGradeBand": "7-9",
                    },
                ),
                version(
                    2,
                    0,
                    {
                        "routeCode": "A2",
                        "contractTypeCode": "LABOR_CONTRACT",
                        "socialTypeCode": "SOCIAL_INSURED",
                        "jobGradeBand": "5-6",
                    },
                ),
                version(3, 1176, valid_a4),
                version(
                    4,
                    0,
                    {
                        "routeCode": "A4",
                        "contractTypeCode": "LABOR_CONTRACT",
                        "socialTypeCode": "SOCIAL_INSURED",
                        "jobGradeBand": "2-4",
                    },
                ),
            ],
            ("A4", "A5", "A1", "B1", "B3"),
            lambda value: value,
        )
        self.assertEqual(metrics["isolatedUatPlanInProductionCount"], 1)
        self.assertEqual(metrics["unexpectedGlobalActiveRouteCount"], 2)
        self.assertEqual(metrics["nonGlobalActivePlanCount"], 1)
        self.assertEqual(metrics["invalidActiveRuleCount"], 1)

        conflicts = release_metrics(
            [version(5, 0, valid_a4), version(6, 0, valid_a4)],
            ("A4", "A5", "A1", "B1", "B3"),
            lambda value: value,
        )
        self.assertEqual(conflicts["targetRouteConflictCount"], 1)

    def test_publish_automation_remains_disabled_and_runbook_is_fail_closed(self):
        publish = PUBLISH.read_text(encoding="utf-8")
        runbook = RUNBOOK.read_text(encoding="utf-8")

        self.assertIn("SIGN_PUBLISH_AUTOMATION_DISABLED", publish)
        self.assertIn("exit 40", publish)
        self.assertNotIn("template_id=", publish)
        self.assertIn("禁止猜测或手填 ID", runbook)
        self.assertIn("任意劳动或劳务方案缺承诺书", runbook)
        self.assertIn("B3 缺保密竞业", runbook)
        self.assertIn("shopDeptId` 必须为保留值 `0`", runbook)

    def test_shell_and_embedded_python_are_syntactically_valid(self):
        subprocess.run(["bash", "-n", str(AUDIT)], check=True)
        subprocess.run(["bash", "-n", str(PUBLISH)], check=True)
        source = AUDIT.read_text(encoding="utf-8")
        blocks = re.findall(r"<<'PY'\n(.*?)\nPY", source, flags=re.DOTALL)
        self.assertGreaterEqual(len(blocks), 4)
        for index, block in enumerate(blocks):
            compile(block, f"audit-heredoc-{index}.py", "exec")


if __name__ == "__main__":
    unittest.main()
