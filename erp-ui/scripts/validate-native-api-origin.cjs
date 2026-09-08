const fs = require("fs")
const path = require("path")
const dotenv = require("dotenv")
const dotenvExpand = require("dotenv-expand")

const ENV_KEY = "VUE_APP_NATIVE_API_ORIGIN"
const ERROR_CODE = "NATIVE_API_ORIGIN_INVALID"
const ERROR_MESSAGE = `${ERROR_CODE}: VUE_APP_NATIVE_API_ORIGIN must be an absolute HTTPS origin without credentials, path, query, or hash`
const SUCCESS_MESSAGE = "NATIVE_API_ORIGIN_VALID"

function parseEnvValue(contents, key = ENV_KEY) {
  return dotenv.parse(String(contents || ""))[key]
}

function expandForEnvironment(parsed, environment) {
  const combined = Object.assign({}, parsed, environment)
  return dotenvExpand({ parsed: combined, ignoreProcessEnv: true }).parsed
}

function loadVueCliProductionEnv(options = {}) {
  const context = options.context || path.resolve(__dirname, "..")
  const readFileSync = options.readFileSync || fs.readFileSync
  const environment = Object.assign({}, options.env || {})
  const envFiles = [
    ".env.production.local",
    ".env.production",
    ".env.local",
    ".env"
  ]
  for (const fileName of envFiles) {
    let contents
    try {
      contents = readFileSync(path.resolve(context, fileName), "utf8")
    } catch (error) {
      if (error && error.code === "ENOENT") continue
      throw error
    }
    const parsed = dotenv.parse(contents)
    const expanded = expandForEnvironment(parsed, environment)
    Object.keys(parsed).forEach(key => {
      environment[key] = expanded[key]
    })
  }
  return environment
}

function resolveNativeApiOrigin(options = {}) {
  try {
    return loadVueCliProductionEnv(options)[ENV_KEY]
  } catch (error) {
    return undefined
  }
}

function validateNativeApiOrigin(value) {
  const rawCandidate = typeof value === "string" ? value : ""
  if (/[\x00-\x1F\x7F\\]/.test(rawCandidate)) {
    return { ok: false, code: ERROR_CODE, message: ERROR_MESSAGE }
  }
  const candidate = rawCandidate.trim()
  if (!candidate) return { ok: false, code: ERROR_CODE, message: ERROR_MESSAGE }
  let parsed
  try {
    parsed = new URL(candidate)
  } catch (error) {
    return { ok: false, code: ERROR_CODE, message: ERROR_MESSAGE }
  }
  const valid = parsed.protocol === "https:" &&
    !!parsed.hostname &&
    !parsed.username &&
    !parsed.password &&
    (candidate === parsed.origin || candidate === `${parsed.origin}/`)
  return valid
    ? { ok: true, origin: parsed.origin }
    : { ok: false, code: ERROR_CODE, message: ERROR_MESSAGE }
}

function runCli(options = {}) {
  const result = validateNativeApiOrigin(resolveNativeApiOrigin(options))
  const output = options.console || console
  if (!result.ok) {
    output.error(ERROR_MESSAGE)
    return 1
  }
  output.log(SUCCESS_MESSAGE)
  return 0
}

if (require.main === module) {
  process.exitCode = runCli({ env: process.env })
}

module.exports = {
  ENV_KEY,
  ERROR_CODE,
  ERROR_MESSAGE,
  SUCCESS_MESSAGE,
  parseEnvValue,
  loadVueCliProductionEnv,
  resolveNativeApiOrigin,
  validateNativeApiOrigin,
  runCli
}
