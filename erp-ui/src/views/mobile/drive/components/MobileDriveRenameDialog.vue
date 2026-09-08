<template>
  <section
    v-if="visible"
    class="detail-mask mobile-drive-rename-mask"
    @click.self="$emit('close')"
  >
    <article
      ref="dialog"
      class="glass-panel mobile-drive-rename-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-drive-rename-title"
      tabindex="-1"
    >
      <header>
        <h2 id="mobile-drive-rename-title">重命名</h2>
        <button type="button" aria-label="关闭重命名" @click="$emit('close')">关闭</button>
      </header>
      <label for="mobile-drive-rename-input">新名称</label>
      <input
        id="mobile-drive-rename-input"
        ref="nameInput"
        v-model="name"
        type="text"
        maxlength="200"
        autocomplete="off"
        @keyup.enter="submit"
      >
      <p v-if="error" role="alert">{{ error }}</p>
      <footer>
        <button type="button" :disabled="busy" @click="$emit('close')">取消</button>
        <button type="button" class="is-primary" :disabled="busy || !trimmedName" @click="submit">
          {{ busy ? '保存中…' : '保存' }}
        </button>
      </footer>
    </article>
  </section>
</template>

<script>
import { mountMobileOverlay, releaseMobileOverlay } from '../../feature/components/mobileOverlayStack'
import { createMobileDialogFocusManager } from '../../feature/components/mobileDialogFocus'

const OVERLAY_CLASS = 'mobile-drive-rename-dialog-open'

export default {
  name: 'MobileDriveRenameDialog',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null },
    busy: { type: Boolean, default: false },
    error: { type: String, default: '' }
  },
  data() {
    return {
      name: '',
      focusManager: null
    }
  },
  computed: {
    trimmedName() {
      return String(this.name || '').trim()
    }
  },
  watch: {
    visible(value) {
      if (value) this.openOverlay()
      else this.closeOverlay()
    },
    node: {
      immediate: true,
      handler(value) {
        this.name = value && value.nodeName ? String(value.nodeName) : ''
      }
    }
  },
  created() {
    this.focusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.dialog,
      getInitialFocus: () => this.$refs.nameInput || this.$refs.dialog,
      onEscape: () => this.$emit('close')
    })
  },
  mounted() {
    if (this.visible) this.openOverlay()
  },
  beforeDestroy() {
    this.closeOverlay()
  },
  methods: {
    openOverlay() {
      this.name = this.node && this.node.nodeName ? String(this.node.nodeName) : ''
      mountMobileOverlay(OVERLAY_CLASS)
      this.$nextTick(() => {
        if (this.visible && this.focusManager) this.focusManager.activate()
      })
    },
    closeOverlay() {
      if (this.focusManager) this.focusManager.deactivate()
      releaseMobileOverlay(OVERLAY_CLASS)
    },
    submit() {
      if (this.busy || !this.trimmedName) return
      this.$emit('confirm', this.trimmedName)
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../feature/components/mobileSheet.scss";

.mobile-drive-rename-mask {
  z-index: 10080;
  align-items: center;
  padding-bottom: max(18px, env(safe-area-inset-bottom, 0px));
}

.mobile-drive-rename-dialog {
  width: min(100%, 360px);
  padding: 18px;
  border-radius: 22px;
}

.mobile-drive-rename-dialog header,
.mobile-drive-rename-dialog footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.mobile-drive-rename-dialog h2 { margin: 0; font-size: 19px; }
.mobile-drive-rename-dialog label { display: block; margin: 18px 0 7px; color: #61738a; font-size: 12px; font-weight: 700; }
.mobile-drive-rename-dialog input { width: 100%; min-height: 44px; box-sizing: border-box; padding: 0 12px; border: 1px solid #cfdbe8; border-radius: 12px; font: inherit; }
.mobile-drive-rename-dialog p { margin: 8px 0 0; color: #b43f3f; font-size: 12px; }
.mobile-drive-rename-dialog button { min-height: 44px; padding: 0 14px; border: 0; border-radius: 12px; background: #e4f1ef; color: #256d65; font: inherit; font-weight: 700; }
.mobile-drive-rename-dialog footer { justify-content: flex-end; margin-top: 18px; }
.mobile-drive-rename-dialog button.is-primary { background: #278a7d; color: #fff; }
.mobile-drive-rename-dialog button:disabled { opacity: 0.55; }
</style>
