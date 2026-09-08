const fs = require("fs")
const path = require("path")

const MAX_FILE_SIZE = 2 * 1024 * 1024
const RULES = [
  {
    ruleId: "FRONTEND_PRIVATE_KEY_API",
    pattern: /\bsetPrivateKey\s*\(/
  },
  {
    ruleId: "FRONTEND_PRIVATE_KEY_PEM",
    pattern: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/
  },
  {
    ruleId: "FRONTEND_PUBLIC_ENV_SECRET_NAME",
    pattern: /^\s*(?:export\s+)?(?:VUE_APP|VITE|NEXT_PUBLIC|NUXT_PUBLIC|REACT_APP)_[A-Z0-9_]*(?:SECRET|TOKEN|PASSWORD|PASSWD|PRIVATE_KEY|ACCESS_KEY|API_KEY)[A-Z0-9_]*\s*=/m
  },
  {
    ruleId: "FRONTEND_KNOWN_CREDENTIAL_FORMAT",
    pattern: /\b(?:AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{30,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,})\b/
  }
]

function findFrontendSecretViolations(rootDir, includePaths) {
  const absoluteRoot = path.resolve(rootDir)
  const violations = []
  const scanPaths = Array.isArray(includePaths) && includePaths.length > 0
    ? includePaths.map(item => path.resolve(absoluteRoot, item))
    : [absoluteRoot]
  scanPaths.filter(item => fs.existsSync(item)).forEach(scanPath => {
    walkTextFiles(absoluteRoot, scanPath, (absolutePath, source) => {
      RULES.forEach(rule => {
        rule.pattern.lastIndex = 0
        if (!rule.pattern.test(source)) return
        violations.push({
          path: toPortablePath(path.relative(absoluteRoot, absolutePath)),
          ruleId: rule.ruleId
        })
      })
    })
  })
  return violations.sort((left, right) => {
    return left.path.localeCompare(right.path) || left.ruleId.localeCompare(right.ruleId)
  })
}

function walkTextFiles(rootDir, currentPath, visit) {
  const stat = fs.lstatSync(currentPath)
  if (stat.isSymbolicLink()) return

  if (stat.isDirectory()) {
    fs.readdirSync(currentPath).sort().forEach(name => {
      walkTextFiles(rootDir, path.join(currentPath, name), visit)
    })
    return
  }

  if (!stat.isFile() || stat.size > MAX_FILE_SIZE) return
  const buffer = fs.readFileSync(currentPath)
  if (buffer.includes(0)) return
  visit(currentPath, buffer.toString("utf8"))
}

function toPortablePath(filePath) {
  return filePath.split(path.sep).join("/")
}

function runCli() {
  const uiRoot = path.resolve(__dirname, "..")
  const violations = findFrontendSecretViolations(uiRoot, [
    "src",
    "public",
    ".env",
    ".env.local",
    ".env.development",
    ".env.production",
    ".env.staging",
    "babel.config.js",
    "build",
    "capacitor.config.ts",
    "vue.config.js"
  ])
  if (violations.length === 0) {
    console.log("frontend secret scan passed")
    return
  }

  console.error("Frontend secret scan failed:")
  violations.forEach(item => console.error(`- ${item.path} [${item.ruleId}]`))
  process.exitCode = 1
}

if (require.main === module) {
  runCli()
}

module.exports = {
  findFrontendSecretViolations
}
