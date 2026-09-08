<template>
  <el-dialog
    :title="dialogTitle"
    :visible="visible"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    :before-close="close"
  >
    <el-alert :title="warningText" type="warning" :closable="false" show-icon class="action-warning" />
    <el-form ref="form" :model="form" :rules="rules" label-width="104px">
      <template v-if="operation.type === 'reassign'">
        <el-form-item label="当前候选人" prop="fromCandidateId">
          <el-select v-model="form.fromCandidateId" placeholder="请选择要替换的待办候选人" style="width:100%">
            <el-option
              v-for="candidate in candidates"
              :key="candidate.candidateId"
              :label="candidateLabel(candidate)"
              :value="candidate.candidateId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新审批人编号" prop="toUserId">
          <el-input v-model.trim="form.toUserId" maxlength="32" placeholder="请输入有效用户编号" />
        </el-form-item>
      </template>
      <el-form-item label="操作原因" prop="reason">
        <el-input
          v-model.trim="form.reason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="必填；原因将永久写入审批审计日志"
        />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button :disabled="loading" @click="close">取消</el-button>
      <el-button type="danger" :loading="loading" @click="confirm">确认{{ actionLabel }}</el-button>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'ApprovalAdminActionDialog',
  props: {
    visible: { type: Boolean, default: false },
    operation: { type: Object, default: () => ({ type: '' }) },
    loading: { type: Boolean, default: false }
  },
  data() {
    const positiveId = (rule, value, callback) => {
      if (!/^\d+$/.test(String(value || '')) || Number(value) <= 0) callback(new Error('请输入有效用户编号'))
      else callback()
    }
    return {
      form: { fromCandidateId: '', toUserId: '', reason: '' },
      rules: {
        fromCandidateId: [{ required: true, message: '请选择要替换的待办候选人', trigger: 'change' }],
        toUserId: [{ validator: positiveId, trigger: 'blur' }],
        reason: [
          { required: true, message: '请输入操作原因', trigger: 'blur' },
          { min: 2, max: 500, message: '原因长度应为2至500个字符', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    candidates() {
      return Array.isArray(this.operation.candidates) ? this.operation.candidates : []
    },
    actionLabel() {
      return { terminate: '终止', reassign: '改派', replay: '重试' }[this.operation.type] || '操作'
    },
    dialogTitle() {
      return `管理员${this.actionLabel}`
    },
    warningText() {
      const labels = {
        terminate: '终止后当前审批轮次不可继续，请先确认业务单据和回调状态。',
        reassign: '改派只变更当前待办候选人，不修改已发布流程版本。',
        replay: '仅在业务侧故障已修复后重试；相同事件键必须保持幂等。'
      }
      return labels[this.operation.type] || '该操作会被完整审计。'
    }
  },
  watch: {
    visible(value) {
      if (!value) return
      this.form = {
        fromCandidateId: this.candidates.length === 1 ? this.candidates[0].candidateId : '',
        toUserId: '',
        reason: ''
      }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    }
  },
  methods: {
    candidateLabel(candidate) {
      const name = candidate.userName || candidate.candidateName || `用户${candidate.userId || '-'}`
      const dept = candidate.deptName ? ` · ${candidate.deptName}` : ''
      return `${name}${dept}（候选记录${candidate.candidateId}）`
    },
    close(done) {
      if (this.loading) return
      this.$emit('update:visible', false)
      if (typeof done === 'function') done()
    },
    confirm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const target = this.operation.target || {}
        const targetLabel = target.businessNo || target.title || target.taskName || target.id || ''
        const message = `再次确认${this.actionLabel}${targetLabel ? `“${targetLabel}”` : '当前对象'}？此操作将写入审计日志。`
        this.$confirm(message, `确认${this.actionLabel}`, {
          confirmButtonText: `确认${this.actionLabel}`,
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.$emit('confirm', {
            type: this.operation.type,
            target: this.operation.target,
            fromCandidateId: this.form.fromCandidateId || undefined,
            toUserId: this.form.toUserId || undefined,
            reason: this.form.reason
          })
        }).catch(() => {})
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.action-warning { margin-bottom: 18px; }
</style>
