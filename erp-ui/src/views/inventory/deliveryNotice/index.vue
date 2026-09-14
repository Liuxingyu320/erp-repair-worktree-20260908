<template>
  <div class="app-container">
    <inventory-page-hero
      title="发货通知"
      eyebrow="履约出库"
      description="统一承接销售发货任务，快速核对客户、商品与发货状态，确保履约可追踪。"
      scope-text="当前门店发货任务"
      icon="el-icon-truck"
      tone="teal"
      :features="['待发任务', '发货确认', '状态跟踪']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="通知单号">
          <el-input v-model="queryParams.noticeNo" placeholder="通知单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="销售单号">
          <el-input v-model="queryParams.salesOrderNo" placeholder="销售单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="待发货" value="pending"/>
            <el-option label="发货中" value="delivering"/>
            <el-option label="已发货" value="completed"/>
            <el-option label="已取消" value="cancelled"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:deliveryNotice:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:deliveryNotice:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      :title="deliveryScopeTitle"
      :description="deliveryScopeDescription"
      type="info"
      show-icon
      :closable="false"
      class="mb12"
    />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small" :empty-text="deliveryNoticeEmptyText">
        <el-table-column label="通知单号" prop="noticeNo" width="170"/>
        <el-table-column label="销售单号" prop="salesOrderNo" width="170"/>
        <el-table-column label="客户" prop="customerName" min-width="140"/>
        <el-table-column label="状态" prop="status" width="100">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="销售门店" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ salesShopLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="发货仓库" min-width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ warehouseLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170"/>
        <el-table-column label="操作" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:deliveryNotice:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
            <el-button v-if="canDeliver(scope.row)" v-hasPermi="['inv:deliveryNotice:deliver']" type="text" size="mini" class="text-success" icon="el-icon-truck" @click="openDeliver(scope.row)">执行发货</el-button>
            <el-button v-if="scope.row.status === 'pending'" v-hasPermi="['inv:deliveryNotice:remove']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="handleCancel(scope.row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog title="发货通知详情" :visible.sync="detailOpen" width="760px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="通知单号">{{ detail.noticeNo }}</el-descriptions-item>
        <el-descriptions-item label="销售单号">{{ detail.salesOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="mini">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="销售门店">{{ salesShopLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="发货仓库">{{ warehouseLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">发货明细</el-divider>
      <el-table :data="detail.details" size="small" border>
        <el-table-column label="商品" prop="productName" min-width="160"/>
        <el-table-column label="出库仓库" min-width="120"><template slot-scope="scope">{{ lineWarehouseLabel(scope.row) }}</template></el-table-column>
        <el-table-column label="通知数量" prop="noticeQty" width="110" align="right"/>
        <el-table-column label="已发数量" prop="deliveredQty" width="110" align="right"/>
        <el-table-column label="未发数量" width="110" align="right">
          <template slot-scope="scope">{{ remainingQty(scope.row) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog title="执行发货" :visible.sync="deliverOpen" width="760px" append-to-body :close-on-click-modal="false"
      :close-on-press-escape="!submitLoading" :show-close="!submitLoading" :before-close="beforeDeliverClose">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="通知单号">{{ deliverDetail.noticeNo }}</el-descriptions-item>
        <el-descriptions-item label="销售单号">{{ deliverDetail.salesOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ deliverDetail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(deliverDetail.status) }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">发货明细</el-divider>
      <el-form label-width="86px" size="small" class="mb12">
        <el-form-item label="发货仓库" required>
          <WarehouseSelect
            v-if="deliverOpen"
            :key="deliverRevision"
            ref="deliverWarehouseSelect"
            v-model="deliverForm.warehouseId"
            purpose="deliverySource"
            :scope-dept-id="deliverDetail.shopDeptId"
            :allowed-warehouse-ids="deliveryWarehouseIds"
            :disabled="submitLoading || deliveryUncertain"
            autoload
            :clearable="false"
            placeholder="请选择发货仓库"
            width="260px"
            @loaded="onDeliverWarehousesLoaded"
            @load-error="onDeliveryWarehouseError"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="deliverWarehousesLoaded && deliverWarehouseOptions.length === 0"
        title="当前销售门店没有可用发货仓库"
        description="请先检查该门店的发货仓库授权，避免录入完发货数量后才被权限拦截。"
        type="warning"
        show-icon
        :closable="false"
        class="mb12"
      />
      <el-alert title="一张通知可包含多个仓库，本次只提交所选仓库的明细。切换仓库会保留其他组已录数量。"
        type="info" :closable="false" class="mb12"/>
      <div v-if="deliveryUncertain" role="alert" class="mb12">
        发货结果待核实，已暂停重复提交。请先重新读取通知和剩余数量。
        <el-button type="text" :disabled="submitLoading" @click="refreshDeliverState">核对最新发货结果</el-button>
      </div>
      <el-button size="mini" :disabled="submitLoading || deliveryUncertain || !activeDeliveryItems.length" @click="fillRemainingDelivery">本仓全部剩余</el-button>
      <el-table :data="activeDeliveryItems" size="small" border empty-text="请选择本通知有待发明细的授权仓库">
        <el-table-column label="商品" prop="productName" min-width="160"/>
        <el-table-column label="通知数量" prop="noticeQty" width="100" align="right"/>
        <el-table-column label="已发数量" prop="deliveredQty" width="100" align="right"/>
        <el-table-column label="未发数量" prop="remainingQty" width="100" align="right"/>
        <el-table-column label="本次发货" width="150">
          <template slot-scope="scope">
            <el-input-number v-model="scope.row.deliverQuantity" :disabled="submitLoading || deliveryUncertain" :min="0" :max="scope.row.remainingQty" :precision="2" size="small" style="width:100%"/>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button :disabled="submitLoading" @click="deliverOpen = false">关闭</el-button>
        <el-button v-hasPermi="['inv:deliveryNotice:deliver']" type="primary" :loading="submitLoading" :disabled="submitLoading || deliveryUncertain || !deliverWarehousesLoaded || !activeDeliveryItems.length || !deliverWarehouseOptions.length" @click="submitDeliver">确认发货</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listDeliveryNotice, getDeliveryNotice, deliverDeliveryNotice, cancelDeliveryNotice } from "@/api/inventory/deliveryNotice"
import { getBusinessEmptyText } from "@/utils/businessEmptyState"
import { getSelectedDeptContext } from "@/utils/shopContext"
import WarehouseSelect from "@/views/inventory/components/WarehouseSelect"
const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { defaultSalesWarehouse, deliveryWarehouseRows } = require("@/utils/salesWarehouse")
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "outbound",
    loadFocusedRow(noticeId) { return getDeliveryNotice(noticeId) },
    actions: {
      deliverDeliveryNoticeAll(row) {
        if (!this.canDeliver(row)) return this.showTodoBusinessHandled()
        this.openDeliver(row)
      }
    }
  })],
  name: "InvDeliveryNotice",
  components: { WarehouseSelect },
  data() {
    return {
      loading: false,
      submitLoading: false,
      total: 0,
      list: [],
      detailOpen: false,
      deliverOpen: false,
      deliverRevision: 0, contextRevision: 0, deliveryUncertain: false, attemptedDetailIds: [],
      deliverWarehousesLoaded: false,
      deliverWarehouseOptions: [],
      detail: { details: [] },
      deliverDetail: { details: [] },
      deliverForm: { warehouseId: undefined, items: [] },
      queryParams: { pageNum: 1, pageSize: 10, noticeNo: undefined, salesOrderNo: undefined, customerName: undefined, status: undefined }
    }
  },
  computed: {
    deliveryWarehouseIds() {
      return Array.from(new Set(this.deliverForm.items.filter(row => row.remainingQty > 0 && row.warehouseId).map(row => String(row.warehouseId))))
    },
    activeDeliveryItems() {
      return deliveryWarehouseRows(this.deliverForm.items, this.deliverForm.warehouseId).filter(row => row.remainingQty > 0)
    },
    selectedDeptContext() {
      void this.contextRevision
      return getSelectedDeptContext()
    },
    deliveryScopeTitle() {
      const context = this.selectedDeptContext
      if (context.isWarehouse) return "当前发货仓库视角：" + (context.deptName || "未命名仓库")
      if (context.isStore) return "当前销售门店视角：" + (context.deptName || "未命名门店")
      return "当前组织的发货通知"
    },
    deliveryScopeDescription() {
      if (this.selectedDeptContext.isWarehouse) {
        return "列表仅显示当前仓库可处理或已处理的通知；“销售门店”表示订单来源，“发货仓库”表示实际或计划扣减库存的仓库。"
      }
      return "“销售门店”表示订单来源，“发货仓库”表示实际或计划扣减库存的仓库。"
    },
    deliveryNoticeEmptyText() {
      return getBusinessEmptyText("deliveryNotice", "missingBaseline")
    }
  },
  created() {
    this._deptChanged = () => this.handleDeliveryContextChanged()
    window.addEventListener("erp:dept-changed", this._deptChanged)
    this.getList()
  },
  beforeDestroy() {
    window.removeEventListener("erp:dept-changed", this._deptChanged)
    this.operationScope().deactivate()
  },
  watch: {
    "$store.getters.id"() { this.handleDeliveryContextChanged() },
    "$store.getters.token"() { this.handleDeliveryContextChanged() },
    deliverOpen(value) { if (!value) this.invalidateDelivery() },
    detailOpen(value) { if (!value) this.operationScope().invalidate("detail") }
  },
  methods: {
    operationScope() {
      if (!this._deliveryScope) this._deliveryScope = createUiOperationScope(() => ({
        actorId: String((this.$store && this.$store.getters.id) || ""),
        deptId: String(getSelectedDeptContext().deptId || ""), revision: this.contextRevision
      }))
      return this._deliveryScope
    },
    invalidateDelivery() {
      this.deliverRevision += 1
      ;["deliverOpen", "deliverSubmit", "deliverRefresh"].forEach(lane => this.operationScope().invalidate(lane))
      this.submitLoading = false
    },
    handleDeliveryContextChanged() {
      this.contextRevision += 1
      this.operationScope().invalidate()
      this.invalidateDelivery()
      this.deliverOpen = false
      this.detailOpen = false
      this.getList()
    },
    beforeDeliverClose(done) { if (!this.submitLoading) done() },
    onDeliveryWarehouseError() {
      if (this.deliverOpen) this.$modal.msgWarning("仓库加载失败，请点击仓库选择框重试")
    },
    fillRemainingDelivery() {
      if (this.submitLoading || this.deliveryUncertain) return
      this.activeDeliveryItems.forEach(row => { row.deliverQuantity = row.remainingQty })
    },
    lineWarehouseLabel(row) {
      const warehouse = this.deliverWarehouseOptions.find(item => String(item.deptId) === String(row.warehouseId))
      return row.warehouseName || (warehouse && warehouse.deptName) || (row.warehouseId ? "仓库 " + row.warehouseId : "未选择")
    },
    getList() {
      const operation = this.operationScope().begin("list")
      const query = { ...this.queryParams }
      this.loading = true
      return this.loadTodoBusinessList(() => listDeliveryNotice(query)).then(res => {
        if (!this.operationScope().isCurrent(operation)) return { discarded: true }
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).finally(() => { if (this.operationScope().isCurrent(operation)) this.loading = false })
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, noticeNo: undefined, salesOrderNo: undefined, customerName: undefined, status: undefined }
      this.getList()
    },
    openDetail(row) {
      const operation = this.operationScope().begin("detail")
      this.detailOpen = true
      this.detail = { details: [] }
      return getDeliveryNotice(row.noticeId).then(res => {
        if (this.operationScope().isCurrent(operation)) this.detail = res.data || { details: [] }
      })
    },
    openDeliver(row) {
      this.invalidateDelivery()
      const operation = this.operationScope().begin("deliverOpen", this.deliverRevision)
      return getDeliveryNotice(row.noticeId).then(res => {
        if (!this.operationScope().isCurrent(operation, this.deliverRevision)) return { discarded: true }
        this.deliverDetail = res.data || { details: [] }
        this.deliverWarehousesLoaded = false
        this.deliverWarehouseOptions = []
        this.deliveryUncertain = false
        this.attemptedDetailIds = []
        this.deliverForm = { warehouseId: undefined, items: this.deliveryItems(this.deliverDetail.details) }
        this.deliverOpen = true
      })
    },
    deliveryItems(details, previousItems = [], resetIds = []) {
      return (details || []).map(item => {
        const previous = previousItems.find(row => String(row.detailId) === String(item.detailId))
        const remaining = this.remainingQty(item)
        const attempted = resetIds.some(id => String(id) === String(item.detailId))
        return {
          ...item,
          warehouseId: item.warehouseId,
          noticeQty: Number(item.noticeQty || 0), deliveredQty: Number(item.deliveredQty || 0), remainingQty: remaining,
          deliverQuantity: attempted ? 0 : previous ? Math.min(Number(previous.deliverQuantity || 0), remaining) : remaining
        }
      })
    },
    onDeliverWarehousesLoaded(warehouses) {
      if (!this.deliverOpen) return
      const ids = this.deliveryWarehouseIds
      this.deliverWarehouseOptions = (warehouses || []).filter(row => ids.includes(String(row.deptId)))
      this.deliverWarehousesLoaded = true
      this.deliverForm.warehouseId = defaultSalesWarehouse(this.deliverWarehouseOptions,
        getSelectedDeptContext().deptId, this.deliverForm.warehouseId || this.deliverDetail.warehouseId)
    },
    refreshDeliverState() {
      if (this.submitLoading) return Promise.resolve({ busy: true })
      const operation = this.operationScope().begin("deliverRefresh", this.deliverRevision)
      const noticeId = this.deliverDetail.noticeId
      this.submitLoading = true
      return getDeliveryNotice(noticeId).then(res => {
        if (!this.operationScope().isCurrent(operation, this.deliverRevision)) return { discarded: true }
        const data = res.data || { details: [] }
        this.deliverForm.items = this.deliveryItems(data.details, this.deliverForm.items, this.attemptedDetailIds)
        this.deliverDetail = data
        this.deliveryUncertain = false
        this.attemptedDetailIds = []
        this.onDeliverWarehousesLoaded(this.deliverWarehouseOptions)
        if (!this.canDeliver(data)) this.deliverOpen = false
        this.getList()
        return { refreshed: true }
      }).catch(error => {
        if (this.operationScope().isCurrent(operation, this.deliverRevision)) {
          this.deliveryUncertain = true
          this.$modal.msgWarning("最新发货结果读取失败，请重试核对后再操作")
        }
        return { failed: true, error }
      }).finally(() => {
        if (this.operationScope().isCurrent(operation, this.deliverRevision)) this.submitLoading = false
      })
    },
    submitDeliver() {
      if (this.submitLoading || this.deliveryUncertain) return Promise.resolve({ busy: true })
      const warehouseId = this.deliverForm.warehouseId
      if (!this.deliverWarehousesLoaded || !this.deliverWarehouseOptions.some(row => String(row.deptId) === String(warehouseId))) {
        this.$modal.msgError("请选择本通知有待发明细的授权仓库")
        return Promise.resolve({ invalid: true })
      }
      const rows = this.activeDeliveryItems
      if (rows.some(row => !Number.isFinite(Number(row.deliverQuantity)) || Number(row.deliverQuantity) < 0 || Number(row.deliverQuantity) > row.remainingQty)) {
        this.$modal.msgError("本次发货数量须在 0 到未发数量之间")
        return Promise.resolve({ invalid: true })
      }
      const items = rows.filter(row => Number(row.deliverQuantity) > 0)
        .map(row => ({ detailId: row.detailId, deliverQuantity: Number(row.deliverQuantity) }))
      if (!items.length) {
        this.$modal.msgError("请至少录入一条本仓发货数量")
        return Promise.resolve({ invalid: true })
      }
      const target = { noticeId: this.deliverDetail.noticeId, warehouseId, items }
      const operation = this.operationScope().begin("deliverSubmit", this.deliverRevision)
      this.submitLoading = true
      let sent = false
      let succeeded = false
      return this.$modal.confirm(this.getDeliveryNoticeDeliverConfirmMessage(items)).then(() => {
        if (!this.operationScope().isCurrent(operation, this.deliverRevision)) return { discarded: true }
        sent = true
        this.attemptedDetailIds = items.map(row => row.detailId)
        return deliverDeliveryNotice(target.noticeId, { warehouseId: target.warehouseId, items: target.items }).then(res => {
          if (!this.operationScope().isCurrent(operation, this.deliverRevision)) return { discarded: true }
          succeeded = true
          this.$modal.msgSuccess(res.msg || "本仓发货成功")
          return { success: true }
        })
      }).catch(error => {
        if (this.operationScope().isCurrent(operation, this.deliverRevision) && sent) {
          // A transport failure may have committed. Query before permitting another submit.
          this.deliveryUncertain = !error || !error.response || error.response.status >= 500
          if (!this.deliveryUncertain) this.attemptedDetailIds = []
        }
        return { failed: true, error }
      }).finally(() => {
        if (this.operationScope().isCurrent(operation, this.deliverRevision)) {
          this.submitLoading = false
          if (succeeded) { this.deliveryUncertain = true; this.refreshDeliverState() }
        }
      })
    },
    async handleCancel(row) {
      if (!row || !row.noticeId) return
      const noticeId=String(row.noticeId), scope=this.operationScope(), operation=scope.begin('cancel:'+noticeId,noticeId)
      try {
        await this.$modal.confirm(this.getDeliveryNoticeCancelConfirmMessage({...row}))
        if(!scope.isCurrent(operation,String(row.noticeId))) return
        const res=await cancelDeliveryNotice(noticeId, {silentError:true})
        if(!scope.isCurrent(operation,String(row.noticeId))) return
        this.$modal.msgSuccess(res.msg || "已取消")
        this.getList()
      } catch(error) {
        if(scope.isCurrent(operation,String(row.noticeId)) && error!=='cancel' && error!=='close') this.$modal.msgError('取消结果未确认，请刷新通知状态后重试')
      }
    },
    async handleExport() {
      const scope=this.operationScope(), query={...this.queryParams}, identity=JSON.stringify(query), operation=scope.begin('export',identity)
      try {
        await this.$modal.confirm(this.getDeliveryNoticeExportConfirmMessage())
        if(!scope.isCurrent(operation,JSON.stringify(this.queryParams))) return
        return this.download("inventory/deliveryNotice/export", query, this.exportFileName("发货通知数据"))
      } catch(error) { /* Canceling the confirmation has no side effects. */ }
    },
    getDeliveryNoticeDeliverConfirmMessage(items) {
      const total = (items || []).reduce((sum, item) => sum + Number(item.deliverQuantity || 0), 0)
      return [
        "确认执行发货通知 [" + this.displayValue(this.deliverDetail.noticeNo) + "]？",
        "销售单号：" + this.displayValue(this.deliverDetail.salesOrderNo),
        "客户：" + this.displayValue(this.deliverDetail.customerName),
        "发货仓库：" + this.displayValue(this.deliverWarehouseName()),
        "本次发货数量：" + total + " 件",
        "本次发货将扣减所选仓库库存，请确认仓库、商品和数量无误。"
      ].join("\n")
    },
    getDeliveryNoticeCancelConfirmMessage(row) {
      return [
        "确认取消发货通知 [" + this.displayValue(row.noticeNo) + "]？",
        "销售单号：" + this.displayValue(row.salesOrderNo),
        "客户：" + this.displayValue(row.customerName),
        "取消后该通知不能继续发货，销售单状态会重新计算。"
      ].join("\n")
    },
    getDeliveryNoticeExportConfirmMessage() {
      return [
        "确认导出发货通知数据？",
        "本次导出包含当前查询结果，筛选状态：" + this.displayValue(this.queryParams.status || "全部"),
        "筛选销售单号：" + this.displayValue(this.queryParams.salesOrderNo || "全部"),
        "请确认导出文件只发送给有权限查看销售和发货数据的人员。"
      ].join("\n")
    },
    deliverWarehouseName() {
      const warehouse = this.deliverWarehouseOptions.find(item => String(item.deptId) === String(this.deliverForm.warehouseId))
      return warehouse ? warehouse.deptName : "未选择发货仓库"
    },
    displayValue(value) {
      return value === undefined || value === null || value === "" ? "-" : value
    },
    canDeliver(row) {
      return row.status === "pending" || row.status === "delivering"
    },
    remainingQty(row) {
      const noticeQty = Number(row.noticeQty || 0)
      const deliveredQty = Number(row.deliveredQty || 0)
      return Math.max(0, Number((noticeQty - deliveredQty).toFixed(2)))
    },
    salesShopLabel(row) {
      if (!row) {
        return "-"
      }
      const name = row.shopDeptName || row.shopName || row.organizationName || row.deptName || row.orgName
      if (name) {
        return name
      }
      return row.shopDeptId !== undefined && row.shopDeptId !== null && row.shopDeptId !== "" ? "未关联组织名称" : "-"
    },
    warehouseLabel(row) {
      if (!row) return "-"
      if (row.warehouseName) return row.warehouseName
      if (row.warehouseId !== undefined && row.warehouseId !== null && row.warehouseId !== "") return "未关联仓库名称"
      return "按明细仓库发货"
    },
    statusLabel(val) {
      const map = { pending: "待发货", delivering: "发货中", completed: "已发货", cancelled: "已取消" }
      return map[val] || "未知发货状态"
    },
    statusTag(val) {
      const map = { pending: "warning", delivering: "primary", completed: "success", cancelled: "info" }
      return map[val] || ""
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.text-danger { color: #F56C6C }
.text-success { color: #67C23A }
</style>
