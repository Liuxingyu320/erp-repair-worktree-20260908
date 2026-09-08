<template>
  <main class="mobile-completeness-page mobile-system-page">
    <header><button type="button" aria-label="返回" @click="$router.back()"><i class="el-icon-arrow-left" aria-hidden="true" /></button><div><small>人事待办</small><h1>资料完整度</h1></div><button type="button" aria-label="刷新资料完整度" @click="reload"><i class="el-icon-refresh" aria-hidden="true" /></button></header>
    <p class="hint">优先补齐当前阶段的业务必填项；点击“去补资料”继续处理，资料覆盖度会保留供后续完善。</p>
    <section v-if="canViewMasterData && masterDataSummary" class="master-summary" aria-labelledby="mobile-master-summary-title">
      <div><small>只读观察</small><h2 id="mobile-master-summary-title">主数据健康摘要</h2></div>
      <dl>
        <div><dt>P0 风险</dt><dd>{{ masterDataSummary.blockingIssueCount || 0 }}</dd></div>
        <div><dt>P1 问题</dt><dd>{{ masterDataSummary.importantIssueCount || 0 }}</dd></div>
        <div><dt>受影响员工</dt><dd>{{ masterDataSummary.affectedEmployeeCount || 0 }}</dd></div>
      </dl>
      <p>移动端仅展示摘要，请在桌面端进入“主数据问题”处理。</p>
    </section>
    <section class="profile-list">
      <article v-for="row in rows" :key="row.userId">
        <div class="title"><div><h2>{{ row.employeeName || '-' }}</h2><p>{{ row.employeeNo || '-' }}</p></div><strong>{{ completion(row) }}%</strong></div>
        <div class="progress"><i :style="{ width: completion(row) + '%' }" /></div>
        <p v-if="missingFields(row).length" class="missing-summary">
          缺少：{{ visibleMissingFields(row).join('、') }}<span v-if="remainingMissingCount(row)">，另有 {{ remainingMissingCount(row) }} 项</span>
        </p><p v-else>资料完整</p>
        <button v-if="missingFields(row).length > missingPreviewLimit" type="button" class="expand-missing" :aria-label="missingToggleAriaLabel(row)" :aria-expanded="isExpanded(row) ? 'true' : 'false'" @click.stop="toggleMissing(row)">
          {{ isExpanded(row) ? "收起缺失项" : `查看全部 ${missingFields(row).length} 项` }}
        </button>
        <p class="coverage-counts">{{ coverageText(row) }}</p>
        <button type="button" :aria-label="`去补资料：${employeeAriaName(row)}`" @click.stop="openEmployee(row)">去补资料</button>
      </article>
    </section>
    <div v-if="!loading && !rows.length" class="empty">{{ $route.query.todoType ? '事项已处理' : '暂无待补资料' }}</div>
    <button v-if="hasMore" type="button" class="load-more" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>
  </main>
</template>

<script>
import { getHrMasterDataSummary, listHrCompletenessEmployees } from "@/api/hr/completeness"
import { profileFieldLabel } from "@/views/hr/components/hrFieldConfig"

export default {
  name: "MobileHrCompleteness",
  data() { return { rows: [], total: 0, pageNum: 1, pageSize: 20, loading: false, expandedRows: {}, missingPreviewLimit: 3, masterDataSummary: null } },
  computed: {
    hasMore() { return this.rows.length < this.total },
    canViewMasterData() {
      const permissions = this.$store && this.$store.getters && this.$store.getters.permissions
      return Array.isArray(permissions) && (permissions.includes("*:*:*") || permissions.includes("hr:masterData:list"))
    }
  },
  created() { this.reload() },
  watch: { "$route.fullPath"() { this.reload() } },
  methods: {
    profile(row) { return (row && row.profile) || row || {} },
    employeeAriaName(row) { return String(row && row.employeeName || "").trim() || "该员工" },
    completion(row) {
      const value = Number(row && (row.requiredCompletionPercent ?? row.profileCompletionPercent))
      return Number.isFinite(value) ? Math.max(0, Math.min(100, Math.round(value))) : 0
    },
    missingFields(row) {
      const values = Array.isArray(row && row.missingRequiredFields)
        ? row.missingRequiredFields
        : (Array.isArray(row && row.missingProfileFields) ? row.missingProfileFields : [])
      return values.map(profileFieldLabel)
    },
    rowKey(row) { return String(row && row.userId || "") },
    isExpanded(row) { return Boolean(this.expandedRows[this.rowKey(row)]) },
    visibleMissingFields(row) {
      const fields = this.missingFields(row)
      return this.isExpanded(row) ? fields : fields.slice(0, this.missingPreviewLimit)
    },
    remainingMissingCount(row) {
      return this.isExpanded(row) ? 0 : Math.max(0, this.missingFields(row).length - this.missingPreviewLimit)
    },
    missingToggleAriaLabel(row) {
      const action = this.isExpanded(row) ? "收起" : "展开"
      return `${this.employeeAriaName(row)}：${action}缺失项，共 ${this.missingFields(row).length} 项`
    },
    toggleMissing(row) {
      const key = this.rowKey(row)
      if (!key) return
      this.$set(this.expandedRows, key, !this.expandedRows[key])
    },
    coverageText(row) {
      const complete = Number(row && (row.requiredCompletedFieldCount ?? row.profileCompletedFieldCount)) || 0
      const applicable = Number(row && (row.requiredApplicableFieldCount ?? row.profileApplicableFieldCount)) || 0
      const coverage = Number(row && (row.coveragePercent ?? row.profileCompletionPercent)) || 0
      return `业务必填 ${complete}/${applicable} · 资料覆盖 ${Math.max(0, Math.min(100, Math.round(coverage)))}%`
    },
    reload() {
      this.pageNum = 1; this.rows = []; this.expandedRows = {}
      return Promise.all([this.load(), this.loadMasterDataSummary()])
    },
    loadMasterDataSummary() {
      if (!this.canViewMasterData) { this.masterDataSummary = null; return Promise.resolve() }
      return getHrMasterDataSummary({}).then(response => { this.masterDataSummary = response.data || {} })
        .catch(() => { this.masterDataSummary = null })
    },
    loadMore() { if (!this.loading && this.hasMore) { this.pageNum += 1; return this.load() } },
    load() {
      this.loading = true
      return listHrCompletenessEmployees({
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        completenessStatus: "INCOMPLETE",
        completenessMetric: "REQUIRED",
        deptId: this.$route.query.deptId || this.$route.query.contextDeptId || undefined
      }).then(response => {
        const next = response.rows || []
        this.rows = this.pageNum === 1 ? next : this.rows.concat(next)
        this.total = Number(response.total || this.rows.length)
        if (!this.rows.length && this.$route.query.todoType) this.$store.dispatch("todo/refreshSummaries").catch(() => {})
      }).finally(() => { this.loading = false })
    },
    openEmployee(row) {
      this.$router.push({
        path: "/mobile/hr/employee",
        query: {
          todoType: "HR_PROFILE_INCOMPLETE",
          userId: row.userId,
          businessId: this.$route.query.businessId,
          deptId: this.$route.query.deptId || this.$route.query.contextDeptId
        }
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.mobile-completeness-page{min-height:var(--mobile-viewport-height,100dvh);padding:14px 14px calc(24px + env(safe-area-inset-bottom));background:var(--mobile-color-page,#f4f5f2);color:var(--mobile-color-ink,#17211d)}.mobile-completeness-page header{display:grid;grid-template-columns:44px 1fr 44px;align-items:center;gap:8px}.mobile-completeness-page header button{width:44px;height:44px;border:0;border-radius:12px;background:var(--mobile-color-surface,#fff);font-size:20px;color:var(--mobile-color-primary,#0b6b53)}.mobile-completeness-page h1{margin:2px 0;font-size:22px;font-weight:700}.mobile-completeness-page small,.mobile-completeness-page p{color:var(--mobile-color-muted,#66736d)}.hint{padding:11px 12px;border-radius:12px;background:var(--mobile-color-info-soft,#edf4fd);color:var(--mobile-color-info,#2866b1);font-size:13px}.profile-list{display:grid;gap:10px}.profile-list article{padding:14px;border:1px solid var(--mobile-color-line,#dde2de);border-radius:16px;background:var(--mobile-color-surface,#fff)}.title{display:flex;justify-content:space-between}.title h2{margin:0;font-size:17px;font-weight:700}.title p{margin:4px 0}.title strong{color:var(--mobile-color-primary,#0b6b53)}.progress{height:8px;overflow:hidden;border-radius:999px;background:var(--mobile-color-line,#dde2de)}.progress i{display:block;height:100%;background:var(--mobile-color-primary,#0b6b53)}.missing-summary{line-height:1.65}.profile-list article>button,.load-more{width:100%;height:40px;margin-top:8px;border:0;border-radius:10px;background:#2563eb;color:#fff;font-weight:600}.profile-list article>.expand-missing{height:auto;padding:5px 0;background:transparent;color:#2563eb;text-align:left;font-weight:500}.load-more{margin-top:14px}.empty{padding:30px;text-align:center;color:#64748b}
.master-summary{margin:0 0 12px;padding:14px;border-radius:16px;background:#fff}.master-summary h2{margin:2px 0 10px;font-size:17px}.master-summary dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:0}.master-summary dl div{padding:9px;border-radius:10px;background:#f8fafc}.master-summary dt{font-size:12px;color:#64748b}.master-summary dd{margin:4px 0 0;font-size:20px;font-weight:700}.master-summary p{margin:10px 0 0;font-size:12px}
.mobile-completeness-page{background:var(--mobile-color-page);color:var(--mobile-color-ink)}
.mobile-completeness-page header{grid-template-columns:44px 1fr 44px}
.mobile-completeness-page header button{width:44px;height:44px;color:var(--mobile-color-primary);background:var(--mobile-color-surface);font-size:20px}
.hint{color:var(--mobile-color-primary);background:var(--mobile-color-primary-soft)}
.master-summary,.profile-list article{border:1px solid var(--mobile-color-line);border-radius:var(--mobile-radius-lg);background:var(--mobile-color-surface)}
.master-summary dl div{background:var(--mobile-color-surface-soft)}
.title strong,.profile-list article>.expand-missing{color:var(--mobile-color-primary)}
.progress{background:var(--mobile-color-line)}
.progress i{background:var(--mobile-color-primary)}
.profile-list article>button,.load-more{min-height:44px;height:44px;background:var(--mobile-color-primary)}
.profile-list article>.expand-missing{min-height:44px;height:auto;padding:8px 0;background:transparent}
.mobile-completeness-page button:focus-visible{outline:3px solid rgba(11,107,83,.24);outline-offset:2px}
</style>
