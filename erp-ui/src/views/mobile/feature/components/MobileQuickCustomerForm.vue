<template>
  <section v-if="open" ref="quickCustomerMask" class="quick-customer-mask" @click.self="requestClose">
    <article ref="quickCustomerDialog" class="quick-customer-sheet mobile-system-sheet" role="dialog" aria-modal="true" aria-labelledby="quick-customer-title" tabindex="-1">
      <header class="quick-customer-header mobile-system-sheet__header">
        <div>
          <span>销售开单</span>
          <h3 id="quick-customer-title">新建客户</h3>
        </div>
        <button type="button" aria-label="关闭新建客户" :disabled="saving" @click="requestClose"><i class="el-icon-close" aria-hidden="true" /></button>
      </header>
      <form class="quick-customer-form" @submit.prevent="submitForm">
        <div class="quick-customer-body mobile-system-sheet__body">
          <label>
            <span>客户名称</span>
            <input
              ref="customerNameInput"
              v-model.trim="localData.customerName"
              type="text"
              required
              maxlength="128"
              autocomplete="organization"
              placeholder="请输入客户名称"
              @input="emitInput"
            >
          </label>
          <label>
            <span>联系人</span>
            <input
              v-model.trim="localData.contactPerson"
              type="text"
              maxlength="64"
              autocomplete="name"
              placeholder="选填"
              @input="emitInput"
            >
          </label>
          <label>
            <span>联系电话</span>
            <input
              v-model.trim="localData.contactPhone"
              type="tel"
              maxlength="32"
              autocomplete="tel"
              inputmode="tel"
              placeholder="选填"
              @input="emitInput"
            >
          </label>
          <p v-if="error" class="quick-customer-error" role="alert" aria-live="assertive">{{ error }}</p>
          <span v-if="saving" class="quick-customer-status" role="status" aria-live="polite">正在创建客户</span>
        </div>
        <footer class="quick-customer-footer mobile-system-sheet__footer">
          <button type="button" :disabled="saving" @click="requestClose">取消</button>
          <button type="submit" class="primary" :disabled="saving || !customerNameReady">
            {{ saving ? "创建中..." : "创建并选择" }}
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

export default {
  name: "MobileQuickCustomerForm",
  props: {
    open: Boolean,
    value: { type: Object, default: () => ({}) },
    saving: Boolean,
    error: { type: String, default: "" }
  },
  data() {
    return {
      localData: Object.assign({ customerName: "", contactPerson: "", contactPhone: "" }, this.value),
      dialogFocusManager: null,
      quickCustomerPortalAnchor: null
    }
  },
  computed: {
    customerNameReady() {
      return !!String(this.localData.customerName || "").trim()
    }
  },
  watch: {
    open(value) {
      if (value) {
        this.localData = Object.assign({ customerName: "", contactPerson: "", contactPhone: "" }, this.value)
        this.$nextTick(() => {
          if (!this.open) return
          mountMobileOverlay("mobile-quick-customer-open")
          this.portalQuickCustomerToBody()
          this.activateQuickCustomerFocus()
        })
      } else {
        this.deactivateQuickCustomerFocus()
        this.restoreQuickCustomerMount()
        releaseMobileOverlay("mobile-quick-customer-open")
      }
    },
    value: {
      deep: true,
      handler(value) {
        this.localData = Object.assign({ customerName: "", contactPerson: "", contactPhone: "" }, value || {})
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.quickCustomerDialog,
      getInitialFocus: () => this.getQuickCustomerInitialFocus(),
      onEscape: () => this.requestClose()
    })
  },
  mounted() {
    if (this.open) {
      this.$nextTick(() => {
        if (!this.open) return
        mountMobileOverlay("mobile-quick-customer-open")
        this.portalQuickCustomerToBody()
        this.activateQuickCustomerFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateQuickCustomerFocus()
    releaseMobileOverlay("mobile-quick-customer-open")
    this.restoreQuickCustomerMount()
  },
  methods: {
    emitInput() {
      this.$emit("input", Object.assign({}, this.localData))
    },
    submitForm() {
      this.emitInput()
      this.$emit("submit")
    },
    getQuickCustomerInitialFocus() {
      return this.$refs.customerNameInput || this.$refs.quickCustomerDialog
    },
    activateQuickCustomerFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateQuickCustomerFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    },
    portalQuickCustomerToBody() {
      if (this.quickCustomerPortalAnchor) return false
      this.quickCustomerPortalAnchor = portalMobileOverlay(this.$refs.quickCustomerMask)
      return !!this.quickCustomerPortalAnchor
    },
    restoreQuickCustomerMount() {
      if (!this.quickCustomerPortalAnchor) return false
      const restored = restoreMobileOverlay(this.$refs.quickCustomerMask, this.quickCustomerPortalAnchor)
      this.quickCustomerPortalAnchor = null
      return restored
    },
    requestClose() {
      if (!this.saving) this.$emit("close")
    }
  }
}
</script>

<style scoped lang="scss">
.quick-customer-mask {
  position: fixed;
  z-index: 10050;
  inset: 0;
  width: 100vw;
  height: var(--mobile-viewport-height, 100dvh);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding:
    max(12px, var(--mobile-safe-top, env(safe-area-inset-top, 0px)))
    12px
    max(12px, var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)));
  background: rgba(23, 33, 29, 0.36);
  box-sizing: border-box;
}

.quick-customer-sheet {
  width: min(100%, 420px);
  max-height: 100%;
  padding: 0;
  border: 1px solid var(--mobile-color-line, #dde2de);
  border-radius: var(--mobile-radius-lg, 16px);
  background: var(--mobile-color-surface, #fff);
  box-shadow: var(--mobile-shadow-sheet, 0 16px 40px rgba(23, 33, 29, 0.16));
  box-sizing: border-box;
}

.quick-customer-header,
.quick-customer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quick-customer-header {
  flex: 0 0 auto;
  padding: 16px;
  border-bottom: 1px solid var(--mobile-color-line, #dde2de);
}

.quick-customer-header span,
label span {
  color: var(--mobile-color-muted, #66736d);
  font-size: 13px;
  font-weight: 800;
}

h3 {
  margin: 3px 0 0;
  color: var(--mobile-color-ink, #17211d);
  font-size: 22px;
}

.quick-customer-header button {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: var(--mobile-radius-md, 12px);
  background: var(--mobile-color-surface-soft, #f8f8f5);
  color: var(--mobile-color-muted, #66736d);
  font-size: 24px;
}

.quick-customer-form {
  min-height: 0;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.quick-customer-body {
  min-height: 0;
  display: grid;
  flex: 1 1 auto;
  gap: 14px;
  padding: 16px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  scroll-padding-bottom: calc(68px + var(--mobile-keyboard-inset, 0px));
}

label {
  display: grid;
  gap: 7px;
}

input,
.quick-customer-footer button {
  min-height: 46px;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: var(--mobile-radius-md, 12px);
  background: var(--mobile-color-surface, #fff);
  color: var(--mobile-color-ink, #17211d);
  font: inherit;
  font-size: 16px;
  box-sizing: border-box;
}

input {
  width: 100%;
  padding: 0 12px;
}

.quick-customer-error {
  margin: 0;
  color: #b42318;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 800;
}

.quick-customer-status {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.quick-customer-footer {
  justify-content: flex-end;
  flex: 0 0 auto;
  padding: 12px 16px max(12px, var(--mobile-safe-bottom, env(safe-area-inset-bottom, 0px)));
  border-top: 1px solid var(--mobile-color-line, #dde2de);
}

.quick-customer-footer button {
  padding: 0 16px;
  font-weight: 900;
}

.quick-customer-footer button.primary {
  border-color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary, #0b6b53);
  color: #fff;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}
</style>
