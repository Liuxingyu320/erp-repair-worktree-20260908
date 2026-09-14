const assert = require('node:assert/strict'), test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const { getMobileFormConfig } = require('../src/views/mobile/feature/mobileFormConfigs')
const { buildMobileFormPayload, createMobileFormData } = require('../src/views/mobile/feature/mobileFormPayloads')
const { getMobileFormValidationError } = require('../src/views/mobile/feature/mobileValidation')
const selection = require('../src/utils/returnSelection')
const root = path.resolve(__dirname, '..'), diagnostic = []
Vue.config.errorHandler = e => diagnostic.push(e)
Vue.config.warnHandler = e => { if (!e.startsWith('Avoid mutating a prop directly')) diagnostic.push(Error(e)) }
test.afterEach(() => assert.deepEqual(diagnostic.splice(0), []))
const tick = async () => { for (let i = 0; i < 4; i++) await Vue.nextTick() }
function load(relative) {
  const file = path.join(root, relative), source = fs.readFileSync(file, 'utf8'), script = compiler.parseComponent(source).script.content
  const code = babel.transformSync(script, { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console,
    require(id) {
      if (id === '@/utils/returnSelection') return selection
      if (id === '@/utils/shopContext') return { isSelectedWarehouse: () => true }
      if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
      if (id === '../mobileValidation') return require('../src/views/mobile/feature/mobileValidation')
      return {}
    }
  }, { filename: file })
  return module.exports.default
}
const row = (id, quantity, returnSelected) => ({ itemType: 'product', itemId: id, productId: id, productName: '物料' + id, purchaseDetailId: 100 + id,
  quantity, maxReturnQuantity: 10, unitPrice: 3, ...(returnSelected === undefined ? {} : { returnSelected }) })
function desktop(rows) {
  const definition = load('src/views/inventory/purchaseReturn/index.vue'), errors = []
  const c = new Vue({ ...definition, created: [], mixins: [], beforeCreate() { this.$modal = { msgError: text => errors.push(text) } } })
  c.form.details = rows
  return { c, errors }
}
function mobile(rows) {
  const config = getMobileFormConfig('purchaseReturn'), field = config.fields.find(f => f.type === 'line-items')
  const definition = load('src/views/mobile/feature/components/MobileLineItemsEditor.vue')
  const c = new Vue({ ...definition, created: [], beforeDestroy: [], propsData: { value: rows, field, itemFields: field.itemFields, context: {}, formData: {} } })
  const data = () => ({ purchaseOrderId: 99, returnTitle: '退货', supplierName: '供应商', shopDeptId: 10, returnReason: '包装破损', responsibility: 'supplier', details: c.rows })
  return { c, config, field, data }
}
test('PC partial return ignores zero/invalid unchecked rows in quantity validation, total and payload', () => {
  const h = desktop([row(1, 2, true), row(2, 0, false), { quantity: -100, returnSelected: false }])
  const payload = h.c.buildPayload()
  assert.equal(payload.details.length, 1); assert.equal(payload.details[0].itemId, 1); assert.equal(payload.totalAmount, 6)
  assert.equal(h.c.totalAmount, '6.00'); assert.equal('returnSelected' in payload.details[0], false)
  assert.equal(h.c.form.details.length, 3)
})
test('PC selected invalid quantities still block; empty selection cannot silently save an empty return', () => {
  for (const quantity of [0, -1, 11, 'bad', Infinity]) {
    const h = desktop([row(1, quantity, true), row(2, 1, false)])
    assert.equal(h.c.buildPayload(), null); assert.ok(h.errors.length)
  }
  const h = desktop([row(1, 1, false)])
  assert.equal(h.c.buildPayload(), null); assert.match(h.errors[0], /勾选/)
})
test('PC all-return selects and fills all eligible rows; clear leaves original rows available', () => {
  const h = desktop([row(1, 0, false), { ...row(2, 0, false), maxReturnQuantity: 3 }])
  h.c.fillAllReturnable(); const payload = h.c.buildPayload()
  assert.deepEqual(Array.from(payload.details, r => r.quantity), [10, 3]); assert.equal(payload.totalAmount, 39)
  h.c.clearReturnQuantities(); assert.equal(h.c.form.details.length, 2); assert.equal(h.c.totalAmount, '0.00'); assert.equal(h.c.buildPayload(), null)
})
test('PC editing persisted positive lines stays selected, while loading a new source starts unselected', () => {
  const h = desktop([row(1, 2)])
  assert.equal(h.c.buildPayload().details.length, 1)
  h.c.form.details = h.c.buildReturnDetails([{ detailId: 1, productId: 1, productName: '物料', returnableQuantity: 3, unitPrice: 10 }])
  assert.equal(h.c.isReturnSelected(h.c.form.details[0]), false); assert.equal(h.c.form.details[0].quantity, 0)
})
test('mobile checkbox selection drives real validation and payload; unchecked invalid lines are untouched and omitted', async () => {
  const h = mobile([row(1, 2, false), row(2, 0, false)])
  assert.ok(getMobileFormValidationError(h.config, h.data()))
  h.c.setReturnSelected(0, true); await tick()
  assert.equal(getMobileFormValidationError(h.config, h.data()), null)
  const payload = buildMobileFormPayload(h.config, h.data(), { selectedDeptId: 10, selectedDeptType: 'WAREHOUSE' })
  assert.equal(payload.details.length, 1); assert.equal(payload.details[0].purchaseDetailId, 101); assert.equal(payload.details[0].quantity, 2)
  assert.equal('returnSelected' in payload.details[0], false); assert.equal(h.c.rows.length, 2)
  h.c.setReturnSelected(0, false); assert.equal(h.c.rows[0].quantity, 2); assert.ok(getMobileFormValidationError(h.config, h.data()))
})
test('mobile selected invalid quantity retains original row index for focus, including NaN and excess quantity', () => {
  for (const quantity of [0, -2, 11, 'bad']) {
    const h = mobile([row(1, 0, false), row(2, quantity, true)])
    const error = getMobileFormValidationError(h.config, h.data())
    assert.equal(error.rowIndex, 1); assert.equal(error.itemFieldKey, 'quantity')
  }
})
test('mobile all-return/clear and requirement indicators follow selected rows', () => {
  const h = mobile([row(1, 0, false), row(2, 0, false)])
  const quantityField = h.field.itemFields.find(f => f.key === 'quantity')
  assert.equal(h.c.itemFieldIsRequired(quantityField, h.c.rows[0]), false)
  h.c.fillAllReturnable(); assert.equal(h.c.itemFieldIsRequired(quantityField, h.c.rows[0]), true)
  assert.equal(getMobileFormValidationError(h.config, h.data()), null)
  const payload = buildMobileFormPayload(h.config, h.data(), { selectedDeptId: 10, selectedDeptType: 'WAREHOUSE' })
  assert.deepEqual(payload.details.map(r => r.quantity), [10, 10])
  h.c.clearReturnQuantities(); assert.equal(h.c.rows.length, 2); assert.ok(getMobileFormValidationError(h.config, h.data()))
})
test('mobile persisted draft without UI flags remains editable; sales return also uses confirmed D05 selection', () => {
  const h = mobile([row(1, 2)])
  assert.equal(h.c.isReturnSelected(h.c.rows[0]), true)
  const form = createMobileFormData(h.config, { ...h.data(), returnId: 42 }, { selectedDeptId: 10, selectedDeptType: 'WAREHOUSE' })
  assert.equal(form.details[0].quantity, 2); assert.equal(selection.isReturnSelected(form.details[0]), true)
  const sales = getMobileFormConfig('salesReturn').fields.find(f => f.type === 'line-items')
  assert.equal(sales.selectionScoped, true)
})
test('PC and mobile templates compile; number entry is readable and row removal remains fixed', () => {
  for (const file of ['src/views/inventory/purchaseReturn/index.vue', 'src/views/mobile/feature/components/MobileLineItemsEditor.vue']) {
    const template = compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).template.content
    assert.equal(compiler.compile(template).errors.length, 0, file)
    if (file.includes('purchaseReturn')) {
      assert.match(template, /label="数量" prop="quantity" width="150"/)
      assert.match(template, /:controls="false"/)
      assert.match(template, /label="操作" width="80" fixed="right"/)
    }
  }
})
