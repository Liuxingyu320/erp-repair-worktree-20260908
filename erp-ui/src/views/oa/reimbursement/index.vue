<template>
  <div class="app-container reimbursement-page">
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
            :disabled="!selectedRows.length"
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
      @closed="resetForm"
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        :disabled="submitting"
        label-width="92px"
        v-loading="formLoading"
      >
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
        <el-button type="primary" :loading="saving" :disabled="uploading" @click="saveDraft">保存草稿</el-button>
        <el-button
          type="success"
          :loading="submitting"
          :disabled="!submissionAvailable || uploading"
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
      <div slot="footer">
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="canAct"
          type="warning"
          :loading="acting"
          @click="approvalAction('return')"
        >退回修改</el-button>
        <el-button
          v-if="canAct"
          type="danger"
          :loading="acting"
          @click="approvalAction('reject')"
        >拒绝</el-button>
        <el-button
          v-if="canAct"
          type="success"
          :loading="acting"
          @click="approvalAction('approve')"
        >同意</el-button>
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
import { hasValidatedSelectedDeptContext } from "@/utils/shopContext"

const {
  applyInvoiceToForm,
  ensureDraftForInvoiceUpload,
  recoverReimbursementDeleteFailure,
  reimbursementFormSnapshot,
  reimbursementReadiness
} = require("@/utils/reimbursementSmartFill")

const INVOICE_UPLOAD_CONTEXT_MESSAGE = "请先选择门店或仓库，再上传发票"

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
  name: "OaReimbursement",
  data() {
    return {
      loading: false,
      formLoading: false,
      saving: false,
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
      autoSavedAt: "",
      detailVisible: false,
      detailLoading: false,
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
      if (!this.approvalTaskId) return false
      const task = this.approvalTasks.find(item =>
        String(item.taskId || item.id) === String(this.approvalTaskId))
      return !!task &&
        String(task.taskStatus || task.status).toUpperCase() === "PENDING" &&
        String(this.approvalInstance.status || "").toUpperCase() === "RUNNING"
    }
  },
  watch: {
    "$route.query.mode"() {
      this.query.pageNum = 1
      this.selectedRows = []
      this.query.exportStatus = this.financeMode ? "not_exported" : ""
      this.loadList()
    }
  },
  created() {
    if (this.financeMode) this.query.exportStatus = "not_exported"
    this.loadAvailability()
    this.loadList().then(() => this.openRouteTarget())
  },
  beforeDestroy() {
    this.clearPreview()
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
      return loader(params).then(response => {
        this.rows = response.rows || []
        this.total = response.total || 0
      }).finally(() => {
        this.loading = false
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
    openRouteTarget() {
      const id = this.$route.query.reimbursementId ||
        this.$route.query.businessId
      if (/^\d+$/.test(String(id || ""))) this.showDetail(id)
    },
    openForm(row) {
      this.formVisible = true
      this.autoSavedAt = ""
      if (!row) {
        this.form = emptyForm()
        this.captureFormBaseline()
        return
      }
      this.formLoading = true
      getReimbursement(row.reimbursementId).then(response => {
        this.form = this.normalizeForm(response.data)
        this.captureFormBaseline()
      }).finally(() => {
        this.formLoading = false
      })
    },
    normalizeForm(value) {
      const source = value || {}
      return {
        ...source,
        items: Array.isArray(source.items) && source.items.length
          ? source.items.map(item => ({ ...item }))
          : [emptyItem()],
        invoices: Array.isArray(source.invoices)
          ? source.invoices.slice() : []
      }
    },
    resetForm() {
      this.form = emptyForm()
      this.formBaseline = ""
      this.autoSavedAt = ""
      this.uploadQueue = Promise.resolve()
      this.uploadPendingCount = 0
      this.uncertainUploadFiles = {}
      if (this.$refs.form) this.$refs.form.clearValidate()
    },
    captureFormBaseline(saved = false) {
      this.formBaseline = reimbursementFormSnapshot(this.form)
      if (saved) this.markAutoSaved()
    },
    markAutoSaved() {
      this.autoSavedAt = new Date().toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit"
      })
    },
    applySavedForm(responseData, requestSnapshot) {
      const latest = this.form
      const changedDuringRequest =
        reimbursementFormSnapshot(latest) !== requestSnapshot
      const remote = this.normalizeForm(responseData)
      if (changedDuringRequest) {
        this.form = {
          ...remote,
          title: latest.title,
          purpose: latest.purpose,
          items: latest.items
        }
        this.formBaseline = reimbursementFormSnapshot(remote)
        this.markAutoSaved()
      } else {
        this.form = remote
        this.captureFormBaseline(true)
      }
      return this.form
    },
    requestCloseForm() {
      if (this.uploading) {
        this.$modal.msgWarning("发票正在上传识别，请处理完成后再关闭")
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
      if (this.uploading) {
        this.$modal.msgWarning("发票正在上传识别，请处理完成后再关闭")
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
      if (this.saving) return Promise.resolve()
      const requestSnapshot = reimbursementFormSnapshot(this.form)
      this.saving = true
      return saveReimbursement(this.form).then(response => {
        this.applySavedForm(response.data, requestSnapshot)
        this.$modal.msgSuccess("草稿已保存，可随时继续补充")
        this.loadList()
        return this.form
      }).catch(error => {
        this.$modal.msgError(responseMessage(error))
        return undefined
      }).finally(() => {
        this.saving = false
      })
    },
    saveAndSubmit() {
      if (this.submitting) return
      let requestSnapshot = ""
      if (!this.submissionAvailable) {
        this.$modal.msgWarning("报销审批当前未开放")
        return
      }
      if (!this.submissionReadiness.ready) {
        this.$modal.msgWarning(this.submissionReadiness.blockingIssues[0])
        return
      }
      this.validateForm().then(() => {
        this.submitting = true
        requestSnapshot = reimbursementFormSnapshot(this.form)
        return saveReimbursement(this.form)
      }).then(response => {
        this.applySavedForm(response.data, requestSnapshot)
        if (!this.form.invoices.length) {
          throw new Error("请至少上传一个发票文件后再提交")
        }
        return submitReimbursement(this.form)
      }).then(() => {
        this.$modal.msgSuccess("报销申请已提交审批")
        this.formVisible = false
        this.refreshTodo()
        this.loadList()
      }).catch(error => {
        if (error && error.message !== "表单校验失败") {
          this.$modal.msgError(responseMessage(error))
        }
      }).finally(() => {
        this.submitting = false
      })
    },
    hasInvoiceUploadContext() {
      if (hasValidatedSelectedDeptContext()) return true
      this.$modal.msgWarning(INVOICE_UPLOAD_CONTEXT_MESSAGE)
      return false
    },
    beforeInvoiceUpload(file) {
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
      const file = option && option.file
      if (!file) return Promise.resolve()
      if (!this.hasInvoiceUploadContext()) return Promise.resolve(false)
      if (!this.uploadPendingCount) {
        this.uploadCompletedCount = 0
        this.uploadFailedCount = 0
      }
      this.uploadPendingCount += 1
      this.uploading = true
      const queued = this.uploadQueue.then(
        () => this.confirmUncertainUpload(file).then(canUpload =>
          canUpload ? this.processInvoiceUpload(file) : false
        ),
        () => this.confirmUncertainUpload(file).then(canUpload =>
          canUpload ? this.processInvoiceUpload(file) : false
        )
      )
      this.uploadQueue = queued.catch(() => undefined)
      return queued.finally(() => {
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
      return this.reloadFormDetail(true).then(() => {
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
      return ensureDraftForInvoiceUpload(this.form, form => {
        const requestSnapshot = reimbursementFormSnapshot(form)
        return saveReimbursement(form).then(response => {
          this.applySavedForm(response.data, requestSnapshot)
          this.$modal.msgSuccess("已自动创建草稿，开始识别发票")
          return this.form
        })
      })
    },
    processInvoiceUpload(file) {
      if (!this.hasInvoiceUploadContext()) return Promise.resolve(false)
      let uploadedInvoice = null
      let smartFill = null
      let uploadAttempted = false
      let knownInvoiceIds = new Set()
      return this.ensureDraftForUpload().then(() => {
        knownInvoiceIds = new Set((this.form.invoices || []).map(invoice =>
          String(invoice.invoiceId)
        ))
        uploadAttempted = true
        return uploadReimbursementInvoice(this.form.reimbursementId, file)
      }).then(response => {
        uploadedInvoice = response.data || {}
        return this.reloadFormDetail(true)
      }).then(() => {
        const fresh = (this.form.invoices || []).find(invoice =>
          String(invoice.invoiceId) === String(uploadedInvoice.invoiceId)
        ) || uploadedInvoice
        if (uploadedInvoice.idempotentReplay) {
          smartFill = { applied: false, itemIndex: -1, replay: true }
          return this.form
        }
        smartFill = applyInvoiceToForm(this.form, fresh)
        if (!smartFill.applied) return this.form
        const requestSnapshot = reimbursementFormSnapshot(this.form)
        return saveReimbursement(this.form).then(response => {
          this.applySavedForm(response.data, requestSnapshot)
          return this.form
        })
      }).then(() => {
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
        const refresh = this.form.reimbursementId
          ? this.reloadFormDetail(true).catch(() => undefined)
          : Promise.resolve()
        return refresh.then(() => {
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
      const requestSnapshot = reimbursementFormSnapshot(this.form)
      const dirtyAtStart = this.formDirty
      return getReimbursement(this.form.reimbursementId).then(response => {
        const remote = this.normalizeForm(response.data)
        const latest = this.form
        const changedDuringRequest =
          reimbursementFormSnapshot(latest) !== requestSnapshot
        if (preserveLocalEdits && (dirtyAtStart || changedDuringRequest)) {
          this.form = {
            ...remote,
            title: latest.title,
            purpose: latest.purpose,
            items: latest.items
          }
          this.formBaseline = reimbursementFormSnapshot(remote)
        } else {
          this.form = remote
          this.captureFormBaseline()
        }
      }).finally(() => {
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
      if (!this.recognitionForm.invoiceId || this.recognizingEngine) return
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
      if (!this.recognitionForm.invoiceId || this.savingRecognition) return
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
    removeInvoice(invoice) {
      const linkedMessage = invoice.itemId != null
        ? "，同时删除对应费用明细" : ""
      let deleteStarted = false
      let refreshFailed = false
      this.$modal.confirm(
        `确认删除“${invoice.originalName}”${linkedMessage}？`,
        "删除发票"
      )
        .then(() => {
          deleteStarted = true
          return deleteReimbursementInvoice(
            this.form.reimbursementId,
            invoice.invoiceId,
            this.form.rowVersion
          )
        })
        .then(response => {
          const detail = response && response.data
          if (detail && detail.reimbursementId != null) {
            this.form = this.normalizeForm(detail)
            this.captureFormBaseline()
          } else {
            this.form.invoices = (this.form.invoices || []).filter(value =>
              String(value.invoiceId) !== String(invoice.invoiceId)
            )
            if (invoice.itemId != null) {
              this.form.items = (this.form.items || []).filter(value =>
                String(value.itemId) !== String(invoice.itemId)
              )
            }
            return this.reloadFormDetail(false).catch(() => {
              refreshFailed = true
              this.$modal.msgWarning(
                "发票已删除，但页面刷新失败，请手动刷新"
              )
            })
          }
        }).then(() => {
          if (!refreshFailed) this.$modal.msgSuccess("发票已删除")
        }).catch(error => {
          if (!deleteStarted) return undefined
          return recoverReimbursementDeleteFailure(
            error,
            value => this.$modal.msgError(responseMessage(value)),
            () => this.reloadFormDetail(false)
          )
        })
    },
    showDetail(id) {
      this.detailVisible = true
      this.detailLoading = true
      this.detail = null
      this.approvalDetail = null
      return getReimbursement(id).then(response => {
        this.detail = response.data || {}
        if (!this.detail.approvalInstanceId) return undefined
        return getApprovalInstance(this.detail.approvalInstanceId, {
          silentError: true
        })
      }).then(response => {
        if (!response) return
        this.approvalDetail = response.data && response.data.data !== undefined
          ? response.data.data : response.data || {}
      }).finally(() => {
        this.detailLoading = false
      })
    },
    withdraw(row) {
      this.$prompt("请输入撤回原因", "撤回报销申请", {
        inputValue: "申请人撤回",
        inputValidator: value => String(value || "").trim()
          ? true : "请输入撤回原因"
      }).then(({ value }) => withdrawReimbursement(
        row.reimbursementId,
        { reason: String(value).trim() }
      )).then(() => {
        this.$modal.msgSuccess("撤回请求已提交")
        this.refreshTodo()
        this.loadList()
      }).catch(() => {})
    },
    approvalAction(action) {
      if (!this.canAct || this.acting) return
      const ask = action === "approve"
        ? this.$modal.confirm("确认同意这张报销申请？", "审批确认")
          .then(() => "")
        : this.$prompt(
          action === "return" ? "请输入退回原因" : "请输入拒绝原因",
          action === "return" ? "退回修改" : "拒绝申请",
          { inputValidator: value => String(value || "").trim()
            ? true : "原因不能为空" }
        ).then(({ value }) => String(value).trim())
      ask.then(reason => {
        this.acting = true
        const payload = {
          requestId: `OA_REIMBURSEMENT:${this.approvalTaskId}:${action}:${Date.now()}`,
          reason
        }
        if (action === "approve") {
          return approveApprovalTask(this.approvalTaskId, payload)
        }
        if (action === "return") {
          return returnApprovalTask(this.approvalTaskId, payload)
        }
        return rejectApprovalTask(this.approvalTaskId, payload)
      }).then(() => {
        this.$modal.msgSuccess("审批动作已提交")
        this.refreshTodo()
        return this.showDetail(this.detail.reimbursementId)
      }).then(() => this.loadList()).catch(() => {}).finally(() => {
        this.acting = false
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
    exportSelected() {
      if (!this.selectedRows.length || this.exporting) return
      const repeated = this.selectedRows.some(row =>
        row.exportStatus === "exported")
      const message = repeated
        ? "所选数据包含已导出报销单，继续将生成新的可追溯批次。是否继续？"
        : "将生成一个包含三张 Excel 工作表和全部原始发票的 ZIP。是否继续？"
      this.$modal.confirm(message, "导出会计资料").then(() => {
        this.exporting = true
        return createReimbursementExport(
          this.selectedRows.map(row => row.reimbursementId)
        )
      }).then(response => {
        const batch = response.data || {}
        return downloadReimbursementExport(batch.batchId).then(blob => ({
          blob,
          batch
        }))
      }).then(({ blob, batch }) => {
        if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
        this.$download.saveAs(
          new Blob([blob], { type: "application/zip" }),
          batch.archiveName || `报销会计资料_${batch.batchNo || Date.now()}.zip`
        )
        this.$modal.msgSuccess("会计资料包已生成并开始下载")
        this.selectedRows = []
        this.loadList()
      }).catch(error => {
        if (error && error !== "cancel") {
          this.$modal.msgError(responseMessage(error))
        }
      }).finally(() => {
        this.exporting = false
      })
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
