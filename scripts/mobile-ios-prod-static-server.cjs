#!/usr/bin/env node

const fs = require("fs")
const http = require("http")
const path = require("path")
const zlib = require("zlib")

const ROOT = path.resolve(__dirname, "..")
const DIST = path.resolve(process.env.DIST || path.join(ROOT, "erp-ui/dist"))
const PORT = Number(process.env.PORT || 19025)
const API_ORIGIN = new URL(process.env.API_ORIGIN || "http://127.0.0.1:8080")
const PRODUCTION_API_PREFIX = "/prod-api"
const NON_PRODUCTION_API_PREFIXES = ["/stage-api", "/dev-api"]

let distValidationCache = {
  directory: "",
  fingerprint: "",
  result: null
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".map": "application/json; charset=utf-8",
  ".txt": "text/plain; charset=utf-8"
}

function apiPathMatches(urlPath, prefix) {
  const pathname = String(urlPath || "").split("?")[0]
  return pathname === prefix || pathname.startsWith(prefix + "/")
}

function isNonProductionApiPath(urlPath) {
  return NON_PRODUCTION_API_PREFIXES.some(prefix => apiPathMatches(urlPath, prefix))
}

function resolveDistAsset(distDirectory, assetUrl) {
  const assetPath = String(assetUrl || "").split(/[?#]/)[0]
  if (!assetPath || /^(?:https?:)?\/\//i.test(assetPath)) {
    return null
  }
  let decoded
  try {
    decoded = decodeURIComponent(assetPath)
  } catch (_) {
    return null
  }
  const resolved = path.resolve(distDirectory, decoded.replace(/^\/+/, ""))
  const prefix = distDirectory.endsWith(path.sep) ? distDirectory : distDirectory + path.sep
  return resolved.startsWith(prefix) ? resolved : null
}

function validateProductionDist(distDirectory = DIST) {
  const resolvedDist = path.resolve(distDirectory)
  const indexFile = path.join(resolvedDist, "index.html")
  const releaseFile = path.join(resolvedDist, "release-info.json")
  if (!fs.existsSync(indexFile)) {
    throw new Error(`missing production index: ${indexFile}`)
  }
  if (!fs.existsSync(releaseFile)) {
    throw new Error("missing release-info.json; run npm run build:prod before starting the LAN server")
  }

  let releaseInfo
  try {
    releaseInfo = JSON.parse(fs.readFileSync(releaseFile, "utf8"))
  } catch (error) {
    throw new Error(`invalid release-info.json: ${error.message}`)
  }
  if (!releaseInfo || typeof releaseInfo.commit !== "string" || !releaseInfo.commit.trim() ||
    typeof releaseInfo.buildTime !== "string" || !releaseInfo.buildTime.trim()) {
    throw new Error("release-info.json is missing commit or buildTime")
  }

  const indexHtml = fs.readFileSync(indexFile, "utf8")
  const scriptUrls = Array.from(
    indexHtml.matchAll(/<script\b[^>]*\bsrc\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>]+))/gi),
    match => match[1] || match[2] || match[3]
  )
    .filter(scriptUrl => String(scriptUrl).split(/[?#]/)[0].endsWith(".js"))
  if (!scriptUrls.length) {
    throw new Error("production index does not reference any JavaScript bundle")
  }

  const scriptFiles = scriptUrls.map(scriptUrl => resolveDistAsset(resolvedDist, scriptUrl))
  if (scriptFiles.some(scriptFile => !scriptFile || !fs.existsSync(scriptFile))) {
    throw new Error("production index references a missing or unsafe JavaScript bundle")
  }
  const bundleSource = scriptFiles.map(scriptFile => fs.readFileSync(scriptFile, "utf8")).join("\n")
  const forbiddenPrefix = NON_PRODUCTION_API_PREFIXES.find(prefix => bundleSource.includes(prefix))
  if (forbiddenPrefix) {
    throw new Error(`non-production API prefix detected in dist: ${forbiddenPrefix}`)
  }
  if (!bundleSource.includes(PRODUCTION_API_PREFIX)) {
    throw new Error(`production API prefix missing from dist: ${PRODUCTION_API_PREFIX}`)
  }

  return { releaseInfo, scriptFiles }
}

function distFingerprint(distDirectory) {
  return ["index.html", "release-info.json"].map(filename => {
    try {
      const stat = fs.statSync(path.join(distDirectory, filename))
      return `${filename}:${stat.mtimeMs}:${stat.size}`
    } catch (_) {
      return `${filename}:missing`
    }
  }).join("|")
}

function getProductionDistState(distDirectory = DIST) {
  const resolvedDist = path.resolve(distDirectory)
  const fingerprint = distFingerprint(resolvedDist)
  if (distValidationCache.directory === resolvedDist &&
    distValidationCache.fingerprint === fingerprint && distValidationCache.result) {
    return distValidationCache.result
  }
  let result
  try {
    result = { ok: true, validation: validateProductionDist(resolvedDist) }
  } catch (error) {
    result = { ok: false, error }
  }
  distValidationCache = { directory: resolvedDist, fingerprint, result }
  return result
}

function send(res, status, headers, body) {
  res.writeHead(status, headers)
  res.end(body)
}

function safeStaticPath(urlPath) {
  let decoded
  try {
    decoded = decodeURIComponent(urlPath.split("?")[0])
  } catch (_) {
    decoded = "/"
  }
  const normalized = path.normalize(decoded).replace(/^(\.\.[/\\])+/, "")
  const file = path.join(DIST, normalized)
  return file.startsWith(DIST) ? file : path.join(DIST, "index.html")
}

function serveStatic(req, res) {
  let file = safeStaticPath(req.url || "/")
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) {
    file = path.join(file, "index.html")
  }
  if (!fs.existsSync(file)) {
    file = path.join(DIST, "index.html")
  }
  const ext = path.extname(file)
  const acceptEncoding = req.headers["accept-encoding"] || ""
  const gzFile = file + ".gz"
  const headers = {
    "Content-Type": MIME[ext] || "application/octet-stream",
    "Cache-Control": ext === ".html" ? "no-store" : "public, max-age=31536000, immutable"
  }
  if (acceptEncoding.includes("gzip") && fs.existsSync(gzFile)) {
    headers["Content-Encoding"] = "gzip"
    send(res, 200, headers, fs.readFileSync(gzFile))
    return
  }
  let body = fs.readFileSync(file)
  if (acceptEncoding.includes("gzip") && body.length > 1024 && ext !== ".png" && ext !== ".jpg" && ext !== ".jpeg") {
    body = zlib.gzipSync(body)
    headers["Content-Encoding"] = "gzip"
  }
  send(res, 200, headers, body)
}

function proxyApi(req, res) {
  const upstreamPath = (req.url || "/").replace(/^\/prod-api/, "") || "/"
  const options = {
    protocol: API_ORIGIN.protocol,
    hostname: API_ORIGIN.hostname,
    port: API_ORIGIN.port,
    method: req.method,
    path: upstreamPath,
    headers: Object.assign({}, req.headers, { host: API_ORIGIN.host })
  }
  const proxy = http.request(options, upstream => {
    const headers = Object.assign({}, upstream.headers)
    delete headers["transfer-encoding"]
    res.writeHead(upstream.statusCode || 502, headers)
    upstream.pipe(res)
  })
  proxy.on("error", error => {
    send(res, 502, { "Content-Type": "application/json; charset=utf-8" }, JSON.stringify({ code: 502, msg: error.message }))
  })
  req.pipe(proxy)
}

function createServer() {
  return http.createServer((req, res) => {
    if (isNonProductionApiPath(req.url)) {
      send(res, 503, { "Content-Type": "application/json; charset=utf-8" }, JSON.stringify({
        code: 503,
        msg: "Non-production frontend bundle detected; rebuild with npm run build:prod"
      }))
      return
    }
    if (apiPathMatches(req.url, PRODUCTION_API_PREFIX)) {
      proxyApi(req, res)
      return
    }
    if (req.method !== "GET" && req.method !== "HEAD") {
      send(res, 405, { "Content-Type": "text/plain; charset=utf-8" }, "Method Not Allowed")
      return
    }
    const distState = getProductionDistState(DIST)
    if (!distState.ok) {
      send(res, 503, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }, JSON.stringify({
        code: 503,
        msg: distState.error.message
      }))
      return
    }
    serveStatic(req, res)
  })
}

if (require.main === module) {
  try {
    const validation = validateProductionDist(DIST)
    const server = createServer()
    server.listen(PORT, "0.0.0.0", () => {
      console.log(`ERP production static server listening on http://127.0.0.1:${PORT}`)
      console.log(`Proxy /prod-api -> ${API_ORIGIN.origin}`)
      console.log(`Release ${validation.releaseInfo.commit} built ${validation.releaseInfo.buildTime}`)
    })
  } catch (error) {
    console.error(`ERP production static server refused to start: ${error.message}`)
    process.exitCode = 1
  }
}

module.exports = {
  apiPathMatches,
  createServer,
  getProductionDistState,
  isNonProductionApiPath,
  validateProductionDist
}
