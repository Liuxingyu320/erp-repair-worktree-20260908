const { PROFILE_COMPLETION_FIELD_KEYS } = require("./profileCompletionFields")

const MAINLAND_MOBILE_PATTERN = /^1[3-9]\d{9}$/
const MAINLAND_ID_CARD_PATTERN = /^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]$/
const ID_CARD_CHECK_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
const ID_CARD_CHECK_CODES = ["1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"]

function trimText(value) {
  return value === null || value === undefined ? "" : String(value).trim()
}

function normalizePhone(value) {
  return trimText(value).replace(/[\s-]/g, "")
}

function normalizeProfileCompletionPayload(values, fieldKeys) {
  const source = values || {}
  const keys = Array.isArray(fieldKeys) ? fieldKeys : PROFILE_COMPLETION_FIELD_KEYS
  return keys.reduce((payload, key) => {
    if (PROFILE_COMPLETION_FIELD_KEYS.indexOf(key) === -1) return payload
    if (key === "phonenumber") {
      payload[key] = normalizePhone(source[key])
    } else if (key === "birthDate") {
      payload[key] = source[key] || ""
    } else {
      payload[key] = trimText(source[key])
    }
    return payload
  }, {})
}

function parseDateOnly(value) {
  const text = trimText(value)
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text)
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return date
}

function currentDateOnly() {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

function isMainlandIdType(value) {
  return trimText(value).indexOf("身份证") > -1
}

function isValidMainlandIdCard(value) {
  const normalized = trimText(value).toUpperCase()
  if (!MAINLAND_ID_CARD_PATTERN.test(normalized) || !parseDateOnly(
    `${normalized.slice(6, 10)}-${normalized.slice(10, 12)}-${normalized.slice(12, 14)}`
  )) return false
  const sum = ID_CARD_CHECK_WEIGHTS.reduce((total, weight, index) => {
    return total + Number(normalized[index]) * weight
  }, 0)
  return ID_CARD_CHECK_CODES[sum % 11] === normalized[17]
}

function validateProfileCompletion(values, requiredKeys) {
  const required = new Set(Array.isArray(requiredKeys) ? requiredKeys : PROFILE_COMPLETION_FIELD_KEYS)
  const normalized = normalizeProfileCompletionPayload(values)
  const errors = {}
  const requiredError = (key, message) => {
    if (required.has(key) && !normalized[key]) errors[key] = message
  }

  requiredError("nickName", "请填写姓名")
  if (required.has("nickName") && normalized.nickName && normalized.nickName.length > 30) {
    errors.nickName = "姓名不能超过 30 个字符"
  }

  requiredError("phonenumber", "请填写手机号")
  if (required.has("phonenumber") && normalized.phonenumber && !MAINLAND_MOBILE_PATTERN.test(normalized.phonenumber)) {
    errors.phonenumber = "请输入正确的手机号"
  }

  if (required.has("sex") && normalized.sex !== "0" && normalized.sex !== "1") errors.sex = "请选择性别"

  requiredError("birthDate", "请选择出生日期")
  if (required.has("birthDate") && normalized.birthDate) {
    const birthDate = parseDateOnly(normalized.birthDate)
    if (!birthDate) errors.birthDate = "请选择正确的出生日期"
    else if (birthDate > currentDateOnly()) errors.birthDate = "出生日期不能晚于今天"
  }

  requiredError("idType", "请选择证件类型")
  if (required.has("idType") && normalized.idType.length > 64) errors.idType = "证件类型不能超过 64 个字符"
  requiredError("idNumber", "请填写证件号码")
  if (required.has("idNumber") && normalized.idNumber && isMainlandIdType(normalized.idType) &&
    !isValidMainlandIdCard(normalized.idNumber)) {
    errors.idNumber = "请输入正确的居民身份证号码"
  } else if (required.has("idNumber") && normalized.idNumber && !isMainlandIdType(normalized.idType) &&
    (normalized.idNumber.length < 3 || normalized.idNumber.length > 64)) {
    errors.idNumber = "证件号码长度应为 3–64 个字符"
  }

  requiredError("registeredResidence", "请填写户籍地址")
  if (required.has("registeredResidence") && normalized.registeredResidence.length > 255) {
    errors.registeredResidence = "户籍地址不能超过 255 个字符"
  }
  requiredError("currentAddress", "请填写现居住地址")
  if (required.has("currentAddress") && normalized.currentAddress.length > 255) {
    errors.currentAddress = "现居住地址不能超过 255 个字符"
  }
  requiredError("maritalStatus", "请选择婚姻状况")
  if (required.has("maritalStatus") && normalized.maritalStatus.length > 32) {
    errors.maritalStatus = "婚姻状况不能超过 32 个字符"
  }
  requiredError("ethnicity", "请填写民族")
  if (required.has("ethnicity") && normalized.ethnicity.length > 64) errors.ethnicity = "民族不能超过 64 个字符"
  return errors
}

module.exports = {
  isValidMainlandIdCard,
  normalizeProfileCompletionPayload,
  validateProfileCompletion
}
