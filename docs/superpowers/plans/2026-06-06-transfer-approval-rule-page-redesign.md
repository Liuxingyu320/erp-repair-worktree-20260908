# Transfer Approval Rule Page Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the transfer approval configuration page so users configure ordered approval positions clearly, without changing the existing transfer order workflow or backend approval engine.

**Architecture:** Keep the existing `inv_transfer_approval_rule` and `inv_transfer_approval_node` API payload unchanged. The page will normalize every rule to sequential all-node approval (`approvalMode = all_nodes`) and every approval row to one ordered position (`node.approvalMode = any_one`, `requiredCount = 1`). Existing saved rules remain editable through the same API, but the page defaults and labels stop emphasizing old "发货方/收货方负责人" wording.

**Tech Stack:** Vue 2, Element UI, existing `@/api/inventory/transferApprovalRule`, existing `@/api/system/post`, existing `@/api/system/dept`.

---

## Scope

This plan changes only the transfer approval configuration page:

- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

Do not modify:

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- SQL schema files
- transfer processing or records pages

The existing backend already advances approval tasks in node order. This page change makes that existing behavior easy to configure as "第 1 级岗位 -> 第 2 级岗位 -> 第 3 级岗位".

## File Structure

- `erp-ui/src/views/inventory/transfer/rules.vue`
  - Owns the list, dialog, node editing table, form validation, payload normalization, and preview text for transfer approval configuration.
  - Keep the component as one file because the existing codebase uses single-file Element UI pages for inventory management screens.
  - Do not introduce new child components for this narrow change.

## Task 1: Baseline Current Page Behavior

**Files:**
- Read: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Capture current approval configuration labels**

Run:

```bash
rg -n "通过方式|审批节点|节点角色|岗位编码|岗位名称|defaultNode|approvalModeOptions|nodeRoleOptions|validateBeforeSubmit|buildSubmitForm" erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- The command finds the rule-level approval selector.
- The command finds the node table currently labeled `审批节点`.
- The command finds `defaultNode()` defaulting to `发货方负责人审批`, `from_leader`, `dz`, `店长`.

- [ ] **Step 2: Confirm no backend changes are required for ordered nodes**

Run:

```bash
rg -n "completeNodeIfReady|resolveNextNodeOrder|isInstanceComplete|APPROVAL_ALL_NODES" erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java
```

Expected:

- The command shows the backend already checks current node completion, then advances to the next node order.

- [ ] **Step 3: Commit nothing after baseline**

Run:

```bash
git status --short erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- The file may already be dirty from previous work; do not revert it.
- No new changes are made by Task 1.

## Task 2: Simplify Rule-Level Approval Strategy

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Change the rule-level approval selector label**

In the template, replace the existing rule-level form item:

```vue
<el-form-item label="通过方式" prop="approvalMode">
  <el-select v-model="form.approvalMode" placeholder="请选择通过方式" style="width:100%">
    <el-option v-for="item in approvalModeOptions" :key="item.value" :label="item.label" :value="item.value"/>
  </el-select>
</el-form-item>
```

with:

```vue
<el-form-item label="整体策略" prop="approvalMode">
  <el-select v-model="form.approvalMode" disabled style="width:100%">
    <el-option v-for="item in approvalModeOptions" :key="item.value" :label="item.label" :value="item.value"/>
  </el-select>
</el-form-item>
```

Rationale:

- Ordered multi-position approval maps to `all_nodes`.
- Users should configure the order in the node table, not choose a global mode that can contradict the ordered UI.

- [ ] **Step 2: Remove rule-level specified count UI**

Delete this block:

```vue
<el-col :span="8" v-if="form.approvalMode === 'quorum'">
  <el-form-item label="指定人数" prop="requiredCount">
    <el-input-number v-model="form.requiredCount" :min="1" :precision="0" style="width:100%"/>
  </el-form-item>
</el-col>
```

Expected:

- The page no longer exposes rule-level `quorum`.
- `requiredCount` still exists in the form object for API compatibility.

- [ ] **Step 3: Update approval mode labels**

Replace `approvalModeOptions` with:

```js
approvalModeOptions: [
  { label: "按顺序全部审批", value: "all_nodes" },
  { label: "任一审批通过", value: "any_one" },
  { label: "所有岗位通过", value: "all_posts" },
  { label: "指定人数通过", value: "quorum" }
],
```

Expected:

- Existing list rows still display a meaningful label for old rules.
- New/edit dialog displays `按顺序全部审批` as disabled global strategy.

## Task 3: Redesign the Approval Node Table as Ordered Positions

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Rename the divider and add a preview line**

Replace:

```vue
<el-divider content-position="left">审批节点</el-divider>
```

with:

```vue
<el-divider content-position="left">审批顺序</el-divider>
<el-alert
  v-if="approvalPreviewText"
  :title="approvalPreviewText"
  type="info"
  :closable="false"
  show-icon
  class="approval-preview"
/>
```

Expected:

- The dialog shows a clear ordered preview such as `审批顺序：店长 -> 运营经理 -> 运营总监`.

- [ ] **Step 2: Rename node table columns**

Change table column labels:

```vue
<el-table-column label="顺序" width="90">
```

to:

```vue
<el-table-column label="审批级别" width="96">
```

Change:

```vue
<el-table-column label="节点名称" min-width="150">
```

to:

```vue
<el-table-column label="显示名称" min-width="150">
```

Change:

```vue
<el-table-column label="节点角色" width="150">
```

to:

```vue
<el-table-column label="审批范围" width="150">
```

Expected:

- The table reads as an ordered position list.

- [ ] **Step 3: Remove per-node approval mode and count columns**

Delete these two table columns:

```vue
<el-table-column label="通过方式" width="140">
  <template slot-scope="scope">
    <el-select v-model="scope.row.approvalMode" size="mini" style="width:100%">
      <el-option v-for="item in approvalModeOptions" :key="item.value" :label="item.label" :value="item.value"/>
    </el-select>
  </template>
</el-table-column>
<el-table-column label="指定人数" width="110">
  <template slot-scope="scope">
    <el-input-number v-model="scope.row.requiredCount" :min="1" :precision="0" size="mini" controls-position="right" class="count-input"/>
  </template>
</el-table-column>
```

Expected:

- Each row means one ordered position.
- If a position has multiple users with the same post, any one user in that post can approve that level.

- [ ] **Step 4: Rename add button**

Replace:

```vue
<el-button type="primary" size="mini" icon="el-icon-plus" plain class="mt8" @click="addNode">添加节点</el-button>
```

with:

```vue
<el-button type="primary" size="mini" icon="el-icon-plus" plain class="mt8" @click="addNode">添加审批岗位</el-button>
```

Expected:

- Button text matches the user task: multiple positions approve in sequence.

## Task 4: Update Defaults and Preview Computation

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Add computed approval preview**

Add this `computed` block after `created()` and before `methods`:

```js
computed: {
  approvalPreviewText() {
    const nodes = this.form && this.form.nodes ? this.form.nodes : []
    const names = nodes
      .slice()
      .sort((a, b) => Number(a.nodeOrder || 0) - Number(b.nodeOrder || 0))
      .map(node => node.postName || node.postCode || node.nodeName)
      .filter(Boolean)
    return names.length ? "审批顺序：" + names.join(" -> ") : ""
  }
},
```

Expected:

- The alert added in Task 3 renders a clear ordered approval preview.

- [ ] **Step 2: Update `emptyForm()` defaults**

In `emptyForm()`, ensure these values are set:

```js
approvalMode: "all_nodes",
requiredCount: 0,
nodes: [this.defaultNode(1)]
```

Expected:

- New rules default to ordered all-node approval.

- [ ] **Step 3: Replace `defaultNode(order)`**

Replace the current `defaultNode(order)` with:

```js
defaultNode(order) {
  return {
    nodeOrder: order,
    nodeName: "第 " + order + " 级审批",
    nodeRole: "post",
    postId: undefined,
    postCode: "",
    postName: "",
    approvalMode: "any_one",
    requiredCount: 1
  }
},
```

Expected:

- New rows no longer default to `发货方负责人审批`.
- New rows no longer default to `店长`, forcing the user to choose the intended position.

- [ ] **Step 4: Update node role labels without removing legacy values**

Replace `nodeRoleOptions` with:

```js
nodeRoleOptions: [
  { label: "按岗位审批", value: "post" },
  { label: "发货方负责人", value: "from_leader" },
  { label: "收货方负责人", value: "to_leader" },
  { label: "区域经理", value: "area_manager" },
  { label: "运营经理", value: "operator_manager" }
],
```

Expected:

- New default is `按岗位审批`.
- Existing old rules can still render their stored values.

## Task 5: Normalize Validation and Submit Payload

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Replace node validation**

In `validateBeforeSubmit()`, replace:

```js
if (this.form.approvalMode === "quorum" && (!this.form.requiredCount || this.form.requiredCount <= 0)) {
  this.$modal.msgError("指定人数必须大于0")
  return false
}
const invalidNode = this.form.nodes.some(node => !node.nodeName || !node.nodeRole || !node.approvalMode)
if (invalidNode) {
  this.$modal.msgError("请完整填写审批节点")
  return false
}
const invalidQuorumNode = this.form.nodes.some(node => node.approvalMode === "quorum" && (!node.requiredCount || node.requiredCount <= 0))
if (invalidQuorumNode) {
  this.$modal.msgError("节点指定人数必须大于0")
  return false
}
```

with:

```js
const invalidNode = this.form.nodes.some(node => !node.nodeName || !node.nodeRole || !node.postCode || !node.postName)
if (invalidNode) {
  this.$modal.msgError("请完整填写每一级审批岗位")
  return false
}
```

Expected:

- Users must select a position for every approval level.
- The page no longer validates hidden quorum fields.

- [ ] **Step 2: Normalize the payload in `buildSubmitForm()`**

Replace the payload construction in `buildSubmitForm()` with:

```js
const payload = Object.assign({}, this.form, {
  documentType: "transfer",
  approvalMode: "all_nodes",
  scopeId: this.form.scopeType === "all" ? undefined : this.form.scopeId,
  conditionValue: this.form.conditionType === "none" ? 0 : this.form.conditionValue,
  requiredCount: 0,
  nodes: this.form.nodes.map(node => Object.assign({}, node, {
    approvalMode: "any_one",
    requiredCount: 1
  }))
})
return payload
```

Expected:

- The backend receives the existing schema.
- All new page-created rules become ordered sequential rules.
- Each approval level lets any user in that configured post approve that level.

- [ ] **Step 3: Keep `syncNodePost()` behavior**

Keep:

```js
syncNodePost(node, postCode) {
  const post = this.postOptions.find(item => item.postCode === postCode)
  if (!post) return
  node.postId = post.postId
  node.postCode = post.postCode
  node.postName = post.postName
},
```

Expected:

- Selecting a post code still fills `postId`, `postCode`, and `postName`.

## Task 6: Improve Reordering Names and Styling

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Update `renumberNodes()` to keep generic names in sync**

Replace `renumberNodes()` with:

```js
renumberNodes() {
  const nodes = this.form.nodes || []
  nodes.forEach((node, index) => {
    const nextOrder = index + 1
    const oldGenericName = /^第\s*\d+\s*级审批$/.test(node.nodeName || "") || /^审批节点\d+$/.test(node.nodeName || "")
    node.nodeOrder = nextOrder
    if (!node.nodeName || oldGenericName) {
      node.nodeName = "第 " + nextOrder + " 级审批"
    }
  })
},
```

Expected:

- Moving rows updates generic labels.
- Custom labels entered by users are preserved.

- [ ] **Step 2: Add alert spacing style**

Add this style block content:

```scss
.approval-preview {
  margin-bottom: 8px;
}
```

Expected:

- The preview alert does not visually collide with the table.

- [ ] **Step 3: Remove unused count input style if no longer referenced**

If `count-input` is no longer referenced in the template, change:

```scss
.order-input,
.count-input {
  width: 72px;
}
```

to:

```scss
.order-input {
  width: 72px;
}
```

Expected:

- The style block reflects the simplified table.

## Task 7: Verify the Page Build

**Files:**
- Verify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Run a focused syntax search**

Run:

```bash
rg -n "approvalPreviewText|添加审批岗位|审批顺序|整体策略|指定人数|count-input" erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- `approvalPreviewText`, `添加审批岗位`, `审批顺序`, and `整体策略` are found.
- `指定人数` and `count-input` are not found in the node table area after this page simplification.

- [ ] **Step 2: Run frontend production build**

Run:

```bash
npm --prefix erp-ui run build:prod
```

Expected:

- Exit code `0`.
- Existing chunk-size warnings are acceptable.
- Syntax or template compilation errors are not acceptable.

- [ ] **Step 3: If the build fails, inspect the failing file and fix only this page**

Run:

```bash
git diff -- erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- The diff only includes the intended page redesign.
- Do not modify unrelated files to make the build pass unless the failure points directly to this page.

## Task 8: Manual UI Verification

**Files:**
- Verify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Open the running UI**

Use the existing ERP frontend dev workflow. If a dev server is already running, open the current route for transfer approval configuration. If no server is running, start the frontend with the existing project command used in this repo.

Expected:

- The `调拨审批配置` menu opens without a blank page.

- [ ] **Step 2: Create a sequential approval rule in the dialog**

In the dialog:

- Set rule name: `门店要货顺序审批`
- Keep overall strategy: `按顺序全部审批`
- Add three approval positions:
  - `第 1 级审批`: `店长`
  - `第 2 级审批`: `运营经理`
  - `第 3 级审批`: `运营总监`

Expected:

- Preview reads `审批顺序：店长 -> 运营经理 -> 运营总监`.
- The confirm button is enabled after every row has a post code and post name.

- [ ] **Step 3: Save and re-open the rule**

Expected:

- The saved rule opens with the same ordered positions.
- The preview still renders in the same order.
- The page does not show confusing `发货方负责人审批` defaults for new rows.

## Task 9: Commit the Page Change

**Files:**
- Commit: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Check whitespace**

Run:

```bash
git diff --check -- erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- No output.
- Exit code `0`.

- [ ] **Step 2: Stage only this page**

Run:

```bash
git add erp-ui/src/views/inventory/transfer/rules.vue
```

Expected:

- Only this page is staged.

- [ ] **Step 3: Confirm staged file list**

Run:

```bash
git diff --cached --name-only
```

Expected:

```text
erp-ui/src/views/inventory/transfer/rules.vue
```

- [ ] **Step 4: Commit**

Run:

```bash
git commit -m "feat: simplify transfer approval rule sequencing"
```

Expected:

- A new commit is created for the page redesign.

## Self-Review Checklist

- The plan only modifies the transfer approval configuration page.
- The plan keeps backend rule payload fields compatible.
- The plan preserves ordered approval behavior through `approvalMode = all_nodes`.
- The plan does not change transfer order creation, delivery, receipt, or records logic.
- The plan includes build verification and manual UI verification.
