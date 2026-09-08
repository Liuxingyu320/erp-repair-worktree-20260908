const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")

function readFile(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), "utf8")
}

function readJson(relativePath) {
  return JSON.parse(readFile(relativePath))
}

function assertFile(relativePath, message) {
  assert.ok(fs.existsSync(path.join(rootDir, relativePath)), message || `${relativePath} should exist`)
}

const packageJson = readJson("package.json")

assertFile("src/utils/apiBaseUrl.js", "native shells should share a pure production API-base resolver")
const apiBaseSource = readFile("src/utils/apiBaseUrl.js")
assert.ok(apiBaseSource.includes("NATIVE_API_ORIGIN_REQUIRED"), "native API-base setup should fail closed with a stable error code")
assert.ok(apiBaseSource.includes('parsedOrigin.protocol !== "https:"'), "native production API origins should require HTTPS")

assert.strictEqual(
  packageJson.scripts["app:sync"],
  "node scripts/harden-capacitor-native-config.cjs && npm run build:prod && NODE_ENV=production npx cap sync && node scripts/harden-capacitor-native-config.cjs && node scripts/verify-capacitor-sync.cjs",
  "app sync script should clean native copies, build the Vue app, sync native platforms, harden generated config, and verify bundled assets"
)

assert.strictEqual(
  packageJson.scripts["app:verify"],
  "node scripts/harden-capacitor-native-config.cjs && npm run test && node scripts/harden-capacitor-native-config.cjs && node scripts/verify-capacitor-sync.cjs",
  "app verification script should clean native copies around the full frontend test gate and native asset sync verification"
)

;["@capacitor/core", "@capacitor/android", "@capacitor/ios"].forEach(dependencyName => {
  assert.ok(
    packageJson.dependencies && packageJson.dependencies[dependencyName],
    `${dependencyName} should be installed as a mobile runtime dependency`
  )
})

assert.ok(
  packageJson.devDependencies && packageJson.devDependencies["@capacitor/cli"],
  "@capacitor/cli should be installed as a development dependency"
)

assertFile("capacitor.config.ts", "Capacitor config should exist at the Vue app root")
const capacitorConfigSource = readFile("capacitor.config.ts")

assert.ok(
  capacitorConfigSource.includes("appId: 'com.erp.mobile'"),
  "Capacitor app id should be stable before native projects are generated"
)

assert.ok(
  capacitorConfigSource.includes("appName: 'ERP Mobile'"),
  "Capacitor app name should identify the ERP mobile shell"
)

assert.ok(
  capacitorConfigSource.includes("webDir: 'dist'"),
  "Capacitor should package the existing Vue production build output"
)

assert.ok(
  capacitorConfigSource.includes("CAPACITOR_SERVER_URL"),
  "Capacitor should support pointing debug builds at an existing web deployment"
)

assert.ok(
  capacitorConfigSource.includes("appendUserAgent: 'ERP-Mobile-App'"),
  "native webviews should advertise that requests are from the ERP mobile app shell"
)

assert.ok(
  !capacitorConfigSource.includes("src/views/mobile"),
  "the app shell config should not modify or depend on mobile page source files"
)

;[
  "android/settings.gradle",
  "android/app/build.gradle",
  "android/app/src/main/AndroidManifest.xml",
  "ios/App/App.xcodeproj/project.pbxproj",
  "ios/App/App/Info.plist"
].forEach(relativePath => {
  assertFile(relativePath, `${relativePath} should be tracked for the native shell`)
})

const verifySyncSource = readFile("scripts/verify-capacitor-sync.cjs")
const androidIgnore = readFile("android/.gitignore")
const iosIgnore = readFile("ios/.gitignore")

assert.ok(androidIgnore.includes("app/src/main/assets/public"))
assert.ok(iosIgnore.includes("App/App/public"))
assert.ok(verifySyncSource.includes("android/app/src/main/assets/public/index.html"))
assert.ok(verifySyncSource.includes("ios/App/App/public/index.html"))
assert.ok(verifySyncSource.includes("dist/index.html"))

const androidStrings = readFile("android/app/src/main/res/values/strings.xml")
assert.ok(
  androidStrings.includes("<string name=\"app_name\">ERP Mobile</string>"),
  "Android display name should match the mobile shell app name"
)

const androidBuildGradle = readFile("android/app/build.gradle")
assert.ok(
  androidBuildGradle.includes(":*.gz'") || androidBuildGradle.includes(":*.gz\""),
  "Android asset packaging should ignore Vue production .gz assets to avoid duplicate resources"
)

const androidInstrumentedTest = readFile(
  "android/app/src/androidTest/java/com/erp/mobile/ExampleInstrumentedTest.java"
)
assert.ok(
  androidInstrumentedTest.includes("package com.erp.mobile;") &&
    androidInstrumentedTest.includes('assertEquals("com.erp.mobile", appContext.getPackageName())'),
  "Android instrumented test should use the same package id as the native application"
)

const iosInfo = readFile("ios/App/App/Info.plist")
assert.ok(
  iosInfo.includes("<string>ERP Mobile</string>"),
  "iOS bundle display name should match the mobile shell app name"
)
