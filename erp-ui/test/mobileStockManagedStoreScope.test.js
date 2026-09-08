const assert = require("assert")
const fs = require("fs")
const path = require("path")

const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)

assert.ok(
  featurePageSource.includes('import { listShopTree } from "@/api/system/dept"') &&
    featurePageSource.includes("showStockManagedStoreScope") &&
    featurePageSource.includes("stockScopeMode") &&
    featurePageSource.includes("当前门店") &&
    featurePageSource.includes("管理门店") &&
    featurePageSource.includes("全部管理门店") &&
    featurePageSource.includes("managedStoreOptions") &&
    featurePageSource.includes("loadManagedStoreOptions"),
  "mobile stock page should expose current-store and managed-store scope controls for store users"
)

const createBaseQuerySource = featureServiceSource.slice(
  featureServiceSource.indexOf("const createBaseQuery"),
  featureServiceSource.indexOf("const normalizeTransferListQuery")
)

assert.ok(
  createBaseQuerySource.includes('featureKey === "stock"') &&
    createBaseQuerySource.includes('source.selectedDeptType === "STORE"') &&
    createBaseQuerySource.includes('source.stockScopeMode === "managed"') &&
    createBaseQuerySource.includes("query.ownOnly = false") &&
    createBaseQuerySource.includes("query.shopDeptId = source.managedStoreId || 0"),
  "mobile stock managed-store mode should query authorized managed stores instead of the login store only"
)
