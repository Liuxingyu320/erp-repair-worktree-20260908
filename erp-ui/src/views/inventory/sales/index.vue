<template>
  <div class="app-container">
    <inventory-page-hero
      title="销售管理"
      eyebrow="门店经营"
      description="从客户需求到销售出库，集中管理订单、商品明细与履约进度。"
      scope-text="当前门店销售业务"
      icon="el-icon-shopping-cart-full"
      tone="blue"
      :features="['销售开单', '客户订单', '出库跟踪']"
    />
    <el-alert
      v-if="!isStoreContext"
      title="当前组织不是门店"
      :description="salesContextAlertDescription"
      type="warning"
      show-icon
      :closable="false"
      class="mb12"
    />

    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="单号">
          <el-input v-model="queryParams.orderNo" placeholder="销售单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryParams.orderTitle" placeholder="销售标题" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="销售日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="草稿" value="draft"/>
            <el-option label="已提交" value="submitted"/>
            <el-option label="已通知" value="noticed"/>
            <el-option label="已出库" value="delivered"/>
            <el-option label="已取消" value="cancelled"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:sales:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-tooltip :disabled="isStoreContext" content="销售单需要选择门店后操作" placement="top">
            <span class="action-tooltip-wrap">
              <el-button ref="newSaleButton" v-hasPermi="['inv:sales:add']" size="mini" icon="el-icon-plus" :disabled="!isStoreContext" @click="openForm(null, $event)">新建销售单</el-button>
            </span>
          </el-tooltip>
          <el-button v-hasPermi="['inv:sales:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div v-if="listError" class="list-load-error mb12" role="alert">
        <span><i class="el-icon-warning-outline"/> {{ listError }}</span>
        <el-button type="text" :disabled="loading" @click="getList">重新加载</el-button>
      </div>
      <el-table v-loading="loading" :data="list" :empty-text="salesEmptyText">
        <el-table-column label="销售单号" prop="orderNo" width="180"/>
        <el-table-column label="标题" prop="orderTitle" min-width="160"/>
        <el-table-column label="客户" prop="customerName" width="140"/>
        <el-table-column label="总金额" prop="totalAmount" width="120"/>
        <el-table-column label="状态" prop="status" width="100">
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180"/>
        <el-table-column label="操作" width="320" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:sales:query']" type="text" size="mini" @click="showDetail(scope.row.orderId)">详情</el-button>
            <el-button v-if="scope.row.status === 'draft'" v-hasPermi="['inv:sales:add']" type="text" size="mini" :disabled="!isStoreContext" @click="openForm(scope.row, $event)">编辑</el-button>
            <el-button v-if="scope.row.status === 'draft'" v-hasPermi="['inv:sales:submit']" type="text" size="mini" :disabled="!isStoreContext" @click="doSubmit(scope.row)">提交</el-button>
            <el-button v-if="scope.row.status === 'submitted'" v-hasPermi="['inv:deliveryNotice:add']" type="text" size="mini" style="color:var(--erp-primary, #0b6b53)" :disabled="!isStoreContext" @click="doCreateDeliveryNotice(scope.row)">生成发货通知</el-button>
            <el-button v-if="scope.row.status === 'draft' || scope.row.status === 'submitted'" v-hasPermi="['inv:sales:remove']" type="text" size="mini" style="color:#F56C6C" :disabled="!isStoreContext" @click="doCancel(scope.row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog title="销售单详情" :visible.sync="detailOpen" width="860px" append-to-body :close-on-click-modal="false">
      <div v-loading="detailLoading" class="order-detail">
        <el-descriptions :column="2" border size="small" class="mb12">
          <el-descriptions-item label="单号">{{ detailOrder.orderNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detailOrder.orderTitle || "-" }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detailOrder.customerName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatAmount(detailOrder.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailOrder.status)" size="mini">{{ statusLabel(detailOrder.status) || "-" }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="销售日期">{{ detailOrder.orderDate || "-" }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">销售明细</el-divider>
        <el-table :data="detailOrder.details" border size="small" empty-text="暂无明细">
          <el-table-column label="物料" min-width="180">
            <template slot-scope="scope">
              <div class="product-name">{{ scope.row.itemName || scope.row.productName || "-" }}</div>
              <div class="product-meta">{{ scope.row.itemCode || scope.row.productCode || "-" }}</div>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template slot-scope="scope">{{ scope.row.spec || "-" }}</template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template slot-scope="scope">{{ scope.row.unit || "-" }}</template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="right">
            <template slot-scope="scope">{{ formatAmount(scope.row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="单价" width="110" align="right">
            <template slot-scope="scope">{{ formatAmount(scope.row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template slot-scope="scope">{{ lineAmount(scope.row) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div slot="footer">
        <el-button @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog
      :title="form.orderId ? '编辑销售单' : '新建销售单'"
      :visible.sync="open"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!formSubmitting"
      :show-close="!formSubmitting"
      :before-close="handleFormBeforeClose"
      @opened="focusSalesTitle"
      @closed="restoreSalesDialogFocus"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="标题" prop="orderTitle">
          <el-input ref="salesTitleInput" v-model="form.orderTitle" aria-label="销售单标题" maxlength="120"/>
        </el-form-item>
        <el-form-item label="客户" prop="customerId">
          <el-select
            v-model="form.customerId"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="loadCustomers"
            :loading="customerLoading"
            placeholder="输入名称或编码搜索有效客户"
            style="width:100%"
            @change="onCustomerChange"
          >
            <el-option
              v-for="item in customerOptions"
              :key="item.customerId"
              :label="customerLabel(item)"
              :value="item.customerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="销售日期" prop="orderDate">
          <el-date-picker v-model="form.orderDate" type="date" placeholder="选择日期" value-format="yyyy-MM-dd" style="width:100%"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500"/>
        </el-form-item>
        <el-divider content-position="left">销售明细</el-divider>
        <el-row v-for="(item, idx) in form.details" :key="idx" :gutter="8" class="mb8 sales-detail-row">
          <el-col :span="5">
            <el-radio-group v-model="item.itemType" size="mini" @change="onItemTypeChange(item)">
              <el-radio-button v-for="type in allowedItemTypes" :key="type" :label="type">{{ itemTypeLabel(type) }}</el-radio-button>
            </el-radio-group>
          </el-col>
          <el-col :span="7">
            <inventory-item-select
              v-model="item.itemId"
              :item-type="item.itemType || 'product'"
              :disabled="!item.itemType"
              width="100%"
              placeholder="搜索物料名称/编码"
              @selected="(selectedItem) => onItemSelected(selectedItem, idx)"
            />
            <div v-if="item.itemName || item.productName" class="detail-product-meta">
              {{ item.itemCode || item.productCode || "-" }} / {{ item.spec || "-" }} / {{ item.unit || "-" }}
            </div>
          </el-col>
          <el-col :span="4">
            <el-input-number v-model="item.quantity" :min="1" :precision="2" placeholder="数量" size="small" style="width:100%"/>
          </el-col>
          <el-col :span="4">
            <el-input-number v-model="item.unitPrice" :min="0.01" :precision="2" placeholder="单价" size="small" style="width:100%"/>
          </el-col>
          <el-col :span="2">
            <span class="amount-txt">{{ (item.quantity * item.unitPrice || 0).toFixed(2) }}</span>
          </el-col>
          <el-col :span="2">
            <el-button type="text" size="mini" style="color:#F56C6C" @click="removeDetail(idx)">删除</el-button>
          </el-col>
        </el-row>
        <el-button type="primary" size="mini" icon="el-icon-plus" plain @click="addDetail">添加明细</el-button>
      </el-form>
      <div slot="footer">
        <el-button :disabled="formSubmitting" @click="closeForm">取消</el-button>
        <el-button v-hasPermi="['inv:sales:add']" type="primary" :loading="formSubmitting" :disabled="!isStoreContext || formSubmitting" @click="doSave(false)">保存草稿</el-button>
        <el-button v-hasPermi="['inv:sales:submit']" type="success" :loading="formSubmitting" :disabled="!isStoreContext || formSubmitting" @click="doSave(true)">保存并提交</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSales, getSalesDetail, saveSales, submitSales, cancelSales } from "@/api/inventory/sales"
import { createDeliveryNotice as createDeliveryNoticeApi } from "@/api/inventory/deliveryNotice"
import { listCustomerOptions } from "@/api/inventory/customer"
import { getSelectedDeptContext, isSelectedStore } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { parseTime } from "@/utils/common"
import InventoryItemSelect from "@/views/inventory/components/InventoryItemSelect"
export default {
  name: "InvSales",
  components: { InventoryItemSelect },
  data() {
    return {
      loading: false, total: 0, list: [], listError: "", listRequestSequence: 0, activeListQuerySnapshot: "",
      open: false, formSubmitting: false, detailOpen: false, detailLoading: false,
      customerLoading: false, customerOptions: [], dateRange: [],
      lastSalesDialogTrigger: null,
      allowedItemTypes: ["product", "gift"],
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, orderTitle: undefined, customerName: undefined, status: undefined },
      form: { orderId: undefined, orderTitle: "", customerId: undefined, customerName: "", orderDate: null, remark: "", totalAmount: 0, details: [] },
      detailOrder: { details: [] },
      rules: {
        orderTitle: [{ required: true, message: "请输入标题", trigger: "blur" }],
        customerId: [{ required: true, message: "请选择有效客户档案", trigger: "change" }],
        orderDate: [{ required: true, message: "请选择销售日期", trigger: "change" }]
      }
    }
  },
  computed: {
    selectedDeptContext() {
      return getSelectedDeptContext()
    },
    isStoreContext() {
      return isSelectedStore()
    },
    currentDeptLabel() {
      const context = this.selectedDeptContext
      if (!context.deptName) {
        return "未选择组织"
      }
      const prefix = context.isWarehouse ? "仓库" : context.isStore ? "门店" : "组织"
      return prefix + "：" + context.deptName
    },
    salesContextAlertDescription() {
      return "销售单需要选择门店后操作。当前选择：" + this.currentDeptLabel + "。"
    },
    salesEmptyText() {
      return getBusinessEmptyText("sales", this.isStoreContext ? "missingBaseline" : "missingContext")
    }
  },
  created() { this.getList() },
  methods: {
    statusType(s) { const m = { draft: 'info', submitted: 'warning', noticed: 'primary', delivered: 'success', cancelled: 'danger' }; return m[s] || 'info' },
    statusLabel(s) { const m = { draft: '草稿', submitted: '已提交', noticed: '已通知', delivered: '已出库', cancelled: '已取消' }; return m[s] || '未知销售状态' },
    toNumber(value) {
      const num = Number(value)
      return isNaN(num) ? 0 : num
    },
    formatAmount(value) {
      return this.toNumber(value).toFixed(2)
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ""
    },
    lineAmount(item) {
      if (this.hasValue(item.amount)) {
        return this.formatAmount(item.amount)
      }
      return this.formatAmount(this.toNumber(item.quantity) * this.toNumber(item.unitPrice))
    },
    getList() {
      const query = this.buildQuery()
      const querySnapshot = JSON.stringify(query)
      const requestSequence = ++this.listRequestSequence
      this.activeListQuerySnapshot = querySnapshot
      this.loading = true
      this.listError = ""
      this.list = []
      this.total = 0
      return listSales(query).then(res => {
        if (!this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true }
        this.list = res.rows || []
        this.total = res.total || 0
        return res
      }).catch(error => {
        if (!this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true, error }
        this.list = []
        this.total = 0
        this.listError = "销售单列表加载失败，请重试。"
        return { failed: true, error }
      }).finally(() => {
        if (this.isCurrentListRequest(requestSequence, querySnapshot)) this.loading = false
      })
    },
    isCurrentListRequest(requestSequence, querySnapshot) {
      return requestSequence === this.listRequestSequence && querySnapshot === this.activeListQuerySnapshot
    },
    buildQuery() {
      return this.addDateRange(Object.assign({}, this.queryParams), this.dateRange, "OrderDate")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      return this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.queryParams = { pageNum: 1, pageSize: 10, orderNo: undefined, orderTitle: undefined, customerName: undefined, status: undefined }
      return this.getList()
    },
    ensureStoreContext() {
      if (this.isStoreContext) {
        return true
      }
      this.$modal.msgError("销售单需要选择门店后操作")
      return false
    },
    resetForm() {
      this.form = { orderId: undefined, orderTitle: "", customerId: undefined, customerName: "", orderDate: this.defaultOrderDate(), remark: "", totalAmount: 0, details: [] }
      this.customerOptions = []
    },
    defaultOrderDate() {
      return parseTime(new Date(), "{y}-{m}-{d}")
    },
    customerLabel(customer) {
      if (!customer) return ""
      return customer.customerName + (customer.customerCode ? "（" + customer.customerCode + "）" : "")
    },
    loadCustomers(keyword) {
      if (!this.isStoreContext) {
        this.customerOptions = []
        return Promise.resolve([])
      }
      this.customerLoading = true
      return listCustomerOptions(keyword ? String(keyword).trim() : undefined).then(res => {
        const selected = this.customerOptions.find(item => String(item.customerId) === String(this.form.customerId))
        this.customerOptions = res.data || []
        if (selected && !this.customerOptions.some(item => String(item.customerId) === String(selected.customerId))) {
          this.customerOptions.unshift(selected)
        }
        return this.customerOptions
      }).catch(() => {
        this.$modal.msgWarning("客户选项加载失败，请检查客户档案权限或稍后重试")
        return this.customerOptions
      }).finally(() => { this.customerLoading = false })
    },
    onCustomerChange(customerId) {
      const customer = this.customerOptions.find(item => String(item.customerId) === String(customerId))
      this.form.customerName = customer ? customer.customerName : ""
    },
    itemTypeLabel(type) {
      return { product: "商品", gift: "礼盒" }[type] || "其他物料"
    },
    onItemTypeChange(detail) {
      Object.assign(detail, {
        itemId: null,
        itemCode: "",
        itemName: "",
        productId: null,
        productName: "",
        productCode: "",
        sku: "",
        spec: "",
        unit: "",
        unitPrice: 0
      })
    },
    onItemSelected(selectedItem, idx) {
      const detail = this.form.details[idx]
      if (!detail) return
      if (!selectedItem) {
        Object.assign(detail, {
          itemId: null,
          itemCode: "",
          itemName: "",
          productId: null,
          productName: "",
          productCode: "",
          sku: "",
          spec: "",
          unit: "",
          unitPrice: 0
        })
        return
      }
      detail.itemType = selectedItem.itemType
      detail.itemId = selectedItem.itemId
      detail.itemCode = selectedItem.itemCode
      detail.itemName = selectedItem.itemName
      detail.productId = selectedItem.productId
      detail.productName = selectedItem.itemName
      detail.productCode = selectedItem.itemCode
      detail.sku = selectedItem.itemCode
      detail.spec = selectedItem.spec
      detail.unit = selectedItem.unit
      detail.unitPrice = selectedItem.salesPrice || 0
    },
    captureSalesDialogTrigger(event) {
      const source = event && (event.currentTarget || event.target)
      const trigger = source && typeof source.closest === "function"
        ? source.closest("button, a[href], input, select, textarea, [tabindex]:not([tabindex='-1'])")
        : null
      if (trigger && typeof trigger.focus === "function") this.lastSalesDialogTrigger = trigger
    },
    focusSalesTitle() {
      this.$nextTick(() => {
        const input = this.$refs.salesTitleInput
        if (input && typeof input.focus === "function") input.focus()
      })
    },
    restoreSalesDialogFocus() {
      const previous = this.lastSalesDialogTrigger
      const fallback = this.$refs.newSaleButton && this.$refs.newSaleButton.$el
      const previousAvailable = previous && document.documentElement.contains(previous) &&
        !previous.matches("[disabled], [aria-disabled='true']")
      const target = previousAvailable ? previous : fallback
      if (target && typeof target.focus === "function") target.focus()
      this.lastSalesDialogTrigger = null
    },
    openForm(row, event) {
      if (!this.ensureStoreContext()) return
      this.captureSalesDialogTrigger(event)
      if (!row) {
        this.resetForm()
        this.open = true
        this.loadCustomers()
        return
      }
      getSalesDetail(row.orderId).then(res => {
        const data = res.data || {}
        this.form = Object.assign({}, data, {
          orderDate: data.orderDate || this.defaultOrderDate(),
          details: (data.details || []).map(this.normalizeDetail)
        })
        this.customerOptions = data.customerId ? [{ customerId: data.customerId, customerName: data.customerName }] : []
        this.open = true
        this.loadCustomers(data.customerName)
      })
    },
    normalizeDetail(detail) {
      const itemType = detail.itemType || "product"
      return Object.assign({}, detail, {
        itemType,
        itemId: detail.itemId || detail.productId,
        itemCode: detail.itemCode || detail.productCode || detail.sku || "",
        itemName: detail.itemName || detail.productName || ""
      })
    },
    addDetail() { this.form.details.push({ itemType: null, itemId: null, itemCode: "", itemName: "", productId: null, productName: "", productCode: "", sku: "", spec: "", unit: "", quantity: 1, unitPrice: 0, amount: 0 }) },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    handleFormBeforeClose(done) {
      if (this.formSubmitting) return
      done()
    },
    closeForm() {
      if (this.formSubmitting) return
      this.open = false
    },
    validateEditorForm() {
      return new Promise(resolve => {
        const formRef = this.$refs.formRef
        if (!formRef || typeof formRef.validate !== "function") {
          resolve(false)
          return
        }
        formRef.validate(valid => resolve(Boolean(valid)))
      })
    },
    doSave(submitAfter) {
      if (this.formSubmitting) return Promise.resolve({ busy: true })
      if (!this.ensureStoreContext()) return Promise.resolve({ invalidContext: true })
      this.formSubmitting = true
      return this.validateEditorForm().then(valid => {
        if (!valid) return { invalid: true }
        return this.submitValidatedForm(submitAfter)
      }).catch(error => ({ failed: true, error })).finally(() => {
        this.formSubmitting = false
      })
    },
    submitValidatedForm(submitAfter) {
      if (!this.form.details || this.form.details.length === 0) {
        this.$modal.msgError("请添加至少一条明细")
        return { invalid: true }
      }
      const validDetails = this.form.details.filter(d => d.itemType && (d.itemId || d.productId))
      if (validDetails.length !== this.form.details.length) {
        this.$modal.msgError("请为所有明细行选择物料")
        return { invalid: true }
      }
      const zeroPriceItem = this.form.details.find(d => this.toNumber(d.unitPrice) <= 0)
      if (zeroPriceItem) {
        this.$modal.msgError("物料 [" + (zeroPriceItem.itemName || zeroPriceItem.productName || "未命名") + "] 售价必须大于0，请先维护售价")
        return { invalid: true }
      }
      this.onCustomerChange(this.form.customerId)
      if (!this.form.customerName) {
        this.$modal.msgError("请选择有效客户档案")
        return { invalid: true }
      }
      this.form.totalAmount = this.form.details.reduce((sum, d) => sum + (d.quantity * d.unitPrice || 0), 0)
      const payload = Object.assign({}, this.form, {
        details: this.form.details.map(detail => Object.assign({}, detail))
      })
      const api = submitAfter ? submitSales : saveSales
      return api(payload).then(() => {
        this.$modal.msgSuccess(submitAfter ? "提交成功" : "已保存草稿")
        this.open = false
        this.getList()
        return { success: true }
      })
    },
    doSubmit(row) {
      if (!this.ensureStoreContext()) return
      getSalesDetail(row.orderId).then(res => {
        submitSales(res.data).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() })
      })
    },
    doCreateDeliveryNotice(row) {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认对销售单 [" + row.orderNo + "] 生成发货通知?").then(() => {
        createDeliveryNoticeApi(row.orderId).then(res => {
          this.$modal.msgSuccess(res.msg || "生成发货通知成功")
          this.getList()
        })
      })
    },
    doCancel(row) {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认取消销售单 [" + row.orderNo + "]?").then(() => {
        cancelSales(row.orderId).then(() => { this.$modal.msgSuccess("已取消"); this.getList() })
      })
    },
    showDetail(orderId) {
      this.detailOpen = true
      this.detailLoading = true
      this.detailOrder = { details: [] }
      getSalesDetail(orderId).then(res => {
        const data = res.data || {}
        this.detailOrder = Object.assign({}, data, {
          details: data.details || []
        })
      }).finally(() => {
        this.detailLoading = false
      })
    },
    handleExport() {
      if (!this.ensureStoreContext()) return
      this.$modal.confirm("确认导出当前查询条件下的销售单数据？").then(() => {
        this.download("inventory/sales/export", { ...this.queryParams }, this.exportFileName("销售单数据"))
      })
    }
  }
}
</script>
<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.mb8 { margin-bottom: 8px }
.order-detail { min-height: 120px; }
.product-name {
  color: #303133;
  font-weight: 500;
  line-height: 20px;
}
.product-meta {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
.sales-detail-row {
  align-items: flex-start;
}
.action-tooltip-wrap {
  display: inline-block;
}
.detail-product-meta {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.amount-txt { line-height: 28px; font-size: 13px; }
.list-load-error {
  align-items: center;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  color: #f56c6c;
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
}
</style>
