<template>
  <section v-if="nativeRuntime && !preview" class="native-push-status mobile-system-panel" aria-label="App 消息提醒">
    <div>
      <strong>App 消息提醒</strong>
      <p role="status" aria-live="polite">{{ statusMessage }}</p>
      <small v-if="status.reason === 'permission-denied'">请在手机系统设置中找到本 App，开启通知权限，再返回这里重新检查。</small>
    </div>
    <button type="button" :disabled="retrying" @click="retryRegistration">{{ retrying ? '检查中…' : '重新检查' }}</button>
  </section>
</template>

<script>
import pushRegistration from '@/services/lazyPushRegistration'

const STATUS_MESSAGES = {
  idle: '正在检查消息提醒状态。',
  checking: '正在检查通知权限。',
  'waiting-token': '通知权限已开启，正在连接消息服务。',
  registering: '正在连接消息服务。',
  ready: '此设备已登记接收当前账号的消息。',
  disabled: '当前账号的消息提醒已关闭。'
}

export default {
  name: 'NativePushStatus',
  props: { preview: Boolean },
  data() {
    return {
      nativeRuntime: pushRegistration.isNativePlatform(),
      status: { phase: 'idle', reason: null },
      retrying: false
    }
  },
  computed: {
    statusMessage() {
      if (this.status.reason === 'permission-denied') return '尚未开启系统通知权限，锁屏时无法提醒。'
      if (this.status.reason === 'channel-unavailable') return '系统消息提醒设置未能完成，请重新检查。'
      return STATUS_MESSAGES[this.status.phase] || '消息服务尚未连接成功，请检查网络后重试。'
    }
  },
  created() {
    this.disposed = false
    this.unsubscribePushStatus = null
    if (!this.nativeRuntime || this.preview) return
    pushRegistration.subscribeStatus(status => { this.status = status }).then(unsubscribe => {
      if (this.disposed) unsubscribe()
      else this.unsubscribePushStatus = unsubscribe
    }).catch(() => { this.status = { phase: 'error', reason: 'service-unavailable' } })
  },
  beforeDestroy() {
    this.disposed = true
    if (this.unsubscribePushStatus) this.unsubscribePushStatus()
  },
  methods: {
    async retryRegistration() {
      if (this.retrying || this.preview) return
      this.retrying = true
      try {
        await pushRegistration.initialize(this.$store.getters.id, { retry: true })
        await pushRegistration.resumeNavigation(this.$store.getters.id)
      } catch (error) {
        this.status = { phase: 'error', reason: 'service-unavailable' }
      } finally {
        this.retrying = false
      }
    }
  }
}
</script>

<style scoped>
.native-push-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  margin-bottom: 16px;
  border-radius: 16px;
  background: #fff;
}
.native-push-status p { margin: 8px 0; color: #475569; line-height: 1.6; }
.native-push-status small { display: block; color: #92400e; line-height: 1.6; }
.native-push-status button {
  flex-shrink: 0;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  color: #1d4ed8;
  background: #eff6ff;
}
.native-push-status button:disabled { opacity: .6; }
</style>
