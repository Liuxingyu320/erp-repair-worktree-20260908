const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")

function source(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

const routerSource = source("src/router/index.js")
const mobileRouteSource = source("src/views/mobile/mobileRouteDefinitions.js")
const purchaseSource = source("src/views/inventory/purchase/index.vue")
const salesSource = source("src/views/inventory/sales/index.vue")
const purchaseReturnSource = source("src/views/inventory/purchaseReturn/index.vue")
const salesReturnSource = source("src/views/inventory/salesReturn/index.vue")
const salesApiSource = source("src/api/inventory/sales.js")
const fileApiSource = fs.existsSync(path.resolve(uiRoot, "src/api/system/file.js"))
  ? source("src/api/system/file.js")
  : ""
const fileUploadSource = source("src/components/FileUpload/index.vue")
const imageUploadSource = source("src/components/ImageUpload/index.vue")

assert.ok(
  !mobileRouteSource.includes("inventory:stock:list") &&
    mobileRouteSource.includes("inv:stock:list") &&
    routerSource.includes("mobileRouteDefinitions"),
  "router should use backend inventory permission inv:stock:list, not stale inventory:stock:list"
)

;[
  ["purchase", purchaseSource, "inv:purchase:edit", "inv:purchase:add"],
  ["sales", salesSource, "inv:sales:edit", "inv:sales:add"],
  ["purchase return", purchaseReturnSource, "inv:purchaseReturn:edit", "inv:purchaseReturn:add"],
  ["sales return", salesReturnSource, "inv:salesReturn:edit", "inv:salesReturn:add"]
].forEach(([name, viewSource, stalePerm, backendPerm]) => {
  assert.ok(
    !viewSource.includes(stalePerm) && viewSource.includes(backendPerm),
    `${name} draft edit/save controls should align with backend save permission ${backendPerm}`
  )
})

assert.ok(
  !salesApiSource.includes("/inventory/sales/deliver"),
  "sales frontend API should not expose the retired direct sales delivery endpoint"
)

assert.ok(
  fileApiSource.includes("export function deleteFile") &&
    fileApiSource.includes("url: '/file/delete'") &&
    fileApiSource.includes("fileUrl"),
  "frontend should wrap the backend file delete endpoint"
)

assert.ok(
  fileUploadSource.includes("deleteFile") &&
    imageUploadSource.includes("deleteFile"),
  "file and image upload components should call backend deletion when a user removes uploaded files"
)

assert.ok(
  fileUploadSource.includes(".trim().toLowerCase()") &&
    fileUploadSource.includes("this.fileType.map(type => String(type).trim().toLowerCase())"),
  "file upload extension validation should be case insensitive"
)

console.log("frontendCompletenessFixes tests passed")
