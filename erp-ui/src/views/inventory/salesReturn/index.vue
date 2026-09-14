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
    <el-dialog title="查找可退原销售单" :visible.sync="sourcePickerOpen" width="min(720px, 96vw)" append-to-body :close-on-click-modal="false">
      <sales-return-source-picker v-if="sourcePickerOpen && dialogOpen" :context-key="String(formEpoch)" @select="selectSourceOrder" />
    </el-dialog>
    <el-dialog :title="formTitle" :visible.sync="dialogOpen" :before-close="closeEditor" width="800px" append-to-body :close-on-click-modal="false" top="3vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="原销售单号" prop="salesOrderId">
              <el-input :value="form.salesOrderNo" readonly placeholder="请查找可退原销售单">
                <el-button slot="append" :disabled="!!form.returnId || submitLoading" @click="openSourcePicker">查找原单</el-button>
              </el-input>
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
        <p>勾选本次要退的商品，未勾选商品不参与退货。</p>
        <el-button size="mini" :disabled="submitLoading" @click="fillAllReturnable">一键全退</el-button>
        <el-button size="mini" :disabled="submitLoading" @click="clearReturnSelection">取消全部勾选</el-button>
        <el-table :data="form.details" size="small" border>
          <el-table-column label="退货" width="64" fixed="left">
            <template slot-scope="scope"><el-checkbox :value="isReturnSelected(scope.row)" :disabled="submitLoading" :aria-label="'选择退货：' + scope.row.productName" @change="value => $set(scope.row, 'returnSelected', value)" /></template>
          </el-table-column>
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
          <el-table-column label="数量" prop="quantity" width="150">
            <template slot-scope="scope">
              <el-input-number v-model="scope.row.quantity" :disabled="submitLoading || !isReturnSelected(scope.row)" :controls="false" :min="0" :max="scope.row.maxReturnQuantity" :precision="2" size="small" style="width:100%"/>
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="110">
            <template slot-scope="scope">
              <span>{{ formatMoney(scope.row.unitPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(isReturnSelected(scope.row) ? toNumber(scope.row.quantity) * toNumber(scope.row.unitPrice) : 0) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-delete" class="text-danger" :disabled="submitLoading" @click="removeDetail(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:8px;font-size:14px">退货总金额：<b style="color:#F56C6C">{{ totalAmount }}</b></div>
      </el-form>
      <div slot="footer">
        <el-button :disabled="submitLoading" @click="closeEditor()">取消</el-button>
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
import { listSalesReturn, getSalesReturn, getSalesReturnActionContext, getSalesReturnDraft, getSalesReturnSourceOrder, saveSalesReturn, submitSalesReturn, submitSalesReturnDraft, confirmSalesReturn, cancelSalesReturn } from "@/api/inventory/salesReturn"
import SalesReturnSourcePicker from "@/views/inventory/components/SalesReturnSourcePicker.vue"
const { isReturnSelected, selectAllReturnRows } = require("@/utils/returnSelection")
import { isSelectedStore, getSelectedDeptId } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "salesReturn",
    loadFocusedRow(returnId) { return getSalesReturnActionContext(returnId, { silentError: true }) },
    actions: {
      confirmSalesReturn(row) {
        if (!row || row.status !== "submitted") return this.showTodoBusinessHandled()
        this.handleConfirm(row)
      }
    }
  })],
  name: "InvSalesReturn",
  components: { SalesReturnSourcePicker },
  data() {
    return {
      sourceLoading: false, sourceSequence: 0, sourcePickerOpen: false, formEpoch: 0, formReadSequence: 0, sourceError: "",
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
      return this.form.details.filter(isReturnSelected).reduce((sum, d) => sum + this.toNumber(d.quantity) * this.toNumber(d.unitPrice), 0).toFixed(2)
    }
  },
  created() { this.getList() },
  watch: {
    dialogOpen(value) { if (!value) { this.sourcePickerOpen = false; this.sourceSequence += 1; this.sourceLoading = false } },
    "$store.getters.id"() { this.invalidateSourceContext() },
    "$route.fullPath"() { this.invalidateSourceContext() }
  },
  beforeDestroy() { this.invalidateSourceContext() },
  deactivated() { this.invalidateSourceContext() },
  methods: {
    isReturnSelected,
    fillAllReturnable() { if (!this.submitLoading) this.form.details = selectAllReturnRows(this.form.details) },
    clearReturnSelection() { if (!this.submitLoading) this.form.details.forEach(row => this.$set(row, "returnSelected", false)) },
    sourceScope() { return JSON.stringify([getSelectedDeptId(), this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath]) },
    invalidateSourceContext() { this.formReadSequence += 1; this.sourceSequence += 1; this.sourceLoading = false; this.sourcePickerOpen = false; this.dialogOpen = false },
    closeEditor(done) { if (this.submitLoading) return; this.invalidateSourceContext(); if (typeof done === "function") done() },
    openSourcePicker() { if (this.dialogOpen && !this.form.returnId && !this.submitLoading && this.ensureStoreContext()) this.sourcePickerOpen = true },
    selectSourceOrder(order) {
      if (!this.dialogOpen || !this.sourcePickerOpen || this.form.returnId || this.submitLoading) return
      this.sourcePickerOpen = false
      this.form.salesOrderId = order.orderId
      return this.loadSalesOrder(order.orderId)
    },
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
      if (this.submitLoading) return
      this.sourceSequence += 1
      this.formEpoch += 1
      const readSequence = ++this.formReadSequence, scope = this.sourceScope()
      this.sourcePickerOpen = false
      this.sourceLoading = false
      this.sourceError = ""
      if (!this.ensureStoreContext()) return
      if (row) {
        return getSalesReturnDraft(row.returnId).then(res => {
          if (readSequence !== this.formReadSequence || scope !== this.sourceScope()) return
          this.form = Object.assign({}, res.data, {
            returnDate: res.data.returnDate || this.defaultReturnDate(),
            details: (res.data.details || []).map(item => Object.assign({}, item, {
              maxReturnQuantity: this.toNumber(item.returnableQuantity)
            }))
          })
          if (this.form.salesOrderId) {
            this.orderOptions = [{ orderId: this.form.salesOrderId, orderNo: this.form.salesOrderNo, customerName: this.form.customerName }]
          }
          this.dialogOpen = true
        })
      } else {
        this.form = { returnId: undefined, returnNo: "", salesOrderId: undefined, salesOrderNo: "", returnTitle: "", customerName: "", totalAmount: 0, returnDate: this.defaultReturnDate(), status: "draft", remark: "", details: [] }
        this.dialogOpen = true
      }
      this.$nextTick(() => { this.$refs.formRef && this.$refs.formRef.clearValidate() })
    },
    openDetail(row) {
      this.detailOpen = true
      getSalesReturn(row.returnId).then(res => { this.detailForm = res.data || { details: [] } })
    },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    handleSalesOrderChange(orderId) {
      if (!orderId) {
        this.sourceSequence += 1
        this.sourceLoading = false
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
      const sequence = ++this.sourceSequence, form = this.form, scope = this.sourceScope(), epoch = this.formEpoch
      const previousAutoTitle = "销售退货-" + (this.form.salesOrderNo || "")
      this.sourceLoading = true
      this.sourceError = ""
      this.form.details = []
      return getSalesReturnSourceOrder(orderId).then(res => {
        if (sequence !== this.sourceSequence || !this.dialogOpen || this.form !== form || epoch !== this.formEpoch ||
            scope !== this.sourceScope() || String(this.form.salesOrderId) !== String(orderId)) return
        const order = res.data || {}
        if (String(order.orderId) !== String(orderId) || !Array.isArray(order.details)) throw Error("原单明细响应不完整")
        this.form.salesOrderNo = order.orderNo
        this.form.customerName = order.customerName
        if (!this.form.returnTitle || this.form.returnTitle === previousAutoTitle) {
          this.form.returnTitle = "销售退货-" + (order.orderNo || "")
        }
        this.form.details = this.buildReturnDetails(order.details || [])
        if (!this.form.details.length) this.$modal.msgWarning("该销售单暂无可退明细")
      }).catch(error => {
        if (sequence === this.sourceSequence && this.form === form && scope === this.sourceScope()) {
          this.sourceError = error && error.message ? error.message : "原单明细加载失败，请重试"
          this.$modal.msgError(this.sourceError)
        }
      }).finally(() => {
        if (sequence === this.sourceSequence) this.sourceLoading = false
      })
    },
    buildReturnDetails(details) {
      return details.map(item => {
        const maxReturnQuantity = this.toNumber(item.returnableQuantity)
        return {
          salesDetailId: item.detailId,
          itemType: item.itemType || "product",
          itemId: item.itemId || item.productId,
          itemCode: item.itemCode || item.sku,
          itemName: item.itemName || item.productName,
          productId: item.productId,
          productName: item.productName,
          sku: item.sku,
          spec: item.spec,
          unit: item.unit,
          quantity: 0,
          returnSelected: false,
          maxReturnQuantity: maxReturnQuantity,
          unitPrice: this.toNumber(item.unitPrice),
          amount: 0,
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
      if (this.sourceError) { this.$modal.msgError(this.sourceError); return null }
      if (this.sourceLoading) {
        this.$modal.msgWarning("请等待原单明细加载完成")
        return null
      }
      if (!this.form.details || !this.form.details.length) {
        this.$modal.msgError("请先选择原销售单并保留至少一条退货明细")
        return null
      }
      const selected = this.form.details.filter(isReturnSelected)
      if (!selected.length) { this.$modal.msgError("请勾选本次要退的商品"); return null }
      const details = selected.map(item => {
        const quantity = Number(item.quantity)
        const unitPrice = this.toNumber(item.unitPrice)
        const { returnSelected, ...detail } = item
        return Object.assign({}, detail, {
          quantity: quantity,
          unitPrice: unitPrice,
          amount: quantity * unitPrice
        })
      })
      const invalid = details.find(item => !(item.itemId || item.productId) || !item.salesDetailId || !Number.isFinite(item.quantity) || item.quantity <= 0 || item.quantity > this.toNumber(item.maxReturnQuantity))
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
      if (this.submitLoading) return
      const returnId = row.returnId, scope = this.sourceScope()
      return this.$modal.confirm("确认提交该退货单？").then(() => {
        if (scope !== this.sourceScope() || this.submitLoading) return
        this.submitLoading = true
        return submitSalesReturnDraft(returnId).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() })
          .finally(() => { this.submitLoading = false })
      }).catch(error => ({ failed: true, error }))
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
