const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), "utf8")

const requiredFiles = [
  "src/views/system/userNotification/index.vue",
  "src/api/oa/todo.js",
  "src/store/modules/todo.js"
]
requiredFiles.forEach(file => assert.ok(fs.existsSync(path.join(uiRoot, file)), `${file} should exist`))

const notificationApi = read("src/api/system/userNotification.js")
;["/list", "/unread-count", "/read"].forEach(endpoint => {
  assert.ok(notificationApi.includes(endpoint), `user notification API should expose ${endpoint}`)
})

const messagePage = read("src/views/system/userNotification/index.vue")
assert.ok(messagePage.includes("listUserNotifications"))
assert.ok(messagePage.includes("markUserNotificationRead"))
assert.ok(messagePage.includes("resolvePushRoute"), "message navigation should reuse the strict route allowlist")
assert.ok(messagePage.includes("JSON.parse"), "route parameters should be parsed as data")
assert.ok(messagePage.includes("loadError") && messagePage.includes("加载失败"),
  "message list failures should be visible and retryable instead of looking like an empty inbox")
assert.ok(messagePage.includes("markAllUserNotificationsRead") && notificationApi.includes("/read-all") && messagePage.includes("snapshotMaxId"),
  "mark-all should use one account-scoped snapshot request instead of per-notification requests")
assert.ok(!messagePage.includes("window.open"), "messages must never open a server-provided URL")
assert.ok(!messagePage.includes("location.href"), "messages must never assign a server-provided URL")

const todoApi = read("src/api/oa/todo.js")
assert.ok(todoApi.includes("/oa/todo/summary"))
const todoStore = read("src/store/modules/todo.js")
assert.ok(todoStore.includes("namespaced: true"))
assert.ok(todoStore.includes("todoTotal"))

const storeIndex = read("src/store/index.js")
assert.ok(storeIndex.includes("import todo from './modules/todo'"))
assert.ok(/modules:\s*\{[\s\S]*\btodo\b/.test(storeIndex))

const header = read("src/layout/components/HeaderNotice/index.vue")
;["通知公告", "我的消息"].forEach(label => {
  assert.ok(header.includes(label), `header should keep an independent ${label} entry`)
})
;["noticeUnreadCount", "messageUnreadCount"].forEach(counter => {
  assert.ok(header.includes(counter), `header should use independent counter ${counter}`)
})
assert.ok(!header.includes("业务待办"), "HeaderNotice must not duplicate the HeaderTodo entry")
assert.ok(!header.includes("todoTotal"), "HeaderNotice must not own the business todo counter")
assert.ok(!/messageUnreadCount\s*\+\s*noticeUnreadCount|noticeUnreadCount\s*\+\s*messageUnreadCount/.test(header),
  "announcement and personal-message counts must never be added together")

const router = read("src/router/index.js")
assert.ok(router.includes("path: '/system/user-notification'"))
assert.ok(router.includes("@/views/system/userNotification/index"))

console.log("targetedUserNotification tests passed")
