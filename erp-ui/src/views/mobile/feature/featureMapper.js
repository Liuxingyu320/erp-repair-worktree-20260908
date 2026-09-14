const { imageUrls } = require("../../../utils/imageGallery")
const { purchaseBusinessStageLabel } = require("../../../utils/purchaseBusinessStage")
const {
  STATUS_LABELS,
  businessTypeLabel,
  configTypeLabel,
  dataScopeLabel,
  deptTypeLabel,
  menuTypeLabel,
  movementTypeLabel,
  statusLabel,
  stockStatusLabel,
  transferTypeLabel
} = require("./mobileFeatureStatusPolicy")
const {
  canViewCost,
  firstText,
  firstValue,
  formatDateText,
  formatDateTimeText,
  formatMoneyText,
  formatProductReferenceCostText,
  formatQuantity,
  formatSignedQuantity,
  formatStockReferenceCostText,
  formatTimeText,
  hasValue,
  isStoreContext,
  joinDetail,
  safeText
} = require("./mobileFeatureValuePolicy")
const {
  buildMobileDetailSections,
  resolveTransferTotalQuantity,
  transferApprovalSummaryParts
} = require("./mobileFeatureDetailSectionsPolicy")
const {
  buildMobileDetailFields,
  buildMobileReviewFields,
  isNoticeRead,
  resolveTransferApprovalNodeName,
  transferBusinessTypeLabel
} = require("./mobileFeatureDetailFieldsPolicy")

function mapMobileFeatureRows(featureKey, rows, options) {
  const list = Array.isArray(rows) ? rows : []
  const mapperOptions = options || {}

  return list.map(function(row, index) {
    let item
    if (featureKey === "sales") item = mapSalesRow(row, index)
    else if (featureKey === "purchase") item = mapPurchaseRow(row, index)
    else if (featureKey === "stock") item = mapStockRow(row, index, mapperOptions)
    else if (featureKey === "replenishment") item = mapTransferRow(row, index)
    else if (featureKey === "product") item = mapProductRow(row, index, mapperOptions)
    else if (featureKey === "oe") item = mapOeRow(row, index, mapperOptions)
    else if (featureKey === "gift") item = mapGiftRow(row, index)
    else if (featureKey === "category") item = mapCategoryRow(row, index)
    else if (featureKey === "customer") item = mapCustomerRow(row, index)
    else if (featureKey === "supplier") item = mapSupplierRow(row, index)
    else if (featureKey === "stockLog") item = mapStockLogRow(row, index)
    else if (featureKey === "stockCheck") item = mapStockCheckRow(row, index)
    else if (featureKey === "salesReturn") item = mapSalesReturnRow(row, index)
    else if (featureKey === "purchaseReturn") item = mapPurchaseReturnRow(row, index)
    else if (featureKey === "outbound") item = mapOutboundRow(row, index)
    else if (featureKey === "transfer") item = mapTransferRow(row, index)
    else if (featureKey === "transferApproval") item = mapTransferApprovalRow(row, index)
    else if (featureKey === "transferRecords") item = mapTransferRecordsRow(row, index)
    else if (featureKey === "fixedAssetRepair") item = mapFixedAssetRepairRow(row, index)
    else if (featureKey === "oaPurchase") item = mapOaPurchaseRow(row, index)
    else if (featureKey === "attendance") item = mapAttendanceRow(row, index)
    else if (featureKey === "salary") item = mapSalaryRow(row, index)
    else if (featureKey === "notice") item = mapNoticeRow(row, index)
    else if (featureKey === "profile") item = mapProfileRow(row, index, mapperOptions)
    else if (featureKey === "systemUser") item = mapSystemUserRow(row, index)
    else if (featureKey === "systemRole") item = mapSystemRoleRow(row, index)
    else if (featureKey === "systemPost") item = mapSystemPostRow(row, index)
    else if (featureKey === "systemDept") item = mapSystemDeptRow(row, index)
    else if (featureKey === "systemMenu") item = mapSystemMenuRow(row, index)
    else if (featureKey === "userShop") item = mapUserShopRow(row, index)
    else if (featureKey === "systemConfig") item = mapSystemConfigRow(row, index)
    else if (featureKey === "systemDictType") item = mapSystemDictTypeRow(row, index)
    else if (featureKey === "systemDictData") item = mapSystemDictDataRow(row, index)
    else if (featureKey === "systemLogininfor") item = mapSystemLogininforRow(row, index)
    else if (featureKey === "systemOperlog") item = mapSystemOperlogRow(row, index)
    else if (featureKey === "monitorJob") item = mapMonitorJobRow(row, index)
    else if (featureKey === "monitorJobLog") item = mapMonitorJobLogRow(row, index)
    else if (featureKey === "monitorOnline") item = mapMonitorOnlineRow(row, index)
    else if (featureKey === "salaryScheme") item = mapSalarySchemeRow(row, index)
    else if (featureKey === "transferRules") item = mapTransferRulesRow(row, index)
    else item = mapGenericRow(row, index)
    return attachMobileActionMeta(item, featureKey, row, mapperOptions)
  })
}

function mapMobileMineRows(options) {
  const source = options || {}
  const selectedDeptName = safeText(source.selectedDeptName, "未选择")
  const selectedDeptType = safeText(source.selectedDeptType, "")
  const userName = safeText(source.userName, "当前账号")
  const contextTitle = selectedDeptType === "WAREHOUSE" ? "当前仓库" : "当前店铺"

  return [
    {
      title: contextTitle,
      code: selectedDeptName,
      detail: "已应用到手机网页端业务接口",
      status: selectedDeptName === "未选择" ? "未选择" : "已选择"
    },
    {
      title: "登录账号",
      code: userName,
      detail: "权限与电脑端保持一致",
      status: "可用"
    }
  ]
}

function mapSalesRow(row, index) {
  const orderNo = firstText(row, ["orderNo", "salesOrderNo", "code"])
  const customer = firstText(row, ["customerName", "customer", "targetDeptName"])
  const amount = firstValue(row, ["totalAmount", "amount"])

  return {
    title: safeText(firstText(row, ["orderTitle", "title"]), customer || "销售单"),
    code: safeText(orderNo, "SO-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([customer, formatMoneyText(amount), formatDateTimeText(firstText(row, ["createTime", "orderDate"]))]),
    status: statusLabel(row.status, STATUS_LABELS.sales)
  }
}

function mapPurchaseRow(row, index) {
  const orderNo = firstText(row, ["orderNo", "purchaseNo", "code"])
  const supplier = firstText(row, ["supplierName", "supplier", "vendorName"])
  const amount = firstValue(row, ["totalAmount", "amount"])

  return {
    title: safeText(firstText(row, ["orderTitle", "title"]), supplier || "采购单"),
    code: safeText(orderNo, "PO-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([supplier, formatMoneyText(amount), formatDateTimeText(firstText(row, ["createTime", "orderDate"]))]),
    status: purchaseBusinessStageLabel(row)
  }
}

function mapStockRow(row, index, options) {
  const product = row && row.product ? row.product : {}
  const productName = firstText(row, ["productName", "goodsName", "skuName"]) ||
    firstText(product, ["productName", "goodsName", "skuName"])
  const productCode = firstText(row, ["productCode", "skuCode", "stockCode", "code"]) ||
    firstText(product, ["productCode", "skuCode", "code"])
  const available = firstValue(row, ["availableQuantity", "currentQuantity", "quantity", "stockQuantity"])
  const warehouse = firstText(row, ["warehouseName", "warehouseDeptName", "shopName", "shopDeptName"])
  const unit = firstText(row, ["unit"]) || firstText(product, ["unit"])
  const referenceCostText = canViewCost(options) ? formatStockReferenceCostText(row) : ""

  return {
    title: safeText(productName, "商品库存"),
    code: safeText(productCode, "ST-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([warehouse, "可用 " + formatQuantity(available) + (unit ? " " + unit : ""), referenceCostText]),
    status: stockStatusLabel(available)
  }
}

function mapProductRow(row, index, options) {
  const productName = firstText(row, ["productName", "goodsName", "name"])
  const productCode = firstText(row, ["productCode", "skuCode", "code"])
  const category = firstText(row, ["categoryName", "categoryFullPath"])
  const supplier = firstText(row, ["supplierName"])
  const referenceCostText = canViewCost(options) ? formatProductReferenceCostText(row) : ""
  const detailParts = isStoreContext(options)
    ? [referenceCostText]
    : [category, supplier, referenceCostText]

  return {
    title: safeText(productName, "商品资料"),
    code: safeText(productCode, "PD-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail(detailParts),
    status: statusLabel(row.status, STATUS_LABELS.product)
  }
}

function mapOeRow(row, index, options) {
  const oeName = firstText(row, ["oeItemName", "itemName", "name"])
  const oeCode = firstText(row, ["oeItemCode", "code"])
  const category = firstText(row, ["categoryName", "categoryFullPath"])
  const supplier = firstText(row, ["supplierName"])
  const costText = formatMoneyText(firstValue(row, ["costPrice"]))
  const detailParts = isStoreContext(options)
    ? [category, costText ? "成本价 " + costText : ""]
    : [category, supplier, costText ? "成本价 " + costText : ""]

  return {
    title: safeText(oeName, "OE资料"),
    imageUrls: imageUrls(row),
    code: safeText(oeCode, "OE-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail(detailParts),
    status: statusLabel(row.status, STATUS_LABELS.oe)
  }
}

function mapGiftRow(row, index) {
  const giftName = firstText(row, ["giftName", "name"])
  const giftCode = firstText(row, ["giftCode", "code"])
  const category = firstText(row, ["categoryName", "categoryFullPath"])
  const price1 = formatMoneyText(firstValue(row, ["guidePrice1"]))
  const price2 = formatMoneyText(firstValue(row, ["guidePrice2"]))

  return {
    title: safeText(giftName, "礼盒资料"),
    imageUrls: imageUrls(row),
    code: safeText(giftCode, "GIFT-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([category, price1 ? "指导售价1 " + price1 : "", price2 ? "指导售价2 " + price2 : ""]),
    status: statusLabel(row.status, STATUS_LABELS.gift)
  }
}

function mapCategoryRow(row, index) {
  const categoryName = firstText(row, ["categoryName", "name"])
  const categoryCode = firstText(row, ["categoryCode", "code"])
  const fullPath = firstText(row, ["categoryFullPath", "path"])
  const level = firstValue(row, ["categoryLevel", "level"])

  return {
    title: safeText(categoryName, "商品分类"),
    code: safeText(categoryCode, "CAT-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([fullPath, hasValue(level) ? "第 " + level + " 级" : ""]),
    status: statusLabel(row.status, STATUS_LABELS.category)
  }
}

function mapCustomerRow(row, index) {
  const customerName = firstText(row, ["customerName", "name"])
  const customerCode = firstText(row, ["customerCode", "code"])
  const preferences = firstText(row, ["teaPreferences", "preferenceTags"])
  const caution = firstText(row, ["cautions"])

  return {
    title: safeText(customerName, "客户资料"),
    code: safeText(customerCode, "CU-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["contactPhone"]), preferences, caution]),
    status: statusLabel(row.status, STATUS_LABELS.customer)
  }
}

function customerBudgetText(row) {
  const min = firstValue(row, ["budgetMin"])
  const max = firstValue(row, ["budgetMax"])
  if (!hasValue(min) && !hasValue(max)) return ""
  if (hasValue(min) && hasValue(max)) return formatMoneyText(min) + " - " + formatMoneyText(max) + "/人"
  return "约 " + formatMoneyText(hasValue(min) ? min : max) + "/人"
}

function mapSupplierRow(row, index) {
  const supplierName = firstText(row, ["supplierName", "name"])
  const supplierCode = firstText(row, ["supplierCode", "code"])
  const contact = firstText(row, ["contactPerson", "contactName"])
  const settlement = firstText(row, ["settlementMethod"])

  return {
    title: safeText(supplierName, "供应商资料"),
    code: safeText(supplierCode, "SP-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([contact, settlement, firstText(row, ["contactPhone"])]),
    status: statusLabel(firstText(row, ["cooperationStatus"]) || row.status, STATUS_LABELS.supplier)
  }
}

function mapStockLogRow(row, index) {
  const product = row && row.product ? row.product : {}
  const productName = firstText(row, ["productName", "goodsName"]) || firstText(product, ["productName", "goodsName"])
  const productCode = firstText(row, ["productCode", "skuCode", "code"]) || firstText(product, ["productCode", "skuCode", "code"])
  const movementType = firstText(row, ["movementType", "businessType"])
  const businessNo = firstText(row, ["businessNo", "orderNo", "code"])
  const changeQuantity = firstValue(row, ["changeQuantity", "quantity"])
  const afterQuantity = firstValue(row, ["afterQuantity", "currentQuantity", "stockQuantity"])

  return {
    title: safeText(productName, "库存流水"),
    code: safeText(productCode, "LOG-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([movementTypeLabel(movementType), businessNo, hasValue(changeQuantity) ? "变动 " + formatSignedQuantity(changeQuantity) : ""]),
    status: hasValue(afterQuantity) ? "结余 " + formatQuantity(afterQuantity) : "可查看"
  }
}

function mapStockCheckRow(row, index) {
  const checkNo = firstText(row, ["checkNo", "orderNo", "code"])
  const warehouse = firstText(row, ["warehouseName", "warehouseDeptName", "shopDeptName"])
  const checkDate = firstText(row, ["checkDate", "createTime"])

  return {
    title: safeText(checkNo, "盘点单"),
    code: safeText(checkNo, "CK-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([warehouse, checkDate, firstText(row, ["remark"])]),
    status: statusLabel(row.status, STATUS_LABELS.stockCheck)
  }
}

function mapSalesReturnRow(row, index) {
  const returnNo = firstText(row, ["returnNo", "orderNo", "salesReturnNo", "code"])
  const customer = firstText(row, ["customerName", "customer", "targetDeptName"])
  const amount = firstValue(row, ["totalAmount", "amount", "returnAmount"])

  return {
    title: safeText(firstText(row, ["returnTitle", "orderTitle", "title"]), customer || "销售退货"),
    code: safeText(returnNo, "SR-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([customer, formatMoneyText(amount), formatDateTimeText(firstText(row, ["createTime", "returnDate"]))]),
    status: statusLabel(row.status, STATUS_LABELS.salesReturn)
  }
}

function mapPurchaseReturnRow(row, index) {
  const returnNo = firstText(row, ["returnNo", "orderNo", "purchaseReturnNo", "code"])
  const supplier = firstText(row, ["supplierName", "supplier", "vendorName", "targetDeptName"])
  const amount = firstValue(row, ["totalAmount", "amount", "returnAmount"])

  return {
    title: safeText(firstText(row, ["returnTitle", "orderTitle", "title"]), supplier || "采购退货"),
    code: safeText(returnNo, "PR-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([supplier, formatMoneyText(amount), formatDateTimeText(firstText(row, ["createTime", "returnDate"]))]),
    status: statusLabel(row.status, STATUS_LABELS.purchaseReturn)
  }
}

function mapOutboundRow(row, index) {
  const noticeNo = firstText(row, ["noticeNo", "deliveryNo", "orderNo", "code"])
  const customer = firstText(row, ["customerName", "customer", "targetDeptName"])
  const itemCount = firstValue(row, ["itemCount", "productCount", "detailCount", "totalQuantity"])
  const itemText = hasValue(itemCount) ? formatQuantity(itemCount) + " 件商品" : ""

  return {
    title: safeText(firstText(row, ["noticeTitle", "orderTitle", "title"]), customer || "发货通知"),
    code: safeText(noticeNo, "DN-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([customer, itemText, formatDateTimeText(firstText(row, ["createTime", "noticeDate"]))]),
    status: statusLabel(row.status, STATUS_LABELS.outbound)
  }
}

function mapTransferRow(row, index) {
  const transferNo = firstText(row, ["transferNo", "orderNo", "code"])
  const fromDept = firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])
  const toDept = firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])
  const title = fromDept && toDept ? fromDept + " → " + toDept : firstText(row, ["transferTitle", "title"])

  return {
    title: safeText(title, "调拨单"),
    code: safeText(transferNo, "TF-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([transferBusinessTypeLabel(row), fromDept, toDept, formatDateTimeText(firstText(row, ["createTime", "transferDate"]))].concat(transferApprovalSummaryParts(row))),
    status: transferStatusLabel(row)
  }
}

function mapTransferApprovalRow(row, index) {
  const transferNo = firstText(row, ["transferNo", "orderNo", "code"])
  const fromDept = firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])
  const toDept = firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])
  const title = fromDept && toDept ? fromDept + " → " + toDept : firstText(row, ["transferTitle", "title"])

  return {
    title: safeText(title, "调拨审批"),
    code: safeText(transferNo, "TFA-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([transferBusinessTypeLabel(row), resolveTransferApprovalNodeName(row), formatDateTimeText(firstText(row, ["submittedTime", "createTime"]))].concat(transferApprovalSummaryParts(row))),
    status: statusLabel(row.status, STATUS_LABELS.transferApproval)
  }
}

function mapTransferRecordsRow(row, index) {
  const transferNo = firstText(row, ["transferNo", "orderNo", "code"])
  const fromDept = firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])
  const toDept = firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])
  const title = fromDept && toDept ? fromDept + " → " + toDept : firstText(row, ["transferTitle", "title"])

  return {
    title: safeText(title, "调拨记录"),
    code: safeText(transferNo, "TFR-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([transferBusinessTypeLabel(row), formatDateTimeText(firstText(row, ["receivedTime", "archivedTime", "createTime"]))].concat(transferApprovalSummaryParts(row))),
    status: transferStatusLabel(row)
  }
}

function mapFixedAssetRepairRow(row, index) {
  const repairId = firstText(row, ["repairId", "id"])

  return {
    title: safeText(firstText(row, ["oeItemName", "assetName", "goodsName"]), "资产报修"),
    imageUrls: imageUrls(row),
    code: safeText(repairId ? "FA-" + repairId : "", "FA-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      firstText(row, ["faultDescription"]),
      hasValue(firstValue(row, ["repairQuantity"])) ? "数量 " + formatQuantity(firstValue(row, ["repairQuantity"])) : "",
      firstText(row, ["shopDeptName", "shopName"])
    ]),
    status: statusLabel(row.status, STATUS_LABELS.fixedAssetRepair)
  }
}

function mapOaPurchaseRow(row, index) {
  const purchaseId = firstText(row, ["purchaseId", "id"])
  const amount = firstValue(row, ["amount", "totalAmount"])
  const createTime = formatDateTimeText(firstText(row, ["createTime", "applyTime"]))

  return {
    title: safeText(firstText(row, ["title", "purchaseTitle"]), "采购申请"),
    code: safeText(purchaseId, "OA-P-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([formatMoneyText(amount), createTime]),
    status: statusLabel(row.status, STATUS_LABELS.oaPurchase)
  }
}

function mapAttendanceRow(row, index) {
  const recordId = firstText(row, ["recordId", "id"])
  const workDate = formatDateText(firstText(row, ["workDate", "attendanceDate", "createTime"]))
  const checkInTime = formatTimeText(firstText(row, ["checkInTime"]))
  const checkOutTime = formatTimeText(firstText(row, ["checkOutTime"]))
  const workHours = firstValue(row, ["workHours"])

  return {
    title: safeText(workDate, "考勤记录"),
    code: safeText(recordId ? "ATT-" + recordId : "", "ATT-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      checkInTime ? "上班 " + checkInTime : "",
      checkOutTime ? "下班 " + checkOutTime : "",
      hasValue(workHours) ? "工时 " + formatQuantity(workHours) + "h" : ""
    ]),
    status: statusLabel(row.status, STATUS_LABELS.attendance)
  }
}

function mapSalaryRow(row, index) {
  const salaryId = firstText(row, ["salaryId", "id"])
  const salaryMonth = firstText(row, ["salaryMonth", "month"])
  const userName = firstText(row, ["userName", "nickName"])
  const workDays = firstValue(row, ["workDays"])
  const totalSalary = firstValue(row, ["totalSalary", "salaryAmount"])

  return {
    title: safeText(salaryMonth ? salaryMonth + " 工资" : "", "工资记录"),
    code: safeText(salaryId ? "SAL-" + salaryId : "", "SAL-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      userName,
      hasValue(workDays) ? "出勤 " + formatQuantity(workDays) + " 天" : "",
      formatMoneyText(totalSalary)
    ]),
    status: firstText(row, ["salaryStatus", "status"])
      ? statusLabel(firstText(row, ["salaryStatus", "status"]), STATUS_LABELS.salary)
      : "已生成"
  }
}

function mapNoticeRow(row, index) {
  const noticeId = firstText(row, ["noticeId", "id"])
  const noticeType = firstText(row, ["noticeType"])
  const createBy = firstText(row, ["createBy"])
  const createTime = formatDateTimeText(firstText(row, ["createTime"]))

  return {
    title: safeText(firstText(row, ["noticeTitle", "title"]), "通知公告"),
    code: safeText(noticeId ? "NOTICE-" + noticeId : "", "NOTICE-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([statusLabel(noticeType, STATUS_LABELS.notice), createBy, createTime]),
    status: isNoticeRead(row) ? "已读" : "未读"
  }
}

function mapProfileRow(row, index, options) {
  const dept = row && row.dept ? row.dept : {}
  const deptName = firstText(row, ["deptName"]) || firstText(dept, ["deptName"])
  const selectedDeptName = options && options.selectedDeptName ? options.selectedDeptName : ""

  return {
    title: safeText(firstText(row, ["nickName", "userName"]), "当前账号"),
    code: safeText(firstText(row, ["userName", "loginName"]), "USER"),
    detail: joinDetail([selectedDeptName || deptName, firstText(row, ["phonenumber", "phone"]), firstText(row, ["email"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.profile)
  }
}

function mapSystemUserRow(row, index) {
  const dept = row && row.dept ? row.dept : {}
  const deptName = firstText(row, ["deptName"]) || firstText(dept, ["deptName"])

  return {
    title: safeText(firstText(row, ["nickName", "userName"]), "系统用户"),
    code: safeText(firstText(row, ["userName", "loginName"]), "USER-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([deptName, firstText(row, ["phonenumber", "phone"]), firstText(row, ["email"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemRoleRow(row, index) {
  return {
    title: safeText(firstText(row, ["roleName"]), "系统角色"),
    code: safeText(firstText(row, ["roleKey"]), "ROLE-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      hasValue(firstValue(row, ["roleSort"])) ? "排序 " + firstValue(row, ["roleSort"]) : "",
      dataScopeLabel(firstText(row, ["dataScope"]))
    ]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemPostRow(row, index) {
  return {
    title: safeText(firstText(row, ["postName"]), "系统岗位"),
    code: safeText(firstText(row, ["postCode"]), "POST-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      hasValue(firstValue(row, ["postSort"])) ? "排序 " + firstValue(row, ["postSort"]) : "",
      firstText(row, ["remark"])
    ]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemDeptRow(row, index) {
  return {
    title: safeText(firstText(row, ["deptName"]), "组织部门"),
    code: safeText(firstText(row, ["deptId", "deptCode", "id"]), "DEPT-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      deptTypeLabel(firstText(row, ["deptType"])),
      firstText(row, ["leader"]),
      firstText(row, ["phone"]),
      firstText(row, ["deptFullPath"])
    ]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemMenuRow(row, index) {
  return {
    title: safeText(firstText(row, ["menuName"]), "系统菜单"),
    code: safeText(firstText(row, ["perms", "path", "menuId", "id"]), "MENU-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([
      menuTypeLabel(firstText(row, ["menuType"]), firstText(row, ["isFrame"])),
      firstText(row, ["path"]),
      firstText(row, ["component"])
    ]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapUserShopRow(row, index) {
  const dept = row && row.dept ? row.dept : {}
  const scopeLabel = firstText(row, ["shopScopeLabel", "scopeLabel"]) || firstText(row, ["deptName"]) || firstText(dept, ["deptName"])

  return {
    title: safeText(firstText(row, ["nickName", "userName"]), "授权用户"),
    code: safeText(firstText(row, ["userName", "loginName"]), "USER-SHOP-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([scopeLabel, firstText(row, ["phonenumber", "phone"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemConfigRow(row, index) {
  return {
    title: safeText(firstText(row, ["configName"]), "参数设置"),
    code: safeText(firstText(row, ["configKey"]), "CONFIG-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["configValue"]), firstText(row, ["remark"])]),
    status: configTypeLabel(firstText(row, ["configType"]))
  }
}

function mapSystemDictTypeRow(row, index) {
  return {
    title: safeText(firstText(row, ["dictName"]), "字典类型"),
    code: safeText(firstText(row, ["dictType"]), "DICT-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["remark"]), formatDateTimeText(firstText(row, ["createTime"]))]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemDictDataRow(row, index) {
  return {
    title: safeText(firstText(row, ["dictLabel"]), "字典数据"),
    code: safeText(firstText(row, ["dictValue"]), "DATA-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["dictType"]), hasValue(firstValue(row, ["dictSort"])) ? "排序 " + firstValue(row, ["dictSort"]) : ""]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapSystemLogininforRow(row, index) {
  return {
    title: safeText(firstText(row, ["userName"]), "登录日志"),
    code: safeText(firstText(row, ["infoId", "id"]), "INFO-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["ipaddr"]), firstText(row, ["loginLocation"]), firstText(row, ["loginTime", "accessTime"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.logStatus)
  }
}

function mapSystemOperlogRow(row, index) {
  const operId = firstText(row, ["operId", "id"])

  return {
    title: safeText(firstText(row, ["title"]), "操作日志"),
    code: safeText(operId ? "OPER-" + operId : "", "OPER-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([businessTypeLabel(firstValue(row, ["businessType"])), firstText(row, ["operName"]), firstText(row, ["operIp"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.operStatus)
  }
}

function mapMonitorJobRow(row, index) {
  const jobId = firstText(row, ["jobId", "id"])

  return {
    title: safeText(firstText(row, ["jobName"]), "定时任务"),
    code: safeText(jobId ? "JOB-" + jobId : "", "JOB-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["jobGroup"]), firstText(row, ["cronExpression"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.jobStatus)
  }
}

function mapMonitorJobLogRow(row, index) {
  const jobLogId = firstText(row, ["jobLogId", "id"])

  return {
    title: safeText(firstText(row, ["jobName"]), "调度日志"),
    code: safeText(jobLogId ? "JOBLOG-" + jobLogId : "", "JOBLOG-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["jobGroup"]), firstText(row, ["jobMessage"]), formatDateTimeText(firstText(row, ["createTime"]))]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.logStatus)
  }
}

function mapMonitorOnlineRow(row, index) {
  return {
    title: safeText(firstText(row, ["userName"]), "在线用户"),
    code: safeText(maskSessionId(firstText(row, ["tokenId"])) || firstText(row, ["ipaddr", "id"]), "ONLINE-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["ipaddr"]), firstText(row, ["loginLocation"]), firstText(row, ["browser"])]),
    status: "在线"
  }
}

function mapSalarySchemeRow(row, index) {
  const schemeId = firstText(row, ["schemeId", "id"])

  return {
    title: safeText(firstText(row, ["schemeName", "name"]), "薪资方案"),
    code: safeText(schemeId ? "SCHEME-" + schemeId : "", "SCHEME-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["roleNames", "roleName"]), formatMoneyText(firstValue(row, ["baseSalary"]))]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapTransferRulesRow(row, index) {
  const ruleId = firstText(row, ["ruleId", "id"])

  return {
    title: safeText(firstText(row, ["ruleName", "name"]), "调拨规则"),
    code: safeText(ruleId ? "RULE-" + ruleId : "", "RULE-" + String(index + 1).padStart(3, "0")),
    detail: joinDetail([firstText(row, ["sourceDeptName", "fromDeptName"]), firstText(row, ["targetDeptName", "toDeptName"])]),
    status: statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)
  }
}

function mapGenericRow(row, index) {
  return {
    title: safeText(firstText(row, ["title", "name"]), "记录"),
    code: safeText(firstText(row, ["code", "id"]), "MOBILE-" + String(index + 1).padStart(3, "0")),
    detail: safeText(firstText(row, ["detail", "remark"]), "-"),
    status: hasValue(firstText(row, ["status"])) ? "状态待确认" : "可查看"
  }
}

function transferStatusLabel(row) {
  const status = firstText(row, ["status"])
  const transferType = firstText(row, ["transferType"])
  const sourceStatus = firstText(row, ["sourceConfirmStatus"]).toUpperCase()
  if (transferType === "cross_store") {
    if (status === "draft" && sourceStatus === "RESELECT_REQUIRED") {
      return "待重选调出店"
    }
    if (status === "approved" &&
      (!sourceStatus || ["NOT_STARTED", "PENDING"].indexOf(sourceStatus) > -1)) {
      return "待调出店确认"
    }
  }
  return statusLabel(status, STATUS_LABELS.transfer)
}

function attachMobileActionMeta(item, featureKey, row, options) {
  if (!item || !row || typeof row !== "object") return item

  const detailFields = buildMobileDetailFields(featureKey, row, item, options)
  Object.defineProperty(item, "_detailFields", {
    value: detailFields,
    enumerable: false
  })
  Object.defineProperty(item, "_detailSections", {
    value: buildMobileDetailSections(featureKey, row, options),
    enumerable: false
  })
  Object.defineProperty(item, "_reviewFields", {
    value: buildMobileReviewFields(featureKey, row, options),
    enumerable: false
  })
  Object.defineProperty(item, "_raw", {
    value: row,
    enumerable: false
  })
  Object.defineProperty(item, "_statusKey", {
    value: firstText(row, ["status"]),
    enumerable: false
  })
  Object.defineProperty(item, "_id", {
    value: resolveActionId(featureKey, row),
    enumerable: false
  })

  return item
}

function resolveActionId(featureKey, row) {
  const idKeys = {
    sales: ["orderId", "salesOrderId", "id"],
    purchase: ["orderId", "purchaseId", "id"],
    stock: ["stockId", "id"],
    replenishment: ["transferId", "id"],
    product: ["productId", "id"],
    category: ["categoryId", "id"],
    customer: ["customerId", "id"],
    supplier: ["supplierId", "id"],
    salesReturn: ["returnId", "salesReturnId", "id"],
    purchaseReturn: ["returnId", "purchaseReturnId", "id"],
    outbound: ["noticeId", "deliveryNoticeId", "id"],
    stockCheck: ["checkId", "stockCheckId", "id"],
    transfer: ["transferId", "id"],
    transferApproval: ["transferId", "id"],
    transferRecords: ["transferId", "id"],
    fixedAssetRepair: ["repairId", "id"],
    oaPurchase: ["purchaseId", "id"],
    attendance: ["recordId", "id"],
    salary: ["salaryId", "id"],
    notice: ["noticeId", "id"],
    profile: ["userId", "id"],
    systemUser: ["userId", "id"],
    systemRole: ["roleId", "id"],
    systemPost: ["postId", "id"],
    systemDept: ["deptId", "id"],
    systemMenu: ["menuId", "id"],
    userShop: ["userId", "id"],
    systemConfig: ["configId", "id"],
    systemDictType: ["dictId", "id"],
    systemDictData: ["dictCode", "id"],
    systemLogininfor: ["infoId", "id"],
    systemOperlog: ["operId", "id"],
    monitorJob: ["jobId", "id"],
    monitorJobLog: ["jobLogId", "id"],
    monitorOnline: ["tokenId", "id"],
    salaryScheme: ["schemeId", "id"],
    transferRules: ["ruleId", "id"]
  }
  return firstValue(row, idKeys[featureKey] || ["id"])
}

module.exports = {
  mapMobileFeatureRows,
  mapMobileMineRows,
  attachMobileActionMeta,
  buildMobileDetailSections,
  buildMobileReviewFields,
  resolveTransferTotalQuantity
}
