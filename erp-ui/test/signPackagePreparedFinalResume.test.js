const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')

const packagePage = read('src/views/oa/signPackage/index.vue')
const recordPanel = read('src/views/oa/signPackage/SignPackageRecordPanel.vue')
const taskDetail = read('src/views/oa/signTask/SignTaskDetailDrawer.vue')
const companyWork = read('src/views/oa/signTask/SignOnboardCompanyWork.vue')
const taskPage = read('src/views/oa/signTask/index.vue')

function methodBody(source, name) {
  const start = source.indexOf(`    ${name}(`)
  assert.ok(start >= 0, `${name} must exist`)
  const brace = source.indexOf('{', start)
  let depth = 0
  for (let index = brace; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] === '}') depth -= 1
    if (depth === 0) return source.slice(brace + 1, index)
  }
  throw new Error(`${name} body is incomplete`)
}

const isPrepared = new Function('row', methodBody(packagePage, 'isPreparedSignatureFirstFinalPackage'))
const isWaiting = new Function('row', methodBody(packagePage, 'isSignatureFirstWaitingCompanyPackage'))
const preparedPackage = {
  status: 'pending_company',
  signingSequence: 'SIGNATURE_FIRST',
  finalConfirmationStatus: 'PREPARED_NOT_SENT',
  finalDocumentVersion: 'FINAL-V1',
  finalDocumentRootHash: 'a'.repeat(64)
}
assert.strictEqual(isPrepared(preparedPackage), true,
  'a sealed signature-first final candidate must resume at explicit sending')
assert.strictEqual(isPrepared({ ...preparedPackage, finalDocumentRootHash: 'short' }), false,
  'prepared-final routing must fail closed when the root hash is invalid')
assert.strictEqual(isWaiting({
  status: 'pending_company',
  signingSequence: 'SIGNATURE_FIRST',
  finalConfirmationStatus: 'WAITING_COMPANY'
}), true, 'the employee one-signature checkpoint must resume at company work')

assert.ok(packagePage.includes('预览并发送最终文件') &&
  packagePage.includes("v-hasPermi=\"['oa:signTask:send']\"") &&
  packagePage.includes("query: { taskId }"),
'prepared finals must route to the existing task preview/send drawer with send permission')
assert.ok(packagePage.includes('继续选择公司和印章') &&
  packagePage.includes("query: { companyWorkPackageId: packageId }") &&
  packagePage.includes("'MANUAL_SIGN_EXCEL_IMPORT'"),
'Excel signature-first packages must resume in the dedicated company-work flow')
assert.ok(packagePage.includes('openGenericCompanyFinalization(latestPackage)') &&
  packagePage.includes("getSignTask(taskId)"),
'ordinary signature-first packages must be distinguished by task source before using the generic flow')
assert.ok(packagePage.includes("requestId: this.createRequestId('package-finalize')") &&
  packagePage.includes('expectedVersion,') &&
  packagePage.includes('expectedTaskVersion,') &&
  packagePage.includes('taskVersion: task.version'),
'the ordinary signature-first finalizer must freeze the request id and both optimistic-lock versions')
assert.ok(packagePage.includes('最终合同已生成但尚未发送') &&
  packagePage.includes('if (preparedPackage.taskId) this.openPreparedFinalTask(preparedPackage)'),
'ordinary finalization must continue to explicit preview/send instead of claiming the file was sent')
assert.ok(packagePage.includes("最终文件已生成，待发送") &&
  packagePage.includes('packageStatusStep(detail)'),
'generated-but-unsent packages must have an honest status and advanced company step')

assert.ok(taskDetail.includes('v-if="isSignatureFirstWaitingCompany"') &&
  taskDetail.includes("v-hasPermi=\"['oa:signTask:batchFinalize']\"") &&
  taskDetail.includes('@click="openSignatureFirstCompanyWork"'),
'the task drawer must not send staged waiting-company work to the legacy package finalizer')
assert.ok(taskDetail.includes('{{ currentStatusLabel }}') &&
  taskDetail.includes('唯一签名已完成，待选公司和印章'),
'the task drawer must describe the exact signature-first checkpoint')

assert.ok(companyWork.includes("focusPackageId: { type: [String, Number], default: '' }") &&
  companyWork.includes("String(row.packageId || '') === packageId") &&
  companyWork.includes('this.selectedKeys = [item.workKey]') &&
  companyWork.includes('this.dialogVisible = true'),
'the persistent company-work card must focus and open the exact package requested by another page')
assert.ok(taskPage.includes(':focus-package-id="$route.query.companyWorkPackageId || \'\'"'),
  'the task centre route must forward the requested package to the resumable work card')

const documentStatus = new Function('row', methodBody(recordPanel, 'documentStatus'))
assert.deepStrictEqual(documentStatus.call({ finalConfirmationStatus: () => 'PREPARED_NOT_SENT' }, { finalPdfUrl: '/final.pdf' }),
  { label: '已生成待发送', type: 'warning' })
assert.deepStrictEqual(documentStatus.call({ finalConfirmationStatus: () => 'PENDING' }, { finalPdfUrl: '/final.pdf' }),
  { label: '待员工确认', type: 'warning' })
assert.deepStrictEqual(documentStatus.call({ finalConfirmationStatus: () => 'CONFIRMED' }, { finalPdfUrl: '/final.pdf' }),
  { label: '已完成', type: 'success' })
assert.ok(recordPanel.includes("if (confirmationStatus === 'PREPARED_NOT_SENT') return '未发送'") &&
  recordPanel.includes("row.finalReadConfirmed === 'Y' ? '已打开' : '待打开'"),
'the contract record must distinguish HR sending from employee final-file opening')

console.log('sign package prepared-final resume tests passed')
