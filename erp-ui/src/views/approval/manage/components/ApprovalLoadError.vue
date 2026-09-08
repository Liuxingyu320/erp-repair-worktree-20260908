<template>
  <div class="approval-load-error" role="alert">
    <i class="el-icon-warning-outline"></i>
    <div class="approval-load-error__copy">
      <strong>{{ title }}</strong>
      <span>{{ error.message }}</span>
      <small v-if="error.diagnostic">{{ error.diagnostic }}</small>
    </div>
    <el-button
      type="primary"
      plain
      size="small"
      icon="el-icon-refresh"
      :loading="loading"
      @click="$emit('retry')"
    >重试</el-button>
  </div>
</template>

<script>
export default {
  name: 'ApprovalLoadError',
  props: {
    title: { type: String, default: '数据加载失败' },
    error: { type: Object, required: true },
    loading: { type: Boolean, default: false }
  }
}
</script>

<style lang="scss" scoped>
.approval-load-error {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 15px;
  border: 1px solid #fbc4c4;
  border-radius: 10px;
  background: #fef0f0;
  color: #606266;
}

.approval-load-error > i {
  color: #f56c6c;
  font-size: 22px;
}

.approval-load-error__copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.approval-load-error__copy strong { color: #c45656; }
.approval-load-error__copy small { color: #909399; }
</style>
