<template>
  <section v-if="open" ref="recordMask" class="detail-mask customer-record-mask" @click.self="requestClose">
    <article ref="recordDialog" class="glass-panel detail-sheet customer-record-sheet" role="dialog" aria-modal="true" aria-labelledby="customer-record-title" tabindex="-1">
      <header class="detail-head">
        <div>
          <span>{{ customerName }}</span>
          <h2 id="customer-record-title">追加服务记录</h2>
        </div>
        <button type="button" aria-label="关闭追加服务记录" :disabled="saving" @click="requestClose">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
        </button>
      </header>
      <form class="customer-record-form" @submit.prevent="submitForm">
        <div class="customer-record-body">
          <label>
            <span>服务日期</span>
            <input ref="serviceDateInput" v-model="form.serviceDate" type="datetime-local" required>
          </label>
          <label>
            <span>到店人数</span>
            <input v-model.number="form.partySize" type="number" min="1" step="1" inputmode="numeric" required>
          </label>
          <label>
            <span>本次茶饮</span>
            <input v-model.trim="form.teaServed" type="text" maxlength="500" placeholder="如：肉桂、熟普">
          </label>
          <label>
            <span>偏好观察</span>
            <textarea v-model.trim="form.preferenceSnapshot" rows="2" maxlength="1000" placeholder="记录本次确认或新增的偏好" />
          </label>
          <label>
            <span>注意事项</span>
            <textarea v-model.trim="form.cautionSnapshot" rows="2" maxlength="1000" placeholder="记录本次服务注意事项" />
          </label>
          <label>
            <span>服务备注</span>
            <textarea v-model.trim="form.serviceNote" rows="3" maxlength="1000" placeholder="本次服务过程与后续跟进建议" />
          </label>
          <label>
            <span>消费金额</span>
            <input v-model.number="form.consumptionAmount" type="number" min="0" step="0.01" inputmode="decimal" placeholder="选填">
          </label>
          <p v-if="localError || error" class="customer-record-error" role="alert" aria-live="assertive">
            {{ localError || error }}
          </p>
        </div>
        <footer class="customer-record-footer">
          <button type="button" :disabled="saving" @click="requestClose">取消</button>
          <button type="submit" class="primary" :disabled="saving">
            {{ saving ? "追加中..." : "追加记录" }}
          </button>
        </footer>
      </form>
    </article>
  </section>
</template>

<script>
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"
const { portalMobileOverlay, restoreMobileOverlay } = require("./mobileOverlayPortal")
const {
  createCustomerServiceRecordForm,
  validateCustomerServiceRecord
} = require("../mobileCustomerServiceRecord")

export default {
  name: "MobileCustomerRecordDialog",
  props: {
    open: Boolean,
    customer: { type: Object, default: null },
    saving: Boolean,
    error: { type: String, default: "" },
    iconPaths: { type: Object, required: true }
  },
  data() {
    return {
      form: createCustomerServiceRecordForm(),
      localError: "",
      dialogFocusManager: null,
      portalAnchor: null
    }
  },
  computed: {
    rawCustomer() {
      return this.customer && (this.customer._raw || this.customer.raw || this.customer) || {}
    },
    customerName() {
      return this.rawCustomer.customerName || this.customer && this.customer.title || "客户服务卡"
    }
  },
  watch: {
    open(value) {
      if (value) {
        this.resetForm()
        this.$nextTick(() => {
          if (!this.open) return
          mountMobileOverlay("mobile-customer-record-open")
          this.portalToBody()
          this.activateFocus()
        })
      } else {
        this.deactivateFocus()
        this.restoreMount()
        releaseMobileOverlay("mobile-customer-record-open")
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.recordDialog,
      getInitialFocus: () => this.$refs.serviceDateInput || this.$refs.recordDialog,
      onEscape: () => this.requestClose()
    })
  },
  mounted() {
    if (this.open) {
      this.$nextTick(() => {
        if (!this.open) return
        mountMobileOverlay("mobile-customer-record-open")
        this.portalToBody()
        this.activateFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateFocus()
    releaseMobileOverlay("mobile-customer-record-open")
    this.restoreMount()
  },
  methods: {
    resetForm() {
      this.form = createCustomerServiceRecordForm(this.rawCustomer)
      this.localError = ""
    },
    submitForm() {
      if (this.saving) return
      this.localError = validateCustomerServiceRecord(this.form)
      if (this.localError) return
      this.$emit("submit", Object.assign({}, this.form))
    },
    requestClose() {
      if (!this.saving) this.$emit("close")
    },
    portalToBody() {
      if (this.portalAnchor) return false
      this.portalAnchor = portalMobileOverlay(this.$refs.recordMask)
      return !!this.portalAnchor
    },
    restoreMount() {
      if (!this.portalAnchor) return false
      const restored = restoreMobileOverlay(this.$refs.recordMask, this.portalAnchor)
      this.portalAnchor = null
      return restored
    },
    activateFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    }
  }
}
</script>

<style scoped lang="scss">
@import "./mobileSheet.scss";

.customer-record-mask { z-index: 10055; }
.customer-record-sheet { max-height: 100%; }
.customer-record-form { min-height: 0; display: flex; flex: 1 1 auto; flex-direction: column; overflow: hidden; }
.customer-record-body { min-height: 0; flex: 1 1 auto; display: grid; gap: 14px; padding: 16px; overflow-x: hidden; overflow-y: auto; }
.customer-record-body label { display: grid; gap: 7px; }
.customer-record-body label span { color: var(--mobile-color-muted, #66736d); font-size: 13px; font-weight: 800; }
.customer-record-body input,
.customer-record-body textarea { width: 100%; min-height: 46px; box-sizing: border-box; border: 1px solid var(--mobile-color-line, #dde2de); border-radius: 12px; padding: 11px 12px; background: #fff; color: var(--mobile-color-ink, #17211d); font: inherit; }
.customer-record-body textarea { min-height: 72px; resize: vertical; }
.customer-record-error { margin: 0; color: #b42318; font-size: 13px; font-weight: 800; }
.customer-record-footer { flex: 0 0 auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 12px 16px max(12px, var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px))); border-top: 1px solid var(--mobile-color-line, #dde2de); background: #fff; }
.customer-record-footer button { min-height: 46px; border: 1px solid var(--mobile-color-line, #dde2de); border-radius: 12px; background: #fff; color: var(--mobile-color-ink, #17211d); font-size: 15px; font-weight: 900; }
.customer-record-footer button.primary { border-color: var(--mobile-color-primary, #0b6b53); background: var(--mobile-color-primary, #0b6b53); color: #fff; }
.customer-record-footer button:disabled { cursor: not-allowed; opacity: .55; }
</style>
