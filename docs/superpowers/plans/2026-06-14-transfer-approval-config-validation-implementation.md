# Transfer Approval Config Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a transfer approval rule validation flow that supports simplified and advanced configuration modes and explains missing approvers before transfer submission.

**Architecture:** Add an additive backend validation API that reuses the approval candidate resolution rules and returns node-level diagnostics. Update the transfer approval rule page to offer simplified and advanced modes, call validation from both modes, and block simplified saves when validation fails while allowing advanced saves with explicit confirmation.

**Tech Stack:** Java 17, Spring Boot, MyBatis, JUnit 5, Vue 2, Element UI, existing Node-based frontend component tests.

---

## File Structure

- Create `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvTransferApprovalRuleValidationRequest.java`
  - Request DTO carrying a draft rule and sample transfer context.
- Create `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferApprovalRuleValidationResult.java`
  - Response VO carrying matched/valid state and node diagnostics.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferApprovalService.java`
  - Expose validation method for rule drafts.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java`
  - Extract candidate resolution diagnostics from existing submit-time logic.
- Modify `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java`
  - Add `/rule/validate` endpoint.
- Modify `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java`
  - Add RED/GREEN tests for validation statuses and the current `ll` scenario.
- Modify `erp-ui/src/api/inventory/transferApprovalRule.js`
  - Add frontend API wrapper.
- Modify `erp-ui/src/views/inventory/transfer/rules.vue`
  - Add simplified/advanced mode controls, grouped advanced sections, validation summary, and save behavior.
- Modify `erp-ui/test/transferApprovalRules.test.js`
  - Add component-script tests for payload construction, mode behavior, and validation save blocking.

## Task 1: Backend Validation DTOs and Service Contract

- [ ] **Step 1: Write failing service tests**

Add tests in `InvTransferApprovalServiceImplTest`:

```java
@Test
@DisplayName("规则预检返回缺少候选人节点而不是创建审批实例")
void shouldValidateRuleAndReturnMissingCandidates()
{
    SecurityContextHolder.setUserId("104");
    SecurityContextHolder.setUserName("ll");
    FakeCandidateMapper candidateMapper = new FakeCandidateMapper();
    FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper()
            .addAncestorDepts(108L, 102L, 100L);
    InvTransferApprovalServiceImpl service = newService(
            rule(node(1, "第 1 级审批", "post", "dz"),
                    node(2, "第 2 级审批", "post", "yyjl"),
                    node(3, "第 3 级审批", "post", "yyzj")),
            candidateMapper, new FakeInstanceMapper(), new FakeTaskMapper(), deptScopeMapper);
    InvTransferOrder transfer = transfer();
    transfer.setTransferType("warehouse");
    transfer.setFromDeptId(104L);
    transfer.setToDeptId(108L);

    InvTransferApprovalRuleValidationResult result = service.validateRuleForTransfer(
            rule(node(1, "第 1 级审批", "post", "dz"),
                    node(2, "第 2 级审批", "post", "yyjl"),
                    node(3, "第 3 级审批", "post", "yyzj")),
            transfer);

    assertThat(result.isMatched()).isTrue();
    assertThat(result.isValid()).isFalse();
    assertThat(result.getNodes()).extracting(InvTransferApprovalRuleValidationResult.NodeResult::getStatus)
            .containsExactly("skipped", "missing_candidate", "missing_candidate");
}
```

Run:

```bash
mvn -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest#shouldValidateRuleAndReturnMissingCandidates test
```

Expected: compile fails because the validation result type and service method do not exist.

- [ ] **Step 2: Create DTO/VO classes and interface method**

Create the request/response classes and add:

```java
InvTransferApprovalRuleValidationResult validateRuleForTransfer(InvTransferApprovalRule rule, InvTransferOrder transfer);
```

to `IInvTransferApprovalService`.

- [ ] **Step 3: Implement minimal validation result generation**

In `InvTransferApprovalServiceImpl`, add `validateRuleForTransfer` and reuse `sortedNodes`, `resolveCandidates`, and skipped-node detection to return node results.

- [ ] **Step 4: Run the RED test until GREEN**

Run the same Maven command. Expected: PASS.

## Task 2: Backend Diagnostic Detail

- [ ] **Step 1: Add failing tests for successful, missing-scope, and self-only results**

Extend `InvTransferApprovalServiceImplTest` with focused tests asserting:

- `ok` when candidates resolve.
- `missing_scope` when users exist but fail approval-scope binding.
- `self_approval_only` when all candidates are the submitter and self-approval is disabled.

- [ ] **Step 2: Extend candidate diagnostics**

Add helper methods in `InvTransferApprovalServiceImpl` to collect:

- approval scope department id
- candidate search department ids
- candidate users before scope filtering
- effective candidates after scope filtering
- actionable message

- [ ] **Step 3: Run backend service tests**

```bash
mvn -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest test
```

Expected: PASS.

## Task 3: Backend Controller Endpoint

- [ ] **Step 1: Add endpoint method**

Add `POST /rule/validate` to `InvTransferApprovalRuleController`, accepting `InvTransferApprovalRuleValidationRequest` and returning `success(transferApprovalService.validateRuleForTransfer(...))`.

- [ ] **Step 2: Run backend rule/controller-adjacent tests**

```bash
mvn -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest,InvTransferApprovalRuleServiceImplTest test
```

Expected: PASS.

## Task 4: Frontend API and Component Test Coverage

- [ ] **Step 1: Add failing frontend tests**

Update `erp-ui/test/transferApprovalRules.test.js` to assert:

- `emptyForm()` defaults to simplified mode.
- `buildValidationPayload()` includes `rule` and `transferSample`.
- Simplified save blocks when validation result is invalid.
- Advanced mode can proceed after confirmation when validation is invalid.

Run:

```bash
node test/transferApprovalRules.test.js
```

Expected: FAIL because these methods/properties do not exist yet.

- [ ] **Step 2: Add API wrapper**

Add `validateTransferApprovalRule(data)` to `erp-ui/src/api/inventory/transferApprovalRule.js`.

- [ ] **Step 3: Implement component methods**

Add:

- `configMode`
- `validationResult`
- `validationLoading`
- `buildValidationPayload`
- `runValidation`
- `isValidationBlocking`
- `doSubmitAfterValidation`

Keep existing `buildSubmitForm()` API-compatible.

- [ ] **Step 4: Run frontend test**

```bash
node test/transferApprovalRules.test.js
```

Expected: PASS.

## Task 5: Frontend UI Implementation

- [ ] **Step 1: Add simplified/advanced mode controls**

Add a segmented control at the top of the dialog with `简化配置` and `高级配置`.

- [ ] **Step 2: Add simplified mode fields**

Show business scenario, scope, approval chain, and validation summary.

- [ ] **Step 3: Reorganize advanced mode**

Wrap existing fields in section dividers:

- 规则匹配
- 审批策略
- 审批节点
- 预检结果

- [ ] **Step 4: Add validation summary UI**

Render each node status with candidates and message.

- [ ] **Step 5: Run frontend test again**

```bash
node test/transferApprovalRules.test.js
```

Expected: PASS.

## Task 6: Final Verification

- [ ] **Step 1: Run backend tests**

```bash
mvn -pl erp-inventory -Dtest=InvTransferApprovalServiceImplTest,InvTransferApprovalRuleServiceImplTest test
```

Expected: PASS.

- [ ] **Step 2: Run frontend tests**

```bash
node test/transferApprovalRules.test.js
```

Expected: PASS.

- [ ] **Step 3: Check worktree status**

```bash
git status --short
```

Expected: only intended implementation files are modified.
