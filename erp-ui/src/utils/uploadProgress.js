import uploadAjax from 'element-ui/packages/upload/src/ajax'
import { getSelectedDeptId } from '@/utils/shopContext'

export function uploadProgressData() {
  return { uploadTasks: {}, uploadGuardField: null, uploadGuardForm: null,
    uploadProgressDestroyed: false, uploadProgressResetting: false, uploadRetrySource: null }
}

export const uploadProgressMethods = {
  uploadContext() {
    return JSON.stringify([this.$route && this.$route.fullPath,
      this.$store && this.$store.getters && this.$store.getters.id, getSelectedDeptId(), this.action, this.contextKey])
  },
  uploadTaskCurrent(task) {
    return task && !this.uploadProgressDestroyed && !this.uploadProgressResetting
      && this.uploadTasks[task.uid] === task && task.context === this.uploadContext()
      && task.formModel === (this.elForm && this.elForm.model)
  },
  beginUploadTask(file) {
    const raw = file.raw || file, uid = String(raw.uid)
    const upload = this.uploadWidget()
    const widgetFile = upload && (upload.uploadFiles || []).find(item => String(item.uid) === uid)
    const task = { uid, raw, widgetFile, resultUrl: '', name: raw.name, context: this.uploadContext(), formModel: this.elForm && this.elForm.model,
      status: 'uploading', progress: 0, message: '' }
    this.$set(this.uploadTasks, uid, task)
    if (this.uploadRetrySource) this.uploadRetrySource.status = 'retried'
    this.uploadRetrySource = null
    this.emitUploadState()
    return task
  },
  finishUploadTask(file, status, message, resultUrl) {
    const task = file && this.uploadTasks[String(file.uid)]
    if (!this.uploadTaskCurrent(task) || task.status !== 'uploading') return
    task.status = status
    task.message = message || ''
    if (status === 'succeeded') { task.progress = 100; task.resultUrl = resultUrl || '' }
    this.emitUploadState()
  },
  handleUploadProgress(event, file) {
    const task = file && this.uploadTasks[String(file.uid)]
    if (!this.uploadTaskCurrent(task) || task.status !== 'uploading') return
    const percent = Number(event && event.percent)
    if (Number.isFinite(percent)) task.progress = Math.max(task.progress, Math.max(0, Math.min(99, Math.floor(percent))))
  },
  uploadHttpRequest(options) {
    const task = options.file && this.uploadTasks[String(options.file.uid)]
    const current = () => this.uploadTaskCurrent(task) && task.status === 'uploading'
    if (!current()) return { abort() {} }
    // Guard before Element's callbacks, which assume the file still exists in its list.
    return uploadAjax({ ...options,
      onProgress: event => { if (current()) options.onProgress(event) },
      onSuccess: response => { if (current()) options.onSuccess(response) },
      onError: error => { if (current()) options.onError(error) }
    })
  },
  uploadWidgetFileList() {
    const files = this.fileList.slice()
    Object.values(this.uploadTasks).forEach(task => {
      if (!this.uploadTaskCurrent(task) || !['uploading', 'succeeded'].includes(task.status) || !task.widgetFile) return
      if (!files.some(file => String(file.uid) === task.uid || (task.resultUrl && file.url === task.resultUrl))) files.push(task.widgetFile)
    })
    return files
  },
  commitUploadedTasks() {
    Object.values(this.uploadTasks).forEach(task => {
      if (task.status === 'succeeded' && this.fileList.some(file => file.url === task.resultUrl)) task.status = 'committed'
    })
  },
  uploadWidget() { return this.$refs.fileUpload || this.$refs.imageUpload },
  canRemoveWidgetFile(file) {
    const upload = this.uploadWidget()
    return Boolean(file && upload && Array.isArray(upload.uploadFiles)
      && upload.uploadFiles.some(item => item === file || String(item.uid) === String(file.uid)))
  },
  removeWidgetUpload(uid) {
    const upload = this.uploadWidget()
    if (upload && Array.isArray(upload.uploadFiles)) {
      upload.uploadFiles = upload.uploadFiles.filter(file => String(file.uid) !== String(uid))
    }
  },
  cancelQueuedUpload(task) {
    if (!task || this.uploadTasks[task.uid] !== task) return
    if (task.status === 'uploading') {
      task.status = 'canceled'
      this.retireUploadOperation(task.raw)
      const upload = this.uploadWidget()
      if (upload && upload.abort) upload.abort(task.raw)
      this.removeWidgetUpload(task.uid)
    }
    this.emitUploadState()
  },
  dismissQueuedUpload(task) {
    this.cancelQueuedUpload(task)
    if (task && this.uploadTasks[task.uid] === task) this.$delete(this.uploadTasks, task.uid)
    this.emitUploadState()
  },
  retryQueuedUpload(task) {
    if (this.disabled || !this.uploadTaskCurrent(task) || !['failed', 'canceled'].includes(task.status)) return
    const upload = this.uploadWidget()
    const inner = upload && upload.$refs && upload.$refs['upload-inner']
    if (!upload || !upload.handleStart || !inner || !inner.upload) return
    const pending = Object.values(this.uploadTasks).filter(item => item.status === 'uploading').length
    if (Math.max(this.fileList.length + pending, (upload.uploadFiles || []).length) >= this.limit) { this.handleExceed(); return }
    try {
      const raw = new File([task.raw], task.name, { type: task.raw.type, lastModified: task.raw.lastModified || Date.now() })
      this.uploadRetrySource = task
      upload.handleStart(raw)
      inner.upload(raw)
    } catch (error) { task.message = '重试未开始，请重新选择文件' }
    finally { this.uploadRetrySource = null }
  },
  uploadHasUnfinished() {
    return Object.values(this.uploadTasks).some(task => ['uploading', 'failed'].includes(task.status))
  },
  emitUploadState() {
    this.$emit('upload-state', { id: this._uid, blocking: this.uploadHasUnfinished() })
  },
  registerUploadFormGuard() {
    if (typeof window !== 'undefined' && window.addEventListener) window.addEventListener('erp:dept-changed', this.resetUploadProgressScope)
    if (!this.elForm || this.uploadGuardField) return
    const prop = '__upload_' + this._uid
    const field = { prop,
      validate: (trigger, callback) => {
        const message = this.uploadHasUnfinished() ? '附件尚未上传完成，请重试或移除失败文件后再提交' : ''
        if (message) this.$modal.msgError(message)
        callback(message, message ? { [prop]: [{ message, field: prop }] } : {})
      },
      resetField: () => this.resetUploadProgressScope(), clearValidate() {},
      removeValidateEvents() {}, addValidateEvents() {}
    }
    this.uploadGuardForm = this.elForm
    this.uploadGuardField = field
    this.elForm.$emit('el.form.addField', field)
  },
  resetUploadProgressScope() {
    if (this.uploadProgressResetting) return
    this.uploadProgressResetting = true
    Object.values(this.uploadTasks).forEach(task => this.cancelQueuedUpload(task))
    this.uploadTasks = {}
    this.uploadRetrySource = null
    this.uploadProgressResetting = false
    this.emitUploadState()
  },
  disposeUploadProgress() {
    if (typeof window !== 'undefined' && window.removeEventListener) window.removeEventListener('erp:dept-changed', this.resetUploadProgressScope)
    this.resetUploadProgressScope()
    this.uploadProgressDestroyed = true
    if (this.uploadGuardForm && this.uploadGuardField) this.uploadGuardForm.$emit('el.form.removeField', this.uploadGuardField)
    this.uploadGuardField = null
    this.uploadGuardForm = null
  }
}
