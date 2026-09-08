# Mobile Remediation R1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 发布一个可独立回滚的 R1 版本，消除手机端跨路由卡死和状态串页，并修复公告 XSS、前端私钥回流和敏感请求负载持久化风险。

**Architecture:** 弹层节点保留在 Vue 组件树中，overlay 工具只管理 body 滚动锁；移动路由使用请求版本令牌隔离异步结果，并以移动路径 key 隔离复用组件。公告在 Java 服务端与 Vue 展示入口分别使用维护中的专用净化器；重复提交状态只保留不可逆内存指纹。

**Tech Stack:** Vue 2.6、Vue Router 3、Node assert tests、Axios、DOMPurify 3.4.11、Java 17、Spring Boot、OWASP Java HTML Sanitizer 20260313.1、JUnit 5、Mockito。

---

## Baseline

- Branch: `codex/mobile-remediation`
- Worktree: `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/mobile-remediation`
- `npm ci`: succeeds; Node 25.8.0 produces an engine warning because the project declares Node `<25`.
- `npm test`: 80 files run, 2 baseline failures.
  - `desktopAuditFollowupFixes.test.js`: clean worktree intentionally lacks untracked `erp-modules/erp-file/src/main/resources/application-local.yml`.
  - `mobileAppShell.test.js`: clean worktree intentionally lacks generated/untracked `erp-ui/android/`.
- Do not fabricate or commit either local artifact. R1 uses focused tests, build verification, and a final full-suite run that reports these known baseline failures separately.

## File Map

### Mobile route stability

- Modify `erp-ui/src/views/mobile/feature/components/mobileOverlayStack.js`: body lock only; no Vue DOM reparenting; add idempotent full release.
- Modify `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`: use lock-only overlay API.
- Modify `erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue`: use lock-only overlay API.
- Modify `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`: use lock-only overlay API.
- Modify `erp-ui/src/views/mobile/feature/components/MobileConfirmDialog.vue`: use lock-only overlay API.
- Modify `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`: use lock-only overlay API.
- Create `erp-ui/src/views/mobile/feature/mobileRouteLoadGuard.js`: route request generation tokens.
- Modify `erp-ui/src/views/mobile/feature/index.vue`: atomically reset route state, release overlays, ignore stale responses.
- Modify `erp-ui/src/App.vue`: key only mobile route views by normalized path.
- Modify `erp-ui/test/mobileOverlayStack.test.js`: prove nodes remain under their Vue parent.
- Create `erp-ui/test/mobileRouteLoadGuard.test.js`: prove stale route responses are rejected.
- Modify `erp-ui/test/mobileFeatureComponentSplit.test.js`: prevent manual body portal regression.

### Notice XSS

- Modify `pom.xml`: manage OWASP sanitizer version `20260313.1`.
- Modify `erp-modules/erp-system/pom.xml`: add sanitizer dependency.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/support/NoticeHtmlSanitizer.java`: server allowlist policy.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java`: sanitize writes and historical reads.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeReadServiceImpl.java`: sanitize the top-notice feed used by HeaderNotice.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeServiceImplTest.java`: sanitizer and service boundary tests.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeReadServiceImplTest.java`: top-notice feed sanitization test.
- Modify `erp-ui/package.json` and `erp-ui/package-lock.json`: add exact DOMPurify version.
- Create `erp-ui/src/utils/sanitizeNoticeHtml.js`: frontend allowlist and text fallback.
- Modify `erp-ui/src/layout/components/HeaderNotice/DetailView.vue`: render only computed sanitized content.
- Create `erp-ui/test/noticeHtmlSecurity.test.js`: verify the frontend sink and dependency contract.

### Frontend secret gate

- Create `erp-ui/scripts/scan-frontend-secrets.cjs`: recursive path/rule scanner that never logs secret values.
- Create `erp-ui/test/frontendSecretScanner.test.js`: temporary-fixture behavior tests.
- Modify `erp-ui/package.json`: run scanner before production builds.
- Modify `erp-ui/test/authSecurityHardening.test.js`: assert the production build gate remains wired.

### Request deduplication

- Create `erp-ui/src/utils/requestDeduplicator.js`: in-memory hashed fingerprint window.
- Create `erp-ui/test/requestDeduplicator.test.js`: duplicate, expiry, sensitive endpoint and storage tests.
- Modify `erp-ui/src/utils/request.js`: remove `sessionStorage` payload caching and delegate to the new module.
- Modify `erp-ui/src/api/login.js`: explicitly disable generic deduplication for unlock.

## Task 1: Keep Mobile Overlays Inside the Vue Tree

**Files:**
- Modify: `erp-ui/test/mobileOverlayStack.test.js`
- Modify: `erp-ui/test/mobileFeatureComponentSplit.test.js`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileOverlayStack.js`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileConfirmDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`

- [x] **Step 1: Replace the portal expectation with a failing ownership test**

In `mobileOverlayStack.test.js`, give each fake overlay a Vue-owned parent, make `body.appendChild` throw, and call the desired lock-only API:

```js
const vueParent = { name: "vue-parent" }
const detailMask = createElement("detail")
detailMask.parentNode = vueParent

body.appendChild = () => {
  throw new Error("Vue-owned overlays must not be reparented")
}

mountMobileOverlay("mobile-detail-sheet-open")

assert.strictEqual(detailMask.parentNode, vueParent)
assert.ok(body.classList.contains("mobile-overlay-open"))
```

Update `mobileFeatureComponentSplit.test.js` to require `!overlayStackSource.includes("appendChild")` and lock calls such as `mountMobileOverlay("mobile-form-sheet-open")`.

- [x] **Step 2: Run the tests and verify RED**

Run:

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileOverlayStack.test.js test/mobileFeatureComponentSplit.test.js
```

Expected: FAIL because the current helper expects an element and still calls `document.body.appendChild`.

- [x] **Step 3: Implement the lock-only overlay API**

Change the public API to accept only a class and add full cleanup:

```js
function mountMobileOverlay(overlayClass) {
  lockMobileBody(overlayClass)
}

function releaseAllMobileOverlays() {
  const body = getBody()
  if (!body) return
  const wasLocked = activeOverlayClasses.length > 0 || body.classList.contains(BODY_LOCK_CLASS)
  activeOverlayClasses.slice().forEach(name => removeBodyClass(body, name))
  activeOverlayClasses = []
  removeBodyClass(body, BODY_LOCK_CLASS)
  if (wasLocked) {
    restoreBodyStyles(body)
    restoreScrollPosition()
  }
}
```

Calling full release when no overlay was active must be a no-op: it must not invoke `window.scrollTo` or alter the current page position.

Export `releaseAllMobileOverlays`. In each Vue component, stop passing `$refs.*Mask`; retain the lifecycle watchers and call only the relevant class:

```js
lockFormSheetBody() {
  mountMobileOverlay("mobile-form-sheet-open")
}
```

Rename the corresponding `*ToBody` methods to `lock*Body` so future code does not imply DOM movement.

- [x] **Step 4: Run focused tests and verify GREEN**

Run the same command. Expected: `2 run, 0 failed`.

- [x] **Step 5: Commit the overlay fix**

```bash
git add erp-ui/src/views/mobile/feature/components erp-ui/test/mobileOverlayStack.test.js erp-ui/test/mobileFeatureComponentSplit.test.js
git commit -m "fix: keep mobile overlays in vue tree"
```

## Task 2: Isolate Mobile Route State and Stale Requests

**Files:**
- Create: `erp-ui/test/mobileRouteLoadGuard.test.js`
- Create: `erp-ui/src/views/mobile/feature/mobileRouteLoadGuard.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/App.vue`
- Modify: `erp-ui/test/mobileAndroidAuditRegression.test.js`

- [x] **Step 1: Write the failing request-generation test**

```js
const assert = require("assert")
const { createMobileRouteLoadGuard } = require("../src/views/mobile/feature/mobileRouteLoadGuard")

const guard = createMobileRouteLoadGuard()
const sales = guard.begin("sales")
const stock = guard.begin("stock")

assert.strictEqual(guard.isCurrent(sales, "stock"), false)
assert.strictEqual(guard.isCurrent(stock, "stock"), true)
guard.invalidate()
assert.strictEqual(guard.isCurrent(stock, "stock"), false)
```

Add static expectations to `mobileAndroidAuditRegression.test.js` for `releaseAllMobileOverlays`, `routeLoadGuard.begin`, `routeLoadGuard.isCurrent`, and a mobile-only router-view key.

- [x] **Step 2: Run tests and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileRouteLoadGuard.test.js test/mobileAndroidAuditRegression.test.js
```

Expected: FAIL because the guard module and route integration do not exist.

- [x] **Step 3: Create the minimal route guard**

```js
function createMobileRouteLoadGuard() {
  let version = 0
  return {
    begin(featureKey) {
      version += 1
      return { version, featureKey: String(featureKey || "") }
    },
    invalidate() {
      version += 1
    },
    isCurrent(token, featureKey) {
      return !!token && token.version === version && token.featureKey === String(featureKey || "")
    }
  }
}

module.exports = { createMobileRouteLoadGuard }
```

- [x] **Step 4: Integrate atomic route reset and guarded writes**

Create one guard per component instance in `data()`. Watch `$route.fullPath` once rather than separate path/query watchers. At the beginning of `applyRouteFeature()`:

```js
this.routeLoadGuard.invalidate()
releaseAllMobileOverlays()
this.loading = false
this.loadingMore = false
```

In `loadData()`, capture a token before the request and gate every asynchronous state write:

```js
const loadToken = this.routeLoadGuard.begin(this.featureKey)
const isCurrentLoad = () => this.routeLoadGuard.isCurrent(loadToken, this.featureKey)

withMobileRetry(() => fetchMobileFeatureData(/* existing options */))
  .then(result => {
    if (!isCurrentLoad()) return
    // existing mapping and state writes
  })
  .catch(error => {
    if (!isCurrentLoad()) return
    // existing error state writes
  })
  .finally(() => {
    if (!isCurrentLoad()) return
    this.loading = false
    this.loadingMore = false
  })
```

Also invalidate and release overlays in `beforeDestroy`.

In `App.vue`, add a computed key that only remounts mobile paths:

```vue
<router-view :key="routeViewKey" />
```

```js
computed: {
  routeViewKey() {
    const path = this.$route && this.$route.path ? this.$route.path : ""
    return path.indexOf("/mobile/") === 0 ? path : "desktop-router-view"
  }
}
```

- [x] **Step 5: Run route tests and verify GREEN**

Run the Step 2 command. Expected: `2 run, 0 failed`.

- [x] **Step 6: Commit the route isolation fix**

```bash
git add erp-ui/src/App.vue erp-ui/src/views/mobile/feature/index.vue erp-ui/src/views/mobile/feature/mobileRouteLoadGuard.js erp-ui/test/mobileRouteLoadGuard.test.js erp-ui/test/mobileAndroidAuditRegression.test.js
git commit -m "fix: isolate mobile route state"
```

## Task 3: Sanitize Notice HTML on the Server

**Files:**
- Modify: `pom.xml`
- Modify: `erp-modules/erp-system/pom.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/support/NoticeHtmlSanitizer.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeReadServiceImpl.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeServiceImplTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeReadServiceImplTest.java`

- [x] **Step 1: Add failing sanitizer and service-boundary tests**

Test that formatting survives while scripts, event handlers and dangerous URLs do not:

```java
@Test
void sanitizeShouldPreserveBusinessFormattingAndRemoveExecutableMarkup()
{
    String result = sanitizer.sanitize("<p><strong>安全</strong><img src=x onerror=alert(1)></p>"
            + "<a href=\"javascript:alert(2)\">链接</a><script>alert(3)</script>");

    assertThat(result).contains("<p><strong>安全</strong></p>", "链接");
    assertThat(result).doesNotContain("script", "onerror", "javascript:", "<img");
}
```

Mock `SysNoticeMapper` and assert `insertNotice`, `updateNotice`, `selectNoticeById`, and `selectNoticeList` expose only sanitized `noticeContent`. Mock `SysNoticeReadMapper` separately and assert `selectNoticeListWithReadStatus` also sanitizes the top-notice feed.

- [x] **Step 2: Run Maven test and verify RED**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysNoticeServiceImplTest,SysNoticeReadServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL to compile because `NoticeHtmlSanitizer` does not exist.

- [x] **Step 3: Add the maintained sanitizer dependency**

In the root POM add property `owasp.java.html.sanitizer.version=20260313.1` and dependency management for:

```xml
<dependency>
    <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
    <artifactId>owasp-java-html-sanitizer</artifactId>
    <version>${owasp.java.html.sanitizer.version}</version>
</dependency>
```

Add the same dependency without a version to `erp-modules/erp-system/pom.xml`.

- [x] **Step 4: Implement one server allowlist**

Create a Spring component with a static `PolicyFactory` built by `HtmlPolicyBuilder`. Allow only `p`, `br`, headings, `strong`, `b`, `em`, `i`, `u`, `s`, `ul`, `ol`, `li`, `blockquote`, `pre`, `code`, and `a`; allow only `http`, `https`, and `mailto` URLs plus `href`/`title` on links; require nofollow on links. Null input returns null.

Use constructor injection in `SysNoticeServiceImpl` and `SysNoticeReadServiceImpl`. Sanitize before insert/update and after select-by-id/list; sanitize `selectNoticeListWithReadStatus` as well so the header feed and historical rows are protected without a destructive migration.

- [x] **Step 5: Run Maven test and verify GREEN**

Run the Step 2 command. Expected: `SysNoticeServiceImplTest` passes with zero failures.

- [x] **Step 6: Commit the server XSS fix**

```bash
git add pom.xml erp-modules/erp-system/pom.xml erp-modules/erp-system/src/main/java/com/erp/system/service erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeServiceImplTest.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysNoticeReadServiceImplTest.java
git commit -m "fix: sanitize notice html on server"
```

## Task 4: Sanitize Notice HTML at the Vue Sink

**Files:**
- Create: `erp-ui/test/noticeHtmlSecurity.test.js`
- Modify: `erp-ui/package.json`
- Modify: `erp-ui/package-lock.json`
- Create: `erp-ui/src/utils/sanitizeNoticeHtml.js`
- Modify: `erp-ui/src/layout/components/HeaderNotice/DetailView.vue`

- [x] **Step 1: Write a failing source contract test**

Assert that the component imports `sanitizeNoticeHtml`, renders `sanitizedNoticeContent`, and no longer contains `v-html="detail.noticeContent"`. Assert `package.json` pins `dompurify` to `3.4.11`.

- [x] **Step 2: Run the test and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/noticeHtmlSecurity.test.js
```

Expected: FAIL because the sink still renders raw API HTML.

- [x] **Step 3: Install DOMPurify and add the wrapper**

```bash
npm install --save-exact dompurify@3.4.11
```

Create `sanitizeNoticeHtml.js` with an explicit HTML-only tag/attribute allowlist matching the server policy. Call `DOMPurify.sanitize`; when DOMPurify is unsupported or throws, return HTML-escaped text rather than the original markup.

- [x] **Step 4: Replace the raw sink**

Add a computed property:

```js
sanitizedNoticeContent() {
  const content = this.detail && this.detail.noticeContent
  return sanitizeNoticeHtml(content)
}
```

Render only `v-html="sanitizedNoticeContent"`.

- [x] **Step 5: Run the test and verify GREEN**

Run the Step 2 command. Expected: `1 run, 0 failed`.

- [x] **Step 6: Commit the frontend XSS fix**

```bash
git add erp-ui/package.json erp-ui/package-lock.json erp-ui/src/utils/sanitizeNoticeHtml.js erp-ui/src/layout/components/HeaderNotice/DetailView.vue erp-ui/test/noticeHtmlSecurity.test.js
git commit -m "fix: sanitize notice html in frontend"
```

## Task 5: Block Frontend Private Keys Before Build

**Files:**
- Create: `erp-ui/test/frontendSecretScanner.test.js`
- Create: `erp-ui/scripts/scan-frontend-secrets.cjs`
- Modify: `erp-ui/package.json`
- Modify: `erp-ui/test/authSecurityHardening.test.js`

- [x] **Step 1: Write failing scanner tests**

Use a temporary directory containing one safe file and files with `BEGIN PRIVATE KEY` and `setPrivateKey(`. Assert returned violations include only relative path and rule ID, never the matched secret value. Also assert the clean real `src/` tree has zero violations.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/frontendSecretScanner.test.js
```

Expected: FAIL because the scanner module does not exist.

- [x] **Step 3: Implement recursive scanning**

Export `findFrontendSecretViolations(rootDir)`. Scan text files below the supplied root, skip symlinks and files over 2 MiB, and report rule IDs for PEM private-key headers and private-key API calls. CLI mode scans `src/`, prints only `relative/path [RULE_ID]`, and exits 1 on any violation.

- [x] **Step 4: Wire the production build gate**

Add:

```json
"prebuild:prod": "node scripts/scan-frontend-secrets.cjs"
```

Extend `authSecurityHardening.test.js` to assert this exact script remains present.

- [x] **Step 5: Run tests and verify GREEN**

```bash
node scripts/run-node-tests.cjs test/frontendSecretScanner.test.js test/authSecurityHardening.test.js
```

Expected: `2 run, 0 failed`.

- [x] **Step 6: Commit the secret gate**

```bash
git add erp-ui/scripts/scan-frontend-secrets.cjs erp-ui/test/frontendSecretScanner.test.js erp-ui/test/authSecurityHardening.test.js erp-ui/package.json
git commit -m "test: block frontend private keys"
```

## Task 6: Replace Persistent Request Payloads with Memory Fingerprints

**Files:**
- Create: `erp-ui/test/requestDeduplicator.test.js`
- Create: `erp-ui/src/utils/requestDeduplicator.js`
- Modify: `erp-ui/src/utils/request.js`
- Modify: `erp-ui/src/api/login.js`

- [x] **Step 1: Write failing behavior tests**

Test these behaviors with an injected `now` value:

- first POST is accepted;
- identical POST inside 1000 ms is rejected;
- changed payload or expired window is accepted;
- `/auth/login`, `/auth/unlockscreen`, password update, reset-password and multipart requests bypass the generic cache;
- exposed test state contains only method/URL/hash/time and never password or serialized payload;
- `request.js` no longer imports cache or refers to `sessionObj`/`sessionStorage`.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/requestDeduplicator.test.js
```

Expected: FAIL because the deduplicator module does not exist and `request.js` persists raw payloads.

- [x] **Step 3: Implement a bounded memory window**

Use a module-local `Map`. Build a fingerprint from uppercase method, normalized URL and a deterministic one-way 32-bit hash of the serialized payload. Delete expired entries before each check and cap the map at 200 entries by removing the oldest. Provide `assertRequestNotDuplicate(config, now)` and test-only reset/snapshot helpers; snapshots expose hashes, never raw payloads.

On duplicate, throw `Error("数据正在处理，请勿重复提交")`. Respect existing `headers.repeatSubmit === false` and `headers.interval`.

- [x] **Step 4: Integrate the interceptor and mark unlock sensitive**

Remove `cache` from `request.js` and replace the entire `sessionObj` block with:

```js
if (config.method === "post" || config.method === "put") {
  assertRequestNotDuplicate(config)
}
```

Set `repeatSubmit: false` on `/auth/unlockscreen` so passwords never enter generic duplicate tracking.

- [x] **Step 5: Run tests and verify GREEN**

```bash
node scripts/run-node-tests.cjs test/requestDeduplicator.test.js test/mobileAndroidAuditRegression.test.js test/shopContextUx.test.js
```

Expected: `3 run, 0 failed`.

- [x] **Step 6: Commit the request protection**

```bash
git add erp-ui/src/utils/requestDeduplicator.js erp-ui/src/utils/request.js erp-ui/src/api/login.js erp-ui/test/requestDeduplicator.test.js
git commit -m "fix: keep duplicate request state in memory"
```

## Task 7: R1 Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-07-10-mobile-remediation-r1.md` only to check completed boxes and record exact verification output.

- [x] **Step 1: Run focused frontend regression**

```bash
cd erp-ui
node scripts/run-node-tests.cjs \
  test/mobileOverlayStack.test.js \
  test/mobileRouteLoadGuard.test.js \
  test/mobileFeatureComponentSplit.test.js \
  test/mobileAndroidAuditRegression.test.js \
  test/noticeHtmlSecurity.test.js \
  test/frontendSecretScanner.test.js \
  test/authSecurityHardening.test.js \
  test/requestDeduplicator.test.js \
  test/shopContextUx.test.js
```

Expected: all listed files pass with zero failures.

- [x] **Step 2: Run backend notice tests**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysNoticeServiceImplTest,SysNoticeControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: zero failures.

- [x] **Step 3: Run the production build**

```bash
cd erp-ui
npm run build:prod
```

Expected: secret scanner exits 0 and Vue production build succeeds.

- [x] **Step 4: Run the full frontend suite and classify only known baseline failures**

```bash
npm test
```

Expected in the clean worktree until local/native fixtures are provided: 84 run, 2 failed, with only `desktopAuditFollowupFixes.test.js` missing `application-local.yml` and `mobileAppShell.test.js` missing generated `android/`. Any additional failure blocks completion.

- [x] **Step 5: Review branch scope**

```bash
git status --short
git log --oneline --decorate 75f864d..HEAD
git diff --check 75f864d..HEAD
```

Expected: only R1 plan/code/tests/dependency files are changed; no local config, generated native shell, secret, jar, database dump or unrelated user file is present.

- [x] **Step 6: Commit verification record**

```bash
git add docs/superpowers/plans/2026-07-10-mobile-remediation-r1.md
git commit -m "docs: record mobile remediation r1 verification"
```

## Verification Record

Recorded on 2026-07-10 in worktree `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/mobile-remediation`.

- Focused frontend regression: 9 files run, 0 failed.
- Backend notice regression: 7 tests run, 0 failures, 0 errors, 0 skipped.
- Production build: completed successfully; the prebuild frontend-secret scan passed. The build retained two pre-existing asset/entrypoint size warnings and produced no compile error.
- Full frontend suite: 84 files run, 2 failed. Both failures match the clean-worktree baseline:
  - `desktopAuditFollowupFixes.test.js` requires untracked local `erp-modules/erp-file/src/main/resources/application-local.yml`.
  - `mobileAppShell.test.js` requires generated/untracked `erp-ui/android/settings.gradle` and related native shell files.
- Dependency audit against `https://registry.npmjs.org`: 4 low, 0 moderate, 0 high, 0 critical. DOMPurify was raised from 3.4.7 to the patched 3.4.11 after the audit detected the newly published moderate advisories affecting `<=3.4.10`.
- Browser regression at 390×844:
  - Reproduced `销售 → 新建 → 新增明细 → 取消 → 放弃修改 → 库存 → 退货` without saving business data.
  - Inventory rendered `库存`, `库存提醒`, and `输入商品名称/编码搜索` together.
  - Sales return rendered `/mobile/sales-return`, `退货`, and `退货待处理` together.
  - Completed 50 inventory/return SPA transitions in five verified batches; no stale title/list state appeared.
  - Final body class was empty, no dialog remained open, and captured console errors were empty.
- Scope review: `git diff --check 75f864d..HEAD` passed; changed paths are limited to the R1 plan, mobile route/overlay code, notice sanitization, request deduplication, dependency metadata, and related tests.
