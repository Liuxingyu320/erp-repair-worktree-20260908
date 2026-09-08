# Mobile Inventory Backend Responsive Multi-Agent Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development for implementation tasks and dispatching-parallel-agents only for independent research or file-isolated tasks. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade `/mobile/inventory` from a static mobile preview into a responsive, backend-connected phone web page for 进销存管理 and仓库管理.

**Architecture:** Keep the current tea-room liquid-glass UI, but introduce a service/mapper layer between the Vue page and existing backend APIs. Parallel agents may investigate APIs, responsive rules, auth/route behavior, and tests, but only one implementer should integrate `index.vue` at a time to avoid conflicts.

**Tech Stack:** Vue 2, Vue Router 3, Vuex/auth utilities, existing inventory API modules, SCSS, Node assert tests, Vue CLI build, Browser visual QA.

---

## File Ownership

- `erp-ui/src/views/mobile/inventory/index.vue`
  - Owner: Integration Agent only.
  - No parallel edits.
- `erp-ui/src/views/mobile/inventory/workbenchData.js`
  - Owner: Data/Mapper Agent.
- `erp-ui/src/views/mobile/inventory/workbenchMapper.js`
  - Owner: Data/Mapper Agent.
- `erp-ui/src/views/mobile/inventory/workbenchService.js`
  - Owner: API Agent.
- `erp-ui/src/views/mobile/inventory/responsive-notes.md`
  - Owner: Responsive Agent.
- `erp-ui/src/permission.js`
  - Owner: Auth/Route Agent.
- `erp-ui/src/router/index.js`
  - Owner: Auth/Route Agent.
- `erp-ui/test/mobileInventoryWorkbench.test.js`
  - Owner: QA/Test Agent and Data/Mapper Agent, coordinated by Integration Agent before final.

## Agent Split

### Agent 1: API Contract Agent

**Scope:** Existing backend-facing inventory APIs.

**Files to read:**
- `erp-ui/src/api/inventory/stock.js`
- `erp-ui/src/api/inventory/sales.js`
- `erp-ui/src/api/inventory/purchase.js`
- `erp-ui/src/api/inventory/deliveryNotice.js`
- `erp-ui/src/api/inventory/stockCheck.js`
- Existing desktop pages under `erp-ui/src/views/inventory/*/index.vue`

**Deliverable:**
- Create `erp-ui/src/views/mobile/inventory/workbenchService.js`.
- Export `fetchMobileInventoryWorkbench(selectedDeptId)` that calls existing APIs:
  - `getStockSummary`
  - `listStock`
  - `listDeliveryNotice`
  - `listPurchase`
  - `listStockCheck`
- Return raw grouped results without UI formatting.
- Do not edit `index.vue`.

**Acceptance:**
- Service has no Element UI dependency.
- Service accepts a selected shop/warehouse context.
- Service tolerates empty API result shapes by returning empty arrays or objects.

### Agent 2: Data Mapper Agent

**Scope:** Convert backend response shapes into mobile workbench display model.

**Files to own:**
- `erp-ui/src/views/mobile/inventory/workbenchData.js`
- `erp-ui/src/views/mobile/inventory/workbenchMapper.js`
- `erp-ui/test/mobileInventoryWorkbench.test.js`

**Deliverable:**
- Create `mapWorkbenchResponse(raw, fallbackData)`.
- Preserve current display contract:
  - metrics: `今日销售 / 待入库 / 低库存 / 待发货`
  - priorities: `销售待发货 / 采购待入库 / 低库存预警 / 盘点差异`
  - quick actions and bottom nav unchanged.
- Add Node assert tests for:
  - normal response mapping
  - empty response fallback
  - missing fields never render `undefined`

**Acceptance:**
- Test can run with `node test/mobileInventoryWorkbench.test.js`.
- Mapper has no Vue dependency.
- Mapper keeps demo fallback for development-only preview.

### Agent 3: Responsive UI Agent

**Scope:** Responsive design audit and CSS recommendations, not final Vue integration.

**Files to create:**
- `erp-ui/src/views/mobile/inventory/responsive-notes.md`

**Deliverable:**
- Audit current `index.vue` screenshot and CSS.
- Provide exact CSS rules for:
  - `360px`, `375px`, `390px`, `430px`, and desktop centered preview.
  - `100dvh`, `safe-area-inset-bottom`, bottom nav spacing.
  - preventing metric label/value overflow.
  - preventing priority row truncation from becoming unreadable.
- Do not edit `index.vue`.

**Acceptance:**
- Notes include concrete selectors and values.
- Notes identify any current risk in the final screenshot.

### Agent 4: Auth and Route Agent

**Scope:** Decide preview/public vs authenticated real data behavior.

**Files to modify:**
- `erp-ui/src/permission.js`
- `erp-ui/src/router/index.js`

**Deliverable:**
- Propose and implement route behavior:
  - `/mobile/inventory` should require login when real API mode is enabled.
  - optional `?demo=1` can keep public demo preview if needed.
- Ensure route title remains `进销存工作台`.
- Ensure no desktop `Layout` wrapper is used.

**Acceptance:**
- Auth behavior is explicit in comments or route meta.
- It does not break `/login`, `/register`, `/select-shop`.

### Agent 5: Integration Agent

**Scope:** Single owner for `index.vue` integration.

**Files to modify:**
- `erp-ui/src/views/mobile/inventory/index.vue`

**Inputs:**
- Agent 1 service.
- Agent 2 mapper.
- Agent 3 responsive notes.
- Agent 4 route/auth decision.

**Deliverable:**
- Add page state:
  - `loading`
  - `error`
  - `isDemo`
  - `data`
- On created/mounted:
  - detect selected shop/warehouse with existing shop context helpers.
  - load backend data via service.
  - map data via mapper.
  - show demo fallback only when allowed.
- Add retry button in error state.
- Keep tea-room liquid-glass visual style.
- Preserve current static labels and layout hierarchy.

**Acceptance:**
- No hardcoded backend result access in template.
- No UI crash when backend is unavailable.
- Existing static screenshot remains visually close.

### Agent 6: QA and Browser Agent

**Scope:** Verification only after integration.

**Files to read/write:**
- Read all changed files.
- Update `docs/previews/mobile-inventory-workbench-implementation.png` if visual check passes.

**Deliverable:**
- Run:
  - `node test/mobileInventoryWorkbench.test.js`
  - `node test/purchaseActionRules.test.js`
  - `npm run build:prod`
- Browser check:
  - `375x812`
  - `390x844`
  - `430x932`
- Verify:
  - no horizontal overflow
  - loading state visible
  - error state visible when backend unavailable
  - real-data request path attempted when logged in
  - demo mode still available if retained

**Acceptance:**
- Report exact commands, exit status, and visual findings.
- No completion claim without fresh verification evidence.

## Execution Order

- [ ] Coordinator locks shared contract:
  - `fetchMobileInventoryWorkbench(selectedDeptId)`
  - `mapWorkbenchResponse(raw, fallbackData)`
  - `index.vue` consumes only mapped `data`.
- [ ] Parallel dispatch:
  - Agent 1 API Contract Agent
  - Agent 2 Data Mapper Agent
  - Agent 3 Responsive UI Agent
  - Agent 4 Auth and Route Agent
- [ ] Coordinator reviews file conflicts and merges notes.
- [ ] Dispatch Agent 5 Integration Agent.
- [ ] Coordinator runs quick sanity test.
- [ ] Dispatch Agent 6 QA and Browser Agent.
- [ ] Coordinator fixes final review issues and reports.

## Risk Controls

- Do not let multiple agents edit `index.vue`.
- Do not add new backend endpoints in this pass.
- Do not remove current tea-room visual assets.
- Do not make `/mobile/inventory` silently public with real user data.
- Keep demo fallback explicit, preferably via `?demo=1`.

## Suggested Dispatch Prompts

### Agent 1 Prompt

```text
You are Agent 1: API Contract Agent. Work only on backend-facing service wiring for /mobile/inventory.

Read:
- erp-ui/src/api/inventory/stock.js
- erp-ui/src/api/inventory/sales.js
- erp-ui/src/api/inventory/purchase.js
- erp-ui/src/api/inventory/deliveryNotice.js
- erp-ui/src/api/inventory/stockCheck.js

Create erp-ui/src/views/mobile/inventory/workbenchService.js.
Export fetchMobileInventoryWorkbench(selectedDeptId).
Use existing APIs only. Return grouped raw results:
{ stockSummary, lowStockRows, deliveryRows, purchaseRows, stockCheckRows }.
Normalize missing response rows to [] and missing summary to {}.
Do not edit index.vue.
Return a summary of APIs used and assumptions about query params.
```

### Agent 2 Prompt

```text
You are Agent 2: Data Mapper Agent. Work on display-data mapping for /mobile/inventory.

Modify:
- erp-ui/src/views/mobile/inventory/workbenchData.js
Create:
- erp-ui/src/views/mobile/inventory/workbenchMapper.js
Modify tests:
- erp-ui/test/mobileInventoryWorkbench.test.js

Create mapWorkbenchResponse(raw, fallbackData). It must return the same display shape as workbenchData.
Add tests for normal raw data, empty raw data, and missing fields.
Do not edit index.vue.
Run node test/mobileInventoryWorkbench.test.js and report results.
```

### Agent 3 Prompt

```text
You are Agent 3: Responsive UI Agent. Do not edit Vue or JS files.

Read erp-ui/src/views/mobile/inventory/index.vue and inspect the current preview screenshot at docs/previews/mobile-inventory-workbench-implementation.png.
Create erp-ui/src/views/mobile/inventory/responsive-notes.md with exact CSS recommendations for 360, 375, 390, 430 widths and desktop centered preview.
Focus on safe area, bottom nav, metrics, priority rows, text overflow, and no horizontal scroll.
```

### Agent 4 Prompt

```text
You are Agent 4: Auth and Route Agent.

Read:
- erp-ui/src/permission.js
- erp-ui/src/router/index.js
- erp-ui/src/permission.js auth flow

Implement route/auth behavior for /mobile/inventory:
- real backend data should require login
- optional demo preview can be public via ?demo=1 if feasible
- keep route hidden and outside desktop Layout

Do not edit index.vue.
Return the exact auth behavior after changes.
```

### Agent 5 Prompt

```text
You are Agent 5: Integration Agent. You are the only agent allowed to edit erp-ui/src/views/mobile/inventory/index.vue.

Integrate:
- fetchMobileInventoryWorkbench(selectedDeptId)
- mapWorkbenchResponse(raw, fallbackData)
- responsive notes from responsive-notes.md
- route/auth behavior decided by Agent 4

Add loading, error, retry, demo/real mode handling.
Keep the tea-room liquid-glass visual style and current IA.
Run node test/mobileInventoryWorkbench.test.js before returning.
```

### Agent 6 Prompt

```text
You are Agent 6: QA and Browser Agent.

Run:
- node test/mobileInventoryWorkbench.test.js
- node test/purchaseActionRules.test.js
- npm run build:prod

Then use Browser to inspect /mobile/inventory at 375x812, 390x844, 430x932.
Verify no horizontal overflow, visible loading/error handling, and correct labels.
Save final screenshot to docs/previews/mobile-inventory-workbench-implementation.png.
Return exact command results and visual findings.
```
