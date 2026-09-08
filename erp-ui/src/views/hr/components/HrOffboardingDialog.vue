<template>
  <div>
    <el-dialog
      title="确认离职"
      width="760px"
      :visible="visible"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      custom-class="hr-offboarding-dialog"
      append-to-body
      @close="close"
    >
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-alert
        v-if="isFuture"
        title="未来最后工作日暂不能确认离职，请在最后工作日当天操作"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="isHighRisk"
        title="当前离职信息包含高风险条件，提交前必须由本人完成结构化二次确认。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="确认后员工状态将立即变为离职，账号立即停用，合同材料进入人事确认。"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="offboard-summary">
        <strong>{{ employeeName }}</strong>
        <span>员工账号：{{ employee && employee.userId || '-' }}</span>
        <span>服务端业务日：{{ businessDate || '正在读取…' }}</span>
      </div>

      <el-form :model="model" label-width="116px" class="offboard-form">
        <el-row :gutter="14">
          <el-col :xs="24" :sm="12">
            <el-form-item label="最后工作日" prop="lastWorkingDate" required>
              <el-date-picker
                v-model="model.lastWorkingDate"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 100%"
              />
              <small v-if="dateWarning" class="date-warning">{{ dateWarning }}</small>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="离职类型" prop="offboardingType" required>
              <el-select v-model="model.offboardingType" placeholder="请明确选择离职类型" style="width: 100%">
                <el-option label="预期主动离职" value="VOLUNTARY_EXPECTED" />
                <el-option label="非预期主动离职" value="VOLUNTARY_UNEXPECTED" />
                <el-option label="辞退" value="TERMINATION" />
                <el-option label="违纪解除" value="DISCIPLINARY_TERMINATION" />
                <el-option label="劳动争议解除" value="DISPUTED_TERMINATION" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="离职原因" prop="reason" required>
              <el-input v-model.trim="model.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="工资结算" prop="salarySettlementStatus" required>
              <el-select v-model="model.salarySettlementStatus" placeholder="请确认实际结算状态" style="width: 100%">
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="未完成" value="PENDING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="资产交接" prop="assetHandoverStatus" required>
              <el-select v-model="model.assetHandoverStatus" placeholder="请确认实际交接状态" style="width: 100%">
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="未完成" value="PENDING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="竞业决定" prop="nonCompeteDecision" required>
              <el-select v-model="model.nonCompeteDecision" placeholder="请明确选择竞业决定" style="width: 100%">
                <el-option label="不适用" value="NOT_APPLICABLE" />
                <el-option label="确定执行" value="REQUIRED" />
                <el-option label="待定" value="PENDING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="补偿金额" prop="compensationAmount">
              <el-input-number v-model="model.compensationAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="补偿说明" prop="compensationNote">
              <el-input v-model.trim="model.compensationNote" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <p class="automation-note">离职确认、交接、结算、保密竞业及解除终止材料只生成草稿，始终停在唯一人事确认，不会自动发送。</p>
      <span slot="footer">
        <el-button :disabled="submitting" @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
          {{ isHighRisk ? '进入高风险二次确认' : '确认离职并停用账号' }}
        </el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="高风险离职二次确认"
      width="660px"
      :visible.sync="riskDialogOpen"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-tag type="danger" effect="dark">高风险离职</el-tag>
      <el-descriptions :column="1" border size="small" class="risk-details">
        <el-descriptions-item label="员工">{{ employeeName }}</el-descriptions-item>
        <el-descriptions-item label="离职类型">{{ offboardingTypeText }}</el-descriptions-item>
        <el-descriptions-item label="最后工作日 / 操作日">{{ model.lastWorkingDate }} / {{ businessDate }}</el-descriptions-item>
        <el-descriptions-item label="工资结算">{{ salarySettlementStatusText }}</el-descriptions-item>
        <el-descriptions-item label="资产交接">{{ assetHandoverStatusText }}</el-descriptions-item>
        <el-descriptions-item label="竞业决定">{{ nonCompeteDecisionText }}</el-descriptions-item>
        <el-descriptions-item label="补偿金额 / 说明">{{ compensationText }} / {{ model.compensationNote || '无' }}</el-descriptions-item>
        <el-descriptions-item label="风险提示">{{ riskCodeText }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="92px" class="risk-form">
        <el-form-item label="确认原因" required>
          <el-input v-model.trim="riskReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-checkbox v-model="riskAcknowledged">我已核对全部离职信息，并确认立即执行离职和账号停用</el-checkbox>
      </el-form>
      <span slot="footer">
        <el-button @click="riskDialogOpen = false">返回修改</el-button>
        <el-button
          type="danger"
          :loading="submitting"
          :disabled="!riskAcknowledged || !validRiskReason"
          @click="confirmRisk"
        >确认高风险离职并立即执行</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { confirmHrEmployeeOffboarding, getHrOffboardingBusinessDate } from "@/api/hr/employee"

const OFFBOARDING_TYPE_LABELS = Object.freeze({
  VOLUNTARY_EXPECTED: "预期主动离职",
  VOLUNTARY_UNEXPECTED: "非预期主动离职",
  TERMINATION: "辞退",
  DISCIPLINARY_TERMINATION: "违纪解除",
  DISPUTED_TERMINATION: "劳动争议解除"
})

const COMPLETION_STATUS_LABELS = Object.freeze({
  COMPLETED: "已完成",
  PENDING: "未完成"
})

const NON_COMPETE_DECISION_LABELS = Object.freeze({
  NOT_APPLICABLE: "不适用",
  REQUIRED: "确定执行",
  PENDING: "待定"
})

const RISK_CODE_LABELS = Object.freeze({
  HISTORICAL_OFFBOARDING: "历史离职补录",
  NON_STANDARD_OFFBOARDING_TYPE: "非标准离职类型",
  SALARY_SETTLEMENT_PENDING: "工资结算未完成",
  ASSET_HANDOVER_PENDING: "资产交接未完成",
  NON_COMPETE_REVIEW_REQUIRED: "竞业决定需复核",
  COMPENSATION_REVIEW_REQUIRED: "补偿信息需复核"
})

const displayLabel = (labels, value, emptyText = "未填写") => {
  if (!value) return emptyText
  return labels[value] || "未知状态"
}

const emptyModel = () => ({
  lastWorkingDate: "",
  offboardingType: "",
  reason: "",
  salarySettlementStatus: "",
  assetHandoverStatus: "",
  nonCompeteDecision: "",
  compensationAmount: 0,
  compensationNote: ""
})

export default {
  name: "HrOffboardingDialog",
  props: {
    visible: { type: Boolean, default: false },
    employee: { type: Object, default: null }
  },
  data() {
    return {
      model: emptyModel(),
      businessDate: "",
      businessDateLoading: false,
      requestId: "",
      submitting: false,
      errorMessage: "",
      riskDialogOpen: false,
      riskAcknowledged: false,
      riskReason: "",
      generation: 0
    }
  },
  computed: {
    employeeName() {
      return this.employee && (this.employee.employeeName || this.employee.nickName || this.employee.userName) || "未命名员工"
    },
    dateStatus() {
      return this.dateRelation(this.model.lastWorkingDate, this.businessDate)
    },
    isFuture() {
      return this.dateStatus === "FUTURE"
    },
    isHistorical() {
      return this.dateStatus === "HISTORICAL"
    },
    dateWarning() {
      if (this.isFuture) return "未来最后工作日暂不能确认离职，请在最后工作日当天操作"
      if (this.isHistorical) return `历史离职补录：最后工作日 ${this.model.lastWorkingDate}，实际操作日 ${this.businessDate}`
      return ""
    },
    isHighRisk() {
      return this.isHistorical ||
        Boolean(this.model.offboardingType && this.model.offboardingType !== "VOLUNTARY_EXPECTED") ||
        Boolean(this.model.salarySettlementStatus && this.model.salarySettlementStatus !== "COMPLETED") ||
        Boolean(this.model.assetHandoverStatus && this.model.assetHandoverStatus !== "COMPLETED") ||
        Boolean(this.model.nonCompeteDecision && this.model.nonCompeteDecision !== "NOT_APPLICABLE") ||
        Number(this.model.compensationAmount || 0) > 0 ||
        Boolean(String(this.model.compensationNote || "").trim())
    },
    riskCodes() {
      const values = []
      if (this.isHistorical) values.push("HISTORICAL_OFFBOARDING")
      if (this.model.offboardingType && this.model.offboardingType !== "VOLUNTARY_EXPECTED") values.push("NON_STANDARD_OFFBOARDING_TYPE")
      if (this.model.salarySettlementStatus && this.model.salarySettlementStatus !== "COMPLETED") values.push("SALARY_SETTLEMENT_PENDING")
      if (this.model.assetHandoverStatus && this.model.assetHandoverStatus !== "COMPLETED") values.push("ASSET_HANDOVER_PENDING")
      if (this.model.nonCompeteDecision && this.model.nonCompeteDecision !== "NOT_APPLICABLE") values.push("NON_COMPETE_REVIEW_REQUIRED")
      if (Number(this.model.compensationAmount || 0) > 0 || String(this.model.compensationNote || "").trim()) {
        values.push("COMPENSATION_REVIEW_REQUIRED")
      }
      return values
    },
    offboardingTypeText() {
      return displayLabel(OFFBOARDING_TYPE_LABELS, this.model.offboardingType)
    },
    salarySettlementStatusText() {
      return displayLabel(COMPLETION_STATUS_LABELS, this.model.salarySettlementStatus)
    },
    assetHandoverStatusText() {
      return displayLabel(COMPLETION_STATUS_LABELS, this.model.assetHandoverStatus)
    },
    nonCompeteDecisionText() {
      return displayLabel(NON_COMPETE_DECISION_LABELS, this.model.nonCompeteDecision)
    },
    riskCodeText() {
      if (!this.riskCodes.length) return "无"
      return this.riskCodes.map(code => RISK_CODE_LABELS[code] || "未知风险").join("、")
    },
    compensationText() {
      return Number(this.model.compensationAmount || 0).toFixed(2)
    },
    hasRequiredData() {
      const reason = String(this.model.reason || "").trim()
      const amount = Number(this.model.compensationAmount)
      return Boolean(this.businessDate && this.model.lastWorkingDate &&
        this.model.offboardingType && reason.length >= 1 && reason.length <= 500 &&
        this.model.salarySettlementStatus && this.model.assetHandoverStatus &&
        this.model.nonCompeteDecision && Number.isFinite(amount) && amount >= 0)
    },
    validRiskReason() {
      const reason = String(this.riskReason || "").trim()
      return reason.length >= 1 && reason.length <= 500
    },
    canSubmit() {
      return !this.businessDateLoading && !this.submitting && !this.isFuture && this.hasRequiredData
    }
  },
  watch: {
    visible(value) {
      if (value) this.openDialog()
      else this.invalidate()
    },
    employee(value, previous) {
      if (this.visible && value && (!previous || value.userId !== previous.userId)) this.openDialog()
    }
  },
  beforeDestroy() {
    this.invalidate()
  },
  methods: {
    dateRelation(date, businessDate) {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date || "") || !/^\d{4}-\d{2}-\d{2}$/.test(businessDate || "")) return "UNKNOWN"
      if (date > businessDate) return "FUTURE"
      if (date < businessDate) return "HISTORICAL"
      return "TODAY"
    },
    requestKey() {
      return `offboard-${this.employee && this.employee.userId || "employee"}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    },
    openDialog() {
      const generation = ++this.generation
      this.model = emptyModel()
      this.businessDate = ""
      this.businessDateLoading = true
      this.requestId = this.requestKey()
      this.errorMessage = ""
      this.riskDialogOpen = false
      this.riskAcknowledged = false
      this.riskReason = ""
      return getHrOffboardingBusinessDate().then(response => {
        if (generation !== this.generation || !this.visible) return null
        const date = response && response.data && response.data.businessDate
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date || "")) throw new Error("服务端业务日期无效")
        this.businessDate = date
        this.model.lastWorkingDate = date
        return date
      }).catch(error => {
        if (generation !== this.generation || !this.visible) return null
        this.errorMessage = error && error.message || "读取服务端业务日期失败，请稍后重试。"
        return null
      }).finally(() => {
        if (generation === this.generation) this.businessDateLoading = false
      })
    },
    invalidate() {
      this.generation += 1
      this.riskDialogOpen = false
    },
    riskConfirmation() {
      return {
        confirmed: true,
        employeeId: this.employee.userId,
        employeeName: this.employeeName,
        offboardingType: this.model.offboardingType,
        lastWorkingDate: this.model.lastWorkingDate,
        operationDate: this.businessDate,
        salarySettlementStatus: this.model.salarySettlementStatus,
        assetHandoverStatus: this.model.assetHandoverStatus,
        nonCompeteDecision: this.model.nonCompeteDecision,
        compensationAmount: Number(this.model.compensationAmount || 0),
        riskStatement: "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用",
        reason: String(this.riskReason || "").trim()
      }
    },
    buildPayload(riskConfirmation) {
      return {
        requestId: this.requestId,
        lastWorkingDate: this.model.lastWorkingDate,
        offboardingType: this.model.offboardingType,
        reason: String(this.model.reason || "").trim(),
        salarySettlementStatus: this.model.salarySettlementStatus,
        assetHandoverStatus: this.model.assetHandoverStatus,
        nonCompeteDecision: this.model.nonCompeteDecision,
        compensationAmount: Number(this.model.compensationAmount || 0),
        compensationNote: String(this.model.compensationNote || "").trim() || null,
        riskConfirmation: riskConfirmation || null
      }
    },
    submit() {
      if (!this.canSubmit) return Promise.resolve(null)
      if (this.isHighRisk) {
        this.riskDialogOpen = true
        return Promise.resolve(null)
      }
      return this.submitPayload(null)
    },
    confirmRisk() {
      if (!this.riskAcknowledged || !this.validRiskReason || !this.isHighRisk) return Promise.resolve(null)
      return this.submitPayload(this.riskConfirmation())
    },
    submitPayload(riskConfirmation) {
      if (this.submitting) return Promise.resolve(null)
      const generation = this.generation
      const requestId = this.requestId
      const employeeId = this.employee && this.employee.userId
      this.submitting = true
      this.errorMessage = ""
      return confirmHrEmployeeOffboarding(employeeId, this.buildPayload(riskConfirmation)).then(response => {
        if (generation !== this.generation || requestId !== this.requestId || !this.visible) return null
        this.riskDialogOpen = false
        const result = response && response.data ? response.data : response
        this.$emit("confirmed", result || {})
        this.$emit("update:visible", false)
        return result
      }).catch(error => {
        if (generation !== this.generation || requestId !== this.requestId || !this.visible) return null
        this.errorMessage = error && error.message || "离职确认失败，请核对信息后重试。"
        return null
      }).finally(() => {
        if (generation === this.generation && requestId === this.requestId) this.submitting = false
      })
    },
    close() {
      if (this.submitting) return false
      this.invalidate()
      this.$emit("update:visible", false)
      return true
    }
  }
}
</script>

<style lang="scss" scoped>
.el-alert { margin-bottom: 14px; }
.offboard-summary { display: flex; flex-wrap: wrap; gap: 8px 24px; margin-bottom: 16px; padding: 13px; border: 1px solid #dbe4ef; border-radius: 7px; background: #f8fafc; }
.offboard-summary strong { width: 100%; color: #1f2937; }
.offboard-summary span { color: #475569; }
.date-warning { display: block; margin-top: 5px; color: #d93025; line-height: 1.4; }
.automation-note { margin: 0; padding: 11px 13px; border-radius: 6px; background: #f0f7ff; color: #476582; line-height: 1.6; }
.risk-details { margin: 14px 0; }
.risk-form { margin-top: 14px; }
::v-deep .hr-offboarding-dialog {
  max-height: 90vh;
  margin-top: 5vh !important;
  display: flex;
  flex-direction: column;
}
::v-deep .hr-offboarding-dialog .el-dialog__body { overflow-y: auto; padding-bottom: 14px; }
::v-deep .hr-offboarding-dialog .el-dialog__footer { flex-shrink: 0; padding-top: 14px; border-top: 1px solid #e5e7eb; background: #fff; }
</style>
