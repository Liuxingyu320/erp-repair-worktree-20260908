const assert = require("assert")
const fs = require("fs")
const path = require("path")

const readUi = relativePath => fs.readFileSync(path.resolve(__dirname, "../", relativePath), "utf8")
const page = readUi("src/views/system/dept/index.vue")
const api = readUi("src/api/system/dept.js")

assert.ok(api.includes("export function listDeptLeaderOptions"), "department API should expose leader options")
assert.ok(api.includes("url: '/system/dept/leader-options'"), "leader options should use the scoped backend endpoint")
assert.ok(page.includes('v-model="form.leaderUserId"'), "department form should submit a stable leader user ID")
assert.ok(!page.includes('v-model="form.leader"'), "free-text leader editing must be removed")
assert.ok(page.includes("待确认负责人"), "legacy leader text should be shown as pending confirmation")
assert.ok(page.includes("employeeNo") && page.includes("deptName"), "options should identify employees by number and organization")
assert.ok(page.includes("this.$route.query.deptId"), "readiness action should focus the affected department")
assert.ok(page.includes('$refs.leaderSelect'), "readiness action should focus the leader selector")
assert.strictEqual((page.match(/\bbeforeDestroy\s*\(\)\s*\{/g) || []).length, 1,
  "department management must have one destruction hook so cleanup steps cannot overwrite each other")
const destroyStart = page.indexOf("beforeDestroy() {")
const destroyEnd = page.indexOf("  beforeRouteLeave(", destroyStart)
const destroySource = page.slice(destroyStart, destroyEnd)
assert.ok(
  destroySource.includes("clearTimeout(this.leaderSearchTimer)") &&
    destroySource.includes("this.leaderSearchTimer = null") &&
    destroySource.includes('window.removeEventListener("beforeunload", this.handleBeforeUnload)'),
  "department destruction must clear the leader search timer and unload listener together"
)

console.log("hrDeptLeaderIdentity.test.js passed")
