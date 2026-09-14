import { getSalesDetail, listSales } from "@/api/inventory/sales"
import { getPurchaseDetail, getPurchaseDraft, getPurchaseActionContext, getPurchaseReceiveContext, listPurchase } from "@/api/inventory/purchase"
import { getStock, listStock, listStockLog } from "@/api/inventory/stock"
import {
  getStockCheck,
  getStockCheckApprovalTrack,
  listStockCheck,
  listStockCheckApprovalTodos
} from "@/api/inventory/stockCheck"
import { getProduct, listProduct } from "@/api/inventory/product"
import { getOe, listOe } from "@/api/inventory/oe"
import { getGift, listGift } from "@/api/inventory/gift"
import { categoryTree, getCategory } from "@/api/inventory/category"
import { getCustomerServiceCard, listCustomerServiceCards } from "@/api/inventory/customer"
import { getSupplier, getSupplierProducts, listSupplier } from "@/api/inventory/supplier"
import { getDeliveryNotice, listDeliveryNotice } from "@/api/inventory/deliveryNotice"
import { getSalesReturn, getSalesReturnDraft, getSalesReturnActionContext, listSalesReturn } from "@/api/inventory/salesReturn"
import { getPurchaseReturn, getPurchaseReturnDraft, getPurchaseReturnActionContext, listPurchaseReturn } from "@/api/inventory/purchaseReturn"
import { getTransferApprovalTrack, getTransferDetail, listTransferProcessing, listTransferRecords } from "@/api/inventory/transfer"
import { getPurchaseDetail as getOaPurchaseDetail, listMyPurchases } from "@/api/oa/purchase"
import { getFixedAssetRepair, listFixedAssetRepairs } from "@/api/oa/fixedAsset"
import { getSalary, listMySalary, listAllSalary } from "@/api/oa/salary"
import { getNotice, listNotice, listNoticeTop } from "@/api/system/notice"
import { getUser, getUserProfile, listUser } from "@/api/system/user"
import { getRole, listRole } from "@/api/system/role"
import { getPost, listPost } from "@/api/system/post"
import { getDept, listDept } from "@/api/system/dept"
import { getMenu, listMenu } from "@/api/system/menu"
import { batchUserShop, getUserShop, shopTree } from "@/api/system/userShop"
import { getConfig, listConfig } from "@/api/system/config"
import { getType, listType } from "@/api/system/dict/type"
import { getData, listData } from "@/api/system/dict/data"
import { list as listLogininfor } from "@/api/system/logininfor"
import { list as listOperlog } from "@/api/system/operlog"
import { getJob, listJob } from "@/api/monitor/job"
import { getJobLog, listJobLog } from "@/api/monitor/jobLog"
import { list as listOnline } from "@/api/monitor/online"
import { getSalaryScheme, listSalaryItems, listSalaryScheme } from "@/api/system/salaryConfig"
import { getTransferApprovalRule, listTransferApprovalRule } from "@/api/inventory/transferApprovalRule"
import { listTransferApprovalTodos } from "@/api/inventory/mobile"
import { getApprovalInstance } from "@/api/approval/monitor"
import { unwrapData } from "@/views/approval/manage/components/approvalUi"
import auth from "@/plugins/auth"

const { buildTodoFocusQuery } = require("@/utils/todoBusinessFocus")

const CONTEXT_SCOPED_FEATURES = [
  "sales",
  "purchase",
  "stock",
  "stockLog",
  "replenishment",
  "stockCheck",
  "salesReturn",
  "purchaseReturn",
  "outbound",
  "transfer",
  "transferApproval",
  "transferRecords",
  "fixedAssetRepair"
]

const normalizeRows = (response) => {
  if (!response) return []
  if (Array.isArray(response.rows)) return response.rows
  if (Array.isArray(response.data)) return response.data
  return []
}

const normalizeTotal = (response) => {
  const total = response && response.total
  return Number.isFinite(Number(total)) ? Number(total) : normalizeRows(response).length
}

const cleanQuery = (query) => {
  return Object.keys(query || {}).reduce((acc, key) => {
    const value = query[key]
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      acc[key] = value
    }
    return acc
  }, {})
}

const createBaseQuery = (options, featureKey) => {
  const source = options || {}
  const query = {
    pageNum: source.pageNum || 1,
    pageSize: source.pageSize || 10
  }
  if (featureKey === "stock" && source.selectedDeptType === "STORE" && source.stockScopeMode === "managed") {
    query.ownOnly = false
    query.shopDeptId = source.managedStoreId || 0
    return cleanQuery(query)
  }
  if (source.selectedDeptId && CONTEXT_SCOPED_FEATURES.indexOf(featureKey) > -1) {
    if (source.selectedDeptType === "WAREHOUSE") {
      query.warehouseId = source.selectedDeptId
    }
    if (source.selectedDeptType === "STORE") {
      query.shopDeptId = source.selectedDeptId
    }
  }
  return cleanQuery(query)
}

const normalizeTransferListQuery = (query, options) => {
  const source = options || {}
  const transferQuery = Object.assign({}, query || {})
  const direction = transferQuery.direction ? String(transferQuery.direction).trim() : ""
  const statusGroup = transferQuery.statusGroup ? String(transferQuery.statusGroup).trim() : ""
  delete transferQuery.direction

  if (statusGroup === "deliverable") {
    transferQuery.statusGroup = statusGroup
    transferQuery.statuses = ["approved", "reserved", "partial_delivered"]
  }

  if (statusGroup === "receivable") {
    transferQuery.statusGroup = statusGroup
    transferQuery.statuses = ["partial_delivered", "delivered", "partial_received"]
  }

  if (source.selectedDeptId && direction === "receive") {
    delete transferQuery.warehouseId
    delete transferQuery.shopDeptId
    transferQuery.toDeptId = source.selectedDeptId
  }

  if (source.selectedDeptId && direction === "deliver") {
    delete transferQuery.warehouseId
    delete transferQuery.shopDeptId
    transferQuery.fromDeptId = source.selectedDeptId
  }

  return cleanQuery(transferQuery)
}

const resolveList = (promise) => {
  return promise.then(response => ({
    rows: normalizeRows(response),
    total: normalizeTotal(response)
  }))
}

const hasValue = (value) => {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

const resolveProductSearchRows = (keyword) => {
  const searchQuery = { pageNum: 1, pageSize: 20, status: "0" }
  return Promise.all([
    listProduct(Object.assign({}, searchQuery, { productCode: keyword })),
    listProduct(Object.assign({}, searchQuery, { productName: keyword }))
  ]).then(results => {
    const products = []
    results.forEach(response => {
      normalizeRows(response).forEach(product => {
        if (product && hasValue(product.productId) && !products.some(item => String(item.productId) === String(product.productId))) {
          products.push(product)
        }
      })
    })
    return products
  })
}

const resolveStockProductQuery = (query) => {
  const stockQuery = Object.assign({}, query || {})
  const keyword = hasValue(stockQuery.productKeyword) ? String(stockQuery.productKeyword).trim() : ""
  delete stockQuery.productKeyword

  if (!keyword) {
    return Promise.resolve({ query: stockQuery, noMatch: false })
  }

  return resolveProductSearchRows(keyword).then(products => {
    if (!products.length) {
      return { query: stockQuery, noMatch: true }
    }

    return {
      query: Object.assign({}, stockQuery, { productId: products[0].productId }),
      noMatch: false
    }
  })
}

const fetchStockRows = (query) => {
  return resolveStockProductQuery(query).then(resolved => {
    if (resolved.noMatch) {
      return { rows: [], total: 0 }
    }
    return resolveList(listStock(resolved.query)).then(result => {
      return hydrateStockProducts(result.rows).then(rows => Object.assign({}, result, { rows }))
    })
  })
}

const fetchStockLogRows = (query) => {
  return resolveStockProductQuery(query).then(resolved => {
    if (resolved.noMatch) {
      return { rows: [], total: 0 }
    }
    return resolveList(listStockLog(resolved.query)).then(result => {
      return hydrateStockProducts(result.rows).then(rows => Object.assign({}, result, { rows }))
    })
  })
}

const hydrateStockProducts = (rows) => {
  const productIds = rows
    .map(row => row && row.productId)
    .filter(value => value !== undefined && value !== null && String(value).trim() !== "")
  const uniqueProductIds = Array.from(new Set(productIds.map(value => String(value)))).slice(0, 10)

  if (uniqueProductIds.length === 0) {
    return Promise.resolve(rows)
  }

  return Promise.all(uniqueProductIds.map(productId => {
    return getProduct(productId).then(res => res.data).catch(() => null)
  })).then(products => {
    const productMap = products.reduce((acc, product) => {
      if (product && product.productId) {
        acc[String(product.productId)] = product
      }
      return acc
    }, {})

    return rows.map(row => {
      const product = row && productMap[String(row.productId)]
      return product ? Object.assign({}, row, { product }) : row
    })
  })
}

const flattenCategoryRows = (items, parentPath, level) => {
  const pathPrefix = parentPath || []
  const currentLevel = level || 1
  return (items || []).reduce((rows, item) => {
    const name = item && item.categoryName ? String(item.categoryName) : ""
    const currentPath = pathPrefix.concat(name).filter(Boolean)
    const row = Object.assign({}, item, {
      categoryLevel: currentLevel,
      categoryFullPath: currentPath.join(" / ")
    })
    delete row.children
    rows.push(row)
    return rows.concat(flattenCategoryRows(item.children || [], currentPath, currentLevel + 1))
  }, [])
}

const filterCategoryRows = (rows, query) => {
  const source = query || {}
  const keyword = hasValue(source.keyword) ? String(source.keyword).trim().toLowerCase() : ""
  const status = hasValue(source.status) ? String(source.status).trim() : ""

  return rows.filter(row => {
    if (status && String(row.status) !== status) return false
    if (!keyword) return true
    return [
      row.categoryName,
      row.categoryCode,
      row.categoryFullPath
    ].some(value => hasValue(value) && String(value).toLowerCase().indexOf(keyword) > -1)
  })
}

const paginateRows = (rows, query) => {
  const pageNum = Math.max(Number(query.pageNum) || 1, 1)
  const pageSize = Math.max(Number(query.pageSize) || 10, 1)
  const start = (pageNum - 1) * pageSize
  return {
    rows: rows.slice(start, start + pageSize),
    total: rows.length
  }
}

const fetchCategoryRows = (query) => {
  return categoryTree().then(response => {
    const rows = flattenCategoryRows(response.data || response.rows || [])
    return paginateRows(filterCategoryRows(rows, query), query)
  })
}

const flattenDeptRows = (items, parentPath, level) => {
  const pathPrefix = parentPath || []
  const currentLevel = level || 1
  return (items || []).reduce((rows, item) => {
    const name = item && item.deptName ? String(item.deptName) : ""
    const currentPath = pathPrefix.concat(name).filter(Boolean)
    const row = Object.assign({}, item, {
      deptLevel: currentLevel,
      deptFullPath: currentPath.join(" / ")
    })
    delete row.children
    rows.push(row)
    return rows.concat(flattenDeptRows(item.children || [], currentPath, currentLevel + 1))
  }, [])
}

const filterDeptRows = (rows, query) => {
  const source = query || {}
  const deptName = hasValue(source.deptName) ? String(source.deptName).trim().toLowerCase() : ""
  const leader = hasValue(source.leader) ? String(source.leader).trim().toLowerCase() : ""
  const phone = hasValue(source.phone) ? String(source.phone).trim() : ""
  const deptType = hasValue(source.deptType) ? String(source.deptType).trim() : ""
  const status = hasValue(source.status) ? String(source.status).trim() : ""

  return rows.filter(row => {
    if (deptName && [
      row.deptName,
      row.deptFullPath
    ].every(value => !hasValue(value) || String(value).toLowerCase().indexOf(deptName) === -1)) return false
    if (leader && (!hasValue(row.leader) || String(row.leader).toLowerCase().indexOf(leader) === -1)) return false
    if (phone && (!hasValue(row.phone) || String(row.phone).indexOf(phone) === -1)) return false
    if (deptType && String(row.deptType || "") !== deptType) return false
    if (status && String(row.status || "") !== status) return false
    return true
  })
}

const fetchDeptRows = (query) => {
  return listDept(query).then(response => {
    const sourceRows = normalizeRows(response)
    const rows = flattenDeptRows(sourceRows.length ? sourceRows : (response && response.data) || [])
    return paginateRows(filterDeptRows(rows, query), query)
  })
}

const flattenMenuRows = (items, parentPath, level) => {
  const pathPrefix = parentPath || []
  const currentLevel = level || 1
  return (items || []).reduce((rows, item) => {
    const name = item && item.menuName ? String(item.menuName) : ""
    const currentPath = pathPrefix.concat(name).filter(Boolean)
    const row = Object.assign({}, item, {
      menuLevel: currentLevel,
      menuFullPath: currentPath.join(" / ")
    })
    delete row.children
    rows.push(row)
    return rows.concat(flattenMenuRows(item.children || [], currentPath, currentLevel + 1))
  }, [])
}

const filterMenuRows = (rows, query) => {
  const source = query || {}
  const menuName = hasValue(source.menuName) ? String(source.menuName).trim().toLowerCase() : ""
  const perms = hasValue(source.perms) ? String(source.perms).trim().toLowerCase() : ""
  const menuType = hasValue(source.menuType) ? String(source.menuType).trim() : ""
  const status = hasValue(source.status) ? String(source.status).trim() : ""

  return rows.filter(row => {
    if (menuName && [
      row.menuName,
      row.menuFullPath,
      row.path,
      row.component
    ].every(value => !hasValue(value) || String(value).toLowerCase().indexOf(menuName) === -1)) return false
    if (perms && (!hasValue(row.perms) || String(row.perms).toLowerCase().indexOf(perms) === -1)) return false
    if (menuType && String(row.menuType || "") !== menuType) return false
    if (status && String(row.status || "") !== status) return false
    return true
  })
}

const fetchMenuRows = (query) => {
  return listMenu(query).then(response => {
    const sourceRows = normalizeRows(response)
    const rows = flattenMenuRows(sourceRows.length ? sourceRows : (response && response.data) || [])
    return paginateRows(filterMenuRows(rows, query), query)
  })
}

const fetchUserShopRows = (query) => {
  return Promise.all([
    resolveList(listUser(query)),
    fetchShopTreeRows()
  ]).then(([userResult, shopRows]) => {
    const users = userResult.rows || []
    const userIds = users
      .map(row => row && row.userId)
      .filter(value => hasValue(value))

    if (!userIds.length) {
      return userResult
    }

    return batchUserShop(userIds).then(response => {
      const scopeMap = response && response.data ? response.data : {}
      const rows = users.map(user => enrichUserShopRow(user, scopeMap[user.userId], shopRows))
      return Object.assign({}, userResult, { rows })
    }).catch(() => {
      const rows = users.map(user => enrichUserShopRow(user, [], shopRows))
      return Object.assign({}, userResult, { rows })
    })
  })
}

const fetchUserShopDetail = (userId) => {
  return Promise.all([
    resolveDetail(getUser(userId)).then(detail => {
      const data = detail || {}
      return data.user || data
    }),
    getUserShop(userId).catch(() => ({})),
    fetchShopTreeRows()
  ]).then(([user, scopeResponse, shopRows]) => {
    const shopIds = scopeResponse && scopeResponse.data && Array.isArray(scopeResponse.data.shopIds)
      ? scopeResponse.data.shopIds
      : (scopeResponse && scopeResponse.shopIds) || []
    return enrichUserShopRow(user, shopIds, shopRows)
  })
}

const fetchShopTreeRows = () => {
  return shopTree().then(response => flattenShopRows(response.data || [])).catch(() => [])
}

const flattenShopRows = (items, parentPath) => {
  const pathPrefix = parentPath || []
  return (items || []).reduce((rows, item) => {
    const name = item && item.deptName ? String(item.deptName) : ""
    const currentPath = pathPrefix.concat(name).filter(Boolean)
    const row = Object.assign({}, item, {
      deptFullPath: currentPath.join(" / ")
    })
    delete row.children
    rows.push(row)
    return rows.concat(flattenShopRows(item.children || [], currentPath))
  }, [])
}

const enrichUserShopRow = (user, shopIds, shopRows) => {
  const normalizedIds = normalizeShopIds(shopIds)
  const scopeRows = shopRows.filter(row => normalizedIds.indexOf(Number(row.deptId)) > -1)
  const scopeLabel = formatShopScopeLabel(scopeRows) || getUserDeptName(user)
  return Object.assign({}, user, {
    shopScopeIds: normalizedIds,
    shopScopeRows: scopeRows,
    shopScopeLabel: scopeLabel
  })
}

const normalizeShopIds = (shopIds) => {
  return Array.from(new Set((shopIds || []).map(id => Number(id)).filter(id => id > 0)))
}

const formatShopScopeLabel = (rows) => {
  const names = (rows || [])
    .filter(row => row && (row.deptType === "STORE" || row.deptType === "WAREHOUSE"))
    .map(row => row.deptName || row.deptFullPath)
    .filter(value => hasValue(value))
  if (!names.length) return ""
  return names.slice(0, 3).join("、") + (names.length > 3 ? " 等" + names.length + "个组织" : "")
}

const getUserDeptName = (user) => {
  const dept = user && user.dept ? user.dept : {}
  return firstValue(user, ["deptName"]) || firstValue(dept, ["deptName"]) || ""
}

const fetchNoticeRows = (query) => {
  return listNoticeTop().then(response => {
    const rows = filterNoticeRows(normalizeRows(response), query)
    return paginateRows(rows, query)
  }).catch(() => resolveList(listNotice(query)))
}

const filterNoticeRows = (rows, query) => {
  const source = query || {}
  const noticeTitle = hasValue(source.noticeTitle) ? String(source.noticeTitle).trim().toLowerCase() : ""
  const createBy = hasValue(source.createBy) ? String(source.createBy).trim().toLowerCase() : ""
  const noticeType = hasValue(source.noticeType) ? String(source.noticeType).trim() : ""
  const isRead = hasValue(source.isRead) ? normalizeBoolean(source.isRead) : null

  return (rows || []).filter(row => {
    if (noticeTitle && String(row.noticeTitle || "").toLowerCase().indexOf(noticeTitle) === -1) return false
    if (createBy && String(row.createBy || "").toLowerCase().indexOf(createBy) === -1) return false
    if (noticeType && String(row.noticeType) !== noticeType) return false
    if (isRead !== null && normalizeBoolean(row.isRead) !== isRead) return false
    return true
  })
}

const fetchProfileRows = () => {
  return getUserProfile().then(response => {
    const data = response && response.data ? response.data : {}
    const user = data.user || data
    const row = Object.assign({}, user, {
      roleGroup: data.roleGroup,
      postGroup: data.postGroup
    })
    return {
      rows: [row],
      total: 1
    }
  })
}

const normalizeSalaryQuery = (query) => {
  const salaryQuery = Object.assign({}, query || {})
  const salaryMonthScope = salaryQuery.salaryMonthScope
  delete salaryQuery.salaryMonthScope

  if (salaryMonthScope === "all") {
    return salaryQuery
  }

  if (!hasValue(salaryQuery.salaryMonth)) {
    salaryQuery.salaryMonth = currentSalaryMonth()
  }

  return salaryQuery
}

const resolveFocusedRow = (request, idKey, targetId) => {
  return request.then(response => {
    const row = response && response.data
    return row && typeof row === "object"
      ? { rows: [Object.assign({}, row, { [idKey]: String(targetId) })], total: 1 }
      : { rows: [], total: 0 }
  }).catch(error => {
    const status = error && error.response && error.response.status
    const code = error && (error.code || error.response && error.response.data && error.response.data.code)
    if (status === 404 || code === 404) return { rows: [], total: 0 }
    throw error
  })
}

const fetchFocusedFeatureData = (featureKey, query) => {
  if (!hasValue(query && query.businessId) || featureKey === "stock") return null
  const ids = {
    sales: query.orderId,
    purchase: query.orderId,
    outbound: query.noticeId,
    stockCheck: query.checkId,
    salesReturn: query.returnId,
    purchaseReturn: query.returnId,
    transfer: query.transferId,
    transferApproval: query.transferId,
    oaPurchase: query.purchaseId,
    fixedAssetRepair: query.repairId
  }
  const id = ids[featureKey]
  if (!hasValue(id)) return null
  if (featureKey === "sales") return resolveFocusedRow(getSalesDetail(id), "orderId", id)
  if (featureKey === "purchase") return resolveFocusedRow(getPurchaseActionContext(id, { silentError: true }), "orderId", id)
  if (featureKey === "outbound") return resolveFocusedRow(getDeliveryNotice(id), "noticeId", id)
  if (featureKey === "stockCheck") return resolveFocusedRow(getStockCheck(id), "checkId", id)
  if (featureKey === "salesReturn") return resolveFocusedRow(getSalesReturnActionContext(id, { silentError: true }), "returnId", id)
  if (featureKey === "purchaseReturn") return resolveFocusedRow(getPurchaseReturnActionContext(id, { silentError: true }), "returnId", id)
  if (featureKey === "transfer" || featureKey === "transferApproval") {
    return resolveFocusedRow(getTransferDetail(id), "transferId", id)
  }
  if (featureKey === "oaPurchase") {
    return resolveFocusedRow(getOaPurchaseDetail(id), "purchaseId", id)
  }
  if (featureKey === "fixedAssetRepair") return resolveFocusedRow(getFixedAssetRepair(id), "repairId", id)
  return null
}

export function fetchMobileFeatureData(featureKey, options = {}) {
  const baseQuery = Object.assign({}, createBaseQuery(options, featureKey), cleanQuery(options.query || {}))
  const query = buildTodoFocusQuery(featureKey, baseQuery)
  const approvalTodo = featureKey === "stockCheck" && (query.approvalTodo === true || query.approvalTodo === "true")
  delete query.approvalTodo
  const focused = fetchFocusedFeatureData(featureKey, query)
  if (focused) return focused

  if (featureKey === "sales") {
    return resolveList(listSales(query))
  }

  if (featureKey === "purchase") {
    if (options.selectedDeptType !== "WAREHOUSE") {
      return Promise.resolve({ rows: [], total: 0 })
    }
    return resolveList(listPurchase(query))
  }

  if (featureKey === "stock") {
    return fetchStockRows(query)
  }

  if (featureKey === "product") {
    return resolveList(listProduct(query))
  }

  if (featureKey === "oe") {
    return resolveList(listOe(query))
  }

  if (featureKey === "gift") {
    return resolveList(listGift(query))
  }

  if (featureKey === "category") {
    return fetchCategoryRows(query)
  }

  if (featureKey === "customer") {
    return resolveList(listCustomerServiceCards(query))
  }

  if (featureKey === "supplier") {
    return resolveList(listSupplier(query))
  }

  if (featureKey === "stockLog") {
    return fetchStockLogRows(query)
  }

  if (featureKey === "replenishment") {
    return resolveList(listTransferProcessing(normalizeTransferListQuery(query, options)))
  }

  if (featureKey === "stockCheck") {
    return resolveList(approvalTodo ? listStockCheckApprovalTodos(query) : listStockCheck(query))
  }

  if (featureKey === "salesReturn") {
    return resolveList(listSalesReturn(query))
  }

  if (featureKey === "purchaseReturn") {
    if (options.selectedDeptType !== "WAREHOUSE") {
      return Promise.resolve({ rows: [], total: 0 })
    }
    return resolveList(listPurchaseReturn(query))
  }

  if (featureKey === "outbound") {
    return resolveList(listDeliveryNotice(query))
  }

  if (featureKey === "transfer") {
    return resolveList(listTransferProcessing(normalizeTransferListQuery(query, options)))
  }

  if (featureKey === "transferApproval") {
    return resolveList(listTransferApprovalTodos(query))
  }

  if (featureKey === "transferRecords") {
    return resolveList(listTransferRecords(query))
  }

  if (featureKey === "oaPurchase") {
    return resolveList(listMyPurchases(query))
  }

  if (featureKey === "fixedAssetRepair") {
    return resolveList(listFixedAssetRepairs(query))
  }

  if (featureKey === "salary") {
    const request = auth.hasPermi("oa:salary:list") ? listAllSalary : listMySalary
    return resolveList(request(normalizeSalaryQuery(query)))
  }

  if (featureKey === "notice") {
    return fetchNoticeRows(query)
  }

  if (featureKey === "profile") {
    return fetchProfileRows()
  }

  if (featureKey === "systemUser") {
    return resolveList(listUser(query))
  }

  if (featureKey === "systemRole") {
    return resolveList(listRole(query))
  }

  if (featureKey === "systemPost") {
    return resolveList(listPost(query))
  }

  if (featureKey === "systemDept") {
    return fetchDeptRows(query)
  }

  if (featureKey === "systemMenu") {
    return fetchMenuRows(query)
  }

  if (featureKey === "userShop") {
    return fetchUserShopRows(query)
  }

  if (featureKey === "systemConfig") {
    return resolveList(listConfig(query))
  }

  if (featureKey === "systemDictType") {
    return resolveList(listType(query))
  }

  if (featureKey === "systemDictData") {
    return resolveList(listData(query))
  }

  if (featureKey === "systemLogininfor") {
    return resolveList(listLogininfor(query))
  }

  if (featureKey === "systemOperlog") {
    return resolveList(listOperlog(query))
  }

  if (featureKey === "monitorJob") {
    return resolveList(listJob(query))
  }

  if (featureKey === "monitorJobLog") {
    return resolveList(listJobLog(query))
  }

  if (featureKey === "monitorOnline") {
    return resolveList(listOnline(query))
  }

  if (featureKey === "salaryScheme") {
    return resolveList(listSalaryScheme(query))
  }

  if (featureKey === "transferRules") {
    return resolveList(listTransferApprovalRule(query))
  }

  return Promise.resolve({ rows: [], total: 0 })
}

export function fetchMobileFeatureDetail(featureKey, item, options = {}) {
  const row = item && (item._raw || item.raw || item)
  const id = resolveDetailId(featureKey, row)
  const approvalSeed = Object.assign({}, row || {})
  if (options.approvalTaskId) approvalSeed.approvalTaskId = options.approvalTaskId
  if (options.approvalInstanceId) approvalSeed.approvalInstanceId = options.approvalInstanceId

  // A specialist's list-to-action flow must not silently require general query permission.
  // Only draft editing and receiving hydrate full task-specific data; ID-only actions use the list header.
  if (["purchase", "purchaseReturn", "salesReturn"].includes(featureKey) && id !== undefined &&
      Array.isArray(options.inventoryPermissions)) {
    const permissions = options.inventoryPermissions
    const has = suffix => permissions.includes("*:*:*") || permissions.includes("inv:" + featureKey + ":" + suffix)
    if (!has("query")) {
      if (row.status === "draft" && has("add")) {
        const readDraft = { purchase: getPurchaseDraft, purchaseReturn: getPurchaseReturnDraft, salesReturn: getSalesReturnDraft }[featureKey]
        return resolveDetail(readDraft(id))
      }
      if (featureKey === "purchase" && row.status === "submitted" && has("receive")) {
        return resolveDetail(getPurchaseReceiveContext(id))
      }
      const actions = featureKey === "purchase" ? ["submit", "receive", "qc", "remove"] : ["submit", "confirm", "remove"]
      if (actions.some(has)) return Promise.resolve(Object.assign({}, row, { details: undefined, _specialistSummaryOnly: true }))
      return Promise.reject(new Error("当前岗位无权读取此单据详情"))
    }
  }

  if (featureKey === "sales" && id !== undefined) {
    return resolveDetail(getSalesDetail(id))
  }

  if (featureKey === "purchase" && id !== undefined) {
    return resolveDetail(getPurchaseDetail(id))
  }

  if (featureKey === "stock" && id !== undefined) {
    return resolveDetail(getStock(id)).then(detail => hydrateStockProducts([detail]).then(rows => rows[0] || detail))
  }

  if (featureKey === "replenishment" && id !== undefined) {
    return resolveTransferDetailWithTrack(id, approvalSeed)
  }

  if (featureKey === "product" && id !== undefined) {
    return resolveDetail(getProduct(id))
  }

  if (featureKey === "oe" && id !== undefined) {
    return resolveDetail(getOe(id))
  }

  if (featureKey === "gift" && id !== undefined) {
    return resolveDetail(getGift(id))
  }

  if (featureKey === "category" && id !== undefined) {
    return resolveDetail(getCategory(id))
  }

  if (featureKey === "customer" && id !== undefined) {
    return resolveDetail(getCustomerServiceCard(id))
  }

  if (featureKey === "supplier" && id !== undefined) {
    return Promise.all([
      resolveDetail(getSupplier(id)),
      getSupplierProducts(id).then(res => res.data || []).catch(() => [])
    ]).then(([supplier, products]) => Object.assign({}, supplier, { products }))
  }

  if (featureKey === "stockCheck" && id !== undefined) {
    return resolveDetail(getStockCheck(id)).then(detail => {
      const resolved = mergeDetailWithApprovalSeed(approvalSeed, detail)
      return resolveInventoryApprovalDetail(resolved, () =>
        getStockCheckApprovalTrack(id).then(res => res.data || []).catch(() => [])
      )
    })
  }

  if (featureKey === "salesReturn" && id !== undefined) {
    return resolveDetail(getSalesReturn(id))
  }

  if (featureKey === "purchaseReturn" && id !== undefined) {
    return resolveDetail(getPurchaseReturn(id))
  }

  if (featureKey === "outbound" && id !== undefined) {
    return resolveDetail(getDeliveryNotice(id))
  }

  if ((featureKey === "transfer" || featureKey === "transferApproval" || featureKey === "transferRecords") && id !== undefined) {
    return resolveTransferDetailWithTrack(id, approvalSeed)
  }

  if (featureKey === "oaPurchase" && id !== undefined) {
    return resolveDetail(getOaPurchaseDetail(id))
  }

  if (featureKey === "fixedAssetRepair" && id !== undefined) {
    return resolveDetail(getFixedAssetRepair(id))
  }

  if (featureKey === "salary" && id !== undefined) {
    return resolveDetail(getSalary(id))
  }

  if (featureKey === "notice" && id !== undefined) {
    return resolveDetail(getNotice(id))
  }

  if (featureKey === "profile") {
    return getUserProfile().then(response => {
      const data = response && response.data ? response.data : {}
      const user = data.user || data
      return Object.assign({}, user, {
        roleGroup: data.roleGroup,
        postGroup: data.postGroup
      })
    })
  }

  if (featureKey === "systemUser" && id !== undefined) {
    return resolveDetail(getUser(id)).then(detail => {
      const data = detail || {}
      return data.user || data
    })
  }

  if (featureKey === "systemRole" && id !== undefined) {
    return resolveDetail(getRole(id))
  }

  if (featureKey === "systemPost" && id !== undefined) {
    return resolveDetail(getPost(id))
  }

  if (featureKey === "systemDept" && id !== undefined) {
    return resolveDetail(getDept(id))
  }

  if (featureKey === "systemMenu" && id !== undefined) {
    return resolveDetail(getMenu(id))
  }

  if (featureKey === "userShop" && id !== undefined) {
    return fetchUserShopDetail(id)
  }

  if (featureKey === "systemConfig" && id !== undefined) {
    return resolveDetail(getConfig(id))
  }

  if (featureKey === "systemDictType" && id !== undefined) {
    return resolveDetail(getType(id))
  }

  if (featureKey === "systemDictData" && id !== undefined) {
    return resolveDetail(getData(id))
  }

  if (featureKey === "monitorJob" && id !== undefined) {
    return resolveDetail(getJob(id))
  }

  if (featureKey === "monitorJobLog" && id !== undefined) {
    return resolveDetail(getJobLog(id))
  }

  if (featureKey === "salaryScheme" && id !== undefined) {
    return Promise.all([
      resolveDetail(getSalaryScheme(id)),
      listSalaryItems(id).then(res => res.data || res.rows || []).catch(() => [])
    ]).then(([scheme, items]) => Object.assign({}, scheme, { items }))
  }

  if (featureKey === "transferRules" && id !== undefined) {
    return resolveDetail(getTransferApprovalRule(id))
  }

  return Promise.resolve(row || {})
}

const resolveDetail = (promise) => {
  return promise.then(response => response && response.data ? response.data : {})
}

const resolveTransferDetailWithTrack = (id, seed) => {
  return resolveDetail(getTransferDetail(id)).then(detail => {
    const resolved = mergeDetailWithApprovalSeed(seed, detail)
    return resolveInventoryApprovalDetail(resolved, () =>
      getTransferApprovalTrack(id)
        .then(response => response && response.data ? response.data : null)
        .catch(() => ({ loadError: true, rounds: [] }))
    )
  })
}

const resolveInventoryApprovalDetail = (detail, legacyLoader) => {
  const source = detail || {}
  const native = String(source.approvalEngine || "").toUpperCase() === "NATIVE" || Boolean(source.approvalTaskId)
  if (!native) {
    return Promise.resolve().then(legacyLoader).then(approvalTrack => Object.assign({}, source, { approvalTrack }))
  }
  if (!source.approvalInstanceId) {
    return Promise.resolve(Object.assign({}, source, {
      unifiedApprovalDetail: null,
      unifiedApprovalLoadError: true
    }))
  }
  return getApprovalInstance(source.approvalInstanceId).then(response => Object.assign({}, source, {
    unifiedApprovalDetail: unwrapData(response),
    unifiedApprovalLoadError: false
  })).catch(() => Object.assign({}, source, {
    unifiedApprovalDetail: null,
    unifiedApprovalLoadError: true
  }))
}

const mergeDetailWithApprovalSeed = (seed, detail) => {
  const source = seed || {}
  const merged = Object.assign({}, source, detail || {})
  if (source.approvalTaskId) merged.approvalTaskId = source.approvalTaskId
  if (source.approvalInstanceId) merged.approvalInstanceId = source.approvalInstanceId
  return merged
}

const resolveDetailId = (featureKey, row) => {
  const idKeys = {
    sales: ["orderId", "salesOrderId", "id"],
    purchase: ["orderId", "purchaseId", "id"],
    stock: ["stockId", "id"],
    replenishment: ["transferId", "id"],
    product: ["productId", "id"],
    oe: ["oeItemId", "id"],
    gift: ["giftId", "id"],
    category: ["categoryId", "id"],
    customer: ["customerId", "id"],
    supplier: ["supplierId", "id"],
    stockCheck: ["checkId", "stockCheckId", "id"],
    salesReturn: ["returnId", "salesReturnId", "id"],
    purchaseReturn: ["returnId", "purchaseReturnId", "id"],
    outbound: ["noticeId", "deliveryNoticeId", "id"],
    transfer: ["transferId", "id"],
    transferApproval: ["transferId", "id"],
    transferRecords: ["transferId", "id"],
    oaPurchase: ["purchaseId", "id"],
    fixedAssetRepair: ["repairId", "id"],
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

function normalizeBoolean(value) {
  if (value === true || value === "true" || value === 1 || value === "1") return true
  if (value === false || value === "false" || value === 0 || value === "0") return false
  return Boolean(value)
}

function currentSalaryMonth() {
  const now = new Date()
  return now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0")
}

function firstValue(source, keys) {
  if (!source || typeof source !== "object") return undefined
  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]
    if (hasValue(value)) return value
  }
  return undefined
}
