<template>
  <section class="attendance-module">
    <el-card shadow="never" class="search-card oa-filter-card">
      <div class="schedule-toolbar">
        <div class="week-switcher">
          <el-button size="small" icon="el-icon-arrow-left" :disabled="weekNavigationLocked" @click="moveWeek(-7)">上一周</el-button>
          <el-date-picker v-model="weekAnchor" type="week" format="yyyy 第 WW 周" value-format="yyyy-MM-dd" size="small" :clearable="false" :disabled="weekNavigationLocked" @change="handleWeekChange" />
          <el-button size="small" :disabled="weekNavigationLocked" @click="moveWeek(7)">下一周<i class="el-icon-arrow-right el-icon--right" /></el-button>
          <strong>{{ weekDays[0].date }} 至 {{ weekDays[6].date }}</strong>
        </div>
        <div class="schedule-actions">
          <el-input
            v-model.trim="employeeKeyword"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            placeholder="员工姓名/工号"
            @keyup.enter.native="searchEmployees"
            @clear="searchEmployees"
          />
          <el-button size="small" icon="el-icon-refresh" @click="refresh">刷新</el-button>
          <el-button v-hasPermi="['oa:attendance:schedule:add', 'oa:attendance:schedule:edit']" type="primary" plain size="small" :loading="saving" @click="handleSave">保存草稿</el-button>
          <el-button v-hasPermi="['oa:attendance:schedule:publish']" type="success" size="small" :loading="publishing" @click="handlePublish">发布本周排班</el-button>
        </div>
      </div>
      <div v-if="validStore && canEditSchedule" class="bulk-schedule-tools">
        <div class="bulk-selection-summary">
          <strong>已选 {{ selectedEmployees.length }} 人</strong>
          <el-button type="text" size="small" @click="selectVisibleEmployees">勾选本页</el-button>
          <el-button type="text" size="small" @click="clearEmployeeSelection">清空勾选</el-button>
          <span>目标周：{{ weekDays[0].date }} 至 {{ weekDays[6].date }}；仅填写空白格</span>
        </div>
        <el-checkbox-group v-model="bulkDates" size="small">
          <el-checkbox v-for="day in weekDays" :key="day.date" :label="day.date">{{ day.label }}</el-checkbox>
        </el-checkbox-group>
        <div class="bulk-fill-actions">
          <el-select v-model="bulkShiftId" size="small" placeholder="选择班次"><el-option v-for="shift in shifts" :key="shiftId(shift)" :label="shiftLabel(shift)" :value="shiftId(shift)" /></el-select>
          <el-select v-model="bulkSiteId" size="small" placeholder="选择考勤地点"><el-option v-for="site in sites" :key="siteId(site)" :label="siteLabel(site)" :value="siteId(site)" /></el-select>
          <el-button type="primary" plain size="small" :disabled="loading || weekNavigationLocked || copying" @click="applyBulkSchedule">批量填充空白格</el-button>
          <el-button size="small" :loading="copying" :disabled="loading || weekNavigationLocked" @click="copyPreviousWeek">复制上周为本周草稿</el-button>
        </div>
        <p v-if="bulkNotice" role="status">{{ bulkNotice }}</p>
      </div>
      <el-alert v-if="batchNotice" :title="batchNotice" :type="batchProgress && batchProgress.failed ? 'warning' : 'info'" :closable="false" show-icon />
    </el-card>

    <el-alert
      v-if="!validStore"
      title="请先切换到需要排班的门店"
      description="排班必须绑定当前授权门店，集团、公司或仓库上下文不能直接发布。"
      type="warning"
      show-icon
      :closable="false"
      class="context-alert"
    />

    <el-card v-else shadow="never" class="table-card oa-table-card schedule-card">
      <div slot="header" class="schedule-card-header">
        <div>
          <h2>{{ shopContext.deptName || '当前门店' }}·周排班</h2>
          <p>每个排班必须绑定已启用考勤地点；发布后班次和地点围栏均按快照锁定。</p>
        </div>
        <div class="schedule-legend"><span class="draft" />草稿 <span class="published" />已发布</div>
      </div>
      <el-alert
        v-if="conflictNotice"
        :title="conflictNotice"
        type="warning"
        show-icon
        :closable="false"
        class="site-alert"
      />
      <el-alert
        v-if="!sites.length"
        title="当前门店没有已启用的考勤地点"
        description="请先在“考勤地点”页签新增、核对并启用地点，再创建排班。"
        type="warning"
        show-icon
        :closable="false"
        class="site-alert"
      />
      <div class="employee-page-summary">
        <span>已加载 {{ employeeQueryRows.length }} / {{ employeeTotal }} 名可排班员工；含已有排班与本地草稿共 {{ employees.length }} 人</span>
        <el-button v-if="hasMoreEmployees" type="text" :loading="employeeLoadingMore" @click="loadMoreEmployees">加载更多员工</el-button>
      </div>
      <el-table v-loading="loading" :data="visibleEmployees" size="small" border empty-text="当前门店没有可排班员工" class="weekly-table">
        <el-table-column v-if="canEditSchedule" label="勾选" fixed width="58">
          <template slot-scope="scope"><el-checkbox :value="Boolean(employeeSelection[String(employeeId(scope.row))])" :aria-label="'选择' + employeeName(scope.row)" @change="selectEmployee(scope.row, $event)" /></template>
        </el-table-column>
        <el-table-column label="员工" fixed width="140">
          <template slot-scope="scope">
            <strong>{{ employeeName(scope.row) }}</strong>
            <div class="cell-subtitle">{{ scope.row.userName || scope.row.employeeNo || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column v-for="day in weekDays" :key="day.date" :label="day.label" min-width="220">
          <template slot-scope="scope">
            <div :class="['schedule-cell', scheduleStatusClass(cell(scope.row, day.date))]">
              <el-select
                :value="cell(scope.row, day.date).shiftId"
                :disabled="publishing || publishLocked || isPublished(cell(scope.row, day.date)) || cell(scope.row, day.date).overwriteBlocked || !canEditSchedule || (!sites.length && !cell(scope.row, day.date).shiftId)"
                size="mini"
                :clearable="!cell(scope.row, day.date).scheduleId || canRemoveSchedule"
                placeholder="休息/未排"
                @input="setCell(scope.row, day.date, 'shiftId', $event)"
              >
                <el-option v-for="shift in shifts" :key="shiftId(shift)" :label="shiftLabel(shift)" :value="shiftId(shift)" />
              </el-select>
              <el-select
                v-if="cell(scope.row, day.date).shiftId && !isPublished(cell(scope.row, day.date))"
                :value="cell(scope.row, day.date).siteId"
                :disabled="publishing || publishLocked || !canEditSchedule || cell(scope.row, day.date).overwriteBlocked"
                size="mini"
                clearable
                placeholder="必选考勤地点"
                @input="setCell(scope.row, day.date, 'siteId', $event)"
              >
                <el-option v-for="site in sites" :key="siteId(site)" :label="siteLabel(site)" :value="siteId(site)" />
              </el-select>
              <div v-if="cell(scope.row, day.date).shiftId && isPublished(cell(scope.row, day.date))" class="selected-site-detail">
                <i class="el-icon-location-outline" />{{ cellSiteLabel(cell(scope.row, day.date)) }}
              </div>
              <small v-else-if="cell(scope.row, day.date).shiftId && !cell(scope.row, day.date).siteId" class="site-required">请选择考勤地点</small>
              <div v-if="cell(scope.row, day.date).shiftId" class="selected-shift-detail">
                <span>{{ cellShiftPeriods(cell(scope.row, day.date)) }}</span>
                <small>{{ cellPunchModeText(cell(scope.row, day.date)) }}</small>
              </div>
              <small v-if="isPublished(cell(scope.row, day.date))">已发布·快照锁定</small>
              <small v-else-if="cell(scope.row, day.date).scheduleId">草稿</small>
              <div v-if="cell(scope.row, day.date).needsReview" class="conflict-review">
                <small>{{ conflictReviewText(cell(scope.row, day.date)) }}</small>
                <el-button v-if="!cell(scope.row, day.date).overwriteBlocked" type="text" size="mini" @click="resolveConflictKeepLocal(scope.row, day.date)">保留本地并使用最新版本</el-button>
                <el-button type="text" size="mini" @click="resolveConflictUseRemote(scope.row, day.date)">采用远端</el-button>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="employees.length > employeePageSize" :current-page.sync="employeePage" :page-size="employeePageSize" :total="employees.length" layout="total, prev, pager, next" class="employee-pagination" />
    </el-card>
  </section>
</template>

<script>
import {
  deleteAttendanceSchedule,
  listAttendanceSites,
  listAttendanceSchedules,
  listAttendanceEmployeeOptions,
  listAttendanceShifts,
  publishAttendanceSchedules,
  saveAttendanceScheduleBatch
} from '@/api/oa/attendanceV2'
import { checkPermi } from '@/utils/permission'

const SEGMENT_PUNCH_MODE = 'PER_WORK_SEGMENT'
const SILENT_READ = { silentError: true }
const VERSION_CONFLICT = 'SCHEDULE_VERSION_CONFLICT'
const SCHEDULE_BATCH_SIZE = 500
const EMPLOYEE_FETCH_SIZE = 100

function offsetTimeText(offset) {
  const value = Number(offset)
  if (!Number.isFinite(value)) return '--:--'
  const minute = ((Math.trunc(value) % 1440) + 1440) % 1440
  const day = Math.floor(value / 1440)
  const time = `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
  return day > 0 ? `+${day}天 ${time}` : time
}

function workSegments(row) {
  const candidates = [row && row.segments, row && row.segmentSnapshots, row && row.shiftSegments]
  const source = candidates.find(Array.isArray) || []
  return source
    .filter(segment => String(segment && segment.segmentType || 'WORK').toUpperCase() === 'WORK')
    .slice()
    .sort((left, right) => Number(left.segmentOrder || 0) - Number(right.segmentOrder || 0))
}

function shiftPeriodsText(row) {
  const work = workSegments(row)
  if (work.length) {
    return work
      .map(segment => `${offsetTimeText(segment.startMinuteOffset)}-${offsetTimeText(segment.endMinuteOffset)}`)
      .join(' / ')
  }
  const start = String(row && (row.startTimeSnapshot || row.startTime) || '').slice(0, 5)
  const end = String(row && (row.endTimeSnapshot || row.endTime) || '').slice(0, 5)
  return start && end ? `${start}-${end}` : ''
}

function dateText(date) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}

function parseLocalDate(value) {
  const parts = String(value || '').slice(0, 10).split('-').map(Number)
  if (parts.length !== 3 || parts.some(part => !Number.isFinite(part))) return new Date()
  return new Date(parts[0], parts[1] - 1, parts[2])
}

function mondayOf(value) {
  const date = value instanceof Date ? new Date(value) : parseLocalDate(value)
  date.setHours(12, 0, 0, 0)
  const day = date.getDay() || 7
  date.setDate(date.getDate() - day + 1)
  return date
}

function rowsFrom(response) {
  if (response && Array.isArray(response.data)) return response.data
  const payload = response && response.data && typeof response.data === 'object' ? response.data : response
  if (payload && Array.isArray(payload.rows)) return payload.rows
  if (payload && Array.isArray(payload.records)) return payload.records
  if (Array.isArray(payload)) return payload
  return []
}

function rawId(row, keys) {
  for (let index = 0; index < keys.length; index += 1) {
    const value = row && row[keys[index]]
    if (value !== undefined && value !== null && String(value) !== '') return value
  }
  return null
}

function draftKey(shopId, userId, workDate) {
  return `${shopId}|${userId}|${workDate}`
}

function isVersionConflict(error) {
  if (!error || error === 'cancel' || error === 'close' || error === 'stale') return false
  const code = error.businessCode || error.code || error.message || error
  return String(code).indexOf(VERSION_CONFLICT) !== -1
}

// Keep Java Long versions as decimal strings; unsafe JSON numbers have already
// lost information and must be read back instead of being acknowledged.
function versionText(value) {
  if (typeof value === 'number' && (!Number.isSafeInteger(value) || value < 0)) return null
  if (typeof value !== 'number' && typeof value !== 'string') return null
  if (!/^\d+$/.test(String(value))) return null
  const text = String(value).replace(/^0+(?=\d)/, '')
  if (text.length > 19 || (text.length === 19 && text > '9223372036854775807')) return null
  return text
}

function nextVersionText(value) {
  const text = versionText(value)
  if (text == null || text === '9223372036854775807') return null
  const digits = text.split('')
  let carry = 1
  for (let index = digits.length - 1; index >= 0 && carry; index--) {
    const next = Number(digits[index]) + carry
    digits[index] = String(next % 10)
    carry = next > 9 ? 1 : 0
  }
  if (carry) digits.unshift('1')
  return digits.join('')
}

export default {
  name: 'AttendanceWeeklySchedule',
  props: {
    shopContext: { type: Object, default: () => ({}) }
  },
  data() {
    const monday = mondayOf(new Date())
    return {
      loading: false,
      saving: false,
      publishing: false,
      publishLocked: false,
      scheduleDestroyed: false,
      scheduleInactive: false,
      loadEpoch: 0,
      searchEpoch: 0,
      publishEpoch: 0,
      saveEpoch: 0,
      lockedWeekAnchor: '',
      weekAnchor: dateText(monday),
      shifts: [],
      sites: [],
      employees: [],
      schedules: [],
      cells: Object.create(null),
      draftEdits: Object.create(null),
      draftEditSequence: 0,
      conflictReadFailed: null,
      employeeKeyword: '',
      employeeQueryRows: [],
      employeeTotal: 0,
      employeeApiPage: 1,
      employeeLoadingMore: false,
      employeePage: 1,
      employeePageSize: 20,
      employeeSelection: Object.create(null),
      bulkDates: [],
      bulkShiftId: null,
      bulkSiteId: null,
      bulkNotice: '',
      copying: false,
      copyEpoch: 0,
      batchProgress: null,
      persistedDrafts: Object.create(null)
    }
  },
  computed: {
    canEditSchedule() {
      return checkPermi(['oa:attendance:schedule:add']) && checkPermi(['oa:attendance:schedule:edit'])
    },
    canRemoveSchedule() {
      return checkPermi(['oa:attendance:schedule:remove'])
    },
    validStore() {
      return Boolean(this.shopContext && this.shopContext.isStore && this.shopContext.deptId)
    },
    weekNavigationLocked() {
      return this.publishing || this.publishLocked || this.saving
    },
    selectedEmployees() {
      return Object.keys(this.employeeSelection).map(key => this.employeeSelection[key])
    },
    visibleEmployees() {
      const start = (this.employeePage - 1) * this.employeePageSize
      return this.employees.slice(start, start + this.employeePageSize)
    },
    hasMoreEmployees() {
      return this.employeeQueryRows.length < this.employeeTotal
    },
    batchNotice() {
      const progress = this.batchProgress
      if (!progress) return ''
      const action = progress.phase === 'publish' ? '发布' : '保存'
      return `${progress.dateFrom} 至 ${progress.dateTo}：已确认${action} ${progress.completed}/${progress.total} 条${progress.failed ? '；本次已停止，请核对下方排班后重试' : progress.completed < progress.total ? `，正在处理第 ${progress.batch} 批` : ''}${progress.unknown ? '；有请求结果待核对，未确认整周完成' : ''}`
    },
    conflictNotice() {
      if (this.isConflictReadFailedFor(this.currentViewContext())) {
        return '排班版本冲突后未能读取最新数据，本地草稿已保留，请稍后重试核对。'
      }
      const shopId = this.shopContext && this.shopContext.deptId
      const dates = {}
      ;(this.weekDays || []).forEach(day => { dates[day.date] = true })
      const hasReview = Object.keys(this.draftEdits || {}).some(key => {
        const edit = this.draftEdits[key]
        return edit && edit.needsReview && String(edit.shopId) === String(shopId) && dates[edit.workDate]
      })
      return hasReview ? '部分排班与服务器不一致，请核对后再保存。本次未保存成功。' : ''
    },
    weekDays() {
      const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      const monday = mondayOf(this.weekAnchor)
      return labels.map((label, index) => {
        const current = new Date(monday)
        current.setDate(monday.getDate() + index)
        return { date: dateText(current), label: `${label} ${String(current.getMonth() + 1).padStart(2, '0')}/${String(current.getDate()).padStart(2, '0')}` }
      })
    }
  },
  watch: {
    'shopContext.deptId'() {
      this.handleShopContextChange()
    }
  },
  created() {
    this.refresh()
  },
  activated() {
    if (!this.scheduleInactive) return
    this.scheduleInactive = false
    this.refresh()
  },
  deactivated() {
    this.scheduleInactive = true
    this.invalidatePendingWrites()
    this.loadEpoch += 1
    this.searchEpoch += 1
  },
  beforeDestroy() {
    this.scheduleDestroyed = true
    this.invalidatePendingWrites()
    this.loadEpoch += 1
    this.searchEpoch += 1
  },
  methods: {
    shiftId(row) {
      return rawId(row, ['shiftId', 'id'])
    },
    siteId(row) {
      return rawId(row, ['siteId', 'id'])
    },
    employeeId(row) {
      return rawId(row, ['userId', 'employeeId', 'id'])
    },
    employeeName(row) {
      return row.nickName || row.employeeName || row.userName || `员工 ${this.employeeId(row) || '-'}`
    },
    shiftLabel(row) {
      const periods = shiftPeriodsText(row)
      return `${row.shiftName || row.name || '-'}${periods ? ` ${periods}` : ''}`
    },
    siteLabel(row) {
      const name = row.siteName || row.name || '-'
      const address = String(row.address || '').trim()
      return address ? `${name} · ${address}` : name
    },
    enabled(row) {
      return String(row.status || 'ENABLED').toUpperCase() === 'ENABLED'
    },
    copyCells(cells) {
      const next = Object.create(null)
      Object.keys(cells || {}).forEach(key => {
        next[key] = Object.assign({}, cells[key])
      })
      return next
    },
    copyDraftEdits(edits) {
      const next = Object.create(null)
      Object.keys(edits || {}).forEach(key => {
        next[key] = Object.assign({}, edits[key])
      })
      return next
    },
    captureScheduleContext() {
      return {
        shopId: this.shopContext.deptId,
        weekAnchor: this.weekAnchor,
        dateFrom: this.weekDays[0].date,
        dateTo: this.weekDays[6].date,
        weekDays: this.weekDays.slice(),
        employees: (this.employees || []).slice(),
        cells: this.copyCells(this.cells),
        schedules: (this.schedules || []).map(row => Object.assign({}, row)),
        persistedDrafts: this.copyCells(this.persistedDrafts),
        draftEdits: this.copyDraftEdits(this.draftEdits),
        publishEpoch: this.publishEpoch,
        saveEpoch: this.saveEpoch
      }
    },
    isSessionCurrent() {
      return !this.scheduleDestroyed && !this.scheduleInactive
    },
    isLoadCurrent(epoch) {
      return this.isSessionCurrent() && epoch === this.loadEpoch
    },
    isPublishCurrent(operation) {
      return Boolean(operation)
        && this.isSessionCurrent()
        && operation.publishEpoch === this.publishEpoch
    },
    isSaveCurrent(epoch) {
      return this.isSessionCurrent() && epoch === this.saveEpoch
    },
    isSearchCurrent(epoch) {
      return this.isSessionCurrent() && epoch === this.searchEpoch
    },
    currentViewContext() {
      return {
        shopId: this.shopContext && this.shopContext.deptId,
        dateFrom: this.weekDays[0] && this.weekDays[0].date,
        dateTo: this.weekDays[6] && this.weekDays[6].date,
        weekAnchor: this.weekAnchor
      }
    },
    matchesView(context) {
      if (!context) return false
      const current = this.currentViewContext()
      return String(context.shopId) === String(current.shopId)
        && context.dateFrom === current.dateFrom
        && context.dateTo === current.dateTo
    },
    isConflictReadFailedFor(context) {
      const fail = this.conflictReadFailed
      return Boolean(fail)
        && context
        && String(fail.shopId) === String(context.shopId)
        && fail.dateFrom === context.dateFrom
        && fail.dateTo === context.dateTo
    },
    matchingDraftKeys(context) {
      const dates = {}
      ;((context && context.weekDays) || this.weekDays || []).forEach(day => { dates[day.date] = true })
      const shopId = context && context.shopId
      return Object.keys(this.draftEdits || {}).filter(key => {
        const edit = this.draftEdits[key]
        return edit && String(edit.shopId) === String(shopId) && dates[edit.workDate]
      })
    },
    shiftNameById(id) {
      if (id == null || id === '') return '未排'
      const row = (this.shifts || []).find(item => String(this.shiftId(item)) === String(id))
      return row ? (row.shiftName || row.name || `班次${id}`) : `班次${id}`
    },
    siteNameById(id) {
      if (id == null || id === '') return '未选地点'
      const row = (this.sites || []).find(item => String(this.siteId(item)) === String(id))
      return row ? this.siteLabel(row) : `地点${id}`
    },
    selectEmployee(employee, selected) {
      const key = String(this.employeeId(employee))
      if (selected) this.$set(this.employeeSelection, key, Object.assign({}, employee))
      else {
        const next = Object.assign(Object.create(null), this.employeeSelection)
        delete next[key]
        this.employeeSelection = next
      }
    },
    selectVisibleEmployees() {
      this.visibleEmployees.forEach(employee => this.selectEmployee(employee, true))
    },
    clearEmployeeSelection() { this.employeeSelection = Object.create(null) },
    acceptEmployeePage(response, append = false) {
      const rows = rowsFrom(response)
      const payload = response && response.data && !Array.isArray(response.data) ? response.data : response
      this.employeeQueryRows = append ? this.mergeEmployees(this.employeeQueryRows, rows) : rows
      const total = Number(payload && payload.total)
      this.employeeTotal = Number.isFinite(total) && total >= 0 ? total : this.employeeQueryRows.length
      this.employees = this.employeeKeyword ? this.employeeQueryRows.slice()
        : this.mergeEmployees(this.employeeQueryRows, this.employeesFromSchedules(this.schedules), this.employeesFromDrafts())
      if (!append) this.employeePage = 1
    },
    loadMoreEmployees() {
      if (!this.validStore || !this.hasMoreEmployees || this.employeeLoadingMore || this.loading) return Promise.resolve()
      const context = this.currentViewContext()
      const epoch = this.searchEpoch
      const loadEpoch = this.loadEpoch
      const keyword = this.employeeKeyword
      const pageNum = this.employeeApiPage + 1
      const current = () => this.isSearchCurrent(epoch) && this.matchesView(context) && this.loadEpoch === loadEpoch && this.employeeKeyword === keyword
      this.employeeLoadingMore = true
      return listAttendanceEmployeeOptions({ shopId: context.shopId, keyword: keyword || undefined, pageNum, pageSize: EMPLOYEE_FETCH_SIZE }).then(response => {
        if (!current()) return
        this.acceptEmployeePage(response, true)
        this.employeeApiPage = pageNum
      }).catch(error => {
        if (current()) this.$modal.msgError(error.message || '员工加载失败，请重试')
      }).finally(() => { if (current()) this.employeeLoadingMore = false })
    },
    canFillEmptyCell(employee, workDate) {
      const value = this.cell(employee, workDate)
      const key = draftKey(this.shopContext.deptId, this.employeeId(employee), workDate)
      return !value.scheduleId && !value.shiftId && !value.needsReview && !value.overwriteBlocked
        && !this.isPublished(value) && !this.draftEdits[key]
    },
    fillEmptyDraft(employee, workDate, shiftId, siteId) {
      if (!this.canFillEmptyCell(employee, workDate)) return false
      const next = Object.assign(this.emptyCell(), { shiftId, siteId, status: 'DRAFT' })
      this.$set(this.cells, `${this.employeeId(employee)}|${workDate}`, next)
      this.rememberDraft(employee, workDate, next)
      return true
    },
    applyBulkSchedule() {
      if (!this.validStore || !this.canEditSchedule || this.loading || this.weekNavigationLocked || this.copying || !this.isSessionCurrent()) return
      const employees = this.selectedEmployees.slice()
      const dates = this.weekDays.filter(day => this.bulkDates.includes(day.date))
      const shift = this.shifts.find(row => String(this.shiftId(row)) === String(this.bulkShiftId))
      const site = this.sites.find(row => String(this.siteId(row)) === String(this.bulkSiteId))
      if (!employees.length || !dates.length || !shift || !site) {
        this.$modal.msgWarning('请勾选员工和日期，并选择班次、考勤地点')
        return
      }
      let added = 0
      employees.forEach(employee => dates.forEach(day => {
        if (this.fillEmptyDraft(employee, day.date, this.shiftId(shift), this.siteId(site))) added += 1
      }))
      this.employees = this.mergeEmployees(this.employees, employees)
      this.bulkNotice = `目标周 ${this.weekDays[0].date} 至 ${this.weekDays[6].date}：已填写 ${added} 条草稿，跳过 ${employees.length * dates.length - added} 个已有排班或本地草稿格；核对后再保存、发布。`
    },
    copyPreviousWeek() {
      if (!this.validStore || !this.canEditSchedule || this.loading || this.weekNavigationLocked || this.copying || !this.isSessionCurrent()) return Promise.resolve()
      const context = this.captureScheduleContext()
      const epoch = ++this.copyEpoch
      const loadEpoch = this.loadEpoch
      const draftSequence = this.draftEditSequence
      const previousFrom = parseLocalDate(context.dateFrom)
      const previousTo = parseLocalDate(context.dateTo)
      previousFrom.setDate(previousFrom.getDate() - 7)
      previousTo.setDate(previousTo.getDate() - 7)
      const source = { shopId: context.shopId, dateFrom: dateText(previousFrom), dateTo: dateText(previousTo) }
      const current = () => this.isSessionCurrent() && this.copyEpoch === epoch && this.loadEpoch === loadEpoch && this.matchesView(context) && !this.weekNavigationLocked
      this.copying = true
      return Promise.all([this.loadWeekDrafts(source), this.loadWeekDrafts(context)]).then(([previous, target]) => {
        if (!current()) return
        if (draftSequence !== this.draftEditSequence) {
          this.bulkNotice = '复制期间已有新的编辑，本次未复制；请核对当前草稿后再试。'
          return
        }
        this.schedules = target
        this.buildCells()
        this.applyDraftOverlays()
        let added = 0, occupied = 0, unavailable = 0
        const employees = []
        previous.forEach(row => {
          const sourceDate = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
          if (sourceDate < source.dateFrom || sourceDate > source.dateTo || (row.shopId != null && String(row.shopId) !== String(context.shopId))) return
          const date = parseLocalDate(sourceDate)
          date.setDate(date.getDate() + 7)
          const targetDate = dateText(date)
          const employee = { userId: this.employeeId(row), nickName: row.nickName || row.employeeName || row.userName }
          if (employee.userId == null || !context.weekDays.some(day => day.date === targetDate)) return
          if (!this.canFillEmptyCell(employee, targetDate)) { occupied += 1; return }
          const shift = this.shifts.find(item => String(this.shiftId(item)) === String(row.shiftId))
          const site = this.sites.find(item => String(this.siteId(item)) === String(row.siteId))
          if (!shift || !site || !this.enabled(shift) || !this.enabled(site)) { unavailable += 1; return }
          if (this.fillEmptyDraft(employee, targetDate, this.shiftId(shift), this.siteId(site))) {
            added += 1
            employees.push(employee)
          }
        })
        this.employees = this.mergeEmployees(this.employees, employees)
        this.bulkNotice = `已从 ${source.dateFrom} 至 ${source.dateTo} 复制到 ${context.dateFrom} 至 ${context.dateTo}：新增 ${added} 条草稿，跳过 ${occupied} 条已有排班或本地草稿、${unavailable} 条班次或地点不可用排班；请核对后再保存、发布。`
      }).catch(error => {
        if (current()) this.$modal.msgError(error.message || '复制上周失败，本地草稿未被覆盖')
      }).finally(() => { if (this.copyEpoch === epoch) this.copying = false })
    },
    clearScheduleView() {
      this.shifts = []
      this.sites = []
      this.employees = []
      this.schedules = []
      this.cells = Object.create(null)
      this.employeeQueryRows = []
      this.employeeTotal = 0
      this.employeeApiPage = 1
      this.employeePage = 1
      this.employeeLoadingMore = false
    },
    invalidatePendingWrites() {
      this.publishEpoch += 1
      this.saveEpoch += 1
      this.publishing = false
      this.publishLocked = false
      this.saving = false
      this.lockedWeekAnchor = ''
      this.copyEpoch += 1
      this.copying = false
      this.batchProgress = null
    },
    beginPublishOperation() {
      const operation = this.captureScheduleContext()
      this.publishLocked = true
      this.lockedWeekAnchor = operation.weekAnchor
      return operation
    },
    endPublishOperation(operation) {
      if (!this.isPublishCurrent(operation)) return
      this.publishing = false
      this.publishLocked = false
      this.lockedWeekAnchor = ''
    },
    handleShopContextChange() {
      this.invalidatePendingWrites()
      this.clearEmployeeSelection()
      this.bulkNotice = ''
      this.bulkDates = []
      this.bulkShiftId = null
      this.bulkSiteId = null
      this.clearScheduleView()
      this.refresh()
    },
    refresh() {
      const epoch = this.loadEpoch + 1
      this.loadEpoch = epoch
      this.employeeLoadingMore = false
      this.employeeApiPage = 1
      const searchEpochAtStart = this.searchEpoch
      const keywordAtStart = this.employeeKeyword
      if (!this.validStore) {
        this.clearScheduleView()
        this.loading = false
        return Promise.resolve()
      }
      this.loading = true
      const shopId = this.shopContext.deptId
      const scheduleQuery = { shopId, dateFrom: this.weekDays[0].date, dateTo: this.weekDays[6].date }
      return Promise.all([
        listAttendanceShifts({ status: 'ENABLED' }, SILENT_READ),
        listAttendanceSites({ shopId, status: 'ENABLED' }),
        listAttendanceSchedules(scheduleQuery, SILENT_READ),
        listAttendanceEmployeeOptions({ shopId, keyword: this.employeeKeyword || undefined, pageNum: 1, pageSize: EMPLOYEE_FETCH_SIZE })
      ]).then(([shiftResponse, siteResponse, scheduleResponse, userResponse]) => {
        if (!this.isLoadCurrent(epoch)) return
        this.shifts = rowsFrom(shiftResponse).filter(this.enabled)
        this.sites = rowsFrom(siteResponse).filter(this.enabled)
        if (!this.sites.some(site => String(this.siteId(site)) === String(this.bulkSiteId))) this.bulkSiteId = this.sites.length === 1 ? this.siteId(this.sites[0]) : null
        this.schedules = rowsFrom(scheduleResponse)
        this.buildCells()
        this.applyDraftOverlays()
        if (this.searchEpoch !== searchEpochAtStart || this.employeeKeyword !== keywordAtStart) return
        if (String(this.shopContext.deptId) !== String(shopId)) return
        this.acceptEmployeePage(userResponse)
      }).catch(error => {
        if (!this.isLoadCurrent(epoch)) return
        this.$modal.msgError(error.message || '周排班加载失败')
      }).finally(() => {
        if (this.isLoadCurrent(epoch)) this.loading = false
      })
    },
    employeesFromSchedules(schedules) {
      const map = Object.create(null)
      ;(schedules || []).forEach(row => {
        const userId = this.employeeId(row)
        if (userId === null || map[String(userId)]) return
        map[String(userId)] = { userId, nickName: row.nickName || row.employeeName || row.userName }
      })
      return Object.keys(map).map(key => map[key])
    },
    employeesFromDrafts() {
      const shopId = this.shopContext && this.shopContext.deptId
      const dates = {}
      ;(this.weekDays || []).forEach(day => { dates[day.date] = true })
      const map = Object.create(null)
      Object.keys(this.draftEdits || {}).forEach(key => {
        const edit = this.draftEdits[key]
        if (!edit || String(edit.shopId) !== String(shopId) || !dates[edit.workDate] || map[String(edit.userId)]) return
        map[String(edit.userId)] = edit.employee || { userId: edit.userId, nickName: edit.nickName }
      })
      return Object.keys(map).map(key => map[key])
    },
    mergeEmployees() {
      const seen = new Set()
      const rows = []
      for (let index = 0; index < arguments.length; index += 1) {
        rows.push.apply(rows, arguments[index] || [])
      }
      return rows.filter(row => {
        const userId = this.employeeId(row)
        const key = String(userId == null ? '' : userId)
        if (!key || seen.has(key)) return false
        seen.add(key)
        return true
      })
    },
    searchEmployees() {
      if (!this.validStore) return Promise.resolve()
      const epoch = this.searchEpoch + 1
      this.searchEpoch = epoch
      this.employeeLoadingMore = false
      this.employeeApiPage = 1
      const shopId = this.shopContext.deptId
      const keyword = this.employeeKeyword
      return listAttendanceEmployeeOptions({ shopId, keyword: this.employeeKeyword || undefined, pageNum: 1, pageSize: EMPLOYEE_FETCH_SIZE }).then(userResponse => {
        if (!this.isSearchCurrent(epoch)) return
        if (String(this.shopContext.deptId) !== String(shopId) || this.employeeKeyword !== keyword) return
        this.acceptEmployeePage(userResponse)
      }).catch(error => {
        if (!this.isSearchCurrent(epoch)) return
        if (String(this.shopContext.deptId) !== String(shopId)) return
        this.$modal.msgError(error.message || '员工搜索失败')
      })
    },
    emptyCell() {
      return {
        scheduleId: null,
        shiftId: null,
        siteId: null,
        status: 'EMPTY',
        rowVersion: null,
        needsReview: false,
        overwriteBlocked: false,
        remoteUnavailable: false
      }
    },
    cellFromScheduleRow(row) {
      if (!row) return this.emptyCell()
      return {
        scheduleId: rawId(row, ['scheduleId', 'id']),
        shiftId: rawId(row, ['shiftId']),
        siteId: rawId(row, ['siteId']),
        status: String(row.status || 'DRAFT').toUpperCase(),
        rowVersion: row.rowVersion,
        shiftNameSnapshot: row.shiftNameSnapshot,
        startTimeSnapshot: row.startTimeSnapshot,
        endTimeSnapshot: row.endTimeSnapshot,
        punchModeSnapshot: row.punchModeSnapshot || row.punchMode,
        siteNameSnapshot: row.siteNameSnapshot,
        addressSnapshot: row.addressSnapshot,
        segmentSnapshots: row.segmentSnapshots || row.segments || row.shiftSegments || [],
        needsReview: false,
        overwriteBlocked: false,
        remoteUnavailable: false
      }
    },
    serverCellFromSchedules(userId, workDate) {
      const date = String(workDate || '').slice(0, 10)
      const row = (this.schedules || []).find(item => {
        return String(this.employeeId(item)) === String(userId)
          && String(item.businessDate || item.workDate || item.scheduleDate || '').slice(0, 10) === date
      })
      return this.cellFromScheduleRow(row)
    },
    buildCells() {
      const next = Object.create(null)
      ;(this.schedules || []).forEach(row => {
        const userId = this.employeeId(row)
        const workDate = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
        if (userId === null || !workDate) return
        next[`${userId}|${workDate}`] = this.cellFromScheduleRow(row)
      })
      this.cells = next
    },
    applyDraftOverlays() {
      const shopId = this.shopContext && this.shopContext.deptId
      const dates = {}
      ;(this.weekDays || []).forEach(day => { dates[day.date] = true })
      Object.keys(this.draftEdits || {}).forEach(key => {
        const edit = this.draftEdits[key]
        if (!edit || String(edit.shopId) !== String(shopId) || !dates[edit.workDate]) return
        const cellKey = `${edit.userId}|${edit.workDate}`
        const remote = this.serverCellFromSchedules(edit.userId, edit.workDate)
        this.syncDraftAgainstServer(edit, remote)
        this.$set(this.draftEdits, key, Object.assign({}, edit))
        const next = Object.assign({}, remote)
        next.localShiftId = edit.shiftId
        next.localSiteId = edit.siteId
        next.needsReview = Boolean(edit.needsReview)
        next.overwriteBlocked = Boolean(edit.overwriteBlocked)
        next.remoteUnavailable = Boolean(edit.remoteUnavailable)
        next.remoteShiftId = edit.remoteShiftId != null ? edit.remoteShiftId : remote.shiftId
        next.remoteSiteId = edit.remoteSiteId != null ? edit.remoteSiteId : remote.siteId
        next.remoteRowVersion = edit.remoteRowVersion != null ? edit.remoteRowVersion : remote.rowVersion
        next.remoteStatus = edit.remoteStatus || remote.status
        if (edit.overwriteBlocked && edit.remoteStatus === 'PUBLISHED') {
          next.status = 'PUBLISHED'
          next.shiftId = remote.shiftId
          next.siteId = remote.siteId
          next.rowVersion = remote.rowVersion
        } else if (edit.overwriteBlocked && edit.remoteStatus === 'DELETED') {
          next.status = 'DELETED'
          next.shiftId = edit.shiftId
          next.siteId = edit.siteId
          next.rowVersion = edit.rowVersion
        } else {
          next.shiftId = edit.shiftId
          next.siteId = edit.siteId
          next.rowVersion = edit.rowVersion
        }
        this.$set(this.cells, cellKey, next)
      })
    },
    syncDraftAgainstServer(edit, serverCell) {
      if (!edit || edit.remoteUnavailable) return
      if (edit.overwriteBlocked && (edit.remoteStatus === 'PUBLISHED' || edit.remoteStatus === 'DELETED')) return
      const serverExists = Boolean(serverCell && (serverCell.scheduleId || serverCell.shiftId) && serverCell.status !== 'EMPTY' && serverCell.status !== 'DELETED')
      if (!serverExists) {
        if (edit.scheduleId) {
          edit.needsReview = true
          edit.overwriteBlocked = true
          edit.remoteStatus = 'DELETED'
          edit.remoteUnavailable = false
        }
        return
      }
      if (this.isPublished(serverCell)) {
        edit.needsReview = true
        edit.overwriteBlocked = true
        edit.remoteStatus = 'PUBLISHED'
        edit.remoteShiftId = serverCell.shiftId
        edit.remoteSiteId = serverCell.siteId
        edit.remoteRowVersion = serverCell.rowVersion
        edit.remoteUnavailable = false
        return
      }
      if (String(serverCell.rowVersion) !== String(edit.rowVersion)) {
        edit.needsReview = true
        edit.overwriteBlocked = false
        edit.remoteStatus = String(serverCell.status || 'DRAFT').toUpperCase()
        edit.remoteShiftId = serverCell.shiftId
        edit.remoteSiteId = serverCell.siteId
        edit.remoteRowVersion = serverCell.rowVersion
        edit.remoteUnavailable = false
      }
    },
    rememberDraft(employee, workDate, cell) {
      const shopId = this.shopContext.deptId
      const userId = this.employeeId(employee)
      if (shopId == null || userId == null || !workDate) return
      const key = draftKey(shopId, userId, workDate)
      const previous = this.draftEdits[key]
      const keepReview = Boolean(previous && (previous.needsReview || previous.overwriteBlocked))
      this.$set(this.draftEdits, key, {
        shopId,
        userId,
        workDate,
        nickName: this.employeeName(employee),
        employee: { userId, nickName: this.employeeName(employee), userName: employee && employee.userName },
        scheduleId: (previous && previous.scheduleId) || cell.scheduleId || null,
        shiftId: cell.shiftId || null,
        siteId: cell.siteId || null,
        rowVersion: previous && previous.rowVersion != null ? previous.rowVersion : cell.rowVersion,
        status: cell.status,
        stamp: ++this.draftEditSequence,
        needsReview: keepReview ? previous.needsReview : false,
        overwriteBlocked: keepReview ? previous.overwriteBlocked : false,
        remoteShiftId: keepReview ? previous.remoteShiftId : undefined,
        remoteSiteId: keepReview ? previous.remoteSiteId : undefined,
        remoteRowVersion: keepReview ? previous.remoteRowVersion : undefined,
        remoteStatus: keepReview ? previous.remoteStatus : undefined,
        remoteUnavailable: keepReview ? previous.remoteUnavailable : false
      })
    },
    forgetDraft(shopId, userId, workDate) {
      const key = draftKey(shopId, userId, workDate)
      if (!this.draftEdits[key]) return
      this.draftEditSequence += 1
      const next = this.copyDraftEdits(this.draftEdits)
      delete next[key]
      this.draftEdits = next
    },
    cellFrom(cells, employee, workDate) {
      const key = `${this.employeeId(employee)}|${workDate}`
      return (cells && cells[key]) || this.emptyCell()
    },
    cell(employee, workDate) {
      return this.cellFrom(this.cells, employee, workDate)
    },
    setCell(employee, workDate, field, value) {
      const userId = this.employeeId(employee)
      const key = `${userId}|${workDate}`
      const existing = this.cell(employee, workDate)
      if (existing.overwriteBlocked || this.isPublished(existing)) return
      if (field === 'shiftId' && !value && existing.scheduleId) {
        if (!this.canRemoveSchedule) return
        const deletion = {
          shopId: this.shopContext.deptId,
          weekAnchor: this.weekAnchor,
          dateFrom: this.weekDays[0].date,
          dateTo: this.weekDays[6].date,
          userId,
          workDate,
          scheduleId: existing.scheduleId,
          rowVersion: existing.rowVersion,
          draftStamp: (this.draftEdits[draftKey(this.shopContext.deptId, userId, workDate)] || {}).stamp,
          loadEpoch: this.loadEpoch
        }
        this.$modal.confirm('确认删除该草稿排班？已发布排班不允许删除。').then(() => {
          if (!this.isSessionCurrent() || !this.matchesView(deletion) || deletion.loadEpoch !== this.loadEpoch) {
            return Promise.reject('stale')
          }
          return deleteAttendanceSchedule(deletion.scheduleId, deletion.rowVersion)
        }).then(() => {
          const draftId = draftKey(deletion.shopId, deletion.userId, deletion.workDate)
          const live = this.draftEdits[draftId]
          if (!live || live.stamp === deletion.draftStamp) {
            this.forgetDraft(deletion.shopId, deletion.userId, deletion.workDate)
          } else if (String(live.scheduleId) === String(deletion.scheduleId)) {
            this.$set(this.draftEdits, draftId, Object.assign({}, live, {
              needsReview: true,
              overwriteBlocked: true,
              remoteUnavailable: false,
              remoteStatus: 'DELETED',
              stamp: ++this.draftEditSequence
            }))
            this.applyDraftOverlays()
          }
          if (!this.isSessionCurrent() || !this.matchesView(deletion) || deletion.loadEpoch !== this.loadEpoch) return
          this.$modal.msgSuccess('草稿排班已删除')
          return this.refresh()
        }).catch(error => {
          if (error === 'cancel' || error === 'close' || error === 'stale') return
          if (!this.isSessionCurrent() || !this.matchesView(deletion)) return
          this.$modal.msgError(error.message || '草稿排班删除失败')
        })
        return
      }
      const next = Object.assign({}, existing, { [field]: value || null })
      if (field === 'shiftId' && !value) next.siteId = null
      if (field === 'shiftId' && value && !next.siteId && this.sites.length === 1) next.siteId = this.siteId(this.sites[0])
      this.$set(this.cells, key, next)
      if (!next.shiftId && !next.scheduleId) this.forgetDraft(this.shopContext.deptId, userId, workDate)
      else this.rememberDraft(employee, workDate, next)
    },
    isPublished(cell) {
      return String(cell && cell.status || '').toUpperCase() === 'PUBLISHED'
    },
    scheduleStatusClass(cell) {
      return this.isPublished(cell) ? 'is-published' : (cell && cell.shiftId ? 'is-draft' : '')
    },
    selectedShift(value) {
      const live = this.shifts.find(row => String(this.shiftId(row)) === String(value && value.shiftId))
      if (this.isPublished(value) && (value.segmentSnapshots || []).length) return value
      return live || value || {}
    },
    cellShiftPeriods(value) {
      return shiftPeriodsText(this.selectedShift(value)) || '工作时段待加载'
    },
    cellPunchModeText(value) {
      const shift = this.selectedShift(value)
      const mode = shift.punchModeSnapshot || shift.punchMode
      return mode === SEGMENT_PUNCH_MODE ? '每工作段打卡' : '班次首尾打卡'
    },
    cellSiteLabel(value) {
      if (value && value.siteNameSnapshot) return value.addressSnapshot
        ? `${value.siteNameSnapshot} · ${value.addressSnapshot}`
        : value.siteNameSnapshot
      const live = this.sites.find(row => String(this.siteId(row)) === String(value && value.siteId))
      return live ? this.siteLabel(live) : `地点 #${value && value.siteId || '-'}`
    },
    draftItems(context) {
      const snapshot = context || this.captureScheduleContext()
      const items = []
      const seen = {}
      const pushItem = (employee, day, value) => {
        const userId = this.employeeId(employee) || (value && value.userId)
        const itemKey = `${userId}|${day.date}`
        if (seen[itemKey] || !value || !value.shiftId || this.isPublished(value) || value.overwriteBlocked) return
        const fullKey = draftKey(snapshot.shopId, userId, day.date)
        const persisted = snapshot.persistedDrafts && snapshot.persistedDrafts[fullKey]
        if (!(snapshot.draftEdits && snapshot.draftEdits[fullKey]) && persisted
          && ['scheduleId', 'rowVersion', 'shiftId', 'siteId'].every(field => String(persisted[field]) === String(value[field]))) return
        if (!value.siteId) throw new Error(`${this.employeeName(employee)} ${day.label} 未选择考勤地点`)
        seen[itemKey] = true
        items.push({
          scheduleId: value.scheduleId || undefined,
          userId,
          businessDate: day.date,
          shiftId: value.shiftId,
          siteId: value.siteId,
          rowVersion: value.rowVersion
        })
      }
      Object.keys(snapshot.draftEdits || {}).forEach(key => {
        const edit = snapshot.draftEdits[key]
        if (!edit || String(edit.shopId) !== String(snapshot.shopId)) return
        const day = snapshot.weekDays.find(item => item.date === edit.workDate)
        if (!day || edit.needsReview || edit.overwriteBlocked) return
        pushItem(edit.employee || { userId: edit.userId, nickName: edit.nickName }, day, {
          scheduleId: edit.scheduleId,
          shiftId: edit.shiftId,
          siteId: edit.siteId,
          rowVersion: edit.rowVersion,
          status: edit.status,
          userId: edit.userId
        })
      })
      snapshot.employees.forEach(employee => {
        snapshot.weekDays.forEach(day => {
          pushItem(employee, day, this.cellFrom(snapshot.cells, employee, day.date))
        })
      })
      return items
    },
    hasUnresolvedConflicts(context) {
      const snapshot = context || this.captureScheduleContext()
      return Object.keys(snapshot.draftEdits || {}).some(key => {
        const edit = snapshot.draftEdits[key]
        return edit && String(edit.shopId) === String(snapshot.shopId)
          && snapshot.weekDays.some(day => day.date === edit.workDate)
          && (edit.needsReview || edit.overwriteBlocked)
      })
    },
    setBatchProgress(context, phase, total, completed, batch, failed = false, unknown = false) {
      if (!this.isSessionCurrent() || !this.matchesView(context)) return
      this.batchProgress = { phase, total, completed, batch, failed, unknown, dateFrom: context.dateFrom, dateTo: context.dateTo }
    },
    mergeSavedSchedules(context, rows) {
      if (!this.isSessionCurrent() || !this.matchesView(context)) return
      const byKey = Object.create(null)
      this.schedules.concat(rows || []).forEach(row => {
        const key = `${this.employeeId(row)}|${String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)}`
        byKey[key] = row
      })
      this.schedules = Object.keys(byKey).map(key => byKey[key])
      this.buildCells()
      this.applyDraftOverlays()
    },
    validateSavedBatch(context, items, rows) {
      const values = Object.create(null)
      let valid = rows.length === items.length
      rows.forEach(row => {
        const date = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
        const key = `${this.employeeId(row)}|${date}`
        if (values[key]) valid = false
        values[key] = row
      })
      items.forEach(item => {
        const row = values[`${item.userId}|${item.businessDate}`]
        const expectedVersion = item.scheduleId != null || item.rowVersion != null ? nextVersionText(item.rowVersion) : '0'
        if (!row || !rawId(row, ['scheduleId', 'id']) || expectedVersion == null || versionText(row.rowVersion) !== expectedVersion
          || String(row.shopId) !== String(context.shopId)
          || (item.scheduleId != null && String(rawId(row, ['scheduleId', 'id'])) !== String(item.scheduleId))
          || String(row.status || '').toUpperCase() !== 'DRAFT'
          || String(row.shiftId) !== String(item.shiftId) || String(row.siteId) !== String(item.siteId)) valid = false
      })
      if (!valid) throw new Error('排班保存返回与提交内容不一致，需读取权威数据核对')
    },
    validatePublishedBatch(context, items, rows) {
      const values = Object.create(null)
      let valid = rows.length === items.length
      rows.forEach(row => {
        const id = rawId(row, ['scheduleId', 'id'])
        if (id == null || values[String(id)]) valid = false
        values[String(id)] = row
      })
      items.forEach(item => {
        const row = values[String(rawId(item, ['scheduleId', 'id']))]
        const expectedVersion = nextVersionText(item.rowVersion)
        if (!row || expectedVersion == null || versionText(row.rowVersion) !== expectedVersion
          || String(row.shopId) !== String(context.shopId)
          || String(this.employeeId(row)) !== String(this.employeeId(item))
          || String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10) !== String(item.businessDate || item.workDate || item.scheduleDate || '').slice(0, 10)
          || String(row.shiftId) !== String(item.shiftId) || String(row.siteId) !== String(item.siteId)
          || String(row.status || '').toUpperCase() !== 'PUBLISHED') valid = false
      })
      if (!valid) throw new Error('排班发布返回与提交内容不一致，需读取权威数据核对')
    },
    async persistDrafts(context, stillCurrent) {
      const snapshot = context || this.captureScheduleContext()
      const current = typeof stillCurrent === 'function' ? stillCurrent : () => this.isSessionCurrent() && this.matchesView(snapshot)
      if (this.hasUnresolvedConflicts(snapshot)) {
        return Promise.reject(new Error('请先核对冲突排班'))
      }
      let items
      try {
        items = this.draftItems(snapshot)
      } catch (error) {
        return Promise.reject(error)
      }
      if (!items.length) return false
      let completed = 0
      for (let start = 0; start < items.length; start += SCHEDULE_BATCH_SIZE) {
        if (!current()) throw 'stale'
        const batch = items.slice(start, start + SCHEDULE_BATCH_SIZE)
        const batchNumber = Math.floor(start / SCHEDULE_BATCH_SIZE) + 1
        this.setBatchProgress(snapshot, 'save', items.length, completed, batchNumber)
        try {
          const response = await saveAttendanceScheduleBatch({ shopId: snapshot.shopId, items: batch })
          const rows = rowsFrom(response)
          this.validateSavedBatch(snapshot, batch, rows)
          this.confirmPersistedDrafts(snapshot, batch, rows)
          rows.forEach(row => {
            const date = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
            const key = draftKey(snapshot.shopId, this.employeeId(row), date)
            this.$set(this.persistedDrafts, key, this.cellFromScheduleRow(row))
          })
          completed += batch.length
          if (!current()) throw 'stale'
          this.mergeSavedSchedules(snapshot, rows)
          this.setBatchProgress(snapshot, 'save', items.length, completed, batchNumber)
        } catch (error) {
          if (error === 'stale' || !current()) throw 'stale'
          this.setBatchProgress(snapshot, 'save', items.length, completed, batchNumber, true, true)
          const failed = new Error(`第 ${batchNumber} 批保存未完成，已确认保存 ${completed}/${items.length} 条；已保留剩余草稿，请核对后重试。`)
          failed.recoveryContext = Object.assign({}, snapshot, { submittedItems: batch, recoveryMessage: failed.message })
          failed.originalError = error
          throw failed
        }
      }
      return true
    },
    confirmPersistedDrafts(snapshot, items, savedRows) {
      const next = this.copyDraftEdits(this.draftEdits)
      const savedMap = Object.create(null)
      ;(savedRows || []).forEach(row => {
        const userId = this.employeeId(row)
        const workDate = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
        if (userId == null || !workDate) return
        savedMap[`${userId}|${workDate}`] = row
      })
      ;(items || []).forEach(item => {
        const key = draftKey(snapshot.shopId, item.userId, item.businessDate)
        const submitted = snapshot.draftEdits && snapshot.draftEdits[key]
        const live = next[key]
        if (!live) return
        if (String(live.shopId) !== String(snapshot.shopId)) return
        if (!(snapshot.weekDays || []).some(day => day.date === live.workDate)) return
        if (submitted && live.stamp === submitted.stamp) {
          delete next[key]
          return
        }
        const saved = savedMap[`${item.userId}|${item.businessDate}`]
        if (!saved) return
        next[key] = Object.assign({}, live, {
          scheduleId: rawId(saved, ['scheduleId', 'id']) || live.scheduleId,
          rowVersion: saved.rowVersion,
          status: String(saved.status || live.status || 'DRAFT').toUpperCase(),
          remoteUnavailable: false
        })
      })
      this.draftEdits = next
    },
    conflictReviewText(cell) {
      if (!cell) return ''
      const localShift = cell.localShiftId != null ? cell.localShiftId : cell.shiftId
      const localSite = cell.localSiteId != null ? cell.localSiteId : cell.siteId
      const localText = `本地：${this.shiftNameById(localShift)} · ${this.siteNameById(localSite)}`
      if (cell.overwriteBlocked && cell.remoteStatus === 'PUBLISHED') {
        return `${localText}；远端：${this.shiftNameById(cell.remoteShiftId)} · ${this.siteNameById(cell.remoteSiteId)}（已发布，不能再按草稿覆盖）`
      }
      if (cell.overwriteBlocked && cell.remoteStatus === 'DELETED') {
        return `${localText}；远端：已删除，不能再按草稿覆盖`
      }
      if (cell.remoteUnavailable) return `${localText}；远端：未能读取最新排班，草稿已保留`
      return `${localText}；远端：${this.shiftNameById(cell.remoteShiftId)} · ${this.siteNameById(cell.remoteSiteId)}（${cell.remoteStatus === 'PUBLISHED' ? '已发布' : '草稿'}，请核对后再保存）`
    },
    resolveConflictKeepLocal(employee, workDate) {
      const shopId = this.shopContext.deptId
      const userId = this.employeeId(employee)
      const key = draftKey(shopId, userId, workDate)
      const edit = this.draftEdits[key]
      if (!edit || edit.overwriteBlocked || edit.remoteRowVersion == null) return
      this.$set(this.draftEdits, key, Object.assign({}, edit, {
        rowVersion: edit.remoteRowVersion,
        needsReview: false,
        overwriteBlocked: false,
        remoteUnavailable: false,
        stamp: ++this.draftEditSequence
      }))
      this.applyDraftOverlays()
    },
    resolveConflictUseRemote(employee, workDate) {
      const shopId = this.shopContext.deptId
      const userId = this.employeeId(employee)
      const remote = this.serverCellFromSchedules(userId, workDate)
      if (remote.scheduleId && remote.status === 'DRAFT') this.$set(this.persistedDrafts, draftKey(shopId, userId, workDate), Object.assign({}, remote))
      this.forgetDraft(shopId, userId, workDate)
      this.buildCells()
      this.applyDraftOverlays()
      const cellKey = `${userId}|${workDate}`
      const rebuilt = this.cells[cellKey] || this.emptyCell()
      if (rebuilt.needsReview !== false) {
        this.$set(this.cells, cellKey, Object.assign({}, rebuilt, {
          needsReview: false,
          overwriteBlocked: false,
          remoteUnavailable: false
        }))
      }
    },
    applyConflictBaseline(context, rows) {
      const sameView = String(this.shopContext && this.shopContext.deptId) === String(context.shopId)
        && this.weekDays[0] && this.weekDays[0].date === context.dateFrom
      if (sameView) this.schedules = rows || []
      const remoteMap = Object.create(null)
      ;(rows || []).forEach(row => {
        const userId = this.employeeId(row)
        const workDate = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
        if (userId == null || !workDate) return
        remoteMap[`${userId}|${workDate}`] = row
      })
      const submitted = this.draftItemsFromSnapshot(context)
      submitted.forEach(item => {
        const key = draftKey(context.shopId, item.userId, item.businessDate)
        const live = this.draftEdits[key] || (context.draftEdits && context.draftEdits[key])
        const remote = remoteMap[`${item.userId}|${item.businessDate}`]
        const changed = !remote
          || String(remote.status || 'DRAFT').toUpperCase() === 'PUBLISHED'
          || String(remote.rowVersion) !== String(item.rowVersion)
        if (!changed && !live) return
        const next = Object.assign({
          shopId: context.shopId,
          userId: item.userId,
          workDate: item.businessDate,
          employee: { userId: item.userId },
          scheduleId: item.scheduleId,
          shiftId: item.shiftId,
          siteId: item.siteId,
          rowVersion: item.rowVersion,
          stamp: ++this.draftEditSequence
        }, live || {})
        if (!changed) {
          next.needsReview = false
          next.remoteUnavailable = false
          this.$set(this.draftEdits, key, next)
          return
        }
        next.needsReview = true
        next.remoteUnavailable = false
        next.shiftId = (live && live.shiftId) || item.shiftId
        next.siteId = (live && live.siteId) || item.siteId
        next.rowVersion = (live && live.rowVersion) || item.rowVersion
        if (!remote) {
          if (!(item.scheduleId || (live && live.scheduleId))) {
            next.needsReview = false
            next.overwriteBlocked = false
            this.$set(this.draftEdits, key, next)
            return
          }
          next.overwriteBlocked = true
          next.remoteStatus = 'DELETED'
        } else if (String(remote.status || 'DRAFT').toUpperCase() === 'PUBLISHED') {
          next.overwriteBlocked = true
          next.remoteStatus = 'PUBLISHED'
          next.remoteShiftId = rawId(remote, ['shiftId'])
          next.remoteSiteId = rawId(remote, ['siteId'])
          next.remoteRowVersion = remote.rowVersion
        } else {
          next.overwriteBlocked = false
          next.remoteStatus = String(remote.status || 'DRAFT').toUpperCase()
          next.remoteShiftId = rawId(remote, ['shiftId'])
          next.remoteSiteId = rawId(remote, ['siteId'])
          next.remoteRowVersion = remote.rowVersion
        }
        this.$set(this.draftEdits, key, next)
      })
      if (sameView) this.buildCells()
      this.applyDraftOverlays()
    },
    draftItemsFromSnapshot(context) {
      if (Array.isArray(context.submittedItems)) return context.submittedItems
      try {
        return this.draftItems(context)
      } catch (error) {
        return []
      }
    },
    recoverVersionConflict(context, stillCurrent) {
      const current = typeof stillCurrent === 'function' ? stillCurrent : () => true
      return this.loadWeekDrafts(context).then(rows => {
        if (!current()) return
        if (this.isConflictReadFailedFor(context)) this.conflictReadFailed = null
        this.matchingDraftKeys(context).forEach(key => {
          const edit = this.draftEdits[key]
          if (edit && edit.remoteUnavailable) this.$set(this.draftEdits, key, Object.assign({}, edit, { remoteUnavailable: false }))
        })
        this.applyConflictBaseline(context, rows)
        if (this.batchProgress && this.matchesView(context)) this.batchProgress.unknown = false
        this.$modal.msgError(context.recoveryMessage || '排班版本已变化，请核对后再保存')
      }).catch(error => {
        if (!current()) return
        this.conflictReadFailed = { shopId: context.shopId, dateFrom: context.dateFrom, dateTo: context.dateTo }
        this.matchingDraftKeys(context).forEach(key => {
          const edit = this.draftEdits[key] || (context.draftEdits && context.draftEdits[key])
          if (!edit) return
          this.$set(this.draftEdits, key, Object.assign({}, edit, { needsReview: true, remoteUnavailable: true }))
        })
        this.applyDraftOverlays()
        this.$modal.msgError((error && error.message) || '冲突重读失败，已保留本地草稿')
      })
    },
    draftScheduleIds(schedules) {
      return (schedules || [])
        .filter(row => String(row.status || 'DRAFT').toUpperCase() === 'DRAFT')
        .map(row => rawId(row, ['scheduleId', 'id']))
        .filter(Boolean)
    },
    loadWeekDrafts(context) {
      return listAttendanceSchedules({
        shopId: context.shopId,
        dateFrom: context.dateFrom,
        dateTo: context.dateTo
      }, SILENT_READ).then(response => rowsFrom(response))
    },
    publishFrozenWeek(operation) {
      return this.persistDrafts(operation, () => this.isPublishCurrent(operation)).then(() => {
        if (!this.isPublishCurrent(operation)) return Promise.reject('stale')
        if (this.matchingDraftKeys(operation).length) throw new Error('保存期间排班有新修改，请核对并保存后再发布')
        return this.loadWeekDrafts(operation)
      }).then(async schedules => {
        if (!this.isPublishCurrent(operation)) return Promise.reject('stale')
        const scheduleIds = this.draftScheduleIds(schedules).filter((id, index, ids) => ids.findIndex(value => String(value) === String(id)) === index)
        if (!scheduleIds.length) {
          const previous = this.batchProgress
          const confirmed = new Set(schedules.filter(row => this.isPublished(row)).map(row => String(rawId(row, ['scheduleId', 'id']))))
          if (previous && previous.phase === 'publish' && previous.failed && previous.requestedIds && previous.requestedIds.length
            && previous.requestedIds.every(id => confirmed.has(String(id)))) {
            this.setBatchProgress(operation, 'publish', previous.requestedIds.length, previous.requestedIds.length, previous.batch)
            this.schedules = schedules
            this.buildCells()
            this.applyDraftOverlays()
            return
          }
          throw new Error('本周没有可发布的排班')
        }
        let completed = 0
        for (let start = 0; start < scheduleIds.length; start += SCHEDULE_BATCH_SIZE) {
          if (!this.isPublishCurrent(operation)) throw 'stale'
          if (this.matchingDraftKeys(operation).length) {
            this.setBatchProgress(operation, 'publish', scheduleIds.length, completed, Math.floor(start / SCHEDULE_BATCH_SIZE) + 1, true)
            throw new Error(`发布前检测到新的排班修改，已确认发布 ${completed}/${scheduleIds.length} 条；其余已停止，请核对新草稿后再操作。`)
          }
          const batch = scheduleIds.slice(start, start + SCHEDULE_BATCH_SIZE)
          const batchNumber = Math.floor(start / SCHEDULE_BATCH_SIZE) + 1
          const frozenRows = schedules.filter(row => batch.some(id => String(id) === String(rawId(row, ['scheduleId', 'id']))))
          const scheduleVersions = Object.create(null)
          frozenRows.forEach(row => { scheduleVersions[String(rawId(row, ['scheduleId', 'id']))] = row.rowVersion })
          if (batch.some(id => nextVersionText(scheduleVersions[String(id)]) == null)) throw new Error('排班版本不完整，请刷新核对后再发布')
          this.setBatchProgress(operation, 'publish', scheduleIds.length, completed, batchNumber)
          try {
            const response = await publishAttendanceSchedules({ shopId: operation.shopId, scheduleIds: batch, scheduleVersions })
            if (!this.isPublishCurrent(operation)) throw 'stale'
            const rows = rowsFrom(response)
            this.validatePublishedBatch(operation, frozenRows, rows)
            completed += batch.length
            this.mergeSavedSchedules(operation, rows)
            this.setBatchProgress(operation, 'publish', scheduleIds.length, completed, batchNumber)
          } catch (error) {
            if (error === 'stale' || !this.isPublishCurrent(operation)) throw 'stale'
            this.setBatchProgress(operation, 'publish', scheduleIds.length, completed, batchNumber, true, true)
            this.batchProgress.requestedIds = scheduleIds.slice()
            try {
              const latest = await this.loadWeekDrafts(operation)
              if (!this.isPublishCurrent(operation)) throw 'stale'
              const requested = new Set(scheduleIds.map(String))
              completed = latest.filter(row => requested.has(String(rawId(row, ['scheduleId', 'id']))) && this.isPublished(row)).length
              this.schedules = latest
              this.buildCells()
              this.applyDraftOverlays()
              if (isVersionConflict(error)) {
                const submittedItems = frozenRows.map(row => ({ scheduleId: rawId(row, ['scheduleId', 'id']), userId: this.employeeId(row), businessDate: String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10), shiftId: row.shiftId, siteId: row.siteId, rowVersion: row.rowVersion }))
                this.applyConflictBaseline(Object.assign({}, operation, { submittedItems, draftEdits: Object.create(null) }), latest)
              }
              this.setBatchProgress(operation, 'publish', scheduleIds.length, completed, batchNumber, true, false)
              this.batchProgress.requestedIds = scheduleIds.slice()
            } catch (readError) {
              if (readError === 'stale' || !this.isPublishCurrent(operation)) throw 'stale'
            }
            throw new Error(`第 ${batchNumber} 批发布未完成，已确认发布 ${completed}/${scheduleIds.length} 条；本次已停止，请核对状态后重试，已发布排班不会再次提交。`)
          }
        }
      }).catch(error => {
        if (error && error.recoveryContext) {
          return this.recoverVersionConflict(error.recoveryContext, () => this.isPublishCurrent(operation)).then(() => Promise.reject('stale'))
        }
        if (isVersionConflict(error)) {
          return this.recoverVersionConflict(operation, () => this.isPublishCurrent(operation)).then(() => Promise.reject('stale'))
        }
        return Promise.reject(error)
      })
    },
    handleSave() {
      if (!this.validStore || this.saving || this.publishing || this.publishLocked || this.scheduleInactive) return
      const epoch = this.saveEpoch + 1
      this.saveEpoch = epoch
      const context = this.captureScheduleContext()
      this.saving = true
      if (!this.lockedWeekAnchor) this.lockedWeekAnchor = context.weekAnchor
      if (this.isConflictReadFailedFor(context)) {
        return this.recoverVersionConflict(context, () => this.isSaveCurrent(epoch)).finally(() => {
          if (!this.isSaveCurrent(epoch)) return
          this.saving = false
          if (!this.publishLocked) this.lockedWeekAnchor = ''
        })
      }
      return this.persistDrafts(context, () => this.isSaveCurrent(epoch)).then(changed => {
        if (!this.isSaveCurrent(epoch)) return
        if (!changed) {
          this.$modal.msgWarning('请至少为一名员工选择班次')
          return
        }
        this.$modal.msgSuccess('本周排班草稿已保存')
        return this.refresh()
      }).catch(error => {
        if (!this.isSaveCurrent(epoch)) return
        if (error === 'stale') return
        if (error && error.recoveryContext) return this.recoverVersionConflict(error.recoveryContext, () => this.isSaveCurrent(epoch))
        if (isVersionConflict(error)) return this.recoverVersionConflict(context, () => this.isSaveCurrent(epoch))
        this.$modal.msgError(error.message || '排班保存失败')
      }).finally(() => {
        if (!this.isSaveCurrent(epoch)) return
        this.saving = false
        if (!this.publishLocked) this.lockedWeekAnchor = ''
      })
    },
    handlePublish() {
      if (!this.validStore || this.publishing || this.publishLocked || this.saving || this.scheduleInactive) return
      const operation = this.beginPublishOperation()
      return this.$modal.confirm(`确认保存并发布 ${operation.dateFrom} 至 ${operation.dateTo} 的排班？发布后员工才能打卡。大周表将分批处理，失败时保留已完成结果并停止。`, '发布排班').then(() => {
        if (!this.isPublishCurrent(operation)) return Promise.reject('stale')
        this.publishing = true
        return this.publishFrozenWeek(operation)
      }).then(() => {
        if (!this.isPublishCurrent(operation)) return
        this.$modal.msgSuccess('本周排班已发布')
        return this.refresh()
      }).catch(error => {
        if (error === 'cancel' || error === 'close' || error === 'stale') return
        if (!this.isPublishCurrent(operation)) return
        this.$modal.msgError(error.message || '排班发布失败')
      }).finally(() => {
        this.endPublishOperation(operation)
      })
    },
    restoreLockedWeek() {
      if (this.lockedWeekAnchor) this.weekAnchor = this.lockedWeekAnchor
    },
    resetWeekTools() {
      this.copyEpoch += 1
      this.copying = false
      this.bulkDates = []
      this.bulkNotice = ''
      this.batchProgress = null
    },
    handleWeekChange(value) {
      if (this.weekNavigationLocked) {
        this.restoreLockedWeek()
        return
      }
      this.weekAnchor = dateText(mondayOf(value))
      this.resetWeekTools()
      this.schedules = []
      this.cells = Object.create(null)
      this.refresh()
    },
    moveWeek(days) {
      if (this.weekNavigationLocked) {
        this.restoreLockedWeek()
        return
      }
      const date = mondayOf(this.weekAnchor)
      date.setDate(date.getDate() + days)
      this.weekAnchor = dateText(date)
      this.resetWeekTools()
      this.schedules = []
      this.cells = Object.create(null)
      this.refresh()
    }
  }
}
</script>

<style scoped>
.schedule-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.week-switcher, .schedule-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.schedule-actions .el-input { width: 180px; }
.bulk-schedule-tools { margin-top: 16px; display: grid; gap: 12px; }
.bulk-selection-summary, .bulk-fill-actions, .employee-page-summary { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.bulk-selection-summary span, .bulk-schedule-tools p, .employee-page-summary { color: #606266; font-size: 13px; }
.bulk-schedule-tools p { margin: 0; line-height: 1.7; }
.bulk-fill-actions .el-select { width: 230px; max-width: 100%; }
.employee-page-summary { margin-bottom: 12px; }
.employee-pagination { margin-top: 14px; overflow-x: auto; }
.context-alert, .table-card { margin-top: 16px; }
.site-alert { margin-bottom: 16px; }
.schedule-card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.schedule-card-header h2 { margin: 0 0 6px; font-size: 18px; }
.schedule-card-header p { margin: 0; color: #6b7280; }
.schedule-legend { white-space: nowrap; color: #6b7280; font-size: 12px; }
.schedule-legend span { display: inline-block; width: 9px; height: 9px; margin: 0 4px 0 10px; border-radius: 50%; }
.schedule-legend .draft { background: #e6a23c; }
.schedule-legend .published { background: #67c23a; }
.schedule-cell { min-height: 104px; padding: 6px; border-radius: 8px; }
.schedule-cell.is-draft { background: #fff8eb; }
.schedule-cell.is-published { background: #f0f9eb; }
.schedule-cell .el-select { display: block; width: 100%; margin-bottom: 5px; }
.schedule-cell small { color: #8492a6; }
.selected-shift-detail { display: flex; flex-direction: column; gap: 3px; margin: 6px 0; color: #425466; font-size: 12px; line-height: 1.45; word-break: break-word; }
.selected-shift-detail small { display: block; }
.selected-site-detail { margin: 5px 0; color: #409eff; font-size: 12px; line-height: 1.4; word-break: break-word; }
.site-required { display: block; margin: -1px 0 5px; color: #f56c6c !important; }
.conflict-review { margin-top: 4px; }
.conflict-review small { display: block; color: #e6a23c !important; }
.cell-subtitle { margin-top: 4px; color: #8492a6; font-size: 12px; }
@media (max-width: 1200px) { .schedule-toolbar { align-items: flex-start; flex-direction: column; } }
</style>
