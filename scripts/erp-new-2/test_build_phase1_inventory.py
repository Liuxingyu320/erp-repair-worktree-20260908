from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path
from types import ModuleType


MODULE_PATH = Path(__file__).with_name("build_phase1_inventory.py")


def load_inventory_module() -> ModuleType:
    spec = importlib.util.spec_from_file_location("build_phase1_inventory", MODULE_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load inventory module: {MODULE_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


inventory = load_inventory_module()


def valid_override(**changes: str) -> dict[str, str]:
    row = {
        "old_page_source": "erp-ui/src/views/index.vue",
        "target_disposition": "重设计",
        "new_route": "/",
        "new_component": "erp-ui-next/src/pages/workbench/WorkbenchPage.vue",
        "automated_tests": "erp-ui-next/src/pages/workbench/WorkbenchPage.test.ts",
        "confirmation_status": "原型已验收，领域迁移待确认",
        "implementation_status": "部分实现",
        "target_evidence": "docs/ERP-NEW_2-阶段3原型验收记录-20260802.md",
        "notes": "Mock-only；真实运行时尚未接线。",
    }
    row.update(changes)
    return row


class TargetOverrideValidationTest(unittest.TestCase):
    def test_source_manifest_tracks_new_frontend_shell_evidence(self) -> None:
        sources = {row["source"] for row in inventory.build_source_manifest()}

        self.assertIn("erp-ui-next/src/app/shell/AppShell.vue", sources)
        self.assertIn("erp-ui-next/src/app/shell/ErpCommandPalette.vue", sources)
        self.assertIn("erp-ui-next/src/app/shell/navigation.test.ts", sources)
        self.assertIn("erp-ui-next/src/app/providers/gateways.ts", sources)
        self.assertIn("erp-ui-next/src/api/http/http-client.ts", sources)
        self.assertIn("erp-ui-next/src/domains/inventory/stock/stock.contract.ts", sources)
        self.assertIn(
            "erp-ui-next/src/domains/inventory/stock/stock-command.contract.ts",
            sources,
        )
        self.assertIn("erp-ui-next/src/pages/inventory/stock/StockWorkbenchPage.vue", sources)
        self.assertIn("erp-ui-next/src/pages/inventory/stock/StockDetailPage.vue", sources)
        self.assertIn(
            "erp-ui-next/src/pages/inventory/stock/StockMovementWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/stock/StockMovementWorkbenchPage.test.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/design-system/components/ErpConfirmationDialog.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/inventory/products/product-command.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/products/ProductEditorPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/products/ProductEditorPage.test.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/inventory/categories/category.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/categories/CategoryWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/categories/CategoryWorkbenchPage.test.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/inventory/suppliers/supplier.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/suppliers/SupplierWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/suppliers/SupplierWorkbenchPage.test.ts",
            sources,
        )
        self.assertIn("erp-ui-next/src/domains/inventory/purchase/purchase.contract.ts", sources)
        self.assertIn(
            "erp-ui-next/src/pages/inventory/purchase/PurchaseWorkbenchPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/domains/inventory/stock-check/stock-check.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/stock-check/StockCheckWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/inventory/report/report.contract.ts", sources
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/report/InventoryReportPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/domains/customers/service-card/service-card.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/customers/service-card/CustomerServiceCardPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/sales/orders/sales-order.contract.ts", sources
        )
        self.assertIn(
            "erp-ui-next/src/pages/sales/orders/SalesOrderWorkbenchPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/domains/sales/returns/sales-return.contract.ts", sources
        )
        self.assertIn(
            "erp-ui-next/src/pages/sales/returns/SalesReturnWorkbenchPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/domains/sales/delivery-notices/delivery-notice.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/sales/delivery-notices/DeliveryNoticeWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/oa/purchases/oa-purchase.contract.ts", sources
        )
        self.assertIn(
            "erp-ui-next/src/pages/oa/purchases/OaPurchaseWorkbenchPage.vue", sources
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/instances/approval-instance.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/instances/ApprovalInstanceWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/instances/approval-instance-detail.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/instances/approval-instance-admin.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/instances/ApprovalInstanceDetailPanel.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/instances/ApprovalAdminActionDialog.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/definitions/approval-definition.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/definitions/ApprovalDefinitionWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/validation/approval-validation.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/validation/approval-validation-command.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/validation/ApprovalValidationWorkbenchPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/validation/ApprovalValidationRunDialog.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/rules/approval-rule.contract.ts",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/rules/ApprovalRuleAuditPage.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/pages/approvals/rules/ApprovalRuleEditorPanel.vue",
            sources,
        )
        self.assertIn(
            "erp-ui-next/src/domains/approvals/rules/approval-rule-command.contract.ts",
            sources,
        )
        self.assertIn("erp-ui-next/src/domains/messages/message.contract.ts", sources)
        self.assertIn("erp-ui-next/src/platform/features/feature-catalog.ts", sources)
        self.assertIn("erp-ui-next/src/test-support/fixtures/messages.ts", sources)

    def test_repository_target_override_file_is_valid(self) -> None:
        rows = inventory.read_target_override_rows()
        indexed = inventory.index_target_overrides(rows)

        self.assertEqual(len(indexed), 36)
        self.assertIn(
            "erp-ui/src/views/approval/manage/components/AdminActionDialog.vue", indexed
        )
        self.assertIn(
            "erp-ui/src/views/approval/manage/components/FlowConfiguration.vue", indexed
        )
        self.assertIn(
            "erp-ui/src/views/approval/manage/components/RuntimeMonitor.vue", indexed
        )
        self.assertIn(
            "erp-ui/src/views/approval/manage/components/ValidationPanel.vue", indexed
        )
        self.assertIn(
            "erp-ui/src/views/approval/manage/components/RuleWizard.vue", indexed
        )
        self.assertIn("erp-ui/src/views/inventory/transfer/rules.vue", indexed)
        self.assertEqual(
            indexed["erp-ui/src/views/inventory/transfer/rules.vue"][
                "target_disposition"
            ],
            "合并",
        )
        self.assertIn(
            "已确认",
            indexed["erp-ui/src/views/inventory/transfer/rules.vue"][
                "confirmation_status"
            ],
        )
        self.assertIn("erp-ui/src/views/inventory/purchase/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/purchaseReturn/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/stockCheck/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/report/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/customer/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/sales/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/salesReturn/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/deliveryNotice/index.vue", indexed)
        self.assertIn("erp-ui/src/views/oa/purchase/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/stock/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/stock/log.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/product/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/category/index.vue", indexed)
        self.assertIn("erp-ui/src/views/inventory/supplier/index.vue", indexed)
        self.assertIn(
            "erp-ui-next/src/pages/inventory/suppliers/SupplierWorkbenchPage.vue",
            indexed["erp-ui/src/views/inventory/supplier/index.vue"]["new_component"],
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/purchase-returns/PurchaseReturnWorkbenchPage.vue",
            indexed["erp-ui/src/views/inventory/purchaseReturn/index.vue"][
                "new_component"
            ],
        )
        self.assertIn(
            "erp-ui-next/src/pages/inventory/categories/CategoryWorkbenchPage.vue",
            indexed["erp-ui/src/views/inventory/category/index.vue"]["new_component"],
        )
        self.assertIn("erp-ui/src/views/system/userNotification/index.vue", indexed)
        self.assertIn("erp-ui/src/views/credential/change-password.vue", indexed)
        self.assertIn("erp-ui/src/views/mobile/profile/index.vue", indexed)
        self.assertIn("erp-ui/src/views/profile-completion/index.vue", indexed)
        self.assertIn("erp-ui/src/views/system/user/profile/index.vue", indexed)
        self.assertEqual(
            indexed["erp-ui/src/views/mobile/profile/index.vue"]["new_route"],
            "/account",
        )
        self.assertIn(
            "已确认",
            indexed["erp-ui/src/views/system/user/profile/resetPwd.vue"][
                "confirmation_status"
            ],
        )
        inventory.validate_target_override_files(indexed)

    def test_indexes_a_complete_target_override(self) -> None:
        indexed = inventory.index_target_overrides([valid_override()])

        self.assertEqual(list(indexed), ["erp-ui/src/views/index.vue"])
        self.assertEqual(indexed["erp-ui/src/views/index.vue"]["target_disposition"], "重设计")

    def test_rejects_duplicate_legacy_source_keys(self) -> None:
        with self.assertRaisesRegex(ValueError, "重复旧页面键"):
            inventory.index_target_overrides([valid_override(), valid_override()])

    def test_rejects_an_unknown_disposition(self) -> None:
        with self.assertRaisesRegex(ValueError, "非法目标处置"):
            inventory.index_target_overrides([valid_override(target_disposition="暂时忽略")])

    def test_rejects_implemented_mapping_without_target_evidence(self) -> None:
        with self.assertRaisesRegex(ValueError, "缺少目标证据"):
            inventory.index_target_overrides(
                [valid_override(implementation_status="已实现", target_evidence="")]
            )

    def test_merges_known_overrides_and_rejects_orphans(self) -> None:
        matrix_rows = [
            {
                "old_page_source": "erp-ui/src/views/index.vue",
                "target_disposition": "待确认（保留/重设计/合并/下线）",
                "new_route": "待设计",
                "new_component": "待设计",
                "legacy_automated_tests": "erp-ui/test/unifiedTodoDesktop.test.js",
                "target_automated_tests": "待补齐",
                "confirmation_status": "待确认",
                "implementation_status": "未开始",
                "target_evidence": "待补齐",
            }
        ]
        overrides = inventory.index_target_overrides([valid_override()])

        inventory.merge_target_overrides(matrix_rows, overrides)

        self.assertEqual(matrix_rows[0]["implementation_status"], "部分实现")
        self.assertEqual(matrix_rows[0]["new_route"], "/")
        self.assertEqual(
            matrix_rows[0]["legacy_automated_tests"],
            "erp-ui/test/unifiedTodoDesktop.test.js",
        )
        self.assertEqual(
            matrix_rows[0]["target_automated_tests"],
            "erp-ui-next/src/pages/workbench/WorkbenchPage.test.ts",
        )

        orphan = inventory.index_target_overrides(
            [valid_override(old_page_source="erp-ui/src/views/missing.vue")]
        )
        with self.assertRaisesRegex(ValueError, "不存在于源矩阵"):
            inventory.merge_target_overrides(matrix_rows, orphan)

    def test_rejects_missing_target_evidence_files(self) -> None:
        overrides = inventory.index_target_overrides(
            [valid_override(new_component="erp-ui-next/src/pages/missing.vue")]
        )

        with self.assertRaisesRegex(ValueError, "目标证据文件不存在"):
            inventory.validate_target_override_files(overrides)


if __name__ == "__main__":
    unittest.main()
