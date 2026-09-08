<template>
  <div class="mobile-hr-page mobile-system-page">
    <main class="mobile-hr-shell" :aria-label="title">
      <div class="mobile-hr-background" :aria-hidden="modalOpen ? 'true' : null" :inert="modalOpen ? '' : null">
      <header class="mobile-hr-header">
        <button
          v-if="back"
          class="mobile-hr-back"
          type="button"
          aria-label="返回"
          @click="handleBack"
        >
          <i class="el-icon-arrow-left" aria-hidden="true" />
        </button>
        <div class="mobile-hr-heading">
          <slot name="header">
            <h1>{{ title }}</h1>
          </slot>
        </div>
      </header>

      <section v-if="loading" class="mobile-hr-state" role="status" aria-live="polite" aria-busy="true">
        <span class="mobile-hr-spinner" aria-hidden="true"></span>
        <strong>正在加载</strong>
        <p>请稍候，正在获取最新人事数据。</p>
      </section>

      <section v-else-if="errorMessage" class="mobile-hr-state is-error" role="alert" aria-live="assertive">
        <strong>加载失败</strong>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="$emit('retry')">重新加载</button>
      </section>

      <section v-else class="mobile-hr-content">
        <slot />
      </section>

      <footer v-if="$slots.footer && !loading && !errorMessage" class="mobile-hr-footer">
        <slot name="footer" />
      </footer>

      <nav v-if="showBottomNav" class="mobile-hr-nav mobile-system-bottom-nav" aria-label="手机底部导航">
        <button
          v-for="item in navigationItems"
          :key="item.path + ':' + item.label"
          :class="['mobile-hr-nav-item', { 'is-active': item.active, active: item.active }]"
          type="button"
          :aria-label="item.label"
          :aria-current="item.active ? 'page' : null"
          @click="openNavigation(item)"
        >
          <svg-icon :icon-class="item.icon || 'user'" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
      </div>
      <slot name="overlay" />
    </main>
  </div>
</template>

<script>
import { getSelectedDeptContext } from "@/utils/shopContext"
import { mobileHrErrorMessage } from "../mobileHrError"

const {
  getMobileHrBottomNav,
  getMobileRouteAccessDecision,
  getMobileRouteDefinition
} = require("../../mobileNavigation")

function messageFromError(error) {
  if (!error) return ""
  return mobileHrErrorMessage(error, "数据加载失败，请稍后重试")
}

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function navigationTarget(source) {
  if (typeof source === "string") return { path: source }
  if (!source || !source.path) return null
  const target = { path: source.path }
  if (source.query && Object.keys(source.query).length) {
    target.query = Object.assign({}, source.query)
  }
  return target
}

function isTaskView(query) {
  return firstQueryValue(query && query.view) === "tasks"
}

function isOnboardingRoute(path) {
  const root = "/mobile/hr/onboarding"
  return path === root || (typeof path === "string" && path.indexOf(root + "/") === 0)
}

function isNavigationActive(item, route) {
  if (!item || !route) return false
  if (item.path === "/mobile/hr/onboarding" && isOnboardingRoute(route.path)) {
    return isTaskView(item.query) === isTaskView(route.query)
  }
  return item.path === route.path
}

function isCurrentTarget(target, route) {
  if (!target || !route || target.path !== route.path) return false
  if (target.path === "/mobile/hr/onboarding" && isTaskView(target.query) !== isTaskView(route.query)) {
    return false
  }
  const targetQuery = target.query || {}
  const routeQuery = route.query || {}
  return Object.keys(targetQuery).every(key => {
    return String(firstQueryValue(targetQuery[key])) === String(firstQueryValue(routeQuery[key]))
  })
}

export default {
  name: "MobileHrShell",
  props: {
    title: { type: String, required: true },
    back: { type: [Boolean, String, Function], default: false },
    loading: { type: Boolean, default: false },
    error: { type: [String, Object, Error], default: "" },
    modalOpen: { type: Boolean, default: false }
  },
  computed: {
    userPermissions() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return Array.isArray(getters.permissions) ? getters.permissions : []
    },
    selectedDeptType() {
      const context = getSelectedDeptContext()
      return context && context.deptType ? context.deptType : "STORE"
    },
    errorMessage() {
      return messageFromError(this.error)
    },
    navigationItems() {
      const currentPath = this.$route && this.$route.path ? this.$route.path : ""
      const items = getMobileHrBottomNav(this.userPermissions)
        .filter(item => item && item.path && getMobileRouteDefinition(item.path))
      const route = { path: currentPath, query: (this.$route && this.$route.query) || {} }
      return items.map(item => Object.assign({}, item, { active: isNavigationActive(item, route) }))
    },
    isTaskFlowRoute() {
      const path = this.$route && this.$route.path ? this.$route.path : ""
      return /\/mobile\/hr\/onboarding\/(create|\d+(\/edit)?)/.test(path)
    },
    showBottomNav() {
      return !this.isTaskFlowRoute && this.navigationItems.length > 0
    }
  },
  watch: {
    showBottomNav: {
      immediate: true,
      handler(value) {
        if (typeof document === "undefined") return
        if (value) document.body.classList.remove("mobile-task-mode")
        else document.body.classList.add("mobile-task-mode")
      }
    }
  },
  beforeDestroy() {
    if (typeof document !== "undefined") {
      document.body.classList.remove("mobile-task-mode")
    }
  },
  methods: {
    handleBack() {
      if (typeof this.back === "function") {
        this.back()
        return
      }
      this.$emit("back")
      if (typeof this.back === "string") {
        this.openPath(this.back)
        return
      }
      if (this.$router) this.$router.back()
    },
    openNavigation(item) {
      if (!item || item.active) return
      this.openPath(navigationTarget(item))
    },
    openPath(source) {
      const target = navigationTarget(source)
      const path = target && target.path
      if (!path || !getMobileRouteDefinition(path) || isCurrentTarget(target, this.$route)) return
      const decision = getMobileRouteAccessDecision(path, this.selectedDeptType, this.userPermissions)
      if (decision && decision.path) {
        this.$emit("navigation-denied", decision)
        return
      }
      if (this.$router) {
        this.$router.push(target).catch(() => {})
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-hr-page {
  min-height: var(--mobile-viewport-height, 100dvh);
  color: var(--mobile-color-ink, #17211d);
  background: var(--mobile-color-page, #f4f5f2);
}

.mobile-hr-shell {
  width: 100%;
  max-width: 480px;
  min-height: var(--mobile-viewport-height, 100dvh);
  margin: 0 auto;
  padding-bottom: calc(var(--mobile-bottom-nav-total, 68px) + 16px);
  box-sizing: border-box;
  background: rgba(248, 251, 250, 0.96);
  box-shadow: 0 0 40px rgba(31, 73, 73, 0.08);
}
.mobile-hr-background { min-height: inherit; }

.mobile-hr-header {
  position: sticky;
  z-index: 8;
  top: 0;
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: max(10px, env(safe-area-inset-top)) 18px 10px;
  box-sizing: border-box;
  background: rgba(248, 251, 250, 0.92);
  border-bottom: 1px solid rgba(29, 92, 87, 0.08);
  backdrop-filter: none;
}

.mobile-hr-back {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  margin-right: 8px;
  padding: 0;
  color: #175e5a;
  font-size: 34px;
  line-height: 1;
  border: 0;
  border-radius: 14px;
  background: rgba(224, 241, 239, 0.9);
}

.mobile-hr-heading {
  min-width: 0;
  flex: 1;
}

.mobile-hr-heading h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.mobile-hr-content {
  min-width: 0;
  padding: 16px;
}

.mobile-hr-state {
  margin: 18px 16px;
  padding: 28px 20px;
  text-align: center;
  border: 1px solid rgba(39, 113, 105, 0.1);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
}

.mobile-hr-state strong,
.mobile-hr-state p {
  display: block;
}

.mobile-hr-state p {
  margin: 8px 0 0;
  color: #68807f;
  font-size: 14px;
  line-height: 1.55;
}

.mobile-hr-state button {
  min-height: 44px;
  margin-top: 18px;
  padding: 0 20px;
  color: #fff;
  border: 0;
  border-radius: 14px;
  background: #16756f;
}

.mobile-hr-state.is-error {
  color: #9a3c35;
  border-color: rgba(190, 75, 64, 0.18);
  background: #fff8f6;
}

.mobile-hr-spinner {
  display: block;
  width: 26px;
  height: 26px;
  margin: 0 auto 12px;
  border: 3px solid #d8ece9;
  border-top-color: #16756f;
  border-radius: 50%;
  animation: mobile-hr-spin 0.8s linear infinite;
}

.mobile-hr-footer {
  position: sticky;
  z-index: 7;
  bottom: calc(68px + env(safe-area-inset-bottom));
  padding: 10px 16px;
  border-top: 1px solid rgba(29, 92, 87, 0.1);
  background: rgba(248, 251, 250, 0.94);
  backdrop-filter: none;
}

.mobile-hr-nav {
  position: fixed;
  z-index: 10;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  width: 100%;
  max-width: 430px;
  min-height: 68px;
  margin: 0 auto;
  padding: 7px 8px calc(7px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  border-top: 1px solid rgba(29, 92, 87, 0.1);
  background: rgba(252, 254, 253, 0.96);
  backdrop-filter: none;
}

.mobile-hr-nav-item {
  min-width: 0;
  min-height: 52px;
  padding: 5px 4px;
  color: #718482;
  border: 0;
  border-radius: 14px;
  background: transparent;
  font-size: 13px;
}

.mobile-hr-nav-item svg {
  display: block;
  margin: 0 auto 4px;
  font-size: 20px;
}

.mobile-hr-nav-item.is-active {
  color: #116a64;
  background: #e4f2f0;
}

/* Shared HR shell polish keeps long forms calm while improving hierarchy. */
.mobile-hr-page {
  overflow-x: hidden;
  background-color: var(--mobile-color-page);
  background-image:
    radial-gradient(circle at 100% 0%, rgba(11, 107, 83, 0.1), transparent 260px),
    radial-gradient(circle at 0% 74%, rgba(40, 102, 177, 0.05), transparent 220px);
}

.mobile-hr-shell {
  background: transparent;
  box-shadow: none;
}

.mobile-hr-header {
  background: rgba(255, 255, 255, 0.9);
  border-bottom-color: rgba(203, 211, 206, 0.68);
  box-shadow: 0 8px 24px rgba(23, 33, 29, 0.035);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

.mobile-hr-back {
  color: var(--mobile-color-primary);
  background: var(--mobile-gradient-surface, linear-gradient(180deg, #fff, #fdfefd));
  border: 1px solid rgba(203, 211, 206, 0.76);
  box-shadow: var(--mobile-shadow-control, 0 4px 12px rgba(23, 33, 29, 0.045));
}

.mobile-hr-state {
  border-color: rgba(203, 211, 206, 0.78);
  background: var(--mobile-gradient-surface, linear-gradient(180deg, #fff, #fdfefd));
  box-shadow: var(--mobile-shadow-card, 0 10px 28px rgba(23, 33, 29, 0.055));
}

.mobile-hr-state button {
  background: var(--mobile-gradient-primary, linear-gradient(135deg, #0b6b53, #128064));
  box-shadow: var(--mobile-shadow-primary, 0 10px 24px rgba(11, 107, 83, 0.22));
}

.mobile-hr-footer {
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 -10px 28px rgba(23, 33, 29, 0.06);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

@media (prefers-reduced-motion: no-preference) {
  .mobile-hr-content {
    animation: mobile-surface-enter var(--mobile-duration-slow, 260ms) var(--mobile-ease-spring, cubic-bezier(.22,1,.36,1)) both;
  }
}

@keyframes mobile-hr-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 320px) {
  .mobile-hr-content { padding: 12px; }
  .mobile-hr-header { padding-right: 12px; padding-left: 12px; }
  .mobile-hr-heading h1 { font-size: 20px; }
}

@media (prefers-reduced-motion: reduce) {
  .mobile-hr-spinner { animation: none; }
}
</style>
