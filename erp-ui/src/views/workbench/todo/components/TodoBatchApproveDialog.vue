<template>
  <el-dialog
    title="批量通过调拨审批"
    :visible="visible"
    width="760px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!executing"
    :before-close="requestClose"
  >
    <div class="batch-approve-dialog" :aria-busy="executing ? 'true' : 'false'">
      <el-alert
        title="每条审批独立提交；部分失败不会撤销已经成功的审批。"
        type="warning"
        :closable="false"
        show-icon
        class="batch-approve-notice"
      />
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="batch-approve-notice"
      />

      <div v-if="executing || results.length" class="batch-progress" aria-live="polite">
        <strong>已处理 {{ processed }}/{{ total }}</strong>
        <el-progress :percentage="progressPercentage" :status="executing ? undefined : progressStatus" />
      </div>

      <el-table :data="displayItems" size="small" border max-height="320" empty-text="没有待处理项目">
        <el-table-column type="index" label="#" width="48" />
        <el-table-column label="单号" min-width="170">
          <template slot-scope="scope">{{ scope.row.businessNo || '未记录单号' }}</template>
        </el-table-column>
        <el-table-column label="调拨方向" min-width="230">
          <template slot-scope="scope">{{ direction(scope.row.row) }}</template>
        </el-table-column>
        <el-table-column v-if="results.length" label="结果" min-width="190">
          <template slot-scope="scope">
            <el-tag size="mini" :type="resultType(scope.row.status)">{{ resultLabel(scope.row.status) }}</el-tag>
            <span class="batch-result-message">{{ scope.row.message || defaultResultMessage(scope.row) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-form v-if="!results.length" label-position="top" class="batch-approve-form" @submit.native.prevent>
        <el-form-item label="统一审批意见（选填）">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="通过意见可不填写，将应用到本批全部单据"
            :disabled="executing"
          />
        </el-form-item>
      </el-form>
    </div>

    <span slot="footer" class="dialog-footer batch-approve-footer">
      <el-button v-if="executing" type="warning" plain @click="$emit('stop')">停止未开始项</el-button>
      <el-button :disabled="executing" @click="requestClose">{{ results.length ? '关闭' : '取消' }}</el-button>
      <el-button
        v-if="canRetry"
        type="warning"
        plain
        :disabled="executing"
        @click="$emit('retry-failed', { comment })"
      >重试未成功项</el-button>
      <el-button
        v-if="!results.length"
        type="primary"
        :loading="executing"
        :disabled="!items.length"
        @click="$emit('confirm', { comment: normalizedComment })"
      >确认批量通过 {{ items.length }} 条</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'TodoBatchApproveDialog',
  props: {
    visible: { type: Boolean, default: false },
    items: { type: Array, default: () => [] },
    executing: { type: Boolean, default: false },
    processed: { type: Number, default: 0 },
    total: { type: Number, default: 0 },
    results: { type: Array, default: () => [] },
    errorMessage: { type: String, default: '' }
  },
  data() {
    return { comment: '' }
  },
  computed: {
    normalizedComment() {
      return String(this.comment || '').trim()
    },
    displayItems() {
      return this.results.length ? this.results : this.items
    },
    progressPercentage() {
      return this.total > 0 ? Math.min(100, Math.round(this.processed * 100 / this.total)) : 0
    },
    successCount() {
      return this.results.filter(item => item.status === 'SUCCESS' || item.status === 'ALREADY_HANDLED').length
    },
    canRetry() {
      return this.results.some(item =>
        (item.status === 'FAILED' && item.errorKind !== 'SESSION') ||
        (item.status === 'CANCELED' && item.errorKind === 'USER_CANCELED')
      )
    },
    progressStatus() {
      return this.results.length && this.successCount === this.results.length ? 'success' : 'warning'
    }
  },
  watch: {
    visible(value) {
      if (value && !this.results.length) this.comment = ''
    }
  },
  methods: {
    direction(row) {
      const source = row || {}
      return `${source.fromDeptName || source.routeParams && source.routeParams.fromDeptName || '调出组织'} → ${source.toDeptName || source.contextDeptName || '调入组织'}`
    },
    resultType(status) {
      if (status === 'SUCCESS' || status === 'ALREADY_HANDLED') return 'success'
      if (status === 'CANCELED') return 'info'
      return 'danger'
    },
    resultLabel(status) {
      return {
        SUCCESS: '成功',
        ALREADY_HANDLED: '已处理',
        FAILED: '未成功',
        CANCELED: '未开始',
        PENDING: '等待中',
        RUNNING: '处理中'
      }[status] || '结果未知'
    },
    defaultResultMessage(item) {
      if (item.status === 'SUCCESS') return '审批通过'
      if (item.status === 'ALREADY_HANDLED') return '服务端状态显示该事项已处理'
      if (item.status === 'CANCELED') return '登录状态失效，未开始处理'
      return '请刷新后确认状态'
    },
    requestClose() {
      if (this.executing) return
      this.$emit('update:visible', false)
      this.$emit('close')
    }
  }
}
</script>

<style lang="scss" scoped>
.batch-approve-dialog { min-height: 260px; }
.batch-approve-notice { margin-bottom: 14px; }
.batch-progress { margin: 0 0 14px; }
.batch-progress strong { display: block; margin-bottom: 8px; color: #334155; }
.batch-approve-form { margin-top: 16px; }
.batch-result-message { margin-left: 7px; color: #64748b; font-size: 12px; }
.batch-approve-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
