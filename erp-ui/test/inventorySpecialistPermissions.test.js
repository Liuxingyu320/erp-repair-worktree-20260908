const assert = require('node:assert/strict'), { test } = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm')
const babel = require('@babel/core'), Vue = require('vue'), compiler = require('vue-template-compiler')
const { createMobileActionRuntime } = require('../src/views/mobile/feature/featureActionRuntime')
const root = path.resolve(__dirname, '../src'), plain = value => JSON.parse(JSON.stringify(value))
function load(relative, mocks = {}) {
  const filename = path.join(root, relative), source = fs.readFileSync(filename, 'utf8')
  const script = relative.endsWith('.vue') ? compiler.parseComponent(source).script.content : source
  const code = babel.transformSync(script, { filename, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, JSON, console,
    require(name) {
      if (Object.hasOwn(mocks, name)) return mocks[name]
      if (name === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
      if (name === '@/utils/purchaseQualityRecovery') return { createPurchaseQualityRecovery: () => ({}) }
      if (name.startsWith('.') && !name.endsWith('.vue')) {
        try { return require(path.resolve(path.dirname(filename), name)) } catch (_) { return {} }
      }
      return {}
    }
  }, { filename })
  return relative.endsWith('.vue') ? module.exports.default : module.exports
}
const fail = () => { throw new Error('hidden generic API must not be called') }, maxId = '9223372036854775807'
for (const [feature, name, idKey] of [['purchase', 'Purchase', 'orderId'], ['purchaseReturn', 'PurchaseReturn', 'returnId'], ['salesReturn', 'SalesReturn', 'returnId']]) {
  test('H5 submit-only ' + feature + ' sends saved ID without query/add/body dependency', async () => {
    const calls = [], runtime = createMobileActionRuntime({
      getPurchaseDetail: fail, getPurchaseReturn: fail, getSalesReturn: fail,
      submitPurchase: fail, submitPurchaseReturn: fail, submitSalesReturn: fail,
      ['submit' + name + 'Draft']: (...args) => { calls.push(args); return Promise.resolve({ data: { status: 'submitted' } }) }
    })
    await runtime.runMobileFeatureAction(feature, 'submit' + name, { [idKey]: maxId, version: '7', remark: 'unsaved row values' })
    assert.deepEqual(calls, [[maxId, '7']])
  })
  const draftApi = 'get' + name + 'Draft', readApi = 'get' + name + (feature === 'purchase' ? 'Detail' : '')
  test('H5 ' + feature + ' add-only uses draft read; query-only keeps general read', async () => {
    for (const permission of ['add', 'query']) {
      const expected = permission === 'add' ? draftApi : readApi, calls = []
      const api = { [draftApi]: fail, [readApi]: fail, [expected]: id => { calls.push(id); return Promise.resolve({ data: { [idKey]: id, details: [{ quantity: 2 }] } }) } }
      const service = load('views/mobile/feature/featureService.js', { ['@/api/inventory/' + feature]: api })
      const result = await service.fetchMobileFeatureDetail(feature, { [idKey]: maxId, status: 'draft', version: '7' }, { inventoryPermissions: ['inv:' + feature + ':' + permission] })
      assert.deepEqual(calls, [maxId]); assert.equal(result.details[0].quantity, 2)
    }
  })
  test('H5 ' + feature + ' submit-only uses list header; no-duty and add on submitted cannot hydrate', async () => {
    const service = load('views/mobile/feature/featureService.js', { ['@/api/inventory/' + feature]: { [readApi]: fail, [draftApi]: fail } })
    const result = await service.fetchMobileFeatureDetail(feature, { [idKey]: maxId, status: 'draft', version: '7', details: [{ quantity: 999 }] }, { inventoryPermissions: ['inv:' + feature + ':submit'] })
    assert.equal(result[idKey], maxId); assert.equal(result._specialistSummaryOnly, true); assert.equal(result.details, undefined)
    for (const permissions of [[], ['inv:' + feature + ':add']]) await assert.rejects(service.fetchMobileFeatureDetail(feature,
      { [idKey]: maxId, status: 'submitted' }, { inventoryPermissions: permissions }), /无权/)
  })
}
for (const [file, name, url] of [['purchase', 'getPurchaseDraft', '/inventory/purchase/draft/'],
  ['purchase', 'getPurchaseReceiveContext', '/inventory/purchase/receive-context/'], ['purchaseReturn', 'getPurchaseReturnDraft', '/inventory/purchaseReturn/draft/'],
  ['salesReturn', 'getSalesReturnDraft', '/inventory/salesReturn/draft/'], ['salesReturn', 'getSalesReturnSourceOrder', '/inventory/salesReturn/source-orders/']]) {
  test('real read API ' + name + ' retains exact ID and accepts only silentError', async () => {
    const calls = [], api = load('api/inventory/' + file + '.js', { '@/utils/request': c => { calls.push(c); return Promise.resolve({ data: {} }) } })
    await api[name](maxId, { silentError: true, url: '/wrong', method: 'post', data: { injected: true } })
    assert.deepEqual(plain(calls), [{ url: url + maxId, method: 'get', silentError: true }])
  })
}
for (const [name, pathPart, entity, idKey] of [['listPurchaseSuppliers', 'suppliers', 'supplier', 'supplierId'], ['listPurchaseProducts', 'products', 'product', 'productId'],
  ['listPurchaseOeItems', 'oe', 'oe', 'oeItemId'], ['listPurchaseGifts', 'gifts', 'gift', 'giftId']]) {
  test('real purchase catalog API ' + pathPart + ' preserves filters on purpose endpoint', async () => {
    const calls = [], api = load('api/inventory/purchase.js', { '@/utils/request': c => { calls.push(c); return Promise.resolve({ rows: [] }) } })
    const query = { pageNum: 2, pageSize: 20, supplierName: 'A', keyword: 'cup' }
    await api[name](query, { silentError: true, params: { injected: true } })
    assert.deepEqual(plain(calls), [{ url: '/inventory/purchase/catalog/' + pathPart, method: 'get', params: query, silentError: true }])
  })
  test('H5 purchase ' + entity + ' never falls back to generic master-data API', async () => {
    const calls = [], service = load('views/mobile/feature/mobileEntityService.js', {
      '@/api/inventory/purchase': { [name]: (query, config) => { calls.push([query, config]); return Promise.resolve({ rows: [{ [idKey]: maxId, productName: '茶', oeItemName: '杯', giftName: '礼盒', supplierName: 'A' }] }) } },
      '@/api/inventory/product': { listProduct: fail }, '@/api/inventory/oe': { listOe: fail }, '@/api/inventory/gift': { listGift: fail },
      '@/api/inventory/supplier': { listSupplier: fail, getSupplierProducts: fail }, '@/api/inventory/mobile': { getMobileOptions: fail }
    })
    const rows = await service.fetchMobileEntityOptions(entity, { keyword: 'tea', context: { featureKey: 'purchase' }, formData: { supplierName: 'A' } })
    assert.equal(rows.length, 1); assert.equal(calls.length, 1); assert.equal(calls[0][1].silentError, true)
    if (entity === 'supplier') assert.equal(calls[0][0].supplierName, 'tea')
    else if (entity !== 'gift') assert.equal(calls[0][0].supplierName, 'A')
  })
  if (entity !== 'supplier') test('actual Vue PC purchase listCatalogItems uses ' + entity + ' purpose read', async () => {
    const calls = [], definition = load('views/inventory/purchase/index.vue', { '@/api/inventory/purchase': {
      [name]: (query, options) => { calls.push([query, options]); return Promise.resolve({ rows: [] }) }
    } })
    const component = new Vue({ data: () => ({}), methods: { listCatalogItems: definition.methods.listCatalogItems, normalizeText: definition.methods.normalizeText } })
    await component.listCatalogItems(entity, { productName: '杯子', pageNum: 3, pageSize: 20, supplierName: 'A' })
    assert.equal(calls.length, 1); assert.equal(calls[0][0].pageNum, 3); assert.equal(calls[0][0].keyword, '杯子'); assert.equal(calls[0][1].silentError, true)
    component.$destroy()
  })
}
test('real sales source list preserves server filters; submit sends no mutable body', async () => {
  const calls = [], api = load('api/inventory/salesReturn.js', { '@/utils/request': c => { calls.push(c); return Promise.resolve({}) } })
  const query = { keyword: '张', startDate: '2026-09-01', endDate: '2026-09-12', pageNum: 2, pageSize: 20 }
  await api.listSalesReturnSourceOrders(query, { silentError: true }); await api.submitSalesReturnDraft(maxId, '7')
  assert.deepEqual(plain(calls), [{ url: '/inventory/salesReturn/source-orders', method: 'get', params: query, silentError: true },
    { url: '/inventory/salesReturn/submit/' + maxId, method: 'post', params: { version: '7' } }])
})
test('H5 receive-only hydrates through receive context without query', async () => {
  const calls = [], service = load('views/mobile/feature/featureService.js', { '@/api/inventory/purchase': {
    getPurchaseDetail: fail, getPurchaseReceiveContext: id => { calls.push(id); return Promise.resolve({ data: { orderId: id, details: [{ quantity: 2 }] } }) }
  } })
  const result = await service.fetchMobileFeatureDetail('purchase', { orderId: maxId, status: 'submitted' }, { inventoryPermissions: ['inv:purchase:receive'] })
  assert.deepEqual(calls, [maxId]); assert.equal(result.details[0].quantity, 2)
})

for (const [feature, name, idKey] of [['purchase', 'Purchase', 'orderId'], ['purchaseReturn', 'PurchaseReturn', 'returnId'], ['salesReturn', 'SalesReturn', 'returnId']]) {
  test('actual H5 Vue ' + feature + ' selected detail forwards duty permissions and enables saved-ID actions', async () => {
    const calls = [], service = load('views/mobile/feature/featureService.js', { ['@/api/inventory/' + feature]: {
      ['get' + name + (feature === 'purchase' ? 'Detail' : '')]: fail,
      ['get' + name + 'Draft']: id => { calls.push(id); return Promise.resolve({ data: { [idKey]: id, status: 'draft', version: '7', details: [{ quantity: 2 }] } }) }
    } })
    const definition = load('views/mobile/feature/index.vue', { './featureService': service })
    const page = new Vue({ data: () => ({ featureKey: feature, userPermissions: ['inv:' + feature + ':add'],
      approvalRouteContext: {}, selectedItem: { [idKey]: maxId, status: 'draft', version: '7' }, selectedItemEditSource: null,
      detailRequestToken: 0, detailLoading: false, detailLoadFailed: false, actionMessage: '' }),
      methods: { loadSelectedItemDetail: definition.methods.loadSelectedItemDetail,
        setActionMessage(message) { this.actionMessage = message }, mobileMapperContext() { return {} } } })
    await page.loadSelectedItemDetail(page.selectedItem)
    assert.deepEqual(calls, [maxId]); assert.equal(page.detailLoadFailed, false); assert.equal(page.detailLoading, false)
    assert.equal(page.selectedItemEditSource.details[0].quantity, 2)
    page.userPermissions = ['inv:' + feature + ':submit']
    await page.loadSelectedItemDetail({ [idKey]: maxId, status: 'draft', version: '7' })
    assert.equal(page.detailLoadFailed, false); assert.equal(page.selectedItemEditSource.details, undefined)
    assert.match(page.actionMessage, /当前岗位可处理/); assert.equal(calls.length, 1)
    page.$destroy()
  })
}

for (const [feature, name, idKey, todoType] of [['purchase', 'Purchase', 'orderId', 'INV_PURCHASE_QC'],
  ['purchaseReturn', 'PurchaseReturn', 'returnId', 'INV_PURCHASE_RETURN_CONFIRM'], ['salesReturn', 'SalesReturn', 'returnId', 'INV_SALES_RETURN_CONFIRM']]) {
  test('real action-context API ' + feature + ' sends exact ID and whitelist silentError', async () => {
    const calls = [], api = load('api/inventory/' + feature + '.js', { '@/utils/request': c => { calls.push(c); return Promise.resolve({}) } })
    await api['get' + name + 'ActionContext'](maxId, { silentError: true, url: '/wrong', method: 'post' })
    assert.deepEqual(plain(calls), [{ url: '/inventory/' + feature + '/action-context/' + maxId, method: 'get', silentError: true }])
  })
  test('H5 ' + feature + ' todo focus reads minimal action context without query/list dependency', async () => {
    const calls = [], header = { [idKey]: maxId, status: 'submitted', _specialistSummaryOnly: true, qcStatus: 'pending' }
    const service = load('views/mobile/feature/featureService.js', {
      '@/utils/todoBusinessFocus': require('../src/utils/todoBusinessFocus'),
      ['@/api/inventory/' + feature]: {
        ['get' + name + (feature === 'purchase' ? 'Detail' : '')]: fail,
        ['list' + name]: fail,
        ['get' + name + 'ActionContext']: (id, options) => { calls.push([id, options]); return Promise.resolve({ data: header }) }
      }
    })
    const result = await service.fetchMobileFeatureData(feature, { selectedDeptType: feature === 'salesReturn' ? 'STORE' : 'WAREHOUSE', query: { todoType, businessId: maxId } })
    assert.equal(result.rows.length, 1); assert.equal(result.rows[0][idKey], maxId)
    assert.equal(result.rows[0]._specialistSummaryOnly, true); assert.equal(result.rows[0].details, undefined)
    assert.deepEqual(plain(calls), [[maxId, { silentError: true }]])
  })
  if (feature !== 'salesReturn') test('actual PC ' + feature + ' todo mixin callback uses task header', async () => {
    const calls = [], definition = load('views/inventory/' + feature + '/index.vue', {
      '@/mixins/todoBusinessFocus': { createTodoBusinessFocusMixin: config => ({ config }) },
      ['@/api/inventory/' + feature]: { ['get' + name + (feature === 'purchase' ? 'Detail' : '')]: fail,
        ['get' + name + 'ActionContext']: id => { calls.push(id); return Promise.resolve({ data: { [idKey]: id, _specialistSummaryOnly: true } }) } }
    })
    const result = await definition.mixins[0].config.loadFocusedRow(maxId)
    assert.deepEqual(calls, [maxId]); assert.equal(result.data._specialistSummaryOnly, true)
  })
}
