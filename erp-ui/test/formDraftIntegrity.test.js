const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const smartFill = require('../src/utils/reimbursementSmartFill')

function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}
function load(relative, mocks = {}) {
  const file = path.resolve(__dirname, '../src', relative)
  const source = fs.readFileSync(file, 'utf8')
  const script = file.endsWith('.vue') ? source.match(/<script>([\s\S]*?)<\/script>/)[1] : source
  const code = babel.transformSync(script, { filename: file, babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, process: { env: {} }, Promise,
    require(name) {
      if (mocks[name]) return mocks[name]
      if (name === '@/mixins/approvalCommandRecovery') return { createApprovalCommandRecovery: () => ({}) }
      if (name === '@/mixins/reimbursementWithdrawRecovery') return { createReimbursementWithdrawRecovery: () => ({}) }
      if (name === '@/mixins/reimbursementExportRecovery') return { default: {}, __esModule: true }
      if (name === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (name === '@/utils/approvalCommandRecovery') return require('../src/utils/approvalCommandRecovery')
      if (name === '@/utils/todoRouteParams') return require('../src/utils/todoRouteParams')
      if (name === '@/utils/todoActionReturn') return require('../src/utils/todoActionReturn')
      if (name === '@/utils/shopContext') return { getSelectedDeptId: () => '10' }
      if (name === '@/utils/uploadProgress') return load('utils/uploadProgress.js')
      if (name === 'element-ui/packages/upload/src/ajax') return () => { throw new Error('unexpected upload transport') }
      if (name === '@/utils/reimbursementSmartFill') return smartFill
      if (name === './mobileReimbursementState') return require('../src/views/mobile/oa/reimbursement/mobileReimbursementState')
      if (name === '@/utils/sessionMode') return { buildSessionAuthHeaders: () => ({}), shouldUseSessionCredentials: () => false }
      if (name === '@/utils/urlSecurity') return { sanitizeFileUrl: value => value }
      if (name === '@/utils/requestSecurity') return { safeTrustedApiUrl: () => '/file/upload' }
      if (name === '@/utils/auth') return { getToken: () => '' }
      return {}
    }
  }, { filename: file })
  return module.exports.default || module.exports
}
function harness(component) {
  const instance = { $store: {getters:{id:'7'},state:{user:{sessionRevision:1}}}, $route: {fullPath:'/oa/reimbursement',query:{}}, $refs: {}, $modal: { confirm: () => Promise.resolve(), msgSuccess() {}, msgError() {}, msgWarning() {} },
    resetForm() {}, $nextTick: () => Promise.resolve() }
  Object.entries(component.methods).forEach(([key, fn]) => { if (typeof fn === 'function') instance[key] = fn.bind(instance) })
  Object.assign(instance, component.data.call(instance))
  Object.entries(component.computed || {}).forEach(([key, fn]) => {
    if (typeof fn === 'function') Object.defineProperty(instance, key, { get: fn.bind(instance), configurable: true })
  })
  return instance
}
const tick = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }

async function run() {
  let physicalDeletes = 0
  const upload = harness(load('components/FileUpload/index.vue', { '@/api/system/file': { deleteFile() { physicalDeletes++; return Promise.resolve() } } }))
  const original = [{ url: '/a.pdf' }, { url: '/b.pdf' }, { url: '/c.pdf' }]
  upload.fileList = original.slice()
  const emitted = []
  upload.$emit = (type, value) => emitted.push(value)
  upload.handleDelete(original[0]); upload.handleDelete(original[1]); upload.handleDelete(original[0])
  assert.deepStrictEqual(upload.fileList.map(x => x.url), ['/c.pdf'])
  assert.equal(physicalDeletes, 0, 'canceling business form must retain original physical files')
  assert.equal(original.length, 3)
  assert.equal(emitted.length, 2)
  const duplicateA = { uid: 'a', url: '/same.pdf' }, duplicateB = { uid: 'b', url: '/same.pdf' }
  upload.fileList = [duplicateA, duplicateB]
  upload.handleDelete(duplicateA); upload.handleDelete(duplicateA)
  assert.equal(upload.fileList.length, 1); assert.equal(upload.fileList[0].uid, 'b')


  const requests = {}; const saved = []
  function request(id, type) { requests[`${id}:${type}`] = deferred(); return requests[`${id}:${type}`].promise }
  const roles = harness(load('views/system/role/index.vue', {
    '@/api/system/role': { getRole: id => request(id, 'role'), deptTreeSelect: id => request(id, 'dept'),
      addRole: payload => { saved.push(payload); return Promise.resolve() }, updateRole: payload => { saved.push(payload); return Promise.resolve() } },
    '@/api/system/menu': { roleMenuTreeselect: id => request(id, 'menu') }
  }))
  const menu = []; const dept = []
  roles.$refs = { form: { validate: fn => fn(true) }, menu: {
    setCheckedKeys: keys => menu.splice(0, menu.length, ...keys), setChecked: key => menu.push(key),
    getCheckedKeys: () => menu.slice(), getHalfCheckedKeys: () => []
  }, dept: { setCheckedKeys: keys => dept.splice(0, dept.length, ...keys), getCheckedKeys: () => dept.slice(), getHalfCheckedKeys: () => [] } }
  roles.getList = () => {}
  const a = roles.handleUpdate({ roleId: 1 })
  roles.submitForm(); assert.equal(saved.length, 0)
  requests['1:role'].resolve({ data: { roleId: 1, roleName: 'A', roleKey: 'a', dataScope: '5' } })
  await tick(); roles.submitForm(); assert.equal(saved.length, 0, 'detail alone cannot enable saving')
  const b = roles.handleCopy({ roleId: 2 })
  requests['2:role'].resolve({ data: { roleId: 2, roleName: 'B', roleKey: 'b', dataScope: '2' } })
  requests['2:menu'].resolve({ menus: [], checkedKeys: [22] })
  requests['2:dept'].resolve({ depts: [], checkedKeys: [202] })
  await b
  requests['1:menu'].resolve({ menus: [], checkedKeys: [11] }); requests['1:dept'].resolve({ depts: [], checkedKeys: [101] })
  await a
  assert.equal(roles.form.roleName, 'B（副本）'); assert.deepStrictEqual(menu, [22]); assert.deepStrictEqual(dept, [202])
  roles.submitForm(); roles.submitForm(); await tick()
  assert.equal(saved.length, 1); assert.equal(saved[0].roleId, undefined); assert.deepStrictEqual(Array.from(saved[0].menuIds), [22])
  const c = roles.handleUpdate({ roleId: 3 })
  requests['3:role'].reject(new Error('network')); await c
  roles.submitForm(); assert.equal(saved.length, 1); assert.equal(roles.roleLoadFailed, true)

  for (const mobile of [false, true]) {
    let calls = 0; let deletion = deferred(); let remote = null
    const component = load(`views/${mobile ? 'mobile/' : ''}oa/reimbursement/index.vue`, {
      '@/api/oa/reimbursement': { deleteReimbursementInvoice: () => { calls++; return deletion.promise }, getReimbursement: () => Promise.resolve({ data: remote }) }
    })
    const page = harness(component)
    page.mode = 'form'; page.formVisible = true
    const invoice = { invoiceId: 50, itemId: 10, originalName: 'invoice.pdf' }
    const local = { reimbursementId: 1, title: 'unsaved', purpose: 'local purpose', rowVersion: 2,
      items: [{ itemId: 10, claimedAmount: '1' }, { itemId: 20, claimedAmount: '99' }, { claimedAmount: '7', sourceInvoiceId: 50 }], invoices: [invoice] }
    remote = { reimbursementId: 1, title: 'old', purpose: 'old purpose', rowVersion: 3, items: [{ itemId: 20, claimedAmount: '2' }], invoices: [] }
    page.form = local; page.captureFormBaseline()
    const remove = mobile ? page.deleteInvoice : page.removeInvoice
    const pending = remove(invoice); await tick()
    const duplicate = remove(invoice); await duplicate
    page.form.title = 'typed during request'
    deletion.resolve({ data: remote }); await pending
    assert.equal(calls, 1)
    assert.equal(page.form.title, 'typed during request'); assert.equal(page.form.purpose, 'local purpose')
    assert.equal(page.form.items.length, 2); assert.equal(page.form.items[0].claimedAmount, '99')
    assert.equal(page.form.items[1].claimedAmount, '7'); assert.equal(page.form.items[1].sourceInvoiceId, null)
    assert.equal(page.form.rowVersion, 3); assert.equal(mobile ? page.isFormDirty : page.formDirty, true)
    deletion = deferred(); page.form = { ...local, invoices: [invoice] }
    const stale = remove(invoice); await tick()
    page.formEpoch++; page.form = { reimbursementId: 99, title: 'another', items: [], invoices: [] }
    deletion.resolve({ data: remote }); await stale
    assert.equal(page.form.title, 'another')
    deletion = deferred(); page.form = { ...local, title: 'retain on conflict', invoices: [invoice] }
    remote = { ...remote, invoices: [invoice] }
    const conflict = remove(invoice); await tick(); deletion.reject(new Error('version conflict')); await conflict
    assert.equal(page.form.title, 'retain on conflict'); assert.equal(page.form.items.length, 3)
  }
  const contract = harness(load('views/oa/laborContract/index.vue'))
  contract.contractForm = { employeeIdCard: 'old-card', employeePhone: 'old-phone', identityManuallyVerified: true }
  contract.employeeOptions = [{ userId: 2, nickName: 'B', deptId: 202 }]
  contract.handleEmployeeChange('2')
  assert.equal(contract.contractForm.employeeName, 'B'); assert.equal(contract.contractForm.employeeIdCard, '')
  assert.equal(contract.contractForm.employeePhone, ''); assert.equal(contract.contractForm.identityManuallyVerified, false)
  console.log('formDraftIntegrity: real component methods passed (attachments, role async/copy, PC/H5 deletion, employee switch)')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
