const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  MOBILE_ROUTES
} = require("../src/views/mobile/mobileNavigation")

const {
  mapMobileFeatureRows
} = require("../src/views/mobile/feature/featureMapper")

const {
  getMobileFeatureActions
} = require("../src/views/mobile/feature/featureActions")

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const featureSearchConfigsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureSearchConfigs.js"),
  "utf8"
)
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)
const featureActionServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionService.js"),
  "utf8"
)
const featureActionRuntimeSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionRuntime.js"),
  "utf8"
)
const workbenchShellSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/components/MobileWorkbenchShell.vue"),
  "utf8"
)

assert.strictEqual(
  MOBILE_ROUTES.notice,
  "/mobile/notice",
  "mobile should expose a dedicated notice route instead of a static bell icon"
)

assert.strictEqual(
  MOBILE_ROUTES.profile,
  "/mobile/profile",
  "mobile should expose a dedicated profile route instead of sending users to desktop profile pages"
)

;[["notice", "/mobile/notice"]].forEach(([key, routePath]) => {
  assert.ok(
    mobileRouteSource.includes(`path: '${routePath}'`) &&
      mobileRouteSource.includes(`featureKey: '${key}'`),
    `${routePath} should be registered as a mobile feature route`
  )
})

assert.ok(
  mobileRouteSource.includes("path: '/mobile/profile'") &&
    mobileRouteSource.includes("component: () => import('@/views/mobile/profile/index')"),
  "/mobile/profile should render the dedicated mobile profile maintenance page"
)

const mineRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/mine'"),
  mobileRouteSource.length
)

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

assert.ok(
  mineRouteSource.includes("path: '/mobile/profile'") &&
    mineRouteSource.includes("path: '/mobile/notice'") &&
    mineRouteSource.includes("behavior: 'logout'"),
  "mobile mine page should expose profile, notice, and logout actions"
)

assert.ok(
  mineRouteSource.includes("contextCopy") &&
    mineRouteSource.includes("STORE") &&
    mineRouteSource.includes("WAREHOUSE") &&
    mineRouteSource.includes("账号与店铺") &&
    mineRouteSource.includes("账号与仓库") &&
    mineRouteSource.includes("切换仓库") &&
    mineRouteSource.includes("当前仓库"),
  "mobile mine route copy should switch between store and warehouse contexts"
)

assert.ok(
  mobileRouteSource.includes("contextualLabel: 'switchBusinessContext'"),
  "mobile profile actions should use contextual store/warehouse switch copy"
)

assert.ok(
  workbenchShellSource.includes("noticePath") &&
    workbenchShellSource.includes("@click=\"openPath(noticePath)\""),
  "mobile workbench notification button should open the phone notice page"
)

assert.ok(
  featureServiceSource.includes("listNoticeTop") &&
    featureServiceSource.includes("listNotice") &&
    featureServiceSource.includes("getNotice") &&
    featureServiceSource.includes("getUserProfile") &&
    featureServiceSource.includes("featureKey === \"notice\"") &&
    featureServiceSource.includes("featureKey === \"profile\""),
  "mobile feature service should reuse desktop notice and profile APIs"
)

assert.ok(
  featurePageSource.includes("logoutMobile") &&
    featurePageSource.includes("behavior === \"logout\"") &&
    featureSearchConfigsSource.includes("notice:") &&
    featureSearchConfigsSource.includes("profile:"),
  "mobile feature page should support notice/profile search and account logout actions"
)

assert.ok(
  featureActionServiceSource.includes("markNoticeRead") &&
    featureActionServiceSource.includes("markNoticeReadAll") &&
    featureActionRuntimeSource.includes("runNoticeAction"),
  "mobile action service should execute notice read actions"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("notice", [{
    noticeId: 12,
    noticeTitle: "端午排班调整",
    noticeType: "1",
    createBy: "admin",
    createTime: "2026-06-10 10:00:00",
    isRead: false
  }]),
  [{
    title: "端午排班调整",
    code: "NOTICE-12",
    detail: "通知 · admin · 2026-06-10 10:00",
    status: "未读"
  }],
  "mobile notice rows should map title, type, author, time, and read status"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("profile", [{
    userName: "zhangsan",
    nickName: "张三",
    phonenumber: "13800000000",
    email: "zhangsan@example.com",
    dept: { deptName: "杭州湖滨店" },
    status: "0"
  }]),
  [{
    title: "张三",
    code: "zhangsan",
    detail: "杭州湖滨店 · 13800000000 · zhangsan@example.com",
    status: "可用"
  }],
  "mobile profile rows should map the same account fields shown in desktop profile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("notice", { _raw: { noticeId: 12, isRead: false } }).map(action => action.id),
  ["markNoticeRead"],
  "unread mobile notices should expose a mark-read action"
)

assert.deepStrictEqual(
  getMobileFeatureActions("notice", { _raw: { noticeId: 13, isRead: true } }).map(action => action.id),
  [],
  "read mobile notices should not expose redundant mark-read actions"
)
