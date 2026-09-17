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
            <span v-if="scope.row.status === 'submitted'" v-hasPermi="['inv:sales:add']">
              <el-button v-hasPermi="['inv:deliveryNotice:add']" type="text" size="mini" :disabled="!isStoreContext" @click="openWarehouseRepair(scope.row)">补全缺仓</el-button>
            </span>
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
          <el-table-column label="出库仓库" min-width="120">
            <template slot-scope="scope">{{ scope.row.warehouseName || (scope.row.warehouseId ? '仓库 ' + scope.row.warehouseId : '未选择') }}</template>
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

    <el-dialog title="受控补全仓库并生成通知" :visible.sync="repairOpen" width="760px" append-to-body
      :close-on-click-modal="false" :close-on-press-escape="!repairSubmitting" :show-close="!repairSubmitting">
      <el-alert title="只处理已核对清单中的历史缺仓单。不能更改已有仓库、数量、价格或客户。"
        type="warning" :closable="false" class="mb12"/>
      <p>销售单：{{ repairForm.orderNo }}；客户：{{ repairForm.customerName }}</p>
      <warehouse-select v-if="repairOpen" :key="repairRevision" v-model="repairDefaultWarehouseId" purpose="deliverySource"
        :scope-dept-id="selectedDeptContext.deptId" autoload @loaded="onRepairWarehousesLoaded" @change="applyRepairDefault" :disabled="repairSubmitting || repairUncertain"/>
      <el-table :data="repairForm.details" size="small">
        <el-table-column label="缺仓明细" prop="productName" min-width="140"/>
        <el-table-column label="原数量" prop="quantity" width="100"/>
        <el-table-column label="原单价" prop="unitPrice" width="100"/>
        <el-table-column label="补入仓库" min-width="180"><template slot-scope="scope">
          <el-select v-model="scope.row.warehouseId" :disabled="repairSubmitting || repairUncertain" placeholder="请选择经核对的仓库">
            <el-option v-for="warehouse in repairWarehouseOptions" :key="warehouse.deptId" :label="warehouse.deptName" :value="warehouse.deptId"/>
          </el-select>
        </template></el-table-column>
      </el-table>
      <p v-if="repairUncertain" role="alert">结果待核实，不能重复补仓。<el-button type="text" @click="refreshWarehouseRepair">刷新核对原单</el-button></p>
      <div slot="footer">
        <el-button :disabled="repairSubmitting" @click="repairOpen = false">关闭</el-button>
        <el-button type="primary" :loading="repairSubmitting" :disabled="repairSubmitting || repairUncertain || !repairWarehouseOptions.length" @click="submitWarehouseRepair">确认映射并生成通知</el-button>
      </div>
    </el-dialog>

    <el-dialog
      :title="form.orderId ? '编辑销售单' : '新建销售单'"
      :visible.sync="open"
      width="940px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!formSubmitting"
      :show-close="!formSubmitting"
      :before-close="handleFormBeforeClose"
      @opened="focusSalesTitle"
      @closed="restoreSalesDialogFocus"
    >
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="formSubmitting" label-width="96px">
        <el-form-item label="标题" prop="orderTitle">
          <el-input ref="salesTitleInput" v-model="form.orderTitle" aria-label="销售单标题" maxlength="120" @input="titleEdited = true"/>
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
          <el-button v-if="canQuickCreateCustomer" type="text" :disabled="formSubmitting" @click="openQuickCustomer">新增客户并选中</el-button>
          <div v-if="recentCustomers.length">最近使用：<el-button v-for="customer in recentCustomers" :key="customer.id" type="text" :disabled="formSubmitting" @click="chooseRecentCustomer(customer)">{{ customer.label }}</el-button></div>
        </el-form-item>
        <el-form-item label="销售日期" prop="orderDate">
          <el-date-picker v-model="form.orderDate" type="date" placeholder="选择日期" value-format="yyyy-MM-dd" style="width:100%"/>
        </el-form-item>
        <el-form-item label="默认出库仓库">
          <warehouse-select v-if="open" :key="editorRevision" v-model="defaultWarehouseId"
            purpose="deliverySource" :scope-dept-id="selectedDeptContext.deptId" autoload
            @loaded="onWarehousesLoaded" @change="applyDefaultWarehouse" @load-error="onWarehouseLoadError"/>
          <div class="detail-product-meta">仅带入空仓行；每行可单独调整。请核对仓库后提交，多仓明细保留在同一销售单中。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500"/>
        </el-form-item>
        <el-divider content-position="left">销售明细</el-divider>
        <el-button size="small" icon="el-icon-goods" :disabled="formSubmitting" @click="batchPickerOpen = true">批量选择物料</el-button>
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
            <label :for="'sales-quantity-' + idx">数量<span class="sales-field-context">（{{ item.itemName || item.productName || ('第 ' + (idx + 1) + ' 行') }}）</span></label>
            <el-input :id="'sales-quantity-' + idx" v-model="item.quantity" inputmode="decimal" aria-label="销售数量，最多两位小数" placeholder="数量" size="small" style="width:100%"/>
            <span v-if="!validInventoryQuantity(item.quantity)" class="text-danger" role="alert">请输入大于 0 的数量，最多两位小数</span>
          </el-col>
          <el-col :span="4">
            <label :for="'sales-price-' + idx">单价<span class="sales-field-context">（{{ item.itemName || item.productName || ('第 ' + (idx + 1) + ' 行') }}）</span></label>
            <el-input-number :id="'sales-price-' + idx" :label="'单价（' + (item.itemName || item.productName || ('第 ' + (idx + 1) + ' 行')) + '）'" v-model="item.unitPrice" :min="0.01" :precision="2" placeholder="单价" size="small" style="width:100%"/>
          </el-col>
          <el-col :span="2">
            <div>金额<span class="sales-field-context">（{{ item.itemName || item.productName || ('第 ' + (idx + 1) + ' 行') }}）</span></div>
            <span class="amount-txt" aria-label="明细金额">{{ (item.quantity * item.unitPrice || 0).toFixed(2) }}</span>
          </el-col>
          <el-col :span="2">
            <el-button type="text" size="mini" style="color:#F56C6C" @click="removeDetail(idx)">删除</el-button>
          </el-col>
          <el-col :span="24" class="mb8">
            <span>出库仓库：</span>
            <el-select v-model="item.warehouseId" size="small" filterable clearable placeholder="草稿可暂不选，提交前必填">
              <el-option v-for="warehouse in warehouseOptions" :key="warehouse.deptId" :label="warehouse.deptName" :value="warehouse.deptId"/>
              <el-option v-if="item.warehouseId && !warehouseOptions.some(w => String(w.deptId) === String(item.warehouseId))"
                :value="item.warehouseId" :label="item.warehouseName || ('仓库 ' + item.warehouseId + '（需核对授权）')" disabled/>
            </el-select>
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
    <sales-material-picker :open="batchPickerOpen && open" :context-key="choiceScope + ':' + editorRevision" :recent="recentMaterials" @close="batchPickerOpen = false" @confirm="addBatchMaterials" />
    <mobile-quick-customer-form :open="quickCustomerOpen && open" v-model="quickCustomerDraft" :saving="quickCustomerSaving" :error="quickCustomerError" @close="quickCustomerOpen = false" @submit="createQuickCustomer" />
    <el-dialog title="保留未保存的销售内容" :visible.sync="closeChoiceOpen" width="min(520px, 96vw)" append-to-body :close-on-click-modal="false" :show-close="false" :close-on-press-escape="false">
      <p>当前内容尚未保存，可以继续填写，也可保留内容后离开。保留的内容仅在本次登录、当前门店页面中恢复。</p>
      <div slot="footer">
        <el-button :disabled="formSubmitting" @click="finishCloseChoice('continue')">继续编辑</el-button>
        <el-button :disabled="formSubmitting" @click="finishCloseChoice('discard')">放弃修改</el-button>
        <el-button :disabled="formSubmitting" @click="finishCloseChoice('keep')">保留并关闭</el-button>
        <el-button v-hasPermi="['inv:sales:add']" type="primary" :loading="formSubmitting" @click="finishCloseChoice('save')">保存草稿</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import SalesMaterialPicker from "@/views/inventory/components/SalesMaterialPicker.vue"
import MobileQuickCustomerForm from "@/views/mobile/feature/components/MobileQuickCustomerForm.vue"
import { createCustomerServiceCard, getCustomerServiceCardCapabilities } from "@/api/inventory/customer"
import { checkPermi } from "@/utils/permission"
const { createCustomerDraft, normalizeQuickCustomerPayload, normalizeCustomerServiceCardCapabilities, isCustomerQuickCreateAllowed } = require("@/views/mobile/feature/mobileQuickCustomer")
const { rememberSalesChoice, recentSalesChoices } = require("@/utils/salesRecentChoices")
const { validInventoryQuantity } = require("@/utils/inventoryQuantity")
import { listSales, getSalesDetail, saveSales, submitSales, cancelSales } from "@/api/inventory/sales"
import { createDeliveryNotice as createDeliveryNoticeApi, repairSalesWarehouses } from "@/api/inventory/deliveryNotice"
import { listCustomerOptions } from "@/api/inventory/customer"
import { getSelectedDeptContext, isSelectedStore } from "@/utils/shopContext"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { parseTime } from "@/utils/common"
import InventoryItemSelect from "@/views/inventory/components/InventoryItemSelect"
import WarehouseSelect from "@/views/inventory/components/WarehouseSelect"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { defaultSalesWarehouse, fillEmptySalesWarehouses } = require("@/utils/salesWarehouse")
export default {
  name: "InvSales",
  components: { SalesMaterialPicker, MobileQuickCustomerForm, InventoryItemSelect, WarehouseSelect },
  data() {
    return {
      closeChoiceOpen: false, retainedDraft: null, formBaseline: "", autoTitle: "", titleEdited: false,
      batchPickerOpen: false, recentRevision: 0,
      quickCustomerOpen: false, quickCustomerSaving: false, quickCustomerError: "", quickCustomerDraft: {}, quickCustomerRequestKey: "", customerKeyword: "", customerCapability: {},
      loading: false, total: 0, list: [], listError: "", listRequestSequence: 0, activeListQuerySnapshot: "",
      open: false, formSubmitting: false, detailOpen: false, detailLoading: false,
      customerLoading: false, customerOptions: [], dateRange: [],
      lastSalesDialogTrigger: null,
      repairOpen: false, repairSubmitting: false, repairUncertain: false, repairRevision: 0,
      repairForm: { details: [] }, repairWarehouseOptions: [], repairDefaultWarehouseId: undefined,
      editorRevision: 0, contextRevision: 0, defaultWarehouseId: undefined, warehouseOptions: [], warehousesLoaded: false,
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
    choiceScope() { return JSON.stringify([this.$store.getters.id, this.$store.state.user.sessionRevision, this.selectedDeptContext.deptId]) },
    recentCustomers() { void this.recentRevision; return recentSalesChoices(this.choiceScope, "customer") },
    recentMaterials() { void this.recentRevision; return recentSalesChoices(this.choiceScope, "material") },
    formDirty() { return this.open && this.formBaseline !== JSON.stringify(this.form) },
    customerContextKey() { return String(this.selectedDeptContext.deptId || "") + "|STORE" },
    canQuickCreateCustomer() { return this.open && this.isStoreContext && isCustomerQuickCreateAllowed({ entity: "customer", field: { quickCreate: true }, hasPermission: checkPermi(["inv:customerCard:add"]), capability: this.customerCapability, contextKey: this.customerContextKey }) },
    selectedDeptContext() {
      void this.contextRevision
      return getSelectedDeptContext()
    },
    isStoreContext() {
      void this.contextRevision
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
  created() {
    this._deptChanged = () => this.handleContextChanged()
    window.addEventListener("erp:dept-changed", this._deptChanged)
    this.getList()
  },
  beforeRouteLeave(to, from, next) {
    if (this.formSubmitting || this.quickCustomerSaving) { this.$modal.msgWarning("请等待当前保存完成"); next(false); return }
    if (!this.formDirty) { next(); return }
    this._pendingNavigation = next
    this.closeChoiceOpen = true
  },
  deactivated() { this.operationScope().deactivate(); this.batchPickerOpen = false; this.quickCustomerOpen = false },
  activated() { this.operationScope().activate(); this.getList() },
  beforeDestroy() {
    window.removeEventListener("erp:dept-changed", this._deptChanged)
    this.operationScope().deactivate()
  },
  watch: {
    "form.orderDate"() { this.updateAutomaticTitle() },
    "$store.state.user.sessionRevision"() { this.retainedDraft = null; this.handleContextChanged() },
    "$store.getters.id"() { this.handleContextChanged() },
    "$store.getters.token"() { this.handleContextChanged() },
    open(value) { if (!value) this.invalidateEditor() },
    detailOpen(value) { if (!value) this.operationScope().invalidate("detail") },
    repairOpen(value) { if (!value) { this.repairRevision += 1; this.operationScope().invalidate("repair"); this.repairSubmitting = false } }
  },
  methods: {
    validInventoryQuantity,
    openWarehouseRepair(row) {
      if (!this.ensureStoreContext()) return
      this.repairRevision += 1
      const operation = this.operationScope().begin("repair", this.repairRevision)
      this.repairWarehouseOptions = []
      this.repairDefaultWarehouseId = undefined
      this.repairUncertain = false
      return getSalesDetail(row.orderId, { silentError: true }).then(res => {
        if (!this.operationScope().isCurrent(operation, this.repairRevision)) return { discarded: true }
        const data = res.data || {}
        const missing = (data.details || []).filter(detail => !detail.warehouseId)
        if (data.status !== "submitted" || !missing.length) {
          this.$modal.msgWarning("原单已无待补空仓明细，或状态已变化，请核对原单和现有发货通知")
          return { unavailable: true }
        }
        this.repairForm = { ...data, details: missing.map(detail => ({ ...detail, warehouseId: undefined })) }
        this.repairOpen = true
      }).catch(error => {
        if (this.operationScope().isCurrent(operation, this.repairRevision)) this.$modal.msgError("读取原单失败，请重试")
        return { failed: true, error }
      })
    },
    onRepairWarehousesLoaded(options) {
      if (!this.repairOpen) return
      this.repairWarehouseOptions = options || []
      // Historical assignments require an explicit reviewed mapping; no automatic default.
    },
    applyRepairDefault() {
      this.repairForm.details = fillEmptySalesWarehouses(this.repairForm.details, this.repairDefaultWarehouseId)
    },
    refreshWarehouseRepair() {
      const operation = this.operationScope().begin("repair", this.repairRevision)
      const orderId = this.repairForm.orderId
      return getSalesDetail(orderId, { silentError: true }).then(res => {
        if (!this.operationScope().isCurrent(operation, this.repairRevision)) return
        const data = res.data || {}
        if (data.status !== "submitted" || !(data.details || []).some(detail => !detail.warehouseId)) {
          this.$modal.msgWarning("原单状态或仓库已变化，请核对现有发货通知；本窗口不再补仓")
          this.repairOpen = false
          this.getList()
          return
        }
        const previous = new Map((this.repairForm.details || []).map(detail => [String(detail.detailId), detail.warehouseId]))
        const missing = (data.details || []).filter(detail => !detail.warehouseId)
        this.repairForm = { ...data, details: missing.map(detail => ({ ...detail, warehouseId: previous.get(String(detail.detailId)) })) }
        this.repairUncertain = false
      }).catch(error => {
        if (this.operationScope().isCurrent(operation, this.repairRevision)) this.$modal.msgError("核对原单失败，结果仍待确认，请稍后重试")
        return { failed: true, error }
      })
    },
    submitWarehouseRepair() {
      if (this.repairSubmitting || this.repairUncertain) return Promise.resolve({ busy: true })
      const details = this.repairForm.details || []
      if (!details.length || details.some(detail => !this.repairWarehouseOptions.some(warehouse => String(warehouse.deptId) === String(detail.warehouseId)))) {
        this.$modal.msgError("请为全部空仓明细选择经核对的授权仓库")
        return Promise.resolve({ invalid: true })
      }
      const orderId = this.repairForm.orderId
      const payload = { version: this.repairForm.version, assignments: details.map(detail => ({ detailId: detail.detailId, warehouseId: detail.warehouseId })) }
      const operation = this.operationScope().begin("repair", this.repairRevision)
      const summary = details.map(detail => (detail.itemName || detail.productName) + " → " + this.repairWarehouseOptions.find(w => String(w.deptId) === String(detail.warehouseId)).deptName).join("；")
      this.repairSubmitting = true
      let sent = false
      return this.$modal.confirm("确认按已核对清单补全销售单 [" + this.repairForm.orderNo + "] 并生成通知？" + summary).then(() => {
        if (!this.operationScope().isCurrent(operation, this.repairRevision)) return { discarded: true }
        sent = true
        return repairSalesWarehouses(orderId, payload, { silentError: true }).then(() => {
          if (!this.operationScope().isCurrent(operation, this.repairRevision)) return { discarded: true }
          this.$modal.msgSuccess("仓库已补全并生成发货通知")
          this.repairOpen = false
          return this.getList()
        })
      }).catch(error => {
        if (sent && this.operationScope().isCurrent(operation, this.repairRevision)) {
          this.repairUncertain = true
          this.$modal.msgError("补仓结果待核实，请先刷新核对原单后再决定是否重试")
        }
        return { failed: true, error }
      }).finally(() => {
        if (this.operationScope().isCurrent(operation, this.repairRevision)) this.repairSubmitting = false
      })
    },
    operationScope() {
      if (!this._salesScope) this._salesScope = createUiOperationScope(() => ({
        actorId: String((this.$store && this.$store.getters.id) || ""),
        deptId: String(getSelectedDeptContext().deptId || ""), revision: this.contextRevision
      }))
      return this._salesScope
    },
    handleContextChanged() {
      this.closeChoiceOpen = false; this.batchPickerOpen = false; this.quickCustomerOpen = false; this.quickCustomerSaving = false; this.customerCapability = {}
      if (this._pendingNavigation) { this._pendingNavigation(false); this._pendingNavigation = null }
      this.contextRevision += 1
      this.operationScope().invalidate()
      this.invalidateEditor()
      this.open = false
      this.detailOpen = false
      this.repairOpen = false
      this.getList()
    },
    invalidateEditor() {
      this.editorRevision += 1
      ;["editor", "save", "customers"].forEach(lane => this.operationScope().invalidate(lane))
      this.formSubmitting = false
      this.customerLoading = false
      this.warehouseOptions = []
      this.warehousesLoaded = false
    },
    onWarehousesLoaded(warehouses) {
      if (!this.open) return
      this.warehouseOptions = warehouses || []
      this.warehousesLoaded = true
      this.defaultWarehouseId = defaultSalesWarehouse(this.warehouseOptions, getSelectedDeptContext().deptId, this.defaultWarehouseId)
      this.applyDefaultWarehouse()
    },
    onWarehouseLoadError() {
      if (this.open) this.$modal.msgWarning("仓库选项加载失败，请点击仓库选择框重试；已录明细已保留")
    },
    applyDefaultWarehouse() {
      this.form.details = fillEmptySalesWarehouses(this.form.details, this.defaultWarehouseId)
    },

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
      const operation = this.operationScope().begin("list")
      const query = this.buildQuery()
      const querySnapshot = JSON.stringify(query)
      const requestSequence = ++this.listRequestSequence
      this.activeListQuerySnapshot = querySnapshot
      this.loading = true
      this.listError = ""
      this.list = []
      this.total = 0
      return listSales(query).then(res => {
        if (!this.operationScope().isCurrent(operation) || !this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true }
        this.list = res.rows || []
        this.total = res.total || 0
        return res
      }).catch(error => {
        if (!this.operationScope().isCurrent(operation) || !this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true, error }
        this.list = []
        this.total = 0
        this.listError = "销售单列表加载失败，请重试。"
        return { failed: true, error }
      }).finally(() => {
        if (this.operationScope().isCurrent(operation) && this.isCurrentListRequest(requestSequence, querySnapshot)) this.loading = false
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
      this.defaultWarehouseId = undefined
      this.titleEdited = false; this.autoTitle = ""
      this.formBaseline = JSON.stringify(this.form)
    },
    defaultOrderDate() {
      return parseTime(new Date(), "{y}-{m}-{d}")
    },
    customerLabel(customer) {
      if (!customer) return ""
      return customer.customerName + (customer.customerCode ? "（" + customer.customerCode + "）" : "")
    },
    loadCustomers(keyword) {
      this.customerKeyword = keyword || ""
      if (!this.isStoreContext) {
        this.customerOptions = []
        return Promise.resolve([])
      }
      const operation = this.operationScope().begin("customers", this.editorRevision)
      this.customerLoading = true
      return listCustomerOptions(keyword ? String(keyword).trim() : undefined, { silentError: true }).then(res => {
        if (!this.operationScope().isCurrent(operation, this.editorRevision)) return []
        const selected = this.customerOptions.find(item => String(item.customerId) === String(this.form.customerId))
        this.customerOptions = res.data || []
        if (selected && !this.customerOptions.some(item => String(item.customerId) === String(selected.customerId))) {
          this.customerOptions.unshift(selected)
        }
        return this.customerOptions
      }).catch(() => {
        if (!this.operationScope().isCurrent(operation, this.editorRevision)) return []
        this.$modal.msgWarning("客户选项加载失败，请检查客户档案权限或稍后重试")
        return this.customerOptions
      }).finally(() => { if (this.operationScope().isCurrent(operation, this.editorRevision)) this.customerLoading = false })
    },
    onCustomerChange(customerId) {
      const customer = this.customerOptions.find(item => String(item.customerId) === String(customerId))
      this.form.customerName = customer ? customer.customerName : ""
      this.updateAutomaticTitle()
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
      if (this.formSubmitting || this.quickCustomerSaving) return Promise.resolve({ busy: true })
      if (!this.ensureStoreContext()) return
      this.invalidateEditor()
      this.captureSalesDialogTrigger(event)
      const operation = this.operationScope().begin("editor", this.editorRevision)
      if (!row) {
        this.resetForm()
        if (this.retainedDraft && this.retainedDraft.scope === this.choiceScope) {
          this.form = JSON.parse(JSON.stringify(this.retainedDraft.form))
          this.formBaseline = this.retainedDraft.baseline
          this.titleEdited = this.retainedDraft.titleEdited
          this.customerOptions = this.retainedDraft.customers
          this.defaultWarehouseId = this.retainedDraft.defaultWarehouseId
        }
        this.open = true
        this.loadCustomerCapability()
        this.loadCustomers(this.form.customerName)
        return
      }
      return getSalesDetail(row.orderId).then(res => {
        if (!this.operationScope().isCurrent(operation, this.editorRevision)) return { discarded: true }
        const data = res.data || {}
        this.form = Object.assign({}, data, {
          orderDate: data.orderDate || this.defaultOrderDate(),
          details: (data.details || []).map(this.normalizeDetail)
        })
        this.customerOptions = data.customerId ? [{ customerId: data.customerId, customerName: data.customerName }] : []
        this.formBaseline = JSON.stringify(this.form)
        this.titleEdited = true
        this.open = true
        this.loadCustomerCapability()
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
    addDetail() { this.form.details.push({ warehouseId: this.defaultWarehouseId, itemType: null, itemId: null, itemCode: "", itemName: "", productId: null, productName: "", productCode: "", sku: "", spec: "", unit: "", quantity: 1, unitPrice: 0, amount: 0 }) },
    removeDetail(idx) { this.form.details.splice(idx, 1) },
    handleFormBeforeClose(done) {
      if (this.formSubmitting || this.quickCustomerSaving) return
      if (this.formDirty) { this.closeChoiceOpen = true; return }
      done()
    },
    closeForm() { return this.handleFormBeforeClose(() => { this.open = false }) },
    finishCloseChoice(action) {
      if (this.formSubmitting || this.quickCustomerSaving) return
      if (action === "save") return this.doSave(false).then(result => { if (result && result.success) this.finishCloseChoice("discard") })
      if (action === "keep") this.retainedDraft = { scope: this.choiceScope, form: JSON.parse(JSON.stringify(this.form)), baseline: this.formBaseline, titleEdited: this.titleEdited, customers: this.customerOptions.map(row => ({ ...row })), defaultWarehouseId: this.defaultWarehouseId }
      if (action === "discard") this.retainedDraft = null
      this.closeChoiceOpen = false
      if (action !== "continue") this.open = false
      if (this._pendingNavigation) { const next = this._pendingNavigation; this._pendingNavigation = null; next(action === "continue" ? false : undefined) }
    },
    updateAutomaticTitle() {
      if (this.titleEdited || !this.form || this.form.orderId) return
      const title = this.form.customerName ? [this.form.customerName, this.form.orderDate].filter(Boolean).join(" ").slice(0, 120) : ""
      this.form.orderTitle = title; this.autoTitle = title
    },
    loadCustomerCapability() {
      const operation = this.operationScope().begin("customer-capability", this.editorRevision), contextKey = this.customerContextKey
      this.customerCapability = {}
      if (!checkPermi(["inv:customerCard:add"]) || !checkPermi(["inv:customerCard:list"])) return Promise.resolve()
      return getCustomerServiceCardCapabilities().then(response => {
        if (this.operationScope().isCurrent(operation, this.editorRevision)) this.customerCapability = normalizeCustomerServiceCardCapabilities(response, contextKey)
      }).catch(() => {})
    },
    openQuickCustomer() {
      if (!this.canQuickCreateCustomer || this.formSubmitting) return
      this.quickCustomerDraft = createCustomerDraft(this.customerKeyword)
      this.quickCustomerRequestKey = "desktop-card-" + crypto.randomUUID()
      this.quickCustomerError = ""; this.quickCustomerOpen = true
    },
    createQuickCustomer() {
      if (this.quickCustomerSaving || !this.canQuickCreateCustomer) return
      let payload
      try { payload = normalizeQuickCustomerPayload(this.quickCustomerDraft) } catch (error) { this.quickCustomerError = error.message; return }
      const operation = this.operationScope().begin("quick-customer", this.editorRevision), deptId = this.selectedDeptContext.deptId
      const assertContext = () => { if (!this.operationScope().isCurrent(operation, this.editorRevision)) throw new Error("账号或组织已变化，请回到原客户创建窗口核对") }
      this.quickCustomerSaving = true; this.quickCustomerError = ""
      return createCustomerServiceCard({ ...payload, requestKey: this.quickCustomerRequestKey, sourceClient: "DESKTOP" }, { deptId, assertContext }).then(response => {
        if (!this.operationScope().isCurrent(operation, this.editorRevision) || !this.quickCustomerOpen) return
        if (!response.data || !response.data.customerId) throw new Error("客户已处理，请搜索客户名称核对结果")
        this.customerOptions.unshift(response.data)
        this.form.customerId = response.data.customerId
        this.onCustomerChange(response.data.customerId)
        this.quickCustomerOpen = false
      }).catch(error => { if (this.operationScope().isCurrent(operation, this.editorRevision)) this.quickCustomerError = error.message || "创建失败，重试将使用同一操作编号" })
        .finally(() => { if (this.operationScope().isCurrent(operation, this.editorRevision)) this.quickCustomerSaving = false })
    },
    chooseRecentCustomer(choice) {
      const operation = this.operationScope().begin("recent-customer", this.editorRevision)
      return listCustomerOptions(choice.label, { silentError: true }).then(response => {
        if (!this.open || !this.operationScope().isCurrent(operation, this.editorRevision)) return
        const customer = (response.data || []).find(row => String(row.customerId) === choice.id)
        if (!customer) { this.$modal.msgWarning("该客户当前不可选，请重新搜索"); return }
        this.customerOptions = [customer, ...this.customerOptions.filter(row => String(row.customerId) !== choice.id)]
        this.form.customerId = customer.customerId; this.onCustomerChange(customer.customerId)
      }).catch(() => { if (this.operationScope().isCurrent(operation, this.editorRevision)) this.$modal.msgError("客户核对失败，请重试") })
    },
    addBatchMaterials(items) {
      if (!this.open || !this.batchPickerOpen || this.formSubmitting) return
      items.forEach(item => {
        const existing = this.form.details.find(row => row.itemType === item.itemType && String(row.itemId) === String(item.itemId))
        if (existing) return
        this.addDetail(); this.onItemSelected(item, this.form.details.length - 1)
      })
      this.batchPickerOpen = false
    },
    rememberChoices() {
      rememberSalesChoice(this.choiceScope, "customer", { id: this.form.customerId, label: this.form.customerName })
      this.form.details.forEach(row => rememberSalesChoice(this.choiceScope, "material", { id: row.itemId || row.productId, type: row.itemType || "product", label: row.itemName || row.productName }))
      this.recentRevision += 1
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
      const operation = this.operationScope().begin("save", this.editorRevision)
      this.formSubmitting = true
      return this.validateEditorForm().then(valid => {
        if (!this.operationScope().isCurrent(operation, this.editorRevision)) return { discarded: true }
        if (!valid) return { invalid: true }
        return this.submitValidatedForm(submitAfter, operation)
      }).catch(error => ({ failed: true, error })).finally(() => {
        if (this.operationScope().isCurrent(operation, this.editorRevision)) this.formSubmitting = false
      })
    },
    submitValidatedForm(submitAfter, operation) {
      if (!this.form.details || this.form.details.length === 0) {
        this.$modal.msgError("请添加至少一条明细")
        return { invalid: true }
      }
      const validDetails = this.form.details.filter(d => d.itemType && (d.itemId || d.productId))
      if (validDetails.length !== this.form.details.length) {
        this.$modal.msgError("请为所有明细行选择物料")
        return { invalid: true }
      }
      if (submitAfter && (!this.warehousesLoaded || this.form.details.some(row => !row.warehouseId ||
        !this.warehouseOptions.some(warehouse => String(warehouse.deptId) === String(row.warehouseId))))) {
        this.$modal.msgError("请为每条明细选择已授权的出库仓库，核对后提交")
        return { invalid: true }
      }
      if (this.form.details.some(row => !validInventoryQuantity(row.quantity))) { this.$modal.msgError("数量必须大于 0 且最多两位小数，请检查标红的明细"); return { invalid: true } }
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
        if (operation && !this.operationScope().isCurrent(operation, this.editorRevision)) return { discarded: true }
        this.rememberChoices()
        this.retainedDraft = null
        this.formBaseline = JSON.stringify(this.form)
        this.$modal.msgSuccess(submitAfter ? "提交成功" : "已保存草稿")
        this.open = false
        this.getList()
        return { success: true }
      })
    },
    doSubmit(row) {
      // Open the editor so warehouse defaults and the operator's confirmation are visible.
      return this.openForm(row)
    },
    doCreateDeliveryNotice(row) {
      if (!this.ensureStoreContext()) return
      const target = { orderId: row.orderId, version: row.version, orderNo: row.orderNo }
      const operation = this.operationScope().begin("createNotice")
      return this.$modal.confirm("确认对销售单 [" + target.orderNo + "] 生成发货通知?").then(() => {
        if (!this.operationScope().isCurrent(operation)) return { discarded: true }
        return createDeliveryNoticeApi(target.orderId, target.version).then(res => {
          if (!this.operationScope().isCurrent(operation)) return { discarded: true }
          this.$modal.msgSuccess(res.msg || "生成发货通知成功")
          return this.getList()
        })
      }).catch(error => ({ failed: true, error }))
    },
    doCancel(row) {
      if (!this.ensureStoreContext()) return
      const target = { orderId: row.orderId, version: row.version, orderNo: row.orderNo }
      const operation = this.operationScope().begin("cancel")
      return this.$modal.confirm("确认取消销售单 [" + target.orderNo + "]?").then(() => {
        if (!this.operationScope().isCurrent(operation)) return { discarded: true }
        return cancelSales(target.orderId, target.version).then(() => {
          if (!this.operationScope().isCurrent(operation)) return { discarded: true }
          this.$modal.msgSuccess("已取消")
          return this.getList()
        })
      }).catch(error => ({ failed: true, error }))
    },
    showDetail(orderId) {
      const operation = this.operationScope().begin("detail")
      this.detailOpen = true
      this.detailLoading = true
      this.detailOrder = { details: [] }
      return getSalesDetail(orderId).then(res => {
        if (!this.operationScope().isCurrent(operation)) return { discarded: true }
        const data = res.data || {}
        this.detailOrder = Object.assign({}, data, { details: data.details || [] })
      }).finally(() => {
        if (this.operationScope().isCurrent(operation)) this.detailLoading = false
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
.sales-field-context { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
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
