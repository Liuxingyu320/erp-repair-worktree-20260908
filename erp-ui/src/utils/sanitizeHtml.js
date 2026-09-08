import DOMPurify from 'dompurify'

const NOTICE_TAGS = [
  'p', 'br', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'b', 'strong', 'i', 'em', 'u', 's', 'strike', 'blockquote',
  'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
  'a', 'img', 'span'
]

const NOTICE_ATTRIBUTES = [
  'href', 'title', 'src', 'alt', 'width', 'height', 'colspan', 'rowspan'
]

export function sanitizeRichText(html) {
  return DOMPurify.sanitize(String(html == null ? '' : html), {
    ALLOWED_TAGS: NOTICE_TAGS,
    ALLOWED_ATTR: NOTICE_ATTRIBUTES
  })
}
