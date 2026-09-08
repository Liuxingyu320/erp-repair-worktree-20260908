const assert = require("assert")
const fs = require("fs")
const path = require("path")

const matcherPath = path.resolve(__dirname, "../src/utils/todoMutationMatcher.js")
assert.ok(fs.existsSync(matcherPath), "todo mutation matcher must exist")

const { matchTodoMutation, scheduleTodoMutationRefresh } = require(matcherPath)

const positives = [
  ["POST", "/inventory/purchase/submit"],
  ["post", "/inventory/purchase/receive/12?from=todo"],
  ["POST", "/inventory/purchase/qc/12"],
  ["DELETE", "/inventory/purchase/12"],
  ["DELETE", "/inventory/purchase/delete/12"],
  ["POST", "/inventory/sales/submit"],
  ["DELETE", "/inventory/sales/12"],
  ["POST", "/inventory/deliveryNotice/create/12"],
  ["POST", "/inventory/deliveryNotice/deliver/12"],
  ["DELETE", "/inventory/deliveryNotice/12"],
  ["POST", "/inventory/transfer/submit"],
  ["POST", "/inventory/transfer/approve"],
  ["POST", "/inventory/transfer/deliver/12"],
  ["POST", "/inventory/transfer/receive/12"],
  ["POST", "/inventory/transfer/shipment/receive/12"],
  ["DELETE", "/inventory/transfer/12"],
  ["POST", "/inventory/stockCheck/submit/12"],
  ["POST", "/inventory/stockCheck/restart/12"],
  ["POST", "/inventory/stockCheck/cancel/12"],
  ["POST", "/inventory/stockCheck/approval/12/approve"],
  ["POST", "/inventory/stockCheck/approval/12/reject"],
  ["DELETE", "/inventory/stockCheck/1,2,300"],
  ["POST", "/inventory/purchaseReturn/submit"],
  ["POST", "/inventory/salesReturn/submit"],
  ["POST", "/inventory/purchaseReturn/confirm/12"],
  ["POST", "/inventory/salesReturn/confirm/12"],
  ["DELETE", "/inventory/purchaseReturn/12"],
  ["DELETE", "/inventory/salesReturn/12"],
  ["POST", "/inventory/stock/adjust"],
  ["POST", "/oa/purchase/submit"],
  ["POST", "/oa/purchase/12/close"],
  ["POST", "/oa/purchase/12/withdraw"],
  ["POST", "/approval/tasks/91/approve"],
  ["POST", "/approval/tasks/91/return"],
  ["POST", "/approval/tasks/91/reject"],
  ["POST", "/oa/fixedAsset/repair/submit"],
  ["POST", "/oa/fixedAsset/repair/12/confirm"],
  ["POST", "/oa/laborContract/send"],
  ["POST", "/oa/laborContract/12/void"],
  ["POST", "/oa/laborContract/mobile/12/sign"],
  ["POST", "/oa/signPackage/12/send"],
  ["POST", "/oa/signPackage/12/void"],
  ["POST", "/oa/signPackage/12/finalize"],
  ["POST", "/oa/signPackage/mobile/12/read/34"],
  ["POST", "/oa/signPackage/mobile/12/sign"],
  ["POST", "/oa/signPackage/mobile/12/refuse"],
  ["POST", "/oa/signPackage/mobile/12/final-confirm"],
  ["POST", "/oa/signTask/12/revalidate"],
  ["POST", "/oa/signTask/12/send"],
  ["POST", "/oa/signTask/12/retry"],
  ["POST", "/oa/signTask/12/notification/retry"],
  ["POST", "/oa/signTask/12/cancel"],
  ["POST", "/oa/signTask/12/resolve"],
  ["POST", "/oa/signTask/12/remind"],
  ["POST", "/system/hr/employee"],
  ["PUT", "/system/hr/employee"],
  ["POST", "/system/hr/onboarding/12/confirm"],
  ["POST", "/system/hr/onboarding/12/cancel"],
  ["POST", "/system/hr/import/confirm"],
  ["PUT", "/system/user/changeStatus"]
]

positives.forEach(([method, url]) => {
  assert.strictEqual(matchTodoMutation(method, url), true, `${method} ${url} must refresh todos`)
})

const negatives = [
  ["GET", "/inventory/purchase/12"],
  ["POST", "/inventory/purchase"],
  ["POST", "/inventory/purchase/submit/again"],
  ["POST", "/inventory/purchase/receive/not-a-number"],
  ["DELETE", "/inventory/stockCheck/1,a"],
  ["POST", "/inventory/stockCheck/approval/12/approve/again"],
  ["POST", "/inventory/transfer/approveExtra"],
  ["POST", "/inventory/stock/export"],
  ["POST", "/oa/purchase/list"],
  ["POST", "/oa/fixedAsset/repair/12/detail"],
  ["POST", "/oa/laborContract/12/send"],
  ["POST", "/oa/signPackage/mobile/12/read"],
  ["POST", "/oa/signPackage/12/finalize/again"],
  ["POST", "/oa/signTask/12/notification"],
  ["POST", "/oa/signTask/not-a-number/resolve"],
  ["POST", "/system/login"],
  ["POST", "/system/notice/markReadAll"],
  ["PATCH", "/system/hr/employee"],
  ["PUT", "/system/user/changeStatus/extra"],
  [null, "/inventory/purchase/submit"],
  ["POST", null]
]

negatives.forEach(([method, url]) => {
  assert.strictEqual(matchTodoMutation(method, url), false, `${method} ${url} must not refresh todos`)
})

async function verifyScheduling() {
  const calls = []
  const scheduled = scheduleTodoMutationRefresh("POST", "/inventory/transfer/approve", (type) => {
    calls.push(type)
    return Promise.resolve("done")
  })
  assert.strictEqual(scheduled, true, "a matched mutation should be scheduled")
  assert.deepStrictEqual(calls, [], "refresh dispatch must not delay the business response")
  await new Promise(resolve => setImmediate(resolve))
  assert.deepStrictEqual(calls, ["todo/invalidateAfterMutation"])

  let unmatchedCalls = 0
  assert.strictEqual(scheduleTodoMutationRefresh("GET", "/inventory/transfer/12", () => { unmatchedCalls += 1 }), false)
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(unmatchedCalls, 0)

  assert.doesNotThrow(() => scheduleTodoMutationRefresh("POST", "/oa/purchase/submit", () => {
    throw new Error("sync dispatch failure")
  }))
  scheduleTodoMutationRefresh("POST", "/approval/tasks/91/approve", () => Promise.reject(new Error("async dispatch failure")))
  await new Promise(resolve => setImmediate(resolve))

  const requestSource = fs.readFileSync(path.resolve(__dirname, "../src/utils/request.js"), "utf8")
  assert.ok(requestSource.includes("scheduleTodoMutationRefresh"), "success interceptor must schedule matched todo refreshes")
  assert.ok(
    /scheduleTodoMutationRefresh\s*\(\s*res\.config\s*&&\s*res\.config\.method\s*,\s*res\.config\s*&&\s*res\.config\.url/.test(requestSource),
    "success interceptor must use the actual response config method and URL"
  )
  assert.ok(requestSource.includes("suppressTodoMutationRefresh"),
    "batch approval requests must be able to suppress per-item todo refreshes")
}

verifyScheduling().then(() => {
  console.log("unifiedTodoMutationRefresh tests passed")
}).catch(error => {
  console.error(error)
  process.exit(1)
})
