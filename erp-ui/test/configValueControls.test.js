const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const Vue = require('vue')
const compiler = require('vue-template-compiler')
const file = path.resolve(__dirname, '../src/views/system/config/index.vue')
const source = fs.readFileSync(file, 'utf8'), sfc = compiler.parseComponent(source)
const compiled = compiler.compile(sfc.template.content)
assert.deepStrictEqual(compiled.errors, [])
const code = babel.transformSync(sfc.script.content, { filename: file, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
const tick = async () => { await new Promise(resolve => setImmediate(resolve)); await Vue.nextTick() }
function setup(form = {}, apiOverrides = {}) {
  const writes = [], errors = [], module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Promise, Set, Map, Object, Array, String, Number, JSON, window: { addEventListener() {}, removeEventListener() {} },
    require(id) {
      if (id === '@/utils/uiOperationScope') return require('../src/utils/uiOperationScope')
      if (id === '@/utils/shopContext') return { getSelectedDeptId: () => '1' }
      if (id === '@/api/system/config') return {
        listConfig: () => Promise.resolve({ rows: [], total: 0 }),
        listConfigDescriptors: () => Promise.resolve({ data: [] }),
        addConfig: payload => { writes.push(payload); return Promise.resolve() },
        updateConfig: payload => { writes.push(payload); return Promise.resolve() },
        ...apiOverrides
      }
      return {}
    }
  }, { filename: file })
  const page = new Vue({ ...module.exports.default, beforeCreate() { this.$store = { getters: { id: '1' }, state: { user: { sessionRevision: 1 } } }; this.$route = { path: '/system/config' }; this.addDateRange = q => q; this.resetForm = () => {}; this.$modal = { msgSuccess() {}, msgError(message) { errors.push(message) } } } })
  page.form = { configId: 5, configKey: 'test.key', configType: 'N', sensitive: false, ...form }
  page.$refs.form = { validate(complete) {
    page.rules.configValue[0].validator({}, page.form.configValue, error => { if (error) errors.push(error.message); complete(!error) })
  } }
  return { page, writes, errors, async submit() { const submittedForm = { ...page.form }; page.open = true; page.submitForm(); await tick(); if (!page.open) page.form = submittedForm }, dispose() { page.$destroy() } }
}
const tests = []
function test(name, run) { tests.push({ name, run }) }
test('booleans show existing case-insensitive value without rewriting it and toggle emits string', async () => {
  const h = setup({ valueType: 'boolean', configValue: ' TRUE ' })
  assert.equal(h.page.hasBooleanValue, true); assert.equal(h.page.booleanConfigValue, 'true')
  assert.equal(h.page.form.configValue, ' TRUE '); assert.equal(h.writes.length, 0)
  h.page.booleanConfigValue = 'false'; await h.submit()
  assert.equal(h.writes[0].configValue, 'false'); h.dispose()
})
test('unselected boolean requires a deliberate choice', async () => {
  const h = setup({ valueType: 'boolean', configValue: '' })
  assert.equal(h.page.hasBooleanValue, false); await h.submit(); assert.equal(h.writes.length, 0)
  h.page.form.configValue = 'true'; await h.submit(); assert.equal(h.writes[0].configValue, 'true'); h.dispose()
})
test('enumeration options preserve zero and reject unknown choices', async () => {
  const h = setup({ valueType: 'enum', validationRule: ' minLength=1; enum=0,1,2 ;maxLength=3', configValue: '9' })
  assert.deepStrictEqual(Array.from(h.page.enumOptions), ['0', '1', '2'])
  await h.submit(); assert.equal(h.writes.length, 0)
  h.page.form.configValue = '0'; await h.submit(); assert.equal(h.writes[0].configValue, '0'); h.dispose()
})
test('integer controls show descriptor range and unit and retain exact string payload', async () => {
  const h = setup({ valueType: 'integer', validationRule: 'min=5;max=10080', descriptorDescription: '有效期，单位为分钟', configValue: '4' })
  assert.equal(h.page.numericRangeHint, '范围：5 至 10080'); assert.equal(h.page.valueUnit, '分钟')
  await h.submit(); assert.equal(h.writes.length, 0); assert(h.errors[0].includes('5'))
  h.page.form.configValue = '10.5'; await h.submit(); assert.equal(h.writes.length, 0)
  h.page.form.configValue = '5'; await h.submit(); assert.equal(h.writes[0].configValue, '5'); h.dispose()
})
test('large integer remains a string without automatic Number rounding', async () => {
  const h = setup({ valueType: 'integer', configValue: '9223372036854775001' })
  await h.submit(); assert.equal(h.writes[0].configValue, '9223372036854775001'); h.dispose()
})
test('decimal preserves precision and supports server-valid exponent syntax', async () => {
  const h = setup({ valueType: 'decimal', validationRule: 'min=-2.5;max=3000', configValue: '-2.6' })
  await h.submit(); assert.equal(h.writes.length, 0)
  h.page.form.configValue = '1.2000'; await h.submit(); assert.equal(h.writes[0].configValue, '1.2000')
  h.page.form.configValue = '2e3'; await h.submit(); assert.equal(h.writes[1].configValue, '2e3')
  h.page.form.configValue = '3e4'; await h.submit(); assert.equal(h.writes.length, 2); h.dispose()
})
test('sensitive fields retain explicit replacement and never pass through numeric/enum controls', async () => {
  const h = setup({ sensitive: true, valueType: 'integer', updateSensitiveValue: false, configValue: '' })
  await h.submit(); assert.equal(h.writes[0].configValue, ''); assert.equal(h.writes[0].updateSensitiveValue, false)
  h.page.form.updateSensitiveValue = true; await h.submit(); assert.equal(h.writes.length, 1)
  h.page.form.configValue = 'local-synthetic-secret'; await h.submit()
  assert.equal(h.writes[1].configValue, 'local-synthetic-secret'); assert.equal(h.writes[1].updateSensitiveValue, true); h.dispose()
})
test('known sensitive descriptor applies the actual sensitive flag and clears old display value', async () => {
  const h = setup({ configKey: 'sensitive.test', configValue: 'previous value' })
  await tick()
  h.page.configDescriptors = [{ configKey: 'sensitive.test', sensitive: true, valueType: 'password', description: '私密值', validationRule: 'minLength=8', displayOrder: 1 }]
  h.page.applyKnownDescriptor()
  assert.equal(h.page.form.sensitive, true); assert.equal(h.page.form.updateSensitiveValue, false); assert.equal(h.page.form.configValue, '')
  assert.equal(h.writes.length, 0); h.dispose()
})
test('JSON and text retain original representation; invalid JSON stays in the editor', async () => {
  const h = setup({ valueType: 'json', configValue: '{bad}' })
  await h.submit(); assert.equal(h.writes.length, 0); assert.equal(h.page.form.configValue, '{bad}')
  h.page.form.configValue = '{"count": 2}'; await h.submit(); assert.equal(h.writes[0].configValue, '{"count": 2}')
  h.page.form.valueType = 'string'; h.page.form.configValue = '原有文本'; await h.submit(); assert.equal(h.writes[1].configValue, '原有文本'); h.dispose()
})
test('edit writes carry optimistic version and metadata but omit server timestamps and display fields', async () => {
  const h = setup({ configName: '测试', configValue: 'text', version: 7, groupCode: 'custom',
    valueType: 'string', displayOrder: 3, createTime: '2026-09-14T06:34:06.000Z',
    updateTime: '2026-09-14T06:35:06.000Z', createBy: 'server', descriptorDescription: '只读说明', valueConfigured: true })
  await h.submit()
  assert.equal(h.writes[0].version, 7)
  assert.equal(h.writes[0].groupCode, 'custom')
  assert.equal(h.writes[0].displayOrder, 3)
  for (const key of ['createTime', 'updateTime', 'createBy', 'descriptorDescription', 'valueConfigured', 'sensitive']) {
    assert.equal(Object.hasOwn(h.writes[0], key), false, key)
  }
  h.dispose()
})
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
test('save failures stay visible and keep the entered value', async () => {
  const h = setup({ configValue: 'my-edit', version: 2 }, { updateConfig: () => Promise.reject(new Error('参数已被其他管理员修改，请刷新后重试')) })
  await h.submit()
  assert(h.page.submitError.includes('其他管理员'))
  assert.equal(h.page.form.configValue, 'my-edit'); assert.equal(h.page.open, true); assert.equal(h.page.submitting, false)
  h.dispose()
})
test('late detail responses cannot replace the newer editor or reopen a cancelled editor', async () => {
  const first = deferred(), second = deferred()
  const h = setup({}, { getConfig: id => id === 1 ? first.promise : second.promise })
  h.page.handleUpdate({ configId: 1 }); h.page.handleUpdate({ configId: 2 })
  second.resolve({ data: { configId: 2, version: 4, configValue: 'newer' } }); await tick()
  first.resolve({ data: { configId: 1, version: 1, configValue: 'older' } }); await tick()
  assert.equal(h.page.form.configId, 2)
  const cancelled = deferred(); const c = setup({}, { getConfig: () => cancelled.promise })
  c.page.handleUpdate({ configId: 3 }); c.page.cancel()
  cancelled.resolve({ data: { configId: 3, version: 1 } }); await tick()
  assert.equal(c.page.open, false); h.dispose(); c.dispose()
})
test('late save failure and validation cannot affect a later opened form', async () => {
  const pending = deferred()
  const h = setup({ configValue: 'first', version: 2 }, { updateConfig: (payload, options) => { assert.equal(options.silentError, true); return pending.promise } })
  h.page.submitForm(); h.page.cancel(); h.page.handleAdd(); h.page.form.configValue = 'new form'
  pending.reject(new Error('old failure')); await tick()
  assert.equal(h.page.submitError, ''); assert.equal(h.page.form.configValue, 'new form'); assert.equal(h.page.open, true)
  const c = setup({ configValue: 'pending validation', version: 1 }); let complete
  c.page.$refs.form.validate = fn => { complete = fn }
  c.page.submitForm(); c.page.cancel(); c.page.handleAdd(); complete(true); await tick()
  assert.equal(c.writes.length, 0); h.dispose(); c.dispose()
})
test('stale detail errors stay silent while current errors remain visible', async () => {
  const pending = deferred()
  const h = setup({}, { getConfig: (id, options) => { assert.equal(options.silentError, true); return pending.promise } })
  h.page.handleUpdate({ configId: 1 }); h.page.cancel(); h.page.handleAdd()
  pending.reject(new Error('old detail failure')); await tick()
  assert.deepStrictEqual(h.errors, []); assert.equal(h.page.open, true)
  const c = setup({}, { getConfig: () => Promise.reject(new Error('current detail failure')) })
  await c.page.handleUpdate({ configId: 2 })
  assert.deepStrictEqual(c.errors, ['current detail failure']); h.dispose(); c.dispose()
})
test('render branches use metadata and sensitive precedence', async () => {
  assert(sfc.template.content.indexOf('v-if="form.sensitive"') < sfc.template.content.indexOf('v-else-if="configValueType === \'boolean\'"'))
  assert(sfc.template.content.includes('v-model="booleanConfigValue"') && sfc.template.content.includes('v-for="option in enumOptions"'))
  assert(sfc.template.content.includes('type="number"') && sfc.template.content.includes(':min="numericRules.min"') && sfc.template.content.includes('slot="append"'))
})
async function run() { for (const test of tests) { await test.run(); console.log('PASS ' + test.name) } console.log('configValueControls: ' + tests.length + ' scenarios/groups passed; 0 failed; 0 skipped') }
run().catch(error => { console.error(error); process.exitCode = 1 })
