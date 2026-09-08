import request from "@/utils/request"

export const listHrOnboarding = params => request({ url: "/system/hr/onboarding/list", method: "get", params })
export const getHrOnboardingSummary = params => request({ url: "/system/hr/onboarding/summary", method: "get", params })
export const getHrOnboarding = id => request({ url: `/system/hr/onboarding/${id}`, method: "get" })
export const createHrOnboarding = data => request({ url: "/system/hr/onboarding", method: "post", data, silentError: true })
export const updateHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}`, method: "put", data, silentError: true })
export const markHrOnboardingReady = (id, data) => request({ url: `/system/hr/onboarding/${id}/ready`, method: "post", data, silentError: true })
export const returnHrOnboardingToDraft = (id, data) => request({ url: `/system/hr/onboarding/${id}/return-to-draft`, method: "post", data, silentError: true })
export const getHrOnboardingConflicts = id => request({ url: `/system/hr/onboarding/${id}/conflicts`, method: "get" })
export const confirmHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/confirm`, method: "post", data, silentError: true })
export const cancelHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/cancel`, method: "post", data, silentError: true })
export const restoreHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/restore`, method: "post", data, silentError: true })
export const getHrOnboardingFormOptions = params => request({ url: "/system/hr/onboarding/form-options", method: "get", params })
export const getHrOnboardingOwnerOptions = params => request({ url: "/system/hr/onboarding/owner-options", method: "get", params })
export const listHrOnboardingPositionConfigs = params => request({ url: "/system/hr/onboarding/config/list", method: "get", params })
export const getHrOnboardingPositionConfig = id => request({ url: `/system/hr/onboarding/config/${id}`, method: "get" })
export const createHrOnboardingPositionConfig = data => request({ url: "/system/hr/onboarding/config", method: "post", data, silentError: true })
export const updateHrOnboardingPositionConfig = (id, data) => request({ url: `/system/hr/onboarding/config/${id}`, method: "put", data, silentError: true })
export const disableHrOnboardingPositionConfig = (id, version) => request({ url: `/system/hr/onboarding/config/${id}/disable`, method: "post", data: { version }, silentError: true })
export const getHrOnboardingPositionConfigOptions = () => request({ url: "/system/hr/onboarding/config/options", method: "get" })
export const previewHrOnboardingImport = data => request({
  url: "/system/hr/onboarding/import/preview",
  method: "post",
  data,
  headers: { "Content-Type": "multipart/form-data" },
  timeout: 60000,
  silentError: true
})
export const getHrOnboardingImportBatch = id => request({ url: `/system/hr/onboarding/import/${id}`, method: "get" })
export const confirmHrOnboardingImport = (id, data) => request({ url: `/system/hr/onboarding/import/${id}/confirm`, method: "post", data, timeout: 60000, silentError: true })
export const downloadHrOnboardingTemplate = () => request({ url: "/system/hr/onboarding/import/template", method: "get", responseType: "blob" })
export const downloadHrOnboardingErrorRows = id => request({ url: `/system/hr/onboarding/import/${id}/errors`, method: "get", responseType: "blob" })

export const HR_ONBOARDING_TEMPLATE_URL = "system/hr/onboarding/import/template"
export const hrOnboardingErrorRowsUrl = id => `system/hr/onboarding/import/${id}/errors`
