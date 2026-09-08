<template>
  <el-select
    :value="selectedValue"
    filterable
    remote
    reserve-keyword
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder || defaultPlaceholder"
    :size="size"
    :loading="loading"
    :style="{ width: width }"
    :remote-method="searchItems"
    @focus="loadDefaultOptions"
    @input="$emit('input', $event)"
    @change="handleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="item in options"
      :key="item.itemType + ':' + item.itemId"
      :label="itemLabel(item)"
      :value="item.itemId"
    >
      <div class="item-option">
        <div class="item-main">
          <span class="item-name">{{ displayValue(item.itemName) }}</span>
          <span class="item-code">{{ displayValue(item.itemCode) }}</span>
        </div>
        <div class="item-meta">{{ itemMeta(item) }}</div>
      </div>
    </el-option>
  </el-select>
</template>

<script>
import { getProduct, listProduct } from "@/api/inventory/product"
import { getOe, listOe } from "@/api/inventory/oe"
import { getGift, listGift } from "@/api/inventory/gift"

const ITEM_TYPES = {
  product: { label: "商品", id: "productId", code: "productCode", name: "productName" },
  oe: { label: "器皿", id: "oeItemId", code: "oeItemCode", name: "oeItemName" },
  gift: { label: "礼盒", id: "giftId", code: "giftCode", name: "giftName" }
}

export default {
  name: "InventoryItemSelect",
  props: {
    value: [Number, String],
    itemType: { type: String, default: "product" },
    placeholder: { type: String, default: "" },
    size: { type: String, default: "small" },
    width: { type: String, default: "220px" },
    clearable: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false }
  },
  data() {
    return {
      loading: false,
      options: []
    }
  },
  computed: {
    selectedValue() {
      if (!this.hasValue(this.value)) return this.value
      const matchedItem = this.options.find(item => String(item.itemId) === String(this.value))
      return matchedItem ? matchedItem.itemId : undefined
    },
    normalizedType() {
      return ITEM_TYPES[this.itemType] ? this.itemType : "product"
    },
    defaultPlaceholder() {
      return "搜索" + ITEM_TYPES[this.normalizedType].label + "名称/编码"
    }
  },
  watch: {
    itemType() {
      this.options = []
      if (this.hasValue(this.value)) this.fetchSelectedItem(this.value)
    },
    value: {
      immediate: true,
      handler(value) {
        if (this.hasValue(value) && !this.hasItem(value)) this.fetchSelectedItem(value)
      }
    }
  },
  methods: {
    loadDefaultOptions() {
      if (this.options.length === 0) this.searchItems("")
    },
    searchItems(query) {
      const keyword = (query || "").trim()
      this.$emit("search-keyword-change", keyword)
      this.loading = true
      this.listRequests(keyword).then(results => {
        const merged = []
        results.forEach(res => {
          ;(res.rows || []).forEach(raw => {
            const item = this.normalizeItem(raw)
            if (item && this.isEnabled(raw) && !merged.some(row => String(row.itemId) === String(item.itemId))) {
              merged.push(item)
            }
          })
        })
        this.options = merged
      }).finally(() => {
        this.loading = false
      })
    },
    listRequests(keyword) {
      if (this.normalizedType === "oe") {
        return Promise.all(keyword
          ? [listOe({ pageNum: 1, pageSize: 20, oeItemName: keyword, status: "0" }), listOe({ pageNum: 1, pageSize: 20, oeItemCode: keyword, status: "0" })]
          : [listOe({ pageNum: 1, pageSize: 30, status: "0" })])
      }
      if (this.normalizedType === "gift") {
        return Promise.all(keyword
          ? [listGift({ pageNum: 1, pageSize: 20, giftName: keyword, status: "0" }), listGift({ pageNum: 1, pageSize: 20, giftCode: keyword, status: "0" })]
          : [listGift({ pageNum: 1, pageSize: 30, status: "0" })])
      }
      return Promise.all([listProduct({ pageNum: 1, pageSize: 30, status: "0", ...(keyword ? { keyword } : {}) })])
    },
    fetchSelectedItem(itemId) {
      const getRequest = this.normalizedType === "oe" ? getOe(itemId)
        : this.normalizedType === "gift" ? getGift(itemId) : getProduct(itemId)
      getRequest.then(res => {
        const item = this.normalizeItem(res.data)
        if (item && !this.hasItem(item.itemId)) this.options = [item].concat(this.options)
      }).catch(() => {})
    },
    normalizeItem(raw) {
      if (!raw) return null
      const type = this.normalizedType
      const fields = ITEM_TYPES[type]
      const itemId = raw[fields.id]
      if (!this.hasValue(itemId)) return null
      return {
        ...raw,
        itemType: type,
        itemId,
        itemCode: raw[fields.code] || "",
        itemName: raw[fields.name] || "",
        productId: type === "product" ? itemId : null,
        productCode: raw[fields.code] || "",
        productName: raw[fields.name] || "",
        spec: type === "oe" ? (raw.itemDescription || "") : (raw.spec || ""),
        unit: type === "oe" ? (raw.orderUnit || "") : type === "gift" ? (raw.replenishmentUnit || "") : (raw.unit || ""),
        grade: raw.grade || "",
        purchasePrice: type === "oe" ? (raw.costPrice || 0) : type === "gift" ? 0 : (raw.purchasePrice || 0),
        salesPrice: type === "gift" ? (raw.guidePrice1 || raw.guidePrice2 || 0) : (raw.salesPrice || 0),
        costPrice: raw.costPrice || 0,
        supplierName: type === "gift" ? "礼盒" : (raw.supplierName || "")
      }
    },
    handleChange(itemId) {
      const item = this.options.find(row => String(row.itemId) === String(itemId)) || null
      this.$emit("search-keyword-change", "")
      this.$emit("change", itemId)
      this.$emit("selected", item)
    },
    handleClear() {
      this.$emit("search-keyword-change", "")
      this.$emit("selected", null)
    },
    itemLabel(item) {
      const itemName = this.hasValue(item.itemName) ? item.itemName : "未命名物料"
      return item.itemCode ? itemName + "（" + item.itemCode + "）" : itemName
    },
    itemMeta(item) {
      return [ITEM_TYPES[item.itemType].label, item.spec, item.unit, item.grade]
        .filter(this.hasValue).join(" / ") || "-"
    },
    displayValue(value) {
      return this.hasValue(value) ? value : "-"
    },
    isEnabled(raw) {
      return !this.hasValue(raw.status) || raw.status === "0"
    },
    hasItem(itemId) {
      return this.options.some(item => String(item.itemId) === String(itemId))
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ""
    }
  }
}
</script>

<style lang="scss" scoped>
.item-option {
  line-height: 1.4;
  padding: 4px 0;
}

.item-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.item-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-code,
.item-meta {
  color: #8492a6;
  font-size: 12px;
}

.item-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
