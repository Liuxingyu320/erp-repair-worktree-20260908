const test = require('node:test'), assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const Vue = require('vue'), compiler = require('vue-template-compiler'), babel = require('@babel/core')
const scopeModule = require('../src/utils/uiOperationScope')
const root = path.resolve(__dirname, '..')
const tick = async () => { for (let i = 0; i < 5; i++) await Vue.nextTick() }
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function loadComponent(file, imports) {
  const script = compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).script.content
  const code = babel.transformSync(script, { babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, console, window: { addEventListener() {}, removeEventListener() {} }, require: id => { if (!Object.hasOwn(imports, id)) throw Error(id); return imports[id] } })
  return module.exports.default
}
function makeAPI() {
  const calls = []
  const api = new Proxy({}, { get(_, name) { return (...args) => { const c = deferred(); calls.push({ name, args, ...c }); return c.promise } } })
  const take = name => { const c = calls.find(c => c.name === name && !c.used); assert.ok(c, 'Expected ' + name); c.used = true; return c }
  return { calls, api, take }
}
function setup(reader = false) {
  const h = makeAPI(), env = { dept: '9' }, messages = []
  const component = loadComponent(reader ? 'src/views/system/notice/ReadUsers.vue' : 'src/views/system/notice/index.vue', {
    '@/api/system/notice': h.api, '@/utils/shopContext': { getSelectedDeptId: () => env.dept }, '@/utils/uiOperationScope': scopeModule,
    '@/utils/noticeDraftRecovery': require('../src/utils/noticeDraftRecovery'), '@/utils/sanitizeNoticeHtml': { sanitizeNoticeHtml: html => html },
    '@/layout/components/HeaderNotice/DetailView': {}, './ReadUsers': {}
  })
  const store = Vue.observable({ getters: { id: '1', businessFeatures: {} }, state: { user: { sessionRevision: 1 } } })
  const p = new Vue({ ...component, created: [], beforeCreate() { this.$store = store; this.$route = { path: '/system/notice' }; this.$modal = { msgSuccess: m => messages.push(m), msgWarning: m => messages.push(m) } } })
  p.$refs.form = { validate: callback => callback(true), clearValidate() {} }
  const start = title => { p.handleAdd(); h.take('getNoticeAudienceOptions').resolve({ data: {} }); p.form.noticeTitle = title; p.form.noticeContent = title + '正文'; return { ...p.form } }
  return { ...h, p, store, env, messages, start }
}
test('S01 late A save success and finally cannot overwrite or unlock a later B draft', async () => {
  const h = setup(), p = h.p
  h.start('A'); p.submitDraft(); const a = h.take('addNotice')
  p.open = false; await tick(); h.start('B'); p.submitDraft(); const b = h.take('addNotice')
  a.resolve({ data: { ...a.args[0], noticeId: '101', version: 1 } }); await tick()
  assert.equal(p.form.noticeTitle, 'B'); assert.equal(p.open, true); assert.equal(p.saving, true); assert.equal(h.messages.length, 0)
  assert.match(p.saveReceipts[0].message, /保存成功/)
  b.resolve({ data: { ...b.args[0], noticeId: '102', version: 1 } }); await tick()
  assert.equal(p.form.noticeId, '102'); assert.equal(p.open, true); assert.equal(p.saving, false); assert.equal(h.messages.length, 1); p.$destroy()
})
test('S01 late A save error and async validation cannot post into or display errors on B', async () => {
  const h = setup(), p = h.p
  h.start('A'); let validated; p.$refs.form.validate = callback => { validated = callback }; p.submitDraft()
  h.start('B'); validated(true); await tick(); assert.equal(h.calls.some(c => c.name === 'addNotice'), false)
  p.$refs.form.validate = callback => callback(true); p.submitDraft(); const b = h.take('addNotice')
  h.start('C'); b.reject({ response: { status: 409, data: { msg: 'B conflict' } } }); await tick()
  assert.equal(p.editorError, ''); assert.equal(p.form.noticeTitle, 'C'); assert.equal(p.open, true); p.$destroy()
})
test('S01 known failure preserves body/audience and enables retry; unknown new save never auto-recreates', async () => {
  const h = setup(), p = h.p
  h.start('A'); p.form.audienceType = 'USER'; p.selectedUserIds = ['77']; p.submitDraft(); const first = h.take('addNotice')
  first.reject({ response: { status: 403, data: { msg: '无保存权限' } } }); await tick()
  assert.equal(p.form.noticeContent, 'A正文'); assert.deepEqual(Array.from(p.selectedUserIds), ['77']); assert.equal(p.saveUnknown, false); assert.equal(p.saving, false)
  p.submitDraft(); h.take('addNotice').reject(Error('timeout')); await tick()
  assert.equal(p.saveUnknown, true); assert.equal(p.form.noticeContent, 'A正文'); p.submitDraft(); await tick()
  assert.equal(h.calls.filter(c => c.name === 'addNotice').length, 2); assert.match(p.editorError, /不会自动重新提交/); p.$destroy()
})
test('S01 read-back requires matching ID, exact fields and next version, not just any version increase', async () => {
  const h = setup(), p = h.p
  h.start('已知'); p.form.noticeId = '101'; p.form.version = 2; p.submitDraft(); const write = h.take('updateNotice'), saved = { ...write.args[0], version: 3 }
  write.reject(Error('timeout')); await tick(); const first = p.verifyDraftSave(); h.take('getNotice').resolve({ data: { ...saved, noticeContent: '其他人修改' } }); await first
  assert.equal(p.saveUnknown, true); assert.equal(p.form.noticeContent, '已知正文')
  const second = p.verifyDraftSave(); h.take('getNotice').resolve({ data: saved }); await second
  assert.equal(p.saveUnknown, false); assert.equal(p.form.version, 3); assert.match(h.messages.at(-1), /内容及版本/); p.$destroy()
})
test('S01 audience preview and edit detail late replies are dropped after opening another draft', async () => {
  const h = setup(), p = h.p
  h.start('A'); const a = p.previewAudience(), preview = h.take('previewNoticeAudience')
  h.start('B'); const b = p.previewAudience(), latest = h.take('previewNoticeAudience')
  preview.reject(Error('old A')); await a; assert.equal(p.previewLoading, true); assert.equal(p.editorError, '')
  latest.resolve({ data: { recipientCount: 3 } }); await b; assert.equal(p.audiencePreview.recipientCount, 3)
  const old = p.handleUpdate({ noticeId: '101' }), read = h.take('getNotice'); h.start('C'); read.resolve({ data: { noticeId: '101', lifecycleStatus: 'DRAFT', noticeTitle: 'A' } }); await old
  assert.equal(p.form.noticeTitle, 'C'); p.$destroy()
})
test('S01 account change clears receipts and invalidates late save responses', async () => {
  const h = setup(), p = h.p; h.start('A'); p.submitDraft(); const write = h.take('addNotice')
  h.store.state.user.sessionRevision++; await tick(); write.resolve({ data: { ...write.args[0], noticeId: '101' } }); await tick()
  assert.equal(p.open, false); assert.equal(p.saveReceipts.length, 0); assert.equal(h.messages.length, 0); p.$destroy()
})
test('S03 filtered rows total is independent of overall read rate, including zero recipients and old server fallback', async () => {
  const h = setup(true), p = h.p
  p.open({ noticeId: '101', noticeTitle: 'A', recipientCount: 100 }); h.take('listNoticeReadUsers').resolve({ rows: [{ userId: '1' }], total: 3, summary: { readCount: 40, recipientCount: 100 } }); await tick()
  assert.equal(p.total, 3); assert.equal(p.readRate, '40%'); assert.equal(p.readCount, 40)
  p.queryParams.searchValue = 'none'; const query = p.getList(); h.take('listNoticeReadUsers').resolve({ rows: [], total: 0, summary: { readCount: 40, recipientCount: 100 } }); await query; assert.equal(p.readRate, '40%')
  const empty = p.getList(); h.take('listNoticeReadUsers').resolve({ rows: [], total: 0, summary: { readCount: 0, recipientCount: 0 } }); await empty; assert.equal(p.readRate, '无接收人')
  const legacy = p.getList(); h.take('listNoticeReadUsers').resolve({ rows: [], total: 8 }); await legacy; assert.equal(p.summaryReady, false); p.$destroy()
})
test('S03 A/B rows, summary, error and finally belong to their notice and request', async () => {
  const h = setup(true), p = h.p
  p.open({ noticeId: '101', noticeTitle: 'A' }); const a = h.take('listNoticeReadUsers')
  p.open({ noticeId: '102', noticeTitle: 'B' }); const b = h.take('listNoticeReadUsers')
  a.reject(Error('A error')); await tick(); assert.equal(p.loading, true); assert.equal(p.loadError, '')
  b.resolve({ rows: [{ userId: 'B' }], total: 1, summary: { readCount: 2, recipientCount: 5 } }); await tick(); assert.equal(p.readRate, '40%'); assert.equal(p.userList[0].userId, 'B')
  const old = p.getList(), r = h.take('listNoticeReadUsers'); p.handleClose(); r.resolve({ rows: [{ userId: 'old' }], total: 100, summary: { readCount: 100, recipientCount: 100 } }); await old
  assert.equal(p.userList.length, 0); assert.equal(p.summaryReady, false); p.$destroy()
})
test('S01 dynamic rich-text read-only follows save state and both notice templates compile', () => {
  const editor = fs.readFileSync(path.join(root, 'src/components/Editor/index.vue'), 'utf8')
  const script = compiler.parseComponent(editor).script.content, ast = require('@babel/parser').parse(script, { sourceType: 'module' })
  const watch = ast.program.body.find(n => n.type === 'ExportDefaultDeclaration').declaration.properties.find(n => n.key.name === 'watch').value.properties.find(n => n.key.name === 'readOnly')
  const method = vm.runInNewContext('({' + script.slice(watch.start, watch.end) + '})').readOnly
  const states = [], mock = { Quill: { enable: value => states.push(value) } }; method.call(mock, true); method.call(mock, false); assert.deepEqual(states, [false, true])
  for (const file of ['src/views/system/notice/index.vue', 'src/views/system/notice/ReadUsers.vue']) assert.deepEqual(compiler.compile(compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).template.content).errors, [])
})
