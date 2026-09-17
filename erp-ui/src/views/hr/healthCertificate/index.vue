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

    <el-alert v-if="canManage && opsError" :title="opsError" type="error" :closable="false" show-icon>
      <el-button type="text" @click="loadOpsSummary">重试统计</el-button>
    </el-alert>
    <section v-if="canManage" class="health-ops-grid" v-loading="opsLoading">
      <button type="button" class="health-ops-cell warning" @click="openQueue('PENDING_ALL')"><span>待审核合计</span><strong>{{ opsValue('pendingReviewCount') }}</strong></button>
      <button type="button" class="health-ops-cell warning" @click="openQueue('PENDING_REVIEW')"><span>旧流程待审核</span><strong>{{ opsValue('legacyPendingCount') }}</strong></button>
      <button type="button" class="health-ops-cell warning" @click="openQueue('APPROVAL_PENDING')"><span>统一审批中</span><strong>{{ opsValue('approvalPendingCount') }}</strong></button>
      <button type="button" class="health-ops-cell warning" @click="openQueue('APPROVAL_SUBMITTING')"><span>待发起 / 发起恢复中</span><strong>{{ opsValue('approvalSubmittingCount') }}</strong></button>
      <button type="button" class="health-ops-cell danger" @click="openQueue('APPROVAL_START_FAILED')"><span>其中：发起失败待恢复</span><strong>{{ opsValue('approvalStartFailedCount') }}</strong></button>
      <button type="button" class="health-ops-cell success" @click="openQueue('', 'VALID')"><span>有效</span><strong>{{ opsValue('validCount') }}</strong></button>
      <button type="button" class="health-ops-cell warning" @click="openQueue('', 'EXPIRING')"><span>30天内到期</span><strong>{{ opsValue('expiringCount') }}</strong></button>
      <button type="button" class="health-ops-cell danger" @click="openQueue('', 'EXPIRED')"><span>已过期</span><strong>{{ opsValue('expiredCount') }}</strong></button>
      <div class="health-ops-cell danger"><span>本实例提醒失败</span><strong>{{ opsValue('reminderFailureCount') }}</strong></div>
      <div class="health-ops-cell"><span>最老待审核（自记录创建起）</span><strong class="age-value">{{ opsLoaded && !opsError ? waitingHours(opsSummary.oldestPendingHours) : '—' }}</strong></div>
    </section>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="我的健康证" name="mine">
        <div class="tab-actions">
          <el-button v-hasPermi="['hr:healthCertificate:self:edit']" type="primary" size="mini" icon="el-icon-plus" :disabled="!intakeEnabled || saving || attachmentBusy" @click="openMineForm()">新增证件</el-button>
          <span>续证保留历史。未来生效的证件通过后显示待生效，当前有效证继续保留。</span>
        </div>
        <el-alert v-if="mineError" :title="mineError" type="error" :closable="false"><el-button type="text" @click="loadMine">重试列表</el-button></el-alert>
        <el-table ref="mineTable" v-loading="mineLoading" class="mine-health-table" :data="mineRows" size="small" empty-text="暂无健康证记录" highlight-current-row row-key="certificateId" :row-class-name="mineRowClass">
          <el-table-column label="证件编号" prop="certificateNo" min-width="150"/>
          <el-table-column label="办理日期" prop="issuedDate" width="120"/>
          <el-table-column label="生效日期" prop="validFrom" width="120"><template slot-scope="s">{{ s.row.validFrom || s.row.issuedDate }}</template></el-table-column>
          <el-table-column label="到期日期" prop="expiresOn" width="120"/>
          <el-table-column label="状态" width="120"><template slot-scope="s"><el-tag :type="statusType(s.row.healthCertificateStatus)" size="mini">{{ statusLabel(s.row.healthCertificateStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="附件" width="90"><template slot-scope="s">{{ s.row.attachmentPresent ? '已绑定' : '未绑定' }}</template></el-table-column>
          <el-table-column label="审核说明" prop="rejectionReason" min-width="160" show-overflow-tooltip/>
          <el-table-column label="操作" width="370" fixed="right">
            <template slot-scope="s">
              <el-button v-if="s.row.attachmentPresent" type="text" size="mini" @click="previewAttachment(s.row)">预览附件</el-button>
              <el-button v-if="s.row.approvalInstanceId" type="text" size="mini" @click="openApproval(s.row)">审批轨迹</el-button>
              <el-button v-if="editable(s.row)" v-hasPermi="['hr:healthCertificate:self:edit']" type="text" size="mini" :disabled="!intakeEnabled || saving || attachmentBusy" @click="openMineForm(s.row)">编辑</el-button>
              <el-button v-if="editable(s.row)" v-hasPermi="['hr:healthCertificate:self:submit']" type="text" size="mini" :disabled="!intakeEnabled" @click="submitMine(s.row)">提交审核</el-button>
              <el-button v-if="canWithdrawMine(s.row)" v-hasPermi="['hr:healthCertificate:self:submit']" type="text" size="mini" class="withdraw-action" :loading="withdrawLoadingId === s.row.certificateId" @click="withdrawMine(s.row)">撤回审批</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="人事审核与到期台账" name="admin" v-if="canManage">
        <el-form :model="query" inline size="small" class="filter-bar">
          <el-form-item label="审核状态"><el-select v-model="query.reviewStatus" clearable>
            <el-option label="发起失败待恢复" value="APPROVAL_START_FAILED"/>
            <el-option label="待审核合计" value="PENDING_ALL"/>
            <el-option label="待发起 / 发起恢复中" value="APPROVAL_SUBMITTING"/>
            <el-option label="旧流程待审核" value="PENDING_REVIEW"/>
            <el-option label="统一审批中" value="APPROVAL_PENDING"/>
            <el-option label="已通过" value="APPROVED"/>
            <el-option label="已退回" value="RETURNED"/>
            <el-option label="已驳回" value="REJECTED"/>
            <el-option label="已撤回" value="WITHDRAWN"/>
            <el-option label="已终止" value="TERMINATED"/>
          </el-select></el-form-item>
          <el-form-item label="证件状态"><el-select v-model="query.healthCertificateStatus" clearable><el-option label="有效" value="VALID"/><el-option label="已通过、待生效" value="NOT_YET_EFFECTIVE"/><el-option label="临期或过期" value="EXPIRING_OR_EXPIRED"/><el-option label="即将到期" value="EXPIRING"/><el-option label="已过期" value="EXPIRED"/></el-select></el-form-item>
          <el-form-item><el-button type="primary" size="mini" @click="loadAdmin">查询</el-button><el-button size="mini" @click="resetAdmin">重置</el-button></el-form-item>
        </el-form>
        <el-alert v-if="adminError" :title="adminError" type="error" :closable="false"><el-button type="text" @click="loadAdmin">重试台账</el-button></el-alert>
        <el-table v-loading="adminLoading" class="admin-health-table" :data="adminRows" size="small">
          <el-table-column label="员工" min-width="150"><template slot-scope="s"><strong>{{ s.row.employeeName }}</strong><div class="muted">{{ s.row.employeeNo }}</div></template></el-table-column>
          <el-table-column label="当前组织" prop="currentDeptName" min-width="150"/>
          <el-table-column label="证件编号" prop="certificateNo" min-width="140"/>
          <el-table-column label="办理日期" prop="issuedDate" width="115"/>
          <el-table-column label="生效日期" prop="validFrom" width="120"><template slot-scope="s">{{ s.row.validFrom || s.row.issuedDate }}</template></el-table-column>
          <el-table-column label="到期日期" prop="expiresOn" width="115"/>
          <el-table-column label="状态" width="120"><template slot-scope="s"><el-tag :type="statusType(s.row.healthCertificateStatus)" size="mini">{{ statusLabel(s.row.healthCertificateStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="230"><template slot-scope="s"><el-button v-if="s.row.attachmentPresent" type="text" size="mini" @click="previewAttachment(s.row)">预览附件</el-button><el-button v-if="s.row.approvalInstanceId" type="text" size="mini" @click="openApproval(s.row)">审批轨迹</el-button><el-button v-if="s.row.reviewStatus === 'APPROVAL_SUBMITTING'" v-hasPermi="['hr:healthCertificate:approvalStartOutbox:list']" type="text" size="mini" @click="openStartRecovery(s.row)">发起恢复</el-button><el-button v-if="s.row.reviewStatus === 'PENDING_REVIEW'" v-hasPermi="['hr:healthCertificate:review']" type="text" size="mini" @click="openReview(s.row)">旧流程审核</el-button></template></el-table-column>
        </el-table>
        <pagination v-show="adminTotal>0" :total="adminTotal" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadAdmin"/>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="mineForm.certificateId ? '编辑健康证' : '新增健康证'" :visible.sync="mineDialog" width="620px" custom-class="health-certificate-mine-dialog" append-to-body :close-on-click-modal="false" :before-close="handleMineDialogClose" :close-on-press-escape="!saving && !attachmentBusy" :show-close="!saving && !attachmentBusy">
      <el-alert v-if="mineSaveError" :title="mineSaveError" type="error" :closable="false"/>
      <el-form :disabled="saving" ref="mineForm" :model="mineForm" :rules="mineRules" label-width="100px">
        <el-form-item label="证件编号"><el-input v-model="mineForm.certificateNo" maxlength="100"/></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="办理日期" prop="issuedDate"><el-date-picker v-model="mineForm.issuedDate" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item></el-col><el-col :span="12"><el-form-item label="生效日期"><el-date-picker v-model="mineForm.validFrom" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item></el-col></el-row>
        <el-form-item label="到期日期" prop="expiresOn"><el-date-picker v-model="mineForm.expiresOn" value-format="yyyy-MM-dd" type="date" style="width:100%"/></el-form-item>
        <el-form-item label="发证机构"><el-input v-model="mineForm.issuerName" maxlength="128"/></el-form-item>
        <el-form-item v-if="driveEnabled" label="云盘附件">
          <drive-attachment-picker v-if="mineDialog" :key="mineEditorRevision" v-model="mineForm.attachmentNodeId" :disabled="saving" @busy="attachmentBusy = $event" />
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button :disabled="saving || attachmentBusy" @click="requestMineClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="!intakeEnabled || attachmentBusy" @click="saveMine">保存草稿</el-button></div>
    </el-dialog>

    <el-dialog title="审核健康证" :visible.sync="reviewDialog" width="520px" custom-class="health-certificate-review-dialog" append-to-body>
      <el-form label-width="90px"><el-form-item label="审核结果"><el-radio-group v-model="reviewForm.decision"><el-radio label="APPROVED">通过</el-radio><el-radio label="REJECTED">驳回</el-radio></el-radio-group></el-form-item><el-form-item v-if="reviewForm.decision==='REJECTED'" label="驳回原因"><el-input v-model="reviewForm.rejectionReason" type="textarea" :rows="3" maxlength="300"/></el-form-item></el-form>
      <div slot="footer"><el-button @click="reviewDialog=false">取消</el-button><el-button type="primary" :loading="reviewing" @click="confirmReview">确认</el-button></div>
    </el-dialog>

    <el-dialog custom-class="health-certificate-start-recovery-dialog" title="恢复原健康证审批发起" :visible.sync="startRecoveryOpen" width="650px" append-to-body @close="closeStartRecovery" :close-on-click-modal="!startRecoveryBusy" :close-on-press-escape="!startRecoveryBusy" :show-close="!startRecoveryBusy">
      <p>沿用原审批轮次和发起标识，不新建申请。已知失败才允许重放；结果不明确时先刷新核对。</p>
      <el-alert v-if="startRecoveryError" :title="startRecoveryError" type="error" :closable="false"/>
      <el-table v-loading="startRecoveryLoading" :data="startRecoveryRows">
        <el-table-column label="轮次" prop="businessRound" width="65"/>
        <el-table-column label="状态" prop="status" width="135"/>
        <el-table-column label="最近错误" prop="lastErrorCode" min-width="170"/>
        <el-table-column label="操作" width="135"><template slot-scope="s"><el-button v-if="s.row.status === 'FAILED'" v-hasPermi="['hr:healthCertificate:approvalStartOutbox:replay']" type="text" :disabled="startRecoveryBusy || startRecoveryNeedsCheck" @click="replayStart(s.row)">重放原发起</el-button></template></el-table-column>
      </el-table>
      <span slot="footer"><el-button :disabled="startRecoveryBusy" @click="startRecoveryOpen=false">关闭</el-button><el-button :disabled="startRecoveryBusy || startRecoveryLoading" @click="loadStartRecovery">刷新核对</el-button></span>
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
const { createUiOperationScope } = require('@/utils/uiOperationScope')
import { getSelectedDeptId } from '@/utils/shopContext'
import DriveAttachmentPicker from '@/views/drive/components/DriveAttachmentPicker.vue'
import { listHealthCertificateApprovalStartOutboxes, replayHealthCertificateApprovalStart, getHealthCertificateCapability, getHealthCertificateAttachment, getHealthCertificateOpsSummary, getMyHealthCertificates, saveMyHealthCertificateDraft, submitMyHealthCertificate, withdrawMyHealthCertificate, listHealthCertificates, reviewHealthCertificate } from '@/api/hr/healthCertificate'
import { getApprovalInstance } from '@/api/approval/monitor'
import { approveApprovalTask, returnApprovalTask } from '@/api/approval/task'
import { statusLabel as approvalStatusLabel, statusType as approvalStatusType } from '@/views/approval/manage/components/approvalUi'

export default {
  name: 'HrHealthCertificate',
  components: { DriveAttachmentPicker },
  data() {
    return {
      mineError: '', mineSaveError: '', mineEditorRevision: 0, mineBaseline: '', healthInactive: false, attachmentBusy: false,
      activeTab: 'mine', mineLoading: false, adminLoading: false, saving: false, reviewing: false,
      approvalDialog: false, approvalLoading: false, approvalActionLoading: false,
      withdrawLoadingId: undefined,
      capabilityLoaded: false, capability: { intakeEnabled: false, reason: '正在检查健康证受理能力' },
      startRecoveryNeedsCheck: false, startRecoveryOpen: false, startRecoveryBusy: false, startRecoveryLoading: false, startRecoveryError: '', startRecoveryCertificateId: '', startRecoveryRows: [],
      opsSummary: {}, opsLoaded: false, opsLoading: false, opsError: '', adminError: '',
      focusCertificateId: '', approvalTaskId: '', approvalInstanceId: '', routeApprovalOpened: false,
      approvalRow: null, approvalDetail: null,
      mineRows: [], adminRows: [], adminTotal: 0, mineDialog: false, reviewDialog: false,
      mineForm: {}, reviewRow: null, reviewForm: { decision: 'APPROVED', rejectionReason: '' },
      query: { pageNum: 1, pageSize: 10, certificateId: undefined, reviewStatus: 'PENDING_ALL', healthCertificateStatus: undefined },
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
    if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.healthDeptChanged)
    this.applyTodoRoute()
    this.refreshAll()
    this.loadCapability()
  },
  beforeDestroy() { this.healthScope().deactivate(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.healthDeptChanged) },
  deactivated() { this.healthInactive = true; this.healthScope().deactivate() },
  activated() { this.healthScope().activate(); if (this.healthInactive) { this.healthInactive = false; this.refreshAll(); this.loadCapability() } },
  beforeRouteLeave(to, from, next) { this.requestMineClose().then(closed => next(closed !== false)) },
  watch: {
    '$store.state.user.sessionRevision'() { this.resetHealthContext(); this.refreshAll(); this.loadCapability() },
    '$route.fullPath'() { this.resetHealthContext(); this.applyTodoRoute(); this.refreshAll(); this.loadCapability() }
  },
  methods: {
    healthDeptChanged() { this.resetHealthContext(); this.query.currentDeptId = undefined; this.refreshAll(); this.loadCapability() },
    healthScope() {
      if (!this._healthScope) this._healthScope = createUiOperationScope(() => ({
        actor: this.$store && this.$store.state && this.$store.state.user && this.$store.state.user.id,
        session: this.$store && this.$store.state && this.$store.state.user && this.$store.state.user.sessionRevision,
        dept: getSelectedDeptId(), filterDept: this.query.currentDeptId || '', route: this.$route && this.$route.fullPath
      }))
      return this._healthScope
    },
    opsValue(key) { return this.opsLoaded && !this.opsError ? Number(this.opsSummary[key] || 0) : '—' },
    closeStartRecovery() { this.healthScope().invalidate('start-recovery'); this.healthScope().invalidate('start-replay'); this.startRecoveryBusy = false; this.startRecoveryLoading = false },
    openStartRecovery(row) {
      if (this.startRecoveryBusy) return
      this.closeStartRecovery(); this.startRecoveryCertificateId = String(row.certificateId)
      this.startRecoveryOpen = true; this.startRecoveryRows = []; return this.loadStartRecovery()
    },
    loadStartRecovery() {
      if (!this.startRecoveryOpen || this.startRecoveryBusy) return Promise.resolve()
      const scope = this.healthScope(), target = this.startRecoveryCertificateId, token = scope.begin('start-recovery', target)
      this.startRecoveryLoading = true; this.startRecoveryError = ''
      return listHealthCertificateApprovalStartOutboxes({ certificateId: target, pageNum: 1, pageSize: 50 }).then(response => {
        if (!scope.isCurrent(token, this.startRecoveryCertificateId) || !this.startRecoveryOpen) return
        if (!response || !Array.isArray(response.rows) || response.rows.some(row => String(row.certificateId) !== target)) throw new Error('发起恢复记录与当前证件不一致')
        this.startRecoveryRows = response.rows; this.startRecoveryNeedsCheck = false
      }).catch(error => { if (scope.isCurrent(token, this.startRecoveryCertificateId) && this.startRecoveryOpen) this.startRecoveryError = error.message || '发起记录读取失败，请重试核对' })
        .finally(() => { if (scope.isCurrent(token, this.startRecoveryCertificateId)) this.startRecoveryLoading = false })
    },
    async replayStart(row) {
      if (this.startRecoveryBusy || this.startRecoveryNeedsCheck || !this.startRecoveryOpen || row.status !== 'FAILED' || String(row.certificateId) !== this.startRecoveryCertificateId) return
      const scope = this.healthScope(), target = this.startRecoveryCertificateId, token = scope.begin('start-replay', target)
      const id = row.outboxId, version = row.version
      this.startRecoveryBusy = true; this.startRecoveryError = ''
      try {
        await this.$modal.confirm('按原审批轮次重放这条已失败的发起记录？')
        if (!scope.isCurrent(token, this.startRecoveryCertificateId) || !this.startRecoveryOpen) return
        await replayHealthCertificateApprovalStart(id, version)
        if (!scope.isCurrent(token, this.startRecoveryCertificateId) || !this.startRecoveryOpen) return
        this.startRecoveryBusy = false; this.$modal.msgSuccess('已按原标识请求恢复，请核对最新发起状态')
        await this.loadStartRecovery(); this.refreshAll()
      } catch (error) {
        if (scope.isCurrent(token, this.startRecoveryCertificateId) && this.startRecoveryOpen && error !== 'cancel' && error !== 'close') { this.startRecoveryNeedsCheck = true; this.startRecoveryError = '恢复结果尚未确认，请先刷新核对原记录，勿重复新建申请' }
      } finally { if (scope.isCurrent(token, this.startRecoveryCertificateId)) this.startRecoveryBusy = false }
    },
    openQueue(reviewStatus, healthCertificateStatus) {
      if (!this.canManage) return
      this.activeTab = 'admin'
      this.query = { ...this.query, pageNum: 1, certificateId: undefined, reviewStatus: reviewStatus || undefined, healthCertificateStatus: healthCertificateStatus || undefined }
      return this.loadAdmin()
    },
    approvalStatusLabel,
    approvalStatusType,
    loadCapability() {
      const scope = this.healthScope(), token = scope.begin('capability')
      this.capabilityLoaded = false
      return getHealthCertificateCapability().then(r => {
        if (scope.isCurrent(token)) this.capability = r.data || { intakeEnabled: false, reason: '健康证新受理暂未开放' }
      }).catch(() => {
        if (scope.isCurrent(token)) this.capability = { intakeEnabled: false, reason: '受理能力检查失败，系统已按关闭处理' }
      }).finally(() => { if (scope.isCurrent(token)) this.capabilityLoaded = true })
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
      if (['EXPIRING_OR_EXPIRED', 'EXPIRING', 'EXPIRED', 'VALID', 'NOT_YET_EFFECTIVE'].includes(healthStatus)) {
        this.query.healthCertificateStatus = healthStatus
        this.query.reviewStatus = undefined
      } else if (['APPROVAL_START_FAILED', 'PENDING_ALL', 'PENDING_REVIEW', 'APPROVAL_SUBMITTING', 'APPROVAL_PENDING', 'APPROVED', 'RETURNED', 'REJECTED', 'WITHDRAWN', 'TERMINATED'].includes(reviewStatus)) {
        this.query.reviewStatus = reviewStatus
        this.query.healthCertificateStatus = undefined
      }
      if (/^[1-9]\d{0,18}$/.test(deptId)) this.query.currentDeptId = deptId
      if (/^[1-9]\d{0,18}$/.test(certificateId)) {
        this.focusCertificateId = certificateId
        if (todoType === 'HR_HEALTH_CERT_REVIEW' && this.canManage) this.query.certificateId = certificateId
      }
    },
    resetHealthContext() {
      this.healthScope().invalidate(); this.opsLoaded = false; this.mineRows = []; this.mineLoading = false; this.mineError = ''
      this.startRecoveryOpen = false; this.approvalDialog = false; this.invalidateMineEditor()
    },
    refreshAll() { return Promise.all([this.loadMine(), ...(this.canManage ? [this.loadAdmin(), this.loadOpsSummary()] : [])]) },
    loadMine() {
      const scope = this.healthScope(), token = scope.begin('mine')
      this.mineLoading = true; this.mineError = ''
      return getMyHealthCertificates().then(r => {
        if (!scope.isCurrent(token)) return
        if (!r || !Array.isArray(r.data)) throw Error('健康证列表响应不完整')
        this.mineRows = r.data
        const focused = this.mineRows.find(row => String(row.certificateId) === this.focusCertificateId)
        if (focused) this.$nextTick(() => {
          if (!scope.isCurrent(token)) return
          if (this.$refs.mineTable) this.$refs.mineTable.setCurrentRow(focused)
          this.maybeOpenRouteApproval(focused)
        })
      }).catch(error => { if (scope.isCurrent(token)) this.mineError = error.message || '健康证列表读取失败，请重试' })
        .finally(() => { if (scope.isCurrent(token)) this.mineLoading = false })
    },
    loadAdmin() {
      const scope = this.healthScope(), payload = { ...this.query }, token = scope.begin('admin', payload)
      this.adminLoading = true; this.adminError = ''
      return listHealthCertificates(payload).then(r => {
        if (!scope.isCurrent(token, this.query)) return
        if (!r || !Array.isArray(r.rows)) throw new Error('健康证台账响应不完整')
        this.adminRows = r.rows; this.adminTotal = r.total || 0
        const focused = this.adminRows.find(row => String(row.certificateId) === this.focusCertificateId)
        if (focused) this.maybeOpenRouteApproval(focused)
      }).catch(error => { if (scope.isCurrent(token, this.query)) this.adminError = error.message || '台账加载失败，请重试' })
        .finally(() => { if (scope.isCurrent(token, this.query)) this.adminLoading = false })
    },
    loadOpsSummary() {
      const scope = this.healthScope(), token = scope.begin('summary')
      this.opsLoading = true; this.opsError = ''
      return getHealthCertificateOpsSummary({ currentDeptId: this.query.currentDeptId }).then(r => {
        if (!scope.isCurrent(token)) return
        if (!r || !r.data || typeof r.data.pendingReviewCount !== 'number') throw new Error('健康证统计响应不完整')
        this.opsSummary = r.data; this.opsLoaded = true
      }).catch(error => { if (scope.isCurrent(token)) this.opsError = error.message || '统计暂时不可用，请重试' })
        .finally(() => { if (scope.isCurrent(token)) this.opsLoading = false })
    },
    resetAdmin() { this.query = { pageNum: 1, pageSize: this.query.pageSize || 10, certificateId: undefined, reviewStatus: undefined, healthCertificateStatus: undefined, currentDeptId: undefined }; this.loadAdmin() },
    editable(row) { return ['DRAFT', 'REJECTED', 'RETURNED', 'WITHDRAWN'].includes(row.reviewStatus) },
    canWithdrawMine(row) {
      if (!row || row.reviewStatus !== 'APPROVAL_PENDING') return false
      return this.mineRows.some(item => String(item.certificateId) === String(row.certificateId))
    },
    invalidateMineEditor() {
      this.healthScope().invalidate('mine-save'); this.healthScope().invalidate('mine-close')
      this.mineEditorRevision += 1; this.mineDialog = false; this.saving = false; this.attachmentBusy = false; this.mineSaveError = ''; this.mineBaseline = ''
    },
    openMineForm(row) {
      if (this.saving || this.attachmentBusy || !this.requireIntake()) return
      this.invalidateMineEditor()
      this.mineForm = row ? { ...row } : { certificateId: undefined, version: undefined, certificateNo: '', issuedDate: '', validFrom: '', expiresOn: '', issuerName: '', attachmentNodeId: undefined }
      this.mineBaseline = JSON.stringify(this.mineForm); this.mineDialog = true
      const revision = this.mineEditorRevision
      this.$nextTick(() => { if (revision === this.mineEditorRevision && this.$refs.mineForm) this.$refs.mineForm.clearValidate() })
    },
    requestMineClose() {
      if (!this.mineDialog) return Promise.resolve(true)
      if (this.saving || this.attachmentBusy) { this.$modal.msgWarning('请等待当前保存或附件上传完成'); return Promise.resolve(false) }
      const scope = this.healthScope(), revision = this.mineEditorRevision, token = scope.begin('mine-close')
      const dirty = this.mineBaseline && this.mineBaseline !== JSON.stringify(this.mineForm)
      const confirm = dirty ? this.$modal.confirm('健康证有未保存内容，确定放弃修改？', '未保存提醒', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) : Promise.resolve()
      return confirm.then(() => { if (!scope.isCurrent(token) || revision !== this.mineEditorRevision) return false; this.invalidateMineEditor(); return true }).catch(() => false)
    },
    handleMineDialogClose() { return this.requestMineClose() },
    saveMine() {
      if (this.saving || this.attachmentBusy || !this.mineDialog || !this.requireIntake()) return Promise.resolve()
      const scope = this.healthScope(), revision = this.mineEditorRevision, token = scope.begin('mine-save'), payload = { ...this.mineForm }
      const current = () => scope.isCurrent(token) && this.mineDialog && revision === this.mineEditorRevision
      this.saving = true; this.mineSaveError = ''
      return new Promise(resolve => this.$refs.mineForm.validate(resolve)).then(ok => {
        if (!ok || !current()) return
        return saveMyHealthCertificateDraft(payload).then(() => {
          if (!current()) return
          this.$modal.msgSuccess('草稿已保存'); this.invalidateMineEditor(); this.refreshTodo(); return this.loadMine()
        })
      }).catch(error => { if (current()) this.mineSaveError = error.message || '保存结果待核对，填写内容已保留' })
        .finally(() => { if (scope.isCurrent(token) && revision === this.mineEditorRevision) this.saving = false })
    },
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
    statusLabel(v) { return { NOT_YET_EFFECTIVE: '已通过、待生效', INVALID_DATES: '日期待核对', VALID: '有效', EXPIRING: '即将到期', EXPIRED: '已过期', NOT_SUBMITTED: '未提交', DRAFT: '草稿', PENDING_REVIEW: '旧流程待审核', APPROVAL_SUBMITTING: '提交中', APPROVAL_PENDING: '统一审批中', APPROVED: '已通过', RETURNED: '已退回', REJECTED: '已驳回', WITHDRAWN: '已撤回', TERMINATED: '已终止' }[v] || (v ? '未知健康证状态' : '-') },
    statusType(v) { return { VALID: 'success', EXPIRING: 'warning', EXPIRED: 'danger', PENDING_REVIEW: 'warning', APPROVAL_SUBMITTING: 'warning', APPROVAL_PENDING: 'warning', RETURNED: 'warning', REJECTED: 'danger', TERMINATED: 'danger', DRAFT: 'info', WITHDRAWN: 'info' }[v] || 'info' }
  }
}
</script>

<style scoped lang="scss">
.page-heading,.tab-actions{display:flex;align-items:center;justify-content:space-between;gap:16px}.page-heading{margin-bottom:16px}.page-heading h2{margin:0 0 6px}.page-heading p,.tab-actions span,.muted,.form-tip{margin:0;color:#909399;font-size:12px}.intake-maintenance{margin-bottom:14px}.health-ops-grid{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:10px;margin-bottom:14px}.health-ops-cell{border:1px solid #e5e7eb;border-radius:6px;background:#fff;padding:12px}.health-ops-cell span{display:block;color:#6b7280;font-size:12px}.health-ops-cell strong{display:block;margin-top:6px;color:#374151;font-size:20px}.health-ops-cell.success strong{color:#67c23a}.health-ops-cell.warning strong{color:#e6a23c}.health-ops-cell.danger strong{color:#f56c6c}.health-ops-cell .age-value{font-size:16px}.tab-actions{margin-bottom:12px}.filter-bar{padding:8px 0}.form-tip{margin-top:5px}.withdraw-action{color:#e6a23c}.approval-heading{display:flex;align-items:center;justify-content:space-between;margin-top:18px}.approval-heading h4,h4{margin:16px 0 10px}.approval-table-scroll{overflow-x:auto}::v-deep .mine-health-table tr.todo-focus-health-certificate>td{background:#fff7d6!important}::v-deep .health-approval-dialog{max-width:calc(100vw - 24px)}@media(max-width:1200px){.health-ops-grid{grid-template-columns:repeat(3,minmax(120px,1fr))}}@media(max-width:760px){.health-ops-grid{grid-template-columns:repeat(2,minmax(120px,1fr))}.page-heading{align-items:flex-start}.tab-actions{align-items:flex-start;flex-direction:column}::v-deep .health-approval-dialog .el-dialog__body{padding:12px}::v-deep .health-approval-dialog .el-dialog__footer{padding:10px 12px 16px}}
</style>
