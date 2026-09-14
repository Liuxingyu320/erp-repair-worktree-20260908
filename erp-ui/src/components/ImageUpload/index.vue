<template>
  <div class="component-upload-image">
    <el-upload
      multiple
      :disabled="disabled"
      :action="uploadImgUrl"
      list-type="picture-card"
      :on-success="handleUploadSuccess"
      :before-upload="handleBeforeUpload"
      :data="data"
      :limit="limit"
      :on-error="handleUploadError"
      :on-progress="handleUploadProgress"
      :http-request="uploadHttpRequest"
      :before-remove="canRemoveWidgetFile"
      :on-exceed="handleExceed"
      ref="imageUpload"
      :on-remove="handleDelete"
      :show-file-list="true"
      :headers="headers"
      :with-credentials="withCredentials"
      :file-list="widgetFileList"
      :accept="accept"
      :on-preview="handlePictureCardPreview"
      :class="{hide: this.fileList.length >= this.limit}"
    >
      <i class="el-icon-plus"></i>
    </el-upload>
    <upload-queue :items="Object.values(uploadTasks)" @cancel="cancelQueuedUpload" @retry="retryQueuedUpload" @dismiss="dismissQueuedUpload" />

    <!-- 上传提示 -->
    <div class="el-upload__tip" slot="tip" v-if="showTip && !disabled">
      请上传
      <template v-if="fileSize"> 大小不超过 <b style="color: #f56c6c">{{ fileSize }}MB</b> </template>
      <template v-if="fileType"> 格式为 <b style="color: #f56c6c">{{ fileType.join("/") }}</b> </template>
      的文件
    </div>

    <el-dialog
      :visible.sync="dialogVisible"
      title="预览"
      width="800"
      append-to-body
    >
      <img
        :src="dialogImageUrl"
        style="display: block; max-width: 100%; margin: 0 auto"
      />
    </el-dialog>
  </div>
</template>

<script>
import { getToken } from "@/utils/auth"
import { applySessionAuthHeaders, buildSessionAuthHeaders, shouldUseSessionCredentials } from "@/utils/sessionMode"
import { deleteFile } from "@/api/system/file"
import Sortable from 'sortablejs'
import UploadQueue from '@/components/UploadQueue'
import { uploadProgressData, uploadProgressMethods } from '@/utils/uploadProgress'
const { safeTrustedApiUrl } = require("@/utils/requestSecurity")
const { sanitizeFileUrl } = require("@/utils/urlSecurity")

export default {
  components: { UploadQueue },
  inject: { elForm: { default: null } },
  created() { this.registerUploadFormGuard() },
  props: {
    contextKey: { type: [String, Number], default: '' },
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
    // 图片数量限制
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
      default: () => ["png", "jpg", "jpeg"]
    },
    accept: {
      type: String,
      default: ""
    },
    capture: {
      type: String,
      default: ""
    },
    compress: {
      type: Boolean,
      default: false
    },
    compressMaxWidth: {
      type: Number,
      default: 1600
    },
    compressMaxHeight: {
      type: Number,
      default: 1600
    },
    compressQuality: {
      type: Number,
      default: 0.82
    },
    compressMinSize: {
      type: Number,
      default: 0.3
    },
    // 是否显示提示
    isShowTip: {
      type: Boolean,
      default: true
    },
    // 禁用组件（仅查看图片）
    disabled: {
      type: Boolean,
      default: false
    },
    // 拖动排序
    drag: {
      type: Boolean,
      default: true
    },
    // 表单编辑场景可关闭即时远程删除，避免取消编辑后原记录引用失效。
    deleteOnRemove: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      ...uploadProgressData(),
      number: 0,
      uploadGeneration: 0,
      pendingUploads: {},
      uploadSequence: 0,
      sortable: null,
      lastEmittedValue: null,
      uploadList: [],
      dialogImageUrl: "",
      dialogVisible: false,
      hideUpload: false,
      uploadImgUrl: safeTrustedApiUrl(process.env.VUE_APP_BASE_API, this.action), // 上传的图片服务器地址
      headers: buildSessionAuthHeaders(getToken()),
      withCredentials: shouldUseSessionCredentials(),
      fileList: []
    }
  },
  mounted() {
    this.applyNativeInputAttributes()
    if (this.drag && !this.disabled) {
      this.$nextTick(() => {
        const element = this.$refs.imageUpload?.$el?.querySelector('.el-upload-list')
        if (!element) return
        this.sortable = Sortable.create(element, {
          onMove: () => this.number === 0,
          onEnd: (evt) => {
            const movedItem = this.fileList.splice(evt.oldIndex, 1)[0]
            this.fileList.splice(evt.newIndex, 0, movedItem)
            this.emitValue()
          }
        })
      })
    }
  },
  watch: {
    contextKey() { this.resetUploadProgressScope() },
    '$route.fullPath'() { this.resetUploadProgressScope() },
    disabled(value) { if (value) this.resetUploadProgressScope() },
    uploadFormModel() { this.resetUploadProgressScope() },
    '$store.getters.id'() { this.resetUploadProgressScope() },
    action(value) {
      this.resetUploadProgressScope()
      this.uploadImgUrl = safeTrustedApiUrl(process.env.VUE_APP_BASE_API, value)
    },
    value: {
      handler(val) {
        const incoming = this.listToString(this.normalizeValue(val))
        if (incoming === this.lastEmittedValue) return
        this.resetUploadBatch()
        this.fileList = this.normalizeValue(val)

      },
      deep: true,
      immediate: true
    }
  },
  beforeDestroy() {
    this.disposeUploadProgress()
    this.resetUploadBatch()
    if (this.sortable) this.sortable.destroy()
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
    normalizeValue(value) {
      const list = Array.isArray(value) ? value : typeof value === "string" ? value.split(",") : value ? [value] : []
      return list.map(item => typeof item === "string" ? { name: item, url: item } : { ...item })
        .filter(item => sanitizeFileUrl(item && item.url))
        .map(item => ({ ...item, url: sanitizeFileUrl(item.url) }))
    },
    emitValue() {
      const urls = this.fileList.map(file => sanitizeFileUrl(file.url)).filter(Boolean)
      this.lastEmittedValue = urls.join(",")
      this.$emit("input", Array.isArray(this.value) ? urls : this.lastEmittedValue)
    },
    resetUploadBatch() {
      this.resetUploadProgressScope()
      this.uploadGeneration++
      if (this.$refs.imageUpload && this.$refs.imageUpload.abort) this.$refs.imageUpload.abort()
      this.pendingUploads = {}
      this.uploadList = []
      // Pending uploads are retired locally; never close another page's loading overlay.
      this.number = 0
      this.lastEmittedValue = null
    },
    finishUpload(file, url) {
      const key = file && String(file.uid)
      const pending = key && this.pendingUploads[key]
      if (!pending || pending.generation !== this.uploadGeneration || pending.done) return false
      pending.done = true
      pending.url = url || ""
      this.number = Math.max(0, this.number - 1)
      this.uploadedSuccessfully()
      return true
    },
    applyNativeInputAttributes() {
      this.$nextTick(() => {
        const input = this.$refs.imageUpload && this.$refs.imageUpload.$el
          ? this.$refs.imageUpload.$el.querySelector('input[type="file"]')
          : null
        if (!input) return
        if (this.accept) input.setAttribute("accept", this.accept)
        if (this.capture) input.setAttribute("capture", this.capture)
      })
    },
    // 上传前loading加载
    handleBeforeUpload(file) {
      applySessionAuthHeaders(this.headers, getToken())
      if (!this.uploadImgUrl) {
        this.$modal.msgError("上传地址不符合安全策略")
        return false
      }
      const extension = (file.name.split(".").pop() || "").toLowerCase()
      const mime = (file.type || "").toLowerCase()
      const isImg = this.fileType.length
        ? this.fileType.map(type => type.toLowerCase()).includes(extension) && /^image\//.test(mime)
        : /^image\//.test(mime)

      if (!isImg) {
        this.$modal.msgError(`文件格式不正确，请上传${this.fileType.join("/")}图片格式文件!`)
        return false
      }
      if (file.name.includes(',')) {
        this.$modal.msgError('文件名不正确，不能包含英文逗号!')
        return false
      }
      if (this.fileSize) {
        const isLt = file.size / 1024 / 1024 <= this.fileSize
        if (!isLt) {
          this.$modal.msgError(`上传图片大小不能超过 ${this.fileSize} MB!`)
          return false
        }
      }
      this.beginUploadTask(file)
      const generation = this.uploadGeneration
      this.pendingUploads[String(file.uid)] = { generation, order: this.uploadSequence++, done: false, url: "" }
      this.number++
      return this.compressImageFile(file).then(result => {
        if (generation !== this.uploadGeneration || !this.uploadTaskCurrent(this.uploadTasks[String(file.uid)]) || this.uploadTasks[String(file.uid)].status !== "uploading") return Promise.reject(new Error("图片上传已取消"))
        return result
      }).catch(error => {
        const task = this.uploadTasks[String(file.uid)]
        if (this.uploadTaskCurrent(task) && task.status === 'uploading') {
          this.finishUploadTask(file, 'failed', '图片准备失败，请重试或移除')
          this.finishUpload(file, '')
        }
        return Promise.reject(error)
      })
    },
    compressImageFile(file) {
      if (!this.shouldCompressImage(file)) {
        return Promise.resolve(file)
      }
      return new Promise(resolve => {
        const reader = new FileReader()
        reader.onload = event => {
          const image = new Image()
          image.onload = () => {
            const ratio = Math.min(
              this.compressMaxWidth / image.width,
              this.compressMaxHeight / image.height,
              1
            )
            if (ratio >= 1) {
              resolve(file)
              return
            }
            const canvas = document.createElement("canvas")
            canvas.width = Math.round(image.width * ratio)
            canvas.height = Math.round(image.height * ratio)
            const context = canvas.getContext("2d")
            if (!context) {
              resolve(file)
              return
            }
            context.drawImage(image, 0, 0, canvas.width, canvas.height)
            const outputType = file.type === "image/png" ? "image/jpeg" : file.type
            canvas.toBlob(blob => {
              if (!blob || blob.size >= file.size) {
                resolve(file)
                return
              }
              resolve(this.createCompressedFile(file, blob, outputType))
            }, outputType, this.compressQuality)
          }
          image.onerror = () => resolve(file)
          image.src = event.target.result
        }
        reader.onerror = () => resolve(file)
        reader.readAsDataURL(file)
      })
    },
    shouldCompressImage(file) {
      if (!this.compress || !file || !file.type) return false
      if (!/^image\/(jpeg|jpg|png|webp)$/.test(file.type)) return false
      return file.size / 1024 / 1024 > this.compressMinSize
    },
    createCompressedFile(file, blob, outputType) {
      const extension = outputType === "image/jpeg" ? "jpg" : outputType.replace("image/", "")
      const name = file.name.replace(/\.[^.]+$/, "") + "." + extension
      try {
        return new File([blob], name, { type: outputType, lastModified: Date.now() })
      } catch (error) {
        blob.name = name
        blob.lastModified = Date.now()
        return blob
      }
    },
    // 文件个数超出
    handleExceed() {
      this.$modal.msgError(`上传文件数量不能超过 ${this.limit} 个!`)
    },
    // 上传成功回调
    handleUploadSuccess(res, file) {
      if (!this.uploadTaskCurrent(file && this.uploadTasks[String(file.uid)])) return
      const pending = file && this.pendingUploads[String(file.uid)]
      if (!pending || pending.done || pending.generation !== this.uploadGeneration) return
      const url = res && res.code === 200 && res.data ? sanitizeFileUrl(res.data.url) : ""
      if (!url) {
        // Element removes network failures itself; business failures need only a UID-based list update.
        const upload = this.$refs.imageUpload
        if (upload && upload.uploadFiles) upload.uploadFiles = upload.uploadFiles.filter(item => item.uid !== file.uid)
        this.$modal.msgError(res && res.code !== 200 && res.msg ? res.msg : "上传响应包含不安全的图片地址，已拒绝保存")
      }
      this.finishUploadTask(file, url ? 'succeeded' : 'failed', url ? '' : '上传响应无有效图片，请重试或移除', url)
      this.finishUpload(file, url)
    },
    retireUploadOperation(file) {
      const pending = file && this.pendingUploads[String(file.uid)]
      if (pending && !pending.done) this.finishUpload(file, '')
    },
    handleDelete(file) {
      const task = file && this.uploadTasks[String(file.uid)]
      if (task && task.status === 'uploading') this.cancelQueuedUpload(task)
      if (task && task.status === 'succeeded') task.status = 'removed'
      const url = file && sanitizeFileUrl(file.url)
      const pending = file && this.pendingUploads[String(file.uid)]
      if (pending && pending.done) pending.url = ""
      if (pending && !pending.done) this.finishUpload(file, "")
      const matches = item => file && file.uid != null && item.uid != null ? item.uid === file.uid : item.url === url
      const existing = this.fileList.find(matches)
      if (!existing) return
      this.fileList = this.fileList.filter(item => !matches(item))
      this.emitValue()
      if (this.deleteOnRemove) this.deleteRemoteFile(existing)
    },
    deleteRemoteFile(file) {
      const safeUrl = sanitizeFileUrl(file && (file.url || file.name))
      return safeUrl ? deleteFile(safeUrl).catch(() => {}) : Promise.resolve()
    },
    handleUploadError(error, file) {
      // Do not call Element.handleRemove: its error callback already removed this exact UID.
      if (!this.uploadTaskCurrent(file && this.uploadTasks[String(file.uid)])) return
      this.finishUploadTask(file, 'failed', '上传图片失败，请重试或移除')
      if (this.finishUpload(file, "")) this.$modal.msgError("上传图片失败，请重试")
    },
    uploadedSuccessfully() {
      if (this.number > 0) return
      const successful = Object.values(this.pendingUploads).filter(item => item.done && item.url)
        .sort((a, b) => a.order - b.order).map(item => ({ name: item.url, url: item.url }))
      if (!this.uploadProgressResetting && !this.uploadProgressDestroyed) this.fileList = this.fileList.concat(successful)
      this.commitUploadedTasks()
      this.pendingUploads = {}
      this.uploadList = []
      if (!this.uploadProgressResetting && !this.uploadProgressDestroyed) this.emitValue()
    },
    // 预览
    handlePictureCardPreview(file) {
      const url = sanitizeFileUrl(file && file.url)
      if (!url) {
        this.$modal.msgError("图片地址不符合安全策略")
        return
      }
      this.dialogImageUrl = url
      this.dialogVisible = true
    },
    // 对象转成指定字符串分隔
    listToString(list, separator) {
      let strs = ""
      separator = separator || ","
      for (let i in list) {
        const safeUrl = sanitizeFileUrl(list[i] && list[i].url)
        if (safeUrl) {
          strs += safeUrl + separator
        }
      }
      return strs != '' ? strs.substr(0, strs.length - 1) : ''
    }
  }
}
</script>
<style scoped lang="scss">
// .el-upload--picture-card 控制加号部分
::v-deep.hide .el-upload--picture-card {
  display: none;
}

::v-deep .el-upload-list--picture-card.is-disabled + .el-upload--picture-card {
  display: none !important;
} 

// 去掉动画效果
::v-deep .el-list-enter-active,
::v-deep .el-list-leave-active {
  transition: none;
}

::v-deep .el-list-enter, .el-list-leave-active {
  opacity: 0;
  transform: translateY(0);
}
</style>

