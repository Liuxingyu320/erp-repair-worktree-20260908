<template>
  <el-dialog
    :title="dialogTitle"
    :visible="visible"
    width="min(560px, 94vw)"
    append-to-body
    :close-on-click-modal="false"
    :before-close="close"
  >
    <el-alert :title="warningText" type="warning" :closable="false" show-icon class="action-warning" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="action-warning" />
    <el-alert v-if="operation.recovery" title="上次改派结果待确认；已保留原接收人和原因，请重试核实原请求。" type="info" :closable="false" class="action-warning" />
    <el-form ref="form" :model="form" :rules="rules" label-width="104px" :disabled="loading || confirming || !!operation.recovery">
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
        <el-form-item label="新审批人" prop="toUserId">
          <el-select v-model="form.toUserId" filterable remote clearable :remote-method="searchRecipients" :loading="recipientLoading"
            placeholder="搜索姓名、部门或账号" style="width:100%" @change="selectRecipient">
            <el-option v-for="person in recipientOptions" :key="person.userId" :value="person.userId"
              :label="recipientLabel(person)" :disabled="!recipientReady" />
          </el-select>
          <div v-if="recipientError" role="alert" class="recipient-error">{{ recipientError }} <el-button type="text" @click="loadRecipients(recipientPage + 1)">重试</el-button></div>
          <el-button v-else-if="recipientHasMore" type="text" :disabled="recipientLoading" @click="loadRecipients(recipientPage + 1)">加载更多人员</el-button>
          <div v-if="selectedRecipient" class="recipient-summary">接收人：{{ recipientLabel(selectedRecipient) }}</div>
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
      <el-button :disabled="loading || confirming" @click="close">取消</el-button>
      <el-button type="danger" :loading="loading || confirming" @click="confirm">{{ operation.recovery ? '重试原改派' : `确认${actionLabel}` }}</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { listApprovalReassignOptions } from '@/api/approval/monitor'
import { getSelectedDeptId } from '@/utils/shopContext'

function positiveId(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return ''
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const text = String(value).trim().replace(/^0+/, '')
  return /^[1-9]\d*$/.test(text) && (text.length < 19 || (text.length === 19 && text <= '9223372036854775807')) ? text : ''
}

export default {
  name: 'ApprovalAdminActionDialog',
  props: {
    visible: { type: Boolean, default: false },
    operation: { type: Object, default: () => ({ type: '' }) },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' }
  },
  data() {
    const selectedUser = (rule, value, callback) => {
      if (!positiveId(value) || !this.selectedRecipient || this.selectedRecipient.userId !== value || !this.recipientReady) callback(new Error('请搜索并选择可改派的审批人'))
      else callback()
    }
    return {
      form: { fromCandidateId: '', toUserId: '', reason: '' },
      recipientOptions: [], selectedRecipient: null, recipientLoading: false,
      recipientReady: false, recipientError: '', recipientKeyword: '', recipientPage: 0,
      recipientHasMore: false, readEpoch: 0, actionEpoch: 0, confirming: false, resettingAction: false,
      rules: {
        fromCandidateId: [{ required: true, message: '请选择要替换的待办候选人', trigger: 'change' }],
        toUserId: [{ validator: selectedUser, trigger: 'change' }],
        reason: [
          { required: true, message: '请输入操作原因', trigger: 'blur' },
          { min: 2, max: 500, message: '原因长度应为2至500个字符', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    candidates() {
      return (Array.isArray(this.operation.candidates) ? this.operation.candidates : [])
        .map(candidate => ({ ...candidate, candidateId: positiveId(candidate.candidateId) }))
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
      if (value) this.resetAction()
      else this.invalidateAction()
    },
    'operation.contextKey'() { if (this.visible) this.resetAction() },
    'form.fromCandidateId'() { if (this.visible && !this.operation.recovery && !this.resettingAction) this.searchRecipients('') },
    '$store.getters.id'() { this.contextChanged() },
    '$route.fullPath'() { this.contextChanged() }
  },
  mounted() { window.addEventListener('erp:dept-changed', this.contextChanged) },
  beforeDestroy() {
    this.invalidateAction()
    window.removeEventListener('erp:dept-changed', this.contextChanged)
  },
  methods: {
    actorScope() { return `${String(this.$store.getters.id || '')}:${String(getSelectedDeptId() || '')}` },
    contextChanged() { this.invalidateAction(); this.$emit('update:visible', false) },
    invalidateAction() {
      this.actionEpoch += 1
      this.readEpoch += 1
      this.recipientReady = false
      this.recipientLoading = false
      this.confirming = false
    },
    resetAction() {
      this.invalidateAction()
      this.resettingAction = true
      const epoch = this.actionEpoch
      this.recipientOptions = []
      this.selectedRecipient = null
      this.recipientError = ''
      this.recipientKeyword = ''
      this.recipientPage = 0
      this.recipientHasMore = false
      const recovery = this.operation.recovery
      this.form = recovery ? { fromCandidateId: recovery.body.fromCandidateId, toUserId: recovery.body.toUserId, reason: recovery.body.reason }
        : { fromCandidateId: this.candidates.length === 1 ? this.candidates[0].candidateId : '', toUserId: '', reason: '' }
      if (recovery) {
        this.selectedRecipient = { ...recovery.recipient }
        this.recipientOptions = [this.selectedRecipient]
        this.recipientReady = true
      }
      this.$nextTick(() => {
        if (epoch !== this.actionEpoch) return
        this.resettingAction = false
        if (this.$refs.form) this.$refs.form.clearValidate()
        if (this.visible && !recovery) this.searchRecipients('')
      })
    },
    recipientLabel(person) { return `${person.userName || '姓名未配置'} · ${person.deptName || '部门未配置'} · ${person.accountName || '账号未配置'}` },
    selectRecipient(id) {
      this.selectedRecipient = this.recipientReady ? this.recipientOptions.find(person => person.userId === id) || null : null
    },
    searchRecipients(keyword) {
      this.recipientKeyword = String(keyword || '').trim().slice(0, 64)
      this.recipientPage = 0
      this.recipientOptions = []
      this.selectedRecipient = null
      this.form.toUserId = ''
      return this.loadRecipients(1)
    },
    loadRecipients(pageNum) {
      const epoch = ++this.readEpoch, actionEpoch = this.actionEpoch
      const scope = this.actorScope(), contextKey = this.operation.contextKey
      const target = this.operation.target || {}, taskId = positiveId(target.taskId || target.id)
      const fromCandidateId = positiveId(this.form.fromCandidateId)
      this.recipientReady = false
      this.recipientError = ''
      if (!this.visible || this.operation.type !== 'reassign' || this.operation.recovery || !taskId || !fromCandidateId) return Promise.resolve()
      this.recipientLoading = true
      const current = () => this.visible && epoch === this.readEpoch && actionEpoch === this.actionEpoch
        && scope === this.actorScope() && contextKey === this.operation.contextKey && fromCandidateId === this.form.fromCandidateId
      return listApprovalReassignOptions(taskId, { fromCandidateId, keyword: this.recipientKeyword, pageNum }).then(response => {
        if (!current()) return
        const data = response.data || {}
        if (String(data.taskId) !== taskId || String(data.instanceId) !== String(target.instanceId)
          || String(data.fromCandidateId) !== fromCandidateId || !Array.isArray(data.rows)) throw new Error('候选人上下文不一致，请重新打开改派')
        const next = data.rows.map(person => ({ ...person, userId: positiveId(person.userId) })).filter(person => person.userId)
        this.recipientOptions = pageNum === 1 ? next : this.recipientOptions.concat(next)
        this.recipientPage = pageNum
        this.recipientHasMore = data.hasMore === true
        this.recipientReady = true
      }).catch(error => {
        if (!current()) return
        this.recipientError = error.message || '候选人加载失败，请重试'
        this.recipientReady = false
      }).finally(() => { if (current()) this.recipientLoading = false })
    },
    candidateLabel(candidate) {
      const name = candidate.userName || candidate.candidateName || `用户${candidate.userId || '-'}`
      const dept = candidate.deptName ? ` · ${candidate.deptName}` : ''
      return `${name}${dept}（候选记录${candidate.candidateId}）`
    },
    close(done) {
      if (this.loading || this.confirming) return
      this.invalidateAction()
      this.$emit('update:visible', false)
      if (typeof done === 'function') done()
    },
    confirm() {
      if (this.loading || this.confirming) return Promise.resolve()
      this.confirming = true
      const epoch = this.actionEpoch, scope = this.actorScope()
      const frozen = JSON.stringify({ operation: this.operation, form: this.form, recipient: this.selectedRecipient })
      return new Promise(resolve => this.$refs.form.validate(resolve)).then(valid => {
        if (!valid) return
        if (this.operation.type === 'reassign' && (!this.recipientReady || !this.selectedRecipient)) return
        const target = this.operation.target || {}
        const targetLabel = target.businessNo || target.title || target.taskName || target.id || ''
        const recipient = this.operation.type === 'reassign' ? `，接收人：${this.recipientLabel(this.selectedRecipient)}` : ''
        const message = `再次确认${this.actionLabel}${targetLabel ? `“${targetLabel}”` : '当前对象'}${recipient}？此操作将写入审计日志。`
        return this.$confirm(message, `确认${this.actionLabel}`, {
          confirmButtonText: `确认${this.actionLabel}`,
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          if (!this.visible || epoch !== this.actionEpoch || scope !== this.actorScope()
            || frozen !== JSON.stringify({ operation: this.operation, form: this.form, recipient: this.selectedRecipient })) return
          this.$emit('confirm', {
            type: this.operation.type,
            target: this.operation.target,
            fromCandidateId: this.form.fromCandidateId || undefined,
            toUserId: this.form.toUserId || undefined,
            reason: this.form.reason,
            recipient: this.selectedRecipient ? { ...this.selectedRecipient } : undefined,
            contextKey: this.operation.contextKey
          })
        }).catch(() => {})
      }).finally(() => { if (epoch === this.actionEpoch) this.confirming = false })
    }
  }
}
</script>

<style lang="scss" scoped>
.action-warning { margin-bottom: 18px; }
.recipient-error { color: #c45656; line-height: 1.5; }
.recipient-summary { margin-top: 8px; line-height: 1.5; overflow-wrap: anywhere; }
</style>
