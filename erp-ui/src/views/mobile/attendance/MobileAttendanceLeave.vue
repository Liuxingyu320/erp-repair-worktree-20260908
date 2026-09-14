<template>
  <section class="leave-card">
    <div class="section-heading">
      <div><small>请假审批</small><h2>{{ approvalFocus ? '请假详情' : '我的请假' }}</h2></div>
      <button v-if="canSelf && !approvalFocus" type="button" :disabled="busy" @click="openNew">新建请假</button>
    </div>

    <div v-if="error" class="leave-message error" role="alert">{{ error }}</div>
    <div v-if="success" class="leave-message success" role="status">{{ success }}</div>

    <leave-balance-summary v-if="canBalance && !approvalFocus" :initial-type-id="showForm ? form.leaveTypeId : ''" />

    <form v-if="showForm && canSelf" class="leave-form" @submit.prevent>
      <label>
        <span>请假类型</span>
        <select v-model="form.leaveTypeId" :disabled="submitLocked || editorLoading || !editorReady || !editableStatus(form.status)" required>
          <option value="" disabled>请选择</option>
          <option v-for="type in leaveTypes" :key="type.leaveTypeId" :value="type.leaveTypeId">
            {{ type.typeName }}{{ type.payPolicy === 'PAID' ? '·带薪' : type.payPolicy === 'UNPAID' ? '·无薪' : '' }}
          </option>
        </select>
      </label>
      <div class="leave-time-grid">
        <label><span>开始时间</span><input v-model="form.startTime" :disabled="submitLocked || editorLoading || !editorReady || !editableStatus(form.status)" type="datetime-local" step="60" required></label>
        <label><span>结束时间</span><input v-model="form.endTime" :disabled="submitLocked || editorLoading || !editorReady || !editableStatus(form.status)" type="datetime-local" step="60" required></label>
      </div>
      <label><span>申请天数{{ requestedDaysRequired ? '（必填）' : '（可选，留空按时段申请）' }}</span><input v-model.trim="form.requestedDays" inputmode="decimal" type="text" placeholder="请填写实际申请的工作日天数" :disabled="submitLocked || editorLoading || !editorReady || !editableStatus(form.status)" :required="requestedDaysRequired"></label>
      <small v-if="requestedDaysUnitMode === 'DAY'">该假种按整天申请。</small><small v-else-if="requestedDaysUnitMode === 'HALF_DAY'">该假种按半天申请，可填写 0.5、1、1.5 天等。</small>
      <p v-if="form.quotaStatus && form.quotaStatus !== 'NOT_REQUIRED'" role="status">额度状态：{{ quotaStatusLabel(form.quotaStatus) }}</p>
      <label><span>请假原因</span><textarea v-model.trim="form.reason" :disabled="submitLocked || editorLoading || !editorReady || !editableStatus(form.status)" maxlength="1000" rows="4" placeholder="请填写真实请假原因" required /></label>
      <small v-if="unsavedChanges" role="status">当前还有未保存的修改或附件。</small>

      <div class="leave-attachments">
        <strong>证明附件</strong>
        <small>支持 PDF/JPG/PNG，是否必传由请假类型规则和时长决定。</small>
        <div v-for="file in form.attachments" :key="file.attachmentId" class="attachment-row">
          <button class="attachment-name" type="button" @click="downloadAttachment(form.leaveRequestId, file)">{{ file.originalName }}</button>
          <button v-if="editableStatus(form.status)" class="attachment-remove" type="button" :aria-label="`删除附件 ${file.originalName}`" @click="removeAttachment(file)"><i class="el-icon-delete" /></button>
        </div>
        <div v-for="entry in pendingFiles" :key="entry.id" class="attachment-row pending">
          <span>{{ entry.file.name }}·{{ entry.state === 'uploading' ? '上传中' : entry.state === 'retry' ? '待重试' : '待上传' }}</span><button class="attachment-remove" type="button" :disabled="submitLocked || entry.state !== 'pending'" :aria-label="`移除待上传附件 ${entry.file.name}`" @click="removePending(entry.id)"><i class="el-icon-close" /></button>
        </div>
        <input ref="leaveFileInput" class="leave-file-input" type="file" multiple accept="application/pdf,image/jpeg,image/png" @change="selectAttachments">
        <button class="secondary-button" type="button" :disabled="submitLocked || editorLoading || totalAttachmentCount >= 10" @click="$refs.leaveFileInput.click()">选择附件</button>
      </div>

      <button v-if="form.leaveRequestId && editableStatus(form.status)" class="secondary-button" type="button" :disabled="busy || !editorReady" @click="reviewCurrentPolicy">重新核对当前请假政策</button>
      <section v-if="policyReview" class="leave-policy-review" aria-label="核对请假政策">
        <h3>核对当前请假政策</h3>
        <p>草稿原政策：{{ policyDescription(policyReview.old) }}</p>
        <p>当前政策：{{ policyDescription(policyReview.next.quotaPolicySnapshot) }}</p>
        <p>本次申请：{{ policyReview.next.requestedDays == null ? minutesText(policyReview.next.totalMinutes) : policyReview.next.requestedDays + ' 天' }}；预计扣减：{{ previewQuota(policyReview.next) }}</p>
        <p>确认后仍需保存草稿，提交后才占用额度。</p>
        <button type="button" @click="cancelPolicyReview">取消核对</button>
        <button type="button" @click="confirmPolicyReview">已核对，采用当前政策</button>
      </section>

      <div class="leave-form-actions">
        <button type="button" :disabled="busy" @click="closeForm">取消</button>
        <button type="button" :disabled="busy || !editorReady || !editableStatus(form.status)" @click="saveDraft(false)">{{ busy ? '处理中…' : '保存草稿' }}</button>
        <button class="submit" type="button" :disabled="busy || !editorReady || !editableStatus(form.status)" @click="saveDraft(true)">保存并提交</button>
      </div>
    </form>

    <div v-else-if="loading" class="leave-state"><i class="el-icon-loading" /> 正在加载服务端请假记录…</div>
    <template v-else>
      <div v-if="!approvalFocus && canRead" class="leave-filter">
        <select v-model="status" @change="loadRows">
          <option value="">全部状态</option>
          <option value="DRAFT">草稿</option><option value="SUBMITTING">提交中</option>
          <option value="PENDING">审批中</option><option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option><option value="RETURNED">已退回</option>
          <option value="CANCELLED">已撤回</option>
        </select>
        <button type="button" :disabled="loading" @click="loadRows">刷新</button>
      </div>

      <div v-if="!canRead" class="leave-state">当前账号没有请假查看权限。</div>
      <div v-else-if="!rows.length" class="leave-state">暂无请假记录。</div>
      <article v-for="row in rows" v-else :key="row.leaveRequestId" class="leave-row">
        <div class="leave-row__top">
          <div><strong>{{ row.leaveTypeName || '请假申请' }}</strong><small>{{ row.userName || '' }}{{ row.leaveRequestNo ? `·${row.leaveRequestNo}` : '' }}</small></div>
          <span :class="['leave-status', statusTone(row.status)]">{{ statusLabel(row.status) }}</span>
        </div>
        <p>{{ timeText(row.startTime) }} 至 {{ timeText(row.endTime) }}</p>
        <p>{{ row.reason || '-' }}</p>
        <div class="leave-row__meta"><span v-if="row.requestedDays != null">申请 {{ row.requestedDays }} 天</span><span>{{ minutesText(row.totalMinutes) }}</span><span>{{ row.attachmentCount || (row.attachments || []).length || 0 }} 个附件</span></div>
        <div v-if="row.attachments && row.attachments.length" class="detail-attachments">
          <button v-for="file in row.attachments" :key="file.attachmentId" type="button" @click.stop="downloadAttachment(row.leaveRequestId, file)">{{ file.originalName }}</button>
        </div>
        <div v-if="!approvalFocus" class="leave-row__actions">
          <button type="button" @click="openRow(row)">查看详情</button>
          <button v-if="editableStatus(row.status)" type="button" @click.stop="editRow(row)">继续编辑</button>
          <button v-if="canSelf && withdrawableStatus(row.status)" type="button" @click="withdraw(row)">撤回</button>
        </div>
      </article>
    </template>
  </section>
</template>

<script>
import {
  createAttendanceLeaveDraft,
  previewAttendanceLeavePolicy,
  deleteAttendanceLeaveAttachment,
  getAttendanceLeave,
  getAttendanceLeaveByClientRequest,
  getAttendanceLeaveAttachmentContent,
  listAttendanceLeaveTypes,
  listMyAttendanceLeaves,
  listShopAttendanceLeaves,
  submitAttendanceLeave,
  updateAttendanceLeaveDraft,
  uploadAttendanceLeaveAttachment,
  withdrawAttendanceLeave
} from '@/api/oa/attendanceV2'
import { checkPermi } from '@/utils/permission'
import { getSelectedDeptContext } from '@/utils/shopContext'

const { exactId, formatUnits, definiteRejection } = require('@/utils/leaveBalanceUi')
const { attendanceErrorText, dataOf } = require('./attendancePunchPolicy')

const localInput = value => {
  const date = value instanceof Date ? value : new Date(value)
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const newClientRequestId = () => {
  const cryptoApi = typeof window !== 'undefined' ? window.crypto : null
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') return `leave:${cryptoApi.randomUUID()}`
  return `leave:${Date.now()}:${Math.random().toString(36).slice(2, 14)}`
}

const EDIT_FIELDS = ['leaveTypeId', 'startTime', 'endTime', 'reason', 'requestedDays']
const businessOf = value => EDIT_FIELDS.reduce((result, key) => {
  const text = value && value[key]
  if (key === 'requestedDays') { const exact = typeof text === 'number' && Number.isFinite(text) && Number(text.toFixed(6)) === text ? text.toFixed(6) : text; result[key] = exact == null || exact === '' ? '' : String(exact).replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, ''); return result }
  result[key] = /Time$/.test(key) ? String(text || '').replace(' ', 'T').slice(0, 16) : String(text == null ? '' : text)
  return result
}, {})
const sameBusiness = (left, right) => EDIT_FIELDS.every(key => businessOf(left)[key] === businessOf(right)[key])
const policyKey = value => value == null ? '' : JSON.stringify(Object.keys(value).sort().map(key => [key, value[key] == null ? null : String(value[key])]))
const samePolicy = (left, right) => policyKey(left) === policyKey(right)
const staleEditor = () => Object.assign(new Error('请假编辑会话已变化'), { staleEditor: true })

const emptyForm = () => {
  const start = new Date()
  start.setSeconds(0, 0)
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return { leaveRequestId: null, clientRequestId: newClientRequestId(), leaveTypeId: '', startTime: localInput(start), endTime: localInput(end), reason: '', requestedDays: '', rowVersion: null, status: 'DRAFT', attachments: [] }
}

export default {
  name: 'MobileAttendanceLeave',
  components: { LeaveBalanceSummary: () => import('@/views/oa/attendance/components/LeaveBalanceSummary.vue') },
  props: {
    todoBusinessId: { type: [String, Number], default: '' }
  },
  data() {
    return {
      loading: false,
      busy: false,
      error: '',
      success: '',
      status: '',
      rows: [],
      leaveTypes: [],
      showForm: false,
      form: emptyForm(),
      pendingFiles: [],
      fileSequence: 0,
      editorEpoch: 0,
      rowsEpoch: 0,
      detailEpoch: 0,
      lifecycleActive: true,
      editorLoading: false,
      editorReady: false,
      submitLocked: false,
      policyReview: null,
      reviewedPolicy: null,
      formContext: '',
      savedBusiness: null,
      savedPolicy: null,
      saveAttempt: null,
      submitAttempt: null,
      attachmentConfirmEpoch: 0
    }
  },
  computed: {
    canBalance() { return checkPermi(['oa:attendance:leave:balance:self']) },
    requestedDaysUnitMode() { if (this.form.quotaPolicySnapshot && String(this.form.quotaPolicySnapshot.leaveTypeId) === String(this.form.leaveTypeId)) return this.form.quotaPolicySnapshot.unitMode || ''; const type = this.leaveTypes.find(item => String(item.leaveTypeId) === String(this.form.leaveTypeId)); return type && type.unitMode || '' },
    requestedDaysRequired() { return ['DAY','HALF_DAY'].includes(this.requestedDaysUnitMode) },
    canSelf() { return checkPermi(['oa:attendance:leave:self']) },
    canList() { return checkPermi(['oa:attendance:leave:list', 'oa:attendance:leave:approve']) },
    canRead() { return this.canSelf || this.canList },
    approvalFocus() { return Boolean(String(this.todoBusinessId || '').trim()) },
    totalAttachmentCount() { return (this.form.attachments || []).length + this.pendingFiles.length },
    unsavedChanges() { return Boolean(this.reviewedPolicy) || this.pendingFiles.length > 0 || Boolean(this.savedBusiness && !sameBusiness(this.form, this.savedBusiness)) }
  },
  watch: {
    todoBusinessId() { this.invalidateEditor(); this.loadRows() }
  },
  created() {
    if (this.canSelf) this.loadTypes()
    this.loadRows()
    if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.handleLeaveContextChange)
  },
  activated() { this.lifecycleActive = true; this.loadRows() },
  deactivated() { this.lifecycleActive = false; this.invalidateEditor() },
  beforeDestroy() {
    this.lifecycleActive = false
    this.invalidateEditor()
    if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.handleLeaveContextChange)
  },
  methods: {
    quotaStatusLabel(status) { return { NOT_RESERVED:'尚未提交占用', RESERVED:'审批占用中', CONSUMED:'已使用', RELEASED:'已释放', REVIEW:'政策或额度待核对', LEGACY_NO_QUOTA:'历史申请无额度记录' }[status] || '待核对' },
    currentLeaveContext() {
      const context = getSelectedDeptContext() || {}
      return `${this.$store && this.$store.getters && this.$store.getters.id || ''}|${context.deptId || ''}`
    },
    invalidateEditor() {
      this.editorEpoch += 1
      this.rowsEpoch += 1
      this.detailEpoch += 1
      this.attachmentConfirmEpoch += 1
      this.busy = false
      this.editorLoading = false
      this.submitLocked = false
      this.policyReview = null
      this.reviewedPolicy = null
      this.loading = false
      this.pendingFiles.forEach(entry => { if (entry.state === 'uploading') entry.state = 'retry' })
    },
    handleLeaveContextChange() {
      this.invalidateEditor()
      if (this.showForm && this.formContext !== this.currentLeaveContext()) this.error = '请切回该草稿所属门店后继续编辑'
      this.loadRows()
    },
    editorOperation() {
      return { epoch: this.editorEpoch, context: this.formContext, snapshot: businessOf(this.form), leaveRequestId: this.form.leaveRequestId, clientRequestId: this.form.clientRequestId }
    },
    isCurrentEditor(operation) {
      return this.lifecycleActive && operation.epoch === this.editorEpoch && operation.context === this.formContext && operation.context === this.currentLeaveContext()
    },
    requireCurrentEditor(operation) { if (!this.isCurrentEditor(operation)) throw staleEditor() },
    requireLeaveDetail(detail, identity) {
      if (!detail || !detail.leaveRequestId || detail.rowVersion == null || !detail.status ||
        !['DRAFT', 'RETURNED', 'SUBMITTING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].includes(String(detail.status).toUpperCase()) ||
        EDIT_FIELDS.some(key => key !== 'requestedDays' && detail[key] == null) || !Array.isArray(detail.attachments) ||
        (identity.leaveRequestId && String(detail.leaveRequestId) !== String(identity.leaveRequestId)) ||
        (!identity.leaveRequestId && String(detail.clientRequestId || '') !== String(identity.clientRequestId || ''))) {
        throw new Error('服务端返回的请假身份或版本无法确认')
      }
      const expected = identity.payload || identity.snapshot || {}
      if (expected.requestedDays != null && expected.requestedDays !== '' && (detail.requestedDays == null || businessOf(detail).requestedDays !== businessOf(expected).requestedDays)) throw new Error('服务端返回的申请天数与本次请求不一致，请核对后重试')
      if (identity.payload && identity.payload.quotaPolicySnapshot && !samePolicy(detail.quotaPolicySnapshot, identity.payload.quotaPolicySnapshot)) throw new Error('服务端尚未确认本次核对的政策，请保留草稿并重试')
      return detail
    },
    mergeLeaveDetail(detail, operation) {
      this.requireCurrentEditor(operation)
      this.requireLeaveDetail(detail, operation)
      const local = businessOf(this.form)
      const incoming = businessOf(detail)
      const merged = Object.assign({}, this.form, detail, incoming, { attachments: detail.attachments || [] })
      EDIT_FIELDS.forEach(key => {
        if (local[key] !== operation.snapshot[key]) merged[key] = local[key]
      })
      this.form = merged
      this.reviewedPolicy = null
      this.savedPolicy = JSON.parse(JSON.stringify(detail.quotaPolicySnapshot || null))
      this.savedBusiness = incoming
      operation.leaveRequestId = detail.leaveRequestId
      operation.snapshot = incoming
      return detail
    },
    loadTypes() {
      return listAttendanceLeaveTypes({ status: 'ENABLED' }).then(response => {
        const payload = dataOf(response)
        this.leaveTypes = Array.isArray(payload) ? payload : []
      }).catch(error => { this.error = attendanceErrorText(error, '请假类型加载失败') })
    },
    loadRows() {
      if (!this.canRead) return Promise.resolve()
      const epoch = ++this.rowsEpoch
      const context = this.currentLeaveContext()
      const current = () => this.lifecycleActive && epoch === this.rowsEpoch && context === this.currentLeaveContext()
      this.loading = true
      let request
      if (this.approvalFocus) {
        request = getAttendanceLeave(this.todoBusinessId).then(response => [dataOf(response)])
      } else if (this.canSelf) {
        request = listMyAttendanceLeaves({ status: this.status || undefined }).then(response => dataOf(response))
      } else {
        const context = getSelectedDeptContext() || {}
        if (!context.isStore || !context.deptId) {
          this.error = '请先切换到待审批申请所属门店'
          this.loading = false
          return Promise.resolve()
        }
        request = listShopAttendanceLeaves({ shopId: context.deptId, status: this.status || undefined }).then(response => dataOf(response))
      }
      return request.then(rows => {
        if (!current()) return
        this.rows = (Array.isArray(rows) ? rows : []).filter(Boolean)
      }).catch(error => {
        if (!current()) return
        this.rows = []
        this.error = attendanceErrorText(error, '请假记录加载失败')
      }).finally(() => { if (current()) this.loading = false })
    },
    openNew() {
      if (!this.lifecycleActive) return
      this.invalidateEditor()
      this.formContext = this.currentLeaveContext()
      this.form = emptyForm()
      this.editorReady = true
      this.savedPolicy = null
      this.savedBusiness = null
      this.saveAttempt = null
      this.submitAttempt = null
      this.pendingFiles = []
      this.showForm = true
      this.error = ''
      this.success = ''
    },
    openRow(row) {
      const epoch = ++this.detailEpoch
      const context = this.currentLeaveContext()
      const current = () => this.lifecycleActive && epoch === this.detailEpoch && context === this.currentLeaveContext()
      const identity = { leaveRequestId: row.leaveRequestId }
      return getAttendanceLeave(identity.leaveRequestId).then(response => {
        if (!current()) return
        const detail = this.requireLeaveDetail(dataOf(response), identity)
        this.rows = this.approvalFocus ? [detail] : this.rows.map(item => String(item.leaveRequestId) === String(detail.leaveRequestId) ? detail : item)
      }).catch(error => { if (current()) this.error = attendanceErrorText(error, '请假详情加载失败') })
    },
    editRow(row) {
      if (!this.lifecycleActive) return
      this.invalidateEditor()
      this.formContext = this.currentLeaveContext()
      this.editorLoading = true
      this.editorReady = false
      this.busy = true
      this.error = ''
      const operation = this.editorOperation()
      operation.leaveRequestId = row.leaveRequestId
      return getAttendanceLeave(operation.leaveRequestId).then(response => {
        this.requireCurrentEditor(operation)
        const detail = this.requireLeaveDetail(dataOf(response), operation)
        if (!this.editableStatus(detail.status)) throw new Error('该申请已不能编辑，请刷新查看最新状态')
        this.form = Object.assign(emptyForm(), detail, businessOf(detail), { attachments: detail.attachments || [] })
        this.editorReady = true
        this.savedPolicy = JSON.parse(JSON.stringify(detail.quotaPolicySnapshot || null))
        this.savedBusiness = businessOf(detail)
        this.saveAttempt = null
        this.submitAttempt = null
        this.pendingFiles = []
        this.showForm = true
      }).catch(error => {
        if (this.isCurrentEditor(operation)) this.error = attendanceErrorText(error, '请假草稿加载失败')
      }).finally(() => {
        if (this.isCurrentEditor(operation)) { this.busy = false; this.editorLoading = false }
      })
    },
    closeForm() {
      this.invalidateEditor()
      this.showForm = false
      this.editorReady = false
      this.form = emptyForm()
      this.savedPolicy = null
      this.savedBusiness = null
      this.saveAttempt = null
      this.submitAttempt = null
      this.pendingFiles = []
    },
    validateForm(policyNeutral = false) {
      if (!this.form.leaveTypeId) return '请选择请假类型'
      if (!this.form.startTime || !this.form.endTime || this.form.startTime >= this.form.endTime) return '请选择有效的请假时间'
      const days = String(this.form.requestedDays || '').trim()
      if (!policyNeutral && this.requestedDaysRequired && !days) return '请填写申请天数'
      if (days && (!/^(0|[1-9]\d*)(\.\d{1,6})?$/.test(days) || Number(days) <= 0)) return '申请天数须为最多六位小数的正数'
      const fraction = (days.split('.')[1] || '').replace(/0+$/, '')
      if (!policyNeutral && days && this.requestedDaysUnitMode === 'DAY' && fraction) return '该假种须按整天申请'
      if (!policyNeutral && days && this.requestedDaysUnitMode === 'HALF_DAY' && fraction && fraction !== '5') return '该假种须按半天申请'
      if (!String(this.form.reason || '').trim()) return '请填写请假原因'
      return ''
    },
    policyDescription(policy) {
      if (!policy) return '未保存政策快照'
      const mode = { DAY:'整天', HALF_DAY:'半天', MINUTE:'分钟', MIXED:'天数或分钟' }[policy.unitMode] || '待核对'
      return `规则版本：${policy.ruleVersion == null ? '不适用' : policy.ruleVersion}；申请单位：${mode}；${policy.balanceRequired ? '需要扣减额度' : '无需扣减额度'}${policy.minutesPerDay != null ? `；每天折合 ${policy.minutesPerDay} 分钟` : ''}`
    },
    previewStillMatches(review) {
      return this.isCurrentEditor(review.operation) && String(this.form.leaveRequestId) === review.id && String(this.form.rowVersion) === review.version && sameBusiness(this.form, review.operation.snapshot)
    },
    async reviewCurrentPolicy() {
      if (this.busy || !this.editorReady || !this.showForm || !this.form.leaveRequestId || !this.editableStatus(this.form.status)) return
      const operation = this.editorOperation()
      if (!this.isCurrentEditor(operation)) { this.error = '请切回该草稿所属门店后继续编辑'; return }
      if (this.saveAttempt || this.submitAttempt) { this.error = '上次保存或提交结果尚未确认，请先重试核对结果'; return }
      const invalid = this.validateForm(true)
      if (invalid) { this.error = invalid; return }
      this.busy = true; this.submitLocked = true; this.error = ''; this.success = ''
      try {
        const id = exactId(this.form.leaveRequestId), version = exactId(this.form.rowVersion, true)
        const review = { operation, id, version, old: JSON.parse(JSON.stringify(this.form.quotaPolicySnapshot || null)) }
        const payload = { ...operation.snapshot, rowVersion: version }
        if (!payload.requestedDays) delete payload.requestedDays
        const next = dataOf(await previewAttendanceLeavePolicy(id, payload))
        this.requireCurrentEditor(operation)
        const context = operation.context.split('|')
        if (!this.previewStillMatches(review) || !next || exactId(next.leaveRequestId) !== id || exactId(next.rowVersion, true) !== version || exactId(next.userId) !== exactId(context[0]) || exactId(next.shopId) !== exactId(context[1]) || !sameBusiness(next, payload)) throw Error('政策预览与当前草稿不匹配，请重新核对')
        const policy = next.quotaPolicySnapshot
        if (!policy || policy.schemaVersion !== 1 || exactId(policy.leaveTypeId) !== exactId(payload.leaveTypeId) || !['DAY','HALF_DAY','MINUTE','MIXED'].includes(policy.unitMode) || typeof policy.balanceRequired !== 'boolean' || !['DAYS','MINUTES'].includes(policy.amountUnit)) throw Error('当前政策信息不完整，请重新读取')
        exactId(policy.leaveTypeVersion, true)
        if (policy.balanceRequired) {
          for (const key of ['ruleId','mappingId','legalEntityId']) exactId(policy[key])
          exactId(policy.mappingVersion, true)
          if (!Number.isSafeInteger(policy.ruleVersion) || policy.ruleVersion < 1 || !['DAYS','HOURS','MINUTES'].includes(policy.displayUnit)) throw Error('当前额度规则不完整')
          exactId(next.quotaUnits, true)
          if (formatUnits(next.quotaUnits, policy.displayUnit, policy.minutesPerDay) === '待核对') throw Error('当前额度单位无法核对')
        }
        review.next = JSON.parse(JSON.stringify(next))
        this.policyReview = review
      } catch (error) { if (this.isCurrentEditor(operation)) this.error = attendanceErrorText(error, '当前政策读取失败') }
      finally { if (this.isCurrentEditor(operation) && !this.policyReview) { this.busy = false; this.submitLocked = false } }
    },
    cancelPolicyReview() { this.policyReview = null; this.busy = false; this.submitLocked = false },
    confirmPolicyReview() {
      const review = this.policyReview
      if (!review) return
      if (!this.previewStillMatches(review)) { this.cancelPolicyReview(); this.error = '草稿或门店已变化，请重新核对当前政策'; return }
      this.reviewedPolicy = review
      this.form.quotaPolicySnapshot = JSON.parse(JSON.stringify(review.next.quotaPolicySnapshot))
      this.form.quotaUnits = review.next.quotaUnits
      this.cancelPolicyReview()
      this.success = '已核对当前政策，请保存草稿；保存并提交后才会占用额度'
    },
    previewQuota(next) { return next.quotaPolicySnapshot.balanceRequired ? formatUnits(next.quotaUnits, next.quotaPolicySnapshot.displayUnit, next.quotaPolicySnapshot.minutesPerDay) : '无需扣减' },
    async saveDraft(shouldSubmit) {
      if (this.busy || this.editorLoading || !this.editorReady || !this.showForm || !this.lifecycleActive) return
      const operation = this.editorOperation()
      if (!this.isCurrentEditor(operation)) { this.error = '请切回该草稿所属门店后继续编辑'; return }
      if (!this.editableStatus(this.form.status) && !this.submitAttempt) { this.error = '该申请已不能编辑，请查看最新状态'; return }
      if (!this.saveAttempt && !this.submitAttempt) {
        const invalid = this.validateForm()
        if (invalid) { this.error = invalid; return }
        if (this.reviewedPolicy && !this.previewStillMatches(this.reviewedPolicy)) { this.error = '核对政策后申请内容已变化，请重新核对当前政策'; return }
      }
      this.attachmentConfirmEpoch += 1
      this.busy = true
      this.error = ''
      this.success = ''
      const queueIds = this.pendingFiles.map(entry => entry.id)
      try {
        // An unknown submit must be reconciled before any draft write is retried.
        if (this.submitAttempt) {
          const attempt = this.submitAttempt
          const recovered = this.requireLeaveDetail(dataOf(await getAttendanceLeave(attempt.leaveRequestId)), attempt)
          this.requireCurrentEditor(operation)
          if (!sameBusiness(recovered, attempt.snapshot)) throw new Error('服务端请假内容已变化，当前输入已保留，请核对后继续')
          operation.snapshot = attempt.snapshot
          if (this.editableStatus(recovered.status)) {
            this.mergeLeaveDetail(recovered, operation)
            this.submitAttempt = null
            this.error = '上次提交尚未确认成功，请核对草稿后重新提交'
            return
          }
          this.mergeLeaveDetail(recovered, operation)
          this.finishLeaveSave(recovered, true)
          this.submitAttempt = null
          return
        }
        const payload = Object.assign({}, operation.snapshot, { rowVersion: this.form.rowVersion })
        if (!payload.requestedDays) delete payload.requestedDays
        if (this.reviewedPolicy) payload.quotaPolicySnapshot = JSON.parse(JSON.stringify(this.reviewedPolicy.next.quotaPolicySnapshot))
        else if (this.form.quotaPolicySnapshot && this.savedBusiness && String(this.savedBusiness.leaveTypeId) === String(this.form.leaveTypeId)) payload.quotaPolicySnapshot = JSON.parse(JSON.stringify(this.form.quotaPolicySnapshot))
        if (!operation.leaveRequestId) payload.clientRequestId = operation.clientRequestId
        const previous = this.saveAttempt
        const attempt = previous || { leaveRequestId: operation.leaveRequestId, clientRequestId: operation.clientRequestId, payload, baseBusiness: this.savedBusiness && Object.assign({}, this.savedBusiness), basePolicy: JSON.parse(JSON.stringify(this.savedPolicy)) }
        // Keep the original create fingerprint until the server identity is known.
        this.saveAttempt = attempt
        operation.snapshot = businessOf(attempt.payload)
        let draft
        if (previous) {
          draft = await this.recoverDraftSave(attempt, null, operation)
        } else {
          try {
            const response = attempt.leaveRequestId
              ? await updateAttendanceLeaveDraft(attempt.leaveRequestId, attempt.payload)
              : await createAttendanceLeaveDraft(payload)
            draft = this.requireLeaveDetail(dataOf(response), attempt)
          } catch (error) {
            draft = await this.recoverDraftSave(attempt, error, operation)
          }
        }
        this.requireCurrentEditor(operation)
        this.mergeLeaveDetail(draft, operation)
        this.saveAttempt = null
        if (!this.editableStatus(draft.status)) {
          this.finishLeaveSave(draft, true)
          return
        }
        draft = await this.uploadPending(draft, operation, queueIds)
        this.requireCurrentEditor(operation)
        if (this.unsavedChanges) {
          this.success = '草稿已保存，当前还有未保存的修改或附件，请再次保存后提交'
          return
        }
        if (shouldSubmit) {
          this.submitLocked = true
          this.submitAttempt = { leaveRequestId: draft.leaveRequestId, clientRequestId: draft.clientRequestId || operation.clientRequestId, snapshot: businessOf(draft) }
          draft = await submitAttendanceLeave(draft.leaveRequestId, draft.rowVersion)
            .then(response => {
              const result = this.requireLeaveDetail(dataOf(response), operation)
              if (!sameBusiness(result, draft)) throw new Error('服务端请假内容已变化，当前输入已保留，请核对后继续')
              return result
            })
            .catch(error => this.recoverSubmittedLeave(draft, error, operation))
          this.requireCurrentEditor(operation)
          this.mergeLeaveDetail(draft, operation)
          if (this.editableStatus(draft.status)) throw new Error('服务端尚未确认提交，请核对后重试')
          this.submitAttempt = null
        }
        this.finishLeaveSave(draft, shouldSubmit)
        await this.loadRows()
      } catch (error) {
        if (this.isCurrentEditor(operation)) this.error = attendanceErrorText(error, shouldSubmit ? '请假提交失败' : '请假草稿保存失败')
      } finally {
        if (this.isCurrentEditor(operation)) { this.busy = false; this.submitLocked = false }
      }
    },
    async recoverDraftSave(attempt, originalError, operation) {
      this.requireCurrentEditor(operation)
      let detail
      try {
        const response = attempt.leaveRequestId
          ? await getAttendanceLeave(attempt.leaveRequestId)
          : await getAttendanceLeaveByClientRequest(attempt.clientRequestId)
        detail = this.requireLeaveDetail(dataOf(response), { leaveRequestId: attempt.leaveRequestId, clientRequestId: attempt.clientRequestId })
      } catch (lookupError) {
        const signal = attendanceErrorText(lookupError, '')
        // Only an explicit not-found permits replay, and only with the original body.
        if (!attempt.leaveRequestId && /LEAVE_CLIENT_REQUEST_NOT_FOUND/.test(signal) && !originalError) {
          this.requireCurrentEditor(operation)
          return this.requireLeaveDetail(dataOf(await createAttendanceLeaveDraft(attempt.payload)), attempt)
        }
        throw originalError || lookupError
      }
      this.requireCurrentEditor(operation)
      if (originalError && definiteRejection(originalError) && attempt.baseBusiness && sameBusiness(detail, attempt.baseBusiness) && samePolicy(detail.quotaPolicySnapshot, attempt.basePolicy)) { this.saveAttempt = null; throw originalError }
      if (!sameBusiness(detail, attempt.payload) || attempt.payload.quotaPolicySnapshot && !samePolicy(detail.quotaPolicySnapshot, attempt.payload.quotaPolicySnapshot)) {
        if (attempt.leaveRequestId && attempt.baseBusiness && sameBusiness(detail, attempt.baseBusiness) && samePolicy(detail.quotaPolicySnapshot, attempt.basePolicy) && this.editableStatus(detail.status)) {
          if (originalError) throw originalError
          this.requireCurrentEditor(operation)
          // The original fields are still authoritative (the version may have
          // advanced only for attachments). Retry the original intent once.
          const payload = Object.assign({}, attempt.payload, { rowVersion: detail.rowVersion })
          try { return this.requireLeaveDetail(dataOf(await updateAttendanceLeaveDraft(attempt.leaveRequestId, payload)), attempt) }
          catch (replayError) {
            // A rejected replay needs its own authoritative check before the old
            // attempt is released. An uncertain replay keeps the same intent.
            if (definiteRejection(replayError)) return this.recoverDraftSave(attempt, replayError, operation)
            throw replayError
          }
        }
        throw new Error('服务端草稿已变化，当前输入已保留，请核对该申请后继续')
      }
      return detail
    },
    recoverSubmittedLeave(draft, originalError, operation) {
      this.requireCurrentEditor(operation)
      const leaveRequestId = draft && draft.leaveRequestId
      const clientRequestId = (draft && draft.clientRequestId) || operation.clientRequestId
      let lookup = getAttendanceLeave(leaveRequestId)
      if (clientRequestId) lookup = lookup.catch(() => {
        this.requireCurrentEditor(operation)
        return getAttendanceLeaveByClientRequest(clientRequestId)
      })
      return lookup.then(response => {
        this.requireCurrentEditor(operation)
        const detail = this.requireLeaveDetail(dataOf(response), { leaveRequestId })
        if (!sameBusiness(detail, draft)) throw new Error('服务端请假内容已变化，当前输入已保留，请核对后继续')
        const status = String(detail.status || '').toUpperCase()
        if (['DRAFT', 'RETURNED'].includes(status)) throw originalError
        return detail
      }).catch(() => { throw originalError })
    },
    finishLeaveSave(detail, submitted) {
      const status = String(detail.status || '').toUpperCase()
      if (!submitted) this.success = '请假草稿已保存'
      else if (status === 'SUBMITTING') this.success = '请假提交处理中，等待审批服务确认'
      else if (status === 'PENDING') this.success = '请假申请已由服务端提交'
      else this.success = `请假申请当前状态：${this.statusLabel(status)}`
      if (!this.unsavedChanges) this.showForm = false
    },
    async uploadPending(draft, operation, queueIds) {
      let current = draft
      for (const id of queueIds) {
        this.requireCurrentEditor(operation)
        const entry = this.pendingFiles.find(item => item.id === id)
        if (!entry) continue
        entry.state = 'uploading'
        try {
          const response = await uploadAttendanceLeaveAttachment(current.leaveRequestId, current.rowVersion, entry.file)
          this.requireCurrentEditor(operation)
          const attachment = dataOf(response)
          if (!attachment || !attachment.attachmentId || String(attachment.leaveRequestId) !== String(current.leaveRequestId) || attachment.requestRowVersion == null) {
            throw new Error('附件上传结果无法确认，请重试')
          }
          // A successful upload is acknowledged independently of the following read.
          this.form.rowVersion = attachment.requestRowVersion
          this.form.attachments = (this.form.attachments || []).filter(file => String(file.attachmentId) !== String(attachment.attachmentId)).concat(attachment)
          this.pendingFiles = this.pendingFiles.filter(item => item.id !== id)
          const refreshed = dataOf(await getAttendanceLeave(current.leaveRequestId))
          current = this.mergeLeaveDetail(refreshed, operation)
        } catch (error) {
          if (this.isCurrentEditor(operation)) {
            const remaining = this.pendingFiles.find(item => item.id === id)
            if (remaining) remaining.state = 'retry'
          }
          throw error
        }
      }
      return current
    },
    selectAttachments(event) {
      const files = Array.from(event && event.target && event.target.files || [])
      if (event && event.target) event.target.value = ''
      if (this.submitLocked || this.editorLoading || !this.editorReady || !this.showForm || !this.lifecycleActive || !this.editableStatus(this.form.status)) return
      const allowed = files.filter(file => /^(application\/pdf|image\/(jpeg|png))$/i.test(file.type || '') && file.size > 0 && file.size <= 5 * 1024 * 1024)
      if (allowed.length !== files.length) this.error = '附件只能是 5MB 以内的 PDF/JPG/PNG 真实文件'
      const room = Math.max(0, 10 - this.totalAttachmentCount)
      this.pendingFiles.push(...allowed.slice(0, room).map(file => ({ id: ++this.fileSequence, file, state: 'pending' })))
    },
    removePending(id) {
      if (this.submitLocked) return
      this.pendingFiles = this.pendingFiles.filter(entry => entry.id !== id || entry.state !== 'pending')
    },
    removeAttachment(file) {
      if (this.busy || this.editorLoading || !this.editorReady || !this.form.leaveRequestId || !this.editableStatus(this.form.status)) return
      const operation = this.editorOperation()
      const token = ++this.attachmentConfirmEpoch
      const attachmentId = file.attachmentId
      const rowVersion = this.form.rowVersion
      return this.$modal.confirm(`确认删除附件“${file.originalName}”？`).then(() => {
        this.requireCurrentEditor(operation)
        if (token !== this.attachmentConfirmEpoch || this.busy) throw staleEditor()
        this.busy = true
        return deleteAttendanceLeaveAttachment(operation.leaveRequestId, attachmentId, rowVersion)
      }).then(response => {
        // Deleting an attachment acknowledges no business-field edits.
        const preserve = Object.assign({}, operation, { snapshot: this.savedBusiness || {} })
        this.mergeLeaveDetail(dataOf(response), preserve)
      }).catch(error => {
        if (this.isCurrentEditor(operation) && !error.staleEditor && error !== 'cancel' && error !== 'close') this.error = attendanceErrorText(error, '附件删除失败')
      }).finally(() => {
        if (this.isCurrentEditor(operation) && token === this.attachmentConfirmEpoch) this.busy = false
      })
    },
    downloadAttachment(leaveRequestId, file) {
      return getAttendanceLeaveAttachmentContent(leaveRequestId, file.attachmentId).then(blob => {
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = file.originalName || `leave-${file.attachmentId}`
        link.click()
        setTimeout(() => URL.revokeObjectURL(url), 60000)
      }).catch(error => { this.error = attendanceErrorText(error, '附件读取失败') })
    },
    withdraw(row) {
      if (this.busy) return
      this.$modal.prompt('请填写撤回原因').then(({ value }) => {
        if (!String(value || '').trim()) throw new Error('撤回原因不能为空')
        this.busy = true
        return withdrawAttendanceLeave(row.leaveRequestId, { rowVersion: row.rowVersion, reason: String(value).trim() })
      }).then(response => {
        const detail = dataOf(response) || {}
        this.success = String(detail.status || '').toUpperCase() === 'CANCELLED'
          ? '请假申请已由服务端撤回'
          : '撤回请求已提交，等待审批服务确认'
        return this.loadRows()
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') this.error = attendanceErrorText(error, '请假撤回失败')
      }).finally(() => { this.busy = false })
    },
    editableStatus(status) { return ['DRAFT', 'RETURNED'].includes(String(status || '').toUpperCase()) },
    withdrawableStatus(status) { return ['DRAFT', 'RETURNED', 'PENDING'].includes(String(status || '').toUpperCase()) },
    statusLabel(status) {
      return { DRAFT: '草稿', SUBMITTING: '提交中', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', RETURNED: '已退回', CANCELLED: '已撤回' }[String(status || '').toUpperCase()] || '未知'
    },
    statusTone(status) {
      const key = String(status || '').toUpperCase()
      if (key === 'APPROVED') return 'success'
      if (['REJECTED', 'RETURNED'].includes(key)) return 'danger'
      if (['PENDING', 'SUBMITTING'].includes(key)) return 'warning'
      return 'info'
    },
    timeText(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' },
    minutesText(value) {
      const minutes = Number(value || 0)
      if (!minutes) return '时长待服务端计算'
      return minutes % 60 ? `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分` : `${minutes / 60} 小时`
    }
  }
}
</script>

<style scoped>
.leave-card { padding: 18px; border: 1px solid rgba(91,116,112,.12); border-radius: 20px; background: rgba(255,255,255,.94); box-shadow: 0 10px 28px rgba(32,57,73,.08); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.section-heading small { color: #4b817a; font-weight: 700; }
.section-heading h2 { margin: 3px 0 0; font-size: 19px; }
.section-heading > button, .leave-filter button, .secondary-button { min-height: 36px; padding: 0 12px; border: 0; border-radius: 10px; background: #e7f3f1; color: #176d63; font-weight: 700; }
.leave-message { margin: 12px 0; padding: 10px 12px; border-radius: 10px; line-height: 1.5; }
.leave-message.error { background: #fff0f0; color: #a53f3f; }
.leave-message.success { background: #e8f7f2; color: #176d63; }
.leave-form { display: grid; gap: 13px; margin-top: 16px; }
.leave-form label { display: grid; gap: 6px; color: #5e7076; font-size: 12px; font-weight: 700; }
.leave-form input, .leave-form select, .leave-form textarea, .leave-filter select { box-sizing: border-box; width: 100%; min-height: 42px; padding: 9px 10px; border: 1px solid #d8e1e2; border-radius: 11px; background: #fff; color: #21383d; font: inherit; }
.leave-time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.leave-attachments { display: grid; gap: 8px; padding: 12px; border-radius: 13px; background: #f5f8f8; }
.leave-attachments > small { color: #718087; line-height: 1.5; }
.leave-file-input { display: none; }
.attachment-row { display: flex; align-items: center; justify-content: space-between; min-height: 38px; padding: 7px 9px; border: 1px solid #dce6e5; border-radius: 9px; background: #fff; color: #375b5d; text-align: left; }
.attachment-name { flex: 1; min-width: 0; padding: 0; border: 0; background: transparent; color: inherit; overflow-wrap: anywhere; text-align: left; }
.attachment-remove { display: grid; place-items: center; flex: 0 0 32px; min-height: 32px; margin: -4px -4px -4px 8px; padding: 0; border: 0; border-radius: 8px; background: #f0f5f4; color: #7a5757; }
.attachment-row.pending { color: #7b6a37; }
.leave-policy-review { padding: 14px; border: 1px solid #c9ddda; border-radius: 12px; background: #f4f9f8; color: #21383d; line-height: 1.6; overflow-wrap: anywhere; }
.leave-policy-review h3 { margin: 0 0 10px; font-size: 16px; }
.leave-policy-review p { margin: 8px 0; font-size: 13px; }
.leave-policy-review button { display: block; width: 100%; min-height: 44px; margin-top: 10px; padding: 9px 10px; border: 0; border-radius: 10px; background: #e0edeb; color: #176d63; font: inherit; font-weight: 700; }
.leave-policy-review button:last-child { background: #176d63; color: #fff; }
.leave-form-actions { display: grid; grid-template-columns: 1fr 1fr 1.25fr; gap: 8px; }
.leave-form-actions button { min-height: 43px; border: 0; border-radius: 11px; background: #e9eeee; color: #53666b; font-weight: 700; }
.leave-form-actions .submit { background: #176d63; color: #fff; }
.leave-form-actions button:disabled { opacity: .5; }
.leave-filter { display: grid; grid-template-columns: 1fr auto; gap: 8px; margin: 14px 0; }
.leave-state { padding: 28px 8px; color: #718087; text-align: center; }
.leave-row { padding: 14px 0; border-top: 1px solid #edf1f2; }
.leave-row__top, .leave-row__meta, .leave-row__actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.leave-row__top > div { display: grid; gap: 3px; }
.leave-row__top small, .leave-row__meta { color: #718087; font-size: 11px; }
.leave-row p { margin: 8px 0 0; color: #52656b; line-height: 1.5; }
.leave-row__meta { margin-top: 9px; }
.leave-status { padding: 5px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.leave-status.success { background: #e8f7f2; color: #176d63; }
.leave-status.warning { background: #fff5df; color: #9a6712; }
.leave-status.danger { background: #fff0f0; color: #a53f3f; }
.leave-status.info { background: #eef1f3; color: #637278; }
.detail-attachments { display: flex; gap: 6px; margin-top: 9px; flex-wrap: wrap; }
.detail-attachments button, .leave-row__actions button { padding: 6px 9px; border: 0; border-radius: 8px; background: #edf5f4; color: #276d65; }
.leave-row__actions { justify-content: flex-end; margin-top: 10px; }
@media (max-width: 390px) { .leave-time-grid { grid-template-columns: 1fr; } .leave-form-actions { grid-template-columns: 1fr 1fr; } .leave-form-actions .submit { grid-column: 1 / -1; } }
</style>
