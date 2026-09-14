import { listPurchaseSuppliers, listPurchaseProducts, listPurchaseOeItems, listPurchaseGifts } from "@/api/inventory/purchase"
import { listSales } from "@/api/inventory/sales"
import { listPurchase } from "@/api/inventory/purchase"
import { listProduct } from "@/api/inventory/product"
import { listOe } from "@/api/inventory/oe"
import { giftCategoryTree, listGift } from "@/api/inventory/gift"
import { listStock } from "@/api/inventory/stock"
import { listStockCheckCounterCandidates } from "@/api/inventory/stockCheck"
import { categoryTree } from "@/api/inventory/category"
import { listCustomerOptions } from "@/api/inventory/customer"
import { getSupplierProducts, listSupplier } from "@/api/inventory/supplier"
import { getMobileOptions } from "@/api/inventory/mobile"
import { listDept, listWarehouseDept } from "@/api/system/dept"
import { getMobileWarehouseOptions } from "@/api/system/mobile"
import { shopTree } from "@/api/system/userShop"
import { listFixedAssetConfigs } from "@/api/oa/fixedAsset"

const SILENT_OPTION_REQUEST = { silentError: true }
const MAX_MOBILE_OPTION_KEYWORD_LENGTH = 80

function normalizeRows(response) {
  if (!response) return []
  if (Array.isArray(response)) return response
  if (Array.isArray(response.rows)) return response.rows
  if (Array.isArray(response.data)) return response.data
  return []
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function normalizeMobileOptionKeyword(keyword) {
  return hasValue(keyword)
    ? String(keyword).trim().slice(0, MAX_MOBILE_OPTION_KEYWORD_LENGTH)
    : ""
}

function createQuery(keyword, options) {
  return Object.assign({
    pageNum: 1,
    pageSize: 20,
    status: "0"
  }, options && options.query ? options.query : {}, keyword ? { keyword } : {})
}

function fetchMobileEntityOptions(entity, options = {}) {
  const keyword = normalizeMobileOptionKeyword(options.keyword)

  if (options.context && options.context.featureKey === "purchase") {
    const purchaseCatalog = {
      supplier: [listPurchaseSuppliers, mapSupplierOption], product: [listPurchaseProducts, mapProductOption],
      oe: [listPurchaseOeItems, mapOeOption], gift: [listPurchaseGifts, mapGiftOption]
    }[entity]
    if (purchaseCatalog) {
      const query = createQuery(keyword, options)
      if (entity === "supplier") { delete query.keyword; query.supplierName = keyword || undefined }
      if (entity === "product" || entity === "oe") {
        const supplierName = options.formData && options.formData.supplierName
        if (supplierName) query.supplierName = supplierName
      }
      return purchaseCatalog[0](query, SILENT_OPTION_REQUEST)
        .then(response => normalizeRows(response).map(purchaseCatalog[1]))
    }
  }

  if (entity === "product") {
    return fetchCompactEntityOptions("product", keyword, options).catch(() => {
      return listProduct(Object.assign(createQuery("", options), keyword ? { productName: keyword } : {}), SILENT_OPTION_REQUEST)
        .then(response => normalizeRows(response).map(mapProductOption))
    })
  }

  if (entity === "oe") {
    return listOe(createQuery(keyword, options), SILENT_OPTION_REQUEST)
      .then(response => normalizeRows(response).map(mapOeOption))
  }

  if (entity === "gift") {
    return listGift(createQuery(keyword, options), SILENT_OPTION_REQUEST)
      .then(response => normalizeRows(response).map(mapGiftOption))
  }

  if (entity === "fixedAssetOe") {
    return fetchFixedAssetOeOptions(keyword, options)
  }

  if (entity === "customer") {
    return listCustomerOptions(keyword).then(response => normalizeRows(response).map(mapCustomerOption))
  }

  if (entity === "supplier") {
    return fetchCompactEntityOptions("supplier", keyword, options).catch(() => {
      return listSupplier(Object.assign(createQuery("", options), keyword ? { supplierName: keyword } : {}), SILENT_OPTION_REQUEST)
        .then(response => enrichSupplierProductRows(normalizeRows(response)).then(rows => rows.map(mapSupplierOption)))
    })
  }

  if (entity === "category") {
    return fetchItemCategoryOptions(keyword, options)
  }

  if (entity === "warehouse") {
    if (isPurposeWarehouseRequest(options)) {
      return fetchPurposeWarehouseOptions(keyword, options)
    }
    return getMobileWarehouseOptions(createCompactQuery(keyword, options), SILENT_OPTION_REQUEST)
      .then(response => normalizeRows(response).map(row => mapBackendOption(row, "WAREHOUSE")))
      .catch(() => fetchAuthorizedDeptOptions("WAREHOUSE").catch(() => {
      return listDept({ deptType: "WAREHOUSE", status: "0" }, SILENT_OPTION_REQUEST).then(response => normalizeRows(response).filter(isStoreOrWarehouse).map(mapDeptOption))
    }))
  }

  if (entity === "store") {
    return fetchAuthorizedDeptOptions("STORE").catch(() => {
      return listDept({ deptType: "STORE", status: "0" }, SILENT_OPTION_REQUEST).then(response => normalizeRows(response).filter(isStoreOrWarehouse).map(mapDeptOption))
    }).then(deptOptions => filterDeptOptions(deptOptions, options))
  }

  if (entity === "dept") {
    return fetchAuthorizedDeptOptions("").catch(() => {
      return listDept({ status: "0" }, SILENT_OPTION_REQUEST).then(response => normalizeRows(response).filter(isStoreOrWarehouse).map(mapDeptOption))
    })
  }

  if (entity === "sales") {
    return listSales(keyword ? { orderNo: keyword, pageNum: 1, pageSize: 20 } : { pageNum: 1, pageSize: 20 }, SILENT_OPTION_REQUEST)
      .then(response => normalizeRows(response).map(mapSalesOption))
  }

  if (entity === "purchase") {
    return listPurchase(keyword ? { orderNo: keyword, pageNum: 1, pageSize: 20 } : { pageNum: 1, pageSize: 20 }, SILENT_OPTION_REQUEST)
      .then(response => normalizeRows(response).map(mapPurchaseOption))
  }

  if (entity === "replenishmentStock") {
    return fetchReplenishmentStockOptions(keyword, options)
  }

  if (entity === "transferSourceStock") {
    return fetchReplenishmentStockOptions(keyword, options)
  }

  if (entity === "stock") {
    return fetchStockOptions(keyword, options)
  }

  if (entity === "stockCheckCounter") {
    return fetchStockCheckCounterOptions(keyword, options)
  }

  return Promise.resolve([])
}

function fetchItemCategoryOptions(keyword, options) {
  const itemType = normalizeItemType(options && options.itemType)
  const request = itemType === "gift" ? giftCategoryTree : categoryTree
  return request(SILENT_OPTION_REQUEST).then(response => {
    return flattenTree(response.data || response.rows || [], "categoryName")
      .filter(row => !keyword || String(row.categoryName || "").indexOf(keyword) > -1 || String(row.categoryCode || "").indexOf(keyword) > -1)
      .map(mapCategoryOption)
  })
}

function fetchCompactEntityOptions(type, keyword, options) {
  const normalizedKeyword = normalizeMobileOptionKeyword(keyword)
  return getMobileOptions(type, createCompactQuery(normalizedKeyword, options), SILENT_OPTION_REQUEST)
    .then(response => normalizeRows(response).map(row => mapBackendOption(row, type)))
}

function createCompactQuery(keyword, options) {
  const normalizedKeyword = normalizeMobileOptionKeyword(keyword)
  const query = options && options.query ? options.query : {}
  return Object.assign({}, normalizedKeyword ? { keyword: normalizedKeyword } : {}, {
    limit: options.limit || query.limit || query.pageSize || 20
  })
}

function fetchAuthorizedDeptOptions(deptType) {
  return shopTree(SILENT_OPTION_REQUEST).then(response => {
    const rows = flattenTree(response.data || response.rows || [], "deptName")
      .filter(row => isStoreOrWarehouse(row))
      .filter(row => !deptType || row.deptType === deptType)
    if (rows.length) return rows.map(mapDeptOption)
    if (deptType === "WAREHOUSE") {
      return listWarehouseDept(undefined, SILENT_OPTION_REQUEST).then(response => normalizeRows(response).map(mapDeptOption))
    }
    return []
  })
}

function isPurposeWarehouseRequest(options) {
  return options && options.field && hasValue(options.field.purpose)
}

function fetchPurposeWarehouseOptions(keyword, options) {
  const context = (options && options.context) || {}
  const query = { purpose: options.field.purpose }
  if (hasValue(context.selectedDeptId)) {
    query.scopeDeptId = context.selectedDeptId
  }
  return listWarehouseDept(query, SILENT_OPTION_REQUEST).then(response => {
    return flattenTree(normalizeRows(response), "deptName")
      .filter(row => row && row.deptType === "WAREHOUSE" && isEnabledDept(row))
      .filter(row => matchWarehouseKeyword(row, keyword))
      .map(mapDeptOption)
  })
}

function fetchReplenishmentStockOptions(keyword, options) {
  const formData = (options && options.formData) || {}
  const warehouseId = formData.fromWarehouseId || formData.fromDeptId
  if (!hasValue(warehouseId) || !hasValue(options && options.itemType)) {
    return Promise.resolve([])
  }
  const query = Object.assign({
    pageNum: 1,
    pageSize: 50,
    shopDeptId: warehouseId,
    warehouseId,
    stockStatus: "available",
    transferSource: true,
    itemType: options.itemType
  }, keyword ? { productName: keyword } : {}, hasValue(options.categoryId) ? { categoryId: options.categoryId } : {})

  return fetchReplenishmentStockRows(query, keyword).then(rows => {
    return filterAllowedStockRows(rows, options).map(mapReplenishmentStockOption)
  })
}

function fetchStockOptions(keyword, options) {
  const formData = (options && options.formData) || {}
  const warehouseId = formData.warehouseId || formData.shopDeptId
  if (!hasValue(warehouseId)) {
    return Promise.resolve([])
  }
  const query = Object.assign({
    pageNum: 1,
    pageSize: 50,
    shopDeptId: warehouseId,
    warehouseId,
    stockStatus: "available"
  }, keyword ? { productName: keyword } : {}, hasValue(options.categoryId) ? { categoryId: options.categoryId } : {})

  return fetchReplenishmentStockRows(query, keyword).then(rows => {
    const allowedRows = filterAllowedStockRows(rows, options)
    if (!keyword || allowedRows.length) {
      return allowedRows.map(mapStockOption)
    }
    return fetchReplenishmentStockRowsByProductIds(query, keyword)
      .then(fallbackRows => filterAllowedStockRows(fallbackRows, options).map(mapStockOption))
  })
}

function fetchFixedAssetOeOptions(keyword, options) {
  const context = (options && options.context) || {}
  const formData = (options && options.formData) || {}
  const shopDeptId = formData.shopDeptId || context.selectedDeptId
  if (!hasValue(shopDeptId)) {
    return Promise.resolve([])
  }
  const query = Object.assign({
    pageNum: 1,
    pageSize: 50,
    shopDeptId,
    status: "0"
  }, keyword ? { oeItemName: keyword } : {})

  return listFixedAssetConfigs(query, SILENT_OPTION_REQUEST).then(response => {
    return normalizeRows(response)
      .filter(row => matchFixedAssetOeKeyword(row, keyword))
      .map(mapFixedAssetOeOption)
  })
}

function fetchStockCheckCounterOptions(keyword, options) {
  const formData = (options && options.formData) || {}
  const context = (options && options.context) || {}
  const warehouseId = formData.warehouseId || context.selectedDeptId
  if (!hasValue(warehouseId)) {
    return Promise.resolve([])
  }
  return listStockCheckCounterCandidates({
    warehouseId,
    keyword: keyword || undefined
  }).then(response => normalizeRows(response).map(mapStockCheckCounterOption))
}

function matchFixedAssetOeKeyword(row, keyword) {
  if (!keyword) return true
  return [row.oeItemName, row.oeItemCode, row.shopDeptName]
    .some(value => String(value || "").indexOf(keyword) > -1)
}

function fetchReplenishmentStockRows(query, keyword) {
  return listStock(query, SILENT_OPTION_REQUEST).then(response => {
    return normalizeRows(response)
      .filter(row => matchStockKeyword(row, keyword))
  })
}

function resolveReplenishmentStockProductIds(keyword) {
  if (!keyword) return Promise.resolve([])
  const searchQuery = { pageNum: 1, pageSize: 20, status: "0" }
  return Promise.all([
    listProduct(Object.assign({}, searchQuery, { productName: keyword }), SILENT_OPTION_REQUEST),
    listProduct(Object.assign({}, searchQuery, { productCode: keyword }), SILENT_OPTION_REQUEST)
  ]).then(results => {
    const productIds = []
    results.forEach(response => {
      normalizeRows(response).forEach(product => {
        const productId = product && product.productId
        if (hasValue(productId) && !productIds.some(id => String(id) === String(productId))) {
          productIds.push(productId)
        }
      })
    })
    return productIds
  })
}

function fetchReplenishmentStockRowsByProductIds(query, keyword) {
  return resolveReplenishmentStockProductIds(keyword).then(productIds => {
    if (!productIds.length) return []
    return Promise.all(productIds.slice(0, 20).map(productId => {
      const productStockQuery = Object.assign({}, query, { productId })
      delete productStockQuery.productName
      return listStock(productStockQuery, SILENT_OPTION_REQUEST)
        .then(response => normalizeRows(response))
        .catch(() => [])
    })).then(results => {
      const rows = []
      results.forEach(productRows => {
        productRows.forEach(row => {
          const rowKey = stockRowKey(row)
          if (row && !rows.some(item => stockRowKey(item) === rowKey)) {
            rows.push(row)
          }
        })
      })
      return rows.filter(row => matchStockKeyword(row, keyword))
    })
  })
}

function stockRowKey(row) {
  if (!row) return ""
  const itemType = normalizeItemType(row.itemType)
  const itemId = firstValue(row, ["itemId", "productId"])
  return String(row.stockId || [itemType, itemId, row.shopDeptId, row.warehouseId, row.batchNo, row.locationCode].join(":"))
}

function filterAllowedStockRows(rows, options) {
  const field = (options && options.field) || {}
  const configuredTypes = Array.isArray(field.allowedItemTypes)
    ? field.allowedItemTypes
    : (Array.isArray(options && options.allowedItemTypes) ? options.allowedItemTypes : [])
  if (!configuredTypes.length) return rows || []
  const allowedTypes = configuredTypes.map(normalizeItemType)
  const selectedType = hasValue(options && options.itemType) ? normalizeItemType(options.itemType) : ""
  return (rows || []).filter(row => {
    const rowType = normalizeItemType(row && row.itemType)
    return allowedTypes.indexOf(rowType) > -1 && (!selectedType || rowType === selectedType)
  })
}

function enrichSupplierProductRows(rows) {
  const sourceRows = Array.isArray(rows) ? rows : []
  return Promise.all(sourceRows.map(row => {
    if (!row || !row.supplierId) return Promise.resolve(row)
    return getSupplierProducts(row.supplierId, SILENT_OPTION_REQUEST).then(response => {
      return Object.assign({}, row, { supplierProducts: normalizeRows(response) })
    }).catch(() => row)
  }))
}

function flattenTree(items, nameKey, parentPath) {
  const prefix = parentPath || []
  return (items || []).reduce((rows, item) => {
    const currentPath = prefix.concat(item[nameKey] || "").filter(Boolean)
    const row = Object.assign({}, item, { optionPath: currentPath.join(" / ") })
    delete row.children
    rows.push(row)
    return rows.concat(flattenTree(item.children || [], nameKey, currentPath))
  }, [])
}

function isStoreOrWarehouse(row) {
  return row && (row.deptType === "STORE" || row.deptType === "WAREHOUSE")
}

function isEnabledDept(row) {
  return row.status === undefined || row.status === null || row.status === "0"
}

function matchWarehouseKeyword(row, keyword) {
  if (!keyword) return true
  return [row.deptName, row.deptId, row.leader, row.phone, row.optionPath, row.deptFullPath]
    .some(value => String(value || "").indexOf(keyword) > -1)
}

function matchStockKeyword(row, keyword) {
  if (!keyword) return true
  return [
    row.itemName,
    row.itemCode,
    row.productName,
    row.productCode,
    row.sku,
    row.itemSpec,
    row.spec,
    row.itemGrade,
    row.grade,
    row.itemCategoryName,
    row.categoryName
  ]
    .some(value => String(value || "").indexOf(keyword) > -1)
}

function stockAvailableQuantity(row) {
  if (hasValue(row.availableQuantity)) return row.availableQuantity
  const currentQuantity = Number(row.currentQuantity)
  const lockedQuantity = Number(row.lockedQuantity || 0)
  if (!Number.isFinite(currentQuantity)) return ""
  return Math.max(currentQuantity - (Number.isFinite(lockedQuantity) ? lockedQuantity : 0), 0)
}

function mapProductOption(row) {
  const item = normalizeMaterialRow(row, "product")
  return {
    value: item.itemId,
    label: item.itemName || item.itemCode || "商品",
    meta: [item.itemCode, item.spec, item.unit, item.salePrice500g ? "¥" + item.salePrice500g : ""].filter(Boolean).join(" · "),
    row: item
  }
}

function mapOeOption(row) {
  const item = normalizeMaterialRow(row, "oe")
  return {
    value: item.itemId,
    label: item.itemName || item.itemCode || "OE",
    meta: [item.itemCode, item.categoryName, item.unit, hasValue(item.costPrice) ? "¥" + item.costPrice : ""].filter(Boolean).join(" · "),
    row: item
  }
}

function mapGiftOption(row) {
  const item = normalizeMaterialRow(row, "gift")
  const guidePrice = hasValue(item.guidePrice1) ? item.guidePrice1 : item.guidePrice2
  return {
    value: item.itemId,
    label: item.itemName || item.itemCode || "礼盒",
    meta: [item.itemCode, item.spec, item.unit, hasValue(guidePrice) ? "¥" + guidePrice : ""].filter(Boolean).join(" · "),
    row: item
  }
}

function mapCustomerOption(row) {
  return {
    value: row.customerId,
    label: row.customerName || row.customerCode || "客户",
    meta: [row.customerCode, row.contactPhone].filter(Boolean).join(" · "),
    row
  }
}

function mapSupplierOption(row) {
  const productNames = (row.supplierProducts || [])
    .map(product => product.productName || product.productCode)
    .filter(Boolean)
    .slice(0, 3)
    .join("、")
  return {
    value: row.supplierId,
    label: row.supplierName || row.supplierCode || "供应商",
    meta: [row.supplierCode, row.contactPerson, row.contactPhone, productNames ? "可供：" + productNames : ""].filter(Boolean).join(" · "),
    row
  }
}

function mapCategoryOption(row) {
  return {
    value: row.categoryId,
    label: row.categoryName || row.categoryCode || "分类",
    meta: row.optionPath || row.categoryCode || "",
    row
  }
}

function mapDeptOption(row) {
  return {
    value: row.deptId,
    label: row.deptName || "组织",
    meta: [row.deptType === "WAREHOUSE" ? "仓库" : "门店", row.optionPath || row.deptFullPath, row.leader, row.phone].filter(Boolean).join(" · "),
    row
  }
}

function filterDeptOptions(deptOptions, options) {
  const rows = Array.isArray(deptOptions) ? deptOptions : []
  const field = (options && options.field) || {}
  const context = (options && options.context) || {}
  if (!field.excludeContextDept || !hasValue(context.selectedDeptId)) {
    return rows
  }
  return rows.filter(option => String(option && option.value) !== String(context.selectedDeptId))
}

function mapReplenishmentStockOption(row) {
  const item = normalizeMaterialRow(row)
  const availableQuantity = stockAvailableQuantity(item)
  return {
    value: item.itemId,
    label: item.itemName || item.itemCode || "物料",
    meta: [
      item.itemCode,
      item.spec,
      item.grade
    ].filter(Boolean).join(" · "),
    row: Object.assign({}, item, {
      availableQuantity,
      availabilityStatus: "loaded"
    })
  }
}

function mapStockOption(row) {
  const item = normalizeMaterialRow(row)
  const availableQuantity = stockAvailableQuantity(item)
  return {
    value: item.itemId,
    label: item.itemName || item.itemCode || "物料",
    meta: [
      item.itemCode,
      item.spec,
      item.grade
    ].filter(Boolean).join(" · "),
    row: Object.assign({}, item, {
      availableQuantity,
      availabilityStatus: "loaded"
    })
  }
}

function mapFixedAssetOeOption(row) {
  return {
    value: row.oeItemId,
    label: row.oeItemName || row.oeItemCode || "固定资产OE",
    meta: [row.oeItemCode, row.assetQuantity ? "数量 " + row.assetQuantity : "", row.assetUnitPrice ? "单价 ¥" + row.assetUnitPrice : ""].filter(Boolean).join(" · "),
    row
  }
}

function mapStockCheckCounterOption(row) {
  const displayName = row.displayName || row.userName || "姓名未配置"
  return {
    value: row.userId,
    label: displayName,
    meta: row.userName && row.userName !== displayName ? "账号 " + row.userName : "可执行盘点",
    row
  }
}

function mapBackendOption(row, type) {
  const value = row.value !== undefined ? row.value : row.deptId
  const materialRow = isMaterialType(type)
    ? normalizeMaterialRow(Object.assign({}, row, { itemId: value }), type)
    : Object.assign({ entityType: type }, row)
  return {
    value,
    label: row.label || row.deptName || row.name || "选项",
    meta: row.meta || row.description || "",
    row: materialRow
  }
}

function normalizeMaterialRow(row, fallbackType) {
  const source = row || {}
  const itemType = normalizeItemType(source.itemType || fallbackType)
  const idKeys = itemType === "oe"
    ? ["itemId", "oeItemId", "productId"]
    : (itemType === "gift" ? ["itemId", "giftId", "productId"] : ["itemId", "productId"])
  const codeKeys = itemType === "oe"
    ? ["itemCode", "oeItemCode", "productCode", "skuCode", "sku", "code"]
    : (itemType === "gift" ? ["itemCode", "giftCode", "productCode", "skuCode", "sku", "code"] : ["itemCode", "productCode", "skuCode", "sku", "code"])
  const nameKeys = itemType === "oe"
    ? ["itemName", "oeItemName", "productName", "skuName", "label", "name"]
    : (itemType === "gift" ? ["itemName", "giftName", "productName", "skuName", "label", "name"] : ["itemName", "productName", "skuName", "label", "name"])
  const itemId = firstValue(source, idKeys)
  const itemCode = firstValue(source, codeKeys)
  const itemName = firstValue(source, nameKeys)
  return Object.assign({}, source, {
    itemType,
    itemId,
    itemCode,
    itemName,
    unit: firstValue(source, ["itemUnit", "unit", "orderUnit", "replenishmentUnit"]),
    spec: firstValue(source, ["itemSpec", "spec", "itemDescription"]),
    grade: firstValue(source, ["itemGrade", "grade"]),
    categoryName: firstValue(source, ["itemCategoryName", "categoryName"]),
    categoryFullPath: firstValue(source, ["itemCategoryFullPath", "categoryFullPath"])
  })
}

function normalizeItemType(value) {
  const normalized = hasValue(value) ? String(value).trim().toLowerCase() : "product"
  return isMaterialType(normalized) ? normalized : "product"
}

function isMaterialType(value) {
  return ["product", "oe", "gift"].indexOf(value) > -1
}

function firstValue(source, keys) {
  for (let i = 0; i < keys.length; i += 1) {
    const value = source && source[keys[i]]
    if (hasValue(value)) return value
  }
  return undefined
}

function mapSalesOption(row) {
  return {
    value: row.orderId || row.salesOrderId,
    label: row.orderNo || row.orderTitle || "销售单",
    meta: [row.customerName, row.status].filter(Boolean).join(" · "),
    row
  }
}

function mapPurchaseOption(row) {
  return {
    value: row.orderId || row.purchaseOrderId,
    label: row.orderNo || row.orderTitle || "采购单",
    meta: [row.supplierName, row.status].filter(Boolean).join(" · "),
    row
  }
}

export {
  fetchMobileEntityOptions,
  mapProductOption,
  mapOeOption,
  mapGiftOption,
  mapDeptOption
}
