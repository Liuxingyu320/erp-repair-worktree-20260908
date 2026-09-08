const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const stockSource = read("src/views/inventory/stock/index.vue")
const stockLogSource = read("src/views/inventory/stock/log.vue")
const itemSelectSource = read("src/views/inventory/components/InventoryItemSelect.vue")
const inventoryStatusConstants = read("../erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvStatusConstants.java")

assert.ok(
  stockSource.includes('oe: "器皿"') &&
    stockLogSource.includes('oe: "器皿"') &&
    itemSelectSource.includes('oe: { label: "器皿"'),
  "stock pages and their item selector should render the OE item type in Chinese"
)

const backendMovementTypes = Array.from(
  inventoryStatusConstants.matchAll(/MOVEMENT_[A-Z_]+\s*=\s*"([^"]+)"/g),
  match => match[1]
)
assert.ok(backendMovementTypes.length > 0, "backend inventory movement types should be discoverable")
backendMovementTypes.forEach(type => {
  assert.ok(
    stockLogSource.includes(`{ value: "${type}", label: "`),
    `stock log should provide a Chinese label for backend movement type ${type}`
  )
})

const selectedValueMatch = itemSelectSource.match(
  /    selectedValue\(\) \{([\s\S]*?)\n    \},\n    normalizedType\(\)/
)
assert.ok(selectedValueMatch, "item selector value normalization should be extractable")

const selectedValue = new Function(selectedValueMatch[1])
const itemSelectContext = {
  value: "1268",
  options: [{ itemId: 1268, itemName: "测试物料" }],
  hasValue(value) {
    return value !== undefined && value !== null && value !== ""
  }
}
assert.strictEqual(
  selectedValue.call(itemSelectContext),
  1268,
  "item selector should reuse the exact option value type instead of displaying a raw string id"
)

const itemLabelMatch = itemSelectSource.match(
  /    itemLabel\(item\) \{([\s\S]*?)\n    \},\n    itemMeta\(item\)/
)
assert.ok(itemLabelMatch, "item selector label formatter should be extractable")
const itemLabel = new Function("item", itemLabelMatch[1])
assert.strictEqual(
  itemLabel.call(itemSelectContext, { itemId: 1268, itemName: "", itemCode: "" }),
  "未命名物料",
  "missing item metadata should use a business fallback instead of exposing the internal id"
)

const movementTypeOptionMatch = stockLogSource.match(
  /    movementTypeOption\(type\) \{([\s\S]*?)\n    \},\n    changeQuantityClass\(value\)/
)
assert.ok(movementTypeOptionMatch, "movement type formatter should be extractable")
const movementTypeOption = new Function("type", movementTypeOptionMatch[1])
const movementContext = {
  movementTypeOptions: [],
  movementTypeAliases: {},
  hasValue: itemSelectContext.hasValue
}
assert.deepStrictEqual(
  movementTypeOption.call(movementContext, "future_internal_code"),
  { label: "其他库存变动", tagType: "info" },
  "unknown movement types should not expose internal English codes"
)

console.log("stockDisplayLocalization tests passed")
