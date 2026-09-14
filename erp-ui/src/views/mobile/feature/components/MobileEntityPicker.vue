<template>
  <div class="mobile-entity-picker">
    <template v-if="entity === 'sales'">
      <p v-if="value">{{ selectedOptionLabel || fallbackLabel || ('已选原单 ' + value) }}</p>
      <button type="button" @click="toggleSalesSource">{{ open ? '收起原单查询' : '查找原销售单' }}</button>
      <sales-return-source-picker v-if="open" :context-key="salesSourceContext + String(salesSourceEpoch)" @select="selectSalesSource" />
    </template>
    <input v-else
      ref="searchInput"
      :id="inputId || null"
      v-model.trim="keyword"
      type="search"
      :required="required"
      :aria-label="searchAriaLabel"
      :aria-required="ariaBoolean(required)"
      :aria-invalid="ariaBoolean(invalid)"
      :aria-describedby="describedBy || null"
      :maxlength="maxKeywordLength"
      :placeholder="placeholderText"
      @input="handleKeywordInput"
      @focus="open = true"
      @keydown.enter="handleSearchEnter"
    >
    <button v-if="entity !== 'sales'" type="button" @click="searchOptions">{{ loading ? "搜索中" : "搜索" }}</button>
    <div v-if="open && entity !== 'sales'" class="picker-options">
      <p v-if="loading" class="picker-state">正在加载可选数据...</p>
      <div v-else-if="loadError" class="picker-state picker-error" role="alert">
        <p>{{ loadError }}</p>
        <button type="button" @click="searchOptions">重新加载</button>
      </div>
      <button
        v-for="option in loading || loadError ? [] : options"
        :key="String(option.value)"
        type="button"
        :class="{ active: String(option.value) === String(value) }"
        @click="selectOption(option)"
      >
        <strong>{{ option.label }}</strong>
        <span>{{ option.meta || "可选择" }}</span>
      </button>
      <template v-if="!loading && !loadError && options.length === 0">
        <p class="picker-state">{{ emptyText }}</p>
        <button v-if="canQuickCreateCustomer" type="button" class="quick-create-button" @click="openQuickCustomerForm">
          新建客户
        </button>
        <p v-else-if="entity === 'customer'" class="picker-guidance">当前门店暂无客户，请联系有权限人员新增</p>
      </template>
    </div>
    <mobile-quick-customer-form
      v-model="quickCustomerDraft"
      :open="quickCustomerOpen"
      :saving="quickCustomerSaving"
      :error="quickCustomerError"
      @close="closeQuickCustomerForm"
      @submit="createQuickCustomer"
    />
  </div>
</template>

<script>
import { createCustomerServiceCard, getCustomerServiceCardCapabilities } from "@/api/inventory/customer"
import MobileQuickCustomerForm from "./MobileQuickCustomerForm.vue"
import { fetchMobileEntityOptions } from "../mobileEntityService"
const {
  createCustomerDraft,
  normalizeQuickCustomerPayload,
  mapCreatedCustomerOption,
  normalizeCustomerServiceCardContext,
  getQuickCustomerErrorMessage,
  normalizeCustomerServiceCardCapabilities,
  isCustomerQuickCreateAllowed
} = require("../mobileQuickCustomer")
const { shouldClearEntitySelection } = require("../mobileEntitySelection")
const { runMobileSearchEnter } = require("./mobileSearchKeyboard")
const { focusElementAndVerify } = require("./mobileFocus")

const MAX_MOBILE_OPTION_KEYWORD_LENGTH = 80

export default {
  name: "MobileEntityPicker",
  components: { MobileQuickCustomerForm, SalesReturnSourcePicker: () => import("@/views/inventory/components/SalesReturnSourcePicker.vue") },
  props: {
    value: {
      type: [String, Number],
      default: ""
    },
    inputId: {
      type: String,
      default: ""
    },
    required: Boolean,
    invalid: Boolean,
    describedBy: {
      type: String,
      default: ""
    },
    entity: {
      type: String,
      required: true
    },
    label: {
      type: String,
      default: ""
    },
    context: {
      type: Object,
      default: () => ({})
    },
    field: {
      type: Object,
      default: () => ({})
    },
    formData: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      keyword: "",
      loading: false,
      loadError: "",
      open: false,
      selectedOptionLabel: "", salesSourceForm: null, salesSourceFrozenContext: "", salesSourceEpoch: 0,
      maxKeywordLength: MAX_MOBILE_OPTION_KEYWORD_LENGTH,
      options: [],
      entityRequestSequence: 0,
      optionsRequestSequence: -1,
      entityInactive: false,
      quickCustomerOpen: false,
      quickCustomerSaving: false,
      quickCustomerError: "",
      quickCustomerRequestKey: "",
      quickCustomerDraft: createCustomerDraft(""),
      customerCapability: {
        contextKey: "",
        resolved: false,
        writeEnabled: false
      },
      customerCapabilityRequestSequence: 0
    }
  },
  computed: {
    entityDependencyKey() {
      const form = this.formData || {}, field = this.field || {}
      return JSON.stringify([field, form.orderId, form.supplierName, form.fromWarehouseId, form.fromDeptId,
        form.warehouseId, form.shopDeptId, field.dependsOn && form[field.dependsOn]])
    },
    salesSourceContext() { return JSON.stringify([this.context, this.formData && this.formData.returnId, this.formData && this.formData.shopDeptId]) },
    searchAriaLabel() {
      return "搜索并选择" + (this.label || "数据")
    },
    placeholderText() {
      return this.field.placeholder || ("搜索" + (this.label || "数据"))
    },
    emptyText() {
      if (this.field.dependsOn && !this.formData[this.field.dependsOn]) {
        return "请先选择" + (this.field.dependsOnLabel || "上级字段")
      }
      if (this.field.emptyText) {
        return this.field.emptyText
      }
      return "暂无可选数据"
    },
    fallbackLabel() {
      const key = this.field && this.field.fallbackLabelKey
      const value = key && this.formData ? this.formData[key] : ""
      return value === undefined || value === null ? "" : String(value).trim()
    },
    customerCapabilityContextKey() {
      if (this.entity !== "customer") return ""
      return normalizeCustomerServiceCardContext(this.context).contextKey
    },
    canQuickCreateCustomer() {
      return isCustomerQuickCreateAllowed({
        entity: this.entity,
        field: this.field,
        hasPermission: this.hasAnyPermission(["inv:customerCard:add"]),
        capability: this.customerCapability,
        contextKey: this.customerCapabilityContextKey
      })
    }
  },
  watch: {
    context: { deep: true, handler() { this.invalidateEntityOptions() } },
    entity() { this.invalidateEntityOptions(); this.open = false },
    entityDependencyKey() { this.invalidateEntityOptions() },
    open(value) { if (!value && this.entity !== "sales") this.invalidateEntityOptions() },
    "$store.state.user.sessionRevision"() { this.invalidateEntityOptions(); this.open = false },
    formData(value, before) { if (value !== before) this.invalidateEntityOptions(); if (this.entity === "sales" && value !== before) { this.salesSourceEpoch += 1; this.open = false; this.salesSourceForm = null } },
    value(value) {
      if (value === undefined || value === null || String(value).trim() === "") {
        this.selectedOptionLabel = ""
      }
      this.hydrateKeywordFromOptions()
    },
    options() {
      this.hydrateKeywordFromOptions()
    },
    fallbackLabel() {
      this.hydrateKeywordFromOptions()
    },
    customerCapabilityContextKey(value, previous) {
      if (value === previous) return
      this.customerCapability = { contextKey: value, resolved: false, writeEnabled: false }
      this.quickCustomerOpen = false
      this.quickCustomerError = ""
      this.loadCustomerServiceCardCapabilities()
    }
  },
  mounted() {
    this._entityDeptChanged = () => { this.invalidateEntityOptions(); this.open = false }
    if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this._entityDeptChanged)
    if (this.fallbackLabel) {
      this.keyword = this.fallbackLabel
      if (this.value !== undefined && this.value !== null && String(this.value).trim() !== "") {
        this.selectedOptionLabel = this.fallbackLabel
      }
    }
    this.loadCustomerServiceCardCapabilities()
    this.searchOptions()
  },
  beforeDestroy() {
    this.entityInactive = true
    this.invalidateEntityOptions()
    this.customerCapabilityRequestSequence += 1
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this._entityDeptChanged)
  },
  deactivated() { this.entityInactive = true; this.invalidateEntityOptions(); this.open = false },
  activated() { this.entityInactive = false },
  methods: {
    entityQueryKey() {
      const user = this.$store && this.$store.state && this.$store.state.user || {}
      return JSON.stringify([this.context, this.entity, this.entityDependencyKey,
        String(this.keyword || "").trim(), user.id, user.sessionRevision])
    },
    invalidateEntityOptions() {
      this.entityRequestSequence += 1
      this.optionsRequestSequence = -1
      this.loading = false
      this.options = []
      this.loadError = ""
    },
    ariaBoolean(value) {
      return value ? "true" : "false"
    },
    handleKeywordInput() {
      this.invalidateEntityOptions()
      if (shouldClearEntitySelection(this.value, this.keyword, this.selectedOptionLabel)) {
        this.selectedOptionLabel = ""
        this.$emit("input", "")
        this.$emit("selection-cleared")
      }
      this.$emit("field-edit")
    },
    handleSearchEnter(event) {
      return runMobileSearchEnter(event, () => this.searchOptions())
    },
    loadCustomerServiceCardCapabilities() {
      const requestSequence = ++this.customerCapabilityRequestSequence
      const contextKey = this.customerCapabilityContextKey
      this.customerCapability = { contextKey, resolved: false, writeEnabled: false }
      if (this.entity !== "customer" || this.field.quickCreate !== true) {
        this.customerCapability = { contextKey: "", resolved: false, writeEnabled: false }
        return Promise.resolve(this.customerCapability)
      }
      if (!contextKey) return Promise.resolve(this.customerCapability)
      return getCustomerServiceCardCapabilities().then(response => {
        if (requestSequence !== this.customerCapabilityRequestSequence ||
          this.customerCapabilityContextKey !== contextKey) return this.customerCapability
        this.customerCapability = normalizeCustomerServiceCardCapabilities(response, contextKey)
        return this.customerCapability
      }).catch(() => {
        if (requestSequence === this.customerCapabilityRequestSequence &&
          this.customerCapabilityContextKey === contextKey) {
          this.customerCapability = { contextKey, resolved: true, writeEnabled: false }
        }
        return this.customerCapability
      })
    },
    searchOptions() {
      if (this.entity === "sales") { if (!this.open) this.toggleSalesSource(); return Promise.resolve() }
      if (this.entityInactive) return Promise.resolve([])
      const sequence = ++this.entityRequestSequence
      const contextKey = this.entityQueryKey(), form = this.formData
      const keyword = String(this.keyword || "").trim()
      const current = () => !this.entityInactive && sequence === this.entityRequestSequence &&
        form === this.formData && contextKey === this.entityQueryKey()
      this.optionsRequestSequence = -1
      this.loadError = ""
      this.loading = true
      return fetchMobileEntityOptions(this.entity, {
        keyword,
        context: { ...this.context },
        field: { ...this.field },
        formData: { ...form }
      }).then(options => {
        if (!current()) return []
        this.options = Array.isArray(options) ? options : []
        this.hydrateKeywordFromOptions()
        this._optionsQueryKey = this.entityQueryKey()
        this._optionsForm = form
        this.optionsRequestSequence = sequence
        this.open = true
        this.loading = false
        if (this.entity === "warehouse" && this.field.salesWarehouseDefault && !this.value && !keyword) {
          const selected = options.find(option => String(option.value) === String(this.context.selectedDeptId)) ||
            (options.length === 1 ? options[0] : null)
          if (selected) this.selectOption(selected)
        }
      }).catch(() => {
        if (!current()) return []
        this.options = []
        this.loadError = this.entity === "customer" ? "加载客户失败，请重试" : "加载可选数据失败，请重试"
      }).finally(() => {
        if (current()) this.loading = false
      })
    },
    hasAnyPermission(permissions) {
      const userPermissions = this.context && Array.isArray(this.context.permissions) ? this.context.permissions : []
      if (userPermissions.indexOf("*:*:*") > -1) return true
      return permissions.some(permission => userPermissions.indexOf(permission) > -1)
    },
    openQuickCustomerForm() {
      if (!this.canQuickCreateCustomer) {
        this.quickCustomerError = "当前门店暂未开放客户新建，请联系管理员"
        return
      }
      this.quickCustomerDraft = createCustomerDraft(this.keyword)
      this.quickCustomerRequestKey = `mobile-card-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      this.quickCustomerError = ""
      this.quickCustomerOpen = true
    },
    closeQuickCustomerForm() {
      if (this.quickCustomerSaving) return
      this.quickCustomerOpen = false
      this.quickCustomerError = ""
    },
    createQuickCustomer() {
      if (!this.canQuickCreateCustomer) {
        this.quickCustomerError = "当前门店暂未开放客户新建，请联系管理员"
        return
      }
      let payload
      try {
        payload = normalizeQuickCustomerPayload(this.quickCustomerDraft)
      } catch (error) {
        this.quickCustomerError = getQuickCustomerErrorMessage(error)
        return
      }
      this.quickCustomerSaving = true
      this.quickCustomerError = ""
      createCustomerServiceCard(Object.assign({}, payload, {
        requestKey: this.quickCustomerRequestKey,
        sourceClient: "MOBILE"
      })).then(response => {
        const option = mapCreatedCustomerOption(response && response.data)
        this.options = [option].concat(this.options.filter(item => String(item.value) !== String(option.value)))
        this.quickCustomerOpen = false
        this.selectOption(option, "created-customer")
        this.$nextTick(this.focusSearchInputAfterQuickCreate)
      }).catch(error => {
        this.quickCustomerError = getQuickCustomerErrorMessage(error, "创建客户失败，请重试")
      }).finally(() => {
        this.quickCustomerSaving = false
      })
    },
    focusSearchInputAfterQuickCreate() {
      return focusElementAndVerify(this.$refs.searchInput)
    },
    hydrateKeywordFromOptions() {
      if (this.value === undefined || this.value === null || String(this.value).trim() === "") {
        if (!this.open || !this.keyword) {
          this.keyword = this.fallbackLabel
        }
        return
      }
      if (this.open && this.keyword && this.keyword !== String(this.value)) return
      const option = this.options.find(option => String(option.value) === String(this.value))
      if (option && option.label) {
        this.selectedOptionLabel = option.label
        this.keyword = option.label
      }
    },
    toggleSalesSource() {
      this.open = !this.open
      this.salesSourceForm = this.open ? this.formData : null
      this.salesSourceFrozenContext = this.salesSourceContext
      this.salesSourceEpoch += 1
    },
    selectSalesSource(row) {
      if (this.entity !== "sales" || !this.open || this.salesSourceForm !== this.formData || this.salesSourceFrozenContext !== this.salesSourceContext) return
      this.selectOption({ value: row.orderId, label: row.orderNo || row.orderId, meta: row.customerName, row }, "sales-source")
    },
    selectOption(option, source) {
      if (source !== "sales-source" && source !== "created-customer" &&
          (this.entityInactive || !this.open || this.loading || this.optionsRequestSequence !== this.entityRequestSequence ||
            this._optionsQueryKey !== this.entityQueryKey() || this._optionsForm !== this.formData || !this.options.includes(option))) return
      this.selectedOptionLabel = option.label || ""
      this.$emit("input", option.value)
      this.$emit("select", option)
      this.keyword = option.label
      this.open = false
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-entity-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(72px, auto);
  gap: 10px;
}

.mobile-entity-picker input,
.mobile-entity-picker button {
  min-height: 46px;
  border: 1px solid rgba(28, 50, 38, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  box-sizing: border-box;
  font: inherit;
  font-size: 16px;
}

.mobile-entity-picker > input {
  min-width: 0;
  padding: 0 12px;
}

.mobile-entity-picker > button {
  padding: 0 14px;
  color: var(--mobile-color-primary, #0b6b53);
  font-weight: 800;
  white-space: nowrap;
}

.picker-options {
  grid-column: 1 / -1;
  display: grid;
  gap: 10px;
  max-height: 220px;
  overflow: auto;
}

.picker-options button {
  display: grid;
  gap: 5px;
  padding: 11px 12px;
  text-align: left;
}

.picker-options strong,
.picker-options span {
  min-width: 0;
  white-space: normal;
  word-break: break-word;
}

.picker-options button.active {
  border-color: rgba(34, 122, 91, 0.72);
  background: rgba(218, 245, 232, 0.9);
}

.picker-options span,
.picker-options p {
  margin: 0;
  color: #64756c;
  font-size: 12px;
}

.picker-state,
.picker-guidance {
  grid-column: 1 / -1;
  margin: 0;
  color: #64756c;
  line-height: 1.5;
}

.picker-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.picker-error p {
  color: #b42318;
}

.picker-error button,
.quick-create-button {
  padding: 0 14px;
  color: var(--mobile-color-primary, #0b6b53);
  font-weight: 900;
}

.quick-create-button {
  grid-column: 1 / -1;
}
</style>
