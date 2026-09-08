const assert = require("assert")
const fs = require("fs")
const path = require("path")

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, "..", relativePath), "utf8")
}

function repoSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, "../..", relativePath), "utf8")
}

function repoFileExists(relativePath) {
  return fs.existsSync(path.resolve(__dirname, "../..", relativePath))
}

const inventoryAndOaViews = [
  "src/views/inventory/customer/index.vue",
  "src/views/inventory/deliveryNotice/index.vue",
  "src/views/inventory/product/index.vue",
  "src/views/inventory/purchase/index.vue",
  "src/views/inventory/purchaseReturn/index.vue",
  "src/views/inventory/sales/index.vue",
  "src/views/inventory/salesReturn/index.vue",
  "src/views/inventory/stock/index.vue",
  "src/views/inventory/stock/log.vue",
  "src/views/inventory/stockCheck/index.vue",
  "src/views/inventory/supplier/index.vue",
  "src/views/inventory/transfer/index.vue",
  "src/views/inventory/transfer/records.vue",
  "src/views/oa/attendance/index.vue",
  "src/views/oa/purchase/index.vue",
  "src/views/oa/salary/index.vue"
]

for (const viewPath of inventoryAndOaViews) {
  const viewSource = source(viewPath)
  assert.ok(
    !viewSource.includes("$download.saveAs(res"),
    `${viewPath} should use the shared download helper so JSON error blobs are shown as messages instead of saved as xlsx files`
  )
}

const datedExportPages = [
  ["src/views/system/user/index.vue", "system/user/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/system/role/index.vue", "system/role/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/system/config/index.vue", "system/config/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/system/dict/index.vue", "system/dict/type/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/system/logininfor/index.vue", "system/logininfor/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/system/operlog/index.vue", "system/operlog/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/monitor/job/log.vue", "schedule/job/log/export", "this.addDateRange({ ...this.queryParams }, this.dateRange)"],
  ["src/views/inventory/stockCheck/index.vue", "inventory/stockCheck/export", "this.addDateRange({ ...this.queryParams }, this.dateRange, \"CheckDate\")"]
]

for (const [viewPath, exportPath, expectedParams] of datedExportPages) {
  const viewSource = source(viewPath)
  assert.ok(viewSource.includes(exportPath), `${viewPath} should still export through ${exportPath}`)
  assert.ok(
    viewSource.includes(expectedParams),
    `${viewPath} should pass the same date range filters to export that it uses for the visible list`
  )
}

const oaExportPermissions = [
  ["src/views/oa/purchase/index.vue", "oa:purchase:export"],
  ["src/views/oa/salary/index.vue", "oa:salary:export"]
]

for (const [viewPath, permission] of oaExportPermissions) {
  const viewSource = source(viewPath)
  assert.ok(
    viewSource.includes(`v-hasPermi="['${permission}']"`),
    `${viewPath} should hide export controls from users without ${permission}`
  )
}

const exportConfirmSource = source("src/utils/exportConfirm.js")

assert.ok(
  exportConfirmSource.includes("export function confirmExportAction") &&
    exportConfirmSource.includes("sensitiveFields") &&
    exportConfirmSource.includes("导出范围"),
  "shared export confirmation helper should describe module, range, and sensitive fields before downloads"
)

const salaryDomainSource = repoSource("erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSalaryRecord.java")
const salaryMapperSource = repoSource("erp-modules/erp-oa/src/main/resources/mapper/oa/OaSalaryRecordMapper.xml")

for (const column of ["员工姓名", "所属部门", "核算店铺"]) {
  assert.ok(
    salaryDomainSource.includes(`@Excel(name = "${column}`),
    `salary exports should include readable ${column} instead of making users infer people and scope from account ids`
  )
}

assert.ok(
  salaryMapperSource.includes("u.nick_name as nick_name") &&
    salaryMapperSource.includes("dept.dept_name as dept_name") &&
    salaryMapperSource.includes("shop_dept.dept_name as shop_dept_name"),
  "salary exports should populate employee name, department, and salary shop names from system tables"
)

const purchaseApplyDomainSource = repoSource("erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaPurchase.java")
const purchaseApplyMapperSource = repoSource("erp-modules/erp-oa/src/main/resources/mapper/oa/OaPurchaseMapper.xml")

for (const column of ["申请人账号", "申请人姓名", "申请部门", "申请店铺"]) {
  assert.ok(
    purchaseApplyDomainSource.includes(`@Excel(name = "${column}`),
    `purchase approval exports should include readable ${column} so approvers can identify the applicant and scope`
  )
}

assert.ok(
  purchaseApplyMapperSource.includes("u.nick_name as applicant_nick_name") &&
    purchaseApplyMapperSource.includes("applicant_dept.dept_name as applicant_dept_name") &&
    purchaseApplyMapperSource.includes("shop_dept.dept_name as shop_dept_name"),
  "purchase approval exports should populate applicant name, department, and shop names from system tables"
)

const stockDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java")

assert.ok(
  stockDomainSource.includes("@Excel(name = \"商品编码\")") &&
    stockDomainSource.includes("@Excel(name = \"商品名称\")") &&
    stockDomainSource.includes("@Excel(name = \"商品分类\")"),
  "stock exports should include business-readable product code, name, and category columns"
)

for (const column of ["库存组织", "库存仓库"]) {
  assert.ok(
    stockDomainSource.includes(`@Excel(name = "${column}`),
    `stock exports should include the readable ${column} column`
  )
}

assert.ok(
  !stockDomainSource.includes("@Excel(name = \"商品ID\")"),
  "stock exports should not expose productId as the primary product identifier for business users"
)

assert.ok(
  stockDomainSource.includes("@Excel(name = \"最后入库时间\", dateFormat = \"yyyy-MM-dd HH:mm:ss\")") &&
    stockDomainSource.includes("@Excel(name = \"最后出库时间\", dateFormat = \"yyyy-MM-dd HH:mm:ss\")"),
  "stock exports should format last in/out times as readable date-time values"
)

const stockMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml")

assert.ok(
  stockMapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
    stockMapperSource.includes("warehouse_dept.dept_name as warehouse_name"),
  "stock exports should populate organization and warehouse names instead of only carrying ids"
)

const productDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvProduct.java")
const productControllerSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java")
const productMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml")
const productImageLinkColumns = ["外包装图片链接", "干茶图片链接", "茶汤图片链接", "叶底图片链接", "补充图片链接"]

for (const column of productImageLinkColumns) {
  assert.ok(
    productDomainSource.includes(`@Excel(name = "${column}")`),
    `product exports should label image URL fields as ${column} so users know they are links`
  )
}

assert.ok(
  productDomainSource.includes("@Excel(name = \"所属店铺\", type = Type.EXPORT)") &&
    productMapperSource.includes("shop_dept.dept_name as shop_dept_name"),
  "product exports should include the readable owning shop for multi-shop product catalogs"
)

assert.ok(
  productControllerSource.includes("importTemplateExcel(response, \"商品导入模板\")"),
  "product import templates should use 商品导入模板 as the workbook title instead of looking like exported 商品数据"
)

assert.ok(
  productControllerSource.includes("INV_COST_VIEW_PERMISSION") &&
    productControllerSource.includes("hideProductPurchaseFields") &&
    productControllerSource.includes("InvProductPurchaseExcelExporter.export(response, list)"),
  "product exports should always include reference cost while only purchase price remains permission-sensitive"
)

for (const viewPath of [
  "src/views/system/user/index.vue",
  "src/views/system/role/index.vue",
  "src/views/system/post/index.vue",
  "src/views/system/config/index.vue",
  "src/views/system/dict/index.vue",
  "src/views/system/dict/data.vue",
  "src/views/system/salary/index.vue",
  "src/views/system/logininfor/index.vue",
  "src/views/system/operlog/index.vue",
  "src/views/monitor/job/log.vue",
  "src/views/inventory/product/index.vue",
  "src/views/inventory/stockCheck/index.vue",
  "src/views/inventory/transfer/index.vue"
]) {
  const viewSource = source(viewPath)
  assert.ok(
    viewSource.includes("confirmExportAction"),
    `${viewPath} should use the shared export confirmation helper before downloading`
  )
}

const stockLogDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockLog.java")

for (const column of ["商品编码", "商品名称", "库存组织", "库存仓库"]) {
  assert.ok(
    stockLogDomainSource.includes(`@Excel(name = "${column}`),
    `stock log exports should include the readable ${column} column`
  )
}

const stockLogMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockLogMapper.xml")

assert.ok(
  stockLogMapperSource.includes("p.product_code") &&
    stockLogMapperSource.includes("p.product_name") &&
    stockLogMapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
    stockLogMapperSource.includes("warehouse_dept.dept_name as warehouse_name"),
  "stock log exports should populate product, organization, and warehouse names instead of requiring ids"
)

assert.ok(
  stockLogDomainSource.includes("purchase=采购单") &&
    stockLogDomainSource.includes("sales=销售发货") &&
    stockLogDomainSource.includes("stock_check=库存盘点") &&
    stockLogDomainSource.includes("cross_store_transfer=异店调货"),
  "stock log exports should translate businessType enum values into readable business names"
)

assert.ok(
  stockLogDomainSource.includes("@Excel(name = \"操作时间\", dateFormat = \"yyyy-MM-dd HH:mm:ss\")"),
  "stock log exports should format operation time as a readable date-time value"
)

const salesOrderDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java")

assert.ok(
  salesOrderDomainSource.includes("noticed=已生成发货通知"),
  "sales order exports should translate the noticed status created by delivery notices"
)

assert.ok(
  salesOrderDomainSource.includes("@Excel(name = \"销售标题\")"),
  "sales order exports should use a specific 销售标题 column instead of the generic 标题"
)

assert.ok(
  salesOrderDomainSource.includes("@Excel(name = \"销售门店\")") &&
    !salesOrderDomainSource.includes("@Excel(name = \"目标门店\")"),
  "sales order exports should label the current shop as 销售门店 instead of the misleading 目标门店"
)

for (const column of ["销售总数量", "已发货数量", "待发货数量"]) {
  assert.ok(
    salesOrderDomainSource.includes(`@Excel(name = "${column}`),
    `sales order exports should include the readable ${column} column`
  )
}

for (const column of ["申请人账号", "申请人姓名", "申请部门"]) {
  assert.ok(
    salesOrderDomainSource.includes(`@Excel(name = "${column}`),
    `sales order exports should distinguish readable ${column} instead of only exporting a raw applicant account`
  )
}

const salesOrderMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml")

assert.ok(
  salesOrderMapperSource.includes("detail_summary.total_quantity") &&
    salesOrderMapperSource.includes("detail_summary.delivered_quantity") &&
    salesOrderMapperSource.includes("detail_summary.remaining_quantity") &&
    salesOrderMapperSource.includes("shop_dept.dept_name as target_dept_name"),
  "sales order exports should populate target store name and order-level quantity summaries from sales details"
)

assert.ok(
  salesOrderMapperSource.includes("u.nick_name as applicant_nick_name") &&
    salesOrderMapperSource.includes("applicant_dept.dept_name as applicant_dept_name"),
  "sales order exports should populate applicant name and department from system tables"
)

const purchaseOrderDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseOrder.java")

assert.ok(
  purchaseOrderDomainSource.includes("@Excel(name = \"采购标题\")"),
  "purchase order exports should use a specific 采购标题 column instead of the generic 标题"
)

for (const column of ["质检状态", "采购总数量", "已收货数量", "待收货数量"]) {
  assert.ok(
    purchaseOrderDomainSource.includes(`@Excel(name = "${column}`),
    `purchase order exports should include the readable ${column} column`
  )
}

for (const column of ["采购店铺", "申请人账号", "申请人姓名", "申请部门"]) {
  assert.ok(
    purchaseOrderDomainSource.includes(`@Excel(name = "${column}`),
    `purchase order exports should include readable ${column} context`
  )
}

assert.ok(
  purchaseOrderDomainSource.includes("pending=待质检") &&
    purchaseOrderDomainSource.includes("passed=质检合格") &&
    purchaseOrderDomainSource.includes("rejected=质检不合格") &&
    purchaseOrderDomainSource.includes("concession=让步接收"),
  "purchase order exports should translate quality-check status values"
)

const purchaseOrderMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseOrderMapper.xml")

assert.ok(
  purchaseOrderMapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
    purchaseOrderMapperSource.includes("u.nick_name as applicant_nick_name") &&
    purchaseOrderMapperSource.includes("applicant_dept.dept_name as applicant_dept_name"),
  "purchase order exports should populate shop, applicant name, and applicant department from system tables"
)

const transferOrderDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java")
const transferLifecycleColumns = ["提交时间", "审批时间", "发货时间", "收货时间", "归档时间", "关闭原因"]

assert.ok(
  transferOrderDomainSource.includes("@Excel(name = \"发货方\")") &&
    !transferOrderDomainSource.includes("@Excel(name = \"发货仓库\")"),
  "transfer exports should label the source as 发货方 because it may be a warehouse or a store"
)

for (const column of transferLifecycleColumns) {
  assert.ok(
    transferOrderDomainSource.includes(`@Excel(name = "${column}`),
    `transfer exports should include the readable ${column} column`
  )
}

const deliveryNoticeDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNotice.java")

for (const column of ["销售门店", "发货仓库", "通知总数量", "已发货数量", "待发货数量"]) {
  assert.ok(
    deliveryNoticeDomainSource.includes(`@Excel(name = "${column}`),
    `delivery notice exports should include the readable ${column} column`
  )
}

const deliveryNoticeMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeMapper.xml")

assert.ok(
  deliveryNoticeMapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
    deliveryNoticeMapperSource.includes("coalesce(warehouse_dept.dept_name, detail_warehouse.warehouse_name) as warehouse_name") &&
    deliveryNoticeMapperSource.includes("detail_summary.notice_quantity") &&
    deliveryNoticeMapperSource.includes("detail_summary.delivered_quantity") &&
    deliveryNoticeMapperSource.includes("detail_summary.remaining_quantity"),
  "delivery notice exports should populate organization, warehouse, and delivery quantity summaries instead of only carrying ids"
)

const stockCheckDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheck.java")

for (const column of ["库存组织", "盘点仓库", "盘点商品数", "盘盈项数", "盘亏项数", "差异数量合计"]) {
  assert.ok(
    stockCheckDomainSource.includes(`@Excel(name = "${column}`),
    `stock check exports should include the readable ${column} column`
  )
}

const stockCheckMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckMapper.xml")

assert.ok(
  stockCheckMapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
    stockCheckMapperSource.includes("warehouse_dept.dept_name as warehouse_name") &&
    stockCheckMapperSource.includes("detail_summary.item_count") &&
    stockCheckMapperSource.includes("detail_summary.profit_item_count") &&
    stockCheckMapperSource.includes("detail_summary.loss_item_count") &&
    stockCheckMapperSource.includes("detail_summary.total_diff_quantity"),
  "stock check exports should populate organization, warehouse, and stock check variance summaries instead of only carrying ids"
)

const readableBusinessColumns = [
  [
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseReturn.java",
    ["采购退货单号", "原采购单号", "退货主题", "供应商", "退货金额", "退货总数量", "已退货数量", "待退货数量", "退货日期", "状态", "退货店铺", "申请人账号", "申请人姓名", "申请部门"]
  ],
  [
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesReturn.java",
    ["销售退货单号", "原销售单号", "退货主题", "客户", "退货金额", "退货总数量", "已退货数量", "待退货数量", "退货日期", "状态", "退货店铺", "申请人账号", "申请人姓名", "申请部门"]
  ]
]

for (const [domainPath, columns] of readableBusinessColumns) {
  const domainSource = repoSource(domainPath)
  for (const column of columns) {
    assert.ok(
      domainSource.includes(`@Excel(name = "${column}`),
      `${domainPath} should export a readable ${column} column`
    )
  }
  assert.ok(
    domainSource.includes("returned=已退货"),
    `${domainPath} should translate returned status into a readable export label`
  )
}

for (const mapperPath of [
  "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseReturnMapper.xml",
  "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesReturnMapper.xml"
]) {
  const mapperSource = repoSource(mapperPath)
  assert.ok(
    mapperSource.includes("detail_summary.total_quantity") &&
      mapperSource.includes("detail_summary.returned_quantity") &&
      mapperSource.includes("detail_summary.remaining_quantity"),
    `${mapperPath} should populate return quantity summaries from return details`
  )
  assert.ok(
    mapperSource.includes("shop_dept.dept_name as shop_dept_name") &&
      mapperSource.includes("u.nick_name as applicant_nick_name") &&
      mapperSource.includes("applicant_dept.dept_name as applicant_dept_name"),
    `${mapperPath} should populate return shop, applicant name, and applicant department from system tables`
  )
}

const businessExportDomains = [
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvCustomer.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNotice.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvProduct.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseOrder.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseReturn.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesReturn.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheck.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockLog.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSupplier.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalRule.java",
  "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferApprovalNode.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaPurchase.java",
  "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSalaryRecord.java",
  "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysSalaryScheme.java",
  "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysSalarySchemeItem.java"
]

for (const domainPath of businessExportDomains) {
  const domainSource = repoSource(domainPath)
  assert.ok(
    !/@Excel\(name\s*=\s*"[^"]*ID"/.test(domainSource),
    `${domainPath} should not expose internal database ID columns in business-facing exports`
  )
}

const customerDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvCustomer.java")
const customerMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerMapper.xml")
const supplierDomainSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSupplier.java")
const supplierMapperSource = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSupplierMapper.xml")

for (const [domainSource, mapperSource, label] of [
  [customerDomainSource, customerMapperSource, "customer"],
  [supplierDomainSource, supplierMapperSource, "supplier"]
]) {
  assert.ok(
    domainSource.includes("@Excel(name = \"所属店铺\")") &&
      mapperSource.includes("shop_dept.dept_name as shop_dept_name"),
    `${label} exports should include the readable owning shop for multi-shop master data`
  )
}

const managementExportDomains = [
  "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java",
  "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysRole.java",
  "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysDictType.java",
  "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysLogininfor.java",
  "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysOperLog.java",
  "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java",
  "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysPost.java",
  "erp-modules/erp-job/src/main/java/com/erp/job/domain/SysJob.java",
  "erp-modules/erp-job/src/main/java/com/erp/job/domain/SysJobLog.java"
]

for (const domainPath of managementExportDomains) {
  const domainSource = repoSource(domainPath)
  const labels = [...domainSource.matchAll(/@Excel\(name\s*=\s*"([^"]+)"/g)].map((match) => match[1])
  assert.ok(labels.every((label) => !/(主键|序号|调用目标字符串)/.test(label)), `${domainPath} should use user-facing labels instead of database terms`)
  assert.ok(labels.every((label) => label === label.trim()), `${domainPath} should not export labels with leading or trailing spaces`)
}

const jobDomainSource = repoSource("erp-modules/erp-job/src/main/java/com/erp/job/domain/SysJob.java")
const jobLogDomainSource = repoSource("erp-modules/erp-job/src/main/java/com/erp/job/domain/SysJobLog.java")

assert.ok(
  jobDomainSource.includes("@Excel(name = \"Cron表达式\")"),
  "job exports should name cron fields explicitly instead of the vague 执行表达式"
)

assert.ok(
  jobDomainSource.includes("@Excel(name = \"任务分组\", readConverterExp = \"DEFAULT=默认,SYSTEM=系统\")") &&
    jobLogDomainSource.includes("@Excel(name = \"任务分组\", readConverterExp = \"DEFAULT=默认,SYSTEM=系统\")"),
  "job exports should translate task group codes such as DEFAULT/SYSTEM into readable labels"
)

assert.ok(
  jobLogDomainSource.includes("@Excel(name = \"开始时间\", width = 30, dateFormat = \"yyyy-MM-dd HH:mm:ss\")") &&
    jobLogDomainSource.includes("@Excel(name = \"结束时间\", width = 30, dateFormat = \"yyyy-MM-dd HH:mm:ss\")"),
  "job log exports should include readable start and end times so users can understand when a task ran"
)

const loginInfoDomainSource = repoSource("erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysLogininfor.java")

assert.ok(
  loginInfoDomainSource.includes("@Excel(name = \"登录IP\")") &&
    loginInfoDomainSource.includes("@Excel(name = \"登录结果\")") &&
    loginInfoDomainSource.includes("@Excel(name = \"登录状态\""),
  "login log exports should make IP, status, and result columns explicit instead of 地址/状态/描述"
)

const userDomainSource = repoSource("erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java")
const userViewSource = source("src/views/system/user/index.vue")
const userControllerSource = repoSource("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java")
const userMapperSource = repoSource("erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml")
const userProfileMapperSource = repoSource("erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml")

assert.ok(
  userDomainSource.includes("@Excel(name = \"用户账号\")") &&
    userDomainSource.includes("@Excel(name = \"姓名\")"),
  "user exports should distinguish account and employee name columns clearly"
)

assert.ok(
  userViewSource.includes('template-file-name="用户导入模板"') &&
    userControllerSource.includes("importTemplateExcel(response, \"用户导入模板\")"),
  "user import templates should use readable Chinese template names in the downloaded file and workbook title"
)

const dictDataDomainSource = repoSource("erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysDictData.java")
const dictDataViewSource = source("src/views/system/dict/data.vue")
const dictDataControllerSource = repoSource("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictDataController.java")

assert.ok(
  dictDataDomainSource.includes("@Excel(name = \"字典项编号\"") &&
    !dictDataDomainSource.includes("@Excel(name = \"字典编码\""),
  "dictionary data exports should call dictCode 字典项编号 because 字典编码 is easily confused with 字典键值"
)

assert.ok(
  dictDataViewSource.includes("label=\"字典项编号\""),
  "dictionary data list should use the same readable 字典项编号 label as the export"
)

assert.ok(
  dictDataViewSource.includes("exportFileName('字典项数据')") ||
    dictDataViewSource.includes('exportFileName("字典项数据")'),
  "dictionary data exports should use 字典项数据 as the file name to avoid confusion with dictionary type exports"
)

assert.ok(
  dictDataControllerSource.includes("exportExcel(response, list, \"字典项数据\")"),
  "dictionary data backend export title should match the readable 字典项数据 file name"
)

const salarySchemeExportPath = "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysSalarySchemeExport.java"

assert.ok(
  repoFileExists(salarySchemeExportPath),
  "salary scheme exports should use a flattened export row so exported files include both scheme info and salary grade details"
)

const salarySchemeExportSource = repoSource(salarySchemeExportPath)
const salarySchemeControllerSource = repoSource("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java")
const salarySchemeServiceSource = repoSource("erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSalaryConfigServiceImpl.java")
const salarySchemeServiceInterfaceSource = repoSource("erp-modules/erp-system/src/main/java/com/erp/system/service/ISysSalaryConfigService.java")
const salarySchemeViewSource = source("src/views/system/salary/index.vue")

for (const column of ["方案名称", "社保口径", "生效日期", "状态", "岗位", "档位", "地区", "基本工资", "管理津贴", "加班费", "奖励津贴", "全勤奖", "社保补贴", "通勤补贴", "工资合计"]) {
  assert.ok(
    salarySchemeExportSource.includes(`@Excel(name = "${column}`),
    `salary scheme exports should include the readable ${column} column`
  )
}

assert.ok(
  salarySchemeControllerSource.includes("ExcelUtil<SysSalarySchemeExport>") &&
    salarySchemeControllerSource.includes("selectSalarySchemeExportList"),
  "salary scheme export controller should export flattened scheme detail rows instead of only high-level schemes"
)

assert.ok(
  salarySchemeViewSource.includes("handleExportScheme()") &&
    salarySchemeViewSource.includes("system/salaryConfig/export") &&
    salarySchemeViewSource.includes("exportFileName(\"薪资方案\")"),
  "salary scheme export button should call a working export handler with the readable 薪资方案 file name"
)

assert.ok(
  salarySchemeServiceInterfaceSource.includes("List<SysSalarySchemeExport> selectSalarySchemeExportList") &&
    salarySchemeServiceSource.includes("selectSalarySchemeExportList") &&
    salarySchemeServiceSource.includes("itemMapper.selectSalarySchemeItemsBySchemeId"),
  "salary scheme export service should expand each scheme into its salary grade detail rows"
)

const operLogDomainSource = repoSource("erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysOperLog.java")

for (const column of ["后端方法", "HTTP方法", "请求URL", "响应结果", "操作状态", "耗时"]) {
  assert.ok(
    operLogDomainSource.includes(`@Excel(name = "${column}`),
    `operation log exports should use the explicit ${column} column label`
  )
}

const transferControllerSource = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java")

assert.ok(
  transferControllerSource.includes("exportRows(response, transfer, request, \"调拨处理中数据\")") &&
    transferControllerSource.includes("util.exportExcel(response, list, sheetName)"),
  "transfer processing export title should match its visible page and file name"
)

const attendanceV2ApiSource = source("src/api/oa/attendanceV2.js")
assert.ok(
  attendanceV2ApiSource.includes("/evidence/${evidenceId}/content") &&
    attendanceV2ApiSource.includes("responseType: 'blob'") &&
    !attendanceV2ApiSource.includes("/export"),
  "attendance V2 evidence must use authenticated blob reads without a legacy export endpoint"
)
