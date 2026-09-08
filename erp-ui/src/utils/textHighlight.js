export function splitHighlightText(value, keyword) {
  const text = String(value == null ? '' : value)
  const query = String(keyword == null ? '' : keyword)
  if (!query) return [{ text, highlighted: false }]

  const segments = []
  const haystack = text.toLowerCase()
  const needle = query.toLowerCase()
  let cursor = 0
  let match = haystack.indexOf(needle)

  while (match !== -1) {
    if (match > cursor) {
      segments.push({ text: text.slice(cursor, match), highlighted: false })
    }
    segments.push({
      text: text.slice(match, match + query.length),
      highlighted: true
    })
    cursor = match + query.length
    match = haystack.indexOf(needle, cursor)
  }

  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), highlighted: false })
  }
  return segments.length ? segments : [{ text, highlighted: false }]
}
