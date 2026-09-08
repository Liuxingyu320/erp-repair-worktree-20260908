<template>
  <section v-if="open" ref="confirmMask" class="detail-mask mobile-confirm-mask" @click.self="$emit('cancel')">
    <article ref="confirmDialog" class="glass-panel mobile-confirm-sheet" role="dialog" aria-modal="true" aria-label="确认操作" :aria-busy="busy ? 'true' : 'false'" tabindex="-1">
      <div class="mobile-confirm-head">
        <span>{{ title }}</span>
        <button type="button" aria-label="关闭" :disabled="busy" @click="$emit('cancel')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
        </button>
      </div>
      <p>{{ message }}</p>
      <div class="mobile-confirm-actions">
        <button ref="cancelButton" type="button" :disabled="busy" @click="$emit('cancel')">{{ cancelText }}</button>
        <button type="button" class="primary" :disabled="busy" @click="$emit('confirm')">
          {{ busy ? '处理中…' : confirmText }}
        </button>
      </div>
    </article>
  </section>
</template>

<script>
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"

export default {
  name: "MobileConfirmDialog",
  props: {
    open: Boolean,
    title: { type: String, default: "确认操作" },
    message: { type: String, default: "" },
    confirmText: { type: String, default: "确定" },
    cancelText: { type: String, default: "取消" },
    busy: { type: Boolean, default: false },
    iconPaths: { type: Object, required: true }
  },
  data() {
    return {
      dialogFocusManager: null
    }
  },
  watch: {
    open(value) {
      if (value) {
        this.$nextTick(() => {
          if (!this.open) return
          this.lockConfirmDialogBody()
          this.activateConfirmDialogFocus()
        })
      } else {
        this.deactivateConfirmDialogFocus()
        this.releaseConfirmDialogBodyLock()
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.confirmDialog,
      getInitialFocus: () => this.$refs.cancelButton || this.$refs.confirmDialog,
      onEscape: () => this.$emit("cancel")
    })
  },
  mounted() {
    if (this.open) {
      this.$nextTick(() => {
        if (!this.open) return
        this.lockConfirmDialogBody()
        this.activateConfirmDialogFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateConfirmDialogFocus()
    this.releaseConfirmDialogBodyLock()
  },
  methods: {
    lockConfirmDialogBody() {
      mountMobileOverlay("mobile-confirm-dialog-open")
    },
    releaseConfirmDialogBodyLock() {
      releaseMobileOverlay("mobile-confirm-dialog-open")
    },
    activateConfirmDialogFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateConfirmDialogFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    }
  }
}
</script>

<style scoped lang="scss">
@import "./mobileSheet.scss";

.mobile-confirm-mask {
  z-index: 10060;
  align-items: center;
  padding:
    max(18px, env(safe-area-inset-top))
    18px
    max(18px, env(safe-area-inset-bottom));
}

.mobile-confirm-sheet {
  width: min(100%, 340px);
  box-sizing: border-box;
  border-radius: 24px;
  padding: 18px;
}

.mobile-confirm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mobile-confirm-head span {
  color: #122019;
  font-size: 18px;
  font-weight: 900;
}

.mobile-confirm-head button {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #4e6258;
  background: rgba(255, 255, 255, 0.58);
}

.mobile-confirm-head svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.mobile-confirm-sheet p {
  margin: 16px 0 0;
  color: #122019;
  font-size: 16px;
  line-height: 1.5;
  font-weight: 800;
}

.mobile-confirm-actions {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mobile-confirm-actions button {
  min-height: 48px;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  background: var(--mobile-color-surface, #fff);
  color: var(--mobile-color-ink, #17211d);
  font-size: 16px;
  line-height: 1.25;
  font-weight: 600;
}

.mobile-confirm-actions .primary {
  color: #fff;
  background: var(--mobile-color-primary, #0b6b53);
  border-color: var(--mobile-color-primary, #0b6b53);
}

.mobile-confirm-sheet button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.mobile-confirm-sheet button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>

<style lang="scss">
body.mobile-confirm-dialog-open {
  overflow: hidden;
}
</style>
