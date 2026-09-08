import cache from "@/plugins/cache"
import { getSelectedDeptContext, isValidInventoryDeptType } from "@/utils/shopContext"
const { isMobileClient } = require("@/utils/clientPlatform")

const DESKTOP_PASSWORD_RESET_REDIRECT = "/user/profile?activeTab=resetPwd"
const MOBILE_PASSWORD_RESET_REDIRECT = "/mobile/profile?mode=reset-password"
export const PENDING_PASSWORD_RESET_REMINDER_KEY = "pending_password_reset_reminder"
export const PASSWORD_RESET_REMINDER_SHOWN_KEY = "password_reset_reminder_shown"

export function hasSelectedDeptContext() {
  const context = getSelectedDeptContext()
  return !!context.deptId && isValidInventoryDeptType(context.deptType)
}

export function getPasswordResetRedirect() {
  return isMobileClient() ? MOBILE_PASSWORD_RESET_REDIRECT : DESKTOP_PASSWORD_RESET_REDIRECT
}

export function getPasswordResetRoute() {
  if (hasSelectedDeptContext()) {
    const redirect = getPasswordResetRedirect()
    return redirect === MOBILE_PASSWORD_RESET_REDIRECT
      ? { path: "/mobile/profile", query: { mode: "reset-password" } }
      : { path: "/user/profile", query: { activeTab: "resetPwd" } }
  }
  return {
    path: "/select-shop",
    query: {
      redirect: getPasswordResetRedirect()
    }
  }
}

export function resetPasswordResetReminderState() {
  cache.session.remove(PENDING_PASSWORD_RESET_REMINDER_KEY)
  cache.session.remove(PASSWORD_RESET_REMINDER_SHOWN_KEY)
}

export function setPendingPasswordResetReminder(message) {
  if (!message) {
    return
  }
  cache.session.setJSON(PENDING_PASSWORD_RESET_REMINDER_KEY, { message })
}

export function getPendingPasswordResetReminder() {
  return cache.session.getJSON(PENDING_PASSWORD_RESET_REMINDER_KEY)
}

export function markPasswordResetReminderShown() {
  cache.session.set(PASSWORD_RESET_REMINDER_SHOWN_KEY, "1")
}

export function hasShownPasswordResetReminder() {
  return cache.session.get(PASSWORD_RESET_REMINDER_SHOWN_KEY) === "1"
}

export function consumePendingPasswordResetReminder() {
  const reminder = getPendingPasswordResetReminder()
  if (!reminder || hasShownPasswordResetReminder()) {
    return null
  }
  markPasswordResetReminderShown()
  return reminder
}

export function showPendingPasswordResetReminderIfReady(showReminder) {
  if (!hasSelectedDeptContext()) {
    return false
  }
  const reminder = consumePendingPasswordResetReminder()
  if (!reminder) {
    return false
  }
  showReminder(reminder.message)
  return true
}
