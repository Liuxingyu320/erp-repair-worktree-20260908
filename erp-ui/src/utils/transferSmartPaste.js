const DEFAULT_UNITS = [
  "公斤", "千克", "毫升", "公升", "kg", "KG", "ml", "ML",
  "瓶", "箱", "盒", "包", "饼", "罐", "袋", "个", "支", "件", "套",
  "桶", "杯", "条", "片", "枚", "组", "提", "板", "卷", "斤", "克", "g", "G", "l", "L"
]

function text(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function normalizeName(value) {
  const source = text(value)
  const normalized = typeof source.normalize === "function" ? source.normalize("NFKC") : source
  return normalized
    .toLowerCase()
    .replace(/[\s\-—_·•,，.。:：;；'"“”‘’（）()\[\]【】{}<>《》/\\]+/g, "")
}

function normalizeUnit(value) {
  const unit = text(value).toLowerCase()
  const aliases = {
    "公斤": "kg",
    "千克": "kg",
    "kg": "kg",
    "克": "g",
    "g": "g",
    "毫升": "ml",
    "ml": "ml",
    "公升": "l",
    "l": "l"
  }
  return aliases[unit] || unit
}

function isValidTransferQuantity(value) {
  const quantity = Number(value)
  return Number.isFinite(quantity) && quantity > 0
}

function adjustTransferQuantity(value, direction) {
  const quantity = Number(value)
  const current = Number.isFinite(quantity) && quantity > 0 ? quantity : 0
  const next = Number(direction) > 0 ? current + 1 : Math.max(current - 1, 0.01)
  return Number(next.toFixed(2))
}

function formatReferenceCostPrice(value) {
  if (value === undefined || value === null || value === "") return "参考成本价 未维护"
  const price = Number(value)
  if (!Number.isFinite(price)) return "参考成本价 未维护"
  return "参考成本价 ¥" + price.toFixed(2)
}

function formatReferenceSubtotal(quantity, referenceCostPrice, unit) {
  if (!isValidTransferQuantity(quantity)) return ""
  if (referenceCostPrice === undefined || referenceCostPrice === null || referenceCostPrice === "") return ""
  const price = Number(referenceCostPrice)
  if (!Number.isFinite(price) || price < 0) return ""
  const amount = Math.round(((Number(quantity) * price) + Number.EPSILON) * 100) / 100
  const quantityNumber = Number(quantity)
  const quantityText = Number.isInteger(quantityNumber)
    ? String(quantityNumber)
    : quantityNumber.toFixed(2).replace(/0+$/, "").replace(/\.$/, "")
  const unitText = text(unit)
  return quantityText + (unitText ? " " + unitText : "") + " × ¥" + price.toFixed(2) + " = ¥" + amount.toFixed(2)
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

const UNIT_PATTERN = DEFAULT_UNITS
  .slice()
  .sort((a, b) => b.length - a.length)
  .map(escapeRegExp)
  .join("|")

const QUANTITY_LINE_PATTERN = new RegExp(
  "^(.+?)\\s*(?:[xX×*]\\s*)?(\\d+(?:\\.\\d+)?)\\s*(" + UNIT_PATTERN + ")\\s*$"
)

function cleanListLine(value) {
  return text(value)
    .replace(/^[\s]*[-–—•·]+\s*/, "")
    .replace(/^[\s]*\d{1,3}[.、)]\s*/, "")
}

function parseQuantityLine(value, lineNo) {
  const rawLine = text(value)
  const line = cleanListLine(rawLine)
  const match = line.match(QUANTITY_LINE_PATTERN)
  if (!match) return null
  const requestedName = text(match[1]).replace(/[xX×*]\s*$/, "").trim()
  const quantity = Number(match[2])
  if (!requestedName || !Number.isFinite(quantity) || quantity <= 0) return null
  return {
    lineNo,
    rawLine,
    requestedName,
    quantity,
    requestedUnit: text(match[3])
  }
}

function parseRecipientLine(line, recipient) {
  const source = text(line)
  if (!source) return false
  const isRecipientLine = /(?:收件人|联系人|联系电话|电话|手机)\s*[:：]?/.test(source)
  const isAddressLine = /(?:收货地址|收件地址|地址)\s*[:：]?/.test(source)
  if (!isRecipientLine && !isAddressLine) return false

  const nameMatch = source.match(/(?:收件人|联系人)\s*[:：]?\s*(.+?)(?=\s*(?:联系电话|电话|手机|收货地址|收件地址|地址)\s*[:：]?|$)/)
  if (nameMatch && text(nameMatch[1])) recipient.recipientName = text(nameMatch[1])

  const phoneMatch = source.match(/(?:联系电话|电话|手机)\s*[:：]?\s*([+\d][\d\s-]{5,24})/)
  if (phoneMatch) recipient.recipientPhone = text(phoneMatch[1]).replace(/\s+/g, "")

  const addressMatch = source.match(/(?:收货地址|收件地址|地址)\s*[:：]?\s*(.+)$/)
  if (addressMatch && text(addressMatch[1])) recipient.shippingAddress = text(addressMatch[1])
  return true
}

function parseTransferPaste(value) {
  const recipient = { recipientName: "", recipientPhone: "", shippingAddress: "" }
  const items = []
  const ignoredLines = []
  const lines = String(value || "").replace(/\r\n?/g, "\n").split("\n")

  lines.forEach((rawLine, index) => {
    const line = text(rawLine)
    if (!line) return
    if (parseRecipientLine(line, recipient)) return
    const item = parseQuantityLine(line, index + 1)
    if (item) items.push(item)
    else ignoredLines.push({ lineNo: index + 1, rawLine: line })
  })

  return { items, recipient, ignoredLines }
}

function candidateKey(candidate) {
  if (!candidate) return ""
  const itemType = text(candidate.itemType) || "product"
  const itemId = candidate.itemId !== undefined && candidate.itemId !== null
    ? candidate.itemId
    : candidate.productId
  return itemId === undefined || itemId === null || itemId === "" ? "" : itemType + ":" + itemId
}

function candidateNames(candidate) {
  const names = [
    candidate && candidate.itemName,
    candidate && candidate.productName,
    candidate && candidate.giftName,
    candidate && candidate.internalTeaName
  ].concat(candidate && Array.isArray(candidate.aliases) ? candidate.aliases : [])
  return Array.from(new Set(names.map(text).filter(Boolean)))
}

function levenshteinDistance(left, right) {
  if (left === right) return 0
  if (!left) return right.length
  if (!right) return left.length
  let previous = Array.from({ length: right.length + 1 }, (_, index) => index)
  for (let i = 1; i <= left.length; i += 1) {
    const current = [i]
    for (let j = 1; j <= right.length; j += 1) {
      current[j] = Math.min(
        current[j - 1] + 1,
        previous[j] + 1,
        previous[j - 1] + (left[i - 1] === right[j - 1] ? 0 : 1)
      )
    }
    previous = current
  }
  return previous[right.length]
}

function bigrams(value) {
  if (value.length < 2) return value ? [value] : []
  const result = []
  for (let index = 0; index < value.length - 1; index += 1) {
    result.push(value.slice(index, index + 2))
  }
  return result
}

function diceCoefficient(left, right) {
  const leftPairs = bigrams(left)
  const rightPairs = bigrams(right)
  if (!leftPairs.length || !rightPairs.length) return left === right ? 1 : 0
  const remaining = rightPairs.slice()
  let matches = 0
  leftPairs.forEach(pair => {
    const index = remaining.indexOf(pair)
    if (index >= 0) {
      matches += 1
      remaining.splice(index, 1)
    }
  })
  return (2 * matches) / (leftPairs.length + rightPairs.length)
}

function nameSimilarity(leftValue, rightValue) {
  const left = normalizeName(leftValue)
  const right = normalizeName(rightValue)
  if (!left || !right) return 0
  if (left === right) return 1
  const longest = Math.max(left.length, right.length)
  const editScore = 1 - (levenshteinDistance(left, right) / longest)
  const diceScore = diceCoefficient(left, right) * 0.96
  let containsScore = 0
  if (left.includes(right) || right.includes(left)) {
    containsScore = 0.84 + (0.12 * Math.min(left.length, right.length) / longest)
  }
  return Math.max(editScore, diceScore, containsScore)
}

function similarityThreshold(query) {
  const length = normalizeName(query).length
  if (length <= 1) return 0.95
  if (length === 2) return 0.48
  if (length === 3) return 0.44
  return 0.4
}

function bestCandidateScore(query, candidate) {
  return candidateNames(candidate).reduce((best, name) => Math.max(best, nameSimilarity(query, name)), 0)
}

function uniqueCandidates(candidates) {
  const seen = new Set()
  return (candidates || []).filter(candidate => {
    const key = candidateKey(candidate)
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function matchTransferPasteItems(items, candidates, options) {
  const opts = Object.assign({ maxCandidates: 8 }, options || {})
  const pool = uniqueCandidates(candidates)
  return (items || []).map(item => {
    const queryName = normalizeName(item.requestedName)
    const ranked = pool.map(candidate => ({
      candidate,
      key: candidateKey(candidate),
      score: bestCandidateScore(item.requestedName, candidate),
      exact: candidateNames(candidate).some(name => normalizeName(name) === queryName)
    })).sort((left, right) => {
      if (left.exact !== right.exact) return left.exact ? -1 : 1
      if (right.score !== left.score) return right.score - left.score
      return text(left.candidate.itemName).localeCompare(text(right.candidate.itemName), "zh-CN")
    })
    const exact = ranked.filter(row => row.exact)
    const similar = ranked.filter(row => row.exact || row.score >= similarityThreshold(item.requestedName))
      .slice(0, opts.maxCandidates)
    if (exact.length === 1) {
      return Object.assign({}, item, {
        matchState: "exact",
        selectedKey: "",
        included: false,
        candidates: similar.length ? similar : exact
      })
    }
    if (similar.length) {
      return Object.assign({}, item, {
        matchState: exact.length > 1 ? "duplicate" : "similar",
        selectedKey: "",
        included: false,
        candidates: similar
      })
    }
    return Object.assign({}, item, {
      matchState: "unmatched",
      selectedKey: "",
      included: false,
      candidates: []
    })
  })
}

function selectedCandidate(row) {
  if (!row || !row.selectedKey) return null
  const match = (row.candidates || []).find(candidate => candidate.key === row.selectedKey)
  return match ? match.candidate : null
}

module.exports = {
  DEFAULT_UNITS,
  adjustTransferQuantity,
  candidateKey,
  formatReferenceCostPrice,
  formatReferenceSubtotal,
  isValidTransferQuantity,
  matchTransferPasteItems,
  nameSimilarity,
  normalizeName,
  normalizeUnit,
  parseQuantityLine,
  parseTransferPaste,
  selectedCandidate
}
