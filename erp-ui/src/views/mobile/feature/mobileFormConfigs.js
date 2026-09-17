const COMMON_STATUS_OPTIONS = [
  { label: "正常", value: "0" },
  { label: "停用", value: "1" }
]

const COOPERATION_STATUS_OPTIONS = [
  { label: "合作中", value: "0" },
  { label: "暂停", value: "1" },
  { label: "终止", value: "2" }
]

const CUSTOMER_LEVEL_OPTIONS = [
  { label: "普通客户", value: "普通客户" },
  { label: "重点客户", value: "重点客户" },
  { label: "VIP客户", value: "VIP客户" }
]

const PRODUCT_UNIT_OPTIONS = [
  { label: "斤", value: "斤" },
  { label: "公斤", value: "公斤" },
  { label: "件", value: "件" },
  { label: "箱", value: "箱" },
  { label: "袋", value: "袋" }
]

const PRODUCT_GRADE_OPTIONS = [
  { label: "普通", value: "普通" },
  { label: "优选", value: "优选" },
  { label: "精品", value: "精品" }
]

const TRANSFER_TYPE_OPTIONS = [
  { label: "门店要货", value: "warehouse" },
  { label: "门店返仓", value: "store_return" },
  { label: "异店调货", value: "cross_store" }
]

const ITEM_TYPE_OPTIONS = [
  { label: "商品", value: "product" },
  { label: "OE", value: "oe" },
  { label: "礼盒", value: "gift" }
]

const TRANSFER_ITEM_TYPES = ["product", "gift"]
const TRANSFER_ITEM_TYPE_OPTIONS = ITEM_TYPE_OPTIONS.filter(option => TRANSFER_ITEM_TYPES.indexOf(option.value) > -1)
const REPLENISHMENT_ITEM_TYPES = ["product", "gift", "oe"]

const createReplenishmentLineItemsField = (allowedItemTypes = TRANSFER_ITEM_TYPES) => ({
  key: "details",
  label: "补货明细",
  type: "line-items",
  required: true,
  allowedItemTypes,
  requirePickerItemType: true,
  selectionMode: "stock-picker",
  smartPaste: true,
  pickerEntity: "replenishmentStock",
  pickerTitle: "选择要货库存",
  addLabel: "从仓库库存选择",
  appendLabel: "继续选择库存商品",
  emptyText: "先选择补货仓库，再从仓库库存里勾选商品",
  pickerField: {
    entity: "replenishmentStock",
    dependsOn: "fromDeptId",
    dependsOnLabel: "补货仓库",
    allowedItemTypes,
    placeholder: "搜索商品名称/编码"
  },
  itemFields: [
    { key: "itemName", label: "物料", type: "readonly", placeholder: "从仓库库存选择" },
    { key: "availableQuantity", label: "仓库可用", type: "readonly", placeholder: "选择商品后显示" },
    { key: "costPrice", label: "参考成本价", type: "readonly", format: "money", placeholder: "-" },
    { key: "quantity", label: "数量", type: "number", required: true },
    { key: "unit", label: "单位", type: "readonly" },
    { key: "itemCode", label: "编码", type: "readonly" },
    { key: "spec", label: "规格", type: "readonly" },
    { key: "remark", label: "备注" }
  ]
})

const TRANSFER_SUBMIT_MODES = [
  { label: "保存草稿", action: "save" },
  { label: "保存并提交", action: "submit" }
]

const createTransferRecipientFields = () => [
  { key: "recipientName", label: "收件人", maxlength: 64, autocomplete: "name", placeholder: "收件人姓名" },
  { key: "recipientPhone", label: "联系电话", type: "tel", maxlength: 32, autocomplete: "tel", placeholder: "手机或座机号码" },
  { key: "shippingAddress", label: "收货地址", type: "textarea", maxlength: 500, autocomplete: "street-address", placeholder: "详细收货地址" }
]

const createTransferSourceLineItemsField = (options = {}) => {
  const includeReturnCondition = options.includeReturnCondition === true
  const itemFields = [
    { key: "itemName", label: "物料", type: "readonly", placeholder: "从来源库存选择" },
    { key: "availableQuantity", label: "来源可用", type: "readonly", placeholder: "选择商品后显示" },
    { key: "costPrice", label: "参考成本价", type: "readonly", format: "money", placeholder: "-" },
    { key: "quantity", label: "数量", type: "number", required: true },
    { key: "unit", label: "单位", type: "readonly" },
    { key: "itemCode", label: "编码", type: "readonly" },
    { key: "spec", label: "规格", type: "readonly" }
  ]
  if (includeReturnCondition) {
    itemFields.push(
      {
        key: "goodsCondition",
        label: "返仓货况",
        type: "select",
        defaultValue: "NORMAL",
        options: [
          { label: "正常", value: "NORMAL" },
          { label: "残损", value: "DAMAGED" },
          { label: "待质检", value: "PENDING_QC" }
        ]
      },
      { key: "conditionNote", label: "货况说明" }
    )
  }
  itemFields.push({ key: "remark", label: "备注" })

  return {
    key: "details",
    label: options.label || "调拨明细",
    type: "line-items",
    required: true,
    allowedItemTypes: TRANSFER_ITEM_TYPES,
    requirePickerItemType: true,
    selectionMode: "stock-picker",
    smartPaste: true,
    pickerEntity: "transferSourceStock",
    pickerTitle: options.pickerTitle || "选择来源库存",
    addLabel: options.addLabel || "从来源库存选择",
    appendLabel: "继续选择库存商品",
    emptyText: options.emptyText || "先选择来源组织，再从可用库存里勾选商品",
    pickerField: {
      entity: "transferSourceStock",
      dependsOn: "fromDeptId",
      dependsOnLabel: options.sourceLabel || "来源组织",
      allowedItemTypes: TRANSFER_ITEM_TYPES,
      placeholder: "搜索商品名称/编码"
    },
    itemFields
  }
}

const createWarehouseTransferFormConfig = (options = {}) => ({
  title: options.title || "门店要货",
  idKey: "transferId",
  createLabel: options.createLabel || "发起要货",
  fixedTransferType: "warehouse",
  submitModes: TRANSFER_SUBMIT_MODES,
  fields: [
    {
      key: "fromDeptId",
      label: "补货仓库",
      type: "entity-picker",
      entity: "warehouse",
      required: true,
      purpose: "replenishmentSource",
      placeholder: "搜索补货仓库",
      clearFieldsOnChange: ["details"]
    },
    { key: "toDeptId", label: "要货门店", type: "context-dept", required: true },
    { key: "transferType", label: "调拨类型", type: "readonly", defaultValue: "warehouse", displayValue: "门店要货" },
    ...createTransferRecipientFields(),
    createReplenishmentLineItemsField(options.allowedItemTypes),
    { key: "remark", label: "备注", type: "textarea" }
  ]
})

const createStoreReturnTransferFormConfig = () => ({
  title: "门店返仓",
  featureFlag: "storeReturn",
  idKey: "transferId",
  createLabel: "发起返仓",
  fixedTransferType: "store_return",
  submitModes: TRANSFER_SUBMIT_MODES,
  fields: [
    { key: "fromDeptId", label: "返仓门店", type: "context-dept", required: true },
    {
      key: "toDeptId",
      label: "目标仓库",
      type: "entity-picker",
      entity: "warehouse",
      required: true,
      purpose: "returnTarget"
    },
    { key: "transferType", label: "调拨类型", type: "readonly", defaultValue: "store_return", displayValue: "门店返仓" },
    ...createTransferRecipientFields(),
    {
      key: "returnReasonCode",
      label: "返仓原因",
      type: "select",
      required: true,
      options: [
        { label: "库存过量", value: "OVERSTOCK" },
        { label: "临近效期", value: "NEAR_EXPIRY" },
        { label: "错发货", value: "WRONG_DELIVERY" },
        { label: "残损", value: "DAMAGED" },
        { label: "闭店", value: "STORE_CLOSURE" },
        { label: "其他", value: "OTHER" }
      ]
    },
    {
      key: "returnReasonText",
      label: "返仓原因说明",
      type: "textarea",
      requiredWhen: { key: "returnReasonCode", value: "OTHER" },
      maxlength: 300
    },
    createTransferSourceLineItemsField({
      label: "返仓明细",
      pickerTitle: "选择门店库存",
      addLabel: "从当前门店库存选择",
      emptyText: "从当前门店可用库存里勾选返仓商品",
      sourceLabel: "返仓门店",
      includeReturnCondition: true
    }),
    { key: "remark", label: "备注", type: "textarea" }
  ]
})

const createCrossStoreTransferFormConfig = () => ({
  title: "异店调货",
  idKey: "transferId",
  createLabel: "发起异店调货",
  fixedTransferType: "cross_store",
  submitModes: TRANSFER_SUBMIT_MODES,
  fields: [
    {
      key: "fromDeptId",
      label: "调出门店",
      type: "entity-picker",
      entity: "store",
      required: true,
      excludeContextDept: true,
      placeholder: "选择有权限的其他调出门店",
      clearFieldsOnChange: ["details"]
    },
    { key: "toDeptId", label: "调入门店", type: "context-dept", required: true },
    { key: "transferType", label: "调拨类型", type: "readonly", defaultValue: "cross_store", displayValue: "异店调货" },
    ...createTransferRecipientFields(),
    createTransferSourceLineItemsField({
      label: "调货明细",
      pickerTitle: "选择调出门店库存",
      addLabel: "从调出门店库存选择",
      emptyText: "先选择有权限的其他调出门店，再从其可用库存里勾选商品",
      sourceLabel: "调出门店"
    }),
    { key: "remark", label: "备注", type: "textarea" }
  ]
})

const createStockCheckLineItemsField = () => ({
  key: "details",
  label: "指定商品",
  type: "line-items",
  required: false,
  allowedItemTypes: ["product"],
  requiredWhen: { key: "checkScope", value: "selected" },
  payloadMode: "stock-check-products",
  selectionMode: "stock-picker",
  pickerEntity: "stock",
  pickerTitle: "选择盘点商品",
  pickerSubtitle: "只显示当前盘点组织有库存的商品",
  addLabel: "选择盘点商品",
  appendLabel: "继续选择商品",
  confirmLabel: "加入盘点",
  emptyText: "选择指定商品时，请从当前盘点组织库存里勾选商品",
  pickerField: {
    entity: "stock",
    dependsOn: "warehouseId",
    dependsOnLabel: "盘点组织",
    allowedItemTypes: ["product"],
    placeholder: "搜索商品名称/编码"
  },
  itemFields: [
    { key: "productName", label: "商品", type: "readonly", placeholder: "从库存选择" },
    { key: "availableQuantity", label: "当前可用", type: "readonly", placeholder: "-" },
    { key: "currentQuantity", label: "账面库存", type: "readonly", placeholder: "-" },
    { key: "unit", label: "单位", type: "readonly" },
    { key: "productCode", label: "编码", type: "readonly" },
    { key: "spec", label: "规格", type: "readonly" }
  ]
})

const createLineItemsField = (label, options) => {
  const source = options || {}
  const allowedItemTypes = Array.isArray(source.allowedItemTypes) && source.allowedItemTypes.length
    ? source.allowedItemTypes.slice()
    : ["product"]
  const itemTypeOptions = ITEM_TYPE_OPTIONS.filter(option => allowedItemTypes.indexOf(option.value) > -1)
  return Object.assign({
    key: "details",
    label,
    type: "line-items",
    required: true,
    allowedItemTypes,
    itemFields: [
      { key: "itemType", label: "物料类型", type: "select", required: true, defaultValue: "", options: itemTypeOptions },
      { key: "itemId", label: "物料", type: "entity-picker", entity: "product", entityBy: "itemType", required: true },
      { key: "itemName", label: "物料名称", type: "readonly" },
      { key: "itemCode", label: "物料编码", type: "readonly" },
      { key: "quantity", label: "数量", type: "number", required: true },
      { key: "price", label: "单价", type: "number" },
      { key: "remark", label: "备注" }
    ]
  }, source)
}

const createReturnLineItemsField = () => createLineItemsField("退货明细", {
  allowManualAdd: false,
  allowedItemTypes: null,
  itemFields: [
    { key: "productName", label: "商品", type: "readonly", required: true, placeholder: "选择原单后自动带出" },
    { key: "maxReturnQuantity", label: "可退数量", type: "readonly", placeholder: "-" },
    { key: "quantity", label: "退货数量", type: "number", required: true },
    { key: "unitPrice", label: "原单价", type: "readonly", format: "money", placeholder: "-" },
    { key: "unit", label: "单位", type: "readonly" },
    { key: "spec", label: "规格", type: "readonly" },
    { key: "reason", label: "退货原因" }
  ]
})

const MOBILE_FORM_CONFIG = {
  oaPurchase: {
    title: "采购申请",
    idKey: "purchaseId",
    createLabel: "新建申请",
    submitModes: [
      { label: "保存草稿", action: "save" },
      { label: "保存并提交", action: "submit" }
    ],
    fields: [
      { key: "title", label: "申请标题", required: true, maxlength: 120 },
      { key: "amount", label: "申请金额", type: "number", required: true },
      { key: "reason", label: "申请原因", type: "textarea", required: true, maxlength: 500 },
      { key: "remark", label: "备注", type: "textarea", maxlength: 500 }
    ]
  },
  sales: {
    title: "销售单",
    idKey: "orderId",
    passthroughFields: ["version"],
    createLabel: "新建销售",
    submitModes: [
      { label: "保存草稿", action: "save" },
      { label: "保存并提交", action: "submit" }
    ],
    fields: [
      { key: "customerId", label: "客户", type: "entity-picker", entity: "customer", required: true, requiredUnless: { key: "customerName" }, fallbackLabelKey: "customerName", quickCreate: true },
      {
        key: "warehouseId",
        label: "默认出库仓库（仅填空行）",
        type: "entity-picker",
        entity: "warehouse",
        purpose: "deliverySource",
        salesWarehouseDefault: true,
        required: false
      },
      { key: "orderTitle", label: "销售标题", required: true },
      { key: "orderDate", label: "销售日期", type: "date" },
      Object.assign(createLineItemsField("销售明细", { allowedItemTypes: ["product", "gift"] }), {
        itemFields: createLineItemsField("销售明细", { allowedItemTypes: ["product", "gift"] }).itemFields.concat([
          { key: "warehouseId", label: "出库仓库", type: "entity-picker", entity: "warehouse", purpose: "deliverySource", inheritFormField: "warehouseId" }
        ])
      }),
      { key: "remark", label: "备注", type: "textarea" }
    ]
  },
  purchase: {
    passthroughFields: ["version"],
    title: "采购单",
    idKey: "orderId",
    createLabel: "新建采购",
    submitModes: [
      { label: "保存草稿", action: "save" },
      { label: "保存并提交", action: "submit" }
    ],
    fields: [
      { key: "supplierId", label: "供应商", type: "entity-picker", entity: "supplier", required: true, requiredUnless: { key: "supplierName" }, fallbackLabelKey: "supplierName" },
      { key: "warehouseId", label: "入库仓库", type: "context-dept", required: true },
      { key: "orderTitle", label: "采购标题", required: true },
      { key: "orderDate", label: "采购日期", type: "date" },
      createLineItemsField("采购明细", { allowedItemTypes: ["product", "oe", "gift"] }),
      { key: "remark", label: "备注", type: "textarea" }
    ]
  },
  replenishment: createWarehouseTransferFormConfig({
    title: "补货申请",
    createLabel: "新建补货",
    allowedItemTypes: REPLENISHMENT_ITEM_TYPES
  }),
  salesReturn: {
    passthroughFields: ["version"],
    title: "销售退货",
    idKey: "returnId",
    createLabel: "新建销售退货",
    submitModes: [
      { label: "保存草稿", action: "save" },
      { label: "保存并提交", action: "submit" }
    ],
    fields: [
      { key: "salesOrderId", label: "原销售单", type: "entity-picker", entity: "sales", required: true, clearFieldsOnChange: ["details"] },
      { key: "returnTitle", label: "退货主题", required: true, maxlength: 128 },
      { key: "customerName", label: "客户", type: "readonly", required: true },
      { key: "shopDeptId", label: "退货门店", type: "context-dept", required: true },
      Object.assign(createReturnLineItemsField(), { selectionScoped: true }),
      { key: "remark", label: "备注", type: "textarea" }
    ]
  },
  purchaseReturn: {
    passthroughFields: ["version"],
    title: "采购退货",
    idKey: "returnId",
    createLabel: "新建采购退货",
    submitModes: [
      { label: "保存草稿", action: "save" },
      { label: "保存并提交", action: "submit" }
    ],
    fields: [
      { key: "purchaseOrderId", label: "原采购单", type: "entity-picker", entity: "purchase", required: true, clearFieldsOnChange: ["details"] },
      { key: "returnTitle", label: "退货主题", required: true, maxlength: 128 },
      { key: "supplierName", label: "供应商", type: "readonly", required: true },
      { key: "shopDeptId", label: "退货仓库", type: "context-dept", required: true },
      { key: "returnReason", label: "退货原因", type: "textarea", required: true },
      { key: "responsibility", label: "责任归属", type: "select", required: true, options: [
        { label: "供应商", value: "supplier" },
        { label: "仓库", value: "warehouse" },
        { label: "运输", value: "transport" },
        { label: "其他", value: "other" }
      ] },
      Object.assign(createReturnLineItemsField(), { selectionScoped: true }),
      { key: "remark", label: "备注", type: "textarea" }
    ]
  },
  stockCheck: {
    title: "库存盘点",
    idKey: "checkId",
    createLabel: "新建盘点",
    submitModes: [
      { label: "创建盘点", action: "save" }
    ],
    fields: [
      { key: "warehouseId", label: "盘点组织", type: "context-dept", required: true },
      {
        key: "counterUserId",
        label: "盘点人",
        type: "entity-picker",
        entity: "stockCheckCounter",
        required: true,
        dependsOn: "warehouseId",
        dependsOnLabel: "盘点组织",
        emptyText: "当前组织暂无具备盘点提交权限的人员"
      },
      { key: "deadline", label: "截止时间", type: "datetime-local", required: true },
      { key: "blindCheck", label: "盘点方式", type: "select", defaultValue: "1", options: [
        { label: "盲盘（推荐）", value: "1" },
        { label: "明盘", value: "0" }
      ] },
      { key: "checkScope", label: "盘点范围", type: "select", defaultValue: "all", options: [
        { label: "全部商品", value: "all" },
        { label: "指定商品", value: "selected" }
      ] },
      createStockCheckLineItemsField(),
      { key: "remark", label: "备注", type: "textarea" }
    ]
  },
  transfer: createWarehouseTransferFormConfig(),
  customer: {
    title: "客户服务卡",
    featureFlag: "customerServiceCard",
    idKey: "customerId",
    createLabel: "新建客户服务卡",
    passthroughFields: ["photoNodeId", "version"],
    submitModes: [
      { label: "保存服务卡", action: "save" }
    ],
    fields: [
      { key: "customerName", label: "客户姓名", required: true, maxlength: 128 },
      { key: "customerCode", label: "客户编码", maxlength: 64 },
      { key: "contactPerson", label: "联系人", maxlength: 64 },
      { key: "contactPhone", label: "联系电话", maxlength: 32 },
      { key: "teaPreferences", label: "喜欢的茶", type: "textarea", maxlength: 500 },
      { key: "preferenceTags", label: "偏好标签", maxlength: 500, placeholder: "多个标签用逗号分隔" },
      { key: "brewingServicePreferences", label: "服务偏好", type: "textarea", maxlength: 1000 },
      { key: "cautions", label: "注意事项", type: "textarea", maxlength: 1000 },
      { key: "budgetMin", label: "人均预算下限", type: "number" },
      { key: "budgetMax", label: "人均预算上限", type: "number" },
      { key: "lastVisitDate", label: "最近到店", type: "date" }
    ]
  },
  fixedAssetRepair: {
    title: "固定资产维修上报",
    idKey: "repairId",
    createLabel: "新建报修",
    submitModes: [
      { label: "提交报修", action: "submit" }
    ],
    fields: [
      { key: "oeItemId", label: "固定资产OE", type: "entity-picker", entity: "fixedAssetOe", required: true },
      { key: "repairQuantity", label: "坏掉数量", type: "number", required: true },
      { key: "faultDescription", label: "破损说明", type: "textarea", required: true },
      { key: "imageUrls", label: "图片/附件", type: "image-upload", action: "/oa/fixedAsset/repair/image/upload", limit: 5, fileSize: 5, accept: "image/*", capture: "environment", deleteOnRemove: false },
      { key: "remark", label: "备注", type: "textarea" }
    ]
  }
}

const MOBILE_TRANSFER_FORM_CONFIG = {
  warehouse: MOBILE_FORM_CONFIG.transfer,
  store_return: createStoreReturnTransferFormConfig(),
  cross_store: createCrossStoreTransferFormConfig()
}

function getMobileTransferFormConfig(transferType) {
  const normalizedType = String(transferType || "warehouse").trim().toLowerCase()
  return MOBILE_TRANSFER_FORM_CONFIG[normalizedType] || MOBILE_TRANSFER_FORM_CONFIG.warehouse
}

function getMobileFormConfig(featureKey, transferType) {
  if (featureKey === "transfer") {
    return getMobileTransferFormConfig(transferType)
  }
  return MOBILE_FORM_CONFIG[featureKey] || null
}

function getMobileFormKeys() {
  return Object.keys(MOBILE_FORM_CONFIG)
}

module.exports = {
  COMMON_STATUS_OPTIONS,
  MOBILE_FORM_CONFIG,
  getMobileFormConfig,
  getMobileTransferFormConfig,
  getMobileFormKeys
}
