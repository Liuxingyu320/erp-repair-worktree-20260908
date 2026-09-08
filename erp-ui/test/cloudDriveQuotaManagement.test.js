const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")
const exists = file => fs.existsSync(path.join(root, file))

const components = [
  "DriveQuotaCenterDrawer.vue",
  "DriveCapacityPanel.vue",
  "DriveOrganizationQuotaPanel.vue",
  "DrivePostQuotaPanel.vue",
  "DriveUserQuotaPanel.vue",
  "DriveQuotaImpactDialog.vue"
]
components.forEach(file => assert.ok(
  exists(`src/views/drive/components/quota/${file}`),
  `${file} should exist`
))

const api = read("src/api/drive/admin.js")
const drawer = read("src/views/drive/components/quota/DriveQuotaCenterDrawer.vue")
const capacity = read("src/views/drive/components/quota/DriveCapacityPanel.vue")
const organizations = read("src/views/drive/components/quota/DriveOrganizationQuotaPanel.vue")
const posts = read("src/views/drive/components/quota/DrivePostQuotaPanel.vue")
const users = read("src/views/drive/components/quota/DriveUserQuotaPanel.vue")
const page = read("src/views/drive/index.vue")
const sidebar = read("src/views/drive/components/DriveSpaceSidebar.vue")
const mobile = read("src/views/mobile/drive/index.vue")

for (const route of [
  "/status",
  "/quota/capacity",
  "/quota/personal-policies",
  "/quota/posts",
  "/quota/users",
  "/organizations/type-rules",
  "/organizations/batch",
  "/quota/impact",
  "/organizations/reconcile"
]) {
  assert.ok(api.includes(route), `admin API should expose ${route}`)
}

assert.ok(
  drawer.includes("Promise.all") &&
    drawer.includes("previewDriveQuotaImpact") &&
    drawer.includes("impactHash: this.impact.impactHash") &&
    drawer.indexOf("previewDriveQuotaImpact") < drawer.indexOf("updateDriveCapacity(data)"),
  "quota center should load centrally and require an impact preview before writes"
)
assert.ok(
  drawer.includes("organization-rule") &&
    drawer.includes("organization-batch") &&
    drawer.includes("reconcileDriveOrganizations") &&
    drawer.includes("quotaPolicyEnabled") &&
    drawer.includes("organizationSyncEnabled") &&
    drawer.includes("capacityReservationEnabled") &&
    drawer.includes("budgetBlockedCount"),
  "quota center should save type rules, explicit batches and reconcile dynamic disks"
)
assert.ok(
  capacity.includes("pendingUploadBytes") &&
    capacity.includes("capacityAccountedBytes") &&
    capacity.includes("remainingUploadBytes") &&
    capacity.includes("cleanupFailedReservationCount") &&
    capacity.includes("reservationEnabled"),
  "capacity management should distinguish committed, pending, safely-accounted and remaining upload capacity"
)

assert.ok(
  organizations.includes("自动建盘") &&
    organizations.includes("requireActiveMember") &&
    organizations.includes("ORGANIZATION_TYPE_RULE") &&
    organizations.includes("default-expand-all") &&
    organizations.includes("额度来源") &&
    organizations.includes("type=\"selection\"") &&
    organizations.includes("ORGANIZATION_BATCH") &&
    organizations.includes("组织树预算保持不变"),
  "organization management should make dynamic rules and safe selected batches understandable"
)
assert.ok(
  organizations.includes("ALL_DIRECT_MEMBERS") &&
    organizations.includes("PERMISSION_ONLY") &&
    organizations.includes("新额度低于当前用量") &&
    organizations.includes("不会删除文件") &&
    organizations.includes("budgetViolation") &&
    organizations.includes("预算不足·只读") &&
    organizations.includes("修复预算后自动恢复写入") &&
    organizations.includes("hierarchyInvalid") &&
    organizations.includes("层级异常·只读"),
  "organization configuration should explain write scope and safe over-quota behavior"
)

assert.ok(
  posts.includes("postOptions") &&
    posts.includes("选择有效岗位") &&
    posts.includes("post.userCount") &&
    posts.includes("[1, 2, 3, 5, 10]") &&
    posts.includes("subjectId <= 0"),
  "post quota UI should use a real post selector, affected headcount and quick levels"
)
assert.ok(
  users.includes("quotaSourceLabel") &&
    users.includes("失效时间") &&
    users.includes("PERSONAL_POLICY"),
  "user overrides should show their effective source and optional expiry"
)

assert.ok(
  page.includes("DriveQuotaCenterDrawer") &&
    page.includes("云盘设置") &&
    page.includes("quotaSourceLabel") &&
    page.includes("overQuotaBytes"),
  "desktop drive should expose the management center and explain effective quota"
)
assert.ok(
  sidebar.includes("组织盘") && sidebar.includes("quotaSourceLabel") &&
    sidebar.includes("overQuota") && sidebar.includes("writeBlockedMessage") &&
    sidebar.includes("activeSpace.canCleanup"),
  "desktop sidebar should use organization-disk wording and show source/overage"
)
assert.ok(
  mobile.includes("quotaSourceLabel") && mobile.includes("overQuotaBytes") &&
    mobile.includes("writeBlockedMessage") && mobile.includes("修复预算后会自动恢复上传"),
  "mobile drive should show quota source and overage without the complex admin center"
)
assert.ok(
  page.includes("canCleanupCurrentSpace") &&
    read("src/views/drive/components/DriveTrashView.vue").includes("canCleanup"),
  "read-only spaces should still expose authorized capacity cleanup without allowing restore"
)

const { GIB, bytesToGiB, gibToBytes, quotaLevel, impactSummary } = require(
  path.join(root, "src/views/drive/quotaState.js")
)
assert.strictEqual(gibToBytes("2", false), 2 * GIB)
assert.strictEqual(gibToBytes("1.125", false), 1.125 * GIB)
assert.strictEqual(gibToBytes("1.1234", false), null)
assert.strictEqual(gibToBytes("0", false), null)
assert.strictEqual(gibToBytes("0", true), 0)
assert.strictEqual(gibToBytes("999999999999", true), null)
assert.strictEqual(bytesToGiB(3 * GIB), 3)
assert.strictEqual(quotaLevel(69, 100), "normal")
assert.strictEqual(quotaLevel(70, 100), "attention")
assert.strictEqual(quotaLevel(85, 100), "warning")
assert.strictEqual(quotaLevel(100, 100), "danger")
assert.strictEqual(quotaLevel(1, 0), "danger")
assert.ok(impactSummary({ affectedCount: 3, deltaBytes: -1024 }).includes("减少 1024 字节"))

console.log("cloudDriveQuotaManagement tests passed")
