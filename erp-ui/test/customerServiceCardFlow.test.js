const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const page = readUi("src/views/inventory/customer/index.vue")
const api = readUi("src/api/inventory/customer.js")
const controller = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java")
const service = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java")
const mapper = readRepo("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerServiceCardMapper.xml")
const dto = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceCardVo.java")
const auditDto = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceAuditVo.java")
const legacyMapper = readRepo("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerMapper.xml")
const legacyService = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceImpl.java")
const migration = readRepo("sql/erp_inventory_customer_service_card_20260713.sql")
const compactPage = page.replace(/\s+/g, " ")

assert.ok(
  page.includes("客户服务卡") && page.includes("喜欢的茶") &&
    page.includes("注意事项") && page.includes("人均预算") && page.includes("历史服务记录"),
  "desktop should replace legacy customer management with service-card content"
)
assert.ok(
  page.includes("addCustomerServiceRecord") && page.includes("updateCustomerServiceCard") &&
    /requestKey:\s*this\.key\(["']record["']\)/.test(page) &&
    /requestKey:\s*this\.key\(["']card["']\)/.test(page) &&
    page.includes("addCustomerServiceRecord(this.selected.customerId, { ...this.record })"),
  "store employees should edit cards and reuse one idempotency key across retries"
)
assert.ok(
  page.includes("getCustomerServiceCardPhoto") && page.includes("URL.createObjectURL") &&
    page.includes("URL.revokeObjectURL") && page.includes("image/svg+xml"),
  "customer photos should use controlled blobs, release object URLs, and reject SVG bindings in the picker"
)
assert.ok(
  api.includes("/inventory/customer/options") && api.includes("/inventory/customer/service-card/capabilities") &&
    api.includes("/inventory/customer/service-card/list") && api.includes("/inventory/customer/service-card/audit") &&
    api.includes("/records") && /responseType:\s*["']blob["']/.test(api) &&
    api.includes("validateCustomerPhotoBlob"),
  "frontend should use safe capability/service-card APIs and reject JSON photo errors"
)
assert.ok(
  controller.includes("inv:customerCard:list") && controller.includes("inv:customerCard:query") &&
    controller.includes("inv:customerCard:add") && controller.includes("inv:customerCard:edit") &&
    controller.includes("inv:customerCard:record:add") && controller.includes("inv:customerCard:audit") &&
    controller.includes('response.setHeader("Cache-Control", "no-store")'),
  "service-card controller should have dedicated permissions and non-cacheable reads"
)
assert.ok(
  page.includes("listCustomerServiceAudits") && page.includes("auditRows") &&
    page.includes("脱敏摘要") && page.includes("inv:customerCard:audit"),
  "desktop manager view should expose the safe audit list behind its dedicated permission"
)
const detailAppendButton = compactPage.match(/<el-button[^>]*@click="openRecord\(detail\)"[^>]*>追加服务/)
assert.ok(detailAppendButton, "detail should expose append-service action")
assert.ok(
  detailAppendButton[0].includes(`v-hasPermi="['inv:customerCard:record:add']"`) &&
    detailAppendButton[0].includes(`detail.status === '0'`),
  "detail append-service action should require permission and an active card"
)
assert.ok(
  page.includes("创建人") && page.includes("最后修改人") && page.includes("最后修改时间") &&
    page.includes("detail.createBy") && page.includes("detail.updateBy") && page.includes("detail.updateTime"),
  "desktop detail should expose creator and last-modifier metadata"
)
;["requestKey", "shopDeptId", "serviceNote", "contactPhone"].forEach(field => {
  assert.ok(!new RegExp("private\\s+[^;]+\\s+" + field + "\\s*;", "i").test(auditDto), `safe audit DTO must not declare ${field}`)
})
assert.ok(
  service.includes("requireStoreContext") && service.includes("assertScopedCard") &&
    service.includes("shopDeptId.equals(card.getShopDeptId())") &&
    service.includes("selectChangeLogIdByRequestKey") && service.includes("writeChangeLog") &&
    service.includes("requireActive(before)") && service.includes("requireEnabledForShop"),
  "all card operations should be scoped, store-rollout gated, active-only, idempotent, and audited"
)
assert.ok(
  service.includes("validateDriveBusinessFile") && service.includes('"CUSTOMER_PHOTO"') &&
    controller.includes("resolvePhotoNode") && controller.includes("readDriveBusinessContent"),
  "photo binding and viewing should both pass through controlled business authorization"
)
;["creditLimit", "usedCredit", "paymentTerm", "accountPeriod"].forEach(field => {
  assert.ok(!new RegExp("private\\s+[^;]+\\s+" + field + "\\s*;", "i").test(dto), `safe card DTO must not declare ${field}`)
})
assert.ok(
  mapper.includes("where c.shop_dept_id = #{shopDeptId}") &&
    mapper.includes("c.contact_phone like") && mapper.includes("p.tea_preferences like") &&
    mapper.includes("p.preference_tags like") && mapper.includes("p.brewing_service_preferences like") &&
    mapper.includes("p.cautions like") &&
    mapper.includes("l.shop_dept_id = #{shopDeptId}") && mapper.includes("c.shop_dept_id = #{shopDeptId}") &&
    migration.includes("inv_customer_service_change_log") && migration.includes("inv:customerCard:audit"),
  "list/search should remain store-isolated and every change should have an audit table"
)
assert.ok(
  controller.includes("requireLegacyCustomerApi()") && controller.includes("requireDisabled") &&
    legacyMapper.includes("inv_customer_service_profile") && legacyMapper.includes("not exists") &&
    legacyService.includes("不能物理删除"),
  "cutover should fail closed for cached legacy permissions and preserve service-card history"
)
assert.ok(
  page.includes("detailRequestSeq") && page.includes("photoRequestSeq") &&
    /Number\(this\.detail\.customerId\)\s*!==\s*Number\(customerId\)/.test(page),
  "late detail and photo responses must not overwrite another customer's drawer"
)

console.log("customerServiceCardFlow tests passed")
