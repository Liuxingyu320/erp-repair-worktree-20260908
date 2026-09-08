const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const {
  createCustomerDraft,
  normalizeQuickCustomerPayload,
  mapCreatedCustomerOption,
  getQuickCustomerErrorMessage,
  normalizeCustomerServiceCardContext,
  normalizeCustomerServiceCardCapabilities,
  isCustomerQuickCreateAllowed
} = require("../src/views/mobile/feature/mobileQuickCustomer")

assert.deepStrictEqual(
  createCustomerDraft(" 柏悦客户 "),
  { customerName: "柏悦客户", contactPerson: "", contactPhone: "" },
  "quick customer draft should reuse the trimmed search keyword"
)

assert.deepStrictEqual(
  normalizeQuickCustomerPayload({
    customerName: " 柏悦客户 ",
    contactPerson: " 林店长 ",
    contactPhone: " 13800000000 "
  }),
  {
    customerName: "柏悦客户",
    contactPerson: "林店长",
    contactPhone: "13800000000"
  },
  "quick customer payload should contain only service-card fields supported by the new endpoint"
)

assert.throws(
  () => normalizeQuickCustomerPayload({ customerName: "   " }),
  /客户名称不能为空/,
  "quick customer creation should reject a blank customer name before sending a request"
)

assert.deepStrictEqual(
  mapCreatedCustomerOption({
    customerId: 9,
    customerName: "柏悦客户",
    customerCode: "C-009",
    contactPerson: "林店长",
    contactPhone: "13800000000"
  }),
  {
    value: 9,
    label: "柏悦客户",
    meta: "C-009 · 林店长 · 13800000000",
    row: {
      customerId: 9,
      customerName: "柏悦客户",
      customerCode: "C-009",
      contactPerson: "林店长",
      contactPhone: "13800000000"
    }
  },
  "created customer should map directly to a selectable option"
)

assert.throws(
  () => mapCreatedCustomerOption({ customerName: "没有编号" }),
  /未返回客户编号/,
  "quick customer creation should not select an incomplete API response"
)

assert.strictEqual(
  getQuickCustomerErrorMessage({ response: { data: { msg: "手机号已存在" } } }),
  "手机号已存在",
  "quick customer errors should prefer the backend business message"
)
assert.strictEqual(
  getQuickCustomerErrorMessage({ response: { data: { code: "FEATURE_DISABLED_FOR_SHOP" } } }),
  "当前门店暂未开放客户新建，请联系管理员",
  "feature-disabled backend codes must become safe Chinese guidance"
)

const enabledCustomerCapability = normalizeCustomerServiceCardCapabilities(
  { data: { writeEnabled: true } },
  "17|STORE"
)
assert.strictEqual(
  isCustomerQuickCreateAllowed({
    entity: "customer",
    field: { quickCreate: true },
    hasPermission: true,
    capability: enabledCustomerCapability,
    contextKey: "17|STORE"
  }),
  true,
  "quick creation requires a resolved writable capability for the current shop"
)
assert.strictEqual(
  isCustomerQuickCreateAllowed({
    entity: "customer",
    field: { quickCreate: true },
    hasPermission: true,
    capability: normalizeCustomerServiceCardCapabilities({ data: { writeEnabled: false } }, "17|STORE"),
    contextKey: "17|STORE"
  }),
  false,
  "read-only shop capabilities must fail closed"
)
assert.strictEqual(
  isCustomerQuickCreateAllowed({
    entity: "customer",
    field: { quickCreate: true },
    hasPermission: true,
    capability: enabledCustomerCapability,
    contextKey: "18|STORE"
  }),
  false,
  "a capability resolved for another shop must not be reused"
)

const malformedPrimaryContext = normalizeCustomerServiceCardContext({
  selectedDeptId: "",
  selectedDeptType: "STORE",
  deptId: "17",
  deptType: "STORE"
})
assert.strictEqual(malformedPrimaryContext.valid, false,
  "an empty primary context must not fall back to a stale legacy organization")
assert.strictEqual(
  normalizeCustomerServiceCardContext({ deptId: "17", deptType: "STORE" }).contextKey,
  "17|STORE",
  "legacy context fields should remain compatible when primary fields are absent"
)

const pickerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileEntityPicker.vue"),
  "utf8"
)
const customerApiSource = fs.readFileSync(
  path.resolve(__dirname, "../src/api/inventory/customer.js"),
  "utf8"
)
const quickFormPath = path.resolve(
  __dirname,
  "../src/views/mobile/feature/components/MobileQuickCustomerForm.vue"
)
assert.ok(fs.existsSync(quickFormPath), "quick customer creation should use an independent lightweight form")
const quickFormSource = fs.readFileSync(quickFormPath, "utf8")

assert.ok(
  pickerSource.includes("MobileQuickCustomerForm") &&
    pickerSource.includes("inv:customerCard:add") &&
    pickerSource.includes("createCustomerServiceCard") &&
    !pickerSource.includes("inv:customer:add") &&
    pickerSource.includes("loadError") &&
    pickerSource.includes("重新加载") &&
    pickerSource.includes("当前门店暂无客户，请联系有权限人员新增"),
  "customer picker should distinguish loading errors, empty permission states, and quick creation"
)

assert.ok(
  quickFormSource.includes("customerName") &&
    quickFormSource.includes("contactPerson") &&
    quickFormSource.includes("contactPhone") &&
    quickFormSource.includes("$emit(\"submit\"") &&
    quickFormSource.includes("mobile-quick-customer-open"),
  "quick customer form should expose only the supported fields and its own overlay lifecycle"
)

assert.ok(
  quickFormSource.includes("createMobileDialogFocusManager") &&
    quickFormSource.includes("activateQuickCustomerFocus") &&
    quickFormSource.includes("deactivateQuickCustomerFocus") &&
    quickFormSource.includes("getQuickCustomerInitialFocus") &&
    quickFormSource.includes("requestClose") &&
    quickFormSource.includes('tabindex="-1"'),
  "quick customer form should trap focus, use its safe close path for Escape, and restore its trigger"
)

assert.strictEqual(
  (quickFormSource.match(
    /this\.\$nextTick\(\(\) => \{\s*if \(!this\.open\) return\s*mountMobileOverlay\("mobile-quick-customer-open"\)/g
  ) || []).length,
  2,
  "quick customer watcher and mounted callbacks should recheck open before locking or activating"
)

assert.ok(
  pickerSource.includes("focusElementAndVerify") &&
    pickerSource.includes("focusSearchInputAfterQuickCreate") &&
    pickerSource.includes("this.$nextTick(this.focusSearchInputAfterQuickCreate)") &&
    pickerSource.includes("focusElementAndVerify(this.$refs.searchInput)"),
  "successful quick customer creation should focus the persistent search input after the create button is removed"
)

assert.ok(
  pickerSource.includes("getCustomerServiceCardCapabilities") &&
    pickerSource.includes("customerCapabilityContextKey") &&
    pickerSource.includes("writeEnabled") &&
    pickerSource.includes("当前门店暂未开放客户新建，请联系管理员"),
  "quick creation must be gated by the current shop capability and fail closed"
)
assert.ok(
  /getCustomerServiceCardCapabilities\(\)[\s\S]*?silentError:\s*true/.test(customerApiSource),
  "customer capability probes must be silent so the picker owns the safe read-only feedback"
)

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function loadPickerComponent(getCapability, fetchOptions) {
  const scriptMatch = pickerSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "mobile entity picker script should exist")
  const script = scriptMatch[1]
    .replace(/^import[^\n]*\n/gm, "")
    .replace(/export default/, "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    getCustomerServiceCardCapabilities: getCapability,
    fetchMobileEntityOptions: fetchOptions,
    MobileQuickCustomerForm: {},
    require(request) {
      if (request === "../mobileQuickCustomer") {
        return require("../src/views/mobile/feature/mobileQuickCustomer")
      }
      if (request === "../mobileEntitySelection") return { shouldClearEntitySelection: () => false }
      if (request === "./mobileSearchKeyboard") return { runMobileSearchEnter: () => true }
      if (request === "./mobileFocus") return { focusElementAndVerify: () => {} }
      throw new Error(`Unexpected picker require: ${request}`)
    },
    Promise,
    Object,
    Array,
    String,
    Number,
    Boolean,
    Math,
    Date,
    Set,
    RegExp,
    TypeError
  }
  vm.runInNewContext(script, sandbox, { filename: "MobileEntityPicker.vue" })
  return sandbox.module.exports
}

const openQuickCustomer = pickerSource.match(
  /openQuickCustomerForm\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*closeQuickCustomerForm/
)
const createQuickCustomer = pickerSource.match(
  /createQuickCustomer\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*focusSearchInputAfterQuickCreate/
)
assert.ok(
  pickerSource.includes('quickCustomerRequestKey: ""') &&
    openQuickCustomer && openQuickCustomer[1].includes("this.quickCustomerRequestKey = `mobile-card-") &&
    createQuickCustomer && createQuickCustomer[1].includes("requestKey: this.quickCustomerRequestKey") &&
    !createQuickCustomer[1].includes("Date.now()"),
  "quick-customer retries should reuse the key created when the form opens"
)

assert.ok(
  /class="quick-customer-error"[^>]*role="alert"[^>]*aria-live="assertive"/.test(quickFormSource) &&
    quickFormSource.includes("height: var(--mobile-viewport-height") &&
    quickFormSource.includes("var(--mobile-keyboard-inset") &&
    /\.quick-customer-body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/.test(quickFormSource) &&
    /class="quick-customer-footer mobile-system-sheet__footer"/.test(quickFormSource) &&
    /\.quick-customer-header button\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/.test(quickFormSource),
  "quick customer feedback should be announced while only its keyboard-safe field body scrolls above fixed actions"
)

;(async () => {
  const capabilityRequests = []
  let optionSearches = 0
  const pickerComponent = loadPickerComponent(
    () => {
      const request = deferred()
      capabilityRequests.push(request)
      return request.promise
    },
    () => {
      optionSearches += 1
      return Promise.resolve([{ value: "customer-1", label: "客户一" }])
    }
  )
  const picker = {
    ...pickerComponent.data(),
    entity: "customer",
    field: { quickCreate: true },
    context: {
      selectedDeptId: "17",
      selectedDeptType: "STORE",
      permissions: ["inv:customerCard:add"]
    },
    formData: {},
    value: "",
    label: "客户",
    $emit() {},
    $nextTick(callback) { return Promise.resolve().then(() => callback && callback()) },
    hasAnyPermission: pickerComponent.methods.hasAnyPermission,
    loadCustomerServiceCardCapabilities: pickerComponent.methods.loadCustomerServiceCardCapabilities,
    hydrateKeywordFromOptions: pickerComponent.methods.hydrateKeywordFromOptions,
    searchOptions: pickerComponent.methods.searchOptions,
    openQuickCustomerForm: pickerComponent.methods.openQuickCustomerForm
  }
  Object.defineProperty(picker, "customerCapabilityContextKey", {
    configurable: true,
    get() { return pickerComponent.computed.customerCapabilityContextKey.call(picker) }
  })
  Object.defineProperty(picker, "canQuickCreateCustomer", {
    configurable: true,
    get() { return pickerComponent.computed.canQuickCreateCustomer.call(picker) }
  })

  const oldContextKey = picker.customerCapabilityContextKey
  const oldCapabilityRequest = picker.loadCustomerServiceCardCapabilities()
  const optionSearch = picker.searchOptions()
  picker.quickCustomerOpen = true
  picker.quickCustomerError = "旧门店错误"
  picker.context = {
    selectedDeptId: "18",
    selectedDeptType: "STORE",
    permissions: ["inv:customerCard:add"]
  }
  const newContextKey = picker.customerCapabilityContextKey
  pickerComponent.watch.customerCapabilityContextKey.call(picker, newContextKey, oldContextKey)
  assert.strictEqual(picker.customerCapability.resolved, false,
    "context changes must immediately make capability unresolved")
  assert.strictEqual(picker.customerCapability.writeEnabled, false)
  assert.strictEqual(picker.quickCustomerOpen, false,
    "context changes must close the quick-create form")
  assert.strictEqual(picker.quickCustomerError, "",
    "context changes must clear the stale quick-create error")
  assert.strictEqual(capabilityRequests.length, 2)

  capabilityRequests[0].resolve({ data: { writeEnabled: true } })
  await oldCapabilityRequest
  await Promise.resolve()
  assert.strictEqual(picker.customerCapability.contextKey, "18|STORE",
    "a slow response from 17|STORE must not overwrite the 18|STORE state")
  assert.strictEqual(picker.canQuickCreateCustomer, false)

  capabilityRequests[1].resolve({ data: { writeEnabled: true } })
  await Promise.resolve()
  await Promise.resolve()
  assert.strictEqual(picker.customerCapability.contextKey, "18|STORE")
  assert.strictEqual(picker.customerCapability.writeEnabled, true)
  assert.strictEqual(picker.canQuickCreateCustomer, true,
    "only the newly resolved writable context may enable quick creation")
  await optionSearch
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.strictEqual(optionSearches, 1,
    "capability context switching must not block or duplicate options search")
  assert.strictEqual(picker.options.length, 1,
    "customer options must remain available independently of capability probes")
})().then(() => {
  console.log("mobileQuickCustomer tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
