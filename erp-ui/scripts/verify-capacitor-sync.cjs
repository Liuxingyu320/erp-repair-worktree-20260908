const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const releaseMode = process.argv.includes("--release")

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

const credentialFiles = [
  "android/app/google-services.json",
  "ios/App/App/GoogleService-Info.plist"
]
const missingCredentials = credentialFiles.filter(file => !fs.existsSync(path.join(uiRoot, file)))
if (releaseMode) {
  missingCredentials.forEach(file => pushErrors.push(`release credential missing: ${file}`))
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

targets.forEach(target => {
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
