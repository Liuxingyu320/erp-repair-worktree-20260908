export const CONTRACT_TYPE_OPTIONS = Object.freeze([
  { value: "LABOR_CONTRACT", label: "劳动合同" },
  { value: "SERVICE_CONTRACT", label: "劳务协议" },
  { value: "INTERNSHIP_AGREEMENT", label: "实习协议" },
  { value: "OUTSOURCING_CONTRACT", label: "外包合同" }
])

export const CONTRACT_TERM_OPTIONS = Object.freeze([
  { value: "FIXED_TERM", label: "固定期限" },
  { value: "OPEN_ENDED", label: "无固定期限" }
])

export const SOCIAL_TYPE_OPTIONS = Object.freeze([
  { value: "SOCIAL_INSURED", label: "缴纳社保" },
  { value: "SOCIAL_UNINSURED", label: "无需缴纳" },
  { value: "DISPATCHED", label: "劳务派遣" },
  { value: "PENDING_CONFIRMATION", label: "待确认" }
])

const OPTIONS_BY_KEY = Object.freeze({
  contractType: CONTRACT_TYPE_OPTIONS,
  contractTerm: CONTRACT_TERM_OPTIONS,
  socialType: SOCIAL_TYPE_OPTIONS
})

export function signingOptionsForKey(key) {
  return OPTIONS_BY_KEY[key]
}

export function signingProfileLabel(options, value) {
  if (value === undefined || value === null || value === "") return "-"
  const matched = (options || []).find(option => option.value === value)
  return matched ? matched.label : (/[^\x00-\x7F]/.test(String(value)) ? value : "未登记中文名称")
}
