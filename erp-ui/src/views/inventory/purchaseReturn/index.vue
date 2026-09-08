<template>
  <div class="app-container warehouse-page">
    <inventory-page-hero
      title="采购退货"
      eyebrow="出库作业"
      description="跟踪退货申请、可退数量与仓库出库，确保每一笔供应商退货都有据可查。"
      scope-text="按当前仓库退货"
      icon="el-icon-refresh-left"
      tone="rose"
      :features="['退货申请', '数量核对', '出库确认']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="退货单号">
          <el-input v-model="queryParams.returnNo" placeholder="退货单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="原采购单号">
          <el-input v-model="queryParams.purchaseOrderNo" placeholder="采购单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="草稿" value="draft"/>
            <el-option label="已提交" value="submitted"/>
            <el-option label="已退货" value="returned"/>
            <el-option label="已取消" value="cancelled"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:purchaseReturn:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button v-if="isWarehouseContext" v-hasPermi="['inv:purchaseReturn:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">新增退货单</el-button>
          <el-button v-hasPermi="['inv:purchaseReturn:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small" :empty-text="purchaseReturnEmptyText">
        <el-table-column label="退货单号" prop="returnNo" width="160"/>
        <el-table-column label="原采购单号" prop="purchaseOrderNo" width="160"/>
        <el-table-column label="供应商" prop="supplierName" min-width="140"/>
        <el-table-column label="退货金额" prop="totalAmount" width="120" align="right"/>
        <el-table-column label="状态" prop="status" width="90">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" prop="applicantName" width="100"/>
        <el-table-column label="创建时间" prop="createTime" width="160"/>
        <el-table-column label="操作" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:purchaseReturn:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
            <el-button v-if="isWarehouseContext && scope.row.status === 'draft'" v-hasPermi="['inv:purchaseReturn:add']" type="text" size="mini" icon="el-icon-edit" @click="openForm(scope.row)">编辑</el-button>
            <el-button v-if="isWarehouseContext && scope.row.status === 'draft'" v-hasPermi="['inv:purchaseReturn:submit']" type="text" size="mini" icon="el-icon-upload" @click="handleSubmit(scope.row)">提交</el-button>
            <el-button v-if="isWarehouseContext && scope.row.status === 'submitted'" v-hasPermi="['inv:purchaseReturn:confirm']" type="text" size="mini" class="text-warning" icon="el-icon-check" @click="handleConfirm(scope.row)">确认退货</el-button>
            <el-button v-if="isWarehouseContext && (scope.row.status === 'draft' || scope.row.status === 'submitted')" v-hasPermi="['inv:purchaseReturn:remove']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="handleCancel(scope.row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="formTitle" :visible.sync="dialogOpen" width="800px" append-to-body :close-on-click-modal="false" top="3vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="原采购单号" prop="purchaseOrderId">
              <el-select
                v-model="form.purchaseOrderId"
                filterable
                remote
                reserve-keyword
                :remote-method="queryPurchaseOrders"
                :loading="orderLoading"
                :disabled="!!form.returnId"
                placeholder="请选择原采购单"
                style="width:100%"
                @change="handlePurchaseOrderChange"
              >
                <el-option
                  v-for="item in orderOptions"
                  :key="item.orderId"
                  :label="item.orderNo + (item.supplierName ? ' / ' + item.supplierName : '')"
                  :value="item.orderId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker v-model="form.returnDate" type="date" value-format="yyyy-MM-dd" placeholder="选择日期" style="width:100%"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="退货主题" prop="returnTitle">
              <el-input v-model="form.returnTitle" maxlength="128"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" disabled placeholder="选择原采购单后自动带出"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="退货原因" prop="returnReason">
              <el-input v-model="form.returnReason" type="textarea" :rows="2" maxlength="500" show-word-limit/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任归属" prop="responsibility">
              <el-select v-model="form.responsibility" placeholder="请选择" style="width:100%">
                <el-option label="供应商" value="supplier"/>
                <el-option label="仓库" value="warehouse"/>
                <el-option label="运输" value="transport"/>
                <el-option label="其他" value="other"/>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图片/附件">
          <file-upload v-model="form.attachmentUrls" :limit="5" :file-size="10" :file-type="['jpg', 'jpeg', 'png', 'pdf', 'doc', 'docx']"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2"/>
        </el-form-item>

        <el-divider content-position="left">退货明细</el-divider>
        <div class="return-batch-actions">
          <span>退货数量默认为 0，可按需录入或一键填入全部可退数量。</span>
          <div>
            <el-button size="mini" type="primary" plain @click="fillAllReturnable">退全部可退数量</el-button>
            <el-button size="mini" @click="clearReturnQuantities">清空</el-button>
          </div>
        </div>
        <el-button type="primary" size="small" icon="el-icon-refresh" :disabled="!form.purchaseOrderId || !!form.returnId" @click="reloadPurchaseOrderDetails" style="margin-bottom:8px">重新载入原单明细</el-button>
        <el-table :data="form.details" size="small" border>
          <el-table-column label="商品名称" prop="productName" min-width="140">
            <template slot-scope="scope">
              <span>{{ scope.row.productName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="SKU" prop="sku" width="100">
            <template slot-scope="scope">
              <span>{{ scope.row.sku }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规格" prop="spec" width="100">
            <template slot-scope="scope">
              <span>{{ scope.row.spec }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="70">
            <template slot-scope="scope">
              <span>{{ scope.row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="100">
            <template slot-scope="scope">
              <el-input-number v-model="scope.row.quantity" :min="0" :max="scope.row.maxReturnQuantity" :precision="2" size="small" style="width:100%"/>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="110">
            <template slot-scope="scope">
              <span>{{ formatMoney(scope.row.unitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(toNumber(scope.row.quantity) * toNumber(scope.row.unitPrice)) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-delete" class="text-danger" @click="removeDetail(scope.$index)"/>
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:8px;font-size:14px">退货总金额：<b style="color:#F56C6C">{{ totalAmount }}</b></div>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:purchaseReturn:add']" type="primary" @click="doSave">保存草稿</el-button>
        <el-button v-hasPermi="['inv:purchaseReturn:submit']" type="success" @click="doSubmit">保存并提交</el-button>
      </div>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="退货单详情" :visible.sync="detailOpen" width="700px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="退货单号">{{ detailForm.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="原采购单号">{{ detailForm.purchaseOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailForm.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="退货主题">{{ detailForm.returnTitle }}</el-descriptions-item>
        <el-descriptions-item label="退货总金额">{{ detailForm.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detailForm.status)" size="mini">{{ statusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detailForm.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailForm.createTime }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">退货明细</el-divider>
      <el-table :data="detailForm.details" size="small" border>
        <el-table-column label="商品名称" prop="productName"/>
        <el-table-column label="SKU" prop="sku" width="100"/>
        <el-table-column label="单位" prop="unit" width="70"/>
        <el-table-column label="数量" prop="quantity" width="100"/>
        <el-table-column label="已退" prop="returnedQuantity" width="100"/>
        <el-table-column label="单价" prop="unitPrice" width="100"/>
        <el-table-column label="金额" prop="amount" width="110"/>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { listPurchaseReturn, getPurchaseReturn, savePurchaseReturn, submitPurchaseReturn, confirmPurchaseReturn, cancelPurchaseReturn } from "@/api/inventory/purchaseReturn"
import { listPurchase, getPurchaseDetail } from "@/api/inventory/purchase"
import { isSelectedWarehouse } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "purchaseReturn",
    loadFocusedRow(returnId) { return getPurchaseReturn(returnId) },
    actions: {
      confirmPurchaseReturn(row) {
        if (!row || row.status !== "submitted") return this.showTodoBusinessHandled()
        this.handleConfirm(row)
      }
    }
  })],
  name: "InvPurchaseReturn",
  data() {
    return {
      loading: false, submitLoading: false, orderLoading: false, total: 0, list: [], orderOptions: [], dialogOpen: false, detailOpen: false,
      queryParams: { pageNum: 1, pageSize: 10, returnNo: undefined, purchaseOrderNo: undefined, supplierName: undefined, status: undefined },
      form: { returnId: undefined, returnNo: "", purchaseOrderId: undefined, purchaseOrderNo: "", returnTitle: "", supplierName: "", totalAmount: 0, returnDate: "", status: "draft", returnReason: "", responsibility: "", attachmentUrls: "", remark: "", details: [] },
      detailForm: { details: [] },
      rules: {
        returnTitle: [{ required: true, message: "退货主题不能为空", trigger: "blur" }],
        supplierName: [{ required: true, message: "供应商名称不能为空", trigger: "blur" }],
        returnDate: [{ required: true, message: "请选择退货日期", trigger: "change" }],
        purchaseOrderId: [{ required: true, message: "请选择原采购单", trigger: "change" }],
        returnReason: [{ required: true, message: "请填写退货原因", trigger: "blur" }],
        responsibility: [{ required: true, message: "请选择责任归属", trigger: "change" }]
      }
    }
  },
  computed: {
    isWarehouseContext() {
      return isSelectedWarehouse()
    },
    purchaseReturnEmptyText() {
      return getBusinessEmptyText("purchaseReturn", this.isWarehouseContext ? "missingBaseline" : "missingContext")
    },
    formTitle() { return this.form.returnId ? "编辑采购退货单" : "新增采购退货单" },
    totalAmount() {
      return this.form.details.reduce((sum, d) => sum + this.toNumber(d.quantity) * this.toNumber(d.unitPrice), 0).toFixed(2)
    }
  },
  created() { this.getList() },
  methods: {
    ensureWarehouseContext() {
      if (this.isWarehouseContext) {
        return true
      }
      this.$modal.msgError("请选择仓库后再操作采购退货")
      return false
    },
    getList() {
      if (!this.isWarehouseContext) {
        this.list = []
        this.total = 0
        return this.handleTodoFocusRows(this.list)
      }
      this.loading = true
      return this.loadTodoBusinessList(() => listPurchaseReturn(this.queryParams)).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).finally(() => { this.loading = false })
    },
    openForm(row) {
      if (!this.ensureWarehouseContext()) return
      if (row) {
        getPurchaseReturn(row.returnId).then(res => {
          this.form = Object.assign({}, res.data, {
            returnDate: res.data.returnDate || this.defaultReturnDate(),
            details: (res.data.details || []).map(item => Object.assign({}, item, {
              maxReturnQuantity: this.toNumber(item.returnableQuantity),
              quantity: this.toNumber(item.quantity)
            }))
          })
          if (this.form.purchaseOrderId) {
            this.orderOptions = [{ orderId: this.form.purchaseOrderId, orderNo: this.form.purchaseOrderNo, supplierName: this.form.supplierName }]
          }
          this.dialogOpen = true
        })
      } else {
        this.form = { returnId: undefined, returnNo: "", purchaseOrderId: undefined, purchaseOrderNo: "", returnTitle: "", supplierName: "", totalAmount: 0, returnDate: this.defaultReturnDate(), status: "draft", returnReason: "", responsibility: "", attachmentUrls: "", remark: "", details: [] }
        this.queryPurchaseOrders("")
        this.dialogOpen = true
      }
      this.$nextTick(() => { this.$refs.formRef && this.$refs.formRef.clearValidate() })
    },
    openDetail(row) {
      if (!this.ensureWarehouseContext()) return
      this.detailOpen = true
      getPurchaseReturn(row.returnId).then(res => { this.detailForm = res.data || { details: [] } })
    },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    queryPurchaseOrders(keyword) {
      if (!this.ensureWarehouseContext()) return
      this.orderLoading = true
      listPurchase({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined }).then(res => {
        this.orderOptions = (res.rows || []).filter(item => item.status !== "draft" && item.status !== "cancelled")
      }).finally(() => { this.orderLoading = false })
    },
    handlePurchaseOrderChange(orderId) {
      if (!orderId) {
        this.form.purchaseOrderNo = ""
        this.form.details = []
        return
      }
      this.loadPurchaseOrder(orderId)
    },
    reloadPurchaseOrderDetails() {
      if (this.form.purchaseOrderId) {
        this.loadPurchaseOrder(this.form.purchaseOrderId)
      }
    },
    loadPurchaseOrder(orderId) {
      getPurchaseDetail(orderId).then(res => {
        const order = res.data || {}
        this.form.purchaseOrderId = order.orderId
        this.form.purchaseOrderNo = order.orderNo
        this.form.supplierName = order.supplierName
        if (!this.form.returnTitle) {
          this.form.returnTitle = "采购退货-" + (order.orderNo || "")
        }
        this.form.details = this.buildReturnDetails(order.details || [])
        if (!this.form.details.length) {
          this.$modal.msgWarning("该采购单暂无可退明细")
        }
      })
    },
    buildReturnDetails(details) {
      return details.map(item => {
        const maxReturnQuantity = this.toNumber(item.returnableQuantity)
        return {
          purchaseDetailId: item.detailId,
          itemType: item.itemType || "product",
          itemId: item.itemId || item.productId,
          itemCode: item.itemCode || item.productCode || item.sku,
          itemName: item.itemName || item.productName,
          productId: item.productId,
          productName: item.productName,
          sku: item.sku,
          spec: item.spec,
          unit: item.unit,
          quantity: 0,
          maxReturnQuantity: maxReturnQuantity,
          unitPrice: this.toNumber(item.unitPrice),
          amount: 0,
          returnedQuantity: this.toNumber(item.historicalReturnedQuantity)
        }
      }).filter(item => item.maxReturnQuantity > 0)
    },
    doSave() {
      if (!this.ensureWarehouseContext()) return
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        if (!payload) return
        this.submitLoading = true
        savePurchaseReturn(payload).then(() => { this.$modal.msgSuccess("保存成功"); this.dialogOpen = false; this.getList() }).finally(() => { this.submitLoading = false })
      })
    },
    doSubmit() {
      if (!this.ensureWarehouseContext()) return
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        if (!payload) return
        this.submitLoading = true
        submitPurchaseReturn(payload).then(() => { this.$modal.msgSuccess("提交成功"); this.dialogOpen = false; this.getList() }).finally(() => { this.submitLoading = false })
      })
    },
    buildPayload() {
      if (!this.form.details || !this.form.details.length) {
        this.$modal.msgError("请先选择原采购单并保留至少一条退货明细")
        return null
      }
      const details = this.form.details.map(item => {
        const quantity = this.toNumber(item.quantity)
        const unitPrice = this.toNumber(item.unitPrice)
        return Object.assign({}, item, {
          quantity: quantity,
          unitPrice: unitPrice,
          amount: quantity * unitPrice
        })
      })
      const invalid = details.find(item => !(item.itemId || item.productId) || item.quantity <= 0 || item.quantity > this.toNumber(item.maxReturnQuantity))
      if (invalid) {
        this.$modal.msgError("退货明细商品和数量不能为空")
        return null
      }
      return Object.assign({}, this.form, {
        totalAmount: details.reduce((sum, item) => sum + item.amount, 0),
        details: details
      })
    },
    fillAllReturnable() {
      ;(this.form.details || []).forEach(item => { item.quantity = this.toNumber(item.maxReturnQuantity) })
    },
    clearReturnQuantities() {
      ;(this.form.details || []).forEach(item => { item.quantity = 0 })
    },
    handleSubmit(row) {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认提交该退货单？").then(() => {
        getPurchaseReturn(row.returnId).then(res => { submitPurchaseReturn(res.data).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() }) })
      })
    },
    handleConfirm(row) {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认执行退货？将扣减对应商品的库存。").then(() => {
        confirmPurchaseReturn(row.returnId).then(() => { this.$modal.msgSuccess("退货成功"); this.getList() })
      })
    },
    handleCancel(row) {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认取消该退货单？").then(() => {
        cancelPurchaseReturn(row.returnId).then(() => { this.$modal.msgSuccess("已取消"); this.getList() })
      })
    },
    handleExport() {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认导出当前查询条件下的采购退货数据？").then(() => {
        this.download("inventory/purchaseReturn/export", { ...this.queryParams }, this.exportFileName("采购退货数据"))
      })
    },
    statusLabel(val) { const map = { draft: "草稿", submitted: "已提交", returned: "已退货", cancelled: "已取消" }; return map[val] || "未知退货状态" },
    statusTag(val) { const map = { draft: "info", submitted: "warning", returned: "success", cancelled: "danger" }; return map[val] || "" },
    defaultReturnDate() {
      const now = new Date()
      return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
    },
    toNumber(value) { const n = Number(value); return Number.isFinite(n) ? n : 0 },
    formatMoney(value) { return this.toNumber(value).toFixed(2) }
  }
}
</script>
<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.text-danger { color: #F56C6C }
.text-warning { color: #E6A23C }
.return-batch-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:8px; color:#606266; }

@import "~@/styles/warehouse-management-page.scss";
</style>
