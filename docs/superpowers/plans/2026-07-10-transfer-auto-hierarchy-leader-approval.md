# Transfer Auto Hierarchy Leader Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically resolve level-4 store and level-3 department highest leaders by employee profile hierarchy and minimum enabled post sort, while preserving configured level-2 and level-1 approval nodes.

**Architecture:** Keep the existing approval rule, node, instance, and task tables. Mark the two system-managed nodes through existing `node_role` values `level4_highest` and `level3_highest`; add them automatically when rules are saved. Resolve their candidates at submit time from `sys_user_profile + sys_user_post + sys_post`, snapshot candidates into existing tasks, and carry non-blocking missing-level warnings through the submit response and transfer status log.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5/AssertJ, Vue 2, Element UI, Node source-contract tests, MySQL 5.7-compatible migration SQL.

---

## File Structure

- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalNode.java`: transient resolved post-sort snapshot field.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalInstance.java`: non-persistent approval warnings returned to the submit service.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java`: non-persistent approval warnings returned by `/transfer/submit`.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`: submitter profile and highest-leader query contracts.
- Modify `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`: full-hierarchy, minimum-post-sort candidate SQL.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`: enforce the two read-only dynamic node markers.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`: resolve, skip, warn, snapshot, and remove the conflicting senior auto-approval shortcut.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`: return warnings and persist them in the submit status log.
- Modify backend tests in `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/` and mapper source tests under `src/test/java/com/erp/inventory/mapper/`.
- Modify `erp-ui/src/views/inventory/transfer/rules.vue`: display protected automatic nodes and preserve editable configured nodes.
- Modify `erp-ui/src/views/inventory/transfer/index.vue`: display successful-submit warnings.
- Modify `erp-ui/test/transferApprovalRules.test.js` and `erp-ui/test/transferRequestFormUx.test.js`: frontend contracts.
- Create matching migration files in `sql/` and `docker/mysql/db/` to convert each existing rule's first two ordered nodes to dynamic markers.

## Task 1: Enforce System-Managed Dynamic Nodes

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`
- Modify: `erp-ui/test/transferApprovalRules.test.js`
- Modify: `erp-ui/src/views/inventory/transfer/rules.vue`

- [ ] **Step 1: Write failing backend rule-normalization tests**

Add tests asserting that `saveRule` stores these first two nodes before all editable configured nodes:

```java
assertThat(savedNodes).extracting(InvTransferApprovalNode::getNodeRole)
        .containsExactly("level4_highest", "level3_highest", "post", "post");
assertThat(savedNodes).extracting(InvTransferApprovalNode::getNodeOrder)
        .containsExactly(1, 2, 3, 4);
assertThat(savedNodes.get(0).getPostCode()).isNull();
assertThat(savedNodes.get(1).getPostCode()).isNull();
```

Also assert repeated saves do not duplicate dynamic nodes.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=InvTransferApprovalRuleServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because saved rules currently preserve only submitted nodes.

- [ ] **Step 3: Implement minimal backend normalization**

In `InvTransferApprovalRuleServiceImpl`, define:

```java
private static final String NODE_ROLE_LEVEL4_HIGHEST = "level4_highest";
private static final String NODE_ROLE_LEVEL3_HIGHEST = "level3_highest";
```

Before validating/saving, rebuild the node list as canonical level 4, canonical level 3, then every non-dynamic submitted node. Renumber all nodes and default dynamic nodes to `any_one`, required count `1`, with no post ID/code/name.

- [ ] **Step 4: Verify backend GREEN**

Run the Task 1 backend test command. Expected: PASS.

- [ ] **Step 5: Write failing frontend automatic-node tests**

Update `transferApprovalRules.test.js` to assert:

```js
assert.deepStrictEqual(
  vm.emptyForm.call(vm).nodes.slice(0, 2).map(node => node.nodeRole),
  ["level4_highest", "level3_highest"]
)
assert.strictEqual(vm.isDynamicNode.call(vm, vm.emptyForm.call(vm).nodes[0]), true)
```

Assert the source disables editing, movement, deletion, post selection, and quorum controls for dynamic rows while configured rows remain editable.

- [ ] **Step 6: Run frontend test and verify RED**

Run:

```bash
cd erp-ui && npm test -- transferApprovalRules.test.js
```

Expected: FAIL because the page currently creates only editable `post` nodes.

- [ ] **Step 7: Implement the read-only UI nodes**

Add `dynamicNode(role)`, `ensureDynamicNodes(nodes)`, and `isDynamicNode(node)` helpers. Render dynamic node labels and an explanation alert, hide their post selectors, and disable moving/deleting them. `buildSubmitForm()` must always emit the two markers first.

- [ ] **Step 8: Verify frontend GREEN**

Run the Task 1 frontend command. Expected: PASS.

## Task 2: Resolve Highest Leaders by Full Profile Hierarchy

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferApprovalCandidateMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalNode.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`

- [ ] **Step 1: Write failing mapper source assertions**

Assert the candidate mapper XML contains profile joins, full hierarchy parameters, enabled user/post filters, non-departed filtering, minimum `post_sort`, store-level exact matching, department-level blank-store matching, and stable `user_id` ordering.

- [ ] **Step 2: Write failing service behavior tests**

Extend the fake candidate mapper with:

```java
Map<String, Object> selectApprovalOrgProfile(Long userId);
List<Map<String, Object>> selectHighestLeaderUsers(
        String companyName, String deptLevel1Name, String deptLevel2Name,
        String deptLevel3Name, String storeName, String hierarchyLevel);
```

Add RED tests for:

- normal employee: level 4, level 3, then configured level 2/1;
- tied minimum post sort: all tied users in the candidate snapshot;
- level-4 leader submitter: skip level 4;
- level-3 leader submitter: skip both dynamic levels;
- level-3 leader is never substituted by a user belonging to a concrete store;
- the old senior-post shortcut no longer auto-approves a level-3 leader submission.

- [ ] **Step 3: Run mapper and service tests and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=InventoryMapperBindingTest,InvTransferApprovalServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/test failure because the mapper methods and dynamic resolution do not exist.

- [ ] **Step 4: Implement mapper queries**

Add a submitter-profile query and a MySQL 5.7-compatible grouped highest-leader query. Normalize profile strings in Java; match company and levels 1-3 exactly, match level 4 exactly for store leaders, and require blank/`-` level 4 for department leaders. Query only enabled/non-deleted users, non-departed profiles, and enabled posts. Group by user and retain every user whose minimum enabled `post_sort` equals the organization-wide minimum.

- [ ] **Step 5: Implement dynamic candidate resolution**

Pre-resolve both candidate sets once per submit. For `level4_highest` and `level3_highest` nodes, use those sets instead of configured posts. Detect submitter membership to apply the confirmed skip rules. Add `resolvedPostSort` to the node and serialize it into `ruleSnapshot`. Remove `isSeniorManagedShopSubmitter` and its direct auto-approval branch.

- [ ] **Step 6: Verify GREEN**

Run the Task 2 Maven command. Expected: PASS.

## Task 3: Skip Missing Dynamic Leaders with Auditable Warnings

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalInstance.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- Modify: `erp-ui/test/transferRequestFormUx.test.js`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`

- [ ] **Step 1: Write RED tests for missing-level warnings**

Cover missing level 4, missing level 3, and both missing. Assert submission still creates tasks from remaining configured nodes and returns exact warnings. Keep the existing test that rejects when no dynamic or configured node produces any candidate.

- [ ] **Step 2: Write RED transfer-service propagation test**

Set warnings on the fake approval instance and assert:

```java
assertThat(submitted.getApprovalWarnings()).containsExactly(expectedWarning);
assertThat(statusLogMapper.logs.get(0).getReason()).contains(expectedWarning);
```

- [ ] **Step 3: Run backend tests and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=InvTransferApprovalServiceImplTest,InvTransferServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because warnings are not exposed or persisted.

- [ ] **Step 4: Implement warning propagation**

Add non-persistent `List<String> approvalWarnings` properties to instance and order. Build one warning per missing dynamic level, combine both missing levels into the confirmed direct-to-level-2 message, copy warnings to the submit response, and append them to the submit status-log reason.

- [ ] **Step 5: Verify backend GREEN**

Run the Task 3 backend command. Expected: PASS.

- [ ] **Step 6: Write and run RED frontend warning test**

Assert both `doSave(true)` and `handleSubmit(row)` inspect `res.data.approvalWarnings` and call `msgWarning` after a successful response.

- [ ] **Step 7: Implement and verify frontend warning display**

Add a single `showSubmitResult(response)` helper used by both submission paths. Run:

```bash
cd erp-ui && npm test -- transferRequestFormUx.test.js
```

Expected: PASS.

## Task 4: Migrate Existing First Two Nodes

**Files:**
- Create: `sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql`
- Create: `docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql`
- Modify: `erp-ui/test/dockerScripts.test.js`

- [ ] **Step 1: Write a failing migration contract test**

Assert both migration copies exist, match byte-for-byte, use a MySQL 5.7-compatible temporary ranking table, convert sequence 1/2 to `level4_highest`/`level3_highest`, clear their post fields, and leave sequence greater than 2 untouched.

- [ ] **Step 2: Run and verify RED**

Run:

```bash
cd erp-ui && npm test -- dockerScripts.test.js
```

Expected: FAIL because migration files do not exist.

- [ ] **Step 3: Create the paired migration**

Use a temporary table with a correlated count over `(node_order, node_id)` rather than MySQL 8 window functions. Update only ranked sequence 1 and 2. Include preflight selects showing affected rules and post-migration role order.

- [ ] **Step 4: Verify GREEN**

Run the Task 4 frontend test command. Expected: PASS.

## Task 5: Full Verification and Commit

**Files:** all files above.

- [ ] **Step 1: Run focused backend tests**

```bash
mvn -pl erp-modules/erp-inventory -am -Dtest=InvTransferApprovalRuleServiceImplTest,InvTransferApprovalServiceImplTest,InvTransferServiceImplTest,InventoryMapperBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass with zero failures/errors.

- [ ] **Step 2: Run full inventory module tests**

```bash
mvn -pl erp-modules/erp-inventory -am -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: reactor build success and zero test failures/errors.

- [ ] **Step 3: Run focused frontend tests**

```bash
cd erp-ui && npm test -- transferApprovalRules.test.js transferRequestFormUx.test.js dockerScripts.test.js
```

Expected: three files run, zero failed.

- [ ] **Step 4: Run the full frontend node-test gate**

```bash
cd erp-ui && npm test
```

Expected: all frontend node test files run, zero failed.

- [ ] **Step 5: Inspect diff and migration parity**

```bash
git diff --check
cmp sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql
git status --short
```

Expected: no whitespace errors, `cmp` exit 0, only intended files changed.

- [ ] **Step 6: Commit the implementation**

```bash
git add erp-modules/erp-inventory erp-ui/src/views/inventory/transfer erp-ui/test sql/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql docker/mysql/db/erp_inventory_transfer_auto_hierarchy_approval_20260710.sql docs/superpowers/plans/2026-07-10-transfer-auto-hierarchy-leader-approval.md
git commit -m "feat: automate transfer hierarchy leader approval"
```

## Task 6: Merge Back to `6月13号`

- [ ] **Step 1: Preserve the base worktree's unrelated dirty changes**

Record `git status --short`, then temporarily stash tracked changes in the base worktree with a unique message. Leave untracked files in place. Confirm the stash exists before merging.

- [ ] **Step 2: Merge the feature branch into `6月13号`**

Use a non-interactive merge from the base worktree. Restore the tracked stash immediately afterward and resolve any overlap by retaining both the user's pre-existing edits and this feature.

- [ ] **Step 3: Re-run focused verification on the merged result**

Run the focused backend and frontend commands from Task 5 in the base worktree.

- [ ] **Step 4: Confirm preservation and branch state**

Compare the base worktree's unrelated status with the recorded pre-merge status, verify the feature commit is an ancestor of `6月13号`, and remove the feature worktree only after all checks pass.
