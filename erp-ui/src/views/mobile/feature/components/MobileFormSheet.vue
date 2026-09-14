<template>
  <section v-if="open" ref="formMask" class="detail-mask form-mask" @click.self="$emit('close')">
    <article ref="formDialog" class="glass-panel detail-sheet form-sheet mobile-system-sheet" role="dialog" aria-modal="true" aria-label="手机表单" tabindex="-1">
      <header class="detail-head mobile-system-sheet__header">
        <div>
          <span>{{ feature.heading }}</span>
          <h2 ref="formTitle" tabindex="-1">{{ title }}</h2>
        </div>
        <button type="button" aria-label="关闭" @click="$emit('close')">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
        </button>
      </header>
      <form class="mobile-form-layout" novalidate @submit.prevent="emitSubmit(defaultSubmitAction)" @focusin="handleFocusIn">
        <div ref="formBody" class="form-body mobile-system-sheet__body">
          <fieldset class="mobile-form-list" :disabled="saving" :inert="saving ? true : null" :aria-busy="saving ? 'true' : 'false'">
            <div
              v-for="field in fields"
              :key="field.key"
              class="mobile-form-field"
              :data-field-key="field.key"
              :role="isCustomField(field) ? 'group' : null"
              :aria-labelledby="isCustomField(field) ? fieldLabelId(field) : null"
              :aria-required="isCustomField(field) ? ariaBoolean(fieldIsRequired(field)) : null"
              :aria-invalid="isCustomField(field) ? ariaBoolean(isFieldInvalid(field)) : null"
              :aria-describedby="isCustomField(field) ? fieldDescribedBy(field) : null"
              :tabindex="isCustomField(field) ? -1 : null"
            >
              <label :id="fieldLabelId(field)" :for="fieldControlId(field)" v-if="!isCustomField(field)" class="mobile-form-label">
                {{ field.label }}<em v-if="fieldIsRequired(field)" aria-hidden="true"> *</em>
              </label>
              <span v-else :id="fieldLabelId(field)" class="mobile-form-label">
                {{ field.label }}<em v-if="fieldIsRequired(field)" aria-hidden="true"> *</em>
              </span>
              <select
                v-if="field.type === 'select'"
                :id="fieldControlId(field)"
                v-model="localData[field.key]"
                :required="fieldIsRequired(field)"
                :aria-required="ariaBoolean(fieldIsRequired(field))"
                :aria-invalid="ariaBoolean(isFieldInvalid(field))"
                :aria-describedby="fieldDescribedBy(field)"
                @change="emitInput"
              >
                <option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <textarea
                v-else-if="field.type === 'textarea'"
                :id="fieldControlId(field)"
                v-model.trim="localData[field.key]"
                :required="fieldIsRequired(field)"
                :aria-required="ariaBoolean(fieldIsRequired(field))"
                :aria-invalid="ariaBoolean(isFieldInvalid(field))"
                :aria-describedby="fieldDescribedBy(field)"
                :placeholder="field.placeholder || field.label"
                :maxlength="field.maxlength || null"
                :autocomplete="field.autocomplete || null"
                rows="3"
                @input="emitInput"
              />
              <input
                v-else-if="field.type === 'context-dept'"
                :id="fieldControlId(field)"
                :value="contextDeptLabel"
                type="text"
                disabled
                :aria-required="ariaBoolean(fieldIsRequired(field))"
                :aria-invalid="ariaBoolean(isFieldInvalid(field))"
                :aria-describedby="fieldDescribedBy(field)"
                :placeholder="field.placeholder || field.label"
              >
              <input
                v-else-if="field.type === 'readonly'"
                :id="fieldControlId(field)"
                :value="fieldDisplayValue(field)"
                type="text"
                disabled
                :aria-required="ariaBoolean(fieldIsRequired(field))"
                :aria-invalid="ariaBoolean(isFieldInvalid(field))"
                :aria-describedby="fieldDescribedBy(field)"
                :placeholder="field.placeholder || field.label"
              >
              <image-upload
                v-else-if="field.type === 'image-upload'"
                :id="fieldControlId(field)"
                v-model="localData[field.key]"
                :action="imageUploadAction(field)"
                :context-key="uploadContextKey"
                :data="imageUploadData(field)"
                :disabled="imageUploadDisabled(field)"
                :delete-on-remove="imageUploadDeleteOnRemove(field)"
                :limit="field.limit || 5"
                :file-size="field.fileSize || 5"
                :accept="field.accept || 'image/*'"
                :capture="field.capture || 'environment'"
                :compress="field.compress !== false"
                @upload-state="handleUploadState"
                @input="emitInput"
              />
              <mobile-line-items-editor
                v-else-if="field.type === 'line-items'"
                :id="fieldControlId(field)"
                v-model="localData[field.key]"
                :label="field.label"
                :item-fields="field.itemFields"
                :context="context"
                :form-data="localData"
                :field="field"
                :validation-error="validationError"
                :described-by="fieldDescribedBy(field)"
                @input="emitInput"
                @smart-paste-recipient="applySmartPasteRecipient"
              />
              <mobile-entity-picker
                v-else-if="field.type === 'entity-picker'"
                v-model="localData[field.key]"
                :input-id="fieldControlId(field)"
                :entity="field.entity"
                :label="field.label"
                :context="context"
                :field="field"
                :form-data="localData"
                :required="fieldIsRequired(field)"
                :invalid="isFieldInvalid(field)"
                :described-by="fieldDescribedBy(field)"
                @input="handleFieldInput(field)"
                @field-edit="emitInput"
                @selection-cleared="handleEntitySelectionCleared(field)"
                @select="option => handleEntitySelect(field, option)"
              />
              <input
                v-else
                :id="fieldControlId(field)"
                v-model.trim="localData[field.key]"
                :type="field.type === 'number' ? 'number' : field.type === 'date' ? 'date' : field.type === 'datetime-local' ? 'datetime-local' : field.type === 'tel' ? 'tel' : 'text'"
                :required="fieldIsRequired(field)"
                :aria-required="ariaBoolean(fieldIsRequired(field))"
                :aria-invalid="ariaBoolean(isFieldInvalid(field))"
                :aria-describedby="fieldDescribedBy(field)"
                :placeholder="field.placeholder || field.label"
                :step="field.type === 'number' ? '0.01' : null"
                :maxlength="field.maxlength || null"
                :autocomplete="field.autocomplete || null"
                @input="emitInput"
              >
            </div>
          </fieldset>
          <section
            v-if="showFixedAssetPurchaseReference"
            class="mobile-fixed-asset-reference"
            role="alert"
            aria-live="assertive"
          >
            <div class="mobile-fixed-asset-reference__heading">
              <span>额度不足，无法上报</span>
              <strong>{{ fixedAssetPrecheck.oeItemName || "同款器皿" }}</strong>
            </div>
            <image-gallery :value="fixedAssetPrecheck" />
            <dl class="mobile-fixed-asset-reference__details">
              <div><dt>器皿编码</dt><dd>{{ fixedAssetPrecheck.oeItemCode || "-" }}</dd></div>
              <div><dt>规格说明</dt><dd>{{ fixedAssetPrecheck.itemDescription || "-" }}</dd></div>
              <div><dt>领用单位</dt><dd>{{ fixedAssetPrecheck.orderUnit || "-" }}</dd></div>
              <div><dt>购买说明</dt><dd>{{ fixedAssetPrecheck.purchaseReferenceNote || "-" }}</dd></div>
            </dl>
            <a
              v-if="safePurchaseReferenceUrl"
              :href="safePurchaseReferenceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="mobile-fixed-asset-reference__link"
            >查看同款购买页面</a>
            <p v-if="purchaseReferenceReady" class="mobile-fixed-asset-reference__guidance">
              请按同款购买参考自行购买，无需再次上报。
            </p>
            <p v-else class="mobile-fixed-asset-reference__missing">
              同款资料尚未完善，请联系仓库<span v-if="missingReferenceText">（缺少：{{ missingReferenceText }}）</span>。
            </p>
          </section>
        </div>
        <div
          v-if="error || effectiveSubmitModes.length === 0"
          :id="errorSummaryId"
          ref="errorSummary"
          class="mobile-form-error-summary"
          role="alert"
          aria-live="assertive"
          tabindex="-1"
        >
          <p>{{ error || "当前账号没有保存权限" }}</p>
          <button v-if="validationError" type="button" @click="focusValidationError">转到错误字段</button>
        </div>
        <p v-if="uploadsUnfinished" role="status">附件尚未上传完成，请处理后再保存。</p>
        <p v-if="returnSourceLoading" role="status">正在加载原单明细…</p>
        <p v-if="returnSourceError" role="alert">{{ returnSourceError }}</p>
        <footer class="form-footer mobile-system-sheet__footer">
          <span v-if="saving" class="mobile-dialog-status" role="status" aria-live="polite">正在保存表单</span>
          <div class="detail-actions">
            <button type="button" class="tertiary" @click="$emit('close')">取消</button>
            <button
              v-for="mode in effectiveSubmitModes"
              :key="mode.action"
              :type="mode.action === primarySubmitAction ? 'submit' : 'button'"
              :class="submitModeClass(mode)"
              :disabled="saving || uploadsUnfinished || returnSourceLoading || !!returnSourceError || mode.disabled === true"
              :title="mode.disabledReason || null"
              @click="handleSubmitModeClick(mode)"
            >
              {{ saving ? "保存中..." : mode.label }}
            </button>
          </div>
        </footer>
      </form>
    </article>
  </section>
</template>

<script>
import ImageGallery from "@/components/ImageGallery"
import MobileEntityPicker from "./MobileEntityPicker.vue"
import MobileLineItemsEditor from "./MobileLineItemsEditor.vue"
import { mountMobileOverlay, releaseMobileOverlay } from "./mobileOverlayStack"
import { createMobileDialogFocusManager } from "./mobileDialogFocus"
import { createSalesReturnDataFromOrder, createPurchaseReturnDataFromOrder } from "../mobileReturnSourceOrders"
import { getSalesReturnSourceOrder } from "@/api/inventory/salesReturn"
import { getPurchaseReturnSourceOrder } from "@/api/inventory/purchaseReturn"
import ImageUpload from "@/components/ImageUpload"

const { isFieldRequired } = require("../mobileValidation")
const { focusElementAndVerify, runFocusWithFallback } = require("./mobileFocus")
const { applyMobileTransferSmartPasteRecipient } = require("../mobileTransferSmartPaste")

export default {
  name: "MobileFormSheet",
  components: { ImageGallery, MobileEntityPicker, MobileLineItemsEditor, ImageUpload },
  props: {
    open: Boolean,
    contextKey: { type: [String, Number], default: '' },
    title: { type: String, default: "" },
    feature: { type: Object, required: true },
    config: { type: Object, required: true },
    value: { type: Object, default: () => ({}) },
    saving: Boolean,
    error: { type: String, default: "" },
    validationError: { type: Object, default: null },
    fixedAssetPrecheck: { type: Object, default: null },
    iconPaths: { type: Object, required: true },
    context: { type: Object, default: () => ({}) },
    submitModes: { type: Array, default: null }
  },
  data() {
    return {
      localData: Object.assign({}, this.value),
      uploadStates: {},
      activeControl: null,
      viewportFrame: null,
      viewportListening: false,
      dialogFocusManager: null,
      returnSourceSequence: 0,
      returnSourceLoading: false,
      returnSourceError: ""
    }
  },
  computed: {
    uploadContextKey() {
      return JSON.stringify([this.contextKey, this.config && this.config.idKey,
        this.value && this.config && this.value[this.config.idKey], this.context && this.context.selectedDeptId])
    },
    uploadsUnfinished() { return Object.values(this.uploadStates).some(Boolean) },
    fields() {
      return (Array.isArray(this.config.fields) ? this.config.fields : []).filter(this.canShowField)
    },
    effectiveSubmitModes() {
      let modes
      if (Array.isArray(this.submitModes)) {
        modes = this.submitModes
      } else {
        modes = Array.isArray(this.config.submitModes) && this.config.submitModes.length
          ? this.config.submitModes
          : [{ label: "保存", action: "save" }]
      }
      return modes.filter(mode => mode && mode.action)
    },
    primarySubmitAction() {
      const submitMode = this.effectiveSubmitModes.find(mode => mode.action === "submit")
      if (submitMode) return submitMode.action
      const finalMode = this.effectiveSubmitModes[this.effectiveSubmitModes.length - 1]
      return finalMode ? finalMode.action : "save"
    },
    defaultSubmitAction() {
      return this.primarySubmitAction
    },
    errorSummaryId() {
      return "mobile-form-" + this._uid + "-error-summary"
    },
    contextDeptLabel() {
      return this.context && this.context.selectedDeptName
        ? this.context.selectedDeptName
        : (this.context && this.context.selectedDeptId ? String(this.context.selectedDeptId) : "")
    },
    showFixedAssetPurchaseReference() {
      return !!(this.fixedAssetPrecheck && this.fixedAssetPrecheck.allowed === false)
    },
    safePurchaseReferenceUrl() {
      const url = this.fixedAssetPrecheck && this.fixedAssetPrecheck.purchaseReferenceUrl
      return /^https:\/\//i.test(url || "") ? url : ""
    },
    purchaseReferenceReady() {
      if (!this.showFixedAssetPurchaseReference) return false
      if (this.fixedAssetPrecheck.purchaseReferenceReady !== undefined && this.fixedAssetPrecheck.purchaseReferenceReady !== null) {
        return this.fixedAssetPrecheck.purchaseReferenceReady === true
      }
      return !!(this.safePurchaseReferenceUrl && this.fixedAssetPrecheck.purchaseReferenceNote)
    },
    missingReferenceText() {
      const fields = this.fixedAssetPrecheck && this.fixedAssetPrecheck.missingPurchaseReferenceFields
      return Array.isArray(fields) ? fields.filter(Boolean).join("、") : ""
    },
    submissionBlocked() {
      return this.showFixedAssetPurchaseReference
    }
  },
  watch: {
    open(value) {
      this.returnSourceSequence += 1
      this.returnSourceLoading = false
      this.returnSourceError = ""
      if (value) {
        this.$nextTick(() => {
          if (!this.open) return
          this.lockFormSheetBody()
          this.activateFormSheetFocus()
          this.startViewportTracking()
          if (this.validationError) this.focusValidationError()
        })
      } else {
        this.stopViewportTracking()
        this.deactivateFormSheetFocus()
        this.releaseFormSheetBodyLock()
      }
    },
    value: {
      deep: true,
      handler(value) {
        this.localData = Object.assign({}, value || {})
      }
    },
    validationError: {
      deep: true,
      handler(value) {
        if (value && this.open) {
          this.$nextTick(this.focusValidationError)
        }
      }
    }
  },
  created() {
    this.dialogFocusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.formDialog,
      getInitialFocus: () => this.getFormSheetInitialFocus(),
      onEscape: () => this.$emit("close")
    })
  },
  mounted() {
    if (this.open) {
      this.$nextTick(() => {
        if (!this.open) return
        this.lockFormSheetBody()
        this.activateFormSheetFocus()
        this.startViewportTracking()
        if (this.validationError) this.focusValidationError()
      })
    }
  },
  beforeDestroy() {
    this.stopViewportTracking()
    this.deactivateFormSheetFocus()
    this.releaseFormSheetBodyLock()
  },
  methods: {
    lockFormSheetBody() {
      mountMobileOverlay("mobile-form-sheet-open")
    },
    releaseFormSheetBodyLock() {
      releaseMobileOverlay("mobile-form-sheet-open")
    },
    getFormSheetInitialFocus() {
      const dialog = this.$refs.formDialog
      if (!dialog || typeof dialog.querySelector !== "function") return this.$refs.formTitle || dialog
      return dialog.querySelector(
        ".mobile-form-field input:not([disabled]), .mobile-form-field textarea:not([disabled]), " +
        ".mobile-form-field select:not([disabled]), .mobile-form-field button:not([disabled])"
      ) || this.$refs.formTitle || dialog
    },
    activateFormSheetFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.activate() : false
    },
    deactivateFormSheetFocus() {
      return this.dialogFocusManager ? this.dialogFocusManager.deactivate() : false
    },
    ariaBoolean(value) {
      return value ? "true" : "false"
    },
    fieldIsRequired(field) {
      return isFieldRequired(field, this.localData)
    },
    isCustomField(field) {
      return ["entity-picker", "line-items", "image-upload"].indexOf(field && field.type) > -1
    },
    imageUploadAction(field) {
      return field && field.action ? field.action : "/file/upload"
    },
    imageUploadData(field) {
      if (!(field && field.action)) {
        return field && field.data ? field.data : undefined
      }
      const data = field.data ? Object.assign({}, field.data) : {}
      if (this.hasRepairImageShopContext()) {
        data.shopDeptId = this.context.selectedDeptId
      }
      return data
    },
    imageUploadDisabled(field) {
      if (!(field && field.action)) return false
      return !this.hasRepairImageShopContext()
    },
    imageUploadDeleteOnRemove(field) {
      return !!(field && field.deleteOnRemove)
    },
    hasRepairImageShopContext() {
      const type = this.context && this.context.selectedDeptType
      const isStore = String(type || "").trim().toUpperCase() === "STORE"
      return isStore && this.hasPositiveShopDeptId(this.context && this.context.selectedDeptId)
    },
    hasPositiveShopDeptId(value) {
      if (value === undefined || value === null || value === "") return false
      const numeric = Number(value)
      return Number.isInteger(numeric) && numeric > 0
    },
    fieldIdPart(field) {
      return String(field && field.key ? field.key : "field").replace(/[^a-zA-Z0-9_-]/g, "-")
    },
    fieldControlId(field) {
      return "mobile-form-" + this._uid + "-" + this.fieldIdPart(field)
    },
    fieldLabelId(field) {
      return this.fieldControlId(field) + "-label"
    },
    isFieldInvalid(field) {
      return !!(this.validationError && String(this.validationError.fieldKey) === String(field.key))
    },
    fieldDescribedBy(field) {
      return this.isFieldInvalid(field) ? this.errorSummaryId : null
    },
    submitModeClass(mode) {
      return mode.action === this.primarySubmitAction ? "primary" : "secondary"
    },
    handleSubmitModeClick(mode) {
      if (mode.action !== this.primarySubmitAction) this.emitSubmit(mode.action)
    },
    fieldDisplayValue(field) {
      if (field.displayValue !== undefined && field.displayValue !== null) {
        return field.displayValue
      }
      const value = this.localData[field.key]
      return value === undefined || value === null ? "" : value
    },
    canShowField(field) {
      if (!field) return false
      if (field.visibleWhen && field.visibleWhen.key &&
        String(this.localData[field.visibleWhen.key]) !== String(field.visibleWhen.value)) {
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
    handleFieldInput(field) {
      ;(field.clearFieldsOnChange || []).forEach(key => {
        const targetField = this.fields.find(item => item.key === key)
        this.$set(this.localData, key, targetField && targetField.type === "line-items" ? [] : "")
      })
      this.emitInput()
    },
    handleEntitySelectionCleared(field) {
      if (["salesOrderId", "purchaseOrderId"].includes(field.key)) {
        this.returnSourceSequence += 1
        this.returnSourceLoading = false
        this.returnSourceError = ""
      }
      const relatedFields = {
        customerId: ["customerName"],
        supplierId: ["supplierName"],
        salesOrderId: ["salesOrderNo", "customerId", "customerName", "details"],
        purchaseOrderId: ["purchaseOrderNo", "supplierId", "supplierName", "details"],
        counterUserId: ["counterName"],
        oeItemId: ["oeItemName"]
      }
      ;(relatedFields[field.key] || []).forEach(key => {
        const targetField = this.fields.find(item => item.key === key)
        this.$set(this.localData, key, targetField && targetField.type === "line-items" ? [] : "")
      })
      if (field.fallbackLabelKey) {
        this.$set(this.localData, field.fallbackLabelKey, "")
      }
      this.emitInput()
    },
    handleEntitySelect(field, option) {
      const row = option && option.row ? option.row : {}
      const label = option && option.label ? option.label : ""
      if (option && option.value !== undefined) {
        this.$set(this.localData, field.key, option.value)
      }

      if (field.salesWarehouseDefault && Array.isArray(this.localData.details)) {
        this.$set(this.localData, "details", this.localData.details.map(detail => Object.assign({}, detail,
          detail.warehouseId ? {} : { warehouseId: option.value, warehouseName: label })))
      }
      if (field.key === "customerId") {
        this.setIfValue("customerName", row.customerName || label)
      }
      if (field.key === "supplierId") {
        this.setIfValue("supplierName", row.supplierName || label)
      }
      if (field.key === "salesOrderId") {
        const orderNo = row.orderNo || row.salesOrderNo || label
        this.updateReturnTitle("销售退货-", this.localData.salesOrderNo, orderNo)
        this.setIfValue("salesOrderNo", orderNo)
        this.setIfValue("customerId", row.customerId)
        this.setIfValue("customerName", row.customerName)
        this.setIfEmpty("returnTitle", orderNo ? "销售退货-" + orderNo : "")
        this.loadSalesReturnSourceOrder(option && option.value, row)
        return
      }
      if (field.key === "purchaseOrderId") {
        const orderNo = row.orderNo || row.purchaseOrderNo || label
        this.updateReturnTitle("采购退货-", this.localData.purchaseOrderNo, orderNo)
        this.setIfValue("purchaseOrderNo", orderNo)
        this.setIfValue("supplierId", row.supplierId)
        this.setIfValue("supplierName", row.supplierName)
        this.setIfEmpty("returnTitle", orderNo ? "采购退货-" + orderNo : "")
        this.loadPurchaseReturnSourceOrder(option && option.value, row)
        return
      }

      this.emitInput()
    },
    applySmartPasteRecipient(recipient) {
      this.localData = applyMobileTransferSmartPasteRecipient(this.localData, recipient)
      this.emitInput()
    },
    updateReturnTitle(prefix, previousOrderNo, nextOrderNo) {
      if (!this.localData.returnTitle || this.localData.returnTitle === prefix + (previousOrderNo || "")) {
        this.$set(this.localData, "returnTitle", nextOrderNo ? prefix + nextOrderNo : "")
      }
    },
    loadSalesReturnSourceOrder(orderId, row) {
      return this.loadReturnSourceOrder("salesOrderId", orderId, row, getSalesReturnSourceOrder, createSalesReturnDataFromOrder)
    },
    loadPurchaseReturnSourceOrder(orderId, row) {
      return this.loadReturnSourceOrder("purchaseOrderId", orderId, row, getPurchaseReturnSourceOrder, createPurchaseReturnDataFromOrder)
    },
    loadReturnSourceOrder(key, orderId, row, fetchOrder, createData) {
      const sequence = ++this.returnSourceSequence
      this.returnSourceLoading = !!orderId
      this.returnSourceError = ""
      this.$set(this.localData, "details", [])
      this.emitInput()
      if (!orderId) return Promise.resolve()
      const current = () => this.open && sequence === this.returnSourceSequence
        && String(this.localData[key]) === String(orderId)
      return fetchOrder(orderId).then(response => {
        if (!current()) return
        const source = Object.assign({}, row || {}, response && response.data ? response.data : {})
        if (String(source.orderId) !== String(orderId)) throw new Error("原单信息不匹配")
        this.applyReturnSourceData(createData(source, this.localData))
      }).catch(() => {
        if (!current()) return
        this.returnSourceError = "原单加载失败，请重新选择原单后再保存"
        this.emitInput()
      }).finally(() => {
        if (current()) this.returnSourceLoading = false
      })
    },
    applyReturnSourceData(data) {
      Object.keys(data || {}).forEach(key => {
        this.$set(this.localData, key, data[key])
      })
      this.emitInput()
    },
    setIfValue(key, value) {
      if (value === undefined || value === null || String(value).trim() === "") return
      this.$set(this.localData, key, value)
    },
    setIfEmpty(key, value) {
      if (this.localData[key] !== undefined && this.localData[key] !== null && String(this.localData[key]).trim() !== "") return
      this.setIfValue(key, value)
    },
    emitInput() {
      if (this.saving) return
      this.$emit("input", Object.assign({}, this.localData))
    },
    handleUploadState(state) {
      if (!state || state.id == null) return
      if (state.blocking) this.$set(this.uploadStates, state.id, true)
      else this.$delete(this.uploadStates, state.id)
    },
    emitSubmit(submitAction) {
      if (this.saving || this.uploadsUnfinished) return
      if (this.returnSourceLoading || this.returnSourceError) return
      const mode = this.effectiveSubmitModes.find(item => item.action === submitAction)
      if (mode && mode.disabled) return
      this.emitInput()
      this.$emit("submit", submitAction)
    },
    handleFocusIn(event) {
      const target = event && event.target
      if (!target || !/^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return
      this.activeControl = target
      this.scheduleActiveControlScroll()
    },
    startViewportTracking() {
      if (typeof window === "undefined" || !window.visualViewport || this.viewportListening) return
      window.visualViewport.addEventListener("resize", this.handleViewportChange, { passive: true })
      window.visualViewport.addEventListener("scroll", this.handleViewportChange, { passive: true })
      this.viewportListening = true
    },
    stopViewportTracking() {
      if (typeof window !== "undefined" && window.visualViewport && this.viewportListening) {
        window.visualViewport.removeEventListener("resize", this.handleViewportChange)
        window.visualViewport.removeEventListener("scroll", this.handleViewportChange)
      }
      this.viewportListening = false
      if (this.viewportFrame === null || typeof window === "undefined") return
      if (typeof window.cancelAnimationFrame === "function") {
        window.cancelAnimationFrame(this.viewportFrame)
      } else {
        window.clearTimeout(this.viewportFrame)
      }
      this.viewportFrame = null
    },
    handleViewportChange() {
      this.scheduleActiveControlScroll()
    },
    scheduleActiveControlScroll() {
      if (!this.open || typeof window === "undefined" || this.viewportFrame !== null) return
      const requestFrame = typeof window.requestAnimationFrame === "function"
        ? window.requestAnimationFrame.bind(window)
        : callback => window.setTimeout(callback, 0)
      this.viewportFrame = requestFrame(() => {
        this.viewportFrame = null
        const focusedControl = typeof document !== "undefined" ? document.activeElement : null
        const target = focusedControl && this.$el.contains(focusedControl) ? focusedControl : this.activeControl
        if (target && this.$el.contains(target)) this.scrollControlIntoView(target)
      })
    },
    scrollControlIntoView(target) {
      return this.scrollTargetWithinFormBody(target)
    },
    scrollTargetWithinFormBody(target) {
      const formBody = this.$refs.formBody
      if (!formBody || !target || typeof formBody.contains !== "function" || !formBody.contains(target)) return false
      if (typeof target.getBoundingClientRect !== "function" || typeof formBody.getBoundingClientRect !== "function") return false
      const targetRect = target.getBoundingClientRect()
      const bodyRect = formBody.getBoundingClientRect()
      const visibleHeight = Number(formBody.clientHeight || bodyRect.height || 0)
      const targetHeight = Number(targetRect.height || 0)
      const centerOffset = Math.max(16, (visibleHeight - targetHeight) / 2)
      const nextTop = Math.max(0, Number(formBody.scrollTop || 0) + targetRect.top - bodyRect.top - centerOffset)
      if (typeof formBody.scrollTo === "function") {
        formBody.scrollTo({ top: nextTop, behavior: "smooth" })
      } else {
        formBody.scrollTop = nextTop
      }
      return true
    },
    focusElement(target) {
      if (!focusElementAndVerify(target)) return false
      this.activeControl = target
      this.scrollControlIntoView(target)
      return true
    },
    focusValidationError() {
      if (!this.validationError || !this.$el) return this.focusErrorSummary()
      const fieldKey = String(this.validationError.fieldKey)
      const fieldContainers = Array.prototype.slice.call(this.$el.querySelectorAll("[data-field-key]"))
      const fieldContainer = fieldContainers.find(node => node.getAttribute("data-field-key") === fieldKey)
      if (!fieldContainer) return this.focusErrorSummary()

      let targetContainer = fieldContainer
      if (this.validationError.rowIndex !== null && this.validationError.rowIndex !== undefined) {
        const rowIndex = String(this.validationError.rowIndex)
        const itemFieldKey = String(this.validationError.itemFieldKey || "")
        const itemContainers = Array.prototype.slice.call(
          fieldContainer.querySelectorAll("[data-row-index][data-item-field-key]")
        )
        const itemContainer = itemContainers.find(node => {
          return node.getAttribute("data-row-index") === rowIndex &&
            node.getAttribute("data-item-field-key") === itemFieldKey
        })
        if (!itemContainer) return this.focusErrorSummary()
        targetContainer = itemContainer
      }

      const target = targetContainer.querySelector(
        "input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [data-validation-control], button, [tabindex]"
      ) || targetContainer
      return runFocusWithFallback(
        () => this.focusElement(target),
        () => this.focusErrorSummary()
      )
    },
    focusErrorSummary() {
      return this.focusElement(this.$refs.errorSummary)
    }
  }
}
</script>

<style scoped lang="scss">
fieldset.mobile-form-list { min-width: 0; margin: 0; padding: 0; border: 0; }
@import "./mobileSheet.scss";

.mobile-fixed-asset-reference {
  margin: 14px 0 4px;
  padding: 14px;
  border: 1px solid rgba(217, 119, 6, 0.3);
  border-radius: 14px;
  background: rgba(255, 247, 237, 0.96);
  color: #7c2d12;
}

.mobile-fixed-asset-reference__heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.mobile-fixed-asset-reference__heading span {
  color: #b45309;
  font-size: 12px;
  font-weight: 700;
}

.mobile-fixed-asset-reference__heading strong {
  color: #7c2d12;
  font-size: 17px;
}

.mobile-fixed-asset-reference__image {
  width: 100%;
  max-height: 210px;
  margin-bottom: 10px;
  border-radius: 10px;
  object-fit: cover;
}

.mobile-fixed-asset-reference__details {
  margin: 0;
}

.mobile-fixed-asset-reference__details div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid rgba(180, 83, 9, 0.12);
}

.mobile-fixed-asset-reference__details dt,
.mobile-fixed-asset-reference__details dd {
  margin: 0;
  font-size: 13px;
  line-height: 19px;
}

.mobile-fixed-asset-reference__details dt {
  color: #9a3412;
  font-weight: 700;
}

.mobile-fixed-asset-reference__link {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  margin-top: 12px;
  border-radius: 10px;
  background: #c2410c;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.mobile-fixed-asset-reference__guidance,
.mobile-fixed-asset-reference__missing {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 19px;
}

.mobile-fixed-asset-reference__missing {
  color: #b91c1c;
  font-weight: 700;
}
</style>

<style lang="scss">
body.mobile-form-sheet-open {
  overflow: hidden;
}

body.mobile-form-sheet-open .bottom-nav {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}
</style>
