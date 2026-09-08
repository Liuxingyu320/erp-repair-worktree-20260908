const DATABASE_NAME = 'erp-mobile-attendance-v2'
const DATABASE_VERSION = 1
const STORE_NAME = 'punch_leases'
const DEFAULT_LEASE_MILLIS = 15000

function positiveId(value) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function ownerLeaseKey(owner) {
  const userId = positiveId(owner && owner.userId)
  const orgId = positiveId(owner && owner.orgId)
  if (!userId || !orgId) throw new Error('考勤打卡跨标签锁缺少身份或门店上下文')
  return `erp:attendance-v2:punch:${userId}:${orgId}`
}

function leaseToken(options = {}) {
  if (options.token) return String(options.token)
  const cryptoRef = options.crypto || (typeof crypto !== 'undefined' ? crypto : null)
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') return cryptoRef.randomUUID()
  const now = Number(typeof options.now === 'function' ? options.now() : options.now || Date.now())
  const random = Number(typeof options.random === 'function' ? options.random() : options.random || Math.random())
  return `${now.toString(36)}-${Math.floor(random * 0x100000000).toString(16).padStart(8, '0')}`
}

function openLeaseDatabase(indexedDBRef) {
  return new Promise((resolve, reject) => {
    let request
    try {
      request = indexedDBRef.open(DATABASE_NAME, DATABASE_VERSION)
    } catch (error) {
      reject(error)
      return
    }
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(STORE_NAME)) {
        database.createObjectStore(STORE_NAME, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('考勤打卡锁数据库打开失败'))
    request.onblocked = () => reject(new Error('考勤打卡锁数据库升级被其他页面阻塞'))
  })
}

function claimIndexedDbLease(database, record, now) {
  return new Promise((resolve, reject) => {
    let acquired = false
    let transaction
    try {
      transaction = database.transaction(STORE_NAME, 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.get(record.key)
      request.onsuccess = () => {
        const current = request.result
        if (current && Number(current.expiresAt) > now) return
        store.put(record)
        acquired = true
      }
      request.onerror = () => transaction.abort()
    } catch (error) {
      reject(error)
      return
    }
    transaction.oncomplete = () => resolve(acquired)
    transaction.onerror = () => reject(transaction.error || new Error('考勤打卡跨标签锁写入失败'))
    transaction.onabort = () => reject(transaction.error || new Error('考勤打卡跨标签锁事务已中止'))
  })
}

function releaseIndexedDbLease(database, key, token) {
  return new Promise((resolve, reject) => {
    let released = false
    let transaction
    try {
      transaction = database.transaction(STORE_NAME, 'readwrite')
      const store = transaction.objectStore(STORE_NAME)
      const request = store.get(key)
      request.onsuccess = () => {
        const current = request.result
        if (!current || current.token !== token) return
        store.delete(key)
        released = true
      }
      request.onerror = () => transaction.abort()
    } catch (error) {
      reject(error)
      return
    }
    transaction.oncomplete = () => resolve(released)
    transaction.onerror = () => reject(transaction.error || new Error('考勤打卡跨标签锁释放失败'))
    transaction.onabort = () => reject(transaction.error || new Error('考勤打卡跨标签锁释放事务已中止'))
  })
}

async function withIndexedDbLease(indexedDBRef, key, work, options) {
  const token = leaseToken(options)
  const requestedLeaseMillis = Number(options.leaseMillis || DEFAULT_LEASE_MILLIS)
  const leaseMillis = Number.isFinite(requestedLeaseMillis)
    ? Math.max(5000, requestedLeaseMillis)
    : DEFAULT_LEASE_MILLIS
  const database = await openLeaseDatabase(indexedDBRef)
  const now = Number(typeof options.now === 'function' ? options.now() : options.now || Date.now())
  if (!Number.isFinite(now) || now <= 0) {
    if (database && typeof database.close === 'function') database.close()
    throw new Error('考勤打卡跨标签锁无法取得可靠时间')
  }
  let acquired = false
  try {
    acquired = await claimIndexedDbLease(database, {
      key,
      token,
      expiresAt: now + leaseMillis
    }, now)
    if (!acquired) throw new Error('另一个标签页正在准备打卡，请稍后重试')
    return await work()
  } finally {
    try {
      if (acquired) {
        const released = await releaseIndexedDbLease(database, key, token)
        if (!released) throw new Error('考勤打卡跨标签锁未能安全释放，本次未提交')
      }
    } finally {
      if (database && typeof database.close === 'function') database.close()
    }
  }
}

async function withAttendancePunchLease(owner, work, options = {}) {
  if (typeof work !== 'function') throw new Error('考勤打卡跨标签锁缺少受保护操作')
  const key = ownerLeaseKey(owner)
  const locks = Object.prototype.hasOwnProperty.call(options, 'locks')
    ? options.locks
    : (typeof navigator !== 'undefined' ? navigator.locks : null)
  if (locks && typeof locks.request === 'function') {
    let unavailable = false
    const result = await locks.request(key, { mode: 'exclusive', ifAvailable: true }, lock => {
      if (!lock) {
        unavailable = true
        return undefined
      }
      return work()
    })
    if (unavailable) throw new Error('另一个标签页正在准备打卡，请稍后重试')
    return result
  }
  const indexedDBRef = Object.prototype.hasOwnProperty.call(options, 'indexedDB')
    ? options.indexedDB
    : (typeof indexedDB !== 'undefined' ? indexedDB : null)
  if (!indexedDBRef || typeof indexedDBRef.open !== 'function') {
    throw new Error('当前浏览器无法建立安全的跨标签打卡锁，本次未提交')
  }
  return withIndexedDbLease(indexedDBRef, key, work, options)
}

module.exports = {
  DATABASE_NAME,
  STORE_NAME,
  claimIndexedDbLease,
  ownerLeaseKey,
  releaseIndexedDbLease,
  withIndexedDbLease,
  withAttendancePunchLease
}
