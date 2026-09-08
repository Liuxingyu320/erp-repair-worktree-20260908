const assert = require("assert")
const fs = require("fs")
const path = require("path")

const state = require("../src/utils/mobileHrQueueState")

function owner(userId, deptId, permissions) {
  return state.createMobileHrQueueOwnerFingerprint({ userId, deptId, permissions })
}

state.resetMobileHrQueueStateCacheForTests()
state.setMobileHrQueueStateNowForTests(1000)

const ownerA = owner(7, 12, ["hr:onboarding:list", "hr:onboarding:query"])
const ownerB = owner(8, 12, ["hr:onboarding:list", "hr:onboarding:query"])
const scopeChanged = owner(7, 99, ["hr:onboarding:list", "hr:onboarding:query"])
const permissionsChanged = owner(7, 12, ["hr:onboarding:list"])
assert.ok(ownerA && !ownerA.includes("hr:onboarding") && !ownerA.includes("7:12"), "fingerprint should be opaque")

const keyA = state.writeMobileHrQueueState(ownerA, "", {
  status: "READY", filters: { keyword: "张三13800000000" },
  rows: [{ onboardingId: 1, employeeName: "张*", phoneNumberMasked: "138****0000" }],
  total: 1, pageNum: 1, scrollTop: 20
})
assert.strictEqual(state.readMobileHrQueueState(ownerB, keyA), null, "another account must not read cached rows")
assert.strictEqual(state.readMobileHrQueueState(ownerA, keyA), null, "owner mismatch should hard-delete the entry")

const changedScopeKey = state.writeMobileHrQueueState(ownerA, "", { rows: [{ onboardingId: 2 }], total: 1, pageNum: 1 })
assert.strictEqual(state.readMobileHrQueueState(scopeChanged, changedScopeKey), null, "changed department scope must miss")
const changedPermissionsKey = state.writeMobileHrQueueState(ownerA, "", { rows: [{ onboardingId: 3 }], total: 1, pageNum: 1 })
assert.strictEqual(state.readMobileHrQueueState(permissionsChanged, changedPermissionsKey), null, "changed permissions must miss")

const exactRows = Array.from({ length: 5000 }, (_, index) => ({ onboardingId: index + 1 }))
const exactKey = state.writeMobileHrQueueState(ownerA, "", { status: "CONFIRMED", filters: {}, rows: exactRows, total: 6000, pageNum: 250, scrollTop: 700 })
const exact = state.readMobileHrQueueState(ownerA, exactKey)
assert.strictEqual(exact.overflow, false)
assert.strictEqual(exact.rows.length, 5000)
assert.strictEqual(exact.pageNum, 250)
assert.strictEqual(exact.total, 6000)
assert.strictEqual(exact.scrollTop, 700)

const overflowRows = Array.from({ length: 6000 }, (_, index) => ({ onboardingId: index + 1 }))
const overflowKey = state.writeMobileHrQueueState(ownerA, "", { status: "CONFIRMED", filters: { keyword: "secret" }, rows: overflowRows, total: 7000, pageNum: 300, scrollTop: 900 })
const overflow = state.readMobileHrQueueState(ownerA, overflowKey)
assert.strictEqual(overflow.overflow, true)
assert.deepStrictEqual(overflow.rows, [])
assert.strictEqual(overflow.pageNum, 1, "overflow must not retain an incoherent deep page")
assert.strictEqual(overflow.total, 0)
assert.strictEqual(overflow.scrollTop, 0)

const snapshot = state.getMobileHrQueueStateCacheSnapshotForTests()
assert.ok(snapshot.every(entry => !Object.prototype.hasOwnProperty.call(entry, "rows") && !Object.prototype.hasOwnProperty.call(entry, "filters")))
assert.ok(!JSON.stringify(snapshot).includes("secret") && !JSON.stringify(snapshot).includes("张三"))

state.clearMobileHrQueueStateCache()
assert.deepStrictEqual(state.getMobileHrQueueStateCacheSnapshotForTests(), [])

const userStore = fs.readFileSync(path.resolve(__dirname, "../src/store/modules/user.js"), "utf8")
const lockStore = fs.readFileSync(path.resolve(__dirname, "../src/store/modules/lock.js"), "utf8")
assert.ok(userStore.includes("clearMobileHrQueueStateCache"), "auth actions should clear HR queue state")
;["Login", "LogOut", "FedLogOut"].forEach(action => {
  assert.ok(new RegExp(`${action}\\([\\s\\S]*?clearMobileHrQueueStateCache`).test(userStore), `${action} should clear HR queue state`)
})
assert.ok(/lockScreen\([\s\S]*?clearMobileHrQueueStateCache/.test(lockStore), "screen lock should clear HR queue state")

console.log("mobileHrQueueState tests passed")
