<template>
  <div class="header-notice-group">
    <el-popover ref="noticePopover" placement="bottom-end" width="320" trigger="manual" :value="noticeVisible" popper-class="notice-popover">
      <div class="notice-header">
        <span class="notice-title">通知公告</span>
        <button
          type="button"
          class="notice-mark-all"
          :disabled="noticeMarkingAll || noticeLoading || noticeReadPendingCount > 0 || noticeUnreadCount <= 0"
          @click="markAllRead"
        >全部已读</button>
      </div>
      <div v-if="noticeLoading" class="notice-loading"><i class="el-icon-loading" /> 加载中...</div>
      <div v-else-if="noticeList.length === 0" class="notice-empty"><i class="el-icon-inbox" /><br>暂无公告</div>
      <div v-else>
        <button v-for="item in noticeList" :key="item.noticeId" type="button" class="notice-item" :class="{ 'is-read': item.isRead }" @click="previewNotice(item)">
          <el-tag size="mini" :type="item.noticeType === '1' ? 'warning' : 'success'" class="notice-tag">
            {{ item.noticeType === '1' ? '通知' : '公告' }}
          </el-tag>
          <span class="notice-item-title">{{ item.noticeTitle }}</span>
          <span class="notice-item-date">{{ item.createTime }}</span>
        </button>
      </div>
    </el-popover>

    <button
      v-popover:noticePopover
      ref="noticeTrigger"
      type="button"
      class="header-entry notice-trigger"
      title="通知公告"
      :aria-label="noticeAriaLabel"
      :aria-expanded="noticeVisible"
      aria-haspopup="dialog"
      @mouseenter="onNoticeEnter"
      @mouseleave="onNoticeLeave"
      @blur="onNoticeLeave"
      @click.stop="onNoticeActivate"
    >
      <svg-icon icon-class="bell" />
      <span v-if="noticeUnreadCount > 0" class="header-badge notice-badge">{{ displayCount(noticeUnreadCount) }}</span>
    </button>

    <button type="button" class="header-entry" title="我的消息" aria-label="我的消息" @click="openMyMessages">
      <i class="el-icon-message-solid" />
      <span v-if="messageUnreadCount > 0" class="header-badge message-badge">{{ displayCount(messageUnreadCount) }}</span>
    </button>

    <notice-detail-view ref="noticeViewRef" />
  </div>
</template>

<script>
import NoticeDetailView from './DetailView'
import { listNoticeTop, markNoticeRead, markNoticeReadAll } from '@/api/system/notice'
import { getUserNotificationUnreadCount } from '@/api/system/userNotification'

const REFRESH_INTERVAL = 60000

export default {
  name: 'HeaderNotice',
  components: { NoticeDetailView },
  data() {
    return {
      noticeList: [],
      noticeUnreadCount: 0,
      messageUnreadCount: 0,
      noticeLoading: false,
      noticeMarkingAll: false,
      noticeReadPendingCount: 0,
      noticeDestroyed: false,
      noticeRequestVersion: 0,
      noticeVisible: false,
      noticeLeaveTimer: null,
      refreshTimer: null
    }
  },
  computed: {
    noticeAriaLabel() {
      return this.noticeUnreadCount > 0
        ? `通知公告，${this.noticeUnreadCount} 条未读`
        : '通知公告'
    }
  },
  mounted() {
    this.loadNoticeTop()
    this.loadMessageUnreadCount()
    this.refreshTimer = setInterval(this.refreshHeaderCounts, REFRESH_INTERVAL)
    if (typeof window !== 'undefined') {
      window.addEventListener('user-notification-changed', this.loadMessageUnreadCount)
    }
  },
  beforeDestroy() {
    this.noticeDestroyed = true
    this.noticeRequestVersion += 1
    clearTimeout(this.noticeLeaveTimer)
    clearInterval(this.refreshTimer)
    if (typeof window !== 'undefined') {
      window.removeEventListener('user-notification-changed', this.loadMessageUnreadCount)
    }
  },
  methods: {
    displayCount(value) {
      const count = Number(value) || 0
      return count > 99 ? '99+' : count
    },
    refreshHeaderCounts() {
      this.loadMessageUnreadCount()
    },
    openMyMessages() {
      this.$router.push('/system/user-notification').catch(() => {})
    },
    loadMessageUnreadCount() {
      return getUserNotificationUnreadCount().then(response => {
        this.messageUnreadCount = Math.max(0, Number(response.data) || 0)
      }).catch(() => {})
    },
    onNoticeEnter() {
      this.openNotice(false)
    },
    // 点击或键盘激活时将焦点送入通知浮层
    onNoticeActivate() {
      this.openNotice(true)
    },
    openNotice(focusPopover = false) {
      clearTimeout(this.noticeLeaveTimer)
      const opening = !this.noticeVisible
      if (opening && !this.noticeLoading && !this.noticeMarkingAll && this.noticeReadPendingCount === 0) {
        this.loadNoticeTop()
      }
      this.noticeVisible = true
      this.$nextTick(() => {
        const popover = this.$refs.noticePopover
        const popper = popover && popover.$refs ? popover.$refs.popper : null
        const trigger = this.$refs.noticeTrigger
        if (!popper) return
        if (popper && !popper._noticeBound) {
          popper._noticeBound = true
          popper.setAttribute('role', 'dialog')
          popper.setAttribute('aria-label', '通知公告')
          popper.setAttribute('tabindex', '-1')
          if (trigger && popper.id) {
            trigger.setAttribute('aria-controls', popper.id)
            trigger.removeAttribute('aria-describedby')
          }
          popper.addEventListener('mouseenter', () => clearTimeout(this.noticeLeaveTimer))
          popper.addEventListener('mouseleave', () => {
            this.noticeLeaveTimer = setTimeout(() => { this.noticeVisible = false }, 100)
          })
          popper.addEventListener('focusin', () => clearTimeout(this.noticeLeaveTimer))
          popper.addEventListener('focusout', event => {
            const nextTarget = event.relatedTarget
            if (popper.contains(nextTarget) || (trigger && trigger.contains(nextTarget))) return
            this.onNoticeLeave()
          })
          popper.addEventListener('keydown', event => {
            if (event.key !== 'Escape') return
            event.preventDefault()
            clearTimeout(this.noticeLeaveTimer)
            this.noticeVisible = false
            this.$nextTick(() => {
              if (this.$refs.noticeTrigger) this.$refs.noticeTrigger.focus()
            })
          })
        }
        if (focusPopover) popper.focus()
      })
    },
    onNoticeLeave() {
      clearTimeout(this.noticeLeaveTimer)
      this.noticeLeaveTimer = setTimeout(() => { this.noticeVisible = false }, 150)
    },
    loadNoticeTop() {
      if (this.noticeDestroyed) return Promise.resolve()
      const requestVersion = ++this.noticeRequestVersion
      this.noticeLoading = true
      return listNoticeTop().then(response => {
        if (this.noticeDestroyed || requestVersion !== this.noticeRequestVersion) return
        this.noticeList = response.data || []
        this.noticeUnreadCount = response.unreadCount !== undefined
          ? response.unreadCount
          : this.noticeList.filter(item => !item.isRead).length
      }).catch(() => {}).finally(() => {
        if (!this.noticeDestroyed && requestVersion === this.noticeRequestVersion) {
          this.noticeLoading = false
        }
      })
    },
    previewNotice(item) {
      this.noticeVisible = false
      if (!item.isRead && !this.noticeMarkingAll) {
        this.noticeReadPendingCount += 1
        markNoticeRead(item.noticeId).catch(() => {}).finally(() => {
          if (this.noticeDestroyed) return
          this.noticeReadPendingCount = Math.max(0, this.noticeReadPendingCount - 1)
          if (this.noticeReadPendingCount === 0) this.loadNoticeTop()
        })
        item.isRead = true
        const index = this.noticeList.indexOf(item)
        if (index !== -1) this.$set(this.noticeList, index, { ...item, isRead: true })
        this.noticeUnreadCount = Math.max(0, this.noticeUnreadCount - 1)
      }
      this.$refs.noticeViewRef.open(item.noticeId)
    },
    async markAllRead() {
      if (this.noticeDestroyed || this.noticeMarkingAll || this.noticeLoading || this.noticeReadPendingCount > 0 || this.noticeUnreadCount <= 0) return
      this.noticeMarkingAll = true
      try {
        await markNoticeReadAll()
        await this.loadNoticeTop()
      } catch (error) {
        // Preserve the last authoritative list and count when either request fails.
      } finally {
        if (!this.noticeDestroyed) this.noticeMarkingAll = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.header-notice-group {
  height: 100%;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.header-entry {
  position: relative;
  width: 34px;
  height: 40px;
  border: 0;
  padding: 0;
  color: #56595f;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 17px;
}
.header-entry:hover,
.header-entry:focus-visible { color: #25282c; background: #eef3f6; outline: none; }
.header-entry .svg-icon { width: 1.1em; height: 1.1em; vertical-align: -0.18em; }
.header-badge {
  position: absolute;
  top: 2px;
  right: -1px;
  color: #fff;
  border: 2px solid #fff;
  border-radius: 10px;
  font-size: 9px;
  height: 16px;
  line-height: 12px;
  padding: 0 3px;
  min-width: 16px;
  text-align: center;
  white-space: nowrap;
  pointer-events: none;
  box-sizing: border-box;
}
.notice-badge { background: #ef4444; }
.message-badge { background: #2866b1; }
.notice-popover { padding: 0 !important; }
.notice-popover:focus,
.notice-popover:focus-visible {
  outline: 2px solid rgba(63, 111, 143, .24);
  outline-offset: 1px;
}
.notice-popover .notice-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f5f8fa;
  border-bottom: 1px solid #e7e8ec;
  font-size: 13px;
  font-weight: 600;
  color: #25282c;
}
.notice-popover .notice-mark-all {
  padding: 0;
  border: 0;
  color: #3f6f8f;
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: normal;
  cursor: pointer;
}
.notice-popover .notice-mark-all:not(:disabled):hover { color: #315b78; }
.notice-popover .notice-mark-all:disabled { color: #747981; cursor: not-allowed; }
.notice-popover .notice-mark-all:focus-visible,
.notice-popover .notice-item:focus-visible {
  outline: 2px solid #3f6f8f;
  outline-offset: -2px;
}
.notice-popover .notice-loading,
.notice-popover .notice-empty { padding: 26px 24px; text-align: center; color: #5f636a; font-size: 12px; font-weight: 500; line-height: 1.8; }
.notice-popover .notice-empty i { color: #747981; font-size: 18px; }
.notice-popover .notice-item {
  display: flex;
  align-items: center;
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #edf0ee;
  cursor: pointer;
  transition: background .15s;
}
.notice-popover .notice-item:last-child { border-bottom: none; }
.notice-popover .notice-item:hover { background: #f3f6f8; }
.notice-popover .notice-item.is-read .notice-tag,
.notice-popover .notice-item.is-read .notice-item-title,
.notice-popover .notice-item.is-read .notice-item-date { opacity: .72; filter: grayscale(1); color: #666b73; }
.notice-popover .notice-tag { flex-shrink: 0; }
.notice-popover .notice-item-title { flex: 1; font-size: 12px; color: #2c2c2e; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.notice-popover .notice-item-date { flex-shrink: 0; font-size: 11px; color: #666b73; }
</style>
