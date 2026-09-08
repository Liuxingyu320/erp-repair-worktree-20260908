<template>
  <el-dialog
    title="确认入职"
    width="720px"
    :visible="visible"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="guardBeforeClose"
    @close="close"
  >
    <template v-if="confirmResult">
      <el-result icon="success" title="入职确认成功" :sub-title="resultMessage">
        <template slot="extra">
          <div v-if="oneTimePassword" class="one-time-password">
            <strong>一次性初始密码</strong>
            <code>{{ oneTimePassword }}</code>
            <p>首次登录必须修改密码；临时凭证在过期后将无法登录。</p>
            <p v-if="oneTimePasswordExpiresAtDisplay">临时密码过期时间：{{ oneTimePasswordExpiresAtDisplay }}</p>
            <p>此密码仅展示一次，请通过安全方式交付员工，关闭后无法再次查看。</p>
            <el-button type="primary" @click="acknowledgePassword">我已安全记录</el-button>
          </div>
          <el-button v-else type="primary" @click="finish">完成</el-button>
        </template>
      </el-result>
    </template>

    <template v-else>
      <el-alert v-if="submitMessage" :title="submitMessage" type="error" :closable="false" show-icon />
      <el-form ref="form" :model="model" label-width="126px">
        <el-form-item label="实际入职日期" prop="actualEntryDate" :error="fieldErrors.actualEntryDate">
          <el-date-picker
            v-model="model.actualEntryDate"
            type="date"
            value-format="yyyy-MM-dd"
            style="width: 100%"
            @change="clearFieldError('actualEntryDate')"
          />
        </el-form-item>
        <el-form-item label="冲突处理" prop="conflictAction" :error="fieldErrors.conflictAction">
          <el-radio-group v-model="model.conflictAction" @change="handleConflictActionChange">
            <el-radio label="CREATE_NEW" :disabled="hasBlockingConflict">新建员工账号</el-radio>
            <el-radio label="BIND_EXISTING" :disabled="!eligibleCandidates.length">绑定现有账号</el-radio>
            <el-radio label="REHIRE_EXISTING" :disabled="!rehireCandidates.length">恢复原账号（再入职）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-alert
          v-if="hasBlockingConflict"
          title="当前存在历史或在职账号，不能新建重复账号；如有可用候选，请选择绑定或恢复原账号，否则先修复账号状态。"
          type="warning"
          :closable="false"
          show-icon
        />

        <section v-if="conflicts.length" class="conflict-summary">
          <h4>冲突候选摘要</h4>
          <div v-for="(candidate, index) in conflicts" :key="conflictKey(candidate, index)" class="conflict-summary__item">
            <div>
              <strong>{{ candidate.name || "未命名候选" }}</strong>
              <span>{{ candidate.maskedPhone || "手机号未提供" }}</span>
              <span>{{ candidate.departmentLabel || "部门未提供" }}</span>
            </div>
            <div class="conflict-summary__meta">
              <el-tag v-if="isRehireCandidate(candidate)" size="mini" type="warning">可恢复再入职</el-tag>
              <el-tag v-else-if="isEligibleCandidate(candidate)" size="mini" type="success">可绑定</el-tag>
              <el-tag v-else-if="candidate.blocking" size="mini" type="danger">阻断冲突</el-tag>
              <el-tag v-else size="mini" type="info">不可处理</el-tag>
              <span v-if="candidate.candidateOnboardingId">入职单 #{{ candidate.candidateOnboardingId }}</span>
              <span v-else-if="candidate.employeeNo">工号 {{ candidate.employeeNo }}</span>
              <span>{{ conflictLabel(candidate) }}</span>
            </div>
          </div>
        </section>

        <el-form-item
          v-if="['BIND_EXISTING', 'REHIRE_EXISTING'].includes(model.conflictAction)"
          :label="model.conflictAction === 'REHIRE_EXISTING' ? '恢复原账号' : '绑定账号'"
          prop="bindUserId"
          :error="fieldErrors.bindUserId"
        >
          <el-radio-group v-model="model.bindUserId" class="candidate-list" @change="clearFieldError('bindUserId')">
            <el-radio
              v-for="candidate in actionCandidates"
              :key="candidate.candidateUserId"
              :label="candidate.candidateUserId"
              border
              class="candidate-card"
            >
              <strong>{{ candidate.name || "未命名账号" }}</strong>
              <span>{{ candidate.maskedPhone || "手机号未提供" }}</span>
              <span>{{ candidate.departmentLabel || "部门未提供" }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <div v-if="conflictLoading" class="conflict-state"><i class="el-icon-loading" /> 正在检查账号冲突…</div>
        <el-alert v-else-if="conflictError" :title="conflictError" type="error" :closable="false" show-icon>
          <el-button type="text" @click="loadConflicts">重新检查</el-button>
        </el-alert>
      </el-form>
      <span slot="footer">
        <el-button @click="close">取消</el-button>
        <el-button
          type="success"
          :loading="submitting"
          :disabled="conflictLoading || Boolean(conflictError) || !canSubmit"
          @click="submit"
        >确认入职</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script>
import { confirmHrOnboarding, getHrOnboardingConflicts } from "@/api/hr/onboarding"
import {
  createIdempotencyKey,
  eligibleBindCandidates,
  eligibleRehireCandidates,
  isOnboardingVersionConflict,
  onboardingErrorBody,
  preferredConflictSelection,
  sanitizeConfirmResult
} from "../onboardingFieldConfig"

const CONFLICT_LABELS = {
  ID_NUMBER: "证件号码重复",
  PHONE: "手机号重复",
  EMPLOYEE_NO: "员工工号重复",
  UNKNOWN: "账号信息冲突",
  EMPLOYEE_ACCOUNT: "现有员工账号",
  ONBOARDING: "其他入职记录"
}

const onboardingConflictLabel = candidate => {
  const source = candidate || {}
  const value = source.conflictType || source.sourceType
  return CONFLICT_LABELS[String(value || "").toUpperCase()] || "账号信息冲突"
}

export default {
  name: "HrOnboardingConfirmDialog",
  props: {
    visible: { type: Boolean, default: false },
    detail: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      model: { actualEntryDate: "", conflictAction: "CREATE_NEW", bindUserId: null },
      idempotencyKey: "",
      conflicts: [],
      conflictLoading: false,
      conflictError: "",
      conflictRequestSequence: 0,
      fieldErrors: {},
      submitMessage: "",
      submitting: false,
      confirmResult: null,
      oneTimePassword: null,
      oneTimePasswordExpiresAt: null,
      resultMessage: "",
      dialogGeneration: 0,
      submitRequestSequence: 0,
      activeSubmitSequence: 0,
      pendingDetail: null
    }
  },
  computed: {
    eligibleCandidates() {
      return eligibleBindCandidates(this.conflicts)
    },
    rehireCandidates() {
      return eligibleRehireCandidates(this.conflicts)
    },
    actionCandidates() {
      return this.model.conflictAction === "REHIRE_EXISTING" ? this.rehireCandidates : this.eligibleCandidates
    },
    hasBlockingConflict() {
      return this.conflicts.some(candidate => Boolean(candidate && candidate.blocking))
    },
    canSubmit() {
      if (!this.model.actualEntryDate) return false
      if (this.model.conflictAction === "CREATE_NEW") return !this.hasBlockingConflict
      return ["BIND_EXISTING", "REHIRE_EXISTING"].includes(this.model.conflictAction) && this.actionCandidates.some(candidate =>
        candidate.candidateUserId === this.model.bindUserId
      )
    },
    oneTimePasswordExpiresAtDisplay() {
      if (!this.oneTimePasswordExpiresAt) return ""
      const value = new Date(this.oneTimePasswordExpiresAt)
      if (Number.isNaN(value.getTime())) return String(this.oneTimePasswordExpiresAt)
      const pad = part => String(part).padStart(2, "0")
      return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}`
    }
  },
  watch: {
    visible(value) {
      if (value) this.openDialog()
      else {
        this.invalidateLifecycle()
        this.disposeSecrets()
      }
    },
    detail(value, previous) {
      this.handleDetailChange(value, previous)
    }
  },
  beforeDestroy() {
    this.invalidateLifecycle()
    this.disposeSecrets()
  },
  methods: {
    conflictLabel: onboardingConflictLabel,
    openDialog() {
      if (this.submitting) {
        this.pendingDetail = this.detail
        this.invalidateLifecycle()
        return Promise.resolve([])
      }
      this.dialogGeneration += 1
      this.conflictRequestSequence += 1
      this.pendingDetail = null
      this.model = { actualEntryDate: "", conflictAction: "CREATE_NEW", bindUserId: null }
      this.disposeSecrets()
      this.idempotencyKey = createIdempotencyKey()
      this.conflicts = []
      this.conflictError = ""
      this.fieldErrors = {}
      this.submitMessage = ""
      return this.loadConflicts()
    },
    handleDetailChange(value, previous) {
      if (this.confirmResult) return
      if (!this.visible || !value) return
      const changed = !previous || value.onboardingId !== previous.onboardingId || value.version !== previous.version
      if (!changed) return
      if (this.submitting) {
        this.pendingDetail = value
        this.invalidateLifecycle()
        return
      }
      this.openDialog()
    },
    invalidateLifecycle() {
      this.dialogGeneration += 1
      this.conflictRequestSequence += 1
    },
    isActiveConflict(generation, requestSequence, onboardingId) {
      return this.visible && generation === this.dialogGeneration && requestSequence === this.conflictRequestSequence &&
        this.detail && this.detail.onboardingId === onboardingId
    },
    loadConflicts() {
      const onboardingId = this.detail && this.detail.onboardingId
      if (!onboardingId) return Promise.resolve([])
      const generation = this.dialogGeneration
      const requestSequence = ++this.conflictRequestSequence
      this.conflictLoading = true
      this.conflictError = ""
      return getHrOnboardingConflicts(onboardingId)
        .then(response => {
          if (!this.isActiveConflict(generation, requestSequence, onboardingId)) return []
          const rows = response && response.data
          this.conflicts = Array.isArray(rows) ? rows : []
          const preferred = preferredConflictSelection(this.detail, this.conflicts)
          this.model.conflictAction = preferred.conflictAction
          this.model.bindUserId = preferred.bindUserId
          if (this.hasBlockingConflict && this.model.conflictAction === "CREATE_NEW") {
            if (this.eligibleCandidates.length) this.model.conflictAction = "BIND_EXISTING"
            else if (this.rehireCandidates.length === 1) {
              this.model.conflictAction = "REHIRE_EXISTING"
              this.model.bindUserId = this.rehireCandidates[0].candidateUserId
            }
          }
          return this.conflicts
        })
        .catch(error => {
          if (!this.isActiveConflict(generation, requestSequence, onboardingId)) return []
          this.conflicts = []
          this.conflictError = (error && error.message) || "账号冲突检查失败，请重试后再确认。"
          return []
        })
        .finally(() => {
          if (this.isActiveConflict(generation, requestSequence, onboardingId)) this.conflictLoading = false
        })
    },
    isEligibleCandidate(candidate) {
      return eligibleBindCandidates([candidate]).length === 1 || eligibleRehireCandidates([candidate]).length === 1
    },
    isRehireCandidate(candidate) {
      return eligibleRehireCandidates([candidate]).length === 1
    },
    conflictKey(candidate, index) {
      return `${candidate.sourceType || "conflict"}-${candidate.candidateUserId || "user"}-${candidate.candidateOnboardingId || "onboarding"}-${index}`
    },
    handleConflictActionChange(action) {
      this.model.bindUserId = null
      if (action === "REHIRE_EXISTING" && this.rehireCandidates.length === 1)
        this.model.bindUserId = this.rehireCandidates[0].candidateUserId
      this.clearFieldError("conflictAction")
    },
    clearFieldError(key) {
      if (this.fieldErrors[key]) {
        if (this.$delete) this.$delete(this.fieldErrors, key)
        else delete this.fieldErrors[key]
      }
    },
    applyServerErrors(error) {
      const body = onboardingErrorBody(error)
      this.fieldErrors = body.fieldErrors && typeof body.fieldErrors === "object" ? { ...body.fieldErrors } : {}
      this.submitMessage = body.msg || body.message || "确认入职失败，请修正后重试。"
    },
    submit() {
      if (this.submitting) return Promise.resolve(null)
      const onboardingId = this.detail.onboardingId
      const version = this.detail.version
      const idempotencyKey = this.idempotencyKey
      const generation = this.dialogGeneration
      const requestSequence = ++this.submitRequestSequence
      this.activeSubmitSequence = requestSequence
      const payload = {
        version,
        actualEntryDate: this.model.actualEntryDate,
        conflictAction: this.model.conflictAction,
        bindUserId: ["BIND_EXISTING", "REHIRE_EXISTING"].includes(this.model.conflictAction) ? this.model.bindUserId : null,
        idempotencyKey
      }
      this.submitting = true
      this.fieldErrors = {}
      this.submitMessage = ""
      return confirmHrOnboarding(onboardingId, payload)
        .then(response => {
          const result = response && response.data ? response.data : response
          const safe = sanitizeConfirmResult(result || {})
          if (!this.isActiveSubmit(generation, requestSequence, onboardingId, version, idempotencyKey)) {
            if (result && typeof result === "object") result.oneTimePassword = null
            return null
          }
          this.applyConfirmResult(result || {})
          this.$emit("confirmed", safe)
          return safe
        })
        .catch(error => {
          if (!this.isActiveSubmit(generation, requestSequence, onboardingId, version, idempotencyKey)) return null
          if (isOnboardingVersionConflict(error)) this.handleVersionConflict(onboardingId)
          else this.applyServerErrors(error)
          return null
        })
        .finally(() => {
          if (requestSequence !== this.activeSubmitSequence) return
          this.submitting = false
          this.activeSubmitSequence = 0
          if (this.pendingDetail && this.visible) this.openDialog()
        })
    },
    isActiveSubmit(generation, requestSequence, onboardingId, version, idempotencyKey) {
      return this.visible && generation === this.dialogGeneration && requestSequence === this.activeSubmitSequence &&
        this.detail && this.detail.onboardingId === onboardingId && this.detail.version === version &&
        this.idempotencyKey === idempotencyKey
    },
    applyConfirmResult(result) {
      const secret = result && result.oneTimePassword ? result.oneTimePassword : null
      const expiresAt = result && result.oneTimePasswordExpiresAt ? result.oneTimePasswordExpiresAt : null
      this.confirmResult = sanitizeConfirmResult(result)
      if (result && typeof result === "object") {
        result.oneTimePassword = null
        result.oneTimePasswordExpiresAt = null
      }
      this.oneTimePassword = secret
      this.oneTimePasswordExpiresAt = secret ? expiresAt : null
      if (this.oneTimePassword) {
        this.resultMessage = "账号创建成功。一次性初始密码只会在本次确认结果中展示；首次登录必须改密。"
      } else if (result.replayed) {
        this.resultMessage = "账号已创建成功。本次为幂等重放，不会再次展示一次性密码；请通过正常的重置密码流程设置新密码。"
      } else if (result.accountStatus) {
        this.resultMessage = "员工账号已关联成功；如需设置登录凭证，请使用正常的重置密码流程。"
      } else {
        this.resultMessage = "员工资料与入职状态已确认。"
      }
    },
    acknowledgePassword() {
      this.oneTimePassword = null
      this.oneTimePasswordExpiresAt = null
    },
    disposeSecrets() {
      this.oneTimePassword = null
      this.oneTimePasswordExpiresAt = null
      this.idempotencyKey = ""
      this.confirmResult = null
      this.resultMessage = ""
    },
    handleVersionConflict(onboardingId) {
      this.invalidateLifecycle()
      this.disposeSecrets()
      this.fieldErrors = {}
      this.submitMessage = ""
      this.$emit("version-conflict", { onboardingId })
      this.$emit("update:visible", false)
    },
    finish() {
      return this.close()
    },
    close() {
      if (this.submitting) return false
      this.invalidateLifecycle()
      this.disposeSecrets()
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
.el-alert { margin-bottom: 18px; }
.candidate-list { display: grid; width: 100%; gap: 10px; }
.candidate-card { width: 100%; height: auto; margin: 0 !important; padding: 13px 14px; }
.candidate-card ::v-deep .el-radio__label { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 12px; width: calc(100% - 28px); }
.candidate-card span { color: #64748b; }
.conflict-summary { margin: 14px 0; padding: 14px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
.conflict-summary h4 { margin: 0 0 10px; }
.conflict-summary__item { padding: 10px 0; border-top: 1px solid #e2e8f0; }
.conflict-summary__item:first-of-type { border-top: 0; }
.conflict-summary__item > div { display: flex; flex-wrap: wrap; gap: 8px 14px; }
.conflict-summary__item span { color: #64748b; }
.conflict-summary__meta { margin-top: 7px; font-size: 12px; }
.conflict-state { padding: 14px; text-align: center; color: #64748b; }
.one-time-password { max-width: 460px; padding: 18px; border: 1px solid #f5c86b; border-radius: 10px; background: #fffbeb; }
.one-time-password code { display: block; margin: 12px 0; font-size: 22px; color: #92400e; letter-spacing: 1px; }
.one-time-password p { color: #7c5b24; line-height: 1.6; }
</style>
