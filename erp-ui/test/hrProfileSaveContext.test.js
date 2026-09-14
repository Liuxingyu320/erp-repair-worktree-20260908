const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const compiler = require('vue-template-compiler')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness(mobile = false) {
  const file = path.resolve(__dirname, '../src/views', mobile ? 'mobile/hr/employee/index.vue' : 'hr/components/HrEmployeeList.vue')
  const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
  assert.deepEqual(compiler.compile(descriptor.template.content).errors, [])
  const imports = [], calls = [], messages = [], errors = []
  const script = descriptor.script.content.replace(/import\s+([\s\S]*?)\s+from\s+["'][^"']+["']\s*/g, (_, names) => {
    imports.push(...names.replace(/[{}]/g, '').split(',').map(n => n.trim()).filter(Boolean)); return ''
  }).replace('export default', 'return')
  const globals = Object.fromEntries(imports.map(n => [n, () => {}]))
  const api = name => (...args) => { const pending = deferred(); calls.push({ name, args, ...pending }); return pending.promise }
  Object.assign(globals, { getSelectedDeptId: () => '10', updateHrEmployee: api('save'), getHrEmployee: api('read'),
    mobileHrErrorMessage: error => error.message })
  const definition = new Function('require', ...Object.keys(globals), script)(id => require(path.resolve(__dirname, '../src', id.slice(2))), ...Object.values(globals))
  const model = { $store: { state: { user: { id: '7', sessionRevision: 1 } }, dispatch: async () => {} },
    $message: { success: m => messages.push(m) }, $modal: { msgSuccess: m => messages.push(m) },
    $refs: { editDrawer: { applyServerErrors: error => errors.push(error.message) } },
    editOpen: true, editDetail: { userId: '1' }, detail: { userId: '1' }, profileEditGeneration: 1, saveLoading: false, detailRequestSequence: 0,
    editorOpen: true, editing: { userId: '1' }, editorTargetId: '1', editorReadEpoch: 1, profileSaveSequence: 0, saving: false, pageInactive: false,
    rows: [], message: '' }
  Object.entries(definition.methods).forEach(([name, fn]) => { model[name] = fn.bind(model) })
  model.getList = model.reload = () => Promise.resolve()
  const take = name => { const call = calls.find(c => c.name === name && !c.used); assert.ok(call, name); call.used = true; return call }
  const save = () => mobile ? model.saveProfile({ userId: model.editorTargetId, patch: { employeeName: 'edited' } }) :
    model.handleSaveProfile({ userId: model.editDetail.userId, employeeName: 'edited' })
  const open = id => {
    if (mobile) { model.invalidateEditorReads(); model.editorTargetId = id; model.editing = { userId: id }; model.editorOpen = true }
    else model.handleEditProfile({ userId: id })
  }
  return { model, calls, messages, errors, definition, take, save, open }
}
for (const mobile of [false, true]) for (const rejected of [false, true]) test(`${mobile ? 'mobile' : 'PC'} old save ${rejected ? 'error' : 'success'} never closes or unlocks B`, async () => {
  const h = harness(mobile), first = h.save(), old = h.take('save')
  h.open('2'); const second = h.save(), current = h.take('save')
  if (rejected) old.reject(Error('A error')); else old.resolve({ data: {} }); await first
  assert.equal(mobile ? h.model.editorOpen : h.model.editOpen, true)
  assert.equal(mobile ? h.model.saving : h.model.saveLoading, true)
  assert.equal(mobile ? h.model.editorTargetId : h.model.editDetail.userId, '2')
  assert.deepEqual(h.messages, [])
  assert.deepEqual(h.errors, [])
  current.reject(Error('B error')); await second
  assert.equal(mobile ? h.model.saving : h.model.saveLoading, false)
  if (!mobile) assert.deepEqual(h.errors, ['B error'])
})
test('PC readback of saved A cannot replace newly opened B detail', async () => {
  const h = harness(), pending = h.save()
  h.take('save').resolve({ data: {} }); await Promise.resolve(); await Promise.resolve()
  const read = h.take('read')
  h.open('2'); h.model.detail = { userId: '2', employeeName: 'B' }
  read.resolve({ data: { userId: '1', employeeName: 'A saved' } }); await pending
  assert.equal(h.model.detail.userId, '2')
  assert.equal(h.model.detail.employeeName, 'B')
})
for (const mobile of [false, true]) test(`${mobile ? 'mobile' : 'PC'} duplicate save emits one write with frozen payload`, async () => {
  const h = harness(mobile), pending = h.save(), write = h.take('save'); await h.save()
  assert.equal(h.calls.filter(c => c.name === 'save').length, 1)
  assert.equal(write.args[0], '1')
  write.reject(Error('failed')); await pending
  assert.equal(mobile ? h.model.editorOpen : h.model.editOpen, true)
})
