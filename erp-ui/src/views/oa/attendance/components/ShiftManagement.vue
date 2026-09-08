<template>
  <section class="attendance-module">
    <el-card shadow="never" class="search-card oa-filter-card">
      <div class="attendance-toolbar">
        <div>
          <h2>班次管理</h2>
          <p>班次会在发布排班时生成快照，后续修改不会重写历史。</p>
        </div>
        <div>
          <el-select v-model="status" clearable size="small" placeholder="全部状态" @change="load">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button size="small" icon="el-icon-refresh" @click="load">刷新</el-button>
          <el-button v-hasPermi="['oa:attendance:shift:add']" type="primary" size="small" icon="el-icon-plus" @click="openCreate">新建班次</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card oa-table-card">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无班次">
        <el-table-column label="班次" min-width="170">
          <template slot-scope="scope">
            <strong>{{ scope.row.shiftName || scope.row.name || '-' }}</strong>
            <div class="cell-subtitle">{{ scope.row.shiftCode || scope.row.code || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="工作时段" min-width="230">
          <template slot-scope="scope">
            <div class="work-periods">{{ workPeriodsText(scope.row) }}</div>
            <div class="cell-subtitle">
              {{ punchModeText(scope.row.punchMode) }}
              <el-tag v-if="isCrossDay(scope.row)" size="mini" type="warning">跨天</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标准工时" width="105">
          <template slot-scope="scope">{{ minutesText(scope.row.standardMinutes) }}</template>
        </el-table-column>
        <el-table-column label="打卡窗口" min-width="210">
          <template slot-scope="scope">
            <div>上班 {{ minutesOffset(scope.row.checkInOpenMinutes, -60) }} 至 {{ minutesOffset(scope.row.checkInCloseMinutes, 30) }}</div>
            <div class="cell-subtitle">下班 {{ minutesOffset(scope.row.checkOutOpenMinutes, -30) }} 至 {{ minutesOffset(scope.row.checkOutCloseMinutes, 120) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template slot-scope="scope">
            <el-tag :type="isEnabled(scope.row) ? 'success' : 'info'" size="mini">
              {{ isEnabled(scope.row) ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['oa:attendance:shift:edit']" type="text" size="mini" @click="openEdit(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['oa:attendance:shift:edit']" type="text" size="mini" @click="toggleStatus(scope.row)">
              {{ isEnabled(scope.row) ? '停用' : '启用' }}
            </el-button>
            <el-button v-hasPermi="['oa:attendance:shift:remove']" type="text" size="mini" :disabled="isEnabled(scope.row)" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="form.shiftId ? '编辑班次' : '新建班次'" :visible.sync="dialogOpen" width="780px" append-to-body @closed="resetForm">
      <el-alert title="已发布排班使用班次快照，这里的修改只影响之后的排班。" type="info" :closable="false" show-icon />
      <el-form ref="shiftForm" :model="form" :rules="rules" label-width="120px" size="small" class="attendance-dialog-form">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="班次名称" prop="shiftName"><el-input v-model.trim="form.shiftName" maxlength="60" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="班次编码" prop="shiftCode"><el-input v-model.trim="form.shiftCode" maxlength="32" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="打卡模式">
              <el-radio-group v-model="form.punchMode">
                <el-radio :label="continuousPunchMode">首尾打卡（连续班）</el-radio>
                <el-radio :label="segmentPunchMode">每工作段打卡（分段班）</el-radio>
              </el-radio-group>
              <div class="form-help">
                {{ form.punchMode === segmentPunchMode
                  ? '每个工作段均需上班、下班打卡；中间时间自动记为休息。相邻段的下班与上班窗口按休息中点夹紧，请以下方实际可打时间为准。'
                  : '只在整个班次开始和结束时打卡。增加工作段只划分工时和休息，不会自动改成分段打卡。' }}
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="工作段" required>
              <div class="segment-editor">
                <div v-for="(segment, index) in form.workSegments" :key="segment.clientKey" class="segment-row">
                  <span class="segment-index">第 {{ index + 1 }} 段</span>
                  <el-time-picker v-model="segment.startTime" value-format="HH:mm:ss" format="HH:mm" :clearable="false" placeholder="上班" @change="syncDerivedShift" />
                  <span>至</span>
                  <el-time-picker v-model="segment.endTime" value-format="HH:mm:ss" format="HH:mm" :clearable="false" placeholder="下班" @change="syncDerivedShift" />
                  <el-button-group>
                    <el-button icon="el-icon-top" size="mini" :disabled="index === 0" title="上移" :aria-label="`上移第 ${index + 1} 个工作段`" @click="moveWorkSegment(index, -1)" />
                    <el-button icon="el-icon-bottom" size="mini" :disabled="index === form.workSegments.length - 1" title="下移" :aria-label="`下移第 ${index + 1} 个工作段`" @click="moveWorkSegment(index, 1)" />
                    <el-button icon="el-icon-delete" size="mini" :disabled="form.workSegments.length === 1" title="删除" :aria-label="`删除第 ${index + 1} 个工作段`" @click="removeWorkSegment(index)" />
                  </el-button-group>
                </div>
                <div class="segment-summary">
                  <el-button type="text" icon="el-icon-plus" :disabled="form.workSegments.length >= 8" @click="addWorkSegment">添加工作段</el-button>
                  <span>标准工时自动计算：<strong>{{ minutesText(form.standardMinutes) }}</strong></span>
                  <span v-if="breakPeriodsText" class="break-periods">休息：{{ breakPeriodsText }}</span>
                  <el-tag v-if="form.crossDay" size="mini" type="warning">跨天</el-tag>
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="迟到宽限(分)"><el-input-number v-model="form.graceInMinutes" :min="0" :max="240" controls-position="right" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="早退宽限(分)"><el-input-number v-model="form.graceOutMinutes" :min="0" :max="240" controls-position="right" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="上班提前开放(分)"><el-input-number v-model="form.checkInOpenMinutes" :min="0" :max="720" controls-position="right" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="上班截止(分)"><el-input-number v-model="form.checkInCloseMinutes" :min="0" :max="720" controls-position="right" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="下班提前开放(分)"><el-input-number v-model="form.checkOutOpenMinutes" :min="0" :max="720" controls-position="right" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="下班截止(分)"><el-input-number v-model="form.checkOutCloseMinutes" :min="0" :max="720" controls-position="right" /></el-form-item></el-col>
          <el-col v-if="form.punchMode === segmentPunchMode" :span="24">
            <el-form-item label="有效打卡窗口">
              <div class="derived-window-panel">
                <p class="form-help">分段班只夹相邻工作段的相对面：上一段下班关闭与下一段上班开放对齐到休息中点；中点时刻仍先打下班卡。第一段上班、最后一段下班和同一段两端不夹紧。</p>
                <p v-if="derivedPunchWindowPreview.error" class="window-error">{{ derivedPunchWindowPreview.error }}</p>
                <ul v-else class="derived-window-list">
                  <li v-for="slot in derivedPunchWindowPreview.slots" :key="`${slot.segmentIndex}-${slot.punchType}`">
                    <strong>{{ slot.segmentLabel }}{{ slot.punchTypeText }}</strong>
                    计划 {{ slot.punchTimeText }}，实际可打 {{ slot.rangeText }}
                    <span v-if="slot.clamped" class="window-clamped">（已按休息中点夹紧，配置为 {{ slot.configuredRangeText }}）</span>
                    <span v-if="!slot.valid" class="window-error">{{ slot.issue }}</span>
                  </li>
                </ul>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import {
  changeAttendanceShiftStatus,
  createAttendanceShift,
  deleteAttendanceShift,
  listAttendanceShifts,
  updateAttendanceShift
} from '@/api/oa/attendanceV2'

const CONTINUOUS_PUNCH_MODE = 'SHIFT_BOUNDARY'
const SEGMENT_PUNCH_MODE = 'PER_WORK_SEGMENT'
let segmentKey = 0

function normalizedTime(value, fallback) {
  const text = String(value || fallback || '').slice(0, 8)
  if (/^\d{2}:\d{2}:\d{2}$/.test(text)) return text
  if (/^\d{2}:\d{2}$/.test(text)) return `${text}:00`
  return ''
}

function minuteOfDay(value) {
  const time = normalizedTime(value)
  if (!time) return null
  const [hour, minute] = time.split(':').map(Number)
  if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null
  return hour * 60 + minute
}

function offsetTime(offset) {
  const value = Number(offset)
  if (!Number.isFinite(value)) return ''
  const minute = ((Math.trunc(value) % 1440) + 1440) % 1440
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}:00`
}

function offsetText(offset) {
  const value = Number(offset)
  if (!Number.isFinite(value)) return '--:--'
  const day = Math.floor(value / 1440)
  const time = offsetTime(value).slice(0, 5)
  if (day > 0) return `+${day}天 ${time}`
  if (day < 0) return `${day}天 ${time}`
  return time
}

function offsetTextFromSeconds(offsetSeconds) {
  const value = Number(offsetSeconds)
  if (!Number.isFinite(value)) return '--:--'
  const total = Math.trunc(value)
  const day = Math.floor(total / 86400)
  const secondsOfDay = ((total % 86400) + 86400) % 86400
  const hour = Math.floor(secondsOfDay / 3600)
  const minute = Math.floor((secondsOfDay % 3600) / 60)
  const second = secondsOfDay % 60
  const clock = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
  const time = second === 0 ? clock : `${clock}:${String(second).padStart(2, '0')}`
  if (day > 0) return `+${day}天 ${time}`
  if (day < 0) return `${day}天 ${time}`
  return time
}

function nonNegativeMinutes(value, fallback) {
  const minutes = Number(value)
  if (!Number.isFinite(minutes)) return fallback
  return Math.max(0, Math.trunc(minutes))
}

function workSegmentLabel(index, count) {
  if (count === 1) return '工作段'
  if (count === 2) return index === 0 ? '上午工作段' : '下午工作段'
  return `第${index + 1}工作段`
}

function timePlus(value, minutes) {
  const base = minuteOfDay(value)
  return offsetTime((base === null ? 0 : base) + minutes)
}

function createWorkSegment(startTime, endTime) {
  segmentKey += 1
  return {
    clientKey: `work-segment-${segmentKey}`,
    startTime: normalizedTime(startTime, '08:00:00'),
    endTime: normalizedTime(endTime, '18:00:00')
  }
}

function workSegmentsFromRow(row) {
  const source = Array.isArray(row && row.segments) ? row.segments : []
  const work = source
    .filter(segment => String(segment && segment.segmentType || 'WORK').toUpperCase() === 'WORK')
    .slice()
    .sort((left, right) => Number(left.segmentOrder || 0) - Number(right.segmentOrder || 0))
    .map(segment => createWorkSegment(offsetTime(segment.startMinuteOffset), offsetTime(segment.endMinuteOffset)))
  return work.length
    ? work
    : [createWorkSegment(row && row.startTime, row && row.endTime)]
}

function buildShiftDefinition(workSegments) {
  if (!Array.isArray(workSegments) || !workSegments.length) throw new Error('请至少保留一个工作段')
  if (workSegments.length > 8) throw new Error('一个班次最多可设置 8 个工作段')
  const work = []
  let previousEnd = null
  let standardMinutes = 0
  workSegments.forEach((item, index) => {
    let start = minuteOfDay(item && item.startTime)
    let end = minuteOfDay(item && item.endTime)
    if (start === null || end === null) throw new Error(`请完整填写第 ${index + 1} 个工作段的上下班时间`)
    while (previousEnd !== null && start < previousEnd) start += 1440
    while (end <= start) end += 1440
    if (end > 2880) throw new Error('工作段顺序跨越两个自然日，请调整排序或时间')
    standardMinutes += end - start
    work.push({ start, end })
    previousEnd = end
  })
  if (standardMinutes > 1440) throw new Error('班次标准工时不能超过 1440 分钟')
  const segments = []
  work.forEach((item, index) => {
    if (index > 0 && work[index - 1].end < item.start) {
      segments.push({
        segmentType: 'BREAK',
        segmentOrder: segments.length + 1,
        startMinuteOffset: work[index - 1].end,
        endMinuteOffset: item.start,
        paid: false
      })
    }
    segments.push({
      segmentType: 'WORK',
      segmentOrder: segments.length + 1,
      startMinuteOffset: item.start,
      endMinuteOffset: item.end,
      paid: true
    })
  })
  return {
    startTime: offsetTime(work[0].start),
    endTime: offsetTime(work[work.length - 1].end),
    crossDay: work[work.length - 1].end >= 1440,
    standardMinutes,
    segments
  }
}

function derivedSegmentPunchWindows(workSegments, offsets) {
  const definition = buildShiftDefinition(workSegments)
  const work = definition.segments
    .filter(segment => segment.segmentType === 'WORK')
    .slice()
    .sort((left, right) => Number(left.segmentOrder) - Number(right.segmentOrder))
  const inOpen = nonNegativeMinutes(offsets && offsets.checkInOpenMinutes, 120)
  const inClose = nonNegativeMinutes(offsets && offsets.checkInCloseMinutes, 120)
  const outOpen = nonNegativeMinutes(offsets && offsets.checkOutOpenMinutes, 120)
  const outClose = nonNegativeMinutes(offsets && offsets.checkOutCloseMinutes, 120)
  const slots = work.map((segment, index) => {
    const label = workSegmentLabel(index, work.length)
    const inOpens = (segment.startMinuteOffset - inOpen) * 60
    const inCloses = (segment.startMinuteOffset + inClose) * 60
    const outOpens = (segment.endMinuteOffset - outOpen) * 60
    const outCloses = (segment.endMinuteOffset + outClose) * 60
    return [
      {
        segmentIndex: index,
        segmentLabel: label,
        punchType: 'IN',
        punchTypeText: '上班',
        workStart: segment.startMinuteOffset,
        workEnd: segment.endMinuteOffset,
        punchInstant: segment.startMinuteOffset * 60,
        opensAt: inOpens,
        closesAt: inCloses,
        configuredOpensAt: inOpens,
        configuredClosesAt: inCloses,
        clamped: false
      },
      {
        segmentIndex: index,
        segmentLabel: label,
        punchType: 'OUT',
        punchTypeText: '下班',
        workStart: segment.startMinuteOffset,
        workEnd: segment.endMinuteOffset,
        punchInstant: segment.endMinuteOffset * 60,
        opensAt: outOpens,
        closesAt: outCloses,
        configuredOpensAt: outOpens,
        configuredClosesAt: outCloses,
        clamped: false
      }
    ]
  }).reduce((all, pair) => all.concat(pair), [])
  for (let index = 0; index < work.length - 1; index += 1) {
    const leftEnd = work[index].endMinuteOffset
    const rightStart = work[index + 1].startMinuteOffset
    const midpoint = (leftEnd + rightStart) * 30
    const previousOut = slots[index * 2 + 1]
    const nextIn = slots[(index + 1) * 2]
    if (previousOut.closesAt > midpoint) {
      previousOut.closesAt = midpoint
      previousOut.clamped = true
    }
    if (nextIn.opensAt < midpoint) {
      nextIn.opensAt = midpoint
      nextIn.clamped = true
    }
  }
  const issues = []
  slots.forEach(slot => {
    slot.punchTimeText = offsetTextFromSeconds(slot.punchInstant)
    slot.rangeText = `${offsetTextFromSeconds(slot.opensAt)}–${offsetTextFromSeconds(slot.closesAt)}`
    slot.configuredRangeText = `${offsetTextFromSeconds(slot.configuredOpensAt)}–${offsetTextFromSeconds(slot.configuredClosesAt)}`
    if (slot.closesAt < slot.opensAt) {
      slot.valid = false
      slot.issue = `${slot.segmentLabel}${slot.punchTypeText}有效窗口在中点夹紧后为空，请拉长休息间隔或调整该侧打卡窗口`
      issues.push(slot.issue)
      return
    }
    if (slot.closesAt - slot.opensAt < 60) {
      slot.valid = false
      slot.issue = `${slot.segmentLabel}${slot.punchTypeText}有效窗口不足 60 秒，请拉长休息间隔或调整该侧打卡窗口`
      issues.push(slot.issue)
      return
    }
    if (slot.punchInstant < slot.opensAt || slot.punchInstant > slot.closesAt) {
      slot.valid = false
      slot.issue = `${slot.segmentLabel}${slot.punchTypeText}的计划时间已落在有效窗口之外`
      issues.push(slot.issue)
      return
    }
    slot.valid = true
    slot.issue = ''
  })
  return { slots, issues }
}

const emptyForm = () => ({
  shiftId: null,
  shiftCode: '',
  shiftName: '',
  startTime: '08:00:00',
  endTime: '20:00:00',
  crossDay: false,
  standardMinutes: 720,
  punchMode: CONTINUOUS_PUNCH_MODE,
  workSegments: [createWorkSegment('08:00:00', '20:00:00')],
  graceInMinutes: 0,
  graceOutMinutes: 0,
  checkInOpenMinutes: 120,
  checkInCloseMinutes: 120,
  checkOutOpenMinutes: 120,
  checkOutCloseMinutes: 120,
  status: 'ENABLED',
  rowVersion: null
})

function responseRows(response) {
  if (response && Array.isArray(response.data)) return response.data
  const payload = response && response.data && typeof response.data === 'object' ? response.data : response
  if (payload && Array.isArray(payload.rows)) return payload.rows
  if (Array.isArray(payload)) return payload
  return []
}

export default {
  name: 'AttendanceShiftManagement',
  data() {
    return {
      loading: false,
      saving: false,
      status: '',
      rows: [],
      dialogOpen: false,
      form: emptyForm(),
      rules: {
        shiftName: [{ required: true, message: '请输入班次名称', trigger: 'blur' }],
        shiftCode: [{ required: true, message: '请输入班次编码', trigger: 'blur' }]
      }
    }
  },
  computed: {
    continuousPunchMode() {
      return CONTINUOUS_PUNCH_MODE
    },
    segmentPunchMode() {
      return SEGMENT_PUNCH_MODE
    },
    breakPeriodsText() {
      try {
        return buildShiftDefinition(this.form.workSegments).segments
          .filter(segment => segment.segmentType === 'BREAK')
          .map(segment => `${offsetText(segment.startMinuteOffset)}–${offsetText(segment.endMinuteOffset)}`)
          .join(' / ')
      } catch (error) {
        return ''
      }
    },
    derivedPunchWindowPreview() {
      if (this.form.punchMode !== SEGMENT_PUNCH_MODE) {
        return { visible: false, slots: [], issues: [], error: '' }
      }
      try {
        const derived = derivedSegmentPunchWindows(this.form.workSegments, this.form)
        return {
          visible: true,
          slots: derived.slots,
          issues: derived.issues,
          error: ''
        }
      } catch (error) {
        return {
          visible: true,
          slots: [],
          issues: [],
          error: error.message || '工作段设置不正确，无法计算有效打卡窗口'
        }
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      return listAttendanceShifts(this.status ? { status: this.status } : {}).then(response => {
        this.rows = responseRows(response)
      }).catch(error => {
        this.rows = []
        this.$modal.msgError(error.message || '班次加载失败')
      }).finally(() => { this.loading = false })
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogOpen = true
    },
    openEdit(row) {
      const workSegments = workSegmentsFromRow(row)
      this.form = Object.assign(emptyForm(), row, {
        shiftId: row.shiftId || row.id,
        crossDay: row.crossDay === true || row.crossDay === 1 || row.crossDay === '1',
        punchMode: [CONTINUOUS_PUNCH_MODE, SEGMENT_PUNCH_MODE].includes(row.punchMode)
          ? row.punchMode
          : CONTINUOUS_PUNCH_MODE,
        workSegments
      })
      this.syncDerivedShift()
      this.dialogOpen = true
    },
    resetForm() {
      this.form = emptyForm()
      this.$refs.shiftForm && this.$refs.shiftForm.clearValidate()
    },
    submit() {
      this.$refs.shiftForm.validate(valid => {
        if (!valid || this.saving) return
        let payload
        try {
          payload = this.shiftPayload()
        } catch (error) {
          this.$modal.msgError(error.message || '工作段设置不正确')
          return
        }
        this.saving = true
        const request = payload.shiftId
          ? updateAttendanceShift(payload.shiftId, payload)
          : createAttendanceShift(payload)
        request.then(() => {
          this.$modal.msgSuccess('班次已保存')
          this.dialogOpen = false
          return this.load()
        }).catch(error => {
          this.$modal.msgError(error.message || '班次保存失败')
        }).finally(() => { this.saving = false })
      })
    },
    toggleStatus(row) {
      const shiftId = row.shiftId || row.id
      const nextStatus = this.isEnabled(row) ? 'DISABLED' : 'ENABLED'
      const verb = nextStatus === 'ENABLED' ? '启用' : '停用'
      this.$modal.confirm(`确认${verb}班次“${row.shiftName || row.name || shiftId}”？`).then(() => {
        return changeAttendanceShiftStatus(shiftId, { status: nextStatus, rowVersion: row.rowVersion })
      }).then(() => {
        this.$modal.msgSuccess(`班次已${verb}`)
        return this.load()
      }).catch(error => {
        if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || `${verb}失败`)
      })
    },
    remove(row) {
      const shiftId = row.shiftId || row.id
      if (this.isEnabled(row)) return
      this.$modal.confirm(`确认删除未启用且未被引用的班次“${row.shiftName || row.name || shiftId}”？`).then(() => {
        return deleteAttendanceShift(shiftId, row.rowVersion)
      }).then(() => {
        this.$modal.msgSuccess('班次已删除')
        return this.load()
      }).catch(error => {
        if (error && error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '班次删除失败')
      })
    },
    isEnabled(row) {
      return String(row.status || 'ENABLED').toUpperCase() === 'ENABLED'
    },
    isCrossDay(row) {
      if (row.crossDay === true || row.crossDay === 1 || row.crossDay === '1') return true
      return (row.segments || []).some(segment => Number(segment.endMinuteOffset) >= 1440)
    },
    timeText(value) {
      return String(value || '--:--').slice(0, 5)
    },
    minutesText(value) {
      const minutes = Number(value)
      if (!Number.isFinite(minutes)) return '-'
      return `${minutes} 分钟`
    },
    minutesOffset(value, fallback) {
      const minutes = Number(value === undefined || value === null ? fallback : value)
      if (minutes === 0) return '准点'
      return fallback < 0 ? `提前 ${Math.abs(minutes)} 分` : `延后 ${minutes} 分`
    },
    punchModeText(value) {
      return value === SEGMENT_PUNCH_MODE ? '每工作段打卡' : '班次首尾打卡'
    },
    workPeriodsText(row) {
      const work = (row && Array.isArray(row.segments) ? row.segments : [])
        .filter(segment => String(segment && segment.segmentType || 'WORK').toUpperCase() === 'WORK')
        .slice()
        .sort((left, right) => Number(left.segmentOrder || 0) - Number(right.segmentOrder || 0))
      if (!work.length) return `${this.timeText(row && row.startTime)}–${this.timeText(row && row.endTime)}`
      return work.map(segment => `${offsetText(segment.startMinuteOffset)}–${offsetText(segment.endMinuteOffset)}`).join(' / ')
    },
    syncDerivedShift() {
      try {
        const definition = buildShiftDefinition(this.form.workSegments)
        this.form.startTime = definition.startTime
        this.form.endTime = definition.endTime
        this.form.crossDay = definition.crossDay
        this.form.standardMinutes = definition.standardMinutes
      } catch (error) {
        this.form.standardMinutes = 0
      }
    },
    addWorkSegment() {
      if (this.form.workSegments.length >= 8) return
      const previous = this.form.workSegments[this.form.workSegments.length - 1]
      if (this.form.workSegments.length === 1 && previous.startTime === '08:00:00' && previous.endTime === '20:00:00') {
        previous.endTime = '12:00:00'
        this.form.workSegments.push(createWorkSegment('14:00:00', '18:00:00'))
      } else {
        const startTime = timePlus(previous && previous.endTime, 60)
        this.form.workSegments.push(createWorkSegment(startTime, timePlus(startTime, 240)))
      }
      this.syncDerivedShift()
    },
    removeWorkSegment(index) {
      if (this.form.workSegments.length <= 1) return
      this.form.workSegments.splice(index, 1)
      this.syncDerivedShift()
    },
    moveWorkSegment(index, direction) {
      const target = index + direction
      if (target < 0 || target >= this.form.workSegments.length) return
      const next = this.form.workSegments.slice()
      const [item] = next.splice(index, 1)
      next.splice(target, 0, item)
      this.form.workSegments = next
      this.syncDerivedShift()
    },
    shiftPayload() {
      const definition = buildShiftDefinition(this.form.workSegments)
      const punchMode = this.form.punchMode === SEGMENT_PUNCH_MODE ? SEGMENT_PUNCH_MODE : CONTINUOUS_PUNCH_MODE
      if (punchMode === SEGMENT_PUNCH_MODE) {
        const derived = derivedSegmentPunchWindows(this.form.workSegments, this.form)
        if (derived.issues.length) throw new Error(derived.issues[0])
      }
      const payload = Object.assign({}, this.form, definition, { punchMode })
      delete payload.workSegments
      return payload
    }
  }
}
</script>

<style scoped>
.attendance-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.attendance-toolbar h2 { margin: 0 0 6px; font-size: 18px; }
.attendance-toolbar p { margin: 0; color: #6b7280; }
.attendance-toolbar > div:last-child { display: flex; gap: 8px; align-items: center; }
.table-card { margin-top: 16px; }
.cell-subtitle { margin-top: 4px; color: #8492a6; font-size: 12px; }
.work-periods { line-height: 1.6; }
.attendance-dialog-form { margin-top: 20px; }
.form-help { margin-top: 6px; color: #8492a6; font-size: 12px; line-height: 1.5; }
.segment-editor { width: 100%; }
.segment-row { display: grid; grid-template-columns: 64px minmax(130px, 1fr) 20px minmax(130px, 1fr) auto; align-items: center; gap: 8px; margin-bottom: 10px; }
.segment-index { color: #606266; font-weight: 600; }
.segment-row .el-date-editor.el-input { width: 100%; }
.segment-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 18px; min-height: 32px; color: #606266; }
.segment-summary strong { color: #1f7a6d; }
.break-periods { color: #8492a6; }
.derived-window-panel { width: 100%; }
.derived-window-list { margin: 8px 0 0; padding-left: 18px; color: #303133; line-height: 1.7; }
.window-clamped { color: #b8820c; }
.window-error { margin: 8px 0 0; color: #c45656; }
@media (max-width: 768px) {
  .attendance-toolbar { align-items: flex-start; flex-direction: column; }
  .attendance-toolbar > div:last-child { flex-wrap: wrap; }
  .segment-row { grid-template-columns: 54px 1fr 20px 1fr; }
  .segment-row .el-button-group { grid-column: 2 / 5; }
}
</style>
