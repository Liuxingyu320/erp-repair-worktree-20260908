# Mobile Inventory Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a phone-browser `进销存工作台` page with Apple-style liquid-glass visuals covering sales, purchase, inventory, and warehouse management.

**Architecture:** Add a self-contained Vue 2 route under `/mobile/inventory`, backed by a small data module for render content and test coverage. The page bypasses the desktop `Layout` and login guard for preview use, so it does not affect existing desktop ERP pages.

**Tech Stack:** Vue 2, Vue Router 3, SCSS, Node assert tests, Vue CLI build.

---

## File Structure

- Create: `erp-ui/src/views/mobile/inventory/workbenchData.js`
  - Owns static display data for metrics, priority tasks, quick actions, and bottom navigation.
- Create: `erp-ui/src/views/mobile/inventory/index.vue`
  - Renders the accepted liquid-glass mobile web page.
- Create: `erp-ui/test/mobileInventoryWorkbench.test.js`
  - Verifies business coverage and route preview access rules.
- Modify: `erp-ui/src/router/index.js`
  - Adds hidden public route `/mobile/inventory`.
- Modify: `erp-ui/src/permission.js`
  - Adds `/mobile/inventory` to the whitelist.

## Tasks

### Task 1: Data Contract Test

- [ ] Create `erp-ui/test/mobileInventoryWorkbench.test.js` with assertions for:
  - `workbenchData.businessDomains` includes `sales`, `purchase`, `inventory`, and `warehouse`.
  - Hero metrics contain `今日销售`, `待入库`, `低库存`, and `待发货`.
  - Quick actions contain `销售开单`, `采购入库`, `查库存`, and `盘点`.
  - `PUBLIC_MOBILE_INVENTORY_PATH` equals `/mobile/inventory`.
- [ ] Run `node test/mobileInventoryWorkbench.test.js` from `erp-ui`.
  - Expected before implementation: fails because `workbenchData.js` does not exist.

### Task 2: Data Module

- [ ] Create `erp-ui/src/views/mobile/inventory/workbenchData.js`.
- [ ] Export:
  - `PUBLIC_MOBILE_INVENTORY_PATH`
  - `workbenchData`
- [ ] Run `node test/mobileInventoryWorkbench.test.js`.
  - Expected after implementation: passes.

### Task 3: Vue Page

- [ ] Create `erp-ui/src/views/mobile/inventory/index.vue`.
- [ ] Import `workbenchData`.
- [ ] Render:
  - Browser-like status/address bar.
  - `进销存工作台` title and `总仓 A区` selector.
  - Hero glass card with `今日经营` and four metrics.
  - Priority list with four rows.
  - Quick action glass grid.
  - Bottom navigation.
- [ ] Use scoped SCSS for the liquid-glass visual system.

### Task 4: Route and Whitelist

- [ ] Add hidden route `/mobile/inventory` in `erp-ui/src/router/index.js`.
- [ ] Add `/mobile/inventory` to `whiteList` in `erp-ui/src/permission.js`.
- [ ] Run the data test again.

### Task 5: Build and Browser Verification

- [ ] Run `npm run build:prod` from `erp-ui`.
- [ ] Start the dev server if the build passes.
- [ ] Open `/mobile/inventory` in Browser at a phone-sized viewport.
- [ ] Compare against `docs/previews/inventory-mobile-liquid-glass-concept.png`.
- [ ] Fix visible mismatches in typography, spacing, card glass effect, and business labels.
