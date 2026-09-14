const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs'), path = require('path'), vm = require('vm'), babel = require('@babel/core')
const Vue = require('vue'), compiler = require('vue-template-compiler')
const root = path.resolve(__dirname, '..'), modules = new Map()
function load(relative, deps = {}) {
  if (modules.has(relative) && !relative.endsWith('.vue')) return modules.get(relative)
  const source = fs.readFileSync(path.join(root, relative), 'utf8')
  const script = relative.endsWith('.vue') ? compiler.parseComponent(source).script.content : source
  const code = babel.transformSync(script, { filename: relative, babelrc: false, configFile: false, plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')] }).code
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, Date, Set, Map, Number, String, JSON, Uint32Array, Array, Blob, setTimeout, clearTimeout,
    document: { body: {} }, window: deps.window,
    require(name) {
      if (name in deps) return deps[name]
      if (name === '@/utils/generator/config') return load('src/utils/generator/config.js')
      if (name === '@/utils/generator/drawingDefault') return load('src/utils/generator/drawingDefault.js')
      if (name === '@/utils/generator/draft') return load('src/utils/generator/draft.js')
      return {}
    }
  }, { filename: relative })
  modules.set(relative, module.exports); return module.exports
}
const draft = load('src/utils/generator/draft.js'), config = load('src/utils/generator/config.js')
const defaults = load('src/utils/generator/drawingDefault.js'); defaults.initDrawingDefaultValue()
const tags = [...config.inputComponents, ...config.selectComponents, ...config.layoutComponents].map(item => item.tag).filter(Boolean)
const clone = x => JSON.parse(JSON.stringify(x))
function record(label = '草稿') { return { format: 'erp-form-design', version: 1, savedAt: '2026-09-12T00:00:00.000Z', fields: [{ ...clone(defaults.drawingDefaultValue[0]), label }], formConf: clone(config.formConf), activeId: 6 } }
function storage() {
  const values = new Map()
  return { values, blocked: false, get length() { return values.size }, key(i) { return [...values.keys()][i] },
    getItem(key) { if (this.blocked) throw new Error('Storage denied'); return values.get(key) || null },
    setItem(key, value) { if (this.blocked) throw new Error('Quota exceeded'); values.set(key, value) }
  }
}
let serial = 0
function setup(data = storage(), actor = '10') {
  let dept = '20', confirm = () => Promise.resolve(), downloads = []
  const window = { localStorage: data, crypto: { getRandomValues(array) { array.fill(++serial) } }, addEventListener() {}, removeEventListener() {} }
  const Component = load('src/views/tool/build/index.vue', { window, '@/utils/shopContext': { getSelectedDeptId: () => dept } }).default
  const page = new Vue({ ...Component, beforeCreate: [function () {
    this.$store = Vue.observable({ getters: { id: actor } }); this.$confirm = (...args) => confirm(...args)
    this.$download = { saveAs(blob, name) { downloads.push({ blob, name }) } }
  }, Component.beforeCreate] })
  return { page, data, downloads, changeDept(value) { dept = value }, confirm(fn) { confirm = fn }, async ready() { await Vue.nextTick(); await Vue.nextTick() }, close() { page.$destroy() } }
}
function edit(h, value) { h.page.drawingList[0].label = value }
async function tick() { await Vue.nextTick(); await Vue.nextTick() }
function input(file) { return { target: { files: [file], value: 'file.json' } } }

test('automatic save restores field properties, nested layout and form configuration', async () => {
  const h = setup(); await h.ready(); edit(h, '采购备注'); h.page.formConf.labelWidth = 140
  h.page.drawingList.push({ layout: 'rowFormItem', componentName: 'row500', formId: 500, children: [{ ...clone(defaults.drawingDefaultValue[0]), formId: 501, vModel: 'nested', label: '子项' }] })
  await tick(); await new Promise(resolve => setTimeout(resolve, 250))
  assert.equal(h.page.draftDirty, false); assert.equal(h.data.length, 1)
  const restored = setup(h.data); await restored.ready()
  assert.equal(restored.page.drawingList[0].label, '采购备注'); assert.equal(restored.page.drawingList[1].children[0].label, '子项')
  assert.equal(restored.page.drawingList[1].children[0].placeholder, defaults.drawingDefaultValue[0].placeholder); assert.equal(restored.page.formConf.labelWidth, 140); assert.equal(restored.page.idGlobal, 501)
  restored.page.addComponent(restored.page.inputComponents[0]); assert.equal(restored.page.activeId, 502)
  h.close(); restored.close()
})
test('parallel editor records remain separate and shared defaults are never mutated', async () => {
  const data = storage(), a = setup(data), b = setup(data); await a.ready(); await b.ready()
  edit(a, '窗口A'); edit(b, '窗口B'); a.page.formConf.span = 12; await tick()
  assert(a.page.persistDesignDraft()); assert(b.page.persistDesignDraft()); assert.notEqual(a.page.draftKey, b.page.draftKey)
  assert.equal(data.length, 2); assert.equal(config.formConf.span, 24); assert.equal(defaults.drawingDefaultValue[0].label, '手机号')
  a.page.openDraftRecovery(); assert.equal(a.page.savedDrafts.length, 2)
  a.page.selectedDraftKey = b.page.draftKey; await a.page.restoreSelectedDraft(); assert.equal(a.page.drawingList[0].label, '窗口B')
  a.close(); b.close()
})
test('blank and incomplete design remains a recoverable draft', async () => {
  const h = setup(); await h.ready(); h.page.formConf.formModel = ''; h.page.drawingList[0].vModel = ''; await tick()
  assert(h.page.persistDesignDraft()); h.page.drawingList = []; h.page.activeData = {}; h.page.activeId = null; await tick()
  assert(h.page.persistDesignDraft()); const restored = setup(h.data); await restored.ready()
  assert.equal(restored.page.drawingList.length, 0); assert.equal(restored.page.formConf.formModel, '')
  h.close(); restored.close()
})
test('actor and department boundaries never write one account design under another key', async () => {
  const data = storage(), a = setup(data, '10'); await a.ready(); edit(a, 'A私有草稿'); await tick(); a.page.persistDesignDraft()
  const b = setup(data, '11'); await b.ready(); assert.equal(b.page.drawingList[0].label, '手机号')
  a.changeDept('99'); edit(a, '原组织未存'); await tick(); assert.equal(a.page.persistDesignDraft(), false)
  assert.equal([...data.values.keys()].some(key => key.includes(':99:')), false)
  a.page.$store.getters.id = '11'; await tick(); assert.equal(a.page.drawingList[0].label, '手机号')
  a.close(); b.close()
})
test('storage denial preserves editor and export remains available, unload warns', async () => {
  const data = storage(), h = setup(data); await h.ready(); edit(h, '必须保留'); await tick(); data.blocked = true
  assert.equal(h.page.persistDesignDraft(), false); assert.equal(h.page.drawingList[0].label, '必须保留')
  h.page.exportDesignDraft(); assert.equal(h.downloads.length, 1); assert((await h.downloads[0].blob.text()).includes('必须保留'))
  let prevented = false; const event = { preventDefault() { prevented = true } }; h.page.beforeDraftUnload(event)
  assert(prevented); assert.equal(event.returnValue, ''); data.blocked = false; assert(h.page.persistDesignDraft()); h.close()
})
test('corrupt stored data is preserved and no other account records are read', async () => {
  const data = storage(), own = draft.designDraftPrefix('10', '20'), other = draft.designDraftPrefix('11', '20')
  data.setItem(own + 'bad', '{broken'); data.setItem(other + 'good', JSON.stringify(record('别人的')))
  const h = setup(data); await h.ready(); assert.equal(h.page.drawingList[0].label, '手机号'); assert(h.page.draftError.includes('损坏'))
  edit(h, '新的'); await tick(); assert(h.page.persistDesignDraft()); assert.equal(data.getItem(own + 'bad'), '{broken'); h.close()
})
test('valid JSON import restores full configuration and only saves a draft', async () => {
  const h = setup(); await h.ready(); const value = record('导入设计'); value.formConf.span = 8
  await h.page.importDesignDraft(input({ size: 500, text: () => Promise.resolve(JSON.stringify(value)) }))
  assert.equal(h.page.drawingList[0].label, '导入设计'); assert.equal(h.page.formConf.span, 8); assert.equal(h.page.drawerVisible, false)
  assert.equal(h.downloads.length, 0); assert.equal(h.page.draftDirty, false); h.close()
})
test('import parsing and pending confirmation cannot overwrite newer edits', async () => {
  const h = setup(); await h.ready(); let finish
  const pending = h.page.importDesignDraft(input({ size: 500, text: () => new Promise(resolve => { finish = resolve }) }))
  edit(h, '读取期间新输入'); await tick(); finish(JSON.stringify(record('旧文件'))); await pending
  assert.equal(h.page.drawingList[0].label, '读取期间新输入')
  let confirm; h.confirm(() => new Promise(resolve => { confirm = resolve }))
  const next = h.page.importDesignDraft(input({ size: 500, text: () => Promise.resolve(JSON.stringify(record('旧确认'))) }))
  await tick(); edit(h, '确认期间新输入'); await tick(); confirm(); await next
  assert.equal(h.page.drawingList[0].label, '确认期间新输入'); h.close()
})
test('import cancellation, invalid file and too-large file preserve current editor', async () => {
  const h = setup(); await h.ready(); edit(h, '现有设计'); await tick(); h.confirm(() => Promise.reject('cancel'))
  await h.page.importDesignDraft(input({ size: 500, text: () => Promise.resolve(JSON.stringify(record('取消'))) }))
  await h.page.importDesignDraft(input({ size: 10, text: () => Promise.resolve('{broken') }))
  let read = false; await h.page.importDesignDraft(input({ size: draft.MAX_DRAFT_BYTES + 1, text() { read = true } }))
  assert.equal(h.page.drawingList[0].label, '现有设计'); assert.equal(read, false); h.close()
})
test('untrusted draft cannot inject render hooks, arbitrary tags or duplicate field identities', () => {
  for (const key of ['__proto__', 'constructor', 'domProps', 'on', 'nativeOn', 'attrs']) {
    const text = JSON.stringify(record()).replace('"label":"草稿"', '"label":"草稿","' + key + '":{}')
    assert.throws(() => draft.parseDesignDraft(text, tags), /不支持的属性/)
  }
  const wrong = record(); wrong.fields[0].tag = 'iframe'; assert.throws(() => draft.parseDesignDraft(JSON.stringify(wrong), tags), /不支持的组件/)
  const duplicate = record(); duplicate.fields.push(clone(duplicate.fields[0])); assert.throws(() => draft.parseDesignDraft(JSON.stringify(duplicate), tags), /重复/)
  assert.equal({}.polluted, undefined)
})
test('recursive selected field identity and next id are derived from actual tree', () => {
  const value = record(); value.fields = [{ formId: 600, componentName: 'r600', layout: 'rowFormItem', children: value.fields }]; value.idGlobal = 1
  const parsed = draft.parseDesignDraft(JSON.stringify(value), tags)
  assert.equal(parsed.idGlobal, 600); assert.equal(draft.findDesignField(parsed.fields, 6).formId, 6)
})
test('existing generator template compiles with draft actions and no automatic publish action', () => {
  const source = fs.readFileSync(path.join(root, 'src/views/tool/build/index.vue'), 'utf8'), sfc = compiler.parseComponent(source)
  const compiled = compiler.compile(sfc.template.content); assert.deepEqual(compiled.errors, [])
  assert(source.includes('@change="importDesignDraft"')); assert(source.includes('openDraftRecovery'))
})

test('account and organization changes synchronously flush the original key before replacing the editor', async () => {
  const h = setup(); await h.ready(); const oldKey = h.page.draftKey
  edit(h, '切换前未等防抖'); h.page.$store.getters.id = '11'; await tick()
  assert.equal(JSON.parse(h.data.getItem(oldKey)).fields[0].label, '切换前未等防抖')
  assert.equal(h.page.drawingList[0].label, '手机号')
  const nextKey = h.page.draftKey; edit(h, '换组织前最后输入'); h.changeDept('30'); h.page.initializeDesignDraft(); await tick()
  assert.equal(JSON.parse(h.data.getItem(nextKey)).fields[0].label, '换组织前最后输入')
  assert.equal(h.page.drawingList[0].label, '手机号'); h.close()
})
test('storage failure during account change retains a private pending snapshot for its original account', async () => {
  const h = setup(); await h.ready(); edit(h, '原账号待恢复'); await tick(); h.data.blocked = true
  h.page.$store.getters.id = '11'; await tick(); assert.equal(h.page.drawingList[0].label, '手机号')
  assert.equal(Object.keys(h.page.retiredDesignDrafts).length, 1)
  h.page.$store.getters.id = '10'; await tick(); assert.equal(h.page.drawingList[0].label, '原账号待恢复')
  h.data.blocked = false; assert(h.page.persistDesignDraft()); assert.equal(Object.keys(h.page.retiredDesignDrafts).length, 0); h.close()
})
test('malformed render-dependent option and rule shapes are rejected without replacing or saving editor', async () => {
  const h = setup(); await h.ready(); edit(h, '好设计'); await tick(); h.page.persistDesignDraft(); const before = h.data.getItem(h.page.draftKey)
  for (const invalid of [
    { tag: 'el-select', options: [null] },
    { tag: 'el-radio-group', options: [1] },
    { tag: 'el-cascader', options: [{ label: '组', children: [null] }], props: { props: {} } },
    { tag: 'el-cascader', options: [], props: {} },
    { regList: [null] }
  ]) {
    const value = record('坏设计'); Object.assign(value.fields[0], invalid)
    await h.page.importDesignDraft(input({ size: 500, text: () => Promise.resolve(JSON.stringify(value)) }))
    assert.equal(h.page.drawingList[0].label, '好设计'); assert.equal(h.data.getItem(h.page.draftKey), before)
  }
  h.close()
})
test('maximum safe imported identity is remapped before subsequent additions and preserves active field name', async () => {
  const h = setup(); await h.ready(); const value = record('高编号'); value.fields[0].formId = Number.MAX_SAFE_INTEGER; value.activeId = Number.MAX_SAFE_INTEGER
  await h.page.importDesignDraft(input({ size: 500, text: () => Promise.resolve(JSON.stringify(value)) }))
  assert.equal(h.page.activeId, 101); assert.equal(h.page.activeData.vModel, 'mobile')
  h.page.addComponent(h.page.inputComponents[0]); h.page.addComponent(h.page.inputComponents[0]); await tick()
  assert.deepEqual(Array.from(h.page.drawingList, item => item.formId), [101, 102, 103])
  assert(h.page.persistDesignDraft()); h.page.exportDesignDraft(); assert.equal(h.downloads.length, 1); h.close()
})

test('unrenderable pending local draft remains exportable and cannot be replaced by an automatic blank save', async () => {
  const h = setup(); await h.ready(); const own = h.page.draftScope, raw = record('未完成旧设计'); raw.fields[0].regList = [null]
  h.page.retiredDesignDrafts[own] = JSON.stringify(raw); h.page.initializeDesignDraft(); await tick()
  assert(h.page.draftPendingRecoveryError); assert.equal(h.page.persistDesignDraft(), false)
  h.page.exportDesignDraft(); assert((await h.downloads[0].blob.text()).includes('未完成旧设计'))
  assert.equal(h.page.retiredDesignDrafts[own], JSON.stringify(raw)); h.close()
})
