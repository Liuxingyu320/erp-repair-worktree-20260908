# Warehouse Inventory Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the warehouse/store inventory rules: only warehouses can purchase into stock, stores replenish through transfer only, warehouse users can view store stock read-only, transfer shipment deducts warehouse stock, and store receipt increases store stock.

**Architecture:** Put hard business invariants in backend service-layer validation, then align frontend context, routing, buttons, and page defaults to the same rules. Reuse the existing `dept_type=WAREHOUSE/STORE`, `Dept-NumId` selected organization header, transfer shipment batches, and `InvStock` organization-as-stock-location model.

**Tech Stack:** Spring Boot services in `erp-modules/erp-inventory`, MyBatis XML mappers, Vue 2 / Element UI in `erp-ui`, existing Ruoyi permission codes and selected organization context.

---

## Confirmed Business Rules

- There is currently one central warehouse, represented as `sys_dept.dept_type = 'WAREHOUSE'`.
- Purchase management belongs to warehouse management only. A store context must not create, submit, receive, quality-check, cancel, or delete purchase orders.
- Store replenishment inventory can increase through transfer receipt only: warehouse-to-store replenishment or approved cross-store transfer. Stores do not purchase directly. Existing non-replenishment stock changes such as sales return and stock-check variance remain valid when they target the selected writable organization.
- Warehouse shipment deducts warehouse inventory immediately and creates a pending-receive shipment batch. The goods are in transit until the store confirms receipt.
- Store receipt increases store inventory and closes that shipment batch.
- Partial shipment is allowed. Current batch receipt remains full-batch receipt unless a later requirement explicitly adds discrepancy handling.
- Warehouse management can view other stores' inventory, but only read-only. It cannot adjust store inventory.
- Backend validation is authoritative. Frontend visibility is user experience only.
- Purchase return belongs to warehouse purchase management when it is tied to purchase stock. Store context must not create or confirm purchase returns.
- Stock check is a direct stock mutation path. It must obey the same writable inventory boundary as manual stock adjustment.

## Current Code Facts

- `InvTransferServiceImpl` already creates shipment batches, deducts source stock on delivery, and adds target stock on shipment receipt.
- `InvPurchaseServiceImpl` currently writes purchase `shopDeptId` from selected context but does not require the selected context to be a warehouse.
- `InvPurchaseServiceImpl.receivePurchase` currently accepts a client-provided `warehouseId` and only checks non-empty.
- `InvStockServiceImpl` supports `ownOnly` and scoped stock queries, but stock detail and stock adjustment are still too tied to current organization only and do not separate read scope from write scope.
- `InvStockCheckServiceImpl` can create and confirm stock checks for a client-provided `warehouseId`; confirmation can increase or decrease inventory.
- `InvPurchaseReturnServiceImpl` creates purchase return documents in the selected context and deducts inventory when confirmed.
- `InvSalesServiceImpl` and `InvDeliveryNoticeServiceImpl` generate `cross_store` transfer records for cross-store sales/dispatch flows.
- `InvDeptScopeMapper` lacks `dept_type` lookup helpers needed by inventory services.
- `shopContext.js` only stores selected dept id/name; frontend pages cannot reliably know whether the current context is a store or a warehouse.
- Transfer, purchase, and stock pages mostly use permission codes and status checks; they do not consistently include selected organization type checks.

## Agent Ownership Model

Agents must not edit files outside their ownership unless the coordinator explicitly reassigns a file. Shared contracts are created first by Agent A and then consumed by later agents.

### Agent A: Backend Organization Contract

**Purpose:** Add authoritative organization type helpers used by inventory services.

**Owned Files:**
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvDeptScopeMapper.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeptScopeMapper.xml`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java`
- Test fakes in inventory service tests that implement `InvDeptScopeMapper`

**Contract to Provide:**
- `String selectDeptTypeById(Long deptId)`
- `boolean isWarehouseDept(Long deptId)` or protected equivalent in `InvBaseService`
- `boolean isStoreDept(Long deptId)` or protected equivalent in `InvBaseService`
- Protected validators:
  - `requireWarehouseContext(Long selectedDeptId, String message)`
  - `requireStoreContext(Long selectedDeptId, String message)`
  - `assertDeptType(Long deptId, String deptType, String message)`
  - `assertWritableInventoryDept(Long inventoryDeptId, Long selectedDeptId, String message)`

**Steps:**
- [ ] Add mapper method signatures and XML query for `dept_type` by `dept_id`, restricted to normal, not-deleted departments.
- [ ] Add protected helpers in `InvBaseService`; admin may bypass visibility scope, but must not bypass the business invariant that a warehouse-only operation targets a warehouse and a store-only operation targets a store.
- [ ] Update existing fake `InvDeptScopeMapper` implementations in tests to return deterministic `STORE` or `WAREHOUSE` values.
- [ ] Run inventory unit tests that compile affected fakes.

**Acceptance:**
- Services can ask whether selected organization is `WAREHOUSE` or `STORE`.
- Existing tests compile after fake mapper updates.
- Business type checks can be reused without duplicating SQL in every service.

### Agent B: Purchase Warehouse-Only Enforcement

**Purpose:** Make purchase management warehouse-only and prevent stores from increasing inventory through purchase.

**Owned Files:**
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvPurchaseServiceImplTest.java`

**Depends On:** Agent A helpers.

**Rules to Enforce:**
- `saveDraft` and `submitPurchase` require selected context to be `WAREHOUSE`; saved `shopDeptId` is the selected warehouse id.
- `receivePurchase` requires selected context to be `WAREHOUSE`.
- `receivePurchase` must reject `warehouseId` values that are not warehouses.
- In current single-warehouse rule, `receivePurchase.warehouseId` should equal selected warehouse id. This avoids warehouse A receiving into warehouse B and makes the client parameter non-authoritative.
- `qualityCheck`, `cancelPurchase`, and `deletePurchase` require the purchase order's `shopDeptId` to be the selected warehouse and type `WAREHOUSE`.
- Purchase stock changes always write `shopDeptId=warehouseId=selectedWarehouseId`.
- Purchase return save, submit, confirm, cancel, and list/detail write actions require warehouse context when they mutate purchase stock.

**Steps:**
- [ ] Add failing tests for a store-selected context attempting purchase save/submit/receive/qc.
- [ ] Add failing test for warehouse context receiving into a store dept id.
- [ ] Add failing test for warehouse context receiving into a different warehouse id.
- [ ] Add failing tests for store context attempting purchase return save/submit/confirm.
- [ ] Implement purchase context validation using Agent A helpers.
- [ ] Normalize receive target to selected warehouse id after validation.
- [ ] Apply the same warehouse-only validation to purchase return service and tests.
- [ ] Run `mvn -pl erp-modules/erp-inventory -Dtest=InvPurchaseServiceImplTest test`.

**Acceptance:**
- Store users cannot purchase even if they mistakenly have `inv:purchase:*` permissions.
- Purchase receiving cannot write to stores.
- Admin may access records by scope rules, but purchase inventory target must still be a warehouse.

### Agent C: Stock Read/Write Boundary

**Purpose:** Let warehouse users view store stock read-only while preventing direct edits to store inventory.

**Owned Files:**
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockServiceImplWarehouseTest.java`

**Depends On:** Agent A helpers.

**Rules to Enforce:**
- Store context: list, summary, detail are limited to current store inventory unless broader admin reporting is explicitly requested elsewhere.
- Warehouse context: can list and detail warehouse inventory plus visible store inventory; store rows are read-only.
- `adjustStock` may only write the selected organization's own inventory. It must reject attempts to adjust another store from warehouse context.
- `InvStockCheckServiceImpl.createCheck`, `inputActualQty`, `submitCheck`, and `confirmCheck` may only write the selected organization's own inventory. A warehouse user may not create or confirm a stock check for a store inventory row through cross-store visibility.
- `selectStockById` should use the same read-scope logic as list, not a stricter current-org-only rule.
- Stock log read rules should stay conservative unless a store/warehouse log view is explicitly added. If warehouse cross-store logs are exposed, it must be read-only.

**Steps:**
- [ ] Add tests for warehouse context listing other store stock with `ownOnly=false`.
- [ ] Add test for warehouse context selecting a store stock row by id.
- [ ] Add test that warehouse context cannot `adjustStock` for another store.
- [ ] Add test that store context cannot use request `warehouseId/shopDeptId` to adjust another organization.
- [ ] Add stock-check tests that warehouse context cannot create or confirm a store stock check.
- [ ] Implement read-scope and write-scope separation.
- [ ] Apply the write-scope helper to manual adjustment and stock-check mutations.
- [ ] Keep transfer receipt and purchase quality-check stock mutations unaffected; those flows bypass `adjustStock`.
- [ ] Run `mvn -pl erp-modules/erp-inventory -Dtest=InvStockServiceImplWarehouseTest test`.

**Acceptance:**
- Warehouse can view store stock when query asks for non-own inventory.
- The same warehouse cannot manually adjust store stock from the stock page.
- Store inventory increases through transfer receipt path, not purchase or manual cross-org adjustment.

### Agent D: Transfer Business Boundary

**Purpose:** Keep the existing transfer shipment/receipt flow and make role/type boundaries match the rules.

**Owned Files:**
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`

**Depends On:** Agent A helpers.

**Rules to Enforce:**
- Warehouse replenishment transfer:
  - requester context is `STORE`;
  - `toDeptId/toWarehouseId` is current store;
  - `fromDeptId/fromWarehouseId` is a `WAREHOUSE`;
  - shipment operation context is the source warehouse;
  - receipt operation context is the target store.
- Cross-store transfer:
  - if kept enabled, source and target are both `STORE`;
  - source store ships, target store receives;
  - no purchase shortcut is involved.
- Existing sales and delivery-notice generated `cross_store` records must keep working. If they generate pending receipt transfer records, validations must explicitly support that system path without allowing warehouse replenishment creation from warehouse context.
- Shipment continues to deduct source stock and create `pending_receive` batch.
- Receipt continues to add target store stock and close shipment batch.
- Partial shipment remains supported.

**Steps:**
- [ ] Add tests for store creating warehouse replenishment transfer.
- [ ] Add test rejecting warehouse context creating a store replenishment request.
- [ ] Add test rejecting store replenishment whose target is not current store.
- [ ] Add test rejecting shipment by target store and receipt by source warehouse.
- [ ] Add test for cross-store type if business keeps it enabled.
- [ ] Add regression tests for `createDeliveredCrossStoreTransfer` or the delivery-notice path so sales-generated cross-store records still work.
- [ ] Implement transfer type and organization type validations.
- [ ] Run `mvn -pl erp-modules/erp-inventory -Dtest=InvTransferServiceImplTest test`.

**Acceptance:**
- Existing partial shipment and batch receipt behavior continues to pass.
- A store can request goods; the warehouse can ship; the store can receive.
- No transfer API lets stores bypass warehouse/store boundaries.

### Agent E: Frontend Organization Context

**Purpose:** Make selected organization type available to desktop and mobile pages.

**Owned Files:**
- `erp-ui/src/utils/shopContext.js`
- `erp-ui/src/views/select-shop/index.vue`
- `erp-ui/src/permission.js`
- `erp-ui/src/utils/request.js` only if a context header extension is needed
- `erp-ui/src/views/mobile/mobileNavigation.js` and mobile workbench files if present

**Contract to Provide:**
- `setSelectedDept(deptId, deptName, deptType, extra)`
- `getSelectedDeptType()`
- `isSelectedWarehouse()`
- `isSelectedStore()`
- `getSelectedDeptContext()`
- Backward compatibility for existing stored id/name.

**Steps:**
- [ ] Persist `deptType` when selecting an organization.
- [ ] Clear `deptType` when clearing selected organization.
- [ ] Add route guard for inventory business pages requiring selected `STORE` or `WAREHOUSE` context on desktop as well as mobile.
- [ ] Make `COMPANY/GROUP` unable to enter write-oriented inventory pages unless a separate report page exists.
- [ ] Keep `Dept-NumId` request header unchanged.
- [ ] Adjust mobile navigation so store context shows transfer/replenishment and warehouse context shows purchase/warehouse actions.
- [ ] Run frontend lint/build after page agents complete.

**Acceptance:**
- Desktop pages know current context type.
- A direct URL cannot accidentally open purchase operations in store context.
- Existing users with only old localStorage id/name get routed to reselect context if type is missing.

### Agent F: Frontend Purchase Page

**Purpose:** Align purchase UI with warehouse-only backend rules.

**Owned Files:**
- `erp-ui/src/views/inventory/purchase/index.vue`
- `erp-ui/src/views/inventory/purchase/purchaseActionRules.js`
- `erp-ui/src/views/inventory/components/WarehouseSelect.vue`

**Depends On:** Agent E context helpers.

**Rules to Implement:**
- In store context, hide or block all purchase write actions.
- In warehouse context, new purchase, edit, submit, receive, qc, cancel, and delete work as before.
- Receiving defaults to current warehouse and locks the warehouse selector to current warehouse.
- If a purchase belongs to a different warehouse than current context, write actions are hidden and backend will reject.

**Steps:**
- [ ] Add computed `isWarehouseContext` and `currentWarehouseId`.
- [ ] Hide write buttons unless `isWarehouseContext`.
- [ ] Default receive `warehouseId` to current selected warehouse id.
- [ ] Add `lockToCurrentWarehouse` or equivalent prop to `WarehouseSelect` without breaking existing callers.
- [ ] Add user-facing guard in `openForm`, `doSave`, `doReceive`, and `submitReceive`.
- [ ] Verify page does not depend on frontend-only checks for security.

**Acceptance:**
- Store context cannot operate purchase UI.
- Warehouse context purchase receipt cannot choose a different target warehouse.

### Agent G: Frontend Stock And Transfer Pages

**Purpose:** Make stock and transfer pages match store/warehouse responsibilities.

**Owned Files:**
- `erp-ui/src/views/inventory/stock/index.vue`
- `erp-ui/src/views/inventory/transfer/index.vue`
- `erp-ui/src/api/inventory/stock.js`
- `erp-ui/src/api/inventory/transfer.js`

**Depends On:** Agent E context helpers and Agent F component contract if shared.

**Stock Rules:**
- Store context: only current store inventory, adjustment only own inventory when permitted.
- Warehouse context: own warehouse inventory plus read-only store inventory view.
- Adjustment buttons appear only for current selected inventory organization, never for other store rows.

**Transfer Rules:**
- Store context: can initiate warehouse replenishment and receive its own shipments.
- Warehouse context: can ship its own warehouse transfer orders, cannot initiate store replenishment.
- Buttons combine permission, status, context type, and row matching:
  - ship only when `isWarehouseContext && row.fromWarehouseId/fromDeptId == currentDeptId`
  - receive only when `isStoreContext && row.toDeptId/toWarehouseId == currentDeptId`
  - create/edit/submit only in store context for target current store
- If cross-store remains enabled, source/target selector and button rules must explicitly distinguish it from warehouse replenishment.

**Steps:**
- [ ] Add current context computed helpers.
- [ ] Add stock view selector for warehouse context: own warehouse vs store inventory read-only.
- [ ] Hide row adjustment for non-current rows.
- [ ] Remove or disable transfer `cross_store` UI unless the final business decision keeps it.
- [ ] Add row-aware `canShip`, `canReceive`, `canCreateRequest`, and `canEditRequest`.
- [ ] Add frontend guards before calling shipment/receipt APIs.

**Acceptance:**
- Warehouse sees and ships only warehouse-side pending transfer tasks.
- Store sees and receives only its own incoming shipments.
- Warehouse stock view can inspect store inventory without write controls.

### Agent H: Permission/Menu/Data And Verification

**Purpose:** Align role permissions and prove the whole rule set works end-to-end.

**Owned Files:**
- SQL permission/menu seed files under `sql/` if the repo has a current seed script for inventory permissions.
- Verification notes in `docs/superpowers/specs/` or `docs/superpowers/plans/`.
- No production source files unless coordinator assigns them.

**Steps:**
- [ ] Query current `sys_menu` permission strings for inventory purchase, stock, transfer, and stock check.
- [ ] Confirm roles can be configured so warehouse admin sees warehouse management and store manager sees store transfer/sales/stock pages.
- [ ] Add or document missing permission strings if role setup cannot express the rule.
- [ ] Build backend inventory module after Agents A-D integrate.
- [ ] Build frontend after Agents E-G integrate.
- [ ] Run browser verification for:
  - warehouse context purchase;
  - store context blocked purchase;
  - warehouse context store stock read-only;
  - store request -> warehouse shipment -> store receipt.

**Acceptance:**
- Role setup can express warehouse admin and store manager views.
- Backend tests pass for the rule set.
- Frontend build passes and verified pages match context behavior.

## Execution Order

1. Agent A first. It establishes the backend organization type contract.
2. Agents B, C, and D in parallel after Agent A lands. They own disjoint backend service files.
3. Agent E in parallel with B/C/D if it only touches frontend context, because it does not depend on backend code.
4. Agents F and G after Agent E lands. They consume frontend context helpers and keep file ownership separate.
5. Agent H after backend and frontend integration. It validates permissions, builds, and browser checks.

## Coordinator Integration Rules

- Do not allow frontend agents to weaken backend rules for convenience.
- Do not allow backend agents to infer business identity from permission strings alone; use selected organization type plus scope.
- Do not use `admin` bypass for business type invariants. Admin may bypass data scope, but purchase inventory target must still be a warehouse and transfer receipt target must still be a store.
- Keep existing transfer shipment batch model. Do not add a new stock table unless the user later requires an explicit in-transit inventory ledger.
- Treat current in-transit as `pending_receive` shipment quantity: shipped minus received.

## Verification Commands

Backend targeted tests:

```bash
mvn -pl erp-modules/erp-inventory -Dtest=InvPurchaseServiceImplTest test
mvn -pl erp-modules/erp-inventory -Dtest=InvStockServiceImplWarehouseTest test
mvn -pl erp-modules/erp-inventory -Dtest=InvTransferServiceImplTest test
```

Backend package:

```bash
mvn -pl erp-modules/erp-inventory -am package -DskipTests
```

Frontend build:

```bash
npm --prefix erp-ui run build:prod
```

Browser verification targets after services are running:

- `/select-shop`
- `/inventory/purchase`
- `/inventory/stock`
- `/inventory/transfer`
- `/mobile/inventory`

## Open Decisions

- Cross-store transfer: the user allowed “其他店调货”. Keep it, but implement it as explicit store-to-store transfer with source store shipment and target store receipt, not as purchase or warehouse replenishment.
- In-transit inventory: current implementation represents in-transit through pending shipment batches. Do not create a separate in-transit stock account unless reporting requires a visible in-transit total.
