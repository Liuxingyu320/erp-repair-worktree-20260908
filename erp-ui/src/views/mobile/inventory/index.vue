<template>
  <div class="inventory-mobile-page mobile-system-page">
    <main class="mobile-shell" aria-label="进销存手机工作台">
      <section class="content-stage mobile-system-scroll" data-mobile-scroll-root>
        <div class="title-row mobile-system-topbar">
          <div>
            <h1>{{ data.title }}</h1>
            <button class="selector-pill" type="button" @click="goSelectShop">
              <span class="selector-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.warehouse" /></svg>
              </span>
              <span>{{ data.selector }}</span>
              <svg class="chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5H7Z" /></svg>
            </button>
          </div>
          <div class="header-actions">
            <button class="todo-entry-button touch-target-min" type="button" :aria-label="todoEntryAriaLabel" @click="openTodoCenter">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.check" /></svg>
              <span v-if="todoBadgeVisible" class="todo-badge">{{ todoBadgeText }}</span>
            </button>
            <button class="notify-button touch-target-min" type="button" aria-label="通知" @click="openPath('/mobile/notice')">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.bell" /></svg>
              <span v-if="noticeUnreadCount > 0" class="notice-badge">{{ noticeBadgeText }}</span>
            </button>
          </div>
        </div>

        <section
          v-if="blockingState"
          :class="['glass-card', 'state-card', 'mobile-data-state', `mobile-data-state--${blockingState.type}`, { error: blockingState.isError }]"
          :role="blockingState.isError ? 'alert' : 'status'"
          :aria-live="blockingState.isError ? 'assertive' : 'polite'"
        >
          <div>
            <strong>{{ blockingState.title }}</strong>
            <p>{{ blockingState.description }}</p>
          </div>
          <button
            v-if="blockingState.actionId"
            class="mobile-button mobile-button--secondary"
            type="button"
            @click="handleBlockingAction(blockingState.actionId)"
          >{{ blockingState.actionLabel }}</button>
        </section>

        <template v-else>
          <section v-if="partialMessage" class="glass-card state-card mobile-alert mobile-alert--warning" role="status" aria-live="polite">
            <div>
              <strong>部分数据暂不可用</strong>
              <p>{{ partialMessage }}</p>
            </div>
            <button type="button" @click="loadWorkbench">重试</button>
          </section>

          <section v-if="todoHealthMessage" class="glass-card state-card mobile-alert mobile-alert--warning" role="status" aria-live="polite">
            <div>
              <strong>待办数据提示</strong>
              <p>{{ todoHealthMessage }}</p>
            </div>
            <button type="button" @click="refreshTodos">刷新</button>
          </section>

          <section class="section-head mobile-section-title">
            <h2>优先处理</h2>
            <button type="button" @click="openTodoCenter">全部 <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6V6Z" /></svg></button>
          </section>

          <div class="todo-counts" aria-label="待办分类数量">
            <span>审批 {{ todoCount('approval') }}</span>
            <span>执行 {{ todoCount('execution') }}</span>
            <span>退回 {{ todoCount('returned') }}</span>
            <span>风险 {{ todoCount('risk') }}</span>
            <span>个人 {{ todoCount('personal') }}</span>
          </div>

          <section class="glass-card priority-card mobile-system-panel mobile-system-panel--continuous">
            <div v-if="todoCards.length === 0" class="priority-empty mobile-data-state mobile-data-state--empty mobile-data-state--compact">
              <strong>{{ todoEmptyTitle }}</strong>
              <span>{{ todoEmptyMessage }}</span>
            </div>
            <button
              v-for="item in todoCards"
              :key="item.todoKey"
              class="priority-row"
              type="button"
              @click="openTodo(item)"
            >
              <div :class="['row-icon', todoTone(item.category)]">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[item.icon]" /></svg>
              </div>
              <div class="row-copy">
                <h3>{{ item.title || item.businessNo || '待处理事项' }}</h3>
                <p>{{ item.businessNo || sourceLabel(item.source) }}</p>
                <p>{{ item.summary || '请进入业务页面查看详情' }}</p>
              </div>
              <div class="status-col">
                <span :class="['status-pill', 'mobile-status-chip', todoTone(item.category)]">{{ categoryLabel(item.category) }}</span>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6V6Z" /></svg>
              </div>
            </button>
          </section>

          <section class="section-head action-title mobile-section-title">
            <h2>快捷操作</h2>
          </section>

          <section class="quick-grid" aria-label="快捷操作">
            <button v-for="action in data.quickActions" :key="action.label" class="glass-action" type="button" @click="openAction(action)">
              <svg :class="action.tone" viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[action.icon]" /></svg>
              <span>{{ action.label }}</span>
            </button>
          </section>

          <section v-if="data.metrics && data.metrics.length" class="glass-card hero-card mobile-system-panel metrics-panel">
            <div class="metric-panel">
              <div v-for="metric in data.metrics" :key="metric.label" class="metric-cell">
                <div class="metric-label">{{ metric.label }}</div>
                <div class="metric-value">
                  <strong class="mobile-metric-value">{{ metric.value }}</strong>
                  <span>{{ metric.unit }}</span>
                </div>
              </div>
            </div>
          </section>
        </template>
      </section>

      <nav class="bottom-nav mobile-system-bottom-nav" aria-label="手机底部导航">
        <button v-for="item in data.bottomNav" :key="item.label" :class="{ active: item.active }" type="button" @click="openNav(item)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[item.icon]" /></svg>
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </main>
  </div>
</template>

<script>
import { mapGetters } from "vuex"
import { constantRoutes } from "@/router"
import { buildAvailableRouteSet } from "@/utils/todoRouteResolver"
import { navigateTodo } from "@/utils/todoNavigator"
import { beginTodoContextLease, formatTodoContextSwitchNotice, rollbackTodoContextLease } from "@/utils/todoContextLease"
import { fetchMobileInventoryWorkbench } from "./workbenchService"
import { clearSelectedDept, getSelectedDeptContext, setSelectedDept } from "@/utils/shopContext"
import { listNoticeTop } from "@/api/system/notice"

const { workbenchData } = require("./workbenchData")
const { mapWorkbenchResponse } = require("./workbenchMapper")
const { mobileQuickActions, mobileBottomNav } = require("../mobileNavigation")
const mobileViewport = require("../mobileViewport")
const startMobileViewportSync = typeof mobileViewport.startMobileViewportSync === "function"
  ? mobileViewport.startMobileViewportSync
  : () => false
const stopMobileViewportSync = typeof mobileViewport.stopMobileViewportSync === "function"
  ? mobileViewport.stopMobileViewportSync
  : () => false

function createEmptyWorkbenchData(selector = "", message = "") {
  return {
    title: workbenchData.title,
    selector: selector || workbenchData.selector || "",
    overview: {
      title: message ? "数据未加载" : workbenchData.overview.title,
      subtitle: message || "正在读取当前组织的真实销售、采购、库存和仓库任务"
    },
    metrics: [],
    priorityItems: [],
    quickActions: [],
    bottomNav: []
  }
}

export default {
  name: "MobileInventoryWorkbench",
  data() {
    return {
      data: createEmptyWorkbenchData(),
      mobileQuickActions,
      mobileBottomNav,
      loading: false,
      errorMessage: "",
      partialMessage: "",
      noticeUnreadCount: null,
      iconPaths: {
        alert: "M12 3 2 21h20L12 3Zm1 14h-2v2h2v-2Zm0-7h-2v6h2v-6Z",
        bag: "M7 7V6a5 5 0 0 1 10 0v1h3v14H4V7h3Zm2 0h6V6a3 3 0 0 0-6 0v1Zm-3 2v10h12V9H6Z",
        bell: "M12 22a2.8 2.8 0 0 0 2.7-2h-5.4A2.8 2.8 0 0 0 12 22Zm7-6-2-2v-4a5 5 0 0 0-4-4.9V3h-2v2.1A5 5 0 0 0 7 10v4l-2 2v2h14v-2Z",
        cart: "M7 18a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm10 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM3 4h2l2.2 10.2A2 2 0 0 0 9.2 16H18v-2H9.2L8.8 12H18.5L21 6H7.5L7 4H3Z",
        check: "M18 4h-2.2A3 3 0 0 0 13 2h-2a3 3 0 0 0-2.8 2H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2Zm-7 0h2a1 1 0 0 1 1 1H10a1 1 0 0 1 1-1Zm5.2 7.4-5.1 5.1-3.1-3.1 1.4-1.4 1.7 1.7 3.7-3.7 1.4 1.4Z",
        cube: "m12 2 9 5v10l-9 5-9-5V7l9-5Zm0 2.3L6.2 7.5 12 10.8l5.8-3.3L12 4.3ZM5 9.2v6.6l6 3.3v-6.6L5 9.2Zm8 9.9 6-3.3V9.2l-6 3.3v6.6Z",
        cubes: "m7 2 5 2.8v5.6l-5 2.8-5-2.8V4.8L7 2Zm10 5 5 2.8v5.6l-5 2.8-5-2.8V9.8L17 7ZM7 12l5 2.8v5.6l-5 2.8-5-2.8v-5.6L7 12Z",
        document: "M6 2h9l5 5v15H6V2Zm8 2v5h4l-4-5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        home: "M3 11 12 3l9 8v10h-6v-6H9v6H3V11Z",
        inbound: "M11 3h2v9l3.5-3.5 1.4 1.4L12 15.8 6.1 9.9l1.4-1.4L11 12V3ZM5 18h14v2H5v-2Z",
        package: "m12 2 8 4v12l-8 4-8-4V6l8-4Zm0 2.2L7 6.7l5 2.5 5-2.5-5-2.5ZM6 8.5v8.3l5 2.5V11L6 8.5Zm7 10.8 5-2.5V8.5L13 11v8.3Z",
        return: "M8 7h8a5 5 0 0 1 0 10h-5v-2h5a3 3 0 0 0 0-6H8v4L3 8l5-5v4Z",
        purchase: "M6 3h12v4h2v14H4V7h2V3Zm2 4h8V5H8v2Zm2 5h4v2h-4v4H8v-4H4v-2h4V8h2v4Z",
        sales: "M5 4h11l3 3v13H5V4Zm10 1.5V8h2.5L15 5.5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        search: "M10.5 4a6.5 6.5 0 0 1 5.1 10.5l4 4-1.4 1.4-4-4A6.5 6.5 0 1 1 10.5 4Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z",
        trend: "M4 18h16v2H4v-2Zm2-4 4-4 3 3 5-7 2 1.4-6.8 9.4-3.1-3.1-2.7 2.7L6 14Z",
        truck: "M3 5h12v9h2.2L20 10h1v7h-2a3 3 0 0 1-6 0H9a3 3 0 0 1-6 0H1v-2h2V5Zm2 2v7h8V7H5Zm11 7h3v-2.8L18.2 12H16v2ZM6 18a1 1 0 1 0 0-2 1 1 0 0 0 0 2Zm10 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z",
        user: "M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5Z",
        warehouse: "M3 9 12 3l9 6v12h-4v-7H7v7H3V9Zm4 0v3h10V9l-5-3.3L7 9Zm2 7h2v5H9v-5Zm4 0h2v5h-2v-5Z",
        warning: "M12 3 2 21h20L12 3Zm1 14h-2v2h2v-2Zm0-7h-2v6h2v-6Z"
      }
    }
  },
  computed: {
    ...mapGetters({
      todoTotal: "todoTotal",
      todoCounts: "todoCounts",
      todoRecent: "todoRecent",
      todoProviderStates: "todoProviderStates",
      permissions: "permissions",
      permissionRoutes: "permission_routes"
    }),
    todoCards() {
      return (this.todoRecent || [])
        .filter(item => item && ["approval", "execution", "returned", "risk", "personal"].includes(item.category))
        .slice(0, 4)
        .map(item => Object.assign({}, item, {
          icon: item.category === "risk"
            ? "warning"
            : item.category === "execution"
              ? "truck"
              : item.category === "returned"
                ? "return"
                : item.category === "personal"
                  ? "user"
                  : "check"
        }))
    },
    hasUnknownTodoProviders() {
      return Object.values(this.todoProviderStates || {}).some(provider =>
        provider && (provider.unknown || provider.status === "unknown")
      )
    },
    hasPendingTodoProviders() {
      return Object.values(this.todoProviderStates || {}).some(provider => provider && provider.status === "pending")
    },
    hasStaleTodoProviders() {
      return Object.values(this.todoProviderStates || {}).some(provider => provider && provider.stale)
    },
    hasFreshTodoProviders() {
      const providers = Object.values(this.todoProviderStates || {})
      return providers.length > 0 && providers.every(provider =>
        provider && provider.status === "fresh" && !provider.stale && !provider.error
      )
    },
    todoEmptyTitle() {
      if (this.hasUnknownTodoProviders) return "待办数据暂时无法确认"
      if (this.hasPendingTodoProviders) return "待办加载中"
      if (this.hasStaleTodoProviders) return "缓存待办待刷新"
      if (!this.hasFreshTodoProviders) return "待办加载中"
      if (Number(this.todoTotal) > 0) return "还有待办未在预览中显示"
      return "暂无可处理待办"
    },
    todoEmptyMessage() {
      if (this.hasUnknownTodoProviders) return "部分来源不可用，暂时不能确认是否没有待办。"
      if (this.hasPendingTodoProviders) return "正在汇总审批、执行、退回、风险和个人事项。"
      if (this.hasStaleTodoProviders) return "当前仅有历史缓存结果，请稍后刷新。"
      if (!this.hasFreshTodoProviders) return "正在汇总审批、执行、退回、风险和个人事项。"
      if (Number(this.todoTotal) > 0) return "请进入全部待办查看当前权限范围内的完整列表。"
      return "当前账号没有符合权限和组织范围的待办。"
    },
    todoBadgeVisible() {
      return Number(this.todoTotal) > 0 || this.hasUnknownTodoProviders
    },
    todoBadgeText() {
      if (this.hasUnknownTodoProviders && !Number(this.todoTotal)) return "?"
      return Number(this.todoTotal) > 99 ? "99+" : String(Math.max(0, Number(this.todoTotal) || 0))
    },
    todoEntryAriaLabel() {
      if (this.hasUnknownTodoProviders) return `待办，${this.todoBadgeText}，部分来源数据未知`
      return `待办，${Math.max(0, Number(this.todoTotal) || 0)} 条`
    },
    noticeBadgeText() {
      return Number(this.noticeUnreadCount) > 99 ? "99+" : String(Math.max(0, Number(this.noticeUnreadCount) || 0))
    },
    todoHealthMessage() {
      if (this.hasUnknownTodoProviders) return "部分来源数据未知，当前待办数量不是完整结果"
      if (this.hasStaleTodoProviders) return "部分来源正在显示缓存待办"
      if (this.hasPendingTodoProviders) return "待办数据正在加载"
      return ""
    },
    availableRouteSet() {
      return buildAvailableRouteSet({
        constantRoutes,
        dynamicRoutes: this.permissionRoutes,
        permissions: this.permissions
      })
    },
    stateTitle() {
      return this.blockingState ? this.blockingState.title : ""
    },
    stateMessage() {
      return this.blockingState ? this.blockingState.description : ""
    },
    blockingState() {
      const message = String(this.errorMessage || "")
      if (/登录状态已过期|登录已过期|会话失效|请重新登录|未登录/i.test(message)) {
        return {
          type: "session-expired",
          title: "登录状态已过期",
          description: message || "请重新登录后继续操作。",
          actionId: "relogin",
          actionLabel: "重新登录",
          isError: true
        }
      }
      if (/权限|无权|未授权|Forbidden|NotPermission/i.test(message)) {
        return {
          type: "permission-error",
          title: "你没有查看此内容的权限",
          description: message || "当前账号没有该模块权限。",
          actionId: "switch-context",
          actionLabel: "切换组织",
          isError: true
        }
      }
      if (!this.selectedDeptId || /请先选择|未选择组织|选择店铺或仓库/i.test(message)) {
        if (!this.selectedDeptId || /请先选择|未选择组织|选择店铺或仓库/i.test(message)) {
          return {
            type: "context-required",
            title: "选择店铺或仓库后继续",
            description: message || "选择组织后才能查看库存工作台。",
            actionId: "select-context",
            actionLabel: "选择组织",
            isError: false
          }
        }
      }
      if (this.loading && !this.errorMessage) {
        return {
          type: "loading",
          title: "正在加载业务内容",
          description: "正在读取当前组织的销售、采购、库存和仓库任务",
          actionId: "",
          actionLabel: "",
          isError: false
        }
      }
      if (this.errorMessage) {
        return {
          type: "network-error",
          title: "暂时无法加载数据",
          description: this.errorMessage,
          actionId: "retry",
          actionLabel: "重试",
          isError: true
        }
      }
      return null
    },
    selectedDeptId() {
      const context = this.getSelectedContext ? this.getSelectedContext() : {}
      return context.selectedDeptId
    }
  },
  created() {
    startMobileViewportSync()
    this.refreshTodos()
    this.loadNoticeCount()
    this.loadWorkbench()
  },
  beforeDestroy() {
    stopMobileViewportSync()
  },
  methods: {
    todoCount(category) {
      const value = Number(this.todoCounts && this.todoCounts[category])
      const count = Number.isFinite(value) ? Math.max(0, value) : 0
      if (this.hasUnknownTodoProviders) return count ? `${count}+` : "?"
      if (this.hasPendingTodoProviders) return count ? `${count}+` : "…"
      return count
    },
    sourceLabel(source) {
      return { approval: "统一审批", inventory: "库存", oa: "OA", system: "系统" }[source] || "未知来源"
    },
    categoryLabel(category) {
      return { approval: "待审批", execution: "待执行", returned: "退回修改", risk: "风险", personal: "个人事项" }[category] || "待处理"
    },
    todoTone(category) {
      if (["risk", "returned"].includes(category)) return "red"
      if (category === "execution") return "blue"
      if (category === "personal") return "violet"
      return "teal"
    },
    refreshTodos() {
      return this.$store.dispatch("todo/refreshSummaries").catch(() => {})
    },
    loadNoticeCount() {
      return listNoticeTop().then(response => {
        const rows = response && Array.isArray(response.data) ? response.data : []
        const unread = response && response.unreadCount !== undefined
          ? Number(response.unreadCount)
          : rows.filter(item => item && !item.isRead).length
        this.noticeUnreadCount = Number.isFinite(unread) ? Math.max(0, unread) : null
      }).catch(() => {
        this.noticeUnreadCount = null
      })
    },
    openTodo(item) {
      return navigateTodo(item, {
        platform: "mobile",
        getPermissions: () => this.permissions,
        getAvailableRouteSet: () => this.availableRouteSet,
        getCurrentContext: () => getSelectedDeptContext(),
        getReturnRoute: () => this.$route,
        beginContextLease: beginTodoContextLease,
        rollbackContextLease: payload => rollbackTodoContextLease({
          expectedLeaseId: payload && payload.contextLease && payload.contextLease.leaseId,
          getCurrentContext: () => getSelectedDeptContext(),
          setSelectedDept,
          clearSelectedDept
        }),
        setSelectedDept,
        router: this.$router,
        showContextNotice: payload => this.$message.info(formatTodoContextSwitchNotice(payload)),
        showError: message => this.$message.error(message),
        refreshSummaries: () => this.$store.dispatch("todo/refreshSummaries")
      })
    },
    openPath(path) {
      if (path && path !== this.$route.path) this.$router.push(path).catch(() => {})
    },
    openTodoCenter() {
      this.$router.push({
        path: "/mobile/todo",
        query: { returnTo: this.$route.fullPath }
      }).catch(() => {})
    },
    getSelectedContext() {
      const context = getSelectedDeptContext()
      const selectedDeptLabel = this.formatDeptLabel(context)
      return {
        selectedDeptId: context.deptId,
        selectedDeptName: selectedDeptLabel,
        selectedDeptType: context.deptType
      }
    },
    formatDeptLabel(context) {
      if (!context.deptName) return ""
      const prefix = context.isWarehouse ? "仓库" : context.isStore ? "门店" : "组织"
      return prefix + "：" + context.deptName
    },
    loadWorkbench() {
      const context = this.getSelectedContext()
      this.data = createEmptyWorkbenchData(context.selectedDeptName)
      this.applyMobileNavigation()
      this.errorMessage = ""
      this.partialMessage = ""

      if (!context.selectedDeptId) {
        this.$router.replace({ path: "/select-shop", query: { redirect: this.$route.fullPath } }).catch(() => {})
        return
      }

      this.loading = true
      fetchMobileInventoryWorkbench(context).then(raw => {
        this.data = mapWorkbenchResponse(raw, workbenchData)
        this.applyMobileNavigation()
        this.partialMessage = raw.errors && raw.errors.length > 0
          ? `${raw.errors.length} 个接口暂时不可用，已显示可获取的数据`
          : ""
      }).catch(() => {
        this.errorMessage = "库存服务暂不可用，请确认库存模块已启动后重试"
        this.data = createEmptyWorkbenchData(context.selectedDeptName, this.errorMessage)
        this.applyMobileNavigation()
      }).finally(() => {
        this.loading = false
      })
    },
    handleBlockingAction(actionId) {
      if (actionId === "relogin") {
        this.$store.dispatch("LogOut").then(() => {
          this.$router.push(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`).catch(() => {})
        }).catch(() => {
          this.$router.push("/login").catch(() => {})
        })
        return
      }
      if (actionId === "select-context" || actionId === "switch-context") {
        this.goSelectShop()
        return
      }
      this.loadWorkbench()
    },
    goSelectShop() {
      this.$router.push({ path: "/select-shop", query: { redirect: "/mobile/inventory" } }).catch(() => {})
    },
    applyMobileNavigation() {
      const context = this.getSelectedContext()
      this.data.quickActions = this.filterActionsByContext(this.mobileQuickActions, context)
      this.data.bottomNav = this.filterActionsByContext(this.mobileBottomNav, context).map(item => ({
        ...item,
        active: item.path === this.$route.path
      }))
    },
    filterActionsByContext(items, context) {
      return (items || []).filter(item => {
        return !this.isPurchasePath(item.path) || context.selectedDeptType === "WAREHOUSE"
      })
    },
    isPurchasePath(path) {
      return path === "/mobile/purchase"
    },
    canOpenPath(path) {
      if (this.isPurchasePath(path) && this.getSelectedContext().selectedDeptType !== "WAREHOUSE") {
        this.$message.warning("请选择仓库后再进入采购")
        return false
      }
      return true
    },
    openAction(action) {
      const path = action.path
      if (path && this.canOpenPath(path)) {
        this.$router.push(path).catch(() => {})
      }
    },
    openNav(item) {
      const path = item.path
      if (path && path !== this.$route.path && this.canOpenPath(path)) {
        this.$router.push(path).catch(() => {})
      }
    }
  }
}
</script>

<style scoped lang="scss">
.inventory-mobile-page {
  min-height: var(--mobile-viewport-height, 100dvh);
  background: var(--mobile-color-page);
  display: flex;
  justify-content: center;
  color: #050b16;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
}

.mobile-shell {
  position: relative;
  width: min(100%, 430px);
  height: var(--mobile-viewport-height, 100dvh);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow: hidden;
  background-image: none;
  background: var(--mobile-color-page);
  box-shadow: 0 22px 72px rgba(15, 23, 42, 0.18);
}

.mobile-shell::before {
  content: "";
  position: absolute;
  inset: 0;
  background: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  pointer-events: none;
}

.content-stage {
  position: relative;
  z-index: 1;
  height: var(--mobile-viewport-height, 100dvh);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 34px 26px 0;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

h1 {
  margin: 0 0 10px;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: -0.02em;
}

button {
  font: inherit;
  letter-spacing: 0;
}

.selector-pill,
.todo-entry-button,
.notify-button,
.glass-card,
.glass-action,
.bottom-nav {
  background: var(--mobile-color-surface, #fff);
  border: 1px solid var(--mobile-color-line, #dde2de);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.mobile-shell::before,
.glass-card,
.bottom-nav {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.glass-card,
.bottom-nav {
  background: var(--mobile-color-surface, #fff);
}

.selector-pill {
  border-radius: 24px;
  height: 45px;
  padding: 0 17px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #101827;
  font-size: 17px;
  font-weight: 800;
}

.selector-icon {
  width: 25px;
  height: 25px;
  color: #0ea5b4;
}

.selector-pill svg,
.todo-entry-button svg,
.notify-button svg {
  fill: currentColor;
}

.selector-pill .chevron {
  width: 17px;
  height: 17px;
  color: #2f3742;
}

.header-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.todo-entry-button,
.notify-button {
  width: 55px;
  height: 55px;
  border-radius: 50%;
  color: #050b16;
  display: grid;
  place-items: center;
  position: relative;
  padding: 0;
}

.todo-entry-button svg,
.notify-button svg {
  width: 26px;
  height: 26px;
}

.notify-button .notice-badge {
  position: absolute;
  right: -5px;
  top: -5px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  box-sizing: border-box;
  border-radius: 999px;
  background: #2f80ff;
  border: 2px solid #fff;
  color: #fff;
  font-size: 9px;
  line-height: 14px;
  font-weight: 900;
  text-align: center;
}

.todo-entry-button .todo-badge {
  position: absolute;
  right: -5px;
  top: -5px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  box-sizing: border-box;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  line-height: 18px;
  font-weight: 900;
  text-align: center;
}

.state-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-radius: 18px;
  margin: -4px 0 16px;
  padding: 12px 14px;
  color: #203047;
}

.state-card strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 900;
}

.state-card p {
  margin: 4px 0 0;
  color: #627086;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 700;
}

.state-card button {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 13px;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: #dc2626;
  font-size: 13px;
  font-weight: 900;
}

.state-card.error {
  border-color: rgba(248, 113, 113, 0.38);
}

.hero-card {
  border-radius: 25px;
  overflow: hidden;
  margin-bottom: 24px;
}

.hero-main {
  position: relative;
  min-height: 93px;
  display: grid;
  grid-template-columns: 70px 1fr 124px;
  gap: 14px;
  align-items: center;
  padding: 18px 18px 15px;
}

.hero-icon {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #0fb2c5;
  background: rgba(255, 255, 255, 0.54);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72), 0 8px 22px rgba(64, 101, 131, 0.12);
}

.hero-icon svg {
  width: 34px;
  height: 34px;
  fill: currentColor;
}

.hero-main h2 {
  margin: 0 0 7px;
  font-size: 23px;
  line-height: 1.2;
  font-weight: 900;
}

.hero-main p {
  margin: 0;
  color: #667080;
  font-size: 15px;
  font-weight: 600;
}

.hero-visual {
  display: none;
}

.metric-panel {
  margin: 0;
  border-radius: 16px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
  border: 1px solid var(--mobile-color-line, #dde2de);
}

.metric-cell {
  min-width: 0;
  padding: 15px 9px 14px;
  position: relative;
}

.metric-cell + .metric-cell::before {
  content: "";
  position: absolute;
  top: 17px;
  bottom: 17px;
  left: 0;
  width: 1px;
  background: rgba(143, 154, 169, 0.22);
}

.metric-label {
  color: #5f6877;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.metric-value {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #050b16;
}

.metric-value strong {
  font-size: 27px;
  line-height: 1;
  font-weight: 900;
}

.metric-value span {
  font-size: 13px;
  font-weight: 800;
}

.mini-icon {
  width: 18px;
  height: 18px;
  fill: currentColor;
  margin-left: auto;
}

.blue { color: #2678f2; }
.amber { color: #f59e0b; }
.red { color: #ef4444; }
.teal { color: #0ea5a4; }

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 4px 10px;
}

.section-head h2 {
  margin: 0;
  font-size: 21px;
  line-height: 1.2;
  font-weight: 900;
}

.section-head button {
  border: 0;
  background: transparent;
  color: #5c6470;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  font-size: 15px;
}

.section-head svg {
  width: 16px;
  height: 16px;
  fill: currentColor;
}

.todo-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -3px 4px 10px;
  color: #627086;
  font-size: 12px;
  font-weight: 700;
}

.todo-counts span {
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
}

.priority-card {
  border-radius: 24px;
  padding: 9px 14px;
}

.priority-empty {
  min-height: 92px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 6px;
  text-align: center;
  color: #66756d;
  font-size: 14px;
  font-weight: 800;
}

.priority-empty span {
  font-size: 12px;
  line-height: 1.4;
}

.priority-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 82px;
  padding: 8px 0;
}

.priority-row + .priority-row {
  border-top: 1px solid rgba(255, 255, 255, 0.64);
}

.thumb {
  height: 68px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--mobile-color-primary-soft, #e7f2ed);
  box-shadow: none;
  position: relative;
}

.thumb.boxes { background-position: 52% 48%; }
.thumb.pallet { background-position: 63% 58%; }
.thumb.scanner { background-position: 34% 64%; }
.thumb.shelf { background-position: 70% 28%; }

.thumb span {
  display: none;
}

.row-icon {
  width: 31px;
  height: 31px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.row-icon svg {
  width: 21px;
  height: 21px;
  fill: currentColor;
}

.row-copy {
  min-width: 0;
}

.row-copy h3 {
  margin: 0 0 7px;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 900;
  white-space: nowrap;
}

.row-copy p {
  margin: 0;
  color: #687384;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-col {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 78px;
}

.status-col > svg {
  width: 15px;
  height: 15px;
  fill: #7c8795;
}

.status-pill {
  --status-color: #111827;
  min-width: 62px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.72);
  color: #111827;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.status-pill::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--status-color);
}

.status-pill.blue { --status-color: #2678f2; }
.status-pill.amber { --status-color: #f59e0b; }
.status-pill.red { --status-color: #ef4444; }
.status-pill.teal { --status-color: #0ea5a4; }
.status-pill.violet { --status-color: #7c3aed; }

.action-title {
  margin-top: 24px;
  margin-bottom: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 9px;
}

.glass-action {
  height: 86px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: #050b16;
  padding: 0;
}

.glass-action svg {
  width: 32px;
  height: 32px;
  fill: currentColor;
}

.glass-action span {
  font-size: 16px;
  font-weight: 800;
  white-space: nowrap;
}

.bottom-nav {
  position: fixed;
  z-index: 8;
  left: 50%;
  bottom: max(14px, env(safe-area-inset-bottom));
  width: min(calc(100% - 34px), 396px);
  min-height: 68px;
  border-radius: 30px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  padding: 8px 8px 7px;
  transform: translateX(-50%);
}

.bottom-nav button {
  border: 0;
  background: transparent;
  color: #59616d;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0;
  min-width: 0;
}

.bottom-nav button.active {
  color: #2678f2;
}

.bottom-nav svg {
  width: 27px;
  height: 27px;
  fill: currentColor;
}

.bottom-nav span {
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

@media (max-width: 380px) {
  .content-stage {
    padding-left: 18px;
    padding-right: 18px;
  }

  h1 {
    font-size: 32px;
  }

  .hero-main {
    grid-template-columns: 60px 1fr;
  }

  .hero-visual {
    display: none;
  }

  .metric-panel {
    grid-template-columns: repeat(2, 1fr);
  }

  .metric-cell:nth-child(3)::before {
    display: none;
  }

  .todo-entry-button,
  .notify-button {
    width: 48px;
    height: 48px;
  }

  .priority-row {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .status-col {
    grid-column: 2;
    justify-content: flex-start;
  }

  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
