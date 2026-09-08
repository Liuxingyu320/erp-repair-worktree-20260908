const MODE_PLACED = "PLACED"
const MODE_LAST_PAGE = "LAST_PAGE"
const MODE_APPENDED_CONFIRMATION_PAGE = "APPENDED_CONFIRMATION_PAGE"

const APPENDED_FIELDS = Object.freeze(["mode"])
const PLACED_FIELDS = Object.freeze([
  "mode", "pageNumber", "x", "y", "width", "height"
])
const LAST_PAGE_FIELDS = Object.freeze([
  "mode", "x", "y", "width", "height"
])

function parsePlacement(value) {
  if (!value || typeof value !== "string") return null
  try {
    const placement = JSON.parse(value)
    return placement && typeof placement === "object" && !Array.isArray(placement)
      ? placement : null
  } catch (error) {
    return null
  }
}

function hasExactFields(placement, fields) {
  const configuredFields = Object.keys(placement)
  return configuredFields.length === fields.length &&
    configuredFields.every(field => fields.includes(field))
}

function finiteNumber(value) {
  if (typeof value !== "number" &&
    !(typeof value === "string" && value.trim() !== "")) return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function normalizeSignImagePlacementJson(value) {
  const placement = parsePlacement(value)
  if (!placement) return null

  const isAppended = placement.mode === MODE_APPENDED_CONFIRMATION_PAGE
  const isPlaced = placement.mode === MODE_PLACED
  const isLastPage = placement.mode === MODE_LAST_PAGE
  const fields = isAppended ? APPENDED_FIELDS
    : isPlaced ? PLACED_FIELDS : isLastPage ? LAST_PAGE_FIELDS : null
  if (!fields || !hasExactFields(placement, fields)) return null
  if (isAppended) {
    return JSON.stringify({ mode: MODE_APPENDED_CONFIRMATION_PAGE })
  }
  if (isPlaced && (!Number.isInteger(placement.pageNumber) || placement.pageNumber <= 0)) {
    return null
  }

  const x = finiteNumber(placement.x)
  const y = finiteNumber(placement.y)
  const width = finiteNumber(placement.width)
  const height = finiteNumber(placement.height)
  if (x === null || y === null || width === null || height === null ||
    x < 0 || y < 0 || width <= 0 || height <= 0) return null

  if (isLastPage) {
    return JSON.stringify({ mode: MODE_LAST_PAGE, x, y, width, height })
  }
  return JSON.stringify({
    mode: MODE_PLACED,
    pageNumber: placement.pageNumber,
    x,
    y,
    width,
    height
  })
}

function isValidSignImagePlacementJson(value) {
  return normalizeSignImagePlacementJson(value) !== null
}

module.exports = {
  MODE_APPENDED_CONFIRMATION_PAGE,
  MODE_LAST_PAGE,
  MODE_PLACED,
  isValidSignImagePlacementJson,
  normalizeSignImagePlacementJson
}
