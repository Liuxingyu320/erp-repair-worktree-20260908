# Cloud Drive P0 Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复云盘不可预览文件入口、手机端剩余额度、重复上传焦点和搜索范围文案四项 P0 问题。

**Architecture:** 保持现有 Vue 2 组件和 `/file/drive/**` API 契约不变。桌面端与手机端复用既有预览能力纯函数，手机端新增一个可独立测试的剩余额度纯函数，页面层只消费这些判断并修正语义和文案。

**Tech Stack:** Vue 2.6、Element UI、CommonJS 状态辅助函数、Node.js `node:test` 源码契约测试。

---

## 文件结构

- `erp-ui/src/views/drive/components/DriveNodeList.vue`：桌面文件名称与预览操作只在文件可预览时交互。
- `erp-ui/src/views/drive/components/DriveToolbar.vue`：统一搜索范围说明，并让隐藏文件输入退出键盘和读屏导航。
- `erp-ui/src/views/mobile/drive/mobileDriveState.js`：提供剩余额度纯函数，并继续导出手机端预览判断。
- `erp-ui/src/views/mobile/drive/index.vue`：显示准确容量、区分可预览文件、统一搜索文案并隐藏重复文件输入。
- `erp-ui/test/cloudDriveDesktop.test.js`：锁定桌面端预览、搜索和上传无障碍契约。
- `erp-ui/test/mobileCloudDrive.test.js`：锁定手机端容量、预览、搜索和上传无障碍契约。

### Task 1: 桌面端预览入口与上传语义

**Files:**
- Modify: `erp-ui/test/cloudDriveDesktop.test.js`
- Modify: `erp-ui/src/views/drive/components/DriveNodeList.vue:9-118`
- Modify: `erp-ui/src/views/drive/components/DriveToolbar.vue:22-66`

- [ ] **Step 1: 写入桌面端失败测试**

在 `erp-ui/test/cloudDriveDesktop.test.js` 的列表与工具栏断言后加入：

```js
assert.ok(
  list.includes("const { formatBytes, isPreviewable } = require('../driveState')") &&
    list.includes('v-if="isPreviewable(scope.row)"') &&
    list.includes('v-else-if="isPreviewable(scope.row)"') &&
    list.includes('class="drive-node-name__static"'),
  'desktop files should expose preview controls only when the file is actually previewable'
)
assert.ok(
  toolbar.includes('aria-label="搜索当前空间的文件和文件夹"') &&
    toolbar.includes('placeholder="搜索当前空间的文件和文件夹"'),
  'desktop search copy should state that search is scoped to the active space'
)
assert.ok(
  toolbar.includes('tabindex="-1"') && toolbar.includes('aria-hidden="true"'),
  'desktop hidden file picker should not duplicate the visible upload control for keyboard or screen-reader users'
)
```

- [ ] **Step 2: 运行桌面端测试并确认 RED**

Run:

```bash
cd erp-ui
node --test test/cloudDriveDesktop.test.js
```

Expected: FAIL，失败消息为 `desktop files should expose preview controls only when the file is actually previewable`。

- [ ] **Step 3: 实现桌面端最小修改**

在 `DriveNodeList.vue` 中导入预览判断：

```js
const { formatBytes, isPreviewable } = require('../driveState')
```

在 `methods` 中暴露 `isPreviewable`，并让图标使用同一规则：

```js
methods: {
  formatBytes,
  isPreviewable,
  openNode(node) {
    this.$emit(node.nodeType === 'FOLDER' ? 'open-folder' : 'preview', node)
  },
  iconClass(node) {
    if (node.nodeType === 'FOLDER') return 'el-icon-folder drive-node-name__icon--folder'
    if (isPreviewable(node)) return 'el-icon-document drive-node-name__icon--preview'
    return 'el-icon-document drive-node-name__icon--file'
  }
}
```

将名称区分为可打开按钮与不可预览静态内容：

```vue
<button
  v-if="scope.row.nodeType === 'FOLDER'"
  type="button"
  class="drive-node-name__button"
  :aria-label="nodeAriaLabel(scope.row)"
  @click="openNode(scope.row)"
>
  <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
  <small v-if="(keyword || mode === 'recent') && scope.row.logicalPath" class="drive-node-name__path">
    {{ scope.row.logicalPath }}
  </small>
</button>
<button
  v-else-if="isPreviewable(scope.row)"
  type="button"
  class="drive-node-name__button"
  :aria-label="nodeAriaLabel(scope.row)"
  @click="openNode(scope.row)"
>
  <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
  <small v-if="(keyword || mode === 'recent') && scope.row.logicalPath" class="drive-node-name__path">
    {{ scope.row.logicalPath }}
  </small>
</button>
<span v-else class="drive-node-name__static">
  <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
  <small v-if="(keyword || mode === 'recent') && scope.row.logicalPath" class="drive-node-name__path">
    {{ scope.row.logicalPath }}
  </small>
</span>
```

在文件操作区只为可预览文件显示预览：

```vue
<el-button
  v-if="isPreviewable(scope.row)"
  type="text"
  :aria-label="'预览 ' + scope.row.nodeName"
  @click="$emit('preview', scope.row)"
>预览</el-button>
```

为静态名称增加与现有名称一致但无指针行为的样式：

```scss
.drive-node-name__static {
  min-width: 0;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 4px 5px;
  overflow: hidden;
  color: #2c3e50;
}
```

在 `DriveToolbar.vue` 将搜索属性改为：

```vue
aria-label="搜索当前空间的文件和文件夹"
placeholder="搜索当前空间的文件和文件夹"
```

并给隐藏文件输入增加：

```vue
tabindex="-1"
aria-hidden="true"
```

删除该隐藏输入原有的 `aria-label`，避免隐藏节点继续声明可访问名称。

- [ ] **Step 4: 运行桌面端测试并确认 GREEN**

Run:

```bash
cd erp-ui
node --test test/cloudDriveDesktop.test.js test/cloudDriveState.test.js
```

Expected: 2 tests PASS，0 failures。

- [ ] **Step 5: 提交桌面端修复**

```bash
git add erp-ui/test/cloudDriveDesktop.test.js \
  erp-ui/src/views/drive/components/DriveNodeList.vue \
  erp-ui/src/views/drive/components/DriveToolbar.vue
git commit -m "fix(ui): clarify cloud drive preview actions"
```

### Task 2: 手机端容量、预览入口与上传语义

**Files:**
- Modify: `erp-ui/test/mobileCloudDrive.test.js`
- Modify: `erp-ui/src/views/mobile/drive/mobileDriveState.js:48-64,166-184`
- Modify: `erp-ui/src/views/mobile/drive/index.vue:15-145,217-289`

- [ ] **Step 1: 写入手机端失败测试**

在 `mobileDriveState` 解构中加入 `getMobileRemainingQuotaBytes`，并在容量格式断言后加入：

```js
assert.strictEqual(
  getMobileRemainingQuotaBytes({ usedBytes: 734003200, quotaBytes: 2147483648 }),
  1413480448,
  'mobile drive should calculate remaining quota from total minus used bytes'
)
assert.strictEqual(
  getMobileRemainingQuotaBytes({ usedBytes: 300, quotaBytes: 200 }),
  0,
  'mobile remaining quota should never become negative'
)
assert.strictEqual(getMobileRemainingQuotaBytes(null), 0)
```

在 `mobileDrivePage` 源码断言后加入：

```js
assert.ok(
  mobileDrivePage.includes('剩余 {{ formatMobileBytes(getMobileRemainingQuotaBytes(activeSpace)) }}') &&
    mobileDrivePage.includes('总额度 {{ formatMobileBytes(activeSpace.quotaBytes) }}'),
  'mobile space card should distinguish remaining quota from total quota'
)
assert.ok(
  mobileDrivePage.includes('v-else-if="isMobilePreviewable(node)"') &&
    mobileDrivePage.includes('v-else class="mobile-drive-row__main mobile-drive-row__main--static"'),
  'mobile unsupported files should not expose a dead-end preview button'
)
assert.ok(
  mobileDrivePage.includes('placeholder="搜索当前空间的文件和文件夹"') &&
    mobileDrivePage.includes('aria-label="搜索当前空间的文件和文件夹"'),
  'mobile search copy should state that search is scoped to the active space'
)
assert.strictEqual(
  (mobileDrivePage.match(/tabindex="-1"/g) || []).length,
  2,
  'both mobile hidden file inputs should leave keyboard navigation'
)
assert.strictEqual(
  (mobileDrivePage.match(/aria-hidden="true"/g) || []).length >= 2,
  true,
  'both mobile hidden file inputs should leave the accessibility tree'
)
```

- [ ] **Step 2: 运行手机端测试并确认 RED**

Run:

```bash
cd erp-ui
node --test test/mobileCloudDrive.test.js
```

Expected: FAIL，失败原因为 `getMobileRemainingQuotaBytes is not a function`。

- [ ] **Step 3: 实现手机端剩余额度纯函数**

在 `mobileDriveState.js` 的 `formatMobileBytes` 后增加：

```js
function getMobileRemainingQuotaBytes(space) {
  const source = space && typeof space === 'object' ? space : {}
  const quotaBytes = Math.max(Number(source.quotaBytes) || 0, 0)
  const usedBytes = Math.max(Number(source.usedBytes) || 0, 0)
  return Math.max(quotaBytes - usedBytes, 0)
}
```

在 `module.exports` 中加入：

```js
getMobileRemainingQuotaBytes,
```

- [ ] **Step 4: 实现手机页面最小修改**

在 `index.vue` 的 `mobileDriveState` 解构中加入：

```js
getMobileRemainingQuotaBytes,
isMobilePreviewable,
```

在 `methods` 中暴露两个纯函数：

```js
methods: {
  formatMobileBytes,
  getMobileRemainingQuotaBytes,
  isMobilePreviewable,
}
```

将容量说明改为：

```vue
<p v-if="activeSpace">
  已使用 {{ formatMobileBytes(activeSpace.usedBytes) }}，
  剩余 {{ formatMobileBytes(getMobileRemainingQuotaBytes(activeSpace)) }}，
  总额度 {{ formatMobileBytes(activeSpace.quotaBytes) }}
</p>
```

将搜索属性统一为：

```vue
aria-label="搜索当前空间的文件和文件夹"
placeholder="搜索当前空间的文件和文件夹"
```

两个隐藏文件输入都增加：

```vue
tabindex="-1"
aria-hidden="true"
```

并删除两个输入原有的 `aria-label`。

将现有文件主按钮改为可预览条件，并增加静态文件内容：

```vue
<button
  v-else-if="isMobilePreviewable(node)"
  type="button"
  class="mobile-drive-row__main"
  :aria-label="'预览 ' + node.nodeName"
  @click="openPreview(node)"
>
  <span class="mobile-drive-row__icon" aria-hidden="true">▤</span>
  <span class="mobile-drive-row__copy">
    <strong>{{ node.nodeName }}</strong>
    <small v-if="keyword && node.logicalPath">{{ node.logicalPath }}</small>
    <small v-else>{{ formatMobileBytes(node.sizeBytes) }}</small>
  </span>
</button>
<div v-else class="mobile-drive-row__main mobile-drive-row__main--static">
  <span class="mobile-drive-row__icon" aria-hidden="true">▤</span>
  <span class="mobile-drive-row__copy">
    <strong>{{ node.nodeName }}</strong>
    <small v-if="keyword && node.logicalPath">{{ node.logicalPath }}</small>
    <small v-else>{{ formatMobileBytes(node.sizeBytes) }}</small>
  </span>
</div>
```

在现有 `.mobile-drive-row__main` 样式附近增加：

```scss
.mobile-drive-row__main--static { cursor: default; }
```

- [ ] **Step 5: 运行手机端测试并确认 GREEN**

Run:

```bash
cd erp-ui
node --test test/mobileCloudDrive.test.js test/cloudDriveState.test.js
```

Expected: 2 tests PASS，0 failures。

- [ ] **Step 6: 提交手机端修复**

```bash
git add erp-ui/test/mobileCloudDrive.test.js \
  erp-ui/src/views/mobile/drive/mobileDriveState.js \
  erp-ui/src/views/mobile/drive/index.vue
git commit -m "fix(mobile): correct cloud drive file actions"
```

### Task 3: 云盘回归与生产构建验证

**Files:**
- Verify: `erp-ui/test/cloudDriveApi.test.js`
- Verify: `erp-ui/test/cloudDriveState.test.js`
- Verify: `erp-ui/test/cloudDriveDesktop.test.js`
- Verify: `erp-ui/test/mobileCloudDrive.test.js`
- Verify: `erp-ui/package.json`

- [ ] **Step 1: 运行四个云盘聚焦测试**

Run:

```bash
cd erp-ui
node --test \
  test/cloudDriveApi.test.js \
  test/cloudDriveState.test.js \
  test/cloudDriveDesktop.test.js \
  test/mobileCloudDrive.test.js
```

Expected: 4 tests PASS，0 failures。

- [ ] **Step 2: 运行前端全量测试**

Run:

```bash
cd erp-ui
npm test
```

Expected: 所有测试文件通过，0 failures。

- [ ] **Step 3: 运行生产构建**

Run:

```bash
cd erp-ui
npm run build:prod
```

Expected: 密钥扫描通过，Vue 生产构建成功；允许保留项目基线已有的 bundle-size 警告。

- [ ] **Step 4: 检查差异与提交状态**

Run:

```bash
git diff --check
git status --short
git log -3 --oneline
```

Expected: `git diff --check` 无输出；工作树无未提交的云盘源码或测试修改；最近提交包含桌面端和手机端两个修复提交。
