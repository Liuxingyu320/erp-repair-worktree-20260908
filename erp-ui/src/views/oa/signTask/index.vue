<template>
  <div class="app-container oa-workspace-page sign-task-center-page">
    <section class="task-hero oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 签署任务</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-s-claim" /></span>
          <div>
            <h1>合同签约中心</h1>
            <p>一名经办人完成补资料、发送、选公司盖章和异常处理，全流程无需审核。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__actions">
        <sign-scope-selector @ready="handleSignScopeReady" @change="handleSignScopeChange" />
        <el-button icon="el-icon-refresh" :loading="loading || metricLoading" @click="refreshAll">刷新数据</el-button>
      </div>
    </section>

    <el-alert
      v-if="signScopeResolved && !hasValidSignScope"
      title="请先选择签约组织"
      description="选择有效的签约组织后，才能加载任务、指标和通知。"
      type="warning"
      show-icon
      :closable="false"
      class="sign-scope-alert"
    />

    <section class="metric-grid" aria-label="签约任务指标">
      <button
        v-for="metric in metrics"
        :key="metric.key"
        type="button"
        class="metric-card"
        :class="[`tone-${metric.tone}`, { 'is-active': activeMetricKey === metric.key }]"
        :aria-pressed="activeMetricKey === metric.key"
        @click="applyMetric(metric)"
      >
        <span class="metric-icon"><i :class="metric.icon" /></span>
        <span class="metric-copy"><small>{{ metric.label }}</small><strong>{{ metric.count }}</strong></span>
      </button>
    </section>

    <el-alert
      v-if="notificationFailures.length"
      title="合同通知发送异常"
      type="warning"
      show-icon
      :closable="false"
      class="notification-failure-alert"
    >
      <div class="notification-failure-list">
        <span>有 {{ notificationFailures.length }} 条通知多次投递失败：</span>
        <el-button
          v-for="item in notificationFailures"
          :key="`${item.taskId}:${item.notificationBusinessKey}`"
          type="text"
          size="mini"
          @click="openTask(item.taskId, item.notificationBusinessKey)"
        >{{ taskNumberLabel(item) }} · 处理通知</el-button>
      </div>
    </el-alert>

    <sign-onboard-company-work
      v-if="canShowCompanyWork"
      :key="signScopeComponentKey"
      ref="onboardCompanyWork"
      :focus-package-id="$route.query.companyWorkPackageId || ''"
      @completed="handleOnboardCompanyWorkCompleted"
      @open-task="openTask"
    />

    <el-card shadow="never" class="task-list-card table-card oa-table-card">
      <div slot="header" class="list-header">
        <div>
          <h2>我的签约任务</h2>
          <p>列表只显示分配给当前经办人、且在当前组织权限内的任务；Excel 签名优先任务请使用上方“待选公司与印章”专区。</p>
        </div>
        <div class="list-header__actions">
          <el-button
            type="success"
            plain
            size="small"
            icon="el-icon-download"
            :loading="exportLoading"
            :disabled="!hasValidSignScope || total === 0"
            @click="handleExport"
          >导出签约数据</el-button>
          <span v-if="total > MAX_SIGN_TASK_EXPORT_ROWS" class="export-limit-warning">
            当前 {{ total }} 条，超过单次 {{ MAX_SIGN_TASK_EXPORT_ROWS }} 条上限，请先缩小筛选条件
          </span>
          <el-button
            v-if="canBatchFinalize"
            type="primary"
            plain
            size="small"
            icon="el-icon-s-claim"
            :disabled="batchFinalizeSelectedIds.length === 0"
            @click="openBatchFinalizeDialog"
          >批量选择公司并盖章（{{ batchFinalizeSelectedIds.length }}）</el-button>
          <el-button
            v-if="isAdmin"
            type="danger"
            plain
            size="small"
            icon="el-icon-delete"
            :disabled="selectedTasks.length === 0"
            @click="openDeleteDialog"
          >批量删除已选（{{ selectedTasks.length }}）</el-button>
          <span class="list-total">共 {{ total }} 条</span>
        </div>
      </div>

      <el-form :model="queryParams" inline size="small" class="task-filter" @submit.native.prevent>
        <el-form-item label="任务编号">
          <el-input v-model.trim="queryParams.taskNo" clearable placeholder="精确任务编号" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="员工">
          <el-input v-model.trim="employeeKeyword" clearable placeholder="姓名或数字账号" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="queryParams.scenario" clearable placeholder="全部场景">
            <el-option v-for="item in scenarioOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable filterable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="taskTable"
        v-accessible-table="'签约任务列表'"
        v-loading="loading"
        :data="taskList"
        :aria-busy="loading ? 'true' : 'false'"
        element-loading-text="正在加载签约任务"
        row-key="taskId"
        border
        size="small"
        @selection-change="handleSelectionChange"
      >
        <template slot="empty">
          <data-state
            :type="listError ? 'error' : 'empty'"
            :title="listError ? '签约任务加载失败' : '当前没有签约任务'"
            :description="listError || '当前组织与筛选条件下没有待处理任务，可重置筛选或刷新数据。'"
          >
            <el-button v-if="listError" type="primary" size="small" icon="el-icon-refresh" @click="getList">重试加载</el-button>
            <template v-else>
              <el-button size="small" @click="resetQuery">重置筛选</el-button>
              <el-button type="primary" size="small" icon="el-icon-refresh" @click="getList">刷新数据</el-button>
            </template>
          </data-state>
        </template>
        <el-table-column
          v-if="canBatchFinalize"
          width="52"
          align="center"
          fixed="left"
        >
          <template slot="header">
            <el-tooltip content="选择当前页可批量选公司盖章的任务" placement="top">
              <span>
                <el-checkbox
                  :value="batchFinalizeAllSelected"
                  :indeterminate="batchFinalizeSelectionIndeterminate"
                  :disabled="batchFinalizeEligibleRows.length === 0"
                  aria-label="选择当前页可批量选公司盖章的任务"
                  @change="toggleAllBatchFinalizeRows"
                />
              </span>
            </el-tooltip>
          </template>
          <template slot-scope="scope">
            <el-tooltip :content="batchFinalizeDisabledReason(scope.row)" placement="top" :disabled="isTaskBatchFinalizable(scope.row)">
              <span class="batch-finalize-checkbox-wrap">
                <el-checkbox
                  :value="isBatchFinalizeSelected(scope.row)"
                  :disabled="!isTaskBatchFinalizable(scope.row)"
                  :aria-label="`选择${scope.row.employeeName || '该员工'}进行批量选公司盖章`"
                  @change="toggleBatchFinalizeRow(scope.row, $event)"
                />
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column
          v-if="isAdmin"
          type="selection"
          width="46"
          align="center"
          :selectable="isTaskDeletable"
        />
        <el-table-column label="任务编号" min-width="135" show-overflow-tooltip>
          <template slot-scope="scope">{{ taskNumberLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="员工" width="120">
          <template slot-scope="scope">
            <div class="employee-cell">
              <strong>{{ scope.row.employeeName || `员工 #${scope.row.employeeId}` }}</strong>
              <small v-if="scope.row.employeeName">账号 {{ scope.row.employeeId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场景" width="90">
          <template slot-scope="scope">{{ scenarioLabel(scope.row.scenario) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="122">
          <template slot-scope="scope">
            <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="riskTagType(scope.row.riskLevel)">{{ riskLabel(scope.row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签署截止" prop="signDeadline" width="165" />
        <el-table-column label="创建时间" prop="createdTime" width="165" />
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ businessText(scope.row.failureDetail, '-') }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="92" align="center">
          <template slot-scope="scope">
            <el-button v-hasPermi="['oa:signTask:query', 'oa:signTask:technicalEvidence']" type="text" size="mini" @click="openTask(scope.row.taskId)">
              {{ actionableStatuses.includes(scope.row.status) ? '处理' : '查看' }}
            </el-button>
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

    <sign-task-detail-drawer
      :visible.sync="detailVisible"
      :task-id="selectedTaskId"
      :notification-business-key="notificationBusinessKey"
      :excel-import-enabled="excelImportAvailable"
      @updated="handleTaskUpdated"
      @notification-retried="handleNotificationRetried"
    />

    <sign-task-batch-finalize-dialog
      :visible.sync="batchFinalizeDialogVisible"
      :task-ids="batchFinalizeSelectedIds"
      @completed="handleBatchFinalizeCompleted"
    />

    <el-dialog
      title="确认批量删除签约任务"
      :visible.sync="deleteDialogVisible"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!deleteLoading"
      :show-close="!deleteLoading"
      @closed="resetDeleteDialog"
    >
      <el-alert
        title="此操作会永久删除任务、签约包、签名阅读证据和受管文件，不会制作备份。员工档案不受影响。"
        type="error"
        show-icon
        :closable="false"
      />
      <p class="delete-summary">即将删除 {{ selectedTasks.length }} 个未完成签约任务：</p>
      <ul class="delete-task-list">
        <li v-for="task in selectedTasks" :key="task.taskId">
          <strong>{{ taskNumberLabel(task) }}</strong>
          <span>{{ task.employeeName || `员工 #${task.employeeId}` }}</span>
        </li>
      </ul>
      <el-checkbox v-model="deleteConfirmed" class="delete-confirmation">
        我已核对上述任务，确认删除后不可恢复
      </el-checkbox>
      <span slot="footer" class="dialog-footer">
        <el-button :disabled="deleteLoading" @click="deleteDialogVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="deleteLoading"
          :disabled="!deleteConfirmed"
          @click="confirmBatchDelete"
        >永久删除</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { deleteSignTasksBatch, getSignTaskCapabilities, getSignTaskMetrics, listSignTaskNotificationFailures, listSignTasks } from '@/api/oa/signTask'
import { SIGN_TASK_STATUS_LABELS, signTaskStatusLabel } from '@/utils/signDictionary'
import { confirmExportAction } from '@/utils/exportConfirm'
import SignScopeSelector from '@/components/SignScopeSelector'
import SignOnboardCompanyWork from './SignOnboardCompanyWork'
import SignTaskBatchFinalizeDialog from './SignTaskBatchFinalizeDialog'
import SignTaskDetailDrawer from './SignTaskDetailDrawer'
const { SIGN_TASK_SCENARIO_OPTIONS, signScenarioLabel } = require('@/utils/signScenario')
const { signBusinessText, signTaskNumberLabel } = require('@/utils/signDisplayText')
const { MAX_SIGN_TASK_EXPORT_ROWS, signTaskExportBlockReason } = require('./signTaskExportPolicy')

export default {
  name: 'OaSignTask',
  components: { SignScopeSelector, SignOnboardCompanyWork, SignTaskBatchFinalizeDialog, SignTaskDetailDrawer },
  data() {
    return {
      loading: false,
      listError: '',
      metricLoading: false,
      signScopeResolved: false,
      selectedSignScope: null,
      signCapabilities: null,
      signCapabilitiesResolved: false,
      signCapabilityLoading: false,
      signScopeEpoch: 0,
      listRequestSequence: 0,
      metricRequestSequence: 0,
      notificationRequestSequence: 0,
      taskList: [],
      total: 0,
      employeeKeyword: '',
      activeMetricKey: '',
      detailVisible: false,
      selectedTaskId: null,
      notificationBusinessKey: '',
      notificationFailures: [],
      selectedTasks: [],
      batchFinalizeSelectedIds: [],
      batchFinalizeDialogVisible: false,
      deleteDialogVisible: false,
      deleteConfirmed: false,
      deleteLoading: false,
      deleteRequestId: '',
      exportLoading: false,
      MAX_SIGN_TASK_EXPORT_ROWS,
      queryParams: {
        pageNum: 1, pageSize: 10, taskNo: '', employeeId: null, employeeName: '',
        scenario: '', status: '', dueSoon: null, completedMonth: null
      },
      metricCounts: { needsData: 0, confirm: 0, failed: 0, viewed: 0, dueSoon: 0, refused: 0, completedMonth: 0 },
      actionableStatuses: ['NEW', 'VALIDATING', 'NEEDS_DATA', 'DRAFT_CREATED', 'WAITING_HR_CONFIRM', 'READY_TO_SEND', 'FAILED', 'PENDING_COMPANY'],
      scenarioOptions: SIGN_TASK_SCENARIO_OPTIONS,
      statusOptions: Object.entries(SIGN_TASK_STATUS_LABELS)
        .map(([value, label]) => ({ value, label }))
    }
  },
  computed: {
    isAdmin() {
      const roles = this.$store.getters && this.$store.getters.roles
      return Array.isArray(roles) && roles.includes('admin')
    },
    canBatchFinalize() {
      if (this.$auth && typeof this.$auth.hasPermi === 'function') {
        return this.$auth.hasPermi('oa:signTask:batchFinalize')
      }
      const permissions = this.$store.getters && this.$store.getters.permissions
      return Array.isArray(permissions) &&
        (permissions.includes('*:*:*') || permissions.includes('oa:signTask:batchFinalize'))
    },
    hasValidSignScope() {
      return !!(this.selectedSignScope && this.selectedSignScope.deptId)
    },
    excelImportAvailable() {
      return String(process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED || 'true').trim().toLowerCase() === 'true' &&
        !!(this.signCapabilities && this.signCapabilities.excelImportEnabled === true)
    },
    canShowCompanyWork() {
      return this.canBatchFinalize && this.hasValidSignScope && this.excelImportAvailable &&
        !!this.signScopeComponentKey
    },
    signScopeComponentKey() {
      const scope = this.selectedSignScope || {}
      const deptId = String(scope.deptId || '').trim()
      const deptType = String(scope.deptType || 'SIGN').trim().toUpperCase()
      if (!/^[1-9]\d{0,18}$/.test(deptId) || !/^[A-Z0-9_-]{1,40}$/.test(deptType)) return ''
      return `${deptId}|${deptType}|${this.signScopeEpoch}`
    },
    batchFinalizeEligibleRows() {
      return this.taskList.filter(task => this.isTaskBatchFinalizable(task))
    },
    batchFinalizeAllSelected() {
      return this.batchFinalizeEligibleRows.length > 0 && this.batchFinalizeEligibleRows.every(task =>
        this.batchFinalizeSelectedIds.includes(String(task.taskId)))
    },
    batchFinalizeSelectionIndeterminate() {
      const selectedCount = this.batchFinalizeEligibleRows.filter(task =>
        this.batchFinalizeSelectedIds.includes(String(task.taskId))).length
      return selectedCount > 0 && selectedCount < this.batchFinalizeEligibleRows.length
    },
    metrics() {
      return [
        { key: 'needsData', label: '待补资料', count: this.metricCounts.needsData, status: 'NEEDS_DATA', icon: 'el-icon-user', tone: 'blue' },
        { key: 'confirm', label: '待选公司盖章', count: this.metricCounts.confirm, status: 'PENDING_COMPANY', icon: 'el-icon-s-claim', tone: 'amber' },
        { key: 'failed', label: '处理失败', count: this.metricCounts.failed, status: 'FAILED', icon: 'el-icon-warning', tone: 'red' },
        { key: 'viewed', label: '已查看未签', count: this.metricCounts.viewed, status: 'VIEWED', icon: 'el-icon-view', tone: 'violet' },
        { key: 'dueSoon', label: '即将逾期', count: this.metricCounts.dueSoon, dueSoon: true, icon: 'el-icon-time', tone: 'orange' },
        { key: 'refused', label: '员工拒签', count: this.metricCounts.refused, status: 'REFUSED', icon: 'el-icon-circle-close', tone: 'red' },
        { key: 'completedMonth', label: '本月已完成', count: this.metricCounts.completedMonth, completedMonth: true, icon: 'el-icon-finished', tone: 'green' }
      ]
    }
  },
  created() {
    this.loadSignCapabilities()
  },
  watch: {
    '$route.query.taskId'() {
      this.openTargetFromRoute()
    }
  },
  methods: {
    invalidateSignScopeRequests() {
      this.signScopeEpoch += 1
      this.listRequestSequence += 1
      this.metricRequestSequence += 1
      this.notificationRequestSequence += 1
      this.loading = false
      this.metricLoading = false
      this.exportLoading = false
    },
    isCurrentSignScopeRequest(scopeEpoch, requestSequence, currentSequence) {
      return scopeEpoch === this.signScopeEpoch &&
        requestSequence === currentSequence &&
        this.signScopeResolved &&
        this.hasValidSignScope
    },
    loadSignCapabilities() {
      this.signCapabilityLoading = true
      return getSignTaskCapabilities().then(response => {
        const data = response && response.data
        this.signCapabilities = data && typeof data === 'object' ? data : null
      }).catch(() => {
        // Excel 扩展能力失败时保持关闭，核心签约仍可使用。
        this.signCapabilities = null
      }).finally(() => {
        this.signCapabilityLoading = false
        this.signCapabilitiesResolved = true
        this.maybeRefreshForSignScope()
      })
    },
    maybeRefreshForSignScope() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved) return
      if (!this.hasValidSignScope) {
        this.clearSignWorkspace(false)
        return
      }
      this.refreshAll()
      this.openTargetFromRoute()
    },
    clearSignWorkspace(invalidate = true) {
      if (invalidate) this.invalidateSignScopeRequests()
      this.taskList = []
      this.total = 0
      this.notificationFailures = []
      this.metricCounts = { needsData: 0, confirm: 0, failed: 0, viewed: 0, dueSoon: 0, refused: 0, completedMonth: 0 }
      this.selectedTasks = []
      this.batchFinalizeSelectedIds = []
      this.detailVisible = false
      this.selectedTaskId = null
      this.notificationBusinessKey = ''
      this.listError = ''
    },
    handleSignScopeReady(option) {
      this.invalidateSignScopeRequests()
      this.signScopeResolved = true
      this.selectedSignScope = option && option.deptId ? option : null
      this.clearSignWorkspace(false)
      this.maybeRefreshForSignScope()
    },
    handleSignScopeChange(option) {
      this.invalidateSignScopeRequests()
      this.signScopeResolved = true
      this.selectedSignScope = option && option.deptId ? option : null
      this.clearSignWorkspace(false)
      this.batchFinalizeDialogVisible = false
      this.deleteDialogVisible = false
      this.deleteRequestId = ''
      this.deleteConfirmed = false
      this.queryParams.pageNum = 1
      this.maybeRefreshForSignScope()
    },
    refreshAll() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved || !this.hasValidSignScope) {
        if (this.signScopeResolved && !this.hasValidSignScope) this.clearSignWorkspace()
        return Promise.resolve(false)
      }
      const scopeEpoch = this.signScopeEpoch
      const requests = [this.getList(), this.loadMetrics(), this.loadNotificationFailures()]
      this.$store.dispatch('todo/refreshSummaries').catch(() => {})
      this.$nextTick(() => {
        if (scopeEpoch !== this.signScopeEpoch || !this.canShowCompanyWork) return
        if (this.$refs.onboardCompanyWork) this.$refs.onboardCompanyWork.refresh()
      })
      return Promise.all(requests)
    },
    getList() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved || !this.hasValidSignScope) {
        return Promise.resolve(false)
      }
      const scopeEpoch = this.signScopeEpoch
      const requestSequence = ++this.listRequestSequence
      this.loading = true
      this.listError = ''
      return listSignTasks(this.queryParams).then(response => {
        if (!this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.listRequestSequence)) return false
        this.taskList = response.rows || []
        this.total = Number(response.total) || 0
        this.selectedTasks = []
        if (!this.batchFinalizeDialogVisible) this.batchFinalizeSelectedIds = []
        return true
      }).catch(() => {
        if (!this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.listRequestSequence)) return false
        this.taskList = []
        this.total = 0
        this.listError = '暂时无法加载签约任务。请检查网络或稍后重试；其他页面不受影响。'
        return false
      }).finally(() => {
        if (this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.listRequestSequence)) {
          this.loading = false
        }
      })
    },
    loadMetrics() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved || !this.hasValidSignScope) {
        return Promise.resolve(false)
      }
      const scopeEpoch = this.signScopeEpoch
      const requestSequence = ++this.metricRequestSequence
      this.metricLoading = true
      return getSignTaskMetrics().then(response => {
        if (!this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.metricRequestSequence)) return false
        this.metricCounts = response.data || {
          needsData: 0, confirm: 0, failed: 0, viewed: 0, dueSoon: 0, refused: 0, completedMonth: 0
        }
        return true
      }).catch(() => {
        // 保留上一次指标，列表仍可继续使用。
        return false
      }).finally(() => {
        if (this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.metricRequestSequence)) {
          this.metricLoading = false
        }
      })
    },
    loadNotificationFailures() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved || !this.hasValidSignScope) {
        return Promise.resolve(false)
      }
      const scopeEpoch = this.signScopeEpoch
      const requestSequence = ++this.notificationRequestSequence
      return listSignTaskNotificationFailures().then(response => {
        if (!this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.notificationRequestSequence)) return false
        const items = Array.isArray(response.data) ? response.data : []
        this.notificationFailures = items.filter(item => item &&
          this.normalizeBusinessKey(item.notificationBusinessKey))
        return true
      }).catch(() => {
        if (this.isCurrentSignScopeRequest(scopeEpoch, requestSequence, this.notificationRequestSequence)) {
          this.notificationFailures = []
        }
        return false
      })
    },
    handleQuery() {
      const keyword = this.employeeKeyword.trim()
      if (keyword.length > 50) {
        this.$modal.msgWarning('员工姓名不能超过50个字符')
        return
      }
      this.queryParams.employeeId = /^[1-9]\d{0,18}$/.test(keyword) ? keyword : null
      this.queryParams.employeeName = keyword && !this.queryParams.employeeId ? keyword : ''
      this.queryParams.pageNum = 1
      this.getList()
    },
    handleExport() {
      if (!this.hasValidSignScope) {
        this.$modal.msgWarning('请先选择签约组织')
        return Promise.resolve(false)
      }
      const blockReason = signTaskExportBlockReason(this.total)
      if (blockReason) {
        this.$modal.msgWarning(blockReason)
        return Promise.resolve(false)
      }
      return confirmExportAction(this, {
        moduleName: '签约数据',
        rangeLabel: `当前签约组织和筛选条件下的 ${this.total} 条任务（最多${MAX_SIGN_TASK_EXPORT_ROWS}条）`,
        filterLabel: this.exportFilterLabel(),
        sensitiveFields: ['身份证号', '家庭住址', '联系电话', '薪资']
      }).then(() => {
        this.exportLoading = true
        return this.download(
          '/oa/signTask/export',
          { ...this.queryParams },
          this.exportFileName('签约数据'),
          { timeout: 60000 }
        ).finally(() => {
          this.exportLoading = false
        })
      })
    },
    exportFilterLabel() {
      const filters = []
      if (this.queryParams.taskNo) filters.push(`任务编号：${this.queryParams.taskNo}`)
      if (this.queryParams.employeeId) filters.push(`员工账号：${this.queryParams.employeeId}`)
      else if (this.queryParams.employeeName) filters.push(`员工姓名：${this.queryParams.employeeName}`)
      if (this.queryParams.scenario) filters.push(`场景：${this.scenarioLabel(this.queryParams.scenario)}`)
      if (this.queryParams.status) filters.push(`状态：${this.statusLabel(this.queryParams.status)}`)
      if (this.queryParams.dueSoon) filters.push('即将逾期')
      if (this.queryParams.completedMonth) filters.push('本月已完成')
      return filters.length ? filters.join('；') : '全部任务'
    },
    handleSelectionChange(rows) {
      this.selectedTasks = Array.isArray(rows) ? rows.slice() : []
    },
    isTaskBatchFinalizable(task) {
      return !!task && String(task.status || '').toUpperCase() === 'PENDING_COMPANY' &&
        !this.isExcelStagedPackageTask(task) &&
        /^[1-9]\d{0,18}$/.test(String(task.packageId || ''))
    },
    isExcelStagedPackageTask(task) {
      return String(task && (task.sourceType || task.taskSourceType) || '').trim().toUpperCase() ===
        'MANUAL_SIGN_EXCEL_IMPORT'
    },
    batchFinalizeDisabledReason(task) {
      if (!task || String(task.status || '').toUpperCase() !== 'PENDING_COMPANY') return '仅“待选公司盖章”任务可批量处理'
      if (this.isExcelStagedPackageTask(task)) return '请在上方“Excel 签名优先 · 待选公司与印章”专区处理'
      if (!/^[1-9]\d{0,18}$/.test(String(task.packageId || ''))) return '任务尚未生成可处理的签约包'
      return ''
    },
    isBatchFinalizeSelected(task) {
      return !!task && this.batchFinalizeSelectedIds.includes(String(task.taskId))
    },
    toggleBatchFinalizeRow(task, checked) {
      if (!this.isTaskBatchFinalizable(task)) return
      const taskId = String(task.taskId)
      if (!checked) {
        this.batchFinalizeSelectedIds = this.batchFinalizeSelectedIds.filter(value => value !== taskId)
        return
      }
      if (this.batchFinalizeSelectedIds.includes(taskId)) return
      if (this.batchFinalizeSelectedIds.length >= 20) {
        this.$modal.msgWarning('单次最多处理20个待选公司盖章任务')
        return
      }
      this.batchFinalizeSelectedIds = this.batchFinalizeSelectedIds.concat(taskId)
    },
    toggleAllBatchFinalizeRows(checked) {
      if (!checked) {
        const currentPageIds = new Set(this.batchFinalizeEligibleRows.map(task => String(task.taskId)))
        this.batchFinalizeSelectedIds = this.batchFinalizeSelectedIds.filter(taskId => !currentPageIds.has(taskId))
        return
      }
      const merged = this.batchFinalizeSelectedIds.slice()
      this.batchFinalizeEligibleRows.forEach(task => {
        const taskId = String(task.taskId)
        if (merged.length < 20 && !merged.includes(taskId)) merged.push(taskId)
      })
      this.batchFinalizeSelectedIds = merged
      if (this.batchFinalizeEligibleRows.length > 20) this.$modal.msgWarning('单次最多处理20个任务，已选择当前页前20项')
    },
    openBatchFinalizeDialog() {
      if (!this.canBatchFinalize) {
        this.$modal.msgError('当前账号没有批量选择公司并盖章权限')
        return
      }
      const selectedRows = this.taskList.filter(task => this.batchFinalizeSelectedIds.includes(String(task.taskId)))
      if (!selectedRows.length) {
        this.$modal.msgWarning('请先选择待选公司盖章任务')
        return
      }
      if (selectedRows.length > 20) {
        this.$modal.msgWarning('单次最多处理20个任务')
        return
      }
      if (selectedRows.some(task => !this.isTaskBatchFinalizable(task))) {
        this.$modal.msgError('所选任务状态已变化，请刷新列表后重新选择')
        return
      }
      this.batchFinalizeDialogVisible = true
    },
    handleBatchFinalizeCompleted() {
      this.batchFinalizeSelectedIds = []
      this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
      this.getList()
      this.loadMetrics()
      this.loadNotificationFailures()
    },
    handleOnboardCompanyWorkCompleted() {
      this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
      this.getList()
      this.loadMetrics()
      this.loadNotificationFailures()
      if (this.$refs.onboardCompanyWork) this.$refs.onboardCompanyWork.refresh()
    },
    isTaskDeletable(task) {
      return !!task && !['SIGNED', 'NO_ACTION'].includes(String(task.status || '').toUpperCase()) &&
        Number.isSafeInteger(Number(task.version)) && Number(task.version) >= 0
    },
    openDeleteDialog() {
      if (!this.isAdmin) {
        this.$modal.msgError('仅系统管理员可删除签约任务')
        return
      }
      if (!this.selectedTasks.length) {
        this.$modal.msgWarning('请先选择要删除的签约任务')
        return
      }
      if (this.selectedTasks.length > 20) {
        this.$modal.msgWarning('单次最多删除20个签约任务')
        return
      }
      const protectedTasks = this.selectedTasks.filter(task => !this.isTaskDeletable(task))
      if (protectedTasks.length) {
        const completed = protectedTasks.some(task =>
          ['SIGNED', 'NO_ACTION'].includes(String(task.status || '').toUpperCase()))
        this.$modal.msgError(completed
          ? '已完成的签约任务不能硬删除，请取消选择后重试'
          : '所选任务缺少有效版本，请刷新列表后重新选择')
        return
      }
      this.deleteConfirmed = false
      this.deleteRequestId = this.createRequestId('hard-delete')
      this.deleteDialogVisible = true
    },
    confirmBatchDelete() {
      if (!this.deleteConfirmed || this.deleteLoading) return
      const items = this.selectedTasks.map(task => ({
        taskId: String(task.taskId),
        expectedVersion: Number(task.version)
      }))
      if (!items.length || items.some(item => !/^[1-9]\d{0,18}$/.test(item.taskId) ||
        !Number.isSafeInteger(item.expectedVersion) || item.expectedVersion < 0)) {
        this.$modal.msgError('所选任务已变化，请刷新列表后重新选择')
        return
      }
      if (!this.deleteRequestId) this.deleteRequestId = this.createRequestId('hard-delete')
      this.deleteLoading = true
      return deleteSignTasksBatch({
        requestId: this.deleteRequestId,
        items,
        irreversibleConfirmed: true
      }).then(response => {
        const result = response.data || {}
        const resultItems = Array.isArray(result.items) ? result.items : []
        const failures = resultItems.filter(item => item && item.result !== 'DELETED')
        this.deleteDialogVisible = false
        if (failures.length) {
          const details = failures.map(item => `${item.taskId || '-'}：${item.message || '删除失败'}`).join('\n')
          this.$alert(details, `删除完成：成功 ${Number(result.deletedCount) || 0}，未成功 ${failures.length}`, {
            type: 'warning',
            confirmButtonText: '知道了'
          })
        } else {
          this.$modal.msgSuccess(`已删除 ${Number(result.deletedCount) || items.length} 个签约任务`)
        }
        this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
        return this.refreshAll()
      }).catch(error => {
        this.$modal.msgError(this.businessText(error && error.message,
          '删除请求失败，请刷新后重试'))
      }).finally(() => {
        this.deleteLoading = false
      })
    },
    resetDeleteDialog() {
      if (!this.deleteLoading) {
        this.deleteConfirmed = false
        this.deleteRequestId = ''
      }
    },
    createRequestId(prefix) {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `sign-${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    resetQuery() {
      this.employeeKeyword = ''
      this.activeMetricKey = ''
      this.queryParams = {
        pageNum: 1, pageSize: 10, taskNo: '', employeeId: null, employeeName: '',
        scenario: '', status: '', dueSoon: null, completedMonth: null
      }
      this.getList()
    },
    applyMetric(metric) {
      const shouldClear = this.activeMetricKey === metric.key
      this.queryParams.status = ''
      this.queryParams.dueSoon = null
      this.queryParams.completedMonth = null
      this.activeMetricKey = shouldClear ? '' : metric.key
      if (!shouldClear) {
        if (metric.status) this.queryParams.status = metric.status
        if (metric.dueSoon) this.queryParams.dueSoon = true
        if (metric.completedMonth) this.queryParams.completedMonth = true
      }
      this.queryParams.pageNum = 1
      this.getList()
    },
    openTask(taskId, notificationBusinessKey = '') {
      const normalized = String(taskId || '')
      if (!/^[1-9]\d{0,18}$/.test(normalized)) return
      this.selectedTaskId = normalized
      this.notificationBusinessKey = this.normalizeBusinessKey(notificationBusinessKey)
      this.detailVisible = true
    },
    openTargetFromRoute() {
      if (!this.signScopeResolved || !this.signCapabilitiesResolved || !this.hasValidSignScope) return
      const taskId = this.$route.query.taskId
      if (!taskId) return
      const scopeEpoch = this.signScopeEpoch
      this.$nextTick(() => {
        if (scopeEpoch !== this.signScopeEpoch || !this.hasValidSignScope) return
        this.openTask(taskId, this.$route.query.notificationBusinessKey || '')
      })
    },
    normalizeBusinessKey(value) {
      const normalized = typeof value === 'string' ? value.trim() : ''
      return /^[A-Za-z0-9:._+-]{1,180}$/.test(normalized) ? normalized : ''
    },
    handleTaskUpdated() {
      this.getList()
      this.loadMetrics()
    },
    handleNotificationRetried() {
      this.notificationBusinessKey = ''
      this.loadNotificationFailures()
      this.loadMetrics()
    },
    scenarioLabel(value) {
      return signScenarioLabel(value)
    },
    taskNumberLabel(value) {
      return signTaskNumberLabel(value)
    },
    businessText(value, fallback) {
      return signBusinessText(value, fallback)
    },
    statusLabel(value) {
      return signTaskStatusLabel(value)
    },
    statusTagType(status) {
      if (['SIGNED', 'NO_ACTION'].includes(status)) return 'success'
      if (['FAILED', 'REFUSED', 'EXPIRED'].includes(status)) return 'danger'
      if (['WAITING_HR_CONFIRM', 'READY_TO_SEND', 'PENDING_SIGN', 'VIEWED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM'].includes(status)) return 'warning'
      if (['CANCELLED'].includes(status)) return 'info'
      return ''
    },
    riskLabel(value) {
      return { NORMAL: '常规', LOW: '低风险', MEDIUM: '关注', HIGH: '高风险' }[value] || '常规'
    },
    riskTagType(value) {
      return value === 'HIGH' ? 'danger' : value === 'MEDIUM' ? 'warning' : 'success'
    }
  }
}
</script>

<style lang="scss" scoped>
.sign-task-center-page { background: transparent; }
.list-header__actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.export-limit-warning { color: #b45309; font-size: 12px; font-weight: 600; }
.batch-finalize-checkbox-wrap { display: inline-flex; align-items: center; justify-content: center; min-width: 24px; min-height: 24px; }
.delete-summary { margin: 18px 0 10px; color: #303133; }
.delete-task-list { max-height: 220px; overflow: auto; margin: 0; padding: 0; list-style: none; border: 1px solid #ebeef5; border-radius: 6px; }
.delete-task-list li { display: flex; justify-content: space-between; gap: 16px; padding: 9px 12px; border-bottom: 1px solid #ebeef5; }
.delete-task-list li:last-child { border-bottom: 0; }
.delete-task-list span { color: #606266; }
.delete-confirmation { margin-top: 18px; white-space: normal; }
.task-hero {
  --oa-hero-end: #075441;
}
.metric-grid { display: grid; grid-template-columns: repeat(7, minmax(116px, 1fr)); gap: 10px; margin: 14px 0; }
.notification-failure-alert { margin-bottom: 14px; }
.notification-failure-list { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 12px; }
.notification-failure-list .el-button + .el-button { margin-left: 0; }
.metric-card {
  min-width: 0;
  min-height: 88px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(15, 23, 42, .05);
  transition: border-color var(--motion-duration-fast) var(--motion-ease-standard), box-shadow var(--motion-duration-fast) var(--motion-ease-standard);
}
.metric-card:hover { border-color: #8fb3a4; box-shadow: 0 8px 20px rgba(23, 33, 29, .08); }
.metric-card.is-active { border-color: var(--erp-primary, #0b6b53); box-shadow: 0 0 0 2px rgba(11, 107, 83, .12); }
.metric-card:focus-visible { outline: 3px solid rgba(11, 107, 83, .22); outline-offset: 2px; }
.metric-icon { width: 34px; height: 34px; flex: none; display: inline-flex; align-items: center; justify-content: center; border-radius: 8px; background: var(--erp-primary-soft, #e7f2ed); color: var(--erp-primary, #0b6b53); font-size: 17px; }
.metric-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.metric-copy small { color: #64748b; font-size: 12px; white-space: nowrap; }
.metric-copy strong { color: #0f172a; font-size: 23px; line-height: 1; }
.tone-amber .metric-icon { color: #b45309; background: #fffbeb; }
.tone-red .metric-icon { color: #dc2626; background: #fef2f2; }
.tone-violet .metric-icon { color: #7c3aed; background: #f5f3ff; }
.tone-orange .metric-icon { color: #ea580c; background: #fff7ed; }
.tone-green .metric-icon { color: #15803d; background: #f0fdf4; }
.task-list-card { border: 1px solid #e2e8f0; }
.list-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.list-header h2 { margin: 0 0 5px; color: #1e293b; font-size: 17px; }
.list-header p { margin: 0; color: #64748b; font-size: 12px; }
.list-total { color: #475569; font-size: 13px; font-weight: 600; }
.task-filter { margin-bottom: 2px; padding: 14px 14px 0; background: #f8fafc; border: 1px solid #edf1f6; border-radius: 12px; }
.employee-cell { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.employee-cell strong { overflow: hidden; color: #1f2937; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.employee-cell small { color: #64748b; font-size: 11px; }
@media (max-width: 1280px) { .metric-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 760px) {
  .task-hero { align-items: flex-start; flex-direction: column; }
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .list-header { align-items: flex-start; flex-direction: column; }
}
</style>
