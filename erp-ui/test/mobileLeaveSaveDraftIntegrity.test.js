const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const { File } = require('buffer')
const filename = path.resolve(__dirname, '../src/views/mobile/attendance/MobileAttendanceLeave.vue')
const script = fs.readFileSync(filename, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
const compiled = babel.transformSync(script, { filename, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
const methodNames = ['createAttendanceLeaveDraft', 'updateAttendanceLeaveDraft', 'getAttendanceLeave', 'getAttendanceLeaveByClientRequest', 'uploadAttendanceLeaveAttachment', 'deleteAttendanceLeaveAttachment', 'submitAttendanceLeave']
const flush = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
function mount() {
  const pending = [], calls = []
  const state = { deptId: 9 }
  const api = Object.fromEntries(methodNames.map(name => [name, (...args) => {
    calls.push({ name, args })
    return new Promise((resolve, reject) => pending.push({ name, args, resolve, reject }))
  }]))
  Object.assign(api, { listAttendanceLeaveTypes: () => Promise.resolve({ data: [] }), listMyAttendanceLeaves: () => Promise.resolve({ data: [] }), listShopAttendanceLeaves: () => Promise.resolve({ data: [] }) })
  const module = { exports: {} }
  vm.runInNewContext(compiled, {
    module, exports: module.exports, Date, Promise, Object, Array, String, Number, Math,
    require(id) {
      if (id === '@/api/oa/attendanceV2') return api
      if (id === '@/utils/permission') return { checkPermi: () => true }
      if (id === '@/utils/shopContext') return { getSelectedDeptContext: () => ({ isStore: true, deptId: state.deptId }) }
      if (id === '@/utils/leaveBalanceUi') return require('../src/utils/leaveBalanceUi')
      if (id === './attendancePunchPolicy') return require('../src/views/mobile/attendance/attendancePunchPolicy')
      throw new Error(id)
    }
  }, { filename })
  const definition = module.exports.default
  const page = new Vue(Object.assign({}, definition, { beforeCreate() { this.$store = { getters: { id: 7 } } } }))
  page.$modal = { confirm: () => Promise.resolve() }
  page.openNew()
  Object.assign(page.form, { leaveTypeId: '1', startTime: '2026-09-13T09:00', endTime: '2026-09-13T10:00', reason: '原原因' })
  function take(name) {
    const index = pending.findIndex(x => x.name === name)
    assert.ok(index >= 0, `expected pending ${name}; actual ${pending.map(x => x.name)}`)
    return pending.splice(index, 1)[0]
  }
  return { page, state, calls, pending, definition, take,
    resolve(name, data) { take(name).resolve({ data }) },
    fail(name, error = new Error('网络失败')) { take(name).reject(error) },
    count(name) { return calls.filter(call => call.name === name).length },
    last(name) { return calls.filter(call => call.name === name).slice(-1)[0] }
  }
}
function detail(page, overrides) {
  return Object.assign({ leaveRequestId: 31, clientRequestId: page.form.clientRequestId, rowVersion: 1, status: 'DRAFT', leaveTypeId: '1', startTime: '2026-09-13T09:00', endTime: '2026-09-13T10:00', reason: '原原因', attachments: [] }, overrides)
}
async function existing(h, overrides) {
  const action = h.page.editRow({ leaveRequestId: 31 })
  h.resolve('getAttendanceLeave', detail(h.page, overrides))
  await action
  return h
}
const select = (page, ...files) => page.selectAttachments({ target: { files, value: 'fake-file-input' } })
const pdf = (name, body) => new File([body || 'PDF'], name, { type: 'application/pdf' })
const attachment = (id, version, name = 'proof.pdf') => ({ attachmentId: id, leaveRequestId: 31, requestRowVersion: version, originalName: name })
const scenarios = []
const test = (name, run) => scenarios.push({ name, run })

test('保存期间的新业务输入保留，下一次更新原草稿', async h => {
  const p = h.page.saveDraft(true)
  const client = h.page.form.clientRequestId
  Object.assign(h.page.form, { leaveTypeId: '2', startTime: '2026-09-14T08:00', endTime: '2026-09-14T11:00', reason: '新原因' })
  h.resolve('createAttendanceLeaveDraft', detail(h.page))
  await p
  assert.equal(h.page.form.leaveRequestId, 31)
  assert.equal(h.page.form.clientRequestId, client)
  assert.equal(h.page.form.reason, '新原因')
  assert.equal(h.page.form.leaveTypeId, '2')
  assert.equal(h.page.form.startTime, '2026-09-14T08:00')
  assert.equal(h.page.form.endTime, '2026-09-14T11:00')
  assert.equal(h.page.showForm, true)
  assert.equal(h.page.unsavedChanges, true)
  assert.equal(h.count('submitAttendanceLeave'), 0)
  const p2 = h.page.saveDraft(false)
  const sent = h.last('updateAttendanceLeaveDraft').args
  assert.equal(sent[0], 31)
  assert.equal(sent[1].rowVersion, 1)
  assert.equal(sent[1].reason, '新原因')
  h.resolve('updateAttendanceLeaveDraft', detail(h.page, Object.assign({}, sent[1], { rowVersion: 2 })))
  await p2
  assert.equal(h.page.showForm, false)
  assert.equal(h.page.unsavedChanges, false)
})

test('冻结文件队列，同名A/B分离，未开始C可移除', async h => {
  const a = pdf('same.pdf', 'A'), b = pdf('same.pdf', 'B'), c = pdf('C.pdf', 'C')
  select(h.page, a, c)
  const cId = h.page.pendingFiles[1].id
  const p = h.page.saveDraft(true)
  h.resolve('createAttendanceLeaveDraft', detail(h.page))
  await flush()
  assert.strictEqual(h.last('uploadAttendanceLeaveAttachment').args[2], a)
  select(h.page, b)
  h.page.removePending(cId)
  h.page.form.reason = '上传时新原因'
  h.resolve('uploadAttendanceLeaveAttachment', attachment(71, 2))
  await flush()
  h.resolve('getAttendanceLeave', detail(h.page, { rowVersion: 2, attachments: [attachment(71, 2)] }))
  await p
  assert.equal(h.count('uploadAttendanceLeaveAttachment'), 1)
  assert.equal(h.page.pendingFiles.length, 1)
  assert.strictEqual(h.page.pendingFiles[0].file, b)
  assert.equal(h.page.form.reason, '上传时新原因')
  assert.equal(h.page.form.attachments.length, 1)
  assert.equal(h.count('submitAttendanceLeave'), 0)
  assert.equal(h.page.showForm, true)
})

test('上传成功但刷新失败仍逐项确认，不重复上传', async h => {
  select(h.page, pdf('A.pdf'))
  const p = h.page.saveDraft(false)
  h.resolve('createAttendanceLeaveDraft', detail(h.page))
  await flush()
  h.resolve('uploadAttendanceLeaveAttachment', attachment(71, 2))
  await flush()
  h.fail('getAttendanceLeave')
  await p
  assert.equal(h.page.form.rowVersion, 2)
  assert.equal(h.page.pendingFiles.length, 0)
  assert.equal(h.page.form.attachments[0].attachmentId, 71)
  assert.equal(h.page.showForm, true)
  const p2 = h.page.saveDraft(false)
  assert.equal(h.last('updateAttendanceLeaveDraft').args[1].rowVersion, 2)
  h.resolve('updateAttendanceLeaveDraft', detail(h.page, { rowVersion: 3, attachments: [attachment(71, 2)] }))
  await p2
  assert.equal(h.count('uploadAttendanceLeaveAttachment'), 1)
})

test('上传未知可按原文件重试，保留原申请且先核对版本', async h => {
  const file = pdf('A.pdf'); select(h.page, file)
  const p = h.page.saveDraft(false)
  h.resolve('createAttendanceLeaveDraft', detail(h.page))
  await flush(); h.fail('uploadAttendanceLeaveAttachment'); await p
  assert.equal(h.page.form.leaveRequestId, 31)
  assert.equal(h.page.pendingFiles[0].state, 'retry')
  const p2 = h.page.saveDraft(false)
  h.fail('updateAttendanceLeaveDraft', new Error('LEAVE_DRAFT_VERSION_CONFLICT'))
  await flush(); h.resolve('getAttendanceLeave', detail(h.page, { rowVersion: 2, attachments: [attachment(71, 2)] }))
  await flush()
  assert.strictEqual(h.last('uploadAttendanceLeaveAttachment').args[2], file)
  assert.equal(h.last('uploadAttendanceLeaveAttachment').args[1], 2)
  h.resolve('uploadAttendanceLeaveAttachment', attachment(71, 2))
  await flush(); h.resolve('getAttendanceLeave', detail(h.page, { rowVersion: 2, attachments: [attachment(71, 2)] }))
  await p2
  assert.equal(h.page.pendingFiles.length, 0)
  assert.equal(h.page.form.attachments.length, 1)
  assert.equal(h.count('createAttendanceLeaveDraft'), 1)
})

test('创建响应丢失以客户端身份恢复，新编辑留待原单更新', async h => {
  const p = h.page.saveDraft(true)
  h.page.form.reason = '网络期间编辑'
  h.fail('createAttendanceLeaveDraft')
  await flush()
  assert.equal(h.last('getAttendanceLeaveByClientRequest').args[0], h.page.form.clientRequestId)
  h.resolve('getAttendanceLeaveByClientRequest', detail(h.page))
  await p
  assert.equal(h.page.form.reason, '网络期间编辑')
  assert.equal(h.page.form.leaveRequestId, 31)
  assert.equal(h.count('submitAttendanceLeave'), 0)
  const p2 = h.page.saveDraft(false)
  assert.equal(h.last('updateAttendanceLeaveDraft').args[1].reason, '网络期间编辑')
  h.resolve('updateAttendanceLeaveDraft', detail(h.page, { reason: '网络期间编辑', rowVersion: 2 }))
  await p2
  assert.equal(h.count('createAttendanceLeaveDraft'), 1)
})

test('确认未找到才重放原创建载荷，改变的输入不配旧指纹', async h => {
  const p = h.page.saveDraft(false)
  h.page.form.reason = '保留新原因'
  h.fail('createAttendanceLeaveDraft'); await flush()
  h.fail('getAttendanceLeaveByClientRequest', new Error('LEAVE_CLIENT_REQUEST_NOT_FOUND')); await p
  const original = h.calls.find(x => x.name === 'createAttendanceLeaveDraft').args[0]
  const p2 = h.page.saveDraft(false)
  h.fail('getAttendanceLeaveByClientRequest', new Error('LEAVE_CLIENT_REQUEST_NOT_FOUND')); await flush()
  assert.deepStrictEqual(h.last('createAttendanceLeaveDraft').args[0], original)
  h.resolve('createAttendanceLeaveDraft', detail(h.page)); await p2
  assert.equal(h.page.form.reason, '保留新原因')
  assert.equal(h.page.unsavedChanges, true)
})

test('创建核对失败不重复创建，不丢客户端身份', async h => {
  const client = h.page.form.clientRequestId
  const p = h.page.saveDraft(false); h.fail('createAttendanceLeaveDraft'); await flush(); h.fail('getAttendanceLeaveByClientRequest'); await p
  const p2 = h.page.saveDraft(false); h.fail('getAttendanceLeaveByClientRequest'); await p2
  assert.equal(h.count('createAttendanceLeaveDraft'), 1)
  assert.equal(h.page.form.clientRequestId, client)
  assert.equal(h.page.showForm, true)
  assert.equal(h.page.busy, false)
})

test('更新冲突不将新远端版本配旧本地内容', async h => {
  await existing(h)
  h.page.form.reason = '我的修改'
  const p = h.page.saveDraft(false)
  h.fail('updateAttendanceLeaveDraft', new Error('conflict')); await flush()
  h.resolve('getAttendanceLeave', detail(h.page, { rowVersion: 8, reason: '别人已修改' })); await p
  assert.equal(h.page.form.reason, '我的修改')
  assert.equal(h.page.form.rowVersion, 1)
  assert.match(h.page.error, /变化/)
  assert.equal(h.page.showForm, true)
})

test('正常无新编辑保存上传后提交，真实提交阶段锁输入', async h => {
  select(h.page, pdf('A.pdf'))
  const p = h.page.saveDraft(true); h.resolve('createAttendanceLeaveDraft', detail(h.page)); await flush()
  h.resolve('uploadAttendanceLeaveAttachment', attachment(71, 2)); await flush()
  h.resolve('getAttendanceLeave', detail(h.page, { rowVersion: 2, attachments: [attachment(71, 2)] })); await flush()
  assert.deepStrictEqual(h.last('submitAttendanceLeave').args, [31, 2])
  assert.equal(h.page.submitLocked, true)
  select(h.page, pdf('B.pdf')); assert.equal(h.page.pendingFiles.length, 0)
  h.resolve('submitAttendanceLeave', detail(h.page, { status: 'PENDING', rowVersion: 3, attachments: [attachment(71, 2)] })); await p
  assert.equal(h.page.showForm, false)
  assert.equal(h.page.busy, false)
  assert.match(h.page.success, /已由服务端提交/)
})

for (const status of ['SUBMITTING', 'APPROVED', 'REJECTED', 'CANCELLED']) {
  test(`提交结果不明恢复权威 ${status}，提示准确`, async h => {
    const p = h.page.saveDraft(true); h.resolve('createAttendanceLeaveDraft', detail(h.page)); await flush()
    h.fail('submitAttendanceLeave'); await flush()
    h.resolve('getAttendanceLeave', detail(h.page, { status, rowVersion: 2 })); await p
    assert.equal(h.page.form.status, status)
    assert.equal(h.page.showForm, false)
    assert.equal(h.page.error, '')
    assert.ok(!h.page.success.includes('已由服务端提交'))
    assert.ok(h.page.success.includes(status === 'SUBMITTING' ? '处理中' : h.page.statusLabel(status)))
  })
}

for (const status of ['DRAFT', 'RETURNED']) {
  test(`恢复权威仍为 ${status} 不伪称提交`, async h => {
    const p = h.page.saveDraft(true); h.resolve('createAttendanceLeaveDraft', detail(h.page)); await flush()
    h.fail('submitAttendanceLeave'); await flush(); h.resolve('getAttendanceLeave', detail(h.page, { status })); await p
    assert.equal(h.page.showForm, true); assert.equal(h.page.success, ''); assert.ok(h.page.error)
    h.page.form.reason = '提交失败后新修改'
    const p2 = h.page.saveDraft(true)
    h.resolve('getAttendanceLeave', detail(h.page, { status, rowVersion: 2 })); await p2
    assert.equal(h.page.form.reason, '提交失败后新修改')
    assert.equal(h.count('updateAttendanceLeaveDraft'), 0)
    assert.equal(h.count('submitAttendanceLeave'), 1)
    assert.equal(h.page.unsavedChanges, true)
  })
}

test('提交恢复错单拒绝，后续查询失败不开始草稿写入', async h => {
  const p = h.page.saveDraft(true); h.resolve('createAttendanceLeaveDraft', detail(h.page)); await flush()
  h.fail('submitAttendanceLeave'); await flush(); h.resolve('getAttendanceLeave', detail(h.page, { leaveRequestId: 99, status: 'PENDING' })); await p
  assert.equal(h.page.form.leaveRequestId, 31); assert.equal(h.page.showForm, true); assert.equal(h.page.success, '')
  const p2 = h.page.saveDraft(true); h.fail('getAttendanceLeave'); await p2
  assert.equal(h.count('updateAttendanceLeaveDraft'), 0)
  assert.equal(h.count('createAttendanceLeaveDraft'), 1)
})

test('A详情慢于B，新建和关闭使旧读取失效', async h => {
  const a = h.page.editRow({ leaveRequestId: 31 }), b = h.page.editRow({ leaveRequestId: 32 })
  const reqA = h.take('getAttendanceLeave'), reqB = h.take('getAttendanceLeave')
  reqB.resolve({ data: detail(h.page, { leaveRequestId: 32, reason: 'B' }) }); await b
  reqA.resolve({ data: detail(h.page) }); await a
  assert.equal(h.page.form.leaveRequestId, 32); assert.equal(h.page.form.reason, 'B')
  const old = h.page.editRow({ leaveRequestId: 31 }); h.page.openNew(); h.page.form.reason = '新建'
  h.fail('getAttendanceLeave'); await old
  assert.equal(h.page.form.reason, '新建'); assert.equal(h.page.error, ''); assert.equal(h.page.busy, false)
  const closed = h.page.editRow({ leaveRequestId: 31 }); h.page.closeForm()
  h.resolve('getAttendanceLeave', detail(h.page)); await closed
  assert.equal(h.page.showForm, false)
})

test('加载B期间不能保存旧A', async h => {
  await existing(h)
  const b = h.page.editRow({ leaveRequestId: 32 }); await h.page.saveDraft(false)
  assert.equal(h.count('updateAttendanceLeaveDraft'), 0)
  h.resolve('getAttendanceLeave', detail(h.page, { leaveRequestId: 32 })); await b
  assert.equal(h.page.form.leaveRequestId, 32)
})

test('旧保存结束不关闭新表单，不发后续上传', async h => {
  select(h.page, pdf('old.pdf'))
  const p = h.page.saveDraft(false); const old = detail(h.page)
  h.page.openNew(); h.page.form.reason = '新表单'
  h.resolve('createAttendanceLeaveDraft', old); await p
  assert.equal(h.page.form.reason, '新表单'); assert.equal(h.page.form.leaveRequestId, null)
  assert.equal(h.page.showForm, true); assert.equal(h.page.busy, false); assert.equal(h.count('uploadAttendanceLeaveAttachment'), 0)
})

test('keep-alive 返回保留新输入且核对原创建结果', async h => {
  const p = h.page.saveDraft(false); const old = detail(h.page)
  h.page.form.reason = '离开前新输入'; h.definition.deactivated.call(h.page)
  h.resolve('createAttendanceLeaveDraft', old); await p
  assert.equal(h.page.busy, false); assert.equal(h.page.form.reason, '离开前新输入')
  h.definition.activated.call(h.page)
  const p2 = h.page.saveDraft(false); h.resolve('getAttendanceLeaveByClientRequest', old); await p2
  assert.equal(h.page.form.leaveRequestId, 31); assert.equal(h.page.form.reason, '离开前新输入')
  assert.equal(h.count('createAttendanceLeaveDraft'), 1)
})

test('门店A→B→A使旧保存失效，之后可核对恢复', async h => {
  const p = h.page.saveDraft(false); const old = detail(h.page)
  h.state.deptId = 10; h.page.handleLeaveContextChange(); h.state.deptId = 9; h.page.handleLeaveContextChange()
  h.resolve('createAttendanceLeaveDraft', old); await p
  assert.equal(h.page.form.leaveRequestId, null); assert.equal(h.page.showForm, true)
  const p2 = h.page.saveDraft(false); h.resolve('getAttendanceLeaveByClientRequest', old); await p2
  assert.equal(h.page.form.leaveRequestId, 31)
})

test('附件删除保留删除前及期间业务编辑和新增文件', async h => {
  await existing(h, { attachments: [attachment(71, 1)] })
  h.page.form.reason = '删除前修改'
  const p = h.page.removeAttachment(h.page.form.attachments[0]); await flush()
  assert.deepStrictEqual(h.last('deleteAttendanceLeaveAttachment').args, [31, 71, 1])
  h.page.form.endTime = '2026-09-13T12:00'; const b = pdf('B.pdf'); select(h.page, b)
  h.resolve('deleteAttendanceLeaveAttachment', detail(h.page, { rowVersion: 2 })); await p
  assert.equal(h.page.form.reason, '删除前修改'); assert.equal(h.page.form.endTime, '2026-09-13T12:00')
  assert.equal(h.page.form.attachments.length, 0); assert.strictEqual(h.page.pendingFiles[0].file, b)
  assert.equal(h.page.unsavedChanges, true)
})

test('删除确认迟到不写新表单，不解开新保存busy', async h => {
  await existing(h, { attachments: [attachment(71, 1)] })
  let confirm; h.page.$modal.confirm = () => new Promise(resolve => { confirm = resolve })
  const p = h.page.removeAttachment(h.page.form.attachments[0])
  const save = h.page.saveDraft(false)
  confirm(); await p
  assert.equal(h.page.busy, true); assert.equal(h.count('deleteAttendanceLeaveAttachment'), 0)
  h.resolve('updateAttendanceLeaveDraft', detail(h.page, { rowVersion: 2, attachments: [attachment(71, 1)] })); await save
})

test('错误创建身份不污染表单或启动附件上传', async h => {
  select(h.page, pdf('A.pdf'))
  const p = h.page.saveDraft(false)
  h.resolve('createAttendanceLeaveDraft', detail(h.page, { clientRequestId: 'wrong' })); await flush()
  h.fail('getAttendanceLeaveByClientRequest'); await p
  assert.equal(h.page.form.leaveRequestId, null); assert.equal(h.count('uploadAttendanceLeaveAttachment'), 0)
  assert.equal(h.page.pendingFiles.length, 1); assert.ok(h.page.error)
})

async function run() {
  const completed = []
  for (const scenario of scenarios) {
    const h = mount()
    let timer
    try {
      await Promise.race([scenario.run(h), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('scenario did not finish: ' + scenario.name)), 4000) })])
      await flush()
      assert.equal(h.pending.length, 0, 'unsettled API calls in ' + scenario.name)
      assert.equal(h.page.busy, false, 'busy leaked in ' + scenario.name)
      completed.push(scenario.name)
    } catch (error) { error.message = scenario.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer); h.page.$destroy() }
  }
  assert.equal(completed.length, scenarios.length)
  console.log(`mobileLeaveSaveDraftIntegrity: ${completed.length} real Vue scenarios passed; 0 failed; 0 skipped`)
}
run().catch(error => { console.error(error); process.exitCode = 1 })
