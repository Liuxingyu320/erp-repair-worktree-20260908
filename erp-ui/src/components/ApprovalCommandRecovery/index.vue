<template>
  <section v-if="message || reason" class="approval-recovery" role="alert">
    <p v-if="message">{{ message }}</p>
    <p v-if="reason">已保留审批意见：{{ reason }}</p>
    <template v-if="unknown">
      <button type="button" :disabled="busy" @click="$emit('check')">{{ checking ? '正在核对…' : checkLabel }}</button>
      <button type="button" :disabled="busy || !checked" @click="$emit('retry')">{{ retryLabel }}</button>
    </template>
  </section>
</template>
<script>
export default {
  name: 'ApprovalCommandRecovery',
  props: { message: String, reason: String, unknown: Boolean, busy: Boolean, checking: Boolean, checked: Boolean, checkLabel: { type: String, default: "核对审批结果" }, retryLabel: { type: String, default: "重试原审批动作" } }
}
</script>
<style scoped>
.approval-recovery{margin:12px 0;padding:12px;border:1px solid #e6a23c;border-radius:8px;background:#fffaf1;color:#704b11}.approval-recovery p{margin:4px 0;line-height:1.6;overflow-wrap:anywhere}.approval-recovery button{min-height:40px;margin:6px 8px 0 0;padding:7px 12px;border:1px solid #c89343;border-radius:6px;background:#fff;color:#704b11}.approval-recovery button:disabled{opacity:.5}
</style>
