<template>
  <div class="drive-attachment-picker">
    <div v-if="value" role="status">已选：{{ selectedName || ('文件 ' + value) }} <el-button type="text" :disabled="disabled || uploading" @click="clear">取消绑定</el-button></div>
    <div>
      <el-button size="small" :disabled="disabled || uploading" @click="openPicker">选择云盘文件</el-button>
      <el-button size="small" :disabled="disabled || !spaceId || uploading || hasPending" @click="$refs.file.click()">上传照片 / PDF</el-button>
      <el-button v-if="uploading" size="small" @click="stopWaiting">停止等待</el-button>
      <input ref="file" type="file" accept="image/*,.pdf,application/pdf" hidden @change="chooseUpload" />
    </div>
    <p class="attachment-tip">文件保存在我的云盘，选好后随健康证草稿一起保存。上传只接受图片或 PDF。</p>
    <el-alert v-if="error" :title="error" type="warning" :closable="false"><el-button v-if="!spaceId" type="text" @click="loadSpaces">重新读取云盘</el-button></el-alert>
    <div v-for="item in receipts" :key="item.operationId" class="attachment-receipt" role="status">
      <span>{{ item.name }}：{{ receiptLabel(item) }}</span>
      <el-button v-if="item.status === 'pending'" type="text" :loading="item.querying" :disabled="disabled" @click="checkUpload(item)">核对上传结果</el-button>
      <el-button v-if="item.status === 'done'" type="text" :disabled="disabled || uploading" @click="selectReceipt(item)">使用此文件</el-button>
    </div>
    <el-dialog title="选择我的云盘文件" :visible.sync="pickerOpen" width="min(780px, 92vw)" append-to-body :close-on-click-modal="false">
      <el-input v-model="keyword" clearable placeholder="搜索照片或 PDF 文件名" @keyup.enter.native="search"><el-button slot="append" icon="el-icon-search" @click="search">搜索</el-button></el-input>
      <p><el-button type="text" :disabled="!trail.length" @click="backFolder">返回上级</el-button> {{ trail.length ? trail[trail.length - 1].name : '我的云盘' }}</p>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false"><el-button type="text" @click="loadFiles">重试</el-button></el-alert>
      <el-table v-loading="loading" :data="rows" max-height="360" empty-text="没有匹配的文件，可以更换关键词或直接上传">
        <el-table-column label="文件名" prop="nodeName" min-width="240" />
        <el-table-column label="操作" width="110"><template slot-scope="s">
          <el-button v-if="s.row.nodeType === 'FOLDER'" type="text" @click="enterFolder(s.row)">打开目录</el-button>
          <el-button v-else type="text" :disabled="disabled || !supported(s.row)" @click="selectNode(s.row)">选择文件</el-button>
        </template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" :page.sync="page" :limit.sync="pageSize" @pagination="loadFiles" />
      <span slot="footer"><el-button @click="pickerOpen = false">关闭</el-button></span>
    </el-dialog>
  </div>
</template>
<script>
import { listDriveSpaces, listDriveNodes, getDriveNode, uploadDriveFile, getDriveUploadReceipt } from '@/api/drive'
import { getSelectedDeptId } from '@/utils/shopContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { newUploadOperationId, uploadActor, uploadContext, pendingUpload, applyPreClaimRejection, applyUploadReceipt, persistUploadReceipts, restoreUploadReceipts } = require('../uploadReceipt')
export default {
  name: 'DriveAttachmentPicker',
  props: { value: { type: [String, Number], default: null }, disabled: Boolean },
  data: () => ({ spaceId: '', selectedName: '', error: '', pickerOpen: false, keyword: '', trail: [], page: 1, pageSize: 10, rows: [], total: 0, loading: false, listError: '', receipts: [], uploading: false, controller: null }),
  computed: {
    identity() { return uploadContext(this) },
    hasPending() { return this.receipts.some(item => ['pending', 'uploading'].includes(item.status)) }
  },
  watch: {
    identity() { this.resetContext() },
    value() { this.scope().invalidate('bind-upload'); this.loadSelected() },
    disabled(value) { if (value) this.scope().invalidate('bind-upload') },
    pickerOpen(value) { if (!value) this.scope().invalidate('files') }
  },
  created() {
    this.receipts = restoreUploadReceipts(uploadActor(this), 'health-attachment').map(item => ({ ...item, querying: false }))
    if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.resetContext)
    this.loadSpaces(); this.loadSelected()
  },
  beforeDestroy() {
    this.scope().deactivate(); if (this.controller) this.controller.abort()
    if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this.resetContext)
  },
  methods: {
    scope() { if (!this._attachmentScope) this._attachmentScope = createUiOperationScope(() => ({ identity: this.identity, dept: getSelectedDeptId() })); return this._attachmentScope },
    resetContext() {
      this.scope().invalidate(); if (this.controller) this.controller.abort()
      this.controller = null; this.uploading = false; this.$emit('busy', false); this.pickerOpen = false; this.selectedName = ''; this.spaceId = ''; this.rows = []; this.trail = []; this.keyword = ''
      this.receipts = restoreUploadReceipts(uploadActor(this), 'health-attachment').map(item => ({ ...item, querying: false })); this.loadSpaces(); this.loadSelected()
    },
    supported(row) { const type = String(row.contentType || '').toLowerCase(); return row.nodeType !== 'FOLDER' && (type === 'application/pdf' || (type.startsWith('image/') && type !== 'image/svg+xml')) },
    loadSpaces() {
      const scope = this.scope(), token = scope.begin('spaces'); this.error = ''
      return listDriveSpaces().then(res => {
        if (!scope.isCurrent(token)) return
        const spaces = res.data || [], personal = spaces.find(space => space.spaceType === 'PERSONAL' && String(space.ownerUserId) === uploadActor(this))
        if (!personal) throw Error('我的云盘暂时不可用，请稍后重试')
        this.spaceId = String(personal.spaceId)
      }).catch(error => { if (scope.isCurrent(token)) this.error = error.message || '我的云盘读取失败' })
    },
    loadSelected() {
      const scope = this.scope(), id = this.value, token = scope.begin('selected', id); this.selectedName = ''
      if (!id) return Promise.resolve()
      return getDriveNode(id).then(res => { if (scope.isCurrent(token, this.value)) this.selectedName = res.data && res.data.nodeName || '' })
        .catch(() => { if (scope.isCurrent(token, this.value)) this.error = '已绑定文件暂时无法读取，请核对文件权限或重新选择' })
    },
    openPicker() { if (this.disabled || this.uploading) return; this.pickerOpen = true; this.page = 1; return this.loadFiles() },
    search() { this.page = 1; return this.loadFiles() },
    enterFolder(row) { this.trail.push({ id: row.nodeId, name: row.nodeName }); this.keyword = ''; this.page = 1; return this.loadFiles() },
    backFolder() { this.trail.pop(); this.keyword = ''; this.page = 1; return this.loadFiles() },
    loadFiles() {
      const scope = this.scope(), query = { spaceId: this.spaceId, parentId: this.trail.length ? this.trail[this.trail.length - 1].id : 0, keyword: this.keyword.trim() || undefined, pageNum: this.page, pageSize: this.pageSize, sortField: 'updated', sortDirection: 'desc' }, token = scope.begin('files')
      this.rows = []; this.loading = true; this.listError = ''
      if (!this.spaceId) { this.loading = false; this.listError = '请先等待我的云盘读取完成'; return Promise.resolve() }
      return listDriveNodes(query).then(res => {
        if (!scope.isCurrent(token) || !this.pickerOpen) return
        if (!res || !Array.isArray(res.rows)) throw Error('文件列表响应不完整')
        this.rows = res.rows; this.total = Number(res.total || 0)
      }).catch(error => { if (scope.isCurrent(token) && this.pickerOpen) this.listError = error.message || '文件列表读取失败' })
        .finally(() => { if (scope.isCurrent(token)) this.loading = false })
    },
    selectNode(row) { if (this.disabled || this.uploading || !this.supported(row)) return; this.scope().invalidate('bind-upload'); this.selectedName = row.nodeName; this.error = ''; this.$emit('input', String(row.nodeId)); this.pickerOpen = false },
    clear() { if (!this.disabled && !this.uploading) { this.scope().invalidate('bind-upload'); this.selectedName = ''; this.$emit('input', undefined) } },
    receiptLabel(item) { return ({ done: '上传完成', pending: '结果待核对', uploading: '正在上传', failed: '未成功，可重新选择文件', rejected: '未受理，可重新选择文件' })[item.status] || item.status },
    persist() { return persistUploadReceipts(uploadActor(this), 'health-attachment', this.receipts) },
    async chooseUpload(event) {
      const file = event.target.files && event.target.files[0]; event.target.value = ''
      if (!file || this.disabled || this.uploading || this.hasPending || !this.spaceId) return
      const type = String(file.type || '').toLowerCase()
      if (!(type === 'application/pdf' || (type.startsWith('image/') && type !== 'image/svg+xml')) || /\.svg$/i.test(file.name)) { this.error = '请选择图片或 PDF 文件'; return }
      const scope = this.scope(), token = scope.begin('upload'); this.error = ''
      let item
      try {
        const operationId = newUploadOperationId()
        item = { id: operationId, operationId, name: file.name, size: file.size, targetSpaceId: this.spaceId, targetParentId: '0', status: 'pending', querying: false }
        this.receipts.push(item)
        if (!this.persist()) { this.receipts.pop(); throw Error('浏览器无法保留上传回执，请检查存储后重试') }
        item.status = 'uploading'; this.uploading = true; this.$emit('busy', true); this.controller = new AbortController()
        const res = await uploadDriveFile(file, item.targetSpaceId, 0, undefined, this.controller.signal, item.operationId)
        if (!scope.isCurrent(token)) return
        Object.assign(item, applyUploadReceipt(item, res && res.data)); this.persist()
        if (item.status === 'done') await this.selectReceipt(item)
      } catch (error) {
        if (!scope.isCurrent(token)) return
        if (item && this.receipts.includes(item)) { Object.assign(item, applyPreClaimRejection(item, error, true) || pendingUpload(item)); this.persist() }
        else this.error = error.message || '上传未能开始'
      } finally { if (scope.isCurrent(token)) { this.controller = null; this.uploading = false; this.$emit('busy', false) } }
    },
    stopWaiting() {
      this.scope().invalidate('upload'); if (this.controller) this.controller.abort()
      this.receipts.forEach(item => { if (item.status === 'uploading') Object.assign(item, pendingUpload(item)) }); this.persist()
      this.controller = null; this.uploading = false; this.$emit('busy', false)
    },
    checkUpload(item) {
      if (this.disabled || item.querying || item.status !== 'pending') return Promise.resolve()
      const scope = this.scope(), token = scope.begin('receipt:' + item.operationId); item.querying = true
      return getDriveUploadReceipt(item.operationId).then(res => { if (scope.isCurrent(token)) { Object.assign(item, applyUploadReceipt(item, res && res.data)); this.persist() } })
        .catch(() => { if (scope.isCurrent(token)) this.error = '上传结果暂时无法核对，请稍后重试' })
        .finally(() => { if (scope.isCurrent(token)) item.querying = false })
    },
    selectReceipt(item) {
      if (this.disabled || item.status !== 'done') return Promise.resolve()
      const scope = this.scope(), token = scope.begin('bind-upload')
      return getDriveNode(item.nodeId).then(res => {
        if (!scope.isCurrent(token) || this.disabled) return
        const row = res.data
        if (!row || String(row.nodeId) !== String(item.nodeId) || !this.supported(row)) throw Error('文件不可用，请核对类型和权限')
        this.selectedName = row.nodeName; this.$emit('input', String(row.nodeId)); this.error = ''
      }).catch(error => { if (scope.isCurrent(token)) this.error = error.message || '文件绑定失败，请重试' })
    }
  }
}
</script>
<style scoped>
.attachment-tip { font-size: 12px; line-height: 1.6; color: #606266; margin: 6px 0; }
.attachment-receipt { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; line-height: 1.6; }
.attachment-receipt span { overflow-wrap: anywhere; }
</style>
