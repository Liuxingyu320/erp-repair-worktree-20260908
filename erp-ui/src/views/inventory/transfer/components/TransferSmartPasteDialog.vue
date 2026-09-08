<template>
  <el-dialog
    title="智能粘贴要货清单"
    :visible.sync="dialogVisible"
    width="980px"
    custom-class="smart-paste-dialog"
    append-to-body
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="smart-paste-intro">
      每行填写“物料名称 + 数量 + 单位”，并可在末尾附上收件人、电话和收货地址。所有物料（包括唯一同名）都必须由你手动选择，选择后仍可改选；单位、规格和参考成本价以商品档案为准，只能调整数量。
    </div>
    <el-input
      v-model="pasteText"
      type="textarea"
      :rows="7"
      resize="vertical"
      maxlength="12000"
      show-word-limit
      placeholder="例如：&#10;蜂蜜20瓶&#10;大红袍2包&#10;&#10;收件人：示例收件人 电话：138****0000&#10;收货地址：示例省示例市示例区示例路 1 号"
    />
    <div class="smart-paste-parse-actions">
      <el-button type="primary" size="small" icon="el-icon-magic-stick" :loading="loading" @click="parseAndMatch">
        解析并匹配
      </el-button>
      <span>匹配范围：{{ scopeText }}</span>
    </div>

    <template v-if="parsed">
      <el-alert
        v-if="ignoredLines.length || loadWarnings.length"
        :title="ignoredText"
        type="warning"
        show-icon
        :closable="false"
        class="smart-paste-alert"
      />
      <el-row :gutter="12" class="smart-paste-recipient">
        <el-col :xs="24" :sm="8">
          <el-input v-model.trim="recipient.recipientName" maxlength="64" clearable placeholder="收件人姓名">
            <template slot="prepend">收件人</template>
          </el-input>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-input v-model.trim="recipient.recipientPhone" maxlength="32" clearable placeholder="联系电话">
            <template slot="prepend">电话</template>
          </el-input>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-input v-model.trim="recipient.shippingAddress" maxlength="500" clearable placeholder="详细收货地址">
            <template slot="prepend">地址</template>
          </el-input>
        </el-col>
      </el-row>
      <div class="smart-paste-summary">
        <el-tag type="success" size="small">唯一同名 {{ exactCount }}</el-tag>
        <el-tag v-if="pendingCount" type="warning" size="small">待你选择 {{ pendingCount }}</el-tag>
        <el-tag v-if="unmatchedCount" type="danger" size="small">未找到 {{ unmatchedCount }}</el-tag>
        <span>已确认 {{ selectedCount }} / {{ rows.length }} 行</span>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" border class="smart-paste-table smart-paste-desktop-table" row-key="lineNo">
        <el-table-column label="粘贴内容" min-width="150">
          <template slot-scope="scope">
            <div class="smart-paste-request-name">{{ scope.row.requestedName }}</div>
            <div class="smart-paste-request-meta">粘贴单位 {{ scope.row.requestedUnit }}</div>
          </template>
        </el-table-column>
        <el-table-column label="调整数量" width="190">
          <template slot-scope="scope">
            <div class="smart-paste-quantity-editor">
              <el-input-number
                v-model="scope.row.quantity"
                :min="0.01"
                :step="1"
                :precision="2"
                size="small"
                :aria-label="scope.row.requestedName + '数量'"
              />
              <span>{{ displayUnit(scope.row) }}</span>
            </div>
            <div v-if="!validQuantity(scope.row)" class="smart-paste-quantity-error">数量必须大于 0</div>
          </template>
        </el-table-column>
        <el-table-column label="匹配状态" width="110">
          <template slot-scope="scope">
            <el-tag :type="stateType(scope.row.matchState)" size="mini">{{ stateLabel(scope.row.matchState) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配物料" min-width="360">
          <template slot-scope="scope">
            <el-select
              v-if="scope.row.candidates.length"
              v-model="scope.row.selectedKey"
              filterable
              popper-class="smart-paste-candidate-dropdown"
              placeholder="请选择正确的物料/规格（选择后仍可改选）"
              style="width:100%"
            >
              <el-option
                v-for="option in scope.row.candidates"
                :key="option.key"
                :label="candidateLabel(option.candidate)"
                :value="option.key"
              />
            </el-select>
            <span v-else class="smart-paste-unmatched">没有达到相似度阈值的候选，请检查名称后重新解析</span>
            <div v-if="scope.row.selectedKey" class="smart-paste-reference-subtotal">
              <span>参考小计</span>
              <strong>{{ referenceSubtotalText(scope.row) }}</strong>
            </div>
            <div v-if="unitWarning(scope.row)" class="smart-paste-unit-warning">
              粘贴单位“{{ scope.row.requestedUnit }}”与档案单位“{{ selectedUnit(scope.row) }}”不同，数量已按档案单位计算
            </div>
            <div v-if="quantityWarning(scope.row)" class="smart-paste-unit-warning">
              申请数量超过当前可用库存；门店要货可作为缺货需求提交，返仓/异店调货请调整数量
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-loading="loading" class="smart-paste-mobile-list">
        <div v-for="row in rows" :key="row.lineNo" class="smart-paste-mobile-card">
          <div class="smart-paste-mobile-card-head">
            <div>
              <div class="smart-paste-request-name">{{ row.requestedName }}</div>
              <div class="smart-paste-request-meta">粘贴单位 {{ row.requestedUnit }}</div>
            </div>
            <el-tag :type="stateType(row.matchState)" size="mini">{{ stateLabel(row.matchState) }}</el-tag>
          </div>
          <div class="smart-paste-mobile-quantity">
            <span>调整数量</span>
            <div class="smart-paste-quantity-editor">
              <el-input-number
                v-model="row.quantity"
                :min="0.01"
                :step="1"
                :precision="2"
                size="small"
                :aria-label="row.requestedName + '数量'"
              />
              <span>{{ displayUnit(row) }}</span>
            </div>
          </div>
          <div v-if="!validQuantity(row)" class="smart-paste-quantity-error">数量必须大于 0</div>
          <el-select
            v-if="row.candidates.length"
            v-model="row.selectedKey"
            filterable
            popper-class="smart-paste-candidate-dropdown"
            placeholder="请选择正确的物料/规格（选择后仍可改选）"
            class="smart-paste-mobile-select"
          >
            <el-option
              v-for="option in row.candidates"
              :key="option.key"
              :label="candidateLabel(option.candidate)"
              :value="option.key"
            />
          </el-select>
          <div v-else class="smart-paste-unmatched">没有达到相似度阈值的候选，请检查名称后重新解析</div>
          <div v-if="row.selectedKey" class="smart-paste-reference-subtotal">
            <span>参考小计</span>
            <strong>{{ referenceSubtotalText(row) }}</strong>
          </div>
          <div v-if="unitWarning(row)" class="smart-paste-unit-warning">
            粘贴单位“{{ row.requestedUnit }}”与档案单位“{{ selectedUnit(row) }}”不同，数量已按档案单位计算
          </div>
          <div v-if="quantityWarning(row)" class="smart-paste-unit-warning">
            申请数量超过当前可用库存；门店要货可作为缺货需求提交，返仓/异店调货请调整数量
          </div>
        </div>
      </div>
    </template>
    <div slot="footer">
      <span v-if="invalidQuantityCount" class="smart-paste-footer-error">有 {{ invalidQuantityCount }} 行数量必须大于 0</span>
      <span v-if="pendingCount" class="smart-paste-footer-warning">还有 {{ pendingCount }} 行物料需要选择</span>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="success" :disabled="!canApply" :loading="loading" @click="applySelection">加入要货明细</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { listStock } from "@/api/inventory/stock"

const {
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
  name: "TransferSmartPasteDialog",
  props: {
    visible: { type: Boolean, default: false },
    warehouseId: [Number, String],
    inventoryDeptId: [Number, String],
    transferType: { type: String, default: "warehouse" },
    initialRecipient: { type: Object, default: () => ({}) },
    allowedItemTypes: { type: Array, default: () => ["product", "gift"] }
  },
  data() {
    return {
      loading: false,
      parsed: false,
      pasteText: "",
      rows: [],
      ignoredLines: [],
      loadWarnings: [],
      recipient: { recipientName: "", recipientPhone: "", shippingAddress: "" },
      requestId: 0
    }
  },
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(value) { this.$emit("update:visible", value) }
    },
    isReturn() { return this.transferType === "store_return" },
    isCrossStore() { return this.transferType === "cross_store" },
    scopeText() {
      if (this.isReturn) return "当前返仓门店可用库存"
      if (this.isCrossStore) return "所选调出门店可用库存"
      return "仅匹配所选补货仓库可用库存"
    },
    exactCount() { return this.rows.filter(row => row.matchState === "exact").length },
    pendingCount() { return this.rows.filter(row => row.candidates.length && !row.selectedKey).length },
    unmatchedCount() { return this.rows.filter(row => !row.candidates.length).length },
    selectedCount() { return this.rows.filter(row => !!row.selectedKey).length },
    invalidQuantityCount() { return this.rows.filter(row => !this.validQuantity(row)).length },
    canApply() { return this.parsed && this.selectedCount > 0 && this.pendingCount === 0 && this.invalidQuantityCount === 0 },
    ignoredText() {
      const ignored = this.ignoredLines.map(row => `第${row.lineNo}行「${row.rawLine}」`)
      return ignored.concat(this.loadWarnings).join("；")
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        if (value) this.resetDialog()
      }
    }
  },
  methods: {
    resetDialog() {
      this.requestId += 1
      this.loading = false
      this.parsed = false
      this.pasteText = ""
      this.rows = []
      this.ignoredLines = []
      this.loadWarnings = []
      this.recipient = {
        recipientName: this.initialRecipient.recipientName || "",
        recipientPhone: this.initialRecipient.recipientPhone || "",
        shippingAddress: this.initialRecipient.shippingAddress || ""
      }
    },
    handleClosed() {
      this.requestId += 1
      this.loading = false
    },
    parseAndMatch() {
      const result = parseTransferPaste(this.pasteText)
      if (!result.items.length) {
        this.$modal.msgError("没有识别到物料行，请按“物料名称20瓶”的格式每行填写一项")
        return
      }
      this.parsed = false
      this.rows = []
      this.ignoredLines = result.ignoredLines || []
      this.loadWarnings = []
      this.recipient = {
        recipientName: result.recipient.recipientName || this.initialRecipient.recipientName || "",
        recipientPhone: result.recipient.recipientPhone || this.initialRecipient.recipientPhone || "",
        shippingAddress: result.recipient.shippingAddress || this.initialRecipient.shippingAddress || ""
      }
      const currentRequestId = this.requestId + 1
      this.requestId = currentRequestId
      this.loading = true
      this.loadCandidatePool().then(pool => {
        if (currentRequestId !== this.requestId || !this.visible) return
        const candidates = pool.candidates
        this.loadWarnings = pool.warnings
        this.rows = matchTransferPasteItems(result.items, candidates)
        this.parsed = true
        if (!candidates.length) this.$modal.msgWarning("所选来源组织没有可匹配的可用库存")
      }).catch(error => {
        if (currentRequestId !== this.requestId) return
        this.$modal.msgError(error && error.message ? error.message : "加载物料候选失败，请重试")
      }).finally(() => {
        if (currentRequestId === this.requestId) this.loading = false
      })
    },
    loadCandidatePool() {
      const warnings = []
      const stockPromises = this.allowedItemTypes.map(itemType => this.loadCandidateGroup(
        itemType === "gift" ? "礼盒库存" : "商品库存",
        this.fetchAllPages(listStock, {
          itemType,
          shopDeptId: this.inventoryDeptId || this.warehouseId,
          warehouseId: this.warehouseId,
          stockStatus: "available",
          transferSource: true
        }, 1, [], warnings)
      ))
      return Promise.all(stockPromises).then(results => {
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
        const next = collected.concat(pageRows)
        const total = Number(response && response.total)
        const hasMore = Number.isFinite(total) ? next.length < total : pageRows.length === pageSize
        if (!hasMore || pageRows.length === 0) return next
        if (pageNum >= 50) {
          warnings.push("候选物料超过10000条，仅匹配前10000条")
          return next
        }
        return this.fetchAllPages(api, baseQuery, pageNum + 1, next, warnings)
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
      const itemId = row.itemId || row.productId
      const name = row.itemName || row.productName || ""
      const code = row.itemCode || row.productCode || ""
      return {
        itemType,
        itemId,
        itemName: name,
        itemCode: code,
        productId: itemType === "product" ? itemId : null,
        productName: name,
        productCode: code,
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
    stateLabel(state) {
      return { exact: "唯一同名", duplicate: "同名多项", similar: "相似候选", unmatched: "未找到" }[state] || "待确认"
    },
    stateType(state) { return state === "exact" ? "success" : state === "unmatched" ? "danger" : "warning" },
    candidateLabel(candidate) {
      const type = candidate.itemType === "gift" ? "礼盒" : "商品"
      const identity = [candidate.itemName || candidate.productName, candidate.itemCode || candidate.productCode].filter(Boolean).join(" · ")
      const meta = [candidate.spec, candidate.grade, candidate.unit].filter(Boolean).join(" / ")
      const stock = "可用 " + this.formatQuantity(candidate.availableQuantity)
      const referenceCost = formatReferenceCostPrice(candidate.referenceCostPrice)
      return ["[" + type + "] " + identity, meta, referenceCost, stock].filter(Boolean).join(" · ")
    },
    selected(row) { return selectedCandidate(row) },
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
    applySelection() {
      if (this.invalidQuantityCount) return this.$modal.msgError("数量必须大于 0，请修改后再加入明细")
      if (this.pendingCount) return this.$modal.msgError("请先判断所有同名或相似物料")
      const items = this.rows.filter(row => !!row.selectedKey).map(row => ({
        candidate: this.selected(row),
        quantity: row.quantity
      })).filter(row => !!row.candidate)
      if (!items.length) return this.$modal.msgError("没有已确认的物料可以加入")
      this.$emit("apply", { items, recipient: Object.assign({}, this.recipient), unmatchedCount: this.unmatchedCount })
      this.dialogVisible = false
    }
  }
}
</script>

<style lang="scss" scoped>
::v-deep .smart-paste-dialog {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 32px);
  margin-top: 16px !important;
}

::v-deep .smart-paste-dialog .el-dialog__header,
::v-deep .smart-paste-dialog .el-dialog__footer { flex: 0 0 auto; }

::v-deep .smart-paste-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}

.smart-paste-intro { margin-bottom: 12px; color: #606266; font-size: 13px; line-height: 1.6; }
.smart-paste-parse-actions { display: flex; align-items: center; gap: 12px; margin-top: 12px; color: #909399; font-size: 12px; }
.smart-paste-alert, .smart-paste-recipient, .smart-paste-summary, .smart-paste-table { margin-top: 12px; }
.smart-paste-recipient .el-col { margin-bottom: 8px; }
.smart-paste-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: #606266; font-size: 12px; }
.smart-paste-request-name { color: #303133; font-weight: 600; }
.smart-paste-request-meta { margin-top: 3px; color: #909399; font-size: 12px; }
.smart-paste-quantity-editor { display: flex; align-items: center; gap: 8px; }
.smart-paste-quantity-editor .el-input-number { width: 142px; }
.smart-paste-quantity-editor > span { color: #606266; font-size: 12px; white-space: nowrap; }
.smart-paste-reference-subtotal {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #315443;
  background: #f0f9eb;
  font-size: 12px;
  line-height: 1.4;
}
.smart-paste-reference-subtotal span { flex: 0 0 auto; font-weight: 600; }
.smart-paste-reference-subtotal strong { color: #1f6b4f; text-align: right; }
.smart-paste-quantity-error, .smart-paste-footer-error { color: #f56c6c; font-size: 12px; }
.smart-paste-quantity-error { margin-top: 5px; }
.smart-paste-unmatched, .smart-paste-footer-warning { color: #e6a23c; font-size: 12px; }
.smart-paste-unit-warning { margin-top: 5px; color: #e6a23c; font-size: 12px; line-height: 1.4; }
.smart-paste-footer-warning, .smart-paste-footer-error { margin-right: 12px; }
.smart-paste-mobile-list { display: none; }

@media (max-width: 768px) {
  ::v-deep .smart-paste-dialog {
    width: calc(100vw - 16px) !important;
    max-width: none;
    max-height: calc(100vh - 16px);
    margin: 8px auto 0 !important;
  }
  ::v-deep .smart-paste-dialog .el-dialog__body { padding: 14px; }
  ::v-deep .smart-paste-dialog .el-dialog__footer { padding: 10px 14px 14px; }
  .smart-paste-parse-actions { align-items: flex-start; flex-direction: column; gap: 6px; }
  .smart-paste-footer-warning { display: block; margin: 0 0 8px; }
  .smart-paste-desktop-table { display: none; }
  .smart-paste-mobile-list { display: block; margin-top: 12px; }
  .smart-paste-mobile-card {
    padding: 12px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    background: #fff;
  }
  .smart-paste-mobile-card + .smart-paste-mobile-card { margin-top: 10px; }
  .smart-paste-mobile-card-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
  }
  .smart-paste-mobile-quantity {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    color: #606266;
    font-size: 12px;
  }
  .smart-paste-mobile-select { width: 100%; }
  .smart-paste-reference-subtotal { align-items: flex-start; flex-direction: column; gap: 3px; }
  .smart-paste-reference-subtotal strong { text-align: left; }
}
</style>

<style lang="scss">
@media (max-width: 768px) {
  .smart-paste-candidate-dropdown {
    max-width: calc(100vw - 16px);
  }

  .smart-paste-candidate-dropdown .el-select-dropdown__item {
    height: auto;
    min-height: 34px;
    padding: 8px 12px;
    line-height: 1.45;
    white-space: normal;
  }
}
</style>
