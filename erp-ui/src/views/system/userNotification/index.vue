<template>
  <div class="app-container system-management-page user-notification-page">
    <system-page-header
      title="我的消息"
      description="查看发给当前账号的业务消息；公告与待办仍在各自入口单独统计。"
      eyebrow="消息中心"
      icon="el-icon-message"
    >
      <div slot="actions" class="message-actions">
          <el-radio-group v-model="filter" size="small">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="unread">未读 {{ unreadCount }}</el-radio-button>
          </el-radio-group>
          <el-button size="small" :disabled="unreadCount === 0" :loading="markingAll" @click="markAllRead">
            全部已读
          </el-button>
          <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadMessages">刷新</el-button>
      </div>
    </system-page-header>

    <el-card shadow="never" class="message-list-card">
      <div v-if="loading" class="message-state"><i class="el-icon-loading" /> 正在加载消息</div>
      <div v-else-if="loadError" class="message-state is-error">
        <i class="el-icon-warning-outline" />
        <span>{{ loadError }}</span>
        <el-button type="text" size="small" @click="loadMessages">重新加载</el-button>
      </div>
      <el-empty v-else-if="filteredMessages.length === 0" :description="filter === 'unread' ? '没有未读消息' : '暂无业务消息'" />
      <button
        v-for="item in filteredMessages"
        v-else
        :key="item.notificationId"
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
  </div>
</template>

<script>
import {
  listUserNotifications,
  markUserNotificationRead
} from '@/api/system/userNotification'
import { resolvePushRoute } from '@/services/pushRegistration'

const READ_BATCH_SIZE = 10

export default {
  name: 'UserNotification',
  data() {
    return {
      loading: false,
      markingAll: false,
      filter: 'all',
      messages: [],
      loadError: ''
    }
  },
  computed: {
    unreadCount() {
      return this.messages.filter(item => item.readStatus !== '1').length
    },
    filteredMessages() {
      return this.filter === 'unread'
        ? this.messages.filter(item => item.readStatus !== '1')
        : this.messages
    }
  },
  created() {
    this.loadMessages()
  },
  methods: {
    loadMessages() {
      this.loading = true
      this.loadError = ''
      return listUserNotifications().then(response => {
        this.messages = Array.isArray(response.data) ? response.data : []
      }).catch(() => {
        this.messages = []
        this.loadError = '消息加载失败，请检查网络后重试。'
      }).finally(() => {
        this.loading = false
      })
    },
    parseRoute(item) {
      let params = {}
      try {
        const parsed = typeof item.routeParams === 'string' ? JSON.parse(item.routeParams) : item.routeParams
        params = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
      } catch (error) {
        params = {}
      }
      if (item.routeType === 'OA_SIGN_HR_TASK') {
        return resolvePushRoute({ routeType: item.routeType, taskId: params.taskId })
      }
      if (item.routeType === 'OA_SIGN_PACKAGE_SIGN') {
        return resolvePushRoute({ routeType: item.routeType, packageId: params.packageId })
      }
      return null
    },
    async openMessage(item) {
      if (item.readStatus !== '1') {
        try {
          await markUserNotificationRead(item.notificationId)
          this.$set(item, 'readStatus', '1')
          this.notifyHeaderChanged()
        } catch (error) {
          // 请求层统一反馈；消息正文仍可阅读。
        }
      }
      const route = this.parseRoute(item)
      if (route) {
        this.$router.push(route).catch(() => {})
      }
    },
    async markAllRead() {
      const unread = this.messages.filter(item => item.readStatus !== '1')
      if (this.markingAll || unread.length === 0) return
      this.markingAll = true
      try {
        for (let offset = 0; offset < unread.length; offset += READ_BATCH_SIZE) {
          const batch = unread.slice(offset, offset + READ_BATCH_SIZE)
          const results = await Promise.allSettled(batch.map(item => markUserNotificationRead(item.notificationId)))
          results.forEach((result, index) => {
            if (result.status === 'fulfilled') this.$set(batch[index], 'readStatus', '1')
          })
        }
        this.notifyHeaderChanged()
      } finally {
        this.markingAll = false
      }
    },
    notifyHeaderChanged() {
      if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new Event('user-notification-changed'))
      }
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
  .message-meta { flex-direction: column; align-items: flex-end; }
}
</style>
