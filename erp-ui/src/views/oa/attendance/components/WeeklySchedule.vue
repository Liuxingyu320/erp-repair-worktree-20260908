<template>
  <section class="attendance-module">
    <el-card shadow="never" class="search-card oa-filter-card">
      <div class="schedule-toolbar">
        <div class="week-switcher">
          <el-button size="small" icon="el-icon-arrow-left" @click="moveWeek(-7)">上一周</el-button>
          <el-date-picker v-model="weekAnchor" type="week" format="yyyy 第 WW 周" value-format="yyyy-MM-dd" size="small" :clearable="false" @change="handleWeekChange" />
          <el-button size="small" @click="moveWeek(7)">下一周<i class="el-icon-arrow-right el-icon--right" /></el-button>
          <strong>{{ weekDays[0].date }} 至 {{ weekDays[6].date }}</strong>
        </div>
        <div class="schedule-actions">
          <el-input
            v-model.trim="employeeKeyword"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            placeholder="员工姓名/工号"
            @keyup.enter.native="refresh"
            @clear="refresh"
          />
          <el-button size="small" icon="el-icon-refresh" @click="refresh">刷新</el-button>
          <el-button v-hasPermi="['oa:attendance:schedule:add', 'oa:attendance:schedule:edit']" type="primary" plain size="small" :loading="saving" @click="handleSave">保存草稿</el-button>
          <el-button v-hasPermi="['oa:attendance:schedule:publish']" type="success" size="small" :loading="publishing" @click="handlePublish">发布本周排班</el-button>
        </div>
      </div>
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
        v-if="!sites.length"
        title="当前门店没有已启用的考勤地点"
        description="请先在“考勤地点”页签新增、核对并启用地点，再创建排班。"
        type="warning"
        show-icon
        :closable="false"
        class="site-alert"
      />
      <el-table v-loading="loading" :data="employees" size="small" border empty-text="当前门店没有可排班员工" class="weekly-table">
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
                :disabled="isPublished(cell(scope.row, day.date)) || !canEditSchedule || (!sites.length && !cell(scope.row, day.date).shiftId)"
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
                :disabled="!canEditSchedule"
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
            </div>
          </template>
        </el-table-column>
      </el-table>
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
      weekAnchor: dateText(monday),
      shifts: [],
      sites: [],
      employees: [],
      schedules: [],
      cells: Object.create(null),
      employeeKeyword: ''
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
      this.refresh()
    }
  },
  created() {
    this.refresh()
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
    refresh() {
      if (!this.validStore) {
        this.shifts = []
        this.sites = []
        this.employees = []
        this.schedules = []
        this.cells = Object.create(null)
        return Promise.resolve()
      }
      this.loading = true
      const shopId = this.shopContext.deptId
      const scheduleQuery = { shopId, dateFrom: this.weekDays[0].date, dateTo: this.weekDays[6].date }
      return Promise.all([
        listAttendanceShifts({ status: 'ENABLED' }),
        listAttendanceSites({ shopId, status: 'ENABLED' }),
        listAttendanceSchedules(scheduleQuery),
        listAttendanceEmployeeOptions({ shopId, keyword: this.employeeKeyword || undefined })
      ]).then(([shiftResponse, siteResponse, scheduleResponse, userResponse]) => {
        this.shifts = rowsFrom(shiftResponse).filter(this.enabled)
        this.sites = rowsFrom(siteResponse).filter(this.enabled)
        this.schedules = rowsFrom(scheduleResponse)
        const users = rowsFrom(userResponse)
        this.employees = this.employeeKeyword
          ? users
          : this.mergeEmployees(users, this.employeesFromSchedules(this.schedules))
        this.buildCells()
      }).catch(error => {
        this.$modal.msgError(error.message || '周排班加载失败')
      }).finally(() => { this.loading = false })
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
    mergeEmployees(primary, additional) {
      const seen = new Set()
      return ([]).concat(primary || [], additional || []).filter(row => {
        const userId = this.employeeId(row)
        const key = String(userId == null ? '' : userId)
        if (!key || seen.has(key)) return false
        seen.add(key)
        return true
      })
    },
    buildCells() {
      const next = Object.create(null)
      ;(this.schedules || []).forEach(row => {
        const userId = this.employeeId(row)
        const workDate = String(row.businessDate || row.workDate || row.scheduleDate || '').slice(0, 10)
        if (userId === null || !workDate) return
        next[`${userId}|${workDate}`] = {
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
          segmentSnapshots: row.segmentSnapshots || row.segments || row.shiftSegments || []
        }
      })
      this.cells = next
    },
    cell(employee, workDate) {
      const key = `${this.employeeId(employee)}|${workDate}`
      return this.cells[key] || { scheduleId: null, shiftId: null, siteId: null, status: 'EMPTY', rowVersion: null }
    },
    setCell(employee, workDate, field, value) {
      const userId = this.employeeId(employee)
      const key = `${userId}|${workDate}`
      const existing = this.cell(employee, workDate)
      if (field === 'shiftId' && !value && existing.scheduleId) {
        if (!this.canRemoveSchedule) return
        this.$modal.confirm('确认删除该草稿排班？已发布排班不允许删除。').then(() => {
          return deleteAttendanceSchedule(existing.scheduleId, existing.rowVersion)
        }).then(() => {
          this.$modal.msgSuccess('草稿排班已删除')
          return this.refresh()
        }).catch(error => {
          if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '草稿排班删除失败')
        })
        return
      }
      const next = Object.assign({}, existing, { [field]: value || null })
      if (field === 'shiftId' && !value) next.siteId = null
      if (field === 'shiftId' && value && !next.siteId && this.sites.length === 1) next.siteId = this.siteId(this.sites[0])
      this.$set(this.cells, key, next)
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
    draftItems() {
      const items = []
      this.employees.forEach(employee => {
        const userId = this.employeeId(employee)
        this.weekDays.forEach(day => {
          const value = this.cell(employee, day.date)
          if (!value.shiftId || this.isPublished(value)) return
          if (!value.siteId) throw new Error(`${this.employeeName(employee)} ${day.label} 未选择考勤地点`)
          items.push({
            scheduleId: value.scheduleId || undefined,
            userId,
            businessDate: day.date,
            shiftId: value.shiftId,
            siteId: value.siteId,
            rowVersion: value.rowVersion
          })
        })
      })
      return items
    },
    persistDrafts() {
      let items
      try {
        items = this.draftItems()
      } catch (error) {
        return Promise.reject(error)
      }
      if (!items.length) return Promise.resolve(false)
      return saveAttendanceScheduleBatch({ shopId: this.shopContext.deptId, items }).then(() => true)
    },
    handleSave() {
      if (!this.validStore || this.saving) return
      this.saving = true
      this.persistDrafts().then(changed => {
        if (!changed) {
          this.$modal.msgWarning('请至少为一名员工选择班次')
          return
        }
        this.$modal.msgSuccess('本周排班草稿已保存')
        return this.refresh()
      }).catch(error => {
        this.$modal.msgError(error.message || '排班保存失败')
      }).finally(() => { this.saving = false })
    },
    handlePublish() {
      if (!this.validStore || this.publishing) return
      this.$modal.confirm('发布后员工才能按本周排班打卡。确认保存并发布当前周？', '发布排班').then(() => {
        this.publishing = true
        return this.persistDrafts().then(() => this.refresh()).then(() => {
          const scheduleIds = this.schedules
            .filter(row => String(row.status || 'DRAFT').toUpperCase() === 'DRAFT')
            .map(row => rawId(row, ['scheduleId', 'id']))
            .filter(Boolean)
          if (!scheduleIds.length) throw new Error('本周没有可发布的排班')
          return publishAttendanceSchedules({ shopId: this.shopContext.deptId, scheduleIds })
        })
      }).then(() => {
        this.$modal.msgSuccess('本周排班已发布')
        return this.refresh()
      }).catch(error => {
        if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '排班发布失败')
      }).finally(() => { this.publishing = false })
    },
    handleWeekChange(value) {
      this.weekAnchor = dateText(mondayOf(value))
      this.refresh()
    },
    moveWeek(days) {
      const date = mondayOf(this.weekAnchor)
      date.setDate(date.getDate() + days)
      this.weekAnchor = dateText(date)
      this.refresh()
    }
  }
}
</script>

<style scoped>
.schedule-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.week-switcher, .schedule-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.schedule-actions .el-input { width: 180px; }
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
.cell-subtitle { margin-top: 4px; color: #8492a6; font-size: 12px; }
@media (max-width: 1200px) { .schedule-toolbar { align-items: flex-start; flex-direction: column; } }
</style>
