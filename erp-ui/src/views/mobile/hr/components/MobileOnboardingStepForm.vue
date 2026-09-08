<template>
  <section
    class="step-form"
    :style="keyboardStyle"
    :aria-labelledby="`mobile-onboarding-step-${currentStep.key}`"
    @focusin="handleFocusIn"
  >
    <header class="step-header">
      <p>第 {{ step + 1 }} 步，共 {{ steps.length }} 步</p>
      <h2 :id="`mobile-onboarding-step-${currentStep.key}`" ref="stepHeading" tabindex="-1">
        {{ currentStep.label }}
        <span v-if="currentMissingCount" class="missing-badge">缺 {{ currentMissingCount }} 项</span>
      </h2>
    </header>

    <nav class="step-progress" aria-label="入职资料步骤">
      <button
        v-for="(item, index) in steps"
        :key="item.key"
        type="button"
        :class="{ 'is-current': index === step, 'has-missing': missingCount(item.key) > 0 }"
        :aria-current="index === step ? 'step' : null"
        :aria-label="`${item.label}${missingCount(item.key) ? `，缺少 ${missingCount(item.key)} 项` : ''}`"
        :disabled="disabled"
        @click="$emit(index < step ? 'back' : 'next', index)"
      >
        <span class="step-index">{{ index + 1 }}</span>
        <span class="step-label">{{ item.label }}</span>
      </button>
    </nav>
    <div class="step-bar" aria-hidden="true">
      <i :style="{ width: (((step + 1) / steps.length) * 100) + '%' }" />
    </div>

    <div v-if="!currentFields.length" class="empty-step" role="status">
      创建入职单后可继续补充本组资料。
    </div>
    <div v-else class="fields">
      <div v-for="field in currentFields" :key="field.key" :ref="`field-${field.key}`" class="field-row">
        <label :id="labelId(field.key)" :for="fieldId(field.key)">{{ field.label }}</label>

        <template v-if="isProtectedSensitive(field)">
          <div class="masked-control">
            <span :aria-label="`${field.label}已脱敏`">{{ maskedValue(field.key) || "未填写" }}</span>
            <button
              :id="fieldId(field.key)"
              type="button"
              :disabled="disabled"
              :aria-invalid="errors[field.key] ? 'true' : 'false'"
              :aria-describedby="errors[field.key] ? errorId(field.key) : null"
              @click="markSensitiveDirty(field.key)"
            >修改</button>
          </div>
        </template>

        <mobile-onboarding-owner-picker
          v-else-if="field.control === 'owner-picker'"
          :value="fieldValue(field.key)"
          :value-label="personValueLabel(field)"
          :picker-label="field.label"
          :input-id="fieldId(field.key)"
          :labelled-by="labelId(field.key)"
          :target-dept-id="fieldValue('targetDeptId')"
          :disabled="disabled"
          :invalid="Boolean(errors[field.key])"
          :described-by="errors[field.key] ? errorId(field.key) : ''"
          @input="updateField(field.key, $event)"
          @select="updatePerson(field, $event)"
        />

        <select
          v-else-if="field.control === 'option-sheet'"
          :id="fieldId(field.key)"
          v-selected-value="fieldValue(field.key)"
          :aria-invalid="errors[field.key] ? 'true' : 'false'"
          :aria-describedby="errors[field.key] ? errorId(field.key) : null"
          :disabled="disabled"
          @change="updateField(field.key, $event.target.value)"
        >
          <option value="">请选择{{ field.label }}</option>
          <option
            v-if="hasUnavailableCurrentValue(field)"
            :value="fieldValue(field.key)"
            disabled
          >
            {{ unavailableCurrentLabel(field) }}
          </option>
          <option v-for="option in fieldOptions(field)" :key="String(optionValue(option))" :value="optionValue(option)">
            {{ optionLabel(option) }}
          </option>
        </select>

        <input
          v-else-if="field.control === 'date-picker'"
          :id="fieldId(field.key)"
          type="date"
          :value="fieldValue(field.key)"
          :aria-invalid="errors[field.key] ? 'true' : 'false'"
          :aria-describedby="errors[field.key] ? errorId(field.key) : null"
          :disabled="disabled"
          @input="updateField(field.key, $event.target.value)"
        >

        <textarea
          v-else-if="field.control === 'textarea' || field.control === 'sensitive-textarea'"
          :id="fieldId(field.key)"
          :value="fieldValue(field.key)"
          rows="3"
          :autocomplete="field.sensitive ? 'off' : null"
          :aria-invalid="errors[field.key] ? 'true' : 'false'"
          :aria-describedby="errors[field.key] ? errorId(field.key) : null"
          :disabled="disabled"
          @input="updateField(field.key, $event.target.value)"
        />

        <input
          v-else
          :id="fieldId(field.key)"
          :type="field.key === 'phoneNumber' || field.key === 'emergencyContactPhone' ? 'tel' : 'text'"
          :value="fieldValue(field.key)"
          :autocomplete="field.sensitive ? 'off' : null"
          :aria-invalid="errors[field.key] ? 'true' : 'false'"
          :aria-describedby="errors[field.key] ? errorId(field.key) : null"
          :disabled="disabled"
          @input="updateField(field.key, $event.target.value)"
        >

        <div v-if="isDirtySensitive(field)" class="sensitive-hint">
          <span>请输入完整新值，不要填写星号占位符。</span>
          <button type="button" :disabled="disabled" @click="revertSensitive(field.key)">撤销修改</button>
        </div>
        <p v-if="errors[field.key]" :id="errorId(field.key)" class="field-error" role="alert">{{ errors[field.key] }}</p>
      </div>
    </div>

    <footer class="sticky-footer mobile-bottom-action-bar">
      <button
        v-if="step > 0"
        class="mobile-button mobile-button--secondary"
        type="button"
        :disabled="disabled"
        @click="$emit('back')"
      >上一步</button>
      <button
        v-if="step < steps.length - 1"
        class="mobile-button mobile-button--primary primary"
        type="button"
        :disabled="disabled"
        @click="$emit('next')"
      >下一步</button>
      <button
        v-else
        class="mobile-button mobile-button--primary save"
        type="button"
        :disabled="disabled"
        @click="$emit('save')"
      >{{ isEdit ? "保存草稿" : "创建入职" }}</button>
    </footer>
  </section>
</template>

<script>
import MobileOnboardingOwnerPicker from "./MobileOnboardingOwnerPicker"
import {
  FORM_STEPS,
  MASKED_FIELD_KEYS,
  optionLabel,
  optionValue
} from "../mobileOnboardingForm"

function normalizedSelectValue(value) {
  return value === null || value === undefined ? "" : String(value)
}

function syncSelectedValue(element, binding) {
  const targetValue = normalizedSelectValue(binding && binding.value)
  const options = Array.from((element && element.options) || [])
  const selectedIndex = options.findIndex(option => {
    const value = Object.prototype.hasOwnProperty.call(option, "_value") ? option._value : option.value
    return normalizedSelectValue(value) === targetValue
  })
  if (element && element.selectedIndex !== selectedIndex) element.selectedIndex = selectedIndex
}

export default {
  name: "MobileOnboardingStepForm",
  components: { MobileOnboardingOwnerPicker },
  props: {
    model: { type: Object, required: true },
    step: { type: Number, default: 0 },
    errors: { type: Object, default: () => ({}) },
    missingCounts: { type: Object, default: () => ({}) },
    options: { type: Object, default: () => ({}) },
    optionsReady: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false }
  },
  directives: {
    selectedValue: {
      inserted: syncSelectedValue,
      componentUpdated: syncSelectedValue
    }
  },
  data() {
    return {
      steps: FORM_STEPS,
      keyboardOffset: 0,
      viewportListening: false,
      viewportTarget: null,
      activeControl: null
    }
  },
  computed: {
    isEdit() { return Object.prototype.hasOwnProperty.call(this.model || {}, "version") },
    currentStep() { return this.steps[this.step] || this.steps[0] },
    currentFields() {
      const fields = this.currentStep ? this.currentStep.fields : []
      return this.isEdit ? fields : fields.filter(field => field.create)
    },
    currentMissingCount() { return this.missingCount(this.currentStep.key) },
    keyboardStyle() { return { "--mobile-keyboard-offset": `${this.keyboardOffset}px` } }
  },
  watch: {
    step() { this.focusStepHeading() }
  },
  mounted() {
    this.startViewportTracking()
    this.focusStepHeading()
  },
  beforeDestroy() { this.stopViewportTracking() },
  methods: {
    optionLabel,
    optionValue,
    fieldId(key) { return `mobile-onboarding-${key}` },
    labelId(key) { return `${this.fieldId(key)}-label` },
    errorId(key) { return `${this.fieldId(key)}-error` },
    fieldValue(key) {
      const value = this.model ? this.model[key] : ""
      return value === null || value === undefined ? "" : value
    },
    missingCount(key) {
      const value = Number(this.missingCounts && this.missingCounts[key])
      return Number.isFinite(value) && value > 0 ? value : 0
    },
    fieldOptions(field) {
      const values = field && field.options && this.options ? this.options[field.options] : []
      return Array.isArray(values) ? values : []
    },
    personValueLabel(field) {
      if (field && field.key === "ownerUserId" && this.model && this.model.ownerName) {
        return String(this.model.ownerName)
      }
      const current = normalizedSelectValue(this.fieldValue(field && field.key))
      const option = this.fieldOptions(field).find(item => {
        return normalizedSelectValue(optionValue(item)) === current
      })
      return option ? optionLabel(option) : ""
    },
    hasUnavailableCurrentValue(field) {
      if (!this.optionsReady || !Object.prototype.hasOwnProperty.call(this.model || {}, "version")) return false
      const currentValue = this.fieldValue(field && field.key)
      if (normalizedSelectValue(currentValue) === "") return false
      return !this.fieldOptions(field).some(option => {
        return normalizedSelectValue(optionValue(option)) === normalizedSelectValue(currentValue)
      })
    },
    unavailableCurrentLabel(field) {
      return `已保存的${field && field.label ? field.label : "选项"}（当前不可选）`
    },
    maskedValue(key) {
      const maskedKey = MASKED_FIELD_KEYS[key]
      return maskedKey && this.model ? this.model[maskedKey] || "" : ""
    },
    isDirtySensitive(field) {
      return Boolean(this.isEdit && field && field.sensitive && Object.prototype.hasOwnProperty.call(this.model || {}, field.key))
    },
    isProtectedSensitive(field) {
      return Boolean(this.isEdit && field && field.sensitive && !this.isDirtySensitive(field))
    },
    updateField(key, value) {
      const next = { ...(this.model || {}), [key]: value }
      this.$emit("input", next)
    },
    updatePerson(field, option) {
      if (!option || !option.userId) return
      const next = {
        ...(this.model || {}),
        [field.key]: option.userId
      }
      if (field.key === "ownerUserId") next.ownerName = option.label || ""
      this.$emit("input", next)
    },
    markSensitiveDirty(key) {
      this.$emit("sensitive-dirty", { key, dirty: true })
      this.$emit("input", { ...(this.model || {}), [key]: "" })
      this.$nextTick(() => this.focusField(key))
    },
    revertSensitive(key) {
      const next = { ...(this.model || {}) }
      delete next[key]
      this.$emit("sensitive-dirty", { key, dirty: false })
      this.$emit("input", next)
    },
    startViewportTracking() {
      if (typeof window === "undefined" || !window.visualViewport || this.viewportListening) return
      this.viewportTarget = window.visualViewport
      this.viewportTarget.addEventListener("resize", this.handleViewportChange, { passive: true })
      this.viewportTarget.addEventListener("scroll", this.handleViewportChange, { passive: true })
      this.viewportListening = true
      this.updateKeyboardOffset()
    },
    stopViewportTracking() {
      if (this.viewportTarget && this.viewportListening) {
        this.viewportTarget.removeEventListener("resize", this.handleViewportChange)
        this.viewportTarget.removeEventListener("scroll", this.handleViewportChange)
      }
      this.viewportListening = false
      this.viewportTarget = null
      this.activeControl = null
      this.keyboardOffset = 0
    },
    handleViewportChange() { this.updateKeyboardOffset() },
    handleFocusIn(event) {
      const target = event && event.target
      if (target && /^(INPUT|TEXTAREA|SELECT|BUTTON)$/.test(target.tagName || "")) this.activeControl = target
      this.updateKeyboardOffset()
    },
    updateKeyboardOffset() {
      if (typeof window === "undefined" || !window.visualViewport) {
        this.keyboardOffset = 0
        return
      }
      const viewport = window.visualViewport
      const innerHeight = Number(window.innerHeight)
      const height = Number(viewport.height)
      const offsetTop = Number(viewport.offsetTop)
      const measured = Number.isFinite(innerHeight) && Number.isFinite(height)
        ? innerHeight - height - (Number.isFinite(offsetTop) ? offsetTop : 0)
        : 0
      this.keyboardOffset = Math.max(0, Math.round(measured))
    },
    focusStepHeading() {
      this.$nextTick(() => {
        const heading = this.$refs.stepHeading
        if (heading && heading.focus) heading.focus()
      })
    },
    focusField(key) {
      this.$nextTick(() => {
        const ref = this.$refs[`field-${key}`]
        const container = Array.isArray(ref) ? ref[0] : ref
        const root = container && container.$el ? container.$el : container
        if (root && root.scrollIntoView) root.scrollIntoView({ behavior: "smooth", block: "center" })
        const control = root && root.querySelector ? root.querySelector("input, select, textarea, button") : null
        if (control && control.focus) control.focus()
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.step-form { min-height: 100%; color: #18312f; }
.step-header { padding: 18px 18px 8px; }
.step-header p { margin: 0 0 5px; color: #6b7f7c; font-size: 12px; }
.step-header h2 { margin: 0; font-size: 22px; outline: none; }
.missing-badge { margin-left: 7px; color: #a44839; font-size: 12px; font-weight: 600; }
.step-progress { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; padding: 8px 16px 10px; }
.step-progress button {
  min-width: 44px;
  min-height: 52px;
  display: grid;
  place-items: center;
  gap: 2px;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  background: var(--mobile-color-surface, #fff);
  color: var(--mobile-color-muted, #66736d);
  font-size: 12px;
  font-weight: 600;
}
.step-progress button .step-index { font-size: 13px; font-weight: 700; color: inherit; }
.step-progress button .step-label { line-height: 1.2; }
.step-progress button.is-current {
  border-color: rgba(11, 107, 83, 0.28);
  background: var(--mobile-color-primary-soft, #e7f2ed);
  color: var(--mobile-color-primary, #0b6b53);
}
.step-progress button.has-missing:not(.is-current) {
  border-color: rgba(196, 50, 43, 0.28);
  color: var(--mobile-color-danger, #c4322b);
}
.step-bar {
  height: 4px;
  margin: 0 16px 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--mobile-color-line, #dde2de);
}
.step-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--mobile-color-primary, #0b6b53);
}
.fields { padding: 0 16px 120px; }
.empty-step { margin: 12px 16px 120px; padding: 22px; border-radius: 16px; background: var(--mobile-color-surface-soft, #f8f8f5); color: var(--mobile-color-muted, #66736d); text-align: center; }
.field-row { margin-bottom: 18px; }
.field-row > label { display: block; margin-bottom: 7px; font-size: 13px; font-weight: 600; color: var(--mobile-color-muted, #66736d); }
input,
select,
textarea {
  width: 100%;
  min-height: 44px;
  box-sizing: border-box;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  background: #fff;
  padding: 10px 12px;
  color: var(--mobile-color-ink, #17211d);
  font: inherit;
  font-size: 16px;
}
textarea { resize: vertical; }
input[aria-invalid="true"], select[aria-invalid="true"], textarea[aria-invalid="true"] { border-color: var(--mobile-color-danger, #c4322b); }
.masked-control { display: flex; align-items: center; min-height: 44px; gap: 8px; border-radius: 12px; background: var(--mobile-color-surface-soft, #f8f8f5); padding-left: 12px; }
.masked-control span { min-width: 0; flex: 1; color: var(--mobile-color-muted, #66736d); }
.masked-control button,
.sensitive-hint button { min-width: 44px; min-height: 44px; border: 0; background: transparent; color: var(--mobile-color-primary, #0b6b53); font-weight: 600; }
.sensitive-hint { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--mobile-color-subtle, #8a948f); font-size: 12px; }
.field-error { margin: 5px 2px 0; color: var(--mobile-color-danger, #c4322b); font-size: 13px; }
.sticky-footer {
  position: fixed;
  z-index: 90;
  right: 0;
  bottom: var(--mobile-keyboard-offset, 0px);
  left: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 8px;
  max-width: 480px;
  margin: 0 auto;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--mobile-color-line, #dde2de);
  background: var(--mobile-color-surface, #fff);
  box-shadow: 0 -8px 20px rgba(23, 33, 29, 0.06);
  backdrop-filter: none;
}
.sticky-footer button { min-width: 44px; min-height: 48px; width: 100%; border-radius: 12px; font-weight: 600; }
.sticky-footer .primary,
.sticky-footer .save {
  border-color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary, #0b6b53);
  color: #fff;
}
.sticky-footer button:disabled { opacity: .45; }
</style>
