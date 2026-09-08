const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")
const { spawnSync } = require("child_process")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
const readRepo = relativePath => fs.readFileSync(path.resolve(repoRoot, relativePath), "utf8")

const packageJson = JSON.parse(readUi("package.json"))
const pomSource = readRepo("pom.xml")
const validateScript = path.resolve(uiRoot, "scripts/validate-onboard-release-build.cjs")
const {
  writeOnboardReleaseProvenance
} = require("../scripts/write-onboard-release-provenance.cjs")

const VALID_ENVIRONMENT = Object.freeze({
  VUE_APP_BUILD_COMMIT: "a".repeat(40),
  VUE_APP_BUILD_RELEASE_ID: "erp-current-release-20260722",
  VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true",
  VUE_APP_BUILD_APPROVED_PATCH_SHA256: "b".repeat(64),
  VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256: "c".repeat(64)
})

function runValidator(overrides = {}) {
  return spawnSync(process.execPath, [validateScript], {
    cwd: uiRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      ...VALID_ENVIRONMENT,
      ...overrides
    }
  })
}

function loadSettings(environment) {
  const variables = [
    "VUE_APP_BUILD_RELEASE_ID",
    "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED",
    "VUE_APP_BUILD_APPROVED_PATCH_SHA256",
    "VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256"
  ]
  const previous = Object.fromEntries(
    variables.map(variable => [variable, process.env[variable]])
  )
  try {
    for (const variable of variables) {
      if (Object.prototype.hasOwnProperty.call(environment, variable)) {
        process.env[variable] = environment[variable]
      } else {
        delete process.env[variable]
      }
    }
    const settingsPath = require.resolve("../src/settings.js")
    delete require.cache[settingsPath]
    return require(settingsPath)
  } finally {
    for (const variable of variables) {
      if (previous[variable] === undefined) {
        delete process.env[variable]
      } else {
        process.env[variable] = previous[variable]
      }
    }
    delete require.cache[require.resolve("../src/settings.js")]
  }
}

assert.strictEqual(
  packageJson.scripts["prebuild:onboard-contract"],
  "node scripts/scan-frontend-secrets.cjs && node scripts/validate-release-build.cjs && node scripts/validate-onboard-release-build.cjs",
  "the dedicated prebuild must preserve the secret and ordinary release metadata gates"
)
assert.strictEqual(
  packageJson.scripts["build:onboard-contract"],
  "vue-cli-service build --report-json",
  "the dedicated build must retain the production report used by the bundle gate"
)
assert.strictEqual(
  packageJson.scripts["postbuild:onboard-contract"],
  "node scripts/check-production-bundle.cjs && node scripts/write-release-info.cjs && node scripts/write-onboard-release-provenance.cjs",
  "the dedicated postbuild must preserve bundle and release-info checks before provenance"
)
assert.strictEqual(
  packageJson.scripts["build:prod"],
  "vue-cli-service build --report-json",
  "ordinary production builds must remain unchanged"
)

for (const property of [
  "build.releaseId",
  "build.approvedPatchSha256",
  "build.approvedSourceManifestSha256"
]) {
  assert.ok(
    pomSource.includes(`<${property}>UNSET</${property}>`),
    `root Maven property ${property} must fail visibly by default`
  )
}
assert.ok(
  pomSource.includes("<commit>${build.commit}</commit>") &&
    pomSource.includes("<releaseId>${build.releaseId}</releaseId>") &&
    pomSource.includes("<approvedPatchSha256>${build.approvedPatchSha256}</approvedPatchSha256>") &&
    pomSource.includes("<approvedSourceManifestSha256>${build.approvedSourceManifestSha256}</approvedSourceManifestSha256>"),
  "Spring Boot build-info must retain commit and embed all approved source provenance"
)

assert.strictEqual(runValidator().status, 0, "valid approved metadata must pass")
for (const [variable, invalidValue] of [
  ["VUE_APP_BUILD_RELEASE_ID", "UNSET"],
  ["VUE_APP_BUILD_RELEASE_ID", "20260717"],
  ["VUE_APP_SIGN_EXCEL_IMPORT_ENABLED", "false"],
  ["VUE_APP_SIGN_EXCEL_IMPORT_ENABLED", "TRUE"],
  ["VUE_APP_SIGN_EXCEL_IMPORT_ENABLED", ""],
  ["VUE_APP_BUILD_APPROVED_PATCH_SHA256", "UNSET"],
  ["VUE_APP_BUILD_APPROVED_PATCH_SHA256", "0".repeat(64)],
  ["VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256", "d".repeat(63)],
  ["VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256", "D".repeat(64)]
]) {
  const result = runValidator({ [variable]: invalidValue })
  assert.notStrictEqual(
    result.status,
    0,
    `${variable}=${invalidValue} must be rejected`
  )
}

const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "onboard-release-provenance-"))
try {
  const distDirectory = path.resolve(temporaryRoot, "dist")
  fs.mkdirSync(distDirectory)
  fs.writeFileSync(path.resolve(distDirectory, "index.html"), "<!doctype html>\n")
  const written = writeOnboardReleaseProvenance(distDirectory, VALID_ENVIRONMENT)
  const output = JSON.parse(
    fs.readFileSync(path.resolve(distDirectory, "release-provenance.json"), "utf8")
  )
  const expected = {
    schemaVersion: 1,
    releaseId: "erp-current-release-20260722",
    signExcelImportEnabled: true,
    candidateCommit: "a".repeat(40),
    approvedPatchSha256: "b".repeat(64),
    approvedSourceManifestSha256: "c".repeat(64)
  }
  assert.deepStrictEqual(written, expected)
  assert.deepStrictEqual(output, expected)
  assert.deepStrictEqual(Object.keys(output), [
    "schemaVersion",
    "releaseId",
    "signExcelImportEnabled",
    "candidateCommit",
    "approvedPatchSha256",
    "approvedSourceManifestSha256"
  ])
} finally {
  fs.rmSync(temporaryRoot, { recursive: true, force: true })
}

assert.deepStrictEqual(
  {
    releaseId: loadSettings({}).releaseId,
    signExcelImportEnabled: loadSettings({}).signExcelImportEnabled,
    approvedPatchSha256: loadSettings({}).approvedPatchSha256,
    approvedSourceManifestSha256: loadSettings({}).approvedSourceManifestSha256
  },
  {
    releaseId: "UNSET",
    signExcelImportEnabled: true,
    approvedPatchSha256: "UNSET",
    approvedSourceManifestSha256: "UNSET"
  },
  "ordinary builds must expose visible UNSET provenance while keeping import enabled"
)
const injectedSettings = loadSettings(VALID_ENVIRONMENT)
assert.strictEqual(injectedSettings.releaseId, "erp-current-release-20260722")
assert.strictEqual(injectedSettings.signExcelImportEnabled, true)
assert.strictEqual(injectedSettings.approvedPatchSha256, "b".repeat(64))
assert.strictEqual(injectedSettings.approvedSourceManifestSha256, "c".repeat(64))

console.log("onboardReleaseProvenance tests passed")
