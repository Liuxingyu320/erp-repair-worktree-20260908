<template>
  <div class="app-container transfer-records-page">
    <inventory-page-hero
      title="调拨记录"
      eyebrow="作业归档"
      description="集中查询已完成、已关闭或已取消的调拨，快速回看审批、收发与差异处理结果。"
      scope-text="当前组织历史记录"
      icon="el-icon-notebook-2"
      tone="slate"
      :features="['审批轨迹', '收发结果', '差异归档']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="单号">
          <el-input v-model="queryParams.orderNo" placeholder="调拨单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.transferType" clearable placeholder="全部">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:transfer:records']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:transfer:records:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="record-toolbar">
        <el-radio-group v-model="queryParams.status" size="mini" @change="handleStatusTab">
          <el-radio-button label="">全部记录</el-radio-button>
          <el-radio-button v-for="item in statusOptions" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
        </el-radio-group>
        <span class="record-total">共 {{ total }} 条主调拨记录</span>
      </div>
      <el-table v-loading="loading" :data="list" size="small">
        <el-table-column label="调拨单号" prop="orderNo" width="170" fixed="left"/>
        <el-table-column label="要货门店" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.toDeptName) }}</template>
        </el-table-column>
        <el-table-column label="要货仓库" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ displayValue(scope.row.fromDeptName) }}</template>
        </el-table-column>
        <el-table-column label="类型" prop="transferType" width="110">
          <template slot-scope="scope">{{ typeLabel(scope.row.transferType) }}</template>
        </el-table-column>
        <el-table-column label="申请数量" prop="totalQuantity" width="100" align="right">
          <template slot-scope="scope">{{ formatQuantity(scope.row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column label="参考总价" prop="totalAmount" width="120" align="right">
          <template slot-scope="scope">{{ formatMoney(scope.row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态 / 审批结果" prop="status" width="230" align="center">
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
            <div v-if="scope.row.approvalSummary" class="approval-summary">
              {{ scope.row.approvalSummary.summaryText }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template slot-scope="scope">{{ formatLocalTime(scope.row.submittedTime) }}</template>
        </el-table-column>
        <el-table-column label="审核通过" width="170">
          <template slot-scope="scope">{{ formatLocalTime(scope.row.approvedTime) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template slot-scope="scope">{{ formatLocalTime(scope.row.receivedTime) }}</template>
        </el-table-column>
        <el-table-column label="归档时间" width="170">
          <template slot-scope="scope">{{ formatLocalTime(scope.row.archivedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:transfer:records:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row.transferId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog title="调拨记录详情" :visible.sync="detailOpen" width="920px" append-to-body>
      <div v-if="detail.transferId">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="mini">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detail.transferType) }}</el-descriptions-item>
          <el-descriptions-item label="要货门店">{{ displayValue(detail.toDeptName) }}</el-descriptions-item>
          <el-descriptions-item label="要货仓库">{{ displayValue(detail.fromDeptName) }}</el-descriptions-item>
          <el-descriptions-item label="申请数量">{{ formatQuantity(detail.totalQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="参考总价">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="审核通过">{{ formatLocalTime(detail.approvedTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatLocalTime(detail.receivedTime) }}</el-descriptions-item>
          <el-descriptions-item label="归档时间">{{ formatLocalTime(detail.archivedTime) }}</el-descriptions-item>
          <el-descriptions-item label="收件人">{{ displayValue(detail.recipientName) }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ displayValue(detail.recipientPhone) }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="3">{{ displayValue(detail.shippingAddress) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">审批进度</el-divider>
        <transfer-approval-progress
          :track="approvalTrack"
          :loading="approvalTrackLoading"
          :error="approvalTrackError"
          @retry="loadApprovalTrack(detail.transferId)"
        />
        <el-divider content-position="left">要货明细</el-divider>
        <el-table :data="detail.details || []" size="small" border show-summary :summary-method="getDetailSummary">
          <el-table-column label="商品" prop="productName" min-width="160"/>
          <el-table-column label="编码" prop="productCode" width="120"/>
          <el-table-column label="申请数量" prop="quantity" width="100" align="right">
            <template slot-scope="scope">{{ formatQuantity(scope.row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="参考成本价" prop="costPrice" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(scope.row.costPrice) }}</template>
          </el-table-column>
          <el-table-column label="小计" prop="amount" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(lineAmount(scope.row)) }}</template>
          </el-table-column>
          <el-table-column label="已发货" prop="deliveredQuantity" width="100" align="right">
            <template slot-scope="scope">{{ formatQuantity(scope.row.deliveredQuantity) }}</template>
          </el-table-column>
          <el-table-column label="已收货" prop="receivedQuantity" width="100" align="right">
            <template slot-scope="scope">{{ formatQuantity(scope.row.receivedQuantity) }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="90"/>
          <el-table-column label="规格" prop="spec" min-width="120"/>
        </el-table>
        <div class="detail-total-bar">
          <span>明细合计</span>
          <strong>{{ formatMoney(detailTotalAmount(detail)) }}</strong>
        </div>
        <el-divider content-position="left">发货与收货批次</el-divider>
        <el-table :data="shipmentRows" size="small" border>
          <el-table-column label="批次号" min-width="150">
            <template slot-scope="scope">{{ displayValue(scope.row.shipmentNo || scope.row.batchNo) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template slot-scope="scope">{{ shipmentStatusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column label="发货人" width="110">
            <template slot-scope="scope">{{ displayValue(scope.row.shippedBy || scope.row.deliveredBy) }}</template>
          </el-table-column>
          <el-table-column label="发货时间" width="160">
            <template slot-scope="scope">{{ formatLocalTime(scope.row.shippedTime || scope.row.deliveredTime) }}</template>
          </el-table-column>
          <el-table-column label="收货人" width="110">
            <template slot-scope="scope">{{ displayValue(scope.row.receivedBy) }}</template>
          </el-table-column>
          <el-table-column label="收货时间" width="160">
            <template slot-scope="scope">{{ formatLocalTime(scope.row.receivedTime) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div slot="footer">
        <el-button @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTransferRecords, getTransferDetail, getTransferApprovalTrack } from "@/api/inventory/transfer"
import TransferApprovalProgress from "@/views/inventory/transfer/components/TransferApprovalProgress"

export default {
  name: "InvTransferRecords",
  components: { TransferApprovalProgress },
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      detailOpen: false,
      detail: {},
      approvalTrack: null,
      approvalTrackLoading: false,
      approvalTrackError: false,
      approvalTrackTransferId: null,
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, status: undefined, transferType: undefined },
      statusOptions: [
        { label: "已完成", value: "received" },
        { label: "已关闭", value: "closed" },
        { label: "已取消", value: "cancelled" },
        { label: "已驳回", value: "rejected" }
      ],
      typeOptions: [
        { label: "门店要货", value: "warehouse" },
        { label: "门店返仓", value: "store_return" },
        { label: "异店调货", value: "cross_store" }
      ]
    }
  },
  computed: {
    shipmentRows() {
      return this.detail.shipments || this.detail.shipmentBatches || this.detail.shipmentList || []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listTransferRecords(this.queryParams).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => {
        this.loading = false
      })
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: this.queryParams.pageSize || 10, orderNo: undefined, status: undefined, transferType: undefined }
      this.getList()
    },
    handleStatusTab() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    statusType(status) {
      const map = { received: "success", closed: "info", cancelled: "danger", rejected: "warning" }
      return map[status] || "info"
    },
    statusLabel(status) {
      const item = this.statusOptions.find(option => option.value === status)
      return item ? item.label : "未知调拨状态"
    },
    shipmentStatusLabel(status) {
      const map = { pending_receive: "待收货", received: "已收货", abnormal: "异常" }
      return map[status] || "未知发货状态"
    },
    typeLabel(type) {
      const item = this.typeOptions.find(option => option.value === type)
      return item ? item.label : "其他调拨类型"
    },
    openDetail(transferId) {
      this.detail = {}
      this.approvalTrackTransferId = transferId
      this.loadApprovalTrack(transferId)
      getTransferDetail(transferId).then(res => {
        if (String(this.approvalTrackTransferId) === String(transferId)) {
          this.detail = res.data || {}
          this.detailOpen = true
        }
      })
    },
    loadApprovalTrack(transferId) {
      if (!transferId) return
      this.approvalTrackTransferId = transferId
      this.approvalTrack = null
      this.approvalTrackError = false
      this.approvalTrackLoading = true
      getTransferApprovalTrack(transferId).then(res => {
        if (String(this.approvalTrackTransferId) === String(transferId)) {
          this.approvalTrack = res.data || null
        }
      }).catch(() => {
        if (String(this.approvalTrackTransferId) === String(transferId)) {
          this.approvalTrackError = true
        }
      }).finally(() => {
        if (String(this.approvalTrackTransferId) === String(transferId)) {
          this.approvalTrackLoading = false
        }
      })
    },
    handleExport() {
      this.$modal.confirm("确认导出当前查询条件下的调拨记录数据？").then(() => {
        this.download("inventory/transfer/records/export", { ...this.queryParams }, this.exportFileName("调拨记录数据"))
      })
    },
    formatQuantity(value) {
      if (value === undefined || value === null || value === "") return "-"
      const num = Number(value)
      if (Number.isNaN(num)) return value
      return Number.isInteger(num) ? String(num) : num.toFixed(2)
    },
    formatMoney(value) {
      if (value === undefined || value === null || value === "") return "-"
      const num = Number(value)
      return Number.isNaN(num) ? value : num.toFixed(2)
    },
    lineAmount(item) {
      const amount = this.optionalNumber(item && item.amount)
      const quantity = this.optionalNumber(item && item.quantity)
      const costPrice = this.optionalNumber(item && item.costPrice)
      if (quantity !== null && costPrice !== null) {
        return Number((quantity * costPrice).toFixed(2))
      }
      return amount
    },
    sumDetailAmounts(details) {
      return (details || []).reduce((sum, item) => {
        const amount = this.lineAmount(item)
        return amount === null ? sum : sum + amount
      }, 0)
    },
    detailTotalAmount(detail) {
      const total = this.optionalNumber(detail && detail.totalAmount)
      if (total !== null) return total
      return this.sumDetailAmounts(detail && detail.details)
    },
    getDetailSummary({ columns, data }) {
      return columns.map((column, index) => {
        if (index === 0) return "合计"
        if (column.property === "quantity" || column.property === "deliveredQuantity" || column.property === "receivedQuantity") {
          const total = (data || []).reduce((sum, item) => sum + this.toNumber(item[column.property]), 0)
          return this.formatQuantity(total)
        }
        if (column.property === "amount") {
          return this.formatMoney(this.sumDetailAmounts(data))
        }
        return ""
      })
    },
    optionalNumber(value) {
      if (value === undefined || value === null || value === "") return null
      const num = Number(value)
      return Number.isNaN(num) ? null : num
    },
    toNumber(value) {
      const num = Number(value || 0)
      return Number.isNaN(num) ? 0 : num
    },
    displayValue(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    formatLocalTime(value) {
      return value ? this.parseTime(value, "{y}-{m}-{d} {h}:{i}:{s}") : "-"
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 { margin-bottom: 12px; }

.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.record-total {
  color: #909399;
  font-size: 12px;
}

.detail-total-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
}

.detail-total-bar strong {
  color: #303133;
  font-size: 15px;
}

.approval-summary {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
  text-align: left;
  overflow-wrap: anywhere;
}
</style>
