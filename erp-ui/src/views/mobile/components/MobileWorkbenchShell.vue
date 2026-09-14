<template>
  <div :class="['workbench-mobile-page', 'mobile-system-page', pageClass]">
    <main class="mobile-shell" :aria-label="profile.title">
      <section class="content-stage mobile-system-scroll" data-mobile-scroll-root>
        <header class="title-row mobile-system-topbar">
          <div class="mobile-system-topbar__copy">
            <h1 class="title-h1 mobile-system-topbar__title">{{ profile.title }}</h1>
            <button class="selector-pill" type="button" @click="goSelectShop">
              <span class="selector-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath(profile.selectorIcon)" /></svg>
              </span>
              <span>{{ selectorLabel }}</span>
              <svg class="chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5H7Z" /></svg>
            </button>
          </div>
          <div class="header-actions mobile-system-topbar__actions">
            <button class="todo-entry-button mobile-icon-button" type="button" :aria-label="todoEntryAriaLabel" @click="openTodoCenter">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath('check')" /></svg>
              <span v-if="todoBadgeVisible" class="todo-badge">{{ todoBadgeText }}</span>
            </button>
            <button class="notify-button mobile-icon-button" type="button" aria-label="通知" @click="openPath(noticePath)">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath('bell')" /></svg>
              <span v-if="noticeUnreadCount > 0" class="notice-badge">{{ noticeBadgeText }}</span>
            </button>
          </div>
        </header>

        <section v-if="routeNoticeMessage" class="glass-card mobile-redirect-banner mobile-alert mobile-alert--info">
          <strong>{{ routeNoticeTitle }}</strong>
          <span>{{ routeNoticeMessage }}</span>
        </section>

        <section v-if="summaryState.type === 'session-expired'" class="glass-card workbench-state mobile-data-state mobile-data-state--session-expired error" role="alert" aria-live="assertive">
          <strong>{{ summaryState.title }}</strong>
          <span>{{ summaryState.description }}</span>
          <button class="mobile-button mobile-button--secondary" type="button" @click="relogin">重新登录</button>
        </section>
        <section v-else-if="summaryState.type === 'context-required'" class="glass-card workbench-state mobile-data-state mobile-data-state--context-required">
          <strong>{{ summaryState.title }}</strong>
          <span>{{ summaryState.description }}</span>
          <button class="mobile-button mobile-button--primary" type="button" @click="goSelectShop">选择组织</button>
        </section>
        <section v-else-if="summaryState.type === 'permission-error'" class="glass-card workbench-state mobile-data-state mobile-data-state--permission-error error" role="alert" aria-live="assertive">
          <strong>{{ summaryState.title }}</strong>
          <span>{{ summaryState.description }}</span>
          <button class="mobile-button mobile-button--secondary" type="button" @click="goSelectShop">切换组织</button>
        </section>
        <section v-else-if="summaryState.type === 'network-error'" class="glass-card workbench-state mobile-data-state mobile-data-state--network-error error" role="alert" aria-live="assertive">
          <strong>{{ summaryState.title }}</strong>
          <span>{{ summaryState.description }}</span>
          <button class="mobile-button mobile-button--secondary" type="button" @click="loadWorkbenchSummary">重试</button>
        </section>
        <section v-else-if="summaryState.type === 'loading'" class="glass-card workbench-state mobile-data-state mobile-data-state--loading" role="status" aria-live="polite">
          <strong>{{ summaryState.title }}</strong>
          <span>{{ summaryState.description }}</span>
        </section>

        <template v-if="showWorkbenchContent">
          <section v-if="todoHealthMessage" class="glass-card mobile-todo-health mobile-alert mobile-alert--warning" role="status" aria-live="polite">
            <strong>待办数据提示</strong>
            <span>{{ todoHealthMessage }}</span>
          </section>

          <section class="section-head todo-title mobile-section-title">
            <div class="todo-heading-copy">
              <h2>{{ profile.todoTitle }}</h2>
              <div class="todo-category-counts" aria-label="待办分类数量">
                <span>审批 {{ countFor("approval") }}</span>
                <span>执行 {{ countFor("execution") }}</span>
                <span>退回 {{ countFor("returned") }}</span>
                <span>风险 {{ countFor("risk") }}</span>
                <span>个人 {{ countFor("personal") }}</span>
              </div>
            </div>
            <button type="button" @click="openTodoCenter">
              全部
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 6 6 6-6 6V6Z" /></svg>
            </button>
          </section>

          <section v-if="todoItems.length" class="glass-card todo-card mobile-system-panel mobile-system-panel--continuous">
            <button
              v-for="item in todoItems"
              :key="item.todoKey"
              class="todo-row"
              type="button"
              @click="openTodo(item)"
            >
              <div :class="['todo-mark', item.tone]">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath(item.icon)" /></svg>
              </div>
              <div class="todo-copy">
                <h3>{{ item.title || item.businessNo || '待处理事项' }}</h3>
                <p>{{ item.summary || item.businessNo || '请进入业务页面查看详情' }}</p>
              </div>
              <div class="todo-status">
                <strong class="mobile-status-chip mobile-status-chip--pending">{{ categoryLabel(item.category) }}</strong>
                <span>{{ sourceLabel(item.source) }}</span>
              </div>
            </button>
          </section>
          <section v-else class="glass-card empty-workbench-card mobile-data-state mobile-data-state--empty mobile-data-state--compact">
            <strong>{{ todoEmptyTitle }}</strong>
            <span>{{ todoEmptyMessage }}</span>
          </section>

          <section class="section-head action-title mobile-section-title">
            <h2>快捷操作</h2>
          </section>

          <section v-if="primaryQuickActions.length" class="quick-grid" aria-label="快捷操作">
            <button v-for="action in primaryQuickActions" :key="action.label" class="glass-action" type="button" @click="openAction(action)">
              <svg :class="action.tone" viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath(action.icon)" /></svg>
              <span>{{ action.label }}</span>
              <small>{{ action.summary }}</small>
            </button>
          </section>
          <section v-if="!primaryQuickActions.length && !overflowQuickActions.length" class="glass-card empty-workbench-card action-empty mobile-data-state mobile-data-state--empty mobile-data-state--compact">
            <strong>当前账号暂无可用快捷操作</strong>
            <span>需要开通对应模块权限后才会显示业务按钮。</span>
          </section>
          <details v-if="overflowQuickActions.length" class="glass-card action-overflow mobile-system-panel">
            <summary>
              <span>更多快捷操作</span>
              <small>{{ overflowQuickActions.length }} 项</small>
            </summary>
            <div class="overflow-action-grid">
              <button v-for="action in overflowQuickActions" :key="action.label" type="button" @click="openAction(action)">
                <svg :class="action.tone" viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath(action.icon)" /></svg>
                <span>{{ action.label }}</span>
              </button>
            </div>
          </details>

          <section v-if="profile.metrics && profile.metrics.length" class="glass-card hero-card mobile-system-panel metrics-panel">
            <div class="metric-panel">
              <div v-for="metric in profile.metrics" :key="metric.key" class="metric-cell" :title="metric.hint">
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
        <button v-for="item in bottomNavItems" :key="item.label" :class="{ active: item.active }" type="button" @click="openNav(item)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="getIconPath(item.icon)" /></svg>
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
import { clearSelectedDept, getSelectedDeptContext, setSelectedDept } from "@/utils/shopContext"
import { listNoticeTop } from "@/api/system/notice"
import { getMobileWorkbenchSummary } from "@/api/inventory/mobile"

const mobileNavigation = require("../mobileNavigation")
const mobileViewport = require("../mobileViewport")
const startMobileViewportSync = typeof mobileViewport.startMobileViewportSync === "function"
  ? mobileViewport.startMobileViewportSync
  : () => false
const stopMobileViewportSync = typeof mobileViewport.stopMobileViewportSync === "function"
  ? mobileViewport.stopMobileViewportSync
  : () => false
const {
  partitionMobileActions,
  resolveMobileWorkbenchSummaryState,
  resolveMobileWorkbenchTodos
} = require("../mobileExperience")
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const {
  applyWorkbenchSummaryToMetrics,
  createWorkbenchSummaryQuery,
  enrichWorkbenchAction,
  enrichWorkbenchNavItem,
  formatWorkbenchDeptLabel,
  getFallbackWorkbenchProfile,
  normalizeWorkbenchContextType,
  resolveWorkbenchTodoItems,
  workbenchCategoryLabel,
  workbenchSourceLabel
} = require("./mobileWorkbenchPolicy")

const getMobileContextProfile = typeof mobileNavigation.getMobileContextProfile === "function"
  ? mobileNavigation.getMobileContextProfile
  : null
const getMobileQuickActions = typeof mobileNavigation.getMobileQuickActions === "function"
  ? mobileNavigation.getMobileQuickActions
  : null
const getMobileBottomNav = typeof mobileNavigation.getMobileBottomNav === "function"
  ? mobileNavigation.getMobileBottomNav
  : null
const getMobileHomePath = typeof mobileNavigation.getMobileHomePath === "function"
  ? mobileNavigation.getMobileHomePath
  : null
const getMobileRouteAccessDecision = typeof mobileNavigation.getMobileRouteAccessDecision === "function"
  ? mobileNavigation.getMobileRouteAccessDecision
  : null
const isMobileBottomNavItemActive = typeof mobileNavigation.isMobileBottomNavItemActive === "function"
  ? mobileNavigation.isMobileBottomNavItemActive
  : null
const hasAnyMobilePermission = typeof mobileNavigation.hasAnyMobilePermission === "function"
  ? mobileNavigation.hasAnyMobilePermission
  : null

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

export default {
  name: "MobileWorkbenchShell",
  props: {
    contextType: {
      type: String,
      required: true
    }
  },
  data() {
    const context = getSelectedDeptContext()
    return {
      selectedDeptName: this.formatDeptLabel(context),
      selectedDeptId: context && context.deptId ? context.deptId : null,
      summaryContextRevision: 0,
      workbenchSummary: null,
      summaryLoading: false,
      summaryLoaded: false,
      summaryError: null,
      noticeUnreadCount: null,
      iconPaths: {
        bag: "M7 7V6a5 5 0 0 1 10 0v1h3v14H4V7h3Zm2 0h6V6a3 3 0 0 0-6 0v1Zm-3 2v10h12V9H6Z",
        bell: "M12 22a2.8 2.8 0 0 0 2.7-2h-5.4A2.8 2.8 0 0 0 12 22Zm7-6-2-2v-4a5 5 0 0 0-4-4.9V3h-2v2.1A5 5 0 0 0 7 10v4l-2 2v2h14v-2Z",
        cart: "M7 18a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm10 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM3 4h2l2.2 10.2A2 2 0 0 0 9.2 16H18v-2H9.2L8.8 12H18.5L21 6H7.5L7 4H3Z",
        check: "M18 4h-2.2A3 3 0 0 0 13 2h-2a3 3 0 0 0-2.8 2H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2Zm-7 0h2a1 1 0 0 1 1 1H10a1 1 0 0 1 1-1Zm5.2 7.4-5.1 5.1-3.1-3.1 1.4-1.4 1.7 1.7 3.7-3.7 1.4 1.4Z",
        cloud: "M6 5h5l2 2h5a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm0 2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-5.8l-2-2H6Z",
        cube: "m12 2 8 4v12l-8 4-8-4V6l8-4Zm0 2.2L7 6.7l5 2.5 5-2.5-5-2.5ZM6 8.5v8.3l5 2.5V11L6 8.5Zm7 10.8 5-2.5V8.5L13 11v8.3Z",
        document: "M6 2h9l5 5v15H6V2Zm8 2v5h4l-4-5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        home: "M3 11 12 3l9 8v10h-6v-6H9v6H3V11Z",
        inbound: "M11 3h2v9l3.5-3.5 1.4 1.4L12 15.8 6.1 9.9l1.4-1.4L11 12V3ZM5 18h14v2H5v-2Z",
        purchase: "M6 3h12v4h2v14H4V7h2V3Zm2 4h8V5H8v2Zm2 5h4v2h-4v4H8v-4H4v-2h4V8h2v4Z",
        return: "M8 7h8a5 5 0 0 1 0 10H7v-2h9a3 3 0 0 0 0-6H8v4L3 8l5-5v4Z",
        sales: "M5 4h11l3 3v13H5V4Zm10 1.5V8h2.5L15 5.5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        search: "M10.5 4a6.5 6.5 0 0 1 5.1 10.5l4 4-1.4 1.4-4-4A6.5 6.5 0 1 1 10.5 4Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z",
        store: "M4 7h16l-1 5H5L4 7Zm2 7h12v7H6v-7Zm2 2v3h3v-3H8Zm5 0v3h3v-3h-3ZM5 3h14l1 3H4l1-3Z",
        transfer: "M7 6h9.2l-2.6-2.6L15 2l5 5-5 5-1.4-1.4L16.2 8H7V6Zm10 12H7.8l2.6 2.6L9 22l-5-5 5-5 1.4 1.4L7.8 16H17v2Z",
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
      userPermissions: "permissions",
      permissionRoutes: "permission_routes"
    }),
    normalizedContextType() {
      return normalizeWorkbenchContextType(this.contextType)
    },
    pageClass() {
      return this.normalizedContextType === "WAREHOUSE" ? "warehouse-context" : "store-context"
    },
    profile() {
      const fallback = getFallbackWorkbenchProfile(this.normalizedContextType)
      const profile = getMobileContextProfile ? getMobileContextProfile(this.normalizedContextType) : {}
      const merged = Object.assign({}, fallback, profile || {})
      const state = this.summaryState || {}
      const summary = state.type === "ready" ? state.summary : this.workbenchSummary
      return Object.assign({}, merged, {
        metrics: applyWorkbenchSummaryToMetrics(merged.metrics, summary)
      })
    },
    homePath() {
      return getMobileHomePath
        ? getMobileHomePath(this.normalizedContextType, this.userPermissions)
        : this.profile.homePath
    },
    userPermissions() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return Array.isArray(getters.permissions) ? getters.permissions : []
    },
    featureState() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return {
        driveEnabled: getters.driveEnabled === true,
        businessFeatures: getters.businessFeatures || {}
      }
    },
    noticePath() {
      return mobileNavigation.MOBILE_ROUTES && mobileNavigation.MOBILE_ROUTES.notice
        ? mobileNavigation.MOBILE_ROUTES.notice
        : "/mobile/notice"
    },
    todoPath() {
      return mobileNavigation.MOBILE_ROUTES && mobileNavigation.MOBILE_ROUTES.todo
        ? mobileNavigation.MOBILE_ROUTES.todo
        : "/mobile/todo"
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
      return "当前账号暂无可处理待办"
    },
    todoEmptyMessage() {
      if (this.hasUnknownTodoProviders) return "部分来源不可用，暂时不能确认是否没有待办。"
      if (this.hasPendingTodoProviders) return "正在汇总审批、执行、退回、风险和个人事项。"
      if (this.hasStaleTodoProviders) return "当前仅有历史缓存结果，请稍后刷新。"
      if (!this.hasFreshTodoProviders) return "正在汇总审批、执行、退回、风险和个人事项。"
      if (Number(this.todoTotal) > 0) return "请进入全部待办查看当前权限范围内的完整列表。"
      return "已按当前角色权限隐藏不可用业务入口。"
    },
    todoHealthMessage() {
      if (this.hasUnknownTodoProviders) return "部分来源暂时未知，当前数量不是完整结果"
      if (this.hasStaleTodoProviders) return "部分来源正在显示缓存待办，请稍后刷新"
      if (this.hasPendingTodoProviders) return "待办数据正在加载"
      return ""
    },
    selectorLabel() {
      return this.selectedDeptName || this.profile.selectorFallback
    },
    routeNoticeTitle() {
      const reason = firstQueryValue(this.$route.query && this.$route.query.mobileRedirectReason)
      if (reason === "unavailable") return "移动端暂未开放"
      if (reason === "context-mismatch") return "已切换到当前组织"
      return "移动端提示"
    },
    routeNoticeMessage() {
      const message = firstQueryValue(this.$route.query && this.$route.query.mobileRedirectMessage)
      return message ? String(message) : ""
    },
    summaryState() {
      return resolveMobileWorkbenchSummaryState({
        contextId: this.selectedDeptId,
        loading: this.summaryLoading,
        summary: this.summaryLoaded ? this.workbenchSummary : null,
        error: this.summaryError
      })
    },
    showWorkbenchContent() {
      const type = this.summaryState && this.summaryState.type
      return type === "ready" || type === "loading"
    },
    quickActions() {
      const actions = getMobileQuickActions
        ? getMobileQuickActions(this.normalizedContextType, this.userPermissions, this.featureState)
        : this.profile.quickActions
      const source = Array.isArray(actions) ? actions : this.profile.quickActions
      return source.map(this.enrichAction)
    },
    quickActionGroups() {
      return partitionMobileActions(this.quickActions, 4)
    },
    primaryQuickActions() {
      return this.quickActionGroups.primary
    },
    overflowQuickActions() {
      return this.quickActionGroups.overflow
    },
    todoItems() {
      return resolveWorkbenchTodoItems(this.todoRecent)
    },
    bottomNavItems() {
      const nav = getMobileBottomNav
        ? getMobileBottomNav(this.normalizedContextType, this.userPermissions, this.featureState)
        : this.profile.bottomNav
      const source = Array.isArray(nav) ? nav : this.profile.bottomNav

      return source.map(item => {
        const enriched = this.enrichNavItem(item)
        return Object.assign({}, enriched, {
          active: isMobileBottomNavItemActive
            ? isMobileBottomNavItemActive(enriched.path, this.$route.path)
            : enriched.path === this.$route.path || (enriched.path === this.homePath && this.$route.path === this.homePath)
        })
      })
    },
    availableRouteSet() {
      return buildAvailableRouteSet({
        constantRoutes,
        dynamicRoutes: this.permissionRoutes,
        permissions: this.userPermissions
      })
    }
  },
  watch: {
    "$store.getters.id"() { this.reloadWorkbenchContext() },
    "$store.getters.token"() { this.reloadWorkbenchContext() },
    userPermissions() { this.reloadWorkbenchContext() },
    contextType() { this.reloadWorkbenchContext() }
  },
  created() {
    this._workbenchContextChanged = () => this.reloadWorkbenchContext()
    window.addEventListener("erp:dept-changed", this._workbenchContextChanged)
    startMobileViewportSync()
    this.loadWorkbenchSummary()
    this.$store.dispatch("todo/refreshSummaries").catch(() => {})
    this.loadNoticeCount()
  },
  beforeDestroy() {
    window.removeEventListener("erp:dept-changed", this._workbenchContextChanged)
    this.workbenchOperationScope().deactivate()
    stopMobileViewportSync()
  },
  deactivated() { this.workbenchOperationScope().deactivate() },
  activated() {
    this.workbenchOperationScope().activate()
    this.reloadWorkbenchContext()
  },
  methods: {
    workbenchOperationScope() {
      if (!this._workbenchOperationScope) this._workbenchOperationScope = createUiOperationScope(() => ({
        actorId: String(this.$store.getters.id || ""),
        deptId: String((getSelectedDeptContext() || {}).deptId || ""),
        contextType: this.contextType,
        revision: this.summaryContextRevision
      }))
      return this._workbenchOperationScope
    },
    reloadWorkbenchContext() {
      this.summaryContextRevision += 1
      this.workbenchOperationScope().invalidate()
      this.workbenchSummary = null
      this.noticeUnreadCount = null
      this.loadWorkbenchSummary()
      this.loadNoticeCount()
    },
    countFor(category) {
      const value = Number(this.todoCounts && this.todoCounts[category])
      const count = Number.isFinite(value) ? Math.max(0, value) : 0
      if (this.hasUnknownTodoProviders) return count ? `${count}+` : "?"
      if (this.hasPendingTodoProviders) return count ? `${count}+` : "…"
      return count
    },
    enrichAction(action) {
      return enrichWorkbenchAction(action, this.normalizedContextType, this.homePath)
    },
    canShowWorkbenchItem(item) {
      if (!hasAnyMobilePermission) return true
      return hasAnyMobilePermission(item && item.permissions, this.userPermissions)
    },
    enrichNavItem(item) {
      return enrichWorkbenchNavItem(item, this.normalizedContextType, this.profile.bottomNav, this.homePath)
    },
    formatDeptLabel(context) {
      return formatWorkbenchDeptLabel(context)
    },
    sourceLabel(source) {
      return workbenchSourceLabel(source)
    },
    categoryLabel(category) {
      return workbenchCategoryLabel(category)
    },
    openTodo(item) {
      return navigateTodo(item, {
        platform: "mobile",
        getPermissions: () => this.userPermissions,
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
    loadNoticeCount() {
      const scope = this.workbenchOperationScope()
      const operation = scope.begin("notice")
      return listNoticeTop().then(response => {
        if (!scope.isCurrent(operation)) return
        const rows = response && Array.isArray(response.data) ? response.data : []
        const unread = response && response.unreadCount !== undefined
          ? Number(response.unreadCount)
          : rows.filter(item => item && !item.isRead).length
        this.noticeUnreadCount = Number.isFinite(unread) ? Math.max(0, unread) : null
      }).catch(() => {
        if (!scope.isCurrent(operation)) return
        this.noticeUnreadCount = null
      })
    },
    loadWorkbenchSummary() {
      const scope = this.workbenchOperationScope()
      const operation = scope.begin("summary")
      const context = getSelectedDeptContext()
      this.selectedDeptId = context && context.deptId ? context.deptId : null
      this.selectedDeptName = this.formatDeptLabel(context)
      if (!context || !context.deptId) {
        this.workbenchSummary = null
        this.summaryLoading = false
        this.summaryLoaded = false
        this.summaryError = null
        return
      }
      this.workbenchSummary = null
      this.summaryLoading = true
      this.summaryLoaded = false
      this.summaryError = null
      const query = createWorkbenchSummaryQuery(context)
      return getMobileWorkbenchSummary(query).then(response => {
        if (!scope.isCurrent(operation)) return null
        const result = response && response.data
        if (result && (String(result.selectedDeptId) !== String(context.deptId) || result.selectedDeptType !== context.deptType)) throw new Error("工作台组织已变化，请刷新")
        this.workbenchSummary = response && response.data ? response.data : null
        this.summaryLoaded = true
        return this.workbenchSummary
      }).catch(error => {
        if (!scope.isCurrent(operation)) return null
        this.workbenchSummary = null
        this.summaryError = error
        return null
      }).finally(() => {
        if (!scope.isCurrent(operation)) return
        this.summaryLoading = false
      })
    },
    getIconPath(icon) {
      return this.iconPaths[icon] || this.iconPaths.cube
    },
    relogin() {
      this.$store.dispatch("LogOut").then(() => {
        this.$router.push(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`).catch(() => {})
      }).catch(() => {
        this.$router.push("/login").catch(() => {})
      })
    },
    goSelectShop() {
      this.$router.push({ path: "/select-shop", query: { redirect: this.homePath } }).catch(() => {})
    },
    openAction(action) {
      if (action && action.path) {
        this.openRoute({ path: action.path, query: action.query })
      }
    },
    openNav(item) {
      if (item && item.path && item.path !== this.$route.path) {
        this.openRoute({ path: item.path, query: item.query })
      }
    },
    openPath(path) {
      this.openRoute(path)
    },
    openTodoCenter() {
      this.openRoute({
        path: this.todoPath,
        query: { returnTo: this.$route.fullPath }
      })
    },
    openRoute(route) {
      if (!route) return
      const targetPath = typeof route === "string" ? route : route.path
      const accessDecision = this.getRouteAccessDecision(targetPath)
      if (accessDecision && accessDecision.path && accessDecision.path !== targetPath) {
        this.showRouteWarning(accessDecision.message)
        this.$router.push({
          path: accessDecision.path,
          query: {
            mobileRedirectReason: accessDecision.reason,
            mobileRedirectMessage: accessDecision.message,
            mobileRedirectFrom: targetPath
          }
        }).catch(() => {})
        return
      }
      if (typeof route === "string") {
        this.$router.push(route).catch(() => {})
        return
      }
      if (route.path) {
        this.$router.push({ path: route.path, query: route.query || {} }).catch(() => {})
      }
    },
    getRouteAccessDecision(path) {
      if (!path || String(path).indexOf("/mobile/") !== 0 || !getMobileRouteAccessDecision) {
        return null
      }
      return getMobileRouteAccessDecision(path, this.normalizedContextType, this.userPermissions, this.featureState)
    },
    showRouteWarning(message) {
      if (!message) return
      if (this.$message && this.$message.warning) {
        this.$message.warning(message)
      }
    }
  }
}
</script>

<style scoped lang="scss">
.workbench-mobile-page {
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

.warehouse-context .mobile-shell::before {
  background: none;
}

.content-stage {
  position: relative;
  z-index: 1;
  min-width: 0;
  height: var(--mobile-viewport-height, 100dvh);
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 34px 24px calc(148px + env(safe-area-inset-bottom));
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
}

.title-row > div {
  min-width: 0;
}

h1 {
  margin: 0 0 18px;
  font-size: 35px;
  line-height: 1.08;
  font-weight: 900;
  letter-spacing: 0;
}

button {
  font: inherit;
  letter-spacing: 0;
  cursor: pointer;
}

.selector-pill,
.todo-entry-button,
.notify-button,
.glass-card,
.glass-action,
.bottom-nav {
  background: var(--mobile-color-surface);
  border: 1px solid var(--mobile-color-line);
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
  background: var(--mobile-color-surface);
}

.title-row.mobile-system-topbar {
  margin: 0 -16px 14px;
  padding: calc(var(--mobile-safe-top) + 12px) 16px 12px;
  background: var(--mobile-color-surface);
  border-bottom: 1px solid var(--mobile-color-line);
}

h1,
.title-h1 {
  margin: 0 0 10px;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
}

.selector-pill {
  border-radius: 24px;
  min-height: 45px;
  max-width: 260px;
  padding: 9px 17px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #101827;
  font-size: 16px;
  font-weight: 800;
}

.selector-pill span:not(.selector-icon) {
  min-width: 0;
  line-height: 1.35;
  overflow-wrap: anywhere;
  white-space: normal;
}

.selector-icon {
  flex: 0 0 auto;
  width: 25px;
  height: 25px;
  color: var(--accent);
}

.selector-pill svg,
.todo-entry-button svg,
.notify-button svg {
  fill: currentColor;
}

.selector-pill .chevron {
  flex: 0 0 auto;
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
  flex: 0 0 auto;
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
  background: #2678f2;
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

.context-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-radius: 18px;
  margin-bottom: 16px;
  padding: 12px 14px;
  color: #203047;
}

.context-card strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 900;
  overflow-wrap: anywhere;
}

.context-card > div {
  min-width: 0;
}

.context-card p {
  margin: 4px 0 0;
  color: #4f5e72;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.mobile-redirect-banner {
  display: block;
  margin-bottom: 16px;
  padding: 12px 14px;
  color: #173b68;
}

.mobile-redirect-banner strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 900;
}

.mobile-redirect-banner span {
  display: block;
  margin-top: 5px;
  color: #355b78;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
}

.mobile-todo-health {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -2px 0 12px;
  padding: 10px 12px;
  border-radius: 14px;
  color: #9a6700;
  font-size: 12px;
}

.mobile-todo-health strong { flex: 0 0 auto; }

.context-card button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 0 13px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  color: var(--accent);
  font-size: 13px;
  font-weight: 900;
}

.hero-card {
  border-radius: 25px;
  overflow: hidden;
  margin-bottom: 22px;
}

.hero-main {
  position: relative;
  min-height: 104px;
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) 112px;
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
  color: var(--accent);
  background: rgba(255, 255, 255, 0.54);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.72), 0 8px 22px rgba(64, 101, 131, 0.12);
}

.hero-icon svg {
  width: 34px;
  height: 34px;
  fill: currentColor;
}

.hero-copy {
  min-width: 0;
}

.hero-copy h2 {
  margin: 0 0 7px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
  overflow-wrap: anywhere;
}

.hero-copy p {
  margin: 0;
  color: #505c6d;
  font-size: 14px;
  line-height: 1.4;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.hero-visual {
  align-self: stretch;
  border-radius: 18px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0.1)),
    url("~@/assets/images/mobile-inventory-tea-room-bg.jpg");
  background-size: cover;
  background-position: center;
  opacity: 0.8;
  overflow: hidden;
}

.warehouse-context .hero-visual {
  background-position: 62% center;
}

.hero-visual span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.68), transparent);
}

.metric-panel {
  margin: 0 12px 12px;
  border-radius: 22px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.62);
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
  color: #4f5968;
  font-size: 13px;
  line-height: 1.3;
  font-weight: 800;
  overflow-wrap: anywhere;
  white-space: normal;
}

.metric-value {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #050b16;
}

.metric-value strong {
  font-size: 26px;
  line-height: 1;
  font-weight: 900;
}

.metric-value span {
  font-size: 13px;
  font-weight: 800;
}

.mini-icon {
  width: 17px;
  height: 17px;
  fill: currentColor;
  margin-left: auto;
}

.blue { color: #2678f2; }
.amber { color: #f59e0b; }
.red { color: #ef4444; }
.teal { color: #0ea5a4; }
.violet { color: #7c3aed; }

.store-context {
  --accent: #2678f2;
}

.warehouse-context {
  --accent: #d97706;
}

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

.todo-heading-copy {
  min-width: 0;
}

.todo-category-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  color: #667085;
  font-size: 11px;
  line-height: 1.2;
  font-weight: 800;
}

.section-head button {
  border: 0;
  background: transparent;
  color: #5c6470;
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 4px;
  font-size: 15px;
  font-weight: 700;
}

.section-head svg {
  width: 16px;
  height: 16px;
  fill: currentColor;
}

.todo-card {
  border-radius: 24px;
  padding: 8px 14px;
  margin-bottom: 16px;
}

.empty-workbench-card {
  border-radius: 22px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-workbench-card strong {
  color: #111827;
  font-size: 16px;
  line-height: 1.25;
  font-weight: 900;
}

.empty-workbench-card span {
  color: #687384;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
}

.todo-title + .empty-workbench-card {
  margin-bottom: 16px;
}

.workbench-state {
  margin-bottom: 16px;
  border-radius: 22px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.workbench-state strong {
  color: #111827;
  font-size: 16px;
  line-height: 1.25;
  font-weight: 900;
}

.workbench-state span {
  color: #687384;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
}

.workbench-state.error strong {
  color: #b45309;
}

.workbench-state button {
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid rgba(38, 120, 242, 0.2);
  border-radius: 18px;
  color: var(--accent);
  background: rgba(255, 255, 255, 0.64);
  font-size: 14px;
  font-weight: 900;
}

.todo-row {
  width: 100%;
  min-height: 74px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 58px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
}

.todo-row + .todo-row {
  border-top: 1px solid rgba(255, 255, 255, 0.64);
}

.todo-mark {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.todo-mark svg {
  width: 23px;
  height: 23px;
  fill: currentColor;
}

.todo-copy {
  min-width: 0;
}

.todo-copy h3 {
  margin: 0 0 7px;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 900;
  overflow-wrap: anywhere;
  white-space: normal;
}

.todo-copy p {
  margin: 0;
  color: #505d70;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
  overflow-wrap: anywhere;
  white-space: normal;
}

.todo-status {
  min-width: 0;
  max-width: 58px;
  text-align: right;
  color: #111827;
  overflow-wrap: anywhere;
}

.todo-status strong {
  display: block;
  font-size: 23px;
  line-height: 1;
  font-weight: 900;
}

.todo-status span {
  display: block;
  margin-top: 4px;
  color: #505d70;
  font-size: 13px;
  line-height: 1.2;
  font-weight: 800;
}

.action-title {
  margin-top: 23px;
  margin-bottom: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 9px;
}

.glass-action {
  min-height: 92px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #050b16;
  padding: 10px 4px;
}

.glass-action svg {
  width: 31px;
  height: 31px;
  fill: currentColor;
}

.glass-action span {
  max-width: 100%;
  font-size: 15px;
  line-height: 1.2;
  font-weight: 900;
  overflow-wrap: anywhere;
  white-space: normal;
}

.glass-action small {
  max-width: 100%;
  color: #505d70;
  font-size: 13px;
  line-height: 1.3;
  font-weight: 800;
  overflow-wrap: anywhere;
  white-space: normal;
}

.action-overflow {
  margin-top: 12px;
  border-radius: 20px;
  padding: 0 14px 12px;
}

.action-overflow summary {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #273548;
  cursor: pointer;
  font-size: 14px;
  font-weight: 900;
}

.action-overflow summary small {
  color: #505d70;
  font-size: 13px;
  font-weight: 800;
}

.overflow-action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.overflow-action-grid button {
  min-width: 0;
  min-height: 46px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #273548;
  background: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  font-weight: 900;
  text-align: left;
}

.overflow-action-grid svg {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  fill: currentColor;
}

.overflow-action-grid span {
  min-width: 0;
  overflow-wrap: anywhere;
  white-space: normal;
}

.board-title {
  margin-top: 23px;
}

.flow-card {
  border-radius: 22px;
  padding: 10px 15px;
}

.flow-row {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  padding: 9px 0;
  align-items: start;
}

.flow-row + .flow-row {
  border-top: 1px solid rgba(255, 255, 255, 0.58);
}

.flow-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 5px rgba(255, 255, 255, 0.56);
}

.flow-row h3 {
  margin: 0 0 4px;
  font-size: 15px;
  line-height: 1.2;
  font-weight: 900;
}

.flow-row p {
  margin: 0;
  color: #505d70;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 700;
}

.bottom-nav {
  position: fixed;
  z-index: 8;
  left: 50%;
  bottom: max(10px, env(safe-area-inset-bottom));
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
  min-height: 44px;
}

.bottom-nav button.active {
  color: var(--accent);
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

@supports (height: 100dvh) {
  .workbench-mobile-page,
  .mobile-shell {
    min-height: 100dvh;
  }
}

@media (max-width: 390px) {
  .content-stage {
    padding-left: 18px;
    padding-right: 18px;
  }

  .title-row {
    gap: 10px;
  }

  .title-row > div {
    max-width: calc(100% - 58px);
  }

  h1 {
    font-size: 32px;
  }

  .todo-entry-button,
  .notify-button {
    width: 48px;
    height: 48px;
  }

  .todo-entry-button svg,
  .notify-button svg {
    width: 23px;
    height: 23px;
  }

  .selector-pill {
    max-width: 100%;
    padding: 0 14px;
    font-size: 15px;
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

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .todo-row {
    grid-template-columns: 38px minmax(0, 1fr) 50px;
    gap: 10px;
  }

  .glass-action {
    min-height: 86px;
  }
}

@media (max-width: 390px), (max-height: 700px) {
  .content-stage {
    padding-top: 24px;
  }

  .title-row {
    margin-bottom: 10px;
  }

  h1 {
    margin-bottom: 12px;
  }

  .context-card {
    margin-bottom: 12px;
    padding: 10px 12px;
  }

  .context-card p {
    font-size: 13px;
  }

  .hero-card {
    margin-bottom: 16px;
    border-radius: 22px;
  }

  .hero-main {
    min-height: 84px;
    gap: 10px;
    padding: 14px;
  }

  .hero-icon {
    width: 52px;
    height: 52px;
  }

  .hero-icon svg {
    width: 29px;
    height: 29px;
  }

  .hero-copy h2 {
    font-size: 20px;
  }

  .hero-copy p {
    font-size: 13px;
  }

  .metric-cell {
    padding: 10px 8px;
  }

  .metric-value {
    margin-top: 5px;
  }

  .metric-value strong {
    font-size: 22px;
  }

  .bottom-nav {
    bottom: max(10px, env(safe-area-inset-bottom));
    min-height: 60px;
    padding: 6px 8px;
  }

  .bottom-nav svg {
    width: 23px;
    height: 23px;
  }

  .bottom-nav span {
    font-size: 13px;
  }
}

@media (max-width: 380px) {
  .title-row > div {
    max-width: calc(100% - 58px);
  }

  .notify-button {
    width: 48px;
    height: 48px;
  }
}

@media (max-width: 380px), (max-height: 700px) {
  .hero-card {
    margin-bottom: 16px;
  }

  .metric-cell {
    padding: 10px 8px;
  }

  .bottom-nav {
    min-height: 60px;
  }
}

@media (max-width: 320px) {
  .content-stage {
    padding-left: 14px;
    padding-right: 14px;
  }

  .title-row {
    gap: 8px;
  }

  h1 {
    font-size: 29px;
  }

  .selector-pill {
    width: 100%;
    box-sizing: border-box;
  }

  .context-card {
    align-items: stretch;
    flex-direction: column;
  }

  .context-card button {
    width: 100%;
  }

  .hero-main {
    grid-template-columns: 52px minmax(0, 1fr);
  }

  .todo-row {
    grid-template-columns: 38px minmax(0, 1fr) 44px;
    gap: 8px;
  }

  .todo-status {
    max-width: 44px;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* Progressive mobile redesign: align the workbench with the shared feature shell. */
.workbench-mobile-page {
  min-height: var(--mobile-viewport-height);
  background: var(--mobile-color-page);
  color: var(--mobile-color-ink);
}

.mobile-shell {
  width: min(100%, 480px);
  min-height: var(--mobile-viewport-height);
  height: var(--mobile-viewport-height);
  background: var(--mobile-color-page);
  box-shadow: none;
}

.content-stage {
  height: var(--mobile-viewport-height);
  padding: 0 16px;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);
  background: var(--mobile-color-page);
  scroll-padding-bottom: calc(var(--mobile-bottom-nav-total) + var(--mobile-keyboard-inset) + 16px);
}

.title-row {
  position: relative;
  z-index: 3;
  align-items: center;
  margin: 0 -16px 14px;
  padding: calc(var(--mobile-safe-top) + 12px) 16px 12px;
  border-bottom: 1px solid var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.title-row > div:first-child {
  flex: 1 1 auto;
  max-width: none;
}

h1 {
  margin: 0 0 7px;
  color: var(--mobile-color-ink);
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.selector-pill,
.todo-entry-button,
.notify-button,
.glass-card,
.glass-action,
.bottom-nav {
  border: 1px solid var(--mobile-color-line);
  background: var(--mobile-color-surface);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.selector-pill {
  min-height: var(--mobile-control-height);
  max-width: 100%;
  padding: 8px 10px;
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-ink);
  font-size: 13px;
}

.selector-icon {
  width: 18px;
  height: 18px;
  color: var(--mobile-color-primary);
}

.selector-pill .chevron {
  width: 14px;
  height: 14px;
  color: var(--mobile-color-muted);
}

.header-actions {
  flex: 0 0 auto;
  gap: 8px;
}

.todo-entry-button,
.notify-button {
  width: 44px;
  height: 44px;
  border-radius: var(--mobile-radius-md);
  color: var(--mobile-color-ink);
}

.todo-entry-button svg,
.notify-button svg {
  width: 22px;
  height: 22px;
}

.notify-button .notice-badge {
  background: var(--mobile-color-info);
}

.todo-entry-button .todo-badge {
  background: var(--mobile-color-danger);
}

.store-context,
.warehouse-context {
  --accent: var(--mobile-color-primary);
}

.glass-card {
  border-radius: var(--mobile-radius-md);
}

.section-head {
  margin: 16px 2px 8px;
}

.section-head h2 {
  color: var(--mobile-color-ink);
  font-size: 16px;
  letter-spacing: -0.01em;
}

.section-head button,
.todo-category-counts {
  color: var(--mobile-color-muted);
}

.section-head button {
  font-size: 13px;
}

.mobile-redirect-banner,
.mobile-todo-health,
.context-card,
.empty-workbench-card,
.workbench-state {
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: var(--mobile-radius-md);
}

.context-card {
  color: var(--mobile-color-ink);
}

.context-card p,
.empty-workbench-card span,
.workbench-state span,
.mobile-redirect-banner span {
  color: var(--mobile-color-muted);
}

.context-card button,
.workbench-state button {
  min-height: 44px;
  border: 1px solid rgba(11, 107, 83, 0.28);
  border-radius: var(--mobile-radius-sm);
  background: var(--mobile-color-primary-soft);
  color: var(--mobile-color-primary);
}

.hero-card {
  margin-bottom: 16px;
  border-radius: var(--mobile-radius-md);
}

.hero-main {
  min-height: 0;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 11px;
  padding: 12px;
}

.hero-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
  box-shadow: none;
}

.hero-icon.blue {
  color: var(--mobile-color-info);
  background: var(--mobile-color-info-soft);
}

.hero-icon.amber {
  color: var(--mobile-color-warning);
  background: var(--mobile-color-warning-soft);
}

.hero-icon svg {
  width: 22px;
  height: 22px;
}

.hero-copy h2 {
  margin-bottom: 4px;
  color: var(--mobile-color-ink);
  font-size: 16px;
}

.hero-copy p {
  color: var(--mobile-color-muted);
  font-size: 13px;
}

.hero-visual {
  display: none;
}

.metric-panel {
  margin: 0 12px 12px;
  border: 1px solid var(--mobile-color-line);
  border-radius: var(--mobile-radius-sm);
  background: var(--mobile-color-surface-soft);
  box-shadow: none;
}

.metric-cell {
  padding: 12px 10px;
  border-left: 1px solid var(--mobile-color-line);
}

.metric-cell:first-child {
  border-left: 0;
}

.metric-cell + .metric-cell::before {
  display: none;
}

.metric-label {
  color: var(--mobile-color-muted);
  font-size: 12px;
}

.metric-value {
  color: var(--mobile-color-ink);
}

.metric-value strong {
  font-size: 22px;
}

.todo-card {
  margin-bottom: 12px;
  padding: 0;
  border-radius: var(--mobile-radius-md);
}

.todo-row {
  min-height: 78px;
  grid-template-columns: 40px minmax(0, 1fr) 64px;
  gap: 10px;
  padding: 13px 14px;
  background: var(--mobile-color-surface);
}

.todo-row + .todo-row {
  border-top-color: var(--mobile-color-line);
}

.todo-mark {
  width: 36px;
  height: 36px;
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
  box-shadow: none;
}

.todo-mark.blue {
  color: var(--mobile-color-info);
  background: var(--mobile-color-info-soft);
}

.todo-mark.amber {
  color: var(--mobile-color-warning);
  background: var(--mobile-color-warning-soft);
}

.todo-mark.red {
  color: var(--mobile-color-danger);
  background: var(--mobile-color-danger-soft);
}

.todo-mark.violet {
  color: #7c3aed;
  background: #f3efff;
}

.todo-mark svg {
  width: 21px;
  height: 21px;
}

.todo-copy h3 {
  margin-bottom: 4px;
  color: var(--mobile-color-ink);
  font-size: 15px;
}

.todo-copy p,
.todo-status span {
  color: var(--mobile-color-muted);
}

.todo-status {
  max-width: 64px;
}

.todo-status strong {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--mobile-color-primary-soft);
  color: var(--mobile-color-primary);
  font-size: 12px;
  line-height: 1;
}

.quick-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.glass-action {
  min-height: 56px;
  grid-template-columns: 24px minmax(0, 1fr);
  grid-template-rows: auto auto;
  display: grid;
  align-items: center;
  gap: 2px 10px;
  padding: 10px 12px;
  border-radius: var(--mobile-radius-md);
  color: var(--mobile-color-ink);
  text-align: left;
}

.glass-action svg {
  grid-row: 1 / span 2;
  width: 23px;
  height: 23px;
  color: var(--mobile-color-primary);
}

.glass-action span {
  font-size: 14px;
}

.glass-action small {
  color: var(--mobile-color-muted);
  font-size: 12px;
}

.action-overflow,
.flow-card {
  border-radius: var(--mobile-radius-md);
}

.action-overflow {
  padding-right: 12px;
  padding-left: 12px;
}

.action-overflow summary,
.action-overflow summary small,
.overflow-action-grid button {
  color: var(--mobile-color-muted);
}

.overflow-action-grid button {
  border-color: var(--mobile-color-line);
  border-radius: var(--mobile-radius-sm);
  background: var(--mobile-color-surface-soft);
}

.flow-card {
  padding: 0 14px;
}

.flow-row {
  padding: 12px 0;
}

.flow-row + .flow-row {
  border-top-color: var(--mobile-color-line);
}

.flow-dot {
  box-shadow: none;
}

.flow-row p {
  color: var(--mobile-color-muted);
}

.bottom-nav {
  z-index: 80;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  min-height: var(--mobile-bottom-nav-total);
  padding: 6px var(--mobile-safe-right) var(--mobile-safe-bottom) var(--mobile-safe-left);
  transform: none;
  border: 0;
  border-top: 1px solid var(--mobile-color-line);
  border-radius: 0;
  background: var(--mobile-color-surface);
  box-shadow: 0 -8px 20px rgba(23, 33, 29, 0.05);
}

.bottom-nav button {
  color: var(--mobile-color-muted);
}

.bottom-nav button.active {
  color: var(--mobile-color-primary);
}

.bottom-nav svg {
  width: 23px;
  height: 23px;
}

@media (max-width: 390px) {
  .metric-cell {
    border-top: 1px solid var(--mobile-color-line);
    border-left: 0;
  }

  .metric-cell:nth-child(even) {
    border-left: 1px solid var(--mobile-color-line);
  }

  .metric-cell:nth-child(-n + 2) {
    border-top: 0;
  }
}

/* Motion-library-inspired polish, translated for a high-frequency ERP surface. */
.selector-pill {
  border-color: rgba(11, 107, 83, 0.18);
  background: var(--mobile-color-primary-soft);
  box-shadow: 0 4px 12px rgba(11, 107, 83, 0.07);
}

.todo-entry-button,
.notify-button {
  border-color: rgba(203, 211, 206, 0.8);
  box-shadow: var(--mobile-shadow-control);
}

:is(.glass-card, .glass-action) {
  border-color: rgba(203, 211, 206, 0.82);
  box-shadow: var(--mobile-shadow-card);
}

.todo-card,
.empty-workbench-card,
.action-overflow,
.metrics-panel {
  overflow: hidden;
}

.quick-grid .glass-action:first-child {
  border-color: rgba(11, 107, 83, 0.78);
  background: var(--mobile-color-primary);
  color: #fff;
  box-shadow: var(--mobile-shadow-primary);
}

.quick-grid .glass-action:first-child svg,
.quick-grid .glass-action:first-child small {
  color: rgba(255, 255, 255, 0.88);
}

.quick-grid .glass-action:first-child span {
  color: #fff;
}

.metrics-panel {
  border-color: rgba(11, 107, 83, 0.13);
  background: var(--mobile-color-surface);
}

.metrics-panel .metric-panel {
  margin: 0;
  border: 0;
  border-radius: inherit;
  background: transparent;
}

.metrics-panel .metric-value strong {
  color: var(--mobile-color-primary-strong);
}

@media (hover: hover) and (pointer: fine) {
  .glass-action:hover,
  .todo-row:hover,
  .selector-pill:hover,
  .todo-entry-button:hover,
  .notify-button:hover {
    transform: translateY(-1px);
    filter: brightness(1.01);
  }
}

@media (prefers-reduced-motion: no-preference) {
  .quick-grid :is(.glass-action) {
    animation: mobile-surface-enter 220ms var(--mobile-ease-spring) both;
  }

  .quick-grid .glass-action:nth-child(2) { animation-delay: 32ms; }
  .quick-grid .glass-action:nth-child(3) { animation-delay: 64ms; }
  .quick-grid .glass-action:nth-child(4) { animation-delay: 96ms; }
}
</style>
