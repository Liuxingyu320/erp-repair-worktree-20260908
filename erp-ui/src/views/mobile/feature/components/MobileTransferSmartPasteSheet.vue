<template>
  <section
    v-if="open"
    ref="smartPasteMask"
    class="smart-paste-mask"
    @click.self="closeSheet"
  >
    <article
      ref="smartPasteDialog"
      class="smart-paste-sheet"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-smart-paste-title"
      tabindex="-1"
    >
      <header class="smart-paste-head">
        <div>
          <span>批量录入</span>
          <strong id="mobile-smart-paste-title">粘贴清单智能匹配</strong>
        </div>
        <button type="button" aria-label="关闭智能匹配" @click="closeSheet">
          <i class="el-icon-close" aria-hidden="true" />
        </button>
      </header>

      <div class="smart-paste-body">
        <p class="smart-paste-intro">
          每行粘贴“物料名称 + 数量 + 单位”。所有物料（包括唯一同名）都必须由你手动选择，选择后仍可改选；单位、规格和参考成本价以商品档案为准，只能调整数量。
        </p>
        <label class="smart-paste-input">
          <span>清单与收货信息</span>
          <textarea
            ref="pasteInput"
            v-model="pasteText"
            rows="7"
            maxlength="12000"
            placeholder="蜂蜜20瓶&#10;大红袍2包&#10;&#10;收件人：示例收件人 电话：138****0000&#10;收货地址：示例省示例市示例区示例路 1 号"
            @paste="handlePaste"
          />
        </label>
        <div class="smart-paste-actions">
          <button type="button" class="primary" :disabled="loading" @click="parseAndMatch">
            {{ loading ? "正在匹配…" : "解析并匹配" }}
          </button>
          <span>{{ scopeText }}</span>
        </div>

        <p v-if="errorMessage" class="smart-paste-error" role="alert">{{ errorMessage }}</p>
        <p v-if="warningText" class="smart-paste-warning" role="status">{{ warningText }}</p>

        <template v-if="parsed">
          <section class="smart-paste-recipient" aria-label="收货信息">
            <label>
              <span>收件人</span>
              <input v-model.trim="recipient.recipientName" type="text" maxlength="64" autocomplete="name" placeholder="收件人姓名">
            </label>
            <label>
              <span>联系电话</span>
              <input v-model.trim="recipient.recipientPhone" type="tel" maxlength="32" autocomplete="tel" placeholder="手机或座机号码">
            </label>
            <label class="recipient-address">
              <span>收货地址</span>
              <textarea v-model.trim="recipient.shippingAddress" rows="2" maxlength="500" autocomplete="street-address" placeholder="详细收货地址" />
            </label>
          </section>

          <div class="smart-paste-summary" role="status" aria-live="polite">
            <span class="success">唯一同名 {{ exactCount }}</span>
            <span v-if="pendingCount" class="pending">待选择 {{ pendingCount }}</span>
            <span v-if="unmatchedCount" class="danger">未找到 {{ unmatchedCount }}</span>
            <b>已确认 {{ selectedCount }} / {{ rows.length }}</b>
          </div>

          <div class="smart-paste-match-list">
            <article v-for="row in rows" :key="row.lineNo" class="smart-paste-match-card">
              <header>
                <div>
                  <strong>{{ row.requestedName }}</strong>
                  <span>粘贴单位 {{ row.requestedUnit }}</span>
                </div>
                <em :class="stateClass(row.matchState)">{{ stateLabel(row.matchState) }}</em>
              </header>
              <div class="smart-paste-quantity-row">
                <span>调整数量</span>
                <div class="smart-paste-quantity-control" :class="{ invalid: !validQuantity(row) }">
                  <button
                    type="button"
                    :aria-label="'减少' + row.requestedName + '数量'"
                    :disabled="!validQuantity(row) || number(row.quantity) <= 0.01"
                    @click="adjustQuantity(row, -1)"
                  >−</button>
                  <input
                    v-model.number="row.quantity"
                    type="number"
                    min="0.01"
                    step="0.01"
                    inputmode="decimal"
                    :aria-label="row.requestedName + '数量'"
                    :aria-invalid="String(!validQuantity(row))"
                  >
                  <span>{{ displayUnit(row) }}</span>
                  <button
                    type="button"
                    :aria-label="'增加' + row.requestedName + '数量'"
                    @click="adjustQuantity(row, 1)"
                  >+</button>
                </div>
              </div>
              <label v-if="row.candidates.length">
                <span class="sr-only">为 {{ row.requestedName }} 选择匹配物料</span>
                <select v-model="row.selectedKey" :aria-label="'为' + row.requestedName + '选择匹配物料'">
                  <option value="">请选择正确的物料和规格（选择后仍可改选）</option>
                  <option v-for="option in row.candidates" :key="option.key" :value="option.key">
                    {{ candidateLabel(option.candidate) }}
                  </option>
                </select>
              </label>
              <p v-else class="unmatched">没有达到相似度阈值的候选，请检查名称后重新解析。</p>
              <div v-if="row.selectedKey" class="smart-paste-reference-subtotal">
                <span>参考小计</span>
                <strong>{{ referenceSubtotalText(row) }}</strong>
              </div>
              <p v-if="!validQuantity(row)" class="row-warning">数量必须大于 0。</p>
              <p v-if="unitWarning(row)" class="row-warning">
                粘贴单位“{{ row.requestedUnit }}”与档案单位“{{ selectedUnit(row) }}”不同，数量已按档案单位计算。
              </p>
              <p v-if="quantityWarning(row)" class="row-warning">
                {{ quantityWarningText }}
              </p>
            </article>
          </div>
        </template>
      </div>

      <footer class="smart-paste-footer">
        <span>{{ footerSummary }}</span>
        <button type="button" @click="closeSheet">取消</button>
        <button type="button" class="primary" :disabled="!canApply || loading" @click="applySelection">加入明细</button>
      </footer>
    </article>
  </section>
</template>

<script>
import { listStock } from "@/api/inventory/stock"
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"

const {
  adjustTransferQuantity,
  candidateKey,
  formatReferenceCostPrice,
  formatReferenceSubtotal,
  isValidTransferQuantity,
  matchTransferPasteItems,
  normalizeUnit,
  parseTransferPaste,
  selectedCandidate
} = require("@/utils/transferSmartPaste")
const { mobileErrorMessage } = require("@/views/mobile/mobileErrorMessage")

export default {
  name: "MobileTransferSmartPasteSheet",
  props: {
    open: { type: Boolean, default: false },
    warehouseId: [Number, String],
    inventoryDeptId: [Number, String],
    transferType: { type: String, default: "warehouse" },
    initialRecipient: { type: Object, default: () => ({}) },
    allowedItemTypes: { type: Array, default: () => ["product", "gift"] }
  },
  data() {
    return {
      pasteText: "",
      loading: false,
      parsed: false,
      rows: [],
      ignoredLines: [],
      loadWarnings: [],
      errorMessage: "",
      recipient: { recipientName: "", recipientPhone: "", shippingAddress: "" },
      requestId: 0,
      dialogFocusManager: null,
      mountParent: null,
      mountNextSibling: null,
      overlayMounted: false
    }
  },
  computed: {
    isReturn() {
      return this.transferType === "store_return"
    },
    isCrossStore() {
      return this.transferType === "cross_store"
    },
    scopeText() {
      if (this.isReturn) return "匹配当前返仓门店可用库存"
      if (this.isCrossStore) return "匹配所选调出门店可用库存"
      return "仅匹配所选补货仓库可用库存"
    },
    quantityWarningText() {
      return this.isReturn || this.isCrossStore
        ? "申请数量超过来源可用库存，请调整数量。"
        : "申请数量超过当前可用库存，仍可作为缺货需求加入，请核对。"
    },
    exactCount() {
      return this.rows.filter(row => row.matchState === "exact").length
    },
    pendingCount() {
      return this.rows.filter(row => row.candidates.length && !row.selectedKey).length
    },
    unmatchedCount() {
      return this.rows.filter(row => !row.candidates.length).length
    },
    selectedCount() {
      return this.rows.filter(row => !!row.selectedKey).length
    },
    invalidQuantityCount() {
      return this.rows.filter(row => !this.validQuantity(row)).length
    },
    canApply() {
      return this.parsed && this.selectedCount > 0 && this.pendingCount === 0 && this.invalidQuantityCount === 0
    },
    warningText() {
      const ignored = this.ignoredLines.map(row => "第" + row.lineNo + "行「" + row.rawLine + "」未识别")
      return ignored.concat(this.loadWarnings).join("；")
    },
    footerSummary() {
      if (!this.parsed) return "粘贴后将自动开始匹配"
      if (this.invalidQuantityCount) return "有 " + this.invalidQuantityCount + " 行数量必须大于 0"
      if (this.pendingCount) return "还有 " + this.pendingCount + " 行物料需要选择"
      return "可加入 " + this.selectedCount + " 行"
    }
  },
  watch: {
    open: {
      immediate: true,
      handler(value) {
        if (value) {
          this.resetSheet()
          this.$nextTick(() => {
            if (!this.open) return
            this.lockOverlay()
            this.portalToBody()
            this.activateFocus()
          })
        } else {
          this.deactivateFocus()
          this.releaseOverlay()
          this.restoreMount()
        }
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.smartPasteDialog,
      getInitialFocus: () => this.$refs.pasteInput || this.$refs.smartPasteDialog,
      onEscape: () => this.closeSheet()
    })
  },
  beforeDestroy() {
    this.requestId += 1
    this.deactivateFocus()
    this.releaseOverlay()
    this.restoreMount()
  },
  methods: {
    resetSheet() {
      this.requestId += 1
      this.pasteText = ""
      this.loading = false
      this.parsed = false
      this.rows = []
      this.ignoredLines = []
      this.loadWarnings = []
      this.errorMessage = ""
      this.recipient = {
        recipientName: this.initialRecipient.recipientName || "",
        recipientPhone: this.initialRecipient.recipientPhone || "",
        shippingAddress: this.initialRecipient.shippingAddress || ""
      }
    },
    handlePaste(event) {
      const clipboard = event && event.clipboardData
      const value = clipboard && typeof clipboard.getData === "function" ? clipboard.getData("text") : ""
      if (!value) return
      event.preventDefault()
      this.pasteText = value
      this.$nextTick(() => this.parseAndMatch())
    },
    parseAndMatch() {
      const result = parseTransferPaste(this.pasteText)
      if (!result.items.length) {
        this.parsed = false
        this.rows = []
        this.errorMessage = "没有识别到物料行，请按“物料名称20瓶”的格式每行填写一项。"
        return
      }
      this.errorMessage = ""
      this.parsed = false
      this.rows = []
      this.ignoredLines = result.ignoredLines || []
      this.loadWarnings = []
      this.recipient = {
        recipientName: result.recipient.recipientName || this.initialRecipient.recipientName || "",
        recipientPhone: result.recipient.recipientPhone || this.initialRecipient.recipientPhone || "",
        shippingAddress: result.recipient.shippingAddress || this.initialRecipient.shippingAddress || ""
      }

      const requestId = this.requestId + 1
      this.requestId = requestId
      this.loading = true
      this.loadCandidatePool().then(pool => {
        if (requestId !== this.requestId || !this.open) return
        const candidates = pool.candidates
        this.loadWarnings = pool.warnings
        this.rows = matchTransferPasteItems(result.items, candidates)
        this.parsed = true
        if (!candidates.length) this.errorMessage = "所选来源组织没有可匹配的可用库存。"
      }).catch(error => {
        if (requestId !== this.requestId) return
        this.errorMessage = error && error.message ? error.message : "加载物料候选失败，请重试。"
      }).finally(() => {
        if (requestId === this.requestId) this.loading = false
      })
    },
    loadCandidatePool() {
      const warnings = []
      const stockRequests = this.allowedItemTypes.map(itemType => this.loadCandidateGroup(
        itemType === "gift" ? "礼盒库存" : "商品库存",
        this.fetchAllPages(listStock, {
          itemType,
          shopDeptId: this.inventoryDeptId || this.warehouseId,
          warehouseId: this.warehouseId || this.inventoryDeptId,
          stockStatus: "available",
          transferSource: true
        }, 1, [], warnings)
      ))
      return Promise.all(stockRequests).then(results => {
        const failed = results.filter(result => !!result.error)
        const candidates = [].concat(...results.filter(result => !result.error).map(result => {
          return result.rows.map(row => this.stockCandidate(row))
        }))
        failed.forEach(result => {
          warnings.push(result.label + "加载失败：" + this.sourceInventoryErrorMessage(result.error))
        })
        const mergedCandidates = this.mergeCandidates(candidates)
        if (failed.length && !mergedCandidates.length) {
          throw new Error("来源库存加载失败：" + failed.map(result => {
            return result.label + "：" + this.sourceInventoryErrorMessage(result.error)
          }).join("；"))
        }
        return { candidates: mergedCandidates, warnings }
      })
    },
    fetchAllPages(api, baseQuery, pageNum = 1, collected = [], warnings = []) {
      const pageSize = 200
      return api(Object.assign({}, baseQuery, { pageNum, pageSize }), { silentError: true }).then(response => {
        const pageRows = response && Array.isArray(response.rows) ? response.rows : []
        const rows = collected.concat(pageRows)
        const total = Number(response && response.total)
        const hasMore = Number.isFinite(total) ? rows.length < total : pageRows.length === pageSize
        if (!hasMore || !pageRows.length) return rows
        if (pageNum >= 50) {
          warnings.push("候选物料超过10000条，仅匹配前10000条")
          return rows
        }
        return this.fetchAllPages(api, baseQuery, pageNum + 1, rows, warnings)
      })
    },
    loadCandidateGroup(label, promise) {
      return promise.then(rows => ({ label, rows, error: null })).catch(error => ({ label, rows: [], error }))
    },
    sourceInventoryErrorMessage(error) {
      return mobileErrorMessage(error, "请检查网络后重试")
    },
    stockCandidate(row) {
      const itemType = row.itemType || "product"
      const itemId = row.itemId || row.productId || row.giftId
      const itemName = row.itemName || row.productName || row.giftName || ""
      const itemCode = row.itemCode || row.productCode || row.giftCode || ""
      return {
        itemType,
        itemId,
        itemName,
        itemCode,
        productId: itemType === "product" ? itemId : null,
        productName: itemName,
        productCode: itemCode,
        internalTeaName: row.internalTeaName || "",
        costPrice: row.costPrice,
        referenceCostPrice: row.referenceCostPrice,
        unit: row.itemUnit || row.unit || "",
        spec: row.itemSpec || row.spec || "",
        grade: row.itemGrade || row.grade || "",
        availableQuantity: this.availableQuantity(row),
        availabilityStatus: "loaded",
        source: "stock"
      }
    },
    mergeCandidates(candidates) {
      const map = new Map()
      ;(candidates || []).forEach(candidate => {
        const key = candidateKey(candidate)
        if (!key) return
        const existing = map.get(key)
        if (!existing) {
          map.set(key, Object.assign({}, candidate))
          return
        }
        existing.availableQuantity = this.number(existing.availableQuantity) + this.number(candidate.availableQuantity)
        ;["itemName", "itemCode", "productName", "productCode", "internalTeaName", "unit", "spec", "grade"].forEach(field => {
          if (!existing[field] && candidate[field]) existing[field] = candidate[field]
        })
        if ((existing.costPrice === undefined || existing.costPrice === null) && candidate.costPrice !== undefined) {
          existing.costPrice = candidate.costPrice
        }
        if (candidate.referenceCostPrice !== undefined && candidate.referenceCostPrice !== null &&
          (existing.referenceCostPrice === undefined || existing.referenceCostPrice === null)) {
          existing.referenceCostPrice = candidate.referenceCostPrice
        }
      })
      return Array.from(map.values())
    },
    selected(row) {
      return selectedCandidate(row)
    },
    selectedUnit(row) {
      const candidate = this.selected(row)
      return candidate && candidate.unit ? candidate.unit : ""
    },
    displayUnit(row) {
      return this.selectedUnit(row) || (row && row.requestedUnit) || ""
    },
    referenceSubtotalText(row) {
      const candidate = this.selected(row)
      if (!candidate) return ""
      if (!this.validQuantity(row)) return "数量有效后自动计算"
      return formatReferenceSubtotal(row.quantity, candidate.referenceCostPrice, this.displayUnit(row)) || "参考成本价未维护，暂无法计算"
    },
    unitWarning(row) {
      const requested = normalizeUnit(row && row.requestedUnit)
      const selected = normalizeUnit(this.selectedUnit(row))
      return !!(requested && selected && requested !== selected)
    },
    quantityWarning(row) {
      const candidate = this.selected(row)
      return !!(candidate && this.number(row.quantity) > this.number(candidate.availableQuantity))
    },
    validQuantity(row) {
      return isValidTransferQuantity(row && row.quantity)
    },
    adjustQuantity(row, direction) {
      this.$set(row, "quantity", adjustTransferQuantity(row && row.quantity, direction))
    },
    availableQuantity(row) {
      if (row.availableQuantity !== undefined && row.availableQuantity !== null && row.availableQuantity !== "") return row.availableQuantity
      return Math.max(this.number(row.currentQuantity) - this.number(row.lockedQuantity), 0)
    },
    number(value) {
      const result = Number(value)
      return Number.isFinite(result) ? result : 0
    },
    formatQuantity(value) {
      const result = this.number(value)
      return Number.isInteger(result) ? String(result) : result.toFixed(2).replace(/0+$/, "").replace(/\.$/, "")
    },
    candidateLabel(candidate) {
      const type = candidate.itemType === "gift" ? "礼盒" : "商品"
      const identity = [candidate.itemName || candidate.productName, candidate.itemCode || candidate.productCode].filter(Boolean).join(" · ")
      const meta = [candidate.spec, candidate.grade, candidate.unit].filter(Boolean).join(" / ")
      const stock = "可用 " + this.formatQuantity(candidate.availableQuantity)
      const referenceCost = formatReferenceCostPrice(candidate.referenceCostPrice)
      return ["[" + type + "] " + identity, meta, referenceCost, stock].filter(Boolean).join(" · ")
    },
    stateLabel(state) {
      return { exact: "唯一同名", duplicate: "同名多项", similar: "相似候选", unmatched: "未找到" }[state] || "待确认"
    },
    stateClass(state) {
      return state === "exact" ? "success" : state === "unmatched" ? "danger" : "pending"
    },
    applySelection() {
      if (this.invalidQuantityCount) {
        this.errorMessage = "数量必须大于 0，请修改后再加入明细。"
        return
      }
      if (this.pendingCount) {
        this.errorMessage = "请先判断所有同名或相似物料。"
        return
      }
      const items = this.rows.filter(row => !!row.selectedKey).map(row => ({
        candidate: this.selected(row),
        quantity: row.quantity
      })).filter(row => !!row.candidate)
      if (!items.length) {
        this.errorMessage = "没有已确认的物料可以加入。"
        return
      }
      this.$emit("apply", {
        items,
        recipient: Object.assign({}, this.recipient),
        unmatchedCount: this.unmatchedCount
      })
      this.closeSheet()
    },
    closeSheet() {
      this.requestId += 1
      this.loading = false
      this.restoreMount()
      this.$emit("update:open", false)
      this.$emit("close")
    },
    lockOverlay() {
      if (this.overlayMounted) return
      mountMobileOverlay("mobile-transfer-smart-paste-open")
      this.overlayMounted = true
    },
    releaseOverlay() {
      if (!this.overlayMounted) return
      releaseMobileOverlay("mobile-transfer-smart-paste-open")
      this.overlayMounted = false
    },
    portalToBody() {
      const mask = this.$refs.smartPasteMask
      if (typeof document === "undefined" || !document.body || !mask || mask.parentNode === document.body) return false
      this.mountParent = mask.parentNode
      this.mountNextSibling = mask.nextSibling
      document.body.appendChild(mask)
      return true
    },
    restoreMount() {
      const mask = this.$refs.smartPasteMask
      const parent = this.mountParent
      if (!mask || !parent || mask.parentNode === parent) return false
      const sibling = this.mountNextSibling
      if (sibling && sibling.parentNode === parent) parent.insertBefore(mask, sibling)
      else parent.appendChild(mask)
      this.mountParent = null
      this.mountNextSibling = null
      return true
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
.smart-paste-mask {
  position: fixed;
  inset: 0;
  z-index: 10060;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  padding: max(10px, env(safe-area-inset-top)) 10px max(10px, env(safe-area-inset-bottom));
  background: rgba(15, 23, 42, 0.42);
  box-sizing: border-box;
}

.smart-paste-sheet {
  width: min(100%, 430px);
  height: calc(100dvh - 20px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  max-height: 920px;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 24px;
  background: #fbfdfb;
  box-shadow: 0 20px 50px rgba(15, 32, 24, 0.24);
  overflow: hidden;
}

.smart-paste-head,
.smart-paste-footer {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  background: #fbfdfb;
}

.smart-paste-head {
  border-bottom: 1px solid rgba(28, 50, 38, 0.1);
}

.smart-paste-head div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.smart-paste-head span,
.smart-paste-actions span,
.smart-paste-footer span {
  color: #60746a;
  font-size: 12px;
  line-height: 1.4;
  font-weight: 800;
}

.smart-paste-head strong {
  color: #122019;
  font-size: 19px;
  line-height: 1.3;
}

.smart-paste-head > button {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  color: #4e6258;
  background: rgba(232, 250, 243, 0.7);
  font-size: 20px;
}

.smart-paste-body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 14px 16px 18px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.smart-paste-intro {
  margin: 0 0 12px;
  color: #50675b;
  font-size: 13px;
  line-height: 1.55;
  font-weight: 700;
}

.smart-paste-input,
.smart-paste-recipient label {
  display: grid;
  gap: 6px;
}

.smart-paste-input > span,
.smart-paste-recipient label > span {
  color: #315443;
  font-size: 13px;
  font-weight: 900;
}

.smart-paste-input textarea,
.smart-paste-recipient input,
.smart-paste-recipient textarea,
.smart-paste-match-card select {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(28, 50, 38, 0.16);
  border-radius: 12px;
  padding: 11px 12px;
  color: #122019;
  background: #fff;
  box-sizing: border-box;
  font: inherit;
}

.smart-paste-input textarea,
.smart-paste-recipient textarea {
  resize: vertical;
  line-height: 1.5;
}

.smart-paste-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
}

.smart-paste-actions button,
.smart-paste-footer button {
  min-height: 44px;
  border: 1px solid rgba(28, 50, 38, 0.14);
  border-radius: 12px;
  padding: 0 14px;
  color: #122019;
  background: #fff;
  font: inherit;
  font-weight: 900;
}

button.primary {
  border-color: transparent;
  color: #fff;
  background: var(--mobile-color-primary, #0b6b53);
}

button:disabled {
  opacity: 0.52;
}

.smart-paste-error,
.smart-paste-warning {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 800;
}

.smart-paste-error {
  color: #9f1239;
  background: #fff1f2;
}

.smart-paste-warning,
.row-warning,
.unmatched {
  color: #92400e;
  background: #fffbeb;
}

.smart-paste-recipient {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.recipient-address {
  grid-column: 1 / -1;
}

.smart-paste-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin: 14px 0 10px;
}

.smart-paste-summary span,
.smart-paste-match-card em {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  border-radius: 999px;
  padding: 0 9px;
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
}

.smart-paste-summary b {
  margin-left: auto;
  color: #315443;
  font-size: 12px;
}

.success {
  color: #065f46;
  background: #d1fae5;
}

.pending {
  color: #92400e;
  background: #fef3c7;
}

.danger {
  color: #9f1239;
  background: #ffe4e6;
}

.smart-paste-match-list {
  display: grid;
  gap: 10px;
}

.smart-paste-match-card {
  display: grid;
  gap: 9px;
  padding: 12px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 14px;
  background: #fff;
}

.smart-paste-match-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.smart-paste-match-card header div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.smart-paste-match-card header strong {
  overflow-wrap: anywhere;
  color: #122019;
  font-size: 15px;
}

.smart-paste-match-card header span {
  color: #60746a;
  font-size: 12px;
  font-weight: 800;
}

.smart-paste-match-card select {
  min-height: 48px;
  line-height: 1.4;
}

.smart-paste-reference-subtotal {
  display: grid;
  gap: 3px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  color: #315443;
  background: #edf8f2;
  font-size: 12px;
  line-height: 1.45;
}

.smart-paste-reference-subtotal span {
  font-weight: 800;
}

.smart-paste-reference-subtotal strong {
  color: #176246;
  font-size: 14px;
}

.smart-paste-quantity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.smart-paste-quantity-row > span {
  flex: 0 0 auto;
  color: #60746a;
  font-size: 12px;
  font-weight: 800;
}

.smart-paste-quantity-control {
  display: grid;
  grid-template-columns: 42px minmax(72px, 96px) auto 42px;
  align-items: center;
  overflow: hidden;
  border: 1px solid rgba(28, 50, 38, 0.18);
  border-radius: 10px;
  background: #fff;
}

.smart-paste-quantity-control.invalid {
  border-color: #e11d48;
}

.smart-paste-quantity-control button,
.smart-paste-quantity-control input {
  min-height: 42px;
  border: 0;
  background: transparent;
  color: #122019;
  font: inherit;
}

.smart-paste-quantity-control button {
  padding: 0;
  font-size: 22px;
  font-weight: 800;
}

.smart-paste-quantity-control button:disabled {
  color: #b7c2bc;
}

.smart-paste-quantity-control input {
  width: 100%;
  padding: 0 6px;
  border-right: 1px solid rgba(28, 50, 38, 0.1);
  border-left: 1px solid rgba(28, 50, 38, 0.1);
  outline: none;
  text-align: center;
  font-size: 16px;
  font-weight: 800;
  -moz-appearance: textfield;
}

.smart-paste-quantity-control input::-webkit-inner-spin-button,
.smart-paste-quantity-control input::-webkit-outer-spin-button {
  margin: 0;
  -webkit-appearance: none;
}

.smart-paste-quantity-control span {
  padding: 0 8px;
  color: #60746a;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.row-warning,
.unmatched {
  margin: 0;
  padding: 8px 10px;
  border-radius: 9px;
  font-size: 12px;
  line-height: 1.45;
  font-weight: 800;
}

.smart-paste-footer {
  border-top: 1px solid rgba(28, 50, 38, 0.1);
  padding-bottom: max(14px, env(safe-area-inset-bottom));
}

.smart-paste-footer span {
  min-width: 0;
  flex: 1 1 auto;
}

.smart-paste-footer button {
  flex: 0 0 auto;
}

.sr-only {
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

@media (max-width: 360px) {
  .smart-paste-recipient {
    grid-template-columns: 1fr;
  }

  .recipient-address {
    grid-column: auto;
  }

  .smart-paste-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .smart-paste-footer {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .smart-paste-footer span {
    grid-column: 1 / -1;
  }
}
</style>

<style lang="scss">
body.mobile-transfer-smart-paste-open {
  overflow: hidden;
}

body.mobile-transfer-smart-paste-open .bottom-nav {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}
</style>
