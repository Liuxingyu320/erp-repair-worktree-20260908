<template>
  <div class="mobile-attendance mobile-system-page">
    <header class="attendance-header">
      <button type="button" aria-label="返回" @click="goBack"><i class="el-icon-arrow-left" /></button>
      <div>
        <small>员工考勤</small>
        <h1>现场打卡</h1>
      </div>
      <button type="button" aria-label="刷新" :disabled="loading || busy" @click="refreshActiveView"><i class="el-icon-refresh" /></button>
    </header>

    <nav class="attendance-tabs" aria-label="考勤功能">
      <button :class="{ active: activeView === 'today' }" type="button" @click="selectView('today')">今日</button>
      <button :class="{ active: activeView === 'records' }" type="button" @click="selectView('records')">我的记录</button>
      <button :class="{ active: activeView === 'leave' }" type="button" @click="selectView('leave')">请假</button>
      <button :class="{ active: activeView === 'correction' }" type="button" @click="selectView('correction')">补卡</button>
    </nav>

    <main class="attendance-content">
      <template v-if="activeView === 'today'">
        <section v-if="loading" class="state-card" role="status">
          <i class="el-icon-loading" /><strong>正在核对今日排班…</strong>
          <p>只有服务端确认可打卡后才会开放相机。</p>
        </section>

        <section v-else-if="loadError" class="state-card error" role="alert">
          <i class="el-icon-warning-outline" /><strong>无法获取今日考勤</strong>
          <p>{{ loadError }}</p>
          <button type="button" @click="loadToday">重试</button>
        </section>

        <template v-else>
          <section :class="['today-card', gate.ready ? 'ready' : 'locked']">
            <div class="today-card__status">
              <span><i :class="gate.ready ? 'el-icon-unlock' : 'el-icon-lock'" /></span>
              <div>
                <small>{{ context.serverTime || '服务器时间未返回' }}</small>
                <h2>{{ gate.ready ? punchTitle : '暂不能打卡' }}</h2>
                <p>{{ gate.ready ? '点击打卡后，浏览器会直接询问定位权限，通过后再开启现场相机。' : gate.reason }}</p>
              </div>
            </div>
            <span class="state-chip">{{ stateLabel }}</span>
          </section>

          <section v-if="context.schedule" class="schedule-card">
            <div class="section-heading">
              <div><small>已发布排班</small><h2>{{ scheduleName }}</h2></div>
              <strong>{{ scheduleTime }}</strong>
            </div>
            <dl>
              <div><dt>业务日</dt><dd>{{ scheduleValue(['businessDate', 'workDate'], '-') }}</dd></div>
              <div><dt>门店</dt><dd>{{ scheduleValue(['shopName', 'shopDeptName'], '-') }}</dd></div>
            </dl>
            <div v-if="segmentTimeline.length" class="segment-timeline" aria-label="今日分段班次打卡时间线">
              <div class="segment-timeline__heading">
                <strong>分段班次·每个工作段上下班都需打卡</strong>
                <span>共 {{ context.punchSlots.length }} 次</span>
              </div>
              <article
                v-for="segment in segmentTimeline"
                :key="segment.key"
                :class="['segment-row', { current: segment.current, upcoming: segment.upcoming, complete: segment.complete }]"
              >
                <header><strong>{{ segment.title }}</strong><span>{{ segment.complete ? '已完成' : (segment.current ? '当前工作段' : (segment.upcoming ? '下一工作段' : '待打卡')) }}</span></header>
                <div class="segment-row__slots">
                  <div v-for="slot in segment.slots" :key="slot.punchSlotKey || `${segment.key}-${slot.punchType}`">
                    <span>{{ punchSlotDisplayLabel(slot) }}</span>
                    <strong>{{ punchSlotStatusText(slot) }}</strong>
                  </div>
                </div>
              </article>
              <p v-if="restNotice" class="segment-break"><i class="el-icon-time" />{{ restNotice }}</p>
            </div>
          </section>

          <section v-if="context.latestPunch" class="latest-card">
            <small>最新打卡</small>
            <strong>{{ latestPunchLabel }}·{{ context.latestPunch.serverPunchTime || context.latestPunch.serverTime || context.latestPunch.punchTime || '-' }}</strong>
            <span v-if="context.latestPunch.resolvedAddress">可读定位地址已写入服务端水印证据</span>
          </section>

          <section v-if="gate.ready || pending.challengeToken || serverAccepted || punchOutcomeUnknown || punchStorageError" class="punch-card">
            <div class="step-list" aria-label="打卡步骤">
              <span :class="{ done: pending.challengeToken }">1<small>服务端凭证</small></span>
              <i />
              <span :class="{ done: pending.location }">2<small>精确定位</small></span>
              <i />
              <span :class="{ done: photo }">3<small>现场照片</small></span>
              <i />
              <span :class="{ done: serverAccepted }">4<small>服务端验证</small></span>
            </div>

            <div v-if="flowError" class="flow-message error" role="alert">{{ flowError }}</div>
            <div v-if="successMessage" class="flow-message success" role="status">{{ successMessage }}</div>
            <div v-if="punchOutcomeUnknown" class="flow-message warning" role="alert">
              <strong>上次打卡结果仍待核对</strong>
              <p>系统已冻结重复提交，只会使用原 requestId 和一次性凭证查询服务端终态。</p>
              <button type="button" :disabled="punchStatusChecking" @click="reconcilePendingPunch">
                {{ punchStatusChecking ? '正在核对…' : '核对打卡结果' }}
              </button>
            </div>
            <div v-else-if="punchStorageError" class="flow-message error" role="alert">
              <strong>无法安全追踪打卡结果</strong>
              <p>{{ punchStorageError }}。为避免重复打卡，本页已停止写入。</p>
            </div>

            <button
              v-if="gate.ready && !pending.challengeToken && !serverAccepted && !punchFrozen"
              class="primary-action"
              type="button"
              :disabled="busy"
              @click="preparePunch"
            >
              <i :class="busy ? 'el-icon-loading' : 'el-icon-location-outline'" />
              {{ busy ? '正在获取打卡凭证与定位…' : `开始${punchTitle}` }}
            </button>

            <div v-if="pending.location && !photo && !serverAccepted && !cameraOpen && !punchFrozen" class="location-ready">
              <i class="el-icon-location-information" />
              <div>
                <strong>现场定位已采集</strong>
                <p>提交后由服务端解析可读地址并生成水印，本页不展示坐标。</p>
              </div>
              <button type="button" @click="openCamera">打开相机</button>
            </div>

            <div v-if="cameraOpen && !serverAccepted" class="live-camera" role="dialog" aria-label="实时现场相机">
              <video ref="liveCameraVideo" autoplay muted playsinline @loadedmetadata="cameraReady = true"></video>
              <p>{{ cameraReady ? '实时画面已就绪，请确认现场后拍摄。' : '正在连接后置相机…' }}</p>
              <div class="live-camera__actions">
                <button type="button" :disabled="busy" @click="stopBrowserCamera">取消</button>
                <button class="capture" type="button" :disabled="busy || !cameraReady" @click="captureBrowserPhoto">拍摄现场照片</button>
              </div>
            </div>

            <div v-if="photo && !serverAccepted && !cameraOpen" class="photo-review">
              <img :src="photoPreviewUrl" alt="待提交的现场照片">
              <div>
                <strong>确认现场照片</strong>
                <p>{{ photo.name }}·{{ fileSizeText(photo.size) }}</p>
                <p>最终水印由服务端写入员工、公司到店铺的完整组织路径、班次名称、权威时间、打卡类型、可读地址和凭证编号，手机预览不作为打卡证据。</p>
                <div class="photo-actions">
                  <button type="button" :disabled="busy" @click="openCamera">重拍</button>
                  <button class="submit" type="button" :disabled="busy" @click="submitPunch">
                    {{ busy ? `正在提交 ${uploadProgress}%` : '提交打卡' }}
                  </button>
                </div>
              </div>
            </div>

          </section>

          <section
            v-if="evidenceId && !evidenceUrl"
            class="evidence-loading evidence-recovery-card"
            :role="evidenceLoadError ? 'alert' : 'status'"
          >
            <strong>{{ serverAccepted ? '打卡已由服务端受理' : '最新打卡证据暂未显示' }}</strong>
            <p v-if="evidenceLoadError">{{ evidenceLoadError }}</p>
            <p v-else>正在通过鉴权接口加载私有水印证据…</p>
            <button v-if="evidenceLoadError" type="button" :disabled="busy" @click="retryEvidence">重试加载水印证据</button>
          </section>

          <section v-if="evidenceUrl" class="evidence-card">
            <div class="section-heading"><div><small>服务端私有证据</small><h2>打卡成功</h2></div><i class="el-icon-circle-check" /></div>
            <button
              type="button"
              class="evidence-open"
              aria-label="全屏查看服务端水印打卡证据"
              @click="openEvidencePreview"
            >
              <img :src="evidenceUrl" alt="服务端生成的水印打卡证据" draggable="false">
              <span><i class="el-icon-zoom-in" /> 点击查看大图</span>
            </button>
            <p>水印中的员工、公司到店铺的完整组织路径、班次名称、时间、打卡类型、可读地址和凭证编号均由服务端生成；该图片通过鉴权接口读取。</p>
          </section>
        </template>
      </template>

      <section v-else-if="activeView === 'records'" class="records-card">
        <div class="section-heading"><div><small>服务端每日结果</small><h2>我的考勤记录</h2></div><strong>{{ recordRows.length }} 天</strong></div>
        <div class="record-filters">
          <label><span>开始日期</span><input v-model="recordQuery.dateFrom" type="date"></label>
          <label><span>结束日期</span><input v-model="recordQuery.dateTo" type="date"></label>
          <button type="button" :disabled="recordsLoading" @click="loadRecords">查询</button>
        </div>
        <div v-if="recordsError" class="flow-message error">{{ recordsError }}</div>
        <div v-if="recordsLoading" class="record-state"><i class="el-icon-loading" /> 正在加载服务端结果…</div>
        <div v-else-if="!recordRows.length" class="record-state">当前日期范围没有已生成的考勤结果。</div>
        <article v-for="row in recordRows" v-else :key="row.dayResultId || row.businessDate" class="record-row">
          <div><strong>{{ row.businessDate || '-' }}</strong><small>{{ minutesAsHours(row.workedMinutes) }} / 计划 {{ minutesAsHours(row.scheduledMinutes) }}</small></div>
          <span :class="['record-status', resultTone(row.resultStatus)]">{{ resultStatusLabel(row.resultStatus) }}</span>
          <dl>
            <div><dt>迟到</dt><dd>{{ row.lateMinutes || 0 }} 分</dd></div>
            <div><dt>早退</dt><dd>{{ row.earlyLeaveMinutes || 0 }} 分</dd></div>
            <div><dt>请假</dt><dd>{{ Number(row.paidLeaveMinutes || 0) + Number(row.unpaidLeaveMinutes || 0) }} 分</dd></div>
            <div><dt>缺勤</dt><dd>{{ row.absenceMinutes || 0 }} 分</dd></div>
          </dl>
        </article>
      </section>

      <mobile-attendance-leave
        v-else-if="activeView === 'leave'"
        ref="leavePanel"
        :todo-business-id="todoBusinessId"
      />
      <mobile-attendance-correction v-else-if="activeView === 'correction'" ref="correctionPanel" :todo-business-id="todoBusinessId" />
    </main>

    <transition name="evidence-preview-fade">
      <div
        v-if="evidencePreviewOpen && evidenceUrl"
        ref="evidencePreview"
        class="evidence-preview"
        role="dialog"
        aria-modal="true"
        aria-labelledby="attendance-evidence-preview-title"
        aria-describedby="attendance-evidence-preview-hint"
        tabindex="-1"
        @click.self="closeEvidencePreview"
        @keydown.esc.stop.prevent="closeEvidencePreview"
      >
        <header class="evidence-preview__header">
          <div>
            <strong id="attendance-evidence-preview-title">水印打卡证据</strong>
            <small id="attendance-evidence-preview-hint">可双指缩放查看细节</small>
          </div>
          <button type="button" aria-label="关闭水印证据大图" @click="closeEvidencePreview">
            <i class="el-icon-close" />
            <span>关闭</span>
          </button>
        </header>
        <div class="evidence-preview__body" @click.self="closeEvidencePreview">
          <img :src="evidenceUrl" alt="服务端生成的水印打卡证据大图" draggable="false">
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import {
  createPunchChallenge,
  getAttendancePunchStatus,
  getPunchEvidenceContent,
  getTodayAttendanceContext,
  listMyAttendanceDayResults,
  submitAttendancePunch
} from '@/api/oa/attendanceV2'
import { Camera, CameraDirection, CameraSource, EncodingType } from '@capacitor/camera'
import { Capacitor } from '@capacitor/core'
import { Geolocation } from '@capacitor/geolocation'
import { getSelectedDeptContext } from '@/utils/shopContext'
import MobileAttendanceLeave from './MobileAttendanceLeave.vue'
import MobileAttendanceCorrection from './MobileAttendanceCorrection.vue'

const {
  actionSegmentLabelOf,
  attendanceErrorText,
  dataOf,
  isSegmentPunchContext,
  normalizeTodayContext,
  punchSlotLabel,
  punchTimeText,
  restMessage,
  resolvePunchGate,
  segmentLabelOf,
  validateLocation,
  validatePhoto
} = require('./attendancePunchPolicy')
const {
  clearAttendancePunchAttempt,
  readAttendancePunchAttempt,
  reconcileAttendancePunch,
  reconcileAttendancePunchStatus,
  runAttendancePunch
} = require('./mobileAttendancePunchAttempt')
const {
  captureLiveFrame,
  liveCameraConstraints,
  requireEnvironmentStream,
  requireLiveCamera,
  stopMediaStream
} = require('./mobileAttendanceLiveCamera')

const emptyContext = () => normalizeTodayContext({})

export default {
  name: 'MobileAttendanceV2',
  components: { MobileAttendanceCorrection, MobileAttendanceLeave },
  data() {
    return {
      activeView: 'today',
      loading: false,
      busy: false,
      loadError: '',
      flowError: '',
      successMessage: '',
      uploadProgress: 0,
      context: emptyContext(),
      pending: { challengeToken: '', expiresAt: '', scheduleId: null, punchType: '', punchSlotKey: '', segmentOrder: null, segmentLabel: '', location: null },
      pendingOwner: null,
      pendingContextEpoch: null,
      photo: null,
      photoCapturedAt: '',
      photoPreviewUrl: '',
      cameraOpen: false,
      cameraReady: false,
      cameraStream: null,
      cameraVisibilityHandler: null,
      deptChangeHandler: null,
      attendanceContextEpoch: 0,
      evidenceId: null,
      evidenceUrl: '',
      evidenceLoadError: '',
      evidencePreviewOpen: false,
      evidencePreviewScrollY: 0,
      evidencePreviewBodyStyle: null,
      serverAccepted: false,
      acceptedPunchContext: null,
      punchAttemptState: null,
      punchStatusChecking: false,
      recordsLoading: false,
      recordsError: '',
      recordRows: [],
      recordQuery: this.defaultRecordQuery()
    }
  },
  computed: {
    gate() {
      return resolvePunchGate(this.context)
    },
    punchTitle() {
      const typeLabel = this.gate.punchType === 'OUT' ? '下班打卡' : '上班打卡'
      if (!isSegmentPunchContext(this.context) || !this.gate.punchSlotKey) return typeLabel
      return `${actionSegmentLabelOf(this.context.nextPunchSlot)}${typeLabel}`
    },
    latestPunchLabel() {
      const latest = this.context.latestPunch || {}
      const type = this.punchTypeLabel(latest.punchType)
      if (!isSegmentPunchContext(this.context) || (!latest.punchSlotKey && !latest.segmentOrder && !latest.segmentLabel)) return type
      return `${actionSegmentLabelOf(latest)}${type}`
    },
    restNotice() {
      return restMessage(this.context)
    },
    segmentTimeline() {
      if (!isSegmentPunchContext(this.context)) return []
      const grouped = new Map()
      ;(this.context.punchSlots || []).forEach(slot => {
        const key = Number(slot.segmentOrder) || slot.segmentLabel || slot.punchSlotKey
        if (!grouped.has(key)) grouped.set(key, [])
        grouped.get(key).push(slot)
      })
      return Array.from(grouped.entries()).map(([key, slots]) => {
        const sorted = slots.slice().sort((left, right) => left.punchType === right.punchType ? 0 : (left.punchType === 'IN' ? -1 : 1))
        const next = sorted.some(slot => slot.punchSlotKey && slot.punchSlotKey === (this.context.nextPunchSlot && this.context.nextPunchSlot.punchSlotKey))
        const current = next && this.gate.ready
        const upcoming = next && !this.gate.ready
        const complete = sorted.length > 0 && sorted.every(slot => this.punchSlotCompleted(slot))
        const rawLabel = segmentLabelOf(sorted[0])
        const segmentName = /工作段$/.test(rawLabel) ? rawLabel : `${rawLabel}工作段`
        const startsAt = punchTimeText(sorted[0] && sorted[0].startAt)
        const endsAt = punchTimeText(sorted[sorted.length - 1] && sorted[sorted.length - 1].endAt)
        const title = startsAt && endsAt ? `${segmentName}·${startsAt}-${endsAt}` : segmentName
        return { key, order: Number(sorted[0] && sorted[0].segmentOrder) || Number.MAX_SAFE_INTEGER, title, slots: sorted, current, upcoming, complete }
      }).sort((left, right) => left.order - right.order)
    },
    stateLabel() {
      if (this.restNotice) return '休息中'
      const labels = {
        READY_IN: '可上班打卡', READY_OUT: '可下班打卡', COMPLETED: '已完成',
        NO_SCHEDULE: '无排班', LOCKED_NO_SCHEDULE: '无排班', OUTSIDE_WINDOW: '窗口外',
        LOCKED_OUTSIDE_WINDOW: '窗口外', BETWEEN_SEGMENTS: '休息中', LOCKED_BETWEEN_SEGMENTS: '休息中', ON_BREAK: '休息中', RESTING: '休息中', ON_LEAVE: '已请假'
      }
      return labels[this.context.state] || (this.gate.ready ? '可打卡' : '已锁定')
    },
    scheduleName() {
      return this.scheduleValue(['shiftNameSnapshot', 'shiftName', 'name'], '今日班次')
    },
    scheduleTime() {
      const start = String(this.scheduleValue(['startTimeSnapshot', 'startTime'], '--:--')).slice(0, 5)
      const end = String(this.scheduleValue(['endTimeSnapshot', 'endTime'], '--:--')).slice(0, 5)
      return `${start} – ${end}`
    },
    todoBusinessId() {
      return this.$route && this.$route.query ? this.$route.query.businessId : ''
    },
    punchOutcomeUnknown() {
      return this.punchAttemptState && this.punchAttemptState.status === 'pending'
    },
    punchStorageError() {
      return this.punchAttemptState && this.punchAttemptState.status === 'error'
        ? this.punchAttemptState.error
        : ''
    },
    punchFrozen() {
      return Boolean(this.punchOutcomeUnknown || this.punchStorageError)
    }
  },
  watch: {
    '$route.query': {
      deep: true,
      handler() { this.applyRouteView() }
    }
  },
  created() {
    this.applyRouteView()
    if (this.activeView === 'today') this.loadToday()
    if (this.activeView === 'records') this.loadRecords()
  },
  mounted() {
    if (typeof document === 'undefined' || typeof document.addEventListener !== 'function') return
    this.cameraVisibilityHandler = () => {
      if (document.visibilityState === 'hidden') this.stopBrowserCamera()
    }
    document.addEventListener('visibilitychange', this.cameraVisibilityHandler)
    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      this.deptChangeHandler = () => this.handleAttendanceDeptChanged()
      window.addEventListener('erp:dept-changed', this.deptChangeHandler)
    }
  },
  beforeDestroy() {
    this.nextAttendanceContextEpoch()
    if (typeof document !== 'undefined' && this.cameraVisibilityHandler) {
      document.removeEventListener('visibilitychange', this.cameraVisibilityHandler)
    }
    if (typeof window !== 'undefined' && this.deptChangeHandler) {
      window.removeEventListener('erp:dept-changed', this.deptChangeHandler)
    }
    this.stopBrowserCamera()
    this.closeEvidencePreview()
    this.revokeUrl('photoPreviewUrl')
    this.revokeUrl('evidenceUrl')
  },
  methods: {
    applyRouteView() {
      const query = this.$route && this.$route.query ? this.$route.query : {}
      const todoTabs = {
        OA_ATTENDANCE_LEAVE_APPROVAL: 'leave',
        OA_ATTENDANCE_CORRECTION_APPROVAL: 'correction'
      }
      const requested = String(query.tab || todoTabs[query.todoType] || 'today')
      this.activeView = ['today', 'records', 'leave', 'correction'].indexOf(requested) > -1 ? requested : 'today'
      if (this.activeView !== 'today') this.stopBrowserCamera()
    },
    selectView(view) {
      if (this.activeView === 'today' && view !== 'today') {
        this.nextAttendanceContextEpoch()
        this.resetPendingCapture({ keepError: true })
        this.closeEvidencePreview()
      }
      this.activeView = view
      if (view === 'today' && !this.loading) this.loadToday()
      if (view === 'records' && !this.recordRows.length) this.loadRecords()
      if (!this.$router || !this.$route) return
      const query = Object.assign({}, this.$route.query || {}, { tab: view })
      delete query.todoType
      delete query.businessId
      this.$router.replace({ path: this.$route.path, query }).catch(() => undefined)
    },
    refreshActiveView() {
      if (this.activeView === 'records') return this.loadRecords()
      if (this.activeView === 'leave' && this.$refs.leavePanel) return this.$refs.leavePanel.loadRows()
      if (this.activeView === 'correction' && this.$refs.correctionPanel) return this.$refs.correctionPanel.loadRows()
      return this.loadToday()
    },
    punchOwner() {
      const context = getSelectedDeptContext() || {}
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return { userId: Number(getters.id), orgId: Number(context.deptId) }
    },
    samePunchOwner(owner) {
      const current = this.punchOwner()
      return Boolean(owner && Number(owner.userId) === current.userId && Number(owner.orgId) === current.orgId)
    },
    currentAttendanceContextEpoch() {
      const current = Number(this.attendanceContextEpoch)
      if (Number.isSafeInteger(current) && current >= 0) return current
      this.attendanceContextEpoch = 0
      return 0
    },
    nextAttendanceContextEpoch() {
      const current = this.currentAttendanceContextEpoch()
      this.attendanceContextEpoch = current >= Number.MAX_SAFE_INTEGER ? 1 : current + 1
      // Advancing the generation explicitly retires all UI work owned by the
      // previous generation. Its guarded finally handlers must not mutate the
      // new context, so release their transient locks here instead.
      this.loading = false
      this.busy = false
      this.recordsLoading = false
      this.punchStatusChecking = false
      return this.attendanceContextEpoch
    },
    attendanceContextValid(owner, epoch) {
      return this.samePunchOwner(owner) && (epoch === undefined || epoch === this.currentAttendanceContextEpoch())
    },
    handleAttendanceDeptChanged() {
      this.nextAttendanceContextEpoch()
      this.stopBrowserCamera()
      this.clearPhoto()
      this.pending = { challengeToken: '', expiresAt: '', scheduleId: null, punchType: '', punchSlotKey: '', segmentOrder: null, segmentLabel: '', location: null }
      this.pendingOwner = null
      this.pendingContextEpoch = null
      this.busy = false
      this.punchStatusChecking = false
      this.serverAccepted = false
      this.acceptedPunchContext = null
      this.successMessage = ''
      this.flowError = ''
      this.context = emptyContext()
      this.revokeUrl('evidenceUrl')
      this.evidenceId = null
      this.evidenceLoadError = ''
      this.recordRows = []
      this.recordsError = ''
      this.recordsLoading = false
      this.punchAttemptState = readAttendancePunchAttempt(this.punchOwner())
      if (this.activeView === 'today') this.loadToday()
      if (this.activeView === 'records') this.loadRecords()
      if (this.activeView === 'leave' && this.$refs.leavePanel && typeof this.$refs.leavePanel.loadRows === 'function') this.$refs.leavePanel.loadRows()
      if (this.activeView === 'correction' && this.$refs.correctionPanel && typeof this.$refs.correctionPanel.loadRows === 'function') this.$refs.correctionPanel.loadRows()
    },
    applySettledPunchAttempt(result, owner) {
      if (!result || result.status !== 'settled' || !result.value) return false
      const attemptOwner = owner || { userId: result.value.userId, orgId: result.value.orgId }
      const accepted = result.value.outcome === 'accepted'
      this.serverAccepted = accepted
      this.acceptedPunchContext = accepted ? {
        scheduleId: result.value.scheduleId,
        punchType: result.value.punchType,
        punchSlotKey: result.value.punchSlotKey
      } : null
      this.resetPendingCapture({ keepError: true })
      if (accepted) {
        this.flowError = ''
        this.successMessage = '已按原 requestId 核对到服务端打卡记录。'
        const payload = result.response || {}
        if (payload.evidenceId) {
          this.evidenceId = payload.evidenceId
          this.evidenceLoadError = ''
        }
      } else {
        this.successMessage = ''
        this.flowError = '服务端已确认上次请求未受理，请重新定位并拍摄现场照片。'
      }
      if (!clearAttendancePunchAttempt(attemptOwner, { expectedRequestId: result.value.requestId })) {
        this.punchAttemptState = { status: 'error', error: '打卡终态已确认，但本地追踪记录无法清除' }
        return accepted
      }
      this.punchAttemptState = readAttendancePunchAttempt(attemptOwner)
      return accepted
    },
    loadToday() {
      const owner = this.punchOwner()
      const epoch = this.nextAttendanceContextEpoch()
      this.loading = true
      this.loadError = ''
      this.punchAttemptState = readAttendancePunchAttempt(owner)
      return getTodayAttendanceContext().then(response => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        this.context = normalizeTodayContext(response)
        const reconciliation = reconcileAttendancePunch(this.context, owner)
        this.punchAttemptState = reconciliation
        this.applySettledPunchAttempt(reconciliation, owner)
        if (this.context.canPunch && this.punchAttemptState.status === 'absent' &&
          !this.samePunchTarget(this.acceptedPunchContext)) {
          this.serverAccepted = false
          this.acceptedPunchContext = null
        }
        if (!this.context.canPunch && !this.serverAccepted && !this.punchOutcomeUnknown) this.resetPendingCapture()
        return this.restoreLatestEvidence(this.context.latestEvidenceId, { owner, epoch })
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        this.context = emptyContext()
        this.loadError = attendanceErrorText(error, '网络或考勤服务暂不可用')
        return false
      }).finally(() => {
        if (epoch === this.attendanceContextEpoch) this.loading = false
      })
    },
    defaultRecordQuery() {
      const to = new Date()
      const from = new Date(to)
      from.setDate(from.getDate() - 30)
      const format = value => [value.getFullYear(), String(value.getMonth() + 1).padStart(2, '0'), String(value.getDate()).padStart(2, '0')].join('-')
      return { dateFrom: format(from), dateTo: format(to) }
    },
    loadRecords() {
      if (this.recordsLoading) return Promise.resolve()
      if (!this.recordQuery.dateFrom || !this.recordQuery.dateTo || this.recordQuery.dateFrom > this.recordQuery.dateTo) {
        this.recordsError = '请选择有效的日期范围'
        return Promise.resolve()
      }
      const owner = this.punchOwner()
      const epoch = this.currentAttendanceContextEpoch()
      this.recordsLoading = true
      this.recordsError = ''
      return listMyAttendanceDayResults(this.recordQuery).then(response => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        const payload = dataOf(response)
        this.recordRows = Array.isArray(payload) ? payload : (payload && Array.isArray(payload.rows) ? payload.rows : [])
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        this.recordRows = []
        this.recordsError = attendanceErrorText(error, '考勤记录加载失败')
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.recordsLoading = false
      })
    },
    preparePunch() {
      if (!this.gate.ready || this.busy || this.punchFrozen) return
      const owner = this.punchOwner()
      const epoch = this.currentAttendanceContextEpoch()
      this.resetPendingCapture()
      this.pendingOwner = owner
      this.pendingContextEpoch = epoch
      this.flowError = ''
      this.successMessage = ''
      this.busy = true
      createPunchChallenge({ scheduleId: this.gate.scheduleId, punchType: this.gate.punchType, punchSlotKey: this.gate.punchSlotKey || undefined }).then(response => {
        if (!this.attendanceContextValid(owner, epoch)) throw new Error('身份或门店已切换，本次未开始打卡。')
        const challenge = dataOf(response) || {}
        if (!challenge.challengeToken) throw new Error('服务端未返回一次性打卡凭证')
        if (String(challenge.punchType || this.gate.punchType).toUpperCase() !== this.gate.punchType) {
          throw new Error('服务端打卡类型已变化，请刷新后重试')
        }
        if (this.gate.punchSlotKey && challenge.punchSlotKey && String(challenge.punchSlotKey) !== String(this.gate.punchSlotKey)) {
          throw new Error('服务端打卡工作段已变化，请刷新后重试')
        }
        this.pending.challengeToken = challenge.challengeToken
        this.pending.expiresAt = challenge.expiresAt || ''
        this.pending.scheduleId = challenge.scheduleId || this.gate.scheduleId
        this.pending.punchType = String(challenge.punchType || this.gate.punchType).toUpperCase()
        this.pending.punchSlotKey = String(challenge.punchSlotKey || this.gate.punchSlotKey || '')
        this.pending.segmentOrder = challenge.segmentOrder || this.gate.segmentOrder || null
        this.pending.segmentLabel = challenge.segmentLabel || this.gate.segmentLabel || ''
        return this.getHighAccuracyPosition()
      }).then(position => {
        if (!this.attendanceContextValid(owner, epoch)) throw new Error('身份或门店已切换，本次未开始打卡。')
        const decision = validateLocation(position)
        if (!decision.ok) throw new Error(decision.reason)
        this.pending.location = decision
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) return
        this.flowError = this.locationErrorText(error)
        this.resetPendingCapture({ keepError: true })
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.busy = false
      })
    },
    getHighAccuracyPosition() {
      if (Capacitor.isNativePlatform()) {
        return Geolocation.checkPermissions().then(status => {
          if (status && status.location === 'granted') return status
          return Geolocation.requestPermissions({ permissions: ['location'] })
        }).then(status => {
          if (!status || status.location !== 'granted') {
            throw new Error('定位权限被拒绝，本次不能打卡。请在系统设置中允许精确位置后重试。')
          }
          return Geolocation.getCurrentPosition({
            enableHighAccuracy: true,
            timeout: 15000,
            maximumAge: 0
          })
        })
      }
      return new Promise((resolve, reject) => {
        if (typeof window !== 'undefined' && window.isSecureContext === false) {
          const error = new Error('手机浏览器只能在 HTTPS 页面弹出定位授权，请使用本地 HTTPS 测试地址后重试。')
          error.code = 'BROWSER_LOCATION_REQUIRES_HTTPS'
          reject(error)
          return
        }
        if (typeof navigator === 'undefined' || !navigator.geolocation) {
          reject(new Error('当前浏览器不支持网页定位，请更换 Safari 或已授权的手机浏览器。'))
          return
        }
        // 必须保持在用户点击打卡后直接调用，由浏览器显示原生定位授权询问。
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 15000,
          maximumAge: 0
        })
      })
    },
    locationErrorText(error) {
      if (error && error.code === 'BROWSER_LOCATION_REQUIRES_HTTPS') return error.message
      if (error && error.code === 1 && !Capacitor.isNativePlatform()) {
        return 'Safari 未授予定位权限。请在当前网站的“网站设置”中把“位置”改为“询问”，然后重新点击打卡。'
      }
      if (error && error.code === 1) return '定位权限被拒绝，本次不能打卡。请在系统设置中允许精确位置后重试。'
      if (error && error.code === 2) return '暂时无法获取位置，请移动到空旷处或检查 GPS 后重试。'
      if (error && error.code === 3) return '定位超时，本次未打卡。'
      return error && error.message ? error.message : '定位失败，本次未打卡。'
    },
    openCamera() {
      if (!this.pending.location || !this.pending.challengeToken || this.serverAccepted || this.punchFrozen ||
        !this.attendanceContextValid(this.pendingOwner, this.pendingContextEpoch)) return
      if (this.challengeExpired()) {
        this.flowError = '一次性打卡凭证已过期，请重新开始打卡。'
        this.resetPendingCapture({ keepError: true })
        return
      }
      if (Capacitor.isNativePlatform()) {
        this.openNativeCamera()
        return
      }
      this.startBrowserCamera()
    },
    startBrowserCamera() {
      if (this.busy || this.cameraOpen || this.punchFrozen) return Promise.resolve()
      const owner = this.pendingOwner
      const epoch = this.pendingContextEpoch
      if (!this.attendanceContextValid(owner, epoch)) return Promise.resolve()
      this.busy = true
      this.flowError = ''
      let mediaDevices
      try {
        mediaDevices = requireLiveCamera(
          typeof navigator !== 'undefined' ? navigator.mediaDevices : null,
          typeof window === 'undefined' ? false : window.isSecureContext
        )
      } catch (error) {
        this.flowError = error.message || '无法启用实时相机，本次未打卡。'
        this.busy = false
        return Promise.resolve()
      }
      return mediaDevices.getUserMedia(liveCameraConstraints()).then(stream => {
        if (!this.attendanceContextValid(owner, epoch)) {
          stopMediaStream(stream)
          throw new Error('ATTENDANCE_CONTEXT_CHANGED')
        }
        requireEnvironmentStream(stream)
        this.cameraStream = stream
        this.cameraReady = false
        this.cameraOpen = true
        return this.$nextTick().then(() => {
          const video = this.$refs.liveCameraVideo
          if (!video) throw new Error('实时相机画面初始化失败')
          video.srcObject = stream
          const playback = typeof video.play === 'function' ? video.play() : null
          return playback && typeof playback.then === 'function' ? playback : undefined
        })
      }).catch(error => {
        this.stopBrowserCamera()
        if (!this.attendanceContextValid(owner, epoch)) return
        const signal = String(error && (error.message || error.name) || '')
        if (/NotAllowed|PermissionDenied/i.test(signal)) {
          this.flowError = '相机权限被拒绝，本次不能打卡。请在当前网站设置中允许相机后重试。'
        } else if (/Overconstrained|NotFound|DevicesNotFound/i.test(signal)) {
          this.flowError = '未检测到可确认的后置实时相机，本次不能打卡。'
        } else {
          this.flowError = signal || '无法打开实时相机，本次未打卡。'
        }
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.busy = false
      })
    },
    captureBrowserPhoto() {
      if (this.busy || !this.cameraOpen || !this.cameraReady || this.punchFrozen) return Promise.resolve()
      const owner = this.pendingOwner
      const epoch = this.pendingContextEpoch
      if (!this.attendanceContextValid(owner, epoch)) return Promise.resolve()
      if (this.challengeExpired()) {
        this.flowError = '拍照前打卡凭证已过期，请重新开始。'
        this.resetPendingCapture({ keepError: true })
        return Promise.resolve()
      }
      this.busy = true
      this.flowError = ''
      return captureLiveFrame(this.$refs.liveCameraVideo).then(file => {
        if (!this.attendanceContextValid(owner, epoch)) {
          this.stopBrowserCamera()
          return false
        }
        this.stopBrowserCamera()
        this.acceptPhoto(file)
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) return
        this.flowError = error && error.message ? error.message : '现场照片拍摄失败，请重试。'
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.busy = false
      })
    },
    stopBrowserCamera() {
      const video = this.$refs && this.$refs.liveCameraVideo
      if (video && video.srcObject) video.srcObject = null
      stopMediaStream(this.cameraStream)
      this.cameraStream = null
      this.cameraOpen = false
      this.cameraReady = false
    },
    openNativeCamera() {
      if (this.busy) return Promise.resolve()
      const owner = this.pendingOwner
      const epoch = this.pendingContextEpoch
      if (!this.attendanceContextValid(owner, epoch)) return Promise.resolve()
      this.busy = true
      this.flowError = ''
      return Camera.checkPermissions().then(status => {
        if (!this.attendanceContextValid(owner, epoch)) throw new Error('ATTENDANCE_CONTEXT_CHANGED')
        if (status && status.camera === 'granted') return status
        return Camera.requestPermissions({ permissions: ['camera'] })
      }).then(status => {
        if (!this.attendanceContextValid(owner, epoch)) throw new Error('ATTENDANCE_CONTEXT_CHANGED')
        if (!status || status.camera !== 'granted') {
          throw new Error('相机权限被拒绝，本次不能打卡。请在系统设置中允许相机后重试。')
        }
        return Camera.takePhoto({
          quality: 82,
          targetWidth: 1920,
          targetHeight: 1920,
          correctOrientation: true,
          encodingType: EncodingType.JPEG,
          saveToGallery: false,
          cameraDirection: CameraDirection.Rear,
          source: CameraSource.Camera,
          editable: 'no',
          includeMetadata: true
        })
      }).then(photo => {
        if (!this.attendanceContextValid(owner, epoch)) throw new Error('ATTENDANCE_CONTEXT_CHANGED')
        return this.nativePhotoFile(photo)
      })
        .then(file => {
          if (!this.attendanceContextValid(owner, epoch)) return false
          this.acceptPhoto(file)
          return true
        })
        .catch(error => {
          if (!this.attendanceContextValid(owner, epoch)) return
          const signal = String(error && (error.message || error.code) || '')
          if (!/cancel|cancelled|canceled/i.test(signal)) {
            this.flowError = signal || '无法打开相机，本次未打卡。'
          }
        }).finally(() => {
          if (this.attendanceContextValid(owner, epoch)) this.busy = false
        })
    },
    nativePhotoFile(photo) {
      if (!photo || !photo.webPath || typeof fetch !== 'function') {
        return Promise.reject(new Error('原生相机未返回可读取的现场照片'))
      }
      return fetch(photo.webPath).then(response => response.blob()).then(blob => {
        if (!blob || blob.size <= 0) throw new Error('原生相机返回了空照片，请重新拍摄')
        const fileName = `attendance-${Date.now()}.jpg`
        return new File([blob], fileName, { type: 'image/jpeg', lastModified: Date.now() })
      })
    },
    acceptPhoto(file) {
      const decision = validatePhoto(file)
      if (!decision.ok) {
        this.flowError = decision.reason
        this.clearPhoto()
        return
      }
      if (this.challengeExpired()) {
        this.flowError = '拍照前打卡凭证已过期，请重新开始。'
        this.resetPendingCapture({ keepError: true })
        return
      }
      const capturedAt = Number(file && file.lastModified)
      if (!Number.isFinite(capturedAt) || capturedAt <= 0 || Math.abs(Date.now() - capturedAt) > 60 * 1000) {
        this.flowError = '无法确认照片由当前实时相机刚刚拍摄，本次不能打卡。'
        this.clearPhoto()
        return
      }
      this.clearPhoto()
      this.photo = file
      this.photoCapturedAt = this.localDateTime(new Date(capturedAt))
      this.photoPreviewUrl = URL.createObjectURL(file)
      this.flowError = ''
    },
    submitPunch() {
      if (this.busy || this.serverAccepted || this.punchFrozen) return
      const owner = this.pendingOwner
      const epoch = this.pendingContextEpoch
      if (!this.attendanceContextValid(owner, epoch)) {
        this.flowError = '身份或门店已切换，旧上下文中的定位和照片已清除。'
        this.resetPendingCapture({ keepError: true })
        return
      }
      const photoDecision = validatePhoto(this.photo)
      if (!photoDecision.ok) {
        this.flowError = photoDecision.reason
        return
      }
      if (!this.pending.location || !this.pending.challengeToken || this.challengeExpired()) {
        this.flowError = '打卡凭证或定位已失效，请重新开始。'
        this.resetPendingCapture({ keepError: true })
        return
      }
      const location = this.pending.location
      this.busy = true
      this.flowError = ''
      this.uploadProgress = 0
      return runAttendancePunch({
        owner,
        scheduleId: this.pending.scheduleId,
        punchType: this.pending.punchType,
        punchSlotKey: this.pending.punchSlotKey,
        challengeToken: this.pending.challengeToken,
        challengeExpiresAt: this.pending.expiresAt
      }, {
        isCurrent: () => this.attendanceContextValid(owner, epoch),
        execute: attempt => submitAttendancePunch({
          challengeToken: attempt.challengeToken,
          punchType: attempt.punchType,
          punchSlotKey: attempt.punchSlotKey || undefined,
          latitude: location.latitude,
          longitude: location.longitude,
          accuracyMeters: location.accuracyMeters,
          clientCoordinateSystem: 'WGS84',
          photo: this.photo,
          clientCaptureTime: this.photoCapturedAt,
          clientRequestId: attempt.requestId,
          appVersion: process.env.VUE_APP_VERSION || 'web'
        }, progress => {
          if (!this.attendanceContextValid(owner, epoch)) return
          if (!progress || !progress.total) return
          this.uploadProgress = Math.min(99, Math.round(progress.loaded * 100 / progress.total))
        }).then(response => dataOf(response) || {}),
        accepted: (result, attempt) => result.success === true && Boolean(result.evidenceId) &&
          Boolean(result.event && String(result.event.resolvedAddress || '').trim()) &&
          String(result.event.clientRequestId || '') === attempt.requestId &&
          Number(result.event.scheduleId) === attempt.scheduleId &&
          String(result.event.punchType || '').toUpperCase() === attempt.punchType &&
          String(result.event.punchSlotKey || '') === attempt.punchSlotKey
      }).then(outcome => {
        if (!this.attendanceContextValid(owner, epoch)) {
          this.resetPendingCapture({ keepError: true })
          this.serverAccepted = false
          this.punchAttemptState = readAttendancePunchAttempt(this.punchOwner())
          this.flowError = '打卡期间身份或门店已切换；旧请求结果未在当前门店展示，请切回原门店核对。'
          return false
        }
        this.punchAttemptState = readAttendancePunchAttempt(owner)
        if (outcome.status === 'blocked') {
          this.flowError = outcome.message || '无法安全追踪打卡结果，本次未提交。'
          return false
        }
        if (outcome.status === 'not-accepted') {
          this.applySettledPunchAttempt({ status: 'settled', value: outcome.attempt }, owner)
          this.flowError = this.punchErrorText(outcome.error)
          return false
        }
        if (outcome.status !== 'accepted') {
          this.resetPendingCapture({ keepError: true })
          this.punchAttemptState = readAttendancePunchAttempt(owner)
          this.flowError = outcome.message || '打卡结果暂时无法确认，已冻结重复提交。'
          return false
        }
        const result = outcome.response || {}
        this.serverAccepted = true
        this.evidenceId = result.evidenceId
        this.evidenceLoadError = ''
        this.uploadProgress = 100
        return this.loadEvidence(result.evidenceId, { owner, epoch }).then(() => {
          if (!this.attendanceContextValid(owner, epoch)) return false
          this.successMessage = `${this.punchTitle}成功，服务端水印证据已生成。`
          this.clearPhoto()
          this.applySettledPunchAttempt({ status: 'settled', value: outcome.attempt, response: result }, owner)
          return this.loadToday()
        })
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) {
          this.resetPendingCapture({ keepError: true })
          this.serverAccepted = false
          this.punchAttemptState = readAttendancePunchAttempt(this.punchOwner())
          this.flowError = '打卡期间身份或门店已切换；旧请求结果未在当前门店展示，请切回原门店核对。'
          return false
        }
        this.resetPendingCapture({ keepError: true })
        this.punchAttemptState = readAttendancePunchAttempt(owner)
        this.flowError = this.punchErrorText(error)
        if (this.serverAccepted) this.evidenceLoadError = this.flowError
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.busy = false
      })
    },
    reconcilePendingPunch() {
      if (this.punchStatusChecking || !this.punchOutcomeUnknown) return Promise.resolve()
      const attempt = this.punchAttemptState.value
      const owner = { userId: attempt.userId, orgId: attempt.orgId }
      const epoch = this.currentAttendanceContextEpoch()
      if (!this.attendanceContextValid(owner, epoch)) {
        this.punchAttemptState = readAttendancePunchAttempt(this.punchOwner())
        this.flowError = '身份或门店已切换，未查询旧上下文中的打卡请求。'
        return Promise.resolve()
      }
      if (attempt.schema === 1 || !attempt.challengeToken) {
        this.flowError = '检测到升级前的打卡追踪，正在通过今日服务端记录核对；系统不会重复提交。'
        return this.loadToday()
      }
      this.punchStatusChecking = true
      this.flowError = ''
      return getAttendancePunchStatus({
        clientRequestId: attempt.requestId,
        challengeToken: attempt.challengeToken
      }).then(response => {
        const reconciliation = reconcileAttendancePunchStatus(dataOf(response), owner)
        if (!this.attendanceContextValid(owner, epoch)) {
          this.punchAttemptState = readAttendancePunchAttempt(this.punchOwner())
          this.flowError = '核对期间身份或门店已切换，旧结果未在当前门店展示。'
          return false
        }
        this.punchAttemptState = reconciliation
        if (reconciliation.status === 'settled') {
          const accepted = this.applySettledPunchAttempt(reconciliation, owner)
          if (accepted && reconciliation.response && reconciliation.response.evidenceId) {
            return this.restoreLatestEvidence(reconciliation.response.evidenceId, { owner, epoch }).then(() => {
              if (!this.attendanceContextValid(owner, epoch)) return false
              this.punchStatusChecking = false
              return this.loadToday()
            })
          }
          if (accepted) {
            this.punchStatusChecking = false
            return this.loadToday()
          }
          return false
        }
        if (reconciliation.status === 'error') {
          this.flowError = reconciliation.error || '无法安全核对打卡结果，仍保持冻结。'
        } else {
          this.flowError = '服务端尚未产生终态，请稍后继续核对；系统不会重复提交。'
        }
        return false
      }).catch(error => {
        const stillCurrent = this.attendanceContextValid(owner, epoch)
        const currentOwner = stillCurrent ? owner : this.punchOwner()
        this.punchAttemptState = readAttendancePunchAttempt(currentOwner)
        this.flowError = stillCurrent
          ? attendanceErrorText(error, '打卡终态查询失败，仍保持冻结。')
          : '核对期间身份或门店已切换，旧结果未在当前门店展示。'
      }).finally(() => {
        if (this.attendanceContextValid(owner, epoch)) this.punchStatusChecking = false
      })
    },
    restoreLatestEvidence(latestEvidenceId, options) {
      const settings = options || {}
      if (settings.owner && !this.attendanceContextValid(settings.owner, settings.epoch)) return Promise.resolve(false)
      if (latestEvidenceId === undefined || latestEvidenceId === null || String(latestEvidenceId).trim() === '') {
        this.revokeUrl('evidenceUrl')
        this.evidenceId = null
        this.evidenceLoadError = ''
        return Promise.resolve(false)
      }
      const sameEvidence = this.evidenceId !== undefined && this.evidenceId !== null &&
        String(this.evidenceId) === String(latestEvidenceId)
      if (sameEvidence && this.evidenceUrl) {
        this.evidenceLoadError = ''
        return Promise.resolve(false)
      }
      if (!sameEvidence) {
        this.revokeUrl('evidenceUrl')
        this.evidenceId = latestEvidenceId
      }
      this.evidenceLoadError = ''
      return this.loadEvidence(latestEvidenceId, { preserveFlowError: true, owner: settings.owner, epoch: settings.epoch }).then(() => true).catch(() => {
        if (settings.owner && !this.attendanceContextValid(settings.owner, settings.epoch)) return false
        this.evidenceLoadError = '最新打卡证据加载失败，请点击重试。'
        return false
      })
    },
    loadEvidence(evidenceId, options) {
      if (!evidenceId) return Promise.reject(new Error('缺少水印证据 ID'))
      const settings = options || {}
      this.busy = true
      return getPunchEvidenceContent(evidenceId).then(blob => {
        if (settings.owner && !this.attendanceContextValid(settings.owner, settings.epoch)) {
          throw new Error('ATTENDANCE_CONTEXT_CHANGED')
        }
        if (typeof Blob === 'undefined' || !(blob instanceof Blob) || blob.size <= 0 || !String(blob.type || '').startsWith('image/')) {
          throw new Error('服务端水印证据不是有效图片')
        }
        this.revokeUrl('evidenceUrl')
        this.evidenceId = evidenceId
        this.evidenceUrl = URL.createObjectURL(blob)
        this.evidenceLoadError = ''
        if (!settings.preserveFlowError) this.flowError = ''
        return blob
      }).finally(() => {
        if (!settings.owner || this.attendanceContextValid(settings.owner, settings.epoch)) this.busy = false
      })
    },
    retryEvidence() {
      if (this.busy || !this.evidenceId) return Promise.resolve()
      const owner = this.punchOwner()
      const epoch = this.currentAttendanceContextEpoch()
      const submittedInCurrentSession = this.serverAccepted
      this.evidenceLoadError = ''
      if (submittedInCurrentSession) this.flowError = ''
      return this.loadEvidence(this.evidenceId, { preserveFlowError: !submittedInCurrentSession, owner, epoch }).then(() => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        if (!submittedInCurrentSession) return true
        this.successMessage = `${this.punchTitle}成功，服务端水印证据已生成。`
        this.clearPhoto()
        return this.loadToday()
      }).catch(error => {
        if (!this.attendanceContextValid(owner, epoch)) return false
        const message = submittedInCurrentSession
          ? this.punchErrorText(error)
          : '最新打卡证据加载失败，请点击重试。'
        this.evidenceLoadError = message
        if (submittedInCurrentSession) this.flowError = message
        return false
      })
    },
    openEvidencePreview() {
      if (!this.evidenceUrl || this.evidencePreviewOpen) return
      this.evidencePreviewOpen = true
      this.lockEvidencePreviewScroll()
      this.$nextTick(() => {
        const preview = this.$refs.evidencePreview
        if (!preview || typeof preview.focus !== 'function') return
        try {
          preview.focus({ preventScroll: true })
        } catch (error) {
          preview.focus()
        }
      })
    },
    closeEvidencePreview() {
      this.evidencePreviewOpen = false
      this.restoreEvidencePreviewScroll()
    },
    lockEvidencePreviewScroll() {
      if (typeof document === 'undefined' || !document.body || this.evidencePreviewBodyStyle) return
      const body = document.body
      const scrollY = typeof window !== 'undefined'
        ? Number(window.pageYOffset || window.scrollY || 0)
        : 0
      this.evidencePreviewScrollY = Number.isFinite(scrollY) ? scrollY : 0
      this.evidencePreviewBodyStyle = {
        overflow: body.style.overflow,
        position: body.style.position,
        top: body.style.top,
        width: body.style.width
      }
      body.style.overflow = 'hidden'
      body.style.position = 'fixed'
      body.style.top = `-${this.evidencePreviewScrollY}px`
      body.style.width = '100%'
    },
    restoreEvidencePreviewScroll() {
      const snapshot = this.evidencePreviewBodyStyle
      if (!snapshot) return
      if (typeof document !== 'undefined' && document.body) {
        const body = document.body
        body.style.overflow = snapshot.overflow
        body.style.position = snapshot.position
        body.style.top = snapshot.top
        body.style.width = snapshot.width
      }
      this.evidencePreviewBodyStyle = null
      if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
        window.scrollTo(0, this.evidencePreviewScrollY)
      }
    },
    challengeExpired() {
      if (!this.pending.expiresAt) return false
      const expires = new Date(this.pending.expiresAt).getTime()
      return Number.isFinite(expires) && Date.now() >= expires
    },
    scheduleValue(keys, fallback) {
      const schedule = this.context.schedule || {}
      for (let index = 0; index < keys.length; index += 1) {
        const value = schedule[keys[index]]
        if (value !== undefined && value !== null && String(value).trim()) return value
      }
      return fallback
    },
    punchTypeLabel(type) {
      return String(type || '').toUpperCase() === 'OUT' ? '下班' : '上班'
    },
    punchSlotDisplayLabel(slot) {
      return punchSlotLabel(slot)
    },
    samePunchTarget(value) {
      if (!value || !this.gate) return false
      return Number(value.scheduleId) === Number(this.gate.scheduleId) &&
        String(value.punchType || '').toUpperCase() === String(this.gate.punchType || '').toUpperCase() &&
        String(value.punchSlotKey || '') === String(this.gate.punchSlotKey || '')
    },
    punchSlotCompleted(slot) {
      const source = slot || {}
      const status = String(source.status || source.slotStatus || '').toUpperCase()
      return source.completed === true || source.punched === true || Boolean(source.punchEventId || source.eventId || source.serverPunchTime || source.punchedAt) || ['COMPLETED', 'PUNCHED', 'DONE'].includes(status)
    },
    punchSlotStatusText(slot) {
      const source = slot || {}
      const punchedAt = punchTimeText(source.serverPunchTime || source.punchedAt)
      if (this.punchSlotCompleted(source)) return punchedAt ? `已打卡 ${punchedAt}` : '已打卡'
      if (source.punchSlotKey && source.punchSlotKey === (this.context.nextPunchSlot && this.context.nextPunchSlot.punchSlotKey)) {
        const target = punchTimeText(source.punchType === 'OUT' ? source.endAt : source.startAt)
        if (this.gate.ready) return target ? `${target}·现在可打卡` : '现在可打卡'
        return this.restNotice || '待打卡'
      }
      const target = punchTimeText(source.punchType === 'OUT' ? source.endAt : source.startAt)
      if (target) return `${target}打卡`
      const opens = punchTimeText(source.opensAt)
      const closes = punchTimeText(source.closesAt)
      if (opens && closes) return `${opens}-${closes}`
      return opens ? `${opens}起` : '待打卡'
    },
    resultStatusLabel(status) {
      const key = String(status || '').toUpperCase()
      const labels = { NORMAL: '正常', LATE: '迟到', EARLY: '早退', LATE_EARLY: '迟到+早退', MISSED_IN: '缺上班卡', MISSED_OUT: '缺下班卡', ABSENT: '缺勤', LEAVE_FULL: '全日请假', LEAVE_PARTIAL: '部分请假', EXCEPTION: '异常' }
      return labels[key] || '待结算'
    },
    resultTone(status) {
      const key = String(status || '').toUpperCase()
      if (key === 'NORMAL') return 'success'
      if (key.indexOf('LEAVE') === 0) return 'info'
      if (!key) return 'pending'
      return 'danger'
    },
    minutesAsHours(value) {
      const minutes = Number(value || 0)
      return `${(minutes / 60).toFixed(minutes % 60 ? 1 : 0)}h`
    },
    fileSizeText(size) {
      return `${(Number(size || 0) / 1024 / 1024).toFixed(2)}MB`
    },
    localDateTime(date) {
      const value = date instanceof Date ? date : new Date(date)
      const pad = (number, length = 2) => String(number).padStart(length, '0')
      return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}.${pad(value.getMilliseconds(), 3)}`
    },
    punchErrorText(error) {
      const signal = [error && error.businessCode, error && error.code, error && error.message].filter(Boolean).join(' ')
      if (/CLIENT_CAPTURE_TIME_REQUIRED/i.test(signal)) return '服务端未收到拍照时间，本次未打卡。请重新开始并拍摄新照片。'
      if (/CLIENT_CAPTURE_TIME_STALE|CAPTURE_TIME.*STALE/i.test(signal)) return '现场照片已超过5分钟有效期，本次未打卡。请重新定位并拍照。'
      return attendanceErrorText(error, '上传或服务端校验失败，本页不显示打卡成功。')
    },
    clearPhoto() {
      this.revokeUrl('photoPreviewUrl')
      this.photo = null
      this.photoCapturedAt = ''
    },
    resetPendingCapture(options) {
      const keepError = options && options.keepError
      this.stopBrowserCamera()
      this.clearPhoto()
      this.pending = { challengeToken: '', expiresAt: '', scheduleId: null, punchType: '', punchSlotKey: '', segmentOrder: null, segmentLabel: '', location: null }
      this.pendingOwner = null
      this.pendingContextEpoch = null
      this.uploadProgress = 0
      if (!keepError) this.flowError = ''
    },
    revokeUrl(key) {
      if (key === 'evidenceUrl') this.closeEvidencePreview()
      const value = this[key]
      if (value && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(value)
      this[key] = ''
    },
    goBack() {
      if (this.evidencePreviewOpen) {
        this.closeEvidencePreview()
        return
      }
      this.stopBrowserCamera()
      if (this.$router && typeof this.$router.back === 'function') this.$router.back()
    }
  }
}
</script>

<style scoped>
.mobile-attendance { min-height: 100vh; color: #172033; background: linear-gradient(180deg, #e9f5f2 0, #f4f6f9 230px); }
.attendance-header { position: sticky; top: 0; z-index: 5; display: grid; grid-template-columns: 42px 1fr 42px; align-items: center; gap: 12px; padding: max(14px, env(safe-area-inset-top)) 16px 12px; background: rgba(239, 248, 246, .94); backdrop-filter: blur(16px); }
.attendance-header button { width: 42px; height: 42px; border: 0; border-radius: 14px; background: rgba(255,255,255,.82); color: #244c45; font-size: 18px; }
.attendance-header button:disabled { opacity: .45; }
.attendance-header small { color: #448078; font-weight: 700; }
.attendance-header h1 { margin: 2px 0 0; font-size: 22px; }
.attendance-tabs { display: grid; grid-template-columns: repeat(4, 1fr); margin: 0 16px; padding: 5px; border-radius: 14px; background: rgba(255,255,255,.76); box-shadow: 0 8px 24px rgba(30,75,68,.08); }
.attendance-tabs button { min-height: 40px; border: 0; border-radius: 10px; background: transparent; color: #657278; font-weight: 700; }
.attendance-tabs button.active { background: #1b7569; color: #fff; box-shadow: 0 6px 14px rgba(27,117,105,.24); }
.attendance-content { padding: 16px 16px calc(28px + env(safe-area-inset-bottom)); }
.state-card, .today-card, .schedule-card, .latest-card, .punch-card, .evidence-card, .placeholder-card { margin-bottom: 14px; padding: 18px; border: 1px solid rgba(91,116,112,.12); border-radius: 20px; background: rgba(255,255,255,.94); box-shadow: 0 10px 28px rgba(32,57,73,.08); }
.state-card { text-align: center; }
.state-card > i, .placeholder-card > i { display: block; margin-bottom: 10px; color: #1b7569; font-size: 30px; }
.state-card strong { display: block; font-size: 18px; }
.state-card p, .placeholder-card p { color: #69767f; line-height: 1.65; }
.state-card button, .evidence-loading button { min-height: 40px; padding: 0 18px; border: 0; border-radius: 12px; background: #e9f4f2; color: #17675e; font-weight: 700; }
.state-card.error { border-color: #f5c2c2; }
.today-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; color: #fff; }
.today-card.ready { background: linear-gradient(135deg, #176d63, #2c9588); }
.today-card.locked { background: linear-gradient(135deg, #5f6877, #818a98); }
.today-card__status { display: flex; gap: 13px; }
.today-card__status > span { display: grid; place-items: center; flex: 0 0 45px; height: 45px; border-radius: 15px; background: rgba(255,255,255,.14); font-size: 21px; }
.today-card small { opacity: .8; }
.today-card h2 { margin: 4px 0; font-size: 21px; }
.today-card p { margin: 0; opacity: .88; line-height: 1.5; }
.state-chip { flex: none; padding: 6px 9px; border-radius: 999px; background: rgba(255,255,255,.14); font-size: 11px; font-weight: 700; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading small { color: #6b7c83; }
.section-heading h2 { margin: 3px 0 0; font-size: 19px; }
.section-heading > strong { color: #176d63; font-size: 18px; }
.schedule-card dl { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 17px 0 0; }
.schedule-card dl div { padding: 11px; border-radius: 12px; background: #f5f8f8; }
.schedule-card dt { color: #78878c; font-size: 12px; }
.schedule-card dd { margin: 5px 0 0; font-weight: 700; }
.segment-timeline { display: grid; gap: 10px; margin-top: 15px; padding-top: 15px; border-top: 1px solid #e7eded; }
.segment-timeline__heading, .segment-row header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.segment-timeline__heading strong { color: #355c58; font-size: 12px; }
.segment-timeline__heading span { flex: none; color: #708187; font-size: 11px; }
.segment-row { padding: 12px; border: 1px solid #e2e9e9; border-radius: 13px; background: #f7f9f9; }
.segment-row.current { border-color: rgba(27,117,105,.35); background: #edf7f5; }
.segment-row.upcoming { border-color: rgba(155,105,21,.22); background: #fffaf0; }
.segment-row.complete { background: #f1f7f4; }
.segment-row header strong { color: #29464a; font-size: 13px; }
.segment-row header span { color: #6b7b80; font-size: 10px; font-weight: 700; }
.segment-row.current header span, .segment-row.complete header span { color: #176d63; }
.segment-row.upcoming header span { color: #8b6218; }
.segment-row__slots { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 9px; }
.segment-row__slots > div { display: grid; gap: 3px; min-width: 0; padding: 8px; border-radius: 9px; background: rgba(255,255,255,.88); }
.segment-row__slots span { color: #708187; font-size: 10px; }
.segment-row__slots strong { overflow: hidden; color: #263e43; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.segment-break { display: flex; align-items: center; gap: 7px; margin: 0; padding: 10px 11px; border-radius: 11px; background: #fff5df; color: #8b6218; font-size: 12px; font-weight: 700; }
.latest-card { display: grid; gap: 5px; }
.latest-card small, .latest-card span { color: #78878c; }
.step-list { display: grid; grid-template-columns: auto 1fr auto 1fr auto 1fr auto; align-items: center; margin-bottom: 18px; }
.step-list > span { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: #e7ecee; color: #66777c; font-weight: 800; }
.step-list > span.done { background: #1b7569; color: #fff; }
.step-list > span small { position: absolute; margin-top: 53px; color: #718087; white-space: nowrap; font-size: 9px; font-weight: 600; }
.step-list > i { height: 2px; background: #e0e7e7; }
.primary-action { width: 100%; min-height: 54px; margin-top: 19px; border: 0; border-radius: 16px; background: #176d63; color: #fff; font-size: 16px; font-weight: 800; }
.primary-action:disabled { opacity: .65; }
.flow-message { margin: 22px 0 12px; padding: 11px 13px; border-radius: 12px; line-height: 1.5; }
.flow-message.error { background: #fff0f0; color: #a63838; }
.flow-message.success { background: #edf8f4; color: #176d63; }
.flow-message.warning { background: #fff7e8; color: #936314; }
.location-ready { display: grid; grid-template-columns: 38px 1fr auto; align-items: center; gap: 10px; margin-top: 22px; padding: 13px; border-radius: 14px; background: #edf7f5; }
.location-ready > i { color: #1b7569; font-size: 26px; }
.location-ready p { margin: 4px 0 0; color: #66777c; font-size: 12px; }
.location-ready button, .photo-actions button { min-height: 38px; padding: 0 13px; border: 0; border-radius: 11px; background: #fff; color: #176d63; font-weight: 700; }
.live-camera { display: grid; gap: 11px; margin-top: 22px; padding: 12px; border-radius: 16px; background: #0c1719; color: #fff; }
.live-camera video { display: block; width: 100%; max-height: 56vh; border-radius: 12px; background: #000; object-fit: contain; }
.live-camera p { margin: 0; color: rgba(255,255,255,.78); font-size: 12px; line-height: 1.45; }
.live-camera__actions { display: grid; grid-template-columns: 1fr 2fr; gap: 9px; }
.live-camera__actions button { min-height: 44px; border: 0; border-radius: 12px; background: rgba(255,255,255,.14); color: #fff; font-weight: 800; }
.live-camera__actions button.capture { background: #20a38f; }
.live-camera__actions button:disabled { opacity: .5; }
.photo-review { display: grid; grid-template-columns: 112px 1fr; gap: 13px; margin-top: 22px; }
.photo-review img { width: 112px; height: 145px; border-radius: 14px; object-fit: cover; background: #edf0f2; }
.photo-review p { margin: 5px 0; color: #6c797f; font-size: 12px; line-height: 1.45; }
.photo-actions { display: flex; gap: 8px; margin-top: 10px; }
.photo-actions button { background: #edf3f2; }
.photo-actions button.submit { background: #176d63; color: #fff; }
.evidence-loading { margin-top: 20px; padding: 14px; border-radius: 14px; background: #fff7e8; }
.evidence-recovery-card { margin: 0 0 14px; border: 1px solid rgba(147,99,20,.16); box-shadow: 0 10px 28px rgba(32,57,73,.08); }
.evidence-loading p { color: #806321; line-height: 1.5; }
.evidence-card .section-heading > i { color: #23a37f; font-size: 30px; }
.evidence-open { position: relative; display: block; width: 100%; min-height: 48px; margin-top: 15px; padding: 0; overflow: hidden; border: 0; border-radius: 14px; background: #edf0f2; color: #fff; font: inherit; text-align: left; -webkit-tap-highlight-color: transparent; }
.evidence-open img { display: block; width: 100%; max-height: 520px; margin: 0; object-fit: contain; background: #edf0f2; }
.evidence-open > span { position: absolute; right: 10px; bottom: 10px; padding: 7px 10px; border-radius: 999px; background: rgba(13, 26, 31, .76); font-size: 12px; font-weight: 700; backdrop-filter: blur(8px); }
.evidence-card p { color: #6c797f; font-size: 12px; }
.evidence-preview { position: fixed; inset: 0; z-index: 3200; display: grid; grid-template-rows: auto minmax(0, 1fr); width: 100%; height: 100vh; height: 100dvh; box-sizing: border-box; padding: max(10px, env(safe-area-inset-top)) 12px max(10px, env(safe-area-inset-bottom)); background: rgba(3, 8, 11, .97); color: #fff; outline: none; overscroll-behavior: contain; }
.evidence-preview__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 54px; padding: 0 2px 10px; }
.evidence-preview__header > div { display: grid; gap: 3px; }
.evidence-preview__header strong { font-size: 16px; }
.evidence-preview__header small { color: rgba(255,255,255,.68); font-size: 11px; }
.evidence-preview__header button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-width: 72px; min-height: 44px; padding: 0 13px; border: 1px solid rgba(255,255,255,.2); border-radius: 14px; background: rgba(255,255,255,.1); color: #fff; font: inherit; font-weight: 700; -webkit-tap-highlight-color: transparent; }
.evidence-preview__body { display: flex; min-width: 0; min-height: 0; align-items: center; justify-content: center; overflow: auto; border-radius: 12px; -webkit-overflow-scrolling: touch; touch-action: pan-x pan-y pinch-zoom; }
.evidence-preview__body img { display: block; width: auto; max-width: 100%; height: auto; max-height: 100%; object-fit: contain; user-select: none; -webkit-user-drag: none; }
.evidence-preview-fade-enter-active, .evidence-preview-fade-leave-active { transition: opacity .16s ease; }
.evidence-preview-fade-enter, .evidence-preview-fade-leave-to { opacity: 0; }
.placeholder-card { padding: 36px 22px; text-align: center; }
.placeholder-card h2 { margin: 0; }
.todo-focus { margin: 14px 0; padding: 10px; border-radius: 10px; background: #f1f5f6; color: #596a70; }
.records-card { padding: 18px; border: 1px solid rgba(91,116,112,.12); border-radius: 20px; background: rgba(255,255,255,.94); box-shadow: 0 10px 28px rgba(32,57,73,.08); }
.record-filters { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; margin: 16px 0; }
.record-filters label { display: grid; gap: 5px; color: #718087; font-size: 11px; }
.record-filters input { min-width: 0; height: 39px; padding: 0 8px; border: 1px solid #d9e2e2; border-radius: 10px; background: #fff; color: #263a40; }
.record-filters button { align-self: end; height: 39px; padding: 0 14px; border: 0; border-radius: 10px; background: #176d63; color: #fff; font-weight: 700; }
.record-state { padding: 28px 10px; color: #718087; text-align: center; }
.record-row { display: grid; grid-template-columns: 1fr auto; gap: 10px; padding: 14px 0; border-top: 1px solid #edf1f2; }
.record-row > div:first-child { display: grid; gap: 4px; }
.record-row small { color: #718087; }
.record-row dl { display: grid; grid-template-columns: repeat(4, 1fr); grid-column: 1 / -1; gap: 7px; margin: 2px 0 0; }
.record-row dl div { padding: 8px; border-radius: 9px; background: #f5f8f8; }
.record-row dt { color: #7c898e; font-size: 10px; }
.record-row dd { margin: 4px 0 0; font-size: 12px; font-weight: 700; }
.record-status { align-self: start; padding: 5px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.record-status.success { background: #e8f7f2; color: #17745f; }
.record-status.info { background: #ecf4ff; color: #356999; }
.record-status.danger { background: #fff0f0; color: #a63f3f; }
.record-status.pending { background: #f0f2f4; color: #66757b; }
@media (max-width: 370px) {
  .schedule-card dl { grid-template-columns: 1fr; }
  .photo-review { grid-template-columns: 1fr; }
  .photo-review img { width: 100%; height: 220px; }
  .location-ready { grid-template-columns: 34px 1fr; }
  .location-ready button { grid-column: 1 / -1; }
  .record-filters { grid-template-columns: 1fr 1fr; }
  .record-filters button { grid-column: 1 / -1; }
}
</style>
