<template>
  <mobile-hr-shell
    :title="mode === 'edit' ? '补充入职资料' : '新建入职'"
    :back="goBack"
    :loading="initialLoading"
    :error="loadError"
    @retry="retryLoad"
  >
    <main class="mobile-form-page" aria-label="入职资料表单">
      <section v-if="optionsLoading" class="inline-state" role="status" aria-live="polite">
        正在加载可选项…
      </section>
      <section v-else-if="optionsError" class="inline-state is-error" role="alert">
        <span>{{ optionsError }}</span>
        <button type="button" @click="loadOptions(routeGeneration)">重试选项</button>
      </section>

      <section v-if="conflictNotice" class="inline-state is-warning" role="status">
        <span>{{ conflictNotice }}</span>
        <button v-if="conflictRefreshFailed" type="button" @click="retryConflictRefresh">重新获取最新版</button>
      </section>
      <section v-if="submitError" class="inline-state is-error" role="alert">
        <span>{{ submitError }}</span>
        <button v-if="!createOutcomeUnknown" type="button" :disabled="submitting" @click="retrySubmit">重试保存</button>
      </section>
      <section v-if="createOutcomeUnknown" class="inline-state is-warning" role="alert">
        <span>{{ outcomeUnknownMessage }}</span>
        <button type="button" :disabled="submitting" @click="openQueueForReconciliation">返回入职列表核对</button>
      </section>
      <section v-if="navigationError" class="inline-state is-error" role="alert">
        <span>{{ navigationError }}</span>
        <button type="button" :disabled="submitting" @click="retryNavigation">重试跳转</button>
      </section>

      <mobile-onboarding-step-form
        ref="stepForm"
        :model="model"
        :step="activeStep"
        :errors="fieldErrors"
        :missing-counts="missingCounts"
        :options="options"
        :options-ready="!optionsLoading && !optionsError"
        :disabled="formLocked"
        @input="handleModelInput"
        @sensitive-dirty="handleSensitiveDirty"
        @back="previousStep"
        @next="nextStep"
        @save="submit"
      />
      <div v-if="submitting" class="saving-shield" role="status" aria-live="assertive" aria-busy="true">
        {{ savedOnboardingId ? "保存成功，正在打开详情…" : "正在保存，请勿离开…" }}
      </div>
    </main>
  </mobile-hr-shell>
</template>

<script>
import {
  createHrOnboarding,
  getHrOnboarding,
  getHrOnboardingFormOptions,
  updateHrOnboarding
} from "@/api/hr/onboarding"
import MobileHrShell from "../components/MobileHrShell"
import MobileOnboardingStepForm from "../components/MobileOnboardingStepForm"
import { mobileHrErrorMessage } from "../mobileHrError"
import {
  CREATE_FIELDS,
  FORM_STEPS,
  MASKED_FIELD_KEYS,
  SENSITIVE_FIELDS,
  errorsByStep,
  firstErrorLocation,
  isMaskPlaceholder,
  missingCountsByStep,
  normalizePayload
} from "../mobileOnboardingForm"

function firstQueryValue(value) { return Array.isArray(value) ? value[0] : value }
const { normalizePositiveDecimalId } = require("@/utils/positiveDecimalId")
function positiveId(value) { return normalizePositiveDecimalId(firstQueryValue(value)) || null }
function errorBody(error) {
  if (!error || typeof error !== "object") return {}
  if (error.response && error.response.data && typeof error.response.data === "object") return error.response.data
  if (error.data && typeof error.data === "object") return error.data
  return error
}
function errorMessage(error, fallback) {
  return mobileHrErrorMessage(error, fallback)
}
function isVersionConflict(error) { return errorBody(error).errorCode === "ONBOARDING_VERSION_CONFLICT" }
function isStructuredBackendError(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return false
  if (body.fieldErrors && typeof body.fieldErrors === "object") return true
  if (body.errorCode !== undefined && body.errorCode !== null && String(body.errorCode).trim() !== "") return true
  const hasCode = body.code !== undefined && body.code !== null && String(body.code).trim() !== ""
  const hasMessage = [body.msg, body.message].some(value => value !== undefined && value !== null && String(value).trim() !== "")
  return hasCode && hasMessage
}
function httpTransportStatus(error) {
  if (!error || typeof error !== "object") return null
  const response = error.response && typeof error.response === "object" ? error.response : {}
  const candidate = response.status !== undefined ? response.status : error.status
  const status = Number(candidate)
  return Number.isInteger(status) && status >= 100 && status <= 599 ? status : null
}
function hasDeterministicServerResponse(error) {
  const status = httpTransportStatus(error)
  if (status === 408) return false
  const response = error && error.response && typeof error.response === "object" ? error.response : {}
  const responsePayload = response.data && typeof response.data === "object" ? response.data : null
  const directPayload = error && error.data && typeof error.data === "object" ? error.data : null
  if (isStructuredBackendError(responsePayload) || isStructuredBackendError(directPayload)) return true
  if (error && typeof error === "object" && (
    (error.fieldErrors && typeof error.fieldErrors === "object") ||
    (error.errorCode !== undefined && error.errorCode !== null && String(error.errorCode).trim() !== "")
  )) return true
  return status !== null && status >= 400 && status < 500
}
// Create POST has no idempotency key, so a statusless failure is terminal and must be reconciled.
// Edit PATCH remains retryable because its required version makes repeated writes bounded/conflict-safe.
function isCreateOutcomeUnknown(error) { return !hasDeterministicServerResponse(error) }
function emptyCounts() {
  return FORM_STEPS.reduce((result, step) => {
    result[step.key] = 0
    return result
  }, {})
}
function createModel() {
  return CREATE_FIELDS.reduce((result, key) => {
    result[key] = key.endsWith("Id") ? null : ""
    return result
  }, {})
}
function modelFromMaskedDetail(detail) {
  const source = detail && typeof detail === "object" ? detail : {}
  const result = {}
  result.version = Number(source.version)
  FORM_STEPS.forEach(step => step.fields.forEach(field => {
    if (!field.sensitive && Object.prototype.hasOwnProperty.call(source, field.key)) result[field.key] = source[field.key]
  }))
  if (source.ownerName) result.ownerName = source.ownerName
  Object.keys(MASKED_FIELD_KEYS).forEach(key => {
    const maskedKey = MASKED_FIELD_KEYS[key]
    if (!Object.prototype.hasOwnProperty.call(source, maskedKey)) return
    const value = source[maskedKey]
    result[maskedKey] = !value ? "" : (isMaskPlaceholder(String(value)) ? String(value) : "已填写")
  })
  return result
}
function copyModel(model) { return Object.assign({}, model || {}) }
function combinedMissingFields(detail) {
  const result = {}
  const seen = new Set()
  const append = value => {
    if (!value || typeof value !== "object") return
    Object.keys(value).forEach(group => {
      const values = Array.isArray(value[group]) ? value[group] : []
      values.forEach((entry, index) => {
        const key = typeof entry === "string" ? entry : entry && (entry.key || entry.field)
        const identity = key ? `key:${key}` : `${group}:${index}:${entry && entry.label ? entry.label : ""}`
        if (seen.has(identity)) return
        seen.add(identity)
        if (!result[group]) result[group] = []
        result[group].push(entry)
      })
    })
  }
  append(detail && detail.missingOnboardingFields)
  append(detail && detail.missingProfileFields)
  return result
}
function safeStateQuery(route) {
  const raw = firstQueryValue(route && route.query && route.query.stateKey)
  const stateKey = raw === undefined || raw === null ? "" : String(raw).trim()
  return /^qs_[a-z0-9_]{12,80}$/i.test(stateKey) ? { stateKey } : undefined
}
function responseData(response) {
  return response && Object.prototype.hasOwnProperty.call(response, "data") ? response.data : response
}

export default {
  name: "MobileHrOnboardingFormPage",
  components: { MobileHrShell, MobileOnboardingStepForm },
  data() {
    return {
      mode: "create",
      recordId: null,
      model: createModel(),
      originalModel: createModel(),
      sensitiveDirty: {},
      activeStep: 0,
      fieldErrors: {},
      missingCounts: emptyCounts(),
      options: {},
      optionsLoading: false,
      optionsError: "",
      initialLoading: false,
      loadError: "",
      submitError: "",
      navigationError: "",
      createOutcomeUnknown: false,
      outcomeUnknownMessage: "",
      conflictNotice: "",
      conflictRefreshFailed: false,
      submitting: false,
      successNavigationPending: false,
      successNavigationTarget: "",
      savedOnboardingId: null,
      navigationPromise: null,
      routeGeneration: 0,
      detailRequestSequence: 0,
      optionsRequestSequence: 0,
      submitRequestSequence: 0,
      conflictRequestSequence: 0,
      destroyed: false
    }
  },
  watch: {
    "$route.fullPath": {
      immediate: true,
      handler() { this.initializeRoute() }
    }
  },
  computed: {
    formLocked() { return this.submitting || Boolean(this.savedOnboardingId) || this.createOutcomeUnknown }
  },
  beforeDestroy() {
    this.destroyed = true
    this.routeGeneration += 1
    this.detailRequestSequence += 1
    this.optionsRequestSequence += 1
    this.conflictRequestSequence += 1
  },
  beforeRouteLeave(to, from, next) {
    const intendedSuccess = Boolean(this.savedOnboardingId) && to && to.path === this.successNavigationTarget
    const intendedReconciliation = this.createOutcomeUnknown && to && to.path === "/mobile/hr/onboarding"
    if ((this.submitting || this.savedOnboardingId || this.createOutcomeUnknown) && !intendedSuccess && !intendedReconciliation) {
      next(false)
      return
    }
    next()
  },
  beforeRouteUpdate(to, from, next) {
    if (this.submitting || this.savedOnboardingId || this.createOutcomeUnknown) {
      next(false)
      return
    }
    next()
  },
  methods: {
    initializeRoute() {
      const generation = ++this.routeGeneration
      const route = this.$route || {}
      const isCreate = route.path === "/mobile/hr/onboarding/create"
      const recordId = isCreate ? null : positiveId(route.params && route.params.id)
      this.mode = isCreate ? "create" : "edit"
      this.recordId = recordId
      this.activeStep = 0
      this.fieldErrors = {}
      this.missingCounts = emptyCounts()
      this.submitError = ""
      this.navigationError = ""
      this.createOutcomeUnknown = false
      this.outcomeUnknownMessage = ""
      this.conflictNotice = ""
      this.conflictRefreshFailed = false
      this.successNavigationPending = false
      this.successNavigationTarget = ""
      this.savedOnboardingId = null
      this.navigationPromise = null
      this.loadError = ""
      this.sensitiveDirty = {}
      this.options = {}
      this.model = isCreate ? createModel() : {}
      this.originalModel = copyModel(this.model)
      this.loadOptions(generation)

      if (isCreate) {
        this.initialLoading = false
        return Promise.resolve(this.model)
      }
      if (!recordId) {
        this.initialLoading = false
        this.loadError = "入职记录编号无效，请返回任务列表重试。"
        return Promise.resolve(null)
      }
      return this.loadDetail(recordId, generation)
    },
    isCurrentRoute(generation, recordId) {
      return !this.destroyed && generation === this.routeGeneration && recordId === this.recordId
    },
    loadOptions(generation = this.routeGeneration) {
      const sequence = ++this.optionsRequestSequence
      this.optionsLoading = true
      this.optionsError = ""
      return getHrOnboardingFormOptions({
        includeOwners: false,
        includeSupervisors: false
      })
        .then(response => {
          if (this.destroyed || generation !== this.routeGeneration || sequence !== this.optionsRequestSequence) return null
          const values = responseData(response)
          this.options = values && typeof values === "object" ? values : {}
          return this.options
        })
        .catch(error => {
          if (this.destroyed || generation !== this.routeGeneration || sequence !== this.optionsRequestSequence) return null
          this.options = {}
          this.optionsError = errorMessage(error, "可选项加载失败，请重试后再选择组织、岗位和字典项。")
          return null
        })
        .finally(() => {
          if (generation === this.routeGeneration && sequence === this.optionsRequestSequence) this.optionsLoading = false
        })
    },
    loadDetail(recordId = this.recordId, generation = this.routeGeneration) {
      const sequence = ++this.detailRequestSequence
      this.initialLoading = true
      this.loadError = ""
      return getHrOnboarding(recordId)
        .then(response => {
          if (!this.isCurrentRoute(generation, recordId) || sequence !== this.detailRequestSequence) return null
          const detail = responseData(response) || {}
          if (positiveId(detail.onboardingId) !== recordId) throw new Error("入职记录响应不匹配")
          this.model = modelFromMaskedDetail(detail)
          this.originalModel = copyModel(this.model)
          this.missingCounts = missingCountsByStep(combinedMissingFields(detail))
          return detail
        })
        .catch(error => {
          if (!this.isCurrentRoute(generation, recordId) || sequence !== this.detailRequestSequence) return null
          this.model = {}
          this.originalModel = {}
          this.loadError = errorMessage(error, "入职资料加载失败，请重试。")
          return null
        })
        .finally(() => {
          if (this.isCurrentRoute(generation, recordId) && sequence === this.detailRequestSequence) this.initialLoading = false
        })
    },
    retryLoad() {
      if (this.mode === "edit" && this.recordId) return this.loadDetail(this.recordId, this.routeGeneration)
      return this.initializeRoute()
    },
    handleModelInput(next) {
      const previous = this.model || {}
      this.model = copyModel(next)
      Object.keys(this.fieldErrors).forEach(key => {
        if (this.model[key] !== previous[key]) this.$delete(this.fieldErrors, key)
      })
    },
    handleSensitiveDirty(change) {
      if (!change || !SENSITIVE_FIELDS.includes(change.key)) return
      this.$set(this.sensitiveDirty, change.key, Boolean(change.dirty))
      if (!change.dirty && Object.prototype.hasOwnProperty.call(this.model, change.key)) this.$delete(this.model, change.key)
    },
    previousStep(target) {
      if (this.submitting) return false
      const next = Number.isInteger(target) ? target : this.activeStep - 1
      this.activeStep = Math.max(0, Math.min(FORM_STEPS.length - 1, next))
      return true
    },
    nextStep(target) {
      if (this.submitting) return false
      const next = Number.isInteger(target) ? target : this.activeStep + 1
      this.activeStep = Math.max(0, Math.min(FORM_STEPS.length - 1, next))
      return true
    },
    focusFirstError() {
      const location = firstErrorLocation(this.fieldErrors)
      if (!location) return
      this.activeStep = location.stepIndex
      this.$nextTick(() => {
        const form = this.$refs.stepForm
        if (form && form.focusField) form.focusField(location.fieldKey)
      })
    },
    applyFieldErrors(errors, missingFields) {
      this.fieldErrors = errors && typeof errors === "object" ? { ...errors } : {}
      const fieldCounts = errorsByStep(this.fieldErrors)
      const serverCounts = missingCountsByStep(missingFields)
      this.missingCounts = FORM_STEPS.reduce((result, step) => {
        result[step.key] = Math.max(Object.keys(fieldCounts[step.key]).length, serverCounts[step.key])
        return result
      }, {})
      this.focusFirstError()
    },
    applySubmitFailure(error) {
      const body = errorBody(error)
      this.createOutcomeUnknown = false
      this.outcomeUnknownMessage = ""
      this.applyFieldErrors(body.fieldErrors, body.missingOnboardingFields || body.missingFields)
      this.submitError = errorMessage(error, "保存失败，已保留当前填写内容，请检查网络后重试。")
    },
    submit() {
      if (this.savedOnboardingId) return this.retryNavigation()
      if (this.createOutcomeUnknown) return this.openQueueForReconciliation()
      if (this.submitting) return Promise.resolve(null)
      let payload
      try {
        payload = normalizePayload(this.model, {
          mode: this.mode,
          originalModel: this.originalModel,
          dirtySensitiveFields: this.sensitiveDirty
        })
      } catch (error) {
        this.applyFieldErrors(error.fieldErrors || {}, {})
        this.submitError = "敏感字段不能提交脱敏占位符，请输入完整新值或撤销修改。"
        return Promise.resolve(null)
      }
      if (this.mode === "edit" && !Number.isFinite(Number(payload.version))) {
        this.loadError = "入职记录版本缺失，请重新加载后再保存。"
        return Promise.resolve(null)
      }

      const generation = this.routeGeneration
      const recordId = this.recordId
      const sequence = ++this.submitRequestSequence
      this.submitting = true
      this.submitError = ""
      this.fieldErrors = {}
      const request = this.mode === "create"
        ? createHrOnboarding(payload)
        : updateHrOnboarding(recordId, payload)
      return request
        .then(response => {
          if (!this.isActiveSubmit(generation, recordId, sequence)) return null
          const result = responseData(response) || {}
          const resultId = positiveId(result.onboardingId)
          if (!resultId || (recordId && resultId !== recordId)) throw new Error("保存结果记录编号未确认，请核对当前记录")
          this.savedOnboardingId = resultId
          return this.navigateToDetail(resultId).then(() => result)
        })
        .catch(error => {
          if (!this.isActiveSubmit(generation, recordId, sequence)) return null
          if (this.mode === "edit" && isVersionConflict(error)) return this.refreshAfterConflict(generation, recordId)
          if (this.mode === "create" && isCreateOutcomeUnknown(error)) {
            this.markCreateOutcomeUnknown()
            return null
          }
          this.applySubmitFailure(error)
          return null
        })
        .finally(() => {
          if (generation === this.routeGeneration && sequence === this.submitRequestSequence && !this.savedOnboardingId) {
            this.submitting = false
          }
        })
    },
    isActiveSubmit(generation, recordId, sequence) {
      return !this.destroyed && generation === this.routeGeneration && recordId === this.recordId && sequence === this.submitRequestSequence
    },
    retrySubmit() { return this.submit() },
    markCreateOutcomeUnknown() {
      this.createOutcomeUnknown = true
      this.fieldErrors = {}
      this.submitError = ""
      this.outcomeUnknownMessage = "提交结果未知：入职单可能已经创建。为避免重复，请返回入职列表核对后再决定是否新建。"
    },
    openQueueForReconciliation() {
      const target = { path: "/mobile/hr/onboarding" }
      const query = safeStateQuery(this.$route)
      if (query) target.query = query
      if (this.$route && this.$route.path === target.path) return Promise.resolve(true)
      return Promise.resolve()
        .then(() => {
          if (!this.$router || typeof this.$router.replace !== "function") throw new Error("ROUTER_UNAVAILABLE")
          return this.$router.replace(target)
        })
        .then(() => true)
        .catch(() => {
          this.navigationError = "无法返回入职列表，请稍后重试。"
          return false
        })
    },
    retryNavigation() {
      if (!this.savedOnboardingId) return Promise.resolve(false)
      if (this.navigationPromise) return this.navigationPromise
      return this.navigateToDetail(this.savedOnboardingId)
    },
    refreshAfterConflict(generation = this.routeGeneration, recordId = this.recordId) {
      const sequence = ++this.conflictRequestSequence
      const baseline = copyModel(this.originalModel)
      this.submitError = ""
      this.conflictNotice = "检测到其他人已更新此入职单，正在获取最新脱敏资料。"
      this.conflictRefreshFailed = false
      return getHrOnboarding(recordId)
        .then(response => {
          if (!this.isCurrentRoute(generation, recordId) || sequence !== this.conflictRequestSequence) return null
          const detail = responseData(response) || {}
          if (positiveId(detail.onboardingId) !== recordId) throw new Error("入职记录响应不匹配")
          const userModel = copyModel(this.model)
          const dirtySensitive = { ...this.sensitiveDirty }
          const latestBaseline = modelFromMaskedDetail(detail)
          const merged = copyModel(latestBaseline)
          FORM_STEPS.forEach(step => step.fields.forEach(field => {
            if (field.sensitive) {
              if (dirtySensitive[field.key] && Object.prototype.hasOwnProperty.call(userModel, field.key)) merged[field.key] = userModel[field.key]
              return
            }
            if (Object.prototype.hasOwnProperty.call(userModel, field.key) && userModel[field.key] !== baseline[field.key]) {
              merged[field.key] = userModel[field.key]
            }
          }))
          this.originalModel = copyModel(latestBaseline)
          this.model = merged
          this.sensitiveDirty = dirtySensitive
          this.missingCounts = missingCountsByStep(combinedMissingFields(detail))
          this.fieldErrors = {}
          this.conflictNotice = "已载入最新版本并保留你的未提交修改，请复核后再次保存。"
          return detail
        })
        .catch(error => {
          if (!this.isCurrentRoute(generation, recordId) || sequence !== this.conflictRequestSequence) return null
          this.conflictRefreshFailed = true
          this.conflictNotice = "版本已变化，但最新版加载失败；当前输入仍保留，请先重新获取最新版。"
          this.submitError = errorMessage(error, "最新版加载失败，请重试。")
          return null
        })
    },
    retryConflictRefresh() {
      if (this.submitting || !this.recordId) return Promise.resolve(null)
      this.submitting = true
      return this.refreshAfterConflict(this.routeGeneration, this.recordId)
        .finally(() => { this.submitting = false })
    },
    navigateToDetail(recordId) {
      const target = { path: `/mobile/hr/onboarding/${recordId}` }
      const query = safeStateQuery(this.$route)
      if (query) target.query = query
      this.successNavigationTarget = target.path
      this.navigationError = ""
      if (this.$route && this.$route.path === target.path) {
        this.successNavigationPending = false
        this.submitting = false
        return Promise.resolve(true)
      }
      if (this.navigationPromise) return this.navigationPromise
      this.successNavigationPending = true
      this.submitting = true
      const navigation = Promise.resolve()
        .then(() => {
          if (!this.$router || typeof this.$router.replace !== "function") throw new Error("ROUTER_UNAVAILABLE")
          return this.$router.replace(target)
        })
        .then(() => {
          this.successNavigationPending = false
          this.submitting = false
          this.navigationPromise = null
          this.navigationError = ""
          return true
        })
        .catch(() => {
          this.successNavigationPending = false
          this.submitting = false
          this.navigationPromise = null
          this.navigationError = "保存成功但跳转失败，请重试打开入职详情。"
          return false
        })
      this.navigationPromise = navigation
      return navigation
    },
    goBack() {
      if (this.createOutcomeUnknown) return this.openQueueForReconciliation()
      if (this.submitting || this.savedOnboardingId) return false
      if (this.$router) this.$router.back()
      return true
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-form-page { position: relative; min-height: calc(100dvh - 150px); margin: -16px; }
.inline-state { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 12px 18px 0; padding: 11px 13px; border-radius: 12px; background: #edf6f4; color: #345e58; font-size: 13px; }
.inline-state.is-error { background: #fff0ed; color: #963f34; }
.inline-state.is-warning { background: #fff7e8; color: #815f25; }
.inline-state button { min-width: 44px; min-height: 44px; border: 0; border-radius: 10px; background: rgba(255, 255, 255, .8); color: inherit; font-weight: 650; }
.saving-shield { position: fixed; z-index: 20; top: max(12px, env(safe-area-inset-top)); left: 50%; min-height: 44px; transform: translateX(-50%); border-radius: 22px; background: #183f3a; padding: 0 18px; color: #fff; line-height: 44px; box-shadow: 0 8px 28px rgba(21, 65, 59, .22); }

@media (max-width: 320px) {
  .mobile-form-page { margin: -12px; }
}
</style>
