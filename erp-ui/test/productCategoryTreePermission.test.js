const assert = require("assert")
const fs = require("fs")
const path = require("path")

const productSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/product/index.vue"),
  "utf8"
)
const repoRoot = path.resolve(__dirname, "..", "..")
const migrationPath = path.resolve(repoRoot, "sql/erp_product_category_tree_permission_20260628.sql")

assert.ok(
  productSource.includes(`v-hasPermi="['inv:category:list', 'inv:category:tree']"`) ||
    productSource.includes(`v-hasPermi="['inv:category:list','inv:category:tree']"`),
  "product page category refresh should allow category management or readonly category tree permission"
)

assert.ok(
  productSource.includes("categoryLoadError") &&
    productSource.includes("分类加载失败，请联系管理员授权"),
  "product page should show a local category tree load failure message instead of silently showing only all products"
)

assert.ok(fs.existsSync(migrationPath), "category tree readonly permission migration should exist")
const migrationSource = fs.readFileSync(migrationPath, "utf8")

assert.ok(
  migrationSource.includes("inv:category:tree") &&
    migrationSource.includes("商品分类树查询") &&
    migrationSource.includes("inv:product:list") &&
    migrationSource.includes("INSERT IGNORE INTO sys_role_menu"),
  "migration should create a hidden readonly category tree permission and grant it to product-list roles"
)

console.log("productCategoryTreePermission tests passed")
