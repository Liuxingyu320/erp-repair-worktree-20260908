const assert = require("assert")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

const capabilityNames = [
  "healthCertificate",
  "storeReturn",
  "transferDiscrepancy",
  "customerServiceCard"
]
const configKeys = [
  "feature.hr.health-certificate.enabled",
  "feature.inventory.store-return.enabled",
  "feature.inventory.transfer-discrepancy.enabled",
  "feature.inventory.customer-service-card.enabled"
]
const retiredCapabilityNames = [
  "team",
  "oaOeReplenishment",
  "inventoryOeReplenishment"
]
const retiredConfigKeys = [
  "feature.hr.team.enabled",
  "feature.oa.oe-replenishment.enabled",
  "feature.inventory.oe-replenishment.enabled"
]

const systemGate = read("erp-modules/erp-system/src/main/java/com/erp/system/service/BusinessFeatureGate.java")
const loginController = read("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java")
const userStore = read("erp-ui/src/store/modules/user.js")
const routes = read("erp-ui/src/views/mobile/mobileRouteDefinitions.js")
const fixedAssetService = read("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java")

configKeys.forEach(configKey => {
  assert.ok(systemGate.includes(`"${configKey}"`), `${configKey} must be represented in the login capability gate`)
})
capabilityNames.forEach(capability => {
  assert.ok(systemGate.includes(`result.put("${capability}"`), `${capability} must be exposed by the login response`)
  assert.ok(userStore.includes(`${capability}: false`), `${capability} must default to false in the browser`)
})
retiredConfigKeys.forEach(configKey => {
  assert.ok(!systemGate.includes(configKey), `${configKey} must not remain in the active capability contract`)
})
retiredCapabilityNames.forEach(capability => {
  assert.ok(!userStore.includes(`${capability}: false`), `${capability} must not remain in browser capability state`)
})

assert.ok(loginController.includes("putBusinessFeatureState(ajax)"), "getInfo must return business feature capabilities")
assert.ok(userStore.includes("features[key] === true"), "the browser must accept only an explicit boolean true")
assert.ok(userStore.includes("commit('SET_BUSINESS_FEATURES', null)"), "login and logout flows must clear stale capabilities")
assert.ok(!routes.includes("/mobile/hr/team"), "the retired HR team route must be absent")
assert.ok(!routes.includes("/mobile/oe-replenishment"), "the retired OE replenishment route must be absent")
assert.ok(!fixedAssetService.includes("enqueueReplenishment"), "fixed-asset reports must not create a second replenishment workflow")

console.log("newBusinessFeatureFlags tests passed")
