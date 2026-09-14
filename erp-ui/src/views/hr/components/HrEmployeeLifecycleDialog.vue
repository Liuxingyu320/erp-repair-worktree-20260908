<template>
  <el-dialog :title="title" :visible="visible" width="680px" append-to-body
    custom-class="hr-lifecycle-dialog" :close-on-click-modal="false" @close="close">
    <p v-if="loading" role="status">正在核对员工办理资格…</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="pending" title="上次办理结果尚未确认，请使用原请求核对结果。" type="warning" :closable="false" show-icon />
    <el-alert v-if="success" :title="success" type="success" :closable="false" show-icon />
    <template v-if="context">
      <p><strong>{{ displayContext.employeeName || '员工' }}</strong> · {{ displayContext.employeeNo || employeeId }} · {{ displayContext.departmentName || '-' }}</p>
      <el-alert v-if="context.blockedReason && !pending" :title="context.blockedReason" type="info" :closable="false" />
      <el-form label-position="top" :model="form" @submit.native.prevent="submit">
        <template v-if="scenario === 'REGULARIZE'">
          <p>当前状态：{{ displayContext.employeeStatus }}；岗位：{{ displayContext.postName || '-' }}</p>
          <el-form-item label="实际转正日期">
            <el-date-picker v-model="form.actualRegularizationDate" type="date" value-format="yyyy-MM-dd" placeholder="请选择实际日期" :disabled="locked" />
          </el-form-item>
        </template>
        <template v-else>
          <p>{{ pending ? "上次办理所依据的合同" : "当前合同" }}：{{ contractTypeLabel }} · {{ contractTermLabel }}</p>
          <p>{{ displayContext.contractStartDate || '-' }} 至 {{ displayContext.contractEndDate || '-' }}；签约主体：{{ displayContext.legalEntityName || '-' }}</p>
          <el-form-item label="新合同开始日期">
            <el-date-picker v-model="form.contractStartDate" type="date" value-format="yyyy-MM-dd" :disabled="locked" />
          </el-form-item>
          <el-form-item label="新合同结束日期">
            <el-date-picker v-model="form.contractEndDate" type="date" value-format="yyyy-MM-dd" :disabled="locked" />
          </el-form-item>
        </template>
      </el-form>
      <p>办理记录会保留在员工档案中；合同签署准备完成后，仍由人事确认发送。</p>
      <h4>最近办理记录</h4>
      <p v-if="!context.history || !context.history.length">暂无记录</p>
      <ul v-else class="lifecycle-history">
        <li v-for="row in context.history" :key="row.actionId">
          <strong>{{ actionLabel(row.actionType) }}</strong> · {{ row.effectiveDate || '-' }} · {{ row.operatorName || '系统' }}
          <span>{{ deliveryLabel(row) }}</span>
          <el-button v-if="row.taskId" type="text" size="mini" @click="openTask(row.taskId)" v-hasPermi="['oa:signTask:query']">查看签署任务</el-button>
        </li>
      </ul>
    </template>
    <span slot="footer">
      <el-button :disabled="submitting" @click="initialize">刷新状态</el-button>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">{{ pending ? '核对上次办理结果' : '确认' + title }}</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { getHrEmployeeLifecycleContext, confirmHrEmployeeLifecycle } from '@/api/hr/employee'
import { getSelectedDeptId } from '@/utils/shopContext'

function exactId(value) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  if (typeof value !== 'number' && typeof value !== 'string') return ''
  const id = String(value).trim().replace(/^0+/, '')
  return /^[1-9]\d{0,18}$/.test(id) && (id.length < 19 || id <= '9223372036854775807') ? id : ''
}
function copy(value) { return JSON.parse(JSON.stringify(value)) }

export default {
  name: 'HrEmployeeLifecycleDialog',
  props: { visible: Boolean, employeeId: [String, Number], scenario: { type: String, default: 'REGULARIZE' } },
  data() { return { context: null, form: {}, loading: false, submitting: false, error: '', success: '',
    generation: 0, pending: null, destroyed: false, readScope: '' } },
  computed: {
    entryKey() { return JSON.stringify([this.visible, this.employeeId, this.scenario]) },
    title() { return this.scenario === 'REGULARIZE' ? '办理转正' : '办理续签' },
    locked() { return this.loading || this.submitting || !!this.pending || !this.context || !this.context.eligible },
    canSubmit() { return !this.loading && !this.submitting && !!this.context && !!this.readScope && this.readScope === this.scope()
      && (!!this.pending || this.context.eligible) },
    displayContext() { return this.pending ? this.pending.preview : this.context },
    contractTypeLabel() { return ({ LABOR_CONTRACT: '劳动合同', SERVICE_CONTRACT: '劳务合同', INTERNSHIP_AGREEMENT: '实习协议', OUTSOURCING_CONTRACT: '外包合同' })[this.displayContext.contractTypeCode] || this.displayContext.contractTypeCode || '-' },
    contractTermLabel() { return ({ FIXED_TERM: '固定期限', OPEN_ENDED: '无固定期限' })[this.displayContext.contractTermCode] || this.displayContext.contractTermCode || '-' }
  },
  watch: {
    entryKey() { if (this.visible) this.initialize(); else this.invalidate() },
    '$store.getters.id'() { this.scopeChanged() },
    '$route.fullPath'() { this.scopeChanged() }
  },
  created() {
    window.addEventListener('erp:dept-changed', this.scopeChanged)
    if (this.visible) this.initialize()
  },
  deactivated() { this.scopeChanged() },
  beforeDestroy() { this.destroyed = true; this.invalidate(); window.removeEventListener('erp:dept-changed', this.scopeChanged) },
  methods: {
    actorId() { return exactId(this.$store && this.$store.getters && this.$store.getters.id) },
    scope() { return JSON.stringify([exactId(this.employeeId), this.scenario, this.actorId(),
      String(getSelectedDeptId() || ''), this.$route && this.$route.fullPath]) },
    storageKey() { return 'hr-lifecycle-pending:' + this.scope() },
    invalidate() { this.generation++; this.context = null; this.pending = null; this.readScope = ''; this.loading = false; this.submitting = false },
    scopeChanged() { this.invalidate(); if (this.visible) this.$emit('update:visible', false) },
    close() { this.invalidate(); this.$emit('update:visible', false) },
    current(generation, scope) { return !this.destroyed && this.visible && generation === this.generation && scope === this.scope() },
    restorePending(scope) {
      const raw = window.sessionStorage.getItem(this.storageKey())
      if (!raw) return null
      const value = JSON.parse(raw)
      if (!value || value.scope !== scope || !value.body || !value.preview
          || exactId(value.preview.userId) !== exactId(this.employeeId) || value.preview.scenario !== this.scenario
          || typeof value.body.requestId !== 'string' || !/^[a-zA-Z0-9-]{1,64}$/.test(value.body.requestId)) throw Error('上次办理记录无效，请核对员工办理历史后再操作')
      if (this.scenario === 'REGULARIZE' ? value.body.preservePositionSalary !== true
        : value.body.expectedCycleKey !== value.preview.cycleKey || value.body.decision !== 'RENEW') throw Error('上次办理记录与员工合同不一致')
      return value
    },
    async initialize() {
      this.invalidate(); this.error = ''; this.success = ''; this.form = {}
      if (!this.visible || !this.actorId() || !exactId(this.employeeId) || !['REGULARIZE', 'RENEWAL'].includes(this.scenario)) { this.error = '员工办理标识无效'; return }
      const generation = this.generation, scope = this.scope(); this.loading = true
      try {
        this.pending = this.restorePending(scope)
        const response = await getHrEmployeeLifecycleContext(exactId(this.employeeId), this.scenario)
        if (!this.current(generation, scope)) return
        const context = response && response.data
        if (!context || exactId(context.userId) !== exactId(this.employeeId) || context.scenario !== this.scenario) throw Error('员工办理上下文不一致，请刷新')
        this.context = context; this.readScope = scope
        if (this.pending) this.form = copy(this.pending.body)
        else this.form = this.scenario === 'REGULARIZE' ? { actualRegularizationDate: context.businessDate } : { contractStartDate: '', contractEndDate: '' }
      } catch (error) { if (this.current(generation, scope)) { this.context = null; this.readScope = ''; this.error = error.message || '办理资格读取失败，请刷新重试' } }
      finally { if (this.current(generation, scope)) this.loading = false }
    },
    payload() {
      const date = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
      const requestId = 'hr-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2)
      if (this.scenario === 'REGULARIZE') {
        const actual = this.form.actualRegularizationDate
        if (!date(actual) || actual > this.context.businessDate || (this.context.entryDate && actual < this.context.entryDate)
            || (this.context.probationStartDate && actual < this.context.probationStartDate)
            || (this.context.contractEndDate && actual > this.context.contractEndDate)) throw Error('请填写入职、试用及合同期限内且不晚于今天的实际转正日期')
        return { requestId, preservePositionSalary: true, actualRegularizationDate: actual }
      }
      if (!date(this.form.contractStartDate) || !date(this.form.contractEndDate) || this.form.contractStartDate <= this.context.contractEndDate
          || this.form.contractEndDate <= this.form.contractStartDate) throw Error('新合同开始日期须晚于原合同结束，结束日期须晚于开始日期')
      return { requestId, decision: 'RENEW', expectedCycleKey: this.context.cycleKey,
        contractStartDate: this.form.contractStartDate, contractEndDate: this.form.contractEndDate,
        contractTypeCode: this.context.contractTypeCode, contractTermCode: this.context.contractTermCode,
        legalEntityId: this.context.legalEntityId, legalEntityCode: this.context.legalEntityCode, legalEntityName: this.context.legalEntityName }
    },
    clearPendingRecord(key, requestId) {
      try {
        const raw = window.sessionStorage.getItem(key)
        if (raw && JSON.parse(raw).body.requestId === requestId) window.sessionStorage.removeItem(key)
      } catch (ignored) { /* A confirmed result stays confirmed even if browser storage becomes unavailable. */ }
    },
    async submit() {
      if (!this.canSubmit) return
      const generation = this.generation, scope = this.scope(), storageKey = this.storageKey()
      let pending
      try {
        pending = this.pending || { scope, body: this.payload(), preview: copy(this.context) }
        window.sessionStorage.setItem(storageKey, JSON.stringify(pending))
      } catch (error) { this.error = error.message || '无法保留办理请求，请检查浏览器存储后重试'; return }
      this.pending = pending; this.submitting = true; this.error = ''
      try {
        const response = await confirmHrEmployeeLifecycle(exactId(this.employeeId), this.scenario, copy(pending.body))
        const actionId = exactId(response && response.data && response.data.actionId)
        if (!actionId) throw Error('办理响应未能确认，请使用原请求核对结果')
        this.clearPendingRecord(storageKey, pending.body.requestId)
        if (!this.current(generation, scope)) return
        this.pending = null; this.context.eligible = false; this.success = '办理成功，记录编号 ' + actionId + '。签署准备状态请刷新后查看。'
        this.$emit('confirmed', { userId: exactId(this.employeeId), actionId })
      } catch (error) {
        const status = error && error.response && error.response.status
        const definite = status >= 200 && status < 500 && status !== 408
        if (definite) this.clearPendingRecord(storageKey, pending.body.requestId)
        if (!this.current(generation, scope)) return
        if (definite) this.pending = null
        this.error = error.message || '办理结果尚未确认，请使用原请求核对结果'
      } finally { if (this.current(generation, scope)) this.submitting = false }
    },
    actionLabel(value) { return ({ REGULARIZATION_CONFIRMED: '转正已确认', RENEWAL_DECISION: '待续签决定', RENEWAL_CONFIRMED: '续签已确认', RENEWAL_DECLINED: '不续签已确认' })[value] || '办理记录' },
    deliveryLabel(row) {
      if (row.actionType === 'RENEWAL_DECISION') return '等待人事办理'
      if (row.taskId) return '已生成签署任务，请查看当前状态'
      return ({ PENDING: '签署准备待处理', SENDING: '正在准备签署', RETRY: '签署准备待重试', DEAD: '签署准备失败，请联系管理员核对' })[row.deliveryStatus] || '签署状态待核对'
    },
    openTask(taskId) { const id = exactId(taskId); if (id) this.$router.push({ path: '/oa/sign-task', query: { taskId: id } }) }
  }
}
</script>

<style scoped>
.lifecycle-history { list-style: none; margin: 0; padding: 0; }
.lifecycle-history li { padding: 8px 0; border-bottom: 1px solid #eee; }
.lifecycle-history span { display: block; color: #606266; }
</style>
<style>
@media (max-width: 700px) {
  .hr-lifecycle-dialog { width: calc(100% - 24px) !important; margin-top: 5vh !important; }
  .hr-lifecycle-dialog .el-date-editor.el-input { width: 100%; }
  .hr-lifecycle-dialog .el-dialog__footer { white-space: normal; }
}
</style>
