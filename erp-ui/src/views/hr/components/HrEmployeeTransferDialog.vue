<template>
  <div>
    <el-dialog
      title="确认调岗"
      width="760px"
      :visible="visible"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      custom-class="hr-transfer-dialog"
      append-to-body
      @close="close"
    >
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-alert
        v-if="isFuture"
        title="未来日期的调岗暂不能确认，请在生效当天操作"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="isHistorical"
        title="高风险补录"
        type="warning"
        :closable="false"
        show-icon
      >
        <span>调岗生效日期 {{ model.effectiveDate }}，实际操作日期 {{ businessDate }}。确认后将立即修改当前员工档案。</span>
      </el-alert>
      <el-alert
        v-else-if="isToday"
        title="调岗将在今天确认后立即生效，并进入合同自动化任务。"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="transfer-summary">
        <strong>{{ employeeName }}</strong>
        <span>{{ currentDeptName }} / {{ currentPostName }}</span>
        <i class="el-icon-right" />
        <span>{{ selectedDeptName || "请选择新组织" }} / {{ selectedPostName || "请选择新岗位" }}</span>
      </div>

      <el-form :model="model" label-width="116px" class="transfer-form">
        <el-row :gutter="14">
          <el-col :xs="24" :sm="12">
            <el-form-item label="调岗生效日期" prop="effectiveDate" required>
              <el-date-picker
                v-model="model.effectiveDate"
                type="date"
                value-format="yyyy-MM-dd"
                style="width: 100%"
              />
              <small v-if="dateWarning" class="date-warning">{{ dateWarning }}</small>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="服务端业务日">
              <el-input :value="businessDate || '正在读取…'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24"><h3 class="transfer-section-title">岗位与工作信息</h3></el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="目标组织" prop="targetDeptId" required>
              <el-select v-model="model.targetDeptId" filterable style="width: 100%">
                <el-option v-for="item in departmentOptions" :key="item.deptId" :label="item.deptName" :value="item.deptId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="目标岗位" prop="postId" required>
              <el-select v-model="model.postId" filterable style="width: 100%">
                <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="目标职级" prop="jobGradeCode" required>
              <el-input v-model.trim="model.jobGradeCode" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="直属主管" prop="directSupervisorId">
              <el-select v-model="model.directSupervisorId" clearable filterable style="width: 100%">
                <el-option v-for="item in supervisorOptions" :key="item.userId" :label="item.employeeName" :value="item.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="工作地点" prop="workLocation" required>
              <el-input v-model.trim="model.workLocation" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="城市级别" prop="workCityLevel" required>
              <el-input v-model.trim="model.workCityLevel" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><h3 class="transfer-section-title">合同主体</h3></el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="法人主体编号" prop="legalEntityId" required>
              <el-input-number v-model="model.legalEntityId" :min="1" :precision="0" :controls="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="法人主体编码" prop="legalEntityCode" required>
              <el-input v-model.trim="model.legalEntityCode" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="法人主体名称" prop="legalEntityName" required>
              <el-input v-model.trim="model.legalEntityName" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col v-if="canAdjustSalary" :span="24">
            <el-form-item label="">
              <el-checkbox v-model="model.adjustSalary">同时调整工资</el-checkbox>
            </el-form-item>
          </el-col>
          <template v-if="canAdjustSalary && model.adjustSalary">
          <el-col :xs="24" :sm="8">
            <el-form-item label="基本工资" prop="baseSalary" required>
              <el-input-number v-model="model.baseSalary" :min="0" :precision="2" :controls="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="岗位工资" prop="postSalary" required>
              <el-input-number v-model="model.postSalary" :min="0" :precision="2" :controls="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="外勤补贴" prop="fieldAllowance" required>
              <el-input-number v-model="model.fieldAllowance" :min="0" :precision="2" :controls="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="绩效工资" prop="performanceSalary" required>
              <el-input-number v-model="model.performanceSalary" :min="0" :precision="2" :controls="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="薪资合计" prop="salaryTotal" required>
              <el-input-number v-model="model.salaryTotal" :min="0" :precision="2" :controls="false" disabled />
              <small class="auto-total-note">由四项薪资自动合计</small>
            </el-form-item>
          </el-col>
          </template>
        </el-row>
      </el-form>

      <p class="automation-note">确认成功后，系统会按调岗前后冻结快照判断是否生成合同或补充协议草稿；所有草稿仍由唯一人事确认后发送。</p>
      <span slot="footer">
        <el-button :disabled="submitting" @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
          {{ isHistorical ? "进入高风险二次确认" : "确认调岗" }}
        </el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="历史调岗补录二次确认"
      width="620px"
      :visible.sync="riskDialogOpen"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-tag type="danger" effect="dark">高风险补录</el-tag>
      <el-descriptions :column="1" border size="small" class="risk-details">
        <el-descriptions-item label="员工">{{ employeeName }}</el-descriptions-item>
        <el-descriptions-item label="原组织 / 岗位">{{ currentDeptName }} / {{ currentPostName }}</el-descriptions-item>
        <el-descriptions-item label="新组织 / 岗位">{{ selectedDeptName }} / {{ selectedPostName }}</el-descriptions-item>
        <el-descriptions-item label="调岗生效日期">{{ model.effectiveDate }}</el-descriptions-item>
        <el-descriptions-item label="实际操作日期">{{ businessDate }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        title="该操作将按历史日期补录并立即修改当前员工档案"
        type="error"
        :closable="false"
        show-icon
      />
      <el-form label-width="92px" class="risk-form">
        <el-form-item label="补录原因" required>
          <el-input v-model.trim="historicalReason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-checkbox v-model="riskAcknowledged">我已核对员工、原组织岗位、新组织岗位和日期，并确认承担历史补录风险</el-checkbox>
      </el-form>
      <span slot="footer">
        <el-button @click="riskDialogOpen = false">返回修改</el-button>
        <el-button
          type="danger"
          :loading="submitting"
          :disabled="!riskAcknowledged || !historicalReason"
          @click="confirmHistorical"
        >确认历史补录并立即生效</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { confirmHrEmployeeTransfer, getHrTransferBusinessDate } from "@/api/hr/employee"

const emptyModel = () => ({
  effectiveDate: "",
  targetDeptId: null,
  postId: null,
  jobGradeCode: "",
  directSupervisorId: null,
  workLocation: "",
  workCityLevel: "",
  legalEntityId: null,
  legalEntityCode: "",
  legalEntityName: "",
  baseSalary: null,
  postSalary: null,
  fieldAllowance: null,
  performanceSalary: null,
  salaryTotal: null,
  adjustSalary: false
})

export default {
  name: "HrEmployeeTransferDialog",
  props: {
    visible: { type: Boolean, default: false },
    employee: { type: Object, default: null },
    options: { type: Object, default: () => ({}) }
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
      historicalReason: "",
      generation: 0
    }
  },
  computed: {
    departmentOptions() {
      return Array.isArray(this.options.departments) ? this.options.departments : []
    },
    postOptions() {
      return Array.isArray(this.options.posts) ? this.options.posts : []
    },
    supervisorOptions() {
      return Array.isArray(this.options.supervisors) ? this.options.supervisors : []
    },
    employeeName() {
      return this.employee && (this.employee.employeeName || this.employee.nickName || this.employee.userName) || "未命名员工"
    },
    currentDeptId() {
      return this.employee && (this.employee.deptId || (this.employee.dept && this.employee.dept.deptId)) || null
    },
    currentDeptName() {
      return this.employee && (this.employee.departmentName || (this.employee.dept && this.employee.dept.deptName)) || "未填写"
    },
    currentPostId() {
      const ids = this.employee && this.employee.postIds
      return Array.isArray(ids) && ids.length ? ids[0] : null
    },
    currentPostName() {
      const canonical = this.postOptions.find(item => Number(item.postId) === Number(this.currentPostId))
      return canonical && canonical.postName || this.employee && (this.employee.postNames || this.field("positionNames")) || "未填写"
    },
    selectedDept() {
      return this.departmentOptions.find(item => Number(item.deptId) === Number(this.model.targetDeptId)) || null
    },
    selectedDeptName() {
      return this.selectedDept && this.selectedDept.deptName
    },
    selectedPost() {
      return this.postOptions.find(item => Number(item.postId) === Number(this.model.postId)) || null
    },
    selectedPostName() {
      return this.selectedPost && this.selectedPost.postName
    },
    selectedSupervisor() {
      return this.supervisorOptions.find(item => Number(item.userId) === Number(this.model.directSupervisorId)) || null
    },
    dateStatus() {
      return this.dateRelation(this.model.effectiveDate, this.businessDate)
    },
    isFuture() {
      return this.dateStatus === "FUTURE"
    },
    isToday() {
      return this.dateStatus === "TODAY"
    },
    isHistorical() {
      return this.dateStatus === "HISTORICAL"
    },
    dateWarning() {
      if (this.isFuture) return "未来日期的调岗暂不能确认，请在生效当天操作"
      if (this.isHistorical) return `高风险补录：业务生效日 ${this.model.effectiveDate}，实际操作日 ${this.businessDate}`
      return ""
    },
    canAdjustSalary() {
      const getters = this.$store && this.$store.getters || {}
      const permissions = Array.isArray(getters.permissions) ? getters.permissions : []
      return permissions.includes("*:*:*") || permissions.includes("hr:employee:salary:edit")
    },
    salaryValid() {
      const values = [this.model.baseSalary, this.model.postSalary, this.model.fieldAllowance, this.model.performanceSalary, this.model.salaryTotal]
      if (values.some(value => value === null || value === undefined || value === "" || !Number.isFinite(Number(value)) || Number(value) < 0)) return false
      const components = values.slice(0, 4).reduce((total, value) => total + Number(value), 0)
      return Number(this.model.salaryTotal) > 0 && Math.abs(components - Number(this.model.salaryTotal)) < 0.005
    },
    hasRequiredData() {
      return Boolean(this.businessDate && this.model.effectiveDate && this.selectedDept && this.selectedPost &&
        this.model.jobGradeCode && this.model.workLocation && this.model.workCityLevel &&
        this.model.legalEntityId && this.model.legalEntityCode && this.model.legalEntityName &&
        (!this.model.adjustSalary || (this.canAdjustSalary && this.salaryValid)))
    },
    hasBusinessChanges() {
      return Number(this.currentDeptId) !== Number(this.model.targetDeptId) ||
        Number(this.currentPostId) !== Number(this.model.postId) ||
        String(this.field("jobGrade") || "") !== String(this.model.jobGradeCode || "") ||
        Number(this.field("directSupervisorUserId") || 0) !== Number(this.model.directSupervisorId || 0) ||
        String(this.field("workLocation") || "") !== String(this.model.workLocation || "") ||
        String(this.field("workCityLevel") || "") !== String(this.model.workCityLevel || "") ||
        Number(this.field("legalEntityId") || 0) !== Number(this.model.legalEntityId || 0) ||
        String(this.field("legalEntityCode") || "") !== String(this.model.legalEntityCode || "") ||
        String(this.field("legalEntity") || this.field("legalEntityName") || "") !== String(this.model.legalEntityName || "") ||
        (this.model.adjustSalary && this.canAdjustSalary && ["baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal"]
          .some(key => this.numberField(key) !== this.model[key]))
    },
    canSubmit() {
      return !this.businessDateLoading && !this.submitting && !this.isFuture &&
        this.hasRequiredData && this.hasBusinessChanges
    }
  },
  watch: {
    visible(value) {
      if (value) this.openDialog()
      else this.invalidate()
    },
    employee(value, previous) {
      if (this.visible && value && (!previous || value.userId !== previous.userId)) this.openDialog()
    },
    canAdjustSalary(value) {
      if (!value) this.model.adjustSalary = false
    },
    "model.baseSalary": "syncSalaryTotal",
    "model.postSalary": "syncSalaryTotal",
    "model.fieldAllowance": "syncSalaryTotal",
    "model.performanceSalary": "syncSalaryTotal"
  },
  beforeDestroy() {
    this.invalidate()
  },
  methods: {
    syncSalaryTotal() {
      const values = [this.model.baseSalary, this.model.postSalary, this.model.fieldAllowance, this.model.performanceSalary]
      this.model.salaryTotal = values.some(value => value === null || value === undefined || value === "" || !Number.isFinite(Number(value)))
        ? null
        : Number(values.reduce((total, value) => total + Number(value), 0).toFixed(2))
    },
    field(key) {
      if (!this.employee) return undefined
      const profile = { ...(this.employee.fields || {}), ...(this.employee.profile || {}) }
      return profile[key] !== undefined && profile[key] !== null ? profile[key] : this.employee[key]
    },
    dateRelation(date, businessDate) {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date || "") || !/^\d{4}-\d{2}-\d{2}$/.test(businessDate || "")) return "UNKNOWN"
      if (date > businessDate) return "FUTURE"
      if (date < businessDate) return "HISTORICAL"
      return "TODAY"
    },
    requestKey() {
      return `transfer-${this.employee && this.employee.userId || "employee"}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    },
    numberField(key) {
      const value = this.field(key)
      return value === null || value === undefined || value === "" || !Number.isFinite(Number(value)) ? null : Number(value)
    },
    initialModel() {
      return {
        ...emptyModel(),
        targetDeptId: this.currentDeptId,
        postId: this.currentPostId,
        jobGradeCode: this.field("jobGrade") || "",
        directSupervisorId: this.numberField("directSupervisorUserId"),
        workLocation: this.field("workLocation") || "",
        workCityLevel: this.field("workCityLevel") || "",
        legalEntityId: this.numberField("legalEntityId"),
        legalEntityCode: this.field("legalEntityCode") || "",
        legalEntityName: this.field("legalEntity") || this.field("legalEntityName") || "",
        baseSalary: this.numberField("baseSalary"),
        postSalary: this.numberField("postSalary"),
        fieldAllowance: this.numberField("fieldAllowance"),
        performanceSalary: this.numberField("performanceSalary"),
        salaryTotal: this.numberField("salaryTotal")
      }
    },
    openDialog() {
      const generation = ++this.generation
      this.model = this.initialModel()
      this.submitting = false
      this.businessDate = ""
      this.businessDateLoading = true
      this.requestId = this.requestKey()
      this.errorMessage = ""
      this.riskDialogOpen = false
      this.riskAcknowledged = false
      this.historicalReason = ""
      return getHrTransferBusinessDate().then(response => {
        if (generation !== this.generation || !this.visible) return null
        const date = response && response.data && response.data.businessDate
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date || "")) throw new Error("服务端业务日期无效")
        this.businessDate = date
        this.model.effectiveDate = date
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
    buildPayload(riskConfirmation) {
      return {
        requestId: this.requestId,
        effectiveDate: this.model.effectiveDate,
        targetDeptId: this.model.targetDeptId,
        targetDeptName: this.selectedDeptName,
        postId: this.model.postId,
        postCode: this.selectedPost && this.selectedPost.postCode,
        postName: this.selectedPostName,
        jobGradeCode: this.model.jobGradeCode,
        jobGradeName: this.model.jobGradeCode,
        workLocation: this.model.workLocation,
        workCityLevel: this.model.workCityLevel,
        directSupervisorId: this.model.directSupervisorId,
        directSupervisorName: this.selectedSupervisor && this.selectedSupervisor.employeeName,
        legalEntityId: this.model.legalEntityId,
        legalEntityCode: this.model.legalEntityCode,
        legalEntityName: this.model.legalEntityName,
        adjustSalary: this.model.adjustSalary && this.canAdjustSalary,
        ...(this.model.adjustSalary && this.canAdjustSalary ? {
          baseSalary: this.model.baseSalary,
          postSalary: this.model.postSalary,
          fieldAllowance: this.model.fieldAllowance,
          performanceSalary: this.model.performanceSalary,
          salaryTotal: this.model.salaryTotal
        } : {}),
        riskConfirmation: riskConfirmation || null
      }
    },
    historicalConfirmation() {
      return {
        confirmed: true,
        employeeId: this.employee.userId,
        employeeName: this.employeeName,
        beforeDeptId: this.currentDeptId,
        beforeDeptName: this.currentDeptName,
        beforePostId: this.currentPostId,
        beforePostName: this.currentPostName,
        afterDeptId: this.model.targetDeptId,
        afterDeptName: this.selectedDeptName,
        afterPostId: this.model.postId,
        afterPostName: this.selectedPostName,
        effectiveDate: this.model.effectiveDate,
        operationDate: this.businessDate,
        riskStatement: "该操作将按历史日期补录并立即修改当前员工档案",
        reason: this.historicalReason
      }
    },
    submit() {
      if (!this.canSubmit) return Promise.resolve(null)
      if (this.isHistorical) {
        this.riskDialogOpen = true
        return Promise.resolve(null)
      }
      return this.submitPayload(null)
    },
    confirmHistorical() {
      if (!this.riskAcknowledged || !this.historicalReason || !this.isHistorical) return Promise.resolve(null)
      return this.submitPayload(this.historicalConfirmation())
    },
    submitPayload(riskConfirmation) {
      if (this.submitting || !this.canSubmit || !this.visible) return Promise.resolve(null)
      const generation = this.generation
      const requestId = this.requestId
      const employeeId = this.employee && this.employee.userId
      this.submitting = true
      this.errorMessage = ""
      return confirmHrEmployeeTransfer(employeeId, this.buildPayload(riskConfirmation)).then(response => {
        if (generation !== this.generation || requestId !== this.requestId || !this.visible) return null
        this.riskDialogOpen = false
        const result = response && response.data ? response.data : response
        this.$emit("confirmed", result || {})
        this.$emit("update:visible", false)
        return result
      }).catch(error => {
        if (generation !== this.generation || requestId !== this.requestId || !this.visible) return null
        this.errorMessage = error && error.message || "调岗确认失败，请核对信息后重试。"
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
.transfer-summary { display: grid; grid-template-columns: auto minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 10px; margin-bottom: 16px; padding: 13px; border: 1px solid #dbe4ef; border-radius: 7px; background: #f8fafc; }
.transfer-summary strong { grid-column: 1 / -1; color: #1f2937; }
.transfer-summary span { color: #475569; }
.transfer-form ::v-deep .el-input-number { width: 100%; }
.transfer-section-title { margin: 4px 0 14px; padding-bottom: 8px; border-bottom: 1px solid #e5e7eb; color: #334155; font-size: 14px; }
.auto-total-note { display: block; margin-top: 4px; color: #64748b; line-height: 1.4; }
.date-warning { display: block; margin-top: 5px; color: #d93025; line-height: 1.4; }
.automation-note { margin: 0; padding: 11px 13px; border-radius: 6px; background: #f0f7ff; color: #476582; line-height: 1.6; }
.risk-details { margin: 14px 0; }
.risk-form { margin-top: 14px; }
::v-deep .hr-transfer-dialog {
  max-height: 90vh;
  margin-top: 5vh !important;
  display: flex;
  flex-direction: column;
}
::v-deep .hr-transfer-dialog .el-dialog__body { overflow-y: auto; padding-bottom: 14px; }
::v-deep .hr-transfer-dialog .el-dialog__footer { flex-shrink: 0; padding-top: 14px; border-top: 1px solid #e5e7eb; background: #fff; }
@media (max-width: 700px) {
  .transfer-summary { grid-template-columns: 1fr; }
  .transfer-summary strong { grid-column: auto; }
  .transfer-summary i { transform: rotate(90deg); }
}
</style>
