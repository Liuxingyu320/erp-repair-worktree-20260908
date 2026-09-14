<template>
  <div class="mobile-messages-page mobile-system-page">
    <header class="mobile-messages-header mobile-system-topbar">
      <button type="button" @click="$router.push('/mobile/mine').catch(() => {})">返回我的</button>
      <router-link to="/mobile/profile">消息提醒设置</router-link>
    </header>
    <user-notification />
  </div>
</template>

<script>
import UserNotification from '@/views/system/userNotification/index.vue'

export default {
  name: 'MobileMessages',
  components: { UserNotification }
}
</script>

<style scoped lang="scss">
.mobile-messages-page {
  min-height: var(--mobile-viewport-height, 100vh);
  padding-bottom: calc(20px + var(--mobile-safe-bottom, 0px));
  background: #f4f7fb;
}
.mobile-messages-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: calc(12px + var(--mobile-safe-top, 0px)) 16px 12px;
  button, a { min-height: 44px; display: inline-flex; align-items: center; }
  button { border: 0; background: transparent; color: #2563eb; }
}
.mobile-messages-page ::v-deep .user-notification-page { padding: 12px; min-height: 0; }
.mobile-messages-page ::v-deep .message-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.mobile-messages-page ::v-deep .message-row { min-height: 76px; padding: 14px 8px; }
.mobile-messages-page ::v-deep .message-content { overflow-wrap: anywhere; }
.mobile-messages-page ::v-deep .message-content span { white-space: normal; overflow: visible; text-overflow: clip; }
.mobile-messages-page ::v-deep .message-meta { min-width: 0; font-size: 11px; max-width: 95px; }
.mobile-messages-page ::v-deep .el-card__body { padding: 8px 12px; }
</style>
