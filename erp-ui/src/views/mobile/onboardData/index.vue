<template>
  <div
    :class="['mobile-onboard-data-page', 'mobile-system-page', { 'is-embedded': embedded }]"
    :data-mobile-scroll-root="embedded ? null : ''"
  >
    <header v-if="!embedded" class="mobile-onboard-data-header">
      <button type="button" class="back-button" aria-label="返回" @click="goBack"><i class="el-icon-arrow-left" aria-hidden="true" /></button>
      <div>
        <h1>{{ selectedRequest && isSignatureFirst ? '入职签约包' : '合同资料补全' }}</h1>
        <p>{{ selectedRequest ? detailSubtitle : '查看我的入职签约包与待补资料' }}</p>
      </div>
      <button type="button" class="refresh-button" :disabled="loading || submitting" @click="refresh">
        {{ loading ? '加载中' : '刷新' }}
      </button>
    </header>

    <component :is="embedded ? 'div' : 'main'" class="mobile-onboard-data-main">
      <section v-if="errorMessage" class="state-card is-error" role="alert">
        <strong>资料加载失败</strong>
        <span>{{ errorMessage }}</span>
        <button type="button" @click="refresh">重试</button>
      </section>

      <section v-else-if="loading" class="state-card" role="status">正在加载入职签约包...</section>

      <template v-else-if="!selectedRequest">
        <section v-if="!requests.length" class="state-card">
          <strong>当前没有待补资料</strong>
          <span>合同资料齐全后，HR 会继续生成并发送合同。</span>
        </section>
        <template v-else>
          <button
            v-for="item in requests"
            :key="item.requestId"
            type="button"
            class="request-card"
            @click="openRequest(item)"
          >
            <div>
              <strong>{{ item.title || '入职合同资料补充' }}</strong>
              <span>{{ missingSummary(item) }}</span>
              <small v-if="item.reviewReason || item.rejectReason">驳回原因：{{ item.reviewReason || item.rejectReason }}</small>
            </div>
            <em :class="statusClass(item)">{{ statusLabel(item) }}</em>
          </button>
        </template>
      </template>

      <template v-else>
        <section class="detail-card intro-card">
          <div>
            <span class="eyebrow">{{ isSignatureFirst ? '入职签约包 · 员工确认' : '入职合同补资' }}</span>
            <h2>{{ selectedRequest.employeeName || '本人资料' }}</h2>
            <p v-if="isSignatureFirst">请核对本签约包内的冻结事实和文件清单，并完成本签约包唯一一次手写签名。HR 选择公司与印章、生成并发送最终合同后，你只需逐份打开并确认文件，不再签名。</p>
            <p v-else>请按当前真实情况填写。你提交的是个人事实，不会直接改变合同类型、薪资或保险。</p>
          </div>
          <em :class="statusClass(selectedRequest)">{{ statusLabel(selectedRequest) }}</em>
        </section>

        <section v-if="selectedRequest.reviewReason || selectedRequest.rejectReason" class="detail-card rejection-card">
          <strong>HR 审核意见</strong>
          <p>{{ selectedRequest.reviewReason || selectedRequest.rejectReason }}</p>
        </section>

        <section v-if="isSignatureFirst" class="detail-card fact-card">
          <div class="section-heading">
            <div>
              <strong>本签约包冻结事实</strong>
              <p>以下内容来自本批次冻结快照；如有不符，请先停止签名并联系 HR 更正。</p>
            </div>
            <em>仅限本签约包</em>
          </div>
          <dl v-if="factConfirmationItems.length" class="fact-list">
            <div v-for="item in factConfirmationItems" :key="item.key">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
          <p v-else class="field-empty">签约事实快照暂不可用，请刷新；仍为空时请联系 HR 重新发送任务。</p>
          <div class="document-plan">
            <strong>本签约包文件清单</strong>
            <ul v-if="plannedDocumentNames.length">
              <li v-for="name in plannedDocumentNames" :key="name">{{ name }}</li>
            </ul>
            <p v-else>文件清单尚未返回，请联系 HR 核对后再签名。</p>
          </div>
        </section>

        <section class="detail-card form-card">
          <template v-if="editableFieldDefinitions.length">
            <label v-for="field in editableFieldDefinitions" :key="field.key" class="mobile-field">
              <span>{{ field.label }}<i v-if="field.required">*</i></span>
              <textarea
                v-if="field.type === 'textarea'"
                v-model.trim="formValues[field.key]"
                :disabled="!canEdit"
                :maxlength="field.maxlength || 200"
                :placeholder="field.placeholder"
                rows="3"
              />
              <select v-else-if="field.type === 'select'" v-model="formValues[field.key]" :disabled="!canEdit">
                <option value="" disabled>请选择</option>
                <option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <input
                v-else
                v-model.trim="formValues[field.key]"
                :disabled="!canEdit"
                :type="field.type || 'text'"
                :maxlength="field.maxlength || 100"
                :placeholder="field.placeholder"
              >
              <small v-if="field.help">{{ field.help }}</small>
            </label>
          </template>
          <div v-else-if="!isSignatureFirst" class="field-empty">当前任务没有可由员工填写的字段，请联系 HR 核对。</div>
          <div v-else class="field-empty">
            {{ signatureAlreadyCaptured ? '本任务无需再补充个人字段，已留存的唯一签名会继续保留。' : '本任务无需补充个人字段，请继续完成下方事实确认和手写签名。' }}
          </div>
        </section>

        <section v-if="requiresSignatureCapture" class="detail-card signature-card">
          <div class="section-heading">
            <div>
              <strong>本签约包唯一一次手写签名</strong>
              <p>该签名不会保存到员工全局档案，也不会用于其他签约包；最终文件只需确认，不会要求再次签名。</p>
            </div>
          </div>
          <div class="signature-box">
            <canvas
              ref="signatureCanvas"
              :aria-disabled="submitting"
              @pointerdown.prevent="startDraw"
              @pointermove.prevent="draw"
              @pointerup.prevent="finishDraw"
              @pointercancel.prevent="finishDraw"
              @lostpointercapture="finishDraw"
            ></canvas>
          </div>
          <button type="button" class="clear-signature-button" :disabled="submitting" @click="clearSignature">清空签名</button>
          <label class="mobile-field confirmation-field">
            <span>签名确认语<i>*</i></span>
            <strong v-if="signatureConfirmationPrompt" class="confirmation-copy">{{ signatureConfirmationPrompt }}</strong>
            <input
              v-model.trim="factConfirmationText"
              type="text"
              autocomplete="off"
              :disabled="submitting"
              placeholder="请在此完整输入上方文字"
            >
            <small>必须完整输入上方指定文字后才能提交。</small>
          </label>
        </section>

        <section v-else-if="isSignatureFirst && signatureAlreadyCaptured" class="detail-card signature-complete-card">
          <strong>本签约包唯一一次手写签名已留存</strong>
          <p v-if="canEdit">采集时间：{{ selectedRequest.signatureSampleTime || '已记录' }}。本次只需更正事实并重新提交，不会再次采集或覆盖签名。</p>
          <p v-else>采集时间：{{ selectedRequest.signatureSampleTime || '已记录' }}。后续只会应用于本签约包的最终合同，员工不再签名。</p>
        </section>

        <section class="detail-card boundary-card">
          <strong>以下内容由 HR 确定</strong>
          <p>劳务人员类型、商业保险、合同类型、社保类型、岗位职级、合同日期、薪资、签约主体和印章。</p>
        </section>

        <p v-if="validationMessage" class="validation-message" role="alert">{{ validationMessage }}</p>
        <p v-if="submitError" class="validation-message" role="alert">{{ submitError }}</p>

        <button
          v-if="canEdit"
          type="button"
          class="submit-button"
          :disabled="submitting || !canSubmitCurrentRequest"
          @click="submit"
        >{{ submitting ? '提交中...' : submitButtonText }}</button>
        <section v-else class="state-card submitted-card">
          <strong>{{ completedStateTitle }}</strong>
          <span>{{ completedStateDescription }}</span>
        </section>
      </template>
    </component>
  </div>
</template>

<script>
import {
  getOnboardSignDataRequest,
  listMyOnboardSignDataRequests,
  submitOnboardSignDataRequest
} from "@/api/oa/signTask"
const { mobileErrorMessage } = require("../mobileErrorMessage")

const EMPLOYEE_FIELD_DEFINITIONS = Object.freeze({
  currentAddress: {
    key: "currentAddress", label: "现住址", type: "textarea", required: true, maxlength: 255,
    placeholder: "请填写当前实际居住地址（不要用身份证登记地址代替）"
  },
  studentStatus: {
    key: "studentStatus", label: "当前是否在校", type: "select", required: true,
    options: [{ value: "STUDENT", label: "在校" }, { value: "NON_STUDENT", label: "非在校" }]
  },
  schoolName: { key: "schoolName", label: "学校名称及学籍说明", type: "text", maxlength: 100, placeholder: "在校时填写" },
  retirementStatus: {
    key: "retirementStatus", label: "当前是否已退休", type: "select", required: true,
    options: [{ value: "RETIRED", label: "已退休" }, { value: "NOT_RETIRED", label: "未退休" }]
  },
  incomeStartYearMonth: {
    key: "incomeStartYearMonth", label: "以个人劳动收入为主要生活来源的起始月份", type: "month", required: true,
    help: "仅 16—18 周岁且非在校员工的 2-10 声明使用。"
  }
})
const EMPLOYEE_FIELD_KEYS = Object.freeze(Object.keys(EMPLOYEE_FIELD_DEFINITIONS))
const MIN_SIGNATURE_DISTANCE = 24
const MIN_SIGNATURE_POINTS = 4
const FACT_CONFIRMATION_FIELDS = Object.freeze([
  ["employeeName", "姓名"], ["idNumber", "身份证号码"], ["phone", "联系电话"],
  ["currentAddress", "现住址"], ["contractTypeCode", "合同类型"], ["socialTypeCode", "社保状态"],
  ["employeePost", "岗位"], ["jobGradeCode", "职级"], ["workLocation", "工作地点"],
  ["cityLevel", "城市等级"], ["contractTermCode", "合同期限类型"],
  ["contractStartDate", "合同开始日"], ["contractEndDate", "合同结束日"],
  ["probationStartDate", "试用期开始日"], ["probationEndDate", "试用期结束日"],
  ["workSchedule", "工时制度"], ["salaryTotal", "综合工资"], ["baseSalary", "底薪"],
  ["postSalary", "岗位津贴"], ["fieldAllowance", "驻外补贴"],
  ["performanceSalary", "绩效津贴"], ["salaryVersion", "薪酬确认书版本"]
])
const FACT_CODE_LABELS = Object.freeze({
  LABOR_CONTRACT: "劳动合同", SERVICE_CONTRACT: "劳务合同",
  SOCIAL_INSURED: "有社保", SOCIAL_UNINSURED: "无社保",
  FIXED_TERM: "固定期限", OPEN_ENDED: "无固定期限",
  STANDARD: "标准工时制", A: "A版", B: "B版"
})

function normalizedId(value) {
  if (value === undefined || value === null) return ""
  const text = String(value).trim()
  return /^\d+$/.test(text) ? text.replace(/^0+/, "") : ""
}

function asObject(value) {
  if (!value) return {}
  if (typeof value === "object" && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {}
  } catch (ignored) { return {} }
}

export default {
  name: "MobileOnboardDataRequest",
  props: {
    embedded: { type: Boolean, default: false },
    requestId: { type: [String, Number], default: null }
  },
  data() {
    return {
      loading: false,
      submitting: false,
      requests: [],
      selectedRequest: null,
      formValues: {},
      errorMessage: "",
      submitError: "",
      validationMessage: "",
      factConfirmationText: "",
      signatureRequestId: "",
      hasSignature: false,
      drawing: false,
      lastPoint: null,
      activePointerId: null,
      currentStrokeDistance: 0,
      currentStrokePoints: 0,
      signatureInkDistance: 0,
      signatureInkPoints: 0,
      requestGeneration: 0,
      submitGeneration: 0,
      requestOwnershipDestroyed: false
    }
  },
  computed: {
    routeRequestId() {
      const requested = normalizedId(this.requestId)
      if (requested) return requested
      const route = this.$route || {}
      return normalizedId((route.query && route.query.requestId) || (route.params && route.params.requestId))
    },
    editableFieldDefinitions() {
      if (!this.selectedRequest) return []
      const raw = this.selectedRequest.editableFields || this.selectedRequest.allowedFields || this.selectedRequest.missingFields || []
      const keys = (Array.isArray(raw) ? raw : []).map(item => typeof item === "string" ? item : (item && (item.field || item.key))).filter(key => EMPLOYEE_FIELD_KEYS.includes(key))
      return Array.from(new Set(keys)).map(key => EMPLOYEE_FIELD_DEFINITIONS[key]).map(field => {
        if (field.key === "schoolName") {
          return this.formValues.studentStatus === "STUDENT"
            ? Object.assign({}, field, { required: true }) : null
        }
        if (field.key === "incomeStartYearMonth" && this.formValues.studentStatus !== "NON_STUDENT") return null
        return field
      }).filter(Boolean)
    },
    requestStatus() {
      return String((this.selectedRequest && (this.selectedRequest.status || this.selectedRequest.reviewStatus)) || "").toUpperCase()
    },
    isSignatureFirst() {
      return String((this.selectedRequest && this.selectedRequest.signingSequence) || "").toUpperCase() === "SIGNATURE_FIRST"
    },
    detailSubtitle() {
      return this.isSignatureFirst ? "确认签约包并完成唯一一次手写签名" : "填写个人事实，提交后由 HR 审核"
    },
    signatureConfirmationPrompt() {
      return String((this.selectedRequest && this.selectedRequest.signatureConfirmationPrompt) || "")
    },
    signatureAlreadyCaptured() {
      return !!(this.selectedRequest && this.selectedRequest.signatureCaptured)
    },
    requiresSignatureCapture() {
      return this.isSignatureFirst && this.canEdit && !this.signatureAlreadyCaptured
    },
    factConfirmationItems() {
      const snapshot = asObject(this.selectedRequest && this.selectedRequest.factSnapshot)
      return FACT_CONFIRMATION_FIELDS.map(([key, label]) => ({
        key, label, value: this.factDisplayValue(key, snapshot[key])
      })).filter(item => item.value !== "")
    },
    plannedDocumentNames() {
      const values = this.selectedRequest && (this.selectedRequest.plannedDocumentNames || this.selectedRequest.documentNames)
      return Array.from(new Set((Array.isArray(values) ? values : []).map(value => String(value || "").trim()).filter(Boolean)))
    },
    canSubmitCurrentRequest() {
      if (!this.canEdit) return false
      if (!this.isSignatureFirst) return this.editableFieldDefinitions.length > 0
      if (!this.factConfirmationItems.length || !this.plannedDocumentNames.length) return false
      if (this.signatureAlreadyCaptured) return true
      return this.hasSignature && !!this.signatureConfirmationPrompt &&
        this.factConfirmationText === this.signatureConfirmationPrompt
    },
    submitButtonText() {
      if (!this.isSignatureFirst) return "提交给 HR 审核"
      if (this.signatureAlreadyCaptured) return "更正事实并重新提交（无需再次签名）"
      return this.editableFieldDefinitions.length ? "补充资料并确认签约包（签名仅一次）" : "确认本签约包并签名（仅一次）"
    },
    completedStateTitle() {
      if (!this.isCompleted) return "资料已提交，待 HR 审核"
      return this.isSignatureFirst ? "本签约包唯一一次签名已完成" : "资料已完成"
    },
    completedStateDescription() {
      if (!this.isCompleted) return "审核通过前不会生成合同；如需修改，请联系 HR 驳回后重新提交。"
      if (this.isSignatureFirst) {
        return "请等待 HR 选择公司与印章并生成最终合同。HR 发送最终文件后，你只需在本签约包中核对确认，不再签名。"
      }
      return "HR 已核验并继续处理合同。"
    },
    canEdit() { return ["PENDING_EMPLOYEE", "REJECTED"].includes(this.requestStatus) },
    isCompleted() { return ["APPROVED", "COMPLETED"].includes(this.requestStatus) }
  },
  watch: {
    routeRequestId(value, previous) {
      if (value === previous) return
      if (value) this.loadDetail(value)
      else {
        this.selectedRequest = null
        this.loadList()
      }
    }
  },
  created() {
    if (this.routeRequestId) this.loadDetail(this.routeRequestId)
    else this.loadList()
  },
  mounted() {
    if (typeof window !== "undefined") window.addEventListener("resize", this.resizeSignatureCanvas)
  },
  beforeDestroy() {
    this.requestOwnershipDestroyed = true
    this.requestGeneration += 1
    this.submitGeneration += 1
    if (typeof window !== "undefined") window.removeEventListener("resize", this.resizeSignatureCanvas)
  },
  methods: {
    loadList() {
      if (this.requestOwnershipDestroyed) return Promise.resolve([])
      const generation = ++this.requestGeneration
      this.submitGeneration += 1
      this.submitting = false
      this.loading = true
      this.errorMessage = ""
      return listMyOnboardSignDataRequests().then(response => {
        if (!this.isCurrentRequestGeneration(generation)) return []
        const data = response && response.data !== undefined ? response.data : response
        const rows = Array.isArray(data) ? data : (data && (data.rows || data.items)) || []
        this.requests = rows.map(item => Object.assign({}, item, { requestId: normalizedId(item.requestId || item.id) }))
        return this.requests
      }).catch(error => {
        if (!this.isCurrentRequestGeneration(generation)) return []
        this.requests = []
        this.errorMessage = this.errorText(error, "合同补资任务加载失败")
        return []
      }).finally(() => {
        if (this.isCurrentRequestGeneration(generation)) this.loading = false
      })
    },
    loadDetail(requestId, options) {
      const normalized = normalizedId(requestId)
      if (!normalized || this.requestOwnershipDestroyed) return Promise.resolve(null)
      const internalRefreshOwner = options && options.internalRefreshOwner
      const generation = ++this.requestGeneration
      if (internalRefreshOwner) {
        internalRefreshOwner.requestGeneration = generation
      } else {
        this.submitGeneration += 1
        this.submitting = false
      }
      this.loading = true
      this.errorMessage = ""
      this.submitError = ""
      return getOnboardSignDataRequest(normalized).then(response => {
        if (!this.isCurrentRequestGeneration(generation)) return null
        const data = response && response.data !== undefined ? response.data : response
        this.selectedRequest = Object.assign({}, data || {}, { requestId: normalized })
        this.factConfirmationText = ""
        this.signatureRequestId = ""
        this.hasSignature = false
        this.resetSignatureMetrics()
        const submitted = asObject(this.selectedRequest.submittedValues || this.selectedRequest.employeeSubmittedValues)
        const initial = asObject(this.selectedRequest.currentValues || this.selectedRequest.defaultValues)
        this.formValues = Object.assign({}, initial, submitted)
        EMPLOYEE_FIELD_KEYS.forEach(key => {
          if (this.formValues[key] === undefined || this.formValues[key] === null) this.$set(this.formValues, key, "")
        })
        this.$emit("loaded", this.selectedRequest)
        return this.selectedRequest
      }).catch(error => {
        if (!this.isCurrentRequestGeneration(generation)) return null
        if (!internalRefreshOwner) this.selectedRequest = null
        this.errorMessage = this.errorText(error, "补资任务详情加载失败")
        return null
      }).finally(() => {
        if (!this.isCurrentRequestGeneration(generation)) return
        this.loading = false
        this.$nextTick(() => {
          if (this.isCurrentRequestGeneration(generation) && this.requiresSignatureCapture) this.initSignatureCanvas()
        })
      })
    },
    isCurrentRequestGeneration(generation) {
      return !this.requestOwnershipDestroyed && generation === this.requestGeneration
    },
    isCurrentSubmitOwnership(requestGeneration, submitGeneration, requestId, version) {
      return this.isCurrentRequestGeneration(requestGeneration) &&
        submitGeneration === this.submitGeneration &&
        normalizedId(this.selectedRequest && this.selectedRequest.requestId) === requestId &&
        String(this.selectedRequest && this.selectedRequest.version) === String(version)
    },
    openRequest(item) {
      const requestId = normalizedId(item && item.requestId)
      if (!requestId) return
      this.$router.push({ path: "/mobile/onboard-data", query: { requestId } }).catch(() => {})
    },
    validate() {
      for (const field of this.editableFieldDefinitions) {
        if (field.required && !String(this.formValues[field.key] || "").trim()) return `请填写${field.label}`
      }
      if (this.formValues.studentStatus === "STUDENT" && this.editableFieldDefinitions.some(field => field.key === "schoolName") && !String(this.formValues.schoolName || "").trim()) {
        return "在校状态下请填写学校信息"
      }
      if (this.isSignatureFirst) {
        if (!this.factConfirmationItems.length) return "签约事实快照不可用，请联系 HR 重新发送任务"
        if (!this.plannedDocumentNames.length) return "本次文件清单不可用，请联系 HR 重新发送任务"
        if (this.requiresSignatureCapture) {
          if (!this.hasSignature) return "请完成本签约包唯一一次手写签名"
          if (!this.signatureConfirmationPrompt || this.factConfirmationText !== this.signatureConfirmationPrompt) {
            return `请完整输入：${this.signatureConfirmationPrompt || "签名确认语"}`
          }
        }
      }
      return ""
    },
    submit() {
      if (this.submitting) return Promise.resolve(null)
      this.validationMessage = this.validate()
      if (this.validationMessage || !this.selectedRequest || !this.canEdit) return Promise.resolve(null)
      const requestId = normalizedId(this.selectedRequest.requestId)
      const version = this.selectedRequest.version
      const requestGeneration = this.requestGeneration
      const submitGeneration = ++this.submitGeneration
      const payload = { version }
      this.editableFieldDefinitions.forEach(field => { payload[field.key] = this.formValues[field.key] })
      if (this.requiresSignatureCapture) {
        const canvas = this.$refs.signatureCanvas
        if (!canvas || typeof canvas.toDataURL !== "function") {
          this.validationMessage = "签名图像暂不可用，请刷新后重新签名"
          return Promise.resolve(null)
        }
        if (!this.signatureRequestId) this.signatureRequestId = this.createSignatureRequestId()
        payload.factConfirmationText = this.factConfirmationText
        payload.signatureRequestId = this.signatureRequestId
        payload.signatureDataUrl = canvas.toDataURL("image/png")
      }
      const successMessage = this.signatureAlreadyCaptured
        ? "事实更正已提交，原唯一手写签名已保留"
        : (this.isSignatureFirst ? "入职签约包已确认，唯一一次手写签名已提交" : "资料已提交，请等待 HR 审核")
      this.submitting = true
      this.submitError = ""
      return submitOnboardSignDataRequest(requestId, payload).then(() => {
        if (!this.isCurrentSubmitOwnership(requestGeneration, submitGeneration, requestId, version)) return null
        this.$modal.msgSuccess(successMessage)
        const refreshOwnership = { submitGeneration, requestId, version }
        return this.loadDetail(requestId, { internalRefreshOwner: refreshOwnership }).then(detail => {
          if (detail) refreshOwnership.version = detail.version
          if (!detail || !this.isCurrentSubmitOwnership(
            refreshOwnership.requestGeneration,
            refreshOwnership.submitGeneration,
            requestId,
            refreshOwnership.version
          )) return null
          this.$emit("updated", detail)
          return detail
        }).finally(() => {
          if (this.isCurrentSubmitOwnership(
            refreshOwnership.requestGeneration,
            refreshOwnership.submitGeneration,
            requestId,
            refreshOwnership.version
          )) this.submitting = false
        })
      }).catch(error => {
        if (!this.isCurrentSubmitOwnership(requestGeneration, submitGeneration, requestId, version)) return null
        this.submitError = this.errorText(error, "资料提交结果暂未确认，请刷新任务确认状态后再重试")
        return null
      }).finally(() => {
        if (this.isCurrentSubmitOwnership(requestGeneration, submitGeneration, requestId, version)) {
          this.submitting = false
        }
      })
    },
    missingSummary(item) {
      const values = item && (item.missingFields || item.editableFields || item.allowedFields)
      const labels = (Array.isArray(values) ? values : []).map(value => {
        const key = typeof value === "string" ? value : (value && (value.field || value.key))
        return EMPLOYEE_FIELD_DEFINITIONS[key] ? EMPLOYEE_FIELD_DEFINITIONS[key].label : ""
      }).filter(Boolean)
      if (String(item && item.signingSequence || "").toUpperCase() === "SIGNATURE_FIRST") {
        return labels.length ? `待补：${labels.join("、")}；并确认签约包、完成唯一一次手写签名` : "待确认入职签约包并完成唯一一次手写签名"
      }
      return labels.length ? `待补：${labels.join("、")}` : "点击查看需补内容"
    },
    factDisplayValue(key, value) {
      if (value === undefined || value === null || String(value).trim() === "") return ""
      const text = String(value).trim()
      if (key === "idNumber") return text.length >= 8 ? `${text.slice(0, 4)}**********${text.slice(-4)}` : "********"
      if (key === "phone") return text.length >= 7 ? `${text.slice(0, 3)}****${text.slice(-4)}` : "****"
      if (["salaryTotal", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary"].includes(key)) return `¥${text}`
      return FACT_CODE_LABELS[text.toUpperCase()] || text
    },
    createSignatureRequestId() {
      const random = Math.random().toString(36).slice(2, 12)
      return `onboard-sign-${Date.now()}-${random}`
    },
    initSignatureCanvas() {
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      const rect = canvas.getBoundingClientRect()
      const ratio = (typeof window !== "undefined" && window.devicePixelRatio) || 1
      canvas.width = Math.max(1, Math.round(rect.width * ratio))
      canvas.height = Math.max(1, Math.round(rect.height * ratio))
      const context = canvas.getContext("2d")
      context.setTransform(ratio, 0, 0, ratio, 0, 0)
      context.fillStyle = "#ffffff"
      context.fillRect(0, 0, rect.width, rect.height)
      context.strokeStyle = "#111827"
      context.lineWidth = 3
      context.lineCap = "round"
      context.lineJoin = "round"
    },
    resizeSignatureCanvas() {
      if (this.requiresSignatureCapture && !this.hasSignature) {
        this.resetSignatureMetrics()
        this.$nextTick(() => this.initSignatureCanvas())
      }
    },
    startDraw(event) {
      if (this.submitting) return
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      if (event && event.pointerType === "mouse" && event.button !== 0) return
      if (this.drawing) return
      if (this.signatureRequestId) this.signatureRequestId = ""
      this.drawing = true
      this.activePointerId = event && event.pointerId !== undefined ? event.pointerId : null
      this.currentStrokeDistance = 0
      this.currentStrokePoints = 0
      this.lastPoint = this.pointerPoint(event, canvas)
      if (event && event.pointerId !== undefined && typeof canvas.setPointerCapture === "function") {
        try { canvas.setPointerCapture(event.pointerId) } catch (ignored) {}
      }
      this.drawDot(this.lastPoint)
    },
    draw(event) {
      if (this.submitting || !this.drawing || !this.lastPoint) return
      if (this.activePointerId !== null && event && event.pointerId !== this.activePointerId) return
      const canvas = this.$refs.signatureCanvas
      if (!canvas) return
      const point = this.pointerPoint(event, canvas)
      const distance = Math.hypot(point.x - this.lastPoint.x, point.y - this.lastPoint.y)
      const context = canvas.getContext("2d")
      context.beginPath()
      context.moveTo(this.lastPoint.x, this.lastPoint.y)
      context.lineTo(point.x, point.y)
      context.stroke()
      this.lastPoint = point
      if (distance >= 0.5) {
        this.currentStrokeDistance += distance
        this.currentStrokePoints += 1
      }
    },
    finishDraw(event) {
      if (!this.drawing) return
      if (this.activePointerId !== null && event && event.pointerId !== undefined && event.pointerId !== this.activePointerId) return
      const canvas = this.$refs.signatureCanvas
      const pointerId = this.activePointerId
      if (this.currentStrokeDistance >= 6 && this.currentStrokePoints >= 2) {
        this.signatureInkDistance += this.currentStrokeDistance
        this.signatureInkPoints += this.currentStrokePoints
      }
      this.hasSignature = this.signatureInkDistance >= MIN_SIGNATURE_DISTANCE &&
        this.signatureInkPoints >= MIN_SIGNATURE_POINTS
      this.drawing = false
      this.lastPoint = null
      this.activePointerId = null
      this.currentStrokeDistance = 0
      this.currentStrokePoints = 0
      if (canvas && pointerId !== null && typeof canvas.releasePointerCapture === "function") {
        try {
          if (typeof canvas.hasPointerCapture !== "function" || canvas.hasPointerCapture(pointerId)) {
            canvas.releasePointerCapture(pointerId)
          }
        } catch (ignored) {}
      }
    },
    drawDot(point) {
      const canvas = this.$refs.signatureCanvas
      if (!canvas || !point) return
      const context = canvas.getContext("2d")
      context.beginPath()
      context.arc(point.x, point.y, 1.5, 0, Math.PI * 2)
      context.fillStyle = "#111827"
      context.fill()
    },
    pointerPoint(event, canvas) {
      const source = event.touches && event.touches.length ? event.touches[0] : event
      const rect = canvas.getBoundingClientRect()
      return { x: source.clientX - rect.left, y: source.clientY - rect.top }
    },
    clearSignature() {
      if (this.submitting) return
      this.hasSignature = false
      this.signatureRequestId = ""
      this.resetSignatureMetrics()
      this.initSignatureCanvas()
    },
    resetSignatureMetrics() {
      this.drawing = false
      this.lastPoint = null
      this.activePointerId = null
      this.currentStrokeDistance = 0
      this.currentStrokePoints = 0
      this.signatureInkDistance = 0
      this.signatureInkPoints = 0
    },
    statusLabel(item) {
      const status = String(item && (item.status || item.reviewStatus) || "").toUpperCase()
      const sequence = String(item && item.signingSequence || "").toUpperCase()
      if (status === "COMPLETED" && sequence === "SIGNATURE_FIRST") return "等待公司与印章"
      if (status === "REJECTED" && item && item.signatureCaptured) return "待更正（签名已保留）"
      return {
        PENDING_EMPLOYEE: sequence === "SIGNATURE_FIRST" ? "待确认并签名" : "待填写",
        SUBMITTED: "待 HR 审核",
        APPROVED: "已通过",
        REJECTED: "已驳回",
        PROFILE_SYNC_FAILED: "档案同步中",
        COMPLETED: "已完成",
        CANCELLED: "已取消"
      }[status] || "待处理"
    },
    statusClass(item) {
      const status = String(item && (item.status || item.reviewStatus) || "").toUpperCase()
      if (["APPROVED", "COMPLETED"].includes(status)) return "status-chip is-success"
      if (status === "REJECTED") return "status-chip is-danger"
      if (status === "SUBMITTED") return "status-chip is-warning"
      return "status-chip"
    },
    goBack() {
      if (this.embedded) {
        this.$emit("back")
        return
      }
      if (this.selectedRequest) {
        this.$router.push({ path: "/mobile/onboard-data" }).catch(() => {})
        return
      }
      if (window.history.length > 1) this.$router.back()
      else this.$router.push({ path: "/mobile/mine" }).catch(() => {})
    },
    refresh() {
      if (this.routeRequestId) return this.loadDetail(this.routeRequestId)
      if (this.selectedRequest && this.selectedRequest.requestId) {
        return this.loadDetail(this.selectedRequest.requestId)
      }
      return this.loadList()
    },
    errorText(error, fallback) {
      return mobileErrorMessage(error, fallback)
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-onboard-data-page { min-height: 100vh; background: #f3f6fa; color: #1f2937; }
.mobile-onboard-data-page.is-embedded { min-height: 0; background: transparent; }
.mobile-onboard-data-header {
  position: sticky; top: 0; z-index: 5; display: grid; grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center; gap: 8px; padding: calc(12px + env(safe-area-inset-top)) 14px 12px;
  background: rgba(255, 255, 255, 0.96); border-bottom: 1px solid #e5eaf1;
}
.mobile-onboard-data-header h1 { margin: 0; font-size: 19px; }
.mobile-onboard-data-header p { margin: 3px 0 0; color: #718096; font-size: 12px; }
.back-button, .refresh-button { border: 0; background: transparent; color: #4055cf; }
.back-button { font-size: 32px; line-height: 1; }
.refresh-button { font-size: 13px; }
.mobile-onboard-data-main { display: flex; flex-direction: column; gap: 12px; padding: 14px 14px calc(28px + env(safe-area-inset-bottom)); }
.state-card, .request-card, .detail-card {
  border: 1px solid #e4e9f1; border-radius: 15px; background: #fff; box-shadow: 0 8px 22px rgba(39, 52, 82, 0.06);
}
.state-card { display: flex; flex-direction: column; gap: 7px; padding: 24px 18px; color: #667085; text-align: center; }
.state-card strong { color: #27364d; }
.state-card button { align-self: center; border: 0; border-radius: 10px; padding: 9px 18px; background: #4055cf; color: #fff; }
.state-card.is-error { color: #b4232f; background: #fff7f7; border-color: #f3c8cc; }
.request-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; width: 100%; padding: 16px; text-align: left; }
.request-card > div { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.request-card strong { color: #253247; font-size: 15px; }
.request-card span { color: #68758a; font-size: 13px; line-height: 1.5; }
.request-card small { color: #b4232f; line-height: 1.45; }
.status-chip { flex: 0 0 auto; border-radius: 999px; padding: 5px 9px; background: #edf1ff; color: #4055cf; font-size: 11px; font-style: normal; }
.status-chip.is-warning { background: #fff4dc; color: #a05b00; }
.status-chip.is-success { background: #e6f7ef; color: #167a55; }
.status-chip.is-danger { background: #fff0f1; color: #b4232f; }
.detail-card { padding: 16px; }
.intro-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.intro-card .eyebrow { color: #4055cf; font-size: 11px; letter-spacing: 0.08em; }
.intro-card h2 { margin: 5px 0; font-size: 19px; }
.intro-card p, .boundary-card p, .rejection-card p { margin: 0; color: #667085; font-size: 13px; line-height: 1.65; }
.rejection-card { color: #9c2f39; background: #fff8f8; border-color: #f3c8cc; }
.rejection-card p { margin-top: 6px; color: #9c2f39; }
.form-card { display: flex; flex-direction: column; gap: 16px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.section-heading strong { color: #27364d; }
.section-heading p { margin: 5px 0 0; color: #667085; font-size: 12px; line-height: 1.55; }
.section-heading em { flex: 0 0 auto; border-radius: 999px; padding: 4px 8px; background: #eef2ff; color: #4055cf; font-size: 11px; font-style: normal; }
.fact-list { display: grid; gap: 0; margin: 14px 0 0; border: 1px solid #e5eaf1; border-radius: 11px; overflow: hidden; }
.fact-list > div { display: grid; grid-template-columns: 108px minmax(0, 1fr); gap: 10px; padding: 10px 12px; border-bottom: 1px solid #edf0f5; }
.fact-list > div:last-child { border-bottom: 0; }
.fact-list dt { color: #667085; font-size: 12px; }
.fact-list dd { margin: 0; color: #253247; font-size: 13px; overflow-wrap: anywhere; }
.document-plan { margin-top: 14px; border-radius: 11px; padding: 12px; background: #f7f8fc; }
.document-plan strong { color: #344054; font-size: 13px; }
.document-plan ul { display: grid; gap: 7px; margin: 9px 0 0; padding-left: 20px; color: #475467; font-size: 13px; }
.document-plan p { margin: 8px 0 0; color: #b4232f; font-size: 12px; line-height: 1.5; }
.signature-card { display: flex; flex-direction: column; gap: 12px; }
.signature-box { height: 180px; overflow: hidden; border: 1px dashed #98a2b3; border-radius: 12px; background: #fff; touch-action: none; }
.signature-box canvas { display: block; width: 100%; height: 100%; touch-action: none; }
.clear-signature-button { align-self: flex-end; min-height: 44px; border: 1px solid #cbd5e1; border-radius: 10px; padding: 8px 14px; background: #fff; color: #4055cf; }
.confirmation-field { margin-top: 2px; }
.confirmation-copy { border-radius: 10px; padding: 10px 12px; background: #f3f5ff; color: #27364d; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.signature-complete-card { background: #f2fbf7; border-color: #c8ebda; }
.signature-complete-card p { margin: 7px 0 0; color: #667085; font-size: 13px; line-height: 1.6; }
.mobile-field { display: flex; flex-direction: column; gap: 7px; }
.mobile-field > span { color: #344054; font-size: 13px; font-weight: 600; }
.mobile-field i { margin-left: 3px; color: #d33c4a; font-style: normal; }
.mobile-field input, .mobile-field select, .mobile-field textarea {
  width: 100%; box-sizing: border-box; border: 1px solid #ccd5e2; border-radius: 11px; padding: 11px 12px;
  background: #fff; color: #1f2937; font: inherit; outline: none;
}
.mobile-field input:focus, .mobile-field select:focus, .mobile-field textarea:focus { border-color: #6678e6; box-shadow: 0 0 0 3px rgba(64, 85, 207, 0.12); }
.mobile-field small { color: #7a8799; line-height: 1.45; }
.boundary-card { background: #f8fafc; }
.boundary-card p { margin-top: 7px; }
.validation-message { margin: 0; border-radius: 10px; padding: 10px 12px; background: #fff0f1; color: #b4232f; font-size: 13px; }
.submit-button { border: 0; border-radius: 13px; padding: 14px 16px; background: #4055cf; color: #fff; font-size: 15px; font-weight: 700; box-shadow: 0 10px 24px rgba(64, 85, 207, 0.22); }
.submit-button:disabled { opacity: 0.55; box-shadow: none; }
.submitted-card { background: #f2fbf7; border-color: #c8ebda; }
.field-empty { color: #8a5a13; line-height: 1.6; }
.mobile-onboard-data-page { color: var(--mobile-color-ink); background: var(--mobile-color-page); }
.mobile-onboard-data-header { grid-template-columns: 44px minmax(0, 1fr) auto; border-bottom-color: var(--mobile-color-line); background: var(--mobile-color-surface); }
.back-button, .refresh-button, .intro-card .eyebrow, .clear-signature-button { color: var(--mobile-color-primary); }
.back-button, .refresh-button { min-width: 44px; min-height: 44px; }
.state-card, .request-card, .detail-card { border-color: var(--mobile-color-line); border-radius: var(--mobile-radius-lg); background: var(--mobile-color-surface); box-shadow: none; }
.status-chip:not(.is-warning):not(.is-success):not(.is-danger), .section-heading em { color: var(--mobile-color-primary); background: var(--mobile-color-primary-soft); }
.confirmation-copy { color: var(--mobile-color-ink); background: var(--mobile-color-primary-soft); }
.mobile-field input:focus, .mobile-field select:focus, .mobile-field textarea:focus { border-color: var(--mobile-color-primary); box-shadow: var(--mobile-focus-ring); }
.state-card button, .submit-button { min-height: var(--mobile-primary-control-height); color: #fff; background: var(--mobile-color-primary); box-shadow: none; }
.mobile-field input, .mobile-field select, .mobile-field textarea { min-height: 44px; font-size: 16px; }
.submit-button { font-size: 16px; }
.mobile-onboard-data-page button:focus-visible, .mobile-onboard-data-page input:focus-visible, .mobile-onboard-data-page select:focus-visible, .mobile-onboard-data-page textarea:focus-visible { outline: 2px solid var(--mobile-color-primary); outline-offset: 2px; }
</style>
