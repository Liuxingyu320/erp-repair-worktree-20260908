<template>
  <el-dialog
    title="确认额度影响"
    :visible="visible"
    width="560px"
    append-to-body
    :close-on-click-modal="false"
    @close="$emit('cancel')"
  >
    <div v-if="impact" class="quota-impact">
      <p class="quota-impact__summary">{{ impactSummary(impact) }}</p>
      <dl>
        <div><dt>修改前逻辑分配</dt><dd>{{ formatBytes(impact.beforeAllocatedBytes) }}</dd></div>
        <div><dt>修改后逻辑分配</dt><dd>{{ formatBytes(impact.afterAllocatedBytes) }}</dd></div>
        <div><dt>将超额的空间</dt><dd :class="{ 'is-danger': impact.overQuotaCount }">{{ impact.overQuotaCount || 0 }} 个</dd></div>
        <div><dt>需要清理</dt><dd :class="{ 'is-danger': impact.overQuotaBytes }">{{ formatBytes(impact.overQuotaBytes) }}</dd></div>
      </dl>
      <el-alert
        v-for="warning in impact.warnings || []"
        :key="warning"
        class="quota-impact__warning"
        type="warning"
        :title="warning"
        :closable="false"
        show-icon
      />
      <p class="quota-impact__note">确认时会再次核对人员、组织、用量和版本；数据变化后本次确认自动失效。</p>
    </div>
    <div slot="footer">
      <el-button :disabled="saving" @click="$emit('cancel')">返回修改</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('confirm')">确认并保存</el-button>
    </div>
  </el-dialog>
</template>

<script>
const { formatBytes } = require('../../driveState')
const { impactSummary } = require('../../quotaState')

export default {
  name: 'DriveQuotaImpactDialog',
  props: {
    visible: { type: Boolean, default: false },
    impact: { type: Object, default: null },
    saving: { type: Boolean, default: false }
  },
  methods: { formatBytes, impactSummary }
}
</script>

<style lang="scss" scoped>
.quota-impact__summary { margin: 0 0 16px; color: #1f2d3d; font-size: 16px; font-weight: 600; }
.quota-impact dl { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 0 0 16px; }
.quota-impact dl div { padding: 12px; border-radius: 9px; background: #f6f8fb; }
.quota-impact dt { color: #8795a8; font-size: 12px; }
.quota-impact dd { margin: 5px 0 0; color: #243447; font-weight: 600; }
.quota-impact .is-danger { color: #d93025; }
.quota-impact__warning { margin-top: 8px; }
.quota-impact__note { margin: 14px 0 0; color: #7b8794; font-size: 12px; line-height: 1.6; }
</style>
