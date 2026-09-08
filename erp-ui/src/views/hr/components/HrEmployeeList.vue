<template>
  <div class="app-container hr-page">
    <div class="hr-page-header">
      <div class="hr-page-title">
        <span class="hr-page-title__icon" aria-hidden="true"><i class="el-icon-user-solid" /></span>
        <div>
          <span class="hr-page-title__eyebrow">员工档案</span>
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </div>
      </div>
      <div class="hr-page-actions">
        <el-button
          v-if="signDataImportActionsEnabled"
          size="mini"
          type="success"
          plain
          icon="el-icon-s-claim"
          @click="openSignDataImport"
          v-hasPermi="['oa:signTask:send']"
        >批量处理入职合同<span v-if="selectedSignDataEmployeeCount > 0">（{{ selectedSignDataEmployeeCount }}）</span></el-button>
        <el-button
          v-if="signDataImportActionsEnabled && selectedSignDataEmployeeCount > 0"
          size="mini"
          type="text"
          @click="clearSignDataSelection"
          v-hasPermi="['oa:signTask:send']"
        >清空选择</el-button>
        <el-button v-if="showImport" size="mini" type="info" plain icon="el-icon-upload2" @click="handleImport" v-hasPermi="['hr:import:confirm']">导入</el-button>
        <el-button v-if="showTemplate" size="mini" type="primary" plain icon="el-icon-document" @click="handleTemplate" v-hasPermi="['hr:import:template']">模板</el-button>
        <el-button v-if="showExport" size="mini" type="warning" plain icon="el-icon-download" @click="handleExport" v-hasPermi="['hr:employee:export']">导出</el-button>
      </div>
    </div>

    <div class="hr-task-strip">
      <button
        v-for="task in taskCards"
        :key="task.key"
        type="button"
        class="hr-task-card"
        :class="[`is-${task.type}`, { active: activeTask === task.key }]"
        :aria-pressed="activeTask === task.key ? 'true' : 'false'"
        @click="setActiveTask(task.key)"
      >
        <span class="hr-task-card__icon" aria-hidden="true"><i :class="taskIcon(task.key)" /></span>
        <span class="hr-task-card__copy">
          <strong>{{ task.value }}{{ task.suffix || "" }}</strong>
          <span>{{ task.label }}</span>
        </span>
        <span v-if="activeTask === task.key" class="hr-task-card__state">当前</span>
      </button>
    </div>

    <div class="search-card hr-smart-search">
      <div class="hr-search-line">
        <el-input
          v-model="queryParams.keyword"
          class="hr-keyword-input"
          prefix-icon="el-icon-search"
          placeholder="搜索姓名 / 手机号 / 员工号 / 岗位工号 / 登录账号"
          clearable
          @keyup.enter.native="handleQuery"
        />
        <el-button class="hr-search-submit" type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button :class="{ 'is-open': advancedFilterOpen }" icon="el-icon-s-operation" size="mini" @click="advancedFilterOpen = !advancedFilterOpen">筛选</el-button>
        <el-button :class="{ 'is-open': columnSettingOpen }" icon="el-icon-set-up" size="mini" @click="columnSettingOpen = !columnSettingOpen">列设置</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </div>

      <el-collapse-transition>
        <div v-show="advancedFilterOpen" class="hr-filter-panel">
          <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="82px">
            <el-form-item label="组织" prop="deptId">
              <el-select v-model="queryParams.deptId" placeholder="请选择部门或门店" clearable filterable style="width: 220px">
                <el-option v-for="item in departmentOptions" :key="item.deptId" :label="item.deptName" :value="item.deptId" />
              </el-select>
            </el-form-item>
            <el-form-item label="员工状态" prop="employeeStatus">
              <el-select v-model="queryParams.employeeStatus" placeholder="员工状态" clearable filterable style="width: 150px">
                <el-option v-for="item in employeeStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="人员类别" prop="employeeCategory">
              <el-select v-model="queryParams.employeeCategory" placeholder="人员类别" clearable filterable style="width: 150px">
                <el-option v-for="item in employeeCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="完整度" prop="completenessStatus">
              <el-select v-model="queryParams.completenessStatus" placeholder="完整度" clearable style="width: 150px">
                <el-option label="完整" value="COMPLETE" />
                <el-option label="缺资料" value="INCOMPLETE" />
              </el-select>
            </el-form-item>
            <el-form-item label="账号" prop="accountConfigurationStatus">
              <el-select v-model="queryParams.accountConfigurationStatus" placeholder="账号配置" clearable style="width: 150px">
                <el-option label="已配置" value="COMPLETE" />
                <el-option label="待配置" value="MISSING" />
              </el-select>
            </el-form-item>
            <el-form-item label="健康证" prop="healthCertificateStatus">
              <el-select v-model="queryParams.healthCertificateStatus" placeholder="证件状态" clearable style="width: 150px">
                <el-option label="有效" value="VALID" />
                <el-option label="即将到期" value="EXPIRING" />
                <el-option label="已过期" value="EXPIRED" />
                <el-option label="未提交" value="NOT_SUBMITTED" />
              </el-select>
            </el-form-item>
            <el-form-item label="到期范围" prop="healthCertificateExpiresFrom">
              <el-date-picker v-model="queryParams.healthCertificateExpiresFrom" type="date" value-format="yyyy-MM-dd" placeholder="开始日期" style="width:145px" />
              <span class="date-separator">至</span>
              <el-date-picker v-model="queryParams.healthCertificateExpiresTo" type="date" value-format="yyyy-MM-dd" placeholder="结束日期" style="width:145px" />
            </el-form-item>
          </el-form>
        </div>
      </el-collapse-transition>

      <el-collapse-transition>
        <div v-show="columnSettingOpen" class="hr-column-panel">
          <span>可选字段</span>
          <el-checkbox-group v-model="selectedOptionalColumns">
            <el-checkbox v-for="field in optionalListFields" :key="field.key" :label="field.key">{{ field.label }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-collapse-transition>
    </div>

    <div class="table-card hr-workbench-list" v-loading="loading">
      <div class="hr-list-header">
        <div class="hr-list-header__title">
          <span aria-hidden="true"><i class="el-icon-notebook-2" /></span>
          <div>
            <strong>员工摘要</strong>
            <small>集中查看身份、组织岗位与档案完整度</small>
          </div>
        </div>
        <span class="hr-list-header__queue">共 <strong>{{ total }}</strong> 条 <i /> {{ activeTaskLabel }}</span>
      </div>

      <div v-if="listError" class="hr-list-error">
        <el-alert :title="listError" type="error" :closable="false" show-icon />
        <el-button size="mini" type="primary" plain icon="el-icon-refresh" @click="retryList">重试</el-button>
      </div>

      <div v-if="!listError && rows.length" class="hr-employee-list">
        <div
          v-for="row in rows"
          :key="row.userId || profileRawValue(row, 'profileId')"
          class="hr-employee-row"
          :class="[completionTone(row), { 'is-contract-selected': isSignDataEmployeeSelected(row) }]"
          @click="openDetail(row)"
        >
          <div class="hr-row-main">
            <div class="hr-person-block">
              <el-checkbox
                v-if="signDataImportActionsEnabled"
                class="hr-contract-selector"
                :value="isSignDataEmployeeSelected(row)"
                :disabled="!employeeSelectableForSignData(row)"
                :aria-label="`选择${row.employeeName || row.nickName || row.userName || '员工'}处理入职合同`"
                @click.native.stop
                @change="toggleSignDataSelection(row, $event)"
                v-hasPermi="['oa:signTask:send']"
              />
              <span class="hr-person-avatar" aria-hidden="true">{{ employeeInitial(row) }}</span>
              <div class="hr-person-identity">
                <div class="hr-person">
                  <strong>{{ row.employeeName || row.nickName || row.userName || "-" }}</strong>
                  <span class="hr-person__phone"><i class="el-icon-mobile-phone" />{{ row.phoneNumberMasked || row.phonenumber || "-" }}</span>
                </div>
                <div class="hr-person-ids">
                  <span>员工号 {{ profileValue(row, "employeeNo") }}</span>
                  <span>岗位工号 {{ row.positionNo || profileValue(row, "positionNo") }}</span>
                </div>
              </div>
            </div>
            <div class="hr-row-tags">
              <el-tag size="mini" :type="statusTagType(row)">{{ profileValue(row, "employeeStatus") }}</el-tag>
              <el-tag v-if="profileRawValue(row, 'employeeCategory')" size="mini" type="info">{{ profileValue(row, "employeeCategory") }}</el-tag>
              <el-tag size="mini" :type="healthCertificateTagType(row.healthCertificateStatus)">{{ healthCertificateLabel(row.healthCertificateStatus) }}</el-tag>
              <el-tag v-for="risk in riskTags(row)" :key="risk" size="mini" type="warning">{{ risk }}</el-tag>
            </div>
          </div>
          <div class="hr-row-sub">
            <span class="hr-row-context">
              <i class="el-icon-office-building" aria-hidden="true" />
              <span><small>组织岗位</small><strong>{{ deptName(row) }} · {{ row.positionName || profileValue(row, "positionNames") }}</strong></span>
            </span>
            <span class="hr-row-context">
              <i class="el-icon-document-checked" aria-hidden="true" />
              <span><small>证件信息</small><strong>健康证到期：{{ row.healthCertificateExpiresOn || "未提交" }}</strong></span>
            </span>
            <span class="hr-row-context hr-row-context--remark" :title="profileValue(row, 'remark')">
              <i class="el-icon-chat-line-square" aria-hidden="true" />
              <span><small>备注</small><strong>{{ profileValue(row, "remark") }}</strong></span>
            </span>
          </div>
          <div class="hr-row-meta">
            <div class="hr-progress">
              <span class="hr-progress__label"><span>档案覆盖度</span><strong>{{ completionPercent(row) }}%</strong></span>
              <el-progress :percentage="completionPercent(row)" :stroke-width="8" :show-text="false" />
              <small>必填字段：已填 {{ row.requiredCompletedFieldCount || 0 }}/{{ row.requiredApplicableFieldCount || 0 }}</small>
            </div>
            <span class="hr-account-state" :class="{ 'is-missing': row.accountConfigurationStatus === 'MISSING' }">
              <i :class="row.accountConfigurationStatus === 'MISSING' ? 'el-icon-warning-outline' : 'el-icon-circle-check'" />
              {{ accountText(row) }}
            </span>
            <div class="hr-row-actions">
              <el-button
                v-if="source === 'employee' && mode === 'employee' && row.profileInitialized === false"
                size="mini"
                type="text"
                icon="el-icon-document-add"
                :loading="samePositiveDecimalId(initializeLoadingUserId, row.userId)"
                @click.stop="handleInitializeProfile(row)"
                v-hasPermi="['hr:employee:edit']"
              >建立档案</el-button>
              <el-button
                v-if="source === 'employee' && mode === 'employee'"
                size="mini"
                type="text"
                icon="el-icon-sort"
                @click.stop="openTransfer(row)"
                v-hasPermi="['hr:employee:transfer']"
              >确认调岗</el-button>
              <el-button
                v-if="source === 'employee' && mode === 'employee' && profileValue(row, 'employeeStatus') !== '离职'"
                size="mini"
                type="text"
                icon="el-icon-circle-close"
                @click.stop="openOffboarding(row)"
                v-hasPermi="['hr:employee:offboard']"
              >确认离职</el-button>
              <el-button class="hr-row-actions__detail" size="mini" type="text" icon="el-icon-view" @click.stop="openDetail(row)" v-hasPermi="['hr:employee:query']">查看资料</el-button>
            </div>
          </div>
          <div v-if="selectedOptionalColumns.length" class="hr-optional-fields">
            <span v-for="field in visibleOptionalFields" :key="field.key">{{ field.label }}：{{ profileValue(row, field.key) }}</span>
          </div>
        </div>
      </div>

      <div v-else-if="!rows.length" class="hr-empty">{{ listEmptyText }}</div>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </div>

    <hr-profile-detail-drawer
      :visible.sync="detailOpen"
      :detail="detail"
      :missing-fields="detail && Array.isArray(detail.missingRequiredFields) ? detail.missingRequiredFields : (detail && Array.isArray(detail.missingProfileFields) ? detail.missingProfileFields : [])"
      :completion-percent="detail ? completionPercent(detail) : 0"
      :profile-value="profileValue"
      :profile-raw-value="profileRawValue"
      :dept-name="deptName"
      :allow-transfer="mode === 'employee'"
      :allow-offboarding="mode === 'employee' && detail && profileValue(detail, 'employeeStatus') !== '离职'"
      :show-onboard-contract-actions="signDataImportActionsEnabled"
      @export="handleExportSingle"
      @edit="handleEditProfile"
      @transfer="openTransfer"
      @offboard="openOffboarding"
      @onboard-contract="openDetailSignDataImport"
      @refresh="refreshDetail"
    />

    <hr-profile-edit-drawer
      ref="editDrawer"
      :visible.sync="editOpen"
      :detail="editDetail"
      :saving="saveLoading"
      :missing-fields="editDetail && Array.isArray(editDetail.missingRequiredFields) ? editDetail.missingRequiredFields : (editDetail && Array.isArray(editDetail.missingProfileFields) ? editDetail.missingProfileFields : [])"
      @save="handleSaveProfile"
    />

    <hr-employee-transfer-dialog
      :visible.sync="transferOpen"
      :employee="transferEmployee"
      :options="transferOptions"
      @confirmed="handleTransferConfirmed"
    />

    <hr-offboarding-dialog
      :visible.sync="offboardingOpen"
      :employee="offboardingEmployee"
      @confirmed="handleOffboardingConfirmed"
    />

    <hr-sign-data-import-dialog
      v-if="signDataImportActionsEnabled"
      :visible.sync="signDataImportOpen"
      :employees="signDataImportEmployees"
      @completed="handleSignDataImportCompleted"
    />

    <excel-import-dialog
      v-if="showImport"
      ref="importRef"
      title="人事员工档案导入"
      :action="importAction"
      :template-action="templateAction"
      template-file-name="员工档案导入模板"
      update-support-label="是否更新已经存在的员工档案"
      @success="getList"
    />
  </div>
</template>

<script>
import ExcelImportDialog from "@/components/ExcelImportDialog"
import { confirmExportAction } from "@/utils/exportConfirm"
import HrProfileDetailDrawer from "./HrProfileDetailDrawer"
import HrProfileEditDrawer from "./HrProfileEditDrawer"
import HrEmployeeTransferDialog from "./HrEmployeeTransferDialog"
import HrSignDataImportDialog from "./HrSignDataImportDialog"
import {
  formatProfileDisplayValue,
  HR_TASKS,
  OPTIONAL_LIST_FIELDS,
  profileFieldLabel
} from "./hrFieldConfig"
import {
  HR_EXPORT_ACTION,
  getHrEmployee,
  getHrEmployeeFormOptions,
  getHrEmployeeSummary,
  initializeHrEmployeeProfile,
  listHrEmployees,
  updateHrEmployee
} from "@/api/hr/employee"
import { listHrOnboarding } from "@/api/hr/onboarding"
import { listHrCompletenessEmployees } from "@/api/hr/completeness"
import {
  LEGACY_HR_EMPLOYEE_IMPORT_ACTION,
  LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION
} from "@/api/hr/legacyEmployeeImport"
import { loadHrEmployeePreferences, saveHrEmployeePreferences } from "@/utils/hrEmployeePreferences"

const HrOffboardingDialog = () => import("./HrOffboardingDialog")

function normalizePositiveDecimalId(value) {
  if (value === undefined || value === null) return ""
  const text = String(value).trim()
  if (!/^\d+$/.test(text)) return ""
  return text.replace(/^0+/, "")
}

export default {
  name: "HrEmployeeList",
  components: {
    ExcelImportDialog,
    HrProfileDetailDrawer,
    HrProfileEditDrawer,
    HrEmployeeTransferDialog,
    HrOffboardingDialog,
    HrSignDataImportDialog
  },
  props: {
    title: {
      type: String,
      required: true
    },
    subtitle: {
      type: String,
      default: "员工档案数据"
    },
    source: {
      type: String,
      default: "employee"
    },
    mode: {
      type: String,
      default: "employee"
    },
    defaultEmployeeStatus: {
      type: String,
      default: ""
    },
    showCompleteness: {
      type: Boolean,
      default: false
    },
    showImport: {
      type: Boolean,
      default: false
    },
    showTemplate: {
      type: Boolean,
      default: false
    },
    showExport: {
      type: Boolean,
      default: true
    },
    showOnboardContractActions: {
      type: Boolean,
      default: false
    },
    initialEmployeeId: {
      type: [String, Number],
      default: undefined
    },
    initialAction: {
      type: String,
      default: undefined
    },
    initialFilters: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      loading: false,
      listError: "",
      activeTask: this.mode === "completeness" ? "incomplete" : "all",
      advancedFilterOpen: false,
      columnSettingOpen: false,
      selectedOptionalColumns: [],
      preferencesReady: false,
      pendingRouteEmployeeId: undefined,
      pendingRouteAction: undefined,
      rows: [],
      total: 0,
      listRequestSequence: 0,
      summaryRequestSequence: 0,
      detailRequestSequence: 0,
      detail: null,
      detailOpen: false,
      detailLoading: false,
      editDetail: null,
      editOpen: false,
      saveLoading: false,
      initializeLoadingUserId: undefined,
      transferOpen: false,
      transferEmployee: null,
      transferRequestSequence: 0,
      transferOptions: {
        departments: [],
        posts: [],
        supervisors: []
      },
      offboardingOpen: false,
      offboardingEmployee: null,
      offboardingRequestSequence: 0,
      signDataImportOpen: false,
      signDataImportEmployees: [],
      selectedSignDataEmployees: {},
      importAction: LEGACY_HR_EMPLOYEE_IMPORT_ACTION,
      templateAction: LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION,
      optionalListFields: OPTIONAL_LIST_FIELDS,
      employeeStatusOptions: [],
      employeeCategoryOptions: [],
      departmentOptions: [],
      summary: {
        totalEmployeeCount: 0,
        completeEmployeeCount: 0,
        incompleteEmployeeCount: 0,
        requiredCompleteEmployeeCount: 0,
        requiredIncompleteEmployeeCount: 0,
        accountConfigurationRiskCount: 0,
        averageProfileCompletionPercent: 0,
        averageRequiredCompletionPercent: 0
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        keyword: undefined,
        deptId: undefined,
        employeeStatus: undefined,
        employeeCategory: undefined,
        completenessStatus: undefined,
        completenessMetric: "REQUIRED",
        accountConfigurationStatus: undefined,
        healthCertificateStatus: undefined,
        healthCertificateExpiresFrom: undefined,
        healthCertificateExpiresTo: undefined,
        contractDue: undefined,
        offboardAccountOnly: undefined,
        userId: undefined
      }
    }
  },
  computed: {
    taskCards() {
      const counts = {
        total: this.summary.totalEmployeeCount,
        complete: this.summary.requiredCompleteEmployeeCount ?? this.summary.completeEmployeeCount,
        incomplete: this.summary.requiredIncompleteEmployeeCount ?? this.summary.incompleteEmployeeCount,
        accountRisk: this.summary.accountConfigurationRiskCount,
        average: this.summary.averageRequiredCompletionPercent ?? this.summary.averageProfileCompletionPercent
      }
      return HR_TASKS.map(task => ({
        ...task,
        value: counts[task.countKey] || 0
      }))
    },
    activeTaskLabel() {
      const task = this.taskCards.find(item => item.key === this.activeTask)
      return task ? task.label : "全部员工"
    },
    visibleOptionalFields() {
      return this.optionalListFields.filter(field => this.selectedOptionalColumns.includes(field.key))
    },
    signDataImportActionsEnabled() {
      return this.showOnboardContractActions && this.source === "employee" && this.mode === "employee" &&
        this.signExcelImportFeatureEnabled && this.hasOnboardContractPermission
    },
    signExcelImportFeatureEnabled() {
      return String(process.env.VUE_APP_SIGN_EXCEL_IMPORT_ENABLED || "true").trim().toLowerCase() === "true"
    },
    hasOnboardContractPermission() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      const permissions = Array.isArray(getters.permissions) ? getters.permissions : []
      return permissions.includes("*:*:*") || permissions.includes("oa:signTask:send")
    },
    selectedSignDataEmployeesList() {
      return Object.keys(this.selectedSignDataEmployees)
        .map(key => this.selectedSignDataEmployees[key])
        .filter(Boolean)
    },
    selectedSignDataEmployeeCount() {
      return this.selectedSignDataEmployeesList.length
    },
    listEmptyText() {
      return this.listError ? "加载失败" : "暂无员工档案"
    }
  },
  created() {
    this.restorePreferences()
    this.applyInitialFilters(this.initialFilters)
    const initialEmployeeId = normalizePositiveDecimalId(this.initialEmployeeId)
    if (initialEmployeeId) {
      this.queryParams.userId = initialEmployeeId
      this.pendingRouteEmployeeId = initialEmployeeId
      this.pendingRouteAction = this.initialAction
    }
    if (this.defaultEmployeeStatus) {
      this.queryParams.employeeStatus = this.defaultEmployeeStatus
    }
    this.loadFormOptions()
    this.getList()
  },
  activated() {
    const employeeId = normalizePositiveDecimalId(this.initialEmployeeId)
    if (!employeeId || normalizePositiveDecimalId(this.queryParams.userId) === employeeId) {
      return Promise.resolve(null)
    }
    return this.openRouteEmployee(employeeId, this.initialAction)
  },
  watch: {
    initialEmployeeId(value, previous) {
      if (value !== previous) this.openRouteEmployee(value, this.initialAction)
    },
    initialAction(value, previous) {
      const employeeId = normalizePositiveDecimalId(this.initialEmployeeId)
      if (value !== previous && employeeId) {
        this.openRouteEmployee(employeeId, value)
      }
    },
    initialFilters: {
      deep: true,
      handler(value, previous) {
        const current = this.normalizedInitialFilters(value)
        const prior = this.normalizedInitialFilters(previous)
        if (JSON.stringify(current) === JSON.stringify(prior)) return
        this.applyInitialFilters(current)
        this.queryParams.pageNum = 1
        this.getList()
      }
    },
    advancedFilterOpen() {
      this.persistPreferences()
    },
    selectedOptionalColumns: {
      deep: true,
      handler() { this.persistPreferences() }
    },
    "queryParams.pageSize"() {
      this.persistPreferences()
    }
  },
  methods: {
    preferenceContext() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      let storage = null
      try { storage = typeof window !== "undefined" ? window.localStorage : null } catch (ignored) { storage = null }
      return {
        storage,
        userId: getters.id,
        permissions: Array.isArray(getters.permissions) ? getters.permissions : [],
        columns: this.optionalListFields.map(field => field.key)
      }
    },
    restorePreferences() {
      const context = this.preferenceContext()
      const preferences = loadHrEmployeePreferences(
        context.storage, context.userId, context.permissions, context.columns)
      this.advancedFilterOpen = preferences.advancedFilterOpen
      this.selectedOptionalColumns = preferences.selectedOptionalColumns
      this.queryParams.pageSize = preferences.pageSize
      this.preferencesReady = true
    },
    persistPreferences() {
      if (!this.preferencesReady) return
      const context = this.preferenceContext()
      saveHrEmployeePreferences(context.storage, context.userId, context.permissions, {
        advancedFilterOpen: this.advancedFilterOpen,
        selectedOptionalColumns: this.selectedOptionalColumns,
        pageSize: this.queryParams.pageSize
      }, context.columns)
    },
    normalizedInitialFilters(value) {
      const source = value && typeof value === "object" ? value : {}
      const deptId = Number(source.deptId)
      return {
        deptId: Number.isSafeInteger(deptId) && deptId > 0 ? deptId : undefined,
        contractDue: source.contractDue === true ? true : undefined,
        offboardAccountOnly: source.offboardAccountOnly === true ? true : undefined
      }
    },
    applyInitialFilters(value) {
      const filters = this.normalizedInitialFilters(value)
      this.queryParams.deptId = filters.deptId
      this.queryParams.contractDue = filters.contractDue
      this.queryParams.offboardAccountOnly = filters.offboardAccountOnly
      return filters
    },
    listRequest(params) {
      if (this.source === "onboarding") return listHrOnboarding(params)
      if (this.source === "completeness") return listHrCompletenessEmployees(params)
      return listHrEmployees(params)
    },
    getList() {
      const requestSequence = ++this.listRequestSequence
      this.loading = true
      this.listError = ""
      const params = { ...this.queryParams }
      const request = this.listRequest(params).then(response => {
        if (requestSequence !== this.listRequestSequence) return
        this.rows = response.rows || []
        this.total = response.total || 0
        const routeId = this.pendingRouteEmployeeId
        const routeAction = this.pendingRouteAction
        this.pendingRouteEmployeeId = undefined
        this.pendingRouteAction = undefined
        const routeRow = routeId && this.rows.find(row =>
          normalizePositiveDecimalId(row.userId) === normalizePositiveDecimalId(routeId))
        if (routeRow && routeAction === "initializeProfile") return this.handleInitializeProfile(routeRow)
        if (routeRow) return this.openDetail(routeRow)
      }).catch(error => {
        if (requestSequence !== this.listRequestSequence) return
        this.pendingRouteEmployeeId = undefined
        this.rows = []
        this.total = 0
        this.listError = (error && error.response && error.response.data && error.response.data.msg)
          || "员工档案加载失败，请重试"
      }).finally(() => {
        if (requestSequence !== this.listRequestSequence) return
        this.loading = false
      })
      this.loadSummary(params)
      return request
    },
    employeeWithExactId(payload, employeeId, fallback) {
      const normalizedEmployeeId = normalizePositiveDecimalId(employeeId)
      const employee = Object.assign({}, fallback || {}, payload || {})
      if (normalizedEmployeeId) employee.userId = normalizedEmployeeId
      return employee
    },
    retryList() {
      this.queryParams.pageNum = 1
      return this.getList()
    },
    openRouteEmployee(value, action = this.initialAction) {
      const employeeId = normalizePositiveDecimalId(value)
      const validId = employeeId || undefined
      this.queryParams.pageNum = 1
      this.queryParams.userId = validId
      this.pendingRouteEmployeeId = validId
      this.pendingRouteAction = action === "initializeProfile" ? action : undefined
      this.detailRequestSequence += 1
      this.detail = null
      this.detailOpen = false
      this.detailLoading = false
      return this.getList()
    },
    loadSummary(params) {
      const requestSequence = ++this.summaryRequestSequence
      if (this.source !== "employee") return
      const filters = { ...params }
      delete filters.pageNum
      delete filters.pageSize
      delete filters.completenessStatus
      delete filters.completenessMetric
      delete filters.accountConfigurationStatus
      getHrEmployeeSummary(filters).then(response => {
        if (requestSequence !== this.summaryRequestSequence) return
        this.summary = { ...this.summary, ...(response.data || {}) }
      }).catch(() => {
        if (requestSequence !== this.summaryRequestSequence) return
        this.summary = {
          totalEmployeeCount: 0,
          completeEmployeeCount: 0,
          incompleteEmployeeCount: 0,
          accountConfigurationRiskCount: 0,
          averageProfileCompletionPercent: 0
        }
      })
    },
    loadFormOptions() {
      if (this.source !== "employee") return
      getHrEmployeeFormOptions().then(response => {
        const options = response.data || {}
        this.departmentOptions = Array.isArray(options.departments) ? options.departments : []
        this.transferOptions = {
          departments: this.departmentOptions,
          posts: Array.isArray(options.posts) ? options.posts : [],
          supervisors: Array.isArray(options.supervisors) ? options.supervisors : []
        }
        this.employeeStatusOptions = this.fieldOptions(options, "employeeStatus")
        this.employeeCategoryOptions = this.fieldOptions(options, "employeeCategory")
      }).catch(() => {
        this.departmentOptions = []
        this.transferOptions = { departments: [], posts: [], supervisors: [] }
        this.employeeStatusOptions = []
        this.employeeCategoryOptions = []
      })
    },
    fieldOptions(options, key) {
      const enumValues = options.enumOptions && options.enumOptions[key]
      const dictionaryValues = options.dictionaries && options.dictionaries[key]
      return this.normalizeOptions(Array.isArray(enumValues) && enumValues.length ? enumValues : dictionaryValues)
    },
    normalizeOptions(values) {
      if (!Array.isArray(values)) return []
      return values.map(item => {
        if (item && typeof item === "object") {
          const value = item.value !== undefined ? item.value : item.dictValue
          const label = item.label !== undefined ? item.label : item.dictLabel
          return { value, label: label === undefined ? value : label }
        }
        return { value: item, label: item }
      }).filter(item => item.value !== undefined && item.value !== null)
    },
    setActiveTask(task) {
      this.activeTask = task
      this.queryParams.completenessStatus = undefined
      this.queryParams.accountConfigurationStatus = undefined
      if (task === "complete") this.queryParams.completenessStatus = "COMPLETE"
      if (task === "incomplete") this.queryParams.completenessStatus = "INCOMPLETE"
      if (task === "accountRisk") this.queryParams.accountConfigurationStatus = "MISSING"
      this.handleQuery()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize,
        keyword: undefined,
        deptId: undefined,
        employeeStatus: this.defaultEmployeeStatus || undefined,
        employeeCategory: undefined,
        completenessStatus: undefined,
        completenessMetric: "REQUIRED",
        accountConfigurationStatus: undefined,
        healthCertificateStatus: undefined,
        healthCertificateExpiresFrom: undefined,
        healthCertificateExpiresTo: undefined,
        contractDue: undefined,
        offboardAccountOnly: undefined,
        userId: undefined
      }
      this.applyInitialFilters(this.initialFilters)
      this.activeTask = this.mode === "completeness" ? "incomplete" : "all"
      this.resetForm("queryForm")
      this.getList()
    },
    profile(row) {
      if (!row) return {}
      return { ...(row.fields || {}), ...(row.profile || {}) }
    },
    profileRawValue(row, key) {
      if (!row) return undefined
      const profile = this.profile(row)
      if (profile[key] !== undefined && profile[key] !== null) return profile[key]
      return row ? row[key] : undefined
    },
    profileValue(row, key) {
      const value = this.profileRawValue(row, key)
      return formatProfileDisplayValue(key, value)
    },
    deptName(row) {
      if (!row) return "-"
      if (row.departmentName) return row.departmentName
      return row.dept ? row.dept.deptName || "-" : "-"
    },
    statusTagType(row) {
      const status = this.profileRawValue(row, "employeeStatus")
      if (status === "离职" || status === "待离职") return "danger"
      if (status === "待入职") return "warning"
      return "success"
    },
    taskIcon(taskKey) {
      return {
        all: "el-icon-user",
        complete: "el-icon-circle-check",
        incomplete: "el-icon-document",
        accountRisk: "el-icon-key",
        average: "el-icon-data-analysis"
      }[taskKey] || "el-icon-data-board"
    },
    employeeInitial(row) {
      const name = row && (row.employeeName || row.nickName || row.userName)
      return String(name || "-").trim().slice(0, 1).toUpperCase()
    },
    completionTone(row) {
      const percent = this.completionPercent(row)
      if (percent >= 100) return "is-complete"
      if (percent < 40) return "is-risk"
      return "is-progress"
    },
    accountText(row) {
      if (!row) return "-"
      return row.accountConfigurationStatus === "COMPLETE" ? "账号已配置" : "账号待配置"
    },
    missingFields(row) {
      const missing = row && (Array.isArray(row.missingRequiredFields) ? row.missingRequiredFields : row.missingProfileFields)
      if (!Array.isArray(missing)) return []
      return missing.map(profileFieldLabel)
    },
    missingText(row) {
      const missing = this.missingFields(row)
      return missing.length ? missing.join("、") : "完整"
    },
    completionPercent(row) {
      if (!row) return 0
      const value = row.requiredCompletionPercent
      const numeric = Number(value)
      if (value !== undefined && value !== null && value !== "" && Number.isFinite(numeric)) {
        return Math.max(0, Math.min(100, numeric))
      }
      const completed = Number(row.requiredCompletedFieldCount)
      const applicable = Number(row.requiredApplicableFieldCount)
      if (!Number.isFinite(completed) || !Number.isFinite(applicable)) return 0
      return applicable === 0 ? 100 : Math.max(0, Math.min(100, Math.round(completed * 100 / applicable)))
    },
    riskTags(row) {
      const tags = []
      const percent = this.completionPercent(row)
      if (percent < 100) tags.push(`业务必填${percent}%`)
      if (row && row.accountConfigurationStatus === "MISSING") tags.push("账号待配置")
      return tags
    },
    healthCertificateLabel(status) {
      return { VALID: "健康证有效", EXPIRING: "健康证即将到期", EXPIRED: "健康证已过期", NOT_SUBMITTED: "健康证未提交" }[status] || "健康证未提交"
    },
    healthCertificateTagType(status) {
      return { VALID: "success", EXPIRING: "warning", EXPIRED: "danger", NOT_SUBMITTED: "info" }[status] || "info"
    },
    samePositiveDecimalId(left, right) {
      const normalizedLeft = normalizePositiveDecimalId(left)
      return !!normalizedLeft && normalizedLeft === normalizePositiveDecimalId(right)
    },
    employeeSelectableForSignData(row) {
      return !!normalizePositiveDecimalId(row && row.userId)
    },
    signDataSelectionKey(row) {
      return normalizePositiveDecimalId(row && row.userId)
    },
    isSignDataEmployeeSelected(row) {
      if (!this.employeeSelectableForSignData(row)) return false
      return !!this.selectedSignDataEmployees[this.signDataSelectionKey(row)]
    },
    toggleSignDataSelection(row, selected) {
      if (!this.employeeSelectableForSignData(row)) return
      const key = this.signDataSelectionKey(row)
      if (!selected) {
        this.$delete(this.selectedSignDataEmployees, key)
        return
      }
      if (!this.selectedSignDataEmployees[key] && this.selectedSignDataEmployeeCount >= 100) {
        this.$modal.msgWarning("一次最多选择100名员工处理入职合同")
        return
      }
      const employeeId = normalizePositiveDecimalId(row.userId)
      this.$set(this.selectedSignDataEmployees, key, {
        employeeId,
        userId: employeeId,
        employeeName: row.employeeName || row.nickName || row.userName || `员工 #${row.userId}`,
        employeeStatus: this.profileRawValue(row, "employeeStatus") || ""
      })
    },
    clearSignDataSelection() {
      this.selectedSignDataEmployees = {}
    },
    openSignDataImport() {
      if (this.selectedSignDataEmployeeCount > 100) {
        this.$modal.msgWarning("一次最多选择100名员工处理入职合同")
        return
      }
      this.signDataImportEmployees = this.selectedSignDataEmployeesList.map(employee => Object.assign({}, employee))
      this.signDataImportOpen = true
    },
    openDetailSignDataImport(employee) {
      const employeeId = normalizePositiveDecimalId(employee && employee.userId)
      if (!this.signDataImportActionsEnabled || !employeeId || !employee) {
        this.$modal.msgWarning("当前员工缺少有效账号，无法准备入职合同")
        return
      }
      this.signDataImportEmployees = [{
        employeeId,
        userId: employeeId,
        employeeName: employee.employeeName || employee.nickName || employee.userName || `员工 #${employeeId}`,
        employeeStatus: this.profileRawValue(employee, "employeeStatus") || ""
      }]
      this.signDataImportOpen = true
    },
    handleSignDataImportCompleted() {
      const listRefresh = this.getList()
      const todoRefresh = this.$store && typeof this.$store.dispatch === "function"
        ? this.$store.dispatch("todo/refreshSummaries").catch(() => null)
        : Promise.resolve(null)
      return Promise.all([listRefresh, todoRefresh])
    },
    openDetail(row) {
      const requestSequence = ++this.detailRequestSequence
      const employeeId = normalizePositiveDecimalId(row && row.userId)
      if (!employeeId) {
        this.detail = row
        this.detailOpen = true
        this.detailLoading = false
        return
      }
      this.detailLoading = true
      getHrEmployee(employeeId).then(response => {
        if (requestSequence !== this.detailRequestSequence ||
          employeeId !== normalizePositiveDecimalId(row.userId)) return
        const currentRow = this.rows.find(item =>
          normalizePositiveDecimalId(item && item.userId) === employeeId) || row
        this.detail = this.employeeWithExactId(response.data, employeeId, currentRow)
        this.detailOpen = true
      }).catch(() => {
        if (requestSequence !== this.detailRequestSequence ||
          employeeId !== normalizePositiveDecimalId(row.userId)) return
        this.detail = null
        this.detailOpen = false
      }).finally(() => {
        if (requestSequence !== this.detailRequestSequence ||
          employeeId !== normalizePositiveDecimalId(row.userId)) return
        this.detailLoading = false
      })
    },
    refreshDetail() {
      if (this.detail) {
        this.openDetail(this.detail)
      }
    },
    handleExport() {
      confirmExportAction(this, {
        moduleName: this.title,
        rangeLabel: "当前查询条件下的员工档案",
        filterLabel: `关键词：${this.queryParams.keyword || "全部"}；员工状态：${this.queryParams.employeeStatus || "全部"}`
      }).then(() => {
        this.download(HR_EXPORT_ACTION, { ...this.queryParams }, this.exportFileName(this.title))
      })
    },
    handleExportSingle(row) {
      confirmExportAction(this, {
        moduleName: "单个员工档案",
        rangeLabel: row.employeeName || row.nickName || row.userName || "当前员工",
        filterLabel: `用户编号：${row.userId || "未绑定"}`
      }).then(() => {
        this.download(HR_EXPORT_ACTION, { userId: row.userId }, this.exportFileName(row.employeeName || row.nickName || row.userName || "员工档案"))
      })
    },
    handleEditProfile(row) {
      this.editDetail = row || this.detail
      this.editOpen = true
    },
    handleInitializeProfile(row) {
      const employeeId = normalizePositiveDecimalId(row && row.userId)
      if (!employeeId) return Promise.resolve(null)
      const employeeName = row.employeeName || row.nickName || row.userName || `用户 ${employeeId}`
      if (row.profileInitialized === true) {
        return getHrEmployee(employeeId).then(response => {
          this.editDetail = this.employeeWithExactId(response.data, employeeId, row)
          this.editOpen = true
          return this.editDetail
        })
      }
      return this.$modal.confirm(`将为“${employeeName}”建立空白员工档案，建立后请继续补齐业务必填资料。`)
        .then(() => {
          this.initializeLoadingUserId = employeeId
          return initializeHrEmployeeProfile(employeeId)
        })
        .then(response => {
          const detail = this.employeeWithExactId(response.data, employeeId, { ...row, profileInitialized: true })
          this.$modal.msgSuccess("员工档案已建立，请继续补齐资料")
          this.detail = detail
          this.detailOpen = false
          this.editDetail = detail
          this.editOpen = true
          this.getList()
          return detail
        })
        .catch(error => {
          if (error === "cancel" || error === "close") return null
          const message = error && error.response && error.response.data && error.response.data.msg
          this.$modal.msgError(message || "员工档案建立失败，请重试")
          return null
        })
        .finally(() => { this.initializeLoadingUserId = undefined })
    },
    openTransfer(row) {
      const employeeId = normalizePositiveDecimalId(row && row.userId)
      if (this.source !== "employee" || this.mode !== "employee" || !employeeId) return Promise.resolve(null)
      const requestSequence = ++this.transferRequestSequence
      return getHrEmployee(employeeId).then(response => {
        if (requestSequence !== this.transferRequestSequence) return null
        this.transferEmployee = this.employeeWithExactId(response.data, employeeId, row)
        this.transferOpen = true
        return this.transferEmployee
      }).catch(() => null)
    },
    handleTransferConfirmed() {
      const employeeId = normalizePositiveDecimalId(this.transferEmployee && this.transferEmployee.userId)
      this.$modal.msgSuccess("调岗已确认，合同任务正在生成")
      this.transferOpen = false
      this.getList()
      if (this.detail && this.samePositiveDecimalId(this.detail.userId, employeeId)) {
        getHrEmployee(employeeId).then(response => {
          if (this.detail && this.samePositiveDecimalId(this.detail.userId, employeeId)) {
            this.detail = this.employeeWithExactId(response.data, employeeId, this.detail)
          }
        })
      }
      return this.$store.dispatch("todo/invalidateAfterMutation").catch(() => null)
    },
    openOffboarding(row) {
      const employeeId = normalizePositiveDecimalId(row && row.userId)
      if (this.source !== "employee" || this.mode !== "employee" || !employeeId ||
        this.profileValue(row, "employeeStatus") === "离职") return Promise.resolve(null)
      const requestSequence = ++this.offboardingRequestSequence
      return getHrEmployee(employeeId).then(response => {
        if (requestSequence !== this.offboardingRequestSequence) return null
        const employee = this.employeeWithExactId(response.data, employeeId, row)
        if (this.profileValue(employee, "employeeStatus") === "离职") return null
        this.offboardingEmployee = employee
        this.offboardingOpen = true
        return employee
      }).catch(() => null)
    },
    handleOffboardingConfirmed() {
      const employeeId = normalizePositiveDecimalId(this.offboardingEmployee && this.offboardingEmployee.userId)
      this.$modal.msgSuccess("离职已确认，合同任务正在生成")
      this.offboardingOpen = false
      const listRefresh = this.getList()
      let detailRefresh = Promise.resolve(null)
      if (this.detail && this.samePositiveDecimalId(this.detail.userId, employeeId)) {
        detailRefresh = getHrEmployee(employeeId).then(response => {
          if (this.detail && this.samePositiveDecimalId(this.detail.userId, employeeId)) {
            this.detail = this.employeeWithExactId(response.data, employeeId, this.detail)
          }
        }).catch(() => null)
      }
      const todoRefresh = this.$store.dispatch("todo/refreshSummaries").catch(() => null)
      return Promise.all([listRefresh, detailRefresh, todoRefresh])
    },
    handleSaveProfile(payload) {
      this.saveLoading = true
      updateHrEmployee(payload.userId, payload).then(() => {
        this.$modal.msgSuccess("档案已保存")
        this.editOpen = false
        this.getList()
        if (payload.userId) {
          const employeeId = normalizePositiveDecimalId(payload.userId)
          return getHrEmployee(employeeId || payload.userId).then(response => {
            this.detail = this.employeeWithExactId(response.data, employeeId, this.detail)
          })
        }
        return null
      }).catch(error => {
        if (this.$refs.editDrawer && this.$refs.editDrawer.applyServerErrors) {
          this.$refs.editDrawer.applyServerErrors(error)
        }
      }).finally(() => {
        this.saveLoading = false
      })
    },
    handleTemplate() {
      this.download(LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION.replace(/^\/system\//, "system/"), {}, this.exportFileName("员工档案导入模板"))
    },
    handleImport() {
      this.$refs.importRef.open()
    }
  }
}
</script>

<style lang="scss" scoped>
.hr-page {
  --hr-accent: var(--erp-primary, #0b6b53);
  --hr-accent-dark: var(--erp-primary-hover, #075441);
  --hr-ink: var(--erp-text, #17211d);
  --hr-muted: var(--erp-text-secondary, #66736d);
  min-width: 760px;
  min-height: calc(100vh - 144px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px 24px;
  background: var(--erp-canvas, #f4f5f2);
}

.hr-page-header {
  min-height: 96px;
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 18px 22px;
  overflow: hidden;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  background: var(--erp-surface, #ffffff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));

  &::before,
  &::after {
    display: none;
    content: none;
  }

  h2 {
    margin: 3px 0 4px;
    color: var(--hr-ink);
    font-size: 27px;
    font-weight: 700;
    letter-spacing: -0.02em;
    line-height: 1.15;
  }

  p { margin: 0; color: var(--hr-muted); font-size: 13px; line-height: 1.6; }
}

.hr-page-title {
  z-index: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 17px;
}

.hr-page-title__icon {
  width: 50px;
  height: 50px;
  flex: 0 0 50px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #9fbfb2;
  border-radius: 13px;
  background: var(--hr-accent);
  color: #fff;
  box-shadow: 0 10px 22px rgba(11, 107, 83, 0.2);
  font-size: 23px;
}

.hr-page-title__eyebrow {
  color: var(--hr-accent);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.17em;
}

.hr-page-actions {
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;

  ::v-deep .el-button {
    height: 40px;
    margin-left: 0;
    padding: 0 17px;
    border-radius: 11px;
    background: #ffffff;
    font-weight: 600;
  }

  ::v-deep .el-button--warning.is-plain {
    border-color: #e9d69b;
    background: #fffaf0;
    color: #b17a16;
  }
}

.hr-task-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 12px;
}

.hr-task-card {
  --task-accent: var(--hr-accent);
  --task-soft: var(--erp-primary-soft, #e7f2ed);
  min-width: 0;
  min-height: 76px;
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 13px;
  text-align: left;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  background: #ffffff;
  box-shadow: var(--erp-shadow-subtle, 0 2px 10px rgba(23, 33, 29, 0.045));
  cursor: pointer;
  transition: border-color var(--motion-duration-fast) var(--motion-ease-standard), box-shadow var(--motion-duration-fast) var(--motion-ease-standard);

  &:hover {
    border-color: var(--task-accent);
    box-shadow: 0 10px 24px rgba(23, 33, 29, 0.08);
  }

  &:focus-visible {
    outline: 3px solid rgba(11, 107, 83, 0.22);
    outline-offset: 2px;
  }

  &.is-success { --task-accent: #28a978; --task-soft: #eaf8f3; }
  &.is-warning { --task-accent: #d99a26; --task-soft: #fff6e3; }
  &.is-danger { --task-accent: #dd6472; --task-soft: #fff0f2; }
  &.is-info { --task-accent: #2d9ca7; --task-soft: #eaf8f9; }

  &.active {
    border-color: var(--task-accent);
    background: var(--task-soft);
    box-shadow: inset 3px 0 0 var(--task-accent);
  }
}

.hr-task-card__icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--task-soft);
  color: var(--task-accent);
  font-size: 17px;
}

.hr-task-card__copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;

  strong { color: #25344e; font-size: 21px; line-height: 1; }
  > span { overflow: hidden; color: #7b879a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
}

.hr-task-card__state {
  position: absolute;
  top: 8px;
  right: 9px;
  color: var(--task-accent);
  font-size: 9px;
  font-weight: 700;
}

.hr-smart-search {
  padding: 12px 14px;
  border-color: var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
}

.hr-search-line {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) repeat(4, auto);
  gap: 8px;
  align-items: center;

  ::v-deep .el-input__inner {
    height: 42px;
    border-color: #dce3ee;
    border-radius: 12px;
    background: #f9fafc;
    transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
  }

  ::v-deep .el-input__inner:focus {
    border-color: var(--hr-accent);
    background: #fff;
    box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.18);
  }

  ::v-deep > .el-button {
    height: 42px;
    margin-left: 0;
    padding: 0 16px;
    border-color: #dce3ee;
    border-radius: 11px;
    color: #4d5b70;
    font-weight: 500;
  }

  ::v-deep > .el-button.is-open {
    border-color: #9fbfb2;
    background: var(--erp-primary-soft, #e7f2ed);
    color: var(--hr-accent);
  }

  ::v-deep > .hr-search-submit {
    border-color: var(--hr-accent);
    background: var(--hr-accent);
    color: #fff;
    box-shadow: 0 7px 16px rgba(11, 107, 83, 0.18);
  }
}

.hr-filter-panel,
.hr-column-panel {
  margin-top: 14px;
  padding: 16px 14px 4px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: 13px;
  background: var(--erp-surface-muted, #f8f8f5);
}

.hr-filter-panel {
  ::v-deep .el-form-item { margin-bottom: 12px; }
  ::v-deep .el-input__inner { border-color: #dfe5ee; border-radius: 9px; }
}

.hr-column-panel {
  display: flex;
  gap: 14px;
  align-items: flex-start;

  > span {
    flex: 0 0 auto;
    color: #5e6c80;
    font-size: 12px;
    font-weight: 600;
  }
}

.hr-workbench-list {
  min-height: 280px;
  padding: 18px;
  border-color: var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
}

.hr-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 15px;
  color: #33435c;
}

.hr-list-header__title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;

  > span {
    width: 36px;
    height: 36px;
    flex: 0 0 36px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 11px;
    background: var(--erp-primary-soft, #e7f2ed);
    color: var(--hr-accent);
    font-size: 17px;
  }

  > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  strong { font-size: 14px; }
  small { color: #929cad; font-size: 10px; }
}

.hr-list-header__queue {
  flex: 0 0 auto;
  padding: 8px 11px;
  border: 1px solid #e2e7f0;
  border-radius: 10px;
  background: #f8f9fc;
  color: #7d889b;
  font-size: 11px;

  strong { color: var(--hr-accent); font-size: 13px; }
  i { width: 3px; height: 3px; display: inline-block; margin: 0 5px; border-radius: 50%; background: #abb5c5; vertical-align: middle; }
}

.hr-list-error {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  .el-alert { flex: 1; }
  .el-button { flex: 0 0 auto; }
}

.hr-employee-list {
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.hr-employee-row {
  position: relative;
  padding: 16px 17px 14px;
  overflow: hidden;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  background: #fff;
  box-shadow: 0 4px 16px rgba(23, 33, 29, 0.04);
  cursor: pointer;
  transition: border-color var(--motion-duration-fast) var(--motion-ease-standard), box-shadow var(--motion-duration-fast) var(--motion-ease-standard);

  &::before {
    width: 3px;
    position: absolute;
    top: 16px;
    bottom: 16px;
    left: 0;
    border-radius: 0 3px 3px 0;
    background: #e3a93b;
    content: '';
  }

  &.is-complete::before { background: #35b584; }
  &.is-risk::before { background: #df6d78; }
  &.is-contract-selected {
    border-color: #8fb3a4;
    box-shadow: 0 0 0 2px rgba(11, 107, 83, 0.12), 0 10px 24px rgba(23, 33, 29, 0.06);
  }

  &:hover {
    border-color: #9fbfb2;
    box-shadow: 0 10px 24px rgba(23, 33, 29, 0.08);
  }
}

.hr-row-main,
.hr-row-meta,
.hr-optional-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.hr-row-main { justify-content: space-between; }

.hr-person-block {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hr-contract-selector {
  flex: 0 0 auto;
  margin-right: -2px;

  ::v-deep .el-checkbox__inner { width: 18px; height: 18px; border-radius: 5px; }
  ::v-deep .el-checkbox__inner::after { top: 2px; left: 6px; height: 8px; }
}

.hr-person-avatar {
  width: 43px;
  height: 43px;
  flex: 0 0 43px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #c6ddd3;
  border-radius: 13px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--hr-accent);
  font-size: 17px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 #fff;
}

.hr-person-identity { min-width: 0; display: flex; flex-direction: column; gap: 5px; }

.hr-person {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 9px;

  strong { color: #25344d; font-size: 16px; }
}

.hr-person__phone {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #7b879a;
  font-size: 11px;
}

.hr-person-ids {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    padding: 3px 7px;
    border-radius: 6px;
    background: #f3f5f8;
    color: #78859a;
    font-size: 10px;
  }
}

.hr-row-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;

  ::v-deep .el-tag { border-radius: 7px; font-weight: 500; }
}

.hr-row-sub {
  display: grid;
  grid-template-columns: minmax(170px, 0.9fr) minmax(190px, 0.95fr) minmax(260px, 1.4fr);
  gap: 8px;
  margin-top: 13px;
}

.hr-row-context {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8f9fb;

  > i { flex: 0 0 auto; color: #7180d8; font-size: 15px; }
  > span { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  small { color: #9aa4b4; font-size: 9px; }
  strong { overflow: hidden; color: #5c6980; font-size: 11px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
}

.hr-row-context--remark {
  > i { color: #6e9da5; }
}

.hr-row-meta {
  display: grid;
  grid-template-columns: minmax(300px, 420px) auto minmax(350px, 1fr);
  gap: 14px;
  margin-top: 13px;
  padding-top: 12px;
  border-top: 1px solid #edf0f4;
  color: #8a95a6;
}

.hr-progress {
  display: grid;
  grid-template-columns: 126px minmax(130px, 1fr);
  gap: 5px 10px;
  align-items: center;

  ::v-deep .el-progress-bar__outer { background: #e9edf4; }
  ::v-deep .el-progress-bar__inner { background: var(--hr-accent); }
  small { grid-column: 1 / -1; color: #98a2b2; font-size: 9px; }
}

.hr-progress__label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;

  > span { color: #728096; font-size: 10px; }
  strong { color: var(--hr-accent); font-size: 13px; }
}

.hr-account-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 9px;
  border-radius: 9px;
  background: #edf8f4;
  color: #278a69;
  font-size: 10px;
  font-weight: 600;

  &.is-missing { background: #fff0f2; color: #c35260; }
}

.hr-row-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;

  ::v-deep .el-button {
    height: 30px;
    margin-left: 0;
    padding: 0 9px;
    border-radius: 8px;
    color: #63718a;
    font-size: 10px;
    font-weight: 500;
  }

  ::v-deep .el-button:hover { background: var(--erp-primary-soft, #e7f2ed); color: var(--hr-accent); }

  ::v-deep .hr-row-actions__detail {
    background: var(--erp-primary-soft, #e7f2ed);
    color: var(--hr-accent);
  }
}

.hr-optional-fields {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e5e9f0;
  color: #69768b;
  font-size: 11px;
}

.hr-empty {
  padding: 72px 0;
  text-align: center;
  color: #8d98aa;
}

::v-deep .pagination-container {
  margin-top: 16px;
  padding: 16px 0 0 !important;
  border-top: 1px solid #edf0f4;
  background: transparent;
}

::v-deep .pagination-container .el-pagination { color: #788499; }
::v-deep .pagination-container .el-pager li,
::v-deep .pagination-container .btn-prev,
::v-deep .pagination-container .btn-next {
  min-width: 32px;
  height: 32px;
  border-radius: 8px;
  line-height: 32px;
}

::v-deep .pagination-container .el-pager li.active {
  background: var(--hr-accent);
  box-shadow: 0 6px 14px rgba(11, 107, 83, 0.2);
}

@media (max-width: 1280px) {
  .hr-page { padding-right: 16px; padding-left: 16px; }
  .hr-task-card { gap: 9px; padding-right: 10px; padding-left: 10px; }
  .hr-task-card__icon { width: 36px; height: 36px; flex-basis: 36px; }
  .hr-row-meta { grid-template-columns: minmax(280px, 370px) auto 1fr; gap: 10px; }
  .hr-row-actions ::v-deep .el-button { padding-right: 6px; padding-left: 6px; }
}

@media (max-width: 1100px) {
  .hr-row-sub { grid-template-columns: 1fr 1fr; }
  .hr-row-context--remark { grid-column: 1 / -1; }
  .hr-row-meta { grid-template-columns: 1fr auto; }
  .hr-row-actions { grid-column: 1 / -1; justify-content: flex-start; }
}

@media (max-width: 960px) {
  .hr-page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .hr-task-strip {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .hr-search-line {
    grid-template-columns: 1fr 1fr;
  }

  .hr-keyword-input {
    grid-column: 1 / -1;
  }

  .hr-progress {
    grid-template-columns: 1fr;

    small { grid-column: 1; }
  }

  .hr-row-sub,
  .hr-row-meta { grid-template-columns: 1fr; }
  .hr-row-context--remark,
  .hr-row-actions { grid-column: 1; }
  .hr-row-actions { justify-content: flex-start; flex-wrap: wrap; }
}
</style>
