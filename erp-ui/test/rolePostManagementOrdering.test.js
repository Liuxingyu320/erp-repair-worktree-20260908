const assert = require("assert")
const fs = require("fs")
const path = require("path")

const roleMapper = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-system/src/main/resources/mapper/system/SysRoleMapper.xml"),
  "utf8"
)
const postMapper = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-system/src/main/resources/mapper/system/SysPostMapper.xml"),
  "utf8"
)

assert.ok(
  roleMapper.includes("order by r.role_sort, r.role_id"),
  "role management should return roles by position hierarchy with role_id as a stable tie breaker"
)

assert.ok(
  /<select id="selectRoleAll"[\s\S]*order by r\.role_sort, r\.role_id[\s\S]*<\/select>/.test(roleMapper),
  "role option APIs should use the same hierarchy order as role management"
)

assert.ok(
  /<select id="selectPostList"[\s\S]*order by post_sort, post_id[\s\S]*<\/select>/.test(postMapper),
  "post management should return posts by position hierarchy with post_id as a stable tie breaker"
)

assert.ok(
  /<select id="selectPostAll"[\s\S]*order by post_sort, post_id[\s\S]*<\/select>/.test(postMapper),
  "post option APIs should use the same hierarchy order as post management"
)

console.log("rolePostManagementOrdering tests passed")
