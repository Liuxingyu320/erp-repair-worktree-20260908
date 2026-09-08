const emptyTextMap = {
  sales: {
    missingContext: "请选择门店后查看销售单。",
    missingBaseline: "暂无销售单；请先维护商品资料和客户信息，再新建销售单。"
  },
  deliveryNotice: {
    missingContext: "请选择门店或仓库后查看发货通知。",
    missingBaseline: "暂无发货通知；销售单提交并生成通知后会出现在这里。"
  },
  purchaseReturn: {
    missingContext: "请选择仓库后查看采购退货单。",
    missingBaseline: "暂无采购退货单；需要先有已收货采购单，才能发起采购退货。"
  },
  salesReturn: {
    missingContext: "请选择门店后查看销售退货单。",
    missingBaseline: "暂无销售退货单；需要先有已出库销售单，才能发起销售退货。"
  },
  oaPurchase: {
    missingContext: "暂无采购申请；如门店/部门未配置审批流，请先联系管理员配置。",
    missingBaseline: "暂无采购申请；可以从新建采购申请开始。"
  },
  salary: {
    missingContext: "暂无工资记录；请先选择月份并确认工资参数。",
    missingBaseline: "暂无工资记录；请先维护考勤和工资参数，再计算本月工资。"
  },
  signPackage: {
    missingContext: "暂无签约包；请先维护员工和签约模板。",
    missingBaseline: "暂无签约包；需要先有可用模板和员工资料，才能新建签约包。"
  },
  signTemplate: {
    missingContext: "暂无签约模板；请先上传模板文件。",
    missingBaseline: "暂无签约模板；新增模板后才能匹配签约包文件。"
  },
  fixedAssetConfig: {
    missingContext: "暂无店铺固定资产配置；可点击添加店铺固定资产明细后选择店铺维护。",
    missingBaseline: "固定资产基础配置为空；请维护固定资产明细。"
  },
  fixedAssetRepair: {
    missingContext: "请先选择店铺并维护固定资产配置后再查看维修上报。",
    missingBaseline: "暂无维修上报；店铺有固定资产配置后才能提交维修申请。"
  }
}

export function getBusinessEmptyText(moduleKey, state = "missingBaseline") {
  const moduleText = emptyTextMap[moduleKey] || {}
  return moduleText[state] || moduleText.missingBaseline || "暂无数据"
}
