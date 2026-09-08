<template>
  <div class="app-container oa-workspace-page fixed-asset-config-page">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 固定资产</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-coin" /></span>
          <div>
            <h1>固定资产配置</h1>
            <p>按店铺维护固定资产明细和年度申报比例；超过额度后员工按同款参考自行购买且无需上报。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__actions">
        <el-button size="mini" icon="el-icon-refresh" @click="getList">刷新</el-button>
        <el-button type="primary" size="mini" icon="el-icon-plus" @click="openConfigForm()" v-hasPermi="['oa:fixedAsset:config:edit']">添加店铺固定资产明细</el-button>
        <el-button size="mini" icon="el-icon-download" :disabled="assetActionDisabled" @click="handleExport" v-hasPermi="['oa:fixedAsset:config:export']">导出</el-button>
      </div>
    </section>

    <el-card shadow="never" class="search-card oa-filter-card mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="店铺">
          <treeselect
            v-model="queryParams.shopDeptId"
            :options="shopOptions"
            :normalizer="normalizer"
            :append-to-body="true"
            :z-index="3000"
            placeholder="全部可见店铺"
            class="dept-select"
            @input="handleQuery"
          />
        </el-form-item>
        <el-form-item label="OE器皿">
          <el-input v-model="queryParams.oeItemName" placeholder="固定资产OE器皿" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" @change="handleQuery">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="assetActionDisabled && !storeConfigRows.length"
      title="固定资产基础配置为空"
      description="可直接添加店铺固定资产明细，并在弹窗内选择店铺；导出前需先筛选店铺。"
      type="warning"
      show-icon
      :closable="false"
      class="mb12"
    />

    <section class="quota-grid">
      <div class="quota-cell">
        <span>固定资产总金额</span>
        <strong>{{ money(quota.assetTotalAmount) }}</strong>
      </div>
      <div class="quota-cell">
        <span>年度申报比例</span>
        <strong>{{ percent(quota.annualRepairRatio) }}</strong>
      </div>
      <div class="quota-cell">
        <span>年度额度</span>
        <strong>{{ money(quota.annualQuotaAmount) }}</strong>
      </div>
      <div class="quota-cell">
        <span>月度释放额度</span>
        <strong>{{ money(quota.monthlyQuotaAmount) }}</strong>
      </div>
      <div class="quota-cell">
        <span>当前可用额度</span>
        <strong>{{ money(quota.availableQuotaAmount) }}</strong>
      </div>
    </section>

    <el-card shadow="never" class="table-card oa-table-card">
      <div slot="header" class="oa-card-heading">
        <div class="oa-card-heading__title">
          <span class="oa-card-heading__icon"><i class="el-icon-office-building" /></span>
          <div>
            <h2>店铺资产配置</h2>
            <p>按店铺查看资产总额、剩余可报销金额与明细</p>
          </div>
        </div>
        <el-tag size="small" type="info">{{ total }} 家店铺</el-tag>
      </div>
      <el-table v-loading="loading" :data="storeConfigRows" row-key="shopDeptId" border stripe :empty-text="fixedAssetConfigEmptyText">
        <el-table-column label="店铺" prop="shopDeptName" min-width="180" show-overflow-tooltip />
        <el-table-column label="固定资产总金额" prop="assetTotalAmount" width="160" align="right">
          <template slot-scope="scope">{{ money(scope.row.assetTotalAmount) }}</template>
        </el-table-column>
        <el-table-column label="剩余可报销金额" prop="availableQuotaAmount" width="160" align="right">
          <template slot-scope="scope">{{ money(scope.row.availableQuotaAmount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="mini" @click="openDetailDrawer(scope.row)">查看明细</el-button>
            <el-button type="text" size="mini" @click="openConfigForm(scope.row)" v-hasPermi="['oa:fixedAsset:config:edit']">维护明细</el-button>
            <el-button type="text" size="mini" class="danger-text" @click="removeStoreConfig(scope.row)" v-hasPermi="['oa:fixedAsset:config:delete']">删除配置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <el-drawer
      :visible.sync="detailDrawerOpen"
      :with-header="false"
      size="780px"
      append-to-body
      custom-class="asset-detail-drawer"
    >
      <div class="drawer-header">
        <div>
          <span class="drawer-eyebrow">固定资产明细</span>
          <h3>{{ activeStoreConfig.shopDeptName || "-" }}</h3>
        </div>
        <el-button icon="el-icon-close" circle size="mini" @click="detailDrawerOpen = false" />
      </div>
      <div class="drawer-metrics">
        <div class="drawer-metric">
          <span>固定资产总金额</span>
          <strong>{{ money(activeStoreConfig.assetTotalAmount) }}</strong>
        </div>
        <div class="drawer-metric">
          <span>剩余可报销金额</span>
          <strong>{{ money(activeStoreConfig.availableQuotaAmount) }}</strong>
        </div>
      </div>
      <div class="drawer-table-card">
        <div class="drawer-table-title">
          <span>明细列表</span>
          <strong>{{ (activeStoreConfig.details || []).length }} 条</strong>
        </div>
        <el-table :data="activeStoreConfig.details || []" border size="small" empty-text="暂无固定资产明细">
          <el-table-column label="OE编码" prop="oeItemCode" width="150" show-overflow-tooltip />
          <el-table-column label="固定资产明细" prop="oeItemName" min-width="260" show-overflow-tooltip />
          <el-table-column label="数量" prop="assetQuantity" width="90" align="right">
            <template slot-scope="scope">{{ quantity(scope.row.assetQuantity) }}</template>
          </el-table-column>
          <el-table-column label="资产单价" prop="assetUnitPrice" width="120" align="right">
            <template slot-scope="scope">{{ money(scope.row.assetUnitPrice) }}</template>
          </el-table-column>
          <el-table-column label="资产金额" prop="assetAmount" width="120" align="right">
            <template slot-scope="scope">{{ money(scope.row.assetAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" prop="status" width="90" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog :title="selectedAssetRows.length ? '维护店铺固定资产明细' : '添加店铺固定资产明细'" :visible.sync="configOpen" width="760px" append-to-body>
      <el-form ref="configForm" :model="form" :rules="rules" label-width="112px">
        <el-form-item label="店铺" prop="shopDeptId">
          <treeselect
            v-model="form.shopDeptId"
            :options="shopOptions"
            :normalizer="normalizer"
            :append-to-body="true"
            :z-index="3000"
            placeholder="选择店铺"
            @input="handleConfigShopChange"
          />
        </el-form-item>
        <el-form-item label="固定资产OE" prop="oeItemId">
          <el-select
            v-model="selectedOeItemIds"
            multiple
            filterable
            remote
            reserve-keyword
            collapse-tags
            :multiple-limit="form.configId ? 1 : 0"
            placeholder="请选择OE器皿，可输入名称或编码搜索"
            :remote-method="searchOeItems"
            :loading="oeLoading"
            style="width: 100%"
            @change="syncSelectedOe"
            @visible-change="handleOeDropdownVisible"
          >
            <el-option v-for="item in oeOptions" :key="item.oeItemId" :label="item.oeItemName + ' / ' + item.oeItemCode" :value="item.oeItemId" />
          </el-select>
        </el-form-item>
        <el-form-item label="固定资产明细" required>
          <el-table
            v-if="selectedAssetRows.length"
            :data="selectedAssetRows"
            border
            class="asset-detail-table"
            size="mini"
          >
            <el-table-column label="OE编码" prop="oeItemCode" width="120" show-overflow-tooltip />
            <el-table-column label="OE名称" prop="oeItemName" min-width="160" show-overflow-tooltip />
            <el-table-column label="资产单价" width="110" align="right">
              <template slot-scope="scope">{{ money(scope.row.assetUnitPrice) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="150" align="center">
              <template slot-scope="scope">
                <el-input-number
                  v-model="scope.row.assetQuantity"
                  :min="0.01"
                  :precision="2"
                  size="mini"
                  controls-position="right"
                  @change="recalculateAmount(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="资产金额" width="120" align="right">
              <template slot-scope="scope">{{ money(scope.row.assetAmount) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template slot-scope="scope">
                <el-button type="text" size="mini" class="danger-text" @click="removeAssetRow(scope.row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="asset-detail-empty">请选择一个或多个 OE 器皿后维护数量。</div>
        </el-form-item>
        <el-form-item label="年度申报比例" prop="annualRepairRatio">
          <el-input-number v-model="form.annualRepairRatio" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="configOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import {
  deleteFixedAssetConfig,
  getFixedAssetQuota,
  listFixedAssetConfigs,
  saveFixedAssetConfig
} from "@/api/oa/fixedAsset"
import { listOe } from "@/api/inventory/oe"
import { shopTree } from "@/api/system/userShop"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"

export default {
  name: "OaFixedAssetConfig",
  components: { Treeselect },
  data() {
    return {
      loading: false,
      saving: false,
      oeLoading: false,
      configOpen: false,
      detailDrawerOpen: false,
      total: 0,
      rawConfigRows: [],
      allStoreConfigRows: [],
      storeConfigRows: [],
      shopOptions: [],
      oeOptions: [],
      selectedOeItemIds: [],
      selectedAssetRows: [],
      originalAssetRows: [],
      activeStoreConfig: { details: [] },
      quota: {},
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        shopDeptId: undefined,
        oeItemName: undefined,
        status: undefined
      },
      form: {},
      rules: {
        shopDeptId: [{ required: true, message: "请选择店铺", trigger: "change" }],
        annualRepairRatio: [{ required: true, message: "请输入年度申报比例", trigger: "change" }]
      }
    }
  },
  created() {
    this.loadShopTree()
    this.getList()
  },
  computed: {
    assetActionDisabled() {
      return !this.queryParams.shopDeptId
    },
    fixedAssetConfigEmptyText() {
      return getBusinessEmptyText("fixedAssetConfig", this.assetActionDisabled ? "missingContext" : "missingBaseline")
    }
  },
  methods: {
    normalizer(node) {
      return {
        id: node.deptId || node.id,
        label: node.deptName || node.label,
        children: node.children && node.children.length ? node.children : undefined
      }
    },
    loadShopTree() {
      shopTree().then(res => {
        this.shopOptions = res.data || []
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, shopDeptId: undefined, oeItemName: undefined, status: undefined }
      this.getList()
    },
    getList() {
      this.loading = true
      const listParams = Object.assign({}, this.queryParams, { pageNum: 1, pageSize: 5000 })
      listFixedAssetConfigs(listParams).then(res => {
        this.rawConfigRows = res.rows || []
        this.loadQuota()
        return this.hydrateStoreQuotaRows(this.groupFixedAssetConfigs(this.rawConfigRows))
      }).then(rows => {
        this.allStoreConfigRows = rows
        this.total = this.allStoreConfigRows.length
        this.storeConfigRows = this.paginateStoreRows(this.allStoreConfigRows)
      }).finally(() => {
        this.loading = false
      })
    },
    loadQuota() {
      getFixedAssetQuota({ shopDeptId: this.queryParams.shopDeptId }).then(res => {
        this.quota = res.data || {}
      }).catch(() => {
        this.quota = {}
      })
    },
    openDetailDrawer(row) {
      this.activeStoreConfig = row || { details: [] }
      this.detailDrawerOpen = true
    },
    openConfigForm(row) {
      const storeRow = row || this.findStoreConfigByShop(this.queryParams.shopDeptId)
      this.applyStoreConfigToForm(storeRow, this.queryParams.shopDeptId)
      this.configOpen = true
      this.loadSelectableOeOptions()
    },
    handleConfigShopChange(shopDeptId) {
      const storeRow = this.findStoreConfigByShop(shopDeptId)
      this.applyStoreConfigToForm(storeRow, shopDeptId)
      this.loadSelectableOeOptions()
    },
    applyStoreConfigToForm(storeRow, shopDeptId) {
      const details = storeRow && storeRow.details ? storeRow.details : []
      const annualRepairRatio = storeRow && storeRow.annualRepairRatio
        ? storeRow.annualRepairRatio
        : (this.form.annualRepairRatio || this.quota.annualRepairRatio || 20)
      this.form = {
        shopDeptId: storeRow ? storeRow.shopDeptId : shopDeptId,
        annualRepairRatio,
        status: storeRow ? (storeRow.status === "1" ? "1" : "0") : (this.form.status || "0"),
        remark: storeRow ? (storeRow.remark || "") : (this.form.remark || "")
      }
      this.selectedOeItemIds = details.map(item => item.oeItemId).filter(Boolean)
      this.selectedAssetRows = details.map(item => this.createAssetRow(item, item))
      this.originalAssetRows = this.selectedAssetRows.map(item => Object.assign({}, item))
      this.oeOptions = this.selectedAssetRows.map(row => ({
        oeItemId: row.oeItemId,
        oeItemCode: row.oeItemCode,
        oeItemName: row.oeItemName,
        costPrice: row.assetUnitPrice
      }))
    },
    searchOeItems(keyword) {
      this.loadSelectableOeOptions(keyword)
    },
    handleOeDropdownVisible(visible) {
      if (visible) {
        this.loadSelectableOeOptions()
      }
    },
    loadSelectableOeOptions(keyword = "") {
      keyword = (keyword || "").trim()
      this.oeLoading = true
      listOe({ pageNum: 1, pageSize: 50, oeItemName: keyword, status: "0" }).then(res => {
        const selectedOptions = this.selectedAssetRows.map(row => ({
          oeItemId: row.oeItemId,
          oeItemCode: row.oeItemCode,
          oeItemName: row.oeItemName,
          costPrice: row.assetUnitPrice
        }))
        const mergedOptions = selectedOptions.concat(res.rows || [])
        this.oeOptions = mergedOptions.filter((item, index, list) =>
          item.oeItemId && list.findIndex(option => option.oeItemId === item.oeItemId) === index
        )
      }).finally(() => {
        this.oeLoading = false
      })
    },
    syncSelectedOe(oeItemIds) {
      const ids = Array.isArray(oeItemIds) ? oeItemIds : []
      const existingRows = this.selectedAssetRows.reduce((result, row) => {
        result[row.oeItemId] = row
        return result
      }, {})
      this.selectedAssetRows = ids.map(oeItemId => {
        if (existingRows[oeItemId]) {
          return existingRows[oeItemId]
        }
        const oeItem = this.oeOptions.find(item => item.oeItemId === oeItemId) || { oeItemId }
        return this.createAssetRow(oeItem)
      })
    },
    createAssetRow(oeItem, source = {}) {
      const row = {
        configId: source.configId,
        oeItemId: oeItem.oeItemId || source.oeItemId,
        oeItemCode: oeItem.oeItemCode || source.oeItemCode || "",
        oeItemName: oeItem.oeItemName || source.oeItemName || "",
        assetQuantity: Number(source.assetQuantity || 1),
        assetUnitPrice: Number(source.assetUnitPrice || oeItem.assetUnitPrice || oeItem.costPrice || 0),
        assetAmount: 0
      }
      this.recalculateAmount(row)
      return row
    },
    removeAssetRow(row) {
      this.selectedAssetRows = this.selectedAssetRows.filter(item => item.oeItemId !== row.oeItemId)
      this.selectedOeItemIds = this.selectedOeItemIds.filter(oeItemId => oeItemId !== row.oeItemId)
    },
    recalculateAmount(row) {
      if (!row) return
      const qty = Number(row.assetQuantity || 0)
      const price = Number(row.assetUnitPrice || 0)
      row.assetAmount = Number((qty * price).toFixed(2))
    },
    saveConfig() {
      this.$refs.configForm.validate(valid => {
        if (!valid) return
        if (!this.selectedAssetRows.length) {
          this.$modal.msgWarning("请选择固定资产OE器皿")
          return
        }
        const invalidRow = this.selectedAssetRows.find(row => Number(row.assetQuantity || 0) <= 0)
        if (invalidRow) {
          this.$modal.msgWarning("请输入有效的固定资产数量")
          return
        }
        this.saving = true
        this.saveAssetRows().then(() => {
          this.$modal.msgSuccess("固定资产配置已保存")
          this.configOpen = false
          this.getList()
        }).finally(() => {
          this.saving = false
        })
      })
    },
    saveAssetRows() {
      const selectedConfigIds = this.selectedAssetRows.map(row => row.configId).filter(Boolean)
      const selectedOeItemIds = this.selectedAssetRows.map(row => row.oeItemId).filter(Boolean)
      const removedRows = this.originalAssetRows.filter(row =>
        row.configId && selectedConfigIds.indexOf(row.configId) === -1 && selectedOeItemIds.indexOf(row.oeItemId) === -1
      )
      const deleteRequests = removedRows.map(row => () => deleteFixedAssetConfig(row.configId))
      const saveRequests = this.selectedAssetRows.map(row => () => saveFixedAssetConfig({
        configId: row.configId,
        shopDeptId: this.form.shopDeptId,
        oeItemId: row.oeItemId,
        oeItemName: row.oeItemName,
        assetQuantity: row.assetQuantity,
        assetUnitPrice: row.assetUnitPrice,
        assetAmount: row.assetAmount,
        annualRepairRatio: this.form.annualRepairRatio,
        status: this.form.status,
        remark: this.form.remark
      }))
      return this.runAssetRowRequestsSequentially(deleteRequests.concat(saveRequests))
    },
    runAssetRowRequestsSequentially(requests) {
      return requests.reduce((chain, request) => chain.then(() => request()), Promise.resolve())
    },
    removeStoreConfig(row) {
      const details = row && row.details ? row.details : []
      this.$modal.confirm("确认删除该店铺固定资产配置？将删除该店铺下所有固定资产明细。").then(() => {
        return Promise.all(details.filter(item => item.configId).map(item => deleteFixedAssetConfig(item.configId)))
      }).then(() => {
        this.$modal.msgSuccess("删除成功")
        this.getList()
      })
    },
    groupFixedAssetConfigs(rows) {
      const groups = []
      const groupMap = {}
      ;(rows || []).forEach(item => {
        const shopKey = item.shopDeptId || item.shopDeptName
        if (!shopKey) return
        if (!groupMap[shopKey]) {
          groupMap[shopKey] = {
            shopDeptId: item.shopDeptId,
            shopDeptName: item.shopDeptName || "-",
            detailCount: 0,
            assetTotalQuantity: 0,
            assetTotalAmount: 0,
            status: "0",
            remark: item.remark || "",
            details: []
          }
          groups.push(groupMap[shopKey])
        }
        const group = groupMap[shopKey]
        const detail = this.normalizeAssetDetail(item)
        group.details.push(detail)
        group.detailCount = group.details.length
        group.assetTotalQuantity = Number((group.assetTotalQuantity + Number(detail.assetQuantity || 0)).toFixed(2))
        group.assetTotalAmount = Number((group.assetTotalAmount + Number(detail.assetAmount || 0)).toFixed(2))
        group.status = this.resolveStoreStatus(group.details)
      })
      return groups
    },
    hydrateStoreQuotaRows(rows) {
      if (!rows || !rows.length) {
        return Promise.resolve([])
      }
      return Promise.all(rows.map(row =>
        getFixedAssetQuota({ shopDeptId: row.shopDeptId }).then(res => {
          const quota = res.data || {}
          return Object.assign({}, row, {
            availableQuotaAmount: Number(quota.availableQuotaAmount || 0),
            annualRepairRatio: Number(quota.annualRepairRatio || 0)
          })
        }).catch(() => Object.assign({}, row, {
          availableQuotaAmount: 0,
          annualRepairRatio: 0
        }))
      ))
    },
    normalizeAssetDetail(item) {
      const quantity = Number(item.assetQuantity || 0)
      const unitPrice = Number(item.assetUnitPrice || 0)
      const amount = Number(item.assetAmount || (quantity * unitPrice).toFixed(2))
      return Object.assign({}, item, {
        assetQuantity: quantity,
        assetUnitPrice: unitPrice,
        assetAmount: amount
      })
    },
    paginateStoreRows(rows) {
      const pageNum = Number(this.queryParams.pageNum || 1)
      const pageSize = Number(this.queryParams.pageSize || 10)
      const start = (pageNum - 1) * pageSize
      return rows.slice(start, start + pageSize)
    },
    findStoreConfigByShop(shopDeptId) {
      if (!shopDeptId) return null
      return this.allStoreConfigRows.find(row => String(row.shopDeptId) === String(shopDeptId))
    },
    resolveStoreStatus(details) {
      const rows = details || []
      if (!rows.length) return "1"
      if (rows.every(item => item.status === "0")) return "0"
      if (rows.some(item => item.status === "0")) return "partial"
      return "1"
    },
    handleExport() {
      this.download("oa/fixedAsset/config/export", { ...this.queryParams }, this.exportFileName("固定资产配置"))
    },
    money(value) {
      const numberValue = Number(value || 0)
      return "¥" + numberValue.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    quantity(value) {
      const numberValue = Number(value || 0)
      return numberValue.toLocaleString("zh-CN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
    },
    percent(value) {
      const numberValue = Number(value || 0)
      return numberValue.toFixed(2) + "%"
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 {
  margin-bottom: 12px;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.page-header h2 {
  margin: 4px 0;
  font-size: 20px;
}
.page-header p {
  margin: 0;
  color: #6b7280;
}
.section-eyebrow {
  color: var(--erp-primary, #0b6b53);
  font-size: 12px;
}
.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.dept-select {
  width: 320px;
  max-width: 100%;
}
.quota-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.quota-cell {
  min-height: 76px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  padding: 14px;
}
.quota-cell span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}
.quota-cell strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  color: #1f2937;
}
.quota-cell.warning strong {
  color: #d97706;
}
.muted-text {
  color: #909399;
  font-size: 12px;
}
.danger-text {
  color: #f56c6c;
}
.quota-helper {
  margin-top: 6px;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}
.quota-helper.danger {
  color: #f56c6c;
}
.asset-detail-drawer ::v-deep .el-drawer__body {
  height: 100%;
  padding: 0;
  background: #f5f7fb;
  overflow: auto;
}
.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.drawer-eyebrow {
  display: block;
  margin-bottom: 6px;
  color: var(--erp-primary, #0b6b53);
  font-size: 12px;
  line-height: 1;
}
.drawer-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.drawer-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 16px 24px 0;
}
.drawer-metric {
  min-height: 78px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  padding: 14px 16px;
}
.drawer-metric span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}
.drawer-metric strong {
  display: block;
  margin-top: 8px;
  color: #1f2937;
  font-size: 20px;
}
.drawer-table-card {
  margin: 16px 24px 24px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.drawer-table-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
  color: #1f2937;
  font-size: 14px;
}
.drawer-table-title strong {
  color: #6b7280;
  font-weight: 500;
}
.drawer-table-card ::v-deep .el-table {
  border-left: 0;
  border-right: 0;
}
.drawer-table-card ::v-deep .el-table th {
  background: #f8fafc;
  color: #374151;
}
.drawer-table-card ::v-deep .el-table td,
.drawer-table-card ::v-deep .el-table th {
  padding: 9px 0;
}
.asset-detail-table {
  width: 100%;
}
.asset-detail-table ::v-deep .el-input-number {
  width: 118px;
}
.asset-detail-empty {
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  color: #909399;
  line-height: 42px;
  padding: 0 12px;
}
@media (max-width: 1200px) {
  .quota-grid {
    grid-template-columns: repeat(3, minmax(140px, 1fr));
  }
}
@media (max-width: 768px) {
  .page-header {
    display: block;
  }
  .header-actions {
    justify-content: flex-start;
    margin-top: 12px;
  }
  .quota-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
