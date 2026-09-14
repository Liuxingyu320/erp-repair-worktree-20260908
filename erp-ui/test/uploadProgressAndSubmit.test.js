const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const unexpectedVueErrors = []
Vue.config.errorHandler = error => unexpectedVueErrors.push(error)
Vue.config.warnHandler = message => {
  // The mountless harness applies parent prop updates directly. All other Vue diagnostics fail verification.
  if (!message.startsWith('Avoid mutating a prop directly')) unexpectedVueErrors.push(new Error(message))
}
test.afterEach(() => assert.deepEqual(unexpectedVueErrors.splice(0), []))
const root = path.resolve(__dirname, '..')
const tick = async () => { for (let i = 0; i < 7; i++) await Vue.nextTick() }

// Execute actual Vue scripts and Element Form/Upload methods. Only rendering,
// auth transport and XHR are replaced; no upload or validation algorithm is copied.
function environment() {
  const requests = [], errors = [], events = [], listeners = new Set(), vueErrors = []
  let dept = '10'
  const store = Vue.observable({ getters: { id: '100' } })
  const route = Vue.observable({ fullPath: '/edit' })
  const window = { Promise, addEventListener(name, fn) { if (name === 'erp:dept-changed') listeners.add(fn) },
    removeEventListener(name, fn) { if (name === 'erp:dept-changed') listeners.delete(fn) } }
  const xhr = options => { const req = { options, aborted: false, abort() { this.aborted = true } }; requests.push(req); return req }
  const cache = {}
  function load(relative) {
    if (cache[relative]) return cache[relative]
    const filename = path.join(root, relative), source = fs.readFileSync(filename, 'utf8')
    const script = filename.endsWith('.vue') ? source.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1] : source
    const code = babel.transformSync(script, { filename, babelrc: false, configFile: false, parserOpts: { plugins: ['jsx'] },
      plugins: [() => ({ visitor: { ObjectMethod(p) { if (p.node.key.name === 'render') p.remove() } } }),
        require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
    const module = { exports: {} }
    vm.runInNewContext(code, { module, exports: module.exports, require: dependency, window,
      process: { env: { VUE_APP_BASE_API: '' } }, File, Blob, URL, Promise, Date, console, setTimeout, clearTimeout }, { filename })
    cache[relative] = module.exports.default || module.exports
    return cache[relative]
  }
  function dependency(id) {
    if (id === '@/utils/uploadProgress') return load('src/utils/uploadProgress.js')
    if (id === 'element-ui/packages/upload/src/ajax' || id === './ajax') return xhr
    if (id === '@/utils/shopContext') return { getSelectedDeptId: () => dept, getSelectedDeptName: () => 'Warehouse', isSelectedWarehouse: () => true }
    if (id === 'element-ui/src/utils/merge') return Object.assign
    if (id === '@/utils/auth') return { getToken: () => 'token' }
    if (id === '@/utils/sessionMode') return { buildSessionAuthHeaders: () => ({}), applySessionAuthHeaders() {}, shouldUseSessionCredentials: () => false }
    if (id === '@/utils/requestSecurity') return { safeTrustedApiUrl: (base, action) => action }
    if (id === '@/utils/urlSecurity') return require('../src/utils/urlSecurity')
    if (id === '@/api/system/file') return { deleteFile() { throw Error('Unexpected storage deletion') } }
    if (id.startsWith('@/api/inventory/')) return {}
    if (id === '@/mixins/todoBusinessFocus') return { createTodoBusinessFocusMixin: () => ({}) }
    if (['@/utils/purchaseReceiveRecovery', '@/utils/common', './purchaseActionRules', '@/utils/purchaseBusinessStage', '@/views/inventory/components/WarehouseSelect'].includes(id)) return {}
    if (id === 'sortablejs') return { create() {} }
    if (id === './mobileDialogFocus') return { createMobileDialogFocusManager: () => ({ release() {} }) }
    if (id === './mobileOverlayStack') return { releaseMobileOverlay() {}, mountMobileOverlay() {} }
    if (id === '../mobileValidation') return require('../src/views/mobile/feature/mobileValidation')
    if (id === './mobileFocus') return {}
    if (id === '../mobileTransferSmartPaste') return {}
    if (['../mobileReturnSourceOrders', '@/api/inventory/sales', '@/api/inventory/purchaseReturn'].includes(id)) return {}
    if (['./upload-list', './upload', 'element-ui/packages/progress', 'element-ui/src/mixins/migrating', './upload-dragger.vue',
      '@/components/UploadQueue', '@/components/ImageGallery', './MobileEntityPicker.vue', './MobileLineItemsEditor.vue', '@/components/ImageUpload'].includes(id)) return {}
    throw Error('Unexpected dependency ' + id)
  }
  function create(component, props, parent) {
    return new Vue({ ...component, parent, propsData: props,
      beforeCreate() { this.$store = store; this.$route = route; this.$modal = {
        msgError(message) { errors.push(message) }, loading() { throw Error('Fullscreen upload loader forbidden') }, closeLoading() { throw Error('Cannot close another loader') }
      } }
    })
  }
  const form = create(load('node_modules/element-ui/packages/form/src/form.vue'), { model: { title: 'editable' }, rules: {} })
  function uploader(kind = 'FileUpload', props = {}, parent = form) {
    const image = kind === 'ImageUpload'
    const c = create(load('src/components/' + kind + '/index.vue'), { action: '/file/upload', drag: false, compress: false,
      value: image ? [] : '', fileType: image ? ['png'] : ['pdf'], ...props }, parent)
    c.$on('input', value => { events.push({ c, value }); c.value = value })
    const outer = create(load('node_modules/element-ui/packages/upload/src/index.vue'), {
      action: '/file/upload', fileList: c.widgetFileList, beforeUpload: c.handleBeforeUpload, beforeRemove: c.canRemoveWidgetFile,
      onProgress: c.handleUploadProgress, onSuccess: c.handleUploadSuccess, onError: c.handleUploadError,
      onRemove: image ? c.handleDelete : () => {}
    }, c)
    const inner = create(load('node_modules/element-ui/packages/upload/src/upload.vue'), {
      action: '/file/upload', multiple: true, autoUpload: true, fileList: outer.uploadFiles, beforeUpload: c.handleBeforeUpload,
      onStart: outer.handleStart, onRemove: outer.handleRemove, onProgress: outer.handleProgress,
      onSuccess: outer.handleSuccess, onError: outer.handleError, httpRequest: c.uploadHttpRequest
    }, outer)
    c.$refs[image ? 'imageUpload' : 'fileUpload'] = outer
    outer.$refs['upload-inner'] = inner
    inner.$refs.input = { value: null }
    c.$watch('widgetFileList', value => { outer.fileList = value })
    outer.$watch('uploadFiles', value => { inner.fileList = value })
    return { c, outer, inner, add: (...files) => inner.uploadFiles(files) }
  }
  return { load, create, form, uploader, store, route, requests, errors, events, listeners, vueErrors,
    changeDept(id) { dept = id; [...listeners].forEach(fn => fn()) } }
}
function raw(name = 'a.pdf') { return new File(['file contents'], name, { type: name.endsWith('.png') ? 'image/png' : 'application/pdf' }) }
function ok(url) { return { code: 200, data: { url } } }
function task(c, f) { return c.uploadTasks[String(f.uid)] }
function urls(c) { return c.fileList.map(item => item.url).join(',') }
function valid(form) { let result; form.validate(value => { result = value }); return result }

for (const kind of ['FileUpload', 'ImageUpload']) {
  const suffix = kind === 'ImageUpload' ? '.png' : '.pdf'
  test(kind + ': actual Element form blocks unfinished upload but ordinary edits continue', async () => {
    const e = environment(), h = e.uploader(kind), f = raw('a' + suffix)
    h.add(f); await tick()
    assert.equal(e.form.fields.length, 1); assert.equal(valid(e.form), false)
    e.form.model.title = 'edited during upload'; assert.equal(e.form.model.title, 'edited during upload')
    e.requests[0].options.onProgress({ percent: 47.9 }); assert.equal(task(h.c, f).progress, 47)
    e.requests[0].options.onProgress({ percent: 20 }); assert.equal(task(h.c, f).progress, 47)
    e.requests[0].options.onProgress({ percent: 100 }); assert.equal(task(h.c, f).progress, 99)
    await assert.rejects(e.form.validate())
    e.requests[0].options.onSuccess(ok('/a' + suffix)); await tick()
    assert.equal(task(h.c, f).progress, 100); assert.equal(await e.form.validate(), true)
    assert.equal(urls(h.c), '/a' + suffix)
    assert.equal(e.form.model.title, 'edited during upload')
  })
  test(kind + ': failed file blocks save; retry uses new raw UID and late old callbacks cannot poison it', async () => {
    const e = environment(), h = e.uploader(kind), f = raw('a' + suffix)
    h.add(f); await tick(); const old = e.requests[0]
    old.options.onError(Error('offline')); await tick()
    assert.equal(valid(e.form), false); assert.equal(task(h.c, f).status, 'failed')
    h.c.retryQueuedUpload(task(h.c, f)); await tick()
    assert.equal(e.requests.length, 2)
    const retry = e.requests[1]; assert.notEqual(retry.options.file.uid, f.uid)
    old.options.onSuccess(ok('/stale' + suffix)); old.options.onError(Error('late')); old.options.onProgress({ percent: 80 })
    assert.equal(valid(e.form), false)
    retry.options.onSuccess(ok('/retry' + suffix)); await tick()
    assert.equal(valid(e.form), true); assert.equal(urls(h.c), '/retry' + suffix)
  })
  test(kind + ': cancel aborts exact request; callbacks cannot remove or overwrite another upload', async () => {
    const e = environment(), h = e.uploader(kind), a = raw('a' + suffix), b = raw('b' + suffix)
    h.add(a, b); await tick()
    h.c.cancelQueuedUpload(task(h.c, a)); assert.equal(e.requests[0].aborted, true); assert.equal(e.requests[1].aborted, false)
    e.requests[0].options.onError(Error('abort')); e.requests[0].options.onProgress({ percent: 70 }); e.requests[0].options.onSuccess(ok('/late' + suffix))
    assert.equal(h.outer.uploadFiles.length, 1); assert.equal(h.outer.uploadFiles[0].uid, b.uid)
    assert.equal(valid(e.form), false)
    e.requests[1].options.onSuccess(ok('/b' + suffix)); await tick()
    assert.equal(valid(e.form), true); assert.equal(urls(h.c), '/b' + suffix)
    h.c.retryQueuedUpload(task(h.c, a)); await tick()
    assert.equal(e.requests.length, 3); assert.equal(valid(e.form), false)
  })
  test(kind + ': business failure or unsafe URL remains blocked until explicitly removed', async () => {
    const e = environment(), h = e.uploader(kind), f = raw('a' + suffix)
    h.add(f); await tick(); e.requests[0].options.onSuccess(ok('javascript:bad')); await tick()
    assert.equal(valid(e.form), false); assert.equal(urls(h.c), '')
    h.c.dismissQueuedUpload(task(h.c, f)); assert.equal(valid(e.form), true)
  })
  test(kind + ': replacement form model rejects old callback even before Vue watcher flush', async () => {
    const e = environment(), h = e.uploader(kind), f = raw('a' + suffix)
    h.add(f); await tick(); e.form.model = { title: 'new record' }
    e.requests[0].options.onSuccess(ok('/wrong-record' + suffix)); await tick()
    assert.equal(urls(h.c), ''); assert.equal(e.events.length, 0); assert.equal(e.requests[0].aborted, true)
    assert.equal(valid(e.form), true)
  })
  test(kind + ': changed account, department, route, explicit record context and disposal retire uploads', async () => {
    for (const change of [e => { e.store.getters.id = '200' }, e => e.changeDept('20'), e => { e.route.fullPath = '/other' },
      (e, h) => { h.c.contextKey = 'new record' }, (e, h) => h.c.$destroy()]) {
      const e = environment(), h = e.uploader(kind), f = raw('a' + suffix)
      h.add(f); await tick(); change(e, h)
      e.requests[0].options.onSuccess(ok('/stale' + suffix)); await tick()
      assert.equal(urls(h.c), ''); assert.equal(e.events.length, 0); assert.equal(e.requests[0].aborted, true)
    }
  })
}

test('actual Element rules rebind/reset/clear/dispose works with multiple upload guards', async () => {
  const e = environment(), a = e.uploader(), b = e.uploader('ImageUpload')
  a.add(raw()); b.add(raw('b.png')); await tick()
  assert.equal(e.form.fields.length, 2)
  e.form.rules = { title: [{ required: true }] }; await tick(); e.form.clearValidate()
  assert.equal(valid(e.form), false)
  e.requests[0].options.onSuccess(ok('/a.pdf')); await tick(); assert.equal(valid(e.form), false)
  e.form.resetFields(); await tick(); assert.equal(valid(e.form), true)
  assert.equal(e.requests[1].aborted, true)
  a.c.$destroy(); assert.equal(e.form.fields.length, 1)
  b.c.$destroy(); assert.equal(e.form.fields.length, 0); assert.equal(e.listeners.size, 0)
})
test('compression cancellation followed by rejected before-upload never splices another widget file', async () => {
  const e = environment(), h = e.uploader('ImageUpload'); let finishCompression
  h.c.compressImageFile = f => f.name === 'a.png' ? new Promise(resolve => { finishCompression = resolve }) : Promise.resolve(f)
  const a = raw('a.png'), b = raw('b.png'); h.add(a, b); await tick()
  h.c.cancelQueuedUpload(task(h.c, a)); finishCompression(a); await tick()
  assert.equal(e.requests.length, 1); assert.equal(h.outer.uploadFiles.length, 1); assert.equal(h.outer.uploadFiles[0].uid, b.uid)
  e.requests[0].options.onSuccess(ok('/b.png')); await tick(); assert.equal(urls(h.c), '/b.png')
})
test('compression rejection is retryable and blocks actual form validation', async () => {
  const e = environment(), h = e.uploader('ImageUpload'), a = raw('a.png')
  h.c.compressImageFile = () => Promise.reject(Error('bad image'))
  h.add(a); await tick(); assert.equal(valid(e.form), false); assert.equal(e.requests.length, 0)
  h.c.compressImageFile = f => Promise.resolve(f); h.c.retryQueuedUpload(task(h.c, a)); await tick()
  assert.equal(e.requests.length, 1); e.requests[0].options.onSuccess(ok('/retry.png')); await tick(); assert.equal(valid(e.form), true)
})
test('retry counts successful but not yet committed widget files and observes disabled state', async () => {
  const e = environment(), h = e.uploader('FileUpload', { limit: 2 }), a = raw('a.pdf'), b = raw('b.pdf'), c = raw('c.pdf')
  h.add(a, b); await tick(); e.requests[0].options.onError(Error('offline')); await tick()
  h.add(c); await tick(); e.requests[1].options.onSuccess(ok('/b.pdf')); await tick()
  h.c.retryQueuedUpload(task(h.c, a)); assert.equal(e.requests.length, 3)
  assert.ok(e.errors.some(message => message.includes('不能超过')))
  h.c.disabled = true; h.c.retryQueuedUpload(task(h.c, a)); assert.equal(e.requests.length, 3)
})
test('mobile sheet has independent per-child submission guards while input remains enabled', async () => {
  const e = environment(), options = e.load('src/views/mobile/feature/components/MobileFormSheet.vue')
  const c = e.create(options, { open: false, feature: {}, config: { idKey: 'repairId', fields: [] }, value: { repairId: '9007199254740993', reason: 'old' }, iconPaths: {}, context: { selectedDeptId: '10' } })
  const emitted = []; c.$on('submit', v => emitted.push(v)); let input
  c.$on('input', v => { input = v })
  c.handleUploadState({ id: 1, blocking: true }); c.handleUploadState({ id: 2, blocking: true })
  c.localData.reason = 'edited'; c.emitInput(); assert.equal(input.reason, 'edited')
  c.emitSubmit('save'); assert.equal(emitted.length, 0)
  c.handleUploadState({ id: 1, blocking: false }); c.emitSubmit('save'); assert.equal(emitted.length, 0)
  const oldContext = c.uploadContextKey; c.contextKey = 'next'; assert.notEqual(c.uploadContextKey, oldContext)
  c.handleUploadState({ id: 1, blocking: false }); assert.equal(c.uploadsUnfinished, true)
  c.handleUploadState({ id: 2, blocking: false }); c.emitSubmit('save'); assert.deepEqual(emitted, ['save'])
})
test('templates compile and attachment removal uses a labelled native non-submit button', () => {
  for (const relative of ['src/components/FileUpload/index.vue', 'src/components/ImageUpload/index.vue', 'src/components/UploadQueue/index.vue',
    'src/views/mobile/feature/components/MobileFormSheet.vue', 'src/views/mobile/feature/index.vue', 'src/views/inventory/purchase/index.vue']) {
    const source = fs.readFileSync(path.join(root, relative), 'utf8')
    const template = compiler.parseComponent(source).template.content
    assert.equal(compiler.compile(template).errors.length, 0, relative)
    if (relative.includes('FileUpload')) {
      assert.match(template, /<button type="button" class="attachment-remove-button" :aria-label=/)
      assert.match(template, /@click="handleDelete\(file\)"/)
    }
  }
})

for (const kind of ['FileUpload', 'ImageUpload']) {
  const suffix = kind === 'ImageUpload' ? '.png' : '.pdf'
  test(kind + ': deleting original attachment preserves in-flight Element files and commits both successes', async () => {
    const e = environment(), h = e.uploader(kind, { value: '/original' + suffix }), a = raw('a' + suffix), b = raw('b' + suffix)
    h.add(a, b); await tick(); e.requests[0].options.onSuccess(ok('/a' + suffix)); await tick()
    h.c.handleDelete(h.c.fileList[0]); await tick()
    assert.equal(h.outer.uploadFiles.length, 2)
    e.requests[1].options.onProgress({ percent: 75 }); e.requests[1].options.onSuccess(ok('/b' + suffix)); await tick()
    assert.equal(valid(e.form), true); assert.equal(urls(h.c), '/a' + suffix + ',/b' + suffix)
    if (kind === 'ImageUpload') assert.equal(h.c.number, 0)
    h.c.handleDelete(h.c.fileList[0]); await tick()
    assert.equal(h.outer.uploadFiles.length, 1); assert.equal(urls(h.c), '/b' + suffix)
  })
  test(kind + ': retry one failed file does not re-submit another ready in-flight file', async () => {
    const e = environment(), h = e.uploader(kind), a = raw('a' + suffix), b = raw('b' + suffix)
    h.add(a, b); await tick(); const bRequest = e.requests[1]
    e.requests[0].options.onError(Error('offline')); await tick()
    h.c.retryQueuedUpload(task(h.c, a)); await tick()
    assert.equal(e.requests.length, 3); assert.equal(bRequest.aborted, false)
    assert.equal(task(h.c, b).status, 'uploading')
    bRequest.options.onSuccess(ok('/b' + suffix)); e.requests[2].options.onSuccess(ok('/a' + suffix)); await tick()
    assert.equal(valid(e.form), true); assert.equal(urls(h.c), '/b' + suffix + ',/a' + suffix)
    if (kind === 'ImageUpload') assert.equal(h.c.number, 0)
  })
}
test('removing early successful image before another finishes never resurrects it', async () => {
  const e = environment(), h = e.uploader('ImageUpload'), a = raw('a.png'), b = raw('b.png')
  h.add(a, b); await tick(); e.requests[0].options.onSuccess(ok('/a.png')); await tick()
  h.outer.handleRemove(h.outer.getFile(a)); await tick()
  assert.equal(h.outer.uploadFiles.length, 1)
  e.requests[1].options.onSuccess(ok('/b.png')); await tick()
  assert.equal(urls(h.c), '/b.png'); assert.equal(valid(e.form), true)
})
test('QC upload table outside Element form blocks its own submit and rechecks after confirmation', async () => {
  const e = environment(), definition = e.load('src/views/inventory/purchase/index.vue')
  const c = e.create({ ...definition, created: [], beforeDestroy: [], mixins: [] }, {})
  c.qcOpen = true; c.qcForm.orderId = '101'; c.qcTargetOrderId = '101'; c.qcForm.receiptBatchId = '201'
  c.qcForm.items = [{ batchDetailId: '301', attachmentUrls: '' }]
  c.handleQcUploadState({ id: 1, blocking: true })
  assert.equal((await c.submitQualityCheck()).blocked, true)
  c.handleQcUploadState({ id: 1, blocking: false }); assert.equal(c.qualityUploadsBlocked(), false)
  let confirm; const calls = []
  c.$modal.confirm = () => new Promise(resolve => { confirm = resolve })
  c.getQualityCheckConfirmMessage = () => 'Confirm'
  let result = c.confirmAndSubmitQualityCheck({}, async (...args) => { calls.push(args) })
  c.handleQcUploadState({ id: 2, blocking: true }); confirm(); await result; assert.equal(calls.length, 0)
  c.handleQcUploadState({ id: 2, blocking: false })
  result = c.confirmAndSubmitQualityCheck({}, async (...args) => { calls.push(args) })
  c.qcForm.items[0].attachmentUrls = '/new.pdf'; confirm(); await result; assert.equal(calls.length, 0)
  result = c.confirmAndSubmitQualityCheck({}, async (...args) => { calls.push(args) })
  c.qcForm.receiptBatchId = '202'; confirm(); await result; assert.equal(calls.length, 0)
  c.getList = () => Promise.resolve(); c.$modal.msgSuccess = () => {}
  result = c.confirmAndSubmitQualityCheck({}, async (...args) => { calls.push(args) })
  confirm(); await result; assert.equal(calls.length, 1)
})
