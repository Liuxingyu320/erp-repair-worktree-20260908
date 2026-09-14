<template>
  <div class="mobile-reimbursement mobile-system-page">
    <approval-command-recovery :message="withdrawError" :reason="withdrawReason" :unknown="withdrawUnknown" :busy="acting || checkingWithdraw" :checking="checkingWithdraw" :checked="withdrawChecked" check-label="核对撤回结果" retry-label="重试原撤回请求" @check="checkWithdrawCommand" @retry="retryWithdrawCommand" />
    <section v-if="submitRecoveryMessage" class="state-card" role="alert">
      <p>{{ submitRecoveryMessage }}</p>
      <template v-if="submitUnknown">
        <button type="button" :disabled="submitting || checkingSubmission" @click="checkSubmissionResult">核对提交结果</button>
        <button type="button" :disabled="submitting || checkingSubmission || !submitChecked" @click="retrySubmissionIntent">重试原提交</button>
        <button type="button" :disabled="submitting || checkingSubmission" @click="openSubmissionRecord">查看当前报销</button>
      </template>
    </section>
    <header class="mobile-header">
      <button type="button" aria-label="返回" @click="goBack">
        <i class="el-icon-arrow-left" aria-hidden="true" />
      </button>
      <div>
        <small>OA 费用管理</small>
        <h1>{{ pageTitle }}</h1>
      </div>
      <button type="button" aria-label="刷新" :disabled="loading || busy" @click="refreshCurrent">
        <i class="el-icon-refresh" />
      </button>
    </header>

    <main class="mobile-content">
      <section v-if="error" class="state-card error" role="alert">
        <strong>暂时无法加载</strong>
        <p>{{ error }}</p>
        <button type="button" @click="reload">重试</button>
      </section>

      <template v-else-if="mode === 'list'">
        <section class="intro-card">
          <div>
            <small>我的报销</small>
            <h2>发票随手传，状态清楚看</h2>
          </div>
          <strong>{{ total }} 笔</strong>
        </section>
        <section class="list-filter" aria-label="筛选报销记录">
          <label>
            <span class="sr-only">搜索报销标题或单号</span>
            <input
              v-model.trim="listQuery.keyword"
              type="search"
              placeholder="搜索标题或报销单号"
              @keyup.enter="applyListFilters"
            >
          </label>
          <label>
            <span class="sr-only">筛选报销状态</span>
            <select v-model="listQuery.status" @change="applyListFilters">
              <option value="">全部状态</option>
              <option value="draft">草稿</option>
              <option value="pending">审批中</option>
              <option value="approved">已通过</option>
              <option value="returned">已退回</option>
              <option value="rejected">已拒绝</option>
              <option value="withdrawn">已撤回</option>
            </select>
          </label>
          <button type="button" :disabled="loading" @click="applyListFilters">查询</button>
        </section>
        <section v-if="loading" class="state-card" role="status">正在加载报销记录…</section>
        <section v-else-if="!rows.length" class="state-card">
          <strong>还没有报销申请</strong>
          <p>新建草稿、填写费用明细，再上传发票提交审批。</p>
        </section>
        <template v-else>
          <article
            v-for="row in rows"
            :key="row.reimbursementId"
            class="claim-card"
          >
            <div class="claim-head">
              <div>
                <small>{{ row.reimbursementNo }}</small>
                <h2>{{ row.title || '未命名报销' }}</h2>
              </div>
              <span :class="['status-chip', statusTone(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </div>
            <div class="claim-meta">
              <strong>{{ money(row.totalAmount) }}</strong>
              <span>{{ row.invoiceCount || 0 }} 张发票</span>
            </div>
            <footer>
              <span>{{ row.createTime || '-' }}</span>
              <button
                type="button"
                @click="openDetail(row.reimbursementId)"
              >查看详情</button>
              <button
                v-if="editable(row)"
                type="button"
                @click="openForm(row.reimbursementId)"
              >编辑</button>
              <button
                v-if="row.status === 'pending'"
                type="button"
                @click="withdraw(row)"
              >撤回</button>
            </footer>
          </article>
          <button
            v-if="hasMoreRows"
            type="button"
            class="load-more"
            :disabled="loadingMore"
            @click="loadMoreRows"
          >{{ loadingMore ? '加载中…' : `加载更多（已显示 ${rows.length} / ${total}）` }}</button>
        </template>
      </template>

      <template v-else-if="mode === 'form'">
        <section v-if="formConflict" class="state-card" role="alert" style="margin-bottom: 16px; padding: 12px; border: 1px solid #e6a23c">
          <strong>需要核对报销记录</strong>
          <p>{{ formConflict.message }}</p>
          <p>本地草稿（版本 {{ form.rowVersion == null ? '新建' : form.rowVersion }}）：{{ form.title }} · {{ form.purpose }}</p>
          <ul><li v-for="(row, index) in form.items" :key="'local-' + index">{{ row.expenseType || '未分类' }} · {{ row.description || row.merchantName || '未填写说明' }} · 金额 {{ row.claimedAmount }}</li></ul>
          <template v-if="formConflict.remote">
            <p>最新记录（版本 {{ formConflict.remote.rowVersion }}）：{{ formConflict.remote.title }} · {{ formConflict.remote.purpose }}</p>
            <ul><li v-for="(row, index) in formConflict.remote.items" :key="'remote-' + index">{{ row.expenseType || '未分类' }} · {{ row.description || row.merchantName || '未填写说明' }} · 金额 {{ row.claimedAmount }}</li></ul>
          </template>
          <button type="button" @click="reviewFormConflict">重新获取最新对照</button>
          <button type="button" @click="useLatestConflictRecord">放弃本地修改并加载最新</button>
        </section>

        <section class="smart-start-card">
          <div class="smart-start-copy">
            <span class="smart-start-icon"><i class="el-icon-camera-solid" aria-hidden="true" /></span>
            <div>
              <small>最快的开始方式</small>
              <h2>先拍发票，自动生成报销内容</h2>
              <p>无需先填表或保存，识别后自动带出日期、商户、金额和费用类型。</p>
            </div>
          </div>
          <div class="invoice-upload-actions">
            <button
              type="button"
              :disabled="busy"
              @click="openInvoicePicker('camera')"
            >拍照发票</button>
            <button
              type="button"
              :disabled="busy"
              @click="openInvoicePicker('file')"
            >批量选择</button>
          </div>
          <input
            ref="invoiceCameraInput"
            class="file-input"
            type="file"
            accept="image/*"
            capture="environment"
            @change="invoiceSelected($event, 'camera')"
          >
          <input
            ref="invoiceInput"
            class="file-input"
            type="file"
            accept=".pdf,.png,.jpg,.jpeg,.ofd"
            multiple
            @change="invoiceSelected($event, 'file')"
          >
          <p class="hint">选择门店或仓库后，系统会自动创建草稿；多张发票将依次识别，避免互相阻塞。</p>
          <div v-if="pendingInvoiceFile" class="invoice-upload-status" role="status" aria-live="polite">
            <div>
              <strong>{{ pendingInvoiceFile.name }}</strong>
              <small v-if="uploading">正在上传并识别 {{ uploadProgress }}%</small>
              <small v-else-if="uploadError" class="warning">{{ uploadError }}</small>
            </div>
            <button v-if="uploading" type="button" @click="cancelInvoiceUpload">取消当前</button>
            <button
              v-else-if="uploadError"
              type="button"
              :disabled="uploadQueueActive"
              @click="retryInvoiceUpload"
            >重试</button>
            <span v-if="uploading" class="invoice-upload-progress" aria-hidden="true">
              <i :style="{ width: `${uploadProgress}%` }" />
            </span>
          </div>
        </section>

        <section class="form-card">
          <label>
            <span>报销标题</span>
            <input v-model.trim="form.title" maxlength="120" placeholder="例如：7月客户拜访交通费">
          </label>
          <label>
            <span>报销事由</span>
            <textarea v-model.trim="form.purpose" maxlength="500" rows="3" placeholder="说明费用用途"></textarea>
          </label>
          <div class="section-head">
            <div>
              <small>费用明细</small>
              <h2>{{ moneyCents(formTotalCents) }}</h2>
            </div>
            <button type="button" @click="addItem">
              <i class="el-icon-plus" aria-hidden="true" /> 添加
            </button>
          </div>
          <article
            v-for="(item, index) in form.items"
            :key="index"
            class="item-card"
          >
            <div class="item-order">
              <strong>明细 {{ index + 1 }}</strong>
              <button type="button" aria-label="删除明细" @click="removeItem(index)">删除</button>
            </div>
            <label>
              <span>费用类型</span>
              <select v-model="item.expenseType">
                <option value="">请选择</option>
                <option v-for="type in expenseTypes" :key="type" :value="type">{{ type }}</option>
              </select>
            </label>
            <label>
              <span>发生日期</span>
              <input v-model="item.expenseDate" type="date">
            </label>
            <label>
              <span>商户名称（选填）</span>
              <input v-model.trim="item.merchantName" maxlength="120">
            </label>
            <label>
              <span>费用说明</span>
              <input v-model.trim="item.description" maxlength="300">
            </label>
            <label>
              <span>报销金额</span>
              <input
                v-model="item.claimedAmount"
                type="number"
                min="0"
                step="0.01"
                inputmode="decimal"
              >
            </label>
          </article>
        </section>

        <section class="invoice-card">
          <div class="section-head">
            <div>
              <small>发票附件 · 只需处理异常识别结果</small>
              <h2>{{ form.invoices.length }} 个文件</h2>
            </div>
          </div>
          <article v-for="invoice in form.invoices" :key="invoice.invoiceId" class="invoice-record">
            <div class="invoice-row">
              <div>
                <strong>{{ invoice.originalName }}</strong>
                <small :class="{ warning: ['failed', 'partial', 'unconfigured'].includes(invoice.recognitionStatus) }">
                  {{ recognitionStatusLabel(invoice.recognitionStatus) }} · {{ recognitionEngineLabel(invoice) }}
                </small>
                <small v-if="invoice.sellerName || invoice.invoiceTotalAmount != null">
                  {{ invoice.sellerName || '未识别销售方' }}
                  {{ invoice.invoiceTotalAmount == null ? '' : money(invoice.invoiceTotalAmount) }}
                </small>
                <small v-if="invoice.duplicateStatus === 'warning'" class="warning">与历史发票疑似重复，请核对</small>
              </div>
              <button
                v-if="canPreviewInvoice(invoice)"
                type="button"
                @click="openInvoicePreview(form.reimbursementId, invoice)"
              >预览</button>
              <button type="button" @click="downloadInvoice(invoice)">下载</button>
              <button type="button" class="delete" @click="deleteInvoice(invoice)">删除</button>
            </div>
            <details class="recognition-editor">
              <summary>查看并核对识别字段</summary>
              <label><span>发票号码</span><input v-model.trim="invoice.invoiceNumber" :disabled="busy" maxlength="40"></label>
              <label><span>开票日期</span><input v-model="invoice.invoiceDate" :disabled="busy" type="date"></label>
              <label><span>销售方</span><input v-model.trim="invoice.sellerName" :disabled="busy" maxlength="200"></label>
              <label><span>销售方税号</span><input v-model.trim="invoice.sellerTaxNo" :disabled="busy" maxlength="40"></label>
              <label><span>价税合计</span><input v-model="invoice.invoiceTotalAmount" :disabled="busy" type="number" min="0" step="0.01"></label>
              <label><span>税额</span><input v-model="invoice.taxAmount" :disabled="busy" type="number" min="0" step="0.01"></label>
              <label><span>商品或服务摘要</span><input v-model.trim="invoice.commoditySummary" :disabled="busy" maxlength="500"></label>
              <label class="recognition-target">
                <span>应用到费用明细</span>
                <select v-model="invoice.targetItemKey" :disabled="busy">
                  <option value="">自动选择（优先复用已关联明细）</option>
                  <option
                    v-for="(expenseItem, expenseIndex) in form.items"
                    :key="expenseIndex"
                    :value="String(expenseIndex)"
                  >
                    {{ reimbursementItemOption(expenseItem, expenseIndex) }}
                  </option>
                </select>
              </label>
              <div class="recognition-actions">
                <button
                  type="button"
                  :disabled="busy || !recognitionAvailability.cloudConfigured"
                  @click="rerunRecognition(invoice, 'cloud')"
                >云端识别</button>
                <button
                  type="button"
                  :disabled="busy || !recognitionAvailability.localAvailable"
                  @click="rerunRecognition(invoice, 'local')"
                >本地识别</button>
                <button type="button" :disabled="busy" @click="applyRecognition(invoice)">应用明细</button>
                <button
                  type="button"
                  :disabled="busy"
                  @click="saveInvoiceRecognition(invoice)"
                >保存核对</button>
              </div>
            </details>
          </article>
        </section>

        <section class="readiness-card">
          <div class="readiness-head">
            <div>
              <small>提交前检查</small>
              <h2>{{ submissionReadiness.ready ? '内容已齐全' : `还差 ${submissionReadiness.blockingIssues.length} 项` }}</h2>
            </div>
            <span :class="['status-chip', submissionReadiness.ready ? 'success' : 'warning']">
              {{ submissionReadiness.ready ? '可提交' : '待补充' }}
            </span>
          </div>
          <ul class="readiness-list">
            <li
              v-for="check in submissionReadiness.checks"
              :key="check.key"
              :class="{ ready: check.ready }"
            >
              <i :class="check.ready ? 'el-icon-circle-check' : 'el-icon-warning-outline'" />
              {{ check.label }}
            </li>
          </ul>
          <p v-if="submissionReadiness.warnings.length" class="readiness-warning">
            {{ submissionReadiness.warnings.join('；') }}
          </p>
        </section>

        <div class="form-actions">
          <button type="button" :disabled="busy || !!formConflict" @click="saveDraftOnly">
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
          <button
            type="button"
            class="primary"
            :disabled="busy || !!formConflict || !submissionAvailable"
            @click="submitForm"
          >{{ submitting ? '提交中…' : (submissionReadiness.ready ? '确认无误并提交' : `提交前还差 ${submissionReadiness.blockingIssues.length} 项`) }}</button>
        </div>
      </template>

      <template v-else-if="mode === 'detail' && detail">
        <section class="detail-card">
          <div class="claim-head">
            <div>
              <small>{{ detail.reimbursementNo }}</small>
              <h2>{{ detail.title }}</h2>
            </div>
            <span :class="['status-chip', statusTone(detail.status)]">
              {{ statusLabel(detail.status) }}
            </span>
          </div>
          <dl>
            <div><dt>申请人</dt><dd>{{ detail.applicantNickName || detail.applicantName || '-' }}</dd></div>
            <div><dt>申请部门</dt><dd>{{ detail.applicantDeptName || '-' }}</dd></div>
            <div><dt>归属店铺</dt><dd>{{ detail.shopDeptName || '-' }}</dd></div>
            <div><dt>报销总额</dt><dd class="amount">{{ money(detail.totalAmount) }}</dd></div>
          </dl>
          <div class="purpose">
            <small>报销事由</small>
            <p>{{ detail.purpose || '-' }}</p>
          </div>
        </section>

        <section class="detail-card">
          <h2>费用明细</h2>
          <article v-for="item in detail.items" :key="item.itemId" class="detail-row">
            <div>
              <strong>{{ item.expenseType }} · {{ item.description }}</strong>
              <small>{{ item.expenseDate }} {{ item.merchantName || '' }}</small>
            </div>
            <span>{{ money(item.claimedAmount) }}</span>
          </article>
        </section>

        <section class="detail-card">
          <h2>发票附件</h2>
          <article v-for="invoice in detail.invoices" :key="invoice.invoiceId" class="invoice-row">
            <div>
              <strong>{{ invoice.originalName }}</strong>
              <small>{{ recognitionStatusLabel(invoice.recognitionStatus) }} · {{ recognitionEngineLabel(invoice) }}</small>
              <small>{{ invoice.invoiceNumber || invoice.sellerName || fileSize(invoice.fileSize) }}</small>
              <small v-if="invoice.duplicateStatus === 'warning'" class="warning">疑似历史重复</small>
            </div>
            <button
              v-if="canPreviewInvoice(invoice)"
              type="button"
              @click="openInvoicePreview(detail.reimbursementId, invoice)"
            >预览</button>
            <button type="button" @click="downloadDetailInvoice(invoice)">下载</button>
          </article>
        </section>

        <section v-if="approvalDetail" class="detail-card">
          <h2>审批轨迹</h2>
          <ol class="timeline">
            <li v-for="task in tasks" :key="task.taskId">
              <span :class="['dot', statusTone(task.taskStatus)]" />
              <div>
                <strong>{{ task.nodeName || '审批节点' }}</strong>
                <small>{{ approvalStatusLabel(task.taskStatus) }} · {{ task.completedTime || '等待处理' }}</small>
              </div>
            </li>
          </ol>
        </section>

        <div v-if="editable(detail) && !approvalTaskId" class="single-action">
          <button type="button" @click="openForm(detail.reimbursementId)">编辑并重新提交</button>
        </div>
      </template>
    </main>

    <button
      v-if="mode === 'list'"
      type="button"
      class="floating-add"
      aria-label="新建报销"
      @click="openForm()"
    ><i class="el-icon-plus" aria-hidden="true" /></button>

    <approval-command-recovery :message="approvalError" :reason="approvalReason" :unknown="approvalUnknown" :busy="acting || checkingApproval" :checking="checkingApproval" :checked="approvalChecked" @check="checkApprovalCommand" @retry="retryApprovalCommand" />
    <footer v-if="mode === 'detail' && canAct" class="approval-bar">
      <button type="button" :disabled="busy || approvalUnknown || checkingApproval" @click="approvalAction('return')">退回</button>
      <button type="button" class="reject" :disabled="busy || approvalUnknown || checkingApproval" @click="approvalAction('reject')">拒绝</button>
      <button type="button" class="approve" :disabled="busy || approvalUnknown || checkingApproval" @click="approvalAction('approve')">同意</button>
    </footer>

    <div
      v-if="invoicePreview.open"
      class="invoice-preview"
      role="dialog"
      aria-modal="true"
      :aria-label="invoicePreview.name || '发票预览'"
    >
      <section class="invoice-preview-card">
        <header>
          <strong>{{ invoicePreview.name }}</strong>
          <button type="button" @click="closeInvoicePreview">关闭</button>
        </header>
        <div class="invoice-preview-body">
          <div v-if="invoicePreview.loading" class="preview-state" role="status">正在准备预览…</div>
          <div v-else-if="invoicePreview.error" class="preview-state error" role="alert">
            {{ invoicePreview.error }}
          </div>
          <img
            v-else-if="invoicePreview.kind === 'image' && invoicePreview.url"
            :src="invoicePreview.url"
            :alt="invoicePreview.name"
          >
          <iframe
            v-else-if="invoicePreview.kind === 'pdf' && invoicePreview.url"
            :src="invoicePreview.url"
            title="发票文件预览"
          />
          <div v-else class="preview-state">此格式暂不支持在线预览，请下载后查看。</div>
        </div>
        <footer>
          <button type="button" @click="closeInvoicePreview">关闭</button>
          <button type="button" @click="downloadPreviewInvoice">下载文件</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script>
import { createReimbursementWithdrawRecovery } from "@/mixins/reimbursementWithdrawRecovery"
import { createApprovalCommandRecovery } from "@/mixins/approvalCommandRecovery"
import ApprovalCommandRecovery from "@/components/ApprovalCommandRecovery"
import {
  deleteReimbursementInvoice,
  getReimbursement,
  getReimbursementAvailability,
  getReimbursementInvoice,
  listMyReimbursements,
  recognizeReimbursementInvoice,
  saveReimbursement,
  submitReimbursement,
  updateReimbursementInvoiceRecognition,
  uploadReimbursementInvoice,
  withdrawReimbursement
} from "@/api/oa/reimbursement"
import { getApprovalInstance } from "@/api/approval/monitor"
import {
  approveApprovalTask,
  rejectApprovalTask,
  returnApprovalTask
} from "@/api/approval/task"
import {
  statusLabel as approvalStatusLabel
} from "@/views/approval/manage/components/approvalUi"
import { blobValidate } from "@/utils/common"
import { getSelectedDeptId, hasValidatedSelectedDeptContext } from "@/utils/shopContext"

const {
  formatMoney,
  formatMoneyCents,
  mergeReimbursementRows,
  normalizeMoneyInput,
  parseMoneyCents,
  reimbursementErrorMessage,
  serializeReimbursementForm,
  stableApprovalRequestId,
  sumMoneyCents,
  validateReimbursementSubmission
} = require("./mobileReimbursementState")
const {
  applyInvoiceToForm,
  mergeReimbursementInvoiceDeletion,
  cloneReimbursementDraft, captureReimbursementSave, mergeReimbursementSavedDraft,
  mergeReimbursementReadDraft, isReimbursementVersionConflict, reimbursementDeleteMatches,
  reimbursementReadiness
} = require("@/utils/reimbursementSmartFill")

const INVOICE_UPLOAD_CONTEXT_MESSAGE = "请先选择门店或仓库，再上传发票"
const SILENT_READ = { silentError: true }
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { approvalFailureKind } = require("@/utils/approvalCommandRecovery")

function dateText() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`
}

function item() {
  return {
    expenseType: "",
    expenseDate: dateText(),
    merchantName: "",
    description: "",
    claimedAmount: undefined
  }
}

function form() {
  return {
    reimbursementId: undefined,
    rowVersion: undefined,
    title: "",
    purpose: "",
    items: [item()],
    invoices: []
  }
}

function scalar(value) {
  const candidate = Array.isArray(value) ? value[0] : value
  return candidate === undefined || candidate === null
    ? "" : String(candidate).trim()
}

function emptyInvoicePreview() {
  return {
    open: false,
    loading: false,
    error: "",
    url: "",
    kind: "unsupported",
    reimbursementId: "",
    invoice: null,
    name: ""
  }
}

export default {
  components: { ApprovalCommandRecovery },
  mixins: [createReimbursementWithdrawRecovery({ refresh: vm => { vm.$store.dispatch("todo/invalidateAfterMutation").catch(() => {}); return vm.loadList(true) } }), createApprovalCommandRecovery({
    target: vm => vm.detail && ({ businessCode: 'OA_REIMBURSEMENT', businessId: vm.detail.reimbursementId, taskId: vm.approvalTaskId, instanceId: vm.detail.approvalInstanceId, approvalRound: vm.detail.approvalRound }),
    visible: vm => vm.mode === 'detail' && !vm.pageInactive, canAct: vm => vm.canAct, loading: 'acting',
    success: (vm, command) => { vm.$store.dispatch('todo/invalidateAfterMutation').catch(() => {}); return vm.openDetail(command.businessId) }
  })],
  name: "MobileOaReimbursement",
  data() {
    return {
      mode: "list",
      loading: false,
      loadingMore: false,
      saving: false,
      formEpoch: 0,
      formReadSeq: 0,
      detailEpoch: 0,
      listReadSeq: 0,
      pageReadSeq: 0,
      pageInactive: false,
      deptListenerBound: false,
      submitting: false,
      submitIntent: null, submitUnknown: false, submitChecked: false, checkingSubmission: false, submitRecoveryMessage: "",
      uploading: false,
      uploadQueueActive: false,
      uploadQueueToken: 0,
      uploadBatchTotal: 0,
      uploadBatchCompleted: 0,
      uploadBatchFailures: [],
      uploadProgress: 0,
      uploadError: "",
      pendingInvoiceFile: null,
      pendingInvoiceKnownIds: [],
      checkingUploadResult: false,
      uploadController: null,
      recognizingInvoiceId: "",
      savingRecognitionInvoiceId: "",
      deletingInvoiceId: "",
      acting: false,
      error: "",
      rows: [],
      total: 0,
      listQuery: {
        pageNum: 1,
        pageSize: 20,
        keyword: "",
        status: ""
      },
      form: form(),
      formBaseline: "",
      formServerSnapshot: null, formConflict: null, saveInFlight: false, saveOperationSeq: 0, deleteOperationSeq: 0,
      detail: null,
      approvalDetail: null,
      invoicePreview: emptyInvoicePreview(),
      invoicePreviewSequence: 0,
      submissionAvailable: false,
      recognitionAvailability: {
        cloudConfigured: false,
        cloudMessage: "云端OCR密钥未配置",
        localAvailable: false
      },
      expenseTypes: ["交通费", "差旅费", "住宿费", "餐饮费", "办公费", "招待费", "通讯费", "其他"]
    }
  },
  computed: {
    routeQuery() {
      return this.$route && this.$route.query || {}
    },
    routeReimbursementId() {
      return scalar(this.routeQuery.reimbursementId ||
        this.routeQuery.businessId)
    },
    approvalTaskId() {
      return scalar(this.routeQuery.approvalTaskId)
    },
    pageTitle() {
      if (this.mode === "form") return this.form.reimbursementId ? "编辑报销" : "新建报销"
      if (this.mode === "detail") return this.approvalTaskId ? "报销审批" : "报销详情"
      return "我的报销"
    },
    busy() {
      return this.saveInFlight || this.saving || this.submitting || this.submitUnknown || this.checkingSubmission || this.uploadWorkActive ||
        this.acting ||
        !!this.deletingInvoiceId ||
        !!this.recognizingInvoiceId || !!this.savingRecognitionInvoiceId
    },
    uploadWorkActive() {
      return this.uploadQueueActive || this.checkingUploadResult ||
        this.uploading
    },
    formTotalCents() {
      return sumMoneyCents(this.form.items.map(value => value.claimedAmount))
    },
    submissionReadiness() {
      return reimbursementReadiness(this.form)
    },
    hasMoreRows() {
      return this.rows.length < this.total
    },
    isFormDirty() {
      return this.mode === "form" && Boolean(this.formBaseline) &&
        serializeReimbursementForm(this.form) !== this.formBaseline
    },
    tasks() {
      const source = this.approvalDetail || {}
      return Array.isArray(source.tasks)
        ? source.tasks
        : Array.isArray(source.taskList) ? source.taskList : []
    },
    instance() {
      return this.approvalDetail && this.approvalDetail.instance || {}
    },
    canAct() {
      if (!this.detail || this.loading || this.error) return false
      if (this.routeReimbursementId && String(this.routeReimbursementId) !== String(this.detail.reimbursementId)) return false
      if (String(this.instance.instanceId) !== String(this.detail.approvalInstanceId) || String(this.instance.businessId) !== String(this.detail.reimbursementId) || this.instance.businessCode !== "OA_REIMBURSEMENT") return false
      const task = this.tasks.find(value =>
        String(value.taskId || value.id) === String(this.approvalTaskId))
      return !!task &&
        String(task.taskStatus || task.status).toUpperCase() === "PENDING" &&
        String(this.instance.status || "").toUpperCase() === "RUNNING"
    }
  },
  watch: {
    "$route.query": {
      deep: true,
      handler() {
        this.reload()
      }
    }
  },
  created() {
    this.bindDeptListener()
    this.loadAvailability()
    this.reload()
  },
  mounted() {
    if (typeof window !== "undefined") {
      window.addEventListener("beforeunload", this.handleBeforeUnload)
    }
  },
  activated() {
    if (!this.pageInactive) return
    this.pageInactive = false
    this.bindDeptListener()
    // A kept-alive form may contain an unsaved draft. Refresh its remote version
    // with the existing merge policy; never reopen/reset the form on activation.
    if (this.mode === "form") {
      if (this.form.reimbursementId) {
        const pending = this.refreshForm(true)
        const token = { epoch: this.formEpoch, readSeq: this.formReadSeq,
          reimbursementId: this.formReadId(this.form.reimbursementId), deptId: this.liveDeptId() }
        pending.catch(error => {
          if (this.isCurrentFormRead(token)) this.$modal.msgError(reimbursementErrorMessage(error))
        })
      }
      return
    }
    this.reload()
  },
  deactivated() {
    this.pageInactive = true
    this.invalidatePendingFormReads()
  },
  beforeDestroy() {
    this.pageInactive = true
    this.formEpoch += 1
    this.invalidatePendingFormReads()
    this.unbindDeptListener()
    if (typeof window !== "undefined") {
      window.removeEventListener("beforeunload", this.handleBeforeUnload)
    }
    this.cancelInvoiceBatch(false)
    this.releaseInvoicePreviewUrl()
  },
  beforeRouteLeave(to, from, next) {
    this.confirmSafeLeave()
      .then(() => next())
      .catch(() => next(false))
  },
  beforeRouteUpdate(to, from, next) {
    this.confirmSafeLeave()
      .then(() => next())
      .catch(() => next(false))
  },
  methods: {
    approvalStatusLabel,
    statusLabel(status) {
      return {
        draft: "草稿",
        submitting: "提交中",
        pending: "审批中",
        approved: "已通过",
        returned: "已退回",
        rejected: "已拒绝",
        withdrawn: "已撤回",
        terminated: "已终止"
      }[status] || status || "-"
    },
    statusTone(status) {
      const value = String(status || "").toUpperCase()
      if (["APPROVED", "SUCCESS"].includes(value)) return "success"
      if (["PENDING", "RUNNING", "SUBMITTING", "RETURNED"].includes(value)) return "warning"
      if (["REJECTED", "TERMINATED", "FAILED"].includes(value)) return "danger"
      return "info"
    },
    recognitionStatusLabel(status) {
      return {
        pending: "等待识别",
        succeeded: "识别成功",
        partial: "部分识别",
        failed: "识别失败",
        corrected: "已人工核对",
        unconfigured: "引擎未配置"
      }[status] || "等待识别"
    },
    recognitionEngineLabel(invoice) {
      if (!invoice) return "-"
      if (invoice.recognitionEngine === "manual") return "人工核对"
      if (invoice.recognitionEngine === "cloud") return "云端OCR"
      if (invoice.recognitionEngine === "local") return "本地OCR"
      return "自动识别"
    },
    reimbursementItemOption(expenseItem, index) {
      const source = expenseItem || {}
      const summary = source.merchantName || source.description ||
        source.expenseType || "未填写"
      return `明细 ${index + 1} · ${summary}`
    },
    money(value) {
      return formatMoney(value)
    },
    moneyCents(value) {
      return formatMoneyCents(value)
    },
    fileSize(value) {
      const bytes = Number(value)
      if (!Number.isFinite(bytes)) return "-"
      return bytes < 1024 * 1024
        ? `${(bytes / 1024).toFixed(1)} KB`
        : `${(bytes / 1024 / 1024).toFixed(1)} MB`
    },
    editable(value) {
      return !!value && ["draft", "returned", "withdrawn"].includes(value.status)
    },
    loadAvailability() {
      return getReimbursementAvailability().then(response => {
        this.submissionAvailable = !!(response.data && response.data.enabled)
        this.recognitionAvailability = {
          ...this.recognitionAvailability,
          ...(response.data && response.data.recognition)
        }
      }).catch(() => {
        this.submissionAvailable = false
      })
    },
    liveDeptId() {
      return getSelectedDeptId()
    },
    sameDeptId(deptId) {
      return String(this.liveDeptId() || "") === String(deptId || "")
    },
    formReadId(value) {
      return value == null || value === "" ? "" : String(value)
    },
    beginFormRead(reimbursementId) {
      this.formReadSeq += 1
      return {
        epoch: this.formEpoch,
        readSeq: this.formReadSeq,
        reimbursementId: this.formReadId(reimbursementId),
        deptId: this.liveDeptId()
      }
    },
    isCurrentFormRead(token) {
      if (this.pageInactive || this.mode !== "form") return false
      if (!token || token.epoch !== this.formEpoch || token.readSeq !== this.formReadSeq) return false
      if (!this.sameDeptId(token.deptId)) return false
      return this.formReadId(this.form && this.form.reimbursementId) === token.reimbursementId
    },
    isCurrentDetailRead(epoch, reimbursementId, deptId) {
      return !this.pageInactive
        && this.mode === "detail"
        && epoch === this.detailEpoch
        && this.formReadId(this.detail && this.detail.reimbursementId || reimbursementId) === this.formReadId(reimbursementId)
        && this.sameDeptId(deptId)
    },
    isCurrentListRead(listSeq, deptId) {
      return !this.pageInactive && this.mode === "list" && listSeq === this.listReadSeq && this.sameDeptId(deptId)
    },
    invalidatePendingFormReads() {
      this.pageReadSeq += 1
      this.formReadSeq += 1
      this.detailEpoch += 1
      this.listReadSeq += 1
      this.loading = false
      this.loadingMore = false
    },
    bindDeptListener() {
      if (typeof window === "undefined" || this.deptListenerBound) return
      window.addEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = true
    },
    unbindDeptListener() {
      if (typeof window === "undefined" || !this.deptListenerBound) return
      window.removeEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = false
    },
    handleDeptChanged() {
      this.invalidatePendingFormReads()
    },
    refreshCurrent() {
      if (!this.isFormDirty) return this.reload()
      return this.confirmDiscardChanges()
        .then(() => this.reload())
        .catch(() => undefined)
    },
    reload() {
      if (this.pageInactive) return Promise.resolve()
      this.error = ""
      if (/^\d+$/.test(this.routeReimbursementId)) {
        return this.openDetail(this.routeReimbursementId)
      }
      if (this.mode === "form" && this.form.reimbursementId) {
        return this.openForm(this.form.reimbursementId)
      }
      return this.loadList(true)
    },
    loadList(reset = true) {
      if (this.pageInactive) return Promise.resolve()
      this.mode = "list"
      this.formReadSeq += 1
      this.detailEpoch += 1
      this.listReadSeq += 1
      const listSeq = this.listReadSeq
      const deptId = this.liveDeptId()
      if (reset) {
        this.listQuery.pageNum = 1
        this.loading = true
      } else {
        this.loadingMore = true
      }
      const requestedPage = this.listQuery.pageNum
      return listMyReimbursements({
        pageNum: requestedPage,
        pageSize: this.listQuery.pageSize,
        title: this.listQuery.keyword || undefined,
        status: this.listQuery.status || undefined
      }, SILENT_READ)
        .then(response => {
          if (!this.isCurrentListRead(listSeq, deptId)) return
          const incoming = Array.isArray(response.rows) ? response.rows : []
          this.rows = reset
            ? incoming
            : mergeReimbursementRows(this.rows, incoming)
          this.total = response.total || 0
        }).catch(error => {
          if (!this.isCurrentListRead(listSeq, deptId)) return
          if (reset) {
            this.error = reimbursementErrorMessage(error)
          } else {
            this.listQuery.pageNum = Math.max(1, requestedPage - 1)
            this.$modal.msgError(reimbursementErrorMessage(error))
          }
        }).finally(() => {
          if (listSeq !== this.listReadSeq) return
          this.loading = false
          this.loadingMore = false
        })
    },
    applyListFilters() {
      if (this.loading || this.loadingMore) return
      this.loadList(true)
    },
    loadMoreRows() {
      if (this.loading || this.loadingMore || !this.hasMoreRows) return
      this.listQuery.pageNum += 1
      this.loadList(false)
    },
    normalizeForm(source) {
      const value = source || {}
      const items = Array.isArray(value.items)
        ? value.items.map(row => ({
          ...row,
          claimedAmount: normalizeMoneyInput(row.claimedAmount)
        })) : [item()]
      return {
        ...value,
        items,
        invoices: Array.isArray(value.invoices)
          ? value.invoices.map(invoice => ({
            ...invoice,
            targetItemKey: invoice.targetItemKey == null
              ? "" : String(invoice.targetItemKey),
            invoiceTotalAmount: normalizeMoneyInput(
              invoice.invoiceTotalAmount
            ),
            taxAmount: normalizeMoneyInput(invoice.taxAmount),
            amountWithoutTax: normalizeMoneyInput(invoice.amountWithoutTax)
          })) : []
      }
    },
    resetFormRecovery() {
      this.submissionScope().invalidate(); this.submitIntent = null; this.submitUnknown = false; this.submitChecked = false; this.checkingSubmission = false; this.submitRecoveryMessage = ""

      this.formConflict = null
      this.formServerSnapshot = null
      this.saveInFlight = false
      this.saveOperationSeq += 1
      this.saving = false
      this.submitting = false
      this.deletingInvoiceId = ""
      this.deleteOperationSeq += 1
      this.uploadQueueToken += 1
      this.uploadQueueActive = false
      this.uploading = false
      this.uploadController = null
      this.pendingInvoiceFile = null
      this.pendingInvoiceKnownIds = []
      this.checkingUploadResult = false
    },
    captureFormOperation() {
      return { epoch: this.formEpoch, page: this.pageReadSeq, reimbursementId: this.form.reimbursementId }
    },
    isCurrentFormOperation(operation) {
      return !this.pageInactive && this.mode === "form" && operation.epoch === this.formEpoch && operation.page === this.pageReadSeq &&
        (operation.reimbursementId == null || String(operation.reimbursementId) === String(this.form.reimbursementId))
    },
    ensureFormWritable() {
      if (this.submitUnknown || this.checkingSubmission) { this.$modal.msgWarning("请先核对上次提交结果，再修改草稿"); return false }

      if (this.pageInactive || this.mode !== "form" || this.loading) { this.$modal.msgWarning("请等待当前报销记录加载完成"); return false }
      if (this.form.status && !this.editable(this.form)) { this.$modal.msgWarning("当前记录已不能编辑，请查看最新状态"); return false }
      if (!this.formConflict) return true
      this.$modal.msgWarning("草稿有待核对的变化，请先核对最新记录再保存或提交")
      return false
    },
    setFormConflict(kind, message, remote, extra = {}) {
      this.formConflict = { kind, message, remote: remote ? cloneReimbursementDraft(remote) : null,
        reimbursementId: remote && remote.reimbursementId || this.form.reimbursementId, ...extra }
    },
    async reviewFormConflict() {
      const conflict = this.formConflict
      if (!conflict) return
      const id = conflict.reimbursementId
      if (!id) { this.$modal.msgWarning("新建结果尚不明确，请先在报销记录列表核对，避免重复新建"); return }
      let token
      const operation = this.captureFormOperation()
      try {
        token = this.beginFormRead(this.form.reimbursementId)
        const response = await getReimbursement(id, SILENT_READ)
        if (this.formConflict !== conflict || !this.isCurrentFormRead(token)) return
        const remote = this.normalizeForm(response.data)
        if (conflict.kind === "deleteUnknown" && reimbursementDeleteMatches(conflict.baseline, remote, conflict.invoice)) {
          this.form = mergeReimbursementInvoiceDeletion(this.form, remote, conflict.invoice)
          this.formBaseline = serializeReimbursementForm(remote)
          this.formServerSnapshot = cloneReimbursementDraft(remote)
          this.formConflict = null
          this.$modal.msgSuccess("已核对：发票及关联费用已删除")
        } else this.formConflict = { ...conflict, remote: cloneReimbursementDraft(remote) }
      } catch (error) {
        if (this.formConflict === conflict && this.isCurrentFormOperation(operation) && (!token || this.isCurrentFormRead(token))) this.$modal.msgWarning("最新记录暂时无法读取，草稿和原版本已保留，请稍后重新核对")
      }
    },
    async useLatestConflictRecord() {
      const conflict = this.formConflict
      if (!conflict) return
      if (!conflict.reimbursementId) { this.$modal.msgWarning("请先在报销记录列表找到已保存记录再打开，避免重复新建"); return }
      try { await this.$modal.confirm("将放弃当前未保存修改并加载最新记录，确认已核对？", "加载最新记录") } catch (_) { return }
      if (this.formConflict !== conflict) return
      const requestSnapshot = serializeReimbursementForm(this.form)
      const token = this.beginFormRead(this.form.reimbursementId)
      try {
        const response = await getReimbursement(conflict.reimbursementId, SILENT_READ)
        if (this.formConflict !== conflict || !this.isCurrentFormRead(token)) return
        if (serializeReimbursementForm(this.form) !== requestSnapshot) {
          this.formConflict = { ...conflict, remote: cloneReimbursementDraft(response.data) }
          this.$modal.msgWarning("加载期间又有新修改，草稿已保留，请重新核对后选择")
          return
        }
        this.form = this.normalizeForm(response.data)
        this.captureFormBaseline()
        this.formConflict = null
      } catch (error) {
        if (this.formConflict === conflict && this.isCurrentFormRead(token)) this.$modal.msgError("最新记录加载失败，原草稿仍保留")
      }
    },
    async persistFormDraft() {
      if (!this.ensureFormWritable()) throw new Error("草稿尚未完成冲突核对")
      if (this.saveInFlight) throw new Error("草稿正在保存，请稍后重试")
      const operation = this.captureFormOperation()
      const capture = captureReimbursementSave(this.form)
      const saveSeq = ++this.saveOperationSeq
      this.saveInFlight = true
      this.formReadSeq += 1
      try {
        const response = await saveReimbursement(cloneReimbursementDraft(capture.payload))
        if (!this.isCurrentFormOperation(operation)) throw new Error("STALE_FORM_OPERATION")
        const remote = this.normalizeForm(response.data)
        try {
          this.form = mergeReimbursementSavedDraft(this.form, remote, capture)
        } catch (error) {
          this.setFormConflict("saveIdentity", error.message, remote)
          throw error
        }
        this.formBaseline = serializeReimbursementForm(remote)
        this.formServerSnapshot = cloneReimbursementDraft(remote)

        return cloneReimbursementDraft(remote)
      } catch (error) {
        if (this.isCurrentFormOperation(operation) && !this.formConflict) {
          if (isReimbursementVersionConflict(error)) {
            this.setFormConflict("version", "报销记录已被其他操作修改，原草稿和版本已保留，请核对最新记录", null)
            await this.reviewFormConflict()
          } else if (!error.response && /timeout|network|ECONN|unknown|网络|超时/i.test(String(error.code || "") + " " + String(error.message || ""))) {
            this.setFormConflict("saveUnknown", "保存结果尚不明确，原草稿已保留，请先核对，避免重复保存", null)
          }
        }
        throw error
      } finally {
        if (saveSeq === this.saveOperationSeq) this.saveInFlight = false
      }
    },
    captureFormBaseline() {
      this.formBaseline = serializeReimbursementForm(this.form)
      this.formServerSnapshot = cloneReimbursementDraft(this.form)
    },
    openForm(id) {
      if (this.pageInactive) return
      this.formEpoch += 1
      this.resetFormRecovery()
      this.listReadSeq += 1
      this.detailEpoch += 1
      this.error = ""
      this.mode = "form"
      if (!id) {
        this.loading = false
        this.form = form()
        this.beginFormRead("")
        this.captureFormBaseline()
        return
      }
      const token = this.beginFormRead(id)
      this.form = Object.assign(form(), { reimbursementId: id })
      this.captureFormBaseline()
      this.loading = true
      return getReimbursement(id, SILENT_READ).then(response => {
        if (!this.isCurrentFormRead(token)) return
        this.form = this.normalizeForm(response.data)
        this.captureFormBaseline()
      }).catch(error => {
        if (!this.isCurrentFormRead(token)) return
        this.error = reimbursementErrorMessage(error)
      }).finally(() => {
        if (this.isCurrentFormRead(token)) this.loading = false
      })
    },
    openDetail(id) {
      if (this.pageInactive) return
      this.formEpoch += 1
      this.formReadSeq += 1
      this.loading = false
      this.listReadSeq += 1
      this.detailEpoch += 1
      const epoch = this.detailEpoch
      const deptId = this.liveDeptId()
      const actor = String(this.$store.getters.id), session = this.$store.state && this.$store.state.user && this.$store.state.user.sessionRevision
      const current = () => this.isCurrentDetailRead(epoch, id, deptId) && actor === String(this.$store.getters.id) && session === (this.$store.state && this.$store.state.user && this.$store.state.user.sessionRevision)
      this.mode = "detail"
      this.error = ""
      this.loading = true
      this.detail = null
      this.approvalDetail = null
      return getReimbursement(id, SILENT_READ).then(response => {
        if (!current()) return
        const record = response.data || {}
        if (String(record.reimbursementId) !== String(id)) throw Error("详情与当前报销目标不一致，请重新打开待办")
        this.detail = record
        if (!this.detail.approvalInstanceId) return undefined
        return getApprovalInstance(this.detail.approvalInstanceId, {
          silentError: true
        })
      }).then(response => {
        if (!response || !current()) return
        const approval = response.data && response.data.data !== undefined ? response.data.data : response.data || {}
        const instance = approval.instance || {}
        if (String(instance.instanceId) !== String(this.detail.approvalInstanceId) || String(instance.businessId) !== String(id) || instance.businessCode !== "OA_REIMBURSEMENT") throw Error("审批实例与当前报销单不一致，请返回待办核对")
        this.approvalDetail = approval
      }).catch(error => {
        if (!current()) return
        this.error = reimbursementErrorMessage(error)
      }).finally(() => {
        if (epoch === this.detailEpoch && this.mode === "detail" && !this.pageInactive) this.loading = false
      })
    },
    addItem() {
      if (this.form.items.length < 100) this.form.items.push(item())
    },
    removeItem(index) {
      if (this.form.items.length === 1) {
        this.$modal.msgWarning("请至少保留一条费用明细")
        return
      }
      this.form.items.splice(index, 1)
    },
    persist(requireComplete = false) {
      if (!this.ensureFormWritable()) return Promise.reject(new Error("草稿尚未完成冲突核对"))
      const validation = requireComplete ? validateReimbursementSubmission(this.form) : ""
      if (validation) return Promise.reject(new Error(validation))
      return this.persistFormDraft()
    },
    saveDraftOnly() {
      if (this.busy || !this.ensureFormWritable()) return Promise.resolve()
      const operation = this.captureFormOperation()
      this.saving = true
      return this.persist(false).then(() => {
        if (this.isCurrentFormOperation(operation)) this.$modal.msgSuccess(this.isFormDirty ? "草稿已保存，期间新增修改尚未保存" : "草稿已保存，可继续补充后再提交")
      }).catch(error => {
        if (this.isCurrentFormOperation(operation)) this.$modal.msgError(reimbursementErrorMessage(error))
      }).finally(() => {
        if (operation.epoch === this.formEpoch) this.saving = false
      })
    },
    submissionScope() {
      if (!this._submissionScope) this._submissionScope = createUiOperationScope(() => ({ actor: this.$store.getters.id,
        session: this.$store.state && this.$store.state.user && this.$store.state.user.sessionRevision,
        dept: this.liveDeptId(), epoch: this.formEpoch, inactive: this.pageInactive, mode: this.mode }))
      return this._submissionScope
    },
    submitForm() {
      if (this.submitUnknown) return this.checkSubmissionResult()
      if (this.busy || !this.ensureFormWritable()) return Promise.resolve()
      if (!this.submissionAvailable) { this.$modal.msgWarning("报销审批当前未开放"); return Promise.resolve() }
      if (!this.submissionReadiness.ready) { this.$modal.msgWarning(this.submissionReadiness.blockingIssues[0]); return Promise.resolve() }
      const validation = validateReimbursementSubmission(this.form)
      if (validation) { this.$modal.msgWarning(validation); return Promise.resolve() }
      const operation = this.captureFormOperation(), scope = this.submissionScope(), token = scope.begin("submit")
      const current = () => scope.isCurrent(token) && this.isCurrentFormOperation(operation)
      this.submitting = true; this.submitRecoveryMessage = ""
      let draftSaved = false
      return this.persist(true).then(saved => {
        if (!current()) return
        draftSaved = true
        if (this.isFormDirty) throw new Error("保存期间有新的未保存修改，已保留草稿，请核对后再次提交")
        if (!this.ensureFormWritable()) throw new Error("请先完成草稿核对")
        const baseRound = Number(saved.approvalRound || 0)
        if (!saved.reimbursementId || !Number.isSafeInteger(baseRound) || baseRound < 0 || saved.rowVersion == null) throw Error("保存回包缺少审批轮次或版本，请核对草稿后再提交")
        const payload = cloneReimbursementDraft(saved)
        payload.expectedBaseRound = baseRound; payload.expectedVersion = saved.rowVersion
        const intent = Object.freeze({ reimbursementId: String(saved.reimbursementId), baseRound, version: String(saved.rowVersion), payload: Object.freeze(payload) })
        this.submitIntent = intent
        return this.sendSubmissionIntent(intent, token)
      }).catch(error => {
        if (!current()) return
        const message = reimbursementErrorMessage(error)
        if (draftSaved) this.$modal.msgWarning(`草稿已保存，本次尚未发出提交请求：${message}`)
        else this.$modal.msgError(message)
      }).finally(() => { if (current()) this.submitting = false })
    },
    sendSubmissionIntent(intent, token) {
      const scope = this.submissionScope(), current = () => scope.isCurrent(token) && this.submitIntent === intent
      return submitReimbursement(intent.payload).then(response => {
        if (!current()) return
        const record = response && response.data
        if (!record || String(record.reimbursementId) !== intent.reimbursementId || Number(record.approvalRound) !== intent.baseRound + 1 || !["pending", "submitting"].includes(record.status)) {
          this.submitUnknown = true; this.submitRecoveryMessage = "提交回包尚不能确认原审批轮次，请核对结果"; return
        }
        this.applySubmissionResult(record, intent)
      }).catch(error => {
        if (!current()) return
        const kind = approvalFailureKind(error)
        if (kind === "handled") return
        this.submitUnknown = kind === "unknown"; this.submitChecked = false
        this.submitRecoveryMessage = this.submitUnknown ? "草稿已保存，提交结果待核对；请求可能已经受理，不会自动重新保存或提交。" : "草稿已保存，审批提交被拒绝：" + reimbursementErrorMessage(error)
        if (!this.submitUnknown) this.submitIntent = null
      })
    },
    applySubmissionResult(record, intent) {
      if (this.submitIntent !== intent) return
      this.submitUnknown = false; this.submitChecked = false; this.submitIntent = null; this.submitting = false; this.checkingSubmission = false
      this.submitRecoveryMessage = ""
      this.$modal.msgSuccess(record.status === "submitting" ? "报销申请已受理，审批正在发起，请稍后查看" : "报销申请当前已进入审批")
      this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
      return this.openDetail(intent.reimbursementId)
    },
    checkSubmissionResult() {
      const intent = this.submitIntent
      if (!intent || !this.submitUnknown || this.checkingSubmission || this.submitting) return Promise.resolve()
      const scope = this.submissionScope(), token = scope.begin("submit-check"), current = () => scope.isCurrent(token) && this.submitIntent === intent
      this.checkingSubmission = true; this.submitChecked = false
      return getReimbursement(intent.reimbursementId, SILENT_READ).then(response => {
        if (!current()) return
        const record = response.data || {}
        if (String(record.reimbursementId) !== intent.reimbursementId) throw Error("查询结果与原报销单不一致")
        if (Number(record.approvalRound) === intent.baseRound + 1 && ["pending", "submitting"].includes(record.status)) return this.applySubmissionResult(record, intent)
        const unchanged = ["draft", "returned", "withdrawn"].includes(record.status) && Number(record.approvalRound || 0) === intent.baseRound && String(record.rowVersion) === intent.version
        this.submitChecked = unchanged
        this.submitRecoveryMessage = unchanged ? "已核对：原草稿版本与审批轮次未变，可显式重试原提交；不会先重复保存草稿。" : "报销状态、版本或审批轮次已变化，不能重试原提交。请打开当前报销详情核对。"
      }).catch(error => { if (current() && !(error && error.notified)) this.submitRecoveryMessage = "提交结果仍待核对：" + reimbursementErrorMessage(error) })
        .finally(() => { if (scope.isCurrent(token)) this.checkingSubmission = false })
    },
    retrySubmissionIntent() {
      const intent = this.submitIntent
      if (!intent || !this.submitUnknown || !this.submitChecked || this.submitting || this.checkingSubmission) return Promise.resolve()
      const scope = this.submissionScope(), token = scope.begin("submit")
      this.submitting = true; this.submitChecked = false
      return this.sendSubmissionIntent(intent, token).finally(() => { if (scope.isCurrent(token)) this.submitting = false })
    },
    openSubmissionRecord() {
      const id = this.submitIntent && this.submitIntent.reimbursementId
      if (this.submitting || this.checkingSubmission || !id) return
      this.submissionScope().invalidate(); this.submitIntent = null; this.submitUnknown = false; this.submitChecked = false; this.submitRecoveryMessage = ""
      return this.openDetail(id)
    },
    hasInvoiceUploadContext() {
      if (this.formConflict) { this.$modal.msgWarning("请先核对草稿变化，再上传发票"); return false }
      if (hasValidatedSelectedDeptContext()) return true
      this.$modal.msgWarning(INVOICE_UPLOAD_CONTEXT_MESSAGE)
      return false
    },
    openInvoicePicker(kind) {
      if (this.busy) return
      if (!this.hasInvoiceUploadContext()) return
      const input = kind === "camera" ? this.$refs.invoiceCameraInput : this.$refs.invoiceInput
      if (input && typeof input.click === "function") input.click()
    },
    invoiceSelected(event) {
      const target = event && event.target
      if (!this.hasInvoiceUploadContext()) {
        if (target) target.value = ""
        return
      }
      const files = Array.from(target && target.files || [])
      if (target) target.value = ""
      if (!files.length || this.busy) return
      const validFiles = files.filter(file => this.validateInvoiceFile(file))
      if (!validFiles.length) return
      this.processInvoiceQueue(validFiles)
    },
    validateInvoiceFile(file) {
      const extension = String(file.name).split(".").pop().toLowerCase()
      if (!["pdf", "png", "jpg", "jpeg", "ofd"].includes(extension)) {
        this.$modal.msgWarning(`${file.name}：只支持 PDF、JPG、PNG 或 OFD`)
        return false
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$modal.msgWarning(`${file.name}：单个发票不能超过10MB`)
        return false
      }
      return true
    },
    async processInvoiceQueue(files) {
      if (this.busy) return false
      if (!this.hasInvoiceUploadContext()) return false
      const queueToken = ++this.uploadQueueToken
      this.uploadQueueActive = true
      this.uploadBatchTotal = files.length
      this.uploadBatchCompleted = 0
      this.uploadBatchFailures = []
      try {
        if (!this.form.reimbursementId) {
          if (!this.hasInvoiceUploadContext()) return false
          await this.persist(false)
          if (queueToken !== this.uploadQueueToken) return
          this.$modal.msgSuccess("已自动创建草稿，开始识别发票")
        }
        for (const file of files) {
          if (queueToken !== this.uploadQueueToken) break
          const uploaded = await this.uploadInvoiceFile(file)
          if (queueToken !== this.uploadQueueToken) break
          if (uploaded) this.uploadBatchCompleted += 1
          else this.uploadBatchFailures.push(file.name)
        }
        if (queueToken === this.uploadQueueToken && files.length > 1) {
          const summary = `已处理 ${this.uploadBatchCompleted} 张发票` +
            (this.uploadBatchFailures.length
              ? `，${this.uploadBatchFailures.length} 张失败`
              : "，费用明细已自动生成")
          if (this.uploadBatchFailures.length) this.$modal.msgWarning(summary)
          else this.$modal.msgSuccess(summary)
        }
      } catch (error) {
        if (queueToken === this.uploadQueueToken) {
          this.$modal.msgError(reimbursementErrorMessage(error))
        }
      } finally {
        if (queueToken === this.uploadQueueToken) {
          this.uploadQueueActive = false
        }
      }
    },
    uploadInvoiceFile(file) {
      if (!file || !this.form.reimbursementId || this.uploading) {
        return Promise.resolve(false)
      }
      if (!this.hasInvoiceUploadContext()) return Promise.resolve(false)
      const operation = this.captureFormOperation()
      const current = () => this.isCurrentFormOperation(operation)
      const controller = new AbortController()
      let uploadedInvoice = null
      const knownInvoiceIds = new Set((this.form.invoices || []).map(invoice =>
        String(invoice.invoiceId)
      ))
      this.uploadController = controller
      this.pendingInvoiceFile = file
      this.pendingInvoiceKnownIds = Array.from(knownInvoiceIds)
      this.uploadProgress = 0
      this.uploadError = ""
      this.uploading = true
      return uploadReimbursementInvoice(
        this.form.reimbursementId,
        file,
        event => {
          if (!current()) return
          const loaded = Number(event && event.loaded) || 0
          const total = Number(event && event.total) || Number(file.size) || 0
          this.uploadProgress = total > 0
            ? Math.min(100, Math.round(loaded * 100 / total))
            : 0
        },
        controller.signal
      )
        .then(response => {
          if (!current()) throw new Error("STALE_FORM_OPERATION")
          uploadedInvoice = response.data || {}
          return this.refreshForm(true).then(() => uploadedInvoice)
        }).then(uploadedInvoice => {
          if (!current()) throw new Error("STALE_FORM_OPERATION")
          const invoice = (this.form.invoices || []).find(value =>
            String(value.invoiceId) === String(uploadedInvoice.invoiceId)
          ) || uploadedInvoice
          const smartFill = uploadedInvoice.idempotentReplay
            ? { applied: false, itemIndex: -1, replay: true }
            : applyInvoiceToForm(this.form, invoice)
          const persist = smartFill.applied
            ? this.persist(false)
            : Promise.resolve(this.form)
          return persist.then(() => {
            if (!current()) return false
            if (uploadedInvoice.idempotentReplay) {
              this.$modal.msgWarning(
                `${file.name} 已存在于当前报销单，未重复识别或生成明细`
              )
            } else if (invoice.duplicateStatus === "warning") {
              this.$modal.msgWarning(
                `${file.name} 已生成明细，但疑似与历史发票重复`
              )
            } else if (invoice.recognitionStatus === "partial") {
              this.$modal.msgWarning(
                `${file.name} 已部分识别并填入明细，请提交前核对`
              )
            } else if (smartFill.applied) {
              const suffix = smartFill.incomplete ? "，仍有字段需要补充" : ""
              this.$modal.msgSuccess(
                `${file.name} 已识别并生成明细 ${smartFill.itemIndex + 1}${suffix}`
              )
            } else {
              this.$modal.msgWarning(
                invoice.recognitionMessage
                  ? reimbursementErrorMessage({
                    message: invoice.recognitionMessage
                  })
                  : `${file.name} 已上传，识别结果需要人工核对`
              )
            }
            this.pendingInvoiceFile = null
            this.pendingInvoiceKnownIds = []
            this.uploadError = ""
            return true
          })
        }).catch(error => {
          if (!current()) return false
          if (this.isCanceledUpload(error)) {
            this.pendingInvoiceFile = null
            this.pendingInvoiceKnownIds = []
            this.uploadError = ""
            return this.refreshForm(true)
              .catch(() => undefined)
              .then(() => false)
          }
          this.uploadError = reimbursementErrorMessage(error)
          return this.refreshForm(true).catch(() => undefined).then(() => {
            if (!current()) return false
            const recovered = uploadedInvoice && uploadedInvoice.invoiceId
              ? uploadedInvoice
              : (this.form.invoices || []).find(invoice =>
                !knownInvoiceIds.has(String(invoice.invoiceId)) &&
                String(invoice.originalName || "") === String(file.name || "") &&
                Number(invoice.fileSize) === Number(file.size)
              )
            if (recovered) {
              this.pendingInvoiceFile = null
              this.pendingInvoiceKnownIds = []
              this.uploadError = ""
              this.$modal.msgWarning(
                `${file.name} 已上传，但自动填充未完成；无需重新上传，请核对后保存草稿`
              )
              return true
            }
            this.$modal.msgError(
              `${file.name}：${this.uploadError}；系统会在重试前先检查是否已上传`
            )
            return false
          })
        }).finally(() => {
          if (this.uploadController === controller) {
            this.uploadController = null
            this.uploading = false
          }
        })
    },
    retryInvoiceUpload() {
      if (!this.pendingInvoiceFile || this.busy) return
      if (!this.hasInvoiceUploadContext()) return
      const file = this.pendingInvoiceFile
      const knownInvoiceIds = new Set(this.pendingInvoiceKnownIds || [])
      const retryToken = ++this.uploadQueueToken
      this.checkingUploadResult = true
      this.refreshForm(true).then(() => {
        if (retryToken !== this.uploadQueueToken) return false
        const recovered = (this.form.invoices || []).find(invoice =>
          !knownInvoiceIds.has(String(invoice.invoiceId)) &&
          String(invoice.originalName || "") === String(file.name || "") &&
          Number(invoice.fileSize) === Number(file.size)
        )
        if (recovered) {
          this.pendingInvoiceFile = null
          this.pendingInvoiceKnownIds = []
          this.uploadError = ""
          this.$modal.msgWarning(
            `${file.name} 实际已上传，无需重复上传，请直接核对`
          )
          return false
        }
        return this.uploadInvoiceFile(file)
      }).catch(() => {
        if (retryToken === this.uploadQueueToken) {
          this.$modal.msgWarning("暂时无法确认上传结果，请网络恢复后再重试")
        }
      }).finally(() => {
        if (retryToken === this.uploadQueueToken) {
          this.checkingUploadResult = false
        }
      })
    },
    cancelInvoiceUpload(clearFile = true) {
      if (this.uploadController &&
          typeof this.uploadController.abort === "function") {
        this.uploadController.abort()
      }
      this.uploadController = null
      this.uploading = false
      this.uploadProgress = 0
      this.uploadError = ""
      if (clearFile) {
        this.pendingInvoiceFile = null
        this.pendingInvoiceKnownIds = []
      }
    },
    cancelInvoiceBatch(clearFile = true) {
      this.uploadQueueToken += 1
      this.checkingUploadResult = false
      this.cancelInvoiceUpload(clearFile)
      this.uploadQueueActive = false
    },
    isCanceledUpload(error) {
      return !!error && (
        error.code === "ERR_CANCELED" ||
        error.name === "CanceledError" ||
        error.name === "AbortError" ||
        error.__CANCEL__ === true
      )
    },
    refreshForm(preserveLocalEdits = true) {
      if (this.pageInactive) return Promise.resolve()
      const reimbursementId = this.form.reimbursementId
      if (!reimbursementId) return Promise.resolve()
      if (this.formConflict) return this.reviewFormConflict()
      const token = this.beginFormRead(reimbursementId)
      const requestSnapshot = serializeReimbursementForm(this.form)
      const dirtyAtStart = this.isFormDirty
      this.loading = true
      return getReimbursement(reimbursementId, SILENT_READ).then(response => {
        if (!this.isCurrentFormRead(token)) return
        const remote = this.normalizeForm(response.data)
        const changedDuringRequest = serializeReimbursementForm(this.form) !== requestSnapshot
        try {
          this.form = mergeReimbursementReadDraft(this.form, remote, this.formServerSnapshot, preserveLocalEdits && (dirtyAtStart || changedDuringRequest))
        } catch (error) {
          this.setFormConflict("readConflict", error.message, remote)
          throw error
        }
        this.formBaseline = serializeReimbursementForm(remote)
        this.formServerSnapshot = cloneReimbursementDraft(remote)
      }).catch(error => {
        if (!this.isCurrentFormRead(token)) return
        throw error
      }).finally(() => {
        if (!this.isCurrentFormRead(token)) return
        this.loading = false

      })
    },
    rerunRecognition(invoice, engine) {
      if (!invoice || this.busy || !this.ensureFormWritable()) return
      if (engine === "cloud" &&
          !this.recognitionAvailability.cloudConfigured) {
        this.$modal.msgWarning(
          this.recognitionAvailability.cloudMessage || "云端OCR尚未配置"
        )
        return
      }
      this.recognizingInvoiceId = invoice.invoiceId
      recognizeReimbursementInvoice(
        this.form.reimbursementId,
        invoice.invoiceId,
        engine
      ).then(() => {
        this.$modal.msgSuccess(
          `${engine === "cloud" ? "云端" : "本地"}识别已完成`
        )
        return this.refreshForm(true)
      }).catch(error => {
        this.$modal.msgError(reimbursementErrorMessage(error))
      }).finally(() => {
        this.recognizingInvoiceId = ""
      })
    },
    recognitionPayload(invoice) {
      const fields = [
        "invoiceType", "invoiceCode", "invoiceNumber", "invoiceDate",
        "sellerName", "sellerTaxNo", "purchaserName", "purchaserTaxNo",
        "amountWithoutTax", "taxAmount", "invoiceTotalAmount", "checkCode",
        "serviceType", "commoditySummary"
      ]
      return fields.reduce((result, field) => {
        result[field] = invoice[field] === "" ? null : invoice[field]
        return result
      }, {})
    },
    saveInvoiceRecognition(invoice) {
      if (!invoice || this.busy || !this.ensureFormWritable()) return
      this.savingRecognitionInvoiceId = invoice.invoiceId
      updateReimbursementInvoiceRecognition(
        this.form.reimbursementId,
        invoice.invoiceId,
        this.recognitionPayload(invoice)
      ).then(() => {
        this.$modal.msgSuccess("发票识别结果已人工核对")
        return this.refreshForm(true)
      }).catch(error => {
        this.$modal.msgError(reimbursementErrorMessage(error))
      }).finally(() => {
        this.savingRecognitionInvoiceId = ""
      })
    },
    applyRecognition(invoice) {
      if (!invoice) return
      if (!this.form.items.length) this.form.items.push(item())
      const targetIndex = Number(invoice.targetItemKey)
      const hasTarget = invoice.targetItemKey !== undefined &&
        invoice.targetItemKey !== null &&
        invoice.targetItemKey !== "" &&
        Number.isInteger(targetIndex) &&
        targetIndex >= 0 &&
        targetIndex < this.form.items.length
      const result = applyInvoiceToForm(
        this.form,
        invoice,
        hasTarget ? { targetIndex } : {}
      )
      if (!result.applied) {
        this.$modal.msgWarning("当前没有可应用的识别字段")
        return
      }
      this.form.items[result.itemIndex].claimedAmount = normalizeMoneyInput(
        this.form.items[result.itemIndex].claimedAmount
      )
      this.$modal.msgSuccess(`已应用到明细 ${result.itemIndex + 1}`)
    },
    async deleteInvoice(invoice) {
      if (this.busy || !invoice || !this.ensureFormWritable()) return
      const reimbursementId = this.form.reimbursementId
      const operation = this.captureFormOperation()
      const invoiceId = String(invoice.invoiceId)
      const frozenInvoice = cloneReimbursementDraft(invoice)
      const baseline = cloneReimbursementDraft(this.formServerSnapshot || this.form)
      const expectedVersion = this.form.rowVersion
      const current = () => this.isCurrentFormOperation(operation) && String(this.form.reimbursementId) === String(reimbursementId)
      const deleteSeq = ++this.deleteOperationSeq
      this.deletingInvoiceId = invoiceId
      let deleteStarted = false
      try {
        const linkedMessage = invoice.itemId != null ? "，同时删除对应费用明细" : ""
        await this.$modal.confirm(`确认删除“${invoice.originalName}”${linkedMessage}？`, "删除发票")
        if (!current()) return
        deleteStarted = true
        this.formReadSeq += 1
        const response = await deleteReimbursementInvoice(reimbursementId, invoice.invoiceId, expectedVersion)
        if (!current()) return
        const detail = response && response.data
        if (detail && detail.reimbursementId != null && detail.rowVersion != null && Array.isArray(detail.items) && Array.isArray(detail.invoices)) {
          const remote = this.normalizeForm(detail)
          this.form = mergeReimbursementInvoiceDeletion(this.form, remote, frozenInvoice)
          this.formBaseline = serializeReimbursementForm(remote)
          this.formServerSnapshot = cloneReimbursementDraft(remote)
          this.$modal.msgSuccess("发票已删除")
        } else {
          this.setFormConflict("deleteUnknown", "删除响应未包含权威记录，正在核对，原草稿与版本已保留", null, { baseline, invoice: frozenInvoice })
          await this.reviewFormConflict()
        }
      } catch (error) {
        if (!deleteStarted || !current()) return
        const conflict = isReimbursementVersionConflict(error)
        this.setFormConflict(conflict ? "version" : "deleteUnknown",
          conflict ? "删除遇到版本冲突，原草稿与版本已保留；请比较最新费用后再继续" : "删除结果尚不明确，原草稿与版本已保留，核对前不能再次保存或提交",
          null, { baseline, invoice: frozenInvoice })
        this.$modal.msgError(reimbursementErrorMessage(error))
        await this.reviewFormConflict()
      } finally {
        if (deleteSeq === this.deleteOperationSeq && this.deletingInvoiceId === invoiceId) this.deletingInvoiceId = ""
      }
    },
    downloadInvoice(invoice) {
      return this.downloadFile(this.form.reimbursementId, invoice)
    },
    downloadDetailInvoice(invoice) {
      return this.downloadFile(this.detail.reimbursementId, invoice)
    },
    downloadFile(reimbursementId, invoice) {
      getReimbursementInvoice(reimbursementId, invoice.invoiceId, "download")
        .then(blob => {
          if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
          this.$download.saveAs(blob, invoice.originalName || "发票附件")
        }).catch(error => this.$modal.msgError(
          reimbursementErrorMessage(error)
        ))
    },
    canPreviewInvoice(invoice) {
      const extension = String(
        invoice && (invoice.fileExtension || invoice.originalName) || ""
      ).split(".").pop().toLowerCase()
      return ["pdf", "png", "jpg", "jpeg"].includes(extension)
    },
    previewKind(invoice, blob) {
      const extension = String(
        invoice && (invoice.fileExtension || invoice.originalName) || ""
      ).split(".").pop().toLowerCase()
      const type = String(blob && blob.type || "").toLowerCase()
      if (extension === "pdf" || type.includes("pdf")) return "pdf"
      if (["png", "jpg", "jpeg"].includes(extension) ||
          type.startsWith("image/")) return "image"
      return "unsupported"
    },
    openInvoicePreview(reimbursementId, invoice) {
      if (!invoice || !this.canPreviewInvoice(invoice)) return
      const sequence = ++this.invoicePreviewSequence
      this.releaseInvoicePreviewUrl()
      this.invoicePreview = {
        ...emptyInvoicePreview(),
        open: true,
        loading: true,
        reimbursementId,
        invoice,
        name: invoice.originalName || "发票附件"
      }
      getReimbursementInvoice(reimbursementId, invoice.invoiceId, "preview")
        .then(blob => {
          if (sequence !== this.invoicePreviewSequence) return
          if (!blobValidate(blob)) throw new Error("发票预览内容无效")
          const kind = this.previewKind(invoice, blob)
          this.invoicePreview.kind = kind
          if (kind !== "unsupported") {
            this.invoicePreview.url = URL.createObjectURL(blob)
          }
        }).catch(error => {
          if (sequence !== this.invoicePreviewSequence) return
          this.invoicePreview.error = reimbursementErrorMessage(error)
        }).finally(() => {
          if (sequence === this.invoicePreviewSequence) {
            this.invoicePreview.loading = false
          }
        })
    },
    releaseInvoicePreviewUrl() {
      if (this.invoicePreview && this.invoicePreview.url) {
        URL.revokeObjectURL(this.invoicePreview.url)
      }
    },
    closeInvoicePreview() {
      this.invoicePreviewSequence += 1
      this.releaseInvoicePreviewUrl()
      this.invoicePreview = emptyInvoicePreview()
    },
    downloadPreviewInvoice() {
      const preview = this.invoicePreview
      if (!preview || !preview.invoice) return
      this.downloadFile(preview.reimbursementId, preview.invoice)
    },
    approvalAction(action) { return this.runApprovalCommand(action) },
    handleBeforeUnload(event) {
      if (!this.isFormDirty && !this.uploadWorkActive) return
      event.preventDefault()
      event.returnValue = ""
    },
    confirmDiscardChanges() {
      return this.$modal.confirm(
        "当前修改尚未保存，离开后将丢失。确认离开？",
        "放弃未保存修改"
      )
    },
    confirmSafeLeave() {
      if (this.uploadWorkActive) {
        return this.$modal.confirm(
          "发票仍在上传或核对上传结果，离开将取消当前任务；未保存修改也会丢失。确认离开？",
          "取消上传并离开"
        ).then(() => {
          this.cancelInvoiceBatch(false)
        })
      }
      if (this.isFormDirty) return this.confirmDiscardChanges()
      return Promise.resolve()
    },
    async goBack() {
      if (this.mode !== "list" && !this.routeReimbursementId) {
        try {
          await this.confirmSafeLeave()
        } catch (_) {
          return
        }
        this.formBaseline = ""
        this.loadList(true)
        return
      }
      if (window.history.length > 1) this.$router.back()
      else this.$router.replace("/mobile/mine").catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-reimbursement{min-height:100vh;min-height:100dvh;padding-bottom:calc(82px + env(safe-area-inset-bottom));color:#183b3a;background:#eef4f3}.mobile-header{position:sticky;z-index:10;top:0;display:grid;grid-template-columns:42px 1fr 42px;align-items:center;gap:10px;padding:max(12px,env(safe-area-inset-top)) 14px 12px;background:rgba(250,252,251,.95);border-bottom:1px solid rgba(38,91,84,.1);backdrop-filter:blur(14px)}.mobile-header button{width:40px;height:40px;border:0;border-radius:12px;color:#226c64;background:#e0efed;font-size:27px}.mobile-header button:last-child{font-size:17px}.mobile-header small{color:#668682}.mobile-header h1{margin:2px 0 0;font-size:20px}.mobile-content{max-width:560px;margin:0 auto;padding:14px}.intro-card,.claim-card,.form-card,.invoice-card,.detail-card,.state-card{margin-bottom:12px;padding:16px;border:1px solid rgba(35,102,93,.1);border-radius:18px;background:#fff;box-shadow:0 8px 24px rgba(29,78,72,.05)}.intro-card{display:flex;align-items:center;justify-content:space-between;color:#fff;background:linear-gradient(130deg,#226b64,#244d64)}.intro-card small{opacity:.72}.intro-card h2{margin:5px 0 0;font-size:19px}.intro-card>strong{font-size:24px}.claim-head,.section-head,.item-order{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.claim-head small,.section-head small{color:#74918d}.claim-head h2,.section-head h2,.detail-card h2{margin:5px 0 0;font-size:17px}.claim-meta{display:flex;align-items:center;justify-content:space-between;margin:17px 0}.claim-meta strong,.amount{color:#cc6b2e;font-size:19px}.claim-meta span{color:#78918e;font-size:13px}.claim-card footer{display:flex;align-items:center;gap:8px;padding-top:12px;border-top:1px solid #edf2f1}.claim-card footer>span{flex:1;color:#829592;font-size:12px}.claim-card button,.section-head button,.invoice-row button,.single-action button,.state-card button{padding:8px 12px;border:0;border-radius:10px;color:#256d65;background:#e4f1ef;font-weight:600}.status-chip{flex:none;padding:5px 9px;border-radius:999px;font-size:12px}.status-chip.success{color:#217a50;background:#e1f4e8}.status-chip.warning{color:#946119;background:#fff0d1}.status-chip.danger{color:#ad4141;background:#fde7e7}.status-chip.info{color:#58716e;background:#e8efee}.form-card>label,.item-card label{display:block;margin-top:14px}.form-card label span,.item-card label span{display:block;margin-bottom:6px;color:#66827e;font-size:13px}.form-card input,.form-card textarea,.form-card select,.item-card input,.item-card select{width:100%;min-height:44px;padding:10px 11px;box-sizing:border-box;border:1px solid #d5e3e0;border-radius:11px;outline:0;color:#183b3a;background:#fbfdfc;font:inherit}.form-card textarea{resize:vertical}.section-head{align-items:center;margin:20px 0 10px}.item-card{margin-top:10px;padding:13px;border-radius:14px;background:#f5f9f8}.item-order button{padding:0;border:0;color:#c54e4e;background:transparent}.file-input{display:none}.hint{padding:10px 12px;border-radius:10px;color:#6f827f;background:#f2f6f5;font-size:13px}.invoice-row,.detail-row{display:flex;align-items:center;gap:8px;padding:12px 0;border-bottom:1px solid #edf2f1}.invoice-row:last-child,.detail-row:last-child{border-bottom:0}.invoice-row div,.detail-row div{min-width:0;flex:1}.invoice-row strong,.invoice-row small,.detail-row strong,.detail-row small{display:block;overflow-wrap:anywhere}.invoice-row small,.detail-row small{margin-top:4px;color:#80938f;font-size:12px}.invoice-row .warning{color:#b16e1d}.invoice-row button{padding:7px 9px}.invoice-row .delete{color:#b54242;background:#fbe8e8}.form-actions{display:grid;grid-template-columns:1fr 1.35fr;gap:9px;margin:14px 0}.form-actions button,.approval-bar button{min-height:46px;border:0;border-radius:13px;color:#236c64;background:#deeeeb;font-weight:700}.form-actions .primary{color:#fff;background:#278a7d}.detail-card dl{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:18px 0}.detail-card dt,.purpose small{color:#78918d;font-size:12px}.detail-card dd{margin:4px 0 0;overflow-wrap:anywhere}.purpose{padding:12px;border-radius:12px;background:#f3f7f6}.purpose p{margin:6px 0 0;line-height:1.6;white-space:pre-wrap}.detail-row>span{color:#c66c34;font-weight:700}.timeline{margin:16px 0 0;padding:0;list-style:none}.timeline li{position:relative;display:flex;gap:11px;padding:0 0 18px}.timeline li:not(:last-child)::before{position:absolute;top:12px;bottom:0;left:5px;width:2px;background:#dce8e6;content:""}.dot{position:relative;z-index:1;width:12px;height:12px;margin-top:3px;border-radius:50%;background:#9fb1af}.dot.success{background:#35a36b}.dot.warning{background:#dfa03c}.dot.danger{background:#d45b5b}.timeline strong,.timeline small{display:block}.timeline small{margin-top:4px;color:#78908d}.single-action button{width:100%;min-height:46px}.state-card{text-align:center}.state-card p{color:#6c827f;line-height:1.5}.state-card.error{color:#a43d3d}.floating-add{position:fixed;right:22px;bottom:calc(24px + env(safe-area-inset-bottom));width:56px;height:56px;border:0;border-radius:50%;color:#fff;background:#278a7d;box-shadow:0 10px 24px rgba(25,111,100,.3);font-size:28px}.approval-bar{position:fixed;z-index:12;right:0;bottom:0;left:0;display:grid;grid-template-columns:1fr 1fr 1.2fr;gap:8px;padding:10px 14px calc(10px + env(safe-area-inset-bottom));background:rgba(255,255,255,.97);border-top:1px solid rgba(34,89,82,.12)}.approval-bar .reject{color:#a43d3d;background:#fde7e7}.approval-bar .approve{color:#fff;background:#278a7d}button:disabled{opacity:.5}.invoice-record{border-bottom:1px solid #edf2f1}.invoice-record:last-child{border-bottom:0}.recognition-editor{padding:0 0 12px}.recognition-editor summary{padding:9px 0;color:#28776e;font-weight:700;cursor:pointer}.recognition-editor label{display:block;margin-top:9px}.recognition-editor label span{display:block;margin-bottom:5px;color:#66827e;font-size:12px}.recognition-editor input,.recognition-editor select{width:100%;min-height:42px;padding:9px 10px;box-sizing:border-box;border:1px solid #d5e3e0;border-radius:10px;color:#183b3a;background:#fbfdfc;font:inherit}.recognition-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}.recognition-actions button{min-height:40px;border:0;border-radius:10px;color:#256d65;background:#e4f1ef;font-weight:700}

.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.mobile-header{grid-template-columns:44px 1fr 44px}.mobile-header button{width:44px;height:44px}
.list-filter{display:grid;grid-template-columns:minmax(0,1fr) 116px 64px;gap:8px;margin-bottom:12px;padding:12px;border:1px solid rgba(35,102,93,.1);border-radius:16px;background:#fff}
.list-filter input,.list-filter select{width:100%;min-height:44px;box-sizing:border-box;padding:0 10px;border:1px solid #d5e3e0;border-radius:11px;color:#183b3a;background:#fbfdfc;font:inherit}
.list-filter button,.load-more{min-height:44px;border:0;border-radius:11px;color:#fff;background:#278a7d;font-weight:700}
.load-more{width:100%;margin:0 0 12px}
.claim-card button,.section-head button,.invoice-row button,.item-order button,.recognition-actions button{min-height:44px}
.item-order button{min-width:52px;padding:0 8px}
.invoice-upload-actions{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-bottom:10px}
.invoice-upload-actions button{min-height:46px;border:0;border-radius:12px;color:#256d65;background:#e4f1ef;font-weight:700}
.invoice-upload-actions button:first-child{color:#fff;background:#278a7d}
.invoice-upload-status{position:relative;display:flex;align-items:center;gap:10px;min-height:58px;margin:10px 0;padding:9px 10px;overflow:hidden;border-radius:12px;background:#f2f7f6}
.invoice-upload-status>div{min-width:0;display:flex;flex:1;flex-direction:column;gap:4px}.invoice-upload-status strong,.invoice-upload-status small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.invoice-upload-status small{color:#6f827f}.invoice-upload-status .warning{color:#a96419}.invoice-upload-status button{min-height:44px;padding:0 10px;border:0;background:transparent;color:#256d65;font-weight:700}
.invoice-upload-progress{position:absolute;right:0;bottom:0;left:0;height:3px;background:#dce9e7}.invoice-upload-progress i{display:block;height:100%;background:#278a7d;transition:width .2s ease}
.invoice-preview{position:fixed;z-index:3000;inset:0;display:flex;align-items:flex-end;justify-content:center;padding:14px 12px calc(12px + env(safe-area-inset-bottom));background:rgba(16,38,36,.58)}
.invoice-preview-card{display:flex;width:min(100%,560px);max-height:90vh;flex-direction:column;overflow:hidden;border-radius:22px;background:#fff;box-shadow:0 24px 70px rgba(11,44,40,.3)}
.invoice-preview-card header,.invoice-preview-card footer{display:flex;min-height:60px;align-items:center;justify-content:space-between;gap:12px;padding:8px 14px}.invoice-preview-card header{border-bottom:1px solid #e6efed}.invoice-preview-card header strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.invoice-preview-card footer{justify-content:flex-end;border-top:1px solid #e6efed}
.invoice-preview-card button{min-height:44px;padding:0 14px;border:0;border-radius:12px;color:#256d65;background:#e4f1ef;font-weight:700}.invoice-preview-card footer button:last-child{color:#fff;background:#278a7d}
.invoice-preview-body{min-height:280px;flex:1;overflow:auto;padding:12px;background:#f3f7f6}.invoice-preview-body img{display:block;width:100%;max-height:62vh;object-fit:contain;border-radius:12px;background:#e5eeec}.invoice-preview-body iframe{width:100%;min-height:60vh;border:0;border-radius:12px;background:#fff}.preview-state{display:grid;min-height:280px;place-items:center;color:#6f827f;text-align:center}.preview-state.error{color:#a43d3d}
.mobile-reimbursement{color:var(--mobile-color-ink);background:var(--mobile-color-page)}
.mobile-header{border-bottom-color:var(--mobile-color-line);background:var(--mobile-color-surface)}
.intro-card{background:var(--mobile-color-primary)}
.intro-card,.claim-card,.form-card,.invoice-card,.detail-card,.state-card{border-color:var(--mobile-color-line);border-radius:var(--mobile-radius-lg);box-shadow:none}
.form-card input,.form-card textarea,.form-card select,.item-card input,.item-card select,.recognition-editor input,.recognition-editor select{font-size:16px}
.form-actions .primary,.invoice-upload-actions button:first-child,.floating-add,.approval-bar .approve,.list-filter button,.load-more{background:var(--mobile-color-primary)}
.claim-card button,.section-head button,.invoice-row button,.recognition-actions button,.invoice-upload-actions button{color:var(--mobile-color-primary);background:var(--mobile-color-primary-soft)}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible{outline:3px solid rgba(39,138,125,.28);outline-offset:2px}
@media(max-width:420px){.list-filter{grid-template-columns:1fr 108px}.list-filter button{grid-column:1/-1}.claim-card footer{flex-wrap:wrap}.claim-card footer>span{flex-basis:100%}.invoice-row{flex-wrap:wrap}.invoice-row>div{flex-basis:100%}}
.smart-start-card,.readiness-card{margin-bottom:12px;padding:16px;border:1px solid var(--mobile-color-line);border-radius:var(--mobile-radius-lg);background:var(--mobile-color-surface);box-shadow:none}.smart-start-card{border-color:rgba(11,107,83,.18);background:var(--mobile-color-primary-soft)}.smart-start-copy{display:flex;align-items:flex-start;gap:12px}.smart-start-icon{display:grid;width:44px;height:44px;flex:none;place-items:center;border-radius:12px;color:#fff;background:var(--mobile-color-primary);font-size:21px}.smart-start-copy small{color:var(--mobile-color-muted)}.smart-start-copy h2{margin:3px 0 5px;font-size:17px;font-weight:700}.smart-start-copy p{margin:0;color:var(--mobile-color-muted);font-size:13px;line-height:1.5}.smart-start-card .invoice-upload-actions{margin:14px 0 8px}.readiness-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.readiness-head small{color:var(--mobile-color-muted)}.readiness-head h2{margin:4px 0 0;font-size:17px;font-weight:700}.readiness-list{display:grid;gap:7px;margin:13px 0 0;padding:0;list-style:none}.readiness-list li{display:flex;align-items:center;gap:7px;padding:9px 10px;border-radius:8px;color:var(--mobile-color-warning);background:var(--mobile-color-warning-soft);font-size:13px}.readiness-list li.ready{color:var(--mobile-color-success);background:var(--mobile-color-success-soft)}.readiness-warning{margin:10px 0 0;padding:10px;border-radius:10px;color:var(--mobile-color-warning);background:var(--mobile-color-warning-soft);font-size:13px;line-height:1.5}
.mobile-header{backdrop-filter:none;-webkit-backdrop-filter:none}
.form-actions,.approval-bar{z-index:90}
.form-actions .primary,.approval-bar .approve{min-height:48px}
</style>
