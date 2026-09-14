<template>
  <div class="app-container transfer-page">
    <inventory-page-hero
      title="调拨作业"
      eyebrow="仓间协同"
      description="把门店要货、审批、仓库发货与门店收货串成一条清晰、可追踪的作业链路。"
      scope-text="当前组织待处理作业"
      icon="el-icon-sort"
      tone="teal"
      :features="['要货申请', '审批协同', '收发确认']"
    />
    <div class="transfer-dashboard">
      <el-card v-for="item in summaryCards" :key="item.key" shadow="never" class="transfer-metric">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value" :class="item.className">{{ item.value }}</div>
        <div class="metric-extra">{{ item.extra }}</div>
      </el-card>
    </div>

    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="单号">
          <el-input v-model="queryParams.orderNo" placeholder="调拨单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option v-for="item in activeStatusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.transferType" clearable placeholder="全部">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="店铺">
          <el-select
            v-model="queryParams.storeDeptId"
            filterable
            clearable
            placeholder="按店铺筛选"
            style="width:240px"
          >
            <el-option
              v-for="store in authorizedStoreOptions"
              :key="store.deptId"
              :label="store.label || store.deptName"
              :value="store.deptId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:transfer:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-if="isStoreContext" v-hasPermi="['inv:transfer:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">发起要货</el-button>
          <el-button v-if="isStoreContext" v-hasPermi="['inv:transfer:add']" size="mini" icon="el-icon-sort" @click="openForm(null, 'cross_store')">异店调货</el-button>
          <el-button v-if="isStoreContext && storeReturnEnabled" v-hasPermi="['inv:transfer:add']" size="mini" icon="el-icon-back" @click="openForm(null, 'store_return')">门店返仓</el-button>
          <el-button v-hasPermi="['inv:transfer:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="transferContextAlert"
      :title="transferContextAlert.title"
      :description="transferContextAlert.description"
      :type="transferContextAlert.type"
      show-icon
      :closable="false"
      class="context-alert mb12"
    />

    <el-card shadow="never">
      <div class="processing-head">
        <div>
          <div class="panel-title">调拨处理中</div>
          <div class="panel-sub">{{ transferPanelSub }}</div>
        </div>
        <el-tag size="mini" type="info">当前组织：{{ currentDeptLabel }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="list" :empty-text="transferEmptyText" size="small">
        <el-table-column label="调拨单号" prop="orderNo" width="170" fixed="left"/>
        <el-table-column label="目标组织" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">
            <div>{{ displayValue(scope.row.toDeptName) }}</div>
            <div v-if="targetOrgAncestorPath(scope.row)" class="transfer-org-path">{{ targetOrgAncestorPath(scope.row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源组织" min-width="140" show-overflow-tooltip>
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
        <el-table-column label="状态 / 审批进度" prop="status" width="230" align="center">
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.status)" size="mini">{{ statusLabel(scope.row.status, scope.row) }}</el-tag>
            <div v-if="scope.row.approvalSummary" class="approval-summary">
              <div>{{ scope.row.approvalSummary.summaryText }}</div>
              <el-tooltip
                v-if="scope.row.approvalSummary.currentCandidateDisplayNames && scope.row.approvalSummary.currentCandidateDisplayNames.length"
                :content="scope.row.approvalSummary.currentCandidateDisplayNames.join('、')"
                placement="top"
              >
                <div class="approval-summary-candidates">候选：{{ approvalCandidateText(scope.row.approvalSummary) }}</div>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前责任" width="130">
          <template slot-scope="scope">{{ currentOwner(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template slot-scope="scope">{{ formatLocalTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template slot-scope="scope">
            <div class="compact-row-actions">
              <el-button
                v-hasPermi="[transferPrimaryAction(scope.row).permission]"
                type="primary"
                plain
                size="mini"
                @click="runTransferAction(transferPrimaryAction(scope.row).key, scope.row)"
              >{{ transferPrimaryAction(scope.row).label }}</el-button>
              <el-dropdown
                v-if="transferMoreActions(scope.row).length"
                trigger="click"
                @command="handleTransferMoreCommand($event, scope.row)"
              >
                <el-button size="mini">更多<i class="el-icon-arrow-down el-icon--right"/></el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item
                    v-for="action in transferMoreActions(scope.row)"
                    :key="action.key"
                    v-hasPermi="[action.permission]"
                    :command="action.key"
                    :class="action.danger ? 'dropdown-danger' : ''"
                  >{{ action.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog :title="formDialogTitle" :visible.sync="open" width="1080px" custom-class="transfer-request-dialog" append-to-body :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="98px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item :label="isReturnForm ? '返仓门店' : (isCrossStoreForm ? '调入门店' : '要货门店')">
              <el-input :value="currentShopLabel" disabled class="transfer-readonly-input" />
              <div class="form-tip">{{ currentShopFormTip }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              :label="isReturnForm ? '目标仓库' : (isCrossStoreForm ? '调出门店' : '要货仓库')"
              :prop="isReturnForm ? 'toWarehouseId' : (isCrossStoreForm ? 'fromDeptId' : 'fromWarehouseId')"
            >
              <el-select
                v-if="isCrossStoreForm"
                v-model="form.fromDeptId"
                filterable
                :clearable="false"
                placeholder="请选择有权限的调出门店"
                style="width:100%"
                @change="onSourceStoreChange"
              >
                <el-option
                  v-for="store in authorizedSourceStores"
                  :key="store.deptId"
                  :label="store.deptName"
                  :value="store.deptId"
                />
              </el-select>
              <WarehouseSelect
                v-else-if="isReturnForm"
                v-model="form.toWarehouseId"
                :clearable="false"
                purpose="returnTarget"
                :scope-dept-id="currentShopDeptId"
                placeholder="请选择返仓目标仓库"
                width="100%"
                @selected="onWarehouseSelected"
              />
              <WarehouseSelect
                v-else
                v-model="form.fromWarehouseId"
                :clearable="false"
                purpose="replenishmentSource"
                :scope-dept-id="currentShopDeptId"
                placeholder="请选择要货仓库"
                width="100%"
                @selected="onWarehouseSelected"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="isReturnForm" :gutter="12">
          <el-col :span="8"><el-form-item label="返仓原因" prop="returnReasonCode"><el-select v-model="form.returnReasonCode" style="width:100%" placeholder="请选择"><el-option v-for="item in returnReasonOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="原因说明" :required="form.returnReasonCode==='OTHER'"><el-input v-model="form.returnReasonText" maxlength="300" placeholder="选择其他原因时必填"/></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="调拨类型">
              <el-select v-model="form.transferType" disabled placeholder="请选择类型" class="transfer-readonly-input" style="width:100%">
                <el-option v-for="item in formTypeOptions" :key="item.value" :label="item.label" :value="item.value"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="500" placeholder="填写要货原因或补充说明"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="收件人" prop="recipientName">
              <el-input v-model.trim="form.recipientName" maxlength="64" clearable placeholder="收件人姓名" autocomplete="name"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话" prop="recipientPhone">
              <el-input v-model.trim="form.recipientPhone" maxlength="32" clearable placeholder="手机或座机号码" autocomplete="tel"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货地址" prop="shippingAddress">
              <el-input v-model.trim="form.shippingAddress" maxlength="500" clearable placeholder="详细收货地址" autocomplete="street-address"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ isReturnForm ? '返仓明细' : (isCrossStoreForm ? '调货明细' : '要货明细') }}</el-divider>
        <el-table :data="form.details" size="small" border>
          <el-table-column label="物料" min-width="190" show-overflow-tooltip>
            <template slot-scope="scope">
              <el-input :value="displayValue(scope.row.itemName || scope.row.productName)" size="small" disabled class="transfer-readonly-input" placeholder="请从仓库库存选择" :title="displayValue(scope.row.itemName || scope.row.productName)"/>
            </template>
          </el-table-column>
          <el-table-column label="编码" width="160" class-name="transfer-code-column" show-overflow-tooltip>
            <template slot-scope="scope">
              <el-input :value="scope.row.itemCode || scope.row.productCode" size="small" disabled class="transfer-readonly-input" :title="displayValue(scope.row.itemCode || scope.row.productCode)"/>
            </template>
          </el-table-column>
          <el-table-column :label="isReturnForm ? '返仓数量' : (isCrossStoreForm ? '需求数量' : '要货数量')" width="140">
            <template slot-scope="scope">
              <el-input-number v-model="scope.row.quantity" :min="0.01" :precision="2" size="small" style="width:100%"/>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="90">
            <template slot-scope="scope">
              <el-input :value="scope.row.unit" size="small" disabled class="transfer-readonly-input" placeholder="档案单位"/>
            </template>
          </el-table-column>
          <el-table-column :label="isReturnForm ? '门店可用' : (isCrossStoreForm ? '调出店可用' : '仓库可用')" width="110" align="right">
            <template slot-scope="scope">
              <el-tag v-if="isDetailShortage(scope.row)" type="danger" size="mini">{{ sourceShortageText }}</el-tag>
              <span v-else-if="isDetailAvailabilityPending(scope.row)" class="availability-muted">库存待确认</span>
              <span v-else>{{ formatQuantity(scope.row.availableQuantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="参考成本价" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(scope.row.costPrice) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="110" align="right">
            <template slot-scope="scope">{{ formatMoney(lineAmount(scope.row)) }}</template>
          </el-table-column>
          <el-table-column label="规格" min-width="120">
            <template slot-scope="scope">
              <el-input :value="scope.row.spec" size="small" disabled class="transfer-readonly-input" placeholder="档案规格"/>
            </template>
          </el-table-column>
          <el-table-column v-if="isReturnForm" label="货况" width="120">
            <template slot-scope="scope"><el-select v-model="scope.row.goodsCondition" size="small"><el-option label="正常" value="NORMAL"/><el-option label="残损" value="DAMAGED"/><el-option label="待质检" value="PENDING_QC"/></el-select></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template slot-scope="scope">
              <el-button type="text" size="mini" class="text-danger" @click="removeDetail(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="transfer-amount-summary">参考总价：{{ formatMoney(formTotalAmount) }}</div>
        <div class="detail-actions mt10">
          <el-button type="success" size="mini" icon="el-icon-document-copy" plain @click="openSmartPaste">智能粘贴清单</el-button>
          <el-button type="primary" size="mini" icon="el-icon-plus" plain @click="openStockPicker">{{ isReturnForm ? '从门店库存选择' : (isCrossStoreForm ? '从调出店库存选择' : '从仓库库存选择') }}</el-button>
          <el-button v-if="!isReturnForm && !isCrossStoreForm" type="warning" size="mini" icon="el-icon-goods" plain @click="openProductPicker">从物料档案选择</el-button>
        </div>
      </el-form>
      <div slot="footer">
        <el-button @click="open = false">取消</el-button>
        <el-button v-hasPermi="['inv:transfer:add','inv:transfer:edit']" type="primary" @click="doSave(false)">保存草稿</el-button>
        <el-button v-hasPermi="['inv:transfer:submit']" type="success" @click="doSave(true)">保存并提交</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="isReturnForm ? '选择返仓库存' : (isCrossStoreForm ? '选择调出店库存' : '选择要货库存')" :visible.sync="stockPickerOpen" width="1120px" custom-class="stock-picker-dialog" append-to-body :close-on-click-modal="false">
      <div class="stock-picker-head">
        <div>
          <div class="panel-title">{{ displayValue(form.fromDeptName) }}</div>
          <div class="panel-sub">{{ isReturnForm ? '按物料类型查看当前返仓门店的可用库存' : (isCrossStoreForm ? '只显示当前用户有权限的调出门店可用库存' : '按物料类型查看当前要货仓库的可用库存') }}</div>
        </div>
        <el-form inline size="small" class="stock-picker-filter">
          <el-form-item label="物料类型" required>
            <el-radio-group v-model="stockQuery.itemType" size="small" @change="handleStockItemTypeChange">
              <el-radio-button v-for="type in allowedItemTypes" :key="type" :label="type">{{ itemTypeLabel(type) }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="物料">
            <inventory-item-select
              v-model="stockQuery.itemId"
              :item-type="stockQuery.itemType || 'product'"
              :disabled="!stockQuery.itemType"
              placeholder="搜索商品名称/编码"
              width="220px"
              @search-keyword-change="handleStockKeywordChange"
              @change="handleStockQueryChange"
            />
          </el-form-item>
          <el-form-item label="分类">
            <el-cascader
              v-model="stockQuery.categoryId"
              :options="stockCategoryOptions"
              :props="stockCategoryProps"
              :disabled="stockCategoryLoading || !stockQuery.itemType"
              clearable
              filterable
              :placeholder="stockCategoryPlaceholder"
              style="width:180px"
              @change="handleStockCategoryChange"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="mini" icon="el-icon-search" @click="loadStockPicker">查询</el-button>
            <el-button size="mini" icon="el-icon-refresh" @click="resetStockPicker">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table
        ref="stockPickerTable"
        v-loading="stockPickerLoading"
        :data="stockList"
        :empty-text="stockPickerEmptyText"
        size="small"
        border
        row-key="stockId"
        @selection-change="handleStockSelectionChange"
      >
        <el-table-column type="selection" width="44" :reserve-selection="true" :selectable="isStockSelectable"/>
        <el-table-column label="物料" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockProductName(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="编码" width="130" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockProductCode(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="物料分类" min-width="150" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockProductCategory(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="等级" width="90" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockProductGrade(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template slot-scope="scope">{{ stockProductSpec(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="可用库存" width="110" align="right">
          <template slot-scope="scope">{{ formatQuantity(stockAvailableQuantity(scope.row)) }}</template>
        </el-table-column>
        <el-table-column label="参考成本价" width="120" align="right">
          <template slot-scope="scope">{{ formatMoney(scope.row.costPrice) }}</template>
        </el-table-column>
      </el-table>
      <pagination v-show="stockTotal>0" :total="stockTotal" :page.sync="stockQuery.pageNum" :limit.sync="stockQuery.pageSize" @pagination="loadStockPicker"/>
      <div slot="footer">
        <el-button v-if="stockQuery.itemType === 'gift' && !stockPickerLoading && stockList.length === 0" type="warning" plain @click="openCatalogFromStockPicker">从礼盒档案选择</el-button>
        <el-button @click="stockPickerOpen = false">取消</el-button>
        <el-button type="primary" :disabled="selectedStockRows.length === 0" @click="confirmStockSelection">加入明细</el-button>
      </div>
    </el-dialog>

    <el-dialog title="选择要货物料" :visible.sync="productPickerOpen" width="620px" append-to-body :close-on-click-modal="false">
      <el-form label-width="86px" size="small">
        <el-form-item label="物料类型" required>
          <el-radio-group v-model="productPickerForm.itemType" size="small" @change="onCatalogItemTypeChange">
            <el-radio-button v-for="type in allowedItemTypes" :key="type" :label="type">{{ itemTypeLabel(type) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物料">
          <inventory-item-select
            v-model="productPickerForm.itemId"
            :item-type="productPickerForm.itemType || 'product'"
            :disabled="!productPickerForm.itemType"
            placeholder="搜索物料名称/编码"
            width="100%"
            @selected="onCatalogItemSelected"
          />
        </el-form-item>
      </el-form>
      <div class="product-picker-tip">
        选择物料后可提交要货需求；仓库当前无库存时会标记为仓库缺货，待采购入库或库存调整后再发货。
      </div>
      <div slot="footer">
        <el-button @click="productPickerOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!productPickerForm.itemType || !productPickerForm.itemId" @click="confirmProductSelection">加入明细</el-button>
      </div>
    </el-dialog>

    <transfer-smart-paste-dialog
      v-if="smartPasteOpen"
      :visible.sync="smartPasteOpen"
      :warehouse-id="form.fromWarehouseId"
      :inventory-dept-id="form.fromDeptId || form.fromWarehouseId"
      :transfer-type="form.transferType"
      :initial-recipient="{
        recipientName: form.recipientName,
        recipientPhone: form.recipientPhone,
        shippingAddress: form.shippingAddress
      }"
      :allowed-item-types="allowedItemTypes"
      @apply="applySmartPaste"
    />

    <el-dialog
      ref="transferDetailDialog"
      title="调拨单详情"
      :visible.sync="detailOpen"
      width="900px"
      custom-class="transfer-detail-dialog"
      append-to-body
      :close-on-click-modal="false"
      @open="resetDetailDialogScroll"
      @opened="resetDetailDialogScroll"
      @close="handleDetailDialogClose"
      @closed="handleDetailDialogClosed"
    >
      <div
        v-if="detail.transferId"
        v-loading="detailLoading"
        class="transfer-detail-body"
        element-loading-text="正在加载调拨详情"
      >
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="mini">{{ statusLabel(detail.status, detail) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detail.transferType) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ auditName(detail.createdByName) }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ submittedAuditName(detail) }}</el-descriptions-item>
          <el-descriptions-item label="来源组织">{{ displayValue(detail.fromDeptName) }}</el-descriptions-item>
          <el-descriptions-item label="目标组织">{{ displayValue(detail.toDeptName) }}</el-descriptions-item>
          <el-descriptions-item label="申请数量">{{ formatQuantity(detail.totalQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="参考总价">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatLocalTime(detail.submittedTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核通过">{{ formatLocalTime(detail.approvedTime) }}</el-descriptions-item>
          <el-descriptions-item label="收件人">{{ displayValue(detail.recipientName) }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ displayValue(detail.recipientPhone) }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="3">{{ displayValue(detail.shippingAddress) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.transferType === 'cross_store'" label="调出店确认">{{ sourceConfirmStatusLabel(detail.sourceConfirmStatus) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.sourceConfirmedBy" label="确认人 / 时间">{{ detail.sourceConfirmedBy }} / {{ formatLocalTime(detail.sourceConfirmedTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.sourceConfirmRemark" label="确认说明">{{ detail.sourceConfirmRemark }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reselectionFromTransferId" label="余量来源单">#{{ detail.reselectionFromTransferId }}</el-descriptions-item>
          <el-descriptions-item label="归档时间">{{ formatLocalTime(detail.archivedTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">审批进度</el-divider>
        <unified-approval-progress
          v-if="isNativeApproval(detail)"
          :detail="unifiedApprovalDetail"
          :loading="unifiedApprovalLoading"
          :error="unifiedApprovalError"
          @retry="loadTransferApproval(detail)"
        />
        <transfer-approval-progress
          v-else
          :track="approvalTrack"
          :loading="approvalTrackLoading"
          :error="approvalTrackError"
          @retry="loadApprovalTrack(detail.transferId)"
        />
        <el-divider content-position="left">调拨明细</el-divider>
        <el-table :data="detail.details || []" size="small" border show-summary :summary-method="getDetailSummary">
          <el-table-column label="物料" min-width="160"><template slot-scope="scope">{{ scope.row.itemName || scope.row.productName || "-" }}</template></el-table-column>
          <el-table-column label="编码" width="120"><template slot-scope="scope">{{ scope.row.itemCode || scope.row.productCode || "-" }}</template></el-table-column>
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
          <el-table-column label="剩余待发" width="100" align="right">
            <template slot-scope="scope">{{ formatQuantity(remainingToShip(scope.row)) }}</template>
          </el-table-column>
          <el-table-column label="单位" prop="unit" width="90"/>
          <el-table-column label="规格" prop="spec" min-width="120"/>
        </el-table>
        <div class="detail-total-bar">
          <span>明细合计</span>
          <strong>{{ formatMoney(detailTotalAmount(detail)) }}</strong>
        </div>
        <el-divider content-position="left">发货批次</el-divider>
        <el-table :data="detail.shipments || []" size="small" border row-key="shipmentId">
          <el-table-column type="expand">
            <template slot-scope="scope">
              <el-table :data="scope.row.details || []" size="mini" border>
                <el-table-column label="物料" min-width="160"><template slot-scope="detailScope">{{ detailScope.row.itemName || detailScope.row.productName || "-" }}</template></el-table-column>
                <el-table-column label="本批发货" prop="shippedQuantity" width="100" align="right">
                  <template slot-scope="detailScope">{{ formatQuantity(detailScope.row.shippedQuantity) }}</template>
                </el-table-column>
                <el-table-column label="本批收货" prop="receivedQuantity" width="100" align="right">
                  <template slot-scope="detailScope">{{ formatQuantity(detailScope.row.receivedQuantity) }}</template>
                </el-table-column>
              </el-table>
            </template>
          </el-table-column>
          <el-table-column label="批次号" prop="shipmentNo" min-width="150"/>
          <el-table-column label="状态" width="100" align="center">
            <template slot-scope="scope">
              <el-tag :type="shipmentStatusType(scope.row.status)" size="mini">{{ shipmentStatusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发货人" prop="shippedBy" width="100"/>
          <el-table-column label="发货时间" prop="shippedTime" width="160">
            <template slot-scope="scope">{{ formatLocalTime(scope.row.shippedTime) }}</template>
          </el-table-column>
          <el-table-column label="收货人" prop="receivedBy" width="100"/>
          <el-table-column label="收货时间" prop="receivedTime" width="160">
            <template slot-scope="scope">{{ formatLocalTime(scope.row.receivedTime) }}</template>
          </el-table-column>
        </el-table>
        <template v-if="detail.discrepancies && detail.discrepancies.length">
          <el-divider content-position="left">收货差异</el-divider>
          <el-table :data="detail.discrepancies" size="small" border>
            <el-table-column label="差异单号" prop="discrepancyNo" min-width="150"/>
            <el-table-column label="类型" min-width="140"><template slot-scope="scope">{{ discrepancyTypeLabel(scope.row.discrepancyType) }}</template></el-table-column>
            <el-table-column label="实发/验收" width="120"><template slot-scope="scope">{{ formatQuantity(scope.row.shippedQuantity) }}/{{ formatQuantity(scope.row.acceptedQuantity) }}</template></el-table-column>
            <el-table-column label="短少/拒收/残损" min-width="145"><template slot-scope="scope">{{ formatQuantity(scope.row.shortageQuantity) }}/{{ formatQuantity(scope.row.rejectedQuantity) }}/{{ formatQuantity(scope.row.damagedQuantity) }}</template></el-table-column>
            <el-table-column label="状态" width="105"><template slot-scope="scope"><el-tag :type="scope.row.status==='RESOLVED'?'success':'danger'" size="mini">{{ discrepancyStatusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="处理决定" min-width="120"><template slot-scope="scope">{{ resolutionDecisionLabel(scope.row.resolutionDecision) }}</template></el-table-column>
            <el-table-column label="操作" width="100"><template slot-scope="scope"><el-button v-if="scope.row.status!=='RESOLVED'" v-hasPermi="['inv:transfer:discrepancy:handle']" type="text" @click="openDiscrepancy(scope.row)">处理</el-button></template></el-table-column>
          </el-table>
        </template>
        <el-divider content-position="left">业务履约进度</el-divider>
        <div class="flow-steps">
          <div v-for="step in flowSteps(detail)" :key="step.key" class="flow-step" :class="step.className">
            <div class="flow-dot">{{ step.index }}</div>
            <div>
              <div class="flow-title">{{ step.title }}</div>
              <div class="flow-sub">{{ step.sub }}</div>
            </div>
          </div>
        </div>
      </div>
      <div slot="footer">
        <el-button
          v-if="detail.status === 'submitted' && (!isNativeApproval(detail) || canHandleUnifiedApproval(detail))"
          v-hasPermi="['inv:transfer:approve']"
          type="success"
          @click="openTransferApproval(detail, 'approve')"
        >通过</el-button>
        <el-button
          v-if="detail.status === 'submitted' && canHandleUnifiedApproval(detail)"
          v-hasPermi="['inv:transfer:approve']"
          type="warning"
          @click="openTransferApproval(detail, 'return')"
        >退回修改</el-button>
        <el-button
          v-if="detail.status === 'submitted' && (!isNativeApproval(detail) || canHandleUnifiedApproval(detail))"
          v-hasPermi="['inv:transfer:approve']"
          type="danger"
          @click="openTransferApproval(detail, 'reject')"
        >驳回</el-button>
        <el-button
          v-if="canConfirmSource(detail)"
          v-hasPermi="['inv:transfer:deliver']"
          type="primary"
          @click="openSourceConfirm(detail)"
        >调出店确认</el-button>
        <el-button
          v-if="canWithdrawNativeTransfer(detail)"
          v-hasPermi="['inv:transfer:submit']"
          type="warning"
          plain
          :loading="withdrawLoadingId === detail.transferId"
          @click="handleWithdraw(detail)"
        >撤回审批</el-button>
        <el-button @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="调拨审批"
      :visible.sync="transferApprovalOpen"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
    >
      <section class="transfer-approval-review" aria-label="待审批调拨单摘要">
        <div class="transfer-approval-review__heading">
          <span>请确认待审批单据</span>
          <strong>{{ displayValue(transferApprovalReview.orderNo || transferApprovalForm.orderNo) }}</strong>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="transferApprovalReview.transferType === 'cross_store' ? '调入门店' : '要货门店'">{{ displayValue(transferApprovalReview.toDeptName) }}</el-descriptions-item>
          <el-descriptions-item :label="transferApprovalReview.transferType === 'cross_store' ? '调出门店' : '要货仓库'">{{ displayValue(transferApprovalReview.fromDeptName) }}</el-descriptions-item>
          <el-descriptions-item label="申请数量">{{ formatQuantity(transferApprovalReview.totalQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="参考总价">{{ formatMoney(transferApprovalReview.totalAmount) }}</el-descriptions-item>
        </el-descriptions>
      </section>
      <el-form label-width="88px" size="small">
        <el-form-item label="调拨单号">
          <span>{{ displayValue(transferApprovalForm.orderNo) }}</span>
        </el-form-item>
        <el-form-item label="审批决定" required>
          <el-radio-group v-model="transferApprovalForm.action">
            <el-radio value="approve" label="approve">通过</el-radio>
            <el-radio v-if="transferApprovalForm.engineMode === 'NATIVE'" value="return" label="return">退回修改</el-radio>
            <el-radio value="reject" label="reject">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见" :required="transferApprovalForm.action !== 'approve'">
          <el-input
            v-model="transferApprovalForm.comment"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            :placeholder="transferApprovalPlaceholder"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="transferApprovalOpen = false">取消</el-button>
        <el-button
          v-hasPermi="['inv:transfer:approve']"
          :type="transferApprovalActionType"
          :loading="transferApprovalLoading"
          @click="submitTransferApproval"
        >确认{{ transferApprovalActionLabel }}</el-button>
      </div>
    </el-dialog>

    <el-dialog title="调出店确认异店调货" :visible.sync="sourceConfirmOpen" width="900px" append-to-body :close-on-click-modal="false">
      <el-alert
        title="请逐行填写本店实际可调数量。少于申请数量的余量会自动生成草稿，退回调入店重新选择其他调出店。"
        type="warning"
        show-icon
        :closable="false"
        class="mb12"
      />
      <el-descriptions :column="3" border size="small" class="mb12">
        <el-descriptions-item label="单号">{{ sourceConfirmDetail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="调出门店">{{ displayValue(sourceConfirmDetail.fromDeptName) }}</el-descriptions-item>
        <el-descriptions-item label="调入门店">{{ displayValue(sourceConfirmDetail.toDeptName) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="sourceConfirmForm.items" size="small" border>
        <el-table-column label="物料" prop="productName" min-width="180"/>
        <el-table-column label="申请数量" prop="requestedQuantity" width="110" align="right"/>
        <el-table-column label="本店确认数量" width="180">
          <template slot-scope="scope">
            <el-input-number
              v-model="scope.row.confirmedQuantity"
              :min="0"
              :max="scope.row.requestedQuantity"
              :precision="2"
              size="small"
              style="width:100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="退回调入店余量" width="140" align="right">
          <template slot-scope="scope">{{ formatQuantity(sourceConfirmRemainder(scope.row)) }}</template>
        </el-table-column>
      </el-table>
      <el-form label-width="90px" class="mt12">
        <el-form-item label="确认说明" :required="sourceConfirmHasRemainder">
          <el-input
            v-model="sourceConfirmForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="有部分或全部无法调出时必填"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="sourceConfirmOpen = false">取消</el-button>
        <el-button size="mini" @click="fillSourceConfirmAll">全部可调</el-button>
        <el-button v-hasPermi="['inv:transfer:deliver']" type="primary" :loading="actionLoading" @click="submitSourceConfirm">确认本店可调数量</el-button>
      </div>
    </el-dialog>

    <el-dialog title="发货" :visible.sync="shipmentOpen" width="860px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="3" border size="small" class="mb12">
        <el-descriptions-item label="单号">{{ shipmentDetail.orderNo }}</el-descriptions-item>
        <el-descriptions-item :label="shipmentDetail.transferType === 'cross_store' ? '调入门店' : (shipmentDetail.transferType === 'store_return' ? '目标仓库' : '要货门店')">{{ displayValue(shipmentDetail.toDeptName) }}</el-descriptions-item>
        <el-descriptions-item :label="shipmentDetail.transferType === 'cross_store' ? '调出门店' : (shipmentDetail.transferType === 'store_return' ? '返仓门店' : '要货仓库')">{{ displayValue(shipmentDetail.fromDeptName) }}</el-descriptions-item>
        <el-descriptions-item label="收件人">{{ displayValue(shipmentDetail.recipientName) }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ displayValue(shipmentDetail.recipientPhone) }}</el-descriptions-item>
        <el-descriptions-item label="收货地址">{{ displayValue(shipmentDetail.shippingAddress) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="shipmentForm.items" size="small" border>
        <el-table-column label="物料" prop="productName" min-width="170"/>
        <el-table-column label="申请数量" prop="quantity" width="100" align="right"/>
        <el-table-column label="已发货" prop="deliveredQuantity" width="100" align="right"/>
        <el-table-column label="剩余待发" prop="remainingQuantity" width="100" align="right"/>
        <el-table-column label="本次发货" width="160">
          <template slot-scope="scope">
            <el-input-number v-model="scope.row.deliverQuantity" :min="0" :max="scope.row.remainingQuantity" :precision="2" size="small" style="width:100%"/>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="shipmentOpen = false">取消</el-button>
        <el-button size="mini" @click="fillShipmentRemaining">全部剩余</el-button>
        <el-button v-hasPermi="['inv:transfer:deliver']" type="primary" :loading="actionLoading" @click="submitShipment">确认发货</el-button>
      </div>
    </el-dialog>

    <el-dialog title="验收与差异收货" :visible.sync="receiptOpen" width="1180px" append-to-body :close-on-click-modal="false">
      <el-form inline size="small" class="mb12">
        <el-form-item label="发货批次">
          <el-select v-model="receiptForm.shipmentId" placeholder="请选择待收货批次" style="width:260px" @change="onReceiptShipmentChange">
            <el-option
              v-for="shipment in receiptForm.shipments"
              :key="shipment.shipmentId"
              :label="shipmentOptionLabel(shipment)"
              :value="shipment.shipmentId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        :title="transferDiscrepancyEnabled ? '请将本批数量分为验收入库、拒收、残损和短少；只有验收入库数量会增加目标库存。' : '差异新建暂未开放，本批只能按发货数量全量正常收货。'"
        type="info"
        show-icon
        :closable="false"
        class="mb12"
      />
      <el-descriptions :column="3" border size="small" class="mb12">
        <el-descriptions-item label="单号">{{ receiptDetail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ displayValue(receiptForm.shipmentNo) }}</el-descriptions-item>
        <el-descriptions-item :label="receiptDetail.transferType === 'cross_store' ? '调入门店' : (receiptDetail.transferType === 'store_return' ? '目标仓库' : '要货门店')">{{ displayValue(receiptDetail.toDeptName) }}</el-descriptions-item>
        <el-descriptions-item :label="receiptDetail.transferType === 'cross_store' ? '调出门店' : (receiptDetail.transferType === 'store_return' ? '返仓门店' : '要货仓库')">{{ displayValue(receiptDetail.fromDeptName) }}</el-descriptions-item>
        <el-descriptions-item label="发货人">{{ displayValue(receiptForm.shippedBy) }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ displayValue(receiptForm.shippedTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="receiptForm.items" size="small" border>
        <el-table-column label="物料" prop="productName" min-width="170"/>
        <el-table-column label="本批发货" prop="shippedQuantity" width="100" align="right"/>
        <el-table-column label="本批已收" prop="receivedQuantity" width="100" align="right"/>
        <el-table-column label="本批待收" prop="remainingQuantity" width="100" align="right"/>
        <el-table-column label="验收入库" width="145">
          <template slot-scope="scope">
            <el-input-number v-model="scope.row.receiveQuantity" :min="0" :max="scope.row.remainingQuantity" :precision="2" :disabled="!transferDiscrepancyEnabled" size="small" style="width:100%"/>
          </template>
        </el-table-column>
        <el-table-column v-if="transferDiscrepancyEnabled" label="拒收" width="135"><template slot-scope="scope"><el-input-number v-model="scope.row.rejectedQuantity" :min="0" :max="scope.row.remainingQuantity" :precision="2" size="small" style="width:100%"/></template></el-table-column>
        <el-table-column v-if="transferDiscrepancyEnabled" label="残损" width="135"><template slot-scope="scope"><el-input-number v-model="scope.row.damagedQuantity" :min="0" :max="scope.row.remainingQuantity" :precision="2" size="small" style="width:100%"/></template></el-table-column>
        <el-table-column v-if="transferDiscrepancyEnabled" label="自动短少" width="95" align="right"><template slot-scope="scope"><span :class="{'text-danger':receiptShortage(scope.row)>0}">{{ formatQuantity(receiptShortage(scope.row)) }}</span></template></el-table-column>
        <el-table-column v-if="transferDiscrepancyEnabled" label="差异说明 / 凭证" min-width="220"><template slot-scope="scope"><el-input v-model="scope.row.discrepancyNote" size="small" placeholder="有差异时必填说明"/><transfer-evidence-picker v-if="receiptOpen" v-model="scope.row.attachmentRefs" :context-key="transferContextToken() + ':receipt:' + receiptDetail.transferId + ':' + receiptForm.shipmentId + ':' + scope.row.detailId" :disabled="actionLoading" @upload-state="$set(scope.row, 'evidenceState', $event)"/></template></el-table-column>
      </el-table>
      <div slot="footer">
        <el-button @click="receiptOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:transfer:receive']" type="primary" :loading="actionLoading" @click="submitReceipt">确认收货</el-button>
      </div>
    </el-dialog>

    <el-dialog title="调拨差异处理（逐项）" :visible.sync="discrepancyOpen" width="1080px" append-to-body :close-on-click-modal="false">
      <el-alert
        v-if="selectedDiscrepancy && selectedDiscrepancy.status === 'PENDING_QC'"
        title="当前差异包含待质检明细；只能从待质检状态继续退回来源或核销，处理完成前不会结案。"
        type="warning"
        show-icon
        :closable="false"
        class="mb12"
      />
      <el-table :data="discrepancyForm.items" size="small" border empty-text="没有可处置的差异类别">
        <el-table-column label="物料" min-width="150"><template slot-scope="scope">{{ scope.row.itemName || scope.row.itemCode || scope.row.detailId }}</template></el-table-column>
        <el-table-column label="类别" width="90"><template slot-scope="scope">{{ scope.row.categoryLabel }}</template></el-table-column>
        <el-table-column label="差异数量" width="100" align="right"><template slot-scope="scope">{{ formatQuantity(scope.row.quantity) }}</template></el-table-column>
        <el-table-column label="冻结成本" width="110" align="right"><template slot-scope="scope">{{ formatMoney(scope.row.costPrice) }}</template></el-table-column>
        <el-table-column label="决定" width="160">
          <template slot-scope="scope">
            <el-select v-model="scope.row.decision" placeholder="请选择" size="small" style="width:100%">
              <el-option v-for="option in scope.row.decisionOptions" :key="option.value" :label="option.label" :value="option.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="附件要求 / 引用" min-width="210">
          <template slot-scope="scope">
            <transfer-evidence-picker v-if="discrepancyOpen" v-model="scope.row.attachmentRefs" :context-key="transferContextToken() + ':discrepancy:' + selectedDiscrepancy.discrepancyId + ':' + discrepancyForm.requestId + ':' + scope.row.detailId + ':' + scope.row.category" :required="scope.row.attachmentRequired && ['RETURN_SOURCE', 'WRITE_OFF'].includes(scope.row.decision)" :disabled="actionLoading" @upload-state="$set(scope.row, 'evidenceState', $event)"/>
            <span v-if="scope.row.attachmentRequired" class="text-danger discrepancy-attachment-hint">残损直接退回/核销需附件和说明</span>
          </template>
        </el-table-column>
        <el-table-column label="逐项说明" min-width="190">
          <template slot-scope="scope"><el-input v-model="scope.row.note" size="small" maxlength="1000" placeholder="可补充本类别依据"/></template>
        </el-table-column>
      </el-table>
      <el-form :model="discrepancyForm" label-width="85px" class="mt12">
        <el-form-item label="责任方"><el-select v-model="discrepancyForm.responsibleParty" clearable style="width:240px"><el-option label="来源方" value="SOURCE"/><el-option label="目标方" value="TARGET"/><el-option label="物流" value="LOGISTICS"/><el-option label="待确认" value="UNCONFIRMED"/></el-select></el-form-item>
        <el-form-item label="处理说明"><el-input v-model="discrepancyForm.note" type="textarea" :rows="3" maxlength="1000" placeholder="记录本次逐项处置依据和后续动作"/></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="discrepancyOpen=false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitDiscrepancy">确认逐项处理</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { acknowledgeTransferCommand } from "@/utils/request"
import {
  listTransferProcessing,
  getTransferOpsSummary,
  getTransferDetail,
  getTransferDiscrepancy,
  getTransferApprovalTrack,
  saveTransfer,
  submitTransfer,
  approveTransfer,
  confirmTransferSource,
  deliverTransfer,
  receiveShipment,
  cancelTransfer,
  withdrawTransfer,
  resolveTransferDiscrepancy
} from "@/api/inventory/transfer"
import { getProduct } from "@/api/inventory/product"
import { listStock } from "@/api/inventory/stock"
import { categoryTree } from "@/api/inventory/category"
import { giftCategoryTree } from "@/api/inventory/gift"
import { listShopTree, listVisibleStoreDept } from "@/api/system/dept"
import { getApprovalInstance } from "@/api/approval/monitor"
import { approveApprovalTask, rejectApprovalTask, returnApprovalTask } from "@/api/approval/task"
import { confirmExportAction } from "@/utils/exportConfirm"
import { formatInventoryDeptLabel, getSelectedDeptContext, getSelectedDeptId, getSelectedDeptName, isSelectedStore, isSelectedWarehouse } from "@/utils/shopContext"
import {
  buildTransferOrgHierarchy,
  buildTransferVisibleStoreOptions,
  resolveTransferOrgAncestorPath
} from "./transferOrgHierarchy"
import InventoryItemSelect from "@/views/inventory/components/InventoryItemSelect"
import WarehouseSelect from "@/views/inventory/components/WarehouseSelect"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")
const { buildTransferApprovalPayload } = require("@/utils/transferApprovalPayload")
const { findTodoFocusShipment } = require("@/utils/todoBusinessFocus")
const { returnAfterTodoAction } = require("@/utils/todoActionReturn")
const {
  canCancelTransfer,
  canConfirmTransferSource,
  canEditTransfer,
  canHandleTransferDiscrepancy,
  canReceiveTransfer,
  canShipTransferForContext,
  mergeTransferDetailForAction,
  resolveTransferMoreActions,
  resolveTransferPrimaryAction
} = require("./transferActionRules")
import TransferApprovalProgress from "@/views/inventory/transfer/components/TransferApprovalProgress"
import UnifiedApprovalProgress from "@/views/inventory/components/UnifiedApprovalProgress"
import { unwrapData } from "@/views/approval/manage/components/approvalUi"

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "transfer",
    loadFocusedRow(transferId) { return getTransferDetail(transferId) },
    actions: {
      approveTransfer(row) {
        if (!row || row.status !== "submitted") return this.showTodoBusinessHandled()
        return this.openDetail(row.transferId)
      },
      deliverTransferAll(row) {
        if (!this.canShip(row)) return this.showTodoBusinessHandled()
        this.openShipment(row)
      },
      confirmTransferSource(row) {
        if (!this.canConfirmSource(row)) return this.showTodoBusinessHandled()
        this.openSourceConfirm(row)
      },
      receiveTransferShipment(row, focus) {
        if (!this.canReceive(row)) return this.showTodoBusinessHandled()
        this.openReceipt(row, focus)
      },
      handleTransferDiscrepancy(row) {
        if (!this.canHandleDiscrepancy(row)) return this.showTodoBusinessHandled()
        this.openDiscrepancyForTransfer(row)
      },
      editReplenishment(row) {
        if (!this.canEditTransfer(row)) return this.showTodoBusinessHandled()
        this.openForm(row)
      },
      editTransfer(row) {
        if (!this.canEditTransfer(row)) return this.showTodoBusinessHandled()
        this.openForm(row)
      }
    }
  })],
  name: "InvTransfer",
  components: {
    TransferEvidencePicker: () => import("@/components/TransferEvidencePicker.vue"),
    InventoryItemSelect,
    WarehouseSelect,
    TransferApprovalProgress,
    UnifiedApprovalProgress,
    TransferSmartPasteDialog: () => import("@/views/inventory/transfer/components/TransferSmartPasteDialog")
  },
  data() {
    return {
      loading: false,
      actionLoading: false,
      stockPickerLoading: false,
      list: [],
      total: 0,
      opsSummary: {},
      open: false,
      detailOpen: false,
      detailLoading: false,
      detailRequestSequence: 0,
      formEditRequestSequence: 0,
      detailAvailabilityRequestSequence: 0,
      detailAvailabilityRequestTokens: new WeakMap(),
      listRequestSequence: 0,
      opsSummaryRequestSequence: 0,
      stockPickerRequestSequence: 0,
      transferContextEpoch: 0,
      transferApprovalOpen: false,
      transferApprovalLoading: false,
      transferApprovalReview: {},
      sourceConfirmOpen: false,
      shipmentOpen: false,
      receiptOpen: false,
      discrepancyOpen: false,
      stockPickerOpen: false,
      productPickerOpen: false,
      smartPasteOpen: false,
      stockList: [],
      stockTotal: 0,
      stockProductMap: {},
      stockCategoryOptions: [],
      stockCategoryLoading: false,
      stockCategoryItemType: null,
      stockCategoryRequestId: 0,
      selectedStockRows: [],
      authorizedStoreOptions: [],
      orgHierarchy: { byId: {}, stores: [] },
      storeOptionsRequestSequence: 0,
      allowedItemTypes: ["product", "gift"],
      productPickerForm: { itemType: null, itemId: undefined, item: null },
      detail: {},
      transferApprovalForm: { transferId: undefined, taskId: undefined, instanceId: undefined, engineMode: "LEGACY", orderNo: "", action: "approve", comment: "" },
      approvalTrack: null,
      approvalTrackLoading: false,
      approvalTrackError: false,
      approvalTrackTransferId: null,
      approvalRequestSequence: 0,
      actionRequestSequence: 0,
      unifiedApprovalDetail: null,
      unifiedApprovalLoading: false,
      unifiedApprovalError: false,
      withdrawLoadingId: undefined,
      shipmentDetail: {},
      sourceConfirmDetail: {},
      receiptDetail: {},
      shipmentForm: { items: [] },
      sourceConfirmForm: { items: [], remark: "" },
      receiptForm: { shipmentId: undefined, shipmentNo: "", shippedBy: "", shippedTime: "", shipments: [], items: [] },
      selectedDiscrepancy: null,
      discrepancyForm: { version: undefined, requestId: "", items: [], responsibleParty: "UNCONFIRMED", note: "" },
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, status: undefined, transferType: undefined, storeDeptId: undefined },
      stockQuery: { pageNum: 1, pageSize: 10, itemType: null, itemId: undefined, productId: undefined, categoryId: undefined, shopDeptId: undefined, warehouseId: undefined, stockStatus: "available", transferSource: true },
      stockSearchKeyword: "",
      stockCategoryProps: { value: "categoryId", label: "categoryName", children: "children", checkStrictly: true, emitPath: false },
      form: this.emptyForm(),
      deliverableStatusValues: ["approved", "reserved", "partial_delivered"],
      statusOptions: [
        { label: "草稿", value: "draft" },
        { label: "审核中", value: "submitted" },
        { label: "待发货", value: "approved" },
        { label: "已锁库", value: "reserved" },
        { label: "部分发货", value: "partial_delivered" },
        { label: "待收货", value: "delivered" },
        { label: "部分收货", value: "partial_received" },
        { label: "差异处理", value: "discrepancy" }
      ],
      typeOptions: [
        { label: "门店要货", value: "warehouse" },
        { label: "门店返仓", value: "store_return" },
        { label: "异店调货", value: "cross_store" }
      ],
      returnReasonOptions: [
        { label: "库存过量", value: "OVERSTOCK" },
        { label: "临近效期", value: "NEAR_EXPIRY" },
        { label: "错发货", value: "WRONG_DELIVERY" },
        { label: "残损", value: "DAMAGED" },
        { label: "闭店", value: "STORE_CLOSURE" },
        { label: "其他", value: "OTHER" }
      ],
      rules: {
        fromWarehouseId: [{ required: true, message: "请选择要货仓库", trigger: "change" }],
        fromDeptId: [{ required: true, message: "请选择调出门店", trigger: "change" }],
        toWarehouseId: [{ required: true, message: "请选择返仓目标仓库", trigger: "change" }],
        returnReasonCode: [{ required: true, message: "请选择返仓原因", trigger: "change" }],
        recipientName: [{ max: 64, message: "收件人姓名不能超过64个字符", trigger: "blur" }],
        recipientPhone: [{
          validator: (rule, value, callback) => {
            const phone = String(value || "").trim()
            if (!phone || /^[+\d][\d\s()\-]{5,31}$/.test(phone)) callback()
            else callback(new Error("请输入正确的联系电话"))
          },
          trigger: "blur"
        }],
        shippingAddress: [{ max: 500, message: "收货地址不能超过500个字符", trigger: "blur" }]
      }
    }
  },
  computed: {
    currentShopDeptId() {
      return getSelectedDeptId()
    },
    currentShopName() {
      return getSelectedDeptName() || "未选择门店"
    },
    currentUserId() {
      return this.$store && this.$store.getters ? this.$store.getters.id : undefined
    },
    currentUsername() {
      return this.$store && this.$store.getters ? this.$store.getters.name : ""
    },
    currentShopLabel() {
      return formatInventoryDeptLabel(this.currentDeptContext, { fallback: "未选择门店" })
    },
    currentShopIdTip() {
      return this.currentShopDeptId ? "请确认当前门店后提交要货" : "请先选择具体门店"
    },
    currentShopFormTip() {
      if (this.isReturnForm) return "返仓来源固定为当前门店"
      if (this.isCrossStoreForm) return "异店调货由当前调入店发起"
      return this.currentShopIdTip
    },
    currentDeptContext() {
      return getSelectedDeptContext()
    },
    currentDeptLabel() {
      return formatInventoryDeptLabel(this.currentDeptContext, { fallback: "未选择组织" })
    },
    isStoreContext() {
      return isSelectedStore()
    },
    isWarehouseContext() {
      return isSelectedWarehouse()
    },
    businessFeatures() {
      return this.$store && this.$store.getters
        ? this.$store.getters.businessFeatures || {}
        : {}
    },
    storeReturnEnabled() {
      return this.businessFeatures.storeReturn === true
    },
    transferDiscrepancyEnabled() {
      return this.businessFeatures.transferDiscrepancy === true
    },
    transferContextAlert() {
      if (this.isStoreContext) {
        return {
          type: "info",
          title: "当前门店：" + this.currentShopName,
          description: "门店可以发起要货、返仓并处理自己作为来源或目标的发货收货。"
        }
      }
      if (this.isWarehouseContext) {
        return {
          type: "info",
          title: "当前仓库：" + this.currentShopName,
          description: "仓库可以处理已提交的门店要货、返仓收货和收货差异；草稿不会出现在仓库作业中。"
        }
      }
      return {
        type: "warning",
        title: "当前组织不能直接处理调拨",
        description: "调拨业务需要选择门店或仓库。公司/组织可登录，但请切换到具体门店发起要货或具体仓库发货。"
      }
    },
    transferEmptyText() {
      if (!this.isStoreContext && !this.isWarehouseContext) {
        return "请选择门店或仓库后查看调拨单。"
      }
      if (this.isStoreContext) {
        return "当前门店暂无处理中调拨单。可发起要货或门店返仓。"
      }
      return "当前仓库暂无待处理调拨单。提交后的单据会出现在这里，草稿不会出现。"
    },
    transferPanelSub() {
      if (this.isWarehouseContext) {
        return "当前仓库显示已提交要货、返仓收货及差异处理单；草稿不会出现"
      }
      return "草稿、审核中、待发货、部分发货、待收货和部分收货主单"
    },
    activeStatusOptions() {
      if (this.isWarehouseContext) {
        return this.statusOptions.filter(item => item.value !== "draft")
      }
      return this.statusOptions
    },
    stockPickerEmptyText() {
      if (!this.form.fromWarehouseId) {
        if (this.isReturnForm) return "当前返仓来源门店未就绪"
        return this.isCrossStoreForm ? "请先选择调出门店" : "请先选择要货仓库"
      }
      if (!this.stockQuery.itemType) {
        return "请先选择物料类型"
      }
      if (this.stockQuery.itemType === "gift") {
        return this.isCrossStoreForm
          ? "当前调出门店暂无可用礼盒库存，请选择其他调出门店。"
          : "当前仓库暂无可用礼盒库存。可从礼盒档案发起缺货要货；如无可选礼盒，请先维护礼盒分类和礼盒档案。"
      }
      return this.isCrossStoreForm
        ? "当前调出门店暂无可用库存，请选择其他调出门店。"
        : "当前仓库暂无可用库存。请先采购入库、库存调整，或选择其他有库存的仓库。"
    },
    stockCategoryPlaceholder() {
      if (!this.stockQuery.itemType) return "请先选择物料类型"
      const label = this.itemTypeLabel(this.stockQuery.itemType)
      if (!this.stockCategoryLoading && this.stockCategoryItemType === this.stockQuery.itemType && this.stockCategoryOptions.length === 0) {
        return "暂无" + label + "分类"
      }
      return "选择" + label + "分类"
    },
    formTypeOptions() {
      return this.typeOptions.filter(item => item.value === "warehouse" ||
        item.value === "cross_store" ||
        (item.value === "store_return" && (this.storeReturnEnabled || this.form.transferType === "store_return")))
    },
    isReturnForm() {
      return this.form.transferType === "store_return"
    },
    isCrossStoreForm() {
      return this.form.transferType === "cross_store"
    },
    authorizedSourceStores() {
      const current = String(this.currentShopDeptId || "")
      return this.authorizedStoreOptions.filter(store => String(store.deptId) !== current)
    },
    sourceShortageText() {
      return this.isCrossStoreForm ? "调出店缺货" : "仓库缺货"
    },
    formDialogTitle() {
      if (this.form.transferId) {
        if (this.isReturnForm) return "编辑返仓单"
        return this.isCrossStoreForm ? "编辑异店调货单" : "编辑要货单"
      }
      if (this.isReturnForm) return "发起门店返仓"
      return this.isCrossStoreForm ? "发起异店调货" : "发起门店要货"
    },
    sourceConfirmHasRemainder() {
      return this.sourceConfirmForm.items.some(item => this.sourceConfirmRemainder(item) > 0)
    },
    summaryCards() {
      return [
        { key: "returns", label: "返仓处理中", value: Number(this.opsSummary.storeReturnProcessingCount || 0), extra: "当前组织可见返仓", className: "" },
        { key: "receive", label: "返仓待收货", value: Number(this.opsSummary.returnPendingReceiveCount || 0), extra: this.opsSummary.returnPendingReceiveCount ? "最老等待 " + this.formatDuration(this.opsSummary.oldestReturnWaitingSeconds) : "暂无待收货", className: "warning-value" },
        { key: "risk", label: "未结差异", value: Number(this.opsSummary.openDiscrepancyCount || 0), extra: "待处理 / 待质检", className: "danger-value" },
        { key: "qc", label: "待质检", value: Number(this.opsSummary.pendingQcCount || 0), extra: "24小时已收返仓 " + Number(this.opsSummary.returnReceivedLast24h || 0), className: "success-value" }
      ]
    },
    formTotalAmount() {
      return this.sumDetailAmounts(this.form.details)
    },
    transferApprovalActionLabel() {
      if (this.transferApprovalForm.action === "return") return "退回"
      if (this.transferApprovalForm.action === "reject") {
        return this.transferApprovalForm.engineMode === "NATIVE" ? "拒绝" : "驳回"
      }
      return "通过"
    },
    transferApprovalActionType() {
      return this.transferApprovalForm.action === "return"
        ? "warning"
        : this.transferApprovalForm.action === "reject" ? "danger" : "primary"
    },
    transferApprovalPlaceholder() {
      if (this.transferApprovalForm.action === "return") return "请输入退回修改原因"
      if (this.transferApprovalForm.action === "reject") return this.transferApprovalForm.engineMode === "NATIVE"
        ? "请输入拒绝原因"
        : "请输入调拨驳回原因"
      return "可填写调拨审批意见"
    }
  },
  created() {
    if (typeof window !== "undefined") {
      window.addEventListener("erp:dept-changed", this.handleTransferDeptChanged)
    }
    this.loadAuthorizedStoreOptions()
    this.getList()
  },
  beforeDestroy() {
    if (typeof window !== "undefined") {
      window.removeEventListener("erp:dept-changed", this.handleTransferDeptChanged)
    }
    this.storeOptionsRequestSequence += 1
    this.transferContextEpoch += 1
    this.listRequestSequence += 1
    this.opsSummaryRequestSequence += 1
    this.stockPickerRequestSequence += 1
    this.detailRequestSequence += 1
    this.formEditRequestSequence += 1
    this.detailAvailabilityRequestSequence += 1
    this.detailAvailabilityRequestTokens = new WeakMap()
    this.approvalRequestSequence += 1
    this.actionRequestSequence += 1
  },
  methods: {
    emptyForm() {
      return {
        transferId: undefined,
        fromDeptId: undefined,
        fromDeptName: "",
        fromWarehouseId: undefined,
        toDeptId: undefined,
        toDeptName: "",
        toWarehouseId: undefined,
        transferType: "warehouse",
        returnReasonCode: undefined,
        returnReasonText: "",
        attachmentNodeIds: "",
        recipientName: "",
        recipientPhone: "",
        shippingAddress: "",
        remark: "",
        totalQuantity: 0,
        totalAmount: 0,
        details: [this.emptyDetail()]
      }
    },
    loadAuthorizedStoreOptions() {
      const requestSequence = ++this.storeOptionsRequestSequence
      const contextKey = this.transferContextKey()
      const isWarehouse = this.isWarehouseContext
      const isStore = this.isStoreContext
      const isCurrentRequest = () => requestSequence === this.storeOptionsRequestSequence &&
        contextKey === this.transferContextKey()
      this.orgHierarchy = { byId: {}, stores: [] }
      this.authorizedStoreOptions = []
      if (!this.currentShopDeptId || (!isWarehouse && !isStore)) {
        return Promise.resolve([])
      }
      const request = isWarehouse
        ? listVisibleStoreDept({ scopeDeptId: this.currentShopDeptId })
        : listShopTree()
      return request.then(res => {
        if (!isCurrentRequest()) return []
        const payload = res && res.data !== undefined ? res.data : res
        const hierarchy = buildTransferOrgHierarchy(payload)
        const stores = isWarehouse
          ? buildTransferVisibleStoreOptions(payload, hierarchy)
          : hierarchy.stores
        this.orgHierarchy = hierarchy
        this.authorizedStoreOptions = stores
        return stores
      }).catch(() => {
        if (!isCurrentRequest()) return []
        this.orgHierarchy = { byId: {}, stores: [] }
        this.authorizedStoreOptions = []
        return []
      })
    },
    transferContextKey() {
      return String(this.currentShopDeptId || "") + "|" +
        (this.isWarehouseContext ? "WAREHOUSE" : this.isStoreContext ? "STORE" : "")
    },
    transferContextToken() {
      return String(this.transferContextEpoch) + "|" + this.transferContextKey()
    },
    isTransferContextCurrent(contextToken) {
      return contextToken === this.transferContextToken()
    },
    captureTransferAction() {
      return {
        contextToken: this.transferContextToken(),
        actionSequence: ++this.actionRequestSequence
      }
    },
    isTransferActionCurrent(actionToken) {
      return !!actionToken &&
        actionToken.actionSequence === this.actionRequestSequence &&
        this.isTransferContextCurrent(actionToken.contextToken)
    },
    snapshotTransferValue(value) {
      if (value === undefined || value === null) return value
      return JSON.parse(JSON.stringify(value))
    },
    resetTransferContextState() {
      this.loading = false
      this.actionLoading = false
      this.list = []
      this.total = 0
      this.opsSummary = {}
      this.queryParams.storeDeptId = undefined

      this.open = false
      this.form = this.emptyForm()
      this.detailOpen = false
      this.detailLoading = false
      this.detail = {}
      this.transferApprovalOpen = false
      this.transferApprovalLoading = false
      this.transferApprovalReview = {}
      this.transferApprovalForm = {
        transferId: undefined,
        taskId: undefined,
        instanceId: undefined,
        engineMode: "LEGACY",
        orderNo: "",
        action: "approve",
        comment: ""
      }
      this.sourceConfirmOpen = false
      this.sourceConfirmDetail = {}
      this.sourceConfirmForm = { items: [], remark: "" }
      this.shipmentOpen = false
      this.shipmentDetail = {}
      this.shipmentForm = { items: [] }
      this.receiptOpen = false
      this.receiptDetail = {}
      this.receiptForm = {
        shipmentId: undefined,
        shipmentNo: "",
        shippedBy: "",
        shippedTime: "",
        shipments: [],
        items: []
      }
      this.discrepancyOpen = false
      this.selectedDiscrepancy = null
      this.discrepancyForm = {
        version: undefined,
        requestId: "",
        items: [],
        responsibleParty: "UNCONFIRMED",
        note: ""
      }
      this.stockPickerOpen = false
      this.stockPickerLoading = false
      this.productPickerOpen = false
      this.smartPasteOpen = false
      this.stockList = []
      this.stockTotal = 0
      this.stockProductMap = {}
      this.stockCategoryOptions = []
      this.stockCategoryLoading = false
      this.stockCategoryItemType = null
      this.selectedStockRows = []
      this.withdrawLoadingId = undefined
      this.clearDetailApprovalState()
    },
    handleTransferDeptChanged() {
      this.transferContextEpoch += 1
      this.listRequestSequence += 1
      this.opsSummaryRequestSequence += 1
      this.stockPickerRequestSequence += 1
      this.detailRequestSequence += 1
      this.formEditRequestSequence += 1
      this.detailAvailabilityRequestSequence += 1
      this.detailAvailabilityRequestTokens = new WeakMap()
      this.approvalRequestSequence += 1
      this.actionRequestSequence += 1
      this.stockCategoryRequestId += 1
      this.resetTransferContextState()
      this.loadAuthorizedStoreOptions()
      this.getList()
    },
    flattenDeptList(depts) {
      const result = []
      const visit = list => {
        ;(list || []).forEach(item => {
          result.push(item)
          if (item.children && item.children.length) visit(item.children)
        })
      }
      visit(depts)
      return result
    },
    emptyDetail() {
      return {
        itemType: null,
        itemId: undefined,
        itemName: "",
        itemCode: "",
        productId: undefined,
        productName: "",
        productCode: "",
        quantity: 1,
        deliveredQuantity: 0,
        receivedQuantity: 0,
        availableQuantity: undefined,
        costPrice: undefined,
        amount: undefined,
        unit: "",
        spec: "",
        grade: "",
        goodsCondition: "NORMAL",
        conditionNote: ""
      }
    },
    getList() {
      const requestSequence = ++this.listRequestSequence
      const contextToken = this.transferContextToken()
      const isCurrentRequest = () => requestSequence === this.listRequestSequence &&
        this.isTransferContextCurrent(contextToken)
      this.loading = true
      return this.loadTodoBusinessList(() => listTransferProcessing(this.buildTransferQuery())).then(res => {
        if (!isCurrentRequest()) return null
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).then(result => {
        if (!isCurrentRequest()) return result
        return this.loadOpsSummary(contextToken).then(() => result)
      }).finally(() => {
        if (isCurrentRequest()) this.loading = false
      })
    },
    loadOpsSummary(contextToken = this.transferContextToken()) {
      const requestSequence = ++this.opsSummaryRequestSequence
      const isCurrentRequest = () => requestSequence === this.opsSummaryRequestSequence &&
        this.isTransferContextCurrent(contextToken)
      if (!isCurrentRequest()) return Promise.resolve()
      if (!this.currentShopDeptId) {
        this.opsSummary = {}
        return Promise.resolve()
      }
      return getTransferOpsSummary().then(res => {
        if (isCurrentRequest()) this.opsSummary = res.data || {}
      }).catch(() => {
        if (isCurrentRequest()) this.opsSummary = {}
      })
    },
    formatDuration(seconds) {
      const total = Math.max(0, Number(seconds || 0))
      if (total < 3600) return Math.max(1, Math.floor(total / 60)) + "分钟"
      if (total < 86400) return Math.floor(total / 3600) + "小时"
      return Math.floor(total / 86400) + "天"
    },
    buildTransferQuery() {
      const query = Object.assign({}, this.queryParams)
      if (this.todoBusinessFocus) return query
      return query
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: this.queryParams.pageSize || 10, orderNo: undefined, status: undefined, transferType: undefined, storeDeptId: undefined }
      this.getList()
    },
    targetOrgAncestorPath(row) {
      return resolveTransferOrgAncestorPath(row, this.orgHierarchy)
    },
    auditName(value) {
      return value && String(value).trim() ? String(value).trim() : "历史姓名未记录"
    },
    submittedAuditName(row) {
      const submittedName = row && row.submittedByName
      if (submittedName && String(submittedName).trim()) {
        return String(submittedName).trim()
      }
      return row && row.submittedTime ? "历史姓名未记录" : "未提交"
    },
    statusType(status) {
      const map = {
        draft: "info",
        submitted: "warning",
        approved: "success",
        reserved: "",
        partial_delivered: "",
        delivered: "warning",
        partial_received: "warning",
        discrepancy: "danger"
      }
      return map[status] || "info"
    },
    statusLabel(status, row) {
      const sourceStatus = String(row && row.sourceConfirmStatus || "").toUpperCase()
      if (row && row.transferType === "cross_store") {
        if (status === "draft" && sourceStatus === "RESELECT_REQUIRED") return "待重选调出店"
        if (status === "approved" && (!sourceStatus || ["NOT_STARTED", "PENDING"].includes(sourceStatus))) {
          return "待调出店确认"
        }
      }
      const item = this.statusOptions.find(option => option.value === status)
      return item ? item.label : "未知调拨状态"
    },
    sourceConfirmStatusLabel(status) {
      return {
        NOT_REQUIRED: "无需确认",
        NOT_STARTED: "尚未开始",
        PENDING: "待调出店确认",
        CONFIRMED: "已全量确认",
        PARTIAL: "已部分确认",
        REJECTED: "调出店无法调出",
        RESELECT_REQUIRED: "待调入店重选调出店"
      }[String(status || "").toUpperCase()] || "待调出店确认"
    },
    itemTypeLabel(type) {
      return { product: "商品", gift: "礼盒", oe: "OE器皿" }[type] || "其他物料"
    },
    shipmentStatusLabel(status) {
      const map = {
        pending_receive: "待收货",
        received: "已收货",
        discrepancy: "存在差异",
        abnormal: "异常"
      }
      return map[status] || "未知发货状态"
    },
    shipmentStatusType(status) {
      const map = {
        pending_receive: "warning",
        received: "success",
        discrepancy: "danger",
        abnormal: "danger"
      }
      return map[status] || "info"
    },
    typeLabel(type) {
      const item = this.typeOptions.find(option => option.value === type)
      return item ? item.label : "其他调拨类型"
    },
    currentOwner(row) {
      const status = row && row.status
      const isReturn = row && row.transferType === "store_return"
      if (row && row.transferType === "cross_store") {
        const sourceStatus = String(row.sourceConfirmStatus || "").toUpperCase()
        if (status === "draft" && sourceStatus === "RESELECT_REQUIRED") return "调入门店"
        if (status === "approved" && (!sourceStatus || ["NOT_STARTED", "PENDING"].includes(sourceStatus))) return "调出门店确认"
      }
      const map = {
        draft: "制单人",
        submitted: "审批人",
        approved: isReturn ? "来源门店" : "来源仓库",
        reserved: isReturn ? "来源门店" : "来源仓库",
        partial_delivered: isReturn ? "来源门店" : "来源仓库",
        delivered: isReturn ? "目标仓库" : "目标门店",
        partial_received: isReturn ? "目标仓库" : "目标门店",
        discrepancy: "运营处理人"
      }
      return map[status] || "-"
    },
    approvalCandidateText(summary) {
      const names = (summary && summary.currentCandidateDisplayNames) || []
      if (names.length <= 2) return names.join("、")
      return names.slice(0, 2).join("、") + ` 等${summary.currentCandidateCount || names.length}人`
    },
    transferOrgContext() {
      return {
        currentDeptId: this.currentShopDeptId,
        storeContext: this.isStoreContext,
        warehouseContext: this.isWarehouseContext
      }
    },
    canShip(row) {
      return canShipTransferForContext(row, this.transferOrgContext())
    },
    canConfirmSource(row) {
      return canConfirmTransferSource(row, this.transferOrgContext())
    },
    canReceive(row) {
      return canReceiveTransfer(row, this.transferOrgContext())
    },
    canEditTransfer(row) {
      return canEditTransfer(row, this.transferOrgContext())
    },
    canSubmitTransfer(row) {
      return canEditTransfer(row, this.transferOrgContext())
    },
    canCancelTransfer(row) {
      return canCancelTransfer(row, this.transferOrgContext())
    },
    canHandleDiscrepancy(row) {
      return canHandleTransferDiscrepancy(row, this.transferOrgContext())
    },
    scalarRouteValue(value) {
      const candidate = Array.isArray(value) ? value[0] : value
      return candidate === undefined || candidate === null ? "" : String(candidate).trim()
    },
    approvalRouteContext() {
      const query = this.$route && this.$route.query || {}
      return {
        taskId: this.scalarRouteValue(query.approvalTaskId),
        instanceId: this.scalarRouteValue(query.approvalInstanceId)
      }
    },
    unifiedTaskId(row) {
      const source = row || {}
      const context = this.approvalRouteContext()
      const taskId = this.scalarRouteValue(source.approvalTaskId || context.taskId)
      const routeInstanceId = this.scalarRouteValue(source.approvalInstanceId || context.instanceId)
      const businessInstanceId = this.scalarRouteValue(source.approvalInstanceId)
      if (context.instanceId && businessInstanceId && String(context.instanceId) !== String(businessInstanceId)) return ""
      return taskId && routeInstanceId ? taskId : ""
    },
    isNativeApproval(row) {
      const source = row || {}
      return String(source.approvalEngine || "").toUpperCase() === "NATIVE" ||
        !!this.scalarRouteValue(source.approvalTaskId || this.approvalRouteContext().taskId)
    },
    canUseUnifiedTask(row) {
      return this.isNativeApproval(row) && !!this.unifiedTaskId(row)
    },
    canHandleUnifiedApproval(row) {
      const taskId = this.unifiedTaskId(row)
      if (!taskId || !this.unifiedApprovalDetail) return false
      const tasks = Array.isArray(this.unifiedApprovalDetail.tasks) ? this.unifiedApprovalDetail.tasks : []
      const task = tasks.find(item => String(item.taskId || item.id) === String(taskId))
      const instance = this.unifiedApprovalDetail.instance || {}
      return !!task && String(task.taskStatus || task.status || "").toUpperCase() === "PENDING" &&
        String(instance.status || "").toUpperCase() === "RUNNING"
    },
    canWithdrawNativeTransfer(row) {
      if (!row || row.status !== "submitted" || !this.isNativeApproval(row) || !this.canCancelTransfer(row)) {
        return false
      }
      const instance = this.unifiedApprovalDetail && this.unifiedApprovalDetail.instance || {}
      const isCurrentDetail = this.detail && String(this.detail.transferId || "") === String(row.transferId || "")
      if (isCurrentDetail && instance.applicantUserId !== undefined && instance.applicantUserId !== null) {
        return String(instance.applicantUserId) === String(this.currentUserId)
      }
      if (row.submittedUserId !== undefined && row.submittedUserId !== null) {
        return String(row.submittedUserId) === String(this.currentUserId)
      }
      return !!row.createBy && !!this.currentUsername && String(row.createBy) === String(this.currentUsername)
    },
    transferActionContext(row) {
      return Object.assign({}, this.transferOrgContext(), {
        nativeApproval: this.isNativeApproval(row),
        unifiedTaskAvailable: this.canUseUnifiedTask(row),
        withdrawAllowed: this.canWithdrawNativeTransfer(row)
      })
    },
    transferPrimaryAction(row) {
      const action = resolveTransferPrimaryAction(row, this.transferActionContext(row))
      if (action.key === "detail" && row && row.status === "submitted" && this.isNativeApproval(row)) {
        return Object.assign({}, action, { label: "查看审批" })
      }
      return action
    },
    transferMoreActions(row) {
      return resolveTransferMoreActions(row, this.transferActionContext(row))
    },
    handleTransferMoreCommand(command, row) {
      // Element UI emits `command` before its body-level popper has finished
      // hiding. Let that DOM update complete before opening another overlay.
      this.$nextTick(() => this.runTransferAction(command, row))
    },
    runTransferAction(action, row) {
      const handlers = {
        detail: () => this.openDetail(row.transferId, row),
        edit: () => this.openForm(row),
        submit: () => this.handleSubmit(row),
        approve: () => this.openTransferApproval(row, "approve"),
        return: () => this.openTransferApproval(row, "return"),
        reject: () => this.openTransferApproval(row, "reject"),
        sourceConfirm: () => this.openSourceConfirm(row),
        ship: () => this.openShipment(row),
        receive: () => this.openReceipt(row),
        discrepancy: () => this.openDiscrepancyForTransfer(row),
        withdraw: () => this.handleWithdraw(row),
        cancel: () => this.handleCancel(row)
      }
      if (handlers[action]) handlers[action]()
    },
    formatLocalTime(value) {
      return value ? this.parseTime(value, "{y}-{m}-{d} {h}:{i}:{s}") : "-"
    },
    ensureStoreContext() {
      if (this.isStoreContext) {
        return true
      }
      this.$modal.msgError("门店才能发起要货、异店调货或返仓申请")
      return false
    },
    ensureCanShip(row) {
      if (this.canShip(row)) {
        return true
      }
      this.$modal.msgError(row && ["cross_store", "store_return"].includes(row.transferType) ? "只能由来源门店发货" : "只能由来源仓库发货")
      return false
    },
    ensureCanReceive(row) {
      if (this.canReceive(row)) {
        return true
      }
      this.$modal.msgError(row && row.transferType === "store_return" ? "只能由目标仓库验收返仓" : "只能由目标门店收货")
      return false
    },
    onWarehouseSelected(warehouse) {
      if (!warehouse) {
        if (this.isReturnForm) {
          this.form.toDeptId = undefined
          this.form.toDeptName = ""
          this.form.toWarehouseId = undefined
        } else {
          this.invalidateDetailAvailabilityRequests()
          this.form.fromDeptId = undefined
          this.form.fromDeptName = ""
          this.form.fromWarehouseId = undefined
          ;(this.form.details || []).forEach(detail => {
            this.$set(detail, "availableQuantity", undefined)
            this.$set(detail, "availabilityStatus", "unknown")
          })
        }
        this.stockList = []
        return
      }
      if (this.isReturnForm) {
        this.form.toDeptId = warehouse.deptId
        this.form.toDeptName = warehouse.deptName
        this.form.toWarehouseId = warehouse.deptId
      } else {
        this.form.fromDeptId = warehouse.deptId
        this.form.fromDeptName = warehouse.deptName
        this.form.fromWarehouseId = warehouse.deptId
      }
      this.stockList = []
      this.selectedStockRows = []
      this.refreshAllDetailAvailability()
    },
    onSourceStoreChange(deptId) {
      const source = this.authorizedSourceStores.find(store => String(store.deptId) === String(deptId))
      this.form.fromDeptId = deptId
      this.form.fromWarehouseId = deptId
      this.form.fromDeptName = source ? source.deptName : ""
      this.stockList = []
      this.selectedStockRows = []
      this.refreshAllDetailAvailability()
    },
    applyCurrentShopToForm() {
      const currentDeptId = this.normalizeId(this.currentShopDeptId)
      if (this.form.transferType === "store_return") {
        this.form.fromDeptId = currentDeptId
        this.form.fromWarehouseId = currentDeptId
        this.form.fromDeptName = this.currentShopName === "未选择门店" ? "" : this.currentShopName
        if (this.form.toWarehouseId && !this.form.toDeptId) this.form.toDeptId = this.form.toWarehouseId
      } else {
        this.form.toDeptId = currentDeptId
        this.form.toWarehouseId = currentDeptId
        this.form.toDeptName = this.currentShopName === "未选择门店" ? "" : this.currentShopName
        if (this.form.fromWarehouseId && !this.form.fromDeptId) this.form.fromDeptId = this.form.fromWarehouseId
        if (this.isCrossStoreForm && this.form.fromDeptId && !this.form.fromWarehouseId) {
          this.form.fromWarehouseId = this.form.fromDeptId
        }
      }
    },
    normalizeId(value) {
      if (value === undefined || value === null || value === "") return undefined
      const num = Number(value)
      return Number.isNaN(num) ? value : num
    },
    normalizeTransferDetail(item) {
      return Object.assign({}, this.emptyDetail(), item, {
        itemType: item.itemType || "product",
        itemId: item.itemId || item.productId,
        itemName: item.itemName || item.productName || "",
        itemCode: item.itemCode || item.productCode || ""
      })
    },
    hasSameItem(items, itemType, itemId) {
      if (!itemId) return false
      return (items || []).some(item => {
        const currentType = item.itemType || "product"
        const currentId = item.itemId || item.productId
        return currentType === itemType && String(currentId) === String(itemId)
      })
    },
    openForm(row, transferType = "warehouse") {
      if (!this.ensureStoreContext()) return
      const formRequestSequence = ++this.formEditRequestSequence
      if (!row && transferType === "store_return" && !this.storeReturnEnabled) {
        this.$modal.msgWarning("门店返仓新建暂未开放")
        return
      }
      if (!row) {
        this.form = this.emptyForm()
        this.form.transferType = transferType
        this.applyCurrentShopToForm()
        this.open = true
        this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate())
        return
      }
      const contextToken = this.transferContextToken()
      let availabilityStage = false
      getTransferDetail(row.transferId, { silentError: true }).then(res => {
        if (formRequestSequence !== this.formEditRequestSequence || !this.isTransferContextCurrent(contextToken)) return null
        const data = res.data || {}
        if (!this.canEditTransfer(data)) {
          this.$modal.msgError("只能编辑当前门店发起的草稿")
          return
        }
        this.form = Object.assign(this.emptyForm(), data, { details: data.details && data.details.length ? data.details.map(item => this.normalizeTransferDetail(item)) : [this.emptyDetail()] })
        this.applyCurrentShopToForm()
        if (this.form.transferType === "cross_store" &&
          String(this.form.sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED") {
          this.form.fromDeptId = undefined
          this.form.fromDeptName = ""
          this.form.fromWarehouseId = undefined
          this.form.details.forEach(detail => {
            this.$set(detail, "availableQuantity", undefined)
            this.$set(detail, "availabilityStatus", "unknown")
          })
        }
        availabilityStage = true
        const sourceReselectionRequired = this.form.transferType === "cross_store" &&
          String(this.form.sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED"
        return this.refreshAllDetailAvailability({ failOnError: !sourceReselectionRequired }).then(() => {
          if (formRequestSequence !== this.formEditRequestSequence || !this.isTransferContextCurrent(contextToken)) return null
          this.open = true
          this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate())
          return data
        })
      }).catch(error => {
        if (formRequestSequence !== this.formEditRequestSequence || !this.isTransferContextCurrent(contextToken)) return null
        this.handleTransferActionError(error, availabilityStage
          ? "来源库存复查失败，无法安全编辑草稿"
          : "加载要货草稿失败")
        return null
      })
    },
    addDetail() {
      this.openStockPicker()
    },
    openProductPicker() {
      if (!this.ensureStoreContext()) return
      if (this.isReturnForm) {
        this.$modal.msgError("返仓物料必须从当前门店实际可用库存中选择")
        return
      }
      if (this.isCrossStoreForm) {
        this.$modal.msgError("异店调货物料必须从所选调出门店的可用库存中选择")
        return
      }
      if (!this.form.fromWarehouseId) {
        this.$modal.msgError("请先选择要货仓库")
        return
      }
      this.productPickerForm = { itemType: null, itemId: undefined, item: null }
      this.productPickerOpen = true
    },
    onCatalogItemTypeChange() {
      this.productPickerForm.itemId = undefined
      this.productPickerForm.item = null
    },
    onCatalogItemSelected(item) {
      this.productPickerForm.item = item
    },
    confirmProductSelection() {
      const product = this.productPickerForm.item
      if (!product || !product.itemId) {
        this.$modal.msgError("请选择要货物料")
        return
      }
      if (this.hasSameItem(this.form.details, product.itemType, product.itemId)) {
        this.$modal.msgError("该物料已在要货明细中")
        return
      }
      const detail = this.buildDetailFromProduct(product)
      const emptyIndex = this.form.details.findIndex(item => !item.itemId && !item.productId)
      if (emptyIndex >= 0) {
        this.form.details.splice(emptyIndex, 1, detail)
      } else {
        this.form.details.push(detail)
      }
      this.productPickerOpen = false
      this.refreshDetailAvailability(detail)
    },
    buildDetailFromProduct(product) {
      return {
        itemType: product.itemType || "product",
        itemId: product.itemId || product.productId,
        itemName: product.itemName || product.productName || "",
        itemCode: product.itemCode || product.productCode || "",
        productId: product.productId,
        productName: product.itemName || product.productName || "",
        productCode: product.itemCode || product.productCode || "",
        quantity: 1,
        deliveredQuantity: 0,
        receivedQuantity: 0,
        availableQuantity: undefined,
        availabilityStatus: "pending",
        costPrice: product.costPrice,
        unit: product.unit || "",
        spec: product.spec || "",
        grade: product.grade || "",
        goodsCondition: this.isReturnForm ? "NORMAL" : undefined,
        conditionNote: ""
      }
    },
    openSmartPaste() {
      if (!this.ensureStoreContext()) return
      if (!this.form.fromWarehouseId) {
        this.$modal.msgError(this.isReturnForm
          ? "当前返仓门店库存上下文无效，请重新选择门店"
          : (this.isCrossStoreForm ? "请先选择调出门店" : "请先选择要货仓库"))
        return
      }
      this.smartPasteOpen = true
    },
    applySmartPaste(payload) {
      const selectedRows = payload && Array.isArray(payload.items) ? payload.items : []
      if (!selectedRows.length) return
      let added = 0
      let merged = 0
      let emptyIndex = this.form.details.findIndex(item => !item.itemId && !item.productId)
      const availabilityRefreshes = []
      selectedRows.forEach(row => {
        const candidate = row.candidate
        if (!candidate) return
        const itemType = candidate.itemType || "product"
        const itemId = candidate.itemId || candidate.productId
        const archiveUnit = candidate.itemUnit || candidate.unit || ""
        const archiveSpec = candidate.itemSpec || candidate.spec || ""
        const existing = this.form.details.find(detail => {
          return (detail.itemType || "product") === itemType &&
            String(detail.itemId || detail.productId) === String(itemId)
        })
        if (existing) {
          existing.quantity = this.toNumber(existing.quantity) + this.toNumber(row.quantity)
          existing.unit = archiveUnit || existing.unit || ""
          existing.spec = archiveSpec || existing.spec || ""
          merged += 1
          return
        }
        const detail = candidate.source === "stock"
          ? this.buildDetailFromStock(candidate)
          : this.buildDetailFromProduct(candidate)
        detail.quantity = row.quantity
        if (emptyIndex >= 0) {
          this.form.details.splice(emptyIndex, 1, detail)
          emptyIndex = this.form.details.findIndex(item => !item.itemId && !item.productId)
        } else {
          this.form.details.push(detail)
        }
        if (candidate.source !== "stock") availabilityRefreshes.push(this.refreshDetailAvailability(detail))
        added += 1
      })
      const recipient = payload.recipient || {}
      ;["recipientName", "recipientPhone", "shippingAddress"].forEach(field => {
        if (recipient[field]) this.form[field] = recipient[field]
      })
      this.smartPasteOpen = false
      const unmatched = Number(payload.unmatchedCount || 0)
      const parts = ["已加入 " + added + " 项"]
      if (merged) parts.push("合并 " + merged + " 项已有明细")
      if (unmatched) parts.push(unmatched + " 行未匹配、未加入")
      this.$modal.msgSuccess(parts.join("；"))
      Promise.all(availabilityRefreshes).catch(() => {})
    },
    openStockPicker() {
      if (!this.ensureStoreContext()) return
      if (!this.form.fromWarehouseId) {
        if (this.isReturnForm) {
          this.$modal.msgError("当前返仓门店库存上下文无效，请重新选择门店")
        } else {
          this.$modal.msgError(this.isCrossStoreForm ? "请先选择调出门店" : "请先选择要货仓库")
        }
        return
      }
      this.stockQuery = {
        pageNum: 1,
        pageSize: this.stockQuery.pageSize || 10,
        itemType: null,
        itemId: undefined,
        productId: undefined,
        categoryId: undefined,
        shopDeptId: this.form.fromDeptId || this.form.fromWarehouseId,
        warehouseId: this.form.fromWarehouseId,
        stockStatus: "available",
        transferSource: true
      }
      this.stockSearchKeyword = ""
      this.selectedStockRows = []
      this.stockCategoryRequestId += 1
      this.stockCategoryOptions = []
      this.stockCategoryItemType = null
      this.stockPickerOpen = true
      this.stockList = []
      this.stockTotal = 0
    },
    loadStockPicker() {
      if (!this.stockQuery.itemType) {
        this.stockList = []
        this.stockTotal = 0
        return
      }
      const requestSequence = ++this.stockPickerRequestSequence
      const contextToken = this.transferContextToken()
      const isCurrentRequest = () => requestSequence === this.stockPickerRequestSequence &&
        this.isTransferContextCurrent(contextToken)
      this.stockPickerLoading = true
      const query = Object.assign({}, this.stockQuery, {
        shopDeptId: this.form.fromDeptId || this.form.fromWarehouseId,
        warehouseId: this.form.fromWarehouseId
      })
      const keyword = String(this.stockSearchKeyword || "").trim()
      if (keyword) {
        delete query.itemId
        delete query.productId
        query.productName = keyword
      }
      listStock(query).then(res => {
        if (!isCurrentRequest()) return null
        this.stockList = res.rows || []
        this.stockTotal = res.total || 0
        return this.hydrateStockProducts(this.stockList)
      }).finally(() => {
        if (isCurrentRequest()) this.stockPickerLoading = false
      })
    },
    handleStockQueryChange() {
      this.stockQuery.pageNum = 1
    },
    handleStockKeywordChange(keyword) {
      this.stockSearchKeyword = String(keyword || "").trim()
      this.stockQuery.pageNum = 1
    },
    handleStockItemTypeChange() {
      this.stockQuery.itemId = undefined
      this.stockQuery.productId = undefined
      this.stockQuery.categoryId = undefined
      this.stockSearchKeyword = ""
      this.selectedStockRows = []
      this.stockCategoryOptions = []
      this.stockCategoryItemType = null
      this.loadStockCategories(this.stockQuery.itemType)
      this.loadStockPicker()
    },
    handleStockCategoryChange() {
      this.stockQuery.pageNum = 1
    },
    resetStockPicker() {
      this.stockQuery = {
        pageNum: 1,
        pageSize: this.stockQuery.pageSize || 10,
        itemType: null,
        itemId: undefined,
        productId: undefined,
        categoryId: undefined,
        shopDeptId: this.form.fromDeptId || this.form.fromWarehouseId,
        warehouseId: this.form.fromWarehouseId,
        stockStatus: "available",
        transferSource: true
      }
      this.stockSearchKeyword = ""
      this.stockList = []
      this.stockTotal = 0
      this.stockCategoryRequestId += 1
      this.stockCategoryOptions = []
      this.stockCategoryItemType = null
    },
    loadStockCategories(itemType) {
      if (!["product", "gift"].includes(itemType)) {
        this.stockCategoryOptions = []
        this.stockCategoryItemType = itemType || null
        return Promise.resolve()
      }
      const requestId = this.stockCategoryRequestId + 1
      this.stockCategoryRequestId = requestId
      this.stockCategoryLoading = true
      this.stockCategoryOptions = []
      this.stockCategoryItemType = itemType
      const request = itemType === "gift" ? giftCategoryTree() : categoryTree()
      return request.then(res => {
        if (requestId === this.stockCategoryRequestId) {
          this.stockCategoryOptions = this.decorateStockCategories(res.data || [])
        }
      }).catch(() => {
        if (requestId === this.stockCategoryRequestId) this.stockCategoryOptions = []
      }).finally(() => {
        if (requestId === this.stockCategoryRequestId) this.stockCategoryLoading = false
      })
    },
    openCatalogFromStockPicker() {
      const itemType = this.stockQuery.itemType || "gift"
      this.stockPickerOpen = false
      this.productPickerForm = { itemType, itemId: undefined, item: null }
      this.productPickerOpen = true
    },
    decorateStockCategories(list) {
      return (list || []).map(item => {
        const children = this.decorateStockCategories(item.children || [])
        return Object.assign({}, item, {
          children: children.length ? children : undefined
        })
      })
    },
    hydrateStockProducts(rows) {
      const productIds = (rows || [])
        .filter(row => row && (row.itemType || "product") === "product" && !row.itemName && !row.productName)
        .map(row => row.productId)
        .filter(value => value !== undefined && value !== null && value !== "" && !this.stockProductMap[String(value)])
      const uniqueProductIds = Array.from(new Set(productIds.map(value => String(value))))
      if (uniqueProductIds.length === 0) return Promise.resolve()
      return Promise.all(uniqueProductIds.map(productId => {
        return getProduct(productId).then(res => res.data).catch(() => null)
      })).then(products => {
        const nextMap = Object.assign({}, this.stockProductMap)
        products.forEach(product => {
          if (product && product.productId) {
            nextMap[String(product.productId)] = product
          }
        })
        this.stockProductMap = nextMap
      })
    },
    resolveStockProduct(row) {
      return this.stockProductMap[String(row.productId)] || {}
    },
    stockProductName(row) {
      const product = this.resolveStockProduct(row)
      return this.displayValue(row.itemName || row.productName || product.productName || (row.itemId || row.productId ? "物料编号 " + (row.itemId || row.productId) : ""))
    },
    stockProductCode(row) {
      const product = this.resolveStockProduct(row)
      return this.displayValue(row.itemCode || row.productCode || product.productCode)
    },
    stockProductCategory(row) {
      const product = this.resolveStockProduct(row)
      return this.displayValue(row.itemCategoryFullPath || row.itemCategoryName || row.categoryFullPath || row.categoryName || product.categoryFullPath || product.categoryName)
    },
    stockProductGrade(row) {
      const product = this.resolveStockProduct(row)
      return this.displayValue(row.itemGrade || row.grade || product.grade)
    },
    stockProductSpec(row) {
      const product = this.resolveStockProduct(row)
      return this.displayValue(row.itemSpec || row.spec || product.spec)
    },
    stockAvailableQuantity(row) {
      if (row.availableQuantity !== undefined && row.availableQuantity !== null && row.availableQuantity !== "") {
        return row.availableQuantity
      }
      return Math.max(this.toNumber(row.currentQuantity) - this.toNumber(row.lockedQuantity), 0)
    },
    refreshAllDetailAvailability(options = {}) {
      const details = (this.form.details || []).filter(item => item.itemId || item.productId)
      return Promise.all(details.map(item => this.refreshDetailAvailability(item, options)))
    },
    invalidateDetailAvailabilityRequests() {
      this.detailAvailabilityRequestSequence += 1
      this.detailAvailabilityRequestTokens = new WeakMap()
    },
    refreshDetailAvailability(detail, options = {}) {
      const itemId = detail && (detail.itemId || detail.productId)
      if (!detail || !itemId) {
        return Promise.resolve()
      }
      const requestId = ++this.detailAvailabilityRequestSequence
      this.detailAvailabilityRequestTokens.set(detail, requestId)
      if (!this.form.fromWarehouseId) {
        this.$set(detail, "availableQuantity", undefined)
        this.$set(detail, "availabilityStatus", "unknown")
        return options.failOnError
          ? Promise.reject(new Error("草稿缺少来源组织，请重新选择补货仓库"))
          : Promise.resolve()
      }
      const sourceDeptId = this.form.fromDeptId || this.form.fromWarehouseId
      const sourceWarehouseId = this.form.fromWarehouseId
      const isCurrentRequest = () => this.detailAvailabilityRequestTokens.get(detail) === requestId &&
        String(this.form.fromDeptId || this.form.fromWarehouseId) === String(sourceDeptId) &&
        String(this.form.fromWarehouseId) === String(sourceWarehouseId)
      this.$set(detail, "availabilityStatus", "pending")
      const query = {
        pageNum: 1,
        pageSize: 1,
        itemType: detail.itemType || "product",
        itemId,
        shopDeptId: sourceDeptId,
        warehouseId: sourceWarehouseId,
        transferSource: true
      }
      return listStock(query, { silentError: true }).then(res => {
        if (!isCurrentRequest()) return
        const stock = (res.rows || [])[0]
        this.$set(detail, "availableQuantity", stock ? this.stockAvailableQuantity(stock) : 0)
        if (stock) {
          this.$set(detail, "costPrice", stock.costPrice)
        }
        this.$set(detail, "availabilityStatus", "loaded")
      }).catch(error => {
        if (!isCurrentRequest()) return
        this.$set(detail, "availableQuantity", undefined)
        this.$set(detail, "availabilityStatus", "unknown")
        if (options.failOnError) throw error
      })
    },
    isDetailShortage(row) {
      return row && row.availabilityStatus === "loaded" && this.toNumber(row.availableQuantity) <= 0
    },
    isDetailAvailabilityPending(row) {
      return row && (row.availabilityStatus === "pending" || row.availabilityStatus === "unknown")
    },
    isStockSelectable(row) {
      if (this.toNumber(this.stockAvailableQuantity(row)) <= 0) return false
      return !this.hasSameItem(this.form.details, row.itemType || "product", row.itemId || row.productId)
    },
    handleStockSelectionChange(rows) {
      this.selectedStockRows = rows || []
    },
    confirmStockSelection() {
      if (this.selectedStockRows.length === 0) {
        this.$modal.msgError(this.isReturnForm ? "请选择返仓物料" : "请选择要货物料")
        return
      }
      let emptyIndex = this.form.details.findIndex(item => !item.itemId && !item.productId)
      this.selectedStockRows.forEach(row => {
        const detail = this.buildDetailFromStock(row)
        if (emptyIndex >= 0) {
          this.form.details.splice(emptyIndex, 1, detail)
          emptyIndex = this.form.details.findIndex(item => !item.itemId && !item.productId)
        } else {
          this.form.details.push(detail)
        }
      })
      this.stockPickerOpen = false
    },
    buildDetailFromStock(row) {
      const product = this.resolveStockProduct(row)
      return {
        itemType: row.itemType || "product",
        itemId: row.itemId || row.productId,
        itemName: row.itemName || row.productName || product.productName || "",
        itemCode: row.itemCode || row.productCode || product.productCode || "",
        productId: row.productId,
        productName: row.itemName || row.productName || product.productName || "",
        productCode: row.itemCode || row.productCode || product.productCode || "",
        quantity: 1,
        deliveredQuantity: 0,
        receivedQuantity: 0,
        availableQuantity: this.stockAvailableQuantity(row),
        availabilityStatus: "loaded",
        costPrice: row.costPrice,
        unit: row.itemUnit || row.unit || product.unit || "",
        spec: row.itemSpec || row.spec || product.spec || "",
        grade: row.itemGrade || row.grade || product.grade || "",
        goodsCondition: this.isReturnForm ? "NORMAL" : undefined,
        conditionNote: ""
      }
    },
    removeDetail(index) {
      this.form.details.splice(index, 1)
      if (this.form.details.length === 0) {
        this.form.details.push(this.emptyDetail())
      }
    },
    validateDetails() {
      if (!this.form.details || this.form.details.length === 0) {
        this.$modal.msgError(this.isReturnForm
          ? "请添加至少一条返仓明细"
          : (this.isCrossStoreForm ? "请添加至少一条调货明细" : "请添加至少一条要货明细"))
        return false
      }
      if (this.form.details.some(item => !item.itemType || !(item.itemId || item.productId))) {
        this.$modal.msgError("请为所有明细行选择物料")
        return false
      }
      if (this.form.details.some(item => !item.quantity || Number(item.quantity) <= 0)) {
        this.$modal.msgError(this.isReturnForm
          ? "返仓数量必须大于0"
          : (this.isCrossStoreForm ? "需求数量必须大于0" : "要货数量必须大于0"))
        return false
      }
      return true
    },
    handleActionError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      this.$modal.msgError(message)
    },
    doSave(submitAfter) {
      const actionToken = this.captureTransferAction()
      if (!this.ensureStoreContext()) return
      const isReturnForm = this.isReturnForm
      const isCrossStoreForm = this.isCrossStoreForm
      if (this.isReturnForm && (!this.form.transferId || submitAfter) && !this.storeReturnEnabled) {
        this.$modal.msgWarning("门店返仓新建和提交暂未开放")
        return
      }
      this.applyCurrentShopToForm()
      if (this.isReturnForm && this.form.returnReasonCode === "OTHER" && !String(this.form.returnReasonText || "").trim()) {
        this.$modal.msgError("请填写其他返仓原因")
        return
      }
      this.$refs.formRef.validate(valid => {
        if (!this.isTransferActionCurrent(actionToken) || !valid || !this.validateDetails()) return
        const payload = this.snapshotTransferValue(Object.assign({}, this.form, {
          totalQuantity: this.form.details.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
          totalAmount: this.formTotalAmount,
          details: this.form.details.map((item, index) => this.buildDetailPayload(item, index))
        }))
        const api = submitAfter ? submitTransfer : saveTransfer
        api(payload).then(response => {
          if (!this.isTransferActionCurrent(actionToken)) return response
          if (submitAfter) {
            this.showSubmitResult(response)
          } else {
            this.$modal.msgSuccess("已保存草稿")
          }
          this.open = false
          this.getList()
          acknowledgeTransferCommand(response)
        }).catch(error => {
          if (!this.isTransferActionCurrent(actionToken)) return
          this.handleTransferActionError(error, submitAfter
            ? (isReturnForm ? "提交返仓单失败" : (isCrossStoreForm ? "提交异店调货单失败" : "提交要货单失败"))
            : (isReturnForm ? "保存返仓单失败" : (isCrossStoreForm ? "保存异店调货单失败" : "保存要货单失败")))
        })
      })
    },
    buildDetailPayload(item, index) {
      return {
        detailId: item.detailId,
        transferId: item.transferId,
        itemType: item.itemType || "product",
        itemId: item.itemId || item.productId,
        itemName: item.itemName || item.productName,
        itemCode: item.itemCode || item.productCode,
        productId: item.productId,
        productName: item.productName,
        productCode: item.productCode,
        quantity: item.quantity,
        deliveredQuantity: item.deliveredQuantity,
        receivedQuantity: item.receivedQuantity,
        costPrice: item.costPrice,
        amount: this.lineAmount(item),
        unit: item.unit,
        spec: item.spec,
        grade: item.grade,
        goodsCondition: item.goodsCondition,
        conditionNote: item.conditionNote,
        lotId: item.lotId,
        sourceLocationId: item.sourceLocationId,
        sortOrder: index
      }
    },
    handleSubmit(row) {
      if (!this.canSubmitTransfer(row)) {
        this.$modal.msgError("只能提交当前门店发起的草稿")
        return
      }
      if (row && row.transferType === "store_return" && !this.storeReturnEnabled) {
        this.$modal.msgWarning("门店返仓提交暂未开放")
        return
      }
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      getTransferDetail(transferId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        const payload = this.snapshotTransferValue(res.data || {})
        return submitTransfer(payload).then(response => {
          if (!this.isTransferActionCurrent(actionToken)) return response
          this.showSubmitResult(response)
          this.getList()
          acknowledgeTransferCommand(response)
        }).catch(error => {
          if (!this.isTransferActionCurrent(actionToken)) return
          this.handleActionError(error, "提交失败，请确认当前门店仍有权提交该要货单")
        })
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.handleTransferActionError(error, "提交要货单失败")
      })
    },
    showSubmitResult(response) {
      const warnings = response && response.data && Array.isArray(response.data.approvalWarnings)
        ? response.data.approvalWarnings.filter(Boolean)
        : []
      if (warnings.length > 0) {
        this.$modal.msgWarning("提交成功；" + warnings.join("；"))
        return
      }
      this.$modal.msgSuccess("提交成功")
    },
    handleApprove(row) {
      this.openTransferApproval(row, "approve")
    },
    handleReject(row) {
      this.openTransferApproval(row, "reject")
    },
    openTransferApproval(row, action) {
      const source = row || {}
      const actionToken = this.captureTransferAction()
      this.transferApprovalReview = Object.assign({}, source)
      const native = this.isNativeApproval(source)
      const taskId = native ? this.unifiedTaskId(source) : (source.currentTaskId || source.taskId)
      const normalizedAction = action === "return" ? "return" : action === "reject" ? "reject" : "approve"
      const prepare = native ? this.loadTransferApproval(source) : Promise.resolve()
      return prepare.then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        if (native && (!taskId || !this.canHandleUnifiedApproval(source))) {
          this.$modal.msgWarning(taskId ? "该统一审批待办已处理或不再属于当前用户" : "请从工作台待办进入统一审批")
          return
        }
        this.transferApprovalForm = {
          transferId: source.transferId,
          taskId,
          instanceId: source.approvalInstanceId || this.approvalRouteContext().instanceId,
          engineMode: native ? "NATIVE" : "LEGACY",
          orderNo: source.orderNo || "",
          action: normalizedAction,
          comment: ""
        }
        this.transferApprovalOpen = true
      })
    },
    submitTransferApproval() {
      const actionToken = this.captureTransferAction()
      const formSnapshot = this.snapshotTransferValue(this.transferApprovalForm) || {}
      const native = formSnapshot.engineMode === "NATIVE"
      const action = formSnapshot.action
      const reason = String(formSnapshot.comment || "").trim()
      if (native && !formSnapshot.taskId) {
        this.$modal.msgError("当前统一审批任务不存在，请从工作台重新进入")
        return
      }
      if (action !== "approve" && !reason) {
        this.$modal.msgError(action === "return" ? "请输入退回原因" : action === "reject" ? "请输入驳回原因" : "请输入审批意见")
        return
      }
      let payload
      let request
      if (native) {
        const taskAction = action === "approve"
          ? approveApprovalTask
          : action === "return" ? returnApprovalTask : rejectApprovalTask
        payload = {
          requestId: `INV_TRANSFER:${formSnapshot.taskId}:${action}:${Date.now()}`,
          reason
        }
        request = taskAction(formSnapshot.taskId, payload)
      } else {
        try {
          payload = buildTransferApprovalPayload(formSnapshot.transferId, formSnapshot)
        } catch (error) {
          this.$modal.msgError(error.message || "请输入审批意见")
          return
        }
        request = approveTransfer(payload)
      }
      this.transferApprovalLoading = true
      return request.then(response => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.$modal.msgSuccess(native
          ? "统一审批动作已提交"
          : payload.action === "reject" ? "已驳回，可修改后重新提交" : "审批通过")
        this.transferApprovalOpen = false
        this.detailOpen = false
        acknowledgeTransferCommand(response)
        const invalidation = this.$store && typeof this.$store.dispatch === "function"
          ? this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
          : Promise.resolve()
        return Promise.resolve(invalidation)
          .then(() => returnAfterTodoAction({
            router: this.$router,
            todoType: "INV_TRANSFER_APPROVAL",
            businessId: formSnapshot.transferId
          }))
          .then(result => result && result.returned ? result : this.getList())
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.handleTransferActionError(error, native
          ? "统一审批动作提交失败"
          : payload.action === "reject" ? "驳回要货单失败" : "审批要货单失败")
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.transferApprovalLoading = false
      })
    },
    openSourceConfirm(row) {
      if (!this.canConfirmSource(row)) {
        this.$modal.msgError("只能由当前调出门店确认已审批的异店调货")
        return
      }
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      getTransferDetail(transferId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const data = res.data || {}
        if (!this.canConfirmSource(data)) {
          this.$modal.msgWarning("该异店调货已确认或状态已变化")
          this.getList()
          return
        }
        this.sourceConfirmDetail = data
        this.sourceConfirmForm = {
          items: (data.details || []).map(item => ({
            detailId: item.detailId,
            productName: item.itemName || item.productName,
            requestedQuantity: this.toNumber(item.quantity),
            confirmedQuantity: this.toNumber(item.quantity)
          })),
          remark: ""
        }
        this.sourceConfirmOpen = true
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.handleTransferActionError(error, "加载异店调货明细失败")
      })
    },
    sourceConfirmRemainder(item) {
      return Math.max(Number((this.toNumber(item && item.requestedQuantity) -
        this.toNumber(item && item.confirmedQuantity)).toFixed(2)), 0)
    },
    fillSourceConfirmAll() {
      this.sourceConfirmForm.items.forEach(item => {
        item.confirmedQuantity = item.requestedQuantity
      })
    },
    submitSourceConfirm() {
      if (!this.canConfirmSource(this.sourceConfirmDetail)) {
        this.$modal.msgError("当前门店不能确认该异店调货")
        return
      }
      const actionToken = this.captureTransferAction()
      const invalid = this.sourceConfirmForm.items.find(item => {
        const quantity = Number(item.confirmedQuantity)
        return !Number.isFinite(quantity) || quantity < 0 || quantity > Number(item.requestedQuantity)
      })
      if (invalid) {
        this.$modal.msgError((invalid.productName || "物料") + "：确认数量必须在0和申请数量之间")
        return
      }
      const remark = String(this.sourceConfirmForm.remark || "").trim()
      if (this.sourceConfirmHasRemainder && !remark) {
        this.$modal.msgError("有部分或全部无法调出时，请填写确认说明")
        return
      }
      const sourceConfirmDetail = this.snapshotTransferValue(this.sourceConfirmDetail) || {}
      const transferId = sourceConfirmDetail.transferId
      const hasRemainder = this.sourceConfirmHasRemainder
      const payload = this.snapshotTransferValue({
        items: this.sourceConfirmForm.items.map(item => ({
          detailId: item.detailId,
          confirmedQuantity: Number(item.confirmedQuantity)
        })),
        remark
      })
      const confirmedTotal = payload.items.reduce((sum, item) => sum + item.confirmedQuantity, 0)
      const message = hasRemainder
        ? (confirmedTotal > 0
            ? "确认部分可调？未确认余量将自动退回调入店，生成待重选调出店草稿。"
            : "确认本店全部无法调出？原单将关闭，全部数量退回调入店重新选择调出店。")
        : "确认本店可按申请数量全部调出？确认后方可进入发货。"
      this.$modal.confirm(message).then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        this.actionLoading = true
        return confirmTransferSource(transferId, payload)
      }).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const result = res && res.data || {}
        this.$modal.msgSuccess(result.reselectionOrderNo
          ? "确认完成，余量草稿 " + result.reselectionOrderNo + " 已退回调入店"
          : "调出店确认完成，可以发货")
        this.sourceConfirmOpen = false
        this.detailOpen = false
        this.getList()
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        if (error !== "cancel" && error !== "close") {
          this.handleTransferActionError(error, "调出店确认失败")
        }
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.actionLoading = false
      })
    },
    openShipment(row) {
      if (!this.ensureCanShip(row)) return
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      const rowSnapshot = this.snapshotTransferValue(row) || {}
      getTransferDetail(transferId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const data = mergeTransferDetailForAction(res.data, rowSnapshot)
        this.shipmentDetail = data
        this.shipmentForm.items = (data.details || []).map(item => {
          const remaining = this.remainingToShip(item)
          return {
            detailId: item.detailId,
            productName: item.itemName || item.productName,
            quantity: this.toNumber(item.quantity),
            deliveredQuantity: this.toNumber(item.deliveredQuantity),
            remainingQuantity: remaining,
            deliverQuantity: remaining
          }
        })
        this.shipmentOpen = true
      })
    },
    fillShipmentRemaining() {
      this.shipmentForm.items.forEach(item => {
        item.deliverQuantity = item.remainingQuantity
      })
    },
    submitShipment() {
      if (!this.ensureCanShip(this.shipmentDetail)) return
      const actionToken = this.captureTransferAction()
      const shipmentDetail = this.snapshotTransferValue(this.shipmentDetail) || {}
      const items = this.snapshotTransferValue(this.shipmentForm.items
        .filter(item => Number(item.deliverQuantity || 0) > 0)
        .map(item => ({ detailId: item.detailId, deliverQuantity: item.deliverQuantity })))
      if (items.length === 0) {
        this.$modal.msgError("请至少填写一条本次发货数量")
        return
      }
      const transferId = shipmentDetail.transferId
      this.$modal.confirm(this.getShipmentConfirmMessage(items)).then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        this.actionLoading = true
        return deliverTransfer(transferId, { items })
      }).then(response => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.$modal.msgSuccess("发货成功")
        this.shipmentOpen = false
        this.getList()
        acknowledgeTransferCommand(response)
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken) || this.isCancelError(error)) return
        this.handleTransferActionError(error, "确认发货失败")
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.actionLoading = false
      })
    },
    openReceipt(row, todoFocus) {
      if (!this.ensureCanReceive(row)) return
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      const todoFocusSnapshot = this.snapshotTransferValue(todoFocus)
      getTransferDetail(transferId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const data = this.snapshotTransferValue(res.data || {})
        const pendingShipments = (data.shipments || []).filter(item => item.status === "pending_receive")
        if (pendingShipments.length === 0) {
          if (todoFocusSnapshot && todoFocusSnapshot.shipmentId) this.showTodoBusinessHandled()
          else this.$modal.msgError("该要货单没有待收货发货批次")
          return
        }
        this.receiptDetail = data
        this.receiptForm.shipments = pendingShipments
        const focusedShipment = todoFocusSnapshot && todoFocusSnapshot.shipmentId
          ? findTodoFocusShipment(data, todoFocusSnapshot)
          : null
        if (todoFocusSnapshot && todoFocusSnapshot.shipmentId && !focusedShipment) {
          this.receiptOpen = false
          this.showTodoBusinessHandled()
          return
        }
        this.applyReceiptShipment(focusedShipment || pendingShipments[0])
        this.receiptOpen = true
      })
    },
    onReceiptShipmentChange(shipmentId) {
      const shipment = this.receiptForm.shipments.find(item => String(item.shipmentId) === String(shipmentId))
      if (shipment) {
        this.applyReceiptShipment(shipment)
      }
    },
    applyReceiptShipment(shipment) {
      this.receiptForm.shipmentId = shipment.shipmentId
      this.receiptForm.shipmentNo = shipment.shipmentNo
      this.receiptForm.shippedBy = shipment.shippedBy
      this.receiptForm.shippedTime = shipment.shippedTime
      this.receiptForm.items = (shipment.details || []).map(item => {
        const shipped = this.toNumber(item.shippedQuantity)
        const received = this.toNumber(item.receivedQuantity)
        const remaining = Math.max(shipped - received, 0)
        return {
          detailId: item.transferDetailId,
          productName: item.itemName || item.productName,
          shippedQuantity: shipped,
          receivedQuantity: received,
          remainingQuantity: remaining,
          receiveQuantity: remaining,
          rejectedQuantity: 0,
          damagedQuantity: 0,
          discrepancyNote: "",
          attachmentRefs: ""
        }
      })
    },
    shipmentOptionLabel(shipment) {
      const total = (shipment.details || []).reduce((sum, item) => sum + this.toNumber(item.shippedQuantity), 0)
      return shipment.shipmentNo + " / " + this.formatQuantity(total) + " 件"
    },
    submitReceipt() {
      if (!this.ensureCanReceive(this.receiptDetail)) return
      if (this.receiptForm.items.some(row => row.evidenceState && !row.evidenceState.valid))
        return this.$modal.msgError("凭证尚未上传完成或不可用，请先处理附件后再收货")
      const actionToken = this.captureTransferAction()
      if (!this.transferDiscrepancyEnabled) {
        const difference = this.receiptForm.items.find(item =>
          Math.abs(this.toNumber(item.receiveQuantity) - this.toNumber(item.remainingQuantity)) > 0.000001 ||
          this.toNumber(item.rejectedQuantity) > 0 || this.toNumber(item.damagedQuantity) > 0)
        if (difference) {
          this.$modal.msgWarning("调拨差异新建暂未开放，请按本批发货数量全量正常收货")
          return
        }
      }
      const invalid = this.receiptForm.items.find(item => {
        const classified = this.toNumber(item.receiveQuantity) + this.toNumber(item.rejectedQuantity) + this.toNumber(item.damagedQuantity)
        return classified > this.toNumber(item.remainingQuantity) + 0.000001
      })
      if (invalid) return this.$modal.msgError(`物料「${invalid.productName}」分类数量超过本批待收数量`)
      const missingNote = this.receiptForm.items.find(item => {
        const hasDifference = this.receiptShortage(item) > 0 || this.toNumber(item.rejectedQuantity) > 0 || this.toNumber(item.damagedQuantity) > 0
        return hasDifference && !String(item.discrepancyNote || item.attachmentRefs || "").trim()
      })
      if (missingNote) return this.$modal.msgError(`物料「${missingNote.productName}」存在差异，请填写说明或凭证`)
      const items = this.snapshotTransferValue(this.receiptForm.items.map(item => ({
        detailId: item.detailId,
        receiveQuantity: this.toNumber(item.receiveQuantity),
        rejectedQuantity: this.toNumber(item.rejectedQuantity),
        damagedQuantity: this.toNumber(item.damagedQuantity),
        discrepancyNote: item.discrepancyNote,
        attachmentRefs: item.attachmentRefs
      })))
      if (!this.receiptForm.shipmentId) {
        this.$modal.msgError("请选择待收货发货批次")
        return
      }
      const receiptDetail = this.snapshotTransferValue(this.receiptDetail) || {}
      const receiptForm = this.snapshotTransferValue(this.receiptForm) || {}
      const shipmentId = receiptForm.shipmentId
      const warehouseId = this.currentShopDeptId
      const hasDifference = (receiptForm.items || []).some(item =>
        this.toNumber(item.receiveQuantity) < this.toNumber(item.remainingQuantity) ||
        this.toNumber(item.rejectedQuantity) > 0 || this.toNumber(item.damagedQuantity) > 0)
      this.$modal.confirm(this.getReceiptConfirmMessage(items)).then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        this.actionLoading = true
        return receiveShipment(shipmentId, { warehouseId, items })
      }).then(response => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.$modal.msgSuccess(hasDifference ? "收货已登记，差异单已生成" : "收货成功")
        this.receiptOpen = false
        this.getList()
        acknowledgeTransferCommand(response)
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken) || this.isCancelError(error)) return
        this.handleTransferActionError(error, "确认收货失败")
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.actionLoading = false
      })
    },
    receiptShortage(item) {
      const classified = this.toNumber(item.receiveQuantity) + this.toNumber(item.rejectedQuantity) + this.toNumber(item.damagedQuantity)
      return Math.max(this.toNumber(item.remainingQuantity) - classified, 0)
    },
    discrepancyStatusLabel(status) {
      return { OPEN: "待处理", PENDING_QC: "待质检", RESOLVED: "已处理" }[status] || "未知差异状态"
    },
    discrepancyTypeLabel(type) {
      return {
        SHORTAGE: "数量短少",
        REJECTED: "拒收",
        DAMAGED: "残损",
        MIXED: "多种差异",
        PENDING_QC: "待质检确认"
      }[String(type || "").toUpperCase()] || "其他差异"
    },
    resolutionDecisionLabel(decision) {
      return {
        ACCEPT_ACTUAL: "按实收结案",
        RESHIP: "补发",
        RETURN_SOURCE: "退回来源方",
        PENDING_QC: "待质检",
        WRITE_OFF: "核销差异",
        MIXED: "逐项处理"
      }[String(decision || "").toUpperCase()] || "尚未处理"
    },
    discrepancyCategoryLabel(category) {
      return {
        SHORTAGE: "短少",
        REJECTED: "拒收",
        DAMAGED: "残损"
      }[String(category || "").toUpperCase()] || "差异"
    },
    discrepancyDecisionOptions(category, pendingQc) {
      const normalized = String(category || "").toUpperCase()
      const options = {
        RESHIP: "安排补发",
        RETURN_SOURCE: "退回来源",
        ACCEPT_ACTUAL: "按实收结案",
        PENDING_QC: "转待质检",
        WRITE_OFF: "核销差异"
      }
      let values = []
      if (normalized === "SHORTAGE" && !pendingQc) values = ["RESHIP", "ACCEPT_ACTUAL", "WRITE_OFF"]
      if (normalized === "REJECTED") values = pendingQc ? ["RETURN_SOURCE", "WRITE_OFF"] : ["RETURN_SOURCE", "PENDING_QC", "WRITE_OFF"]
      if (normalized === "DAMAGED") values = pendingQc ? ["RETURN_SOURCE", "WRITE_OFF"] : ["PENDING_QC", "RETURN_SOURCE", "WRITE_OFF"]
      return values.map(value => ({ value, label: options[value] }))
    },
    createDiscrepancyRequestId(discrepancyId) {
      return "ITD:" + discrepancyId + ":" + Date.now() + ":" + Math.random().toString(36).slice(2, 10)
    },
    buildDiscrepancyResolutionItems(discrepancy, detailSnapshot) {
      const details = Array.isArray(discrepancy && discrepancy.details) ? discrepancy.details : []
      const dispositions = Array.isArray(discrepancy && discrepancy.dispositions) ? discrepancy.dispositions : []
      const latest = {}
      dispositions.forEach(row => {
        if (!row) return
        latest[String(row.discrepancyDetailId) + ":" + String(row.category || "").toUpperCase()] = row
      })
      const sourceDetail = detailSnapshot || this.detail || {}
      const shipments = Array.isArray(sourceDetail.shipments) ? sourceDetail.shipments : []
      const shipment = shipments.find(row => String(row.shipmentId) === String(discrepancy.shipmentId))
      const shipmentDetails = shipment && Array.isArray(shipment.details) ? shipment.details : []
      const pendingQc = String(discrepancy.status || "").toUpperCase() === "PENDING_QC"
      const categories = [
        ["SHORTAGE", "shortageQuantity"],
        ["REJECTED", "rejectedQuantity"],
        ["DAMAGED", "damagedQuantity"]
      ]
      const result = []
      details.forEach(detail => {
        categories.forEach(([category, quantityKey]) => {
          const quantity = this.toNumber(detail[quantityKey])
          if (quantity <= 0) return
          const key = String(detail.discrepancyDetailId) + ":" + category
          const prior = latest[key]
          if (prior && String(prior.decision || "").toUpperCase() !== "PENDING_QC") return
          const options = this.discrepancyDecisionOptions(category, pendingQc)
          if (!options.length) return
          const shipmentDetail = shipmentDetails.find(row => String(row.shipmentDetailId) === String(detail.shipmentDetailId))
          result.push({
            detailId: detail.discrepancyDetailId,
            itemName: detail.itemName || detail.itemCode || detail.discrepancyDetailId,
            itemCode: detail.itemCode,
            category,
            categoryLabel: this.discrepancyCategoryLabel(category),
            quantity,
            costPrice: shipmentDetail && shipmentDetail.costPrice !== undefined ? shipmentDetail.costPrice : null,
            decision: "",
            decisionOptions: options,
            note: prior && prior.note ? prior.note : "",
            attachmentRefs: prior && prior.attachmentRefs ? prior.attachmentRefs : (detail.attachmentRefs || ""),
            attachmentRequired: category === "DAMAGED" && !pendingQc
          })
        })
      })
      return result
    },
    openDiscrepancy(row) {
      if (!row || !row.discrepancyId) return
      const actionToken = this.captureTransferAction()
      const discrepancySource = this.snapshotTransferValue(row) || {}
      const discrepancyId = discrepancySource.discrepancyId
      const detailSnapshot = this.snapshotTransferValue(this.detail)
      this.actionLoading = true
      getTransferDiscrepancy(discrepancyId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const discrepancy = Object.assign({}, discrepancySource, this.snapshotTransferValue(res.data || {}))
        const items = this.buildDiscrepancyResolutionItems(discrepancy, detailSnapshot)
        this.selectedDiscrepancy = discrepancy
        this.discrepancyForm = {
          version: discrepancy.version,
          requestId: this.createDiscrepancyRequestId(discrepancy.discrepancyId),
          items,
          responsibleParty: discrepancy.responsibleParty || "UNCONFIRMED",
          note: ""
        }
        this.discrepancyOpen = true
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.handleTransferActionError(error, "加载差异台账失败")
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.actionLoading = false
      })
    },
    openDiscrepancyForTransfer(row) {
      const actionToken = this.captureTransferAction()
      const transferId = row && row.transferId
      if (!transferId) return
      getTransferDetail(transferId).then(res => {
        if (!this.isTransferActionCurrent(actionToken)) return
        const data = this.snapshotTransferValue(res.data || {})
        this.detail = data
        const discrepancy = (data.discrepancies || []).find(item => item.status !== "RESOLVED")
        if (!discrepancy) return this.$modal.msgWarning("当前调拨单没有待处理差异")
        this.openDiscrepancy(discrepancy)
      })
    },
    submitDiscrepancy() {
      if ((this.discrepancyForm.items || []).some(row => row.evidenceState && !row.evidenceState.valid))
        return this.$modal.msgError("凭证尚未上传完成、不可用或未选择，请先处理附件")
      if (!String(this.discrepancyForm.requestId || "").trim()) return this.$modal.msgError("差异处置 requestId 缺失，请重新打开")
      if (!this.discrepancyForm.items || !this.discrepancyForm.items.length) return this.$modal.msgError("没有可处置的差异类别，请刷新台账")
      if (!String(this.discrepancyForm.note || "").trim()) return this.$modal.msgError("请填写差异处理说明")
      const invalid = this.discrepancyForm.items.find(row => {
        const selected = (row.decisionOptions || []).some(option => option.value === row.decision)
        const requiresAttachment = row.category === "DAMAGED" && row.attachmentRequired && ["RETURN_SOURCE", "WRITE_OFF"].includes(row.decision)
        return !selected || (requiresAttachment && !require("@/utils/transferEvidence").hasEvidence(row.attachmentRefs))
      })
      if (invalid) return this.$modal.msgError(invalid.attachmentRequired ? `物料「${invalid.itemName}」残损终结处置需上传或选择凭证附件` : `物料「${invalid.itemName}」请选择合法处置决定`)
      const actionToken = this.captureTransferAction()
      const discrepancyId = this.selectedDiscrepancy && this.selectedDiscrepancy.discrepancyId
      const payload = this.snapshotTransferValue({
        requestId: this.discrepancyForm.requestId,
        version: this.discrepancyForm.version,
        responsibleParty: this.discrepancyForm.responsibleParty,
        note: this.discrepancyForm.note,
        items: this.discrepancyForm.items.map(row => ({
          detailId: row.detailId,
          category: row.category,
          decision: row.decision,
          quantity: row.quantity,
          note: row.note,
          attachmentRefs: row.attachmentRefs
        }))
      })
      this.actionLoading = true
      resolveTransferDiscrepancy(discrepancyId, payload).then(response => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.$modal.msgSuccess("差异已处理")
        this.discrepancyOpen = false
        this.detailOpen = false
        this.getList()
        acknowledgeTransferCommand(response)
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.handleTransferActionError(error, "差异处理失败")
      }).finally(() => {
        if (this.isTransferActionCurrent(actionToken)) this.actionLoading = false
      })
    },
    handleCancel(row) {
      if (!this.canCancelTransfer(row)) {
        this.$modal.msgError("只能取消当前门店发起的草稿或待审单")
        return
      }
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      const message = this.getTransferCancelConfirmMessage(this.snapshotTransferValue(row) || {})
      this.$modal.confirm(message).then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        return cancelTransfer(transferId)
      }).then(response => {
        if (!this.isTransferActionCurrent(actionToken)) return
        this.$modal.msgSuccess("已取消")
        this.getList()
        acknowledgeTransferCommand(response)
      }).catch(error => {
        if (!this.isTransferActionCurrent(actionToken) || this.isCancelError(error)) return
        this.handleTransferActionError(error, "取消要货单失败")
      })
    },
    handleWithdraw(row) {
      if (!this.canWithdrawNativeTransfer(row) || this.withdrawLoadingId) {
        if (!this.withdrawLoadingId) this.$modal.msgWarning("只有当前申请人可以撤回统一审批中的调拨单")
        return
      }
      const actionToken = this.captureTransferAction()
      const transferId = row.transferId
      this.$modal.confirm("确认撤回调拨审批？系统将使用默认原因“申请人撤回调拨审批”，业务状态将在审批回调后恢复为草稿。", "撤回审批").then(() => {
        if (!this.isTransferActionCurrent(actionToken)) return null
        this.withdrawLoadingId = transferId
        return withdrawTransfer(transferId).then(response => {
          if (!this.isTransferActionCurrent(actionToken)) return
          this.$modal.msgSuccess("撤回请求已提交")
          this.detailOpen = false
          if (this.$store && typeof this.$store.dispatch === "function") {
            this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
          }
          acknowledgeTransferCommand(response)
          return this.getList()
        }).finally(() => {
          if (this.isTransferActionCurrent(actionToken)) this.withdrawLoadingId = undefined
        })
      }).catch(() => {})
    },
    openDetail(transferId, contextRow) {
      if (transferId === undefined || transferId === null || transferId === "") return Promise.resolve(null)
      const requestSequence = ++this.detailRequestSequence
      this.approvalRequestSequence += 1
      this.detail = Object.assign({}, contextRow || {}, { transferId })
      this.detailLoading = true
      this.detailOpen = true
      this.clearDetailApprovalState()
      this.resetDetailDialogScroll()
      return getTransferDetail(transferId, { silentError: true }).then(res => {
        if (!this.isActiveDetailRequest(requestSequence, transferId)) return null
        this.detail = Object.assign({}, contextRow || {}, res.data || {}, { transferId })
        this.detailLoading = false
        this.resetDetailDialogScroll()
        void this.loadTransferApproval(this.detail)
        return this.detail
      }).catch(error => {
        if (!this.isActiveDetailRequest(requestSequence, transferId)) return null
        this.detailOpen = false
        this.handleActionError(error, "调拨详情加载失败，请重试")
        return null
      }).finally(() => {
        if (this.isActiveDetailRequest(requestSequence, transferId)) {
          this.detailLoading = false
        }
      })
    },
    isActiveDetailRequest(requestSequence, transferId) {
      return requestSequence === this.detailRequestSequence &&
        String(this.detail && this.detail.transferId) === String(transferId)
    },
    clearDetailApprovalState() {
      this.approvalTrackTransferId = null
      this.approvalTrack = null
      this.approvalTrackLoading = false
      this.approvalTrackError = false
      this.unifiedApprovalDetail = null
      this.unifiedApprovalLoading = false
      this.unifiedApprovalError = false
    },
    resetDetailDialogScroll() {
      this.$nextTick(() => {
        const dialog = this.$refs.transferDetailDialog
        const wrapper = dialog && dialog.$el
        if (!wrapper) return
        wrapper.scrollTop = 0
        const body = wrapper.querySelector(".el-dialog__body")
        if (body) body.scrollTop = 0
      })
    },
    handleDetailDialogClose() {
      this.detailRequestSequence += 1
      this.approvalRequestSequence += 1
      this.detailLoading = false
      this.clearDetailApprovalState()
    },
    handleDetailDialogClosed() {
      if (this.detailOpen) return
      this.detail = {}
      this.resetDetailDialogScroll()
    },
    loadApprovalTrack(transferId) {
      if (!transferId) return
      const source = this.detail && String(this.detail.transferId) === String(transferId)
        ? this.detail
        : { transferId }
      return this.loadTransferApproval(source)
    },
    loadTransferApproval(row) {
      const source = row || {}
      const transferId = source.transferId
      if (!transferId) return Promise.resolve(null)
      const requestSequence = ++this.approvalRequestSequence
      this.approvalTrackTransferId = transferId
      this.approvalTrack = null
      this.approvalTrackLoading = false
      this.approvalTrackError = false
      this.unifiedApprovalDetail = null
      this.unifiedApprovalLoading = false
      this.unifiedApprovalError = false
      if (this.isNativeApproval(source)) {
        const instanceId = source.approvalInstanceId || this.approvalRouteContext().instanceId
        if (!instanceId) {
          this.unifiedApprovalError = true
          return Promise.resolve(null)
        }
        this.approvalTrackLoading = false
        this.unifiedApprovalLoading = true
        return getApprovalInstance(instanceId).then(response => {
          if (this.isActiveApprovalRequest(requestSequence, transferId)) {
            this.unifiedApprovalDetail = unwrapData(response)
          }
          return this.unifiedApprovalDetail
        }).catch(() => {
          if (this.isActiveApprovalRequest(requestSequence, transferId)) this.unifiedApprovalError = true
          return null
        }).finally(() => {
          if (this.isActiveApprovalRequest(requestSequence, transferId)) this.unifiedApprovalLoading = false
        })
      }
      this.unifiedApprovalLoading = false
      this.approvalTrackLoading = true
      return getTransferApprovalTrack(transferId).then(res => {
        if (this.isActiveApprovalRequest(requestSequence, transferId)) {
          this.approvalTrack = res.data || null
        }
      }).catch(() => {
        if (this.isActiveApprovalRequest(requestSequence, transferId)) {
          this.approvalTrackError = true
        }
      }).finally(() => {
        if (this.isActiveApprovalRequest(requestSequence, transferId)) {
          this.approvalTrackLoading = false
        }
      })
    },
    isActiveApprovalRequest(requestSequence, transferId) {
      return requestSequence === this.approvalRequestSequence &&
        String(this.approvalTrackTransferId) === String(transferId)
    },
    handleExport() {
      confirmExportAction(this, {
        moduleName: "调拨处理中数据",
        rangeLabel: "当前查询结果",
        filterLabel: this.getTransferExportConfirmMessage()
      }).then(() => {
        this.download("inventory/transfer/export", this.buildTransferQuery(), this.exportFileName("调拨处理中数据"))
      })
    },
    getTransferApproveConfirmMessage(row) {
      const crossStore = row && row.transferType === "cross_store"
      return [
        "确认审批通过" + (crossStore ? "异店调货单" : "要货单") + " [" + this.displayValue(row.orderNo) + "]？",
        (crossStore ? "调入门店：" : "要货门店：") + this.displayValue(row.toDeptName || this.currentShopName),
        (crossStore ? "调出门店：" : "发货方：") + this.displayValue(row.fromDeptName),
        "总数量：" + this.formatQuantity(row.totalQuantity) + " 件",
        crossStore ? "审批通过后，必须先由调出门店确认可调数量，再执行发货。" : "审批通过后，来源仓库可继续执行发货。"
      ].join("\n")
    },
    getTransferCancelConfirmMessage(row) {
      return [
        "确认取消要货单 [" + this.displayValue(row.orderNo) + "]？",
        "要货门店：" + this.displayValue(row.toDeptName || this.currentShopName),
        "发货方：" + this.displayValue(row.fromDeptName),
        "取消后该单不能继续审批、发货或收货。"
      ].join("\n")
    },
    getTransferExportConfirmMessage() {
      return [
        "确认导出调拨处理中数据？",
        "本次导出包含当前查询结果，当前组织：" + this.displayValue(this.currentShopName),
        "筛选单号：" + this.displayValue(this.queryParams.orderNo || "全部"),
        "请确认导出文件只发送给有权限查看调拨数据的人员。"
      ].join("\n")
    },
    getShipmentConfirmMessage(items, shipmentDetail = this.shipmentDetail) {
      const totalQuantity = (items || []).reduce((sum, item) => sum + this.toNumber(item.deliverQuantity), 0)
      const crossStore = shipmentDetail.transferType === "cross_store"
      const storeReturn = shipmentDetail.transferType === "store_return"
      return [
        "确认提交调拨发货？",
        "调拨单号：" + this.displayValue(shipmentDetail.orderNo),
        (crossStore ? "调入门店：" : (storeReturn ? "目标仓库：" : "要货门店：")) + this.displayValue(shipmentDetail.toDeptName),
        (crossStore ? "调出门店：" : (storeReturn ? "返仓门店：" : "发货仓库：")) + this.displayValue(shipmentDetail.fromDeptName),
        "本次发货品项：" + (items || []).length + " 个",
        "本次数量：" + this.formatQuantity(totalQuantity) + " 件",
        "提交后会减少" + (crossStore || storeReturn ? "调出门店" : "发货仓库") + "库存，并生成待目标组织确认收货的发货批次。"
      ].join("\n")
    },
    getReceiptConfirmMessage(items, receiptDetail = this.receiptDetail, receiptForm = this.receiptForm) {
      const acceptedQuantity = (items || []).reduce((sum, item) => sum + this.toNumber(item.receiveQuantity), 0)
      const rejectedQuantity = (items || []).reduce((sum, item) => sum + this.toNumber(item.rejectedQuantity), 0)
      const damagedQuantity = (items || []).reduce((sum, item) => sum + this.toNumber(item.damagedQuantity), 0)
      return [
        "确认提交本批验收结果？",
        "调拨单号：" + this.displayValue(receiptDetail.orderNo),
        "发货批次：" + this.displayValue(receiptForm.shipmentNo),
        "目标组织：" + this.displayValue(receiptDetail.toDeptName),
        "来源组织：" + this.displayValue(receiptDetail.fromDeptName),
        "验收入库：" + this.formatQuantity(acceptedQuantity) + "；拒收：" + this.formatQuantity(rejectedQuantity) + "；残损：" + this.formatQuantity(damagedQuantity),
        "仅验收入库数量增加库存，其余数量会生成差异单。"
      ].join("\n")
    },
    handleTransferActionError(error, actionName) {
      const message = this.resolveActionErrorMessage(error)
      this.$modal.msgError(actionName + (message ? "：" + message : ""))
    },
    resolveActionErrorMessage(error) {
      if (!error) return ""
      if (typeof error === "string") return error
      if (error.message) return error.message
      if (error.msg) return error.msg
      return ""
    },
    isCancelError(error) {
      return error === "cancel" || error === "close" || (error && (error.message === "cancel" || error.message === "close"))
    },
    remainingToShip(item) {
      return Math.max(this.toNumber(item.quantity) - this.toNumber(item.deliveredQuantity), 0)
    },
    flowSteps(row) {
      const status = row && row.status
      const crossStore = row && row.transferType === "cross_store"
      if (crossStore) {
        const sourceStatus = String(row.sourceConfirmStatus || "").toUpperCase()
        const sourceConfirmed = ["CONFIRMED", "PARTIAL"].includes(sourceStatus)
        const activeByStatus = {
          draft: 0,
          submitted: 1,
          approved: 2,
          reserved: 2,
          partial_delivered: 3,
          delivered: 4,
          partial_received: 4,
          received: 5
        }
        let activeIndex = activeByStatus[status] === undefined ? 0 : activeByStatus[status]
        if (status === "approved" && sourceConfirmed) activeIndex = 3
        const steps = [
          { key: "request", title: "调入店发起", sub: "选择有权限的调出店和所需物料", index: 1 },
          { key: "approve", title: "调入侧审批", sub: "审批通过后通知调出店", index: 2 },
          { key: "source-confirm", title: "调出店确认", sub: "逐行确认可调数量，余量退回重选", index: 3 },
          { key: "ship", title: "调出店发货", sub: "只能按已确认数量发货", index: 4 },
          { key: "receive", title: "调入店收货", sub: "按发货批次验收", index: 5 },
          { key: "archive", title: "完成归档", sub: "全部收货后进入记录", index: 6 }
        ]
        return steps.map((step, index) => Object.assign({}, step, {
          className: index < activeIndex ? "done" : index === activeIndex ? "current" : ""
        }))
      }
      const order = ["draft", "submitted", "approved", "delivered", "received"]
      const activeIndex = Math.max(order.indexOf(status), status === "partial_delivered" ? 2 : status === "partial_received" ? 3 : 0)
      const steps = [
        { key: "request", title: "提交调拨申请", sub: "门店发起要货", index: 1 },
        { key: "approve", title: "上级审批", sub: "审批人通过或驳回", index: 2 },
        { key: "ship", title: "仓库发货", sub: "仓库管理员按剩余数量发货", index: 3 },
        { key: "receive", title: "门店确认收货", sub: "每次发货对应一次收货", index: 4 },
        { key: "archive", title: "完成并进入记录", sub: "全部发货且全部收货后归档", index: 5 }
      ]
      return steps.map((step, index) => Object.assign({}, step, {
        className: index < activeIndex ? "done" : index === activeIndex ? "current" : ""
      }))
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
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 { margin-bottom: 12px; }
.mt10 { margin-top: 10px; }
.context-alert { border-radius: 8px; }
.text-success { color: #67C23A; }
.text-warning { color: #E6A23C; }
.text-danger { color: #F56C6C; }
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.transfer-amount-summary {
  margin-top: 10px;
  text-align: right;
  font-weight: 600;
  color: #303133;
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

.transfer-approval-review {
  margin-bottom: 18px;
}

.transfer-approval-review__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}

.transfer-approval-review__heading strong {
  color: #303133;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.approval-summary {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
  text-align: left;
  overflow-wrap: anywhere;
}

.approval-summary-candidates {
  margin-top: 2px;
  color: var(--erp-info, #2866b1);
  cursor: help;
}

.availability-muted,
.product-picker-tip {
  color: #909399;
  font-size: 12px;
}

.product-picker-tip {
  line-height: 1.5;
  padding-left: 86px;
}

.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

::v-deep .stock-picker-dialog {
  max-width: calc(100vw - 48px);
}

::v-deep .transfer-request-dialog {
  max-width: calc(100vw - 48px);
}

::v-deep .transfer-detail-dialog {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 32px);
  margin-top: 16px !important;
}

::v-deep .transfer-detail-dialog .el-dialog__header,
::v-deep .transfer-detail-dialog .el-dialog__footer {
  flex: 0 0 auto;
}

::v-deep .transfer-detail-dialog .el-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}

.transfer-detail-body {
  min-height: 180px;
}

::v-deep .transfer-readonly-input .el-input__inner {
  color: #606266;
  -webkit-text-fill-color: #606266;
  background-color: #f8fafc;
  border-color: #dcdfe6;
}

::v-deep .transfer-code-column .cell {
  padding-left: 8px;
  padding-right: 8px;
}

.transfer-dashboard {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.transfer-metric {
  .metric-label {
    color: #606266;
    font-size: 12px;
    margin-bottom: 8px;
  }

  .metric-value {
    color: #303133;
    font-size: 24px;
    font-weight: 700;
    line-height: 1;
  }

  .metric-extra {
    color: #909399;
    font-size: 12px;
    margin-top: 8px;
  }
}

.success-value { color: #67C23A !important; }
.warning-value { color: #E6A23C !important; }
.danger-value { color: #F56C6C !important; }

.processing-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.panel-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.transfer-org-path {
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
}

.receipt-quantity-display {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;

  strong {
    color: #303133;
    font-weight: 600;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.flow-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}

.flow-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fafafa;

  &.done {
    border-color: #b3e19d;
    background: #f0f9eb;
  }

  &.current {
    border-color: #f3d19e;
    background: #fdf6ec;
  }
}

.flow-dot {
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #dcdfe6;
  color: #606266;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.flow-step.done .flow-dot {
  background: #67C23A;
  color: #fff;
}

.flow-step.current .flow-dot {
  background: #E6A23C;
  color: #fff;
}

.flow-title {
  color: #303133;
  font-weight: 600;
}

.flow-sub {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.compact-row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

::v-deep .dropdown-danger { color: #f56c6c; }

@media (max-width: 1200px) {
  .transfer-dashboard,
  .flow-steps {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
}

</style>
