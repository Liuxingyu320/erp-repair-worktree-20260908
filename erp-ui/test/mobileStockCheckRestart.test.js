const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const babel = require('@babel/core')
const root = process.env.U5_SOURCE_ROOT || path.resolve(__dirname, '..')
const fallback = process.env.U5_FALLBACK_ROOT || root
const plain = value => JSON.parse(JSON.stringify(value))
const vnodeText = node => !node ? '' : ((node.text || '') + (node.children || []).map(vnodeText).join(' '))
function harness(rows, actionId = 'saveStockCheckInput') {
  const requests = [], cache = {}, diagnostics = []
  function load(relative) {
    if (cache[relative]) return cache[relative]
    let file = path.join(root, relative)
    if (!fs.existsSync(file)) file = path.join(fallback, relative)
    const source = fs.readFileSync(file, 'utf8')
    const component = relative.endsWith('.vue') ? compiler.parseComponent(source) : null
    const code = babel.transformSync(component ? component.script.content : source, { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    function requireDependency(id) {
      if (id === '@/api/inventory/purchase') return {}
      if (id === '@/utils/request') return options => new Promise((resolve, reject) => requests.push({ options, resolve, reject }))
      if (id.startsWith('@/')) return load('src/' + id.substring(2) + (path.extname(id) ? '' : '.js'))
      if (id.startsWith('.')) return load(path.posix.normalize(path.posix.join(path.posix.dirname(relative), id)) + (path.extname(id) ? '' : '.js'))
      return require(id)
    }
    vm.runInNewContext(code, { module, exports: module.exports, require: requireDependency, console, setTimeout, clearTimeout, Date, Promise, Set, Map }, { filename: file })
    const result = module.exports.default || module.exports
    if (component) { const compiled = compiler.compileToFunctions(component.template.content); result.render = compiled.render; result.staticRenderFns = compiled.staticRenderFns }
    cache[relative] = result
    return result
  }
  const component = load('src/views/mobile/feature/components/MobileActionDialog.vue')
  const page = new Vue({ ...component, propsData: { open: true, action: { id: actionId }, item: { checkId: '77', details: rows }, selectedDeptId: '20', iconPaths: {} } })
  page.initQuantityRows()
  let confirmation
  page.$on('confirm', value => { confirmation = value })
  const runtime = load('src/views/mobile/feature/featureActionRuntime.js').createMobileActionRuntime(load('src/api/inventory/stockCheck.js'))
  return { page, requests, load, run: () => { page.confirm(); assert.ok(confirmation); return runtime.runMobileFeatureAction('stockCheck', actionId, { checkId: '77' }, { actionPayload: confirmation }) }, confirmation: () => confirmation }
}
const row = (token = 'round-old', fields = {}) => ({ detailId: '1', actualQty: null, snapshotVersion: token, previousActualQty: 8, previousRecountQty: 9, needsSnapshotReview: true, ...fields })
const tick = async () => { for (let n = 0; n < 10; n++) await Vue.nextTick() }
test('actual Vue form preserves blank changed rows and shows former physical counts without copying them', () => {
  const h = harness([row(), row('round-old', { detailId: '2', actualQty: 7, needsSnapshotReview: false })])
  assert.equal(h.page.quantityRows[0].quantity, '')
  assert.equal(h.page.quantityRows[1].quantity, 7)
  assert.equal(h.page.quantityRows[0].previousActualQty, 8)
  assert.equal(h.page.quantityRows[0].previousRecountQty, 9)
  assert.equal(h.page.quantityRows[0].snapshotVersion, 'round-old')
  assert.equal(h.page.validateQuantityRows(), false)
  const text = vnodeText(h.page._render())
  assert.match(text, /上轮实盘 8/)
  assert.match(text, /上轮复盘 9/)
  assert.match(text, /库存已变化/)
  h.page.$destroy()
})
test('normalization and both emitted input shapes echo the original token', () => {
  const h = harness([row()]); h.page.quantityRows[0].quantity = 0; h.page.confirm()
  assert.equal(h.confirmation().items[0].snapshotVersion, 'round-old')
  assert.equal(h.confirmation().details[0].snapshotVersion, 'round-old')
  assert.equal(h.confirmation().items[0].quantity, 0)
  h.page.$destroy()
})
test('runtime fresh GET cannot replace a stale form token and failed input never submits', async () => {
  const h = harness([row()], 'submitStockCheck'); h.page.quantityRows[0].quantity = 5
  const operation = h.run(); const rejected = assert.rejects(operation, /重新/)
  assert.equal(h.requests[0].options.url, '/inventory/stockCheck/77')
  h.requests.shift().resolve({ data: { details: [row('round-new')] } }); await tick()
  const input = h.requests.shift(); assert.equal(input.options.url, '/inventory/stockCheck/input/77')
  assert.equal(input.options.data.details[0].snapshotVersion, 'round-old')
  input.reject(new Error('盘点已重新开始')); await rejected
  assert.equal(h.requests.length, 0); h.page.$destroy()
})
test('old clients without tokens stay missing even if runtime sees a restarted detail', async () => {
  const h = harness([row(undefined, { snapshotVersion: undefined })]); h.page.quantityRows[0].quantity = 6
  const operation = h.run(); h.requests.shift().resolve({ data: { details: [row('round-new')] } }); await tick()
  const input = h.requests.shift(); assert.equal(Object.prototype.hasOwnProperty.call(input.options.data.details[0], 'snapshotVersion'), false)
  input.resolve({}); await operation; h.page.$destroy()
})
test('current form passes token through actual API write and then submits the same check', async () => {
  const h = harness([row('round-new')], 'submitStockCheck'); h.page.quantityRows[0].quantity = 6
  const operation = h.run(); h.requests.shift().resolve({ data: { details: [row('round-new')] } }); await tick()
  const input = h.requests.shift(); assert.deepEqual(plain(input.options.data.details), [{ detailId: '1', actualQty: 6, snapshotVersion: 'round-new', recountQty: null }])
  input.resolve({}); await tick(); const submit = h.requests.shift(); assert.equal(submit.options.url, '/inventory/stockCheck/submit/77'); submit.resolve({}); await operation; h.page.$destroy()
})
