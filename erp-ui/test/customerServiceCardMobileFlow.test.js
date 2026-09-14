const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const { mapMobileFeatureRows } = require("../src/views/mobile/feature/featureMapper")
const { getMobileFeatureActions } = require("../src/views/mobile/feature/featureActions")
const { getMobileFormConfig } = require("../src/views/mobile/feature/mobileFormConfigs")
const { createMobileFormData, buildMobileFormPayload } = require("../src/views/mobile/feature/mobileFormPayloads")
const { createMobileActionRuntime } = require("../src/views/mobile/feature/featureActionRuntime")
const {
  MOBILE_ACTION_PERMISSIONS,
  MOBILE_FORM_CREATE_PERMISSIONS,
  MOBILE_FORM_EDIT_PERMISSIONS
} = require("../src/views/mobile/feature/mobileFeaturePolicy")
const {
  createCustomerServiceRecordForm,
  validateCustomerServiceRecord,
  buildCustomerServiceRecordPayload
} = require("../src/views/mobile/feature/mobileCustomerServiceRecord")
const { getWorkbenchActionDetails } = require("../src/views/mobile/components/mobileWorkbenchPolicy")

const routes = readUi("src/views/mobile/mobileRouteDefinitions.js")
const service = readUi("src/views/mobile/feature/featureService.js")
const actionService = readUi("src/views/mobile/feature/featureActionService.js")
const page = readUi("src/views/mobile/feature/index.vue")
const customerPage = readUi("src/views/mobile/customer/index.vue")
const customerApi = readUi("src/api/inventory/customer.js")
const search = readUi("src/views/mobile/feature/featureSearchConfigs.js")
const picker = readUi("src/views/mobile/feature/components/MobileEntityPicker.vue")

assert.ok(
  routes.includes("path: '/mobile/customer'") && routes.includes("featureKey: 'customer'") &&
    routes.includes("@/views/mobile/customer/index") &&
    routes.includes("allowedDeptTypes: ['STORE']") && routes.includes("inv:customerCard:list") &&
    routes.includes("客户服务卡"),
  "mobile customer entry should be a store-only service-card page"
)
assert.deepStrictEqual(
  getWorkbenchActionDetails("STORE", "客户资料"),
  {
    icon: "user",
    tone: "teal",
    path: "/mobile/customer",
    summary: "查客户",
    permissions: ["inv:customerCard:list"]
  },
  "mobile workbench should expose customer cards through the dedicated permission"
)
assert.ok(
  service.includes("listCustomerServiceCards") && service.includes("getCustomerServiceCard") &&
    !service.includes("resolveList(listCustomer(query))"),
  "mobile list/detail should use service-card DTOs instead of the legacy financial customer endpoint"
)

const mapped = mapMobileFeatureRows("customer", [{
  customerId: 9,
  customerName: "柏悦客户",
  customerCode: "C-009",
  contactPhone: "13800000000",
  teaPreferences: "岩茶",
  brewingServicePreferences: "先淡后浓",
  cautions: "忌浓茶",
  budgetMin: 100,
  budgetMax: 200,
  photoNodeId: 81,
  status: "0",
  serviceRecords: [{ serviceDate: "2026-07-13 10:00:00", teaServed: "肉桂", partySize: 2, serviceNote: "喜欢盖碗" }]
}])[0]

assert.ok(mapped.detail.includes("岩茶") && mapped.detail.includes("忌浓茶"), "mobile cards should summarize tea preferences and cautions")
assert.ok(mapped._detailFields.some(field => field.label === "人均预算" && field.value.includes("¥100.00")), "mobile detail should show per-person budget")
assert.ok(mapped._detailFields.some(field => field.label === "客户照片"), "mobile detail should disclose that a controlled photo is available")
assert.deepStrictEqual(mapped._detailSections.map(section => section.title), ["历史服务记录"], "mobile detail should render service history")

assert.deepStrictEqual(
  getMobileFeatureActions("customer", mapped).map(action => action.id),
  ["addCustomerServiceRecord", "editCustomer", "previewCustomerPhoto"],
  "active mobile customer cards should expose service recording, scoped editing and photo viewing"
)

const config = getMobileFormConfig("customer")
assert.ok(config && config.idKey === "customerId" && config.passthroughFields.includes("version"), "mobile customer form should preserve optimistic-lock metadata")
const form = createMobileFormData(config, {
  customerId: 9,
  customerName: "柏悦客户",
  teaPreferences: "岩茶",
  photoNodeId: 81,
  version: 3
})
const payload = buildMobileFormPayload(config, form, { submitAction: "save" })
assert.strictEqual(payload.customerId, 9)
assert.strictEqual(payload.photoNodeId, 81)
assert.strictEqual(payload.version, 3)
assert.strictEqual(payload.sourceClient, "MOBILE")
assert.ok(/^customer-card-/.test(payload.requestKey), "mobile writes should include a fresh idempotency key")
form.requestKey = "customer-card-retry-key"
assert.strictEqual(
  buildMobileFormPayload(config, form, { submitAction: "save" }).requestKey,
  "customer-card-retry-key",
  "mobile card retries should reuse the key created when the form opened"
)

const calls = []
const runtime = createMobileActionRuntime({
  createCustomerServiceCard: data => { calls.push(["create", data]); return "created" },
  updateCustomerServiceCard: (id, data) => { calls.push(["update", id, data]); return "updated" },
  addCustomerServiceRecord: (id, data) => { calls.push(["record", id, data]); return "recorded" }
})
assert.strictEqual(runtime.saveMobileFeatureForm("customer", { customerName: "新客户", submitAction: "save" }), "created")
assert.strictEqual(runtime.saveMobileFeatureForm("customer", { customerId: 9, customerName: "柏悦客户", version: 3, submitAction: "save" }), "updated")
assert.deepStrictEqual(calls, [
  ["create", { customerName: "新客户" }],
  ["update", 9, { customerName: "柏悦客户", version: 3 }]
], "mobile form runtime should route create/update through service-card endpoints without leaking UI-only fields")

const recordForm = createCustomerServiceRecordForm(mapped._raw, new Date(2026, 6, 13, 10, 30))
assert.strictEqual(recordForm.serviceDate, "2026-07-13T10:30")
assert.strictEqual(recordForm.partySize, 1)
assert.strictEqual(recordForm.preferenceSnapshot, "岩茶")
assert.strictEqual(recordForm.cautionSnapshot, "忌浓茶")
assert.strictEqual(validateCustomerServiceRecord({ serviceDate: recordForm.serviceDate, partySize: 0 }), "到店人数必须为大于 0 的整数")
const recordPayload = buildCustomerServiceRecordPayload({
  ...recordForm,
  partySize: 2,
  teaServed: " 肉桂 ",
  consumptionAmount: 380
}, { requestKey: "customer-record-test" })
assert.deepStrictEqual(recordPayload, {
  serviceDate: "2026-07-13 10:30:00",
  partySize: 2,
  teaServed: "肉桂",
  preferenceSnapshot: "岩茶",
  cautionSnapshot: "忌浓茶",
  serviceNote: "",
  consumptionAmount: 380,
  requestKey: "customer-record-test",
  sourceClient: "MOBILE"
})
assert.strictEqual(runtime.runMobileFeatureAction("customer", "addCustomerServiceRecord", mapped, {
  actionPayload: { ...recordForm, partySize: 2, teaServed: "肉桂", consumptionAmount: 380 }
}), "recorded")
const runtimeRecordCall = calls[calls.length - 1]
assert.strictEqual(runtimeRecordCall[0], "record")
assert.strictEqual(runtimeRecordCall[1], 9)
assert.strictEqual(runtimeRecordCall[2].sourceClient, "MOBILE")
assert.ok(/^customer-record-/.test(runtimeRecordCall[2].requestKey), "mobile service records should have a fresh idempotency key")
assert.strictEqual(
  buildCustomerServiceRecordPayload(recordForm).requestKey,
  recordForm.requestKey,
  "mobile record retries should reuse the key created when the dialog opened"
)

assert.ok(
  actionService.includes("createCustomerServiceCard") && actionService.includes("updateCustomerServiceCard") &&
    actionService.includes("addCustomerServiceRecord") &&
    MOBILE_FORM_CREATE_PERMISSIONS.customer.includes("inv:customerCard:add") &&
    MOBILE_FORM_EDIT_PERMISSIONS.customer.includes("inv:customerCard:edit") &&
    MOBILE_ACTION_PERMISSIONS.addCustomerServiceRecord.includes("inv:customerCard:record:add") &&
    page.includes("MobileCustomerRecordDialog") &&
    page.includes('action.behavior === "customer-record"') &&
    page.includes("getCustomerServiceCardPhoto") && page.includes('action.behavior === "preview-customer-photo"'),
  "mobile runtime should call service-card create/update/record and controlled photo APIs behind dedicated permissions"
)
assert.ok(
  customerPage.includes("listCustomerServiceCards") &&
    customerPage.includes("getCustomerServiceCard") &&
    customerPage.includes("getCustomerServiceCardPhoto") &&
    customerPage.includes("createCustomerServiceCard") &&
    customerPage.includes("updateCustomerServiceCard") &&
    customerPage.includes("addCustomerServiceRecord") &&
    customerPage.includes("getCustomerServiceCardCapabilities") &&
    customerPage.includes("inv:customerCard:record:add") &&
    customerPage.includes("MobileCustomerRecordDialog") &&
    customerPage.includes("contactPhone ||") &&
    !customerPage.includes("maskPhone"),
  "mobile customer route should use an independent service-card page with full phone, photo, edit, and service-record flows"
)
assert.ok(
  customerPage.includes("创建人") && customerPage.includes("最后修改人") &&
    customerPage.includes("最后修改时间") && customerPage.includes("selected.createBy") &&
    customerPage.includes("selected.updateBy") && customerPage.includes("selected.updateTime"),
  "mobile detail should expose creator and last-modifier metadata"
)
assert.ok(
  customerPage.includes("detailRequestSeq") && customerPage.includes("photoRequestSeq") &&
    customerPage.includes("listRequestSeq") && /this\.selected\.status\s*!==\s*"0"/.test(customerPage),
  "mobile customer requests should ignore stale responses and archived cards should stay read-only"
)
assert.ok(
  customerApi.includes("validateCustomerPhotoBlob") &&
    /contentType\.includes\(["']application\/json["']\)/.test(customerApi),
  "mobile photo flow should reject JSON business errors before creating an object URL"
)
assert.ok(
  search.includes('customer: { fields: [{ key: "keyword"') &&
    picker.includes("inv:customerCard:add") && !picker.includes("inv:customer:add"),
  "mobile search and quick creation should use the new store-scoped service-card contract"
)

console.log("customerServiceCardMobileFlow tests passed")
