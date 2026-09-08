<template>
  <div v-if="visible" class="mobile-hr-editor" role="dialog" aria-modal="true" aria-label="编辑员工档案">
    <button type="button" class="editor-backdrop" aria-label="关闭编辑" @click="$emit('close')" />
    <section class="editor-sheet">
      <header>
        <div><small>员工资料</small><h2>补全或修改档案</h2></div>
        <button type="button" class="icon-button" aria-label="关闭" @click="$emit('close')"><i class="el-icon-close" aria-hidden="true" /></button>
      </header>
      <div class="editor-body">
        <label>姓名<input v-model.trim="form.employeeName" /></label>
        <label>手机号<span class="current-value">当前：{{ maskedSensitiveValues.phoneNumber || '-' }}</span><input :value="form.phoneNumber" inputmode="tel" placeholder="输入新手机号；留空保持原值" @input="setSensitiveField('phoneNumber', $event.target.value)" /></label>
        <label>工号<input :value="form.employeeNo" disabled /></label>
        <label>员工状态<input :value="form.employeeStatus" disabled /></label>
        <label>合同到期日<input v-model="form.contractEndDate" type="date" /></label>
        <label>紧急联系人<input v-model.trim="form.emergencyContact" /></label>
        <label>紧急联系人电话<span class="current-value">当前：{{ maskedSensitiveValues.emergencyContactPhone || '-' }}</span><input :value="form.emergencyContactPhone" inputmode="tel" placeholder="输入新号码；留空保持原值" @input="setSensitiveField('emergencyContactPhone', $event.target.value)" /></label>
        <label>开户银行<input v-model.trim="form.bankName" /></label>
        <label>银行卡号<span class="current-value">当前：{{ maskedSensitiveValues.bankAccount || '-' }}</span><input :value="form.bankAccount" inputmode="numeric" placeholder="输入新卡号；留空保持原值" @input="setSensitiveField('bankAccount', $event.target.value)" /></label>
      </div>
      <footer>
        <button type="button" class="secondary" @click="$emit('close')">取消</button>
        <button type="button" class="primary" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
      </footer>
    </section>
  </div>
</template>

<script>
const NON_SENSITIVE_FIELDS = ["employeeName", "contractEndDate", "emergencyContact", "bankName"]
const SENSITIVE_FIELDS = ["phoneNumber", "emergencyContactPhone", "bankAccount"]
const MASK_PATTERN = /[\*＊•●○◯◎◉◌◍◦∙]/

export default {
  name: "MobileHrProfileEditor",
  props: {
    visible: { type: Boolean, default: false },
    detail: { type: Object, default: null },
    saving: { type: Boolean, default: false }
  },
  data() {
    return {
      form: { userId: undefined },
      initialForm: {},
      maskedSensitiveValues: {},
      dirtySensitiveFields: {}
    }
  },
  watch: {
    visible(value) { if (value) this.reset() },
    detail() { if (this.visible) this.reset() }
  },
  methods: {
    reset() {
      const detail = this.detail || {}
      this.form = {
        userId: detail.userId,
        employeeName: this.detailValue(detail, "employeeName") || "",
        employeeNo: this.detailValue(detail, "employeeNo") || "",
        employeeStatus: this.detailValue(detail, "employeeStatus") || "",
        contractEndDate: this.detailValue(detail, "contractEndDate") || "",
        emergencyContact: this.detailValue(detail, "emergencyContact") || "",
        bankName: this.detailValue(detail, "bankName") || "",
        phoneNumber: "",
        emergencyContactPhone: "",
        bankAccount: ""
      }
      this.initialForm = { ...this.form }
      this.maskedSensitiveValues = {
        phoneNumber: detail.phoneNumberMasked || "",
        emergencyContactPhone: detail.emergencyContactPhoneMasked || "",
        bankAccount: detail.bankAccountMasked || ""
      }
      this.dirtySensitiveFields = {}
    },
    detailValue(detail, key) {
      const fields = detail.fields || {}
      const profile = detail.profile || {}
      if (fields[key] !== undefined && fields[key] !== null) return fields[key]
      if (profile[key] !== undefined && profile[key] !== null) return profile[key]
      if (key === "employeeName") return detail.employeeName || detail.nickName || detail.userName
      return detail[key]
    },
    setSensitiveField(key, value) {
      if (!SENSITIVE_FIELDS.includes(key)) return
      this.form[key] = value
      if (this.$set) this.$set(this.dirtySensitiveFields, key, true)
      else this.dirtySensitiveFields[key] = true
    },
    buildPatch() {
      const patch = {}
      NON_SENSITIVE_FIELDS.forEach(key => {
        if (this.form[key] !== this.initialForm[key]) patch[key] = this.form[key]
      })
      SENSITIVE_FIELDS.forEach(key => {
        if (!this.dirtySensitiveFields[key]) return
        const value = this.form[key]
        if (typeof value === "string" && MASK_PATTERN.test(value)) return
        patch[key] = value
      })
      return patch
    },
    submit() {
      if (!this.form.userId) {
        this.$message.warning("未绑定账号的档案请在电脑端完成入职确认")
        return
      }
      this.$emit("save", {
        userId: this.form.userId,
        patch: this.buildPatch()
      })
    }
  }
}
</script>

<style scoped>
.mobile-hr-editor{position:fixed;inset:0;z-index:2600;display:flex;align-items:flex-end}.editor-backdrop{position:absolute;inset:0;border:0;background:rgba(15,23,42,.48)}.editor-sheet{position:relative;width:100%;max-height:88vh;overflow:auto;padding:18px 16px calc(16px + env(safe-area-inset-bottom));border-radius:22px 22px 0 0;background:#fff}.editor-sheet header{display:flex;justify-content:space-between;align-items:flex-start}.editor-sheet h2{margin:3px 0 14px;font-size:20px}.editor-sheet small{color:#64748b}.icon-button{border:0;background:var(--mobile-color-surface-soft,#f8f8f5);border-radius:12px;width:44px;height:44px;font-size:20px}.editor-body{display:grid;gap:12px}.editor-body label{display:grid;gap:6px;color:var(--mobile-color-muted,#66736d);font-size:13px}.editor-body input{min-height:44px;height:44px;padding:0 12px;border:1px solid var(--mobile-color-line-strong,#cbd3ce);border-radius:12px;font-size:16px}.editor-sheet footer{display:grid;grid-template-columns:1fr 2fr;gap:10px;margin-top:18px}.editor-sheet footer button{height:44px;border:0;border-radius:12px;font-weight:600}.secondary{background:#f1f5f9;color:#334155}.primary{background:#2563eb;color:#fff}.primary:disabled{opacity:.6}
.editor-sheet{border-radius:var(--mobile-radius-lg) var(--mobile-radius-lg) 0 0;background:var(--mobile-color-surface)}
.editor-sheet small,.editor-body label{color:var(--mobile-color-muted)}
.icon-button{width:44px;height:44px;color:var(--mobile-color-primary);background:var(--mobile-color-primary-soft);font-size:20px}
.editor-body input{height:44px;border-color:var(--mobile-color-line-strong);color:var(--mobile-color-ink);font-size:16px}
.secondary{color:var(--mobile-color-ink);background:var(--mobile-color-surface-soft)}
.primary{color:#fff;background:var(--mobile-color-primary)}
.mobile-hr-editor button:focus-visible,.mobile-hr-editor input:focus-visible{outline:3px solid rgba(11,107,83,.24);outline-offset:2px}
</style>
