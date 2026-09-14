<template>
  <div class="app-container system-management-page user-notification-page">
    <system-page-header
      title="我的消息"
      description="查看发给当前账号的业务消息；公告与待办仍在各自入口单独统计。"
      eyebrow="消息中心"
      icon="el-icon-message"
    >
      <div slot="actions" class="message-actions">
          <el-button size="small" :disabled="loading || unreadCount === 0 || snapshotMaxId == null || !!openingId" :loading="markingAll" title="标记本次加载时已有的全部未读消息；筛选不改变范围" @click="markAllRead">全部已读</el-button>
          <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="refreshMessages">刷新</el-button>
      </div>
    </system-page-header>

    <el-form :inline="true" class="message-filters" @submit.native.prevent="handleQuery">
      <el-form-item label="关键词"><el-input v-model.trim="query.keyword" maxlength="100" clearable placeholder="搜索标题或正文" @clear="handleQuery" /></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.routeType" clearable placeholder="全部类型" @change="handleQuery"><el-option v-for="type in routeTypes" :key="type" :value="type" :label="routeTypeLabel(type)" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.readStatus" @change="handleQuery"><el-option label="全部" value="" /><el-option :label="'未读（共 ' + unreadCount + '）'" value="0" /><el-option label="已读" value="1" /></el-select></el-form-item>
      <el-form-item label="日期"><el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" start-placeholder="开始日期" end-placeholder="结束日期" @change="handleDateRange" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit" icon="el-icon-search">搜索</el-button></el-form-item>
    </el-form>
    <el-alert v-if="actionError" :title="actionError" type="warning" :closable="false" show-icon />
    <el-button v-if="navigationRetry" size="small" :disabled="!!openingId || markingAll" @click="retryBusinessNavigation">重新打开业务</el-button>
    <el-card shadow="never" class="message-list-card">
      <div v-if="loading" class="message-state"><i class="el-icon-loading" /> 正在加载消息</div>
      <div v-else-if="loadError" class="message-state is-error">
        <i class="el-icon-warning-outline" />
        <span>{{ loadError }}</span>
        <el-button type="text" size="small" @click="loadMessages">重新加载</el-button>
      </div>
      <el-empty v-else-if="filteredMessages.length === 0" :description="query.readStatus === '0' ? '没有符合条件的未读消息' : '没有符合条件的业务消息'" />
      <button
        v-for="item in filteredMessages"
        v-else
        :key="item.notificationId"
        :data-notification-id="item.notificationId"
        :disabled="!!openingId || markingAll"
        type="button"
        class="message-row"
        :class="{ 'is-unread': item.readStatus !== '1' }"
        @click="openMessage(item)"
      >
        <span class="message-dot" aria-hidden="true" />
        <span class="message-content">
          <strong>{{ item.title || '业务消息' }}</strong>
          <span>{{ item.body || '点击查看对应业务' }}</span>
        </span>
        <span class="message-meta">
          <time>{{ item.createTime || '-' }}</time>
          <el-tag v-if="item.readStatus !== '1'" size="mini" type="danger">未读</el-tag>
          <el-tag v-else size="mini" type="info">已读</el-tag>
        </span>
      </button>
    </el-card>
    <el-pagination v-if="total > 0" class="message-pagination" :current-page="query.pageNum" :page-size="query.pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" />
  </div>
</template>

<script>
import { listUserNotifications, markUserNotificationRead, markAllUserNotificationsRead } from '@/api/system/userNotification'
import { resolvePushRoute } from '@/services/pushRoute'
import { getSelectedDeptId } from '@/utils/shopContext'
const { isMobileClient } = require('@/utils/clientPlatform')

const emptyQuery = () => ({ keyword: '', routeType: '', readStatus: '', startDate: '', endDate: '', pageNum: 1, pageSize: 20 })
function decimalId(value, zero = false) {
  if (typeof value !== 'string' && typeof value !== 'number') return ''
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const text = String(value).trim().replace(/^0+(?=\d)/, '')
  return /^(0|[1-9]\d*)$/.test(text) && (zero || text !== '0') &&
    (text.length < 19 || (text.length === 19 && text <= '9223372036854775807')) ? text : ''
}
function boundedCount(value) {
  if (!['string', 'number'].includes(typeof value) || !/^\d+$/.test(String(value))) throw Error('消息数量响应无效，请重试')
  const number = Number(value)
  if (!Number.isSafeInteger(number) || number < 0) throw Error('消息数量响应无效，请重试')
  return number
}
function atMost(id, maximum) { return id.length < maximum.length || (id.length === maximum.length && id <= maximum) }

export default {
  name: 'UserNotification',
  data() {
    return { loading: false, markingAll: false, openingId: '', query: emptyQuery(), messages: [], total: 0,
      unreadCount: 0, routeTypes: [], snapshotMaxId: null, dateRange: [], loadError: '', actionError: '', navigationRetry: null,
      readEpoch: 0, actionEpoch: 0, activeScope: '', inactive: false, savedPosition: null }
  },
  computed: { filteredMessages() { return this.messages } },
  created() {
    this.activeScope = this.messageScope()
    this.restoreViewState()
    this.loadMessages(true)
  },
  mounted() {
    window.addEventListener('erp:dept-changed', this.onScopeChanged)
    window.addEventListener('pagehide', this.saveViewState)
    window.addEventListener('user-notification-opened', this.onNotificationOpened)
  },
  activated() {
    if (this.inactive) {
      this.inactive = false
      if (this.activeScope !== this.messageScope()) this.onScopeChanged()
      else this.loadMessages(true)
    }
  },
  deactivated() { this.suspendMessages() },
  beforeDestroy() {
    this.suspendMessages()
    window.removeEventListener('erp:dept-changed', this.onScopeChanged)
    window.removeEventListener('pagehide', this.saveViewState)
    window.removeEventListener('user-notification-opened', this.onNotificationOpened)
  },
  beforeRouteLeave(to, from, next) { this.suspendMessages(); next() },
  watch: { '$store.getters.id'() { this.onScopeChanged() } },
  methods: {
    onNotificationOpened() {
      if (!this.inactive) this.loadMessages(true)
    },
    messageScope() { return `${String(this.$store.getters.id || '')}:${String(getSelectedDeptId() || '')}` },
    viewStorageKey() { return 'erp:message-view:v1:' + this.activeScope },
    currentScope(scope) { return !this.inactive && scope === this.activeScope && scope === this.messageScope() },
    onScopeChanged() {
      if (this.activeScope === this.messageScope()) return
      this.readEpoch += 1; this.actionEpoch += 1
      this.loading = false; this.markingAll = false; this.openingId = ''
      this.messages = []; this.total = 0; this.unreadCount = 0; this.routeTypes = []
      this.loadError = ''; this.actionError = ''; this.navigationRetry = null
      this.query = emptyQuery(); this.dateRange = []; this.snapshotMaxId = null; this.savedPosition = null
      this.activeScope = this.messageScope(); this.restoreViewState()
      if (!this.inactive) this.loadMessages(true)
    },
    suspendMessages() {
      if (!this.inactive) this.saveViewState()
      this.inactive = true; this.readEpoch += 1; this.actionEpoch += 1; this.navigationRetry = null
      this.loading = false; this.markingAll = false; this.openingId = ''
    },
    async loadMessages(restorePosition = false) {
      const epoch = ++this.readEpoch, scope = this.activeScope
      if (!this.currentScope(scope)) return
      const params = { ...this.query, snapshotMaxId: this.snapshotMaxId == null ? undefined : this.snapshotMaxId }
      const querySnapshot = JSON.stringify(params)
      this.loading = true; this.loadError = ''
      const current = () => epoch === this.readEpoch && this.currentScope(scope)
      try {
        const response = await listUserNotifications(params)
        if (!current()) return
        const data = response && response.data
        const snapshot = decimalId(data && data.snapshotMaxId, true)
        if (!data || !Array.isArray(data.rows) || !Array.isArray(data.routeTypes) || !snapshot
          || (params.snapshotMaxId != null && snapshot !== params.snapshotMaxId)
          || Number(data.pageNum) !== params.pageNum || Number(data.pageSize) !== params.pageSize) throw Error('消息分页响应不完整，请重试')
        const rows = data.rows.map(row => ({ ...row, notificationId: decimalId(row.notificationId) }))
        if (rows.length > params.pageSize || rows.some(row => !row.notificationId || !atMost(row.notificationId, snapshot))
          || new Set(rows.map(row => row.notificationId)).size !== rows.length) throw Error('消息记录响应无效，请重试')
        this.messages = rows; this.total = boundedCount(data.total); this.unreadCount = boundedCount(data.unreadCount)
        this.routeTypes = [...new Set(data.routeTypes.filter(type => typeof type === 'string' && type.length <= 64))]
        this.snapshotMaxId = snapshot
        if (!rows.length && this.total > 0 && this.query.pageNum > Math.ceil(this.total / this.query.pageSize)) {
          this.query.pageNum = Math.ceil(this.total / this.query.pageSize)
          return this.loadMessages(restorePosition)
        }
        this.saveViewState(false)
      } catch (error) {
        if (!current()) return
        this.messages = []; this.total = 0
        this.loadError = error.message || '消息加载失败，请检查网络后重试。'
      } finally {
        if (current()) {
          this.loading = false
          this.$nextTick(() => {
            if (!current() || querySnapshot !== JSON.stringify({ ...this.query, snapshotMaxId: params.snapshotMaxId })) return
            if (restorePosition) this.restoreBrowsePosition()
            else { const surface = this.scrollSurface(); if (surface) surface.scrollTop = 0 }
          })
        }
      }
    },
    handleQuery() { this.query.pageNum = 1; this.savedPosition = null; this.loadMessages() },
    handleDateRange(value) {
      this.query.startDate = value && value[0] || ''; this.query.endDate = value && value[1] || ''
      this.handleQuery()
    },
    changePage(page) { this.query.pageNum = page; this.savedPosition = null; this.loadMessages() },
    changePageSize(size) { this.query.pageSize = size; this.handleQuery() },
    refreshMessages() { this.snapshotMaxId = null; this.handleQuery() },
    routeTypeLabel(type) { return { OA_SIGN_HR_TASK: '人事签约任务', OA_SIGN_PACKAGE_SIGN: '合同签署' }[type] || type || '业务消息' },
    parseRoute(item) {
      let params = {}
      try {
        const parsed = typeof item.routeParams === 'string' ? JSON.parse(item.routeParams) : item.routeParams
        params = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
      } catch (error) { params = {} }
      return resolvePushRoute({
        ...params,
        routeType: item.routeType,
        recipientUserId: this.$store.getters.id
      }, { mobile: !!(this.$route && this.$route.path.startsWith('/mobile/')) || isMobileClient() })
    },
    async openMessage(item) {
      if (this.openingId || this.markingAll || !this.messages.includes(item)) return
      const scope = this.activeScope, epoch = ++this.actionEpoch, route = this.parseRoute(item), id = decimalId(item.notificationId)
      if (!id || !this.currentScope(scope)) return
      this.openingId = id; this.actionError = ''; this.navigationRetry = null; this.saveViewState()
      const current = () => epoch === this.actionEpoch && this.currentScope(scope)
      try {
        if (item.readStatus !== '1') {
          try {
            await markUserNotificationRead(id)
            if (!current()) return
            this.$set(item, 'readStatus', '1'); this.unreadCount = Math.max(0, this.unreadCount - 1)
            this.notifyHeaderChanged()
          } catch (error) {
            if (!current()) return
            this.actionError = '消息已读状态暂未确认，可刷新核对；仍可查看业务。'
          }
        }
        if (current() && route && (!this.$route || route.path !== this.$route.path)) {
          try { await this.$router.push(route) }
          catch (error) {
            if (!current()) return
            this.navigationRetry = { route, scope }
            this.actionError = '业务页面暂未打开，请重试；消息已读状态保持。'
          }
        }
      } finally { if (epoch === this.actionEpoch) this.openingId = '' }
    },
    async retryBusinessNavigation() {
      const retry = this.navigationRetry
      if (!retry || !this.currentScope(retry.scope) || this.openingId || this.markingAll) return
      const epoch = ++this.actionEpoch
      this.openingId = 'navigation'
      try { await this.$router.push(retry.route) }
      catch (error) {
        if (epoch === this.actionEpoch && this.currentScope(retry.scope)) this.actionError = '业务页面暂未打开，请重试。'
      } finally { if (epoch === this.actionEpoch) this.openingId = '' }
    },
    async markAllRead() {
      const scope = this.activeScope, maximum = decimalId(this.snapshotMaxId, true)
      if (this.markingAll || this.openingId || this.loading || !this.currentScope(scope) || !maximum || !this.unreadCount) return
      const epoch = ++this.actionEpoch
      this.readEpoch += 1; this.markingAll = true; this.actionError = ''
      const current = () => epoch === this.actionEpoch && this.currentScope(scope)
      try {
        const response = await markAllUserNotificationsRead(maximum)
        if (!current()) return
        const data = response && response.data
        if (!data || decimalId(data.snapshotMaxId, true) !== maximum) throw Error('已读结果未确认')
        const unreadCount = boundedCount(data.unreadCount)
        boundedCount(data.changed)
        this.messages.forEach(item => { if (atMost(item.notificationId, maximum)) this.$set(item, 'readStatus', '1') })
        this.unreadCount = unreadCount; this.notifyHeaderChanged()
        await this.loadMessages()
      } catch (error) {
        if (current()) this.actionError = '批量已读结果暂未确认，请刷新核对后重试。'
      } finally { if (epoch === this.actionEpoch) this.markingAll = false }
    },
    scrollSurface() {
      if (!this.$el || typeof document === 'undefined') return null
      return this.$el.closest('.app-main') || document.scrollingElement
    },
    saveViewState(capturePosition = true) {
      if (!this.activeScope || this.activeScope !== this.messageScope()) return
      if (capturePosition !== false) {
        const surface = this.scrollSurface()
        if (surface) {
          const top = surface.getBoundingClientRect ? surface.getBoundingClientRect().top : 0
          const anchor = Array.from(this.$el.querySelectorAll('.message-row')).find(row => row.getBoundingClientRect().bottom > top)
          this.savedPosition = { top: surface.scrollTop || 0, id: anchor && anchor.dataset.notificationId,
            offset: anchor ? anchor.getBoundingClientRect().top - top : 0 }
        }
      }
      try { window.sessionStorage.setItem(this.viewStorageKey(), JSON.stringify({ version: 1, query: this.query, snapshotMaxId: this.snapshotMaxId, position: this.savedPosition })) } catch (error) { /* In-memory view remains available. */ }
    },
    restoreViewState() {
      try {
        const state = JSON.parse(window.sessionStorage.getItem(this.viewStorageKey()) || 'null')
        if (!state || state.version !== 1 || !state.query) return
        const q = state.query
        if (!Number.isInteger(q.pageNum) || q.pageNum < 1 || q.pageNum > 100000 || ![20, 50, 100].includes(q.pageSize)
          || typeof q.keyword !== 'string' || q.keyword.length > 100 || typeof q.routeType !== 'string' || q.routeType.length > 64
          || !['', '0', '1'].includes(q.readStatus) || ![q.startDate, q.endDate].every(date => typeof date === 'string' && (!date || /^\d{4}-\d{2}-\d{2}$/.test(date)))) return
        this.query = Object.fromEntries(Object.keys(emptyQuery()).map(key => [key, q[key]])); this.snapshotMaxId = decimalId(state.snapshotMaxId, true) || null
        this.dateRange = q.startDate && q.endDate ? [q.startDate, q.endDate] : []
        if (state.position && Number.isFinite(state.position.top) && state.position.top >= 0 && Number.isFinite(state.position.offset)) this.savedPosition = state.position
      } catch (error) { /* Invalid preferences cannot broaden the message query. */ }
    },
    restoreBrowsePosition() {
      const surface = this.scrollSurface(), position = this.savedPosition
      if (!surface || !position) return
      const anchor = Array.from(this.$el.querySelectorAll('.message-row')).find(row => row.dataset.notificationId === position.id)
      const surfaceTop = surface.getBoundingClientRect ? surface.getBoundingClientRect().top : 0
      surface.scrollTop = anchor ? Math.max(0, surface.scrollTop + anchor.getBoundingClientRect().top - surfaceTop - position.offset) : position.top
    },
    notifyHeaderChanged() {
      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') window.dispatchEvent(new Event('user-notification-changed'))
    }
  }
}
</script>

<style lang="scss" scoped>
.user-notification-page {
  background: #f4f7fb;
  min-height: calc(100vh - 96px);
}
.message-header-card,
.message-list-card {
  border: 1px solid #e4eaf2;
}
.message-list-card { margin-top: 14px; }
.message-filters { margin-top: 16px; }
.message-pagination { margin: 16px 0; overflow-x: auto; }
.message-row:focus-visible { outline: 2px solid #2563eb; outline-offset: -2px; }
.message-row:disabled { cursor: wait; }
.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.message-header h2 { margin: 0 0 8px; color: #172033; font-size: 20px; }
.message-header p { margin: 0; color: #64748b; font-size: 13px; }
.message-actions { display: flex; align-items: center; gap: 10px; }
.message-state { padding: 44px; text-align: center; color: #64748b; }
.message-state.is-error { display: flex; align-items: center; justify-content: center; gap: 8px; color: #b91c1c; }
.message-row {
  width: 100%;
  border: 0;
  border-bottom: 1px solid #edf1f6;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 12px;
  text-align: left;
  cursor: pointer;
}
.message-row:last-child { border-bottom: 0; }
.message-row:hover { background: #f8fbff; }
.message-row.is-unread { background: #f5f9ff; }
.message-dot { width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; flex: none; }
.message-row.is-unread .message-dot { background: #2563eb; box-shadow: 0 0 0 4px rgba(37, 99, 235, .1); }
.message-content { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 6px; }
.message-content strong { color: #1e293b; font-size: 14px; }
.message-content span { color: #64748b; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.message-meta { flex: none; display: flex; align-items: center; gap: 10px; color: #94a3b8; font-size: 12px; }
@media (max-width: 720px) {
  .message-header { align-items: flex-start; flex-direction: column; }
  .message-actions { width: 100%; flex-wrap: wrap; }
  .message-row { align-items: flex-start; }
  .message-filters ::v-deep .el-form-item { display: block; margin-right: 0; }
  .message-filters ::v-deep .el-form-item__content { max-width: calc(100% - 56px); }
  .message-filters ::v-deep .el-date-editor { max-width: 100%; }
  .message-meta { flex-direction: column; align-items: flex-end; }
}
</style>
