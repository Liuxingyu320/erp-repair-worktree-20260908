<template>
  <section class="attendance-module day-result-management">
    <overtime-transfer-dialog :open="overtimeTransferOpen" :source="overtimeTransferSource" @close="overtimeTransferOpen = false" @changed="handleLoad" />
    <el-alert
      v-if="!validStore"
      title="请先切换到需要查看或结算考勤的门店"
      type="warning"
      show-icon
      :closable="false"
      class="context-alert"
    />
    <el-alert
      v-else-if="contextMismatch"
      title="工资异常链接与当前门店不一致"
      :description="`链接要求门店 ${expectedShopId}，当前为 ${shopContext.deptName || shopContext.deptId}。请先切换到正确门店，页面不会跨门店查询。`"
      type="error"
      show-icon
      :closable="false"
      class="context-alert"
    />

    <el-card shadow="never" class="oa-filter-card day-filter-card">
      <div class="day-filter-heading">
        <div>
          <h2>每日考勤结果</h2>
          <p v-if="salaryMonth">已按工资异常链接聚焦 {{ salaryMonth }}，数据只来自已发布排班和考勤 V2 结算。</p>
          <p v-else>查询、预检并结算当前门店的分钟级考勤结果。</p>
        </div>
        <el-tag v-if="query.exceptionOnly" type="warning" effect="dark">仅看异常</el-tag>
      </div>
      <el-form :inline="true" size="small" class="day-filter-form" @submit.native.prevent>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="query.dates"
            type="daterange"
            value-format="yyyy-MM-dd"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="员工">
          <el-select
            v-model="query.userId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchEmployees"
            :loading="employeeLoading"
            placeholder="全部员工"
          >
            <el-option
              v-for="item in employees"
              :key="item.userId"
              :label="`${item.userName || '未命名'}${item.employeeNo ? `·${item.employeeNo}` : ''}`"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item><el-checkbox v-model="query.exceptionOnly">仅看异常/未结算</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" icon="el-icon-search" :loading="loading" @click="handleLoad">查询并预检</el-button></el-form-item>
        <el-form-item>
          <el-button
            v-hasPermi="['oa:attendance:day:settle']"
            type="success"
            icon="el-icon-circle-check"
            :loading="settling"
            :disabled="!preflight || !preflight.totalSchedules"
            @click="settle(false)"
          >结算未结算结果</el-button>
        </el-form-item>
        <el-form-item>
          <el-button
            v-hasPermi="['oa:attendance:day:settle']"
            type="danger"
            plain
            icon="el-icon-refresh-left"
            :loading="settling"
            :disabled="!preflight || !preflight.settledCount"
            @click="settle(true)"
          >重算已结算结果</el-button>
        </el-form-item>
      </el-form>
      <p class="settlement-scope-note"><i class="el-icon-warning-outline" /> 员工和“仅看异常”只筛选表格；结算始终按当前门店与所选日期整批执行，以确认窗中的预检数量为准。</p>
    </el-card>

    <el-alert
      v-if="salaryMonth"
      title="工资异常聚焦说明"
      description="本表只能展示已发布排班的每日结果。如工资预检提示某员工整月没有已发布排班，请先到“周排班”补齐并发布。"
      type="info"
      show-icon
      :closable="false"
      class="salary-focus-alert"
    />

    <section v-if="preflight" class="preflight-summary" aria-label="结算预检汇总">
      <div><small>已发布排班</small><strong>{{ number(preflight.totalSchedules) }}</strong></div>
      <div class="ready"><small>预检可计算</small><strong>{{ number(preflight.readyCount) }}</strong></div>
      <div :class="{ blocked: number(preflight.blockedCount) > 0 }"><small>预检阻断</small><strong>{{ number(preflight.blockedCount) }}</strong></div>
      <div><small>已结算</small><strong>{{ number(preflight.settledCount) }}</strong></div>
      <div><small>{{ unfinalizedMonth ? `${unfinalizedMonth} 未结算` : '未结算' }}</small><strong>{{ unfinalizedCount == null ? '-' : unfinalizedCount }}</strong></div>
    </section>

    <el-alert
      v-if="preflight && preflight.blockedCount"
      :title="`预检发现 ${preflight.blockedCount} 条阻断，其中 ${activeBlockedCount} 条会阻断本次未结算批次`"
      description="常见原因包括班次尚未结束、缺上/下班卡、请假或补卡尚在审批。普通结算会跳过已结算行；显式重算时，所有阻断都必须先处理。"
      type="error"
      show-icon
      :closable="false"
      class="preflight-alert"
    />

    <el-card v-if="blockedItems.length" shadow="never" class="blocked-card">
      <div slot="header"><strong>阻断明细</strong><span>最多展示前 20 条</span></div>
      <div v-for="item in blockedItems.slice(0, 20)" :key="item.scheduleId" class="blocked-row">
        <span>{{ item.businessDate }}·{{ item.userName || `员工 ${item.userId}` }}</span>
        <strong>{{ issueText(item.issueCodes) }}</strong>
      </div>
    </el-card>

    <el-card shadow="never" class="oa-table-card day-table-card">
      <div slot="header" class="day-table-heading">
        <div><strong>每日结果</strong><span>当前显示 {{ displayRows.length }} / {{ mergedRows.length }} 条</span></div>
        <span>“预览”分钟尚未结算，只有服务端结算成功才会成为工资依据</span>
      </div>
      <el-table v-loading="loading" :data="displayRows" size="small" border empty-text="当前范围没有已发布排班结果">
        <el-table-column label="日期" prop="businessDate" width="105" fixed />
        <el-table-column label="员工" prop="userName" width="120" fixed />
        <el-table-column label="结算状态" width="105">
          <template slot-scope="scope"><el-tag :type="stateTag(scope.row.__state)" size="mini">{{ stateLabel(scope.row.__state) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="结果" width="125">
          <template slot-scope="scope"><el-tag :type="resultTag(scope.row)" size="mini">{{ resultLabel(scope.row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="计划(分)" width="85" align="right"><template slot-scope="scope">{{ metric(scope.row, 'scheduledMinutes') }}</template></el-table-column>
        <el-table-column label="工作(分)" width="85" align="right"><template slot-scope="scope">{{ metric(scope.row, 'workedMinutes') }}</template></el-table-column>
        <el-table-column label="带薪假(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'paidLeaveMinutes') }}</template></el-table-column>
        <el-table-column label="无薪假(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'unpaidLeaveMinutes') }}</template></el-table-column>
        <el-table-column label="缺勤(分)" width="85" align="right"><template slot-scope="scope">{{ metric(scope.row, 'absenceMinutes') }}</template></el-table-column>
        <el-table-column label="迟到(分)" width="85" align="right"><template slot-scope="scope">{{ metric(scope.row, 'lateMinutes') }}</template></el-table-column>
        <el-table-column label="原加班(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'rawOvertimeMinutes') }}</template></el-table-column>
        <el-table-column label="已用加班(分)" width="110" align="right"><template slot-scope="scope">{{ metric(scope.row, 'timeCreditUsedMinutes') }}</template></el-table-column>
        <el-table-column label="净加班(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'netOvertimeMinutes') }}</template></el-table-column>
        <el-table-column label="原早退(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'earlyLeaveMinutes') }}</template></el-table-column>
        <el-table-column label="已抵扣(分)" width="100" align="right"><template slot-scope="scope">{{ metric(scope.row, 'timeCreditOffsetMinutes') }}</template></el-table-column>
        <el-table-column label="净早退(分)" width="100" align="right"><template slot-scope="scope"><strong :class="{ 'net-early-warning': metric(scope.row, 'netEarlyLeaveMinutes') > 0 }">{{ metric(scope.row, 'netEarlyLeaveMinutes') }}</strong></template></el-table-column>
        <el-table-column label="异常原因" min-width="280">
          <template slot-scope="scope"><span :class="{ 'exception-text': scope.row.__exceptional }">{{ rowIssueText(scope.row) }}</span></template>
        </el-table-column>
        <el-table-column label="处理" width="190" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="hasRemainingWorkIssue(scope.row)"
              v-hasPermi="['oa:attendance:remaining-work:manage']"
              type="text"
              size="mini"
              @click="openRemainingWork(scope.row)"
            >剩余工作核验</el-button>
            <el-button
              v-if="hasTimeCreditAction(scope.row)"
              v-hasPermi="['oa:attendance:time-credit:manage']"
              type="text"
              size="mini"
              @click="openTimeCredit(scope.row)"
            >加班抵扣</el-button>
            <el-button v-if="scope.row.settledAt" v-hasPermi="['oa:attendance:leave:balance:convert']" type="text" size="mini" @click="overtimeTransferSource = scope.row; overtimeTransferOpen = true">核定转休</el-button>
            <span v-if="!hasRemainingWorkIssue(scope.row) && !hasTimeCreditAction(scope.row) && !scope.row.settledAt">-</span>
          </template>
        </el-table-column>
        <el-table-column label="结算时间" width="145"><template slot-scope="scope">{{ dateTime(scope.row.settledAt) }}</template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      title="部分请假剩余工作核验"
      :visible.sync="remainingDialogOpen"
      width="760px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetRemainingDialog"
    >
      <el-alert
        title="这里只确认请假后仍需工作的时间，不会生成现场打卡槽，也不会改写原照片或补卡记录。每次提交都会追加保留审计记录。"
        type="warning"
        show-icon
        :closable="false"
      />
      <div v-loading="remainingLoading" class="remaining-work-body">
        <div v-if="remainingRow" class="remaining-work-person">
          <strong>{{ remainingRow.businessDate }} · {{ remainingRow.userName || `员工 ${remainingRow.userId}` }}</strong>
          <span>排班 {{ remainingRow.scheduleId }}</span>
        </div>
        <el-empty v-if="!remainingLoading && !remainingIntervals.length" description="当前请假区间已变化，暂无待核验的剩余工作；请刷新预检。" />
        <div v-for="interval in remainingIntervals" :key="remainingIntervalKey(interval)" class="remaining-interval-card">
          <div class="remaining-interval-heading">
            <div>
              <strong>{{ interval.segmentLabel || '工作段' }}</strong>
              <span>{{ intervalText(interval) }} · {{ number(interval.remainingMinutes) }} 分钟</span>
            </div>
            <div>
              <el-tag :type="remainingStateTag(interval.state)" size="mini">{{ remainingStateLabel(interval.state) }}</el-tag>
              <el-button type="text" size="mini" @click="selectRemainingInterval(interval)">
                {{ interval.latestConfirmation ? '重新核验' : '开始核验' }}
              </el-button>
            </div>
          </div>
          <div v-if="interval.latestConfirmation" class="remaining-latest">
            最近结论：{{ remainingDecisionLabel(interval.latestConfirmation.decision) }}；
            {{ interval.latestConfirmation.reason || '未填写原因' }}；
            {{ dateTime(interval.latestConfirmation.decidedAt) }}
          </div>
        </div>

        <el-form v-if="selectedRemainingInterval" label-width="110px" size="small" class="remaining-confirm-form">
          <h4>核验 {{ intervalText(selectedRemainingInterval) }}</h4>
          <el-form-item label="核验结论" required>
            <el-radio-group v-model="remainingForm.decision" @change="remainingDecisionChanged">
              <el-radio label="ATTENDED">确认出勤</el-radio>
              <el-radio label="ABSENT">确认缺勤</el-radio>
              <el-radio label="RETURN_FOR_EVIDENCE">退回补证</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="remainingForm.decision === 'ATTENDED'">
            <el-form-item label="实际到岗" required>
              <el-date-picker v-model="remainingForm.actualArrivalTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择实际到岗时间" style="width:100%" />
            </el-form-item>
            <el-form-item label="实际离岗" required>
              <el-date-picker v-model="remainingForm.actualDepartureTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择实际离岗时间" style="width:100%" />
            </el-form-item>
          </template>
          <el-form-item label="核验原因" required>
            <el-input v-model.trim="remainingForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="至少填写 2 个字符，说明证据和判断依据" />
          </el-form-item>
          <el-form-item label="补充证据">
            <el-upload
              action=""
              :auto-upload="false"
              :limit="1"
              :file-list="remainingFiles"
              :on-change="remainingFileChanged"
              :on-remove="remainingFileRemoved"
              accept=".pdf,.png,.jpg,.jpeg"
            >
              <el-button size="mini" icon="el-icon-paperclip">选择图片或 PDF</el-button>
              <div slot="tip" class="el-upload__tip">附件只作为管理核验依据，不伪造现场照片水印。</div>
            </el-upload>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="remainingSaving" @click="submitRemainingWork">追加核验记录</el-button>
            <el-button @click="selectedRemainingInterval = null">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>

    <el-dialog
      title="店长调整：加班抵扣早退"
      :visible.sync="timeCreditDialogOpen"
      width="780px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetTimeCreditDialog"
    >
      <el-alert
        title="只可使用同一工资月、早退日期当日或之前已产生的加班。抵扣部分不再计算加班费；原打卡、照片、定位、原加班和原早退均保留。"
        type="warning"
        show-icon
        :closable="false"
      />
      <div v-loading="timeCreditLoading" class="time-credit-body">
        <template v-if="timeCreditContext && timeCreditContext.target">
          <div class="time-credit-target">
            <div>
              <small>员工与早退日期</small>
              <strong>{{ timeCreditContext.target.userName || `员工 ${timeCreditContext.target.userId}` }} · {{ timeCreditContext.target.businessDate }}</strong>
            </div>
            <div><small>原早退</small><strong>{{ number(timeCreditContext.targetRawEarlyLeaveMinutes) }} 分钟</strong></div>
            <div><small>已抵扣</small><strong>{{ number(timeCreditContext.targetOffsetMinutes) }} 分钟</strong></div>
            <div><small>剩余早退</small><strong>{{ number(timeCreditContext.targetRemainingEarlyLeaveMinutes) }} 分钟</strong></div>
          </div>

          <el-alert
            v-if="timeCreditContext.salaryLocked"
            title="该员工本月工资已生成，本月考勤抵扣已锁定；如确需调整，请先按工资流程撤回/删除该月工资后再操作。"
            type="error"
            show-icon
            :closable="false"
            class="time-credit-lock-alert"
          />

          <el-alert
            v-if="timeCreditContext.ledgerInconsistent"
            :title="timeCreditContext.ledgerWarning || '当前抵扣台账与重算后的日结不一致，只能先撤销失效调整。'"
            type="error"
            show-icon
            :closable="false"
            class="time-credit-lock-alert"
          />

          <el-form v-if="!timeCreditContext.ledgerInconsistent && number(timeCreditContext.targetRemainingEarlyLeaveMinutes) > 0" label-width="116px" size="small" class="time-credit-form">
            <el-form-item label="加班来源日" required>
              <el-select v-model="timeCreditForm.sourceDayResultId" placeholder="选择此前已产生加班的日期" style="width:100%" @change="timeCreditSourceChanged">
                <el-option
                  v-for="source in timeCreditContext.sources || []"
                  :key="source.dayResultId"
                  :label="`${source.businessDate} · 原加班 ${number(source.rawOvertimeMinutes)} 分钟 · 已用 ${number(source.usedMinutes)} · 可用 ${number(source.availableMinutes)}`"
                  :value="source.dayResultId"
                  :disabled="String(source.dayResultId) === String(timeCreditContext.target.dayResultId)"
                />
              </el-select>
              <div v-if="!(timeCreditContext.sources || []).length" class="form-tip warning">本月在该早退日之前没有可用加班分钟。</div>
            </el-form-item>
            <el-form-item label="抵扣分钟" required>
              <el-input-number v-model="timeCreditForm.adjustmentMinutes" :min="1" :max="timeCreditMaximum" :step="1" step-strictly />
              <span class="form-tip">本次最多 {{ timeCreditMaximum }} 分钟</span>
            </el-form-item>
            <el-form-item label="调整原因" required>
              <el-input v-model.trim="timeCreditForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="至少填写 2 个字符，例如：经店长核对，使用8月20日延时工作抵扣8月23日提前离岗" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="timeCreditSaving" :disabled="Boolean(timeCreditContext.salaryLocked) || !timeCreditMaximum" @click="submitTimeCredit">确认抵扣并保留审计记录</el-button>
            </el-form-item>
          </el-form>

          <section class="time-credit-history">
            <h4>调整历史</h4>
            <el-empty v-if="!(timeCreditContext.history || []).length" description="尚无抵扣记录" :image-size="70" />
            <div v-for="item in timeCreditContext.history || []" :key="item.adjustmentId" class="time-credit-history-row">
              <div>
                <strong>{{ item.sourceBusinessDate }} → {{ item.targetBusinessDate }} · {{ number(item.adjustmentMinutes) }} 分钟</strong>
                <span>{{ item.operatorName || '-' }} · {{ dateTime(item.createTime) }} · {{ item.reason }}</span>
                <span v-if="item.reversed">已由 {{ item.reversalOperatorName || '-' }} 于 {{ dateTime(item.reversedAt) }} 撤销：{{ item.reversalReason }}</span>
              </div>
              <el-tag v-if="item.reversed" type="info" size="mini">已撤销</el-tag>
              <el-button v-else type="text" size="mini" :disabled="Boolean(timeCreditContext.salaryLocked) || timeCreditSaving" @click="reverseTimeCredit(item)">撤销</el-button>
            </div>
          </section>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import {
  applyAttendanceTimeCredit,
  countUnfinalizedAttendanceDayResults,
  confirmAttendanceRemainingWork,
  getAttendanceDayResultPreflight,
  getAttendanceTimeCreditContext,
  listAttendanceRemainingWorkIntervals,
  listAttendanceDayResults,
  listAttendanceEmployeeOptions,
  reverseAttendanceTimeCredit,
  settleAttendanceDayResults,
  uploadAttendanceRemainingWorkAttachment
} from '@/api/oa/attendanceV2'
import { checkPermi } from '@/utils/permission'

const pad = value => String(value).padStart(2, '0')
const dateText = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const currentMonthRange = () => {
  const now = new Date()
  return [dateText(new Date(now.getFullYear(), now.getMonth(), 1)), dateText(new Date(now.getFullYear(), now.getMonth() + 1, 0))]
}
const payloadOf = response => {
  if (!response || typeof response !== 'object') return response
  return Object.prototype.hasOwnProperty.call(response, 'data') ? response.data : response
}
const arrayOf = response => {
  const payload = payloadOf(response)
  return Array.isArray(payload) ? payload : []
}

const ISSUE_LABELS = Object.freeze({
  SHIFT_NOT_ENDED: '班次或下班打卡窗口尚未结束',
  PENDING_CORRECTION: '有补卡申请尚在审批',
  PENDING_LEAVE: '有请假申请尚在审批',
  MISSING_IN: '缺上班卡',
  MISSING_OUT: '缺下班卡',
  MISSING_DAY_RESULT: '尚未生成每日结算结果',
  LEAVE_REQUEST_REVIEW_REQUIRED: '请假申请已提交，原结果等待重新结算',
  CORRECTION_REQUEST_REVIEW_REQUIRED: '补卡申请已提交，原结果等待重新结算',
  LEAVE_DECISION_CHANGED: '请假审批状态已变化，原结果等待重新结算',
  CORRECTION_DECISION_CHANGED: '补卡审批状态已变化，原结果等待重新结算',
  APPROVED_LEAVE_CHANGED: '请假已批准，原结果等待重新结算',
  REMAINING_WORK_CONFIRMATION_CHANGED: '剩余工作已核验，等待重新结算',
  APPROVED_CORRECTION_CHANGED: '补卡已批准，原结果等待重新结算',
  PUNCH_ORDER_INVALID: '上下班打卡时间顺序异常',
  INVALID_APPROVED_LEAVE_SEGMENT: '已审批请假时段数据异常',
  OVERLAPPING_APPROVED_LEAVE: '已审批请假时段重叠',
  SCHEDULE_SEGMENT_SNAPSHOT_MISSING: '排班缺少班次时段快照',
  SCHEDULE_SEGMENT_SNAPSHOT_INVALID: '排班班次时段快照异常',
  REMAINING_WORK_CONFIRMATION_REQUIRED: '部分请假后仍有工作时段，需管理者核验',
  REMAINING_WORK_EVIDENCE_REQUIRED: '剩余工作已退回补证，需重新核验',
  INVALID_REMAINING_WORK_CONFIRMATION: '剩余工作核验记录无效或已过期'
})

export default {
  name: 'AttendanceDayResultManagement',
  components: { OvertimeTransferDialog: () => import('./OvertimeTransferDialog.vue') },
  props: { shopContext: { type: Object, default: () => ({}) } },
  data() {
    return {
      overtimeTransferOpen: false, overtimeTransferSource: {},
      loading: false,
      employeeLoading: false,
      settling: false,
      rows: [],
      employees: [],
      preflight: null,
      unfinalizedCount: null,
      remainingDialogOpen: false,
      remainingLoading: false,
      remainingSaving: false,
      remainingRow: null,
      remainingIntervals: [],
      selectedRemainingInterval: null,
      remainingFiles: [],
      remainingForm: {
        decision: 'ATTENDED',
        actualArrivalTime: '',
        actualDepartureTime: '',
        reason: ''
      },
      timeCreditDialogOpen: false,
      timeCreditLoading: false,
      timeCreditSaving: false,
      timeCreditRow: null,
      timeCreditContext: null,
      timeCreditRequestIds: Object.create(null),
      timeCreditForm: {
        sourceDayResultId: '',
        adjustmentMinutes: 1,
        reason: ''
      },
      expectedShopId: '',
      salaryMonth: '',
      query: { dates: currentMonthRange(), userId: '', exceptionOnly: false }
    }
  },
  computed: {
    validStore() {
      return Boolean(this.shopContext && this.shopContext.isStore && this.shopContext.deptId)
    },
    contextMismatch() {
      return Boolean(this.expectedShopId && this.validStore && String(this.expectedShopId) !== String(this.shopContext.deptId))
    },
    canSettle() {
      return checkPermi(['oa:attendance:day:settle'])
    },
    timeCreditMaximum() {
      const context = this.timeCreditContext || {}
      const sources = Array.isArray(context.sources) ? context.sources : []
      const source = sources.find(item => String(item.dayResultId) === String(this.timeCreditForm.sourceDayResultId))
      return Math.max(0, Math.min(
        this.number(source && source.availableMinutes),
        this.number(context.targetRemainingEarlyLeaveMinutes)
      ))
    },
    preflightBySchedule() {
      const map = Object.create(null)
      ;((this.preflight && this.preflight.items) || []).forEach(item => { map[String(item.scheduleId)] = item })
      return map
    },
    mergedRows() {
      return this.rows.map(row => {
        const item = this.preflightBySchedule[String(row.scheduleId)] || null
        const missing = String(row.resultStatus || '').toUpperCase() === 'MISSING_RESULT'
        const display = missing && item && item.preview ? item.preview : row
        const codes = this.codes(row.exceptionCodes)
          .concat(item && Array.isArray(item.issueCodes) ? item.issueCodes : [])
          .filter((code, index, values) => code && values.indexOf(code) === index)
        const currentIssueCodes = item && Array.isArray(item.issueCodes)
          ? item.issueCodes
          : this.codes(row.exceptionCodes)
        const previewStatus = String(display && display.resultStatus || row.resultStatus || '').toUpperCase()
        const exceptionalStatuses = ['MISSING_RESULT', 'LATE', 'EARLY', 'LATE_EARLY', 'MISSED_IN', 'MISSED_OUT', 'ABSENT', 'EXCEPTION']
        return Object.assign({}, row, {
          __display: display || row,
          __state: item ? item.state : (row.settledAt ? 'SETTLED' : 'READY'),
          __codes: codes,
          __currentIssueCodes: currentIssueCodes,
          __previewStatus: previewStatus,
          __exceptional: Boolean((item && item.state === 'BLOCKED') || codes.length || exceptionalStatuses.includes(String(row.resultStatus || '').toUpperCase()) || exceptionalStatuses.includes(previewStatus))
        })
      })
    },
    displayRows() {
      return this.query.exceptionOnly ? this.mergedRows.filter(row => row.__exceptional) : this.mergedRows
    },
    blockedItems() {
      return ((this.preflight && this.preflight.items) || []).filter(item => item && item.state === 'BLOCKED')
    },
    activeBlockedCount() {
      return this.blockedItems.filter(item => item.settled !== true).length
    },
    unfinalizedMonth() {
      const dates = this.query.dates || []
      if (dates.length !== 2) return ''
      const fromMonth = String(dates[0] || '').slice(0, 7)
      return fromMonth && fromMonth === String(dates[1] || '').slice(0, 7) ? fromMonth : ''
    }
  },
  watch: {
    'shopContext.deptId'() {
      this.loadEmployeeOptions()
      this.load()
    },
    '$route.query': {
      deep: true,
      handler() {
        this.applyRouteQuery()
        this.load()
      }
    }
  },
  created() {
    this.applyRouteQuery()
    this.loadEmployeeOptions()
    this.load()
  },
  methods: {
    applyRouteQuery() {
      const routeQuery = this.$route && this.$route.query ? this.$route.query : {}
      const month = /^\d{4}-\d{2}$/.test(String(routeQuery.salaryMonth || '')) ? String(routeQuery.salaryMonth) : ''
      this.salaryMonth = month
      this.expectedShopId = routeQuery.shopId == null ? '' : String(routeQuery.shopId)
      this.query.exceptionOnly = String(routeQuery.exceptionOnly || '').toLowerCase() === 'true'
      this.query.userId = routeQuery.userId || ''
      if (month) {
        const parts = month.split('-').map(Number)
        this.query.dates = [`${month}-01`, dateText(new Date(parts[0], parts[1], 0))]
      }
    },
    requestParams() {
      const dates = this.query.dates || []
      return {
        shopId: this.shopContext.deptId,
        dateFrom: dates[0],
        dateTo: dates[1],
        userId: this.query.userId || undefined
      }
    },
    validateRequest() {
      if (!this.validStore) return '请先切换到门店'
      if (this.contextMismatch) return '当前门店与工资异常链接不一致'
      const dates = this.query.dates || []
      if (dates.length !== 2 || !dates[0] || !dates[1] || dates[0] > dates[1]) return '请选择有效日期范围'
      const from = new Date(`${dates[0]}T00:00:00`)
      const to = new Date(`${dates[1]}T00:00:00`)
      const days = Math.round((to.getTime() - from.getTime()) / 86400000)
      if (!Number.isFinite(days) || days < 0 || days >= 31) return '结算预检一次最多支持 31 个日历日'
      return ''
    },
    handleLoad() {
      const invalid = this.validateRequest()
      if (invalid) { this.$modal.msgWarning(invalid); return Promise.resolve() }
      return this.load()
    },
    load() {
      const invalid = this.validateRequest()
      if (invalid) {
        this.rows = []
        this.preflight = null
        this.unfinalizedCount = null
        return Promise.resolve()
      }
      if (this.loading) return Promise.resolve()
      this.loading = true
      const params = this.requestParams()
      const count = this.unfinalizedMonth
        ? countUnfinalizedAttendanceDayResults({ shopId: params.shopId, month: this.unfinalizedMonth }).then(payloadOf).catch(() => null)
        : Promise.resolve(null)
      return Promise.all([
        listAttendanceDayResults(params),
        getAttendanceDayResultPreflight({ shopId: params.shopId, dateFrom: params.dateFrom, dateTo: params.dateTo }),
        count
      ]).then(([rowsResponse, preflightResponse, unfinalized]) => {
        this.rows = arrayOf(rowsResponse)
        this.preflight = payloadOf(preflightResponse) || null
        this.unfinalizedCount = unfinalized == null ? null : Number(unfinalized)
      }).catch(error => {
        this.rows = []
        this.preflight = null
        this.unfinalizedCount = null
        this.$modal.msgError(error.message || '每日考勤结果加载失败')
      }).finally(() => { this.loading = false })
    },
    refreshPreflight() {
      const invalid = this.validateRequest()
      if (invalid) return Promise.reject(new Error(invalid))
      const params = this.requestParams()
      return getAttendanceDayResultPreflight({ shopId: params.shopId, dateFrom: params.dateFrom, dateTo: params.dateTo }).then(response => {
        this.preflight = payloadOf(response) || null
        return this.preflight
      })
    },
    settle(recalculate) {
      if (!this.canSettle || this.settling) return
      this.settling = true
      let settlementResult = null
      this.refreshPreflight().then(preflight => {
        if (!preflight || !preflight.totalSchedules) {
          this.$modal.msgWarning('当前范围没有已发布排班，无可结算结果')
          return false
        }
        const items = Array.isArray(preflight.items) ? preflight.items : []
        const blocked = items.filter(item => item && item.ready !== true && (recalculate || item.settled !== true))
        if (blocked.length) {
          this.$modal.msgError(`本次结算有 ${blocked.length} 条阻断，请先处理阻断明细`)
          return false
        }
        if (recalculate) {
          return this.$modal.confirm(
            `重算会覆盖 ${preflight.settledCount || 0} 条已结算结果，并可能改变工资依据。是否继续？`,
            '重算风险确认',
            { confirmButtonText: '继续校验', type: 'warning' }
          ).then(() => this.$modal.confirm(
            `最终确认：将对 ${preflight.totalSchedules} 条已发布排班执行服务端重算，不可撤销。`,
            '最终确认重算',
            { confirmButtonText: '确认覆盖并重算', type: 'error' }
          )).then(() => true)
        }
        const pendingCount = items.filter(item => item && item.settled !== true).length
        if (!pendingCount) {
          this.$modal.msgWarning('当前范围已全部结算，如需更新请使用“重算已结算结果”')
          return false
        }
        return this.$modal.confirm(
          `预检：总排班 ${preflight.totalSchedules} 条，阻断 ${preflight.blockedCount || 0} 条，本次将结算 ${pendingCount} 条，已结算 ${preflight.settledCount || 0} 条将跳过。`,
          '确认考勤结算',
          { confirmButtonText: '确认结算', type: 'warning' }
        ).then(() => true)
      }).then(confirmed => {
        if (!confirmed) return null
        const params = this.requestParams()
        return settleAttendanceDayResults({
          shopId: params.shopId,
          dateFrom: params.dateFrom,
          dateTo: params.dateTo,
          recalculate: Boolean(recalculate)
        })
      }).then(response => {
        if (!response) return
        settlementResult = payloadOf(response) || {}
        this.$modal.msgSuccess(`${recalculate ? '重算' : '结算'}完成：成功 ${settlementResult.settledCount || 0} 条，跳过 ${settlementResult.skippedCount || 0} 条`)
        return this.load()
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '考勤结算失败')
      }).finally(() => { this.settling = false })
    },
    loadEmployeeOptions(keyword) {
      if (!this.validStore || this.contextMismatch) { this.employees = []; return Promise.resolve() }
      this.employeeLoading = true
      return listAttendanceEmployeeOptions({ shopId: this.shopContext.deptId, keyword: keyword || undefined }).then(response => {
        this.employees = arrayOf(response)
      }).catch(error => {
        this.employees = []
        this.$modal.msgError(error.message || '员工选项加载失败')
      }).finally(() => { this.employeeLoading = false })
    },
    searchEmployees(keyword) {
      return this.loadEmployeeOptions(String(keyword || '').trim())
    },
    hasRemainingWorkIssue(row) {
      const codes = this.codes(row && row.__currentIssueCodes)
      return codes.some(code => ['REMAINING_WORK_CONFIRMATION_REQUIRED', 'REMAINING_WORK_EVIDENCE_REQUIRED', 'INVALID_REMAINING_WORK_CONFIRMATION'].includes(String(code).toUpperCase()))
    },
    hasTimeCreditAction(row) {
      if (!checkPermi(['oa:attendance:time-credit:manage'])) return false
      return Boolean(row && row.dayResultId && row.settledAt && (
        this.metric(row, 'earlyLeaveMinutes') > 0 ||
        this.metric(row, 'timeCreditOffsetMinutes') > 0
      ))
    },
    openTimeCredit(row) {
      if (!this.hasTimeCreditAction(row)) return Promise.resolve()
      this.timeCreditRow = row
      this.timeCreditDialogOpen = true
      return this.loadTimeCreditContext()
    },
    loadTimeCreditContext() {
      if (!this.timeCreditRow || !this.timeCreditRow.dayResultId) return Promise.resolve()
      this.timeCreditLoading = true
      return getAttendanceTimeCreditContext(this.timeCreditRow.dayResultId).then(response => {
        this.timeCreditContext = payloadOf(response) || null
        const sources = this.timeCreditContext && Array.isArray(this.timeCreditContext.sources)
          ? this.timeCreditContext.sources.filter(item => String(item.dayResultId) !== String(this.timeCreditRow.dayResultId))
          : []
        if (this.timeCreditContext) this.timeCreditContext.sources = sources
        const selectedExists = sources.some(item => String(item.dayResultId) === String(this.timeCreditForm.sourceDayResultId))
        if (!selectedExists) this.timeCreditForm.sourceDayResultId = sources.length ? sources[0].dayResultId : ''
        this.timeCreditSourceChanged()
      }).catch(error => {
        this.timeCreditContext = null
        this.$modal.msgError(error.message || '加班抵扣信息加载失败')
      }).finally(() => { this.timeCreditLoading = false })
    },
    resetTimeCreditDialog() {
      this.timeCreditRow = null
      this.timeCreditContext = null
      this.timeCreditForm = { sourceDayResultId: '', adjustmentMinutes: 1, reason: '' }
    },
    timeCreditSourceChanged() {
      const max = this.timeCreditMaximum
      if (max <= 0) this.timeCreditForm.adjustmentMinutes = 1
      else if (!this.timeCreditForm.adjustmentMinutes || this.timeCreditForm.adjustmentMinutes > max) this.timeCreditForm.adjustmentMinutes = max
    },
    submitTimeCredit() {
      if (this.timeCreditSaving || !this.timeCreditContext || this.timeCreditContext.salaryLocked) return
      const reason = String(this.timeCreditForm.reason || '').trim()
      const minutes = this.number(this.timeCreditForm.adjustmentMinutes)
      if (!this.timeCreditForm.sourceDayResultId) { this.$modal.msgWarning('请选择加班来源日'); return }
      if (!Number.isInteger(minutes) || minutes < 1 || minutes > this.timeCreditMaximum) { this.$modal.msgWarning('抵扣分钟超过本次可用范围'); return }
      if (reason.length < 2) { this.$modal.msgWarning('请填写至少 2 个字符的调整原因'); return }
      this.timeCreditSaving = true
      const command = {
        sourceDayResultId: this.timeCreditForm.sourceDayResultId,
        targetDayResultId: this.timeCreditContext.target.dayResultId,
        adjustmentMinutes: minutes,
        reason
      }
      const clientRequestId = this.timeCreditCommandRequestId('apply', command)
      return applyAttendanceTimeCredit({ ...command, clientRequestId }).then(() => {
        this.clearTimeCreditCommandRequestId('apply', command)
        this.$modal.msgSuccess('已追加加班抵扣记录；该分钟不再计算加班费')
        this.timeCreditForm.reason = ''
        return Promise.all([this.loadTimeCreditContext(), this.load()])
      }).catch(error => {
        this.$modal.msgError(error.message || '加班抵扣失败')
      }).finally(() => { this.timeCreditSaving = false })
    },
    reverseTimeCredit(item) {
      if (!item || item.reversed || this.timeCreditSaving || (this.timeCreditContext && this.timeCreditContext.salaryLocked)) return
      return this.$prompt('请输入撤销原因（至少 2 个字符）。撤销只追加反向记录，不删除原抵扣。', '撤销加班抵扣', {
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        inputPattern: /^.{2,500}$/,
        inputErrorMessage: '撤销原因需填写 2 至 500 个字符'
      }).then(({ value }) => {
        this.timeCreditSaving = true
        const command = {
          adjustmentId: item.adjustmentId,
          reason: String(value || '').trim()
        }
        const clientRequestId = this.timeCreditCommandRequestId('reverse', command)
        return reverseAttendanceTimeCredit(item.adjustmentId, {
          clientRequestId,
          reason: command.reason
        }).then(() => {
          this.clearTimeCreditCommandRequestId('reverse', command)
          this.$modal.msgSuccess('抵扣已撤销，原记录和撤销记录均已保留')
          return Promise.all([this.loadTimeCreditContext(), this.load()])
        }).finally(() => { this.timeCreditSaving = false })
      }).catch(error => {
        if (error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '撤销抵扣失败')
      })
    },
    timeCreditCommandRequestId(action, command) {
      const signature = `${action}:${JSON.stringify(command || {})}`
      if (!this.timeCreditRequestIds[signature]) {
        this.timeCreditRequestIds[signature] = this.newTimeCreditRequestId(action)
      }
      return this.timeCreditRequestIds[signature]
    },
    clearTimeCreditCommandRequestId(action, command) {
      const signature = `${action}:${JSON.stringify(command || {})}`
      delete this.timeCreditRequestIds[signature]
    },
    newTimeCreditRequestId(action) {
      const random = window.crypto && typeof window.crypto.randomUUID === 'function'
        ? window.crypto.randomUUID().replace(/-/g, '').slice(0, 16)
        : `${Math.random().toString(36).slice(2, 10)}${Math.random().toString(36).slice(2, 10)}`
      return `time-credit:${action}:${Date.now()}:${random}`.slice(0, 64)
    },
    openRemainingWork(row) {
      if (!checkPermi(['oa:attendance:remaining-work:manage'])) return
      this.remainingRow = row
      this.remainingDialogOpen = true
      this.remainingLoading = true
      this.remainingIntervals = []
      this.selectedRemainingInterval = null
      return listAttendanceRemainingWorkIntervals(row.scheduleId).then(response => {
        this.remainingIntervals = arrayOf(response)
      }).catch(error => {
        this.$modal.msgError(error.message || '剩余工作区间加载失败')
      }).finally(() => { this.remainingLoading = false })
    },
    resetRemainingDialog() {
      this.remainingRow = null
      this.remainingIntervals = []
      this.selectedRemainingInterval = null
      this.remainingFiles = []
      this.remainingForm = { decision: 'ATTENDED', actualArrivalTime: '', actualDepartureTime: '', reason: '' }
    },
    selectRemainingInterval(interval) {
      this.selectedRemainingInterval = interval
      this.remainingFiles = []
      this.remainingForm = {
        decision: 'ATTENDED',
        actualArrivalTime: this.localDateTime(interval.remainingStart),
        actualDepartureTime: this.localDateTime(interval.remainingEnd),
        reason: ''
      }
    },
    remainingDecisionChanged(value) {
      if (value === 'ATTENDED' && this.selectedRemainingInterval) {
        this.remainingForm.actualArrivalTime = this.localDateTime(this.selectedRemainingInterval.remainingStart)
        this.remainingForm.actualDepartureTime = this.localDateTime(this.selectedRemainingInterval.remainingEnd)
      } else {
        this.remainingForm.actualArrivalTime = ''
        this.remainingForm.actualDepartureTime = ''
      }
    },
    remainingFileChanged(file, files) {
      this.remainingFiles = files.slice(-1)
    },
    remainingFileRemoved() {
      this.remainingFiles = []
    },
    submitRemainingWork() {
      const interval = this.selectedRemainingInterval
      if (!interval || this.remainingSaving) return
      const reason = String(this.remainingForm.reason || '').trim()
      if (reason.length < 2) { this.$modal.msgWarning('请填写至少 2 个字符的核验原因'); return }
      if (this.remainingForm.decision === 'ATTENDED' && (!this.remainingForm.actualArrivalTime || !this.remainingForm.actualDepartureTime)) {
        this.$modal.msgWarning('请填写实际到岗和离岗时间')
        return
      }
      this.remainingSaving = true
      const payload = {
        scheduleId: interval.scheduleId,
        remainingStart: interval.remainingStart,
        remainingEnd: interval.remainingEnd,
        decision: this.remainingForm.decision,
        actualArrivalTime: this.remainingForm.decision === 'ATTENDED' ? this.remainingForm.actualArrivalTime : null,
        actualDepartureTime: this.remainingForm.decision === 'ATTENDED' ? this.remainingForm.actualDepartureTime : null,
        reason
      }
      let confirmationId = null
      confirmAttendanceRemainingWork(payload).then(response => {
        const saved = payloadOf(response) || {}
        confirmationId = saved.confirmationId
        const selected = this.remainingFiles[0]
        if (selected && selected.raw && confirmationId) return uploadAttendanceRemainingWorkAttachment(confirmationId, selected.raw)
        return null
      }).then(() => {
        this.$modal.msgSuccess('剩余工作核验已追加保存')
        this.selectedRemainingInterval = null
        this.remainingFiles = []
        return Promise.all([this.openRemainingWork(this.remainingRow), this.load()])
      }).catch(error => {
        this.$modal.msgError(error.message || '剩余工作核验保存失败')
      }).finally(() => { this.remainingSaving = false })
    },
    remainingIntervalKey(interval) {
      return `${interval.scheduleSegmentSnapshotId || 'boundary'}:${interval.remainingStart}:${interval.remainingEnd}`
    },
    intervalText(interval) {
      return `${this.dateTimeSeconds(interval && interval.remainingStart)} 至 ${this.dateTimeSeconds(interval && interval.remainingEnd)}`
    },
    localDateTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : ''
    },
    dateTimeSeconds(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
    },
    remainingDecisionLabel(value) {
      return { ATTENDED: '确认出勤', ABSENT: '确认缺勤', RETURN_FOR_EVIDENCE: '退回补证' }[String(value || '').toUpperCase()] || '未知结论'
    },
    remainingStateLabel(value) {
      return { PENDING: '待核验', CONFIRMED: '已核验', NEEDS_EVIDENCE: '待补证' }[String(value || '').toUpperCase()] || '待核验'
    },
    remainingStateTag(value) {
      return { PENDING: 'warning', CONFIRMED: 'success', NEEDS_EVIDENCE: 'danger' }[String(value || '').toUpperCase()] || 'info'
    },
    codes(value) {
      if (Array.isArray(value)) return value.map(item => String(item || '').trim()).filter(Boolean)
      return String(value || '').split(',').map(item => item.trim()).filter(Boolean)
    },
    issueLabel(code) {
      const key = String(code || '').toUpperCase()
      if (ISSUE_LABELS[key]) return ISSUE_LABELS[key]
      if (key.startsWith('MULTIPLE_APPROVED_CORRECTION_')) return '同一打卡方向存在多条已批准补卡'
      if (key.startsWith('INVALID_APPROVED_CORRECTION_')) return '已批准补卡与排班不匹配'
      if (key.startsWith('APPROVED_CORRECTION_OUTSIDE_WINDOW_')) return '已批准补卡时间超出打卡窗口'
      if (key.startsWith('INVALID_ACCEPTED_PUNCH_')) return '已受理打卡事件与排班不匹配'
      return key ? '其他考勤异常' : '-'
    },
    issueText(codes) {
      const values = this.codes(codes)
      return values.length ? values.map(this.issueLabel).join('、') : '-'
    },
    rowIssueText(row) {
      if (row.__codes && row.__codes.length) return this.issueText(row.__codes)
      const status = String(row.__previewStatus || row.resultStatus || '').toUpperCase()
      const offset = this.metric(row, 'timeCreditOffsetMinutes')
      const netEarly = this.metric(row, 'netEarlyLeaveMinutes')
      if (offset > 0 && ['EARLY', 'LATE_EARLY'].includes(status)) {
        return netEarly > 0 ? `早退已抵扣 ${offset} 分钟，剩余 ${netEarly} 分钟` : `原早退 ${this.metric(row, 'earlyLeaveMinutes')} 分钟已全部抵扣`
      }
      return { LATE: '迟到', EARLY: '早退', LATE_EARLY: '迟到且早退', ABSENT: '缺勤', MISSED_IN: '缺上班卡', MISSED_OUT: '缺下班卡', EXCEPTION: '考勤异常' }[status] || '-'
    },
    metric(row, key) {
      return this.number(row && row.__display ? row.__display[key] : row && row[key])
    },
    number(value) {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : 0
    },
    stateLabel(value) {
      return { READY: '待结算', SETTLED: '已结算', BLOCKED: '已阻断' }[String(value || '').toUpperCase()] || '未知'
    },
    stateTag(value) {
      return { READY: 'warning', SETTLED: 'success', BLOCKED: 'danger' }[String(value || '').toUpperCase()] || 'info'
    },
    resultLabel(row) {
      const original = String(row.resultStatus || '').toUpperCase()
      const preview = String(row.__previewStatus || original).toUpperCase()
      const labels = { NORMAL: '正常', LATE: '迟到', EARLY: '早退', LATE_EARLY: '迟到+早退', MISSED_IN: '缺上班卡', MISSED_OUT: '缺下班卡', ABSENT: '缺勤', LEAVE_FULL: '全日请假', LEAVE_PARTIAL: '部分请假', EXCEPTION: '异常', MISSING_RESULT: '未结算' }
      if (this.metric(row, 'timeCreditOffsetMinutes') > 0 && ['EARLY', 'LATE_EARLY'].includes(original)) {
        return this.metric(row, 'netEarlyLeaveMinutes') > 0 ? `${labels[original]}·部分抵扣` : `${labels[original]}·已抵扣`
      }
      return original === 'MISSING_RESULT' && preview && preview !== 'MISSING_RESULT'
        ? `预览·${labels[preview] || '未知结果'}`
        : (labels[original] || '未知结果')
    },
    resultTag(row) {
      const status = String(row.resultStatus || '').toUpperCase()
      if (status === 'NORMAL') return 'success'
      if (status.indexOf('LEAVE_') === 0) return 'info'
      if (status === 'MISSING_RESULT') return 'warning'
      return 'danger'
    },
    dateTime(value) {
      return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
    }
  }
}
</script>

<style scoped>
.context-alert, .day-filter-card, .salary-focus-alert, .preflight-summary, .preflight-alert, .blocked-card, .day-table-card { margin-top: 16px; }
.day-filter-heading, .day-table-heading, .blocked-card ::v-deep .el-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.day-filter-heading h2 { margin: 0 0 6px; font-size: 18px; }
.day-filter-heading p { margin: 0; color: #6b7280; line-height: 1.6; }
.day-filter-form { margin-top: 16px; }
.day-filter-form .el-select { width: 210px; }
.settlement-scope-note { margin: -2px 0 0; color: #8a6d3b; font-size: 12px; line-height: 1.6; }
.preflight-summary { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 12px; }
.preflight-summary > div { display: grid; gap: 6px; padding: 15px 17px; border: 1px solid #e5e9f0; border-radius: 12px; background: #fff; box-shadow: 0 5px 16px rgba(31, 45, 61, .04); }
.preflight-summary small { color: #77808e; }
.preflight-summary strong { color: #303133; font-size: 24px; }
.preflight-summary .ready strong { color: #27966c; }
.preflight-summary .blocked { border-color: #f4c6c6; background: #fff7f7; }
.preflight-summary .blocked strong { color: #c45656; }
.blocked-card ::v-deep .el-card__header span, .day-table-heading span { color: #8492a6; font-size: 12px; font-weight: 400; }
.blocked-row { display: grid; grid-template-columns: minmax(190px, .55fr) 1fr; gap: 16px; padding: 9px 0; border-bottom: 1px solid #f0f2f5; }
.blocked-row:last-child { border-bottom: 0; }
.blocked-row span { color: #606266; }
.blocked-row strong, .exception-text { color: #c45656; font-weight: 600; }
.day-table-heading > div { display: flex; align-items: center; gap: 12px; }
.day-table-heading > span { max-width: 520px; line-height: 1.5; text-align: right; }
.remaining-work-body { min-height: 180px; margin-top: 16px; }
.remaining-work-person, .remaining-interval-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.remaining-work-person { padding: 0 2px 12px; color: #606266; }
.remaining-work-person span, .remaining-interval-heading span, .remaining-latest { color: #8492a6; font-size: 12px; }
.remaining-interval-card { margin-top: 10px; padding: 14px 16px; border: 1px solid #e5e9f0; border-radius: 10px; background: #fafbfc; }
.remaining-interval-heading > div { display: flex; align-items: center; gap: 10px; }
.remaining-latest { margin-top: 8px; line-height: 1.6; }
.remaining-confirm-form { margin-top: 18px; padding: 16px 18px 4px; border: 1px solid #cfe6df; border-radius: 12px; background: #f4fbf8; }
.remaining-confirm-form h4 { margin: 0 0 16px; color: #1f7a6d; }
.net-early-warning { color: #c45656; }
.time-credit-body { min-height: 180px; margin-top: 16px; }
.time-credit-target { display: grid; grid-template-columns: 1.5fr repeat(3, 1fr); gap: 10px; }
.time-credit-target > div { display: grid; gap: 5px; padding: 13px 15px; border: 1px solid #e5e9f0; border-radius: 10px; background: #fafbfc; }
.time-credit-target small { color: #8492a6; }
.time-credit-target strong { color: #303133; }
.time-credit-lock-alert { margin-top: 14px; }
.time-credit-form { margin-top: 16px; padding: 17px 18px 2px; border: 1px solid #cfe6df; border-radius: 12px; background: #f4fbf8; }
.form-tip { margin-left: 10px; color: #8492a6; font-size: 12px; }
.form-tip.warning { margin: 7px 0 0; color: #c45656; }
.time-credit-history { margin-top: 18px; }
.time-credit-history h4 { margin: 0 0 10px; }
.time-credit-history-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 12px 2px; border-top: 1px solid #ebeef5; }
.time-credit-history-row > div { display: grid; gap: 5px; }
.time-credit-history-row span { color: #8492a6; font-size: 12px; line-height: 1.5; }
@media (max-width: 1200px) { .preflight-summary { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) {
  .day-filter-heading, .day-table-heading { flex-direction: column; }
  .preflight-summary { grid-template-columns: 1fr 1fr; }
  .blocked-row { grid-template-columns: 1fr; gap: 5px; }
  .time-credit-target { grid-template-columns: 1fr 1fr; }
}
</style>
