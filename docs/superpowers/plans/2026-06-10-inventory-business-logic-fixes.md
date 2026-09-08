# Inventory Business Logic Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the inventory business logic defects found in the audit: return order creation, stock check confirmation, manual stock adjustment, stock log movement types, and cost calculation consistency.

**Architecture:** Keep changes inside the existing RuoYi Cloud inventory module and Vue2 inventory pages. Backend services remain the source of truth for product identity, returnable quantity, amount, and inventory mutation validation; frontend changes only improve selection and payload correctness.

**Tech Stack:** Spring Boot, MyBatis XML mappers, JUnit 5/Mockito, Vue2, Element UI.

---

### Task 1: Backend Return Validation And Recalculation

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java`
- Modify/Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/*Return*Test.java`

- [x] Write failing tests showing return detail product, price, and amount are derived from the original order detail.
- [x] Run the focused return tests and verify they fail before production changes.
- [x] Update service validation to reject details not present in the original order and recalculate line amount and total amount.
- [x] Run focused return tests until green.

### Task 2: Stock Check Snapshot Guard

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java`
- Modify/Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckServiceImplTest.java`

- [x] Write a failing test where current stock changed after the check snapshot and confirmation is rejected.
- [x] Run the focused stock check test and verify it fails for the expected reason.
- [x] Add confirmation-time snapshot validation before applying profit/loss diff.
- [x] Run focused stock check tests until green.

### Task 3: Manual Stock Adjustment Available Guard

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java`
- Modify/Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockServiceImplWarehouseTest.java`

- [x] Write a failing test where current stock is sufficient but available stock is not.
- [x] Run the focused stock service test and verify it fails.
- [x] Reject negative adjustment when available stock is insufficient.
- [x] Run focused stock tests until green.

### Task 4: Movement Type Contract

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvStatusConstants.java`
- Modify: inventory services that write stock logs
- Reviewed: `erp-ui/src/views/inventory/stock/log.vue`
- Modify/Test: inventory service tests

- [x] Add/align movement type constants for purchase return out, sales return in, stock check profit, and stock check loss.
- [x] Update stock log writers and keep frontend filter labels aligned with the same values.
- [x] Run focused backend tests and frontend unit tests if available.

### Task 5: Delivery And Transfer Cost Consistency

**Files:**
- Reuse: existing `selectInvStockByProductShopWarehouseForUpdate` in `InvStockMapper.java` and `InvStockMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- Modify/Test: delivery/transfer service tests where feasible

- [x] Reuse the existing row-locking stock read helper for operations that need cost before deduction.
- [x] Use it in sales delivery and transfer shipment paths.
- [x] Run focused delivery and transfer tests.

### Task 6: Final Verification And Commit

- [x] Run `mvn -pl erp-inventory test -DskipITs` from `erp-modules`.
- [x] Run relevant frontend tests from `erp-ui` if dependencies are available.
- [x] Review `git diff`.
- [x] Commit the branch with a clear message.
