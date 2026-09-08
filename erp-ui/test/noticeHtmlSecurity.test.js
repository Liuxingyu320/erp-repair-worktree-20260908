const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const sanitizerPath = path.join(uiRoot, "src/utils/sanitizeNoticeHtml.js")
const detailViewSource = fs.readFileSync(
  path.join(uiRoot, "src/layout/components/HeaderNotice/DetailView.vue"),
  "utf8"
)
const packageJson = JSON.parse(fs.readFileSync(path.join(uiRoot, "package.json"), "utf8"))

assert.strictEqual(
  packageJson.dependencies && packageJson.dependencies.dompurify,
  "3.4.13",
  "notice HTML should use the reviewed release patched for GHSA-55q2-fjhq-7xh7"
)
assert.ok(fs.existsSync(sanitizerPath), "the notice HTML sanitizer wrapper should exist")

const sanitizerSource = fs.readFileSync(sanitizerPath, "utf8")

assert.ok(
  sanitizerSource.includes("DOMPurify.sanitize") &&
    sanitizerSource.includes("ALLOWED_TAGS") &&
    sanitizerSource.includes("ALLOWED_ATTR") &&
    sanitizerSource.includes("escapeHtml"),
  "the wrapper should use an explicit DOMPurify allowlist and escaped-text fallback"
)
assert.ok(
  detailViewSource.includes('import { sanitizeNoticeHtml } from "@/utils/sanitizeNoticeHtml"') &&
    detailViewSource.includes("sanitizedNoticeContent()") &&
    detailViewSource.includes('v-html="sanitizedNoticeContent"') &&
    !detailViewSource.includes('v-html="detail.noticeContent"'),
  "the notice detail sink should render only computed sanitized HTML"
)

console.log("noticeHtmlSecurity tests passed")
