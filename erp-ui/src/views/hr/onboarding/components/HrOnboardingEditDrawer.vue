<template>
  <el-drawer
    title="完善入职资料"
    size="760px"
    :visible="visible"
    append-to-body
    :wrapper-closable="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="guardBeforeClose"
    custom-class="hr-onboarding-edit-drawer"
    @close="close"
  >
    <div class="edit-drawer-layout">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step v-for="step in steps" :key="step.title" :title="step.title" />
      </el-steps>

      <el-alert v-if="submitMessage" :title="submitMessage" type="error" :closable="false" show-icon />

      <div v-if="activeStep === 1" class="derived-preview">
        <span v-for="field in derivedFields" :key="field.key">
          <small>{{ field.label }}</small>
          <strong>{{ detailValue(field.key) || "由组织岗位自动计算" }}</strong>
        </span>
      </div>

      <el-form ref="form" :model="model" label-width="142px" class="edit-step-form">
        <el-form-item
          v-for="field in currentFields"
          :key="field.key"
          :ref="`field-${field.key}`"
          :label="field.label"
          :prop="field.key"
          :error="fieldErrors[field.key]"
        >
          <template v-if="field.control === 'sensitive'">
            <div v-if="!dirtySensitiveFields[field.key]" class="masked-field">
              <span>{{ maskedValue(field.key) || "未填写" }}</span>
              <el-button type="text" @click="markSensitiveDirty(field.key)">修改</el-button>
            </div>
            <div v-else class="sensitive-editor">
              <el-input
                v-model="sensitiveValues[field.key]"
                :type="field.key.includes('Address') || field.key === 'registeredResidence' ? 'textarea' : 'text'"
                placeholder="请输入完整的新值"
                @input="clearFieldError(field.key)"
              />
              <el-button type="text" @click="revertSensitive(field.key)">撤销修改</el-button>
            </div>
          </template>
          <el-date-picker
            v-else-if="field.control === 'date'"
            v-model="model[field.key]"
            type="date"
            value-format="yyyy-MM-dd"
            clearable
            style="width: 100%"
            @change="clearFieldError(field.key)"
          />
          <el-select
            v-else-if="field.control === 'select'"
            v-model="model[field.key]"
            filterable
            clearable
            style="width: 100%"
            @change="clearFieldError(field.key)"
          >
            <el-option
              v-for="option in fieldOptions(field)"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="model[field.key]"
            :type="field.control === 'textarea' ? 'textarea' : 'text'"
            :rows="field.control === 'textarea' ? 3 : undefined"
            @input="clearFieldError(field.key)"
          />
        </el-form-item>
      </el-form>

      <footer class="edit-actions">
        <el-button :disabled="activeStep === 0" @click="activeStep -= 1">上一步</el-button>
        <el-button v-if="activeStep < steps.length - 1" @click="activeStep += 1">下一步</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存草稿</el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<script>
import { updateHrOnboarding } from "@/api/hr/onboarding"
import {
  DERIVED_ONBOARDING_FIELDS,
  EDIT_STEPS,
  SENSITIVE_ONBOARDING_FIELDS,
  buildOnboardingUpdatePayload,
  firstErrorLocation,
  isOnboardingVersionConflict,
  onboardingErrorBody
} from "../onboardingFieldConfig"

const emptySensitiveState = () => Object.keys(SENSITIVE_ONBOARDING_FIELDS).reduce((result, key) => {
  result[key] = ""
  return result
}, {})

const emptyDirtyState = () => Object.keys(SENSITIVE_ONBOARDING_FIELDS).reduce((result, key) => {
  result[key] = false
  return result
}, {})

export default {
  name: "HrOnboardingEditDrawer",
  props: {
    visible: { type: Boolean, default: false },
    detail: { type: Object, default: () => ({}) },
    options: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      steps: EDIT_STEPS,
      derivedFields: DERIVED_ONBOARDING_FIELDS,
      activeStep: 0,
      model: {},
      dirtySensitiveFields: emptyDirtyState(),
      sensitiveValues: emptySensitiveState(),
      fieldErrors: {},
      submitMessage: "",
      pendingFocusField: "",
      submitting: false,
      dialogGeneration: 0,
      submitRequestSequence: 0,
      activeSubmitSequence: 0,
      pendingDetail: null
    }
  },
  computed: {
    currentFields() {
      return this.steps[this.activeStep] ? this.steps[this.activeStep].fields : []
    }
  },
  watch: {
    visible(value) {
      if (value) this.initialize(this.detail)
      else this.invalidateLifecycle()
    },
    detail(value, previous) {
      if (this.visible && (!previous || value.onboardingId !== previous.onboardingId || value.version !== previous.version)) {
        this.initialize(value)
      }
    }
  },
  beforeDestroy() {
    this.invalidateLifecycle()
  },
  methods: {
    initialize(detail) {
      const source = detail || {}
      if (this.submitting) {
        this.pendingDetail = source
        this.invalidateLifecycle()
        return
      }
      this.dialogGeneration += 1
      this.pendingDetail = null
      this.model = {}
      this.steps.forEach(step => step.fields.forEach(field => {
        if (!Object.prototype.hasOwnProperty.call(SENSITIVE_ONBOARDING_FIELDS, field.key)) {
          this.$set ? this.$set(this.model, field.key, source[field.key]) : (this.model[field.key] = source[field.key])
        }
      }))
      this.dirtySensitiveFields = emptyDirtyState()
      this.sensitiveValues = emptySensitiveState()
      this.fieldErrors = {}
      this.submitMessage = ""
      this.pendingFocusField = ""
      this.activeStep = 0
    },
    invalidateLifecycle() {
      this.dialogGeneration += 1
    },
    isActiveWrite(generation, requestSequence, onboardingId, version) {
      return this.visible && generation === this.dialogGeneration && requestSequence === this.activeSubmitSequence &&
        this.detail && this.detail.onboardingId === onboardingId && this.detail.version === version
    },
    detailValue(key) {
      return this.detail ? this.detail[key] : undefined
    },
    maskedValue(key) {
      const maskKey = SENSITIVE_ONBOARDING_FIELDS[key]
      return this.detail && maskKey ? this.detail[maskKey] : ""
    },
    fieldOptions(field) {
      const values = this.options && field.optionSource ? this.options[field.optionSource] : []
      return Array.isArray(values) ? values : []
    },
    markSensitiveDirty(key) {
      if (this.$set) this.$set(this.dirtySensitiveFields, key, true)
      else this.dirtySensitiveFields[key] = true
      this.sensitiveValues[key] = ""
      this.clearFieldError(key)
    },
    revertSensitive(key) {
      if (this.$set) this.$set(this.dirtySensitiveFields, key, false)
      else this.dirtySensitiveFields[key] = false
      this.sensitiveValues[key] = ""
      this.clearFieldError(key)
    },
    clearFieldError(key) {
      if (this.fieldErrors[key]) {
        if (this.$delete) this.$delete(this.fieldErrors, key)
        else delete this.fieldErrors[key]
      }
    },
    buildUpdatePayload() {
      const built = buildOnboardingUpdatePayload(
        this.detail || {}, this.model, this.dirtySensitiveFields, this.sensitiveValues
      )
      this.fieldErrors = built.errors
      if (!built.payload) this.focusFirstError()
      return built.payload
    },
    focusFirstError() {
      const location = firstErrorLocation(this.fieldErrors)
      if (!location) return
      this.activeStep = location.stepIndex
      this.pendingFocusField = location.fieldKey
      this.$nextTick(() => {
        const ref = this.$refs[`field-${location.fieldKey}`]
        const formItem = Array.isArray(ref) ? ref[0] : ref
        if (formItem && formItem.$el && formItem.$el.scrollIntoView) {
          formItem.$el.scrollIntoView({ behavior: "smooth", block: "center" })
        }
        const input = formItem && formItem.$el && formItem.$el.querySelector("input,textarea")
        if (input && input.focus) input.focus()
      })
    },
    applyServerErrors(error) {
      const body = onboardingErrorBody(error)
      this.fieldErrors = body.fieldErrors && typeof body.fieldErrors === "object" ? { ...body.fieldErrors } : {}
      this.submitMessage = body.msg || body.message || "保存失败，请修正服务端标记的字段。"
      this.focusFirstError()
    },
    submit() {
      if (this.submitting) return Promise.resolve(null)
      const payload = this.buildUpdatePayload()
      if (!payload) return Promise.resolve(null)
      const onboardingId = this.detail.onboardingId
      const version = this.detail.version
      const generation = this.dialogGeneration
      const requestSequence = ++this.submitRequestSequence
      this.activeSubmitSequence = requestSequence
      this.submitting = true
      this.submitMessage = ""
      return updateHrOnboarding(onboardingId, payload)
        .then(response => {
          if (!this.isActiveWrite(generation, requestSequence, onboardingId, version)) return null
          const updated = response && response.data ? response.data : response
          this.$emit("saved", updated || {})
          this.$emit("update:visible", false)
          return updated
        })
        .catch(error => {
          if (!this.isActiveWrite(generation, requestSequence, onboardingId, version)) return null
          if (isOnboardingVersionConflict(error)) this.handleVersionConflict(onboardingId)
          else this.applyServerErrors(error)
          return null
        })
        .finally(() => {
          if (requestSequence !== this.activeSubmitSequence) return
          this.submitting = false
          this.activeSubmitSequence = 0
          if (this.pendingDetail && this.visible) this.initialize(this.pendingDetail)
        })
    },
    handleVersionConflict(onboardingId) {
      this.invalidateLifecycle()
      this.fieldErrors = {}
      this.submitMessage = ""
      this.$emit("version-conflict", { onboardingId })
      this.$emit("update:visible", false)
    },
    close() {
      if (this.submitting) return false
      this.invalidateLifecycle()
      this.$emit("update:visible", false)
      return true
    },
    guardBeforeClose(done) {
      if (this.submitting) return
      done()
    }
  }
}
</script>

<style lang="scss" scoped>
.edit-drawer-layout { height: 100%; display: flex; flex-direction: column; padding: 0 28px 24px; }
.el-steps { margin: 10px 0 22px; }
.el-alert { margin-bottom: 16px; }
.edit-step-form { flex: 1; min-height: 0; overflow-y: auto; padding-right: 16px; }
.masked-field,
.sensitive-editor { display: flex; align-items: center; gap: 12px; }
.masked-field > span { flex: 1; min-height: 36px; padding: 0 12px; border-radius: 4px; background: #f5f7fa; color: #64748b; line-height: 36px; }
.sensitive-editor .el-input,
.sensitive-editor ::v-deep .el-textarea { flex: 1; }
.derived-preview { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 16px; }
.derived-preview span { padding: 9px 10px; border-radius: 6px; background: #f8fafc; }
.derived-preview small,
.derived-preview strong { display: block; }
.derived-preview small { color: #94a3b8; }
.derived-preview strong { margin-top: 3px; color: #334155; font-size: 13px; }
.edit-actions { flex-shrink: 0; display: flex; justify-content: flex-end; padding-top: 16px; border-top: 1px solid #e2e8f0; }
</style>
