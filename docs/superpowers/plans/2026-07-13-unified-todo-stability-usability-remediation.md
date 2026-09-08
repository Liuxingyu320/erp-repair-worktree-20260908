# Unified Todo Stability and Usability Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the unified todo runtime, make HeaderTodo the single business-todo entry, and make desktop/mobile filtering, degraded counts, empty states, keyboard use, and touch targets reliable.

**Architecture:** Keep inventory, OA, and system Providers as the only business fact sources and keep Todo Store as the only summary polling authority. Add one small filter-query utility shared by desktop and mobile, centralize valid inventory-context detection in `shopContext`, and make each todo card one semantic full-card button. Fix Spring wiring and notification/signing refresh regressions without changing provider business rules.

**Tech Stack:** Java 17, Spring Boot 3/Spring Framework 6, JUnit 5, AssertJ, Vue 2.6, Vuex 3, Vue Router 3, Element UI, Node `assert`/`vm` contract tests, Maven, npm.

---

## File map and responsibility boundaries

- `erp-modules/erp-system/src/main/java/com/erp/system/service/push/ApnsPushDeliveryClient.java`: APNs production constructor wiring only.
- `erp-modules/erp-system/src/main/java/com/erp/system/service/push/FirebasePushDeliveryClient.java`: FCM production constructor wiring only.
- `erp-modules/erp-system/src/test/java/com/erp/system/service/push/PushDeliveryClientSpringWiringTest.java`: proves both production clients can be created by Spring without real push credentials.
- `erp-ui/src/layout/components/HeaderNotice/index.vue`: announcements and personal messages only; no business-todo entry or polling.
- `erp-ui/src/views/oa/signTask/index.vue`: read-only task-center refresh.
- `erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue`: post-mutation todo invalidation.
- `erp-ui/src/utils/shopContext.js`: one pure valid store/warehouse context predicate plus existing session-backed helpers.
- `erp-ui/src/utils/todoFilterQuery.js`: filter query whitelist, canonicalization, equality, active-filter detection, and safe pagination bounds.
- `erp-ui/src/views/workbench/todo/index.vue`: desktop route/filter synchronization, safe scope, empty reset, and semantic rows.
- `erp-ui/src/views/mobile/todo/index.vue`: mobile route/filter synchronization, selected-source count semantics, empty reset, semantic cards, and 44px controls.
- Existing Node tests remain focused by concern; create only `erp-ui/test/unifiedTodoFilterQuery.test.js` for the new pure query utility.

Execution must preserve unrelated dirty files. Stage and commit only the exact files listed by each task. All service and UI verification uses local host processes; do not use Docker, Testcontainers, or any virtual machine.

### Task 1: Repair Spring wiring for both push delivery clients

**Files:**
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/push/PushDeliveryClientSpringWiringTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/ApnsPushDeliveryClient.java:9-48`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/FirebasePushDeliveryClient.java:8-42`

- [ ] **Step 1: Write the failing Spring wiring test**

```java
package com.erp.system.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.erp.system.config.PushNotificationProperties;

class PushDeliveryClientSpringWiringTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void springCreatesBothProductionClientsWithoutDefaultConstructors()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PushNotificationProperties.class);
            assertThat(context).hasSingleBean(ApnsPushDeliveryClient.class);
            assertThat(context).hasSingleBean(FirebasePushDeliveryClient.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            PushNotificationProperties.class,
            ApnsPushDeliveryClient.class,
            FirebasePushDeliveryClient.class
    })
    static class TestConfiguration
    {
    }
}
```

- [ ] **Step 2: Run the focused test and verify the current failure**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=PushDeliveryClientSpringWiringTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL; the context reports `No default constructor found` for `ApnsPushDeliveryClient` before implementation.

- [ ] **Step 3: Mark both production constructors as injection constructors**

Add this import to both clients:

```java
import org.springframework.beans.factory.annotation.Autowired;
```

Annotate the public constructor in `ApnsPushDeliveryClient`:

```java
@Autowired
public ApnsPushDeliveryClient(PushNotificationProperties properties)
{
    this(properties, null);
}
```

Annotate the public constructor in `FirebasePushDeliveryClient`:

```java
@Autowired
public FirebasePushDeliveryClient(PushNotificationProperties properties)
{
    this(properties, null);
}
```

Do not annotate the package-private gateway constructors and do not initialize either network client during bean creation.

- [ ] **Step 4: Run wiring and existing push tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=PushDeliveryClientSpringWiringTest,ApnsPushDeliveryClientTest,FirebasePushDeliveryClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS; Spring creates both clients and existing disabled/lazy-delivery tests remain green.

- [ ] **Step 5: Commit the isolated backend repair**

```bash
git add \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/ApnsPushDeliveryClient.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/FirebasePushDeliveryClient.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/push/PushDeliveryClientSpringWiringTest.java
git commit -m "fix: restore system push client startup wiring"
```

### Task 2: Restore header notification ownership and refresh contracts

**Files:**
- Modify: `erp-ui/test/headerNoticeReadAll.test.js:8-52`
- Modify: `erp-ui/test/targetedUserNotification.test.js:42-48`
- Modify: `erp-ui/test/signTaskCenter.test.js:35-107`
- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue:1-211`
- Modify: `erp-ui/src/views/oa/signTask/index.vue:198-204`
- Modify: `erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue:444-489`

- [ ] **Step 1: Strengthen the failing frontend contracts**

Add to `headerNoticeReadAll.test.js`:

```js
assert.ok(!componentSource.includes("业务待办"),
  "HeaderNotice must not expose a second business-todo entry")
assert.ok(!/todo\/refresh['"]/.test(componentSource),
  "HeaderNotice must not dispatch the removed todo/refresh action")
assert.ok(!componentSource.includes("todoTotal") && !componentSource.includes("checkPermi"),
  "HeaderNotice must not own todo totals or todo routing")
assert.ok(componentSource.includes('type="button" class="notice-mark-all"'),
  "mark-all must be a semantic keyboard-operable button")
```

Update its local-state assertion and duplicate guard to use the current counter name:

```js
assert.ok(
  !markAllMethod[1].includes("this.noticeUnreadCount = 0") &&
    !markAllMethod[1].includes("this.noticeList = this.noticeList.map"),
  "HeaderNotice must not pretend success by clearing local state"
)
assert.ok(
  componentSource.includes("noticeMarkingAll") &&
    /if\s*\(this\.noticeMarkingAll\s*\|\|\s*this\.noticeLoading\s*\|\|\s*this\.noticeUnreadCount\s*<=\s*0/.test(markAllMethod[1]),
  "HeaderNotice should prevent duplicate or meaningless mark-all clicks"
)
```

Replace the HeaderNotice expectations in `targetedUserNotification.test.js` with:

```js
const header = read("src/layout/components/HeaderNotice/index.vue")
;["通知公告", "我的消息"].forEach(label => {
  assert.ok(header.includes(label), `header notice group should keep ${label}`)
})
assert.ok(!header.includes("业务待办") && !header.includes("todoTotal"),
  "business todos must remain owned by HeaderTodo")
;["noticeUnreadCount", "messageUnreadCount"].forEach(counter => {
  assert.ok(header.includes(counter), `header should use independent counter ${counter}`)
})
```

Add to `signTaskCenter.test.js` after loading `page` and `detail`:

```js
assert.ok(page.includes("todo/refreshSummaries") && !/todo\/refresh['"]/.test(page),
  "read-only task-center refresh must use refreshSummaries")
assert.ok(detail.includes("todo/invalidateAfterMutation") && !/todo\/refresh['"]/.test(detail),
  "successful signing mutations must invalidate the todo cache")
assert.ok(!/finally\s*\(\s*\(\)\s*=>\s*\{[\s\S]*this\.refreshTodo\(\)/.test(detail),
  "failed signing mutations must not be reported as successful todo invalidations")
```

- [ ] **Step 2: Run the three tests and verify they fail for the known regressions**

Run:

```bash
cd erp-ui
for f in test/headerNoticeReadAll.test.js test/targetedUserNotification.test.js test/signTaskCenter.test.js; do
  node "$f" || exit 1
done
```

Expected: FAIL on missing async mark-all, the duplicate business-todo entry, or removed `todo/refresh` action usage.

- [ ] **Step 3: Reduce HeaderNotice to announcements and personal messages**

In `HeaderNotice/index.vue`:

1. Remove the first `业务待办` button.
2. Remove `checkPermi`, the `todoTotal` computed property, `openTodoCenter`, both `todo/refresh` dispatches, and `.todo-badge`.
3. Add `noticeMarkingAll: false` to data.
4. Keep the interval but make it refresh only personal-message unread count.
5. Refresh announcements when the popover is opened if no announcement load is already active.

Use these methods:

```js
refreshHeaderCounts() {
  return this.loadMessageUnreadCount()
},
onNoticeEnter() {
  clearTimeout(this.noticeLeaveTimer)
  this.noticeVisible = true
  if (!this.noticeLoading && !this.noticeMarkingAll) this.loadNoticeTop()
  this.$nextTick(() => {
    const popover = this.$refs.noticePopover
    const popper = popover && popover.$refs ? popover.$refs.popper : null
    if (popper && !popper._noticeBound) {
      popper._noticeBound = true
      popper.addEventListener('mouseenter', () => clearTimeout(this.noticeLeaveTimer))
      popper.addEventListener('mouseleave', () => {
        this.noticeLeaveTimer = setTimeout(() => { this.noticeVisible = false }, 100)
      })
    }
  })
},
async markAllRead() {
  if (this.noticeMarkingAll || this.noticeLoading || this.noticeUnreadCount <= 0) return
  this.noticeMarkingAll = true
  try {
    await markNoticeReadAll()
    await this.loadNoticeTop()
  } catch (error) {
    // The request layer owns feedback; preserve the authoritative current state.
  } finally {
    this.noticeMarkingAll = false
  }
}
```

Render the mark-all control as a semantic button with a disabled state and progress label:

```vue
<button
  type="button"
  class="notice-mark-all"
  :disabled="noticeMarkingAll || noticeLoading || noticeUnreadCount <= 0"
  @click="markAllRead"
>{{ noticeMarkingAll ? '处理中...' : '全部已读' }}</button>
```

Use button-reset styles while preserving the existing link appearance:

```scss
.notice-popover .notice-mark-all {
  padding: 0;
  border: 0;
  background: transparent;
  color: #409eff;
  font: inherit;
  font-size: 12px;
  font-weight: normal;
  cursor: pointer;
}
.notice-popover .notice-mark-all:disabled { opacity: .5; cursor: not-allowed; }
```

- [ ] **Step 4: Replace stale signing refresh actions on success paths**

In `signTask/index.vue`, change the read-only refresh line to:

```js
this.$store.dispatch('todo/refreshSummaries').catch(() => {})
```

In `SignTaskDetailDrawer.vue`, use:

```js
refreshTodo() {
  return this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
}
```

Replace the two mutation flows with these complete success-only refresh paths:

```js
retryNotification() {
  if (!this.notificationBusinessKey) return Promise.resolve()
  this.actionLoading = true
  return retrySignTaskNotification(this.task.taskId, {
    requestId: this.createRequestId('notification'),
    businessKey: this.notificationBusinessKey
  }).then(() => {
    this.$modal.msgSuccess('失败通知已重新入队')
    this.$emit('notification-retried')
    this.refreshTodo()
    return this.loadDetail()
  }).finally(() => {
    this.actionLoading = false
  })
},
runDetailAction(action, successMessage) {
  if (this.actionLoading) return Promise.resolve()
  const requestedTaskId = String(this.task.taskId)
  const requestSequence = ++this.actionRequestSequence
  this.actionLoading = true
  return action().then(response => {
    const nextDetail = response && response.data && response.data.task ? response.data : null
    const targetIsCurrent = requestSequence === this.actionRequestSequence &&
      requestedTaskId === String(this.taskId) && this.visible
    if (nextDetail && targetIsCurrent) this.detail = nextDetail
    if (nextDetail && nextDetail.task.status === 'FAILED') {
      this.$modal.msgError(nextDetail.task.failureDetail || '任务处理失败，请查看原因后重试')
    } else {
      this.$modal.msgSuccess(successMessage)
    }
    this.$emit('updated', nextDetail)
    this.refreshTodo()
    return targetIsCurrent ? this.loadDetail() : null
  }).finally(() => {
    if (requestSequence === this.actionRequestSequence) this.actionLoading = false
  })
}
```

- [ ] **Step 5: Run the focused contracts and all unified todo contracts**

Run:

```bash
cd erp-ui
for f in test/headerNoticeReadAll.test.js test/targetedUserNotification.test.js test/signTaskCenter.test.js test/unifiedTodo*.test.js; do
  node "$f" || exit 1
done
```

Expected: every listed test prints its `tests passed` line and exits 0.

- [ ] **Step 6: Commit the header and refresh repair**

```bash
git add \
  erp-ui/test/headerNoticeReadAll.test.js \
  erp-ui/test/targetedUserNotification.test.js \
  erp-ui/test/signTaskCenter.test.js \
  erp-ui/src/layout/components/HeaderNotice/index.vue \
  erp-ui/src/views/oa/signTask/index.vue \
  erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue
git commit -m "fix: unify todo and notification refresh ownership"
```

### Task 3: Centralize organization validity and selected-source count semantics

**Files:**
- Modify: `erp-ui/test/shopContextUx.test.js:55-96`
- Modify: `erp-ui/test/unifiedTodoDesktop.test.js:31-100`
- Modify: `erp-ui/test/unifiedTodoMobile.test.js:110-380`
- Modify: `erp-ui/src/utils/shopContext.js:11-90`
- Modify: `erp-ui/src/layout/components/HeaderTodo/index.vue:52-85`
- Modify: `erp-ui/src/views/workbench/todo/index.vue:134-240`
- Modify: `erp-ui/src/views/mobile/todo/index.vue:113-272`

- [ ] **Step 1: Add failing contracts for the shared scope and count rules**

Expose `hasValidInventoryDeptContext` in the `shopContextUx.test.js` VM export list and add:

```js
assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "9", deptType: "STORE" }), true)
assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: 10, deptType: "warehouse" }), true)
assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "", deptType: "STORE" }), false)
assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "9", deptType: "COMPANY" }), false)
assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "9", deptType: "" }), false)
```

Add desktop source contracts:

```js
assert.ok(todoPage.includes("hasValidInventoryDeptContext"),
  "desktop todo must use the shared current-organization predicate")
assert.ok(todoPage.includes(':disabled="!hasCurrentOrgContext"'),
  "desktop todo must disable current organization without a valid context")
assert.ok(!todoPage.includes("已驳回") && todoPage.includes("退回修改"),
  "returned todos must be presented as actionable changes")
assert.ok(!headerTodo.includes("已驳回") && headerTodo.includes("退回修改"),
  "header returned todos must use the same actionable wording")
```

Add this dependency to the desktop `loadVueComponent` VM context so `data()` can use the shared predicate:

```js
hasValidInventoryDeptContext: context => !!(context && context.deptId &&
  ["STORE", "WAREHOUSE"].includes(String(context.deptType || "").toUpperCase()))
```

Add mobile behavior contracts using provider summaries:

```js
const countContext = {
  selectedSources: ["oa", "system"],
  providerStates: {
    inventory: { summary: { approval: 100 }, status: "fresh" },
    oa: { summary: { approval: 4 }, status: "fresh" },
    system: { summary: null, unknown: true, status: "unknown" }
  },
  hasUnknownProvider: true,
  hasPendingProvider: false
}
Object.assign(countContext, component.methods)
Object.defineProperty(countContext, "selectedCounts", {
  get() { return component.computed.selectedCounts.call(countContext) }
})
assert.strictEqual(countContext.countFor("approval"), "4+",
  "mobile counts must include only selected known providers and mark unknown remainder")
countContext.providerStates.oa.summary.approval = 0
assert.strictEqual(countContext.countFor("approval"), "?",
  "an unknown selected source must not become a trusted zero")
```

Before loading the mobile component, add these dependencies to the existing VM context:

```js
hasValidInventoryDeptContext: context => !!(context && context.deptId &&
  ["STORE", "WAREHOUSE"].includes(String(context.deptType || "").toUpperCase())),
aggregateSummaries: entries => ({
  counts: (entries || []).reduce((counts, entry) => {
    for (const category of ["approval", "execution", "returned", "risk", "personal"]) {
      counts[category] += Number(entry.summary && entry.summary[category]) || 0
    }
    counts.total = counts.approval + counts.execution + counts.returned + counts.risk + counts.personal
    return counts
  }, { total: 0, approval: 0, execution: 0, returned: 0, risk: 0, personal: 0 })
})
```

Also assert the mobile source imports `hasValidInventoryDeptContext`, does not define local `hasValidTodoContext`, and contains `退回修改` rather than the category label `驳回`.

- [ ] **Step 2: Run the three tests and verify failure**

Run:

```bash
cd erp-ui
for f in test/shopContextUx.test.js test/unifiedTodoDesktop.test.js test/unifiedTodoMobile.test.js; do
  node "$f" || exit 1
done
```

Expected: FAIL because the pure predicate, desktop guard, actionable wording, and selected-source counts are not implemented.

- [ ] **Step 3: Add the pure organization predicate**

Add to `shopContext.js`:

```js
export function hasValidInventoryDeptContext(context = {}) {
  const deptId = context.deptId
  const hasDeptId = deptId !== undefined && deptId !== null && String(deptId).trim() !== ""
  return hasDeptId && isValidInventoryDeptType(context.deptType)
}
```

Replace `hasSelectedInventoryDeptContext` with:

```js
export function hasSelectedInventoryDeptContext() {
  return hasValidInventoryDeptContext(getSelectedDeptContext())
}
```

- [ ] **Step 4: Apply the shared rule and actionable wording**

In both todo pages import `hasValidInventoryDeptContext` with `getSelectedDeptContext`. Delete the mobile-local `hasValidTodoContext` function. Use a `contextSnapshot` in both components and compute:

```js
hasCurrentOrgContext() {
  return hasValidInventoryDeptContext(this.contextSnapshot)
}
```

Initialize `scopeMode` to `current_org` only when that computed predicate would be true; otherwise use `all_authorized`. Disable the desktop current-org radio button when false. Normalize every route or user-supplied `current_org` value back to `all_authorized` when false.

Change unified-todo category labels only:

```js
returned: '退回修改'
```

Do not change business status labels elsewhere in OA or inventory pages.

- [ ] **Step 5: Derive mobile counts from selected Provider summaries**

Import `aggregateSummaries` from `@/utils/todoAggregator` and replace global `todoTotal`/`todoCounts` use in the mobile unified page with:

```js
selectedCounts() {
  const summaries = this.selectedSources
    .map(source => ({ source, summary: this.providerStates[source] && this.providerStates[source].summary }))
    .filter(entry => entry.summary !== null && entry.summary !== undefined)
  return aggregateSummaries(summaries).counts
},
todoTotalDisplay() {
  const total = Number(this.selectedCounts.total) || 0
  if (this.hasPendingProvider) return total ? `${total}+` : '加载中'
  if (this.hasUnknownProvider) return total ? `${total}+` : '未知'
  return total
}
```

Use this method:

```js
countFor(category) {
  const value = Number(this.selectedCounts && this.selectedCounts[category])
  const count = Number.isFinite(value) ? Math.max(0, value) : 0
  if (this.hasUnknownProvider) return count ? `${count}+` : '?'
  if (this.hasPendingProvider) return count ? `${count}+` : '…'
  return count
}
```

- [ ] **Step 6: Run scope, desktop, mobile, aggregation, and store tests**

Run:

```bash
cd erp-ui
for f in \
  test/shopContextUx.test.js \
  test/unifiedTodoAggregation.test.js \
  test/unifiedTodoStore.test.js \
  test/unifiedTodoDesktop.test.js \
  test/unifiedTodoMobile.test.js; do
  node "$f" || exit 1
done
```

Expected: PASS with no exact zero shown for an unknown selected source.

- [ ] **Step 7: Commit scope and count semantics**

```bash
git add \
  erp-ui/test/shopContextUx.test.js \
  erp-ui/test/unifiedTodoDesktop.test.js \
  erp-ui/test/unifiedTodoMobile.test.js \
  erp-ui/src/utils/shopContext.js \
  erp-ui/src/layout/components/HeaderTodo/index.vue \
  erp-ui/src/views/workbench/todo/index.vue \
  erp-ui/src/views/mobile/todo/index.vue
git commit -m "fix: align unified todo scope and count semantics"
```

### Task 4: Add a shared safe todo filter-query contract

**Files:**
- Create: `erp-ui/src/utils/todoFilterQuery.js`
- Create: `erp-ui/test/unifiedTodoFilterQuery.test.js`

- [ ] **Step 1: Write the failing pure utility test**

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const file = path.resolve(__dirname, "../src/utils/todoFilterQuery.js")
assert.ok(fs.existsSync(file), "shared todo filter query utility must exist")
let source = fs.readFileSync(file, "utf8")
  .replace(/export\s+const\s+/g, "const ")
  .replace(/export\s+function\s+/g, "function ")
source += `
module.exports = {
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
}`
const sandbox = { module: { exports: {} }, exports: {}, Object, Array, String, Number, JSON }
vm.runInNewContext(source, sandbox, { filename: file })
const api = sandbox.module.exports
const plain = value => JSON.parse(JSON.stringify(value))

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery({}, { hasCurrentOrgContext: true })), {
  category: "all", source: "all", scopeMode: "current_org", keyword: ""
})
assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { category: "bad", source: "bad", scope: "current_org", keyword: ["  needle  "] },
  { hasCurrentOrgContext: false }
)), { category: "all", source: "all", scopeMode: "all_authorized", keyword: "needle" })
assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { category: "approval", source: "oa", scope: "all_authorized", pageNum: "3", pageSize: "20" },
  { hasCurrentOrgContext: true, includePagination: true }
)), {
  category: "approval", source: "oa", scopeMode: "all_authorized", keyword: "", pageNum: 3, pageSize: 20
})
assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { pageNum: "999999", pageSize: "100" },
  { hasCurrentOrgContext: true, includePagination: true }
)), {
  category: "all", source: "all", scopeMode: "current_org", keyword: "", pageNum: 1000, pageSize: 100
})
const filters = { category: "risk", source: "inventory", scopeMode: "current_org", keyword: "缺货", pageNum: 2, pageSize: 10 }
assert.deepStrictEqual(plain(api.buildTodoFilterQuery(filters, {
  hasCurrentOrgContext: true, includePagination: true
})), {
  category: "risk", source: "inventory", scope: "current_org", keyword: "缺货", pageNum: "2", pageSize: "10"
})
assert.strictEqual(api.todoRouteQueryMatches({
  category: "risk", source: "inventory", scope: "current_org", keyword: "缺货", pageNum: "2", pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), true)
assert.strictEqual(api.todoRouteQueryMatches({
  category: "all", source: "all", scope: "current_org"
}, { category: "all", source: "all", scopeMode: "all_authorized", keyword: "" }, {
  hasCurrentOrgContext: false
}), false, "an unsafe raw scope must be replaced by its canonical safe URL")
assert.strictEqual(api.todoFilterStateEquals(filters, { ...filters }, {
  hasCurrentOrgContext: true, includePagination: true
}), true)
assert.strictEqual(api.hasActiveTodoFilters(filters, { hasCurrentOrgContext: true }), true)
assert.strictEqual(api.hasActiveTodoFilters({
  category: "all", source: "all", scopeMode: "current_org", keyword: ""
}, { hasCurrentOrgContext: true }), false)

console.log("unifiedTodoFilterQuery tests passed")
```

- [ ] **Step 2: Run the test and verify the file-missing failure**

Run:

```bash
cd erp-ui && node test/unifiedTodoFilterQuery.test.js
```

Expected: FAIL with `shared todo filter query utility must exist`.

- [ ] **Step 3: Implement the complete pure utility**

```js
export const TODO_CATEGORY_VALUES = Object.freeze(['all', 'approval', 'execution', 'returned', 'risk', 'personal'])
export const TODO_SOURCE_VALUES = Object.freeze(['all', 'inventory', 'oa', 'system'])
export const TODO_SCOPE_VALUES = Object.freeze(['current_org', 'all_authorized'])

const TODO_PAGE_SIZES = Object.freeze([10, 20, 30, 50, 100])
const MAX_TODO_PREFIX = 100000

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function cleanText(value, maxLength = 100) {
  if (value === undefined || value === null) return ''
  return String(firstQueryValue(value)).trim().slice(0, maxLength)
}

function allowedValue(value, allowed, fallback) {
  const normalized = cleanText(value)
  return allowed.includes(normalized) ? normalized : fallback
}

function positiveInteger(value, fallback) {
  const number = Number(firstQueryValue(value))
  return Number.isSafeInteger(number) && number > 0 ? number : fallback
}

export function normalizeTodoFilterQuery(query = {}, options = {}) {
  const hasCurrentOrgContext = options.hasCurrentOrgContext === true
  const includePagination = options.includePagination === true
  const defaultScope = hasCurrentOrgContext ? 'current_org' : 'all_authorized'
  const requestedScope = allowedValue(query.scope === undefined ? query.scopeMode : query.scope,
    TODO_SCOPE_VALUES, defaultScope)
  const scopeMode = requestedScope === 'current_org' && !hasCurrentOrgContext
    ? 'all_authorized'
    : requestedScope
  const normalized = {
    category: allowedValue(query.category, TODO_CATEGORY_VALUES, 'all'),
    source: allowedValue(query.source, TODO_SOURCE_VALUES, 'all'),
    scopeMode,
    keyword: cleanText(query.keyword)
  }
  if (!includePagination) return normalized
  const requestedPageSize = positiveInteger(query.pageSize, 10)
  const pageSize = TODO_PAGE_SIZES.includes(requestedPageSize) ? requestedPageSize : 10
  const maxPageNum = Math.max(1, Math.floor(MAX_TODO_PREFIX / pageSize))
  normalized.pageNum = Math.min(positiveInteger(query.pageNum, 1), maxPageNum)
  normalized.pageSize = pageSize
  return normalized
}

export function buildTodoFilterQuery(filters = {}, options = {}) {
  const normalized = normalizeTodoFilterQuery({
    category: filters.category,
    source: filters.source,
    scope: filters.scopeMode,
    keyword: filters.keyword,
    pageNum: filters.pageNum,
    pageSize: filters.pageSize
  }, options)
  const query = {
    category: normalized.category,
    source: normalized.source,
    scope: normalized.scopeMode
  }
  if (normalized.keyword) query.keyword = normalized.keyword
  if (options.includePagination === true) {
    query.pageNum = String(normalized.pageNum)
    query.pageSize = String(normalized.pageSize)
  }
  return query
}

export function todoFilterStateEquals(left, right, options = {}) {
  return JSON.stringify(buildTodoFilterQuery(left, options)) ===
    JSON.stringify(buildTodoFilterQuery(right, options))
}

function stableQueryString(query = {}) {
  const normalized = Object.keys(query).sort().reduce((result, key) => {
    const value = firstQueryValue(query[key])
    if (value !== undefined && value !== null && String(value) !== '') result[key] = String(value)
    return result
  }, {})
  return JSON.stringify(normalized)
}

export function todoRouteQueryMatches(query, filters, options = {}) {
  return stableQueryString(query) === stableQueryString(buildTodoFilterQuery(filters, options))
}

export function hasActiveTodoFilters(filters, options = {}) {
  const current = normalizeTodoFilterQuery({
    category: filters.category,
    source: filters.source,
    scope: filters.scopeMode,
    keyword: filters.keyword
  }, options)
  const defaults = normalizeTodoFilterQuery({}, options)
  return current.category !== defaults.category || current.source !== defaults.source ||
    current.scopeMode !== defaults.scopeMode || current.keyword !== defaults.keyword
}
```

- [ ] **Step 4: Run the utility test**

Run:

```bash
cd erp-ui && node test/unifiedTodoFilterQuery.test.js
```

Expected: `unifiedTodoFilterQuery tests passed`.

- [ ] **Step 5: Commit the pure filter contract**

```bash
git add erp-ui/src/utils/todoFilterQuery.js erp-ui/test/unifiedTodoFilterQuery.test.js
git commit -m "feat: add safe unified todo filter query contract"
```

### Task 5: Implement desktop filter persistence, reset, and semantic rows

**Files:**
- Modify: `erp-ui/test/unifiedTodoDesktop.test.js:31-220`
- Modify: `erp-ui/src/views/workbench/todo/index.vue:1-455`

- [ ] **Step 1: Add failing desktop integration contracts**

Add source assertions:

```js
assert.ok(todoPage.includes("normalizeTodoFilterQuery") && todoPage.includes("buildTodoFilterQuery") &&
  todoPage.includes("todoRouteQueryMatches") && todoPage.includes("$router.replace"),
"desktop filters must use the shared canonical URL contract")
assert.ok(todoPage.includes("'$route.query'") && todoPage.includes("handleRouteQuery"),
  "browser back/forward must reapply desktop filters")
assert.ok(todoPage.includes("hasActiveFilters") && todoPage.includes("clearFilters") && todoPage.includes("清除筛选"),
  "filtered empty states must offer one-step reset")
assert.ok(todoPage.includes('class="todo-row-action"') && todoPage.includes("todo-row-action:focus-visible"),
  "each desktop row must expose one full-row semantic button with visible focus")
assert.ok(!/<article[\s\S]*?@click="openTodo\(row\)"/.test(todoPage),
  "the article container must not be a second nested interactive target")
```

Add this utility loader to `unifiedTodoDesktop.test.js` and spread its result into the existing `loadVueComponent` VM context together with `hasValidInventoryDeptContext`:

```js
function loadTodoFilterQuery() {
  const file = path.resolve(__dirname, "../src/utils/todoFilterQuery.js")
  let source = fs.readFileSync(file, "utf8")
    .replace(/export\s+const\s+/g, "const ")
    .replace(/export\s+function\s+/g, "function ")
  source += `
module.exports = {
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
}`
  const sandbox = { module: { exports: {} }, exports: {}, Object, Array, String, Number, JSON }
  vm.runInNewContext(source, sandbox, { filename: file })
  return sandbox.module.exports
}

const todoFilterQuery = loadTodoFilterQuery()
```

The component VM defaults must include:

```js
hasValidInventoryDeptContext: context => !!(context && context.deptId &&
  ["STORE", "WAREHOUSE"].includes(String(context.deptType || "").toUpperCase())),
...todoFilterQuery
```

Add this block inside `runQualityContracts()`:

```js
const routeReplacements = []
const pageQueries = []
const routeComponent = loadVueComponent(todoPage, {
  getSelectedDeptContext: () => ({}),
  hasValidInventoryDeptContext: () => false,
  ...todoFilterQuery
})
const routeContext = {
  ...routeComponent.data(),
  $route: { path: "/workbench/todo", query: { scope: "current_org" } },
  $router: {
    replace(location) {
      routeReplacements.push(location)
      routeContext.$route.query = location.query
      return Promise.resolve()
    }
  },
  $store: {
    state: { todo: { pageLoading: false } },
    dispatch(action, query) {
      assert.strictEqual(action, "todo/refreshPage")
      pageQueries.push(query)
      return Promise.resolve({
        rows: [], total: 0, estimatedTotal: 0,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  }
}
Object.assign(routeContext, routeComponent.methods)
Object.defineProperties(routeContext, {
  hasCurrentOrgContext: { get: () => false },
  selectedSources: {
    get() { return routeComponent.computed.selectedSources.call(routeContext) }
  }
})
routeContext.applyRouteQuery(routeContext.$route.query)
assert.strictEqual(routeContext.filters.scopeMode, "all_authorized")
await routeContext.syncRouteQuery()
assert.strictEqual(routeReplacements.length, 1)
assert.strictEqual(routeReplacements[0].query.scope, "all_authorized")
await routeContext.loadPage()
const settledRequestCount = pageQueries.length
await routeContext.handleRouteQuery(routeContext.$route.query)
assert.strictEqual(pageQueries.length, settledRequestCount,
  "replaying the canonical query must not request the page twice")
routeContext.filters.category = "risk"
routeContext.filters.keyword = "缺货"
await routeContext.runFilterQuery()
assert.strictEqual(routeReplacements.at(-1).query.category, "risk")
assert.strictEqual(routeReplacements.at(-1).query.keyword, "缺货")
await routeContext.clearFilters()
assert.deepStrictEqual(JSON.parse(JSON.stringify(routeContext.filters)), {
  category: "all", source: "all", scopeMode: "all_authorized", keyword: "", pageNum: 1, pageSize: 10
})
```

- [ ] **Step 2: Run the desktop test and verify failure**

Run:

```bash
cd erp-ui && node test/unifiedTodoDesktop.test.js
```

Expected: FAIL because route synchronization, clear filters, and the semantic row button are absent.

- [ ] **Step 3: Wire canonical route state into the desktop page**

Import the shared helper functions. At the start of desktop `data()`, build and return the context-backed filter object:

```js
const contextSnapshot = getSelectedDeptContext()
const filters = normalizeTodoFilterQuery({}, {
  hasCurrentOrgContext: hasValidInventoryDeptContext(contextSnapshot),
  includePagination: true
})
```

Use `contextSnapshot` and `filters` as the corresponding properties in the existing returned data object, replacing the literal filter object. Add:

```js
watch: {
  '$route.query': {
    deep: true,
    handler(query) {
      return this.handleRouteQuery(query || {})
    }
  }
},
mounted() {
  window.addEventListener('erp:dept-changed', this.handleDeptChanged)
},
beforeDestroy() {
  window.removeEventListener('erp:dept-changed', this.handleDeptChanged)
}
```

Use these methods as the only URL synchronization path:

```js
filterQueryOptions() {
  return { hasCurrentOrgContext: this.hasCurrentOrgContext, includePagination: true }
},
applyRouteQuery(query) {
  const next = normalizeTodoFilterQuery(query, this.filterQueryOptions())
  Object.assign(this.filters, next)
  return next
},
syncRouteQuery() {
  const options = this.filterQueryOptions()
  if (todoRouteQueryMatches(this.$route.query || {}, this.filters, options)) return Promise.resolve()
  const query = buildTodoFilterQuery(this.filters, options)
  return this.$router.replace({ path: this.$route.path, query }).catch(() => {})
},
handleRouteQuery(query) {
  const next = normalizeTodoFilterQuery(query, this.filterQueryOptions())
  if (todoFilterStateEquals(next, this.filters, this.filterQueryOptions())) return Promise.resolve()
  Object.assign(this.filters, next)
  return this.syncRouteQuery().then(() => this.loadPage())
},
runFilterQuery() {
  this.filters.pageNum = 1
  this.filters.scopeMode = normalizeTodoFilterQuery({ scope: this.filters.scopeMode },
    this.filterQueryOptions()).scopeMode
  return this.syncRouteQuery().then(() => this.loadPage())
},
clearFilters() {
  Object.assign(this.filters, normalizeTodoFilterQuery({}, this.filterQueryOptions()))
  return this.syncRouteQuery().then(() => this.loadPage())
},
handleDeptChanged() {
  this.contextSnapshot = getSelectedDeptContext()
  const nextScope = normalizeTodoFilterQuery({ scope: this.filters.scopeMode },
    this.filterQueryOptions()).scopeMode
  this.filters.scopeMode = nextScope
  this.filters.pageNum = 1
  return this.syncRouteQuery().then(() => this.loadPage())
}
```

Use this initialization and route every desktop filter/pagination action through it:

```js
created() {
  this.applyRouteQuery(this.$route.query || {})
  this.syncRouteQuery().then(() => this.loadPage())
},
handleFilterChange() {
  return this.runFilterQuery()
},
handleQuery() {
  return this.runFilterQuery()
},
handlePagination() {
  return this.syncRouteQuery().then(() => this.loadPage())
},
goUnknownPage(offset) {
  if ((offset < 0 && !this.canPreviousUnknownPage) || (offset > 0 && !this.canNextUnknownPage)) return
  this.filters.pageNum = Math.max(1, this.filters.pageNum + offset)
  return this.syncRouteQuery().then(() => this.loadPage())
}
```

Change `<pagination @pagination="loadPage">` to `<pagination @pagination="handlePagination">`.

- [ ] **Step 4: Add filtered empty reset and the single row button**

Add a computed property:

```js
hasActiveFilters() {
  return hasActiveTodoFilters(this.filters, this.filterQueryOptions())
}
```

Inside both desktop empty states add:

```vue
<el-button v-if="hasActiveFilters" type="text" @click="clearFilters">清除筛选</el-button>
```

Replace the clickable article/nested Element button with:

```vue
<article
  v-for="row in rows"
  :key="row.todoKey"
  class="todo-row"
  :class="{ stale: staleSources.includes(row.source) }"
>
  <button
    type="button"
    class="todo-row-action"
    :aria-label="`${row.title || row.businessNo || '待处理事项'}，${categoryLabel(row.category)}，去处理`"
    @click="openTodo(row)"
  >
    <span class="priority-mark" :class="row.priority || 'normal'"></span>
    <span class="todo-main">
      <span class="todo-title-row">
        <strong>{{ row.title || row.businessNo || '待处理事项' }}</strong>
        <el-tag size="mini" :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
        <el-tag v-if="staleSources.includes(row.source)" size="mini" type="warning">缓存数据</el-tag>
      </span>
      <span class="todo-summary">{{ row.summary || row.businessNo || '请进入业务页面查看详情' }}</span>
      <span class="todo-meta">
        <span><i class="el-icon-office-building"></i>{{ row.contextDeptName || row.deptName || '个人事项' }}</span>
        <span><i class="el-icon-time"></i>等待 {{ waitingText(row.waitingSeconds, row.createdTime) }}</span>
        <span>{{ sourceLabel(row.source) }}</span>
        <span>{{ categoryLabel(row.category) }}</span>
      </span>
    </span>
    <span class="todo-process-label">去处理</span>
  </button>
</article>
```

Use these complete structural styles, retaining the existing color-specific priority, title, summary, and metadata rules under the renamed selectors:

```scss
.todo-row {
  border-bottom: 1px solid #edf2f7;
}
.todo-row-action {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 14px;
  padding: 16px 6px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background .15s;
}
.todo-row-action:hover { background: #f8fafc; }
.todo-row-action:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
  border-radius: 8px;
}
.todo-title-row,
.todo-meta { display: flex; align-items: center; gap: 8px; }
.todo-summary { display: block; margin: 6px 0; }
.todo-process-label {
  flex: 0 0 auto;
  padding: 7px 15px;
  border: 1px solid #409eff;
  border-radius: 4px;
  color: #409eff;
}
```

- [ ] **Step 5: Run desktop, query, navigator, and store tests**

Run:

```bash
cd erp-ui
for f in \
  test/unifiedTodoFilterQuery.test.js \
  test/unifiedTodoDesktop.test.js \
  test/unifiedTodoNavigator.test.js \
  test/unifiedTodoRouteResolver.test.js \
  test/unifiedTodoStore.test.js; do
  node "$f" || exit 1
done
```

Expected: PASS; desktop route replay does not duplicate the page request.

- [ ] **Step 6: Commit the desktop usability change**

```bash
git add erp-ui/test/unifiedTodoDesktop.test.js erp-ui/src/views/workbench/todo/index.vue
git commit -m "feat: preserve desktop unified todo filters"
```

### Task 6: Implement mobile filter persistence, reset, semantic cards, and 44px targets

**Files:**
- Modify: `erp-ui/test/unifiedTodoMobile.test.js:110-430`
- Modify: `erp-ui/src/views/mobile/todo/index.vue:1-488`

- [ ] **Step 1: Add failing mobile URL, reset, semantic, and touch contracts**

Add source assertions:

```js
assert.ok(todoSource.includes("normalizeTodoFilterQuery") && todoSource.includes("buildTodoFilterQuery") &&
  todoSource.includes("todoRouteQueryMatches") && todoSource.includes("$router.replace"),
"mobile filters must use the shared canonical URL contract")
assert.ok(todoSource.includes("'$route.query'") && todoSource.includes("handleRouteQuery"),
  "browser back/forward must reapply mobile filters")
assert.ok(todoSource.includes("hasActiveFilters") && todoSource.includes("clearFilters") && todoSource.includes("清除筛选"),
  "mobile filtered empty states must offer one-step reset")
assert.ok(todoSource.includes('class="todo-card-action"') && todoSource.includes("todo-card-action:focus-visible"),
  "each mobile card must expose one semantic full-card button")
assert.ok(!/<article[\s\S]*?@click="openTodo\(row\)"/.test(todoSource),
  "the mobile article must not remain a second interactive target")
for (const contract of [
  /\.back-button, \.refresh-button[\s\S]*?(?:min-)?(?:width|height):\s*44px/,
  /\.category-chips button[\s\S]*?min-height:\s*44px/,
  /\.filter-row select[\s\S]*?height:\s*44px/,
  /\.keyword-row input[\s\S]*?height:\s*44px/,
  /\.keyword-row button[\s\S]*?min-height:\s*44px/,
  /\.todo-process-label[\s\S]*?min-height:\s*44px/
]) assert.ok(contract.test(todoSource), `missing mobile touch contract ${contract}`)
```

Add this independent utility loader to `unifiedTodoMobile.test.js`:

```js
function loadTodoFilterQuery() {
  const file = path.resolve(__dirname, "../src/utils/todoFilterQuery.js")
  let source = fs.readFileSync(file, "utf8")
    .replace(/export\s+const\s+/g, "const ")
    .replace(/export\s+function\s+/g, "function ")
  source += `
module.exports = {
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
}`
  const sandbox = { module: { exports: {} }, exports: {}, Object, Array, String, Number, JSON }
  vm.runInNewContext(source, sandbox, { filename: file })
  return sandbox.module.exports
}

const mobileFilterQuery = loadTodoFilterQuery()
```

Extend the mobile component VM defaults with:

```js
hasValidInventoryDeptContext: context => !!(context && context.deptId &&
  ["STORE", "WAREHOUSE"].includes(String(context.deptType || "").toUpperCase())),
aggregateSummaries: entries => ({
  counts: (entries || []).reduce((counts, entry) => {
    for (const category of ["approval", "execution", "returned", "risk", "personal"]) {
      counts[category] += Number(entry.summary && entry.summary[category]) || 0
    }
    counts.total = counts.approval + counts.execution + counts.returned + counts.risk + counts.personal
    return counts
  }, { total: 0, approval: 0, execution: 0, returned: 0, risk: 0, personal: 0 })
}),
...mobileFilterQuery
```

Add this block inside `runBehaviorContracts()`:

```js
const mobileRouteReplacements = []
const mobilePageQueries = []
const mobileRouteComponent = loadVueComponent(todoSource, {
  getSelectedDeptContext: () => ({}),
  hasValidInventoryDeptContext: () => false,
  ...mobileFilterQuery
})
const mobileRouteContext = {
  ...mobileRouteComponent.data(),
  pageSize: 2,
  $route: { path: "/mobile/todo", query: { scope: "current_org" } },
  $router: {
    replace(location) {
      mobileRouteReplacements.push(location)
      mobileRouteContext.$route.query = location.query
      return Promise.resolve()
    }
  },
  $nextTick: callback => callback(),
  $refs: {},
  $store: {
    state: { todo: { pageLoading: false } },
    dispatch(action, query) {
      assert.strictEqual(action, "todo/refreshPage")
      mobilePageQueries.push(query)
      return Promise.resolve({
        rows: query.pageNum === 1 ? [{ todoKey: "M1" }, { todoKey: "M2" }] : [{ todoKey: "M3" }],
        total: null, estimatedTotal: 3,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  }
}
Object.assign(mobileRouteContext, mobileRouteComponent.methods)
Object.defineProperties(mobileRouteContext, {
  hasCurrentOrgContext: { get: () => false },
  selectedSources: {
    get() { return mobileRouteComponent.computed.selectedSources.call(mobileRouteContext) }
  },
  canLoadMore: {
    get() { return mobileRouteComponent.computed.canLoadMore.call(mobileRouteContext) }
  }
})
mobileRouteContext.applyRouteQuery(mobileRouteContext.$route.query)
assert.strictEqual(mobileRouteContext.filters.scopeMode, "all_authorized")
await mobileRouteContext.syncRouteQuery()
assert.deepStrictEqual(Object.keys(mobileRouteReplacements[0].query).sort(), ["category", "scope", "source"])
await mobileRouteContext.resetAndLoad()
const mobileSettledCount = mobilePageQueries.length
await mobileRouteContext.handleRouteQuery(mobileRouteContext.$route.query)
assert.strictEqual(mobilePageQueries.length, mobileSettledCount,
  "replaying the same mobile route must not reset the list")
mobileRouteContext.filters.category = "risk"
mobileRouteContext.filters.keyword = "缺货"
await mobileRouteContext.handleFilterChange()
assert.strictEqual(mobileRouteReplacements.at(-1).query.keyword, "缺货")
assert.strictEqual("pageNum" in mobileRouteReplacements.at(-1).query, false)
const replacementCountBeforeLoadMore = mobileRouteReplacements.length
mobileRouteContext.total = null
mobileRouteContext.lastPageSize = 2
await mobileRouteContext.loadMore()
assert.strictEqual(mobileRouteReplacements.length, replacementCountBeforeLoadMore,
  "load-more must not rewrite the mobile URL")
await mobileRouteContext.clearFilters()
assert.deepStrictEqual(JSON.parse(JSON.stringify(mobileRouteContext.filters)), {
  category: "all", source: "all", scopeMode: "all_authorized", keyword: ""
})
assert.strictEqual(mobileRouteContext.pageNum, 1)
```

- [ ] **Step 2: Run the mobile test and verify failure**

Run:

```bash
cd erp-ui && node test/unifiedTodoMobile.test.js
```

Expected: FAIL because mobile URL persistence, clear filters, single-button cards, and 44px rules are absent.

- [ ] **Step 3: Wire canonical filter state into mobile reset/load behavior**

Import the query utility. At the start of mobile `data()`, replace the local scope calculation with:

```js
const contextSnapshot = getSelectedDeptContext()
const filters = normalizeTodoFilterQuery({}, {
  hasCurrentOrgContext: hasValidInventoryDeptContext(contextSnapshot),
  includePagination: false
})
```

Use `contextSnapshot` and `filters` in the returned data object. Add this route lifecycle:

```js
watch: {
  '$route.query': {
    deep: true,
    handler(query) {
      return this.handleRouteQuery(query || {})
    }
  }
},
created() {
  this.applyRouteQuery(this.$route.query || {})
  this.syncRouteQuery().then(() => this.resetAndLoad())
}
```

Use `includePagination: false` in the complete filter methods:

```js
filterQueryOptions() {
  return { hasCurrentOrgContext: this.hasCurrentOrgContext, includePagination: false }
},
applyRouteQuery(query) {
  const next = normalizeTodoFilterQuery(query, this.filterQueryOptions())
  Object.assign(this.filters, next)
  return next
},
syncRouteQuery() {
  const options = this.filterQueryOptions()
  if (todoRouteQueryMatches(this.$route.query || {}, this.filters, options)) return Promise.resolve()
  return this.$router.replace({
    path: this.$route.path,
    query: buildTodoFilterQuery(this.filters, options)
  }).catch(() => {})
},
handleRouteQuery(query) {
  const next = normalizeTodoFilterQuery(query, this.filterQueryOptions())
  if (todoFilterStateEquals(next, this.filters, this.filterQueryOptions())) return Promise.resolve()
  Object.assign(this.filters, next)
  this.focusSelectedCategory()
  return this.syncRouteQuery().then(() => this.resetAndLoad())
},
handleFilterChange() {
  this.filters.scopeMode = normalizeTodoFilterQuery({ scope: this.filters.scopeMode },
    this.filterQueryOptions()).scopeMode
  this.focusSelectedCategory()
  return this.syncRouteQuery().then(() => this.resetAndLoad())
},
clearFilters() {
  Object.assign(this.filters, normalizeTodoFilterQuery({}, this.filterQueryOptions()))
  this.focusSelectedCategory()
  return this.syncRouteQuery().then(() => this.resetAndLoad())
},
handleDeptChanged() {
  if (this.isDestroyed) return { discarded: true }
  this.contextSnapshot = getSelectedDeptContext()
  this.filters.scopeMode = normalizeTodoFilterQuery({ scope: this.filters.scopeMode },
    this.filterQueryOptions()).scopeMode
  this.focusSelectedCategory()
  return this.syncRouteQuery().then(() => this.resetAndLoad())
}
```

Do not call `syncRouteQuery` from `loadMore`; pagination remains local and resets to page 1 after navigation or reload.

- [ ] **Step 4: Add reset UI, category visibility, and one semantic card button**

Add:

```js
hasActiveFilters() {
  return hasActiveTodoFilters(this.filters, this.filterQueryOptions())
},
focusSelectedCategory() {
  this.$nextTick(() => {
    const value = this.$refs[`category-${this.filters.category}`]
    const element = Array.isArray(value) ? value[0] : value
    if (element && typeof element.scrollIntoView === 'function') {
      element.scrollIntoView({ block: 'nearest', inline: 'nearest' })
    }
  })
}
```

Change the category button and empty state to:

```vue
<button
  v-for="item in categories"
  :key="item.value"
  :ref="`category-${item.value}`"
  type="button"
  :class="{ active: filters.category === item.value }"
  @click="selectCategory(item.value)"
>{{ item.label }}</button>

<div v-if="!pageLoading && rows.length === 0" class="empty-card">
  <i :class="total === null ? 'el-icon-warning-outline' : 'el-icon-circle-check'"></i>
  <strong>{{ total === null ? '部分数据未知' : '暂无待办' }}</strong>
  <span>{{ total === null ? '当前结果不完整，暂时无法确认是否没有待办。' : '当前筛选条件下没有需要处理的事项。' }}</span>
  <button v-if="hasActiveFilters" class="empty-reset" type="button" @click="clearFilters">清除筛选</button>
</div>
```

Use one full-card button:

```vue
<article
  v-for="row in rows"
  :key="row.todoKey"
  class="todo-card"
  :class="[row.priority || 'normal', { stale: staleSources.includes(row.source) }]"
>
  <button
    type="button"
    class="todo-card-action"
    :aria-label="`${row.title || row.businessNo || '待处理事项'}，${categoryLabel(row.category)}，去处理`"
    @click="openTodo(row)"
  >
    <span class="todo-card-head">
      <span class="category-label">{{ categoryLabel(row.category) }}</span>
      <span v-if="staleSources.includes(row.source)" class="cache-label">缓存</span>
      <small>{{ sourceLabel(row.source) }}</small>
    </span>
    <span class="todo-card-title">{{ row.title || row.businessNo || '待处理事项' }}</span>
    <span class="todo-card-summary">{{ row.summary || row.businessNo || '请进入业务页面查看详情' }}</span>
    <span class="todo-meta">
      <span><i class="el-icon-office-building"></i>{{ row.contextDeptName || row.deptName || '个人事项' }}</span>
      <span><i class="el-icon-time"></i>{{ waitingText(row.waitingSeconds, row.createdTime) }}</span>
    </span>
    <span class="todo-process-label">去处理</span>
  </button>
</article>
```

- [ ] **Step 5: Raise all primary mobile targets to 44px without changing the visual language**

Apply these minimums while keeping the current colors, radii, and layout:

```scss
.todo-header { grid-template-columns: 44px 1fr 44px; }
.back-button, .refresh-button { width: 44px; height: 44px; }
.category-chips button { min-width: 44px; min-height: 44px; }
.filter-row select { height: 44px; }
.keyword-row input { height: 44px; }
.keyword-row button { min-width: 44px; min-height: 44px; }
.empty-reset { min-width: 44px; min-height: 44px; border: 0; background: transparent; color: #0f766e; font-weight: 700; }
.todo-card { padding: 0; }
.todo-card-action {
  position: relative;
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 14px 14px 56px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}
.todo-card-action:focus-visible { outline: 2px solid #0f766e; outline-offset: 2px; border-radius: 16px; }
.todo-card-head { display: flex; align-items: center; gap: 7px; }
.todo-card-title { display: block; margin: 10px 0 5px; font-size: 17px; font-weight: 700; }
.todo-card-summary { display: block; color: #60717e; font-size: 13px; line-height: 1.5; }
.todo-process-label {
  position: absolute;
  right: 14px;
  bottom: 13px;
  display: inline-flex;
  min-width: 44px;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-radius: 10px;
  background: #0f766e;
  color: #fff;
  font-weight: 700;
}
```

Remove the old `.todo-card h3`, `.todo-card p`, and `.todo-card > button` rules after their declarations have been replaced above. The only interactive element is `.todo-card-action`.

- [ ] **Step 6: Run mobile, query, aggregation, and lifecycle tests**

Run:

```bash
cd erp-ui
for f in \
  test/unifiedTodoFilterQuery.test.js \
  test/unifiedTodoAggregation.test.js \
  test/unifiedTodoLifecycle.test.js \
  test/unifiedTodoMobile.test.js; do
  node "$f" || exit 1
done
```

Expected: PASS; load-more stays local while route changes reset to page 1 exactly once.

- [ ] **Step 7: Commit the mobile usability change**

```bash
git add erp-ui/test/unifiedTodoMobile.test.js erp-ui/src/views/mobile/todo/index.vue
git commit -m "feat: improve mobile unified todo usability"
```

### Task 7: Verify the complete change with local processes and real UI evidence

**Files:**
- Verify only; do not modify unrelated baseline files.
- Save new evidence under: `docs/audit-screenshots/unified-todo-20260713/`

- [ ] **Step 1: Prove no removed action or duplicate todo ownership remains**

Run:

```bash
rg -n "todo/refresh['\"]|业务待办|todoTotal|checkPermi" \
  erp-ui/src/layout/components/HeaderNotice/index.vue \
  erp-ui/src/views/oa/signTask/index.vue \
  erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue
```

Expected: no matches for `todo/refresh`, `业务待办`, `todoTotal`, or `checkPermi`; matches for `refreshSummaries`/`invalidateAfterMutation` are allowed because the regex requires a closing quote immediately after `refresh`.

- [ ] **Step 2: Run all focused frontend contracts**

Run:

```bash
cd erp-ui
for f in \
  test/headerNoticeReadAll.test.js \
  test/targetedUserNotification.test.js \
  test/signTaskCenter.test.js \
  test/shopContextUx.test.js \
  test/unifiedTodoFilterQuery.test.js \
  test/unifiedTodo*.test.js; do
  node "$f" || exit 1
done
```

Expected: every listed test exits 0.

- [ ] **Step 3: Run the complete frontend suite and classify only pre-existing unrelated failures**

Run:

```bash
cd erp-ui && npm test
```

Expected: `headerNoticeReadAll` and all unified-todo tests pass. If the seven previously observed unrelated baseline contracts still fail, record their names without modifying them: contract automation numbered SQL, labor touch target, mobile auth preview data, mobile inventory scanner promise, production data isolation, mobile profile 44px, and mobile progressive scroller. Any new or todo-related failure blocks completion.

- [ ] **Step 4: Build the production frontend**

Run:

```bash
cd erp-ui && npm run build:prod
```

Expected: exit 0; secret scan passes and Vue production assets are generated.

- [ ] **Step 5: Run system push and unified todo backend tests**

Run:

```bash
mvn -pl erp-common/erp-common-core,erp-modules/erp-inventory,erp-modules/erp-oa,erp-modules/erp-system -am \
  -Dtest=PushDeliveryClientSpringWiringTest,ApnsPushDeliveryClientTest,FirebasePushDeliveryClientTest,TodoContractTest,InvTodoControllerTest,InvTodoMapperBindingTest,InvTodoServiceImplTest,OaTodoControllerTest,OaTodoMapperBindingTest,OaTodoServiceImplTest,OaSignTodoProviderTest,SysTodoControllerTest,SysTodoMapperBindingTest,SysTodoServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass; no Testcontainers profile or virtual machine is used.

- [ ] **Step 6: Build and start the latest system service locally**

Run:

```bash
mvn -pl erp-modules/erp-system -am clean package -DskipTests
java -jar erp-modules/erp-system/target/erp-modules-system.jar --spring.profiles.active=local
```

Expected: the service reaches its normal started state and listens on port `9201`; logs contain neither `No default constructor found` nor push credential/file access during startup. Stop it with Ctrl-C after verification.

- [ ] **Step 7: Run the local ERP stack and capture after-state screenshots**

Start auth, gateway, system, OA, inventory, and `erp-ui` from current source as local host processes. Use the in-app browser and the existing local audit account. Do not execute any approval, signing, notification retry, or other business mutation.

Capture and visually inspect at the same states/viewports as the before images:

- `08-home-single-todo-entry.png`: desktop header contains one business-todo entry.
- `09-desktop-filter-url-restored.png`: filtered desktop todo URL contains canonical query; navigating away and back restores filters.
- `10-desktop-empty-clear-filter.png`: empty filtered state shows and successfully uses “清除筛选”.
- `11-mobile-todo-unknown-counts.png`: at 390×844 with one Provider unavailable, selected-source totals use `N+`, `?`, or `…` rather than false zeros.
- `12-mobile-todo-touch-keyboard.png`: selected chip is visible, controls measure at least 44px, and Tab/Enter opens the same safe todo navigation path once.

Compare these against `03-home-duplicate-todo.png`, `05-desktop-empty-state-no-reset.png`, and `07-mobile-todo-unknown-counts.png`. Reset the browser viewport and stop all local service processes when finished.

- [ ] **Step 8: Review the final diff and commit any verification-only test adjustment**

Run:

```bash
git diff --check
git status --short
git log --oneline -8
```

Expected: no whitespace errors; only planned source/test changes plus pre-existing unrelated dirty files and audit screenshots appear. If a verification contract required a legitimate planned adjustment, stage only that exact file and commit it as:

```bash
git commit -m "test: complete unified todo remediation coverage"
```

Do not stage tracked Docker JARs, Python bytecode, unrelated audit folders, HR documents, Android wrapper files, or any pre-existing user changes.
