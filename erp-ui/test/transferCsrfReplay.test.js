const assert = require("assert")
const fs = require("fs")
const path = require("path")
const babel = require("../node_modules/@babel/core")
const axios = require("../node_modules/axios/dist/node/axios.cjs")
const root = path.resolve(__dirname, "..")

function runtime(options = {}) {
  const state = { actor: "101", dept: "10", revision: 0 }
  const calls = []
  let release, started, csrfCalls = 0, firstConfig
  const gate = new Promise(resolve => { release = resolve })
  const csrfStarted = new Promise(resolve => { started = resolve })
  const storage = new Map()
  const adapter = async config => {
    if (config.url === "/auth/csrf") {
      csrfCalls++
      started()
      await gate
      return { status: 200, data: { code: 200 }, headers: {}, config }
    }
    if (config.url.includes("/commands/"))
      return { status: 200, data: { code: 200, data: { status: "SUCCEEDED" } }, headers: {}, config }
    if (!firstConfig) firstConfig = config
    calls.push({ dept: config.headers.get("Dept-NumId"),
      requestId: config.headers.get("X-Request-Id"), data: config.data, url: config.url })
    if (calls.length === 1 || options.rejectReplay) {
      const error = new Error("synthetic forbidden")
      error.config = config
      error.response = { status: 403, data: { code: 403 },
        headers: options.noCsrfMarker ? {} : { "x-erp-csrf-required": "true" }, config }
      throw error
    }
    return { status: 200, data: { code: 200, data: { transferId: 701 } }, headers: {}, config }
  }
  const dependencies = {
    axios: { defaults: { headers: {} }, getAdapter: axios.getAdapter,
      create: config => axios.create(Object.assign({}, config, { adapter })) },
    "@/plugins/element-services": { Notification: { error() {} }, Message() {}, Loading: {},
      MessageBox: { confirm: async () => true, alert: async () => {} } },
    "@/store": { getters: { get id() { return state.actor } },
      state: { user: { get sessionRevision() { return state.revision } } }, dispatch: async () => {} },
    "@/utils/auth": { getToken: () => null },
    "@/utils/shopContext": { getSelectedDeptType: () => "STORE", getSelectedInventoryDeptId: () => state.dept },
    "@/utils/signScopeContext": { getSelectedSignScopeDeptId: () => null, SIGN_SCOPE_HEADER: "Sign-Scope" },
    "@/utils/errorCode": {},
    "@/utils/common": { blobValidate: () => false, tansParams: () => "" },
    "@/utils/todoMutationMatcher": { scheduleTodoMutationRefresh() {} },
    "@capacitor/core": { Capacitor: { isNativePlatform: () => false } },
    "@/utils/sessionMode": {
      applySessionAuthHeaders: headers => {
        if (typeof headers.set === "function") headers.set("X-XSRF-TOKEN", "synthetic-csrf")
        else headers["X-XSRF-TOKEN"] = "synthetic-csrf"
        return headers
      },
      hasSessionCandidate: () => true, isCookiePreferredSession: () => true,
      shouldUseSessionCredentials: () => true
    },
    "./apiBaseUrl": { resolveApiBaseUrl: () => "https://erp.invalid" },
    "./requestSecurity": { assertTrustedRequestBaseUrl() {}, assertTrustedRequestUrl() {} }
  }
  const source = fs.readFileSync(path.join(root, "src/utils/request.js"), "utf8")
  const transformed = babel.transformSync(source, {
    babelrc: false, configFile: false,
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  new Function("require", "module", "exports", "sessionStorage", transformed)(
    name => Object.prototype.hasOwnProperty.call(dependencies, name)
      ? dependencies[name] : require(path.join(root, "src/utils", name)),
    module, module.exports, {
      getItem: key => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    })
  return { state, calls, release, csrfStarted, request: module.exports.default,
    acknowledge: module.exports.acknowledgeTransferCommand,
    get firstConfig() { return firstConfig }, get csrfCalls() { return csrfCalls } }
}

function approval(explicit = true) {
  return { url: "/inventory/transfer/approve", method: "post", inventoryDeptId: "88",
    data: { transferId: 701, action: "approve", comment: "synthetic approval" },
    headers: explicit ? { "X-Request-Id": "TODOQ:replay-701" } : {} }
}

async function run() {
  for (const explicit of [true, false]) {
    const test = runtime()
    const config = approval(explicit)
    const pending = test.request(config)
    await test.csrfStarted
    config.data.comment = "edited form after original dispatch"
    test.release()
    test.acknowledge(await pending)
    assert.strictEqual(test.csrfCalls, 1)
    assert.strictEqual(test.calls.length, 2)
    assert.deepStrictEqual(test.calls.map(call => call.dept), ["88", "88"])
    assert.strictEqual(test.calls[0].requestId, test.calls[1].requestId)
    assert.strictEqual(test.calls[0].data, test.calls[1].data)
    assert.strictEqual(JSON.parse(test.calls[1].data).comment, "synthetic approval")
  }

  for (const change of [
    test => { test.state.actor = "202" },
    test => { test.state.dept = "20" },
    test => { test.state.revision += 2 },
    test => { test.firstConfig.data = JSON.stringify({ transferId: 702 }) },
    test => { test.firstConfig.headers.set("X-Request-Id", "TODOQ:changed-701") },
    test => { test.firstConfig.url = "/inventory/transfer/save" },
    test => { delete test.firstConfig.__transferReplayPermit }
  ]) {
    const test = runtime()
    const pending = test.request(approval())
    const rejected = assert.rejects(pending, error => error.code === "TRANSFER_REPLAY_CHANGED")
    await test.csrfStarted
    change(test)
    test.release()
    await rejected
    assert.strictEqual(test.calls.length, 1, "changed request must never reach the network adapter again")
  }

  const dispatchRace = runtime()
  const config = approval()
  config.transformRequest = [data => {
    dispatchRace.state.actor = "202"
    return JSON.stringify(data)
  }]
  await assert.rejects(dispatchRace.request(config), error => error.code === "TRANSFER_REPLAY_CHANGED")
  assert.strictEqual(dispatchRace.calls.length, 0, "recheck after Axios transforms and before network dispatch")

  const repeat = runtime({ rejectReplay: true })
  const repeatFailure = assert.rejects(repeat.request(approval()))
  await repeat.csrfStarted
  repeat.release()
  await repeatFailure
  assert.strictEqual(repeat.calls.length, 2)
  assert.strictEqual(repeat.csrfCalls, 1, "CSRF recovery must not loop")

  const ordinary403 = runtime({ noCsrfMarker: true })
  await assert.rejects(ordinary403.request(approval()))
  assert.strictEqual(ordinary403.calls.length, 1)
  assert.strictEqual(ordinary403.csrfCalls, 0, "permission denials must not be replayed")
  console.log("transfer CSRF replay: 12 real Axios scenario groups passed")
}

run().catch(error => { console.error(error); process.exitCode = 1 })
