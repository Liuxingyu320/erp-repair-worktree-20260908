const assert = require('assert'), fs = require('fs'), path = require('path'), vm = require('vm')
const babel = require('@babel/core'), Vue = require('vue'), compiler = require('vue-template-compiler')
const { fixture, body, deferred, flush } = require('./purchaseReceiveRecovery.test')
const recoveryModule = require('../src/utils/purchaseReceiveRecovery')
const root = path.resolve(__dirname, '..')
function load(relative, mocks) {
  const filename = path.join(root, relative), source = fs.readFileSync(filename, 'utf8')
  const script = relative.endsWith('.vue') ? compiler.parseComponent(source).script.content : source
  const code = babel.transformSync(script, { filename, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, JSON, console,
    require(name) {
      if (name === '@/utils/inventoryQuantity') return require('../src/utils/inventoryQuantity')
      if (mocks[name]) return mocks[name]
      if (name === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
      if (name === '@/utils/purchaseQualityRecovery') return { createPurchaseQualityRecovery: () => ({}) }
      if (name === './mobileRouteLoadGuard') return { createMobileRouteLoadGuard: () => ({ invalidate() {} }) }
      if (name === './featureActionRuntime') return require('../src/views/mobile/feature/featureActionRuntime')
      return {}
    }
  }, { filename })
  return relative.endsWith('.vue') ? module.exports.default : module.exports
}
function setup(mobile = false) {
  const h = fixture(), notices = [], requests = [], reads = [], runtimeReads = []
  let failTransport = false, deferTransport = null
  const store = { getters: { get id() { return h.state.actor } } }
  const context = { getSelectedInventoryDeptId: () => h.state.dept, getSelectedDeptId: () => h.state.dept,
    getSelectedDeptName: () => '仓库', isSelectedWarehouse: () => true,
    getSelectedDeptContext: () => ({ deptId: h.state.dept, deptType: 'WAREHOUSE', isWarehouse: true }) }
  const api = load('src/api/inventory/purchase.js', {
    '@/store': store, '@/utils/shopContext': context,
    '@/utils/request': async config => {
      await Promise.resolve()
      const payload = JSON.parse(config.transformRequest[0](config.data))
      requests.push({ orderId: config.url.split('/').pop(), payload, requestId: config.headers['X-Request-Id'], dept: config.inventoryDeptId })
      const response = h.response(config.url.split('/').pop(), payload, config.headers['X-Request-Id'])
      if (deferTransport) return deferTransport.promise
      if (failTransport) { failTransport = false; throw new Error('response lost after commit') }
      return response
    }
  })
  h.deps.transport = api.receivePurchase
  const recovery = h.newService()
  const mocks = {
    '@/utils/shopContext': context, '@/utils/common': { parseTime: () => '2026-09-12 09:00:00' },
    '@/utils/purchaseReceiveRecovery': { ...recoveryModule, getPurchaseReceiveRecovery: () => recovery },
    '@/api/inventory/purchase': { ...api, getPurchaseReceiveContext(id) {
      const q = deferred(); reads.push({ id, ...q }); return q.promise
    } }
  }
  const serviceMocks = { ...mocks, '@/api/inventory/purchase': { ...api, getPurchaseReceiveContext(id) {
    const q = deferred(); runtimeReads.push({ id, ...q }); return q.promise
  } } }
  const service = load('src/views/mobile/feature/featureActionService.js', serviceMocks)
  mocks['./featureActionService'] = service
  const definition = load(mobile ? 'src/views/mobile/feature/index.vue' : 'src/views/inventory/purchase/index.vue', mocks)
  const instance = new Vue({ ...definition, created: [], beforeDestroy: [], mixins: [],
    computed: { ...definition.computed, ...(mobile ? { featureKey: () => 'purchase', userPermissions: () => ['*:*:*'] } : {}) },
    beforeCreate() {
      this.$store = { getters: Vue.observable({ id: h.state.actor }) }
      this.$route = { fullPath: '/test/purchase', query: {} }
      this.$modal = { confirm: () => Promise.resolve(), msgError: message => notices.push({ type: 'error', message }),
        msgSuccess: message => notices.push({ type: 'success', message }), msgWarning: message => notices.push({ type: 'warning', message }) }
    }
  })
  instance.getList = () => Promise.resolve()
  instance.refresh = () => Promise.resolve()
  instance.requestMobileConfirm = () => Promise.resolve()
  instance.showActionError = error => notices.push({ type: 'error', message: error.message })
  instance.$refs.receiveFormRef = { validate: callback => callback(true), clearValidate() {} }
  async function openPC(id = 31) {
    const action = instance.doReceive({ orderId: id, orderNo: 'PO' + id }); await flush()
    if (reads.length && reads[reads.length - 1].id === id) reads[reads.length - 1].resolve({ data: { orderId: id, details: [{ detailId: 101, quantity: 10, receivedQuantity: 0 }] } })
    await action
    if (instance.receiveForm.details.length) instance.receiveForm.details[0].receiveQuantity = 2
  }
  return { ...h, page: instance, definition, api, recovery, notices, requests, reads, runtimeReads, openPC,
    failNext() { failTransport = true }, deferNext(value) { deferTransport = value } }
}
const cases = [], test = (name, run) => cases.push({ name, run })

test('API requires explicit identity and checks context immediately before serialization', async () => {
  const h = setup(); await assert.rejects(h.api.receivePurchase(31, body()), /标识/); assert.equal(h.requests.length, 0)
  const action = h.api.receivePurchase(31, body(), 'receive:explicit-1', { actor: '7', dept: '9' }); h.state.actor = 8
  await assert.rejects(action, /变化/); assert.equal(h.requests.length, 0); h.page.$destroy()
})
test('PC freezes all fields before confirmation and suppresses confirmation double-click', async () => {
  const h = setup(); await h.openPC(); const confirm = deferred(); h.page.$modal.confirm = () => confirm.promise
  h.page.receiveForm.supplierBatchNo = 'ORIGINAL'; h.page.receiveForm.remark = '原备注'
  const first = h.page.submitReceive(); await flush()
  assert.equal(h.page.receiveSubmitting, true); await h.page.submitReceive()
  h.page.receiveForm.remark = '新备注'; h.page.receiveForm.details[0].receiveQuantity = 3
  confirm.resolve(); await first; await flush()
  assert.equal(h.requests.length, 1); assert.equal(h.requests[0].payload.remark, '原备注'); assert.equal(h.requests[0].payload.supplierBatchNo, 'ORIGINAL')
  assert.equal(h.requests[0].payload.items[0].receiveQuantity, 2); assert.equal(h.page.receiveSubmitting, false)
  assert.equal(h.notices.filter(x => x.type === 'success').length, 1); h.page.$destroy()
})
test('PC validation callback cannot write another form session', async () => {
  const h = setup(); await h.openPC(); let validate
  h.page.$refs.receiveFormRef.validate = fn => { validate = fn }
  const action = h.page.submitReceive(); await flush(); h.page.invalidateReceiveSession(); validate(true); await action
  assert.equal(h.requests.length, 0); h.page.$destroy()
})
test('PC confirmation then close/reopen cannot send an old order', async () => {
  const h = setup(); await h.openPC(); const confirm = deferred(); h.page.$modal.confirm = () => confirm.promise
  const action = h.page.submitReceive(); await flush(); h.page.invalidateReceiveSession(); await h.openPC(32)
  confirm.resolve(); await action; assert.equal(h.requests.length, 0); assert.equal(h.page.receiveForm.orderId, 32); h.page.$destroy()
})
test('PC lost response retains recovery entry after closing and does not fetch remaining first', async () => {
  const h = setup(); await h.openPC(); h.failNext(); await h.page.submitReceive(); await flush()
  assert.equal(h.page.receiveRecoveryRecords.length, 1)
  h.page.receiveOpen = false; await Vue.nextTick(); await h.page.refreshReceiveRecovery()
  const readCount = h.reads.length
  await h.page.recoverPurchaseReceive(h.page.receiveRecoveryRecords[0]); await flush()
  assert.equal(h.reads.length, readCount); assert.equal(h.requests.length, 2); assert.deepStrictEqual(h.requests[0], h.requests[1])
  assert.equal(h.batches.size, 1); assert.equal(h.page.receiveRecoveryRecords.length, 0); h.page.$destroy()
})
test('PC retry reconciles the original before validating now-empty input', async () => {
  const h = setup(); await h.openPC(); h.failNext(); await h.page.submitReceive(); await flush()
  h.page.receiveForm.details[0].receiveQuantity = 0; h.page.receiveForm.details[0].remainingQuantity = 0
  h.page.receiveForm.arrivedTime = ''; h.page.$refs.receiveFormRef.validate = () => { throw Error('must not validate new input before recovery') }
  await h.page.submitReceive(); assert.equal(h.requests.length, 2); assert.deepStrictEqual(h.requests[0], h.requests[1])
  assert.equal(h.page.receiveForm.details[0].receiveQuantity, 0); assert.equal(h.page.receiveNeedsReopen, true); h.page.$destroy()
})
test('PC store failure prevents request and exposes an error', async () => {
  const h = setup(); await h.openPC(); h.store.reserve = async () => { throw new Error('storage full') }
  await h.page.submitReceive(); assert.equal(h.requests.length, 0); assert(h.notices.some(x => /存储/.test(x.message))); h.page.$destroy()
})
test('PC failed detail identity cannot become a writable target', async () => {
  const h = setup(), action = h.page.doReceive({ orderId: 31 }); await flush()
  h.reads[0].resolve({ data: { orderId: 32, details: [] } }); await action
  await h.page.submitReceive(); assert.equal(h.requests.length, 0); assert.equal(h.page.receiveNeedsReopen, true); h.page.$destroy()
})
test('H5 real page -> service -> runtime -> recovery -> API freezes original dialog item and fields', async () => {
  const h = setup(true); h.page.selectedItem = { _raw: { orderId: 31, details: [{ detailId: 101, quantity: 10, receivedQuantity: 0 }] } }
  await h.page.openActionDialog({ id: 'receivePurchaseAll', successText: '收货' })
  const payload = { ...body(), items: [{ detailId: 101, quantity: 2 }] }
  const action = h.page.confirmActionDialog(payload); await flush()
  payload.remark = '修改后'; payload.items[0].quantity = 8
  assert.equal(h.runtimeReads.length, 1)
  h.runtimeReads[0].resolve({ data: { orderId: 31, warehouseId: 9, details: [{ detailId: 101, quantity: 10, receivedQuantity: 0 }] } })
  await action; assert.equal(h.requests.length, 1); assert.equal(h.requests[0].payload.items[0].receiveQuantity, 2)
  assert.equal(h.requests[0].payload.remark, '原备注'); assert.equal(h.page.actionLoadingKey, ''); h.page.$destroy()
})
test('H5 item context changes while dialog is open block confirmation', async () => {
  const h = setup(true); h.page.selectedItem = { _raw: { orderId: 31 } }; await h.page.openActionDialog({ id: 'receivePurchaseAll' })
  h.page.detailRequestToken += 1; h.page.selectedItem = { _raw: { orderId: 32 } }
  await h.page.confirmActionDialog(body()); assert.equal(h.runtimeReads.length, 0); assert.equal(h.requests.length, 0); h.page.$destroy()
})
test('H5 route/keep-alive exit during the pre-write detail read prevents transport', async () => {
  const h = setup(true); h.page.selectedItem = { _raw: { orderId: 31 } }; await h.page.openActionDialog({ id: 'receivePurchaseAll' })
  const action = h.page.confirmActionDialog({ ...body(), items: [{ detailId: 101, quantity: 2 }] }); await flush()
  h.definition.deactivated.call(h.page)
  h.runtimeReads[0].resolve({ data: { orderId: 31, details: [{ detailId: 101, quantity: 10, receivedQuantity: 0 }] } }); await action
  assert.equal(h.requests.length, 0); assert.equal(h.page.actionLoadingKey, ''); h.page.$destroy()
})
test('PC-created unknown command recovers in H5 despite no remaining quantity or selected row', async () => {
  const h = setup(true); h.failNext()
  await assert.rejects(h.recovery.run({ orderId: 31, payload: body(), observedRequestId: null }))
  await h.page.refreshPurchaseReceiveRecords(); assert.equal(h.page.purchaseReceiveRecords.length, 1)
  h.page.selectedItem = null
  await h.page.recoverMobilePurchaseReceive(h.page.purchaseReceiveRecords[0]); await flush()
  assert.equal(h.runtimeReads.length, 0); assert.deepStrictEqual(h.requests[0], h.requests[1]); assert.equal(h.batches.size, 1)
  assert(h.notices.some(x => x.type === 'success' && /B0/.test(x.message))); h.page.$destroy()
})
test('H5 opening receive with pending command directs recovery before latest detail validation', async () => {
  const h = setup(true); h.failNext(); await assert.rejects(h.recovery.run({ orderId: 31, payload: body(), observedRequestId: null }))
  h.page.selectedItem = { _raw: { orderId: 31, status: 'received', details: [] } }
  await h.page.openActionDialog({ id: 'receivePurchaseAll' }); assert.equal(h.page.actionDialog.open, false)
  assert.equal(h.runtimeReads.length, 0); assert.equal(h.page.purchaseReceiveRecords.length, 1); h.page.$destroy()
})
test('PC organization change while confirmation is pending creates no record', async () => {
  const h = setup(); await h.openPC(); const confirm = deferred(); h.page.$modal.confirm = () => confirm.promise
  const action = h.page.submitReceive(); await flush(); h.state.dept = 10; h.page.handleProductDeptChanged()
  confirm.resolve(); await action; assert.equal(h.requests.length, 0); assert.equal(h.store.records.size, 0); h.page.$destroy()
})
test('H5 new input after another window created an unknown command only replays that command', async () => {
  const h = setup(true); h.page.selectedItem = { _raw: { orderId: 31 } }; await h.page.openActionDialog({ id: 'receivePurchaseAll' })
  h.failNext(); await assert.rejects(h.recovery.run({ orderId: 31, payload: body(), observedRequestId: null }))
  await h.page.confirmActionDialog({ ...body({ remark: '不能偷发新备注' }), items: [{ detailId: 101, quantity: 4 }] })
  assert.equal(h.runtimeReads.length, 0); assert.equal(h.requests.length, 2); assert.deepStrictEqual(h.requests[0], h.requests[1])
  assert.equal(h.batches.size, 1); assert(h.notices.some(x => /本次新输入尚未提交/.test(x.message))); h.page.$destroy()
})
test('PC reopening for an explicitly new arrival sends a new command ID', async () => {
  const h = setup(); await h.openPC(); await h.page.submitReceive(); await flush()
  await h.openPC(); h.page.receiveForm.remark = '第二次到货'; await h.page.submitReceive()
  assert.equal(h.requests.length, 2); assert.notEqual(h.requests[0].requestId, h.requests[1].requestId)
  assert.equal(h.batches.size, 2); assert.equal(h.requests[1].payload.remark, '第二次到货'); h.page.$destroy()
})
async function main() {
  for (const test of cases) {
    let timer
    try { await Promise.race([test.run(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('scenario timed out')), 4000) })]); await flush() }
    catch (error) { error.message = test.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer) }
  }
  for (const relative of ['src/views/inventory/purchase/index.vue', 'src/views/mobile/feature/index.vue']) {
    const source = fs.readFileSync(path.join(root, relative), 'utf8')
    assert.deepStrictEqual(compiler.compile(compiler.parseComponent(source).template.content).errors, [])
  }
  console.log('purchaseReceiveComponentRecovery: ' + cases.length + ' real Vue/API-chain cases passed; 2 templates compiled; 0 failed; 0 skipped')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
