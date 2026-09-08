<template>
  <div class="mobile-todo-page mobile-system-page">
    <main class="todo-shell" aria-label="手机待办中心">
      <header class="todo-header">
        <button class="back-button" type="button" aria-label="返回" @click="goBack">
          <i class="el-icon-arrow-left"></i>
        </button>
        <div>
          <span>统一工作台</span>
          <h1>我的待办</h1>
        </div>
        <button class="refresh-button" type="button" aria-label="刷新待办" :disabled="pageLoading || !!pendingRouteOperation" @click="manualRefresh">
          <i class="el-icon-refresh" :class="{ spinning: pageLoading }"></i>
        </button>
      </header>

      <section class="summary-card" aria-label="待办分类统计">
        <button
          v-for="item in summaryItems"
          :key="item.value"
          type="button"
          class="summary-stat"
          :class="{ active: filters.category === item.value }"
          :aria-pressed="filters.category === item.value ? 'true' : 'false'"
          @click="selectCategory(item.value)"
        >
          <small>{{ item.label }}</small>
          <strong>{{ item.count }}</strong>
        </button>
        <div class="summary-scope">
          <span>{{ executionScopeDescription }}</span>
          <span>{{ approvalScopeDescription }}</span>
        </div>
      </section>

      <section class="filter-card">
        <div class="filter-row">
          <select v-model="filters.source" aria-label="待办来源" @change="handleFilterChange">
            <option v-for="item in sources" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <form class="keyword-row" @submit.prevent="handleQuery">
          <i class="el-icon-search"></i>
          <input v-model.trim="keywordDraft" type="search" aria-label="待办关键词" placeholder="搜索标题、单号或摘要" />
          <button type="submit">查询</button>
        </form>
        <div v-if="hasActiveFilters" class="active-filters" aria-label="当前生效的筛选条件">
          <button
            v-for="item in activeFilterItems"
            :key="item.key"
            type="button"
            class="active-filter-chip"
            :aria-label="`移除筛选：${item.label}`"
            @click="removeActiveFilter(item.key)"
          >{{ item.label }}<i class="el-icon-close" aria-hidden="true"></i></button>
          <button type="button" class="reset-filters" @click="clearFilters">重置全部</button>
        </div>
        <p class="scope-help">{{ summaryScopeHelp }}</p>
      </section>

      <section class="priority-filter" aria-label="待办优先级">
        <span class="priority-filter-label">优先级</span>
        <div class="priority-filter-track" role="group" aria-label="待办优先级">
          <button
            v-for="item in priorities"
            :key="item.value"
            type="button"
            class="priority-filter-button"
            :class="{ active: filters.priority === item.value }"
            :aria-pressed="filters.priority === item.value ? 'true' : 'false'"
            @click="selectPriority(item.value)"
          >{{ item.label }}</button>
        </div>
      </section>

      <section v-if="statusMessage" class="state-banner warning" role="status">
        <i class="el-icon-warning-outline"></i>
        <span>{{ statusMessage }}</span>
      </section>
      <section v-if="pageError" class="state-banner error" role="alert">
        <i class="el-icon-circle-close"></i>
        <span>{{ pageError }}</span>
        <button type="button" :disabled="!!pendingRouteOperation" @click="retryPage">重试</button>
      </section>

      <section class="list-heading">
        <div>
          <h2>待办事项</h2>
          <small v-if="total === null">已加载 {{ rows.length }} 条 · 总数未知</small>
          <small v-else>共 {{ total }} 条</small>
          <small class="todo-sort-label">{{ sortLabel }}</small>
        </div>
        <span>{{ scopeDescription }}</span>
      </section>

      <section v-if="batchApproveEnabled && batchSelectableRows.length" class="mobile-todo-batch-toolbar" aria-label="批量审批工具栏">
        <div>
          <strong>已选 {{ selectedBatchRows.length }} 条</strong>
          <small>当前已加载事项，单次最多 20 条</small>
        </div>
        <button type="button" :disabled="batchExecuting" @click="toggleAllBatchRows(!allBatchRowsSelected)">
          {{ allBatchRowsSelected ? '取消全选' : '选择前20条' }}
        </button>
        <button type="button" :disabled="!selectedBatchRows.length || batchExecuting" class="primary" @click="openBatchApproval">
          批量通过
        </button>
      </section>

      <p class="mobile-todo-sr-only" aria-live="polite">{{ approvalAnnouncement }}</p>

      <section class="todo-list" aria-live="polite">
        <article
          v-for="row in rows"
          :key="row.todoKey"
          class="todo-card"
          :class="[row.priority || 'normal', { stale: staleSources.includes(row.provider || row.source) }]"
        >
          <button
            type="button"
            class="todo-card-action"
            :aria-label="`${row.title || row.businessNo || '待处理事项'}，${categoryLabel(row.category)}，${processLabel(row)}`"
            @click="openTodo(row)"
          >
            <span class="todo-card-head">
              <span class="category-label">{{ categoryLabel(row.category) }}</span>
              <span v-if="staleSources.includes(row.provider || row.source)" class="cache-label">缓存</span>
              <small>{{ sourceLabel(row.source) }}</small>
            </span>
            <span class="todo-card-title">{{ row.title || row.businessNo || '待处理事项' }}</span>
            <span class="todo-card-summary">{{ row.summary || row.businessNo || '请进入业务页面查看详情' }}</span>
            <span class="todo-meta">
              <span><i class="el-icon-office-building"></i>{{ row.contextDeptName || row.deptName || '个人事项' }}</span>
              <span><i class="el-icon-time"></i>{{ waitingText(row.waitingSeconds, row.createdTime) }}</span>
            </span>
            <span class="todo-process-label">{{ processLabel(row) }}</span>
          </button>
          <div v-if="isQuickApprovable(row) || isBatchApprovable(row)" class="mobile-todo-approval-actions">
            <label v-if="isBatchApprovable(row)" class="mobile-todo-batch-select">
              <input
                type="checkbox"
                :checked="isBatchSelected(row)"
                :disabled="batchExecuting"
                :aria-label="`选择${row.businessNo || row.title || '当前调拨审批'}`"
                @change="toggleBatchRow(row, $event.target.checked)"
              />
              <span>选择</span>
            </label>
            <button
              v-if="isQuickApprovable(row)"
              type="button"
              class="mobile-todo-quick-approve"
              :disabled="quickSubmitting || batchExecuting || batchDialogVisible"
              @click="openQuickApproval(row)"
            >{{ quickSubmitting && quickRow && quickRow.todoKey === row.todoKey ? '提交中…' : '直接通过' }}</button>
          </div>
        </article>

        <div v-if="!pageLoading && rows.length === 0" class="empty-card">
          <i :class="total === null ? 'el-icon-warning-outline' : 'el-icon-circle-check'"></i>
          <strong>{{ total === null ? '部分数据未知' : '暂无待办' }}</strong>
          <span>{{ emptyStateDescription }}</span>
          <button v-if="hasActiveFilters" class="empty-reset" type="button" @click="clearFilters">清除筛选</button>
        </div>
      </section>

      <button
        v-if="canLoadMore"
        class="load-more"
        type="button"
        :disabled="pageLoading || loadingMore"
        @click="loadMore"
      >{{ pageLoading || loadingMore ? '加载中…' : total === null ? '继续加载（总数未知）' : '加载更多' }}</button>
      <p v-else-if="rows.length" class="list-end">{{ total === null ? '当前返回不足一页，已停止继续加载' : '已加载全部待办' }}</p>
    </main>

    <mobile-todo-quick-approve-sheet
      :visible="quickDialogVisible"
      :row="quickRow || {}"
      :preview="quickPreview"
      :loading="quickPreviewLoading"
      :submitting="quickSubmitting"
      :error-message="quickError"
      :eligible="quickRow ? isCurrentQuickApprovalEligible(quickRow) : false"
      :next-available="quickNextAvailable"
      :retry-available="quickRetryAvailable"
      @close="closeQuickApproval"
      @confirm="submitQuickApproval"
      @retry="retryQuickApproval"
    />

    <mobile-todo-batch-approve-sheet
      :visible="batchDialogVisible"
      :items="batchItems"
      :executing="batchExecuting"
      :processed="batchProcessed"
      :total="batchTotal"
      :results="batchResults"
      :error-message="batchError"
      @close="closeBatchApproval"
      @confirm="submitBatchApproval"
      @retry="retryFailedBatchApproval"
      @stop="stopBatchApproval"
    />
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { constantRoutes } from '@/router'
import defaultSettings from '@/settings'
import { approveApprovalTask } from '@/api/approval/task'
import { approveTransfer, getTransferDetail } from '@/api/inventory/transfer'
import { buildAvailableRouteSet } from '@/utils/todoRouteResolver'
import { navigateTodo } from '@/utils/todoNavigator'
import { aggregateSummaries, TODO_SORT_LABEL } from '@/utils/todoAggregator'
import {
  canBatchApproveTodo,
  canQuickApproveTodo,
  classifyTodoApprovalError,
  createBatchApprovalItems,
  createTodoApprovalRequestId,
  executeTodoApproval,
  executeTodoApprovalBatch,
  loadTodoApprovalPreview,
  resolveTodoApprovalTarget,
  snapshotApprovalRow
} from '@/utils/todoApprovalActions'
import { fetchTodoSummary } from '@/api/workbench/todo'
import { beginTodoContextLease, formatTodoContextSwitchNotice, rollbackTodoContextLease } from '@/utils/todoContextLease'
import { clearSelectedDept, getSelectedDeptContext, hasValidInventoryDeptContext, setSelectedDept } from '@/utils/shopContext'
import { buildTodoFilterQuery, hasActiveTodoFilters, normalizeTodoFilterQuery, todoFilterStateEquals, todoRouteQueryMatches } from '@/utils/todoFilterQuery'
import MobileTodoBatchApproveSheet from './components/MobileTodoBatchApproveSheet'
import MobileTodoQuickApproveSheet from './components/MobileTodoQuickApproveSheet'

const BUSINESS_SOURCE_VALUES = ['inventory', 'oa', 'system']
const PROVIDER_VALUES = ['approval', 'inventory', 'oa', 'system']

function providersForBusinessSource(source) {
  return source === 'all' || !BUSINESS_SOURCE_VALUES.includes(source)
    ? PROVIDER_VALUES.slice()
    : ['approval', source]
}

function createScopedProviderState() {
  return { summary: null, error: null, stale: false, unknown: false, status: 'pending' }
}

function createScopedProviderStates() {
  return PROVIDER_VALUES.reduce((states, source) => {
    states[source] = createScopedProviderState()
    return states
  }, {})
}

function unwrapSummaryResponse(response) {
  let value = response
  if (value && value.data && typeof value.data === 'object' && !Array.isArray(value.data)) {
    value = value.data
    if (value.data && typeof value.data === 'object' && !Array.isArray(value.data) &&
      value.approval === undefined && value.counts === undefined) {
      value = value.data
    }
  }
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function displaySelectedCount(value, { unknown = false, pending = false } = {}) {
  const number = Number(value)
  const count = Number.isFinite(number) ? Math.max(0, number) : 0
  if (unknown) return count > 0 ? `${count}+` : '?'
  if (pending) return count > 0 ? `${count}+` : '…'
  return count
}

function normalizeMobileReturnPath(value) {
  const path = Array.isArray(value) ? value[0] : value
  if (typeof path !== 'string' || !path.startsWith('/mobile/') || path.startsWith('/mobile/todo')) return ''
  return path
}

export default {
  name: 'MobileUnifiedTodoCenter',
  components: { MobileTodoBatchApproveSheet, MobileTodoQuickApproveSheet },
  data() {
    const contextSnapshot = getSelectedDeptContext()
    const filters = normalizeTodoFilterQuery({}, {
      hasCurrentOrgContext: hasValidInventoryDeptContext(contextSnapshot),
      includePagination: false
    })
    return {
      contextSnapshot,
      returnPath: normalizeMobileReturnPath(this.$route && this.$route.query && this.$route.query.returnTo),
      keywordDraft: filters.keyword,
      categories: [
        { label: '全部', value: "all" },
        { label: '审批', value: "approval" },
        { label: '执行', value: "execution" },
        { label: '退回修改', value: "returned" },
        { label: '风险', value: "risk" },
        { label: '个人', value: "personal" }
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
      sortLabel: TODO_SORT_LABEL,
      rows: [],
      total: 0,
      estimatedTotal: 0,
      pageNum: 1,
      pageSize: 10,
      lastPageSize: 0,
      staleSources: [],
      unknownSources: [],
      failures: [],
      pageError: '',
      loadingMore: false,
      scopedProviderStates: createScopedProviderStates(),
      scopedSummaryKey: '',
      scopedSummarySequence: 0,
      scopedSummaryPromise: null,
      requestVersion: 0,
      isDestroyed: false,
      todoActive: true,
      routeOperationSequence: 0,
      pendingRouteOperation: null,
      localRouteAttempts: [],
      quickApproveEnabled: defaultSettings.todoQuickApproveEnabled === true,
      quickDialogVisible: false,
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
      permissions: 'permissions',
      permissionRoutes: 'permission_routes'
    }),
    pageLoading() {
      return !!(this.$store.state.todo && this.$store.state.todo.pageLoading)
    },
    hasCurrentOrgContext() {
      return hasValidInventoryDeptContext(this.contextSnapshot)
    },
    hasActiveFilters() {
      return hasActiveTodoFilters(this.filters, this.filterQueryOptions())
    },
    selectedProviders() {
      return providersForBusinessSource(this.filters.source)
    },
    selectedCounts() {
      const providerStates = this.scopedProviderStates || {}
      const selectedProviders = Array.isArray(this.selectedProviders) ? this.selectedProviders : []
      const summaries = selectedProviders.reduce((results, source) => {
        const provider = providerStates[source]
        if (provider && provider.summary !== null && provider.summary !== undefined) {
          results.push({ source, summary: provider.summary })
        }
        return results
      }, [])
      return aggregateSummaries(summaries).counts
    },
    hasUnknownProvider() {
      return this.selectedProviders.some(source => {
        const provider = this.scopedProviderStates[source]
        return provider && (provider.unknown || provider.status === 'unknown')
      })
    },
    hasPendingProvider() {
      return this.selectedProviders.some(source => this.scopedProviderStates[source] && this.scopedProviderStates[source].status === 'pending')
    },
    todoTotalDisplay() {
      return displaySelectedCount(this.selectedCounts && this.selectedCounts.total, {
        unknown: this.hasUnknownProvider,
        pending: this.hasPendingProvider
      })
    },
    summaryItems() {
      return this.categories.map(item => ({
        ...item,
        count: item.value === 'all' ? this.todoTotalDisplay : this.countFor(item.value)
      }))
    },
    executionScopeDescription() {
      return this.hasCurrentOrgContext
        ? '本人专属待办跨已授权门店'
        : '本人专属待办正常显示'
    },
    approvalScopeDescription() {
      const deptName = this.contextSnapshot && this.contextSnapshot.deptName
      return this.hasCurrentOrgContext
        ? `公共待办：当前门店（${deptName || '未命名门店'}）`
        : '选择门店后显示公共待办'
    },
    summaryScopeHelp() {
      const scopeText = `${this.executionScopeDescription} · ${this.approvalScopeDescription}`
      const filteredByPriority = this.filters.priority !== 'all'
      if (this.filters.keyword && filteredByPriority) {
        return '上方数字为当前关键词和优先级在各分类中的结果；' + scopeText
      }
      if (this.filters.keyword) return '上方数字为当前关键词在各分类中的结果；' + scopeText
      if (filteredByPriority) return '上方数字为当前优先级在各分类中的结果；' + scopeText
      return '上方数字与当前来源和可处理范围一致；' + scopeText
    },
    scopeDescription() {
      return `${this.executionScopeDescription} · ${this.approvalScopeDescription}`
    },
    emptyStateDescription() {
      const priority = this.filters.priority === 'all'
        ? ''
        : `当前优先级为${this.priorityLabel(this.filters.priority)}；`
      return this.total === null
        ? `${priority}当前结果不完整，暂时无法确认是否没有待办。`
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
      const pending = this.selectedProviders.filter(source => this.scopedProviderStates[source] && this.scopedProviderStates[source].status === 'pending')
      const stale = Array.from(new Set([
        ...this.staleSources,
        ...this.selectedProviders.filter(source => this.scopedProviderStates[source] && this.scopedProviderStates[source].stale)
      ]))
      const unknown = Array.from(new Set([
        ...this.unknownSources,
        ...this.selectedProviders.filter(source => {
          const provider = this.scopedProviderStates[source]
          return provider && (provider.unknown || provider.status === 'unknown')
        })
      ]))
      const messages = []
      if (pending.length) messages.push(`正在加载：${this.sourceNames(pending)}`)
      if (unknown.length) messages.push(`数据未知：${this.sourceNames(unknown)}`)
      if (stale.length) messages.push(`缓存数据：${this.sourceNames(stale)}`)
      if (this.failures.length) messages.push('部分服务暂不可用')
      return messages.join('；')
    },
    canLoadMore() {
      if (this.pendingRouteOperation) return false
      if (this.pageLoading || this.loadingMore) return false
      if (this.total === null) return this.lastPageSize >= this.pageSize
      return this.rows.length < this.total
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
      const selectable = this.batchSelectableRows.slice(0, 20)
      return selectable.length > 0 && selectable.every(row => this.selectedTodoKeys.includes(row.todoKey))
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
    return this.syncRouteAndReset()
  },
  mounted() {
    this.isDestroyed = false
    window.addEventListener('erp:dept-changed', this.handleDeptChanged)
  },
  activated() {
    return this.resumeTodoActivity()
  },
  deactivated() {
    this.suspendTodoActivity()
  },
  beforeDestroy() {
    this.isDestroyed = true
    this.todoActive = false
    this.requestVersion += 1
    this.scopedSummarySequence += 1
    this.scopedSummaryPromise = null
    this.routeOperationSequence += 1
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
    approvalTargetsMatch(left, right) {
      const leftTarget = resolveTodoApprovalTarget(left)
      const rightTarget = resolveTodoApprovalTarget(right)
      return ['engine', 'transferId', 'taskId', 'contextDeptId']
        .every(key => leftTarget[key] === rightTarget[key])
    },
    currentQuickApprovalRow(row) {
      if (!row || !row.todoKey) return null
      const current = this.rows.find(item => item && item.todoKey === row.todoKey)
      if (!current || !this.isQuickApprovable(current) || !this.approvalTargetsMatch(row, current)) return null
      return current
    },
    isCurrentQuickApprovalEligible(row) {
      return !!this.currentQuickApprovalRow(row)
    },
    prepareBatchExecution(items) {
      const currentRows = new Map(this.rows.map(row => [row.todoKey, row]))
      const executableItems = []
      const validationResults = []
      ;(items || []).forEach(item => {
        const current = currentRows.get(item.todoKey)
        let message = ''
        if (!current) {
          message = '该事项已离开当前待办，未发送审批请求；请关闭后刷新确认。'
        } else if (!this.isBatchApprovable(current)) {
          message = '审批权限、候选关系或数据新鲜度已变化，未发送审批请求；请关闭后刷新。'
        } else if (!this.approvalTargetsMatch(item.row, current)) {
          message = '审批任务标识已变化，未复用旧请求号；请关闭后重新打开审批。'
        }
        if (message) {
          validationResults.push({
            ...item,
            status: 'FAILED',
            errorKind: 'VALIDATION',
            message
          })
          return
        }
        executableItems.push({
          ...item,
          row: snapshotApprovalRow(current),
          businessNo: current.businessNo || current.title || item.businessNo
        })
      })
      return { executableItems, validationResults }
    },
    mergeBatchExecutionResults(items, executionResults, validationResults) {
      const byRequestId = new Map(
        validationResults.concat(executionResults).map(item => [item.requestId, item])
      )
      return items.map(item => byRequestId.get(item.requestId) || {
        ...item,
        status: 'FAILED',
        errorKind: 'VALIDATION',
        message: '审批资格校验未完成，未发送请求；请关闭后刷新。'
      })
    },
    isBatchResultRetryable(item) {
      if (!item) return false
      if (item.status === 'CANCELED') return item.errorKind === 'USER_CANCELED'
      return item.status === 'FAILED' && !['SESSION', 'FORBIDDEN', 'VALIDATION'].includes(item.errorKind)
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
        this.$message.info('当前已加载的可审批事项超过20条，已选择前20条')
      }
    },
    clearBatchSelection() {
      if (!this.batchExecuting) this.selectedTodoKeys = []
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
      if (item && item.errorKind === 'VALIDATION' && item.message) return item.message
      return {
        SESSION: '登录状态已失效，请重新登录后确认结果',
        FORBIDDEN: '审批权限或候选关系已变化',
        CONFLICT: '任务状态已变化，可能已被其他人处理',
        UNKNOWN: '网络结果未知；请用原请求号重试或查看详情确认',
        USER_CANCELED: '用户已停止，尚未开始处理'
      }[item.errorKind] || '审批未成功，请刷新后重试'
    },
    reconcileBatchResults(results, authoritativeRefresh) {
      const remaining = new Set(this.rows.map(row => row.todoKey))
      return results.map(item => {
        if (item.status === 'SUCCESS') return { ...item, message: '审批通过' }
        const provider = item.row && (item.row.provider || item.row.source)
        const reliable = authoritativeRefresh === true &&
          !this.staleSources.includes(provider) && !this.unknownSources.includes(provider)
        if (item.status === 'FAILED' && item.errorKind === 'CONFLICT' && reliable && !remaining.has(item.todoKey)) {
          return { ...item, status: 'ALREADY_HANDLED', message: '接口报告任务冲突，刷新后该事项已不在当前账号待办中' }
        }
        if (item.status === 'FAILED' && item.errorKind === 'UNKNOWN' && reliable && !remaining.has(item.todoKey)) {
          return { ...item, message: '网络结果未知；刷新后当前已加载列表未找到该事项，未按成功处理' }
        }
        return { ...item, message: this.batchFailureMessage(item) }
      })
    },
    async runBatchApproval(items, comment, retry = false) {
      if (this.batchExecuting || !Array.isArray(items) || !items.length) return
      const { executableItems, validationResults } = this.prepareBatchExecution(items)
      this.batchExecuting = true
      this.batchStopRequested = false
      this.batchError = ''
      this.batchProcessed = validationResults.length
      this.batchTotal = items.length
      try {
        let executionResults = []
        let refreshState = { refreshed: false, authoritative: false }
        if (executableItems.length) {
          executionResults = await executeTodoApprovalBatch(executableItems, { approveApprovalTask, approveTransfer }, {
            comment,
            concurrency: 3,
            shouldStop: () => this.batchStopRequested,
            onProgress: (item, completed) => {
              this.batchProcessed = validationResults.length + completed
            }
          })
          await this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
          refreshState = await this.reloadLoadedTodoPages()
        }
        const results = this.mergeBatchExecutionResults(items, executionResults, validationResults)
        this.batchProcessed = items.length
        const successfulKeys = new Set(results.filter(item => item.status === 'SUCCESS').map(item => item.todoKey))
        // A successful mutation is authoritative even if a provider briefly serves
        // an older page snapshot. Never leave an already-approved row actionable.
        this.removeApprovedRows(successfulKeys)
        const reconciled = this.reconcileBatchResults(results, refreshState.authoritative)
        if (retry) {
          const replacements = new Map(reconciled.map(item => [item.requestId, item]))
          this.batchResults = this.batchResults.map(item => replacements.get(item.requestId) || item)
        } else {
          this.batchResults = reconciled
        }
        const failedKeys = new Set(this.batchResults
          .filter(item => this.isBatchResultRetryable(item))
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
      const items = this.batchResults.filter(item => this.isBatchResultRetryable(item))
      if (!items.length) {
        this.batchError = '当前结果不可直接重试，请关闭弹层并刷新待办后重新确认。'
        return
      }
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
    async openQuickApproval(row) {
      if (!this.isQuickApprovable(row) || this.quickSubmitting || this.batchExecuting || this.batchDialogVisible) return
      const sequence = ++this.quickRequestSequence
      this.quickRow = row
      this.quickPreview = null
      this.quickError = ''
      this.quickRetryAvailable = false
      this.quickRequestId = createTodoApprovalRequestId('MOBILETODOQ', row.todoKey || row.businessId)
      this.quickDialogVisible = true
      this.quickPreviewLoading = true
      try {
        const preview = await loadTodoApprovalPreview(row, { getTransferDetail })
        if (sequence !== this.quickRequestSequence || !this.quickDialogVisible ||
          !this.quickRow || this.quickRow.todoKey !== row.todoKey) return
        this.quickPreview = preview
      } catch (error) {
        if (sequence !== this.quickRequestSequence || !this.quickDialogVisible) return
        this.quickError = '详情已变化或暂时无法读取，请刷新待办后重试。'
        await this.reloadLoadedTodoPages()
      } finally {
        if (sequence === this.quickRequestSequence) this.quickPreviewLoading = false
      }
    },
    closeQuickApproval() {
      if (this.quickSubmitting) return
      this.quickRequestSequence += 1
      this.quickDialogVisible = false
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
      if (!row || this.quickSubmitting || !this.quickPreview) return
      const currentRow = this.currentQuickApprovalRow(row)
      if (!currentRow) {
        this.quickRetryAvailable = false
        this.quickError = '审批权限、候选关系或待办状态已变化，请关闭后刷新待办。'
        this.approvalAnnouncement = this.quickError
        return
      }
      const requestId = this.quickRequestId
      const nextRowBeforeSubmit = continueNext ? this.nextQuickApprovalRow(currentRow) : null
      const nextTodoKey = nextRowBeforeSubmit ? nextRowBeforeSubmit.todoKey : ''
      this.quickSubmitting = true
      this.quickError = ''
      this.quickRetryAvailable = false
      try {
        await executeTodoApproval(currentRow, { approveApprovalTask, approveTransfer }, {
          requestId,
          comment,
          suppressTodoMutationRefresh: true
        })
        this.approvalAnnouncement = `${row.businessNo || row.title || '当前单据'}审批通过。`
        this.$message.success(this.approvalAnnouncement)
        await this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
        await this.reloadLoadedTodoPages()
        // The mutation response is authoritative; remove the row even if the
        // immediately refreshed provider page still contains an older snapshot.
        this.removeApprovedRows(new Set([row.todoKey]))
        this.quickSubmitting = false
        if (continueNext) {
          const nextRow = this.rows.find(item => item.todoKey === nextTodoKey && this.isQuickApprovable(item)) ||
            this.rows.find(item => this.isQuickApprovable(item))
          if (nextRow) {
            await this.openQuickApproval(nextRow)
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
        const refreshState = await this.reloadLoadedTodoPages()
        const provider = row.provider || row.source
        const providerFresh = refreshState.refreshed &&
          !this.staleSources.includes(provider) && !this.unknownSources.includes(provider)
        const stillPending = this.rows.some(item => item.todoKey === row.todoKey)
        if (providerFresh && refreshState.authoritative && !stillPending && failure.errorKind === 'CONFLICT') {
          this.approvalAnnouncement = `${row.businessNo || row.title || '当前单据'}刷新后已不在待办中，服务端状态显示已处理。`
          this.$message.info(this.approvalAnnouncement)
          this.quickSubmitting = false
          this.closeQuickApproval()
          return
        }
        if (providerFresh && stillPending && failure.errorKind === 'UNKNOWN') {
          this.quickRetryAvailable = true
          this.quickError = '审批结果暂时未知；该事项仍在待办中，可使用原请求号安全重试。'
        } else if (providerFresh && refreshState.authoritative && !stillPending && failure.errorKind === 'UNKNOWN') {
          this.quickError = '审批结果暂时未知；完整刷新后未找到该事项，仍未按成功处理。请查看详情确认。'
        }
      } finally {
        this.quickSubmitting = false
      }
    },
    removeApprovedRows(todoKeys) {
      if (!todoKeys || typeof todoKeys.has !== 'function' || !todoKeys.size) return
      const before = this.rows.length
      this.rows = this.rows.filter(row => !todoKeys.has(row.todoKey))
      const removed = before - this.rows.length
      if (removed > 0 && this.total !== null) this.total = Math.max(0, Number(this.total || 0) - removed)
      if (removed > 0) this.estimatedTotal = Math.max(this.rows.length, Number(this.estimatedTotal || 0) - removed)
      this.synchronizeBatchSelection()
    },
    async reloadLoadedTodoPages() {
      if (!this.isTodoRouteActive()) return { refreshed: false, discarded: true }
      const targetPageCount = Math.max(1, Number(this.pageNum) || 1)
      const previous = {
        rows: this.rows.slice(),
        total: this.total,
        estimatedTotal: this.estimatedTotal,
        pageNum: this.pageNum,
        lastPageSize: this.lastPageSize,
        staleSources: this.staleSources.slice(),
        unknownSources: this.unknownSources.slice(),
        failures: this.failures.slice()
      }
      this.requestVersion += 1
      const requestVersion = this.requestVersion
      this.rows = []
      this.pageNum = 1
      this.total = 0
      this.estimatedTotal = 0
      this.lastPageSize = 0
      this.staleSources = []
      this.unknownSources = []
      this.failures = []
      this.loadingMore = false
      const summaryRequest = this.refreshScopedSummaries({ force: true })
      let loadedPages = 0
      let reachedEnd = false
      for (let page = 1; page <= targetPageCount; page += 1) {
        this.pageNum = page
        const result = await this.loadPage(page > 1, { requestVersion, requestedPage: page })
        if (!result || result.discarded) break
        loadedPages = page
        if (this.lastPageSize < this.pageSize || (this.total !== null && this.rows.length >= this.total)) {
          reachedEnd = true
          break
        }
      }
      await summaryRequest
      const loadedRequestedPages = loadedPages === targetPageCount
      if (!loadedPages || (!loadedRequestedPages && !reachedEnd)) {
        Object.assign(this, previous)
        return { refreshed: false, authoritative: false }
      }
      this.pageNum = loadedPages
      this.synchronizeBatchSelection()
      return { refreshed: true, authoritative: reachedEnd, loadedPages }
    },
    goBack() {
      const context = getSelectedDeptContext()
      const fallbackPath = context && context.isWarehouse
        ? "/mobile/warehouse"
        : context && context.isStore
          ? "/mobile/store"
          : "/mobile/mine"
      this.$router.replace(this.returnPath || fallbackPath).catch(() => {})
    },
    isTodoRoutePath() {
      const route = this.$route || {}
      return route.name === 'MobileUnifiedTodoCenter' || route.path === '/mobile/todo'
    },
    isTodoRouteActive() {
      return !this.isDestroyed && this.todoActive && this.isTodoRoutePath()
    },
    suspendTodoActivity() {
      if (!this.todoActive) return
      this.todoActive = false
      this.requestVersion += 1
      this.scopedSummarySequence += 1
      this.scopedSummaryPromise = null
      this.routeOperationSequence += 1
      this.pendingRouteOperation = null
      this.localRouteAttempts = []
      this.loadingMore = false
    },
    resumeTodoActivity() {
      if (this.isDestroyed || this.todoActive || !this.isTodoRoutePath()) return Promise.resolve()
      this.todoActive = true
      this.contextSnapshot = getSelectedDeptContext()
      this.applyRouteQuery(this.$route.query || {})
      return this.syncRouteAndReset()
    },
    countFor(category) {
      return displaySelectedCount(this.selectedCounts && this.selectedCounts[category], {
        unknown: this.hasUnknownProvider,
        pending: this.hasPendingProvider
      })
    },
    filterQueryOptions() {
      return {
        hasCurrentOrgContext: this.hasCurrentOrgContext,
        includePagination: false
      }
    },
    normalizeFilters(filters = this.filters) {
      return normalizeTodoFilterQuery({
        category: filters.category,
        source: filters.source,
        scope: filters.scopeMode,
        keyword: filters.keyword,
        priority: filters.priority
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
      if (!landed) {
        this.applyRouteQuery(this.$route.query || {})
      }
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
        path: '/mobile/todo',
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
    syncRouteAndReset() {
      return this.syncRouteQuery().then(result => {
        if (!result.active || !this.isTodoRouteActive()) return undefined
        return this.resetAndLoad()
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
          if (result.active && !result.landed) return this.resetAndLoad()
          return undefined
        })
      }
      const options = this.filterQueryOptions()
      const next = normalizeTodoFilterQuery(query || {}, options)
      const changed = !todoFilterStateEquals(next, this.filters, options)
      if (changed) {
        this.requestVersion += 1
        Object.assign(this.filters, next)
      }
      return this.syncRouteQuery().then(result => {
        if (changed && result.active) return this.resetAndLoad()
        return undefined
      })
    },
    runFilterQuery() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.requestVersion += 1
      Object.assign(this.filters, this.normalizeFilters())
      return this.syncRouteAndReset()
    },
    clearFilters() {
      if (!this.isTodoRouteActive()) return Promise.resolve()
      this.requestVersion += 1
      Object.assign(this.filters, normalizeTodoFilterQuery({}, this.filterQueryOptions()))
      this.keywordDraft = this.filters.keyword
      return this.syncRouteAndReset()
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
    sourceNames(values) {
      return values.map(this.sourceLabel).join('、')
    },
    sourceLabel(source) {
      return { approval: '统一审批', inventory: '库存', oa: 'OA', system: '系统' }[source] || '未知来源'
    },
    categoryLabel(category) {
      return { approval: '待审批', execution: '待执行', returned: '退回修改', risk: '风险提醒', personal: '个人事项' }[category] || '待处理'
    },
    priorityLabel(priority) {
      return { urgent: '紧急', important: '重要', normal: '普通' }[priority] || '普通'
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
    waitingText(waitingSeconds, createdTime) {
      let seconds = Number(waitingSeconds)
      if (!Number.isFinite(seconds) && createdTime) seconds = Math.max(0, (Date.now() - new Date(createdTime).getTime()) / 1000)
      if (!Number.isFinite(seconds)) return '等待时长未知'
      if (seconds >= 86400) return `等待 ${Math.floor(seconds / 86400)} 天`
      if (seconds >= 3600) return `等待 ${Math.floor(seconds / 3600)} 小时`
      return `等待 ${Math.max(1, Math.floor(seconds / 60))} 分钟`
    },
    selectCategory(category) {
      if (this.filters.category === category) return
      this.filters.category = category
      return this.handleFilterChange()
    },
    selectPriority(priority) {
      if (this.filters.priority === priority) return
      this.filters.priority = priority
      return this.handleFilterChange()
    },
    handleFilterChange() {
      return this.runFilterQuery()
    },
    handleQuery() {
      this.filters.keyword = String(this.keywordDraft || '').trim()
      return this.runFilterQuery()
    },
    async manualRefresh() {
      if (!this.isTodoRouteActive()) return { discarded: true }
      const routeSequence = this.routeOperationSequence
      const startedWithPendingRoute = !!this.pendingRouteOperation
      const summaryTasks = [this.$store.dispatch('todo/refreshSummaries')]
      if (!startedWithPendingRoute) summaryTasks.push(this.refreshScopedSummaries({ force: true }))
      await Promise.allSettled(summaryTasks)
      if (!this.isTodoRouteActive() || startedWithPendingRoute || this.pendingRouteOperation ||
        routeSequence !== this.routeOperationSequence) {
        return { discarded: true }
      }
      return this.resetAndLoad()
    },
    retryPage() {
      if (!this.isTodoRouteActive() || this.pendingRouteOperation) return { discarded: true }
      return this.resetAndLoad()
    },
    resetAndLoad() {
      if (!this.isTodoRouteActive()) return { discarded: true }
      this.clearBatchSelection()
      this.requestVersion += 1
      const requestVersion = this.requestVersion
      Object.assign(this.filters, this.normalizeFilters())
      this.pageNum = 1
      this.rows = []
      this.total = 0
      this.estimatedTotal = 0
      this.lastPageSize = 0
      this.staleSources = []
      this.unknownSources = []
      this.failures = []
      this.loadingMore = false
      return Promise.all([
        this.refreshScopedSummaries(),
        this.loadPage(false, { requestVersion, requestedPage: 1 })
      ]).then(results => results[1])
    },
    refreshScopedSummaries({ force = false } = {}) {
      if (!this.isTodoRouteActive()) return Promise.resolve({ discarded: true })
      const sources = this.selectedProviders.slice()
      const contextDeptId = this.contextSnapshot && this.contextSnapshot.deptId !== undefined && this.contextSnapshot.deptId !== null
        ? String(this.contextSnapshot.deptId).trim()
        : ''
      const keyword = String(this.filters.keyword || '').trim()
      const priority = this.filters.priority === 'all' ? '' : this.filters.priority
      const params = {
        contextDeptId,
        scopeMode: this.filters.scopeMode,
        keyword,
        priority
      }
      const key = JSON.stringify([sources.slice().sort(), params.scopeMode, params.contextDeptId, params.keyword, params.priority])
      const sameKey = key === this.scopedSummaryKey
      if (!force && sameKey && this.scopedSummaryPromise) return this.scopedSummaryPromise
      if (!force && sameKey && sources.every(source => {
        const provider = this.scopedProviderStates[source]
        return provider && provider.status !== 'pending'
      })) {
        return Promise.resolve({ cached: true })
      }

      const sequence = ++this.scopedSummarySequence
      this.scopedSummaryKey = key
      sources.forEach(source => {
        const provider = this.scopedProviderStates[source] || createScopedProviderState()
        const summary = sameKey ? provider.summary : null
        this.scopedProviderStates[source] = {
          summary,
          error: null,
          stale: false,
          unknown: false,
          status: 'pending'
        }
      })

      const request = Promise.all(sources.map(source => {
        return Promise.resolve()
          .then(() => fetchTodoSummary(source, params))
          .then(response => ({ source, ok: true, summary: unwrapSummaryResponse(response) }))
          .catch(error => ({ source, ok: false, error }))
      })).then(results => {
        if (!this.isTodoRouteActive() || sequence !== this.scopedSummarySequence || key !== this.scopedSummaryKey) {
          return { discarded: true }
        }
        results.forEach(result => {
          const previous = this.scopedProviderStates[result.source] || createScopedProviderState()
          if (result.ok) {
            this.scopedProviderStates[result.source] = {
              summary: result.summary,
              error: null,
              stale: false,
              unknown: false,
              status: 'fresh'
            }
            return
          }
          const hasCachedSummary = previous.summary !== null && previous.summary !== undefined
          this.scopedProviderStates[result.source] = {
            summary: hasCachedSummary ? previous.summary : null,
            error: result.error || new Error('Todo summary request failed'),
            stale: hasCachedSummary,
            unknown: !hasCachedSummary,
            status: hasCachedSummary ? 'stale' : 'unknown'
          }
        })
        return { discarded: false }
      })
      this.scopedSummaryPromise = request.then(result => {
        if (sequence === this.scopedSummarySequence) this.scopedSummaryPromise = null
        return result
      })
      return this.scopedSummaryPromise
    },
    async loadMore() {
      if (!this.isTodoRouteActive() || this.pendingRouteOperation) return { discarded: true }
      if (!this.canLoadMore) return
      const requestVersion = this.requestVersion
      const requestedPage = this.pageNum + 1
      this.loadingMore = true
      this.pageNum = requestedPage
      try {
        const result = await this.loadPage(true, { requestVersion, requestedPage })
        if ((!result || result.discarded) && this.isActiveRequest(requestVersion) && this.pageNum === requestedPage) {
          this.pageNum = Math.max(1, requestedPage - 1)
        }
        return result
      } finally {
        if (this.isActiveRequest(requestVersion)) this.loadingMore = false
      }
    },
    async loadPage(append, request = {}) {
      const requestVersion = request.requestVersion === undefined ? this.requestVersion : request.requestVersion
      const requestedPage = request.requestedPage === undefined ? this.pageNum : request.requestedPage
      if (!this.isActiveRequest(requestVersion)) return { discarded: true }
      this.pageError = ''
      Object.assign(this.filters, this.normalizeFilters())
      try {
        const result = await this.$store.dispatch('todo/refreshPage', {
          providers: this.selectedProviders,
          businessSource: this.filters.source === 'all' ? '' : this.filters.source,
          category: this.filters.category === 'all' ? '' : this.filters.category,
          scopeMode: this.filters.scopeMode,
          keyword: this.filters.keyword,
          priority: this.filters.priority === 'all' ? '' : this.filters.priority,
          pageNum: requestedPage,
          pageSize: this.pageSize
        })
        if (!this.isActiveRequest(requestVersion)) return { discarded: true }
        if (!result || result.discarded) return result
        const nextRows = Array.isArray(result.rows) ? result.rows : []
        this.lastPageSize = nextRows.length
        this.rows = append ? this.mergeRows(this.rows, nextRows) : this.mergeRows([], nextRows)
        this.total = result.total === null ? null : Number(result.total || 0)
        this.estimatedTotal = Number(result.estimatedTotal || this.rows.length)
        this.staleSources = result.staleSources || []
        this.unknownSources = result.unknownSources || []
        this.failures = result.failures || []
        return result
      } catch (error) {
        if (!this.isActiveRequest(requestVersion)) return { discarded: true }
        this.pageError = '待办加载失败，请稍后重试'
        return null
      }
    },
    isActiveRequest(requestVersion) {
      return this.isTodoRouteActive() && requestVersion === this.requestVersion
    },
    handleDeptChanged() {
      if (!this.isTodoRouteActive()) return { discarded: true }
      this.contextSnapshot = getSelectedDeptContext()
      this.requestVersion += 1
      Object.assign(this.filters, this.normalizeFilters())
      return this.syncRouteAndReset()
    },
    mergeRows(existing, incoming) {
      const seen = new Set()
      return (existing || []).concat(incoming || []).filter(row => {
        const key = row && row.todoKey
        if (!key || seen.has(key)) return false
        seen.add(key)
        return true
      })
    },
    openTodo(row) {
      this.suspendTodoActivity()
      const navigation = navigateTodo(row, {
        platform: 'mobile',
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
        if (result && result.ok === false && this.isTodoRoutePath()) {
          return this.resumeTodoActivity().then(() => result)
        }
        return result
      }, error => {
        return this.resumeTodoActivity().then(() => { throw error })
      })
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-todo-page {
  min-height: var(--mobile-viewport-height, 100dvh);
  background: var(--mobile-color-page, #f4f5f2);
  color: var(--mobile-color-ink, #17211d);
  font-family: var(--mobile-font-family, Inter, "PingFang SC", "Microsoft YaHei", sans-serif);
}

.todo-shell {
  width: min(100%, 480px);
  min-height: var(--mobile-viewport-height, 100dvh);
  margin: 0 auto;
  padding: calc(12px + var(--mobile-safe-top, env(safe-area-inset-top, 0px))) 16px calc(34px + var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)));
  box-sizing: border-box;
}

button, input, select { font: inherit; }
button { -webkit-tap-highlight-color: transparent; }

.todo-header {
  display: grid;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  gap: 12px;
  margin: 0 -16px 14px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--mobile-color-line, #dde2de);
  background: var(--mobile-color-surface, #fff);
}
.todo-header h1 { margin: 2px 0 0; font-size: 24px; line-height: 1.2; font-weight: 700; }
.todo-header span { color: var(--mobile-color-muted, #66736d); font-size: 12px; font-weight: 600; }
.back-button, .refresh-button {
  width: 44px; height: 44px; padding: 0; border: 1px solid var(--mobile-color-line, #dde2de); border-radius: 12px;
  background: var(--mobile-color-surface, #fff); color: var(--mobile-color-ink, #17211d); box-shadow: none;
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.summary-card, .filter-card, .priority-filter, .todo-card, .empty-card, .state-banner {
  border: 1px solid var(--mobile-color-line, #dde2de); background: var(--mobile-color-surface, #fff);
  box-shadow: none; backdrop-filter: none; -webkit-backdrop-filter: none;
}
.summary-card {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px;
  padding: 14px; border-radius: 18px; margin-bottom: 12px;
}
.summary-stat {
  min-width: 0;
  min-height: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 8px 5px;
  border: 1px solid #dbe4e9;
  border-radius: 12px;
  background: #f8fafb;
  color: #334155;
}
.summary-stat small { color: #64748b; font-size: 11px; }
.summary-stat strong { color: #0f766e; font-size: 22px; line-height: 1; }
.summary-stat.active { border-color: #0f766e; background: #e4f3f1; }
.summary-stat.active small { color: #115e59; font-weight: 700; }
.summary-scope {
  grid-column: 1 / -1;
  display: grid;
  gap: 3px;
  padding: 3px 2px 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.45;
}

.filter-card { padding: 12px; border-radius: 18px; }
.filter-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.filter-row select, .keyword-row { border: 1px solid #dbe4e9; border-radius: 12px; background: #f8fafb; }
.filter-row select { width: 100%; height: 44px; padding: 0 10px; color: #344b5a; }
.keyword-row { display: grid; grid-template-columns: 24px 1fr auto; align-items: center; margin-top: 8px; padding: 0 6px 0 12px; }
.keyword-row input { min-width: 0; height: 44px; border: 0; outline: 0; background: transparent; }
.keyword-row button { min-width: 44px; min-height: 44px; border: 0; border-radius: 9px; padding: 7px 12px; background: #e1efed; color: #0f766e; font-weight: 700; }
.active-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: 9px; }
.active-filter-chip, .reset-filters {
  min-height: 44px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  padding: 6px 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
}
.active-filter-chip i { margin-left: 5px; }
.reset-filters { margin-left: auto; border-color: transparent; background: transparent; color: #0f766e; font-weight: 700; }
.scope-help { margin: 8px 2px 0; color: #64748b; font-size: 11px; line-height: 1.55; }

.priority-filter {
  min-width: 0;
  margin-top: 10px;
  padding: 10px 12px 12px;
  overflow: hidden;
  border-radius: 18px;
}
.priority-filter-label {
  display: block;
  margin: 0 2px 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}
.priority-filter-track {
  display: flex;
  gap: 8px;
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.priority-filter-track::-webkit-scrollbar { display: none; }
.priority-filter-button {
  min-width: 70px;
  min-height: 44px;
  flex: 0 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  padding: 0 16px;
  background: #f8fafc;
  color: #475569;
  font-weight: 700;
}
.priority-filter-button.active {
  border-color: #0f766e;
  background: #dff1ee;
  color: #0f615c;
  box-shadow: inset 0 0 0 1px #0f766e;
}

.summary-stat:focus-visible,
.priority-filter-button:focus-visible,
.filter-row select:focus-visible,
.active-filter-chip:focus-visible,
.reset-filters:focus-visible,
.keyword-row button:focus-visible,
.back-button:focus-visible,
.refresh-button:focus-visible,
.load-more:focus-visible,
.empty-reset:focus-visible {
  outline: 2px solid #0f766e;
  outline-offset: 2px;
}
.mobile-todo-batch-toolbar button:focus-visible,
.mobile-todo-batch-select input:focus-visible,
.mobile-todo-quick-approve:focus-visible {
  outline: 2px solid #0f766e;
  outline-offset: 2px;
}
.keyword-row:focus-within { border-color: #0f766e; box-shadow: 0 0 0 2px rgba(15, 118, 110, .16); }

.state-banner { display: flex; align-items: center; gap: 8px; margin-top: 10px; padding: 11px 12px; border-radius: 14px; color: #9a6700; font-size: 13px; }
.state-banner.error { color: #b42318; }
.state-banner button { min-width: 44px; min-height: 44px; margin-left: auto; border: 0; background: transparent; color: inherit; font-weight: 700; }
.list-heading { display: flex; align-items: flex-end; justify-content: space-between; margin: 18px 3px 10px; }
.list-heading h2 { margin: 0 0 2px; font-size: 19px; }
.list-heading small, .list-heading > span { color: #64748b; font-size: 12px; }
.list-heading > span { max-width: 58%; text-align: right; line-height: 1.4; }
.list-heading .todo-sort-label { display: block; margin-top: 3px; color: #475569; font-weight: 700; }
.mobile-todo-batch-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  margin: 0 0 10px;
  border: 1px solid rgba(11, 107, 83, .22);
  border-radius: 16px;
  padding: 10px;
  background: #eff8f5;
}
.mobile-todo-batch-toolbar > div { min-width: 0; display: grid; gap: 2px; }
.mobile-todo-batch-toolbar strong { color: #164e43; font-size: 14px; }
.mobile-todo-batch-toolbar small { color: #60746a; font-size: 11px; line-height: 1.35; }
.mobile-todo-batch-toolbar button {
  min-height: 44px;
  border: 1px solid #aac8be;
  border-radius: 11px;
  padding: 8px 10px;
  background: #fff;
  color: #0b6b53;
  font-weight: 800;
}
.mobile-todo-batch-toolbar button.primary { border-color: #0b6b53; background: #0b6b53; color: #fff; }
.mobile-todo-batch-toolbar button:disabled { opacity: .5; }
.mobile-todo-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
.todo-list { display: grid; gap: 10px; }
.todo-card { position: relative; padding: 0; border-radius: 18px; overflow: hidden; }
.todo-card::before { content: ""; position: absolute; inset: 0 auto 0 0; width: 4px; background: #0f766e; pointer-events: none; }
.todo-card.urgent::before { background: #dc2626; }
.todo-card.important::before { background: #d97706; }
.todo-card.stale { border-color: #f5c978; }
.todo-card-action {
  position: relative;
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 14px 14px 56px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}
.todo-card-action:focus-visible { outline: 2px solid #0f766e; outline-offset: -2px; border-radius: 18px; }
.todo-card-head { display: flex; align-items: center; gap: 7px; }
.todo-card-head small { margin-left: auto; color: #64748b; }
.category-label, .cache-label { padding: 3px 8px; border-radius: 999px; background: #e4f3f1; color: #0f766e; font-size: 11px; font-weight: 700; }
.cache-label { background: #fff3d6; color: #a16207; }
.todo-card-title { display: block; margin: 10px 0 5px; font-size: 17px; font-weight: 700; }
.todo-card-summary { display: block; color: #60717e; font-size: 13px; line-height: 1.5; }
.todo-meta { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-top: 10px; color: #64748b; font-size: 11px; }
.todo-meta i { margin-right: 3px; }
.todo-process-label {
  position: absolute;
  right: 14px;
  bottom: 13px;
  display: inline-flex;
  min-width: 44px;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-radius: 10px;
  background: #0f766e;
  color: #fff;
  font-weight: 700;
}
.mobile-todo-approval-actions {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 56px;
  border-top: 1px solid #e3e8e5;
  padding: 6px 12px 7px 14px;
  background: #fbfdfc;
}
.mobile-todo-batch-select {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  color: #41574e;
  font-size: 13px;
  font-weight: 800;
}
.mobile-todo-batch-select input { width: 22px; height: 22px; margin: 0; accent-color: #0b6b53; }
.mobile-todo-quick-approve {
  min-width: 104px;
  min-height: 44px;
  margin-left: auto;
  border: 1px solid #0b6b53;
  border-radius: 11px;
  padding: 8px 14px;
  background: #0b6b53;
  color: #fff;
  font-weight: 800;
}
.mobile-todo-quick-approve:disabled { opacity: .56; }
.empty-card { display: flex; flex-direction: column; align-items: center; gap: 7px; padding: 30px 15px; border-radius: 18px; color: #64748b; text-align: center; }
.empty-card i { font-size: 28px; color: #0f766e; }
.empty-card span { font-size: 12px; }
.empty-reset { min-width: 44px; min-height: 44px; border: 0; background: transparent; color: #0f766e; font-weight: 700; }
.load-more { width: 100%; min-height: 44px; margin-top: 13px; padding: 12px; border: 0; border-radius: 14px; background: #dcecea; color: #0f766e; font-weight: 800; }
.list-end { margin: 16px 0 0; color: #64748b; font-size: 12px; text-align: center; }

/* Second-pass dashboard polish inspired by bento metrics and restrained primitives. */
.mobile-todo-page {
  overflow-x: hidden;
  background-color: var(--mobile-color-page);
  background-image:
    radial-gradient(circle at 100% 0%, rgba(11, 107, 83, 0.1), transparent 260px),
    radial-gradient(circle at 0% 72%, rgba(40, 102, 177, 0.055), transparent 220px);
}

.todo-shell {
  padding-top: 0;
  background: transparent;
}

.todo-header {
  position: sticky;
  z-index: 12;
  top: 0;
  margin: 0 -16px 14px;
  padding-top: calc(12px + var(--mobile-safe-top, env(safe-area-inset-top, 0px)));
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 24px rgba(23, 33, 29, 0.035);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

.back-button,
.refresh-button {
  background: var(--mobile-gradient-surface, linear-gradient(180deg, #fff, #fdfefd));
  box-shadow: var(--mobile-shadow-control, 0 4px 12px rgba(23, 33, 29, 0.045));
}

.summary-card,
.filter-card,
.priority-filter,
.todo-card,
.empty-card,
.state-banner {
  border-color: rgba(203, 211, 206, 0.78);
  background: var(--mobile-gradient-surface, linear-gradient(180deg, #fff, #fdfefd));
  box-shadow: var(--mobile-shadow-card, 0 10px 28px rgba(23, 33, 29, 0.055));
}

.summary-card {
  background:
    linear-gradient(135deg, rgba(231, 242, 237, 0.62), rgba(255, 255, 255, 0.96) 54%),
    #fff;
}

.summary-stat {
  border-color: rgba(203, 211, 206, 0.78);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 4px 12px rgba(23, 33, 29, 0.035);
}

.summary-stat.active {
  border-color: rgba(11, 107, 83, 0.26);
  color: var(--mobile-color-primary);
  background: linear-gradient(180deg, rgba(231, 242, 237, 0.98), rgba(231, 242, 237, 0.66));
  box-shadow: 0 7px 18px rgba(11, 107, 83, 0.1);
}

.filter-row select,
.keyword-row {
  border-color: rgba(203, 211, 206, 0.84);
  background: #fbfdfc;
  box-shadow: var(--mobile-shadow-inset, inset 0 1px 2px rgba(23, 33, 29, 0.045));
}

.todo-card::before {
  width: 3px;
  background: var(--mobile-color-primary);
}

.todo-process-label,
.load-more {
  background: var(--mobile-gradient-primary, linear-gradient(135deg, #0b6b53, #128064));
  color: #fff;
  box-shadow: var(--mobile-shadow-primary, 0 10px 24px rgba(11, 107, 83, 0.22));
}

.todo-card-action:active {
  background: rgba(231, 242, 237, 0.58);
}

@media (prefers-reduced-motion: no-preference) {
  .summary-card,
  .filter-card,
  .priority-filter {
    animation: mobile-surface-enter var(--mobile-duration-slow, 260ms) var(--mobile-ease-spring, cubic-bezier(.22,1,.36,1)) both;
  }

  .filter-card {
    animation-delay: 34ms;
  }
}

@media (max-width: 360px) {
  .summary-card { padding: 12px 9px; gap: 6px; }
  .summary-stat { min-height: 60px; }
  .todo-shell { padding-left: 12px; padding-right: 12px; }
  .todo-header { margin-right: -12px; margin-left: -12px; }
  .mobile-todo-batch-toolbar { grid-template-columns: 1fr 1fr; }
  .mobile-todo-batch-toolbar > div { grid-column: 1 / -1; }
}
</style>
