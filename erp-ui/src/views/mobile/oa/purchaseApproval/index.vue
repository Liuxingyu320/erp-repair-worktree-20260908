<template>
  <div class="mobile-purchase-approval mobile-system-page">
    <header class="page-header">
      <button type="button" aria-label="返回" @click="goBack">
        <i class="el-icon-arrow-left" aria-hidden="true" />
      </button>
      <div>
        <small>统一审批</small>
        <h1>OA 采购审批</h1>
      </div>
      <button type="button" aria-label="刷新" :disabled="loading || actionLoading" @click="load">
        <i class="el-icon-refresh" />
      </button>
    </header>

    <main class="page-content">
      <section v-if="loading" class="state-card" role="status">正在加载采购申请…</section>
      <section v-else-if="error" class="state-card error" role="alert">
        <strong>无法打开审批</strong>
        <p>{{ error }}</p>
        <button type="button" @click="handleErrorAction">
          {{ hasValidPurchaseId ? '重试' : '返回待办' }}
        </button>
      </section>

      <template v-else-if="purchase">
        <section v-if="canEdit" class="edit-card">
          <div class="section-head edit-heading">
            <div>
              <small>{{ purchase.status === 'returned' ? '审批已退回' : '申请可继续编辑' }}</small>
              <h2>修改后重新提交</h2>
            </div>
            <span class="status-chip warning">待修改</span>
          </div>
          <p v-if="purchaseAvailabilityResolved && !purchaseSubmissionAvailable" class="availability-warning">
            审批规则尚未发布或采购提交尚未开放；当前可以继续保存草稿。
          </p>
          <label>
            <span>采购标题</span>
            <input v-model.trim="editForm.title" maxlength="120" type="text" placeholder="请输入采购标题">
          </label>
          <label>
            <span>采购金额</span>
            <input v-model.trim="editForm.amount" type="text" inputmode="decimal" placeholder="请输入采购金额">
          </label>
          <label>
            <span>采购说明</span>
            <textarea v-model.trim="editForm.reason" maxlength="500" rows="4" placeholder="请输入采购说明"></textarea>
          </label>
          <div class="edit-actions">
            <button type="button" class="save" :disabled="actionLoading" @click="saveEditable(false)">保存草稿</button>
            <button type="button" class="submit" :disabled="actionLoading || !purchaseSubmissionAvailable" @click="saveEditable(true)">
              {{ actionLoading ? '提交中…' : '保存并重新提交' }}
            </button>
          </div>
        </section>

        <section class="summary-card">
          <div class="summary-head">
            <div>
              <small>采购申请 #{{ purchase.purchaseId }}</small>
              <h2>{{ purchase.title || '未命名申请' }}</h2>
            </div>
            <span :class="['status-chip', statusTone(purchase.status)]">{{ purchaseStatusLabel(purchase.status) }}</span>
          </div>
          <dl>
            <div><dt>申请人</dt><dd>{{ purchase.applicantNickName || purchase.applicantName || '-' }}</dd></div>
            <div><dt>申请部门</dt><dd>{{ purchase.applicantDeptName || '-' }}</dd></div>
            <div><dt>归属门店</dt><dd>{{ purchase.shopDeptName || purchase.shopDeptId || '-' }}</dd></div>
            <div><dt>采购金额</dt><dd class="amount">¥ {{ amountText }}</dd></div>
          </dl>
          <div class="reason-block">
            <small>采购说明</small>
            <p>{{ purchase.reason || '-' }}</p>
          </div>
        </section>

        <section v-if="approvalDetail" class="timeline-card">
          <div class="section-head">
            <h2>审批轨迹</h2>
            <span :class="['status-chip', statusTone(instance.status)]">{{ approvalStatusLabel(instance.status) }}</span>
          </div>
          <ol v-if="tasks.length" class="task-list">
            <li v-for="task in tasks" :key="task.taskId">
              <span :class="['task-dot', statusTone(task.taskStatus)]" />
              <div>
                <strong>{{ task.nodeName || '审批节点' }}</strong>
                <small>第 {{ task.nodeOrder || '-' }} 级 · {{ approvalStatusLabel(task.taskStatus) }}</small>
                <small v-if="task.completedTime">完成于 {{ task.completedTime }}</small>
              </div>
            </li>
          </ol>
          <p v-else class="empty-copy">暂无审批节点记录</p>
        </section>

        <section v-if="actions.length" class="action-log-card">
          <h2>操作记录</h2>
          <article v-for="item in actions" :key="item.actionId">
            <div>
              <strong>{{ item.operatorName || '系统' }}</strong>
              <span>{{ approvalStatusLabel(item.actionType) }}</span>
            </div>
            <p>{{ item.actionReason || '无备注' }}</p>
            <small>{{ item.createTime || '-' }}</small>
          </article>
        </section>

        <section v-if="!canAct && !canEdit" class="handled-card">
          {{ approvalTaskId ? '该待办已处理或不再属于当前用户。' : '当前页面仅查看审批轨迹。' }}
        </section>
      </template>
    </main>

    <footer v-if="purchase && canAct" class="action-bar">
      <button type="button" class="return" :disabled="actionLoading" @click="executeAction('return')">退回修改</button>
      <button type="button" class="reject" :disabled="actionLoading" @click="executeAction('reject')">拒绝</button>
      <button type="button" class="approve" :disabled="actionLoading" @click="executeAction('approve')">
        {{ actionLoading ? '处理中…' : '同意' }}
      </button>
    </footer>
  </div>
</template>

<script>
import { getPurchaseAvailability, getPurchaseDetail, savePurchase, submitPurchase } from "@/api/oa/purchase"
import { getApprovalInstance } from "@/api/approval/monitor"
import { approveApprovalTask, rejectApprovalTask, returnApprovalTask } from "@/api/approval/task"
import { statusLabel as approvalStatusLabel } from "@/views/approval/manage/components/approvalUi"
const { mobileErrorMessage } = require("../../mobileErrorMessage")

function scalar(value) {
  const candidate = Array.isArray(value) ? value[0] : value
  return candidate === undefined || candidate === null ? "" : String(candidate).trim()
}

function errorMessage(error) {
  return mobileErrorMessage(error, "数据加载失败，请稍后重试")
}

function normalizeAmount(value) {
  const source = String(value === undefined || value === null ? "" : value)
    .trim().replace(/,/g, "")
  const match = source.match(/^(\d+)(?:\.(\d{0,2}))?$/)
  if (!match) return ""
  const whole = match[1].replace(/^0+(?=\d)/, "")
  return `${whole}.${String(match[2] || "").padEnd(2, "0")}`
}

export default {
  name: "MobileOaPurchaseApproval",
  data() {
    return {
      loading: false,
      actionLoading: false,
      error: "",
      purchase: null,
      approvalDetail: null,
      purchaseSubmissionAvailable: false,
      purchaseAvailabilityResolved: false,
      editForm: { purchaseId: undefined, title: "", amount: undefined, reason: "", rowVersion: undefined }
    }
  },
  computed: {
    routeQuery() { return this.$route && this.$route.query || {} },
    purchaseId() { return scalar(this.routeQuery.purchaseId || this.routeQuery.businessId) },
    approvalTaskId() { return scalar(this.routeQuery.approvalTaskId) },
    hasValidPurchaseId() {
      return /^\d+$/.test(this.purchaseId) && Number(this.purchaseId) > 0
    },
    approvalInstanceId() {
      return scalar(this.routeQuery.approvalInstanceId || this.purchase && this.purchase.approvalInstanceId)
    },
    instance() { return this.approvalDetail && this.approvalDetail.instance || {} },
    tasks() { return this.approvalDetail && Array.isArray(this.approvalDetail.tasks) ? this.approvalDetail.tasks : [] },
    actions() { return this.approvalDetail && Array.isArray(this.approvalDetail.actions) ? this.approvalDetail.actions : [] },
    currentTask() {
      return this.tasks.find(item => String(item.taskId) === String(this.approvalTaskId)) || null
    },
    canAct() {
      return !!this.approvalTaskId && !!this.currentTask &&
        String(this.currentTask.taskStatus || "").toUpperCase() === "PENDING" &&
        String(this.instance.status || "").toUpperCase() === "RUNNING"
    },
    canEdit() {
      return !!this.purchase && ["draft", "returned", "withdrawn"].includes(String(this.purchase.status || "").toLowerCase())
    },
    amountText() {
      return normalizeAmount(this.purchase && this.purchase.amount) || "0.00"
    }
  },
  watch: {
    "$route.query": { deep: true, handler() { this.load() } }
  },
  created() {
    this.load()
  },
  methods: {
    approvalStatusLabel,
    purchaseStatusLabel(status) {
      return { draft: "草稿", submitting: "提交中", pending: "审批中", approved: "已通过", returned: "已退回", rejected: "已拒绝", withdrawn: "已撤回", terminated: "已终止", cancelled: "已关闭" }[status] || (status ? "未知采购状态" : "-")
    },
    statusTone(status) {
      const value = String(status || "").toUpperCase()
      if (["APPROVED", "SUCCESS"].includes(value)) return "success"
      if (["RUNNING", "PENDING", "SUBMITTING", "COMPLETING", "RETURNING", "REJECTING"].includes(value)) return "warning"
      if (["REJECTED", "TERMINATED", "FAILED"].includes(value)) return "danger"
      return "info"
    },
    load() {
      if (this.loading) return
      if (!this.hasValidPurchaseId) {
        this.purchase = null
        this.approvalDetail = null
        this.error = "该入口需要从工作台携带采购申请上下文打开"
        return
      }
      this.loading = true
      this.loadPurchaseAvailability()
      this.error = ""
      this.purchase = null
      this.approvalDetail = null
      return getPurchaseDetail(this.purchaseId).then(response => {
        this.purchase = response.data || {}
        this.syncEditForm()
        if (!this.approvalInstanceId) return undefined
        return getApprovalInstance(this.approvalInstanceId)
      }).then(response => {
        if (!response) return
        this.approvalDetail = response.data && response.data.data !== undefined
          ? response.data.data : response.data || {}
      }).catch(error => {
        this.error = errorMessage(error)
      }).finally(() => {
        this.loading = false
      })
    },
    syncEditForm() {
      const source = this.purchase || {}
      this.editForm = {
        ...source,
        purchaseId: source.purchaseId,
        title: source.title || "",
        amount: source.amount,
        reason: source.reason || "",
        rowVersion: source.rowVersion
      }
    },
    loadPurchaseAvailability() {
      this.purchaseSubmissionAvailable = false
      this.purchaseAvailabilityResolved = false
      return getPurchaseAvailability().then(response => {
        this.purchaseSubmissionAvailable = !!(response.data && response.data.enabled === true)
      }).catch(() => {
        this.purchaseSubmissionAvailable = false
      }).finally(() => {
        this.purchaseAvailabilityResolved = true
      })
    },
    validateEditForm() {
      if (!String(this.editForm.title || "").trim()) return "请输入采购标题"
      if (!normalizeAmount(this.editForm.amount)) {
        return "请输入有效采购金额（最多两位小数）"
      }
      if (!String(this.editForm.reason || "").trim()) return "请输入采购说明"
      return ""
    },
    saveEditable(submitAfterSave) {
      if (!this.canEdit || this.actionLoading) return
      if (submitAfterSave && !this.purchaseSubmissionAvailable) {
        this.$modal.msgWarning("审批规则尚未发布或采购提交尚未开放")
        return
      }
      const validationMessage = this.validateEditForm()
      if (validationMessage) {
        this.$modal.msgWarning(validationMessage)
        return
      }
      this.actionLoading = true
      const payload = {
        ...this.editForm,
        title: String(this.editForm.title).trim(),
        amount: normalizeAmount(this.editForm.amount),
        reason: String(this.editForm.reason).trim()
      }
      return savePurchase(payload).then(response => {
        const saved = response.data || payload
        if (!submitAfterSave) {
          this.$modal.msgSuccess("已保存草稿")
          return saved
        }
        return submitPurchase(saved).then(() => {
          this.$modal.msgSuccess("采购申请已重新提交")
          return this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
        })
      }).then(() => this.load()).finally(() => {
        this.actionLoading = false
      }).catch(() => {})
    },
    executeAction(action) {
      if (!this.canAct || this.actionLoading) return
      const ask = action === "approve"
        ? this.$modal.confirm("确认同意这条采购申请？", "审批确认").then(() => "")
        : this.$prompt(action === "return" ? "请输入退回原因" : "请输入拒绝原因",
          action === "return" ? "退回修改" : "拒绝申请",
          { inputValidator: value => String(value || "").trim() ? true : "原因不能为空" })
          .then(({ value }) => String(value).trim())
      return ask.then(reason => {
        this.actionLoading = true
        const payload = {
          requestId: `OA_PURCHASE:${this.approvalTaskId}:${action}:v1`,
          reason
        }
        if (action === "approve") return approveApprovalTask(this.approvalTaskId, payload)
        if (action === "return") return returnApprovalTask(this.approvalTaskId, payload)
        return rejectApprovalTask(this.approvalTaskId, payload)
      }).then(() => {
        this.$modal.msgSuccess("审批动作已提交")
        this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
        return this.load()
      }).finally(() => {
        this.actionLoading = false
      }).catch(() => {})
    },
    handleErrorAction() {
      if (!this.hasValidPurchaseId) {
        return this.$router.replace("/mobile/todo").catch(() => {})
      }
      return this.load()
    },
    goBack() {
      if (window.history.length > 1) this.$router.back()
      else this.$router.replace("/mobile/todo").catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-purchase-approval{min-height:100vh;min-height:100dvh;padding-bottom:calc(88px + env(safe-area-inset-bottom));color:#19353a;background:#eef5f4}.page-header{position:sticky;z-index:10;top:0;display:grid;grid-template-columns:42px 1fr 42px;align-items:center;gap:10px;padding:max(12px,env(safe-area-inset-top)) 16px 12px;background:rgba(248,251,250,.94);border-bottom:1px solid rgba(32,94,89,.1);backdrop-filter:blur(14px)}.page-header button{width:40px;height:40px;border:0;border-radius:12px;color:#226c67;background:#e2f0ee;font-size:28px}.page-header button:last-child{font-size:17px}.page-header small{color:#5c7d7a}.page-header h1{margin:2px 0 0;font-size:20px}.page-content{width:100%;max-width:520px;margin:0 auto;padding:14px;box-sizing:border-box}.edit-card,.summary-card,.timeline-card,.action-log-card,.state-card,.handled-card{margin-bottom:12px;padding:16px;border:1px solid rgba(38,104,96,.1);border-radius:18px;background:#fff;box-shadow:0 8px 24px rgba(27,78,74,.06)}.summary-head,.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.summary-head small,.edit-heading small{color:#71908d}.summary-head h2,.section-head h2,.action-log-card h2{margin:5px 0 0;font-size:18px}.availability-warning{margin:14px 0 0;padding:10px 12px;border-radius:10px;color:#8b5d18;background:#fff3d9;font-size:13px;line-height:1.5}.edit-card label{display:block;margin-top:14px}.edit-card label span{display:block;margin-bottom:6px;color:#607d79;font-size:13px}.edit-card input,.edit-card textarea{width:100%;padding:11px 12px;box-sizing:border-box;border:1px solid #d6e4e1;border-radius:11px;outline:0;color:#19353a;background:#fbfdfd;font:inherit}.edit-card textarea{resize:vertical}.edit-card input:focus,.edit-card textarea:focus{border-color:#2c8d84;box-shadow:0 0 0 3px rgba(44,141,132,.1)}.edit-actions{display:grid;grid-template-columns:1fr 1.4fr;gap:9px;margin-top:16px}.edit-actions button{min-height:44px;border:0;border-radius:12px;font-weight:600}.edit-actions .save{color:#226c67;background:#e2f0ee}.edit-actions .submit{color:#fff;background:#278d80}.status-chip{flex:none;padding:5px 9px;border-radius:999px;font-size:12px}.status-chip.success{color:#237c54;background:#e1f5e9}.status-chip.warning{color:#9b6517;background:#fff0d2}.status-chip.danger{color:#b84242;background:#fde7e7}.status-chip.info{color:#58716f;background:#e9f0ef}.summary-card dl{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:18px 0}.summary-card dl div{min-width:0}.summary-card dt,.reason-block small{color:#78908e;font-size:12px}.summary-card dd{overflow-wrap:anywhere;margin:4px 0 0;font-size:14px}.summary-card .amount{color:#cb6e24;font-size:17px;font-weight:700}.reason-block{padding:12px;border-radius:12px;background:#f4f8f7}.reason-block p{margin:6px 0 0;line-height:1.6;white-space:pre-wrap}.task-list{margin:16px 0 0;padding:0;list-style:none}.task-list li{position:relative;display:flex;gap:12px;padding:0 0 18px}.task-list li:not(:last-child)::before{position:absolute;top:13px;bottom:0;left:5px;width:2px;background:#dce9e7;content:""}.task-dot{position:relative;z-index:1;flex:none;width:12px;height:12px;margin-top:4px;border-radius:50%;background:#9bb0ae}.task-dot.success{background:#39a56d}.task-dot.warning{background:#e2a13d}.task-dot.danger{background:#d85d5d}.task-list strong,.task-list small{display:block}.task-list small{margin-top:4px;color:#718986}.action-log-card article{padding:12px 0;border-bottom:1px solid #edf2f1}.action-log-card article:last-child{border-bottom:0}.action-log-card article div{display:flex;justify-content:space-between;gap:10px}.action-log-card article span,.action-log-card article small{color:#76908d;font-size:12px}.action-log-card article p{margin:6px 0;color:#405f5c}.handled-card,.empty-copy{color:#6f8885;font-size:13px}.state-card{text-align:center}.state-card p{color:#657d7a}.state-card button{padding:9px 18px;border:0;border-radius:10px;color:#fff;background:#2c8d84}.state-card.error{color:#a63e3e}.action-bar{position:fixed;z-index:12;right:0;bottom:0;left:0;display:grid;grid-template-columns:1fr 1fr 1.2fr;gap:8px;padding:10px 14px calc(10px + env(safe-area-inset-bottom));background:rgba(255,255,255,.96);border-top:1px solid rgba(31,88,82,.12);box-shadow:0 -8px 24px rgba(25,72,68,.08)}.action-bar button{min-height:44px;border:0;border-radius:12px;font-weight:600}.action-bar .return{color:#8a5d19;background:#fff0cf}.action-bar .reject{color:#a94242;background:#fde8e8}.action-bar .approve{color:#fff;background:#278d80}.edit-actions button:disabled,.action-bar button:disabled,.page-header button:disabled{opacity:.55}
.page-header{grid-template-columns:44px 1fr 44px}
.page-header button{width:44px;height:44px;font-size:20px}
.state-card button{min-height:44px}
button:focus-visible,input:focus-visible,textarea:focus-visible{outline:3px solid rgba(39,141,128,.28);outline-offset:2px}
.mobile-purchase-approval{color:var(--mobile-color-ink);background:var(--mobile-color-page)}
.page-header{border-bottom-color:var(--mobile-color-line);background:var(--mobile-color-surface)}
.edit-card,.summary-card,.timeline-card,.action-log-card,.state-card,.handled-card{border-color:var(--mobile-color-line);border-radius:var(--mobile-radius-lg);box-shadow:none}
.edit-card input,.edit-card textarea{font-size:16px}
.edit-actions .submit,.action-bar .approve,.state-card button{background:var(--mobile-color-primary)}
</style>
