<template>
  <el-dialog
    :visible="visible"
    width="min(1180px, 94vw)"
    top="5vh"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!requestInFlight"
    :show-close="!requestInFlight"
    :before-close="beforeClose"
    title="预览导入住职资料"
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="import-toolbar">
      <el-upload
        ref="upload"
        action="#"
        accept=".xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :disabled="fileMutationLocked"
        :file-list="fileList"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-button icon="el-icon-folder-opened">选择 Excel</el-button>
      </el-upload>
      <el-button
        type="primary"
        :loading="previewing"
        :disabled="!previewFile || confirming"
        v-hasPermi="['hr:onboarding:import:preview']"
        @click="handlePreview"
      >
        生成预览
      </el-button>
      <el-button
        plain
        icon="el-icon-download"
        :loading="templateDownloading"
        v-hasPermi="['hr:onboarding:import:template']"
        @click="downloadTemplate"
      >
        下载模板
      </el-button>
      <el-button
        v-if="batchId && canDownloadErrors"
        plain
        icon="el-icon-document-delete"
        :loading="errorsDownloading"
        v-hasPermi="['hr:onboarding:import:preview']"
        @click="downloadErrorRows"
      >
        下载失败行
      </el-button>
    </div>

    <el-alert
      title="文件只会先进入预览批次；确认所选有效行后才会创建入职记录。所有敏感字段均以脱敏文本展示。"
      type="info"
      :closable="false"
      show-icon
    />

    <div v-if="batchId" class="batch-summary">
      <span>批次：{{ plainText(batchNo || batchId) }}</span>
      <span>版本：{{ version }}</span>
      <span>总行数：{{ countValue('totalRows') }}</span>
      <span class="is-success">成功：{{ countValue('successRows') }}</span>
      <span class="is-error">失败：{{ countValue('failureRows') }}</span>
      <el-tag size="small" :type="batchStatusType">{{ batchStatusLabel }}</el-tag>
    </div>

    <div v-if="batchId" class="category-summary">
      <el-tag type="success">可导入 · {{ countValue('importableRows') }}</el-tag>
      <el-tag type="warning">需确认 · {{ countValue('warningRows') }}</el-tag>
      <el-tag type="danger">不可导入 · {{ countValue('invalidRows') }}</el-tag>
      <el-tag type="warning">疑似重复 · {{ countValue('duplicateRows') }}</el-tag>
      <el-tag>可绑定现有账号 · {{ countValue('bindableRows') }}</el-tag>
    </div>

    <div v-if="previewError" class="text-error" role="alert">{{ plainText(previewError) }}</div>

    <el-table
      v-if="rows.length"
      v-loading="previewing || batchLoading"
      :data="rows"
      border
      max-height="470"
      class="preview-table"
      row-key="rowId"
    >
      <el-table-column label="处理" width="210" fixed="left">
        <template slot-scope="scope">
          <span v-if="scope.row.category === 'INVALID'" class="is-muted">不可导入</span>
          <el-checkbox
            v-else-if="scope.row.category === 'IMPORTABLE'"
            v-model="scope.row.selected"
            :disabled="scope.row.disabled || confirmLocked"
            @change="syncRowDecision(scope.row)"
          >导入</el-checkbox>
          <el-radio-group
            v-else
            v-model="scope.row.decision"
            size="mini"
            :disabled="scope.row.disabled || confirmLocked"
            @change="syncRowDecision(scope.row)"
          >
            <el-radio-button v-if="scope.row.category !== 'BINDABLE_ACCOUNT'" label="CONTINUE">继续</el-radio-button>
            <el-radio-button v-else label="BIND_EXISTING">绑定</el-radio-button>
            <el-radio-button label="SKIP">跳过</el-radio-button>
          </el-radio-group>
        </template>
      </el-table-column>
      <el-table-column prop="sourceRowNumber" label="源行" width="72" />
      <el-table-column label="分类" width="170">
        <template slot-scope="scope">
          <el-tag size="mini" :type="categoryType(scope.row.category)">{{ categoryLabel(scope.row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="姓名" min-width="110">
        <template slot-scope="scope">{{ plainText(scope.row.employeeName) }}</template>
      </el-table-column>
      <el-table-column label="手机号（脱敏）" min-width="130">
        <template slot-scope="scope">{{ plainText(scope.row.phoneNumberMasked) }}</template>
      </el-table-column>
      <el-table-column label="证件号（脱敏）" min-width="160">
        <template slot-scope="scope">{{ plainText(scope.row.idNumberMasked) }}</template>
      </el-table-column>
      <el-table-column label="公司 / 部门 / 门店 / 岗位" min-width="260">
        <template slot-scope="scope">{{ organizationText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="预计入职" width="110">
        <template slot-scope="scope">{{ plainText(scope.row.expectedEntryDateText) }}</template>
      </el-table-column>
      <el-table-column label="候选绑定账号（脱敏）" min-width="250">
        <template slot-scope="scope">
          <template v-if="scope.row.category === 'BINDABLE_ACCOUNT' && scope.row.candidateUserId">
            <el-radio
              v-model="scope.row.bindUserId"
              :label="scope.row.candidateUserId"
              :disabled="scope.row.decision !== 'BIND_EXISTING' || confirmLocked"
              @change="syncRowDecision(scope.row)"
            >{{ plainText(scope.row.candidateSummary) }}</el-radio>
          </template>
          <span v-else>{{ plainText(scope.row.candidateSummary) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="警告 / 错误 / 结果" min-width="260">
        <template slot-scope="scope">
          <div v-if="scope.row.warningCodes" class="text-warning">{{ importIssueListLabel(scope.row.warningCodes) }}</div>
          <div v-if="scope.row.errorCodes" class="text-error">{{ importIssueListLabel(scope.row.errorCodes) }}</div>
          <div v-if="scope.row.resultMessage" :class="scope.row.rowStatus === 'FAILED' ? 'text-error' : 'text-result'">
            {{ plainText(scope.row.resultMessage) }}
          </div>
          <span v-if="!scope.row.warningCodes && !scope.row.errorCodes && !scope.row.resultMessage" class="is-muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else-if="!previewing" description="选择文件并生成预览后，在这里逐行确认" :image-size="80" />

    <div v-if="errors.length" class="batch-errors">
      <h4>批次失败明细</h4>
      <div v-for="(error, index) in errors" :key="`${error.rowId || 'batch'}-${index}`" class="text-error">
        第 {{ plainText(error.sourceRowNumber || '-') }} 行：{{ plainText(error.message || importIssueLabel(error.code)) }}
      </div>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="requestClose">关闭</el-button>
      <el-button v-if="batchId" :loading="batchLoading" :disabled="confirming" @click="refreshBatch">刷新批次</el-button>
      <el-button
        type="primary"
        :loading="confirming"
        :disabled="!canConfirm"
        v-hasPermi="['hr:onboarding:import:confirm']"
        @click="confirmSelectedRows"
      >确认所选有效行（{{ selectedRowCount }}）</el-button>
    </span>
  </el-dialog>
</template>

<script>
import {
  confirmHrOnboardingImport,
  downloadHrOnboardingErrorRows,
  downloadHrOnboardingTemplate,
  getHrOnboardingImportBatch,
  previewHrOnboardingImport
} from "@/api/hr/onboarding"
import { blobValidate } from "@/utils/common"

export const IMPORT_CATEGORIES = ["IMPORTABLE", "WARNING", "INVALID", "POSSIBLE_DUPLICATE", "BINDABLE_ACCOUNT"]
export const COMPLETED_BATCH_STATUSES = ["COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED"]

const CATEGORY_LABELS = {
  IMPORTABLE: "可导入",
  WARNING: "需确认",
  INVALID: "不可导入",
  POSSIBLE_DUPLICATE: "疑似重复",
  BINDABLE_ACCOUNT: "可绑定现有账号"
}

const IMPORT_ISSUE_LABELS = {
  FORMULA_NOT_ALLOWED: "不允许使用公式单元格",
  TEXT_CELL_REQUIRED: "该字段必须使用文本格式",
  ALIAS_CONFLICT: "表头别名存在冲突",
  FIELD_LENGTH_EXCEEDED: "字段内容超过长度限制",
  SAME_FILE_DUPLICATE: "文件内存在重复数据",
  PHONE_INVALID: "手机号格式不正确",
  EMERGENCY_PHONE_INVALID: "紧急联系人电话格式不正确",
  ID_NUMBER_INVALID: "证件号码格式不正确",
  BANK_ACCOUNT_INVALID: "银行卡号格式不正确",
  DATE_ORDER_INVALID: "日期先后顺序不正确",
  DATE_INVALID: "日期格式不正确",
  ACTIVE_ONBOARDING: "已有进行中的入职记录",
  CANCELLED_ONBOARDING: "匹配到已取消的入职记录",
  EXISTING_EMPLOYEE_BLOCKING: "已存在员工账号，不能重复创建",
  ORGANIZATION_UNRESOLVED: "公司或部门无法识别",
  STORE_UNRESOLVED: "门店无法识别",
  ORGANIZATION_CONTRADICTORY: "组织信息相互矛盾",
  ORGANIZATION_AMBIGUOUS: "组织信息匹配到多个结果",
  EMPLOYEE_NAME_REQUIRED: "员工姓名不能为空",
  PHONE_NUMBER_REQUIRED: "手机号不能为空",
  EXPECTED_ENTRY_DATE_REQUIRED: "预计入职日期不能为空",
  EMPLOYEE_CATEGORY_REQUIRED: "员工类型不能为空",
  POST_UNRESOLVED: "岗位无法识别",
  POST_AMBIGUOUS: "岗位匹配到多个结果",
  SUPERVISOR_UNRESOLVED: "直属主管无法识别",
  SUPERVISOR_AMBIGUOUS: "直属主管匹配到多个结果",
  DICTIONARY_CONFIGURATION_MISSING: "业务字典尚未配置",
  DICTIONARY_UNRESOLVED: "字典值无法识别",
  DICTIONARY_MAPPING_AMBIGUOUS: "字典值匹配到多个结果",
  IMPORT_CELL_TEXT_LIMIT_EXCEEDED: "单元格文本超过导入限制",
  IMPORT_COLUMN_LIMIT_EXCEEDED: "文件列数超过导入限制",
  IMPORT_FILE_INVALID: "导入文件无效",
  IMPORT_FILE_REQUIRED: "请选择导入文件",
  IMPORT_FILE_SIZE_LIMIT_EXCEEDED: "文件大小超过导入限制",
  IMPORT_HEADER_REQUIRED: "导入文件缺少表头",
  IMPORT_HEADER_UNRECOGNIZED: "导入表头无法识别",
  IMPORT_ROW_LIMIT_EXCEEDED: "文件行数超过导入限制",
  IMPORT_SHEET_LIMIT_EXCEEDED: "工作表数量超过导入限制",
  IMPORT_TOTAL_CELL_LIMIT_EXCEEDED: "文件单元格总数超过导入限制",
  IMPORT_ZIP_ENTRY_LIMIT_EXCEEDED: "文件内部条目数量超过限制",
  IMPORT_ZIP_ENTRY_TOO_LARGE: "文件内部条目过大",
  IMPORT_ZIP_PATH_INVALID: "文件内部路径无效",
  IMPORT_ZIP_RATIO_SUSPICIOUS: "文件压缩比例异常",
  IMPORT_ZIP_TOTAL_TOO_LARGE: "文件解压后大小超过限制",
  BIND_DECISION_REQUIRED: "请选择是否绑定现有账号",
  BIND_USER_REQUIRED: "请选择要绑定的员工账号",
  CONTINUE_DECISION_REQUIRED: "请确认是否继续导入",
  DUPLICATE_ROW_DECISION: "同一行存在重复处理决定",
  IMPORT_CREATE_FAILED: "创建入职记录失败",
  IMPORT_DECISION_REQUIRED: "请选择该行的处理方式",
  IMPORT_ERROR_EXPORT_FAILED: "导出失败行失败",
  IMPORT_PAYLOAD_INVALID: "导入请求内容无效",
  IMPORT_PROCESSING_LEASE_LOST: "导入任务已被其他请求接管，请刷新",
  IMPORT_ROW_DECISION_UPDATE_FAILED: "保存行处理决定失败",
  IMPORT_ROW_FAILURE_UPDATE_FAILED: "保存失败结果失败",
  IMPORT_ROW_SUCCESS_UPDATE_FAILED: "保存成功结果失败",
  IMPORT_TEMPLATE_EXPORT_FAILED: "下载导入模板失败",
  IMPORT_VERSION_CONFLICT: "导入批次已更新，请刷新后重试",
  IMPORT_VERSION_REQUIRED: "导入批次版本缺失",
  ONBOARDING_VALIDATION_FAILED: "入职资料校验未通过",
  OUT_OF_SCOPE: "当前账号无权处理该组织的数据",
  ROW_DECISION_INVALID: "该行处理方式无效",
  ROW_INVALID: "该行资料无效",
  ROW_NOT_CONFIRMABLE: "该行当前不能确认导入",
  ROW_NOT_IN_BATCH: "该行不属于当前导入批次",
  ROW_PAYLOAD_INVALID: "该行请求内容无效",
  ROW_PROCESSING_FAILED: "该行处理失败",
  STALE_BIND_CANDIDATE: "候选账号信息已变化，请刷新",
  STALE_CONFLICT: "冲突信息已变化，请刷新"
}

export const importCategoryLabel = value => CATEGORY_LABELS[String(value || "").toUpperCase()] || "待确认分类"
export const importIssueLabel = value => IMPORT_ISSUE_LABELS[String(value || "").toUpperCase()] || "未识别的导入校验问题"
export const importIssueListLabel = value => {
  const values = Array.isArray(value) ? value : String(value || "").split(/[,，;；\s]+/)
  const labels = values.filter(Boolean).map(importIssueLabel)
  return labels.length ? Array.from(new Set(labels)).join("；") : "—"
}

export const plainText = value => value === undefined || value === null || value === "" ? "—" : String(value)

export const unwrapImportResponse = response => {
  if (!response) return {}
  return response.data && typeof response.data === "object" ? response.data : response
}

export const initializePreviewRows = rows => (Array.isArray(rows) ? rows : []).map(source => {
  const row = { ...source }
  row.disabled = row.category === "INVALID" || row.rowStatus === "SUCCESS" || row.rowStatus === "FAILED"
  row.selected = row.category === "IMPORTABLE" && !row.disabled
  row.decision = row.category === "IMPORTABLE" ? "IMPORT" : null
  row.bindUserId = null
  return row
})

export const buildSelectedRows = rows => (Array.isArray(rows) ? rows : [])
  .filter(row => row.selected && !row.disabled && IMPORT_CATEGORIES.includes(row.category) && row.category !== "INVALID")
  .filter(row => row.category !== "BINDABLE_ACCOUNT" || (row.decision === "BIND_EXISTING" && row.bindUserId !== null))
  .map(row => ({
    rowId: row.rowId,
    decision: row.decision,
    bindUserId: row.category === "BINDABLE_ACCOUNT" ? row.bindUserId : null
  }))

export const unresolvedDecisionRow = rows => (Array.isArray(rows) ? rows : []).find(row =>
  !row.disabled && ["WARNING", "POSSIBLE_DUPLICATE", "BINDABLE_ACCOUNT"].includes(row.category) &&
  !["CONTINUE", "BIND_EXISTING", "SKIP"].includes(row.decision)
)

export default {
  name: "HrOnboardingImportDialog",
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      previewFile: null,
      fileList: [],
      fileGeneration: 0,
      batchFile: null,
      batchFileGeneration: null,
      previewing: false,
      confirming: false,
      batchLoading: false,
      templateDownloading: false,
      errorsDownloading: false,
      previewRequestSequence: 0,
      batchRequestSequence: 0,
      confirmRequestSequence: 0,
      batchId: null,
      batchNo: "",
      version: null,
      status: "",
      rows: [],
      errors: [],
      totals: {},
      previewError: ""
    }
  },
  computed: {
    selectedRows() {
      return buildSelectedRows(this.rows)
    },
    selectedRowCount() {
      return this.selectedRows.length
    },
    confirmLocked() {
      return this.confirming || this.previewing || this.batchLoading || this.status !== "PREVIEWED"
    },
    requestInFlight() {
      return this.previewing || this.confirming
    },
    fileMutationLocked() {
      return this.requestInFlight || this.batchLoading
    },
    canConfirm() {
      return Boolean(this.hasFreshFileBoundBatch() && this.selectedRowCount && !this.confirmLocked)
    },
    canDownloadErrors() {
      return Number(this.totals.invalidRows) > 0 || Number(this.totals.failureRows) > 0
    },
    batchStatusLabel() {
      return ({
        PREVIEWED: "待确认",
        PROCESSING: "处理中",
        COMPLETED: "处理完成",
        COMPLETED_WITH_ERRORS: "完成但有失败行",
        FAILED: "批次失败"
      })[this.status] || "未知批次状态"
    },
    batchStatusType() {
      if (this.status === "COMPLETED") return "success"
      if (this.status === "COMPLETED_WITH_ERRORS" || this.status === "FAILED") return "danger"
      if (this.status === "PROCESSING") return "warning"
      return "info"
    }
  },
  beforeDestroy() {
    this.invalidateRequests()
  },
  methods: {
    plainText,
    categoryLabel: importCategoryLabel,
    importIssueLabel,
    importIssueListLabel,
    invalidateRequests() {
      this.previewRequestSequence += 1
      this.batchRequestSequence += 1
      this.confirmRequestSequence += 1
    },
    clearActiveBatch() {
      this.batchId = null
      this.batchNo = ""
      this.version = null
      this.status = ""
      this.batchFile = null
      this.batchFileGeneration = null
      this.rows = []
      this.errors = []
      this.totals = {}
    },
    invalidateFileAndBatch() {
      this.invalidateRequests()
      this.fileGeneration += 1
      this.clearActiveBatch()
      this.previewError = ""
    },
    resetState() {
      this.invalidateRequests()
      this.fileGeneration += 1
      this.previewFile = null
      this.fileList = []
      this.previewing = false
      this.confirming = false
      this.batchLoading = false
      this.clearActiveBatch()
      this.previewError = ""
    },
    handleOpen() {
      this.resetState()
    },
    handleClose() {
      this.invalidateRequests()
      this.$emit("update:visible", false)
    },
    requestClose() {
      if (this.previewing || this.confirming) {
        this.$message.warning("当前请求仍在处理中，请稍候")
        return
      }
      this.$emit("update:visible", false)
    },
    beforeClose(done) {
      if (this.requestInFlight) {
        this.$message.warning("当前请求仍在处理中，请稍候")
        return
      }
      done()
    },
    handleFileChange(file, fileList) {
      if (this.fileMutationLocked) {
        this.fileList = this.previewFile ? this.fileList.slice(-1) : []
        return
      }
      const raw = file && file.raw
      this.invalidateFileAndBatch()
      this.previewFile = null
      this.fileList = []
      if (!raw) return
      const name = String(raw.name || "")
      if (!/\.(xlsx|xls)$/i.test(name)) {
        this.$message.error("仅支持 .xlsx 或 .xls 文件")
        if (this.$refs.upload) this.$refs.upload.clearFiles()
        return
      }
      if (Number(raw.size) > 10 * 1024 * 1024) {
        this.$message.error("文件不能超过 10MB")
        if (this.$refs.upload) this.$refs.upload.clearFiles()
        return
      }
      this.previewFile = raw
      this.fileList = fileList.slice(-1)
      this.previewError = ""
    },
    handleFileRemove() {
      if (this.fileMutationLocked) return
      this.invalidateFileAndBatch()
      this.previewFile = null
      this.fileList = []
    },
    handlePreview() {
      if (this.fileMutationLocked || !this.previewFile) return
      return this.submitPreview()
    },
    submitPreview() {
      if (this.fileMutationLocked || !this.previewFile) return Promise.resolve(null)
      const selectedFile = this.previewFile
      const selectedFileGeneration = this.fileGeneration
      const requestSequence = ++this.previewRequestSequence
      this.batchRequestSequence += 1
      this.confirmRequestSequence += 1
      const formData = new FormData()
      formData.append("file", selectedFile)
      this.previewing = true
      this.previewError = ""
      this.clearActiveBatch()
      return previewHrOnboardingImport(formData)
        .then(response => this.applyPreviewResponse(response, requestSequence, selectedFile, selectedFileGeneration))
        .catch(error => {
          if (!this.isCurrentPreviewRequest(requestSequence, selectedFile, selectedFileGeneration)) return
          this.previewError = (error && error.message) || "预览失败，请检查模板和文件内容"
        })
        .finally(() => {
          if (this.isCurrentPreviewRequest(requestSequence, selectedFile, selectedFileGeneration)) this.previewing = false
        })
    },
    isCurrentPreviewRequest(requestSequence, selectedFile, selectedFileGeneration) {
      return requestSequence === this.previewRequestSequence &&
        selectedFile === this.previewFile && selectedFileGeneration === this.fileGeneration
    },
    applyPreviewResponse(response, requestSequence, selectedFile = this.previewFile, selectedFileGeneration = this.fileGeneration) {
      if (!this.isCurrentPreviewRequest(requestSequence, selectedFile, selectedFileGeneration)) return false
      const batch = unwrapImportResponse(response)
      if (!batch.batchId || batch.version === undefined || batch.version === null) {
        this.previewError = "服务端未返回有效的导入批次，请重新预览"
        return false
      }
      this.applyBatch(batch, { file: selectedFile, generation: selectedFileGeneration })
      return true
    },
    applyBatch(batch, fileIdentity) {
      if (fileIdentity) {
        this.batchFile = fileIdentity.file
        this.batchFileGeneration = fileIdentity.generation
      }
      this.batchId = batch.batchId
      this.batchNo = batch.batchNo || ""
      this.version = batch.version
      this.status = batch.status || "PREVIEWED"
      this.rows = initializePreviewRows(batch.rows)
      this.errors = Array.isArray(batch.errors) ? batch.errors : []
      this.totals = {
        totalRows: batch.totalRows,
        importableRows: batch.importableRows,
        warningRows: batch.warningRows,
        invalidRows: batch.invalidRows,
        duplicateRows: batch.duplicateRows,
        bindableRows: batch.bindableRows,
        successRows: batch.successRows,
        failureRows: batch.failureRows
      }
    },
    syncRowDecision(row) {
      if (row.category === "IMPORTABLE") {
        row.decision = row.selected ? "IMPORT" : null
        return
      }
      row.selected = row.decision === "CONTINUE" || row.decision === "BIND_EXISTING"
      if (row.decision !== "BIND_EXISTING") row.bindUserId = null
    },
    buildConfirmPayload() {
      return { version: this.version, rows: buildSelectedRows(this.rows) }
    },
    validateSelectedRows() {
      const unresolved = unresolvedDecisionRow(this.rows)
      if (unresolved) {
        this.$message.warning(`第 ${plainText(unresolved.sourceRowNumber)} 行需要明确选择继续、绑定或跳过`)
        return false
      }
      const pendingBind = this.rows.find(row => row.selected && row.category === "BINDABLE_ACCOUNT" && !row.bindUserId)
      if (pendingBind) {
        this.$message.warning(`第 ${plainText(pendingBind.sourceRowNumber)} 行需要明确选择脱敏候选账号`)
        return false
      }
      if (!this.selectedRows.length) {
        this.$message.warning("请至少选择一条有效行")
        return false
      }
      return true
    },
    hasFreshFileBoundBatch() {
      return Boolean(this.batchId && this.version !== null && this.status === "PREVIEWED" &&
        this.previewFile && this.batchFile === this.previewFile && this.batchFileGeneration === this.fileGeneration)
    },
    confirmSelectedRows() {
      if (this.confirming || this.previewing || !this.hasFreshFileBoundBatch() || !this.validateSelectedRows()) return
      const activeBatchId = this.batchId
      const activeVersion = this.version
      const activeFile = this.previewFile
      const activeFileGeneration = this.fileGeneration
      const requestSequence = ++this.confirmRequestSequence
      const payload = this.buildConfirmPayload()
      this.confirming = true
      return confirmHrOnboardingImport(activeBatchId, payload)
        .then(response => {
          if (requestSequence !== this.confirmRequestSequence || activeBatchId !== this.batchId || activeVersion !== this.version ||
            activeFile !== this.previewFile || activeFileGeneration !== this.fileGeneration) return
          const batch = unwrapImportResponse(response)
          if (batch.batchId !== activeBatchId) {
            this.previewError = "服务端返回了不同的导入批次，当前结果已忽略"
            return
          }
          this.applyBatch(batch)
          if (batch.status === "PROCESSING") {
            this.$message.warning("批次正在由另一个请求处理，请稍后刷新批次")
          } else {
            this.$message.success(`导入处理完成：成功 ${Number(batch.successRows) || 0} 行，失败 ${Number(batch.failureRows) || 0} 行`)
            this.$emit("completed", batch)
          }
        })
        .catch(error => {
          if (requestSequence !== this.confirmRequestSequence) return
          this.previewError = (error && error.message) || "批次确认失败，请刷新后重试"
        })
        .finally(() => {
          if (requestSequence === this.confirmRequestSequence) this.confirming = false
        })
    },
    refreshBatch() {
      if (!this.batchId || this.batchLoading || this.confirming || this.batchFile !== this.previewFile ||
        this.batchFileGeneration !== this.fileGeneration) return
      const activeBatchId = this.batchId
      const requestSequence = ++this.batchRequestSequence
      this.batchLoading = true
      return getHrOnboardingImportBatch(activeBatchId)
        .then(response => {
          if (requestSequence !== this.batchRequestSequence || activeBatchId !== this.batchId) return
          const batch = unwrapImportResponse(response)
          if (batch.batchId === activeBatchId) this.applyBatch(batch)
        })
        .catch(error => {
          if (requestSequence === this.batchRequestSequence) this.previewError = (error && error.message) || "刷新批次失败"
        })
        .finally(() => {
          if (requestSequence === this.batchRequestSequence) this.batchLoading = false
        })
    },
    downloadTemplate() {
      if (this.templateDownloading) return
      this.templateDownloading = true
      return downloadHrOnboardingTemplate()
        .then(blob => this.saveDownloadBlob(blob, "入职导入模板.xlsx"))
        .catch(error => this.$message.error((error && error.message) || "模板下载失败"))
        .finally(() => { this.templateDownloading = false })
    },
    downloadErrorRows() {
      if (!this.batchId || this.errorsDownloading) return
      const activeBatchId = this.batchId
      this.errorsDownloading = true
      return downloadHrOnboardingErrorRows(activeBatchId)
        .then(blob => {
          if (activeBatchId !== this.batchId) return
          return this.saveDownloadBlob(blob, `入职导入失败行_${activeBatchId}.xlsx`)
        })
        .catch(error => this.$message.error((error && error.message) || "失败行下载失败"))
        .finally(() => { this.errorsDownloading = false })
    },
    saveDownloadBlob(value, fileName) {
      const blob = value instanceof Blob ? value : new Blob([value])
      if (!blobValidate(blob)) return this.$download.printErrMsg(blob)
      this.$download.saveAs(blob, fileName)
      return Promise.resolve()
    },
    countValue(key) {
      return Number(this.totals[key]) || 0
    },
    categoryType(category) {
      if (category === "IMPORTABLE") return "success"
      if (category === "INVALID") return "danger"
      if (category === "WARNING" || category === "POSSIBLE_DUPLICATE") return "warning"
      return "info"
    },
    organizationText(row) {
      return [row.companyName, row.deptLevel1Name, row.deptLevel2Name, row.deptLevel3Name, row.storeName, row.positionName]
        .filter(value => value !== undefined && value !== null && value !== "")
        .map(plainText)
        .join(" / ") || "—"
    }
  }
}
</script>

<style lang="scss" scoped>
.import-toolbar,
.batch-summary,
.category-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.import-toolbar {
  margin-bottom: 14px;

  ::v-deep .el-upload-list {
    display: inline-block;
    max-width: 300px;
    margin: 0 0 0 10px;
    vertical-align: middle;
  }
}

.batch-summary {
  margin: 16px 0 10px;
  color: #475569;
}

.category-summary {
  margin-bottom: 12px;
}

.preview-table {
  margin-top: 12px;
}

.batch-errors {
  max-height: 120px;
  margin-top: 12px;
  padding: 10px 12px;
  overflow: auto;
  border-radius: 6px;
  background: #fff7ed;

  h4 { margin: 0 0 6px; }
}

.text-warning { color: #b45309; white-space: pre-wrap; overflow-wrap: anywhere; }
.text-error { color: #b91c1c; white-space: pre-wrap; overflow-wrap: anywhere; }
.text-result { color: #047857; white-space: pre-wrap; overflow-wrap: anywhere; }
.is-muted { color: #94a3b8; }
.is-success { color: #047857; }
.is-error { color: #b91c1c; }
</style>
