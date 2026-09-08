# Stock Check Difference Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add mandatory operations-director approval before any stock-check difference changes inventory, while auto-completing snapshot-valid zero-difference checks and preserving rejection, self-approval, invalidation, and migration evidence on desktop and mobile.

**Architecture:** Keep stock-check approval independent from transfer approval. `InvStockCheckServiceImpl` owns draft, submit, restart, cancel, and delete state transitions; `InvStockCheckApprovalServiceImpl` owns approval instances, tasks, candidate validation, approve/reject, and history; `InvStockCheckAdjustmentService` owns snapshot locking and atomic inventory changes. MyBatis persistence stores immutable approval rounds, while desktop and mobile use the same REST contract.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, MySQL 8, JUnit 5, Mockito, AssertJ, Vue 2, Element UI, Node source-contract tests.

---

## File map

**Create:**

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheckApprovalInstance.java` — immutable approval-round metadata and detail snapshot.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheckApprovalTask.java` — candidate and actual approval action.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvStockCheckApprovalRequest.java` — approve/reject payload.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvStockCheckSnapshotChange.java` — stale snapshot evidence.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvStockCheckAdjustmentResult.java` — validation/apply result.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckApprovalInstanceMapper.java`.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckApprovalTaskMapper.java`.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckApprovalCandidateMapper.java`.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckApprovalInstanceMapper.xml`.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckApprovalTaskMapper.xml`.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckApprovalCandidateMapper.xml`.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvStockCheckApprovalService.java`.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckApprovalServiceImpl.java`.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckAdjustmentService.java`.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckApprovalServiceImplTest.java`.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckAdjustmentServiceTest.java`.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/StockCheckApprovalMapperBindingTest.java`.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/sql/StockCheckApprovalSqlSourceTest.java`.
- `sql/erp_inventory_stock_check_approval_20260710.sql` and mirrored `docker/mysql/db/erp_inventory_stock_check_approval_20260710.sql`.
- `erp-ui/test/stockCheckApprovalUx.test.js`.
- `erp-ui/test/mobileStockCheckApproval.test.js`.

**Modify:**

- `InvStatusConstants`, `InvStockCheck`, `InvStockCheckMapper`, `InvStockCheckDetailMapper`, `InvStateGuard`, `IInvStockCheckService`, `InvStockCheckServiceImpl`, and `InvStockCheckController` for the new state machine.
- `InvStockCheckMapper.xml` and `InvStockCheckDetailMapper.xml` for approval fields, todo filtering, and snapshot restart.
- `InvStockCheckServiceImplTest` for submission, rejection editing, restart, cancel, delete, and controller contracts.
- `erp-ui/src/api/inventory/stockCheck.js` and `erp-ui/src/views/inventory/stockCheck/index.vue` for desktop workflow.
- Mobile feature action, runtime, mapper, dialog, route, and workbench files for the same workflow.

### Task 1: Approval persistence model and mapper bindings

**Files:** domain, mapper, mapper XML, and binding-test files listed above.

- [ ] **Step 1: Write the failing mapper binding test**

Create `StockCheckApprovalMapperBindingTest` that parses all three XML resources and asserts these statement IDs:

```java
assertMapped(configuration, InvStockCheckApprovalInstanceMapper.class, "insertInstance");
assertMapped(configuration, InvStockCheckApprovalInstanceMapper.class, "selectByIdForUpdate");
assertMapped(configuration, InvStockCheckApprovalInstanceMapper.class, "selectByCheckId");
assertMapped(configuration, InvStockCheckApprovalTaskMapper.class, "insertTask");
assertMapped(configuration, InvStockCheckApprovalTaskMapper.class, "selectByInstanceIdForUpdate");
assertMapped(configuration, InvStockCheckApprovalCandidateMapper.class, "selectOperationsDirectorCandidates");
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=StockCheckApprovalMapperBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because the approval mapper types do not exist.

- [ ] **Step 3: Add exact domain and mapper contracts**

Use these mapper signatures:

```java
InvStockCheckApprovalInstance selectByIdForUpdate(Long instanceId);
InvStockCheckApprovalInstance selectRunningByCheckIdForUpdate(Long checkId);
List<InvStockCheckApprovalInstance> selectByCheckId(Long checkId);
int insertInstance(InvStockCheckApprovalInstance instance);
int updateInstance(InvStockCheckApprovalInstance instance);

InvStockCheckApprovalTask selectByInstanceIdForUpdate(Long instanceId);
List<InvStockCheckApprovalTask> selectByInstanceId(Long instanceId);
int insertTask(InvStockCheckApprovalTask task);
int updateTask(InvStockCheckApprovalTask task);

List<Map<String, Object>> selectOperationsDirectorCandidates(@Param("deptId") Long deptId);
```

The instance must expose `instanceId`, `checkId`, `roundNo`, `status`, submit/finish identity and time, summary counts, `detailSnapshot`, `invalidReason`, `invalidDetailSnapshot`, and a transient `tasks` list. The task must expose candidate snapshots, actual approver, comment, status, and `selfApproved`.

- [ ] **Step 4: Add XML with lock-safe and history-safe SQL**

Implement `FOR UPDATE` reads for the current instance/task, ordered history by `round_no desc`, generated keys on inserts, and explicit update fields. Candidate SQL must join user, post, role permission, and hierarchical `sys_user_shop` scope:

```sql
where p.post_code = 'yyzj'
  and m.perms = 'inv:stockCheck:approve'
  and target_dept.dept_id = #{deptId}
  and (target_dept.dept_id = scope_dept.dept_id
       or find_in_set(scope_dept.dept_id, target_dept.ancestors))
```

- [ ] **Step 5: Run GREEN and commit**

Expected: mapper binding test passes. Commit:

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper erp-modules/erp-inventory/src/main/resources/mapper/inventory erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/StockCheckApprovalMapperBindingTest.java
git commit -m "feat(inventory): add stock check approval persistence"
```

### Task 2: Snapshot validation and atomic inventory adjustment

**Files:** create `InvStockCheckAdjustmentService`, result VOs and test; modify no controller/UI files.

- [ ] **Step 1: Write failing adjustment tests**

Cover: all products are locked before writes; stale snapshot returns changes and performs no stock/log writes; valid profit and loss apply once; zero differences write no stock log. Assert the result contract:

```java
InvStockCheckAdjustmentResult result = service.evaluate(check, details, true);
assertThat(result.hasSnapshotChanges()).isFalse();
verify(stockLogMapper, times(2)).insertInvStockLog(any());
```

- [ ] **Step 2: Run RED**

Run the new test class; expect missing service/result types.

- [ ] **Step 3: Implement the two-phase evaluator**

Add:

```java
public InvStockCheckAdjustmentResult evaluate(InvStockCheck check,
        List<InvStockCheckDetail> details, boolean apply)
```

First lock every inventory row and compare `bookQty` with current quantity. Return all `InvStockCheckSnapshotChange` objects before any write if one differs. Only when the change list is empty and `apply=true` may the second loop call `addInvStockWithCost`/`deductInvStockWithCost` and insert `InvStockLog` rows.

- [ ] **Step 4: Run GREEN plus existing stock-check tests**

Run both `InvStockCheckAdjustmentServiceTest` and `InvStockCheckServiceImplTest`; expect zero failures.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckAdjustmentService.java erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckAdjustmentServiceTest.java
git commit -m "feat(inventory): validate stock check snapshots atomically"
```

### Task 3: Approval instance creation, candidate rules, approve/reject, and history

**Files:** create approval service/interface/request/test; consume Task 1 mappers and Task 2 adjustment service.

- [ ] **Step 1: Write failing service tests**

Tests must prove: no candidate blocks submit; candidate snapshots are saved; operations director can self-approve with `selfApproved="1"`; non-candidate fails; rejection requires a reason and sets instance/task to `rejected`; repeated action fails; history attaches tasks.

```java
InvStockCheckApprovalRequest request = new InvStockCheckApprovalRequest();
request.setInstanceId(41L);
request.setComment("差异核实无误");
service.approve(1001L, request, 20L);
assertThat(taskCaptor.getValue().getSelfApproved()).isEqualTo("1");
```

- [ ] **Step 2: Run RED**

Expected: approval service and request class are missing.

- [ ] **Step 3: Implement approval lifecycle**

Use interface methods:

```java
InvStockCheckApprovalInstance createPendingApproval(InvStockCheck check,
        List<InvStockCheckDetail> details);
void approve(Long checkId, InvStockCheckApprovalRequest request, Long selectedShopDeptId);
void reject(Long checkId, InvStockCheckApprovalRequest request, Long selectedShopDeptId);
void cancelRunning(Long checkId);
List<InvStockCheckApprovalInstance> selectTrack(Long checkId, Long selectedShopDeptId);
```

Build JSON detail snapshots with Fastjson2. On approve, lock check/instance/task, re-resolve current candidates, run Task 2 evaluator with `apply=true`, then either invalidate without inventory writes or complete task, instance, and check in the same transaction. On reject, require nonblank comment and set the check to `rejected` with visible last-rejection fields.

- [ ] **Step 4: Run GREEN**

Expected: all new approval service tests pass.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto erp-modules/erp-inventory/src/main/java/com/erp/inventory/service erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckApprovalServiceImpl.java erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckApprovalServiceImplTest.java
git commit -m "feat(inventory): add stock check approval lifecycle"
```

### Task 4: Stock-check submit, reject-edit, restart, cancel, and delete state machine

**Files:** modify status constants, stock-check domain/mappers/service/state guard and existing service tests.

- [ ] **Step 1: Replace old controller-contract and state tests with failing new cases**

Assert constants and transitions:

```java
assertThat(InvStatusConstants.PENDING_APPROVAL).isEqualTo("pending_approval");
assertThat(InvStatusConstants.INVALIDATED).isEqualTo("invalidated");
assertThat(InvStateGuard.isStockCheckEditable("rejected")).isTrue();
```

Add tests for save-only input, zero-difference auto-completion, difference submission, stale submit invalidation, restart clearing actual quantities, pending cancellation closing approval, and physical delete rejection when `approvalRound > 0`.

- [ ] **Step 2: Run RED**

Expected: new constants, fields, mapper methods, and state behavior are missing.

- [ ] **Step 3: Implement the new stock-check state flow**

Add constants `PENDING_APPROVAL` and `INVALIDATED`. Permit editing in `draft` and `rejected`; submitting in the same states; cancelling in `draft`, `rejected`, `pending_approval`, and `invalidated`. Change `inputActualQty` to save only. `submitCheck` must validate the snapshot before either auto-completing or creating an approval instance.

Extend `InvStockCheck` and mapper XML with approval/submission/rejection/invalidation fields. Add detail mapper method:

```java
int resetSnapshot(@Param("detailId") Long detailId,
        @Param("bookQty") BigDecimal bookQty,
        @Param("costPrice") BigDecimal costPrice);
```

Add `restartCheck(Long checkId, Long selectedShopDeptId)` and `selectApprovalTodoList(...)` to the service interface.

- [ ] **Step 4: Run GREEN**

Run existing and new stock-check/approval/adjustment tests; expect zero failures.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvStatusConstants.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheck.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvStockCheckService.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStateGuard.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckMapper.java erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckDetailMapper.java erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckMapper.xml erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckDetailMapper.xml erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckServiceImplTest.java
git commit -m "feat(inventory): enforce stock check approval states"
```

### Task 5: REST endpoints and permission boundary

**Files:** modify `InvStockCheckController`, service tests, and API contract.

- [ ] **Step 1: Write failing reflection and permission tests**

Require these endpoint mappings and annotations:

```text
POST /input/{checkId}             inv:stockCheck:submit (save only)
POST /submit/{checkId}            inv:stockCheck:submit
POST /restart/{checkId}           inv:stockCheck:submit
GET  /approval/todo               inv:stockCheck:approve
POST /approval/{checkId}/approve  inv:stockCheck:approve
POST /approval/{checkId}/reject   inv:stockCheck:approve
GET  /{checkId}/approval-track    inv:stockCheck:query
```

Assert `/confirm/{checkId}` is absent.

- [ ] **Step 2: Run RED**

Expected: submit/restart/approval endpoints are absent and confirm still exists.

- [ ] **Step 3: Implement controller contract**

Inject both stock-check services, start pagination for todo, pass the selected shop header to every read/write, and return the refreshed check or track payload after actions. Keep `@IdempotentSubmit(timeout = 30)` on submit/restart/approve/reject/cancel.

- [ ] **Step 4: Run GREEN**

Run inventory tests; expect controller contract and service tests to pass.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockCheckController.java erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckServiceImplTest.java
git commit -m "feat(inventory): expose stock check approval endpoints"
```

### Task 6: Idempotent database migration and legacy data classification

**Files:** create both SQL copies and `StockCheckApprovalSqlSourceTest`.

- [ ] **Step 1: Write failing SQL source tests**

Assert the two scripts are byte-identical and contain approval columns/tables, unique round index, `inv:stockCheck:approve`, `role_key='yyzj'`, legacy `checking` classification for incomplete/stale/zero-difference/difference rows, and no direct inventory update.

- [ ] **Step 2: Run RED**

Expected: SQL files do not exist.

- [ ] **Step 3: Implement repeat-safe migration**

Use `information_schema` helper procedures for missing columns/indexes, `CREATE TABLE IF NOT EXISTS` for instance/task tables, and `INSERT ... SELECT ... WHERE NOT EXISTS` for menu and role grants. Add backup tables before legacy updates. Do not grant approval to admin, store-manager, or warehouse roles.

- [ ] **Step 4: Validate SQL**

Run the source test, then apply the script twice to a temporary MySQL schema containing copies of `inv_stock_check`, `inv_stock_check_detail`, `inv_stock`, `sys_menu`, `sys_role`, and `sys_role_menu`. Expected: both applications succeed and row counts/permissions do not duplicate.

- [ ] **Step 5: Commit**

```bash
git add sql/erp_inventory_stock_check_approval_20260710.sql docker/mysql/db/erp_inventory_stock_check_approval_20260710.sql erp-modules/erp-inventory/src/test/java/com/erp/inventory/sql/StockCheckApprovalSqlSourceTest.java
git commit -m "feat(db): add stock check approval schema"
```

### Task 7: Desktop stock-check approval UX

**Files:** modify stock-check API/view and create `stockCheckApprovalUx.test.js`.

- [ ] **Step 1: Write failing desktop source-contract tests**

Assert API functions for submit, restart, todo, approve, reject, and track; no `confirmStockCheck`; a “待我审批” quick filter that calls the todo endpoint; status labels for pending/rejected/invalidated; separate save and submit buttons; reject reason banner; self-approval tag; approval history rendering; `inv:stockCheck:approve` guarding approval actions.

- [ ] **Step 2: Run RED**

Run `npm test -- stockCheckApprovalUx.test.js`; expect missing API/actions/statuses.

- [ ] **Step 3: Implement desktop API and view**

Use payloads:

```js
approveStockCheck(checkId, { instanceId, comment })
rejectStockCheck(checkId, { instanceId, comment })
```

Make rejection comment mandatory, approval comment optional, display the current difference summary before submit/approve, and load approval track with the detail. Add a permission-guarded “待我审批” filter backed by `listStockCheckApprovalTodos`. Keep `rejected` editable and expose “重新盘点” only for `invalidated`.

- [ ] **Step 4: Run GREEN and existing UX tests**

Run the new test plus `stockCheckSoldQuantityUx`, export, warehouse-scope, and unused-export tests.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/api/inventory/stockCheck.js erp-ui/src/views/inventory/stockCheck/index.vue erp-ui/test/stockCheckApprovalUx.test.js
git commit -m "feat(ui): add desktop stock check approval"
```

### Task 8: Mobile stock-check approval UX

**Files:** modify mobile feature action/runtime/mapper/dialog/route/workbench files and create `mobileStockCheckApproval.test.js`.

- [ ] **Step 1: Write failing mobile tests**

Assert draft/rejected actions include save and submit; pending action includes approve/reject only with the approve permission; invalidated action includes restart; a “待我审批” filter routes list loading through the todo endpoint; old confirm action is absent; mapper labels include pending/rejected/invalidated; rejection reason and self-approval appear in detail; route allows both `STORE` and `WAREHOUSE`; workbench queries `pending_approval` instead of `checking`.

- [ ] **Step 2: Run RED**

Run the new test with existing mobile feature action/runtime/mapper tests; expect old confirm behavior and labels.

- [ ] **Step 3: Implement mobile flow**

Import new APIs into `featureActionService` and `featureService`; when `query.approvalTodo` is true, load `listStockCheckApprovalTodos` instead of the normal list. Add runtime actions `saveStockCheckInput`, `submitStockCheck`, `approveStockCheck`, `rejectStockCheck`, `restartStockCheck`, and `cancelStockCheck`. Update `MobileActionDialog` so approve may collect an optional comment, reject requires a comment, and submit/approve show stock-difference rows.

- [ ] **Step 4: Run GREEN**

Run `mobileStockCheckApproval`, `mobileFeatureActions`, `mobileFeatureActionRuntime`, `mobileFeatureMapper`, `mobileFormPayloads`, `mobileFeatureFormConfig`, and mobile workbench tests.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/mobile/feature erp-ui/src/views/mobile/inventory erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/test/mobileStockCheckApproval.test.js erp-ui/test/mobileFeatureActions.test.js erp-ui/test/mobileFeatureActionRuntime.test.js erp-ui/test/mobileFeatureMapper.test.js
git commit -m "feat(mobile): add stock check approval workflow"
```

### Task 9: Full verification and implementation notes

**Files:** update the design/plan only if implementation names changed; do not add unrelated refactors.

- [ ] **Step 1: Run backend regression**

```bash
mvn -pl erp-modules/erp-inventory -am test
```

Expected: reactor success and zero inventory test failures.

- [ ] **Step 2: Run frontend regression and production build**

```bash
cd erp-ui
npm test
npm run build:prod
```

Expected: all Node tests pass and production build succeeds.

- [ ] **Step 3: Verify migration and forbidden bypasses**

Confirm both SQL copies are identical, `/confirm/{checkId}` and `confirmStockCheck` no longer exist, only approval service calls inventory adjustment, and `inv:stockCheck:approve` is granted only to `yyzj`.

- [ ] **Step 4: Inspect final diff and status**

Run `git diff --check`, `git status --short`, and `git log --oneline --decorate -10`. Expected: no whitespace errors, no generated build artifacts staged, and each task has a focused commit.

- [ ] **Step 5: Final commit if verification changed tracked files**

Stage only intentional source/docs changes and commit as:

```bash
git commit -m "test: verify stock check approval workflow"
```
