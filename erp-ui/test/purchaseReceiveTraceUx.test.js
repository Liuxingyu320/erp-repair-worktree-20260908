const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/purchase/index.vue"),
  "utf8"
)

const purchaseFormStart = source.indexOf("'新建采购单'")
const productDialogStart = source.indexOf('title="选择采购物料"')
const receiveDialogStart = source.indexOf('title="采购收货"')
const qualityDialogStart = source.indexOf('title="采购质检"')

assert.ok(purchaseFormStart > -1 && productDialogStart > purchaseFormStart,
  "purchase editor boundaries should be discoverable")
assert.ok(receiveDialogStart > productDialogStart && qualityDialogStart > receiveDialogStart,
  "receipt dialog boundaries should be discoverable")

const purchaseForm = source.slice(purchaseFormStart, productDialogStart)
const receiveDialog = source.slice(receiveDialogStart, qualityDialogStart)

;["supplierBatchNo", "deliveryNoteNo", "arrivedTime", "receiveForm.remark"].forEach(field => {
  assert.ok(!purchaseForm.includes(field),
    `${field} must not be bound inside the purchase order editor`)
  assert.ok(receiveDialog.includes(field),
    `${field} must be editable inside the actual receipt dialog`)
})

assert.ok(
  receiveDialog.includes('ref="receiveFormRef"') &&
    receiveDialog.includes(':rules="receiveRules"') &&
    receiveDialog.includes('prop="arrivedTime"'),
  "receipt trace fields should use the validated receipt form"
)
assert.ok(
  source.includes('arrivedTime: [{ required: true') &&
    source.includes('supplierBatchNo: [{ max: 100') &&
    source.includes('remark: [{ max: 500'),
  "receipt trace fields should enforce required time and backend-compatible limits"
)
assert.ok(
  source.includes("this.receiveForm = Object.assign({}, this.receiveForm, {") &&
    !source.includes('warehouseId: this.currentWarehouseId,\n          supplierBatchNo: "",\n          deliveryNoteNo: "",\n          arrivedTime: this.defaultReceiveTime(),\n          remark: "",\n          details: details'),
  "late purchase-detail responses must preserve trace values already entered by the receiver"
)
assert.ok(
  source.includes("const requestSequence = ++this.receiveRequestSequence") &&
    source.includes("requestSequence !== this.receiveRequestSequence || !this.receiveOpen"),
  "a late response from an older receipt dialog must not overwrite the current order or its trace values"
)

const submitStart = source.indexOf("submitReceive()")
const validatedStart = source.indexOf("submitValidatedReceive()", submitStart)
assert.ok(
  submitStart > -1 && validatedStart > submitStart &&
    source.slice(submitStart, validatedStart).includes("formRef.validate"),
  "receipt submission should validate the form before building the API payload"
)

console.log("purchase receive trace UX tests passed")
