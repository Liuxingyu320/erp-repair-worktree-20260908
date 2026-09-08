<template>
  <div class="app-container report-page">
    <inventory-page-hero
      title="报表中心"
      eyebrow="经营分析"
      description="汇总库存健康度与销售表现，让预警、收支和经营趋势一目了然。"
      scope-text="当前组织经营数据"
      icon="el-icon-data-analysis"
      tone="slate"
      :features="['库存预警', '销售表现', '经营汇总']"
    />
    <el-card shadow="never" class="search-card mb12">
      <el-form :model="queryParams" inline size="small" class="query-form">
        <el-form-item label="商品">
          <product-select
            v-model="queryParams.productId"
            placeholder="搜索商品名称/编码"
            width="220px"
            @change="handleProductChange"
          />
        </el-form-item>
        <el-form-item label="库存组织">
          <el-tag size="small">{{ currentDeptLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="dateRange"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="预警类型">
          <el-select v-model="queryParams.stockStatus" clearable placeholder="全部预警" style="width: 130px">
            <el-option label="低库存" value="low"/>
            <el-option label="缺货" value="empty"/>
            <el-option label="阈值未配置" value="unconfigured"/>
          </el-select>
        </el-form-item>
        <el-form-item class="query-actions">
          <el-button v-hasPermi="['inv:report:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="summaryLoading" class="metric-grid mb12">
      <div class="metric-card">
        <div class="metric-value">{{ displayValue(summary.stockItemCount) }}</div>
        <div class="metric-label">库存品项</div>
        <div class="metric-extra">不同计量单位不再直接相加</div>
      </div>
      <div v-if="canViewCostMetrics" class="metric-card">
        <div class="metric-value">{{ formatMoney(summary.totalStockCost) }}</div>
        <div class="metric-label">库存成本</div>
        <div class="metric-extra">库存成本按当前库存表统计</div>
      </div>
      <div class="metric-card">
        <div class="metric-value warning-value">{{ displayValue(summary.warningStockCount) }}</div>
        <div class="metric-label">已配置阈值的库存预警</div>
        <div class="metric-extra">缺货 {{ displayValue(summary.zeroStockCount) }} · 阈值未配置 {{ displayValue(summary.missingSafetyStockCount) }}</div>
      </div>
      <div v-if="canViewCostMetrics" class="metric-card">
        <div class="metric-value">{{ formatMoney(summary.purchaseAmount) }}</div>
        <div class="metric-label">已入库采购净额</div>
        <div class="metric-extra">实际收货并扣除已确认采购退货 · {{ displayValue(summary.purchaseOrderCount) }} 单</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ formatMoney(summary.salesAmount) }}</div>
        <div class="metric-label">净销售收入</div>
        <div class="metric-extra">实际出库并扣除已确认销售退货 · {{ displayValue(summary.salesOrderCount) }} 单</div>
      </div>
      <div v-if="canViewCostMetrics" class="metric-card">
        <div class="metric-value">{{ formatMoney(summary.salesCost) }}</div>
        <div class="metric-label">净销售成本</div>
        <div class="metric-extra">销售出库成本 - 销售退货冲回成本</div>
      </div>
      <div v-if="canViewCostMetrics" class="metric-card">
        <div class="metric-value" :class="marginClass(summary.grossMargin)">{{ formatMoney(summary.grossMargin) }}</div>
        <div class="metric-label">已实现毛利</div>
        <div class="metric-extra">净销售收入 - 净销售成本</div>
      </div>
    </div>

    <el-alert
      v-if="canViewCostMetrics && hasNegativeSalesCost"
      class="mb12"
      type="warning"
      :closable="false"
      show-icon
      title="净销售成本为负，请核对历史退货成本"
      description="所选期间的销售退货冲回成本高于销售出库成本。新确认的退货会按原销售出库成本冲回；历史单据请结合库存日志复核。"
    />

    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <span>库存预警与阈值配置</span>
        <el-button v-hasPermi="['inv:report:list']" type="text" size="mini" icon="el-icon-refresh" @click="getWarningList">刷新</el-button>
      </div>
      <el-table v-loading="warningLoading" :data="warningList" size="small">
        <el-table-column label="商品编码" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.productCode) }}</template>
        </el-table-column>
        <el-table-column label="商品名称" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.productName) }}</template>
        </el-table-column>
        <el-table-column label="商品分类" min-width="160" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.categoryFullPath || scope.row.categoryName) }}</template>
        </el-table-column>
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.spec) }}</template>
        </el-table-column>
        <el-table-column label="库存组织" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.warehouseName || scope.row.shopDeptName) }}</template>
        </el-table-column>
        <el-table-column label="当前库存" width="110" align="right">
          <template slot-scope="scope">{{ formatQuantity(scope.row.currentQuantity) }}</template>
        </el-table-column>
        <el-table-column label="可用库存" width="110" align="right">
          <template slot-scope="scope">
            <span :class="stockQuantityClass(scope.row)">{{ formatQuantity(availableQuantityValue(scope.row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="安全下限" width="100" align="right">
          <template slot-scope="scope">{{ hasSafetyThreshold(scope.row) ? formatQuantity(scope.row.safetyStockMin) : "未配置" }}</template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template slot-scope="scope">
            <span :class="hasSafetyThreshold(scope.row) ? 'text-danger' : ''">{{ hasSafetyThreshold(scope.row) ? formatQuantity(warningGap(scope.row)) : "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="warningTagType(scope.row)" size="mini">{{ stockStatusLabel(scope.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后入库" prop="lastInTime" width="160"/>
        <el-table-column label="最后出库" prop="lastOutTime" width="160"/>
      </el-table>
      <pagination v-show="warningTotal>0" :total="warningTotal" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getWarningList"/>
    </el-card>
  </div>
</template>

<script>
import { getReportSummary, listStockWarning } from "@/api/inventory/report"
import ProductSelect from "@/views/inventory/components/ProductSelect"
import { getSelectedDeptContext, getSelectedDeptId, getSelectedDeptName } from "@/utils/shopContext"

export default {
  name: "InvReport",
  components: { ProductSelect },
  data() {
    return {
      summaryLoading: false,
      warningLoading: false,
      dateRange: [],
      summary: {},
      warningList: [],
      warningTotal: 0,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        productId: undefined,
        stockStatus: undefined
      }
    }
  },
  computed: {
    currentDeptId() {
      return getSelectedDeptId()
    },
    currentDeptContext() {
      return getSelectedDeptContext()
    },
    currentDeptLabel() {
      const deptName = getSelectedDeptName() || "当前组织"
      const deptType = this.currentDeptContext.isWarehouse ? "仓库" : this.currentDeptContext.isStore ? "门店" : "组织"
      return deptName + "（" + deptType + "）"
    },
    canViewCostMetrics() {
      return this.$auth && this.$auth.hasPermi("inv:cost:view")
    },
    hasNegativeSalesCost() {
      return Number(this.summary.salesCost || 0) < 0
    }
  },
  created() {
    this.loadData()
  },
  methods: {
    loadData() {
      if (!this.ensureEntryContext()) return
      this.getSummary()
      this.getWarningList()
    },
    getSummary() {
      this.summaryLoading = true
      getReportSummary(this.buildQuery(false)).then(res => {
        this.summary = res.data || {}
      }).finally(() => {
        this.summaryLoading = false
      })
    },
    getWarningList() {
      if (!this.ensureEntryContext()) return
      this.warningLoading = true
      listStockWarning(this.buildQuery(true)).then(res => {
        this.warningList = res.rows || []
        this.warningTotal = res.total || 0
      }).finally(() => {
        this.warningLoading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.dateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        productId: undefined,
        stockStatus: undefined
      }
      this.loadData()
    },
    handleProductChange() {
      this.queryParams.pageNum = 1
    },
    buildQuery(withPage) {
      const query = Object.assign({}, this.queryParams)
      if (!withPage) {
        delete query.pageNum
        delete query.pageSize
      }
      return this.addDateRange(query, this.dateRange)
    },
    ensureEntryContext() {
      if (!this.currentDeptId) {
        this.$message.warning("请先选择店铺或仓库")
        return false
      }
      return true
    },
    availableQuantityValue(row) {
      return this.hasValue(row.availableQuantity) ? row.availableQuantity : row.currentQuantity
    },
    safetyStockMinValue(row) {
      return this.hasValue(row.safetyStockMin) ? row.safetyStockMin : 0
    },
    hasSafetyThreshold(row) {
      return Number(this.safetyStockMinValue(row) || 0) > 0
    },
    warningGap(row) {
      const available = Number(this.availableQuantityValue(row) || 0)
      const safety = Number(this.safetyStockMinValue(row) || 0)
      return Math.max(safety - available, 0)
    },
    stockStatusLabel(row) {
      if (!this.hasSafetyThreshold(row)) return "阈值未配置"
      const available = Number(this.availableQuantityValue(row) || 0)
      return available <= 0 ? "缺货" : "低库存"
    },
    warningTagType(row) {
      if (!this.hasSafetyThreshold(row)) return "info"
      return Number(this.availableQuantityValue(row) || 0) <= 0 ? "danger" : "warning"
    },
    stockQuantityClass(row) {
      if (!this.hasSafetyThreshold(row)) return ""
      return Number(this.availableQuantityValue(row) || 0) <= 0 ? "text-danger" : "text-warning"
    },
    marginClass(value) {
      const number = Number(value || 0)
      return number < 0 ? "text-danger" : "text-success"
    },
    formatQuantity(value) {
      if (!this.hasValue(value)) return "0"
      const number = Number(value)
      if (isNaN(number)) return value
      return number.toLocaleString("zh-CN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
    },
    formatMoney(value) {
      if (!this.hasValue(value)) return "0.00"
      const number = Number(value)
      if (isNaN(number)) return value
      return number.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    formatPercent(value) {
      if (!this.hasValue(value)) return "0.00%"
      const number = Number(value)
      if (isNaN(number)) return value
      return (number * 100).toFixed(2) + "%"
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

<style scoped>
.report-page {
  background: #f5f7fb;
}

::v-deep .search-card .el-card__body {
  padding: 18px 22px 6px;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
}

::v-deep .query-form .el-form-item {
  margin-right: 14px;
  margin-bottom: 12px;
}

.query-actions {
  white-space: nowrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 86px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 8px;
}

.metric-value {
  color: #172033;
  font-size: 22px;
  font-weight: 700;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.metric-label {
  margin-top: 4px;
  color: #536273;
  font-size: 13px;
  font-weight: 600;
}

.metric-extra {
  margin-top: 4px;
  color: #8a96a8;
  font-size: 12px;
  line-height: 16px;
}

.warning-value {
  color: #d97706;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.text-danger {
  color: #f56c6c;
  font-weight: 700;
}

.text-warning {
  color: #d97706;
  font-weight: 700;
}

.text-success {
  color: #16a34a;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(150px, 1fr));
  }
}

@media (max-width: 700px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
