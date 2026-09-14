<template>
  <div>
    <el-upload
      :action="uploadUrl"
      :before-upload="handleBeforeUpload"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      name="file"
      :show-file-list="false"
      :headers="headers"
      :with-credentials="withCredentials"
      style="display: none"
      ref="upload"
      v-if="this.type == 'url'"
    >
    </el-upload>
    <div class="editor" ref="editor" :style="styles"></div>
  </div>
</template>

<script>
import axios from "axios"
import Quill from "quill"
import "quill/dist/quill.core.css"
import "quill/dist/quill.snow.css"
import "quill/dist/quill.bubble.css"
import { getToken } from "@/utils/auth"
import { applySessionAuthHeaders, buildSessionAuthHeaders, shouldUseSessionCredentials } from "@/utils/sessionMode"
const { safeTrustedApiUrl } = require("@/utils/requestSecurity")
const { sanitizeFileUrl } = require("@/utils/urlSecurity")

export default {
  name: "Editor",
  props: {
    /* 编辑器的内容 */
    value: {
      type: String,
      default: "",
    },
    /* 高度 */
    height: {
      type: Number,
      default: null,
    },
    /* 最小高度 */
    minHeight: {
      type: Number,
      default: null,
    },
    /* 只读 */
    readOnly: {
      type: Boolean,
      default: false,
    },
    /* 可读名称 */
    ariaLabel: {
      type: String,
      default: "富文本内容",
    },
    /* 上传文件大小限制(MB) */
    fileSize: {
      type: Number,
      default: 5,
    },
    /* 类型（base64格式、url格式） */
    type: {
      type: String,
      default: "url",
    }
  },
  data() {
    return {
      uploadUrl: safeTrustedApiUrl(process.env.VUE_APP_BASE_API, "/file/upload"), // 上传的图片服务器地址
      headers: buildSessionAuthHeaders(getToken()),
      withCredentials: shouldUseSessionCredentials(),
      Quill: null,
      currentValue: "",
      lastSelectionIndex: 0,
      options: {
        theme: "snow",
        bounds: document.body,
        debug: "warn",
        modules: {
          // 工具栏配置
          toolbar: [
            ["bold", "italic", "underline", "strike"],       // 加粗 斜体 下划线 删除线
            ["blockquote", "code-block"],                    // 引用  代码块
            [{ list: "ordered" }, { list: "bullet" }],       // 有序、无序列表
            [{ indent: "-1" }, { indent: "+1" }],            // 缩进
            [{ size: ["small", false, "large", "huge"] }],   // 字体大小
            [{ header: [1, 2, 3, 4, 5, 6, false] }],         // 标题
            [{ color: [] }, { background: [] }],             // 字体颜色、字体背景颜色
            [{ align: [] }],                                 // 对齐方式
            ["clean"],                                       // 清除文本格式
            ["link", "image", "video"]                       // 链接、图片、视频
          ],
        },
        placeholder: "请输入内容",
        readOnly: this.readOnly,
      },
    }
  },
  computed: {
    styles() {
      let style = {}
      if (this.minHeight) {
        style.minHeight = `${this.minHeight}px`
      }
      if (this.height) {
        style.height = `${this.height}px`
      }
      return style
    }
  },
  watch: {
    readOnly(value) {
      if (this.Quill) this.Quill.enable(!value)
    },
    value: {
      handler(val) {
        if (val !== this.currentValue) {
          this.currentValue = val === null ? "" : val
          if (this.Quill) {
            this.Quill.clipboard.dangerouslyPasteHTML(this.currentValue)
          }
        }
      },
      immediate: true,
    },
  },
  mounted() {
    this.init()
  },
  beforeDestroy() {
    this.Quill = null
  },
  methods: {
    init() {
      const editor = this.$refs.editor
      this.Quill = new Quill(editor, this.options)
      this.applyAccessibleNames()
      // 如果设置了上传地址则自定义图片上传事件
      if (this.type == 'url') {
        let toolbar = this.Quill.getModule("toolbar")
        toolbar.addHandler("image", (value) => {
          if (value) {
            this.$refs.upload.$children[0].$refs.input.click()
          } else {
            this.Quill.format("image", false)
          }
        })
        this.Quill.root.addEventListener('paste', this.handlePasteCapture, true)
      }
      this.Quill.clipboard.dangerouslyPasteHTML(this.currentValue)
      this.Quill.on("text-change", (delta, oldDelta, source) => {
        const html = this.$refs.editor.children[0].innerHTML
        const text = this.Quill.getText()
        const quill = this.Quill
        this.currentValue = html
        this.$emit("input", html)
        this.$emit("on-change", { html, text, quill })
      })
      this.Quill.on("text-change", (delta, oldDelta, source) => {
        this.$emit("on-text-change", delta, oldDelta, source)
      })
      this.Quill.on("selection-change", (range, oldRange, source) => {
        if (range && typeof range.index === "number") {
          this.lastSelectionIndex = range.index
        }
        this.$emit("on-selection-change", range, oldRange, source)
      })
      this.Quill.on("editor-change", (eventName, ...args) => {
        this.$emit("on-editor-change", eventName, ...args)
      })
    },
    applyAccessibleNames() {
      if (!this.Quill || !this.Quill.root) return
      this.Quill.root.setAttribute("aria-label", this.ariaLabel)
      this.Quill.root.setAttribute("role", "textbox")
      this.Quill.root.setAttribute("aria-multiline", "true")
      const labels = {
        "ql-bold": "加粗", "ql-italic": "斜体", "ql-underline": "下划线", "ql-strike": "删除线",
        "ql-blockquote": "引用", "ql-code-block": "代码块", "ql-list": "列表", "ql-indent": "缩进",
        "ql-clean": "清除格式", "ql-link": "插入链接", "ql-image": "插入图片", "ql-video": "插入视频"
      }
      const toolbar = this.$refs.editor && this.$refs.editor.previousElementSibling
      if (!toolbar || !toolbar.classList.contains("ql-toolbar")) return
      toolbar.setAttribute("role", "toolbar")
      toolbar.setAttribute("aria-label", `${this.ariaLabel}工具栏`)
      toolbar.querySelectorAll("button").forEach(button => {
        const className = Array.from(button.classList).find(name => labels[name])
        let label = className ? labels[className] : "编辑器操作"
        if (className === "ql-list") label = button.value === "ordered" ? "有序列表" : "无序列表"
        if (className === "ql-indent") label = button.value === "+1" ? "增加缩进" : "减少缩进"
        button.setAttribute("aria-label", label)
        button.setAttribute("title", label)
      })
      toolbar.querySelectorAll(".ql-picker-label").forEach((picker, index) => {
        const pickerNames = ["字号", "标题级别", "文字颜色", "背景颜色", "对齐方式"]
        const label = pickerNames[index] || "格式选项"
        picker.setAttribute("aria-label", label)
        picker.setAttribute("title", label)
      })
    },
    // 上传前校检格式和大小
    handleBeforeUpload(file) {
      applySessionAuthHeaders(this.headers, getToken())
      if (!this.uploadUrl) {
        this.$message.error("上传地址不符合安全策略")
        return false
      }
      const type = ["image/jpeg", "image/jpg", "image/png", "image/svg"]
      const isJPG = type.includes(file.type)
      // 检验文件格式
      if (!isJPG) {
        this.$message.error(`图片格式错误!`)
        return false
      }
      // 校检文件大小
      if (this.fileSize) {
        const isLt = file.size / 1024 / 1024 < this.fileSize
        if (!isLt) {
          this.$message.error(`上传文件大小不能超过 ${this.fileSize} MB!`)
          return false
        }
      }
      return true
    },
    handleUploadSuccess(res, file) {
      // 如果上传成功
      const imageUrl = res && res.code == 200 && res.data
        ? sanitizeFileUrl(res.data.url)
        : ""
      if (imageUrl) {
        // 获取富文本组件实例
        let quill = this.Quill
        if (!quill) return
        // 获取光标所在位置
        let length = this.getInsertIndex()
        // 插入图片  res.url为服务器返回的图片地址
        quill.insertEmbed(length, "image", imageUrl)
        // 调整光标到最后
        quill.setSelection(length + 1)
      } else {
        this.$message.error("图片插入失败")
      }
    },
    handleUploadError() {
      this.$message.error("图片插入失败")
    },
    // 复制粘贴图片处理
    handlePasteCapture(e) {
      const clipboard = e.clipboardData || window.clipboardData
      if (clipboard && clipboard.items) {
        for (let i = 0; i < clipboard.items.length; i++) {
          const item = clipboard.items[i]
          if (item.type.indexOf('image') !== -1) {
            e.preventDefault()
            const file = item.getAsFile()
            this.insertImage(file).catch(() => {
              this.handleUploadError()
            })
          }
        }
      }
    },
    getInsertIndex() {
      const quill = this.Quill
      if (!quill) return 0
      const range = quill.getSelection(true)
      if (range && typeof range.index === "number") {
        this.lastSelectionIndex = range.index
        return range.index
      }
      return Math.min(this.lastSelectionIndex || quill.getLength(), quill.getLength())
    },
    insertImage(file) {
      applySessionAuthHeaders(this.headers, getToken())
      const formData = new FormData()
      formData.append("file", file)
      return axios.post(this.uploadUrl, formData, {
        headers: Object.assign({ "Content-Type": "multipart/form-data" }, this.headers),
        withCredentials: this.withCredentials
      }).then(res => {
        this.handleUploadSuccess(res.data)
      })
    }
  }
}
</script>

<style>
.editor, .ql-toolbar {
  white-space: pre-wrap !important;
  line-height: normal !important;
}
.quill-img {
  display: none;
}
.ql-snow .ql-tooltip[data-mode="link"]::before {
  content: "请输入链接地址:";
}
.ql-snow .ql-tooltip.ql-editing a.ql-action::after {
  border-right: 0px;
  content: "保存";
  padding-right: 0px;
}
.ql-snow .ql-tooltip[data-mode="video"]::before {
  content: "请输入视频地址:";
}
.ql-snow .ql-picker.ql-size .ql-picker-label::before,
.ql-snow .ql-picker.ql-size .ql-picker-item::before {
  content: "14px";
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="small"]::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="small"]::before {
  content: "10px";
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="large"]::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="large"]::before {
  content: "18px";
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value="huge"]::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value="huge"]::before {
  content: "32px";
}
.ql-snow .ql-picker.ql-header .ql-picker-label::before,
.ql-snow .ql-picker.ql-header .ql-picker-item::before {
  content: "文本";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="1"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="1"]::before {
  content: "标题1";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="2"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="2"]::before {
  content: "标题2";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="3"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="3"]::before {
  content: "标题3";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="4"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="4"]::before {
  content: "标题4";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="5"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="5"]::before {
  content: "标题5";
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value="6"]::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value="6"]::before {
  content: "标题6";
}
.ql-snow .ql-picker.ql-font .ql-picker-label::before,
.ql-snow .ql-picker.ql-font .ql-picker-item::before {
  content: "标准字体";
}
.ql-snow .ql-picker.ql-font .ql-picker-label[data-value="serif"]::before,
.ql-snow .ql-picker.ql-font .ql-picker-item[data-value="serif"]::before {
  content: "衬线字体";
}
.ql-snow .ql-picker.ql-font .ql-picker-label[data-value="monospace"]::before,
.ql-snow .ql-picker.ql-font .ql-picker-item[data-value="monospace"]::before {
  content: "等宽字体";
}
</style>
