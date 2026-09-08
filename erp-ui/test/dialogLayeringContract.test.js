const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const sourceRoot = path.join(root, "src")

function listFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) return listFiles(absolute)
    return entry.name.endsWith(".vue") ? [absolute] : []
  })
}

function lineNumber(source, offset) {
  return source.slice(0, offset).split("\n").length
}

function hasSafeLayeringContract(attributes) {
  const bareAppendToBody = /(?:^|\s)append-to-body(?:\s|$)/.test(attributes)
  const boundTrueAppendToBody = /(?:^|\s)(?::|v-bind:)append-to-body\s*=\s*["']true["']/.test(attributes)
  const sameContextModal = /(?:^|\s)(?::|v-bind:)modal-append-to-body\s*=\s*["']false["']/.test(attributes)
  return bareAppendToBody || boundTrueAppendToBody || sameContextModal
}

function unsafeOverlayTags(file, source) {
  const findings = []
  const tagPattern = /<(el-(?:dialog|drawer))\b([\s\S]*?)>/g
  let match
  while ((match = tagPattern.exec(source))) {
    if (hasSafeLayeringContract(match[2])) continue
    findings.push(`${path.relative(root, file)}:${lineNumber(source, match.index)}:${match[1]}`)
  }
  return findings
}

const runtimeFindings = listFiles(sourceRoot)
  .flatMap(file => unsafeOverlayTags(file, fs.readFileSync(file, "utf8")))
  .sort()

assert.deepStrictEqual(runtimeFindings, [],
  `dialogs and drawers must escape the desktop stacking context (or keep their modal in the same context):\n${runtimeFindings.join("\n")}`)

const generatorPath = path.join(sourceRoot, "utils/generator/html.js")
const generatorFindings = unsafeOverlayTags(generatorPath, fs.readFileSync(generatorPath, "utf8"))
assert.deepStrictEqual(generatorFindings, [],
  "generated dialogs must include the same safe layering contract")

console.log("dialog layering contract tests passed")
