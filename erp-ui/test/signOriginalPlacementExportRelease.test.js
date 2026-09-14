const assert = require("assert")
const fs = require("fs")
const path = require("path")
const { execFileSync, spawnSync } = require("child_process")
const { withReleaseSourceFixture } = require("./helpers/releaseSourceFixture")

const root = path.resolve(__dirname, "../..")
const manifestPath = path.join(root,
  "scripts/sign-original-placement-export-release-20260809.json")
const sourceListPath = path.join(root,
  "scripts/sign-original-placement-export-release-files-20260809.list")
const migrationListPath = path.join(root,
  "scripts/sign-original-placement-export-migrations-20260809.list")
const verifier = path.join(root,
  "scripts/verify-sign-original-placement-export-release.sh")
const wrapper = path.join(root,
  "scripts/release-sign-original-placement-export-20260809.sh")
const rootPom = path.join(root, "pom.xml")
const databaseVerifierSource = path.join(root,
  "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/" +
  "OaSignLaborPlacementDatabaseVerifierTest.java")

const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"))
const sources = fs.readFileSync(sourceListPath, "utf8").split(/\r?\n/)
  .map(line => line.trim()).filter(line => line && !line.startsWith("#"))
const migrations = fs.readFileSync(migrationListPath, "utf8").split(/\r?\n/)
  .map(line => line.trim()).filter(line => line && !line.startsWith("#"))

assert.strictEqual(manifest.schemaVersion, 1)
assert.strictEqual(manifest.releaseId, "sign-original-placement-export-20260809")
assert.strictEqual(manifest.status, "development")
assert.strictEqual(manifest.deployable, false)
assert.strictEqual(manifest.deploymentAuthorized, false)
assert.strictEqual(manifest.approvalRecord, null)
assert.strictEqual(manifest.workingTreeReproducible, false)
assert.strictEqual(manifest.pinnedBuildInputs.scope,
  "cross-release-reactor-build-inputs-not-rev06-business-source")
assert.strictEqual(manifest.pinnedBuildInputs.fileCount,
  manifest.pinnedBuildInputs.files.length)
for (const requiredPom of [
  "pom.xml",
  "erp-api/pom.xml",
  "erp-modules/pom.xml",
  "erp-modules/erp-oa/pom.xml"
]) {
  assert.ok(manifest.pinnedBuildInputs.files.some(item => item.file === requiredPom))
}
assert.strictEqual(manifest.sourceFileCount, sources.length)
assert.strictEqual(new Set(sources).size, sources.length)
assert.deepStrictEqual(migrations,
  ["erp_oa_sign_labor_contract_placement_v7_20260809.sql"])
assert.strictEqual(manifest.migrationCount, 1)
assert.strictEqual(manifest.migrations[0].sha256,
  "829ae042311b6b4bc14f5969062951f4991fdc84340a60272a9e103bf35f1584")
assert.deepStrictEqual(manifest.gateOrder, [
  "VERIFY_SOURCE_MANIFEST_AND_CODE_COMPATIBILITY",
  "VERIFY_EXACT_V7_SOURCE_DOCX_SHA256",
  "BUILD_AND_RECORD_CANDIDATE_OA_ARTIFACT_SHA256",
  "STOP_PACKAGE_WRITES_AND_OA_SERVICE",
  "INSTALL_AND_ATTEST_OA_ARTIFACT_WHILE_STOPPED",
  "RUN_READ_ONLY_PREFLIGHT_AND_JAVA_DB_GATE_PRE_SKIP_ZERO",
  "APPLY_IMMUTABLE_MIGRATION_ONCE",
  "RUN_JAVA_DB_GATE_POST_SKIP_ZERO",
  "START_OA_SERVICE",
  "VERIFY_OA_HEALTH_UP",
  "VERIFY_READ_ONLY_SIGNING_ROUTES"
])
assert.strictEqual(manifest.artifactAttestation.candidateEvidenceRequired, true)
assert.strictEqual(
  manifest.artifactAttestation.installedEvidenceRequiredWhileStopped, true)
assert.ok(manifest.artifactAttestation.requiredJarEntries.includes(
  "com/erp/oa/service/impl/OaSignDocumentService.class"))
for (const requiredClass of [
  "com/erp/oa/service/impl/OaPdfPageNumberService.class",
  "com/erp/oa/service/impl/OaSignLaborPlacementProfileRegistry.class",
  "com/erp/oa/service/impl/OaSignPlanVersionFingerprint.class"
]) {
  assert.ok(manifest.artifactAttestation.requiredJarEntries.includes(requiredClass))
}
assert.ok(manifest.artifactAttestation.requiredJarEntries.includes(
  "oa/sign/templates/onboard-20260721-v7/09_ONBOARD_LABOR_CONTRACT.docx"))
assert.ok(manifest.artifactAttestation.requiredJarEntries.includes(
  "META-INF/erp-sign-placement-release.properties"))
assert.deepStrictEqual(manifest.artifactAttestation.buildInfoProperties, {
  "build.commit": "baselineCommit",
  "build.releaseId": "releaseId",
  "build.approvedPatchSha256": "sourceSnapshotSha256",
  "build.approvedSourceManifestSha256": "manifestSha256"
})
assert.strictEqual(manifest.readOnlyRouteSmoke.minimumRouteCount, 2)
assert.strictEqual(manifest.readOnlyRouteSmoke.requiredHttpMethod, "GET")
assert.strictEqual(manifest.readOnlyRouteSmoke.responseBodyPersisted, false)
assert.deepStrictEqual(manifest.readOnlyRouteSmoke.exactPaths,
  ["/signPackage/scope/options", "/signPackage/template/types"])
assert.strictEqual(manifest.readOnlyRouteSmoke.sameOriginAsOaHealthUrl, true)
assert.strictEqual(manifest.rollbackPolicy.candidatePackageCountMustEqual, 0)
assert.strictEqual(manifest.historicalCoverage.zeroSamplesClaim, "not-verified")

for (const directDependency of [
  "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPdfPageNumberService.java",
  "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaPdfPageNumberServiceTest.java",
  "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignGoldenFixture.java"
]) {
  assert.ok(sources.includes(directDependency), directDependency)
}

for (const source of sources) {
  assert.ok(!/(^|\/)(output|target|dist|node_modules)(\/|$)/i.test(source), source)
  assert.ok(!source.includes("tmp/pdfs/sign-export-audit"), source)
  assert.ok(!/(^|\/)(drive|cloud-drive|pds)(\/|$)/i.test(source), source)
  assert.ok(fs.statSync(path.join(root, source)).isFile(), source)
}

execFileSync("bash", ["-n", verifier], { cwd: root, stdio: "pipe" })
execFileSync("bash", ["-n", wrapper], { cwd: root, stdio: "pipe" })
const wrapperText = fs.readFileSync(wrapper, "utf8")
const rootPomText = fs.readFileSync(rootPom, "utf8")
const databaseVerifierText = fs.readFileSync(databaseVerifierSource, "utf8")
assert.match(wrapperText, /FORMAL_EXPORT_DRY_RUN_VERIFIED/)
assert.doesNotMatch(wrapperText,
  /ALL_SIGNED_CONFIRMED_LABOR_CONTRACTS_DRY_RUN_VERIFIED/)
assert.match(wrapperText, /assert_same_database_identity/)
assert.match(wrapperText, /@@server_uuid, @@hostname, @@port, DATABASE\(\)/)
assert.match(wrapperText, /route origin differs from the OA health origin/)
assert.match(wrapperText,
  /authorization header file must contain exactly one Bearer header/)
assert.match(wrapperText, /--connect-timeout 3 --max-time 15/)
assert.match(wrapperText, /--self-test-route-policy/)
assert.match(wrapperText, /META-INF\/erp-sign-placement-release\.properties/)
for (const buildProperty of [
  "build.commit",
  "build.releaseId",
  "build.approvedPatchSha256",
  "build.approvedSourceManifestSha256"
]) {
  assert.match(wrapperText, new RegExp(`-D${buildProperty.replace(".", "\\.")}=`))
}
for (const pomContract of [
  "<build.releaseId>UNSET</build.releaseId>",
  "<build.approvedPatchSha256>UNSET</build.approvedPatchSha256>",
  "<build.approvedSourceManifestSha256>UNSET</build.approvedSourceManifestSha256>",
  "<releaseId>${build.releaseId}</releaseId>",
  "<approvedPatchSha256>${build.approvedPatchSha256}</approvedPatchSha256>",
  "<approvedSourceManifestSha256>${build.approvedSourceManifestSha256}</approvedSourceManifestSha256>"
]) {
  assert.ok(rootPomText.includes(pomContract), pomContract)
}
assert.match(databaseVerifierText, /"FORMAL_EXPORT_DRY_RUN_VERIFIED"/)
assert.match(databaseVerifierText, /SELECT @@server_uuid AS server_uuid/)
assert.match(databaseVerifierText, /evidence\.put\("databaseIdentity", databaseIdentity\)/)
const approvedGate = spawnSync("bash", [verifier, "--approved"], {
  cwd: root,
  encoding: "utf8"
})
assert.notStrictEqual(approvedGate.status, 0)
assert.match(`${approvedGate.stdout}${approvedGate.stderr}`,
  /approved gate requires project-manager-approved manifest status/)
const executeWhileDevelopment = spawnSync("bash", [wrapper, "--execute-migration"], {
  cwd: root,
  encoding: "utf8",
  env: {
    ...process.env,
    ERP_SIGN_RELEASE_AUTHORIZED: "true",
    ERP_SIGN_PACKAGE_WRITES_BLOCKED: "true",
    ERP_SIGN_OA_SERVICE_STOPPED: "true",
    ERP_SIGN_PLACEMENT_DB_VERIFY: "true"
  }
})
assert.notStrictEqual(executeWhileDevelopment.status, 0)
assert.match(`${executeWhileDevelopment.stdout}${executeWhileDevelopment.stderr}`,
  /approved gate requires project-manager-approved manifest status/)
const routePolicySelfTest = spawnSync("bash", [wrapper, "--self-test-route-policy"], {
  cwd: root,
  encoding: "utf8"
})
assert.strictEqual(routePolicySelfTest.status, 0,
  `${routePolicySelfTest.stdout}${routePolicySelfTest.stderr}`)
assert.match(routePolicySelfTest.stdout,
  /READ_ONLY_SIGNING_ROUTE_POLICY_SELF_TEST_PASSED cases=7 network=disabled/)
const verifierText = fs.readFileSync(verifier, "utf8")
assert.match(verifierText, /sha256\(path\) != recorded/,
  "the immutable historical verifier must retain pinned content-hash enforcement")
withReleaseSourceFixture(root, [
  ...sources, ...manifest.pinnedBuildInputs.files.map(item => item.file),
  path.relative(root, sourceListPath), path.relative(root, migrationListPath),
  path.relative(root, manifestPath), path.relative(root, verifier), path.relative(root, wrapper)
], fixture => {
  fixture.commit()
  const verify = () => spawnSync("bash", [path.join(fixture.root, path.relative(root, verifier)), "--source"], {
    cwd: fixture.root, encoding: "utf8", env: fixture.env
  })
  // The clean fixture cannot impersonate the historical dirty candidate.
  // Keep the immutable production verifier and its recorded hashes untouched.
  const sourceGate = verify()
  assert.notStrictEqual(sourceGate.status, 0)
  assert.match(`${sourceGate.stdout}${sourceGate.stderr}`,
    /\[FAIL\] (?:pinned build input|pinned build-input git status) drift: pom\.xml/)
  fs.appendFileSync(path.join(fixture.root, "pom.xml"), "\n<!-- test-only content drift -->\n")
  const hashDrift = verify()
  assert.notStrictEqual(hashDrift.status, 0)
  assert.match(`${hashDrift.stdout}${hashDrift.stderr}`, /\[FAIL\] pinned build input drift: pom\.xml/)
})

console.log(`sign original placement release contract passed (files=${sources.length})`)
