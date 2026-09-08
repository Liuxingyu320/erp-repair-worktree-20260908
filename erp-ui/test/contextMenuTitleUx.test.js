const assert = require('assert')
const fs = require('fs')
const path = require('path')

const sidebarSource = fs.readFileSync(
  path.resolve(__dirname, '../src/layout/components/Sidebar/SidebarItem.vue'),
  'utf8'
)

assert.ok(
  sidebarSource.includes('getSelectedDeptContext'),
  'sidebar menu title should use selected organization context'
)

assert.ok(
  sidebarSource.includes('getMenuTitle') &&
    sidebarSource.includes('isStoreCommodityMenu') &&
    sidebarSource.includes('商品资料'),
  'store users should see the cangku product/category bucket as 商品资料 instead of 仓库管理'
)

assert.ok(
  sidebarSource.includes('this.selectedDeptContext.isStore') &&
    sidebarSource.includes('return title'),
  'context title override should be limited to store context and preserve normal menu titles otherwise'
)

console.log('contextMenuTitleUx tests passed')
