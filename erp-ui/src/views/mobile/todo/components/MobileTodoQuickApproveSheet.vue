<template>
  <section
    v-if="visible"
    class="detail-mask mobile-todo-quick-mask"
    @click.self="requestClose"
  >
    <article
      ref="dialog"
      class="glass-panel detail-sheet mobile-todo-quick-sheet"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-todo-quick-title"
      :aria-busy="loading || submitting ? 'true' : 'false'"
      tabindex="-1"
    >
      <header class="detail-head">
        <div>
          <span>调拨审批</span>
          <h2 id="mobile-todo-quick-title">快速通过确认</h2>
        </div>
        <button type="button" aria-label="关闭快速通过" :disabled="submitting" @click="requestClose">×</button>
      </header>

      <div class="detail-sheet-body mobile-todo-quick-body">
        <p v-if="loading" class="detail-loading" role="status">正在读取最新调拨详情…</p>
        <p v-else-if="effectiveErrorMessage" class="mobile-todo-approval-error" role="alert">{{ effectiveErrorMessage }}</p>
        <template v-else-if="preview">
          <dl class="detail-list mobile-todo-quick-summary">
            <div><dt>单号</dt><dd>{{ transferNo }}</dd></div>
            <div><dt>方向</dt><dd>{{ directionText }}</dd></div>
            <div><dt>数量</dt><dd>{{ quantityText }}</dd></div>
            <div v-if="amountText"><dt>金额</dt><dd>{{ amountText }}</dd></div>
            <div v-if="approvalNode"><dt>节点</dt><dd>{{ approvalNode }}</dd></div>
          </dl>

          <section class="mobile-todo-quick-detail" aria-labelledby="mobile-todo-quick-detail-title">
            <header class="mobile-todo-quick-detail-head">
              <h3 id="mobile-todo-quick-detail-title">调拨明细</h3>
              <span>共 {{ detailRows.length }} 项<span v-if="detailRows.length > 2"> · 上下滑动查看</span></span>
            </header>

            <ol
              v-if="detailRows.length"
              class="mobile-todo-quick-detail-list"
              role="list"
              aria-label="调拨货品明细，可上下滑动查看"
              tabindex="0"
            >
              <li v-for="(item, index) in detailRows" :key="detailRowKey(item, index)">
                <header class="mobile-todo-quick-detail-item-head">
                  <span class="mobile-todo-quick-detail-index" :aria-label="`序号 ${index + 1}`">{{ index + 1 }}</span>
                  <div>
                    <small>物料</small>
                    <strong>{{ display(item.itemName || item.productName) }}</strong>
                    <span>编码 {{ display(item.itemCode || item.productCode) }}</span>
                  </div>
                  <em>类型 · {{ itemTypeLabel(item.itemType) }}</em>
                </header>

                <dl class="mobile-todo-quick-detail-fields">
                  <div><dt>规格 / 等级</dt><dd>{{ specGrade(item) }}</dd></div>
                  <div><dt>申请数量</dt><dd>{{ quantity(item.quantity) }}</dd></div>
                  <div><dt>单位</dt><dd>{{ display(item.unit) }}</dd></div>
                  <div><dt>参考成本价</dt><dd>{{ money(item.costPrice) }}</dd></div>
                  <div class="mobile-todo-quick-detail-amount"><dt>小计</dt><dd>{{ money(lineAmount(item)) }}</dd></div>
                </dl>
              </li>
            </ol>

            <div v-else class="mobile-todo-quick-detail-empty" role="status">
              <strong>暂无调拨明细</strong>
              <span>请刷新待办后重试，或从“查看审批”进入完整详情核对。</span>
            </div>

            <footer v-if="detailRows.length" class="mobile-todo-quick-detail-total">
              <strong>合计</strong>
              <span>申请数量 {{ quantity(detailQuantityTotal) }}</span>
              <em>{{ money(detailTotal) }}</em>
            </footer>
          </section>

          <ul v-if="warnings.length" class="mobile-todo-approval-warnings" aria-label="审批提示">
            <li v-for="warning in warnings" :key="warning">{{ warning }}</li>
          </ul>

          <label class="mobile-todo-comment">
            <span>通过意见（选填）</span>
            <textarea
              v-model.trim="comment"
              rows="3"
              maxlength="500"
              placeholder="可不填写；退回和驳回仍必须填写原因"
              :disabled="submitting"
            />
            <small>{{ comment.length }}/500</small>
          </label>
        </template>
      </div>

      <footer class="detail-sheet-footer mobile-todo-quick-footer">
        <button ref="closeButton" type="button" :disabled="submitting" @click="requestClose">取消</button>
        <button
          v-if="retryAvailable && eligible"
          ref="retryButton"
          type="button"
          class="warning"
          :disabled="submitting"
          @click="retry"
        >{{ submitting ? '重试中…' : '用原请求号重试' }}</button>
        <button
          v-else-if="canSubmit"
          ref="confirmButton"
          type="button"
          class="primary"
          :disabled="submitting"
          @click="confirm(false)"
        >{{ submitting ? '提交中…' : '确认通过' }}</button>
        <button
          v-if="canSubmit && nextAvailable"
          ref="continueButton"
          type="button"
          class="primary continue"
          :disabled="submitting"
          @click="confirm(true)"
        >{{ submitting ? '提交中…' : '通过并处理下一条' }}</button>
      </footer>
    </article>
  </section>
</template>

<script>
import { createMobileDialogFocusManager } from '../../feature/components/mobileDialogFocus'
import { mountMobileOverlay, releaseMobileOverlay } from '../../feature/components/mobileOverlayStack'

export default {
  name: 'MobileTodoQuickApproveSheet',
  props: {
    visible: { type: Boolean, default: false },
    row: { type: Object, default: () => ({}) },
    preview: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    submitting: { type: Boolean, default: false },
    errorMessage: { type: String, default: '' },
    eligible: { type: Boolean, default: true },
    nextAvailable: { type: Boolean, default: false },
    retryAvailable: { type: Boolean, default: false }
  },
  data() {
    return {
      comment: '',
      dialogFocusManager: null
    }
  },
  computed: {
    effectiveErrorMessage() {
      return this.eligible
        ? this.errorMessage
        : '审批权限或待办状态已变化，请关闭后刷新待办。'
    },
    canSubmit() {
      return !!this.preview && this.detailRows.length > 0 && !this.loading && !this.effectiveErrorMessage
    },
    transferNo() {
      const preview = this.preview || {}
      return preview.orderNo || preview.transferNo || preview.businessNo || this.row.businessNo || '未记录单号'
    },
    directionText() {
      const preview = this.preview || {}
      const from = preview.fromDeptName || preview.fromWarehouseName || preview.sourceDeptName || '调出组织待确认'
      const to = preview.toDeptName || preview.toWarehouseName || preview.targetDeptName || '调入组织待确认'
      return `${from} → ${to}`
    },
    detailRows() {
      const values = this.preview && this.preview.details
      return Array.isArray(values) ? values : []
    },
    detailQuantityTotal() {
      return this.detailRows.reduce((total, item) => total + (this.numberOrNull(item && item.quantity) || 0), 0)
    },
    detailTotal() {
      const explicitTotal = this.numberOrNull(this.preview && this.preview.totalAmount)
      if (explicitTotal !== null) return explicitTotal
      const amounts = this.detailRows.map(item => this.lineAmount(item)).filter(value => value !== null)
      return amounts.length ? amounts.reduce((sum, value) => sum + value, 0) : null
    },
    quantityText() {
      const preview = this.preview || {}
      if (this.detailRows.length) {
        return `${this.quantity(this.detailQuantityTotal)}（${this.detailRows.length} 项）`
      }
      const fallback = this.numberOrNull(preview.totalQuantity ?? preview.quantity)
      return fallback !== null ? this.quantity(fallback) : '0 项'
    },
    amountText() {
      const preview = this.preview || {}
      const explicitAmount = this.numberOrNull(preview.totalAmount ?? preview.referenceAmount)
      const amount = explicitAmount !== null ? explicitAmount : this.detailTotal
      return amount === null ? '' : this.money(amount)
    },
    approvalNode() {
      const preview = this.preview || {}
      const summary = preview.approvalSummary || {}
      return summary.currentNodeName || preview.currentNodeName || ''
    },
    warnings() {
      const values = this.preview && Array.isArray(this.preview.approvalWarnings)
        ? this.preview.approvalWarnings
        : []
      return values.map(value => String(value || '').trim()).filter(Boolean)
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.comment = ''
        this.$nextTick(() => {
          if (!this.visible) return
          mountMobileOverlay('mobile-todo-quick-approve-open')
          this.activateFocus()
        })
      } else {
        this.deactivateFocus()
        releaseMobileOverlay('mobile-todo-quick-approve-open')
      }
    },
    submitting(value) {
      if (!value && this.visible) this.$nextTick(() => this.focusPrimaryAction())
    },
    retryAvailable(value) {
      if (value && this.visible) this.$nextTick(() => this.focusPrimaryAction())
    },
    'row.todoKey'() {
      this.comment = ''
      if (this.visible) this.$nextTick(() => this.focusPrimaryAction())
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
        mountMobileOverlay('mobile-todo-quick-approve-open')
        this.activateFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateFocus()
    releaseMobileOverlay('mobile-todo-quick-approve-open')
  },
  methods: {
    hasValue(value) {
      return value !== undefined && value !== null && String(value).trim() !== ''
    },
    display(value) {
      return this.hasValue(value) ? String(value) : '—'
    },
    numberOrNull(value) {
      if (!this.hasValue(value)) return null
      const number = Number(value)
      return Number.isFinite(number) ? number : null
    },
    quantity(value) {
      const number = this.numberOrNull(value)
      return number === null ? '—' : number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    },
    money(value) {
      const number = this.numberOrNull(value)
      return number === null
        ? '无权限或暂无数据'
        : `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    },
    itemTypeLabel(value) {
      return { product: '商品', gift: '礼盒', oe: 'OE器皿' }[String(value || '').toLowerCase()] || this.display(value)
    },
    specGrade(item) {
      return [item && item.spec, item && item.grade].filter(value => this.hasValue(value)).join(' / ') || '—'
    },
    lineAmount(item) {
      const quantity = this.numberOrNull(item && item.quantity)
      const costPrice = this.numberOrNull(item && item.costPrice)
      if (quantity !== null && costPrice !== null) return Number((quantity * costPrice).toFixed(2))
      return this.numberOrNull(item && item.amount)
    },
    detailRowKey(row, index) {
      return row && (row.detailId || row.itemId || row.productId) || index
    },
    primaryActionElement() {
      return this.$refs.retryButton || this.$refs.continueButton || this.$refs.confirmButton || null
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
      if (!this.submitting) this.$emit('close')
    },
    confirm(continueNext) {
      if (!this.canSubmit || this.submitting) return
      this.$emit('confirm', { comment: this.comment, continueNext: continueNext === true })
    },
    retry() {
      if (!this.retryAvailable || !this.eligible || this.submitting) return
      this.$emit('retry', { comment: this.comment })
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../feature/components/mobileSheet.scss";

.mobile-todo-quick-mask { z-index: 10070; }
.mobile-todo-quick-sheet { width: min(100%, 440px); }
.detail-head button { font-size: 28px; line-height: 1; }
.mobile-todo-quick-body { padding-top: 12px; }
.mobile-todo-quick-summary div:first-child { border-top: 0; }
.mobile-todo-quick-detail { margin-top: 16px; }
.mobile-todo-quick-detail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.mobile-todo-quick-detail-head h3 {
  margin: 0;
  color: #17211d;
  font-size: 15px;
  line-height: 1.4;
  font-weight: 900;
}
.mobile-todo-quick-detail-head span { color: #66736d; font-size: 12px; font-weight: 800; }
.mobile-todo-quick-detail-list {
  max-height: min(42vh, 360px);
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  display: grid;
  gap: 10px;
  margin: 10px 0 0;
  padding: 0 2px 0 0;
  list-style: none;
}
.mobile-todo-quick-detail-list:focus-visible {
  outline: 2px solid rgba(11, 107, 83, .42);
  outline-offset: 3px;
}
.mobile-todo-quick-detail-list > li {
  border: 1px solid var(--mobile-color-line, #dde2de);
  border-radius: 14px;
  padding: 12px;
  background: var(--mobile-color-surface-soft, #f8f8f5);
}
.mobile-todo-quick-detail-item-head {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: start;
}
.mobile-todo-quick-detail-index {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #e1f3ec;
  color: #0b6b53;
  font-size: 12px;
  font-weight: 900;
}
.mobile-todo-quick-detail-item-head > div { min-width: 0; display: grid; gap: 2px; }
.mobile-todo-quick-detail-item-head small,
.mobile-todo-quick-detail-item-head span { color: #66736d; font-size: 11px; line-height: 1.35; font-weight: 700; }
.mobile-todo-quick-detail-item-head strong { color: #17211d; font-size: 14px; line-height: 1.4; overflow-wrap: anywhere; }
.mobile-todo-quick-detail-item-head em {
  border-radius: 999px;
  padding: 5px 8px;
  background: #e7f2ed;
  color: #0b6b53;
  font-size: 11px;
  line-height: 1;
  font-style: normal;
  font-weight: 900;
  white-space: nowrap;
}
.mobile-todo-quick-detail-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px 12px;
  margin: 12px 0 0;
  padding-top: 11px;
  border-top: 1px solid var(--mobile-color-line, #dde2de);
}
.mobile-todo-quick-detail-fields div { min-width: 0; display: grid; gap: 3px; }
.mobile-todo-quick-detail-fields dt { color: #66736d; font-size: 11px; line-height: 1.35; font-weight: 800; }
.mobile-todo-quick-detail-fields dd { margin: 0; color: #24352e; font-size: 13px; line-height: 1.4; font-weight: 900; overflow-wrap: anywhere; }
.mobile-todo-quick-detail-fields .mobile-todo-quick-detail-amount { grid-column: 1 / -1; }
.mobile-todo-quick-detail-amount dd { color: #0b6b53; }
.mobile-todo-quick-detail-empty {
  display: grid;
  gap: 4px;
  margin-top: 10px;
  border: 1px dashed var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  padding: 12px;
  color: #66736d;
  background: #fbfdfc;
}
.mobile-todo-quick-detail-empty strong { color: #24352e; font-size: 13px; }
.mobile-todo-quick-detail-empty span { font-size: 12px; line-height: 1.5; }
.mobile-todo-quick-detail-total {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
  border-radius: 12px;
  padding: 10px 12px;
  background: #e7f2ed;
  color: #164e43;
  font-size: 12px;
  font-weight: 800;
}
.mobile-todo-quick-detail-total span { text-align: center; }
.mobile-todo-quick-detail-total em { font-style: normal; font-size: 13px; font-weight: 900; }
.mobile-todo-approval-error {
  margin: 14px 0 0;
  border: 1px solid #fecaca;
  border-radius: 12px;
  padding: 12px;
  background: #fff1f2;
  color: #b42318;
  font-size: 14px;
  line-height: 1.5;
  font-weight: 700;
}
.mobile-todo-approval-warnings {
  margin: 14px 0 0;
  border: 1px solid #f5d38a;
  border-radius: 12px;
  padding: 10px 12px 10px 30px;
  background: #fff8e8;
  color: #8a5a00;
  font-size: 13px;
  line-height: 1.5;
}
.mobile-todo-comment { display: grid; gap: 7px; margin-top: 16px; }
.mobile-todo-comment > span { color: #24352e; font-size: 14px; font-weight: 800; }
.mobile-todo-comment textarea {
  width: 100%;
  min-height: 92px;
  box-sizing: border-box;
  resize: vertical;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  padding: 11px 12px;
  background: #fff;
  color: #17211d;
  font: inherit;
  line-height: 1.45;
}
.mobile-todo-comment textarea:focus { border-color: #0b6b53; outline: 2px solid rgba(11, 107, 83, .16); }
.mobile-todo-comment small { justify-self: end; color: #66736d; }
.mobile-todo-quick-footer { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.mobile-todo-quick-footer button {
  min-height: 48px;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
  color: #17211d;
  font-weight: 800;
}
.mobile-todo-quick-footer .primary { border-color: #0b6b53; background: #0b6b53; color: #fff; }
.mobile-todo-quick-footer .continue { grid-column: 1 / -1; }
.mobile-todo-quick-footer .warning { border-color: #d99a22; background: #fff8e8; color: #8a5a00; }
.mobile-todo-quick-footer button:disabled { opacity: .58; }
</style>
