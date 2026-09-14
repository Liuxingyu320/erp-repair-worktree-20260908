const assert = require('assert')
const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')
const { createTransferCommandRecovery } = require('../src/utils/transferCommandRecovery')
const { createMobileActionRuntime } = require('../src/views/mobile/feature/featureActionRuntime')

async function main() {
  const saved = new Map()
  const calls = []
  const storage = { getItem: key => saved.get(key) || null,
    setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) }
  let sequence = 0
  const recovery = createTransferCommandRecovery({ storage: () => storage,
    context: () => ({ actor: '11', dept: '201' }), newId: () => 'transfer:consumer-' + (++sequence),
    status: async () => ({ status: 'SUCCEEDED' }), transport: async config => {
      calls.push(config); return { code: 200, data: { transferId: 8 } }
    } })
  const source = fs.readFileSync(path.join(__dirname, '../src/api/inventory/transfer.js'), 'utf8')
  const code = babel.transformSync(source, { babelrc: false, configFile: false,
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const mod = { exports: {} }
  new Function('require', 'module', 'exports', code)(name => {
    assert.strictEqual(name, '@/utils/request')
    return { __esModule: true, default: config => recovery.run(config) }
  }, mod, mod.exports)
  const runtime = createMobileActionRuntime(mod.exports)
  const page = fs.readFileSync(path.join(__dirname, '../src/views/mobile/feature/index.vue'), 'utf8')
  const start = '    submitMobileForm(submitAction = "save") {'
  const body = page.slice(page.indexOf(start) + start.length, page.indexOf('\n    },\n    validateMobileForm', page.indexOf(start)))
  const submit = new Function('saveMobileFeatureForm', 'acknowledgeTransferCommand',
    'getFixedAssetPrecheckFromError', 'mobileErrorMessage', `return function(submitAction = 'save') {${body}}`)(
      runtime.saveMobileFeatureForm, result => recovery.acknowledge(result), () => null, error => error.message)
  const target = { featureKey: 'transfer', formSheet: { config: {}, open: true, mode: 'create' },
    snapshotMobileFormData: data => JSON.stringify(data || {}),
    canUseMobileSubmitMode: () => true, getMobileFormValidationError: () => null,
    normalizeMobileFormPayload: () => ({ details: [{ productId: 1, quantity: 2 }] }),
    showActionSuccess() {}, closeMobileForm() { this.formSheet.open = false }, closeItem() {},
    loadData() {}, showToastError(message) { throw Error(message) } }
  submit.call(target)
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(saved.size, 0, 'mobile save acknowledges only after closing the submitted form')
  target.formSheet = { config: {}, open: true, mode: 'create' }
  submit.call(target)
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(calls.length, 2)
  assert.notStrictEqual(calls[0].headers['X-Request-Id'], calls[1].headers['X-Request-Id'])
  const result = await runtime.runMobileFeatureAction('transfer', 'withdrawTransfer', { raw: { transferId: 8 } })
  assert.strictEqual(result.msg, '撤回请求已提交')
  assert.strictEqual(saved.size, 1)
  recovery.acknowledge(result)
  assert.strictEqual(saved.size, 0, 'mobile withdrawal preserves the original response acknowledgment')
  console.log('transfer API, recovery, mobile form and withdrawal consumption passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
