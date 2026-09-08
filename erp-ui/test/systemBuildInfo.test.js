const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
const readRepo = relativePath => fs.readFileSync(path.resolve(repoRoot, relativePath), "utf8")

const settingsSource = readUi("src/settings.js")
const userStoreSource = readUi("src/store/modules/user.js")
const navbarSource = readUi("src/layout/components/Navbar.vue")
const validateBuildSource = readUi("scripts/validate-release-build.cjs")
const writeReleaseInfoSource = readUi("scripts/write-release-info.cjs")
const packageJson = JSON.parse(readUi("package.json"))
const controllerSource = readRepo(
  "erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java"
)

assert.ok(
  settingsSource.includes("VUE_APP_BUILD_COMMIT") &&
    settingsSource.includes("VUE_APP_BUILD_TIME") &&
    settingsSource.includes("|| 'UNSET'"),
  "frontend release diagnostics must come from explicit build-time variables with a fail-visible default"
)

assert.ok(
  controllerSource.includes('ajax.put("systemBuild"') &&
    controllerSource.includes("SysBuildInfoVo.unavailable()") &&
    controllerSource.includes("systemBuildInfoProvider.current()"),
  "authenticated user bootstrap must expose the safe system build contract"
)

assert.ok(
  userStoreSource.includes("systemBuild: { commit: 'UNSET', buildTime: 'UNSET', version: 'UNSET' }") &&
    userStoreSource.includes("commit('SET_SYSTEM_BUILD', res.systemBuild)"),
  "user store must retain system build metadata without treating missing data as a valid release"
)

assert.ok(
  navbarSource.includes("版本信息") &&
    navbarSource.includes("前后端版本不一致") &&
    navbarSource.includes("/^[0-9a-f]{40}$/") &&
    navbarSource.includes("copyVersionInfo") &&
    navbarSource.includes("system.version="),
  "navbar must expose copyable release diagnostics and warn only for two valid, different commit hashes"
)

assert.ok(
  packageJson.scripts["prebuild:prod"].includes("validate-release-build.cjs") &&
    packageJson.scripts["postbuild:prod"].includes("write-release-info.cjs") &&
    validateBuildSource.includes("^[0-9a-f]{40}$") &&
    writeReleaseInfoSource.includes('release-info.json') &&
    writeReleaseInfoSource.includes("JSON.stringify({ commit, buildTime, todoQuickApproveEnabled, todoBatchApproveEnabled }") ,
  "production builds must reject missing release metadata and emit a machine-verifiable release marker"
)

console.log("systemBuildInfo tests passed")
