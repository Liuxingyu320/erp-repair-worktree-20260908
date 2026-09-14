const test = require('node:test'), assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const babel = require('@babel/core')
// Actual Vue component watchers/mount ordering, with no network or browser process.
class Node {
  constructor(type, tag, text = '') { this.nodeType = type; this.tagName = tag; this.data = text; this.childNodes = []; this.parentNode = null; this.attrs = {} }
  appendChild(n) { if (n.parentNode) n.parentNode.removeChild(n); this.childNodes.push(n); n.parentNode = this; return n }
  insertBefore(n, ref) { if (!ref) return this.appendChild(n); if (n.parentNode) n.parentNode.removeChild(n); this.childNodes.splice(this.childNodes.indexOf(ref), 0, n); n.parentNode = this; return n }
  removeChild(n) { this.childNodes.splice(this.childNodes.indexOf(n), 1); n.parentNode = null; return n }
  setAttribute(k, v) { this.attrs[k] = v }
  removeAttribute(k) { delete this.attrs[k] }
  hasAttribute(k) { return Object.hasOwn(this.attrs, k) }
  get firstChild() { return this.childNodes[0] }
  get nextSibling() { return this.parentNode && this.parentNode.childNodes[this.parentNode.childNodes.indexOf(this) + 1] }
  get textContent() { return this.nodeType === 3 ? this.data : this.childNodes.map(n => n.textContent).join('') }
  set textContent(v) { this.childNodes = []; this.data = v }
}
global.window = { navigator: { userAgent: 'test' }, addEventListener() {}, removeEventListener() {} }
global.document = { createElement: tag => new Node(1, tag.toUpperCase()), createElementNS: (_, tag) => new Node(1, tag.toUpperCase()), createTextNode: text => new Node(3, undefined, text), createComment: text => new Node(8, undefined, text), createEvent: () => ({ timeStamp: 0 }), querySelector: () => null }
const Vue = require('vue'), compiler = require('vue-template-compiler'), cron = require('../src/utils/cronExpression')
Vue.config.productionTip = false; Vue.config.devtools = false
const dir = path.resolve(__dirname, '../src/components/Crontab')
function load(file) {
  const script = compiler.parseComponent(fs.readFileSync(file, 'utf8')).script.content
  const code = babel.transformSync(script, { babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, console, require: id => id === '@/utils/cronExpression' ? cron : load(path.resolve(path.dirname(file), id)) })
  return module.exports.default
}
const tick = async () => { for (let i = 0; i < 6; i++) await Vue.nextTick() }
const names = ['second', 'min', 'hour', 'day', 'month', 'week', 'year']
const childName = { second: 'Second', min: 'Min', hour: 'Hour', day: 'Day', month: 'Month', week: 'Week', year: 'Year' }
function mount(expression) {
  const component = load(path.join(dir, 'index.vue')), events = []
  const children = Object.fromEntries(names.map(name => [name, { ...component.components['Crontab' + childName[name]], render: h => h('div') }]))
  const p = new Vue({ ...component, propsData: { expression }, render(h) {
    return h('div', this.textOnly || this.parseError ? [] : names.map(name => h(children[name], { key: name, ref: 'cron' + name, props: { check: this.checkNumber, cron: this.crontabValueObj }, on: { update: this.updateCrontabValue } })))
  } })
  p.$on('fill', value => events.push(value)); p.$mount(); return { p, events }
}
for (const expression of ['0 0 9 ? * 6#2', '0 0 9 ? * 1#5', '0 0 9 ? * 7#5', '0 0 0 * * ? 2030-2035', '0 0 0 * * ? 2030/2', '0 0 0 * * ? 2000-2005', '0 0 0 ? JAN MON', '0 0 0 LW * ?', '0 0 0 * * ? 2099/1']) {
  test('ROOT-08 actual parent/children mount keeps unchanged expression: ' + expression, async () => {
    const { p, events } = mount(expression); await tick()
    assert.equal(p.crontabValueString, expression); assert.equal(events.length, 0)
    p.submitFill(); assert.deepEqual(events, [expression]); p.$destroy()
  })
}
test('ROOT-08 weekday/year hydration and editing one field preserve the other fields', async () => {
  const h = mount('0 0 9 ? * 6#2 2030-2035'); await tick()
  assert.equal(h.p.$refs.cronweek.average01, 2); assert.equal(h.p.$refs.cronweek.average02, 6)
  assert.equal(h.p.$refs.cronyear.cycle01, 2030); assert.equal(h.p.$refs.cronyear.cycle02, 2035)
  h.p.$refs.cronweek.average01 = 5; await tick(); assert.equal(h.p.crontabValueString, '0 0 9 ? * 6#5 2030-2035')
  h.p.hidePopup(); assert.equal(h.events.length, 0); h.p.$destroy()
})
test('ROOT-09 zero/negative/nonfinite steps and huge direct enumerations terminate with explicit error', { timeout: 1000 }, () => {
  for (const value of ['0/0', '0/-1', '0/NaN', '0/Infinity', '0/no', '0/0.5', '0/9007199254740993']) {
    assert.equal(cron.parseCronExpression(value + ' * * * * ?').valid, false)
    assert.throws(() => cron.expandField(value, 0, 59))
  }
  assert.throws(() => cron.expandField('0/1', 0, 1000000000)); assert.throws(() => cron.expandField('0/1', 0, Infinity))
  const result = load(path.join(dir, 'result.vue'))
  assert.throws(() => result.methods.getAverageArr.call({}, '0/0', 59))
})
test('ROOT-09 bounded preview has Quartz weekday, fifth occurrence, nearest weekday and leap-day semantics', { timeout: 1000 }, () => {
  const from = new Date(2026, 8, 13, 0, 0, 0)
  const cases = [
    ['0 0 9 ? * 6#2', new Date(2026, 9, 9, 9)],
    ['0 0 9 ? * 7#5', new Date(2026, 9, 31, 9)],
    ['0 0 9 ? * 1L', new Date(2026, 8, 27, 9)],
    ['0 0 9 1W * ?', new Date(2026, 9, 1, 9)],
    ['0 0 0 29 2 ?', new Date(2028, 1, 29)],
    ['0 0 0 LW * ?', new Date(2026, 8, 30)]
  ]
  for (const [expression, expected] of cases) assert.equal(cron.previewCronExpression(expression, from).times[0].getTime(), expected.getTime(), expression)
  assert.equal(cron.previewCronExpression('0 0 0 31 2 ?', from).times.length, 0)
})
test('ROOT-08/09 job generator entry and fill preserve invalid/original text and only write on explicit fill', () => {
  const script = compiler.parseComponent(fs.readFileSync(path.resolve(dir, '../../views/monitor/job/index.vue'), 'utf8')).script.content
  const ast = require('@babel/parser').parse(script, { sourceType: 'module' })
  const methods = ast.program.body.find(n => n.type === 'ExportDefaultDeclaration').declaration.properties.find(n => n.key.name === 'methods').value
  const names = ['handleShowCron', 'crontabFill']
  const snippets = methods.properties.filter(n => names.includes(n.key.name)).map(n => script.slice(n.start, n.end)).join(',')
  const functions = vm.runInNewContext('({' + snippets + '})', cron)
  const errors = [], p = { form: { cronExpression: '0/0 * * * * ?' }, openCron: false, $modal: { msgError: error => errors.push(error) } }
  functions.handleShowCron.call(p); assert.equal(p.openCron, false); assert.equal(p.form.cronExpression, '0/0 * * * * ?'); assert.match(errors[0], /秒/)
  p.form.cronExpression = '0 0 9 ? * 6#2'; functions.handleShowCron.call(p); assert.equal(p.expression, p.form.cronExpression); assert.equal(p.openCron, true)
  functions.crontabFill.call(p, '0 0 9 ? * 6#5'); assert.equal(p.form.cronExpression, '0 0 9 ? * 6#5')
})
test('ROOT-08/09 all changed Cron and job templates compile', () => {
  for (const file of ['index.vue', 'week.vue', 'year.vue', 'result.vue', '../../views/monitor/job/index.vue']) {
    const template = compiler.parseComponent(fs.readFileSync(path.resolve(dir, file), 'utf8')).template.content
    assert.deepEqual(compiler.compile(template).errors, [])
  }
})
