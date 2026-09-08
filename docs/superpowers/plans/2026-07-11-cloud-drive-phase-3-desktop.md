# 企业云盘阶段 3：桌面云盘 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用阶段 2 的鉴权 API 交付桌面端完整云盘，包括空间导航、目录浏览、搜索和排序、文件夹、上传队列、预览、下载、重命名、同空间移动、最近使用、回收站和容量显示。

**Architecture:** `views/drive/index.vue` 只负责编排空间、目录和对话框状态；API、纯状态函数和六个可复用组件分离。文件内容始终通过鉴权 Blob API 获取，预览对象 URL 在关闭和组件销毁时回收，上传队列逐项重试且不在刷新后重复提交已完成项。

**Tech Stack:** Vue 2.6, Element UI 2.15, Axios wrapper, `file-saver`, SCSS, Node `assert` source-contract and pure-function tests.

---

## File map

**Create:**

- `erp-ui/src/api/drive/index.js` — 所有云盘 JSON、multipart 和 Blob API。
- `erp-ui/src/views/drive/driveState.js` — CommonJS-compatible route、排序、容量、上传队列和预览纯函数。
- `erp-ui/src/views/drive/index.vue` — 桌面云盘页面编排。
- `erp-ui/src/views/drive/components/DriveSpaceSidebar.vue` — 空间、最近使用、回收站和容量。
- `erp-ui/src/views/drive/components/DriveToolbar.vue` — 面包屑、搜索、排序、新建和上传按钮。
- `erp-ui/src/views/drive/components/DriveNodeList.vue` — 文件夹/文件列表与行级操作。
- `erp-ui/src/views/drive/components/DriveUploadQueue.vue` — 上传进度、失败和重试。
- `erp-ui/src/views/drive/components/DrivePreviewDialog.vue` — 图片、PDF、文本和不支持预览提示。
- `erp-ui/src/views/drive/components/DriveMoveDialog.vue` — 同空间目录浏览与目标选择。
- `erp-ui/src/views/drive/components/DriveTrashView.vue` — 恢复、彻底删除和清空回收站。
- `erp-ui/src/assets/icons/svg/cloud-drive.svg` — 动态菜单图标。
- `erp-ui/test/cloudDriveApi.test.js`.
- `erp-ui/test/cloudDriveState.test.js`.
- `erp-ui/test/cloudDriveDesktop.test.js`.

**Modify:**

- `erp-ui/src/utils/errorCode.js` only if business codes are mapped there; otherwise keep drive messages in `driveState.js` and do not broaden the global table.
- `erp-ui/test/frontendTestGate.test.js` only if the new source tree requires an explicit numbered-copy assertion update; do not change dependency versions.

### Task 1: Add the API contract and pure state helpers

**Files:** API module, `driveState.js`, API/state tests.

- [ ] **Step 1: Write failing API source-contract tests**

Assert every exported function and exact external path/method:

```js
const contracts = [
  ["listDriveSpaces", "url: '/file/drive/spaces'", "method: 'get'"],
  ["listDriveNodes", "url: '/file/drive/nodes'", "method: 'get'"],
  ["getDriveNode", "url: '/file/drive/nodes/' + nodeId", "method: 'get'"],
  ["createDriveFolder", "url: '/file/drive/folders'", "method: 'post'"],
  ["uploadDriveFile", "url: '/file/drive/files'", "method: 'post'"],
  ["renameDriveNode", "url: '/file/drive/nodes/' + nodeId + '/name'", "method: 'put'"],
  ["moveDriveNode", "url: '/file/drive/nodes/' + nodeId + '/move'", "method: 'put'"],
  ["trashDriveNode", "url: '/file/drive/nodes/' + nodeId", "method: 'delete'"],
  ["listRecentDriveNodes", "url: '/file/drive/recent'", "method: 'get'"],
  ["listDriveTrash", "url: '/file/drive/trash'", "method: 'get'"],
  ["restoreDriveNode", "url: '/file/drive/trash/' + nodeId + '/restore'", "method: 'post'"],
  ["purgeDriveNode", "url: '/file/drive/trash/' + nodeId", "method: 'delete'"],
  ["emptyDriveTrash", "url: '/file/drive/trash'", "method: 'delete'"],
  ["getDriveContent", "url: '/file/drive/nodes/' + nodeId + '/content'", "responseType: 'blob'"],
  ["updateDriveQuota", "url: '/file/drive/spaces/' + spaceId + '/quota'", "method: 'put'"]
]
```

`cloudDriveState.test.js` must test `formatBytes`, `normalizeRouteState`, `sortNodes`, `createUploadItem`, `updateUploadProgress`, `markUploadFailed`, `markUploadDone`, `isPreviewable`, `driveErrorMessage`, and asynchronous `parseDriveBlobError` for an Axios error whose `response.data` is a JSON Blob.

The API contract test also asserts both multipart upload and Blob content requests set `timeout: 0`; ordinary JSON calls keep the repository's default timeout.

- [ ] **Step 2: Run RED**

```bash
cd erp-ui
npm test -- cloudDriveApi.test.js cloudDriveState.test.js
```

Expected: missing modules/functions.

- [ ] **Step 3: Implement exact API behavior**

The multipart function must preserve caller progress and disable the normal 10-second timeout:

```js
export function uploadDriveFile(file, spaceId, parentId, onUploadProgress) {
  const data = new FormData()
  data.append("file", file)
  data.append("spaceId", spaceId)
  data.append("parentId", parentId == null ? 0 : parentId)
  return request({
    url: '/file/drive/files',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    onUploadProgress
  })
}

export function getDriveContent(nodeId, mode) {
  return request({
    url: '/file/drive/nodes/' + nodeId + '/content',
    method: 'get',
    params: { mode },
    responseType: 'blob',
    timeout: 0
  })
}

export function trashDriveNode(nodeId, version) {
  return request({
    url: '/file/drive/nodes/' + nodeId,
    method: 'delete',
    params: { version }
  })
}
```

`normalizeRouteState` returns `{ spaceId, parentId, view, keyword }`, accepts only `view` values `files|recent|trash`, converts missing/invalid parent to `0`, and never reads selected shop state. `sortNodes` always keeps folders first, then applies allowlisted `name|size|updated` and `asc|desc`. `driveErrorMessage` maps all stable `DRIVE_*` codes from the roadmap to concise Chinese messages and falls back to the server message.

Content requests use `responseType:'blob'`, so error JSON may also arrive as a Blob. Implement:

```js
async function parseDriveBlobError(error) {
  const data = error && error.response && error.response.data
  if (typeof Blob !== "undefined" && data instanceof Blob && /json/i.test(data.type || "")) {
    try {
      const payload = JSON.parse(await data.text())
      return { code: payload.businessCode || payload.code, message: payload.msg || "" }
    } catch (_) {
      return { code: "", message: "" }
    }
  }
  if (data && typeof data === "object" && !(typeof Blob !== "undefined" && data instanceof Blob)) {
    return { code: data.businessCode || data.code || "", message: data.msg || data.message || "" }
  }
  return {
    code: error && (error.businessCode || error.code),
    message: error && error.message
  }
}
```

Keep `driveState.js` CommonJS-compatible for the repository's direct Node tests and export every helper explicitly:

```js
module.exports = {
  formatBytes,
  normalizeRouteState,
  sortNodes,
  createUploadItem,
  updateUploadProgress,
  markUploadFailed,
  markUploadDone,
  isPreviewable,
  driveErrorMessage,
  parseDriveBlobError
}
```

Upload item shape is fixed:

```js
{
  id: `${Date.now()}-${sequence}`,
  file,
  name: file.name,
  size: file.size,
  targetSpaceId: spaceId,
  targetParentId: parentId,
  targetPath: logicalPath,
  progress: 0,
  status: "queued",
  error: ""
}
```

- [ ] **Step 4: Run GREEN**

Expected: both tests print their pass message and the runner reports 0 failed.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/api/drive/index.js erp-ui/src/views/drive/driveState.js erp-ui/test/cloudDriveApi.test.js erp-ui/test/cloudDriveState.test.js
git commit -m "feat(ui): add cloud drive client contracts"
```

### Task 2: Build the desktop shell, spaces, breadcrumbs, list, search, and sorting

**Files:** page, sidebar, toolbar, node list, icon, desktop source-contract test.

- [ ] **Step 1: Write the failing desktop structure test**

The test reads all component sources and asserts:

- `index.vue` name is `CloudDrive` and imports the three components;
- `DriveSpaceSidebar` renders “我的文件 / 公司公共盘 / 部门盘 / 最近使用 / 回收站” from data, not hard-coded duplicate pages;
- `DriveToolbar` exposes `search`, `sort-change`, `create-folder`, and `select-files` events;
- `DriveNodeList` has keyboard-accessible folder open and row actions;
- the page reads `response.rows/response.total`, renders pagination, and resets page 1 on space/folder/search/sort changes;
- icon file exists and contains one `<svg>` with a `viewBox`;
- page loads no selected shop context and does not import `shopContext`.

- [ ] **Step 2: Run RED**

```bash
npm test -- cloudDriveDesktop.test.js
```

Expected: component and icon files do not exist.

- [ ] **Step 3: Implement the page state and read-only browsing flow**

`index.vue` state must be explicit:

```js
data() {
  return {
    spaces: [],
    activeSpaceId: null,
    activeView: "files",
    parentId: 0,
    breadcrumbs: [],
    nodes: [],
    total: 0,
    pageNum: 1,
    pageSize: 50,
    loading: false,
    loadError: "",
    keyword: "",
    sortField: "updated",
    sortDirection: "desc",
    uploadItems: [],
    previewNode: null,
    moveNode: null
  }
}
```

On creation: normalize route query, call `listDriveSpaces`, assign `spaces=response.data || []`, select the requested visible space or first space, then load nodes. Space change resets parent, keyword and page. Folder open clears search, resets page, updates parent and route query. When `parentId != 0`, call `getDriveNode(parentId)`, read its node from `response.data`, and render its ordered `breadcrumbs`; root uses an empty breadcrumb list. Search is debounced 300 ms, resets to page 1, and passes `spaceId`, `parentId`, `keyword`, `sortField`, `sortDirection`, `pageNum`, `pageSize` to the API. Set `nodes=response.rows || []` and `total=Number(response.total || 0)` from the backend `TableDataInfo`; do not infer the total from the current page. Render labelled Element pagination when `total > pageSize`; page changes reload without altering directory/search state, while sort changes reset page 1. A nonblank keyword intentionally searches the whole selected space; `parentId` remains in route/UI state only so clearing the keyword returns to the same folder. Search results show `logicalPath`; opening a search-result folder clears the keyword and navigates to that folder, while a file opens preview without changing the saved directory. Server remains authoritative for sorting; client `sortNodes` is only a deterministic fallback for unpaged recent/trash responses.

Page route synchronization:

```js
this.$router.replace({
  path: this.$route.path,
  query: { space: String(this.activeSpaceId), parent: String(this.parentId), view: this.activeView, keyword: this.keyword || undefined }
}).catch(() => {})
```

Use business empty states: “当前文件夹暂无文件”“没有找到匹配文件”“文件服务暂不可用，请稍后重试”。 A failed load keeps the previous `nodes` array and shows an alert; it must not replace the list with an empty success state.

Create the icon with this exact safe SVG (no script, style or external reference):

```svg
<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
  <path d="M6 5h5l2 2h5a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm0 2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-5.8l-2-2H6Z"/>
</svg>
```

- [ ] **Step 4: Run GREEN and visually inspect at 1440x900 and 1024x768**

Expected: header/top navigation remains usable, left panel does not collapse into the list, long Chinese names truncate with tooltip, and all clickable controls have visible focus.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/drive/index.vue erp-ui/src/views/drive/components/DriveSpaceSidebar.vue erp-ui/src/views/drive/components/DriveToolbar.vue erp-ui/src/views/drive/components/DriveNodeList.vue erp-ui/src/assets/icons/svg/cloud-drive.svg erp-ui/test/cloudDriveDesktop.test.js
git commit -m "feat(ui): add desktop cloud drive shell"
```

### Task 3: Add folder creation and the retryable upload queue

**Files:** page, toolbar, upload queue, desktop/state tests.

- [ ] **Step 1: Add failing upload-queue tests**

Test that selecting three files creates three queued items; each item captures its selection-time space, parent and logical destination; navigating elsewhere before a queued upload starts does not retarget it; uploader runs at most two simultaneously; progress clamps to 0–100; one failure does not stop remaining items; retry resets only the failed item; completed items never upload again after a route refresh; write controls are hidden when active space `canWrite=false`.

Source-contract assertions must include `accept` for the allowed extensions and reject any use of the legacy `/file/upload` URL.

- [ ] **Step 2: Run RED**

Run desktop/state tests; expect missing queue component and handlers.

- [ ] **Step 3: Implement exact queue transitions**

Queue states are `queued|uploading|done|failed`. The scheduler is deterministic:

```js
pumpUploads() {
  const running = this.uploadItems.filter(item => item.status === "uploading").length
  const slots = Math.max(0, 2 - running)
  this.uploadItems.filter(item => item.status === "queued").slice(0, slots).forEach(this.startUpload)
}
```

Selection calls `createUploadItem(file, activeSpaceId, parentId, currentLogicalPath)` and the queue displays that destination. `startUpload` calls `uploadDriveFile(item.file, item.targetSpaceId, item.targetParentId, event => progress)`. On success mark done, reload spaces, reload the current list only when it still matches the item's target, then pump. On failure retain the item/file, await `parseDriveBlobError(error)`, pass its code/message to `driveErrorMessage`, mark failed, then pump. Retry changes only that item back to queued and keeps the original target. Remove is allowed only for done/failed/queued, not uploading.

New folder uses `this.$prompt`, trims through the backend contract, captures the current `spaceId/parentId` before awaiting, sends `{ spaceId, parentId, name }`, parses structured errors through `parseDriveBlobError`, reports `DRIVE_NAME_CONFLICT`, and reloads only that unchanged destination. The toolbar reads `activeSpace.canWrite`; no `v-hasPermi` is used for personal write because space capability is authoritative.

- [ ] **Step 4: Run GREEN**

Expected: queue tests and desktop contract tests pass; manual upload shows progress and can retry a forced failure.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/drive/index.vue erp-ui/src/views/drive/components/DriveToolbar.vue erp-ui/src/views/drive/components/DriveUploadQueue.vue erp-ui/src/views/drive/driveState.js erp-ui/test/cloudDriveState.test.js erp-ui/test/cloudDriveDesktop.test.js
git commit -m "feat(ui): add cloud drive upload queue"
```

### Task 4: Add authenticated preview, download, rename, and move

**Files:** page, preview dialog, move dialog, node list, desktop tests.

- [ ] **Step 1: Add failing interaction tests**

Assert: image/PDF/text open through `getDriveContent(...,'preview')`; Office click opens the unsupported-preview state with a download button; download uses `getDriveContent(...,'download')` plus `saveAs(blob,node.nodeName)`; Blob error JSON is parsed with `parseDriveBlobError`; every created object URL is revoked on next preview, close and `beforeDestroy`; rename sends current version; move dialog never displays another space; stale version refreshes the current directory.

- [ ] **Step 2: Run RED**

Expected: preview/move components and methods are absent.

- [ ] **Step 3: Implement safe Blob handling and lifecycle actions**

`DrivePreviewDialog` props: `visible`, `node`, `loading`, `error`, `objectUrl`, `textContent`; emits `close` and `download`. Render image with `<img>`, PDF with `<iframe title="文件预览">`, and text with `<pre>{{ textContent }}</pre>` so Vue escapes content. Never use `v-html`.

Preview logic:

```js
async openPreview(node) {
  this.revokePreviewUrl()
  this.previewNode = node
  if (!node.canPreview) return
  try {
    const blob = await getDriveContent(node.nodeId, "preview")
    if (["txt", "csv"].includes((node.extension || "").toLowerCase())) {
      this.previewText = await blob.text()
    } else {
      this.previewObjectUrl = URL.createObjectURL(blob)
    }
  } catch (error) {
    const parsed = await parseDriveBlobError(error)
    this.previewError = driveErrorMessage(parsed.code, parsed.message)
  }
}
```

Download and every rename/move failure use the same asynchronous parser before choosing the user message. `DriveMoveDialog` receives one source node and space ID, offers virtual root, and pages through `listDriveNodes({spaceId,parentId,pageNum,pageSize:100})`, consuming `response.rows` and retaining only folders. It exposes load-more until the backend total is exhausted, disables the source folder plus any candidate whose exact `ancestorIds` token contains the source ID, and emits one target parent ID. Rename/move use Element confirmation, pass `version`, and on `DRIVE_CONCURRENT_MODIFICATION` close the dialog and reload.

- [ ] **Step 4: Run GREEN and check URL cleanup in a browser**

Expected: opening ten previews retains at most one active object URL; closing removes it; downloaded file name matches the logical node name.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/drive/index.vue erp-ui/src/views/drive/components/DriveNodeList.vue erp-ui/src/views/drive/components/DrivePreviewDialog.vue erp-ui/src/views/drive/components/DriveMoveDialog.vue erp-ui/test/cloudDriveDesktop.test.js
git commit -m "feat(ui): add drive preview and file actions"
```

### Task 5: Add recent use, recycle bin, capacity, quota management, and accessibility states

**Files:** page, sidebar, trash view, toolbar/list, desktop tests.

- [ ] **Step 1: Add failing mode and accessibility tests**

Prove: recent mode calls only `listRecentDriveNodes`; recent folder/file/open-location actions switch space and destination correctly; trash calls `listDriveTrash({spaceId})`; delete asks for confirmation and then changes to trash; restore and purge use trash-root ID; purge accepts 202, `PURGING` disables actions and triggers bounded polling, and `PURGE_FAILED` offers retry-purge but no restore; polling is cancelled on mode leave/destroy; clear trash is disabled when empty; quota editor requires `canManageQuota`; sidebar displays used/quota with accessible text; controls have button elements/labels; no action is represented by a click-only `<div>`.

- [ ] **Step 2: Run RED**

Run desktop tests; expect missing trash component/mode handlers.

- [ ] **Step 3: Implement complete modes**

Use one `loadCurrentView` dispatch:

```js
if (this.activeView === "recent") return this.loadRecent()
if (this.activeView === "trash") return this.loadTrash()
return this.loadNodes()
```

In recent mode, clicking a folder switches to its `spaceId`, enters that folder and returns to files view; clicking a file opens the normal authenticated preview. Offer “打开所在位置” for files, which selects the row's space, navigates to `parentId`, clears search and returns to files view. Re-run capability checks from the returned VO and current space rather than assuming a historical log still grants write access.

`loadRecent` and `loadTrash` unwrap `response.data || []`; JSON mutation endpoints likewise read their returned node from `response.data`. Blob endpoints are the only drive calls that return the Blob directly.

Soft delete confirmation says the item remains for 30 days and still occupies capacity, then calls `trashDriveNode(node.nodeId,node.version)`. A concurrent-modification response reloads the directory instead of silently deleting a newer node. Purge/empty confirmations say the action cannot be undone. Their HTTP 202 completion means “已开始清理”: reload trash, show `PURGING` as “正在清理” with actions disabled, and poll trash plus spaces every 2 seconds for at most 60 seconds while the trash view is active. Cancel the timer on view change and `beforeDestroy`; after the bound, stop and tell the user that cleanup continues in the background. Show `PURGE_FAILED` as “清理失败” with retry-purge only because some bytes may already be gone. Restore/purge reload both trash and space capacity. If original parent no longer exists, show the backend success message that the item returned to the space root.

Quota editing is visible only when `space.canManageQuota`; input is in GB, converted with `Math.round(gb * 1024 ** 3)`, must be at least current `usedBytes`, and sends the current space version. Display exact byte values in tooltip so rounded GB does not hide limits. Parse quota errors through the shared helper; on `DRIVE_CONCURRENT_MODIFICATION`, close the editor, reload spaces and explain that usage/quota changed before allowing another edit.

Add `aria-label` to icon-only controls, keyboard Enter/Space folder open, `role="status"`/`aria-live="polite"` for upload progress, and `role="alert"` for load/action failures.

- [ ] **Step 4: Run GREEN and keyboard-only QA**

Expected: Tab reaches spaces, breadcrumbs, search, actions and dialogs in logical order; Esc closes dialogs; focus returns to the invoking button.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/drive/index.vue erp-ui/src/views/drive/components/DriveSpaceSidebar.vue erp-ui/src/views/drive/components/DriveToolbar.vue erp-ui/src/views/drive/components/DriveNodeList.vue erp-ui/src/views/drive/components/DriveTrashView.vue erp-ui/test/cloudDriveDesktop.test.js
git commit -m "feat(ui): complete desktop cloud drive"
```

### Task 6: Desktop regression and production build

**Files:** no production additions unless tests expose a documented defect.

- [ ] **Step 1: Run focused tests**

```bash
cd erp-ui
npm test -- cloudDriveApi.test.js cloudDriveState.test.js cloudDriveDesktop.test.js
```

Expected: 3 run, 0 failed.

- [ ] **Step 2: Run the full frontend gate**

```bash
npm test
```

Expected: every committed Node test passes; no duplicate copied test files.

- [ ] **Step 3: Run production build**

```bash
npm run build:prod
```

Expected: secret scan and Vue production build succeed; no new dependency or engine warning is introduced by cloud drive.

- [ ] **Step 4: Perform real desktop flow QA**

With four identities (ordinary employee, department manager, cloud-drive company manager, admin), verify space visibility, write controls, upload progress, PDF/image/text preview, Office download fallback, search, sort, move, delete, restore, purge and quota editing. Capture only synthetic test files.

- [ ] **Step 5: Record and commit verification changes**

Append results to `docs/superpowers/verification/2026-07-11-cloud-drive.md`. Commit only if the verification file changed:

```bash
git add docs/superpowers/verification/2026-07-11-cloud-drive.md
git commit -m "test: verify desktop cloud drive"
```
