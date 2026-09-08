<template>
  <el-select
    :value="value"
    filterable
    remote
    reserve-keyword
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :size="size"
    :loading="loading"
    :style="{ width: width }"
    :remote-method="searchProduct"
    @focus="loadDefaultOptions"
    @input="$emit('input', $event)"
    @change="handleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="product in productOptions"
      :key="product.productId"
      :label="productLabel(product)"
      :value="product.productId"
    >
      <div v-if="showContextMeta" class="product-option">
        <div class="product-main">
          <span class="product-name">{{ displayValue(product.productName) }}</span>
          <span class="product-code">{{ displayValue(product.productCode) }}</span>
        </div>
        <div class="product-meta">{{ productMeta(product) }}</div>
      </div>
      <template v-else>
        <span>{{ displayValue(product.productName) }}</span>
        <span class="product-code">{{ displayValue(product.productCode) }}</span>
      </template>
    </el-option>
  </el-select>
</template>

<script>
import { getProduct, listProduct } from "@/api/inventory/product"

export default {
  name: "InventoryProductSelect",
  props: {
    value: [Number, String],
    placeholder: { type: String, default: "搜索商品名称/编码" },
    size: { type: String, default: "small" },
    width: { type: String, default: "220px" },
    clearable: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    keywordSearch: { type: Boolean, default: false },
    showContextMeta: { type: Boolean, default: false }
  },
  data() {
    return {
      loading: false,
      productOptions: []
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(value) {
        if (value !== undefined && value !== null && value !== "" && !this.hasProduct(value)) {
          this.fetchSelectedProduct(value)
        }
      }
    }
  },
  methods: {
    loadDefaultOptions() {
      if (this.productOptions.length === 0) {
        this.searchProduct("")
      }
    },
    searchProduct(query) {
      this.loading = true
      const keyword = (query || "").trim()
      if (this.keywordSearch) {
        const params = keyword
          ? { pageNum: 1, pageSize: 30, status: "0", keyword: keyword }
          : { pageNum: 1, pageSize: 30, status: "0" }
        listProduct(params).then(res => {
          this.productOptions = (res.rows || []).filter(product => this.isEnabledProduct(product))
        }).finally(() => {
          this.loading = false
        })
        return
      }
      const requests = keyword
        ? [
            listProduct({ pageNum: 1, pageSize: 20, productName: keyword }),
            listProduct({ pageNum: 1, pageSize: 20, productCode: keyword })
          ]
        : [listProduct({ pageNum: 1, pageSize: 20, status: "0" })]
      Promise.all(requests).then(results => {
        const products = []
        results.forEach(res => {
          ;(res.rows || []).forEach(product => {
            if (!this.hasProductIn(products, product.productId) && this.isEnabledProduct(product)) {
              products.push(product)
            }
          })
        })
        this.productOptions = products
      }).finally(() => {
        this.loading = false
      })
    },
    fetchSelectedProduct(productId) {
      getProduct(productId).then(res => {
        const product = res.data
        if (product && product.productId && !this.hasProduct(product.productId)) {
          this.productOptions = [product].concat(this.productOptions)
        }
      }).catch(() => {})
    },
    handleChange(productId) {
      const product = this.productOptions.find(item => String(item.productId) === String(productId))
      this.$emit("change", productId)
      this.$emit("selected", product || null)
    },
    handleClear() {
      this.$emit("selected", null)
    },
    productLabel(product) {
      const name = product.productName || product.productId
      return product.productCode ? name + "（" + product.productCode + "）" : String(name)
    },
    productMeta(product) {
      return [
        product.categoryFullPath || product.categoryName,
        product.spec,
        product.unit
      ].filter(item => item !== undefined && item !== null && String(item).trim() !== "").join(" / ") || "-"
    },
    displayValue(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    isEnabledProduct(product) {
      return product.status === undefined || product.status === null || product.status === "0"
    },
    hasProduct(productId) {
      return this.hasProductIn(this.productOptions, productId)
    },
    hasProductIn(products, productId) {
      return products.some(item => String(item.productId) === String(productId))
    }
  }
}
</script>

<style lang="scss" scoped>
.product-option {
  line-height: 1.4;
  padding: 4px 0;
}

.product-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.product-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-code {
  float: right;
  color: #8492a6;
  font-size: 12px;
}

.product-main .product-code {
  float: none;
  flex: 0 0 auto;
}

.product-meta {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
