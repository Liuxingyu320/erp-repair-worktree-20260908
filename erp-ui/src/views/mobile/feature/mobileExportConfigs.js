const MOBILE_EXPORT_CONFIG = {
  sales: { url: "inventory/sales/export", name: "销售单数据", contextScoped: true, permissions: ["inv:sales:export"] },
  purchase: { url: "inventory/purchase/export", name: "采购单数据", contextScoped: true, permissions: ["inv:purchase:export"] },
  stock: { url: "inventory/stock/export", name: "库存数据", contextScoped: true, permissions: ["inv:stock:export"] },
  stockLog: { url: "inventory/stock/log/export", name: "库存变动日志", contextScoped: true, permissions: ["inv:stock:export"] },
  product: { url: "inventory/product/export", name: "商品数据", permissions: ["inv:product:export"] },
  oe: { url: "inventory/oe/export", name: "OE资料", permissions: ["inv:oe:export"] },
  gift: { url: "inventory/gift/export", name: "礼盒资料", permissions: ["inv:gift:export"] },
  customer: { url: "inventory/customer/export", name: "客户数据", permissions: ["inv:customer:export"] },
  supplier: { url: "inventory/supplier/export", name: "供应商数据", permissions: ["inv:supplier:export"] },
  stockCheck: { url: "inventory/stockCheck/export", name: "库存盘点数据", contextScoped: true, permissions: ["inv:stockCheck:export"] },
  salesReturn: { url: "inventory/salesReturn/export", name: "销售退货数据", contextScoped: true, permissions: ["inv:salesReturn:export"] },
  purchaseReturn: { url: "inventory/purchaseReturn/export", name: "采购退货数据", contextScoped: true, permissions: ["inv:purchaseReturn:export"] },
  outbound: { url: "inventory/deliveryNotice/export", name: "发货通知数据", contextScoped: true, permissions: ["inv:deliveryNotice:export"] },
  transfer: { url: "inventory/transfer/export", name: "调拨处理中数据", contextScoped: true, permissions: ["inv:transfer:export"] },
  transferRecords: { url: "inventory/transfer/records/export", name: "调拨记录数据", contextScoped: true, permissions: ["inv:transfer:records:export"] },
  oaPurchase: { url: "oa/purchase/export/my", name: "我的采购申请", permissions: ["oa:purchase:export"] },
  salary: { url: "oa/salary/export", name: "工资记录", permissions: ["oa:salary:export"] },
  systemUser: { url: "system/user/export", name: "用户数据", permissions: ["system:user:export"] },
  systemRole: { url: "system/role/export", name: "角色数据", permissions: ["system:role:export"] },
  systemPost: { url: "system/post/export", name: "岗位数据", permissions: ["system:post:export"] },
  systemConfig: { url: "system/config/export", name: "参数数据", permissions: ["system:config:export"] },
  systemDictType: { url: "system/dict/type/export", name: "字典类型", permissions: ["system:dict:export"] },
  systemDictData: { url: "system/dict/data/export", name: "字典项数据", permissions: ["system:dict:export"] },
  systemLogininfor: { url: "system/logininfor/export", name: "登录日志", permissions: ["system:logininfor:export"] },
  systemOperlog: { url: "system/operlog/export", name: "操作日志", permissions: ["system:operlog:export"] },
  monitorJob: { url: "schedule/job/export", name: "定时任务", permissions: ["monitor:job:export"] },
  monitorJobLog: { url: "schedule/job/log/export", name: "调度日志", permissions: ["monitor:job:export"] }
}

module.exports = {
  MOBILE_EXPORT_CONFIG
}
