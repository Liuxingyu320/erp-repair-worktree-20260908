<template>
  <el-dialog
    :title="dialogTitle"
    :visible="visible"
    :width="showFullDetails ? '960px' : '620px'"
    top="4vh"
    :custom-class="dialogClass"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :before-close="requestClose"
    @opened="resetScroll"
  >
    <div
      ref="dialogBody"
      v-loading="loading"
      class="quick-approve-dialog"
      :class="{ 'is-compact': !showFullDetails }"
      :aria-busy="loading ? 'true' : 'false'"
      :aria-label="showFullDetails ? '调拨审批完整详情' : '快速通过确认'"
    >
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="quick-approve-error"
      />

      <template v-if="!loading && preview">
        <template v-if="showFullDetails">
          <section class="quick-approve-section" aria-labelledby="quick-transfer-heading">
            <h3 id="quick-transfer-heading" class="quick-approve-section__title">调拨基本信息</h3>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="单号">{{ display(preview.orderNo || row.businessNo) }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusType(preview.status)" size="mini">{{ statusLabel(preview.status) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="调拨类型">{{ transferTypeLabel(preview.transferType) }}</el-descriptions-item>
              <el-descriptions-item label="调出组织">{{ display(preview.fromDeptName) }}</el-descriptions-item>
              <el-descriptions-item label="调出仓库">{{ display(preview.fromWarehouseName) }}</el-descriptions-item>
              <el-descriptions-item label="调入组织">{{ display(preview.toDeptName) }}</el-descriptions-item>
              <el-descriptions-item label="调入仓库">{{ display(preview.toWarehouseName) }}</el-descriptions-item>
              <el-descriptions-item label="申请数量">{{ quantity(preview.totalQuantity) }}</el-descriptions-item>
              <el-descriptions-item label="参考总价">{{ money(detailTotal) }}</el-descriptions-item>
              <el-descriptions-item label="创建人">{{ display(preview.createdByName) }}</el-descriptions-item>
              <el-descriptions-item label="提交人">{{ display(preview.submittedByName || preview.submitterName || preview.createByName) }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{ display(preview.submittedTime) }}</el-descriptions-item>
              <el-descriptions-item label="收件人">{{ display(preview.recipientName) }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ display(preview.recipientPhone) }}</el-descriptions-item>
              <el-descriptions-item label="收货地址" :span="3">{{ display(preview.shippingAddress) }}</el-descriptions-item>
              <el-descriptions-item v-if="hasValue(preview.returnReasonText)" label="返仓原因" :span="3">
                {{ display(preview.returnReasonText) }}
              </el-descriptions-item>
              <el-descriptions-item v-if="preview.transferType === 'cross_store'" label="调出店确认">
                {{ sourceConfirmStatusLabel(preview.sourceConfirmStatus) }}
              </el-descriptions-item>
              <el-descriptions-item v-if="hasValue(preview.sourceConfirmedBy)" label="确认人 / 时间" :span="2">
                {{ display(preview.sourceConfirmedBy) }} / {{ display(preview.sourceConfirmedTime) }}
              </el-descriptions-item>
              <el-descriptions-item v-if="hasValue(preview.sourceConfirmRemark)" label="确认说明" :span="3">
                {{ display(preview.sourceConfirmRemark) }}
              </el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="quick-approve-section" aria-labelledby="quick-approval-heading">
            <h3 id="quick-approval-heading" class="quick-approve-section__title">审批进度</h3>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="当前节点">{{ currentNode }}</el-descriptions-item>
              <el-descriptions-item label="审批轮次">{{ approvalRound }}</el-descriptions-item>
              <el-descriptions-item label="节点进度">{{ approvalProgress }}</el-descriptions-item>
              <el-descriptions-item label="当前审批人" :span="3">{{ approvalCandidates }}</el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-for="(warning, index) in approvalWarnings"
              :key="`${index}-${warning}`"
              :title="warning"
              type="warning"
              :closable="false"
              show-icon
              class="quick-approve-warning-alert"
            />
          </section>

          <section class="quick-approve-section" aria-labelledby="quick-details-heading">
            <h3 id="quick-details-heading" class="quick-approve-section__title">
              调拨明细 <span>共 {{ detailRows.length }} 项</span>
            </h3>
            <el-table
              :data="detailRows"
              :row-key="detailRowKey"
              size="small"
              border
              stripe
              show-summary
              :summary-method="detailSummary"
              empty-text="暂无调拨明细"
              class="quick-approve-detail-table"
            >
              <el-table-column type="index" label="序号" width="56" align="center" />
              <el-table-column label="物料" min-width="150">
                <template slot-scope="scope">{{ display(scope.row.itemName || scope.row.productName) }}</template>
              </el-table-column>
              <el-table-column label="编码" min-width="110">
                <template slot-scope="scope">{{ display(scope.row.itemCode || scope.row.productCode) }}</template>
              </el-table-column>
              <el-table-column label="类型" width="82">
                <template slot-scope="scope">{{ itemTypeLabel(scope.row.itemType) }}</template>
              </el-table-column>
              <el-table-column label="规格 / 等级" min-width="120">
                <template slot-scope="scope">{{ specGrade(scope.row) }}</template>
              </el-table-column>
              <el-table-column label="申请数量" prop="quantity" width="96" align="right">
                <template slot-scope="scope">{{ quantity(scope.row.quantity) }}</template>
              </el-table-column>
              <el-table-column label="单位" prop="unit" width="70" align="center">
                <template slot-scope="scope">{{ display(scope.row.unit) }}</template>
              </el-table-column>
              <el-table-column label="参考成本价" prop="costPrice" width="112" align="right">
                <template slot-scope="scope">{{ money(scope.row.costPrice) }}</template>
              </el-table-column>
              <el-table-column label="小计" prop="amount" width="112" align="right">
                <template slot-scope="scope">{{ money(lineAmount(scope.row)) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </template>

        <section v-else class="quick-approve-compact-summary" aria-label="快速通过摘要">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="单号">{{ display(preview.orderNo || row.businessNo) }}</el-descriptions-item>
            <el-descriptions-item label="审批节点">{{ currentNode }}</el-descriptions-item>
            <el-descriptions-item label="调出组织">{{ display(preview.fromDeptName) }}</el-descriptions-item>
            <el-descriptions-item label="调入组织">{{ display(preview.toDeptName) }}</el-descriptions-item>
            <el-descriptions-item label="申请数量">{{ quantity(preview.totalQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="参考总价">{{ money(detailTotal) }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ display(preview.submittedByName || preview.submitterName || preview.createByName) }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ display(preview.submittedTime) }}</el-descriptions-item>
          </el-descriptions>
          <p class="quick-approve-compact-tip">
            如需核对物料、收货信息和审批进度，请从“查看审批”进入。
          </p>
        </section>

        <section class="quick-approve-section" aria-labelledby="quick-comment-heading">
          <h3 id="quick-comment-heading" class="quick-approve-section__title">审批处理</h3>
          <el-form label-position="top" class="quick-approve-form" @submit.native.prevent>
            <el-form-item label="审批意见（选填）">
              <el-input
                v-model="comment"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="通过意见可不填写"
                :disabled="submitting"
                aria-label="审批意见（选填）"
              />
            </el-form-item>
          </el-form>
          <p class="quick-approve-warning">
            系统提交时会再次校验当前账号、审批任务、权限和组织范围。
          </p>
        </section>
      </template>
    </div>

    <span slot="footer" class="dialog-footer quick-approve-footer">
      <el-button :disabled="submitting" @click="requestClose">取消</el-button>
      <el-button
        v-if="retryAvailable"
        ref="retryAction"
        type="warning"
        plain
        :loading="submitting"
        :disabled="loading || !preview"
        @click="$emit('retry', { comment: normalizedComment })"
      >使用原请求号重试</el-button>
      <el-button
        v-if="nextAvailable && !retryAvailable"
        ref="continueAction"
        type="primary"
        :loading="submitting"
        :disabled="loading || !preview || !!errorMessage"
        @click="confirm(true)"
      >通过并处理下一条</el-button>
      <el-button
        v-if="!retryAvailable"
        ref="confirmAction"
        :type="nextAvailable ? 'success' : 'primary'"
        :loading="submitting"
        :disabled="loading || !preview || !!errorMessage"
        @click="confirm(false)"
      >确认通过</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'TodoQuickApproveDialog',
  props: {
    visible: { type: Boolean, default: false },
    mode: { type: String, default: 'quick' },
    row: { type: Object, default: () => ({}) },
    preview: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    submitting: { type: Boolean, default: false },
    errorMessage: { type: String, default: '' },
    nextAvailable: { type: Boolean, default: false },
    retryAvailable: { type: Boolean, default: false }
  },
  data() {
    return { comment: '' }
  },
  computed: {
    showFullDetails() {
      return this.mode === 'detail'
    },
    dialogTitle() {
      return this.showFullDetails ? '调拨审批详情' : '确认通过调拨审批'
    },
    dialogClass() {
      return this.showFullDetails ? 'todo-approval-detail-modal' : 'todo-quick-approve-modal'
    },
    approvalSummary() {
      return this.preview && this.preview.approvalSummary || {}
    },
    currentNode() {
      return this.display(this.approvalSummary.currentNodeName || this.approvalSummary.nodeName || this.preview && this.preview.currentNodeName)
    },
    approvalRound() {
      const value = this.approvalSummary.roundNo || this.preview && this.preview.approvalRound
      return this.hasValue(value) ? `第 ${value} 轮` : '—'
    },
    approvalProgress() {
      const current = Number(this.approvalSummary.currentNodeOrder)
      const completed = Number(this.approvalSummary.completedNodeCount)
      const total = Number(this.approvalSummary.totalNodeCount)
      if (Number.isFinite(current) && current > 0 && Number.isFinite(total) && total > 0) {
        return `第 ${current} / ${total} 节点`
      }
      if (Number.isFinite(completed) && completed >= 0 && Number.isFinite(total) && total > 0) {
        return `已完成 ${completed} / ${total} 节点`
      }
      return this.display(this.approvalSummary.summaryText)
    },
    approvalCandidates() {
      const values = this.approvalSummary.currentCandidateDisplayNames
      return Array.isArray(values) && values.length ? values.join('、') : '当前账号'
    },
    approvalWarnings() {
      const values = this.preview && this.preview.approvalWarnings
      return Array.isArray(values) ? values.filter(value => this.hasValue(value)) : []
    },
    detailRows() {
      const values = this.preview && this.preview.details
      return Array.isArray(values) ? values : []
    },
    detailTotal() {
      const explicitTotal = this.numberOrNull(this.preview && this.preview.totalAmount)
      if (explicitTotal !== null) return explicitTotal
      const amounts = this.detailRows.map(item => this.lineAmount(item)).filter(value => value !== null)
      return amounts.length ? amounts.reduce((sum, value) => sum + value, 0) : null
    },
    normalizedComment() {
      return String(this.comment || '').trim()
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.comment = ''
        this.resetScroll()
      }
    },
    loading(value) {
      if (!value && this.preview) this.focusPrimaryAction()
    },
    retryAvailable(value) {
      if (value) this.focusPrimaryAction()
    },
    submitting(value) {
      if (!value && !this.loading && this.preview) this.focusPrimaryAction()
    },
    'row.todoKey'() {
      this.comment = ''
      this.resetScroll()
    }
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
    statusLabel(value) {
      return {
        draft: '草稿', submitted: '待审批', approved: '待发货', reserved: '已锁库',
        partial_delivered: '部分发货', delivered: '待收货', partial_received: '部分收货',
        discrepancy: '待差异处理', received: '已完成', closed: '已关闭', cancelled: '已取消', rejected: '已驳回'
      }[String(value || '').toLowerCase()] || this.display(value)
    },
    statusType(value) {
      return {
        submitted: 'warning', approved: 'success', received: 'success', closed: 'info',
        cancelled: 'info', rejected: 'danger', discrepancy: 'danger'
      }[String(value || '').toLowerCase()] || ''
    },
    transferTypeLabel(value) {
      return { warehouse: '门店要货', store_return: '门店返仓', cross_store: '异店调货' }[value] || this.display(value)
    },
    sourceConfirmStatusLabel(value) {
      return { PENDING: '待确认', CONFIRMED: '已确认', PARTIAL: '部分确认', REJECTED: '已拒绝' }[String(value || '').toUpperCase()] || this.display(value)
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
    detailSummary({ columns }) {
      return columns.map((column, index) => {
        if (index === 0) return '合计'
        if (column.property === 'quantity') {
          return this.quantity(this.detailRows.reduce((sum, item) => sum + (this.numberOrNull(item.quantity) || 0), 0))
        }
        if (column.property === 'amount') return this.money(this.detailTotal)
        return ''
      })
    },
    requestClose() {
      if (this.submitting) return
      this.$emit('update:visible', false)
      this.$emit('close')
    },
    resetScroll() {
      this.$nextTick(() => {
        const body = this.$refs.dialogBody
        if (body) body.scrollTop = 0
      })
    },
    focusPrimaryAction() {
      this.$nextTick(() => {
        const control = this.retryAvailable
          ? this.$refs.retryAction
          : this.$refs.continueAction || this.$refs.confirmAction
        const element = control && (control.$el || control)
        if (!element || typeof element.focus !== 'function') return
        try { element.focus({ preventScroll: true }) } catch (error) { element.focus() }
      })
    },
    confirm(continueNext) {
      if (this.loading || this.submitting || !this.preview || this.errorMessage) return
      this.$emit('confirm', {
        comment: this.normalizedComment,
        continueNext: continueNext === true
      })
    }
  }
}
</script>

<style lang="scss">
.todo-quick-approve-modal,
.todo-approval-detail-modal {
  max-width: calc(100vw - 32px);
  margin-bottom: 4vh;
}
.todo-quick-approve-modal .el-dialog__body,
.todo-approval-detail-modal .el-dialog__body {
  padding: 14px 20px 8px;
}
</style>

<style lang="scss" scoped>
.quick-approve-dialog {
  min-height: 240px;
  max-height: calc(92vh - 150px);
  overflow-y: auto;
  padding-right: 4px;
}
.quick-approve-dialog.is-compact { min-height: 180px; }
.quick-approve-error { margin-bottom: 14px; }
.quick-approve-section + .quick-approve-section { margin-top: 18px; }
.quick-approve-section__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  color: #1f2937;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}
.quick-approve-section__title span {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}
.quick-approve-warning-alert { margin-top: 10px; }
.quick-approve-compact-tip {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}
.quick-approve-detail-table { width: 100%; }
.quick-approve-form { margin-top: 0; }
.quick-approve-form ::v-deep .el-form-item { margin-bottom: 8px; }
.quick-approve-warning {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}
.quick-approve-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
@media (max-width: 760px) {
  .quick-approve-dialog { max-height: calc(96vh - 150px); }
  .quick-approve-footer { flex-wrap: wrap; }
}
</style>
