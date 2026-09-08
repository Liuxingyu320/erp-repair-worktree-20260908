<template>
  <mobile-hr-shell
    title="入职详情"
    :back="goBack"
    :loading="loading"
    :error="error"
    :modal-open="modalOpen"
    @retry="loadDetail"
  >
    <main v-if="detail" class="detail-page" aria-labelledby="onboarding-detail-title">
      <header class="identity-header">
        <div>
          <p class="eyebrow">{{ detail.onboardingNo || "入职单" }}</p>
          <h1 id="onboarding-detail-title">{{ detail.employeeName || "未命名员工" }}</h1>
          <p>{{ detail.phoneNumberMasked || "手机号未填写" }} · {{ organizationLabel }}</p>
        </div>
        <span class="status-pill" :class="`is-${String(detail.status || '').toLowerCase()}`">{{ statusLabel }}</span>
      </header>
      <p v-if="confirmRefreshWarning" class="inline-warning" role="status">{{ confirmRefreshWarning }}</p>

      <section class="progress-grid" aria-label="入职必填完成度">
        <div>
          <div class="progress-label"><span>入职必填完成度</span><strong>{{ safePercent(detail.onboardingCompletionPercent) }}%</strong></div>
          <div class="progress-track"><span :style="{ width: safePercent(detail.onboardingCompletionPercent) + '%' }" /></div>
          <small>已填 {{ detail.onboardingCompletedFieldCount || 0 }}/{{ detail.onboardingRequiredFieldCount || 0 }}</small>
        </div>
        <div v-if="linkedEmployeeProfile">
          <div class="progress-label"><span>档案覆盖度</span><strong>{{ safePercent(linkedEmployeeProfile.profileCompletionPercent) }}%</strong></div>
          <div class="progress-track is-profile"><span :style="{ width: safePercent(linkedEmployeeProfile.profileCompletionPercent) + '%' }" /></div>
          <small>已填 {{ linkedEmployeeProfile.profileCompletedFieldCount || 0 }}/适用 {{ linkedEmployeeProfile.profileApplicableFieldCount || 0 }}</small>
        </div>
      </section>

      <section class="detail-section" aria-labelledby="basic-title">
        <h2 id="basic-title">入职信息</h2>
        <dl class="info-list">
          <div><dt>预计入职</dt><dd>{{ detail.expectedEntryDate || "待填写" }}</dd></div>
          <div><dt>岗位</dt><dd>{{ detail.positionName || "待填写" }}</dd></div>
          <div><dt>人员类别</dt><dd>{{ employeeCategoryLabel(detail.employeeCategory) }}</dd></div>
          <div><dt>入职负责人</dt><dd>{{ detail.ownerName || "未分配" }}</dd></div>
          <div><dt>入职后补录期限</dt><dd>{{ postEntryLabel }}</dd></div>
        </dl>
      </section>

      <section class="detail-section" aria-labelledby="masked-title">
        <h2 id="masked-title">脱敏档案</h2>
        <dl class="info-list">
          <div v-for="item in maskedRows" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value || "未填写" }}</dd></div>
        </dl>
      </section>

      <section class="detail-section" aria-labelledby="missing-title">
        <div class="section-heading">
          <h2 id="missing-title">资料补充情况</h2>
        </div>
        <div class="missing-summary">
          <strong>当前入职必填缺 {{ onboardingMissingCount }} 项</strong>
          <span>另有 {{ profileMissingCount }} 项可后续完善（不影响确认入职）</span>
        </div>
        <p v-if="!missingGroups.length" class="empty-state">当前入职资料已齐全，暂无入职后待补资料</p>
        <div v-else class="missing-list">
          <div v-for="group in missingGroups" :key="group.key" class="missing-group">
            <div><strong>{{ group.label }}</strong><span>{{ group.fields.length }} 项</span></div>
            <ul><li v-for="field in group.fields" :key="field.key || field.label">{{ field.label || field.key }}</li></ul>
          </div>
        </div>
      </section>

      <section class="detail-section" aria-labelledby="risk-title">
        <h2 id="risk-title">账号配置风险</h2>
        <p v-if="!riskCodes.length" class="empty-state">暂未发现账号配置风险</p>
        <ul v-else class="risk-list"><li v-for="risk in riskCodes" :key="risk">{{ riskLabel(risk) }}</li></ul>
      </section>

      <section v-if="readinessBlockingCodes.length" class="detail-section blocker-section" aria-labelledby="blocker-title">
        <h2 id="blocker-title">流程阻塞原因</h2>
        <ul class="risk-list"><li v-for="code in readinessBlockingCodes" :key="code">{{ blockerLabel(code) }}</li></ul>
        <p class="blocker-help">请先修复组织岗位主数据，刷新后即可继续。</p>
      </section>

      <section class="detail-section" aria-labelledby="log-title">
        <h2 id="log-title">操作记录</h2>
        <p v-if="!presentedOperationLogs.length" class="empty-state">暂无操作记录</p>
        <ol v-else class="timeline">
          <li v-for="(log, index) in presentedOperationLogs" :key="`${log.time || 'log'}-${index}`">
            <time>{{ log.time }}</time>
            <strong>{{ log.operator }} · {{ log.title }}</strong>
            <p>{{ log.description }}</p>
          </li>
        </ol>
      </section>
    </main>

    <template v-if="detail && availableActions.length" #footer>
      <div class="action-footer">
        <button v-if="primaryAction" class="primary-action" type="button" :disabled="actionBusy" @click="performAction(primaryAction.key, $event)">
          {{ primaryActionLabel }}
        </button>
        <button v-if="secondaryActions.length" ref="actionTrigger" class="more-action" type="button"
          aria-haspopup="dialog" :disabled="actionBusy" @click="openActionSheet">更多操作</button>
      </div>
    </template>

    <template #overlay>
      <div v-if="actionSheetVisible" class="modal-layer action-layer" role="presentation" @click.self="closeActionSheet">
        <section ref="actionSheet" class="action-sheet" role="dialog" aria-modal="true" aria-labelledby="action-sheet-title"
          tabindex="-1" @keydown="handleDialogKeydown($event, closeActionSheet, 'actionSheet')">
          <header><h2 id="action-sheet-title">更多操作</h2><button type="button" aria-label="关闭更多操作" @click="closeActionSheet"><i class="el-icon-close" aria-hidden="true" /></button></header>
          <button v-for="action in secondaryActions" :key="action.key" type="button" :disabled="actionBusy"
            @click="performAction(action.key)">{{ action.label }}</button>
        </section>
      </div>

      <div v-else-if="cancelVisible" class="modal-layer" role="presentation" @click.self="closeCancel">
        <section ref="cancelDialog" class="dialog-card" role="dialog" aria-modal="true" aria-labelledby="cancel-title"
          tabindex="-1" @keydown="handleDialogKeydown($event, closeCancel, 'cancelDialog')">
          <header><h2 id="cancel-title">取消入职</h2><button type="button" aria-label="关闭取消入职" :disabled="actionSubmitting" @click="closeCancel"><i class="el-icon-close" aria-hidden="true" /></button></header>
          <label>取消原因<textarea v-model="cancelReason" rows="4" maxlength="200" @input="cancelError = ''" /></label>
          <p v-if="cancelError" class="form-error" role="alert">{{ cancelError }}</p>
          <footer><button type="button" :disabled="actionSubmitting" @click="closeCancel">暂不取消</button><button class="danger-action" type="button" :disabled="actionSubmitting" @click="submitCancel">确认取消</button></footer>
        </section>
      </div>

      <div v-else-if="confirmVisible" class="modal-layer" role="presentation" @click.self="closeConfirm">
        <section ref="confirmDialog" class="dialog-card confirm-card" role="dialog" aria-modal="true" aria-labelledby="confirm-title"
          tabindex="-1" @keydown="handleDialogKeydown($event, closeConfirm, 'confirmDialog')">
          <header><h2 id="confirm-title">确认入职</h2><button type="button" aria-label="关闭确认入职" :disabled="confirmSubmitting" @click="closeConfirm"><i class="el-icon-close" aria-hidden="true" /></button></header>
          <div v-if="conflictLoading" class="async-state" role="status" aria-live="polite">正在检查账号冲突…</div>
          <div v-else-if="conflictError" class="async-state is-error" role="alert"><p>{{ conflictError }}</p><button type="button" @click="loadConflicts">重新检查</button></div>
          <template v-else>
            <label>实际入职日期<input v-model="confirmModel.actualEntryDate" type="date" @input="confirmError = ''"></label>
            <fieldset>
              <legend>冲突处理</legend>
              <label class="radio-row"><input v-model="confirmModel.conflictAction" type="radio" value="CREATE_NEW" :disabled="hasBlockingConflict" @change="selectConflictAction">新建员工账号</label>
              <label class="radio-row"><input v-model="confirmModel.conflictAction" type="radio" value="BIND_EXISTING" :disabled="!eligibleCandidates.length" @change="selectConflictAction">绑定现有账号</label>
              <label class="radio-row"><input v-model="confirmModel.conflictAction" type="radio" value="REHIRE_EXISTING" :disabled="!rehireCandidates.length" @change="selectConflictAction">恢复原账号（再入职）</label>
            </fieldset>
            <div v-if="conflicts.length" class="candidate-list" aria-label="冲突候选">
              <label v-for="candidate in conflicts" :key="candidate.key" class="candidate-card" :class="{ 'is-disabled': !candidateSelectable(candidate) }">
                <input v-if="['BIND_EXISTING', 'REHIRE_EXISTING'].includes(confirmModel.conflictAction)" v-model="confirmModel.bindUserId" type="radio"
                  :value="candidate.candidateUserId" :disabled="!candidateSelectable(candidate)" @change="confirmError = ''">
                <span><strong>{{ candidate.name || "未命名候选" }}</strong><small>{{ candidate.phoneNumberMasked || "手机号未提供" }}</small><small>{{ candidate.departmentLabel || "部门未提供" }}</small></span>
              </label>
            </div>
            <p v-if="confirmError" class="form-error" role="alert">{{ confirmError }}</p>
          </template>
          <footer><button type="button" :disabled="confirmSubmitting" @click="closeConfirm">取消</button><button class="primary-action" type="button"
            :disabled="confirmSubmitting || conflictLoading || !!conflictError" @click="submitConfirm">确认入职</button></footer>
        </section>
      </div>

      <div v-else-if="resultVisible" class="modal-layer" role="presentation">
        <section ref="resultDialog" class="dialog-card result-card" role="dialog" aria-modal="true" aria-labelledby="result-title"
          tabindex="-1" @keydown="handleDialogKeydown($event, closeOtp, 'resultDialog')">
          <header><h2 id="result-title">入职确认结果</h2></header>
          <template v-if="oneTimePassword">
            <p>一次性初始密码仅在此处展示一次，请通过安全方式交付员工。</p>
            <code class="otp-value">{{ oneTimePassword }}</code>
            <p class="security-note">首次登录必须修改密码；临时凭证过期后将无法登录。</p>
            <p v-if="oneTimePasswordExpiresAtDisplay" class="security-note">临时密码过期时间：{{ oneTimePasswordExpiresAtDisplay }}</p>
            <p class="security-note">请勿复制到不受控应用，关闭后无法恢复。</p>
          </template>
          <template v-else><p>{{ resultMessage }}</p><p v-if="confirmResult && confirmResult.employeeNo">员工编号：{{ confirmResult.employeeNo }}</p></template>
          <footer><button class="primary-action" type="button" @click="closeOtp">我已知晓</button></footer>
        </section>
      </div>
    </template>
  </mobile-hr-shell>
</template>

<script>
import {
  cancelHrOnboarding,
  confirmHrOnboarding,
  getHrOnboarding,
  getHrOnboardingConflicts,
  markHrOnboardingReady,
  restoreHrOnboarding,
  returnHrOnboardingToDraft
} from "@/api/hr/onboarding"
import { checkPermi } from "@/utils/permission"
import { getHrEmployee } from "@/api/hr/employee"
import { presentOnboardingLog } from "@/views/hr/onboarding/onboardingFieldConfig"
import MobileHrShell from "../components/MobileHrShell"
import { mobileHrErrorMessage } from "../mobileHrError"

const ACTIONS = [
  { key: "EDIT", label: "编辑资料", permission: "hr:onboarding:edit" },
  { key: "MARK_READY", label: "标记资料就绪", permission: "hr:onboarding:ready" },
  { key: "RETURN_TO_DRAFT", label: "退回草稿", permission: "hr:onboarding:return" },
  { key: "CONFIRM", label: "确认入职", permission: "hr:onboarding:confirm" },
  { key: "CANCEL", label: "取消入职", permission: "hr:onboarding:cancel" },
  { key: "RESTORE", label: "恢复入职", permission: "hr:onboarding:restore" }
]
const STATUS_LABELS = { DRAFT: "草稿", READY: "待确认", CONFIRMED: "已入职", CANCELLED: "已取消" }
const RISK_LABELS = {
  ACCOUNT_ALREADY_BOUND: "手机号已绑定账号",
  DUPLICATE_ACTIVE_ONBOARDING: "存在重复的有效入职单",
  ACCOUNT_CONFIGURATION_FAILED: "账号或权限配置失败",
  ROLE_CONFIGURATION_MISSING: "岗位角色配置缺失",
  DATA_SCOPE_CONFIGURATION_MISSING: "数据权限配置缺失",
  ACCOUNT_CONFIGURATION_MISSING: "账号或权限配置缺失"
}
const BLOCKER_LABELS = {
  DERIVED_COMPANY_MISSING: "目标组织无法识别所属公司",
  DERIVED_DEPARTMENT_SUPERVISOR_MISSING: "入职资料已保存；目标组织未配置部门负责人，请由管理员在桌面端组织管理中补充后刷新",
  DERIVED_STORE_MISSING: "目标门店信息未能自动生成",
  DERIVED_POSITION_MISSING: "目标岗位信息未能自动生成",
  STATUS_NOT_READY: "当前入职单尚未进入待确认状态",
  ACTUAL_ENTRY_DATE_REQUIRED: "请填写实际入职日期"
}
const GROUP_LABELS = { IDENTITY: "身份资料", ORGANIZATION: "组织岗位", EMPLOYMENT: "合同社保", CONTRACT: "合同社保", SOCIAL: "合同社保" }
const EMPLOYEE_CATEGORY_LABELS = { FULL_TIME: "正式员工", INTERN: "实习生" }

function cleanQueryText(value) {
  const first = Array.isArray(value) ? value[0] : value
  return first === undefined || first === null ? "" : String(first).trim()
}
function numericId(value) {
  const text = cleanQueryText(value)
  return /^\d+$/.test(text) && Number(text) > 0 ? Number(text) : null
}
function createIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID()
  const bytes = new Uint8Array(16)
  if (typeof crypto !== "undefined" && crypto.getRandomValues) crypto.getRandomValues(bytes)
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, "0"))
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`
}
function errorBody(error) {
  if (!error || typeof error !== "object") return {}
  if (error.response && error.response.data && typeof error.response.data === "object") return error.response.data
  if (error.data && typeof error.data === "object") return error.data
  return error
}
function isVersionConflict(error) { return errorBody(error).errorCode === "ONBOARDING_VERSION_CONFLICT" }
function safeCandidate(candidate, index) {
  const source = candidate && typeof candidate === "object" ? candidate : {}
  const normalizedId = numericId(source.candidateUserId)
  return {
    key: `candidate-${normalizedId === null ? "invalid" : normalizedId}-${index}`,
    candidateUserId: normalizedId,
    name: source.name || "",
    phoneNumberMasked: source.maskedPhone || "",
    departmentLabel: source.departmentLabel || "",
    eligibleForBind: Boolean(normalizedId !== null && source.eligibleForBind && Array.isArray(source.allowedDecisions) && source.allowedDecisions.includes("BIND_EXISTING")),
    eligibleForRehire: Boolean(normalizedId !== null && source.eligibleForRehire && Array.isArray(source.allowedDecisions) && source.allowedDecisions.includes("REHIRE_EXISTING")),
    blocking: Boolean(source.blocking)
  }
}
function safeConfirmResult(result) {
  const source = result && typeof result === "object" ? result : {}
  return {
    onboardingId: source.onboardingId,
    userId: source.userId,
    employeeNo: source.employeeNo,
    accountStatus: source.accountStatus,
    replayed: Boolean(source.replayed),
    riskCodes: Array.isArray(source.riskCodes) ? source.riskCodes.slice() : []
  }
}
function dialogControls(root) {
  if (!root || !root.querySelectorAll) return []
  return Array.from(root.querySelectorAll('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
}
function missingFieldIdentity(field) {
  if (field && typeof field === "object") return field.key || field.label || ""
  return String(field || "")
}
function normalizedMissingGroups(value, prefix, label, excludedFields) {
  if (!value || typeof value !== "object") return []
  const seen = new Set(excludedFields || [])
  return Object.keys(value).reduce((groups, group) => {
    const fields = (Array.isArray(value[group]) ? value[group] : []).filter(field => {
      const identity = missingFieldIdentity(field)
      if (!identity) return true
      if (seen.has(identity)) return false
      seen.add(identity)
      return true
    })
    if (fields.length) groups.push({ key: `${prefix}-${group}`, label: `${label} · ${GROUP_LABELS[group] || "其他资料"}`, fields })
    return groups
  }, [])
}
function missingFieldIdentities(groups) {
  return groups.reduce((identities, group) => identities.concat(group.fields.map(missingFieldIdentity).filter(Boolean)), [])
}

export default {
  name: "MobileHrOnboardingDetail",
  components: { MobileHrShell },
  data() {
    return {
      detail: null, linkedEmployeeProfile: null, loading: false, error: "", detailRequestSequence: 0, confirmedRefreshSequence: 0,
      actionSubmitting: false, actionRequestSequence: 0,
      actionSheetVisible: false, actionTriggerElement: null,
      cancelVisible: false, cancelReason: "", cancelError: "",
      confirmVisible: false, conflictLoading: false, conflictError: "", conflicts: [],
      conflictRequestSequence: 0, confirmSubmitting: false, confirmRequestSequence: 0,
      confirmModel: { actualEntryDate: "", conflictAction: "CREATE_NEW", bindUserId: null },
      confirmError: "", idempotencyKey: "", confirmResult: null,
      resultVisible: false, oneTimePassword: null, oneTimePasswordExpiresAt: null, resultMessage: "", confirmRefreshWarning: ""
    }
  },
  computed: {
    oneTimePasswordExpiresAtDisplay() {
      if (!this.oneTimePasswordExpiresAt) return ""
      const value = new Date(this.oneTimePasswordExpiresAt)
      if (Number.isNaN(value.getTime())) return String(this.oneTimePasswordExpiresAt)
      const pad = part => String(part).padStart(2, "0")
      return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}`
    },
    statusLabel() { return STATUS_LABELS[this.detail && this.detail.status] || (this.detail && this.detail.status ? "未知入职状态" : "未知") },
    organizationLabel() {
      const row = this.detail || {}
      return row.storeName || row.deptLevel3Name || row.deptLevel2Name || row.deptLevel1Name || row.companyName || "组织待填写"
    },
    postEntryLabel() {
      if (!this.detail || !this.detail.postEntryDueDate) return "未设置"
      return `${this.detail.postEntryDueDate}${this.detail.postEntryOverdue ? "（已逾期）" : ""}`
    },
    maskedRows() {
      const row = this.detail || {}
      return [
        { label: "证件号码", value: row.idNumberMasked },
        { label: "银行卡号", value: row.bankAccountMasked },
        { label: "户口所在地", value: row.registeredResidenceMasked },
        { label: "现居地址", value: row.currentAddressMasked },
        { label: "紧急联系人电话", value: row.emergencyContactPhoneMasked }
      ]
    },
    onboardingMissingGroups() {
      return normalizedMissingGroups(this.detail && this.detail.missingOnboardingFields, "onboarding", "当前入职需补")
    },
    profileMissingGroups() {
      return normalizedMissingGroups(
        this.detail && this.detail.missingProfileFields,
        "profile",
        "入职后待补",
        missingFieldIdentities(this.onboardingMissingGroups)
      )
    },
    missingGroups() { return this.onboardingMissingGroups.concat(this.profileMissingGroups) },
    onboardingMissingCount() { return this.onboardingMissingGroups.reduce((total, group) => total + group.fields.length, 0) },
    profileMissingCount() { return this.profileMissingGroups.reduce((total, group) => total + group.fields.length, 0) },
    riskCodes() { return this.detail && Array.isArray(this.detail.accountConfigurationRiskCodes) ? this.detail.accountConfigurationRiskCodes : [] },
    readinessBlockingCodes() { return this.detail && Array.isArray(this.detail.readinessBlockingCodes) ? this.detail.readinessBlockingCodes : [] },
    operationLogs() { return this.detail && Array.isArray(this.detail.operationLogs) ? this.detail.operationLogs : [] },
    presentedOperationLogs() { return this.operationLogs.map(presentOnboardingLog).filter(Boolean) },
    availableActions() { return ACTIONS.filter(action => this.actionPermitted(action.key)) },
    primaryAction() {
      if (this.detail && this.detail.status === "DRAFT") {
        return this.availableActions.find(action => action.key === "MARK_READY") ||
          this.availableActions.find(action => action.key === "EDIT") || null
      }
      if (this.detail && this.detail.status === "READY") return this.availableActions.find(action => action.key === "CONFIRM") || null
      return this.availableActions.length === 1 ? this.availableActions[0] : null
    },
    primaryActionLabel() { return this.primaryAction && this.primaryAction.key === "EDIT" ? "补充资料" : (this.primaryAction ? this.primaryAction.label : "") },
    secondaryActions() { return this.availableActions.filter(action => !this.primaryAction || action.key !== this.primaryAction.key) },
    eligibleCandidates() { return this.conflicts.filter(candidate => candidate.eligibleForBind) },
    rehireCandidates() { return this.conflicts.filter(candidate => candidate.eligibleForRehire) },
    hasBlockingConflict() { return this.conflicts.some(candidate => candidate.blocking) },
    actionBusy() { return this.actionSubmitting || this.confirmSubmitting },
    modalOpen() { return this.actionSheetVisible || this.cancelVisible || this.confirmVisible || this.resultVisible }
  },
  watch: {
    "$route.params.id"() {
      this.invalidateRequests()
      this.disposeSecrets()
      this.closeAllDialogs()
      this.detail = null
      this.linkedEmployeeProfile = null
      this.loadDetail()
    }
  },
  created() { this.loadDetail() },
  beforeRouteLeave(to, from, next) {
    this.invalidateRequests()
    this.disposeSecrets()
    next()
  },
  beforeDestroy() {
    this.invalidateRequests()
    this.disposeSecrets()
  },
  methods: {
    numericRouteId() { return numericId(this.$route && this.$route.params && this.$route.params.id) },
    navigationQuery() {
      const stateKey = cleanQueryText(this.$route && this.$route.query && this.$route.query.stateKey)
      return /^qs_[a-z0-9_]{12,80}$/i.test(stateKey) ? { stateKey } : undefined
    },
    goBack() { return this.$router.push({ path: "/mobile/hr/onboarding", query: this.navigationQuery() }).catch(() => {}) },
    invalidateRequests() {
      this.detailRequestSequence += 1
      this.confirmedRefreshSequence += 1
      this.actionRequestSequence += 1
      this.conflictRequestSequence += 1
      this.confirmRequestSequence += 1
      this.actionSubmitting = false
      this.confirmSubmitting = false
      this.conflictLoading = false
    },
    loadDetail() {
      const onboardingId = this.numericRouteId()
      const sequence = ++this.detailRequestSequence
      this.confirmedRefreshSequence += 1
      this.error = ""
      this.confirmRefreshWarning = ""
      this.linkedEmployeeProfile = null
      if (!onboardingId) {
        this.loading = false
        this.detail = null
        this.error = "入职记录编号无效，请返回列表重试"
        return Promise.resolve(null)
      }
      this.loading = true
      return getHrOnboarding(onboardingId)
        .then(response => {
          if (sequence !== this.detailRequestSequence || onboardingId !== this.numericRouteId()) return null
          const row = response && response.data ? response.data : response
          if (!row || Number(row.onboardingId) !== onboardingId) throw new Error("返回的入职记录不匹配")
          this.detail = row
          return this.loadLinkedEmployeeProfile(row, sequence, onboardingId).then(() => row)
        })
        .catch(error => {
          if (sequence !== this.detailRequestSequence) return null
          this.detail = null
          this.error = mobileHrErrorMessage(error, "暂时无法加载入职详情")
          return null
        })
        .finally(() => { if (sequence === this.detailRequestSequence) this.loading = false })
    },
    loadLinkedEmployeeProfile(row, sequence, onboardingId) {
      const linkedUserId = numericId(row && row.linkedUserId)
      if (linkedUserId === null || !checkPermi(["hr:employee:query"])) return Promise.resolve(null)
      return getHrEmployee(row.linkedUserId).then(response => {
        if (sequence !== this.detailRequestSequence || onboardingId !== this.numericRouteId()) return null
        this.linkedEmployeeProfile = response && response.data ? response.data : null
        return this.linkedEmployeeProfile
      }).catch(() => {
        if (sequence === this.detailRequestSequence && onboardingId === this.numericRouteId()) {
          this.linkedEmployeeProfile = null
        }
        return null
      })
    },
    safePercent(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Math.min(100, Math.max(0, number)) : 0
    },
    employeeCategoryLabel(value) { return EMPLOYEE_CATEGORY_LABELS[value] || (/[^\x00-\x7F]/.test(String(value || "")) ? value : "其他人员类别") },
    riskLabel(code) { return RISK_LABELS[code] || "其他账号风险" },
    blockerLabel(code) { return BLOCKER_LABELS[code] || "流程暂被未知配置问题阻塞" },
    actionPermitted(key) {
      const config = ACTIONS.find(action => action.key === key)
      const allowed = this.detail && Array.isArray(this.detail.allowedActions) && this.detail.allowedActions.includes(key)
      if (!config || !allowed || !checkPermi([config.permission])) return false
      if (key === "CONFIRM") return this.detail.status === "READY" && this.safePercent(this.detail.onboardingCompletionPercent) === 100
      return true
    },
    openEdit() {
      if (!this.actionPermitted("EDIT")) return Promise.resolve(null)
      return this.$router.push({ path: `/mobile/hr/onboarding/${this.detail.onboardingId}/edit`, query: this.navigationQuery() }).catch(() => {})
    },
    performAction(key, event) {
      if (!this.actionPermitted(key) || this.actionBusy) return Promise.resolve(null)
      if (key === "EDIT") return this.openEdit()
      if (key === "CONFIRM") {
        this.captureActionTrigger(event)
        return this.openConfirm()
      }
      if (key === "CANCEL") {
        this.captureActionTrigger(event)
        return this.openCancel()
      }
      const requests = {
        MARK_READY: { request: markHrOnboardingReady, message: "已标记为资料就绪" },
        RETURN_TO_DRAFT: { request: returnHrOnboardingToDraft, message: "已退回草稿" },
        RESTORE: { request: restoreHrOnboarding, message: "已恢复入职单" }
      }
      const operation = requests[key]
      if (!operation) return Promise.resolve(null)
      this.closeActionSheet()
      return this.submitStateRequest(operation.request, { version: this.detail.version }, operation.message)
    },
    submitStateRequest(request, payload, successMessage) {
      if (this.actionSubmitting || !this.detail) return Promise.resolve(null)
      const onboardingId = this.detail.onboardingId
      const version = this.detail.version
      const sequence = ++this.actionRequestSequence
      this.actionSubmitting = true
      return request(onboardingId, payload)
        .then(response => {
          if (!this.isActiveAction(sequence, onboardingId, version)) return null
          const row = response && response.data ? response.data : response
          if (row && Number(row.onboardingId) === Number(onboardingId)) this.detail = row
          if (this.$message) this.$message.success(successMessage)
          this.refreshTodoSummaries()
          return row
        })
        .catch(error => {
          if (!this.isActiveAction(sequence, onboardingId, version)) return null
          if (isVersionConflict(error)) return this.refreshVersionConflict()
          if (this.$message) this.$message.error(mobileHrErrorMessage(error, "操作失败，请重试"))
          return null
        })
        .finally(() => { if (sequence === this.actionRequestSequence) this.actionSubmitting = false })
    },
    isActiveAction(sequence, onboardingId, version) {
      return sequence === this.actionRequestSequence && this.detail && this.numericRouteId() === Number(onboardingId) &&
        Number(this.detail.onboardingId) === Number(onboardingId) && this.detail.version === version
    },
    refreshVersionConflict() {
      this.closeAllDialogs()
      this.disposeSecrets()
      this.restoreDialogFocus()
      if (this.$message) this.$message.warning("入职单已更新，正在加载最新版本")
      return this.loadDetail()
    },
    refreshTodoSummaries() {
      const dispatch = this.$store && this.$store.dispatch
      if (typeof dispatch !== "function") return Promise.resolve()
      return dispatch.call(this.$store, "todo/refreshSummaries").catch(() => {})
    },
    openActionSheet(event) {
      this.actionTriggerElement = (event && event.currentTarget) || this.$refs.actionTrigger || null
      this.actionSheetVisible = true
      this.focusDialog("actionSheet")
    },
    captureActionTrigger(event) {
      if (this.actionTriggerElement) return
      const trigger = event && event.currentTarget
      if (trigger && trigger.focus) this.actionTriggerElement = trigger
    },
    closeActionSheet(restoreFocus = true) {
      this.actionSheetVisible = false
      if (restoreFocus) this.restoreDialogFocus()
    },
    openCancel() {
      if (!this.actionPermitted("CANCEL") || this.actionBusy) return Promise.resolve(null)
      this.closeActionSheet(false)
      this.cancelReason = ""
      this.cancelError = ""
      this.cancelVisible = true
      this.focusDialog("cancelDialog")
      return Promise.resolve()
    },
    closeCancel() {
      if (this.actionSubmitting) return false
      this.cancelVisible = false
      this.cancelReason = ""
      this.cancelError = ""
      this.restoreDialogFocus()
      return true
    },
    submitCancel() {
      if (!this.actionPermitted("CANCEL") || this.actionSubmitting) return Promise.resolve(null)
      const reason = String(this.cancelReason || "").trim()
      if (!reason) {
        this.cancelError = "取消原因不能为空"
        return Promise.resolve(null)
      }
      if (reason.length > 200) {
        this.cancelError = "取消原因不能超过 200 个字符"
        return Promise.resolve(null)
      }
      const version = this.detail.version
      return this.submitStateRequest(cancelHrOnboarding, { version, reason }, "已取消入职单").then(result => {
        if (result) this.closeCancel()
        return result
      })
    },
    openConfirm() {
      if (!this.actionPermitted("CONFIRM") || this.actionBusy) return Promise.resolve([])
      this.closeActionSheet(false)
      this.confirmVisible = true
      this.confirmModel = { actualEntryDate: "", conflictAction: "CREATE_NEW", bindUserId: null }
      this.confirmError = ""
      this.conflicts = []
      this.conflictError = ""
      this.idempotencyKey = createIdempotencyKey()
      this.focusDialog("confirmDialog")
      return this.loadConflicts()
    },
    loadConflicts() {
      if (!this.confirmVisible || !this.detail) return Promise.resolve([])
      const onboardingId = this.detail.onboardingId
      const version = this.detail.version
      const sequence = ++this.conflictRequestSequence
      this.conflictLoading = true
      this.conflictError = ""
      return getHrOnboardingConflicts(onboardingId)
        .then(response => {
          if (!this.isActiveConflict(sequence, onboardingId, version)) return []
          const rows = response && response.data ? response.data : response
          this.conflicts = (Array.isArray(rows) ? rows : []).map(safeCandidate)
          const preferred = this.detail.preferredConflictAction === "BIND_EXISTING"
            ? this.eligibleCandidates.find(candidate => candidate.candidateUserId === this.detail.preferredBindUserId)
            : null
          if (preferred) {
            this.confirmModel.conflictAction = "BIND_EXISTING"
            this.confirmModel.bindUserId = preferred.candidateUserId
          } else if (this.hasBlockingConflict && this.eligibleCandidates.length) {
            this.confirmModel.conflictAction = "BIND_EXISTING"
          } else if (this.hasBlockingConflict && this.rehireCandidates.length === 1) {
            this.confirmModel.conflictAction = "REHIRE_EXISTING"
            this.confirmModel.bindUserId = this.rehireCandidates[0].candidateUserId
          }
          return this.conflicts
        })
        .catch(error => {
          if (!this.isActiveConflict(sequence, onboardingId, version)) return []
          this.conflicts = []
          this.conflictError = mobileHrErrorMessage(error, "账号冲突检查失败，请重试")
          return []
        })
        .finally(() => { if (sequence === this.conflictRequestSequence) this.conflictLoading = false })
    },
    isActiveConflict(sequence, onboardingId, version) {
      return this.confirmVisible && sequence === this.conflictRequestSequence && this.detail &&
        this.numericRouteId() === Number(onboardingId) && this.detail.onboardingId === onboardingId && this.detail.version === version
    },
    selectConflictAction() {
      this.confirmModel.bindUserId = null
      if (this.confirmModel.conflictAction === "REHIRE_EXISTING" && this.rehireCandidates.length === 1)
        this.confirmModel.bindUserId = this.rehireCandidates[0].candidateUserId
      this.confirmError = ""
    },
    candidateSelectable(candidate) {
      if (this.confirmModel.conflictAction === "REHIRE_EXISTING") return Boolean(candidate && candidate.eligibleForRehire)
      return Boolean(candidate && candidate.eligibleForBind)
    },
    validateConfirmation() {
      if (!this.confirmModel.actualEntryDate) return "请选择实际入职日期"
      if (this.confirmModel.conflictAction === "CREATE_NEW") return this.hasBlockingConflict ? "存在阻断冲突，不能新建重复账号" : ""
      if (!["BIND_EXISTING", "REHIRE_EXISTING"].includes(this.confirmModel.conflictAction)) return "请选择有效的冲突处理方式"
      const candidates = this.confirmModel.conflictAction === "REHIRE_EXISTING" ? this.rehireCandidates : this.eligibleCandidates
      return candidates.some(candidate => candidate.candidateUserId === this.confirmModel.bindUserId)
        ? "" : (this.confirmModel.conflictAction === "REHIRE_EXISTING" ? "请选择可恢复的原账号" : "请选择可绑定的现有账号")
    },
    submitConfirm() {
      if (!this.actionPermitted("CONFIRM") || this.confirmSubmitting || this.conflictLoading || this.conflictError) return Promise.resolve(null)
      const validation = this.validateConfirmation()
      if (validation) {
        this.confirmError = validation
        return Promise.resolve(null)
      }
      const onboardingId = this.detail.onboardingId
      const version = this.detail.version
      const key = this.idempotencyKey
      const sequence = ++this.confirmRequestSequence
      const payload = {
        version,
        actualEntryDate: this.confirmModel.actualEntryDate,
        conflictAction: this.confirmModel.conflictAction,
        bindUserId: ["BIND_EXISTING", "REHIRE_EXISTING"].includes(this.confirmModel.conflictAction) ? this.confirmModel.bindUserId : null,
        idempotencyKey: key
      }
      this.confirmSubmitting = true
      this.confirmError = ""
      return confirmHrOnboarding(onboardingId, payload)
        .then(response => {
          const result = response && response.data ? response.data : response
          if (!this.isActiveConfirm(sequence, onboardingId, version, key)) {
            if (result && typeof result === "object") result.oneTimePassword = null
            return null
          }
          this.applyConfirmResult(result || {})
          return this.confirmResult
        })
        .catch(error => {
          if (!this.isActiveConfirm(sequence, onboardingId, version, key)) return null
          if (isVersionConflict(error)) return this.refreshVersionConflict()
          this.confirmError = mobileHrErrorMessage(error, "确认入职失败，请修正后重试")
          return null
        })
        .finally(() => { if (sequence === this.confirmRequestSequence) this.confirmSubmitting = false })
    },
    isActiveConfirm(sequence, onboardingId, version, key) {
      return this.confirmVisible && sequence === this.confirmRequestSequence && this.detail &&
        this.numericRouteId() === Number(onboardingId) && this.detail.onboardingId === onboardingId &&
        this.detail.version === version && this.idempotencyKey === key
    },
    applyConfirmResult(result) {
      const secret = result && result.oneTimePassword ? String(result.oneTimePassword) : null
      const expiresAt = result && result.oneTimePasswordExpiresAt ? result.oneTimePasswordExpiresAt : null
      this.confirmResult = safeConfirmResult(result)
      const resultVersion = Number(result && result.version)
      const version = Number.isInteger(resultVersion) && resultVersion >= 0
        ? resultVersion
        : (this.detail && this.detail.version)
      const onboardingId = this.detail && this.detail.onboardingId
      this.detail = Object.assign({}, this.detail, { status: "CONFIRMED", allowedActions: [], version })
      this.confirmRefreshWarning = ""
      if (result && typeof result === "object") {
        result.oneTimePassword = null
        result.oneTimePasswordExpiresAt = null
      }
      this.oneTimePassword = secret
      this.oneTimePasswordExpiresAt = secret ? expiresAt : null
      if (secret) this.resultMessage = "账号创建成功，一次性密码只会展示一次；首次登录必须改密。"
      else if (this.confirmResult.replayed) this.resultMessage = "账号已关联。本次为幂等重放，不会再次展示一次性密码；请通过正常的重置密码流程设置新密码。"
      else this.resultMessage = "员工账号已关联；如需设置登录凭证，请使用正常的重置密码流程。"
      this.confirmVisible = false
      this.resultVisible = true
      this.idempotencyKey = ""
      this.focusDialog("resultDialog")
      this.refreshTodoSummaries()
      this.refreshConfirmedDetail(onboardingId)
    },
    refreshConfirmedDetail(onboardingId) {
      const id = numericId(onboardingId)
      if (!id || id !== this.numericRouteId()) return Promise.resolve(null)
      const sequence = ++this.confirmedRefreshSequence
      this.confirmRefreshWarning = ""
      return getHrOnboarding(id)
        .then(response => {
          if (sequence !== this.confirmedRefreshSequence || id !== this.numericRouteId()) return null
          const row = response && response.data ? response.data : response
          const currentVersion = Number(this.detail && this.detail.version)
          const nextVersion = Number(row && row.version)
          if (!row || Number(row.onboardingId) !== id || row.status !== "CONFIRMED" ||
            (Number.isFinite(currentVersion) && Number.isFinite(nextVersion) && nextVersion < currentVersion)) {
            throw new Error("确认后的最新详情尚未就绪")
          }
          this.detail = row
          this.confirmRefreshWarning = ""
          return row
        })
        .catch(() => {
          if (sequence !== this.confirmedRefreshSequence || id !== this.numericRouteId()) return null
          this.confirmRefreshWarning = "入职已确认，但最新详情暂时无法刷新；当前操作已锁定。"
          if (this.$message) this.$message.warning("入职已确认，最新详情暂时无法刷新")
          return null
        })
    },
    closeConfirm() {
      if (this.confirmSubmitting) return false
      this.confirmVisible = false
      this.conflictRequestSequence += 1
      this.conflictLoading = false
      this.conflicts = []
      this.conflictError = ""
      this.confirmError = ""
      this.idempotencyKey = ""
      this.restoreDialogFocus()
      return true
    },
    closeOtp() {
      this.oneTimePassword = null
      this.oneTimePasswordExpiresAt = null
      this.resultVisible = false
      this.confirmResult = null
      this.resultMessage = ""
      this.restoreDialogFocus()
    },
    disposeSecrets() {
      this.oneTimePassword = null
      this.oneTimePasswordExpiresAt = null
      this.idempotencyKey = ""
      this.confirmResult = null
      this.resultMessage = ""
      this.resultVisible = false
    },
    closeAllDialogs() {
      this.actionSheetVisible = false
      this.cancelVisible = false
      this.confirmVisible = false
      this.resultVisible = false
    },
    focusDialog(refName) {
      if (!this.$nextTick) return
      this.$nextTick(() => {
        const root = this.$refs && this.$refs[refName]
        const first = dialogControls(root)[0]
        if (first && first.focus) first.focus()
        else if (root && root.focus) root.focus()
      })
    },
    restoreDialogFocus() {
      const target = this.actionTriggerElement
      this.actionTriggerElement = null
      if (target && target.focus) this.$nextTick(() => target.focus())
    },
    handleDialogKeydown(event, close, refName) {
      if (!event) return
      if (event.key === "Escape") {
        event.preventDefault()
        close()
        return
      }
      if (event.key !== "Tab") return
      const root = this.$refs && this.$refs[refName]
      const controls = dialogControls(root)
      if (!controls.length) return
      const first = controls[0]
      const last = controls[controls.length - 1]
      const active = typeof document !== "undefined" ? document.activeElement : null
      if (!controls.includes(active)) { event.preventDefault(); (event.shiftKey ? last : first).focus() }
      else if (event.shiftKey && active === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && active === last) { event.preventDefault(); first.focus() }
    }
  }
}
</script>

<style lang="scss" scoped>
.detail-page { padding: 16px 14px 112px; color: #17324d; }
.identity-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 4px 2px 16px; }
.identity-header h1 { margin: 2px 0 4px; font-size: 23px; line-height: 1.3; }
.identity-header p { margin: 0; color: #6b7f91; font-size: 13px; }
.identity-header .eyebrow { color: #0f8b83; font-weight: 700; }
.inline-warning { margin: 0 0 12px; padding: 10px 12px; border-radius: 9px; background: #fff4d8; color: #805b00; font-size: 12px; }
.status-pill { flex: none; padding: 5px 9px; border-radius: 999px; background: #eef4f7; color: #476173; font-size: 12px; font-weight: 700; }
.status-pill.is-ready { background: #fff4d8; color: #9a6500; }
.status-pill.is-confirmed { background: #daf6ec; color: #08785f; }
.status-pill.is-cancelled { background: #fde8e8; color: #b23a3a; }
.progress-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; padding: 14px; border: 1px solid #dce7ec; border-radius: 14px; background: #fff; }
.progress-label { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 8px; color: #607586; font-size: 12px; }
.progress-label strong { color: #17324d; }
.progress-track { height: 7px; overflow: hidden; border-radius: 99px; background: #e7f0f2; }
.progress-track span { display: block; height: 100%; border-radius: inherit; background: #0f9a8d; }
.progress-track.is-profile span { background: #3d7cc9; }
.detail-section { margin-top: 12px; padding: 16px; border: 1px solid #dce7ec; border-radius: 14px; background: #fff; }
.detail-section h2 { margin: 0 0 12px; font-size: 16px; }
.section-heading { display: flex; justify-content: space-between; align-items: center; }
.section-heading span { color: #718596; font-size: 12px; }
.missing-summary { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px 12px; margin: 0 0 12px; font-size: 13px; }
.missing-summary strong { color: #805b00; }
.missing-summary span { color: #607586; }
.info-list { margin: 0; }
.info-list > div { display: flex; justify-content: space-between; gap: 18px; min-height: 36px; padding: 8px 0; border-bottom: 1px solid #edf2f4; }
.info-list > div:last-child { border-bottom: 0; }
.info-list dt { color: #718596; font-size: 13px; }
.info-list dd { margin: 0; color: #17324d; font-size: 13px; text-align: right; overflow-wrap: anywhere; }
.empty-state { margin: 0; padding: 10px 0; color: #718596; text-align: center; }
.missing-group + .missing-group { margin-top: 12px; }
.missing-group > div { display: flex; justify-content: space-between; font-size: 13px; }
.missing-group > div span { color: #98700c; }
.missing-group ul, .risk-list { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0 0; padding: 0; list-style: none; }
.missing-group li, .risk-list li { padding: 5px 8px; border-radius: 7px; background: #fff4d8; color: #805b00; font-size: 12px; }
.risk-list li { background: #fde8e8; color: #a23434; }
.blocker-section { border-color: #fecaca; background: #fffafa; }
.blocker-help { margin: 10px 0 0; color: #991b1b; font-size: 12px; line-height: 1.55; }
.timeline { margin: 0; padding: 0 0 0 18px; border-left: 2px solid #dce7ec; list-style: none; }
.timeline li { position: relative; padding: 0 0 16px 10px; }
.timeline li::before { position: absolute; top: 4px; left: -24px; width: 8px; height: 8px; border-radius: 50%; background: #0f9a8d; content: ""; }
.timeline time { display: block; color: #718596; font-size: 11px; }
.timeline strong { display: block; margin-top: 3px; font-size: 13px; }
.timeline p { margin: 4px 0 0; color: #607586; font-size: 12px; }
.action-footer { display: flex; gap: 10px; width: 100%; padding: 10px 14px calc(10px + env(safe-area-inset-bottom)); border-top: 1px solid #dce7ec; background: rgba(255, 255, 255, .98); }
button, input, textarea { font: inherit; }
.action-footer button, .dialog-card button, .action-sheet button { min-height: 44px; border-radius: 10px; }
.primary-action { flex: 1; border: 0; background: #0f8b83; color: #fff; font-weight: 700; }
.more-action { min-width: 104px; border: 1px solid #bfd0d8; background: #fff; color: #385367; }
button:disabled { opacity: .48; }
.modal-layer { position: fixed; z-index: 1200; inset: 0; display: flex; align-items: center; justify-content: center; padding: 18px; background: rgba(11, 28, 40, .48); }
.action-layer { align-items: flex-end; padding: 0; }
.action-sheet { width: min(100%, 430px); padding: 14px 14px calc(14px + env(safe-area-inset-bottom)); border-radius: 18px 18px 0 0; background: #fff; outline: none; }
.action-sheet header, .dialog-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.action-sheet h2, .dialog-card h2 { margin: 0; font-size: 18px; }
.action-sheet header button, .dialog-card header button { min-width: 44px; border: 0; background: transparent; font-size: 24px; }
.action-sheet > button { width: 100%; margin-top: 8px; border: 0; background: #f2f6f7; color: #17324d; text-align: left; padding: 0 14px; }
.dialog-card { width: min(100%, 390px); max-height: calc(100dvh - 36px); overflow-y: auto; padding: 18px; border-radius: 16px; background: #fff; outline: none; }
.dialog-card > label { display: grid; gap: 7px; margin-top: 16px; color: #526a7c; font-size: 13px; }
.dialog-card textarea, .dialog-card input[type="date"] { box-sizing: border-box; width: 100%; min-height: 44px; padding: 10px; border: 1px solid #bfd0d8; border-radius: 9px; background: #fff; color: #17324d; }
.dialog-card footer { display: flex; justify-content: flex-end; gap: 9px; margin-top: 18px; }
.dialog-card footer button { min-width: 104px; padding: 0 14px; border: 1px solid #bfd0d8; background: #fff; color: #385367; }
.dialog-card footer .primary-action { border: 0; background: #0f8b83; color: #fff; }
.dialog-card footer .danger-action { border: 0; background: #c44242; color: #fff; }
.form-error, .async-state.is-error { color: #b43434; font-size: 13px; }
.async-state { padding: 26px 0; color: #607586; text-align: center; }
.confirm-card fieldset { margin: 16px 0 0; padding: 12px; border: 1px solid #dce7ec; border-radius: 10px; }
.confirm-card legend { padding: 0 5px; color: #526a7c; font-size: 13px; }
.radio-row { display: flex; align-items: center; gap: 10px; min-height: 44px; }
.candidate-list { display: grid; gap: 8px; margin-top: 12px; }
.candidate-card { display: flex; align-items: flex-start; gap: 10px; min-height: 58px; padding: 10px; border: 1px solid #dce7ec; border-radius: 10px; }
.candidate-card.is-disabled { opacity: .58; }
.candidate-card span { display: grid; gap: 3px; }
.candidate-card small { color: #718596; }
.result-card { text-align: center; }
.result-card header { justify-content: center; }
.otp-value { display: block; margin: 18px 0; padding: 14px; border-radius: 10px; background: #edf6f5; color: #0a665f; font-size: 20px; font-weight: 800; letter-spacing: 1px; overflow-wrap: anywhere; }
.security-note { color: #9a6500; font-size: 12px; }
@media (max-width: 360px) { .progress-grid { grid-template-columns: 1fr; } }
</style>
