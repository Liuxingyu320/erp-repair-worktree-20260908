const assert = require("assert")
const fs = require("fs")
const path = require("path")

const onlineSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/monitor/online/index.vue"),
  "utf8"
)
const mobileActionsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActions.js"),
  "utf8"
)
const mobileMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureMapper.js"),
  "utf8"
)

assert.ok(
  !onlineSource.includes("list.slice(") &&
    onlineSource.includes("pageNum: 1") &&
    onlineSource.includes("pageSize: 10") &&
    onlineSource.includes('@pagination="getList"'),
  "online sessions must use backend pagination instead of slicing the full Redis result in the browser"
)

assert.ok(
  onlineSource.includes("response.truncated") &&
    onlineSource.includes("response.scanLimit") &&
    onlineSource.includes("response.invalidSessionCount") &&
    onlineSource.includes("当前结果可能不完整") &&
    onlineSource.includes("在线状态暂不可用，请稍后重试") &&
    onlineSource.includes("loadError = true"),
  "bounded scan truncation, invalid sessions, and Redis failures must be visible to operators"
)

assert.ok(
  onlineSource.includes(':disabled="scope.row.currentSession"') &&
    onlineSource.includes("当前会话请使用退出登录") &&
    mobileActionsSource.includes("if (!row.currentSession) actions.push") &&
    onlineSource.includes("maskToken(scope.row.tokenId)") &&
    mobileMapperSource.includes("maskSessionId(firstText(row, [\"tokenId\"]))"),
  "desktop and mobile online-session views must protect the current session and mask raw session ids"
)

assert.ok(
  onlineSource.includes("登录 IP：") && onlineSource.includes("登录时间："),
  "force-logout confirmation should identify the target session by IP and login time"
)

console.log("onlineSessionGovernance tests passed")
