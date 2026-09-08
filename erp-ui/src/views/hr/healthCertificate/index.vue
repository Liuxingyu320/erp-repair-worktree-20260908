<template>
  <div class="app-container health-certificate-page">
    <div class="page-heading">
      <div><h2>健康证管理</h2><p>员工自行维护证件，新提交进入统一审批，通过后自动投影到员工档案。</p></div>
      <el-button icon="el-icon-refresh" size="mini" @click="refreshAll">刷新</el-button>
    </div>

    <el-alert
      v-if="capabilityLoaded && !intakeEnabled"
      class="intake-maintenance"
      type="warning"
      :closable="false"
      show-icon
      :title="capability.reason || '健康证新受理暂未开放'"
      description="您仍可查看历史记录；人事仍可处理已有待审核记录和到期台账。"
    />

    <section v-if="canManage" class="health-ops-grid">
      <div class="health-ops-cell warning"><span>待审核</span><strong>{{ Number(opsSummary.pendingReviewCount || 0) }}</strong></div>
      <div class="health-ops-cell success"><span>有效</span><strong>{{ Number(opsSummary.validCount || 0) }}</strong></div>
      <div class="health-ops-cell warning"><span>30天内到期</span><strong>{{ Number(opsSummary.expiringCount || 0) }}</strong></div>
      <div class="health-ops-cell danger"><span>已过期</span><strong>{{ Number(opsSummary.expiredCount || 0) }}</strong></div>
      <div class="health-ops-cell danger"><span>本实例提醒失败</span><strong>{{ Number(opsSummary.reminderFailureCount || 0) }}</strong></div>
      <div class="health-ops-cell"><span>最老待审核</span><strong class="age-value">{{ waitingHours(opsSummary.oldestPendingHours) }}</strong></div>
    </section>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="我的健康证" name="mine">
        <div class="tab-actions">
          <el-button v-hasPermi="['hr:healthCertificate:self:edit']" type="primary" size="mini" icon="el-icon-plus" :disabled="!intakeEnabled" @click="openMineForm()">新增证件</el-button>
          <span>续证不会覆盖历史，审核通过后仅最新一条作为当前证件。</span>
        </div>
        <el-table ref="mineTable" v-loading="mineLoading" class="mine-health-table" :data="mineRows" size="small" empty-text="暂无健康证记录" highlight-current-row row-key="certificateId" :row-class-name="mineRowClass">
          <el-table-column label="证件编号" prop="certificateNo" min-width="150"/>
          <el-table-column label="办理日期" prop="issuedDate" width="120"/>
          <el-table-column label="到期日期" prop="expiresOn" width="120"/>
          <el-table-column label="状态" width="120"><template slot-scope="s"><el-tag :type="statusType(s.row.healthCertificateStatus)" size="mini">{{ statusLabel(s.row.healthCertificateStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="附件" width="90"><template slot-scope="s">{{ s.row.attachmentPresent ? '已绑定' : '未绑定' }}</template></el-table-column>
          <el-table-column label="审核说明" prop="rejectionReason" min-width="160" show-overflow-tooltip/>
          <el-table-column label="操作" width="370" fixed="right">
            <template slot-scope="s">
              <el-button v-if="s.row.attachmentPresent" type="text" size="mini" @click="previewAttachment(s.row)">预览附件</el-button>
              <el-button v-if="s.row.approvalInstanceId" type="text" size="mini" @click="openApproval(s.row)">审批轨迹</el-button>
              <el-button v-if="editable(s.row)" v-hasPermi="['hr:healthCertificate:self:edit']" type="text" size="mini" :disabled="!intakeEnabled" @click="openMineForm(s.row)">编辑</el-button>
              <el-button v-if="editable(s.row)" v-hasPermi="['hr:healthCertificate:self:submit']" type="text" size="mini" :disabled="!intakeEnabled" @click="submitMine(s.row)">提交审核</el-button>
              <el-button v-if="canWithdrawMine(s.row)" v-hasPermi="['hr:healthCertificate:self:submit']" type="text" size="mini" class="withdraw-action" :loading="withdrawLoadingId === s.row.certificateId" @click="withdrawMine(s.row)">撤回审批</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="人事审核与到期台账" name="admin" v-if="canManage">
        <el-form :model="query" inline size="small" class="filter-bar">
          <el-form-item label="审核状态"><el-select v-model="query.reviewStatus" clearable>
            <el-option label="旧流程待审核" value="PENDING_REVIEW"/>
            <el-option label="统一审批中" value="APPROVAL_PENDING"/>
            <el-option label="已通过" value="APPROVED"/>
            <el-option label="已退回" value="RETURNED"/>
            <el-option label="已驳回" value="REJECTED"/>
            <el-option label="已撤回" value="WITHDRAWN"/>
            <el-option label="已终止" value="TERMINATED"/>
          </el-select></el-form-item>
          <el-form-item label="证件状态"><el-select v-model="query.healthCertificateStatus" clearable><el-option label="临期或过期" value="EXPIRING_OR_EXPIRED"/><el-option label="即将到期" value="EXPIRING"/><el-option label="已过期" value="EXPIRED"/></el-select></el-form-item>
          <el-form-item><el-button type="primary" size="mini" @click="loadAdmin">查询</el-button><el-button size="mini" @click="resetAdmin">重置</el-button></el-form-item>
        </el-form>
        <el-table v-loading="adminLoading" class="admin-health-table" :data="adminRows" size="small">
          <el-table-column label="员工" min-width="150"><template slot-scope="s"><strong>{{ s.row.employeeName }}</strong><div class="muted">{{ s.row.employeeNo }}</div></template></el-table-column>
          <el-table-column label="当前组织" prop="currentDeptName" min-width="150"/>
          <el-table-column label="证件编号" prop="certificateNo" min-width="140"/>
          <el-table-column label="办理日期" prop="issuedDate" width="115"/>
          <el-table-column label="到期日期" prop="expiresOn" width="115"/>
          <el-table-column label="状态" width="120"><template slot-scope="s"><el-tag :type="statusType(s.row.healthCertificateStatus)" size="mini">{{ statusLabel(s.row.healthCertificateStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="230"><template slot-scope="s"><el-button v-if="s.row.attachmentPresent" type="text" size="mini" @click="previewAttachment(s.row)">预览附件</el-button><el-button v-if="s.row.approvalInstanceId" type="text" size="mini" @click="openApproval(s.row)">审批轨迹</el-button><el-button v-if="s.row.reviewStatus === 'PENDING_REVIEW'" v-hasPermi="['hr:healthCertificate:review']" type="text" size="mini" @click="openReview(s.row)">旧流程审核</el-button></template></el-table-column>
        </el-table>
        <pagination v-show="adminTotal>0" :total="adminTotal" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadAdmin"/>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="mineForm.certificateId ? '编辑健康证' : '新增健康证'" :visible.sync="mineDialog" width="620px" custom-class="health-certificate-mine-dialog" append-to-body :close-on-click-modal="false">
      <el-form ref="mineForm" :model="mineForm" :rules="mineRules" label-width="100px">
        <el-form-item label="证件编号"><el-input v-model="mineForm.certificateNo" maxlength="100"/></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="办理日期" prop="issuedDate"><el-date-picker v-model="mineForm.issuedDate" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item></el-col><el-col :span="12"><el-form-item label="生效日期"><el-date-picker v-model="mineForm.validFrom" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item></el-col></el-row>
        <el-form-item label="到期日期" prop="expiresOn"><el-date-picker v-model="mineForm.expiresOn" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item>
        <el-form-item label="发证机构"><el-input v-model="mineForm.issuerName" maxlength="128"/></el-form-item>
        <el-form-item v-if="driveEnabled" label="云盘附件">
          <el-select v-model="mineForm.attachmentNodeId" clearable filterable style="width:100%" placeholder="选择最近上传的云盘文件">
            <el-option v-for="file in recentFiles" :key="file.nodeId" :label="file.nodeName || file.name" :value="file.nodeId"/>
          </el-select>
          <div class="form-tip">请先把健康证照片或 PDF 上传到“我的云盘”，再在此绑定受控文件。</div>
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="mineDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!intakeEnabled" @click="saveMine">保存草稿</el-button></div>
    </el-dialog>

    <el-dialog title="审核健康证" :visible.sync="reviewDialog" width="520px" custom-class="health-certificate-review-dialog" append-to-body>
      <el-form label-width="90px"><el-form-item label="审核结果"><el-radio-group v-model="reviewForm.decision"><el-radio label="APPROVED">通过</el-radio><el-radio label="REJECTED">驳回</el-radio></el-radio-group></el-form-item><el-form-item v-if="reviewForm.decision==='REJECTED'" label="驳回原因"><el-input v-model="reviewForm.rejectionReason" type="textarea" :rows="3" maxlength="300"/></el-form-item></el-form>
      <div slot="footer"><el-button @click="reviewDialog=false">取消</el-button><el-button type="primary" :loading="reviewing" @click="confirmReview">确认</el-button></div>
    </el-dialog>

    <el-dialog title="健康证与统一审批轨迹" :visible.sync="approvalDialog" width="920px" custom-class="health-approval-dialog" append-to-body>
      <div v-loading="approvalLoading">
        <el-descriptions v-if="approvalRow" :column="3" border size="small">
          <el-descriptions-item label="员工">{{ approvalRow.employeeName || approvalRow.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="证件编号">{{ approvalRow.certificateNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务状态">{{ statusLabel(approvalRow.healthCertificateStatus || approvalRow.reviewStatus) }}</el-descriptions-item>
          <el-descriptions-item label="办理日期">{{ approvalRow.issuedDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="到期日期">{{ approvalRow.expiresOn || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批轮次">{{ approvalRow.approvalRound || '-' }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="approvalDetail">
          <div class="approval-heading"><h4>统一审批节点</h4><el-tag size="mini" :type="approvalStatusType(approvalInstance.status)">{{ approvalStatusLabel(approvalInstance.status) }}</el-tag></div>
          <div class="approval-table-scroll"><el-table :data="approvalTasks" border size="small">
            <el-table-column label="顺序" prop="nodeOrder" width="70"/>
            <el-table-column label="节点" prop="nodeName" min-width="160"/>
            <el-table-column label="状态" width="110"><template slot-scope="s">{{ approvalStatusLabel(s.row.taskStatus) }}</template></el-table-column>
            <el-table-column label="完成时间" prop="completedTime" min-width="170"/>
          </el-table></div>
          <h4>操作记录</h4>
          <div class="approval-table-scroll"><el-table :data="approvalActions" border size="small">
            <el-table-column label="时间" prop="createTime" width="170"/>
            <el-table-column label="动作" width="110"><template slot-scope="s">{{ approvalStatusLabel(s.row.actionType) }}</template></el-table-column>
            <el-table-column label="操作人" prop="operatorName" min-width="120"/>
            <el-table-column label="意见" min-width="220"><template slot-scope="s">{{ s.row.actionReason || '-' }}</template></el-table-column>
          </el-table></div>
        </template>
      </div>
      <div slot="footer">
        <el-button @click="approvalDialog=false">关闭</el-button>
        <el-button v-if="canWithdrawMine(approvalRow)" v-hasPermi="['hr:healthCertificate:self:submit']" type="warning" plain :loading="withdrawLoadingId === approvalRow.certificateId" @click="withdrawMine(approvalRow)">撤回审批</el-button>
        <el-button v-if="canHandleApproval" type="warning" :loading="approvalActionLoading" @click="handleApprovalAction('return')">退回修改</el-button>
        <el-button v-if="canHandleApproval" type="success" :loading="approvalActionLoading" @click="handleApprovalAction('approve')">同意</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listRecentDriveNodes } from '@/api/drive'
import { getHealthCertificateCapability, getHealthCertificateAttachment, getHealthCertificateOpsSummary, getMyHealthCertificates, saveMyHealthCertificateDraft, submitMyHealthCertificate, withdrawMyHealthCertificate, listHealthCertificates, reviewHealthCertificate } from '@/api/hr/healthCertificate'
import { getApprovalInstance } from '@/api/approval/monitor'
import { approveApprovalTask, returnApprovalTask } from '@/api/approval/task'
import { statusLabel as approvalStatusLabel, statusType as approvalStatusType } from '@/views/approval/manage/components/approvalUi'

export default {
  name: 'HrHealthCertificate',
  data() {
    return {
      activeTab: 'mine', mineLoading: false, adminLoading: false, saving: false, reviewing: false,
      approvalDialog: false, approvalLoading: false, approvalActionLoading: false,
      withdrawLoadingId: undefined,
      capabilityLoaded: false, capability: { intakeEnabled: false, reason: '正在检查健康证受理能力' },
      opsSummary: {},
      focusCertificateId: '', approvalTaskId: '', approvalInstanceId: '', routeApprovalOpened: false,
      approvalRow: null, approvalDetail: null,
      mineRows: [], adminRows: [], adminTotal: 0, recentFiles: [], mineDialog: false, reviewDialog: false,
      mineForm: {}, reviewRow: null, reviewForm: { decision: 'APPROVED', rejectionReason: '' },
      query: { pageNum: 1, pageSize: 10, certificateId: undefined, reviewStatus: 'PENDING_REVIEW', healthCertificateStatus: undefined },
      mineRules: { issuedDate: [{ required: true, message: '请选择办理日期', trigger: 'change' }], expiresOn: [{ required: true, message: '请选择到期日期', trigger: 'change' }] }
    }
  },
  computed: {
    driveEnabled() { return !!(this.$store && this.$store.getters && this.$store.getters.driveEnabled === true) },
    canManage() { const p = (this.$store.getters && this.$store.getters.permissions) || []; return p.includes('*:*:*') || p.includes('hr:healthCertificate:list') || p.includes('hr:healthCertificate:review') },
    intakeEnabled() { return this.capabilityLoaded && this.capability.intakeEnabled === true },
    approvalInstance() { return this.approvalDetail && this.approvalDetail.instance || {} },
    approvalTasks() { return this.approvalDetail && Array.isArray(this.approvalDetail.tasks) ? this.approvalDetail.tasks : [] },
    approvalActions() { return this.approvalDetail && Array.isArray(this.approvalDetail.actions) ? this.approvalDetail.actions : [] },
    canHandleApproval() {
      if (!this.approvalTaskId || !this.approvalDetail) return false
      const task = this.approvalTasks.find(item => String(item.taskId) === String(this.approvalTaskId))
      return !!task && String(task.taskStatus || '').toUpperCase() === 'PENDING' &&
        String(this.approvalInstance.status || '').toUpperCase() === 'RUNNING'
    }
  },
  created() {
    this.applyTodoRoute()
    this.refreshAll()
    this.loadCapability().then(() => { if (this.intakeEnabled && this.driveEnabled) this.loadRecentFiles() })
  },
  methods: {
    approvalStatusLabel,
    approvalStatusType,
    loadCapability() {
      return getHealthCertificateCapability().then(r => {
        this.capability = r.data || { intakeEnabled: false, reason: '健康证新受理暂未开放' }
      }).catch(() => {
        this.capability = { intakeEnabled: false, reason: '受理能力检查失败，系统已按关闭处理' }
      }).finally(() => { this.capabilityLoaded = true })
    },
    requireIntake() {
      if (this.intakeEnabled) return true
      this.$modal.msgWarning(this.capability.reason || '健康证新受理暂未开放')
      return false
    },
    applyTodoRoute() {
      const source = (this.$route && this.$route.query) || {}
      const scalar = value => Array.isArray(value) ? value[0] : value
      const view = String(scalar(source.healthCertificateView) || '')
      const healthStatus = String(scalar(source.healthCertificateStatus) || '')
      const reviewStatus = String(scalar(source.reviewStatus) || '')
      const deptId = String(scalar(source.currentDeptId || source.deptId) || '')
      const certificateId = String(scalar(source.certificateId || source.businessId) || '')
      const todoType = String(scalar(source.todoType) || '')
      this.approvalTaskId = String(scalar(source.approvalTaskId) || '')
      this.approvalInstanceId = String(scalar(source.approvalInstanceId) || '')
      if (todoType === 'HR_HEALTH_CERT_REVIEW' && this.canManage) {
        this.activeTab = 'admin'
        this.query.reviewStatus = undefined
        this.query.healthCertificateStatus = undefined
      }
      if (view === 'admin' && this.canManage) this.activeTab = 'admin'
      if (view === 'mine') this.activeTab = 'mine'
      if (['EXPIRING_OR_EXPIRED', 'EXPIRING', 'EXPIRED'].includes(healthStatus)) {
        this.query.healthCertificateStatus = healthStatus
        this.query.reviewStatus = undefined
      } else if (['PENDING_REVIEW', 'APPROVAL_SUBMITTING', 'APPROVAL_PENDING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN', 'TERMINATED'].includes(reviewStatus)) {
        this.query.reviewStatus = reviewStatus
        this.query.healthCertificateStatus = undefined
      }
      if (/^[1-9]\d{0,18}$/.test(deptId)) this.query.currentDeptId = deptId
      if (/^[1-9]\d{0,18}$/.test(certificateId)) {
        this.focusCertificateId = certificateId
        if (todoType === 'HR_HEALTH_CERT_REVIEW' && this.canManage) this.query.certificateId = certificateId
      }
    },
    refreshAll() { this.loadMine(); if (this.canManage) { this.loadAdmin(); this.loadOpsSummary() } },
    loadMine() { this.mineLoading = true; return getMyHealthCertificates().then(r => { this.mineRows = r.data || []; const focused = this.mineRows.find(row => String(row.certificateId) === this.focusCertificateId); if (focused) { this.$nextTick(() => this.$refs.mineTable && this.$refs.mineTable.setCurrentRow(focused)); this.maybeOpenRouteApproval(focused) } }).finally(() => { this.mineLoading = false }) },
    loadAdmin() { this.adminLoading = true; return listHealthCertificates(this.query).then(r => { this.adminRows = r.rows || []; this.adminTotal = r.total || 0; const focused = this.adminRows.find(row => String(row.certificateId) === this.focusCertificateId); if (focused) this.maybeOpenRouteApproval(focused) }).finally(() => { this.adminLoading = false }) },
    loadOpsSummary() { return getHealthCertificateOpsSummary().then(r => { this.opsSummary = r.data || {} }).catch(() => { this.opsSummary = {} }) },
    resetAdmin() { this.query = { pageNum: 1, pageSize: this.query.pageSize || 10, certificateId: undefined, reviewStatus: undefined, healthCertificateStatus: undefined, currentDeptId: undefined }; this.loadAdmin() },
    loadRecentFiles() {
      if (!this.driveEnabled) {
        this.recentFiles = []
        return Promise.resolve([])
      }
      return listRecentDriveNodes(50).then(r => {
        if (!this.driveEnabled) {
          this.recentFiles = []
          return []
        }
        const rows = r.rows || r.data || []
        this.recentFiles = Array.isArray(rows) ? rows.filter(x => {
          const contentType = String(x.contentType || '').toLowerCase()
          return x.nodeType !== 'FOLDER' && ((contentType.startsWith('image/') && contentType !== 'image/svg+xml') || contentType === 'application/pdf')
        }) : []
        return this.recentFiles
      }).catch(() => {
        this.recentFiles = []
        return []
      })
    },
    editable(row) { return ['DRAFT', 'REJECTED', 'RETURNED', 'WITHDRAWN'].includes(row.reviewStatus) },
    canWithdrawMine(row) {
      if (!row || row.reviewStatus !== 'APPROVAL_PENDING') return false
      return this.mineRows.some(item => String(item.certificateId) === String(row.certificateId))
    },
    openMineForm(row) { if (!this.requireIntake()) return; this.mineForm = row ? { ...row } : { certificateId: undefined, version: undefined, certificateNo: '', issuedDate: '', validFrom: '', expiresOn: '', issuerName: '', attachmentNodeId: undefined }; this.mineDialog = true; this.$nextTick(() => this.$refs.mineForm && this.$refs.mineForm.clearValidate()) },
    saveMine() { if (!this.requireIntake()) return; this.$refs.mineForm.validate(ok => { if (!ok) return; this.saving = true; saveMyHealthCertificateDraft(this.mineForm).then(() => { this.$modal.msgSuccess('草稿已保存'); this.mineDialog = false; this.refreshTodo(); this.loadMine() }).finally(() => { this.saving = false }) }) },
    submitMine(row) { if (!this.requireIntake()) return; this.$modal.confirm('提交后将进入统一审批，确认提交？').then(() => submitMyHealthCertificate({ certificateId: row.certificateId, version: row.version })).then(() => { this.$modal.msgSuccess('已提交统一审批'); this.refreshTodo(); this.refreshAll() }) },
    withdrawMine(row) {
      if (!this.canWithdrawMine(row) || this.withdrawLoadingId) {
        if (!this.withdrawLoadingId) this.$modal.msgWarning('只有本人可以撤回统一审批中的健康证申请')
        return
      }
      return this.$modal.confirm('确认撤回健康证审批？系统将使用默认原因“申请人撤回健康证审批”，业务状态将在审批回调后变为已撤回。', '撤回审批').then(() => {
        this.withdrawLoadingId = row.certificateId
        return withdrawMyHealthCertificate(row.certificateId).then(() => {
          this.$modal.msgSuccess('撤回请求已提交')
          this.approvalDialog = false
          this.refreshTodo()
          return this.refreshAll()
        }).finally(() => { this.withdrawLoadingId = undefined })
      }).catch(() => {})
    },
    openReview(row) { this.reviewRow = row; this.reviewForm = { decision: 'APPROVED', rejectionReason: '' }; this.reviewDialog = true },
    confirmReview() { if (this.reviewForm.decision === 'REJECTED' && !this.reviewForm.rejectionReason.trim()) return this.$modal.msgError('请填写驳回原因'); this.reviewing = true; reviewHealthCertificate(this.reviewRow.certificateId, { ...this.reviewForm, version: this.reviewRow.version }).then(() => { this.$modal.msgSuccess('审核完成'); this.reviewDialog = false; this.refreshTodo(); this.refreshAll() }).finally(() => { this.reviewing = false }) },
    maybeOpenRouteApproval(row) {
      if (this.routeApprovalOpened || !this.approvalInstanceId ||
        String(row.approvalInstanceId || '') !== String(this.approvalInstanceId)) return
      this.routeApprovalOpened = true
      this.openApproval(row, true)
    },
    openApproval(row, fromRoute) {
      const instanceId = fromRoute ? this.approvalInstanceId : row && row.approvalInstanceId
      if (!instanceId) return this.$modal.msgWarning('该记录尚未关联统一审批实例')
      if (!fromRoute) this.approvalTaskId = ''
      this.approvalRow = row || null
      this.approvalDialog = true
      this.approvalLoading = true
      this.approvalDetail = null
      return getApprovalInstance(instanceId).then(response => {
        this.approvalDetail = response.data && response.data.data !== undefined ? response.data.data : response.data || {}
      }).finally(() => { this.approvalLoading = false })
    },
    handleApprovalAction(action) {
      if (!this.canHandleApproval || this.approvalActionLoading) return
      const input = action === 'approve'
        ? this.$modal.confirm('确认通过这条健康证申请？', '审批确认').then(() => '')
        : this.$prompt('请输入退回原因', '退回修改', { inputValidator: value => String(value || '').trim() ? true : '原因不能为空' }).then(({ value }) => String(value).trim())
      return input.then(reason => {
        this.approvalActionLoading = true
        const data = { requestId: `HR_HEALTH_CERTIFICATE:${this.approvalTaskId}:${action}:v1`, reason }
        return action === 'approve' ? approveApprovalTask(this.approvalTaskId, data) : returnApprovalTask(this.approvalTaskId, data)
      }).then(() => {
        this.$modal.msgSuccess('审批动作已提交')
        this.refreshTodo()
        const row = this.approvalRow
        this.refreshAll()
        return this.openApproval(row, true)
      }).finally(() => { this.approvalActionLoading = false }).catch(() => {})
    },
    refreshTodo() { return this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {}) },
    mineRowClass({ row }) { return this.focusCertificateId && String(row.certificateId) === this.focusCertificateId ? 'todo-focus-health-certificate' : '' },
    previewAttachment(row) {
      const popup = window.open('about:blank', '_blank')
      if (popup) popup.opener = null
      return getHealthCertificateAttachment(row.certificateId, 'preview').then(blob => {
        const target = URL.createObjectURL(blob)
        if (popup) popup.location.replace(target)
        else this.$download.saveAs(blob, this.attachmentName(row, blob))
        window.setTimeout(() => URL.revokeObjectURL(target), 60000)
      }).catch(error => {
        if (popup && !popup.closed) popup.close()
        throw error
      })
    },
    attachmentName(row, blob) {
      const extensions = { 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'image/png': 'png', 'image/gif': 'gif', 'image/webp': 'webp' }
      return `健康证附件-${row.certificateNo || row.certificateId}.${extensions[blob.type] || 'bin'}`
    },
    waitingHours(value) { const hours = Math.max(0, Number(value || 0)); if (!hours) return '-'; if (hours < 24) return hours + ' 小时'; return Math.floor(hours / 24) + ' 天' },
    statusLabel(v) { return { VALID: '有效', EXPIRING: '即将到期', EXPIRED: '已过期', NOT_SUBMITTED: '未提交', DRAFT: '草稿', PENDING_REVIEW: '旧流程待审核', APPROVAL_SUBMITTING: '提交中', APPROVAL_PENDING: '统一审批中', APPROVED: '已通过', RETURNED: '已退回', REJECTED: '已驳回', WITHDRAWN: '已撤回', TERMINATED: '已终止' }[v] || (v ? '未知健康证状态' : '-') },
    statusType(v) { return { VALID: 'success', EXPIRING: 'warning', EXPIRED: 'danger', PENDING_REVIEW: 'warning', APPROVAL_SUBMITTING: 'warning', APPROVAL_PENDING: 'warning', RETURNED: 'warning', REJECTED: 'danger', TERMINATED: 'danger', DRAFT: 'info', WITHDRAWN: 'info' }[v] || 'info' }
  }
}
</script>

<style scoped lang="scss">
.page-heading,.tab-actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:16px}.page-heading h2{margin:0 0 6px}.page-heading p,.tab-actions span,.muted,.form-tip{margin:0;color:#909399;font-size:12px}.intake-maintenance{margin-bottom:14px}.health-ops-grid{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:10px;margin-bottom:14px}.health-ops-cell{border:1px solid #e5e7eb;border-radius:6px;background:#fff;padding:12px}.health-ops-cell span{display:block;color:#6b7280;font-size:12px}.health-ops-cell strong{display:block;margin-top:6px;color:#374151;font-size:20px}.health-ops-cell.success strong{color:#67c23a}.health-ops-cell.warning strong{color:#e6a23c}.health-ops-cell.danger strong{color:#f56c6c}.health-ops-cell .age-value{font-size:16px}.tab-actions{margin-bottom:12px}.filter-bar{padding:8px 0}.form-tip{margin-top:5px}.withdraw-action{color:#e6a23c}.approval-heading{display:flex;align-items:center;justify-content:space-between;margin-top:18px}.approval-heading h4,h4{margin:16px 0 10px}.approval-table-scroll{overflow-x:auto}::v-deep .mine-health-table tr.todo-focus-health-certificate>td{background:#fff7d6!important}::v-deep .health-approval-dialog{max-width:calc(100vw - 24px)}@media(max-width:1200px){.health-ops-grid{grid-template-columns:repeat(3,minmax(120px,1fr))}}@media(max-width:760px){.health-ops-grid{grid-template-columns:repeat(2,minmax(120px,1fr))}.page-heading{align-items:flex-start}.tab-actions{align-items:flex-start;flex-direction:column}::v-deep .health-approval-dialog .el-dialog__body{padding:12px}::v-deep .health-approval-dialog .el-dialog__footer{padding:10px 12px 16px}}
</style>
