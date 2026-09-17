<template>
  <el-dialog
    title="新建入职"
    width="620px"
    append-to-body
    :visible="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="guardBeforeClose"
    @close="close"
  >
    <el-alert v-if="submitMessage" :title="submitMessage" type="error" :closable="false" show-icon />
    <el-form ref="form" :model="model" label-width="112px" class="onboarding-create-form">
      <el-form-item
        v-for="field in fields"
        :key="field.key"
        :label="field.label"
        :prop="field.key"
        :error="fieldErrors[field.key]"
      >
        <el-date-picker
          v-if="field.control === 'date'"
          v-model="model[field.key]"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择日期"
          style="width: 100%"
          @change="clearFieldError(field.key)"
        />
        <el-select
          v-else-if="field.control === 'select'"
          v-model="model[field.key]"
          filterable
          clearable
          placeholder="请选择"
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
          v-model.trim="model[field.key]"
          :maxlength="field.key === 'phoneNumber' ? 32 : 100"
          clearable
          @input="clearFieldError(field.key)"
        />
      </el-form-item>
    </el-form>
    <span slot="footer">
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit(false)">创建</el-button>
      <el-button type="success" :loading="submitting" @click="submit(true)">创建并继续下一位</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { createHrOnboarding } from "@/api/hr/onboarding"
import { QUICK_CREATE_FIELDS } from "../onboardingFieldConfig"

const createDefaultModel = () => ({
  employeeName: "",
  phoneNumber: "",
  expectedEntryDate: "",
  targetDeptId: null,
  targetPostId: null,
  employeeCategory: "",
  ownerUserId: null
})

export default {
  name: "HrOnboardingCreateDialog",
  props: {
    visible: { type: Boolean, default: false },
    options: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      fields: QUICK_CREATE_FIELDS,
      model: createDefaultModel(),
      fieldErrors: {},
      submitMessage: "",
      submitting: false,
      dialogGeneration: 0,
      submitRequestSequence: 0,
      activeSubmitSequence: 0,
      pendingReset: false
    }
  },
  watch: {
    visible(value) {
      if (value) {
        if (this.submitting) this.pendingReset = true
        else this.openLifecycle()
      } else {
        this.invalidateLifecycle()
      }
    }
  },
  beforeDestroy() {
    this.invalidateLifecycle()
  },
  methods: {
    openLifecycle() {
      this.dialogGeneration += 1
      this.pendingReset = false
      this.model = createDefaultModel()
      this.fieldErrors = {}
      this.submitMessage = ""
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    reset() {
      if (!this.submitting) this.openLifecycle()
    },
    invalidateLifecycle() {
      this.dialogGeneration += 1
    },
    isActiveWrite(generation, requestSequence) {
      return this.visible && generation === this.dialogGeneration && requestSequence === this.activeSubmitSequence
    },
    fieldOptions(field) {
      const values = this.options && field.optionSource ? this.options[field.optionSource] : []
      return Array.isArray(values) ? values : []
    },
    clearFieldError(key) {
      if (this.fieldErrors[key]) this.$delete(this.fieldErrors, key)
    },
    applyServerErrors(error) {
      const body = (error && error.response && error.response.data) || (error && error.data) || error || {}
      this.fieldErrors = body.fieldErrors && typeof body.fieldErrors === "object"
        ? { ...body.fieldErrors }
        : {}
      this.submitMessage = body.msg || body.message || "创建失败，请核对填写内容。"
    },
    prepareNextPerson() {
      const shared = {
        expectedEntryDate: this.model.expectedEntryDate,
        targetDeptId: this.model.targetDeptId,
        targetPostId: this.model.targetPostId,
        employeeCategory: this.model.employeeCategory,
        ownerUserId: this.model.ownerUserId
      }
      this.model = Object.assign(createDefaultModel(), shared)
      this.fieldErrors = {}
      this.submitMessage = ""
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    submit(continueNext) {
      if (this.submitting) return Promise.resolve(null)
      this.submitting = true
      const validationGeneration = this.dialogGeneration
      const run = () => {
        if (!this.visible || validationGeneration !== this.dialogGeneration) {
          this.submitting = false
          return Promise.resolve(null)
        }
        const generation = validationGeneration
        const requestSequence = ++this.submitRequestSequence
        this.activeSubmitSequence = requestSequence
        this.fieldErrors = {}
        this.submitMessage = ""
        const payload = { ...this.model }
        return createHrOnboarding(payload)
          .then(response => {
            if (!this.isActiveWrite(generation, requestSequence)) return null
            const created = response && response.data ? response.data : response
            this.$emit("created", created || {})
            if (continueNext) {
              this.prepareNextPerson()
              if (this.$modal && this.$modal.msgSuccess) this.$modal.msgSuccess("已创建，可继续填写下一位")
              return created
            }
            this.$emit("update:visible", false)
            return created
          })
          .catch(error => {
            if (this.isActiveWrite(generation, requestSequence)) this.applyServerErrors(error)
            return null
          })
          .finally(() => {
            if (requestSequence !== this.activeSubmitSequence) return
            this.submitting = false
            this.activeSubmitSequence = 0
            if (this.pendingReset && this.visible) this.openLifecycle()
          })
      }
      if (!this.$refs.form || typeof this.$refs.form.validate !== "function") return run()
      return new Promise(resolve => {
        this.$refs.form.validate(valid => {
          if (!valid || !this.visible || validationGeneration !== this.dialogGeneration) {
            this.submitting = false
            resolve(null)
            return
          }
          resolve(run())
        })
      })
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
.onboarding-create-form { padding: 8px 28px 0 8px; }
.el-alert { margin-bottom: 18px; }
</style>
