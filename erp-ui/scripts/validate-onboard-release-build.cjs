const RELEASE_ID = "erp-current-release-20260722"
const SHA256_PATTERN = /^[0-9a-f]{64}$/
const ZERO_SHA256_PATTERN = /^0{64}$/

const ENVIRONMENT_VARIABLES = Object.freeze({
  releaseId: "VUE_APP_BUILD_RELEASE_ID",
  approvedPatchSha256: "VUE_APP_BUILD_APPROVED_PATCH_SHA256",
  approvedSourceManifestSha256: "VUE_APP_BUILD_APPROVED_SOURCE_MANIFEST_SHA256",
  signExcelImportEnabled: "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED"
})

function requireApprovedSha256(value, environmentVariable) {
  if (!SHA256_PATTERN.test(value) || ZERO_SHA256_PATTERN.test(value)) {
    throw new Error(
      `${environmentVariable} must be a non-zero 64-character lowercase SHA-256`
    )
  }
  return value
}

function readOnboardReleaseMetadata(environment = process.env) {
  const releaseId = environment[ENVIRONMENT_VARIABLES.releaseId] || ""
  if (releaseId !== RELEASE_ID) {
    throw new Error(
      `${ENVIRONMENT_VARIABLES.releaseId} must equal the fixed release ID ${RELEASE_ID}`
    )
  }
  if (environment[ENVIRONMENT_VARIABLES.signExcelImportEnabled] !== "true") {
    throw new Error(
      `${ENVIRONMENT_VARIABLES.signExcelImportEnabled} must equal the exact string true for the approved gray/release artifact`
    )
  }

  return Object.freeze({
    releaseId,
    signExcelImportEnabled: true,
    approvedPatchSha256: requireApprovedSha256(
      environment[ENVIRONMENT_VARIABLES.approvedPatchSha256] || "",
      ENVIRONMENT_VARIABLES.approvedPatchSha256
    ),
    approvedSourceManifestSha256: requireApprovedSha256(
      environment[ENVIRONMENT_VARIABLES.approvedSourceManifestSha256] || "",
      ENVIRONMENT_VARIABLES.approvedSourceManifestSha256
    )
  })
}

function main() {
  try {
    const metadata = readOnboardReleaseMetadata(process.env)
    console.log(`onboard contract release metadata validated: releaseId=${metadata.releaseId}`)
  } catch (error) {
    console.error(`onboard contract release metadata invalid: ${error.message}`)
    process.exitCode = 1
  }
}

if (require.main === module) {
  main()
}

module.exports = {
  ENVIRONMENT_VARIABLES,
  RELEASE_ID,
  readOnboardReleaseMetadata
}
