<template>
  <div class="app-container completeness-page">
    <header class="page-heading">
      <div><h2>人事资料治理</h2><p>业务必填用于待办闭环，资料覆盖度用于持续完善；主数据检查当前只读观察。</p></div>
      <el-button icon="el-icon-refresh" :loading="loading" @click="loadData">刷新</el-button>
    </header>

    <section class="summary-grid">
      <button class="summary-card" type="button" @click="switchSummary('all')">
        <span>员工总数</span><strong>{{ summary.totalEmployeeCount || 0 }}</strong>
      </button>
      <button class="summary-card" type="button" @click="switchSummary('incomplete')">
        <span>业务必填待补</span><strong>{{ requiredIncompleteCount }}</strong>
      </button>
      <div class="summary-card"><span>平均业务必填完成率</span><strong>{{ requiredAverage }}%</strong></div>
      <div class="summary-card"><span>平均资料覆盖度</span><strong>{{ coverageAverage }}%</strong></div>
      <button class="summary-card summary-card--risk" type="button" @click="switchTab('accountRisk')">
        <span>账号待配置</span><strong>{{ summary.accountConfigurationRiskCount || 0 }}</strong>
      </button>
      <button v-if="canViewMasterData" class="summary-card summary-card--master" type="button" @click="switchTab('masterData')">
        <span>主数据问题</span><strong>{{ masterDataSummary.totalIssueCount || 0 }}</strong>
      </button>
    </section>

    <el-card shadow="never" class="queue-card">
      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane label="员工资料" name="employees" />
        <el-tab-pane label="部门汇总" name="departments" />
        <el-tab-pane name="accountRisk"><span slot="label"><i class="el-icon-key" /> 账号待配置</span></el-tab-pane>
        <el-tab-pane v-if="canViewMasterData" name="masterData"><span slot="label"><i class="el-icon-connection" /> 主数据问题</span></el-tab-pane>
      </el-tabs>

      <el-form v-if="activeTab !== 'masterData'" inline size="small" @submit.native.prevent>
        <el-form-item label="搜索">
          <el-input v-model.trim="query.keyword" clearable placeholder="工号 / 姓名" @keyup.enter.native="search" />
        </el-form-item>
        <el-form-item label="档案状态" v-if="activeTab !== 'departments'">
          <el-select v-model="query.completenessStatus" clearable placeholder="全部">
            <el-option label="待补齐" value="INCOMPLETE" /><el-option label="已完整" value="COMPLETE" />
          </el-select>
        </el-form-item>
        <el-button type="primary" size="small" @click="search">查询</el-button>
        <el-button size="small" @click="resetQuery">重置</el-button>
      </el-form>

      <el-alert v-if="activeTab === 'accountRisk'" class="risk-note" type="info" :closable="false"
        title="此队列由服务端根据账号配置缺失状态生成，用于跟进账号与岗位权限配置。" />

      <hr-master-data-issues v-if="activeTab === 'masterData'" ref="masterDataIssues" @summary="onMasterDataSummary" />

      <el-table v-else-if="activeTab === 'departments'" v-accessible-table="'部门资料完整度列表'" v-loading="loading" :data="rows" row-key="departmentId">
        <el-table-column label="部门" min-width="180">
          <template slot-scope="scope">
            <el-button type="text" @click="openDepartment(scope.row)">{{ scope.row.departmentName || "未命名部门" }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="employeeCount" label="员工数" width="120" />
        <el-table-column label="业务必填 / 资料覆盖" min-width="250">
          <template slot-scope="scope">
            <el-progress :percentage="requiredPercent(scope.row)" />
            <div class="coverage-line">资料覆盖 {{ coveragePercent(scope.row) }}%</div>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-accessible-table="'员工资料完整度列表'" v-loading="loading" :data="rows" row-key="userId">
        <el-table-column prop="employeeNo" label="工号" width="120" />
        <el-table-column prop="employeeName" label="姓名" min-width="120" />
        <el-table-column prop="departmentName" label="部门" min-width="140" />
        <el-table-column label="入职资料" min-width="180">
          <template slot-scope="scope">
            <el-progress v-if="scope.row.onboardingId" :percentage="percent(scope.row.onboardingCompletionPercent)" />
            <span v-else class="muted">无关联入职单</span>
            <el-tag v-if="scope.row.onboardingStatus" size="mini" type="info">{{ onboardingStatusLabel(scope.row.onboardingStatus) }}</el-tag>
            <div v-if="missingLabels(scope.row).length" class="missing-fields">待补：{{ missingLabels(scope.row).join('、') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="业务必填 / 资料覆盖" min-width="190">
          <template slot-scope="scope">
            <el-progress :percentage="requiredPercent(scope.row)" />
            <div class="coverage-line">资料覆盖 {{ coveragePercent(scope.row) }}%</div>
            <div v-if="profileMissing(scope.row).visible.length" class="profile-missing-tags">
              <el-tag v-for="label in profileMissing(scope.row).visible" :key="label" size="mini" type="warning">{{ label }}</el-tag>
              <span v-if="profileMissing(scope.row).remaining">另有 {{ profileMissing(scope.row).remaining }} 项</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="补齐期限" width="130">
          <template slot-scope="scope">
            <span>{{ scope.row.postEntryDueDate || "未开始" }}</span>
            <el-tag v-if="scope.row.postEntryOverdue" size="mini" type="warning">已逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号配置" min-width="160">
          <template slot-scope="scope">
            <el-tag :type="scope.row.accountConfigurationStatus === 'MISSING' ? 'warning' : 'success'" size="mini">
              {{ scope.row.accountConfigurationStatus === "MISSING" ? "账号待配置" : "已配置" }}
            </el-tag>
            <div v-for="code in riskCodes(scope.row)" :key="code" class="risk-code">{{ riskLabel(code) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="处理" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button v-if="canOpenEmployeeLink" type="text" @click="openSafeLink(scope.row.employeeDetailUrl)">员工档案</el-button>
            <el-button v-if="canOpenOnboardingLink && scope.row.onboardingDetailUrl" type="text" @click="openSafeLink(scope.row.onboardingDetailUrl)">入职单</el-button>
            <el-button v-if="canOpenPositionConfigLink && scope.row.positionConfigurationUrl" type="text" @click="openSafeLink(scope.row.positionConfigurationUrl)">岗位配置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-if="activeTab !== 'departments' && activeTab !== 'masterData' && total > 0" :total="total"
        :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadData" />
    </el-card>
  </div>
</template>

<script>
import { getHrCompletenessDepartments, getHrCompletenessSummary, getHrMasterDataSummary, listHrCompletenessEmployees } from "@/api/hr/completeness"
import { missingProfileLabels } from "@/views/hr/components/hrFieldConfig"

const positiveId = value => /^\d+$/.test(String(value || "")) && Number.isSafeInteger(Number(value)) && Number(value) > 0
  ? Number(value)
  : undefined
const defaultQuery = (route = {}) => ({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  deptId: positiveId(route.deptId || route.contextDeptId),
  completenessStatus: String(route.completenessStatus || "").toUpperCase() === "INCOMPLETE"
    ? "INCOMPLETE"
    : undefined,
  completenessMetric: "REQUIRED"
})
const ACCOUNT_RISK_LABELS = {
  ROLE_CONFIGURATION_MISSING: "岗位角色配置缺失",
  DATA_SCOPE_CONFIGURATION_MISSING: "数据范围配置缺失",
  ACCOUNT_CONFIGURATION_MISSING: "账号配置缺失",
  ACCOUNT_CONFIGURATION_FAILED: "账号配置失败"
}

export default {
  name: "HrCompleteness",
  components: { HrMasterDataIssues: () => import("./components/HrMasterDataIssues.vue") },
  data() {
    const route = this.$route && this.$route.query ? this.$route.query : {}
    return { activeTab: "employees", query: defaultQuery(route), summary: {}, masterDataSummary: {}, rows: [], total: 0, loading: false, requestSequence: 0 }
  },
  created() { this.loadData() },
  beforeDestroy() { this.requestSequence += 1 },
  computed: {
    canOpenEmployeeLink() { return this.hasAllPermissions(["hr:employee:list", "hr:employee:query"]) },
    canOpenOnboardingLink() { return this.hasAllPermissions(["hr:onboarding:list", "hr:onboarding:query"]) },
    canOpenPositionConfigLink() { return this.hasAllPermissions(["hr:onboarding:config"]) },
    canViewMasterData() { return this.hasAllPermissions(["hr:masterData:list"]) },
    requiredIncompleteCount() { return Number(this.summary.requiredIncompleteEmployeeCount ?? this.summary.incompleteEmployeeCount) || 0 },
    requiredAverage() { return this.percent(this.summary.averageRequiredCompletionPercent ?? this.summary.averageProfileCompletionPercent) },
    coverageAverage() { return this.percent(this.summary.averageCoveragePercent ?? this.summary.averageProfileCompletionPercent) }
  },
  methods: {
    hasAllPermissions(required) {
      const permissions = this.$store && this.$store.getters && Array.isArray(this.$store.getters.permissions)
        ? this.$store.getters.permissions
        : []
      return permissions.includes("*:*:*") || required.every(permission => permissions.includes(permission))
    },
    requestParams() {
      return this.activeTab === "accountRisk"
        ? { ...this.query, accountConfigurationStatus: "MISSING" }
        : { ...this.query }
    },
    loadData() {
      const sequence = ++this.requestSequence
      const params = this.requestParams()
      this.loading = true
      const summaryRequest = getHrCompletenessSummary({ ...params, pageNum: undefined, pageSize: undefined })
      const rowsRequest = this.activeTab === "departments"
        ? getHrCompletenessDepartments({ ...params, pageNum: undefined, pageSize: undefined })
        : this.activeTab === "masterData" ? Promise.resolve({ rows: [], total: 0 }) : listHrCompletenessEmployees(params)
      const masterSummaryRequest = this.canViewMasterData
        ? getHrMasterDataSummary({})
        : Promise.resolve({ data: {} })
      return Promise.all([summaryRequest, rowsRequest, masterSummaryRequest]).then(([summaryResponse, rowsResponse, masterResponse]) => {
        if (sequence !== this.requestSequence) return
        this.summary = summaryResponse.data || {}
        this.rows = this.activeTab === "departments"
          ? (Array.isArray(rowsResponse.data) ? rowsResponse.data : [])
          : (Array.isArray(rowsResponse.rows) ? rowsResponse.rows : [])
        this.total = this.activeTab === "departments" ? this.rows.length : (Number(rowsResponse.total) || 0)
        this.masterDataSummary = masterResponse.data || this.masterDataSummary || {}
      }).catch(error => {
        if (sequence !== this.requestSequence) return
        this.summary = {}; this.rows = []; this.total = 0
        this.$message.error((error && error.message) || "资料完整度加载失败")
      }).finally(() => { if (sequence === this.requestSequence) this.loading = false })
    },
    handleTabClick() { this.query.pageNum = 1; return this.loadData() },
    switchTab(tab) { if (tab === "masterData" && !this.canViewMasterData) return Promise.resolve(); this.activeTab = tab; return this.handleTabClick() },
    switchSummary(type) {
      this.activeTab = "employees"
      this.query.pageNum = 1
      this.query.completenessStatus = type === "incomplete" ? "INCOMPLETE" : undefined
      return this.loadData()
    },
    openDepartment(row) {
      const deptId = Number(row && (row.deptId || row.departmentId))
      if (!Number.isSafeInteger(deptId) || deptId <= 0) return Promise.resolve(null)
      this.activeTab = "employees"
      this.query = { ...this.query, pageNum: 1, deptId, completenessStatus: "INCOMPLETE" }
      return this.loadData()
    },
    search() { this.query.pageNum = 1; return this.loadData() },
    resetQuery() { this.query = defaultQuery(); return this.loadData() },
    percent(value) { const number = Number(value); return Number.isFinite(number) ? Math.min(100, Math.max(0, number)) : 0 },
    requiredPercent(row) { return this.percent(row && (row.requiredCompletionPercent ?? row.profileCompletionPercent)) },
    coveragePercent(row) { return this.percent(row && (row.coveragePercent ?? row.profileCompletionPercent)) },
    profileMissing(row) { return missingProfileLabels(row) },
    onMasterDataSummary(value) { this.masterDataSummary = value || {} },
    missingLabels(row) { return Array.isArray(row.missingOnboardingFields) ? row.missingOnboardingFields.map(item => item.label || item.key).filter(Boolean) : [] },
    riskCodes(row) { return Array.isArray(row.accountConfigurationRiskCodes) ? row.accountConfigurationRiskCodes : [] },
    riskLabel(code) { return ACCOUNT_RISK_LABELS[code] || "其他账号风险" },
    onboardingStatusLabel(status) { return { DRAFT: "草稿", READY: "待确认", CONFIRMED: "已确认", CANCELLED: "已取消" }[status] || "未知入职状态" },
    openSafeLink(url) {
      if (typeof url !== "string" || url.startsWith("//")) return
      if (!/^\/hr\/(employee|onboarding|position-config)(?:[/?]|$)/.test(url)) return
      if (url.startsWith("/hr/employee") && !this.canOpenEmployeeLink) return
      if (url.startsWith("/hr/onboarding") && !this.canOpenOnboardingLink) return
      if (url.startsWith("/hr/position-config") && !this.canOpenPositionConfigLink) return
      this.$router.push(url)
    }
  }
}
</script>

<style lang="scss" scoped>
.completeness-page { background: #f6f8fb; min-height: calc(100vh - 84px); }
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:18px; }
.page-heading h2 { margin:0 0 6px; color:#172033; } .page-heading p { margin:0; color:#64748b; }
.summary-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:14px; margin-bottom:16px; }
.summary-card { text-align:left; border:1px solid #e4e9f1; border-radius:10px; background:#fff; padding:18px; color:#64748b; }
button.summary-card { cursor:pointer; } .summary-card strong { display:block; margin-top:8px; color:#172033; font-size:26px; }
.summary-card--risk { border-left:4px solid #d99c35; } .summary-card--master { border-left:4px solid #5577d1; } .queue-card { border-radius:10px; }
.risk-note { margin:12px 0; } .muted,.missing-fields,.risk-code { color:#7b8798; font-size:12px; }
.missing-fields,.risk-code { margin-top:5px; } .risk-code { color:#9a6a22; }
.profile-missing-tags { display:flex; flex-wrap:wrap; gap:4px; margin-top:6px; color:#7b8798; font-size:12px; }
.coverage-line { margin-top:4px; color:#7b8798; font-size:12px; }
@media (max-width: 1000px) { .summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>
