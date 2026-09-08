import DOMPurify from "dompurify"

function escapeHtml(value) {
  return String(value == null ? "" : value).replace(/[&<>"']/g, character => {
    return {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }[character]
  })
}

export function sanitizeHighlightedCode(value) {
  const source = String(value == null ? "" : value)
  try {
    if (!DOMPurify || DOMPurify.isSupported === false || typeof DOMPurify.sanitize !== "function") {
      return escapeHtml(source)
    }
    return DOMPurify.sanitize(source, {
      ALLOWED_TAGS: ["span"],
      ALLOWED_ATTR: ["class"],
      ALLOW_DATA_ATTR: false,
      ALLOW_ARIA_ATTR: false
    })
  } catch (_) {
    return escapeHtml(source)
  }
}
