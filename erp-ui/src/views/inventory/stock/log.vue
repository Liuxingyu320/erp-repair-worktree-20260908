<template>
  <div class="app-container">
    <el-alert
      :title="scopeAlertTitle"
      :description="scopeAlertDescription"
      :type="fromVisibleScope ? 'warning' : 'info'"
      show-icon
      :closable="false"
      class="mb12"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="物料类型">
          <el-select v-model="queryParams.itemType" clearable placeholder="全部类型" style="width:120px" @change="handleItemTypeQueryChange">
            <el-option v-for="type in allowedItemTypes" :key="type" :label="itemTypeLabel(type)" :value="type"/>
          </el-select>
        </el-form-item>
        <el-form-item label="物料">
          <inventory-item-select
            v-model="queryParams.itemId"
            :item-type="queryParams.itemType || 'product'"
            :disabled="!queryParams.itemType"
            placeholder="搜索物料名称/编码"
            width="220px"
            @change="handleProductQueryChange"
          />
        </el-form-item>
        <el-form-item label="批次">
          <el-input v-model="queryParams.batchNo" clearable placeholder="批次号" style="width:140px" @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="效期">
          <el-date-picker
            v-model="queryParams.expiryDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="选择日期"
            style="width:140px"
          />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input v-model="queryParams.serialNo" clearable placeholder="序列号" style="width:150px" @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="库位">
          <el-input v-model="queryParams.locationCode" clearable placeholder="库位编码" style="width:140px" @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="库位名">
          <el-input v-model="queryParams.locationName" clearable placeholder="库位名称" style="width:140px" @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="变动类型">
          <el-select v-model="queryParams.movementType" clearable placeholder="全部类型">
            <el-option
              v-for="item in movementTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:stock:log']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:stock:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small">
        <el-table-column label="物料编码" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ productCode(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ productName(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="库存组织" min-width="150" show-overflow-tooltip>
          <template slot-scope="scope">{{ inventoryDeptName(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="批次号" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.batchNo) }}</template>
        </el-table-column>
        <el-table-column label="效期" width="110" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.expiryDate) }}</template>
        </el-table-column>
        <el-table-column label="序列号" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.serialNo) }}</template>
        </el-table-column>
        <el-table-column label="库位" min-width="150" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockLocation(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="变动类型" prop="movementType" width="140">
          <template slot-scope="scope">
            <el-tag :type="movementTypeTagType(scope.row.movementType)" size="mini">{{ movementTypeLabel(scope.row.movementType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务单号" prop="businessNo" width="180"/>
        <el-table-column label="变动数量" width="100" align="right">
          <template slot-scope="scope">
            <span :class="changeQuantityClass(scope.row.changeQuantity)">{{ formatQuantity(scope.row.changeQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" width="100" align="right">
          <template slot-scope="scope">{{ formatQuantity(scope.row.beforeQuantity) }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="100" align="right">
          <template slot-scope="scope">{{ formatQuantity(scope.row.afterQuantity) }}</template>
        </el-table-column>
        <el-table-column label="成本价" width="100" align="right">
          <template slot-scope="scope">{{ formatMoney(scope.row.costPrice) }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160"/>
        <el-table-column label="操作人" prop="createBy" width="110" show-overflow-tooltip/>
        <el-table-column label="时间" prop="createTime" width="160"/>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>
  </div>
</template>

<script>
import { listStockLog } from "@/api/inventory/stock"
import { getProduct } from "@/api/inventory/product"
import { listShopTree } from "@/api/system/dept"
import InventoryItemSelect from "@/views/inventory/components/InventoryItemSelect"
import { getSelectedDeptName } from "@/utils/shopContext"

export default {
  name: "InvStockLog",
  components: { InventoryItemSelect },
  data() {
    return {
      loading: false,
      total: 0,
      list: [],
      productMap: {},
      deptMap: {},
      allowedItemTypes: ["product", "oe", "gift"],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        itemType: undefined,
        itemId: undefined,
        productId: undefined,
        batchNo: undefined,
        expiryDate: undefined,
        serialNo: undefined,
        locationCode: undefined,
        locationName: undefined,
        movementType: undefined
      },
      movementTypeOptions: [
        { value: "purchase_in", label: "采购入库", tagType: "success" },
        { value: "purchase_return_out", label: "采购退货出库", tagType: "danger" },
        { value: "sales_out", label: "销售出库", tagType: "warning" },
        { value: "sales_return_in", label: "销售退货入库", tagType: "success" },
        { value: "transfer_in", label: "调拨入库", tagType: "success" },
        { value: "transfer_out", label: "调拨出库", tagType: "warning" },
        { value: "stock_check_profit", label: "盘盈入库", tagType: "success" },
        { value: "stock_check_loss", label: "盘亏出库", tagType: "danger" },
        { value: "adjustment", label: "库存调整", tagType: "info" }
      ],
      movementTypeAliases: {
        outbound_out: { label: "出库", tagType: "warning" },
        purchase_return: { label: "采购退货", tagType: "danger" },
        sales_return: { label: "销售退货", tagType: "success" },
        check: { label: "库存盘点", tagType: "info" }
      }
    }
  },
  created() {
    this.loadDeptMap()
    this.getList()
  },
  computed: {
    fromVisibleScope() {
      return this.$route.query && this.$route.query.fromScope === "visible"
    },
    scopeAlertTitle() {
      return this.fromVisibleScope ? "日志范围已切回当前组织" : "当前组织库存流水"
    },
    scopeAlertDescription() {
      const deptName = getSelectedDeptName() || "当前组织"
      return this.fromVisibleScope
        ? "上一页为可见/管理库存范围；本页仅显示“" + deptName + "”的库存流水。"
        : "本页仅显示“" + deptName + "”的库存流水。"
    }
  },
  methods: {
    itemTypeLabel(type) {
      return { product: "商品", oe: "器皿", gift: "礼盒" }[type] || "其他物料"
    },
    getList() {
      this.loading = true
      listStockLog(this.queryParams).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        return this.hydrateProducts(this.list)
      }).finally(() => { this.loading = false })
    },
    handleProductQueryChange() {
      this.queryParams.pageNum = 1
    },
    handleItemTypeQueryChange() {
      this.queryParams.itemId = undefined
      this.queryParams.productId = undefined
      this.queryParams.pageNum = 1
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize || 10,
        itemType: undefined,
        itemId: undefined,
        productId: undefined,
        batchNo: undefined,
        expiryDate: undefined,
        serialNo: undefined,
        locationCode: undefined,
        locationName: undefined,
        movementType: undefined
      }
      this.getList()
    },
    handleExport() {
      this.$modal.confirm("确认导出当前查询条件下的变动日志？").then(() => {
        this.download("inventory/stock/log/export", { ...this.queryParams }, this.exportFileName("库存变动日志"))
      })
    },
    loadDeptMap() {
      listShopTree().then(res => {
        const map = {}
        this.flattenDeptList(res.data || res.rows || []).forEach(dept => {
          if (dept.deptId) {
            map[String(dept.deptId)] = dept.deptName
          }
        })
        this.deptMap = map
      }).catch(() => {})
    },
    hydrateProducts(rows) {
      const productIds = rows
        .filter(row => (row.itemType || "product") === "product")
        .map(row => row.productId)
        .filter(value => this.hasValue(value) && !this.productMap[String(value)])
      const uniqueProductIds = Array.from(new Set(productIds.map(value => String(value))))
      if (uniqueProductIds.length === 0) return Promise.resolve()
      return Promise.all(uniqueProductIds.map(productId => {
        return getProduct(productId).then(res => res.data).catch(() => null)
      })).then(products => {
        const nextProductMap = Object.assign({}, this.productMap)
        products.forEach(product => {
          if (product && product.productId) {
            nextProductMap[String(product.productId)] = product
          }
        })
        this.productMap = nextProductMap
      })
    },
    flattenDeptList(depts) {
      const result = []
      const visit = list => {
        ;(list || []).forEach(item => {
          result.push(item)
          if (item.children && item.children.length) {
            visit(item.children)
          }
        })
      }
      visit(depts)
      return result
    },
    resolveProduct(row) {
      if (row.product) return row.product
      return this.productMap[String(row.productId)] || {}
    },
    productName(row) {
      const product = this.resolveProduct(row)
      return this.displayValue(row.itemName || row.productName || product.productName)
    },
    productCode(row) {
      const product = this.resolveProduct(row)
      return this.displayValue(row.itemCode || row.productCode || product.productCode)
    },
    inventoryDeptName(row) {
      return this.displayValue(row.warehouseName || row.warehouseDeptName || row.shopName || row.shopDeptName || row.deptName || this.deptMap[String(row.warehouseId || row.shopDeptId)])
    },
    stockLocation(row) {
      const locationCode = row.locationCode
      const locationName = row.locationName
      if (this.hasValue(locationCode) && this.hasValue(locationName)) {
        return locationCode + " / " + locationName
      }
      return this.displayValue(locationCode || locationName)
    },
    movementTypeLabel(type) {
      return this.movementTypeOption(type).label
    },
    movementTypeTagType(type) {
      return this.movementTypeOption(type).tagType
    },
    movementTypeOption(type) {
      const matched = this.movementTypeOptions.find(item => item.value === type)
      if (matched) return matched
      if (this.movementTypeAliases[type]) return this.movementTypeAliases[type]
      return { label: this.hasValue(type) ? "其他库存变动" : "-", tagType: "info" }
    },
    changeQuantityClass(value) {
      const quantity = this.toNumber(value)
      if (quantity === null) return ""
      return quantity > 0 ? "text-success" : quantity < 0 ? "text-danger" : ""
    },
    formatQuantity(value) {
      if (!this.hasValue(value)) return "-"
      const number = Number(value)
      if (isNaN(number)) return value
      return number.toFixed(2).replace(/\.?0+$/, "")
    },
    formatMoney(value) {
      if (!this.hasValue(value)) return "-"
      const number = Number(value)
      if (isNaN(number)) return value
      return number.toFixed(2)
    },
    toNumber(value) {
      if (!this.hasValue(value)) return null
      const number = Number(value)
      return isNaN(number) ? null : number
    },
    displayValue(value) {
      return this.hasValue(value) ? value : "-"
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ""
    }
  }
}
</script>
<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.text-success { color: #67C23A; font-weight: 600 }
.text-danger { color: #F56C6C; font-weight: 600 }
</style>
