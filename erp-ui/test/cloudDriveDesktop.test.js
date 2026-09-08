const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), "utf8")
const exists = relativePath => fs.existsSync(path.join(uiRoot, relativePath))

const files = {
  page: "src/views/drive/index.vue",
  sidebar: "src/views/drive/components/DriveSpaceSidebar.vue",
  toolbar: "src/views/drive/components/DriveToolbar.vue",
  list: "src/views/drive/components/DriveNodeList.vue",
  uploadQueue: "src/views/drive/components/DriveUploadQueue.vue",
  preview: "src/views/drive/components/DrivePreviewDialog.vue",
  move: "src/views/drive/components/DriveMoveDialog.vue",
  trash: "src/views/drive/components/DriveTrashView.vue",
  icon: "src/assets/icons/svg/cloud-drive.svg"
}

Object.entries(files).forEach(([label, relativePath]) => {
  assert.ok(exists(relativePath), `${label} cloud-drive source should exist`)
})

const page = read(files.page)
const sidebar = read(files.sidebar)
const toolbar = read(files.toolbar)
const list = read(files.list)
const uploadQueue = read(files.uploadQueue)
const preview = read(files.preview)
const move = read(files.move)
const trashView = read(files.trash)
const icon = read(files.icon)
const loadNodesSource = page.slice(page.indexOf('async loadNodes()'), page.indexOf('async loadRecent()'))
const handleSearchSource = page.slice(page.indexOf('handleSearch(value)'), page.indexOf('handleSortChange(sort)'))

assert.ok(page.includes("name: 'CloudDrive'"), "desktop page should have a stable component name")
assert.ok(
  page.includes("import DriveSpaceSidebar") &&
    page.includes("import DriveToolbar") &&
    page.includes("import DriveNodeList"),
  "desktop page should compose the space, toolbar and node-list components"
)

assert.ok(
  sidebar.includes('v-for="space in spaces"') &&
    sidebar.includes("我的文件") &&
    sidebar.includes("公司公共盘") &&
    sidebar.includes("部门盘") &&
    sidebar.includes("最近使用") &&
    sidebar.includes("回收站") &&
    sidebar.includes("<button"),
  "space sidebar should render backend spaces and special views as accessible navigation"
)

for (const eventName of ["search", "sort-change", "create-folder", "select-files"]) {
  assert.ok(toolbar.includes(`$emit('${eventName}'`), `toolbar should expose ${eventName}`)
}
assert.ok(
  toolbar.includes("breadcrumbs") && toolbar.includes("accept=") && toolbar.includes("<button"),
  "toolbar should provide breadcrumbs, an allowlisted file picker and keyboard-native controls"
)

assert.ok(
  list.includes("<button") &&
    list.includes("aria-label") &&
    list.includes("logicalPath") &&
    !list.includes("@keydown.enter") &&
    !list.includes("@keydown.space"),
  "native node buttons should remain keyboard-accessible without duplicate key handlers"
)
assert.ok(
  list.includes("const { formatBytes, formatDriveDateTime, isPreviewable } = require('../driveState')") &&
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
  toolbar.includes('tabindex="-1"') && toolbar.includes('aria-hidden="true"') && toolbar.includes('hidden'),
  'desktop hidden file picker should not duplicate the visible upload control for keyboard or screen-reader users'
)
assert.ok(
  list.includes('formatDriveDateTime') &&
    list.includes('formatDriveDateTime(scope.row.updateTime || scope.row.createTime)'),
  'desktop file timestamps should be concise local date-times rather than raw backend ISO strings'
)
assert.ok(
  page.includes('@click="retryLoad"') &&
    page.includes('async retryLoad()') &&
    page.includes('resetVisibleContent()') &&
    page.includes('this.resetVisibleContent()'),
  'desktop loading failures should be retryable and context changes should clear stale file rows'
)
assert.ok(
  loadNodesSource.includes('this.nodes = []') &&
    loadNodesSource.includes('this.total = 0') &&
    !loadNodesSource.includes('previousNodes') &&
    !loadNodesSource.includes('this.nodes = previousNodes'),
  'desktop list requests must not restore rows that belong to an older query, sort or page after failure'
)
assert.ok(
  (page.match(/this\.clearListResults\(\{ preserveBreadcrumbs: true \}\)/g) || []).length >= 3,
  'desktop search, sort and pagination changes should clear mismatched rows while preserving valid breadcrumbs'
)
assert.ok(
  handleSearchSource.includes('this.syncRoute()'),
  'desktop search should replace the route immediately even when the list request later fails'
)
assert.ok(
  page.includes('cloud-drive-empty__actions') &&
    page.includes(`v-else-if="!loadError && activeView === 'files'`) &&
    page.includes('openFilePickerFromEmptyState') &&
    page.includes('ref="driveToolbar"'),
  'writable empty folders should offer direct create and upload actions without suggesting them during service failures'
)

assert.ok(
  page.includes("response.rows || []") &&
    page.includes("Number(response.total || 0)") &&
    page.includes("<el-pagination") &&
    page.includes(":current-page=\"pageNum\"") &&
    page.includes("@current-change=\"handlePageChange\""),
  "desktop browsing should preserve backend pagination metadata"
)
assert.ok(
  (page.match(/this\.pageNum = 1/g) || []).length >= 4 &&
    page.includes("pageSize: this.pageSize") &&
    page.includes("sortField: this.sortField") &&
    page.includes("sortDirection: this.sortDirection"),
  "space, folder, search and sort changes should reset page one and send allowlisted paging state"
)
assert.ok(
  page.includes("getDriveNode(parentId)") &&
    page.includes("response.data.breadcrumbs") &&
    page.includes("isCurrentLoadRequest(request)") &&
    page.includes("setTimeout") &&
    page.includes(", 300)"),
  "folder breadcrumbs should commit only with the matching backend list request and search should stay debounced"
)
assert.ok(
  page.includes('beforeRouteUpdate(to, from, next)') &&
    page.includes('async applyRouteState(routeState)') &&
    page.includes('isRouteStateCurrent(routeState)') &&
    page.includes('if (this.applyingRouteState) return') &&
    (page.match(/this\.syncRoute\('push'\)/g) || []).length >= 5,
  'desktop user navigation should create route history while browser back/forward reapplies the routed drive state'
)
assert.ok(
  page.includes("当前文件夹暂无文件") &&
    page.includes("没有找到匹配文件") &&
    page.includes("文件服务暂不可用，请稍后重试") &&
    page.includes("role=\"alert\""),
  "desktop page should distinguish empty, no-match and unavailable states"
)
assert.ok(
  !page.includes("shopContext") && !page.includes("selectedShop") && !page.includes("selectedDept"),
  "cloud drive route state must not depend on selected inventory-shop context"
)

assert.ok(
  page.includes("import DriveUploadQueue") &&
    page.includes("createUploadItem(file, targetSpaceId, targetParentId, targetPath)") &&
    page.includes("item.targetSpaceId") &&
    page.includes("item.targetParentId") &&
    page.includes("uploadDriveFile(") &&
    page.includes("onUploadProgress"),
  "selected files should retain their selection-time destination and progress callback"
)
assert.ok(
  page.includes("pumpUploads()") &&
    page.includes("selectUploadStartCandidates(this.uploadItems, 2)") &&
    page.includes("markUploadDone") &&
    page.includes("markUploadFailed") &&
    page.includes("parseDriveBlobError"),
  "upload queue should run two items, isolate failures and continue pumping"
)
assert.ok(
  page.includes('new AbortController()') &&
    page.includes('controller.signal') &&
    page.includes('cancelUpload(itemId)') &&
    page.includes('cancelAllUploads()') &&
    page.includes('isDriveRequestCanceled(error)') &&
    read('src/api/drive/index.js').includes('signal') &&
    uploadQueue.includes("$emit('cancel'") &&
    uploadQueue.includes("canceled: '已取消'"),
  'desktop uploads should support per-task cancellation and abort every live request on teardown'
)
assert.ok(
  page.includes("retryUpload(itemId)") &&
    page.includes("item.status !== 'failed'") &&
    page.includes("removeUpload(itemId)") &&
    page.includes("item.status !== 'uploading'") &&
    page.includes("item.status === 'done'"),
  "retry/remove behavior should target one eligible queue item and never resubmit completed items"
)
assert.ok(
  page.includes("createDriveFolder") &&
    page.includes("this.$prompt") &&
    page.includes("const targetSpaceId = this.activeSpaceId") &&
    page.includes("const targetParentId = this.parentId") &&
    page.includes("DRIVE_NAME_CONFLICT"),
  "new-folder flow should capture its destination and surface structured name conflicts"
)
assert.ok(
  uploadQueue.includes('role="status"') &&
  uploadQueue.includes('aria-live="polite"') &&
    uploadQueue.includes("$emit('cancel'") &&
    uploadQueue.includes("$emit('retry'") &&
    uploadQueue.includes("$emit('remove'") &&
    uploadQueue.includes("item.targetPath"),
  "upload queue should expose progress, destination, retry and removal accessibly"
)
assert.ok(
  toolbar.includes("v-if=\"activeView === 'files' && canWrite\"") &&
    toolbar.includes(".doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.txt,.csv,.jpg,.jpeg,.png,.gif,.webp,.heic,.heif,.zip,.rar,.7z"),
  "write controls should follow active-space capability and the upload picker should allow only supported types"
)
assert.ok(
  ![page, toolbar, uploadQueue, read("src/api/drive/index.js")].some(source => source.includes("'/file/upload'")),
  "cloud drive must never call the legacy public upload endpoint"
)

assert.ok(
  page.includes("import DrivePreviewDialog") &&
    page.includes("import DriveMoveDialog") &&
    page.includes("getDriveContent(node.nodeId, 'preview')") &&
    page.includes("getDriveContent(node.nodeId, 'download')") &&
    page.includes("saveAs(blob, node.nodeName)"),
  "preview and download should use authenticated Blob endpoints and preserve the logical file name"
)
assert.ok(
  page.includes("async openPreview(node)") &&
    page.includes("await blob.text()") &&
    page.includes("URL.createObjectURL(blob)") &&
    page.includes("releaseDriveObjectUrl(this.previewObjectUrl, URL)") &&
    page.includes("this.revokePreviewUrl()") &&
    page.includes("beforeDestroy()"),
  "preview should support text/object URLs and revoke every object URL on replacement or teardown"
)
assert.ok(
  page.includes("renameDriveNode(node.nodeId") &&
    page.includes("version: node.version") &&
    page.includes("moveDriveNode(this.moveNode.nodeId") &&
    page.includes("DRIVE_CONCURRENT_MODIFICATION") &&
    page.includes("await this.loadNodes()"),
  "rename and move should use optimistic versions and refresh stale directories"
)
assert.ok(
  list.includes("$emit('rename'") &&
    list.includes("$emit('move'") &&
    list.includes("row.canWrite") &&
    list.includes("handleAction"),
  "node list should expose capability-aware rename and move actions"
)
assert.ok(
  preview.includes('<img') &&
    preview.includes('<iframe') &&
    preview.includes('title="文件预览"') &&
    preview.includes('<pre>{{ textContent }}</pre>') &&
    preview.includes("暂不支持在线预览") &&
    preview.includes("$emit('download'") &&
    !preview.includes("v-html"),
  "preview dialog should safely render image/PDF/text and offer download fallback"
)
assert.ok(
  move.includes("listDriveNodes({") &&
    move.includes("spaceId: this.spaceId") &&
    move.includes("response.rows || []") &&
    move.includes("Number(response.total || 0)") &&
    move.includes("isCurrentFolderRequest(request)") &&
    move.includes("node.nodeType === 'FOLDER'") &&
    move.includes("ancestorIds.includes(sourceId)") &&
    move.includes("pageSize: 100") &&
    move.includes('if (!reset && loadMoreFailed)') &&
    move.includes('this.pageNum = Math.max(1, request.pageNum - 1)') &&
    move.includes("isSameParentTarget") &&
    move.includes("Number(this.node.parentId)") &&
    move.includes("$emit('confirm', this.targetParentId)"),
  "move dialog should page folders only within the source space and block descendant or no-op targets"
)

assert.ok(
  page.includes("if (this.activeView === 'recent') return this.loadRecent()") &&
    page.includes("if (this.activeView === 'trash') return this.loadTrash()") &&
    page.includes("listRecentDriveNodes") &&
    page.includes("this.nodes = preserveRecentOrder(response.data)") &&
    page.includes("listDriveTrash({ spaceId: this.activeSpaceId })") &&
    page.includes("response.data || []"),
  "current-view loading should dispatch exclusively to files, recent or trash APIs"
)
assert.ok(
  page.includes("handleFolderOpen(node)") &&
    page.includes("this.activeSpaceId = node.spaceId") &&
    page.includes("openNodeLocation(node)") &&
    page.includes("this.parentId = Number(node.parentId) || 0") &&
    list.includes("$emit('open-location'") &&
    list.includes("mode === 'recent'"),
  "recent results should open folders/files or navigate to their recorded space location"
)
assert.ok(
  page.includes("全部可访问空间") &&
    page.includes("activeView === 'recent'") &&
    list.includes("nodeContext(scope.row)") &&
    list.includes("node.spaceName") &&
    list.includes("join(' · ')") ,
  "global recent results should clearly identify their cross-space scope and source space"
)
assert.ok(
  page.includes("trashDriveNode(node.nodeId, node.version)") &&
    page.includes("保留 30 天") &&
    page.includes("仍占用云盘容量") &&
    page.includes("restoreDriveNode(node.nodeId)") &&
    page.includes("purgeDriveNode(node.nodeId)") &&
    page.includes("emptyDriveTrash(this.activeSpaceId)"),
  "recycle-bin actions should use root IDs, optimistic soft delete and explicit retention warnings"
)
assert.ok(
  page.includes("startTrashPolling()") &&
    page.includes("setTimeout(poll, 2000)") &&
    page.includes("60 * 1000") &&
    page.includes("clearDriveTimer(this.trashPollTimer, clearTimeout)") &&
    page.includes("cancelTrashPolling()") &&
    page.includes("beforeDestroy()"),
  "accepted purge jobs should poll for at most one minute and cancel on view leave or destroy"
)
assert.ok(
  trashView.includes("正在清理") &&
    trashView.includes("清理失败") &&
    trashView.includes("item.status === 'TRASHED'") &&
    trashView.includes("item.status === 'PURGING'") &&
    trashView.includes("item.status === 'PURGE_FAILED'") &&
    trashView.includes("$emit('restore'") &&
    trashView.includes("$emit('purge'") &&
    trashView.includes("$emit('retry-purge'") &&
    trashView.includes(":disabled=\"!items.length"),
  "trash view should disable in-flight actions and offer retry-only behavior after purge failure"
)
assert.ok(
  page.includes("activeSpace.canManageQuota") &&
    page.includes("Math.round(gb * 1024 ** 3)") &&
    page.includes("quotaBytes: nextQuotaBytes") &&
    page.includes("version: space.version") &&
    page.includes("nextQuotaBytes < Number(space.usedBytes"),
  "quota editor should be capability-gated, byte-accurate and optimistic-lock aware"
)
assert.ok(
  sidebar.includes("aria-label") &&
    sidebar.includes("capacityLabel(space)") &&
    sidebar.includes("space.usedBytes") &&
    sidebar.includes("space.quotaBytes") &&
    uploadQueue.includes('aria-live="polite"') &&
    ![page, sidebar, toolbar, list, uploadQueue, preview, move, trashView]
      .some(source => /<div[^>]*@click=/i.test(source)),
  "capacity and action states should remain accessible without click-only div controls"
)

assert.strictEqual((icon.match(/<svg\b/g) || []).length, 1, "cloud-drive icon should contain one SVG root")
assert.ok(icon.includes('viewBox="0 0 24 24"'), "cloud-drive icon should declare its viewBox")
assert.ok(!/<script|<style|(?:href|src)=/i.test(icon), "cloud-drive icon should contain no active or external content")

console.log("cloudDriveDesktop tests passed")
