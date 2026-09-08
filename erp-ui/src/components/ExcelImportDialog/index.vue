<template>
  <div>
    <el-dialog :title="title" :visible.sync="visible" :width="width" append-to-body @close="handleClose">
    <el-upload ref="uploadRef" :limit="1" accept=".xlsx, .xls" :headers="headers" :with-credentials="withCredentials" :action="uploadUrl" :disabled="isUploading" :on-progress="handleProgress" :on-success="handleSuccess" :on-error="handleUploadError" :auto-upload="false" drag>
      <i class="el-icon-upload"></i>
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <div class="el-upload__tip text-center" slot="tip">
        <div class="el-upload__tip" slot="tip">
          <el-checkbox v-model="updateSupport"> {{ updateSupportLabel }} </el-checkbox>
        </div>
        <span>仅允许导入xls、xlsx格式文件。</span>
        <el-link v-if="templateUrl" type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="handleDownloadTemplate">下载模板</el-link>
      </div>
    </el-upload>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="isUploading" :disabled="isUploading" @click="handleSubmit">确 定</el-button>
      <el-button :disabled="isUploading" @click="visible = false">取 消</el-button>
    </div>
    </el-dialog>

    <el-dialog title="导入结果" :visible.sync="resultVisible" width="760px" append-to-body @closed="clearImportResult">
      <div class="import-result-summary">
        <el-tag type="success">成功 {{ importResult.successCount }} 条</el-tag>
        <el-tag v-if="importResult.createdCount" type="success">新增 {{ importResult.createdCount }} 条</el-tag>
        <el-tag v-if="importResult.updatedCount" type="info">更新 {{ importResult.updatedCount }} 条</el-tag>
        <el-tag :type="importResult.failureCount ? 'danger' : 'info'">失败 {{ importResult.failureCount }} 条</el-tag>
      </div>
      <el-alert v-if="importResult.committed === false" title="本批次存在失败项，所有数据库变更均已回滚，请修正后重新导入。" type="error" :closable="false" show-icon />
      <div v-if="visibleFailures.length" class="import-result-failures">
        <div v-for="item in visibleFailures" :key="`${item.line}-${item.reason}`">
          <strong>第 {{ item.line }} 行</strong><span>{{ item.reason }}</span>
        </div>
        <p v-if="remainingFailureCount">其余 {{ remainingFailureCount }} 条请下载失败清单</p>
      </div>
      <el-empty v-else description="没有失败记录" :image-size="70" />
      <div v-if="importResult.temporaryCredentials.length" class="temporary-credential-result">
        <el-alert title="以下临时密码仅在本次响应中可用，24 小时失效。下载文件属于敏感文件，请通过受控渠道交付并及时删除。" type="warning" :closable="false" show-icon />
        <el-table :data="importResult.temporaryCredentials.slice(0, 20)" size="mini" max-height="300">
          <el-table-column label="登录账号" prop="userName" min-width="150" />
          <el-table-column label="临时密码" min-width="220">
            <template slot-scope="scope"><code>{{ scope.row.temporaryPassword }}</code></template>
          </el-table-column>
          <el-table-column label="失效时间" prop="expiresAt" min-width="170" />
        </el-table>
        <p v-if="importResult.temporaryCredentials.length > 20">界面仅预览前 20 条，请下载一次性清单完成交付。</p>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button v-if="importResult.temporaryCredentials.length" type="danger" plain @click="downloadCredentials">下载一次性临时密码清单</el-button>
        <el-button v-if="importResult.failureCount" type="primary" plain @click="downloadFailures">下载失败清单</el-button>
        <el-button type="primary" @click="closeResult">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { failureCsv, parseLegacyImportResult, safeCsvCell } from '@/utils/importResult'
import { credentialCsv, parseImportResult } from '@/utils/importResult'
import { getToken } from '@/utils/auth'
import { applySessionAuthHeaders, buildSessionAuthHeaders, shouldUseSessionCredentials } from '@/utils/sessionMode'
const { safeTrustedApiUrl } = require('@/utils/requestSecurity')

export default {
  props: {
    // 对话框标题
    title: {
      type: String,
      default: '数据导入'
    },
    // 对话框宽度
    width: {
      type: String,
      default: '400px'
    },
    // 上传接口地址（必传）
    action: {
      type: String,
      required: true
    },
    // 模板下载接口地址，不传则不显示下载模板链接
    templateAction: {
      type: String,
      default: ''
    },
    // 模板文件名
    templateFileName: {
      type: String,
      default: 'template'
    },
    // 覆盖更新勾选框的说明文字
    updateSupportLabel: {
      type: String,
      default: '是否更新已经存在的数据'
    }
  },
  data() {
    return {
      visible: false,
      isUploading: false,
      updateSupport: false,
      resultVisible: false,
      uploadAttemptId: 0,
      settledUploadAttemptId: 0,
      importResult: this.emptyImportResult(),
      headers: buildSessionAuthHeaders(getToken()),
      withCredentials: shouldUseSessionCredentials()
    }
  },
  computed: {
    uploadUrl() {
      return safeTrustedApiUrl(
        process.env.VUE_APP_BASE_API,
        this.action + '?updateSupport=' + (this.updateSupport ? 1 : 0)
      )
    },
    templateUrl() {
      return !!this.templateAction
    },
    visibleFailures() {
      return this.importResult.failures.slice(0, 20)
    },
    remainingFailureCount() {
      return Math.max(0, this.importResult.failureCount - this.visibleFailures.length)
    }
  },
  methods: {
    // 打开对话框（供父组件通过 ref 调用）
    open() {
      this.clearImportResult()
      this.updateSupport = false
      this.isUploading = false
      this.uploadAttemptId = 0
      this.settledUploadAttemptId = 0
      this.resultVisible = false
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.uploadRef) {
          this.$refs.uploadRef.clearFiles()
        }
      })
    },
    // 关闭时清理
    handleClose() {
      this.isUploading = false
      this.settledUploadAttemptId = this.uploadAttemptId
      if (this.$refs.uploadRef) {
        this.$refs.uploadRef.clearFiles()
      }
    },
    // 下载模板
    handleDownloadTemplate() {
      this.download(this.templateAction, {}, this.exportFileName(this.templateFileName))
    },
    // 上传进度
    handleProgress() {
      if (this.uploadAttemptId > 0 && this.settledUploadAttemptId !== this.uploadAttemptId) {
        this.isUploading = true
      }
    },
    // 上传成功
    handleSuccess(response, file) {
      if (!this.settleUploadAttempt()) return
      this.isUploading = false
      if (!response || (response.code != null && Number(response.code) !== 200)) {
        this.clearImportResult()
        this.restoreRetryFile(file)
        this.$modal.msgError(response && response.msg ? response.msg : '导入失败，请检查文件后重试。')
        return
      }
      if (this.$refs.uploadRef) {
        this.$refs.uploadRef.clearFiles()
      }
      this.visible = false
      this.showImportResult(response)
      this.$emit('success')
    },
    handleUploadError(error, file) {
      if (!this.settleUploadAttempt()) return
      this.isUploading = false
      this.clearImportResult()
      this.restoreRetryFile(file)
      this.$modal.msgError(this.uploadFailureMessage(error))
    },
    settleUploadAttempt() {
      if (!this.isUploading || this.uploadAttemptId === 0 ||
        this.settledUploadAttemptId === this.uploadAttemptId) {
        return false
      }
      this.settledUploadAttemptId = this.uploadAttemptId
      return true
    },
    restoreRetryFile(file) {
      const uploadRef = this.$refs.uploadRef
      if (!uploadRef || !Array.isArray(uploadRef.uploadFiles) || !file) return
      file.status = 'ready'
      file.percentage = 0
      const uid = file.uid
      const existingIndex = uploadRef.uploadFiles.findIndex(item =>
        item === file || (uid != null && item && item.uid === uid)
      )
      if (existingIndex >= 0) {
        uploadRef.uploadFiles.splice(existingIndex, 1, file)
      } else {
        uploadRef.uploadFiles.splice(0, uploadRef.uploadFiles.length, file)
      }
    },
    uploadFailureMessage(error) {
      const message = String(error && error.message || error || '')
      const status = Number(error && error.status)
      if (status === 401) return '登录状态已失效，请重新登录后重试导入。'
      if (status === 403) return '当前账号没有导入权限。'
      if (/timeout|timed out/i.test(message)) return '导入请求超时，请检查网络后重试。'
      if (/network|failed to fetch|load failed|offline/i.test(message) || !status) {
        return '导入失败，网络连接异常，请重试。'
      }
      return `导入失败（接口 ${status} 异常），请重试。`
    },
    showImportResult(response) {
      this.importResult = parseImportResult(response)
      this.resultVisible = true
    },
    parseLegacyResult(message) {
      return parseLegacyImportResult(message)
    },
    downloadFailures() {
      const content = `\ufeff${failureCsv(this.importResult.failures)}`
      const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = this.exportFileName('员工导入失败清单')
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
    },
    downloadCredentials() {
      const content = `\ufeff${credentialCsv(this.importResult.temporaryCredentials)}`
      const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = this.exportFileName('一次性临时密码清单')
      document.body.appendChild(anchor)
      anchor.click()
      document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
    },
    emptyImportResult() {
      return {
        committed: true,
        createdCount: 0,
        updatedCount: 0,
        successCount: 0,
        failureCount: 0,
        failures: [],
        temporaryCredentials: [],
        lines: []
      }
    },
    clearImportResult() {
      if (this.importResult && Array.isArray(this.importResult.temporaryCredentials)) {
        this.importResult.temporaryCredentials.forEach(item => { item.temporaryPassword = "" })
      }
      this.importResult = this.emptyImportResult()
    },
    // 提交上传
    handleSubmit() {
      applySessionAuthHeaders(this.headers, getToken())
      if (!this.uploadUrl) {
        this.$modal.msgError('上传地址不符合安全策略。')
        return
      }
      const files = this.$refs.uploadRef.uploadFiles
      if (!files || files.length === 0) {
        this.$modal.msgError('请选择要上传的文件。')
        return
      }
      const name = files[0].name.toLowerCase()
      if (!name.endsWith('.xls') && !name.endsWith('.xlsx')) {
        this.$modal.msgError('请选择后缀为 "xls" 或 "xlsx" 的文件。')
        return
      }
      this.uploadAttemptId += 1
      this.isUploading = true
      try {
        this.$refs.uploadRef.submit()
      } catch (error) {
        this.handleUploadError(error, files[0])
      }
    }
  }
}
</script>

<style scoped>
.import-result-summary { display: flex; gap: 10px; margin-bottom: 14px; }
.credential-download-alert { margin-bottom: 14px; }
.import-result-failures { max-height: 55vh; overflow: auto; }
.import-result-failures > div { display: grid; grid-template-columns: 80px 1fr; gap: 10px; padding: 8px 0; border-bottom: 1px solid #ebeef5; }
.import-result-failures span { overflow-wrap: anywhere; color: #606266; }
.import-result-failures p { color: #909399; }
.temporary-credential-result { margin-top: 18px; }
.temporary-credential-result code { user-select: all; overflow-wrap: anywhere; }
.temporary-credential-result p { color: #c45656; }
</style>
