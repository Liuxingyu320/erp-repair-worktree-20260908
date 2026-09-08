const assert = require('assert')
const fs = require('fs')
const path = require('path')

const userSource = fs.readFileSync(path.resolve(__dirname, '../src/views/system/user/index.vue'), 'utf8')
const shopSource = fs.readFileSync(path.resolve(__dirname, '../src/views/system/shop/index.vue'), 'utf8')

assert.ok(
  userSource.includes('command="handleShopScope"') &&
    userSource.includes('组织授权') &&
    userSource.includes('<el-dropdown size="mini" trigger="click"') &&
    userSource.includes('v-if="canNavigateShopScope"') &&
    userSource.includes('hasPermiAnd(["system:userShop:list", "system:userShop:query", "system:userShop:edit"])'),
  'user list More menu should expose shop authorization only when list, query and edit permissions are all present'
)

assert.ok(
  userSource.includes('handleShopScope(row)') &&
    userSource.includes('path: "/system/shop"') &&
    userSource.includes('userName: row.userName') &&
    userSource.includes('userId: row.userId'),
  'user list should navigate to shop authorization with the selected user identity in query params'
)

assert.ok(
    userSource.includes('confirmShopScopeAfterCreate') &&
    userSource.includes('this.canNavigateShopScope') &&
    userSource.includes('新增成功。是否现在配置该用户可管理的公司、组织、门店或仓库？'),
  'new user success flow should guide admins to configure organization authorization next'
)

assert.ok(
 userSource.includes('setupStatus') &&
    userSource.includes('配置状态') &&
    userSource.includes('未分配角色') &&
    userSource.includes('未授权管理范围') &&
    userSource.includes('missingRole') &&
    userSource.includes('missingShopScope'),
  'user list should make missing role/management scope authorization visible after account creation'
)

assert.ok(
  shopSource.includes('v-if="canSaveShopScope"') &&
    shopSource.includes('hasPermiAnd(["system:userShop:query", "system:userShop:edit"])') &&
    shopSource.includes('@click="submitShopScope"'),
  'shop authorization save action should require both query and edit permissions used by the backend'
)

assert.ok(
  shopSource.includes('applyRouteUserQuery') &&
    shopSource.includes('focusRouteUser') &&
    shopSource.includes('routeUserId') &&
    shopSource.includes('routeUserName'),
  'shop authorization page should read route query and focus the intended user'
)

assert.ok(
  shopSource.includes('this.$refs.userTable.setCurrentRow(matchedUser)') &&
    shopSource.includes('this.loadCurrentUserShop(matchedUser)'),
  'shop authorization page should select the matched user and load the user shop scope automatically'
)

console.log('userShopNavigationUx tests passed')
