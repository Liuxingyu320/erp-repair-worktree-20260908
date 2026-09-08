const assert = require('assert')
const fs = require('fs')
const path = require('path')

const mapperPath = path.resolve(__dirname, '../../erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml')
const source = fs.readFileSync(mapperPath, 'utf8')

const deptFilterMatch = source.match(/<if test="deptId != null and deptId != 0">([\s\S]*?)<\/if>/)

assert.ok(deptFilterMatch, 'user list mapper should keep a deptId filter for organization tree clicks')

const deptFilter = deptFilterMatch[1]

assert.ok(
  deptFilter.includes('sys_user_shop'),
  'deptId filtering should include sys_user_shop so users scoped to a selected store are listed even when their main dept is an area'
)

assert.ok(
  /find_in_set\(#\{deptId\},\s*[^)]*ancestors/.test(deptFilter),
  'deptId filtering should continue matching descendant departments when an area node is selected'
)

console.log('userDeptShopScopeMapper tests passed')
