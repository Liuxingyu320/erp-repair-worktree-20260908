const assert = require("assert")
const fs = require("fs")
const path = require("path")

const userStoreSource = fs.readFileSync(
  path.resolve(__dirname, "../src/store/modules/user.js"),
  "utf8"
)
const profileSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/profile/index.vue"),
  "utf8"
)
const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)
const passwordResetReminderSourcePath = path.resolve(
  __dirname,
  "../src/utils/passwordResetReminder.js"
)
const passwordResetReminderSource = fs.existsSync(passwordResetReminderSourcePath)
  ? fs.readFileSync(passwordResetReminderSourcePath, "utf8")
  : ""

assert.ok(
  passwordResetReminderSource.includes("/mobile/profile?mode=reset-password") &&
    passwordResetReminderSource.includes("isMobileClient") &&
    !passwordResetReminderSource.includes("matchMedia"),
  "password reset reminders should use the dedicated mobile profile route only for a mobile client"
)

assert.ok(
  userStoreSource.includes("getPasswordResetRoute"),
  "password reset prompt should route through a shared helper"
)

assert.ok(
  passwordResetReminderSource.includes("PENDING_PASSWORD_RESET_REMINDER_KEY"),
  "password reset reminder state should be shared through a small utility"
)

assert.ok(
  userStoreSource.includes("setPendingPasswordResetReminder") &&
    userStoreSource.includes("showPendingPasswordResetReminderIfReady"),
  "GetInfo should record password reset state and only show it when a shop context is ready"
)

assert.ok(
  userStoreSource.includes("confirmButtonText: '去修改'") &&
    userStoreSource.includes("cancelButtonText: '稍后'"),
  "initial-password reminder should use action-specific button labels instead of ambiguous confirm/cancel text"
)

assert.ok(
  (passwordResetReminderSource.includes("path: '/select-shop'") ||
    passwordResetReminderSource.includes('path: "/select-shop"')) &&
    passwordResetReminderSource.includes("redirect: getPasswordResetRedirect()"),
  "password reset prompt should keep shop selection first and send users to reset password after selecting a shop"
)

assert.ok(
  !userStoreSource.includes("router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })"),
  "password reset prompt should not skip shop selection by pushing directly to Profile"
)

assert.ok(
  selectShopSource.includes("showPendingPasswordResetReminderAfterSelection"),
  "shop selection confirmation should show any pending initial-password reminder after persisting shop context"
)

assert.ok(
  selectShopSource.includes("resolveShopEntryRedirect") &&
    !selectShopSource.includes("isMobileHomeRedirect"),
  "mobile enter-workbench action should ignore stale secondary-page redirects"
)

assert.ok(
  passwordResetReminderSource.includes("PASSWORD_RESET_REMINDER_SHOWN_KEY"),
  "password reset reminder should be marked as shown so selecting a shop does not create repeated prompts"
)

assert.ok(
  profileSource.includes("this.$route.query && this.$route.query.activeTab"),
  "profile page should support opening the reset password tab from a URL redirect"
)
