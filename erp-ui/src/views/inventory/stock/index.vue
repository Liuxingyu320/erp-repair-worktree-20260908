<template>
  <div class="app-container stock-page">
    <inventory-page-hero
      title="库存管理"
      eyebrow="库存中心"
      description="统一查看现存量、库存预警与变动轨迹，快速掌握当前组织的库存健康度。"
      scope-text="库存、预警与流水"
      icon="el-icon-box"
      tone="blue"
      :features="['现存量', '库存预警', '变动日志']"
    />
    <div class="stock-layout" :class="{ 'is-category-collapsed': categoryCollapsed }">
      <aside class="category-panel">
        <el-card shadow="never" class="category-card">
          <div slot="header" class="card-header">
            <span>物料分类</span>
            <div class="card-actions">
              <el-tooltip content="刷新分类" placement="top">
                <el-button v-hasPermi="['inv:category:list', 'inv:category:tree']" type="text" size="mini" icon="el-icon-refresh" @click="loadCategories">刷新</el-button>
              </el-tooltip>
              <el-tooltip content="收起分类" placement="top">
                <el-button type="text" size="mini" icon="el-icon-d-arrow-left" @click="toggleCategoryPanel(true)">收起</el-button>
              </el-tooltip>
            </div>
          </div>
          <el-input
            v-model="categoryKeyword"
            size="small"
            clearable
            prefix-icon="el-icon-search"
            placeholder="搜索分类"
            class="category-search"
          />
          <el-tree
            ref="categoryTree"
            v-loading="categoryLoading"
            class="category-tree"
            :data="categoryTreeData"
            :props="categoryProps"
            node-key="categoryId"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :filter-node-method="filterCategoryNode"
            @node-click="handleCategoryClick"
          >
            <span class="tree-node" slot-scope="{ data }">
              <span class="tree-node-marker"></span>
              <span class="tree-node-label">{{ data.categoryName }}</span>
            </span>
          </el-tree>
          <div class="category-summary">
            <div v-if="categoryLoadError" class="category-error">{{ categoryLoadError }}</div>
            <div>当前分类：{{ selectedCategoryName }}</div>
            <div>筛选到 {{ total }} 条库存</div>
          </div>
        </el-card>
      </aside>

      <main class="stock-main">
        <div class="collapsed-category-action">
          <el-button size="mini" icon="el-icon-d-arrow-right" @click="toggleCategoryPanel(false)">展开分类</el-button>
        </div>

    <el-card shadow="never" class="search-card mb12">
      <el-form :model="queryParams" inline size="small" class="query-form">
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
          <el-input v-model="queryParams.batchNo" clearable placeholder="批次号" style="width:140px" @keyup.enter.native="handleQuery"/>
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
          <el-input v-model="queryParams.serialNo" clearable placeholder="序列号" style="width:150px" @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="库位">
          <el-input v-model="queryParams.locationCode" clearable placeholder="库位编码" style="width:140px" @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="库位名">
          <el-input v-model="queryParams.locationName" clearable placeholder="库位名称" style="width:140px" @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="库存组织">
          <el-tag size="small">{{ currentDeptLabel }}</el-tag>
        </el-form-item>
        <el-form-item v-if="showScopeToggle" label="查看范围">
          <el-radio-group v-model="queryParams.ownOnly" size="mini" @change="handleScopeChange">
            <el-radio-button :label="true">{{ currentScopeLabel }}</el-radio-button>
            <el-radio-button :label="false">{{ managedScopeLabel }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="showManagedStoreFilter" label="店铺">
          <el-select
            v-model="queryParams.shopDeptId"
            filterable
            placeholder="全部管理门店"
            style="width:180px"
            @change="handleStoreFilterChange"
          >
            <el-option label="全部管理门店" :value="allStoreOptionValue" />
            <el-option
              v-for="dept in visibleStoreOptions"
              :key="dept.deptId"
              :label="dept.deptName"
              :value="dept.deptId"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="showWarehouseStoreFilter" label="店铺">
          <el-select
            v-model="queryParams.shopDeptId"
            clearable
            filterable
            placeholder="全部可见店铺"
            style="width:180px"
            @change="handleStoreFilterChange"
          >
            <el-option
              v-for="dept in visibleStoreOptions"
              :key="dept.deptId"
              :label="dept.deptName"
              :value="dept.deptId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库存状态">
          <el-select v-model="queryParams.stockStatus" clearable placeholder="全部状态" style="width:140px" @change="handleStatusChange">
            <el-option label="正常" value="normal"/>
            <el-option label="低库存" value="low"/>
            <el-option label="缺货" value="empty"/>
          </el-select>
        </el-form-item>
        <el-form-item class="query-actions">
          <el-button v-hasPermi="['inv:stock:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-tooltip :content="canAdjustCurrentScope ? '调整当前组织库存' : '可见/管理范围只读，请先切回当前组织'" placement="top">
            <span>
              <el-button v-hasPermi="['inv:stock:adjust']" size="mini" icon="el-icon-edit" :disabled="!canAdjustCurrentScope" @click="openAdjust()">库存调整</el-button>
            </span>
          </el-tooltip>
          <el-button v-hasPermi="['inv:stock:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
          <el-button v-hasPermi="['inv:stock:log']" size="mini" icon="el-icon-document" @click="openStockLog">变动日志</el-button>
        </el-form-item>
      </el-form>
      <div class="metric-row">
        <div class="metric-card">
          <div class="metric-value">{{ displayValue(summary.stockItemCount) }}</div>
          <div class="metric-label">{{ stockMetricTitle }}</div>
          <div class="metric-extra">{{ stockMetricExtra }}；不同单位不直接相加</div>
        </div>
        <div class="metric-card">
          <div class="metric-value">{{ displayValue(summary.zeroStockCount) }}</div>
          <div class="metric-label">缺货品项</div>
          <div class="metric-extra">请按商品单位查看表格中的实际数量</div>
        </div>
        <div class="metric-card">
          <div class="metric-value warning-value">{{ displayValue(summary.warningCount) }}</div>
          <div class="metric-label">低库存预警</div>
          <div class="metric-extra">缺货 {{ displayValue(summary.zeroStockCount) }} / 低库存 {{ displayValue(summary.lowStockCount) }}</div>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="stockContextAlert"
      :title="stockContextAlert.title"
      :description="stockContextAlert.description"
      :type="stockContextAlert.type"
      show-icon
      :closable="false"
      class="context-alert mb12"
    />

    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="table-title">
          <span>{{ selectedCategoryName }}</span>
        </div>
        <el-button v-hasPermi="['inv:stock:list']" type="text" size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
      </div>
      <div class="stock-tabs">
        <el-radio-group v-model="queryParams.stockScope" size="mini" @change="handleScopeChange">
          <el-radio-button label="">全部库存</el-radio-button>
          <el-radio-button label="warning">预警库存</el-radio-button>
        </el-radio-group>
        <span class="stock-total">共 {{ total }} 条库存记录</span>
      </div>
      <el-table
        ref="stockTable"
        v-accessible-table="'库存列表'"
        v-loading="loading || summaryLoading"
        :data="list"
        :aria-busy="loading || summaryLoading ? 'true' : 'false'"
        element-loading-text="正在加载库存数据"
        size="small"
        class="stock-table"
        @row-click="openStockDetail"
      >
        <template slot="empty">
          <data-state
            :type="stockLoadError ? 'error' : 'empty'"
            :title="stockLoadError ? '库存数据加载失败' : '当前暂无库存记录'"
            :description="stockLoadError || stockEmptyText"
          >
            <el-button v-if="stockLoadError" type="primary" size="small" icon="el-icon-refresh" @click="getList">重试加载</el-button>
            <template v-else>
              <el-button size="small" @click="resetQuery">重置筛选</el-button>
              <el-button type="primary" size="small" icon="el-icon-refresh" @click="getList">刷新库存</el-button>
            </template>
          </data-state>
        </template>
        <el-table-column label="物料编码" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ productCode(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ productName(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="物料分类" min-width="160" show-overflow-tooltip>
          <template slot-scope="scope">{{ productCategory(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="90" show-overflow-tooltip>
          <template slot-scope="scope">{{ productGrade(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="规格" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ productSpec(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="当前库存" width="120" align="right">
          <template slot-scope="scope">
            <span :class="stockQuantityClass(scope.row)">{{ formatQuantity(currentQuantityValue(scope.row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="stockStatusTagType(scope.row)" size="mini">{{ stockStatusLabel(scope.row) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>
      </main>
    </div>

    <el-dialog title="库存调整" :visible.sync="adjustOpen" width="560px" append-to-body :close-on-click-modal="false">
      <el-form ref="adjustRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
        <el-form-item label="物料类型" prop="itemType">
          <el-radio-group v-model="adjustForm.itemType" size="small" @change="onAdjustItemTypeChange">
            <el-radio-button v-for="type in allowedItemTypes" :key="type" :label="type">{{ itemTypeLabel(type) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物料" prop="itemId">
          <inventory-item-select
            v-model="adjustForm.itemId"
            :item-type="adjustForm.itemType || 'product'"
            :disabled="!adjustForm.itemType"
            placeholder="搜索物料名称/编码"
            width="100%"
            @selected="onAdjustItemSelected"
          />
        </el-form-item>
        <el-form-item label="库存组织">
          <span>{{ adjustInventoryDeptName }}</span>
          <span class="muted-text">{{ adjustInventoryDeptHint }}</span>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="adjustForm.batchNo" maxlength="64" clearable placeholder="请输入批次号"/>
        </el-form-item>
        <el-form-item label="效期">
          <el-date-picker
            v-model="adjustForm.expiryDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="选择效期"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input v-model="adjustForm.serialNo" maxlength="64" clearable placeholder="请输入序列号"/>
        </el-form-item>
        <el-form-item label="库位编码">
          <el-input v-model="adjustForm.locationCode" maxlength="64" clearable placeholder="请输入库位编码"/>
        </el-form-item>
        <el-form-item label="库位名称">
          <el-input v-model="adjustForm.locationName" maxlength="128" clearable placeholder="请输入库位名称"/>
        </el-form-item>
        <el-form-item label="调整前数量">
          <span class="quantity-preview">{{ formatQuantity(adjustBeforeQuantity) }}</span>
        </el-form-item>
        <el-form-item label="调整数量" prop="adjustQuantity">
          <el-input-number v-model="adjustForm.adjustQuantity" :precision="2" style="width:100%"/>
          <div style="font-size:12px;color:#909399;margin-top:4px">正数为入库，负数为出库</div>
        </el-form-item>
        <el-form-item label="调整后数量">
          <span class="quantity-preview" :class="adjustAfterQuantityClass">{{ formatQuantity(adjustAfterQuantity) }}</span>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="请输入调整原因"/>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="adjustOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:stock:adjust']" type="primary" :loading="adjustLoading" @click="doAdjust">确认调整</el-button>
      </div>
    </el-dialog>

    <el-dialog title="库存详情" :visible.sync="detailOpen" width="760px" append-to-body>
      <div v-if="detailStock" class="stock-detail">
        <div class="stock-detail-image">
          <el-image
            v-if="productImageUrl(detailStock)"
            :src="productImageUrl(detailStock)"
            fit="cover"
            :preview-src-list="[productImageUrl(detailStock)]"
          />
          <div v-else class="stock-image-empty">暂无图片</div>
        </div>
        <el-descriptions :column="2" border size="small" class="stock-detail-info">
          <el-descriptions-item label="物料编码">{{ productCode(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="物料名称">{{ productName(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="物料分类">{{ productCategory(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ productGrade(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ productSpec(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="库存组织">{{ inventoryDeptName(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ displayValue(detailStock.batchNo) }}</el-descriptions-item>
          <el-descriptions-item label="效期">{{ displayValue(detailStock.expiryDate) }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ displayValue(detailStock.serialNo) }}</el-descriptions-item>
          <el-descriptions-item label="库位">{{ stockLocation(detailStock) }}</el-descriptions-item>
          <el-descriptions-item label="当前库存">{{ formatQuantity(currentQuantityValue(detailStock)) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="stockStatusTagType(detailStock)" size="mini">{{ stockStatusLabel(detailStock) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最后入库">{{ displayValue(detailStock.lastInTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后出库">{{ displayValue(detailStock.lastOutTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listStock, getStockSummary, adjustStock } from "@/api/inventory/stock"
import { getProduct } from "@/api/inventory/product"
import { categoryTree } from "@/api/inventory/category"
import { listShopTree, listVisibleStoreDept } from "@/api/system/dept"
import { getSelectedDeptContext, getSelectedDeptId, getSelectedDeptName, isSelectedStore, isSelectedWarehouse } from "@/utils/shopContext"
import InventoryItemSelect from "@/views/inventory/components/InventoryItemSelect"

export default {
  name: "InvStock",
  components: { InventoryItemSelect },
  data() {
    return {
      loading: false,
      summaryLoading: false,
      categoryLoading: false,
      adjustLoading: false,
      categoryCollapsed: false,
      categoryKeyword: "",
      categoryLoadError: "",
      stockLoadError: "",
      categoryTreeData: [{ categoryId: 0, categoryName: "全部库存", categoryFullPath: "全部库存", children: [] }],
      categoryOptions: [],
      categoryProps: { children: "children", label: "categoryName" },
      selectedCategory: null,
      total: 0,
      list: [],
      summary: {},
      adjustOpen: false,
      detailOpen: false,
      detailStock: null,
      productMap: {},
      deptMap: {},
      storeOptions: [],
      allStoreOptionValue: 0,
      adjustStockRow: null,
      allowedItemTypes: ["product", "oe", "gift"],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        itemType: undefined,
        itemId: undefined,
        productId: undefined,
        categoryId: undefined,
        shopDeptId: undefined,
        batchNo: undefined,
        expiryDate: undefined,
        serialNo: undefined,
        locationCode: undefined,
        locationName: undefined,
        stockScope: "",
        stockStatus: undefined,
        ownOnly: true
      },
      adjustForm: {
        itemType: null,
        itemId: undefined,
        productId: undefined,
        shopDeptId: undefined,
        warehouseId: undefined,
        batchNo: "",
        expiryDate: undefined,
        serialNo: "",
        locationCode: "",
        locationName: "",
        adjustQuantity: 0,
        reason: ""
      },
      adjustRules: {
        itemType: [{ required: true, message: "请选择物料类型", trigger: "change" }],
        itemId: [{ required: true, message: "请选择物料", trigger: "change" }],
        adjustQuantity: [
          { required: true, message: "请输入调整数量", trigger: "change" },
          { validator: (rule, value, callback) => {
            if (Number(value) === 0) {
              callback(new Error("调整数量不能为0"))
            } else {
              callback()
            }
          }, trigger: "change" }
        ],
        reason: [
          { required: true, message: "请输入调整原因", trigger: "blur" },
          { validator: (rule, value, callback) => {
            if (!value || !String(value).trim()) {
              callback(new Error("请输入调整原因"))
            } else {
              callback()
            }
          }, trigger: "blur" }
        ]
      }
    }
  },
  computed: {
    currentDeptId() {
      return getSelectedDeptId()
    },
    currentDeptName() {
      return getSelectedDeptName() || "当前组织"
    },
    currentDeptContext() {
      return getSelectedDeptContext()
    },
    isWarehouseContext() {
      return isSelectedWarehouse()
    },
    isStoreContext() {
      return isSelectedStore()
    },
    stockEntry() {
      if (this.$route.query && this.$route.query.stockEntry) {
        return this.$route.query.stockEntry
      }
      return this.$route.path.indexOf("/cangku/") === 0 ? "warehouse" : "store"
    },
    isWarehouseStockEntry() {
      return this.stockEntry === "warehouse"
    },
    isStoreStockEntry() {
      return this.stockEntry !== "warehouse"
    },
    isEntryContextValid() {
      if (this.isWarehouseStockEntry) {
        return this.isWarehouseContext
      }
      return this.isStoreContext
    },
    showWarehouseStoreFilter() {
      return this.isWarehouseStockEntry && this.isWarehouseContext && this.queryParams.ownOnly === false
    },
    showStoreStockFilter() {
      return this.isStoreStockEntry && this.isStoreContext
    },
    showManagedStoreFilter() {
      return this.showStoreStockFilter && this.queryParams.ownOnly === false
    },
    showScopeToggle() {
      return (this.isWarehouseStockEntry && this.isWarehouseContext) || this.showStoreStockFilter
    },
    canAdjustCurrentScope() {
      return this.isEntryContextValid && this.queryParams.ownOnly !== false
    },
    currentScopeLabel() {
      return this.isWarehouseStockEntry ? "当前仓库" : "当前门店"
    },
    managedScopeLabel() {
      return this.isWarehouseStockEntry ? "可见库存" : "管理门店"
    },
    visibleStoreOptions() {
      return this.storeOptions
    },
    selectedCategoryName() {
      return this.selectedCategory && this.selectedCategory.categoryId ? this.selectedCategory.categoryFullPath : "全部库存"
    },
    selectedStoreFilterName() {
      if (this.showStoreStockFilter) {
        if (this.queryParams.ownOnly !== false) return this.currentDeptName
        if (Number(this.queryParams.shopDeptId) === this.allStoreOptionValue) return "全部管理门店"
        const store = this.visibleStoreOptions.find(item => String(item.deptId) === String(this.queryParams.shopDeptId))
        return store ? store.deptName : "管理门店"
      }
      if (!this.hasValue(this.queryParams.shopDeptId)) return ""
      const store = this.visibleStoreOptions.find(item => String(item.deptId) === String(this.queryParams.shopDeptId))
      return store ? store.deptName : this.deptMap[String(this.queryParams.shopDeptId)] || ""
    },
    entryContextMessage() {
      return this.isWarehouseStockEntry ? "请选择仓库后查看仓库库存管理" : "请选择门店后查看进销存库存"
    },
    stockContextAlert() {
      if (!this.isEntryContextValid) {
        return {
          type: "warning",
          title: "当前组织和库存入口不匹配",
          description: this.entryContextMessage + "。当前选择：" + this.currentDeptLabel + "。"
        }
      }
      if (this.isWarehouseStockEntry && this.isWarehouseContext && this.queryParams.ownOnly === false) {
        return {
          type: "info",
          title: "正在查看可见库存",
          description: "这里包含当前仓库可见的门店库存，只读查看；库存调整请切回当前仓库范围。"
        }
      }
      return null
    },
    stockEmptyText() {
      if (!this.isEntryContextValid) {
        return this.entryContextMessage
      }
      if (this.isWarehouseStockEntry) {
        return "当前仓库暂无可用库存。请先完成采购入库、库存调整，或切换到有库存的仓库。"
      }
      return "当前门店暂无库存。请先发起要货、确认收货，或进行库存调整。"
    },
    currentDeptLabel() {
      const prefix = this.currentDeptContext.isWarehouse ? "仓库" : this.currentDeptContext.isStore ? "门店" : "组织"
      return prefix + "：" + this.currentDeptName
    },
    stockMetricTitle() {
      if (this.isWarehouseStockEntry) {
        return this.queryParams.ownOnly === false ? "可见库存品项" : "仓库库存品项"
      }
      return this.queryParams.ownOnly === false ? "管理门店库存品项" : "门店库存品项"
    },
    stockMetricExtra() {
      if ((this.showWarehouseStoreFilter || this.showStoreStockFilter) && this.selectedStoreFilterName) {
        return "店铺：" + this.selectedStoreFilterName
      }
      if (this.isWarehouseStockEntry && this.queryParams.ownOnly === false) {
        return "门店库存只读"
      }
      return "只统计当前组织"
    },
    adjustBeforeQuantity() {
      if (!this.adjustForm.itemId) return null
      const stock = this.adjustStockRow || this.findStockRow(this.adjustForm.itemType, this.adjustForm.itemId, this.adjustForm.shopDeptId, this.adjustForm.warehouseId)
      if (!stock) return 0
      return this.toNumber(this.currentQuantityValue(stock))
    },
    adjustAfterQuantity() {
      const beforeQuantity = this.toNumber(this.adjustBeforeQuantity)
      const adjustQuantity = this.toNumber(this.adjustForm.adjustQuantity)
      if (beforeQuantity === null || adjustQuantity === null) return null
      return beforeQuantity + adjustQuantity
    },
    adjustAfterQuantityClass() {
      if (this.adjustAfterQuantity === null) return ""
      return this.adjustAfterQuantity < 0 ? "text-danger" : "text-success"
    },
    adjustInventoryDeptId() {
      const row = this.adjustStockRow || {}
      return row.warehouseId || row.shopDeptId || this.adjustForm.warehouseId || this.adjustForm.shopDeptId || this.currentDeptId
    },
    adjustInventoryDeptName() {
      const row = this.adjustStockRow || {}
      return this.displayValue(row.warehouseName || row.warehouseDeptName || row.shopName || row.shopDeptName || this.deptMap[String(this.adjustInventoryDeptId)] || this.currentDeptName)
    },
    adjustInventoryDeptHint() {
      return this.isWarehouseContext ? "调整将写入当前仓库库存" : "调整将写入当前门店库存"
    }
  },
  watch: {
    categoryKeyword(value) {
      if (this.$refs.categoryTree) {
        this.$refs.categoryTree.filter(value)
      }
    },
    "$route.fullPath"() {
      this.applyEntryDefaults()
      this.loadVisibleStoreOptions()
      if (this.ensureEntryContext()) {
        this.loadStockData()
      }
    }
  },
  created() {
    this.applyEntryDefaults()
    this.loadCategories()
    this.loadDeptMap()
    this.loadVisibleStoreOptions()
    if (this.ensureEntryContext(false)) {
      this.loadStockData()
    }
  },
  methods: {
    itemTypeLabel(type) {
      return { product: "商品", oe: "器皿", gift: "礼盒" }[type] || "其他物料"
    },
    flattenCategories(list) {
      const result = []
      const walk = items => {
        ;(items || []).forEach(item => {
          result.push(item)
          if (item.children && item.children.length) {
            walk(item.children)
          }
        })
      }
      walk(list)
      return result
    },
    decorateCategories(list, parentPath) {
      const pathPrefix = parentPath || []
      return (list || []).map(item => {
        const currentName = item.categoryName || ""
        const currentPath = pathPrefix.concat(currentName)
        const children = this.decorateCategories(item.children || [], currentPath)
        return Object.assign({}, item, {
          categoryFullPath: currentPath.filter(Boolean).join(" / "),
          children: children
        })
      })
    },
    getDefaultCategoryTree(children) {
      return [{ categoryId: 0, categoryName: "全部库存", categoryFullPath: "全部库存", children: children || [] }]
    },
    loadCategories() {
      this.categoryLoading = true
      return categoryTree().then(res => {
        this.categoryLoadError = ""
        const roots = this.decorateCategories(res.data || [])
        this.categoryTreeData = this.getDefaultCategoryTree(roots)
        this.categoryOptions = this.flattenCategories(roots)
        this.$nextTick(() => {
          if (this.$refs.categoryTree) {
            this.$refs.categoryTree.filter(this.categoryKeyword)
            this.$refs.categoryTree.setCurrentKey(this.queryParams.categoryId || 0)
          }
        })
      }).catch(() => {
        this.categoryLoadError = "分类加载失败，请联系管理员授权"
        this.categoryTreeData = this.getDefaultCategoryTree()
        this.categoryOptions = []
        this.selectedCategory = null
        this.queryParams.categoryId = undefined
        this.$nextTick(() => {
          if (this.$refs.categoryTree) {
            this.$refs.categoryTree.setCurrentKey(0)
          }
        })
      }).finally(() => {
        this.categoryLoading = false
      })
    },
    toggleCategoryPanel(collapsed) {
      if (this.categoryCollapsed === collapsed) {
        return
      }
      this.categoryCollapsed = collapsed
      this.refreshStockTableLayout()
    },
    refreshStockTableLayout() {
      this.$nextTick(() => {
        if (this.$refs.stockTable) {
          this.$refs.stockTable.doLayout()
        }
      })
      window.setTimeout(() => {
        if (this.$refs.stockTable) {
          this.$refs.stockTable.doLayout()
        }
      }, 220)
    },
    filterCategoryNode(value, data) {
      if (!value) return true
      const keyword = value.toLowerCase()
      const text = [
        data.categoryName,
        data.categoryCode,
        data.categoryFullPath
      ].filter(Boolean).join(" ").toLowerCase()
      return text.indexOf(keyword) !== -1
    },
    handleCategoryClick(data) {
      this.selectedCategory = data.categoryId ? data : null
      this.queryParams.categoryId = data.categoryId || undefined
      this.queryParams.pageNum = 1
      this.loadStockData()
    },
    applyEntryDefaults() {
      if (this.showStoreStockFilter) {
        this.normalizeStoreFilter()
        return
      }
      if (!this.isWarehouseStockEntry || !this.isWarehouseContext) {
        this.queryParams.ownOnly = true
      }
      this.normalizeStoreFilter()
    },
    ensureEntryContext(showMessage = true) {
      if (this.isEntryContextValid) {
        return true
      }
      this.list = []
      this.total = 0
      this.summary = {}
      this.stockLoadError = ""
      if (showMessage) {
        this.$modal.msgError(this.entryContextMessage)
      }
      return false
    },
    loadStockData() {
      if (!this.ensureEntryContext()) return
      this.getSummary()
      this.getList()
    },
    getSummary() {
      if (!this.ensureEntryContext(false)) return
      this.summaryLoading = true
      const summaryParams = Object.assign({}, this.queryParams, { stockScope: "" })
      getStockSummary(summaryParams).then(res => {
        this.summary = res.data || {}
      }).finally(() => { this.summaryLoading = false })
    },
    getList() {
      if (!this.ensureEntryContext(false)) return
      this.loading = true
      this.stockLoadError = ""
      return listStock(this.queryParams).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        return this.hydrateProducts(this.list)
      }).catch(() => {
        this.list = []
        this.total = 0
        this.stockLoadError = "暂时无法加载库存列表。请检查网络或稍后重试；当前不会把失败误显示为 0 条库存。"
      }).finally(() => { this.loading = false })
    },
    handleProductQueryChange() {
      this.queryParams.pageNum = 1
    },
    handleItemTypeQueryChange() {
      this.queryParams.itemId = undefined
      this.queryParams.productId = undefined
      this.queryParams.categoryId = undefined
      this.selectedCategory = null
      this.queryParams.pageNum = 1
    },
    handleStatusChange() {
      this.queryParams.pageNum = 1
    },
    handleScopeChange() {
      this.queryParams.pageNum = 1
      this.normalizeStoreFilter()
      if (this.showManagedStoreFilter) {
        this.loadAuthorizedStoreOptions()
      } else if (this.showWarehouseStoreFilter) {
        this.loadVisibleStoreOptions()
      }
      this.loadStockData()
    },
    handleStoreFilterChange() {
      this.queryParams.pageNum = 1
      this.normalizeStoreFilter()
      this.loadStockData()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.loadStockData()
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize || 10,
        itemType: undefined,
        itemId: undefined,
        productId: undefined,
        categoryId: undefined,
        shopDeptId: undefined,
        batchNo: undefined,
        expiryDate: undefined,
        serialNo: undefined,
        locationCode: undefined,
        locationName: undefined,
        stockScope: "",
        stockStatus: undefined,
        ownOnly: true
      }
      this.selectedCategory = null
      this.$nextTick(() => {
        if (this.$refs.categoryTree) {
          this.$refs.categoryTree.setCurrentKey(0)
        }
      })
      this.applyEntryDefaults()
      this.loadStockData()
    },
    normalizeStoreFilter() {
      if (!this.showWarehouseStoreFilter && !this.showStoreStockFilter) {
        this.queryParams.shopDeptId = undefined
        return
      }
      if (this.showStoreStockFilter) {
        if (this.queryParams.ownOnly === false) {
          if (!this.hasValue(this.queryParams.shopDeptId) || this.storeOptions.length === 0) {
            this.queryParams.shopDeptId = this.allStoreOptionValue
            return
          }
          if (Number(this.queryParams.shopDeptId) === this.allStoreOptionValue) {
            return
          }
          if (this.storeOptions.length > 0) {
            const matchedStore = this.storeOptions.find(item => String(item.deptId) === String(this.queryParams.shopDeptId))
            if (matchedStore) {
              // 当前组织 ID 来自 sessionStorage，通常是字符串；接口选项 ID 通常是数字。
              // Element UI 会严格匹配下拉值，因此回写选项的原始值以正确显示门店名称。
              this.queryParams.shopDeptId = matchedStore.deptId
            } else {
              this.queryParams.shopDeptId = this.allStoreOptionValue
            }
          }
          return
        }
        this.queryParams.ownOnly = true
        this.queryParams.shopDeptId = this.currentDeptId
        return
      }
      if (!this.hasValue(this.queryParams.shopDeptId) || this.storeOptions.length === 0) {
        return
      }
      const matchedStore = this.storeOptions.find(item => String(item.deptId) === String(this.queryParams.shopDeptId))
      if (matchedStore) {
        this.queryParams.shopDeptId = matchedStore.deptId
      } else {
        this.queryParams.shopDeptId = undefined
      }
    },
    openAdjust(row) {
      if (!this.ensureEntryContext()) return
      if (!this.canAdjustCurrentScope) {
        this.$modal.msgError("可见/管理库存范围为只读，请先切回当前组织")
        return
      }
      if (row && !this.canAdjustStock(row)) {
        this.$modal.msgError("只能调整当前组织库存")
        return
      }
      const currentDeptId = this.currentDeptId
      const stock = row && (row.itemId !== undefined || row.productId !== undefined)
        ? row : this.findStockRow(this.queryParams.itemType, this.queryParams.itemId || this.queryParams.productId, currentDeptId, currentDeptId)
      this.adjustStockRow = stock || null
      this.adjustForm = {
        itemType: stock ? (stock.itemType || "product") : (this.queryParams.itemType || null),
        itemId: stock ? (stock.itemId || stock.productId) : (this.queryParams.itemId || this.queryParams.productId),
        productId: stock ? stock.productId : this.queryParams.productId,
        shopDeptId: stock ? stock.shopDeptId : currentDeptId,
        warehouseId: stock ? stock.warehouseId : currentDeptId,
        batchNo: stock ? stock.batchNo : "",
        expiryDate: stock ? stock.expiryDate : undefined,
        serialNo: stock ? stock.serialNo : "",
        locationCode: stock ? stock.locationCode : "",
        locationName: stock ? stock.locationName : "",
        adjustQuantity: 0,
        reason: ""
      }
      this.adjustOpen = true
      this.$nextTick(() => {
        if (this.$refs.adjustRef) {
          this.$refs.adjustRef.clearValidate()
        }
      })
    },
    onAdjustItemTypeChange() {
      this.adjustForm.itemId = undefined
      this.adjustForm.productId = undefined
      this.adjustStockRow = null
    },
    onAdjustItemSelected(item) {
      if (!item || !this.adjustForm.itemId) {
        this.adjustStockRow = null
        return
      }
      this.adjustForm.productId = item.productId
      const currentDeptId = this.currentDeptId
      const stock = this.findStockRow(this.adjustForm.itemType, this.adjustForm.itemId, currentDeptId, currentDeptId)
      this.adjustStockRow = stock || null
      if (stock) {
        this.adjustForm.shopDeptId = stock.shopDeptId
        this.adjustForm.warehouseId = stock.warehouseId
        this.adjustForm.batchNo = stock.batchNo || ""
        this.adjustForm.expiryDate = stock.expiryDate || undefined
        this.adjustForm.serialNo = stock.serialNo || ""
        this.adjustForm.locationCode = stock.locationCode || ""
        this.adjustForm.locationName = stock.locationName || ""
      } else {
        this.adjustForm.shopDeptId = currentDeptId
        this.adjustForm.warehouseId = currentDeptId
        this.adjustForm.batchNo = ""
        this.adjustForm.expiryDate = undefined
        this.adjustForm.serialNo = ""
        this.adjustForm.locationCode = ""
        this.adjustForm.locationName = ""
      }
    },
    adjustProductName() {
      const row = this.adjustStockRow || {}
      const product = this.adjustForm.itemType === "product" && this.hasValue(this.adjustForm.productId)
        ? (this.productMap[String(this.adjustForm.productId)] || this.resolveProduct(row))
        : {}
      return this.displayValue(row.itemName || row.productName || product.productName || "未命名物料")
    },
    getAdjustConfirmMessage(payload) {
      const quantity = this.toNumber(payload.adjustQuantity)
      const direction = quantity > 0 ? "增加库存" : "扣减库存"
      const detailLines = [
        "确认写入库存调整",
        "物料：" + this.adjustProductName(),
        "库存组织：" + this.adjustInventoryDeptName,
        "调整方向：" + direction,
        "调整前：" + this.formatQuantity(this.adjustBeforeQuantity),
        "本次调整：" + this.formatQuantity(payload.adjustQuantity),
        "调整后：" + this.formatQuantity(this.adjustAfterQuantity),
        "调整原因：" + this.displayValue(payload.reason),
        "提交后会立即生成库存流水，不可直接撤回。"
      ]
      return detailLines.join("\n")
    },
    doAdjust() {
      if (!this.ensureEntryContext()) return
      if (!this.canAdjustCurrentScope) {
        this.$modal.msgError("可见/管理库存范围为只读，请先切回当前组织")
        return
      }
      this.$refs.adjustRef.validate(valid => {
        if (!valid) return
        if (this.adjustAfterQuantity !== null && this.adjustAfterQuantity < 0) {
          this.$modal.msgError("调整后库存不能为负数")
          return
        }
        const inventoryDeptId = this.adjustForm.warehouseId || this.adjustForm.shopDeptId || this.currentDeptId
        if (!this.hasValue(inventoryDeptId)) {
          this.$modal.msgError("请先选择店铺")
          return
        }
        if (String(inventoryDeptId) !== String(this.currentDeptId)) {
          this.$modal.msgError("只能调整当前组织库存")
          return
        }
        const batchNo = this.trimToUndefined(this.adjustForm.batchNo)
        const serialNo = this.trimToUndefined(this.adjustForm.serialNo)
        const locationCode = this.trimToUndefined(this.adjustForm.locationCode)
        const locationName = this.trimToUndefined(this.adjustForm.locationName)
        const payload = {
          itemType: this.adjustForm.itemType,
          itemId: this.adjustForm.itemId,
          productId: this.adjustForm.productId,
          shopDeptId: this.currentDeptId,
          warehouseId: this.currentDeptId,
          batchNo,
          expiryDate: this.adjustForm.expiryDate,
          serialNo,
          locationCode,
          locationName,
          adjustQuantity: this.adjustForm.adjustQuantity,
          reason: (this.adjustForm.reason || "").trim()
        }
        this.$modal.confirm(this.getAdjustConfirmMessage(payload)).then(() => {
          this.adjustLoading = true
          adjustStock(payload).then(() => { this.$modal.msgSuccess("调整成功"); this.adjustOpen = false; this.loadStockData() }).finally(() => { this.adjustLoading = false })
        })
      })
    },
    handleExport() {
      if (!this.ensureEntryContext()) return
      this.$modal.confirm("确认导出当前查询条件下的库存数据？").then(() => {
        this.download("inventory/stock/export", { ...this.queryParams }, this.exportFileName("库存数据"))
      })
    },
    openStockLog() {
      const route = {
        path: "/inventory/stock-log",
        query: {
          fromScope: this.queryParams.ownOnly === false ? "visible" : "current",
          stockEntry: this.stockEntry
        }
      }
      if (this.queryParams.ownOnly === false) {
        this.$modal.confirm("库存流水只显示当前组织的数据，将从可见/管理库存范围切回当前组织。是否继续？")
          .then(() => this.$router.push(route))
        return
      }
      this.$router.push(route)
    },
    loadDeptMap() {
      listShopTree().then(res => {
        const map = Object.assign({}, this.deptMap)
        this.flattenDeptList(res.data || res.rows || []).forEach(dept => {
          if (dept.deptId) {
            map[String(dept.deptId)] = dept.deptName
          }
        })
        this.deptMap = map
      }).catch(() => {})
    },
    loadVisibleStoreOptions() {
      if (!this.isWarehouseStockEntry || !this.isWarehouseContext || !this.hasValue(this.currentDeptId)) {
        this.storeOptions = []
        this.normalizeStoreFilter()
        return Promise.resolve([])
      }
      return listVisibleStoreDept({ scopeDeptId: this.currentDeptId }).then(res => {
        const stores = this.normalizeStoreOptions(res.data || res.rows || [])
        const nextDeptMap = Object.assign({}, this.deptMap)
        stores.forEach(store => {
          nextDeptMap[String(store.deptId)] = store.deptName
        })
        this.deptMap = nextDeptMap
        this.storeOptions = stores
        this.normalizeStoreFilter()
        return stores
      }).catch(() => {
        this.storeOptions = []
        this.normalizeStoreFilter()
        return []
      })
    },
    loadAuthorizedStoreOptions() {
      if (!this.showManagedStoreFilter || !this.hasValue(this.currentDeptId)) {
        this.storeOptions = []
        this.normalizeStoreFilter()
        return Promise.resolve([])
      }
      return listShopTree().then(res => {
        const stores = this.normalizeStoreOptions(res.data || res.rows || [])
        const nextDeptMap = Object.assign({}, this.deptMap)
        stores.forEach(store => {
          nextDeptMap[String(store.deptId)] = store.deptName
        })
        this.deptMap = nextDeptMap
        this.storeOptions = stores
        this.normalizeStoreFilter()
        return stores
      }).catch(() => {
        this.storeOptions = []
        this.normalizeStoreFilter()
        return []
      })
    },
    normalizeStoreOptions(depts) {
      const stores = []
      const seen = {}
      this.flattenDeptList(depts).forEach(dept => {
        const deptType = dept.deptType ? String(dept.deptType).toUpperCase() : ""
        if (!dept.deptId || deptType !== "STORE" || seen[String(dept.deptId)]) {
          return
        }
        seen[String(dept.deptId)] = true
        stores.push({ deptId: dept.deptId, deptName: dept.deptName })
      })
      return stores
    },
    hydrateProducts(rows) {
      const productIds = rows
        .filter(row => row && (row.itemType || "product") === "product" && !row.itemName && !row.productName)
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
    productCategory(row) {
      const product = this.resolveProduct(row)
      return this.displayValue(row.itemCategoryFullPath || row.itemCategoryName || row.categoryFullPath || row.categoryName || product.categoryFullPath || product.categoryName)
    },
    productGrade(row) {
      const product = this.resolveProduct(row)
      return this.displayValue(row.itemGrade || row.grade || product.grade)
    },
    productSpec(row) {
      const product = this.resolveProduct(row)
      return this.displayValue(row.itemSpec || row.spec || product.spec)
    },
    productImageUrl(row) {
      const product = this.resolveProduct(row)
      return row.imageUrl || row.packageImageUrl || row.dryTeaImageUrl || row.teaSoupImageUrl || row.leafBottomImageUrl || row.extraImageUrl ||
        product.imageUrl || product.packageImageUrl || product.dryTeaImageUrl || product.teaSoupImageUrl || product.leafBottomImageUrl || product.extraImageUrl || ""
    },
    openStockDetail(row) {
      this.detailStock = row
      this.detailOpen = true
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
    inventoryDeptId(row) {
      return row ? (row.warehouseId || row.shopDeptId) : undefined
    },
    canAdjustStock(row) {
      return row && this.hasValue(this.currentDeptId) && String(this.inventoryDeptId(row)) === String(this.currentDeptId)
    },
    stockStatusLabel(row) {
      const availableQuantity = this.toNumber(this.availableQuantityValue(row))
      if (availableQuantity === null) return "-"
      if (availableQuantity <= 0) return "缺货"
      if (availableQuantity <= this.safetyStockMinValue(row)) return "低库存"
      return "正常"
    },
    stockStatusTagType(row) {
      const status = this.stockStatusLabel(row)
      if (status === "缺货") return "danger"
      if (status === "低库存") return "warning"
      return "success"
    },
    currentQuantityValue(row) {
      return row.currentQuantity
    },
    availableQuantityValue(row) {
      if (this.hasValue(row.availableQuantity)) return row.availableQuantity
      const currentQuantity = this.toNumber(row.currentQuantity)
      const lockedQuantity = this.toNumber(row.lockedQuantity)
      if (currentQuantity === null || lockedQuantity === null) return null
      return currentQuantity - lockedQuantity
    },
    safetyStockMinValue(row) {
      const safeRow = row || {}
      const product = this.resolveProduct(safeRow)
      const value = this.hasValue(safeRow.safetyStockMin) ? safeRow.safetyStockMin : product.safetyStockMin
      const safetyStockMin = this.toNumber(value)
      return safetyStockMin === null ? 10 : safetyStockMin
    },
    totalCostValue(row) {
      if (this.hasValue(row.totalCost)) return row.totalCost
      const currentQuantity = this.toNumber(row.currentQuantity)
      const costPrice = this.toNumber(row.costPrice)
      if (currentQuantity === null || costPrice === null) return null
      return currentQuantity * costPrice
    },
    findStockRow(itemType, itemId, shopDeptId, warehouseId) {
      if (!this.hasValue(itemId)) return null
      const exact = this.list.find(row => {
        const productMatched = (row.itemType || "product") === (itemType || "product") && String(row.itemId || row.productId) === String(itemId)
        const shopMatched = !this.hasValue(shopDeptId) || String(row.shopDeptId) === String(shopDeptId)
        const warehouseMatched = !this.hasValue(warehouseId) || String(row.warehouseId) === String(warehouseId)
        return productMatched && shopMatched && warehouseMatched
      })
      if (exact) return exact
      if (this.hasValue(shopDeptId) || this.hasValue(warehouseId)) {
        return null
      }
      return this.list.find(row => (row.itemType || "product") === (itemType || "product") && String(row.itemId || row.productId) === String(itemId)) || null
    },
    stockQuantityClass(row) {
      const quantity = this.toNumber(this.currentQuantityValue(row))
      if (quantity === null) return ""
      return quantity <= 0 ? "text-danger" : quantity <= this.safetyStockMinValue(row) ? "text-warning" : ""
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
    trimToUndefined(value) {
      if (!this.hasValue(value)) return undefined
      const text = String(value).trim()
      return text ? text : undefined
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ""
    }
  }
}
</script>
<style lang="scss" scoped>
.app-container {
  padding: 14px 18px 24px;
  background: linear-gradient(180deg, #f8fafc 0%, #f5f7fb 48%, #f4f6fa 100%);
}

.app-container ::v-deep .el-card {
  border: 1px solid #dde6f2;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.app-container ::v-deep .el-card:hover {
  border-color: #cfdae9;
}

.stock-page {
  .mb12 {
    margin-bottom: 12px;
  }

  .context-alert {
    border-radius: 8px;
  }

  .stock-layout {
    display: flex;
    width: 100%;
    align-items: stretch;
    gap: 18px;
    min-width: 0;
  }

  ::v-deep .category-card.el-card,
  ::v-deep .search-card.el-card,
  ::v-deep .table-card.el-card {
    border: none !important;
    box-shadow: none !important;
  }

  aside.category-panel {
    background-color: transparent !important;
  }

  .category-panel {
    width: 240px;
    min-width: 0;
    display: flex;
    flex: 0 0 240px;
    padding: 0 !important;
    background: transparent !important;
    border: none !important;
    box-shadow: none !important;
    overflow: hidden;
    opacity: 1;
    transition: flex-basis 0.22s ease, width 0.22s ease, opacity 0.16s ease;
  }

  .stock-main {
    min-width: 0;
    flex: 1 1 auto;
  }

  .collapsed-category-action {
    display: none;
    margin-bottom: 10px;
  }

  .is-category-collapsed {
    gap: 0;

    .category-panel {
      width: 0;
      flex-basis: 0;
      opacity: 0;
      pointer-events: none;
    }

    .collapsed-category-action {
      display: flex;
    }
  }

  .category-card {
    display: flex;
    width: 100%;
    margin: 0 !important;
    min-height: calc(100vh - 250px);
    flex: 1 1 auto;
    flex-direction: column;
  }

  ::v-deep .category-card .el-card__header {
    padding: 9px 10px;
  }

  ::v-deep .category-card .el-card__body {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    padding: 10px;
  }

  .category-search {
    margin-bottom: 8px;
  }

  ::v-deep .category-search .el-input__inner {
    height: 28px;
    line-height: 28px;
  }

  ::v-deep .category-tree {
    flex: 1 1 auto;
    overflow: auto;
    color: #475569;
    font-size: 12px;
  }

  ::v-deep .category-tree .el-tree-node__content {
    height: 27px;
    margin: 1px 0;
    border-radius: 5px;
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  ::v-deep .category-tree .el-tree-node__content:hover {
    background: #f5f7fb;
  }

  ::v-deep .category-tree .is-current > .el-tree-node__content {
    background: #edf5ff;
    color: #1f5fbf;
    font-weight: 600;
  }

  ::v-deep .category-tree .el-tree-node__expand-icon {
    color: #a8b1c0;
    font-size: 12px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    min-width: 0;
    gap: 8px;
  }

  .card-header > span,
  .table-title span {
    flex: 0 1 auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .card-actions {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 6px;
  }

  .card-actions .el-button {
    padding: 0 2px;
    font-size: 12px;
    font-weight: 700;
  }

  .category-summary {
    margin-top: auto;
    padding: 10px 11px;
    border-radius: 8px;
    background: #f7f9fd;
    color: #6d7a8e;
    font-size: 12px;
    line-height: 18px;
  }

  .category-error {
    margin-bottom: 4px;
    color: #d14b45;
    font-weight: 600;
  }

  .tree-node {
    display: flex;
    min-width: 0;
    width: 100%;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding-right: 8px;
  }

  .tree-node-marker {
    width: 4px;
    height: 4px;
    flex: 0 0 4px;
    border-radius: 50%;
    background: #cbd5e1;
  }

  .tree-node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .query-form {
    display: flex;
    flex-wrap: wrap;
  }

  ::v-deep .search-card .el-card__body {
    padding: 18px 22px 16px;
  }

  ::v-deep .query-form .el-form-item {
    margin-right: 14px;
    margin-bottom: 12px;
  }

  ::v-deep .query-form .el-form-item__label {
    color: #344258;
    font-size: 13px;
    font-weight: 700;
    line-height: 34px;
  }

  ::v-deep .query-form .el-input__inner {
    height: 34px;
    line-height: 34px;
    border-radius: 7px;
    font-size: 13px;
  }

  .query-actions {
    white-space: nowrap;
  }

  .query-actions .el-button {
    height: 32px;
    padding: 7px 12px;
    border-radius: 7px;
    font-size: 13px;
  }

  .metric-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(120px, 1fr));
    gap: 10px;
    margin-top: 2px;
  }

  .metric-card {
    min-height: 68px;
    padding: 10px 12px;
    background: #f8fafc;
    border: 1px solid #eef3f8;
    border-radius: 8px;
  }

  .metric-value {
    color: #0f172a;
    font-size: 21px;
    font-weight: 700;
    line-height: 24px;
    font-variant-numeric: tabular-nums;
    letter-spacing: 0;
  }

  .metric-label {
    margin-top: 2px;
    color: #64748b;
    font-size: 12px;
  }

  .metric-extra {
    margin-top: 3px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 16px;
  }

  .warning-value {
    color: #d97706;
  }

  .table-title {
    display: inline-flex;
    min-width: 0;
    align-items: center;
    gap: 8px;
  }

  .stock-tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 44px;
    padding-bottom: 12px;
    border-bottom: 1px solid #edf2f7;
  }

  .stock-tabs ::v-deep .el-radio-button__inner {
    height: 30px;
    min-width: 78px;
    padding: 7px 12px;
    color: #64748b;
    font-size: 12px;
    font-weight: 700;
  }

  .stock-tabs ::v-deep .el-radio-button__orig-radio:checked + .el-radio-button__inner {
    border-color: #4258ee;
    background: #4258ee;
    color: #ffffff;
    box-shadow: -1px 0 0 0 #4258ee;
  }

  .stock-tabs ::v-deep .el-radio-button__orig-radio:checked + .el-radio-button__inner:hover,
  .stock-tabs ::v-deep .el-radio-button__orig-radio:checked + .el-radio-button__inner:focus {
    color: #ffffff;
  }

  .stock-total {
    color: #8a99ad;
    font-size: 12px;
    white-space: nowrap;
  }

  ::v-deep .table-card .el-card__body {
    padding: 16px 18px 16px;
  }

  ::v-deep .table-card .el-table__fixed,
  ::v-deep .table-card .el-table__fixed-right {
    box-shadow: none !important;
  }

  ::v-deep .table-card .el-table__fixed::before,
  ::v-deep .table-card .el-table__fixed-right::before {
    background-color: #eef3f8;
  }

  .stock-table ::v-deep .el-table__row {
    cursor: pointer;
  }
}

.stock-detail {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.stock-detail-image {
  width: 180px;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.stock-detail-image ::v-deep .el-image,
.stock-detail-image ::v-deep img {
  width: 100%;
  height: 100%;
}

.stock-image-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 700;
}

.stock-detail-info {
  min-width: 0;
}

.text-danger { color: #dc2626; font-weight: 700 }
.text-warning { color: #d97706; font-weight: 700 }
.text-success { color: #0f9f6e; font-weight: 700 }
.quantity-preview { font-weight: 700 }
.muted-text {
  color: #8a99ad;
  font-size: 12px;
  margin-left: 8px;
}
.muted-block {
  margin-left: 0;
  margin-top: 4px;
}

@media (max-width: 992px) {
  .stock-page {
    .stock-layout {
      flex-direction: column;
    }

    .category-panel {
      width: 100%;
      flex: 0 0 auto;
      max-height: 900px;
      transition: max-height 0.22s ease, opacity 0.16s ease;
    }

    .is-category-collapsed {
      .category-panel {
        width: 100%;
        max-height: 0;
        flex-basis: auto;
      }
    }

    .category-card {
      min-height: auto;
      margin-bottom: 12px;
    }

    .metric-row {
      grid-template-columns: repeat(2, minmax(120px, 1fr));
    }

    .stock-tabs {
      align-items: flex-start;
      flex-direction: column;
    }
  }

  .stock-detail {
    grid-template-columns: 1fr;
  }

  .stock-detail-image {
    width: 100%;
    max-width: 220px;
  }
}

@media (max-width: 640px) {
  .stock-page {
    .metric-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>
