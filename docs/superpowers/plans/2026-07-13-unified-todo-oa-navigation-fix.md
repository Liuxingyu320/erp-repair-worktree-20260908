# Unified Todo OA Navigation Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified OA purchase todos reliably open the focused OA approval processor and refresh unified todo state after approval.

**Architecture:** Register a menu-independent hidden processing route and point the existing todo route contract at it. Keep discovery in `/workbench/todo`, processing in the OA component, and connect them through the existing standard query contract.

**Tech Stack:** Vue 2, Vue Router 3, Vuex, Node.js assertion tests, Vue CLI.

---

### Task 1: Processing route contract

**Files:**
- Modify: `erp-ui/test/unifiedTodoRouteResolver.test.js`
- Modify: `erp-ui/test/unifiedTodoDesktop.test.js`
- Modify: `erp-ui/src/router/index.js`
- Modify: `erp-ui/src/utils/todoRouteResolver.js`

- [ ] Change resolver and desktop contract assertions to require `/oa/todo/process` and verify the route is a hidden constant route.
- [ ] Run `node test/unifiedTodoRouteResolver.test.js && node test/unifiedTodoDesktop.test.js` from `erp-ui`; expect the new assertions to fail against `/oa/todo`.
- [ ] Add a hidden constant `/oa/todo/process` route using `@/views/oa/todo`, and update the OA route matrix candidate.
- [ ] Re-run both tests; expect PASS.

### Task 2: Approval completion contract

**Files:**
- Modify: `erp-ui/test/unifiedTodoDesktop.test.js`
- Modify: `erp-ui/src/views/oa/todo/index.vue`

- [ ] Add a component-level test that a successful `doAudit` dispatches `todo/invalidateAfterMutation` once and reloads the OA list once.
- [ ] Run `node test/unifiedTodoDesktop.test.js`; expect failure because the store is not notified.
- [ ] Update the approval success chain to refresh unified state and the business list with `Promise.allSettled`, without turning a completed approval into an error when refresh fails.
- [ ] Re-run the desktop test; expect PASS.

### Task 3: Single failure report regression

**Files:**
- Modify: `erp-ui/test/unifiedTodoNavigator.test.js`

- [ ] Add explicit counters asserting one `showError` call and one summary refresh for one missing-route navigation.
- [ ] Temporarily run the test against a deliberately duplicated call to demonstrate the assertion fails, then restore the real single call.
- [ ] Run `node test/unifiedTodoNavigator.test.js`; expect PASS with one report.

### Task 4: Verification and commits

**Files:**
- Verify all files above and documentation.

- [ ] Run focused tests: `node test/unifiedTodoRouteResolver.test.js && node test/unifiedTodoNavigator.test.js && node test/unifiedTodoDesktop.test.js`.
- [ ] Run `npm test` from `erp-ui`; expect zero failures.
- [ ] Run `npm run build:prod` from `erp-ui`; expect exit code 0.
- [ ] Commit only the planned documentation, tests, router, resolver, and OA page to `7月12号`.
- [ ] Synchronize the complete unified-todo architecture and fix to `codex/local-source-integration-20260712`, then repeat focused tests and production build there.

### Task 5: Complete the shared business-focus contract

**Files:**
- Modify: `erp-ui/src/utils/todoBusinessFocus.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/oa/fixedAsset/repair/index.vue`
- Modify: `erp-ui/test/unifiedTodoBusinessFocus.test.js`

- [ ] Align `INV_TRANSFER_RETURNED` with the real `editTransfer` action exposed by the generic transfer page.
- [ ] Add the generic mobile focus consumer: exact-row selection, transient query consumption, fresh detail read, action lookup, and handled-state refresh.
- [ ] Validate a transfer shipment only from the fresh detail and never fall back after a failed detail load.
- [ ] Connect fixed-asset confirmation to the shared desktop focus mixin and re-read exact state before mutation.
- [ ] Run the complete unified-todo test group and production build.
