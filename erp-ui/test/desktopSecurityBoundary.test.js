const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const uiRoot = path.resolve(__dirname, '..')

function readUi(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), 'utf8')
}

function assertIncludes(source, needle, message) {
  assert(source.includes(needle), `${message}\nExpected source to include: ${needle}`)
}

function assertNotIncludes(source, needle, message) {
  assert(!source.includes(needle), `${message}\nSource must not include: ${needle}`)
}

function loadImportResultFormatter() {
  const source = readUi('src/utils/importResult.js')
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(/\bexport\s+function\s+/g, 'function ') +
      '\nmodule.exports = { formatImportResult }',
    sandbox,
    { filename: 'importResult.js' }
  )
  return sandbox.module.exports.formatImportResult
}

function loadTextHighlighter() {
  const source = readUi('src/utils/textHighlight.js')
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(/\bexport\s+function\s+splitHighlightText/, 'function splitHighlightText') +
      '\nmodule.exports = { splitHighlightText }',
    sandbox,
    { filename: 'textHighlight.js' }
  )
  return sandbox.module.exports.splitHighlightText
}

function normalize(value) {
  return JSON.parse(JSON.stringify(value))
}

const noticeSource = readUi('src/layout/components/HeaderNotice/DetailView.vue')

assert.ok(
  noticeSource.includes("import { sanitizeRichText } from '@/utils/sanitizeHtml'") ||
    noticeSource.includes('import { sanitizeNoticeHtml } from "@/utils/sanitizeNoticeHtml"'),
  'Notice details should use an allowlisted rich-text sanitizer.'
)
assertIncludes(
  noticeSource,
  'v-html="sanitizedNoticeContent"',
  'Notice details should render only the computed sanitized value.'
)
assertNotIncludes(
  noticeSource,
  'v-html="detail.noticeContent"',
  'Notice details must never render raw API content as HTML.'
)

const importSource = readUi('src/components/ExcelImportDialog/index.vue')

assertIncludes(
  importSource,
  "import { failureCsv, parseLegacyImportResult, safeCsvCell } from '@/utils/importResult'",
  'Excel import results should use the shared plain-text formatter.'
)
assertIncludes(
  importSource,
  'parseLegacyImportResult(',
  'Excel import responses should be normalized before display.'
)
assertNotIncludes(
  importSource,
  'v-html=',
  'Excel import result dialogs should render interpolated text rather than HTML.'
)
assertNotIncludes(
  importSource,
  'dangerouslyUseHTMLString: true',
  'Excel import results must never opt into HTML string rendering.'
)

const formatImportResult = loadImportResultFormatter()

assert.strictEqual(formatImportResult(null), '导入完成')
assert.strictEqual(
  formatImportResult({ msg: '成功 1 条<br/>失败：<img src=x onerror=alert(1)>' }),
  '成功 1 条\n失败：<img src=x onerror=alert(1)>',
  'Legacy line breaks should become readable plain text while hostile-looking values remain literal text.'
)
assert.strictEqual(
  formatImportResult({
    data: {
      successCount: 3,
      failureCount: 1,
      errors: [{ row: 2, message: '商品编码重复' }]
    }
  }),
  '导入完成：成功 3 条，失败 1 条\n第 2 行：商品编码重复',
  'Structured import results should produce a concise readable summary.'
)

const searchSource = readUi('src/components/HeaderSearch/index.vue')

assertIncludes(
  searchSource,
  "import { splitHighlightText } from '@/utils/textHighlight'",
  'Header search should use the text-segmentation highlighter.'
)
assertIncludes(
  searchSource,
  'highlightSegments(item.path)',
  'Header search should render highlighted text segments.'
)
assertNotIncludes(
  searchSource,
  'v-html=',
  'Header search must not render menu metadata through v-html.'
)

const splitHighlightText = loadTextHighlighter()

assert.deepStrictEqual(
  normalize(splitHighlightText('Purchase / purchaseReturn', 'PURCHASE')),
  [
    { text: 'Purchase', highlighted: true },
    { text: ' / ', highlighted: false },
    { text: 'purchase', highlighted: true },
    { text: 'Return', highlighted: false }
  ],
  'Highlighting should be case-insensitive and support repeated matches.'
)
assert.deepStrictEqual(
  normalize(splitHighlightText('a.+b.+c', '.+')),
  [
    { text: 'a', highlighted: false },
    { text: '.+', highlighted: true },
    { text: 'b', highlighted: false },
    { text: '.+', highlighted: true },
    { text: 'c', highlighted: false }
  ],
  'Regex punctuation should be treated as literal text.'
)
assert.deepStrictEqual(
  normalize(splitHighlightText('<img>X</img>', 'img')),
  [
    { text: '<', highlighted: false },
    { text: 'img', highlighted: true },
    { text: '>X</', highlighted: false },
    { text: 'img', highlighted: true },
    { text: '>', highlighted: false }
  ],
  'Hostile-looking menu metadata should remain inert text segments.'
)
assert.deepStrictEqual(
  normalize(splitHighlightText('库存管理', '')),
  [{ text: '库存管理', highlighted: false }],
  'An empty keyword should return one plain segment.'
)

console.log('desktopSecurityBoundary assertions passed')
