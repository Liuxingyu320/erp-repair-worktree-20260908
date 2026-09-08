# Transfer Approval Configuration UX and Validation Design

## Goal

Make transfer approval rules configurable without requiring users to understand the backend rule engine. The configuration page should support a safe simplified mode for normal operators and a better structured advanced mode for administrators, with backend-backed validation that explains why an approval chain will or will not produce valid approvers.

The immediate production symptom this design addresses is user `ll` submitting a warehouse transfer request and receiving `审批规则未配置有效审批人`. The current system matched the rule, but all configured nodes resolved to no effective candidates.

## Current Problem

The existing page exposes low-level rule fields directly:

- `approvalMode`
- `nodeRole`
- `postCode`
- `requiredCount`
- rule scope fields

Those fields are technically accurate but hard to configure safely. Users cannot see how a node maps to real approvers until a transfer is submitted. For the current `ll` case, the rule is configured as `店长 -> 运营经理 -> 运营总监`, but the backend skips the `post + dz` store-manager node and then looks for `yyjl` and `yyzj` on the target store ancestor chain. No valid candidate exists there, so submission fails.

## Users and Modes

The page will support two modes.

Simplified mode is the default for normal users. It hides backend terms and guides users through business scenarios and approval chains.

Advanced mode is available to administrators. It keeps the full rule engine capabilities but organizes them into understandable sections, adds guardrails, and uses the same validation results as simplified mode.

## Simplified Mode

Simplified mode shows:

- Rule name
- Business scenario:
  - Store requests goods from warehouse
  - Cross-store transfer
  - Warehouse transfer
- Applicable scope:
  - All
  - Selected organization or store
- Approval chain:
  - Ordered approval levels
  - Each level selects a post and a business search scope
- Validation result for each approval level

Simplified mode must not generate hidden-invalid configurations. For example, it should not create a `post + dz` node for a warehouse request flow if the backend will skip that node. If the intended behavior is store-manager approval, the mode should map it to an explicit supported node role or mark the selected template as unsupported until the backend can resolve that scope safely.

## Advanced Mode

Advanced mode keeps complete configuration power, but the UI is reorganized into these sections:

- Rule matching:
  - Document type
  - Transfer type
  - Scope type and scope value
  - Quantity condition
- Approval strategy:
  - Overall approval mode
  - Required count when quorum is selected
  - Reject action
  - Self-approval setting
  - Priority and status
- Approval nodes:
  - Node order
  - Node display name
  - Approval scope
  - Post selector
  - Node-level approval mode
- Validation result:
  - Matched sample context
  - Candidate search departments
  - Effective approvers
  - Blocking reasons

Advanced labels should explain behavior in business language. Examples:

- `post`: search configured post on the target store ancestor chain
- `from_leader`: search only the source department
- `to_leader`: search only the target department

Risky combinations should be flagged inline. For example, `post + dz` in a store request scenario should show that the backend currently skips this node and therefore it is not a useful approval level in that flow.

Advanced mode may allow saving a risky rule only after confirmation. The validation warning must remain visible. Transfer submission must still be blocked by backend validation if no effective approver exists.

## Backend Validation API

Add a backend validation endpoint:

```http
POST /inventory/transfer/rule/validate
```

Request shape:

```json
{
  "rule": {
    "ruleId": 3,
    "ruleName": "门店要货顺序审批",
    "documentType": "transfer",
    "transferType": "warehouse",
    "scopeType": "all",
    "approvalMode": "all_nodes",
    "nodes": []
  },
  "transferSample": {
    "transferType": "warehouse",
    "fromDeptId": 104,
    "toDeptId": 108,
    "totalQuantity": 5
  }
}
```

Response shape:

```json
{
  "matched": true,
  "valid": false,
  "ruleId": 3,
  "ruleName": "门店要货顺序审批",
  "nodes": [
    {
      "nodeOrder": 2,
      "nodeName": "第 2 级审批",
      "nodeRole": "post",
      "postCode": "yyjl",
      "postName": "运营经理",
      "approvalScopeDeptId": 108,
      "candidateDeptIds": [102, 100],
      "candidates": [],
      "status": "missing_candidate",
      "message": "目标门店上级链长沙分公司、ERP科技下没有可审批的运营经理，或审批人未绑定市场部门"
    }
  ]
}
```

The endpoint should use the same candidate resolution logic as transfer submission. It should not duplicate business rules in the frontend.

## Validation Statuses

The frontend should handle these statuses:

- `ok`: candidates exist and the node can create approval tasks
- `skipped`: the node is intentionally skipped by backend logic
- `missing_post`: configured post code does not exist or is disabled
- `missing_candidate`: no enabled user exists for the resolved departments and post
- `missing_scope`: users exist but are not bound to the approval scope department
- `self_approval_only`: all candidates are the submitter and self-approval is disabled
- `unmatched_rule`: sample transfer does not match the rule

Messages should name the real departments and posts involved. A generic "no effective approver" message is not enough on the configuration page.

## Data Flow

Simplified mode:

1. User selects a scenario.
2. The page creates a compatible rule draft from that scenario.
3. User edits the approval chain.
4. The page calls the validation endpoint with a sample transfer context.
5. The page shows candidates and blocking reasons next to each approval level.
6. Save is blocked if validation fails.

Advanced mode:

1. Administrator edits low-level fields in grouped sections.
2. The page calls the same validation endpoint.
3. Warnings are shown inline and in the validation summary.
4. Administrator may save with validation warnings after confirmation, but the warning state remains visible.

Transfer submission:

1. Backend still calls the real approval instance creation logic.
2. If no effective candidates exist, submission is blocked.
3. The error message should include the first actionable missing node when possible.

## Resolving the Current `ll` Case

The current data has:

- Submitter: `ll`, user `104`
- Target store: `108 市场部门`
- Transfer: `TF202606140001`, warehouse request from `104 仓库` to `108 市场部门`
- Rule: `门店要货顺序审批`
- Nodes: `dz -> yyjl -> yyzj`
- Target store ancestors: `102 长沙分公司`, `100 ERP科技`

Validation should show that no effective `yyjl` or `yyzj` approver exists on the target store ancestor chain with the needed store scope. It should guide the administrator toward one of these fixes:

- Add valid `yyjl` and `yyzj` users under the expected ancestor departments and bind them to `108 市场部门`.
- Change the business template if the intended business rule is to approve inside the target store department instead of its ancestor chain.
- Change the approval chain to a post and search scope that actually resolves an approver, such as a target-store department post if that scope is supported by the implementation.

## Error Handling

Configuration validation failures should be shown as actionable messages in the form.

API failures should show a normal request failure message and keep the last successful validation result visible, marked as stale.

When a saved rule later fails during transfer submission, the backend should prefer a specific message such as `第 2 级审批 运营经理没有有效审批人` over the generic `审批规则未配置有效审批人`.

## Compatibility

Do not change the existing approval tables in this iteration.

Existing rules remain loadable in advanced mode. Simplified mode may show a compatibility banner when a legacy rule uses unsupported combinations.

The validation endpoint should be additive. Existing submit, approve, list, and preview APIs continue to work.

## Testing

Backend tests:

- Validate a rule that matches and resolves candidates successfully.
- Validate the current `ll` scenario and return a missing-candidate result.
- Validate skipped `post + dz` behavior.
- Validate missing store-scope bindings.
- Validate self-approval-only blocking.
- Ensure transfer submission still blocks when validation would fail.

Frontend tests:

- Simplified mode builds a valid rule payload for store warehouse requests.
- Simplified mode blocks save when validation returns missing candidates.
- Advanced mode displays grouped sections and validation warnings.
- Advanced mode requires confirmation before saving a risky rule.
- Existing advanced/legacy rules remain editable.

## Non-Goals

This design does not migrate transfer approval to Flowable.

This design does not change approval task snapshot tables.

This design does not add direct specified-approver persistence. That requires a separate data-model extension because the current approval node schema stores post-based nodes, not user-based nodes.

This design does not introduce tenant-level approval configuration.

This design does not remove the existing advanced rule engine.

## Acceptance Criteria

- A normal user can configure a store warehouse request approval chain without seeing backend field names.
- An administrator can still access all advanced rule settings.
- Both modes can show the actual approvers a rule will produce for a sample transfer.
- The page explains the exact reason when a node has no effective approver.
- The current `ll` scenario is diagnosable from the configuration page before submit.
- No existing transfer approval records are invalidated.
