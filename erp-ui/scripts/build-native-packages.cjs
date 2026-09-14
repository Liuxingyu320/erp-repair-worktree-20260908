const fs = require("fs")
const path = require("path")
const crypto = require("crypto")
const { spawnSync } = require("child_process")
const { resolveNativeApiOrigin, validateNativeApiOrigin } = require("./validate-native-api-origin.cjs")

const uiRoot = path.resolve(__dirname, "..")
const repositoryRoot = path.resolve(uiRoot, "..")
const platformArgument = process.argv.find(argument => argument.startsWith("--platform="))
const platform = platformArgument ? platformArgument.slice("--platform=".length) : "all"
if (!["all", "android", "ios"].includes(platform)) throw new Error("platform must be all, android, or ios")
const origin = validateNativeApiOrigin(resolveNativeApiOrigin({ env: process.env }))
if (!origin.ok) throw new Error(origin.message)
const nodeMajor = Number(process.versions.node.split(".")[0])
if (nodeMajor < 22 || nodeMajor > 24) throw new Error("Use Node.js 22, 23 or 24 for this project")

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: uiRoot, env: process.env, stdio: "inherit", ...options })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(command + " failed; no completed package manifest was produced")
  return result.stdout
}
function git(args) {
  return run("git", args, { cwd: repositoryRoot, stdio: ["ignore", "pipe", "inherit"] }).toString()
}
function sourceSnapshot() {
  const hash = crypto.createHash("sha256")
  const files = git(["ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "erp-ui"]).split("\0")
  const generated = new Set(["erp-ui/android/capacitor.settings.gradle", "erp-ui/android/app/capacitor.build.gradle",
    "erp-ui/ios/App/CapApp-SPM/Package.swift",
    "erp-ui/ios/App/App.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"])
  for (const file of [...new Set(files.filter(Boolean))].sort()) {
    if (generated.has(file) || file.startsWith("erp-ui/test/")) continue
    const target = path.join(repositoryRoot, file)
    hash.update(file + "\0")
    hash.update(fs.existsSync(target) && fs.statSync(target).isFile() ? fs.readFileSync(target) : "<deleted>")
    hash.update("\0")
  }
  return hash.digest("hex")
}
const startedAt = new Date().toISOString()
const snapshot = sourceSnapshot()
process.env.VUE_APP_BUILD_COMMIT = git(["rev-parse", "HEAD"]).trim()
process.env.VUE_APP_BUILD_TIME = startedAt
process.env.VUE_APP_NATIVE_API_ORIGIN = origin.origin
process.env.NODE_ENV = "production"
const stamp = startedAt.replace(/[:.]/g, "-")
const output = path.join(uiRoot, "build/mobile-packages", stamp)
fs.mkdirSync(output, { recursive: true })
run("npm", ["run", "app:sync"])
const manifest = {
  createdAt: startedAt,
  baseCommit: process.env.VUE_APP_BUILD_COMMIT,
  sourceSnapshotSha256: snapshot,
  includesWorkingTreeChanges: git(["status", "--porcelain"]).trim().length > 0,
  apiOrigin: origin.origin,
  bundleId: "com.erp.mobile",
  httpsConnectivityVerified: false,
  pushDeliveryVerified: false,
  productionReady: false,
  artifacts: [],
}
function artifact(name, source, role) {
  const destination = path.join(output, name)
  fs.copyFileSync(source, destination)
  manifest.artifacts.push({
    file: name, role,
    sha256: crypto.createHash("sha256").update(fs.readFileSync(destination)).digest("hex")
  })
}
if (platform === "all" || platform === "android") {
  run(path.join(uiRoot, "android/gradlew"),
    ["-p", path.join(uiRoot, "android"), ":app:assembleDebug", ":app:assembleRelease", "--console=plain"])
  artifact("ERP-Mobile-Android-TEST.apk",
    path.join(uiRoot, "android/app/build/outputs/apk/debug/app-debug.apk"), "debug-signed test package")
  artifact("ERP-Mobile-Android-UNSIGNED.apk",
    path.join(uiRoot, "android/app/build/outputs/apk/release/app-release-unsigned.apk"), "requires release signing")
  manifest.androidFcmConfigPresent = fs.existsSync(path.join(uiRoot, "android/app/google-services.json"))
}
if (platform === "all" || platform === "ios") {
  const derived = path.join(output, "ios-derived")
  run("xcodebuild", ["-project", "ios/App/App.xcodeproj", "-scheme", "App", "-configuration", "Release",
    "-sdk", "iphoneos", "-destination", "generic/platform=iOS", "-derivedDataPath", derived,
    "CODE_SIGNING_ALLOWED=NO", "CODE_SIGNING_REQUIRED=NO", "build"])
  const payload = path.join(output, "ios-unsigned/Payload")
  fs.mkdirSync(payload, { recursive: true })
  run("ditto", [path.join(derived, "Build/Products/Release-iphoneos/App.app"), path.join(payload, "App.app")])
  const ipa = path.join(output, "ERP-Mobile-iOS-UNSIGNED.ipa")
  run("ditto", ["-c", "-k", "--keepParent", payload, ipa])
  manifest.artifacts.push({ file: path.basename(ipa), role: "not installable until signed with a push-enabled profile",
    sha256: crypto.createHash("sha256").update(fs.readFileSync(ipa)).digest("hex") })
}
if (sourceSnapshot() !== snapshot) {
  throw new Error("Source files changed during packaging; rebuild after edits settle")
}
const nativeLock = path.join(uiRoot, "ios/App/App.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
if (fs.existsSync(nativeLock)) {
  manifest.nativeDependencyLockSha256 = crypto.createHash("sha256").update(fs.readFileSync(nativeLock)).digest("hex")
}
fs.writeFileSync(path.join(output, "package-manifest.json"), JSON.stringify(manifest, null, 2) + "\n")
fs.writeFileSync(path.join(output, "READ-ME.txt"),
  "Local build candidates only. HTTPS connectivity and physical-device push delivery are NOT verified.\n" +
  "Android TEST is debug-signed. Android UNSIGNED and iOS UNSIGNED require release signing.\n" +
  "Validate the final signed iOS IPA with scripts/verify_ios_push_ipa.py before device acceptance.\n")
console.log("Native build candidates: " + output)
