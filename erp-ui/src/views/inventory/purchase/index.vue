<template>
  <div class="app-container warehouse-page">
    <inventory-page-hero
      title="采购管理"
      eyebrow="入库作业"
      description="从采购下单到收货质检，全程跟踪入库进度、到货批次与供应商履约。"
      scope-text="按当前仓库入库"
      icon="el-icon-s-order"
      tone="amber"
      :features="['采购下单', '收货登记', '质量检验']"
    />
    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="单号">
          <el-input v-model="queryParams.orderNo" placeholder="采购单号" clearable @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryParams.orderTitle" placeholder="采购标题" clearable @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter.native="handleQuery"/>
        </el-form-item>
        <el-form-item label="采购日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option label="草稿" value="draft"/>
            <el-option label="已提交" value="submitted"/>
            <el-option label="已收货" value="received"/>
            <el-option label="已取消" value="cancelled"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:purchase:list']" type="primary" size="mini" icon="el-icon-search" @click="handleQuery">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-if="isWarehouseContext" v-hasPermi="['inv:purchase:add']" size="mini" icon="el-icon-plus" @click="openForm(null)">新建采购单</el-button>
          <el-button v-hasPermi="['inv:purchase:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="purchaseContextAlert"
      :title="purchaseContextAlert.title"
      :description="purchaseContextAlert.description"
      :type="purchaseContextAlert.type"
      show-icon
      :closable="false"
      class="context-alert mb12"
    />

    <el-card shadow="never">
      <div v-if="listError" class="list-load-error mb12" role="alert">
        <span><i class="el-icon-warning-outline"/> {{ listError }}</span>
        <el-button type="text" :disabled="loading" @click="getList">重新加载</el-button>
      </div>
      <el-table v-loading="loading" :data="list" :empty-text="purchaseEmptyText">
        <el-table-column label="采购单号" prop="orderNo" width="180"/>
        <el-table-column label="标题" prop="orderTitle" min-width="160"/>
        <el-table-column label="供应商" prop="supplierName" width="140"/>
        <el-table-column label="总金额（元）" prop="totalAmount" width="130" align="right">
          <template slot-scope="scope">{{ formatCurrency(scope.row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="业务阶段" prop="businessStage" width="120">
          <template slot-scope="scope">
            <el-tag :type="purchaseStageTone(scope.row)" size="mini">{{ purchaseStageLabel(scope.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180"/>
        <el-table-column label="操作" width="190" fixed="right">
          <template slot-scope="scope">
            <div class="compact-row-actions">
              <el-button
                v-hasPermi="[purchasePrimaryAction(scope.row).permission]"
                type="primary"
                plain
                size="mini"
                @click="runPurchaseAction(purchasePrimaryAction(scope.row).key, scope.row)"
              >{{ purchasePrimaryAction(scope.row).label }}</el-button>
              <el-dropdown
                v-if="purchaseMoreActions(scope.row).length"
                trigger="click"
                @command="handlePurchaseMoreCommand($event, scope.row)"
              >
                <el-button size="mini">更多<i class="el-icon-arrow-down el-icon--right"/></el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item
                    v-for="action in purchaseMoreActions(scope.row)"
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

    <el-dialog title="采购单详情" :visible.sync="detailOpen" width="920px" append-to-body :close-on-click-modal="false">
      <div v-loading="detailLoading" class="order-detail">
        <el-descriptions :column="2" border size="small" class="mb12">
          <el-descriptions-item label="单号">{{ detailOrder.orderNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detailOrder.orderTitle || "-" }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detailOrder.supplierName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatCurrency(detailOrder.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailOrder.status)" size="mini">{{ statusLabel(detailOrder.status) || "-" }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="质检状态">
            <el-tag :type="qcStatusType(detailOrder.qcStatus)" size="mini">{{ qcStatusLabel(detailOrder.qcStatus) || "-" }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">采购明细</el-divider>
        <el-table :data="detailOrder.details" border size="small" empty-text="暂无明细">
          <el-table-column label="商品" min-width="180">
            <template slot-scope="scope">
              <div class="product-name">{{ scope.row.productName || "-" }}</div>
              <div class="product-meta">{{ scope.row.productCode || scope.row.sku || "-" }}</div>
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
          <el-table-column label="已收" width="100" align="right">
            <template slot-scope="scope">{{ formatAmount(scope.row.receivedQuantity) }}</template>
          </el-table-column>
          <el-table-column label="未收" width="100" align="right">
            <template slot-scope="scope">{{ formatAmount(Math.max(toNumber(scope.row.quantity) - toNumber(scope.row.receivedQuantity), 0)) }}</template>
          </el-table-column>
          <el-table-column label="进价" width="110" align="right">
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
      :title="form.orderId ? '编辑采购单' : '新建采购单'"
      :visible.sync="open"
      width="980px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!formSubmitting"
      :show-close="!formSubmitting"
      :before-close="handleFormBeforeClose"
      top="3vh"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标题" prop="orderTitle">
              <el-input v-model="form.orderTitle" maxlength="120"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购日期" prop="orderDate">
              <el-date-picker v-model="form.orderDate" type="date" placeholder="选择日期" value-format="yyyy-MM-dd" style="width:100%"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select
                v-model="form.supplierId"
                filterable
                remote
                reserve-keyword
                clearable
                :remote-method="loadSuppliers"
                :loading="supplierLoading"
                :disabled="form.details.length > 0 && !!form.supplierId"
                placeholder="输入名称或编码搜索合作中供应商"
                style="width:100%"
                @change="onSupplierChange"
              >
                <el-option
                  v-for="item in supplierOptions"
                  :key="item.supplierId"
                  :label="supplierLabel(item)"
                  :value="item.supplierId"
                />
              </el-select>
              <div v-if="form.details.length > 0 && form.supplierId" class="form-tip">如需更换供应商，请先清空采购明细</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总金额">
              <el-input :value="formTotalAmount" disabled>
                <template slot="append">元</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500"/>
        </el-form-item>
        <el-divider content-position="left">采购明细</el-divider>
        <div class="detail-toolbar">
          <el-tooltip :disabled="!!form.supplierId" content="请先选择供应商" placement="top">
            <span class="action-tooltip-wrap">
              <el-button type="primary" size="mini" icon="el-icon-plus" plain :disabled="!form.supplierId" @click="openProductSelector">选择物料</el-button>
            </span>
          </el-tooltip>
          <el-button size="mini" icon="el-icon-delete" :disabled="!form.details.length" @click="clearDetails">清空明细</el-button>
        </div>
        <el-table :data="form.details" border size="small" empty-text="请先选择物料">
          <el-table-column label="物料" min-width="210">
            <template slot-scope="scope">
              <div class="product-name">
                <el-tag size="mini" effect="plain">{{ itemTypeLabel(scope.row.itemType || "product") }}</el-tag>
                {{ scope.row.itemName || scope.row.productName || "-" }}
              </div>
              <div class="product-meta">{{ scope.row.itemCode || scope.row.productCode || scope.row.sku || "-" }}</div>
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="140">
            <template slot-scope="scope">{{ scope.row.supplierName || form.supplierName || "-" }}</template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template slot-scope="scope">{{ scope.row.spec || "-" }}</template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template slot-scope="scope">{{ scope.row.unit || "-" }}</template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template slot-scope="scope">
              <el-input-number v-model="scope.row.quantity" :min="1" :precision="2" controls-position="right" size="small" style="width:120px"/>
            </template>
          </el-table-column>
          <el-table-column label="进价" width="160">
            <template slot-scope="scope">
              <el-input-number v-model="scope.row.unitPrice" :min="0" :precision="2" controls-position="right" size="small" style="width:130px"/>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template slot-scope="scope">{{ lineAmount(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template slot-scope="scope">
              <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C" @click="removeDetail(scope.$index)"/>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <div slot="footer">
        <el-button :disabled="formSubmitting" @click="closeForm">取消</el-button>
        <el-button v-hasPermi="['inv:purchase:add']" type="primary" :loading="formSubmitting" :disabled="formSubmitting" @click="doSave(false)">保存草稿</el-button>
        <el-button v-hasPermi="['inv:purchase:submit']" type="success" :loading="formSubmitting" :disabled="formSubmitting" @click="doSave(true)">保存并提交</el-button>
      </div>
    </el-dialog>

    <el-dialog title="选择采购物料" :visible.sync="productDialogOpen" width="920px" append-to-body :close-on-click-modal="false">
      <el-form :model="productQuery" inline size="small" class="product-query">
        <el-form-item label="物料类型" required>
          <el-radio-group v-model="selectedItemType" size="small" @change="handleItemTypeChange">
            <el-radio-button v-for="type in allowedItemTypes" :key="type" :label="type">{{ itemTypeLabel(type) }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="productQuery.productName" :disabled="!selectedItemType" placeholder="物料名称/编码" clearable @keyup.enter.native="searchProducts"/>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="productQuery.supplierName" disabled placeholder="当前单据供应商"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" :disabled="!selectedItemType" @click="searchProducts">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetProductQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table ref="productTable" v-loading="productLoading" :data="productList" border size="small" height="360" :empty-text="selectedItemType ? '暂无可选物料' : '请先选择物料类型'" @selection-change="handleProductSelectionChange">
        <el-table-column type="selection" width="45"/>
        <el-table-column label="物料名称" prop="itemName" min-width="170"/>
        <el-table-column label="物料编码" prop="itemCode" width="130"/>
        <el-table-column label="规格" prop="spec" width="110"/>
        <el-table-column label="单位" prop="unit" width="70"/>
        <el-table-column label="供应商" prop="supplierName" min-width="140"/>
        <el-table-column label="采购参考价" prop="purchasePrice" width="110" align="right"/>
      </el-table>
      <pagination v-show="productTotal>0" :total="productTotal" :page.sync="productQuery.pageNum" :limit.sync="productQuery.pageSize" @pagination="getProductList"/>
      <div slot="footer">
        <el-button @click="productDialogOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedItemType || !productSelection.length" @click="confirmProductSelection">添加到明细</el-button>
      </div>
    </el-dialog>

    <el-dialog title="采购收货" :visible.sync="receiveOpen" width="920px" append-to-body :close-on-click-modal="false">
      <el-form ref="receiveFormRef" :model="receiveForm" :rules="receiveRules" label-width="96px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="采购单号">
              <el-input v-model="receiveForm.orderNo" disabled/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseId" required>
              <warehouse-select
                v-model="receiveForm.warehouseId"
                :shop-dept-id="currentWarehouseId"
                purpose="currentWarehouse"
                :clearable="false"
                :disabled="true"
                placeholder="请选择收货仓库"
                width="100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="供应商批次" prop="supplierBatchNo">
              <el-input v-model.trim="receiveForm.supplierBatchNo" maxlength="100" show-word-limit clearable placeholder="选填，便于追溯"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送货单号" prop="deliveryNoteNo">
              <el-input v-model.trim="receiveForm.deliveryNoteNo" maxlength="100" show-word-limit clearable placeholder="选填"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="到货时间" prop="arrivedTime">
              <el-date-picker v-model="receiveForm.arrivedTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择实际到货时间" style="width:100%"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="收货备注" prop="remark">
          <el-input v-model.trim="receiveForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="可记录车牌、包装状态等收货信息"/>
        </el-form-item>
        <div class="receive-batch-actions">
          <span>本次收货数量默认为 0，请核对后录入。</span>
          <div>
            <el-button size="mini" type="primary" plain @click="fillReceiveQuantities">全部剩余</el-button>
            <el-button size="mini" @click="clearReceiveQuantities">清空</el-button>
          </div>
        </div>
        <el-table v-loading="receiveLoading" :data="receiveForm.details" border size="mini">
          <el-table-column label="商品名" prop="productName" min-width="180"/>
          <el-table-column label="采购数量" prop="quantity" width="110"/>
          <el-table-column label="已收数量" prop="receivedQuantity" width="110"/>
          <el-table-column label="未收数量" prop="remainingQuantity" width="110"/>
          <el-table-column label="本次收货数量" width="180">
            <template slot-scope="scope">
              <el-input-number
                v-model="scope.row.receiveQuantity"
                :min="0"
                :max="scope.row.remainingQuantity"
                :precision="2"
                :disabled="scope.row.remainingQuantity <= 0"
                size="mini"
                controls-position="right"
                style="width:150px"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <div slot="footer">
        <el-button :disabled="receiveSubmitting" @click="receiveOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:purchase:receive']" type="primary" :loading="receiveSubmitting" :disabled="receiveLoading || receiveSubmitting" @click="submitReceive">提交收货</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="采购质检"
      :visible.sync="qcOpen"
      width="1180px"
      top="5vh"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!qcSubmitting"
      :show-close="!qcSubmitting"
      :before-close="handleQcBeforeClose"
      @closed="handleQcDialogClosed"
    >
      <div v-if="qcLoading" class="dialog-state-panel" role="status">
        <i class="el-icon-loading"/> 正在加载待检收货批次…
      </div>
      <div v-else-if="qcLoadError" class="dialog-state-panel dialog-state-error" role="alert">
        <span><i class="el-icon-warning-outline"/> {{ qcLoadError }}</span>
        <el-button type="primary" plain size="small" :disabled="qcLoading" @click="retryQualityCheckLoad">重试</el-button>
      </div>
      <div v-else>
        <el-alert
          v-if="!qcLegacyMode"
          type="info"
          :closable="false"
          show-icon
          title="逐行填写合格、拒收和让步数量，三者之和必须等于本次检验数量。合格和让步数量才会增加库存。"
          class="mb12"
        />
        <el-form ref="qcFormRef" :model="qcForm" :rules="qcRules" label-width="96px">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="采购单号">
                <el-input v-model="qcForm.orderNo" disabled/>
              </el-form-item>
            </el-col>
            <el-col v-if="!qcLegacyMode" :span="16">
              <el-form-item label="收货批次">
                <el-select v-model="qcForm.receiptBatchId" style="width:100%" @change="handleQcBatchChange">
                  <el-option
                    v-for="batch in qcBatches"
                    :key="batch.batchId"
                    :label="formatReceiptBatchLabel(batch)"
                    :value="batch.batchId"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <template v-if="qcLegacyMode">
            <el-alert type="warning" :closable="false" show-icon title="该单据是历史待检数据，将按整单质检方式处理。" class="mb12"/>
            <el-form-item label="质检结果" prop="qcResult">
              <el-radio-group v-model="qcForm.qcResult">
                <el-radio label="passed">合格</el-radio>
                <el-radio label="concession">让步接收</el-radio>
                <el-radio label="rejected">拒收</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="质检备注" prop="qcRemark">
              <el-input v-model="qcForm.qcRemark" type="textarea" :rows="3" maxlength="500" show-word-limit/>
            </el-form-item>
          </template>
        </el-form>

        <template v-if="!qcLegacyMode">
          <div class="receive-batch-actions">
            <span>待检明细 {{ qcForm.items.length }} 条</span>
            <div>
              <el-button size="mini" type="success" plain @click="fillAllQcPassed">全部合格</el-button>
              <el-button size="mini" @click="clearQcClassifications">清空分类数量</el-button>
            </div>
          </div>
          <el-table :data="qcForm.items" border size="mini" max-height="520">
            <el-table-column label="物料" min-width="160" fixed="left">
              <template slot-scope="scope">
                <div class="product-name">{{ scope.row.itemName || '-' }}</div>
                <div class="product-meta">{{ scope.row.itemCode || '-' }} / {{ scope.row.unit || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="待检" prop="pendingQuantity" width="82" align="right"/>
            <el-table-column label="本次检验" width="130">
              <template slot-scope="scope">
                <el-input-number v-model="scope.row.inspectedQuantity" :min="0" :max="scope.row.pendingQuantity" :precision="2" :controls="false" size="mini" style="width:105px"/>
              </template>
            </el-table-column>
            <el-table-column label="合格" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.acceptedQuantity" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="拒收" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.rejectedQuantity" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="让步" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.concessionQuantity" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="缺陷等级" width="118">
              <template slot-scope="scope">
                <el-select v-model="scope.row.defectLevel" clearable size="mini" placeholder="选填">
                  <el-option label="轻微" value="minor"/><el-option label="一般" value="major"/><el-option label="严重" value="critical"/>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="拒收/让步原因" min-width="190">
              <template slot-scope="scope"><el-input v-model="scope.row.defectReason" maxlength="500" size="mini" clearable placeholder="有拒收或让步数量时必填"/></template>
            </el-table-column>
            <el-table-column label="附件" min-width="180">
              <template slot-scope="scope">
                <file-upload v-model="scope.row.attachmentUrls" :limit="3" :file-size="10" :is-show-tip="false" :drag="false" :file-type="['jpg', 'jpeg', 'png', 'pdf']"/>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <div slot="footer">
        <el-button :disabled="qcSubmitting" @click="closeQualityCheck">取消</el-button>
        <el-button v-hasPermi="['inv:purchase:qc']" type="primary" :loading="qcSubmitting" :disabled="qcLoading || qcSubmitting || !!qcLoadError || !qcForm.orderId" @click="submitQualityCheck">提交质检</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listPurchase, getPurchaseDetail, savePurchase, submitPurchase, receivePurchase, qualityCheckPurchase, listPendingReceiptBatches, qualityCheckPurchaseBatch, cancelPurchase, deleteDraftPurchase } from "@/api/inventory/purchase"
import { listProduct } from "@/api/inventory/product"
import { listOe } from "@/api/inventory/oe"
import { listGift } from "@/api/inventory/gift"
import { listSupplier } from "@/api/inventory/supplier"
import WarehouseSelect from "@/views/inventory/components/WarehouseSelect"
import { parseTime } from "@/utils/common"
import { getSelectedDeptId, getSelectedDeptName, isSelectedWarehouse } from "@/utils/shopContext"
import { canReceivePurchase, canQualityCheckPurchase, canCancelPurchase } from "./purchaseActionRules"
import { purchaseBusinessStageLabel, purchaseBusinessStageTone } from "@/utils/purchaseBusinessStage"
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")
export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "purchase",
    loadFocusedRow(orderId) { return getPurchaseDetail(orderId) },
    actions: {
      qualityCheckPurchase(row) {
        if (!this.canQualityCheck(row)) return this.showTodoBusinessHandled()
        this.openQualityCheck(row)
      },
      receivePurchaseAll(row) {
        if (!this.canReceive(row)) return this.showTodoBusinessHandled()
        this.doReceive(row)
      }
    }
  })],
  name: "InvPurchase",
  components: { WarehouseSelect },
  data() {
    return {
      loading: false, total: 0, list: [], listError: "", listRequestSequence: 0, activeListQuerySnapshot: "",
      open: false, formSubmitting: false, detailOpen: false, detailLoading: false, receiveOpen: false, receiveLoading: false,
      receiveSubmitting: false, receiveRequestSequence: 0,
      qcOpen: false, qcLoading: false, qcSubmitting: false, qcLegacyMode: false, qcBatches: [],
      qcRequestSequence: 0, qcLoadError: "", qcTargetOrderId: undefined,
      productDialogOpen: false, productLoading: false, productList: [], productTotal: 0, productSelection: [],
      supplierLoading: false, supplierOptions: [],
      allowedItemTypes: ["product", "oe", "gift"],
      selectedItemType: null,
      dateRange: [],
      queryParams: { pageNum: 1, pageSize: 10, orderNo: undefined, orderTitle: undefined, supplierName: undefined, status: undefined },
      form: { orderId: undefined, orderTitle: "", supplierId: undefined, supplierName: "", orderDate: null, remark: "", totalAmount: 0, details: [] },
      detailOrder: { details: [] },
      productQuery: { pageNum: 1, pageSize: 10, productName: undefined, supplierName: undefined, status: "0" },
      receiveForm: { orderId: undefined, orderNo: "", shopDeptId: undefined, warehouseId: undefined, supplierBatchNo: "", deliveryNoteNo: "", arrivedTime: "", remark: "", details: [] },
      qcForm: { orderId: undefined, orderNo: "", receiptBatchId: undefined, items: [], qcResult: "", qcRemark: "" },
      rules: {
        orderTitle: [{ required: true, message: "请输入标题", trigger: "blur" }],
        supplierId: [{ required: true, message: "请选择合作中的供应商档案", trigger: "change" }],
        orderDate: [{ required: true, message: "请选择采购日期", trigger: "change" }]
      },
      receiveRules: {
        supplierBatchNo: [{ max: 100, message: "供应商批次不能超过 100 个字符", trigger: "blur" }],
        deliveryNoteNo: [{ max: 100, message: "送货单号不能超过 100 个字符", trigger: "blur" }],
        arrivedTime: [{ required: true, message: "请选择实际到货时间", trigger: "change" }],
        remark: [{ max: 500, message: "收货备注不能超过 500 个字符", trigger: "blur" }]
      },
      qcRules: {
        qcResult: [{ required: true, message: "请选择质检结果", trigger: "change" }]
      }
    }
  },
  computed: {
    currentWarehouseId() {
      return getSelectedDeptId()
    },
    currentWarehouseName() {
      return getSelectedDeptName() || "当前仓库"
    },
    isWarehouseContext() {
      return isSelectedWarehouse()
    },
    purchaseContextAlert() {
      if (!this.isWarehouseContext) {
        return {
          type: "warning",
          title: "当前组织不能操作采购",
          description: "采购单只能在仓库上下文中创建、收货和质检。请切换到仓库后再操作。"
        }
      }
      return {
        type: "info",
        title: "当前采购仓库：" + this.currentWarehouseName,
        description: "采购单、收货和质检都会进入当前仓库库存。"
      }
    },
    purchaseEmptyText() {
      if (!this.isWarehouseContext) {
        return "采购单只能在仓库上下文中创建、收货和质检。请切换到仓库后再查看。"
      }
      return "当前仓库暂无采购单。可新建采购单，或调整筛选条件。"
    },
    formTotalAmount() {
      return this.form.details.reduce((sum, item) => sum + this.toNumber(item.quantity) * this.toNumber(item.unitPrice), 0).toFixed(2)
    }
  },
  created() { this.getList() },
  methods: {
    statusType(s) { const m = { draft: 'info', submitted: 'warning', received: 'success', cancelled: 'danger' }; return m[s] || 'info' },
    statusLabel(s) { const m = { draft: '草稿', submitted: '已提交', received: '已收货', cancelled: '已取消' }; return m[s] || '未知采购状态' },
    itemTypeLabel(type) { return { product: "商品", oe: "OE 器皿", gift: "礼盒" }[type] || '其他物料' },
    qcStatusType(s) { const m = { pending: 'warning', passed: 'success', pass: 'success', concession: 'success', rejected: 'danger', reject: 'danger' }; return m[s] || 'info' },
    qcStatusLabel(s) { const m = { pending: '待质检', passed: '合格', pass: '合格', concession: '让步接收', rejected: '拒收', reject: '拒收' }; return m[s] || '未质检' },
    purchaseStageLabel(row) { return purchaseBusinessStageLabel(row) },
    purchaseStageTone(row) { return purchaseBusinessStageTone(row) },
    canReceive(row) { return this.isWarehouseContext && canReceivePurchase(row) },
    canQualityCheck(row) { return this.isWarehouseContext && canQualityCheckPurchase(row) },
    canCancel(row) { return this.isWarehouseContext && canCancelPurchase(row) },
    purchasePrimaryAction(row) {
      if (this.canQualityCheck(row)) return { key: "qc", label: "质检", permission: "inv:purchase:qc" }
      if (this.canReceive(row)) return { key: "receive", label: "收货", permission: "inv:purchase:receive" }
      if (this.isWarehouseContext && row && row.status === "draft") {
        return { key: "submit", label: "提交", permission: "inv:purchase:submit" }
      }
      return { key: "detail", label: "详情", permission: "inv:purchase:query" }
    },
    purchaseMoreActions(row) {
      const primaryKey = this.purchasePrimaryAction(row).key
      const actions = []
      const add = (key, label, permission, danger) => {
        if (key !== primaryKey) actions.push({ key, label, permission, danger: Boolean(danger) })
      }
      add("detail", "详情", "inv:purchase:query")
      if (this.isWarehouseContext && row && row.status === "draft") {
        add("edit", "编辑", "inv:purchase:add")
        add("submit", "提交", "inv:purchase:submit")
        add("delete", "删除草稿", "inv:purchase:remove", true)
      }
      if (this.canReceive(row)) add("receive", "收货", "inv:purchase:receive")
      if (this.canQualityCheck(row)) add("qc", "质检", "inv:purchase:qc")
      if (this.canCancel(row)) add("cancel", "取消采购单", "inv:purchase:remove", true)
      return actions
    },
    handlePurchaseMoreCommand(command, row) {
      this.runPurchaseAction(command, row)
    },
    runPurchaseAction(action, row) {
      const handlers = {
        detail: () => this.showDetail(row.orderId),
        edit: () => this.openForm(row),
        submit: () => this.doSubmit(row),
        receive: () => this.doReceive(row),
        qc: () => this.openQualityCheck(row),
        delete: () => this.doDeleteDraft(row),
        cancel: () => this.doCancel(row)
      }
      if (handlers[action]) handlers[action]()
    },
    ensureWarehouseContext() {
      if (this.isWarehouseContext) {
        return true
      }
      this.$modal.msgError("请选择仓库后再操作采购")
      return false
    },
    toNumber(value) {
      const num = Number(value)
      return isNaN(num) ? 0 : num
    },
    formatAmount(value) {
      return this.toNumber(value).toFixed(2)
    },
    formatCurrency(value) {
      return "¥" + this.formatAmount(value)
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
      this.queryParams = { pageNum: 1, pageSize: 10, orderNo: undefined, orderTitle: undefined, supplierName: undefined, status: undefined }
      return this.getList()
    },
    getList() {
      if (!this.isWarehouseContext) {
        this.listRequestSequence += 1
        this.activeListQuerySnapshot = ""
        this.loading = false
        this.listError = ""
        this.list = []
        this.total = 0
        return this.handleTodoFocusRows(this.list)
      }
      const query = this.buildQuery()
      const querySnapshot = JSON.stringify(query)
      const requestSequence = ++this.listRequestSequence
      this.activeListQuerySnapshot = querySnapshot
      this.loading = true
      this.listError = ""
      this.list = []
      this.total = 0
      return this.loadTodoBusinessList(() => listPurchase(query)).then(res => {
        if (!this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true }
        this.list = res.rows || []
        this.total = res.total || 0
        return Promise.resolve(this.handleTodoFocusRows(this.list)).then(() => res)
      }).catch(error => {
        if (!this.isCurrentListRequest(requestSequence, querySnapshot)) return { discarded: true, error }
        this.list = []
        this.total = 0
        this.listError = "采购单列表加载失败，请重试。"
        return { failed: true, error }
      }).finally(() => {
        if (this.isCurrentListRequest(requestSequence, querySnapshot)) this.loading = false
      })
    },
    isCurrentListRequest(requestSequence, querySnapshot) {
      return requestSequence === this.listRequestSequence && querySnapshot === this.activeListQuerySnapshot
    },
    getProductList() {
      if (!this.selectedItemType) {
        this.productList = []
        this.productTotal = 0
        return
      }
      this.productLoading = true
      this.listCatalogItems().then(res => {
        this.productList = (res.rows || []).filter(p => p.status === "0").map(item => this.normalizeCatalogItem(item))
        this.productTotal = res.total || 0
      }).finally(() => { this.productLoading = false })
    },
    listCatalogItems() {
      const keyword = this.normalizeText(this.productQuery.productName)
      const base = { pageNum: this.productQuery.pageNum, pageSize: this.productQuery.pageSize, status: "0" }
      if (this.selectedItemType === "oe") {
        return listOe(Object.assign(base, keyword ? { keyword } : {}, this.productQuery.supplierName ? { supplierName: this.productQuery.supplierName } : {}))
      }
      if (this.selectedItemType === "gift") {
        return listGift(Object.assign(base, keyword ? { keyword } : {}))
      }
      return listProduct(Object.assign(base, keyword ? { keyword } : {}, this.productQuery.supplierName ? { supplierName: this.productQuery.supplierName } : {}))
    },
    normalizeCatalogItem(raw) {
      const type = this.selectedItemType || "product"
      if (type === "oe") {
        return Object.assign({}, raw, { itemType: type, itemId: raw.oeItemId, itemCode: raw.oeItemCode, itemName: raw.oeItemName, productId: null, productCode: raw.oeItemCode, productName: raw.oeItemName, spec: raw.itemDescription || "", unit: raw.orderUnit || "", purchasePrice: raw.costPrice || 0 })
      }
      if (type === "gift") {
        return Object.assign({}, raw, { itemType: type, itemId: raw.giftId, itemCode: raw.giftCode, itemName: raw.giftName, productId: null, productCode: raw.giftCode, productName: raw.giftName, unit: raw.replenishmentUnit || "", supplierName: "礼盒", purchasePrice: 0 })
      }
      return Object.assign({}, raw, { itemType: type, itemId: raw.productId, itemCode: raw.productCode, itemName: raw.productName })
    },
    searchProducts() {
      this.productQuery.pageNum = 1
      this.getProductList()
    },
    resetProductQuery() {
      this.productQuery = { pageNum: 1, pageSize: 10, productName: undefined, supplierName: this.form.supplierName || undefined, status: "0" }
      if (this.selectedItemType) this.getProductList()
    },
    handleItemTypeChange() {
      this.productSelection = []
      this.resetProductQuery()
      this.$nextTick(() => {
        if (this.$refs.productTable) this.$refs.productTable.clearSelection()
      })
    },
    openProductSelector() {
      if (!this.ensureWarehouseContext()) return
      if (!this.form.supplierId || !this.form.supplierName) {
        this.$modal.msgError("请先选择合作中的供应商档案")
        return
      }
      this.productDialogOpen = true
      this.selectedItemType = null
      this.productSelection = []
      this.productList = []
      this.productTotal = 0
      this.$nextTick(() => {
        if (this.$refs.productTable) this.$refs.productTable.clearSelection()
      })
    },
    handleProductSelectionChange(rows) {
      this.productSelection = rows || []
    },
    confirmProductSelection() {
      if (!this.productSelection.length) {
        this.$modal.msgError("请选择物料")
        return
      }
      const missingSupplierNames = []
      const mismatchedSupplierNames = []
      let changedCount = 0
      this.productSelection.forEach(product => {
        if (!this.normalizeText(product.supplierName)) {
          missingSupplierNames.push(product.itemName)
          return
        }
        if (product.itemType !== "gift" && this.normalizeText(product.supplierName) !== this.normalizeText(this.form.supplierName)) {
          mismatchedSupplierNames.push(product.itemName)
          return
        }
        const existing = this.form.details.find(item => item.itemType === product.itemType && String(item.itemId) === String(product.itemId))
        if (existing) {
          existing.quantity = this.toNumber(existing.quantity) + 1
          if (!this.hasValue(existing.unitPrice)) existing.unitPrice = product.purchasePrice || 0
          changedCount++
          return
        }
        this.form.details.push(this.buildDetailFromProduct(product))
        changedCount++
      })
      if (missingSupplierNames.length > 0) {
        this.$modal.msgError("以下物料未绑定供应商：" + missingSupplierNames.join("、"))
      }
      if (mismatchedSupplierNames.length > 0) {
        this.$modal.msgError("以下物料不属于当前供应商：" + mismatchedSupplierNames.join("、"))
      }
      if (changedCount > 0) {
        this.productDialogOpen = false
      }
    },
    buildDetailFromProduct(product) {
      return {
        itemType: product.itemType,
        itemId: product.itemId,
        itemCode: product.itemCode,
        itemName: product.itemName,
        productId: product.productId,
        productName: product.itemName,
        productCode: product.itemCode,
        supplierName: product.supplierName,
        sku: product.sku,
        spec: product.spec,
        unit: product.unit,
        quantity: 1,
        unitPrice: product.purchasePrice || 0,
        amount: product.purchasePrice || 0,
        receivedQuantity: 0
      }
    },
    openForm(row) {
      if (!this.ensureWarehouseContext()) return
      if (!row) {
        this.form = { orderId: undefined, orderTitle: "", supplierId: undefined, supplierName: "", orderDate: this.defaultOrderDate(), remark: "", totalAmount: 0, details: [] }
        this.supplierOptions = []
        this.open = true
        this.loadSuppliers()
        this.$nextTick(() => { if (this.$refs.formRef) this.$refs.formRef.clearValidate() })
        return
      }
      getPurchaseDetail(row.orderId).then(res => {
        const data = res.data || {}
        this.form = Object.assign({}, data, {
          orderDate: data.orderDate || this.defaultOrderDate(),
          details: (data.details || []).map(item => this.normalizeDetail(item))
        })
        this.supplierOptions = data.supplierId ? [{ supplierId: data.supplierId, supplierName: data.supplierName }] : []
        this.open = true
        this.loadSuppliers(data.supplierName)
        this.$nextTick(() => { if (this.$refs.formRef) this.$refs.formRef.clearValidate() })
      })
    },
    normalizeDetail(item) {
      return Object.assign({}, item, {
        itemType: item.itemType || "product",
        itemId: item.itemId || item.productId,
        itemCode: item.itemCode || item.productCode || item.sku || "",
        itemName: item.itemName || item.productName || "",
        productCode: item.productCode || item.itemCode || "",
        supplierName: item.supplierName || "",
        quantity: this.hasValue(item.quantity) ? item.quantity : 1,
        unitPrice: this.hasValue(item.unitPrice) ? item.unitPrice : 0,
        amount: this.hasValue(item.amount) ? item.amount : 0,
        receivedQuantity: this.hasValue(item.receivedQuantity) ? item.receivedQuantity : 0
      })
    },
    removeDetail(idx) {
      this.form.details.splice(idx, 1)
    },
    clearDetails() {
      this.form.details = []
    },
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
      if (!this.ensureWarehouseContext()) return Promise.resolve({ invalidContext: true })
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
      this.onSupplierChange(this.form.supplierId)
      if (!this.form.supplierName) {
        this.$modal.msgError("请选择合作中的供应商档案")
        return { invalid: true }
      }
      const invalidQuantity = this.form.details.find(d => this.toNumber(d.quantity) <= 0)
      if (invalidQuantity) {
        this.$modal.msgError("采购数量必须大于0")
        return { invalid: true }
      }
      const invalidPrice = this.form.details.find(d => this.toNumber(d.unitPrice) < 0)
      if (invalidPrice) {
        this.$modal.msgError("进价不能小于0")
        return { invalid: true }
      }
      const payload = this.buildPurchasePayload()
      const api = submitAfter ? submitPurchase : savePurchase
      return api(payload).then(() => {
        this.$modal.msgSuccess(submitAfter ? "提交成功" : "已保存草稿")
        this.open = false
        this.getList()
        return { success: true }
      })
    },
    buildPurchasePayload() {
      const details = this.form.details.map(item => {
        const quantity = this.toNumber(item.quantity)
        const unitPrice = this.toNumber(item.unitPrice)
        return {
          detailId: item.detailId,
          itemType: item.itemType || "product",
          itemId: item.itemId || item.productId,
          itemCode: item.itemCode || item.productCode || item.sku,
          itemName: item.itemName || item.productName,
          productId: item.productId,
          productName: item.productName,
          sku: item.sku,
          spec: item.spec,
          unit: item.unit,
          quantity: quantity,
          unitPrice: unitPrice,
          amount: quantity * unitPrice,
          receivedQuantity: item.receivedQuantity
        }
      })
      return Object.assign({}, this.form, {
        supplierName: this.form.supplierName,
        orderDate: this.form.orderDate || this.defaultOrderDate(),
        totalAmount: details.reduce((sum, item) => sum + item.amount, 0),
        details: details
      })
    },
    lineAmount(item) {
      return (this.toNumber(item.quantity) * this.toNumber(item.unitPrice)).toFixed(2)
    },
    defaultOrderDate() {
      return parseTime(new Date(), "{y}-{m}-{d}")
    },
    defaultReceiveTime() {
      return parseTime(new Date(), "{y}-{m}-{d} {h}:{i}:{s}")
    },
    supplierLabel(supplier) {
      if (!supplier) return ""
      return supplier.supplierName + (supplier.supplierCode ? "（" + supplier.supplierCode + "）" : "")
    },
    loadSuppliers(keyword) {
      if (!this.isWarehouseContext) {
        this.supplierOptions = []
        return Promise.resolve([])
      }
      this.supplierLoading = true
      return listSupplier({
        pageNum: 1,
        pageSize: 100,
        status: "0",
        cooperationStatus: "0",
        supplierName: keyword ? String(keyword).trim() : undefined
      }, { silentError: true }).then(res => {
        const selected = this.supplierOptions.find(item => String(item.supplierId) === String(this.form.supplierId))
        this.supplierOptions = res.rows || []
        if (selected && !this.supplierOptions.some(item => String(item.supplierId) === String(selected.supplierId))) {
          this.supplierOptions.unshift(selected)
        }
        return this.supplierOptions
      }).catch(() => {
        this.$modal.msgWarning("供应商选项加载失败，请检查供应商档案权限或稍后重试")
        return this.supplierOptions
      }).finally(() => { this.supplierLoading = false })
    },
    onSupplierChange(supplierId) {
      const supplier = this.supplierOptions.find(item => String(item.supplierId) === String(supplierId))
      this.form.supplierName = supplier ? supplier.supplierName : ""
      this.productQuery.supplierName = this.form.supplierName || undefined
    },
    normalizeText(value) {
      if (value === undefined || value === null) return ""
      return String(value).trim()
    },
    doSubmit(row) {
      if (!this.ensureWarehouseContext()) return
      getPurchaseDetail(row.orderId).then(res => {
        submitPurchase(res.data).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() })
      })
    },
    doReceive(row) {
      if (!this.ensureWarehouseContext()) return
      const requestSequence = ++this.receiveRequestSequence
      this.receiveOpen = true
      this.receiveLoading = true
      this.receiveForm = { orderId: row.orderId, orderNo: row.orderNo, shopDeptId: this.currentWarehouseId, warehouseId: this.currentWarehouseId, supplierBatchNo: "", deliveryNoteNo: "", arrivedTime: this.defaultReceiveTime(), remark: "", details: [] }
      getPurchaseDetail(row.orderId).then(res => {
        if (requestSequence !== this.receiveRequestSequence || !this.receiveOpen) return
        const order = res.data || {}
        const details = (order.details || []).map(item => {
          const quantity = this.toNumber(item.quantity)
          const receivedQuantity = this.toNumber(item.receivedQuantity)
          const remainingQuantity = Math.max(quantity - receivedQuantity, 0)
          return Object.assign({}, item, {
            quantity: quantity,
            receivedQuantity: receivedQuantity,
            remainingQuantity: remainingQuantity,
            receiveQuantity: 0
          })
        })
        this.receiveForm = Object.assign({}, this.receiveForm, {
          orderId: order.orderId || row.orderId,
          orderNo: order.orderNo || row.orderNo,
          shopDeptId: this.currentWarehouseId,
          warehouseId: this.currentWarehouseId,
          details: details
        })
        this.$nextTick(() => {
          if (this.$refs.receiveFormRef) this.$refs.receiveFormRef.clearValidate()
        })
      }).finally(() => {
        if (requestSequence === this.receiveRequestSequence) this.receiveLoading = false
      })
    },
    submitReceive() {
      if (this.receiveSubmitting) return
      if (!this.ensureWarehouseContext()) return
      this.receiveForm.warehouseId = this.currentWarehouseId
      const formRef = this.$refs.receiveFormRef
      if (formRef) {
        formRef.validate(valid => {
          if (valid) this.submitValidatedReceive()
        })
        return
      }
      this.submitValidatedReceive()
    },
    submitValidatedReceive() {
      if (!this.receiveForm.details || this.receiveForm.details.length === 0) {
        this.$modal.msgError("采购单无可收货明细")
        return
      }
      if (!this.hasValue(this.receiveForm.warehouseId)) {
        this.$modal.msgError("启用仓库模式后，采购收货必须选择仓库")
        return
      }
      const items = []
      for (let i = 0; i < this.receiveForm.details.length; i++) {
        const detail = this.receiveForm.details[i]
        const receiveQuantity = this.toNumber(detail.receiveQuantity)
        const remainingQuantity = this.toNumber(detail.remainingQuantity)
        if (receiveQuantity < 0) {
          this.$modal.msgError("本次收货数量不能小于 0")
          return
        }
        if (receiveQuantity > remainingQuantity) {
          this.$modal.msgError("商品 [" + (detail.productName || detail.detailId) + "] 本次收货数量不能超过未收数量")
          return
        }
        if (receiveQuantity > 0) {
          items.push({ detailId: detail.detailId, receiveQuantity: receiveQuantity })
        }
      }
      if (items.length === 0) {
        this.$modal.msgError("请至少录入一条大于 0 的本次收货数量")
        return
      }
      const data = {
        warehouseId: this.currentWarehouseId,
        supplierBatchNo: this.normalizeText(this.receiveForm.supplierBatchNo) || undefined,
        deliveryNoteNo: this.normalizeText(this.receiveForm.deliveryNoteNo) || undefined,
        arrivedTime: this.receiveForm.arrivedTime || this.defaultReceiveTime(),
        remark: this.normalizeText(this.receiveForm.remark) || undefined,
        items: items
      }
      this.$modal.confirm(this.getReceiveConfirmMessage(items)).then(() => {
        this.receiveSubmitting = true
        receivePurchase(this.receiveForm.orderId, data).then(() => {
          this.$modal.msgSuccess("收货成功")
          this.receiveOpen = false
          this.getList()
        }).finally(() => {
          this.receiveSubmitting = false
        })
      })
    },
    fillReceiveQuantities() {
      ;(this.receiveForm.details || []).forEach(item => {
        item.receiveQuantity = this.toNumber(item.remainingQuantity)
      })
    },
    clearReceiveQuantities() {
      ;(this.receiveForm.details || []).forEach(item => {
        item.receiveQuantity = 0
      })
    },
    openQualityCheck(row) {
      if (!this.ensureWarehouseContext()) return
      this.qcTargetOrderId = row.orderId
      this.qcForm = { orderId: row.orderId, orderNo: row.orderNo, receiptBatchId: undefined, items: [], qcResult: "", qcRemark: "" }
      this.qcBatches = []
      this.qcLegacyMode = false
      this.qcLoadError = ""
      this.qcOpen = true
      return this.loadQualityCheckBatches(row.orderId)
    },
    loadQualityCheckBatches(orderId) {
      const requestSequence = ++this.qcRequestSequence
      this.qcLoading = true
      this.qcLoadError = ""
      this.qcBatches = []
      this.qcLegacyMode = false
      this.qcForm = Object.assign({}, this.qcForm, {
        receiptBatchId: undefined,
        batchNo: undefined,
        items: []
      })
      return listPendingReceiptBatches(orderId).then(res => {
        if (!this.isCurrentQcRequest(requestSequence, orderId)) return { discarded: true }
        this.qcBatches = res.data || []
        this.qcLegacyMode = this.qcBatches.length === 0
        if (!this.qcLegacyMode) {
          this.applyQcBatch(this.qcBatches[0])
        }
        this.$nextTick(() => {
          if (this.$refs.qcFormRef) this.$refs.qcFormRef.clearValidate()
        })
        return res
      }).catch(error => {
        if (!this.isCurrentQcRequest(requestSequence, orderId)) return { discarded: true, error }
        this.qcBatches = []
        this.qcLegacyMode = false
        this.qcForm = Object.assign({}, this.qcForm, {
          receiptBatchId: undefined,
          batchNo: undefined,
          items: []
        })
        this.qcLoadError = "待检收货批次加载失败，请重试。"
        return { failed: true, error }
      }).finally(() => {
        if (this.isCurrentQcRequest(requestSequence, orderId)) this.qcLoading = false
      })
    },
    isCurrentQcRequest(requestSequence, orderId) {
      return this.qcOpen &&
        requestSequence === this.qcRequestSequence &&
        String(orderId) === String(this.qcTargetOrderId) &&
        String(orderId) === String(this.qcForm.orderId)
    },
    retryQualityCheckLoad() {
      if (!this.qcOpen || this.qcLoading || !this.qcTargetOrderId) return Promise.resolve({ blocked: true })
      return this.loadQualityCheckBatches(this.qcTargetOrderId)
    },
    invalidateQualityCheckLoad() {
      this.qcRequestSequence += 1
      this.qcLoading = false
    },
    closeQualityCheck() {
      if (this.qcSubmitting) return
      this.invalidateQualityCheckLoad()
      this.qcOpen = false
    },
    handleQcBeforeClose(done) {
      if (this.qcSubmitting) return
      this.invalidateQualityCheckLoad()
      done()
    },
    handleQcDialogClosed() {
      this.invalidateQualityCheckLoad()
      this.qcTargetOrderId = undefined
      this.qcLoadError = ""
    },
    handleQcBatchChange(batchId) {
      const batch = this.qcBatches.find(item => String(item.batchId) === String(batchId))
      if (batch) this.applyQcBatch(batch)
    },
    applyQcBatch(batch) {
      const items = (batch.details || []).filter(item => this.toNumber(item.pendingQuantity) > 0).map(item => {
        const pending = this.toNumber(item.pendingQuantity)
        return Object.assign({}, item, {
          pendingQuantity: pending,
          inspectedQuantity: pending,
          acceptedQuantity: pending,
          rejectedQuantity: 0,
          concessionQuantity: 0,
          defectLevel: "",
          defectReason: "",
          attachmentUrls: ""
        })
      })
      this.qcForm = Object.assign({}, this.qcForm, {
        receiptBatchId: batch.batchId,
        batchNo: batch.batchNo,
        items: items
      })
    },
    formatReceiptBatchLabel(batch) {
      const trace = batch.supplierBatchNo ? " / 供应商批次 " + batch.supplierBatchNo : ""
      return (batch.batchNo || "-") + " / 到货 " + (batch.arrivedTime || "-") + " / 待检 " + this.formatAmount(batch.pendingQuantity) + trace
    },
    fillAllQcPassed() {
      ;(this.qcForm.items || []).forEach(item => {
        item.inspectedQuantity = this.toNumber(item.pendingQuantity)
        item.acceptedQuantity = this.toNumber(item.pendingQuantity)
        item.rejectedQuantity = 0
        item.concessionQuantity = 0
        item.defectLevel = ""
        item.defectReason = ""
      })
    },
    clearQcClassifications() {
      ;(this.qcForm.items || []).forEach(item => {
        item.acceptedQuantity = 0
        item.rejectedQuantity = 0
        item.concessionQuantity = 0
      })
    },
    submitQualityCheck() {
      if (this.qcSubmitting || this.qcLoading || this.qcLoadError || !this.qcOpen || !this.qcForm.orderId) {
        return Promise.resolve({ blocked: true })
      }
      if (!this.ensureWarehouseContext()) return
      if (this.qcLegacyMode) {
        this.$refs.qcFormRef.validate(valid => {
          if (!valid) return
          this.confirmAndSubmitQualityCheck(
            { qcResult: this.qcForm.qcResult, qcRemark: this.qcForm.qcRemark },
            qualityCheckPurchase
          )
        })
        return
      }
      const items = this.buildQualityCheckItems()
      if (!items) return
      this.confirmAndSubmitQualityCheck({ receiptBatchId: this.qcForm.receiptBatchId, items: items }, qualityCheckPurchaseBatch)
    },
    buildQualityCheckItems() {
      const items = []
      for (let i = 0; i < (this.qcForm.items || []).length; i++) {
        const row = this.qcForm.items[i]
        const inspected = this.toNumber(row.inspectedQuantity)
        const accepted = this.toNumber(row.acceptedQuantity)
        const rejected = this.toNumber(row.rejectedQuantity)
        const concession = this.toNumber(row.concessionQuantity)
        const itemName = row.itemName || row.itemCode || row.batchDetailId
        if ([inspected, accepted, rejected, concession].some(value => value < 0)) {
          this.$modal.msgError("物料 [" + itemName + "] 质检数量不能为负数")
          return null
        }
        if (inspected === 0 && accepted === 0 && rejected === 0 && concession === 0) continue
        if (inspected <= 0 || inspected > this.toNumber(row.pendingQuantity)) {
          this.$modal.msgError("物料 [" + itemName + "] 本次检验数量必须大于 0 且不能超过待检数量")
          return null
        }
        if (Math.abs(accepted + rejected + concession - inspected) > 0.000001) {
          this.$modal.msgError("物料 [" + itemName + "] 合格+拒收+让步数量必须等于本次检验数量")
          return null
        }
        const defectReason = this.normalizeText(row.defectReason)
        if ((rejected > 0 || concession > 0) && !defectReason) {
          this.$modal.msgError("物料 [" + itemName + "] 有拒收或让步数量，请填写原因")
          return null
        }
        const attachmentUrls = this.normalizeText(row.attachmentUrls)
          ? String(row.attachmentUrls).split(",").map(value => value.trim()).filter(Boolean)
          : []
        items.push({
          batchDetailId: row.batchDetailId,
          inspectedQuantity: inspected,
          acceptedQuantity: accepted,
          rejectedQuantity: rejected,
          concessionQuantity: concession,
          defectLevel: row.defectLevel || undefined,
          defectReason: defectReason || undefined,
          attachmentUrls: attachmentUrls
        })
      }
      if (items.length === 0) {
        this.$modal.msgError("请至少完成一条质检明细")
        return null
      }
      return items
    },
    confirmAndSubmitQualityCheck(data, api) {
      const orderId = this.qcForm.orderId
      return this.$modal.confirm(this.getQualityCheckConfirmMessage()).then(() => {
        if (!this.qcOpen || this.qcLoading || this.qcLoadError || this.qcSubmitting ||
          String(orderId) !== String(this.qcTargetOrderId) || String(orderId) !== String(this.qcForm.orderId)) {
          return { discarded: true }
        }
        this.qcSubmitting = true
        return api(orderId, data).then(() => {
          this.$modal.msgSuccess("质检提交成功")
          this.qcOpen = false
          this.getList()
          return { success: true }
        }).finally(() => {
          this.qcSubmitting = false
        })
      }).catch(error => ({ failed: true, error }))
    },
    getReceiveConfirmMessage(items) {
      const totalQuantity = (items || []).reduce((sum, item) => sum + this.toNumber(item.receiveQuantity), 0)
      return [
        "确认提交采购收货？",
        "采购单号：" + (this.receiveForm.orderNo || "-"),
        "入库仓库：" + this.currentWarehouseName,
        "本次收货品项：" + (items || []).length + " 个",
        "本次数量：" + this.formatAmount(totalQuantity),
        "提交后会生成待检收货记录；质检合格或让步接收后才增加当前仓库库存。"
      ].join("\n")
    },
    getQualityCheckConfirmMessage() {
      if (!this.qcLegacyMode) {
        const totals = (this.qcForm.items || []).reduce((sum, item) => {
          sum.inspected += this.toNumber(item.inspectedQuantity)
          sum.accepted += this.toNumber(item.acceptedQuantity)
          sum.rejected += this.toNumber(item.rejectedQuantity)
          sum.concession += this.toNumber(item.concessionQuantity)
          return sum
        }, { inspected: 0, accepted: 0, rejected: 0, concession: 0 })
        return [
          "确认提交逐行质检？",
          "采购单号：" + (this.qcForm.orderNo || "-"),
          "收货批次：" + (this.qcForm.batchNo || "-"),
          "本次检验：" + this.formatAmount(totals.inspected),
          "合格 / 拒收 / 让步：" + [totals.accepted, totals.rejected, totals.concession].map(value => this.formatAmount(value)).join(" / "),
          "提交后将写入不可覆盖的质检记录，并按合格与让步数量入库。"
        ].join("\n")
      }
      const resultLabel = this.qcStatusLabel(this.qcForm.qcResult)
      return [
        "确认提交采购质检？",
        "采购单号：" + (this.qcForm.orderNo || "-"),
        "质检结果：" + resultLabel,
        "提交后会更新采购单质检状态，影响后续采购入库和追溯。"
      ].join("\n")
    },
    doCancel(row) {
      if (!this.ensureWarehouseContext()) return
      if (!this.canCancel(row)) {
        this.$modal.msgError("采购单已发生收货，不能取消")
        return
      }
      this.$modal.confirm("确认取消采购单 [" + row.orderNo + "]?").then(() => {
        cancelPurchase(row.orderId).then(() => { this.$modal.msgSuccess("已取消"); this.getList() })
      })
    },
    doDeleteDraft(row) {
      if (!this.ensureWarehouseContext()) return
      if (row.status !== "draft") {
        this.$modal.msgError("只有草稿采购单可以删除")
        return
      }
      this.$modal.confirm("确认删除草稿采购单 [" + row.orderNo + "]?").then(() => {
        deleteDraftPurchase(row.orderId).then(() => { this.$modal.msgSuccess("已删除"); this.getList() })
      })
    },
    showDetail(orderId) {
      if (!this.ensureWarehouseContext()) return
      this.detailOpen = true
      this.detailLoading = true
      this.detailOrder = { details: [] }
      getPurchaseDetail(orderId).then(res => {
        const data = res.data || {}
        this.detailOrder = Object.assign({}, data, {
          details: (data.details || []).map(item => this.normalizeDetail(item))
        })
      }).finally(() => {
        this.detailLoading = false
      })
    },
    handleExport() {
      if (!this.ensureWarehouseContext()) return
      this.$modal.confirm("确认导出当前查询条件下的采购单数据？").then(() => {
        this.download("inventory/purchase/export", this.buildQuery(), this.exportFileName("采购单数据", this.dateRange))
      })
    },
    hasValue(value) {
      return value !== undefined && value !== null && value !== ""
    }
  }
}
</script>
<style lang="scss" scoped>
.mb12 { margin-bottom: 12px }
.mb8 { margin-bottom: 8px }
.context-alert { border-radius: 8px; }
.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.action-tooltip-wrap {
  display: inline-block;
}
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
  margin-top: 3px;
}
.product-query {
  margin-bottom: 8px;
}
.order-detail {
  min-height: 120px;
}
.receive-batch-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #606266;
}
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
.amount-txt { line-height: 28px; font-size: 13px; }
.compact-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  white-space: nowrap;
}
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
.dialog-state-panel {
  align-items: center;
  color: #606266;
  display: flex;
  gap: 8px;
  justify-content: center;
  min-height: 180px;
}
.dialog-state-error {
  color: #f56c6c;
  flex-direction: column;
}
::v-deep .dropdown-danger { color: #f56c6c; }

@import "~@/styles/warehouse-management-page.scss";
</style>
