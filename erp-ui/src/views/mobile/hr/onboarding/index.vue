<template>
  <mobile-hr-shell
    title="入职管理"
    :loading="loading && !rows.length"
    :error="!rows.length ? error : ''"
    :modal-open="filterVisible"
    @retry="refresh"
  >
    <div ref="scrollRegion" class="queue-scroll" @scroll.passive="rememberScroll">
      <section class="queue-tools" aria-label="入职筛选">
        <div class="status-tabs" role="tablist" aria-label="入职状态">
          <button
            v-for="item in statusTabs"
            :key="item.value"
            type="button"
            role="tab"
            :aria-selected="status === item.value ? 'true' : 'false'"
            :class="{ 'is-active': status === item.value }"
            @click="changeStatus(item.value)"
          >{{ item.label }}</button>
        </div>

        <div class="search-row">
          <label class="search-field">
            <span class="sr-only">搜索入职记录</span>
            <input v-model.trim="filters.keyword" type="search" autocomplete="off"
              placeholder="姓名 / 手机 / 岗位 / 组织" @input="onKeywordInput">
          </label>
          <button ref="filterTrigger" class="filter-button" type="button" @click="openFilters">
            筛选<span v-if="activeFilterCount"> · {{ activeFilterCount }}</span>
          </button>
        </div>

        <div v-if="error && rows.length" class="inline-error" role="alert">
          <span>{{ error }}</span><button type="button" @click="refresh">重试</button>
        </div>
      </section>

      <mobile-onboarding-list
        :items="visibleRows"
        :selected-status="status"
        :loading="loading"
        :has-more="hasMore"
        :can-select="canQuery"
        @select="openDetail"
        @load-more="loadMore"
        @refresh="refresh"
      />
    </div>

    <template #footer>
      <button v-if="canAdd" class="create-button" type="button" @click="openCreate">新建入职</button>
    </template>

    <template #overlay>
      <div v-if="filterVisible" class="filter-overlay" role="presentation" @click.self="closeFilters">
        <section ref="filterSheet" class="filter-sheet" role="dialog" aria-modal="true"
          aria-labelledby="mobile-filter-title" tabindex="-1" @keydown="handleFilterKeydown">
          <header>
            <h2 id="mobile-filter-title">筛选入职记录</h2>
            <button type="button" aria-label="关闭筛选" @click="closeFilters"><i class="el-icon-close" aria-hidden="true" /></button>
          </header>

          <div class="filter-body">
            <div v-if="optionsLoading" class="options-state" role="status" aria-live="polite">正在加载筛选选项…</div>
            <div v-else-if="optionsError" class="options-state is-error" role="alert">
              <span>{{ optionsError }}</span><button type="button" @click="loadOptions">重试选项</button>
            </div>
            <div class="date-grid">
              <label>预计入职起始<input v-model="filterDraft.expectedEntryDateFrom" type="date"></label>
              <label>预计入职截止<input v-model="filterDraft.expectedEntryDateTo" type="date"></label>
            </div>
            <label>公司 / 组织
              <select v-model="filterDraft.targetDeptId" :disabled="optionsLoading || !!optionsError">
                <option value="">全部组织</option>
                <option v-for="item in optionList('organizations')" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label>门店
              <select v-model="filterDraft.targetStoreId" :disabled="optionsLoading || !!optionsError">
                <option value="">全部门店</option>
                <option v-for="item in optionList('stores')" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label>员工类别
              <select v-model="filterDraft.employeeCategory" :disabled="optionsLoading || !!optionsError">
                <option value="">全部类别</option>
                <option v-for="item in optionList('employeeCategories')" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label>入职负责人
              <select v-model="filterDraft.ownerUserId" :disabled="optionsLoading || !!optionsError">
                <option value="">全部负责人</option>
                <option v-for="item in optionList('owners')" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
          </div>

          <footer>
            <button class="reset-button" type="button" @click="resetFilterDraft">重置</button>
            <button class="apply-button" type="button" @click="applyFiltersAndClose">应用筛选</button>
          </footer>
        </section>
      </div>
    </template>
  </mobile-hr-shell>
</template>

<script>
import { getHrOnboardingFormOptions, listHrOnboarding } from "@/api/hr/onboarding"
import { checkPermi } from "@/utils/permission"
import {
  createMobileHrQueueOwnerFingerprint,
  removeMobileHrQueueState,
  readMobileHrQueueState,
  writeMobileHrQueueState,
  resetMobileHrQueueStateCacheForTests as resetStateCache,
  setMobileHrQueueStateNowForTests as setStateNow,
  getMobileHrQueueStateCacheSnapshotForTests as getStateSnapshot
} from "@/utils/mobileHrQueueState"
import MobileHrShell from "../components/MobileHrShell"
import MobileOnboardingList from "../components/MobileOnboardingList"
import { mobileHrErrorMessage } from "../mobileHrError"

const STATUS_TABS = [
  { value: "DRAFT", label: "草稿" },
  { value: "READY", label: "待确认" },
  { value: "CONFIRMED", label: "已入职" },
  { value: "CANCELLED", label: "已取消" }
]
const STATUS_VALUES = STATUS_TABS.map(item => item.value)
const SAFE_RESTORE_PAGE = 10

function firstQueryValue(value) { return Array.isArray(value) ? value[0] : value }
function cleanText(value) {
  const result = firstQueryValue(value)
  return result === undefined || result === null ? "" : String(result).trim()
}
function positiveId(value) {
  const text = cleanText(value)
  return /^\d+$/.test(text) && Number(text) > 0 ? Number(text) : undefined
}
function positiveNumber(value) {
  const text = cleanText(value)
  return /^\d+$/.test(text) ? Number(text) : 0
}
function emptyFilters() {
  return { keyword: "", expectedEntryDateFrom: "", expectedEntryDateTo: "", targetDeptId: "", targetStoreId: "", employeeCategory: "", ownerUserId: "" }
}
function cloneFilters(filters) {
  const result = Object.assign(emptyFilters(), filters || {})
  result.keyword = cleanText(result.keyword).slice(0, 200)
  return result
}
function cloneRows(rows) {
  return Array.isArray(rows) ? rows.map(row => Object.assign({}, row)) : []
}

export function resetMobileHrQueueStateCacheForTests() {
  resetStateCache()
}
export function setMobileHrQueueStateNowForTests(value) { setStateNow(value) }
export function getMobileHrQueueStateCacheSnapshotForTests() {
  return getStateSnapshot()
}
export function seedMobileHrQueueStateForTests(state, ownerFingerprint) {
  return writeMobileHrQueueState(ownerFingerprint, "", state)
}

function ownerFingerprint(component) {
  const getters = component && component.$store && component.$store.getters ? component.$store.getters : {}
  if (!getters.token || getters.isLock || !getters.id) return ""
  return createMobileHrQueueOwnerFingerprint({ userId: getters.id, deptId: getters.deptId, permissions: getters.permissions })
}

function routeFilters(query) {
  return {
    keyword: "",
    expectedEntryDateFrom: cleanText(query.expectedEntryDateFrom),
    expectedEntryDateTo: cleanText(query.expectedEntryDateTo),
    targetDeptId: positiveId(query.targetDeptId) || "",
    targetStoreId: positiveId(query.targetStoreId) || "",
    employeeCategory: cleanText(query.employeeCategory),
    ownerUserId: positiveId(query.ownerUserId) || ""
  }
}
function initialState(route, fingerprint) {
  const query = (route && route.query) || {}
  const stateKey = cleanText(query.stateKey)
  const cached = readMobileHrQueueState(fingerprint, stateKey)
  if (cached && !cached.overflow) return Object.assign({ stateKey, cacheHit: true, pageClamped: false }, cached)
  if (cached && cached.overflow) {
    return {
      stateKey: "", cacheHit: false, status: cached.status, taskView: cached.taskView,
      filters: cloneFilters(cached.filters), rows: [], total: 0, pageNum: 1, scrollTop: 0, pageClamped: true
    }
  }
  const taskView = cleanText(query.view) === "tasks"
  const routeStatus = cleanText(query.status).toUpperCase()
  const status = taskView ? "READY" : (STATUS_VALUES.includes(routeStatus) ? routeStatus : "DRAFT")
  const requestedPage = positiveId(query.pageNum) || 1
  const pageNum = Math.min(requestedPage, SAFE_RESTORE_PAGE)
  return {
    stateKey: "", cacheHit: false, status, taskView, filters: routeFilters(query), rows: [], total: 0,
    pageNum, scrollTop: 0, pageClamped: pageNum !== requestedPage
  }
}

export default {
  name: "MobileHrOnboardingQueue",
  components: { MobileHrShell, MobileOnboardingList },
  data() {
    const fingerprint = ownerFingerprint(this)
    const restored = initialState(this.$route, fingerprint)
    return {
      statusTabs: STATUS_TABS,
      status: restored.status,
      taskView: restored.taskView,
      filters: cloneFilters(restored.filters),
      filterDraft: cloneFilters(restored.filters),
      rows: cloneRows(restored.rows), total: restored.total, pageNum: restored.pageNum, pageSize: 20,
      loading: false, error: "", options: {}, optionsLoading: false, optionsError: "",
      optionsRequestSequence: 0, filterVisible: false, filterTriggerElement: null,
      requestSequence: 0, keywordTimer: null, scrollTimer: null,
      pendingScrollTop: restored.scrollTop, savedScrollTop: restored.scrollTop,
      stateKey: restored.stateKey, cacheHit: restored.cacheHit, routePageClamped: restored.pageClamped,
      ownerFingerprint: fingerprint
    }
  },
  computed: {
    canAdd() { return checkPermi(["hr:onboarding:add"]) },
    canQuery() { return checkPermi(["hr:onboarding:query"]) },
    queueOwnerFingerprint() { return ownerFingerprint(this) },
    visibleRows() { return this.queueOwnerFingerprint === this.ownerFingerprint ? this.rows : [] },
    hasMore() { return this.queueOwnerFingerprint === this.ownerFingerprint && this.rows.length < this.total },
    activeFilterCount() {
      return [this.filters.expectedEntryDateFrom, this.filters.expectedEntryDateTo, this.filters.targetDeptId,
        this.filters.targetStoreId, this.filters.employeeCategory, this.filters.ownerUserId].filter(Boolean).length
    }
  },
  watch: {
    "$route.query": { deep: true, handler(value) { return this.applyRouteQuery(value || {}) } },
    queueOwnerFingerprint(value, previous) {
      if (!value || value === previous) return
      this.requestSequence += 1
      this.ownerFingerprint = value
      this.stateKey = ""
      this.rows = []
      this.total = 0
      this.pageNum = 1
      this.pendingScrollTop = 0
      this.savedScrollTop = 0
      this.loadPage({ reset: true })
    }
  },
  created() {
    this.loadOptions()
    if (!this.stateKey) this.syncRouteState(this.savedScrollTop)
    if (this.cacheHit) this.restoreScroll()
    else this.loadRouteState(this.pageNum, { reconcileRoute: this.routePageClamped })
  },
  activated() { this.restoreScroll() },
  beforeRouteLeave(to, from, next) {
    this.persistQueueState(this.currentScrollTop())
    next()
  },
  beforeDestroy() {
    this.persistQueueState(this.currentScrollTop())
    this.requestSequence += 1
    this.optionsRequestSequence += 1
    if (this.keywordTimer) clearTimeout(this.keywordTimer)
    if (this.scrollTimer) clearTimeout(this.scrollTimer)
  },
  methods: {
    optionList(key) { return Array.isArray(this.options[key]) ? this.options[key] : [] },
    loadOptions() {
      const sequence = ++this.optionsRequestSequence
      this.optionsLoading = true
      this.optionsError = ""
      return getHrOnboardingFormOptions()
        .then(response => {
          if (sequence !== this.optionsRequestSequence) return
          this.options = response && response.data ? response.data : {}
        })
        .catch(error => {
          if (sequence !== this.optionsRequestSequence) return
          this.options = {}
          this.optionsError = mobileHrErrorMessage(error, "筛选选项加载失败")
        })
        .finally(() => { if (sequence === this.optionsRequestSequence) this.optionsLoading = false })
    },
    captureRequestState() { return { status: this.status, filters: cloneFilters(this.filters) } },
    requestStateKey(state) { return JSON.stringify({ status: state.status, filters: cloneFilters(state.filters) }) },
    requestStateMatches(state) { return this.requestStateKey(state) === this.requestStateKey(this.captureRequestState()) },
    requestParams(pageNum, requestState) {
      const state = requestState || this.captureRequestState()
      const filters = state.filters
      return { pageNum, pageSize: this.pageSize, keyword: filters.keyword || "", status: state.status,
        expectedEntryDateFrom: filters.expectedEntryDateFrom || undefined, expectedEntryDateTo: filters.expectedEntryDateTo || undefined,
        targetDeptId: positiveId(filters.targetDeptId), targetStoreId: positiveId(filters.targetStoreId),
        employeeCategory: filters.employeeCategory || undefined, ownerUserId: positiveId(filters.ownerUserId) }
    },
    loadPage({ reset = false, pageNum } = {}) {
      const nextPage = pageNum || (reset ? 1 : this.pageNum)
      const sequence = ++this.requestSequence
      const requestState = this.captureRequestState()
      this.loading = true; this.error = ""
      if (reset) { this.pageNum = 1; this.rows = []; this.total = 0 }
      return listHrOnboarding(this.requestParams(nextPage, requestState))
        .then(response => {
          if (sequence !== this.requestSequence || !this.requestStateMatches(requestState)) return
          const incoming = Array.isArray(response && response.rows) ? response.rows : []
          this.rows = reset ? incoming : this.rows.concat(incoming)
          this.total = Number(response && response.total) || 0
          if (incoming.length || nextPage === 1) this.pageNum = nextPage
          this.persistQueueState(this.savedScrollTop)
          return this.syncRouteState(this.savedScrollTop).then(() => this.restoreScroll())
        })
        .catch(error => {
          if (sequence !== this.requestSequence) return
          if (reset) { this.rows = []; this.total = 0 }
          this.error = mobileHrErrorMessage(error, "暂时无法获取入职记录")
        })
        .finally(() => { if (sequence === this.requestSequence) this.loading = false })
    },
    loadRouteState(pageNum, options = {}) {
      const target = Math.min(positiveId(pageNum) || 1, SAFE_RESTORE_PAGE)
      return target > 1 ? this.restorePageWindow(target, options) : this.loadPage({ reset: true, pageNum: 1 })
    },
    restorePageWindow(pageNum, { reconcileRoute = false } = {}) {
      const target = Math.min(positiveId(pageNum) || 1, SAFE_RESTORE_PAGE)
      const sequence = ++this.requestSequence
      const requestState = this.captureRequestState()
      const rebuilt = []
      let total = 0; let effectivePage = 1
      this.loading = true; this.error = ""; this.rows = []; this.total = 0
      const next = page => listHrOnboarding(this.requestParams(page, requestState)).then(response => {
        if (sequence !== this.requestSequence || !this.requestStateMatches(requestState)) return
        const incoming = Array.isArray(response && response.rows) ? response.rows : []
        total = Number(response && response.total) || 0
        if (incoming.length || page === 1) effectivePage = page
        rebuilt.push(...incoming)
        if (page < target && incoming.length === this.pageSize && rebuilt.length < total) return next(page + 1)
      })
      return next(1).then(() => {
        if (sequence !== this.requestSequence || !this.requestStateMatches(requestState)) return
        this.rows = rebuilt; this.total = total; this.pageNum = effectivePage
        this.persistQueueState(this.savedScrollTop)
        const finish = () => this.restoreScroll()
        return (reconcileRoute || effectivePage !== target ? this.syncRouteState(this.savedScrollTop) : Promise.resolve()).then(finish)
      }).catch(error => {
        if (sequence !== this.requestSequence) return
        this.rows = []; this.total = 0; this.pageNum = 1
        this.error = mobileHrErrorMessage(error, "暂时无法恢复入职记录")
      }).finally(() => { if (sequence === this.requestSequence) this.loading = false })
    },
    applyFilters() {
      this.pendingScrollTop = 0; this.savedScrollTop = 0; this.pageNum = 1
      this.stateKey = ""
      const request = this.loadPage({ reset: true })
      this.syncRouteState(0)
      return request
    },
    applyFiltersAndClose() {
      this.filters = cloneFilters(this.filterDraft)
      this.closeFilters()
      return this.applyFilters()
    },
    onKeywordInput() {
      if (this.keywordTimer) clearTimeout(this.keywordTimer)
      this.requestSequence += 1
      this.loading = false
      this.keywordTimer = setTimeout(() => { this.keywordTimer = null; this.applyFilters() }, 300)
    },
    changeStatus(status) {
      if (!STATUS_VALUES.includes(status)) return Promise.resolve()
      if (this.keywordTimer) { clearTimeout(this.keywordTimer); this.keywordTimer = null }
      this.status = status; this.taskView = false; this.filters = cloneFilters(this.filters)
      return this.applyFilters()
    },
    refresh() { this.pendingScrollTop = 0; this.savedScrollTop = 0; this.pageNum = 1; return this.loadPage({ reset: true }) },
    loadMore() {
      if (this.loading || this.rows.length >= this.total) return Promise.resolve()
      return this.loadPage({ reset: false, pageNum: this.pageNum + 1 })
    },
    openFilters(event) {
      this.filterDraft = cloneFilters(this.filters)
      this.filterTriggerElement = (event && event.currentTarget) || this.$refs.filterTrigger || null
      this.filterVisible = true
      this.$nextTick(() => {
        const sheet = this.$refs.filterSheet
        const first = sheet && sheet.querySelector("button, input, select, [tabindex]:not([tabindex='-1'])")
        if (first && first.focus) first.focus()
        else if (sheet && sheet.focus) sheet.focus()
      })
    },
    resetFilterDraft() { this.filterDraft = emptyFilters() },
    closeFilters(restoreFocus = true) {
      this.filterVisible = false
      this.filterDraft = cloneFilters(this.filters)
      if (restoreFocus) this.$nextTick(() => {
        if (this.filterTriggerElement && this.filterTriggerElement.focus) this.filterTriggerElement.focus()
      })
    },
    handleFilterKeydown(event) {
      if (!event) return
      if (event.key === "Escape") { event.preventDefault(); this.closeFilters(); return }
      if (event.key !== "Tab") return
      const sheet = this.$refs.filterSheet
      const focusable = sheet ? Array.from(sheet.querySelectorAll("button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex='-1'])")) : []
      if (!focusable.length) { event.preventDefault(); return }
      const active = typeof document !== "undefined" ? document.activeElement : null
      const first = focusable[0]; const last = focusable[focusable.length - 1]
      if (event.shiftKey && active === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && active === last) { event.preventDefault(); first.focus() }
    },
    currentScrollTop() { return this.$refs.scrollRegion ? this.$refs.scrollRegion.scrollTop : this.savedScrollTop },
    persistQueueState(scrollTop) {
      const currentFingerprint = ownerFingerprint(this)
      if (!currentFingerprint || currentFingerprint !== this.ownerFingerprint) {
        removeMobileHrQueueState(this.ownerFingerprint, this.stateKey)
        this.stateKey = ""
        return ""
      }
      this.savedScrollTop = positiveNumber(scrollTop)
      this.stateKey = writeMobileHrQueueState(currentFingerprint, this.stateKey, { status: this.status, taskView: this.taskView, filters: this.filters,
        rows: this.rows, total: this.total, pageNum: this.pageNum, scrollTop: this.savedScrollTop })
      return this.stateKey
    },
    routeQuery() {
      const query = {}
      if (this.stateKey) query.stateKey = this.stateKey
      if (this.taskView) query.view = "tasks"
      if (this.status !== "DRAFT" || this.taskView) query.status = this.status
      ;["expectedEntryDateFrom", "expectedEntryDateTo", "targetDeptId", "targetStoreId", "employeeCategory", "ownerUserId"].forEach(key => {
        const value = this.filters[key]
        if (value !== "" && value !== undefined && value !== null) query[key] = String(value)
      })
      return query
    },
    syncRouteState(scrollTop) {
      if (!this.$router || !this.$route) return Promise.resolve()
      this.persistQueueState(scrollTop)
      return this.$router.replace({ path: this.$route.path, query: this.routeQuery() }).catch(() => {})
    },
    rememberScroll() {
      this.savedScrollTop = this.currentScrollTop()
      this.pendingScrollTop = this.savedScrollTop
      if (this.scrollTimer) clearTimeout(this.scrollTimer)
      this.scrollTimer = setTimeout(() => { this.scrollTimer = null; this.persistQueueState(this.savedScrollTop) }, 120)
    },
    restoreScroll() {
      const scrollTop = this.pendingScrollTop
      if (!scrollTop) return
      this.$nextTick(() => { if (this.$refs.scrollRegion) this.$refs.scrollRegion.scrollTop = scrollTop; this.pendingScrollTop = 0 })
    },
    canonicalDataKey(state) { return JSON.stringify({ status: state.status, taskView: state.taskView, pageNum: state.pageNum, filters: state.filters }) },
    applyRouteQuery(query) {
      const next = initialState({ query }, ownerFingerprint(this))
      if (next.cacheHit) {
        const unchanged = next.stateKey === this.stateKey && this.canonicalDataKey(next) === this.canonicalDataKey({ status: this.status, taskView: this.taskView, pageNum: this.pageNum, filters: this.filters })
        if (unchanged) return Promise.resolve()
        this.requestSequence += 1
        this.status = next.status; this.taskView = next.taskView; this.filters = cloneFilters(next.filters)
        this.rows = cloneRows(next.rows); this.total = next.total; this.pageNum = next.pageNum
        this.stateKey = next.stateKey; this.savedScrollTop = next.scrollTop; this.pendingScrollTop = next.scrollTop
        this.error = ""; this.loading = false; this.restoreScroll()
        return Promise.resolve()
      }
      const currentKey = this.canonicalDataKey({ status: this.status, taskView: this.taskView, pageNum: this.pageNum, filters: this.filters })
      if (currentKey === this.canonicalDataKey(next) && !this.stateKey) return Promise.resolve()
      this.status = next.status; this.taskView = next.taskView; this.filters = cloneFilters(next.filters)
      this.rows = []; this.total = 0; this.pageNum = next.pageNum; this.stateKey = ""; this.savedScrollTop = 0; this.pendingScrollTop = 0
      return this.loadRouteState(next.pageNum, { reconcileRoute: next.pageClamped })
    },
    navigationQuery() { this.persistQueueState(this.currentScrollTop()); return this.routeQuery() },
    openDetail(row) {
      if (!this.canQuery || !row || !/^\d+$/.test(String(row.onboardingId || ""))) return
      this.$router.push({ path: `/mobile/hr/onboarding/${row.onboardingId}`, query: this.navigationQuery() }).catch(() => {})
    },
    openCreate() {
      if (!this.canAdd) return
      this.$router.push({ path: "/mobile/hr/onboarding/create", query: this.navigationQuery() }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.queue-scroll { max-height: calc(100dvh - 154px - env(safe-area-inset-top) - env(safe-area-inset-bottom)); overflow-y: auto; overscroll-behavior: contain; scrollbar-width: none; }
.queue-scroll::-webkit-scrollbar { display: none; }
.queue-tools { position: sticky; z-index: 4; top: 0; padding-bottom: 12px; background: var(--mobile-color-page, #f4f5f2); backdrop-filter: none; }
.status-tabs { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px; padding: 4px; border-radius: 15px; background: #e7f0ee; }
.status-tabs button { min-width: 0; min-height: 44px; padding: 0 4px; color: #617875; font-size: 13px; font-weight: 600; border: 0; border-radius: 12px; background: transparent; }
.status-tabs button.is-active { color: #126c66; background: #fff; box-shadow: 0 3px 10px rgba(43, 92, 87, 0.1); }
.search-row { display: flex; gap: 9px; margin-top: 10px; }
.search-field { flex: 1; min-width: 0; }
.search-field input, .filter-body input, .filter-body select { width: 100%; min-height: 46px; padding: 0 12px; box-sizing: border-box; color: #18343b; font: inherit; border: 1px solid #cedfdd; border-radius: 13px; background: #fff; }
.filter-button { flex: 0 0 auto; min-height: 46px; padding: 0 14px; color: #126c66; font-weight: 700; border: 1px solid #b9dcd7; border-radius: 13px; background: #edf8f6; }
.inline-error, .options-state { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; padding: 10px 12px; color: #526b68; font-size: 13px; border-radius: 12px; background: #edf7f5; }
.inline-error, .options-state.is-error { color: #923f38; background: #fff1ef; }
.inline-error button, .options-state button { min-height: 44px; color: inherit; border: 0; background: transparent; font-weight: 700; }
.filter-overlay { position: fixed; z-index: 30; inset: 0; display: flex; align-items: flex-end; justify-content: center; padding: 0 12px; background: rgba(16, 38, 39, 0.44); }
.filter-sheet { width: 100%; max-width: 430px; max-height: calc(92dvh - env(safe-area-inset-top)); overflow: hidden; border-radius: 24px 24px 0 0; background: #f9fcfb; box-shadow: 0 -16px 40px rgba(19, 56, 55, 0.18); }
.filter-sheet header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-bottom: 1px solid #e5eeec; }
.filter-sheet h2 { margin: 0; font-size: 19px; }
.filter-sheet header button { width: 44px; height: 44px; color: #47615e; font-size: 28px; border: 0; border-radius: 14px; background: #edf3f2; }
.filter-body { display: grid; gap: 13px; max-height: 60dvh; padding: 16px 18px; overflow-y: auto; }
.filter-body label { display: grid; gap: 7px; color: #516a67; font-size: 13px; font-weight: 600; }
.filter-body select:disabled { color: #8a9997; background: #edf1f0; }
.date-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.filter-sheet footer { display: flex; gap: 10px; padding: 12px 18px calc(12px + env(safe-area-inset-bottom)); border-top: 1px solid #e5eeec; }
.filter-sheet footer button { flex: 1; min-height: 48px; font-weight: 700; border-radius: 14px; }
.reset-button { color: #526b68; border: 1px solid #c8d9d7; background: #fff; }
.apply-button { color: #fff; border: 1px solid #16756f; background: #16756f; }
.create-button { width: 100%; min-height: 48px; color: #fff; font-weight: 700; border: 0; border-radius: 15px; background: #16756f; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 360px) { .status-tabs button { font-size: 12px; } .date-grid { grid-template-columns: 1fr; } }
</style>
