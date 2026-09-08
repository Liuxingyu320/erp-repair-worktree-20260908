const assert = require("assert")

const {
  MOBILE_FORM_CONFIG,
  getMobileFormConfig,
  getMobileTransferFormConfig,
  getMobileFormKeys
} = require("../src/views/mobile/feature/mobileFormConfigs")

const requiredBusinessForms = [
  "oaPurchase",
  "sales",
  "purchase",
  "replenishment",
  "salesReturn",
  "purchaseReturn",
  "stockCheck",
  "transfer"
]

requiredBusinessForms.forEach(featureKey => {
  const config = getMobileFormConfig(featureKey)
  assert.ok(config, `${featureKey} should have a mobile form config`)
  assert.strictEqual(MOBILE_FORM_CONFIG[featureKey], config, `${featureKey} should be exposed in MOBILE_FORM_CONFIG`)
  if (featureKey !== "stockCheck" && featureKey !== "oaPurchase") {
    assert.ok(
      config.fields.some(field => field.type === "line-items"),
      `${featureKey} should support editable line items on mobile`
    )
  }
})

assert.ok(
  getMobileFormKeys().filter(key => requiredBusinessForms.includes(key)).length === requiredBusinessForms.length,
  "mobile form keys should include every P0/P1 business form"
)

assert.deepStrictEqual(
  getMobileFormConfig("sales").fields
    .filter(field => ["customerId", "warehouseId"].includes(field.key))
    .map(field => field.type),
  ["entity-picker", "entity-picker"],
  "mobile sales form should use pickers instead of raw customer/warehouse ids"
)

assert.deepStrictEqual(
  getMobileFormConfig("purchase").fields
    .filter(field => ["supplierId", "warehouseId"].includes(field.key))
    .map(field => field.type),
  ["entity-picker", "context-dept"],
  "mobile purchase form should select a supplier and bind the inbound warehouse to the current warehouse context"
)

assert.strictEqual(
  getMobileFormConfig("sales").fields.find(field => field.key === "warehouseId").purpose,
  "deliverySource",
  "mobile sales should only offer warehouses that can fulfill the current store's delivery"
)

;["sales", "purchase"].forEach(featureKey => {
  const config = getMobileFormConfig(featureKey)
  const partyField = config.fields.find(field => field.key === (featureKey === "sales" ? "customerId" : "supplierId"))
  const titleField = config.fields.find(field => field.key === "orderTitle")

  assert.deepStrictEqual(
    {
      fallbackLabelKey: partyField.fallbackLabelKey,
      requiredUnless: partyField.requiredUnless
    },
    {
      fallbackLabelKey: featureKey === "sales" ? "customerName" : "supplierName",
      requiredUnless: { key: featureKey === "sales" ? "customerName" : "supplierName" }
    },
    `${featureKey} edit forms should display and accept the persisted party name when no synthetic id exists`
  )
  assert.strictEqual(titleField.required, true, `${featureKey} title should match backend required validation`)
})

assert.ok(
  getMobileFormConfig("purchase").fields.some(field => field.key === "orderDate") &&
    !getMobileFormConfig("purchase").fields.some(field => field.key === "expectedDate"),
  "mobile purchase forms should use the backend orderDate field"
)

{
  const purchaseDetails = getMobileFormConfig("purchase").fields.find(field => field.key === "details")
  const salesDetails = getMobileFormConfig("sales").fields.find(field => field.key === "details")
  const transferDetails = getMobileFormConfig("transfer").fields.find(field => field.key === "details")

  assert.deepStrictEqual(
    purchaseDetails.allowedItemTypes,
    ["product", "oe", "gift"],
    "mobile purchase details should allow product, OE, and gift materials"
  )
  assert.deepStrictEqual(
    salesDetails.allowedItemTypes,
    ["product", "gift"],
    "mobile sales details should never offer OE materials"
  )
  assert.deepStrictEqual(
    transferDetails.allowedItemTypes,
    ["product", "gift"],
    "mobile transfer details should exclude OE materials"
  )

  ;[purchaseDetails, salesDetails].forEach(detailsField => {
    assert.deepStrictEqual(
      detailsField.itemFields.slice(0, 2).map(field => ({ key: field.key, type: field.type })),
      [
        { key: "itemType", type: "select" },
        { key: "itemId", type: "entity-picker" }
      ],
      `${detailsField.label} should choose a material type before choosing the material`
    )
    assert.strictEqual(
      detailsField.itemFields[0].defaultValue,
      "",
      `${detailsField.label} should require an explicit material type before opening a catalog`
    )
    assert.ok(
      detailsField.itemFields.some(field => field.key === "itemName" && field.type === "readonly") &&
        detailsField.itemFields.some(field => field.key === "itemCode" && field.type === "readonly"),
      `${detailsField.label} should edit and display generic material names and codes`
    )
  })
  assert.strictEqual(transferDetails.selectionMode, "stock-picker",
    "mobile transfer details should choose inventory from the typed source organization")
  assert.ok(
    transferDetails.itemFields.some(field => field.key === "costPrice" && !field.permissions),
    "mobile replenishment details should always expose the business reference cost"
  )
}

assert.strictEqual(
  getMobileFormConfig("salesReturn").fields.find(field => field.key === "salesOrderId").required,
  true,
  "mobile sales-return form should require the original sales order before submit"
)

assert.strictEqual(
  getMobileFormConfig("purchaseReturn").fields.find(field => field.key === "purchaseOrderId").required,
  true,
  "mobile purchase-return form should require the original purchase order before submit"
)

;["salesReturn", "purchaseReturn"].forEach(featureKey => {
  const detailsField = getMobileFormConfig(featureKey).fields.find(field => field.key === "details")
  assert.strictEqual(
    detailsField.allowManualAdd,
    false,
    `${featureKey} return details should come from the selected source order instead of blank manual rows`
  )
  assert.ok(
    detailsField.itemFields.some(field => field.key === "productName" && field.type === "readonly") &&
      detailsField.itemFields.some(field => field.key === "maxReturnQuantity" && field.type === "readonly") &&
      detailsField.itemFields.some(field => field.key === "unitPrice" && field.type === "readonly"),
    `${featureKey} return details should show source-order product, returnable quantity, and original unit price`
  )
})

assert.deepStrictEqual(
  getMobileFormConfig("salesReturn").fields
    .filter(field => ["customerName", "customerId"].includes(field.key))
    .map(field => ({ key: field.key, type: field.type, required: field.required })),
  [{ key: "customerName", type: "readonly", required: true }],
  "mobile sales-return form should display the source-order customer name instead of requiring a second customer picker"
)

assert.deepStrictEqual(
  getMobileFormConfig("purchaseReturn").fields
    .filter(field => ["supplierName", "supplierId"].includes(field.key))
    .map(field => ({ key: field.key, type: field.type, required: field.required })),
  [{ key: "supplierName", type: "readonly", required: true }],
  "mobile purchase-return form should display the source-order supplier name instead of requiring a second supplier picker"
)

assert.deepStrictEqual(
  ["salesReturn", "purchaseReturn"].map(featureKey => {
    const field = getMobileFormConfig(featureKey).fields.find(item => item.key === "shopDeptId")
    return [featureKey, field && field.type, field && field.label]
  }),
  [
    ["salesReturn", "context-dept", "退货门店"],
    ["purchaseReturn", "context-dept", "退货仓库"]
  ],
  "mobile returns should display and submit the current inventory organization instead of exposing an unrelated warehouse picker"
)

{
  const transferMatrix = ["warehouse", "store_return", "cross_store"].map(transferType => {
    const config = getMobileTransferFormConfig(transferType)
    const fromField = config.fields.find(field => field.key === "fromDeptId")
    const toField = config.fields.find(field => field.key === "toDeptId")
    const typeField = config.fields.find(field => field.key === "transferType")
    const detailsField = config.fields.find(field => field.key === "details")
    return {
      transferType,
      fixedTransferType: config.fixedTransferType,
      source: [fromField.type, fromField.entity || ""],
      target: [toField.type, toField.entity || ""],
      typeField: [typeField.type, typeField.defaultValue],
      pickerEntity: detailsField.pickerEntity
    }
  })

  assert.deepStrictEqual(transferMatrix, [
    {
      transferType: "warehouse",
      fixedTransferType: "warehouse",
      source: ["entity-picker", "warehouse"],
      target: ["context-dept", ""],
      typeField: ["readonly", "warehouse"],
      pickerEntity: "replenishmentStock"
    },
    {
      transferType: "store_return",
      fixedTransferType: "store_return",
      source: ["context-dept", ""],
      target: ["entity-picker", "warehouse"],
      typeField: ["readonly", "store_return"],
      pickerEntity: "transferSourceStock"
    },
    {
      transferType: "cross_store",
      fixedTransferType: "cross_store",
      source: ["entity-picker", "store"],
      target: ["context-dept", ""],
      typeField: ["readonly", "cross_store"],
      pickerEntity: "transferSourceStock"
    }
  ], "mobile transfer forms should fix all three direction contracts instead of exposing a generic organization pair")

  assert.strictEqual(
    getMobileTransferFormConfig("cross_store").fields.find(field => field.key === "fromDeptId").excludeContextDept,
    true,
    "cross-store source picker should exclude the current target store"
  )
}

assert.deepStrictEqual(
  getMobileFormConfig("replenishment").submitModes.map(mode => mode.action),
  ["save", "submit"],
  "mobile replenishment should create a real replenishment transfer draft or submitted request"
)

assert.ok(
  getMobileFormConfig("replenishment").fields.some(field => field.key === "fromDeptId" && field.entity === "warehouse") &&
    getMobileFormConfig("replenishment").fields.some(field => field.key === "toDeptId" && field.type === "context-dept") &&
    getMobileFormConfig("replenishment").fields.some(field => field.key === "details" && field.type === "line-items"),
  "mobile replenishment form should collect source warehouse, confirm current store, and requested items"
)

{
  const replenishmentConfig = getMobileFormConfig("replenishment")
  const sourceWarehouseField = replenishmentConfig.fields.find(field => field.key === "fromDeptId")
  const transferTypeField = replenishmentConfig.fields.find(field => field.key === "transferType")
  const detailsField = replenishmentConfig.fields.find(field => field.key === "details")
  const itemFields = detailsField.itemFields || []

  assert.strictEqual(
    sourceWarehouseField.purpose,
    "replenishmentSource",
    "mobile replenishment warehouse picker should use the desktop replenishment-source warehouse scope"
  )

  const returnTargetField = getMobileFormConfig("transfer", "store_return").fields.find(
    field => field.key === "toDeptId"
  )
  assert.strictEqual(
    returnTargetField.purpose,
    "returnTarget",
    "mobile store-return warehouse picker should use its own target scope instead of replenishment-source semantics"
  )

  assert.deepStrictEqual(
    {
      type: transferTypeField.type,
      defaultValue: transferTypeField.defaultValue,
      displayValue: transferTypeField.displayValue
    },
    {
      type: "readonly",
      defaultValue: "warehouse",
      displayValue: "门店要货"
    },
    "mobile replenishment should show a fixed warehouse transfer type instead of offering cross-store transfer"
  )

  assert.ok(
    detailsField.selectionMode === "stock-picker" &&
      detailsField.pickerEntity === "replenishmentStock" &&
      detailsField.requirePickerItemType === true &&
      detailsField.allowedItemTypes.join(",") === "product,gift,oe" &&
      detailsField.pickerField.allowedItemTypes.join(",") === "product,gift,oe" &&
      itemFields.some(field => field.key === "itemName" && field.type === "readonly") &&
      itemFields.some(field => field.key === "availableQuantity" && field.type === "readonly") &&
      itemFields.some(field => field.key === "costPrice" && field.type === "readonly" && !field.permissions) &&
      !itemFields.some(field => field.key === "price"),
    "mobile replenishment details should use a batch stock picker, show reference price, and avoid generic price entry"
  )
}

assert.deepStrictEqual(
  getMobileFormConfig("oaPurchase").fields.map(field => field.key),
  ["title", "amount", "reason", "remark"],
  "mobile OA purchase form should match desktop/backend main-table fields"
)

assert.deepStrictEqual(
  getMobileFormConfig("oaPurchase").submitModes.map(mode => mode.action),
  ["save", "submit"],
  "mobile OA purchase form should support saving a draft and submitting"
)

{
  const stockCheckFields = getMobileFormConfig("stockCheck").fields
  const inventoryOrgField = stockCheckFields.find(field => field.key === "warehouseId")
  const blindCheckField = stockCheckFields.find(field => field.key === "blindCheck")
  assert.ok(
    inventoryOrgField && inventoryOrgField.type === "context-dept" && inventoryOrgField.label === "盘点组织",
    "mobile stock-check creation must bind the inventory organization to the current store or warehouse"
  )
  assert.ok(
    blindCheckField && blindCheckField.defaultValue === "1",
    "mobile stock-check creation should default to blind counting"
  )
}

{
  const fixedAssetRepairConfig = getMobileFormConfig("fixedAssetRepair")
  const imageField = fixedAssetRepairConfig.fields.find(field => field.key === "imageUrls")
  const quantityField = fixedAssetRepairConfig.fields.find(field => field.key === "repairQuantity")
  assert.deepStrictEqual(
    {
      label: quantityField.label,
      type: quantityField.type,
      required: quantityField.required
    },
    {
      label: "坏掉数量",
      type: "number",
      required: true
    },
    "mobile fixed asset repair should collect damaged quantity instead of a repair amount"
  )
  assert.deepStrictEqual(
    {
      type: imageField.type,
      limit: imageField.limit,
      fileSize: imageField.fileSize,
      accept: imageField.accept
    },
    {
      type: "image-upload",
      limit: 5,
      fileSize: 5,
      accept: "image/*"
    },
    "mobile fixed asset repair should upload photos instead of asking users to paste image URLs"
  )
}

{
  const stockCheckDetails = getMobileFormConfig("stockCheck").fields.find(field => field.key === "details")
  assert.ok(
    stockCheckDetails &&
      stockCheckDetails.type === "line-items" &&
      stockCheckDetails.payloadMode === "stock-check-products" &&
      stockCheckDetails.selectionMode === "stock-picker" &&
      stockCheckDetails.pickerEntity === "stock" &&
      stockCheckDetails.requiredWhen &&
      stockCheckDetails.requiredWhen.key === "checkScope" &&
      stockCheckDetails.requiredWhen.value === "selected",
    "mobile stock-check creation should support selecting specific stocked products"
  )
  const stockCheckFields = getMobileFormConfig("stockCheck").fields
  const counterField = stockCheckFields.find(field => field.key === "counterUserId")
  const deadlineField = stockCheckFields.find(field => field.key === "deadline")
  assert.ok(
    counterField && counterField.required === true && counterField.entity === "stockCheckCounter" &&
      deadlineField && deadlineField.required === true && deadlineField.type === "datetime-local",
    "mobile stock-check creation should require a real counter and deadline"
  )
}

;[
  "supplier",
  "product",
  "category",
  "salaryScheme",
  "transferRules"
].forEach(featureKey => {
  assert.strictEqual(
    getMobileFormConfig(featureKey),
    null,
    `${featureKey} should not expose mobile create/edit form capability`
  )
})

{
  const customerConfig = getMobileFormConfig("customer")
  assert.ok(
    customerConfig &&
      customerConfig.idKey === "customerId" &&
      customerConfig.passthroughFields.includes("version") &&
      customerConfig.fields.some(field => field.key === "teaPreferences") &&
      customerConfig.fields.some(field => field.key === "cautions") &&
      customerConfig.fields.some(field => field.key === "budgetMin"),
    "mobile customer service cards should support scoped create/edit while preserving optimistic-lock metadata"
  )
}
