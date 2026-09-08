export function getFirstSelectableIndex(options) {
  return Array.isArray(options) && options.length > 0 ? 0 : -1
}
