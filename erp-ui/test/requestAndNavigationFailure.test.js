const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const requestSource = fs.readFileSync(path.join(root, "src/utils/request.js"), "utf8")
const routerSource = fs.readFileSync(path.join(root, "src/router/index.js"), "utf8")
const { settleNavigation } = require("../src/router/navigationPromise")

assert.ok(
  /interceptors\.request\.use\([\s\S]*?error\s*=>\s*\{[\s\S]*?return Promise\.reject\(error\)/.test(requestSource),
  "request setup failures must remain rejected"
)
assert.ok(
  routerSource.includes("Router.isNavigationFailure") &&
    routerSource.includes("Router.NavigationFailureType.duplicated") &&
    !routerSource.includes(".catch(err => err)"),
  "the global router wrapper must only settle duplicate navigation"
)
assert.ok(
  requestSource.includes("let sessionExpiryPromise = null") &&
    requestSource.includes("store.dispatch('FedLogOut')") &&
    !/code === 401[\s\S]*?store\.dispatch\('LogOut'\)/.test(requestSource),
  "expired sessions must be cleared once locally without calling the remote logout endpoint"
)
assert.ok(
  /code === 401[\s\S]*?handleExpiredSession\(\)/.test(requestSource) &&
    /Number\(statusCode\) === 401[\s\S]*?handleExpiredSession\(\)/.test(requestSource),
  "both business and HTTP 401 responses must enter the unified expiry flow"
)
assert.ok(
  /handleExpiredSession[\s\S]*?store\.dispatch\('FedLogOut'\)[\s\S]*?MessageBox\.confirm/.test(requestSource),
  "local session state must be cleared before the expiry prompt is shown"
)

async function run() {
  const duplicated = Object.assign(new Error("duplicate"), { type: "duplicated" })
  const duplicateResult = await settleNavigation(
    Promise.reject(duplicated),
    error => error.type === "duplicated"
  )
  assert.strictEqual(duplicateResult, duplicated, "duplicate navigation should remain a no-op")

  const cancelled = Object.assign(new Error("cancelled"), { type: "cancelled" })
  await assert.rejects(
    settleNavigation(Promise.reject(cancelled), error => error.type === "duplicated"),
    error => error === cancelled,
    "cancelled navigation must remain observable by the caller"
  )

  const unexpected = new Error("unexpected")
  await assert.rejects(
    settleNavigation(Promise.reject(unexpected), () => false),
    error => error === unexpected,
    "unexpected navigation errors must remain observable by the caller"
  )

  console.log("request and navigation failure tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
