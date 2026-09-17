<template>
  <div class="app-container warehouse-page">
    <inventory-draft-recovery feature="purchaseReturn" @recovered="onDraftRecovered" />
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
      <el-alert v-if="sourceError" :title="sourceError" type="error" :closable="false" show-icon class="mb12" />
      <el-form ref="formRef" v-loading="formLoading" :disabled="submitLoading || formLoading" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="原采购单号" prop="purchaseOrderId">
              <el-select
                :value="form.purchaseOrderId"
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
          <span>先勾选要退的商品，再填写数量；未勾选商品不参与本次退货。</span>
          <div>
            <el-button size="mini" type="primary" plain @click="fillAllReturnable">退全部可退数量</el-button>
            <el-button size="mini" @click="clearReturnQuantities">清空</el-button>
          </div>
        </div>
        <el-button type="primary" size="small" icon="el-icon-refresh" :disabled="!form.purchaseOrderId || !!form.returnId" @click="reloadPurchaseOrderDetails" style="margin-bottom:8px">重新载入原单明细</el-button>
        <el-table v-loading="sourceLoading" :data="form.details" size="small" border>
          <el-table-column label="退货" width="64" fixed="left" align="center">
            <template slot-scope="scope"><el-checkbox :value="isReturnSelected(scope.row)" :aria-label="'选择退货：' + (scope.row.productName || scope.row.itemName)" @change="value => $set(scope.row, 'returnSelected', value)" /></template>
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
              <el-input-number v-model="scope.row.quantity" :min="0" :max="scope.row.maxReturnQuantity" :precision="2" :controls="false" :disabled="!isReturnSelected(scope.row)" size="small" style="width:100%"/>
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
              <el-button type="text" size="mini" icon="el-icon-delete" class="text-danger" @click="removeDetail(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align:right;margin-top:8px;font-size:14px">退货总金额：<b style="color:#F56C6C">{{ totalAmount }}</b></div>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:purchaseReturn:add']" :disabled="!sourceReady || sourceLoading || formLoading || submitLoading" :loading="submitLoading" type="primary" @click="doSave">保存草稿</el-button>
        <el-button v-hasPermi="['inv:purchaseReturn:submit']" :disabled="!sourceReady || sourceLoading || formLoading || submitLoading" :loading="submitLoading" type="success" @click="doSubmit">保存并提交</el-button>
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
import InventoryDraftRecovery from "@/views/inventory/components/InventoryDraftRecovery.vue"
const { isReturnSelected, selectAllReturnRows } = require("@/utils/returnSelection")
const { createUiOperationScope } = require("@/utils/uiOperationScope")
import { listPurchaseReturn, getPurchaseReturn, getPurchaseReturnDraft, getPurchaseReturnActionContext, savePurchaseReturn, submitPurchaseReturn, submitPurchaseReturnDraft, listPurchaseReturnSourceOrders, getPurchaseReturnSourceOrder, confirmPurchaseReturn, cancelPurchaseReturn } from "@/api/inventory/purchaseReturn"
import { isSelectedWarehouse, getSelectedDeptId } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "purchaseReturn",
    loadFocusedRow(returnId) { return getPurchaseReturnActionContext(returnId, { silentError: true }) },
    actions: {
      confirmPurchaseReturn(row) {
        if (!row || row.status !== "submitted") return this.showTodoBusinessHandled()
        this.handleConfirm(row)
      }
    }
  })],
  name: "InvPurchaseReturn",
  components: { InventoryDraftRecovery },
  data() {
    return {
      loading: false, submitLoading: false, orderLoading: false, total: 0, list: [], orderOptions: [], dialogOpen: false, detailOpen: false,
      formLoading: false, sourceLoading: false, sourceReady: false, sourceLoadedId: "", sourceError: "", autoSourceTitle: "",
      queryParams: { pageNum: 1, pageSize: 10, returnNo: undefined, purchaseOrderNo: undefined, supplierName: undefined, status: undefined },
      form: { returnId: undefined, returnNo: "", purchaseOrderId: undefined, purchaseOrderNo: "", returnTitle: "", supplierName: "", totalAmount: 0, returnDate: "", status: "draft", returnReason: "", responsibility: "", attachmentUrls: "", remark: "", details: [] },
      detailForm: { details: [] },
      rules: {
        returnTitle: [{ required: true, whitespace: true, message: "退货主题不能为空", trigger: "blur" }],
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
    actorContextKey() {
      const store = this.$store || {}, user = store.state && store.state.user || {}
      return JSON.stringify([store.getters && store.getters.id, user.sessionRevision])
    },
    purchaseReturnEmptyText() {
      return getBusinessEmptyText("purchaseReturn", this.isWarehouseContext ? "missingBaseline" : "missingContext")
    },
    formTitle() { return this.form.returnId ? "编辑采购退货单" : "新增采购退货单" },
    totalAmount() {
      return this.form.details.filter(isReturnSelected).reduce((sum, d) => sum + this.toNumber(d.quantity) * this.toNumber(d.unitPrice), 0).toFixed(2)
    }
  },
  watch: {
    dialogOpen(value) { if (!value) this.invalidateReturnForm() },
    detailOpen(value) { if (!value) this.returnScope().invalidate("detail") },
    actorContextKey() { this.handleReturnContextChanged() }
  },
  created() {
    if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this.handleReturnContextChanged)
    this.getList()
  },
  activated() {
    const scope = this.returnScope()
    scope.activate()
    if (this._resumeReturnSource && this.dialogOpen && this._resumeReturnSource.context === this.returnContextKey()) {
      this._returnSourceToken = scope.begin("source", this.sourceLoadedId)
      this.sourceReady = true
    }
    this._resumeReturnSource = null
  },
  deactivated() {
    this._resumeReturnSource = this.sourceReady && !this.sourceLoading && !this.formLoading && !this.submitLoading
      ? { context: this.returnContextKey() } : null
    this.returnScope().deactivate()
    this.invalidateReturnForm()
  },
  beforeDestroy() {
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.handleReturnContextChanged)
    this.returnScope().deactivate()
  },
  methods: {
    onDraftRecovered({ record, response }) {
      const data = response.data
      if (this.dialogOpen && String(this.form.returnId || "new") === String(record.payload.returnId || "new")) {
        this.$set(this.form, "returnId", data.returnId)
        this.$set(this.form, "version", data.version)
        this.$set(this.form, "status", data.status)
        this.$modal.msgWarning(data.status === "draft" ? "已找回草稿编号，当前输入仍保留；请核对后再保存" : "上次操作已提交，当前输入保留供核对，请关闭窗口查看原单")
      }
      return this.getList()
    },
    isReturnSelected,
    returnScope() {
      if (!this._returnScope) this._returnScope = createUiOperationScope(() => this.returnContextKey())
      return this._returnScope
    },
    returnContextKey() { return JSON.stringify([this.actorContextKey, getSelectedDeptId()]) },
    returnSourceId(value) { return value == null ? "" : String(value) },
    invalidateReturnForm() {
      const scope = this.returnScope()
      ;["form", "source", "source-change", "source-options", "save"].forEach(lane => scope.invalidate(lane))
      this.formLoading = false
      this.sourceLoading = false
      this.orderLoading = false
      this.submitLoading = false
      this.sourceReady = false
    },
    handleReturnContextChanged() {
      this.returnScope().invalidate()
      this.invalidateReturnForm()
      this.dialogOpen = false
      this.detailOpen = false
      this.form.details = []
      this.detailForm = { details: [] }
      this.list = []
      this.total = 0
      this.loading = false
      this._resumeReturnSource = null
    },
    ensureReturnSourceReady() {
      const current = this.dialogOpen && this.sourceReady && !this.formLoading && !this.sourceLoading &&
        this.returnSourceId(this.form.purchaseOrderId) === this.sourceLoadedId &&
        this.returnScope().isCurrent(this._returnSourceToken, this.sourceLoadedId)
      if (!current) this.$modal.msgWarning("请先完成原采购单加载，再保存或提交")
      return current
    },
    ensureWarehouseContext() {
      if (this.isWarehouseContext) {
        return true
      }
      this.$modal.msgError("请选择仓库后再操作采购退货")
      return false
    },
    getList() {
      const scope = this.returnScope(), token = scope.begin("list")
      if (!this.isWarehouseContext) {
        this.list = []
        this.total = 0
        return this.handleTodoFocusRows(this.list)
      }
      this.loading = true
      return this.loadTodoBusinessList(() => listPurchaseReturn(this.queryParams)).then(res => {
        if (!scope.isCurrent(token)) return
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).finally(() => { if (scope.isCurrent(token)) this.loading = false })
    },
    openForm(row) {
      if (!this.ensureWarehouseContext()) return
      this.invalidateReturnForm()
      const scope = this.returnScope()
      const token = scope.begin("form")
      this.sourceError = ""
      this.autoSourceTitle = ""
      this.sourceLoadedId = ""
      this.dialogOpen = true
      if (row) {
        const returnId = this.returnSourceId(row.returnId)
        this.formLoading = true
        return getPurchaseReturnDraft(returnId, { silentError: true }).then(res => {
          if (!scope.isCurrent(token) || !this.dialogOpen) return
          if (!res.data || this.returnSourceId(res.data.returnId) !== returnId) throw new Error("退货单已变化，请重新打开")
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
          this.sourceLoadedId = this.returnSourceId(this.form.purchaseOrderId)
          this._returnSourceToken = scope.begin("source", this.sourceLoadedId)
          this.sourceReady = Boolean(this.sourceLoadedId)
          this.$nextTick(() => { if (scope.isCurrent(token) && this.$refs.formRef) this.$refs.formRef.clearValidate() })
        }).catch(error => {
          if (scope.isCurrent(token) && this.dialogOpen) this.sourceError = error.message || "退货草稿加载失败，请重新打开"
        }).finally(() => {
          if (scope.isCurrent(token)) this.formLoading = false
        })
      } else {
        this.form = { returnId: undefined, returnNo: "", purchaseOrderId: undefined, purchaseOrderNo: "", returnTitle: "", supplierName: "", totalAmount: 0, returnDate: this.defaultReturnDate(), status: "draft", returnReason: "", responsibility: "", attachmentUrls: "", remark: "", details: [] }
        this.queryPurchaseOrders("")
      }
      this.$nextTick(() => { if (scope.isCurrent(token) && this.$refs.formRef) this.$refs.formRef.clearValidate() })
    },
    openDetail(row) {
      if (!this.ensureWarehouseContext()) return
      const scope = this.returnScope(), token = scope.begin("detail")
      this.detailOpen = true
      this.detailForm = { details: [] }
      return getPurchaseReturn(row.returnId).then(res => {
        if (scope.isCurrent(token) && this.detailOpen) this.detailForm = res.data || { details: [] }
      })
    },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    queryPurchaseOrders(keyword) {
      if (!this.ensureWarehouseContext()) return
      const scope = this.returnScope(), token = scope.begin("source-options")
      this.orderLoading = true
      return listPurchaseReturnSourceOrders({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined }, { silentError: true }).then(res => {
        if (!scope.isCurrent(token) || !this.dialogOpen) return
        this.orderOptions = (res.rows || []).filter(item => item.status !== "draft" && item.status !== "cancelled")
      }).catch(error => {
        if (scope.isCurrent(token) && this.dialogOpen) this.sourceError = error.message || "采购单搜索失败，请重试"
      }).finally(() => { if (scope.isCurrent(token)) this.orderLoading = false })
    },
    handlePurchaseOrderChange(orderId) {
      if (this.form.returnId || this.submitLoading || this.formLoading) return
      return this.requestReturnSource(orderId)
    },
    reloadPurchaseOrderDetails() {
      if (this.form.purchaseOrderId && !this.form.returnId && !this.submitLoading) {
        return this.requestReturnSource(this.form.purchaseOrderId)
      }
    },
    requestReturnSource(orderId) {
      const scope = this.returnScope(), token = scope.begin("source-change")
      const edited = (this.form.details || []).some(item => isReturnSelected(item) || this.toNumber(item.quantity) !== 0)
      const proceed = () => {
        if (!scope.isCurrent(token) || !this.dialogOpen) return
        this.form.purchaseOrderId = orderId || undefined
        return this.loadPurchaseOrder(orderId)
      }
      if (!edited) return proceed()
      return this.$modal.confirm("已填写的退货商品和数量将重新载入，确认继续吗？", "重新选择原单").then(proceed).catch(error => {
        if (scope.isCurrent(token) && error && error.message && !["cancel", "close"].includes(error.message)) this.sourceError = error.message
      })
    },
    loadPurchaseOrder(orderId) {
      const scope = this.returnScope(), sourceId = this.returnSourceId(orderId)
      const token = scope.begin("source", sourceId)
      this._returnSourceToken = token
      this.sourceReady = false
      this.sourceError = ""
      this.sourceLoading = Boolean(sourceId)
      if (!sourceId) {
        this.sourceLoadedId = ""
        this.form.purchaseOrderNo = ""
        this.form.supplierName = ""
        this.form.details = []
        return Promise.resolve()
      }
      return getPurchaseReturnSourceOrder(orderId, { silentError: true }).then(res => {
        if (!scope.isCurrent(token, this.returnSourceId(this.form.purchaseOrderId)) || !this.dialogOpen) return
        const order = res.data || {}
        if (this.returnSourceId(order.orderId) !== sourceId) throw new Error("原采购单已变化，请重新选择")
        this.form.purchaseOrderNo = order.orderNo
        this.form.supplierName = order.supplierName
        const nextTitle = "采购退货-" + (order.orderNo || "")
        if (!this.form.returnTitle || this.form.returnTitle === this.autoSourceTitle) {
          this.form.returnTitle = nextTitle
        }
        this.autoSourceTitle = nextTitle
        this.form.details = this.buildReturnDetails(order.details || [])
        this.sourceLoadedId = sourceId
        this.sourceReady = true
        if (!this.form.details.length) {
          this.$modal.msgWarning("该采购单暂无可退明细")
        }
      }).catch(error => {
        if (scope.isCurrent(token, this.returnSourceId(this.form.purchaseOrderId)) && this.dialogOpen) this.sourceError = error.message || "原采购单加载失败，请重试"
      }).finally(() => {
        if (scope.isCurrent(token, this.returnSourceId(this.form.purchaseOrderId))) this.sourceLoading = false
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
          returnSelected: false,
          maxReturnQuantity: maxReturnQuantity,
          unitPrice: this.toNumber(item.unitPrice),
          amount: 0,
          returnedQuantity: this.toNumber(item.historicalReturnedQuantity)
        }
      }).filter(item => item.maxReturnQuantity > 0)
    },
    doSave() {
      return this.saveReturnForm(false)
    },
    doSubmit() {
      return this.saveReturnForm(true)
    },
    saveReturnForm(submit) {
      if (this.submitLoading || !this.ensureWarehouseContext() || !this.ensureReturnSourceReady()) return
      const scope = this.returnScope(), token = scope.begin("save")
      const sourceToken = this._returnSourceToken, sourceId = this.sourceLoadedId
      const payload = this.buildPayload()
      if (!payload) return
      this.submitLoading = true
      this.$refs.formRef.validate(valid => {
        const current = () => scope.isCurrent(token) && scope.isCurrent(sourceToken, this.returnSourceId(this.form.purchaseOrderId)) && this.dialogOpen && this.sourceLoadedId === sourceId
        if (!current()) return
        if (!valid) { this.submitLoading = false; return }
        const save = submit ? submitPurchaseReturn : savePurchaseReturn
        save(payload).then(() => {
          if (!current()) return
          this.$modal.msgSuccess(submit ? "提交成功" : "保存成功")
          this.dialogOpen = false
          this.getList()
        }).catch(error => {
          if (current() && !(error && error.notified)) this.sourceError = error && error.message || "保存失败，已保留填写内容"
        }).finally(() => { if (scope.isCurrent(token)) this.submitLoading = false })
      })
    },
    buildPayload() {
      if (this.form.status && this.form.status !== "draft") { this.$modal.msgWarning("原单已提交，请关闭窗口查看原单，当前输入仍保留"); return null }
      if (!this.form.details || !this.form.details.length) {
        this.$modal.msgError("请先选择原采购单并保留至少一条退货明细")
        return null
      }
      const selected = this.form.details.filter(isReturnSelected)
      if (!selected.length) {
        this.$modal.msgError("请至少勾选一条退货明细")
        return null
      }
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
      const invalid = details.find(item => !(item.itemId || item.productId) || !Number.isFinite(item.quantity) || item.quantity <= 0 || item.quantity > this.toNumber(item.maxReturnQuantity))
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
      this.form.details = selectAllReturnRows(this.form.details)
    },
    clearReturnQuantities() {
      ;(this.form.details || []).forEach(item => { item.quantity = 0; this.$set(item, "returnSelected", false) })
    },
    handleSubmit(row) {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认提交该退货单？").then(() => {
        submitPurchaseReturnDraft(row.returnId, row.version).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() })
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
