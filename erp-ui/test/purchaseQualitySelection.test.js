const assert = require('node:assert/strict'), test = require('node:test')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..'), diagnostics = []
Vue.config.errorHandler = e => diagnostics.push(e)
Vue.config.warnHandler = e => { if (!e.startsWith('Avoid mutating a prop directly')) diagnostics.push(Error(e)) }
test.afterEach(() => assert.deepEqual(diagnostics.splice(0), []))
function load(relative) {
  const file = path.join(root, relative), source = fs.readFileSync(file, 'utf8')
  const code = babel.transformSync(compiler.parseComponent(source).script.content, { filename: file, babelrc: false,
    configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console, require(id) {
    if (id === '@/utils/shopContext') return { isSelectedWarehouse: () => true }
    if (id === '@/utils/inventoryQuantity') return require('../src/utils/inventoryQuantity')
    if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
    return {}
  } }, { filename: file })
  return module.exports.default
}
function harness(mobile = false) {
  const definition = load(mobile ? 'src/views/mobile/feature/components/MobileActionDialog.vue' : 'src/views/inventory/purchase/index.vue')
  const errors = [], c = new Vue({ ...definition, mixins: [], created: [], mounted: [], beforeDestroy: [], watch: {},
    propsData: mobile ? { open: true, action: { id: 'qualityCheckPurchase' }, iconPaths: {} } : {},
    beforeCreate() { this.$modal = { msgError: m => errors.push(m), msgSuccess() {} } }
  })
  const details = [1, 2, 3].map(id => ({ batchDetailId: String(id), itemName: '物料' + id, pendingQuantity: 10 }))
  const batch = { batchId: '20', batchNo: 'B20', details }
  if (mobile) { c.qualityBatches = [batch]; c.receiptBatchId = '20'; c.applyQualityBatch() }
  else { c.qcOpen = true; c.qcTargetOrderId = '10'; c.qcForm.orderId = '10'; c.applyQcBatch(batch) }
  return { c, errors, rows: () => mobile ? c.qualityRows : c.qcForm.items,
    build: () => mobile ? c.buildQualityItems() : c.buildQualityCheckItems(),
    select: value => mobile ? c.setQualitySelection(value) : c.setQcSelection(value),
    pass: () => mobile ? c.fillSelectedQualityPassed() : c.fillAllQcPassed(),
    clear: () => mobile ? c.clearSelectedQualityResults() : c.clearQcClassifications() }
}
for (const mobile of [false, true]) {
  const label = mobile ? 'H5' : 'PC'
  test(label + ': new batches are unselected and zero quantity; no implicit passed result or submission', () => {
    const h = harness(mobile)
    assert.equal(h.rows().every(r => r.qcSelected === false && r.inspectedQuantity === 0 && r.acceptedQuantity === 0), true)
    h.pass(); assert.equal(h.rows().every(r => r.inspectedQuantity === 0), true)
    assert.equal(h.build(), null)
  })
  test(label + ': selected partial inspection is submitted while unchecked invalid and empty rows stay pending', () => {
    const h = harness(mobile), [a, b] = h.rows()
    a.qcSelected = true; a.inspectedQuantity = 3; a.acceptedQuantity = 2; a.rejectedQuantity = 1; a.defectReason = '破损'
    b.inspectedQuantity = NaN; b.acceptedQuantity = -1
    const out = h.build()
    assert.equal(out.length, 1); assert.equal(out[0].batchDetailId, '1'); assert.equal(out[0].inspectedQuantity, 3)
    assert.equal(out[0].rejectedQuantity, 1); assert.equal('qcSelected' in out[0], false); assert.equal(h.rows().length, 3)
    a.qcSelected = false; assert.equal(h.build(), null); assert.equal(a.inspectedQuantity, 3)
  })
  test(label + ': all-passed and clear affect only selected rows and preserve valid partial quantity', () => {
    const h = harness(mobile), [a, b, c] = h.rows()
    a.qcSelected = true; a.inspectedQuantity = 4; a.defectReason = '旧'; a.rejectedQuantity = 4
    c.qcSelected = true
    h.pass(); assert.equal(a.inspectedQuantity, 4); assert.equal(a.acceptedQuantity, 4); assert.equal(a.defectReason, '')
    assert.equal(c.inspectedQuantity, 10); assert.equal(b.inspectedQuantity, 0)
    assert.equal(h.build().length, 2); h.clear(); assert.equal(a.acceptedQuantity, 0); assert.equal(a.inspectedQuantity, 4)
    assert.equal(h.build(), null); h.select(true); h.pass(); assert.equal(h.build().length, 3)
    h.select(false); assert.equal(h.build(), null)
  })
  test(label + ': selected invalid quantities, mismatched totals and missing rejection reason all block', () => {
    for (const value of [0, -1, 11, NaN, Infinity, 'bad']) {
      const h = harness(mobile), a = h.rows()[0]
      a.qcSelected = true; a.inspectedQuantity = value; a.acceptedQuantity = value
      assert.equal(h.build(), null, String(value))
    }
    const h = harness(mobile), a = h.rows()[0]; a.qcSelected = true; a.inspectedQuantity = 3; a.acceptedQuantity = 2
    assert.equal(h.build(), null); a.rejectedQuantity = 1; assert.equal(h.build(), null)
    a.defectReason = '原因'; assert.equal(h.build().length, 1)
    a.pendingQuantity = NaN; assert.equal(h.build(), null)
  })
  test(label + ': changing batch clears old selections and values; positive old manually supplied rows remain supported', () => {
    const h = harness(mobile); h.select(true); h.pass()
    if (mobile) { h.c.receiptBatchId = '20'; h.c.applyQualityBatch() } else h.c.applyQcBatch({ batchId: '21', details: [{ batchDetailId: '8', pendingQuantity: 2 }] })
    assert.equal(h.rows().some(r => r.qcSelected), false); assert.equal(h.build(), null)
    const a = h.rows()[0]; delete a.qcSelected; a.inspectedQuantity = 1; a.acceptedQuantity = 1
    assert.equal(h.build().length, 1)
  })
}
test('PC confirmation totals include only selected rows and confirmation rejects changed selection/quantity/reason', async () => {
  for (const mutation of [r => { r.qcSelected = false }, r => { r.inspectedQuantity = 2 }, r => { r.defectReason = 'changed' }]) {
    const h = harness(), a = h.rows()[0]; a.qcSelected = true; a.inspectedQuantity = 3; a.acceptedQuantity = 3
    h.rows()[1].inspectedQuantity = 100; h.rows()[1].acceptedQuantity = 100
    assert.ok(h.c.getQualityCheckConfirmMessage().includes('本次检验：3.00'))
    const data = { receiptBatchId: '20', items: h.build() }, calls = []
    let resolve; h.c.$modal.confirm = () => new Promise(r => { resolve = r })
    const pending = h.c.confirmAndSubmitQualityCheck(data, async (...args) => calls.push(args))
    mutation(a); resolve(); await pending; assert.equal(calls.length, 0); assert.ok(h.errors.length)
  }
})
test('PC matching confirmation sends exact selected IDs once and cannot be duplicated while API is pending', async () => {
  const h = harness(); h.rows()[1].qcSelected = true; h.pass()
  h.c.$modal.confirm = () => Promise.resolve(); h.c.getList = () => Promise.resolve()
  let finish; const calls = [], api = (...args) => { calls.push(args); return new Promise(r => { finish = r }) }
  const data = { receiptBatchId: '20', items: h.build() }
  const a = h.c.confirmAndSubmitQualityCheck(data, api)
  for (let i = 0; i < 3; i++) await Vue.nextTick()
  const b = h.c.confirmAndSubmitQualityCheck(data, api); await b
  assert.equal(calls.length, 1); assert.equal(calls[0][1].items[0].batchDetailId, '2')
  h.select(false); assert.equal(h.rows()[1].qcSelected, true)
  finish(); await a; assert.equal(h.c.qcOpen, false)
})
test('H5 confirm emits selected rows only and historical whole-order QC still requires explicit result', () => {
  const h = harness(true), emitted = []; h.c.$on('confirm', payload => emitted.push(payload))
  h.rows()[1].qcSelected = true; h.pass(); h.c.confirm()
  assert.equal(emitted.length, 1); assert.equal(emitted[0].qualityItems.length, 1); assert.equal(emitted[0].qualityItems[0].batchDetailId, '2')
  h.c.qualityLegacyMode = true; h.c.qcResult = ''; h.c.confirm(); assert.equal(emitted.length, 1)
  h.c.qcResult = 'rejected'; h.c.comment = ''; h.c.confirm(); assert.equal(emitted.length, 1)
  h.c.comment = '退回'; h.c.confirm(); assert.equal(emitted.length, 2)
})
test('actual PC and H5 templates compile with accessible selection controls and selected-row input disabling', () => {
  for (const rel of ['src/views/inventory/purchase/index.vue', 'src/views/mobile/feature/components/MobileActionDialog.vue']) {
    const source = fs.readFileSync(path.join(root, rel), 'utf8')
    const out = compiler.compile(compiler.parseComponent(source).template.content)
    assert.deepEqual(out.errors, []); assert.deepEqual(out.tips, [])
    assert.ok(source.includes('所选全部合格')); assert.ok(source.includes('未勾选商品继续待检'))
  }
})

