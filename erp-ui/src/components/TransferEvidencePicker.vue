<template>
  <div class="transfer-evidence" aria-label="调拨凭证附件">
    <p v-if="legacy" class="evidence-note">历史附件引用（只读）：{{ legacy }}。新增凭证请上传或选择文件。</p>
    <ul v-if="selected.length" class="evidence-files">
      <li v-for="file in selected" :key="file.id">
        <img v-if="file.thumbnail" :src="file.thumbnail" :alt="file.name" class="evidence-thumbnail">
        <span>{{ file.name }}<small v-if="file.error">{{ file.error }}</small></span>
        <button v-if="file.canPreview" type="button" :disabled="disabled" @click="preview(file)">预览</button>
        <button type="button" :disabled="disabled" @click="remove(file)">移除</button>
      </li>
    </ul>
    <p v-if="!driveEnabled" class="evidence-note">附件功能暂未启用，请联系管理员核对。</p>
    <button v-else type="button" :disabled="disabled" @click="openPicker">上传 / 选择已有附件</button>
    <div v-if="pickerOpen && driveEnabled" class="evidence-picker">
      <label>云盘位置
        <select v-model="spaceId" :disabled="disabled || loading" @change="changeSpace">
          <option value="" disabled>请选择可访问空间</option>
          <option v-for="space in spaces" :key="space.spaceId" :value="space.spaceId">{{ space.spaceName }}</option>
        </select>
      </label>
      <div class="evidence-toolbar">
        <button v-if="folders.length" type="button" :disabled="loading || disabled" @click="upFolder">返回上级</button>
        <span>{{ folders.length ? folders[folders.length - 1].name : '根目录' }}</span>
        <input v-model="keyword" type="search" placeholder="搜索当前目录文件" :disabled="disabled" @keydown.enter.prevent="search">
        <button type="button" :disabled="loading || disabled" @click="search">搜索</button>
      </div>
      <div class="evidence-toolbar">
        <button type="button" :disabled="!canUpload || disabled" @click="$refs.fileInput.click()">上传文件</button>
        <button type="button" :disabled="!canUpload || disabled" @click="$refs.cameraInput.click()">拍照</button>
        <input ref="fileInput" class="evidence-hidden" type="file" multiple @change="filesChosen">
        <input ref="cameraInput" class="evidence-hidden" type="file" accept="image/*" capture="environment" @change="filesChosen">
      </div>
      <p v-if="loading">正在加载文件…</p>
      <p v-else-if="listFailed"><button type="button" @click="loadNodes(retryPage)">重试读取</button></p>
      <ul v-else class="evidence-candidates">
        <li v-for="node in nodes" :key="node.nodeId">
          <span>{{ node.nodeName }}</span>
          <button v-if="node.nodeType === 'FOLDER'" type="button" :disabled="disabled" @click="enterFolder(node)">打开</button>
          <button v-else type="button" :disabled="disabled || selected.length + tasks.length >= 10" @click="chooseNode(node)">选择</button>
        </li>
      </ul>
      <div class="evidence-toolbar">
        <button type="button" :disabled="loading || page <= 1" @click="loadNodes(page - 1)">上一页</button>
        <span>第 {{ page }} 页</span>
        <button type="button" :disabled="loading || page * pageSize >= total" @click="loadNodes(page + 1)">下一页</button>
        <button type="button" @click="pickerOpen = false">收起</button>
      </div>
    </div>
    <ul v-if="tasks.length" class="evidence-files" aria-live="polite">
      <li v-for="task in tasks" :key="task.key">
        <span>{{ task.name }} · {{ task.message }}<progress v-if="task.status === 'uploading'" :value="task.progress" max="100" /></span>
        <button v-if="task.status === 'failed'" type="button" :disabled="disabled" @click="upload(task)">重试</button>
        <button type="button" :disabled="disabled" @click="cancelTask(task)">{{ task.status === 'uploading' ? '取消上传' : '移除本次上传' }}</button>
      </li>
    </ul>
    <p v-if="error" class="evidence-error" role="alert">{{ error }}</p>
    <div v-if="previewUrl" class="evidence-preview">
      <button type="button" @click="closePreview">关闭预览</button>
      <img v-if="previewImage" :src="previewUrl" alt="凭证预览">
      <iframe v-else :src="previewUrl" title="凭证预览" sandbox />
    </div>
  </div>
</template>

<script>
import { listDriveSpaces, listDriveNodes, getDriveNode, uploadDriveFile, getDriveContent } from "@/api/drive"
const { evidenceId, parseEvidence, encodeEvidence, evidenceNode } = require("@/utils/transferEvidence")

export default {
  name: "TransferEvidencePicker",
  props: { value: { type: String, default: "" }, contextKey: { type: String, required: true }, disabled: Boolean, required: Boolean },
  data() {
    return { selected: [], legacy: "", tasks: [], generation: 0, metadataGeneration: 0, listGeneration: 0,
      sequence: 0, pickerOpen: false, spaces: [], spaceId: "", folders: [], keyword: "", nodes: [],
      page: 1, retryPage: 1, pageSize: 20, total: 0, loading: false, listFailed: false, error: "",
      metadataLoading: false, previewUrl: "", previewImage: false, previewGeneration: 0 }
  },
  computed: {
    driveEnabled() { return !!(this.$store && this.$store.getters && this.$store.getters.driveEnabled === true) },
    scopeKey() {
      const user = this.$store && this.$store.state.user || {}
      return [this.contextKey, user.id || "", this.$route && this.$route.fullPath || "", this.driveEnabled].join("|")
    },
    canUpload() {
      const space = this.spaces.find(s => s.spaceId === this.spaceId)
      return !!(this.driveEnabled && space && space.canWrite && (!this.folders.length || this.folders[this.folders.length - 1].canWrite))
    }
  },
  watch: {
    scopeKey() { this.retire(); this.readValue() },
    value() { this.readValue() },
    required() { this.state() }
  },
  created() { this.readValue() },
  beforeDestroy() { this.retire() },
  deactivated() { this.retire() },
  methods: {
    token() { return { generation: this.generation, scope: this.scopeKey } },
    current(token) { return token.generation === this.generation && token.scope === this.scopeKey && !this._isDestroyed },
    state() {
      const valid = (!this.selected.length || this.driveEnabled) && !this.metadataLoading && !this.tasks.length && !this.selected.some(f => f.error) && (!this.required || this.selected.length > 0)
      this.$emit("upload-state", { valid, pending: this.tasks.length, contextKey: this.contextKey })
      return valid
    },
    retire() {
      this.generation++; this.metadataGeneration++; this.listGeneration++
      this.tasks.forEach(task => { if (task.controller) task.controller.abort() })
      this.tasks = []; this.selected.forEach(file => { if (file.thumbnail) URL.revokeObjectURL(file.thumbnail) })
      this.selected = []; this.closePreview(); this.pickerOpen = false; this.spaces = []; this.nodes = []
      this.spaceId = ""; this.folders = []; this.loading = false; this.metadataLoading = false
    },
    async readValue() {
      const token = this.token(), version = ++this.metadataGeneration
      const parsed = parseEvidence(this.value); this.legacy = parsed.legacy
      const old = new Map(this.selected.map(file => [file.id, file]))
      this.selected.filter(file => !parsed.ids.includes(file.id)).forEach(file => { if (file.thumbnail) URL.revokeObjectURL(file.thumbnail) })
      this.selected = parsed.ids.map(id => old.get(id) || { id, name: "正在读取附件…" })
      if (!this.driveEnabled) { this.selected.forEach(file => { this.$set(file, "name", "凭证附件"); this.$set(file, "error", "附件功能暂未启用") }); this.metadataLoading = false; this.state(); return }
      this.metadataLoading = this.selected.some(file => !file.contentType); this.state()
      await Promise.all(this.selected.map(async file => {
        if (file.contentType && !file.error) return
        try {
          const response = await getDriveNode(file.id)
          if (!this.current(token) || version !== this.metadataGeneration) return
          const node = evidenceNode(response && response.data)
          if (node.id !== file.id) throw Error("附件读取结果不匹配")
          Object.keys(node).forEach(key => this.$set(file, key, node[key])); this.$delete(file, "error")
          this.thumbnail(file, token)
        } catch (_) {
          if (this.current(token) && version === this.metadataGeneration) {
            this.$set(file, "name", "凭证附件"); this.$set(file, "error", "当前无法读取，请移除后重新选择")
          }
        }
      }))
      if (this.current(token) && version === this.metadataGeneration) { this.metadataLoading = false; this.state() }
    },
    emitSelection() {
      this.legacy = ""; this.$emit("input", encodeEvidence(this.selected.map(file => file.id))); this.state()
    },
    remove(file) {
      if (this.disabled) return
      if (file.thumbnail) URL.revokeObjectURL(file.thumbnail)
      this.selected = this.selected.filter(item => item !== file); this.emitSelection()
    },
    async openPicker() {
      if (this.disabled || !this.driveEnabled) return
      this.pickerOpen = true; this.error = ""
      const token = this.token(); this.loading = true
      try {
        const response = await listDriveSpaces()
        if (!this.current(token)) return
        this.spaces = (response && response.data || []).map(s => Object.assign({}, s, { spaceId: evidenceId(s.spaceId) }))
        if (!this.spaces.length) throw Error("没有可访问的云盘空间，请联系管理员核对云盘权限")
        if (!this.spaces.some(s => s.spaceId === this.spaceId)) this.spaceId = this.spaces[0].spaceId
        await this.loadNodes(1)
      } catch (error) { if (this.current(token)) this.error = error.message || "云盘不可用，请稍后重试" }
      finally { if (this.current(token)) this.loading = false }
    },
    changeSpace() { this.folders = []; this.keyword = ""; this.loadNodes(1) },
    search() { this.loadNodes(1) },
    enterFolder(node) {
      try { this.folders.push({ id: evidenceId(node.nodeId), name: node.nodeName, canWrite: node.canWrite }); this.keyword = ""; this.loadNodes(1) }
      catch (error) { this.error = error.message }
    },
    upFolder() { this.folders.pop(); this.keyword = ""; this.loadNodes(1) },
    async loadNodes(nextPage) {
      if (!this.driveEnabled) return
      const token = this.token(), version = ++this.listGeneration
      const spaceId = this.spaceId, parentId = this.folders.length ? this.folders[this.folders.length - 1].id : "0"
      this.retryPage = nextPage
      this.nodes = []; this.loading = true; this.listFailed = false; this.error = ""
      try {
        const response = await listDriveNodes({ spaceId, parentId, keyword: this.keyword, pageNum: nextPage, pageSize: this.pageSize })
        if (!this.current(token) || version !== this.listGeneration) return
        this.nodes = (response && response.rows || []).filter(n => n && n.status === "ACTIVE").map(n => Object.assign({}, n, { nodeId: evidenceId(n.nodeId) }))
        this.total = Number(response.total || 0); this.page = nextPage
      } catch (error) {
        if (this.current(token) && version === this.listGeneration) { this.listFailed = true; this.error = error.message || "文件列表读取失败" }
      } finally { if (this.current(token) && version === this.listGeneration) this.loading = false }
    },
    chooseNode(raw) {
      if (this.disabled || !this.driveEnabled) return
      try {
        const file = evidenceNode(raw)
        if (this.selected.some(f => f.id === file.id)) return
        if (this.selected.length + this.tasks.length >= 10) throw Error("每项最多选择10个凭证附件")
        this.selected.push(file); this.emitSelection(); this.thumbnail(file, this.token())
      } catch (error) { this.error = error.message }
    },
    filesChosen(event) {
      const files = Array.from(event.target.files || []); event.target.value = ""
      if (this.disabled || !this.canUpload) return
      for (const file of files) {
        if (this.selected.length + this.tasks.length >= 10) { this.error = "每项最多选择10个凭证附件"; break }
        const task = { key: ++this.sequence, raw: file, name: file.name, status: "uploading", progress: 0,
          message: "准备上传", spaceId: this.spaceId, parentId: this.folders.length ? this.folders[this.folders.length - 1].id : "0", controller: null }
        this.tasks.push(task); this.state(); this.upload(task)
      }
    },
    async upload(task) {
      if (this.disabled || !this.driveEnabled || !this.tasks.includes(task) || task.active) return
      task.active = true
      const token = this.token(); task.controller = new AbortController(); task.status = "uploading"; task.message = "上传中"; this.state()
      try {
        const response = await uploadDriveFile(task.raw, task.spaceId, task.parentId, event => {
          if (!this.current(token) || !this.tasks.includes(task)) return
          task.progress = event.total ? Math.min(99, Math.floor(event.loaded * 100 / event.total)) : 0
          task.message = task.progress ? "上传中 " + task.progress + "%" : "上传中"
        }, task.controller.signal)
        if (!this.current(token) || !this.tasks.includes(task)) return
        const file = evidenceNode(response && response.data)
        if (!this.selected.some(f => f.id === file.id)) this.selected.push(file)
        this.tasks = this.tasks.filter(t => t !== task); this.emitSelection(); this.thumbnail(file, token)
      } catch (error) {
        if (!this.current(token) || !this.tasks.includes(task)) return
        const status = error && error.response && error.response.status
        const definite = status >= 400 && status < 500 && status !== 408
        task.status = definite ? "failed" : "unknown"
        task.message = definite ? "上传失败，可重试或移除" : "上传结果未确认，不能提交；请移除此项，再在已有附件中查找"
        this.error = error.message || task.message
      } finally { task.active = false; if (this.current(token)) this.state() }
    },
    cancelTask(task) {
      if (this.disabled) return
      this.tasks = this.tasks.filter(t => t !== task); if (task.controller) task.controller.abort(); this.error = ""; this.state()
    },
    async thumbnail(file, token) {
      if (!this.driveEnabled || !file.canPreview || !/^image\/(jpeg|png|gif|webp)$/.test(file.contentType)) return
      try {
        const blob = await getDriveContent(file.id, "preview")
        if (!this.current(token) || !this.selected.includes(file) || !blob || blob.type !== file.contentType) return
        if (file.thumbnail) URL.revokeObjectURL(file.thumbnail)
        this.$set(file, "thumbnail", URL.createObjectURL(blob))
      } catch (_) { /* The readable file name remains usable if a thumbnail is unavailable. */ }
    },
    async preview(file) {
      if (!this.driveEnabled) return
      const token = this.token(); this.closePreview(); const version = this.previewGeneration
      try {
        const blob = await getDriveContent(file.id, "preview")
        if (!this.current(token) || version !== this.previewGeneration || !this.selected.includes(file)) return
        const image = /^image\/(jpeg|png|gif|webp)$/.test(blob && blob.type)
        if (!image && (!blob || blob.type !== "application/pdf")) throw Error("此类型暂不支持凭证预览")
        this.previewImage = image; this.previewUrl = URL.createObjectURL(blob)
      } catch (error) { if (this.current(token) && version === this.previewGeneration) this.error = error.message || "凭证预览失败" }
    },
    closePreview() { this.previewGeneration++; if (this.previewUrl) URL.revokeObjectURL(this.previewUrl); this.previewUrl = "" }
  }
}
</script>

<style scoped>
.transfer-evidence { min-width: 0; font-size: 13px; overflow-wrap: anywhere; }
.transfer-evidence button { padding: 5px 8px; color: #315bb5; border: 1px solid #cbd5e1; border-radius: 5px; background: white; cursor: pointer; }
.transfer-evidence button:disabled { opacity: .5; cursor: default; }
.evidence-note { margin: 4px 0; color: #64748b; }
.evidence-files,.evidence-candidates { list-style: none; margin: 8px 0; padding: 0; }
.evidence-files li,.evidence-candidates li,.evidence-toolbar { display: flex; align-items: center; gap: 6px; margin: 6px 0; flex-wrap: wrap; }
.evidence-files li>span,.evidence-candidates li>span { flex: 1; min-width: 90px; }
.evidence-files small,.evidence-error { display: block; color: #b42318; }
.evidence-thumbnail { width: 42px; height: 42px; object-fit: cover; border-radius: 4px; }
.evidence-picker { padding: 8px; border: 1px solid #cbd5e1; margin-top: 8px; border-radius: 6px; }
.evidence-picker select,.evidence-toolbar input { max-width: 100%; min-width: 0; padding: 6px; border: 1px solid #cbd5e1; }
.evidence-hidden { display: none; }
.evidence-preview { margin-top: 8px; }
.evidence-preview img,.evidence-preview iframe { display: block; width: 100%; max-height: 500px; object-fit: contain; border: 0; }
.evidence-preview iframe { height: 400px; }
</style>
