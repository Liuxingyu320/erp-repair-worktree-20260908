const assert = require('assert')
const fs = require('fs')
const path = require('path')

const userViewPath = path.resolve(__dirname, '../src/views/system/user/index.vue')
const userFilterPath = path.resolve(__dirname, '../src/views/system/user/components/UserFilterDrawer.vue')
const mapperPath = path.resolve(__dirname, '../../erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml')
const sysUserPath = path.resolve(__dirname, '../../erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java')

const userView = fs.readFileSync(userViewPath, 'utf8')
const userFilter = fs.readFileSync(userFilterPath, 'utf8')
const mapper = fs.readFileSync(mapperPath, 'utf8')
const sysUser = fs.readFileSync(sysUserPath, 'utf8')

assert.ok(
  userFilter.includes('label="岗位"') &&
    userFilter.includes('v-model="draft.postId"') &&
    userView.includes('postId: undefined') &&
    userView.includes('Object.assign(this.queryParams, filters)'),
  'user advanced filter drawer should provide a persisted post selector applied to queryParams.postId'
)

assert.ok(
  userView.includes('key="postNames"') && userView.includes('prop="postNames"'),
  'user list should display the user post names'
)

assert.ok(
  !userView.includes('<el-table-column label="创建时间"'),
  'user list should not display the create time column'
)

assert.ok(
  mapper.includes('post_names') && mapper.includes('postNames') && mapper.includes('sys_user_post'),
  'user list mapper should return aggregated post names while preserving postId filtering'
)

assert.ok(
  sysUser.includes('private String postNames') &&
    sysUser.includes('getPostNames()') &&
    sysUser.includes('setPostNames(String postNames)'),
  'SysUser should expose postNames for user list display'
)

console.log('userPostDisplaySearch tests passed')
