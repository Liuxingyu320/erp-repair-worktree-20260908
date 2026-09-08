# Desktop Runtime Entry UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复算式验证码说明、菜单搜索直接回车和顶部搜索/通知键盘入口，同时保持现有接口、权限、视觉布局与鼠标交互不变。

**Architecture:** 保留现有 Vue 2 + Element UI 组件边界。菜单搜索只新增一个无副作用的索引工具，组件在结果集更新后调用它；登录与顶部入口直接补充语义化模板属性和现有方法绑定，不引入新的状态管理或接口。

**Tech Stack:** Vue 2.6、Element UI 2.15、CommonJS Node `assert` 测试、Vue CLI 4、SCSS。

---

## 文件结构

- Create: `erp-ui/src/utils/searchSelection.js` — 唯一负责根据当前搜索结果集计算默认可选索引。
- Create: `erp-ui/test/desktopRuntimeEntryUx.test.js` — 覆盖本轮运行验收确认的行为与可访问性契约。
- Modify: `erp-ui/src/components/HeaderSearch/index.vue` — 语义化搜索按钮、默认首项选择和回车导航。
- Modify: `erp-ui/src/views/login.vue` — 算式结果文案、图片说明和键盘刷新。
- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue` — 语义化通知按钮、动态名称和键盘打开。
- Create: `docs/superpowers/audits/2026-07-10-r1-runtime/README.md` — 汇总真实页面证据、通过项、遗留项和发布建议。

### Task 1: 菜单搜索默认首项与语义化入口

**Files:**
- Create: `erp-ui/test/desktopRuntimeEntryUx.test.js`
- Create: `erp-ui/src/utils/searchSelection.js`
- Modify: `erp-ui/src/components/HeaderSearch/index.vue`

- [ ] **Step 1: 写失败测试**

创建 `erp-ui/test/desktopRuntimeEntryUx.test.js`：

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")
const readUi = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")

function loadSearchSelection() {
  const source = readUi("src/utils/searchSelection.js")
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(/\bexport\s+function\s+getFirstSelectableIndex/, "function getFirstSelectableIndex") +
      "\nmodule.exports = { getFirstSelectableIndex }",
    sandbox,
    { filename: "searchSelection.js" }
  )
  return sandbox.module.exports
}

const searchSource = readUi("src/components/HeaderSearch/index.vue")

assert.ok(
  searchSource.includes('<button') &&
    searchSource.includes('aria-label="搜索菜单"') &&
    searchSource.includes('type="button"') &&
    searchSource.includes('@click.stop="click"'),
  "header search should expose a semantic button that preserves the existing click action"
)
assert.ok(
  searchSource.includes("getFirstSelectableIndex(this.options)") &&
    !searchSource.includes('@mouseleave="activeIndex = -1"'),
  "search results should keep a valid first selection so Enter works without an ArrowDown precondition"
)

const { getFirstSelectableIndex } = loadSearchSelection()
assert.strictEqual(getFirstSelectableIndex([]), -1)
assert.strictEqual(getFirstSelectableIndex(null), -1)
assert.strictEqual(getFirstSelectableIndex([{ path: "/system/notice" }]), 0)
assert.strictEqual(getFirstSelectableIndex([{ path: "/a" }, { path: "/b" }]), 0)

console.log("desktopRuntimeEntryUx search tests passed")
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js
```

Expected: FAIL；首次失败应指出搜索入口不是语义化按钮，或 `src/utils/searchSelection.js` 尚不存在。不能在测试已经通过的情况下开始实现。

- [ ] **Step 3: 实现最小搜索选择工具**

创建 `erp-ui/src/utils/searchSelection.js`：

```js
export function getFirstSelectableIndex(options) {
  return Array.isArray(options) && options.length > 0 ? 0 : -1
}
```

- [ ] **Step 4: 修改搜索组件**

将搜索图标入口改为原生按钮：

```vue
<button
  type="button"
  class="search-trigger"
  aria-label="搜索菜单"
  @click.stop="click"
>
  <svg-icon class-name="search-icon" icon-class="search" />
</button>
```

导入工具：

```js
import { getFirstSelectableIndex } from '@/utils/searchSelection'
```

打开搜索和查询结果刷新后设置首项；没有结果时工具会返回 `-1`：

```js
click() {
  this.show = !this.show
  if (this.show) {
    this.options = this.searchPool
    this.activeIndex = getFirstSelectableIndex(this.options)
  }
},
```

```js
querySearch(query) {
  if (query !== '') {
    const q = query.toLowerCase()
    const pathMatches = this.searchPool.filter(item =>
      item.path.toLowerCase().includes(q)
    )
    const fuseMatches = this.fuse.search(query).map(item => item.item)
    const merged = [...pathMatches]
    fuseMatches.forEach(item => {
      if (!merged.find(m => m.path === item.path)) {
        merged.push(item)
      }
    })
    this.options = merged
  } else {
    this.options = this.searchPool
  }
  this.activeIndex = getFirstSelectableIndex(this.options)
},
```

删除搜索结果项的 `@mouseleave="activeIndex = -1"`。在现有 `.header-search` 样式内加入按钮重置，保持视觉布局：

```scss
.search-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
```

- [ ] **Step 5: 运行聚焦测试并确认通过**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js desktopSecurityBoundary.test.js
```

Expected: 2 files run, 0 failed；安全文本高亮测试继续通过。

- [ ] **Step 6: 提交搜索修复**

```bash
git add erp-ui/test/desktopRuntimeEntryUx.test.js erp-ui/src/utils/searchSelection.js erp-ui/src/components/HeaderSearch/index.vue
git commit -m "fix: make desktop menu search directly selectable"
```

### Task 2: 算式验证码文案与键盘刷新

**Files:**
- Modify: `erp-ui/test/desktopRuntimeEntryUx.test.js`
- Modify: `erp-ui/src/views/login.vue`

- [ ] **Step 1: 向测试文件追加失败断言**

在最终 `console.log` 之前加入：

```js
const loginSource = readUi("src/views/login.vue")

assert.strictEqual(
  (loginSource.match(/placeholder="请输入图中算式结果"/g) || []).length,
  2,
  "desktop and mobile login inputs should both explain that the math result is required"
)
assert.ok(
  loginSource.includes('message: "请输入图中算式结果"'),
  "captcha validation should repeat the same actionable instruction"
)
assert.strictEqual(
  (loginSource.match(/alt="验证码算式，点击刷新"/g) || []).length,
  2,
  "both captcha images should expose useful alternative text"
)
assert.strictEqual(
  (loginSource.match(/aria-label="刷新验证码"/g) || []).length,
  2,
  "both captcha refresh controls should have an accessible name"
)
assert.ok(
  (loginSource.match(/@keydown\.enter\.prevent="getCode"/g) || []).length === 2 &&
    (loginSource.match(/@keydown\.space\.prevent="getCode"/g) || []).length === 2,
  "both captcha refresh controls should support Enter and Space"
)
assert.ok(
  loginSource.indexOf('this.loginForm.code = ""') < loginSource.indexOf("getCodeImg().then"),
  "refreshing the captcha should clear the stale answer before requesting a new challenge"
)
```

- [ ] **Step 2: 运行测试并确认新增断言失败**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js
```

Expected: FAIL，错误应指出桌面和手机输入框仍未使用“请输入图中算式结果”。

- [ ] **Step 3: 修改桌面与手机验证码模板**

两处验证码输入框统一使用：

```vue
placeholder="请输入图中算式结果"
```

两处验证码容器统一增加：

```vue
role="button"
tabindex="0"
aria-label="刷新验证码"
@click="getCode"
@keydown.enter.prevent="getCode"
@keydown.space.prevent="getCode"
```

两处图片与回退文案统一为：

```vue
<img
  v-if="codeUrl"
  :src="codeUrl"
  class="desktop-login-code-img"
  alt="验证码算式，点击刷新"
>
<span v-else>验证码加载中</span>
```

手机端保留原有 `login-code-img` 类名。将校验规则改为：

```js
code: [{ required: true, trigger: "change", message: "请输入图中算式结果" }]
```

在 `getCode()` 发起请求前清空旧答案：

```js
getCode() {
  this.loginForm.code = ""
  getCodeImg().then(res => {
```

- [ ] **Step 4: 运行登录相关测试并确认通过**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js mobileAuthEntryPages.test.js
```

Expected: 2 files run, 0 failed；验证码 API 和手机登录入口契约继续通过。

- [ ] **Step 5: 提交验证码修复**

```bash
git add erp-ui/test/desktopRuntimeEntryUx.test.js erp-ui/src/views/login.vue
git commit -m "fix: clarify math captcha entry"
```

### Task 3: 通知入口可访问名称与键盘打开

**Files:**
- Modify: `erp-ui/test/desktopRuntimeEntryUx.test.js`
- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue`

- [ ] **Step 1: 向测试文件追加失败断言**

在最终 `console.log` 之前加入：

```js
const noticeSource = readUi("src/layout/components/HeaderNotice/index.vue")

assert.ok(
  noticeSource.includes('<button') &&
    noticeSource.includes('type="button"') &&
    noticeSource.includes(':aria-label="noticeAriaLabel"') &&
    noticeSource.includes(':aria-expanded="noticeVisible"') &&
    noticeSource.includes('aria-haspopup="dialog"'),
  "the notice trigger should be a semantic button that reports its popover state"
)
assert.ok(
  noticeSource.includes('@click.stop="onNoticeActivate"') &&
    noticeSource.includes('@blur="onNoticeLeave"') &&
    !noticeSource.includes('@focus="onNoticeEnter"'),
  "native button activation should open the notice popover without reopening it on focus return"
)
assert.ok(
  noticeSource.includes("noticeAriaLabel()") &&
    noticeSource.includes('`通知公告，${this.unreadCount} 条未读`'),
  "the notice button should announce the unread count without relying on the visual badge"
)
```

- [ ] **Step 2: 运行测试并确认新增断言失败**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js
```

Expected: FAIL，错误应指出通知入口仍是无名称的 `div`。

- [ ] **Step 3: 修改通知入口模板与计算属性**

将触发器改为语义化按钮：

```vue
<button
  v-popover:noticePopover
  type="button"
  class="notice-trigger"
  :aria-label="noticeAriaLabel"
  :aria-expanded="noticeVisible"
  aria-haspopup="dialog"
  @mouseenter="onNoticeEnter"
  @mouseleave="onNoticeLeave"
  @blur="onNoticeLeave"
  @click.stop="onNoticeActivate"
>
  <svg-icon icon-class="bell" />
  <span v-if="unreadCount > 0" class="notice-badge">{{ unreadCount }}</span>
</button>
```

在 `data()` 与 `mounted()` 之间增加：

```js
computed: {
  noticeAriaLabel() {
    return this.unreadCount > 0
      ? `通知公告，${this.unreadCount} 条未读`
      : '通知公告'
  }
},
```

浮层打开后将 Element UI 的 tooltip 语义修正为非模态 dialog，并让键盘激活进入浮层；Escape 关闭后只恢复触发按钮焦点，不通过 focus 自动重开：

```js
onNoticeEnter() {
  this.openNotice(false)
},
onNoticeActivate() {
  this.openNotice(true)
},
openNotice(focusPopover = false) {
  clearTimeout(this.noticeLeaveTimer)
  this.noticeVisible = true
  this.$nextTick(() => {
    const popper = this.$refs.noticePopover.$refs.popper
    const trigger = this.$refs.noticeTrigger
    if (!popper) return
    popper.setAttribute('role', 'dialog')
    popper.setAttribute('aria-label', '通知公告')
    if (trigger && popper.id) {
      trigger.setAttribute('aria-controls', popper.id)
      trigger.removeAttribute('aria-describedby')
    }
    if (focusPopover) popper.focus()
  })
},
```

“全部已读”和公告行使用原生 `button`。在首次绑定浮层时加入完整焦点事件：

```js
popper.addEventListener('focusin', () => clearTimeout(this.noticeLeaveTimer))
popper.addEventListener('focusout', event => {
  const nextTarget = event.relatedTarget
  if (popper.contains(nextTarget) || (trigger && trigger.contains(nextTarget))) return
  this.onNoticeLeave()
})
popper.addEventListener('keydown', event => {
  if (event.key !== 'Escape') return
  event.preventDefault()
  clearTimeout(this.noticeLeaveTimer)
  this.noticeVisible = false
  this.$nextTick(() => {
    if (this.$refs.noticeTrigger) this.$refs.noticeTrigger.focus()
  })
})
```

按钮样式只重置浏览器默认外观，不移除默认焦点轮廓：

```scss
.notice-trigger {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  position: relative;
  transform: translateX(-6px);
}
```

- [ ] **Step 4: 运行聚焦测试并确认通过**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js desktopSecurityBoundary.test.js mobileAuthEntryPages.test.js
```

Expected: 3 files run, 0 failed。

- [ ] **Step 5: 提交通知入口修复**

```bash
git add erp-ui/test/desktopRuntimeEntryUx.test.js erp-ui/src/layout/components/HeaderNotice/index.vue
git commit -m "fix: make desktop notice trigger keyboard accessible"
```

### Task 4: 全量验证、真实浏览器回归与审计记录

**Files:**
- Create: `docs/superpowers/audits/2026-07-10-r1-runtime/README.md`
- Verify only: `erp-ui/`

- [ ] **Step 1: 运行聚焦前端回归**

Run:

```bash
cd erp-ui && npm test -- desktopRuntimeEntryUx.test.js desktopSecurityBoundary.test.js mobileAuthEntryPages.test.js desktopAuditRemediation.test.js hrPaginationUx.test.js shopContextUx.test.js
```

Expected: 6 files run, 0 failed。

- [ ] **Step 2: 运行前端全量测试**

Run:

```bash
cd erp-ui && npm test
```

Expected: 本轮新增测试必须通过；若仍出现既有 Android 本地配置缺失基线，记录准确文件名和错误，不把它归因于本轮改动。

- [ ] **Step 3: 运行生产构建**

Run:

```bash
cd erp-ui && npm run build:prod
```

Expected: exit 0，生成生产包；允许记录既有 chunk 体积警告，但不得有编译错误。

- [ ] **Step 4: 运行真实浏览器回归**

使用已经授权并已登录的本地完整服务环境，按顺序验证：

1. 1440×900 登录页显示“请输入图中算式结果”。
2. Tab 可聚焦验证码刷新控件，Enter/Space 刷新并清空旧答案。
3. 正确算式结果登录后选择“测试门店”。
4. 全局搜索输入“通知公告”，不按方向键直接 Enter，进入 `/system/notice`。
5. Tab 可聚焦搜索和通知按钮，辅助名称分别为“搜索菜单”和“通知公告”。
6. 返回 `/hr/employee`，搜索、清空、进入第 2 页，确认总数 152、首行“绳淇月”、组织仍为“测试门店”。

Expected: 六步全部通过；每个关键状态只保留稳定渲染的截图，不采用空白、黑屏或过渡帧。

- [ ] **Step 5: 编写运行验收记录**

创建 `docs/superpowers/audits/2026-07-10-r1-runtime/README.md`，至少包含以下完整结构：

```markdown
# R1 电脑端真实运行验收

## 环境

- 日期：2026-07-10
- 视口：1440×900
- 组织：测试门店
- 分支：codex/desktop-audit-remediation-r1

## 结果

| 流程 | 状态 | 证据 |
| --- | --- | --- |
| 登录与算式验证码说明 | 通过 | `21-login-math-captcha.png` |
| 组织选择与上下文保持 | 通过 | `24-context-hr-page-2.png` |
| HR 搜索、统计与分页 | 通过 | `24-context-hr-page-2.png` |
| 全局搜索直接回车 | 通过 | `22-search-direct-enter.png` |
| 搜索与通知键盘入口 | 通过 | 浏览器可访问性快照与手工键盘回归 |

## 遗留风险

- 前端依赖审计仍有 Vue 2 生命周期相关低风险项，当前依赖树无直接无破坏性修复版本。
- 全站无障碍覆盖不在本轮范围，后续应按高频流程分批检查表单标签、焦点顺序和浮层内容操作。

## 发布建议

- 先在测试环境回放登录、组织选择、员工档案和公告搜索。
- 观察验证码失败率、菜单搜索导航成功率和前端异常日志。
- 发现入口阻断时可按本轮独立提交逐项回滚。
```

- [ ] **Step 6: 提交验收记录**

```bash
git add docs/superpowers/audits/2026-07-10-r1-runtime/README.md docs/superpowers/audits/2026-07-10-r1-runtime/*.png
git commit -m "docs: record desktop R1 runtime acceptance"
```

- [ ] **Step 7: 最终工作区核对**

Run:

```bash
git status --short --branch
git log --oneline -8
```

Expected: 只保留明确说明的本地运行产物；实现、测试、设计、计划和采用的验收证据均已提交。
