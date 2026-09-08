# Frontend Inventory Report Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the requested frontend fixes, add practical inventory batch/location visibility, and add a report center for inventory, purchase, and sales operations.

**Architecture:** Keep changes incremental and aligned with the existing RuoYi Cloud/Vue 2 patterns. Backend additions should be additive controllers/services/mapper methods where possible; frontend additions should reuse existing inventory/mobile APIs, Element UI tables/forms, and route/menu conventions.

**Tech Stack:** Java Spring Boot, MyBatis XML mappers, Vue 2, Element UI, existing ERP permission model.

---

### Task 1: Frontend Completeness Fixes

**Files:**
- Modify: `erp-ui/src/router/index.js`
- Modify: `erp-ui/src/views/system/shop/index.vue`
- Modify: `erp-ui/src/views/system/operlog/index.vue`
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`
- Modify: `erp-ui/src/views/inventory/sales/index.vue`
- Modify: `erp-ui/src/views/inventory/purchaseReturn/index.vue`
- Modify: `erp-ui/src/views/inventory/salesReturn/index.vue`
- Modify: `erp-ui/src/api/inventory/deliveryNotice.js`
- Modify as needed: existing frontend tests under `erp-ui/test`

- [ ] Add or adjust failing frontend contract tests for permission strings and visible controls.
- [ ] Replace stale `inventory:stock:list` with `inv:stock:list`.
- [ ] Align draft edit/save permissions with backend permission behavior without changing codegen or npm test scripts.
- [ ] Hide/disable the shop authorization save action unless `system:userShop:edit` is available.
- [ ] Resolve the `system:operlog:query` detail mismatch by either removing the dead detail permission usage or wiring it to an implemented behavior.
- [ ] Keep sales delivery UI on delivery notice flow and remove stale direct-sales-delivery assumptions from frontend code/tests.

### Task 2: Mobile Replenishment and Attachment Deletion

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/featureService.js`
- Modify: `erp-ui/src/views/mobile/feature/featureActionRuntime.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormPayloads.js`
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
- Modify: `erp-ui/src/api/inventory/transfer.js` or `erp-ui/src/api/inventory/purchase.js` if using an existing business order as the replenishment target
- Modify or create: `erp-ui/src/api/system/file.js` only if the project has no existing frontend delete wrapper
- Modify as needed: relevant `erp-ui/test/mobile*` tests

- [ ] Add a failing test that proves mobile replenishment is not just a low-stock list: it must expose a create/submit action.
- [ ] Implement replenishment as a real business action using the closest existing backend flow, preferring transfer order creation when source/destination warehouse data is available.
- [ ] Add frontend file delete API wrapper and use it in upload/attachment components where deletion already exists visually but does not call backend delete.
- [ ] Keep this task frontend-only unless an existing backend endpoint is missing.

### Task 3: Inventory Batch, Expiry, Serial, and Location Visibility

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockLog.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvStockAdjustRequest.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockLogMapper.xml`
- Modify: `erp-ui/src/views/inventory/stock/index.vue`
- Modify: `erp-ui/src/views/inventory/stock/log.vue`
- Create: `sql/erp_inventory_batch_location_upgrade_20260612.sql`
- Modify or create focused backend/frontend tests where existing test harness supports it

- [ ] Add failing mapper/domain tests or static contract tests for batch number, expiry date, serial number, and location fields.
- [ ] Add additive DB columns to `inv_stock` and `inv_stock_log`.
- [ ] Surface the fields in stock list, stock adjustment form, and stock log list.
- [ ] Do not refactor the whole stock engine into lot-level costing; this task is a practical visibility layer.

### Task 4: Report Center

**Files:**
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvReportController.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvReportSummary.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvReportMapper.java`
- Create: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvReportService.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvReportServiceImpl.java`
- Create: `erp-ui/src/api/inventory/report.js`
- Create: `erp-ui/src/views/inventory/report/index.vue`
- Modify: `erp-ui/src/router/index.js` only if a static fallback route is needed
- Create or modify: focused tests for API contract and frontend route/API wiring

- [ ] Add failing tests for report API contract and frontend API wrapper.
- [ ] Add backend `/inventory/report/summary` with inventory totals, warning stock count, purchase amount, sales amount, and gross margin.
- [ ] Add backend `/inventory/report/stock-warning` for low-stock rows.
- [ ] Add frontend report center page with summary cards and warning-stock table.
- [ ] Use existing `inv:report:list` style permission for the new report endpoint and page metadata.

### Task 5: Integration and Verification

**Files:**
- All changed files.

- [ ] Review child-agent changes for overlap with existing dirty files.
- [ ] Run focused frontend tests that are available without adding npm scripts.
- [ ] Run focused backend tests or compile modules if dependencies are locally available.
- [ ] Record any tests that cannot run because of environment constraints.
