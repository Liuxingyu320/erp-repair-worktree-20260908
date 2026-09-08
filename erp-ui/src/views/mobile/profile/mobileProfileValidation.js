const PASSWORD_RULES = {
  "0": { pattern: /^[^<>"'|\\]+$/, message: "密码不能包含非法字符：< > \" ' \\ |" },
  "1": { pattern: /^[0-9]+$/, message: "密码只能为数字（0-9）" },
  "2": { pattern: /^[a-zA-Z]+$/, message: "密码只能为英文字母（a-z、A-Z）" },
  "3": { pattern: /^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]+$/, message: "密码必须同时包含字母和数字" },
  "4": { pattern: /^(?=.*[A-Za-z])(?=.*\d)(?=.*[~!@#$%^&*()\-=_+])[A-Za-z\d~!@#$%^&*()\-=_+]+$/, message: "密码必须同时包含字母、数字和特殊字符（~!@#$%^&*()-=_+）" }
}

function text(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function validateMobileProfile(profile) {
  const value = profile || {}
  const errors = {}
  if (!text(value.nickName)) errors.nickName = "用户昵称不能为空"
  if (!/^1[3-9][0-9]{9}$/.test(text(value.phonenumber))) {
    errors.phonenumber = "请输入正确的手机号码"
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text(value.email))) {
    errors.email = "请输入正确的邮箱地址"
  }
  if (!text(value.currentAddress)) {
    errors.currentAddress = "现住地不能为空"
  } else if (text(value.currentAddress).length > 255) {
    errors.currentAddress = "现住地长度不能超过255个字符"
  }
  if (text(value.emergencyContactPhone) && !/^[0-9+\-\s()]+$/.test(text(value.emergencyContactPhone))) {
    errors.emergencyContactPhone = "请输入正确的联系电话"
  }
  if (text(value.bankAccount) && !/^[0-9]{8,32}$/.test(text(value.bankAccount))) {
    errors.bankAccount = "银行卡号应为8至32位数字"
  }
  return errors
}

function validateMobilePassword(password, policyType) {
  const value = password || {}
  const errors = {}
  const oldPassword = String(value.oldPassword || "")
  const newPassword = String(value.newPassword || "")
  const confirmPassword = String(value.confirmPassword || "")
  const rule = PASSWORD_RULES[String(policyType || "0")] || PASSWORD_RULES["0"]

  if (!oldPassword) errors.oldPassword = "旧密码不能为空"
  if (!newPassword) {
    errors.newPassword = "新密码不能为空"
  } else if (newPassword.length < 8 || newPassword.length > 20) {
    errors.newPassword = "新密码长度必须介于 8 和 20 之间"
  } else if (!rule.pattern.test(newPassword)) {
    errors.newPassword = rule.message
  }
  if (!confirmPassword) {
    errors.confirmPassword = "确认密码不能为空"
  } else if (newPassword !== confirmPassword) {
    errors.confirmPassword = "两次输入的密码不一致"
  }
  return errors
}

module.exports = {
  PASSWORD_RULES,
  validateMobileProfile,
  validateMobilePassword
}
