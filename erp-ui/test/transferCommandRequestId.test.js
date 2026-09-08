const assert = require("assert")
const fs = require("fs")
const path = require("path")
const babel = require("../node_modules/@babel/core")
const {
  applyPersistentCommandRequestId,
  isPersistentTransferCommand
} = require("../src/utils/persistentCommand")

const apiPath = path.resolve(__dirname, "../src/api/inventory/transfer.js")
const requestPath = path.resolve(__dirname, "../src/utils/request.js")

const requestSource = fs.readFileSync(requestPath, "utf8")
assert.match(requestSource,
  /isPersistentTransferCommand\(config\)[\s\S]*applyPersistentCommandRequestId\(config\)/,
  "the shared request interceptor must materialize command request ids")

function loadApi(request) {
  const source = fs.readFileSync(apiPath, "utf8")
  const transformed = babel.transformSync(source, {
    filename: apiPath,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  new Function("require", "module", "exports", transformed)(specifier => {
    if (specifier === "@/utils/request") {
      return { __esModule: true, default: request }
    }
    throw new Error(`unexpected transfer API dependency: ${specifier}`)
  }, module, module.exports)
  return module.exports
}

const calls = []
const api = loadApi(config => {
  calls.push(config)
  return config
})

const commandCalls = [
  () => api.saveTransfer({ transferId: 1 }),
  () => api.submitTransfer({ transferId: 1 }),
  () => api.approveTransfer({ transferId: 1, action: "approve" }),
  () => api.deliverTransfer(1, { items: [] }),
  () => api.receiveTransfer(1, { items: [] }),
  () => api.receiveShipment(2, { items: [] }),
  () => api.cancelTransfer(1),
  () => api.withdrawTransfer(1),
  () => api.deleteTransferDraft(1),
  () => api.resolveTransferDiscrepancy(3, { items: [] })
]

const commandConfigs = commandCalls.map(invoke => invoke())
commandConfigs.forEach(config => {
  assert.strictEqual(isPersistentTransferCommand(config), true,
    "persistent transfer APIs must match the request interceptor contract")
  applyPersistentCommandRequestId(config)
})
const generatedIds = commandConfigs.map(config => config.headers["X-Request-Id"])
generatedIds.forEach(requestId => {
  assert.match(requestId, /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/,
    "every persistent transfer command must carry a backend-valid X-Request-Id")
})
assert.strictEqual(new Set(generatedIds).size, generatedIds.length,
  "separate transfer commands must not reuse generated request ids")

const retryConfig = commandConfigs[0]
const firstRequestId = retryConfig.headers["X-Request-Id"]
applyPersistentCommandRequestId(retryConfig)
assert.strictEqual(retryConfig.headers["X-Request-Id"], firstRequestId,
  "a retried command must reuse its original request id")

const explicit = api.approveTransfer({ transferId: 7, action: "approve" }, {
  inventoryDeptId: "88",
  headers: {
    "x-request-id": "TODOQ:legacy-701",
    "X-Trace-Id": "trace-1"
  }
})
applyPersistentCommandRequestId(explicit)
assert.strictEqual(explicit.inventoryDeptId, "88")
assert.strictEqual(explicit.headers["X-Request-Id"], "TODOQ:legacy-701",
  "caller-owned idempotency ids must survive API adaptation")
assert.strictEqual(explicit.headers["X-Trace-Id"], "trace-1")
assert.strictEqual(explicit.headers["x-request-id"], undefined,
  "request-id header casing should be normalized before Axios sees it")

const outConfig = api.outTransfer(9, { items: [] })
applyPersistentCommandRequestId(outConfig)
assert.match(outConfig.headers["X-Request-Id"], /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/,
  "legacy outTransfer must use the delivery command contract")

const inConfig = api.inTransfer(9, { items: [] })
applyPersistentCommandRequestId(inConfig)
assert.match(inConfig.headers["X-Request-Id"], /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/,
  "legacy inTransfer must use the receipt command contract")

const read = api.getTransferDetail(1)
assert.strictEqual(read.headers, undefined, "read requests do not need command ids")
const sourceConfirmation = api.confirmTransferSource(1, { items: [] })
assert.strictEqual(sourceConfirmation.headers, undefined,
  "the separately protected source-confirm endpoint should retain its existing contract")
assert.strictEqual(isPersistentTransferCommand(sourceConfirmation), false)
assert.strictEqual(isPersistentTransferCommand(read), false)

assert.strictEqual(isPersistentTransferCommand({
  url: "/inventory/transfer/11/shipments", method: "post"
}), true, "V2 shipment commands must carry request ids")
assert.strictEqual(isPersistentTransferCommand({
  url: "/inventory/transfer/shipments/12/receipts", method: "post"
}), true, "V2 receipt commands must carry request ids")
assert.strictEqual(isPersistentTransferCommand({
  url: "/inventory/transfer/discrepancies/13/adjudications", method: "post"
}), true, "V2 discrepancy adjudications must carry request ids")

assert.ok(calls.length >= commandCalls.length + 5)
console.log("transfer command request id tests passed")
