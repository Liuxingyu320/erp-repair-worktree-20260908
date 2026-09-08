<template>
  <div class="app-container home-page desktop-home-page">
    <section class="content-card home-hero" aria-label="桌面工作台概览">
      <div class="hero-main">
        <div class="hero-kicker-row">
          <span class="hero-kicker"><i class="el-icon-data-board"></i> 智慧经营工作台</span>
          <span class="hero-date"><i class="el-icon-date"></i> {{ todayLabel }} · {{ weekdayLabel }}</span>
        </div>
        <div class="hero-copy">
          <h1>{{ greeting }}，{{ displayName }}</h1>
          <p>从关键事项开始，高效推进今天的经营与协同工作。</p>
        </div>
        <div class="hero-context">
          <span class="context-icon"><i class="el-icon-office-building"></i></span>
          <span class="context-copy">
            <small>当前组织</small>
            <strong>{{ currentDeptLabel }}</strong>
          </span>
          <button type="button" class="context-switch" @click="goTo('/select-shop')">
            切换 <i class="el-icon-arrow-right"></i>
          </button>
        </div>
      </div>

      <aside class="hero-status" aria-label="今日待办概览">
        <div class="hero-status__head">
          <span><i class="el-icon-finished"></i> 今日待办</span>
          <span class="status-live"><i></i> 实时概览</span>
        </div>
        <div class="hero-status__value">
          <strong :class="{ 'is-text-status': todoStatusIsText }">{{ todoTotalDisplay }}</strong>
          <span>{{ todoStatusUnit }}</span>
        </div>
        <p>{{ todoSummaryHint }}</p>
        <div class="todo-mini-grid" aria-label="待办分类统计">
          <span><small>审批</small><strong>{{ todoMetric('approval') }}</strong></span>
          <span><small>执行</small><strong>{{ todoMetric('execution') }}</strong></span>
          <span><small>退回</small><strong>{{ todoMetric('returned') }}</strong></span>
          <span><small>风险</small><strong>{{ todoMetric('risk') }}</strong></span>
          <span><small>个人</small><strong>{{ todoMetric('personal') }}</strong></span>
        </div>
        <div class="hero-actions">
          <el-button type="primary" icon="el-icon-finished" @click="goTo('/workbench/todo')">进入待办</el-button>
          <el-button icon="el-icon-refresh" @click="refreshPage">刷新状态</el-button>
        </div>
        <span class="version-text">ERP v{{ version }}</span>
      </aside>
    </section>

    <section class="summary-grid" aria-label="关键入口">
      <button
        v-for="item in summaryCards"
        :key="item.label"
        type="button"
        :class="['summary-card', `summary-card--${item.tone}`]"
        :aria-label="`${item.label}：${item.value}，${item.hint}`"
        @click="goTo(item.path)"
      >
        <span :class="['summary-icon', item.tone]">
          <i :class="item.icon"></i>
        </span>
        <span class="summary-content">
          <span class="summary-topline">
            <span class="summary-label">{{ item.label }}</span>
            <span class="summary-arrow"><i class="el-icon-right"></i></span>
          </span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </span>
      </button>
    </section>

    <section class="home-columns">
      <div class="content-card quick-card">
        <div class="card-title-row">
          <div>
            <span class="section-eyebrow">常用功能</span>
            <h2>快速进入业务模块</h2>
          </div>
          <span class="card-meta">{{ availableQuickLinks.length }} 个可用入口</span>
        </div>
        <div class="quick-grid">
          <button
            v-for="link in availableQuickLinks"
            :key="link.path"
            type="button"
            :class="['quick-link', `quick-link--${link.tone || 'indigo'}`]"
            :aria-label="`${link.title}：${link.description}`"
            @click="goTo(link.path)"
          >
            <span class="quick-icon"><i :class="link.icon"></i></span>
            <span class="quick-copy">
              <strong>{{ link.title }}</strong>
              <small>{{ link.description }}</small>
            </span>
            <span class="quick-arrow"><i class="el-icon-arrow-right"></i></span>
          </button>
        </div>
      </div>

      <div class="content-card guide-card">
        <div class="card-title-row">
          <div>
            <span class="section-eyebrow">今日建议</span>
            <h2>先处理高频事项</h2>
          </div>
          <el-button type="text" @click="goTo('/workbench/todo')">查看全部 <i class="el-icon-arrow-right"></i></el-button>
        </div>
        <ul class="guide-list">
          <li v-for="item in guideItems" :key="item.title">
            <button
              type="button"
              class="guide-item"
              :disabled="!item.todo && !item.path"
              @click="handleGuide(item)"
            >
              <span :class="['guide-indicator', item.tone]"><i></i></span>
              <span class="guide-copy">
                <strong>{{ item.title }}</strong>
                <small>{{ item.description }}</small>
              </span>
              <span :class="['guide-action', { muted: !item.todo && !item.path }]">
                {{ item.action }} <i v-if="item.todo || item.path" class="el-icon-arrow-right"></i>
              </span>
            </button>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script>
import { mapGetters } from "vuex"
import { constantRoutes } from "@/router"
import { buildAvailableRouteSet } from "@/utils/todoRouteResolver"
import { navigateTodo } from "@/utils/todoNavigator"
import { beginTodoContextLease, formatTodoContextSwitchNotice, rollbackTodoContextLease } from "@/utils/todoContextLease"
import { clearSelectedDept, getSelectedDeptContext, setSelectedDept } from "@/utils/shopContext"
import { visibleDashboardEntries } from "@/utils/dashboardNavigation"

export default {
  name: "Index",
  data() {
    return {
      version: "3.6.8",
      quickLinks: [
        { title: "选择组织", description: "切换门店或仓库上下文", icon: "el-icon-office-building", tone: "teal", path: "/select-shop", always: true },
        { title: "商品资料", description: "维护商品与分类资料", icon: "el-icon-goods", tone: "violet", storePath: "/cangku/product", warehousePath: "/cangku/product", productCatalog: true },
        { title: "库存查询", description: "查看库存与预警状态", icon: "el-icon-data-analysis", tone: "blue", storePath: "/inventory/stock", warehousePath: "/cangku/stock" },
        { title: "销售管理", description: "处理销售单据流转", icon: "el-icon-shopping-cart-full", tone: "rose", storePath: "/inventory/sales", storeOnly: true },
        { title: "采购管理", description: "跟进采购与入库进度", icon: "el-icon-s-order", tone: "amber", warehousePath: "/cangku/purchase", warehouseOnly: true },
        { title: "我的待办", description: "集中处理审批、执行与风险事项", icon: "el-icon-finished", tone: "indigo", path: "/workbench/todo", always: true }
      ]
    }
  },
  computed: {
    ...mapGetters([
      "nickName",
      "name",
      "sidebarRouters",
      "permission_routes",
      "permissions",
      "todoTotal",
      "todoCounts",
      "todoRecent",
      "todoProviderStates"
    ]),
    displayName() {
      return this.nickName || this.name || "欢迎回来"
    },
    greeting() {
      const hour = new Date().getHours()
      if (hour < 11) return "早上好"
      if (hour < 14) return "中午好"
      if (hour < 18) return "下午好"
      return "晚上好"
    },
    todayLabel() {
      const today = new Date()
      return `${today.getMonth() + 1}月${today.getDate()}日`
    },
    weekdayLabel() {
      return ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"][new Date().getDay()]
    },
    selectedDeptContext() {
      return getSelectedDeptContext()
    },
    isWarehouseContext() {
      return this.selectedDeptContext.isWarehouse
    },
    isStoreContext() {
      return this.selectedDeptContext.isStore
    },
    currentDeptLabel() {
      const context = this.selectedDeptContext
      if (!context.deptName) {
        return "未选择组织"
      }
      const prefix = context.isWarehouse ? "仓库" : context.isStore ? "门店" : "组织"
      return `${prefix}：${context.deptName}`
    },
    summaryCards() {
      const cards = [
        {
          label: "组织上下文",
          value: this.selectedDeptContext.deptName || "待选择",
          hint: this.selectedDeptContext.deptName ? "业务数据将按当前组织加载" : "进入业务前先选择门店或仓库",
          icon: "el-icon-office-building",
          tone: "teal",
          path: "/select-shop",
          always: true
        },
        {
          label: "库存协同",
          value: "库存查询",
          hint: this.isWarehouseContext ? "查看当前仓库库存、预警与变动" : this.isStoreContext ? "查看当前门店库存、预警与变动" : "选择组织后查看库存、预警与变动",
          icon: "el-icon-data-line",
          tone: "blue",
          path: this.resolveContextRoute({ storePath: "/inventory/stock", warehousePath: "/cangku/stock" })
        },
        {
          label: "审批流转",
          value: this.todoTotalDisplay,
          hint: this.todoSummaryHint,
          icon: "el-icon-finished",
          tone: "amber",
          path: "/workbench/todo",
          always: true
        }
      ]
      return visibleDashboardEntries(cards, this.todoAvailableRouteSet)
    },
    hasUnknownTodoProviders() {
      return Object.values(this.todoProviderStates || {}).some(provider =>
        provider.unknown === true || provider.status === "unknown"
      )
    },
    hasPendingTodoProviders() {
      return Object.values(this.todoProviderStates || {}).some(provider => provider.status === "pending")
    },
    todoTotalDisplay() {
      if (this.hasPendingTodoProviders) return "加载中"
      if (!this.hasUnknownTodoProviders) return this.todoTotal
      return this.todoTotal > 0 ? `${this.todoTotal}+` : "部分数据未知"
    },
    todoStatusUnit() {
      return /^\d+\+?$/.test(String(this.todoTotalDisplay)) ? "项待处理" : "待办状态"
    },
    todoStatusIsText() {
      return !/^\d+\+?$/.test(String(this.todoTotalDisplay))
    },
    todoSummaryHint() {
      const counts = this.todoCounts || {}
      const summary = `审批 ${counts.approval || 0} · 执行 ${counts.execution || 0} · 退回 ${counts.returned || 0} · 风险 ${counts.risk || 0} · 个人 ${counts.personal || 0}`
      if (this.hasPendingTodoProviders) return "正在加载待办数据"
      return this.hasUnknownTodoProviders ? `${summary} · 部分数据未知` : summary
    },
    todoAvailableRouteSet() {
      return buildAvailableRouteSet({
        constantRoutes,
        dynamicRoutes: this.permission_routes,
        permissions: this.permissions
      })
    },
    visibleRoutePaths() {
      const paths = new Set()
      const normalizePath = (path) => (path || "").replace(/\/+/g, "/")
      const walk = (routes = [], parentPath = "") => {
        routes.forEach(route => {
          const routePath = route.path && route.path.startsWith("/")
            ? route.path
            : normalizePath(`${parentPath}/${route.path || ""}`)
          if (routePath) {
            paths.add(routePath)
          }
          if (route.children) {
            walk(route.children, routePath)
          }
        })
      }
      walk(this.sidebarRouters || [])
      return paths
    },
    availableQuickLinks() {
      const links = this.quickLinks
        .map(link => this.contextualizeQuickLink(link))
        .filter(link => this.isQuickLinkVisible(link))
      return visibleDashboardEntries(links, this.todoAvailableRouteSet)
    },
    guideItems() {
      const recent = (this.todoRecent || []).slice(0, 3)
      if (recent.length) {
        return recent.map(todo => ({
          title: todo.title || todo.businessNo || "待处理事项",
          description: `${this.todoCategoryLabel(todo.category)} · ${todo.deptName || "个人事项"}`,
          tone: todo.priority === "urgent" ? "red" : todo.priority === "important" ? "amber" : "blue",
          action: "处理",
          todo
        }))
      }
      return [{
        title: this.hasPendingTodoProviders ? "待办加载中" : this.hasUnknownTodoProviders ? "部分数据未知" : "暂无待办",
        description: this.hasPendingTodoProviders
          ? "正在获取各业务来源的待办数据。"
          : this.hasUnknownTodoProviders ? "部分来源尚未成功加载，请刷新后再查看。" : "当前没有需要优先处理的事项。",
        tone: this.hasPendingTodoProviders || this.hasUnknownTodoProviders ? "amber" : "teal",
        action: this.hasPendingTodoProviders ? "同步中" : this.hasUnknownTodoProviders ? "查看" : "已清空",
        path: this.hasUnknownTodoProviders ? "/workbench/todo" : ""
      }]
    }
  },
  methods: {
    contextualizeQuickLink(link) {
      const resolved = Object.assign({}, link, { path: this.resolveContextRoute(link) })
      if (resolved.productCatalog && this.isStoreContext) {
        resolved.title = "查看商品资料"
        resolved.description = "查询商品、编码和规格"
      }
      return resolved
    },
    resolveContextRoute(link) {
      if (!link) return ""
      if (this.isWarehouseContext && link.warehousePath) {
        return link.warehousePath
      }
      if (this.isStoreContext && link.storePath) {
        return link.storePath
      }
      return link.path || link.storePath || link.warehousePath || ""
    },
    isQuickLinkVisible(link) {
      if (link.storeOnly && !this.isStoreContext) return false
      if (link.warehouseOnly && !this.isWarehouseContext) return false
      return link.always || this.visibleRoutePaths.has(link.path)
    },
    todoCategoryLabel(category) {
      return { approval: "待审批", execution: "待执行", returned: "退回修改", risk: "风险提醒", personal: "个人事项" }[category] || "待处理"
    },
    todoMetric(category) {
      return (this.todoCounts && this.todoCounts[category]) || 0
    },
    handleGuide(item) {
      if (!item.todo) {
        this.goTo(item.path)
        return
      }
      return navigateTodo(item.todo, {
        platform: "desktop",
        getPermissions: () => this.permissions,
        getAvailableRouteSet: () => this.todoAvailableRouteSet,
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
    goTo(path) {
      if (!path || this.$route.path === path) return
      return this.$router.push(path).catch(() => {
        this.$message.error("页面暂时无法打开，请刷新后重试")
        return false
      })
    },
    refreshPage() {
      window.location.reload()
    }
  }
}
</script>

<style scoped lang="scss">
.home-page {
  --home-ink: #242320;
  --home-muted: #78736d;
  --home-border: #ded9d0;
  --home-primary: #252421;
  --home-primary-strong: #171715;
  --home-primary-soft: #eeebe5;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
}

.home-hero {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(340px, 0.7fr);
  gap: clamp(24px, 4vw, 56px);
  min-height: 226px;
  overflow: hidden;
  padding: clamp(28px, 3vw, 42px) !important;
  border: 1px solid #ded9d0 !important;
  border-radius: var(--erp-radius-lg, 16px) !important;
  color: var(--home-ink);
  background-color: #fbf8f3 !important;
  background-image:
    linear-gradient(90deg, rgba(255, 253, 249, 0.99) 0%, rgba(255, 253, 249, 0.97) 47%, rgba(255, 253, 249, 0.78) 72%, rgba(255, 253, 249, 0.64) 100%),
    url("~@/assets/images/desktop-login-tea-room-bg.jpg") !important;
  background-position: center, center right !important;
  background-size: auto, cover !important;
  box-shadow: 0 14px 38px rgba(55, 50, 44, 0.08) !important;
  animation: home-rise var(--motion-duration-base) var(--motion-ease-enter) both;
}

.home-hero::before {
  position: absolute;
  z-index: -1;
  inset: 0;
  pointer-events: none;
  content: "";
  background-image:
    linear-gradient(rgba(97, 88, 78, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(97, 88, 78, 0.045) 1px, transparent 1px);
  background-size: 30px 30px;
  opacity: 0.48;
  -webkit-mask-image: linear-gradient(90deg, #000 0%, rgba(0, 0, 0, 0.52) 56%, transparent 82%);
  mask-image: linear-gradient(90deg, #000 0%, rgba(0, 0, 0, 0.52) 56%, transparent 82%);
}

.hero-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero-kicker-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.hero-kicker,
.hero-date {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  padding: 6px 11px;
  border: 1px solid #ded9d0;
  border-radius: 999px;
  color: #5e5953;
  background: rgba(255, 253, 249, 0.74);
  font-size: 12px;
  font-weight: 650;
  line-height: 1;
}

.hero-date {
  color: #78736d;
  background: rgba(238, 235, 229, 0.82);
  font-weight: 500;
}

.hero-copy h1 {
  margin: 0;
  color: var(--home-ink);
  font-size: clamp(30px, 3vw, 42px);
  font-weight: 760;
  line-height: 1.2;
  letter-spacing: -0.035em;
}

.hero-copy p {
  max-width: 600px;
  margin: 12px 0 0;
  color: var(--home-muted);
  font-size: 14px;
  line-height: 1.7;
}

.hero-context {
  width: min(100%, 560px);
  min-height: 64px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 26px;
  padding: 10px 12px;
  border: 1px solid rgba(201, 195, 186, 0.86);
  border-radius: 15px;
  background: rgba(255, 253, 249, 0.86);
  box-shadow: 0 8px 22px rgba(55, 50, 44, 0.045);
}

.context-icon {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: var(--home-primary);
  background: #eeebe5;
  box-shadow: none;
  font-size: 18px;
}

.context-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.context-copy small {
  color: #8d877f;
  font-size: 11px;
}

.context-copy strong {
  overflow: hidden;
  color: var(--home-ink);
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-switch {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 9px;
  border: 1px solid #d2ccc3;
  border-radius: 9px;
  color: var(--home-ink);
  background: #f4f0e9;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  transition: background var(--motion-duration-fast) var(--motion-ease-standard);
}

.context-switch:hover {
  background: #e9e4dc;
}

.context-switch:focus-visible {
  outline: 2px solid rgba(37, 36, 33, 0.64);
  outline-offset: 2px;
}

.hero-status {
  position: relative;
  align-self: stretch;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid rgba(222, 217, 208, 0.96);
  border-radius: var(--erp-radius-md, 12px);
  color: var(--home-ink);
  background: rgba(255, 253, 249, 0.94);
  box-shadow: 0 14px 32px rgba(55, 50, 44, 0.1);
}

.hero-status::before {
  display: none;
  content: none;
}

.hero-status__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #5e5953;
  font-size: 13px;
  font-weight: 700;
}

.hero-status__head > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.hero-status__head > span:first-child i {
  color: var(--home-primary);
  font-size: 16px;
}

.status-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8d877f;
  font-size: 11px;
  font-weight: 500;
}

.status-live i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.12);
}

.hero-status__value {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 16px;
}

.hero-status__value strong {
  color: var(--home-ink);
  font-size: clamp(30px, 3.2vw, 44px);
  font-weight: 780;
  line-height: 1;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
}

.hero-status__value span {
  color: #8d877f;
  font-size: 12px;
}

.hero-status > p {
  margin: 9px 0 0;
  color: #8d877f;
  font-size: 12px;
  line-height: 1.55;
}

.todo-mini-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.todo-mini-grid > span {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  padding: 8px 9px;
  border-radius: 9px;
  border: 1px solid #e7e1d8;
  background: #f6f2ec;
}

.todo-mini-grid small {
  color: #8d877f;
  font-size: 11px;
}

.todo-mini-grid strong {
  color: var(--home-primary-strong);
  font-size: 13px;
}

.hero-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 9px;
  margin-top: 16px;
}

.hero-actions ::v-deep .el-button {
  width: 100%;
  min-width: 0;
  margin-left: 0;
}

.version-text {
  align-self: flex-end;
  margin-top: 11px;
  color: #9a958e;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.section-eyebrow {
  display: inline-flex;
  align-items: center;
  margin-bottom: 7px;
  color: var(--home-primary);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 18px;
  animation: home-rise var(--motion-duration-slow) 40ms var(--motion-ease-enter) both;
}

.summary-card,
.quick-link {
  border: 1px solid var(--home-border);
  background: #ffffff;
  color: var(--home-ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition:
    border-color var(--motion-duration-fast) var(--motion-ease-standard),
    box-shadow var(--motion-duration-fast) var(--motion-ease-standard);
}

.summary-card:hover,
.quick-link:hover {
  border-color: #a9a198;
  box-shadow: var(--erp-shadow-card-hover, 0 14px 32px rgba(23, 33, 29, 0.075));
}

.summary-card:focus-visible,
.quick-link:focus-visible,
.guide-item:focus-visible {
  outline: 3px solid rgba(37, 36, 33, 0.18);
  outline-offset: 3px;
}

.summary-card {
  --card-accent: var(--home-primary);
  --card-soft: var(--home-primary-soft);
  position: relative;
  min-height: 132px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  padding: 20px;
  border-radius: var(--erp-radius-md, 12px);
  background: linear-gradient(135deg, #faf7f2 0%, #fffdf9 58%);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(55, 50, 44, 0.055));
}

.summary-card::before { display: none; content: none; }

.summary-card--teal {
  --card-accent: #5e5953;
  --card-soft: #eeebe5;
}

.summary-card--blue {
  --card-accent: #5e5953;
  --card-soft: #eeebe5;
}

.summary-card--amber {
  --card-accent: #5e5953;
  --card-soft: #eeebe5;
}

.summary-icon {
  width: 50px;
  height: 50px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 14px;
  font-size: 23px;
}

.summary-icon.teal {
  color: #4a4641;
  background: #eeebe5;
}

.summary-icon.blue {
  color: #4a4641;
  background: #eeebe5;
}

.summary-icon.amber {
  color: #4a4641;
  background: #eeebe5;
}

.summary-content,
.quick-copy,
.guide-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.summary-content {
  flex: 1;
}

.summary-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.summary-arrow {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: var(--card-accent);
  background: var(--card-soft);
  font-size: 12px;
  transition:
    color var(--motion-duration-fast) var(--motion-ease-standard),
    transform var(--motion-duration-fast) var(--motion-ease-standard);
}

.summary-card:hover .summary-arrow {
  transform: translateX(2px);
}

.summary-label,
.summary-content small,
.quick-link small,
.guide-list small {
  color: var(--home-muted);
  font-size: 12px;
  line-height: 1.45;
}

.summary-label {
  font-size: 12px;
  font-weight: 600;
}

.summary-content strong,
.quick-link strong,
.guide-list strong {
  color: var(--home-ink);
  font-size: 16px;
  line-height: 1.35;
}

.home-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(330px, 0.75fr);
  gap: 18px;
  animation: home-rise var(--motion-duration-slow) 60ms var(--motion-ease-enter) both;
}

.quick-card,
.guide-card {
  padding: 24px !important;
  border-radius: var(--erp-radius-lg, 16px) !important;
  background-image: linear-gradient(180deg, #fffdf9 0%, #faf7f2 100%);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(55, 50, 44, 0.055)) !important;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.card-title-row h2 {
  margin: 0;
  color: var(--home-ink);
  font-size: 19px;
  font-weight: 720;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.card-meta {
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--home-primary);
  background: var(--home-primary-soft);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.guide-card .card-title-row ::v-deep .el-button--text {
  flex: 0 0 auto;
  font-size: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 12px;
}

.quick-link {
  --quick-color: var(--home-primary);
  --quick-soft: var(--home-primary-soft);
  min-height: 94px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 15px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8f4ee 0%, #fffdf9 62%);
}

.quick-link--teal {
  --quick-color: #5e5953;
  --quick-soft: #eeebe5;
}

.quick-link--violet {
  --quick-color: #5e5953;
  --quick-soft: #eeebe5;
}

.quick-link--blue {
  --quick-color: #5e5953;
  --quick-soft: #eeebe5;
}

.quick-link--rose {
  --quick-color: #5e5953;
  --quick-soft: #eeebe5;
}

.quick-link--amber {
  --quick-color: #5e5953;
  --quick-soft: #eeebe5;
}

.quick-link--indigo {
  --quick-color: var(--home-primary);
  --quick-soft: var(--home-primary-soft);
}

.quick-icon {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px solid #ded9d0;
  color: var(--quick-color);
  background: var(--quick-soft);
  box-shadow: none;
  font-size: 20px;
}

.quick-link strong {
  font-size: 14px;
}

.quick-arrow {
  color: #aaa39a;
  font-size: 13px;
  transition:
    color var(--motion-duration-fast) var(--motion-ease-standard),
    transform var(--motion-duration-fast) var(--motion-ease-standard);
}

.quick-link:hover .quick-arrow {
  color: var(--quick-color);
  transform: translateX(2px);
}

.guide-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.guide-list li {
  border-top: 1px solid #ebe6de;
}

.guide-list li:first-child {
  border-top: 0;
}

.guide-item {
  width: 100%;
  min-height: 76px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border: 0;
  border-radius: 12px;
  color: inherit;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background var(--motion-duration-fast) var(--motion-ease-standard);
}

.guide-item:not(:disabled):hover {
  background: linear-gradient(90deg, #f3efe8 0%, rgba(243, 239, 232, 0.36) 100%);
}

.guide-item:disabled {
  cursor: default;
}

.guide-indicator {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: var(--home-primary-soft);
}

.guide-indicator i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.guide-indicator.teal {
  background: #eeebe5;
}

.guide-indicator.teal i {
  background: #78736d;
  box-shadow: 0 0 0 4px rgba(120, 115, 109, 0.11);
}

.guide-indicator.blue {
  background: #eeebe5;
}

.guide-indicator.blue i {
  background: #78736d;
  box-shadow: 0 0 0 4px rgba(120, 115, 109, 0.11);
}

.guide-indicator.amber {
  background: #fff6dc;
}

.guide-indicator.amber i {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.12);
}

.guide-indicator.red {
  background: #fff0f1;
}

.guide-indicator.red i {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.11);
}

.guide-copy strong {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guide-copy small {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.guide-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--home-primary);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.guide-action.muted {
  color: #9a958e;
  font-weight: 500;
}

@keyframes home-rise {
  from {
    opacity: 0;
    transform: translateY(8px);
    filter: blur(var(--motion-enter-blur));
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@media (min-width: 992px) and (max-height: 820px) {
  .home-page {
    gap: 14px;
  }

  .home-hero {
    gap: 28px;
    padding: 22px 28px !important;
    border-radius: 18px !important;
  }

  .hero-kicker-row {
    margin-bottom: 10px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .hero-copy p {
    margin-top: 7px;
    line-height: 1.5;
  }

  .hero-context {
    min-height: 54px;
    margin-top: 16px;
    padding: 7px 10px;
  }

  .context-icon {
    width: 36px;
    height: 36px;
  }

  .hero-status {
    padding: 14px 16px;
    border-radius: 15px;
  }

  .hero-status__value {
    margin-top: 10px;
  }

  .hero-status__value strong {
    font-size: 30px;
  }

  .hero-status > p {
    margin-top: 6px;
  }

  .todo-mini-grid {
    gap: 6px;
    margin-top: 10px;
  }

  .todo-mini-grid > span {
    padding: 5px 7px;
  }

  .hero-actions {
    margin-top: 10px;
  }

  .version-text {
    margin-top: 7px;
  }

  .summary-grid {
    gap: 14px;
  }

  .summary-card {
    min-height: 108px;
    padding: 16px;
  }
}

@media (min-width: 992px) and (max-height: 820px) {
  .home-page {
    gap: 14px;
  }

  .home-hero {
    gap: 28px;
    padding: 22px 28px !important;
    border-radius: 18px !important;
  }

  .hero-kicker-row {
    margin-bottom: 10px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .hero-copy p {
    margin-top: 7px;
    line-height: 1.5;
  }

  .hero-context {
    min-height: 54px;
    margin-top: 16px;
    padding: 7px 10px;
  }

  .context-icon {
    width: 36px;
    height: 36px;
  }

  .hero-status {
    padding: 14px 16px;
    border-radius: 15px;
  }

  .hero-status__value {
    margin-top: 10px;
  }

  .hero-status__value strong {
    font-size: 30px;
  }

  .hero-status > p {
    margin-top: 6px;
  }

  .todo-mini-grid {
    gap: 6px;
    margin-top: 10px;
  }

  .todo-mini-grid > span {
    padding: 5px 7px;
  }

  .hero-actions {
    margin-top: 10px;
  }

  .version-text {
    margin-top: 7px;
  }

  .summary-grid {
    gap: 14px;
  }

  .summary-card {
    min-height: 108px;
    padding: 16px;
  }
}

@media (max-width: 1240px) {
  .quick-grid {
    grid-template-columns: repeat(2, minmax(190px, 1fr));
  }
}

@media (max-width: 1080px) {
  .home-hero,
  .home-columns {
    grid-template-columns: 1fr;
  }

  .hero-status {
    width: 100%;
  }

  .quick-grid {
    grid-template-columns: repeat(3, minmax(180px, 1fr));
  }
}

@media (max-width: 860px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .summary-card {
    min-height: 112px;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(170px, 1fr));
  }
}

@media (max-width: 620px) {
  .home-page {
    gap: 14px;
  }

  .home-hero {
    gap: 20px;
    min-height: 0;
    padding: 22px !important;
    border-radius: 18px !important;
  }

  .hero-kicker-row {
    align-items: flex-start;
    flex-direction: column;
    margin-bottom: 16px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-context {
    grid-template-columns: auto minmax(0, 1fr);
    margin-top: 20px;
  }

  .context-switch {
    grid-column: 1 / -1;
    justify-content: center;
  }

  .hero-status {
    padding: 17px;
  }

  .hero-actions,
  .quick-grid {
    grid-template-columns: 1fr;
  }

  .quick-card,
  .guide-card {
    padding: 18px !important;
  }

  .card-title-row {
    align-items: flex-start;
  }

  .card-meta {
    display: none;
  }

  .guide-item {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .guide-action {
    grid-column: 2;
    justify-self: start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-hero,
  .summary-grid,
  .home-columns {
    animation: none;
  }

  .context-switch,
  .summary-card,
  .summary-arrow,
  .quick-link,
  .quick-icon,
  .quick-arrow,
  .guide-item {
    transition: none;
  }
}
</style>
