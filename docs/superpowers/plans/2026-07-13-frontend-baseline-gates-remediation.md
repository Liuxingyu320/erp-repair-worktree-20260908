# 前端九项基线门禁治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 逐项修复当前前端全量测试中的 9 个失败文件，使 142 个 Node 测试文件、密钥扫描、生产构建和受控 Capacitor 同步校验全部通过，同时不触碰调岗/离职业务代码和不提交生成物或凭据。

**Architecture:** 每一类根因独立 TDD、独立提交；生产缺陷改最小生产代码，只有 `mobileAppShell.test.js` 因错误要求 Git 跟踪受控生成目录而收窄为“稳定原生工程 + 同步校验脚本”契约。移动 viewport 修复复用已有引用计数工具，不创建第二套监听器。最终用 Node 24.14.0 跑整个 `scripts/run-node-tests.cjs` 门禁。

**Tech Stack:** Vue 2、SCSS、CommonJS Node source-contract tests、Node.js 24.14.0、npm 11、Capacitor 8、Vue CLI production build。

---

## 0. 失败清单、所有权和禁止项

- 执行分支：`codex/frontend-baseline-gates-20260713`。
- 执行 worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713`。

| 测试文件 | 已复现根因 | 生产文件 |
| --- | --- | --- |
| `headerNoticeReadAll.test.js` | 全部已读传 ids、乐观清空且不等待权威刷新 | `HeaderNotice/index.vue` |
| `laborContractModule.test.js` | 三类移动触控目标低于 44px | `mobile/contract/index.vue` |
| `mobileAppShell.test.js` | 测试错误要求干净 checkout 存在被 Git 忽略的 cap sync 输出 | 测试契约与 verify script |
| `mobileAuthEntryPages.test.js` | 选店生产页仍含 preview 组织和绕行逻辑 | `select-shop/index.vue` |
| `mobileProductionDataIsolation.test.js` | 与上一项相同根因 | `select-shop/index.vue` |
| `mobileInventoryWorkbench.test.js` | 仓库流程文案没有明确真实“供应商到货确认”，能力契约断言失败 | `MobileWorkbenchShell.vue` |
| `mobileProfileMaintenance.test.js` | 头像操作只有 34px | `mobile/profile/index.vue` |
| `mobileProgressiveRedesign.test.js` | 首个红灯是 shell 滚动根；其后还会检查 inventory/profile 的 viewport 生命周期、三个页面的 shared-system class、去玻璃/图片覆盖和统一底栏 active 逻辑 | 三个移动页/壳 |
| `unifiedTodoBusinessFocus.test.js` | 门店退回调拨在 generic transfer 页没有 `editReplenishment` action | `featureActions.js` |

禁止修改：`HrEmployeeTransferDialog.vue`、任何离职 DTO/规则、System/OA lifecycle 代码、SQL 迁移。禁止删除断言、跳过测试、提交 `dist`、Android/iOS `public`、Google/Firebase 凭据、签名文件、本机缓存。

## Task 1: 固定 Node 24 并逐项复现红灯

**Files:**

- Verify only: `erp-ui/package.json`
- Verify only: nine test files

- [ ] **Step 1: 切换到仓库支持的 Node**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713/erp-ui
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
node --version
npm --version
```

Expected: Node `v24.14.0`，npm `11.11.0`；满足 `>=22 <25`。

- [ ] **Step 2: 逐个复现九个失败**

```bash
node test/headerNoticeReadAll.test.js
node test/laborContractModule.test.js
node test/mobileAppShell.test.js
node test/mobileAuthEntryPages.test.js
node test/mobileInventoryWorkbench.test.js
node test/mobileProductionDataIsolation.test.js
node test/mobileProfileMaintenance.test.js
node test/mobileProgressiveRedesign.test.js
node test/unifiedTodoBusinessFocus.test.js
```

Expected: 每个文件以本计划表格中的断言失败；如果失败原因不同，先更新验证记录，不扩张到无关代码。

## Task 2: 让 HeaderNotice 全部已读等待服务端权威状态

**Files:**

- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue`
- Verify: `erp-ui/src/api/system/notice.js`
- Verify: `erp-ui/test/headerNoticeReadAll.test.js`

- [ ] **Step 1: 重新运行单测确认当前红灯**

```bash
node test/headerNoticeReadAll.test.js
```

Expected: `HeaderNotice should await its mark-all workflow`。

- [ ] **Step 2: 增加提交状态并替换方法**

在 data 中加入：

```js
noticeMarkingAll: false,
```

方法使用参数为空的 API，不乐观改本地数组：

```js
async markAllRead() {
  if (this.noticeMarkingAll || this.noticeLoading) return
  this.noticeMarkingAll = true
  try {
    await markNoticeReadAll()
    await this.loadNoticeTop()
  } catch (error) {
    // 保留原列表和未读数，下一次点击可以安全重试。
  } finally {
    this.noticeMarkingAll = false
  }
}
```

保留 `loadNoticeTop()` 返回 Promise。不要把 ids 或 body 重新加回 `markNoticeReadAll`。

- [ ] **Step 3: 运行测试确认通过**

```bash
node test/headerNoticeReadAll.test.js
```

- [ ] **Step 4: 提交**

```bash
git add src/layout/components/HeaderNotice/index.vue
git commit -m "fix: await authoritative notice read-all refresh"
```

## Task 3: 修复移动劳动合同 44px 触控目标

**Files:**

- Modify: `erp-ui/src/views/mobile/contract/index.vue`
- Verify: `erp-ui/test/laborContractModule.test.js`

- [ ] **Step 1: 运行单测确认 44px 断言失败**

```bash
node test/laborContractModule.test.js
```

- [ ] **Step 2: 增加最小 CSS**

```scss
.section-head a {
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
}

.certificate-link {
  min-height: 44px;
  align-items: center;
}

.confirm-text-row input {
  min-height: 44px;
}
```

保留现有颜色、边框和布局属性，只把触控目标扩到 44px。

- [ ] **Step 3: 运行测试并提交**

```bash
node test/laborContractModule.test.js
git add src/views/mobile/contract/index.vue
git commit -m "fix: preserve mobile contract touch targets"
```

## Task 4: 修正 Capacitor“源码门禁”和“生成物门禁”的边界

**Files:**

- Modify: `erp-ui/test/mobileAppShell.test.js`
- Verify: `erp-ui/scripts/verify-capacitor-sync.cjs`
- Verify: `erp-ui/android/.gitignore`
- Verify: `erp-ui/ios/.gitignore`

- [ ] **Step 1: 用 Git 证据确认 generated public 不受版本控制**

```bash
git ls-files android/app/src/main/assets/public/index.html ios/App/App/public/index.html
git check-ignore -v android/app/src/main/assets/public/index.html ios/App/App/public/index.html
```

Expected: `git ls-files` 无输出；`git check-ignore` 分别指向 Android/iOS `.gitignore`。这是收窄测试的依据。

- [ ] **Step 2: 运行测试确认因 ignored 文件缺失失败**

```bash
node test/mobileAppShell.test.js
```

Expected: 缺少 `android/app/src/main/assets/public/index.html` 或 iOS 对应文件。

- [ ] **Step 3: 让源码测试只要求稳定原生工程文件**

稳定文件列表保留：

```js
;[
  "android/settings.gradle",
  "android/app/build.gradle",
  "android/app/src/main/AndroidManifest.xml",
  "ios/App/App.xcodeproj/project.pbxproj",
  "ios/App/App/Info.plist"
].forEach(relativePath => {
  assertFile(relativePath, `${relativePath} should be tracked for the native shell`)
})
```

删除这里对两个 generated `public/index.html` 的 `assertFile`，但新增相反的生命周期契约：

```js
const verifySyncSource = readFile("scripts/verify-capacitor-sync.cjs")
const androidIgnore = readFile("android/.gitignore")
const iosIgnore = readFile("ios/.gitignore")

assert.ok(androidIgnore.includes("app/src/main/assets/public"))
assert.ok(iosIgnore.includes("App/App/public"))
assert.ok(verifySyncSource.includes("android/app/src/main/assets/public/index.html"))
assert.ok(verifySyncSource.includes("ios/App/App/public/index.html"))
assert.ok(verifySyncSource.includes("dist/index.html"))
```

这样干净 checkout 的 `npm test` 只验证源文件；`npm run app:sync && npm run app:verify` 继续严格验证生成副本。

- [ ] **Step 4: 运行测试，确认没有伪造生成文件**

```bash
node test/mobileAppShell.test.js
git status --short --ignored android/app/src/main/assets/public ios/App/App/public
```

Expected: 单测通过；generated public 仍显示 `!!` 或不存在，没有 staged 文件。

- [ ] **Step 5: 提交测试契约修复**

```bash
git add test/mobileAppShell.test.js
git commit -m "test: separate capacitor source and sync gates"
```

## Task 5: 从选店生产入口彻底移除 preview 组织

**Files:**

- Modify: `erp-ui/src/views/select-shop/index.vue`
- Verify: `erp-ui/test/mobileAuthEntryPages.test.js`
- Verify: `erp-ui/test/mobileProductionDataIsolation.test.js`

- [ ] **Step 1: 运行两个测试确认同一根因**

```bash
node test/mobileAuthEntryPages.test.js
node test/mobileProductionDataIsolation.test.js
```

- [ ] **Step 2: 删除 preview 数据和所有 preview 控制流**

具体删除：

- data 中完整 `previewDeptTree`。
- computed 中 `isPreviewMode`。
- `fetchDeptTree` 的 preview early return。
- template 两处 `isPreviewMode ? ... : ...`，改成固定“请选择当前操作组织”和“当前账号”。
- `prepareTreeAfterLoad` 的 preview 自动选择第一组织。
- `getPreviewRedirect()` 整个方法。
- `handleConfirm` 改为 `const redirect = this.getConfirmRedirect()`。
- `handleInvalidCachedDept` 的 preview 例外，缓存失效一律提示。
- `handleCancel` 的 preview 例外，始终执行 `FedLogOut` 后回登录。

不得把示例组织移到另一个 production 文件。

- [ ] **Step 3: 搜索确认生产入口无遗留 token**

```bash
rg -n "previewDeptTree|isPreviewMode|getPreviewRedirect|云岫茶室|青炉茶社|preview:\s*[\"']1[\"']" \
  src/views/select-shop/index.vue
```

Expected: 无输出。

- [ ] **Step 4: 运行两个测试和权限 guard 回归**

```bash
node test/mobileAuthEntryPages.test.js
node test/mobileProductionDataIsolation.test.js
node test/mobileRouteLoadGuard.test.js
```

- [ ] **Step 5: 提交**

```bash
git add src/views/select-shop/index.vue
git commit -m "fix: remove preview organizations from shop selection"
```

## Task 6: 让仓库工作台文案只承诺已有能力

**Files:**

- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Verify: `erp-ui/test/mobileInventoryWorkbench.test.js`

- [ ] **Step 1: 运行测试确认能力文案断言失败**

```bash
node test/mobileInventoryWorkbench.test.js
```

- [ ] **Step 2: 修改仓库 flow 文案**

把 fallback profile 的收货步骤改为：

```js
{ label: "收货", text: "供应商到货后确认入库", tone: "amber" }
```

确认文件不含 `扫码确认入库`。不要新增 camera/scanner 插件或虚假按钮。

- [ ] **Step 3: 运行测试并提交**

```bash
node test/mobileInventoryWorkbench.test.js
git add src/views/mobile/components/MobileWorkbenchShell.vue
git commit -m "fix: align warehouse copy with available capabilities"
```

## Task 7: 把移动头像操作扩大到 44px

**Files:**

- Modify: `erp-ui/src/views/mobile/profile/index.vue`
- Verify: `erp-ui/test/mobileProfileMaintenance.test.js`

- [ ] **Step 1: 运行测试确认当前为 34px**

```bash
node test/mobileProfileMaintenance.test.js
```

- [ ] **Step 2: 修改唯一 CSS 属性**

```scss
.avatar-wrap button {
  min-height: 44px;
}
```

保留其他样式，不能通过放宽测试阈值修复。

- [ ] **Step 3: 运行测试并提交**

```bash
node test/mobileProfileMaintenance.test.js
git add src/views/mobile/profile/index.vue
git commit -m "fix: enlarge mobile profile avatar action"
```

## Task 8: 给三个遗漏页面接入共享 viewport 生命周期和真实滚动根

**Files:**

- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/inventory/index.vue`
- Modify: `erp-ui/src/views/mobile/profile/index.vue`
- Verify: `erp-ui/src/views/mobile/mobileViewport.js`
- Verify: `erp-ui/test/mobileProgressiveRedesign.test.js`

- [ ] **Step 1: 运行测试并确认首个 shell 失败**

```bash
node test/mobileProgressiveRedesign.test.js
```

Expected: 首个错误为 `MobileWorkbenchShell.vue should identify its real page scroller`。注意修复 shell 后同一测试还会检查 inventory/profile，必须一次完成三处。

- [ ] **Step 2: 标记每个真实滚动根**

```html
<!-- MobileWorkbenchShell.vue / inventory/index.vue -->
<section class="content-stage" data-mobile-scroll-root>

<!-- profile/index.vue -->
<main class="mobile-profile-shell" aria-label="手机个人资料" data-mobile-scroll-root>
```

- [ ] **Step 3: 把现有 DOM 接到 shared mobile system class**

Workbench 必须形成以下精确 class：

```html
<header class="title-row mobile-system-topbar">
<section v-if="todoItems.length" class="glass-card todo-card mobile-system-panel mobile-system-panel--continuous">
<section class="glass-card hero-card mobile-system-panel">
<nav class="bottom-nav mobile-system-bottom-nav">
```

Inventory 必须形成：

```html
<div class="title-row mobile-system-topbar">
<section class="glass-card hero-card mobile-system-panel">
<section class="glass-card priority-card mobile-system-panel mobile-system-panel--continuous">
<nav class="bottom-nav mobile-system-bottom-nav">
```

Profile 必须形成：

```html
<header class="profile-header mobile-system-topbar">
<section class="profile-card profile-summary mobile-system-panel">
<nav class="mobile-profile-bottom-nav mobile-system-bottom-nav">
```

三个标记滚动根同时加 `mobile-system-scroll`，外层页面加 `mobile-system-page`。不要删原业务 class，避免现有局部样式和事件绑定失效。

- [ ] **Step 4: 三个组件都复用已有引用计数工具**

每个 script 引入：

```js
const { startMobileViewportSync, stopMobileViewportSync } = require("../mobileViewport")
```

`MobileWorkbenchShell.vue` 的相对路径也是 `../mobileViewport`；`inventory/index.vue` 和 `profile/index.vue` 同样位于 mobile 子目录。生命周期：

```js
created() {
  startMobileViewportSync()
  // 保留该组件原有 created 逻辑
},
beforeDestroy() {
  stopMobileViewportSync()
}
```

若组件已有 `beforeDestroy`，合并内容，不创建重复 hook。不要复制 `mobileViewport.js` 或直接注册 window listener。

- [ ] **Step 5: 让标记节点确实成为滚动容器并覆盖旧玻璃样式**

Shell 与 inventory 使用：

```scss
.mobile-shell {
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
}

.content-stage {
  height: var(--mobile-viewport-height, 100dvh);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);
}

.mobile-shell {
  background-image: none;
  background: var(--mobile-color-page);
}

.mobile-shell::before,
.glass-card {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
```

Profile 使用：

```scss
.mobile-profile-page {
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow: hidden;
  padding-bottom: 0;
}

.mobile-profile-shell {
  height: var(--mobile-viewport-height, 100dvh);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 24px);
}

.mobile-profile-bottom-nav {
  background: var(--mobile-color-surface);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}
```

确保 box-sizing 和既有底部导航留白不丢失。

- [ ] **Step 6: 统一 profile 底栏 active 逻辑**

扩展现有 destructuring 并添加方法：

```js
const {
  getMobileBottomNav,
  getMobileHomePath,
  isMobileBottomNavItemActive
} = require("../mobileNavigation")

isBottomNavItemActive(item) {
  return Boolean(item && isMobileBottomNavItemActive(
    item.path, this.$route.path
  ))
}
```

template 改为 `:class="{ active: isBottomNavItemActive(item) }"`，让 `/mobile/profile` 正确归入“我的”。

- [ ] **Step 7: 运行 progressive 与相关移动回归**

```bash
node test/mobileProgressiveRedesign.test.js
node test/mobileInventoryWorkbench.test.js
node test/mobileProfileMaintenance.test.js
node test/mobileOverlayStack.test.js
```

Expected: 全部退出 `0`。若第四个文件名在仓库不存在，用 `rg --files test | rg 'Overlay.*test'` 找到实际 overlay 测试后执行，不创建空测试替代。

- [ ] **Step 8: 提交**

```bash
git add \
  src/views/mobile/components/MobileWorkbenchShell.vue \
  src/views/mobile/inventory/index.vue \
  src/views/mobile/profile/index.vue
git commit -m "fix: wire mobile viewport lifecycle to real scrollers"
```

## Task 9: 恢复门店退回调拨的既有编辑动作

**Files:**

- Modify: `erp-ui/src/views/mobile/feature/featureActions.js`
- Verify: `erp-ui/src/utils/todoBusinessFocus.js`
- Verify: `erp-ui/test/unifiedTodoBusinessFocus.test.js`

- [ ] **Step 1: 运行测试确认 action 缺失**

```bash
node test/unifiedTodoBusinessFocus.test.js
```

- [ ] **Step 2: 按组织上下文选择草稿编辑 action**

把 generic transfer 的 draft 分支改为：

```js
if (featureKey === "transfer") {
  if (status === "draft") {
    actions.push(createAction(
      isStoreOrUnknownContext(context) ? "editReplenishment" : "editTransfer"
    ))
    actions.push(createAction("submitTransferDraft"))
    actions.push(createAction("deleteTransferDraft"))
  }
  // 保留 submitted/deliver/receive/cancel 逻辑
}
```

这让 todo focus 中固定的 `INV_TRANSFER_RETURNED -> editReplenishment` 能在门店 generic transfer 页面找到现有 edit-form 行为，同时仓库草稿仍使用 `editTransfer`。不要改 todoType 或业务 API。

- [ ] **Step 3: 增加反向断言并运行测试**

在现有测试补充仓库上下文仍返回 `editTransfer` 且不返回 `editReplenishment`，防止所有调拨都被当成补货。

```bash
node test/unifiedTodoBusinessFocus.test.js
node test/mobileFeatureActions.test.js
```

若第二个实际文件名不同，用 `rg --files test | rg 'featureActions.*test|mobile.*Actions.*test'` 定位并运行实际测试。

- [ ] **Step 4: 提交**

```bash
git add src/views/mobile/feature/featureActions.js test/unifiedTodoBusinessFocus.test.js
git commit -m "fix: restore returned transfer todo edit focus"
```

## Task 10: 九项专项、142 文件全量、构建和 Capacitor 验证

**Files:**

- Create: `docs/superpowers/verification/2026-07-13-frontend-baseline-gates.md`

- [ ] **Step 1: 再跑九项专项**

```bash
node test/headerNoticeReadAll.test.js
node test/laborContractModule.test.js
node test/mobileAppShell.test.js
node test/mobileAuthEntryPages.test.js
node test/mobileInventoryWorkbench.test.js
node test/mobileProductionDataIsolation.test.js
node test/mobileProfileMaintenance.test.js
node test/mobileProgressiveRedesign.test.js
node test/unifiedTodoBusinessFocus.test.js
```

Expected: 九项全部退出 `0`。

- [ ] **Step 2: 运行前端全量**

```bash
npm test
```

Expected: 输出 142 个测试文件全部通过；不再有“133 pass / 9 fail”。记录脚本实际输出，不手算。

- [ ] **Step 3: 运行密钥扫描和生产构建**

```bash
node scripts/scan-frontend-secrets.cjs
npm run build:prod
```

Expected: 两条命令退出 `0`。

- [ ] **Step 4: 执行受控原生同步和校验**

```bash
npm run app:sync
npm run app:verify
```

Expected: `dist`、Android public、iOS public 静态引用一致；非 release 模式允许缺少正式推送凭据警告。任何 build/API 配置失败都必须真实报告，不能把生成目录提交来绕过。

- [ ] **Step 5: 确认生成物仍未被 Git 跟踪**

```bash
git status --short --ignored \
  dist android/app/src/main/assets/public ios/App/App/public
git ls-files \
  dist android/app/src/main/assets/public ios/App/App/public
```

Expected: 第一条只可能显示 `!!`；第二条无相关生成文件输出。

- [ ] **Step 6: 检查分支边界和空白错误**

```bash
cd ..
git diff --check codex/phase3-multi-agent-plan-20260713..HEAD
git diff --name-only codex/phase3-multi-agent-plan-20260713..HEAD
```

Expected: 无空白错误；文件只在本计划清单和验证文档内。

- [ ] **Step 7: 写验证记录并提交**

记录每个失败的原始断言、根因、修复提交、专项结果、全量文件数、Node/npm 版本、构建、同步、凭据警告和未推送状态。

```bash
git add docs/superpowers/verification/2026-07-13-frontend-baseline-gates.md
git commit -m "docs: record frontend baseline gate verification"
```

- [ ] **Step 8: 向 Agent 1 交付**

发送分支 `codex/frontend-baseline-gates-20260713`、提交区间、九项根因表、142 文件结果、构建/Capacitor 结果和干净工作树证据。不得合并到集成分支、不得推送远端、不得修改 Agent 1/2 文件。
