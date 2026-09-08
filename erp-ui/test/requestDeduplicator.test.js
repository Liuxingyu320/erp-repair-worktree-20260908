const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  assertRequestNotDuplicate,
  getRequestDeduplicatorStateForTest,
  resetRequestDeduplicatorForTest
} = require("../src/utils/requestDeduplicator")

function request(data, overrides = {}) {
  return Object.assign({
    method: "post",
    url: "/inventory/sales",
    headers: { interval: 1000 },
    data
  }, overrides)
}

resetRequestDeduplicatorForTest()
assert.doesNotThrow(() => assertRequestNotDuplicate(request({ orderNo: "SO-1" }), 1000))
assert.throws(
  () => assertRequestNotDuplicate(request({ orderNo: "SO-1" }), 1500),
  /数据正在处理，请勿重复提交/,
  "an identical write inside the interval should be rejected"
)
assert.doesNotThrow(
  () => assertRequestNotDuplicate(request({ orderNo: "SO-2" }), 1500),
  "a changed payload should not be treated as a duplicate"
)
assert.doesNotThrow(
  () => assertRequestNotDuplicate(request({ orderNo: "SO-1" }), 2000),
  "a request at the expiry boundary should be accepted"
)

resetRequestDeduplicatorForTest()
const sensitiveRequests = [
  request({ username: "admin", password: "not-a-real-password" }, { url: "/auth/login" }),
  request({ password: "not-a-real-password" }, { url: "/auth/unlockscreen" }),
  request({ oldPassword: "old", newPassword: "new" }, { url: "/system/user/profile/updatePwd" }),
  request({ password: "new" }, { url: "/system/user/resetPwd" }),
  request("file-content", { headers: { "Content-Type": "multipart/form-data" } })
]

sensitiveRequests.forEach((config, index) => {
  assert.doesNotThrow(() => assertRequestNotDuplicate(config, 3000 + index))
  assert.doesNotThrow(() => assertRequestNotDuplicate(config, 3001 + index))
})
assert.deepStrictEqual(
  getRequestDeduplicatorStateForTest(),
  [],
  "password and multipart requests should never enter generic duplicate tracking"
)

resetRequestDeduplicatorForTest()
assertRequestNotDuplicate(request({ note: "private-business-note" }), 4000)
const state = getRequestDeduplicatorStateForTest()
const serializedState = JSON.stringify(state)

assert.strictEqual(state.length, 1)
assert.ok(state[0].payloadHash, "tracked state should contain only a payload hash")
assert.ok(!serializedState.includes("private-business-note"), "tracked state must not expose request payload values")
assert.ok(!Object.prototype.hasOwnProperty.call(state[0], "data"), "tracked state must not contain a data field")

const requestSource = fs.readFileSync(path.resolve(__dirname, "../src/utils/request.js"), "utf8")
const loginApiSource = fs.readFileSync(path.resolve(__dirname, "../src/api/login.js"), "utf8")

assert.ok(
  requestSource.includes("assertRequestNotDuplicate(config)") &&
    !requestSource.includes("sessionObj") &&
    !requestSource.includes("cache.session") &&
    !requestSource.includes("@/plugins/cache"),
  "Axios request interception should delegate to memory-only duplicate tracking"
)
assert.ok(
  /url:\s*['\"]\/auth\/unlockscreen['\"][\s\S]*?repeatSubmit:\s*false/.test(loginApiSource),
  "unlock requests should explicitly bypass generic duplicate tracking"
)

console.log("requestDeduplicator tests passed")
