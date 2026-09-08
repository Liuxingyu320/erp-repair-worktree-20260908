<template>
  <div class="app-container unified-todo-page">
    <el-card shadow="never" class="todo-filter-card">
      <div slot="header" class="todo-page-header">
        <div>
          <span class="section-eyebrow">统一工作台</span>
          <h2>我的待办</h2>
          <p>当前账号：{{ currentAccountName }} · 待我审批：{{ currentApprovalCount }} · 仅显示分配给本人的可处理事项；集中查看审批、执行、退回修改、风险和个人事项。</p>
        </div>
        <el-button icon="el-icon-refresh" :loading="pageLoading" @click="manualRefresh">刷新</el-button>
      </div>

      <el-tabs v-model="filters.category" class="category-tabs" aria-label="待办分类" @tab-click="handleFilterChange">
        <el-tab-pane v-for="item in categories" :key="item.value" :label="item.label" :name="item.value" />
      </el-tabs>

      <el-form :model="filters" size="small" class="todo-filter-form" @submit.native.prevent>
        <div class="todo-filter-grid">
          <el-form-item label="来源" class="todo-filter-group todo-source-group">
            <el-select v-model="filters.source" aria-label="待办来源" @change="handleFilterChange">
              <el-option v-for="item in sources" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词" class="todo-filter-group todo-keyword-group">
            <div class="todo-keyword-control">
              <el-input
                v-model="keywordDraft"
                aria-label="待办关键词"
                clearable
                placeholder="标题、单号或摘要"
                @keyup.enter.native="handleQuery"
                @clear="handleQuery"
              />
              <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <div v-if="hasActiveFilters" class="active-filter-bar" aria-label="当前生效的筛选条件">
        <span>当前筛选</span>
        <el-tag
          v-for="item in activeFilterItems"
          :key="item.key"
          closable
          size="small"
          @close="removeActiveFilter(item.key)"
        >{{ item.label }}</el-tag>
        <el-button type="text" size="small" @click="clearFilters">重置全部</el-button>
      </div>
      <p class="scope-help">{{ scopeDescription }}</p>
    </el-card>

    <el-alert
      v-if="statusMessage && !providerDiagnosticRows.length"
      :title="statusMessage"
      type="warning"
      :closable="false"
      show-icon
      class="provider-alert"
    />
    <el-card
      v-if="providerDiagnosticRows.length"
      shadow="never"
      class="provider-diagnostics"
      role="alert"
    >
      <div slot="header" class="provider-diagnostics__header">
        <div>
          <strong>待办来源诊断</strong>
          <span>失败来源保持“未知”，不会以 0 条待办代替；其他来源可继续浏览。</span>
        </div>
      </div>
      <div
        v-for="item in providerDiagnosticRows"
        :key="item.source"
        class="provider-diagnostic-row"
      >
        <el-tag size="mini" :type="item.stale ? 'warning' : 'danger'">{{ item.label }}</el-tag>
        <div class="provider-diagnostic-row__copy">
          <strong>{{ item.message }}</strong>
          <span>{{ providerLastSuccessText(item) }}</span>
          <small>接口反馈：{{ item.diagnostic.message }}</small>
          <small v-if="item.diagnostic.detail">{{ item.diagnostic.detail }}</small>
        </div>
        <el-button
          type="primary"
          plain
          size="small"
          icon="el-icon-refresh"
          :loading="!!retryingProviders[item.source]"
          @click="retryProvider(item.source)"
        >重试此来源</el-button>
      </div>
    </el-card>
    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      :closable="false"
      show-icon
      class="provider-alert"
    />

    <el-card shadow="never" class="todo-list-card">
      <div slot="header" class="todo-list-header">
        <div>
          <strong>待办列表</strong>
          <small v-if="total === null">已加载约 {{ estimatedTotal }} 条（总数未知，仅供参考）</small>
          <small v-else>共 {{ total }} 条</small>
          <small class="todo-sort-label">{{ sortLabel }}</small>
        </div>
        <span class="scope-description">{{ scopeDescription }}</span>
      </div>

      <div class="todo-priority-filter">
        <span>优先级</span>
        <el-radio-group
          v-model="filters.priority"
          size="mini"
          aria-label="待办优先级"
          @change="handlePriorityChange"
        >
          <el-radio-button v-for="item in priorities" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="batchApproveEnabled && batchSelectableRows.length" class="todo-batch-toolbar">
        <el-checkbox
          :value="allBatchRowsSelected"
          :indeterminate="someBatchRowsSelected"
          :disabled="batchExecuting"
          @change="toggleAllBatchRows"
        >选择本页可批量审批项</el-checkbox>
        <span>已选 {{ selectedBatchRows.length }} 条（当前页，最多 20 条）</span>
        <span class="todo-batch-toolbar__spacer"></span>
        <el-button size="small" :disabled="!selectedBatchRows.length || batchExecuting" @click="clearBatchSelection">取消选择</el-button>
        <el-button
          type="primary"
          size="small"
          :disabled="!selectedBatchRows.length || batchExecuting"
          @click="openBatchApproval"
        >批量通过</el-button>
      </div>

      <div v-loading="pageLoading" class="todo-list-body">
        <div v-if="!pageLoading && rows.length === 0 && total === null" class="todo-empty unknown">
          <i class="el-icon-warning-outline"></i>
          <strong>部分数据未知</strong>
          <span>{{ emptyStateDescription }}</span>
          <el-button v-if="hasActiveFilters" type="text" @click="clearFilters">清除筛选</el-button>
        </div>
        <div v-else-if="!pageLoading && rows.length === 0" class="todo-empty">
          <i class="el-icon-circle-check"></i>
          <strong>暂无待办</strong>
          <span>{{ emptyStateDescription }}</span>
          <el-button v-if="hasActiveFilters" type="text" @click="clearFilters">清除筛选</el-button>
        </div>

        <article
          v-for="row in rows"
          :key="row.todoKey"
          class="todo-row"
          :class="{ stale: staleSources.includes(row.provider || row.source) }"
          :data-todo-key="row.todoKey"
        >
          <div class="todo-row-layout">
            <el-checkbox
              v-if="isBatchApprovable(row)"
              class="todo-batch-selector"
              :value="isBatchSelected(row)"
              :disabled="batchExecuting"
              :aria-label="`选择 ${row.businessNo || row.title || '当前调拨审批'}`"
              @change="toggleBatchRow(row, $event)"
            />
            <button
              type="button"
              class="todo-row-action"
              :aria-label="`${row.title || row.businessNo || '待处理事项'}，${categoryLabel(row.category)}，${processLabel(row)}`"
              @click="handleTodoAction(row)"
            >
              <span class="priority-mark" :class="row.priority || 'normal'"></span>
              <span class="todo-main">
                <span class="todo-title-row">
                  <strong>{{ row.title || row.businessNo || '待处理事项' }}</strong>
                  <el-tag size="mini" :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
                  <el-tag v-if="staleSources.includes(row.provider || row.source)" size="mini" type="warning">缓存数据</el-tag>
                </span>
                <span class="todo-summary">{{ row.summary || row.businessNo || '请进入业务页面查看详情' }}</span>
                <span class="todo-meta">
                  <span><i class="el-icon-office-building"></i>{{ row.contextDeptName || row.deptName || '个人事项' }}</span>
                  <span><i class="el-icon-time"></i>等待 {{ waitingText(row.waitingSeconds, row.createdTime) }}</span>
                  <span>{{ sourceLabel(row.source) }}</span>
                  <span>{{ categoryLabel(row.category) }}</span>
                </span>
              </span>
              <span class="todo-process-label">{{ processLabel(row) }}</span>
            </button>
            <el-button
              v-if="isQuickApprovable(row)"
              type="success"
              plain
              class="todo-quick-approve"
              :loading="quickSubmitting && quickRow && quickRow.todoKey === row.todoKey"
              :disabled="quickSubmitting || batchExecuting || batchDialogVisible"
              :aria-label="`通过 ${row.businessNo || row.title || '当前调拨审批'}`"
              @click="openQuickApproval(row, 'quick')"
            >通过</el-button>
          </div>
        </article>
      </div>

      <pagination
        v-if="total !== null && total > 0"
        :total="total"
        :page.sync="filters.pageNum"
        :limit.sync="filters.pageSize"
        @pagination="handlePagination"
      />
      <div v-else-if="total === null" class="unknown-pagination" aria-label="总数未知的待办分页">
        <el-button
          size="mini"
          icon="el-icon-arrow-left"
          :disabled="!canPreviousUnknownPage"
          @click="goUnknownPage(-1)"
        >上一页</el-button>
        <span>第 {{ filters.pageNum }} 页 / 总数未知</span>
        <el-button
          size="mini"
          :disabled="!canNextUnknownPage"
          @click="goUnknownPage(1)"
        >下一页<i class="el-icon-arrow-right el-icon--right"></i></el-button>
      </div>
    </el-card>

    <p class="todo-live-status" aria-live="polite" aria-atomic="true">{{ approvalAnnouncement }}</p>
    <todo-quick-approve-dialog
      :visible.sync="quickDialogVisible"
      :mode="quickDialogMode"
      :row="quickRow || {}"
      :preview="quickPreview"
      :loading="quickPreviewLoading"
      :submitting="quickSubmitting"
      :error-message="quickError"
      :next-available="quickNextAvailable"
      :retry-available="quickRetryAvailable"
      @close="closeQuickApproval"
      @confirm="submitQuickApproval"
      @retry="retryQuickApproval"
    />
    <todo-batch-approve-dialog
      :visible.sync="batchDialogVisible"
      :items="batchItems"
      :executing="batchExecuting"
      :processed="batchProcessed"
      :total="batchTotal"
      :results="batchResults"
      :error-message="batchError"
      @close="closeBatchApproval"
      @confirm="submitBatchApproval"
      @retry-failed="retryFailedBatchApproval"
      @stop="stopBatchApproval"
    />
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { constantRoutes } from '@/router'
import defaultSettings from '@/settings'
import { getTransferDetail, approveTransfer } from '@/api/inventory/transfer'
import { approveApprovalTask } from '@/api/approval/task'
import { TODO_SORT_LABEL } from '@/utils/todoAggregator'
import {
  canBatchApproveTodo,
  canQuickApproveTodo,
  classifyTodoApprovalError,
  createBatchApprovalItems,
  createTodoApprovalRequestId,
  executeTodoApproval,
  executeTodoApprovalBatch,
  loadTodoApprovalPreview
} from '@/utils/todoApprovalActions'
import {
  beginTodoActionReturn,
  clearTodoActionReturn,
  consumeTodoActionReturn
} from '@/utils/todoActionReturn'
import { buildAvailableRouteSet } from '@/utils/todoRouteResolver'
import { navigateTodo } from '@/utils/todoNavigator'
import { beginTodoContextLease, formatTodoContextSwitchNotice, rollbackTodoContextLease } from '@/utils/todoContextLease'
import { clearSelectedDept, getSelectedDeptContext, hasValidInventoryDeptContext, setSelectedDept } from '@/utils/shopContext'
import TodoBatchApproveDialog from './components/TodoBatchApproveDialog'
import TodoQuickApproveDialog from './components/TodoQuickApproveDialog'
import {
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
} from '@/utils/todoFilterQuery'

const BUSINESS_SOURCE_VALUES = ['inventory', 'oa', 'system']
const PROVIDER_VALUES = ['approval', 'inventory', 'oa', 'system']

function providersForBusinessSource(source) {
  return source === 'all' || !BUSINESS_SOURCE_VALUES.includes(source)
    ? PROVIDER_VALUES.slice()
    : ['approval', source]
}

export default {
  name: 'UnifiedTodoCenter',
  components: { TodoBatchApproveDialog, TodoQuickApproveDialog },
  data() {
    const contextSnapshot = getSelectedDeptContext()
    const filters = normalizeTodoFilterQuery({}, {
      hasCurrentOrgContext: hasValidInventoryDeptContext(contextSnapshot),
      includePagination: true
    })
    return {
      contextSnapshot,
      sortLabel: TODO_SORT_LABEL,
      categories: [
        { label: '全部', value: "all" },
        { label: '待审批', value: "approval" },
        { label: '待执行', value: "execution" },
        { label: '退回修改', value: "returned" },
        { label: '风险提醒', value: "risk" },
        { label: '个人事项', value: "personal" }
      ],
      sources: [
        { label: '全部来源', value: "all" },
        { label: '库存', value: "inventory" },
        { label: 'OA', value: "oa" },
        { label: '系统', value: "system" }
      ],
      priorities: [
        { label: '全部', value: "all" },
        { label: '紧急', value: "urgent" },
        { label: '重要', value: "important" },
        { label: '普通', value: "normal" }
      ],
      filters,
      keywordDraft: filters.keyword,
      rows: [],
      total: 0,
      estimatedTotal: 0,
      staleSources: [],
      unknownSources: [],
      failures: [],
      pageError: '',
      todoDisposed: false,
      todoActive: true,
      routeOperationSequence: 0,
      pageRequestSequence: 0,
      pendingRouteOperation: null,
      localRouteAttempts: [],
      retryingProviders: {},
      quickApproveEnabled: defaultSettings.todoQuickApproveEnabled === true,
      quickDialogVisible: false,
      quickDialogMode: 'quick',
      quickRow: null,
      quickPreview: null,
      quickPreviewLoading: false,
      quickSubmitting: false,
      quickError: '',
      quickRetryAvailable: false,
      quickRequestId: '',
      quickRequestSequence: 0,
      approvalAnnouncement: '',
      batchApproveEnabled: defaultSettings.todoBatchApproveEnabled === true,
      selectedTodoKeys: [],
      batchDialogVisible: false,
      batchItems: [],
      batchExecuting: false,
      batchStopRequested: false,
      batchProcessed: 0,
      batchTotal: 0,
      batchResults: [],
      batchError: ''
    }
  },
  computed: {
    ...mapGetters({
      providerStates: 'todoProviderStates',
      permissions: 'permissions',
      permissionRoutes: 'permission_routes',
      currentNickName: 'nickName',
      currentLoginName: 'name',
      todoCounts: 'todoCounts'
    }),
    currentAccountName() {
      return this.currentNickName || this.currentLoginName || '当前用户'
    },
    currentApprovalCount() {
      return Math.max(0, Number(this.todoCounts && this.todoCounts.approval) || 0)
    },
    pageLoading() {
      return this.$store.state.todo.pageLoading
    },
    selectedProviders() {
      return providersForBusinessSource(this.filters.source)
    },
    hasCurrentOrgContext() {
      return hasValidInventoryDeptContext(this.contextSnapshot)
    },
    hasActiveFilters() {
      return hasActiveTodoFilters(this.filters, this.filterQueryOptions())
    },
    canPreviousUnknownPage() {
      return this.total === null && !this.pageLoading && this.filters.pageNum > 1
    },
    canNextUnknownPage() {
      return this.total === null && !this.pageLoading && this.rows.length >= this.filters.pageSize
    },
    scopeDescription() {
      const deptName = this.contextSnapshot && this.contextSnapshot.deptName
      return this.hasCurrentOrgContext
        ? `本人专属待办跨已授权门店 · 公共待办：当前门店（${deptName || '未命名门店'}）`
        : '本人专属待办正常显示 · 选择门店后显示公共待办'
    },
    emptyStateDescription() {
      const priority = this.filters.priority === 'all'
        ? ''
        : `当前优先级为${this.priorityLabel(this.filters.priority)}；`
      return this.total === null
        ? `${priority}当前页为空，但总数未知，无法确认是否确实没有待办。`
        : `${priority}当前筛选条件下没有需要处理的事项。`
    },
    activeFilterItems() {
      const defaults = normalizeTodoFilterQuery({}, this.filterQueryOptions())
      const items = []
      if (this.filters.category !== defaults.category) {
        const category = this.categories.find(item => item.value === this.filters.category)
        items.push({ key: 'category', label: `分类：${category ? category.label : this.filters.category}` })
      }
      if (this.filters.source !== defaults.source) {
        const source = this.sources.find(item => item.value === this.filters.source)
        items.push({ key: 'source', label: `来源：${source ? source.label : this.filters.source}` })
      }
      if (this.filters.keyword) items.push({ key: 'keyword', label: `关键词：${this.filters.keyword}` })
      if (this.filters.priority !== defaults.priority) {
        const priority = this.priorities.find(item => item.value === this.filters.priority)
        items.push({ key: 'priority', label: `优先级：${priority ? priority.label : this.filters.priority}` })
      }
      return items
    },
    statusMessage() {
      const pending = this.selectedProviders.filter(source => {
        const provider = this.providerStates[source]
        return provider && provider.summary === null
      })
      const stale = Array.from(new Set([
        ...this.staleSources,
        ...this.selectedProviders.filter(source => this.providerStates[source] && this.providerStates[source].stale)
      ]))
      const messages = []
      if (this.unknownSources.length || pending.length) {
        messages.push(`数据未知：${this.sourceNames(Array.from(new Set([...this.unknownSources, ...pending])))}`)
      }
      if (stale.length) messages.push(`缓存数据：${this.sourceNames(stale)}`)
      return messages.join('；')
    },
    providerFailureMap() {
      return (this.failures || []).reduce((result, failure) => {
        if (failure && failure.source) result[failure.source] = failure
        return result
      }, {})
    },
    providerDiagnosticRows() {
      return this.selectedProviders.map(source => {
        const provider = this.providerStates[source] || {}
        const failure = this.providerFailureMap[source]
        const unknown = this.unknownSources.includes(source) || provider.unknown
        const stale = this.staleSources.includes(source) || provider.stale
        const error = failure && failure.error || provider.error || null
        if (!unknown && !stale && !error) return null
        const diagnostic = this.failureDiagnostic(error)
        let message
        if (stale) {
          message = `${this.sourceLabel(source)}实时数据暂不可用，当前仅显示最近成功缓存。`
        } else {
          message = `${this.sourceLabel(source)}数据未知，无法确认是否存在待办。`
        }
        return {
          source,
          label: this.sourceLabel(source),
          stale,
          unknown,
          message,
          diagnostic,
          lastSuccessAt: provider.lastSuccessAt || null
        }
      }).filter(Boolean)
    },
    availableRouteSet() {
      return buildAvailableRouteSet({
        constantRoutes,
        dynamicRoutes: this.permissionRoutes,
        permissions: this.permissions
      })
    },
    quickNextAvailable() {
      return !!this.nextQuickApprovalRow(this.quickRow)
    },
    batchSelectableRows() {
      return this.rows.filter(row => this.isBatchApprovable(row))
    },
    selectedBatchRows() {
      const selected = new Set(this.selectedTodoKeys)
      return this.batchSelectableRows.filter(row => selected.has(row.todoKey))
    },
    allBatchRowsSelected() {
      return this.batchSelectableRows.length > 0 &&
        this.batchSelectableRows.slice(0, 20).every(row => this.selectedTodoKeys.includes(row.todoKey))
    },
    someBatchRowsSelected() {
      return this.selectedBatchRows.length > 0 && !this.allBatchRowsSelected
    }
  },
  watch: {
    '$route.query': {
      deep: true,
      handler(query) {
        return this.handleRouteQuery(query || {})
      }
    }
  },
  created() {
    this.applyRouteQuery(this.$route.query || {})
    return this.syncRouteAndLoad().then(() => this.restoreTodoActionReturn())
  },
  mounted() {
    window.addEventListener('erp:dept-changed', this.handleDeptChanged)
  },
  activated() {
    return this.resumeTodoActivity()
  },
  deactivated() {
    this.suspendTodoActivity()
  },
  beforeDestroy() {
    this.todoDisposed = true
    this.todoActive = false
    this.routeOperationSequence += 1
    this.pageRequestSequence += 1
    this.quickRequestSequence += 1
    this.pendingRouteOperation = null
    this.localRouteAttempts = []
    window.removeEventListener('erp:dept-changed', this.handleDeptChanged)
  },
  methods: {
    quickEligibilityOptions() {
      return {
        enabled: this.quickApproveEnabled,
        permissions: this.permissions,
        staleSources: this.staleSources
      }
    },
    isQuickApprovable(row) {
      return canQuickApproveTodo(row, this.quickEligibilityOptions())
    },
    batchEligibilityOptions() {
      return {
        enabled: this.batchApproveEnabled,
        permissions: this.permissions,
        staleSources: this.staleSources
      }
    },
    isBatchApprovable(row) {
      return canBatchApproveTodo(row, this.batchEligibilityOptions())
    },
    isBatchSelected(row) {
      return !!row && this.selectedTodoKeys.includes(row.todoKey)
    },
    toggleBatchRow(row, selected) {
      if (!this.isBatchApprovable(row) || this.batchExecuting) return
      const keys = this.selectedTodoKeys.filter(key => key !== row.todoKey)
      if (selected) {
        if (keys.length >= 20) {
          this.$message.warning('单次最多选择20条待审批事项')
          return
        }
        keys.push(row.todoKey)
      }
      this.selectedTodoKeys = keys
    },
    toggleAllBatchRows(selected) {
      if (this.batchExecuting) return
      this.selectedTodoKeys = selected
        ? this.batchSelectableRows.slice(0, 20).map(row => row.todoKey)
        : []
      if (selected && this.batchSelectableRows.length > 20) {
        this.$message.info('本页可审批事项超过20条，已选择前20条')
      }
    },
    clearBatchSelection() {
      if (this.batchExecuting) return
      this.selectedTodoKeys = []
    },
    synchronizeBatchSelection() {
      const allowed = new Set(this.batchSelectableRows.map(row => row.todoKey))
      this.selectedTodoKeys = this.selectedTodoKeys.filter(key => allowed.has(key))
    },
    openBatchApproval() {
      if (this.batchExecuting || !this.selectedBatchRows.length) return
      try {
        this.batchItems = createBatchApprovalItems(this.selectedBatchRows)
      } catch (error) {
        this.$message.warning(error.message || '无法创建批量审批')
        return
      }
      this.batchResults = []
      this.batchProcessed = 0
      this.batchTotal = this.batchItems.length
      this.batchError = ''
      this.batchDialogVisible = true
    },
    batchFailureMessage(item) {
      return {
        SESSION: '登录状态已失效，请重新登录后确认结果',
        FORBIDDEN: '审批权限或候选关系已变化',
        CONFLICT: '任务状态已变化，可能已被其他人处理',
        UNKNOWN: '网络结果未知，已刷新当前列表；请用原请求号重试或查看详情确认',
        USER_CANCELED: '用户已停止，尚未开始处理'
      }[item.errorKind] || '审批未成功，请刷新后重试'
    },
    reconcileBatchResults(results) {
      const remaining = new Set(this.rows.map(row => row.todoKey))
      return results.map(item => {
        if (item.status === 'SUCCESS') return { ...item, message: '审批通过' }
        const provider = item.row && (item.row.provider || item.row.source)
        const reliable = !this.staleSources.includes(provider) && !this.unknownSources.includes(provider)
        if (item.status === 'FAILED' && item.errorKind === 'CONFLICT' && reliable && !remaining.has(item.todoKey)) {
          return { ...item, status: 'ALREADY_HANDLED', message: '接口报告任务冲突，刷新后该事项已不在当前账号待办中' }
        }
        if (item.status === 'FAILED' && item.errorKind === 'UNKNOWN' && reliable && !remaining.has(item.todoKey)) {
          return { ...item, message: '网络结果未知；刷新后当前页未找到该事项，未按成功处理，请用原请求号重试确认' }
        }
        if (item.status === 'CANCELED') return { ...item, message: this.batchFailureMessage(item) }
        return { ...item, message: this.batchFailureMessage(item) }
      })
    },
    async runBatchApproval(items, comment, retry = false) {
      if (this.batchExecuting || !Array.isArray(items) || !items.length) return
      this.batchExecuting = true
      this.batchStopRequested = false
      this.batchError = ''
      this.batchProcessed = 0
      this.batchTotal = items.length
      try {
        const results = await executeTodoApprovalBatch(items, { approveApprovalTask, approveTransfer }, {
          comment,
          concurrency: 3,
          shouldStop: () => this.batchStopRequested,
          onProgress: (item, completed) => {
            this.batchProcessed = completed
          }
        })
        this.batchProcessed = items.length
        await this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
        // Invalidation clears every provider page cache, so the converging reload must
        // fetch every selected provider. A targeted reload here would mark untouched
        // sources as unknown immediately after a successful approval.
        await this.loadPage()
        await this.ensureValidKnownPage()
        const reconciled = this.reconcileBatchResults(results)
        if (retry) {
          const replacements = new Map(reconciled.map(item => [item.requestId, item]))
          this.batchResults = this.batchResults.map(item => replacements.get(item.requestId) || item)
        } else {
          this.batchResults = reconciled
        }
        const failedKeys = new Set(this.batchResults
          .filter(item => item.status === 'FAILED' || item.status === 'CANCELED')
          .map(item => item.todoKey))
        this.selectedTodoKeys = this.rows
          .filter(row => failedKeys.has(row.todoKey) && this.isBatchApprovable(row))
          .map(row => row.todoKey)
        const succeeded = this.batchResults.filter(item => item.status === 'SUCCESS' || item.status === 'ALREADY_HANDLED').length
        const failed = this.batchResults.length - succeeded
        this.approvalAnnouncement = `批量审批完成：成功或已处理${succeeded}条，未成功${failed}条。`
        this.$message[failed ? 'warning' : 'success'](this.approvalAnnouncement)
      } catch (error) {
        this.batchError = '批量审批执行异常，已保留当前选择，请刷新状态后重试。'
      } finally {
        this.batchExecuting = false
        this.batchStopRequested = false
      }
    },
    stopBatchApproval() {
      if (!this.batchExecuting) return
      this.batchStopRequested = true
      this.approvalAnnouncement = '正在停止批量审批；已发出的请求会继续收口，未开始项不会再提交。'
    },
    submitBatchApproval({ comment = '' } = {}) {
      return this.runBatchApproval(this.batchItems.slice(), comment, false)
    },
    retryFailedBatchApproval({ comment = '' } = {}) {
      const items = this.batchResults.filter(item =>
        (item.status === 'FAILED' && item.errorKind !== 'SESSION') ||
        (item.status === 'CANCELED' && item.errorKind === 'USER_CANCELED')
      )
      return this.runBatchApproval(items, comment, true)
    },
    closeBatchApproval() {
      if (this.batchExecuting) return
      this.batchDialogVisible = false
      this.batchItems = []
      this.batchResults = []
      this.batchStopRequested = false
      this.batchProcessed = 0
      this.batchTotal = 0
      this.batchError = ''
    },
    nextQuickApprovalRow(row) {
      if (!row || !Array.isArray(this.rows) || !this.rows.length) return null
      const currentIndex = this.rows.findIndex(item => item && item.todoKey === row.todoKey)
      if (currentIndex < 0) return this.rows.find(item => this.isQuickApprovable(item)) || null
      const candidates = this.rows.slice(currentIndex + 1).concat(this.rows.slice(0, currentIndex))
      return candidates.find(item => item && item.todoKey !== row.todoKey && this.isQuickApprovable(item)) || null
    },
    async openQuickApproval(row, mode = 'quick') {
      if (!this.isQuickApprovable(row) || this.quickSubmitting || this.batchExecuting || this.batchDialogVisible) return
      const sequence = ++this.quickRequestSequence
      this.quickDialogMode = mode === 'detail' ? 'detail' : 'quick'
      this.quickRow = row
      this.quickPreview = null
      this.quickError = ''
      this.quickRetryAvailable = false
      this.quickRequestId = createTodoApprovalRequestId('TODOQ', row.todoKey || row.businessId)
      this.quickDialogVisible = true
      this.quickPreviewLoading = true
      try {
        const preview = await loadTodoApprovalPreview(row, { getTransferDetail })
        if (sequence !== this.quickRequestSequence || !this.quickDialogVisible || !this.quickRow || this.quickRow.todoKey !== row.todoKey) return
        this.quickPreview = preview
      } catch (error) {
        if (sequence !== this.quickRequestSequence || !this.quickDialogVisible) return
        this.quickError = '详情已变化或暂时无法读取，请刷新待办后重试。'
        await this.loadPage({ networkSources: [row.provider || row.source] })
        await this.ensureValidKnownPage()
      } finally {
        if (sequence === this.quickRequestSequence) this.quickPreviewLoading = false
      }
    },
    closeQuickApproval() {
      if (this.quickSubmitting) return
      this.quickRequestSequence += 1
      this.quickDialogVisible = false
      this.quickDialogMode = 'quick'
      this.quickRow = null
      this.quickPreview = null
      this.quickPreviewLoading = false
      this.quickError = ''
      this.quickRetryAvailable = false
      this.quickRequestId = ''
    },
    retryQuickApproval({ comment = '' } = {}) {
      if (!this.quickRetryAvailable) return
      return this.submitQuickApproval({ comment, continueNext: false })
    },
    async submitQuickApproval({ comment = '', continueNext = false } = {}) {
      const row = this.quickRow
      if (!row || this.quickSubmitting || !this.quickPreview || !this.isQuickApprovable(row)) return
      const requestId = this.quickRequestId
      const nextDialogMode = this.quickDialogMode
      const nextTodoKey = continueNext && this.nextQuickApprovalRow(row)
        ? this.nextQuickApprovalRow(row).todoKey
        : ''
      this.quickSubmitting = true
      this.quickError = ''
      this.quickRetryAvailable = false
      try {
        await executeTodoApproval(row, { approveApprovalTask, approveTransfer }, {
          requestId,
          comment,
          suppressTodoMutationRefresh: true
        })
        this.approvalAnnouncement = `${row.businessNo || row.title || '当前单据'}审批通过。`
        this.$message.success(this.approvalAnnouncement)
        await this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
        // The mutation invalidates the complete aggregated page cache. Reload all
        // selected providers so OA, inventory and system rows stay authoritative.
        await this.loadPage()
        await this.ensureValidKnownPage()
        this.quickSubmitting = false
        if (continueNext) {
          const nextRow = this.rows.find(item => item.todoKey === nextTodoKey && this.isQuickApprovable(item)) ||
            this.rows.find(item => this.isQuickApprovable(item))
          if (nextRow) {
            await this.openQuickApproval(nextRow, nextDialogMode)
            return
          }
          this.approvalAnnouncement += ' 当前筛选下可快速审批事项已处理完。'
          this.$message.info('当前筛选下可快速审批事项已处理完')
        }
        this.closeQuickApproval()
      } catch (error) {
        const failure = classifyTodoApprovalError(error)
        const messages = {
          SESSION: '登录状态已失效，请重新登录后确认审批结果。',
          FORBIDDEN: '审批权限或候选关系已变化，请刷新后处理。',
          CONFLICT: '该任务状态已变化，可能已被处理。请刷新确认。',
          UNKNOWN: '审批结果暂时未知，已保留当前待办，请刷新确认后再试。'
        }
        this.quickError = messages[failure.errorKind] || messages.UNKNOWN
        this.approvalAnnouncement = this.quickError
        await this.loadPage({ networkSources: [row.provider || row.source] })
        await this.ensureValidKnownPage()
        const provider = row.provider || row.source
        const reliableRefresh = !this.staleSources.includes(provider) && !this.unknownSources.includes(provider)
        const stillPending = this.rows.some(item => item.todoKey === row.todoKey)
        if (reliableRefresh && !stillPending && failure.errorKind === 'CONFLICT') {
          this.approvalAnnouncement = `${row.businessNo || row.title || '当前单据'}刷新后已不在待办中，服务端状态显示已处理。`
          this.$message.info(this.approvalAnnouncement)
          this.quickSubmitting = false
          this.closeQuickApproval()
          return
        }
        if (reliableRefresh && stillPending && failure.errorKind === 'UNKNOWN') {
          this.quickRetryAvailable = true
          this.quickError = '审批结果暂时未知；该事项仍在待办中，可使用原请求号安全重试。'
        } else if (reliableRefresh && !stillPending && failure.errorKind === 'UNKNOWN') {
          this.quickError = '审批结果暂时未知；刷新后当前页未找到该事项，未按成功处理。请再次刷新或在业务审批记录中确认。'
        }
      } finally {
        this.quickSubmitting = false
      }
    },
    isTodoRoutePath() {
      const route = this.$route || {}
      return route.name === 'UnifiedTodoCenter' || route.path === '/workbench/todo'
    },
    isTodoRouteActive() {
      return !this.todoDisposed && this.todoActive && this.isTodoRoutePath()
    },
    suspendTodoActivity() {
      if (!this.todoActive) return
      this.todoActive = false
      this.routeOperationSequence += 1
      this.pageRequestSequence += 1
      this.pendingRouteOperation = null
      this.localRouteAttempts = []
    },
    resumeTodoActivity() {
      if (this.todoDisposed || this.todoActive || !this.isTodoRoutePath()) return Promise.resolve()
      this.todoActive = true
      this.contextSnapshot = getSelectedDeptContext()
      this.applyRouteQuery(this.$route.query || {})
      return this.syncRouteAndLoad().then(() => this.restoreTodoActionReturn())
    },
    sourceNames(sources) {
      return sources.map(this.sourceLabel).join('、')
    },
    sourceLabel(source) {
      return { approval: '统一审批', inventory: '库存', oa: 'OA', system: '系统' }[source] || '未知来源'
    },
    failureDiagnostic(error) {
      const source = error && typeof error === 'object' ? error : {}
      const message = source.message && source.message !== 'Todo provider request failed'
        ? String(source.message)
        : '该来源接口暂不可用'
      const status = source.status || (typeof source.code === 'number' ? source.code : '')
      const code = typeof source.code === 'string' ? source.code : ''
      const requestId = source.requestId ? String(source.requestId) : ''
      return {
        message,
        status,
        code,
        requestId,
        detail: [
          status ? `状态码：${status}` : '',
          code ? `错误码：${code}` : '',
          requestId ? `请求标识：${requestId}` : ''
        ].filter(Boolean).join(' · ')
      }
    },
    providerLastSuccessText(item) {
      const value = item && item.lastSuccessAt
      if (!value) return '最近成功：尚无成功记录'
      const date = new Date(value)
      if (!Number.isFinite(date.getTime())) return '最近成功：时间未知'
      const pad = number => String(number).padStart(2, '0')
      return `最近成功：${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    },
    categoryLabel(category) {
      return { approval: '待审批', execution: '待执行', returned: '退回修改', risk: '风险', personal: '个人' }[category] || '待处理'
    },
    processLabel(row) {
      const source = row || {}
      const todoType = String(source.todoType || source.type || '').toUpperCase()
      if (source.category === 'approval') return '查看审批'
      if (source.category === 'returned') return '修改并提交'
      if (source.category === 'risk') return '查看风险'
      if (source.category === 'personal') return '查看事项'
      if (source.category === 'execution') {
        if (todoType.includes('DELIVER')) return '执行发货'
        if (todoType.includes('RECEIVE')) return '确认收货'
        if (todoType.includes('QC') || todoType.includes('QUALITY')) return '执行质检'
        return '立即处理'
      }
      return '查看详情'
    },
    filterQueryOptions() {
      return {
        hasCurrentOrgContext: this.hasCurrentOrgContext,
        includePagination: true
      }
    },
    normalizeFilters(filters = this.filters) {
      return normalizeTodoFilterQuery({
        category: filters.category,
        source: filters.source,
        scope: filters.scopeMode,
        keyword: filters.keyword,
        priority: filters.priority,
        pageNum: filters.pageNum,
        pageSize: filters.pageSize
      }, this.filterQueryOptions())
    },
    applyRouteQuery(query) {
      const next = normalizeTodoFilterQuery(query || {}, this.filterQueryOptions())
      Object.assign(this.filters, next)
      this.keywordDraft = next.keyword
      return next
    },
    removeLocalRouteAttempt(attempt) {
      const index = this.localRouteAttempts.indexOf(attempt)
      if (index >= 0) this.localRouteAttempts.splice(index, 1)
    },
    scheduleLocalRouteAttemptCleanup(attempt) {
      const cleanup = () => this.removeLocalRouteAttempt(attempt)
      if (typeof this.$nextTick === 'function') {
        this.$nextTick(cleanup)
      } else {
        Promise.resolve().then(cleanup)
      }
    },
    routeQueryMatchesExpected(query, expectedQuery) {
      if (!query || typeof query !== 'object' || Array.isArray(query)) return false
      const queryKeys = Object.keys(query).sort()
      const expectedKeys = Object.keys(expectedQuery).sort()
      if (queryKeys.length !== expectedKeys.length) return false
      return queryKeys.every((key, index) =>
        key === expectedKeys[index] && query[key] === expectedQuery[key]
      )
    },
    findLocalRouteAttempt(query) {
      for (let index = this.localRouteAttempts.length - 1; index >= 0; index -= 1) {
        const attempt = this.localRouteAttempts[index]
        if (this.routeQueryMatchesExpected(query || {}, attempt.expectedQuery)) return attempt
      }
      return null
    },
    finishRouteOperation(operation, attempt) {
      if (!this.isTodoRouteActive()) return { active: false, landed: false }
      this.scheduleLocalRouteAttemptCleanup(attempt)
      if (operation.sequence !== this.routeOperationSequence) {
        return { active: false, landed: false }
      }
      if (this.pendingRouteOperation && this.pendingRouteOperation.sequence === operation.sequence) {
        this.pendingRouteOperation = null
      }
      const landed = this.routeQueryMatchesExpected(this.$route.query || {}, operation.expectedQuery)
      if (!landed) this.applyRouteQuery(this.$route.query || {})
      return { active: true, landed }
    },
    syncRouteQuery() {
      if (!this.isTodoRouteActive()) return Promise.resolve({ active: false, landed: false })
      const options = this.filterQueryOptions()
      const filters = this.normalizeFilters()
      Object.assign(this.filters, filters)
      const expectedQuery = buildTodoFilterQuery(filters, options)
      const operation = {
        sequence: ++this.routeOperationSequence,
        filters: { ...filters },
        options: { ...options },
        expectedQuery: { ...expectedQuery }
      }
      this.pendingRouteOperation = operation
      if (todoRouteQueryMatches(this.$route.query || {}, filters, options)) {
        this.pendingRouteOperation = null
        return Promise.resolve({ active: true, landed: true })
      }
      const location = {
        path: '/workbench/todo',
        query: { ...expectedQuery }
      }
      const attempt = {
        sequence: operation.sequence,
        filters: operation.filters,
        options: operation.options,
        expectedQuery: operation.expectedQuery
      }
      this.localRouteAttempts.push(attempt)
      let navigation
      try {
        navigation = this.$router.replace(location)
      } catch (error) {
        return Promise.resolve(this.finishRouteOperation(operation, attempt))
      }
      return Promise.resolve(navigation).then(
        () => this.finishRouteOperation(operation, attempt),
        () => this.finishRouteOperation(operation, attempt)
      )
    },
    syncRouteAndLoad() {
      return this.syncRouteQuery().then(result => {
        if (!result.active || !this.isTodoRouteActive()) return undefined
        return this.loadPage()
      })
    },
    handleRouteQuery(query) {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      const localAttempt = this.findLocalRouteAttempt(query)
      if (localAttempt) {
        this.removeLocalRouteAttempt(localAttempt)
        if (localAttempt.sequence === this.routeOperationSequence || this.pendingRouteOperation) {
          return Promise.resolve()
        }
        return this.syncRouteQuery().then(result => {
          if (result.active && !result.landed) return this.loadPage()
          return undefined
        })
      }
      const options = this.filterQueryOptions()
      const next = normalizeTodoFilterQuery(query || {}, options)
      const changed = !todoFilterStateEquals(next, this.filters, options)
      if (changed) {
        this.clearBatchSelection()
        this.pageRequestSequence += 1
        Object.assign(this.filters, next)
      }
      return this.syncRouteQuery().then(result => {
        if (changed && result.active) return this.loadPage()
        return undefined
      })
    },
    runFilterQuery() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.clearBatchSelection()
      const next = this.normalizeFilters({ ...this.filters, pageNum: 1 })
      this.pageRequestSequence += 1
      Object.assign(this.filters, next)
      return this.syncRouteAndLoad()
    },
    clearFilters() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.clearBatchSelection()
      this.pageRequestSequence += 1
      Object.assign(this.filters, normalizeTodoFilterQuery({}, this.filterQueryOptions()))
      this.keywordDraft = this.filters.keyword
      return this.syncRouteAndLoad()
    },
    removeActiveFilter(key) {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      const defaults = normalizeTodoFilterQuery({}, this.filterQueryOptions())
      if (key === 'category') this.filters.category = defaults.category
      if (key === 'source') this.filters.source = defaults.source
      if (key === 'keyword') {
        this.filters.keyword = defaults.keyword
        this.keywordDraft = defaults.keyword
      }
      if (key === 'priority') this.filters.priority = defaults.priority
      return this.runFilterQuery()
    },
    handleDeptChanged() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.clearBatchSelection()
      this.contextSnapshot = getSelectedDeptContext()
      const next = this.normalizeFilters({ ...this.filters, pageNum: 1 })
      this.pageRequestSequence += 1
      Object.assign(this.filters, next)
      return this.syncRouteAndLoad()
    },
    priorityLabel(priority) {
      return { urgent: '紧急', important: '重要', normal: '普通' }[priority] || '普通'
    },
    priorityType(priority) {
      return priority === 'urgent' ? 'danger' : priority === 'important' ? 'warning' : 'info'
    },
    waitingText(waitingSeconds, createdTime) {
      let seconds = Number(waitingSeconds)
      if (!Number.isFinite(seconds) && createdTime) {
        seconds = Math.max(0, (Date.now() - new Date(createdTime).getTime()) / 1000)
      }
      if (!Number.isFinite(seconds)) return '未知'
      if (seconds >= 86400) return `${Math.floor(seconds / 86400)} 天`
      if (seconds >= 3600) return `${Math.floor(seconds / 3600)} 小时`
      return `${Math.max(1, Math.floor(seconds / 60))} 分钟`
    },
    handleFilterChange() {
      return this.runFilterQuery()
    },
    handleQuery() {
      this.filters.keyword = String(this.keywordDraft || '').trim()
      return this.runFilterQuery()
    },
    handlePriorityChange() {
      return this.runFilterQuery()
    },
    handlePagination() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.clearBatchSelection()
      this.pageRequestSequence += 1
      Object.assign(this.filters, this.normalizeFilters())
      return this.syncRouteAndLoad()
    },
    goUnknownPage(offset) {
      if ((offset < 0 && !this.canPreviousUnknownPage) || (offset > 0 && !this.canNextUnknownPage)) return
      this.filters.pageNum = Math.max(1, this.filters.pageNum + offset)
      return this.handlePagination()
    },
    async manualRefresh() {
      if (!this.isTodoRouteActive()) return
      this.clearBatchSelection()
      await Promise.allSettled([
        this.$store.dispatch('todo/refreshSummaries'),
        this.loadPage()
      ])
    },
    retryProvider(source) {
      if (!this.isTodoRouteActive() || this.retryingProviders[source]) return Promise.resolve()
      this.clearBatchSelection()
      this.$set(this.retryingProviders, source, true)
      return Promise.allSettled([
        this.$store.dispatch('todo/refreshProviderSummary', source),
        this.loadPage({ networkSources: [source] })
      ]).then(results => ({ source, results }))
        .finally(() => { this.$set(this.retryingProviders, source, false) })
    },
    async loadPage(options = {}) {
      if (!this.isTodoRouteActive()) return { discarded: true }
      const requestSequence = ++this.pageRequestSequence
      this.pageError = ''
      const pageQuery = {
        providers: this.selectedProviders,
        businessSource: this.filters.source === 'all' ? '' : this.filters.source,
        category: this.filters.category === 'all' ? '' : this.filters.category,
        scopeMode: this.filters.scopeMode,
        keyword: this.filters.keyword,
        priority: this.filters.priority === 'all' ? '' : this.filters.priority,
        pageNum: this.filters.pageNum,
        pageSize: this.filters.pageSize
      }
      if (Array.isArray(options.networkSources)) {
        pageQuery.networkSources = options.networkSources.slice()
      }
      try {
        const result = await this.$store.dispatch('todo/refreshPage', pageQuery)
        if (!this.isTodoRouteActive() || requestSequence !== this.pageRequestSequence) return { discarded: true }
        if (result.discarded) return result
        this.rows = result.rows || []
        this.total = result.total === null ? null : Number(result.total || 0)
        this.estimatedTotal = Number(result.estimatedTotal || this.rows.length)
        this.staleSources = result.staleSources || []
        this.unknownSources = result.unknownSources || []
        this.failures = result.failures || []
        this.synchronizeBatchSelection()
        return result
      } catch (error) {
        if (!this.isTodoRouteActive() || requestSequence !== this.pageRequestSequence) return { discarded: true }
        this.pageError = '待办加载失败，请稍后重试'
        return { error }
      }
    },
    ensureValidKnownPage() {
      if (this.total === null || this.rows.length || this.filters.pageNum <= 1) return Promise.resolve(false)
      const lastPage = Math.max(1, Math.ceil(Math.max(0, Number(this.total) || 0) / this.filters.pageSize))
      if (lastPage >= this.filters.pageNum) return Promise.resolve(false)
      this.filters.pageNum = lastPage
      return this.syncRouteAndLoad().then(() => true)
    },
    nextTodoRow(row) {
      if (!row || !Array.isArray(this.rows) || !this.rows.length) return null
      const index = this.rows.findIndex(item => item && item.todoKey === row.todoKey)
      if (index < 0) return this.rows[0] || null
      return this.rows[index + 1] || this.rows[index - 1] || null
    },
    currentTodoScrollTop() {
      if (typeof window === 'undefined') return 0
      return Math.max(0, Number(window.scrollY || window.pageYOffset) || 0)
    },
    restoreTodoActionReturn() {
      if (!this.isTodoRouteActive()) return Promise.resolve({ restored: false })
      const result = consumeTodoActionReturn()
      if (!result.ok || !result.context) return Promise.resolve({ restored: false, reason: result.reason })
      const context = result.context
      return Promise.resolve(this.$nextTick()).then(() => {
        if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
          window.scrollTo({ top: context.scrollTop, behavior: 'auto' })
        }
        if (typeof document === 'undefined' || typeof document.querySelectorAll !== 'function') {
          return { restored: true, focused: false }
        }
        const availableKeys = new Set(this.rows.map(item => item.todoKey))
        const preferredKey = availableKeys.has(context.nextTodoKey)
          ? context.nextTodoKey
          : availableKeys.has(context.todoKey)
            ? context.todoKey
            : this.rows[0] && this.rows[0].todoKey
        const article = Array.from(document.querySelectorAll('.todo-row[data-todo-key]'))
          .find(element => element.dataset && element.dataset.todoKey === preferredKey)
        const target = article && (article.querySelector('.todo-quick-approve') || article.querySelector('.todo-row-action'))
        if (target && typeof target.focus === 'function') {
          try { target.focus({ preventScroll: true }) } catch (error) { target.focus() }
          return { restored: true, focused: true, todoKey: preferredKey }
        }
        return { restored: true, focused: false, todoKey: preferredKey }
      })
    },
    handleTodoAction(row) {
      if (this.isQuickApprovable(row)) return this.openQuickApproval(row, 'detail')
      return this.openTodo(row)
    },
    openTodo(row) {
      const nextRow = this.nextTodoRow(row)
      const returnContext = beginTodoActionReturn({
        returnRoute: this.$route,
        todoKey: row && row.todoKey,
        nextTodoKey: nextRow && nextRow.todoKey,
        todoType: row && (row.todoType || row.type),
        businessId: row && (row.businessId || row.routeParams && row.routeParams.businessId),
        scrollTop: this.currentTodoScrollTop()
      })
      this.suspendTodoActivity()
      const navigation = navigateTodo(row, {
        platform: 'desktop',
        getPermissions: () => this.permissions,
        getAvailableRouteSet: () => this.availableRouteSet,
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
        refreshSummaries: () => this.$store.dispatch('todo/refreshSummaries')
      })
      return Promise.resolve(navigation).then(result => {
        if (result && result.ok === false) {
          if (returnContext.ok) clearTodoActionReturn()
          return this.isTodoRoutePath()
            ? this.resumeTodoActivity().then(() => result)
            : result
        }
        return result
      }, error => {
        if (returnContext.ok) clearTodoActionReturn()
        return this.resumeTodoActivity().then(() => { throw error })
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.unified-todo-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-filter-card,
.todo-list-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.todo-page-header,
.todo-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.section-eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.todo-page-header h2 {
  margin: 4px 0;
  color: #0f172a;
  font-size: 22px;
}

.todo-page-header p,
.scope-description,
.todo-list-header small {
  color: #64748b;
  font-size: 13px;
}

.category-tabs {
  margin-top: -4px;
}

.todo-filter-form {
  padding: 12px 14px 0;
  border-radius: 10px;
  background: #f8fafc;
}

.todo-filter-grid {
  display: grid;
  grid-template-columns: minmax(160px, .55fr) minmax(360px, 1.45fr);
  gap: 14px 18px;
  align-items: end;
}

.todo-filter-form ::v-deep .todo-filter-group {
  min-width: 0;
  margin: 0 0 12px;
}

.todo-filter-form ::v-deep .todo-filter-group .el-form-item__label {
  float: none;
  display: block;
  padding: 0 0 6px;
  color: #475569;
  line-height: 20px;
  text-align: left;
}

.todo-filter-form ::v-deep .todo-filter-group .el-form-item__content {
  display: block;
  min-width: 0;
  line-height: 32px;
}

.todo-source-group ::v-deep .el-select {
  width: 100%;
}

.todo-keyword-control {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.active-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 10px 14px 0;
  color: #475569;
  font-size: 12px;
}

.active-filter-bar > .el-button { margin-left: auto; }

.scope-help {
  margin: 8px 14px 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.provider-alert {
  border-radius: 10px;
}

.provider-diagnostics {
  border: 1px solid #fed7aa;
  border-radius: 10px;
  background: #fffbeb;
}

.provider-diagnostics__header > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.provider-diagnostics__header strong { color: #92400e; }
.provider-diagnostics__header span {
  color: #a16207;
  font-size: 12px;
}

.provider-diagnostic-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 0;
  border-top: 1px solid #fde7b2;
}

.provider-diagnostic-row:first-child {
  padding-top: 0;
  border-top: 0;
}

.provider-diagnostic-row:last-child { padding-bottom: 0; }

.provider-diagnostic-row__copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.provider-diagnostic-row__copy strong { color: #78350f; }
.provider-diagnostic-row__copy span,
.provider-diagnostic-row__copy small {
  color: #92400e;
  font-size: 12px;
}

.todo-list-header > div {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

.todo-sort-label {
  color: #475569 !important;
  font-weight: 600;
}

.todo-priority-filter,
.todo-batch-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 6px;
  color: #475569;
  font-size: 13px;
}

.todo-batch-toolbar {
  margin-bottom: 4px;
  padding: 10px 12px;
  border: 1px solid #bae6d3;
  border-radius: 8px;
  background: #f0fdf7;
}

.todo-batch-toolbar__spacer { flex: 1; }

.todo-list-body {
  min-height: 180px;
}

.todo-row {
  border-bottom: 1px solid #edf2f7;
}

.todo-row-layout {
  display: flex;
  align-items: center;
  gap: 10px;
}

.todo-batch-selector {
  flex: 0 0 auto;
  margin-left: 6px;
}

.todo-row-action {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 14px;
  padding: 16px 6px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background .15s;
}

.todo-quick-approve {
  flex: 0 0 auto;
  margin-right: 6px;
}

.todo-live-status {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.todo-row-action:hover { background: var(--erp-surface-muted, #f8f8f5); }
.todo-row-action:focus-visible {
  outline: 2px solid var(--erp-primary, #0b6b53);
  outline-offset: 2px;
  border-radius: 8px;
}
.todo-row.stale { background: #fffbeb; }

.priority-mark {
  width: 4px;
  height: 42px;
  flex: 0 0 4px;
  border-radius: 4px;
  background: #64748b;
}

.priority-mark.urgent { background: #ef4444; }
.priority-mark.important { background: #f59e0b; }

.todo-main {
  min-width: 0;
  flex: 1;
}

.todo-title-row,
.todo-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-title-row strong {
  overflow: hidden;
  color: #1e293b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-summary {
  display: block;
  margin: 6px 0;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-meta {
  flex-wrap: wrap;
  color: #64748b;
  font-size: 12px;
}

.todo-meta i { margin-right: 3px; }

.todo-process-label {
  flex: 0 0 auto;
  padding: 7px 15px;
  border: 1px solid var(--erp-primary, #0b6b53);
  border-radius: 4px;
  color: var(--erp-primary-strong, #075441);
}

.todo-empty {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #64748b;
}

.todo-empty i { font-size: 30px; }
.todo-empty strong { color: #475569; }
.todo-empty.unknown i { color: #f59e0b; }

.unknown-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 0 2px;
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 900px) {
  .todo-row-action { align-items: flex-start; }
  .todo-meta { flex-direction: column; align-items: flex-start; gap: 3px; }
  .todo-row-layout { align-items: flex-start; flex-wrap: wrap; }
  .todo-quick-approve { margin: 0 6px 8px auto; }
}

@media (max-width: 820px) {
  .todo-filter-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .todo-keyword-group {
    grid-column: auto;
  }
}
</style>
