const fs = require("fs")
const path = require("path")
const { spawnSync } = require("child_process")

const rootDir = path.resolve(__dirname, "..")
const testDir = path.join(rootDir, "test")

function listTestFiles() {
  return fs.readdirSync(testDir)
    .filter(file => /\.test(?:\.[cm]?js|\.js)$/.test(file) || /\.test\.js$/.test(file))
    .sort()
}

const duplicatePattern = / [2-9][0-9]*\.js$/
const duplicateCopies = fs.readdirSync(testDir).filter(file => duplicatePattern.test(file))
if (duplicateCopies.length > 0) {
  console.error("Duplicate copied test files are not allowed:")
  duplicateCopies.forEach(file => console.error(`- test/${file}`))
  process.exit(1)
}

const requestedFiles = process.argv.slice(2)
const testFiles = requestedFiles.length > 0 ? requestedFiles : listTestFiles()

let failures = 0

for (const file of testFiles) {
  const relativeFile = file.startsWith("test/") ? file : path.join("test", file)
  const absoluteFile = path.join(rootDir, relativeFile)
  console.log(`[frontend-test] ${relativeFile}`)
  const result = spawnSync(process.execPath, [absoluteFile], {
    cwd: rootDir,
    stdio: "inherit"
  })
  if (result.status !== 0) {
    failures += 1
  }
}

console.log(`\nfront-end node tests: ${testFiles.length} run, ${failures} failed`)

if (failures > 0) {
  process.exit(1)
}
