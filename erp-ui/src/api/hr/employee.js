import request from "@/utils/request"

export const HR_EXPORT_ACTION = "system/hr/employee/export"

export const listHrEmployees = params => request({
  url: "/system/hr/employee/list",
  method: "get",
  params
})

export const getHrEmployeeSummary = params => request({
  url: "/system/hr/employee/summary",
  method: "get",
  params
})

export const getHrEmployeeFormOptions = () => request({
  url: "/system/hr/employee/form-options",
  method: "get"
})

export const getHrEmployee = userId => request({
  url: `/system/hr/employee/${userId}`,
  method: "get"
})

export const initializeHrEmployeeProfile = userId => request({
  url: `/system/hr/employee/${userId}/profile/initialize`,
  method: "post",
  silentError: true
})

export const getHrTransferBusinessDate = () => request({
  url: "/system/hr/employee/transfer/business-date",
  method: "get"
})

export const confirmHrEmployeeTransfer = (userId, data) => request({
  url: `/system/hr/employee/${userId}/transfer`,
  method: "post",
  data,
  silentError: true
})

export const getHrOffboardingBusinessDate = () => request({
  url: "/system/hr/employee/offboard/business-date",
  method: "get"
})

export const confirmHrEmployeeOffboarding = (userId, data) => request({
  url: `/system/hr/employee/${userId}/offboard`,
  method: "post",
  data,
  silentError: true
})

export const updateHrEmployee = (userId, data) => {
  const patch = { ...(data || {}) }
  delete patch.userId
  return request({
    url: `/system/hr/employee/${userId}`,
    method: "patch",
    data: patch,
    silentError: true
  })
}

export const previewHrEmployeeDerived = data => request({
  url: "/system/hr/employee/derived-preview",
  method: "post",
  data,
  silentError: true
})

export const revealHrEmployeeSensitiveField = (userId, fieldKey) => request({
  url: `/system/hr/employee/${userId}/sensitive/reveal`,
  method: "post",
  data: { fieldKey },
  silentError: true
})

export const exportHrEmployeeSensitive = data => request({
  url: "/system/hr/employee/export-sensitive",
  method: "post",
  data,
  responseType: "blob"
})
