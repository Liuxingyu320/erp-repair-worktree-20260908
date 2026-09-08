const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  FileResponseError,
  normalizeFileResponse,
  normalizeTransportFailure
} = require("../src/utils/fileResponse")

const root = path.resolve(__dirname, "..")
const requestSource = fs.readFileSync(path.join(root, "src/utils/request.js"), "utf8")

function byteBlob(bytes, type) {
  return new Blob([Uint8Array.from(bytes)], { type })
}

async function expectFileError(value, metadata, expected) {
  await assert.rejects(
    normalizeFileResponse(value, metadata),
    error => {
      assert.ok(error instanceof FileResponseError)
      assert.strictEqual(error.code, expected.code)
      if (expected.businessCode !== undefined) {
        assert.strictEqual(error.businessCode, expected.businessCode)
      }
      if (expected.message) {
        assert.match(error.message, expected.message)
      }
      return true
    }
  )
}

async function run() {
  await expectFileError(
    new Blob([JSON.stringify({
      code: 401,
      msg: "会话已过期",
      businessCode: "SESSION_EXPIRED"
    })], { type: "application/json;charset=utf-8" }),
    { status: 200 },
    { code: 401, businessCode: "SESSION_EXPIRED", message: /会话已过期/ }
  )

  for (const status of [403, 404, 500]) {
    await expectFileError(
      new Blob([JSON.stringify({
        code: status,
        msg: `business-${status}`,
        businessCode: `BUSINESS_${status}`
      })], { type: "text/plain" }),
      { status: 200 },
      { code: status, businessCode: `BUSINESS_${status}`, message: new RegExp(`business-${status}`) }
    )
  }

  const arrayBufferPayload = new TextEncoder().encode(JSON.stringify({
    code: 500,
    msg: "arraybuffer business failure"
  })).buffer
  await expectFileError(
    arrayBufferPayload,
    { status: 200, headers: { "content-type": "application/octet-stream" } },
    { code: 500, message: /arraybuffer business failure/ }
  )

  await expectFileError(
    new Blob(["<!doctype html><html><body>gateway error</body></html>"], {
      type: "text/html"
    }),
    { status: 200 },
    { code: "FILE_RESPONSE_INVALID", message: /网页错误/ }
  )

  await expectFileError(
    new Blob(["plain upstream failure"], { type: "text/plain" }),
    { status: 503 },
    { code: 503, message: /plain upstream failure/ }
  )

  const validPdf = new Blob(["%PDF-1.7\n%%EOF"], { type: "application/pdf" })
  assert.strictEqual(await normalizeFileResponse(validPdf, { status: 200 }), validPdf)

  const validPng = byteBlob(
    [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00],
    "image/png"
  )
  assert.strictEqual(await normalizeFileResponse(validPng, { status: 200 }), validPng)

  const validZip = byteBlob(
    [0x50, 0x4b, 0x03, 0x04, 0x14, 0x00],
    "application/zip"
  )
  assert.strictEqual(await normalizeFileResponse(validZip, { status: 200 }), validZip)

  const validOffice = byteBlob(
    [0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1, 0x00],
    "application/vnd.ms-excel"
  )
  assert.strictEqual(await normalizeFileResponse(validOffice, { status: 200 }), validOffice)

  const validText = new Blob(["legitimate drive note"], { type: "text/plain" })
  assert.strictEqual(
    await normalizeFileResponse(validText, { status: 200 }),
    validText,
    "legitimate text files must not be mistaken for gateway failures"
  )

  await expectFileError(
    validPdf,
    { status: 500 },
    { code: 500, message: /服务器内部错误/ }
  )

  assert.deepStrictEqual(
    normalizeTransportFailure(new Error("Network Error")),
    { code: "NETWORK_ERROR", message: "后端接口连接异常", httpStatus: 0 }
  )
  assert.deepStrictEqual(
    normalizeTransportFailure(Object.assign(new Error("timeout of 10000ms exceeded"), {
      code: "ECONNABORTED"
    })),
    { code: "TIMEOUT", message: "系统接口请求超时", httpStatus: 0 }
  )
  assert.deepStrictEqual(
    normalizeTransportFailure({
      message: "Request failed with status code 404",
      response: { status: 404 }
    }),
    { code: 404, message: "访问资源不存在", httpStatus: 404 }
  )

  assert.ok(
    requestSource.includes("normalizeFileResponse(res.data") &&
      requestSource.includes("normalizeFileResponse(error.response.data"),
    "both HTTP 2xx business blobs and HTTP error blobs must use the shared parser"
  )
  assert.ok(
    /code === 401[\s\S]*?handleExpiredSession\(\)/.test(requestSource) &&
      requestSource.includes("let sessionExpiryPromise = null"),
    "file business 401 must reuse the existing single-flight expiry flow"
  )
  assert.ok(
    !requestSource.includes("res.request.responseType ===  'blob'"),
    "binary responses must not bypass business-code handling"
  )

  console.log("file response stability tests passed")
}

run().catch(error => {
  console.error(error)
  process.exitCode = 1
})
