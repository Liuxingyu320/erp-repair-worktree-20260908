const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("@babel/core")
const { imageUrls } = require("../src/utils/imageGallery")

function loadProgressUtility() {
  const file = path.resolve(__dirname, '../src/utils/uploadProgress.js')
  const code = babel.transformSync(fs.readFileSync(file, 'utf8'), { babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, File: globalThis.File,
    require(id) {
      if (id === '@/utils/shopContext') return { getSelectedDeptId: () => null }
      if (id === 'element-ui/packages/upload/src/ajax') return () => ({ abort() {} })
      throw new Error('Unexpected upload utility dependency ' + id)
    }
  })
  return module.exports
}

const sourcePath = path.resolve(__dirname, "../src/components/ImageUpload/index.vue")
const source = fs.readFileSync(sourcePath, "utf8")
const code = babel.transformSync(source.match(/<script>([\s\S]*?)<\/script>/)[1], {
  babelrc: false, configFile: false, plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
}).code
const moduleScope = { exports: {} }
vm.runInNewContext(code, {
  module: moduleScope, exports: moduleScope.exports, process: { env: {} }, Promise,
  require(id) {
    if (id === "@/utils/uploadProgress") return loadProgressUtility()
    if (id === "@/components/UploadQueue") return {}
    if (id === "@/utils/auth") return { getToken: () => "test" }
    if (id === "@/utils/sessionMode") return {
      buildSessionAuthHeaders: () => ({}), applySessionAuthHeaders() {}, shouldUseSessionCredentials: () => false
    }
    if (id === "@/utils/requestSecurity") return { safeTrustedApiUrl: () => "/inventory/image/upload" }
    if (id === "@/utils/urlSecurity") return require("../src/utils/urlSecurity")
    if (id === "@/api/system/file") return { deleteFile: () => { throw new Error("Reference removal must not delete storage") } }
    if (id === "sortablejs") return { create() {} }
    throw new Error("Unexpected dependency " + id)
  }
})
const component = moduleScope.exports.default
function harness(initial = []) {
  const events = [], errors = []
  const props = { action: "/inventory/image/upload", value: initial, fileSize: 5, fileType: ["jpg", "jpeg", "png"], compress: false, deleteOnRemove: false }
  const instance = Object.assign(component.data.call(props), props, {
    $refs: { imageUpload: { uploadFiles: [], abort() {}, handleRemove() { throw new Error("Element already removed the failure") } } },
    $emit(name, value) { if (name === "input") events.push({ name, value }) },
    $set(target, key, value) { target[key] = value },
    $delete(target, key) { delete target[key] },
    $modal: { loading() {}, closeLoading() {}, msgError(value) { errors.push(value) } }
  })
  for (const [key, fn] of Object.entries(component.methods)) instance[key] = fn.bind(instance)
  instance.fileList = instance.normalizeValue(initial)
  return { instance, events, errors }
}
const file = uid => ({ uid, name: uid + ".PNG", type: "image/png", size: 5 * 1024 * 1024 })
const ok = url => ({ code: 200, data: { url } })
async function main() {
  assert.deepStrictEqual(imageUrls({ imageUrls: ["/b.png", "javascript:bad", "/a.png"] }), ["/b.png", "/a.png"])
  assert.deepStrictEqual(imageUrls({ imageUrl: "/old.png,/second.png" }), ["/old.png", "/second.png"])
  assert.deepStrictEqual(imageUrls({ imageUrls: [], imageUrl: "/stale.png" }), [])
  assert.deepStrictEqual(imageUrls('["/b.png","/a.png"]'), ["/b.png", "/a.png"])
  let h = harness(["/old.png"]), a = file(1), b = file(2)
  await h.instance.handleBeforeUpload(a); await h.instance.handleBeforeUpload(b)
  h.instance.$refs.imageUpload.uploadFiles = [b] // Element's actual network-error behavior
  h.instance.handleUploadError(new Error("offline"), a)
  h.instance.handleUploadSuccess(ok("/b.png"), b)
  assert.deepStrictEqual(Array.from(h.events.at(-1).value), ["/old.png", "/b.png"])
  assert.strictEqual(h.instance.$refs.imageUpload.uploadFiles[0], b)
  h.instance.handleUploadError(new Error("duplicate"), a)
  assert.strictEqual(h.events.length, 1)
  h = harness(); a = file(3); b = file(4)
  await h.instance.handleBeforeUpload(a); await h.instance.handleBeforeUpload(b)
  h.instance.handleUploadSuccess(ok("/b.png"), b)
  h.instance.handleUploadSuccess(ok("/a.png"), a)
  assert.deepStrictEqual(Array.from(h.events.at(-1).value), ["/a.png", "/b.png"], "selection order survives reverse responses")
  h = harness(); a = file(5); b = file(6)
  await h.instance.handleBeforeUpload(a); await h.instance.handleBeforeUpload(b)
  h.instance.handleUploadSuccess(ok("/a.png"), a)
  h.instance.handleDelete(a)
  h.instance.handleUploadSuccess(ok("/b.png"), b)
  assert.deepStrictEqual(Array.from(h.events.at(-1).value), ["/b.png"], "removing early success must not resurrect it")
  h = harness(); a = file(7)
  await h.instance.handleBeforeUpload(a)
  component.beforeDestroy.call(h.instance)
  h.instance.handleUploadSuccess(ok("/late.png"), a)
  assert.strictEqual(h.events.length, 0, "late callbacks from closed form ignored")
  h = harness(["/saved.png"]); a = file(8); b = file(9)
  await h.instance.handleBeforeUpload(a); await h.instance.handleBeforeUpload(b)
  h.instance.handleUploadError(new Error("offline"), a)
  h.instance.handleUploadError(new Error("offline"), b)
  assert.strictEqual(h.instance.number, 0)
  assert.deepStrictEqual(Array.from(h.events.at(-1).value), ["/saved.png"])
  h.instance.handleDelete(h.instance.fileList[0])
  assert.deepStrictEqual(Array.from(h.events.at(-1).value), [])
  assert.strictEqual(h.instance.handleBeforeUpload({ ...file(10), name: "unsupported.webp", type: "image/webp" }), false)
  assert.strictEqual(h.instance.handleBeforeUpload({ ...file(11), size: 5 * 1024 * 1024 + 1 }), false)
  console.log("imageUploadGalleryLifecycle tests passed")
}
main().catch(error => { console.error(error); process.exitCode = 1 })
