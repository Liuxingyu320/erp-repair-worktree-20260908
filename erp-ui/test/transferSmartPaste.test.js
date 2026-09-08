const assert = require("assert")

const {
  adjustTransferQuantity,
  formatReferenceCostPrice,
  formatReferenceSubtotal,
  isValidTransferQuantity,
  matchTransferPasteItems,
  normalizeUnit,
  parseTransferPaste,
  selectedCandidate
} = require("../src/utils/transferSmartPaste")

const pasted = `蜂蜜20瓶
百香果30瓶
气泡水24瓶
干姜水24瓶
雪莲马蹄2箱
鲜榨芒果1箱
青瓜酱1瓶
无花果雪梨2箱
乌梅金桂1箱
百合银耳1箱
蜡烛6盒
茶渍粉2盒
滇红2包
大红袍2包
桂花九曲红梅6包
正山小种6包
古树熟普5包
普洱熟茶5包
老白茶5饼
古树生普2包
茉莉花3包
胎菊3包
玫瑰花5包
凤凰单丛（鸭屎香）3包
铁观音3包
高山乌龙2包
金牡丹2包
西湖龙井5包
明前龙井5包
姜糖五味饮3包
红枣片2盒
山楂片3包
桂花3包

收件人：示例收件人电话：13800000000
收货地址：示例省示例市示例区示例路 1 号`

const parsed = parseTransferPaste(pasted)
assert.strictEqual(parsed.items.length, 33)
assert.deepStrictEqual(parsed.recipient, {
  recipientName: "示例收件人",
  recipientPhone: "13800000000",
  shippingAddress: "示例省示例市示例区示例路 1 号"
})
assert.deepStrictEqual(parsed.ignoredLines, [])
assert.deepStrictEqual(parsed.items[0], {
  lineNo: 1,
  rawLine: "蜂蜜20瓶",
  requestedName: "蜂蜜",
  quantity: 20,
  requestedUnit: "瓶"
})
assert.strictEqual(parsed.items[23].requestedName, "凤凰单丛（鸭屎香）")

const numbered = parseTransferPaste("1. 蜂蜜 × 2 瓶\n- 老白茶 1 饼\n备注：周五前送达")
assert.strictEqual(numbered.items.length, 2)
assert.strictEqual(numbered.items[0].quantity, 2)
assert.strictEqual(numbered.ignoredLines.length, 1)
assert.strictEqual(normalizeUnit("公斤"), "kg")

assert.strictEqual(adjustTransferQuantity(20, -1), 19)
assert.strictEqual(adjustTransferQuantity(1, -1), 0.01, "minus control must never produce a non-positive quantity")
assert.strictEqual(adjustTransferQuantity("", 1), 1, "plus control should repair an empty quantity")
assert.strictEqual(isValidTransferQuantity(0), false)
assert.strictEqual(isValidTransferQuantity("0.01"), true)
assert.strictEqual(formatReferenceCostPrice(36), "参考成本价 ¥36.00")
assert.strictEqual(formatReferenceCostPrice(0), "参考成本价 ¥0.00")
assert.strictEqual(formatReferenceCostPrice(null), "参考成本价 未维护")
assert.strictEqual(formatReferenceSubtotal(25, 24.92, "瓶"), "25 瓶 × ¥24.92 = ¥623.00")
assert.strictEqual(formatReferenceSubtotal(2.5, 30, "3斤"), "2.5 3斤 × ¥30.00 = ¥75.00")
assert.strictEqual(formatReferenceSubtotal(0, 24.92, "瓶"), "", "invalid quantities must not produce a subtotal")
assert.strictEqual(formatReferenceSubtotal(25, null, "瓶"), "", "missing reference cost must not produce a subtotal")

const candidates = [
  { itemType: "product", itemId: 1, itemName: "蜂蜜", itemCode: "P001" },
  { itemType: "product", itemId: 2, itemName: "大红袍", itemCode: "P002", spec: "125g/罐 · 一级" },
  { itemType: "product", itemId: 3, itemName: "大红袍", itemCode: "P003", spec: "125g/罐 · 二级" },
  { itemType: "product", itemId: 4, itemName: "凤凰单丛鸭屎香", itemCode: "P004" },
  { itemType: "gift", itemId: 5, itemName: "雪莲马蹄礼盒", itemCode: "G005" }
]

const matches = matchTransferPasteItems([
  { requestedName: "蜂蜜", quantity: 2, requestedUnit: "瓶" },
  { requestedName: "大红袍", quantity: 3, requestedUnit: "包" },
  { requestedName: "凤凰单丛（鸭屎香）", quantity: 3, requestedUnit: "包" },
  { requestedName: "雪莲马蹄", quantity: 2, requestedUnit: "箱" },
  { requestedName: "不存在物料", quantity: 1, requestedUnit: "盒" }
], candidates)

assert.strictEqual(matches[0].matchState, "exact")
assert.strictEqual(matches[0].selectedKey, "", "unique exact names must still wait for manual selection")
assert.strictEqual(matches[0].included, false)
assert.strictEqual(matches[1].matchState, "duplicate")
assert.strictEqual(matches[1].selectedKey, "", "same-name variants must wait for manual confirmation")
assert.strictEqual(matches[2].matchState, "exact", "punctuation differences should normalize to an exact name")
assert.strictEqual(matches[3].matchState, "similar")
assert.strictEqual(matches[3].selectedKey, "", "fuzzy candidates must never be chosen automatically")
assert.strictEqual(matches[4].matchState, "unmatched")

matches[0].selectedKey = "product:1"
assert.strictEqual(selectedCandidate(matches[0]).itemCode, "P001")
matches[1].selectedKey = "product:3"
assert.strictEqual(selectedCandidate(matches[1]).itemCode, "P003")

console.log("transfer smart paste tests passed")
