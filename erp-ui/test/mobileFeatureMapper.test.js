const assert = require("assert")

const {
  mapMobileFeatureRows,
  mapMobileMineRows
} = require("../src/views/mobile/feature/featureMapper")

assert.deepStrictEqual(
  mapMobileFeatureRows("sales", [{
    orderNo: "SO20260607001",
    orderTitle: "茶席销售单",
    customerName: "云岫茶室",
    totalAmount: 1280,
    status: "submitted"
  }]),
  [{
    title: "茶席销售单",
    code: "SO20260607001",
    detail: "云岫茶室 · ¥1280.00",
    status: "待发货"
  }],
  "sales rows should map backend order fields to mobile cards"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("purchase", [{
    orderNo: "PO20260607002",
    supplierName: "春山茶业",
    totalAmount: "560.5",
    status: "received"
  }]),
  [{
    title: "春山茶业",
    code: "PO20260607002",
    detail: "春山茶业 · ¥560.50",
    status: "已完成"
  }],
  "purchase rows should map supplier and status labels"
)

assert.strictEqual(
  mapMobileFeatureRows("purchase", [{
    orderNo: "PO-QC-001",
    supplierName: "春山茶业",
    status: "submitted",
    qcStatus: "pending"
  }])[0].status,
  "待质检",
  "submitted purchase rows with pending QC must not be mislabeled as pending receipt"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("stock", [{
    productId: 12,
    product: { productName: "明前龙井", productCode: "TEA-001", unit: "盒" },
    availableQuantity: 6,
    warehouseName: "总仓"
  }]),
  [{
    title: "明前龙井",
    code: "TEA-001",
    detail: "总仓 · 可用 6 盒",
    status: "低库存"
  }],
  "stock rows should use hydrated product data when list rows only contain product ids"
)

{
  const stockRows = [{
    stockId: 9,
    productId: 12,
    productName: "大红袍",
    productCode: "WLC-000002",
    categoryFullPath: "乌龙茶 / 武夷岩茶",
    grade: "特级",
    spec: "150g/罐",
    unit: "罐",
    shopDeptName: "主仓库",
    warehouseName: "主仓库",
    batchNo: "B20260702",
    expiryDate: "2027-07-02",
    serialNo: "SN-001",
    locationCode: "A-01",
    locationName: "一号货架",
    currentQuantity: 1000,
    lockedQuantity: 2,
    availableQuantity: 998,
    costPrice: 42.5,
    totalCost: 42500,
    safetyStockMin: 10,
    lastInTime: "2026-07-02 10:00:00",
    lastOutTime: "2026-07-02 18:00:00"
  }]
  const stockItem = mapMobileFeatureRows("stock", stockRows)[0]

  assert.strictEqual(
    stockItem.detail,
    "主仓库 · 可用 998 罐",
    "mobile stock rows should hide reference cost unless the role can view costs"
  )

  assert.deepStrictEqual(
    stockItem._detailFields,
    [
      { label: "商品编码", value: "WLC-000002" },
      { label: "商品分类", value: "乌龙茶 / 武夷岩茶" },
      { label: "等级", value: "特级" },
      { label: "规格", value: "150g/罐" },
      { label: "库存组织", value: "主仓库" },
      { label: "库存仓库", value: "主仓库" },
      { label: "批次号", value: "B20260702" },
      { label: "效期", value: "2027-07-02" },
      { label: "序列号", value: "SN-001" },
      { label: "库位", value: "A-01 / 一号货架" },
      { label: "当前库存", value: "1000 罐" },
      { label: "锁定库存", value: "2 罐" },
      { label: "可用库存", value: "998 罐" },
      { label: "预警库存", value: "10 罐" },
      { label: "最后入库", value: "2026-07-02 10:00" },
      { label: "最后出库", value: "2026-07-02 18:00" }
    ],
    "mobile stock detail sheets should hide cost metadata by default"
  )

  const costVisibleStockItem = mapMobileFeatureRows("stock", stockRows, { permissions: ["inv:cost:view"] })[0]
  assert.strictEqual(
    costVisibleStockItem.detail,
    "主仓库 · 可用 998 罐 · 参考成本价 ¥42.50",
    "mobile stock rows should show reference cost for roles with inv:cost:view"
  )
  assert.ok(
    costVisibleStockItem._detailFields.some(field => field.label === "库存总成本" && field.value === "¥42500.00"),
    "mobile stock detail sheets should show stock total cost for roles with inv:cost:view"
  )
}

{
  const warehouseProduct = mapMobileFeatureRows("product", [{
    productCode: "DHP0103001",
    productName: "大红袍",
    categoryName: "乌龙茶",
    supplierName: "大齐茶业",
    salePrice500g: 88,
    costPrice: 42.5,
    status: "0"
  }], { selectedDeptType: "WAREHOUSE", permissions: ["inv:cost:view"] })[0]

  assert.strictEqual(
    warehouseProduct.detail,
    "乌龙茶 · 大齐茶业 · 参考成本价 ¥42.50",
    "warehouse product rows should show supplier and reference cost, not sale price"
  )
  assert.deepStrictEqual(
    warehouseProduct._detailFields.filter(field => ["供应商", "参考成本价"].indexOf(field.label) > -1),
    [
      { label: "供应商", value: "大齐茶业" },
      { label: "参考成本价", value: "¥42.50" }
    ],
    "warehouse product detail sheets should label the cost value as reference cost"
  )
}

{
  const storeProduct = mapMobileFeatureRows("product", [{
    productCode: "DHP0103001",
    productName: "大红袍",
    categoryName: "乌龙茶",
    supplierName: "大齐茶业",
    salePrice500g: 88,
    costPrice: 42.5,
    status: "0"
  }], { selectedDeptType: "STORE" })[0]

  assert.strictEqual(
    storeProduct.detail,
    "-",
    "store product rows should hide supplier context and reference cost without cost permission"
  )
  assert.ok(
    !storeProduct._detailFields.some(field => field.label === "供应商"),
    "store product detail sheets should hide supplier fields"
  )
  assert.ok(
    !storeProduct._detailFields.some(field => field.label === "参考成本价"),
    "store product detail sheets should hide reference cost without cost permission"
  )
}

assert.deepStrictEqual(
  mapMobileFeatureRows("stockCheck", [{
    checkNo: "CK20260607003",
    warehouseName: "A区仓",
    checkDate: "2026-06-07",
    status: "checking"
  }]),
  [{
    title: "CK20260607003",
    code: "CK20260607003",
    detail: "A区仓 · 2026-06-07",
    status: "待确认"
  }],
  "stock check rows should map check number and status labels"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("transferApproval", [{
    transferId: 91,
    taskId: 901,
    orderNo: "TF20260703001",
    fromDeptName: "中央仓",
    toDeptName: "湖滨店",
    nodeName: "运营经理",
    submittedTime: "2026-07-03 15:20:00",
    status: "submitted"
  }]),
  [{
    title: "中央仓 → 湖滨店",
    code: "TF20260703001",
    detail: "运营经理 · 2026-07-03 15:20",
    status: "待审批"
  }],
  "transfer approval rows should map candidate task metadata to mobile cards"
)

{
  const approval = mapMobileFeatureRows("transferApproval", [{
    transferId: 92,
    orderNo: "TF-REVIEW-TOP",
    fromDeptName: "中央仓",
    toDeptName: "北京柏悦",
    totalQuantity: 2,
    totalAmount: 10,
    status: "submitted",
    approvalTrack: {
      currentNode: {
        nodeName: "运营经理",
        postName: "运营岗",
        state: "current"
      }
    }
  }], { permissions: ["inv:cost:view"] })[0]

  assert.deepStrictEqual(approval._reviewFields, [
    { label: "调出", value: "中央仓" },
    { label: "调入", value: "北京柏悦" },
    { label: "申请数量", value: "2" },
    { label: "参考总价", value: "¥10.00" },
    { label: "审批节点", value: "运营经理" }
  ], "transfer approval confirmation should expose a structured, permission-filtered review summary")
  assert.ok(!Object.keys(approval).includes("_reviewFields"),
    "review fields should remain non-enumerable mobile metadata")
  assert.ok(
    approval._detailFields.some(field => field.label === "审批节点" && field.value === "运营经理"),
    "transfer approval detail should resolve the current node from the real approval-track response shape"
  )
}

{
  const approval = mapMobileFeatureRows("transferApproval", [{
    transferId: 94,
    orderNo: "TF-REVIEW-ROUND-NODE",
    fromDeptName: "主仓库",
    toDeptName: "北京柏悦",
    totalQuantity: 1,
    status: "submitted",
    approvalTrack: {
      rounds: [{
        roundNo: 1,
        nodes: [{ nodeName: "区域负责人", state: "current" }]
      }]
    }
  }])[0]

  assert.ok(
    approval._reviewFields.some(field => field.label === "审批节点" && field.value === "区域负责人"),
    "transfer approval confirmation should fall back to the current node in approval-track rounds"
  )
}

{
  const approval = mapMobileFeatureRows("transferApproval", [{
    transferId: 93,
    orderNo: "TF-REVIEW-DETAILS",
    fromWarehouseName: "二号仓",
    targetDeptName: "湖滨店",
    currentNodeName: "区域负责人",
    status: "submitted",
    details: [
      { detailId: 1, quantity: 1.5, costPrice: 8 },
      { detailId: 2, quantity: 2.25, costPrice: 4 }
    ]
  }])[0]

  assert.deepStrictEqual(
    approval._reviewFields,
    [
      { label: "调出", value: "二号仓" },
      { label: "调入", value: "湖滨店" },
      { label: "申请数量", value: "3.75" },
      { label: "参考总价", value: "¥21.00" },
      { label: "审批节点", value: "区域负责人" }
    ],
    "transfer review should derive both quantity and reference total from detail rows"
  )
}

{
  const isoTime = mapMobileFeatureRows("transferApproval", [{
    orderNo: "TF-ISO",
    nodeName: "运营经理",
    submittedTime: "2026-07-03T07:20:30.000Z",
    status: "submitted"
  }])[0].detail
  assert.match(isoTime, /运营经理 · \d{4}-\d{2}-\d{2} \d{2}:\d{2}$/,
    "mobile business times must render as local YYYY-MM-DD HH:mm text")
  assert.ok(!isoTime.includes("T") && !isoTime.includes("Z"),
    "mobile business cards must not expose raw ISO timestamps")
}

{
  const mapped = mapMobileFeatureRows("transfer", [{
    transferId: 91,
    orderNo: "TF-91",
    fromDeptName: "中央仓",
    toDeptName: "湖滨店",
    status: "submitted",
    approvalSummary: {
      summaryText: "待三级负责人审批",
      currentCandidateDisplayNames: ["张三", "李四"]
    },
    approvalTrack: {
      state: "in_progress",
      rounds: [{
        roundNo: 1,
        status: "in_progress",
        nodes: [{
          nodeOrder: 1,
          nodeName: "三级负责人",
          state: "current",
          candidateDisplayNames: ["张三", "李四"],
          approvalModeText: "任一人通过即可"
        }]
      }]
    },
    details: [{ productName: "茶叶", quantity: 2 }]
  }])[0]

  assert.ok(mapped.detail.includes("待三级负责人审批"), "transfer card should include approval summary")
  assert.ok(mapped.detail.includes("张三、李四"), "transfer card should include at most two current candidates")
  assert.strictEqual(mapped._detailSections[0].title, "审批进度")
  assert.ok(mapped._detailSections[0].rows[0].meta.includes("张三、李四"))
  assert.strictEqual(mapped._detailSections[0].rows[0].value, "当前节点")
  assert.strictEqual(mapped._detailSections[1].title, "调拨明细", "approval progress should precede line items")
}

{
  const mapped = mapMobileFeatureRows("transfer", [{
    transferId: 911,
    orderNo: "TF-911",
    fromDeptId: 201,
    fromDeptName: "南山店",
    fromWarehouseId: 901,
    fromWarehouseName: "中央仓",
    toDeptId: 202,
    toDeptName: "福田店",
    transferType: "cross_store",
    sourceBusinessType: "sales_delivery",
    sourceBusinessId: 101,
    status: "delivered"
  }])[0]

  assert.ok(mapped.detail.includes("跨店销售在途"),
    "automatic sales transfers should be distinguishable from manual cross-store transfers in the list")
  assert.ok(mapped._detailFields.some(field => field.label === "业务来源" && field.value === "南山店"))
  assert.ok(mapped._detailFields.some(field => field.label === "实际发货仓库" && field.value === "中央仓"))
}

{
  const mapped = mapMobileFeatureRows("transfer", [{
    transferId: 912,
    orderNo: "TF-912",
    fromDeptId: 201,
    fromDeptName: "南山店",
    toDeptId: 202,
    toDeptName: "福田店",
    transferType: "cross_store",
    sourceConfirmStatus: "PENDING",
    sourceConfirmedBy: "",
    status: "approved"
  }])[0]

  assert.strictEqual(mapped.status, "待调出店确认",
    "approved manual cross-store transfers must not be mislabeled as ready to ship")
  assert.ok(mapped._detailFields.some(field =>
    field.label === "调出店确认" && field.value === "待调出店确认"),
  "mobile transfer details should expose the explicit source-store confirmation state")
}

{
  const mapped = mapMobileFeatureRows("transfer", [{
    transferId: 913,
    orderNo: "TF-913",
    fromDeptId: 201,
    fromDeptName: "南山店",
    toDeptId: 202,
    toDeptName: "福田店",
    transferType: "cross_store",
    sourceConfirmStatus: "RESELECT_REQUIRED",
    reselectionFromTransferId: 912,
    status: "draft"
  }])[0]

  assert.strictEqual(mapped.status, "待重选调出店",
    "returned remainder drafts should tell the target store to select another source")
  assert.ok(mapped._detailFields.some(field =>
    field.label === "余量来源单" && field.value === "#912"),
  "mobile transfer details should retain the parent transfer linkage for returned quantities")
}

{
  const mapped = mapMobileFeatureRows("transfer", [{
    transferId: 911,
    orderNo: "TF-911",
    fromDeptId: 201,
    fromDeptName: "南山店",
    toDeptId: 301,
    toDeptName: "中央仓",
    transferType: "store_return",
    status: "draft"
  }])[0]

  assert.ok(mapped._detailFields.some(field => field.label === "目标仓库" && field.value === "中央仓"),
    "store-return details should label the destination as a warehouse")
}

{
  const mapped = mapMobileFeatureRows("transferRecords", [{
    transferId: 92,
    orderNo: "TF-92",
    status: "rejected",
    approvalTrack: {
      state: "rejected",
      rounds: [
        {
          roundNo: 1,
          status: "rejected",
          nodes: [{ nodeOrder: 1, nodeName: "店长", state: "rejected", actualApproverDisplayName: "王店长", comment: "请补充备注" }]
        },
        {
          roundNo: 2,
          status: "approved",
          nodes: [{ nodeOrder: 1, nodeName: "店长", state: "approved", actualApproverDisplayName: "李店长" }]
        }
      ]
    }
  }])[0]

  assert.deepStrictEqual(
    mapped._detailSections.map(section => section.title),
    ["审批进度", "历史审批 · 第 1 轮"],
    "latest approval round should appear before older approval history"
  )
  assert.ok(mapped._detailSections[1].rows[0].extra.includes("意见：请补充备注"))
}

assert.deepStrictEqual(
  mapMobileFeatureRows("salesReturn", [{
    returnNo: "SR20260607004",
    customerName: "云岫茶室",
    totalAmount: 268,
    status: "submitted"
  }]),
  [{
    title: "云岫茶室",
    code: "SR20260607004",
    detail: "云岫茶室 · ¥268.00",
    status: "待确认"
  }],
  "sales return rows should use the dedicated return order API shape"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("purchaseReturn", [{
    returnNo: "PR20260607005",
    supplierName: "春山茶业",
    totalAmount: 188.5,
    returnReason: "质检不合格",
    status: "submitted"
  }]),
  [{
    title: "春山茶业",
    code: "PR20260607005",
    detail: "春山茶业 · ¥188.50",
    status: "待确认"
  }],
  "purchase return rows should use the dedicated supplier return order API shape"
)

const purchaseReturnDetailRow = mapMobileFeatureRows("purchaseReturn", [{
  returnNo: "PR20260607005",
  supplierName: "春山茶业",
  totalAmount: 188.5,
  returnReason: "质检不合格",
  status: "submitted"
}])[0]

assert.deepStrictEqual(
  purchaseReturnDetailRow._detailFields,
  [
    { label: "供应商", value: "春山茶业" },
    { label: "金额", value: "¥188.50" },
    { label: "原因", value: "质检不合格" }
  ],
  "mobile detail sheets should expose business fields beyond the card summary"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("outbound", [{
    noticeNo: "DN20260607006",
    customerName: "青炉茶社",
    itemCount: 3,
    status: "pending"
  }]),
  [{
    title: "青炉茶社",
    code: "DN20260607006",
    detail: "青炉茶社 · 3 件商品",
    status: "待发货"
  }],
  "outbound rows should map delivery notice fields instead of sales order rows"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("transfer", [{
    transferNo: "TF20260607007",
    fromDeptName: "中央仓",
    toDeptName: "湖畔店",
    status: "approved"
  }]),
  [{
    title: "中央仓 → 湖畔店",
    code: "TF20260607007",
    detail: "中央仓 · 湖畔店",
    status: "待出库"
  }],
  "transfer rows should map transfer source and target organizations"
)

{
  const replenishmentItem = mapMobileFeatureRows("replenishment", [{
    transferId: 72,
    orderNo: "TF20260629001",
    fromDeptName: "中央仓",
    toDeptName: "湖畔店",
    totalQuantity: 3,
    status: "submitted"
  }])[0]

  assert.deepStrictEqual(
    replenishmentItem,
    {
      title: "中央仓 → 湖畔店",
      code: "TF20260629001",
      detail: "中央仓 · 湖畔店",
      status: "待审批"
    },
    "mobile replenishment rows should display submitted transfer requests like the desktop transfer page"
  )

  assert.strictEqual(
    replenishmentItem._id,
    72,
    "mobile replenishment actions should resolve transferId, not stockId"
  )
}

{
  const replenishmentRows = [{
    transferId: 73,
    orderNo: "TF20260701001",
    fromDeptName: "主仓库",
    toDeptName: "测试门店",
    totalQuantity: 2,
    status: "draft",
    details: [{
      productName: "大红袍",
      productCode: "WLC-000002",
      quantity: 2,
      deliveredQuantity: 0,
      receivedQuantity: 0,
      costPrice: "12.30",
      amount: "24.60",
      unit: "罐",
      spec: "150g/罐"
    }]
  }]
  const replenishmentDetail = mapMobileFeatureRows("replenishment", replenishmentRows)[0]

  assert.deepStrictEqual(
    replenishmentDetail._detailFields,
    [
      { label: "要货仓库", value: "主仓库" },
      { label: "要货门店", value: "测试门店" },
      { label: "申请数量", value: "2" },
      { label: "参考总价", value: "¥24.60" }
    ],
    "mobile replenishment detail sheets should show reference total amount without the internal cost permission"
  )

  assert.deepStrictEqual(
    replenishmentDetail._detailSections,
    [{
      title: "补货明细",
      rows: [{
        title: "大红袍",
        meta: "WLC-000002 · 150g/罐",
        value: "2 罐",
        extra: "已发 0 罐 · 已收 0 罐 · 参考成本价 ¥12.30 · 小计 ¥24.60"
      }]
    }],
    "mobile replenishment detail cards should show reference cost and line subtotal without the internal cost permission"
  )

  const costVisibleReplenishment = mapMobileFeatureRows("replenishment", replenishmentRows, { permissions: ["inv:cost:view"] })[0]
  assert.ok(
    costVisibleReplenishment._detailFields.some(field => field.label === "参考总价" && field.value === "¥24.60") &&
      costVisibleReplenishment._detailSections[0].rows[0].extra.includes("参考成本价 ¥12.30"),
    "mobile replenishment detail cards should show reference cost for roles with inv:cost:view"
  )
}

assert.deepStrictEqual(
  mapMobileMineRows({ selectedDeptName: "测试部门", userName: "admin" }).map(item => item.status),
  ["已选择", "可用"],
  "mine rows should reflect selected shop context"
)

assert.deepStrictEqual(
  mapMobileMineRows({ selectedDeptName: "仓库：主仓库", selectedDeptType: "WAREHOUSE", userName: "warehouse" }).map(item => item.title),
  ["当前仓库", "登录账号"],
  "mine rows should label the selected warehouse context as a warehouse instead of a shop"
)

const mappedProfile = mapMobileFeatureRows("profile", [{
  userName: "zhangsan",
  nickName: "张三",
  phonenumber: "13800000000",
  dept: { deptName: "总部人事部" },
  status: "0"
}], {
  selectedDeptType: "WAREHOUSE",
  selectedDeptName: "仓库：主仓库"
})[0]

assert.ok(
  mappedProfile._detailFields.some(field => field.label === "所属组织" && field.value === "总部人事部") &&
    mappedProfile._detailFields.some(field => field.label === "当前业务组织" && field.value === "仓库：主仓库"),
  "mobile profile detail should distinguish account department from selected business context"
)

assert.strictEqual(
  mapMobileFeatureRows("attendance", [{ workDate: "2026-07-15", status: "pending_checkout" }])[0].status,
  "未签退",
  "attendance status codes must render in Chinese"
)

assert.strictEqual(
  mapMobileFeatureRows("salary", [{ salaryMonth: "2026-07", salaryStatus: "draft" }])[0].status,
  "草稿",
  "salary status codes must render in Chinese"
)

assert.ok(
  mapMobileFeatureRows("systemRole", [{ roleName: "门店管理员", roleKey: "shop_admin", roleSort: 1, dataScope: "4", status: "0" }])[0].detail.includes("本部门及以下"),
  "numeric role data scopes must render as Chinese business labels"
)

assert.strictEqual(
  mapMobileFeatureRows("attendance", [{ workDate: "2026-07-15", status: "FUTURE_STATUS" }])[0].status,
  "未知状态",
  "unknown backend status codes must use a safe Chinese fallback"
)
