const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  validateMobileProfile,
  validateMobilePassword
} = require("../src/views/mobile/profile/mobileProfileValidation")

assert.deepStrictEqual(
  validateMobileProfile({ nickName: "", phonenumber: "1", email: "bad" }),
  {
    nickName: "用户昵称不能为空",
    phonenumber: "请输入正确的手机号码",
    email: "请输入正确的邮箱地址",
    currentAddress: "现住地不能为空"
  },
  "mobile profile should expose field-specific errors"
)

assert.deepStrictEqual(
  validateMobileProfile({
    nickName: " 张三 ",
    phonenumber: "13800000000",
    email: "zhangsan@example.com",
    currentAddress: "上海市浦东新区"
  }),
  {},
  "valid mobile profile fields should pass"
)

assert.strictEqual(
  validateMobileProfile({
    nickName: "张三",
    phonenumber: "13800000000",
    email: "zhangsan@example.com",
    currentAddress: "住".repeat(256)
  }).currentAddress,
  "现住地长度不能超过255个字符",
  "mobile profile should enforce the current-address length limit"
)

assert.strictEqual(
  validateMobilePassword({
    oldPassword: "old-password",
    newPassword: "12345678",
    confirmPassword: "87654321"
  }, "0").confirmPassword,
  "两次输入的密码不一致",
  "mobile password form should require exact confirmation"
)

assert.strictEqual(
  validateMobilePassword({
    oldPassword: "old-password",
    newPassword: "abcdefgh",
    confirmPassword: "abcdefgh"
  }, "3").newPassword,
  "密码必须同时包含字母和数字",
  "mobile password form should follow the configured password character policy"
)

assert.deepStrictEqual(
  validateMobilePassword({
    oldPassword: "old-password",
    newPassword: "abc12345",
    confirmPassword: "abc12345"
  }, "3"),
  {},
  "a password satisfying the configured policy should pass"
)

const profilePagePath = path.resolve(__dirname, "../src/views/mobile/profile/index.vue")
assert.ok(fs.existsSync(profilePagePath), "mobile profile should have a dedicated page")
const profileSource = fs.readFileSync(profilePagePath, "utf8")
const routeSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)

;["getUserProfile", "updateUserProfile", "uploadAvatar", "updateUserPwd"].forEach(apiName => {
  assert.ok(profileSource.includes(apiName), `mobile profile should reuse ${apiName}`)
})

assert.ok(
  routeSource.includes("component: () => import('@/views/mobile/profile/index')"),
  "mobile profile route should render the dedicated mobile maintenance page"
)

assert.ok(
  profileSource.includes("mode === \"reset-password\"") &&
    profileSource.includes("resetPasswordResetReminderState") &&
    profileSource.includes('formData.append("avatarfile"') &&
    profileSource.includes('autocomplete="current-password"') &&
    profileSource.includes('autocomplete="new-password"'),
  "mobile profile should support direct reset mode, avatar upload, and password-safe autocomplete"
)

assert.ok(
  profileSource.includes("profileErrors") &&
    profileSource.includes("passwordErrors") &&
    profileSource.includes('role="alert"') &&
    profileSource.includes("mobile-profile-current-address") &&
    profileSource.includes("身份与户籍") &&
    profileSource.includes("教育经历") &&
    profileSource.includes("银行与社保") &&
    profileSource.includes("任职信息") &&
    profileSource.includes("合同信息"),
  "mobile profile should keep visible field and request errors"
)

assert.ok(
  profileSource.includes("isPreviewMode") &&
    profileSource.includes("演示/预览资料") &&
    profileSource.includes("previewProfile") &&
    profileSource.includes(':disabled="isPreviewMode || profileSaving"'),
  "mobile profile should preserve the existing local preview flow without allowing writes"
)

assert.ok(
  /<input ref="avatarInput"[^>]+:disabled="isPreviewMode \|\| avatarSaving"/.test(profileSource),
  "mobile profile preview should also disable the hidden avatar file control"
)

assert.ok(
  profileSource.includes('import defAva from "@/assets/images/profile.jpg"') &&
    profileSource.includes("this.user.avatar || defAva"),
  "mobile profile should always render the existing default avatar asset"
)

assert.ok(
  /\.avatar-wrap button\s*\{[^}]*min-height:\s*44px/s.test(profileSource),
  "mobile profile avatar action should keep a 44px touch target"
)

console.log("mobileProfileMaintenance tests passed")
