const assert = require("assert")

const {
  cloneMobileEditFormSource,
  snapshotMobileFormSheet,
  restoreMobileFormSheet,
  createMobileFormData,
  buildMobileFormPayload,
  normalizeLineItems
} = require("../src/views/mobile/feature/mobileFormPayloads")

const {
  getMobileFormValidationError,
  isFieldRequired,
  validateMobileForm
} = require("../src/views/mobile/feature/mobileValidation")

const {
  getMobileFormConfig,
  getMobileTransferFormConfig
} = require("../src/views/mobile/feature/mobileFormConfigs")

const salesConfig = getMobileFormConfig("sales")
const purchaseConfig = getMobileFormConfig("purchase")
const salesReturnConfig = getMobileFormConfig("salesReturn")
const purchaseReturnConfig = getMobileFormConfig("purchaseReturn")
const transferConfig = getMobileFormConfig("transfer")
const storeReturnTransferConfig = getMobileTransferFormConfig("store_return")
const crossStoreTransferConfig = getMobileTransferFormConfig("cross_store")
const replenishmentConfig = getMobileFormConfig("replenishment")
const stockCheckConfig = getMobileFormConfig("stockCheck")

assert.strictEqual(typeof getMobileFormValidationError, "function", "structured mobile form validation should be exported")
assert.strictEqual(typeof isFieldRequired, "function", "dynamic required evaluation should be exported")

{
  const config = { idKey: "transferId" }
  const formSheet = {
    open: true,
    mode: "edit",
    saving: false,
    error: "",
    validationError: null,
    config,
    data: {
      transferId: 148,
      recipientName: "李昌林",
      details: [{ itemId: 182, itemName: "蜂蜜", quantity: 25, costPrice: 2 }]
    },
    initialData: {
      transferId: 148,
      recipientName: "李昌林",
      details: [{ itemId: 182, itemName: "蜂蜜", quantity: 20, costPrice: 2 }]
    }
  }
  const snapshot = snapshotMobileFormSheet(formSheet)
  formSheet.data.recipientName = ""
  formSheet.data.details.length = 0

  const restored = restoreMobileFormSheet(snapshot)
  assert.strictEqual(restored.open, true, "continuing editing should reopen the same mobile form")
  assert.strictEqual(restored.mode, "edit", "continuing editing should retain edit mode")
  assert.strictEqual(restored.config, config, "continuing editing should retain the resolved form configuration")
  assert.strictEqual(restored.data.recipientName, "李昌林", "continuing editing should restore the recipient")
  assert.strictEqual(restored.data.details.length, 1, "continuing editing should restore all draft details")
  assert.strictEqual(restored.data.details[0].quantity, 25, "continuing editing should preserve the unsaved quantity")
  assert.notStrictEqual(restored.data, snapshot.data, "restored form data should be independently cloned")
  assert.notStrictEqual(restored.data.details, snapshot.data.details, "restored detail rows should be independently cloned")
}

{
  const mappedDraft = { title: "主仓库 → 测试门店", code: "TF202607100001", status: "草稿" }
  Object.defineProperty(mappedDraft, "_raw", {
    enumerable: false,
    value: {
      transferId: 144,
      fromDeptId: 1245,
      fromWarehouseId: 1245,
      toDeptId: 1268,
      toWarehouseId: 1268,
      transferType: "warehouse",
      details: [{ itemType: "product", itemId: 182, itemName: "仓库QA茶样", quantity: 1 }]
    }
  })

  const editSource = cloneMobileEditFormSource(mappedDraft)
  assert.deepStrictEqual(
    createMobileFormData(replenishmentConfig, editSource, { selectedDeptId: 1268, selectedDeptType: "STORE" }),
    {
      transferId: 144,
      fromDeptId: 1245,
      fromWarehouseId: 1245,
      toDeptId: 1268,
      toWarehouseId: 1268,
      transferType: "warehouse",
      recipientName: "",
      recipientPhone: "",
      shippingAddress: "",
      details: [{ itemType: "product", itemId: 182, itemName: "仓库QA茶样", quantity: 1, sortOrder: 0, remark: "" }],
      remark: ""
    },
    "mobile replenishment editing should clone the non-enumerable raw detail before building form data"
  )
  assert.notStrictEqual(editSource, mappedDraft._raw, "mobile edit data should not mutate the detail response")
  assert.notStrictEqual(editSource.details, mappedDraft._raw.details, "mobile edit line items should be cloned")
}

assert.deepStrictEqual(
  createMobileFormData(salesConfig, {
    _raw: {
      orderId: 12,
      customerId: 3,
      warehouseId: 8,
      details: [{ productId: 5, quantity: 2, price: 18 }]
    }
  }),
  {
    orderId: 12,
    customerId: 3,
    warehouseId: 8,
    orderTitle: "",
    orderDate: "",
    details: [{ itemType: "product", itemId: 5, quantity: 2, price: 18, amount: 36, remark: "" }],
    remark: ""
  },
  "form data should preserve ids and normalize line items for editing"
)

{
  const salesDraft = createMobileFormData(salesConfig, {
    orderId: 120,
    orderTitle: "门店销售草稿",
    customerName: "北京柏悦客户",
    details: [{
      productId: 5,
      productName: "龙井茶",
      quantity: 2,
      unitPrice: 18,
      warehouseId: 8
    }]
  })

  assert.strictEqual(salesDraft.customerId, "", "backend sales drafts do not persist a synthetic customer id")
  assert.strictEqual(salesDraft.customerName, "北京柏悦客户", "sales draft editing should retain the persisted customer name")
  assert.strictEqual(salesDraft.warehouseId, 8, "sales draft editing should recover the warehouse from detail rows")
  assert.strictEqual(salesDraft.details[0].price, 18, "sales draft editing should hydrate the visible price from backend unitPrice")
  assert.strictEqual(
    validateMobileForm(salesConfig, salesDraft),
    "",
    "an existing customer name should satisfy sales draft validation when the backend has no customer id"
  )
}

{
  const purchaseDraft = createMobileFormData(purchaseConfig, {
    orderId: 220,
    orderTitle: "主仓采购草稿",
    supplierName: "主仓供应商",
    orderDate: "2026-07-11",
    details: [{
      itemType: "product",
      itemId: 5,
      itemName: "龙井茶",
      quantity: 3,
      unitPrice: 9,
      warehouseId: 18
    }]
  })

  assert.strictEqual(purchaseDraft.supplierId, "", "backend purchase drafts do not persist a synthetic supplier id")
  assert.strictEqual(purchaseDraft.supplierName, "主仓供应商", "purchase draft editing should retain the persisted supplier name")
  assert.strictEqual(purchaseDraft.warehouseId, 18, "purchase draft editing should recover the warehouse from detail rows")
  assert.strictEqual(purchaseDraft.orderDate, "2026-07-11", "purchase editing should use the backend orderDate field")
  assert.strictEqual(purchaseDraft.details[0].price, 9, "purchase draft editing should hydrate the visible price from backend unitPrice")
  assert.strictEqual(
    validateMobileForm(purchaseConfig, purchaseDraft),
    "",
    "an existing supplier name should satisfy purchase draft validation when the backend has no supplier id"
  )
}

assert.strictEqual(
  createMobileFormData(purchaseConfig, null, { selectedDeptId: 18, selectedDeptType: "WAREHOUSE" }).warehouseId,
  18,
  "new mobile purchase orders must bind the inbound warehouse to the current warehouse context"
)

assert.deepStrictEqual(
  [
    createMobileFormData(salesReturnConfig, null, { selectedDeptId: 8, selectedDeptType: "STORE" }).shopDeptId,
    createMobileFormData(purchaseReturnConfig, null, { selectedDeptId: 18, selectedDeptType: "WAREHOUSE" }).shopDeptId
  ],
  [8, 18],
  "new mobile returns must carry the current store or warehouse context"
)

assert.deepStrictEqual(
  normalizeLineItems([{ productId: 8, quantity: 2, referenceCostPrice: 6.5 }]),
  [{ itemType: "product", itemId: 8, quantity: 2, price: 6.5, amount: 13, remark: "" }],
  "line-item editing should fall back to a backend reference cost when no explicit unit price exists"
)

assert.deepStrictEqual(
  normalizeLineItems([
    { productId: "5", productCode: "TEA-5", productName: "龙井茶", quantity: "2", price: "18", remark: "现货" },
    { productId: "", quantity: "1", price: "9" }
  ]),
  [{ itemType: "product", itemId: 5, itemCode: "TEA-5", itemName: "龙井茶", quantity: 2, price: 18, amount: 36, remark: "现货" }],
  "line items should coerce numeric fields, normalize legacy product metadata, and drop empty rows"
)

assert.deepStrictEqual(
  normalizeLineItems([{ productId: "6", sku: "SKU-006", productName: "兼容商品", quantity: "1" }]),
  [{ itemType: "product", itemId: 6, itemCode: "SKU-006", itemName: "兼容商品", quantity: 1, remark: "" }],
  "line items should treat legacy sku values as the generic material code"
)

assert.deepStrictEqual(
  buildMobileFormPayload(salesConfig, {
    customerId: "3",
    customerName: "北京柏悦客户",
    warehouseId: "8",
    orderTitle: "门店销售",
    details: [{ productId: "5", quantity: "2", price: "18" }]
  }, { submitAction: "submit" }),
  {
    customerId: 3,
    customerName: "北京柏悦客户",
    warehouseId: 8,
    orderTitle: "门店销售",
    details: [{ itemType: "product", itemId: 5, warehouseId: 8, quantity: 2, unitPrice: 18, amount: 36, remark: "" }],
    submitAction: "submit"
  },
  "form payload should normalize pickers, carry backend-required customer name, line items, and submit action"
)

assert.deepStrictEqual(
  buildMobileFormPayload(salesConfig, {
    customerId: "3",
    warehouseId: "8",
    orderTitle: "门店销售",
    details: [{ productId: "5", quantity: "2", price: "18" }]
  }, { submitAction: "submit" }).details,
  [{ itemType: "product", itemId: 5, warehouseId: 8, quantity: 2, unitPrice: 18, amount: 36, remark: "" }],
  "mobile sales payload should copy the selected outbound warehouse into each sales detail so generated notices are warehouse-visible"
)

assert.deepStrictEqual(
  buildMobileFormPayload(salesReturnConfig, {
    salesOrderId: "21",
    salesOrderNo: "SO202607050001",
    returnTitle: "销售退货-SO202607050001",
    customerId: "3",
    customerName: "北京柏悦客户",
    shopDeptId: "8",
    details: [{ productId: "5", quantity: "1", price: "18", reason: "客户退货" }]
  }, { submitAction: "submit" }),
  {
    salesOrderId: 21,
    salesOrderNo: "SO202607050001",
    returnTitle: "销售退货-SO202607050001",
    customerName: "北京柏悦客户",
    shopDeptId: 8,
    details: [{ itemType: "product", itemId: 5, productId: 5, quantity: 1, unitPrice: 18, amount: 18, remark: "", reason: "客户退货" }],
    submitAction: "submit"
  },
  "mobile sales-return payload should carry backend-required return title and customer name"
)

assert.deepStrictEqual(
  buildMobileFormPayload(salesReturnConfig, {
    salesOrderId: "21",
    salesOrderNo: "SO202607050001",
    returnTitle: "销售退货-SO202607050001",
    customerId: "3",
    customerName: "北京柏悦客户",
    shopDeptId: "8",
    details: [{
      salesDetailId: "51",
      productId: "5",
      productName: "龙井茶",
      quantity: "1",
      maxReturnQuantity: "2",
      unitPrice: "18",
      price: "18"
    }]
  }, { submitAction: "submit" }).details,
  [{
    itemType: "product",
    itemId: 5,
    itemName: "龙井茶",
    productId: 5,
    quantity: 1,
    unitPrice: 18,
    amount: 18,
    remark: "",
    productName: "龙井茶",
    salesDetailId: 51,
    maxReturnQuantity: 2
  }],
  "mobile sales-return payload should preserve source sales detail id and original unit price"
)

assert.deepStrictEqual(
  buildMobileFormPayload(purchaseReturnConfig, {
    purchaseOrderId: "31",
    purchaseOrderNo: "PO202607050001",
    returnTitle: "采购退货-PO202607050001",
    supplierId: "7",
    supplierName: "主仓供应商",
    shopDeptId: "8",
    returnReason: "包装破损",
    responsibility: "supplier",
    details: [{ productId: "5", quantity: "1", price: "9", reason: "供应商退货" }]
  }, { submitAction: "submit" }),
  {
    purchaseOrderId: 31,
    purchaseOrderNo: "PO202607050001",
    returnTitle: "采购退货-PO202607050001",
    supplierName: "主仓供应商",
    shopDeptId: 8,
    returnReason: "包装破损",
    responsibility: "supplier",
    details: [{ itemType: "product", itemId: 5, quantity: 1, unitPrice: 9, amount: 9, remark: "", reason: "供应商退货" }],
    submitAction: "submit"
  },
  "mobile purchase-return payload should carry backend-required return title and supplier name"
)

assert.deepStrictEqual(
  buildMobileFormPayload(purchaseReturnConfig, {
    purchaseOrderId: "31",
    purchaseOrderNo: "PO202607050001",
    returnTitle: "采购退货-PO202607050001",
    supplierId: "7",
    supplierName: "主仓供应商",
    shopDeptId: "8",
    details: [{
      purchaseDetailId: "61",
      productId: "5",
      productName: "龙井茶",
      quantity: "1",
      maxReturnQuantity: "3",
      unitPrice: "9",
      price: "9"
    }]
  }, { submitAction: "submit" }).details,
  [{
    itemType: "product",
    itemId: 5,
    quantity: 1,
    unitPrice: 9,
    amount: 9,
    remark: "",
    itemName: "龙井茶",
    purchaseDetailId: 61,
    maxReturnQuantity: 3
  }],
  "mobile purchase-return payload should preserve source purchase detail id and original unit price"
)

assert.deepStrictEqual(
  buildMobileFormPayload(transferConfig, {
    fromDeptId: "101",
    toDeptId: "202",
    transferType: "warehouse",
    details: [{ productId: "5", quantity: "2" }]
  }, { submitAction: "submit" }),
  {
    fromDeptId: 101,
    toDeptId: 202,
    fromWarehouseId: 101,
    toWarehouseId: 202,
    transferType: "warehouse",
    details: [{ itemType: "product", itemId: 5, quantity: 2, remark: "", sortOrder: 0 }],
    submitAction: "submit"
  },
  "transfer form payload should use backend transfer fields instead of source/target aliases"
)

assert.deepStrictEqual(
  createMobileFormData(transferConfig, null, { selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: "",
    toDeptId: 202,
    toWarehouseId: 202,
    transferType: "warehouse",
    recipientName: "",
    recipientPhone: "",
    shippingAddress: "",
    details: [],
    remark: ""
  },
  "store warehouse-transfer creation should default the target organization to the selected store"
)

assert.deepStrictEqual(
  createMobileFormData(storeReturnTransferConfig, null, { selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: 202,
    fromWarehouseId: 202,
    toDeptId: "",
    transferType: "store_return",
    recipientName: "",
    recipientPhone: "",
    shippingAddress: "",
    returnReasonCode: "",
    returnReasonText: "",
    details: [],
    remark: ""
  },
  "store-return creation should fix the source to the selected store"
)

assert.deepStrictEqual(
  createMobileFormData(crossStoreTransferConfig, null, { selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: "",
    toDeptId: 202,
    toWarehouseId: 202,
    transferType: "cross_store",
    recipientName: "",
    recipientPhone: "",
    shippingAddress: "",
    details: [],
    remark: ""
  },
  "cross-store creation should fix the target to the selected store"
)

assert.deepStrictEqual(
  createMobileFormData(crossStoreTransferConfig, {
    transferId: 91,
    fromDeptId: 201,
    fromWarehouseId: 201,
    toDeptId: 202,
    toWarehouseId: 202,
    transferType: "cross_store",
    sourceConfirmStatus: "RESELECT_REQUIRED",
    details: [{ detailId: 501, itemType: "product", itemId: 5, quantity: 2 }]
  }, { selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    transferId: 91,
    fromDeptId: "",
    fromWarehouseId: "",
    toDeptId: 202,
    transferType: "cross_store",
    recipientName: "",
    recipientPhone: "",
    shippingAddress: "",
    details: [{
      detailId: 501,
      itemType: "product",
      itemId: 5,
      quantity: 2,
      remark: "",
      sortOrder: 0
    }],
    remark: "",
    toWarehouseId: 202
  },
  "returned cross-store quantities should clear the rejected source and force the target store to choose another one"
)

assert.deepStrictEqual(
  createMobileFormData(replenishmentConfig, null, { selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: "",
    toDeptId: 202,
    toWarehouseId: 202,
    transferType: "warehouse",
    recipientName: "",
    recipientPhone: "",
    shippingAddress: "",
    details: [],
    remark: ""
  },
  "store replenishment creation should default the target store and use transfer payload fields"
)

assert.deepStrictEqual(
  buildMobileFormPayload(replenishmentConfig, {
    fromDeptId: "101",
    toDeptId: "202",
    transferType: "cross_store",
    details: [{
      itemType: "product",
      itemId: "5",
      itemName: "龙井茶",
      itemCode: "TEA001",
      quantity: "2",
      unit: "斤",
      spec: "500g",
      grade: "精品",
      costPrice: "99",
      price: "88"
    }]
  }, { submitAction: "submit", selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: 101,
    toDeptId: 202,
    fromWarehouseId: 101,
    toWarehouseId: 202,
    transferType: "warehouse",
    details: [{
      itemType: "product",
      itemId: 5,
      itemName: "龙井茶",
      itemCode: "TEA001",
      quantity: 2,
      costPrice: 99,
      amount: 198,
      unit: "斤",
      spec: "500g",
      grade: "精品",
      sortOrder: 0,
      remark: ""
    }],
    submitAction: "submit"
  },
  "mobile replenishment should force warehouse transfer and preserve stock item metadata with reference amount snapshots"
)

assert.deepStrictEqual(
  buildMobileFormPayload(crossStoreTransferConfig, {
    fromDeptId: "101",
    fromWarehouseId: "999",
    toDeptId: "202",
    toWarehouseId: "998",
    transferType: "warehouse",
    details: [{ itemType: "product", itemId: "5", quantity: "2" }]
  }, { submitAction: "submit", selectedDeptId: 202, selectedDeptType: "STORE" }),
  {
    fromDeptId: 101,
    toDeptId: 202,
    fromWarehouseId: 101,
    toWarehouseId: 202,
    transferType: "cross_store",
    details: [{ itemType: "product", itemId: 5, quantity: 2, sortOrder: 0, remark: "" }],
    submitAction: "submit"
  },
  "typed cross-store payload should overwrite stale type and physical-location fields with source-store to current-store direction"
)

assert.deepStrictEqual(
  buildMobileFormPayload(purchaseConfig, {
    supplierId: "7",
    warehouseId: "8",
    details: [{
      itemType: "oe",
      itemId: "31",
      itemCode: "OE-031",
      itemName: "茶具套装",
      warehouseId: "999",
      quantity: "2",
      price: "120"
    }]
  }).details,
  [{
    itemType: "oe",
    itemId: 31,
    itemCode: "OE-031",
    itemName: "茶具套装",
    warehouseId: 8,
    quantity: 2,
    unitPrice: 120,
    amount: 240,
    remark: ""
  }],
  "mobile purchase payload should accept OE and overwrite stale line warehouses with the current inbound warehouse"
)

assert.deepStrictEqual(
  buildMobileFormPayload(salesConfig, {
    customerId: "3",
    warehouseId: "8",
    details: [
      { itemType: "oe", itemId: "31", quantity: "1", price: "120" },
      { itemType: "gift", itemId: "41", itemCode: "GIFT-041", itemName: "双罐礼盒", quantity: "1", price: "88" }
    ]
  }).details,
  [{
    itemType: "gift",
    itemId: 41,
    itemCode: "GIFT-041",
    itemName: "双罐礼盒",
    warehouseId: 8,
    quantity: 1,
    unitPrice: 88,
    amount: 88,
    remark: ""
  }],
  "mobile sales payload should keep gifts and exclude OE even when stale form data contains it"
)

assert.deepStrictEqual(
  buildMobileFormPayload(transferConfig, {
    fromDeptId: "101",
    toDeptId: "202",
    transferType: "warehouse",
    details: [
      { itemType: "oe", itemId: "5", quantity: "1" },
      { itemType: "product", itemId: "5", itemName: "龙井茶", quantity: "2" },
      { itemType: "gift", itemId: "5", itemName: "龙井礼盒", quantity: "1" }
    ]
  }).details,
  [
    { itemType: "product", itemId: 5, itemName: "龙井茶", quantity: 2, remark: "", sortOrder: 0 },
    { itemType: "gift", itemId: 5, itemName: "龙井礼盒", quantity: 1, remark: "", sortOrder: 1 }
  ],
  "mobile warehouse transfer payload should exclude stale OE rows and keep product and gift order stable"
)

assert.deepStrictEqual(
  buildMobileFormPayload(stockCheckConfig, {
    warehouseId: "8",
    checkScope: "products",
    details: [
      { productId: "5", productName: "龙井茶", productCode: "TEA001", availableQuantity: "12" },
      { productId: "5", productName: "龙井茶" },
      { productId: "" }
    ],
    remark: "抽盘"
  }),
  {
    warehouseId: 8,
    checkScope: "products",
    details: [{ productId: 5 }],
    remark: "抽盘"
  },
  "mobile stock-check payload should send unique selected product ids for specified-product checks"
)

assert.strictEqual(
  validateMobileForm(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: [{ itemType: "product", itemId: 5, quantity: 2, price: 18 }]
  }),
  "",
  "valid mobile sales form should pass validation"
)

assert.strictEqual(
  validateMobileForm(salesReturnConfig, {
    customerId: 3,
    customerName: "北京柏悦客户",
    shopDeptId: 8,
    returnTitle: "销售退货",
    details: [{ productId: 5, quantity: 1, price: 18 }]
  }),
  "请填写原销售单",
  "mobile sales-return form should stop before backend when the original sales order is missing"
)

assert.strictEqual(
  validateMobileForm(purchaseReturnConfig, {
    supplierId: 7,
    supplierName: "主仓供应商",
    shopDeptId: 8,
    returnTitle: "采购退货",
    details: [{ productId: 5, quantity: 1, price: 9 }]
  }),
  "请填写原采购单",
  "mobile purchase-return form should stop before backend when the original purchase order is missing"
)

assert.strictEqual(
  validateMobileForm(salesReturnConfig, {
    salesOrderId: 21,
    salesOrderNo: "SO202607050001",
    returnTitle: "销售退货-SO202607050001",
    customerName: "北京柏悦客户",
    shopDeptId: 8,
    details: [{ salesDetailId: 51, productId: 5, productName: "龙井茶", quantity: 1, maxReturnQuantity: 2, unitPrice: 18 }]
  }),
  "",
  "mobile sales-return validation should accept source-order customer name without requiring customer id"
)

assert.strictEqual(
  validateMobileForm(purchaseReturnConfig, {
    purchaseOrderId: 31,
    purchaseOrderNo: "PO202607050001",
    returnTitle: "采购退货-PO202607050001",
    supplierName: "主仓供应商",
    shopDeptId: 8,
    returnReason: "包装破损",
    responsibility: "supplier",
    details: [{ purchaseDetailId: 61, productId: 5, productName: "龙井茶", quantity: 1, maxReturnQuantity: 3, unitPrice: 9 }]
  }),
  "",
  "mobile purchase-return validation should accept source-order supplier name without requiring supplier id"
)

assert.strictEqual(
  validateMobileForm(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: []
  }),
  "请添加销售明细",
  "line-item fields should require at least one detail row"
)

assert.strictEqual(
  validateMobileForm(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: [{ itemType: "product", itemId: 5, quantity: 0, price: 18 }]
  }),
  "销售明细第1行数量必须大于0",
  "line-item quantity must be positive"
)

assert.deepStrictEqual(
  getMobileFormValidationError(salesConfig, {
    customerId: "",
    warehouseId: 8,
    details: []
  }),
  { message: "请填写客户", fieldKey: "customerId", rowIndex: null, itemFieldKey: null },
  "structured validation should target an ordinary required field"
)

assert.deepStrictEqual(
  getMobileFormValidationError(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: []
  }),
  { message: "请添加销售明细", fieldKey: "details", rowIndex: null, itemFieldKey: null },
  "structured validation should target an empty line-items field"
)

assert.deepStrictEqual(
  getMobileFormValidationError(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: [{ itemType: "product", itemId: "", quantity: 1, price: 18 }]
  }),
  { message: "销售明细第1行请填写物料", fieldKey: "details", rowIndex: 0, itemFieldKey: "itemId" },
  "structured validation should target a required control in a line-item row"
)

assert.deepStrictEqual(
  getMobileFormValidationError(salesConfig, {
    customerId: 3,
    warehouseId: 8,
    orderTitle: "门店销售",
    details: [{ itemType: "product", itemId: 5, quantity: 0, price: 18 }]
  }),
  { message: "销售明细第1行数量必须大于0", fieldKey: "details", rowIndex: 0, itemFieldKey: "quantity" },
  "structured validation should target a non-positive line-item quantity"
)

assert.strictEqual(
  validateMobileForm(stockCheckConfig, {
    warehouseId: 8,
    counterUserId: 7,
    deadline: "2026-07-14T12:00",
    checkScope: "all"
  }),
  "",
  "mobile stock-check creation should not require manual detail rows"
)

assert.strictEqual(
  validateMobileForm(stockCheckConfig, {
    warehouseId: 8,
    counterUserId: 7,
    deadline: "2026-07-14T12:00",
    checkScope: "selected",
    details: []
  }),
  "请添加指定商品",
  "mobile specified-product stock checks should require selected products"
)

assert.strictEqual(
  validateMobileForm(stockCheckConfig, {
    warehouseId: 8,
    counterUserId: 7,
    deadline: "2026-07-14T12:00",
    checkScope: "selected",
    details: [{ productId: 5, productName: "龙井茶" }]
  }),
  "",
  "mobile specified-product stock checks should pass when products are selected"
)

assert.deepStrictEqual(
  buildMobileFormPayload(stockCheckConfig, {
    warehouseId: 8,
    counterUserId: 7,
    deadline: "2026-07-14T12:00",
    checkScope: "all"
  }),
  {
    warehouseId: 8,
    counterUserId: 7,
    deadline: "2026-07-14 12:00:00",
    checkScope: "all"
  },
  "mobile stock-check payload should send a backend-compatible deadline and real counter user id"
)

assert.deepStrictEqual(
  createMobileFormData(stockCheckConfig, null, {
    selectedDeptId: 202,
    selectedDeptType: "STORE"
  }),
  {
    warehouseId: 202,
    counterUserId: "",
    deadline: "",
    blindCheck: "1",
    checkScope: "all",
    details: [],
    remark: ""
  },
  "mobile store stock-check creation should bind the current store and start as a blind count"
)

const stockCheckDetailsField = stockCheckConfig.fields.find(field => field.key === "details")
assert.strictEqual(isFieldRequired(stockCheckDetailsField, { checkScope: "all" }), false)
assert.strictEqual(isFieldRequired(stockCheckDetailsField, { checkScope: "selected" }), true)
