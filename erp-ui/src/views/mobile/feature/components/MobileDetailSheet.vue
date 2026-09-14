<template>
  <section v-if="item" ref="detailMask" class="detail-mask" @click.self="$emit('close')">
    <article ref="detailDialog" class="glass-panel detail-sheet mobile-system-sheet" role="dialog" aria-modal="true" aria-label="单据详情" tabindex="-1">
      <header class="detail-head mobile-system-sheet__header">
        <div>
          <span>{{ feature.heading }}</span>
          <h2 ref="detailTitle" tabindex="-1">{{ item.title }}</h2>
        </div>
        <button type="button" aria-label="关闭" @click="$emit('close')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
        </button>
      </header>
      <div class="detail-sheet-body mobile-system-sheet__body">
        <image-gallery v-if="item.imageUrls" :value="item.imageUrls" />
        <dl class="detail-list">
          <div>
            <dt>单号</dt>
            <dd>{{ item.code || "-" }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ item.status || "-" }}</dd>
          </div>
          <div>
            <dt>摘要</dt>
            <dd>{{ item.detail || "-" }}</dd>
          </div>
          <div v-for="field in detailFields" :key="field.label">
            <dt>{{ field.label }}</dt>
            <dd>{{ field.value }}</dd>
          </div>
        </dl>
        <div v-if="detailLoading" class="detail-loading" role="status" aria-live="polite">正在加载完整详情...</div>
        <section v-if="detailSections.length" class="detail-section-list" aria-label="明细">
          <section v-for="section in detailSections" :key="section.title" class="detail-section">
            <h3>{{ section.title }}</h3>
            <article
              v-for="(row, index) in section.rows"
              :key="section.title + index"
              class="detail-section-row"
              :aria-current="row.current ? 'step' : null"
            >
              <div>
                <strong>{{ row.title }}</strong>
                <span>{{ row.meta || "-" }}</span>
                <small v-if="row.extra">{{ row.extra }}</small>
              </div>
              <em>{{ row.value || "-" }}</em>
            </article>
          </section>
        </section>
        <details v-if="showOtherActions" class="detail-other-actions">
          <summary>其他操作</summary>
          <div v-if="secondaryActions.length" class="detail-action-list detail-secondary-action-list">
            <button
              v-for="action in secondaryActions"
              :key="action.id"
              :class="['detail-action-button', action.tone]"
              type="button"
              :disabled="isActionDisabled(action)"
              @click="$emit('action', action)"
            >
              {{ actionButtonLabel(action) }}
            </button>
          </div>
          <div v-if="primaryActions.length" class="detail-utility-actions">
            <button type="button" @click="$emit('refresh')">刷新列表</button>
            <button type="button" @click="$emit('select-shop')">切换上下文</button>
          </div>
        </details>
      </div>
      <div v-if="actionLoadingKey || actionMessage" class="detail-action-feedback">
        <span v-if="actionLoadingKey" class="mobile-dialog-status" role="status" aria-live="polite">正在处理操作</span>
        <p
          v-if="actionMessage"
          class="detail-action-message"
          :role="actionMessageRole"
          :aria-live="actionMessageLive"
        >{{ actionMessage }}</p>
      </div>
      <footer v-if="detailLoadFailed" class="detail-actions detail-sheet-footer mobile-system-sheet__footer detail-retry-footer">
        <button type="button" @click="$emit('close')">关闭</button>
        <button type="button" class="primary" @click="$emit('retry-detail')">重新加载详情</button>
      </footer>
      <footer v-else-if="detailLoading" class="detail-actions detail-sheet-footer mobile-system-sheet__footer detail-loading-footer">
        <button type="button" @click="$emit('close')">关闭</button>
        <button type="button" class="primary" :disabled="detailLoading || detailLoadFailed">正在加载详情...</button>
      </footer>
      <footer v-else-if="primaryActions.length" class="detail-actions detail-sheet-footer mobile-system-sheet__footer detail-primary-footer">
        <button
          v-for="action in primaryActions"
          :key="action.id"
          :class="['detail-action-button', action.tone]"
          type="button"
          :disabled="isActionDisabled(action)"
          @click="$emit('action', action)"
        >
          {{ actionButtonLabel(action) }}
        </button>
      </footer>
      <footer v-else class="detail-actions detail-sheet-footer mobile-system-sheet__footer detail-utility-footer">
        <button type="button" @click="$emit('refresh')">刷新列表</button>
        <button type="button" class="primary" @click="$emit('select-shop')">切换上下文</button>
      </footer>
    </article>
  </section>
</template>

<script>
import ImageGallery from "@/components/ImageGallery"
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"

export default {
  name: "MobileDetailSheet",
  components: { ImageGallery },
  props: {
    feature: { type: Object, required: true },
    item: { type: Object, default: null },
    detailFields: { type: Array, default: () => [] },
    detailSections: { type: Array, default: () => [] },
    detailLoading: Boolean,
    detailLoadFailed: Boolean,
    primaryActions: { type: Array, default: () => [] },
    secondaryActions: { type: Array, default: () => [] },
    actionLoadingKey: { type: String, default: "" },
    actionMessage: { type: String, default: "" },
    actionMessageType: { type: String, default: "status" },
    iconPaths: { type: Object, required: true }
  },
  data() {
    return {
      dialogFocusManager: null
    }
  },
  computed: {
    showOtherActions() {
      return !this.detailLoading && !this.detailLoadFailed &&
        (this.primaryActions.length > 0 || this.secondaryActions.length > 0)
    },
    actionMessageRole() {
      return this.actionMessageType === "error" ? "alert" : "status"
    },
    actionMessageLive() {
      return this.actionMessageType === "error" ? "assertive" : "polite"
    }
  },
  watch: {
    item(value) {
      if (value) {
        this.$nextTick(() => {
          if (!this.item) return
          this.lockDetailSheetBody()
          this.activateDetailSheetFocus()
        })
      } else {
        this.deactivateDetailSheetFocus()
        this.releaseDetailSheetBodyLock()
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.detailDialog,
      getInitialFocus: () => this.$refs.detailTitle || this.$refs.detailDialog,
      onEscape: () => this.$emit("close")
    })
  },
  mounted() {
    if (this.item) {
      this.$nextTick(() => {
        if (!this.item) return
        this.lockDetailSheetBody()
        this.activateDetailSheetFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateDetailSheetFocus()
    this.releaseDetailSheetBodyLock()
  },
  methods: {
    isActionDisabled(action) {
      return this.detailLoading || this.detailLoadFailed || !!this.actionLoadingKey || !action || action.disabled === true
    },
    actionButtonLabel(action) {
      if (this.actionLoadingKey === action.id) return "处理中..."
      if (this.detailLoading) return "正在加载详情..."
      return action.label
    },
    lockDetailSheetBody() {
      mountMobileOverlay("mobile-detail-sheet-open")
    },
    releaseDetailSheetBodyLock() {
      releaseMobileOverlay("mobile-detail-sheet-open")
    },
    activateDetailSheetFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateDetailSheetFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    }
  }
}
</script>

<style scoped lang="scss">
@import "./mobileSheet.scss";
</style>

<style lang="scss">
body.mobile-detail-sheet-open {
  overflow: hidden;
}

body.mobile-detail-sheet-open .bottom-nav {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}
</style>
