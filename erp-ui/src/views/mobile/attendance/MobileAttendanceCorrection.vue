<template>
  <section class="correction-card">
    <div class="section-heading">
      <div><small>保留原始证据</small><h2>{{ approvalFocus ? '补卡详情' : '我的补卡' }}</h2></div>
      <button v-if="canSelf && !approvalFocus" type="button" :disabled="busy || !!recoveryAttempt || scheduleLoading || !!scheduleError" @click="openNew">新建补卡</button>
    </div>
    <div v-if="error" class="correction-message error" role="alert">{{ error }}</div>
    <div v-if="scheduleError" class="correction-message warning" role="status">{{ scheduleError }}</div>
    <div v-if="success" class="correction-message success" role="status">{{ success }}</div>

    <div v-if="recoveryAttempt && recoveryStorageUnavailable" class="correction-message warning" role="alert">浏览器未能保存恢复标记，请在核对完成前保留当前页面。原申请标识：{{ recoveryAttempt.clientRequestId }}。</div>
    <div v-if="recoveryAttempt" class="correction-message warning" role="status">原补卡结果尚待核对。<button type="button" :disabled="busy" @click="reconcileCorrection(true)">核对原申请并恢复</button></div>
    <form v-if="showForm && canSelf" class="correction-form" @submit.prevent>
      <fieldset :disabled="busy || (!!recoveryAttempt && !restoredInputRequired)" class="correction-fields">
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
      </fieldset>
      <div class="correction-actions">
        <button type="button" :disabled="busy || !!recoveryAttempt" @click="closeForm">取消</button>
        <button type="button" :disabled="busy || !!recoveryAttempt" @click="saveDraft(false)">{{ busy ? '处理中…' : '保存草稿' }}</button>
        <button class="submit" type="button" :disabled="busy || !!recoveryAttempt" @click="saveDraft(true)">保存并提交</button>
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
          <button v-if="canSelf && row.status === 'SUBMITTING'" type="button" :disabled="busy || !!recoveryAttempt" @click="retrySubmitting(row)">重试原申请提交</button>
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
  getAttendanceCorrectionByClientRequest,
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
const recovery = require('@/utils/correctionRequestRecovery')
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { normalizePositiveDecimalId } = require('@/utils/positiveDecimalId')
const { definiteRejection } = require('@/utils/leaveBalanceUi')
const pad = number => String(number).padStart(2, '0')
const dateOnly = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const localInput = date => `${dateOnly(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
const emptyForm = () => ({ correctionRequestId: null, clientRequestId: recovery.newClientRequestId(), scheduleId: '', correctionType: 'MISSING_PUNCH', targetPunchType: 'IN', targetPunchSlotKey: '', targetScheduleSegmentSnapshotId: null, originalPunchEventId: '', requestedPunchTime: '', reason: '', rowVersion: null, status: 'DRAFT' })

export default {
  name: 'MobileAttendanceCorrection',
  props: { todoBusinessId: { type: [String, Number], default: '' } },
  data() {
    return { recoveryStorageUnavailable: false, restoredInputRequired: false, recoveryAttempt: null, editorGeneration: 0, savedBusiness: null, loading: false, scheduleLoading: false, busy: false, error: '', scheduleError: '', success: '', status: '', rows: [], schedules: [], punchEvents: [], showForm: false, form: emptyForm() }
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
  watch: {
    todoBusinessId() { this.invalidateCorrectionEditor(); this.restoreRecovery(); this.loadRows() },
    '$store.state.user.sessionRevision'() { this.invalidateCorrectionEditor(); this.restoreRecovery(); this.refresh() },
    '$route.fullPath'() { this.invalidateCorrectionEditor(); this.restoreRecovery(); this.refresh() }
  },
  beforeDestroy() { this.correctionScope().deactivate(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.correctionShopChanged) },
  deactivated() { this.correctionScope().deactivate() },
  activated() { this.correctionScope().activate(); this.restoreRecovery() },
  created() {
    this.restoreRecovery()
    if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.correctionShopChanged)
    if (this.canSelf) this.refresh()
    else this.loadRows()
  },
  methods: {
    correctionIdentity() {
      const user = this.$store && this.$store.state && this.$store.state.user || {}
      const shop = getSelectedDeptContext() || {}
      return { actor: String(user.id || this.$store && this.$store.getters && this.$store.getters.id || ''), session: user.sessionRevision || 0, shop: String(shop.deptId || '') }
    },
    correctionScope() {
      if (!this._correctionScope) this._correctionScope = createUiOperationScope(() => ({ ...this.correctionIdentity(), generation: this.editorGeneration, focus: String(this.todoBusinessId || '') }))
      return this._correctionScope
    },
    invalidateCorrectionEditor() {
      this.correctionScope().invalidate(); this.editorGeneration += 1; this.busy = false
      this.showForm = false; this.form = emptyForm(); this.savedBusiness = null; this.recoveryAttempt = null; this.restoredInputRequired = false; this.recoveryStorageUnavailable = false
    },
    correctionShopChanged() { this.invalidateCorrectionEditor(); this.restoreRecovery(); this.refresh() },
    restoreRecovery() {
      if (!this.canSelf || this.recoveryAttempt) return
      this.recoveryAttempt = recovery.read(this.correctionIdentity())
      if (this.recoveryAttempt) this.error = '上次补卡结果尚待核对，请先核对原申请；不会重复新建。'
    },
    rememberAttempt(attempt) {
      this.recoveryAttempt = attempt
      if (!attempt) this.restoredInputRequired = false
      this.recoveryStorageUnavailable = !!attempt && !recovery.write(this.correctionIdentity(), attempt)
      if (!attempt) recovery.write(this.correctionIdentity(), null)
    },
    requireCorrectionDetail(response, attempt) {
      const row = dataOf(response)
      const id = row && normalizePositiveDecimalId(row.correctionRequestId)
      if (!id || attempt.id && id !== String(attempt.id) || !attempt.id && String(row.clientRequestId || '') !== attempt.clientRequestId)
        throw new Error('服务端补卡身份与原申请不一致，请核对')
      const owner = this.correctionIdentity()
      if (row.userId != null && owner.actor && String(row.userId) !== owner.actor || row.shopId != null && owner.shop && String(row.shopId) !== owner.shop)
        throw new Error('补卡所属员工或门店已变化，请核对')
      if (!['DRAFT', 'RETURNED', 'SUBMITTING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].includes(String(row.status || '').toUpperCase()) || row.rowVersion == null)
        throw new Error('服务端补卡状态不完整，请重试核对')
      return { ...row, correctionRequestId: id }
    },
    mergeCorrection(row) {
      this.form = Object.assign(emptyForm(), row, { clientRequestId: row.clientRequestId || this.form.clientRequestId, requestedPunchTime: String(row.requestedPunchTime || '').replace(' ', 'T').slice(0, 16) })
      this.savedBusiness = recovery.businessOf(row)
    },
    finishCorrection(row, submitted) {
      this.mergeCorrection(row); this.rememberAttempt(null)
      this.success = submitted ? (row.status === 'SUBMITTING' ? '补卡提交处理中，可在原记录重试推进' : `补卡申请当前状态：${this.statusLabel(row.status)}`) : '补卡草稿已保存'
      this.showForm = false
      return this.loadRows()
    },
    rowsOf(response) { const payload = dataOf(response); return Array.isArray(payload) ? payload : [] },
    loadEligibleSchedules() {
      const scope = this.correctionScope(), token = scope.begin('schedules')
      this.scheduleLoading = true; this.scheduleError = ''
      const to = new Date(); const from = new Date(to); from.setDate(from.getDate() - 365)
      return listAttendanceCorrectionEligibleSchedules({ dateFrom: dateOnly(from), dateTo: dateOnly(to) }).then(response => { if (scope.isCurrent(token)) this.schedules = this.rowsOf(response) })
        .catch(error => {
          if (!scope.isCurrent(token)) return
          this.schedules = []
          this.scheduleError = `${attendanceErrorText(error, '可补卡排班加载失败')}；暂不能新建补卡，历史补卡记录仍可查看。`
        }).finally(() => { if (scope.isCurrent(token)) this.scheduleLoading = false })
    },
    refresh() {
      const schedules = this.canSelf && !this.approvalFocus
        ? this.loadEligibleSchedules()
        : Promise.resolve()
      return Promise.all([schedules, this.loadRows()])
    },
    loadRows() {
      if (!this.canRead) return Promise.resolve()
      const scope = this.correctionScope(), token = scope.begin('list', this.status)
      this.loading = true
      if (!this.recoveryAttempt) this.error = ''
      let request
      if (this.approvalFocus) request = getAttendanceCorrection(this.todoBusinessId).then(response => [dataOf(response)])
      else if (this.canSelf) request = listMyAttendanceCorrections({ status: this.status || undefined }).then(response => dataOf(response))
      else {
        const context = getSelectedDeptContext() || {}
        if (!context.isStore || !context.deptId) { this.error = '请先切换到待审批申请所属门店'; this.loading = false; return Promise.resolve() }
        request = listShopAttendanceCorrections({ shopId: context.deptId, status: this.status || undefined }).then(response => dataOf(response))
      }
      return request.then(rows => { if (!scope.isCurrent(token, this.status)) return; this.rows = (Array.isArray(rows) ? rows : []).filter(Boolean) })
        .catch(error => { if (scope.isCurrent(token, this.status)) { this.error = attendanceErrorText(error, '补卡记录加载失败') } })
        .finally(() => { if (scope.isCurrent(token, this.status)) this.loading = false })
    },
    openNew() {
      if (this.busy || this.recoveryAttempt) return
      this.invalidateCorrectionEditor()
      if (!this.schedules.length) { this.scheduleError = '近一年内没有可用的已发布排班，不能新建补卡。'; return }
      this.form = emptyForm(); this.form.scheduleId = this.schedules[0].scheduleId; this.showForm = true; this.error = ''; this.success = ''; this.scheduleChanged()
    },
    openRow(row) {
      const scope = this.correctionScope(), token = scope.begin('row', String(row.correctionRequestId))
      return getAttendanceCorrection(row.correctionRequestId).then(response => {
        if (!scope.isCurrent(token)) return
        const detail = this.requireCorrectionDetail(response, { id: String(row.correctionRequestId) })
        this.rows = this.rows.map(item => item.correctionRequestId === detail.correctionRequestId ? detail : item)
      }).catch(error => { if (scope.isCurrent(token)) this.error = attendanceErrorText(error, '补卡详情加载失败') })
    },
    editRow(row) {
      if (this.busy || this.recoveryAttempt) return Promise.resolve()
      this.invalidateCorrectionEditor()
      const scope = this.correctionScope(), token = scope.begin('edit', String(row.correctionRequestId))
      this.busy = true; this.error = ''
      return getAttendanceCorrection(row.correctionRequestId).then(response => {
        if (!scope.isCurrent(token)) return
        const detail = this.requireCorrectionDetail(response, { id: String(row.correctionRequestId) })
        if (!this.editableStatus(detail.status)) { this.success = `补卡申请当前状态：${this.statusLabel(detail.status)}`; return }
        this.savedBusiness = recovery.businessOf(detail)
        this.form = Object.assign(emptyForm(), detail, { requestedPunchTime: String(detail.requestedPunchTime || '').slice(0, 16), targetPunchSlotKey: detail.targetPunchSlotKey || '', targetScheduleSegmentSnapshotId: detail.targetScheduleSegmentSnapshotId || null, originalPunchEventId: detail.originalPunchEventId || '' })
        this.showForm = true
        return this.loadPunchEvents(detail.scheduleId).then(() => { if (scope.isCurrent(token)) this.preserveOriginalEvent(detail) })
      }).catch(error => { if (scope.isCurrent(token)) this.error = attendanceErrorText(error, '补卡草稿加载失败') }).finally(() => { if (scope.isCurrent(token)) this.busy = false })
    },
    closeForm() { if (!this.busy && !this.recoveryAttempt) { this.invalidateCorrectionEditor(); this.punchEvents = [] } },
    scheduleChanged() {
      const scope = this.correctionScope(), token = scope.begin('schedule-change', String(this.form.scheduleId))
      this.form.originalPunchEventId = ''
      this.form.targetPunchSlotKey = ''
      this.form.targetScheduleSegmentSnapshotId = null
      return this.loadPunchEvents(this.form.scheduleId).then(() => {
        if (!scope.isCurrent(token, String(this.form.scheduleId))) return
        if (this.usesSlotTargeting && this.selectableCorrectionTargets.length) this.form.targetPunchSlotKey = this.selectableCorrectionTargets[0].punchSlotKey
        this.targetSlotChanged()
      })
    },
    loadPunchEvents(scheduleId) {
      const scope = this.correctionScope(), token = scope.begin('events', String(scheduleId))
      if (!scheduleId) { this.punchEvents = []; return Promise.resolve() }
      return listAttendanceCorrectionEligiblePunchEvents(scheduleId).then(response => { if (scope.isCurrent(token, String(this.form.scheduleId))) this.punchEvents = this.rowsOf(response) })
        .catch(error => { if (scope.isCurrent(token, String(this.form.scheduleId))) { this.punchEvents = []; this.error = attendanceErrorText(error, '原打卡事件加载失败') } })
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
    async saveDraft(shouldSubmit) {
      if (this.busy) return
      if (this.recoveryAttempt) return this.reconcileCorrection(true)
      if (!this.showForm || !this.canSelf || !this.editableStatus(this.form.status)) return
      const invalid = this.validateForm(); if (invalid) { this.error = invalid; return }
      const scope = this.correctionScope(), token = scope.begin('save')
      const payload = { scheduleId: this.form.scheduleId, correctionType: this.form.correctionType, targetPunchType: this.form.targetPunchType,
        targetPunchSlotKey: this.usesSlotTargeting ? this.form.targetPunchSlotKey : null,
        targetScheduleSegmentSnapshotId: this.usesSlotTargeting ? this.form.targetScheduleSegmentSnapshotId : null,
        originalPunchEventId: this.form.originalPunchEventId || null, requestedPunchTime: this.form.requestedPunchTime, reason: this.form.reason, rowVersion: this.form.rowVersion }
      const id = normalizePositiveDecimalId(this.form.correctionRequestId)
      if (this.form.correctionRequestId && !id) { this.error = '补卡编号无效，请重新从列表打开'; return }
      const clientRequestId = this.form.clientRequestId || recovery.newClientRequestId()
      if (!id) payload.clientRequestId = clientRequestId
      this.busy = true; this.error = ''; this.success = ''
      try {
        const attempt = { stage: id ? 'UPDATE' : 'CREATE', id, clientRequestId, payload, shouldSubmit: !!shouldSubmit,
          baseBusiness: this.savedBusiness, baseVersion: this.form.rowVersion, payloadHash: await recovery.fingerprint(payload) }
        if (!scope.isCurrent(token)) return
        this.rememberAttempt(attempt)
        let row
        try {
          row = this.requireCorrectionDetail(id ? await updateAttendanceCorrectionDraft(id, payload) : await createAttendanceCorrectionDraft(payload), attempt)
          if (!recovery.sameBusiness(row, payload)) throw new Error('服务端补卡内容与本次保存不同，请核对')
        } catch (error) {
          if (!scope.isCurrent(token)) return
          return await this.recoverCorrectionAttempt(attempt, token, false, error)
        }
        if (!scope.isCurrent(token)) return
        this.mergeCorrection(row)
        if (shouldSubmit && this.editableStatus(row.status)) return await this.submitKnownCorrection(row, attempt, token)
        return await this.finishCorrection(row, !this.editableStatus(row.status))
      } catch (error) { if (scope.isCurrent(token)) this.error = `${attendanceErrorText(error, '补卡保存结果待核对')}；请核对原申请后继续` }
      finally { if (scope.isCurrent(token)) this.busy = false }
    },
    async submitKnownCorrection(row, previous, token) {
      const scope = this.correctionScope()
      if (!scope.isCurrent(token)) return
      const attempt = { ...previous, stage: 'SUBMIT', id: String(row.correctionRequestId), payload: recovery.businessOf(row), baseVersion: row.rowVersion, shouldSubmit: true }
      this.rememberAttempt(attempt)
      try {
        const result = this.requireCorrectionDetail(await submitAttendanceCorrection(attempt.id, row.rowVersion), attempt)
        if (!scope.isCurrent(token)) return
        if (!recovery.sameBusiness(result, attempt.payload) || this.editableStatus(result.status)) throw new Error('补卡提交结果尚未确认')
        return await this.finishCorrection(result, true)
      } catch (error) {
        if (!scope.isCurrent(token)) return
        return await this.recoverCorrectionAttempt(attempt, token, false, error)
      }
    },
    async reconcileCorrection(allowReplay) {
      if (this.busy || !this.recoveryAttempt) return
      const scope = this.correctionScope(), token = scope.begin('save'), attempt = this.recoveryAttempt
      this.busy = true; this.error = ''
      try {
        if (this.restoredInputRequired) {
          const invalid = this.validateForm(); if (invalid) throw new Error(invalid)
          const payload = { ...recovery.businessOf(this.form), clientRequestId: attempt.clientRequestId, rowVersion: null }
          ;['originalPunchEventId', 'targetScheduleSegmentSnapshotId', 'targetPunchSlotKey'].forEach(key => { if (!payload[key]) payload[key] = null })
          const digest = await recovery.fingerprint(payload)
          if (!scope.isCurrent(token)) return
          if (!digest || digest !== attempt.payloadHash) throw new Error('填写内容与原保存摘要不一致，尚未发送，请按原内容核对')
          attempt.payload = payload; attempt.restored = false; this.restoredInputRequired = false
        }
        return await this.recoverCorrectionAttempt(attempt, token, allowReplay === true, null)
      }
      catch (error) { if (scope.isCurrent(token)) this.error = `${attendanceErrorText(error, '补卡结果仍待核对')}；保留原申请标识，请稍后重试核对` }
      finally { if (scope.isCurrent(token)) this.busy = false }
    },
    async recoverCorrectionAttempt(attempt, token, allowReplay, originalError) {
      const scope = this.correctionScope(); if (!scope.isCurrent(token)) return
      let row
      try {
        row = this.requireCorrectionDetail(attempt.id ? await getAttendanceCorrection(attempt.id) : await getAttendanceCorrectionByClientRequest(attempt.clientRequestId), attempt)
      } catch (error) {
        if (!scope.isCurrent(token)) return
        const notFound = !attempt.id && /CORRECTION_CLIENT_REQUEST_NOT_FOUND/.test(attendanceErrorText(error, ''))
        if (notFound && allowReplay && attempt.restored && attempt.stage === 'CREATE' && !attempt.payload) {
          if (!attempt.payloadHash) throw new Error('原保存尚未找到，且当前设备没有可核对的内容摘要，请保留此标识联系管理员核对')
          this.form = emptyForm(); this.form.clientRequestId = attempt.clientRequestId
          this.showForm = true; this.restoredInputRequired = true
          this.error = '尚未找到原申请，请按原内容填写后再次核对。内容摘要一致时只用原标识重放保存。'
          return
        }
        if (notFound && originalError && definiteRejection(originalError)) { this.rememberAttempt(null); throw originalError }
        if (notFound && allowReplay && attempt.stage === 'CREATE' && attempt.payload) {
          row = this.requireCorrectionDetail(await createAttendanceCorrectionDraft(attempt.payload), attempt)
        } else throw originalError || error
      }
      if (!scope.isCurrent(token)) return
      if (attempt.payload && !recovery.sameBusiness(row, attempt.payload)) {
        if (attempt.stage === 'UPDATE' && attempt.baseBusiness && recovery.sameBusiness(row, attempt.baseBusiness) && String(row.rowVersion) === String(attempt.baseVersion) && this.editableStatus(row.status)) {
          if (originalError && definiteRejection(originalError)) { this.rememberAttempt(null); throw originalError }
          if (allowReplay) row = this.requireCorrectionDetail(await updateAttendanceCorrectionDraft(attempt.id, attempt.payload), attempt)
          else throw originalError || new Error('上次保存尚未确认，重试核对后可按原内容恢复')
        } else throw new Error('服务端补卡内容已变化，已保留当前输入，请到原申请核对')
      }
      if (!scope.isCurrent(token)) return
      if (attempt.payload && !recovery.sameBusiness(row, attempt.payload)) throw new Error('恢复结果与原补卡内容不一致')
      if (attempt.restored) {
        // After a reload we only read the original record. Never reconstruct or resubmit personal fields from storage.
        const sameDigest = attempt.payloadHash && await recovery.fingerprint(row) === attempt.payloadHash
        if (!scope.isCurrent(token)) return
        this.mergeCorrection(row); this.rememberAttempt(null); this.showForm = this.editableStatus(row.status)
        if (!sameDigest) this.error = '已找回服务端记录，但未确认上次修改内容一致，请核对后再编辑；不会自动提交。'
        this.success = `已找回原申请，请核对服务端内容；当前状态：${this.statusLabel(row.status)}`
        return this.loadRows()
      }
      if (!this.editableStatus(row.status)) return this.finishCorrection(row, true)
      this.mergeCorrection(row)
      if (attempt.stage === 'SUBMIT') {
        if (allowReplay) return this.submitKnownCorrection(row, attempt, token)
        if (originalError && definiteRejection(originalError)) this.rememberAttempt(null)
        throw originalError || new Error('上次提交尚未确认，请核对后重试原申请提交')
      }
      if (attempt.shouldSubmit) return this.submitKnownCorrection(row, attempt, token)
      return this.finishCorrection(row, false)
    },
    async retrySubmitting(row) {
      if (this.busy || this.recoveryAttempt || !this.canSelf || row.status !== 'SUBMITTING') return
      const scope = this.correctionScope(), token = scope.begin('save')
      this.busy = true; this.error = ''
      try {
        const detail = this.requireCorrectionDetail(await getAttendanceCorrection(row.correctionRequestId), { id: String(row.correctionRequestId) })
        if (!scope.isCurrent(token)) return
        if (detail.status !== 'SUBMITTING') return await this.finishCorrection(detail, true)
        return await this.submitKnownCorrection(detail, { clientRequestId: detail.clientRequestId || recovery.newClientRequestId(), payloadHash: await recovery.fingerprint(detail) }, token)
      } catch (error) { if (scope.isCurrent(token)) this.error = attendanceErrorText(error, '原补卡推进失败，请重试核对') }
      finally { if (scope.isCurrent(token)) this.busy = false }
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
.correction-fields { display: grid; gap: 13px; min-width: 0; border: 0; padding: 0; margin: 0; }
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
