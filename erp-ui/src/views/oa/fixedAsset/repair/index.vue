<template>
  <div class="app-container oa-workspace-page fixed-asset-repair-page">
    <section class="oa-page-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 资产上报</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-s-tools" /></span>
          <div>
            <h1>固定资产维修上报</h1>
            <p>额度内破损可上报并记入维修额度；超过额度请按同款参考自行购买且无需上报。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__actions">
        <el-button type="primary" size="mini" icon="el-icon-plus" :disabled="repairActionDisabled" @click="openRepairForm" v-hasPermi="['oa:fixedAsset:repair:add']">新建上报</el-button>
        <el-button size="mini" icon="el-icon-download" :disabled="!currentStoreDeptId" @click="handleExport" v-hasPermi="['oa:fixedAsset:repair:export']">导出</el-button>
      </div>
    </section>

    <el-card shadow="never" class="search-card oa-filter-card mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="当前门店">
          <el-tag v-if="currentStoreDeptId" type="success">{{ currentStoreName }}</el-tag>
          <el-tag v-else type="warning">请切换到门店</el-tag>
        </el-form-item>
        <el-form-item label="资产">
          <el-input v-model="queryParams.oeItemName" placeholder="固定资产OE器皿" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部" @change="handleQuery">
            <el-option label="历史待确认（只读）" value="pending_confirm" />
            <el-option label="已上报" value="submitted" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="repairActionDisabled"
      :title="repairActionAlert.title"
      :description="repairActionAlert.description"
      type="warning"
      show-icon
      :closable="false"
      class="mb12"
    />

    <section class="repair-summary">
      <div class="oa-metric-card">
        <span>上报记录</span>
        <strong>{{ total }}</strong>
      </div>
      <div class="oa-metric-card oa-metric-card--warning">
        <span>历史待确认</span>
        <strong>{{ pendingConfirmCount }}</strong>
      </div>
      <div class="oa-metric-card oa-metric-card--success">
        <span>已上报</span>
        <strong>{{ submittedCount }}</strong>
      </div>
      <div class="oa-metric-card oa-metric-card--danger">
        <span>已驳回/取消</span>
        <strong>{{ closedCount }}</strong>
      </div>
    </section>

    <el-card shadow="never" class="table-card oa-table-card">
      <div slot="header" class="oa-card-heading">
        <div class="oa-card-heading__title">
          <span class="oa-card-heading__icon"><i class="el-icon-document" /></span>
          <div>
            <h2>维修上报记录</h2>
            <p>查看资产、额度占用与处理结果</p>
          </div>
        </div>
        <el-tag v-if="currentStoreDeptId" size="small" type="success">{{ currentStoreName }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="list" border stripe :empty-text="fixedAssetRepairEmptyText">
        <el-table-column label="店铺" prop="shopDeptName" min-width="130" show-overflow-tooltip />
        <el-table-column label="固定资产" prop="oeItemName" min-width="170" show-overflow-tooltip />
        <el-table-column label="坏掉数量" prop="repairQuantity" width="100" align="right">
          <template slot-scope="scope">{{ quantity(scope.row.repairQuantity) }}</template>
        </el-table-column>
        <el-table-column label="上报类型" width="120">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.exceptionApproved === 'Y'" type="warning" size="mini">{{ exceptionLabel(scope.row.exceptionType) }}</el-tag>
            <el-tag v-else size="mini" type="success">正常上报</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="110">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" prop="applicantName" width="110" show-overflow-tooltip />
        <el-table-column label="批准人" prop="approvedBy" width="110" show-overflow-tooltip />
        <el-table-column label="批准时间" prop="approvedTime" width="160" show-overflow-tooltip />
        <el-table-column label="故障说明" prop="faultDescription" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="mini" @click="showDetail(scope.row)">详情</el-button>
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

    <el-dialog title="新建固定资产维修上报" :visible.sync="repairOpen" width="780px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请选择本店铺已配置的 OE 器皿，并填写破损情况。"
        class="mb12"
      />
      <el-form ref="repairForm" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="当前门店">
          <el-input :value="currentStoreName" disabled />
        </el-form-item>
        <el-form-item label="固定资产明细" required>
          <div class="repair-row-toolbar">
            <el-button size="mini" icon="el-icon-plus" @click="addRepairAssetRow">添加资产</el-button>
            <span>合计占用 {{ money(repairUsageAmount) }}</span>
          </div>
          <el-table :data="repairAssetRows" border size="mini" class="repair-asset-table" empty-text="请添加固定资产明细">
            <el-table-column label="固定资产" min-width="260">
              <template slot-scope="scope">
                <el-select
                  v-model="scope.row.oeItemId"
                  filterable
                  placeholder="选择本店铺已配置OE器皿"
                  style="width: 100%"
                  @change="syncAsset(scope.row)"
                >
                  <el-option
                    v-for="item in assetOptions"
                    :key="item.oeItemId"
                    :label="item.oeItemName + ' / ' + item.oeItemCode"
                    :value="item.oeItemId"
                    :disabled="isAssetOptionDisabled(item, scope.row)"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="坏掉数量" width="160" align="center">
              <template slot-scope="scope">
                <el-input-number
                  v-model="scope.row.repairQuantity"
                  :min="0.01"
                  :max="scope.row.assetQuantity || undefined"
                  :precision="2"
                  controls-position="right"
                  size="mini"
                  style="width: 130px"
                />
              </template>
            </el-table-column>
            <el-table-column label="占用额度" width="120" align="right">
              <template slot-scope="scope">{{ money(repairRowUsage(scope.row)) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template slot-scope="scope">
                <el-button type="text" size="mini" :disabled="repairAssetRows.length === 1" @click="removeRepairAssetRow(scope.$index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="quota-helper" :class="{ danger: repairQuotaExceeded }">
            当前明细合计占用额度 {{ money(repairUsageAmount) }}，当前可用额度 {{ money(quota.availableQuotaAmount) }}
          </div>
          <div v-if="repairQuotaExceeded" class="quota-helper danger">
            {{ overQuotaReferencesReady
              ? '当前可用额度不足，不能上报；请按下方同款参考自行购买且无需上报。'
              : '当前可用额度不足，不能上报；同款资料尚未完善，请联系仓库。' }}
          </div>
          <div v-if="repairQuotaExceeded" class="purchase-reference-list">
            <div v-for="item in overQuotaPurchaseItems" :key="item.oeItemId" class="purchase-reference-card">
              <img v-if="item.imageUrl" :src="item.imageUrl" alt="同款OE图片">
              <div class="purchase-reference-content">
                <strong>{{ item.oeItemName || '同款OE器皿' }}</strong>
                <span>编码：{{ item.oeItemCode || '-' }} · 订货单位：{{ item.orderUnit || '-' }}</span>
                <p>{{ item.itemDescription || '暂无规格说明' }}</p>
                <p>购买说明：{{ item.purchaseReferenceNote || '待仓库维护' }}</p>
                <el-button
                  v-if="isPurchaseReferenceReady(item)"
                  type="text"
                  size="mini"
                  icon="el-icon-link"
                  @click="openPurchaseReference(item.purchaseReferenceUrl)"
                >打开同款购买页面</el-button>
                <el-tag v-else type="warning" size="mini">同款资料待完善，请联系仓库</el-tag>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="破损说明" prop="faultDescription">
          <el-input v-model="form.faultDescription" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="图片/附件">
          <image-upload v-model="form.imageUrls" :limit="5" :file-size="5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="repairOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="repairQuotaExceeded || repairActionDisabled" @click="submitRepair">上报</el-button>
      </div>
    </el-dialog>

    <el-dialog title="维修上报详情" :visible.sync="detailOpen" width="560px" append-to-body>
      <div class="detail-list">
        <div><span>固定资产</span><strong>{{ detail.oeItemName || '-' }}</strong></div>
        <div><span>坏掉数量</span><strong>{{ quantity(detail.repairQuantity) }}</strong></div>
        <div><span>状态</span><strong>{{ statusLabel(detail.status) }}</strong></div>
        <div><span>历史异常批准</span><strong>{{ detail.exceptionApproved === 'Y' ? exceptionLabel(detail.exceptionType) : '否' }}</strong></div>
        <div><span>申请人</span><strong>{{ detail.applicantName || '-' }}</strong></div>
        <div><span>批准人</span><strong>{{ detail.approvedBy || '-' }}</strong></div>
        <div><span>批准时间</span><strong>{{ detail.approvedTime || '-' }}</strong></div>
        <div class="full"><span>破损说明</span><p>{{ detail.faultDescription || '-' }}</p></div>
        <div class="full"><span>附件</span><p>{{ detail.imageUrls || '-' }}</p></div>
      </div>
      <div slot="footer">
        <el-button type="primary" @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getFixedAssetQuota, getFixedAssetRepair, listFixedAssetConfigs, listFixedAssetRepairs, submitFixedAssetRepairBatch } from "@/api/oa/fixedAsset"
import ImageUpload from "@/components/ImageUpload"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { getSelectedDeptContext } from "@/utils/shopContext"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")
const resolveSelectedDeptContext = typeof getSelectedDeptContext === "function"
  ? getSelectedDeptContext
  : () => ({})

export default {
  name: "OaFixedAssetRepair",
  components: { ImageUpload },
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "fixedAssetRepair",
    loadFocusedRow(repairId) { return getFixedAssetRepair(repairId) }
  })],
  data() {
    const selectedContext = resolveSelectedDeptContext()
    const defaultShopDeptId = selectedContext.isStore ? selectedContext.deptId : undefined
    return {
      loading: false,
      saving: false,
      repairOpen: false,
      detailOpen: false,
      total: 0,
      list: [],
      assetOptions: [],
      repairAssetRows: [],
      quota: {},
      detail: {},
      queryParams: { pageNum: 1, pageSize: 10, shopDeptId: defaultShopDeptId, oeItemName: undefined, status: undefined },
      form: {},
      rules: {
        faultDescription: [{ required: true, message: "请输入破损说明", trigger: "blur" }]
      }
    }
  },
  computed: {
    selectedDeptContext() {
      return resolveSelectedDeptContext()
    },
    currentStoreDeptId() {
      return this.selectedDeptContext.isStore ? this.selectedDeptContext.deptId : undefined
    },
    currentStoreName() {
      return this.selectedDeptContext.deptName || "当前门店"
    },
    repairActionDisabled() {
      return !this.currentStoreDeptId
    },
    repairActionAlert() {
      if (!this.currentStoreDeptId) {
        return {
          title: "请先切换到门店并维护固定资产配置",
          description: "维修上报只能由当前门店发起，并且当前门店需要存在可用固定资产明细。"
        }
      }
      return null
    },
    fixedAssetRepairEmptyText() {
      return getBusinessEmptyText("fixedAssetRepair", !this.currentStoreDeptId ? "missingContext" : "missingBaseline")
    },
    pendingConfirmCount() {
      return this.list.filter(item => item.status === "pending_confirm").length
    },
    submittedCount() {
      return this.list.filter(item => item.status === "submitted").length
    },
    closedCount() {
      return this.list.filter(item => item.status === "rejected" || item.status === "cancelled").length
    },
    repairUsageAmount() {
      return Number(this.repairAssetRows.reduce((total, row) => total + this.repairRowUsage(row), 0).toFixed(2))
    },
    repairQuotaExceeded() {
      return this.repairUsageAmount > Number(this.quota.availableQuotaAmount || 0)
    },
    overQuotaPurchaseItems() {
      const selectedIds = this.repairAssetRows.map(row => row.oeItemId).filter(Boolean)
      return this.assetOptions.filter(item => selectedIds.includes(item.oeItemId))
    },
    overQuotaReferencesReady() {
      return this.overQuotaPurchaseItems.length > 0 && this.overQuotaPurchaseItems.every(this.isPurchaseReferenceReady)
    }
  },
  created() {
    this.getList()
  },
  methods: {
    effectiveShopDeptId() {
      return this.currentStoreDeptId
    },
    effectiveQueryParams() {
      return Object.assign({}, this.queryParams, { shopDeptId: this.effectiveShopDeptId() })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, shopDeptId: this.effectiveShopDeptId(), oeItemName: undefined, status: undefined }
      this.getList()
    },
    getList() {
      if (!this.currentStoreDeptId) {
        this.list = []
        this.total = 0
        this.quota = {}
        return this.handleTodoFocusRows([])
      }
      this.loading = true
      const params = this.effectiveQueryParams()
      this.queryParams.shopDeptId = params.shopDeptId
      return this.loadTodoBusinessList(() => listFixedAssetRepairs(params)).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        this.loadQuota(params.shopDeptId)
        return this.handleTodoFocusRows(this.list)
      }).finally(() => {
        this.loading = false
      })
    },
    loadQuota(shopDeptId) {
      shopDeptId = shopDeptId || this.effectiveShopDeptId()
      getFixedAssetQuota({ shopDeptId }).then(res => {
        this.quota = res.data || {}
      }).catch(() => {
        this.quota = {}
      })
    },
    openRepairForm() {
      if (this.repairActionDisabled) {
        this.$modal.msgWarning(this.repairActionAlert.title)
        return
      }
      const shopDeptId = this.effectiveShopDeptId()
      this.form = {
        shopDeptId,
        faultDescription: "",
        imageUrls: "",
        remark: ""
      }
      this.repairAssetRows = [this.createRepairAssetRow()]
      this.loadAssets(shopDeptId)
      this.loadQuota(shopDeptId)
      this.repairOpen = true
    },
    loadAssets(shopDeptId) {
      shopDeptId = shopDeptId || this.form.shopDeptId || this.effectiveShopDeptId()
      if (!shopDeptId) {
        this.assetOptions = []
        return
      }
      listFixedAssetConfigs({ pageNum: 1, pageSize: 100, shopDeptId, status: "0" }).then(res => {
        this.assetOptions = res.rows || []
      })
    },
    createRepairAssetRow(asset) {
      return {
        oeItemId: asset && asset.oeItemId ? asset.oeItemId : undefined,
        oeItemCode: asset && asset.oeItemCode ? asset.oeItemCode : "",
        oeItemName: asset && asset.oeItemName ? asset.oeItemName : "",
        assetQuantity: asset && asset.assetQuantity ? asset.assetQuantity : undefined,
        assetUnitPrice: asset && asset.assetUnitPrice ? asset.assetUnitPrice : 0,
        repairQuantity: 1
      }
    },
    addRepairAssetRow() {
      this.repairAssetRows.push(this.createRepairAssetRow())
    },
    removeRepairAssetRow(index) {
      if (this.repairAssetRows.length <= 1) return
      this.repairAssetRows.splice(index, 1)
    },
    syncAsset(row) {
      const asset = this.assetOptions.find(item => item.oeItemId === row.oeItemId)
      if (asset) {
        row.oeItemCode = asset.oeItemCode
        row.oeItemName = asset.oeItemName
        row.assetQuantity = asset.assetQuantity
        row.assetUnitPrice = asset.assetUnitPrice
        const maxQuantity = Number(asset.assetQuantity || 0)
        if (maxQuantity > 0 && Number(row.repairQuantity || 0) > maxQuantity) {
          row.repairQuantity = maxQuantity
        }
      }
    },
    isAssetOptionDisabled(asset, currentRow) {
      return this.repairAssetRows.some(row => row !== currentRow && row.oeItemId === asset.oeItemId)
    },
    repairRowUsage(row) {
      return Number((Number(row.assetUnitPrice || 0) * Number(row.repairQuantity || 0)).toFixed(2))
    },
    validateRepairAssetRows() {
      if (!this.repairAssetRows.length) {
        this.$modal.msgWarning("请添加固定资产明细")
        return false
      }
      const selectedIds = []
      for (const row of this.repairAssetRows) {
        if (!row.oeItemId) {
          this.$modal.msgWarning("请选择固定资产")
          return false
        }
        if (Number(row.repairQuantity || 0) <= 0) {
          this.$modal.msgWarning("坏掉数量必须大于0")
          return false
        }
        if (Number(row.assetQuantity || 0) > 0 && Number(row.repairQuantity || 0) > Number(row.assetQuantity || 0)) {
          this.$modal.msgWarning("坏掉数量不能超过店铺配置数量")
          return false
        }
        if (selectedIds.includes(row.oeItemId)) {
          this.$modal.msgWarning("同一固定资产请合并坏掉数量后再上报")
          return false
        }
        selectedIds.push(row.oeItemId)
      }
      return true
    },
    submitRepair() {
      if (this.repairActionDisabled) {
        this.$modal.msgWarning(this.repairActionAlert.title)
        return
      }
      this.$refs.repairForm.validate(valid => {
        if (!valid) return
        if (!this.validateRepairAssetRows()) return
        if (this.repairQuotaExceeded) {
          this.$modal.msgWarning(this.overQuotaReferencesReady
            ? "当前可用额度不足，请按同款参考自行购买且无需上报"
            : "当前可用额度不足，同款资料尚未完善，请联系仓库")
          return
        }
        this.saving = true
        const payload = Object.assign({}, this.form, {
          items: this.repairAssetRows.map(row => ({
            oeItemId: row.oeItemId,
            repairQuantity: row.repairQuantity
          }))
        })
        submitFixedAssetRepairBatch(payload).then(() => {
          this.$modal.msgSuccess("固定资产维修上报成功")
          this.repairOpen = false
          this.getList()
        }).catch(error => {
          const responseData = error && error.response ? error.response.data : null
          if (responseData && responseData.errorCode === "FIXED_ASSET_QUOTA_EXCEEDED") {
            this.loadQuota(this.form.shopDeptId)
            const precheck = responseData.precheck || {}
            this.$modal.msgWarning(precheck.message || "额度已被其他上报占用，不能继续上报")
          }
          return Promise.reject(error)
        }).finally(() => {
          this.saving = false
        })
      })
    },
    showDetail(row) {
      this.detail = Object.assign({}, row)
      this.detailOpen = true
    },
    handleExport() {
      this.download("oa/fixedAsset/repair/export", this.effectiveQueryParams(), this.exportFileName("固定资产维修上报"))
    },
    exceptionLabel(type) {
      if (type === "special_extra") return "历史超额批准"
      return "历史批准"
    },
    statusLabel(status) {
      const labels = {
        draft: "草稿",
        pending_confirm: "历史待确认（只读）",
        submitted: "已上报",
        rejected: "已驳回",
        cancelled: "已取消"
      }
      return labels[status] || (status ? "未知状态" : "-")
    },
    statusTag(status) {
      if (status === "pending_confirm") return "warning"
      if (status === "submitted") return "success"
      if (status === "rejected") return "danger"
      return "info"
    },
    money(value) {
      const numberValue = Number(value || 0)
      return "¥" + numberValue.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    quantity(value) {
      if (value === undefined || value === null || value === "" || Number(value) <= 0) {
        return "-"
      }
      const numberValue = Number(value || 0)
      return numberValue.toLocaleString("zh-CN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
    },
    openPurchaseReference(url) {
      if (!url || !/^https:\/\//i.test(url)) {
        this.$modal.msgWarning("购买链接不可用，请联系仓库维护")
        return
      }
      const opened = window.open(url, "_blank", "noopener,noreferrer")
      if (opened) opened.opener = null
    },
    isPurchaseReferenceReady(item) {
      if (!item) return false
      return !!(item.oeItemCode && item.oeItemName && item.itemDescription && item.orderUnit && item.imageUrl &&
        item.purchaseReferenceNote && /^https:\/\//i.test(item.purchaseReferenceUrl || ""))
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
  gap: 8px;
}
.repair-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.repair-summary > div {
  min-height: 72px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  padding: 14px;
}
.repair-summary span,
.detail-list span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}
.repair-summary strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
  color: #1f2937;
}
.detail-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.detail-list .full {
  grid-column: 1 / -1;
}
.detail-list strong,
.detail-list p {
  margin: 6px 0 0;
  color: #1f2937;
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
.repair-row-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #6b7280;
  font-size: 12px;
}
.repair-asset-table {
  width: 100%;
}
.purchase-reference-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}
.purchase-reference-card {
  display: flex;
  gap: 12px;
  padding: 10px;
  border: 1px solid #f5c2c7;
  border-radius: 6px;
  background: #fff8f8;
}
.purchase-reference-card img {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  object-fit: cover;
}
.purchase-reference-content {
  min-width: 0;
}
.purchase-reference-content span,
.purchase-reference-content p {
  display: block;
  margin: 5px 0 0;
  color: #6b7280;
  font-size: 12px;
}
@media (max-width: 900px) {
  .repair-summary {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
}
@media (max-width: 768px) {
  .page-header {
    display: block;
  }
  .header-actions {
    margin-top: 12px;
  }
}
</style>
