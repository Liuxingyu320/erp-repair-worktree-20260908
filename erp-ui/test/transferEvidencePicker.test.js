const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const babel = require('@babel/core')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const evidence = require('../src/utils/transferEvidence')
const root = path.resolve(__dirname, '..')
const tick = async () => { for (let n = 0; n < 8; n++) await Vue.nextTick() }
const diagnostics = []
Vue.config.errorHandler = error => diagnostics.push(error)
Vue.config.warnHandler = message => { if (!message.startsWith('Avoid mutating a prop directly')) diagnostics.push(message) }
test.afterEach(() => assert.deepEqual(diagnostics.splice(0), []))

function harness(props = {}) {
  const requests = [], states = [], urls = [], revoked = [], messages = []
  const store = Vue.observable({ getters: { driveEnabled: true }, state: { user: { id: '7' } } })
  const route = Vue.observable({ fullPath: '/transfer' })
  const request = options => new Promise((resolve, reject) => requests.push({ options, resolve, reject }))
  const URL = { createObjectURL: blob => { const id = 'blob:test-' + urls.length; urls.push(id); return id }, revokeObjectURL: id => revoked.push(id) }
  const cache = {}
  function load(relative) {
    if (cache[relative]) return cache[relative]
    const filename = path.join(root, relative), raw = fs.readFileSync(filename, 'utf8')
    const script = relative.endsWith('.vue') ? compiler.parseComponent(raw).script.content : raw
    const code = babel.transformSync(script, { filename, babelrc: false, configFile: false,
      plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    function dependency(id) {
      if (id === '@/api/drive') return load('src/api/drive/index.js')
      if (id === '@/utils/request') return request
      if (id === '@/utils/transferEvidence') return evidence
      if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
      return new Proxy({}, { get: (_, key) => key === '__esModule' ? false : () => ({}) })
    }
    vm.runInNewContext(code, { module, exports: module.exports, require: dependency, URL, Blob, File, FormData, AbortController,
      Promise, Date, Set, Map, console, setTimeout, clearTimeout, process: { env: {} } }, { filename })
    cache[relative] = module.exports.default || module.exports
    return cache[relative]
  }
  const component = load('src/components/TransferEvidencePicker.vue')
  const c = new Vue({ ...component, propsData: { contextKey: 'transfer:1:receipt:2:row:3', ...props },
    beforeCreate() { this.$store = store; this.$route = route } })
  c.$on('input', value => { c.value = value })
  c.$on('upload-state', value => states.push(value))
  function take(suffix) {
    const index = requests.findIndex(r => r.options.url.endsWith(suffix))
    assert.ok(index >= 0, 'missing request ' + suffix)
    return requests.splice(index, 1)[0]
  }
  function upload(name = 'photo.png') {
    c.spaces = [{ spaceId: '10', canWrite: true }]; c.spaceId = '10'
    c.filesChosen({ target: { files: [new File(['a'], name, { type: 'image/png' })], value: 'input' } })
    return take('/files')
  }
  return { c, store, route, requests, states, take, upload, urls, revoked, load, messages }
}
const node = (id = '11', extra = {}) => ({ nodeId: id, nodeType: 'FILE', status: 'ACTIVE', nodeName: 'photo.png', contentType: 'image/png', canPreview: false, ...extra })

test('IDs retain the full Long range and reject unsafe numbers, folders and trash', () => {
  assert.equal(evidence.encodeEvidence(['9223372036854775807']), 'drive:9223372036854775807')
  for (const value of [9223372036854775807, '9223372036854775808', '0', '01', '1e3']) assert.throws(() => evidence.evidenceId(value))
  assert.throws(() => evidence.evidenceNode(node('11', { nodeType: 'FOLDER' })))
  assert.throws(() => evidence.evidenceNode(node('11', { status: 'TRASHED' })))
  assert.equal(evidence.parseEvidence('https://example.com/a').legacy, 'https://example.com/a')
})
test('actual API uploads multipart to controlled cloud drive and emits node references only', async () => {
  const h = harness(), req = h.upload()
  assert.equal(req.options.url, '/file/drive/files'); assert.equal(req.options.data.get('spaceId'), '10')
  assert.equal(req.options.data.get('parentId'), '0'); assert.equal(h.c.state(), false)
  req.resolve({ data: node('9223372036854775807') }); await tick()
  assert.equal(h.c.value, 'drive:9223372036854775807'); assert.equal(h.c.state(), true)
  h.c.$destroy()
})
test('one successful upload cannot clear another pending upload or permit parent submission', async () => {
  const h = harness(), a = h.upload('a.png'), b = h.upload('b.png')
  a.resolve({ data: node('11') }); await tick()
  assert.equal(h.c.value, 'drive:11'); assert.equal(h.c.tasks.length, 1); assert.equal(h.c.state(), false)
  h.c.remove(h.c.selected[0]); await tick(); assert.equal(h.c.value, '')
  b.resolve({ data: node('12') }); await tick()
  assert.equal(h.c.value, 'drive:12'); assert.equal(h.c.state(), true); h.c.$destroy()
})
test('cancellation and late upload response never restore a removed task', async () => {
  const h = harness(), req = h.upload(), task = h.c.tasks[0]
  h.c.cancelTask(task); assert.equal(req.options.signal.aborted, true)
  req.resolve({ data: node() }); await tick()
  assert.equal(h.c.value, ''); assert.equal(h.c.selected.length, 0); h.c.$destroy()
})
test('unknown upload and unsafe response ID block submission without automatic retry', async () => {
  for (const outcome of ['network', 'unsafe']) {
    const h = harness(), req = h.upload()
    if (outcome === 'network') req.reject(Error('network'))
    else req.resolve({ data: node(9223372036854775807) })
    await tick(); assert.equal(h.c.tasks[0].status, 'unknown'); assert.equal(h.c.state(), false)
    assert.equal(h.c.value, ''); assert.equal(h.requests.length, 0); h.c.$destroy()
  }
})
test('explicit retry affects only failed upload and leaves another upload pending', async () => {
  const h = harness(), a = h.upload('a.png'), b = h.upload('b.png')
  a.reject({ response: { status: 413 }, message: 'too large' }); await tick()
  const task = h.c.tasks[0]; assert.equal(task.status, 'failed')
  const pending = h.c.upload(task), retry = h.take('/files')
  assert.equal(h.requests.length, 0); assert.equal(b.options.signal.aborted, false)
  retry.resolve({ data: node('11') }); await pending; await tick()
  assert.equal(h.c.state(), false); assert.equal(h.c.tasks.length, 1)
  b.resolve({ data: node('12') }); await tick(); assert.equal(h.c.state(), true); h.c.$destroy()
})
test('same-tick actor/context change invalidates upload, candidate list and metadata responses', async () => {
  const h = harness(), req = h.upload()
  h.c.loadNodes(1); const list = h.take('/nodes')
  h.store.state.user.id = '8'
  req.resolve({ data: node('11') }); list.resolve({ rows: [node('12')], total: 1 }); await tick()
  assert.equal(h.c.selected.length, 0); assert.equal(h.c.nodes.length, 0); assert.equal(h.c.value, '')
  h.c.$destroy()
})
test('failed metadata cannot borrow prior row selection and can be removed', async () => {
  const h = harness({ value: 'drive:11' }), req = h.take('/nodes/11')
  req.reject(Error('denied')); await tick()
  assert.equal(h.c.state(), false); assert.match(h.c.selected[0].error, /无法读取/)
  h.c.remove(h.c.selected[0]); await tick(); assert.equal(h.c.value, ''); assert.equal(h.c.state(), true); h.c.$destroy()
})
test('out-of-order list and retry retain the requested page and exact folder identity', async () => {
  const h = harness(); h.c.spaceId = '10'
  const first = h.c.loadNodes(1), a = h.take('/nodes')
  const second = h.c.loadNodes(2), b = h.take('/nodes')
  b.reject(Error('network')); a.resolve({ rows: [node()], total: 100 }); await Promise.all([first, second])
  assert.equal(h.c.nodes.length, 0); assert.equal(h.c.page, 1); assert.equal(h.c.retryPage, 2)
  const retry = h.c.loadNodes(h.c.retryPage), r = h.take('/nodes'); assert.equal(r.options.params.pageNum, 2)
  r.resolve({ rows: [node('12')], total: 100 }); await retry
  assert.equal(h.c.page, 2); assert.equal(h.c.nodes[0].nodeId, '12'); h.c.$destroy()
})
test('preview uses authorized content API and late closed preview never creates a blob URL', async () => {
  const h = harness(); h.c.chooseNode(node('11', { canPreview: true }))
  const thumbnail = h.take('/nodes/11/content'); thumbnail.resolve(new Blob(['img'], { type: 'image/png' })); await tick()
  assert.equal(h.c.selected[0].thumbnail, 'blob:test-0')
  const pending = h.c.preview(h.c.selected[0]), req = h.take('/nodes/11/content')
  assert.equal(req.options.params.mode, 'preview'); h.c.closePreview()
  req.resolve(new Blob(['img'], { type: 'image/png' })); await pending
  assert.equal(h.urls.length, 1); h.c.$destroy(); assert.deepEqual(h.revoked, ['blob:test-0'])
})
test('historic text is read-only and cannot satisfy required evidence; another picker has independent state', async () => {
  const a = harness({ value: 'node-legacy', required: true }), b = harness()
  await tick(); assert.equal(a.c.state(), false); assert.equal(a.requests.length, 0)
  b.c.chooseNode(node()); await tick(); assert.equal(b.c.value, 'drive:11'); assert.equal(a.c.value, 'node-legacy')
  a.c.$destroy(); b.c.$destroy()
})
test('actual PC and H5 submission methods stop on pending or failed evidence', () => {
  const h = harness()
  const pc = h.load('src/views/inventory/transfer/index.vue').methods
  const errors = [], ctx = { receiptDetail: {}, receiptForm: { items: [{ evidenceState: { valid: false } }] },
    discrepancyForm: { items: [{ evidenceState: { valid: false } }] }, ensureCanReceive: () => true,
    $modal: { msgError: value => errors.push(value) } }
  pc.submitReceipt.call(ctx); pc.submitDiscrepancy.call(ctx); assert.equal(errors.length, 2)
  const mobile = h.load('src/views/mobile/feature/components/MobileActionDialog.vue').methods
  const m = new Vue({ data: () => ({ showTransferDiscrepancy: false, showTransferReceipt: true,
    quantityRows: [{ evidenceState: { valid: false } }], action: { id: 'receiveTransferShipment' }, error: '' }), methods: mobile })
  m.confirm(); assert.match(m.error, /凭证/)
  m.quantityRows = [{ detailId: '2', quantity: 1, rejectedQuantity: 0, damagedQuantity: 0, attachmentRefs: 'drive:9223372036854775807' }]
  assert.equal(m.normalizeQuantityRows()[0].attachmentRefs, 'drive:9223372036854775807')
  m.$destroy(); h.c.$destroy()
})
test('templates retain two PC attachment components, camera capture and actual renderable filename', async () => {
  for (const relative of ['src/components/TransferEvidencePicker.vue', 'src/views/inventory/transfer/index.vue', 'src/views/mobile/feature/components/MobileActionDialog.vue']) {
    const template = compiler.parseComponent(fs.readFileSync(path.join(root, relative), 'utf8')).template.content
    const compiled = compiler.compile(template)
    assert.deepEqual(compiled.errors, []); assert.doesNotThrow(() => new Function(compiled.render))
    if (relative.endsWith('transfer/index.vue')) assert.equal((compiled.render.match(/_c\('transfer-evidence-picker'/g) || []).length, 2)
    if (relative.includes('TransferEvidencePicker')) assert.match(template, /capture="environment"/)
  }
  const h = harness(); h.c.chooseNode(node('11', { nodeName: '<unsafe>.png' })); await tick()
  const template = compiler.parseComponent(fs.readFileSync(path.join(root, 'src/components/TransferEvidencePicker.vue'), 'utf8')).template.content
  Object.assign(h.c.$options, compiler.compileToFunctions(template)); const rendered = h.c._render()
  assert.equal(rendered.tag, 'div'); h.c.$destroy()
})
test('same-tick switch to required terminal decision cannot submit legacy text using earlier valid state', () => {
  const h = harness(), pc = h.load('src/views/inventory/transfer/index.vue').methods
  const row = { detailId: '2', itemName: '商品', category: 'DAMAGED', attachmentRequired: true,
    decision: 'WRITE_OFF', decisionOptions: [{ value: 'WRITE_OFF' }], attachmentRefs: 'legacy-node', evidenceState: { valid: true } }
  const errors = []
  pc.submitDiscrepancy.call({ discrepancyForm: { requestId: 'request-1', note: '核对照片', items: [row] },
    $modal: { msgError: message => errors.push(message) } })
  assert.equal(errors.length, 1); assert.match(errors[0], /凭证/)
  const mobile = h.load('src/views/mobile/feature/components/MobileActionDialog.vue').methods
  const m = new Vue({ data: () => ({ discrepancyRows: [row], error: '' }), methods: mobile })
  assert.equal(m.validateDiscrepancyRows(), false); assert.match(m.error, /凭证/)
  m.$destroy(); h.c.$destroy()
})

test('disabled file capability issues no list, upload, metadata or content request and cannot satisfy required evidence',async()=>{
 const h=harness({required:true});h.store.getters.driveEnabled=false;await tick();await h.c.openPicker();await h.c.loadNodes(1);h.c.chooseNode(node());h.c.value='drive:11';await tick();await h.c.preview({id:'11'});assert.equal(h.requests.length,0);assert.equal(h.c.canUpload,false);assert.equal(h.c.pickerOpen,false);assert.equal(h.c.state(),false);assert.match(h.c.selected[0].error,/暂未启用/);h.c.$destroy()
})
test('disabling file capability invalidates pending upload before reactive watcher flush',async()=>{
 const h=harness(),r=h.upload();h.store.getters.driveEnabled=false;r.resolve({data:node()});await tick();assert.equal(h.c.value,'');assert.equal(h.c.selected.length,0);assert.equal(h.c.pickerOpen,false);assert.equal(h.requests.length,0);h.c.$destroy()
})
