const assert = require("assert")

const {
  normalizeProtectedFileBlob,
  validateDocxBlob,
  validatePngBlob
} = require("../src/utils/protectedFileBlob")

const bytes = values => new Uint8Array(values)

async function run() {
  const validPdf = new Blob([
    "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n"
  ], { type: "application/octet-stream" })
  const normalizedPdf = await normalizeProtectedFileBlob(validPdf, "preview-pdf")
  assert.strictEqual(normalizedPdf.type, "application/pdf")

  await assert.rejects(
    normalizeProtectedFileBlob(
      new Blob(["<html><script>unsafe</script></html>"], { type: "application/pdf" }),
      "archive-pdf"
    ),
    /有效的 PDF/,
    "an HTML error response must never become a PDF object URL"
  )

  const validDocx = new Blob([
    bytes([0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00])
  ], { type: "application/octet-stream" })
  assert.strictEqual(await validateDocxBlob(validDocx), true)
  const normalizedDocx = await normalizeProtectedFileBlob(validDocx, "archive")
  assert.strictEqual(
    normalizedDocx.type,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  )
  assert.strictEqual(
    await validateDocxBlob(new Blob(["<!doctype html>"], { type: "application/octet-stream" })),
    false
  )

  const validPng = new Blob([
    bytes([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00])
  ], { type: "application/octet-stream" })
  assert.strictEqual(await validatePngBlob(validPng), true)
  const normalizedPng = await normalizeProtectedFileBlob(validPng, "signature")
  assert.strictEqual(normalizedPng.type, "image/png")

  await assert.rejects(
    normalizeProtectedFileBlob(validPdf, "unknown-kind"),
    /不支持/,
    "unknown protected file kinds must fail closed"
  )
}

run().then(() => {
  console.log("protectedFileBlob tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
