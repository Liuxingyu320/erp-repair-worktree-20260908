const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('@babel/core')
const payloads = require('../src/views/mobile/feature/mobileActionPayloads')
const file = path.resolve(__dirname, '../src/views/mobile/feature/components/MobileActionDialog.vue')
const source = fs.readFileSync(file, 'utf8')
const code = babel.transformSync(source.match(/<script>([\s\S]*?)<\/script>/)[1], {
  filename: file, babelrc: false, configFile: false,
  plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')]
}).code
const moduleObject = { exports: {} }
vm.runInNewContext(code, { module: moduleObject, exports: moduleObject.exports,
  require: name => name === '../mobileActionPayloads' ? payloads : {}
})
const component = moduleObject.exports.default
const page = { action: { id: 'saveStockCheckInput' }, item: { details: [
  { detailId: 1, actualQty: null, bookQty: 0, productName: '新盘点' },
  { detailId: 2, actualQty: 5, bookQty: 10, recountQty: 8, recountRequired: '1' }
] } }
Object.assign(page, component.data.call(page))
Object.entries(component.methods).forEach(([name, fn]) => { page[name] = fn.bind(page) })
Object.entries(component.computed).forEach(([name, fn]) => Object.defineProperty(page, name, { get: fn.bind(page) }))
page.initQuantityRows()
assert.strictEqual(page.quantityRows[0].quantity, '', 'unentered count stays blank')
page.quantityRows[0].quantity = 5
page.quantityRows[1].quantity = 6
assert.strictEqual(page.validateQuantityRows(), true, 'new or increased actual quantity has no prior-value cap')
assert.strictEqual(page.quantityRows[1].recountQty, 8)
let result = payloads.buildStockCheckInputPayload(page.rawItem.details, page.normalizeQuantityRows())
assert.deepStrictEqual(result, [{ detailId: 1, actualQty: 5, recountQty: null }, { detailId: 2, actualQty: 6, recountQty: 8 }])
page.quantityRows[0].quantity = -1
assert.strictEqual(page.validateQuantityRows(), false)
page.quantityRows[0].quantity = 0
page.quantityRows[1].recountQty = -1
assert.strictEqual(page.validateQuantityRows(), false)
assert.throws(() => payloads.buildStockCheckInputPayload([{ detailId: 1 }], [{ detailId: 1, quantity: 8, recountQty: -1 }]), /复盘数量/)
result = payloads.buildStockCheckInputPayload([{ detailId: 1, actualQty: 8, recountQty: 8 }], [{ detailId: 1, quantity: 8 }])
assert.deepStrictEqual(result, [{ detailId: 1, actualQty: 8, recountQty: 8 }], 'existing recount survives clients that omit it')
page.handleStockCheckActualChange(page.quantityRows[1])
assert.strictEqual(page.quantityRows[1].recountQty, '', 'changing first count invalidates previous recount')
assert.ok(source.includes(':max="isStockCheckQuantityAction() ? undefined : row.remaining"'))
assert.ok(source.includes('recountQty: row.recountQty'))
console.log('stockCheckInputIntegrity: actual counts and recount round-trip passed')
