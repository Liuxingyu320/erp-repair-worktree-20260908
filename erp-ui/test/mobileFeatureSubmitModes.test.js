const assert = require("assert")
const fs = require("fs")
const path = require("path")

const actionRuntimeSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionRuntime.js"),
  "utf8"
)

;[
  "submitAction === \"submit\"",
  "call(\"submitSales\", payload)",
  "call(\"submitPurchase\", payload)",
  "call(\"submitSalesReturn\", payload)",
  "call(\"submitPurchaseReturn\", payload)",
  "call(\"submitTransfer\", payload)",
  "call(\"submitOaPurchaseApi\", payload)"
].forEach(expected => {
  assert.ok(actionRuntimeSource.includes(expected), `feature action runtime should include ${expected}`)
})

const pageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const policySource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/mobileFeaturePolicy.js"),
  "utf8"
)

const formSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileFormSheet.vue"),
  "utf8"
)

assert.ok(
  pageSource.includes("@submit=\"submitMobileForm\"") &&
    pageSource.includes("MOBILE_FORM_SUBMIT_PERMISSIONS") &&
    policySource.includes("inv:transfer:add") &&
    policySource.includes("inv:sales:add") &&
    policySource.includes("inv:purchase:add") &&
    policySource.includes("inv:salesReturn:add") &&
    policySource.includes("inv:purchaseReturn:add") &&
    policySource.includes("inv:stockCheck:add") &&
    policySource.includes("oa:purchase:add") &&
    policySource.includes("oa:fixedAsset:repair:add") &&
    pageSource.includes("availableMobileSubmitModes") &&
    pageSource.includes("submitAction") &&
    pageSource.includes("保存并提交"),
  "mobile feature page should pass permitted selected submit modes and permission-gate every mobile business form"
)

assert.ok(
  pageSource.includes("MOBILE_ACTION_PERMISSIONS") &&
    policySource.includes("calculateSalary") &&
    policySource.includes("oa:salary:calculate") &&
    pageSource.includes("canShowTopAction(action)") &&
    pageSource.includes("this.hasAnyPermission(action.permissions || MOBILE_ACTION_PERMISSIONS[action.actionId])"),
  "mobile top actions such as salary calculation should be hidden without explicit permissions"
)

assert.ok(
  formSheetSource.includes("primarySubmitAction") &&
    formSheetSource.includes('mode.action === this.primarySubmitAction ? "primary" : "secondary"') &&
    formSheetSource.includes('class="tertiary"') &&
    formSheetSource.includes("return this.primarySubmitAction") &&
    !/v-for="mode in effectiveSubmitModes"[^>]*class="primary"/.test(formSheetSource),
  "the final permitted submit mode should be the sole primary action, while draft and cancel remain secondary/tertiary"
)

assert.ok(
  formSheetSource.includes('mode.action === "submit"') &&
    formSheetSource.includes("this.effectiveSubmitModes[this.effectiveSubmitModes.length - 1]") &&
    formSheetSource.includes('@submit.prevent="emitSubmit(defaultSubmitAction)"') &&
    formSheetSource.includes("novalidate") &&
    formSheetSource.includes(":type=\"mode.action === primarySubmitAction ? 'submit' : 'button'\"") &&
    formSheetSource.includes('@click="handleSubmitModeClick(mode)"'),
  "Enter should prefer submit, or the final/only permitted mode when submit is unavailable"
)
