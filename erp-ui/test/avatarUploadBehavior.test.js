const assert = require("assert")
const fs = require("fs")
const vm = require("vm")
const babel = require("@babel/core")
let uploads = [], errors = []
let resolveUpload
const store = { getters: { avatar: "/original.png" }, commit() {} }
class FormDataMock { constructor() { this.entries = [] } append(...args) { this.entries.push(args) } }
class FileReaderMock { readAsDataURL() { this.result = "data:image/png;base64,test"; this.onload() } }
const file = "src/views/system/user/profile/userAvatar.vue"
const source = fs.readFileSync(require("path").resolve(__dirname, "..", file), "utf8")
const code = babel.transformSync(source.match(/<script>([\s\S]*?)<\/script>/)[1], {
  babelrc: false, configFile: false, plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
}).code
const scope = { exports: {} }
vm.runInNewContext(code, {
  exports: scope.exports, module: scope, FormData: FormDataMock, FileReader: FileReaderMock,
  require(name) {
    if (name === "@/store") return { __esModule: true, default: store }
    if (name === "vue-cropper") return { VueCropper: {} }
    if (name === "@/api/system/user") return { uploadAvatar(data) { uploads.push(data); return new Promise(resolve => { resolveUpload = resolve }) } }
    if (name === "@/utils") return { debounce: fn => fn }
    throw new Error("Unexpected dependency " + name)
  }
})
const component = scope.exports.default
const view = { ...component.data(), $modal: { msgError: value => errors.push(value), msgSuccess() {} }, $refs: { cropper: { getCropBlob: fn => fn({ type: "image/png", size: 1024 }) } } }
for (const [name, fn] of Object.entries(component.methods)) view[name] = fn.bind(view)
view.open = true
assert.strictEqual(view.beforeUpload({ name: "phone.HEIC", type: "image/heic", size: 12 }), false)
assert.strictEqual(errors.length, 1)
assert.strictEqual(view.options.img, "/original.png")
view.beforeUpload({ name: "PHOTO.JPG", type: "image/jpeg", size: 5 * 1024 * 1024 })
assert.strictEqual(view.options.filename, "PHOTO.png")
view.uploadImg(); view.uploadImg()
assert.strictEqual(uploads.length, 1, "double click must remain one in-flight upload")
assert.strictEqual(uploads[0].entries[0][0], "avatarfile")
assert.strictEqual(uploads[0].entries[0][2], "PHOTO.png", "PNG crop must not retain original JPEG extension")
resolveUpload({ imgUrl: "/new.png" })
Promise.resolve().then(() => Promise.resolve()).then(() => {
  assert.strictEqual(view.uploading, false)
  assert.strictEqual(view.options.img, "/new.png")
  console.log("avatarUploadBehavior tests passed")
}).catch(error => { console.error(error); process.exitCode = 1 })
