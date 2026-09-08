const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const componentSource = fs.readFileSync(
  path.resolve(__dirname, '../src/views/mobile/attendance/index.vue'),
  'utf8'
)

function loadComponent(runtime) {
  const match = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, 'mobile attendance component must expose a script block')
  const transformed = match[1]
    .replace(/import\s+[\s\S]*?\s+from\s+['"][^'"]+['"]\s*/g, '')
    .replace('export default', 'module.exports =')

  const policy = Object.assign({
    attendanceErrorText: () => '',
    clearAttendancePunchAttempt: () => true,
    dataOf: value => value,
    normalizeTodayContext: () => ({}),
    readAttendancePunchAttempt: () => ({ status: 'absent' }),
    reconcileAttendancePunch: () => ({ status: 'absent' }),
    resolvePunchGate: () => ({ ready: false }),
    stopMediaStream: () => {},
    validateLocation: () => ({ ok: true }),
    validatePhoto: () => ({ ok: true })
  }, runtime.policy || {})
  const sandbox = Object.assign({
    module: { exports: {} },
    exports: {},
    require: () => policy,
    getSelectedDeptContext: () => ({ deptId: 1 }),
    MobileAttendanceCorrection: {},
    MobileAttendanceLeave: {},
    Promise,
    Date,
    Math,
    Number,
    Object,
    Array,
    String,
    Blob,
    process: { env: {} }
  }, runtime)
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox, { filename: 'mobile-attendance.vue' })
  return sandbox.module.exports
}

function bind(component, target) {
  Object.keys(component.methods || {}).forEach(name => {
    target[name] = component.methods[name].bind(target)
  })
  return target
}

const bodyStyle = { overflow: 'auto', position: 'relative', top: '', width: '90%' }
const scrollCalls = []
const revokedUrls = []
let focusCalls = 0
const runtime = {
  document: { body: { style: bodyStyle } },
  window: {
    pageYOffset: 240,
    scrollY: 240,
    scrollTo: (x, y) => scrollCalls.push([x, y])
  },
  URL: {
    createObjectURL: () => 'blob:new-private-evidence',
    revokeObjectURL: value => revokedUrls.push(value)
  }
}
const component = loadComponent(runtime)
const target = bind(component, {
  evidenceUrl: 'blob:private-evidence',
  evidencePreviewOpen: false,
  evidencePreviewScrollY: 0,
  evidencePreviewBodyStyle: null,
  $refs: {
    evidencePreview: { focus: () => { focusCalls += 1 } }
  },
  $nextTick: callback => callback()
})

target.openEvidencePreview()
assert.strictEqual(target.evidencePreviewOpen, true, 'private evidence should open in the in-app preview')
assert.strictEqual(bodyStyle.position, 'fixed', 'opening should lock iPhone Safari background scrolling')
assert.strictEqual(bodyStyle.top, '-240px', 'scroll lock should preserve the current page offset')
assert.strictEqual(bodyStyle.width, '100%')
assert.strictEqual(focusCalls, 1, 'dialog should receive focus after opening')

target.closeEvidencePreview()
assert.strictEqual(target.evidencePreviewOpen, false)
assert.deepStrictEqual(bodyStyle, { overflow: 'auto', position: 'relative', top: '', width: '90%' }, 'closing should restore every prior inline body style')
assert.deepStrictEqual(scrollCalls, [[0, 240]], 'closing should restore the previous page scroll position')
assert.strictEqual(target.evidenceUrl, 'blob:private-evidence', 'closing the overlay must keep the Blob URL alive for the evidence thumbnail')
assert.deepStrictEqual(revokedUrls, [])

target.openEvidencePreview()
target.revokeUrl('evidenceUrl')
assert.strictEqual(target.evidencePreviewOpen, false, 'revoking evidence must first close the overlay')
assert.strictEqual(target.evidenceUrl, '')
assert.deepStrictEqual(revokedUrls, ['blob:private-evidence'], 'the private Blob URL must be revoked exactly once when retired')
assert.deepStrictEqual(bodyStyle, { overflow: 'auto', position: 'relative', top: '', width: '90%' })

const destroyBodyStyle = { overflow: '', position: '', top: '', width: '' }
const destroyRevokedUrls = []
const destroyComponent = loadComponent({
  document: { body: { style: destroyBodyStyle } },
  window: { pageYOffset: 18, scrollY: 18, scrollTo: () => {} },
  URL: {
    createObjectURL: () => '',
    revokeObjectURL: value => destroyRevokedUrls.push(value)
  }
})
const destroyTarget = bind(destroyComponent, {
  photoPreviewUrl: 'blob:pending-photo',
  evidenceUrl: 'blob:restored-private-evidence',
  evidencePreviewOpen: false,
  evidencePreviewScrollY: 0,
  evidencePreviewBodyStyle: null,
  $refs: {},
  $nextTick: callback => callback()
})
destroyTarget.openEvidencePreview()
destroyComponent.beforeDestroy.call(destroyTarget)
assert.strictEqual(destroyTarget.evidencePreviewOpen, false, 'component teardown must close the full-screen evidence preview')
assert.strictEqual(destroyTarget.photoPreviewUrl, '')
assert.strictEqual(destroyTarget.evidenceUrl, '')
assert.deepStrictEqual(
  destroyRevokedUrls,
  ['blob:pending-photo', 'blob:restored-private-evidence'],
  'component teardown must retire both private Blob URLs exactly once'
)
assert.deepStrictEqual(destroyBodyStyle, { overflow: '', position: '', top: '', width: '' }, 'component teardown must restore body scrolling')

function todayTarget(component, overrides) {
  return bind(component, Object.assign({
    loading: false,
    loadError: '',
    flowError: '',
    successMessage: '',
    busy: false,
    context: {},
    pending: { challengeToken: '', expiresAt: '', scheduleId: null, punchType: '', location: null },
    photo: null,
    photoCapturedAt: '',
    photoPreviewUrl: '',
    uploadProgress: 0,
    evidenceId: null,
    evidenceUrl: '',
    evidenceLoadError: '',
    evidencePreviewOpen: false,
    evidencePreviewScrollY: 0,
    evidencePreviewBodyStyle: null,
    serverAccepted: false,
    $store: { getters: { id: 1 } },
    $refs: {},
    $nextTick: callback => callback()
  }, overrides || {}))
}

async function shouldRestoreLatestPrivateEvidenceWithoutBlockingNextPunch() {
  const calls = { today: 0, evidence: [], created: [], revoked: [] }
  const context = {
    state: 'READY_OUT',
    canPunch: true,
    latestEvidenceId: 'evidence-200',
    latestPunch: { punchType: 'IN', serverPunchTime: '2026-08-21T08:01:02' }
  }
  const restoredComponent = loadComponent({
    policy: { normalizeTodayContext: () => context },
    getTodayAttendanceContext: () => {
      calls.today += 1
      return Promise.resolve({ data: context })
    },
    getPunchEvidenceContent: evidenceId => {
      calls.evidence.push(evidenceId)
      return Promise.resolve(new Blob(['private-watermark'], { type: 'image/jpeg' }))
    },
    URL: {
      createObjectURL: blob => {
        calls.created.push(blob)
        return 'blob:restored-evidence'
      },
      revokeObjectURL: value => calls.revoked.push(value)
    }
  })
  const restored = todayTarget(restoredComponent)

  await restored.loadToday()

  assert.strictEqual(restored.context, context, 'today context must remain available after evidence restoration')
  assert.strictEqual(restored.evidenceId, 'evidence-200')
  assert.strictEqual(restored.evidenceUrl, 'blob:restored-evidence')
  assert.strictEqual(restored.evidenceLoadError, '')
  assert.strictEqual(restored.serverAccepted, false, 'restoring historical evidence must not block the next OUT punch')
  assert.deepStrictEqual(calls.evidence, ['evidence-200'])

  restored.openEvidencePreview()
  assert.strictEqual(restored.evidencePreviewOpen, true, 'evidence restored after a page reload must still open full-screen')
  restored.closeEvidencePreview()

  await restored.loadToday()
  assert.deepStrictEqual(calls.evidence, ['evidence-200'], 'same evidence id with a live Blob URL must not be downloaded again')
}

async function shouldKeepTodayContextAndOfferRetryWhenEvidenceFails() {
  let evidenceAttempts = 0
  let todayCalls = 0
  const context = {
    state: 'READY_OUT',
    canPunch: true,
    latestEvidenceId: 'evidence-500',
    latestPunch: { punchType: 'IN', serverPunchTime: '2026-08-21T08:01:02' }
  }
  const failedComponent = loadComponent({
    policy: { normalizeTodayContext: () => context },
    getTodayAttendanceContext: () => {
      todayCalls += 1
      return Promise.resolve({ data: context })
    },
    getPunchEvidenceContent: () => {
      evidenceAttempts += 1
      if (evidenceAttempts === 1) return Promise.reject(new Error('network'))
      return Promise.resolve(new Blob(['private-watermark'], { type: 'image/jpeg' }))
    },
    URL: {
      createObjectURL: () => 'blob:retried-evidence',
      revokeObjectURL: () => {}
    }
  })
  const failed = todayTarget(failedComponent)

  await failed.loadToday()

  assert.strictEqual(failed.context, context, 'evidence failure must not clear the successful today response')
  assert.strictEqual(failed.loadError, '', 'evidence failure must not become a today-context failure')
  assert.strictEqual(failed.evidenceId, 'evidence-500')
  assert.strictEqual(failed.evidenceUrl, '')
  assert.match(failed.evidenceLoadError, /证据加载失败.*重试/)
  assert.strictEqual(failed.serverAccepted, false)

  await failed.retryEvidence()

  assert.strictEqual(failed.evidenceUrl, 'blob:retried-evidence')
  assert.strictEqual(failed.evidenceLoadError, '')
  assert.strictEqual(failed.successMessage, '', 'retrying historical evidence must not claim a new punch success')
  assert.strictEqual(failed.serverAccepted, false)
  assert.strictEqual(todayCalls, 1, 'historical evidence retry must not recursively reload today context')
}

async function shouldRejectLateEvidenceAfterAnAbaDeptSwitch() {
  let selectedDeptId = 1
  let resolveEvidence
  const created = []
  const fencedComponent = loadComponent({
    getSelectedDeptContext: () => ({ deptId: selectedDeptId }),
    getPunchEvidenceContent: () => new Promise(resolve => { resolveEvidence = resolve }),
    URL: {
      createObjectURL: blob => {
        created.push(blob)
        return 'blob:must-not-be-created'
      },
      revokeObjectURL: () => {}
    }
  })
  const fenced = todayTarget(fencedComponent, { attendanceContextEpoch: 7 })
  const owner = fenced.punchOwner()
  const request = fenced.loadEvidence('evidence-old-shop', { owner, epoch: 7 })

  selectedDeptId = 2
  fenced.nextAttendanceContextEpoch()
  selectedDeptId = 1
  fenced.nextAttendanceContextEpoch()
  resolveEvidence(new Blob(['old-shop-private-watermark'], { type: 'image/jpeg' }))

  await assert.rejects(request, /ATTENDANCE_CONTEXT_CHANGED/)
  assert.strictEqual(fenced.evidenceUrl, '', 'late evidence from the old generation must not appear after A→B→A')
  assert.deepStrictEqual(created, [], 'a rejected old-generation Blob must never receive a browser URL')
}

async function shouldReleaseRetiredGenerationLocks() {
  const lifecycleComponent = loadComponent({})
  const lifecycle = todayTarget(lifecycleComponent, {
    attendanceContextEpoch: 9,
    loading: true,
    busy: true,
    recordsLoading: true,
    punchStatusChecking: true
  })

  assert.strictEqual(lifecycle.nextAttendanceContextEpoch(), 10)
  assert.strictEqual(lifecycle.loading, false, 'retiring a today request must release its loading lock')
  assert.strictEqual(lifecycle.busy, false, 'retiring a location/camera/punch request must release its busy lock')
  assert.strictEqual(lifecycle.recordsLoading, false)
  assert.strictEqual(lifecycle.punchStatusChecking, false)
}

async function shouldRetireEvidenceMissingFromNewTodayContext() {
  const revoked = []
  const evidenceComponent = loadComponent({
    URL: {
      createObjectURL: () => '',
      revokeObjectURL: value => revoked.push(value)
    }
  })
  const evidence = todayTarget(evidenceComponent, {
    attendanceContextEpoch: 4,
    evidenceId: 'evidence-yesterday',
    evidenceUrl: 'blob:evidence-yesterday'
  })
  const owner = evidence.punchOwner()

  await evidence.restoreLatestEvidence(null, { owner, epoch: 4 })

  assert.strictEqual(evidence.evidenceId, null, 'a today response without evidence must clear the previous evidence id')
  assert.strictEqual(evidence.evidenceUrl, '', 'a today response without evidence must hide the previous evidence image')
  assert.deepStrictEqual(revoked, ['blob:evidence-yesterday'])
}

Promise.resolve()
  .then(shouldRestoreLatestPrivateEvidenceWithoutBlockingNextPunch)
  .then(shouldKeepTodayContextAndOfferRetryWhenEvidenceFails)
  .then(shouldRejectLateEvidenceAfterAnAbaDeptSwitch)
  .then(shouldReleaseRetiredGenerationLocks)
  .then(shouldRetireEvidenceMissingFromNewTodayContext)
  .then(() => console.log('attendance evidence preview tests passed'))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
