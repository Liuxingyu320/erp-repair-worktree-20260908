const assert = require("assert")
const fs = require("fs")
const path = require("path")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8")

const jobPage = read("src/views/monitor/job/index.vue")
const jobApi = read("src/api/monitor/job.js")
const approvalPage = read("src/views/approval/manage/index.vue")
const approvalUi = read("src/views/approval/manage/components/approvalUi.js")
const flowConfiguration = read("src/views/approval/manage/components/FlowConfiguration.vue")
const runtimeMonitor = read("src/views/approval/manage/components/RuntimeMonitor.vue")
const validationPanel = read("src/views/approval/manage/components/ValidationPanel.vue")
const approvalDefinitionApi = read("src/api/approval/definition.js")
const approvalMonitorApi = read("src/api/approval/monitor.js")
const approvalValidationApi = read("src/api/approval/validation.js")
const todoPage = read("src/views/workbench/todo/index.vue")
const todoStore = read("src/store/modules/todo.js")

assert.ok(
  jobPage.includes("jobListError") &&
    jobPage.includes("retryJobList") &&
    jobPage.includes('role="alert"'),
  "the job page must render a recoverable page-level list failure"
)
assert.ok(
  /getList\(\)[\s\S]*?listJob\([\s\S]*?silentError:\s*true[\s\S]*?\.catch\([\s\S]*?\.finally\(/.test(jobPage),
  "job list failures must be handled locally and always release loading"
)
assert.ok(
  jobPage.includes("writeActionsDisabled") &&
    jobPage.includes(':disabled="writeActionsDisabled"') &&
    jobPage.includes(':disabled="single || writeActionsDisabled"') &&
    jobPage.includes(':disabled="multiple || writeActionsDisabled || deletionPending"'),
  "job mutations must be disabled while the list is loading or failed"
)
assert.ok(
  /handleStatusChange\(row\)[\s\S]*?actionLoading\s*=\s*true[\s\S]*?this\.\$modal\.confirm/.test(jobPage) &&
    /handleRun\(row\)[\s\S]*?actionLoading\s*=\s*true[\s\S]*?this\.\$modal\.confirm/.test(jobPage) &&
    /handleDelete\(row\)[\s\S]*?actionLoading\s*=\s*true[\s\S]*?this\.\$modal\.confirm/.test(jobPage) &&
    /submitForm\(\)[\s\S]*?submitLoading\s*=\s*true[\s\S]*?\.validate/.test(jobPage),
  "job writes must lock before confirmation or validation can be submitted twice"
)
assert.ok(
  /export function listJob\(query,\s*config\s*=\s*\{\}\)/.test(jobApi) &&
    jobApi.includes("silentError: config.silentError === true"),
  "the job list API must allow the page to suppress duplicate global errors"
)

assert.ok(
  approvalPage.includes("visitedTabs") &&
    approvalPage.includes('v-if="visitedTabs.definition"') &&
    approvalPage.includes('v-if="visitedTabs.monitor"') &&
    approvalPage.includes('v-if="visitedTabs.validation"'),
  "approval tabs must mount only after first activation"
)
assert.ok(
  approvalPage.includes("templateLoadError") &&
    approvalPage.includes("retryTemplates") &&
    approvalPage.includes("formatApprovalLoadError"),
  "approval template failures must stay visible and retryable at page level"
)
assert.ok(
  approvalUi.includes("formatApprovalLoadError") &&
    approvalUi.includes("requestId") &&
    approvalUi.includes("status"),
  "approval load errors must expose safe diagnostics without inventing data"
)
assert.ok(
  flowConfiguration.includes("rulesLoadError") &&
    flowConfiguration.includes("retryRules") &&
    runtimeMonitor.includes("sourceErrors") &&
    runtimeMonitor.includes("retrySource") &&
    validationPanel.includes("validationLoadError") &&
    validationPanel.includes("retryValidationRuns"),
  "each approval data source must isolate its own failure and recovery action"
)
for (const [file, source] of [
  ["definition", approvalDefinitionApi],
  ["monitor", approvalMonitorApi],
  ["validation", approvalValidationApi]
]) {
  assert.ok(
    source.includes("silentError: config.silentError === true"),
    `${file} approval reads must support page-owned error presentation`
  )
}

assert.ok(
  todoPage.includes("provider-diagnostics") &&
    todoPage.includes("retryProvider") &&
    todoPage.includes("providerLastSuccessText") &&
    todoPage.includes("providerDiagnosticRows"),
  "the todo page must show provider-level diagnosis, last success, and retry"
)
assert.ok(
  todoPage.includes("networkSources") &&
    todoStore.includes("networkSources") &&
    todoStore.includes("USE_CACHED_PROVIDER_PAGE"),
  "a provider retry must reuse healthy provider caches instead of refetching every source"
)
assert.ok(
  todoPage.includes("failureDiagnostic") &&
    todoPage.includes("requestId") &&
    todoStore.includes("requestId"),
  "todo provider failures must retain safe status and request-id diagnostics"
)

console.log("desktopP0Recovery tests passed")
