<template>
  <div
    ref="lineItemsRoot"
    class="mobile-line-items-editor"
    role="group"
    :aria-label="label"
    :aria-required="ariaBoolean(lineItemsRequired)"
    :aria-invalid="ariaBoolean(lineItemsInvalid)"
    :aria-describedby="lineItemsInvalid ? describedBy || null : null"
    data-validation-control
    tabindex="-1"
  >
    <section v-if="usesStockPicker && field.smartPaste" class="smart-paste-entry">
      <div>
        <strong>复制整张要货清单</strong>
        <span>粘贴后自动解析数量和收货信息，单位以商品档案为准，相似物料由你确认。</span>
      </div>
      <button ref="smartPasteOpener" type="button" :disabled="!canOpenStockPicker" @click="openSmartPaste">
        粘贴清单智能匹配
      </button>
      <small v-if="!canOpenStockPicker">{{ missingDependencyText }}</small>
      <p v-if="smartPasteMessage" class="smart-paste-result" role="status" aria-live="polite">{{ smartPasteMessage }}</p>
    </section>
    <div v-if="usesStockPicker && rows.length === 0" class="line-empty">
      <p>{{ field.emptyText || "请先选择库存商品" }}</p>
      <button ref="stockPickerOpener" class="add-line primary" type="button" :disabled="!canOpenStockPicker" @click="openStockPicker">
        {{ field.addLabel || "从仓库库存选择" }}
      </button>
      <small v-if="!canOpenStockPicker">{{ missingDependencyText }}</small>
    </div>
    <article v-for="(row, index) in rows" :key="index" class="line-item" :data-row-index="index">
      <header>
        <strong>{{ label }} {{ index + 1 }}</strong>
        <button type="button" @click="removeRow(index)">删除</button>
      </header>
      <div
        v-for="itemField in visibleItemFields"
        :key="itemField.key"
        class="line-item-field"
        :data-row-index="index"
        :data-item-field-key="itemField.key"
        :role="itemField.type === 'entity-picker' ? 'group' : null"
        :aria-labelledby="itemField.type === 'entity-picker' ? itemLabelId(index, itemField) : null"
        :aria-invalid="ariaBoolean(isItemInvalid(index, itemField))"
        :aria-describedby="itemDescribedBy(index, itemField)"
        :tabindex="isItemInvalid(index, itemField) ? -1 : null"
      >
        <label
          v-if="itemField.type !== 'entity-picker'"
          :id="itemLabelId(index, itemField)"
          class="line-item-label"
          :for="itemControlId(index, itemField)"
        >
          {{ itemField.label }}<em v-if="itemFieldIsRequired(itemField, row)" aria-hidden="true"> *</em>
        </label>
        <span v-else :id="itemLabelId(index, itemField)" class="line-item-label">
          {{ itemField.label }}<em v-if="itemFieldIsRequired(itemField, row)" aria-hidden="true"> *</em>
        </span>
        <mobile-entity-picker
          v-if="itemField.type === 'entity-picker' && canUseEntityPicker(row, itemField)"
          :key="itemField.key + ':' + resolveItemPickerEntity(row, itemField)"
          v-model="row[itemField.key]"
          :input-id="itemControlId(index, itemField)"
          :entity="resolveItemPickerEntity(row, itemField)"
          :label="itemField.label"
          :context="context"
          :field="itemField"
          :form-data="formData"
          :required="itemFieldIsRequired(itemField, row)"
          :invalid="isItemInvalid(index, itemField)"
          :described-by="itemDescribedBy(index, itemField)"
          @field-edit="emitRows"
          @selection-cleared="handleItemSelectionCleared(index)"
          @select="option => handleSelect(index, itemField, option)"
        />
        <select
          v-else-if="itemField.type === 'select'"
          :id="itemControlId(index, itemField)"
          v-model="row[itemField.key]"
          :required="itemFieldIsRequired(itemField, row)"
          :aria-required="ariaBoolean(itemFieldIsRequired(itemField, row))"
          :aria-invalid="ariaBoolean(isItemInvalid(index, itemField))"
          :aria-describedby="itemDescribedBy(index, itemField)"
          @change="handleItemTypeChange(index, itemField)"
        >
          <option disabled value="">请选择物料类型</option>
          <option v-for="option in itemField.options || []" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <input
          v-else-if="itemField.type === 'entity-picker'"
          :id="itemControlId(index, itemField)"
          type="text"
          disabled
          :aria-required="ariaBoolean(itemFieldIsRequired(itemField, row))"
          :aria-invalid="ariaBoolean(isItemInvalid(index, itemField))"
          :aria-describedby="itemDescribedBy(index, itemField)"
          placeholder="请先选择物料类型"
        >
        <input
          v-else-if="itemField.type === 'readonly'"
          :id="itemControlId(index, itemField)"
          :value="formatReadonlyValue(row, itemField)"
          type="text"
          disabled
          :aria-required="ariaBoolean(itemFieldIsRequired(itemField, row))"
          :aria-invalid="ariaBoolean(isItemInvalid(index, itemField))"
          :aria-describedby="itemDescribedBy(index, itemField)"
          :placeholder="itemField.placeholder || itemField.label"
        >
        <input
          v-else
          :id="itemControlId(index, itemField)"
          v-model.trim="row[itemField.key]"
          :type="itemField.type === 'number' ? 'number' : 'text'"
          :step="itemField.type === 'number' ? '0.01' : null"
          :required="itemFieldIsRequired(itemField, row)"
          :aria-required="ariaBoolean(itemFieldIsRequired(itemField, row))"
          :aria-invalid="ariaBoolean(isItemInvalid(index, itemField))"
          :aria-describedby="itemDescribedBy(index, itemField)"
          :placeholder="itemField.label"
          @input="emitRows"
        >
      </div>
      <p v-if="lineAmount(row)">小计 {{ lineAmount(row) }}</p>
    </article>
    <p v-if="referenceTotalAmount" class="line-total" role="status" aria-live="polite">
      <span>参考总价</span>
      <strong>{{ formatMoney(referenceTotalAmount) }}</strong>
    </p>
    <button
      v-if="usesStockPicker && rows.length > 0"
      ref="stockPickerOpener"
      class="add-line primary"
      type="button"
      :disabled="!canOpenStockPicker"
      @click="openStockPicker"
    >
      {{ field.appendLabel || field.addLabel || "继续选择库存商品" }}
    </button>
    <button v-else-if="!usesStockPicker && field.allowManualAdd !== false" class="add-line" type="button" @click="addRow">新增明细</button>

    <section
      v-if="usesStockPicker && pickerOpen"
      ref="stockPickerMask"
      class="stock-picker-mask"
      @click.self="closeStockPicker"
    >
      <article ref="stockPickerDialog" class="stock-picker-sheet" role="dialog" aria-modal="true" aria-label="选择要货库存" tabindex="-1">
        <header class="stock-picker-head">
          <div>
            <strong>{{ field.pickerTitle || "选择要货库存" }}</strong>
            <span>{{ pickerSubtitle }}</span>
          </div>
          <button type="button" aria-label="关闭库存选择" @click="closeStockPicker"><i class="el-icon-close" aria-hidden="true" /></button>
        </header>
        <div v-if="requiresPickerItemType" class="stock-picker-item-types" role="group" aria-label="物料类型">
          <button
            v-for="option in pickerItemTypeOptions"
            :key="option.value"
            type="button"
            :class="{ active: pickerItemType === option.value }"
            @click="handlePickerItemTypeChange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
        <label class="stock-picker-category">
          <span>分类</span>
          <select v-model="pickerCategoryId" :disabled="pickerCategoryLoading || !canLoadPickerOptions" @change="handlePickerCategoryChange">
            <option value="">全部{{ pickerItemTypeLabel }}分类</option>
            <option v-if="pickerCategoryEmptyText" value="__empty__" disabled>{{ pickerCategoryEmptyText }}</option>
            <option v-for="option in pickerCategoryOptions" :key="String(option.value)" :value="option.value">
              {{ pickerCategoryLabel(option) }}
            </option>
          </select>
        </label>
        <div class="stock-picker-search">
          <input ref="stockPickerSearchInput" v-model.trim="pickerKeyword" type="search" aria-label="搜索物料名称、编码或条码" :maxlength="maxKeywordLength" :disabled="!canLoadPickerOptions" placeholder="搜索物料名称/编码/条码" @keydown.enter="handleSearchEnter">
          <button type="button" :disabled="!canLoadPickerOptions" @click="loadPickerOptions">{{ pickerLoading ? "搜索中" : "搜索" }}</button>
        </div>
        <div class="stock-picker-list">
          <p v-if="pickerLoading" class="stock-picker-status" role="status" aria-live="polite">正在加载库存商品</p>
          <div v-else-if="pickerError" class="stock-picker-error" role="alert">
            <p>{{ pickerError }}</p>
            <button type="button" @click="loadPickerOptions">重新加载来源库存</button>
          </div>
          <article
            v-for="option in pickerOptions"
            :key="stockOptionKey(option)"
            class="stock-option"
            :class="{ selected: isPickerSelected(option) }"
          >
            <button
              type="button"
              class="check-mark stock-option-select"
              :aria-label="stockOptionSelectLabel(option)"
              @click="togglePickerOption(option)"
            >
              {{ isPickerSelected(option) ? "已选" : "选择" }}
            </button>
            <span class="stock-option-main">
              <strong>{{ option.label }}</strong>
              <small>{{ option.meta || "可选择" }}</small>
              <em v-if="showsReferenceCost">参考成本价 {{ formatMoney(referenceCost(option.row)) }}</em>
            </span>
            <span class="stock-option-side">
              <b>可用 {{ option.row && option.row.availableQuantity !== undefined ? option.row.availableQuantity : "-" }}</b>
              <input
                v-if="isPickerSelected(option) && requiresPickerQuantity"
                :value="pickerQuantity(option)"
                type="number"
                min="1"
                step="0.01"
                :aria-label="stockOptionQuantityLabel(option)"
                @input="updatePickerQuantity(option, $event.target.value)"
              >
            </span>
          </article>
          <p v-if="!pickerLoading && !pickerError && pickerOptions.length === 0" class="stock-picker-empty">{{ pickerEmptyText }}</p>
        </div>
        <footer class="stock-picker-footer">
          <span class="stock-picker-summary" role="status" aria-live="polite">
            <b>已选 {{ selectedPickerCount }} 个物料</b>
            <em v-if="selectedPickerTotalAmount">参考总价 {{ formatMoney(selectedPickerTotalAmount) }}</em>
          </span>
          <button type="button" @click="closeStockPicker">取消</button>
          <button type="button" class="primary" :disabled="selectedPickerCount === 0 || pickerLoading || !!pickerError" @click="confirmStockSelection">{{ field.confirmLabel || "加入明细" }}</button>
        </footer>
      </article>
    </section>

    <mobile-transfer-smart-paste-sheet
      v-if="usesStockPicker && field.smartPaste && smartPasteOpen"
      :open.sync="smartPasteOpen"
      :warehouse-id="formData.fromWarehouseId || formData.fromDeptId"
      :inventory-dept-id="formData.fromDeptId || formData.fromWarehouseId"
      :transfer-type="formData.transferType || 'warehouse'"
      :initial-recipient="{
        recipientName: formData.recipientName,
        recipientPhone: formData.recipientPhone,
        shippingAddress: formData.shippingAddress
      }"
      :allowed-item-types="field.allowedItemTypes || ['product', 'gift']"
      @apply="applySmartPaste"
    />
  </div>
</template>

<script>
import MobileEntityPicker from "./MobileEntityPicker.vue"
import { fetchMobileEntityOptions } from "../mobileEntityService"
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"

const { isFieldRequired } = require("../mobileValidation")
const { resolveMobileLineItemDefaultPrice } = require("../mobileLineItemPricing")
const { focusElementAndVerify } = require("./mobileFocus")
const { runMobileSearchEnter } = require("./mobileSearchKeyboard")
const { mergeMobileTransferSmartPasteDetails } = require("../mobileTransferSmartPaste")
const { mobileErrorMessage } = require("../../mobileErrorMessage")

const MobileTransferSmartPasteSheet = () => import(
  /* webpackChunkName: "chunk-mobile-transfer-smart-paste" */
  "./MobileTransferSmartPasteSheet.vue"
)

const MAX_MOBILE_OPTION_KEYWORD_LENGTH = 80

export default {
  name: "MobileLineItemsEditor",
  components: { MobileEntityPicker, MobileTransferSmartPasteSheet },
  props: {
    value: {
      type: Array,
      default: () => []
    },
    itemFields: {
      type: Array,
      default: () => []
    },
    label: {
      type: String,
      default: "明细"
    },
    context: {
      type: Object,
      default: () => ({})
    },
    formData: {
      type: Object,
      default: () => ({})
    },
    field: {
      type: Object,
      default: () => ({})
    },
    validationError: {
      type: Object,
      default: null
    },
    describedBy: {
      type: String,
      default: ""
    }
  },
  data() {
    return {
      rows: this.normalizeRows(this.value),
      pickerOpen: false,
      pickerItemType: "",
      pickerKeyword: "",
      pickerCategoryId: "",
      pickerCategoryOptions: [],
      pickerCategoryLoading: false,
      pickerCategoryItemType: "",
      pickerCategoryRequestId: 0,
      maxKeywordLength: MAX_MOBILE_OPTION_KEYWORD_LENGTH,
      pickerLoading: false,
      pickerError: "",
      pickerRequestId: 0,
      pickerOptions: [],
      pickerSelected: {},
      smartPasteOpen: false,
      smartPasteMessage: "",
      dialogFocusManager: null
    }
  },
  computed: {
    usesStockPicker() {
      return this.field && this.field.selectionMode === "stock-picker"
    },
    lineItemsRequired() {
      return isFieldRequired(this.field, this.formData)
    },
    lineItemsInvalid() {
      return !!(this.validationError && String(this.validationError.fieldKey) === String(this.field.key))
    },
    visibleItemFields() {
      return (this.itemFields || []).filter(this.canShowField)
    },
    showsReferenceCost() {
      return this.visibleItemFields.some(field => field && field.key === "costPrice")
    },
    requiresPickerQuantity() {
      const itemFields = this.visibleItemFields
      return itemFields.some(field => field && field.key === "quantity")
    },
    pickerField() {
      return this.field && this.field.pickerField ? this.field.pickerField : {}
    },
    pickerItemTypeOptions() {
      const labels = { product: "商品", oe: "OE 器皿", gift: "礼盒" }
      const types = this.field && Array.isArray(this.field.allowedItemTypes) ? this.field.allowedItemTypes : []
      return types.map(type => ({ value: type, label: labels[type] || "其他物料" }))
    },
    requiresPickerItemType() {
      return !!(this.field && this.field.requirePickerItemType && this.pickerItemTypeOptions.length > 1)
    },
    pickerItemTypeLabel() {
      const selected = this.pickerItemTypeOptions.find(option => option.value === this.pickerItemType)
      return selected ? selected.label : "物料"
    },
    pickerCategoryEmptyText() {
      if (!this.canLoadPickerOptions || this.pickerCategoryLoading || this.pickerCategoryOptions.length) return ""
      if (this.pickerCategoryItemType !== this.pickerItemType) return ""
      return "暂无" + this.pickerItemTypeLabel + "分类"
    },
    canLoadPickerOptions() {
      return this.canOpenStockPicker && (!this.requiresPickerItemType || !!this.pickerItemType)
    },
    pickerDependencyKey() {
      return this.pickerField.dependsOn || "fromDeptId"
    },
    canOpenStockPicker() {
      return !this.pickerDependencyKey || !!this.formData[this.pickerDependencyKey]
    },
    missingDependencyText() {
      return "请先选择" + (this.pickerField.dependsOnLabel || "补货仓库")
    },
    pickerSubtitle() {
      return this.canOpenStockPicker ? (this.field.pickerSubtitle || "只显示当前仓库有可用库存的商品") : this.missingDependencyText
    },
    pickerEmptyText() {
      if (!this.canOpenStockPicker) return this.missingDependencyText
      if (!this.canLoadPickerOptions) return "请先选择物料类型"
      if (this.field && this.field.emptyResultText) return this.field.emptyResultText
      return "当前仓库暂无可用" + this.pickerItemTypeLabel + "库存，请切换仓库或先完成入库"
    },
    selectedPickerCount() {
      return Object.keys(this.pickerSelected || {}).length
    },
    referenceTotalAmount() {
      return this.calculateReferenceTotal(this.rows.map(row => ({ row, quantity: row.quantity })))
    },
    selectedPickerTotalAmount() {
      const entries = Object.keys(this.pickerSelected || {}).map(key => {
        const selected = this.pickerSelected[key] || {}
        const option = selected.option || {}
        return { row: option.row || {}, quantity: selected.quantity }
      })
      return this.calculateReferenceTotal(entries)
    }
  },
  watch: {
    value: {
      deep: true,
      handler(value) {
        this.rows = this.normalizeRows(value)
      }
    },
    pickerOpen(open) {
      if (open) {
        this.$nextTick(() => {
          if (!this.pickerOpen) return
          this.lockStockPickerBody()
          this.portalStockPickerToBody()
          this.activateStockPickerFocus()
        })
      } else {
        this.deactivateStockPickerFocus()
        this.releaseStockPickerBodyLock()
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.stockPickerDialog,
      getInitialFocus: () => this.$refs.stockPickerSearchInput || this.$refs.stockPickerDialog,
      onEscape: () => this.closeStockPicker()
    })
  },
  mounted() {
    if (this.pickerOpen) {
      this.$nextTick(() => {
        if (!this.pickerOpen) return
        this.lockStockPickerBody()
        this.portalStockPickerToBody()
        this.activateStockPickerFocus()
      })
    }
  },
  beforeDestroy() {
    this.deactivateStockPickerFocus()
    this.releaseStockPickerBodyLock()
    this.restoreStockPickerMount()
  },
  methods: {
    ariaBoolean(value) {
      return value ? "true" : "false"
    },
    itemFieldIsRequired(itemField, row) {
      return isFieldRequired(itemField, row)
    },
    itemIdPart(value) {
      return String(value || "item").replace(/[^a-zA-Z0-9_-]/g, "-")
    },
    itemControlId(index, itemField) {
      const prefix = this.$attrs.id || ("mobile-line-items-" + this._uid)
      return prefix + "-row-" + index + "-" + this.itemIdPart(itemField && itemField.key)
    },
    itemLabelId(index, itemField) {
      return this.itemControlId(index, itemField) + "-label"
    },
    isItemInvalid(index, itemField) {
      return !!(
        this.lineItemsInvalid &&
        this.validationError.rowIndex !== null &&
        this.validationError.rowIndex !== undefined &&
        Number(this.validationError.rowIndex) === index &&
        String(this.validationError.itemFieldKey) === String(itemField.key)
      )
    },
    itemDescribedBy(index, itemField) {
      return this.isItemInvalid(index, itemField) ? this.describedBy || null : null
    },
    handleSearchEnter(event) {
      return runMobileSearchEnter(event, () => this.loadPickerOptions())
    },
    normalizeRows(value) {
      return (Array.isArray(value) ? value : [])
        .map(row => this.normalizeDetailRow(row))
        .filter(row => this.isAllowedItemType(row.itemType))
    },
    normalizeDetailRow(row) {
      const source = row || {}
      const normalized = Object.assign({ remark: "" }, source)
      const itemId = this.firstValue(source, ["itemId", "productId"])
      const itemCode = this.firstValue(source, ["itemCode", "productCode", "skuCode", "sku"])
      const itemName = this.firstValue(source, ["itemName", "productName", "skuName"])
      if (itemId !== undefined || itemCode !== undefined || itemName !== undefined || source.itemType) {
        normalized.itemType = this.normalizeItemType(source.itemType)
        normalized.itemId = itemId === undefined ? "" : itemId
        normalized.itemCode = itemCode === undefined ? "" : itemCode
        normalized.itemName = itemName === undefined ? "" : itemName
      }
      return normalized
    },
    createEmptyRow() {
      return (this.visibleItemFields || []).reduce((row, field) => {
        row[field.key] = field.defaultValue !== undefined ? field.defaultValue : ""
        return row
      }, {})
    },
    addRow() {
      this.rows = this.rows.concat([this.createEmptyRow()])
      this.emitRows()
    },
    removeRow(index) {
      this.rows = this.rows.filter((_, rowIndex) => rowIndex !== index)
      this.emitRows()
    },
    handleItemSelectionCleared(index) {
      const rows = this.rows.slice()
      const row = Object.assign({}, rows[index], {
        itemId: "",
        itemCode: "",
        itemName: "",
        productId: "",
        productCode: "",
        productName: "",
        unit: "",
        spec: "",
        grade: "",
        availableQuantity: "",
        availabilityStatus: "",
        price: ""
      })
      rows.splice(index, 1, row)
      this.rows = rows
      this.emitRows()
    },
    handleSelect(index, field, option) {
      const rows = this.rows.slice()
      const optionRow = (option && option.row) || {}
      const itemType = this.normalizeItemType(optionRow.itemType || this.resolveItemPickerEntity(rows[index], field))
      const itemId = this.firstValue(optionRow, ["itemId", "productId", "oeItemId", "giftId"])
      const itemCode = this.firstValue(optionRow, ["itemCode", "productCode", "oeItemCode", "giftCode", "skuCode", "sku"])
      const itemName = this.firstValue(optionRow, ["itemName", "productName", "oeItemName", "giftName", "skuName"])
      const row = Object.assign({}, rows[index], {
        [field.key]: itemId === undefined ? option.value : itemId,
        itemType,
        itemId: itemId === undefined ? option.value : itemId,
        itemCode: itemCode || "",
        itemName: itemName || option.label || ""
      })
      if (option.row) {
        if (optionRow.unit || optionRow.itemUnit) row.unit = optionRow.itemUnit || optionRow.unit
        if (optionRow.spec || optionRow.itemSpec) row.spec = optionRow.itemSpec || optionRow.spec
        if (optionRow.grade || optionRow.itemGrade) row.grade = optionRow.itemGrade || optionRow.grade
        if (optionRow.availableQuantity !== undefined && optionRow.availableQuantity !== null) {
          row.availableQuantity = optionRow.availableQuantity
        }
        if (optionRow.availabilityStatus) row.availabilityStatus = optionRow.availabilityStatus
        const defaultPrice = this.resolveOptionPrice(optionRow, itemType)
        if (defaultPrice !== undefined && defaultPrice !== null && defaultPrice !== "" && !row.price) row.price = defaultPrice
      }
      rows.splice(index, 1, row)
      this.rows = rows
      this.emitRows()
    },
    handleItemTypeChange(index, field) {
      const rows = this.rows.slice()
      const row = Object.assign({}, rows[index], {
        itemType: this.normalizeItemType(rows[index] && rows[index][field.key]),
        itemId: "",
        itemCode: "",
        itemName: "",
        productId: "",
        productCode: "",
        productName: "",
        unit: "",
        spec: "",
        grade: "",
        availableQuantity: "",
        availabilityStatus: "",
        price: ""
      })
      rows.splice(index, 1, row)
      this.rows = rows
      this.emitRows()
    },
    resolveItemPickerEntity(row, field) {
      if (field && field.entityBy) {
        const itemType = this.normalizeItemType(row && row[field.entityBy])
        return this.isAllowedItemType(itemType) ? itemType : "product"
      }
      return (field && field.entity) || "product"
    },
    canUseEntityPicker(row, field) {
      if (!field || !field.entityBy) return true
      const itemType = row && row[field.entityBy]
      return itemType !== undefined && itemType !== null && String(itemType).trim() !== "" && this.isAllowedItemType(itemType)
    },
    resolveOptionPrice(row, itemType) {
      return resolveMobileLineItemDefaultPrice(row, itemType, this.context && this.context.featureKey)
    },
    formatReadonlyValue(row, field) {
      const value = row[field.key]
      if (field.format === "money") return this.formatMoney(value)
      return value === undefined || value === null || value === "" ? "" : value
    },
    lineAmount(row) {
      const quantity = Number(row.quantity)
      const price = Number(row.price || row.estimatedPrice || (this.showsReferenceCost ? this.referenceCost(row) : undefined))
      if (!Number.isFinite(quantity) || !Number.isFinite(price)) return ""
      return (quantity * price).toFixed(2)
    },
    canShowField(field) {
      if (!field) return false
      if (field.visibleWhen && field.visibleWhen.key &&
        String(this.formData[field.visibleWhen.key]) !== String(field.visibleWhen.value)) {
        return false
      }
      if (!Array.isArray(field.permissions) || field.permissions.length === 0) return true
      return this.hasAnyPermission(field.permissions)
    },
    hasAnyPermission(permissions) {
      const userPermissions = this.context && Array.isArray(this.context.permissions) ? this.context.permissions : []
      if (userPermissions.indexOf("*:*:*") > -1) return true
      return permissions.some(permission => userPermissions.indexOf(permission) > -1)
    },
    emitRows() {
      this.$emit("input", this.rows.map(row => Object.assign({}, row)))
    },
    lockStockPickerBody() {
      mountMobileOverlay("mobile-stock-picker-open")
    },
    releaseStockPickerBodyLock() {
      releaseMobileOverlay("mobile-stock-picker-open")
    },
    portalStockPickerToBody() {
      const mask = this.$refs.stockPickerMask
      if (typeof document === "undefined" || !document.body || !mask || mask.parentNode === document.body) return false
      this.stockPickerMountParent = mask.parentNode
      this.stockPickerMountNextSibling = mask.nextSibling
      document.body.appendChild(this.$refs.stockPickerMask)
      return true
    },
    restoreStockPickerMount() {
      const mask = this.$refs.stockPickerMask
      const parent = this.stockPickerMountParent
      if (!mask || !parent || mask.parentNode === parent) return false
      const nextSibling = this.stockPickerMountNextSibling
      if (nextSibling && nextSibling.parentNode === parent) {
        parent.insertBefore(mask, nextSibling)
      } else {
        parent.appendChild(mask)
      }
      this.stockPickerMountParent = null
      this.stockPickerMountNextSibling = null
      return true
    },
    activateStockPickerFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateStockPickerFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    },
    openStockPicker() {
      if (!this.canOpenStockPicker) return
      this.pickerOpen = true
      this.pickerItemType = this.requiresPickerItemType ? "" : ((this.pickerItemTypeOptions[0] && this.pickerItemTypeOptions[0].value) || "product")
      this.pickerCategoryId = ""
      this.pickerError = ""
      this.pickerOptions = []
      this.pickerCategoryOptions = []
      this.pickerCategoryItemType = ""
      this.pickerSelected = this.rows.reduce((selected, row) => {
        const option = this.optionFromDetail(row)
        if (row.itemId && this.isAllowedItemType(row.itemType)) {
          selected[this.stockOptionKey(option)] = {
            option,
            quantity: row.quantity || 1
          }
        }
        return selected
      }, {})
      if (this.canLoadPickerOptions) {
        this.loadPickerCategories()
        this.loadPickerOptions()
      }
    },
    openSmartPaste() {
      if (!this.canOpenStockPicker) return
      this.smartPasteMessage = ""
      this.smartPasteOpen = true
    },
    applySmartPaste(payload) {
      const result = mergeMobileTransferSmartPasteDetails(this.rows, payload && payload.items)
      this.rows = result.details
      this.emitRows()
      this.$emit("smart-paste-recipient", (payload && payload.recipient) || {})
      const parts = ["已加入 " + result.addedCount + " 项"]
      if (result.mergedCount) parts.push("合并 " + result.mergedCount + " 项已有明细")
      const unmatchedCount = Number(payload && payload.unmatchedCount || 0)
      if (unmatchedCount) parts.push(unmatchedCount + " 行未匹配、未加入")
      this.smartPasteMessage = parts.join("；")
      this.$nextTick(() => focusElementAndVerify(this.$refs.smartPasteOpener) || focusElementAndVerify(this.$refs.lineItemsRoot))
    },
    closeStockPicker() {
      this.pickerRequestId += 1
      this.pickerLoading = false
      this.restoreStockPickerMount()
      this.pickerOpen = false
    },
    loadPickerOptions() {
      const requestId = this.pickerRequestId + 1
      this.pickerRequestId = requestId
      if (!this.canLoadPickerOptions) {
        this.pickerLoading = false
        this.pickerError = ""
        this.pickerOptions = []
        return Promise.resolve([])
      }
      this.pickerLoading = true
      this.pickerError = ""
      this.pickerOptions = []
      return fetchMobileEntityOptions(this.field.pickerEntity || "replenishmentStock", {
        keyword: this.pickerKeyword,
        itemType: this.pickerItemType,
        categoryId: this.pickerCategoryId,
        context: this.context,
        field: this.pickerField,
        formData: this.formData
      }).then(options => {
        if (requestId !== this.pickerRequestId || !this.pickerOpen) return []
        this.pickerOptions = Array.isArray(options) ? options : []
        this.pickerError = ""
        return this.pickerOptions
      }).catch(error => {
        if (requestId !== this.pickerRequestId || !this.pickerOpen) return []
        this.pickerOptions = []
        this.pickerError = mobileErrorMessage(error, "来源库存加载失败，请检查网络后重试")
        return []
      }).finally(() => {
        if (requestId === this.pickerRequestId) this.pickerLoading = false
      })
    },
    handlePickerItemTypeChange(itemType) {
      this.pickerItemType = itemType
      this.pickerCategoryId = ""
      this.pickerError = ""
      this.pickerCategoryOptions = []
      this.pickerCategoryItemType = ""
      this.pickerOptions = []
      this.loadPickerCategories()
      this.loadPickerOptions()
    },
    loadPickerCategories() {
      const itemType = this.pickerItemType
      if (!itemType) return Promise.resolve([])
      const requestId = this.pickerCategoryRequestId + 1
      this.pickerCategoryRequestId = requestId
      this.pickerCategoryLoading = true
      this.pickerCategoryOptions = []
      this.pickerCategoryItemType = itemType
      return fetchMobileEntityOptions("category", {
        itemType: this.pickerItemType,
        context: this.context,
        field: this.pickerField,
        formData: this.formData,
        limit: 100
      }).then(options => {
        if (requestId === this.pickerCategoryRequestId) {
          this.pickerCategoryOptions = Array.isArray(options) ? options : []
        }
        return this.pickerCategoryOptions
      }).catch(() => {
        if (requestId === this.pickerCategoryRequestId) this.pickerCategoryOptions = []
        return []
      }).finally(() => {
        if (requestId === this.pickerCategoryRequestId) this.pickerCategoryLoading = false
      })
    },
    handlePickerCategoryChange() {
      this.loadPickerOptions()
    },
    pickerCategoryLabel(option) {
      return (option && (option.meta || option.label)) || "分类"
    },
    togglePickerOption(option) {
      const key = this.stockOptionKey(option)
      const selected = Object.assign({}, this.pickerSelected)
      if (selected[key]) {
        delete selected[key]
      } else {
        const existing = this.rows.find(row => this.stockOptionKey(this.optionFromDetail(row)) === key)
        selected[key] = {
          option,
          quantity: existing && existing.quantity ? existing.quantity : 1
        }
      }
      this.pickerSelected = selected
    },
    stockOptionSelectLabel(option) {
      const action = this.isPickerSelected(option) ? "取消选择" : "选择"
      return action + String((option && option.label) || "商品")
    },
    stockOptionQuantityLabel(option) {
      return String((option && option.label) || "商品") + "要货数量"
    },
    isPickerSelected(option) {
      return !!this.pickerSelected[this.stockOptionKey(option)]
    },
    pickerQuantity(option) {
      const selected = this.pickerSelected[this.stockOptionKey(option)]
      return selected ? selected.quantity : 1
    },
    updatePickerQuantity(option, value) {
      const key = this.stockOptionKey(option)
      if (!this.pickerSelected[key]) return
      const selected = Object.assign({}, this.pickerSelected)
      selected[key] = Object.assign({}, selected[key], { quantity: value })
      this.pickerSelected = selected
    },
    confirmStockSelection() {
      const selectedRows = Object.keys(this.pickerSelected).map(key => {
        const selected = this.pickerSelected[key]
        return this.detailFromOption(selected.option, selected.quantity)
      }).filter(row => row.itemId && this.isAllowedItemType(row.itemType))
      const selectedIds = selectedRows.reduce((ids, row) => {
        ids[this.stockOptionKey(this.optionFromDetail(row))] = true
        return ids
      }, {})
      this.rows = this.rows.filter(row => !selectedIds[this.stockOptionKey(this.optionFromDetail(row))]).concat(selectedRows)
      this.emitRows()
      this.closeStockPicker()
      this.$nextTick(() => this.focusStockPickerAfterConfirm())
    },
    focusStockPickerAfterConfirm() {
      return focusElementAndVerify(this.$refs.stockPickerOpener) ||
        focusElementAndVerify(this.$refs.lineItemsRoot)
    },
    detailFromOption(option, quantity) {
      const row = (option && option.row) || {}
      const itemType = this.normalizeItemType(row.itemType)
      const itemId = this.firstValue(row, ["itemId", "productId"])
      const itemCode = this.firstValue(row, ["itemCode", "productCode", "skuCode", "sku"])
      const itemName = this.firstValue(row, ["itemName", "productName", "skuName"])
      const detail = {
        itemType,
        itemId: itemId === undefined ? option.value : itemId,
        itemName: itemName || option.label || "",
        itemCode: itemCode || "",
        quantity: this.requiresPickerQuantity ? (quantity || 1) : "",
        availableQuantity: row.availableQuantity,
        currentQuantity: row.currentQuantity,
        availabilityStatus: row.availabilityStatus || "loaded",
        unit: row.itemUnit || row.unit || "",
        spec: row.itemSpec || row.spec || "",
        grade: row.itemGrade || row.grade || "",
        remark: ""
      }
      if (this.field.payloadMode === "stock-check-products" && itemType === "product") {
        detail.productId = detail.itemId
        detail.productName = detail.itemName
        detail.productCode = detail.itemCode
      }
      const costPrice = this.referenceCost(row)
      if (this.showsReferenceCost && costPrice !== undefined) {
        detail.costPrice = costPrice
      }
      return detail
    },
    optionFromDetail(row) {
      const itemType = this.normalizeItemType(row.itemType)
      const itemId = this.firstValue(row, ["itemId", "productId"])
      const itemName = this.firstValue(row, ["itemName", "productName", "skuName"])
      const itemCode = this.firstValue(row, ["itemCode", "productCode", "skuCode", "sku"])
      return {
        value: itemId,
        label: itemName || itemCode || "物料",
        meta: [itemCode, row.itemSpec || row.spec, row.itemUnit || row.unit].filter(Boolean).join(" · "),
        row: Object.assign({}, row, { itemType, itemId, itemName, itemCode })
      }
    },
    stockOptionKey(option) {
      const row = (option && option.row) || option || {}
      const itemType = this.normalizeItemType(row.itemType)
      const itemId = this.firstValue(row, ["itemId", "productId"])
      const value = itemId === undefined ? option && option.value : itemId
      return itemType + ":" + String(value === undefined || value === null ? "" : value)
    },
    isAllowedItemType(itemType) {
      const allowedTypes = this.field && Array.isArray(this.field.allowedItemTypes)
        ? this.field.allowedItemTypes
        : []
      if (!allowedTypes.length) return true
      return allowedTypes.indexOf(this.normalizeItemType(itemType)) > -1
    },
    normalizeItemType(itemType) {
      const normalized = itemType === undefined || itemType === null || String(itemType).trim() === ""
        ? "product"
        : String(itemType).trim().toLowerCase()
      return ["product", "oe", "gift"].indexOf(normalized) > -1 ? normalized : "product"
    },
    firstValue(source, keys) {
      for (let index = 0; index < keys.length; index += 1) {
        const value = source && source[keys[index]]
        if (value !== undefined && value !== null && String(value).trim() !== "") return value
      }
      return undefined
    },
    referenceCost(row) {
      return this.firstValue(row, ["costPrice", "referenceCostPrice"])
    },
    calculateReferenceTotal(entries) {
      if (!this.showsReferenceCost) return ""
      let hasAmount = false
      const total = (entries || []).reduce((sum, entry) => {
        const quantity = Number(entry && entry.quantity)
        const costPrice = Number(this.referenceCost(entry && entry.row))
        if (!Number.isFinite(quantity) || !Number.isFinite(costPrice)) return sum
        hasAmount = true
        return sum + quantity * costPrice
      }, 0)
      return hasAmount ? total.toFixed(2) : ""
    },
    formatMoney(value) {
      if (value === undefined || value === null || value === "") return "-"
      const numberValue = Number(value)
      return Number.isFinite(numberValue) ? "¥" + numberValue.toFixed(2) : String(value)
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-line-items-editor {
  display: grid;
  gap: 12px;
}

.line-item {
  display: grid;
  gap: 13px;
  padding: 14px;
  border: 1px solid rgba(28, 50, 38, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.54);
}

.line-item header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.line-item header strong {
  min-width: 0;
  line-height: 1.3;
  word-break: break-word;
}

.line-item header button {
  flex: 0 0 auto;
  min-width: 68px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid rgba(28, 50, 38, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  color: #122019;
  font: inherit;
  font-weight: 800;
}

.line-item-field {
  display: grid;
  gap: 7px;
}

.line-item-label {
  line-height: 1.3;
  color: #60746a;
  font-size: 13px;
  font-weight: 900;
}

.line-item-label em {
  color: #b45309;
  font-style: normal;
}

.line-item input,
.line-item select {
  min-height: 46px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 8px;
  padding: 0 12px;
  box-sizing: border-box;
  font: inherit;
}

.line-item input:disabled {
  color: #315443;
  background: rgba(232, 250, 243, 0.76);
  opacity: 1;
}

.line-item p {
  margin: 0;
  color: #315443;
  font-weight: 700;
}

.line-total {
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(34, 122, 91, 0.24);
  border-radius: 8px;
  color: #315443;
  background: rgba(232, 250, 243, 0.76);
  font-weight: 900;
}

.line-total strong {
  color: #0b6b53;
  font-size: 18px;
}

.smart-paste-entry {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(34, 122, 91, 0.28);
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(232, 250, 243, 0.9), rgba(255, 255, 255, 0.74));
}

.smart-paste-entry div {
  display: grid;
  gap: 4px;
}

.smart-paste-entry strong {
  color: #123b2d;
  font-size: 15px;
}

.smart-paste-entry span,
.smart-paste-entry small,
.smart-paste-result {
  margin: 0;
  color: #557064;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 800;
}

.smart-paste-entry > button {
  min-height: 48px;
  border: 1px solid rgba(11, 107, 83, 0.34);
  border-radius: 10px;
  color: #0b6b53;
  background: #fff;
  font: inherit;
  font-weight: 900;
}

.smart-paste-entry > button:disabled {
  opacity: 0.58;
}

.smart-paste-result {
  padding: 9px 10px;
  border-radius: 9px;
  color: #065f46;
  background: #d1fae5;
}

.line-empty {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px dashed rgba(28, 50, 38, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.46);
}

.line-empty p,
.line-empty small {
  margin: 0;
  color: #60746a;
  line-height: 1.4;
  font-weight: 800;
}

.add-line {
  min-height: 48px;
  border-radius: 8px;
  border: 1px dashed rgba(28, 50, 38, 0.3);
  background: rgba(255, 255, 255, 0.66);
  color: #122019;
  font: inherit;
  font-weight: 900;
}

.add-line.primary {
  border-style: solid;
  border-color: transparent;
  color: #fff;
  background: var(--mobile-color-primary, #0b6b53);
}

.add-line:disabled {
  opacity: 0.58;
}

.stock-picker-mask {
  position: fixed;
  inset: 0;
  z-index: 10040;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  padding:
    max(12px, env(safe-area-inset-top))
    12px
    max(12px, env(safe-area-inset-bottom));
  background: rgba(15, 23, 42, 0.32);
  box-sizing: border-box;
}

.stock-picker-sheet {
  width: min(100%, 396px);
  height: calc(100vh - 24px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  height: calc(100dvh - 24px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  max-height: calc(100vh - 24px - env(safe-area-inset-bottom));
  max-height: calc(100dvh - 24px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 24px;
  background: #fbfdfb;
  box-shadow: 0 16px 34px rgba(31, 63, 48, 0.18);
  box-sizing: border-box;
  overflow: hidden;
}

.stock-picker-head,
.stock-picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stock-picker-head div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.stock-picker-head strong {
  color: #122019;
  font-size: 18px;
  line-height: 1.25;
}

.stock-picker-head span,
.stock-picker-footer span {
  color: #60746a;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 800;
}

.stock-picker-head button {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.72);
  color: #4e6258;
  font-size: 22px;
  line-height: 1;
}

.stock-picker-category {
  display: grid;
  gap: 6px;
}

.stock-picker-item-types {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 4px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 8px;
  background: rgba(232, 250, 243, 0.62);
}

.stock-picker-item-types button {
  min-width: 0;
  min-height: 44px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #315443;
  font: inherit;
  font-weight: 900;
}

.stock-picker-item-types button.active {
  background: #fff;
  color: #0f6f5d;
  box-shadow: 0 1px 4px rgba(28, 50, 38, 0.14);
}

.stock-picker-category span {
  color: #60746a;
  font-size: 12px;
  line-height: 1.35;
  font-weight: 900;
}

.stock-picker-category select {
  width: 100%;
  min-height: 44px;
  min-width: 0;
  padding: 0 12px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 12px;
  background: #fff;
  color: #122019;
  font: inherit;
  font-weight: 900;
  box-sizing: border-box;
}

.stock-picker-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px;
  gap: 10px;
}

.stock-picker-search input,
.stock-picker-search button,
.stock-picker-footer button {
  min-height: 44px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 12px;
  background: #fff;
  font: inherit;
  font-weight: 900;
  box-sizing: border-box;
}

.stock-picker-search input {
  min-width: 0;
  padding: 0 12px;
}

.stock-picker-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-y;
  display: grid;
  align-content: start;
  gap: 10px;
}

.stock-option {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 14px;
  background: #fff;
  text-align: left;
  font: inherit;
}

.stock-option.selected {
  border-color: rgba(34, 122, 91, 0.58);
  background: rgba(232, 250, 243, 0.84);
}

.check-mark {
  width: 56px;
  min-height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  border: 1px solid rgba(28, 50, 38, 0.22);
  background: #fff;
  color: #0f8f74;
  font-weight: 900;
  padding: 0;
  font: inherit;
}

.stock-option-main {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.stock-option-main strong,
.stock-option-main small,
.stock-option-main em {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.stock-option-main strong {
  color: #122019;
  font-weight: 900;
}

.stock-option-main small,
.stock-option-main em {
  color: #60746a;
  font-size: 12px;
  line-height: 1.35;
  font-style: normal;
  font-weight: 800;
}

.stock-option-side {
  grid-column: 2;
  min-width: 0;
  display: grid;
  gap: 8px;
}

.stock-option-side b {
  color: #0f6f5d;
  font-size: 13px;
}

.stock-option-side input {
  width: 100%;
  min-height: 44px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 10px;
  padding: 0 10px;
  font: inherit;
  box-sizing: border-box;
}

.stock-picker-empty {
  margin: 8px 0;
  color: #60746a;
  text-align: center;
  font-weight: 800;
}

.stock-picker-status {
  margin: 8px 0;
  color: #60746a;
  text-align: center;
  font-weight: 800;
}

.stock-picker-error {
  display: grid;
  justify-items: center;
  gap: 10px;
  margin: 8px 0;
  padding: 14px;
  border: 1px solid rgba(199, 62, 62, 0.2);
  border-radius: 12px;
  color: #a32626;
  background: rgba(255, 239, 239, 0.72);
  text-align: center;
  font-weight: 800;
}

.stock-picker-error p {
  margin: 0;
}

.stock-picker-error button {
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid rgba(163, 38, 38, 0.28);
  border-radius: 10px;
  color: #8f2020;
  background: #fff;
  font: inherit;
  font-weight: 900;
}

.stock-picker-footer {
  position: sticky;
  bottom: 0;
  z-index: 2;
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  padding-top: 2px;
  background: #fbfdfb;
}

.stock-picker-footer button {
  padding: 0 14px;
  color: #122019;
}

.stock-picker-summary {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.stock-picker-summary b,
.stock-picker-summary em {
  min-width: 0;
  font-style: normal;
  line-height: 1.3;
}

.stock-picker-summary em {
  color: #0b6b53;
}

.stock-picker-footer .primary {
  color: #fff;
  border-color: transparent;
  background: var(--mobile-color-primary, #0b6b53);
}

.stock-picker-footer button:disabled {
  opacity: 0.58;
}
</style>

<style lang="scss">
body.mobile-stock-picker-open {
  overflow: hidden;
}

body.mobile-stock-picker-open .bottom-nav {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}
</style>
