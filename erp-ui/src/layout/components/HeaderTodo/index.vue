<template>
  <div class="header-todo">
    <el-popover
      ref="todoPopover"
      placement="bottom-end"
      width="380"
      trigger="click"
      popper-class="header-todo-popover"
    >
      <div class="todo-popover-header">
        <div>
          <strong>我的待办</strong>
          <span v-if="healthHint" class="provider-health" :class="{ warning: todoPartialFailure }">
            {{ healthHint }}
          </span>
        </div>
        <el-button type="text" size="mini" @click="viewAll">查看全部</el-button>
      </div>
      <p class="todo-scope-hint">
        <i class="el-icon-office-building" aria-hidden="true"></i>{{ summaryScopeHint }}
      </p>

      <div v-if="(summaryLoading || isSummaryPending) && recentItems.length === 0" class="todo-popover-state">
        <i class="el-icon-loading"></i> 加载中...
      </div>
      <div v-else-if="recentItems.length === 0" class="todo-popover-state">
        <i class="el-icon-circle-check"></i>
        <span>{{ hasUnknownProvider ? '部分数据未知，请重试' : '暂无待办' }}</span>
        <el-button
          v-if="hasUnknownProvider"
          type="text"
          size="mini"
          :loading="summaryLoading"
          @click.stop="retrySummary"
        >重试</el-button>
      </div>
      <div v-else class="todo-recent-list">
        <div v-for="item in recentItems" :key="item.todoKey" class="todo-recent-item">
          <span class="priority-dot" :class="item.priority || 'normal'"></span>
          <button type="button" class="todo-recent-content" @click="handleTodo(item)">
            <strong>{{ item.title || item.businessNo || '待处理事项' }}</strong>
            <small>{{ item.deptName || '个人事项' }} · {{ categoryLabel(item.category) }}</small>
          </button>
          <el-button type="text" size="mini" @click="handleTodo(item)">{{ processLabel(item) }}</el-button>
        </div>
      </div>

      <button
        slot="reference"
        type="button"
        class="right-menu-item hover-effect todo-trigger"
        :aria-label="triggerAriaLabel"
        @click="ensureSummary"
      >
        <i class="el-icon-finished todo-icon"></i>
        <span v-if="todoTotal > 0" class="todo-badge">{{ badgeText }}</span>
        <span v-if="hasHealthIssue" class="todo-health-dot" :title="healthHint"></span>
      </button>
    </el-popover>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { constantRoutes } from '@/router'
import { buildAvailableRouteSet } from '@/utils/todoRouteResolver'
import { navigateTodo } from '@/utils/todoNavigator'
import { beginTodoContextLease, formatTodoContextSwitchNotice, rollbackTodoContextLease } from '@/utils/todoContextLease'
import { clearSelectedDept, getSelectedDeptContext, hasValidInventoryDeptContext, setSelectedDept } from '@/utils/shopContext'

const CATEGORY_LABELS = {
  approval: '待审批',
  execution: '待执行',
  returned: '退回修改',
  risk: '风险提醒',
  personal: '个人事项'
}

export default {
  name: 'HeaderTodo',
  computed: {
    ...mapGetters([
      'todoTotal',
      'todoRecent',
      'todoPartialFailure',
      'todoProviderStates',
      'permissions',
      'permission_routes'
    ]),
    summaryLoading() {
      return this.$store.state.todo.summaryLoading
    },
    badgeText() {
      return this.todoTotal > 99 ? '99+' : String(Math.min(this.todoTotal, 99))
    },
    triggerAriaLabel() {
      const count = this.isSummaryPending ? '正在加载' : `${this.todoTotal || 0} 条`
      const health = this.healthHint ? `，${this.healthHint}` : ''
      return `我的待办，${count}，${this.summaryScopeHint}${health}`
    },
    scopeSnapshot() {
      const todoState = this.$store && this.$store.state && this.$store.state.todo ? this.$store.state.todo : {}
      return {
        version: Number(todoState.contextVersion || 0),
        context: getSelectedDeptContext() || {}
      }
    },
    summaryScopeHint() {
      const context = this.scopeSnapshot.context || {}
      if (!hasValidInventoryDeptContext(context)) return '执行与审批范围：全部授权组织'
      return `执行范围：${context.deptName || '当前组织'} · 审批含跨组织`
    },
    recentItems() {
      return (this.todoRecent || []).slice(0, 5)
    },
    hasUnknownProvider() {
      return Object.values(this.todoProviderStates || {}).some(provider =>
        provider.unknown === true || provider.status === 'unknown'
      )
    },
    isSummaryPending() {
      return Object.values(this.todoProviderStates || {}).some(provider => provider.status === 'pending')
    },
    hasHealthIssue() {
      return this.todoPartialFailure || this.hasUnknownProvider
    },
    healthHint() {
      const states = Object.values(this.todoProviderStates || {})
      const unknown = states.filter(provider => provider.unknown === true || provider.status === 'unknown').length
      const stale = states.filter(provider => provider.stale).length
      if (unknown) return `${unknown} 个来源数据未知`
      if (stale) return `${stale} 个来源使用缓存`
      return ''
    },
    availableRouteSet() {
      return buildAvailableRouteSet({
        constantRoutes,
        dynamicRoutes: this.permission_routes,
        permissions: this.permissions
      })
    }
  },
  methods: {
    categoryLabel(category) {
      return CATEGORY_LABELS[category] || '待处理'
    },
    processLabel(item) {
      const source = item || {}
      if (source.category === 'approval') return '查看审批'
      if (source.category === 'returned') return '修改'
      if (source.category === 'risk') return '查看风险'
      if (source.category === 'personal') return '查看事项'
      if (source.category === 'execution') return '立即处理'
      return '查看详情'
    },
    ensureSummary() {
      if (!this.summaryLoading && (this.hasUnknownProvider || this.isSummaryPending)) {
        this.$store.dispatch('todo/refreshSummaries')
      }
    },
    retrySummary() {
      if (this.summaryLoading) return Promise.resolve()
      return this.$store.dispatch('todo/refreshSummaries')
    },
    closePopover() {
      const popover = this.$refs.todoPopover
      if (popover && typeof popover.doClose === 'function') popover.doClose()
    },
    viewAll() {
      this.closePopover()
      this.$router.push('/workbench/todo').catch(() => {})
    },
    handleTodo(item) {
      this.closePopover()
      return navigateTodo(item, {
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
    }
  }
}
</script>

<style lang="scss" scoped>
.header-todo {
  height: 32px;
}

.todo-trigger {
  position: relative;
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  color: #dbeafe;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.todo-trigger:focus-visible {
  outline: 2px solid #67c9f3;
  outline-offset: 2px;
  border-radius: 5px;
}

.todo-icon {
  font-size: 18px;
}

.todo-badge {
  position: absolute;
  top: -5px;
  right: -7px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 9px;
  color: #fff;
  background: #f56c6c;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  white-space: nowrap;
  box-sizing: border-box;
}

.todo-health-dot {
  position: absolute;
  right: -2px;
  bottom: 1px;
  width: 7px;
  height: 7px;
  border: 2px solid #172033;
  border-radius: 50%;
  background: #fbbf24;
}
</style>

<style lang="scss">
.header-todo-popover {
  padding: 0;
}

.todo-popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.provider-health {
  margin-left: 8px;
  color: #d97706;
  font-size: 11px;
  font-weight: 400;
}

.todo-scope-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 8px 14px;
  border-bottom: 1px solid #eef2f7;
  background: #fff;
  color: #475569;
  font-size: 11px;
  line-height: 1.4;
}

.todo-popover-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 14px;
  color: #64748b;
  text-align: center;
}

.todo-recent-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f5f9;
}

.todo-recent-item:last-child {
  border-bottom: 0;
}

.priority-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
  background: #64748b;
}

.priority-dot.urgent { background: #ef4444; }
.priority-dot.important { background: #f59e0b; }

.todo-recent-content {
  min-width: 0;
  flex: 1;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.todo-recent-content strong,
.todo-recent-content small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-recent-content strong {
  color: #1e293b;
  font-size: 13px;
}

.todo-recent-content small {
  margin-top: 4px;
  color: #64748b;
  font-size: 11px;
}
</style>
