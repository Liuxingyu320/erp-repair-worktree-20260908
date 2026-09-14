const assert = require("node:assert/strict")
const { test } = require("node:test")
const fs = require("node:fs")
const path = require("node:path")
const vm = require("node:vm")
const babel = require("@babel/core")
const compiler = require("vue-template-compiler")
const Vue = require("vue")
const {
  MOBILE_FORM_CONFIG,
  getMobileFormConfig,
  getMobileTransferFormConfig
} = require("../src/views/mobile/feature/mobileFormConfigs")

Vue.config.productionTip = false
Vue.config.silent = true

const REPAIR_ACTION = "/oa/fixedAsset/repair/image/upload"
const DEFAULT_ACTION = "/file/upload"
const repairPagePath = path.resolve(__dirname, "../src/views/oa/fixedAsset/repair/index.vue")
const sheetPath = path.resolve(__dirname, "../src/views/mobile/feature/components/MobileFormSheet.vue")

const ImageUploadStub = {
  name: "ImageUpload",
  props: {
    value: [String, Object, Array],
    action: { type: String, default: DEFAULT_ACTION },
    data: Object,
    disabled: { type: Boolean, default: false },
    deleteOnRemove: { type: Boolean, default: false },
    limit: Number,
    fileSize: Number,
    accept: String,
    capture: String,
    compress: Boolean
  },
  render(h) {
    return h("div", { class: "image-upload-stub" })
  }
}

let currentShop = {}

function plain(value) {
  if (value === undefined) return undefined
  if (value === null) return null
  return JSON.parse(JSON.stringify(value))
}

function transformScript(source, filename) {
  return babel.transformSync(source, {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")]
  }).code
}

function loadSfc(file, requireImpl) {
  const descriptor = compiler.parseComponent(fs.readFileSync(file, "utf8"))
  assert.ok(descriptor.template && descriptor.script, `${file} must have template and script`)
  const compiled = compiler.compile(descriptor.template.content)
  assert.deepEqual(compiled.errors, [], `${file} template should compile: ${compiled.errors.join("\n")}`)
  const module = { exports: {} }
  vm.runInNewContext(transformScript(descriptor.script.content, file), {
    module,
    exports: module.exports,
    require: requireImpl,
    process: { env: {} },
    Promise,
    console
  }, { filename: file })
  return {
    definition: module.exports.default || module.exports,
    compiled
  }
}

function collectImageUploads(vnode, found = []) {
  if (!vnode) return found
  if (Array.isArray(vnode)) {
    vnode.forEach(node => collectImageUploads(node, found))
    return found
  }
  const options = vnode.componentOptions
  const tag = options && options.tag
  const ctorName = options && options.Ctor && options.Ctor.options && options.Ctor.options.name
  if (tag === "image-upload" || tag === "ImageUpload" || ctorName === "ImageUpload") {
    const attrs = (vnode.data && vnode.data.attrs) || {}
    const props = options.propsData || {}
    found.push(snapshotUpload(Object.assign({}, attrs, props)))
  }
  collectImageUploads(vnode.children, found)
  if (options) collectImageUploads(options.children, found)
  return found
}

function snapshotUpload(raw) {
  const deleteOnRemove = raw.deleteOnRemove !== undefined ? raw.deleteOnRemove : raw["delete-on-remove"]
  return {
    action: raw.action,
    data: plain(raw.data == null ? {} : raw.data),
    disabled: raw.disabled === true || raw.disabled === "" || raw.disabled === "true",
    deleteOnRemove: deleteOnRemove === true || deleteOnRemove === ""
  }
}

function attachRender(definition, compiled, extraComponents) {
  return {
    name: definition.name,
    props: definition.props,
    data: definition.data,
    computed: definition.computed,
    methods: definition.methods,
    render: new Function(compiled.render),
    staticRenderFns: compiled.staticRenderFns.map(code => new Function(code)),
    components: Object.assign({}, extraComponents)
  }
}

const repairPage = loadSfc(repairPagePath, id => {
  if (id === "@/utils/shopContext") return { getSelectedDeptContext: () => currentShop }
  if (id === "@/api/oa/fixedAsset") {
    return {
      getFixedAssetQuota: () => Promise.resolve({ data: {} }),
      getFixedAssetRepair: () => Promise.resolve({ data: {} }),
      listFixedAssetConfigs: () => Promise.resolve({ rows: [] }),
      listFixedAssetRepairs: () => Promise.resolve({ rows: [], total: 0 }),
      submitFixedAssetRepairBatch: () => Promise.resolve({})
    }
  }
  if (id === "@/components/ImageUpload") return ImageUploadStub
  if (id === "@/components/ImageGallery") return { render(h) { return h("div") } }
  if (id === "@/utils/businessEmptyState") return { getBusinessEmptyText: () => "" }
  if (id === "@/mixins/todoBusinessFocus") return { createTodoBusinessFocusMixin: () => ({}) }
  if (id === "@/utils/uiOperationScope") return require("../src/utils/uiOperationScope")
  throw new Error("unexpected repair page dependency " + id)
})

const sheetSfc = loadSfc(sheetPath, id => {
  if (id === "@/components/ImageGallery") return { render(h) { return h("div") } }
  if (id === "./MobileEntityPicker.vue") return { render(h) { return h("div") } }
  if (id === "./MobileLineItemsEditor.vue") return { render(h) { return h("div") } }
  if (id === "./mobileOverlayStack") return { mountMobileOverlay() {}, releaseMobileOverlay() {} }
  if (id === "./mobileDialogFocus") {
    return { createMobileDialogFocusManager: () => ({ activate() { return false }, deactivate() { return false } }) }
  }
  if (id === "../mobileReturnSourceOrders") {
    return { createSalesReturnDataFromOrder() { return {} }, createPurchaseReturnDataFromOrder() { return {} } }
  }
  if (id === "@/api/inventory/salesReturn") return { getSalesReturnSourceOrder: () => Promise.resolve({}) }
  if (id === "@/api/inventory/purchaseReturn") return { getPurchaseReturnSourceOrder: () => Promise.resolve({}) }
  if (id === "@/components/ImageUpload") return ImageUploadStub
  if (id === "../mobileValidation") return { isFieldRequired() { return false } }
  if (id === "./mobileFocus") return { focusElementAndVerify() {}, runFocusWithFallback() {} }
  if (id === "../mobileTransferSmartPaste") return { applyMobileTransferSmartPasteRecipient() {} }
  throw new Error("unexpected form sheet dependency " + id)
})

function renderRepairPage(shop) {
  currentShop = shop || {}
  const vm = new Vue(attachRender(repairPage.definition, repairPage.compiled, {
    ImageUpload: ImageUploadStub,
    ImageGallery: { render(h) { return h("div") } }
  }))
  const uploads = collectImageUploads(vm._render())
  vm.$destroy()
  return uploads
}

function mountSheet(config, context) {
  const Ctor = Vue.extend(attachRender(sheetSfc.definition, sheetSfc.compiled, {
    ImageUpload: ImageUploadStub,
    ImageGallery: { render(h) { return h("div") } },
    MobileEntityPicker: { render(h) { return h("div") } },
    MobileLineItemsEditor: { render(h) { return h("div") } }
  }))
  return new Ctor({
    propsData: {
      open: true,
      title: (config && config.title) || "表单",
      feature: { heading: (config && config.title) || "表单" },
      config,
      value: {},
      saving: false,
      iconPaths: { close: "" },
      context: context || {}
    }
  })
}

test("PC repair page wires the dedicated image endpoint with current store data", () => {
  const uploads = renderRepairPage({ deptId: 8, deptName: "一店", isStore: true, deptType: "STORE" })
  assert.equal(uploads.length, 1, "repair dialog should render one image-upload")
  assert.equal(uploads[0].action, REPAIR_ACTION)
  assert.deepEqual(uploads[0].data, { shopDeptId: 8 })
  assert.equal(uploads[0].disabled, false)
  assert.equal(uploads[0].deleteOnRemove, false)
})

test("PC repair page disables the dedicated endpoint without a current store", () => {
  const missing = renderRepairPage({})
  assert.equal(missing.length, 1)
  assert.equal(missing[0].action, REPAIR_ACTION)
  assert.deepEqual(missing[0].data, {})
  assert.equal(missing[0].disabled, true)
  assert.equal(missing[0].deleteOnRemove, false)

  const warehouse = renderRepairPage({ deptId: 5, isStore: false, deptType: "WAREHOUSE" })
  assert.equal(warehouse[0].disabled, true)
  assert.deepEqual(warehouse[0].data, {})
  assert.equal(warehouse[0].action, REPAIR_ACTION)
})

test("PC repair page redraws shopDeptId after switching stores", () => {
  const first = renderRepairPage({ deptId: 8, isStore: true, deptType: "STORE" })
  const second = renderRepairPage({ deptId: 19, isStore: true, deptType: "STORE" })
  assert.deepEqual(first[0].data, { shopDeptId: 8 })
  assert.deepEqual(second[0].data, { shopDeptId: 19 })
  assert.equal(second[0].action, REPAIR_ACTION)
  assert.equal(second[0].disabled, false)
})

test("mobile repair config keeps public image-upload fields and adds the dedicated action", () => {
  const imageField = getMobileFormConfig("fixedAssetRepair").fields.find(field => field.key === "imageUrls")
  assert.equal(imageField.type, "image-upload")
  assert.equal(imageField.limit, 5)
  assert.equal(imageField.fileSize, 5)
  assert.equal(imageField.accept, "image/*")
  assert.equal(imageField.action, REPAIR_ACTION)
  assert.equal(imageField.deleteOnRemove, false)

  const configs = Object.assign({}, MOBILE_FORM_CONFIG, {
    transferWarehouse: getMobileTransferFormConfig("warehouse"),
    transferStoreReturn: getMobileTransferFormConfig("store_return"),
    transferCrossStore: getMobileTransferFormConfig("cross_store")
  })
  for (const [key, config] of Object.entries(configs)) {
    for (const field of (config && config.fields) || []) {
      if (field.type !== "image-upload") continue
      if (key === "fixedAssetRepair" && field.key === "imageUrls") {
        assert.equal(field.action, REPAIR_ACTION)
      } else {
        assert.notEqual(field.action, REPAIR_ACTION, `${key}.${field.key} must not use the repair image endpoint`)
      }
    }
  }
})

test("mobile form sheet uses dedicated action, shop data and local-only remove for repair photos", () => {
  const vm = mountSheet(getMobileFormConfig("fixedAssetRepair"), {
    selectedDeptId: 8,
    selectedDeptType: "STORE",
    selectedDeptName: "一店"
  })
  let uploads = collectImageUploads(vm._render())
  assert.equal(uploads.length, 1)
  assert.equal(uploads[0].action, REPAIR_ACTION)
  assert.deepEqual(uploads[0].data, { shopDeptId: 8 })
  assert.equal(uploads[0].disabled, false)
  assert.equal(uploads[0].deleteOnRemove, false)

  vm.context = { selectedDeptId: 19, selectedDeptType: "STORE", selectedDeptName: "二店" }
  uploads = collectImageUploads(vm._render())
  assert.equal(uploads[0].action, REPAIR_ACTION)
  assert.deepEqual(uploads[0].data, { shopDeptId: 19 })
  assert.equal(uploads[0].disabled, false)

  vm.context = { selectedDeptType: "STORE" }
  uploads = collectImageUploads(vm._render())
  assert.equal(uploads[0].action, REPAIR_ACTION)
  assert.deepEqual(uploads[0].data, {})
  assert.equal(uploads[0].disabled, true)
  vm.$destroy()
})

test("mobile form sheet disables the dedicated endpoint unless the context is a store", () => {
  const vm = mountSheet(getMobileFormConfig("fixedAssetRepair"), {
    selectedDeptId: 8,
    selectedDeptType: "WAREHOUSE",
    selectedDeptName: "仓库"
  })
  const warehouse = collectImageUploads(vm._render())
  assert.equal(warehouse[0].action, REPAIR_ACTION)
  assert.deepEqual(warehouse[0].data, {})
  assert.equal(warehouse[0].disabled, true)

  for (const context of [
    { selectedDeptId: 8 },
    { selectedDeptId: 0, selectedDeptType: "STORE" },
    { selectedDeptId: -1, selectedDeptType: "STORE" },
    { selectedDeptId: 1.5, selectedDeptType: "STORE" },
    { selectedDeptId: "invalid", selectedDeptType: "STORE" }
  ]) {
    vm.context = context
    const disabled = collectImageUploads(vm._render())[0]
    assert.equal(disabled.action, REPAIR_ACTION)
    assert.deepEqual(disabled.data, {})
    assert.equal(disabled.disabled, true)
  }

  vm.context = { selectedDeptId: 8, selectedDeptType: "STORE", selectedDeptName: "一店" }
  const store = collectImageUploads(vm._render())
  assert.deepEqual(store[0].data, { shopDeptId: 8 })
  assert.equal(store[0].disabled, false)
  vm.$destroy()
})

test("mobile form sheet leaves non-repair image fields on the default upload entry", () => {
  const vm = mountSheet({
    title: "其他表单",
    fields: [
      { key: "photos", type: "image-upload", limit: 3, fileSize: 5, accept: "image/*" }
    ]
  }, { selectedDeptId: 8, selectedDeptType: "STORE" })
  const uploads = collectImageUploads(vm._render())
  assert.equal(uploads.length, 1)
  assert.equal(uploads[0].action, DEFAULT_ACTION)
  assert.equal(uploads[0].disabled, false)
  assert.equal(uploads[0].deleteOnRemove, false)
  assert.equal(uploads[0].data && uploads[0].data.shopDeptId, undefined)
  vm.$destroy()
})
