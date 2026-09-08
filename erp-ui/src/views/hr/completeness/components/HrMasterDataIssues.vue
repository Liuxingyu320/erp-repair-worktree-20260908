<template>
  <section class="master-data-panel">
    <el-alert
      class="observation-note"
      :type="summary.enforcementEnabled ? 'warning' : 'info'"
      :closable="false"
      show-icon
      :title="observationTitle"
    />

    <div class="master-summary">
      <div><span>问题总数</span><strong>{{ summary.totalIssueCount || 0 }}</strong></div>
      <div class="is-blocking"><span>阻断风险</span><strong>{{ summary.blockingIssueCount || 0 }}</strong></div>
      <div><span>重要问题</span><strong>{{ summary.importantIssueCount || 0 }}</strong></div>
      <div><span>去重受影响员工</span><strong>{{ summary.affectedEmployeeCount || 0 }}</strong></div>
      <div><span>问题覆盖人次</span><strong>{{ summary.affectedOccurrenceCount || 0 }}</strong></div>
    </div>

    <el-form inline size="small" class="master-filter" @submit.native.prevent>
      <el-form-item label="搜索">
        <el-input v-model.trim="query.keyword" clearable placeholder="问题 / 组织 / 岗位" @keyup.enter.native="search" />
      </el-form-item>
      <el-form-item label="问题类型">
        <el-select v-model="query.issueCode" clearable filterable placeholder="全部">
          <el-option v-for="item in issueCodes" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select v-model="query.severity" clearable placeholder="全部">
          <el-option label="阻断风险" value="P0" />
          <el-option label="重要问题" value="P1" />
          <el-option label="一般问题" value="P2" />
        </el-select>
      </el-form-item>
      <el-form-item><el-checkbox v-model="query.affectedOnly">只看已影响员工</el-checkbox></el-form-item>
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
      <el-button v-if="canExport" size="small" icon="el-icon-download" @click="exportIssues">导出问题</el-button>
    </el-form>

    <el-table v-accessible-table="'人事主数据问题列表'" v-loading="loading" :data="rows" :row-key="rowKey">
      <el-table-column label="问题" min-width="210">
        <template slot-scope="scope">
          <div class="issue-title"><el-tag size="mini" :type="severityType(scope.row.severity)">{{ severityLabel(scope.row.severity) }}</el-tag><strong>{{ scope.row.issueName }}</strong></div>
        </template>
      </el-table-column>
      <el-table-column label="对象" min-width="180">
        <template slot-scope="scope">
          <strong>{{ scope.row.resourceName || scope.row.resourceId || '-' }}</strong>
          <div class="muted">{{ resourceTypeLabel(scope.row.resourceType) }}<span v-if="scope.row.employeeCategory"> · {{ employeeCategoryLabel(scope.row.employeeCategory) }}</span></div>
        </template>
      </el-table-column>
      <el-table-column prop="deptName" label="组织" min-width="140" />
      <el-table-column prop="affectedEmployeeCount" label="该问题影响员工" width="130" align="center" />
      <el-table-column label="说明与建议" min-width="280">
        <template slot-scope="scope"><div>{{ scope.row.detail }}</div><div class="suggestion">建议：{{ scope.row.suggestion }}</div></template>
      </el-table-column>
      <el-table-column label="处理" width="90" fixed="right">
        <template slot-scope="scope">
          <el-button v-if="canOpenAction(scope.row)" type="text" @click="openAction(scope.row)">{{ actionLabel(scope.row) }}</el-button>
          <span v-else class="muted">仅查看</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-if="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
  </section>
</template>

<script>
import { getHrMasterDataIssueCodes, getHrMasterDataSummary, listHrMasterDataIssues } from "@/api/hr/completeness"

const defaultQuery = () => ({ pageNum: 1, pageSize: 10, keyword: "", issueCode: undefined, severity: undefined, affectedOnly: false })

export default {
  name: "HrMasterDataIssues",
  data() {
    return { query: defaultQuery(), rows: [], total: 0, summary: {}, issueCodes: [], loading: false, requestSequence: 0 }
  },
  created() { this.load() },
  beforeDestroy() { this.requestSequence += 1 },
  computed: {
    permissions() {
      const values = this.$store && this.$store.getters && this.$store.getters.permissions
      return Array.isArray(values) ? values : []
    },
    canExport() { return this.hasPermission("hr:masterData:export") },
    observationTitle() {
      if (this.summary.readinessEnabled === false) return "主数据只读检查当前未启用。"
      if (this.summary.enforcementEnabled) return "主数据门禁已开启：组织变更及在用岗位停用、删除将执行就绪校验；存量问题仍通过清单持续治理。"
      return "当前为观察模式：只检查、只提示，不自动修改数据，也不阻断现有业务。"
    }
  },
  methods: {
    hasPermission(permission) { return this.permissions.includes("*:*:*") || this.permissions.includes(permission) },
    load() {
      const sequence = ++this.requestSequence
      this.loading = true
      return Promise.all([
        getHrMasterDataSummary({ ...this.query, pageNum: undefined, pageSize: undefined }),
        listHrMasterDataIssues(this.query),
        this.issueCodes.length ? Promise.resolve({ data: this.issueCodes }) : getHrMasterDataIssueCodes()
      ]).then(([summaryResponse, issuesResponse, codesResponse]) => {
        if (sequence !== this.requestSequence) return
        this.summary = summaryResponse.data || {}
        this.rows = Array.isArray(issuesResponse.rows) ? issuesResponse.rows : []
        this.total = Number(issuesResponse.total) || 0
        this.issueCodes = Array.isArray(codesResponse.data) ? codesResponse.data : []
        this.$emit("summary", this.summary)
      }).catch(error => {
        if (sequence !== this.requestSequence) return
        this.summary = {}; this.rows = []; this.total = 0
        this.$message.error((error && error.message) || "主数据问题加载失败")
      }).finally(() => { if (sequence === this.requestSequence) this.loading = false })
    },
    reload() { return this.load() },
    search() { this.query.pageNum = 1; return this.load() },
    reset() { this.query = defaultQuery(); return this.load() },
    rowKey(row) { return `${row.issueCode || "issue"}:${row.resourceType || "resource"}:${row.resourceId || "unknown"}` },
    severityType(value) { return value === "P0" ? "danger" : value === "P1" ? "warning" : "info" },
    severityLabel(value) { return { P0: "阻断", P1: "重要", P2: "一般" }[value] || "待确认" },
    resourceTypeLabel(value) {
      return { DEPARTMENT: "组织", EMPLOYEE: "员工", POST: "岗位", POSITION_CONFIG: "岗位配置", POSITION_CONFIG_PAIR: "岗位与人员类别", SYSTEM_CONFIG: "系统参数" }[value] || "其他对象"
    },
    employeeCategoryLabel(value) {
      const labels = { FULL_TIME: "全职", PART_TIME: "兼职", INTERN: "实习生", LABOR_DISPATCH: "劳务派遣", OUTSOURCED: "外包人员" }
      return labels[String(value || "").toUpperCase()] || (/[^\x00-\x7F]/.test(String(value || "")) ? value : "其他人员类别")
    },
    actionLabel(row) { return row && row.issueCode === "EMPLOYEE_PROFILE_MISSING" ? "建立档案" : "去处理" },
    canOpenAction(row) {
      const url = row && row.actionUrl
      if (typeof url !== "string" || url.startsWith("//")) return false
      if (url.startsWith("/system/dept")) return this.hasPermission("system:dept:edit")
      if (url.startsWith("/system/post")) return this.hasPermission("system:post:edit")
      if (url.startsWith("/system/config")) return this.hasPermission("system:config:edit")
      if (url.startsWith("/hr/employee")) return this.hasPermission("hr:employee:edit")
      if (url.startsWith("/hr/position-config")) return this.hasPermission("hr:onboarding:config")
      return false
    },
    openAction(row) { if (this.canOpenAction(row)) this.$router.push(row.actionUrl) },
    exportIssues() {
      if (!this.canExport) return
      return this.download("/system/hr/completeness/master-data/export", { ...this.query, pageNum: undefined, pageSize: undefined }, `人事主数据问题_${Date.now()}.xlsx`)
    }
  }
}
</script>

<style lang="scss" scoped>
.observation-note { margin: 4px 0 14px; }
.master-summary { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:10px; margin-bottom:14px; }
.master-summary>div { padding:12px 14px; border:1px solid #e6eaf0; border-radius:8px; background:#f8fafc; color:#64748b; }
.master-summary strong { display:block; margin-top:5px; color:#172033; font-size:21px; }
.master-summary .is-blocking { border-left:4px solid #e25555; }
.master-filter { margin-bottom:4px; }
.issue-title { display:flex; align-items:center; gap:7px; margin-bottom:5px; }
.muted { color:#8490a3; font-size:12px; margin-top:4px; }
.suggestion { margin-top:5px; color:#8a6324; font-size:12px; }
@media (max-width:1000px) { .master-summary { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>
