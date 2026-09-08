<template>
  <div class="owner-field">
    <button
      ref="trigger"
      :id="inputId || null"
      type="button"
      class="owner-trigger"
      :disabled="disabled"
      :aria-invalid="invalid ? 'true' : 'false'"
      :aria-describedby="describedBy || null"
      :aria-labelledby="triggerLabelledBy"
      aria-haspopup="dialog"
      @click="openPicker"
    >
      <span :id="inputId ? `${inputId}-value` : null" :class="{ placeholder: !displayLabel }">
        {{ displayLabel || `请选择${pickerLabel}` }}
      </span>
      <small v-if="value && unavailable">当前保存值不可选</small>
      <i class="el-icon-arrow-right" aria-hidden="true" />
    </button>

    <div v-if="open" class="owner-overlay" @mousedown.self.prevent="closePicker">
      <section
        ref="dialog"
        class="owner-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="owner-picker-title"
        @keydown="handleDialogKeydown"
      >
        <header>
          <button type="button" class="plain-action" @click="closePicker">取消</button>
          <h3 id="owner-picker-title">选择{{ pickerLabel }}</h3>
          <button type="button" class="plain-action confirm" :disabled="!draftValue" @click="confirmSelection">确认</button>
        </header>

        <div class="scope-switch" role="group" aria-label="组织范围">
          <button
            type="button"
            :class="{ active: scopeMode === 'target' }"
            :disabled="!targetDeptId"
            :aria-pressed="scopeMode === 'target' ? 'true' : 'false'"
            @click="setScope('target')"
          >目标组织</button>
          <button
            type="button"
            :class="{ active: scopeMode === 'all' }"
            :aria-pressed="scopeMode === 'all' ? 'true' : 'false'"
            @click="setScope('all')"
          >全部可管理组织</button>
        </div>

        <label v-if="scopeMode === 'target'" class="children-toggle">
          <input v-model="includeChildren" type="checkbox" @change="reload">
          <span>包含下级组织</span>
        </label>

        <div class="owner-search">
          <label class="sr-only" for="mobile-owner-search">搜索负责人</label>
          <input
            id="mobile-owner-search"
            ref="search"
            v-model="keyword"
            type="search"
            role="combobox"
            aria-autocomplete="list"
            aria-expanded="true"
            aria-controls="mobile-owner-options"
            :aria-activedescendant="activeOptionId"
            :maxlength="keywordMaxLength"
            placeholder="搜索姓名、工号或手机号"
            @input="scheduleSearch"
            @keydown.down.prevent="moveActive(1)"
            @keydown.up.prevent="moveActive(-1)"
            @keydown.enter.prevent="chooseActive"
          >
          <button type="button" :disabled="loading" @click="reload">搜索</button>
        </div>

        <div
          id="mobile-owner-options"
          ref="results"
          class="owner-results"
          role="listbox"
          :aria-busy="loading || loadingMore ? 'true' : 'false'"
          @scroll.passive="handleScroll"
        >
          <div v-if="loading && !rows.length" class="owner-state" role="status">正在加载{{ pickerLabel }}…</div>

          <div v-else-if="initialError && !rows.length" class="owner-state is-error" :role="errorType === 'forbidden' ? 'alert' : 'status'">
            <strong>{{ errorType === "forbidden" ? "无权查看" : "加载失败" }}</strong>
            <span>{{ initialError }}</span>
            <button v-if="errorType !== 'forbidden'" type="button" @click="reload">重试</button>
          </div>

          <div v-else-if="!visibleRows.length" class="owner-state">
            <strong>{{ emptyTitle }}</strong>
            <span>{{ emptyHint }}</span>
          </div>

          <template v-else>
            <button
              v-for="(option, index) in visibleRows"
              :id="optionId(option)"
              :key="String(option.userId)"
              type="button"
              class="owner-option"
              :class="{ selected: option.userId === draftValue, active: index === activeIndex }"
              role="option"
              :aria-selected="option.userId === draftValue ? 'true' : 'false'"
              @mouseenter="activeIndex = index"
              @click="selectDraft(option)"
            >
              <span class="avatar" aria-hidden="true">{{ option.label.slice(0, 1) }}</span>
              <span class="owner-copy">
                <strong>{{ option.label }}</strong>
                <small>
                  <em v-if="isRecent(option)">最近</em>
                  {{ optionMeta(option) || "可选择" }}
                </small>
              </span>
              <span v-if="option.userId === draftValue" class="check" aria-hidden="true">✓</span>
            </button>
          </template>

          <div v-if="pageError && rows.length" class="page-error" role="alert">
            <span>{{ pageError }}</span>
            <button type="button" @click="loadMore">重试</button>
          </div>
          <button v-else-if="hasMore" type="button" class="load-more" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? "加载中…" : "加载更多" }}
          </button>
          <p v-else-if="rows.length" class="list-end">已加载全部负责人</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import { getHrOnboardingOwnerOptions } from "@/api/hr/onboarding"
import {
  OWNER_KEYWORD_MAX_LENGTH,
  OWNER_PAGE_SIZE,
  mergeOwnerOptions,
  normalizeOwnerOption,
  normalizeOwnerPage,
  normalizedOwnerId,
  ownerOptionMeta,
  ownerPickerError,
  ownerRecentStorageKey,
  readRecentOwners,
  rememberRecentOwner,
  writeRecentOwners
} from "../mobileOnboardingOwnerPicker"

export default {
  name: "MobileOnboardingOwnerPicker",
  props: {
    value: { type: [String, Number], default: "" },
    valueLabel: { type: String, default: "" },
    pickerLabel: { type: String, default: "入职负责人" },
    targetDeptId: { type: [String, Number], default: null },
    disabled: { type: Boolean, default: false },
    invalid: { type: Boolean, default: false },
    describedBy: { type: String, default: "" },
    inputId: { type: String, default: "" },
    labelledBy: { type: String, default: "" }
  },
  data() {
    return {
      open: false,
      keyword: "",
      keywordMaxLength: OWNER_KEYWORD_MAX_LENGTH,
      scopeMode: "target",
      includeChildren: true,
      rows: [],
      total: 0,
      pageNum: 1,
      pageSize: OWNER_PAGE_SIZE,
      loading: false,
      loadingMore: false,
      initialError: "",
      pageError: "",
      errorType: "",
      draftValue: null,
      draftOption: null,
      unavailable: false,
      hydrated: false,
      activeIndex: -1,
      requestSequence: 0,
      searchTimer: null,
      returnFocus: null,
      recentEntries: [],
      historyGuardActive: false,
      historyListening: false,
      suppressNextPopstate: false,
      historyGuardToken: ""
    }
  },
  computed: {
    selectedOption() {
      const current = normalizedOwnerId(this.value)
      return this.rows.find(option => option.userId === current)
    },
    accountId() {
      return normalizedOwnerId(this.$store && this.$store.getters && this.$store.getters.id)
    },
    recentStorageKey() {
      return ownerRecentStorageKey(this.accountId, this.targetDeptId)
    },
    triggerLabelledBy() {
      return [this.labelledBy, this.inputId && `${this.inputId}-value`].filter(Boolean).join(" ") || null
    },
    displayLabel() {
      return (this.selectedOption && this.selectedOption.label) || this.valueLabel || ""
    },
    visibleRows() {
      const rows = this.rows.slice()
      if (!this.draftValue) return rows
      return rows.sort((left, right) => {
        if (left.userId === this.draftValue) return -1
        if (right.userId === this.draftValue) return 1
        return 0
      })
    },
    hasMore() { return this.rows.length < this.total },
    activeOptionId() {
      const option = this.visibleRows[this.activeIndex]
      return option ? this.optionId(option) : null
    },
    emptyTitle() {
      if (this.keyword.trim()) return `没有匹配的${this.pickerLabel}`
      return this.scopeMode === "target" ? "当前组织暂无人员" : `暂无可选${this.pickerLabel}`
    },
    emptyHint() {
      if (this.keyword.trim()) return "请尝试姓名、工号或手机号的其他关键词。"
      return this.scopeMode === "target" ? "可切换到全部可管理组织继续查找。" : "当前数据范围内没有启用人员。"
    }
  },
  watch: {
    targetDeptId() {
      if (!normalizedOwnerId(this.targetDeptId)) this.scopeMode = "all"
      if (this.open) {
        this.loadRecentEntries()
        this.reload()
      }
    },
    value() {
      if (!this.open) this.hydrateClosedValue()
    }
  },
  beforeDestroy() {
    this.cancelPending()
    this.removeHistoryListener()
  },
  methods: {
    optionMeta: ownerOptionMeta,
    isRecent(option) {
      return this.recentEntries.some(entry => entry.userId === option.userId)
    },
    optionId(option) { return `mobile-owner-option-${option.userId}` },
    hydrateClosedValue() {
      const current = normalizedOwnerId(this.value)
      if (!current) {
        this.unavailable = false
        return
      }
      this.unavailable = this.hydrated && !this.rows.some(option => option.userId === current)
    },
    openPicker() {
      if (this.disabled) return
      this.returnFocus = typeof document === "undefined" ? null : document.activeElement
      this.scopeMode = normalizedOwnerId(this.targetDeptId) ? "target" : "all"
      this.draftValue = normalizedOwnerId(this.value)
      this.draftOption = this.draftValue ? normalizeOwnerOption({
        userId: this.draftValue,
        label: this.valueLabel || `用户 ${this.draftValue}`
      }) : null
      this.keyword = ""
      this.loadRecentEntries()
      this.open = true
      this.installHistoryGuard()
      this.reload()
      this.$nextTick(() => {
        if (this.$refs.search && this.$refs.search.focus) this.$refs.search.focus()
      })
    },
    closePicker(options) {
      const fromHistory = Boolean(options && options.fromHistory)
      if (!fromHistory) this.releaseHistoryGuard()
      this.cancelPending()
      this.open = false
      this.$nextTick(() => {
        const target = this.returnFocus || this.$refs.trigger
        if (target && target.focus) target.focus()
      })
    },
    cancelPending() {
      this.requestSequence += 1
      if (this.searchTimer) clearTimeout(this.searchTimer)
      this.searchTimer = null
    },
    storage() {
      try {
        return typeof window !== "undefined" ? window.localStorage : null
      } catch (error) {
        return null
      }
    },
    loadRecentEntries() {
      this.recentEntries = readRecentOwners(this.storage(), this.recentStorageKey)
    },
    hydrateRecentEntries(rows) {
      if (!this.recentStorageKey || !this.recentEntries.length) return
      const authorized = new Set((rows || []).map(option => option.userId))
      this.recentEntries = this.recentEntries.filter(entry => authorized.has(entry.userId))
      writeRecentOwners(this.storage(), this.recentStorageKey, this.recentEntries)
    },
    rememberRecentSelection(userId) {
      this.recentEntries = rememberRecentOwner(
        this.storage(), this.recentStorageKey, userId
      )
    },
    installHistoryGuard() {
      if (typeof window === "undefined" || !window.history || !window.history.pushState) return
      if (!this.historyListening) {
        window.addEventListener("popstate", this.handlePopState)
        this.historyListening = true
      }
      const previous = window.history.state && typeof window.history.state === "object"
        ? window.history.state
        : {}
      this.historyGuardToken = `owner-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      window.history.pushState({
        ...previous,
        __mobileOnboardingOwnerPicker: this.historyGuardToken
      }, "", window.location && window.location.href)
      this.historyGuardActive = true
    },
    releaseHistoryGuard() {
      if (!this.historyGuardActive || typeof window === "undefined" || !window.history) return
      this.historyGuardActive = false
      this.suppressNextPopstate = true
      window.history.back()
    },
    handlePopState() {
      if (this.suppressNextPopstate) {
        this.suppressNextPopstate = false
        return
      }
      if (!this.open) return
      this.historyGuardActive = false
      this.closePicker({ fromHistory: true })
    },
    removeHistoryListener() {
      if (typeof window !== "undefined" && this.historyListening) {
        window.removeEventListener("popstate", this.handlePopState)
      }
      this.historyListening = false
    },
    setScope(scope) {
      if (scope === "target" && !normalizedOwnerId(this.targetDeptId)) return
      if (this.scopeMode === scope) return
      this.scopeMode = scope
      this.reload()
    },
    scheduleSearch() {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      this.requestSequence += 1
      this.loading = true
      this.loadingMore = false
      this.rows = []
      this.total = 0
      this.activeIndex = -1
      this.initialError = ""
      this.pageError = ""
      this.searchTimer = setTimeout(() => {
        this.searchTimer = null
        this.reload()
      }, 250)
    },
    reload() {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      this.searchTimer = null
      this.pageNum = 1
      this.rows = []
      this.total = 0
      this.activeIndex = -1
      return this.fetchPage(false)
    },
    requestParams(pageNum) {
      const params = {
        keyword: this.keyword.trim(),
        includeChildren: this.includeChildren,
        pageNum,
        pageSize: this.pageSize
      }
      const targetDeptId = normalizedOwnerId(this.targetDeptId)
      if (this.scopeMode === "target" && targetDeptId) params.deptId = targetDeptId
      const hydrationIds = [this.draftValue].concat(
        this.recentEntries.map(entry => entry.userId)
      ).map(normalizedOwnerId).filter(Boolean)
      const uniqueHydrationIds = Array.from(new Set(hydrationIds)).slice(0, 6)
      if (uniqueHydrationIds.length) params.userIds = uniqueHydrationIds.join(",")
      return params
    },
    fetchPage(append) {
      if (!this.open) return Promise.resolve(null)
      const pageNum = append ? this.pageNum + 1 : 1
      const sequence = ++this.requestSequence
      if (append) this.loadingMore = true
      else this.loading = true
      if (append) this.pageError = ""
      else {
        this.initialError = ""
        this.errorType = ""
      }
      return getHrOnboardingOwnerOptions(this.requestParams(pageNum))
        .then(response => {
          if (!this.open || sequence !== this.requestSequence) return null
          const page = normalizeOwnerPage(response)
          this.rows = append ? mergeOwnerOptions(this.rows, page.rows) : page.rows
          this.total = page.total
          this.pageNum = pageNum
          this.hydrated = true
          if (pageNum === 1) this.hydrateRecentEntries(page.rows)
          const current = normalizedOwnerId(this.value)
          this.unavailable = Boolean(current && !this.rows.some(option => option.userId === current))
          if (this.draftValue) {
            const hydratedDraft = this.rows.find(option => option.userId === this.draftValue)
            if (hydratedDraft) this.draftOption = hydratedDraft
          }
          return page
        })
        .catch(error => {
          if (!this.open || sequence !== this.requestSequence) return null
          const state = ownerPickerError(error)
          if (append) this.pageError = state.message
          else {
            this.initialError = state.message
            this.errorType = state.type
          }
          return null
        })
        .finally(() => {
          if (sequence !== this.requestSequence) return
          this.loading = false
          this.loadingMore = false
        })
    },
    loadMore() {
      if (this.loading || this.loadingMore || !this.hasMore) return Promise.resolve(null)
      return this.fetchPage(true)
    },
    handleScroll(event) {
      const target = event && event.target
      if (!target || target.scrollHeight - target.scrollTop - target.clientHeight > 80) return
      this.loadMore()
    },
    selectDraft(option) {
      this.draftValue = option.userId
      this.draftOption = option
      this.activeIndex = this.visibleRows.findIndex(item => item.userId === option.userId)
    },
    moveActive(delta) {
      if (!this.visibleRows.length) return
      const next = this.activeIndex < 0
        ? (delta > 0 ? 0 : this.visibleRows.length - 1)
        : (this.activeIndex + delta + this.visibleRows.length) % this.visibleRows.length
      this.activeIndex = next
      this.$nextTick(() => {
        const id = this.activeOptionId
        const option = id && typeof document !== "undefined" ? document.getElementById(id) : null
        if (option && option.scrollIntoView) option.scrollIntoView({ block: "nearest" })
      })
    },
    chooseActive() {
      const option = this.visibleRows[this.activeIndex]
      if (option) this.selectDraft(option)
      else this.reload()
    },
    confirmSelection() {
      if (!this.draftValue) return
      const option = this.draftOption || normalizeOwnerOption({
        userId: this.draftValue,
        label: this.valueLabel || `用户 ${this.draftValue}`
      })
      this.rememberRecentSelection(this.draftValue)
      this.$emit("input", this.draftValue)
      this.$emit("select", option)
      this.closePicker()
    },
    handleDialogKeydown(event) {
      if (event.key === "Escape") {
        event.preventDefault()
        this.closePicker()
        return
      }
      if (event.key !== "Tab") return
      const dialog = this.$refs.dialog
      const focusable = dialog && dialog.querySelectorAll
        ? Array.from(dialog.querySelectorAll("button:not([disabled]), input:not([disabled])"))
        : []
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.owner-field { width: 100%; }
.owner-trigger { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; width: 100%; min-height: 48px; gap: 4px 10px; border: 1px solid #c8d7d4; border-radius: 12px; background: #fff; padding: 9px 12px; color: #18312f; font: inherit; text-align: left; }
.owner-trigger[aria-invalid="true"] { border-color: #b7493b; }
.owner-trigger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.owner-trigger .placeholder { color: #82938f; }
.owner-trigger small { grid-column: 1; color: #a44839; font-size: 12px; }
.owner-trigger i { grid-column: 2; grid-row: 1 / span 2; font-size: 24px; font-style: normal; }
.owner-overlay { position: fixed; z-index: 40; inset: 0; display: flex; align-items: flex-end; justify-content: center; background: rgba(15, 35, 32, .42); }
.owner-dialog { display: flex; flex-direction: column; width: min(100%, 430px); max-height: min(88vh, 760px); padding-bottom: env(safe-area-inset-bottom); border-radius: 22px 22px 0 0; background: #f8fbfa; box-shadow: 0 -12px 40px rgba(17, 49, 44, .18); }
.owner-dialog header { display: grid; grid-template-columns: 72px 1fr 72px; align-items: center; min-height: 56px; border-bottom: 1px solid #dbe6e3; padding: 0 10px; }
.owner-dialog h3 { margin: 0; font-size: 17px; text-align: center; }
.plain-action { min-width: 44px; min-height: 44px; border: 0; background: transparent; color: #516b66; font: inherit; }
.plain-action.confirm { color: #16796d; font-weight: 700; }
.plain-action:disabled { opacity: .4; }
.scope-switch { display: grid; grid-template-columns: 1fr 1.45fr; gap: 6px; margin: 12px 14px 6px; padding: 4px; border-radius: 12px; background: #e7f0ee; }
.scope-switch button { min-height: 44px; border: 0; border-radius: 9px; background: transparent; color: #59716d; font: inherit; }
.scope-switch button.active { background: #fff; color: #116d62; box-shadow: 0 2px 8px rgba(18, 68, 60, .1); font-weight: 700; }
.children-toggle { display: flex; align-items: center; min-height: 44px; gap: 8px; margin: 0 16px; color: #516b66; font-size: 13px; }
.children-toggle input { width: 20px; min-height: 20px; }
.owner-search { display: grid; grid-template-columns: minmax(0, 1fr) 64px; gap: 8px; padding: 8px 14px 10px; }
.owner-search input { width: 100%; min-height: 44px; box-sizing: border-box; border: 1px solid #bfd1cd; border-radius: 12px; background: #fff; padding: 9px 12px; color: #18312f; font: inherit; }
.owner-search button { min-width: 44px; min-height: 44px; border: 0; border-radius: 12px; background: #16796d; color: #fff; font: inherit; font-weight: 650; }
.owner-results { min-height: 180px; flex: 1; overflow-y: auto; overscroll-behavior: contain; padding: 2px 14px 18px; }
.owner-option { display: grid; grid-template-columns: 42px minmax(0, 1fr) 28px; align-items: center; width: 100%; min-height: 64px; gap: 10px; border: 1px solid transparent; border-bottom-color: #e0e9e7; background: transparent; padding: 8px 6px; color: #18312f; text-align: left; }
.owner-option.active { border-color: #80afa7; border-radius: 12px; background: #edf6f4; }
.owner-option.selected { color: #116d62; }
.avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: #d9ebe7; color: #126d62; font-weight: 750; }
.owner-copy { min-width: 0; }
.owner-copy strong, .owner-copy small { display: block; overflow-wrap: anywhere; }
.owner-copy small { margin-top: 3px; color: #6f827e; font-size: 12px; line-height: 1.35; }
.owner-copy em { margin-right: 4px; border-radius: 5px; background: #dceeea; padding: 1px 4px; color: #116d62; font-style: normal; }
.check { font-size: 20px; font-weight: 750; text-align: center; }
.owner-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 190px; gap: 8px; padding: 20px; color: #647b77; text-align: center; }
.owner-state strong { color: #294944; }
.owner-state button, .page-error button { min-width: 72px; min-height: 44px; border: 1px solid #16796d; border-radius: 12px; background: #fff; color: #16796d; }
.owner-state.is-error strong, .page-error { color: #a44839; }
.page-error { display: flex; align-items: center; justify-content: space-between; min-height: 56px; gap: 8px; }
.load-more { width: 100%; min-height: 48px; margin-top: 8px; border: 1px solid #bfd1cd; border-radius: 12px; background: #fff; color: #16796d; font: inherit; }
.list-end { margin: 14px 0 0; color: #83938f; font-size: 12px; text-align: center; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }
@media (prefers-reduced-motion: reduce) {
  .owner-dialog { scroll-behavior: auto; }
}
</style>
