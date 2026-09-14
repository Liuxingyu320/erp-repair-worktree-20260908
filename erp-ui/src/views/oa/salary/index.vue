<template>
  <div class="app-container oa-workspace-page oa-salary-page">
    <legacy-salary-notice />
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 历史工资</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-money" /></span>
          <div>
            <h1>历史工资记录</h1>
            <p>查询和导出已有工资记录；当前工资以员工档案确认的合同工资为准。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>历史记录月份</span>
        <strong>{{ salaryMonth || '未选择' }}</strong>
        <small>{{ viewScope === 'all' ? '全部员工视图' : '个人工资视图' }}</small>
      </div>
    </section>

    <section class="oa-metric-grid" aria-label="工资概览">
      <div class="oa-metric-card">
        <span>当前记录</span>
        <strong>{{ total }} 条</strong>
        <small>按当前月份和视图统计</small>
      </div>
      <div class="oa-metric-card oa-metric-card--cyan">
        <span>查看范围</span>
        <strong>{{ viewScope === 'all' ? '全部员工' : '仅本人' }}</strong>
        <small>权限范围决定可见数据</small>
      </div>
      <div class="oa-metric-card oa-metric-card--success">
        <span>本页实发合计</span>
        <strong>{{ pagePayrollAmount }}</strong>
        <small>仅汇总当前表格页数据</small>
      </div>
      <div class="oa-metric-card oa-metric-card--warning">
        <span>核算依据</span>
        <strong>考勤联动</strong>
        <small>历史金额保持原样</small>
      </div>
    </section>

    <el-card shadow="never" class="search-card oa-filter-card salary-filter-card">
      <el-form inline size="small">
        <el-form-item v-if="canViewAllSalary()" label="视图">
          <el-radio-group v-model="viewScope" size="small" @change="handleScopeChange">
            <el-radio-button label="my">我的</el-radio-button>
            <el-radio-button label="all">全部</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="工资月份">
          <el-date-picker v-model="salaryMonth" type="month" value-format="yyyy-MM" placeholder="选择月份" size="small"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" @click="handleQuery">查询</el-button>


          <el-button v-hasPermi="['oa:salary:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card oa-table-card">
      <div slot="header" class="oa-card-heading">
        <div class="oa-card-heading__title">
          <span class="oa-card-heading__icon"><i class="el-icon-s-data" /></span>
          <div>
            <h2>工资明细</h2>
            <p>按所选月份查询已保存的工资记录</p>
          </div>
        </div>
        <el-tag size="small" type="info">{{ salaryMonth || '-' }}</el-tag>
      </div>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
      <el-button v-if="listError" size="mini" @click="getList">重试当前查询</el-button>
      <el-table v-loading="loading" :data="list" size="small" border :empty-text="salaryEmptyText">
        <el-table-column label="月份" prop="salaryMonth" width="90" fixed/>
        <el-table-column label="用户" prop="userName" width="100"/>
        <el-table-column label="应出勤(分)" prop="scheduledMinutes" width="100"/>
        <el-table-column label="核定工作(分)" prop="workedMinutes" width="110"/>
        <el-table-column label="带薪假(分)" prop="paidLeaveMinutes" width="95"/>
        <el-table-column label="无薪假(分)" prop="unpaidLeaveMinutes" width="95"/>
        <el-table-column label="缺勤(分)" prop="absenceMinutes" width="90"/>
        <el-table-column label="迟到(分)" prop="lateTotalMinutes" width="80"/>
        <el-table-column label="早退(分)" prop="earlyTotalMinutes" width="80"/>
        <el-table-column label="加班(h)" prop="overtimeHours" width="80"/>
        <el-table-column label="合同综合工资" prop="baseSalary" width="100"/>
        <el-table-column label="迟到扣款" prop="lateDeduction" width="90"/>
        <el-table-column label="早退扣款" prop="earlyDeduction" width="90"/>
        <el-table-column label="缺勤扣款" prop="absentDeduction" width="90"/>
        <el-table-column label="加班费" prop="overtimePay" width="90"/>
        <el-table-column label="其他奖金" prop="otherBonus" width="90"/>
        <el-table-column label="其他扣款" prop="otherDeduction" width="90"/>
        <el-table-column label="实发工资" prop="totalSalary" width="100" fixed="right">
          <template slot-scope="scope">
            <strong class="salary-total">{{ scope.row.totalSalary }}</strong>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog title="工资参数" :visible.sync="configOpen" width="720px" append-to-body>
      <el-form
        ref="configForm"
        v-loading="configLoading"
        :model="salaryConfig"
        :rules="configRules"
        label-width="128px"
        size="small"
      >
        <el-alert
          title="上下班时间由考勤中心的班次和已发布排班决定"
          description="这里的日标准工时仅用于把无薪假、缺勤分钟折算为扣款，不再控制打卡时间。"
          type="info"
          :closable="false"
          show-icon
          class="salary-config-alert"
        />
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="日标准工时" prop="workHoursPerDay">
              <el-input-number v-model="salaryConfig.workHoursPerDay" :min="0.5" :precision="2" :step="0.5" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="迟到扣款/分钟" prop="latePenaltyPerMin">
              <el-input-number v-model="salaryConfig.latePenaltyPerMin" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="早退扣款/分钟" prop="earlyPenaltyPerMin">
              <el-input-number v-model="salaryConfig.earlyPenaltyPerMin" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="缺勤扣款/天" prop="absentPenaltyPerDay">
              <el-input-number v-model="salaryConfig.absentPenaltyPerDay" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="加班费/小时" prop="overtimePayPerHour">
              <el-input-number v-model="salaryConfig.overtimePayPerHour" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button @click="configOpen = false">取 消</el-button>

      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getSelectedDeptId } from "@/utils/shopContext"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
import LegacySalaryNotice from "@/views/hr/components/LegacySalaryNotice"
import { listMySalary, listAllSalary, calculateSalary, preflightSalaryAttendance, getSalaryConfig, saveSalaryConfig } from "@/api/oa/salary"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"

export default {
  components: { LegacySalaryNotice },
  name: "OaSalary",
  data() {
    return {
      loading: false,
      listError: "",
      calcLoading: false,
      configOpen: false,
      configLoading: false,
      configSaving: false,
      total: 0,
      list: [],
      salaryMonth: "",
      viewScope: "my",
      queryParams: { pageNum: 1, pageSize: 10 },
      salaryConfig: this.defaultSalaryConfig(),
      configRules: {
        workHoursPerDay: [{ required: true, message: "请输入日标准工时", trigger: "blur" }],
        latePenaltyPerMin: [{ required: true, message: "请输入迟到扣款", trigger: "blur" }],
        earlyPenaltyPerMin: [{ required: true, message: "请输入早退扣款", trigger: "blur" }],
        absentPenaltyPerDay: [{ required: true, message: "请输入缺勤扣款", trigger: "blur" }],
        overtimePayPerHour: [{ required: true, message: "请输入加班费", trigger: "blur" }]
      }
    }
  },
  watch: {
    actorContextKey() { this.resetSalaryContext() },
    salaryMonth() { this.queryParams.pageNum = 1; this.getList() }
  },
  activated() {
    this.salaryScope().activate()
    if (this._refreshSalaryOnActivate) { this._refreshSalaryOnActivate = false; this.getList() }
  },
  deactivated() { this.salaryScope().deactivate(); this.list = []; this.total = 0; this.loading = false; this._refreshSalaryOnActivate = true },
  beforeDestroy() { window.removeEventListener("erp:dept-changed", this.resetSalaryContext); this.salaryScope().deactivate() },
  created() {
    window.addEventListener("erp:dept-changed", this.resetSalaryContext)
    const now = new Date()
    this.salaryMonth = now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0")
  },
  computed: {
    actorContextKey() {
      const store = this.$store || {}, getters = store.getters || {}
      return JSON.stringify([String(getters.id || ""), ((store.state || {}).user || {}).sessionRevision || 0, getters.permissions || []])
    },
    salaryEmptyText() {
      return getBusinessEmptyText("salary", "missingBaseline")
    },
    pagePayrollAmount() {
      const amount = (this.list || []).reduce((sum, item) => sum + Number(item.totalSalary || 0), 0)
      return "¥" + amount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    }
  },
  methods: {
    salaryScope() {
      if (!this._salaryScope) this._salaryScope = createUiOperationScope(() => ({ actor: this.actorContextKey, dept: getSelectedDeptId(), route: this.$route && this.$route.path }))
      return this._salaryScope
    },
    resetSalaryContext() {
      this.salaryScope().invalidate()
      this.list = []
      this.total = 0
      this.loading = false
      this.configOpen = false
      this.salaryConfig = this.defaultSalaryConfig()
      if ((this.$store.getters || {}).id) this.getList()
    },
    salaryQuerySnapshot() {
      const params = { ...this.queryParams }
      if (this.salaryMonth) params.salaryMonth = this.salaryMonth
      return { scope: this.viewScope === "all" && this.canViewAllSalary() ? "all" : "my", params }
    },
    defaultSalaryConfig() {
      return {
        configId: undefined,
        shopDeptId: undefined,
        workStartTime: "09:00:00",
        workEndTime: "18:00:00",
        workHoursPerDay: 8,
        latePenaltyPerMin: 1,
        earlyPenaltyPerMin: 1,
        absentPenaltyPerDay: 100,
        overtimePayPerHour: 20
      }
    },
    getList() {
      const query = this.salaryQuerySnapshot(), scope = this.salaryScope(), token = scope.begin("list", query)
      this.loading = true
      this.listError = ""
      this.list = []
      this.total = 0
      const request = query.scope === "all" ? listAllSalary : listMySalary
      return request(query.params, { silentError: true }).then(res => {
        if (!scope.isCurrent(token, this.salaryQuerySnapshot())) return
        this.list = res.rows || []
        this.total = Number(res.total) || 0
      }).catch(error => {
        if (scope.isCurrent(token, this.salaryQuerySnapshot())) this.listError = error && error.message || "工资记录读取失败，请重试"
      }).finally(() => { if (scope.isCurrent(token, this.salaryQuerySnapshot())) this.loading = false })
    },
    canViewAllSalary() {
      return this.$auth && this.$auth.hasPermi("oa:salary:list")
    },
    handleScopeChange() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    doCalculate() {
      if (!this.salaryMonth) {
        this.$modal.msgWarning("请选择工资月份")
        return Promise.resolve()
      }
      this.calcLoading = true
      const shopDeptId = this.salaryConfig && this.salaryConfig.shopDeptId
      return preflightSalaryAttendance({ salaryMonth: this.salaryMonth, shopDeptId }).then(res => {
        const preflight = res.data || {}
        if (preflight.blocked) {
          return this.showAttendancePreflightWarning(preflight)
        }
        return this.$modal.confirm("确认重新计算 " + this.salaryMonth + " 的工资？将覆盖已有数据。").then(() => {
          return calculateSalary({ salaryMonth: this.salaryMonth, shopDeptId }).then(() => {
            this.$modal.msgSuccess("工资计算完成")
            this.getList()
          })
        })
      }).finally(() => {
        this.calcLoading = false
      })
    },
    showAttendancePreflightWarning(preflight) {
      const salaryMissing = (preflight.issues || []).some(issue => issue.category === "SALARY_PROFILE")
      const issues = (preflight.issues || []).slice(0, 5).map(issue => {
        const employee = issue.nickName || issue.userName || issue.userId || "未知员工"
        const date = String(issue.workDate || (issue.category === "SALARY_PROFILE" ? "员工档案" : "本月排班")).slice(0, 10)
        return employee + " " + date + "（" + (issue.reason || "考勤状态异常") + "）"
      })
      const message = [
        "本月有 " + Number(preflight.exceptionRecordCount || 0) + " 条工资计算待处理项，涉及 " +
          Number(preflight.affectedEmployeeCount || 0) + " 名员工，工资尚未计算。",
        ...issues
      ].join("\n")
      return this.$modal.confirm(message, salaryMissing ? "合同工资待入档" : "考勤异常", {
        confirmButtonText: salaryMissing ? "查看员工档案" : "查看异常考勤",
        cancelButtonText: "暂不处理",
        type: "warning",
        dangerouslyUseHTMLString: false
      }).then(() => {
        if (salaryMissing) return this.$router.push({ path: "/hr/employee" })
        return this.$router.push({
          path: "/oa/attendance-v2",
          query: {
            tab: "day",
            salaryMonth: this.salaryMonth,
            shopId: String(preflight.shopDeptId || (this.salaryConfig && this.salaryConfig.shopDeptId) || ""),
            exceptionOnly: "true"
          }
        })
      })
    },
    openConfig() {
      this.configOpen = true
      this.configLoading = true
      this.salaryConfig = this.defaultSalaryConfig()
      getSalaryConfig({}).then(res => {
        this.salaryConfig = Object.assign(this.defaultSalaryConfig(), res.data || {})
      }).finally(() => {
        this.configLoading = false
        this.$nextTick(() => {
          if (this.$refs.configForm) {
            this.$refs.configForm.clearValidate()
          }
        })
      })
    },
    saveConfig() {
      this.$refs.configForm.validate(valid => {
        if (!valid) {
          return
        }
        this.configSaving = true
        saveSalaryConfig(this.salaryConfig).then(() => {
          this.$modal.msgSuccess("工资参数已保存")
          this.configOpen = false
        }).finally(() => {
          this.configSaving = false
        })
      })
    },
    handleExport() {
      const query = this.salaryQuerySnapshot(), scope = this.salaryScope(), token = scope.begin("export", query)
      const params = { ...query.params }
      delete params.pageNum
      delete params.pageSize
      const label = query.scope === "my" ? "我的工资" : "组织工资"
      return this.$modal.confirm(`确认导出${label}（${params.salaryMonth || "全部月份"}）？`, "导出提示").then(() => {
        if (!scope.isCurrent(token, this.salaryQuerySnapshot())) { this.$modal.msgWarning("导出条件或访问范围已变化，请重新发起导出"); return }
        return this.download(query.scope === "my" ? "oa/salary/export/my" : "oa/salary/export", params, this.exportFileName(label + (params.salaryMonth ? "_" + params.salaryMonth : "")))
      }).catch(error => { if (error !== "cancel" && error !== "close" && scope.isCurrent(token) && !(error && error.notified)) this.$modal.msgError(error && error.message || "下载失败，请重试") })
    }
  }
}
</script>

<style scoped>
.salary-filter-card {
  margin-bottom: 16px;
}
.salary-config-alert {
  margin-bottom: 16px;
}
</style>
