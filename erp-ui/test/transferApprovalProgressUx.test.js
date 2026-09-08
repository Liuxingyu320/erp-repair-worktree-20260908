const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")

const transferApi = read("src/api/inventory/transfer.js")
const component = read("src/views/inventory/transfer/components/TransferApprovalProgress.vue")
const indexPage = read("src/views/inventory/transfer/index.vue")
const recordsPage = read("src/views/inventory/transfer/records.vue")

assert.ok(
  transferApi.includes("getTransferApprovalTrack") && transferApi.includes("/approval-track"),
  "transfer API should expose the approval-track read endpoint"
)

assert.ok(
  component.includes("candidateDisplayNames") &&
    component.includes("actualApproverDisplayName") &&
    component.includes("approvalModeText") &&
    component.includes("候选审批人") &&
    component.includes("实际审批人"),
  "shared approval progress should distinguish candidates from actual approvers"
)

assert.ok(
  indexPage.includes("approvalSummary") &&
    indexPage.includes("TransferApprovalProgress") &&
    indexPage.includes("业务履约进度"),
  "processing page should render list summaries and both approval/business progress"
)

assert.ok(
  recordsPage.includes("TransferApprovalProgress") && recordsPage.includes("approvalSummary"),
  "records page should reuse the same approval progress and summary model"
)

for (const [label, source] of [["processing", indexPage], ["records", recordsPage]]) {
  assert.ok(
    source.includes("approvalTrackError") &&
      source.includes("loadApprovalTrack") &&
      source.includes("getTransferApprovalTrack") &&
      source.includes(".catch(() =>") &&
      source.includes("approvalTrackError = true"),
    `${label} detail should isolate approval-track failures from the main detail request`
  )
}

console.log("transferApprovalProgressUx tests passed")
