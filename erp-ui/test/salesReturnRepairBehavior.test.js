const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const babel = require('@babel/core')
const { createSalesReturnDataFromOrder } = require('../src/views/mobile/feature/mobileReturnSourceOrders')
const { buildMobileFormPayload } = require('../src/views/mobile/feature/mobileFormPayloads')
const { getMobileFormConfig } = require('../src/views/mobile/feature/mobileFormConfigs')
const { validateMobileForm } = require('../src/views/mobile/feature/mobileValidation')
const plain = value => JSON.parse(JSON.stringify(value))
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function loadModule(relative, deps = {}) {
  const filename = path.resolve(__dirname, '../src', relative)
  const source = fs.readFileSync(filename, 'utf8')
  const script = filename.endsWith('.vue') ? source.match(/<script>([\s\S]*?)<\/script>/)[1] : source
  const code = babel.transformSync(script, {
    filename, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
  }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise,
    require(name) {
      if (Object.hasOwn(deps, name)) return deps[name]
      if (['./MobileEntityPicker.vue', './MobileLineItemsEditor.vue', '@/components/ImageUpload', '@/components/ImageGallery'].includes(name)) return {}
      if (name === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin() { return {} } }
      if (name === '@/utils/shopContext') return { isSelectedStore: () => true, isSelectedWarehouse: () => true, getSelectedDeptId: () => 8 }
      if (name === '@/utils/returnSelection') return require('../src/utils/returnSelection')
      if (name === '@/views/inventory/components/SalesReturnSourcePicker.vue') return {}
      if (name === '@/api/inventory/salesReturn') return {}
      if (name === '@/utils/businessEmptyState') return { getBusinessEmptyText: () => '' }
      if (name.startsWith('.')) return require(path.resolve(path.dirname(filename), name))
      throw new Error('Unexpected application dependency: ' + name)
    }
  }, { filename })
  return module.exports.default || module.exports
}
function harness(component, extras = {}) {
  const events = []
  const value = { salesOrderId: 1, returnTitle: '', details: [] }
  const props = { value, open: true, fields: [], effectiveSubmitModes: [{ action: 'save' }] }
  const instance = Object.assign({}, props, component.data.call(props), {
    $set(target, key, value) { target[key] = value },
    $emit(...args) { events.push(args) },
    $modal: { msgWarning() {}, msgError() {} }
  }, extras)
  for (const [key, method] of Object.entries(component.methods)) instance[key] = method.bind(instance)
  return { instance, events }
}
function mobile(deps) {
  return loadModule('views/mobile/feature/components/MobileFormSheet.vue', deps)
}
const source = (id, prefix = 'SO') => ({ orderId: id, orderNo: prefix + id, customerName: '客户' + id, supplierName: '供应商' + id,
  details: [{ detailId: id * 10, productId: 101, productName: '商品', deliveredQuantity: 10, returnableQuantity: 2, unitPrice: 10 }] })

test('mobile mixed return keeps product and gift with same ID and distinct source prices', () => {
  const data = createSalesReturnDataFromOrder({ ...source(1), details: [
    { detailId: 10, itemType: 'product', itemId: 101, productId: 101, itemName: '商品', deliveredQuantity: 10, returnableQuantity: 2, unitPrice: 10 },
    { detailId: 11, itemType: 'gift', itemId: 101, productId: null, itemName: '礼盒', deliveredQuantity: 5, returnableQuantity: 3, unitPrice: 90 }
  ] }, { shopDeptId: 8 })
  data.details.forEach(row => { row.quantity = 1 })
  const config = getMobileFormConfig('salesReturn')
  assert.equal(validateMobileForm(config, data), '')
  const payload = buildMobileFormPayload(config, data, 'submit')
  assert.deepEqual(payload.details.map(row => [row.itemType, row.itemId, row.salesDetailId, row.amount]), [
    ['product', 101, 10, 10], ['gift', 101, 11, 90]
  ])
  assert.equal(payload.details[1].productId, undefined)
})

test('mobile A then B ignores late A and blocks submit until B loaded', async () => {
  const a = deferred(), b = deferred()
  const component = mobile({ '@/api/inventory/salesReturn': { getSalesReturnSourceOrder: id => id === 1 ? a.promise : b.promise }, '@/api/inventory/purchaseReturn': {} })
  const { instance, events } = harness(component)
  const first = instance.loadSalesReturnSourceOrder(1, source(1))
  instance.localData.salesOrderId = 2
  const second = instance.loadSalesReturnSourceOrder(2, source(2))
  instance.emitSubmit('save')
  assert.equal(events.filter(event => event[0] === 'submit').length, 0)
  b.resolve({ data: source(2) }); await second
  a.resolve({ data: source(1) }); await first
  assert.equal(instance.localData.salesOrderId, 2)
  assert.equal(instance.localData.customerName, '客户2')
  assert.equal(instance.localData.details[0].salesDetailId, 20)
  instance.emitSubmit('save')
  assert.equal(events.filter(event => event[0] === 'submit').length, 1)
})

test('mobile close/reopen invalidates old source and failed load remains blocked', async () => {
  const old = deferred()
  const component = mobile({ '@/api/inventory/salesReturn': { getSalesReturnSourceOrder: () => old.promise }, '@/api/inventory/purchaseReturn': {} })
  const { instance, events } = harness(component)
  const pending = instance.loadSalesReturnSourceOrder(1, source(1))
  instance.stopViewportTracking = instance.deactivateFormSheetFocus = instance.releaseFormSheetBodyLock = () => {}
  instance.open = false
  component.watch.open.call(instance, false)
  instance.open = true
  old.resolve({ data: source(1) }); await pending
  assert.equal(instance.localData.details.length, 0)
  await instance.loadReturnSourceOrder('salesOrderId', 1, source(1), () => Promise.reject(new Error('offline')), createSalesReturnDataFromOrder)
  assert.match(instance.returnSourceError, /加载失败/)
  instance.emitSubmit('save')
  assert.equal(events.filter(event => event[0] === 'submit').length, 0)
})

test('mobile purchase return uses scoped source API and retains manual title', async () => {
  const calls = []
  const component = mobile({ '@/api/inventory/sales': {}, '@/api/inventory/purchaseReturn': {
    getPurchaseReturnSourceOrder(id) { calls.push(id); return Promise.resolve({ data: source(id, 'PO') }) }
  } })
  const { instance } = harness(component)
  instance.localData.purchaseOrderId = 3
  instance.localData.returnTitle = '手工标题'
  await instance.loadPurchaseReturnSourceOrder(3, source(3, 'PO'))
  assert.deepEqual(calls, [3])
  assert.equal(instance.localData.purchaseOrderId, 3)
  assert.equal(instance.localData.returnTitle, '手工标题')
  instance.updateReturnTitle('采购退货-', 'PO3', 'PO4')
  assert.equal(instance.localData.returnTitle, '手工标题')
  instance.localData.returnTitle = '采购退货-PO3'
  instance.updateReturnTitle('采购退货-', 'PO3', 'PO4')
  assert.equal(instance.localData.returnTitle, '采购退货-PO4')
})

test('PC return enforces remaining quantity and keeps gift identity', () => {
  const component = loadModule('views/inventory/salesReturn/index.vue', { '@/api/inventory/salesReturn': {}, '@/api/inventory/sales': {} })
  const { instance } = harness(component)
  const rows = instance.buildReturnDetails([{ detailId: 1, itemType: 'gift', itemId: 101, itemName: '礼盒', productName: '礼盒', deliveredQuantity: 10, returnableQuantity: 2, unitPrice: 80 }])
  assert.equal(rows[0].maxReturnQuantity, 2)
  instance.form.details = rows
  assert.equal(instance.buildPayload(), null)
  rows[0].returnSelected = true; rows[0].quantity = 1
  assert.equal(instance.buildPayload().details.length, 1)
  rows[0].quantity = 3
  assert.equal(instance.buildPayload(), null)
})

test('PC source loading uses initialized generation and ignores stale responses', async () => {
  const a = deferred(), b = deferred()
  const component = loadModule('views/inventory/salesReturn/index.vue', { '@/api/inventory/salesReturn': { getSalesReturnSourceOrder: id => id === 1 ? a.promise : b.promise } })
  const { instance } = harness(component)
  instance.dialogOpen = true
  instance.form.salesOrderId = 1
  const first = instance.loadSalesOrder(1)
  instance.form.salesOrderId = 2
  const second = instance.loadSalesOrder(2)
  b.resolve({ data: source(2) }); await second
  a.resolve({ data: source(1) }); await first
  assert.equal(instance.form.salesOrderNo, 'SO2')
  assert.equal(instance.form.details[0].salesDetailId, 20)
})

test('specialized purchase API requests only draft ID and scoped return source', async () => {
  const calls = []
  const request = options => { calls.push(plain(options)); return Promise.resolve() }
  const api = loadModule('api/inventory/purchaseReturn.js', { '@/utils/request': request })
  await api.submitPurchaseReturnDraft(4)
  await api.getPurchaseReturnSourceOrder(5)
  await api.listPurchaseReturnSourceOrders({ pageSize: 20 })
  assert.deepEqual(calls, [
    { url: '/inventory/purchaseReturn/submit/4', method: 'post' },
    { url: '/inventory/purchaseReturn/source-orders/5', method: 'get' },
    { url: '/inventory/purchaseReturn/source-orders', method: 'get', params: { pageSize: 20 } }
  ])
})
