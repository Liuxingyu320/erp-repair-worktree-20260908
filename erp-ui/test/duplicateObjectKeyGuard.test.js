const assert = require("assert")
const fs = require("fs")
const path = require("path")
const parser = require("@babel/parser")

const rootDir = path.resolve(__dirname, "..")
const sourceDir = path.join(rootDir, "src")
const parserPlugins = [
  "jsx",
  "dynamicImport",
  "objectRestSpread",
  "optionalChaining",
  "nullishCoalescingOperator",
  "classProperties",
  "topLevelAwait"
]

function listSourceFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolutePath = path.join(directory, entry.name)
    if (entry.isDirectory()) return listSourceFiles(absolutePath)
    return /\.(?:js|vue)$/.test(entry.name) ? [absolutePath] : []
  })
}

function scriptSource(file, source) {
  if (!file.endsWith(".vue")) return { source, lineOffset: 0 }
  const match = source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/)
  if (!match) return null
  return {
    source: match[1],
    lineOffset: source.slice(0, match.index).split("\n").length - 1
  }
}

function staticPropertyKey(property) {
  if (!property || property.computed || property.type === "SpreadElement") return null
  if (property.key.type === "Identifier") return property.key.name
  if (property.key.type === "StringLiteral" || property.key.type === "NumericLiteral") {
    return String(property.key.value)
  }
  return null
}

function duplicateKeys(file, source, lineOffset) {
  const ast = parser.parse(source, {
    sourceType: "unambiguous",
    plugins: parserPlugins
  })
  const findings = []
  const visited = new Set()

  function visit(node) {
    if (!node || typeof node !== "object" || visited.has(node)) return
    visited.add(node)

    if (node.type === "ObjectExpression") {
      const keys = new Map()
      ;(node.properties || []).forEach(property => {
        const key = staticPropertyKey(property)
        if (!key) return
        const line = lineOffset + property.loc.start.line
        if (keys.has(key)) {
          findings.push({
            file: path.relative(rootDir, file),
            key,
            line,
            previousLine: keys.get(key)
          })
          return
        }
        keys.set(key, line)
      })
    }

    Object.values(node).forEach(value => {
      if (Array.isArray(value)) value.forEach(visit)
      else visit(value)
    })
  }

  visit(ast)
  return findings
}

const findings = listSourceFiles(sourceDir).flatMap(file => {
  const extracted = scriptSource(file, fs.readFileSync(file, "utf8"))
  if (!extracted) return []
  return duplicateKeys(file, extracted.source, extracted.lineOffset)
}).sort((left, right) => {
  return left.file.localeCompare(right.file) || left.line - right.line || left.key.localeCompare(right.key)
})

assert.deepStrictEqual(findings, [], [
  "duplicate static object keys are not allowed because JavaScript silently keeps the last definition:",
  ...findings.map(item => `${item.file}:${item.line}:${item.key} (previous ${item.previousLine})`)
].join("\n"))

console.log("duplicate object key guard passed")
