<template>
  <div class="app-container reimbursement-page">
    <approval-command-recovery :message="withdrawError" :reason="withdrawReason" :unknown="withdrawUnknown" :busy="acting || checkingWithdraw" :checking="checkingWithdraw" :checked="withdrawChecked" check-label="核对撤回结果" retry-label="重试原撤回请求" @check="checkWithdrawCommand" @retry="retryWithdrawCommand" />
    <section v-if="financeMode" v-hasPermi="['oa:reimbursement:finance:export']" class="mb16">
      <el-button icon="el-icon-time" @click="openExportHistory">我的历史导出批次</el-button>
      <el-alert v-if="exportRecoveryError" :title="exportRecoveryError" type="warning" :closable="false" />
      <div v-if="pendingExportCommand">
        <span>原请求已保留（{{ pendingExportCommand.reimbursementIds.length }} 张报销单）。</span>
        <el-button type="text" :disabled="exporting || checkingExport" @click="checkExportCommand">核对批次创建结果</el-button>
        <el-button type="text" :disabled="exporting || checkingExport || !exportCommandChecked" @click="retryExportCommand">重试原批次创建</el-button>
      </div>
      <el-alert v-for="batch in exportReceipts" :key="batch.batchId" :title="`原批次 ${batch.batchNo} 已生成，包含 ${batch.reimbursementCount} 张报销单`" type="info" :closable="false">
        <el-button type="text" :disabled="!!downloadingBatchId" @click="downloadOriginalExport(batch)">重新下载此批次</el-button>
      </el-alert>
    </section>
    <el-dialog title="我的历史导出批次" :visible.sync="exportHistoryVisible" append-to-body width="90%">
      <el-alert v-if="exportHistoryError" :title="exportHistoryError" type="error" :closable="false"><el-button type="text" @click="loadExportHistory">重新读取</el-button></el-alert>
      <el-table v-loading="exportHistoryLoading" :data="exportHistoryRows" row-key="batchId">
        <el-table-column label="原批次号" prop="batchNo" min-width="170" />
        <el-table-column label="创建时间" prop="createTime" min-width="160" />
        <el-table-column label="创建人" prop="createdByName" min-width="100" />
        <el-table-column label="报销数" prop="reimbursementCount" width="80" />
        <el-table-column label="原包状态" width="105"><template slot-scope="scope">{{ scope.row.archiveStatus === 'AVAILABLE' ? '可下载' : '原包不可用' }}</template></el-table-column>
        <el-table-column label="操作" width="150"><template slot-scope="scope"><el-button type="text" :disabled="!!downloadingBatchId || scope.row.archiveStatus !== 'AVAILABLE'" @click="downloadOriginalExport(scope.row)">下载原批次</el-button></template></el-table-column>
      </el-table>
      <p>标记“已导出”表示批次已生成。原包不可用时请核对后显式生成新批次，历史批次不会重建。</p>
      <pagination v-show="exportHistoryTotal > 0" :total="exportHistoryTotal" :page.sync="exportHistoryPage" :limit="10" @pagination="loadExportHistory" />
    </el-dialog>
    <section class="page-hero">
      <div>
        <span class="eyebrow">OA 协同 · 费用报销</span>
        <h1>{{ financeMode ? '报销财务台账' : '我的报销' }}</h1>
        <p v-if="financeMode">只展示审批通过的报销单，可导出会计 Excel 和原始发票附件。</p>
        <p v-else>填写费用明细、上传发票，并跟踪部门负责人和财务负责人审批。</p>
      </div>
      <div class="hero-total">
        <span>当前结果</span>
        <strong>{{ total }} 张</strong>
      </div>
    </section>

    <el-alert
      v-if="!financeMode && availabilityResolved && !submissionAvailable"
      title="报销审批当前未开放"
      description="仍可保存草稿和上传发票，审批配置开放后即可提交。"
      type="warning"
      :closable="false"
      show-icon
      class="mb16"
    />

    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline size="small">
        <el-form-item label="标题">
          <el-input
            v-model.trim="query.title"
            clearable
            placeholder="报销标题"
            @keyup.enter.native="search"
          />
        </el-form-item>
        <el-form-item v-if="financeMode" label="申请人">
          <el-input
            v-model.trim="query.applicantName"
            clearable
            placeholder="姓名或账号"
            @keyup.enter.native="search"
          />
        </el-form-item>
        <el-form-item v-if="financeMode" label="导出状态">
          <el-select v-model="query.exportStatus" clearable placeholder="全部">
            <el-option label="未导出" value="not_exported" />
            <el-option label="已导出" value="exported" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="审批状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
          <el-button
            v-if="!financeMode"
            v-hasPermi="['oa:reimbursement:self']"
            icon="el-icon-plus"
            @click="openForm()"
          >新建报销</el-button>
          <el-button
            v-if="financeMode"
            icon="el-icon-finished"
            :disabled="!unexportedRows.length"
            @click="selectUnexportedRows"
          >选择本页未导出（{{ unexportedRows.length }}）</el-button>
          <el-button
            v-if="financeMode"
            v-hasPermi="['oa:reimbursement:finance:export']"
            type="success"
            icon="el-icon-download"
            :loading="exporting"
            :disabled="!selectedRows.length || !!pendingExportCommand"
            @click="exportSelected"
          >导出 Excel＋发票（{{ selectedRows.length }}）</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        ref="reimbursementTable"
        v-loading="loading"
        :data="rows"
        row-key="reimbursementId"
        @selection-change="selectedRows = $event"
      >
        <el-table-column v-if="financeMode" type="selection" width="46" />
        <el-table-column label="报销单号" prop="reimbursementNo" min-width="175" />
        <el-table-column label="标题" prop="title" min-width="150" show-overflow-tooltip />
        <el-table-column
          v-if="financeMode"
          label="申请人"
          min-width="110"
        >
          <template slot-scope="scope">
            {{ scope.row.applicantNickName || scope.row.applicantName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="部门/店铺" min-width="150" show-overflow-tooltip>
          <template slot-scope="scope">
            {{ scope.row.applicantDeptName || scope.row.shopDeptName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="125" align="right">
          <template slot-scope="scope">{{ money(scope.row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="发票" width="80" align="center">
          <template slot-scope="scope">{{ scope.row.invoiceCount || 0 }} 个</template>
        </el-table-column>
        <el-table-column v-if="financeMode" label="导出" width="95">
          <template slot-scope="scope">
            <el-tag
              :type="scope.row.exportStatus === 'exported' ? 'success' : 'info'"
              size="mini"
            >{{ scope.row.exportStatus === 'exported' ? '已导出' : '未导出' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-else label="状态" width="100">
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.status)" size="mini">
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="financeMode ? '审批通过时间' : '创建时间'"
          min-width="165"
        >
          <template slot-scope="scope">
            {{ financeMode ? (scope.row.approvedTime || '-') : (scope.row.createTime || '-') }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="185" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" @click="showDetail(scope.row.reimbursementId)">详情</el-button>
            <template v-if="!financeMode">
              <el-button
                v-if="editable(scope.row)"
                type="text"
                size="mini"
                @click="openForm(scope.row)"
              >编辑</el-button>
              <el-button
                v-if="scope.row.status === 'pending'"
                type="text"
                size="mini"
                @click="withdraw(scope.row)"
              >撤回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="query.pageNum"
        :limit.sync="query.pageSize"
        @pagination="loadList"
      />
    </el-card>

    <el-dialog
      :title="form.reimbursementId ? '编辑报销申请' : '新建报销申请'"
      :visible.sync="formVisible"
      width="96%"
      custom-class="reimbursement-dialog"
      :close-on-click-modal="false"
      :before-close="handleFormBeforeClose"
      append-to-body
      @closed="handleFormClosed"
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        :disabled="submitting"
        label-width="92px"
        v-loading="formLoading"
      >
        <section v-if="formConflict" class="state-card" role="alert" style="margin-bottom: 16px; padding: 12px; border: 1px solid #e6a23c">
          <strong>需要核对报销记录</strong>
          <p>{{ formConflict.message }}</p>
          <p>本地草稿（版本 {{ form.rowVersion == null ? '新建' : form.rowVersion }}）：{{ form.title }} · {{ form.purpose }}</p>
          <ul><li v-for="(row, index) in form.items" :key="'local-' + index">{{ row.expenseType || '未分类' }} · {{ row.description || row.merchantName || '未填写说明' }} · 金额 {{ row.claimedAmount }}</li></ul>
          <template v-if="formConflict.remote">
            <p>最新记录（版本 {{ formConflict.remote.rowVersion }}）：{{ formConflict.remote.title }} · {{ formConflict.remote.purpose }}</p>
            <ul><li v-for="(row, index) in formConflict.remote.items" :key="'remote-' + index">{{ row.expenseType || '未分类' }} · {{ row.description || row.merchantName || '未填写说明' }} · 金额 {{ row.claimedAmount }}</li></ul>
          </template>
          <button type="button" @click="reviewFormConflict">重新获取最新对照</button>
          <button type="button" @click="useLatestConflictRecord">放弃本地修改并加载最新</button>
        </section>
        <div class="smart-start-card">
          <div class="smart-start-icon">
            <i class="el-icon-camera-solid" aria-hidden="true" />
          </div>
          <div class="smart-start-copy">
            <strong>先上传发票，系统自动生成报销内容</strong>
            <small>已选择门店或仓库后，无需先填表或保存；支持多选，云端优先、本地兜底，识别成功后自动生成对应明细。</small>
          </div>
          <el-upload
            action="#"
            accept=".pdf,.png,.jpg,.jpeg,.ofd"
            multiple
            :show-file-list="false"
            :http-request="uploadInvoice"
            :before-upload="beforeInvoiceUpload"
          >
            <el-button
              type="primary"
              icon="el-icon-upload2"
              :loading="uploading"
            >{{ uploading ? `正在处理 ${uploadPendingCount} 个文件` : '批量选择发票' }}</el-button>
          </el-upload>
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="报销标题" prop="title">
              <el-input v-model.trim="form.title" maxlength="120" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="报销总额">
              <div class="computed-amount">{{ money(formTotal) }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="报销事由" prop="purpose">
          <el-input
            v-model.trim="form.purpose"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <div class="section-title">
          <div>
            <strong>费用明细</strong>
            <small>总金额由明细自动汇总，不能手工修改</small>
          </div>
          <el-button type="text" icon="el-icon-plus" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="费用类型" min-width="120">
            <template slot-scope="scope">
              <el-select v-model="scope.row.expenseType" filterable allow-create placeholder="选择或输入">
                <el-option
                  v-for="type in expenseTypes"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="费用日期" width="150">
            <template slot-scope="scope">
              <el-date-picker
                v-model="scope.row.expenseDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="选择日期"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="商户" min-width="120">
            <template slot-scope="scope">
              <el-input v-model.trim="scope.row.merchantName" maxlength="120" />
            </template>
          </el-table-column>
          <el-table-column label="费用说明" min-width="170">
            <template slot-scope="scope">
              <el-input v-model.trim="scope.row.description" maxlength="300" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="140">
            <template slot-scope="scope">
              <el-input-number
                v-model="scope.row.claimedAmount"
                :min="0"
                :max="9999999999.99"
                :precision="2"
                :controls="false"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="" width="54" align="center">
            <template slot-scope="scope">
              <el-button
                type="text"
                class="danger-link"
                icon="el-icon-delete"
                :aria-label="`删除明细 ${scope.$index + 1}`"
                :title="`删除明细 ${scope.$index + 1}`"
                @click="removeItem(scope.$index)"
              />
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title invoice-title">
          <div>
            <strong>发票附件与自动识别</strong>
            <small>上传后云端优先、本地兜底；结果可人工核对修正</small>
          </div>
          <div class="invoice-actions">
            <el-tag
              size="mini"
              :type="recognitionAvailability.cloudConfigured ? 'success' : 'info'"
            >云OCR{{ recognitionAvailability.cloudConfigured ? '可用' : '未配置' }}</el-tag>
            <el-tag
              size="mini"
              :type="recognitionAvailability.localAvailable ? 'success' : 'danger'"
            >本地OCR{{ recognitionAvailability.localAvailable ? '可用' : '不可用' }}</el-tag>
            <el-upload
              action="#"
              accept=".pdf,.png,.jpg,.jpeg,.ofd"
              multiple
              :show-file-list="false"
              :http-request="uploadInvoice"
              :before-upload="beforeInvoiceUpload"
            >
              <el-button
                size="small"
                type="primary"
                plain
                :loading="uploading"
              >继续添加发票</el-button>
            </el-upload>
          </div>
        </div>
        <el-alert
          v-if="!form.reimbursementId"
          title="选择门店或仓库后即可上传，系统会自动创建草稿并填入识别结果"
          type="info"
          :closable="false"
          show-icon
          class="mb12"
        />
        <el-table :data="form.invoices" border size="small" empty-text="尚未上传发票">
          <el-table-column label="文件名" prop="originalName" min-width="190" show-overflow-tooltip />
          <el-table-column label="大小" width="100">
            <template slot-scope="scope">{{ fileSize(scope.row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="识别结果" min-width="220">
            <template slot-scope="scope">
              <el-tag :type="recognitionStatusType(scope.row.recognitionStatus)" size="mini">
                {{ recognitionStatusLabel(scope.row.recognitionStatus) }}
              </el-tag>
              <div class="recognition-summary">
                {{ recognitionEngineLabel(scope.row) }}
                <span v-if="scope.row.sellerName"> · {{ scope.row.sellerName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发票号码" min-width="145">
            <template slot-scope="scope">
              {{ scope.row.invoiceNumber || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="价税合计" width="110" align="right">
            <template slot-scope="scope">
              {{ scope.row.invoiceTotalAmount == null ? '-' : money(scope.row.invoiceTotalAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="校验" width="130">
            <template slot-scope="scope">
              <el-tag
                v-if="scope.row.duplicateStatus === 'warning'"
                type="warning"
                size="mini"
              >疑似重复发票</el-tag>
              <span v-else class="muted">未发现历史重复</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template slot-scope="scope">
              <el-button
                type="text"
                size="mini"
                @click="openRecognition(scope.row, false)"
              >识别/核对</el-button>
              <el-button
                v-if="scope.row.fileExtension !== 'ofd'"
                type="text"
                size="mini"
                @click="previewInvoice(form.reimbursementId, scope.row)"
              >预览</el-button>
              <el-button
                type="text"
                size="mini"
                @click="downloadInvoice(form.reimbursementId, scope.row)"
              >下载</el-button>
              <el-button
                type="text"
                size="mini"
                class="danger-link"
                @click="removeInvoice(scope.row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <section class="readiness-card">
          <div class="readiness-head">
            <div>
              <strong>提交前检查</strong>
              <small v-if="submissionReadiness.ready">内容已齐全，可以提交审批</small>
              <small v-else>还差 {{ submissionReadiness.blockingIssues.length }} 项，系统会持续提示</small>
            </div>
            <el-tag :type="submissionReadiness.ready ? 'success' : 'warning'">
              {{ submissionReadiness.ready ? '已就绪' : '待补充' }}
            </el-tag>
          </div>
          <div class="readiness-grid">
            <div
              v-for="check in submissionReadiness.checks"
              :key="check.key"
              :class="['readiness-item', { ready: check.ready }]"
            >
              <i :class="check.ready ? 'el-icon-circle-check' : 'el-icon-warning-outline'" />
              <span>{{ check.label }}</span>
            </div>
          </div>
          <el-alert
            v-if="submissionReadiness.warnings.length"
            :title="submissionReadiness.warnings.join('；')"
            type="warning"
            :closable="false"
            show-icon
          />
        </section>
      </el-form>
      <div slot="footer" class="form-dialog-footer">
        <span class="autosave-state">
          {{ autoSavedAt ? `最近保存 ${autoSavedAt}` : '发票上传前会自动保存草稿' }}
        </span>
        <el-button @click="requestCloseForm">关闭</el-button>
        <el-button type="primary" :loading="saving" :disabled="formWriteBusy || !!formConflict" @click="saveDraft">保存草稿</el-button>
        <el-button
          type="success"
          :loading="submitting"
          :disabled="formWriteBusy || !!formConflict || !submissionAvailable || uploading"
          @click="saveAndSubmit"
        >{{ submissionReadiness.ready ? '确认无误并提交审批' : `提交前还差 ${submissionReadiness.blockingIssues.length} 项` }}</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="报销详情与审批轨迹"
      :visible.sync="detailVisible"
      width="96%"
      custom-class="reimbursement-detail-dialog"
      append-to-body
    >
      <div v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button type="text" @click="openRouteTarget(true)">重新读取目标</el-button><el-button type="text" @click="$router.push('/workbench/todo')">返回待办</el-button></el-alert>
        <el-descriptions v-if="detail" :column="3" border size="small">
          <el-descriptions-item label="报销单号">{{ detail.reimbursementNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicantNickName || detail.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请部门">{{ detail.applicantDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归属店铺">{{ detail.shopDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ money(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="3">{{ detail.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报销事由" :span="3">{{ detail.purpose || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h3>费用明细</h3>
        <el-table v-if="detail" :data="detail.items || []" border size="small">
          <el-table-column label="费用类型" prop="expenseType" width="130" />
          <el-table-column label="日期" prop="expenseDate" width="120" />
          <el-table-column label="商户" prop="merchantName" min-width="130" />
          <el-table-column label="说明" prop="description" min-width="180" />
          <el-table-column label="金额" width="120" align="right">
            <template slot-scope="scope">{{ money(scope.row.claimedAmount) }}</template>
          </el-table-column>
        </el-table>

        <h3>发票附件</h3>
        <el-table v-if="detail" :data="detail.invoices || []" border size="small">
          <el-table-column label="文件名" prop="originalName" min-width="190" />
          <el-table-column label="识别结果" min-width="210">
            <template slot-scope="scope">
              <el-tag :type="recognitionStatusType(scope.row.recognitionStatus)" size="mini">
                {{ recognitionStatusLabel(scope.row.recognitionStatus) }}
              </el-tag>
              <div class="recognition-summary">
                {{ scope.row.invoiceNumber || scope.row.sellerName || '未提取关键字段' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="价税合计" width="115" align="right">
            <template slot-scope="scope">
              {{ scope.row.invoiceTotalAmount == null ? '-' : money(scope.row.invoiceTotalAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="重复提示" width="140">
            <template slot-scope="scope">
              <el-tag
                v-if="scope.row.duplicateStatus === 'warning'"
                type="warning"
                size="mini"
              >疑似重复</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170">
            <template slot-scope="scope">
              <el-button
                type="text"
                size="mini"
                @click="openRecognition(scope.row, true)"
              >识别信息</el-button>
              <el-button
                v-if="scope.row.fileExtension !== 'ofd'"
                type="text"
                size="mini"
                @click="previewInvoice(detail.reimbursementId, scope.row)"
              >预览</el-button>
              <el-button
                type="text"
                size="mini"
                @click="downloadInvoice(detail.reimbursementId, scope.row)"
              >下载</el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="approvalDetail">
          <h3>审批轨迹</h3>
          <el-table :data="approvalTasks" border size="small">
            <el-table-column label="顺序" prop="nodeOrder" width="70" />
            <el-table-column label="节点" prop="nodeName" min-width="150" />
            <el-table-column label="状态" width="110">
              <template slot-scope="scope">
                {{ approvalStatusLabel(scope.row.taskStatus || scope.row.status) }}
              </template>
            </el-table-column>
            <el-table-column label="完成时间" prop="completedTime" min-width="165" />
          </el-table>
        </template>
      </div>
      <approval-command-recovery :message="approvalError" :reason="approvalReason" :unknown="approvalUnknown" :busy="acting || checkingApproval" :checking="checkingApproval" :checked="approvalChecked" @check="checkApprovalCommand" @retry="retryApprovalCommand" />
      <div slot="footer">
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="approvalTaskId" :disabled="acting || checkingApproval" @click="returnToNextTodo">返回待办并定位下一条</el-button>
        <el-button
          v-if="canAct"
          type="warning"
          :loading="acting"
          :disabled="approvalUnknown || checkingApproval" @click="approvalAction('return')"
        >退回修改</el-button>
        <el-button
          v-if="canAct"
          type="danger"
          :loading="acting"
          :disabled="approvalUnknown || checkingApproval" @click="approvalAction('reject')"
        >拒绝</el-button>
        <el-button
          v-if="canAct"
          type="success"
          :loading="acting"
          :disabled="approvalUnknown || checkingApproval" @click="approvalAction('approve')"
        >同意</el-button>
        <el-button
          v-if="canAct"
          type="primary"
          :loading="acting"
          :disabled="approvalUnknown || checkingApproval"
          @click="approveAndOpenNext"
        >同意并打开下一条</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="发票识别与人工核对"
      :visible.sync="recognitionVisible"
      width="96%"
      custom-class="reimbursement-recognition-dialog"
      append-to-body
    >
      <el-alert
        :title="recognitionStatusLabel(recognitionForm.recognitionStatus)"
        :description="recognitionForm.recognitionMessage || '请核对识别结果'"
        :type="recognitionAlertType(recognitionForm.recognitionStatus)"
        :closable="false"
        show-icon
        class="mb12"
      />
      <el-form :model="recognitionForm" label-width="110px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发票类型">
              <el-input v-model.trim="recognitionForm.invoiceType" :disabled="recognitionReadOnly" maxlength="80" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开票日期">
              <el-date-picker
                v-model="recognitionForm.invoiceDate"
                :disabled="recognitionReadOnly"
                type="date"
                value-format="yyyy-MM-dd"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票代码">
              <el-input v-model.trim="recognitionForm.invoiceCode" :disabled="recognitionReadOnly" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票号码">
              <el-input v-model.trim="recognitionForm.invoiceNumber" :disabled="recognitionReadOnly" maxlength="40" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售方">
              <el-input v-model.trim="recognitionForm.sellerName" :disabled="recognitionReadOnly" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售方税号">
              <el-input v-model.trim="recognitionForm.sellerTaxNo" :disabled="recognitionReadOnly" maxlength="40" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购买方">
              <el-input v-model.trim="recognitionForm.purchaserName" :disabled="recognitionReadOnly" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购买方税号">
              <el-input v-model.trim="recognitionForm.purchaserTaxNo" :disabled="recognitionReadOnly" maxlength="40" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="不含税金额">
              <el-input-number v-model="recognitionForm.amountWithoutTax" :disabled="recognitionReadOnly" :min="0" :precision="2" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税额">
              <el-input-number v-model="recognitionForm.taxAmount" :disabled="recognitionReadOnly" :min="0" :precision="2" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="价税合计">
              <el-input-number v-model="recognitionForm.invoiceTotalAmount" :disabled="recognitionReadOnly" :min="0" :precision="2" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="消费类型">
              <el-input v-model.trim="recognitionForm.serviceType" :disabled="recognitionReadOnly" maxlength="80" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="校验码">
              <el-input v-model.trim="recognitionForm.checkCode" :disabled="recognitionReadOnly" maxlength="40" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="商品/服务摘要">
              <el-input v-model.trim="recognitionForm.commoditySummary" :disabled="recognitionReadOnly" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button @click="recognitionVisible = false">关闭</el-button>
        <template v-if="!recognitionReadOnly">
          <el-button
            :loading="recognizingEngine === 'cloud'"
            :disabled="!recognitionAvailability.cloudConfigured || !!recognizingEngine"
            @click="rerunRecognition('cloud')"
          >云端识别</el-button>
          <el-button
            :loading="recognizingEngine === 'local'"
            :disabled="!recognitionAvailability.localAvailable || !!recognizingEngine"
            @click="rerunRecognition('local')"
          >本地识别</el-button>
          <el-button @click="applyRecognitionToItem">应用到费用明细</el-button>
          <el-button type="primary" :loading="savingRecognition" @click="saveRecognitionCorrection">保存人工修正</el-button>
        </template>
      </div>
    </el-dialog>

    <el-dialog
      title="发票预览"
      :visible.sync="previewVisible"
      width="96%"
      custom-class="reimbursement-preview-dialog"
      append-to-body
      @closed="clearPreview"
    >
      <img
        v-if="previewKind === 'image'"
        :src="previewUrl"
        class="invoice-image"
        alt="发票预览"
      >
      <iframe
        v-else-if="previewUrl"
        :src="previewUrl"
        class="invoice-frame"
        title="发票预览"
      />
    </el-dialog>
  </div>
</template>

<script>
import reimbursementExportRecovery from "@/mixins/reimbursementExportRecovery"
import { createReimbursementWithdrawRecovery } from "@/mixins/reimbursementWithdrawRecovery"
import { createApprovalCommandRecovery } from "@/mixins/approvalCommandRecovery"
import ApprovalCommandRecovery from "@/components/ApprovalCommandRecovery"
import {
  createReimbursementExport,
  deleteReimbursementInvoice,
  downloadReimbursementExport,
  getReimbursement,
  getReimbursementAvailability,
  getReimbursementInvoice,
  listFinanceReimbursements,
  listMyReimbursements,
  recognizeReimbursementInvoice,
  saveReimbursement,
  submitReimbursement,
  updateReimbursementInvoiceRecognition,
  uploadReimbursementInvoice,
  withdrawReimbursement
} from "@/api/oa/reimbursement"
import { getApprovalInstance } from "@/api/approval/monitor"
import {
  approveApprovalTask,
  rejectApprovalTask,
  returnApprovalTask
} from "@/api/approval/task"
import {
  statusLabel as approvalStatusLabel
} from "@/views/approval/manage/components/approvalUi"
import { blobValidate } from "@/utils/common"
import { getSelectedDeptId, hasValidatedSelectedDeptContext } from "@/utils/shopContext"

const {
  applyInvoiceToForm,
  ensureDraftForInvoiceUpload,
  mergeReimbursementInvoiceDeletion,
  cloneReimbursementDraft, captureReimbursementSave, mergeReimbursementSavedDraft,
  mergeReimbursementReadDraft, isReimbursementVersionConflict, reimbursementDeleteMatches,
  reimbursementFormSnapshot,
  reimbursementReadiness
} = require("@/utils/reimbursementSmartFill")

const { createUiOperationScope } = require("@/utils/uiOperationScope")
const { sanitizeRouteParams } = require("@/utils/todoRouteParams")
const { returnAfterTodoAction } = require("@/utils/todoActionReturn")

const INVOICE_UPLOAD_CONTEXT_MESSAGE = "请先选择门店或仓库，再上传发票"
const SILENT_READ = { silentError: true }

const STATUS_OPTIONS = [
  ["draft", "草稿"],
  ["submitting", "提交中"],
  ["pending", "审批中"],
  ["approved", "已通过"],
  ["returned", "已退回"],
  ["rejected", "已拒绝"],
  ["withdrawn", "已撤回"],
  ["terminated", "已终止"]
].map(([value, label]) => ({ value, label }))

function today() {
  const value = new Date()
  const month = String(value.getMonth() + 1).padStart(2, "0")
  const day = String(value.getDate()).padStart(2, "0")
  return `${value.getFullYear()}-${month}-${day}`
}

function emptyItem() {
  return {
    expenseType: "",
    expenseDate: today(),
    merchantName: "",
    description: "",
    claimedAmount: undefined
  }
}

function emptyForm() {
  return {
    reimbursementId: undefined,
    reimbursementNo: "",
    rowVersion: undefined,
    title: "",
    purpose: "",
    items: [emptyItem()],
    invoices: []
  }
}

function emptyRecognition() {
  return {
    invoiceId: undefined,
    recognitionStatus: "pending",
    recognitionEngine: "",
    recognitionProvider: "",
    recognitionMessage: "",
    invoiceType: "",
    invoiceCode: "",
    invoiceNumber: "",
    invoiceDate: "",
    sellerName: "",
    sellerTaxNo: "",
    purchaserName: "",
    purchaserTaxNo: "",
    amountWithoutTax: undefined,
    taxAmount: undefined,
    invoiceTotalAmount: undefined,
    checkCode: "",
    serviceType: "",
    commoditySummary: ""
  }
}

function responseMessage(error) {
  return error && error.response && error.response.data &&
    error.response.data.msg || error && (error.msg || error.message) ||
    "操作失败，请稍后重试"
}

export default {
  components: { ApprovalCommandRecovery },
  mixins: [reimbursementExportRecovery, createReimbursementWithdrawRecovery({ refresh: vm => { vm.$store.dispatch("todo/invalidateAfterMutation").catch(() => {}); return vm.loadList() } }), createApprovalCommandRecovery({
    target: vm => vm.detail && ({ businessCode: 'OA_REIMBURSEMENT', businessId: vm.detail.reimbursementId, taskId: vm.approvalTaskId, instanceId: vm.detail.approvalInstanceId, approvalRound: vm.detail.approvalRound }),
    visible: vm => vm.detailVisible && !vm.pageInactive, canAct: vm => vm.canAct, loading: 'acting',
    success: (vm, command) => { vm.refreshTodo(); vm.loadList(); return vm.showDetail(command.businessId) }
  })],
  name: "OaReimbursement",
  data() {
    return {
      loading: false,
      formLoading: false,
      saving: false,
      formEpoch: 0,
      formReadSeq: 0,
      detailEpoch: 0,
      pageReadSeq: 0,
      pageInactive: false,
      deptListenerBound: false,
      deletingInvoiceId: "",
      submitting: false,
      uploading: false,
      uploadQueue: Promise.resolve(),
      uploadPendingCount: 0,
      uploadCompletedCount: 0,
      uploadFailedCount: 0,
      uncertainUploadFiles: {},
      recognizingEngine: "",
      savingRecognition: false,
      exporting: false,
      acting: false,
      rows: [],
      total: 0,
      selectedRows: [],
      query: {
        pageNum: 1,
        pageSize: 10,
        title: "",
        status: "",
        applicantName: "",
        exportStatus: ""
      },
      formVisible: false,
      form: emptyForm(),
      formBaseline: "",
      formServerSnapshot: null, formConflict: null, saveInFlight: false, saveOperationSeq: 0, deleteOperationSeq: 0,
      autoSavedAt: "",
      detailVisible: false,
      detailLoading: false,
      detailError: "",
      loadedRouteTargetKey: "",
      detail: null,
      approvalDetail: null,
      submissionAvailable: false,
      availabilityResolved: false,
      previewVisible: false,
      previewUrl: "",
      previewKind: "",
      recognitionVisible: false,
      recognitionReadOnly: false,
      recognitionForm: emptyRecognition(),
      recognitionAvailability: {
        autoRecognize: true,
        cloudConfigured: false,
        cloudProvider: "baidu",
        cloudMessage: "云端OCR密钥未配置",
        localAvailable: false,
        localImageOcrAvailable: false,
        localProvider: "tesseract",
        localMessage: ""
      },
      statusOptions: STATUS_OPTIONS,
      expenseTypes: ["交通费", "差旅费", "住宿费", "餐饮费", "办公费", "招待费", "通讯费", "其他"],
      rules: {
        title: [
          { required: true, message: "请输入报销标题", trigger: "blur" },
          { max: 120, message: "标题长度不能超过120", trigger: "blur" }
        ],
        purpose: [
          { required: true, message: "请输入报销事由", trigger: "blur" },
          { max: 500, message: "报销事由长度不能超过500", trigger: "blur" }
        ]
      }
    }
  },
  computed: {
    routeTargetKey() { return JSON.stringify(this.reimbursementRouteTarget()) },
    financeMode() {
      return String(this.$route.query.mode || "").toLowerCase() === "finance"
    },
    formTotal() {
      return (this.form.items || []).reduce((sum, item) => {
        const amount = Number(item.claimedAmount)
        return sum + (Number.isFinite(amount) ? amount : 0)
      }, 0)
    },
    submissionReadiness() {
      return reimbursementReadiness(this.form)
    },
    formWriteBusy() {
      return this.saveInFlight || this.saving || this.submitting || this.uploading ||
        !!this.deletingInvoiceId || !!this.recognizingEngine || this.savingRecognition
    },
    formDirty() {
      return Boolean(this.formBaseline) &&
        reimbursementFormSnapshot(this.form) !== this.formBaseline
    },
    unexportedRows() {
      return (this.rows || []).filter(row => row.exportStatus !== "exported")
    },
    approvalTasks() {
      const data = this.approvalDetail || {}
      return Array.isArray(data.tasks)
        ? data.tasks
        : Array.isArray(data.taskList) ? data.taskList : []
    },
    approvalInstance() {
      return this.approvalDetail && this.approvalDetail.instance || {}
    },
    approvalTaskId() {
      return this.$route.query.approvalTaskId || ""
    },
    canAct() {
      if (!this.approvalTaskId || !this.detail || this.detailLoading || this.detailError) return false
      const target = this.reimbursementRouteTarget()
      if (target.reimbursementId && target.reimbursementId !== String(this.detail.reimbursementId)) return false
      if (String(this.approvalInstance.instanceId) !== String(this.detail.approvalInstanceId) || String(this.approvalInstance.businessId) !== String(this.detail.reimbursementId) || this.approvalInstance.businessCode !== "OA_REIMBURSEMENT") return false
      const task = this.approvalTasks.find(item =>
        String(item.taskId || item.id) === String(this.approvalTaskId))
      return !!task &&
        String(task.taskStatus || task.status).toUpperCase() === "PENDING" &&
        String(this.approvalInstance.status || "").toUpperCase() === "RUNNING"
    }
  },
  watch: {
    "$route.fullPath"() { this.invalidatePendingFormReads() },
    routeTargetKey() { this.openRouteTarget() },
    detailVisible(value) { if (!value) { this.detailEpoch += 1; this.detailLoading = false; this.approvalDetail = null } },
    "$store.state.user.sessionRevision"() { this.invalidatePendingFormReads(); this.detail = null; this.detailVisible = false; this.loadedRouteTargetKey = "" },
    "$route.query.mode"() {
      this.query.pageNum = 1
      this.selectedRows = []
      this.query.exportStatus = this.financeMode ? "not_exported" : ""
      this.loadList()
    }
  },
  created() {
    if (this.financeMode) this.query.exportStatus = "not_exported"
    this.bindDeptListener()
    this.loadAvailability()
    this.loadList().catch(() => {})
    this.openRouteTarget()
  },
  activated() {
    if (!this.pageInactive) return
    this.pageInactive = false
    this.bindDeptListener()
    this.openRouteTarget(true)
    this.loadList().catch(() => {})
  },
  deactivated() {
    this.pageInactive = true
    this.invalidatePendingFormReads()
  },
  beforeDestroy() {
    this.pageInactive = true
    this.formEpoch += 1
    this.invalidatePendingFormReads()
    this.unbindDeptListener()
    this.clearPreview()
  },
  beforeRouteUpdate(to, from, next) {
    const token = this.routeNavigationScope().begin("navigate")
    if (this.uploading || this.formWriteBusy) { this.$modal.msgWarning("当前操作尚未完成，请稍后切换"); next(false); return }
    if (!this.formVisible || !this.formDirty) { next(); return }
    this.$modal.confirm("当前修改尚未保存，切换待办后将丢失。确认切换？", "放弃未保存修改")
      .then(() => { if (this.routeNavigationScope().isCurrent(token)) { this.formVisible = false; next() } else next(false) })
      .catch(() => next(false))
  },
  beforeRouteLeave(to, from, next) {
    if (this.uploading) {
      this.$modal.msgWarning("发票正在上传识别，请处理完成后再离开")
      next(false)
      return
    }
    if (!this.formVisible || !this.formDirty) {
      next()
      return
    }
    this.$modal.confirm(
      "当前修改尚未保存，离开后将丢失。确认离开？",
      "放弃未保存修改"
    ).then(() => next()).catch(() => next(false))
  },
  methods: {
    approvalStatusLabel,
    statusLabel(status) {
      const match = STATUS_OPTIONS.find(item => item.value === status)
      return match ? match.label : status || "-"
    },
    statusType(status) {
      if (status === "approved") return "success"
      if (["pending", "submitting", "returned"].includes(status)) return "warning"
      if (["rejected", "terminated"].includes(status)) return "danger"
      return "info"
    },
    money(value) {
      const amount = Number(value)
      return `¥ ${Number.isFinite(amount) ? amount.toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }) : "0.00"}`
    },
    fileSize(value) {
      const bytes = Number(value)
      if (!Number.isFinite(bytes) || bytes <= 0) return "-"
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    },
    editable(row) {
      return !!row && ["draft", "returned", "withdrawn"].includes(row.status)
    },
    recognitionStatusLabel(status) {
      return {
        pending: "等待识别",
        succeeded: "识别成功",
        partial: "部分识别",
        failed: "识别失败",
        corrected: "已人工核对",
        unconfigured: "引擎未配置"
      }[status] || "等待识别"
    },
    recognitionStatusType(status) {
      if (["succeeded", "corrected"].includes(status)) return "success"
      if (["partial", "pending", "unconfigured"].includes(status)) return "warning"
      return "danger"
    },
    recognitionAlertType(status) {
      const type = this.recognitionStatusType(status)
      return type === "danger" ? "error" : type
    },
    recognitionEngineLabel(invoice) {
      if (!invoice) return "-"
      if (invoice.recognitionEngine === "manual") return "人工核对"
      if (invoice.recognitionEngine === "cloud") {
        return `云端 · ${invoice.recognitionProvider || "OCR"}`
      }
      if (invoice.recognitionEngine === "local") {
        return `本地 · ${invoice.recognitionProvider || "OCR"}`
      }
      return invoice.recognitionMessage || "尚未识别"
    },
    loadAvailability() {
      this.availabilityResolved = false
      return getReimbursementAvailability().then(response => {
        this.submissionAvailable = !!(response.data && response.data.enabled)
        this.recognitionAvailability = {
          ...this.recognitionAvailability,
          ...(response.data && response.data.recognition)
        }
      }).catch(() => {
        this.submissionAvailable = false
      }).finally(() => {
        this.availabilityResolved = true
      })
    },
    loadList() {
      const sequence = this.listSequence = (this.listSequence || 0) + 1
      const deptId = getSelectedDeptId()
      const current = () => !this.pageInactive && sequence === this.listSequence && this.sameDeptId(deptId)
      this.loading = true
      const loader = this.financeMode
        ? listFinanceReimbursements
        : listMyReimbursements
      const params = { ...this.query }
      if (this.financeMode) delete params.status
      else {
        delete params.applicantName
        delete params.exportStatus
      }
      return loader(params, { silentError: true }).then(response => {
        if (!current()) return
        this.rows = response.rows || []
        this.total = response.total || 0
      }).catch(error => { if (current()) { this.rows = []; this.total = 0; this.$modal.msgError(error.message || "列表加载失败，请重试") } }).finally(() => {
        if (current()) this.loading = false
      })
    },
    search() {
      this.query.pageNum = 1
      this.loadList()
    },
    selectUnexportedRows() {
      const table = this.$refs.reimbursementTable
      if (!table) return
      table.clearSelection()
      this.unexportedRows.forEach(row => table.toggleRowSelection(row, true))
    },
    async returnToNextTodo() {
      if (this.acting || this.checkingApproval) return
      const result = await returnAfterTodoAction({ router: this.$router, todoType: "OA_REIMBURSEMENT_APPROVAL", businessId: this.detail && this.detail.reimbursementId })
      if (!result.returned) return this.$router.push("/workbench/todo")
      return result
    },
    reimbursementRouteTarget() {
      const source = this.$route && this.$route.query || {}
      const result = sanitizeRouteParams({ reimbursementId: source.reimbursementId == null ? null : source.reimbursementId,
        businessId: source.businessId == null ? null : source.businessId,
        approvalTaskId: source.approvalTaskId == null ? null : source.approvalTaskId,
        approvalInstanceId: source.approvalInstanceId == null ? null : source.approvalInstanceId })
      const query = result.ok ? result.params : {}
      const normalize = value => /^[1-9]\d{0,18}$/.test(String(value || "")) ? String(value) : ""
      return { reimbursementId: normalize(query.reimbursementId || query.businessId),
        taskId: normalize(query.approvalTaskId), instanceId: normalize(query.approvalInstanceId) }
    },
    routeNavigationScope() {
      if (!this._routeNavigationScope) this._routeNavigationScope = createUiOperationScope(() => ({ actor: this.$store.getters.id, session: this.$store.state.user.sessionRevision, dept: this.liveDeptId() }))
      return this._routeNavigationScope
    },
    openRouteTarget(force = false) {
      if (this.pageInactive) return Promise.resolve()
      const target = this.reimbursementRouteTarget(), key = JSON.stringify(target)
      if (!target.reimbursementId) { this.loadedRouteTargetKey = ""; this.detailVisible = false; this.detail = null; this.approvalDetail = null; return Promise.resolve() }
      if (!force && this.loadedRouteTargetKey === key) return Promise.resolve()
      this.loadedRouteTargetKey = key
      return this.showDetail(target.reimbursementId, target)
    },
    liveDeptId() {
      return getSelectedDeptId()
    },
    sameDeptId(deptId) {
      return String(this.liveDeptId() || "") === String(deptId || "")
    },
    formReadId(value) {
      return value == null || value === "" ? "" : String(value)
    },
    beginFormRead(reimbursementId) {
      this.formReadSeq += 1
      return {
        epoch: this.formEpoch,
        readSeq: this.formReadSeq,
        reimbursementId: this.formReadId(reimbursementId),
        deptId: this.liveDeptId()
      }
    },
    isCurrentFormRead(token) {
      if (this.pageInactive || !this.formVisible) return false
      if (!token || token.epoch !== this.formEpoch || token.readSeq !== this.formReadSeq) return false
      if (!this.sameDeptId(token.deptId)) return false
      return this.formReadId(this.form && this.form.reimbursementId) === token.reimbursementId
    },
    isCurrentDetailRead(epoch, reimbursementId, deptId) {
      return !this.pageInactive
        && this.detailVisible
        && epoch === this.detailEpoch
        && this.formReadId(this.detail && this.detail.reimbursementId || reimbursementId) === this.formReadId(reimbursementId)
        && this.sameDeptId(deptId)
    },
    invalidatePendingFormReads() {
      this.listSequence = (this.listSequence || 0) + 1
      this.loading = false
      this.pageReadSeq += 1
      this.formReadSeq += 1
      this.detailEpoch += 1
      this.formLoading = false
      this.detailLoading = false
    },
    bindDeptListener() {
      if (typeof window === "undefined" || this.deptListenerBound) return
      window.addEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = true
    },
    unbindDeptListener() {
      if (typeof window === "undefined" || !this.deptListenerBound) return
      window.removeEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = false
    },
    handleDeptChanged() {
      this.invalidatePendingFormReads()
      this.routeNavigationScope().invalidate()
      this.detail = null; this.approvalDetail = null; this.loadedRouteTargetKey = ""
      if (!this.pageInactive) { this.loadList().catch(() => {}); this.openRouteTarget() }
    },
    openForm(row) {
      if (this.pageInactive) return
      this.pageReadSeq += 1
      this.detailEpoch += 1
      this.detailLoading = false
      this.formEpoch += 1
      this.resetFormRecovery()
      this.formVisible = true
      this.autoSavedAt = ""
      if (!row) {
        this.formLoading = false
        this.form = emptyForm()
        this.beginFormRead("")
        this.captureFormBaseline()
        return
      }
      const reimbursementId = row.reimbursementId
      const token = this.beginFormRead(reimbursementId)
      this.form = Object.assign(emptyForm(), { reimbursementId })
      this.captureFormBaseline()
      this.formLoading = true
      return getReimbursement(reimbursementId, SILENT_READ).then(response => {
        if (!this.isCurrentFormRead(token)) return
        this.form = this.normalizeForm(response.data)
        this.captureFormBaseline()
      }).catch(error => {
        if (!this.isCurrentFormRead(token)) return
        this.$modal.msgError(responseMessage(error))
      }).finally(() => {
        if (this.isCurrentFormRead(token)) this.formLoading = false
      })
    },
    normalizeForm(value) {
      const source = value || {}
      return {
        ...source,
        items: Array.isArray(source.items)
          ? source.items.map(item => ({ ...item }))
          : [emptyItem()],
        invoices: Array.isArray(source.invoices)
          ? source.invoices.slice() : []
      }
    },
    resetForm() {
      this.resetFormRecovery()
      this.formEpoch += 1
      this.formReadSeq += 1
      this.form = emptyForm()
      this.formBaseline = ""
      this.autoSavedAt = ""
      this.formLoading = false
      this.uploadQueue = Promise.resolve()
      this.uploadPendingCount = 0
      this.uncertainUploadFiles = {}
      if (this.$refs.form) this.$refs.form.clearValidate()
    },
    handleFormClosed() {
      if (this.formVisible) return
      this.resetForm()
    },
    resetFormRecovery() {
      this.formConflict = null
      this.formServerSnapshot = null
      this.saveInFlight = false
      this.saveOperationSeq += 1
      this.saving = false
      this.submitting = false
      this.deletingInvoiceId = ""
      this.deleteOperationSeq += 1
      this.uploadQueue = Promise.resolve()
      this.uploadPendingCount = 0
      this.uploading = false
      this.uncertainUploadFiles = {}
    },
    captureFormOperation() {
      return { epoch: this.formEpoch, page: this.pageReadSeq, reimbursementId: this.form.reimbursementId }
    },
    isCurrentFormOperation(operation) {
      return !this.pageInactive && this.formVisible && operation.epoch === this.formEpoch && operation.page === this.pageReadSeq &&
        (operation.reimbursementId == null || String(operation.reimbursementId) === String(this.form.reimbursementId))
    },
    ensureFormWritable() {
      if (this.pageInactive || !this.formVisible || this.formLoading) { this.$modal.msgWarning("请等待当前报销记录加载完成"); return false }
      if (this.form.status && !this.editable(this.form)) { this.$modal.msgWarning("当前记录已不能编辑，请查看最新状态"); return false }
      if (!this.formConflict) return true
      this.$modal.msgWarning("草稿有待核对的变化，请先核对最新记录再保存或提交")
      return false
    },
    setFormConflict(kind, message, remote, extra = {}) {
      this.formConflict = { kind, message, remote: remote ? cloneReimbursementDraft(remote) : null,
        reimbursementId: remote && remote.reimbursementId || this.form.reimbursementId, ...extra }
    },
    async reviewFormConflict() {
      const conflict = this.formConflict
      if (!conflict) return
      const id = conflict.reimbursementId
      if (!id) { this.$modal.msgWarning("新建结果尚不明确，请先在报销记录列表核对，避免重复新建"); return }
      let token
      const operation = this.captureFormOperation()
      try {
        token = this.beginFormRead(this.form.reimbursementId)
        const response = await getReimbursement(id, SILENT_READ)
        if (this.formConflict !== conflict || !this.isCurrentFormRead(token)) return
        const remote = this.normalizeForm(response.data)
        if (conflict.kind === "deleteUnknown" && reimbursementDeleteMatches(conflict.baseline, remote, conflict.invoice)) {
          this.form = mergeReimbursementInvoiceDeletion(this.form, remote, conflict.invoice)
          this.formBaseline = reimbursementFormSnapshot(remote)
          this.formServerSnapshot = cloneReimbursementDraft(remote)
          this.formConflict = null
          this.$modal.msgSuccess("已核对：发票及关联费用已删除")
        } else this.formConflict = { ...conflict, remote: cloneReimbursementDraft(remote) }
      } catch (error) {
        if (this.formConflict === conflict && this.isCurrentFormOperation(operation) && (!token || this.isCurrentFormRead(token))) this.$modal.msgWarning("最新记录暂时无法读取，草稿和原版本已保留，请稍后重新核对")
      }
    },
    async useLatestConflictRecord() {
      const conflict = this.formConflict
      if (!conflict) return
      if (!conflict.reimbursementId) { this.$modal.msgWarning("请先在报销记录列表找到已保存记录再打开，避免重复新建"); return }
      try { await this.$modal.confirm("将放弃当前未保存修改并加载最新记录，确认已核对？", "加载最新记录") } catch (_) { return }
      if (this.formConflict !== conflict) return
      const requestSnapshot = reimbursementFormSnapshot(this.form)
      const token = this.beginFormRead(this.form.reimbursementId)
      try {
        const response = await getReimbursement(conflict.reimbursementId, SILENT_READ)
        if (this.formConflict !== conflict || !this.isCurrentFormRead(token)) return
        if (reimbursementFormSnapshot(this.form) !== requestSnapshot) {
          this.formConflict = { ...conflict, remote: cloneReimbursementDraft(response.data) }
          this.$modal.msgWarning("加载期间又有新修改，草稿已保留，请重新核对后选择")
          return
        }
        this.form = this.normalizeForm(response.data)
        this.captureFormBaseline()
        this.formConflict = null
      } catch (error) {
        if (this.formConflict === conflict && this.isCurrentFormRead(token)) this.$modal.msgError("最新记录加载失败，原草稿仍保留")
      }
    },
    async persistFormDraft() {
      if (!this.ensureFormWritable()) throw new Error("草稿尚未完成冲突核对")
      if (this.saveInFlight) throw new Error("草稿正在保存，请稍后重试")
      const operation = this.captureFormOperation()
      const capture = captureReimbursementSave(this.form)
      const saveSeq = ++this.saveOperationSeq
      this.saveInFlight = true
      this.formReadSeq += 1
      try {
        const response = await saveReimbursement(cloneReimbursementDraft(capture.payload))
        if (!this.isCurrentFormOperation(operation)) throw new Error("STALE_FORM_OPERATION")
        const remote = this.normalizeForm(response.data)
        try {
          this.applySavedForm(remote, capture)
        } catch (error) {
          this.setFormConflict("saveIdentity", error.message, remote)
          throw error
        }
        this.formBaseline = reimbursementFormSnapshot(remote)
        this.formServerSnapshot = cloneReimbursementDraft(remote)
        this.markAutoSaved()
        return cloneReimbursementDraft(remote)
      } catch (error) {
        if (this.isCurrentFormOperation(operation) && !this.formConflict) {
          if (isReimbursementVersionConflict(error)) {
            this.setFormConflict("version", "报销记录已被其他操作修改，原草稿和版本已保留，请核对最新记录", null)
            await this.reviewFormConflict()
          } else if (!error.response && /timeout|network|ECONN|unknown|网络|超时/i.test(String(error.code || "") + " " + String(error.message || ""))) {
            this.setFormConflict("saveUnknown", "保存结果尚不明确，原草稿已保留，请先核对，避免重复保存", null)
          }
        }
        throw error
      } finally {
        if (saveSeq === this.saveOperationSeq) this.saveInFlight = false
      }
    },
    captureFormBaseline(saved = false) {
      this.formBaseline = reimbursementFormSnapshot(this.form)
      this.formServerSnapshot = cloneReimbursementDraft(this.form)
      if (saved) this.markAutoSaved()
    },
    markAutoSaved() {
      this.autoSavedAt = new Date().toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit"
      })
    },
    applySavedForm(responseData, capture) {
      this.form = mergeReimbursementSavedDraft(this.form, this.normalizeForm(responseData), capture)
      return this.form
    },
    requestCloseForm() {
      if (this.formWriteBusy) {
        this.$modal.msgWarning("报销操作正在处理，请完成后再关闭")
        return
      }
      if (!this.formDirty) {
        this.formVisible = false
        return
      }
      this.$modal.confirm(
        "当前修改尚未保存，关闭后将丢失。确认关闭？",
        "放弃未保存修改"
      ).then(() => {
        this.formVisible = false
      }).catch(() => {})
    },
    handleFormBeforeClose(done) {
      if (this.formWriteBusy) {
        this.$modal.msgWarning("报销操作正在处理，请完成后再关闭")
        return
      }
      if (!this.formDirty) {
        done()
        return
      }
      this.$modal.confirm(
        "当前修改尚未保存，关闭后将丢失。确认关闭？",
        "放弃未保存修改"
      ).then(done).catch(() => {})
    },
    addItem() {
      if (this.form.items.length >= 100) {
        this.$modal.msgWarning("单张报销单最多100条费用明细")
        return
      }
      this.form.items.push(emptyItem())
    },
    removeItem(index) {
      if (this.form.items.length === 1) {
        this.$modal.msgWarning("请至少保留一条费用明细")
        return
      }
      this.form.items.splice(index, 1)
    },
    validateItems() {
      if (!(this.form.items || []).length) return "请至少填写一条费用明细"
      const invalid = this.form.items.some(item =>
        !String(item.expenseType || "").trim() ||
        !item.expenseDate ||
        !String(item.description || "").trim() ||
        !Number.isFinite(Number(item.claimedAmount)) ||
        Number(item.claimedAmount) <= 0
      )
      return invalid ? "请完整填写每条费用明细" : ""
    },
    validateForm() {
      const itemError = this.validateItems()
      if (itemError) {
        this.$modal.msgWarning(itemError)
        return Promise.reject(new Error(itemError))
      }
      return new Promise((resolve, reject) => {
        this.$refs.form.validate(valid => valid
          ? resolve()
          : reject(new Error("表单校验失败")))
      })
    },
    saveDraft() {
      if (this.formWriteBusy || !this.ensureFormWritable()) return Promise.resolve()
      const operation = this.captureFormOperation()
      this.saving = true
      return this.persistFormDraft().then(() => {
        if (!this.isCurrentFormOperation(operation)) return
        this.$modal.msgSuccess(this.formDirty ? "草稿已保存，期间新增修改尚未保存" : "草稿已保存，可随时继续补充")
        this.loadList()
        return this.form
      }).catch(error => {
        if (this.isCurrentFormOperation(operation)) this.$modal.msgError(responseMessage(error))
        return undefined
      }).finally(() => {
        if (operation.epoch === this.formEpoch) this.saving = false
      })
    },
    saveAndSubmit() {
      if (this.formWriteBusy || !this.ensureFormWritable()) return Promise.resolve()
      if (!this.submissionAvailable) { this.$modal.msgWarning("报销审批当前未开放"); return Promise.resolve() }
      if (!this.submissionReadiness.ready) { this.$modal.msgWarning(this.submissionReadiness.blockingIssues[0]); return Promise.resolve() }
      const operation = this.captureFormOperation()
      this.submitting = true
      return this.validateForm().then(() => {
        if (!this.isCurrentFormOperation(operation)) throw new Error("STALE_FORM_OPERATION")
        return this.persistFormDraft()
      }).then(saved => {
        if (!this.isCurrentFormOperation(operation)) throw new Error("STALE_FORM_OPERATION")
        if (this.formDirty) throw new Error("保存期间有新的未保存修改，已保留草稿，请核对后再次提交")
        if (!this.ensureFormWritable()) throw new Error("请先完成草稿核对")
        if (!saved.invoices.length) throw new Error("请至少上传一个发票文件后再提交")
        return submitReimbursement({ ...cloneReimbursementDraft(saved), expectedBaseRound: saved.approvalRound || 0, expectedVersion: saved.rowVersion })
      }).then(() => {
        if (!this.isCurrentFormOperation(operation)) return
        this.$modal.msgSuccess("报销申请已提交审批")
        this.formVisible = false
        this.refreshTodo()
        this.loadList()
      }).catch(error => {
        if (this.isCurrentFormOperation(operation) && error && error.message !== "表单校验失败") this.$modal.msgError(responseMessage(error))
      }).finally(() => {
        if (operation.epoch === this.formEpoch) this.submitting = false
      })
    },
    hasInvoiceUploadContext() {
      if (this.formConflict) { this.$modal.msgWarning("请先核对草稿变化，再上传发票"); return false }
      if (hasValidatedSelectedDeptContext()) return true
      this.$modal.msgWarning(INVOICE_UPLOAD_CONTEXT_MESSAGE)
      return false
    },
    beforeInvoiceUpload(file) {
      if (this.deletingInvoiceId || this.saving || this.submitting) return false
      if (!this.hasInvoiceUploadContext()) return false
      const extension = String(file.name || "").split(".").pop().toLowerCase()
      if (!["pdf", "png", "jpg", "jpeg", "ofd"].includes(extension)) {
        this.$modal.msgWarning("发票只支持 PDF、JPG、PNG 或 OFD")
        return false
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$modal.msgWarning("单个发票文件不能超过10MB")
        return false
      }
      return true
    },
    uploadInvoice(option) {
      if (this.deletingInvoiceId || this.saving || this.submitting) return Promise.resolve(false)
      const file = option && option.file
      if (!file) return Promise.resolve()
      if (!this.hasInvoiceUploadContext()) return Promise.resolve(false)
      const operation = this.captureFormOperation()
      const current = () => this.isCurrentFormOperation(operation)
      if (!this.uploadPendingCount) {
        this.uploadCompletedCount = 0
        this.uploadFailedCount = 0
      }
      this.uploadPendingCount += 1
      this.uploading = true
      const queued = this.uploadQueue.then(
        () => current() ? this.confirmUncertainUpload(file).then(canUpload =>
          current() && canUpload ? this.processInvoiceUpload(file) : false
        ) : false,
        () => current() ? this.confirmUncertainUpload(file).then(canUpload =>
          current() && canUpload ? this.processInvoiceUpload(file) : false
        ) : false
      )
      this.uploadQueue = queued.catch(() => undefined)
      return queued.finally(() => {
        if (!current()) return
        this.uploadPendingCount = Math.max(0, this.uploadPendingCount - 1)
        if (!this.uploadPendingCount) {
          this.uploading = false
          this.loadList()
          if (this.uploadCompletedCount > 1 || this.uploadFailedCount) {
            const summary = `已处理 ${this.uploadCompletedCount} 张发票` +
              (this.uploadFailedCount
                ? `，${this.uploadFailedCount} 张结果需检查`
                : "，费用明细已自动生成")
            if (this.uploadFailedCount) this.$modal.msgWarning(summary)
            else this.$modal.msgSuccess(summary)
          }
        }
      })
    },
    uploadFileKey(file) {
      return [
        String(file && file.name || ""),
        Number(file && file.size || 0),
        Number(file && file.lastModified || 0)
      ].join(":")
    },
    matchingNewInvoice(file, knownInvoiceIds) {
      const known = new Set(knownInvoiceIds || [])
      return (this.form.invoices || []).find(invoice =>
        !known.has(String(invoice.invoiceId)) &&
        String(invoice.originalName || "") === String(file && file.name || "") &&
        Number(invoice.fileSize) === Number(file && file.size)
      )
    },
    confirmUncertainUpload(file) {
      const key = this.uploadFileKey(file)
      const pending = this.uncertainUploadFiles[key]
      if (!pending || !this.form.reimbursementId) return Promise.resolve(true)
      const operation = this.captureFormOperation()
      return this.reloadFormDetail(true).then(() => {
        if (!this.isCurrentFormOperation(operation)) return false
        const recovered = this.matchingNewInvoice(
          file,
          pending.knownInvoiceIds
        )
        this.$delete(this.uncertainUploadFiles, key)
        if (recovered) {
          this.uploadCompletedCount += 1
          this.$modal.msgWarning(
            `${file.name} 上次已实际上传，无需重复上传；请在发票列表继续核对`
          )
          return false
        }
        return true
      }).catch(() => {
        if (!this.isCurrentFormOperation(operation)) return false
        this.$modal.msgWarning(
          `${file.name} 上次上传结果暂时无法确认，请等待网络恢复后再试，避免重复报销`
        )
        return false
      })
    },
    ensureDraftForUpload() {
      if (!this.hasInvoiceUploadContext()) {
        return Promise.reject(new Error(INVOICE_UPLOAD_CONTEXT_MESSAGE))
      }
      return ensureDraftForInvoiceUpload(this.form, () => {
        return this.persistFormDraft().then(() => {
          this.$modal.msgSuccess("已自动创建草稿，开始识别发票")
          return this.form
        })
      })
    },
    processInvoiceUpload(file) {
      if (!this.hasInvoiceUploadContext()) return Promise.resolve(false)
      const operation = this.captureFormOperation()
      const current = () => this.isCurrentFormOperation(operation)
      let uploadedInvoice = null
      let smartFill = null
      let uploadAttempted = false
      let knownInvoiceIds = new Set()
      return this.ensureDraftForUpload().then(() => {
        if (!current()) throw new Error("STALE_FORM_OPERATION")
        knownInvoiceIds = new Set((this.form.invoices || []).map(invoice =>
          String(invoice.invoiceId)
        ))
        uploadAttempted = true
        return uploadReimbursementInvoice(this.form.reimbursementId, file)
      }).then(response => {
        if (!current()) throw new Error("STALE_FORM_OPERATION")
        uploadedInvoice = response.data || {}
        return this.reloadFormDetail(true)
      }).then(() => {
        if (!current()) throw new Error("STALE_FORM_OPERATION")
        const fresh = (this.form.invoices || []).find(invoice =>
          String(invoice.invoiceId) === String(uploadedInvoice.invoiceId)
        ) || uploadedInvoice
        if (uploadedInvoice.idempotentReplay) {
          smartFill = { applied: false, itemIndex: -1, replay: true }
          return this.form
        }
        smartFill = applyInvoiceToForm(this.form, fresh)
        if (!smartFill.applied) return this.form
        return this.persistFormDraft().then(() => this.form)
      }).then(() => {
        if (!current()) throw new Error("STALE_FORM_OPERATION")
        this.uploadCompletedCount += 1
        if (uploadedInvoice.idempotentReplay) {
          this.$modal.msgWarning(
            `${file.name} 已存在于当前报销单，未重复识别或生成明细`
          )
        } else if (uploadedInvoice.duplicateStatus === "warning") {
          this.$modal.msgWarning(
            `${file.name} 已上传并生成明细，但疑似与历史发票重复`
          )
        } else if (uploadedInvoice.recognitionStatus === "partial") {
          this.$modal.msgWarning(
            `${file.name} 已部分识别并填入明细，请提交前核对`
          )
        } else if (smartFill && smartFill.applied) {
          const suffix = smartFill.incomplete
            ? "，仍有字段需要补充"
            : ""
          this.$modal.msgSuccess(
            `${file.name} 已识别并生成明细 ${smartFill.itemIndex + 1}${suffix}`
          )
        } else {
          this.$modal.msgWarning(
            `${file.name} 已上传，但识别结果需要人工核对`
          )
        }
        return true
      }).catch(error => {
        if (!current()) return false
        const refresh = this.form.reimbursementId
          ? this.reloadFormDetail(true).catch(() => undefined)
          : Promise.resolve()
        return refresh.then(() => {
        if (!current()) return false
          const recovered = !uploadAttempted
            ? null
            : uploadedInvoice && uploadedInvoice.invoiceId
              ? uploadedInvoice
              : this.matchingNewInvoice(file, knownInvoiceIds)
          if (recovered) {
            this.uploadCompletedCount += 1
            this.$modal.msgWarning(
              `${file.name} 已上传，但自动填充未完成；无需重新上传，请在发票列表核对后保存草稿`
            )
            return true
          }
          if (uploadAttempted) {
            this.$set(this.uncertainUploadFiles, this.uploadFileKey(file), {
              knownInvoiceIds: Array.from(knownInvoiceIds)
            })
          }
          this.uploadFailedCount += 1
          this.$modal.msgError(
            `${file.name}：${responseMessage(error)}；请刷新发票列表确认后再重试`
          )
          return false
        })
      })
    },
    reloadFormDetail(preserveLocalEdits = true) {
      if (this.pageInactive) return Promise.resolve()
      const reimbursementId = this.form.reimbursementId
      if (!reimbursementId) return Promise.resolve()
      if (this.formConflict) return this.reviewFormConflict()
      const token = this.beginFormRead(reimbursementId)
      const requestSnapshot = reimbursementFormSnapshot(this.form)
      const dirtyAtStart = this.formDirty
      this.formLoading = true
      return getReimbursement(reimbursementId, SILENT_READ).then(response => {
        if (!this.isCurrentFormRead(token)) return
        const remote = this.normalizeForm(response.data)
        const changedDuringRequest = reimbursementFormSnapshot(this.form) !== requestSnapshot
        try {
          this.form = mergeReimbursementReadDraft(this.form, remote, this.formServerSnapshot, preserveLocalEdits && (dirtyAtStart || changedDuringRequest))
        } catch (error) {
          this.setFormConflict("readConflict", error.message, remote)
          throw error
        }
        this.formBaseline = reimbursementFormSnapshot(remote)
        this.formServerSnapshot = cloneReimbursementDraft(remote)
      }).catch(error => {
        if (!this.isCurrentFormRead(token)) return
        throw error
      }).finally(() => {
        if (!this.isCurrentFormRead(token)) return
        this.formLoading = false
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    openRecognition(invoice, readOnly) {
      this.recognitionReadOnly = !!readOnly
      this.recognitionForm = {
        ...emptyRecognition(),
        ...invoice
      }
      this.recognitionVisible = true
    },
    rerunRecognition(engine) {
      if (!this.recognitionForm.invoiceId || this.formWriteBusy || !this.ensureFormWritable()) return
      if (engine === "cloud" &&
          !this.recognitionAvailability.cloudConfigured) {
        this.$modal.msgWarning(
          this.recognitionAvailability.cloudMessage || "云端OCR尚未配置"
        )
        return
      }
      this.recognizingEngine = engine
      recognizeReimbursementInvoice(
        this.form.reimbursementId,
        this.recognitionForm.invoiceId,
        engine
      ).then(response => {
        this.recognitionForm = {
          ...emptyRecognition(),
          ...response.data
        }
        this.$modal.msgSuccess(
          `${engine === "cloud" ? "云端" : "本地"}识别已完成，请核对`
        )
        return this.reloadFormDetail()
      }).catch(error => {
        this.$modal.msgError(responseMessage(error))
      }).finally(() => {
        this.recognizingEngine = ""
      })
    },
    recognitionPayload() {
      const fields = [
        "invoiceType", "invoiceCode", "invoiceNumber", "invoiceDate",
        "sellerName", "sellerTaxNo", "purchaserName", "purchaserTaxNo",
        "amountWithoutTax", "taxAmount", "invoiceTotalAmount", "checkCode",
        "serviceType", "commoditySummary"
      ]
      return fields.reduce((result, field) => {
        result[field] = this.recognitionForm[field] === ""
          ? null : this.recognitionForm[field]
        return result
      }, {})
    },
    saveRecognitionCorrection() {
      if (!this.recognitionForm.invoiceId || this.formWriteBusy || !this.ensureFormWritable()) return
      this.savingRecognition = true
      updateReimbursementInvoiceRecognition(
        this.form.reimbursementId,
        this.recognitionForm.invoiceId,
        this.recognitionPayload()
      ).then(response => {
        this.recognitionForm = {
          ...emptyRecognition(),
          ...response.data
        }
        this.$modal.msgSuccess("发票识别结果已人工核对")
        return this.reloadFormDetail()
      }).catch(error => {
        this.$modal.msgError(responseMessage(error))
      }).finally(() => {
        this.savingRecognition = false
      })
    },
    applyRecognitionToItem() {
      const invoice = this.recognitionForm
      const result = applyInvoiceToForm(this.form, invoice)
      if (!result.applied) {
        this.$modal.msgWarning(
          result.reason === "item_limit"
            ? "费用明细已达到100条上限"
            : "当前没有可应用的识别字段"
        )
        return
      }
      this.$modal.msgSuccess(`已应用到明细 ${result.itemIndex + 1}`)
    },
    async removeInvoice(invoice) {
      if (this.formWriteBusy || !invoice || !this.ensureFormWritable()) return
      const reimbursementId = this.form.reimbursementId
      const operation = this.captureFormOperation()
      const invoiceId = String(invoice.invoiceId)
      const frozenInvoice = cloneReimbursementDraft(invoice)
      const baseline = cloneReimbursementDraft(this.formServerSnapshot || this.form)
      const expectedVersion = this.form.rowVersion
      const current = () => this.isCurrentFormOperation(operation) && String(this.form.reimbursementId) === String(reimbursementId)
      const deleteSeq = ++this.deleteOperationSeq
      this.deletingInvoiceId = invoiceId
      let deleteStarted = false
      try {
        const linkedMessage = invoice.itemId != null ? "，同时删除对应费用明细" : ""
        await this.$modal.confirm(`确认删除“${invoice.originalName}”${linkedMessage}？`, "删除发票")
        if (!current()) return
        deleteStarted = true
        this.formReadSeq += 1
        const response = await deleteReimbursementInvoice(reimbursementId, invoice.invoiceId, expectedVersion)
        if (!current()) return
        const detail = response && response.data
        if (detail && detail.reimbursementId != null && detail.rowVersion != null && Array.isArray(detail.items) && Array.isArray(detail.invoices)) {
          const remote = this.normalizeForm(detail)
          this.form = mergeReimbursementInvoiceDeletion(this.form, remote, frozenInvoice)
          this.formBaseline = reimbursementFormSnapshot(remote)
          this.formServerSnapshot = cloneReimbursementDraft(remote)
          this.$modal.msgSuccess("发票已删除")
        } else {
          this.setFormConflict("deleteUnknown", "删除响应未包含权威记录，正在核对，原草稿与版本已保留", null, { baseline, invoice: frozenInvoice })
          await this.reviewFormConflict()
        }
      } catch (error) {
        if (!deleteStarted || !current()) return
        const conflict = isReimbursementVersionConflict(error)
        this.setFormConflict(conflict ? "version" : "deleteUnknown",
          conflict ? "删除遇到版本冲突，原草稿与版本已保留；请比较最新费用后再继续" : "删除结果尚不明确，原草稿与版本已保留，核对前不能再次保存或提交",
          null, { baseline, invoice: frozenInvoice })
        this.$modal.msgError(responseMessage(error))
        await this.reviewFormConflict()
      } finally {
        if (deleteSeq === this.deleteOperationSeq && this.deletingInvoiceId === invoiceId) this.deletingInvoiceId = ""
      }
    },
    showDetail(id, routeTarget = null) {
      if (this.pageInactive) return
      this.pageReadSeq += 1
      this.formReadSeq += 1
      this.formLoading = false
      this.detailEpoch += 1
      const epoch = this.detailEpoch
      const deptId = this.liveDeptId()
      this.detailVisible = true
      this.detailLoading = true
      this.detail = null
      this.detailError = ""
      this.approvalDetail = null
      const actor = String(this.$store.getters.id), session = this.$store.state.user.sessionRevision
      const current = () => this.isCurrentDetailRead(epoch, id, deptId) && actor === String(this.$store.getters.id) && session === this.$store.state.user.sessionRevision
      return getReimbursement(id, SILENT_READ).then(response => {
        if (!current()) return
        const detail = response.data || {}
        if (String(detail.reimbursementId) !== String(id)) throw Error("详情回包与当前报销目标不一致，请重新打开待办")
        if (routeTarget && routeTarget.instanceId && String(detail.approvalInstanceId) !== routeTarget.instanceId) throw Error("该待办的审批轮次已变化，请返回待办刷新")
        this.detail = detail
        if (!this.detail.approvalInstanceId) return undefined
        return getApprovalInstance(this.detail.approvalInstanceId, {
          silentError: true
        })
      }).then(response => {
        if (!response || !current()) return
        const approval = response.data && response.data.data !== undefined ? response.data.data : response.data || {}
        const instance = approval.instance || {}
        if (String(instance.instanceId) !== String(this.detail.approvalInstanceId) || String(instance.businessId) !== String(id) || instance.businessCode !== "OA_REIMBURSEMENT") throw Error("审批实例与当前报销单不一致，请返回待办核对")
        this.approvalDetail = approval
      }).catch(error => {
        if (!current()) return
        this.detailError = responseMessage(error)
      }).finally(() => {
        if (epoch === this.detailEpoch && this.detailVisible && !this.pageInactive) this.detailLoading = false
      })
    },
    approvalAction(action) { return this.runApprovalCommand(action) },
    approveAndOpenNext() {
      return this.runApprovalCommand("approve").then(() => {
        if (this.approvalUnknown || this.approvalError || this.acting) return
        return this.returnToNextTodo()
      })
    },
    previewInvoice(reimbursementId, invoice) {
      getReimbursementInvoice(
        reimbursementId,
        invoice.invoiceId,
        "preview"
      ).then(blob => {
        if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
        this.clearPreview()
        this.previewUrl = URL.createObjectURL(blob)
        this.previewKind = String(invoice.contentType || "").startsWith("image/")
          ? "image" : "pdf"
        this.previewVisible = true
      }).catch(error => this.$modal.msgError(responseMessage(error)))
    },
    downloadInvoice(reimbursementId, invoice) {
      getReimbursementInvoice(
        reimbursementId,
        invoice.invoiceId,
        "download"
      ).then(blob => {
        if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
        this.$download.saveAs(blob, invoice.originalName || "发票附件")
      }).catch(error => this.$modal.msgError(responseMessage(error)))
    },
    clearPreview() {
      if (this.previewUrl) URL.revokeObjectURL(this.previewUrl)
      this.previewUrl = ""
      this.previewKind = ""
    },
    refreshTodo() {
      return this.$store.dispatch("todo/invalidateAfterMutation")
        .catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.reimbursement-page{background:#f6f8fb;min-height:calc(100vh - 84px)}.page-hero{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:16px;padding:24px 28px;border-radius:18px;color:#fff;background:linear-gradient(125deg,#24465e,#297765)}.eyebrow{font-size:12px;letter-spacing:.12em;opacity:.75}.page-hero h1{margin:8px 0 6px;font-size:28px}.page-hero p{margin:0;opacity:.82}.hero-total{min-width:120px;text-align:right}.hero-total span{display:block;font-size:12px;opacity:.72}.hero-total strong{display:block;margin-top:5px;font-size:24px}.filter-card{margin-bottom:16px}.table-card,.filter-card{border:0;border-radius:14px}.mb16{margin-bottom:16px}.mb12{margin-bottom:12px}.computed-amount{height:40px;color:#d06a2b;font-size:22px;font-weight:700;line-height:40px}.section-title{display:flex;align-items:center;justify-content:space-between;margin:22px 0 10px}.section-title strong,.section-title small{display:block}.section-title small{margin-top:4px;color:#81908e;font-size:12px}.invoice-title{margin-top:26px}.invoice-actions{display:flex;align-items:center;gap:8px}.recognition-summary{margin-top:5px;color:#73817f;font-size:12px;line-height:1.35}.danger-link{color:#e25a5a}.muted{color:#8a9896;font-size:12px}h3{margin:22px 0 10px;font-size:15px}.invoice-image{display:block;max-width:100%;max-height:70vh;margin:0 auto}.invoice-frame{width:100%;height:70vh;border:0}
  .smart-start-card{display:flex;align-items:center;gap:14px;margin:0 0 22px;padding:16px 18px;border:1px solid #c9e7df;border-radius:14px;background:linear-gradient(120deg,#edf9f5,#f4f8ff)}.smart-start-icon{display:grid;width:46px;height:46px;flex:none;place-items:center;border-radius:13px;color:#fff;background:#278a7d;font-size:22px}.smart-start-copy{min-width:0;flex:1}.smart-start-copy strong,.smart-start-copy small{display:block}.smart-start-copy small{margin-top:5px;color:#607874;line-height:1.5}.readiness-card{margin-top:18px;padding:16px;border:1px solid #e0e9e7;border-radius:14px;background:#fbfdfc}.readiness-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.readiness-head strong,.readiness-head small{display:block}.readiness-head small{margin-top:4px;color:#71827f}.readiness-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:13px 0}.readiness-item{display:flex;align-items:center;gap:7px;padding:9px 10px;border-radius:9px;color:#95651e;background:#fff4df}.readiness-item.ready{color:#237652;background:#e8f6ee}.form-dialog-footer{display:flex;align-items:center;gap:8px}.autosave-state{margin-right:auto;color:#7c8b89;font-size:12px}.invoice-actions{flex-wrap:wrap;justify-content:flex-end}::v-deep .reimbursement-dialog{max-width:1120px;margin-top:4vh!important}::v-deep .reimbursement-detail-dialog{max-width:1060px}::v-deep .reimbursement-recognition-dialog{max-width:820px}::v-deep .reimbursement-preview-dialog{max-width:920px}::v-deep .reimbursement-dialog .el-dialog__body,::v-deep .reimbursement-detail-dialog .el-dialog__body,::v-deep .reimbursement-recognition-dialog .el-dialog__body{max-height:calc(100vh - 170px);overflow:auto}
  @media(max-width:900px){.page-hero{align-items:flex-start;padding:20px}.hero-total{display:none}.smart-start-card{align-items:flex-start;flex-wrap:wrap}.smart-start-copy{flex-basis:calc(100% - 64px)}.smart-start-card .el-upload,.smart-start-card .el-button{width:100%}.readiness-grid{grid-template-columns:1fr}.form-dialog-footer{flex-wrap:wrap}.autosave-state{width:100%;margin:0 0 4px;text-align:left}::v-deep .reimbursement-dialog{width:calc(100% - 20px)!important;margin-top:10px!important;margin-bottom:10px!important}::v-deep .reimbursement-dialog .el-dialog__body{max-height:calc(100vh - 190px);padding:14px}::v-deep .reimbursement-dialog .el-dialog__footer{padding:10px 14px}}
</style>
