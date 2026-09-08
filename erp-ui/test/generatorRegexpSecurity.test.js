const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('../node_modules/@babel/core')
const compiler = require('../node_modules/vue-template-compiler')

const root = path.resolve(__dirname, '..')

function loadModule(relativePath, dependencies = {}) {
  const absolutePath = path.join(root, relativePath)
  const source = fs.readFileSync(absolutePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: absolutePath,
    babelrc: false,
    configFile: false,
    sourceType: 'module',
    plugins: [require.resolve('../node_modules/@babel/plugin-transform-modules-commonjs')]
  })
  const moduleRef = { exports: {} }
  vm.runInNewContext(transformed.code, {
    module: moduleRef,
    exports: moduleRef.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) return dependencies[request]
      throw new Error(`unexpected dependency: ${request}`)
    }
  }, { filename: absolutePath })
  return { exports: moduleRef.exports, source }
}

const regexpModule = loadModule('src/utils/generator/regexp.js')
const securityModule = loadModule('src/utils/generator/security.js')
const {
  parseRegExpPattern,
  serializeRegExpPattern,
  serializeScriptString,
  serializeScriptValue
} = regexpModule.exports

function plain(value) {
  return value && { source: value.source, flags: value.flags }
}
function throwsNamed(callback, name, message) {
  assert.throws(callback, error => error && error.name === name, message)
}

assert.deepStrictEqual(
  plain(parseRegExpPattern('/^1(3|4|5|7|8|9)\\d{9}$/')),
  { source: '^1(3|4|5|7|8|9)\\d{9}$', flags: '' },
  'the default mobile-number pattern should remain compatible'
)
assert.deepStrictEqual(plain(parseRegExpPattern('^[a-z]+$')), { source: '^[a-z]+$', flags: '' })
assert.deepStrictEqual(plain(parseRegExpPattern('/^[a-z]+$/imu')), { source: '^[a-z]+$', flags: 'imu' })
assert.deepStrictEqual(
  plain(parseRegExpPattern('/^https?:\\/\\/[a-z/]+\\/[a-z]+$/i')),
  { source: '^https?:\\/\\/[a-z/]+\\/[a-z]+$', flags: 'i' },
  'escaped slashes and slashes inside character classes should not close the literal early'
)
assert.strictEqual(parseRegExpPattern(null), null)
assert.strictEqual(parseRegExpPattern(undefined), null)
assert.strictEqual(parseRegExpPattern('   '), null)
throwsNamed(() => parseRegExpPattern(42), 'TypeError')
assert.throws(() => parseRegExpPattern('/abc'), /闭合斜杠/)
assert.throws(() => parseRegExpPattern('//'), /不能为空/)
throwsNamed(() => parseRegExpPattern('/[a-/'), 'SyntaxError')
for (const value of ['/abc/g', '/abc/y', '/abc/d', '/abc/v', '/abc/z', '/abc/ii']) {
  throwsNamed(() => parseRegExpPattern(value), 'SyntaxError', `${value} should reject unsafe or invalid flags`)
}
throwsNamed(() => parseRegExpPattern('a'.repeat(513)), 'RangeError')
assert.strictEqual(
  serializeRegExpPattern('/a\\/b[\\/c]/ms'),
  'new RegExp("a\\\\/b[\\\\/c]", "ms")'
)
assert.strictEqual(
  serializeScriptString('</script>\u2028\u2029'),
  '"\\u003c/script>\\u2028\\u2029"',
  'script-sensitive characters must be escaped after JSON serialization'
)
assert.strictEqual(serializeScriptValue({ value: '</script>' }), '{"value":"\\u003c/script>"}')

const generatorModule = loadModule('src/utils/generator/js.js', {
  '@/utils/index': { exportDefault: 'export default ', titleCase: value => value },
  './config': { trigger: { 'el-input': 'blur' } },
  './regexp': regexpModule.exports,
  './security': securityModule.exports
})
const { makeUpJs } = generatorModule.exports

function generatedConfig(field, overrides = {}) {
  return Object.assign({
    fields: [field],
    formBtns: false,
    formRef: 'elForm',
    formModel: 'formData',
    formRules: 'rules'
  }, overrides)
}

function compileGeneratedSfc(script, template = '<div />') {
  const sfc = `<template>${template}</template><script>${script}</script>`
  assert.strictEqual((sfc.match(/<script(?:\\s|>)/gi) || []).length, 1,
    'generated SFC must contain exactly one opening script block')
  assert.strictEqual((sfc.match(/<\/script>/gi) || []).length, 1,
    'generated SFC must contain exactly one closing script block')
  const descriptor = compiler.parseComponent(sfc)
  assert.ok(descriptor.script, 'generated SFC must retain its intended script block')
  const transformed = babel.transformSync(descriptor.script.content, {
    filename: 'generated-component.js',
    babelrc: false,
    configFile: false,
    sourceType: 'module',
    plugins: [require.resolve('../node_modules/@babel/plugin-transform-modules-commonjs')]
  })
  const moduleRef = { exports: {} }
  const sandbox = { module: moduleRef, exports: moduleRef.exports }
  vm.runInNewContext(transformed.code, sandbox, { filename: 'generated-component.js' })
  return { component: moduleRef.exports.default || moduleRef.exports, sandbox, sfc }
}

const maliciousPattern = '/safe/;globalThis.__generatorRegexpExecuted = true'
delete globalThis.__generatorRegexpExecuted
throwsNamed(() => makeUpJs(generatedConfig({
    tag: 'el-input',
    vModel: 'phone',
    label: '手机号',
    placeholder: '请输入手机号',
    defaultValue: '',
    required: false,
    regList: [{ pattern: maliciousPattern, message: '非法规则' }]
  }), 'file'), 'SyntaxError', 'trailing expressions must be rejected as flags instead of executed')
assert.strictEqual(globalThis.__generatorRegexpExecuted, undefined)

const scriptBreakout = '</script><script>globalThis.__generatorScriptExecuted = true</script>'
const maliciousMessage = `格式错误' }, hacked: true, extra: { message: '${scriptBreakout}\u2028\u2029`
const maliciousDefault = `', injected: globalThis.__generatorDefaultExecuted = true, safe: '${scriptBreakout}\u2028\u2029`
const maliciousRequiredMessage = `请输入'手机号${scriptBreakout}\u2028`
const maliciousTrigger = `blur${scriptBreakout}\u2029`
generatorModule.exports = loadModule('src/utils/generator/js.js', {
  '@/utils/index': { exportDefault: 'export default ', titleCase: value => value },
  './config': { trigger: { 'el-input': maliciousTrigger } },
  './regexp': regexpModule.exports,
  './security': securityModule.exports
}).exports
const hardenedMakeUpJs = generatorModule.exports.makeUpJs

delete globalThis.__generatorScriptExecuted
delete globalThis.__generatorDefaultExecuted
const generated = hardenedMakeUpJs(generatedConfig({
    tag: 'el-input',
    vModel: 'phone',
    label: '手机号',
    placeholder: maliciousRequiredMessage,
    defaultValue: maliciousDefault,
    required: true,
    regList: [{ pattern: scriptBreakout, message: maliciousMessage }]
  }), 'file')
assert.ok(generated.includes(`message: ${serializeScriptString(maliciousMessage)}`),
  'malicious validation messages must remain JSON string data')
assert.ok(generated.includes(`message: ${serializeScriptString(maliciousRequiredMessage)}`),
  'required messages must remain JSON string data')
assert.ok(generated.includes(`trigger: ${serializeScriptString(maliciousTrigger)}`))
assert.ok(generated.includes(`pattern: new RegExp(${serializeScriptString(scriptBreakout)}, "")`))
assert.ok(!generated.toLowerCase().includes('</script>'), 'generated script content must not contain an HTML closing script tag')
assert.ok(generated.includes('\\u003c/script>'))
assert.ok(generated.includes('\\u2028') && generated.includes('\\u2029'))

const compiled = compileGeneratedSfc(generated)
const generatedData = compiled.component.data()
assert.deepStrictEqual(Object.keys(generatedData.formData), ['phone'],
  'default-value injection must not add generated data keys')
assert.strictEqual(generatedData.formData.phone, maliciousDefault)
assert.strictEqual(generatedData.rules.phone[0].message, maliciousRequiredMessage)
assert.strictEqual(generatedData.rules.phone[0].trigger, maliciousTrigger)
assert.strictEqual(generatedData.rules.phone[1].message, maliciousMessage)
assert.strictEqual(generatedData.rules.phone[1].trigger, maliciousTrigger)
assert.strictEqual(globalThis.__generatorScriptExecuted, undefined)
assert.strictEqual(globalThis.__generatorDefaultExecuted, undefined)
assert.strictEqual(compiled.sandbox.__generatorScriptExecuted, undefined)
assert.strictEqual(compiled.sandbox.__generatorDefaultExecuted, undefined)

for (const invalidModel of ['phone, injected: true', 'phone-name', '</script>', '', 42]) {
  throwsNamed(() => hardenedMakeUpJs(generatedConfig({
    tag: 'el-input',
    vModel: invalidModel,
    defaultValue: '',
    required: false,
    regList: []
  }), 'file'), 'TypeError', `unsafe vModel ${String(invalidModel)} should be rejected`)
}

const htmlModule = loadModule('src/utils/generator/html.js', {
  './config': { trigger: { 'el-input': 'blur' } },
  './security': securityModule.exports
})
const { makeUpHtml } = htmlModule.exports

function generatedHtmlConfig(fields, overrides = {}) {
  return Object.assign({
    fields,
    formBtns: true,
    formRef: 'elForm',
    formModel: 'formData',
    formRules: 'rules',
    size: 'medium',
    labelPosition: 'right',
    labelWidth: 100,
    gutter: 15,
    disabled: false
  }, overrides)
}

function inputField(overrides = {}) {
  return Object.assign({
    layout: 'colFormItem',
    tag: 'el-input',
    vModel: 'phone',
    label: '手机号',
    placeholder: '请输入手机号',
    defaultValue: '',
    span: 24,
    clearable: true,
    required: true,
    regList: []
  }, overrides)
}

const templateBreakout = `x" @click="globalThis.__generatorTemplateExecuted = true`
const textBreakout = '</el-button><el-button @click="globalThis.__generatorTemplateExecuted = true">{{globalThis.__generatorTemplateExecuted = true}}'
const uploadField = {
  layout: 'colFormItem',
  tag: 'el-upload',
  vModel: 'attachment',
  label: `附件${templateBreakout}`,
  defaultValue: [],
  span: 24,
  action: '/upload',
  accept: templateBreakout,
  name: templateBreakout,
  buttonText: textBreakout,
  showTip: true,
  fileSize: 2,
  sizeUnit: 'MB',
  'list-type': 'text',
  'auto-upload': true,
  required: false
}
const legalAndEscapedConf = generatedHtmlConfig([
  inputField({
    label: `手机号${templateBreakout}`,
    placeholder: templateBreakout,
    prepend: textBreakout,
    append: textBreakout
  }),
  uploadField
])
const generatedHtml = makeUpHtml(legalAndEscapedConf, 'file')
assert.ok(generatedHtml.includes('&quot;'), 'attribute-context values must be HTML-attribute escaped')
assert.ok(generatedHtml.includes('&lt;/el-button&gt;'), 'text-context values must be HTML-text escaped')
const compiledTemplate = compiler.compile(generatedHtml)
assert.deepStrictEqual(compiledTemplate.errors, [], 'escaped generated template must compile')
assert.ok(!/on:\s*\{[^}]*["']?click["']?\s*:\s*function[^}]*globalThis\.__generatorTemplateExecuted/.test(compiledTemplate.render),
  'escaped config data must not compile the injected expression into a click handler')
assert.ok(!/_s\(globalThis\.__generatorTemplateExecuted/.test(compiledTemplate.render),
  'escaped text must not compile injected mustache expressions')

const generatedLegalScript = hardenedMakeUpJs(legalAndEscapedConf, 'file')
const compiledLegalSfc = compileGeneratedSfc(generatedLegalScript, generatedHtml)
assert.deepStrictEqual(Object.keys(compiledLegalSfc.component.data().formData), ['phone', 'attachment'],
  'legal input and upload components must retain their generated models')

const buttonConf = generatedHtmlConfig([{
  layout: 'colFormItem',
  tag: 'el-button',
  label: '操作',
  default: '保存',
  span: 24,
  type: 'primary',
  size: 'medium',
  disabled: false,
  required: false
}], { formBtns: false })
const buttonHtml = makeUpHtml(buttonConf, 'file')
const buttonScript = hardenedMakeUpJs(buttonConf, 'file')
const compiledButtonSfc = compileGeneratedSfc(buttonScript, buttonHtml)
assert.ok(buttonHtml.includes('保存'), 'a legal button without vModel must retain its text')
assert.deepStrictEqual(Object.keys(compiledButtonSfc.component.data().formData), [],
  'a legal button without vModel must compile without inventing a model field')

for (const invalidModel of ['phone" @click="attack', 'phone-name', '</el-form>', '', 42]) {
  throwsNamed(() => makeUpHtml(generatedHtmlConfig([inputField({ vModel: invalidModel })]), 'file'),
    'TypeError', `makeUpHtml must independently reject unsafe vModel ${String(invalidModel)}`)
}

for (const key of ['formRef', 'formModel', 'formRules']) {
  const override = { [key]: `safe" @click="attack` }
  throwsNamed(() => makeUpHtml(generatedHtmlConfig([inputField()], override), 'file'),
    'TypeError', `makeUpHtml must reject unsafe ${key}`)
  throwsNamed(() => hardenedMakeUpJs(generatedConfig(inputField(), override), 'file'),
    'TypeError', `makeUpJs must reject unsafe ${key}`)
}

for (const source of [regexpModule.source, securityModule.source, generatorModule.source, htmlModule.source]) {
  assert.ok(!/\beval\s*\(/.test(source), 'generator regex handling must not use eval')
  assert.ok(!/\bnew\s+Function\b/.test(source), 'generator regex handling must not use new Function')
}

console.log('generator regexp security tests passed')
