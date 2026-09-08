const assert = require('assert')

const {
  attemptStorageKey,
  clearAttendancePunchAttempt,
  legacyAcknowledgementKey,
  legacyV1StorageKey,
  readAttendancePunchAttempt,
  reconcileAttendancePunch,
  reconcileAttendancePunchStatus,
  runAttendancePunch,
  storageKey,
  writeVerified
} = require('../src/views/mobile/attendance/mobileAttendancePunchAttempt')

class MemoryStorage {
  constructor() { this.values = new Map(); this.failSet = false; this.afterSet = null; this.beforeRemove = null }
  get length() { return this.values.size }
  key(index) { return Array.from(this.values.keys())[index] || null }
  getItem(key) { return this.values.has(key) ? this.values.get(key) : null }
  setItem(key, value) {
    if (this.failSet) throw new Error('storage unavailable')
    this.values.set(key, String(value))
    if (this.afterSet) this.afterSet(key, String(value))
  }
  removeItem(key) {
    if (this.beforeRemove) this.beforeRemove(key)
    this.values.delete(key)
  }
}

const owner = { userId: 9001, orgId: 1176 }
const base = {
  owner,
  scheduleId: 31,
  punchType: 'IN',
  punchSlotKey: 'MORNING-IN',
  challengeToken: 'challenge-token-1234567890',
  challengeExpiresAt: '2026-08-31T10:03:00'
}
const deterministic = {
  now: 1788141600000,
  random: 0.25,
  withLease: async (leaseOwner, work) => {
    assert.deepStrictEqual(leaseOwner, owner)
    return work()
  }
}

;(async () => {
  {
    const storage = new MemoryStorage()
    let dispatched = 0
    const first = await runAttendancePunch(base, {
      execute: async () => { dispatched += 1; throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })
    assert.strictEqual(first.status, 'outcome-unknown')
    assert.strictEqual(first.dispatched, 1)
    assert.ok(first.attempt.challengeToken)

    const duplicate = await runAttendancePunch(base, {
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, now: deterministic.now + 1, random: 0.5 })
    assert.strictEqual(duplicate.dispatched, 0)
    assert.strictEqual(dispatched, 1)

    const pending = reconcileAttendancePunch({
      schedule: { scheduleId: 31 },
      latestPunch: { clientRequestId: 'other', scheduleId: 31, punchType: 'IN', punchSlotKey: 'MORNING-IN' }
    }, owner, { storage })
    assert.strictEqual(pending.status, 'pending')

    const accepted = reconcileAttendancePunchStatus({
      status: 'ACCEPTED',
      clientRequestId: first.attempt.requestId,
      event: {
        punchEventId: 81,
        clientRequestId: first.attempt.requestId,
        scheduleId: 31,
        punchType: 'IN',
        punchSlotKey: 'MORNING-IN',
        verificationStatus: 'ACCEPTED',
        resolvedAddress: '测试地址'
      },
      evidenceId: 99
    }, owner, { storage })
    assert.strictEqual(accepted.status, 'settled')
    assert.strictEqual(accepted.value.outcome, 'accepted')
    assert.strictEqual(clearAttendancePunchAttempt(owner, {
      storage,
      expectedRequestId: accepted.value.requestId
    }), true)
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'absent')
  }

  {
    const storage = new MemoryStorage()
    const first = await runAttendancePunch(base, {
      execute: async () => { throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })
    const pending = reconcileAttendancePunchStatus({
      status: 'PENDING',
      clientRequestId: first.attempt.requestId
    }, owner, { storage })
    assert.strictEqual(pending.status, 'pending')
    const rejected = reconcileAttendancePunchStatus({
      status: 'NOT_ACCEPTED',
      clientRequestId: first.attempt.requestId
    }, owner, { storage })
    assert.strictEqual(rejected.status, 'settled')
    assert.strictEqual(rejected.value.outcome, 'not-accepted')
  }

  {
    const storage = new MemoryStorage()
    storage.failSet = true
    let dispatched = 0
    const result = await runAttendancePunch(base, {
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(result.status, 'blocked')
    assert.strictEqual(dispatched, 0)
  }

  {
    const storage = new MemoryStorage()
    const accepted = await runAttendancePunch(base, {
      execute: async attempt => ({
        success: true,
        event: {
          punchEventId: 81,
          clientRequestId: attempt.requestId,
          scheduleId: 31,
          punchType: 'IN',
          punchSlotKey: 'MORNING-IN',
          verificationStatus: 'ACCEPTED',
          resolvedAddress: '测试地址'
        },
        evidenceId: 99
      }),
      accepted: response => response.success === true && response.evidenceId === 99
    }, { storage, ...deterministic })
    assert.strictEqual(accepted.status, 'accepted')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).value.outcome, '')
  }

  {
    const storage = new MemoryStorage()
    const first = await runAttendancePunch(base, {
      execute: async () => { throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })
    const mismatched = reconcileAttendancePunchStatus({
      status: 'ACCEPTED',
      clientRequestId: first.attempt.requestId,
      event: {
        punchEventId: 82,
        clientRequestId: first.attempt.requestId,
        scheduleId: 31,
        punchType: 'IN',
        punchSlotKey: 'OTHER-SLOT',
        verificationStatus: 'ACCEPTED',
        resolvedAddress: '测试地址'
      },
      evidenceId: 99
    }, owner, { storage })
    assert.strictEqual(mismatched.status, 'error')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
  }

  {
    const storage = new MemoryStorage()
    const oldAttempt = (await runAttendancePunch(base, {
      execute: async () => { throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })).attempt
    const newAttempt = {
      ...oldAttempt,
      requestId: 'attendance-new-tab-request-0001',
      startedAt: oldAttempt.startedAt + 1
    }
    storage.beforeRemove = removedKey => {
      storage.beforeRemove = null
      assert.strictEqual(removedKey, attemptStorageKey(owner, oldAttempt.requestId))
      writeVerified(newAttempt, { storage })
    }
    assert.strictEqual(clearAttendancePunchAttempt(owner, {
      storage,
      expectedRequestId: oldAttempt.requestId
    }), true)
    assert.strictEqual(
      readAttendancePunchAttempt(owner, { storage }).value.requestId,
      newAttempt.requestId,
      'a new request written during old-request cleanup must live under a different key and survive'
    )
    assert.strictEqual(clearAttendancePunchAttempt(owner, {
      storage,
      expectedRequestId: newAttempt.requestId
    }), true)
  }

  {
    const storage = new MemoryStorage()
    let dispatched = 0
    storage.afterSet = (key, value) => {
      storage.afterSet = null
      const primary = JSON.parse(value)
      const concurrent = {
        ...primary,
        requestId: 'attendance-concurrent-tab-0001',
        startedAt: primary.startedAt + 1
      }
      storage.values.set(
        attemptStorageKey(owner, concurrent.requestId),
        JSON.stringify(concurrent)
      )
    }
    const conflict = await runAttendancePunch(base, {
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(conflict.status, 'outcome-unknown')
    assert.strictEqual(conflict.dispatched, 0)
    assert.strictEqual(dispatched, 0, 'concurrent owner attempts must be detected before transport dispatch')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).attemptCount, 2)
  }

  {
    const storage = new MemoryStorage()
    const legacy = {
      schema: 2,
      status: 'pending',
      outcome: '',
      userId: owner.userId,
      orgId: owner.orgId,
      scheduleId: 31,
      punchType: 'IN',
      punchSlotKey: 'MORNING-IN',
      requestId: 'attendance-legacy-request-0001',
      challengeToken: 'challenge-token-legacy-123456',
      challengeExpiresAt: '2026-08-31T10:03:00',
      startedAt: deterministic.now
    }
    storage.setItem(storageKey(owner), JSON.stringify(legacy))
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).value.requestId, legacy.requestId)
    assert.strictEqual(clearAttendancePunchAttempt(owner, {
      storage,
      expectedRequestId: legacy.requestId
    }), true)
    assert.ok(storage.getItem(storageKey(owner)), 'legacy shared key must never be removed by the new client')
    assert.ok(storage.getItem(legacyAcknowledgementKey(owner, legacy.requestId)))
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'absent')

    const laterLegacy = {
      ...legacy,
      requestId: 'attendance-later-old-tab-0002',
      startedAt: legacy.startedAt + 1
    }
    storage.setItem(storageKey(owner), JSON.stringify(laterLegacy))
    assert.strictEqual(
      readAttendancePunchAttempt(owner, { storage }).value.requestId,
      laterLegacy.requestId,
      'a still-open old tab that writes a new legacy attempt must become visible and frozen'
    )
  }

  {
    const storage = new MemoryStorage()
    for (const outcome of ['accepted', 'rejected']) {
      const requestId = `attendance-real-v1-settled-${outcome}`
      const legacySettled = {
        schema: 1,
        status: 'settled',
        outcome,
        userId: owner.userId,
        orgId: owner.orgId,
        scheduleId: 31,
        punchType: 'IN',
        punchSlotKey: 'MORNING-IN',
        requestId,
        startedAt: deterministic.now
      }
      storage.setItem(legacyV1StorageKey(owner), JSON.stringify(legacySettled))
      assert.strictEqual(
        readAttendancePunchAttempt(owner, { storage }).status,
        'absent',
        `a v1 settled/${outcome} marker must be retired without claiming current success`
      )
      assert.ok(storage.getItem(legacyAcknowledgementKey(owner, requestId)))
    }
  }

  {
    const storage = new MemoryStorage()
    const legacyV1 = {
      schema: 1,
      status: 'pending',
      outcome: '',
      userId: owner.userId,
      orgId: owner.orgId,
      scheduleId: 31,
      punchType: 'IN',
      punchSlotKey: 'MORNING-IN',
      requestId: 'attendance-real-v1-request-0001',
      startedAt: deterministic.now
    }
    storage.setItem(legacyV1StorageKey(owner), JSON.stringify(legacyV1))
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).value.schema, 1)
    let dispatched = 0
    const blocked = await runAttendancePunch(base, {
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(blocked.status, 'outcome-unknown')
    assert.strictEqual(blocked.dispatched, 0)
    assert.strictEqual(dispatched, 0, 'a real v1 pending marker must freeze the upgraded client')

    const accepted = reconcileAttendancePunch({
      schedule: { scheduleId: 31 },
      latestEvidenceId: 501,
      latestPunch: {
        punchEventId: 401,
        clientRequestId: legacyV1.requestId,
        scheduleId: 31,
        punchType: 'IN',
        punchSlotKey: 'MORNING-IN',
        verificationStatus: 'ACCEPTED',
        resolvedAddress: '测试地址'
      }
    }, owner, { storage })
    assert.strictEqual(accepted.status, 'settled')
    assert.strictEqual(clearAttendancePunchAttempt(owner, {
      storage,
      expectedRequestId: legacyV1.requestId
    }), true)
    assert.ok(storage.getItem(legacyV1StorageKey(owner)), 'the upgraded client must acknowledge, not delete, the v1 shared key')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'absent')
  }

  {
    const storage = new MemoryStorage()
    let currentChecks = 0
    let dispatched = 0
    const result = await runAttendancePunch(base, {
      isCurrent: () => {
        currentChecks += 1
        return currentChecks === 1
      },
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(result.status, 'outcome-unknown')
    assert.strictEqual(result.dispatched, 0)
    assert.strictEqual(dispatched, 0, 'context must be rechecked after lease release and immediately before transport')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
  }

  {
    const storage = new MemoryStorage()
    let currentChecks = 0
    let dispatched = 0
    const result = await runAttendancePunch(base, {
      isCurrent: () => {
        currentChecks += 1
        if (currentChecks === 2) {
          storage.setItem(legacyV1StorageKey(owner), JSON.stringify({
            schema: 1,
            status: 'pending',
            outcome: '',
            userId: owner.userId,
            orgId: owner.orgId,
            scheduleId: 31,
            punchType: 'OUT',
            punchSlotKey: 'OTHER-OUT',
            requestId: 'attendance-old-tab-overwrite-0001',
            startedAt: deterministic.now + 1
          }))
        }
        return true
      },
      execute: async () => { dispatched += 1 },
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(result.status, 'outcome-unknown')
    assert.strictEqual(result.dispatched, 0)
    assert.strictEqual(dispatched, 0, 'the v1 interlock must be rechecked immediately before transport')
  }

  {
    const storage = new MemoryStorage()
    const first = await runAttendancePunch(base, {
      execute: async () => { throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })
    const accepted = reconcileAttendancePunch({
      schedule: { scheduleId: 31 },
      latestEvidenceId: 99,
      latestPunch: {
        punchEventId: 83,
        clientRequestId: first.attempt.requestId,
        scheduleId: 31,
        punchType: 'IN',
        punchSlotKey: 'MORNING-IN',
        verificationStatus: 'ACCEPTED',
        resolvedAddress: '测试地址'
      }
    }, owner, { storage })
    assert.strictEqual(accepted.status, 'settled')
    assert.strictEqual(accepted.value.outcome, 'accepted')
  }

  {
    const storage = new MemoryStorage()
    const rejected = await runAttendancePunch(base, {
      execute: async () => {
        const error = new Error('photo rejected')
        error.response = { status: 422 }
        throw error
      },
      accepted: () => false
    }, { storage, ...deterministic })
    assert.strictEqual(rejected.status, 'not-accepted')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
  }

  {
    const storage = new MemoryStorage()
    const currentOwner = { userId: 9002, orgId: 1176 }
    const first = await runAttendancePunch(base, {
      execute: async () => { throw new Error('timeout') },
      accepted: () => false
    }, { storage, ...deterministic })
    assert.strictEqual(first.status, 'outcome-unknown')
    assert.strictEqual(readAttendancePunchAttempt(currentOwner, { storage }).status, 'absent')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
  }

  {
    const storage = new MemoryStorage()
    const incomplete = await runAttendancePunch(base, {
      execute: async attempt => ({
        success: true,
        event: {
          clientRequestId: attempt.requestId,
          scheduleId: 31,
          punchType: 'IN',
          punchSlotKey: 'MORNING-IN',
          resolvedAddress: '测试地址'
        },
        evidenceId: 99
      }),
      accepted: () => true
    }, { storage, ...deterministic })
    assert.strictEqual(incomplete.status, 'outcome-unknown')
    assert.strictEqual(readAttendancePunchAttempt(owner, { storage }).status, 'pending')
  }

  console.log('mobile attendance punch attempt tests passed')
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
