const assert = require("assert")
const {
  applyActualQtyChange,
  buildStockCheckSummary,
  decorateCategories,
  filterDetailsByCategory,
  flattenCategories,
  formatQuantity,
  getCategoryFilterIds,
  hasDetailCategoryMetadata,
  lossQuantity,
  normalizeDetail,
  profitQuantity,
  refreshDetailDiff,
  requiresRecount,
  rowLossQuantity
} = require("../src/views/inventory/stockCheck/stockCheckDetailRules")

const decoratedCategories = decorateCategories([
  {
    categoryId: 1,
    categoryName: "茶叶",
    children: [
      {
        categoryId: 2,
        categoryName: "红茶",
        ancestors: "0,1",
        children: [
          { categoryId: 3, categoryName: "正山小种", ancestors: "0,1,2" }
        ]
      }
    ]
  },
  { categoryId: 4, categoryName: "茶具" }
])
const categoryOptions = flattenCategories(decoratedCategories)

assert.deepStrictEqual(
  categoryOptions.map(item => [item.categoryId, item.categoryFullPath]),
  [
    [1, "茶叶"],
    [2, "茶叶 / 红茶"],
    [3, "茶叶 / 红茶 / 正山小种"],
    [4, "茶具"]
  ],
  "category decoration should build deterministic full paths before flattening"
)

assert.deepStrictEqual(
  Array.from(getCategoryFilterIds(1, categoryOptions)).sort(),
  ["1", "2", "3"],
  "a category filter should include all descendants from ancestors and nested children"
)

const detailRows = [
  { productId: 10, categoryId: 2, categoryName: "红茶" },
  { productId: 11, categoryId: 3, categoryFullPath: "茶叶 / 红茶 / 正山小种" },
  { productId: 12, categoryId: 4, categoryName: "茶具" },
  { productId: 13, categoryFullPath: "茶叶 / 红茶 / 滇红" },
  null
]

assert.deepStrictEqual(
  filterDetailsByCategory(detailRows, 1, categoryOptions).map(row => row.productId),
  [10, 11, 13],
  "category filtering should accept descendant ids and legacy path-only metadata"
)
assert.strictEqual(
  filterDetailsByCategory(detailRows, undefined, categoryOptions),
  detailRows,
  "an empty category filter should preserve the original detail collection"
)
assert.strictEqual(hasDetailCategoryMetadata({ productId: 10 }), false)
assert.strictEqual(hasDetailCategoryMetadata({ categoryId: 0 }), true)
assert.strictEqual(hasDetailCategoryMetadata({ categoryName: "红茶" }), true)

assert.strictEqual(
  requiresRecount({ bookQty: 10, actualQty: 9 }, 1),
  true,
  "a difference equal to the threshold should require a recount"
)
assert.strictEqual(requiresRecount({ bookQty: 10, actualQty: 9 }, 0), false)
assert.strictEqual(requiresRecount({ bookQty: 10, actualQty: "" }, 1), false)
assert.strictEqual(
  requiresRecount({ actualQty: 9, recountRequired: "1" }, 1),
  true,
  "a server-frozen recount flag should survive when the book quantity is unavailable"
)

const recounted = { bookQty: 10, actualQty: 8, recountQty: 9 }
applyActualQtyChange(recounted, 1)
assert.deepStrictEqual(
  recounted,
  {
    bookQty: 10,
    actualQty: 8,
    recountQty: 9,
    recountRequired: "1",
    diffQty: -1,
    diffType: "loss"
  },
  "recounted quantities should drive the displayed variance once the threshold is reached"
)

const thresholdCleared = {
  bookQty: 10,
  actualQty: 9.5,
  recountQty: 8,
  recountRequired: "1"
}
applyActualQtyChange(thresholdCleared, 1)
assert.strictEqual(thresholdCleared.recountRequired, "0")
assert.strictEqual(thresholdCleared.recountQty, null)
assert.strictEqual(thresholdCleared.diffQty, -0.5)
assert.strictEqual(thresholdCleared.diffType, "loss")

const originalDetail = { bookQty: 5, actualQty: 7 }
const normalizedDetail = normalizeDetail(originalDetail)
assert.deepStrictEqual(originalDetail, { bookQty: 5, actualQty: 7 })
assert.deepStrictEqual(
  normalizedDetail,
  { bookQty: 5, actualQty: 7, diffQty: 2, diffType: "profit" },
  "detail normalization should not mutate API response objects"
)

const missingBook = { actualQty: 3, diffQty: 99, diffType: "profit" }
refreshDetailDiff(missingBook)
assert.strictEqual(missingBook.diffQty, null)
assert.strictEqual(missingBook.diffType, "none")

const summary = buildStockCheckSummary([
  { bookQty: 10, actualQty: 8 },
  { bookQty: 10, actualQty: 11, recountQty: 12 },
  { bookQty: 6, actualQty: "" }
])
assert.deepStrictEqual(summary, {
  itemCount: 3,
  enteredCount: 2,
  missingCount: 1,
  lossQuantity: 2,
  profitQuantity: 2
})
assert.strictEqual(lossQuantity({ bookQty: 10, actualQty: 8 }), 2)
assert.strictEqual(lossQuantity({ bookQty: 10, actualQty: "" }), null)
assert.strictEqual(profitQuantity({ bookQty: 10, actualQty: 11.25 }), 1.25)
assert.strictEqual(profitQuantity({ bookQty: 10, actualQty: "" }), 0)
assert.strictEqual(rowLossQuantity({ totalDiffQuantity: -3.5 }), 3.5)
assert.strictEqual(rowLossQuantity({ totalDiffQuantity: 2 }), 0)
assert.strictEqual(formatQuantity(null), "-")
assert.strictEqual(formatQuantity("not-a-number"), "-")
assert.strictEqual(formatQuantity(12), "12")
assert.strictEqual(formatQuantity(12.5), "12.5")
assert.strictEqual(formatQuantity(12.345), "12.35")

console.log("stockCheckDetailRules tests passed")
