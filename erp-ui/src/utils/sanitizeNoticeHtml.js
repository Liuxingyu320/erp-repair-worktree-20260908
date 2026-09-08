import DOMPurify from "dompurify"

const ALLOWED_TAGS = [
  "p", "br", "h1", "h2", "h3", "h4", "h5", "h6",
  "strong", "b", "em", "i", "u", "s",
  "ul", "ol", "li", "blockquote", "pre", "code", "a"
]

const ALLOWED_ATTR = ["href", "title", "rel"]
const ALLOWED_URI_REGEXP = /^(?:(?:https?|mailto):|[/?#])/i

export function escapeHtml(value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, character => {
    const replacements = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }
    return replacements[character]
  })
}

export function sanitizeNoticeHtml(value) {
  const source = String(value == null ? "" : value)
  if (!source) return ""

  try {
    if (!DOMPurify || DOMPurify.isSupported === false) {
      return escapeHtml(source)
    }
    return DOMPurify.sanitize(source, {
      ALLOWED_TAGS,
      ALLOWED_ATTR,
      ALLOWED_URI_REGEXP,
      ALLOW_DATA_ATTR: false,
      ALLOW_ARIA_ATTR: false
    })
  } catch (error) {
    return escapeHtml(source)
  }
}
