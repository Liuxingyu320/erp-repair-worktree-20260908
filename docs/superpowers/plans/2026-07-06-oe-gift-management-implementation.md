# OE Gift Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build OE utensil and gift-box catalog management, connect OE utensils to fixed assets, and expose desktop and mobile read-only query pages.

**Architecture:** Add independent inventory catalog tables and Java services for OE and gift data; keep them out of stock, purchase, sales, transfer, and product tables. Refactor OA fixed asset config and repair from product fields to OE fields. Reuse the existing Vue desktop table/drawer pattern and the mobile configurable feature runtime.

**Tech Stack:** Spring Boot, MyBatis XML, MySQL migration SQL, Vue 2, Element UI, Node-based frontend static tests, Maven/JUnit backend tests.

---

### Task 1: Backend OE/Gift Catalog Foundation

**Files:**
- Create: `sql/erp_inventory_oe_gift_management_20260706.sql`
- Create: `docker/mysql/db/erp_inventory_oe_gift_management_20260706.sql`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOeCategory.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOeItem.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvGiftCategory.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvGiftBox.java`
- Create: matching mapper, service, controller, and MyBatis XML files under `erp-modules/erp-inventory`.
- Test: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java`

- [ ] Write failing backend mapper/service tests for OE/gift field bindings and no product-table coupling.
- [ ] Create SQL tables, menu permissions, Java domains, mappers, services, controllers.
- [ ] Run targeted inventory tests and fix compile errors.
- [ ] Commit backend catalog foundation.

### Task 2: Fixed Asset OE Refactor

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetConfig.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetRepair.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetConfigMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetRepairMapper.xml`
- Test: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaFixedAssetServiceImplTest.java`

- [ ] Write failing tests proving config/repair use `oeItemId`, reject unconfigured OE repair, and list OE names.
- [ ] Replace product fields with OE fields in OA domains, mappers, SQL joins, validation, and repair snapshot logic.
- [ ] Run targeted OA tests and fix compile errors.
- [ ] Commit fixed asset OE refactor.

### Task 3: Desktop OE/Gift Pages

**Files:**
- Create: `erp-ui/src/api/inventory/oe.js`
- Create: `erp-ui/src/api/inventory/gift.js`
- Create: `erp-ui/src/views/inventory/oe/category/index.vue`
- Create: `erp-ui/src/views/inventory/oe/index.vue`
- Create: `erp-ui/src/views/inventory/gift/category/index.vue`
- Create: `erp-ui/src/views/inventory/gift/index.vue`
- Modify: fixed asset config/repair Vue pages.
- Test: `erp-ui/test/oeGiftManagement.test.js`

- [ ] Write failing static tests for APIs, pages, read-only mode, fixed asset OE picker, and price visibility.
- [ ] Implement desktop APIs and pages by following product/category layout.
- [ ] Update fixed asset desktop pages to search/select OE instead of products.
- [ ] Run targeted frontend tests and fix regressions.
- [ ] Commit desktop implementation.

### Task 4: Mobile OE/Gift Pages

**Files:**
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/mobileNavigation.js`
- Modify: `erp-ui/src/views/mobile/feature/featureService.js`
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
- Modify: `erp-ui/src/views/mobile/feature/featureSearchConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileExportConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileEntityService.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormPayloads.js`
- Test: existing mobile feature tests plus new OE/gift assertions.

- [ ] Write failing mobile tests for `/mobile/oe`, `/mobile/gift`, price visibility, export config, and `fixedAssetOe`.
- [ ] Add mobile route/workbench entries and feature service/mapper/search/export support.
- [ ] Change mobile fixed asset repair form and payload from `productId` to `oeItemId`.
- [ ] Run targeted mobile tests and fix regressions.
- [ ] Commit mobile implementation.

### Task 5: Final Verification

**Files:**
- All touched files.

- [ ] Run backend targeted Maven tests for inventory and OA.
- [ ] Run frontend targeted Node tests for OE/gift, fixed asset, and mobile features.
- [ ] Review SQL and generated menu permissions.
- [ ] Report any tests that cannot run in this environment.
