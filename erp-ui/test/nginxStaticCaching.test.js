const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "../..")
const nginxConfigs = [
  "docker/nginx/conf/nginx.conf",
  "docker/nginx/conf/nginx.host.conf"
]

nginxConfigs.forEach(relativePath => {
  const source = fs.readFileSync(path.join(rootDir, relativePath), "utf8")

  assert.ok(
    source.includes("gzip_static on;") &&
      source.includes("gzip on;") &&
      source.includes("gzip_vary on;"),
    `${relativePath} should serve pre-compressed build assets when available`
  )

  assert.ok(
    source.includes("Cache-Control \"public, max-age=31536000, immutable\"") &&
      source.includes("expires 1y"),
    `${relativePath} should cache fingerprinted static assets for one year`
  )

  assert.ok(
    source.includes("location = /index.html") &&
      source.includes("Cache-Control \"no-cache, no-store, must-revalidate\"") &&
      source.includes("location / {") &&
      source.includes("Cache-Control \"no-cache\""),
    `${relativePath} should avoid long caching the SPA entry document`
  )
})

console.log("nginxStaticCaching tests passed")
