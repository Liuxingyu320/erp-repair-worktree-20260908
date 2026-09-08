#!/usr/bin/env python3
"""Prevent transactional business services from reintroducing approval starts."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
BUSINESS_SOURCE_ROOTS = (
    ROOT / "erp-modules/erp-inventory/src/main/java",
    ROOT / "erp-modules/erp-oa/src/main/java",
    ROOT / "erp-modules/erp-system/src/main/java",
)
ALLOWED_DISPATCHERS = {
    "InvTransferApprovalStartDispatcher.java",
    "InvStockCheckApprovalStartDispatcher.java",
    "OaPurchaseApprovalStartDispatcher.java",
    "OaReimbursementApprovalStartDispatcher.java",
    "HrHealthCertificateApprovalStartDispatcher.java",
}
REPLAY_CONTROLLERS = {
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalStartOutboxController.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockCheckApprovalStartOutboxController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseApprovalStartOutboxController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaReimbursementApprovalStartOutboxController.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrHealthCertificateApprovalStartOutboxController.java",
}
REMOTE_START = re.compile(
    r"\b(?:approvalService|remoteApprovalService)\s*\.\s*start\s*\("
)
OPS_PERMISSIONS = (
    "inv:transfer:approvalStartOutbox:list",
    "inv:transfer:approvalStartOutbox:replay",
    "inv:stockCheck:approvalStartOutbox:list",
    "inv:stockCheck:approvalStartOutbox:replay",
    "oa:purchase:approvalStartOutbox:list",
    "oa:purchase:approvalStartOutbox:replay",
    "oa:reimbursement:approvalStartOutbox:list",
    "oa:reimbursement:approvalStartOutbox:replay",
    "hr:healthCertificate:approvalStartOutbox:list",
    "hr:healthCertificate:approvalStartOutbox:replay",
)


def approval_start_callers() -> dict[str, int]:
    callers: dict[str, int] = {}
    for source_root in BUSINESS_SOURCE_ROOTS:
        for path in source_root.rglob("*.java"):
            count = len(REMOTE_START.findall(path.read_text(encoding="utf-8")))
            if count:
                callers[str(path.relative_to(ROOT))] = count
    return callers


class ApprovalStartOutboxContractTest(unittest.TestCase):
    def test_only_outbox_dispatchers_can_call_remote_approval_start(self):
        callers = approval_start_callers()
        unexpected = {
            path: count
            for path, count in callers.items()
            if Path(path).name not in ALLOWED_DISPATCHERS
        }
        self.assertEqual(
            {},
            unexpected,
            "approval start must run after commit through a durable outbox dispatcher",
        )

    def test_every_business_flow_has_exactly_one_dispatcher_call(self):
        callers = approval_start_callers()
        by_name = {Path(path).name: count for path, count in callers.items()}
        self.assertEqual(
            {name: 1 for name in ALLOWED_DISPATCHERS},
            by_name,
            "missing or duplicate approval-start dispatch paths",
        )

    def test_manual_replay_attempts_dispatch_immediately(self):
        for relative in REPLAY_CONTROLLERS:
            with self.subTest(controller=relative):
                source = (ROOT / relative).read_text(encoding="utf-8")
                replay = source[source.index("replay(") :]
                replay_call = replay.index("replayFailed(")
                dispatch_call = replay.index("dispatchOneNow(")
                self.assertLess(
                    replay_call,
                    dispatch_call,
                    "manual replay must commit the reset before dispatching",
                )

    def test_outbox_ops_permissions_default_only_to_admin(self):
        seed = (ROOT / "sql/erp_unified_approval_seed_20260716.sql").read_text(
            encoding="utf-8"
        )
        reimbursement = (
            ROOT / "sql/erp_oa_reimbursement_20260730.sql"
        ).read_text(encoding="utf-8")
        start = seed.index(
            "-- Approval-start outbox operations use dedicated permissions."
        )
        end = seed.index(
            "-- End approval-start outbox operations permissions.", start
        )
        reimbursement_start = reimbursement.index(
            "-- Reimbursement approval-start outbox operations use dedicated permissions."
        )
        reimbursement_end = reimbursement.index(
            "-- End reimbursement approval-start outbox operations permissions.",
            reimbursement_start,
        )
        ops_blocks = (seed[start:end], reimbursement[
            reimbursement_start:reimbursement_end
        ])
        ops_block = "\n".join(ops_blocks)

        for permission in OPS_PERMISSIONS:
            with self.subTest(permission=permission):
                self.assertIn(permission, ops_block)
        self.assertIn("role_info.role_key = 'admin'", ops_block)
        self.assertIn("role_key = 'admin'", ops_block)
        for business_permission in (
            "inv:transfer:approve",
            "inv:stockCheck:approve",
            "oa:todo:approve",
            "oa:reimbursement:approve",
            "oa:reimbursement:finance:approve",
            "hr:healthCertificate:review",
        ):
            self.assertNotIn(
                business_permission,
                ops_block,
                "ops grants must never be derived from business approval roles",
            )

    def test_health_seed_rerun_preserves_admin_disabled_state(self):
        seed = (ROOT / "sql/erp_unified_approval_seed_20260716.sql").read_text(
            encoding="utf-8"
        )
        template_gate = seed[
            seed.index("SET @health_seed_template_promotable") :
            seed.index("INSERT INTO approval_rule", seed.index(
                "SET @health_seed_template_promotable"
            ))
        ]
        rule_gate = seed[
            seed.index("SET @health_seed_rule_activatable") :
            seed.index("SET @health_definition_snapshot")
        ]
        rule_update = seed[
            seed.index("UPDATE approval_rule\nSET rule_status = 'ACTIVE'") :
            seed.index("UPDATE approval_template", seed.index(
                "UPDATE approval_rule\nSET rule_status = 'ACTIVE'"
            ))
        ]
        template_update = seed[
            seed.index("UPDATE approval_template\nSET engine_mode = 'NATIVE'") :
            seed.index("-- Inventory native approval definitions.")
        ]

        self.assertIn("seed_template.engine_mode = 'LEGACY'", template_gate)
        self.assertIn("seed_template.template_status = 'ACTIVE'", template_gate)
        self.assertIn("seed_template.create_by = 'system'", template_gate)
        self.assertIn("seed_rule.rule_status = 'DRAFT'", rule_gate)
        self.assertIn("seed_rule.current_version_id IS NULL", rule_gate)
        self.assertIn("seed_rule.create_by = 'system'", rule_gate)
        self.assertIn("AND @health_seed_rule_activatable = 1", rule_update)
        self.assertIn(
            "AND @health_seed_template_promotable = 1", template_update
        )


if __name__ == "__main__":
    unittest.main()
