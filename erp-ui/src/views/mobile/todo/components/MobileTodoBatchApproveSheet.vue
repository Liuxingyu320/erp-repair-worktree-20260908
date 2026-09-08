<template>
  <section
    v-if="visible"
    class="detail-mask mobile-todo-batch-mask"
    @click.self="requestClose"
  >
    <article
      ref="dialog"
      class="glass-panel detail-sheet mobile-todo-batch-sheet"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-todo-batch-title"
      :aria-busy="executing ? 'true' : 'false'"
      tabindex="-1"
    >
      <header class="detail-head">
        <div>
          <span>当前已加载待办</span>
          <h2 id="mobile-todo-batch-title">批量通过 {{ total || items.length }} 条</h2>
        </div>
        <button type="button" aria-label="关闭批量通过" :disabled="executing" @click="requestClose">×</button>
      </header>

      <div class="detail-sheet-body mobile-todo-batch-body">
        <p class="mobile-todo-batch-notice">每条审批独立提交；部分失败不会撤销已经成功的审批。</p>
        <p v-if="errorMessage" class="mobile-todo-batch-error" role="alert">{{ errorMessage }}</p>

        <div v-if="executing || results.length" class="mobile-todo-batch-progress" role="status" aria-live="polite">
          <strong>已处理 {{ processed }}/{{ total }}</strong>
          <span><i :style="{ width: progressPercentage + '%' }"></i></span>
        </div>

        <ol class="mobile-todo-batch-items" aria-label="批量审批项目">
          <li v-for="item in displayItems" :key="item.requestId || item.todoKey">
            <div>
              <strong>{{ item.businessNo || item.row && (item.row.businessNo || item.row.title) || '未记录单号' }}</strong>
              <small>{{ direction(item.row) }}</small>
            </div>
            <span v-if="results.length" :class="['result', resultTone(item.status)]">{{ resultLabel(item) }}</span>
          </li>
        </ol>

        <label v-if="!results.length" class="mobile-todo-batch-comment">
          <span>统一通过意见（选填）</span>
          <textarea v-model.trim="comment" rows="3" maxlength="500" :disabled="executing" placeholder="将应用到本批全部单据" />
          <small>{{ comment.length }}/500</small>
        </label>
      </div>

      <footer class="detail-sheet-footer mobile-todo-batch-footer">
        <button v-if="executing" ref="stopButton" type="button" class="warning" @click="$emit('stop')">停止未开始项</button>
        <button ref="closeButton" type="button" :disabled="executing" @click="requestClose">{{ results.length ? '关闭' : '取消' }}</button>
        <button v-if="canRetry" ref="retryButton" type="button" class="warning" :disabled="executing" @click="retry">重试未成功项</button>
        <button v-if="!results.length" ref="confirmButton" type="button" class="primary" :disabled="executing || !items.length" @click="confirm">
          {{ executing ? '处理中…' : `确认通过 ${items.length} 条` }}
        </button>
      </footer>
    </article>
  </section>
</template>

<script>
import { createMobileDialogFocusManager } from '../../feature/components/mobileDialogFocus'
import { mountMobileOverlay, releaseMobileOverlay } from '../../feature/components/mobileOverlayStack'

export default {
  name: 'MobileTodoBatchApproveSheet',
  props: {
    visible: { type: Boolean, default: false },
    items: { type: Array, default: () => [] },
    executing: { type: Boolean, default: false },
    processed: { type: Number, default: 0 },
    total: { type: Number, default: 0 },
    results: { type: Array, default: () => [] },
    errorMessage: { type: String, default: '' }
  },
  data() {
    return {
      comment: '',
      dialogFocusManager: null
    }
  },
  computed: {
    displayItems() {
      return this.results.length ? this.results : this.items
    },
    progressPercentage() {
      return this.total > 0 ? Math.min(100, Math.round((this.processed / this.total) * 100)) : 0
    },
    canRetry() {
      return this.results.some(item =>
        (item.status === 'FAILED' && !['SESSION', 'FORBIDDEN', 'VALIDATION'].includes(item.errorKind)) ||
        (item.status === 'CANCELED' && item.errorKind === 'USER_CANCELED')
      )
    }
  },
  watch: {
    visible(value) {
      if (value) {
        if (!this.results.length) this.comment = ''
        this.$nextTick(() => {
          if (!this.visible) return
          mountMobileOverlay('mobile-todo-batch-approve-open')
          this.activateFocus()
        })
      } else {
        this.deactivateFocus()
        releaseMobileOverlay('mobile-todo-batch-approve-open')
      }
    },
    executing(value) {
      if (!value && this.visible) this.$nextTick(() => this.focusPrimaryAction())
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.dialog,
      getInitialFocus: () => this.primaryActionElement() || this.$refs.closeButton || this.$refs.dialog,
      onEscape: () => this.requestClose()
    })
  },
  mounted() {
    if (this.visible) {
      this.$nextTick(() => {
        if (!this.visible) return
        mountMobileOverlay('mobile-todo-batch-approve-open')
        this.activateFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateFocus()
    releaseMobileOverlay('mobile-todo-batch-approve-open')
  },
  methods: {
    primaryActionElement() {
      return this.$refs.retryButton || this.$refs.confirmButton || this.$refs.stopButton || null
    },
    focusPrimaryAction() {
      const element = this.primaryActionElement()
      if (element && typeof element.focus === 'function') element.focus()
    },
    activateFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    },
    requestClose() {
      if (!this.executing) this.$emit('close')
    },
    confirm() {
      if (!this.executing && this.items.length) this.$emit('confirm', { comment: this.comment })
    },
    retry() {
      if (!this.executing && this.canRetry) this.$emit('retry', { comment: this.comment })
    },
    direction(row = {}) {
      const params = row.routeParams || {}
      const from = row.fromDeptName || params.fromDeptName || '调出组织'
      const to = row.toDeptName || params.toDeptName || '调入组织'
      return `${from} → ${to}`
    },
    resultTone(status) {
      if (status === 'SUCCESS' || status === 'ALREADY_HANDLED') return 'success'
      if (status === 'CANCELED') return 'muted'
      return 'danger'
    },
    resultLabel(item) {
      if (item.status === 'SUCCESS') return '通过'
      if (item.status === 'ALREADY_HANDLED') return '已处理'
      if (item.status === 'CANCELED') return item.message || '未开始'
      return item.message || '未成功'
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../feature/components/mobileSheet.scss";

.mobile-todo-batch-mask { z-index: 10070; }
.mobile-todo-batch-sheet { width: min(100%, 440px); }
.detail-head button { font-size: 28px; line-height: 1; }
.mobile-todo-batch-body { padding-top: 12px; }
.mobile-todo-batch-notice, .mobile-todo-batch-error {
  margin: 0 0 12px;
  border-radius: 12px;
  padding: 11px 12px;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 700;
}
.mobile-todo-batch-notice { border: 1px solid #f5d38a; background: #fff8e8; color: #8a5a00; }
.mobile-todo-batch-error { border: 1px solid #fecaca; background: #fff1f2; color: #b42318; }
.mobile-todo-batch-progress { display: grid; gap: 7px; margin-bottom: 12px; color: #24352e; font-size: 13px; }
.mobile-todo-batch-progress > span { height: 8px; overflow: hidden; border-radius: 999px; background: #dfe8e4; }
.mobile-todo-batch-progress i { display: block; height: 100%; border-radius: inherit; background: #0b6b53; transition: width .18s ease; }
.mobile-todo-batch-items { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.mobile-todo-batch-items li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #dde2de;
  border-radius: 12px;
  padding: 10px 11px;
  background: #f8faf9;
}
.mobile-todo-batch-items li div { min-width: 0; display: grid; gap: 3px; }
.mobile-todo-batch-items strong, .mobile-todo-batch-items small { overflow-wrap: anywhere; }
.mobile-todo-batch-items small { color: #66736d; }
.mobile-todo-batch-items .result { max-width: 128px; border-radius: 999px; padding: 5px 8px; font-size: 11px; font-weight: 800; text-align: center; }
.mobile-todo-batch-items .success { background: #e1f3ec; color: #0b6b53; }
.mobile-todo-batch-items .danger { background: #fff1f2; color: #b42318; }
.mobile-todo-batch-items .muted { background: #eef1ef; color: #52635b; }
.mobile-todo-batch-comment { display: grid; gap: 7px; margin-top: 14px; }
.mobile-todo-batch-comment > span { color: #24352e; font-size: 14px; font-weight: 800; }
.mobile-todo-batch-comment textarea {
  width: 100%; min-height: 88px; box-sizing: border-box; resize: vertical;
  border: 1px solid #cbd3ce; border-radius: 12px; padding: 11px 12px; color: #17211d; font: inherit;
}
.mobile-todo-batch-comment small { justify-self: end; color: #66736d; }
.mobile-todo-batch-footer { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.mobile-todo-batch-footer button {
  min-height: 48px; border: 1px solid #cbd3ce; border-radius: 12px; padding: 10px;
  background: #fff; color: #17211d; font-weight: 800;
}
.mobile-todo-batch-footer .primary { border-color: #0b6b53; background: #0b6b53; color: #fff; }
.mobile-todo-batch-footer .warning { border-color: #d99a22; background: #fff8e8; color: #8a5a00; }
.mobile-todo-batch-footer button:disabled { opacity: .58; }
</style>
