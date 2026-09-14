const fs = require("fs")
const path = require("path")
const crypto = require("crypto")

const uiRoot = path.resolve(__dirname, "..")
const releaseMode = process.argv.includes("--release")
const platformArgument = process.argv.find(argument => argument.startsWith("--platform="))
const platform = platformArgument ? platformArgument.slice("--platform=".length) : "all"
if (!["all", "android", "ios"].includes(platform)) {
  console.error("platform must be all, android, or ios")
  process.exit(1)
}

const pushErrors = []

function requireText(relativePath, expectedText) {
  const filePath = path.join(uiRoot, relativePath)
  if (!fs.existsSync(filePath)) {
    pushErrors.push(`missing ${relativePath}`)
    return
  }
  if (!fs.readFileSync(filePath, "utf8").includes(expectedText)) {
    pushErrors.push(`${relativePath} does not contain ${expectedText}`)
  }
}

requireText("package.json", "@capacitor/push-notifications")
requireText("android/app/src/main/AndroidManifest.xml", "android.permission.POST_NOTIFICATIONS")
requireText("ios/App/App/AppDelegate.swift", "capacitorDidRegisterForRemoteNotifications")
requireText("ios/App/App/App.entitlements", "aps-environment")
requireText("ios/App/App.xcodeproj/project.pbxproj", "CODE_SIGN_ENTITLEMENTS = App/App.entitlements")
requireText("android/capacitor.settings.gradle", "capacitor-push-notifications")
requireText("ios/App/CapApp-SPM/Package.swift", "CapacitorPushNotifications")

if (!fs.existsSync(path.join(uiRoot, "node_modules/@capacitor/push-notifications/package.json"))) {
  pushErrors.push("node_modules/@capacitor/push-notifications is not installed")
}

// iOS uses APNs directly: GoogleService-Info.plist is neither needed nor a
// substitute for the final signed app's aps-environment entitlement.
const credentialFiles = platform === "ios" ? [] : ["android/app/google-services.json"]
const missingCredentials = credentialFiles.filter(file => !fs.existsSync(path.join(uiRoot, file)))
if (releaseMode) {
  missingCredentials.forEach(file => pushErrors.push(`release credential missing: ${file}`))
  const { validateNativeApiOrigin, resolveNativeApiOrigin } = require("./validate-native-api-origin.cjs")
  if (!validateNativeApiOrigin(resolveNativeApiOrigin({ env: process.env })).ok) {
    pushErrors.push("release requires a configured HTTPS native API origin")
  }
  if (!missingCredentials.length && platform !== "ios") {
    try {
      const google = JSON.parse(fs.readFileSync(path.join(uiRoot, credentialFiles[0]), "utf8"))
      const matches = (google.client || []).some(client =>
        client.client_info && client.client_info.android_client_info &&
        client.client_info.android_client_info.package_name === "com.erp.mobile")
      if (!matches || !google.project_info || !google.project_info.project_id) {
        pushErrors.push("Android FCM configuration must match com.erp.mobile and contain a project ID")
      }
    } catch (error) {
      pushErrors.push("Android FCM configuration is not valid JSON")
    }
  }
} else if (missingCredentials.length) {
  console.warn(`[mobile-push] native credentials not present; web verification continues: ${missingCredentials.join(", ")}`)
}

if (pushErrors.length) {
  console.error("[mobile-push] Capacitor verification failed:")
  pushErrors.forEach(error => console.error(`- ${error}`))
  process.exit(1)
}

const targets = [
  {
    name: "Android",
    indexPath: path.join(uiRoot, "android/app/src/main/assets/public/index.html")
  },
  {
    name: "iOS",
    indexPath: path.join(uiRoot, "ios/App/App/public/index.html")
  }
]

function readFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`${path.relative(uiRoot, filePath)} does not exist`)
  }
  return fs.readFileSync(filePath, "utf8")
}

function extractAssetRefs(html) {
  const refs = new Set()
  const pattern = /(?:src|href)=["']?([^"'\s>]+)/g
  let match

  while ((match = pattern.exec(html))) {
    const ref = match[1]
    if (/^(?:static\/|\/static\/)/.test(ref)) {
      refs.add(ref.replace(/^\//, ""))
    }
  }

  return Array.from(refs).sort()
}

function diffRefs(expected, actual) {
  const actualSet = new Set(actual)
  return expected.filter(ref => !actualSet.has(ref))
}

const distIndexPath = path.join(uiRoot, "dist/index.html")
const distRefs = extractAssetRefs(readFile(distIndexPath))

if (!distRefs.length) {
  throw new Error("dist/index.html does not contain static asset references; run a production build before syncing")
}

const failures = []

function verifyCopiedFiles(source, destination, relative = "") {
  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const child = path.join(relative, entry.name)
    const sourcePath = path.join(source, entry.name)
    const destinationPath = path.join(destination, entry.name)
    if (entry.isDirectory()) {
      if (!fs.existsSync(destinationPath) || !fs.statSync(destinationPath).isDirectory()) {
        throw new Error(`copied web asset directory missing: ${child}`)
      }
      verifyCopiedFiles(sourcePath, destinationPath, child)
    } else if (entry.isFile()) {
      const hash = file => crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex")
      if (!fs.existsSync(destinationPath) || hash(sourcePath) !== hash(destinationPath)) {
        throw new Error(`copied web asset content mismatch: ${child}`)
      }
    }
  }
}

targets.filter(target => platform === "all" || target.name.toLowerCase() === platform).forEach(target => {
  const refs = extractAssetRefs(readFile(target.indexPath))
  const missing = diffRefs(distRefs, refs)
  const extra = diffRefs(refs, distRefs)

  if (missing.length || extra.length) {
    failures.push({
      target: target.name,
      missing,
      extra
    })
  }
  try {
    verifyCopiedFiles(path.join(uiRoot, "dist"), path.dirname(target.indexPath))
    if (releaseMode) {
      const configPath = target.name === "Android"
        ? "android/app/src/main/assets/capacitor.config.json"
        : "ios/App/App/capacitor.config.json"
      const config = JSON.parse(readFile(path.join(uiRoot, configPath)))
      if ((config.server && (config.server.url || config.server.cleartext)) ||
          config.loggingBehavior !== "none") {
        throw new Error("release native config must bundle web assets with debug logging disabled")
      }
    }
  } catch (error) {
    failures.push({ target: target.name, missing: [error.message], extra: [] })
  }
})

if (failures.length) {
  console.error("Capacitor bundled web assets are not synced with dist:")
  failures.forEach(failure => {
    console.error(`- ${failure.target}`)
    if (failure.missing.length) {
      console.error(`  missing: ${failure.missing.join(", ")}`)
    }
    if (failure.extra.length) {
      console.error(`  stale: ${failure.extra.join(", ")}`)
    }
  })
  process.exit(1)
}

console.log("Capacitor bundled web assets match dist")
if (releaseMode && platform !== "android") {
  console.log("iOS source checks passed; verify the final signed IPA and test APNs on a physical device before distribution")
}
