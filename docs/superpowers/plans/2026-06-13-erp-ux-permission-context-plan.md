# ERP UX Permission Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce confusing “no permission”, blank data, and wrong organization-context experiences in inventory, purchase, transfer, and organization selection flows.

**Architecture:** Keep business APIs unchanged for this pass. Add explicit frontend context guidance and empty-state copy where users currently see hidden actions or generic empty tables, and add an idempotent SQL migration that makes backend permissions assignable through `sys_menu`.

**Tech Stack:** Vue 2, Element UI, Node source assertion tests, MySQL idempotent migration SQL.

---

### Task 1: Interaction Contract Tests

**Files:**
- Create: `erp-ui/test/inventoryBusinessUx.test.js`

- [ ] **Step 1: Write the failing test**

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const selectShopSource = read("src/views/select-shop/index.vue")
const stockSource = read("src/views/inventory/stock/index.vue")
const purchaseSource = read("src/views/inventory/purchase/index.vue")
const transferSource = read("src/views/inventory/transfer/index.vue")

assert.ok(
  selectShopSource.includes("库存、采购、调拨建议选择门店或仓库") &&
    selectShopSource.includes("getSelectionBusinessHint") &&
    selectShopSource.includes("hasBusinessChildren"),
  "organization selection should explain business impact and make expandable parents obvious"
)

assert.ok(
  stockSource.includes("stockEmptyText") &&
    stockSource.includes("当前仓库暂无可用库存") &&
    stockSource.includes("stockContextAlert"),
  "stock page should explain invalid context and empty inventory next steps"
)

assert.ok(
  purchaseSource.includes("purchaseContextAlert") &&
    purchaseSource.includes("采购单只能在仓库上下文中创建、收货和质检") &&
    purchaseSource.includes(":empty-text=\"purchaseEmptyText\""),
  "purchase page should explain why warehouse-only actions are unavailable"
)

assert.ok(
  transferSource.includes("transferContextAlert") &&
    transferSource.includes("请先选择要货仓库") &&
    transferSource.includes(":empty-text=\"stockPickerEmptyText\""),
  "transfer page should explain store/warehouse requirements and empty source stock"
)

console.log("inventoryBusinessUx tests passed")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node erp-ui/test/inventoryBusinessUx.test.js`

Expected: `AssertionError` before the implementation because the new strings and computed properties are not present.

- [ ] **Step 3: Implement minimal frontend changes**

Modify `select-shop`, `stock`, `purchase`, and `transfer` Vue files to include the named computed properties and visible Element UI alerts/empty text.

- [ ] **Step 4: Run test to verify it passes**

Run: `node erp-ui/test/inventoryBusinessUx.test.js`

Expected: `inventoryBusinessUx tests passed`.

### Task 2: Permission Menu Alignment

**Files:**
- Create: `sql/erp_inventory_permission_alignment_20260613.sql`
- Modify: `erp-ui/test/inventoryBusinessUx.test.js`

- [ ] **Step 1: Extend the failing test**

Add SQL assertions that the migration contains button menu rows for:

```js
const permissionSql = fs.readFileSync(
  path.resolve(__dirname, "../../sql/erp_inventory_permission_alignment_20260613.sql"),
  "utf8"
)
;[
  "inv:sales:submit",
  "inv:purchase:submit",
  "inv:purchase:qc",
  "inv:product:import",
  "inv:report:list",
  "inv:sales:export",
  "inv:purchase:export"
].forEach(permission => {
  assert.ok(permissionSql.includes(permission), "permission migration should include " + permission)
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node erp-ui/test/inventoryBusinessUx.test.js`

Expected: `ENOENT` or `AssertionError` because the SQL file does not exist or lacks the permissions.

- [ ] **Step 3: Add idempotent SQL**

Create `sql/erp_inventory_permission_alignment_20260613.sql` with `INSERT INTO sys_menu ... ON DUPLICATE KEY UPDATE` rows for the missing permissions and `INSERT IGNORE INTO sys_role_menu` rows for the admin role.

- [ ] **Step 4: Run test to verify it passes**

Run: `node erp-ui/test/inventoryBusinessUx.test.js`

Expected: `inventoryBusinessUx tests passed`.

### Task 3: Verification

**Files:**
- No additional files.

- [ ] **Step 1: Run focused source tests**

Run:

```bash
node erp-ui/test/inventoryBusinessUx.test.js
node erp-ui/test/purchaseActionRules.test.js
node erp-ui/test/stockWarehouseStoreFilter.test.js
node erp-ui/test/transferApprovalRules.test.js
```

Expected: each command prints its `tests passed` line.

- [ ] **Step 2: Apply SQL locally**

Run:

```bash
mysql -uroot BossERP_stock_state_75c59ee < sql/erp_inventory_permission_alignment_20260613.sql
```

Expected: command exits with status `0`.

- [ ] **Step 3: Verify permissions are now assignable**

Run:

```bash
mysql -uroot BossERP_stock_state_75c59ee -N -e "select perms from sys_menu where perms in ('inv:sales:submit','inv:purchase:submit','inv:purchase:qc','inv:product:import','inv:report:list','inv:sales:export','inv:purchase:export') order by perms"
```

Expected: all seven permissions are returned.

- [ ] **Step 4: Browser smoke test**

Open `http://localhost:1025` and inspect:
- `/select-shop` shows business context guidance.
- `/cangku/purchase` under a store context explains that purchase operations need warehouse context.
- `/inventory/transfer` explains that only stores can initiate requisitions and that source warehouse stock may be empty.
- `/cangku/stock` under a store context explains the context mismatch.
