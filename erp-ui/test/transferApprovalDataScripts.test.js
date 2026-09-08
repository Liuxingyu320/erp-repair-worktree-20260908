const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "../..")
const preview = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_position_chain_preview_20260710.sql"), "utf8")
const cleanup = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_temporary_post_cleanup_20260710.sql"), "utf8")
const purge = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_temporary_post_purge_20260710.sql"), "utf8")
const fourLevel = fs.readFileSync(path.join(root,
  "sql/erp_inventory_transfer_four_level_approval_20260713.sql"), "utf8")
const dockerFourLevel = fs.readFileSync(path.join(root,
  "docker/mysql/db/erp_inventory_transfer_four_level_approval_20260713.sql"), "utf8")

assert.ok(preview.includes("tmp_transfer_level4_candidates"))
assert.ok(preview.includes("tmp_transfer_level3_candidates"))
assert.ok(preview.includes("p.post_code = 'dz'"))
assert.ok(preview.includes("p.post_code = 'dzzy'"))
assert.ok(preview.includes("max(eligible.post_sort)"))
assert.ok(!/\b(update|delete)\s+(sys_|inv_)/i.test(preview))

assert.ok(cleanup.includes("sijifzr"))
assert.ok(cleanup.includes("sanjifzr"))
assert.ok(cleanup.includes("signal sqlstate '45000'"))
assert.ok(cleanup.includes("inv_transfer_approval_node"))
assert.ok(cleanup.includes("inv_transfer_approval_task"))
assert.ok(cleanup.includes("delete up"))
assert.ok(cleanup.includes("delete p"))

assert.ok(purge.includes("create procedure purge_transfer_temporary_posts"))
assert.ok(purge.includes("declare exit handler for sqlexception"))
assert.ok(purge.includes("start transaction"))
assert.ok(purge.includes("rollback"))
assert.ok(purge.includes("commit"))
assert.ok(purge.includes("临时岗位任务数量已变化，停止清理"))
assert.ok(purge.includes("待审批临时任务数量已变化，停止清理"))
assert.ok(purge.includes("审核中调拨单数量已变化，停止清理"))
assert.ok(purge.includes("已取消调拨单数量已变化，停止清理"))
assert.ok(purge.includes("temporary_post_purge_reset"))
assert.ok(purge.includes("set transfer_order.status = 'draft'"))
assert.ok(purge.includes("set approval_instance.status = 'closed'"))
assert.ok(purge.includes("delete pending_task"))
assert.ok(purge.includes("history_task.post_id = null"))
assert.ok(purge.includes("history_task.post_code = ''"))
assert.ok(purge.includes("delete user_post"))
assert.ok(purge.includes("delete temporary_post"))
assert.ok(purge.includes("清理后仍存在临时岗位，事务回滚"))
assert.ok(purge.includes("清理后仍存在临时岗位任务引用，事务回滚"))

assert.strictEqual(fourLevel, dockerFourLevel)
;[
  "start transaction",
  "level4_highest",
  "level3_highest",
  "operations_director",
  "general_manager",
  "yyzj",
  "zjl",
  "inv:transfer:approve",
  "signal sqlstate '45000'",
  "commit"
].forEach(fragment =>
  assert.ok(fourLevel.toLowerCase().includes(fragment.toLowerCase()), fragment)
)
assert.ok(!fourLevel.includes("inv_transfer_approval_instance"))
assert.ok(!fourLevel.includes("inv_transfer_approval_task"))
assert.ok(!fourLevel.includes("inv_transfer_order"))
assert.ok(!fourLevel.includes("inv_transfer_status_log"))

console.log("transfer approval data scripts tests passed")
