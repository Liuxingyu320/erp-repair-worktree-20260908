<template>
  <el-select
    :value="value"
    filterable
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :size="size"
    :loading="loading"
    :style="{ width: width }"
    @focus="loadWarehouses"
    @input="$emit('input', $event)"
    @change="handleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="warehouse in filteredWarehouses"
      :key="warehouse.deptId"
      :label="warehouseLabel(warehouse)"
      :value="warehouse.deptId"
    >
      <span>{{ displayValue(warehouse.deptName) }}</span>
    </el-option>
  </el-select>
</template>

<script>
import { listWarehouseDept } from "@/api/system/dept"

export default {
  name: "InventoryWarehouseSelect",
  props: {
    value: [Number, String],
    placeholder: { type: String, default: "请选择仓库" },
    size: { type: String, default: "small" },
    width: { type: String, default: "220px" },
    clearable: { type: Boolean, default: true },
    disabled: { type: Boolean, default: false },
    shopDeptId: [Number, String],
    scopeDeptId: [Number, String],
    purpose: { type: String, default: "" },
    autoload: { type: Boolean, default: false },
    allowedWarehouseIds: { type: Array, default: null }
  },
  data() {
    return {
      loaded: false,
      loading: false,
      warehouses: [],
      requestSequence: 0
    }
  },
  computed: {
    filteredWarehouses() {
      const candidates = this.allowedWarehouseIds === null ? this.warehouses : this.warehouses.filter(item =>
        this.allowedWarehouseIds.some(id => String(id) === String(item.deptId)))
      if (!this.shopDeptId) {
        return candidates
      }
      const shopDeptId = String(this.shopDeptId)
      return candidates.filter(item => {
        return String(item.deptId) === shopDeptId ||
          String(item.parentId) === shopDeptId ||
          String(item.ancestors || "").split(",").includes(shopDeptId)
      })
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(value) {
        if (value !== undefined && value !== null && value !== "" && !this.hasWarehouse(value)) {
          this.loadWarehouses()
        }
      }
    },
    purpose() {
      this.reloadWarehouses()
    },
    scopeDeptId() {
      this.reloadWarehouses()
    },
    shopDeptId() {
      this.reloadWarehouses()
    }
  },
  mounted() {
    if (this.autoload) {
      this.loadWarehouses()
    }
  },
  beforeDestroy() { this.requestSequence += 1 },
  methods: {
    reloadWarehouses() {
      this.requestSequence += 1
      this.loaded = false
      this.loading = false
      this.warehouses = []
      this._warehousePromise = null
      if (this.autoload || (this.value !== undefined && this.value !== null && this.value !== "")) {
        return this.loadWarehouses()
      }
      return Promise.resolve([])
    },
    loadWarehouses() {
      if (this.loaded) return Promise.resolve(this.filteredWarehouses)
      if (this.loading) return this._warehousePromise
      const sequence = ++this.requestSequence
      const query = this.warehouseQuery()
      const current = () => sequence === this.requestSequence && JSON.stringify(query) === JSON.stringify(this.warehouseQuery())
      this.loading = true
      this._warehousePromise = listWarehouseDept(query).then(res => {
        if (!current()) return []
        this.warehouses = this.flattenDeptList(res.data || res.rows || res || [])
          .filter(item => item && item.deptType === "WAREHOUSE" && this.isEnabledWarehouse(item))
        this.loaded = true
        this.$emit("loaded", this.filteredWarehouses)
        return this.filteredWarehouses
      }).catch(error => {
        if (current()) this.$emit("load-error", error)
        return []
      }).finally(() => {
        if (current()) this.loading = false
      })
      return this._warehousePromise
    },
    warehouseQuery() {
      const query = {}
      const scopeDeptId = this.scopeDeptId || this.shopDeptId
      if (this.purpose) {
        query.purpose = this.purpose
      }
      if (scopeDeptId) {
        query.scopeDeptId = scopeDeptId
      }
      return query
    },
    flattenDeptList(list) {
      const result = []
      ;(list || []).forEach(item => {
        result.push(item)
        if (item.children && item.children.length) {
          result.push(...this.flattenDeptList(item.children))
        }
      })
      return result
    },
    handleChange(warehouseId) {
      const warehouse = this.warehouses.find(item => String(item.deptId) === String(warehouseId))
      this.$emit("change", warehouseId)
      this.$emit("selected", warehouse || null)
    },
    handleClear() {
      this.$emit("selected", null)
    },
    warehouseLabel(warehouse) {
      return warehouse.deptName ? warehouse.deptName : "-"
    },
    displayValue(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    isEnabledWarehouse(warehouse) {
      return warehouse.status === undefined || warehouse.status === null || warehouse.status === "0"
    },
    hasWarehouse(warehouseId) {
      return this.warehouses.some(item => String(item.deptId) === String(warehouseId))
    }
  }
}
</script>
