const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const manifest = JSON.parse(fs.readFileSync(path.join(rootDir, "package.json"), "utf8"))
const lock = JSON.parse(fs.readFileSync(path.join(rootDir, "package-lock.json"), "utf8"))
const packages = lock.packages

const minimatch3Path = "node_modules/minimatch"
const bracePath = "node_modules/brace-expansion"
const minimatch10Path = "node_modules/@capacitor/cli/node_modules/minimatch"
const compatibilityPatch = path.join(rootDir, "patches/minimatch+3.1.5.patch")

assert.strictEqual(packages[minimatch3Path].version, "3.1.5")
assert.strictEqual(packages[minimatch3Path].dependencies["brace-expansion"], "^1.1.7")
assert.strictEqual(
  manifest.overrides && manifest.overrides["brace-expansion"],
  "5.0.9",
  "every transitive chain must use the release patched for GHSA-rgw5-rvv9-x895"
)
assert.strictEqual(manifest.scripts.postinstall, "patch-package")
assert.ok(fs.existsSync(compatibilityPatch), "the minimatch 3 CommonJS compatibility patch must be versioned")
assert.match(
  fs.readFileSync(compatibilityPatch, "utf8"),
  /typeof braceExpansion === 'function' \? braceExpansion : braceExpansion\.expand/,
  "the patch must support both the legacy callable export and brace-expansion 5 named export"
)

assert.strictEqual(packages[minimatch10Path].version, "10.2.5")
assert.strictEqual(packages[minimatch10Path].dependencies["brace-expansion"], "^5.0.5")
assert.strictEqual(
  packages[bracePath].version,
  "5.0.9",
  "the resolved brace-expansion package must bound intermediate arrays as well as final output"
)

const lockedBraceVersions = [...new Set(Object.entries(packages)
  .filter(([packagePath]) => packagePath === bracePath || packagePath.endsWith("/brace-expansion"))
  .map(([, metadata]) => metadata.version))]
  .sort()
assert.deepStrictEqual(
  lockedBraceVersions,
  ["5.0.9"],
  "no vulnerable brace-expansion copy may re-enter the lockfile"
)

const brace5 = require(path.join(rootDir, bracePath))
const minimatch3 = require(path.join(rootDir, minimatch3Path))
const minimatch10 = require(path.join(rootDir, minimatch10Path)).minimatch

assert.deepStrictEqual(brace5.expand("asset-{1..3}.{js,css}"), [
  "asset-1.js",
  "asset-1.css",
  "asset-2.js",
  "asset-2.css",
  "asset-3.js",
  "asset-3.css"
])

assert.strictEqual(brace5.EXPANSION_MAX_LENGTH, 4_000_000)
const boundedExpansion = brace5.expand("{a,b}".repeat(16), {
  max: 100_000,
  maxLength: 128
})
assert.ok(boundedExpansion.length > 0)
assert.ok(
  boundedExpansion.reduce((total, value) => total + value.length, 0) <= 128,
  "brace-expansion 5 must enforce the total output-length bound from the DoS fix"
)

for (const matcher of [minimatch3, minimatch10]) {
  assert.strictEqual(matcher("src/components/Button.vue", "src/{components,views}/**/*.vue"), true)
  assert.strictEqual(matcher("src/components/Button.js", "src/{components,views}/**/*.vue"), false)
}

console.log("dependencyBraceExpansionSecurity tests passed")
