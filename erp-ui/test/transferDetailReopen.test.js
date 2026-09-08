const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const transferSource = read("src/views/inventory/transfer/index.vue")
const transferApiSource = read("src/api/inventory/transfer.js")
const motionSource = read("src/assets/styles/motion.scss")

const detailDialog = transferSource.match(
  /<el-dialog\s+ref="transferDetailDialog"[\s\S]*?<\/el-dialog>/
)

assert.ok(detailDialog, "the transfer detail dialog should expose a stable ref")
assert.ok(
  detailDialog[0].includes('custom-class="transfer-detail-dialog"') &&
    detailDialog[0].includes('@open="resetDetailDialogScroll"') &&
    detailDialog[0].includes('@opened="resetDetailDialogScroll"') &&
    detailDialog[0].includes('@close="handleDetailDialogClose"') &&
    detailDialog[0].includes('@closed="handleDetailDialogClosed"'),
  "the reusable dialog should reset scroll and clean up every open/close cycle"
)
assert.ok(
  detailDialog[0].includes('v-loading="detailLoading"') &&
    detailDialog[0].includes('element-loading-text="正在加载调拨详情"'),
  "a second detail click should show immediate loading feedback"
)

assert.ok(
  transferSource.includes("detailRequestSequence: 0") &&
    transferSource.includes("approvalRequestSequence: 0") &&
    transferSource.includes("const requestSequence = ++this.detailRequestSequence") &&
    transferSource.includes("this.isActiveDetailRequest(requestSequence, transferId)") &&
    transferSource.includes("const requestSequence = ++this.approvalRequestSequence") &&
    transferSource.includes("this.isActiveApprovalRequest(requestSequence, transferId)"),
  "detail and approval requests should have independent latest-request ownership"
)

const openDetailStart = transferSource.indexOf("openDetail(transferId, contextRow)")
const openDetailEnd = transferSource.indexOf("\n    isActiveDetailRequest(requestSequence, transferId)", openDetailStart)
const openDetailSource = transferSource.slice(openDetailStart, openDetailEnd)

assert.ok(
  openDetailSource.indexOf("this.detailOpen = true") < openDetailSource.indexOf("getTransferDetail(transferId") &&
    openDetailSource.includes("{ silentError: true }") &&
    openDetailSource.includes(".catch(error =>") &&
    openDetailSource.includes(".finally(() =>"),
  "detail loading should respond immediately and always settle after request failure"
)
assert.ok(
  transferApiSource.includes("export function getTransferDetail(transferId, config = {})") &&
    transferApiSource.includes("request(withConfig({ url: '/inventory/transfer/' + transferId, method: 'get' }, config))"),
  "the detail API should accept the local silent-error request configuration"
)

assert.ok(
  /handleTransferMoreCommand\(command, row\)\s*\{[\s\S]*?this\.\$nextTick\(\(\) => this\.runTransferAction\(command, row\)\)/.test(transferSource),
  "opening a dialog from More should wait until the dropdown popper begins closing"
)
assert.ok(
  /handleDetailDialogClose\(\)\s*\{[\s\S]*?this\.detailRequestSequence \+= 1[\s\S]*?this\.approvalRequestSequence \+= 1[\s\S]*?this\.clearDetailApprovalState\(\)/.test(transferSource),
  "closing the dialog should invalidate in-flight detail and approval responses"
)
assert.ok(
  /handleDetailDialogClosed\(\)\s*\{\s*if \(this\.detailOpen\) return[\s\S]*?this\.detail = \{\}/.test(transferSource),
  "an interrupted close transition must not clear a newly reopened detail"
)
assert.ok(
  transferSource.includes('wrapper.querySelector(".el-dialog__body")') &&
    transferSource.includes("wrapper.scrollTop = 0") &&
    transferSource.includes("if (body) body.scrollTop = 0"),
  "each reopen should reset both the legacy wrapper scroll and the dedicated body scroll"
)
assert.ok(
  /\.transfer-detail-dialog\s*\{[\s\S]*?display:\s*flex;[\s\S]*?max-height:\s*calc\(100vh - 32px\);/.test(transferSource) &&
    /\.transfer-detail-dialog \.el-dialog__body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow:\s*auto;/.test(transferSource),
  "large transfer detail content should scroll inside a viewport-bounded dialog"
)

const dialogMotionStart = motionSource.indexOf("/* Dialog and message-box wrappers")
const dialogMotionEnd = motionSource.indexOf("/* Message boxes are compact enough", dialogMotionStart)
const dialogMotionSource = motionSource.slice(dialogMotionStart, dialogMotionEnd)

assert.ok(dialogMotionStart >= 0 && dialogMotionEnd > dialogMotionStart)
assert.ok(
  !dialogMotionSource.includes("filter:") &&
    !dialogMotionSource.includes("blur("),
  "large dialogs must avoid full-surface blur animation on Windows GPU paths"
)

console.log("transfer detail reopen tests passed")
