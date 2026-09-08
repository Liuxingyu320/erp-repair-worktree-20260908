<template>
  <el-drawer
    title="签约任务详情"
    :visible="visible"
    size="760px"
    append-to-body
    destroy-on-close
    custom-class="sign-task-detail-drawer"
    @close="close"
  >
    <div v-loading="loading" class="drawer-content">
      <el-empty v-if="!loading && !detail" description="任务不存在或无权查看" />
      <template v-else-if="detail">
        <section class="detail-section">
          <h3><span>1</span>任务基本信息</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="任务编号">{{ taskNumberLabel(task) }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag size="mini" :type="statusTagType(task.status)">{{ currentStatusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="签约场景">{{ scenarioLabel(task.scenario) }}</el-descriptions-item>
            <el-descriptions-item label="自动化级别">{{ automationLabel(task.automationLevel) }}</el-descriptions-item>
            <el-descriptions-item v-if="task.scenario === 'TRANSFER'" label="调岗生效日期">
              {{ task.businessEffectiveDate || signPackage.transferEffectiveDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="task.scenario === 'TRANSFER'" label="调岗业务标识">
              <el-tag v-if="task.historicalSupplement || signPackage.historicalSupplement" size="mini" type="danger">历史调岗补录</el-tag>
              <span v-else>当日调岗</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="task.scenario === 'OFFBOARD'" label="最后工作日">
              {{ task.businessEffectiveDate || signPackage.leaveDate || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="task.scenario === 'OFFBOARD'" label="离职业务标识">
              <el-tag v-if="task.historicalSupplement || signPackage.historicalSupplement" size="mini" type="danger">历史离职补录</el-tag>
              <span v-else>当日离职</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="task.scenario === 'OFFBOARD'" label="风险等级">
              <el-tag :type="task.riskLevel === 'HIGH' ? 'danger' : 'success'" size="mini">{{ riskLabel(task.riskLevel) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ task.createdTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="签署截止">{{ task.signDeadline || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前责任人">{{ responsibleLabel }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3><span>2</span>员工与组织资料</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="员工">{{ signPackage.employeeNameSnapshot || employeeFallback }}</el-descriptions-item>
            <el-descriptions-item label="员工账号">{{ task.employeeId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ maskedPhone }}</el-descriptions-item>
            <el-descriptions-item label="身份证">{{ maskedIdCard }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ signPackage.deptNameSnapshot || '-' }}</el-descriptions-item>
            <el-descriptions-item label="门店/组织">{{ signPackage.shopDeptName || task.shopDeptId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ signPackage.postNameSnapshot || '-' }}</el-descriptions-item>
            <el-descriptions-item label="岗位等级">{{ dictionaryLabel(signPackage.postLevelSnapshot) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3><span>3</span>合同与薪资字段</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同类型">{{ dictionaryLabel(signPackage.employmentType) }}</el-descriptions-item>
            <el-descriptions-item label="社保口径">{{ dictionaryLabel(signPackage.socialType) }}</el-descriptions-item>
            <el-descriptions-item label="合同开始">{{ signPackage.contractStartDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合同结束">{{ signPackage.contractEndDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="试用期">{{ dateRange(signPackage.probationStartDate, signPackage.probationEndDate) }}</el-descriptions-item>
            <el-descriptions-item label="薪资版本">{{ dictionaryLabel(signPackage.salaryVersion) }}</el-descriptions-item>
            <el-descriptions-item label="基本工资">{{ money(signPackage.baseSalary) }}</el-descriptions-item>
            <el-descriptions-item label="岗位工资">{{ money(signPackage.postSalary) }}</el-descriptions-item>
            <el-descriptions-item label="绩效工资">{{ money(signPackage.performanceSalary) }}</el-descriptions-item>
            <el-descriptions-item label="薪资合计">{{ money(signPackage.salaryTotal) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3><span>4</span>匹配方案</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="方案名称">{{ signPackage.sourcePlanName || '未匹配' }}</el-descriptions-item>
            <el-descriptions-item label="方案版本">{{ task.planVersionId || signPackage.planVersionId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="签约包编号">{{ businessNoLabel(signPackage.packageNo) }}</el-descriptions-item>
            <el-descriptions-item label="文档版本">{{ versionLabel(frozenDocumentVersion) }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3><span>5</span>文件清单和预览</h3>
          <el-table :data="documents" border size="small" empty-text="暂无已生成文件">
            <el-table-column label="文件" prop="documentName" min-width="220" show-overflow-tooltip />
            <el-table-column label="模板类型" min-width="150" show-overflow-tooltip>
              <template slot-scope="scope">{{ templateTypeLabel(scope.row.templateType) }}</template>
            </el-table-column>
            <el-table-column label="版本" width="110">
              <template slot-scope="scope">{{ versionLabel(scope.row.documentVersion || scope.row.templateVersionSnapshot) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.errorMessage ? 'danger' : 'success'">
                  {{ scope.row.errorMessage ? '生成失败' : '已生成' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template slot-scope="scope">
                <el-button type="text" size="mini" :disabled="!scope.row.documentId" @click="previewDocument(scope.row)">预览</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="detail-section validation-section">
          <h3><span>6</span>系统校验结果</h3>
          <div v-loading="validationLoading" class="validation-summary" :class="`is-${validationPresentation.tone}`">
            <i :class="validationPresentation.icon" />
            <div>
              <strong>{{ validationPresentation.label }}</strong>
              <p>{{ validationPresentation.message }}</p>
            </div>
            <el-button v-if="task.packageId" type="text" size="mini" @click="loadValidation">重新校验</el-button>
          </div>
          <ul v-if="validationResult && validationResult.documentResults" class="validation-list">
            <li v-for="item in validationResult.documentResults" :key="item.documentId">
              <el-tag size="mini" :type="verificationTagType(item.status)">{{ verificationLabel(item.status) }}</el-tag>
              <span>{{ item.documentName || '文件' }}</span>
              <small>{{ businessText(item.message, '文件校验未通过，请刷新后重试') }}</small>
            </li>
          </ul>

          <div class="business-event-history">
            <h4>业务状态事件</h4>
            <el-table :data="taskEvents" border size="mini" empty-text="暂无任务状态事件">
              <el-table-column label="状态变化" min-width="180">
                <template slot-scope="scope">{{ statusLabel(scope.row.fromStatus) }} → {{ statusLabel(scope.row.toStatus) }}</template>
              </el-table-column>
              <el-table-column label="原因分类" min-width="150" show-overflow-tooltip>
                <template slot-scope="scope">{{ reasonLabel(scope.row.reasonCode) }}</template>
              </el-table-column>
              <el-table-column label="原因说明" min-width="220" show-overflow-tooltip>
                <template slot-scope="scope">{{ businessText(scope.row.reasonDetail, '-') }}</template>
              </el-table-column>
              <el-table-column label="发生时间" prop="createdTime" width="165" />
            </el-table>
          </div>

          <el-collapse v-if="canViewTechnicalEvidence" class="technical-evidence">
            <el-collapse-item title="技术证据（仅审计/系统管理员可见）" name="technical">
              <h4>文件验证证据</h4>
              <el-table :data="technicalEvidence" border size="mini" empty-text="暂无文件验证证据">
                <el-table-column label="证据类型" min-width="150">
                  <template slot-scope="scope">{{ evidenceTypeLabel(scope.row.evidenceType) }}</template>
                </el-table-column>
                <el-table-column label="记录校验值" prop="expectedHash" min-width="220" show-overflow-tooltip />
                <el-table-column label="实际校验值" prop="actualHash" min-width="220" show-overflow-tooltip />
                <el-table-column label="文件位置" prop="fileUrl" min-width="220" show-overflow-tooltip />
                <el-table-column label="记录大小" prop="expectedSize" width="110" />
                <el-table-column label="实际大小" prop="actualSize" width="110" />
                <el-table-column label="验真" width="90">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="scope.row.matches ? 'success' : 'danger'">{{ scope.row.matches ? '一致' : '不一致' }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <h4>任务事件链</h4>
              <el-table :data="taskEvents" border size="mini" empty-text="暂无任务事件">
                <el-table-column label="状态变化" min-width="180">
                  <template slot-scope="scope">{{ statusLabel(scope.row.fromStatus) }} → {{ statusLabel(scope.row.toStatus) }}</template>
                </el-table-column>
                <el-table-column label="前序校验值" prop="prevEventHash" min-width="220" show-overflow-tooltip />
                <el-table-column label="事件校验值" prop="eventHash" min-width="220" show-overflow-tooltip />
              </el-table>
              <h4>签约包事件链</h4>
              <el-table :data="packageEvents" border size="mini" empty-text="暂无签约包事件">
                <el-table-column label="事件" min-width="160">
                  <template slot-scope="scope">{{ eventTypeLabel(scope.row.eventType) }}</template>
                </el-table-column>
                <el-table-column label="文档校验值" prop="documentHash" min-width="220" show-overflow-tooltip />
                <el-table-column label="前序校验值" prop="prevEventHash" min-width="220" show-overflow-tooltip />
                <el-table-column label="事件校验值" prop="eventHash" min-width="220" show-overflow-tooltip />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </section>

        <section class="detail-section operational-section">
          <h3><span>7</span>运行与异常信息</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="当前责任人">{{ responsibleLabel }}</el-descriptions-item>
            <el-descriptions-item label="下一动作">{{ actionGuidance }}</el-descriptions-item>
            <el-descriptions-item label="签署截止">{{ task.signDeadline || '-' }}</el-descriptions-item>
            <el-descriptions-item label="冻结文档版本">{{ versionLabel(frozenDocumentVersion) }}</el-descriptions-item>
            <el-descriptions-item label="公司主体就绪">{{ companyReadinessLabel }}</el-descriptions-item>
            <el-descriptions-item label="合同印章就绪">{{ sealReadinessLabel }}</el-descriptions-item>
            <el-descriptions-item label="最近通知状态">{{ latestNotificationLabel }}</el-descriptions-item>
            <el-descriptions-item label="最近通知时间">{{ detail.latestNotificationTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="终态时间">{{ task.terminalTime || signPackage.terminalTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="终态原因分类">{{ reasonLabel(task.terminalReasonCode || signPackage.terminalReasonCode) }}</el-descriptions-item>
            <el-descriptions-item label="终态原因" :span="2">
              {{ businessText(task.terminalReasonDetail || signPackage.terminalReasonDetail, '-') }}
            </el-descriptions-item>
            <el-descriptions-item label="处置状态">
              <el-tag size="mini" :type="resolutionTagType(task.resolutionStatus)">
                {{ resolutionStatusLabel(task.resolutionStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处置时间">{{ task.resolvedTime || signPackage.resolvedTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处置原因分类">{{ reasonLabel(task.resolutionReasonCode || signPackage.resolutionReasonCode) }}</el-descriptions-item>
            <el-descriptions-item label="处置说明" :span="2">
              {{ businessText(task.resolutionReasonDetail || signPackage.resolutionReasonDetail, '-') }}
            </el-descriptions-item>
            <el-descriptions-item label="替代来源任务">{{ task.reissueOfTaskId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="替代目标任务">{{ task.reissuedToTaskId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="失败原因分类">{{ reasonLabel(task.failureCode) }}</el-descriptions-item>
            <el-descriptions-item label="已重试 / 下次重试">
              {{ Number(task.retryCount || 0) }} 次 / {{ task.nextRetryTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="失败说明" :span="2">{{ businessText(task.failureDetail, '-') }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="businessActionsAllowed" class="detail-section action-section">
          <h3><span>8</span>任务处理</h3>
          <el-alert
            v-if="task.failureDetail"
            :title="businessText(task.failureDetail)"
            type="error"
            show-icon
            :closable="false"
            class="failure-alert"
          />
          <el-alert
            v-if="taskMutationError || notificationMutationError"
            title="上次操作结果尚未确认"
            :description="`${taskMutationError || notificationMutationError}。本页面已冻结原请求号和请求参数，请直接重试同一动作。`"
            type="warning"
            show-icon
            :closable="false"
            class="failure-alert"
          />
          <p class="action-guidance">{{ actionGuidance }}</p>
          <div class="action-buttons">
            <el-button
              v-if="canCreateDraft && canOpenPackageEditor"
              v-hasPermi="['oa:signPackage:add']"
              type="primary"
              plain
              @click="openPackageEditor"
            >创建草稿并补资料</el-button>
            <el-button
              v-if="task.status === 'NEEDS_DATA' && task.packageId && canOpenPackageEditor"
              v-hasPermi="['oa:signPackage:add']"
              type="primary"
              plain
              @click="openPackageEditor"
            >补充资料</el-button>
            <el-button
              v-if="canRevalidate"
              v-hasPermi="['oa:signTask:revalidate']"
              type="primary"
              :loading="actionLoading"
              @click="revalidate"
            >重新校验并生成草稿</el-button>
            <el-button
              v-if="task.status === 'READY_TO_SEND'"
              v-hasPermi="['oa:signTask:send']"
              type="success"
              :loading="actionLoading"
              @click="send"
            >发送给员工</el-button>
            <template v-if="isPreparedSignatureFirstFinal">
              <el-button
                v-if="excelImportEnabled"
                v-hasPermi="['oa:signTask:send']"
                type="success"
                :loading="actionLoading"
                @click="requestPreparedFinalSend"
              >发送最终文件（员工仅确认）</el-button>
            </template>
            <template v-if="isSignatureFirstWaitingCompany">
              <el-button
                v-if="excelImportEnabled"
                v-hasPermi="['oa:signTask:batchFinalize']"
                type="warning"
                :loading="actionLoading"
                @click="openSignatureFirstCompanyWork"
              >继续选择公司和印章</el-button>
            </template>
            <el-button
              v-if="task.status === 'PENDING_COMPANY' && !isPreparedSignatureFirstFinal && !isSignatureFirstWaitingCompany && canOpenCompanyFinalization"
              v-hasPermi="['oa:signPackage:send']"
              type="warning"
              :loading="actionLoading"
              @click="openCompanyFinalization"
            >选择公司并盖章</el-button>
            <el-button
              v-if="task.status === 'FAILED'"
              v-hasPermi="['oa:signTask:retry']"
              type="danger"
              :loading="actionLoading"
              @click="retry"
            >重试失败任务</el-button>
            <el-button
              v-if="notificationRetryBusinessKey"
              v-hasPermi="['oa:signTask:retry']"
              type="warning"
              plain
              :loading="actionLoading"
              @click="retryNotification"
            >重新发送通知</el-button>
            <el-button
              v-if="canCancel"
              v-hasPermi="['oa:signTask:cancel']"
              plain
              :loading="actionLoading"
              @click="cancel"
            >取消任务</el-button>
            <el-button
              v-if="canCloseResolution"
              v-hasPermi="[terminalResolutionPermission]"
              type="danger"
              plain
              :loading="resolutionSubmitting"
              @click="openResolution('CLOSE')"
            >关闭异常处置</el-button>
            <el-button
              v-if="canReissueResolution"
              v-hasPermi="[terminalResolutionPermission]"
              type="primary"
              :loading="resolutionSubmitting"
              @click="openResolution('REISSUE')"
            >创建替代版本</el-button>
            <el-button
              v-if="canExtendResolution"
              v-hasPermi="['oa:signTask:resolveExpiry']"
              type="warning"
              plain
              :loading="resolutionSubmitting"
              @click="openResolution('EXTEND')"
            >延长签署期限</el-button>
          </div>
        </section>
      </template>
    </div>

    <el-dialog
      :title="resolutionDialogTitle"
      :visible="resolutionOpen"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!resolutionSubmitting"
      :show-close="!resolutionSubmitting"
      @close="closeResolutionDialog"
    >
      <el-alert
        v-if="resolutionError"
        title="处置结果尚未确认"
        :description="`${resolutionError}。已冻结本次请求号和全部参数，请直接使用原请求号重试。`"
        type="warning"
        show-icon
        :closable="false"
        class="resolution-error"
      />
      <el-alert
        v-if="resolutionAction === 'REISSUE'"
        title="原任务、原签约包和原文件保持终态只读；系统将创建有来源关联的新任务和新签约包。"
        type="info"
        show-icon
        :closable="false"
        class="resolution-notice"
      />
      <el-form :model="resolutionForm" label-width="118px" size="small">
        <el-form-item label="处置动作">
          <el-input :value="resolutionActionLabel" disabled />
        </el-form-item>
        <el-form-item label="冻结文档版本">
          <el-input v-model="resolutionForm.documentVersion" disabled />
        </el-form-item>
        <el-form-item label="预期任务版本">
          <el-input :value="String(resolutionForm.expectedVersion)" disabled />
        </el-form-item>
        <el-form-item label="处置原因码" required>
          <el-select
            v-model="resolutionForm.reasonCode"
            :disabled="resolutionFormFrozen"
            placeholder="请选择原因分类"
            style="width: 100%"
          >
            <el-option
              v-for="option in resolutionReasonOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处置原因说明" required>
          <el-input
            v-model="resolutionForm.reasonDetail"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :disabled="resolutionFormFrozen"
            placeholder="请记录沟通结果、修改内容或延期依据"
          />
        </el-form-item>
        <el-form-item v-if="resolutionAction === 'EXTEND'" label="延期天数" required>
          <el-input-number
            v-model="resolutionForm.extensionDays"
            :min="1"
            :max="365"
            :step="1"
            step-strictly
            :disabled="resolutionFormFrozen"
          />
          <small class="resolution-help">按当前截止时间顺延日历日，仅限到期前的员工签署阶段。</small>
        </el-form-item>
        <el-form-item v-if="resolutionAction === 'REISSUE'" label="替代方案版本" required>
          <el-input-number
            v-model="resolutionForm.replacementPlanVersionId"
            :min="1"
            :step="1"
            step-strictly
            :disabled="resolutionFormFrozen"
            style="width: 100%"
          />
          <small class="resolution-help">默认沿用当前冻结方案版本；如已发布修订版，请填写与当前场景、门店和法人范围一致的新版本 ID。</small>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button :disabled="resolutionSubmitting" @click="closeResolutionDialog">取消</el-button>
        <el-button
          v-if="resolutionError"
          :loading="resolutionSubmitting"
          @click="checkResolutionOutcome"
        >刷新核对结果</el-button>
        <el-button
          type="primary"
          :loading="resolutionSubmitting"
          @click="requestResolution"
        >{{ resolutionError ? '使用原请求号重试' : '确认处置' }}</el-button>
      </span>
    </el-dialog>
  </el-drawer>
</template>

<script>
import {
  cancelSignTask,
  getSignTask,
  revalidateSignTask,
  resolveSignTaskException,
  retrySignTask,
  retrySignTaskNotification,
  sendSignTaskBatch,
  sendSignTask
} from '@/api/oa/signTask'
import { downloadFinalSignPackageDocument, downloadSignPackageDocument, verifySignPackage } from '@/api/oa/signPackage'
import { checkPermi } from '@/utils/permission'
import { templateTypeLabel } from '@/utils/signTemplateType'
import { signDictionaryLabel, signPackageEventLabel, signTaskReasonLabel, signTaskStatusLabel } from '@/utils/signDictionary'
const { normalizeProtectedFileBlob } = require('@/utils/protectedFileBlob')
const { signScenarioLabel } = require('@/utils/signScenario')
const {
  signBusinessNumberLabel,
  signBusinessText,
  signTaskNumberLabel,
  signVersionLabel
} = require('@/utils/signDisplayText')

const TERMINAL_STATUSES = ['SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION']
const EMPLOYEE_DEADLINE_STATUSES = ['PENDING_SIGN', 'VIEWED', 'PENDING_FINAL_CONFIRM']
const TERMINAL_EXCEPTION_STATUSES = ['REFUSED', 'EXPIRED']
const RESOLUTION_REASON_OPTIONS = {
  CLOSE: [
    { value: 'EMPLOYEE_CONTACTED', label: '已与员工沟通' },
    { value: 'BUSINESS_CANCELLED', label: '业务确认不再签约' },
    { value: 'OTHER_CLOSED', label: '其他关闭原因' }
  ],
  REISSUE: [
    { value: 'DOCUMENT_CORRECTED', label: '文档或资料已修正' },
    { value: 'BUSINESS_UPDATED', label: '业务条件已变更' },
    { value: 'EMPLOYEE_REQUESTED', label: '员工要求重新签约' },
    { value: 'OTHER_REISSUE', label: '其他替代原因' }
  ],
  EXTEND: [
    { value: 'BUSINESS_APPROVED', label: '业务已批准延期' },
    { value: 'EMPLOYEE_REQUESTED', label: '员工申请延期' },
    { value: 'OTHER_EXTENSION', label: '其他延期原因' }
  ]
}

function emptyResolutionForm() {
  return {
    documentVersion: '',
    expectedVersion: 0,
    reasonCode: '',
    reasonDetail: '',
    extensionDays: null,
    replacementPlanVersionId: null
  }
}

export default {
  name: 'SignTaskDetailDrawer',
  props: {
    visible: { type: Boolean, default: false },
    taskId: { type: [Number, String], default: null },
    notificationBusinessKey: { type: String, default: '' },
    excelImportEnabled: { type: Boolean, default: false }
  },
  data() {
    return {
      loading: false,
      validationLoading: false,
      actionLoading: false,
      detail: null,
      validationResult: null,
      detailRequestSequence: 0,
      validationRequestSequence: 0,
      actionRequestSequence: 0,
      taskMutationReplay: null,
      taskMutationError: '',
      notificationMutationReplay: null,
      notificationMutationError: '',
      resolutionOpen: false,
      resolutionSubmitting: false,
      resolutionError: '',
      resolutionTaskId: '',
      resolutionAction: '',
      resolutionRequestId: '',
      resolutionPayloadSnapshot: null,
      resolutionBaselineDeadline: '',
      resolutionForm: emptyResolutionForm()
    }
  },
  computed: {
    task() {
      return this.detail && this.detail.task ? this.detail.task : {}
    },
    signPackage() {
      return this.detail && this.detail.signPackage ? this.detail.signPackage : {}
    },
    frozenDocumentVersion() {
      return String(this.signPackage.finalDocumentVersion ||
        (this.detail && this.detail.documentVersion) || this.signPackage.documentVersion || '').trim()
    },
    responsibleLabel() {
      if (['VALIDATING', 'SENDING'].includes(this.task.status)) return '系统自动处理'
      if (['PENDING_SIGN', 'VIEWED', 'PENDING_FINAL_CONFIRM'].includes(this.task.status)) {
        const employee = this.signPackage.employeeNameSnapshot || this.employeeFallback
        return `员工：${employee}`
      }
      if (['SIGNED', 'CANCELLED', 'NO_ACTION'].includes(this.task.status) ||
        (['REFUSED', 'EXPIRED'].includes(this.task.status) && this.task.resolutionStatus !== 'OPEN')) {
        return '无待处理责任人'
      }
      if (this.task.assignedHrUserName) return this.task.assignedHrUserName
      return this.task.assignedHrUserId ? `HR用户 #${this.task.assignedHrUserId}` : '未分配'
    },
    documents() {
      return Array.isArray(this.signPackage.documents) ? this.signPackage.documents : []
    },
    technicalEvidence() {
      return this.validationResult && Array.isArray(this.validationResult.technicalEvidence)
        ? this.validationResult.technicalEvidence
        : []
    },
    taskEvents() {
      return this.detail && Array.isArray(this.detail.events) ? this.detail.events : []
    },
    packageEvents() {
      return Array.isArray(this.signPackage.events) ? this.signPackage.events : []
    },
    businessActionsAllowed() {
      return !!(this.detail && this.detail.businessActionsAllowed)
    },
    canViewTechnicalEvidence() {
      return !!(this.detail && this.detail.technicalEvidenceView) &&
        checkPermi(['oa:signTask:technicalEvidence'])
    },
    employeeFallback() {
      return this.task.employeeId ? `员工 #${this.task.employeeId}` : '-'
    },
    maskedPhone() {
      const value = String(this.signPackage.employeePhoneSnapshot || '')
      return value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : value || '-'
    },
    maskedIdCard() {
      const value = String(this.signPackage.employeeIdCardSnapshot || '')
      return value.length >= 8 ? `${value.slice(0, 4)}**********${value.slice(-4)}` : value || '-'
    },
    validationPresentation() {
      if (!this.task.packageId) {
        return { tone: 'warning', label: '尚未生成', message: '尚未生成签约文件，请先补齐资料并重新校验。', icon: 'el-icon-warning-outline' }
      }
      if (!this.validationResult) {
        return { tone: 'warning', label: '尚未完成验真', message: '尚未取得系统验真结果，请重新验真。', icon: 'el-icon-warning-outline' }
      }
      if (this.validationResult.status === 'VERIFIED') {
        return { tone: 'success', label: '验真通过', message: this.validationResult.message || '文件完整，系统验真通过。', icon: 'el-icon-circle-check' }
      }
      if (this.validationResult.status === 'LEGACY_LIMITED') {
        return { tone: 'warning', label: '旧版验真', message: this.validationResult.message || '该文件为历史数据，仅支持旧版验真。', icon: 'el-icon-warning-outline' }
      }
      if (this.validationResult.status === 'FILE_MISSING') {
        return { tone: 'danger', label: '文件缺失', message: this.validationResult.message || '验真文件缺失，请处理后重试。', icon: 'el-icon-circle-close' }
      }
      return { tone: 'danger', label: '验真异常', message: this.validationResult.message || '文件或签署记录不一致，请处理后重试。', icon: 'el-icon-circle-close' }
    },
    canRevalidate() {
      return this.businessActionsAllowed &&
        ['NEW', 'VALIDATING', 'NEEDS_DATA', 'DRAFT_CREATED', 'FAILED'].includes(this.task.status) &&
        !this.detail.hasConfirmation && !!this.task.packageId
    },
    canCreateDraft() {
      return this.businessActionsAllowed && !this.task.packageId && !this.detail.hasConfirmation &&
        ['NEW', 'NEEDS_DATA', 'FAILED'].includes(this.task.status)
    },
    canOpenPackageEditor() {
      return this.hasSignPackageNavigationPermissions('oa:signPackage:add')
    },
    canOpenCompanyFinalization() {
      return this.hasSignPackageNavigationPermissions('oa:signPackage:send')
    },
    canCancel() {
      return this.businessActionsAllowed &&
        ['NEEDS_DATA', 'DRAFT_CREATED', 'WAITING_HR_CONFIRM', 'READY_TO_SEND', 'FAILED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM'].includes(this.task.status)
    },
    currentStatusLabel() {
      if (this.isPreparedSignatureFirstFinal) return '最终文件已生成，待发送'
      if (this.isSignatureFirstWaitingCompany) return '唯一签名已完成，待选公司和印章'
      return this.statusLabel(this.task.status)
    },
    isSignatureFirstWaitingCompany() {
      const sourceType = String(this.task.sourceType || this.task.taskSourceType || '').trim().toUpperCase()
      return this.businessActionsAllowed && this.task.status === 'PENDING_COMPANY' &&
        sourceType === 'MANUAL_SIGN_EXCEL_IMPORT' &&
        String(this.signPackage.status || '').trim().toLowerCase() === 'pending_company' &&
        String(this.signPackage.signingSequence || '').trim().toUpperCase() === 'SIGNATURE_FIRST' &&
        String(this.signPackage.finalConfirmationStatus || '').trim().toUpperCase() === 'WAITING_COMPANY'
    },
    isPreparedSignatureFirstFinal() {
      return this.businessActionsAllowed && this.task.status === 'PENDING_COMPANY' &&
        String(this.signPackage.status || '').trim().toLowerCase() === 'pending_company' &&
        String(this.signPackage.signingSequence || '').trim().toUpperCase() === 'SIGNATURE_FIRST' &&
        String(this.signPackage.finalConfirmationStatus || '').trim().toUpperCase() === 'PREPARED_NOT_SENT' &&
        !!String(this.signPackage.finalDocumentVersion || '').trim() &&
        /^[0-9a-f]{64}$/i.test(String(this.signPackage.finalDocumentRootHash || '').trim())
    },
    shouldPreviewFinalDocument() {
      const packageStatus = String(this.signPackage.status || '').trim().toLowerCase()
      const confirmationStatus = String(this.signPackage.finalConfirmationStatus || '').trim().toUpperCase()
      const whitelistedState =
        (packageStatus === 'pending_company' && confirmationStatus === 'PREPARED_NOT_SENT') ||
        (packageStatus === 'pending_final_confirm' && confirmationStatus === 'PENDING') ||
        (packageStatus === 'signed' && confirmationStatus === 'CONFIRMED')
      return whitelistedState &&
        !!String(this.signPackage.finalDocumentVersion || '').trim() &&
        /^[0-9a-f]{64}$/i.test(String(this.signPackage.finalDocumentRootHash || '').trim())
    },
    terminalResolutionPermission() {
      return this.task.status === 'REFUSED'
        ? 'oa:signTask:resolveRefusal'
        : 'oa:signTask:resolveExpiry'
    },
    canCloseResolution() {
      return this.businessActionsAllowed && TERMINAL_EXCEPTION_STATUSES.includes(this.task.status) &&
        this.task.resolutionStatus === 'OPEN' && !!this.frozenDocumentVersion &&
        Number.isSafeInteger(Number(this.task.version)) && Number(this.task.version) >= 0
    },
    canReissueResolution() {
      return this.canCloseResolution
    },
    canExtendResolution() {
      return this.businessActionsAllowed && EMPLOYEE_DEADLINE_STATUSES.includes(this.task.status) &&
        !this.task.resolutionStatus && !!this.frozenDocumentVersion &&
        Number.isSafeInteger(Number(this.task.version)) && Number(this.task.version) >= 0 &&
        this.deadlineIsFuture(this.task.signDeadline)
    },
    companyReadinessLabel() {
      if (this.signPackage.legalEntityNameSnapshot) {
        return `已确定：${this.signPackage.legalEntityNameSnapshot}`
      }
      if (this.signPackage.legalEntityIdSnapshot) return '已确定'
      if (this.task.status === 'PENDING_COMPANY') return '待经办人选择'
      if (['PENDING_FINAL_CONFIRM', 'SIGNED'].includes(this.task.status)) return '未记录（需核查）'
      return '员工首次签名后确定'
    },
    sealReadinessLabel() {
      if (this.signPackage.sealNameSnapshot) return `已确定：${this.signPackage.sealNameSnapshot}`
      if (this.signPackage.sealIdSnapshot) return '已确定'
      const policies = this.documents.map(document => document.companySealRequired)
      const requiresSeal = policies.includes('Y')
      const policyComplete = policies.length > 0 && policies.every(value => ['Y', 'N'].includes(value))
      if (!policyComplete) return '冻结印章策略缺失（需核查）'
      if (!requiresSeal) return '冻结策略明确无需印章'
      if (this.task.status === 'PENDING_COMPANY') return '冻结策略要求印章，待经办人选择'
      if (['PENDING_FINAL_CONFIRM', 'SIGNED'].includes(this.task.status)) return '冻结策略要求印章但未记录（需核查）'
      return '冻结策略要求印章，待首次签名后选择'
    },
    notificationRetryBusinessKey() {
      return this.notificationBusinessKey ||
        (this.detail && this.detail.latestNotificationBusinessKey) || ''
    },
    latestNotificationLabel() {
      const status = this.detail && this.detail.latestNotificationStatus
      const channel = this.detail && this.detail.latestNotificationChannel
      const channelLabel = { IN_APP: '站内消息', MOBILE_PUSH: '移动推送' }[channel] || '通知'
      const retryCount = Number(this.detail && this.detail.latestNotificationRetryCount) || 0
      const labels = {
        PENDING: `${channelLabel}待投递`,
        SENDING: `${channelLabel}投递中`,
        SENT: `${channelLabel}已投递`,
        RETRY: `${channelLabel}等待重试（已重试 ${retryCount} 次）`,
        DEAD: `${channelLabel}投递失败，待人工重新入队`
      }
      return labels[status] || '暂无通知记录'
    },
    resolutionDialogTitle() {
      return {
        CLOSE: '关闭签约异常处置',
        REISSUE: '创建替代签约版本',
        EXTEND: '延长签署期限'
      }[this.resolutionAction] || '签约异常处置'
    },
    resolutionActionLabel() {
      return { CLOSE: '关闭异常', REISSUE: '创建替代版本', EXTEND: '到期前延期' }[this.resolutionAction] || '-'
    },
    resolutionReasonOptions() {
      return RESOLUTION_REASON_OPTIONS[this.resolutionAction] || []
    },
    resolutionFormFrozen() {
      return this.resolutionSubmitting || !!this.resolutionError || !!this.resolutionPayloadSnapshot
    },
    actionGuidance() {
      const messages = {
        NEW: this.task.packageId ? '任务刚创建，请运行校验生成草稿。' : '任务尚无草稿，请先创建草稿并补齐资料。',
        VALIDATING: '系统正在校验资料，请稍后刷新。',
        NEEDS_DATA: this.task.packageId ? '员工资料不完整，请补充后重新校验。' : '员工资料不完整，请先创建草稿并补齐资料。',
        DRAFT_CREATED: '草稿已生成，重新校验后可直接发送。',
        WAITING_HR_CONFIRM: '这是升级前的历史状态，重新校验后可直接发送，无需审核。',
        READY_TO_SEND: '文件已就绪，可以直接发送给员工。',
        SENDING: '系统正在发送合同，请稍后刷新。',
        FAILED: '发送或生成失败，请查看原因并重试。',
        PENDING_SIGN: '合同已发送，等待员工阅读和签署。',
        VIEWED: '员工已查看合同，尚未完成签署。',
        PENDING_COMPANY: this.isPreparedSignatureFirstFinal
          ? '最终合同已生成但尚未发送。请逐份预览后发送给员工确认；员工不会再次签名。'
          : (this.isSignatureFirstWaitingCompany
            ? '员工已完成事实确认和唯一一次手写签名。请继续选择公司和印章，生成最终文件后再显式发送。'
            : '员工已完成首次手写签名，请选择合同公司和该公司印章。'),
        PENDING_FINAL_CONFIRM: '公司与印章已补齐，等待员工确认最终合同，无需再次签名。',
        SIGNED: '员工已确认最终合同，签约完成。',
        REFUSED: this.task.resolutionStatus === 'OPEN'
          ? '员工已拒签：请记录沟通结果后关闭异常，或创建有来源关联的替代版本。'
          : '员工拒签版本已冻结，可查看终态与处置记录。',
        EXPIRED: this.task.resolutionStatus === 'OPEN'
          ? '合同已逾期：请关闭异常或创建替代版本，已过期记录不能原地延期。'
          : '逾期版本已冻结，可查看终态与处置记录。',
        CANCELLED: '任务已取消。',
        NO_ACTION: '系统判定无需签约操作。'
      }
      return messages[this.task.status] || '请根据当前状态处理任务。'
    }
  },
  watch: {
    visible(value) {
      if (value) this.loadDetail()
    },
    taskId(value, oldValue) {
      if (value && value !== oldValue) {
        if (this.resolutionPayloadSnapshot) {
          this.resolutionOpen = false
        } else {
          this.resetResolutionState()
        }
        if (!this.taskMutationReplay && !this.notificationMutationReplay) {
          this.resetMutationReplayState()
        }
      }
      if (this.visible && value && value !== oldValue) this.loadDetail()
    }
  },
  methods: {
    close() {
      this.detailRequestSequence += 1
      this.validationRequestSequence += 1
      this.actionRequestSequence += 1
      this.loading = false
      this.validationLoading = false
      this.actionLoading = false
      this.$emit('update:visible', false)
    },
    loadDetail() {
      if (!this.taskId) return Promise.resolve()
      const requestedTaskId = String(this.taskId)
      const requestSequence = ++this.detailRequestSequence
      this.validationRequestSequence += 1
      this.loading = true
      this.validationLoading = false
      this.detail = null
      this.validationResult = null
      return getSignTask(requestedTaskId).then(response => {
        if (requestSequence !== this.detailRequestSequence ||
            requestedTaskId !== String(this.taskId) || !this.visible) return null
        this.detail = response.data || null
        return this.loadValidation(requestedTaskId)
      }).catch(() => {
        if (requestSequence === this.detailRequestSequence &&
            requestedTaskId === String(this.taskId) && this.visible) this.detail = null
      }).finally(() => {
        if (requestSequence === this.detailRequestSequence) this.loading = false
      })
    },
    loadValidation(requestedTaskId = String(this.taskId)) {
      const requestSequence = ++this.validationRequestSequence
      const requestedPackageId = this.task.packageId
      if (!this.task.packageId) {
        this.validationResult = null
        return Promise.resolve()
      }
      this.validationLoading = true
      return verifySignPackage(requestedPackageId, this.canViewTechnicalEvidence).then(response => {
        if (requestSequence !== this.validationRequestSequence ||
            requestedTaskId !== String(this.taskId) ||
            requestedPackageId !== this.task.packageId || !this.visible) return
        this.validationResult = response.data || null
      }).catch(() => {
        if (requestSequence === this.validationRequestSequence &&
            requestedTaskId === String(this.taskId) &&
            requestedPackageId === this.task.packageId && this.visible) {
          this.validationResult = { status: 'FILE_MISSING', message: '系统校验暂不可用，请稍后重试。', documentResults: [] }
        }
      }).finally(() => {
        if (requestSequence === this.validationRequestSequence) this.validationLoading = false
      })
    },
    revalidate() {
      return this.runStableTaskAction('REVALIDATE', revalidateSignTask,
        'MANUAL_REVALIDATE', '经办人手工重新校验', '校验已完成')
    },
    send() {
      return this.runStableTaskAction('SEND', sendSignTask,
        'MANUAL_SEND', '经办人直接发送', '发送操作已完成')
    },
    requestPreparedFinalSend() {
      if (!this.excelImportEnabled || !this.isPreparedSignatureFirstFinal) {
        this.$modal.msgWarning('最终合同尚未准备完成，请刷新任务后重试')
        return Promise.resolve(null)
      }
      return this.$modal.confirm(
        '确认已逐份预览最终 PDF 并发送给员工？发送后员工只需确认最终文件，不会再次签名。'
      ).then(() => this.sendPreparedFinal()).catch(() => null)
    },
    sendPreparedFinal() {
      if (this.actionLoading) return Promise.resolve(null)
      const taskId = String(this.task.taskId || '')
      if (!/^[1-9]\d{0,18}$/.test(taskId) || !this.isPreparedSignatureFirstFinal) {
        this.$modal.msgWarning('任务状态已变化，请刷新后重试')
        return Promise.resolve(null)
      }
      const actionKey = 'SEND_STAGED_FINAL'
      const previous = this.taskMutationReplay
      if (previous && (previous.taskId !== taskId || previous.actionKey !== actionKey)) {
        this.$modal.msgWarning('上一个任务操作结果尚未确认，请先使用原请求号重试同一动作')
        return Promise.resolve(null)
      }
      const replay = previous || Object.freeze({
        taskId,
        actionKey,
        payload: Object.freeze({
          requestId: this.createRequestId('final-send'),
          taskIds: Object.freeze([taskId])
        })
      })
      this.taskMutationReplay = replay
      this.taskMutationError = ''
      return this.runDetailAction(() => sendSignTaskBatch({
        requestId: replay.payload.requestId,
        taskIds: replay.payload.taskIds.slice()
      }).then(response => {
        const data = response && response.data ? response.data : {}
        const items = Array.isArray(data.items) ? data.items : []
        const item = items.find(value => String(value && value.taskId || '') === replay.taskId)
        const result = String(item && item.result || '').trim().toUpperCase()
        if (!['SENT', 'ALREADY_SENT'].includes(result)) {
          throw new Error(this.businessText(item && item.message,
            '最终文件发送结果暂未确认'))
        }
        return getSignTask(replay.taskId)
      }), '最终文件已发送，员工仅需确认文件，不再签名', replay)
    },
    retry() {
      return this.runStableTaskAction('RETRY', retrySignTask,
        'MANUAL_RETRY', '经办人手工重试失败任务', '失败任务已重新处理')
    },
    retryNotification() {
      const businessKey = this.notificationRetryBusinessKey
      if (!businessKey) return Promise.resolve()
      const taskId = String(this.task.taskId || '')
      const previous = this.notificationMutationReplay
      if (previous && (previous.taskId !== taskId || previous.businessKey !== businessKey)) {
        this.$modal.msgWarning('另一个通知重试结果尚未确认，请先使用原请求号重试')
        return Promise.resolve(null)
      }
      const replay = previous || Object.freeze({
        taskId,
        businessKey,
        payload: Object.freeze({ requestId: this.createRequestId('notification'), businessKey })
      })
      this.notificationMutationReplay = replay
      this.notificationMutationError = ''
      this.actionLoading = true
      return retrySignTaskNotification(replay.taskId, Object.assign({}, replay.payload)).then(() => {
        if (this.notificationMutationReplay === replay) {
          this.notificationMutationReplay = null
          this.notificationMutationError = ''
        }
        this.$modal.msgSuccess('失败通知已重新入队')
        this.$emit('notification-retried')
        this.refreshTodo()
        return this.loadDetail()
      }).catch(error => {
        if (this.notificationMutationReplay === replay) {
          this.notificationMutationError = this.businessText(error && error.message,
            '通知重试结果暂未确认')
          this.$modal.msgError(`${this.notificationMutationError}，请使用原请求号重试`)
        }
        return null
      }).finally(() => {
        this.actionLoading = false
      })
    },
    cancel() {
      return this.$modal.confirm('取消后本任务不再继续发送，确认取消吗？').then(() =>
        this.runStableTaskAction('CANCEL', cancelSignTask,
          'OPERATOR_CANCELLED', '经办人手工取消任务', '任务已取消')
      ).catch(() => {})
    },
    openResolution(action) {
      if (!this.resolutionActionAllowed(action)) {
        this.$modal.msgWarning('当前任务状态已不允许执行该处置，请刷新后查看')
        return false
      }
      const taskId = String(this.task.taskId || '')
      if (this.resolutionPayloadSnapshot && this.resolutionTaskId === taskId &&
          this.resolutionAction === action) {
        this.resolutionOpen = true
        return true
      }
      if (this.resolutionPayloadSnapshot) {
        this.$modal.msgWarning('上一次处置结果尚未确认，请先用原请求号重试，不能改成另一个动作')
        return false
      }
      const options = RESOLUTION_REASON_OPTIONS[action] || []
      this.resolutionTaskId = taskId
      this.resolutionAction = action
      this.resolutionRequestId = this.createRequestId('resolution')
      this.resolutionPayloadSnapshot = null
      this.resolutionBaselineDeadline = action === 'EXTEND'
        ? String(this.task.signDeadline || '').trim()
        : ''
      this.resolutionError = ''
      this.resolutionForm = {
        documentVersion: this.frozenDocumentVersion,
        expectedVersion: Number(this.task.version),
        reasonCode: options.length ? options[0].value : '',
        reasonDetail: '',
        extensionDays: action === 'EXTEND' ? 1 : null,
        replacementPlanVersionId: action === 'REISSUE' && Number.isSafeInteger(Number(this.task.planVersionId))
          ? Number(this.task.planVersionId) : null
      }
      this.resolutionOpen = true
      return true
    },
    closeResolutionDialog() {
      if (this.resolutionSubmitting) return false
      this.resolutionOpen = false
      if (!this.resolutionError) this.resetResolutionState()
      return true
    },
    resetResolutionState() {
      this.resolutionOpen = false
      this.resolutionSubmitting = false
      this.resolutionError = ''
      this.resolutionTaskId = ''
      this.resolutionAction = ''
      this.resolutionRequestId = ''
      this.resolutionPayloadSnapshot = null
      this.resolutionBaselineDeadline = ''
      this.resolutionForm = emptyResolutionForm()
    },
    resetMutationReplayState() {
      this.taskMutationReplay = null
      this.taskMutationError = ''
      this.notificationMutationReplay = null
      this.notificationMutationError = ''
    },
    resolutionActionAllowed(action) {
      if (action === 'CLOSE') return this.canCloseResolution
      if (action === 'REISSUE') return this.canReissueResolution
      if (action === 'EXTEND') return this.canExtendResolution
      return false
    },
    requestResolution() {
      if (this.resolutionSubmitting) return Promise.resolve(null)
      const validationMessage = this.resolutionValidationMessage()
      if (validationMessage) {
        this.$modal.msgWarning(validationMessage)
        return Promise.resolve(null)
      }
      const confirmation = {
        CLOSE: '关闭后原任务仍保持拒签/过期终态，确认记录本次处置吗？',
        REISSUE: '将新建替代任务和签约包，原版本永久保持终态只读，确认继续吗？',
        EXTEND: '延期将同步修改任务与签约包截止时间并写入审计事件，确认继续吗？'
      }[this.resolutionAction]
      return this.$modal.confirm(confirmation).then(() => this.submitResolution()).catch(() => null)
    },
    submitResolution() {
      if (this.resolutionSubmitting) return Promise.resolve(null)
      const validationMessage = this.resolutionValidationMessage()
      if (validationMessage) {
        this.$modal.msgWarning(validationMessage)
        return Promise.resolve(null)
      }
      if (!this.resolutionPayloadSnapshot) {
        this.resolutionPayloadSnapshot = Object.freeze(this.buildResolutionPayload())
      }
      const taskId = this.resolutionTaskId
      const action = this.resolutionAction
      const requestId = this.resolutionPayloadSnapshot.requestId
      const payload = Object.assign({}, this.resolutionPayloadSnapshot)
      this.resolutionSubmitting = true
      this.resolutionError = ''
      return resolveSignTaskException(taskId, payload).then(response => {
        if (taskId !== this.resolutionTaskId || action !== this.resolutionAction ||
            requestId !== this.resolutionRequestId) return null
        const nextDetail = response && response.data ? response.data : null
        const successMessage = {
          CLOSE: '异常处置已关闭，原终态记录保持不变',
          REISSUE: '替代任务和新签约包已创建',
          EXTEND: '签署截止时间已延长'
        }[action]
        this.resolutionOpen = false
        this.resetResolutionState()
        this.$modal.msgSuccess(successMessage)
        this.$emit('updated', nextDetail)
        this.refreshTodo()
        return this.loadDetail()
      }).catch(error => {
        if (taskId !== this.resolutionTaskId || action !== this.resolutionAction ||
            requestId !== this.resolutionRequestId) return null
        this.resolutionError = this.businessText(error && error.message,
          '处置结果暂未确认，已保留原请求号和参数')
        this.$modal.msgError(`${this.resolutionError}，请使用原请求号重试`)
        return null
      }).finally(() => {
        if (taskId === this.resolutionTaskId && requestId === this.resolutionRequestId) {
          this.resolutionSubmitting = false
        }
      })
    },
    checkResolutionOutcome() {
      if (this.resolutionSubmitting || !this.resolutionTaskId || !this.resolutionPayloadSnapshot) {
        return Promise.resolve(null)
      }
      const taskId = this.resolutionTaskId
      const requestId = this.resolutionRequestId
      this.resolutionSubmitting = true
      return getSignTask(taskId).then(response => {
        if (taskId !== this.resolutionTaskId || requestId !== this.resolutionRequestId) return null
        const currentDetail = response && response.data ? response.data : null
        if (!currentDetail || !currentDetail.task) throw new Error('任务详情暂时不可用')
        this.detail = currentDetail
        if (!this.resolutionOutcomeConfirmed(currentDetail)) {
          this.$modal.msgWarning('尚未查到本次处置已生效的记录，请继续使用原请求号重试')
          return currentDetail
        }
        this.resetResolutionState()
        this.$modal.msgSuccess('已通过刷新确认上次处置结果')
        this.$emit('updated', currentDetail)
        this.refreshTodo()
        return currentDetail
      }).catch(error => {
        if (taskId === this.resolutionTaskId && requestId === this.resolutionRequestId) {
          this.$modal.msgError(this.businessText(error && error.message, '任务结果核对失败，请稍后重试'))
        }
        return null
      }).finally(() => {
        if (taskId === this.resolutionTaskId && requestId === this.resolutionRequestId) {
          this.resolutionSubmitting = false
        }
      })
    },
    resolutionOutcomeConfirmed(currentDetail) {
      const task = currentDetail && currentDetail.task ? currentDetail.task : {}
      if (this.resolutionAction === 'CLOSE') return task.resolutionStatus === 'CLOSED'
      if (this.resolutionAction === 'REISSUE') {
        return task.resolutionStatus === 'REISSUED' && !!task.reissuedToTaskId
      }
      if (this.resolutionAction === 'EXTEND') {
        const snapshot = this.resolutionPayloadSnapshot || {}
        const expectedVersion = Number(snapshot.expectedVersion)
        const extensionDays = Number(snapshot.extensionDays)
        const baselineDeadline = this.deadlineTimestamp(this.resolutionBaselineDeadline)
        const currentDeadline = this.deadlineTimestamp(task.signDeadline)
        return Number.isSafeInteger(Number(task.version)) && Number(task.version) > expectedVersion &&
          Number.isSafeInteger(extensionDays) && extensionDays > 0 &&
          Number.isFinite(baselineDeadline) && Number.isFinite(currentDeadline) &&
          currentDeadline === baselineDeadline + extensionDays * 24 * 60 * 60 * 1000 &&
          !!task.resolvedTime &&
          String(task.resolutionReasonCode || '') === String(snapshot.reasonCode || '') &&
          String(task.resolutionReasonDetail || '') === String(snapshot.reasonDetail || '')
      }
      return false
    },
    resolutionValidationMessage() {
      if (!['CLOSE', 'REISSUE', 'EXTEND'].includes(this.resolutionAction)) return '处置动作无效'
      if (!this.resolutionRequestId || !this.resolutionTaskId) return '处置请求上下文已失效，请关闭后重新打开'
      if (!String(this.resolutionForm.documentVersion || '').trim()) return '冻结文档版本缺失，请刷新任务'
      const expectedVersion = Number(this.resolutionForm.expectedVersion)
      if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) return '任务版本无效，请刷新任务'
      const reasonCode = String(this.resolutionForm.reasonCode || '').trim()
      if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(reasonCode)) return '请选择有效的处置原因分类'
      const reasonDetail = String(this.resolutionForm.reasonDetail || '').trim()
      if (!reasonDetail) return '请填写处置原因说明'
      if (reasonDetail.length > 500) return '处置原因说明不能超过500个字符'
      if (this.resolutionAction === 'EXTEND') {
        const extensionDays = Number(this.resolutionForm.extensionDays)
        if (!Number.isSafeInteger(extensionDays) || extensionDays < 1 || extensionDays > 365) {
          return '延期天数必须为1到365之间的整数'
        }
      }
      if (this.resolutionAction === 'REISSUE') {
        const replacementPlanVersionId = Number(this.resolutionForm.replacementPlanVersionId)
        if (!Number.isSafeInteger(replacementPlanVersionId) || replacementPlanVersionId <= 0) {
          return '请填写有效的已发布替代方案版本 ID'
        }
      }
      return ''
    },
    buildResolutionPayload() {
      const payload = {
        requestId: this.resolutionRequestId,
        action: this.resolutionAction,
        documentVersion: String(this.resolutionForm.documentVersion || '').trim(),
        reasonCode: String(this.resolutionForm.reasonCode || '').trim(),
        reasonDetail: String(this.resolutionForm.reasonDetail || '').trim(),
        expectedVersion: Number(this.resolutionForm.expectedVersion)
      }
      if (this.resolutionAction === 'EXTEND') {
        payload.extensionDays = Number(this.resolutionForm.extensionDays)
      }
      if (this.resolutionAction === 'REISSUE') {
        payload.replacementPlanVersionId = Number(this.resolutionForm.replacementPlanVersionId)
      }
      return payload
    },
    runStableTaskAction(actionKey, apiAction, reasonCode, reasonDetail, successMessage) {
      if (this.actionLoading) return Promise.resolve(null)
      const taskId = String(this.task.taskId || '')
      const previous = this.taskMutationReplay
      if (previous && (previous.taskId !== taskId || previous.actionKey !== actionKey)) {
        this.$modal.msgWarning('上一个任务操作结果尚未确认，请先使用原请求号重试同一动作')
        return Promise.resolve(null)
      }
      const replay = previous || Object.freeze({
        taskId,
        actionKey,
        payload: Object.freeze({
          requestId: this.createRequestId('task'),
          reasonCode,
          reasonDetail
        })
      })
      this.taskMutationReplay = replay
      this.taskMutationError = ''
      return this.runDetailAction(
        () => apiAction(replay.taskId, Object.assign({}, replay.payload)),
        successMessage,
        replay
      )
    },
    runDetailAction(action, successMessage, replay) {
      if (this.actionLoading) return Promise.resolve()
      const requestedTaskId = String(this.task.taskId)
      const requestSequence = ++this.actionRequestSequence
      this.actionLoading = true
      return action().then(response => {
        if (this.taskMutationReplay === replay) {
          this.taskMutationReplay = null
          this.taskMutationError = ''
        }
        const nextDetail = response && response.data && response.data.task ? response.data : null
        const targetIsCurrent = requestSequence === this.actionRequestSequence &&
          requestedTaskId === String(this.taskId) && this.visible
        if (nextDetail && targetIsCurrent) this.detail = nextDetail
        if (nextDetail && nextDetail.task.status === 'FAILED') {
          this.$modal.msgError(this.businessText(nextDetail.task.failureDetail,
            '任务处理失败，请查看原因后重试'))
        } else {
          this.$modal.msgSuccess(successMessage)
        }
        this.$emit('updated', nextDetail)
        this.refreshTodo()
        return targetIsCurrent ? this.loadDetail() : null
      }).catch(error => {
        if (this.taskMutationReplay === replay) {
          this.taskMutationError = this.businessText(error && error.message,
            '任务操作结果暂未确认')
          this.$modal.msgError(`${this.taskMutationError}，请使用原请求号重试`)
        }
        return null
      }).finally(() => {
        if (requestSequence === this.actionRequestSequence) this.actionLoading = false
      })
    },
    refreshTodo() {
      return this.$store.dispatch('todo/invalidateAfterMutation').catch(() => {})
    },
    hasSignPackageNavigationPermissions(actionPermission) {
      if (!actionPermission || !this.$auth || typeof this.$auth.hasPermi !== 'function') {
        return false
      }
      try {
        return ['oa:signPackage:list', actionPermission]
          .every(permission => this.$auth.hasPermi(permission) === true)
      } catch (error) {
        return false
      }
    },
    openPackageEditor() {
      if (!this.hasSignPackageNavigationPermissions('oa:signPackage:add')) return
      const query = this.task.packageId
        ? { packageId: String(this.task.packageId) }
        : { taskId: String(this.task.taskId) }
      this.$router.push({ path: '/oa/sign-task/package', query }).catch(() => {})
      this.close()
    },
    openCompanyFinalization() {
      if (!this.hasSignPackageNavigationPermissions('oa:signPackage:send')) return
      if (!this.task.packageId) return
      this.$router.push({
        path: '/oa/sign-task/package',
        query: { packageId: String(this.task.packageId), action: 'finalize' }
      }).catch(() => {})
      this.close()
    },
    openSignatureFirstCompanyWork() {
      const packageId = String(this.task.packageId || '')
      let authorized = false
      try {
        authorized = !!this.$auth && typeof this.$auth.hasPermi === 'function' &&
          this.$auth.hasPermi('oa:signTask:batchFinalize') === true
      } catch (error) {
        authorized = false
      }
      if (!authorized || !this.isSignatureFirstWaitingCompany || !/^[1-9]\d{0,18}$/.test(packageId)) {
        this.$modal.msgWarning('签约包状态已变化，请刷新后重试')
        return
      }
      this.$router.push({
        path: '/oa/sign-task',
        query: { companyWorkPackageId: packageId }
      }).catch(() => {})
      this.close()
    },
    createRequestId(prefix) {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `sign-${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    previewDocument(document) {
      if (!this.task.packageId || !document.documentId) return
      const opened = window.open('about:blank', '_blank')
      if (!opened) {
        this.$modal.msgWarning('浏览器阻止了预览窗口，请允许弹窗后重试')
        return
      }
      const download = this.shouldPreviewFinalDocument
        ? downloadFinalSignPackageDocument
        : downloadSignPackageDocument
      download(this.task.packageId, document.documentId).then(blob => {
        return normalizeProtectedFileBlob(blob, 'sign-package-pdf')
      }).then(fileBlob => {
        if (opened.closed) return null
        const url = URL.createObjectURL(fileBlob)
        opened.location.href = url
        setTimeout(() => URL.revokeObjectURL(url), 60000)
        return url
      }).catch(error => {
        if (!opened.closed) opened.close()
        this.$modal.msgWarning((error && error.message) || '签署文件校验失败')
      })
    },
    money(value) {
      if (value === undefined || value === null || value === '') return '-'
      const amount = Number(value)
      return Number.isFinite(amount) ? `¥${amount.toFixed(2)}` : value
    },
    dateRange(start, end) {
      return start || end ? `${start || '未填写'} 至 ${end || '未填写'}` : '-'
    },
    deadlineIsFuture(value) {
      const timestamp = this.deadlineTimestamp(value)
      return Number.isFinite(timestamp) && timestamp > Date.now()
    },
    deadlineTimestamp(value) {
      if (!value) return NaN
      const normalized = String(value).trim().replace(' ', 'T')
      return new Date(normalized).getTime()
    },
    resolutionStatusLabel(status) {
      if (status === 'OPEN') return '待人工处置'
      if (status === 'CLOSED') return '已关闭'
      if (status === 'REISSUED') return '已创建替代版本'
      return TERMINAL_EXCEPTION_STATUSES.includes(this.task.status)
        ? '历史记录（无在线处置状态）'
        : '未进入异常处置'
    },
    resolutionTagType(status) {
      if (status === 'OPEN') return 'danger'
      if (status === 'CLOSED') return 'success'
      if (status === 'REISSUED') return ''
      return 'info'
    },
    statusLabel(status) {
      return signTaskStatusLabel(status)
    },
    statusTagType(status) {
      if (['SIGNED', 'NO_ACTION'].includes(status)) return 'success'
      if (['FAILED', 'REFUSED', 'EXPIRED'].includes(status)) return 'danger'
      if (['WAITING_HR_CONFIRM', 'READY_TO_SEND', 'PENDING_SIGN', 'VIEWED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM'].includes(status)) return 'warning'
      if (TERMINAL_STATUSES.includes(status)) return 'info'
      return ''
    },
    scenarioLabel(value) {
      return signScenarioLabel(value)
    },
    automationLabel(value) {
      const labels = { MANUAL: '人工处理', ASSISTED: '系统辅助', AUTO: '自动处理' }
      return labels[value] || '未知处理方式'
    },
    verificationLabel(status) {
      if (status === 'VERIFIED') return '验真通过'
      if (status === 'LEGACY_LIMITED') return '旧版验真'
      if (status === 'FILE_MISSING') return '文件缺失'
      return '验真异常'
    },
    verificationTagType(status) {
      return status === 'VERIFIED' ? 'success' : status === 'LEGACY_LIMITED' ? 'warning' : 'danger'
    },
    riskLabel(value) {
      return { LOW: '低风险', NORMAL: '常规', MEDIUM: '关注', HIGH: '高风险' }[value] || '常规'
    },
    templateTypeLabel,
    dictionaryLabel(value) {
      return signDictionaryLabel(value)
    },
    reasonLabel(value) {
      return signTaskReasonLabel(value)
    },
    taskNumberLabel(value) {
      return signTaskNumberLabel(value)
    },
    businessText(value, fallback) {
      return signBusinessText(value, fallback)
    },
    businessNoLabel(value) {
      return signBusinessNumberLabel(value)
    },
    versionLabel(value) {
      return signVersionLabel(value)
    },
    evidenceTypeLabel(value) {
      return {
        RENDERED_DOCUMENT: '生成文件', REVIEW_PDF: '阅读文件', SIGNATURE_SAMPLE: '本任务手写签名样本', SIGNATURE_IMAGE: '员工签名',
        SIGNED_PDF: '首次签名文件', SIGN_CERTIFICATE: '签约证明', FINAL_RENDERED_DOCUMENT: '最终生成文件',
        FINAL_REVIEW_PDF: '最终阅读文件', FINAL_SIGNATURE_IMAGE: '复用员工签名', COMPANY_SEAL: '公司印章',
        FINAL_SIGNED_PDF: '最终合同'
      }[value] || '其他证据'
    },
    eventTypeLabel(value) {
      return signPackageEventLabel(value)
    }
  }
}
</script>

<style lang="scss" scoped>
.drawer-content { min-height: 280px; padding: 0 22px 28px; }
.detail-section { margin-bottom: 22px; }
.detail-section h3 { display: flex; align-items: center; gap: 9px; margin: 0 0 12px; color: #1e293b; font-size: 15px; }
.detail-section h3 span { width: 23px; height: 23px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: #334155; font-size: 12px; }
.validation-summary { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid; border-radius: 6px; }
.validation-summary > i { font-size: 25px; }
.validation-summary > div { flex: 1; }
.validation-summary strong { font-size: 15px; }
.validation-summary p { margin: 4px 0 0; font-size: 13px; }
.validation-summary.is-success { color: #166534; border-color: #bbf7d0; background: #f0fdf4; }
.validation-summary.is-warning { color: #9a3412; border-color: #fed7aa; background: #fff7ed; }
.validation-summary.is-danger { color: #991b1b; border-color: #fecaca; background: #fef2f2; }
.validation-list { list-style: none; margin: 12px 0 0; padding: 0; }
.validation-list li { display: grid; grid-template-columns: 76px minmax(120px, 1fr) minmax(180px, 2fr); align-items: center; gap: 10px; padding: 9px 4px; border-bottom: 1px solid #eef2f7; }
.validation-list small { color: #64748b; }
.technical-evidence { margin-top: 14px; }
.operational-section ::v-deep .el-descriptions-item__content { word-break: break-word; }
.action-section { padding: 16px; border: 1px solid #dbe4ef; border-radius: 7px; background: #f8fafc; }
.failure-alert { margin-bottom: 12px; }
.action-guidance { margin: 0 0 14px; color: #475569; line-height: 1.6; }
.action-buttons { display: flex; flex-wrap: wrap; gap: 10px; }
.action-buttons .el-button + .el-button { margin-left: 0; }
.resolution-error, .resolution-notice { margin-bottom: 14px; }
.resolution-help { display: block; margin-top: 5px; color: #64748b; line-height: 1.5; }
@media (max-width: 760px) {
  .validation-list li { grid-template-columns: 76px 1fr; }
  .validation-list small { grid-column: 1 / -1; }
}
</style>

<style lang="scss">
.sign-task-detail-drawer .el-drawer__body { overflow-y: auto; }
</style>
