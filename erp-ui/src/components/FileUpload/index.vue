<template>
  <div class="upload-file">
    <el-upload
      multiple
      :action="uploadFileUrl"
      :before-upload="handleBeforeUpload"
      :file-list="widgetFileList"
      :data="data"
      :limit="limit"
      :on-error="handleUploadError"
      :on-progress="handleUploadProgress"
      :http-request="uploadHttpRequest"
      :before-remove="canRemoveWidgetFile"
      :on-exceed="handleExceed"
      :on-success="handleUploadSuccess"
      :show-file-list="false"
      :headers="headers"
      :with-credentials="withCredentials"
      class="upload-file-uploader"
      ref="fileUpload"
      v-if="!disabled"
    >
      <!-- 上传按钮 -->
      <el-button size="mini" type="primary">选取文件</el-button>
      <!-- 上传提示 -->
      <div class="el-upload__tip" slot="tip" v-if="showTip">
        请上传
        <template v-if="fileSize"> 大小不超过 <b style="color: #f56c6c">{{ fileSize }}MB</b> </template>
        <template v-if="fileType"> 格式为 <b style="color: #f56c6c">{{ fileType.join("/") }}</b> </template>
        的文件
      </div>
    </el-upload>
    <upload-queue :items="Object.values(uploadTasks)" @cancel="cancelQueuedUpload" @retry="retryQueuedUpload" @dismiss="dismissQueuedUpload" />

    <!-- 文件列表 -->
    <transition-group ref="uploadFileList" class="upload-file-list el-upload-list el-upload-list--text" name="el-fade-in-linear" tag="ul">
      <li :key="file.url" class="el-upload-list__item ele-upload-list__item-content" v-for="(file, index) in fileList">
        <el-link
          v-if="safeFileHref(file)"
          :href="safeFileHref(file)"
          :underline="false"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="el-icon-document"> {{ getFileName(file.name) }} </span>
        </el-link>
        <span v-else class="unsafe-file-link">
          <span class="el-icon-document"> {{ getFileName(file.name) }} </span>
        </span>
        <div class="ele-upload-list__item-content-action">
          <button type="button" class="attachment-remove-button" :aria-label="'删除附件：' + getFileName(file.name)" @click="handleDelete(file)" v-if="!disabled">删除</button>
        </div>
      </li>
    </transition-group>
  </div>
</template>

<script>
import { getToken } from "@/utils/auth"
import { applySessionAuthHeaders, buildSessionAuthHeaders, shouldUseSessionCredentials } from "@/utils/sessionMode"
import Sortable from 'sortablejs'
import UploadQueue from '@/components/UploadQueue'
import { uploadProgressData, uploadProgressMethods } from '@/utils/uploadProgress'
const { sanitizeFileUrl } = require("@/utils/urlSecurity")
const { safeTrustedApiUrl } = require("@/utils/requestSecurity")

export default {
  components: { UploadQueue },
  inject: { elForm: { default: null } },
  created() { this.registerUploadFormGuard() },
  name: "FileUpload",
  props: {
    contextKey: { type: [String, Number], default: '' },
    // 值
    value: [String, Object, Array],
    // 上传接口地址
    action: {
      type: String,
      default: "/file/upload"
    },
    // 上传携带的参数
    data: {
      type: Object
    },
    // 数量限制
    limit: {
      type: Number,
      default: 5
    },
    // 大小限制(MB)
    fileSize: {
      type: Number,
      default: 5
    },
    // 文件类型, 例如['png', 'jpg', 'jpeg']
    fileType: {
      type: Array,
      default: () => ["doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "pdf"]
    },
    // 是否显示提示
    isShowTip: {
      type: Boolean,
      default: true
    },
    // 禁用组件（仅查看文件）
    disabled: {
      type: Boolean,
      default: false
    },
    // 拖动排序
    drag: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      ...uploadProgressData(),
      lastEmittedValue: null,
      uploadOperations: {},
      uploadPeriodResults: {},
      uploadTombstones: [],
      uploadRetiredIdentities: new WeakSet(),
      uploadSelectionSequence: 0,
      uploadBusyPeriodId: 0,
      uploadLoadingOwned: false,
      uploadDestroyed: false,
      uploadFileUrl: safeTrustedApiUrl(process.env.VUE_APP_BASE_API, this.action), // 上传文件服务器地址
      headers: buildSessionAuthHeaders(getToken()),
      withCredentials: shouldUseSessionCredentials(),
      fileList: []
    }
  },
  mounted() {
    if (this.drag && !this.disabled) {
      this.$nextTick(() => {
        const element = this.$refs.uploadFileList?.$el || this.$refs.uploadFileList
        Sortable.create(element, {
          ghostClass: 'file-upload-darg',
          onEnd: (evt) => {
            const movedItem = this.fileList.splice(evt.oldIndex, 1)[0]
            this.fileList.splice(evt.newIndex, 0, movedItem)
            this.lastEmittedValue = this.listToString(this.fileList)
            this.$emit("input", this.lastEmittedValue)
          }
        })
      })
    }
  },
  beforeDestroy() {
    this.disposeUploadProgress()
    if (this.uploadDestroyed) return
    this.uploadDestroyed = true
    Object.keys(this.uploadOperations).forEach(uid => {
      const operation = this.uploadOperations[uid]
      this.rememberUploadTombstone(operation)
      this.deleteUploadOperation(uid)
    })
    this.uploadPeriodResults = {}
    this.closeOwnedUploadLoading()
  },
  watch: {
    contextKey() { this.resetUploadProgressScope() },
    '$route.fullPath'() { this.resetUploadProgressScope() },
    disabled(value) { if (value) this.resetUploadProgressScope() },
    uploadFormModel() { this.resetUploadProgressScope() },
    '$store.getters.id'() { this.resetUploadProgressScope() },
    action(value) {
      this.resetUploadProgressScope()
      this.uploadFileUrl = safeTrustedApiUrl(process.env.VUE_APP_BASE_API, value)
    },
    value: {
      handler(val) {
        const values = Array.isArray(val) ? val : typeof val === 'string' ? val.split(',') : val ? [val] : []
        const incoming = this.listToString(values.map(item => typeof item === 'string' ? { url: item } : item))
        if (incoming === this.lastEmittedValue) return
        this.resetUploadProgressScope()
        if (val) {
          let temp = 1
          // 首先将值转为数组
          const list = Array.isArray(val)
            ? val
            : (typeof val === "string" ? val.split(",") : [val])
          // 然后将数组转为对象数组
          this.fileList = list.map(item => {
            if (typeof item === "string") {
              item = { name: item, url: item }
            } else {
              item = Object.assign({}, item)
            }
            item.uid = item.uid || new Date().getTime() + temp++
            return item
          })
        } else {
          this.fileList = []
          return []
        }
      },
      deep: true,
      immediate: true
    }
  },
  computed: {
    widgetFileList() { return this.uploadWidgetFileList() },
    uploadFormModel() { return this.elForm && this.elForm.model },
    // 是否显示提示
    showTip() {
      return this.isShowTip && (this.fileType || this.fileSize)
    },
  },
  methods: {
    ...uploadProgressMethods,
    // 上传前校检格式和大小
    handleBeforeUpload(file) {
      applySessionAuthHeaders(this.headers, getToken())
      if (!this.uploadFileUrl) {
        this.$modal.msgError("上传地址不符合安全策略")
        return false
      }
      // 校检文件类型
      if (this.fileType) {
        const fileName = file.name.split('.')
        const fileExt = fileName[fileName.length - 1].trim().toLowerCase()
        const allowedFileTypes = this.fileType.map(type => String(type).trim().toLowerCase())
        const isTypeOk = allowedFileTypes.indexOf(fileExt) >= 0
        if (!isTypeOk) {
          this.$modal.msgError(`文件格式不正确，请上传${this.fileType.join("/")}格式文件!`)
          return false
        }
      }
      // 校检文件名是否包含特殊字符
      if (file.name.includes(',')) {
        this.$modal.msgError('文件名不正确，不能包含英文逗号!')
        return false
      }
      // 校检文件大小
      if (this.fileSize) {
        const isLt = file.size / 1024 / 1024 < this.fileSize
        if (!isLt) {
          this.$modal.msgError(`上传文件大小不能超过 ${this.fileSize} MB!`)
          return false
        }
      }
      const uid = this.uploadUid(file)
      const fileIdentity = this.uploadFileIdentity(file)
      if (!uid || !fileIdentity || this.uploadDestroyed || this.uploadOperations[uid] ||
        this.uploadRetiredIdentities.has(fileIdentity) ||
        this.uploadTombstones.some(item => item.uid === uid && item.fileIdentity === fileIdentity)) return false
      const pendingBefore = this.pendingUploadCount()
      if (pendingBefore === 0) {
        this.uploadBusyPeriodId += 1
        // A local per-file queue replaces the fullscreen loading overlay.
        this.$set(this.uploadPeriodResults, this.uploadBusyPeriodId, { successes: [] })
      }
      this.$set(this.uploadOperations, uid, {
        uid,
        fileIdentity,
        order: ++this.uploadSelectionSequence,
        busyPeriodId: this.uploadBusyPeriodId,
        state: "pending",
        result: null,
        committed: false,
        removeHandled: false
      })
      this.beginUploadTask(file)
      return true
    },
    // 文件个数超出
    handleExceed() {
      this.$modal.msgError(`上传文件数量不能超过 ${this.limit} 个!`)
    },
    // 上传失败
    handleUploadError(err, file) {
      this.settleUploadOperation(file, "network_failed", null, "上传文件失败，请重试")
    },
    // 上传成功回调
    handleUploadSuccess(res, file) {
      const url = res && res.code === 200 && res.data && typeof res.data.url === "string"
        ? res.data.url.trim()
        : ""
      const safeUrl = sanitizeFileUrl(url)
      if (safeUrl) {
        this.settleUploadOperation(file, "succeeded", { name: safeUrl, url: safeUrl })
        return
      }
      const message = res && res.code !== 200 && res.msg
        ? res.msg
        : (url ? "上传响应包含不安全的文件地址，已拒绝保存" : "上传成功响应缺少文件地址，请重试")
      this.settleUploadOperation(file, "business_failed", null, message)
    },
    // 编辑时仅移除表单引用；业务保存前不能删除仍被原记录使用的文件。
    handleDelete(file) {
      const uid = this.uploadUid(file)
      const index = this.fileList.findIndex(value => value === file ||
        (uid ? this.uploadUid(value) === uid : file && value.url === file.url))
      if (index < 0) return
      this.fileList.splice(index, 1)
      this.lastEmittedValue = this.listToString(this.fileList)
      this.$emit("input", this.listToString(this.fileList))
    },
    uploadUid(file) {
      const value = file && file.uid !== undefined
        ? file.uid
        : (file && file.raw && file.raw.uid)
      return value === undefined || value === null || String(value) === "" ? "" : String(value)
    },
    uploadFileIdentity(file) {
      return file && (file.raw || file)
    },
    safeFileHref(file) {
      return sanitizeFileUrl(file && (file.url || file.name))
    },
    pendingUploadCount() {
      return Object.keys(this.uploadOperations).reduce((count, uid) => {
        return count + (this.uploadOperations[uid].state === "pending" ? 1 : 0)
      }, 0)
    },
    retireUploadOperation(file) {
      const uid = this.uploadUid(file), operation = this.uploadOperations[uid]
      if (!operation || operation.state !== 'pending') return
      operation.state = 'canceled'
      this.rememberUploadTombstone(operation)
      this.deleteUploadOperation(uid)
      if (this.pendingUploadCount() === 0) this.completeUploadBusyPeriod(operation.busyPeriodId)
    },
    settleUploadOperation(file, state, result, message) {
      if (!this.uploadTaskCurrent(file && this.uploadTasks[this.uploadUid(file)])) return
      const uid = this.uploadUid(file)
      const operation = uid && this.uploadOperations[uid]
      if (this.uploadDestroyed || !operation || operation.state !== "pending" ||
        operation.fileIdentity !== this.uploadFileIdentity(file)) return
      const pendingBefore = this.pendingUploadCount()
      this.finishUploadTask(file, state === 'succeeded' ? 'succeeded' : 'failed', message, result && result.url)
      operation.state = state
      operation.result = result
      const period = this.uploadPeriodResults[operation.busyPeriodId]
      if (state === "succeeded") {
        if (period) period.successes.push({ order: operation.order, result })
      } else {
        if (state === "business_failed" && !operation.removeHandled && this.$refs.fileUpload) {
          operation.removeHandled = true
          this.$refs.fileUpload.handleRemove(file)
        }
        if (message) this.$modal.msgError(message)
      }
      this.rememberUploadTombstone(operation)
      this.deleteUploadOperation(uid)
      if (pendingBefore > 0 && this.pendingUploadCount() === 0) {
        this.completeUploadBusyPeriod(operation.busyPeriodId)
      }
    },
    completeUploadBusyPeriod(busyPeriodId) {
      const period = this.uploadPeriodResults[busyPeriodId]
      const completed = (period ? period.successes : [])
        .sort((left, right) => left.order - right.order)
      if (completed.length && !this.uploadProgressResetting && !this.uploadProgressDestroyed) {
        this.fileList = this.fileList.concat(completed.map(item => item.result))
        this.commitUploadedTasks()
        this.lastEmittedValue = this.listToString(this.fileList)
            this.$emit("input", this.lastEmittedValue)
      }
      if (typeof this.$delete === "function") this.$delete(this.uploadPeriodResults, busyPeriodId)
      else delete this.uploadPeriodResults[busyPeriodId]
      this.closeOwnedUploadLoading()
    },
    rememberUploadTombstone(operation) {
      if (!operation || !operation.uid || !operation.fileIdentity) return
      this.uploadRetiredIdentities.add(operation.fileIdentity)
      this.uploadTombstones.push({ uid: operation.uid, fileIdentity: operation.fileIdentity })
      if (this.uploadTombstones.length > 32) {
        this.uploadTombstones.splice(0, this.uploadTombstones.length - 32)
      }
    },
    deleteUploadOperation(uid) {
      if (typeof this.$delete === "function") this.$delete(this.uploadOperations, uid)
      else delete this.uploadOperations[uid]
    },
    closeOwnedUploadLoading() {
      if (!this.uploadLoadingOwned) return
      this.uploadLoadingOwned = false
      this.$modal.closeLoading()
    },
    // 获取文件名称
    getFileName(name) {
      // 如果是url那么取最后的名字 如果不是直接返回
      if (name.lastIndexOf("/") > -1) {
        return name.slice(name.lastIndexOf("/") + 1)
      } else {
        return name
      }
    },
    // 对象转成指定字符串分隔
    listToString(list, separator) {
      let strs = ""
      separator = separator || ","
      for (let i in list) {
        const safeUrl = sanitizeFileUrl(list[i] && list[i].url)
        if (safeUrl) strs += safeUrl + separator
      }
      return strs != '' ? strs.substr(0, strs.length - 1) : ''
    }
  }
}
</script>

<style scoped lang="scss">
.file-upload-darg {
  opacity: 0.5;
  background: #c8ebfb;
}
.upload-file-uploader {
  margin-bottom: 5px;
}
.upload-file-list .el-upload-list__item {
  border: 1px solid #e4e7ed;
  line-height: 2;
  margin-bottom: 10px;
  position: relative;
}
.upload-file-list .ele-upload-list__item-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: inherit;
}
.attachment-remove-button {
  margin-right: 10px;
  padding: 4px 8px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: #c23535;
  cursor: pointer;
  font: inherit;
}
.attachment-remove-button:focus-visible {
  outline: 2px solid #0b6b53;
  outline-offset: 2px;
}
.unsafe-file-link {
  color: #909399;
  cursor: not-allowed;
}
</style>
