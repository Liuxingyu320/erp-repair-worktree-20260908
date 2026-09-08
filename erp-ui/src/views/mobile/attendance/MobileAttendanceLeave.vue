<template>
  <section class="leave-card">
    <div class="section-heading">
      <div><small>请假审批</small><h2>{{ approvalFocus ? '请假详情' : '我的请假' }}</h2></div>
      <button v-if="canSelf && !approvalFocus" type="button" :disabled="busy" @click="openNew">新建请假</button>
    </div>

    <div v-if="error" class="leave-message error" role="alert">{{ error }}</div>
    <div v-if="success" class="leave-message success" role="status">{{ success }}</div>

    <form v-if="showForm && canSelf" class="leave-form" @submit.prevent>
      <label>
        <span>请假类型</span>
        <select v-model="form.leaveTypeId" required>
          <option value="" disabled>请选择</option>
          <option v-for="type in leaveTypes" :key="type.leaveTypeId" :value="type.leaveTypeId">
            {{ type.typeName }}{{ type.payPolicy === 'PAID' ? '·带薪' : type.payPolicy === 'UNPAID' ? '·无薪' : '' }}
          </option>
        </select>
      </label>
      <div class="leave-time-grid">
        <label><span>开始时间</span><input v-model="form.startTime" type="datetime-local" step="60" required></label>
        <label><span>结束时间</span><input v-model="form.endTime" type="datetime-local" step="60" required></label>
      </div>
      <label><span>请假原因</span><textarea v-model.trim="form.reason" maxlength="1000" rows="4" placeholder="请填写真实请假原因" required /></label>

      <div class="leave-attachments">
        <strong>证明附件</strong>
        <small>支持 PDF/JPG/PNG，是否必传由请假类型规则和时长决定。</small>
        <div v-for="file in form.attachments" :key="file.attachmentId" class="attachment-row">
          <button class="attachment-name" type="button" @click="downloadAttachment(form.leaveRequestId, file)">{{ file.originalName }}</button>
          <button v-if="editableStatus(form.status)" class="attachment-remove" type="button" :aria-label="`删除附件 ${file.originalName}`" @click="removeAttachment(file)"><i class="el-icon-delete" /></button>
        </div>
        <div v-for="(file, index) in pendingFiles" :key="`${file.name}-${index}`" class="attachment-row pending">
          <span>{{ file.name }}·待上传</span><button class="attachment-remove" type="button" :aria-label="`移除待上传附件 ${file.name}`" @click="pendingFiles.splice(index, 1)"><i class="el-icon-close" /></button>
        </div>
        <input ref="leaveFileInput" class="leave-file-input" type="file" multiple accept="application/pdf,image/jpeg,image/png" @change="selectAttachments">
        <button class="secondary-button" type="button" :disabled="busy || totalAttachmentCount >= 10" @click="$refs.leaveFileInput.click()">选择附件</button>
      </div>

      <div class="leave-form-actions">
        <button type="button" :disabled="busy" @click="closeForm">取消</button>
        <button type="button" :disabled="busy" @click="saveDraft(false)">{{ busy ? '处理中…' : '保存草稿' }}</button>
        <button class="submit" type="button" :disabled="busy" @click="saveDraft(true)">保存并提交</button>
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
        <div class="leave-row__meta"><span>{{ minutesText(row.totalMinutes) }}</span><span>{{ row.attachmentCount || (row.attachments || []).length || 0 }} 个附件</span></div>
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

const emptyForm = () => {
  const start = new Date()
  start.setSeconds(0, 0)
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return { leaveRequestId: null, clientRequestId: newClientRequestId(), leaveTypeId: '', startTime: localInput(start), endTime: localInput(end), reason: '', rowVersion: null, status: 'DRAFT', attachments: [] }
}

export default {
  name: 'MobileAttendanceLeave',
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
      pendingFiles: []
    }
  },
  computed: {
    canSelf() { return checkPermi(['oa:attendance:leave:self']) },
    canList() { return checkPermi(['oa:attendance:leave:list', 'oa:attendance:leave:approve']) },
    canRead() { return this.canSelf || this.canList },
    approvalFocus() { return Boolean(String(this.todoBusinessId || '').trim()) },
    totalAttachmentCount() { return (this.form.attachments || []).length + this.pendingFiles.length }
  },
  watch: {
    todoBusinessId() { this.loadRows() }
  },
  created() {
    if (this.canSelf) this.loadTypes()
    this.loadRows()
  },
  methods: {
    loadTypes() {
      return listAttendanceLeaveTypes({ status: 'ENABLED' }).then(response => {
        const payload = dataOf(response)
        this.leaveTypes = Array.isArray(payload) ? payload : []
      }).catch(error => { this.error = attendanceErrorText(error, '请假类型加载失败') })
    },
    loadRows() {
      if (!this.canRead) return Promise.resolve()
      this.loading = true
      this.error = ''
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
        this.rows = (Array.isArray(rows) ? rows : []).filter(Boolean)
      }).catch(error => {
        this.rows = []
        this.error = attendanceErrorText(error, '请假记录加载失败')
      }).finally(() => { this.loading = false })
    },
    openNew() {
      this.form = emptyForm()
      this.pendingFiles = []
      this.showForm = true
      this.error = ''
      this.success = ''
    },
    openRow(row) {
      return getAttendanceLeave(row.leaveRequestId).then(response => {
        const detail = dataOf(response)
        this.rows = this.approvalFocus ? [detail] : this.rows.map(item => item.leaveRequestId === detail.leaveRequestId ? detail : item)
      }).catch(error => { this.error = attendanceErrorText(error, '请假详情加载失败') })
    },
    editRow(row) {
      this.busy = true
      this.error = ''
      return getAttendanceLeave(row.leaveRequestId).then(response => {
        const detail = dataOf(response) || {}
        this.form = Object.assign(emptyForm(), detail, {
          startTime: String(detail.startTime || '').slice(0, 16),
          endTime: String(detail.endTime || '').slice(0, 16),
          attachments: detail.attachments || []
        })
        this.pendingFiles = []
        this.showForm = true
      }).catch(error => { this.error = attendanceErrorText(error, '请假草稿加载失败') })
        .finally(() => { this.busy = false })
    },
    closeForm() {
      if (this.busy) return
      this.showForm = false
      this.form = emptyForm()
      this.pendingFiles = []
    },
    validateForm() {
      if (!this.form.leaveTypeId) return '请选择请假类型'
      if (!this.form.startTime || !this.form.endTime || this.form.startTime >= this.form.endTime) return '请选择有效的请假时间'
      if (!String(this.form.reason || '').trim()) return '请填写请假原因'
      return ''
    },
    saveDraft(shouldSubmit) {
      if (this.busy) return
      const invalid = this.validateForm()
      if (invalid) { this.error = invalid; return }
      this.busy = true
      this.error = ''
      this.success = ''
      const payload = {
        leaveTypeId: this.form.leaveTypeId,
        startTime: this.form.startTime,
        endTime: this.form.endTime,
        reason: this.form.reason,
        rowVersion: this.form.rowVersion
      }
      if (!this.form.leaveRequestId) payload.clientRequestId = this.form.clientRequestId
      const save = this.form.leaveRequestId
        ? updateAttendanceLeaveDraft(this.form.leaveRequestId, payload)
        : createAttendanceLeaveDraft(payload)
      save.then(response => {
        const draft = dataOf(response)
        if (!draft || !draft.leaveRequestId) throw new Error('服务端未返回请假草稿')
        // Persist the server identity/version immediately. If an attachment upload or
        // submit fails, retrying must update this draft instead of creating another one.
        this.form = Object.assign({}, this.form, draft, {
          startTime: String(draft.startTime || this.form.startTime || '').slice(0, 16),
          endTime: String(draft.endTime || this.form.endTime || '').slice(0, 16),
          attachments: draft.attachments || this.form.attachments || []
        })
        return this.uploadPending(draft)
      }).then(draft => {
        if (!shouldSubmit) return draft
        return submitAttendanceLeave(draft.leaveRequestId, draft.rowVersion)
          .then(response => dataOf(response))
          .catch(error => this.recoverSubmittedLeave(draft, error))
      }).then(result => {
        if (!result || !result.leaveRequestId) throw new Error('服务端未返回请假申请')
        this.success = shouldSubmit ? '请假申请已由服务端提交' : '请假草稿已保存'
        this.showForm = false
        this.pendingFiles = []
        return this.loadRows()
      }).catch(error => {
        this.error = attendanceErrorText(error, shouldSubmit ? '请假提交失败' : '请假草稿保存失败')
      }).finally(() => { this.busy = false })
    },
    recoverSubmittedLeave(draft, originalError) {
      const leaveRequestId = draft && draft.leaveRequestId
      const clientRequestId = (draft && draft.clientRequestId) || this.form.clientRequestId
      let lookup = leaveRequestId
        ? getAttendanceLeave(leaveRequestId)
        : Promise.reject(originalError)
      if (clientRequestId) {
        lookup = lookup.catch(() => getAttendanceLeaveByClientRequest(clientRequestId))
      }
      return lookup.then(response => {
        const detail = dataOf(response)
        const status = String(detail && detail.status || '').toUpperCase()
        if (!detail || !detail.leaveRequestId || ['DRAFT', 'RETURNED'].includes(status)) {
          throw originalError
        }
        // The submit command may have succeeded even if its HTTP response was lost.
        // Reconcile the authoritative state instead of retrying a draft update.
        this.form = Object.assign({}, this.form, detail, {
          startTime: String(detail.startTime || this.form.startTime || '').slice(0, 16),
          endTime: String(detail.endTime || this.form.endTime || '').slice(0, 16),
          attachments: detail.attachments || []
        })
        return detail
      }).catch(() => { throw originalError })
    },
    uploadPending(draft) {
      let chain = Promise.resolve(draft)
      this.pendingFiles.forEach(file => {
        chain = chain.then(current => uploadAttendanceLeaveAttachment(current.leaveRequestId, current.rowVersion, file))
          .then(() => getAttendanceLeave(draft.leaveRequestId))
          .then(response => {
            const detail = dataOf(response)
            if (!detail || !detail.leaveRequestId) throw new Error('服务端未返回请假详情')
            this.form = Object.assign({}, this.form, detail, {
              startTime: String(detail.startTime || this.form.startTime || '').slice(0, 16),
              endTime: String(detail.endTime || this.form.endTime || '').slice(0, 16),
              attachments: detail.attachments || []
            })
            return detail
          })
      })
      return chain
    },
    selectAttachments(event) {
      const files = Array.from(event && event.target && event.target.files || [])
      if (event && event.target) event.target.value = ''
      const allowed = files.filter(file => /^(application\/pdf|image\/(jpeg|png))$/i.test(file.type || '') && file.size > 0 && file.size <= 5 * 1024 * 1024)
      if (allowed.length !== files.length) this.error = '附件只能是 5MB 以内的 PDF/JPG/PNG 真实文件'
      const room = Math.max(0, 10 - this.totalAttachmentCount)
      this.pendingFiles.push(...allowed.slice(0, room))
    },
    removeAttachment(file) {
      if (this.busy || !this.form.leaveRequestId) return
      this.$modal.confirm(`确认删除附件“${file.originalName}”？`).then(() => {
        this.busy = true
        return deleteAttendanceLeaveAttachment(this.form.leaveRequestId, file.attachmentId, this.form.rowVersion)
      }).then(response => {
        const detail = dataOf(response)
        this.form = Object.assign({}, this.form, detail, { attachments: detail.attachments || [] })
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') this.error = attendanceErrorText(error, '附件删除失败')
      }).finally(() => { this.busy = false })
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
