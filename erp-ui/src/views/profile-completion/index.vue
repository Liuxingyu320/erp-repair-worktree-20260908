<template>
  <div :class="['profile-completion-page', isMobileViewport ? 'is-mobile-profile mobile-system-page' : 'is-desktop-profile']">
    <main class="profile-completion-shell">
      <header class="completion-hero">
        <div class="hero-mark" aria-hidden="true">
          <i class="el-icon-document-checked" />
        </div>
        <div class="hero-copy">
          <span class="hero-eyebrow">员工入职信息</span>
          <h1>入职资料补全</h1>
          <p>请先完善本人必填资料。组织、门店与岗位信息由管理员维护，不影响本次提交。</p>
        </div>
        <div class="hero-progress" aria-label="资料完成进度">
          <el-progress type="circle" :width="82" :stroke-width="7" :percentage="completionProgress" color="#2f6d5b" />
          <span>还需 {{ missingFieldCount }} 项</span>
        </div>
      </header>

      <el-alert
        v-if="requestError"
        class="request-error"
        type="error"
        :title="requestError"
        :closable="false"
        show-icon
      />

      <section v-if="loading" class="profile-state-card" v-loading="true" element-loading-text="正在读取资料">
        <span>正在读取当前资料…</span>
      </section>

      <section v-else-if="loadFailed" class="profile-state-card load-failed-card">
        <i class="el-icon-warning-outline" aria-hidden="true" />
        <h2>暂时无法读取资料</h2>
        <p>请检查网络后重新加载；在资料读取成功前不会进入后续页面。</p>
        <el-button type="primary" plain icon="el-icon-refresh" @click="loadCompletion">重新加载</el-button>
      </section>

      <div v-else class="completion-layout">
        <section class="completion-form-card">
          <div class="section-heading">
            <div>
              <span>本人填写</span>
              <h2>当前缺失资料</h2>
            </div>
            <strong>{{ missingFieldCount }} / {{ totalFieldCount }}</strong>
          </div>

          <el-form class="completion-form" :model="form" label-position="top" @submit.native.prevent>
            <div class="completion-form-grid">
              <el-form-item
                v-for="field in visibleFields"
                :key="field.key"
                :class="['completion-field', { 'full-width-field': field.type === 'textarea' }]"
                :label="field.label"
                :error="fieldErrors[field.key]"
                :data-field-key="field.key"
                required
              >
                <el-select
                  v-if="field.type === 'select'"
                  v-model="form[field.key]"
                  :placeholder="field.placeholder"
                  :allow-create="!!field.allowCreate"
                  :filterable="!!field.allowCreate"
                  default-first-option
                  @change="clearFieldError(field.key)"
                >
                  <el-option
                    v-for="option in field.options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>

                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="form[field.key]"
                  type="date"
                  value-format="yyyy-MM-dd"
                  format="yyyy-MM-dd"
                  :placeholder="field.placeholder"
                  :picker-options="birthDatePickerOptions"
                  @change="clearFieldError(field.key)"
                />

                <el-input
                  v-else
                  v-model="form[field.key]"
                  :type="field.type === 'textarea' ? 'textarea' : field.type"
                  :rows="field.type === 'textarea' ? 3 : undefined"
                  :label="field.label"
                  :placeholder="field.placeholder"
                  :maxlength="field.maxlength"
                  :autocomplete="field.autocomplete || 'off'"
                  :inputmode="field.inputmode"
                  :show-word-limit="field.type === 'textarea'"
                  @input="clearFieldError(field.key)"
                />
              </el-form-item>
            </div>
          </el-form>

          <div class="privacy-note">
            <i class="el-icon-lock" aria-hidden="true" />
            <span>证件号码仅用于员工档案，已保存内容不会在此页面显示明文。</span>
          </div>
        </section>

        <aside class="completion-summary-column">
          <section v-if="completedSensitiveItems.length" class="summary-card masked-sensitive-section">
            <div class="section-heading compact-heading">
              <div>
                <span>隐私保护</span>
                <h2>已保存敏感资料</h2>
              </div>
              <i class="el-icon-lock" aria-hidden="true" />
            </div>
            <dl class="summary-list sensitive-list">
              <div v-for="item in completedSensitiveItems" :key="item.key">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="summary-card readonly-summary-section">
            <div class="section-heading compact-heading">
              <div>
                <span>管理员维护</span>
                <h2>组织与人事摘要</h2>
              </div>
              <i class="el-icon-office-building" aria-hidden="true" />
            </div>
            <p class="summary-tip">以下信息只读，缺失时也不阻止提交。</p>
            <dl class="summary-list readonly-list">
              <div v-for="item in readonlySummaryItems" :key="item.key">
                <dt>{{ item.label }}</dt>
                <dd :class="{ 'pending-value': !item.available }">{{ item.value }}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </div>

      <footer class="completion-actions">
        <div>
          <strong>资料完整后才能继续</strong>
          <span>保存成功后将前往组织选择页。</span>
        </div>
        <div class="action-buttons">
          <el-button :disabled="saving" @click="handleLogout">退出登录</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="loading || loadFailed || missingFieldCount === 0"
            @click="submitForm"
          >保存并继续</el-button>
        </div>
      </footer>
    </main>
  </div>
</template>

<script>
import { getProfileCompletion, updateProfileCompletion } from "@/api/system/user"
import { clearSelectedDept } from "@/utils/shopContext"
const { isMobileClient } = require("@/utils/clientPlatform")

const {
  PROFILE_COMPLETION_FIELDS,
  PROFILE_COMPLETION_FIELD_KEYS
} = require("./profileCompletionFields")
const {
  normalizeProfileCompletionPayload,
  validateProfileCompletion
} = require("./profileCompletionValidation")

const SENSITIVE_FIELDS = [
  { key: "idNumber", label: "证件号码" }
]

const READONLY_SUMMARY_FIELDS = [
  { key: "employeeNo", label: "工号" },
  { key: "companyName", label: "所属公司" },
  { key: "department", label: "当前部门" },
  { key: "deptLevel1Name", label: "一级部门" },
  { key: "deptLevel2Name", label: "二级部门" },
  { key: "deptLevel3Name", label: "三级部门" },
  { key: "storeName", label: "门店" },
  { key: "positionNames", label: "职位" },
  { key: "jobGrade", label: "职级" },
  { key: "departmentSupervisor", label: "部门主管" },
  { key: "directSupervisor", label: "直属主管" },
  { key: "employeeStatus", label: "员工状态" },
  { key: "employeeCategory", label: "人员类别" },
  { key: "entryDate", label: "入职日期" }
]

function createEmptyForm() {
  return PROFILE_COMPLETION_FIELD_KEYS.reduce((form, key) => {
    form[key] = ""
    return form
  }, {})
}

function safeMessage(fallback) {
  return fallback
}

function unwrapOriginalRedirect(value) {
  const text = String(value || "")
  if (text.split("?")[0] !== "/select-shop") return text || "/"
  const query = text.indexOf("?") > -1 ? text.slice(text.indexOf("?") + 1) : ""
  const pair = query.split("&").find(item => item.split("=")[0] === "redirect")
  if (!pair) return "/"
  try {
    return decodeURIComponent(pair.slice(pair.indexOf("=") + 1)) || "/"
  } catch (error) {
    return "/"
  }
}

export default {
  name: "ProfileCompletion",
  data() {
    return {
      loading: true,
      loadFailed: false,
      saving: false,
      isMobileViewport: false,
      missingFields: [],
      form: createEmptyForm(),
      fieldErrors: {},
      requestError: "",
      completedDisplayValues: {},
      readonlySummary: {},
      birthDatePickerOptions: {
        disabledDate(date) {
          return date.getTime() > Date.now()
        }
      }
    }
  },
  computed: {
    totalFieldCount() {
      return PROFILE_COMPLETION_FIELDS.length
    },
    missingFieldKeys() {
      return this.missingFields.map(field => field.key)
    },
    missingFieldCount() {
      return this.missingFields.length
    },
    completionProgress() {
      if (!this.totalFieldCount) return 100
      return Math.round(((this.totalFieldCount - this.missingFieldCount) / this.totalFieldCount) * 100)
    },
    visibleFields() {
      const missingKeys = new Set(this.missingFieldKeys)
      return PROFILE_COMPLETION_FIELDS.filter(field => missingKeys.has(field.key))
    },
    completedSensitiveItems() {
      return SENSITIVE_FIELDS.filter(field => this.completedDisplayValues[field.key]).map(field => ({
        ...field,
        value: this.completedDisplayValues[field.key]
      }))
    },
    readonlySummaryItems() {
      return READONLY_SUMMARY_FIELDS.map(field => {
        const value = this.readonlySummary[field.key]
        return {
          ...field,
          available: !!value,
          value: value || "待管理员完善"
        }
      })
    }
  },
  created() {
    this.updateViewport()
    this.loadCompletion()
  },
  mounted() {
    window.addEventListener("resize", this.updateViewport)
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.updateViewport)
  },
  methods: {
    updateViewport() {
      this.isMobileViewport = isMobileClient()
    },
    loadCompletion() {
      this.loading = true
      this.loadFailed = false
      this.requestError = ""
      getProfileCompletion().then(response => {
        const completion = response.data || {}
        this.applyCompletion(completion)
        if (!completion.completionRequired) {
          return this.$store.dispatch('GetInfo').then(() => this.routeToShop())
        }
        return null
      }).catch(() => {
        this.loadFailed = true
        this.requestError = safeMessage("读取资料失败，请稍后重试")
      }).finally(() => {
        this.loading = false
      })
    },
    applyCompletion(completion) {
      this.missingFields = Array.isArray(completion.missingFields) ? completion.missingFields : []
      this.form = Object.assign(createEmptyForm(), completion.values || {})
      this.completedDisplayValues = completion.completedDisplayValues || {}
      this.readonlySummary = completion.readonlySummary || {}
      this.fieldErrors = {}
    },
    clearFieldError(fieldKey) {
      if (this.fieldErrors[fieldKey]) {
        this.$delete(this.fieldErrors, fieldKey)
      }
      if (this.requestError === "请检查标红的必填资料") {
        this.requestError = ""
      }
    },
    submitForm() {
      const errors = validateProfileCompletion(this.form, this.missingFieldKeys)
      this.fieldErrors = errors
      this.requestError = ""
      if (Object.keys(errors).length > 0) {
        this.requestError = "请检查标红的必填资料"
        this.scrollToFirstError(Object.keys(errors)[0])
        return
      }

      const payload = normalizeProfileCompletionPayload(this.form, this.missingFieldKeys)
      this.saving = true
      updateProfileCompletion(payload).then(response => {
        const completion = response.data || {}
        return this.$store.dispatch('GetInfo').then(() => completion)
      }).then(completion => {
        this.applyCompletion(completion)
        if (completion.completionRequired || this.$store.getters.profileCompletionRequired) {
          this.requestError = `仍有 ${this.missingFieldCount} 项资料需要完善`
          return
        }
        this.routeToShop()
      }).catch(() => {
        const message = safeMessage("保存失败，请稍后重试")
        this.requestError = message
        this.applyServerFieldError(message)
      }).finally(() => {
        this.saving = false
      })
    },
    applyServerFieldError(message) {
      const field = this.visibleFields.find(item => message.indexOf(item.label) > -1) ||
        (message.indexOf("手机") > -1 && this.visibleFields.find(item => item.key === "phonenumber")) ||
        (message.indexOf("身份证") > -1 && this.visibleFields.find(item => item.key === "idNumber"))
      if (field) {
        this.$set(this.fieldErrors, field.key, message)
        this.scrollToFirstError(field.key)
      }
    },
    scrollToFirstError(fieldKey) {
      this.$nextTick(() => {
        const element = this.$el.querySelector(`[data-field-key="${fieldKey}"]`)
        if (element && element.scrollIntoView) {
          element.scrollIntoView({ behavior: "smooth", block: "center" })
        }
      })
    },
    routeToShop() {
      clearSelectedDept()
      const requestedRedirect = unwrapOriginalRedirect(this.$route.query && this.$route.query.redirect)
      this.$router.replace({
        path: '/select-shop',
        query: requestedRedirect && requestedRedirect !== "/" ? { redirect: requestedRedirect } : {}
      }).catch(() => {})
    },
    handleLogout() {
      clearSelectedDept()
      this.$store.dispatch("LogOut").then(() => {
        this.$router.replace("/login").catch(() => {})
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.profile-completion-page {
  min-height: 100vh;
  min-height: 100dvh;
  min-height: var(--mobile-viewport-height, 100dvh);
  box-sizing: border-box;
  padding:
    calc(var(--mobile-safe-top, env(safe-area-inset-top, 0px)) + 20px)
    16px
    calc(32px + env(safe-area-inset-bottom));
  color: var(--mobile-color-ink, #17211d);
  background: var(--mobile-color-page, #f4f5f2);
}

.profile-completion-shell {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.completion-hero,
.completion-form-card,
.summary-card,
.profile-state-card,
.completion-actions {
  border: 1px solid var(--mobile-color-line, #dde2de);
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
  backdrop-filter: none;
}

.completion-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 18px 16px;
  border-radius: 16px;
}

.hero-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  color: #fff;
  background: var(--mobile-color-primary, #0b6b53);
  box-shadow: none;

  i {
    font-size: 24px;
  }
}

.hero-copy {
  min-width: 0;

  h1 {
    margin: 3px 0 7px;
    font-size: clamp(26px, 3vw, 38px);
    line-height: 1.18;
    letter-spacing: 0.02em;
  }

  p {
    max-width: 720px;
    margin: 0;
    color: #66776f;
    font-size: 14px;
    line-height: 1.7;
  }
}

.hero-eyebrow,
.section-heading span {
  color: #7b8d84;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.hero-progress {
  display: flex;
  align-items: center;
  gap: 13px;

  span {
    color: #53675e;
    font-size: 13px;
    font-weight: 700;
    white-space: nowrap;
  }
}

.request-error {
  margin-top: 16px;
  border-radius: 14px;
}

.completion-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.75fr);
  gap: 20px;
  margin-top: 20px;
  align-items: start;
}

.completion-form-card,
.summary-card,
.profile-state-card {
  border-radius: 22px;
}

.completion-form-card {
  padding: 26px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;

  h2 {
    margin: 4px 0 0;
    color: #24443a;
    font-size: 21px;
  }

  strong {
    padding: 7px 12px;
    border-radius: 999px;
    color: #2f6d5b;
    background: #e8f2ed;
    font-size: 13px;
  }
}

.completion-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.completion-field.full-width-field {
  grid-column: 1 / -1;
}

.completion-form {
  ::v-deep .el-form-item__label {
    padding-bottom: 8px;
    color: #344f46;
    font-weight: 700;
    line-height: 1.35;
  }

  ::v-deep .el-select,
  ::v-deep .el-date-editor.el-input,
  ::v-deep .el-date-editor.el-input__inner {
    width: 100%;
  }

  ::v-deep .el-input__inner {
    height: 44px;
    border-radius: 11px;
    border-color: #d8e3dd;
    line-height: 44px;
  }

  ::v-deep .el-textarea__inner {
    border-radius: 11px;
    border-color: #d8e3dd;
    line-height: 1.65;
  }

  ::v-deep .el-input__inner:focus,
  ::v-deep .el-textarea__inner:focus {
    border-color: #4b8a73;
    box-shadow: 0 0 0 3px rgba(75, 138, 115, 0.1);
  }
}

.privacy-note {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin-top: 4px;
  padding: 13px 15px;
  border-radius: 12px;
  color: #5f7169;
  background: #f3f7f4;
  font-size: 13px;
  line-height: 1.6;

  i {
    margin-top: 3px;
    color: #3f7d67;
  }
}

.completion-summary-column {
  display: grid;
  gap: 20px;
}

.summary-card {
  padding: 22px;
}

.compact-heading {
  margin-bottom: 16px;

  h2 {
    font-size: 18px;
  }

  > i {
    color: #4d826f;
    font-size: 22px;
  }
}

.summary-tip {
  margin: -5px 0 15px;
  color: #84918b;
  font-size: 12px;
  line-height: 1.6;
}

.summary-list {
  display: grid;
  margin: 0;

  > div {
    display: grid;
    grid-template-columns: minmax(90px, 0.7fr) minmax(0, 1.3fr);
    gap: 12px;
    padding: 11px 0;
    border-bottom: 1px solid #edf1ee;
  }

  > div:last-child {
    border-bottom: 0;
  }

  dt,
  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }

  dt {
    color: #839088;
    font-size: 12px;
  }

  dd {
    color: #304b42;
    font-size: 13px;
    font-weight: 650;
    text-align: right;
  }

  .pending-value {
    color: #a28e67;
    font-weight: 500;
  }
}

.sensitive-list dd {
  color: #2f6d5b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  letter-spacing: 0.03em;
}

.profile-state-card {
  min-height: 300px;
  margin-top: 20px;
  display: grid;
  place-items: center;
  padding: 32px;
  color: #72837b;
}

.load-failed-card {
  align-content: center;
  gap: 10px;
  text-align: center;

  > i {
    color: #b18148;
    font-size: 38px;
  }

  h2,
  p {
    margin: 0;
  }

  p {
    max-width: 460px;
    margin-bottom: 8px;
    line-height: 1.7;
  }
}

.completion-actions {
  position: sticky;
  z-index: 5;
  bottom: max(16px, env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  margin-top: 20px;
  padding: 17px 20px;
  border-radius: 18px;

  > div:first-child {
    display: grid;
    gap: 3px;

    strong {
      color: #2d493f;
      font-size: 14px;
    }

    span {
      color: #829087;
      font-size: 12px;
    }
  }
}

.action-buttons {
  display: flex;
  gap: 10px;

  ::v-deep .el-button {
    min-height: 44px;
    border-radius: 11px;
    padding: 11px 22px;
  }

  ::v-deep .el-button--primary {
    border-color: #2f6d5b;
    background: #2f6d5b;
  }
}

@media (max-width: 900px) {
  .completion-layout {
    grid-template-columns: 1fr;
  }

  .completion-summary-column {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .profile-completion-page {
    padding: 14px 12px calc(20px + env(safe-area-inset-bottom));
  }

  .completion-hero {
    grid-template-columns: auto minmax(0, 1fr);
    gap: 14px;
    padding: 19px 17px;
    border-radius: 19px;
  }

  .hero-mark {
    width: 48px;
    height: 48px;
    border-radius: 15px;

    i {
      font-size: 24px;
    }
  }

  .hero-copy h1 {
    font-size: 25px;
  }

  .hero-copy p {
    font-size: 13px;
  }

  .hero-progress {
    grid-column: 1 / -1;
    justify-content: flex-start;
    padding-top: 4px;

    ::v-deep .el-progress--circle {
      width: 58px !important;
      height: 58px !important;
    }

    ::v-deep .el-progress-circle {
      width: 58px !important;
      height: 58px !important;
    }
  }

  .completion-layout {
    margin-top: 14px;
  }

  .completion-form-card,
  .summary-card {
    padding: 19px 16px;
    border-radius: 18px;
  }

  .completion-form-grid,
  .completion-summary-column {
    grid-template-columns: 1fr;
  }

  .completion-field.full-width-field {
    grid-column: auto;
  }

  .completion-actions {
    bottom: max(8px, env(safe-area-inset-bottom));
    align-items: stretch;
    flex-direction: column;
    margin-top: 14px;
    padding: 14px;

    > div:first-child {
      text-align: center;
    }
  }

  .action-buttons {
    display: grid;
    grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);

    ::v-deep .el-button {
      width: 100%;
      margin: 0;
      padding-right: 10px;
      padding-left: 10px;
    }
  }
}

@media (max-width: 360px) {
  .completion-hero {
    grid-template-columns: 1fr;
  }

  .hero-mark {
    display: none;
  }

  .action-buttons {
    grid-template-columns: 1fr;
  }
}

/* Progressive mobile redesign: warm white + deep tea-green tokens only. */
.profile-completion-page.is-mobile-profile {
  color: var(--mobile-color-ink, #17211d);
  background: var(--mobile-color-page, #f4f5f2);
}

.profile-completion-page .completion-hero,
.profile-completion-page .completion-form-card,
.profile-completion-page .summary-card,
.profile-completion-page .profile-state-card,
.profile-completion-page .completion-actions {
  border-color: var(--mobile-color-line, #dde2de);
  border-radius: 16px;
  background: var(--mobile-color-surface, #fff);
  box-shadow: none;
  backdrop-filter: none;
}

.profile-completion-page .hero-mark {
  background: var(--mobile-color-primary, #0b6b53);
  box-shadow: none;
}

.profile-completion-page .section-heading strong,
.profile-completion-page .action-buttons ::v-deep .el-button--primary {
  color: #fff;
  border-color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary, #0b6b53);
}

.profile-completion-page .section-heading strong {
  color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary-soft, #e7f2ed);
}

.profile-completion-page .completion-form ::v-deep .el-input__inner,
.profile-completion-page .completion-form ::v-deep .el-textarea__inner {
  min-height: 48px;
  border-color: var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  font-size: 16px;
}

.profile-completion-page .completion-form ::v-deep .el-input__inner:focus,
.profile-completion-page .completion-form ::v-deep .el-textarea__inner:focus {
  border-color: var(--mobile-color-primary, #0b6b53);
  box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.2);
}

.profile-completion-page .action-buttons ::v-deep .el-button {
  min-height: 48px;
  font-weight: 700;
}
</style>
