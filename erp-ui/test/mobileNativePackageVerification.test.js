const assert = require("assert")
const fs = require("fs")
const os = require("os")
const path = require("path")
const { spawnSync } = require("child_process")

const root = fs.mkdtempSync(path.join(os.tmpdir(), "erp-native-package-"))
function write(relative, contents) {
  const file = path.join(root, relative)
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, contents)
}
function run(...args) {
  return spawnSync(process.execPath, [path.join(root, "scripts/verify-capacitor-sync.cjs"), ...args], {
    encoding: "utf8",
    env: { ...process.env, NODE_PATH: path.resolve(__dirname, "../node_modules"),
      VUE_APP_NATIVE_API_ORIGIN: "https://erp.example.test" }
  })
}
try {
  for (const script of ["verify-capacitor-sync.cjs", "validate-native-api-origin.cjs"]) {
    write("scripts/" + script, fs.readFileSync(path.join(__dirname, "../scripts", script)))
  }
  write("package.json", '{"dependencies":{"@capacitor/push-notifications":"8.1.1"}}')
  write("node_modules/@capacitor/push-notifications/package.json", "{}")
  write("android/app/src/main/AndroidManifest.xml", "android.permission.POST_NOTIFICATIONS")
  write("android/capacitor.settings.gradle", "capacitor-push-notifications")
  write("ios/App/App/AppDelegate.swift", "capacitorDidRegisterForRemoteNotifications")
  write("ios/App/App/App.entitlements", "aps-environment")
  write("ios/App/App.xcodeproj/project.pbxproj", "CODE_SIGN_ENTITLEMENTS = App/App.entitlements")
  write("ios/App/CapApp-SPM/Package.swift", "CapacitorPushNotifications")
  for (const dir of ["dist", "android/app/src/main/assets/public", "ios/App/App/public"]) {
    write(dir + "/index.html", '<script src="/static/js/app.js"></script>')
    write(dir + "/static/js/app.js", "window.erp = true")
    write(dir + "/static/css/mobile.css", "body { margin: 0 }")
  }
  for (const dir of ["android/app/src/main/assets", "ios/App/App"]) {
    write(dir + "/capacitor.config.json", JSON.stringify({ loggingBehavior: "none" }))
  }

  let result = run("--release", "--platform=ios")
  assert.strictEqual(result.status, 0, "direct APNs builds must not require a Firebase iOS plist: " + result.stderr)
  result = run("--release", "--platform=android")
  assert.notStrictEqual(result.status, 0, "FCM Android release must require its configuration")
  assert.ok(result.stderr.includes("google-services.json"))
  write("android/app/google-services.json", JSON.stringify({
    project_info: { project_id: "erp" },
    client: [{ client_info: { android_client_info: { package_name: "com.other.app" } } }]
  }))
  assert.notStrictEqual(run("--release", "--platform=android").status, 0,
    "a different signer's Android application ID must fail")
  write("android/app/google-services.json", JSON.stringify({
    project_info: { project_id: "erp" },
    client: [{ client_info: { android_client_info: { package_name: "com.erp.mobile" } } }]
  }))
  assert.strictEqual(run("--release").status, 0)

  write("ios/App/App/public/static/css/mobile.css", "stale bundled bytes")
  result = run("--platform=ios")
  assert.notStrictEqual(result.status, 0, "same asset filenames must not hide stale file content")
  assert.ok(result.stderr.includes("content mismatch"))
  write("ios/App/App/public/static/css/mobile.css", "body { margin: 0 }")
  write("ios/App/App/capacitor.config.json", JSON.stringify({
    loggingBehavior: "none", server: { url: "https://dev.example.test" }
  }))
  assert.notStrictEqual(run("--release", "--platform=ios").status, 0,
    "a native development-server package must not pass release verification")
  console.log("native package verification passed")
} finally {
  fs.rmSync(root, { recursive: true, force: true })
}
