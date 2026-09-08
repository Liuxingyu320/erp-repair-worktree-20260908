<template>
  <div class="app-container">
    <inventory-page-hero
      title="销售退货"
      eyebrow="售后处理"
      description="关联原销售单处理客户退货，清晰追踪退回商品、库存回补与金额冲回。"
      scope-text="当前门店退货业务"
      icon="el-icon-refresh-left"
      tone="rose"
      :features="['原单追溯', '退货入库', '金额冲回']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="退货单号">
          <el-input v-model="queryParams.returnNo" placeholder="退货单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="原销售单号">
          <el-input v-model="queryParams.salesOrderNo" placeholder="销售单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable @keyup.enter.native="getList"/>
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
          <el-button v-hasPermi="['inv:salesReturn:list']" type="primary" size="mini" icon="el-icon-search" :disabled="!isStoreContext" @click="getList">搜索</el-button>
          <el-button v-hasPermi="['inv:salesReturn:add']" size="mini" icon="el-icon-plus" :disabled="!isStoreContext" @click="openForm(null)">新增退货单</el-button>
          <el-button v-hasPermi="['inv:salesReturn:export']" size="mini" icon="el-icon-download" :disabled="!isStoreContext" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small" :empty-text="salesReturnEmptyText">
        <el-table-column label="退货单号" prop="returnNo" width="160"/>
        <el-table-column label="原销售单号" prop="salesOrderNo" width="160"/>
        <el-table-column label="客户" prop="customerName" min-width="140"/>
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
            <el-button v-hasPermi="['inv:salesReturn:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
            <el-button v-if="scope.row.status === 'draft'" v-hasPermi="['inv:salesReturn:add']" type="text" size="mini" icon="el-icon-edit" @click="openForm(scope.row)">编辑</el-button>
            <el-button v-if="scope.row.status === 'draft'" v-hasPermi="['inv:salesReturn:submit']" type="text" size="mini" icon="el-icon-upload" @click="handleSubmit(scope.row)">提交</el-button>
            <el-button v-if="scope.row.status === 'submitted'" v-hasPermi="['inv:salesReturn:confirm']" type="text" size="mini" class="text-warning" icon="el-icon-check" @click="handleConfirm(scope.row)">确认退货</el-button>
            <el-button v-if="scope.row.status === 'draft' || scope.row.status === 'submitted'" v-hasPermi="['inv:salesReturn:remove']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="handleCancel(scope.row)">取消</el-button>
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
            <el-form-item label="原销售单号" prop="salesOrderId">
              <el-select
                v-model="form.salesOrderId"
                filterable
                remote
                reserve-keyword
                :remote-method="querySalesOrders"
                :loading="orderLoading"
                :disabled="!!form.returnId"
                placeholder="请选择原销售单"
                style="width:100%"
                @change="handleSalesOrderChange"
              >
                <el-option
                  v-for="item in orderOptions"
                  :key="item.orderId"
                  :label="item.orderNo + (item.customerName ? ' / ' + item.customerName : '')"
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
            <el-form-item label="客户名称" prop="customerName">
              <el-input v-model="form.customerName" disabled placeholder="选择原销售单后自动带出"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2"/>
        </el-form-item>

        <el-divider content-position="left">退货明细</el-divider>
        <el-button type="primary" size="small" icon="el-icon-refresh" :disabled="!form.salesOrderId || !!form.returnId" @click="reloadSalesOrderDetails" style="margin-bottom:8px">重新载入原单明细</el-button>
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
        <el-button v-hasPermi="['inv:salesReturn:add']" type="primary" @click="doSave">保存草稿</el-button>
        <el-button v-hasPermi="['inv:salesReturn:submit']" type="success" @click="doSubmit">保存并提交</el-button>
      </div>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="退货单详情" :visible.sync="detailOpen" width="700px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="退货单号">{{ detailForm.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="原销售单号">{{ detailForm.salesOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detailForm.customerName }}</el-descriptions-item>
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
import { listSalesReturn, getSalesReturn, saveSalesReturn, submitSalesReturn, confirmSalesReturn, cancelSalesReturn } from "@/api/inventory/salesReturn"
import { listSales, getSalesDetail } from "@/api/inventory/sales"
import { isSelectedStore } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "salesReturn",
    loadFocusedRow(returnId) { return getSalesReturn(returnId) },
    actions: {
      confirmSalesReturn(row) {
        if (!row || row.status !== "submitted") return this.showTodoBusinessHandled()
        this.handleConfirm(row)
      }
    }
  })],
  name: "InvSalesReturn",
  data() {
    return {
      loading: false, submitLoading: false, orderLoading: false, total: 0, list: [], orderOptions: [], dialogOpen: false, detailOpen: false,
      queryParams: { pageNum: 1, pageSize: 10, returnNo: undefined, salesOrderNo: undefined, customerName: undefined, status: undefined },
      form: { returnId: undefined, returnNo: "", salesOrderId: undefined, salesOrderNo: "", returnTitle: "", customerName: "", totalAmount: 0, returnDate: "", status: "draft", remark: "", details: [] },
      detailForm: { details: [] },
      rules: {
        returnTitle: [{ required: true, message: "退货主题不能为空", trigger: "blur" }],
        customerName: [{ required: true, message: "客户名称不能为空", trigger: "blur" }],
        returnDate: [{ required: true, message: "请选择退货日期", trigger: "change" }],
        salesOrderId: [{ required: true, message: "请选择原销售单", trigger: "change" }]
      }
    }
  },
  computed: {
    isStoreContext() {
      return isSelectedStore()
    },
    salesReturnEmptyText() {
      return getBusinessEmptyText("salesReturn", this.isStoreContext ? "missingBaseline" : "missingContext")
    },
    formTitle() { return this.form.returnId ? "编辑销售退货单" : "新增销售退货单" },
    totalAmount() {
      return this.form.details.reduce((sum, d) => sum + this.toNumber(d.quantity) * this.toNumber(d.unitPrice), 0).toFixed(2)
    }
  },
  created() { this.getList() },
  methods: {
    ensureStoreContext() {
      if (this.isStoreContext) {
        return true
      }
      this.$modal.msgError("请选择门店后再操作销售退货")
      return false
    },
    getList() {
      if (!this.isStoreContext) {
        this.list = []
        this.total = 0
        return this.handleTodoFocusRows(this.list)
      }
      this.loading = true
      return this.loadTodoBusinessList(() => listSalesReturn(this.queryParams)).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).finally(() => { this.loading = false })
    },
    openForm(row) {
      if (!this.ensureStoreContext()) return
      if (row) {
        getSalesReturn(row.returnId).then(res => {
          this.form = Object.assign({}, res.data, { returnDate: res.data.returnDate || this.defaultReturnDate() })
          if (this.form.salesOrderId) {
            this.orderOptions = [{ orderId: this.form.salesOrderId, orderNo: this.form.salesOrderNo, customerName: this.form.customerName }]
          }
          this.dialogOpen = true
        })
      } else {
        this.form = { returnId: undefined, returnNo: "", salesOrderId: undefined, salesOrderNo: "", returnTitle: "", customerName: "", totalAmount: 0, returnDate: this.defaultReturnDate(), status: "draft", remark: "", details: [] }
        this.querySalesOrders("")
        this.dialogOpen = true
      }
      this.$nextTick(() => { this.$refs.formRef && this.$refs.formRef.clearValidate() })
    },
    openDetail(row) {
      this.detailOpen = true
      getSalesReturn(row.returnId).then(res => { this.detailForm = res.data || { details: [] } })
    },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    querySalesOrders(keyword) {
      if (!this.isStoreContext) {
        this.orderOptions = []
        return
      }
      this.orderLoading = true
      listSales({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined }).then(res => {
        this.orderOptions = (res.rows || []).filter(item => item.status !== "draft" && item.status !== "cancelled")
      }).finally(() => { this.orderLoading = false })
    },
    handleSalesOrderChange(orderId) {
      if (!orderId) {
        this.form.salesOrderNo = ""
        this.form.details = []
        return
      }
      this.loadSalesOrder(orderId)
    },
    reloadSalesOrderDetails() {
      if (this.form.salesOrderId) {
        this.loadSalesOrder(this.form.salesOrderId)
      }
    },
    loadSalesOrder(orderId) {
      getSalesDetail(orderId).then(res => {
        const order = res.data || {}
        this.form.salesOrderId = order.orderId
        this.form.salesOrderNo = order.orderNo
        this.form.customerName = order.customerName
        if (!this.form.returnTitle) {
          this.form.returnTitle = "销售退货-" + (order.orderNo || "")
        }
        this.form.details = this.buildReturnDetails(order.details || [])
        if (!this.form.details.length) {
          this.$modal.msgWarning("该销售单暂无可退明细")
        }
      })
    },
    buildReturnDetails(details) {
      return details.map(item => {
        const maxReturnQuantity = this.toNumber(item.deliveredQuantity)
        return {
          salesDetailId: item.detailId,
          productId: item.productId,
          productName: item.productName,
          sku: item.sku,
          spec: item.spec,
          unit: item.unit,
          quantity: Math.min(1, maxReturnQuantity),
          maxReturnQuantity: maxReturnQuantity,
          unitPrice: this.toNumber(item.unitPrice),
          amount: Math.min(1, maxReturnQuantity) * this.toNumber(item.unitPrice),
          returnedQuantity: 0
        }
      }).filter(item => item.maxReturnQuantity > 0)
    },
    doSave() {
      if (!this.ensureStoreContext()) return
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        if (!payload) return
        this.submitLoading = true
        saveSalesReturn(payload).then(() => { this.$modal.msgSuccess("保存成功"); this.dialogOpen = false; this.getList() }).finally(() => { this.submitLoading = false })
      })
    },
    doSubmit() {
      if (!this.ensureStoreContext()) return
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        if (!payload) return
        this.submitLoading = true
        submitSalesReturn(payload).then(() => { this.$modal.msgSuccess("提交成功"); this.dialogOpen = false; this.getList() }).finally(() => { this.submitLoading = false })
      })
    },
    buildPayload() {
      if (!this.form.details || !this.form.details.length) {
        this.$modal.msgError("请先选择原销售单并保留至少一条退货明细")
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
      const invalid = details.find(item => !item.productId || item.quantity <= 0)
      if (invalid) {
        this.$modal.msgError("退货明细商品和数量不能为空")
        return null
      }
      return Object.assign({}, this.form, {
        totalAmount: details.reduce((sum, item) => sum + item.amount, 0),
        details: details
      })
    },
    handleSubmit(row) {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认提交该退货单？").then(() => {
        getSalesReturn(row.returnId).then(res => { submitSalesReturn(res.data).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() }) })
      })
    },
    handleConfirm(row) {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认执行退货？将增加对应商品的库存。").then(() => {
        confirmSalesReturn(row.returnId).then(() => { this.$modal.msgSuccess("退货成功"); this.getList() })
      })
    },
    handleCancel(row) {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认取消该退货单？").then(() => {
        cancelSalesReturn(row.returnId).then(() => { this.$modal.msgSuccess("已取消"); this.getList() })
      })
    },
    handleExport() {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认导出当前查询条件下的销售退货数据？").then(() => {
        this.download("inventory/salesReturn/export", { ...this.queryParams }, this.exportFileName("销售退货数据"))
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
</style>
