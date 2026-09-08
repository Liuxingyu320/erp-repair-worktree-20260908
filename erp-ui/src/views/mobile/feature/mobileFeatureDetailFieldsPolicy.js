const {
  STATUS_LABELS,
  businessTypeLabel,
  configTypeLabel,
  dataScopeLabel,
  deptTypeLabel,
  exceptionTypeLabel,
  menuTypeLabel,
  movementTypeLabel,
  statusLabel,
  transferSourceConfirmLabel,
  transferTypeLabel
} = require("./mobileFeatureStatusPolicy")
const {
  canViewCost,
  firstText,
  firstValue,
  formatDateText,
  formatDateTimeText,
  formatMoneyText,
  formatOptionalQuantity,
  formatQuantity,
  formatSignedQuantity,
  formatTimeText,
  hasValue,
  isStoreContext,
  joinDetail,
  resolveProductReferenceCost,
  resolveStockReferenceCost,
  safeText,
  stockLocationText,
  sumValues
} = require("./mobileFeatureValuePolicy")
const {
  resolveTransferTotalAmount,
  resolveTransferTotalQuantity
} = require("./mobileFeatureDetailSectionsPolicy")

function buildMobileDetailFields(featureKey, row, item, options) {
  if (featureKey === "sales") {
    return compactDetailFields([
      detailField("客户", firstText(row, ["customerName", "customer", "targetDeptName"])),
      detailField("金额", formatMoneyText(firstValue(row, ["totalAmount", "amount"]))),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "orderDate"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "purchase") {
    return compactDetailFields([
      detailField("供应商", firstText(row, ["supplierName", "supplier", "vendorName"])),
      detailField("金额", formatMoneyText(firstValue(row, ["totalAmount", "amount"]))),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "orderDate"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "stock") {
    const product = row.product || {}
    const unit = firstText(row, ["unit"]) || firstText(product, ["unit"])
    const available = firstValue(row, ["availableQuantity", "currentQuantity", "quantity", "stockQuantity"])
    const current = firstValue(row, ["currentQuantity", "stockQuantity"])
    const locked = firstValue(row, ["lockedQuantity"])
    const costFields = canViewCost(options)
      ? [
        detailField("参考成本价", formatMoneyText(resolveStockReferenceCost(row))),
        detailField("库存总成本", formatMoneyText(firstValue(row, ["totalCost"])))
      ]
      : []
    return compactDetailFields([
      detailField("商品编码", firstText(row, ["productCode", "skuCode", "stockCode", "code"]) || firstText(product, ["productCode", "skuCode", "code"])),
      detailField("商品分类", firstText(row, ["categoryFullPath", "categoryName"]) || firstText(product, ["categoryFullPath", "categoryName"])),
      detailField("等级", firstText(row, ["grade"]) || firstText(product, ["grade"])),
      detailField("规格", firstText(row, ["spec", "model"]) || firstText(product, ["spec", "model"])),
      detailField("库存组织", firstText(row, ["shopDeptName", "shopName"])),
      detailField("库存仓库", firstText(row, ["warehouseName", "warehouseDeptName"])),
      detailField("批次号", firstText(row, ["batchNo"])),
      detailField("效期", firstText(row, ["expiryDate"])),
      detailField("序列号", firstText(row, ["serialNo"])),
      detailField("库位", stockLocationText(row)),
      detailField("当前库存", formatOptionalQuantity(current, unit)),
      detailField("锁定库存", formatOptionalQuantity(locked, unit)),
      detailField("可用库存", formatOptionalQuantity(available, unit)),
      ...costFields,
      detailField("预警库存", formatOptionalQuantity(firstValue(row, ["warningQuantity", "minQuantity", "safeQuantity", "safetyStockMin"]), unit)),
      detailField("最后入库", formatDateTimeText(firstText(row, ["lastInTime"]))),
      detailField("最后出库", formatDateTimeText(firstText(row, ["lastOutTime"]))),
      detailField("更新时间", formatDateTimeText(firstText(row, ["updateTime"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "replenishment") {
    return compactDetailFields([
      detailField("要货仓库", firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])),
      detailField("要货门店", firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])),
      ...transferRecipientDetailFields(row),
      detailField("申请数量", formatTransferTotalQuantity(row)),
      firstText(row, ["transferType"]) === "cross_store"
        ? detailField("调出店确认", transferSourceConfirmLabel(firstText(row, ["sourceConfirmStatus"])))
        : null,
      detailField("确认人", firstText(row, ["sourceConfirmedBy"])),
      detailField("确认时间", formatDateTimeText(firstText(row, ["sourceConfirmedTime"]))),
      detailField("确认说明", firstText(row, ["sourceConfirmRemark"])),
      hasValue(firstValue(row, ["reselectionFromTransferId"]))
        ? detailField("余量来源单", "#" + firstValue(row, ["reselectionFromTransferId"]))
        : null,
      detailField("参考总价", formatMoneyText(resolveTransferTotalAmount(row))),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "transferDate"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "product") {
    const productFields = [
      detailField("分类", firstText(row, ["categoryFullPath", "categoryName"])),
      detailField("规格", firstText(row, ["spec", "model"])),
      detailField("单位", firstText(row, ["unit"])),
      canViewCost(options) ? detailField("参考成本价", formatMoneyText(resolveProductReferenceCost(row))) : null,
      detailField("等级", firstText(row, ["grade"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.product))
    ]
    if (!isStoreContext(options)) {
      productFields.splice(1, 0, detailField("供应商", firstText(row, ["supplierName"])))
    }
    return compactDetailFields(productFields)
  }

  if (featureKey === "oe") {
    const oeFields = [
      detailField("上线分类", firstText(row, ["categoryFullPath", "categoryName"])),
      detailField("产品类别名称", firstText(row, ["oeTypeName"])),
      detailField("订货单位", firstText(row, ["orderUnit"])),
      detailField("成本价", formatMoneyText(firstValue(row, ["costPrice"]))),
      detailField("产品描述", firstText(row, ["itemDescription"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.oe))
    ]
    if (!isStoreContext(options)) {
      oeFields.splice(4, 0, detailField("供应商", firstText(row, ["supplierName"])), detailField("供应商电话", firstText(row, ["supplierPhone"])))
    }
    return compactDetailFields(oeFields)
  }

  if (featureKey === "gift") {
    return compactDetailFields([
      detailField("上线分类", firstText(row, ["categoryFullPath", "categoryName"])),
      detailField("等级", firstText(row, ["grade"])),
      detailField("规格", firstText(row, ["spec"])),
      detailField("补货单位", firstText(row, ["replenishmentUnit"])),
      detailField("指导售价1", formatMoneyText(firstValue(row, ["guidePrice1"]))),
      detailField("指导售价2", formatMoneyText(firstValue(row, ["guidePrice2"]))),
      detailField("产品描述", firstText(row, ["productDescription"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.gift))
    ])
  }

  if (featureKey === "category") {
    return compactDetailFields([
      detailField("路径", firstText(row, ["categoryFullPath"])),
      detailField("编码", firstText(row, ["categoryCode"])),
      detailField("层级", firstText(row, ["categoryLevel"])),
      detailField("排序", firstText(row, ["orderNum"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.category)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "customer") {
    return compactDetailFields([
      detailField("联系人", firstText(row, ["contactPerson", "contactName"])),
      detailField("电话", firstText(row, ["contactPhone", "phone"])),
      detailField("喜欢的茶", firstText(row, ["teaPreferences"])),
      detailField("偏好标签", firstText(row, ["preferenceTags"])),
      detailField("服务偏好", firstText(row, ["brewingServicePreferences"])),
      detailField("注意事项", firstText(row, ["cautions"])),
      detailField("人均预算", customerBudgetText(row)),
      detailField("最近到店", formatDateTimeText(firstText(row, ["lastVisitDate"]))),
      detailField("客户照片", hasValue(row.photoNodeId) ? "已绑定，可通过下方操作查看" : ""),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.customer))
    ])
  }

  if (featureKey === "supplier") {
    return compactDetailFields([
      detailField("联系人", firstText(row, ["contactPerson", "contactName"])),
      detailField("电话", firstText(row, ["contactPhone", "phone"])),
      detailField("邮箱", firstText(row, ["contactEmail", "email"])),
      detailField("结算", firstText(row, ["settlementMethod"])),
      detailField("合作", statusLabel(firstText(row, ["cooperationStatus"]) || row.status, STATUS_LABELS.supplier)),
      detailField("地址", firstText(row, ["address"])),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "stockLog") {
    return compactDetailFields([
      detailField("商品", item.title),
      detailField("组织", firstText(row, ["warehouseName", "warehouseDeptName", "shopName", "shopDeptName", "deptName"])),
      detailField("类型", movementTypeLabel(firstText(row, ["movementType", "businessType"]))),
      detailField("业务单号", firstText(row, ["businessNo", "orderNo"])),
      detailField("变动", formatSignedQuantity(firstValue(row, ["changeQuantity", "quantity"]))),
      detailField("变动前", formatQuantity(firstValue(row, ["beforeQuantity"]))),
      detailField("变动后", formatQuantity(firstValue(row, ["afterQuantity", "currentQuantity", "stockQuantity"]))),
      detailField("时间", formatDateTimeText(firstText(row, ["createTime"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "stockCheck") {
    return compactDetailFields([
      detailField("仓库", firstText(row, ["warehouseName", "warehouseDeptName", "shopDeptName"])),
      detailField("日期", formatDateTimeText(firstText(row, ["checkDate", "createTime"]))),
      detailField("盘点人", firstText(row, ["counterName", "submittedBy", "createBy"])),
      detailField("截止时间", formatDateTimeText(firstText(row, ["deadline"]))),
      detailField("驳回原因", firstText(row, ["lastRejectReason"])),
      detailField("驳回人", joinDetail([
        firstText(row, ["lastRejectedBy"]),
        formatDateTimeText(firstText(row, ["lastRejectedTime"]))
      ])),
      detailField("库存变化", firstText(row, ["lastInvalidReason"])),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "salesReturn") {
    return compactDetailFields([
      detailField("客户", firstText(row, ["customerName", "customer", "targetDeptName"])),
      detailField("金额", formatMoneyText(firstValue(row, ["totalAmount", "amount", "returnAmount"]))),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "returnDate"]))),
      detailField("原因", firstText(row, ["returnReason", "reason", "remark"]))
    ])
  }

  if (featureKey === "purchaseReturn") {
    return compactDetailFields([
      detailField("供应商", firstText(row, ["supplierName", "supplier", "vendorName", "targetDeptName"])),
      detailField("金额", formatMoneyText(firstValue(row, ["totalAmount", "amount", "returnAmount"]))),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "returnDate"]))),
      detailField("原因", firstText(row, ["returnReason", "reason", "remark"]))
    ])
  }

  if (featureKey === "outbound") {
    const itemCount = firstValue(row, ["itemCount", "productCount", "detailCount", "totalQuantity"])
    return compactDetailFields([
      detailField("客户", firstText(row, ["customerName", "customer", "targetDeptName"])),
      detailField("商品数", hasValue(itemCount) ? formatQuantity(itemCount) + " 件" : ""),
      detailField("仓库", firstText(row, ["warehouseName", "warehouseDeptName"])),
      detailField("日期", formatDateTimeText(firstText(row, ["createTime", "noticeDate"])))
    ])
  }

  if (featureKey === "transfer" || featureKey === "transferApproval") {
    return compactDetailFields([
      featureKey === "transferApproval" ? detailField("审批节点", resolveTransferApprovalNodeName(row)) : null,
      detailField("业务类型", transferBusinessTypeLabel(row)),
      detailField("业务来源", firstText(row, ["fromDeptName", "sourceDeptName", "fromWarehouseName"])),
      detailField("实际发货仓库", transferWarehouseText(row, "from")),
      detailField(transferTargetLabel(row), firstText(row, ["toDeptName", "targetDeptName", "toWarehouseName"])),
      detailField("实际收货位置", transferWarehouseText(row, "to")),
      ...transferRecipientDetailFields(row),
      detailField("申请数量", formatTransferTotalQuantity(row)),
      firstText(row, ["transferType"]) === "cross_store" &&
        firstText(row, ["sourceBusinessType"]) !== "sales_delivery"
        ? detailField("调出店确认", transferSourceConfirmLabel(firstText(row, ["sourceConfirmStatus"])))
        : null,
      detailField("确认人", firstText(row, ["sourceConfirmedBy"])),
      detailField("确认时间", formatDateTimeText(firstText(row, ["sourceConfirmedTime"]))),
      detailField("确认说明", firstText(row, ["sourceConfirmRemark"])),
      hasValue(firstValue(row, ["reselectionFromTransferId"]))
        ? detailField("余量来源单", "#" + firstValue(row, ["reselectionFromTransferId"]))
        : null,
      detailField("参考总价", formatMoneyText(resolveTransferTotalAmount(row))),
      detailField("日期", formatDateTimeText(firstText(row, ["submittedTime", "createTime", "transferDate"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "transferRecords") {
    return compactDetailFields([
      detailField("调出", firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])),
      detailField("调入", firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])),
      detailField("类型", transferTypeLabel(firstText(row, ["transferType"]))),
      ...transferRecipientDetailFields(row),
      detailField("申请数量", formatTransferTotalQuantity(row)),
      detailField("参考总价", formatMoneyText(resolveTransferTotalAmount(row))),
      detailField("审核通过", formatDateTimeText(firstText(row, ["approvedTime"]))),
      detailField("完成时间", formatDateTimeText(firstText(row, ["receivedTime"]))),
      detailField("归档时间", formatDateTimeText(firstText(row, ["archivedTime"])))
    ])
  }

  if (featureKey === "fixedAssetRepair") {
    return compactDetailFields([
      detailField("店铺", firstText(row, ["shopDeptName", "shopName"])),
      detailField("固定资产", firstText(row, ["productName", "assetName", "goodsName"])),
      detailField("预计维修金额", formatMoneyText(firstValue(row, ["estimatedRepairAmount", "amount"]))),
      detailField("上报时可用额度", formatMoneyText(firstValue(row, ["availableQuotaAmount"]))),
      detailField("异常批准", firstText(row, ["exceptionApproved"]) === "Y" ? "是" : ""),
      detailField("批准类型", exceptionTypeLabel(firstText(row, ["exceptionType"]))),
      detailField("故障说明", firstText(row, ["faultDescription"])),
      detailField("备注", firstText(row, ["remark"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.fixedAssetRepair)),
      detailField("创建时间", formatDateTimeText(firstText(row, ["createTime"]))),
      detailField("上报时间", formatDateTimeText(firstText(row, ["submittedTime"])))
    ])
  }

  if (featureKey === "oaPurchase") {
    return compactDetailFields([
      detailField("标题", firstText(row, ["title", "purchaseTitle"])),
      detailField("申请人", firstText(row, ["applicantName", "createBy", "userName"])),
      detailField("金额", formatMoneyText(firstValue(row, ["amount", "totalAmount"]))),
      detailField("店铺ID", firstText(row, ["shopDeptId"])),
      detailField("审批实例", firstText(row, ["approvalInstanceId"])),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.oaPurchase)),
      detailField("说明", firstText(row, ["reason", "remark", "comment"])),
      detailField("创建时间", formatDateTimeText(firstText(row, ["createTime", "applyTime"]))),
      detailField("更新时间", formatDateTimeText(firstText(row, ["updateTime", "updatedAt"])))
    ])
  }

  if (featureKey === "attendance") {
    return compactDetailFields([
      detailField("日期", formatDateText(firstText(row, ["workDate", "attendanceDate"]))),
      detailField("上班", formatTimeText(firstText(row, ["checkInTime"]))),
      detailField("下班", formatTimeText(firstText(row, ["checkOutTime"]))),
      detailField("工时", hasValue(firstValue(row, ["workHours"])) ? formatQuantity(firstValue(row, ["workHours"])) + "h" : ""),
      detailField("迟到", hasValue(firstValue(row, ["lateMinutes"])) ? formatQuantity(firstValue(row, ["lateMinutes"])) + " 分钟" : ""),
      detailField("早退", hasValue(firstValue(row, ["earlyMinutes"])) ? formatQuantity(firstValue(row, ["earlyMinutes"])) + " 分钟" : ""),
      detailField("状态", statusLabel(row.status, STATUS_LABELS.attendance))
    ])
  }

  if (featureKey === "salary") {
    return compactDetailFields([
      detailField("月份", firstText(row, ["salaryMonth", "month"])),
      detailField("用户", firstText(row, ["userName", "nickName"])),
      detailField("出勤", hasValue(firstValue(row, ["workDays"])) ? formatQuantity(firstValue(row, ["workDays"])) + " 天" : ""),
      detailField("请假", hasValue(firstValue(row, ["leaveDays"])) ? formatQuantity(firstValue(row, ["leaveDays"])) + " 天" : ""),
      detailField("缺勤", hasValue(firstValue(row, ["absentDays"])) ? formatQuantity(firstValue(row, ["absentDays"])) + " 天" : ""),
      detailField("迟到", hasValue(firstValue(row, ["lateTotalMinutes"])) ? formatQuantity(firstValue(row, ["lateTotalMinutes"])) + " 分钟" : ""),
      detailField("早退", hasValue(firstValue(row, ["earlyTotalMinutes"])) ? formatQuantity(firstValue(row, ["earlyTotalMinutes"])) + " 分钟" : ""),
      detailField("加班", hasValue(firstValue(row, ["overtimeHours"])) ? formatQuantity(firstValue(row, ["overtimeHours"])) + "h" : ""),
      detailField("合同综合工资", formatMoneyText(firstValue(row, ["baseSalary"]))),
      detailField("扣款", formatMoneyText(sumValues(row, ["lateDeduction", "earlyDeduction", "absentDeduction", "otherDeduction"]))),
      detailField("加班费", formatMoneyText(firstValue(row, ["overtimePay"]))),
      detailField("奖金", formatMoneyText(firstValue(row, ["otherBonus"]))),
      detailField("实发", formatMoneyText(firstValue(row, ["totalSalary", "salaryAmount"])))
    ])
  }

  if (featureKey === "notice") {
    return compactDetailFields([
      detailField("类型", statusLabel(firstText(row, ["noticeType"]), STATUS_LABELS.notice)),
      detailField("发布人", firstText(row, ["createBy"])),
      detailField("发布时间", firstText(row, ["createTime"])),
      detailField("阅读状态", isNoticeRead(row) ? "已读" : "未读"),
      detailField("内容", plainText(firstText(row, ["noticeContent", "content"])))
    ])
  }

  if (featureKey === "profile") {
    const dept = row && row.dept ? row.dept : {}
    const selectedDeptName = options && options.selectedDeptName ? options.selectedDeptName : ""
    return compactDetailFields([
      detailField("账号", firstText(row, ["userName", "loginName"])),
      detailField("姓名", firstText(row, ["nickName"])),
      detailField("手机", firstText(row, ["phonenumber", "phone"])),
      detailField("邮箱", firstText(row, ["email"])),
      detailField("所属组织", firstText(row, ["deptName"]) || firstText(dept, ["deptName"])),
      detailField("当前业务组织", selectedDeptName),
      detailField("角色", firstText(row, ["roleGroup"])),
      detailField("岗位", firstText(row, ["postGroup"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.profile))
    ])
  }

  if (featureKey === "systemUser") {
    const dept = row && row.dept ? row.dept : {}
    return compactDetailFields([
      detailField("账号", firstText(row, ["userName", "loginName"])),
      detailField("姓名", firstText(row, ["nickName"])),
      detailField("组织", firstText(row, ["deptName"]) || firstText(dept, ["deptName"])),
      detailField("手机", firstText(row, ["phonenumber", "phone"])),
      detailField("邮箱", firstText(row, ["email"])),
      detailField("角色", firstText(row, ["roleGroup", "roleNames"])),
      detailField("岗位", firstText(row, ["postGroup", "postNames"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon))
    ])
  }

  if (featureKey === "systemRole") {
    return compactDetailFields([
      detailField("角色", firstText(row, ["roleName"])),
      detailField("权限字符", firstText(row, ["roleKey"])),
      detailField("显示顺序", firstText(row, ["roleSort"])),
      detailField("数据范围", dataScopeLabel(firstText(row, ["dataScope"]))),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "systemPost") {
    return compactDetailFields([
      detailField("岗位", firstText(row, ["postName"])),
      detailField("编码", firstText(row, ["postCode"])),
      detailField("显示顺序", firstText(row, ["postSort"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "systemDept") {
    return compactDetailFields([
      detailField("组织", firstText(row, ["deptName"])),
      detailField("类型", deptTypeLabel(firstText(row, ["deptType"]))),
      detailField("路径", firstText(row, ["deptFullPath"])),
      detailField("负责人", firstText(row, ["leader"])),
      detailField("电话", firstText(row, ["phone"])),
      detailField("邮箱", firstText(row, ["email"])),
      detailField("排序", firstText(row, ["orderNum"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon))
    ])
  }

  if (featureKey === "systemMenu") {
    return compactDetailFields([
      detailField("菜单名称", firstText(row, ["menuName"])),
      detailField("类型", menuTypeLabel(firstText(row, ["menuType"]), firstText(row, ["isFrame"]))),
      detailField("菜单路径", firstText(row, ["menuFullPath"])),
      detailField("路由地址", firstText(row, ["path"])),
      detailField("组件路径", firstText(row, ["component"])),
      detailField("权限标识", firstText(row, ["perms"])),
      detailField("排序", firstText(row, ["orderNum"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon))
    ])
  }

  if (featureKey === "userShop") {
    const dept = row && row.dept ? row.dept : {}
    return compactDetailFields([
      detailField("账号", firstText(row, ["userName", "loginName"])),
      detailField("姓名", firstText(row, ["nickName"])),
      detailField("所属组织", firstText(row, ["deptName"]) || firstText(dept, ["deptName"])),
      detailField("授权范围", firstText(row, ["shopScopeLabel", "scopeLabel"])),
      detailField("手机", firstText(row, ["phonenumber", "phone"])),
      detailField("邮箱", firstText(row, ["email"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon))
    ])
  }

  if (featureKey === "systemConfig") {
    return compactDetailFields([
      detailField("参数名称", firstText(row, ["configName"])),
      detailField("参数键名", firstText(row, ["configKey"])),
      detailField("参数键值", firstText(row, ["configValue"])),
      detailField("类型", configTypeLabel(firstText(row, ["configType"]))),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "systemDictType") {
    return compactDetailFields([
      detailField("字典名称", firstText(row, ["dictName"])),
      detailField("字典类型", firstText(row, ["dictType"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "systemDictData") {
    return compactDetailFields([
      detailField("字典标签", firstText(row, ["dictLabel"])),
      detailField("字典键值", firstText(row, ["dictValue"])),
      detailField("字典类型", firstText(row, ["dictType"])),
      detailField("样式属性", firstText(row, ["cssClass"])),
      detailField("回显样式", firstText(row, ["listClass"])),
      detailField("排序", firstText(row, ["dictSort"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "systemLogininfor") {
    return compactDetailFields([
      detailField("登录账号", firstText(row, ["userName"])),
      detailField("登录地址", firstText(row, ["ipaddr"])),
      detailField("登录地点", firstText(row, ["loginLocation"])),
      detailField("浏览器", firstText(row, ["browser"])),
      detailField("操作系统", firstText(row, ["os"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.logStatus)),
      detailField("消息", firstText(row, ["msg"])),
      detailField("访问时间", firstText(row, ["loginTime", "accessTime"]))
    ])
  }

  if (featureKey === "systemOperlog") {
    return compactDetailFields([
      detailField("模块", firstText(row, ["title"])),
      detailField("类型", businessTypeLabel(firstValue(row, ["businessType"]))),
      detailField("操作人员", firstText(row, ["operName"])),
      detailField("操作地址", firstText(row, ["operIp"])),
      detailField("请求方式", firstText(row, ["requestMethod"])),
      detailField("请求地址", firstText(row, ["operUrl"])),
      detailField("耗时", hasValue(firstValue(row, ["costTime"])) ? formatQuantity(firstValue(row, ["costTime"])) + "ms" : ""),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.operStatus)),
      detailField("时间", formatDateTimeText(firstText(row, ["operTime", "createTime"])))
    ])
  }

  if (featureKey === "monitorJob") {
    return compactDetailFields([
      detailField("任务名称", firstText(row, ["jobName"])),
      detailField("任务组", firstText(row, ["jobGroup"])),
      detailField("调用目标", firstText(row, ["invokeTarget"])),
      detailField("Cron", firstText(row, ["cronExpression"])),
      detailField("并发", firstText(row, ["concurrent"])),
      detailField("策略", firstText(row, ["misfirePolicy"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.jobStatus))
    ])
  }

  if (featureKey === "monitorJobLog") {
    return compactDetailFields([
      detailField("任务名称", firstText(row, ["jobName"])),
      detailField("任务组", firstText(row, ["jobGroup"])),
      detailField("调用目标", firstText(row, ["invokeTarget"])),
      detailField("日志信息", firstText(row, ["jobMessage"])),
      detailField("异常信息", firstText(row, ["exceptionInfo"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.logStatus)),
      detailField("时间", formatDateTimeText(firstText(row, ["createTime", "startTime"])))
    ])
  }

  if (featureKey === "monitorOnline") {
    return compactDetailFields([
      detailField("账号", firstText(row, ["userName"])),
      detailField("会话", firstText(row, ["tokenId"])),
      detailField("登录地址", firstText(row, ["ipaddr"])),
      detailField("登录地点", firstText(row, ["loginLocation"])),
      detailField("浏览器", firstText(row, ["browser"])),
      detailField("操作系统", firstText(row, ["os"])),
      detailField("登录时间", firstText(row, ["loginTime"]))
    ])
  }

  if (featureKey === "salaryScheme") {
    return compactDetailFields([
      detailField("方案名称", firstText(row, ["schemeName", "name"])),
      detailField("适用角色", firstText(row, ["roleNames", "roleName"])),
      detailField("基本工资", formatMoneyText(firstValue(row, ["baseSalary"]))),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("说明", firstText(row, ["remark"]))
    ])
  }

  if (featureKey === "transferRules") {
    return compactDetailFields([
      detailField("规则名称", firstText(row, ["ruleName", "name"])),
      detailField("调出组织", firstText(row, ["sourceDeptName", "fromDeptName"])),
      detailField("调入组织", firstText(row, ["targetDeptName", "toDeptName"])),
      detailField("调拨类型", transferTypeLabel(firstText(row, ["transferType"]))),
      detailField("审批级别", firstText(row, ["approvalLevel", "level"])),
      detailField("状态", statusLabel(firstText(row, ["status"]), STATUS_LABELS.systemCommon)),
      detailField("备注", firstText(row, ["remark"]))
    ])
  }

  return compactDetailFields([
    detailField("摘要", item.detail)
  ])
}

function buildMobileReviewFields(featureKey, row, options) {
  if (["replenishment", "transfer", "transferApproval"].indexOf(featureKey) === -1) return []

  return compactDetailFields([
    detailField("调出", firstText(row, ["fromDeptName", "fromWarehouseName", "sourceDeptName"])),
    detailField("调入", firstText(row, ["toDeptName", "toWarehouseName", "targetDeptName"])),
    ...transferRecipientDetailFields(row),
    detailField("申请数量", formatTransferTotalQuantity(row)),
    detailField("参考总价", formatMoneyText(resolveTransferTotalAmount(row))),
    detailField("审批节点", resolveTransferApprovalNodeName(row))
  ])
}

function transferRecipientDetailFields(row) {
  return [
    detailField("收件人", firstText(row, ["recipientName"])),
    detailField("联系电话", firstText(row, ["recipientPhone"])),
    detailField("收货地址", firstText(row, ["shippingAddress"]))
  ]
}

function resolveTransferApprovalNodeName(row) {
  const directName = firstText(row, ["nodeName", "currentNodeName"])
  if (directName) return directName

  const unifiedDetail = row && row.unifiedApprovalDetail
  const unifiedTasks = unifiedDetail && Array.isArray(unifiedDetail.tasks) ? unifiedDetail.tasks : []
  const pendingTask = unifiedTasks.find(function(task) {
    return String(task && (task.taskStatus || task.status) || "").toUpperCase() === "PENDING"
  })
  if (pendingTask) return firstText(pendingTask, ["nodeName", "currentNodeName"])

  const approvalTrack = row && row.approvalTrack
  if (!approvalTrack || typeof approvalTrack !== "object" || Array.isArray(approvalTrack)) return ""

  const currentNode = approvalTrack.currentNode
  const currentNodeName = currentNode && typeof currentNode === "object"
    ? firstText(currentNode, ["nodeName", "currentNodeName", "postName"])
    : ""
  if (currentNodeName) return currentNodeName

  const rounds = Array.isArray(approvalTrack.rounds) ? approvalTrack.rounds : []
  for (let roundIndex = rounds.length - 1; roundIndex >= 0; roundIndex -= 1) {
    const nodes = Array.isArray(rounds[roundIndex] && rounds[roundIndex].nodes) ? rounds[roundIndex].nodes : []
    const activeNode = nodes.find(function(node) {
      return node && firstText(node, ["state"]).toLowerCase() === "current"
    })
    const activeNodeName = activeNode ? firstText(activeNode, ["nodeName", "currentNodeName", "postName"]) : ""
    if (activeNodeName) return activeNodeName
  }
  return ""
}

function transferBusinessTypeLabel(row) {
  if (firstText(row, ["sourceBusinessType"]) === "sales_delivery") {
    return "跨店销售在途"
  }
  return transferTypeLabel(firstText(row, ["transferType"]))
}

function transferWarehouseText(row, direction) {
  const source = direction === "to" ? "to" : "from"
  const warehouseName = firstText(row, [
    source + "WarehouseName",
    source === "from" ? "sourceWarehouseName" : "targetWarehouseName"
  ])
  if (warehouseName) return warehouseName
  const warehouseId = firstValue(row, [source + "WarehouseId"])
  const businessDeptId = firstValue(row, [source + "DeptId"])
  if (!hasValue(warehouseId) || String(warehouseId) === String(businessDeptId)) {
    return ""
  }
  return "仓库 #" + warehouseId
}

function transferTargetLabel(row) {
  return firstText(row, ["transferType"]) === "store_return"
    ? "目标仓库"
    : "目标门店"
}

function customerBudgetText(row) {
  const min = firstValue(row, ["budgetMin"])
  const max = firstValue(row, ["budgetMax"])
  if (!hasValue(min) && !hasValue(max)) return ""
  if (hasValue(min) && hasValue(max)) return formatMoneyText(min) + " - " + formatMoneyText(max) + "/人"
  return "约 " + formatMoneyText(hasValue(min) ? min : max) + "/人"
}

function formatTransferTotalQuantity(row) {
  const totalQuantity = resolveTransferTotalQuantity(row)
  return totalQuantity === null ? "" : formatQuantity(totalQuantity)
}

function detailField(label, value) {
  return {
    label,
    value: safeText(value, "")
  }
}

function compactDetailFields(fields) {
  return fields.filter(function(field) {
    return field && hasValue(field.value)
  })
}

function isNoticeRead(row) {
  const value = row && row.isRead
  return value === true || value === "true" || value === 1 || value === "1"
}

function plainText(value) {
  if (!hasValue(value)) return ""
  return String(value).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim()
}

module.exports = {
  buildMobileDetailFields,
  buildMobileReviewFields,
  isNoticeRead,
  resolveTransferApprovalNodeName,
  transferBusinessTypeLabel
}
