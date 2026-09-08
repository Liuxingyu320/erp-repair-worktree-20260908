const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')

const detail = read('src/views/oa/signTask/SignTaskDetailDrawer.vue')
const companyDialog = read('src/views/oa/signTask/SignOnboardCompanyWorkDialog.vue')
const companyCard = read('src/views/oa/signTask/SignOnboardCompanyWork.vue')
const page = read('src/views/oa/signTask/index.vue')

assert.ok(detail.includes('sendSignTaskBatch') && detail.includes("taskIds: Object.freeze([taskId])"),
  'prepared staged finals must use the existing task batch-send endpoint with the same task id')
assert.ok(detail.includes('SEND_STAGED_FINAL') && detail.includes("this.createRequestId('final-send')"),
  'final sending must freeze a replayable request id independently from ordinary task sending')
assert.ok(detail.includes('发送最终文件（员工仅确认）') &&
  detail.includes("v-if=\"isPreparedSignatureFirstFinal\"") &&
  detail.includes("v-hasPermi=\"['oa:signTask:send']\""),
  'the task drawer must expose an explicitly permission-gated final-file send action')
assert.ok(detail.includes("task.status === 'PENDING_COMPANY' && !isPreparedSignatureFirstFinal"),
  'ordinary pending-company tasks must keep the existing company/seal action')
assert.ok(detail.includes('员工只需确认最终文件，不会再次签名') &&
  detail.includes('员工仅需确认文件，不再签名'),
  'both confirmation and success copy must state that the employee does not sign twice')
assert.ok(detail.includes('downloadFinalSignPackageDocument') &&
  detail.includes('? downloadFinalSignPackageDocument') &&
  detail.includes(': downloadSignPackageDocument'),
  'HR preview must open a whitelisted final PDF while ordinary package preview remains unchanged')

const eligibilityMatch = detail.match(
  /    isPreparedSignatureFirstFinal\(\) \{\n([\s\S]*?)\n    \},\n    shouldPreviewFinalDocument/
)
assert.ok(eligibilityMatch, 'prepared-final eligibility must remain executable in isolation')
const isPreparedSignatureFirstFinal = new Function(eligibilityMatch[1])
const prepared = {
  businessActionsAllowed: true,
  task: { status: 'PENDING_COMPANY', sourceType: 'MANUAL_SIGN_EXCEL_IMPORT' },
  signPackage: {
    status: 'pending_company',
    signingSequence: 'SIGNATURE_FIRST',
    finalConfirmationStatus: 'PREPARED_NOT_SENT',
    finalDocumentVersion: 'SP-31-FINAL-V1',
    finalDocumentRootHash: 'a'.repeat(64)
  }
}
assert.strictEqual(isPreparedSignatureFirstFinal.call(prepared), true,
  'the exact generated-but-not-sent staged final must expose final sending')
assert.strictEqual(isPreparedSignatureFirstFinal.call({
  ...prepared,
  task: { status: 'PENDING_COMPANY', sourceType: 'LIFECYCLE' }
}), true, 'an ordinary signature-first package with a sealed final candidate must also allow explicit final sending')
assert.strictEqual(isPreparedSignatureFirstFinal.call({
  ...prepared,
  signPackage: { ...prepared.signPackage, finalConfirmationStatus: 'WAITING_COMPANY' }
}), false, 'a package that still needs company selection must not be sendable')
assert.strictEqual(isPreparedSignatureFirstFinal.call({
  ...prepared,
  signPackage: { ...prepared.signPackage, finalDocumentVersion: '' }
}), false, 'missing final output must fail closed')
assert.strictEqual(isPreparedSignatureFirstFinal.call({
  ...prepared,
  signPackage: { ...prepared.signPackage, finalDocumentRootHash: '' }
}), false, 'an unsealed final document set must fail closed')
assert.strictEqual(isPreparedSignatureFirstFinal.call({
  ...prepared,
  businessActionsAllowed: false
}), false, 'a read-only task detail must not expose the mutation')

const previewEligibilityMatch = detail.match(
  /    shouldPreviewFinalDocument\(\) \{\n([\s\S]*?)\n    \},\n    terminalResolutionPermission/
)
assert.ok(previewEligibilityMatch, 'final-preview state whitelist must remain executable in isolation')
const shouldPreviewFinalDocument = new Function(previewEligibilityMatch[1])
const finalEvidence = {
  finalDocumentVersion: 'SP-31-FINAL-V1',
  finalDocumentRootHash: 'b'.repeat(64)
}
;[
  { status: 'pending_company', finalConfirmationStatus: 'PREPARED_NOT_SENT' },
  { status: 'pending_final_confirm', finalConfirmationStatus: 'PENDING' },
  { status: 'signed', finalConfirmationStatus: 'CONFIRMED' }
].forEach(state => {
  assert.strictEqual(shouldPreviewFinalDocument.call({
    signPackage: { ...finalEvidence, ...state }
  }), true, `${state.status}/${state.finalConfirmationStatus} must preview the final document`)
})
;[
  { status: 'pending_company', finalConfirmationStatus: 'WAITING_COMPANY' },
  { status: 'pending_final_confirm', finalConfirmationStatus: 'PREPARED_NOT_SENT' },
  { status: 'signed', finalConfirmationStatus: 'PENDING' },
  { status: 'pending_sign', finalConfirmationStatus: 'PENDING' }
].forEach(state => {
  assert.strictEqual(shouldPreviewFinalDocument.call({
    signPackage: { ...finalEvidence, ...state }
  }), false, `${state.status}/${state.finalConfirmationStatus} must fail closed`)
})
assert.strictEqual(shouldPreviewFinalDocument.call({
  signPackage: { ...finalEvidence, status: 'signed', finalConfirmationStatus: 'CONFIRMED', finalDocumentRootHash: 'short' }
}), false, 'invalid final root evidence must fail closed')

assert.ok(companyDialog.includes('预览并发送最终文件') &&
  companyDialog.includes("this.$emit('open-task', normalized)"),
  'successful company/seal generation must provide a direct path to preview and send its task')
assert.ok(companyDialog.includes('row.taskId = this.normalizeId(item.taskId)') &&
  companyDialog.includes("/^[1-9]\\d{0,18}$/.test"),
  'execution result task ids must be normalized and validated before opening a task')
assert.ok(companyCard.includes('@open-task="handleOpenTask"') &&
  companyCard.includes("this.$emit('open-task', taskId)"),
  'the dedicated company work card must forward the generated task safely')
assert.ok(page.includes('@open-task="openTask"'),
  'the task centre must open the existing detail drawer from the company work result')

console.log('prepared staged final send entry tests passed')
