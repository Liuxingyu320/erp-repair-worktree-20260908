const MOBILE_SEARCH_CONFIG = {
  sales: { fields: [{ key: "orderNo", label: "单号" }, { key: "orderTitle", label: "标题" }] },
  purchase: { fields: [{ key: "orderNo", label: "单号" }, { key: "orderTitle", label: "标题" }] },
  stock: { fields: [{ key: "productKeyword", label: "商品", placeholder: "输入商品名称/编码搜索" }] },
  product: { fields: [{ key: "productName", label: "商品名称" }, { key: "productCode", label: "商品编码" }, { key: "supplierName", label: "供应商" }] },
  oe: { fields: [{ key: "oeItemName", label: "OE名称" }, { key: "oeItemCode", label: "OE编码" }, { key: "supplierName", label: "供应商" }] },
  gift: { fields: [{ key: "giftName", label: "礼盒名称" }, { key: "giftCode", label: "礼盒编码" }, { key: "grade", label: "等级" }] },
  category: { fields: [{ key: "keyword", label: "分类", placeholder: "输入分类名称/编码/路径搜索" }] },
  customer: { fields: [{ key: "keyword", label: "客户", placeholder: "输入姓名、编码、电话或喜好搜索" }] },
  supplier: { fields: [{ key: "supplierName", label: "供应商名称" }, { key: "supplierCode", label: "供应商编码" }, { key: "contactPhone", label: "联系电话" }] },
  stockLog: { fields: [{ key: "productKeyword", label: "商品", placeholder: "输入商品名称/编码搜索" }, { key: "businessNo", label: "业务单号" }] },
  replenishment: { fields: [{ key: "orderNo", label: "补货单号" }] },
  salesReturn: { fields: [{ key: "returnNo", label: "退货单号" }, { key: "salesOrderNo", label: "销售单号" }, { key: "customerName", label: "客户" }] },
  purchaseReturn: { fields: [{ key: "returnNo", label: "退货单号" }, { key: "purchaseOrderNo", label: "采购单号" }, { key: "supplierName", label: "供应商" }] },
  outbound: { fields: [{ key: "noticeNo", label: "通知单号" }, { key: "salesOrderNo", label: "销售单号" }, { key: "customerName", label: "客户" }] },
  stockCheck: { fields: [{ key: "checkNo", label: "盘点单号" }] },
  transfer: { fields: [{ key: "orderNo", label: "调拨单号" }] },
  transferRecords: { fields: [{ key: "orderNo", label: "调拨单号" }] },
  oaPurchase: { fields: [{ key: "title", label: "标题" }] },
  attendance: { fields: [{ key: "workDate", label: "日期", placeholder: "输入日期，如 2026-06-10" }] },
  salary: { fields: [{ key: "salaryMonth", label: "工资月份", placeholder: "输入月份，如 2026-06" }] },
  notice: { fields: [{ key: "noticeTitle", label: "公告标题" }, { key: "createBy", label: "发布人" }, { key: "noticeType", label: "类型", placeholder: "输入 1 通知或 2 公告" }] },
  profile: { fields: [{ key: "userName", label: "账号" }, { key: "nickName", label: "姓名" }, { key: "phonenumber", label: "手机" }] },
  systemUser: { fields: [{ key: "userName", label: "账号" }, { key: "phonenumber", label: "手机" }, { key: "deptName", label: "组织" }] },
  systemRole: { fields: [{ key: "roleName", label: "角色名称" }, { key: "roleKey", label: "权限字符" }] },
  systemPost: { fields: [{ key: "postName", label: "岗位名称" }, { key: "postCode", label: "岗位编码" }] },
  systemDept: { fields: [{ key: "deptName", label: "组织名称" }, { key: "leader", label: "负责人" }, { key: "phone", label: "电话" }] },
  systemMenu: { fields: [{ key: "menuName", label: "菜单名称" }, { key: "perms", label: "权限标识" }] },
  userShop: { fields: [{ key: "userName", label: "账号" }, { key: "phonenumber", label: "手机" }] },
  systemConfig: { fields: [{ key: "configName", label: "参数名称" }, { key: "configKey", label: "参数键名" }] },
  systemDictType: { fields: [{ key: "dictName", label: "字典名称" }, { key: "dictType", label: "字典类型" }] },
  systemDictData: { fields: [{ key: "dictLabel", label: "字典标签" }, { key: "dictType", label: "字典类型" }, { key: "dictValue", label: "字典键值" }] },
  systemLogininfor: { fields: [{ key: "userName", label: "登录账号" }, { key: "ipaddr", label: "登录地址" }] },
  systemOperlog: { fields: [{ key: "title", label: "系统模块" }, { key: "operName", label: "操作人员" }, { key: "operIp", label: "操作地址" }] },
  monitorJob: { fields: [{ key: "jobName", label: "任务名称" }, { key: "jobGroup", label: "任务组" }] },
  monitorJobLog: { fields: [{ key: "jobName", label: "任务名称" }, { key: "jobGroup", label: "任务组" }] },
  monitorOnline: { fields: [{ key: "userName", label: "登录账号" }, { key: "ipaddr", label: "登录地址" }] },
  salaryScheme: { fields: [{ key: "schemeName", label: "方案名称" }, { key: "roleName", label: "适用角色" }] },
  transferRules: { fields: [{ key: "ruleName", label: "规则名称" }, { key: "sourceDeptName", label: "调出组织" }, { key: "targetDeptName", label: "调入组织" }] }
}

const STOCK_STATUS_FILTERS = {
  stock: [
    { label: "全部状态", value: "" },
    { label: "正常", value: "normal" },
    { label: "低库存", value: "low" },
    { label: "缺货", value: "empty" }
  ]
}

function getMobileSearchConfig(featureKey) {
  return MOBILE_SEARCH_CONFIG[featureKey] || null
}

function getStockStatusFilters(featureKey) {
  return STOCK_STATUS_FILTERS[featureKey] || []
}

module.exports = {
  MOBILE_SEARCH_CONFIG,
  STOCK_STATUS_FILTERS,
  getMobileSearchConfig,
  getStockStatusFilters
}
