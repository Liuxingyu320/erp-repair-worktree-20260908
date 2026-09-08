const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const configPaths = [
  "android/app/src/main/res/xml/config.xml",
  "ios/App/App/config.xml"
]
const nativeConfigDirs = [
  path.join(uiRoot, "android/app/src/main/res/xml"),
  path.join(uiRoot, "ios/App/App")
]
const generatedNativeRoots = [
  path.join(uiRoot, "android/app/build"),
  path.join(uiRoot, "android/app/src/main/assets"),
  path.join(uiRoot, "android/capacitor-cordova-android-plugins"),
  path.join(uiRoot, "ios/App/App/public"),
  path.join(uiRoot, "ios/capacitor-cordova-ios-plugins")
]
const generatedNativeSiblingRules = [
  {
    directory: path.join(uiRoot, "android"),
    pattern: /^capacitor-cordova-android-plugins \d+$/
  },
  {
    directory: path.join(uiRoot, "ios"),
    pattern: /^capacitor-cordova-ios-plugins \d+$/
  },
  {
    directory: path.join(uiRoot, "ios/App/App"),
    pattern: /^public \d+$/
  }
]
const numberedCopyPattern = / \d+(?=\.|$)/
const removedArtifacts = []

function removeArtifact(absolutePath, isDirectory) {
  fs.rmSync(absolutePath, { recursive: isDirectory, force: true })
  removedArtifacts.push(path.relative(uiRoot, absolutePath))
}

function removeNumberedCopies(directory) {
  if (!fs.existsSync(directory)) return

  fs.readdirSync(directory, { withFileTypes: true }).forEach(entry => {
    const absolutePath = path.join(directory, entry.name)
    if (numberedCopyPattern.test(entry.name)) {
      removeArtifact(absolutePath, entry.isDirectory())
      return
    }
    if (entry.isDirectory()) removeNumberedCopies(absolutePath)
  })
}

generatedNativeRoots.forEach(removeNumberedCopies)

generatedNativeSiblingRules.forEach(({ directory, pattern }) => {
  if (!fs.existsSync(directory)) return

  fs.readdirSync(directory, { withFileTypes: true })
    .filter(entry => pattern.test(entry.name))
    .forEach(entry => removeArtifact(path.join(directory, entry.name), entry.isDirectory()))
})

nativeConfigDirs.forEach(configDir => {
  if (!fs.existsSync(configDir)) return
  fs.readdirSync(configDir, { withFileTypes: true })
    .filter(entry => /^config \d+\.xml$/.test(entry.name))
    .forEach(entry => {
      removeArtifact(path.join(configDir, entry.name), entry.isDirectory())
    })
})

configPaths.forEach(relativePath => {
  const absolutePath = path.join(uiRoot, relativePath)
  if (!fs.existsSync(absolutePath)) {
    return
  }

  const source = fs.readFileSync(absolutePath, "utf8")
  const hardened = source
    .replace(/\s*<access\s+origin=["']\*["']\s*\/>\s*/g, "\n")
    .replace(/\n{3,}/g, "\n")

  if (hardened !== source) {
    fs.writeFileSync(absolutePath, hardened, "utf8")
  }
})

if (removedArtifacts.length) {
  console.log(`Removed ${removedArtifacts.length} numbered native artifact(s):`)
  removedArtifacts.sort().forEach(relativePath => console.log(`- ${relativePath}`))
}

console.log("Capacitor native config hardened")
