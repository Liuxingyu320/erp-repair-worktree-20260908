<template>
  <div class="app-container warehouse-page">
    <inventory-draft-recovery feature="purchase" @recovered="onDraftRecovered" />
    <inventory-page-hero
      title="采购管理"
      eyebrow="入库作业"
      description="从采购下单到收货质检，全程跟踪入库进度、到货批次与供应商履约。"
      scope-text="按当前仓库入库"
      icon="el-icon-s-order"
      tone="amber"
      :features="['采购下单', '收货登记', '质量检验']"
    />
    <el-card v-hasPermi="['inv:purchase:receive']" v-if="receiveRecoveryRecords.length || receiveRecoveryError" shadow="never" class="mb12">
      <p role="status">{{ receiveRecoveryError || '以下收货结果待核对；即使采购单已收完，也可在这里恢复原操作。' }}</p>
      <el-button size="small" @click="refreshReceiveRecovery">刷新待核对记录</el-button>
      <el-button v-for="record in receiveRecoveryRecords" :key="record.requestId" :disabled="receiveRecoveryBusy || receiveSubmitting" @click="recoverPurchaseReceive(record)">核对采购单 {{ record.orderId }} 的上一笔收货</el-button>
    </el-card>
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
              <el-input v-model="scope.row.quantity" inputmode="decimal" aria-label="采购数量，最多两位小数" size="small" :disabled="formSubmitting"/><span v-if="!validInventoryQuantity(scope.row.quantity)" class="text-danger" role="alert">大于 0，最多两位小数</span>
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

    <el-dialog title="选择采购物料" :visible.sync="productDialogOpen" @close="handleProductSelectorClose" width="920px" append-to-body :close-on-click-modal="false">
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
      <el-table ref="productTable" v-loading="productLoading" :data="productList" border size="small" height="360" :empty-text="selectedItemType ? '暂无可选物料' : '请先选择物料类型'" :row-key="productSelectionKey" @select="handleProductRowSelect" @select-all="handleProductSelectionChange">
        <el-table-column type="selection" width="45"/>
        <el-table-column label="物料名称" prop="itemName" min-width="170"/>
        <el-table-column label="物料编码" prop="itemCode" width="130"/>
        <el-table-column label="规格" prop="spec" width="110"/>
        <el-table-column label="单位" prop="unit" width="70"/>
        <el-table-column label="供应商" prop="supplierName" min-width="140"/>
        <el-table-column label="采购参考价" prop="purchasePrice" width="110" align="right"/>
      </el-table>
      <pagination v-show="productTotal>0" :total="productTotal" :page.sync="productQuery.pageNum" :limit.sync="productQuery.pageSize" @pagination="getProductList"/>
      <section class="product-chosen-list" aria-label="已选商品">
        <div><strong>已选商品 {{ productSelection.length }} 项</strong><el-button type="text" :disabled="!productSelection.length" @click="clearProductSelection">清空已选</el-button></div>
        <p v-if="!productSelection.length">同一供应商内可以翻页、切换类型和继续搜索，选齐后统一添加。</p>
        <ul v-else>
          <li v-for="item in productSelection" :key="productSelectionKey(item)">
            <span>{{ itemTypeLabel(item.itemType) }} · {{ item.itemName }} <small>{{ item.itemCode }}</small></span>
            <button type="button" :aria-label="'移除已选：' + item.itemName" @click="removeProductSelection(item)">移除</button>
          </li>
        </ul>
      </section>
      <div slot="footer">
        <el-button @click="productDialogOpen = false">取消</el-button>
        <el-button type="primary" :disabled="productLoading || !productSelection.length" @click="confirmProductSelection">添加到明细</el-button>
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
      <p v-if="receiveNeedsReopen" role="status">当前输入尚未作为新收货提交。请重新打开收货窗口，核对最新可收数量后确认新到货。</p>
      <div slot="footer">
        <el-button :disabled="receiveSubmitting" @click="receiveOpen = false">取消</el-button>
        <el-button v-hasPermi="['inv:purchase:receive']" type="primary" :loading="receiveSubmitting" :disabled="receiveLoading || receiveSubmitting || receiveNeedsReopen" @click="submitReceive">提交收货</el-button>
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
          title="勾选本次检验的商品，未勾选商品继续待检。合格、拒收和让步数量之和须等于本次检验数量。"
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
                <el-select v-model="qcForm.receiptBatchId" :disabled="qcSubmitting" style="width:100%" @change="handleQcBatchChange">
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
              <el-checkbox :value="qcForm.items.length > 0 && qcForm.items.every(isQcSelected)" :disabled="qcSubmitting" @change="setQcSelection">全选</el-checkbox>
              <el-button size="mini" type="success" plain :disabled="qcSubmitting" @click="fillAllQcPassed">所选全部合格</el-button>
              <el-button size="mini" :disabled="qcSubmitting" @click="clearQcClassifications">清空分类数量</el-button>
            </div>
          </div>
          <el-table :data="qcForm.items" border size="mini" max-height="520">
            <el-table-column label="检验" width="64" fixed="left" align="center">
              <template slot-scope="scope"><el-checkbox :value="isQcSelected(scope.row)" :disabled="qcSubmitting" :aria-label="'选择检验：' + (scope.row.itemName || scope.row.itemCode)" @change="value => $set(scope.row, 'qcSelected', value)" /></template>
            </el-table-column>
            <el-table-column label="物料" min-width="160" fixed="left">
              <template slot-scope="scope">
                <div class="product-name">{{ scope.row.itemName || '-' }}</div>
                <div class="product-meta">{{ scope.row.itemCode || '-' }} / {{ scope.row.unit || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="待检" prop="pendingQuantity" width="82" align="right"/>
            <el-table-column label="本次检验" width="130">
              <template slot-scope="scope">
                <el-input-number v-model="scope.row.inspectedQuantity" :disabled="qcSubmitting || !isQcSelected(scope.row)" :min="0" :max="scope.row.pendingQuantity" :precision="2" :controls="false" size="mini" style="width:105px"/>
              </template>
            </el-table-column>
            <el-table-column label="合格" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.acceptedQuantity" :disabled="qcSubmitting || !isQcSelected(scope.row)" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="拒收" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.rejectedQuantity" :disabled="qcSubmitting || !isQcSelected(scope.row)" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="让步" width="120">
              <template slot-scope="scope"><el-input-number v-model="scope.row.concessionQuantity" :disabled="qcSubmitting || !isQcSelected(scope.row)" :min="0" :precision="2" :controls="false" size="mini" style="width:96px"/></template>
            </el-table-column>
            <el-table-column label="缺陷等级" width="118">
              <template slot-scope="scope">
                <el-select v-model="scope.row.defectLevel" :disabled="qcSubmitting || !isQcSelected(scope.row)" clearable size="mini" placeholder="选填">
                  <el-option label="轻微" value="minor"/><el-option label="一般" value="major"/><el-option label="严重" value="critical"/>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="拒收/让步原因" min-width="190">
              <template slot-scope="scope"><el-input v-model="scope.row.defectReason" :disabled="qcSubmitting || !isQcSelected(scope.row)" maxlength="500" size="mini" clearable placeholder="有拒收或让步数量时必填"/></template>
            </el-table-column>
            <el-table-column label="附件" min-width="180">
              <template slot-scope="scope">
                <file-upload v-model="scope.row.attachmentUrls" :disabled="qcSubmitting || !isQcSelected(scope.row)" :context-key="[qcOpen, qcForm.orderId, qcForm.receiptBatchId, scope.row.batchDetailId].join(':')" @upload-state="handleQcUploadState" :limit="3" :file-size="10" :is-show-tip="false" :drag="false" :file-type="['jpg', 'jpeg', 'png', 'pdf']"/>
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
import InventoryDraftRecovery from "@/views/inventory/components/InventoryDraftRecovery.vue"
const { validInventoryQuantity } = require("@/utils/inventoryQuantity")
import { listPurchase, getPurchaseDetail, getPurchaseDraft, getPurchaseActionContext, getPurchaseReceiveContext, listPurchaseSuppliers, listPurchaseProducts, listPurchaseOeItems, listPurchaseGifts, savePurchase, submitPurchase, submitPurchaseDraft, qualityCheckPurchase, listPendingReceiptBatches, qualityCheckPurchaseBatch, cancelPurchase, deleteDraftPurchase } from "@/api/inventory/purchase"
import WarehouseSelect from "@/views/inventory/components/WarehouseSelect"
import { parseTime } from "@/utils/common"
import { getSelectedDeptId, getSelectedDeptName, isSelectedWarehouse } from "@/utils/shopContext"
import { canReceivePurchase, canQualityCheckPurchase, canCancelPurchase } from "./purchaseActionRules"
import { purchaseBusinessStageLabel, purchaseBusinessStageTone } from "@/utils/purchaseBusinessStage"
const { getPurchaseReceiveRecovery, purchaseReceiveResultMessage } = require("@/utils/purchaseReceiveRecovery")
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")
export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "purchase",
    loadFocusedRow(orderId) { return getPurchaseActionContext(orderId, { silentError: true }) },
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
  components: { InventoryDraftRecovery, WarehouseSelect },
  data() {
    return {
      loading: false, total: 0, list: [], listError: "", listRequestSequence: 0, activeListQuerySnapshot: "",
      open: false, formSubmitting: false, detailOpen: false, detailLoading: false, receiveOpen: false, receiveLoading: false,
      receiveSubmitting: false, receiveRequestSequence: 0, receiveScope: null, receiveObservedRequestId: null, receiveNeedsReopen: false,
      receiveRecoveryRecords: [], receiveRecoveryError: "", receiveRecoveryListSequence: 0, receiveRecoveryBusy: false,
      qcUploadStates: {},
      qcOpen: false, qcLoading: false, qcSubmitting: false, qcLegacyMode: false, qcBatches: [],
      qcRequestSequence: 0, qcLoadError: "", qcTargetOrderId: undefined,
      productDialogOpen: false, productLoading: false, productList: [], productTotal: 0, productSelection: [],
      productSelectionScope: "", productSelectionForm: null,
      productReadSeq: 0, productReadContext: "", productPageInactive: false, productDeptListenerBound: false,
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
        orderTitle: [{ required: true, whitespace: true, message: "请输入标题", trigger: "blur" }],
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
  created() { this.bindProductDeptListener(); this.getList(); this.refreshReceiveRecovery() },
  activated() { this.productPageInactive = false; this.bindProductDeptListener(); this.refreshReceiveRecovery() },
  deactivated() { this.productPageInactive = true; this.closeProductSelector(); this.invalidateReceiveSession(); this.invalidateEditor() },
  beforeDestroy() {
    this.invalidateReceiveSession(); this.invalidateEditor()
    this.productPageInactive = true
    this.closeProductSelector()
    if (this.productDeptListenerBound && typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.handleProductDeptChanged)
    this.productDeptListenerBound = false
  },
  watch: {
    open(value) { if (!value) this.invalidateEditor() },
    "$store.state.user.sessionRevision"() { this.invalidateEditor() },
    productQuery: { deep: true, handler() { this.handleProductContextChange() } },
    selectedItemType() { this.handleProductContextChange() },
    "form.supplierId"() { this.handleProductContextChange() },
    "form.supplierName"() { this.handleProductContextChange() },
    form() { this.handleProductContextChange() },
    "form.orderId"() { this.handleProductContextChange() },
    productDialogOpen(value) { if (!value) this.handleProductSelectorClose() },
    "$route.fullPath"() { this.closeProductSelector(); this.invalidateReceiveSession(); this.invalidateEditor(); this.refreshReceiveRecovery() },
    "$store.getters.id"() { this.closeProductSelector(); this.invalidateReceiveSession(); this.invalidateEditor(); this.refreshReceiveRecovery() },
    receiveOpen(value) { if (!value) { this.invalidateReceiveSession(); this.invalidateEditor(); this.refreshReceiveRecovery() } }
  },
  methods: {
    onDraftRecovered({ record, response }) {
      const data = response.data
      if (this.open && String(this.form.orderId || "new") === String(record.payload.orderId || "new")) {
        this.$set(this.form, "orderId", data.orderId)
        this.$set(this.form, "version", data.version)
        this.$set(this.form, "status", data.status)
        this.$modal.msgWarning(data.status === "draft" ? "已找回草稿编号，当前输入仍保留；请核对后再保存" : "上次操作已提交，当前输入保留供核对，请关闭窗口查看原单")
      }
      return this.getList()
    },
    validInventoryQuantity,
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
    productContextKey() {
      return JSON.stringify({ type: this.selectedItemType, query: this.productQuery,
        orderId: this.form.orderId, supplierId: this.form.supplierId,
        supplierName: this.form.supplierName, deptId: getSelectedDeptId(), actor: this.productActor() })
    },
    productActor() { return this.$store && this.$store.getters ? this.$store.getters.id : null },
    productScopeKey() {
      return JSON.stringify([this.productActor(), getSelectedDeptId(), this.$route && this.$route.fullPath, this.form.orderId,
        this.form.supplierId, this.form.supplierName])
    },
    isProductSelectionCurrent() {
      return this.productDialogOpen && !this.productPageInactive && this.productSelectionForm === this.form &&
        this.productSelectionScope === this.productScopeKey()
    },
    invalidateProductReads(preserveSelection = false) {
      this.productReadSeq += 1
      this.productReadContext = ""
      this.productLoading = false
      this.productList = []
      this.productTotal = 0
      if (!preserveSelection) {
        this.productSelection = []
        this.productSelectionScope = ""
        this.productSelectionForm = null
      }
    },
    handleProductContextChange() {
      if (this.productSelectionScope && !this.isProductSelectionCurrent()) { this.closeProductSelector(); return }
      if (this.productReadContext && this.productReadContext !== this.productContextKey()) this.invalidateProductReads(true)
    },
    closeProductSelector() {
      this.productDialogOpen = false
      this.invalidateProductReads()
    },
    handleProductSelectorClose() {
      if (!this.productDialogOpen) this.invalidateProductReads()
    },
    bindProductDeptListener() {
      if (!this.productDeptListenerBound && typeof window !== "undefined") {
        window.addEventListener("erp:dept-changed", this.handleProductDeptChanged)
        this.productDeptListenerBound = true
      }
    },
    handleProductDeptChanged() { this.closeProductSelector(); this.invalidateReceiveSession(); this.invalidateEditor(); this.refreshReceiveRecovery() },
    isCurrentProductRead(seq, context) {
      return !this.productPageInactive && this.productDialogOpen && seq === this.productReadSeq && context === this.productContextKey()
    },
    getProductList() {
      if (!this.isProductSelectionCurrent()) { this.closeProductSelector(); return Promise.resolve() }
      if (!this.allowedItemTypes.includes(this.selectedItemType)) {
        this.invalidateProductReads(true)
        return Promise.resolve()
      }
      const type = this.selectedItemType
      const query = Object.assign({}, this.productQuery)
      const context = this.productContextKey()
      const seq = ++this.productReadSeq
      this.productReadContext = context
      this.productList = []
      this.productTotal = 0
      this.productLoading = true
      return this.listCatalogItems(type, query).then(res => {
        if (!this.isCurrentProductRead(seq, context)) return
        this.productList = (res.rows || []).filter(p => p.status === "0").map(item => this.normalizeCatalogItem(item, type))
        this.productTotal = res.total || 0
        this.restoreProductSelection()
      }).catch(() => {
        if (this.isCurrentProductRead(seq, context)) this.$modal.msgError("采购物料加载失败，请重试")
      }).finally(() => {
        if (this.isCurrentProductRead(seq, context)) this.productLoading = false
      })
    },
    listCatalogItems(type = this.selectedItemType, query = this.productQuery) {
      const keyword = this.normalizeText(query.productName)
      const base = { pageNum: query.pageNum, pageSize: query.pageSize, status: "0" }
      const options = { silentError: true }
      if (type === "oe") {
        return listPurchaseOeItems(Object.assign(base, keyword ? { keyword } : {}, query.supplierName ? { supplierName: query.supplierName } : {}), options)
      }
      if (type === "gift") {
        return listPurchaseGifts(Object.assign(base, keyword ? { keyword } : {}), options)
      }
      return listPurchaseProducts(Object.assign(base, keyword ? { keyword } : {}, query.supplierName ? { supplierName: query.supplierName } : {}), options)
    },
    normalizeCatalogItem(raw, type = this.selectedItemType || "product") {
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
      return this.getProductList()
    },
    handleItemTypeChange() {
      return this.resetProductQuery()
    },
    openProductSelector() {
      if (this.productPageInactive || !this.ensureWarehouseContext()) return
      this.invalidateProductReads()
      if (!this.form.supplierId || !this.form.supplierName) {
        this.$modal.msgError("请先选择合作中的供应商档案")
        return
      }
      this.productDialogOpen = true
      this.productSelectionScope = this.productScopeKey()
      this.productSelectionForm = this.form
      this.selectedItemType = null
      this.productSelection = []
      this.productList = []
      this.productTotal = 0
      this.$nextTick(() => {
        if (this.$refs.productTable) this.$refs.productTable.clearSelection()
      })
    },
    productSelectionKey(row) { return row ? row.itemType + ":" + String(row.itemId) : "" },
    restoreProductSelection() {
      const context = this.productContextKey(), sequence = this.productReadSeq
      this.$nextTick(() => {
        if (!this.isProductSelectionCurrent() || context !== this.productContextKey() || sequence !== this.productReadSeq) return
        const table = this.$refs.productTable
        if (!table) return
        table.clearSelection()
        this.productList.forEach(row => {
          if (this.productSelection.some(item => this.productSelectionKey(item) === this.productSelectionKey(row)))
            table.toggleRowSelection(row, true)
        })
      })
    },
    handleProductRowSelect(rows, row) {
      if (!this.isProductSelectionCurrent() || this.productLoading || !this.productList.includes(row)) return
      const key = this.productSelectionKey(row)
      const chosen = (rows || []).some(item => this.productSelectionKey(item) === key)
      this.productSelection = this.productSelection.filter(item => this.productSelectionKey(item) !== key)
      if (chosen) this.productSelection.push(Object.assign({}, row))
    },
    handleProductSelectionChange(rows) {
      if (!this.isProductSelectionCurrent() || this.productLoading) return
      const pageKeys = new Set(this.productList.map(this.productSelectionKey))
      const selectedKeys = new Set((rows || []).map(this.productSelectionKey))
      const outside = this.productSelection.filter(item => !pageKeys.has(this.productSelectionKey(item)))
      const selected = this.productList.filter(item => selectedKeys.has(this.productSelectionKey(item)))
      this.productSelection = outside.concat(selected.map(row => Object.assign({}, row)))
    },
    removeProductSelection(row) {
      if (!this.isProductSelectionCurrent()) { this.closeProductSelector(); return }
      this.productSelection = this.productSelection.filter(item => this.productSelectionKey(item) !== this.productSelectionKey(row))
      this.restoreProductSelection()
    },
    clearProductSelection() {
      this.productSelection = []
      this.restoreProductSelection()
    },
    confirmProductSelection() {
      if (!this.isProductSelectionCurrent()) { this.closeProductSelector(); return }
      if (this.productLoading) return
      if (!this.productSelection.length) {
        this.$modal.msgError("请选择物料")
        return
      }
      const selected = this.productSelection.slice()
      const missing = selected.filter(item => !this.normalizeText(item.supplierName))
      const mismatched = selected.filter(item => item.itemType !== "gift" && this.normalizeText(item.supplierName) !== this.normalizeText(this.form.supplierName))
      if (missing.length || mismatched.length) {
        this.$modal.msgError("以下物料供应商信息已不适用，请移除后重新选择：" + missing.concat(mismatched).map(item => item.itemName).join("、"))
        return
      }
      selected.forEach(product => {
        const existing = this.form.details.find(item => item.itemType === product.itemType && String(item.itemId) === String(product.itemId))
        if (existing) {
          existing.quantity = this.toNumber(existing.quantity) + 1
          if (!this.hasValue(existing.unitPrice)) existing.unitPrice = product.purchasePrice || 0
        } else this.form.details.push(this.buildDetailFromProduct(product))
      })
      this.closeProductSelector()
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
      if (this.formSubmitting) return Promise.resolve({ busy: true })
      const epoch = this.editorEpoch = (this.editorEpoch || 0) + 1, context = this.editorContext()
      if (!this.ensureWarehouseContext()) return
      this.closeProductSelector()
      if (!row) {
        this.form = { orderId: undefined, orderTitle: "", supplierId: undefined, supplierName: "", orderDate: this.defaultOrderDate(), remark: "", totalAmount: 0, details: [] }
        this.supplierOptions = []
        this.open = true
        this.loadSuppliers()
        this.$nextTick(() => { if (this.$refs.formRef) this.$refs.formRef.clearValidate() })
        return
      }
      return getPurchaseDraft(row.orderId, { silentError: true }).then(res => {
        if (!this.editorCurrent(epoch, context)) return
        const data = res.data || {}
        this.form = Object.assign({}, data, {
          orderDate: data.orderDate || this.defaultOrderDate(),
          details: (data.details || []).map(item => this.normalizeDetail(item))
        })
        this.supplierOptions = data.supplierId ? [{ supplierId: data.supplierId, supplierName: data.supplierName }] : []
        this.open = true
        this.loadSuppliers(data.supplierName)
        this.$nextTick(() => { if (this.$refs.formRef) this.$refs.formRef.clearValidate() })
      }).catch(error => { if (this.editorCurrent(epoch, context)) this.$modal.msgError(error.message || "草稿加载失败，请重试") })
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
    editorContext() { return JSON.stringify([getSelectedDeptId(), this.productActor(), this.$store && this.$store.state && this.$store.state.user && this.$store.state.user.sessionRevision, this.$route && this.$route.fullPath]) },
    editorCurrent(epoch, context) { return !this.productPageInactive && epoch === this.editorEpoch && context === this.editorContext() },
    invalidateEditor() { this.editorEpoch = (this.editorEpoch || 0) + 1; this.supplierReadSequence = (this.supplierReadSequence || 0) + 1; this.formSubmitting = false; this.open = false },
    handleFormBeforeClose(done) {
      if (this.formSubmitting) return
      this.invalidateEditor()
      done()
    },
    closeForm() {
      if (this.formSubmitting) return
      this.invalidateEditor()
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
      const epoch = this.editorEpoch, context = this.editorContext()
      this.formSubmitting = true
      return this.validateEditorForm().then(valid => {
        if (!this.editorCurrent(epoch, context) || !valid) return { invalid: true }
        return this.submitValidatedForm(submitAfter, epoch, context)
      }).catch(error => { if (this.editorCurrent(epoch, context) && !error.notified) this.$modal.msgError(error.message || "保存失败，输入已保留"); return { failed: true, error } }).finally(() => {
        if (this.editorCurrent(epoch, context)) this.formSubmitting = false
      })
    },
    submitValidatedForm(submitAfter, epoch = this.editorEpoch, context = this.editorContext()) {
      if (this.form.status && this.form.status !== "draft") { this.$modal.msgWarning("原单已提交，请关闭窗口查看原单，当前输入仍保留"); return { invalid: true } }
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
      const invalidQuantity = this.form.details.find(d => !validInventoryQuantity(d.quantity))
      if (invalidQuantity) {
        this.$modal.msgError("采购数量必须大于 0 且最多两位小数")
        return { invalid: true }
      }
      const invalidPrice = this.form.details.find(d => this.toNumber(d.unitPrice) < 0)
      if (invalidPrice) {
        this.$modal.msgError("进价不能小于0")
        return { invalid: true }
      }
      const payload = this.buildPurchasePayload()
      const api = submitAfter ? submitPurchase : savePurchase
      return api(payload).then(res => {
        if (!this.editorCurrent(epoch, context)) return { discarded: true }
        this.$set(this.form, "orderId", res.data.orderId)
        this.$set(this.form, "version", res.data.version)
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
      const sequence = this.supplierReadSequence = (this.supplierReadSequence || 0) + 1, epoch = this.editorEpoch, context = this.editorContext()
      const current = () => sequence === this.supplierReadSequence && this.editorCurrent(epoch, context)
      if (!this.isWarehouseContext) {
        this.supplierOptions = []
        return Promise.resolve([])
      }
      this.supplierLoading = true
      return listPurchaseSuppliers({
        pageNum: 1,
        pageSize: 100,
        status: "0",
        cooperationStatus: "0",
        supplierName: keyword ? String(keyword).trim() : undefined
      }, { silentError: true }).then(res => {
        if (!current()) return []
        const selected = this.supplierOptions.find(item => String(item.supplierId) === String(this.form.supplierId))
        this.supplierOptions = res.rows || []
        if (selected && !this.supplierOptions.some(item => String(item.supplierId) === String(selected.supplierId))) {
          this.supplierOptions.unshift(selected)
        }
        return this.supplierOptions
      }).catch(() => {
        if (!current()) return []
        this.$modal.msgWarning("供应商选项加载失败，请检查供应商档案权限或稍后重试")
        return this.supplierOptions
      }).finally(() => { if (current()) this.supplierLoading = false })
    },
    onSupplierChange(supplierId) {
      this.closeProductSelector()
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
      submitPurchaseDraft(row.orderId, row.version).then(() => { this.$modal.msgSuccess("提交成功"); this.getList() })
    },
    invalidateReceiveSession() {
      this.receiveRequestSequence += 1
      this.receiveRecoveryListSequence += 1
      this.receiveOpen = false
      this.receiveSubmitting = false
      this.receiveLoading = false
    },
    isCurrentReceive(sequence, scope, requireOpen = true) {
      if (this.productPageInactive || sequence !== this.receiveRequestSequence || (requireOpen && !this.receiveOpen)) return false
      try { getPurchaseReceiveRecovery().assertCurrent(scope); return true } catch (_) { return false }
    },
    async refreshReceiveRecovery() {
      const sequence = ++this.receiveRecoveryListSequence
      try {
        const records = await getPurchaseReceiveRecovery().list()
        if (!this.productPageInactive && sequence === this.receiveRecoveryListSequence) {
          this.receiveRecoveryRecords = records
          this.receiveRecoveryError = ""
        }
      } catch (error) {
        if (!this.productPageInactive && sequence === this.receiveRecoveryListSequence) {
          this.receiveRecoveryRecords = []
          this.receiveRecoveryError = error.message
        }
      }
    },
    async recoverPurchaseReceive(record) {
      if (this.receiveRecoveryBusy || this.receiveSubmitting) return
      const recovery = getPurchaseReceiveRecovery()
      const sequence = this.receiveRequestSequence
      let scope
      this.receiveRecoveryBusy = true
      try {
        scope = recovery.capture()
        await this.$modal.confirm("将核对采购单 " + record.orderId + " 的上一笔收货，本次新输入不会提交。")
        if (!this.isCurrentReceive(sequence, scope, false)) return
        const result = await recovery.run({ orderId: record.orderId, requestId: record.requestId, scope,
          recoveryOnly: true, isCurrent: () => this.isCurrentReceive(sequence, scope, false) })
        if (!this.isCurrentReceive(sequence, scope, false)) return
        this.$modal.msgSuccess(purchaseReceiveResultMessage(result))
        await recovery.acknowledge(result)
        this.getList()
      } catch (error) {
        if (error !== "cancel" && error !== "close" && (!scope || this.isCurrentReceive(sequence, scope, false))) this.$modal.msgError(error.message || "收货结果仍待核对")
      } finally { this.receiveRecoveryBusy = false; this.refreshReceiveRecovery() }
    },
    async doReceive(row) {
      if (this.receiveSubmitting || !this.ensureWarehouseContext()) return
      const requestSequence = ++this.receiveRequestSequence
      this.receiveOpen = true
      this.receiveLoading = true
      this.receiveNeedsReopen = false
      const recovery = getPurchaseReceiveRecovery()
      let scope
      try {
        scope = recovery.capture()
        this.receiveScope = scope
        this.receiveForm = { orderId: row.orderId, orderNo: row.orderNo, shopDeptId: scope.dept, warehouseId: scope.dept, supplierBatchNo: "", deliveryNoteNo: "", arrivedTime: this.defaultReceiveTime(), remark: "", details: [] }
        const head = await recovery.inspect(row.orderId, scope)
        if (!this.isCurrentReceive(requestSequence, scope)) return
        if (head.pending) {
          this.receiveOpen = false
          await this.refreshReceiveRecovery()
          this.$modal.msgWarning("上一笔收货结果待核对，请先使用页面上方的核对入口；新到货暂未提交。")
          return
        }
        this.receiveObservedRequestId = head.observedRequestId
        const res = await getPurchaseReceiveContext(row.orderId)
        if (requestSequence !== this.receiveRequestSequence || !this.receiveOpen || !this.isCurrentReceive(requestSequence, scope)) return
        const order = res.data || {}
        if (String(order.orderId) !== String(row.orderId)) throw new Error("采购详情身份无法确认")
        const details = (order.details || []).map(item => {
          const quantity = this.toNumber(item.quantity), receivedQuantity = this.toNumber(item.receivedQuantity)
          return Object.assign({}, item, { quantity, receivedQuantity, remainingQuantity: Math.max(quantity - receivedQuantity, 0), receiveQuantity: 0 })
        })
        this.receiveForm = Object.assign({}, this.receiveForm, { orderId: order.orderId, orderNo: order.orderNo || row.orderNo,
          shopDeptId: scope.dept, warehouseId: scope.dept, details })
        this.$nextTick(() => { if (this.isCurrentReceive(requestSequence, scope) && this.$refs.receiveFormRef) this.$refs.receiveFormRef.clearValidate() })
      } catch (error) {
        if (!scope || this.isCurrentReceive(requestSequence, scope)) { this.receiveNeedsReopen = true; this.$modal.msgError(error.message || "收货详情加载失败") }
      } finally { if (requestSequence === this.receiveRequestSequence) this.receiveLoading = false }
    },
    submitReceive() {
      if (this.receiveSubmitting || this.receiveLoading || this.receiveNeedsReopen || !this.receiveOpen || !this.ensureWarehouseContext()) return Promise.resolve()
      const operation = { sequence: this.receiveRequestSequence, scope: this.receiveScope,
        head: this.receiveObservedRequestId, form: JSON.parse(JSON.stringify(this.receiveForm)) }
      if (!this.isCurrentReceive(operation.sequence, operation.scope)) return Promise.resolve()
      this.receiveSubmitting = true
      const formRef = this.$refs.receiveFormRef
      return getPurchaseReceiveRecovery().inspect(operation.form.orderId, operation.scope).then(async head => {
        if (!this.isCurrentReceive(operation.sequence, operation.scope)) return
        if (head.pending || head.observedRequestId !== operation.head) {
          // Reconcile before validating edited quantities or reading new remaining amounts.
          await this.$modal.confirm("先核对采购单 " + operation.form.orderId + " 的上一笔收货；当前新输入不会提交。")
          if (!this.isCurrentReceive(operation.sequence, operation.scope)) return
          const recovery = getPurchaseReceiveRecovery()
          const result = await recovery.run({ orderId: operation.form.orderId, requestId: head.observedRequestId,
            recoveryOnly: true, scope: operation.scope, isCurrent: () => this.isCurrentReceive(operation.sequence, operation.scope) })
          if (!this.isCurrentReceive(operation.sequence, operation.scope)) return
          this.$modal.msgSuccess(purchaseReceiveResultMessage(result))
          this.receiveNeedsReopen = true
          await recovery.acknowledge(result)
          this.getList()
          return
        }
        const valid = await new Promise(resolve => {
          if (formRef) formRef.validate(result => resolve(result))
          else resolve(true)
        })
        if (valid && this.isCurrentReceive(operation.sequence, operation.scope)) return this.submitValidatedReceive(operation)
      }).catch(error => {
        if (error !== "cancel" && error !== "close" && this.isCurrentReceive(operation.sequence, operation.scope)) this.$modal.msgError(error.message || "收货结果仍待核对")
      }).finally(() => {
        if (this.isCurrentReceive(operation.sequence, operation.scope)) this.receiveSubmitting = false
        this.refreshReceiveRecovery()
      })
    },
    async submitValidatedReceive(operation) {
      const form = operation.form
      try {
        const items = []
        for (const detail of form.details || []) {
          const quantity = Number(detail.receiveQuantity)
          if (!Number.isFinite(quantity) || quantity < 0 || quantity > Number(detail.remainingQuantity)) throw new Error("本次收货数量须在 0 与未收数量之间")
          if (quantity > 0) items.push({ detailId: detail.detailId, receiveQuantity: quantity })
        }
        if (!items.length) throw new Error("请至少录入一条大于 0 的本次收货数量")
        if (!form.arrivedTime) throw new Error("请选择实际到货时间")
        if (this.normalizeText(form.supplierBatchNo).length > 100 || this.normalizeText(form.deliveryNoteNo).length > 100 || this.normalizeText(form.remark).length > 500)
          throw new Error("收货批号、送货单号或备注超过允许长度")
        const data = { warehouseId: operation.scope.dept, supplierBatchNo: this.normalizeText(form.supplierBatchNo) || undefined,
          deliveryNoteNo: this.normalizeText(form.deliveryNoteNo) || undefined, arrivedTime: form.arrivedTime,
          remark: this.normalizeText(form.remark) || undefined, items }
        await this.$modal.confirm(this.getReceiveConfirmMessage(items))
        if (!this.isCurrentReceive(operation.sequence, operation.scope)) return
        const recovery = getPurchaseReceiveRecovery()
        const result = await recovery.run({ orderId: form.orderId, payload: data, scope: operation.scope,
          observedRequestId: operation.head, isCurrent: () => this.isCurrentReceive(operation.sequence, operation.scope) })
        if (!this.isCurrentReceive(operation.sequence, operation.scope)) return
        this.$modal.msgSuccess(purchaseReceiveResultMessage(result))
        if (result.purchaseReceiveRecovery.recovered) this.receiveNeedsReopen = true
        else this.receiveOpen = false
        await recovery.acknowledge(result)
        this.getList()
      } catch (error) {
        if (error !== "cancel" && error !== "close" && this.isCurrentReceive(operation.sequence, operation.scope)) this.$modal.msgError(error.message || "收货结果待核对，请核对上一笔")
      } finally { this.refreshReceiveRecovery() }
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
          qcSelected: false,
          inspectedQuantity: 0,
          acceptedQuantity: 0,
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
    isQcSelected(row) {
      return !!row && (row.qcSelected === true || (row.qcSelected === undefined && Number(row.inspectedQuantity) > 0))
    },
    setQcSelection(selected) {
      if (this.qcSubmitting) return
      ;(this.qcForm.items || []).forEach(row => this.$set(row, 'qcSelected', selected))
    },
    fillAllQcPassed() {
      if (this.qcSubmitting) return
      ;(this.qcForm.items || []).filter(this.isQcSelected).forEach(item => {
        const requested = Number(item.inspectedQuantity), pending = Number(item.pendingQuantity)
        item.inspectedQuantity = Number.isFinite(requested) && requested > 0 && requested <= pending ? requested : pending
        item.acceptedQuantity = item.inspectedQuantity
        item.rejectedQuantity = 0
        item.concessionQuantity = 0
        item.defectLevel = ""
        item.defectReason = ""
      })
    },
    clearQcClassifications() {
      if (this.qcSubmitting) return
      ;(this.qcForm.items || []).filter(this.isQcSelected).forEach(item => {
        item.acceptedQuantity = 0
        item.rejectedQuantity = 0
        item.concessionQuantity = 0
      })
    },
    handleQcUploadState(state) {
      if (!state || state.id == null) return
      if (state.blocking) this.$set(this.qcUploadStates, state.id, true)
      else this.$delete(this.qcUploadStates, state.id)
    },
    qualityUploadsBlocked() {
      return Object.values(this.qcUploadStates || {}).some(Boolean)
    },
    qualityAttachmentSnapshot() {
      return JSON.stringify((this.qcForm.items || []).map(row => [String(row.batchDetailId), row.attachmentUrls || '']))
    },
    submitQualityCheck() {
      if (this.qualityUploadsBlocked()) {
        this.$modal.msgError('质检附件尚未上传完成，请重试或移除后再提交')
        return Promise.resolve({ blocked: true })
      }
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
        if (!this.isQcSelected(row)) continue
        const inspected = Number(row.inspectedQuantity)
        const accepted = Number(row.acceptedQuantity)
        const rejected = Number(row.rejectedQuantity)
        const concession = Number(row.concessionQuantity)
        const itemName = row.itemName || row.itemCode || row.batchDetailId
        if ([inspected, accepted, rejected, concession].some(value => !Number.isFinite(value) || value < 0)) {
          this.$modal.msgError("物料 [" + itemName + "] 质检数量必须是非负数")
          return null
        }
        if (inspected <= 0 || !Number.isFinite(Number(row.pendingQuantity)) || inspected > Number(row.pendingQuantity)) {
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
        this.$modal.msgError("请至少勾选并完成一条质检明细")
        return null
      }
      return items
    },
    qualityInputSnapshot() {
      return JSON.stringify([this.qcLegacyMode, this.qcForm.qcResult, this.qcForm.qcRemark,
        (this.qcForm.items || []).map(row => [String(row.batchDetailId), this.isQcSelected(row),
          row.inspectedQuantity, row.acceptedQuantity, row.rejectedQuantity, row.concessionQuantity,
          row.defectLevel, row.defectReason, row.attachmentUrls])])
    },
    confirmAndSubmitQualityCheck(data, api) {
      const orderId = this.qcForm.orderId
      const batchId = this.qcForm.receiptBatchId, attachments = this.qualityAttachmentSnapshot(), inputSnapshot = this.qualityInputSnapshot()
      return this.$modal.confirm(this.getQualityCheckConfirmMessage()).then(() => {
        if (inputSnapshot !== this.qualityInputSnapshot()) {
          this.$modal.msgError('本次勾选或质检内容已变化，请核对后重新提交')
          return { discarded: true }
        }
        if (this.qualityUploadsBlocked() || attachments !== this.qualityAttachmentSnapshot()) {
          this.$modal.msgError('附件状态已变化，请处理完毕后重新提交质检')
          return { discarded: true }
        }
        if (!this.qcOpen || this.qcLoading || this.qcLoadError || this.qcSubmitting ||
          String(batchId) !== String(this.qcForm.receiptBatchId) || String(orderId) !== String(this.qcTargetOrderId) || String(orderId) !== String(this.qcForm.orderId)) {
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
        const totals = (this.qcForm.items || []).filter(this.isQcSelected).reduce((sum, item) => {
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
.product-chosen-list { margin-top: 16px; border-top: 1px solid #dcdfe6; padding-top: 12px; }
.product-chosen-list > div, .product-chosen-list li { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.product-chosen-list ul { list-style: none; margin: 0; padding: 0; max-height: 180px; overflow: auto; }
.product-chosen-list li { padding: 6px 0; }
.product-chosen-list small { color: #606266; }
.product-chosen-list button { min-height: 32px; cursor: pointer; }
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
