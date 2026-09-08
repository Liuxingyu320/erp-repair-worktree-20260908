<template>
  <section class="quota-panel" aria-label="容量总览">
    <div v-if="value" class="capacity-cards">
      <article><span>已提交文件</span><strong>{{ formatBytes(value.actualUsedBytes) }}</strong></article>
      <article><span>上传中 / 待清理</span><strong>{{ formatBytes(value.pendingUploadBytes) }}</strong></article>
      <article><span>安全计入总量</span><strong>{{ formatBytes(value.capacityAccountedBytes == null ? value.actualUsedBytes : value.capacityAccountedBytes) }}</strong></article>
      <article><span>可分配容量</span><strong>{{ value.allocatableCapacityBytes == null ? '待确认' : formatBytes(value.allocatableCapacityBytes) }}</strong></article>
      <article><span>当前剩余可上传</span><strong>{{ remainingUploadBytes == null ? '待确认' : formatBytes(remainingUploadBytes) }}</strong></article>
      <article><span>执行模式</span><strong>{{ value.enforcementMode === 'BLOCK' ? '强制拦截' : '仅告警' }}</strong></article>
    </div>
    <el-alert
      v-if="value && Number(value.cleanupFailedReservationCount || 0) > 0"
      :title="`存在 ${Number(value.cleanupFailedReservationCount)} 条上传对象清理失败记录；容量仍安全保留，系统会继续重试。`"
      type="error"
      show-icon
      :closable="false"
      class="quota-panel__alert"
    />
    <el-alert
      v-for="warning in (value && value.warnings) || []"
      :key="warning"
      :title="warning"
      type="warning"
      show-icon
      :closable="false"
      class="quota-panel__alert"
    />
    <el-form label-width="150px" class="quota-form" @submit.native.prevent>
      <el-form-item label="物理可用容量（GiB）">
        <el-input v-model.trim="form.physicalGiB" placeholder="未确认时留空，只能使用告警模式" />
      </el-form-item>
      <el-form-item label="安全保留比例">
        <el-input-number v-model="form.reservePercent" :min="0" :max="90" /> <span class="unit">%</span>
      </el-form-item>
      <el-form-item label="公司公共盘池（GiB）"><el-input v-model.trim="form.publicGiB" /></el-form-item>
      <el-form-item label="个人盘池（GiB）"><el-input v-model.trim="form.personalGiB" /></el-form-item>
      <el-form-item label="组织盘池（GiB）"><el-input v-model.trim="form.organizationGiB" /></el-form-item>
      <el-form-item label="执行模式">
        <el-radio-group v-model="form.enforcementMode">
          <el-radio label="WARN">只告警</el-radio>
          <el-radio label="BLOCK" :disabled="!reservationEnabled">超额时强制拦截</el-radio>
        </el-radio-group>
        <p v-if="!reservationEnabled" class="form-help">请先在运行环境开启上传容量预占，再启用强制拦截。</p>
      </el-form-item>
      <el-form-item label="调整原因"><el-input v-model.trim="form.reason" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">预览影响并保存</el-button>
      </el-form-item>
    </el-form>
  </section>
</template>

<script>
const { formatBytes } = require('../../driveState')
const { bytesToGiB, gibToBytes } = require('../../quotaState')

export default {
  name: 'DriveCapacityPanel',
  props: {
    value: { type: Object, default: null },
    saving: { type: Boolean, default: false },
    reservationEnabled: { type: Boolean, default: false }
  },
  data() {
    return { form: { physicalGiB: '', reservePercent: 20, publicGiB: '0', personalGiB: '0', organizationGiB: '0', enforcementMode: 'WARN', reason: '' } }
  },
  computed: {
    remainingUploadBytes() {
      if (!this.value || this.value.allocatableCapacityBytes == null) return null
      const accounted = Number(this.value.capacityAccountedBytes == null
        ? this.value.actualUsedBytes || 0
        : this.value.capacityAccountedBytes)
      return Math.max(0, Number(this.value.allocatableCapacityBytes || 0) - accounted)
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(value) {
        if (!value) return
        this.form = {
          physicalGiB: value.physicalCapacityBytes == null ? '' : String(bytesToGiB(value.physicalCapacityBytes)),
          reservePercent: Number(value.reservePercent == null ? 20 : value.reservePercent),
          publicGiB: String(bytesToGiB(value.publicPoolBytes)),
          personalGiB: String(bytesToGiB(value.personalPoolBytes)),
          organizationGiB: String(bytesToGiB(value.organizationPoolBytes)),
          enforcementMode: value.enforcementMode || 'WARN',
          reason: ''
        }
      }
    }
  },
  methods: {
    formatBytes,
    submit() {
      const physical = this.form.physicalGiB === '' ? null : gibToBytes(this.form.physicalGiB, false)
      const publicPool = gibToBytes(this.form.publicGiB, true)
      const personalPool = gibToBytes(this.form.personalGiB, true)
      const organizationPool = gibToBytes(this.form.organizationGiB, true)
      if ((this.form.physicalGiB !== '' && physical == null) || [publicPool, personalPool, organizationPool].includes(null)) {
        this.$message.error('容量请输入有效 GiB 数值，最多三位小数')
        return
      }
      if (!this.form.reason) { this.$message.error('请填写调整原因'); return }
      if (this.form.enforcementMode === 'BLOCK' && !this.reservationEnabled) { this.$message.error('开启强制拦截前必须先启用上传容量预占'); return }
      if (this.form.enforcementMode === 'BLOCK' && physical == null) { this.$message.error('强制拦截前必须确认物理容量'); return }
      const data = {
        physicalCapacityBytes: physical,
        reservePercent: this.form.reservePercent,
        publicPoolBytes: publicPool,
        personalPoolBytes: personalPool,
        organizationPoolBytes: organizationPool,
        enforcementMode: this.form.enforcementMode,
        version: Number(this.value && this.value.version || 0),
        reason: this.form.reason
      }
      this.$emit('save-request', { kind: 'capacity', data, impact: { changeType: 'CAPACITY', ...data } })
    }
  }
}
</script>

<style lang="scss" scoped>
.capacity-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 18px; }
.capacity-cards article { padding: 16px; border: 1px solid #e6ebf2; border-radius: 12px; background: #fafcff; }
.capacity-cards span { display: block; color: #8795a8; font-size: 12px; }
.capacity-cards strong { display: block; margin-top: 7px; color: #243447; font-size: 20px; }
.quota-panel__alert { margin-bottom: 8px; }
.quota-form { max-width: 680px; margin-top: 22px; }
.unit { margin-left: 6px; color: #7b8794; }
.form-help { margin: 6px 0 0; color: #8795a8; font-size: 12px; }
@media (max-width: 800px) { .capacity-cards { grid-template-columns: 1fr; } }
</style>
