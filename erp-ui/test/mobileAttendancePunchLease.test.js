const assert = require('assert')

const {
  claimIndexedDbLease,
  ownerLeaseKey,
  releaseIndexedDbLease,
  withIndexedDbLease,
  withAttendancePunchLease
} = require('../src/views/mobile/attendance/mobileAttendancePunchLease')

class FakeIndexedDbDatabase {
  constructor() {
    this.records = new Map()
    this.closed = false
    this.objectStoreNames = { contains: () => true }
  }

  transaction(name, mode) {
    assert.strictEqual(name, 'punch_leases')
    assert.strictEqual(mode, 'readwrite')
    const database = this
    const transaction = {
      aborted: false,
      error: null,
      abort() {
        this.aborted = true
        queueMicrotask(() => { if (this.onabort) this.onabort() })
      },
      objectStore() {
        return {
          get(key) {
            const request = { result: undefined, error: null }
            queueMicrotask(() => {
              if (transaction.aborted) return
              request.result = database.records.get(key)
              if (request.onsuccess) request.onsuccess()
              queueMicrotask(() => {
                if (!transaction.aborted && transaction.oncomplete) transaction.oncomplete()
              })
            })
            return request
          },
          put(record) {
            database.records.set(record.key, { ...record })
          },
          delete(key) {
            database.records.delete(key)
          }
        }
      }
    }
    return transaction
  }

  close() { this.closed = true }
}

function fakeIndexedDb(database, onOpened) {
  return {
    open() {
      const request = { result: null, error: null }
      queueMicrotask(() => {
        request.result = database
        if (onOpened) onOpened()
        if (request.onsuccess) request.onsuccess()
      })
      return request
    }
  }
}

const owner = { userId: 9001, orgId: 1176 }
assert.strictEqual(ownerLeaseKey(owner), 'erp:attendance-v2:punch:9001:1176')
assert.throws(() => ownerLeaseKey({ userId: 9001 }), /缺少身份或门店/)

;(async () => {
  let held = false
  let releaseFirst
  let firstEntered
  const entered = new Promise(resolve => { firstEntered = resolve })
  const locks = {
    request(name, options, callback) {
      assert.strictEqual(name, ownerLeaseKey(owner))
      assert.deepStrictEqual(options, { mode: 'exclusive', ifAvailable: true })
      if (held) return Promise.resolve(callback(null))
      held = true
      return Promise.resolve(callback({ name })).finally(() => { held = false })
    }
  }

  const first = withAttendancePunchLease(owner, () => new Promise(resolve => {
    releaseFirst = resolve
    firstEntered()
  }), { locks })
  await entered
  await assert.rejects(
    withAttendancePunchLease(owner, () => 'must-not-run', { locks }),
    /另一个标签页正在准备打卡/
  )
  releaseFirst('first-complete')
  assert.strictEqual(await first, 'first-complete')
  assert.strictEqual(await withAttendancePunchLease(owner, () => 'second-complete', { locks }), 'second-complete')

  await assert.rejects(
    withAttendancePunchLease(owner, () => 'must-not-run', { locks: null, indexedDB: null }),
    /无法建立安全的跨标签打卡锁/
  )

  const database = new FakeIndexedDbDatabase()
  const key = ownerLeaseKey(owner)
  assert.strictEqual(await claimIndexedDbLease(database, {
    key, token: 'first', expiresAt: 200
  }, 100), true)
  assert.strictEqual(await claimIndexedDbLease(database, {
    key, token: 'second', expiresAt: 250
  }, 150), false, 'an unexpired IDB lease must exclude another tab')
  assert.strictEqual(await claimIndexedDbLease(database, {
    key, token: 'second', expiresAt: 400
  }, 201), true, 'an expired IDB lease may be replaced atomically')
  assert.strictEqual(await releaseIndexedDbLease(database, key, 'wrong-token'), false)
  assert.strictEqual(database.records.get(key).token, 'second')
  assert.strictEqual(await releaseIndexedDbLease(database, key, 'second'), true)
  assert.strictEqual(database.records.has(key), false)

  let opened = false
  const lifecycleDatabase = new FakeIndexedDbDatabase()
  const lifecycleResult = await withIndexedDbLease(
    fakeIndexedDb(lifecycleDatabase, () => { opened = true }),
    key,
    () => 'idb-complete',
    {
      token: 'lifecycle-token',
      now: () => {
        assert.strictEqual(opened, true, 'lease expiry time must be captured after the database opens')
        return 1000
      },
      leaseMillis: 5000
    }
  )
  assert.strictEqual(lifecycleResult, 'idb-complete')
  assert.strictEqual(lifecycleDatabase.records.has(key), false)
  assert.strictEqual(lifecycleDatabase.closed, true)

  const failedReleaseDatabase = new FakeIndexedDbDatabase()
  await assert.rejects(
    withIndexedDbLease(
      fakeIndexedDb(failedReleaseDatabase),
      key,
      () => {
        failedReleaseDatabase.records.set(key, {
          key,
          token: 'replacement-token',
          expiresAt: 9000
        })
        return 'must-not-be-returned'
      },
      { token: 'original-token', now: 1000, leaseMillis: 5000 }
    ),
    /未能安全释放/
  )
  assert.strictEqual(failedReleaseDatabase.closed, true)

  console.log('mobile attendance punch lease tests passed')
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
