const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")

const {
  findFrontendSecretViolations
} = require("../scripts/scan-frontend-secrets.cjs")

const fixtureRoot = fs.mkdtempSync(path.join(os.tmpdir(), "frontend-secret-scan-"))

try {
  fs.mkdirSync(path.join(fixtureRoot, "nested"))
  fs.writeFileSync(path.join(fixtureRoot, "safe.js"), "export const publicLabel = 'safe'\n")
  fs.writeFileSync(
    path.join(fixtureRoot, "private-key.js"),
    "const value = '-----BEGIN PRIVATE KEY-----\\n[redacted]'\n"
  )
  fs.writeFileSync(
    path.join(fixtureRoot, "nested", "crypto.js"),
    "client.setPrivateKey('[redacted]')\n"
  )
  fs.writeFileSync(
    path.join(fixtureRoot, ".env.production"),
    "VUE_APP_API_TOKEN=[redacted-sensitive-value]\n"
  )

  const violations = findFrontendSecretViolations(fixtureRoot)

  assert.deepStrictEqual(
    violations.map(item => [item.path, item.ruleId]),
    [
      [".env.production", "FRONTEND_PUBLIC_ENV_SECRET_NAME"],
      ["nested/crypto.js", "FRONTEND_PRIVATE_KEY_API"],
      ["private-key.js", "FRONTEND_PRIVATE_KEY_PEM"]
    ],
    "scanner should report stable relative paths and rule identifiers"
  )
  assert.ok(
    violations.every(item => Object.keys(item).sort().join(",") === "path,ruleId"),
    "scanner results must not expose matched secret content"
  )
} finally {
  fs.rmSync(fixtureRoot, { recursive: true, force: true })
}

const realUiRoot = path.resolve(__dirname, "..")
assert.deepStrictEqual(
  findFrontendSecretViolations(realUiRoot, [
    "src",
    "public",
    ".env.development",
    ".env.production",
    ".env.staging",
    "babel.config.js",
    "build",
    "capacitor.config.ts",
    "vue.config.js"
  ]),
  [],
  "the committed frontend source and release configuration must not contain client-side credentials"
)

console.log("frontendSecretScanner tests passed")
