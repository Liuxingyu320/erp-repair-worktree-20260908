<template>
  <section class="correction-card">
    <div class="section-heading">
      <div><small>保留原始证据</small><h2>{{ approvalFocus ? '补卡详情' : '我的补卡' }}</h2></div>
      <button v-if="canSelf && !approvalFocus" type="button" :disabled="busy || scheduleLoading || !!scheduleError" @click="openNew">新建补卡</button>
    </div>
    <div v-if="error" class="correction-message error" role="alert">{{ error }}</div>
    <div v-if="scheduleError" class="correction-message warning" role="status">{{ scheduleError }}</div>
    <div v-if="success" class="correction-message success" role="status">{{ success }}</div>

    <form v-if="showForm && canSelf" class="correction-form" @submit.prevent>
      <label><span>对应排班</span>
        <select v-model="form.scheduleId" required @change="scheduleChanged">
          <option value="" disabled>请选择已发布排班</option>
          <option v-for="schedule in schedules" :key="schedule.scheduleId" :value="schedule.scheduleId">{{ scheduleLabel(schedule) }}</option>
        </select>
      </label>
      <div class="correction-grid">
        <label><span>更正类型</span>
          <select v-model="form.correctionType" @change="correctionTypeChanged">
            <option value="MISSING_PUNCH">缺卡补录</option><option value="WRONG_TIME">时间更正</option>
            <option value="WRONG_TYPE">上/下班类型更正</option>
          </select>
        </label>
        <label><span>目标打卡</span>
          <select v-if="usesSlotTargeting" v-model="form.targetPunchSlotKey" @change="targetSlotChanged">
            <option value="" disabled>请选择具体工作段</option>
            <option v-for="slot in selectableCorrectionTargets" :key="slot.punchSlotKey" :value="slot.punchSlotKey">{{ correctionSlotLabel(slot) }}</option>
          </select>
          <select v-else v-model="form.targetPunchType" @change="targetTypeChanged"><option value="IN">上班卡</option><option value="OUT">下班卡</option></select>
        </label>
      </div>
      <label v-if="requiresOriginalEvent"><span>原打卡事件</span>
        <select v-model="form.originalPunchEventId" required @change="eventChanged">
          <option value="" disabled>请选择服务端原始事件</option>
          <option v-for="event in punchEvents" :key="event.punchEventId" :value="event.punchEventId">{{ eventPunchLabel(event) }}·{{ dateTime(event.serverPunchTime) }}</option>
        </select>
      </label>
      <label><span>申请更正为</span><input v-model="form.requestedPunchTime" type="datetime-local" step="60" required></label>
      <label><span>更正原因</span><textarea v-model.trim="form.reason" maxlength="1000" rows="4" placeholder="请说明为何需要补卡，原事件不会被修改" required /></label>
      <div class="evidence-note"><i class="el-icon-lock" /><span>通过后服务端会生成更正结果；原始打卡事件和照片证据保持不变。</span></div>
      <div class="correction-actions">
        <button type="button" :disabled="busy" @click="closeForm">取消</button>
        <button type="button" :disabled="busy" @click="saveDraft(false)">{{ busy ? '处理中…' : '保存草稿' }}</button>
        <button class="submit" type="button" :disabled="busy" @click="saveDraft(true)">保存并提交</button>
      </div>
    </form>

    <div v-else-if="loading" class="correction-state"><i class="el-icon-loading" /> 正在加载服务端补卡记录…</div>
    <template v-else>
      <div v-if="!approvalFocus && canRead" class="correction-filter">
        <select v-model="status" @change="loadRows"><option value="">全部状态</option><option value="DRAFT">草稿</option><option value="SUBMITTING">提交中</option><option value="PENDING">审批中</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option><option value="RETURNED">已退回</option></select>
        <button type="button" :disabled="loading || scheduleLoading" @click="refresh">刷新</button>
      </div>
      <div v-if="!canRead" class="correction-state">当前账号没有补卡查看权限。</div>
      <div v-else-if="!rows.length" class="correction-state">暂无补卡申请。</div>
      <article v-for="row in rows" v-else :key="row.correctionRequestId" class="correction-row">
        <div class="correction-row__top"><div><strong>{{ correctionLabel(row.correctionType) }}</strong><small>{{ row.userName || '' }}{{ row.correctionRequestNo ? `·${row.correctionRequestNo}` : '' }}</small></div><span :class="['correction-status', statusTone(row.status)]">{{ statusLabel(row.status) }}</span></div>
        <p>{{ row.businessDate || '-' }}·{{ correctionRowPunchLabel(row) }}·申请 {{ dateTime(row.requestedPunchTime) }}</p>
        <p v-if="correctionChangeText(row)" class="correction-change">{{ correctionChangeText(row) }}<small v-if="row.originalPunchEventId">原事件 #{{ row.originalPunchEventId }}</small></p>
        <p>{{ row.reason || '-' }}</p>
        <div v-if="!approvalFocus" class="correction-row__actions">
          <button type="button" @click="openRow(row)">查看详情</button>
          <button v-if="canSelf && editableStatus(row.status)" type="button" @click="editRow(row)">继续编辑</button>
        </div>
      </article>
    </template>
  </section>
</template>

<script>
import {
  createAttendanceCorrectionDraft,
  getAttendanceCorrection,
  listAttendanceCorrectionEligiblePunchEvents,
  listAttendanceCorrectionEligibleSchedules,
  listMyAttendanceCorrections,
  listShopAttendanceCorrections,
  submitAttendanceCorrection,
  updateAttendanceCorrectionDraft
} from '@/api/oa/attendanceV2'
import { checkPermi } from '@/utils/permission'
import { getSelectedDeptContext } from '@/utils/shopContext'

const { attendanceErrorText, dataOf, isSegmentPunchContext, normalizePunchSlot, punchSlotLabel } = require('./attendancePunchPolicy')
const pad = number => String(number).padStart(2, '0')
const dateOnly = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const localInput = date => `${dateOnly(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
const emptyForm = () => ({ correctionRequestId: null, scheduleId: '', correctionType: 'MISSING_PUNCH', targetPunchType: 'IN', targetPunchSlotKey: '', targetScheduleSegmentSnapshotId: null, originalPunchEventId: '', requestedPunchTime: '', reason: '', rowVersion: null, status: 'DRAFT' })

export default {
  name: 'MobileAttendanceCorrection',
  props: { todoBusinessId: { type: [String, Number], default: '' } },
  data() {
    return { loading: false, scheduleLoading: false, busy: false, error: '', scheduleError: '', success: '', status: '', rows: [], schedules: [], punchEvents: [], showForm: false, form: emptyForm() }
  },
  computed: {
    canSelf() { return checkPermi(['oa:attendance:correction:self']) },
    canList() { return checkPermi(['oa:attendance:correction:list', 'oa:attendance:correction:approve']) },
    canRead() { return this.canSelf || this.canList },
    approvalFocus() { return Boolean(String(this.todoBusinessId || '').trim()) },
    requiresOriginalEvent() { return ['WRONG_TIME', 'WRONG_TYPE'].includes(this.form.correctionType) },
    selectedSchedule() {
      const selected = this.schedules.find(item => String(item.scheduleId) === String(this.form.scheduleId))
      if (selected) return selected
      if (!this.form.correctionRequestId || !this.form.scheduleId) return null
      return {
        scheduleId: this.form.scheduleId,
        businessDate: this.form.businessDate || '',
        punchModeSnapshot: this.form.targetPunchSlotKey && this.form.targetScheduleSegmentSnapshotId ? 'PER_WORK_SEGMENT' : 'SHIFT_BOUNDARY',
        punchSlots: []
      }
    },
    correctionTargets() {
      const schedule = this.selectedSchedule || {}
      const source = Array.isArray(schedule.punchSlots) ? schedule.punchSlots : []
      const byKey = new Map()
      const fallback = source.length ? [] : (this.punchEvents || [])
      const preserved = this.form.targetPunchSlotKey && this.form.targetScheduleSegmentSnapshotId
        ? [{
            punchSlotKey: this.form.targetPunchSlotKey,
            punchType: this.form.targetPunchType,
            scheduleSegmentSnapshotId: this.form.targetScheduleSegmentSnapshotId,
            segmentOrder: this.form.targetSegmentOrder,
            segmentLabel: this.form.targetSegmentLabelSnapshot
          }]
        : []
      source.concat(fallback, preserved).map(normalizePunchSlot).filter(slot => slot && slot.punchSlotKey).forEach(slot => {
        if (!byKey.has(slot.punchSlotKey)) byKey.set(slot.punchSlotKey, slot)
      })
      return Array.from(byKey.values()).sort((left, right) => {
        const segmentDiff = Number(left.segmentOrder || 0) - Number(right.segmentOrder || 0)
        return segmentDiff || (left.punchType === right.punchType ? 0 : (left.punchType === 'IN' ? -1 : 1))
      })
    },
    selectableCorrectionTargets() {
      if (this.form.correctionType !== 'MISSING_PUNCH') return this.correctionTargets
      return this.correctionTargets.filter(slot => {
        if (this.form.correctionRequestId && String(slot.punchSlotKey) === String(this.form.targetPunchSlotKey)) return true
        if (slot.eligibleForMissingPunch === false) return false
        const status = String(slot.status || slot.slotStatus || '').toUpperCase()
        return status ? status === 'MISSING' : slot.completed !== true
      })
    },
    usesSlotTargeting() {
      const schedule = this.selectedSchedule || {}
      return Boolean(this.form.targetPunchSlotKey && this.form.targetScheduleSegmentSnapshotId) ||
        isSegmentPunchContext({ punchModeSnapshot: schedule.punchModeSnapshot })
    }
  },
  watch: { todoBusinessId() { this.loadRows() } },
  created() {
    if (this.canSelf) this.refresh()
    else this.loadRows()
  },
  methods: {
    rowsOf(response) { const payload = dataOf(response); return Array.isArray(payload) ? payload : [] },
    loadEligibleSchedules() {
      this.scheduleLoading = true; this.scheduleError = ''
      const to = new Date(); const from = new Date(to); from.setDate(from.getDate() - 365)
      return listAttendanceCorrectionEligibleSchedules({ dateFrom: dateOnly(from), dateTo: dateOnly(to) }).then(response => { this.schedules = this.rowsOf(response) })
        .catch(error => {
          this.schedules = []
          this.scheduleError = `${attendanceErrorText(error, '可补卡排班加载失败')}；暂不能新建补卡，历史补卡记录仍可查看。`
        }).finally(() => { this.scheduleLoading = false })
    },
    refresh() {
      const schedules = this.canSelf && !this.approvalFocus
        ? this.loadEligibleSchedules()
        : Promise.resolve()
      return Promise.all([schedules, this.loadRows()])
    },
    loadRows() {
      if (!this.canRead) return Promise.resolve()
      this.loading = true; this.error = ''
      let request
      if (this.approvalFocus) request = getAttendanceCorrection(this.todoBusinessId).then(response => [dataOf(response)])
      else if (this.canSelf) request = listMyAttendanceCorrections({ status: this.status || undefined }).then(response => dataOf(response))
      else {
        const context = getSelectedDeptContext() || {}
        if (!context.isStore || !context.deptId) { this.error = '请先切换到待审批申请所属门店'; this.loading = false; return Promise.resolve() }
        request = listShopAttendanceCorrections({ shopId: context.deptId, status: this.status || undefined }).then(response => dataOf(response))
      }
      return request.then(rows => { this.rows = (Array.isArray(rows) ? rows : []).filter(Boolean) })
        .catch(error => { this.rows = []; this.error = attendanceErrorText(error, '补卡记录加载失败') })
        .finally(() => { this.loading = false })
    },
    openNew() {
      if (!this.schedules.length) { this.scheduleError = '近一年内没有可用的已发布排班，不能新建补卡。'; return }
      this.form = emptyForm(); this.form.scheduleId = this.schedules[0].scheduleId; this.showForm = true; this.error = ''; this.success = ''; this.scheduleChanged()
    },
    openRow(row) {
      return getAttendanceCorrection(row.correctionRequestId).then(response => {
        const detail = dataOf(response)
        this.rows = this.rows.map(item => item.correctionRequestId === detail.correctionRequestId ? detail : item)
      }).catch(error => { this.error = attendanceErrorText(error, '补卡详情加载失败') })
    },
    editRow(row) {
      this.busy = true; this.error = ''
      return getAttendanceCorrection(row.correctionRequestId).then(response => {
        const detail = dataOf(response) || {}
        this.form = Object.assign(emptyForm(), detail, { requestedPunchTime: String(detail.requestedPunchTime || '').slice(0, 16), targetPunchSlotKey: detail.targetPunchSlotKey || '', targetScheduleSegmentSnapshotId: detail.targetScheduleSegmentSnapshotId || null, originalPunchEventId: detail.originalPunchEventId || '' })
        this.showForm = true
        return this.loadPunchEvents(detail.scheduleId).then(() => this.preserveOriginalEvent(detail))
      }).catch(error => { this.error = attendanceErrorText(error, '补卡草稿加载失败') }).finally(() => { this.busy = false })
    },
    closeForm() { if (!this.busy) { this.showForm = false; this.form = emptyForm(); this.punchEvents = [] } },
    scheduleChanged() {
      this.form.originalPunchEventId = ''
      this.form.targetPunchSlotKey = ''
      this.form.targetScheduleSegmentSnapshotId = null
      return this.loadPunchEvents(this.form.scheduleId).then(() => {
        if (this.usesSlotTargeting && this.selectableCorrectionTargets.length) this.form.targetPunchSlotKey = this.selectableCorrectionTargets[0].punchSlotKey
        this.targetSlotChanged()
      })
    },
    loadPunchEvents(scheduleId) {
      if (!scheduleId) { this.punchEvents = []; return Promise.resolve() }
      return listAttendanceCorrectionEligiblePunchEvents(scheduleId).then(response => { this.punchEvents = this.rowsOf(response) })
        .catch(error => { this.punchEvents = []; this.error = attendanceErrorText(error, '原打卡事件加载失败') })
    },
    preserveOriginalEvent(detail) {
      const source = detail || {}
      if (!source.originalPunchEventId || this.punchEvents.some(event => String(event.punchEventId) === String(source.originalPunchEventId))) return
      const sameType = String(source.originalPunchType || '').toUpperCase() === String(source.targetPunchType || '').toUpperCase()
      this.punchEvents.push({
        punchEventId: source.originalPunchEventId,
        serverPunchTime: source.originalPunchTime,
        punchType: source.originalPunchType,
        scheduleSegmentSnapshotId: source.targetScheduleSegmentSnapshotId || null,
        punchSlotKey: sameType ? (source.targetPunchSlotKey || '') : '',
        segmentOrder: source.targetSegmentOrder,
        segmentLabel: source.targetSegmentLabelSnapshot
      })
    },
    correctionTypeChanged() { if (!this.requiresOriginalEvent) this.form.originalPunchEventId = ''; this.eventChanged() },
    targetTypeChanged() { this.setSuggestedTime() },
    targetSlotChanged() {
      const slot = this.selectedTargetSlot()
      this.form.targetScheduleSegmentSnapshotId = slot ? slot.scheduleSegmentSnapshotId : null
      if (slot && ['IN', 'OUT'].includes(slot.punchType)) this.form.targetPunchType = slot.punchType
      this.setSuggestedTime()
    },
    eventChanged() {
      const event = this.punchEvents.find(item => String(item.punchEventId) === String(this.form.originalPunchEventId))
      if (!event) return
      if (this.form.correctionType === 'WRONG_TIME') {
        this.form.targetPunchType = event.punchType
        if (event.punchSlotKey) {
          this.form.targetPunchSlotKey = event.punchSlotKey
          this.form.targetScheduleSegmentSnapshotId = event.scheduleSegmentSnapshotId || null
        }
      }
      if (this.form.correctionType === 'WRONG_TYPE') {
        this.form.targetPunchType = event.punchType === 'IN' ? 'OUT' : 'IN'
        const opposite = this.correctionTargets.find(slot => Number(slot.segmentOrder) === Number(event.segmentOrder) && slot.punchType === this.form.targetPunchType)
        this.form.targetPunchSlotKey = opposite ? opposite.punchSlotKey : ''
        this.form.targetScheduleSegmentSnapshotId = opposite ? opposite.scheduleSegmentSnapshotId : null
      }
      if (!this.form.requestedPunchTime) this.form.requestedPunchTime = String(event.serverPunchTime || '').slice(0, 16)
    },
    setSuggestedTime() {
      const schedule = this.selectedSchedule
      if (!schedule) return
      const businessDate = String(schedule.businessDate || '')
      const slot = this.selectedTargetSlot()
      const time = this.slotSuggestedTime(slot) || (this.form.targetPunchType === 'OUT' ? schedule.endTimeSnapshot : schedule.startTimeSnapshot)
      if (!businessDate || !time) return
      if (/\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}/.test(String(time))) {
        this.form.requestedPunchTime = String(time).replace(' ', 'T').slice(0, 16)
        return
      }
      const date = new Date(`${businessDate}T${String(time).slice(0, 8)}`)
      if (this.form.targetPunchType === 'OUT' && schedule.crossDaySnapshot) date.setDate(date.getDate() + 1)
      this.form.requestedPunchTime = localInput(date)
    },
    validateForm() {
      if (!this.form.scheduleId) return '请选择排班'
      if (this.usesSlotTargeting && (!this.form.targetPunchSlotKey || !this.form.targetScheduleSegmentSnapshotId)) return '请选择需要补卡的具体工作段'
      if (this.requiresOriginalEvent && !this.form.originalPunchEventId) return '请选择需要更正的原打卡事件'
      if (!this.form.requestedPunchTime) return '请填写申请更正时间'
      if (!String(this.form.reason || '').trim()) return '请填写更正原因'
      return ''
    },
    saveDraft(shouldSubmit) {
      if (this.busy) return
      const invalid = this.validateForm(); if (invalid) { this.error = invalid; return }
      this.busy = true; this.error = ''; this.success = ''
      const payload = { scheduleId: this.form.scheduleId, correctionType: this.form.correctionType, targetPunchType: this.form.targetPunchType, targetPunchSlotKey: this.usesSlotTargeting ? this.form.targetPunchSlotKey : null, targetScheduleSegmentSnapshotId: this.usesSlotTargeting ? this.form.targetScheduleSegmentSnapshotId : null, originalPunchEventId: this.form.originalPunchEventId || null, requestedPunchTime: this.form.requestedPunchTime, reason: this.form.reason, rowVersion: this.form.rowVersion }
      const save = this.form.correctionRequestId ? updateAttendanceCorrectionDraft(this.form.correctionRequestId, payload) : createAttendanceCorrectionDraft(payload)
      save.then(response => {
        const draft = dataOf(response)
        if (!draft || !draft.correctionRequestId) throw new Error('服务端未返回补卡草稿')
        // Keep the server identity/version before submit so a failed submit can be
        // retried against the same draft without creating a duplicate request.
        this.form = Object.assign({}, this.form, draft, {
          requestedPunchTime: String(draft.requestedPunchTime || this.form.requestedPunchTime || '').slice(0, 16)
        })
        return draft
      }).then(draft => shouldSubmit ? submitAttendanceCorrection(draft.correctionRequestId, draft.rowVersion).then(response => dataOf(response)) : draft)
        .then(result => {
          if (!result || !result.correctionRequestId) throw new Error('服务端未返回补卡申请')
          this.success = shouldSubmit ? '补卡申请已由服务端提交' : '补卡草稿已保存'; this.showForm = false; return this.loadRows()
        }).catch(error => { this.error = attendanceErrorText(error, shouldSubmit ? '补卡提交失败' : '补卡草稿保存失败') })
        .finally(() => { this.busy = false })
    },
    editableStatus(value) { return ['DRAFT', 'RETURNED'].includes(String(value || '').toUpperCase()) },
    correctionLabel(value) { return { MISSING_PUNCH: '缺卡补录', WRONG_TIME: '时间更正', WRONG_TYPE: '打卡类型更正', OTHER: '其他更正' }[value] || value || '补卡' },
    punchLabel(value) { return String(value || '').toUpperCase() === 'OUT' ? '下班卡' : '上班卡' },
    selectedTargetSlot() { return this.correctionTargets.find(slot => String(slot.punchSlotKey) === String(this.form.targetPunchSlotKey)) || null },
    slotSuggestedTime(slot) {
      const source = slot || {}
      return source.scheduledPunchTime || source.scheduledTime || source.targetTime || (source.punchType === 'OUT' ? (source.endAt || source.segmentEndTime) : (source.startAt || source.segmentStartTime)) || source.opensAt || ''
    },
    correctionSlotLabel(slot) {
      const source = slot || {}
      const scheduled = this.slotSuggestedTime(source)
      const time = scheduled ? String(scheduled).replace(' ', 'T').match(/(?:T)?(\d{2}:\d{2})/) : null
      return `${punchSlotLabel(source)}${time ? `·${time[1]}` : ''}`
    },
    eventPunchLabel(event) { return event && (event.punchSlotKey || event.segmentOrder || event.segmentLabel) ? punchSlotLabel(event) : this.punchLabel(event && event.punchType) },
    correctionRowPunchLabel(row) {
      if (!row || (!row.targetPunchSlotKey && !row.segmentOrder && !row.segmentLabel && !row.targetSegmentLabelSnapshot)) return this.punchLabel(row && row.targetPunchType)
      return punchSlotLabel(Object.assign({}, row, {
        punchType: row.targetPunchType,
        segmentOrder: row.segmentOrder || row.targetSegmentOrder,
        segmentLabel: row.segmentLabel || row.targetSegmentLabelSnapshot
      }))
    },
    originalPunchLabel(row) {
      if (!row || !row.originalPunchType) return '-'
      if (!row.targetPunchSlotKey) return this.punchLabel(row.originalPunchType)
      return punchSlotLabel({
        punchType: row.originalPunchType,
        segmentOrder: row.targetSegmentOrder,
        segmentLabel: row.targetSegmentLabelSnapshot
      })
    },
    correctionChangeText(row) {
      if (!row || !['WRONG_TIME', 'WRONG_TYPE'].includes(String(row.correctionType || '').toUpperCase()) || !row.originalPunchTime || !row.originalPunchType) return ''
      return `原始：${this.originalPunchLabel(row)} ${this.dateTime(row.originalPunchTime)} → 申请：${this.correctionRowPunchLabel(row)} ${this.dateTime(row.requestedPunchTime)}`
    },
    statusLabel(value) { return { DRAFT: '草稿', SUBMITTING: '提交中', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', RETURNED: '已退回', CANCELLED: '已撤回' }[String(value || '').toUpperCase()] || '未知' },
    statusTone(value) { const key = String(value || '').toUpperCase(); return key === 'APPROVED' ? 'success' : ['REJECTED', 'RETURNED'].includes(key) ? 'danger' : ['PENDING', 'SUBMITTING'].includes(key) ? 'warning' : 'info' },
    dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' },
    scheduleLabel(row) { return `${row.businessDate || '-'}·${row.shiftNameSnapshot || ''}${row.shiftNameSnapshot ? '·' : ''}${String(row.startTimeSnapshot || '').slice(0, 5)}-${String(row.endTimeSnapshot || '').slice(0, 5)}${row.crossDaySnapshot ? '·次日' : ''}${Array.isArray(row.punchSlots) && row.punchSlots.length > 2 ? '·分段打卡' : ''}` }
  }
}
</script>

<style scoped>
.correction-card { padding: 18px; border: 1px solid rgba(91,116,112,.12); border-radius: 20px; background: rgba(255,255,255,.94); box-shadow: 0 10px 28px rgba(32,57,73,.08); }
.section-heading, .correction-row__top, .correction-row__actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.section-heading small { color: #4b817a; font-weight: 700; }.section-heading h2 { margin: 3px 0 0; font-size: 19px; }
.section-heading > button, .correction-filter button { min-height: 36px; padding: 0 12px; border: 0; border-radius: 10px; background: #e7f3f1; color: #176d63; font-weight: 700; }
.correction-message { margin: 12px 0; padding: 10px 12px; border-radius: 10px; line-height: 1.5; }.correction-message.error { background: #fff0f0; color: #a53f3f; }.correction-message.warning { background: #fff5df; color: #8a5a00; }.correction-message.success { background: #e8f7f2; color: #176d63; }
.correction-form { display: grid; gap: 13px; margin-top: 16px; }.correction-form label { display: grid; gap: 6px; color: #5e7076; font-size: 12px; font-weight: 700; }
.correction-form input, .correction-form select, .correction-form textarea, .correction-filter select { box-sizing: border-box; width: 100%; min-height: 42px; padding: 9px 10px; border: 1px solid #d8e1e2; border-radius: 11px; background: #fff; color: #21383d; font: inherit; }
.correction-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.evidence-note { display: flex; gap: 8px; padding: 11px; border-radius: 10px; background: #edf5f4; color: #416a67; line-height: 1.5; }
.correction-actions { display: grid; grid-template-columns: 1fr 1fr 1.25fr; gap: 8px; }.correction-actions button { min-height: 43px; border: 0; border-radius: 11px; background: #e9eeee; color: #53666b; font-weight: 700; }.correction-actions .submit { background: #176d63; color: #fff; }
.correction-filter { display: grid; grid-template-columns: 1fr auto; gap: 8px; margin: 14px 0; }.correction-state { padding: 28px 8px; color: #718087; text-align: center; }
.correction-row { padding: 14px 0; border-top: 1px solid #edf1f2; }.correction-row__top > div { display: grid; gap: 3px; }.correction-row__top small { color: #718087; font-size: 11px; }.correction-row p { margin: 8px 0 0; color: #52656b; line-height: 1.5; }
.correction-change { display: grid; gap: 3px; padding: 9px 10px; border-radius: 9px; background: #f3f7f7; }.correction-change small { color: #7a898e; }
.correction-status { padding: 5px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }.correction-status.success { background: #e8f7f2; color: #176d63; }.correction-status.warning { background: #fff5df; color: #9a6712; }.correction-status.danger { background: #fff0f0; color: #a53f3f; }.correction-status.info { background: #eef1f3; color: #637278; }
.correction-row__actions { justify-content: flex-end; margin-top: 10px; }.correction-row__actions button { padding: 6px 9px; border: 0; border-radius: 8px; background: #edf5f4; color: #276d65; }
@media (max-width: 390px) { .correction-grid { grid-template-columns: 1fr; }.correction-actions { grid-template-columns: 1fr 1fr; }.correction-actions .submit { grid-column: 1 / -1; } }
</style>
