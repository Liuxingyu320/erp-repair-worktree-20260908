const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const statePath = path.join(uiRoot, "src/views/drive/driveState.js")

assert.ok(fs.existsSync(statePath), "cloud drive state helpers should exist")

const {
  formatBytes,
  formatDriveDateTime,
  isDriveLoadCurrent,
  isDriveRequestCanceled,
  isMoveFolderLoadCurrent,
  normalizeRouteState,
  preserveRecentOrder,
  sortNodes,
  createUploadItem,
  clearDriveTimer,
  updateUploadProgress,
  markUploadCanceled,
  markUploadFailed,
  markUploadDone,
  releaseDriveObjectUrl,
  selectUploadStartCandidates,
  isPreviewable,
  driveErrorMessage,
  parseDriveBlobError
} = require(statePath)

assert.strictEqual(formatBytes(0), "0 B")
assert.strictEqual(formatBytes(1024), "1 KB")
assert.strictEqual(formatBytes(1536), "1.5 KB")
assert.strictEqual(formatBytes(5 * 1024 * 1024), "5 MB")
assert.strictEqual(formatBytes(-10), "0 B")
assert.strictEqual(
  formatDriveDateTime("2026-07-13T06:39:56.000Z", { timeZone: "Asia/Shanghai" }),
  "2026-07-13 14:39",
  "drive timestamps should be rendered as concise local wall time instead of raw ISO strings"
)
assert.strictEqual(formatDriveDateTime("not-a-date"), "—")

assert.deepStrictEqual(
  normalizeRouteState({ space: "12", parent: "8", view: "recent", keyword: "预算" }),
  { spaceId: 12, parentId: 8, view: "recent", keyword: "预算" }
)
assert.deepStrictEqual(
  normalizeRouteState({ space: "bad", parent: "-1", view: "admin", keyword: ["x"] }),
  { spaceId: null, parentId: 0, view: "files", keyword: "" }
)
assert.deepStrictEqual(
  normalizeRouteState({ space: "3", parent: "2", view: "trash" }),
  { spaceId: 3, parentId: 2, view: "trash", keyword: "" }
)

const nodes = [
  { nodeId: 4, nodeType: "FILE", nodeName: "z.txt", sizeBytes: 2, updateTime: "2026-01-02T00:00:00Z" },
  { nodeId: 2, nodeType: "FOLDER", nodeName: "B", sizeBytes: 0, updateTime: "2026-01-01T00:00:00Z" },
  { nodeId: 3, nodeType: "FILE", nodeName: "a.txt", sizeBytes: 8, updateTime: "2026-01-03T00:00:00Z" },
  { nodeId: 1, nodeType: "FOLDER", nodeName: "A", sizeBytes: 0, updateTime: "2026-01-04T00:00:00Z" }
]
assert.deepStrictEqual(sortNodes(nodes, "name", "asc").map(node => node.nodeId), [1, 2, 3, 4])
assert.deepStrictEqual(sortNodes(nodes, "size", "desc").map(node => node.nodeId), [1, 2, 3, 4])
assert.deepStrictEqual(sortNodes(nodes, "updated", "desc").map(node => node.nodeId), [1, 2, 3, 4])
assert.deepStrictEqual(nodes.map(node => node.nodeId), [4, 2, 3, 1], "sorting should not mutate caller state")

const recentNodes = [
  { nodeId: 8, nodeType: "FILE", nodeName: "刚访问的旧文件.txt", updateTime: "2024-01-01T00:00:00Z" },
  { nodeId: 9, nodeType: "FOLDER", nodeName: "较早访问的新目录", updateTime: "2026-01-01T00:00:00Z" }
]
const preservedRecentNodes = preserveRecentOrder(recentNodes)
assert.deepStrictEqual(
  preservedRecentNodes.map(node => node.nodeId),
  [8, 9],
  "recent items should preserve the backend access-log order"
)
assert.notStrictEqual(preservedRecentNodes, recentNodes, "recent normalization should not expose caller array mutation")

const loadRequest = {
  sequence: 4,
  spaceId: 3,
  parentId: 21,
  view: "files",
  keyword: "预算",
  sortField: "updated",
  sortDirection: "desc",
  pageNum: 2,
  pageSize: 50
}
assert.strictEqual(isDriveLoadCurrent(loadRequest, { ...loadRequest }), true)
assert.strictEqual(
  isDriveLoadCurrent(loadRequest, { ...loadRequest, sequence: 5 }),
  false,
  "a superseded request should never commit list or breadcrumb state"
)
assert.strictEqual(
  isDriveLoadCurrent(loadRequest, { ...loadRequest, parentId: 0 }),
  false,
  "a delayed child-directory response should not overwrite root state"
)

const moveFolderRequest = {
  sequence: 7,
  visible: true,
  nodeId: 91,
  spaceId: 3,
  parentId: 44,
  pageNum: 1,
  pageSize: 100
}
assert.strictEqual(isMoveFolderLoadCurrent(moveFolderRequest, { ...moveFolderRequest }), true)
assert.strictEqual(
  isMoveFolderLoadCurrent(moveFolderRequest, { ...moveFolderRequest, parentId: 0 }),
  false,
  "a delayed move-folder response should not overwrite a newer root request"
)
assert.strictEqual(
  isMoveFolderLoadCurrent(moveFolderRequest, { ...moveFolderRequest, nodeId: 92 }),
  false,
  "closing and reopening move for another node should invalidate the old response"
)
assert.strictEqual(
  isMoveFolderLoadCurrent(moveFolderRequest, { ...moveFolderRequest, visible: false }),
  false,
  "closed move dialogs should reject every pending response"
)

const file = { name: "季度报表.pdf", size: 2048 }
const item = createUploadItem(file, 7, 9, "部门盘/预算")
assert.strictEqual(item.file, file)
assert.strictEqual(item.name, file.name)
assert.strictEqual(item.size, file.size)
assert.strictEqual(item.targetSpaceId, 7)
assert.strictEqual(item.targetParentId, 9)
assert.strictEqual(item.targetPath, "部门盘/预算")
assert.strictEqual(item.progress, 0)
assert.strictEqual(item.status, "queued")
assert.strictEqual(item.error, "")
assert.match(item.id, /^\d+-\d+$/)

const queued = [
  createUploadItem({ name: "a.txt", size: 1 }, 11, 21, "我的文件/原目录"),
  createUploadItem({ name: "b.txt", size: 2 }, 11, 21, "我的文件/原目录"),
  createUploadItem({ name: "c.txt", size: 3 }, 11, 21, "我的文件/原目录")
]
assert.strictEqual(new Set(queued.map(entry => entry.id)).size, 3)
queued.forEach(entry => {
  assert.strictEqual(entry.status, "queued")
  assert.strictEqual(entry.targetSpaceId, 11)
  assert.strictEqual(entry.targetParentId, 21)
  assert.strictEqual(entry.targetPath, "我的文件/原目录")
})

const queueSnapshot = [
  { id: "running", status: "uploading" },
  { id: "next", status: "queued" },
  { id: "later", status: "queued" },
  { id: "failed", status: "failed" }
]
assert.deepStrictEqual(
  selectUploadStartCandidates(queueSnapshot, 2).map(entry => entry.id),
  ["next"],
  "a two-slot queue should start only one item while another upload is running"
)
assert.deepStrictEqual(
  selectUploadStartCandidates(queueSnapshot.slice(1), 2).map(entry => entry.id),
  ["next", "later"],
  "an idle two-slot queue should start the first two queued items"
)
assert.deepStrictEqual(queueSnapshot.map(entry => entry.status), ["uploading", "queued", "queued", "failed"])

const revokedUrls = []
assert.strictEqual(
  releaseDriveObjectUrl("blob:preview-1", { revokeObjectURL: value => revokedUrls.push(value) }),
  ""
)
assert.deepStrictEqual(revokedUrls, ["blob:preview-1"], "object URLs should be revoked exactly once")
assert.strictEqual(releaseDriveObjectUrl("", { revokeObjectURL: value => revokedUrls.push(value) }), "")
assert.deepStrictEqual(revokedUrls, ["blob:preview-1"], "empty object URLs should not invoke the browser API")

const clearedTimers = []
assert.strictEqual(clearDriveTimer(17, value => clearedTimers.push(value)), null)
assert.strictEqual(clearDriveTimer(null, value => clearedTimers.push(value)), null)
assert.deepStrictEqual(clearedTimers, [17], "timer cleanup should clear each live handle and normalize it to null")

const halfway = updateUploadProgress(item, 49.7)
assert.strictEqual(halfway.progress, 50)
assert.strictEqual(updateUploadProgress(item, 120).progress, 100)
assert.strictEqual(updateUploadProgress(item, -8).progress, 0)
assert.strictEqual(item.progress, 0, "queue transitions should be immutable")

const failed = markUploadFailed({ ...item, status: "uploading" }, "网络中断")
assert.strictEqual(failed.status, "failed")
assert.strictEqual(failed.error, "网络中断")
const canceled = markUploadCanceled({ ...item, status: "uploading", controller: {} })
assert.strictEqual(canceled.status, "canceled")
assert.strictEqual(canceled.error, "")
assert.strictEqual(canceled.controller, null)
const done = markUploadDone({ ...item, status: "uploading", progress: 82 })
assert.strictEqual(done.status, "done")
assert.strictEqual(done.progress, 100)
assert.strictEqual(done.error, "")

assert.strictEqual(isPreviewable({ canPreview: true, extension: "PDF" }), true)
assert.strictEqual(isPreviewable({ canPreview: true, extension: "docx" }), false)
assert.strictEqual(isPreviewable({ canPreview: false, extension: "png" }), false)

assert.strictEqual(driveErrorMessage("DRIVE_NAME_CONFLICT"), "同一位置已存在同名文件")
assert.strictEqual(driveErrorMessage("DRIVE_QUOTA_EXCEEDED"), "云盘空间不足")
assert.strictEqual(driveErrorMessage("UNKNOWN", "服务返回信息"), "服务返回信息")
assert.strictEqual(driveErrorMessage("UNKNOWN"), "操作失败，请稍后重试")
assert.strictEqual(isDriveRequestCanceled({ code: "ERR_CANCELED" }), true)
assert.strictEqual(isDriveRequestCanceled({ name: "AbortError" }), true)
assert.strictEqual(isDriveRequestCanceled({ code: "ERR_NETWORK" }), false)

async function run() {
  const blobError = {
    response: {
      data: new Blob(
        [JSON.stringify({ businessCode: "DRIVE_ACCESS_DENIED", msg: "拒绝访问" })],
        { type: "application/json;charset=UTF-8" }
      )
    }
  }
  assert.deepStrictEqual(
    await parseDriveBlobError(blobError),
    { code: "DRIVE_ACCESS_DENIED", message: "拒绝访问" }
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ response: { data: { code: "DRIVE_DISABLED", message: "关闭" } } }),
    { code: "DRIVE_DISABLED", message: "关闭" }
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ response: { status: 503, data: { code: 503, msg: "内部服务器错误" } } }),
    { code: "DRIVE_STORAGE_UNAVAILABLE", message: "内部服务器错误" },
    "gateway 5xx responses should become the stable cloud-drive availability error"
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ response: { status: 200, data: { code: 500, msg: "内部服务器错误" } } }),
    { code: "DRIVE_STORAGE_UNAVAILABLE", message: "内部服务器错误" },
    "wrapped gateway 5xx payloads should not leak generic internal-error copy"
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ response: { status: 401, data: { code: 401, msg: "令牌不能为空" } } }),
    { code: "DRIVE_SESSION_EXPIRED", message: "令牌不能为空" },
    "authentication failures should normalize to user-facing cloud-drive session guidance"
  )
  assert.strictEqual(
    driveErrorMessage("DRIVE_SESSION_EXPIRED", "令牌不能为空"),
    "登录状态已失效，请重新登录"
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ code: "ERR_NETWORK", message: "network error" }),
    { code: "ERR_NETWORK", message: "network error" }
  )
  assert.deepStrictEqual(
    await parseDriveBlobError({ response: { data: new Blob(["not-json"], { type: "application/json" }) } }),
    { code: "", message: "" }
  )
  console.log("cloudDriveState tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
