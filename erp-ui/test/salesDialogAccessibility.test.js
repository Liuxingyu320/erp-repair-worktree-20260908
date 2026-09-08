const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/sales/index.vue"),
  "utf8"
)

assert.ok(source.includes('@opened="focusSalesTitle"'), "sales editor must move focus into the dialog")
assert.ok(source.includes('@closed="restoreSalesDialogFocus"'), "sales editor must restore the invoking control")
assert.ok(source.includes(':close-on-click-modal="false"'), "sales editor must not discard a draft through a backdrop click")
assert.ok(source.includes('ref="salesTitleInput"'), "sales title must expose a stable focus target")
assert.ok(source.includes('aria-label="销售单标题"'), "sales title must have an explicit accessible name")
assert.ok(source.includes("document.documentElement.contains(previous)"), "focus restoration must reject detached triggers")

console.log("sales dialog accessibility tests passed")
