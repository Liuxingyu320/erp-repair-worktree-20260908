const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), "utf8")

const productPage = read("src/views/inventory/product/index.vue")
const giftPage = read("src/views/inventory/gift/index.vue")
const oePage = read("src/views/inventory/oe/index.vue")
const imageUpload = read("src/components/ImageUpload/index.vue")

const productImageFields = [
  "imageUrl",
  "packageImageUrl",
  "dryTeaImageUrl",
  "teaSoupImageUrl",
  "leafBottomImageUrl",
  "extraImageUrl"
]

for (const field of productImageFields) {
  assert.ok(
    productPage.includes(`<image-upload v-model="form.${field}"`),
    `product ${field} should use the shared click-to-upload component`
  )
  assert.ok(
    !productPage.includes(`<el-input v-model="form.${field}"`),
    `product ${field} should no longer ask users to enter an image URL`
  )
}

assert.ok(
  productPage.includes(':preview-src-list="productImagePreviewUrls"'),
  "product details should preview uploaded images instead of exposing raw URLs"
)

for (const [name, source] of [["gift", giftPage], ["OE", oePage]]) {
  assert.ok(
    source.includes('v-model="form.imageUrls"'),
    `${name} image maintenance should use click-to-upload`
  )
  assert.ok(
    !source.includes('<el-input v-model="form.imageUrl"'),
    `${name} image maintenance should no longer expose a URL input`
  )
}

assert.ok(
  productPage.includes('action="/inventory/image/upload"') &&
    productPage.includes(':delete-on-remove="false"') &&
    productPage.includes('accept=".jpg,.jpeg,.png,image/jpeg,image/png"'),
  "inventory image fields should use the permission-scoped image endpoint without deleting saved files during form edits"
)

assert.ok(
  imageUpload.includes("buildSessionAuthHeaders(getToken())") &&
    imageUpload.includes(':with-credentials="withCredentials"') &&
    imageUpload.includes("if (this.number > 0)") &&
    imageUpload.includes("this.uploadedSuccessfully()"),
  "the shared uploader should keep authentication and recover its queue after a failed upload"
)

console.log("inventoryImageUploadUx tests passed")
