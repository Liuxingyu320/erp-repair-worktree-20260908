const assert = require("assert")
const fs = require("fs")
const path = require("path")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
const userSource = read("../src/views/system/user/index.vue")
const shopSource = read("../src/views/system/shop/index.vue")
const splitPaneSource = read("../src/components/ResizableSplitPane/index.vue")
const userApiSource = read("../src/api/system/user.js")
const shopApiSource = read("../src/api/system/userShop.js")
const userStoreSource = read("../src/store/modules/user.js")

assert.ok(
  userStoreSource.includes("systemManagementUxV2: false") &&
    userSource.includes("managementUxV2Enabled") &&
    shopSource.includes("managementUxV2Enabled"),
  "system management UX v2 must be capability-gated and default off"
)

assert.ok(
  userApiSource.includes("export function getUserSetupSummary") &&
    userApiSource.includes("/system/user/setup-summary") &&
    userSource.includes("setupSummaryCards") &&
    userSource.includes("applySetupSummaryFilter"),
  "user management should expose scoped setup-health summary cards"
)

assert.ok(
  userSource.includes('const USER_FILTER_STORAGE_KEY = "system-user-filters-v2"') &&
    userSource.includes('const USER_COLUMN_STORAGE_KEY = "system-user-columns-v2"') &&
    userSource.includes("USER_COLUMN_PRESETS") &&
    userSource.includes("advancedSearchVisible") &&
    userSource.includes('fixed="left"') &&
    userSource.includes("cache.local.remove(USER_COLUMN_STORAGE_KEY)") &&
    !userSource.includes('state["userName"] = this.queryParams["userName"]') &&
    !userSource.includes('state["phonenumber"] = this.queryParams["phonenumber"]'),
  "filters and column presets should be versioned without persisting direct identity fields"
)

assert.ok(
  splitPaneSource.includes('role="separator"') &&
    splitPaneSource.includes('aria-orientation="vertical"') &&
    splitPaneSource.includes("ArrowLeft") &&
    splitPaneSource.includes("localStorage.setItem") &&
    shopSource.includes('storage-key="system-user-shop-split-v2"'),
  "shop authorization should provide a persistent, keyboard-resizable split pane"
)

assert.ok(
  shopApiSource.includes("previewUserShop") &&
    shopApiSource.includes("scopeVersion") &&
    shopSource.includes("确认组织授权变更") &&
    shopSource.includes("USER_SHOP_SCOPE_CONFLICT") &&
    shopSource.includes("applyAuthoritativeShopScope"),
  "shop authorization should preview differences and protect saves with optimistic concurrency"
)

assert.ok(
  shopSource.includes("beforeRouteLeave") &&
    shopSource.includes("beforeunload") &&
    shopSource.includes("handlePagination") &&
    shopSource.includes("runAfterDiscardConfirmation"),
  "shop authorization should guard unsaved changes across route, reload, query and pagination transitions"
)

assert.ok(
  shopSource.includes("仅显示已选") &&
    shopSource.includes("展开到匹配项") &&
    shopSource.includes("清空本次可编辑项") &&
    shopSource.includes("直接授权") &&
    shopSource.includes("上级继承"),
  "shop tree should make large-scope review and inherited authorization understandable"
)

console.log("systemManagementUxV2 tests passed")
