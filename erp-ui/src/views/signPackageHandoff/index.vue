<template>
  <main class="app-container sign-handoff">
    <el-card shadow="never">
      <h2>手机继续签约</h2>
      <p v-if="loading">正在核对当前账号的签约包…</p>
      <div v-else-if="error" role="alert">
        <p>{{ error }}</p><el-button @click="loadPackage">重试</el-button>
      </div>
      <template v-else-if="signPackage">
        <p>签约包 {{ signPackage.packageNo || packageId }} · {{ statusLabel }}</p>
        <template v-if="canContinue">
          <p>使用手机扫描二维码，登录本人账号后继续核对与签署。</p>
          <img v-if="qrImage" :src="qrImage" width="232" height="232" alt="使用手机打开本人的签约包" />
          <p v-else>二维码暂未生成，可复制下方链接在手机打开。</p>
          <el-input ref="linkInput" :value="mobileUrl" readonly aria-label="受保护的手机签约链接" />
          <el-button class="copy-button" @click="copyLink">复制手机链接</el-button>
          <p v-if="copyMessage" role="status">{{ copyMessage }}</p>
        </template>
        <p v-else>当前状态无需继续签名；请按签约状态等待处理或联系经办人。</p>
        <el-button type="text" @click="loadPackage">刷新签约状态</el-button>
      </template>
      <el-button type="text" @click="$router.back()">返回消息</el-button>
    </el-card>
  </main>
</template>

<script>
import qrcode from 'qrcode-generator'
import { getMySignPackage } from '@/api/oa/signPackage'
import { signPackageStatusLabel } from '@/utils/signDictionary'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { normalizePackageId, createHandoffUrl } = require('@/utils/signPackageHandoff')
const { getSelectedDeptId } = require('@/utils/shopContext')

export default {
  name: 'SignPackageHandoff',
  data() { return { signPackage: null, loading: false, error: '', qrImage: '', copyMessage: '', contextRevision: 0 } },
  computed: {
    packageId() { return normalizePackageId(this.$route.query.packageId) },
    statusLabel() { return signPackageStatusLabel(this.signPackage && this.signPackage.status) },
    canContinue() { return this.signPackage && ['pending_sign', 'part_viewed', 'pending_final_confirm'].includes(this.signPackage.status) },
    mobileUrl() { return this.canContinue ? createHandoffUrl(window.location.origin, this.packageId) : '' }
  },
  watch: {
    '$route.query.packageId'() { this.reloadContext() },
    '$store.getters.id'() { this.reloadContext() },
    '$store.getters.token'() { this.reloadContext() }
  },
  created() {
    this._contextChanged = () => this.reloadContext()
    window.addEventListener('erp:dept-changed', this._contextChanged)
    this.loadPackage()
  },
  beforeDestroy() {
    window.removeEventListener('erp:dept-changed', this._contextChanged)
    this.operationScope().deactivate()
  },
  methods: {
    operationScope() {
      if (!this._scope) this._scope = createUiOperationScope(() => ({
        actorId: String(this.$store.getters.id || ''), deptId: String(getSelectedDeptId() || ''),
        packageId: this.packageId, revision: this.contextRevision
      }))
      return this._scope
    },
    reloadContext() { this.contextRevision += 1; this.operationScope().invalidate(); this.loadPackage() },
    async loadPackage() {
      const scope = this.operationScope(), operation = scope.begin('detail'), packageId = this.packageId
      this.signPackage = null; this.qrImage = ''; this.error = ''; this.copyMessage = ''; this.loading = false
      if (!packageId) { this.error = '签约包编号无效，请返回原消息核对。'; return }
      this.loading = true
      try {
        const response = await getMySignPackage(packageId)
        if (!scope.isCurrent(operation)) return
        const value = response && response.data
        if (!value || normalizePackageId(value.packageId) !== packageId) throw Error('签约包信息未确认，请重试。')
        // Keep only display fields; private documents and identity fields never enter the QR payload.
        this.signPackage = { packageId, packageNo: value.packageNo, status: value.status }
        if (this.mobileUrl) {
          try { const qr = qrcode(0, 'M'); qr.addData(this.mobileUrl); qr.make(); this.qrImage = qr.createDataURL(6, 8) }
          catch (error) { this.qrImage = '' }
        }
      } catch (error) {
        if (scope.isCurrent(operation)) this.error = '暂时无法读取本人的签约包，请重试或联系经办人。'
      } finally { if (scope.isCurrent(operation)) this.loading = false }
    },
    async copyLink() {
      const url = this.mobileUrl, scope = this.operationScope(), operation = scope.begin('copy')
      if (!url) return
      try {
        if (!navigator.clipboard || !navigator.clipboard.writeText) throw Error('clipboard unavailable')
        await navigator.clipboard.writeText(url)
        if (scope.isCurrent(operation)) this.copyMessage = '手机链接已复制。'
      } catch (error) {
        if (!scope.isCurrent(operation)) return
        if (this.$refs.linkInput) this.$refs.linkInput.select()
        this.copyMessage = '请复制已选中的链接，在手机浏览器中打开。'
      }
    }
  }
}
</script>

<style scoped>
.sign-handoff { max-width: 620px; margin: 24px auto; }
.sign-handoff h2 { margin-top: 0; }
.sign-handoff img { display: block; margin: 16px auto; }
.sign-handoff p { line-height: 1.7; }
.copy-button { margin: 12px 0; }
</style>
