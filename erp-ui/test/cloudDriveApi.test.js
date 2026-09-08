const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const apiPath = path.join(uiRoot, "src/api/drive/index.js")

assert.ok(fs.existsSync(apiPath), "cloud drive API module should exist")

const source = fs.readFileSync(apiPath, "utf8")
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

function functionSource(name) {
  const start = source.indexOf(`export function ${name}`)
  assert.ok(start >= 0, `${name} should be exported`)
  const next = source.indexOf("\nexport function ", start + 1)
  return source.slice(start, next < 0 ? source.length : next)
}

contracts.forEach(([name, url, behavior]) => {
  const body = functionSource(name)
  assert.ok(body.includes(url), `${name} should use ${url}`)
  assert.ok(body.includes(behavior), `${name} should configure ${behavior}`)
})

const upload = functionSource("uploadDriveFile")
assert.ok(
  upload.includes("new FormData()") &&
    upload.includes("data.append('file', file)") &&
    upload.includes("data.append('spaceId', spaceId)") &&
    upload.includes("data.append('parentId', parentId == null ? 0 : parentId)") &&
    upload.includes("onUploadProgress") &&
    upload.includes("timeout: 0"),
  "multipart uploads should keep progress callbacks and disable the default timeout"
)

const content = functionSource("getDriveContent")
assert.ok(
  content.includes("params: { mode }") && content.includes("timeout: 0"),
  "authenticated Blob content should pass mode and disable the default timeout"
)

const trash = functionSource("trashDriveNode")
assert.ok(trash.includes("params: { version }"), "soft delete should send the optimistic-lock version")

assert.ok(
  functionSource("listDriveTrash").includes("params: query"),
  "trash listing should accept an explicit space-scoped query object"
)

assert.ok(
  !functionSource("listDriveSpaces").includes("timeout: 0") &&
    !functionSource("createDriveFolder").includes("timeout: 0"),
  "ordinary JSON calls should retain the shared request timeout"
)

console.log("cloudDriveApi tests passed")
