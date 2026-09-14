<template>
  <section v-if="open" ref="actionDialogMask" class="detail-mask form-mask" @click.self="$emit('close')">
    <article ref="actionDialog" class="glass-panel detail-sheet form-sheet mobile-system-sheet action-dialog-sheet" role="dialog" aria-modal="true" aria-label="处理动作" tabindex="-1">
      <header class="detail-head mobile-system-sheet__header action-dialog-header">
        <div>
          <span>处理动作</span>
          <h2 tabindex="-1">{{ action.label }}</h2>
        </div>
        <button type="button" aria-label="关闭" @click="$emit('close')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
        </button>
      </header>
      <form class="mobile-form-list action-dialog-form" @submit.prevent="confirm">
        <div class="mobile-system-sheet__body action-dialog-body">
          <p v-if="action.confirmText" class="action-confirm-text">{{ action.confirmText }}</p>
          <section v-if="item" class="action-review-summary" aria-label="待处理单据摘要">
            <span>待处理单据</span>
            <strong>{{ item.title || item.name || item.code || "未命名单据" }}</strong>
            <small v-if="item.code && item.code !== item.title">单号 {{ item.code }}</small>
            <dl v-if="reviewFields.length" class="action-review-fields">
              <div v-for="(field, index) in reviewFields" :key="field.label + index">
                <dt>{{ field.label }}</dt>
                <dd>{{ field.value }}</dd>
              </div>
            </dl>
            <p v-else-if="item.detail">{{ item.detail }}</p>
          </section>
          <p v-if="showQuality && qualityLoading" class="action-confirm-text">正在加载待检收货批次…</p>
          <label v-if="showQuality && qualityLegacyMode">
            <span>质检结果</span>
            <select v-model="qcResult">
              <option value="" disabled>请选择</option>
              <option value="passed">合格</option>
              <option value="concession">让步接收</option>
              <option value="rejected">拒收</option>
            </select>
          </label>
          <p v-if="showQuality && qualityLegacyMode" class="action-confirm-text">该单据是历史待检数据，将按整单方式处理。</p>
          <label v-if="showQuality && !qualityLegacyMode && qualityBatches.length">
            <span>收货批次</span>
            <select v-model="receiptBatchId" @change="applyQualityBatch">
              <option v-for="batch in qualityBatches" :key="batch.batchId" :value="batch.batchId">
                {{ formatQualityBatch(batch) }}
              </option>
            </select>
          </label>
          <section v-if="showQuality && !qualityLegacyMode && qualityRows.length" class="mobile-quality-list">
            <p>勾选本次检验的商品，未勾选商品继续待检。</p>
            <div class="quality-selection-actions">
              <label><input type="checkbox" :checked="qualityRows.every(isQualitySelected)" @change="setQualitySelection($event.target.checked)">全选</label>
              <button type="button" @click="fillSelectedQualityPassed">所选全部合格</button>
              <button type="button" @click="clearSelectedQualityResults">清空分类数量</button>
            </div>
            <article v-for="row in qualityRows" :key="row.batchDetailId" class="mobile-quality-row">
              <header>
                <label><input v-model="row.qcSelected" type="checkbox">选择检验：{{ row.itemName || row.itemCode || row.batchDetailId }}</label>
                <span>待检 {{ row.pendingQuantity }} {{ row.unit || '' }}</span>
              </header>
              <div class="mobile-quality-grid">
                <label><span>本次检验</span><input v-model.number="row.inspectedQuantity" :disabled="!isQualitySelected(row)" type="number" min="0" :max="row.pendingQuantity" step="0.01"></label>
                <label><span>合格</span><input v-model.number="row.acceptedQuantity" :disabled="!isQualitySelected(row)" type="number" min="0" step="0.01"></label>
                <label><span>拒收</span><input v-model.number="row.rejectedQuantity" :disabled="!isQualitySelected(row)" type="number" min="0" step="0.01"></label>
                <label><span>让步</span><input v-model.number="row.concessionQuantity" :disabled="!isQualitySelected(row)" type="number" min="0" step="0.01"></label>
              </div>
              <label>
                <span>缺陷等级</span>
                <select v-model="row.defectLevel" :disabled="!isQualitySelected(row)">
                  <option value="">选填</option><option value="minor">轻微</option><option value="major">一般</option><option value="critical">严重</option>
                </select>
              </label>
              <label>
                <span>拒收/让步原因</span>
                <textarea v-model.trim="row.defectReason" :disabled="!isQualitySelected(row)" rows="2" placeholder="有拒收或让步数量时必填" />
              </label>
            </article>
          </section>
          <label v-if="shipmentOptions.length">
            <span>发货批次</span>
            <select v-model="shipmentId" @change="initQuantityRows">
              <option v-for="shipment in shipmentOptions" :key="shipment.shipmentId" :value="shipment.shipmentId">
                {{ shipment.shipmentNo || shipment.shipmentId }}
              </option>
            </select>
          </label>
          <section v-if="showReceiveMetadata" class="mobile-receive-fields" aria-label="采购收货信息">
            <label>
              <span>实际到货时间</span>
              <input v-model="arrivedTime" type="datetime-local" step="1" required>
            </label>
            <label>
              <span>供应商批次</span>
              <input v-model.trim="supplierBatchNo" type="text" maxlength="100" placeholder="选填，最多100个字符">
            </label>
            <label>
              <span>送货单号</span>
              <input v-model.trim="deliveryNoteNo" type="text" maxlength="100" placeholder="选填，最多100个字符">
            </label>
            <label>
              <span>收货备注</span>
              <textarea v-model.trim="receiveRemark" rows="3" maxlength="500" placeholder="选填，最多500个字符" />
            </label>
          </section>
          <section v-if="showTransferDiscrepancy" class="mobile-discrepancy-fields" aria-label="调拨差异处理信息">
            <label>
              <span>待处理差异单</span>
              <select v-model="discrepancyId" required @change="initDiscrepancyRows">
                <option value="" disabled>请选择</option>
                <option
                  v-for="discrepancy in discrepancyOptions"
                  :key="discrepancy.discrepancyId"
                  :value="discrepancy.discrepancyId"
                >
                  {{ formatDiscrepancyOption(discrepancy) }}
                </option>
              </select>
            </label>
            <section v-if="discrepancyRows.length" class="mobile-discrepancy-list" aria-label="逐项差异处置">
              <article v-for="row in discrepancyRows" :key="row.detailId + ':' + row.category" class="mobile-discrepancy-row">
                <header>
                  <strong>{{ row.itemName || row.itemCode || row.detailId }}</strong>
                  <span>{{ row.categoryLabel }} · 数量 {{ row.quantity }} · 冻结成本 {{ row.costPrice }}</span>
                </header>
                <label>
                  <span>决定</span>
                  <select v-model="row.decision" required>
                    <option value="" disabled>请选择</option>
                    <option v-if="hasDiscrepancyDecision(row, 'RESHIP')" value="RESHIP">安排补发</option>
                    <option v-if="hasDiscrepancyDecision(row, 'RETURN_SOURCE')" value="RETURN_SOURCE">退回来源</option>
                    <option v-if="hasDiscrepancyDecision(row, 'ACCEPT_ACTUAL')" value="ACCEPT_ACTUAL">按实收结案</option>
                    <option v-if="hasDiscrepancyDecision(row, 'PENDING_QC')" value="PENDING_QC">转待质检</option>
                    <option v-if="hasDiscrepancyDecision(row, 'WRITE_OFF')" value="WRITE_OFF">核销差异</option>
                  </select>
                </label>
                <div class="transfer-evidence-field">
                  <span>{{ row.attachmentRequired ? "凭证附件（必填）" : "凭证附件（可选）" }}</span>
                  <transfer-evidence-picker v-if="open" v-model="row.attachmentRefs" :context-key="evidenceEpoch + ':' + selectedDeptId + ':discrepancy:' + discrepancyId + ':' + row.detailId + ':' + row.category" :required="row.attachmentRequired && ['RETURN_SOURCE', 'WRITE_OFF'].includes(row.decision)" @upload-state="$set(row, 'evidenceState', $event)"/>
                </div>
                <label>
                  <span>逐项说明</span>
                  <textarea v-model.trim="row.note" rows="2" maxlength="1000" placeholder="可补充本类别处置依据" />
                </label>
              </article>
            </section>
            <p v-else class="action-confirm-text">当前差异台账没有可继续处置的类别，请刷新后重试。</p>
            <label>
              <span>责任方</span>
              <select v-model="discrepancyResponsibleParty" required>
                <option value="SOURCE">来源方</option>
                <option value="TARGET">目标方</option>
                <option value="LOGISTICS">物流</option>
                <option value="UNCONFIRMED">待确认</option>
              </select>
            </label>
          </section>
          <section v-if="showTransferReceipt && quantityRows.length" class="mobile-transfer-receipt-list" aria-label="调拨验收分类">
            <article v-for="row in quantityRows" :key="row.detailId" class="mobile-transfer-receipt-row">
              <header>
                <strong>{{ row.productName || row.productCode || row.detailId }}</strong>
                <span>待收 {{ row.remaining }}</span>
              </header>
              <div class="mobile-transfer-receipt-grid">
                <label><span>验收入库</span><input v-model.number="row.quantity" type="number" min="0" :max="row.remaining" step="0.01"></label>
                <label><span>拒收</span><input v-model.number="row.rejectedQuantity" type="number" min="0" :max="row.remaining" step="0.01"></label>
                <label><span>残损</span><input v-model.number="row.damagedQuantity" type="number" min="0" :max="row.remaining" step="0.01"></label>
                <div class="mobile-transfer-shortage">
                  <span>自动短少</span>
                  <strong>{{ transferReceiptShortage(row) }}</strong>
                </div>
              </div>
              <label>
                <span>差异说明</span>
                <textarea
                  v-model.trim="row.discrepancyNote"
                  rows="2"
                  maxlength="500"
                  placeholder="有短少、拒收或残损时必填"
                />
              </label>
              <div><span>凭证附件</span><transfer-evidence-picker v-if="open" v-model="row.attachmentRefs" :context-key="evidenceEpoch + ':' + selectedDeptId + ':receipt:' + shipmentId + ':' + row.detailId" @upload-state="$set(row, 'evidenceState', $event)"/></div>
            </article>
          </section>
          <section v-else-if="quantityRows.length" class="action-quantity-list">
            <article v-for="row in quantityRows" :key="row.detailId">
              <div>
                <strong>{{ row.productName || row.productCode || row.detailId }}</strong>
                <span>{{ row.meta }}</span>
                <span v-if="isStockCheckQuantityAction() && row.needsSnapshotReview">库存已变化，请重新核对</span>
                <span v-if="isStockCheckQuantityAction() && row.previousActualQty != null">上轮实盘 {{ row.previousActualQty }}<template v-if="row.previousRecountQty != null"> · 上轮复盘 {{ row.previousRecountQty }}</template></span>
              </div>
              <input v-model.number="row.quantity" @input="handleStockCheckActualChange(row)" type="number" min="0" :max="isStockCheckQuantityAction() ? undefined : row.remaining" step="0.01" :aria-label="quantityInputLabel(row)">
              <label v-if="isStockCheckQuantityAction() && (Number(rawItem.recountThreshold || 0) > 0 || row.recountRequired === '1')">
                复盘数量（达到阈值时填写）
                <input v-model.number="row.recountQty" type="number" min="0" step="0.01" :aria-label="(row.productName || row.productCode || row.detailId) + '复盘数量'">
              </label>
            </article>
          </section>
          <section v-if="stockDifferenceRows.length" class="action-quantity-list">
            <article v-for="row in stockDifferenceRows" :key="row.detailId">
              <div>
                <strong>{{ row.productName || row.productCode || row.detailId }}</strong>
                <span>账面 {{ row.bookQuantity }} · 实盘 {{ row.actualQuantity }}</span>
              </div>
              <em>{{ row.diffText }}</em>
            </article>
          </section>
          <label v-if="action.acceptsComment || action.requiresComment || (showQuality && qualityLegacyMode)">
            <span>处理意见</span>
            <textarea v-model.trim="comment" rows="3" :placeholder="action.commentPlaceholder || '请输入处理意见'" />
          </label>
          <p v-if="error" class="detail-action-message" role="alert" aria-live="assertive">{{ error }}</p>
        </div>
        <footer class="detail-actions mobile-system-sheet__footer action-dialog-footer">
          <button type="button" @click="$emit('close')">取消</button>
          <button ref="confirmButton" type="submit" class="primary" :disabled="qualityLoading">确定</button>
        </footer>
      </form>
    </article>
  </section>
</template>

<script>
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"
import { listPendingReceiptBatches } from "@/api/inventory/purchase"
const { portalMobileOverlay, restoreMobileOverlay } = require("./mobileOverlayPortal")
const {
  filterDeliveryNoticeRowsByWarehouse,
  resolveStockCheckInputQuantity
} = require("../mobileActionPayloads")

function firstDefinedNumber(source, keys, fallback = 0) {
  for (let index = 0; index < keys.length; index += 1) {
    const value = source && source[keys[index]]
    if (value !== undefined && value !== null && value !== "") {
      const numericValue = Number(value)
      return Number.isFinite(numericValue) ? numericValue : fallback
    }
  }
  return fallback
}

function padDateTimePart(value) {
  return String(value).padStart(2, "0")
}

function defaultReceiveTime(date = new Date()) {
  return [
    date.getFullYear(),
    padDateTimePart(date.getMonth() + 1),
    padDateTimePart(date.getDate())
  ].join("-") + "T" + [
    padDateTimePart(date.getHours()),
    padDateTimePart(date.getMinutes()),
    padDateTimePart(date.getSeconds())
  ].join(":")
}

function normalizeReceiveTime(value) {
  const normalized = String(value || "").trim().replace("T", " ")
  return normalized.length === 16 ? normalized + ":00" : normalized
}

export default {
  name: "MobileActionDialog",
  components: { TransferEvidencePicker: () => import("@/components/TransferEvidencePicker.vue") },
  props: {
    open: Boolean,
    action: { type: Object, default: () => ({}) },
    item: { type: Object, default: null },
    reviewFields: { type: Array, default: () => [] },
    iconPaths: { type: Object, required: true },
    selectedDeptId: { type: [String, Number], default: "" }
  },
  data() {
    return {
      comment: "",
      qcResult: "",
      qualityLoading: false,
      qualityLoadFailed: false,
      qualityLegacyMode: false,
      qualityBatches: [],
      receiptBatchId: "",
      qualityRows: [],
      qualityRequestToken: 0,
      evidenceEpoch: 0,
      shipmentId: "",
      arrivedTime: "",
      supplierBatchNo: "",
      deliveryNoteNo: "",
      receiveRemark: "",
      discrepancyId: "",
      discrepancyDecision: "",
      discrepancyRequestId: "",
      discrepancyResponsibleParty: "UNCONFIRMED",
      discrepancyRows: [],
      quantityRows: [],
      error: "",
      dialogFocusManager: null,
      actionDialogPortalAnchor: null
    }
  },
  computed: {
    rawItem() {
      return this.item && (this.item._raw || this.item.raw || this.item) ? (this.item._raw || this.item.raw || this.item) : {}
    },
    showQuality() {
      return this.action.id === "qualityCheckPurchase"
    },
    showReceiveMetadata() {
      return this.action.id === "receivePurchaseAll"
    },
    showTransferReceipt() {
      return this.action.id === "receiveTransferShipment"
    },
    showTransferDiscrepancy() {
      return this.action.id === "handleTransferDiscrepancy"
    },
    discrepancyOptions() {
      if (!this.showTransferDiscrepancy) return []
      const discrepancies = Array.isArray(this.rawItem.discrepancies)
        ? this.rawItem.discrepancies
        : []
      return discrepancies.filter(item =>
        item && String(item.status || "").toUpperCase() !== "RESOLVED"
      )
    },
    shipmentOptions() {
      if (this.action.id !== "receiveTransferShipment") return []
      return (this.rawItem.shipments || []).filter(item => item.status === "pending_receive")
    },
    stockDifferenceRows() {
      if (["submitStockCheck", "approveStockCheck", "returnStockCheck", "rejectStockCheck"].indexOf(this.action.id) === -1) return []
      return (this.rawItem.details || []).map(row => {
        const detailId = row.detailId || row.checkDetailId
        const inputRow = this.quantityRows.find(item => String(item.detailId) === String(detailId))
        const book = firstDefinedNumber(row, ["bookQuantity", "bookQty", "systemQuantity", "quantity"])
        const actual = inputRow
          ? Number(inputRow.quantity)
          : firstDefinedNumber(row, ["actualQuantity", "actualQty", "checkQuantity"])
        const diff = Number((actual - book).toFixed(2))
        return Object.assign({}, row, {
          bookQuantity: book,
          actualQuantity: actual,
          diffText: diff === 0 ? "无差异" : (diff > 0 ? "库存多出 " : "卖出 ") + Math.abs(diff)
        })
      })
    }
  },
  watch: {
    open(value) {
      if (value) {
        this.resetDialog()
        this.$nextTick(() => {
          if (!this.open) return
          this.lockActionDialogBody()
          this.portalActionDialogToBody()
          this.activateActionDialogFocus()
        })
      } else {
        this.deactivateActionDialogFocus()
        this.restoreActionDialogMount()
        this.releaseActionDialogBodyLock()
      }
    },
    action() {
      if (this.open) this.resetDialog()
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.actionDialog,
      getInitialFocus: () => this.getActionDialogInitialFocus(),
      onEscape: () => this.$emit("close")
    })
  },
  mounted() {
    if (this.open) {
      this.$nextTick(() => {
        if (!this.open) return
        this.lockActionDialogBody()
        this.portalActionDialogToBody()
        this.activateActionDialogFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateActionDialogFocus()
    this.releaseActionDialogBodyLock()
    this.restoreActionDialogMount()
  },
  methods: {
    lockActionDialogBody() {
      mountMobileOverlay("mobile-action-dialog-open")
    },
    releaseActionDialogBodyLock() {
      releaseMobileOverlay("mobile-action-dialog-open")
    },
    portalActionDialogToBody() {
      if (this.actionDialogPortalAnchor) return false
      this.actionDialogPortalAnchor = portalMobileOverlay(this.$refs.actionDialogMask)
      return !!this.actionDialogPortalAnchor
    },
    restoreActionDialogMount() {
      if (!this.actionDialogPortalAnchor) return false
      const restored = restoreMobileOverlay(this.$refs.actionDialogMask, this.actionDialogPortalAnchor)
      this.actionDialogPortalAnchor = null
      return restored
    },
    getActionDialogInitialFocus() {
      const dialog = this.$refs.actionDialog
      if (!dialog || typeof dialog.querySelector !== "function") return this.$refs.confirmButton || dialog
      return dialog.querySelector(
        ".mobile-form-list input:not([disabled]), .mobile-form-list textarea:not([disabled]), " +
        ".mobile-form-list select:not([disabled])"
      ) || this.$refs.confirmButton || dialog
    },
    activateActionDialogFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateActionDialogFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    },
    quantityInputLabel(row) {
      return (row.productName || row.productCode || row.detailId || "明细") + "处理数量"
    },
    resetDialog() {
      this.evidenceEpoch++
      this.comment = ""
      this.qcResult = ""
      this.qualityLoading = false
      this.qualityLoadFailed = false
      this.qualityLegacyMode = false
      this.qualityBatches = []
      this.receiptBatchId = ""
      this.qualityRows = []
      this.arrivedTime = this.showReceiveMetadata ? defaultReceiveTime() : ""
      this.supplierBatchNo = ""
      this.deliveryNoteNo = ""
      this.receiveRemark = ""
      this.discrepancyId = this.discrepancyOptions.length
        ? this.discrepancyOptions[0].discrepancyId
        : ""
      this.discrepancyDecision = ""
      this.discrepancyRequestId = this.showTransferDiscrepancy && this.discrepancyId
        ? this.createDiscrepancyRequestId(this.discrepancyId)
        : ""
      this.discrepancyResponsibleParty = "UNCONFIRMED"
      this.discrepancyRows = []
      const preferredShipmentId = this.action && this.action.preferredShipmentId
      const preferredShipment = preferredShipmentId === undefined || preferredShipmentId === null || preferredShipmentId === ""
        ? null
        : this.shipmentOptions.find(item => String(item.shipmentId) === String(preferredShipmentId))
      this.shipmentId = preferredShipment
        ? preferredShipment.shipmentId
        : preferredShipmentId ? "" : (this.shipmentOptions.length ? this.shipmentOptions[0].shipmentId : "")
      this.error = preferredShipmentId && !preferredShipment
        ? "事项已处理"
        : (this.showTransferDiscrepancy && !this.discrepancyOptions.length
            ? "当前调拨单没有待处理差异"
            : "")
      this.initQuantityRows()
      this.initDiscrepancyRows()
      if (this.showQuality) this.loadQualityBatches()
    },
    loadQualityBatches() {
      const orderId = this.rawItem.orderId || this.rawItem.purchaseOrderId || this.rawItem.id
      if (!orderId) {
        this.error = "无法识别采购单，请返回列表重试"
        this.qualityLoadFailed = true
        return
      }
      const requestToken = ++this.qualityRequestToken
      this.qualityLoading = true
      listPendingReceiptBatches(orderId).then(res => {
        if (requestToken !== this.qualityRequestToken || !this.open) return
        this.qualityBatches = (res && res.data) || []
        this.qualityLegacyMode = this.qualityBatches.length === 0
        if (!this.qualityLegacyMode) {
          this.receiptBatchId = this.qualityBatches[0].batchId
          this.applyQualityBatch()
        }
      }).catch(() => {
        if (requestToken !== this.qualityRequestToken || !this.open) return
        this.qualityLoadFailed = true
        this.error = "待检收货批次加载失败，请稍后重试"
      }).finally(() => {
        if (requestToken === this.qualityRequestToken) this.qualityLoading = false
      })
    },
    applyQualityBatch() {
      const batch = this.qualityBatches.find(item => String(item.batchId) === String(this.receiptBatchId))
      this.qualityRows = batch ? (batch.details || []).filter(item => Number(item.pendingQuantity || 0) > 0).map(item => ({
        batchDetailId: item.batchDetailId,
        itemName: item.itemName,
        itemCode: item.itemCode,
        unit: item.unit,
        pendingQuantity: Number(item.pendingQuantity || 0),
        qcSelected: false,
        inspectedQuantity: 0,
        acceptedQuantity: 0,
        rejectedQuantity: 0,
        concessionQuantity: 0,
        defectLevel: "",
        defectReason: ""
      })) : []
      this.error = this.qualityRows.length ? "" : "该批次没有可质检明细"
    },
    formatQualityBatch(batch) {
      return (batch.batchNo || batch.batchId) + " · 待检 " + Number(batch.pendingQuantity || 0)
    },
    formatDiscrepancyOption(discrepancy) {
      const label = discrepancy.discrepancyNo || ("差异单 " + discrepancy.discrepancyId)
      return label +
        " · 短少 " + Number(discrepancy.shortageQuantity || 0) +
        " / 拒收 " + Number(discrepancy.rejectedQuantity || 0) +
        " / 残损 " + Number(discrepancy.damagedQuantity || 0)
    },
    createDiscrepancyRequestId(discrepancyId) {
      return "ITD:" + discrepancyId + ":" + Date.now() + ":" + Math.random().toString(36).slice(2, 10)
    },
    discrepancyCategoryLabel(category) {
      return {
        SHORTAGE: "短少",
        REJECTED: "拒收",
        DAMAGED: "残损"
      }[String(category || "").toUpperCase()] || "差异"
    },
    discrepancyDecisionOptions(category, pendingQc) {
      const normalized = String(category || "").toUpperCase()
      const labels = {
        RESHIP: "安排补发",
        RETURN_SOURCE: "退回来源",
        ACCEPT_ACTUAL: "按实收结案",
        PENDING_QC: "转待质检",
        WRITE_OFF: "核销差异"
      }
      let values = []
      if (normalized === "SHORTAGE" && !pendingQc) values = ["RESHIP", "ACCEPT_ACTUAL", "WRITE_OFF"]
      if (normalized === "REJECTED") values = pendingQc ? ["RETURN_SOURCE", "WRITE_OFF"] : ["RETURN_SOURCE", "PENDING_QC", "WRITE_OFF"]
      if (normalized === "DAMAGED") values = pendingQc ? ["RETURN_SOURCE", "WRITE_OFF"] : ["PENDING_QC", "RETURN_SOURCE", "WRITE_OFF"]
      return values.map(value => ({ value, label: labels[value] }))
    },
    hasDiscrepancyDecision(row, decision) {
      return !!(row && Array.isArray(row.decisionOptions) &&
        row.decisionOptions.some(option => option.value === decision))
    },
    initDiscrepancyRows() {
      if (!this.showTransferDiscrepancy) {
        this.discrepancyRows = []
        return
      }
      const discrepancy = this.discrepancyOptions.find(item => String(item.discrepancyId) === String(this.discrepancyId))
      if (!discrepancy) {
        this.discrepancyRows = []
        return
      }
      this.discrepancyRequestId = this.createDiscrepancyRequestId(this.discrepancyId)
      const latest = {}
      ;(Array.isArray(discrepancy.dispositions) ? discrepancy.dispositions : []).forEach(row => {
        if (!row) return
        latest[String(row.discrepancyDetailId) + ":" + String(row.category || "").toUpperCase()] = row
      })
      const shipment = (Array.isArray(this.rawItem.shipments) ? this.rawItem.shipments : []).find(row => String(row.shipmentId) === String(discrepancy.shipmentId))
      const shipmentDetails = shipment && Array.isArray(shipment.details) ? shipment.details : []
      const pendingQc = String(discrepancy.status || "").toUpperCase() === "PENDING_QC"
      const categories = [
        ["SHORTAGE", "shortageQuantity"],
        ["REJECTED", "rejectedQuantity"],
        ["DAMAGED", "damagedQuantity"]
      ]
      const rows = []
      ;(Array.isArray(discrepancy.details) ? discrepancy.details : []).forEach(detail => {
        categories.forEach(([category, quantityKey]) => {
          const quantity = Number(detail[quantityKey] || 0)
          if (!Number.isFinite(quantity) || quantity <= 0) return
          const prior = latest[String(detail.discrepancyDetailId) + ":" + category]
          if (prior && String(prior.decision || "").toUpperCase() !== "PENDING_QC") return
          const decisionOptions = this.discrepancyDecisionOptions(category, pendingQc)
          if (!decisionOptions.length) return
          const shipmentDetail = shipmentDetails.find(row => String(row.shipmentDetailId) === String(detail.shipmentDetailId))
          rows.push({
            detailId: detail.discrepancyDetailId,
            itemName: detail.itemName || detail.itemCode || detail.discrepancyDetailId,
            itemCode: detail.itemCode,
            category,
            categoryLabel: this.discrepancyCategoryLabel(category),
            quantity,
            costPrice: shipmentDetail && shipmentDetail.costPrice !== undefined && shipmentDetail.costPrice !== null ? shipmentDetail.costPrice : "未知",
            decision: "",
            decisionOptions,
            note: prior && prior.note ? prior.note : "",
            attachmentRefs: prior && prior.attachmentRefs ? prior.attachmentRefs : (detail.attachmentRefs || ""),
            attachmentRequired: category === "DAMAGED" && !pendingQc
          })
        })
      })
      this.discrepancyRows = rows
    },
    initQuantityRows() {
      const rows = this.resolveActionRows()
      this.quantityRows = rows.map(row => {
        const remaining = this.resolveRemaining(row)
        const stockCheckInput = this.isStockCheckQuantityAction()
        return {
          detailId: row.detailId || row.transferDetailId || row.checkDetailId,
          productName: row.productName,
          productCode: row.productCode,
          remaining,
          meta: stockCheckInput
            ? (remaining === "" ? "必须录入实盘数量" : "已录实盘 " + remaining)
            : "剩余 " + remaining,
          quantity: remaining,
          recountQty: row.recountQty == null ? "" : row.recountQty,
          recountRequired: row.recountRequired,
          snapshotVersion: row.snapshotVersion,
          previousActualQty: row.previousActualQty,
          previousRecountQty: row.previousRecountQty,
          needsSnapshotReview: row.needsSnapshotReview,
          rejectedQuantity: 0,
          damagedQuantity: 0,
          discrepancyNote: "",
          attachmentRefs: ""
        }
      }).filter(row => row.detailId !== undefined && (this.isStockCheckQuantityAction() || row.quantity > 0))
    },
    resolveActionRows() {
      if (this.action.id === "receiveTransferShipment") {
        const shipment = this.shipmentOptions.find(item => String(item.shipmentId) === String(this.shipmentId))
        return shipment ? (shipment.details || []) : []
      }
      if (["saveStockCheckInput", "submitStockCheck"].indexOf(this.action.id) > -1) {
        return this.rawItem.details || []
      }
      if (this.action.id === "deliverDeliveryNoticeAll") {
        return filterDeliveryNoticeRowsByWarehouse(this.rawItem.details || [], this.selectedDeptId)
      }
      if (["receivePurchaseAll", "confirmTransferSource", "deliverTransferAll"].indexOf(this.action.id) > -1) {
        return this.rawItem.details || []
      }
      return []
    },
    resolveRemaining(row) {
      if (this.isStockCheckQuantityAction()) {
        return resolveStockCheckInputQuantity(row)
      }
      const total = Number(row.quantity || row.noticeQty || row.shippedQuantity || 0)
      const done = Number(row.receivedQuantity || row.deliveredQuantity || row.deliveredQty || 0)
      return Math.max(Number((total - done).toFixed(2)), 0)
    },
    isStockCheckQuantityAction() {
      return ["saveStockCheckInput", "submitStockCheck"].indexOf(this.action.id) > -1
    },
    requiresQuantityRows() {
      return [
        "receivePurchaseAll",
        "deliverDeliveryNoticeAll",
        "confirmTransferSource",
        "deliverTransferAll",
        "receiveTransferShipment",
        "saveStockCheckInput",
        "submitStockCheck"
      ].indexOf(this.action.id) > -1
    },
    handleStockCheckActualChange(row) {
      if (this.isStockCheckQuantityAction()) row.recountQty = ""
    },
    normalizeQuantityRows() {
      return this.quantityRows.map(row => {
        const quantity = Number(row.quantity)
        const normalized = {
          detailId: row.detailId,
          quantity
        }
        if (this.isStockCheckQuantityAction()) {
          if (row.snapshotVersion != null) normalized.snapshotVersion = row.snapshotVersion
          normalized.recountQty = row.recountQty === "" || row.recountQty == null ? null : Number(row.recountQty)
        }
        if (this.showTransferReceipt) {
          normalized.receiveQuantity = quantity
          normalized.rejectedQuantity = Number(row.rejectedQuantity)
          normalized.damagedQuantity = Number(row.damagedQuantity)
          normalized.discrepancyNote = String(row.discrepancyNote || "").trim()
          normalized.attachmentRefs = String(row.attachmentRefs || "").trim()
        }
        return normalized
      }).filter(row => row.detailId !== undefined && Number.isFinite(row.quantity))
    },
    validateQuantityRows() {
      if (!this.requiresQuantityRows()) return true
      if (!this.quantityRows.length) {
        this.error = "请先选择可处理明细"
        return false
      }
      if (this.showTransferReceipt) {
        for (let index = 0; index < this.quantityRows.length; index += 1) {
          const row = this.quantityRows[index]
          const accepted = Number(row.quantity)
          const rejected = Number(row.rejectedQuantity)
          const damaged = Number(row.damagedQuantity)
          const remaining = Number(row.remaining)
          const itemName = row.productName || row.productCode || row.detailId
          if (![accepted, rejected, damaged, remaining].every(Number.isFinite) ||
            [accepted, rejected, damaged].some(value => value < 0)) {
            this.error = itemName + "：收货分类数量必须是非负数"
            return false
          }
          const classified = accepted + rejected + damaged
          if (classified > remaining + 0.000001) {
            this.error = itemName + "：验收入库、拒收和残损合计不能超过待收数量"
            return false
          }
          if ((classified < remaining - 0.000001 || rejected > 0 || damaged > 0) &&
            !String(row.discrepancyNote || "").trim()) {
            this.error = itemName + "：有短少、拒收或残损时必须填写差异说明"
            return false
          }
        }
        return true
      }
      const allowZero = ["saveStockCheckInput", "submitStockCheck", "confirmTransferSource"].indexOf(this.action.id) > -1
      const invalidRow = this.quantityRows.find(row => {
        const rawQuantity = row.quantity
        if (rawQuantity === "" || rawQuantity === null || rawQuantity === undefined) return true
        const quantity = Number(rawQuantity)
        return row.detailId === undefined || !Number.isFinite(quantity) ||
          (!this.isStockCheckQuantityAction() && quantity > Number(row.remaining)) ||
          (allowZero ? quantity < 0 : quantity <= 0) ||
          (this.isStockCheckQuantityAction() && row.recountQty !== "" && row.recountQty != null &&
            (!Number.isFinite(Number(row.recountQty)) || Number(row.recountQty) < 0))
      })
      if (invalidRow) {
        this.error = this.action.id === "confirmTransferSource"
          ? "请逐行填写0到申请数量之间的可调数量"
          : (allowZero ? "请逐行录入实盘数量，实盘数量不能小于0" : "处理数量必须大于0")
        return false
      }
      if (this.action.id === "confirmTransferSource") {
        const hasRemainder = this.quantityRows.some(row => Number(row.quantity) < Number(row.remaining))
        if (hasRemainder && !String(this.comment || "").trim()) {
          this.error = "有部分或全部无法调出时，请填写说明"
          return false
        }
      }
      return true
    },
    transferReceiptShortage(row) {
      const remaining = Number(row && row.remaining)
      const accepted = Number(row && row.quantity)
      const rejected = Number(row && row.rejectedQuantity)
      const damaged = Number(row && row.damagedQuantity)
      if (![remaining, accepted, rejected, damaged].every(Number.isFinite)) return "-"
      return Math.max(Number((remaining - accepted - rejected - damaged).toFixed(2)), 0)
    },
    validateDiscrepancyRows() {
      if (!this.discrepancyRows.length) {
        this.error = "当前差异台账没有可处置类别，请刷新后重试"
        return false
      }
      const invalid = this.discrepancyRows.find(row => {
        const validDecision = (row.decisionOptions || []).some(option => option.value === row.decision)
        const needsAttachment = row.attachmentRequired && ["RETURN_SOURCE", "WRITE_OFF"].indexOf(row.decision) > -1
        return !validDecision || (needsAttachment && !require("@/utils/transferEvidence").hasEvidence(row.attachmentRefs))
      })
      if (invalid) {
        this.error = invalid.attachmentRequired
          ? invalid.itemName + "：残损终结处置必须上传或选择凭证附件"
          : invalid.itemName + "：请选择该类别允许的处置决定"
        return false
      }
      return true
    },
    normalizeDiscrepancyRows() {
      return this.discrepancyRows.map(row => ({
        detailId: row.detailId,
        category: row.category,
        decision: row.decision,
        quantity: Number(row.quantity),
        note: String(row.note || "").trim(),
        attachmentRefs: String(row.attachmentRefs || "").trim()
      }))
    },
    isQualitySelected(row) {
      return !!row && (row.qcSelected === true || (row.qcSelected === undefined && Number(row.inspectedQuantity) > 0))
    },
    setQualitySelection(selected) {
      this.qualityRows.forEach(row => this.$set(row, 'qcSelected', selected))
    },
    fillSelectedQualityPassed() {
      this.qualityRows.filter(this.isQualitySelected).forEach(row => {
        const inspected = Number(row.inspectedQuantity), pending = Number(row.pendingQuantity)
        row.inspectedQuantity = Number.isFinite(inspected) && inspected > 0 && inspected <= pending ? inspected : pending
        row.acceptedQuantity = row.inspectedQuantity
        row.rejectedQuantity = 0; row.concessionQuantity = 0
        row.defectLevel = ''; row.defectReason = ''
      })
    },
    clearSelectedQualityResults() {
      this.qualityRows.filter(this.isQualitySelected).forEach(row => {
        row.acceptedQuantity = 0; row.rejectedQuantity = 0; row.concessionQuantity = 0
      })
    },
    buildQualityItems() {
      if (!this.showQuality || this.qualityLegacyMode) return []
      if (this.qualityLoading || this.qualityLoadFailed || !this.receiptBatchId || !this.qualityRows.length) {
        this.error = this.error || "请先加载可质检明细"
        return null
      }
      const items = []
      for (let index = 0; index < this.qualityRows.length; index += 1) {
        const row = this.qualityRows[index]
        if (!this.isQualitySelected(row)) continue
        const inspected = Number(row.inspectedQuantity)
        const accepted = Number(row.acceptedQuantity)
        const rejected = Number(row.rejectedQuantity)
        const concession = Number(row.concessionQuantity)
        const itemName = row.itemName || row.itemCode || row.batchDetailId
        if (![inspected, accepted, rejected, concession].every(Number.isFinite) ||
            [inspected, accepted, rejected, concession].some(value => value < 0)) {
          this.error = itemName + "：质检数量必须是非负数"
          return null
        }
        if (inspected <= 0 || !Number.isFinite(Number(row.pendingQuantity)) || inspected > Number(row.pendingQuantity)) {
          this.error = itemName + "：本次检验数量不能超过待检数量"
          return null
        }
        if (Math.abs(accepted + rejected + concession - inspected) > 0.000001) {
          this.error = itemName + "：合格+拒收+让步必须等于本次检验"
          return null
        }
        if ((rejected > 0 || concession > 0) && !String(row.defectReason || "").trim()) {
          this.error = itemName + "：请填写拒收/让步原因"
          return null
        }
        items.push({
          batchDetailId: row.batchDetailId,
          inspectedQuantity: inspected,
          acceptedQuantity: accepted,
          rejectedQuantity: rejected,
          concessionQuantity: concession,
          defectLevel: row.defectLevel || undefined,
          defectReason: String(row.defectReason || "").trim() || undefined
        })
      }
      if (!items.length) {
        this.error = "请至少勾选并完成一条质检明细"
        return null
      }
      return items
    },
    confirm() {
      const evidenceRows = this.showTransferDiscrepancy ? this.discrepancyRows : this.showTransferReceipt ? this.quantityRows : []
      if (evidenceRows.some(row => row.evidenceState && !row.evidenceState.valid)) {
        this.error = "凭证尚未上传完成、不可用或未选择，请先处理附件"
        return
      }
      if (this.action.requiresComment && !this.comment) {
        this.error = "处理意见不能为空"
        return
      }
      if (this.showReceiveMetadata && !String(this.arrivedTime || "").trim()) {
        this.error = "请选择实际到货时间"
        return
      }
      if (this.showTransferDiscrepancy) {
        if (!this.discrepancyId) {
          this.error = "请选择待处理差异单"
          return
        }
        if (!this.discrepancyRequestId) {
          this.error = "差异处置 requestId 缺失，请重新打开"
          return
        }
        if (!this.validateDiscrepancyRows()) {
          return
        }
      }
      if (!this.validateQuantityRows()) return
      if (this.showQuality && this.qualityLegacyMode) {
        if (!this.qcResult) {
          this.error = "请选择质检结果"
          return
        }
        if (["rejected", "concession"].indexOf(this.qcResult) > -1 && !this.comment) {
          this.error = "拒收或让步接收必须填写原因"
          return
        }
      }
      const qualityItems = this.buildQualityItems()
      if (this.showQuality && !this.qualityLegacyMode && !qualityItems) return
      const quantityRows = this.normalizeQuantityRows()
      const payload = {
        comment: this.comment,
        qcResult: this.qcResult,
        qcRemark: this.comment,
        receiptBatchId: this.receiptBatchId,
        qualityItems,
        warehouseId: this.selectedDeptId,
        shipmentId: this.shipmentId,
        arrivedTime: this.showReceiveMetadata ? normalizeReceiveTime(this.arrivedTime) : undefined,
        supplierBatchNo: this.showReceiveMetadata ? this.supplierBatchNo : undefined,
        deliveryNoteNo: this.showReceiveMetadata ? this.deliveryNoteNo : undefined,
        remark: this.showReceiveMetadata ? this.receiveRemark : undefined,
        discrepancyId: this.showTransferDiscrepancy ? this.discrepancyId : undefined,
        requestId: this.showTransferDiscrepancy ? this.discrepancyRequestId : undefined,
        responsibleParty: this.showTransferDiscrepancy
          ? this.discrepancyResponsibleParty
          : undefined,
        note: this.showTransferDiscrepancy ? this.comment : undefined,
        allRemaining: true,
        items: this.showTransferDiscrepancy ? this.normalizeDiscrepancyRows() : quantityRows,
        details: quantityRows.map(row => ({ detailId: row.detailId, actualQuantity: row.quantity, recountQty: row.recountQty, ...(row.snapshotVersion == null ? {} : { snapshotVersion: row.snapshotVersion }) }))
      }
      this.$emit("confirm", payload)
    }
  }
}
</script>

<style scoped lang="scss">
@import "./mobileSheet.scss";

.action-review-summary {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid rgba(79, 70, 229, .2);
  border-radius: 12px;
  background: rgba(238, 242, 255, .72);
}

.action-review-summary > span,
.action-review-summary > small {
  color: #64748b;
  font-size: 12px;
}

.action-review-summary > strong {
  color: #1e293b;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.action-review-summary > p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.action-review-fields {
  display: grid;
  gap: 0;
  margin: 6px 0 0;
}

.action-review-fields > div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0;
  border-top: 1px solid rgba(100, 116, 139, .16);
}

.action-review-fields dt,
.action-review-fields dd {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.action-review-fields dt {
  color: #64748b;
  font-weight: 800;
}

.action-review-fields dd {
  color: #1e293b;
  font-weight: 900;
}

.quality-selection-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.quality-selection-actions label { display: flex; gap: 6px; align-items: center; }
.quality-selection-actions button { min-height: 36px; padding: 6px 10px; }
.mobile-quality-list {
  display: grid;
  gap: 12px;
}

.mobile-receive-fields {
  display: grid;
  gap: 12px;
}

.mobile-discrepancy-fields {
  display: grid;
  gap: 12px;
}

.mobile-transfer-receipt-list {
  display: grid;
  gap: 12px;
}

.mobile-transfer-receipt-row {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, .28);
  border-radius: 12px;
}

.mobile-transfer-receipt-row > header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.mobile-transfer-receipt-row > header span,
.mobile-transfer-shortage span {
  color: #64748b;
  font-size: 12px;
}

.mobile-transfer-receipt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mobile-transfer-shortage {
  display: grid;
  align-content: center;
  gap: 4px;
  min-height: 44px;
}

.mobile-quality-row {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, .28);
  border-radius: 12px;
  background: rgba(248, 250, 252, .78);
}

.mobile-quality-row > header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.mobile-quality-row > header span {
  color: #64748b;
  font-size: 12px;
}

.mobile-quality-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
</style>

<style lang="scss">
body.mobile-action-dialog-open .bottom-nav,
body.mobile-action-dialog-open .mobile-profile-bottom-nav {
  visibility: hidden;
  pointer-events: none;
}
</style>
