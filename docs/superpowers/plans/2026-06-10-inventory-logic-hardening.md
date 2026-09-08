# Inventory Logic Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden inventory business rules so stock, return quantities, transfer cost, and selected shop/warehouse context stay consistent.

**Architecture:** Keep validations in backend services as source of truth, while frontend guards provide earlier and clearer feedback. Preserve current RuoYi/Vue patterns and add focused regression tests around the exact business failures found in review.

**Tech Stack:** Spring Boot Java services, MyBatis XML mappers, JUnit 5, Vue 2, Element UI, Node assert-style frontend tests.

---

### Task 1: Backend Source-Of-Truth Validation

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImplTest.java`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvProductCatalogScopeTest.java`

- [ ] Add failing tests proving delivery rejects a request warehouse outside selected scope and category edit does not accept unsafe parent changes.
- [ ] Implement delivery warehouse type/scope validation in `deliverNotice`.
- [ ] Preserve existing category parent/ancestors on edit unless a validated move path is intentionally implemented later.
- [ ] Run targeted JUnit tests and confirm they pass.

### Task 2: Return Details Linked To Source Detail Rows

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseReturnDetail.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesReturnDetail.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseReturnDetailMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesReturnDetailMapper.xml`
- Modify: `erp-ui/src/views/inventory/purchaseReturn/index.vue`
- Modify: `erp-ui/src/views/inventory/salesReturn/index.vue`
- Create: `sql/erp_inventory_return_detail_source_detail_20260610.sql`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImplTest.java`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvSalesReturnServiceImplTest.java`

- [ ] Add failing tests with duplicate product lines at different unit prices.
- [ ] Add `purchaseDetailId` and `salesDetailId` to return detail domains, mapper result maps, inserts, and selects.
- [ ] Normalize and validate return rows by source detail ID, falling back to product only when the original order has a single matching product row for legacy clients.
- [ ] Pass source detail IDs from the frontend when building return rows.
- [ ] Add SQL migration columns and indexes.
- [ ] Run targeted return service tests.

### Task 3: Transfer Cost And Locking

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferShipmentDetail.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferShipmentDetailMapper.xml`
- Create: `sql/erp_inventory_transfer_shipment_cost_20260610.sql`
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`

- [ ] Add failing tests proving receipt uses shipment cost instead of current source stock cost.
- [ ] Persist `costPrice` on shipment details at delivery.
- [ ] Use shipment cost on receipt and lock target stock before weighted average updates.
- [ ] Run targeted transfer tests.

### Task 4: Frontend Context And Human-Focused Details

**Files:**
- Modify: `erp-ui/src/permission.js`
- Modify: `erp-ui/src/views/inventory/salesReturn/index.vue`
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`
- Modify: `erp-ui/src/views/inventory/sales/index.vue`
- Test: `erp-ui/test/shopContextUx.test.js`

- [ ] Add failing frontend tests for inventory route context coverage.
- [ ] Cover `/inventory/` route context consistently.
- [ ] Add sales return store-context guard matching purchase return behavior.
- [ ] Replace HTML-string order details with structured detail dialogs.
- [ ] Run frontend Node tests.
