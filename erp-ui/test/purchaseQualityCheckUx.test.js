const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/purchase/index.vue"),
  "utf8"
)

assert.ok(
  source.includes('qcResult: ""') &&
    !source.includes('qcResult: "passed"'),
  "quality check result must start empty and require an explicit decision"
)
assert.ok(
  source.includes("质检合格或让步接收后才增加当前仓库库存"),
  "receipt confirmation must explain that stock changes only after accepted QC"
)
assert.ok(
  source.includes("业务阶段") && source.includes("purchaseStageLabel"),
  "purchase list should display the shared business stage"
)

console.log("purchaseQualityCheckUx tests passed")
