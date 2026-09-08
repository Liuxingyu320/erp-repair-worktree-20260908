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
      :on-exceed="handleExceed"
      ref="imageUpload"
      :on-remove="handleDelete"
      :show-file-list="true"
      :headers="headers"
      :with-credentials="withCredentials"
      :file-list="fileList"
      :accept="accept"
      :on-preview="handlePictureCardPreview"
      :class="{hide: this.fileList.length >= this.limit}"
    >
      <i class="el-icon-plus"></i>
    </el-upload>

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
const { safeTrustedApiUrl } = require("@/utils/requestSecurity")
const { sanitizeFileUrl } = require("@/utils/urlSecurity")

export default {
  props: {
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
      default: true
    }
  },
  data() {
    return {
      number: 0,
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
        Sortable.create(element, {
          onEnd: (evt) => {
            const movedItem = this.fileList.splice(evt.oldIndex, 1)[0]
            this.fileList.splice(evt.newIndex, 0, movedItem)
            this.$emit("input", this.listToString(this.fileList))
          }
        })
      })
    }
  },
  watch: {
    value: {
      handler(val) {
        if (val) {
          // 首先将值转为数组
          const list = Array.isArray(val) ? val : this.value.split(',')
          // 然后将数组转为对象数组
          this.fileList = list.map(item => {
            if (typeof item === "string") {
              item = { name: item, url: item }
            }
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
    // 是否显示提示
    showTip() {
      return this.isShowTip && (this.fileType || this.fileSize)
    },
  },
  methods: {
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
      let isImg = false
      if (this.fileType.length) {
        let fileExtension = ""
        if (file.name.lastIndexOf(".") > -1) {
          fileExtension = file.name.slice(file.name.lastIndexOf(".") + 1)
        }
        isImg = this.fileType.some(type => {
          if (file.type.indexOf(type) > -1) return true
          if (fileExtension && fileExtension.indexOf(type) > -1) return true
          return false
        })
      } else {
        isImg = file.type.indexOf("image") > -1
      }

      if (!isImg) {
        this.$modal.msgError(`文件格式不正确，请上传${this.fileType.join("/")}图片格式文件!`)
        return false
      }
      if (file.name.includes(',')) {
        this.$modal.msgError('文件名不正确，不能包含英文逗号!')
        return false
      }
      if (this.fileSize) {
        const isLt = file.size / 1024 / 1024 < this.fileSize
        if (!isLt) {
          this.$modal.msgError(`上传头像图片大小不能超过 ${this.fileSize} MB!`)
          return false
        }
      }
      this.$modal.loading("正在上传图片，请稍候...")
      this.number++
      return this.compressImageFile(file)
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
      const url = res && res.code === 200 && res.data
        ? sanitizeFileUrl(res.data.url)
        : ""
      if (url) {
        this.uploadList.push({ name: url, url })
        this.uploadedSuccessfully()
      } else {
        this.number--
        this.$modal.closeLoading()
        this.$modal.msgError(res && res.code !== 200 && res.msg
          ? res.msg
          : "上传响应包含不安全的图片地址，已拒绝保存")
        this.$refs.imageUpload.handleRemove(file)
        this.uploadedSuccessfully()
      }
    },
    // 删除图片
    handleDelete(file) {
      const findex = this.fileList.map(f => f.name).indexOf(file.name)
      if (findex > -1) {
        const removeFromValue = () => {
          this.fileList.splice(findex, 1)
          this.$emit("input", this.listToString(this.fileList))
        }
        if (!this.deleteOnRemove) {
          removeFromValue()
          return
        }
        this.deleteRemoteFile(this.fileList[findex]).finally(removeFromValue)
      }
    },
    deleteRemoteFile(file) {
      const fileUrl = file && (file.url || file.name)
      const safeUrl = sanitizeFileUrl(fileUrl)
      if (!safeUrl) {
        return Promise.resolve()
      }
      return deleteFile(safeUrl).catch(() => {})
    },
    // 上传失败
    handleUploadError(error, file) {
      if (this.number > 0) {
        this.number--
      }
      this.$modal.msgError("上传图片失败，请重试")
      this.$modal.closeLoading()
      if (file && this.$refs.imageUpload) {
        this.$refs.imageUpload.handleRemove(file)
      }
      this.uploadedSuccessfully()
    },
    // 上传结束处理
    uploadedSuccessfully() {
      if (this.number > 0 && this.uploadList.length === this.number) {
        this.fileList = this.fileList.concat(this.uploadList)
        this.uploadList = []
        this.number = 0
        this.$emit("input", this.listToString(this.fileList))
        this.$modal.closeLoading()
      }
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
          strs += safeUrl.replace(this.baseUrl, "") + separator
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

