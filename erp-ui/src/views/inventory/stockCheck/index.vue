<template>
  <div class="app-container stock-check-page">
    <el-card shadow="never" class="stock-check-overview mb12">
      <div class="overview-main">
        <div class="overview-title-block">
          <div class="overview-kicker">库存盘点</div>
          <h2>{{ currentDeptLabel }}</h2>
        </div>
        <div class="overview-metrics">
          <div class="overview-metric">
            <span>当前页单据</span>
            <strong>{{ list.length }}</strong>
          </div>
          <div class="overview-metric">
            <span>待审批</span>
            <strong>{{ pagePendingApprovalCount }}</strong>
          </div>
          <div class="overview-metric">
            <span>已完成</span>
            <strong>{{ pageCompletedCount }}</strong>
          </div>
          <div class="overview-metric loss">
            <span>当前页盘亏</span>
            <strong>{{ formatQuantity(pageLossQuantity) }}</strong>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="mb12">
      <el-form :model="queryParams" inline size="small">
        <el-form-item label="盘点单号">
          <el-input v-model="queryParams.checkNo" placeholder="请输入盘点单号" clearable @keyup.enter.native="getList"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="库存组织">
          <el-tag size="small">{{ currentDeptLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="盘点日期">
          <el-date-picker
            v-model="dateRange"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['inv:stockCheck:list']" type="primary" size="mini" icon="el-icon-search" @click="getList">搜索</el-button>
          <el-button size="mini" icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button v-hasPermi="['inv:stockCheck:add']" size="mini" icon="el-icon-plus" @click="openCreate">新增</el-button>
          <el-button v-hasPermi="['inv:stockCheck:approve']" :type="todoMode ? 'warning' : 'default'" size="mini" icon="el-icon-s-check" @click="loadApprovalTodos">
            {{ todoMode ? '返回全部' : '待我审批' }}
          </el-button>
          <el-button v-hasPermi="['inv:stockCheck:export']" size="mini" icon="el-icon-download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" size="small" class="stock-check-table">
        <el-table-column label="盘点单号" prop="checkNo" min-width="170"/>
        <el-table-column label="盘点日期" prop="checkDate" width="120"/>
        <el-table-column label="盘点范围" width="110">
          <template slot-scope="scope">{{ checkScopeLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="库存组织" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ inventoryOrgLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="商品数" prop="itemCount" width="90" align="right"/>
        <el-table-column label="盘亏数量" width="120" align="right">
          <template slot-scope="scope">
            <span :class="rowLossQuantity(scope.row) > 0 ? 'loss-quantity-strong' : 'muted-quantity'">
              {{ formatQuantity(rowLossQuantity(scope.row)) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="110">
          <template slot-scope="scope">
            <el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip/>
        <el-table-column label="创建时间" prop="createTime" width="170"/>
        <el-table-column label="操作" width="430" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['inv:stockCheck:query']" type="text" size="mini" icon="el-icon-view" @click="openDetail(scope.row)">详情</el-button>
            <el-button v-if="canInput(scope.row)" v-hasPermi="['inv:stockCheck:submit']" type="text" size="mini" icon="el-icon-edit" @click="openInput(scope.row)">录入实盘</el-button>
            <el-button v-if="canAssign(scope.row)" v-hasPermi="['inv:stockCheck:edit']" type="text" size="mini" icon="el-icon-user" @click="openAssignment(scope.row)">调整指派</el-button>
            <template v-if="scope.row.status === 'pending_approval' && canUseUnifiedTask(scope.row)">
              <el-button v-hasPermi="['inv:stockCheck:approve']" type="text" size="mini" class="text-success" icon="el-icon-check" @click="openApproval(scope.row, 'approve')">通过</el-button>
              <el-button v-hasPermi="['inv:stockCheck:approve']" type="text" size="mini" class="text-warning" icon="el-icon-back" @click="openApproval(scope.row, 'return')">退回</el-button>
              <el-button v-hasPermi="['inv:stockCheck:approve']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="openApproval(scope.row, 'reject')">拒绝</el-button>
            </template>
            <template v-else-if="scope.row.status === 'pending_approval' && !isNativeApproval(scope.row)">
              <el-button v-hasPermi="['inv:stockCheck:approve']" type="text" size="mini" class="text-success" icon="el-icon-check" @click="openApproval(scope.row, 'approve')">通过</el-button>
              <el-button v-hasPermi="['inv:stockCheck:approve']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="openApproval(scope.row, 'reject')">驳回</el-button>
            </template>
            <el-button v-if="canRestart(scope.row)" v-hasPermi="['inv:stockCheck:submit']" type="text" size="mini" class="text-warning" icon="el-icon-refresh" @click="handleRestart(scope.row)">重新盘点</el-button>
            <el-button v-if="canWithdrawNative(scope.row)" v-hasPermi="['inv:stockCheck:submit']" type="text" size="mini" class="text-warning" icon="el-icon-refresh-left" :loading="withdrawLoadingId === scope.row.checkId" @click="handleWithdraw(scope.row)">撤回审批</el-button>
            <el-button v-if="canCancel(scope.row)" v-hasPermi="['inv:stockCheck:remove']" type="text" size="mini" class="text-danger" icon="el-icon-close" @click="handleCancel(scope.row)">取消</el-button>
            <el-button v-if="canDeleteDraft(scope.row)" v-hasPermi="['inv:stockCheck:remove']" type="text" size="mini" class="text-danger" icon="el-icon-delete" @click="handleDelete(scope.row)">删除草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <el-dialog :title="formTitle" :visible.sync="formOpen" width="1080px" append-to-body :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="盘点日期" prop="checkDate">
              <el-date-picker v-model="form.checkDate" value-format="yyyy-MM-dd" type="date" placeholder="请选择日期" style="width:100%"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库存组织">
              <el-input :value="currentDeptLabel" disabled/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" v-if="form.checkId">
              <el-tag :type="statusTag(form.status)" size="mini">{{ statusLabel(form.status) }}</el-tag>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="盘点人" :prop="form.checkId ? undefined : 'counterUserId'">
              <el-select
                v-if="!form.checkId"
                v-model="form.counterUserId"
                filterable
                remote
                clearable
                reserve-keyword
                :remote-method="searchCounterCandidates"
                :loading="counterLoading"
                placeholder="请选择有盘点权限的人员"
                style="width:100%"
              >
                <el-option
                  v-for="item in counterOptions"
                  :key="item.userId"
                  :label="counterOptionLabel(item)"
                  :value="item.userId"
                />
              </el-select>
              <el-input v-else :value="form.counterName || '-'" disabled/>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="截止时间" :prop="form.checkId ? undefined : 'deadline'">
              <el-date-picker
                v-model="form.deadline"
                type="datetime"
                value-format="yyyy-MM-dd HH:mm:ss"
                :disabled="Boolean(form.checkId)"
                placeholder="请选择截止时间"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="盲盘">
              <el-switch
                v-model="form.blindCheck"
                active-value="1"
                inactive-value="0"
                :disabled="Boolean(form.checkId)"
                active-text="开启"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="复盘阈值">
              <el-input-number
                v-model="form.recountThreshold"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width:100%"
                @change="handleRecountThresholdChange"
              />
              <div class="field-help">0 表示不强制复盘</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="请输入备注"/>
        </el-form-item>

        <template v-if="!form.checkId">
          <el-form-item label="盘点范围">
            <el-radio-group v-model="createScope" @change="handleCreateScopeChange">
              <el-radio label="all">全部库存</el-radio>
              <el-radio label="category">按商品分类</el-radio>
              <el-radio label="selected">指定商品</el-radio>
              <el-radio label="sample">抽盘</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="createScope === 'category'" label="商品分类" required>
            <el-select
              v-model="selectedCheckCategoryId"
              filterable
              placeholder="选择分类（包含子分类）"
              style="width:100%"
              @focus="loadCategoryOptions"
              @change="loadCreateCategoryProducts"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.categoryId"
                :label="categoryLabel(item)"
                :value="item.categoryId"
              />
            </el-select>
            <div class="scope-selection-tip">已匹配 {{ categoryCheckProductIds.length }} 个有库存商品</div>
          </el-form-item>
          <el-form-item v-if="createScope === 'selected'" label="指定商品" required>
            <el-select
              v-model="selectedCheckProductIds"
              multiple
              filterable
              remote
              reserve-keyword
              :remote-method="searchCheckStockOptions"
              :loading="checkStockLoading"
              placeholder="输入商品名称或编码，可多选"
              style="width:100%"
            >
              <el-option
                v-for="item in checkStockOptions"
                :key="item.productId"
                :label="checkStockOptionLabel(item)"
                :value="item.productId"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="createScope === 'sample'" label="抽盘数量" required>
            <el-input-number
              v-model="sampleSize"
              :min="1"
              :max="1000"
              :step="1"
              :precision="0"
              controls-position="right"
            />
            <div class="scope-selection-tip">系统按盘点单号生成可复现的随机样本，最多 1000 个商品</div>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="创建后会冻结所选范围的账面库存快照"
            description="支持全仓、分类、指定商品和随机抽盘；范围由服务端再次校验并生成快照。"
          />
        </template>

        <template v-if="form.checkId">
          <el-divider content-position="left">盘点明细</el-divider>
          <div class="detail-filter-toolbar">
            <div class="detail-filter-control">
              <span class="detail-filter-label">商品分类</span>
              <el-select
                v-model="formDetailCategoryId"
                filterable
                clearable
                size="small"
                placeholder="全部分类"
                style="width: 260px"
                @focus="loadCategoryOptions"
              >
                <el-option label="全部分类" :value="undefined"/>
                <el-option
                  v-for="item in categoryOptions"
                  :key="item.categoryId"
                  :label="categoryLabel(item)"
                  :value="item.categoryId"
                />
              </el-select>
            </div>
          </div>
          <div class="detail-total-banner">
            <span>总共 {{ filteredFormDetails.length }} 个商品</span>
          </div>
          <el-alert
            v-if="isBlindInputForm"
            class="blind-check-alert"
            type="warning"
            :closable="false"
            show-icon
            title="盲盘已开启"
            description="录入期间不返回账面数量和差异；先保存初盘，系统会标记达到阈值、必须复盘的商品。"
          />
          <el-table :data="filteredFormDetails" size="small" border class="stock-check-detail-table">
            <el-table-column label="商品" min-width="220">
              <template slot-scope="scope">
                <div class="product-cell">
                  <span class="product-name">{{ scope.row.productName || '-' }}</span>
                  <span class="product-meta">{{ productMeta(scope.row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="!isBlindInputForm" label="盘前库存" prop="bookQty" width="110" align="right"/>
            <el-table-column label="初盘数量" width="150" align="right">
              <template slot-scope="scope">
                <el-input-number
                  v-model="scope.row.actualQty"
                  :min="0"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width: 130px"
                  @change="handleActualQtyChange(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="showRecountColumn" label="复盘数量" width="170" align="right">
              <template slot-scope="scope">
                <el-input-number
                  v-if="needsRecount(scope.row)"
                  v-model="scope.row.recountQty"
                  :min="0"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width: 140px"
                  @change="refreshDiff(scope.row)"
                />
                <span v-else class="muted-quantity">无需复盘</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isBlindInputForm" label="盘点差异" width="170" align="right">
              <template slot-scope="scope">
                <div class="loss-qty-cell">
                  <span :class="lossQuantity(scope.row) > 0 ? 'loss-quantity-strong' : 'muted-quantity'">
                    盘亏 {{ formatLossQuantity(scope.row) }}
                  </span>
                  <el-tag v-if="profitQuantity(scope.row) > 0" type="success" size="mini">
                    盘盈 {{ formatQuantity(profitQuantity(scope.row)) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单位" prop="unit" width="80"/>
          </el-table>
        </template>
      </el-form>
      <div slot="footer">
        <el-button @click="formOpen = false">关闭</el-button>
        <el-button v-if="!form.checkId" v-hasPermi="['inv:stockCheck:add']" type="primary" :loading="submitLoading" @click="createDraft">
          创建草稿
        </el-button>
        <template v-else-if="canInput(form)">
          <el-button v-hasPermi="['inv:stockCheck:submit']" :loading="submitLoading" @click="saveActualQty">保存实盘</el-button>
          <el-button v-hasPermi="['inv:stockCheck:submit']" type="primary" :loading="submitLoading" @click="submitActualQty">提交盘点</el-button>
        </template>
      </div>
    </el-dialog>

    <el-dialog title="盘点详情" :visible.sync="detailOpen" width="1080px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="盘点单号">{{ detail.checkNo }}</el-descriptions-item>
        <el-descriptions-item label="盘点日期">{{ detail.checkDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="mini">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="库存组织" :span="2">{{ inventoryOrgLabel(detail, true) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="盘点范围">{{ checkScopeLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="盘点人">{{ detail.counterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ detail.deadline || '-' }}</el-descriptions-item>
        <el-descriptions-item label="盲盘">{{ detail.blindCheck === '1' ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="复盘阈值">{{ formatQuantity(detail.recountThreshold || 0) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="detail.status === 'rejected'"
        class="approval-alert"
        type="error"
        :closable="false"
        show-icon
        title="已驳回（可修改）"
      >
        <div>原因：{{ detail.lastRejectReason || '-' }}</div>
        <div>驳回人：{{ detail.lastRejectedBy || '-' }} · {{ detail.lastRejectedTime || '-' }}</div>
        <div>请修改实盘数据和说明后重新提交。</div>
      </el-alert>
      <el-alert
        v-if="detail.status === 'invalidated'"
        class="approval-alert"
        type="warning"
        :closable="false"
        show-icon
        title="库存已变化（需重盘）"
        :description="detail.lastInvalidReason || '库存快照已变化，请重新盘点'"
      />
      <el-divider content-position="left">盘点明细</el-divider>
      <div class="detail-filter-toolbar">
        <div class="detail-filter-control">
          <span class="detail-filter-label">商品分类</span>
          <el-select
            v-model="detailDetailCategoryId"
            filterable
            clearable
            size="small"
            placeholder="全部分类"
            style="width: 260px"
            @focus="loadCategoryOptions"
          >
            <el-option label="全部分类" :value="undefined"/>
            <el-option
              v-for="item in categoryOptions"
              :key="item.categoryId"
              :label="categoryLabel(item)"
              :value="item.categoryId"
            />
          </el-select>
        </div>
      </div>
      <div class="detail-total-banner">
        <span>总共 {{ filteredDetailDetails.length }} 个商品</span>
      </div>
      <el-table :data="filteredDetailDetails" size="small" border class="stock-check-detail-table">
        <el-table-column label="商品" min-width="220">
          <template slot-scope="scope">
            <div class="product-cell">
              <span class="product-name">{{ scope.row.productName || '-' }}</span>
              <span class="product-meta">{{ productMeta(scope.row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="盘前库存" prop="bookQty" width="110" align="right"/>
        <el-table-column label="初盘数量" prop="actualQty" width="110" align="right"/>
        <el-table-column label="复盘数量" width="110" align="right">
          <template slot-scope="scope">
            <span v-if="scope.row.recountRequired === '1'">{{ formatQuantity(scope.row.recountQty) }}</span>
            <span v-else class="muted-quantity">无需复盘</span>
          </template>
        </el-table-column>
        <el-table-column label="盘点差异" width="170" align="right">
          <template slot-scope="scope">
            <div class="loss-qty-cell">
              <span :class="lossQuantity(scope.row) > 0 ? 'loss-quantity-strong' : 'muted-quantity'">
                盘亏 {{ formatLossQuantity(scope.row) }}
              </span>
              <el-tag v-if="profitQuantity(scope.row) > 0" type="success" size="mini">
                盘盈 {{ formatQuantity(profitQuantity(scope.row)) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="80"/>
      </el-table>
      <el-divider content-position="left">审批轨迹</el-divider>
      <unified-approval-progress
        v-if="isNativeApproval(detail)"
        :detail="unifiedApprovalDetail"
        :loading="unifiedApprovalLoading"
        :error="unifiedApprovalError"
        @retry="loadStockCheckApproval(detail)"
      />
      <el-empty v-else-if="!approvalTrack.length" description="暂无审批记录" :image-size="64"/>
      <el-timeline v-else class="approval-track">
        <el-timeline-item
          v-for="instance in approvalTrack"
          :key="instance.instanceId"
          :timestamp="instance.finishedTime || instance.submittedTime"
          placement="top"
        >
          <el-card shadow="never">
            <div class="approval-round-title">
              <strong>第 {{ instance.roundNo }} 轮 · {{ approvalStatusLabel(instance.status) }}</strong>
              <span>提交人：{{ instance.submittedBy || '-' }}</span>
            </div>
            <div v-for="task in (instance.tasks || [])" :key="task.taskId" class="approval-task-row">
              <span>候选：{{ task.candidateUserNames || '-' }}</span>
              <span>审批人：{{ task.approverName || '-' }}</span>
              <el-tag v-if="task.selfApproved === '1'" type="warning" size="mini">自审</el-tag>
              <span>意见：{{ task.approvalComment || '-' }}</span>
            </div>
            <div v-if="detailSnapshotRows(instance).length" class="approval-evidence-block">
              <strong>提交冻结明细</strong>
              <el-table :data="detailSnapshotRows(instance)" size="mini" border>
                <el-table-column label="商品" prop="productName" min-width="160"/>
                <el-table-column label="账面" prop="bookQty" width="90" align="right"/>
                <el-table-column label="实盘" prop="actualQty" width="90" align="right"/>
                <el-table-column label="差异" prop="diffQty" width="90" align="right"/>
              </el-table>
            </div>
            <div v-if="invalidSnapshotRows(instance).length" class="approval-evidence-block">
              <strong>库存失效证据</strong>
              <el-table :data="invalidSnapshotRows(instance)" size="mini" border>
                <el-table-column label="商品" prop="productName" min-width="160"/>
                <el-table-column label="提交时库存" prop="bookQuantity" width="110" align="right"/>
                <el-table-column label="审批时库存" prop="currentQuantity" width="110" align="right"/>
              </el-table>
            </div>
            <div v-if="adjustmentResultRows(instance).length" class="approval-evidence-block">
              <strong>库存调整结果</strong>
              <el-table :data="adjustmentResultRows(instance)" size="mini" border>
                <el-table-column label="商品" prop="productName" min-width="160"/>
                <el-table-column label="调整前" prop="beforeQuantity" width="90" align="right"/>
                <el-table-column label="变化" prop="changeQuantity" width="90" align="right"/>
                <el-table-column label="调整后" prop="afterQuantity" width="90" align="right"/>
              </el-table>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <div slot="footer">
        <el-button @click="detailOpen = false">关闭</el-button>
        <el-button v-if="canHandleUnifiedApproval(detail)" v-hasPermi="['inv:stockCheck:approve']" type="success" @click="openApproval(detail, 'approve')">同意</el-button>
        <el-button v-if="canHandleUnifiedApproval(detail)" v-hasPermi="['inv:stockCheck:approve']" type="warning" @click="openApproval(detail, 'return')">退回修改</el-button>
        <el-button v-if="canHandleUnifiedApproval(detail)" v-hasPermi="['inv:stockCheck:approve']" type="danger" @click="openApproval(detail, 'reject')">拒绝</el-button>
        <el-button v-if="canAssign(detail)" v-hasPermi="['inv:stockCheck:edit']" @click="openAssignment(detail)">调整指派</el-button>
        <el-button v-if="['returned', 'rejected'].includes(detail.status) && canInput(detail)" v-hasPermi="['inv:stockCheck:submit']" type="primary" @click="editRejectedFromDetail">修改后重新提交</el-button>
        <el-button v-if="canRestart(detail)" v-hasPermi="['inv:stockCheck:submit']" type="warning" @click="handleRestart(detail)">重新盘点</el-button>
        <el-button v-if="canWithdrawNative(detail)" v-hasPermi="['inv:stockCheck:submit']" type="warning" plain :loading="withdrawLoadingId === detail.checkId" @click="handleWithdraw(detail)">撤回审批</el-button>
      </div>
    </el-dialog>

    <el-dialog title="调整盘点指派" :visible.sync="assignmentOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="assignmentRef" :model="assignmentForm" :rules="assignmentRules" label-width="96px">
        <el-form-item label="盘点单号">
          <el-input :value="assignmentCheck.checkNo || '-'" disabled/>
        </el-form-item>
        <el-form-item label="盘点人" prop="counterUserId">
          <el-select
            v-model="assignmentForm.counterUserId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchAssignmentCandidates"
            :loading="counterLoading"
            placeholder="请选择有盘点权限的人员"
            style="width:100%"
          >
            <el-option
              v-for="item in counterOptions"
              :key="item.userId"
              :label="counterOptionLabel(item)"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="assignmentForm.deadline"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            placeholder="请选择新的截止时间"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="assignmentOpen = false">取消</el-button>
        <el-button type="primary" :loading="assignmentLoading" @click="submitAssignment">保存指派</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="approvalDialogTitle" :visible.sync="approvalOpen" width="620px" append-to-body :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="盘点单号">{{ approvalCheck.checkNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存组织">{{ inventoryOrgLabel(approvalCheck) }}</el-descriptions-item>
        <el-descriptions-item label="盘亏数量">{{ formatQuantity(buildStockCheckSummary(approvalCheck.details).lossQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="盘盈数量">{{ formatQuantity(buildStockCheckSummary(approvalCheck.details).profitQuantity) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="84px" class="approval-form">
        <el-form-item :label="approvalReasonLabel" :required="approvalAction !== 'approve'">
          <el-input v-model="approvalForm.comment" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="approvalReasonPlaceholder"/>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="approvalOpen = false">取消</el-button>
        <el-button :type="approvalActionType" :loading="approvalLoading" @click="submitApproval">
          确认{{ approvalActionLabel }}
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listStockCheck,
  getStockCheck,
  createStockCheck,
  listStockCheckCounterCandidates,
  assignStockCheck,
  inputStockCheck,
  submitStockCheck,
  restartStockCheck,
  listStockCheckApprovalTodos,
  approveStockCheck,
  rejectStockCheck,
  getStockCheckApprovalTrack,
  cancelStockCheck,
  withdrawStockCheck,
  deleteStockCheck
} from "@/api/inventory/stockCheck"
import { categoryTree } from "@/api/inventory/category"
import { getProduct } from "@/api/inventory/product"
import { listStock } from "@/api/inventory/stock"
import { getApprovalInstance } from "@/api/approval/monitor"
import { approveApprovalTask, rejectApprovalTask, returnApprovalTask } from "@/api/approval/task"
import { confirmExportAction } from "@/utils/exportConfirm"
import { formatInventoryDeptLabel, getSelectedDeptContext, getSelectedDeptId } from "@/utils/shopContext"
import UnifiedApprovalProgress from "@/views/inventory/components/UnifiedApprovalProgress"
import { unwrapData } from "@/views/approval/manage/components/approvalUi"
const {
  applyActualQtyChange,
  buildStockCheckSummary,
  decorateCategories,
  effectiveActualQty,
  filterDetailsByCategory: filterStockCheckDetailsByCategory,
  flattenCategories,
  formatQuantity,
  hasDetailCategoryMetadata,
  isActualQtyMissing,
  lossQuantity,
  normalizeDetail,
  numberValue,
  profitQuantity,
  refreshDetailDiff,
  requiresRecount,
  resolveDiffType,
  rowLossQuantity
} = require("./stockCheckDetailRules")
const { createTodoBusinessFocusMixin } = require("@/mixins/todoBusinessFocus")

export default {
  mixins: [createTodoBusinessFocusMixin({
    featureKey: "stockCheck",
    loadFocusedRow(checkId) { return getStockCheck(checkId) },
    actions: {
      approveStockCheck(row) {
        if (!row || row.status !== "pending_approval") return this.showTodoBusinessHandled()
        this.openApproval(row, "approve")
      },
      saveStockCheckInput(row) {
        if (!this.canInput(row)) return this.showTodoBusinessHandled()
        this.openInput(row)
      },
      restartStockCheck(row) {
        if (!row || row.status !== "invalidated") return this.showTodoBusinessHandled()
        this.handleRestart(row)
      }
    }
  })],
  name: "InvStockCheck",
  components: { UnifiedApprovalProgress },
  data() {
    return {
      loading: false,
      submitLoading: false,
      list: [],
      total: 0,
      dateRange: [],
      formOpen: false,
      detailOpen: false,
      assignmentOpen: false,
      assignmentLoading: false,
      assignmentCheck: {},
      assignmentForm: { counterUserId: undefined, deadline: undefined },
      approvalOpen: false,
      approvalLoading: false,
      withdrawLoadingId: undefined,
      approvalAction: "approve",
      approvalCheck: { details: [] },
      approvalForm: { instanceId: undefined, taskId: undefined, engineMode: "LEGACY", comment: "" },
      approvalTrack: [],
      unifiedApprovalDetail: null,
      unifiedApprovalLoading: false,
      unifiedApprovalError: false,
      todoMode: false,
      categoryOptions: [],
      createScope: "all",
      selectedCheckCategoryId: undefined,
      categoryCheckProductIds: [],
      selectedCheckProductIds: [],
      checkStockOptions: [],
      checkStockLoading: false,
      counterLoading: false,
      counterOptions: [],
      sampleSize: 20,
      productCategoryCache: {},
      formDetailCategoryId: undefined,
      detailDetailCategoryId: undefined,
      detail: { details: [] },
      form: this.emptyForm(),
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        checkNo: undefined,
        status: undefined,
        warehouseId: undefined
      },
      statusOptions: [
        { label: "草稿", value: "draft" },
        { label: "待审批", value: "pending_approval" },
        { label: "已退回（可修改）", value: "returned" },
        { label: "已驳回（可修改）", value: "rejected" },
        { label: "库存已变化（需重盘）", value: "invalidated" },
        { label: "已完成", value: "completed" },
        { label: "已取消", value: "cancelled" }
      ],
      rules: {
        checkDate: [{ required: true, message: "请选择盘点日期", trigger: "change" }],
        counterUserId: [{ required: true, message: "请选择盘点人", trigger: "change" }],
        deadline: [{ required: true, message: "请选择盘点截止时间", trigger: "change" }]
      },
      assignmentRules: {
        counterUserId: [{ required: true, message: "请选择盘点人", trigger: "change" }],
        deadline: [{ required: true, message: "请选择盘点截止时间", trigger: "change" }]
      }
    }
  },
  computed: {
    currentDeptId() {
      return getSelectedDeptId()
    },
    currentDeptContext() {
      return getSelectedDeptContext()
    },
    currentUserId() {
      return this.$store && this.$store.getters ? this.$store.getters.id : undefined
    },
    currentDeptLabel() {
      return formatInventoryDeptLabel(this.currentDeptContext, { fallback: "当前组织" })
    },
    formTitle() {
      if (!this.form.checkId) return "新增盘点"
      return this.form.status === "draft" || this.form.status === "rejected" ? "录入实盘" : "盘点单"
    },
    pageLossQuantity() {
      return (this.list || []).reduce((sum, row) => sum + this.rowLossQuantity(row), 0)
    },
    pagePendingApprovalCount() {
      return (this.list || []).filter(row => row.status === "pending_approval").length
    },
    pageCompletedCount() {
      return (this.list || []).filter(row => row.status === "completed").length
    },
    filteredFormDetails() {
      return this.filterDetailsByCategory(this.form.details, this.formDetailCategoryId)
    },
    filteredDetailDetails() {
      return this.filterDetailsByCategory(this.detail.details, this.detailDetailCategoryId)
    },
    isBlindInputForm() {
      return this.form && this.form.blindCheck === "1" &&
        (this.form.status === "draft" || this.form.status === "rejected")
    },
    showRecountColumn() {
      return Number(this.form && this.form.recountThreshold || 0) > 0 ||
        (this.form.details || []).some(item => item.recountRequired === "1")
    },
    approvalDialogTitle() {
      return this.approvalAction === "return"
        ? "退回盘点审批"
        : this.approvalAction === "reject" ? "拒绝盘点审批" : "通过盘点审批"
    },
    approvalActionLabel() {
      return this.approvalAction === "return" ? "退回" : this.approvalAction === "reject" ? "拒绝" : "通过"
    },
    approvalActionType() {
      return this.approvalAction === "return" ? "warning" : this.approvalAction === "reject" ? "danger" : "primary"
    },
    approvalReasonLabel() {
      return this.approvalAction === "return" ? "退回原因" : this.approvalAction === "reject" ? "拒绝原因" : "审批意见"
    },
    approvalReasonPlaceholder() {
      return this.approvalAction === "return"
        ? "请输入退回修改原因"
        : this.approvalAction === "reject" ? "请输入拒绝原因" : "可填写复核意见"
    }
  },
  created() {
    this.getList()
  },
  methods: {
    emptyForm() {
      const currentDeptId = getSelectedDeptId()
      return {
        checkId: undefined,
        checkNo: "",
        checkDate: this.parseTime(new Date(), "{y}-{m}-{d}"),
        shopDeptId: currentDeptId,
        warehouseId: currentDeptId,
        checkScope: "all",
        categoryId: undefined,
        sampleSize: undefined,
        blindCheck: "0",
        counterUserId: undefined,
        counterName: "",
        deadline: undefined,
        recountThreshold: 1,
        status: "draft",
        remark: "",
        details: []
      }
    },
    getList() {
      this.loading = true
      const loader = this.todoMode ? listStockCheckApprovalTodos : listStockCheck
      return this.loadTodoBusinessList(() => loader(
        this.addDateRange(this.queryParams, this.dateRange, "CheckDate")
      )).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
        return this.handleTodoFocusRows(this.list)
      }).finally(() => {
        this.loading = false
      })
    },
    resetQuery() {
      this.dateRange = []
      this.todoMode = false
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        checkNo: undefined,
        status: undefined,
        warehouseId: undefined
      }
      this.getList()
    },
    loadApprovalTodos() {
      this.todoMode = !this.todoMode
      this.queryParams.pageNum = 1
      this.queryParams.status = this.todoMode ? "pending_approval" : undefined
      this.getList()
    },
    openCreate() {
      if (!this.ensureSelectedContext()) return
      this.form = this.emptyForm()
      this.counterOptions = []
      this.createScope = "all"
      this.selectedCheckCategoryId = undefined
      this.categoryCheckProductIds = []
      this.selectedCheckProductIds = []
      this.checkStockOptions = []
      this.sampleSize = 20
      this.formDetailCategoryId = undefined
      this.formOpen = true
      this.searchCounterCandidates("")
      this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate())
    },
    openInput(row) {
      if (!this.ensureWritableCheck(row)) return
      getStockCheck(row.checkId).then(res => {
        this.form = Object.assign(this.emptyForm(), res.data || {})
        this.form.details = (this.form.details || []).map(item => this.normalizeDetail(item))
        this.formDetailCategoryId = undefined
        this.loadCategoryOptions()
        this.hydrateDetailCategories(this.form.details)
        this.formOpen = true
        this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate())
      })
    },
    openDetail(row) {
      return getStockCheck(row.checkId).then(res => {
        this.detail = Object.assign({ details: [] }, row || {}, res.data || {})
        this.detail.details = (this.detail.details || []).map(item => this.normalizeDetail(item))
        this.detailDetailCategoryId = undefined
        this.loadCategoryOptions()
        this.hydrateDetailCategories(this.detail.details)
        this.detailOpen = true
        return this.loadStockCheckApproval(this.detail)
      })
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
    canWithdrawNative(row) {
      if (!row || row.status !== "pending_approval" || !this.isNativeApproval(row) || !this.isWritableCheck(row)) {
        return false
      }
      if (row.submittedUserId !== undefined && row.submittedUserId !== null) {
        return String(row.submittedUserId) === String(this.currentUserId)
      }
      const instance = this.unifiedApprovalDetail && this.unifiedApprovalDetail.instance || {}
      const isCurrentDetail = this.detail && String(this.detail.checkId || "") === String(row.checkId || "")
      return isCurrentDetail && instance.applicantUserId !== undefined && instance.applicantUserId !== null &&
        String(instance.applicantUserId) === String(this.currentUserId)
    },
    loadStockCheckApproval(row) {
      const source = row || {}
      this.approvalTrack = []
      this.unifiedApprovalDetail = null
      this.unifiedApprovalError = false
      if (this.isNativeApproval(source)) {
        const instanceId = source.approvalInstanceId || this.approvalRouteContext().instanceId
        if (!instanceId) {
          this.unifiedApprovalError = true
          return Promise.resolve(null)
        }
        this.unifiedApprovalLoading = true
        return getApprovalInstance(instanceId).then(response => {
          this.unifiedApprovalDetail = unwrapData(response)
          return this.unifiedApprovalDetail
        }).catch(() => {
          this.unifiedApprovalError = true
          return null
        }).finally(() => {
          this.unifiedApprovalLoading = false
        })
      }
      this.unifiedApprovalLoading = false
      return getStockCheckApprovalTrack(source.checkId).then(response => {
        this.approvalTrack = response.data || []
        return this.approvalTrack
      }).catch(() => {
        this.approvalTrack = []
        return []
      })
    },
    editRejectedFromDetail() {
      const row = Object.assign({}, this.detail)
      this.detailOpen = false
      this.openInput(row)
    },
    loadCounterCandidates(keyword, warehouseId) {
      const inventoryDeptId = warehouseId || this.currentDeptId
      if (!inventoryDeptId) {
        this.counterOptions = []
        return Promise.resolve([])
      }
      this.counterLoading = true
      return listStockCheckCounterCandidates({
        warehouseId: inventoryDeptId,
        keyword: String(keyword || "").trim() || undefined
      }).then(res => {
        this.counterOptions = (res.data || []).slice()
        return this.counterOptions
      }).finally(() => {
        this.counterLoading = false
      })
    },
    searchCounterCandidates(keyword) {
      return this.loadCounterCandidates(keyword, this.form.warehouseId || this.currentDeptId)
    },
    searchAssignmentCandidates(keyword) {
      return this.loadCounterCandidates(keyword, this.inventoryDeptId(this.assignmentCheck))
    },
    counterOptionLabel(item) {
      if (!item) return "-"
      const displayName = item.displayName || item.userName || "姓名未配置"
      return item.userName && item.userName !== displayName
        ? displayName + "（" + item.userName + "）"
        : displayName
    },
    openAssignment(row) {
      if (!this.canAssign(row) || !this.ensureWritableCheck(row)) return
      this.assignmentCheck = Object.assign({}, row)
      this.assignmentForm = {
        counterUserId: row.counterUserId,
        deadline: row.deadline
      }
      this.counterOptions = []
      this.assignmentOpen = true
      this.searchAssignmentCandidates("")
      this.$nextTick(() => this.$refs.assignmentRef && this.$refs.assignmentRef.clearValidate())
    },
    submitAssignment() {
      this.$refs.assignmentRef.validate(valid => {
        if (!valid) return
        this.assignmentLoading = true
        assignStockCheck(this.assignmentCheck.checkId, this.assignmentForm).then(res => {
          this.$modal.msgSuccess("盘点指派已更新")
          this.assignmentOpen = false
          if (this.detailOpen && res.data) {
            this.detail = Object.assign({}, this.detail, res.data)
          }
          this.getList()
        }).finally(() => {
          this.assignmentLoading = false
        })
      })
    },
    createDraft() {
      if (!this.ensureSelectedContext()) return
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        if (this.createScope === "category" && !this.selectedCheckCategoryId) {
          this.$modal.msgError("请选择盘点商品分类")
          return
        }
        if (this.createScope === "selected" && this.selectedCheckProductIds.length === 0) {
          this.$modal.msgError("请至少选择一个盘点商品")
          return
        }
        if (this.createScope === "sample" && (!this.sampleSize || this.sampleSize < 1 || this.sampleSize > 1000)) {
          this.$modal.msgError("抽盘数量必须在 1 到 1000 之间")
          return
        }
        this.submitLoading = true
        createStockCheck(this.buildCreatePayload()).then(res => {
          this.$modal.msgSuccess("盘点草稿已创建，已通知指定盘点人")
          this.form = Object.assign(this.emptyForm(), res.data || {})
          this.form.details = (this.form.details || []).map(item => this.normalizeDetail(item))
          this.formDetailCategoryId = undefined
          this.loadCategoryOptions()
          this.hydrateDetailCategories(this.form.details)
          this.getList()
        }).finally(() => {
          this.submitLoading = false
        })
      })
    },
    saveActualQty() {
      if (!this.canInput(this.form) || !this.ensureWritableCheck(this.form)) return
      this.$refs.formRef.validate(valid => {
        if (!valid || !this.validateDetails(false)) return
        this.submitLoading = true
        inputStockCheck(this.form.checkId, this.buildInputPayload()).then(res => {
          this.$modal.msgSuccess("实盘数据已保存")
          this.form = Object.assign(this.emptyForm(), res.data || this.form)
          this.form.details = (this.form.details || []).map(item => this.normalizeDetail(item))
          this.getList()
        }).finally(() => {
          this.submitLoading = false
        })
      })
    },
    submitActualQty() {
      if (!this.canInput(this.form) || !this.ensureWritableCheck(this.form)) return
      this.$refs.formRef.validate(valid => {
        if (!valid || !this.validateDetails(true)) return
        const summary = this.buildStockCheckSummary(this.form.details)
        this.$modal.confirm(this.getStockCheckSubmitMessage(this.form, summary)).then(() => {
          this.submitLoading = true
          return inputStockCheck(this.form.checkId, this.buildInputPayload())
        }).then(res => {
          this.form = Object.assign(this.emptyForm(), res.data || this.form)
          this.form.details = (this.form.details || []).map(item => this.normalizeDetail(item))
          if (!this.validateSubmitDetails(this.form.details)) {
            this.$modal.msgWarning("初盘已保存，请完成系统标记的复盘行后再提交")
            return null
          }
          return submitStockCheck(this.form.checkId)
        }).then(res => {
          if (!res) return
          const result = res.data || {}
          const message = result.status === "completed"
            ? "无差异，盘点已自动完成"
            : result.status === "invalidated"
              ? "库存已变化，请重新盘点"
              : "盘点已提交审批"
          this.$modal.msgSuccess(message)
          this.formOpen = false
          this.getList()
        }).finally(() => {
          this.submitLoading = false
        })
      })
    },
    buildInputPayload() {
      return {
        checkDate: this.form.checkDate,
        recountThreshold: this.form.recountThreshold,
        remark: this.form.remark,
        details: this.form.details.map(item => ({
          detailId: item.detailId,
          actualQty: item.actualQty,
          recountQty: item.recountQty
        }))
      }
    },
    openApproval(row, action) {
      getStockCheck(row.checkId).then(res => {
        this.approvalCheck = Object.assign({ details: [] }, row, res.data || {})
        this.approvalCheck.details = (this.approvalCheck.details || []).map(item => this.normalizeDetail(item))
        this.approvalAction = action
        const native = this.isNativeApproval(this.approvalCheck)
        const taskId = native ? this.unifiedTaskId(this.approvalCheck) : ""
        const prepare = native ? this.loadStockCheckApproval(this.approvalCheck) : Promise.resolve()
        return prepare.then(() => {
          if (native && (!taskId || !this.canHandleUnifiedApproval(this.approvalCheck))) {
            this.$modal.msgWarning(taskId ? "该统一审批待办已处理或不再属于当前用户" : "请从工作台待办进入统一审批")
            return
          }
          this.approvalForm = {
            instanceId: this.approvalCheck.approvalInstanceId || this.approvalRouteContext().instanceId,
            taskId,
            engineMode: native ? "NATIVE" : "LEGACY",
            comment: ""
          }
          this.approvalOpen = true
        })
      })
    },
    submitApproval() {
      const native = this.approvalForm.engineMode === "NATIVE"
      if (native ? !this.approvalForm.taskId : !this.approvalForm.instanceId) {
        this.$modal.msgError(native ? "当前统一审批任务不存在，请从工作台重新进入" : "当前审批实例不存在，请刷新后重试")
        return
      }
      const reason = String(this.approvalForm.comment || "").trim()
      if (this.approvalAction !== "approve" && !reason) {
        this.$modal.msgError(`请输入${this.approvalAction === "return" ? "退回" : "拒绝"}原因`)
        return
      }
      let request
      if (native) {
        const taskAction = this.approvalAction === "approve"
          ? approveApprovalTask
          : this.approvalAction === "return" ? returnApprovalTask : rejectApprovalTask
        request = taskAction(this.approvalForm.taskId, {
          requestId: `INV_STOCK_CHECK:${this.approvalForm.taskId}:${this.approvalAction}:${Date.now()}`,
          reason
        })
      } else {
        const legacyAction = this.approvalAction === "reject" ? rejectStockCheck : approveStockCheck
        request = legacyAction(this.approvalCheck.checkId, {
          instanceId: this.approvalForm.instanceId,
          comment: reason
        })
      }
      this.approvalLoading = true
      request.then(() => {
        this.$modal.msgSuccess(native
          ? "统一审批动作已提交"
          : this.approvalAction === "reject" ? "已驳回，可修改后重新提交" : "审批通过，库存已更新")
        this.approvalOpen = false
        if (this.$store && typeof this.$store.dispatch === "function") {
          this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
        }
        this.getList()
      }).finally(() => {
        this.approvalLoading = false
      })
    },
    handleRestart(row) {
      if (!this.canRestart(row) || !this.ensureWritableCheck(row)) return
      this.$modal.confirm("重新盘点将刷新账面库存并清空已录入的实盘数量，是否继续？").then(() => {
        restartStockCheck(row.checkId).then(() => {
          this.$modal.msgSuccess("库存快照已刷新，请重新录入实盘数量")
          this.detailOpen = false
          this.getList()
        })
      })
    },
    handleWithdraw(row) {
      if (!this.canWithdrawNative(row) || this.withdrawLoadingId) {
        if (!this.withdrawLoadingId) this.$modal.msgWarning("只有当前申请人可以撤回统一审批中的盘点单")
        return
      }
      this.$modal.confirm("确认撤回盘点审批？系统将使用默认原因“申请人撤回盘点审批”，业务状态将在审批回调后恢复为草稿。", "撤回审批").then(() => {
        this.withdrawLoadingId = row.checkId
        return withdrawStockCheck(row.checkId).then(() => {
          this.$modal.msgSuccess("撤回请求已提交")
          this.detailOpen = false
          if (this.$store && typeof this.$store.dispatch === "function") {
            this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
          }
          return this.getList()
        }).finally(() => {
          this.withdrawLoadingId = undefined
        })
      }).catch(() => {})
    },
    handleCancel(row) {
      if (!this.ensureWritableCheck(row)) return
      this.$modal.confirm("确认取消盘点单 [" + row.checkNo + "]?").then(() => {
        cancelStockCheck(row.checkId).then(res => {
          this.$modal.msgSuccess(res.msg || "已取消")
          this.getList()
        })
      })
    },
    handleDelete(row) {
      if (!this.ensureWritableCheck(row)) return
      this.$modal.confirm("确认删除盘点草稿 [" + row.checkNo + "]?").then(() => {
        deleteStockCheck(row.checkId).then(res => {
          this.$modal.msgSuccess(res.msg || "删除草稿成功")
          this.getList()
        })
      })
    },
    handleExport() {
      confirmExportAction(this, {
        moduleName: "库存盘点数据",
        rangeLabel: "当前查询条件下的盘点单数据",
        filterLabel: this.getStockCheckExportFilterLabel()
      }).then(() => {
        this.download(
          "inventory/stockCheck/export",
          this.addDateRange({ ...this.queryParams }, this.dateRange, "CheckDate"),
          this.exportFileName("库存盘点数据", this.dateRange)
        )
      })
    },
    inventoryOrgLabel(row, showId = false) {
      return formatInventoryDeptLabel(row, { showId })
    },
    getStockCheckExportFilterLabel() {
      return [
        "库存组织：" + this.currentDeptLabel,
        "盘点单号：" + (this.queryParams.checkNo || "全部"),
        "状态：" + (this.queryParams.status || "全部"),
        "盘点日期：" + (this.dateRange && this.dateRange.length === 2 ? this.dateRange.join(" 至 ") : "全部")
      ].join("；")
    },
    getStockCheckSubmitMessage(check, summary) {
      if (check && check.blindCheck === "1") {
        return [
          "提交盲盘并由系统计算差异？",
          "盘点单：" + (check.checkNo || "-"),
          "商品数：" + summary.itemCount,
          "盘亏数量：提交后可见",
          "盘盈数量：提交后可见",
          "达到复盘阈值的商品必须先完成复盘。"
        ].join("\n")
      }
      return [
        "提交盘点并进入差异审批？",
        "盘点单：" + (check.checkNo || "-"),
        "商品数：" + summary.itemCount,
        "盘亏数量：" + this.formatQuantity(summary.lossQuantity),
        "盘盈数量：" + this.formatQuantity(summary.profitQuantity),
        "无差异将自动完成，有差异将提交运营总监审批。"
      ].join("\n")
    },
    buildCreatePayload() {
      const productIds = this.createScope === "selected" ? this.selectedCheckProductIds : []
      return {
        checkDate: this.form.checkDate,
        warehouseId: this.currentDeptId,
        checkScope: this.createScope,
        categoryId: this.createScope === "category" ? this.selectedCheckCategoryId : undefined,
        sampleSize: this.createScope === "sample" ? this.sampleSize : undefined,
        blindCheck: this.form.blindCheck,
        counterUserId: this.form.counterUserId,
        deadline: this.form.deadline,
        recountThreshold: this.form.recountThreshold,
        remark: this.form.remark,
        details: productIds.map(productId => ({ productId }))
      }
    },
    handleCreateScopeChange() {
      this.selectedCheckCategoryId = undefined
      this.categoryCheckProductIds = []
      this.selectedCheckProductIds = []
      this.sampleSize = 20
      if (this.createScope === "category") {
        this.loadCategoryOptions()
      } else if (this.createScope === "selected") {
        this.searchCheckStockOptions("")
      }
    },
    loadCreateCategoryProducts(categoryId) {
      this.categoryCheckProductIds = []
      if (!categoryId) return
      this.checkStockLoading = true
      listStock({
        pageNum: 1,
        pageSize: 1000,
        itemType: "product",
        categoryId,
        ownOnly: true
      }).then(res => {
        this.categoryCheckProductIds = Array.from(new Set((res.rows || [])
          .map(item => item.productId)
          .filter(productId => productId !== undefined && productId !== null)))
      }).finally(() => {
        this.checkStockLoading = false
      })
    },
    searchCheckStockOptions(keyword) {
      this.checkStockLoading = true
      listStock({
        pageNum: 1,
        pageSize: 50,
        itemType: "product",
        productName: keyword || undefined,
        ownOnly: true
      }).then(res => {
        const optionMap = {}
        ;(this.checkStockOptions || []).forEach(item => { optionMap[String(item.productId)] = item })
        ;(res.rows || []).forEach(item => {
          if (item.productId !== undefined && item.productId !== null) {
            optionMap[String(item.productId)] = item
          }
        })
        this.checkStockOptions = Object.values(optionMap)
      }).finally(() => {
        this.checkStockLoading = false
      })
    },
    checkStockOptionLabel(item) {
      const code = item.productCode || item.itemCode || "-"
      const name = item.productName || item.itemName || "-"
      const quantity = this.formatQuantity(item.currentQuantity)
      const unit = item.unit || item.itemUnit || ""
      return code + " / " + name + " / 库存 " + quantity + unit
    },
    loadCategoryOptions() {
      if (this.categoryOptions.length > 0) return
      categoryTree().then(res => {
        this.categoryOptions = this.flattenCategories(this.decorateCategories(res.data || []))
      }).catch(() => {
        this.categoryOptions = []
      })
    },
    decorateCategories,
    flattenCategories,
    categoryLabel(item) {
      return item ? (item.categoryFullPath || item.categoryName || "-") : "-"
    },
    filterDetailsByCategory(details, categoryId) {
      return filterStockCheckDetailsByCategory(details, categoryId, this.categoryOptions)
    },
    hydrateDetailCategories(details) {
      const rows = Array.isArray(details) ? details : []
      const missingIds = []
      rows.forEach(row => {
        if (!row || !row.productId || this.hasDetailCategoryMetadata(row)) return
        const productId = String(row.productId)
        if (this.productCategoryCache[productId]) {
          this.applyProductCategoryMetadata(row, this.productCategoryCache[productId])
          return
        }
        if (!missingIds.includes(productId)) {
          missingIds.push(productId)
        }
      })
      missingIds.forEach(productId => {
        getProduct(productId).then(res => {
          const product = res.data || {}
          const metadata = {
            categoryId: product.categoryId,
            categoryName: product.categoryName,
            categoryFullPath: product.categoryFullPath
          }
          this.$set(this.productCategoryCache, productId, metadata)
          rows
            .filter(row => row && String(row.productId) === productId)
            .forEach(row => this.applyProductCategoryMetadata(row, metadata))
        }).catch(() => {})
      })
    },
    hasDetailCategoryMetadata,
    applyProductCategoryMetadata(row, metadata) {
      if (!row || !metadata) return
      if (metadata.categoryId !== null && metadata.categoryId !== undefined) {
        this.$set(row, "categoryId", metadata.categoryId)
      }
      if (metadata.categoryName) {
        this.$set(row, "categoryName", metadata.categoryName)
      }
      if (metadata.categoryFullPath) {
        this.$set(row, "categoryFullPath", metadata.categoryFullPath)
      }
    },
    validateDetails(requireAll) {
      if (!this.form.details || this.form.details.length === 0) {
        this.$modal.msgError("盘点单无明细")
        return false
      }
      const negative = this.form.details.find(item => item.actualQty !== null && item.actualQty !== undefined && Number(item.actualQty) < 0)
      if (negative) {
        this.$modal.msgError("实盘数量不能为负")
        return false
      }
      const negativeRecount = this.form.details.find(item => item.recountQty !== null && item.recountQty !== undefined && Number(item.recountQty) < 0)
      if (negativeRecount) {
        this.$modal.msgError("复盘数量不能为负")
        return false
      }
      if (requireAll && !this.validateSubmitDetails(this.form.details)) {
        return false
      }
      return true
    },
    validateSubmitDetails(details) {
      const missing = (details || []).find(item => item.actualQty === null || item.actualQty === undefined || item.actualQty === "")
      if (missing) {
        this.$modal.msgError("提交前请录入所有明细的实盘数量")
        return false
      }
      const missingRecount = (details || []).find(item => this.needsRecount(item) &&
        (item.recountQty === null || item.recountQty === undefined || item.recountQty === ""))
      if (missingRecount) {
        this.$modal.msgError("商品 [" + (missingRecount.productName || "-") + "] 达到复盘阈值，请录入复盘数量")
        return false
      }
      return true
    },
    normalizeDetail,
    refreshDiff: refreshDetailDiff,
    handleActualQtyChange(row) {
      applyActualQtyChange(row, this.form && this.form.recountThreshold)
    },
    handleRecountThresholdChange() {
      ;(this.form.details || []).forEach(row => this.handleActualQtyChange(row))
    },
    requiresRecountByValues(row) {
      return requiresRecount(row, this.form && this.form.recountThreshold)
    },
    needsRecount(row) {
      return this.requiresRecountByValues(row)
    },
    effectiveActualQty,
    productMeta(row) {
      const parts = [row.categoryFullPath || row.categoryName, row.productCode, row.spec].filter(item => item)
      return parts.length ? parts.join(" / ") : "-"
    },
    buildStockCheckSummary,
    isActualQtyMissing,
    numberValue,
    lossQuantity,
    profitQuantity,
    rowLossQuantity,
    formatLossQuantity(row) {
      const qty = this.lossQuantity(row)
      return qty === null ? "-" : this.formatQuantity(qty)
    },
    formatQuantity,
    resolveDiffType,
    checkScopeLabel(check) {
      const map = {
        all: "全部库存",
        category: "按商品分类",
        selected: "指定商品",
        sample: "抽盘"
      }
      const scope = check && check.checkScope || "all"
      const suffix = scope === "sample" && check && check.sampleSize ? "（" + check.sampleSize + " 个）" : ""
      return (map[scope] || "其他盘点范围") + suffix
    },
    statusLabel(status) {
      const item = this.statusOptions.find(option => option.value === status)
      return item ? item.label : "未知盘点状态"
    },
    statusTag(status) {
      const map = {
        draft: "info",
        pending_approval: "warning",
        returned: "warning",
        rejected: "danger",
        invalidated: "warning",
        completed: "success",
        cancelled: "info"
      }
      return map[status] || "info"
    },
    approvalStatusLabel(status) {
      const map = {
        running: "审批中",
        approved: "已通过",
        rejected: "已驳回",
        invalidated: "库存已变化",
        cancelled: "已取消"
      }
      return map[status] || "未知审批状态"
    },
    parseApprovalSnapshot(value) {
      if (Array.isArray(value)) return value
      if (!value || typeof value !== "string") return []
      try {
        const parsed = JSON.parse(value)
        return Array.isArray(parsed) ? parsed : []
      } catch (error) {
        return []
      }
    },
    detailSnapshotRows(instance) {
      return this.parseApprovalSnapshot(instance && (instance.detailSnapshotItems || instance.detailSnapshot))
    },
    invalidSnapshotRows(instance) {
      return this.parseApprovalSnapshot(instance && (instance.invalidDetailSnapshotItems || instance.invalidDetailSnapshot))
    },
    adjustmentResultRows(instance) {
      return this.parseApprovalSnapshot(instance && (instance.adjustmentResultItems || instance.adjustmentResultSnapshot))
    },
    ensureSelectedContext() {
      if (this.currentDeptId) {
        return true
      }
      this.$modal.msgError("请先选择店铺或仓库")
      return false
    },
    inventoryDeptId(row) {
      return row ? (row.warehouseId || row.shopDeptId) : undefined
    },
    isWritableCheck(row) {
      return row && this.currentDeptId && String(this.inventoryDeptId(row)) === String(this.currentDeptId)
    },
    ensureWritableCheck(row) {
      if (this.isWritableCheck(row)) {
        return true
      }
      this.$modal.msgError("只能盘点当前组织库存")
      return false
    },
    canInput(row) {
      return ["draft", "rejected", "returned"].includes(row.status) &&
        this.isWritableCheck(row) && this.canActAsCounter(row)
    },
    canRestart(row) {
      return row && row.status === "invalidated" && this.isWritableCheck(row) && this.canActAsCounter(row)
    },
    canAssign(row) {
      return row && ["draft", "rejected", "returned", "invalidated"].includes(row.status) && this.isWritableCheck(row)
    },
    canActAsCounter(row) {
      if (!row) return false
      if (row.counterUserId !== undefined && row.counterUserId !== null &&
        String(row.counterUserId) === String(this.currentUserId)) {
        return true
      }
      return Boolean(this.$auth && this.$auth.hasPermi("inv:stockCheck:edit"))
    },
    canCancel(row) {
      if (!row || !["draft", "rejected", "returned", "pending_approval", "invalidated"].includes(row.status) || !this.isWritableCheck(row)) {
        return false
      }
      return row.status !== "pending_approval" || !this.isNativeApproval(row)
    },
    canDeleteDraft(row) {
      return row.status === "draft" && !Number(row.approvalRound || 0) && this.isWritableCheck(row)
    }
  }
}
</script>

<style lang="scss" scoped>
.mb12 { margin-bottom: 12px; }
.text-success { color: #67C23A; }
.text-danger { color: #F56C6C; }
.text-warning { color: #E6A23C; }

.stock-check-page {
  .stock-check-overview {
    border-color: #e5e7eb;

    ::v-deep .el-card__body {
      padding: 18px 20px;
    }
  }

  .overview-main {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;
  }

  .overview-title-block {
    min-width: 220px;

    .overview-kicker {
      color: #6b7280;
      font-size: 13px;
      line-height: 1.4;
    }

    h2 {
      margin: 4px 0 0;
      color: #1f2937;
      font-size: 20px;
      font-weight: 600;
      line-height: 1.35;
    }
  }

  .overview-metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(108px, 1fr));
    gap: 10px;
    flex: 1;
  }

  .overview-metric {
    min-height: 64px;
    padding: 10px 12px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    background: #f9fafb;

    span {
      display: block;
      color: #6b7280;
      font-size: 12px;
      line-height: 1.4;
    }

    strong {
      display: block;
      margin-top: 6px;
      color: #111827;
      font-size: 22px;
      font-weight: 700;
      line-height: 1;
    }

    &.loss {
      border-color: #f8c9c6;
      background: #fff6f5;

      strong {
        color: #d94a45;
      }
    }
  }

  .stock-check-table {
    .loss-quantity-strong {
      font-weight: 700;
    }
  }

  .product-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;
    line-height: 1.25;
  }

  .product-name {
    color: #1f2937;
    font-weight: 600;
  }

  .product-meta {
    color: #6b7280;
    font-size: 12px;
  }

  .approval-alert {
    margin-top: 12px;
  }

  .approval-track {
    padding: 0 8px;
  }

  .approval-round-title,
  .approval-task-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  .approval-round-title {
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .approval-task-row {
    color: #606266;
    font-size: 13px;
  }

  .approval-evidence-block {
    margin-top: 12px;

    > strong {
      display: block;
      margin-bottom: 6px;
      color: #303133;
      font-size: 13px;
    }
  }

  .approval-form {
    margin-top: 16px;
  }

  .loss-qty-cell {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    min-height: 28px;
  }

  .loss-quantity-strong {
    color: #d94a45;
    font-weight: 700;
  }

  .scope-selection-tip {
    margin-top: 6px;
    color: #6b7280;
    font-size: 12px;
  }

  .field-help {
    margin-top: 4px;
    color: #909399;
    font-size: 12px;
    line-height: 1.4;
  }

  .blind-check-alert {
    margin-bottom: 12px;
  }

  .muted-quantity {
    color: #6b7280;
  }

  .detail-filter-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 0 0 10px;
  }

  .detail-filter-control {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .detail-filter-label {
    color: #374151;
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
  }

  .detail-total-banner {
    display: flex;
    align-items: center;
    min-height: 42px;
    margin: 0 0 12px;
    padding: 0 14px;
    border: 1px solid #2563eb;
    border-radius: 8px;
    background: #2563eb;
    color: #ffffff;
    font-size: 15px;
    font-weight: 700;
    line-height: 1.2;
  }
}

@media (max-width: 900px) {
  .stock-check-page {
    .overview-main {
      align-items: stretch;
      flex-direction: column;
    }

    .overview-metrics {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .detail-filter-toolbar {
      align-items: stretch;
      flex-direction: column;
    }

  }
}
</style>
