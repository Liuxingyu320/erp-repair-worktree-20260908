const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const src = path.join(root, "src")
const plugin = fs.readFileSync(path.join(src, "plugins/element-ui.js"), "utf8")
const services = fs.readFileSync(path.join(src, "plugins/element-services.js"), "utf8")
const theme = fs.readFileSync(path.join(src, "assets/styles/element-variables.scss"), "utf8")

function files(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const target = path.join(dir, entry.name)
    if (entry.isDirectory()) return files(target)
    return /\.(?:js|vue)$/.test(entry.name) ? [target] : []
  })
}

function pascal(name) {
  return name.split("-").map(part => part.charAt(0).toUpperCase() + part.slice(1)).join("")
}

function sorted(values) {
  return [...values].sort()
}

const sources = files(src).map(file => fs.readFileSync(file, "utf8"))
const required = new Set()
const transitiveThemeOwner = {
  option: "select",
  "option-group": "select"
}
const transitiveComponents = {
  skeleton: ["skeleton-item"]
}
sources.forEach(source => {
  for (const match of source.matchAll(/<el-([a-z0-9-]+)/g)) required.add(match[1])
  for (const match of source.matchAll(/\btag\s*:\s*['"]el-([a-z0-9-]+)['"]/g)) {
    required.add(match[1])
  }
})

const expectedComponentTags = new Set(required)
required.forEach(component => {
  ;(transitiveComponents[component] || []).forEach(dependency => expectedComponentTags.add(dependency))
})
const expectedComponents = new Set([...expectedComponentTags].map(pascal))
const importedComponents = new Set(
  [...plugin.matchAll(/import\s+(\w+)\s+from\s+['"]element-ui\/(?:packages|src\/transitions)\/[^'"]+['"]/g)]
    .map(match => match[1])
)
const asyncComponentNames = new Set(
  [...plugin.matchAll(/^\s*(El\w+):\s*\(\)\s*=>\s*import\(.*['"]element-ui\/(?:packages|src\/transitions)\/[^'"]+['"]/gm)]
    .map(match => match[1].replace(/^El/, ""))
)
const loadedComponents = new Set([...importedComponents, ...asyncComponentNames])
const componentListMatch = plugin.match(/const components = \[([\s\S]*?)\]\n/)
assert.ok(componentListMatch, "Element UI component registration list is missing")
const registeredComponents = new Set(
  componentListMatch[1].split(",").map(name => name.trim()).filter(Boolean)
)
asyncComponentNames.forEach(name => registeredComponents.add(name))

assert.deepStrictEqual(
  sorted(loadedComponents),
  sorted(expectedComponents),
  "Element UI sync and async imports must exactly match components used by source templates"
)
assert.deepStrictEqual(
  sorted(registeredComponents),
  sorted(expectedComponents),
  "Element UI registrations must exactly match components used by source templates"
)
assert.ok(
  asyncComponentNames.has("Table") &&
    asyncComponentNames.has("Tree") &&
    asyncComponentNames.has("DatePicker") &&
    asyncComponentNames.has("Upload"),
  "heavy Element UI components should stay behind async registration boundaries"
)
assert.ok(
  importedComponents.has("ColorPicker") && !asyncComponentNames.has("ColorPicker"),
  "the always-mounted theme picker should remain synchronous without pulling the advanced form chunk"
)

const expectedTheme = new Set(["base"])
expectedComponentTags.forEach(component => {
  if (component === "collapse-transition") return
  expectedTheme.add(transitiveThemeOwner[component] || component)
})
for (const match of services.matchAll(
  /export\s+\{\s*default\s+as\s+\w+\s*\}\s+from\s+['"]element-ui\/packages\/([^'"]+)['"]/g
)) {
  expectedTheme.add(match[1])
}
const actualTheme = new Set(
  [...theme.matchAll(/theme-chalk\/src\/([^"']+)/g)].map(match => match[1])
)
assert.deepStrictEqual(
  sorted(actualTheme),
  sorted(expectedTheme),
  "Element UI theme imports must exactly match template components and programmatic services"
)

sources.forEach(source => {
  assert.ok(
    !/from\s+['\"]element-ui['\"]/.test(source),
    "source files must not import the complete Element UI entry"
  )
})
assert.ok(!theme.includes("theme-chalk/src/index"), "theme must stay component-scoped")

console.log(
  `Element UI on-demand coverage passed for ${required.size} source-used and ` +
    `${expectedComponentTags.size - required.size} transitive components`
)
