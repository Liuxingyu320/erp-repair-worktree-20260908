const assert = require('assert')
const fs = require('fs')
const path = require('path')

const userViewPath = path.resolve(__dirname, '../src/views/system/user/index.vue')
const mapperPath = path.resolve(__dirname, '../../erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml')
const sysUserPath = path.resolve(__dirname, '../../erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java')

const userView = fs.readFileSync(userViewPath, 'utf8')
const mapper = fs.readFileSync(mapperPath, 'utf8')
const sysUser = fs.readFileSync(sysUserPath, 'utf8')

assert.ok(
  userView.includes('label="主部门"') && userView.includes('label="管理范围"'),
  'user list should distinguish the single primary department from multi-node management scope'
)

assert.ok(
  userView.includes('shopScopeNames') && userView.includes('scopeNamesText'),
  'user list should render management scope names returned by the backend'
)

assert.ok(
  mapper.includes('shop_scope_names') && mapper.includes('group_concat') && mapper.includes('shopScopeNames'),
  'user list mapper should return aggregated management scope names'
)

assert.ok(
  sysUser.includes('private String shopScopeNames') &&
    sysUser.includes('getShopScopeNames()') &&
    sysUser.includes('setShopScopeNames(String shopScopeNames)'),
  'SysUser should expose management scope names for the user list'
)

console.log('userManagementScopeDisplay tests passed')
