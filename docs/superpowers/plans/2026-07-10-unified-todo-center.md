# Unified Todo Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one accurate, permission-aware business todo center whose totals, lists, and processing links stay consistent across the desktop header, desktop home page, desktop todo page, mobile workbench, and mobile todo page.

**Architecture:** Each business service remains the owner of its live actionable state and exposes the same Java todo contract through `/inventory/todo`, `/oa/todo`, and `/system/todo`. A Vuex Todo Store loads providers independently, retains the last successful value when one provider fails, and refreshes on lifecycle events plus a 60-second visible-page poll. The unified pages only discover and route work; all writes continue through the existing business screens and permissions.

**Tech Stack:** Java 17, Spring Boot 4.0.3, MyBatis Spring Boot 4.0.1/XML, Flowable, Sa-Token permission helpers, JUnit 5/AssertJ/Mockito, Vue 2, Vuex 3, Element UI, Axios, Node source-contract tests, MySQL 5.7-compatible SQL.

---

## Delivery boundaries and hard prerequisites

- Work only in `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/unified-todo-center` on `codex/unified-todo-center` until all verification is green.
- The primary workspace `/Users/liuxingyu/Desktop/备份/ERP-NEW` contains user-owned dirty changes. Do not stage, stash, rewrite, or delete them.
- `codex/hr-personnel-management` is not integrated into `6月13号`. Its eight commits `cc1eeaa3^..e42315c8` provide the HR profile service and onboarding lifecycle required by Task 7.
- Before Task 7, compare the primary workspace's uncommitted HR files with `codex/hr-personnel-management`. If the primary HR files are still uncommitted, pause only Task 7 and ask the owner to commit them or explicitly choose which version wins. Inventory, OA, notice, and frontend shell tasks may continue.
- Preserve the current branch's user-profile auto-derivation behavior when replaying HR work. In particular, do not restore writes to derived organization/tenure fields that `2026-07-10-user-profile-auto-derived-fields.md` made read-only.
- Do not merge to `6月13号` until Task 14 passes both desktop and mobile browser verification.

## Shared response contract

Every provider returns the same `TodoItem` and `TodoSummary` shapes. `routeParams` is filled in the provider service, not mapped from arbitrary JSON in SQL.

```java
public class TodoItem
{
    private String todoKey;
    private String source;
    private String type;
    private String category;
    private Long businessId;
    private String businessNo;
    private String title;
    private String summary;
    private String status;
    private String priority;
    private Date createdTime;
    private Long waitingSeconds;
    private Long deptId;
    private String deptName;
    private String deptType;
    private String scopeMode;
    private String routeType;
    private Map<String, String> routeParams = new LinkedHashMap<>();
    private String requiredPermission;
}
```

```java
public class TodoSummary
{
    private String source;
    private long total;
    private long approval;
    private long execution;
    private long returned;
    private long risk;
    private long personal;
    private long urgent;
    private long important;
    private long normal;
    private Map<String, Long> typeCounts = new LinkedHashMap<>();
    private List<TodoItem> recent = new ArrayList<>();
}
```

Provider endpoints:

```text
GET /inventory/todo/summary   GET /inventory/todo/list
GET /oa/todo/summary          GET /oa/todo/list
GET /system/todo/summary      GET /system/todo/list
```

All six endpoints require login only at controller level. Each service builds an `enabledTypes` set from exact operation permissions; a broad list permission must never unlock unrelated todo types.

Every organization-bound row must also carry `deptId`, `deptName`, and `deptType`, plus identical `routeParams.contextDeptId/contextDeptName/contextDeptType` values. They identify the organization context required by the existing write endpoint. Personal signing rows are the only first-release rows allowed to omit an organization context.

Use these exact category/action assignments for category counts and `TodoKeys`:

```text
approval:  INV_TRANSFER_APPROVAL=approve, INV_STOCK_CHECK_APPROVAL=approve,
           OA_PURCHASE_APPROVAL=approve
execution: INV_PURCHASE_QC=quality_check, INV_PURCHASE_RECEIVE=receive,
           INV_SALES_NOTICE_CREATE=create_notice, INV_DELIVERY_EXECUTE=deliver,
           INV_TRANSFER_DELIVER=deliver, INV_TRANSFER_RECEIVE=receive_shipment,
           INV_PURCHASE_RETURN_CONFIRM=confirm_return, INV_SALES_RETURN_CONFIRM=confirm_return,
           OA_FIXED_ASSET_REPAIR_CONFIRM=confirm_repair,
           HR_PROFILE_INCOMPLETE=complete_profile, HR_ONBOARDING_CONFIRM=confirm_onboarding
returned:  INV_TRANSFER_RETURNED=revise, INV_STOCK_CHECK_RETURNED=revise,
           INV_STOCK_CHECK_RESTART=restart, OA_PURCHASE_RETURNED=revise
risk:      INV_OUT_OF_STOCK=view_risk, INV_LOW_STOCK=view_risk,
           HR_CONTRACT_DUE=update_contract, HR_OFFBOARD_ACCOUNT=disable_account
personal:  OA_LABOR_CONTRACT_SIGN=sign, OA_SIGN_PACKAGE_SIGN=sign
```

### Priority and waiting-time matrix

Every union branch must set `createdTime`; the outer projection sets:

```sql
greatest(timestampdiff(second, created_time, current_timestamp), 0) as waiting_seconds
```

Use this complete matrix and add boundary assertions in each provider mapper/service test:

| Type | `createdTime` source | Priority |
|---|---|---|
| `INV_TRANSFER_APPROVAL` | current approval task `create_time` | important; urgent when waiting hours `>= todo.approval.urgent.hours` |
| `INV_STOCK_CHECK_APPROVAL` | current approval instance `submitted_time` | important; same urgent threshold |
| `INV_PURCHASE_QC` | oldest pending inbound record `create_time` | normal |
| `INV_PURCHASE_RECEIVE` | `coalesce(order.update_time, order.create_time)` | normal |
| `INV_SALES_NOTICE_CREATE` | `coalesce(order.update_time, order.create_time)` | normal |
| `INV_DELIVERY_EXECUTE` | `coalesce(notice.update_time, notice.create_time)` | normal |
| `INV_TRANSFER_DELIVER` | `coalesce(order.update_time, order.create_time)` | normal |
| `INV_TRANSFER_RECEIVE` | shipment `create_time` | normal |
| `INV_PURCHASE_RETURN_CONFIRM` | `coalesce(return.update_time, return.create_time)` | important |
| `INV_SALES_RETURN_CONFIRM` | `coalesce(return.update_time, return.create_time)` | important |
| `INV_TRANSFER_RETURNED` | latest rejected approval instance finish/update time | important |
| `INV_STOCK_CHECK_RETURNED` | rejected approval instance `finished_time` | important |
| `INV_STOCK_CHECK_RESTART` | stock check `last_invalidated_time` | important |
| `INV_OUT_OF_STOCK` | oldest `coalesce(stock.update_time,last_out_time,last_in_time,create_time)` in the organization group | urgent |
| `INV_LOW_STOCK` | same stock timestamp expression | normal |
| `OA_PURCHASE_APPROVAL` | Flowable `ACT_RU_TASK.CREATE_TIME_` for `current_task_id` | important; urgent at the same configured approval threshold |
| `OA_PURCHASE_RETURNED` | `coalesce(purchase.update_time,purchase.create_time)` | important |
| `OA_FIXED_ASSET_REPAIR_CONFIRM` | `coalesce(submitted_time,create_time)` | important |
| `OA_LABOR_CONTRACT_SIGN` | `coalesce(sent_time,update_time,create_time)` | important |
| `OA_SIGN_PACKAGE_SIGN` | `coalesce(sent_time,update_time,create_time)` | important |
| `HR_PROFILE_INCOMPLETE` | oldest `coalesce(last_profile_update_time,update_time,create_time)` in group | normal |
| `HR_ONBOARDING_CONFIRM` | oldest `coalesce(last_profile_update_time,update_time,create_time)` in group | normal |
| `HR_CONTRACT_DUE` | `contract_end_date - todo.contract.warning.days` | urgent at 0–7 days; important at 8–30 days |
| `HR_OFFBOARD_ACCOUNT` | oldest `coalesce(leave_date,last_profile_update_time,update_time,create_time)` in group | urgent |

Null timestamp fallbacks must end at `current_timestamp`, yielding `waitingSeconds=0`, never a negative or null value. Tests must cover 23:59:59 versus 24:00:00 approval age and contract due at 7/8/30/31 days.

## File map

Shared backend contract and configuration:

- Create `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoConstants.java`.
- Create `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoItem.java`.
- Create `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoSummary.java`.
- Create `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`.
- Create `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoKeys.java`.
- Create `erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java`.
- Create mirrored migrations `sql/erp_unified_todo_center_20260710.sql` and `docker/mysql/db/erp_unified_todo_center_20260710.sql`.

Inventory provider:

- Create `InvTodoTypes`, `InvTodoCountRow`, `InvTodoMapper.java/xml`, `IInvTodoService`, `InvTodoServiceImpl`, and `InvTodoController` under the existing inventory packages.
- Create `InvTodoMapperBindingTest`, `InvTodoServiceImplTest`, and `InvTodoControllerTest`.
- Modify `InvDeptScopeMapper.java/xml`, `InvMobileServiceImpl`, `MobileWorkbenchSummary`, `InvMobileMapper.java/xml`, and their existing tests.
- Modify `erp-modules/erp-inventory/pom.xml` to use `erp-api-system` for threshold configuration.

OA provider and rejected-request closure:

- Create `OaTodoTypes`, `OaTodoCountRow`, `OaTodoMapper.java/xml`, `IOaTodoService`, `OaTodoServiceImpl`, and `OaTodoController`.
- Create `OaTodoMapperBindingTest`, `OaTodoServiceImplTest`, and `OaTodoControllerTest`.
- Modify the exact OA purchase closure files and tests listed in Task 5.

System/HR provider and notices:

- After the HR prerequisite, create `SysTodoTypes`, `SysTodoCandidateRow`, `SysTodoMapper.java/xml`, `ISysTodoService`, `SysTodoServiceImpl`, and `SysTodoController`.
- Create `SysTodoMapperBindingTest`, `SysTodoServiceImplTest`, and `SysTodoControllerTest`.
- Add stable `deptId` ownership to `SysUserProfile`, HR query/save/import paths, mapper XML, migration, forms, and tests.
- Modify `SysNoticeController`, `ISysNoticeReadService`, `SysNoticeReadServiceImpl`, `SysNoticeReadMapper.java/xml`, and notice tests.

Frontend aggregation and surfaces:

- Create `erp-ui/src/api/workbench/todo.js`.
- Create `erp-ui/src/utils/todoAggregator.js`, `todoRouteResolver.js`, `todoNavigator.js`, `todoMutationMatcher.js`, and `todoRefreshEvents.js`.
- Create `erp-ui/src/store/modules/todo.js`; modify `store/index.js`, `store/getters.js`, `store/modules/user.js`, `utils/request.js`, and `utils/shopContext.js`.
- Create `erp-ui/src/layout/components/HeaderTodo/index.vue` and `erp-ui/src/views/workbench/todo/index.vue`.
- Create `erp-ui/src/views/mobile/todo/index.vue`.
- Modify the exact desktop, mobile, router, and focused business-page paths listed in Tasks 11–13.
- Add Node tests named in Tasks 9–13.

## Task 1: Establish the shared todo contract and threshold configuration

**Files:**

- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoConstants.java`
- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoItem.java`
- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoSummary.java`
- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`
- Create: `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoKeys.java`
- Create: `erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java`
- Create: `sql/erp_unified_todo_center_20260710.sql`
- Create: `docker/mysql/db/erp_unified_todo_center_20260710.sql`
- Modify: `erp-ui/test/dockerScripts.test.js`

- [ ] **Step 1: Write the failing Java contract test**

Assert defaults and accepted constants:

```java
TodoQuery query = new TodoQuery();
assertThat(query.getScopeMode()).isNull();
assertThat(query.getPageNum()).isEqualTo(1);
assertThat(query.getPageSize()).isEqualTo(10);

TodoSummary summary = new TodoSummary();
summary.setApproval(2L);
summary.setExecution(3L);
summary.recalculateTotal();
assertThat(summary.getTotal()).isEqualTo(5L);
summary.getTypeCounts().put("INV_PURCHASE_QC", 2L);
assertThat(summary.getTypeCounts()).containsEntry("INV_PURCHASE_QC", 2L);
assertThat(TodoConstants.CATEGORIES).containsExactly(
        "approval", "execution", "returned", "risk", "personal");
assertThat(TodoKeys.build("inventory", "INV_PURCHASE_QC", 17L, "quality_check"))
        .isEqualTo("inventory:INV_PURCHASE_QC:17:quality_check");
assertThat(TodoKeys.build("inventory", "INV_TRANSFER_RECEIVE", 17L, "receive_shipment"))
        .isNotEqualTo(TodoKeys.build("inventory", "INV_TRANSFER_DELIVER", 17L, "deliver"));
assertThat(TodoKeys.build("system", "HR_PROFILE_INCOMPLETE", 3101L, "complete_profile"))
        .isEqualTo("system:HR_PROFILE_INCOMPLETE:3101:complete_profile");
```

- [ ] **Step 2: Run the test and verify RED**

```bash
mvn -pl erp-common/erp-common-core -am -Dtest=TodoContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the todo contract does not exist.

- [ ] **Step 3: Implement the contract**

`TodoConstants` must define sources (`inventory`, `oa`, `system`), five categories, three priorities, and two scope modes (`current_org`, `all_authorized`). `TodoQuery` contains `category`, `source`, `scopeMode`, `keyword`, `pageNum`, and `pageSize`; normalize page size to `1..100` and reject unknown category/scope strings with `ServiceException` in provider services.

Leave `scopeMode` null when the caller does not specify it. Providers then apply defaults per union branch: approvals use `all_authorized`, execution/returned/risk use `current_org`, and personal rows are owner-scoped without organization filtering. An explicit page toggle overrides those defaults for organization-bound rows. Add a mixed “all categories, no scopeMode” test so cross-organization approvals are not accidentally truncated to the current shop.

When no valid organization header is selected, skip only branches whose effective scope is `current_org`; still return actual all-authorized approvals and owner-scoped personal items. Do not let a missing header make the whole provider fail. An explicit `current_org` list without context returns an empty page with total zero.

`TodoKeys.build(source,type,businessId,action)` validates every segment and returns `source:type:businessId:action`. Every provider must call it after mapping rather than inventing SQL-specific keys. Shipment rows use `shipmentId` as `businessId`; grouped risk/HR rows use `deptId`; the distinct type/action segments prevent collisions. Add provider assertions that every returned key is nonblank and unique.

- [ ] **Step 4: Add threshold configuration and mirrored-SQL assertions**

Insert idempotent `sys_config` rows:

```text
todo.approval.urgent.hours = 24
todo.contract.warning.days = 30
todo.contract.urgent.days = 7
todo.summary.recent.limit = 5
```

Extend `dockerScripts.test.js` to assert both migration files are byte-identical and contain all four keys. Keep SQL MySQL 5.7-compatible.

- [ ] **Step 5: Verify GREEN**

```bash
mvn -pl erp-common/erp-common-core -am -Dtest=TodoContractTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs dockerScripts.test.js
```

Expected: both commands pass.

- [ ] **Step 6: Commit Task 1**

```bash
git add erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo \
  erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo \
  sql/erp_unified_todo_center_20260710.sql \
  docker/mysql/db/erp_unified_todo_center_20260710.sql \
  erp-ui/test/dockerScripts.test.js
git commit -m "feat: define unified todo contract"
```

## Task 2: Query every actionable inventory todo from one fact union

**Files:**

- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTodoCountRow.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTodoMapper.java`
- Create: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java`

- [ ] **Step 1: Write the failing mapper contract test**

Read `InvTodoMapper.xml` and assert it defines `InventoryTodoUnion`, `selectInventoryTodoList`, `selectRecentInventoryTodos`, and `selectInventoryTodoCounts`. Assert the source contains all 15 inventory type constants (2 approval + 8 execution + 3 returned + 2 risk) and these non-negotiable clauses:

```text
t.status = 'pending'
i.status = 'running'
find_in_set(#{userId}, t.candidate_user_ids)
coalesce(o.qc_status, '') <> 'pending'
qc_result = 'pending'
sh.status = 'pending_receive'
o.status in ('approved', 'reserved', 'partial_delivered')
available_quantity <= 0
available_quantity > 0
```

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=InvTodoMapperBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertion failure because the mapper does not exist.

- [ ] **Step 3: Define exact mapper methods**

```java
List<TodoItem> selectInventoryTodoList(
        @Param("query") TodoQuery query,
        @Param("enabledTypes") Set<String> enabledTypes,
        @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
        @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
        @Param("userId") Long userId,
        @Param("username") String username,
        @Param("approvalUrgentHours") int approvalUrgentHours);

List<TodoItem> selectRecentInventoryTodos(
        @Param("query") TodoQuery query,
        @Param("enabledTypes") Set<String> enabledTypes,
        @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
        @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
        @Param("userId") Long userId,
        @Param("username") String username,
        @Param("approvalUrgentHours") int approvalUrgentHours,
        @Param("limit") int limit);

List<InvTodoCountRow> selectInventoryTodoCounts(
        @Param("query") TodoQuery query,
        @Param("enabledTypes") Set<String> enabledTypes,
        @Param("currentScopeDeptIds") List<Long> currentScopeDeptIds,
        @Param("authorizedScopeDeptIds") List<Long> authorizedScopeDeptIds,
        @Param("userId") Long userId,
        @Param("username") String username,
        @Param("approvalUrgentHours") int approvalUrgentHours);
```

- [ ] **Step 4: Implement `InventoryTodoUnion` with exact lifecycle rules**

The union must produce mutually compatible columns for:

- `INV_TRANSFER_APPROVAL`: submitted transfer, running instance, current pending node task, current user in `candidate_user_ids`; never omit the candidate check for administrators.
- `INV_STOCK_CHECK_APPROVAL`: `pending_approval`, current running approval instance/current pending task, current user candidate, and the existing `yyzj` candidate constraint.
- `INV_PURCHASE_QC`: submitted order, `qc_status='pending'`, and at least one pending inbound record.
- `INV_PURCHASE_RECEIVE`: submitted order with positive unreceived detail quantity and `qc_status` not pending. This is intentionally mutually exclusive with QC.
- `INV_SALES_NOTICE_CREATE`: submitted sales with remaining quantity and no active `pending/delivering` notice.
- `INV_DELIVERY_EXECUTE`: pending/delivering notice with remaining notice quantity.
- `INV_TRANSFER_DELIVER`: approved/reserved/partial-delivered transfer scoped only by the source organization.
- `INV_TRANSFER_RECEIVE`: pending-receive shipment scoped only by destination; use `shipment_id` as `businessId` and keep `transferId` for route decoration.
- Purchase-return and sales-return confirmation: submitted return with remaining quantity.
- `INV_TRANSFER_RETURNED`: draft owned by `create_by=#{username}` with a latest rejected approval instance; ordinary drafts must not match.
- `INV_STOCK_CHECK_RETURNED`: rejected and owned by `submitted_user_id=#{userId}`.
- `INV_STOCK_CHECK_RESTART`: invalidated and owned by submitter; use `last_invalidated_time` as created time.
- `INV_OUT_OF_STOCK`: grouped by organization where available quantity is `<=0`.
- `INV_LOW_STOCK`: grouped by organization where available quantity is `>0` and `<=safety_stock_min`; this excludes every out-of-stock row.

Use one outer stable sort:

```sql
order by field(priority, 'urgent', 'important', 'normal'), created_time asc, business_id asc
```

- [ ] **Step 5: Verify mapper GREEN**

Run the Task 2 Maven command. Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java \
  erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTodoCountRow.java \
  erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTodoMapper.java \
  erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml \
  erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java
git commit -m "feat(inventory): query actionable inventory todos"
```

## Task 3: Secure, summarize, and expose the inventory provider

**Files:**

- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTodoService.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTodoController.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvDeptScopeMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeptScopeMapper.xml`
- Modify: `erp-modules/erp-inventory/pom.xml`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTodoServiceImplTest.java`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvTodoControllerTest.java`

- [ ] **Step 1: Write failing permission and scope tests**

Cover all of the following:

```java
assertThat(enabledTypesForQcWithoutList).doesNotContain("INV_PURCHASE_QC");
assertThat(enabledTypesForQcAndList).containsExactly("INV_PURCHASE_QC");
assertThat(adminWithoutCandidateSummary.getApproval()).isZero();
assertThat(fakeMapper.lastCurrentScopeDeptIds).containsExactly(selectedDeptId);
assertThat(fakeMapper.lastAuthorizedScopeDeptIds)
        .containsExactlyInAnyOrder(authorizedStoreId, authorizedWarehouseId);
assertThat(defaultMixedSummary.getRecent()).extracting(TodoItem::getBusinessId)
        .contains(crossOrgApprovalId)
        .doesNotContain(crossOrgPurchaseReceiveId);
assertThat(lowStockOnlyUserTypes).doesNotContain("INV_LOW_STOCK", "INV_OUT_OF_STOCK");
```

Low/out-of-stock requires `inv:stock:list` plus at least one of `inv:purchase:add`, `inv:transfer:add`, or `inv:stock:adjust`.

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTodoServiceImplTest,InvTodoControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the provider service and controller do not exist.

- [ ] **Step 3: Implement provider permissions and scopes**

Add `selectUserAuthorizedInventoryDeptIds(userId)` and `selectAllActiveInventoryDeptIds()` to `InvDeptScopeMapper`. Expand authorized GROUP/COMPANY/STORE/WAREHOUSE roots to active STORE and WAREHOUSE descendants.

Always pass both scope lists to the mapper. Each union branch selects current versus authorized according to explicit `query.scopeMode`, or uses the category default when it is null. Never collapse them into one list before a mixed-category summary query.

Build `enabledTypes` from action **and target-page read** permissions. Both sides of each row are required so the provider cannot create a badge that the user cannot open:

```text
INV_TRANSFER_APPROVAL        inv:transfer:approve        + inv:transfer:list
INV_STOCK_CHECK_APPROVAL     inv:stockCheck:approve      + inv:stockCheck:list
INV_PURCHASE_QC              inv:purchase:qc             + inv:purchase:list
INV_PURCHASE_RECEIVE         inv:purchase:receive        + inv:purchase:list
INV_SALES_NOTICE_CREATE      inv:deliveryNotice:add      + inv:sales:list
INV_DELIVERY_EXECUTE         inv:deliveryNotice:deliver  + inv:deliveryNotice:list
INV_TRANSFER_DELIVER         inv:transfer:deliver        + inv:transfer:list
INV_TRANSFER_RECEIVE         inv:transfer:receive        + inv:transfer:list
INV_PURCHASE_RETURN_CONFIRM  inv:purchaseReturn:confirm  + inv:purchaseReturn:list
INV_SALES_RETURN_CONFIRM     inv:salesReturn:confirm     + inv:salesReturn:list
INV_STOCK_CHECK_RETURNED     inv:stockCheck:submit       + inv:stockCheck:list
INV_STOCK_CHECK_RESTART      inv:stockCheck:submit       + inv:stockCheck:list
```

Returned transfers require `inv:transfer:list` plus `inv:transfer:add` or `inv:transfer:edit`. Inventory risk requires `inv:stock:list` plus a corrective permission as specified above. Set `requiredPermission` to the action permission and retain the read-permission result in service tests.

Decorate `routeParams` with `businessId`, and with `transferId/shipmentId` for transfer receipts. Also set the actionable context:

- purchase/QC/delivery/returns/risk: the warehouse or store used by the write endpoint;
- transfer delivery: source warehouse/store;
- transfer receipt: destination warehouse/store;
- stock check: `check.shop_dept_id`;
- transfer approval: the first active authorized context among destination, source, destination warehouse, and source warehouse for which `validateApprovalShopScope` succeeds.

Join `sys_dept` to populate the matching context name/type. Add tests proving a cross-organization approval receives a context that its current approval endpoint accepts.

- [ ] **Step 4: Load configurable urgency safely**

Add `erp-api-system` to the inventory POM and inject optional `RemoteConfigService`. Read `todo.approval.urgent.hours`; on remote failure, malformed values, or values outside `1..168`, use `24`. Do not fail the provider because configuration is unavailable.

- [ ] **Step 5: Expose login-only endpoints**

```java
@RequiresLogin
@GetMapping("/summary")
public AjaxResult summary(TodoQuery query, HttpServletRequest request)

@RequiresLogin
@GetMapping("/list")
public TableDataInfo list(TodoQuery query, HttpServletRequest request)
```

Use `startPage()` only around the single mapper list query. Validate category and scope before calling PageHelper.

- [ ] **Step 6: Verify GREEN and commit**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTodoMapperBindingTest,InvTodoServiceImplTest,InvTodoControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add erp-modules/erp-inventory
git commit -m "feat(inventory): expose secure todo provider"
```

## Task 4: Replace the legacy mobile inventory totals without breaking its API

**Files:**

- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvMobileServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/MobileWorkbenchSummary.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvMobileMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvMobileMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvMobileController.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvMobileServiceImplPermissionTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvMobileMapperBindingTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvMobileControllerTest.java`

- [ ] **Step 1: Write failing compatibility tests**

Assert that workbench totals are mapped from `IInvTodoService.selectSummary(current_org)` and its `typeCounts`, and that:

```java
assertThat(summary.getTodoCount()).isEqualTo(providerSummary.getTotal());
assertThat(summary.getPendingApprovalCount()).isEqualTo(providerSummary.getApproval());
assertThat(summary.getPurchaseReceiveCount())
        .isEqualTo(providerSummary.getTypeCounts().get("INV_PURCHASE_RECEIVE"));
```

Assert the old transfer todo still passes the actual current user ID even for administrators. Assert any retained `countPendingPurchaseReceive` SQL excludes `qc_status='pending'`.

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvMobileServiceImplPermissionTest,InvMobileMapperBindingTest,InvMobileControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures show the workbench still assembles partial counts and admin bypasses candidate filtering.

- [ ] **Step 3: Delegate totals to the provider**

Keep old response fields for deployed clients, but derive them from `TodoSummary.typeCounts`: purchase receive, transfer receive, delivery notice execute, transfer deliver, and mutually exclusive low/out-of-stock. Derive `todoCount` and `pendingApprovalCount` from category totals. Remove unused mapper count methods only after their callers are gone. Add a test proving every legacy field has an explicit type mapping; do not approximate a field from the broader execution total.

Change `/mobile/workbench/summary` from the broad `inv:stock:list` controller requirement to `@RequiresLogin`; the provider service will only return categories enabled for the current user.

- [ ] **Step 4: Verify and commit**

Run the Task 4 command, then:

```bash
git add erp-modules/erp-inventory
git commit -m "refactor(inventory): unify mobile workbench totals"
```

## Task 5: Give rejected OA purchases a terminal close path

**Files:**

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaPurchaseService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPurchaseServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaPurchaseMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaPurchaseMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaPurchase.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaPurchaseServiceImplTest.java`
- Modify: `erp-ui/src/api/oa/purchase.js`
- Modify: `erp-ui/src/views/oa/purchase/index.vue`
- Modify: `erp-ui/test/oaPurchaseInteractionConsistency.test.js`

- [ ] **Step 1: Write failing closure tests**

Add service tests for rejected owner success, non-owner rejection, non-rejected rejection, and an already-cancelled idempotency error. Also add the rejected-edit lifecycle regression:

```java
OaPurchase edited = service.saveDraft(rejectedEdit, selectedDeptId);
assertThat(edited.getStatus()).isEqualTo("rejected");
assertThat(edited.getProcessInstanceId()).isEqualTo(rejectedProcessId);
assertThat(edited.getCurrentTaskId()).isNull();

OaPurchase resubmitted = service.submitPurchase(rejectedEdit, selectedDeptId);
assertThat(resubmitted.getStatus()).isEqualTo("submitted");
assertThat(resubmitted.getProcessInstanceId()).isNotEqualTo(rejectedProcessId);
```

The close assertion is:

```java
assertThat(closed.getStatus()).isEqualTo("cancelled");
assertThat(closed.getCurrentTaskId()).isNull();
assertThat(closed.getProcessInstanceId()).isEqualTo(originalProcessId);
```

- [ ] **Step 2: Verify RED**

```bash
mvn -pl erp-modules/erp-oa -am -Dtest=OaPurchaseServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs oaPurchaseInteractionConsistency.test.js
```

Expected: backend compile/test failure for close plus a rejected-edit lifecycle failure because `saveDraft` currently forces edited rejected requests to `draft`; frontend assertion fails because close is absent.

- [ ] **Step 3: Implement close under lock**

Reuse the existing `selectOaPurchaseByIdForUpdate` lock query, add `closeRejectedPurchase(purchaseId, selectedShopDeptId)`, and:

```java
@RequiresPermissions("oa:purchase:add")
@IdempotentSubmit(timeout = 30)
@PostMapping("/{purchaseId}/close")
```

The service must enforce `status='rejected'` and `applicantId==SecurityUtils.getUserId()`, preserve process history, set `status='cancelled'`, clear `current_task_id`, and invalidate the existing purchase cache.

Fix `saveDraft` at the same time: a new request and an existing draft save as `draft`; an existing rejected request remains `rejected` while fields are edited and retains its rejected `processInstanceId`/history. Only `submitPurchase` starts a new process and transitions it to `submitted`; only close transitions it to `cancelled`. This keeps `OA_PURCHASE_RETURNED` present throughout editing.

- [ ] **Step 4: Add the user exit action**

Expose `closePurchase(purchaseId)`. Show “关闭申请” only on the current user's rejected rows, confirm before calling, label `cancelled` as “已关闭”, and keep rejected rows editable/resubmittable.

- [ ] **Step 5: Verify GREEN and commit**

Run the Task 5 commands, then:

```bash
git add erp-modules/erp-oa erp-ui/src/api/oa/purchase.js \
  erp-ui/src/views/oa/purchase/index.vue erp-ui/test/oaPurchaseInteractionConsistency.test.js
git commit -m "feat(oa): close rejected purchase requests"
```

## Task 6: Expose OA approval, returned, confirmation, and personal todos

**Files:**

- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoCountRow.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaTodoService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaTodoController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaDeptScopeMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaDeptScopeMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaTodoControllerTest.java`

- [ ] **Step 1: Write failing service and mapper tests**

Cover assignee/candidate-user/candidate-group task-ID deduplication, administrator-without-task invisibility, action-without-target-page-read-permission invisibility, fixed-asset shared-pool removal, rejected-to-returned conversion, cancelled removal, personal ownership, `part_viewed` retention, and the OA rows in the priority/waiting-time matrix. Mock `RemoteConfigService` to prove 23:59:59 remains important, 24:00:00 becomes urgent, and remote/malformed configuration falls back to 24 hours.

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaTodoServiceImplTest,OaTodoMapperBindingTest,OaTodoControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because the OA provider is absent.

- [ ] **Step 3: Reuse Flowable authorization, not status guessing**

Extract the existing current-user Flowable task lookup from `OaPurchaseServiceImpl.selectTodoPurchases` into a package service helper used by both legacy purchase todo and `OaTodoServiceImpl`. Union only purchase rows whose `current_task_id` is in the deduplicated authorized task IDs.

- [ ] **Step 4: Implement the OA union**

Exact rows:

```text
OA_PURCHASE_APPROVAL          status=submitted + actual Flowable task + oa:todo:approve + oa:todo:list
OA_PURCHASE_RETURNED          status=rejected + applicant_id=current user + oa:purchase:add + oa:purchase:list
OA_FIXED_ASSET_REPAIR_CONFIRM status=pending_confirm + org scope + oa:fixedAsset:repair:confirm + oa:fixedAsset:repair:list
OA_LABOR_CONTRACT_SIGN        employee_id=current user + status=pending_sign
OA_SIGN_PACKAGE_SIGN          employee_id=current user + status in (pending_sign, part_viewed)
```

Personal signing rows route to their login-only owned mobile pages on both desktop and mobile, so they require login and ownership rather than HR administrator/list permissions. Fixed-asset rows are a shared pool and disappear for everyone as soon as any authorized confirmer completes the record.

Inject the already available `RemoteConfigService` in `OaTodoServiceImpl` and load `todo.approval.urgent.hours` with the same `1..168` validation/fallback rule as inventory. Join `ACT_RU_TASK` by `current_task_id` for the approval task creation timestamp; do not use purchase creation time to age the current approval node.

- [ ] **Step 5: Add current/all-authorized OA scope**

Add mapper methods that expand authorized roots to active business descendants. Pass separate `currentScopeDeptIds` and `authorizedScopeDeptIds` into all OA mapper methods and apply category defaults per union branch. Approval and personal rows remain visible across current organization when they belong to the actual user; fixed-asset rows honor the requested scope. Set actionable context to `oa_purchase.shop_dept_id` for purchase approval/returned and to the repair's store for fixed assets; personal rows omit context because their owner endpoints ignore `Dept-NumId`.

- [ ] **Step 6: Verify and commit**

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaTodoServiceImplTest,OaTodoMapperBindingTest,OaTodoControllerTest,OaPurchaseServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add erp-modules/erp-oa
git commit -m "feat(oa): expose unified todo provider"
```

## Task 7: Reconcile HR work, add stable organization ownership, and expose system reminders

**Files:**

- Replay and reconcile: `codex/hr-personnel-management` commits `cc1eeaa3^..e42315c8`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileQuery.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrImportExportController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/hr/HrProfileSummary.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java`
- Modify: `erp-ui/src/api/hr/employee.js`
- Modify: `erp-ui/src/views/hr/employee/index.vue`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Modify: `erp-ui/src/views/hr/completeness/index.vue`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeProfileControllerTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeProfileMapperBindingTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeProfileServiceImplTest.java`
- Modify: `erp-ui/test/hrPersonnelManagement.test.js`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/constant/SysTodoTypes.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysTodoCandidateRow.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysTodoMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysTodoService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysTodoController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysTodoControllerTest.java`

- [ ] **Step 1: Enforce the user-change safety gate**

Run read-only comparisons:

```bash
git -C /Users/liuxingyu/Desktop/备份/ERP-NEW status --short -- \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller \
  erp-modules/erp-system/src/main/java/com/erp/system/service \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml \
  erp-ui/src/api/hr erp-ui/src/views/hr
git diff --name-status 6月13号...codex/hr-personnel-management
```

If the first command reports uncommitted HR work, do not copy or overwrite it. Pause Task 7 for owner direction and continue non-HR tasks.

- [ ] **Step 2: Replay HR commits and resolve known conflicts**

```bash
git cherry-pick cc1eeaa3^..e42315c8
```

Resolve `SysUserProfile`, `SysUserProfileMapper.xml`, `SysUserServiceImpl`, and HR frontend conflicts by preserving current derived-field read rules while adding HR-owned source fields and onboarding fields. Continue the cherry-pick only after each conflict is reviewed.

- [ ] **Step 3: Write failing organization ownership tests**

Extend `HrEmployeeProfileMapperBindingTest` and `HrEmployeeProfileServiceImplTest` to require:

```java
profile.setDeptId(3101L);
service.saveEmployeeProfile(profile, "hr-user");
assertThat(saved.getDeptId()).isEqualTo(3101L);
assertThat(visibleToOtherOrg).isEmpty();
```

Cover manual save, import preview/confirm, onboarding confirmation, unbound pending employee, current scope, all authorized scope, and administrator full scope. Add adversarial tests for:

- `selectEmployeeProfile(profileId)` on a profile in another organization;
- updating a hidden profile while sending an authorized new `deptId`;
- moving an authorized profile to an unauthorized new `deptId`;
- confirming/cancelling onboarding by ID outside scope;
- import `UPDATE` matching a phone/employee number in another organization.
- offboard-account grouped `affectedCount` differing from the rows returned by the `offboardAccountOnly` HR filter.

- [ ] **Step 4: Add and backfill `sys_user_profile.dept_id`**

Extend both unified-todo migration copies with an idempotent `dept_id bigint` column and index. Backfill bound profiles from `sys_user.dept_id`; for unbound profiles, backfill by `store_name` only where exactly one active department has that name. Leave ambiguous rows null and exclude them from todo counts until HR assigns a department.

Require `deptId` for new manual/imported HR profiles and make HR forms select an authorized department ID. Scope queries with `p.dept_id = root.dept_id OR find_in_set(root.dept_id, d.ancestors)`; never use `store_name` as an authorization key.

Apply authorization at every ID-based boundary, not only list queries:

- detail validates the stored profile's existing `deptId`;
- update locks/loads the stored row and validates both old and requested new `deptId` before writing;
- confirm/cancel onboarding locks the row and validates its stored `deptId`;
- import update validates the matched stored row before exposing it in preview and again before confirm.

Administrators may bypass organization roots but must still hold the endpoint's HR permission. A null/ambiguous backfilled `deptId` is visible only to administrators until explicitly assigned.

- [ ] **Step 5: Verify the reconciled HR baseline**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeProfileMapperBindingTest,HrEmployeeProfileServiceImplTest,HrProfileCompletenessCalculatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs hrPersonnelManagement.test.js hrMenuPermissions.test.js
```

Expected: PASS before the todo provider is added.

- [ ] **Step 6: Write failing system-todo tests**

Test full-dataset counts, permission combinations, current/all scope, grouped organizations, 7/30-day boundaries, departed-contract exclusion, and enabled-account-only offboarding risk. Use more rows than the UI page size and a spy calculator to prove `HrProfileCompletenessCalculator.calculate` runs for every scoped candidate, not only the first page.

- [ ] **Step 7: Implement four grouped HR reminder types**

```text
HR_PROFILE_INCOMPLETE: non-cancelled/non-departed; calculator reports missing fields;
                       requires hr:completeness:list + hr:employee:list + hr:employee:edit
HR_ONBOARDING_CONFIRM: employee_status=待入职 and onboarding_status=待确认;
                       requires hr:onboarding:list + hr:onboarding:confirm + hr:employee:edit
HR_CONTRACT_DUE:       contract_end_date between today and today+30, employee not 离职;
                       requires hr:employee:list + hr:employee:edit
HR_OFFBOARD_ACCOUNT:   employee_status in (待离职,离职), linked sys_user status=0/del_flag=0;
                       requires hr:employee:list + system:user:edit
```

Return one aggregated item per `dept_id + type`. Put the underlying row count in `routeParams.count`, populate the department context fields, and require the same read/action permission combination used by the target HR page. Use the configured contract warning/urgent thresholds. Counts must be based on the full scoped result, never the current UI page.

Pass separate current and all-authorized department lists to `SysTodoMapper`; null `scopeMode` uses the category defaults, and an explicit toggle applies to every organization-bound HR branch.

`SysTodoMapper.selectScopedTodoCandidates(...)` returns every lightweight scoped profile candidate with the fields required by `HrProfileCompletenessCalculator`, department name/type, onboarding/contract dates, and linked account status. `SysTodoServiceImpl` must call the calculator in Java, create the four reminder flags, group by `deptId+type`, apply the priority/time matrix, then stable-sort and paginate the small grouped result in memory. `SysTodoController.list` returns `new TableDataInfo(pageRows, fullGroupedTotal)` and must not call `startPage()` before the candidate query; PageHelper would incorrectly limit completeness evaluation.

Add `offboardAccountOnly` to `HrProfileQuery`. When true, `selectUserProfileList` joins `sys_user` and returns only profiles in `待离职/离职` whose account is enabled and not deleted. Populate `HrProfileSummary.accountBound/accountEnabled/accountUserName`, expose the filter from `GET /hr/employee/list`, and let the HR employee page call the existing user-status API after `system:user:edit` checks. The grouped todo's `affectedCount` and filtered list total must be identical in the same organization/scope test.

- [ ] **Step 8: Verify and commit Task 7**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysTodoServiceImplTest,SysTodoMapperBindingTest,SysTodoControllerTest,HrEmployeeProfileServiceImplTest,HrEmployeeProfileMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
git add erp-api/erp-api-system erp-modules/erp-system erp-ui/src/api/hr erp-ui/src/views/hr \
  erp-ui/test/hrPersonnelManagement.test.js sql/erp_unified_todo_center_20260710.sql \
  docker/mysql/db/erp_unified_todo_center_20260710.sql
git commit -m "feat(system): add scoped hr todo reminders"
```

## Task 8: Make “all notices read” operate on all active notices

**Files:**

- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysNoticeReadService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeReadServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysNoticeReadMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysNoticeControllerTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeReadServiceImplTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysNoticeReadMapperBindingTest.java`
- Modify: `erp-ui/src/api/system/notice.js`
- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue`
- Create: `erp-ui/test/headerNoticeReadAll.test.js`

- [ ] **Step 1: Write failing all-read tests**

Assert no IDs are accepted from the top-five popover, every active unread notice is inserted, disabled notices are excluded, and the response returns a freshly queried `unreadCount`.

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysNoticeControllerTest,SysNoticeReadServiceImplTest,SysNoticeReadMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs headerNoticeReadAll.test.js
```

- [ ] **Step 3: Implement database-wide mark-all**

Add `markAllRead(userId)` and this mapper operation:

```sql
insert ignore into sys_notice_read (notice_id, user_id, read_time)
select n.notice_id, #{userId}, sysdate()
from sys_notice n
where n.status = '0'
  and not exists (
      select 1 from sys_notice_read r
      where r.notice_id = n.notice_id and r.user_id = #{userId}
  )
```

Make `POST /notice/markReadAll` parameterless and return the real unread count after insertion.

- [ ] **Step 4: Reload rather than locally zero**

Change `markNoticeReadAll()` to send no IDs. In `HeaderNotice`, await the request and then await `loadNoticeTop()`. On failure, preserve the current list/count. Remove every direct `this.unreadCount = 0` path.

- [ ] **Step 5: Verify and commit**

Run the Task 8 commands, then:

```bash
git add erp-modules/erp-system erp-ui/src/api/system/notice.js \
  erp-ui/src/layout/components/HeaderNotice/index.vue erp-ui/test/headerNoticeReadAll.test.js
git commit -m "fix(system): mark all active notices as read"
```

## Task 9: Build frontend provider APIs, aggregation, and deterministic routing

**Files:**

- Create: `erp-ui/src/api/workbench/todo.js`
- Create: `erp-ui/src/utils/todoAggregator.js`
- Create: `erp-ui/src/utils/todoRouteResolver.js`
- Create: `erp-ui/src/utils/todoNavigator.js`
- Create: `erp-ui/test/unifiedTodoAggregation.test.js`
- Create: `erp-ui/test/unifiedTodoRouteResolver.test.js`
- Create: `erp-ui/test/unifiedTodoNavigator.test.js`

- [ ] **Step 1: Write failing aggregation tests**

Test normalization of all three summaries, global `todoKey` deduplication, priority/time/ID sort, and exact multi-provider pagination without exceeding backend `pageSize<=100`.

For global page `p` with size `s`, calculate `requiredEnd=p*s`. For each provider, load its sorted prefix in pages of at most 100 until the provider prefix contains `requiredEnd` rows or its returned `total` is exhausted. Merge the three prefixes, sort, and slice `[(p-1)*s,requiredEnd)`. This is exact because no single provider can contribute more than `requiredEnd` rows to the first `requiredEnd` global results. It remains correct for page 11 and later without sending an invalid oversized request.

```js
assert.deepStrictEqual(aggregateSummaries(results).counts, {
  total: 9, approval: 3, execution: 2, returned: 1, risk: 2, personal: 1
})
assert.deepStrictEqual(sorted.map(item => item.todoKey), [
  "inventory:INV_OUT_OF_STOCK:1:view_risk",
  "oa:OA_PURCHASE_APPROVAL:2:approve",
  "system:HR_PROFILE_INCOMPLETE:3:complete_profile"
])
```

- [ ] **Step 2: Write failing route-matrix tests**

Cover every first-release type on desktop and mobile, store and warehouse aliases, required permission loss, missing route, and organization mismatch. Build an available-route set from registered constant routes (including hidden mobile processing pages) plus permission-filtered dynamic routes; do not use sidebar routes alone. Pass that set into the resolver and make it choose the first available candidate rather than a fixed alias. A resolver result is either:

```js
{ ok: true, location: { path, query } }
// or
{ ok: false, reason: "permission_changed" | "route_missing" | "organization_required" }
```

Add navigator tests for a cross-organization OA purchase, transfer approval, and stock-check approval. Before navigation, the helper must synchronously switch to the item's backend-supplied authorized `contextDeptId/name/type`, emit exactly one context-change event (which queues the asynchronous store refresh), and then resolve the route under the new STORE/WAREHOUSE context. A same-context or personal signing row must not switch context.

- [ ] **Step 3: Run and verify RED**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoAggregation.test.js unifiedTodoRouteResolver.test.js unifiedTodoNavigator.test.js
```

Expected: modules are missing.

- [ ] **Step 4: Implement provider clients**

Export `fetchTodoSummary(source, params)` and `fetchTodoList(source, params)` using this fixed map:

```js
const PROVIDERS = {
  inventory: "/inventory/todo",
  oa: "/oa/todo",
  system: "/system/todo"
}
```

Set `silentError: true` so the Todo Store can display one consolidated partial-failure message.

- [ ] **Step 5: Implement exact route families**

Map inventory rows to existing purchase, sales, delivery-notice, transfer, stock-check, return, and stock pages; OA confirmation rows to fixed-asset pages; personal rows to `/mobile/contract` and `/mobile/sign-package`. Desktop HR rows resolve to `/hr/completeness`, `/hr/onboarding`, or `/hr/employee`; mobile HR rows resolve to the hidden `/mobile/hr/completeness`, `/mobile/hr/onboarding`, or `/mobile/hr/employee` routes created in Task 12. `HR_OFFBOARD_ACCOUNT` uses the employee page with `offboardAccountOnly=true`, not the generic system-user list. For each type, store ordered STORE and WAREHOUSE candidate paths, then intersect them with the visible route set. Never assume `/inventory/...` or `/cangku/...` exists merely from `deptType`.

Use this route matrix; each location also carries `todoType`, `businessId`, and the provider `routeParams`:

| Types | Desktop candidates | Mobile path |
|---|---|---|
| purchase QC/receive | `/cangku/purchase`, `/inventory/purchase` | `/mobile/purchase` |
| sales notice create | `/inventory/sales`, `/cangku/sales` | `/mobile/sales` |
| delivery execute | `/cangku/deliveryNotice`, `/cangku/delivery-notice`, `/inventory/deliveryNotice`, `/inventory/delivery-notice` | `/mobile/outbound` |
| transfer approval | `/inventory/transfer`, `/cangku/transfer` | `/mobile/transfer-approval` |
| transfer deliver/receive/returned | `/inventory/transfer`, `/cangku/transfer` | `/mobile/transfer` |
| stock-check approval/returned/restart | `/inventory/stockCheck`, `/inventory/stock-check`, `/cangku/stockCheck`, `/cangku/stock-check` | `/mobile/stock-check` |
| purchase return confirm | `/cangku/purchaseReturn`, `/cangku/purchase-return`, `/inventory/purchaseReturn`, `/inventory/purchase-return` | `/mobile/purchase-return` |
| sales return confirm | `/inventory/salesReturn`, `/inventory/sales-return`, `/cangku/salesReturn`, `/cangku/sales-return` | `/mobile/sales-return` |
| low/out-of-stock | `/inventory/stock`, `/cangku/stock` | `/mobile/stock` |
| OA purchase approval | `/oa/todo?process=1` | `/mobile/oa-todo` |
| OA purchase returned | `/oa/purchase` | `/mobile/oa-purchase` |
| fixed-asset confirm | `/oa/fixedAsset/repair` | `/mobile/fixed-asset-repair` |
| labor/sign-package personal | `/mobile/contract`, `/mobile/sign-package` | same corresponding mobile path |
| HR profile incomplete | `/hr/completeness` | `/mobile/hr/completeness` |
| HR onboarding | `/hr/onboarding` | `/mobile/hr/onboarding` |
| HR contract due | `/hr/employee?contractDue=true` | `/mobile/hr/employee?contractDue=true` |
| HR offboard account | `/hr/employee?offboardAccountOnly=true` | `/mobile/hr/employee?offboardAccountOnly=true` |

For desktop OA purchase approval, use `/oa/todo?process=1&purchaseId=...`; plain `/oa/todo` will redirect to the unified discovery page in Task 11.

- [ ] **Step 6: Implement context-safe navigation**

`navigateTodo(item, options)` must:

1. re-check `requiredPermission` and visible candidate routes;
2. validate organization-bound rows have a STORE/WAREHOUSE `contextDeptId/name/type`;
3. when context differs, call `setSelectedDept` once with the provider-supplied context; its event listener dispatches `todo/contextChanged`, while navigation can continue because request headers read the synchronously updated session context;
4. resolve again using the new visible/context route candidates;
5. push the final location, or show a mapped error and refresh summaries.

The provider is authoritative for context membership; the frontend must never synthesize a department ID from a business number.

- [ ] **Step 7: Verify and commit**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoAggregation.test.js unifiedTodoRouteResolver.test.js unifiedTodoNavigator.test.js
git add erp-ui/src/api/workbench/todo.js erp-ui/src/utils/todoAggregator.js \
  erp-ui/src/utils/todoRouteResolver.js erp-ui/src/utils/todoNavigator.js \
  erp-ui/test/unifiedTodoAggregation.test.js erp-ui/test/unifiedTodoRouteResolver.test.js \
  erp-ui/test/unifiedTodoNavigator.test.js
git commit -m "feat(ui): aggregate and route unified todos"
```

## Task 10: Add the resilient Vuex Todo Store and refresh lifecycle

**Files:**

- Create: `erp-ui/src/store/modules/todo.js`
- Create: `erp-ui/src/utils/todoRefreshEvents.js`
- Modify: `erp-ui/src/store/index.js`
- Modify: `erp-ui/src/store/getters.js`
- Modify: `erp-ui/src/store/modules/user.js`
- Modify: `erp-ui/src/utils/shopContext.js`
- Create: `erp-ui/test/unifiedTodoStore.test.js`
- Create: `erp-ui/test/unifiedTodoLifecycle.test.js`

- [ ] **Step 1: Write failing store-state tests**

Assert independent provider state:

```js
providers: {
  inventory: { summary: null, error: null, lastSuccessAt: null, stale: false },
  oa:        { summary: null, error: null, lastSuccessAt: null, stale: false },
  system:    { summary: null, error: null, lastSuccessAt: null, stale: false }
},
listCache: {}
```

Test that one rejection retains that provider's last summary with `stale=true`, while successful providers update. With no history, failed source contributes `unknown`, not zero. Key list cache entries by `source + normalized(category,scopeMode,keyword,pageNum,pageSize,contextDeptId)` and store `rows`, `total`, `lastSuccessAt`, `stale`, and `error`. A failed list request must reuse the matching source/query/context cache entry; it must never reuse rows from another organization or filter.

- [ ] **Step 2: Write failing lifecycle tests**

Using fake timers/document visibility, assert:

- one in-flight refresh blocks duplicate refreshes;
- a context change during an in-flight request increments `contextVersion`, prevents old-context results from committing, and queues exactly one immediate refresh for the latest context;
- visible pages poll every 60 seconds;
- hidden pages stop polling;
- visibility restoration refreshes immediately;
- organization change refreshes immediately;
- logout removes timers/listeners and clears state.

- [ ] **Step 3: Run and verify RED**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoStore.test.js unifiedTodoLifecycle.test.js
```

- [ ] **Step 4: Implement namespaced Todo Store**

Actions: `start`, `stop`, `contextChanged`, `refreshSummaries`, `refreshPage`, and `invalidateAfterMutation`. Use `Promise.allSettled`; maintain one request promise as the lock plus `refreshQueued` and monotonic `contextVersion`. If an event arrives while locked, set the queue flag. In `finally`, run exactly one new refresh using the newest context. Discard any response whose captured context version no longer equals state.

`refreshPage` must use the per-provider normalized-query list cache described above. It returns successful rows plus stale cached rows for failed providers and an `unknownSources` list where no cache exists. Getters: `todoTotal`, `todoCounts`, `todoRecent`, `todoPartialFailure`, and `todoProviderStates`.

- [ ] **Step 5: Wire lifecycle sources**

After `GetInfo` commits roles/permissions, dispatch `todo/start`. Before both logout paths clear credentials, dispatch `todo/stop`. Emit `erp:dept-changed` from `setSelectedDept` and `clearSelectedDept`; `todo/start` listens once, dispatches `contextChanged`, and removes the listener in `stop`.

- [ ] **Step 6: Verify and commit**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoAggregation.test.js unifiedTodoStore.test.js unifiedTodoLifecycle.test.js
git add erp-ui/src/store erp-ui/src/utils/todoRefreshEvents.js \
  erp-ui/src/utils/shopContext.js erp-ui/test/unifiedTodoStore.test.js \
  erp-ui/test/unifiedTodoLifecycle.test.js
git commit -m "feat(ui): manage resilient todo state"
```

## Task 11: Add desktop badge, home summaries, and unified desktop page

**Files:**

- Create: `erp-ui/src/layout/components/HeaderTodo/index.vue`
- Create: `erp-ui/src/views/workbench/todo/index.vue`
- Modify: `erp-ui/src/layout/components/Navbar.vue`
- Modify: `erp-ui/src/router/index.js`
- Modify: `erp-ui/src/views/index.vue`
- Modify: `erp-ui/src/views/oa/todo/index.vue`
- Create: `erp-ui/test/unifiedTodoDesktop.test.js`

- [ ] **Step 1: Write failing desktop contracts**

Assert the navbar renders separate `HeaderTodo` and `HeaderNotice`, caps the todo badge at `99+`, and never reads notice unread count for todo totals. Assert `/workbench/todo` is a hidden constant route and home summary values come from Todo Store getters.

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui && node scripts/run-node-tests.cjs unifiedTodoDesktop.test.js
```

- [ ] **Step 3: Implement HeaderTodo**

Show total, five most urgent recent items, provider-stale indicators, “查看全部”, and per-item “去处理”. On resolver failure, show the mapped reason and dispatch `todo/refreshSummaries`; never open a blank route.

- [ ] **Step 4: Implement `/workbench/todo`**

Add category tabs, source filter, current/all-authorized scope, keyword, stable list, loading/empty/partial-failure states, pagination, and manual refresh. Opening a row does not mutate its status. Dispatch Task 10's cached `todo/refreshPage`, which uses Task 9's exact paged-prefix merge. Mark stale provider rows and show unknown sources instead of silently replacing them with an empty list.

- [ ] **Step 5: Replace static home content**

Change “审批流转” to show real total plus approval/execution/risk subtotals. Change “今日建议” to the highest-priority real recent items. When a provider has never loaded successfully, show “部分数据未知” rather than zero.

- [ ] **Step 6: Preserve legacy OA processing while redirecting discovery**

In `views/oa/todo/index.vue`, if `query.process !== '1'`, replace the route with:

```js
{ path: "/workbench/todo", query: { source: "oa", category: "approval" } }
```

When `process=1`, keep the existing approve/reject UI, set the list query to the supplied `purchaseId`, and scroll/highlight the matching row after load. This avoids a redirect loop from unified todo processing links.

- [ ] **Step 7: Verify and commit**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoDesktop.test.js oaPurchaseInteractionConsistency.test.js
git add erp-ui/src/layout/components/HeaderTodo erp-ui/src/layout/components/Navbar.vue \
  erp-ui/src/router/index.js erp-ui/src/views/index.vue \
  erp-ui/src/views/workbench/todo erp-ui/src/views/oa/todo/index.vue \
  erp-ui/test/unifiedTodoDesktop.test.js
git commit -m "feat(ui): add desktop todo center"
```

## Task 12: Add mobile todo page and replace mobile workbench counts

**Files:**

- Create: `erp-ui/src/views/mobile/todo/index.vue`
- Create: `erp-ui/src/views/mobile/hr/components/MobileHrProfileEditor.vue`
- Create: `erp-ui/src/views/mobile/hr/employee/index.vue`
- Create: `erp-ui/src/views/mobile/hr/onboarding/index.vue`
- Create: `erp-ui/src/views/mobile/hr/completeness/index.vue`
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/inventory/index.vue`
- Modify: `erp-ui/src/views/mobile/inventory/workbenchData.js`
- Modify: `erp-ui/src/views/mobile/inventory/workbenchService.js`
- Modify: `erp-ui/src/views/mobile/inventory/workbenchMapper.js`
- Create: `erp-ui/test/unifiedTodoMobile.test.js`
- Modify: `erp-ui/test/mobileInventoryWorkbench.test.js`
- Modify: `erp-ui/test/mobileWorkbenchTaskRouting.test.js`

- [ ] **Step 1: Write failing mobile contracts**

Assert `/mobile/todo` exists but is not a sixth bottom navigation item. Assert workbench badge, task cards, and mobile todo page all use the Todo Store total rather than the transfer-only summary. Assert notice remains a separate entry.

Assert hidden routes exist for `/mobile/hr/employee`, `/mobile/hr/onboarding`, and `/mobile/hr/completeness`; contract-due/profile-incomplete/onboarding todo types resolve to those pages, and offboard-account resolves to `/mobile/hr/employee?offboardAccountOnly=true`. Verify each HR route checks the same permissions as its provider item.

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoMobile.test.js mobileInventoryWorkbench.test.js mobileWorkbenchTaskRouting.test.js
```

- [ ] **Step 3: Implement the mobile page**

Use compact category chips, current/all-authorized toggle, provider failure banner, infinite/load-more pagination using the same deterministic merge, and full-width “去处理” actions. Respect safe areas and existing mobile shell typography.

- [ ] **Step 4: Add actionable mobile HR views**

Build the three hidden mobile HR pages on the existing HR APIs introduced by Task 7:

- employee: filter by `deptId/profileId`, edit incomplete fields and contract end date through `MobileHrProfileEditor`;
- onboarding: filter `待确认`, show missing fields, confirm or cancel with the existing endpoints;
- completeness: grouped score/missing-field list with a link to the employee editor;
- offboard account: load `/mobile/hr/employee` with `deptId` and `offboardAccountOnly=true`, display the grouped affected rows, and call the existing user-status endpoint for each selected bound account.

The views must consume `businessId/deptId/todoType` focus params, enforce old/new organization authorization through the backend, and refresh Todo Store after success.

- [ ] **Step 5: Repoint the workbench**

The mobile shell reads `todoTotal` and `todoCounts`; existing inventory summary remains for inventory metrics that are not business todos. Replace transfer-only approval cards with the Todo Store's recent approval/execution/risk items. Keep existing business shortcuts and bottom navigation unchanged.

- [ ] **Step 6: Verify and commit**

Run the Task 12 command, then:

```bash
git add erp-ui/src/views/mobile erp-ui/test/unifiedTodoMobile.test.js \
  erp-ui/test/mobileInventoryWorkbench.test.js erp-ui/test/mobileWorkbenchTaskRouting.test.js
git commit -m "feat(ui): add mobile todo center"
```

## Task 13: Focus business records and refresh after successful mutations

**Files:**

- Create: `erp-ui/src/utils/todoMutationMatcher.js`
- Modify: `erp-ui/src/utils/request.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/featureService.js`
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`
- Modify: `erp-ui/src/views/inventory/sales/index.vue`
- Modify: `erp-ui/src/views/inventory/deliveryNotice/index.vue`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Modify: `erp-ui/src/views/inventory/stockCheck/index.vue`
- Modify: `erp-ui/src/views/inventory/purchaseReturn/index.vue`
- Modify: `erp-ui/src/views/inventory/salesReturn/index.vue`
- Modify: `erp-ui/src/views/inventory/stock/index.vue`
- Modify: `erp-ui/src/views/oa/purchase/index.vue`
- Modify: `erp-ui/src/views/oa/fixedAsset/repair/index.vue`
- Modify: `erp-ui/src/views/mobile/contract/index.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Modify after Task 7: `erp-ui/src/views/hr/employee/index.vue`
- Modify after Task 7: `erp-ui/src/views/hr/completeness/index.vue`
- Modify after Task 7: `erp-ui/src/views/hr/onboarding/index.vue`
- Create: `erp-ui/test/unifiedTodoMutationRefresh.test.js`
- Create: `erp-ui/test/unifiedTodoBusinessFocus.test.js`

- [ ] **Step 1: Write failing mutation matcher tests**

Use this explicit method + anchored-path allowlist; strip the query string before matching:

```text
POST   ^/inventory/purchase/submit$
POST   ^/inventory/purchase/(receive|qc)/\d+$
DELETE ^/inventory/purchase/(delete/)?\d+$
POST   ^/inventory/sales/submit$
DELETE ^/inventory/sales/\d+$
POST   ^/inventory/deliveryNotice/(create|deliver)/\d+$
DELETE ^/inventory/deliveryNotice/\d+$
POST   ^/inventory/transfer/(submit|approve)$
POST   ^/inventory/transfer/(deliver|receive)/\d+$
POST   ^/inventory/transfer/shipment/receive/\d+$
DELETE ^/inventory/transfer/\d+$
POST   ^/inventory/stockCheck/(submit|restart|cancel)/\d+$
POST   ^/inventory/stockCheck/approval/\d+/(approve|reject)$
DELETE ^/inventory/stockCheck/[\d,]+$
POST   ^/inventory/(purchaseReturn|salesReturn)/(submit)$
POST   ^/inventory/(purchaseReturn|salesReturn)/confirm/\d+$
DELETE ^/inventory/(purchaseReturn|salesReturn)/\d+$
POST   ^/inventory/stock/adjust$
POST   ^/oa/purchase/(submit|approve)$
POST   ^/oa/purchase/\d+/close$
POST   ^/oa/fixedAsset/repair/submit$
POST   ^/oa/fixedAsset/repair/\d+/confirm$
POST   ^/oa/laborContract/(send)$
POST   ^/oa/laborContract/\d+/void$
POST   ^/oa/laborContract/mobile/\d+/sign$
POST   ^/oa/signPackage/\d+/(send|void)$
POST   ^/oa/signPackage/mobile/\d+/(read/\d+|sign)$
POST   ^/system/hr/employee$
PUT    ^/system/hr/employee$
POST   ^/system/hr/onboarding/\d+/(confirm|cancel)$
POST   ^/system/hr/import/confirm$
PUT    ^/system/user/changeStatus$
```

The transfer `/approve` body represents both approve and reject and must match either outcome. Add one positive test per row family and negative tests for saves that cannot change a todo predicate, GET/list/detail calls, exports, login, notice-read operations, and similar prefixes with extra suffixes.

- [ ] **Step 2: Write failing focus tests**

For every type, resolve a location containing `todoType`, `businessId`, and action-specific IDs. Assert these exact focus behaviors:

- transfer/stock-check/OA purchase approvals open the existing approval dialog for the focused row;
- purchase QC/receive, delivery execute, transfer deliver/receive, and both return confirmations open their existing action dialog;
- sales notice-create focuses the sales row and opens its existing notice-create confirmation;
- returned transfer/purchase/stock-check opens the existing edit/detail flow; stock-check restart opens the restart confirmation;
- risk rows load the stock page with the correct organization and `outOfStock` or `lowStock` filter;
- fixed-asset repair opens the confirm action;
- labor contract/sign package opens the owned mobile detail/sign screen;
- grouped HR rows load the target organization/filter and, when `profileId` is supplied, open the mobile/desktop editor or confirmation sheet;
- offboard account loads the exact organization with `offboardAccountOnly=true`, shows the same `affectedCount` rows as the provider, and exposes an account-disable action for each bound enabled user.

If the focused row is absent after the first exact-ID load, show “事项已处理” and refresh the Todo Store. Do not silently fall back to the first row in the list.

- [ ] **Step 3: Run and verify RED**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoMutationRefresh.test.js unifiedTodoBusinessFocus.test.js
```

- [ ] **Step 4: Refresh centrally after confirmed success**

In the Axios success interceptor, call `matchTodoMutation(res.config.method, res.config.url)`. When it matches, schedule `todo/invalidateAfterMutation` without delaying the business response. The matcher must be the explicit anchored method/path table above, not “every POST/PUT”, so login, exports, and unrelated saves do not trigger noise.

- [ ] **Step 5: Add reusable focus consumption**

The generic mobile feature page must pass `businessId` into its query and select the matching row after load. Desktop list pages should apply the same query once, then clear only the transient focus flag with `router.replace` so normal paging does not reopen dialogs.

- [ ] **Step 6: Verify and commit**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  unifiedTodoRouteResolver.test.js unifiedTodoMutationRefresh.test.js unifiedTodoBusinessFocus.test.js
git add erp-ui/src/utils/request.js erp-ui/src/utils/todoMutationMatcher.js \
  erp-ui/src/views erp-ui/test/unifiedTodoMutationRefresh.test.js \
  erp-ui/test/unifiedTodoBusinessFocus.test.js
git commit -m "feat(ui): refresh and focus todo workflows"
```

## Task 14: Full verification, desktop/mobile QA, and integration handoff

**Files:** all files changed above.

- [ ] **Step 1: Run all provider tests**

```bash
mvn -pl erp-modules/erp-inventory -am test
mvn -pl erp-modules/erp-oa -am test
mvn -pl erp-modules/erp-system -am test
```

Expected: all three builds report `BUILD SUCCESS`; no failed or errored tests.

- [ ] **Step 2: Run the full supported frontend source suite**

```bash
cd erp-ui
find test -type f -name '*.test.js' \
  ! -name 'desktopAuditFollowupFixes.test.js' \
  ! -name 'mobileAppShell.test.js' -print0 \
  | sort -z | xargs -0 node scripts/run-node-tests.cjs
```

Expected: zero failures. The two excluded tests are existing local-asset environment checks and were excluded in the clean baseline; do not add new exclusions.

- [ ] **Step 3: Build production frontend**

```bash
cd erp-ui && npm run build:prod
```

Expected: successful Vue production build with no unresolved imports or template errors.

- [ ] **Step 4: Exercise the real desktop flows**

Using signed-in test accounts and the browser workflow, verify at widths 1440×900 and 1024×768:

- operations director sees actionable approve/reject buttons for actual transfer and stock-check candidates;
- non-candidate administrator does not see those approvals;
- approve removes approver todo; reject creates submitter returned todo;
- OA rejected request can resubmit or close; close removes it;
- QC does not double-count as purchase receive;
- desktop header, home, and unified page totals match;
- one provider outage leaves other counts and stale last-known data visible;
- notice badge remains separate and mark-all reloads the true unread count.

- [ ] **Step 5: Exercise the real mobile flows**

At 390×844 and 430×932, verify `/mobile/todo`, store workbench, and warehouse workbench:

- total badge and category counts equal desktop for the same account/context;
- no sixth bottom-nav item appears;
- every inventory, OA personal, and HR grouped item routes to an actionable page;
- switching organization refreshes counts immediately;
- hidden-tab polling pauses and visibility restoration refreshes once;
- long titles, `99+`, safe areas, empty states, and partial-failure banners do not overflow.

- [ ] **Step 6: Verify migration parity and repository state**

```bash
cmp sql/erp_unified_todo_center_20260710.sql \
  docker/mysql/db/erp_unified_todo_center_20260710.sql
git status --short
git log --oneline --decorate -15
```

Expected: `cmp` exits 0; only intentional files are present; every task has its own commit.

- [ ] **Step 7: Request code review and fix findings**

Use `requesting-code-review` against the approved design and this plan. Re-run the smallest affected test after every finding, then repeat Steps 1–6.

- [ ] **Step 8: Merge only after clean QA**

Use `finishing-a-development-branch`. Before merging to `6月13号`, re-check the primary dirty workspace and coordinate any overlapping user-owned files. Never force the merge over uncommitted work.

## Acceptance traceability

- Accurate same-account totals on four surfaces: Tasks 10–12 and Task 14.
- Candidate, permission, and organization enforcement: Tasks 3, 6, and 7.
- Removal only after business completion: provider SQL in Tasks 2, 5–7; refresh in Task 13.
- Reject-to-returned behavior and visible rejected state: Tasks 2, 5, 6, 11–13.
- Partial provider failure without false zero: Tasks 9–12.
- Desktop/mobile actionable routing: Tasks 9 and 13–14.
- Notices independent from business todos: Task 8 and Task 11.
- Existing mobile and business entries retained: Tasks 4, 11, and 12.
