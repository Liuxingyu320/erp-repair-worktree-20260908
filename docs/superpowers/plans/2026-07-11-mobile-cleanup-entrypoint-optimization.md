# Mobile Cleanup and Entrypoint Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove stale non-native Finder-style copies, prevent them from returning, and reduce the initial web entrypoint without changing mobile business behavior or native deployment files.

**Architecture:** Extend the existing frontend test gate to scan only web source/test/root lockfile locations, then delete numbered backup copies after comparing them with the canonical newer files. Convert the desktop `Layout` route component from a synchronous import to Vue Router's supported async component factory so login and mobile entry routes do not download desktop layout code up front. Finish with automated and browser verification; native Android/iOS output remains out of scope.

**Tech Stack:** Vue 2.6, Vue Router 3, Vue CLI/Webpack 4, Node assertion tests, in-app browser.

---

### Task 1: Remove and prevent non-native numbered copies

**Files:**
- Modify: `erp-ui/test/frontendTestGate.test.js`
- Delete: `erp-ui/package-lock 2.json`
- Delete: `erp-ui/src/api/oa/laborContract 2.js`
- Delete: `erp-ui/src/views/mobile/contract/index 2.vue`
- Delete: `erp-ui/src/views/mobile/feature/featureMapper 2.js`
- Delete: `erp-ui/src/views/mobile/feature/index 2.vue`
- Delete: `erp-ui/src/views/mobile/feature/index 3.vue`
- Delete: `erp-ui/src/views/mobile/mobileRouteDefinitions 2.js`
- Delete: `erp-ui/src/views/mobile/mobileRouteDefinitions 3.js`
- Delete: `erp-ui/src/views/mobile/mobileRouteDefinitions 4.js`
- Delete: `erp-ui/src/views/oa/laborContract/index 2.vue`

- [x] Add a recursive test-gate helper that scans `src/` and `test/`, plus root-level files, for basenames matching `/ [2-9]\\.[^/]+$/` while excluding `android/`, `ios/`, `node_modules/`, and `dist/`.
- [x] Run `node test/frontendTestGate.test.js` and verify it fails while listing the ten current non-native copies.
- [x] Confirm every canonical file is newer or identical and delete only the numbered backup copies.
- [x] Run `node test/frontendTestGate.test.js` and verify it passes.

### Task 2: Lazy-load the desktop layout entry

**Files:**
- Modify: `erp-ui/test/productionAssetBudget.test.js`
- Modify: `erp-ui/src/router/index.js`

- [x] Add an assertion requiring `Layout` to use a named dynamic import chunk instead of a synchronous `import Layout from '@/layout'`.
- [x] Run `node test/productionAssetBudget.test.js` and verify it fails on the current synchronous import.
- [x] Replace the synchronous import with `const Layout = () => import(/* webpackChunkName: "chunk-desktop-layout" */ '@/layout')`.
- [x] Run the asset-budget and route-loading tests and verify they pass.
- [x] Run `npm run build:prod`, compare the generated initial `app` asset with the 326,462-byte baseline, and confirm the entrypoint-size warning is removed or report the measured remaining warning honestly.

### Task 3: Regression verification

**Files:**
- Verify: `erp-ui/test/*.test.js`
- Verify: `erp-ui/dist/`

- [x] Run every non-native frontend test and require zero failures.
- [x] Run targeted mobile overlay, draft, authentication, and progressive-redesign tests.
- [x] Start the local web app and inspect mobile-width navigation, authentication redirect, and available signed-in mobile pages in the in-app browser.
- [x] Run `git diff --check` on modified tracked files and confirm no non-native numbered copies remain.

No commit is created in this shared dirty worktree; unrelated user changes must remain untouched.
