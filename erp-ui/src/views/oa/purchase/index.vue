<template>
  <div class="app-container oa-workspace-page oa-purchase-page">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 采购申请</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-shopping-cart-full" /></span>
          <div>
            <h1>采购申请</h1>
            <p>创建行政采购草稿，清晰跟踪提交、审批、退回与关闭状态。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>当前查询结果</span>
        <strong>{{ total }} 条</strong>
        <small>{{ queryParams.status ? statusLabel(queryParams.status) : '全部状态' }}</small>
      </div>
    </section>

    <el-alert
      v-if="purchaseAvailabilityResolved && !purchaseSubmissionAvailable"
      :title="purchaseAvailabilityReason"
      type="warning"
      :closable="false"
      show-icon
      class="mb12"
    />
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon class="mb12" />
    <el-card shadow="never" class="search-card oa-filter-card purchase-filter-card">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="采购标题" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="draft"/>
            <el-option label="提交中" value="submitting"/>
            <el-option label="审批中" value="pending"/>
            <el-option label="已通过" value="approved"/>
            <el-option label="已退回" value="returned"/>
            <el-option label="已拒绝" value="rejected"/>
            <el-option label="已撤回" value="withdrawn"/>
            <el-option label="已终止" value="terminated"/>
            <el-option label="已关闭" value="cancelled"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button v-hasPermi="['oa:purchase:add']" size="mini" icon="el-icon-plus" :disabled="!purchaseDraftAvailable" @click="openForm()">新建采购申请</el-button>
          <el-button v-hasPermi="['oa:purchase:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card oa-table-card">
      <div slot="header" class="oa-card-heading">
        <div class="oa-card-heading__title">
          <span class="oa-card-heading__icon"><i class="el-icon-document" /></span>
          <div>
            <h2>采购申请列表</h2>
            <p>优先处理草稿、退回和审批中的申请</p>
          </div>
        </div>
        <el-tag size="small" type="info">{{ total }} 条</el-tag>
      </div>
      <el-table v-loading="loading" :data="list" :empty-text="oaPurchaseEmptyText">
        <el-table-column label="单号" prop="purchaseId" width="90"/>
        <el-table-column label="标题" prop="title" min-width="160"/>
        <el-table-column label="金额" prop="amount" width="120"/>
        <el-table-column label="店铺编号" prop="shopDeptId" width="120"/>
        <el-table-column label="状态" prop="status" width="110">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.status === 'draft'" type="info" size="mini">草稿</el-tag>
            <el-tag v-else-if="scope.row.status === 'submitting'" type="warning" size="mini">提交中</el-tag>
            <el-tag v-else-if="scope.row.status === 'pending'" type="warning" size="mini">审批中</el-tag>
            <el-tag v-else-if="scope.row.status === 'approved'" type="success" size="mini">已通过</el-tag>
            <el-tag v-else-if="scope.row.status === 'returned'" type="warning" size="mini">已退回</el-tag>
            <el-tag v-else-if="scope.row.status === 'rejected'" type="danger" size="mini">已拒绝</el-tag>
            <el-tag v-else-if="scope.row.status === 'withdrawn'" type="info" size="mini">已撤回</el-tag>
            <el-tag v-else-if="scope.row.status === 'terminated'" type="danger" size="mini">已终止</el-tag>
            <el-tag v-else-if="scope.row.status === 'cancelled'" type="info" size="mini">已关闭</el-tag>
            <el-tag v-else size="mini">未知状态</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180"/>
        <el-table-column label="操作" width="320" fixed="right">
          <template slot-scope="scope">
            <el-button v-if="editable(scope.row)" v-hasPermi="['oa:purchase:add']" type="text" size="mini" :disabled="!purchaseDraftAvailable" @click="openForm(scope.row)">编辑</el-button>
            <el-button v-if="editable(scope.row)" v-hasPermi="['oa:purchase:add']" type="text" size="mini" :disabled="!purchaseSubmissionAvailable" @click="doSubmit(scope.row)">提交</el-button>
            <el-button v-if="scope.row.status === 'pending'" v-hasPermi="['oa:purchase:add']" type="text" size="mini" @click="handleWithdraw(scope.row)">撤回</el-button>
            <el-button v-if="canClosePurchase(scope.row)" v-hasPermi="['oa:purchase:add']" type="text" size="mini" @click="handleClosePurchase(scope.row)">关闭申请</el-button>
            <el-button v-hasPermi="['oa:purchase:add']" type="text" size="mini" :disabled="!purchaseDraftAvailable" @click="copyAsNewDraft(scope.row)">以此再申请</el-button>
            <el-button type="text" size="mini" @click="showDetail(scope.row.purchaseId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-dialog
      :title="form.purchaseId ? '编辑采购申请' : '新建采购申请'"
      :visible.sync="open"
      width="640px"
      append-to-body
      :close-on-click-modal="false"
      :before-close="handleDialogClose"
    >
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon class="mb12" />
      <el-form ref="formRef" v-loading="formLoading" :disabled="formLoading || submitLoading" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="120" show-word-limit/>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="采购说明" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="500" show-word-limit/>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button :disabled="submitLoading" @click="requestCloseForm">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading || formLoading || !formReady || !purchaseDraftAvailable" @click="save(false)">保存草稿</el-button>
        <el-button type="success" :loading="submitLoading" :disabled="submitLoading || formLoading || !formReady || !purchaseSubmissionAvailable" @click="save(true)">保存并提交</el-button>
      </div>
    </el-dialog>

    <el-dialog title="采购申请尚未保存" :visible.sync="closeChoiceOpen" width="460px" append-to-body :close-on-click-modal="false">
      <p>可以保存草稿后关闭，也可以返回继续编辑。</p>
      <p v-if="!purchaseDraftAvailable">{{ purchaseAvailabilityReason }}</p>
      <span slot="footer">
        <el-button @click="closeChoiceOpen = false">继续编辑</el-button>
        <el-button @click="closeForm(true)">放弃修改</el-button>
        <el-button type="primary" :disabled="!purchaseDraftAvailable || !formReady || submitLoading" @click="saveAndClose">保存并关闭</el-button>
      </span>
    </el-dialog>
    <el-dialog title="采购申请与审批轨迹" :visible.sync="detailVisible" width="960px" append-to-body>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="mb12" />
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border size="small">
          <el-descriptions-item label="申请单号">{{ detail.purchaseId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicantNickName || detail.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detail.shopDeptName || detail.shopDeptId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ detail.amount == null ? '-' : detail.amount }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="3">{{ detail.reason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="approvalDetail">
          <div class="detail-heading">
            <h4>统一审批轨迹</h4>
            <el-tag size="mini" :type="approvalStatusType(approvalInstance.status)">{{ approvalStatusLabel(approvalInstance.status) }}</el-tag>
          </div>
          <el-table :data="approvalTasks" border size="small">
            <el-table-column label="顺序" prop="nodeOrder" width="70"/>
            <el-table-column label="节点" prop="nodeName" min-width="140"/>
            <el-table-column label="状态" width="110"><template slot-scope="s">{{ approvalStatusLabel(s.row.taskStatus || s.row.status) }}</template></el-table-column>
            <el-table-column label="处理人" min-width="120"><template slot-scope="s">{{ s.row.operatorName || s.row.assigneeName || '-' }}</template></el-table-column>
            <el-table-column label="完成时间" min-width="160"><template slot-scope="s">{{ s.row.completedTime || s.row.completeTime || '-' }}</template></el-table-column>
          </el-table>
          <h4>操作记录</h4>
          <el-table :data="approvalActions" border size="small">
            <el-table-column label="时间" prop="createTime" width="165"/>
            <el-table-column label="动作" width="105"><template slot-scope="s">{{ approvalStatusLabel(s.row.actionType || s.row.action) }}</template></el-table-column>
            <el-table-column label="操作人" min-width="120"><template slot-scope="s">{{ s.row.operatorName || '系统' }}</template></el-table-column>
            <el-table-column label="意见" min-width="220"><template slot-scope="s">{{ s.row.actionReason || s.row.reason || '-' }}</template></el-table-column>
          </el-table>
        </template>
        <el-empty v-else-if="detail && !detail.approvalInstanceId" description="尚未发起审批" :image-size="72"/>
      </div>
      <approval-command-recovery :message="approvalError" :reason="approvalReason" :unknown="approvalUnknown" :busy="actionLoading || checkingApproval" :checking="checkingApproval" :checked="approvalChecked" @check="checkApprovalCommand" @retry="retryApprovalCommand" />
      <div slot="footer">
        <el-button @click="detailVisible=false">关闭</el-button>
        <el-button v-if="canHandleApproval" type="success" :loading="actionLoading" :disabled="approvalUnknown || checkingApproval" @click="handleApprovalAction('approve')">同意</el-button>
        <el-button v-if="canHandleApproval" type="warning" :loading="actionLoading" :disabled="approvalUnknown || checkingApproval" @click="handleApprovalAction('return')">退回修改</el-button>
        <el-button v-if="canHandleApproval" type="danger" :loading="actionLoading" :disabled="approvalUnknown || checkingApproval" @click="handleApprovalAction('reject')">拒绝</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { createApprovalCommandRecovery } from "@/mixins/approvalCommandRecovery"
import ApprovalCommandRecovery from "@/components/ApprovalCommandRecovery"
import { closePurchase, getPurchaseAvailability, getPurchaseDetail, listMyPurchases, savePurchase, submitPurchase, withdrawPurchase } from "@/api/oa/purchase"
import { getApprovalInstance } from "@/api/approval/monitor"
import { approveApprovalTask, rejectApprovalTask, returnApprovalTask } from "@/api/approval/task"
import { statusLabel as approvalStatusLabel, statusType as approvalStatusType } from "@/views/approval/manage/components/approvalUi"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { getSelectedDeptId } from "@/utils/shopContext"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { purchaseRouteTarget, resolvePurchaseInstanceId, validatePurchaseApproval, canActOnPurchase } = require("@/utils/oaPurchaseContext")
const { getTodoFocus } = require("@/utils/todoBusinessFocus")
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  components: { ApprovalCommandRecovery },
  name: "OaPurchase",
  mixins: [createApprovalCommandRecovery({
    target: vm => vm.detailTarget && ({ businessCode: 'OA_PURCHASE', businessId: vm.detailTarget.purchaseId, taskId: vm.detailTarget.taskId, instanceId: vm.detailTarget.instanceId }),
    visible: vm => vm.detailVisible, canAct: vm => vm.canHandleApproval, loading: 'actionLoading',
    success: (vm, command) => { vm.refreshTodo(); return vm.showDetail(command.businessId) }
  }), createTodoBusinessFocusMixin({
    featureKey: "oaPurchase",
    loadFocusedRow(purchaseId) { return getPurchaseDetail(purchaseId, { silentError: true }) },
    actions: {
      editOaPurchase(row, focus) {
        if (!this.isPurchaseFocusCurrent(focus)) return
        if (!this.editable(row)) return this.showTodoBusinessHandled()
        return this.openForm(row)
      },
      viewOaPurchaseApproval(row, focus) {
        if (!this.isPurchaseFocusCurrent(focus)) return
        if (!row || !row.purchaseId) return this.showTodoBusinessHandled()
        if (this.editable(row)) return this.openForm(row)
        return this.showDetail(row.purchaseId)
      }
    }
  })],
  data() {
    return {
      loading: false,
      submitLoading: false,
      total: 0,
      list: [],
      open: false,
      detailVisible: false,
      detailLoading: false,
      actionLoading: false,
      purchaseSubmissionAvailable: false,
      purchaseDraftAvailable: false, purchaseAvailabilityReason: "正在检查采购功能", closeChoiceOpen: false,
      purchaseAvailabilityResolved: false,
      detail: null,
      approvalDetail: null,
      approvalTaskId: "",
      formSnapshot: "",
      formLoading: false,
      formReady: false,
      formError: "",
      listError: "",
      detailError: "",
      detailTarget: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: undefined,
        status: undefined
      },
      form: {
        purchaseId: undefined,
        title: "",
        amount: undefined,
        reason: ""
      },
      rules: {
        title: [
          { required: true, message: "请输入标题", trigger: "blur" },
          { max: 120, message: "标题长度不能超过120", trigger: "blur" }
        ],
        amount: [{ required: true, message: "请输入金额", trigger: "change" }],
        reason: [
          { required: true, message: "请输入采购说明", trigger: "blur" },
          { max: 500, message: "采购说明长度不能超过500", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this.handlePurchaseContextChanged)
    this.loadPurchaseAvailability()
    this.getList()
  },
  computed: {
    routeTargetKey() { return JSON.stringify(purchaseRouteTarget(this.$route && this.$route.query || {})) },
    actorContextKey() {
      const store = this.$store || {}, user = store.state && store.state.user || {}
      return JSON.stringify([store.getters && store.getters.id, user.sessionRevision])
    },
    approvalInstance() {
      return this.approvalDetail && this.approvalDetail.instance || {}
    },
    approvalTasks() {
      const value = this.approvalDetail && (this.approvalDetail.tasks || this.approvalDetail.taskList)
      return Array.isArray(value) ? value : []
    },
    approvalActions() {
      const value = this.approvalDetail && (this.approvalDetail.actions || this.approvalDetail.actionLogs || this.approvalDetail.history)
      return Array.isArray(value) ? value : []
    },
    canHandleApproval() {
      return this.detailVisible && !this.detailLoading && this.purchaseScope().isCurrent(this._purchaseDetailToken, this.detailTarget) &&
        canActOnPurchase(this.detail, this.approvalDetail, this.detailTarget)
    },
    currentUserId() {
      return this.$store && this.$store.getters ? this.$store.getters.id : undefined
    },
    oaPurchaseEmptyText() {
      if (!this.purchaseAvailabilityResolved) return "正在检查采购功能…"
      if (!this.purchaseDraftAvailable) return "暂无采购记录；" + this.purchaseAvailabilityReason
      return getBusinessEmptyText("oaPurchase", "missingBaseline")
    }
  },
  watch: {
    open(value) { if (!value) this.invalidatePurchaseForm() },
    detailVisible(value) {
      if (!value) { this.purchaseScope().invalidate("detail"); this.purchaseScope().invalidate("action"); this.actionLoading = false }
    },
    actorContextKey() { this.handlePurchaseContextChanged() },
    routeTargetKey() {
      this.handlePurchaseContextChanged(false)
      this.todoBusinessFocus = getTodoFocus("oaPurchase", this.$route && this.$route.query || {})
      if (this.todoBusinessFocus) this.getList()
      else {
        const target = purchaseRouteTarget(this.$route && this.$route.query || {})
        if (target.purchaseId) this.showDetail(target.purchaseId)
      }
    }
  },
  activated() {
    this.purchaseScope().activate()
    if (this._needsPurchaseRefresh) { this._needsPurchaseRefresh = false; this.loadPurchaseAvailability(); this.getList() }
  },
  deactivated() {
    this.purchaseScope().deactivate()
    this.invalidatePurchaseForm()
    this.detailLoading = false
    this.actionLoading = false
    this.loading = false
    this.open = false
    this.detailVisible = false
    this.formSnapshot = ""
    this._needsPurchaseRefresh = true
  },
  beforeDestroy() {
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.handlePurchaseContextChanged)
    this.purchaseScope().deactivate()
  },
  beforeRouteUpdate(to, from, next) {
    if (JSON.stringify(purchaseRouteTarget(to.query)) === JSON.stringify(purchaseRouteTarget(from.query))) { next(); return }
    this.confirmDiscardIfDirty().then(() => next()).catch(() => next(false))
  },
  beforeRouteLeave(to, from, next) {
    if (!this.isFormDirty()) {
      next()
      return
    }
    this.confirmDiscardIfDirty().then(() => {
      next()
    }).catch(() => {
      next(false)
    })
  },
  methods: {
    approvalStatusLabel,
    approvalStatusType,
    purchaseScope() {
      if (!this._purchaseScope) this._purchaseScope = createUiOperationScope(() => ({ actor: this.actorContextKey,
        deptId: getSelectedDeptId(), path: this.$route && this.$route.path }))
      return this._purchaseScope
    },
    isPurchaseFocusCurrent(focus) {
      return Boolean(focus) && this.purchaseScope().isCurrent(focus.operationToken)
    },
    invalidatePurchaseForm() {
      this.closeChoiceOpen = false
      ;["form", "form-open", "save", "submit"].forEach(lane => this.purchaseScope().invalidate(lane))
      this.formReady = false
      this.formLoading = false
      this.submitLoading = false
    },
    handlePurchaseContextChanged(refresh = true) {
      this.purchaseScope().invalidate()
      this.invalidatePurchaseForm()
      this.open = false
      this.detailVisible = false
      this.detailTarget = null
      this.detail = null
      this.approvalDetail = null
      this.list = []
      this.total = 0
      this.loading = false
      this.actionLoading = false
      if (refresh !== false && this.currentUserId) { this.loadPurchaseAvailability(); this.getList() }
    },
    statusLabel(status) {
      return { draft: "草稿", submitting: "提交中", pending: "审批中", approved: "已通过", returned: "已退回", rejected: "已拒绝", withdrawn: "已撤回", terminated: "已终止", cancelled: "已关闭" }[status] || (status ? "未知状态" : "-")
    },
    editable(row) {
      return !!row && ["draft", "returned", "withdrawn"].includes(row.status)
    },
    loadPurchaseAvailability() {
      const scope = this.purchaseScope(), token = scope.begin("availability")
      this._purchaseAvailabilityToken = token
      this.purchaseSubmissionAvailable = false
      this.purchaseDraftAvailable = false
      this.purchaseAvailabilityReason = "正在检查采购功能"
      this.purchaseAvailabilityResolved = false
      this._purchaseAvailabilityPromise = getPurchaseAvailability().then(res => {
        if (!scope.isCurrent(token)) return
        const capability = res.data || {}
        this.purchaseDraftAvailable = capability.canSave === true
        this.purchaseSubmissionAvailable = this.purchaseDraftAvailable && capability.canSubmit === true
        this.purchaseAvailabilityReason = capability.reason || (this.purchaseDraftAvailable ? '审批提交暂未开放' : '采购功能暂时不可用，请刷新后重试')
      }).catch(() => {
        if (scope.isCurrent(token)) { this.purchaseSubmissionAvailable = false; this.purchaseDraftAvailable = false; this.purchaseAvailabilityReason = "采购功能检查失败，请刷新后重试" }
      }).finally(() => {
        if (scope.isCurrent(token)) this.purchaseAvailabilityResolved = true
      })
      return this._purchaseAvailabilityPromise
    },
    getList() {
      const scope = this.purchaseScope(), token = scope.begin("list")
      this.loading = true
      this.listError = ""
      return this.loadTodoBusinessList(() => listMyPurchases({ ...this.queryParams }, { silentError: true })).then(res => {
        if (!scope.isCurrent(token)) return
        this.list = res.rows || []
        this.total = res.total || 0
        if (this.todoBusinessFocus) this.todoBusinessFocus.operationToken = token
        return this.handleTodoFocusRows(this.list)
      }).catch(error => {
        if (scope.isCurrent(token)) this.listError = error && error.message || "采购申请加载失败，请重试"
      }).finally(() => {
        if (scope.isCurrent(token)) this.loading = false
      })
    },
    copyAsNewDraft(row) {
      if (!this.purchaseDraftAvailable) {
        this.$modal.msgWarning(this.purchaseAvailabilityReason)
        return Promise.resolve()
      }
      const purchaseId = row && row.purchaseId != null ? String(row.purchaseId).trim() : ""
      if (!/^[1-9]\d{0,18}$/.test(purchaseId)) {
        this.$modal.msgWarning("原申请不存在，无法再申请")
        return Promise.resolve()
      }
      const start = () => {
        this.invalidatePurchaseForm()
        const scope = this.purchaseScope()
        const token = scope.begin("form")
        this._purchaseFormToken = token
        this.formError = ""
        this.open = true
        this.formLoading = true
        this.formReady = false
        return getPurchaseDetail(purchaseId, { silentError: true }).then(res => {
          if (!scope.isCurrent(token) || !this.open) return
          const source = res.data || {}
          if (String(source.purchaseId) !== purchaseId) throw new Error("原申请已变化，请刷新后再申请")
          this.form = {
            purchaseId: undefined,
            title: source.title || "",
            amount: source.amount,
            reason: source.reason || ""
          }
          this.formReady = true
          this.$nextTick(() => { if (scope.isCurrent(token)) this.markFormClean() })
        }).catch(error => {
          if (scope.isCurrent(token) && this.open) {
            this.formError = (error && error.message) || "原申请加载失败，请稍后重试"
          }
        }).finally(() => {
          if (scope.isCurrent(token)) this.formLoading = false
        })
      }
      if (this.isFormDirty()) return this.confirmDiscardIfDirty().then(start).catch(() => {})
      return start()
    },
    openForm(row) {
      const scope = this.purchaseScope()
      const intent = scope.begin("form-open")
      if (!this.purchaseAvailabilityResolved && !this.purchaseDraftAvailable) {
        const target = row && typeof row === 'object' ? { purchaseId: row.purchaseId } : row
        const capability = scope.isCurrent(this._purchaseAvailabilityToken)
          ? this._purchaseAvailabilityPromise : this.loadPurchaseAvailability()
        return capability.then(() => {
          if (scope.isCurrent(intent) && this.purchaseAvailabilityResolved) return this.openForm(target)
        })
      }
      if (!this.purchaseDraftAvailable) { this.$modal.msgWarning(this.purchaseAvailabilityReason); return Promise.resolve() }
      const eventLike = row && typeof row === "object" &&
        (typeof row.preventDefault === "function" || typeof row.stopPropagation === "function" ||
          (row.type && row.target))
      const rawPurchaseId = !eventLike && row && typeof row === "object" ? row.purchaseId : null
      const purchaseId = rawPurchaseId === undefined || rawPurchaseId === null
        ? "" : String(rawPurchaseId).trim()
      const load = () => {
        if (!scope.isCurrent(intent)) return
        this.invalidatePurchaseForm()
        const token = scope.begin("form")
        this._purchaseFormToken = token
        this.formError = ""
        this.open = true
        if (!/^[1-9]\d{0,18}$/.test(purchaseId)) {
          this.form = { purchaseId: undefined, title: "", amount: undefined, reason: "" }
          this.formReady = true
          this.$nextTick(() => { if (scope.isCurrent(token)) this.markFormClean() })
          return Promise.resolve()
        }
        this.formLoading = true
        return getPurchaseDetail(purchaseId, { silentError: true }).then(res => {
          if (!scope.isCurrent(token) || !this.open) return
          if (!res.data || String(res.data.purchaseId) !== purchaseId) throw new Error("采购申请已变化，请重新打开")
          this.form = Object.assign({}, res.data, { purchaseId })
          this.formReady = true
          this.$nextTick(() => { if (scope.isCurrent(token)) this.markFormClean() })
        }).catch(error => {
          if (scope.isCurrent(token) && this.open) this.formError = error && error.message || "采购申请加载失败，请重新打开"
        }).finally(() => { if (scope.isCurrent(token)) this.formLoading = false })
      }
      if (this.isFormDirty()) return this.confirmDiscardIfDirty().then(load).catch(() => {})
      return load()
    },
    save(submitAfterSave) {
      if (!this.purchaseDraftAvailable) { this.$modal.msgWarning(this.purchaseAvailabilityReason); return }
      const scope = this.purchaseScope()
      if (this.submitLoading || this.formLoading || !this.formReady || !this.open || !scope.isCurrent(this._purchaseFormToken)) return
      if (submitAfterSave && !this.purchaseSubmissionAvailable) {
        this.$modal.msgWarning("审批规则尚未发布或采购提交尚未开放")
        return
      }
      const token = scope.begin("save"), formToken = this._purchaseFormToken
      const payload = { ...this.form }
      const current = () => scope.isCurrent(token) && scope.isCurrent(formToken) && this.open
      this.submitLoading = true
      this.$refs.formRef.validate(valid => {
        if (!current()) return
        if (!valid) { this.submitLoading = false; return }
        savePurchase(payload).then(res => {
          if (!current()) return
          if (!res.data || (payload.purchaseId && String(res.data.purchaseId) !== String(payload.purchaseId))) throw new Error("保存结果与采购申请不一致，请重新核对")
          this.form = { ...res.data }
          this.markFormClean()
          if (!submitAfterSave) {
            this.$modal.msgSuccess("已保存草稿")
            this.closeForm(true)
            this.getList()
            return
          }
          return this.submitSavedPurchase({ ...res.data }, current)
        }).catch(error => {
          if (!current()) return
          if (/FEATURE_DISABLED/.test(error && error.message || '')) {
            this.formError = '采购功能当前不可用，填写内容已保留，请稍后重试'
            this.loadPurchaseAvailability()
          } else if (!(error && error.notified)) this.formError = error && error.message || '保存结果待核对，请保留填写内容后重试' 
        }).finally(() => {
          if (scope.isCurrent(token)) this.submitLoading = false
        })
      })
    },
    doSubmit(row) {
      if (this.submitLoading) return
      if (!this.purchaseSubmissionAvailable) {
        this.$modal.msgWarning("审批规则尚未发布或采购提交尚未开放")
        return
      }
      const scope = this.purchaseScope(), token = scope.begin("submit")
      const payload = { ...row }
      this.submitLoading = true
      return this.submitSavedPurchase(payload, () => scope.isCurrent(token)).catch(error => {
        if (scope.isCurrent(token) && !(error && error.notified)) this.$modal.msgError(error && error.message || "提交结果待核对，请刷新查看")
      }).finally(() => {
        if (scope.isCurrent(token)) this.submitLoading = false
      })
    },
    submitSavedPurchase(row, current = () => true) {
      if (!this.purchaseSubmissionAvailable) return Promise.reject(new Error("审批规则尚未发布或采购提交尚未开放"))
      if (!current()) return Promise.resolve()
      return submitPurchase({ ...row }).then(() => {
        if (!current()) return
        this.$modal.msgSuccess("提交成功")
        this.closeForm(true)
        this.getList()
      })
    },
    canClosePurchase(row) {
      return !!row && row.status === "rejected" && row.applicantId !== undefined && row.applicantId !== null &&
        this.currentUserId !== undefined && this.currentUserId !== null &&
        String(row.applicantId) === String(this.currentUserId)
    },
    handleWithdraw(row) {
      return this.$prompt("请输入撤回原因", "撤回采购申请", { inputValue: "申请人撤回", inputValidator: value => String(value || "").trim() ? true : "请输入撤回原因" }).then(({ value }) => {
        return withdrawPurchase(row.purchaseId, { reason: String(value).trim() })
      }).then(() => {
        this.$modal.msgSuccess("撤回请求已提交")
        this.refreshTodo()
        this.getList()
      }).catch(() => {})
    },
    handleClosePurchase(row) {
      return this.$modal.confirm("确认关闭这条已驳回的采购申请？关闭后不可重新提交。", "关闭申请").then(() => {
        return closePurchase(row.purchaseId)
      }).then(() => {
        this.$modal.msgSuccess("申请已关闭")
        this.getList()
      }).catch(() => {})
    },
    requestCloseForm() {
      if (this.submitLoading) return
      if (this.isFormDirty()) this.closeChoiceOpen = true
      else this.closeForm(true)
    },
    handleDialogClose() { this.requestCloseForm() },
    saveAndClose() {
      if (this.submitLoading || !this.purchaseDraftAvailable) return
      this.closeChoiceOpen = false
      return this.save(false)
    },
    closeForm(force) {
      if (!force && this.isFormDirty()) {
        this.requestCloseForm()
        return
      }
      this.open = false
      this.closeChoiceOpen = false
      this.formSnapshot = ""
      this.invalidatePurchaseForm()
    },
    markFormClean() {
      this.formSnapshot = this.snapshotForm()
    },
    snapshotForm() {
      const source = this.form || {}
      return JSON.stringify({
        purchaseId: source.purchaseId || null,
        title: source.title || "",
        amount: source.amount === undefined || source.amount === null ? "" : String(source.amount),
        reason: source.reason || ""
      })
    },
    isFormDirty() {
      return this.open && !!this.formSnapshot && this.snapshotForm() !== this.formSnapshot
    },
    confirmDiscardIfDirty() {
      if (!this.isFormDirty()) return Promise.resolve()
      return this.$modal.confirm("当前采购申请有未保存内容，确定放弃修改吗？", "未保存提醒", { confirmButtonText: "放弃修改", cancelButtonText: "继续编辑" })
    },
    showDetail(purchaseId) {
      const target = purchaseRouteTarget(this.$route && this.$route.query || {}, purchaseId)
      const scope = this.purchaseScope()
      scope.invalidate("action")
      const token = scope.begin("detail", target)
      this._purchaseDetailToken = token
      this.detailTarget = target
      this.approvalTaskId = target.taskId
      this.actionLoading = false
      this.detailVisible = true
      this.detailLoading = true
      this.detailError = ""
      this.detail = null
      this.approvalDetail = null
      const current = () => scope.isCurrent(token, this.detailTarget) && this.detailVisible
      let purchase
      return getPurchaseDetail(target.purchaseId, { silentError: true }).then(res => {
        if (!current()) return
        purchase = res.data || {}
        const instanceId = resolvePurchaseInstanceId(purchase, target)
        if (!instanceId) return null
        return getApprovalInstance(instanceId, { silentError: true })
      }).then(response => {
        if (!current() || !purchase) return
        const approval = response && (response.data && response.data.data !== undefined ? response.data.data : response.data || {})
        validatePurchaseApproval(purchase, approval, target)
        this.detail = purchase
        this.approvalDetail = approval
      }).catch(error => {
        if (current()) this.detailError = error && error.message || "采购审批加载失败，请重试"
      }).finally(() => { if (current()) this.detailLoading = false })
    },
    handleApprovalAction(action) { return this.runApprovalCommand(action) },
    refreshTodo() {
      return this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
    },
    handleExport() {
      this.$modal.confirm("确认导出当前查询条件下的采购申请数据？", "导出提示").then(() => {
        this.download("oa/purchase/export/my", { ...this.queryParams }, this.exportFileName("我的采购申请"))
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 {
  margin-bottom: 12px;
}
.purchase-filter-card {
  margin-bottom: 16px;
}
.detail-heading { display:flex; align-items:center; justify-content:space-between; margin-top:20px; }
.detail-heading h4, h4 { margin: 16px 0 10px; }
</style>
