const fs = require("fs")
const path = require("path")
const {
  readOnboardReleaseMetadata
} = require("./validate-onboard-release-build.cjs")

const CANDIDATE_COMMIT_PATTERN = /^[0-9a-f]{40}$/

function createOnboardReleaseProvenance(environment = process.env) {
  const metadata = readOnboardReleaseMetadata(environment)
  const candidateCommit = environment.VUE_APP_BUILD_COMMIT || ""
  if (!CANDIDATE_COMMIT_PATTERN.test(candidateCommit)) {
    throw new Error(
      "VUE_APP_BUILD_COMMIT must be a full 40-character lowercase Git SHA"
    )
  }

  return {
    schemaVersion: 1,
    releaseId: metadata.releaseId,
    signExcelImportEnabled: metadata.signExcelImportEnabled,
    candidateCommit,
    approvedPatchSha256: metadata.approvedPatchSha256,
    approvedSourceManifestSha256: metadata.approvedSourceManifestSha256
  }
}

function writeOnboardReleaseProvenance(distDirectory, environment = process.env) {
  const provenance = createOnboardReleaseProvenance(environment)
  const indexPath = path.resolve(distDirectory, "index.html")
  if (!fs.existsSync(indexPath)) {
    throw new Error(
      "refusing to write release-provenance.json before a frontend build exists"
    )
  }

  const outputPath = path.resolve(distDirectory, "release-provenance.json")
  fs.writeFileSync(outputPath, `${JSON.stringify(provenance, null, 2)}\n`, {
    encoding: "utf8",
    mode: 0o644
  })
  return provenance
}

function main() {
  const root = path.resolve(__dirname, "..")
  try {
    writeOnboardReleaseProvenance(path.resolve(root, "dist"), process.env)
    console.log("frontend release-provenance.json written")
  } catch (error) {
    console.error(`refusing to write onboard release provenance: ${error.message}`)
    process.exitCode = 1
  }
}

if (require.main === module) {
  main()
}

module.exports = {
  createOnboardReleaseProvenance,
  writeOnboardReleaseProvenance
}
