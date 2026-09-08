const assert = require("assert")
const babelParser = require("@babel/parser")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")
const sqlPath = path.join(repoRoot, "sql/erp_mobile_qa_data_audit_20260710.sql")
const guardPath = path.join(repoRoot, "scripts/qa/require-isolated-qa-env.sh")
const runbookPath = path.join(repoRoot, "docs/mobile-qa-data-governance.md")

const propertyName = node => {
  if (!node) return undefined
  if (node.type === "Identifier") return node.name
  if (node.type === "StringLiteral") return node.value
  return undefined
}

const parameterName = node => {
  if (node?.type === "Identifier") return node.name
  if (node?.type === "AssignmentPattern") return propertyName(node.left)
  return undefined
}

const visitChildNodes = (node, visitor) => {
  Object.entries(node).forEach(([key, value]) => {
    if (["loc", "start", "end", "extra"].includes(key)) return
    if (Array.isArray(value)) {
      value.forEach(child => visitor(child))
    } else if (value && typeof value === "object") {
      visitor(value)
    }
  })
}

const isPasswordTarget = node => {
  if (!node) return false
  if (node.type === "MemberExpression") return propertyName(node.property)?.toLowerCase() === "password"
  return propertyName(node)?.toLowerCase() === "password"
}

const isNonEmptyCredentialLiteral = node => {
  if (!node) return false
  if (node.type === "StringLiteral") return node.value !== ""
  if (node.type === "TemplateLiteral") {
    return node.expressions.length > 0 || node.quasis.some(quasi => quasi.value.cooked !== "")
  }
  return false
}

const isQaPasswordEnvLookup = node =>
  node?.type === "MemberExpression" &&
  propertyName(node.property) === "ERP_QA_PASSWORD" &&
  node.object?.type === "MemberExpression" &&
  propertyName(node.object.object) === "process" &&
  propertyName(node.object.property) === "env"

const isQaPasswordLiteralFallback = node =>
  node?.type === "LogicalExpression" &&
  ["||", "??"].includes(node.operator) &&
  isQaPasswordEnvLookup(node.left) &&
  isNonEmptyCredentialLiteral(node.right)

const findHardcodedQaPasswordViolations = source => {
  const ast = babelParser.parse(source, { sourceType: "script" })
  const violations = []
  const reportedNodes = new Set()
  const passwordParamIndexesByCallable = new Map()

  const report = (node, kind) => {
    if (reportedNodes.has(node)) return
    reportedNodes.add(node)
    violations.push(kind)
  }

  const collectCallableSignatures = node => {
    if (!node || typeof node !== "object") return

    let callableName
    let parameters
    if (node.type === "FunctionDeclaration" && node.id) {
      callableName = node.id.name
      parameters = node.params
    } else if (
      node.type === "VariableDeclarator" &&
      node.id?.type === "Identifier" &&
      ["FunctionExpression", "ArrowFunctionExpression"].includes(node.init?.type)
    ) {
      callableName = node.id.name
      parameters = node.init.params
    }

    if (callableName && parameters) {
      const passwordParamIndexes = parameters
        .map((parameter, index) => (/password/i.test(parameterName(parameter) || "") ? index : -1))
        .filter(index => index >= 0)
      if (passwordParamIndexes.length > 0) {
        passwordParamIndexesByCallable.set(callableName, passwordParamIndexes)
      }
    }

    visitChildNodes(node, collectCallableSignatures)
  }

  collectCallableSignatures(ast)

  const visit = node => {
    if (!node || typeof node !== "object") return

    if (
      node.type === "ObjectProperty" &&
      isPasswordTarget(node.key) &&
      isNonEmptyCredentialLiteral(node.value)
    ) {
      report(node.value, "password-property-literal")
    }
    if (
      node.type === "VariableDeclarator" &&
      isPasswordTarget(node.id) &&
      isNonEmptyCredentialLiteral(node.init)
    ) {
      report(node.init, "password-variable-literal")
    }
    if (
      node.type === "AssignmentExpression" &&
      isPasswordTarget(node.left) &&
      isNonEmptyCredentialLiteral(node.right)
    ) {
      report(node.right, "password-assignment-literal")
    }
    if (isQaPasswordLiteralFallback(node)) {
      report(node, "qa-password-env-literal-fallback")
    }
    if (node.type === "CallExpression" && node.callee?.type === "Identifier") {
      const passwordParamIndexes = passwordParamIndexesByCallable.get(node.callee.name) || []
      passwordParamIndexes.forEach(index => {
        const argument = node.arguments[index]
        if (isNonEmptyCredentialLiteral(argument) || isQaPasswordLiteralFallback(argument)) {
          report(argument, "password-call-argument-literal")
        }
      })
    }

    visitChildNodes(node, visit)
  }

  visit(ast)
  return violations
}

;[sqlPath, guardPath, runbookPath].forEach(filePath => {
  assert.ok(fs.existsSync(filePath), `${path.relative(repoRoot, filePath)} should exist`)
})

const sqlSource = fs.readFileSync(sqlPath, "utf8")
const sqlWithoutComments = sqlSource
  .replace(/\/\*[\s\S]*?\*\//g, "")
  .replace(/^\s*--.*$/gm, "")

assert.ok(/\bselect\b/i.test(sqlWithoutComments), "QA audit should contain read-only SELECT statements")
assert.ok(
  !/\b(delete|update|insert|truncate|drop|alter|replace|create)\b/i.test(sqlWithoutComments),
  "QA candidate audit must not contain a destructive or schema-changing statement"
)

;["qa_", "qa-", "test_", "test-"].forEach(marker => {
  assert.ok(sqlSource.includes(marker), `QA audit should identify marker ${marker}`)
})

;[
  "sys_user",
  "inv_customer",
  "inv_sales_order",
  "inv_purchase_order",
  "inv_sales_return",
  "inv_purchase_return",
  "inv_stock_check",
  "inv_transfer_order"
].forEach(tableName => {
  assert.ok(sqlSource.includes(tableName), `QA audit should inventory ${tableName}`)
})

assert.ok(
  sqlSource.includes("source_table") &&
    sqlSource.includes("source_id") &&
    sqlSource.includes("organization_id") &&
    sqlSource.includes("create_by") &&
    sqlSource.includes("create_time"),
  "QA candidates should include stable review and ownership fields"
)

const guardSource = fs.readFileSync(guardPath, "utf8")
assert.ok(
  guardSource.includes("ERP_QA_RUN_ID") &&
    guardSource.includes("ERP_QA_DATABASE") &&
    guardSource.includes("ERP_QA_ALLOWED_ORG_ID") &&
    guardSource.includes("erp_prod") &&
    guardSource.includes("production"),
  "write-capable QA scripts should require an isolated database, organization and run id"
)
assert.ok(!guardSource.includes("PASSWORD"), "QA guard should not request or print database credentials")

const runbookSource = fs.readFileSync(runbookPath, "utf8")
;["只读审计", "备份", "关联关系", "人工审批", "事务", "回滚", "复核"].forEach(term => {
  assert.ok(runbookSource.includes(term), `QA governance runbook should cover ${term}`)
})

const credentialedQaScripts = [
  "scripts/mobile-android-full-qa-20260705.cjs",
  "scripts/mobile-android-real-submit-qa-20260705.cjs",
  "scripts/mobile-implementation-retest.cjs",
  "scripts/mobile-ios-prod-risk-qa.cjs"
]

const fixturePasswordKey = ["pass", "word"].join("")
const fixtureSentinel = ["non", "authenticating", "fixture"].join("_")
const fixtureLiteral = JSON.stringify(fixtureSentinel)
const fixtureEnvLookup = ["process", "env", "ERP_QA_PASSWORD"].join(".")
const fixtureCallableName = ["fixture", "Login"].join("")
const fixtureArrowName = ["fixture", "Login", "Arrow"].join("")
const fixtureContextIdentifier = ["fixture", "Context"].join("")
const fixtureIdentityIdentifier = ["fixture", "Identity"].join("")

;[
  `const role = {\n  ${fixturePasswordKey}: ${fixtureLiteral}\n}`,
  `const role = { ${fixturePasswordKey}: ${fixtureLiteral} }`,
  `const ${fixturePasswordKey} = ${fixtureLiteral}`,
  `role.${fixturePasswordKey} = ${fixtureLiteral}`,
  `const ERP_QA_PASSWORD = ${fixtureEnvLookup} || ${fixtureLiteral}`,
  `const ERP_QA_PASSWORD = ${fixtureEnvLookup} ?? ${fixtureLiteral}`
].forEach(fixtureSource => {
  assert.strictEqual(
    findHardcodedQaPasswordViolations(fixtureSource).length,
    1,
    "credential detector should reject a non-authenticating literal fixture"
  )
})

;[
  `function ${fixtureCallableName}(context, identity, ${fixturePasswordKey}) {}\n${fixtureCallableName}(${fixtureContextIdentifier}, ${fixtureIdentityIdentifier}, ${fixtureLiteral})`,
  `const ${fixtureArrowName} = (context, ${fixturePasswordKey}) => {}\n${fixtureArrowName}(${fixtureContextIdentifier}, ${fixtureLiteral})`,
  `function ${fixtureCallableName}(context, ${fixturePasswordKey}) {}\n${fixtureCallableName}(${fixtureContextIdentifier}, ${fixtureEnvLookup} || ${fixtureLiteral})`,
  `function ${fixtureCallableName}(context, ${fixturePasswordKey}) {}\n${fixtureCallableName}(${fixtureContextIdentifier}, ${fixtureEnvLookup} ?? ${fixtureLiteral})`,
  `function ${fixtureCallableName}(context, identity, ${fixturePasswordKey} = ERP_QA_PASSWORD) {}\n${fixtureCallableName}(${fixtureContextIdentifier}, ${fixtureIdentityIdentifier}, ${fixtureLiteral})`
].forEach(fixtureSource => {
  assert.strictEqual(
    findHardcodedQaPasswordViolations(fixtureSource).length,
    1,
    "credential detector should reject a literal passed to a password parameter"
  )
})

const selectorText = ["input", `[type=${fixturePasswordKey}]`].join("")
const diagnosticText = [fixturePasswordKey, " unavailable"].join("")
;[
  `const role = { ${fixturePasswordKey}: ERP_QA_PASSWORD }`,
  `const selector = ${JSON.stringify(selectorText)}`,
  `assert.ok(source.includes(${JSON.stringify(diagnosticText)}))`,
  `console.error(${JSON.stringify(diagnosticText)})`,
  `const matcher = /${fixturePasswordKey}:/`,
  `function ${fixtureCallableName}(context, identity, ${fixturePasswordKey}) {}\n${fixtureCallableName}(${fixtureContextIdentifier}, ${fixtureIdentityIdentifier}, ERP_QA_PASSWORD)`,
  `function inspectSelector(selector) {}\ninspectSelector(${JSON.stringify(selectorText)})`,
  `const inspectDiagnostic = message => {}\ninspectDiagnostic(${JSON.stringify(diagnosticText)})`
].forEach(fixtureSource => {
  assert.strictEqual(
    findHardcodedQaPasswordViolations(fixtureSource).length,
    0,
    "credential detector should allow env propagation and non-credential text"
  )
})

credentialedQaScripts.forEach(relativePath => {
  const scriptSource = fs.readFileSync(path.join(repoRoot, relativePath), "utf8")
  const lookupMatches = scriptSource.match(/process\.env\.ERP_QA_PASSWORD/g) || []
  assert.strictEqual(lookupMatches.length, 1, `${relativePath} should read ERP_QA_PASSWORD exactly once`)
  assert.ok(
    /if\s*\(\s*ERP_QA_PASSWORD\s*===\s*undefined\s*\|\|\s*ERP_QA_PASSWORD\s*===\s*""\s*\)/.test(
      scriptSource
    ),
    `${relativePath} should fail closed when ERP_QA_PASSWORD is missing or empty`
  )

  const lookupIndex = scriptSource.indexOf("process.env.ERP_QA_PASSWORD")
  const messageIndex = scriptSource.indexOf('console.error("ERP_QA_PASSWORD is required")')
  const exitIndex = scriptSource.indexOf("process.exit(2)")
  const workIndex = scriptSource.search(/\b(?:async\s+function|fetch\s*\(|axios\.|createConnection\s*\()/)
  assert.ok(
    messageIndex > lookupIndex && exitIndex > messageIndex && (workIndex === -1 || exitIndex < workIndex),
    `${relativePath} should exit 2 with a variable-only message before automation or network work`
  )
  assert.strictEqual(
    findHardcodedQaPasswordViolations(scriptSource).length,
    0,
    `${relativePath} should not contain a literal password assignment`
  )
})

console.log("mobileQaDataGovernance tests passed")
