const assert = require("assert")
const fs = require("fs")
const path = require("path")

const userPagePath = path.resolve(__dirname, "../src/views/system/user/index.vue")
const source = fs.readFileSync(userPagePath, "utf8")
const scriptMatch = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)

assert.ok(scriptMatch, "system user page should contain a script block")

const script = scriptMatch[1]
const componentsMatch = script.match(/components\s*:\s*\{([\s\S]*?)\n\s*\},\n\s*data\s*\(/)

assert.ok(componentsMatch, "system user page should expose a components registry")

const registeredComponents = componentsMatch[1]
  .split(",")
  .map(entry => entry.trim())
  .filter(Boolean)
  .map(entry => {
    const value = entry.includes(":") ? entry.split(":").pop().trim() : entry
    assert.match(value, /^[A-Za-z_$][\w$]*$/, `component registration must resolve to an identifier: ${entry}`)
    return value
  })

const importedBindings = new Set()
const importPattern = /import\s+(?!["'])([\s\S]*?)\s+from\s+["'][^"']+["']/g
let importMatch

while ((importMatch = importPattern.exec(script))) {
  const clause = importMatch[1].trim()
  const defaultBinding = clause.match(/^([A-Za-z_$][\w$]*)/)
  if (defaultBinding) importedBindings.add(defaultBinding[1])

  const namedBindings = clause.match(/\{([\s\S]*?)\}/)
  if (namedBindings) {
    namedBindings[1].split(",").map(binding => binding.trim()).filter(Boolean).forEach(binding => {
      const alias = binding.split(/\s+as\s+/).pop().trim()
      importedBindings.add(alias)
    })
  }

  const namespaceBinding = clause.match(/\*\s+as\s+([A-Za-z_$][\w$]*)/)
  if (namespaceBinding) importedBindings.add(namespaceBinding[1])
}

const locallyDeclaredBindings = new Set()
const declarationPattern = /(?:const|let|var|class|function)\s+([A-Za-z_$][\w$]*)/g
let declarationMatch

while ((declarationMatch = declarationPattern.exec(script))) {
  locallyDeclaredBindings.add(declarationMatch[1])
}

const unboundComponents = registeredComponents.filter(name => (
  !importedBindings.has(name) && !locallyDeclaredBindings.has(name)
))

assert.deepStrictEqual(
  unboundComponents,
  [],
  `system user page registers unbound component variables: ${unboundComponents.join(", ")}`
)

for (const component of ["SupervisorUserSelect", "UserFilterDrawer", "UserFormReview"]) {
  assert.ok(
    registeredComponents.includes(component) && importedBindings.has(component),
    `${component} must remain imported before it is registered on the system user page`
  )
}
