<template>
  <section class="approval-panel">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline size="small" @submit.native.prevent>
        <el-form-item label="业务类型">
          <el-select v-model="query.businessCode" clearable placeholder="全部业务">
            <el-option v-for="item in monitorTemplates" :key="item.businessCode" :label="item.templateName" :value="item.businessCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model.trim="query.businessId" clearable placeholder="业务编号或单号" @keyup.enter.native="search" />
        </el-form-item>
        <el-form-item label="实例状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option v-for="item in instanceStatuses" :key="item" :label="statusLabel(item)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="回调状态">
          <el-select v-model="query.callbackStatus" clearable placeholder="全部状态">
            <el-option label="无需回调" value="NONE" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="成功" value="SUCCEEDED" />
            <el-option label="失败待重试" value="FAILED" />
            <el-option label="死信" value="DEAD" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button icon="el-icon-refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="sourceErrorEntries.length" class="approval-source-errors" role="alert">
      <div class="approval-source-errors__heading">
        <i class="el-icon-warning-outline"></i>
        <div>
          <strong>部分运行监控来源不可用</strong>
          <span>其他来源仍可浏览；失败来源保持未知，不会按 0 条实例处理。</span>
        </div>
      </div>
      <div v-for="item in sourceErrorEntries" :key="item.source" class="approval-source-error">
        <el-tag size="mini" type="danger">{{ sourceLabel(item.source) }}</el-tag>
        <span>{{ item.error.message }}</span>
        <small v-if="item.error.diagnostic">{{ item.error.diagnostic }}</small>
        <el-button
          type="text"
          icon="el-icon-refresh"
          :loading="loadingSources[item.source]"
          @click="retrySource(item.source)"
        >重试此来源</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div slot="header" class="card-heading">
        <div>
          <strong>审批实例</strong>
          <small>统一引擎与兼容引擎实例在同一处监控，不允许删除运行和审计记录。</small>
        </div>
        <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column label="引擎" width="100" align="center">
          <template slot-scope="scope">
            <el-tag :type="isLegacy(scope.row) ? 'info' : 'success'" size="mini">
              {{ isLegacy(scope.row) ? '旧引擎' : '统一引擎' }}
            </el-tag>
            <el-tag v-if="scope.row.loadState === 'STALE'" type="warning" size="mini">最近成功数据</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务" min-width="150">
          <template slot-scope="scope">
            <strong>{{ scope.row.templateName || businessLabel(scope.row.businessCode) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="业务单号" min-width="150">
          <template slot-scope="scope">{{ scope.row.businessNo || scope.row.businessId || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请人" min-width="120">
          <template slot-scope="scope">{{ scope.row.applicantName || scope.row.applicantUserId || scope.row.applicantId || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前节点" prop="currentNodeName" min-width="140" />
        <el-table-column label="实例状态" width="125">
          <template slot-scope="scope"><el-tag :type="statusType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="回调" width="115">
          <template slot-scope="scope">
            <span v-if="isLegacy(scope.row)" class="muted">原业务处理</span>
            <el-tag v-else :type="statusType(scope.row.callbackStatus)" size="mini">{{ statusLabel(scope.row.callbackStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="165"><template slot-scope="scope">{{ scope.row.startedTime || scope.row.createTime || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['approval:instance:query']" type="text" size="mini" @click="openDetail(scope.row)">详情</el-button>
            <el-button
              v-if="canTerminate(scope.row)"
              v-hasPermi="['approval:instance:terminate']"
              type="text"
              size="mini"
              class="danger-text"
              @click="openAdminAction('terminate', scope.row)"
            >终止</el-button>
            <el-tooltip v-if="isLegacy(scope.row)" content="旧实例由原业务处理" placement="top">
              <span class="disabled-action"><el-button type="text" size="mini" disabled>终止</el-button></span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!instanceTotalKnown && rows.length" class="unknown-total-hint">
        部分审批来源不可用，当前仅展示已成功来源的数据，总数未知。
      </div>
      <pagination v-show="instanceTotalKnown && total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </el-card>

    <el-dialog title="审批实例详情" :visible.sync="detailVisible" width="1120px" append-to-body>
      <div v-loading="detailLoading">
        <div v-if="isLegacyDetail" class="legacy-readonly">
          <el-alert
            title="旧实例由原业务处理"
            description="统一审批中心仅提供旧引擎实例的查询与审计视图，不改变原业务的处理入口。"
            type="info"
            :closable="false"
            show-icon
          />
          <div class="legacy-actions">
            <el-tooltip v-for="item in legacyDisabledActions" :key="item" content="旧实例由原业务处理" placement="top">
              <span class="disabled-action"><el-button size="mini" disabled>{{ item }}</el-button></span>
            </el-tooltip>
          </div>
        </div>

        <el-descriptions v-if="detailInstance" :column="3" border size="small" class="detail-summary">
          <el-descriptions-item label="引擎"><el-tag :type="isLegacyDetail ? 'info' : 'success'" size="mini">{{ isLegacyDetail ? '旧引擎' : '统一引擎' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="业务">{{ detailInstance.templateName || businessLabel(detailInstance.businessCode) }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detailInstance.businessNo || detailInstance.businessId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批轮次">{{ detailInstance.businessRound || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detailInstance.applicantName || detailInstance.applicantUserId || detailInstance.applicantId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规则版本">{{ isLegacyDetail ? '原业务配置' : (detailInstance.versionName || detailInstance.ruleVersionNo || detailInstance.versionNo || '-') }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(detailInstance.status)" size="mini">{{ statusLabel(detailInstance.status) }}</el-tag></el-descriptions-item>
        </el-descriptions>

        <h4>节点任务与候选人</h4>
        <el-table :data="tasks" border size="small">
          <el-table-column label="顺序" prop="nodeOrder" width="70" align="center" />
          <el-table-column label="节点" prop="nodeName" min-width="140" />
          <el-table-column label="状态" width="105"><template slot-scope="scope">{{ statusLabel(scope.row.taskStatus || scope.row.status) }}</template></el-table-column>
          <el-table-column label="候选人" min-width="200"><template slot-scope="scope">{{ candidateNames(scope.row) }}</template></el-table-column>
          <el-table-column label="处理人" min-width="120"><template slot-scope="scope">{{ scope.row.operatorName || scope.row.assigneeName || '-' }}</template></el-table-column>
          <el-table-column label="完成时间" width="165"><template slot-scope="scope">{{ scope.row.completedTime || scope.row.completeTime || scope.row.actionTime || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="90">
            <template slot-scope="scope">
              <el-button
                v-if="!isLegacyDetail && String(scope.row.taskStatus || scope.row.status).toUpperCase() === 'PENDING'"
                v-hasPermi="['approval:task:reassign']"
                type="text"
                size="mini"
                @click="openAdminAction('reassign', scope.row)"
              >改派</el-button>
              <el-tooltip v-else-if="isLegacyDetail && String(scope.row.taskStatus || scope.row.status).toUpperCase() === 'PENDING'" content="旧实例由原业务处理" placement="top">
                <span class="disabled-action"><el-button type="text" size="mini" disabled>改派</el-button></span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <h4>业务回调</h4>
        <el-table v-if="!isLegacyDetail" :data="callbacks" border size="small">
          <el-table-column label="事件" min-width="130"><template slot-scope="scope">{{ statusLabel(scope.row.callbackAction || scope.row.eventType) }}</template></el-table-column>
          <el-table-column label="事件键" prop="eventKey" min-width="210" />
          <el-table-column label="状态" width="100"><template slot-scope="scope"><el-tag :type="statusType(scope.row.callbackStatus || scope.row.status)" size="mini">{{ statusLabel(scope.row.callbackStatus || scope.row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="重试次数" prop="retryCount" width="90" align="center" />
          <el-table-column label="最近错误" min-width="220" show-overflow-tooltip><template slot-scope="scope">{{ callbackErrorText(scope.row) }}</template></el-table-column>
          <el-table-column label="操作" width="100">
            <template slot-scope="scope">
              <el-button
                v-if="canReplay(scope.row)"
                v-hasPermi="['approval:callback:replay']"
                type="text"
                size="mini"
                @click="openAdminAction('replay', scope.row)"
              >重试</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="旧引擎无统一回调记录，由原业务处理" :image-size="64" />

        <h4>操作轨迹</h4>
        <el-table :data="actions" border size="small">
          <el-table-column label="时间" prop="createTime" width="165" />
          <el-table-column label="动作" width="120"><template slot-scope="scope">{{ statusLabel(scope.row.actionType || scope.row.action) }}</template></el-table-column>
          <el-table-column label="节点" prop="nodeName" min-width="130" />
          <el-table-column label="操作人" min-width="120"><template slot-scope="scope">{{ scope.row.operatorName || scope.row.operatorId || '系统' }}</template></el-table-column>
          <el-table-column label="原因/意见" min-width="260" show-overflow-tooltip><template slot-scope="scope">{{ scope.row.actionReason || scope.row.reason || '-' }}</template></el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <admin-action-dialog
      :visible.sync="adminActionVisible"
      :operation="adminOperation"
      :loading="adminActionLoading"
      :error="adminActionError"
      @confirm="submitAdminAction"
    />
  </section>
</template>

<script>
import {
  getApprovalInstance,
  getLegacyApprovalInstance,
  listApprovalInstances,
  listLegacyApprovalInstances,
  listLegacyApprovalTemplates,
  reassignApprovalTask,
  replayApprovalCallback,
  terminateApprovalInstance
} from '@/api/approval/monitor'
import AdminActionDialog from './AdminActionDialog'
import { businessCodeLabel, entityId, formatApprovalLoadError, statusLabel, statusType, toArray, unwrapData, unwrapRows } from './approvalUi'
import { getSelectedDeptId } from '@/utils/shopContext'

function decimalId(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return ''
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const id = String(value).trim().replace(/^0+/, '')
  return /^[1-9]\d*$/.test(id) && (id.length < 19 || (id.length === 19 && id <= '9223372036854775807')) ? id : ''
}

export default {
  name: 'ApprovalRuntimeMonitor',
  components: { AdminActionDialog },
  props: { templates: { type: Array, default: () => [] } },
  data() {
    return {
      rows: [],
      total: 0,
      nativeRows: [],
      nativeTotal: 0,
      legacyRows: [],
      legacyTotal: 0,
      sourceQueryKeys: { native: '', legacy: '' },
      sourceErrors: { native: null, legacy: null, legacyTemplates: null },
      loadingSources: { native: false, legacy: false, legacyTemplates: false },
      legacyTemplates: [],
      mergedPrefixLimit: 100,
      query: { pageNum: 1, pageSize: 10, businessCode: '', businessId: '', status: '', callbackStatus: '' },
      instanceStatuses: ['RUNNING', 'COMPLETING', 'RETURNING', 'REJECTING', 'WITHDRAWING', 'TERMINATING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN', 'TERMINATED', 'INVALIDATED'],
      detailVisible: false,
      detailLoading: false,
      detail: null,
      detailEngineMode: '',
      detailSourceRow: null,
      legacyDisabledActions: ['终止', '改派', '回调重试'],
      adminActionVisible: false,
      adminActionLoading: false,
      adminActionError: '', adminEpoch: 0, detailReadEpoch: 0, pageInactive: false,
      adminOperation: { type: '', target: null }
    }
  },
  computed: {
    loading() {
      return this.loadingSources.native || this.loadingSources.legacy
    },
    sourceErrorEntries() {
      return ['native', 'legacy', 'legacyTemplates']
        .filter(source => !!this.sourceErrors[source])
        .map(source => ({ source, error: this.sourceErrors[source] }))
    },
    instanceTotalKnown() {
      return !this.sourceErrors.native && !this.sourceErrors.legacy
    },
    monitorTemplates() {
      const byCode = new Map()
      const allTemplates = [...this.templates, ...this.legacyTemplates]
      allTemplates.forEach(item => {
        if (!item || !item.businessCode) return
        const current = byCode.get(item.businessCode)
        if (!current || String(item.engineMode || '').toUpperCase() !== 'LEGACY') byCode.set(item.businessCode, item)
      })
      return Array.from(byCode.values())
    },
    detailInstance() { return this.detail && (this.detail.instance || this.detail) },
    isLegacyDetail() { return String(this.detailEngineMode || '').toUpperCase() === 'LEGACY' },
    tasks() { return toArray(this.detail && (this.detail.tasks || this.detail.taskList)) },
    callbacks() { return toArray(this.detail && (this.detail.callbacks || this.detail.outboxEvents || this.detail.callbackOutboxes)) },
    actions() {
      const values = toArray(this.detail && (this.detail.actions || this.detail.actionLogs || this.detail.history))
      if (!this.isLegacyDetail || values.length) return values
      return this.tasks.filter(item => item.action).map(item => ({
        createTime: item.actionTime,
        actionType: item.action,
        nodeName: item.nodeName,
        operatorName: item.operatorName,
        operatorId: item.operatorUserId,
        actionReason: item.comment
      }))
    }
  },
  created() {
    this.loadLegacyTemplates()
    this.load()
  },
  mounted() { window.addEventListener('erp:dept-changed', this.invalidateAdminContext) },
  watch: {
    '$route.fullPath'() { this.invalidateAdminContext() },
    '$store.getters.id'() { this.invalidateAdminContext() }
  },
  activated() { this.pageInactive = false },
  deactivated() { this.pageInactive = true; this.invalidateAdminContext() },
  beforeDestroy() {
    this.pageInactive = true
    this.invalidateAdminContext()
    window.removeEventListener('erp:dept-changed', this.invalidateAdminContext)
  },
  methods: {
    statusLabel,
    statusType,
    isLegacy(row) { return String(row && row.engineMode || '').toUpperCase() === 'LEGACY' },
    businessLabel(code) {
      const found = this.monitorTemplates.find(item => item.businessCode === code)
      return found ? found.templateName : businessCodeLabel(code)
    },
    sourceLabel(source) {
      return {
        native: '统一引擎实例',
        legacy: '兼容引擎实例',
        legacyTemplates: '兼容引擎模板'
      }[source] || '审批来源'
    },
    setSourceError(source, error) {
      this.$set(this.sourceErrors, source, error)
    },
    setSourceLoading(source, loading) {
      this.$set(this.loadingSources, source, loading)
    },
    loadLegacyTemplates() {
      this.setSourceLoading('legacyTemplates', true)
      return listLegacyApprovalTemplates({ silentError: true }).then(response => {
        this.legacyTemplates = toArray(unwrapData(response))
        this.setSourceError('legacyTemplates', null)
        return response
      }).catch(error => {
        this.setSourceError('legacyTemplates', formatApprovalLoadError(
          error,
          '兼容引擎模板暂不可用；实例列表仍会按已知业务代码展示。'
        ))
        return { error }
      }).finally(() => { this.setSourceLoading('legacyTemplates', false) })
    },
    monitorLoadKey(filters, pageSize) {
      return JSON.stringify({ ...filters, callbackStatus: this.query.callbackStatus || '', pageSize })
    },
    applySourcePage(source, response, loadKey) {
      const page = unwrapRows(response)
      if (source === 'native') {
        this.nativeRows = page.rows
        this.nativeTotal = page.total
      } else {
        this.legacyRows = page.rows
        this.legacyTotal = page.total
      }
      this.$set(this.sourceQueryKeys, source, loadKey)
      this.setSourceError(source, null)
    },
    loadMonitorSource(source, request, loadKey) {
      this.setSourceLoading(source, true)
      return request.then(response => {
        this.applySourcePage(source, response, loadKey)
        return response
      }).catch(error => {
        this.setSourceError(source, formatApprovalLoadError(
          error,
          source === 'native'
            ? '统一审批实例服务暂不可用。'
            : '兼容审批实例服务暂不可用。'
        ))
        return { error }
      }).finally(() => { this.setSourceLoading(source, false) })
    },
    rebuildRows(loadKey, end, overPrefixLimit) {
      const nativeRows = this.sourceQueryKeys.native === loadKey ? this.nativeRows : []
      const legacyRows = this.sourceQueryKeys.legacy === loadKey ? this.legacyRows : []
      const combined = [
        ...nativeRows.map(item => ({
          ...item,
          engineMode: item.engineMode || 'NATIVE',
          loadState: this.sourceErrors.native ? 'STALE' : 'FRESH'
        })),
        ...legacyRows.map(item => ({
          ...item,
          engineMode: 'LEGACY',
          loadState: this.sourceErrors.legacy ? 'STALE' : 'FRESH'
        }))
      ].sort(this.compareInstances)
      this.total =
        (this.sourceQueryKeys.native === loadKey ? this.nativeTotal : 0) +
        (this.sourceQueryKeys.legacy === loadKey ? this.legacyTotal : 0)
      if (overPrefixLimit) {
        this.rows = []
        this.$modal.msgWarning(`统一监控最多浏览排序后的前${this.mergedPrefixLimit}条，请缩小业务或状态筛选范围`)
        return
      }
      this.rows = combined.slice((this.query.pageNum - 1) * this.query.pageSize, end)
    },
    load(options = {}) {
      const requestedSources = Array.isArray(options.sources)
        ? options.sources.filter(source => ['native', 'legacy'].includes(source))
        : ['native', 'legacy']
      const filters = ['businessCode', 'businessId', 'status'].reduce((result, key) => {
        if (this.query[key] !== '' && this.query[key] !== undefined) result[key] = this.query[key]
        return result
      }, {})
      const end = this.query.pageNum * this.query.pageSize
      const pageSize = Math.min(end, this.mergedPrefixLimit)
      const overPrefixLimit = end > this.mergedPrefixLimit
      const nativeParams = { ...filters, pageNum: 1, pageSize }
      if (this.query.callbackStatus) nativeParams.callbackStatus = this.query.callbackStatus
      const loadKey = this.monitorLoadKey(filters, pageSize)
      const requests = []
      if (requestedSources.includes('native')) {
        requests.push(this.loadMonitorSource(
          'native',
          listApprovalInstances(nativeParams, { silentError: true }),
          loadKey
        ))
      }
      if (requestedSources.includes('legacy')) {
        if (this.query.callbackStatus) {
          this.legacyRows = []
          this.legacyTotal = 0
          this.$set(this.sourceQueryKeys, 'legacy', loadKey)
          this.setSourceError('legacy', null)
        } else {
          requests.push(this.loadMonitorSource(
            'legacy',
            listLegacyApprovalInstances({ ...filters, pageNum: 1, pageSize }, { silentError: true }),
            loadKey
          ))
        }
      }
      return Promise.all(requests).then(() => {
        this.rebuildRows(loadKey, end, overPrefixLimit)
        return {
          rows: this.rows,
          total: this.instanceTotalKnown ? this.total : null,
          sourceErrors: this.sourceErrors
        }
      })
    },
    retrySource(source) {
      if (source === 'legacyTemplates') return this.loadLegacyTemplates()
      return this.load({ sources: [source] })
    },
    instanceTime(row) {
      const value = row && (row.startedTime || row.createTime)
      const parsed = value ? new Date(value).getTime() : 0
      return Number.isFinite(parsed) ? parsed : 0
    },
    instanceSortKey(row) {
      const engine = this.isLegacy(row) ? 'LEGACY' : 'NATIVE'
      const id = entityId(row, ['legacyInstanceId', 'instanceId', 'id', 'businessId'])
      return `${engine}:${row && row.businessCode || ''}:${id || ''}`
    },
    compareInstances(left, right) {
      const timeDifference = this.instanceTime(right) - this.instanceTime(left)
      if (timeDifference) return timeDifference
      const leftKey = this.instanceSortKey(left)
      const rightKey = this.instanceSortKey(right)
      if (leftKey === rightKey) return 0
      return leftKey < rightKey ? 1 : -1
    },
    search() { this.query.pageNum = 1; return this.load() },
    reset() {
      this.query = { pageNum: 1, pageSize: 10, businessCode: '', businessId: '', status: '', callbackStatus: '' }
      return this.load()
    },
    canTerminate(row) {
      return !this.isLegacy(row) && ['RUNNING', 'COMPLETING', 'RETURNING', 'REJECTING'].includes(String(row.status || '').toUpperCase())
    },
    canReplay(row) {
      return !this.isLegacyDetail && ['RETRY', 'DEAD', 'FAILED'].includes(String(row.callbackStatus || row.status || '').toUpperCase())
    },
    callbackErrorText(row) {
      const value = row && (row.lastErrorMessage || row.lastError)
      if (!value) return '-'
      const text = String(value).trim()
      const labels = {
        INVALID_PAYLOAD: '回调数据无效',
        REMOTE_TIMEOUT: '远程服务超时',
        REMOTE_UNAVAILABLE: '远程服务不可用',
        PERMANENT_FAILURE: '远程服务拒绝处理'
      }
      const code = text.toUpperCase()
      if (labels[code]) return labels[code]
      if (/^REMOTE_HTTP_\d{3}$/.test(code)) return '远程服务返回异常状态'
      return /[A-Za-z]{3,}|[A-Z0-9]+_[A-Z0-9_]+/.test(text)
        ? '系统处理异常，请查看服务日志'
        : text
    },
    candidateNames(task) {
      let candidates = toArray(task && (task.candidates || task.candidateList))
      if (!candidates.length && this.detail && task && task.taskId) {
        candidates = toArray(this.detail.candidates).filter(item => String(item.taskId) === String(task.taskId))
      }
      if (!candidates.length) return task.candidateNames || '-'
      return candidates.map(item => item.userName || item.candidateName || item.userId).filter(Boolean).join('、') || '-'
    },
    openDetail(row) {
      this.adminEpoch += 1
      this.adminActionVisible = false
      this.adminActionLoading = false
      const epoch = ++this.detailReadEpoch, scope = this.adminScope()
      const legacy = this.isLegacy(row)
      const request = legacy
        ? getLegacyApprovalInstance(row.businessCode, entityId(row, ['legacyInstanceId', 'id']))
        : getApprovalInstance(entityId(row, ['instanceId', 'id']))
      this.detailSourceRow = { ...row }
      this.detailEngineMode = legacy ? 'LEGACY' : 'NATIVE'
      this.detail = null
      this.detailVisible = true
      this.detailLoading = true
      return request.then(response => {
        if (!this.pageInactive && epoch === this.detailReadEpoch && scope === this.adminScope()) this.detail = unwrapData(response)
      }).finally(() => { if (epoch === this.detailReadEpoch) this.detailLoading = false })
    },
    isLegacyAdminTarget(target) {
      if (target && target.engineMode) return this.isLegacy(target)
      return this.isLegacyDetail
    },
    openAdminAction(type, target) {
      if (this.pageInactive || this.adminActionLoading) return
      if (this.isLegacyAdminTarget(target)) {
        this.$modal.msgWarning('旧实例由原业务处理')
        return
      }
      let candidates = []
      let recovery = null
      if (type === 'reassign') {
        target = { ...target, instanceId: target.instanceId || (this.detailInstance || {}).instanceId,
          businessRound: (this.detailInstance || {}).businessRound,
          businessNo: (this.detailInstance || {}).businessNo || target.businessNo }
        if (!decimalId(target.taskId || target.id) || !decimalId(target.instanceId) || !decimalId(this.$store.getters.id)) {
          this.$modal.msgWarning('审批任务或账号上下文不完整，请重新打开实例')
          return
        }
        try { recovery = this.readReassignRecovery(target) } catch (error) {
          this.$modal.msgWarning(error.message)
          return
        }
        candidates = this.pendingCandidates(target)
        if (recovery) candidates = [recovery.source]
        if (!recovery && !candidates.length) {
          this.$modal.msgWarning('当前任务没有可改派的待办候选人，请刷新实例详情')
          return
        }
      }
      this.adminEpoch += 1
      this.adminActionError = ''
      this.adminOperation = { type, target: { ...target }, candidates, recovery, contextKey: this.adminContextKey(type, target) }
      this.adminActionVisible = true
    },
    pendingCandidates(task) {
      let candidates = toArray(task && (task.candidates || task.candidateList))
      if (!candidates.length && this.detail && task) {
        const taskId = entityId(task, ['taskId', 'id'])
        candidates = toArray(this.detail.candidates).filter(item => String(item.taskId) === String(taskId))
      }
      return candidates.filter(item => String(item.candidateStatus || item.status || '').toUpperCase() === 'PENDING')
    },
    adminRequestId(type, target) {
      const targetId = entityId(target, ['instanceId', 'taskId', 'outboxId', 'callbackId', 'id']) || 'UNKNOWN'
      const random = Math.random().toString(36).slice(2, 10).toUpperCase()
      return `${String(type || 'ACTION').toUpperCase()}:${targetId}:${Date.now()}:${random}`.slice(0, 128)
    },
    adminScope() { return `${String(this.$store.getters.id || '')}:${String(getSelectedDeptId() || '')}` },
    adminContextKey(type, target) {
      return JSON.stringify([this.adminScope(), type, String(target.instanceId || ''), String(target.nodeId || ''),
        String(entityId(target, type === 'reassign' ? ['taskId', 'id'] : ['instanceId', 'outboxId', 'id']) || ''), String(target.businessRound || '')])
    },
    invalidateAdminContext() {
      this.adminEpoch += 1
      this.detailReadEpoch += 1
      this.adminActionVisible = false
      this.adminActionLoading = false
      this.detailVisible = false
      this.detailLoading = false
      this.detail = null
    },
    reassignStorageKey(target) {
      return `erp:approval-reassign:v1:${this.adminScope()}:${decimalId(target.taskId || target.id)}`
    },
    readReassignRecovery(target) {
      const text = window.sessionStorage.getItem(this.reassignStorageKey(target))
      if (!text) return null
      const command = JSON.parse(text)
      if (command.contextKey !== this.adminContextKey('reassign', target) || !command.body || !command.recipient
        || !command.source || !decimalId(command.body.fromCandidateId) || !decimalId(command.body.toUserId)
        || decimalId(command.source.candidateId) !== command.body.fromCandidateId
        || decimalId(command.recipient.userId) !== command.body.toUserId
        || typeof command.body.reason !== 'string' || command.body.reason.trim().length < 2 || command.body.reason.length > 500
        || typeof command.body.requestId !== 'string' || !command.body.requestId || command.body.requestId.length > 128) {
        throw new Error('存在上下文不一致的改派恢复记录，请先核对原审批实例')
      }
      return command
    },
    submitReassignAction(payload) {
      const target = this.adminOperation.target || {}
      const contextKey = this.adminContextKey('reassign', target)
      if (!this.adminActionVisible || this.pageInactive || this.adminActionLoading
        || payload.contextKey !== contextKey || contextKey !== this.adminOperation.contextKey) return Promise.resolve()
      const epoch = this.adminEpoch, key = this.reassignStorageKey(target)
      let command
      try {
        command = this.readReassignRecovery(target)
        if (!command) {
          const fromCandidateId = decimalId(payload.fromCandidateId), toUserId = decimalId(payload.toUserId)
          const source = this.pendingCandidates(target).find(item => decimalId(item.candidateId) === fromCandidateId)
          if (!source || !toUserId || !payload.recipient || decimalId(payload.recipient.userId) !== toUserId) throw new Error('请选择当前任务的候选人和接收人')
          command = { contextKey, target: { ...target }, source: { ...source, candidateId: fromCandidateId },
            recipient: { ...payload.recipient, userId: toUserId },
            body: { fromCandidateId, toUserId, reason: payload.reason, requestId: this.adminRequestId('reassign', target) } }
          window.sessionStorage.setItem(key, JSON.stringify(command))
        } else if (command.body.fromCandidateId !== decimalId(payload.fromCandidateId)
          || command.body.toUserId !== decimalId(payload.toUserId) || command.body.reason !== payload.reason) {
          throw new Error('上次改派结果待确认，请使用原接收人和原因重试')
        }
      } catch (error) {
        this.adminActionError = error.message || '无法保存改派恢复记录，请稍后重试'
        return Promise.resolve()
      }
      this.adminActionLoading = true
      this.adminActionError = ''
      const current = () => !this.pageInactive && epoch === this.adminEpoch && contextKey === this.adminContextKey('reassign', target)
      return reassignApprovalTask(decimalId(target.taskId || target.id), { ...command.body }).then(() => {
        try { window.sessionStorage.removeItem(key) } catch (error) { /* Replaying the retained original command remains idempotent. */ }
        if (!current()) return
        this.$modal.msgSuccess('改派已提交')
        this.adminActionVisible = false
        return Promise.all([this.load(), this.detailVisible && this.detailSourceRow ? this.openDetail(this.detailSourceRow) : Promise.resolve()])
          .catch(() => this.$modal.msgWarning('改派已提交，列表刷新失败，请手动刷新核对'))
      }, error => {
        const status = error.response && Number(error.response.status)
        const definiteRejection = status >= 200 && status < 500 && status !== 408
        if (definiteRejection) {
          try { window.sessionStorage.removeItem(key) } catch (ignored) { /* Keep the original retry identity if storage is unavailable. */ }
        }
        if (!current()) return
        if (!definiteRejection) this.$set(this.adminOperation, 'recovery', command)
        this.adminActionError = definiteRejection ? (error.message || '改派被拒绝，请刷新核对')
          : '改派结果暂未确认，已保留原接收人和原因；请重试原改派核实结果'
      }).finally(() => { if (epoch === this.adminEpoch) this.adminActionLoading = false })
    },
    submitAdminAction(payload) {
      if (payload.type === 'reassign') return this.submitReassignAction(payload)
      if (this.adminActionLoading) return Promise.resolve()
      const target = payload.target || {}
      if (this.isLegacyAdminTarget(target)) {
        this.$modal.msgWarning('旧实例由原业务处理')
        return Promise.resolve()
      }
      let request
      const requestId = this.adminRequestId(payload.type, target)
      this.adminActionLoading = true
      if (payload.type === 'terminate') {
        request = terminateApprovalInstance(entityId(target, ['instanceId', 'id']), { reason: payload.reason, requestId })
      } else {
        request = replayApprovalCallback(entityId(target, ['outboxId', 'callbackId', 'id']), { reason: payload.reason, requestId })
      }
      return request.then(() => {
        this.$modal.msgSuccess(`${{ terminate: '终止', reassign: '改派', replay: '回调重试' }[payload.type]}已提交`)
        this.adminActionVisible = false
        return Promise.all([this.load(), this.detailVisible && this.detailSourceRow ? this.openDetail(this.detailSourceRow) : Promise.resolve()])
      }).finally(() => { this.adminActionLoading = false })
    }
  }
}
</script>

<style lang="scss" scoped>
.approval-panel { display: flex; flex-direction: column; gap: 12px; }
.filter-card { border-radius: 10px; }
.filter-card ::v-deep .el-form-item { margin-bottom: 0; }
.approval-source-errors {
  padding: 14px 16px;
  border: 1px solid #fbc4c4;
  border-radius: 10px;
  background: #fef0f0;
}
.approval-source-errors__heading,
.approval-source-error {
  display: flex;
  align-items: center;
  gap: 10px;
}
.approval-source-errors__heading > i { color: #f56c6c; font-size: 22px; }
.approval-source-errors__heading > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.approval-source-errors__heading strong { color: #c45656; }
.approval-source-errors__heading span,
.approval-source-error small { color: #909399; font-size: 12px; }
.approval-source-error {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f5c2c2;
}
.approval-source-error > span { min-width: 0; flex: 1; }
.unknown-total-hint { margin-top: 12px; color: #e6a23c; font-size: 13px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.card-heading div { display: flex; flex-direction: column; gap: 4px; }
.card-heading small, .muted { color: #8492a6; font-size: 12px; }
.danger-text { color: #f56c6c; }
.detail-summary { margin-bottom: 18px; }
.legacy-readonly { margin-bottom: 18px; }
.legacy-actions { display: flex; gap: 8px; margin-top: 10px; }
.disabled-action { display: inline-block; }
h4 { margin: 20px 0 10px; color: #303133; }
</style>
