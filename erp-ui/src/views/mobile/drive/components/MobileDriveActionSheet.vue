<template>
  <section
    v-if="visible"
    class="detail-mask mobile-drive-action-mask"
    @click.self="$emit('close')"
  >
    <article
      ref="dialog"
      class="glass-panel mobile-drive-action-sheet"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-drive-action-title"
      tabindex="-1"
    >
      <header>
        <div>
          <span>文件操作</span>
          <h2 id="mobile-drive-action-title">{{ node ? node.nodeName : '更多操作' }}</h2>
        </div>
        <button ref="closeButton" type="button" aria-label="关闭更多操作" @click="$emit('close')">关闭</button>
      </header>

      <div class="mobile-drive-action-sheet__actions">
        <button v-if="node && node.nodeType === 'FILE'" type="button" @click="$emit('download')">下载文件</button>
        <button v-if="node && node.canWrite" type="button" @click="$emit('rename')">重命名</button>
        <button v-if="node && node.canWrite" type="button" @click="$emit('move')">移动到其他文件夹</button>
        <button v-if="node && node.canDelete" class="is-danger" type="button" @click="$emit('trash')">
          移入回收站
        </button>
      </div>
    </article>
  </section>
</template>

<script>
import { mountMobileOverlay, releaseMobileOverlay } from '../../feature/components/mobileOverlayStack'
import { createMobileDialogFocusManager } from '../../feature/components/mobileDialogFocus'

const OVERLAY_CLASS = 'mobile-drive-action-sheet-open'

export default {
  name: 'MobileDriveActionSheet',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null }
  },
  data() {
    return {
      focusManager: null
    }
  },
  watch: {
    visible(value) {
      if (value) this.openOverlay()
      else this.closeOverlay()
    }
  },
  created() {
    this.focusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.dialog,
      getInitialFocus: () => this.$refs.closeButton || this.$refs.dialog,
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
      mountMobileOverlay(OVERLAY_CLASS)
      this.$nextTick(() => {
        if (this.visible && this.focusManager) this.focusManager.activate()
      })
    },
    closeOverlay() {
      if (this.focusManager) this.focusManager.deactivate()
      releaseMobileOverlay(OVERLAY_CLASS)
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../feature/components/mobileSheet.scss";

.mobile-drive-action-mask {
  z-index: 10070;
  padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
}

.mobile-drive-action-sheet {
  width: min(100%, 430px);
  padding: 0 16px calc(14px + env(safe-area-inset-bottom, 0px));
  border-radius: 22px 22px 0 0;
}

.mobile-drive-action-sheet header {
  min-height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e8edf3;
}

.mobile-drive-action-sheet header div { min-width: 0; }
.mobile-drive-action-sheet header span { color: #748397; font-size: 11px; font-weight: 700; }
.mobile-drive-action-sheet h2 { margin: 3px 0 0; overflow: hidden; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-action-sheet button { min-height: 44px; border: 0; border-radius: 12px; font: inherit; font-weight: 700; }
.mobile-drive-action-sheet header button { padding: 0 12px; background: #e4f1ef; color: #256d65; }
.mobile-drive-action-sheet__actions { display: grid; gap: 8px; padding-top: 12px; }
.mobile-drive-action-sheet__actions button { width: 100%; padding: 0 14px; background: #f0f7f5; color: #24534e; text-align: left; }
.mobile-drive-action-sheet__actions button.is-danger { background: #fff0f0; color: #b43f3f; }
</style>
