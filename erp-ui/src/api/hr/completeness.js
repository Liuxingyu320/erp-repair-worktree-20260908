import request from "@/utils/request"

export const getHrCompletenessSummary = params => request({
  url: "/system/hr/completeness/summary",
  method: "get",
  params
})

export const listHrCompletenessEmployees = params => request({
  url: "/system/hr/completeness/employees",
  method: "get",
  params
})

export const getHrCompletenessDepartments = params => request({
  url: "/system/hr/completeness/departments",
  method: "get",
  params
})

export const getHrMasterDataSummary = params => request({
  url: "/system/hr/completeness/master-data/summary",
  method: "get",
  params
})

export const listHrMasterDataIssues = params => request({
  url: "/system/hr/completeness/master-data/issues",
  method: "get",
  params
})

export const getHrMasterDataIssueCodes = () => request({
  url: "/system/hr/completeness/master-data/codes",
  method: "get"
})
