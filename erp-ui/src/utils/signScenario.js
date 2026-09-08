const SIGN_SCENARIOS = Object.freeze([
  { taskValue: "ONBOARD", packageValue: "onboard", label: "入职" },
  { taskValue: "RENEWAL", packageValue: "renewal", label: "续签" },
  { taskValue: "TRANSFER", packageValue: "transfer", label: "调岗" },
  { taskValue: "REGULARIZE", packageValue: "regularize", label: "转正" },
  { taskValue: "OFFBOARD", packageValue: "offboard", label: "离职" }
])

const SIGN_TASK_SCENARIO_OPTIONS = Object.freeze(
  SIGN_SCENARIOS.map(item => Object.freeze({ value: item.taskValue, label: item.label }))
)

const SIGN_PACKAGE_SCENARIO_OPTIONS = Object.freeze(
  SIGN_SCENARIOS.map(item => Object.freeze({ value: item.packageValue, label: item.label }))
)

function normalizeTaskScenario(value) {
  const normalized = String(value || "").trim().toUpperCase()
  return normalized === "RENEW" ? "RENEWAL" : normalized
}

function normalizePackageScenario(value) {
  return normalizeTaskScenario(value).toLowerCase()
}

function packageScenarioHasEntryDate(value) {
  return normalizePackageScenario(value) === "onboard"
}

function packageScenarioHasContractDates(value) {
  return ["onboard", "renewal"].includes(normalizePackageScenario(value))
}

function packageScenarioHasProbationDates(value) {
  return normalizePackageScenario(value) === "onboard"
}

function packageScenarioHasSalary(value) {
  return ["onboard", "renewal", "transfer", "regularize"].includes(normalizePackageScenario(value))
}

function signScenarioLabel(value) {
  const normalized = normalizeTaskScenario(value)
  const scenario = SIGN_SCENARIOS.find(item => item.taskValue === normalized)
  if (scenario) return scenario.label
  if (normalized === "CHANGE") return "合同变更"
  if (!value) return "-"
  return /[A-Za-z]/.test(String(value)) ? "未登记签约场景" : value
}

module.exports = {
  SIGN_TASK_SCENARIO_OPTIONS,
  SIGN_PACKAGE_SCENARIO_OPTIONS,
  normalizeTaskScenario,
  normalizePackageScenario,
  packageScenarioHasEntryDate,
  packageScenarioHasContractDates,
  packageScenarioHasProbationDates,
  packageScenarioHasSalary,
  signScenarioLabel
}
