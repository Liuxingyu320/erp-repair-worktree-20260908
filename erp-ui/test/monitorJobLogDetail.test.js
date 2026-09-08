const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")

function source(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

const apiSource = source("src/api/monitor/jobLog.js")
const viewSource = source("src/views/monitor/job/log.vue")
const mobileFeatureServiceSource = source("src/views/mobile/feature/featureService.js")

assert.ok(
  apiSource.includes("export function getJobLog"),
  "job log API should expose getJobLog for GET /schedule/job/log/{jobLogId}"
)

assert.ok(
  apiSource.includes("url: '/schedule/job/log/' + jobLogId"),
  "getJobLog should call the job log detail endpoint"
)

assert.ok(
  viewSource.includes("getJobLog"),
  "job log view should import and call getJobLog before opening detail"
)

assert.ok(
  viewSource.includes("getJobLog(row.jobLogId)"),
  "job log detail should be loaded by id instead of trusting the paged list row"
)

assert.ok(
  mobileFeatureServiceSource.includes("getJobLog"),
  "mobile monitor job log detail should import and call getJobLog"
)

assert.ok(
  mobileFeatureServiceSource.includes('featureKey === "monitorJobLog"') &&
    mobileFeatureServiceSource.includes("resolveDetail(getJobLog(id))"),
  "mobile monitor job log detail should be loaded by id instead of falling back to the list row"
)

console.log("monitorJobLogDetail tests passed")
