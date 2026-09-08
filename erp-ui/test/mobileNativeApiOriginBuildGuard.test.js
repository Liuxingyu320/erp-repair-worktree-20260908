const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")
const { spawnSync } = require("child_process")

const root = path.resolve(__dirname, "..")
const scriptPath = path.resolve(root, "scripts/validate-native-api-origin.cjs")
const packageJson = require("../package.json")
const {
  ERROR_CODE,
  ERROR_MESSAGE,
  SUCCESS_MESSAGE,
  parseEnvValue,
  loadVueCliProductionEnv,
  resolveNativeApiOrigin,
  validateNativeApiOrigin,
  runCli
} = require("../scripts/validate-native-api-origin.cjs")

for (const value of [
  undefined,
  null,
  "",
  "   ",
  "http://api.example.com",
  "/api",
  "api.example.com",
  "https://user@example.com",
  "https://@example.com",
  "https://user:secret@example.com",
  "https://api.example.com/.",
  "https://api.example.com/v1",
  "https://api.example.com?",
  "https://api.example.com?token=secret",
  "https://api.example.com#",
  "https://api.example.com#fragment",
  "https://api.example.com\\evil",
  "https://api.example.com\\",
  "https://api.example.com\t",
  "https://api.example.com\r\n",
  "https://api.example.com\n",
  "https://api.example.com/\ttab",
  "https://api.example.com/\rreturn",
  "https://api.example.com/\nline",
  "HTTPS://api.example.com",
  "https://API.example.com",
  "https://api.example.com:443"
]) {
  const result = validateNativeApiOrigin(value)
  assert.deepStrictEqual(result, {
    ok: false,
    code: ERROR_CODE,
    message: ERROR_MESSAGE
  }, `invalid native API origin must fail closed: ${typeof value}`)
}

assert.deepStrictEqual(validateNativeApiOrigin("https://api.example.com"), {
  ok: true,
  origin: "https://api.example.com"
})
assert.deepStrictEqual(validateNativeApiOrigin("  https://api.example.com:8443/  "), {
  ok: true,
  origin: "https://api.example.com:8443"
}, "a trailing slash may be normalized to the HTTPS origin")

assert.strictEqual(parseEnvValue([
  "# production",
  "VUE_APP_NATIVE_API_ORIGIN = 'https://quoted.example.com/'",
  "UNRELATED=value"
].join("\n")), "https://quoted.example.com/")
assert.strictEqual(parseEnvValue(
  "VUE_APP_NATIVE_API_ORIGIN=https://comment.example.com/ # deployment origin"
), "https://comment.example.com/ # deployment origin",
"dotenv 8.6 must preserve unquoted inline comments exactly as Vue CLI does")
assert.strictEqual(parseEnvValue("VUE_APP_OTHER=value"), undefined)
assert.strictEqual(parseEnvValue([
  "VUE_APP_NATIVE_API_ORIGIN=https://first.example.com",
  "VUE_APP_NATIVE_API_ORIGIN=https://last.example.com"
].join("\n")), "https://last.example.com",
"dotenv 8.6 must use the last duplicate key in one file")
assert.strictEqual(
  validateNativeApiOrigin(parseEnvValue(
    'VUE_APP_NATIVE_API_ORIGIN="https://api.example.com\\n"'
  )).ok,
  false,
  "dotenv-expanded boundary control characters must fail before whitespace normalization"
)

function envReader(files, reads) {
  return filePath => {
    const name = path.basename(filePath)
    reads.push(name)
    if (Object.prototype.hasOwnProperty.call(files, name)) return files[name]
    const error = new Error("missing")
    error.code = "ENOENT"
    throw error
  }
}

const processInput = { VUE_APP_NATIVE_API_ORIGIN: "https://process.example.com" }
const processSnapshot = Object.assign({}, processInput)
const processReads = []
assert.strictEqual(resolveNativeApiOrigin({
  env: processInput,
  context: "/isolated",
  readFileSync: envReader({
    ".env.production.local": "VUE_APP_NATIVE_API_ORIGIN=https://local.example.com",
    ".env.production": "VUE_APP_NATIVE_API_ORIGIN=https://production.example.com"
  }, processReads)
}), "https://process.example.com")
assert.deepStrictEqual(processInput, processSnapshot,
"loading Vue CLI production env must not mutate the caller's env object")
assert.deepStrictEqual(processReads, [
  ".env.production.local", ".env.production", ".env.local", ".env"
])

const emptyReads = []
assert.strictEqual(resolveNativeApiOrigin({
  env: { VUE_APP_NATIVE_API_ORIGIN: "" },
  context: "/isolated",
  readFileSync: envReader({
    ".env.production": "VUE_APP_NATIVE_API_ORIGIN=https://file.example.com"
  }, emptyReads)
}), "", "an explicitly empty process value must fail instead of silently falling back")

const priorityReads = []
assert.strictEqual(resolveNativeApiOrigin({
  env: {},
  context: "/isolated",
  readFileSync: envReader({
    ".env.production.local": [
      "API_HOST=local.example.com",
      "VUE_APP_NATIVE_API_ORIGIN=https://${API_HOST}/"
    ].join("\n"),
    ".env.production": "VUE_APP_NATIVE_API_ORIGIN=https://production.example.com",
    ".env.local": "VUE_APP_NATIVE_API_ORIGIN=https://base-local.example.com",
    ".env": "VUE_APP_NATIVE_API_ORIGIN=https://base.example.com"
  }, priorityReads)
}), "https://local.example.com/",
".env.production.local and dotenv-expand must match Vue CLI production build precedence")

const loadedDuplicateEnv = loadVueCliProductionEnv({
  env: {},
  context: "/isolated",
  readFileSync: envReader({
    ".env.production": [
      "VUE_APP_NATIVE_API_ORIGIN=https://first.example.com",
      "VUE_APP_NATIVE_API_ORIGIN=https://last.example.com"
    ].join("\n"),
    ".env": "VUE_APP_NATIVE_API_ORIGIN=https://lower.example.com"
  }, [])
})
assert.strictEqual(loadedDuplicateEnv.VUE_APP_NATIVE_API_ORIGIN, "https://last.example.com")
assert.strictEqual(validateNativeApiOrigin(parseEnvValue(
  "VUE_APP_NATIVE_API_ORIGIN=https://api.example.com # comment"
)).ok, false, "dotenv inline-comment text must reach validation instead of being hand-stripped")

const tempEnvDir = fs.mkdtempSync(path.join(os.tmpdir(), "native-api-origin-"))
try {
  fs.writeFileSync(path.join(tempEnvDir, ".env.production"), [
    "API_HOST=production.example.com",
    "VUE_APP_NATIVE_API_ORIGIN=https://${API_HOST}"
  ].join("\n"))
  fs.writeFileSync(path.join(tempEnvDir, ".env.production.local"), [
    "API_HOST=production-local.example.com",
    "VUE_APP_NATIVE_API_ORIGIN=https://${API_HOST}/"
  ].join("\n"))
  fs.writeFileSync(path.join(tempEnvDir, ".env"), "VUE_APP_NATIVE_API_ORIGIN=https://base.example.com")
  assert.strictEqual(resolveNativeApiOrigin({
    env: {},
    context: tempEnvDir
  }), "https://production-local.example.com/")
} finally {
  fs.rmSync(tempEnvDir, { recursive: true, force: true })
}

const captured = { logs: [], errors: [] }
const missingExitCode = runCli({
  env: {},
  readFileSync() { throw new Error("missing") },
  console: {
    log(message) { captured.logs.push(message) },
    error(message) { captured.errors.push(message) }
  }
})
assert.strictEqual(missingExitCode, 1)
assert.deepStrictEqual(captured.logs, [])
assert.deepStrictEqual(captured.errors, [ERROR_MESSAGE])

const sensitiveValue = "https://private-user:private-password@example.com/internal?token=private-token"
const invalidCli = spawnSync(process.execPath, [scriptPath], {
  cwd: root,
  env: Object.assign({}, process.env, { VUE_APP_NATIVE_API_ORIGIN: sensitiveValue }),
  encoding: "utf8"
})
assert.strictEqual(invalidCli.status, 1, "invalid CLI input must exit nonzero")
assert.strictEqual(invalidCli.stdout, "")
assert.strictEqual(invalidCli.stderr.trim(), ERROR_MESSAGE)
for (const secret of ["private-user", "private-password", "private-token", sensitiveValue]) {
  assert.ok(!`${invalidCli.stdout}${invalidCli.stderr}`.includes(secret),
    "CLI diagnostics must never echo the rejected origin or embedded secrets")
}

const validCli = spawnSync(process.execPath, [scriptPath], {
  cwd: root,
  env: Object.assign({}, process.env, { VUE_APP_NATIVE_API_ORIGIN: "https://api.example.com/" }),
  encoding: "utf8"
})
assert.strictEqual(validCli.status, 0)
assert.strictEqual(validCli.stderr, "")
assert.strictEqual(validCli.stdout.trim(), SUCCESS_MESSAGE)

function commandSegments(scriptName) {
  const command = packageJson.scripts[scriptName]
  assert.strictEqual(typeof command, "string", `${scriptName} must exist`)
  return command.split(/\s*&&\s*/).map(segment => segment.trim())
}

const guardCommand = "node scripts/validate-native-api-origin.cjs"
assert.deepStrictEqual(commandSegments("preapp:sync"), [guardCommand],
  "npm must run the native origin guard through the app:sync pre-lifecycle")
const syncSegments = commandSegments("app:sync")
assert.ok(syncSegments.indexOf("npm run build:prod") >= 0)
assert.ok(syncSegments.indexOf("NODE_ENV=production npx cap sync") >= 0,
  "the guarded app:sync lifecycle must contain both production build and Capacitor sync")

assert.deepStrictEqual(commandSegments("preapp:verify:release"), [guardCommand],
  "npm must run the native origin guard through the release verification pre-lifecycle")
const releaseSegments = commandSegments("app:verify:release")
assert.ok(releaseSegments.indexOf("node scripts/verify-capacitor-sync.cjs --release") >= 0)

console.log("mobile native API origin build guard tests passed")
