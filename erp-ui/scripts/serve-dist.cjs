const fs = require("fs")
const http = require("http")
const path = require("path")

const root = path.resolve(process.env.ERP_UI_DIST_ROOT || path.resolve(__dirname, "../dist"))
const port = Number(process.env.ERP_UI_PREVIEW_PORT || "19528")
const host = "127.0.0.1"
const mimeTypes = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".ico": "image/x-icon",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".woff": "font/woff",
  ".woff2": "font/woff2"
}

if (!Number.isInteger(port) || port < 1024 || port > 65535) {
  console.error("ERP_UI_PREVIEW_PORT must be an unprivileged TCP port")
  process.exit(1)
}
if (!fs.existsSync(path.resolve(root, "index.html"))) {
  console.error(`frontend dist is missing: ${root}`)
  process.exit(1)
}

function resolveRequestPath(requestUrl) {
  let pathname
  try {
    pathname = decodeURIComponent(new URL(requestUrl, `http://${host}:${port}`).pathname)
  } catch (error) {
    return null
  }
  const requested = path.resolve(root, `.${pathname}`)
  if (requested !== root && !requested.startsWith(root + path.sep)) {
    return null
  }
  return fs.existsSync(requested) && fs.statSync(requested).isFile()
    ? requested
    : path.resolve(root, "index.html")
}

const server = http.createServer((request, response) => {
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.writeHead(405, { Allow: "GET, HEAD" })
    response.end()
    return
  }
  const file = resolveRequestPath(request.url || "/")
  if (!file) {
    response.writeHead(400)
    response.end()
    return
  }
  response.writeHead(200, {
    "Cache-Control": "no-store",
    "Content-Type": mimeTypes[path.extname(file).toLowerCase()] || "application/octet-stream",
    "X-Content-Type-Options": "nosniff"
  })
  if (request.method === "HEAD") {
    response.end()
    return
  }
  fs.createReadStream(file).pipe(response)
})

server.listen(port, host, () => {
  console.log(`ERP_UI_DIST_READY http://${host}:${port}`)
})

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)))
}
