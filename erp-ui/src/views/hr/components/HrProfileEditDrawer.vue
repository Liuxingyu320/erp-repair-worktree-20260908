<template>
  <el-drawer
    ref="profileDrawer"
    :title="drawerTitle"
    :visible.sync="drawerVisible"
    size="860px"
    append-to-body
    :wrapper-closable="false"
    @opened="syncDrawerAccessibility"
  >
    <div v-if="form" class="hr-profile-edit">
      <div class="hr-edit-summary">
        <div>
          <h3>{{ fieldValue("employeeName") || detailName }}</h3>
          <p>{{ fieldValue("employeeNo") || "-" }} · {{ maskedSensitiveValues.phoneNumber || "-" }} · {{ fieldValue("employeeStatus") || "-" }}</p>
        </div>
        <el-tag size="small" type="warning">编辑中</el-tag>
      </div>

      <el-alert
        class="hr-edit-alert"
        title="敏感信息留空表示保持原值；只有明确修改的敏感字段才会提交。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert v-if="optionsError" class="hr-edit-alert" :title="optionsError" type="error" :closable="false" show-icon />
      <el-alert v-if="derivedPreviewError" class="hr-edit-alert" :title="derivedPreviewError" type="warning" :closable="false" show-icon />
      <el-alert v-if="validationMessage" class="hr-edit-alert" :title="validationMessage" type="warning" :closable="false" show-icon />

      <div class="hr-missing-mode-bar">
        <el-switch v-model="missingOnly" active-text="仅看缺失项" :disabled="!actionableMissingKeys.length" />
        <span v-if="actionableMissingKeys.length">可直接补充 {{ actionableMissingKeys.length }} 项</span>
      </div>
      <div v-if="readOnlyMissingGuidance.length" class="hr-missing-guidance">
        <strong>需要通过其他入口处理</strong>
        <p v-for="item in readOnlyMissingGuidance" :key="`${item.type}-${item.key}`">
          <span>{{ item.label }}</span>：{{ item.source }}
        </p>
      </div>

      <el-form ref="editForm" :model="form" label-width="118px" size="small">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane v-for="group in visibleDetailGroups" :key="group.title" :label="tabLabel(group)" :name="group.title">
            <div class="hr-edit-grid">
              <el-form-item
                v-for="field in visibleGroupFields(group)"
                :key="field.key"
                :label="field.label"
                :required="isRequiredField(field.key)"
                :error="fieldErrors[field.key]"
              >
                <el-input v-if="isReadOnlyField(field)" :value="displayValue(field.key)" disabled />

                <el-select
                  v-else-if="field.key === 'deptId'"
                  :value="form.deptId"
                  placeholder="请选择组织"
                  clearable
                  filterable
                  :disabled="optionsLoading || Boolean(optionsError)"
                  @input="setRelationValue('deptId', $event)"
                >
                  <el-option v-for="item in formOptions.departments" :key="item.deptId" :label="item.deptName" :value="item.deptId" />
                </el-select>

                <el-select
                  v-else-if="field.key === 'postIds'"
                  :value="form.postIds"
                  placeholder="请选择岗位"
                  multiple
                  clearable
                  filterable
                  :disabled="optionsLoading || Boolean(optionsError)"
                  @input="setRelationValue('postIds', $event)"
                >
                  <el-option v-for="item in formOptions.posts" :key="item.postId" :label="item.postName" :value="item.postId" />
                </el-select>

                <el-select
                  v-else-if="field.key === 'directSupervisorUserId'"
                  :value="form.directSupervisorUserId"
                  placeholder="请选择直属主管"
                  clearable
                  filterable
                  :disabled="optionsLoading || Boolean(optionsError)"
                  @input="setRelationValue('directSupervisorUserId', $event)"
                >
                  <el-option v-for="item in formOptions.supervisors" :key="item.userId" :label="item.employeeName" :value="item.userId" />
                </el-select>

                <div v-else-if="isSensitiveField(field.key)" class="hr-sensitive-edit">
                  <span>当前值：{{ maskedSensitiveValues[field.key] || "-" }}</span>
                  <el-input
                    :value="sensitiveEditValue(field.key)"
                    :type="longTextFields.includes(field.key) ? 'textarea' : 'text'"
                    :rows="2"
                    placeholder="输入新值；留空且未修改则保持原值"
                    clearable
                    @input="setSensitiveFieldValue(field.key, $event)"
                  />
                  <el-button v-if="dirtySensitiveFields[field.key]" type="text" size="mini" @click="resetSensitiveField(field.key)">撤销本字段修改</el-button>
                </div>

                <el-date-picker
                  v-else-if="isDateField(field.key)"
                  :value="fieldValue(field.key)"
                  type="date"
                  value-format="yyyy-MM-dd"
                  placeholder="选择日期"
                  clearable
                  @input="setFieldValue(field.key, $event)"
                />
                <el-input-number
                  v-else-if="field.key === 'renewalCount'"
                  :value="numberValue(field.key)"
                  :min="0"
                  controls-position="right"
                  @input="setFieldValue(field.key, $event)"
                />
                <el-select
                  v-else-if="isOptionField(field.key) || fieldOptions(field.key).length"
                  :value="fieldValue(field.key)"
                  placeholder="请选择"
                  clearable
                  filterable
                  :disabled="optionsLoading || Boolean(optionsError)"
                  @input="setFieldValue(field.key, $event)"
                >
                  <el-option v-for="item in fieldOptions(field.key)" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-input
                  v-else
                  :value="fieldValue(field.key)"
                  :type="longTextFields.includes(field.key) ? 'textarea' : 'text'"
                  :rows="2"
                  clearable
                  @input="setFieldValue(field.key, $event)"
                />
              </el-form-item>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <div class="hr-edit-footer">
        <el-button size="small" @click="drawerVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-check" :loading="saving" @click="submitForm">保存档案</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { getHrEmployeeFormOptions, previewHrEmployeeDerived } from "@/api/hr/employee"
import {
  DETAIL_GROUPS,
  EDIT_REQUIRED_FIELDS,
  READ_ONLY_DERIVED_FIELDS,
  SENSITIVE_FIELDS,
  profileFieldLabel,
  resolveMissingProfileFields
} from "./hrFieldConfig"
import {
  CONTRACT_TERM_OPTIONS,
  CONTRACT_TYPE_OPTIONS,
  SOCIAL_TYPE_OPTIONS,
  signingOptionsForKey
} from "./signingProfileOptions"

const RELATION_FIELDS = ["deptId", "postIds", "directSupervisorUserId"]
const RECORD_FIELDS = [
  "accountCreateBy", "accountCreateTime", "accountUpdateBy", "accountUpdateTime",
  "profileCreateBy", "profileCreateTime", "profileUpdateBy", "profileUpdateTime"
]
const BACKEND_READ_ONLY_FIELDS = ["employeeStatus", ...RECORD_FIELDS]
const DERIVED_PREVIEW_INPUT_FIELDS = ["workStartDate", "entryDate", "leaveDate"]
const OPTION_FIELD_KEYS = [
  "sex", "employeeStatus", "employeeCategory", "idType", "bloodType", "maritalStatus",
  "foreignNationalFlag", "contractType", "contractTerm", "socialType", "householdType", "attendanceMethod", "workCityLevel"
]
const MASKED_VALUE_TOKENS = ["****", "••••", "●●●●", "＊＊"]
const MASKED_VALUE_PATTERN = /[\*＊•●○◯◎◉◌◍◦∙]/
const DETAIL_GROUP_TITLES = ["基础信息", "组织岗位", "身份户籍", "教育信息", "用工合同", "社保公积金", "联系人与银行", "数据记录"]

export default {
  name: "HrProfileEditDrawer",
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    detail: {
      type: Object,
      default: null
    },
    saving: {
      type: Boolean,
      default: false
    },
    missingFields: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      activeTab: DETAIL_GROUP_TITLES[0],
      missingOnly: false,
      detailGroups: DETAIL_GROUPS,
      form: null,
      initialForm: null,
      maskedSensitiveValues: {},
      sensitiveValues: {},
      dirtySensitiveFields: {},
      validationMessage: "",
      fieldErrors: {},
      optionsLoading: false,
      optionsError: "",
      derivedPreviewError: "",
      derivedPreviewTimer: null,
      derivedPreviewSequence: 0,
      longTextFields: ["registeredResidence", "currentAddress", "remark"],
      formOptions: {
        enumOptions: {},
        dictionaries: {},
        departments: [],
        posts: [],
        supervisors: []
      }
    }
  },
  computed: {
    drawerVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit("update:visible", value)
      }
    },
    drawerTitle() {
      return this.detail ? `编辑档案：${this.detail.employeeName || this.detail.nickName || this.detail.userName || "-"}` : "编辑员工档案"
    },
    detailName() {
      return this.detail ? this.detail.employeeName || this.detail.nickName || this.detail.userName || "-" : "-"
    },
    confirmedEmployee() {
      return Boolean(this.detail && this.detail.userId)
    },
    missingResolution() {
      return resolveMissingProfileFields(this.missingFields)
    },
    actionableMissingKeys() {
      return this.missingResolution.actionable.filter(key => {
        const exists = DETAIL_GROUPS.some(group => group.fields.some(field => field[0] === key))
        return exists && !this.isReadOnlyField({ key })
      })
    },
    readOnlyMissingGuidance() {
      const described = [...this.missingResolution.workflow, ...this.missingResolution.derived]
        .map(item => ({ ...item, label: profileFieldLabel(item.key), type: "described" }))
      const unknown = this.missingResolution.unknown.map(key => ({
        key,
        label: `待确认字段：${key}`,
        source: "请联系系统管理员确认历史字段配置",
        type: "unknown"
      }))
      return [...described, ...unknown]
    },
    visibleDetailGroups() {
      return this.missingOnly ? this.detailGroups.filter(group => this.groupMissingCount(group) > 0) : this.detailGroups
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.resetForm()
        this.loadFormOptions()
      } else {
        this.resetDerivedPreviewLifecycle()
      }
    },
    detail() {
      if (this.visible) this.resetForm()
    },
    missingFields() {
      if (this.visible) this.applyMissingMode()
    }
  },
  beforeDestroy() {
    this.resetDerivedPreviewLifecycle()
  },
  methods: {
    syncDrawerAccessibility() {
      const component = this.$refs.profileDrawer
      const dialog = component && component.$el && component.$el.querySelector(".el-drawer[role='dialog']")
      if (!dialog) return
      dialog.removeAttribute("aria-labelledby")
    },
    groupFields(group) {
      return group.fields.map(field => ({ key: field[0], label: field[1] }))
    },
    resetForm() {
      const detail = this.detail || {}
      const values = {}
      const masked = {}
      DETAIL_GROUPS.forEach(group => {
        group.fields.forEach(([key]) => {
          if (RELATION_FIELDS.includes(key)) return
          if (SENSITIVE_FIELDS.includes(key)) masked[key] = this.maskedDetailValue(detail, key)
          else values[key] = this.detailValue(detail, key)
        })
      })
      const postIds = Array.isArray(detail.postIds) ? detail.postIds.slice() : []
      this.form = {
        userId: detail.userId,
        values,
        deptId: detail.deptId === undefined ? null : detail.deptId,
        postIds,
        directSupervisorUserId: this.detailValue(detail, "directSupervisorUserId")
      }
      this.initialForm = this.copyForm(this.form)
      this.maskedSensitiveValues = masked
      this.sensitiveValues = {}
      this.dirtySensitiveFields = {}
      this.fieldErrors = {}
      this.validationMessage = ""
      this.derivedPreviewError = ""
      this.applyMissingMode()
    },
    applyMissingMode() {
      this.missingOnly = this.actionableMissingKeys.length > 0
      const first = DETAIL_GROUPS.find(group => this.groupMissingCount(group) > 0)
      this.activeTab = first ? first.title : DETAIL_GROUP_TITLES[0]
    },
    groupMissingCount(group) {
      const keys = new Set(this.actionableMissingKeys)
      return group && Array.isArray(group.fields) ? group.fields.filter(field => keys.has(field[0])).length : 0
    },
    visibleGroupFields(group) {
      const fields = this.groupFields(group)
      if (!this.missingOnly) return fields
      const keys = new Set(this.actionableMissingKeys)
      return fields.filter(field => keys.has(field.key))
    },
    tabLabel(group) {
      const count = this.groupMissingCount(group)
      return count > 0 ? `${group.title}（${count}）` : group.title
    },
    detailValue(detail, key) {
      const fields = detail.fields || {}
      const profile = detail.profile || {}
      if (fields[key] !== undefined && fields[key] !== null) return fields[key]
      if (profile[key] !== undefined && profile[key] !== null) return profile[key]
      if (key === "employeeName") return detail.employeeName !== undefined ? detail.employeeName : detail.nickName
      if (key === "positionNames" && fields.positionName !== undefined) return fields.positionName
      return detail[key]
    },
    maskedDetailValue(detail, key) {
      const maskedKey = `${key}Masked`
      if (detail[maskedKey] !== undefined && detail[maskedKey] !== null) return detail[maskedKey]
      if (key === "phoneNumber" && detail.phonenumber !== undefined) return detail.phonenumber
      return this.detailValue(detail, key)
    },
    copyForm(form) {
      return {
        userId: form.userId,
        values: { ...form.values },
        deptId: form.deptId,
        postIds: form.postIds.slice(),
        directSupervisorUserId: form.directSupervisorUserId
      }
    },
    loadFormOptions() {
      this.optionsLoading = true
      this.optionsError = ""
      getHrEmployeeFormOptions().then(response => {
        const options = response.data || {}
        this.formOptions = {
          enumOptions: options.enumOptions || {},
          dictionaries: options.dictionaries || {},
          departments: Array.isArray(options.departments) ? options.departments : [],
          posts: Array.isArray(options.posts) ? options.posts : [],
          supervisors: Array.isArray(options.supervisors) ? options.supervisors : []
        }
      }).catch(() => {
        this.formOptions = { enumOptions: {}, dictionaries: {}, departments: [], posts: [], supervisors: [] }
        this.optionsError = "表单选项加载失败，组织、岗位和枚举字段已安全禁用，请重试打开编辑。"
      }).finally(() => {
        this.optionsLoading = false
      })
    },
    fieldOptions(key) {
      const signingOptions = typeof signingOptionsForKey === "function" ? signingOptionsForKey(key) : null
      if (signingOptions) return signingOptions
      const enumValues = this.formOptions.enumOptions[key]
      const dictionaryValues = this.formOptions.dictionaries[key]
      const values = Array.isArray(enumValues) && enumValues.length ? enumValues : dictionaryValues
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
    isSensitiveField(key) {
      return SENSITIVE_FIELDS.includes(key)
    },
    isOptionField(key) {
      return OPTION_FIELD_KEYS.includes(key)
    },
    isReadOnlyField(field) {
      if (field.key === "employeeNo") return this.confirmedEmployee
      return READ_ONLY_DERIVED_FIELDS.includes(field.key) || BACKEND_READ_ONLY_FIELDS.includes(field.key)
    },
    isDateField(key) {
      return key.endsWith("Date")
    },
    isRequiredField(key) {
      return EDIT_REQUIRED_FIELDS.some(field => field.key === key)
    },
    fieldValue(key) {
      if (!this.form) return ""
      if (RELATION_FIELDS.includes(key)) return this.form[key]
      const value = this.form.values[key]
      return value === undefined || value === null ? "" : value
    },
    numberValue(key) {
      const value = this.fieldValue(key)
      return value === "" ? undefined : Number(value)
    },
    displayValue(key) {
      const value = this.fieldValue(key)
      return value === "" || (Array.isArray(value) && !value.length) ? "-" : value
    },
    setFieldValue(key, value) {
      const field = { key }
      if (this.isReadOnlyField(field) || RELATION_FIELDS.includes(key) || this.isSensitiveField(key)) return
      this.$set(this.form.values, key, value)
      this.$delete(this.fieldErrors, key)
      if (DERIVED_PREVIEW_INPUT_FIELDS.includes(key)) this.scheduleDerivedPreview()
    },
    setRelationValue(key, value) {
      const normalized = key === "postIds" ? (Array.isArray(value) ? value : []) : value
      this.$set(this.form, key, normalized)
      this.$delete(this.fieldErrors, key)
      if (key === "deptId" || key === "postIds") this.scheduleDerivedPreview()
    },
    sensitiveEditValue(key) {
      return this.sensitiveValues[key] === undefined ? "" : this.sensitiveValues[key]
    },
    setSensitiveFieldValue(key, value) {
      this.$set(this.sensitiveValues, key, value)
      this.$set(this.dirtySensitiveFields, key, true)
      this.$delete(this.fieldErrors, key)
    },
    resetSensitiveField(key) {
      this.$delete(this.sensitiveValues, key)
      this.$delete(this.dirtySensitiveFields, key)
      this.$delete(this.fieldErrors, key)
    },
    scheduleDerivedPreview() {
      clearTimeout(this.derivedPreviewTimer)
      this.derivedPreviewTimer = setTimeout(() => this.loadDerivedPreview(), 250)
    },
    loadDerivedPreview() {
      if (!this.form) return
      const requestSequence = ++this.derivedPreviewSequence
      const payload = {
        deptId: this.form.deptId,
        postIds: this.form.postIds.slice()
      }
      DERIVED_PREVIEW_INPUT_FIELDS.forEach(key => {
        if (this.form.values[key] !== undefined) payload[key] = this.form.values[key]
      })
      if (!payload.deptId) return
      this.derivedPreviewError = ""
      previewHrEmployeeDerived(payload).then(response => {
        if (requestSequence !== this.derivedPreviewSequence || !this.visible || !this.form) return
        const result = response.data || {}
        const derived = { ...(result.fields || {}), ...(result.profile || {}) }
        if (derived.positionName !== undefined && derived.positionNames === undefined) derived.positionNames = derived.positionName
        READ_ONLY_DERIVED_FIELDS.forEach(key => {
          if (derived[key] !== undefined) this.$set(this.form.values, key, derived[key])
        })
      }).catch(() => {
        if (requestSequence === this.derivedPreviewSequence) {
          this.derivedPreviewError = "组织岗位派生预览失败，派生字段未改动；请检查选项后重试。"
        }
      })
    },
    resetDerivedPreviewLifecycle() {
      clearTimeout(this.derivedPreviewTimer)
      this.derivedPreviewTimer = null
      this.derivedPreviewSequence += 1
    },
    sameValue(left, right) {
      if (Array.isArray(left) || Array.isArray(right)) {
        return JSON.stringify(left || []) === JSON.stringify(right || [])
      }
      return left === right
    },
    isMaskedPlaceholder(value) {
      if (typeof value !== "string") return false
      return MASKED_VALUE_TOKENS.some(token => value.includes(token)) || MASKED_VALUE_PATTERN.test(value)
    },
    buildEmployeeUpdatePayload() {
      const patch = {}
      const errors = {}
      Object.keys(this.form.values).forEach(key => {
        const field = { key }
        if (key === "employeeNo" || this.isReadOnlyField(field) || this.isSensitiveField(key) || RELATION_FIELDS.includes(key)) return
        if (!this.sameValue(this.form.values[key], this.initialForm.values[key])) patch[key] = this.form.values[key]
      })
      SENSITIVE_FIELDS.forEach(key => {
        if (!this.dirtySensitiveFields[key]) return
        const value = this.sensitiveValues[key]
        if (this.isMaskedPlaceholder(value)) {
          errors[key] = "不能提交脱敏占位符，请输入完整的新值或撤销本字段修改。"
          return
        }
        patch[key] = value
      })
      RELATION_FIELDS.forEach(key => {
        if (!this.sameValue(this.form[key], this.initialForm[key])) patch[key] = this.form[key]
      })
      this.fieldErrors = errors
      return Object.keys(errors).length ? null : patch
    },
    validateRequiredFields() {
      const missing = EDIT_REQUIRED_FIELDS.filter(field => {
        if (this.isSensitiveField(field.key)) {
          if (this.dirtySensitiveFields[field.key]) return !this.sensitiveValues[field.key]
          return !this.maskedSensitiveValues[field.key]
        }
        return !this.fieldValue(field.key) && this.fieldValue(field.key) !== 0 && this.fieldValue(field.key) !== false
      }).map(field => field.label)
      if (missing.length) {
        this.validationMessage = `必填项未完整：${missing.join("、")}`
        this.$message.warning(this.validationMessage)
        return false
      }
      this.validationMessage = ""
      return true
    },
    applyServerErrors(error) {
      const response = error && error.response && error.response.data
      const body = response || (error && error.data) || {}
      const errors = body.fieldErrors
      if (errors && typeof errors === "object" && !Array.isArray(errors)) {
        this.fieldErrors = { ...errors }
      }
      this.validationMessage = body.msg || body.message || "保存失败，服务端未接受本次修改。"
    },
    submitForm() {
      if (!this.validateRequiredFields()) return
      const patch = this.buildEmployeeUpdatePayload()
      if (!patch) {
        this.validationMessage = "请修正字段错误后再保存。"
        return
      }
      if (!Object.keys(patch).length) {
        this.$message.info("没有需要保存的修改")
        return
      }
      this.$emit("save", { userId: this.form.userId, ...patch })
    }
  }
}
</script>

<style lang="scss" scoped>
.hr-profile-edit {
  padding: 0 20px 24px;
}

.hr-edit-summary {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;

  h3 {
    margin: 0 0 6px;
    font-size: 20px;
    color: #303133;
  }

  p {
    margin: 0;
    color: #606266;
  }
}

.hr-missing-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0;
  color: #606266;
  font-size: 12px;
}

.hr-missing-guidance {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f4f6f9;
  color: #606266;
  font-size: 12px;

  strong { display: block; margin-bottom: 5px; color: #303133; }
  p { margin: 3px 0; }
  span { font-weight: 600; }
}

.hr-edit-alert {
  margin-bottom: 12px;
}

.hr-edit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  gap: 2px 12px;
}

.hr-sensitive-edit {
  display: flex;
  flex-direction: column;
  gap: 6px;

  > span {
    color: #909399;
    font-size: 12px;
  }

  .el-button {
    align-self: flex-start;
  }
}

.hr-edit-footer {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 0 0;
  margin-top: 12px;
  background: #fff;
}

::v-deep .el-date-editor.el-input,
::v-deep .el-select,
::v-deep .el-input-number {
  width: 100%;
}

@media (max-width: 768px) {
  .hr-edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
