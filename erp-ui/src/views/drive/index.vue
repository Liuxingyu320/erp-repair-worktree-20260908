<template>
  <div class="app-container cloud-drive-page">
    <header class="cloud-drive-page__header">
      <div class="cloud-drive-page__intro">
        <span class="cloud-drive-page__brandmark" aria-hidden="true">
          <i class="el-icon-folder-opened" />
        </span>
        <div>
          <span class="cloud-drive-page__eyebrow">云端工作空间</span>
          <h1>企业云盘</h1>
          <p>安全保存、协作与查找企业文件</p>
        </div>
      </div>
      <div class="cloud-drive-page__header-tools">
        <div v-if="activeView === 'recent'" class="cloud-drive-page__space-summary cloud-drive-page__space-summary--recent">
          <span class="cloud-drive-page__summary-icon" aria-hidden="true"><i class="el-icon-time" /></span>
          <span class="cloud-drive-page__summary-copy">
            <small>全部可访问空间</small>
            <strong>最近使用</strong>
            <em>快速找回近期处理的文件</em>
          </span>
        </div>
        <div v-else-if="activeSpace" class="cloud-drive-page__space-summary">
          <div class="cloud-drive-page__summary-heading">
            <span>当前空间</span>
            <strong>{{ activeSpace.spaceName }}</strong>
          </div>
          <el-tooltip
            :content="`已使用 ${Number(activeSpace.usedBytes || 0)} 字节 / 额度 ${Number(activeSpace.quotaBytes || 0)} 字节`"
            placement="bottom"
          >
            <div class="cloud-drive-page__usage">
              <span>{{ formatBytes(activeSpace.usedBytes) }} / {{ formatBytes(activeSpace.quotaBytes) }}</span>
              <em>{{ activeSpaceUsagePercent }}%</em>
            </div>
          </el-tooltip>
          <span class="cloud-drive-page__usage-track" aria-hidden="true">
            <span :style="{ width: activeSpaceUsageBarWidth + '%' }" />
          </span>
          <div class="cloud-drive-page__summary-footer">
            <small v-if="activeSpace.quotaSourceLabel">额度来源：{{ activeSpace.quotaSourceLabel }}</small>
            <el-button
              v-if="activeSpace.canManageQuota"
              type="text"
              icon="el-icon-setting"
              @click="editQuota"
            >调整额度</el-button>
          </div>
        </div>
        <el-button
          v-if="canOpenQuotaCenter"
          class="cloud-drive-page__settings"
          type="primary"
          plain
          icon="el-icon-setting"
          @click="quotaCenterVisible = true"
        >云盘设置</el-button>
      </div>
    </header>

    <section class="cloud-drive-shell">
      <DriveSpaceSidebar
        :spaces="spaces"
        :active-space-id="activeSpaceId"
        :active-view="activeView"
        :loading="loading"
        @select-space="handleSpaceChange"
        @select-view="handleViewChange"
      />

      <main class="cloud-drive-content">
        <el-alert
          v-if="activeSpace && activeSpace.overQuota"
          class="cloud-drive-content__quota-alert"
          type="error"
          show-icon
          :closable="false"
          :title="`当前空间已超额，需要清理 ${formatBytes(activeSpace.overQuotaBytes)} 后才能新增文件`"
        />
        <el-alert
          v-if="activeSpace && activeSpace.writeBlockedMessage"
          class="cloud-drive-content__quota-alert"
          type="warning"
          show-icon
          :closable="false"
          :title="`${activeSpace.writeBlockedMessage}。现有文件仍可下载，有权限的人员可继续清理空间。`"
        />
        <DriveToolbar
          ref="driveToolbar"
          :breadcrumbs="breadcrumbs"
          :keyword="keyword"
          :sort-field="sortField"
          :sort-direction="sortDirection"
          :can-write="Boolean(activeSpace && activeSpace.canWrite)"
          :active-view="activeView"
          @navigate="navigateToBreadcrumb"
          @search="handleSearch"
          @sort-change="handleSortChange"
          @create-folder="handleCreateFolder"
          @select-files="handleSelectFiles"
        />

        <div v-if="loadError" class="cloud-drive-content__alert">
          <el-alert
            role="alert"
            :title="loadError"
            type="error"
            show-icon
            :closable="false"
          />
          <el-button size="mini" icon="el-icon-refresh" @click="retryLoad">重新加载</el-button>
        </div>

        <section class="cloud-drive-content__body" aria-label="文件列表">
          <DriveTrashView
            v-if="activeView === 'trash'"
            :items="trashItems"
            :loading="loading"
            :can-write="Boolean(activeSpace && activeSpace.canWrite)"
            :can-cleanup="canCleanupCurrentSpace"
            @restore="restoreTrashNode"
            @purge="purgeTrashNode"
            @retry-purge="retryPurgeTrashNode"
            @empty="emptyTrash"
          />

          <DriveNodeList
            v-else-if="nodes.length || loading"
            :nodes="nodes"
            :loading="loading"
            :keyword="keyword"
            :mode="activeView"
            @open-folder="handleFolderOpen"
            @preview="handlePreview"
            @download="handleDownload"
            @open-location="openNodeLocation"
            @rename="renameNode"
            @move="openMove"
            @trash="trashNode"
          />

          <div v-else-if="activeView !== 'trash'" class="cloud-drive-empty" role="status">
            <span class="cloud-drive-empty__icon" aria-hidden="true"><i class="el-icon-folder-opened" /></span>
            <strong>{{ emptyStateMessage }}</strong>
            <span v-if="keyword">换个关键词，或清空搜索返回当前目录</span>
            <span v-else-if="activeView === 'recent'">最近打开或下载的文件会出现在这里</span>
            <div
              v-else-if="!loadError && activeView === 'files' && activeSpace && activeSpace.canWrite"
              class="cloud-drive-empty__writable"
            >
              <span>从新建文件夹或上传第一个文件开始</span>
              <div class="cloud-drive-empty__actions">
                <el-button icon="el-icon-folder-add" @click="handleCreateFolder">新建文件夹</el-button>
                <el-button type="primary" icon="el-icon-upload2" @click="openFilePickerFromEmptyState">上传文件</el-button>
              </div>
            </div>
            <span v-else-if="activeView === 'files' && !loadError">当前空间暂无可查看的文件</span>
          </div>
        </section>

        <el-pagination
          v-if="activeView === 'files' && total > pageSize"
          class="cloud-drive-pagination"
          background
          layout="prev, pager, next, total"
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          aria-label="文件列表分页"
          @current-change="handlePageChange"
        />
      </main>
    </section>

    <el-alert v-if="uploadReceiptStorageUnavailable" type="warning" :closable="false" show-icon
      title="浏览器未能保存上传进度，请保留此页面，核对上传结果后再关闭。" />
    <DriveUploadQueue
      :items="uploadItems"
      @cancel="cancelUpload"
      @retry="retryUpload"
      @query="queryUpload"
      @remove="removeUpload"
    />

    <DrivePreviewDialog
      :visible="Boolean(previewNode)"
      :node="previewNode"
      :loading="previewLoading"
      :error="previewError"
      :object-url="previewObjectUrl"
      :text-content="previewText"
      @close="closePreview"
      @download="handleDownload"
    />

    <DriveMoveDialog
      :visible="Boolean(moveNode)"
      :node="moveNode"
      :space-id="moveNode ? moveNode.spaceId : null"
      @close="closeMove"
      @confirm="confirmMove"
    />

    <DriveQuotaCenterDrawer
      :visible.sync="quotaCenterVisible"
      @changed="reloadSpacesAndCurrentView"
    />
  </div>
</template>

<script>
import {
  createDriveFolder,
  emptyDriveTrash,
  getDriveContent,
  getDriveNode,
  listDriveNodes,
  listDriveSpaces,
  listDriveTrash,
  listRecentDriveNodes,
  moveDriveNode,
  purgeDriveNode,
  renameDriveNode,
  restoreDriveNode,
  trashDriveNode,
  updateDriveQuota,
  uploadDriveFile,
  getDriveUploadReceipt
} from '@/api/drive'
import { saveAs } from 'file-saver'
import DriveSpaceSidebar from './components/DriveSpaceSidebar.vue'
import DriveToolbar from './components/DriveToolbar.vue'
import DriveNodeList from './components/DriveNodeList.vue'
import DriveUploadQueue from './components/DriveUploadQueue.vue'
import DrivePreviewDialog from './components/DrivePreviewDialog.vue'
import DriveMoveDialog from './components/DriveMoveDialog.vue'
import DriveTrashView from './components/DriveTrashView.vue'
import DriveQuotaCenterDrawer from './components/quota/DriveQuotaCenterDrawer.vue'

const {
  clearDriveTimer,
  createUploadItem,
  driveErrorMessage,
  formatBytes,
  isDriveLoadCurrent,
  isPreviewable,
  markUploadCanceled,
  normalizeRouteState,
  parseDriveBlobError,
  preserveRecentOrder,
  releaseDriveObjectUrl,
  selectUploadStartCandidates,
  sortNodes,
  updateUploadProgress
} = require('./driveState')

const { uploadActor, uploadContext, pendingUpload, applyPreClaimRejection, applyUploadReceipt, persistUploadReceipts, restoreUploadReceipts, matchesUploadFile } = require('./uploadReceipt')

export default {
  name: 'CloudDrive',
  components: {
    DriveSpaceSidebar,
    DriveToolbar,
    DriveNodeList,
    DriveUploadQueue,
    DrivePreviewDialog,
    DriveMoveDialog,
    DriveTrashView,
    DriveQuotaCenterDrawer
  },
  data() {
    return {
      spaces: [],
      activeSpaceId: null,
      activeView: 'files',
      parentId: 0,
      breadcrumbs: [],
      nodes: [],
      trashItems: [],
      total: 0,
      pageNum: 1,
      pageSize: 50,
      loading: false,
      loadError: '',
      keyword: '',
      sortField: 'updated',
      sortDirection: 'desc',
      uploadItems: [],
      uploadReceiptOwner: '',
      uploadReceiptStorageUnavailable: false,
      uploadDisposed: false,
      previewNode: null,
      moveNode: null,
      previewLoading: false,
      previewError: '',
      previewObjectUrl: '',
      previewText: '',
      previewSequence: 0,
      searchTimer: null,
      loadSequence: 0,
      spacesSequence: 0,
      applyingRouteState: false,
      routeApplySequence: 0,
      trashPollTimer: null,
      trashPollStartedAt: 0,
      quotaCenterVisible: false
    }
  },
  computed: {
    uploadIdentity() { return uploadContext(this) },
    activeSpace() {
      return this.spaces.find(space => Number(space.spaceId) === Number(this.activeSpaceId)) || null
    },
    canOpenQuotaCenter() {
      return this.spaces.some(space => space.canOpenQuotaCenter || space.canManageQuota)
    },
    activeSpaceUsagePercent() {
      if (!this.activeSpace) return 0
      const quota = Number(this.activeSpace.quotaBytes || 0)
      const used = Number(this.activeSpace.usedBytes || 0)
      if (quota <= 0 || used <= 0) return 0
      return Math.min(999, Math.max(0, Math.round(used / quota * 100)))
    },
    activeSpaceUsageBarWidth() {
      return Math.min(100, this.activeSpaceUsagePercent)
    },
    canCleanupCurrentSpace() {
      if (!this.activeSpace) return false
      return this.activeSpace.canCleanup == null
        ? Boolean(this.activeSpace.canWrite)
        : Boolean(this.activeSpace.canCleanup)
    },
    currentLogicalPath() {
      const parts = [this.activeSpace && this.activeSpace.spaceName]
        .concat(this.breadcrumbs.map(item => item.nodeName))
        .filter(Boolean)
      return parts.join('/') || '根目录'
    },
    emptyStateMessage() {
      if (this.loadError) return '文件服务暂不可用，请稍后重试'
      if (this.keyword) return '没有找到匹配文件'
      if (this.activeView === 'recent') return '暂无最近使用的文件'
      if (this.activeView === 'trash') return '回收站为空'
      return '当前文件夹暂无文件'
    }
  },
  watch: {
    uploadIdentity() {
      this.loadSequence += 1
      this.spacesSequence += 1
      this.spaces = []
      this.nodes = []
      this.trashItems = []
      this.breadcrumbs = []
      this.total = 0
      this.uploadItems.forEach(item => { if (item.controller) item.controller.abort() })
      this.uploadReceiptOwner = uploadActor(this)
      this.uploadReceiptStorageUnavailable = false
      this.uploadItems = restoreUploadReceipts(this.uploadReceiptOwner, 'pc')
    }
  },
  created() {
    this.uploadReceiptOwner = uploadActor(this)
    this.uploadItems = restoreUploadReceipts(this.uploadReceiptOwner, 'pc')
    this.initialize()
  },
  beforeDestroy() {
    this.searchTimer = clearDriveTimer(this.searchTimer, clearTimeout)
    this.cancelTrashPolling()
    this.uploadDisposed = true
    this.loadSequence += 1
    this.spacesSequence += 1
    this.cancelAllUploads()
    this.previewSequence += 1
    this.revokePreviewUrl()
  },
  beforeRouteUpdate(to, from, next) {
    const routeState = normalizeRouteState(to && to.query)
    if (this.isRouteStateCurrent(routeState)) {
      next()
      return
    }
    next()
    this.applyRouteState(routeState)
  },
  methods: {
    formatBytes,
    async initialize() {
      const routeState = normalizeRouteState(this.$route && this.$route.query)
      this.activeView = routeState.view
      this.parentId = routeState.parentId
      this.keyword = routeState.keyword
      await this.loadSpaces(routeState.spaceId)
      if (this.activeView === 'trash' && !this.canCleanupCurrentSpace) {
        this.activeView = 'files'
      }
      if (this.activeSpaceId) await this.loadCurrentView()
    },
    isRouteStateCurrent(routeState) {
      return Number(routeState && routeState.spaceId) === Number(this.activeSpaceId) &&
        Number(routeState && routeState.parentId) === Number(this.parentId) &&
        String((routeState && routeState.view) || 'files') === String(this.activeView) &&
        String((routeState && routeState.keyword) || '') === String(this.keyword)
    },
    async applyRouteState(routeState) {
      const applySequence = ++this.routeApplySequence
      this.applyingRouteState = true
      this.cancelTrashPolling()
      this.resetVisibleContent()
      this.activeView = routeState.view
      this.parentId = routeState.parentId
      this.keyword = routeState.keyword
      this.pageNum = 1
      try {
        if (!this.spaces.length) {
          await this.loadSpaces(routeState.spaceId)
        } else {
          const selected = this.spaces.find(space => Number(space.spaceId) === Number(routeState.spaceId)) ||
            this.spaces[0]
          this.activeSpaceId = selected ? selected.spaceId : null
        }
        if (applySequence !== this.routeApplySequence) return
        if (this.activeView === 'trash' && !this.canCleanupCurrentSpace) {
          this.activeView = 'files'
        }
        if (this.activeSpaceId != null) await this.loadCurrentView()
      } finally {
        if (applySequence === this.routeApplySequence) {
          this.applyingRouteState = false
          this.syncRoute('replace')
        }
      }
    },
    async loadSpaces(preferredSpaceId) {
      const sequence = ++this.spacesSequence
      const identity = uploadContext(this)
      const current = () => !this.uploadDisposed && sequence === this.spacesSequence && identity === uploadContext(this)
      try {
        const response = await listDriveSpaces()
        if (!current()) return
        const nextSpaces = response.data || []
        this.spaces = nextSpaces
        const requested = preferredSpaceId == null ? this.activeSpaceId : preferredSpaceId
        const selected = nextSpaces.find(space => Number(space.spaceId) === Number(requested)) || nextSpaces[0]
        this.activeSpaceId = selected ? selected.spaceId : null
      } catch (error) {
        if (!current()) return
        const parsed = await parseDriveBlobError(error)
        if (!current()) return
        this.loadError = driveErrorMessage(parsed.code, parsed.message || '文件服务暂不可用，请稍后重试')
      }
    },
    async loadCurrentView() {
      if (this.activeView === 'recent') return this.loadRecent()
      if (this.activeView === 'trash') return this.loadTrash()
      return this.loadNodes()
    },
    async retryLoad() {
      this.loadError = ''
      if (!this.spaces.length || this.activeSpaceId == null) await this.loadSpaces()
      if (this.activeSpaceId != null) await this.loadCurrentView()
    },
    async loadBreadcrumbs(parentId) {
      if (Number(parentId) === 0) return []
      const response = await getDriveNode(parentId)
      return response.data && response.data.breadcrumbs
        ? response.data.breadcrumbs
        : []
    },
    isCurrentLoadRequest(request) {
      return isDriveLoadCurrent(request, {
        sequence: this.loadSequence,
        spaceId: this.activeSpaceId,
        parentId: this.parentId,
        view: this.activeView,
        keyword: this.keyword,
        sortField: this.sortField,
        sortDirection: this.sortDirection,
        pageNum: this.pageNum,
        pageSize: this.pageSize
      })
    },
    async loadNodes() {
      if (!this.activeSpaceId) return
      const request = {
        sequence: ++this.loadSequence,
        spaceId: this.activeSpaceId,
        parentId: this.parentId,
        view: this.activeView,
        keyword: this.keyword,
        sortField: this.sortField,
        sortDirection: this.sortDirection,
        pageNum: this.pageNum,
        pageSize: this.pageSize
      }
      this.nodes = []
      this.total = 0
      this.loading = true
      this.loadError = ''
      try {
        const breadcrumbs = await this.loadBreadcrumbs(request.parentId)
        if (!this.isCurrentLoadRequest(request)) return
        const response = await listDriveNodes({
          spaceId: request.spaceId,
          parentId: request.parentId,
          keyword: request.keyword || undefined,
          sortField: request.sortField,
          sortDirection: request.sortDirection,
          pageNum: request.pageNum,
          pageSize: request.pageSize
        })
        if (!this.isCurrentLoadRequest(request)) return
        this.breadcrumbs = breadcrumbs
        this.nodes = response.rows || []
        this.total = Number(response.total || 0)
        this.syncRoute()
      } catch (error) {
        if (!this.isCurrentLoadRequest(request)) return
        const parsed = await parseDriveBlobError(error)
        if (!this.isCurrentLoadRequest(request)) return
        this.loadError = driveErrorMessage(parsed.code, parsed.message || '文件服务暂不可用，请稍后重试')
      } finally {
        if (this.isCurrentLoadRequest(request)) this.loading = false
      }
    },
    async loadRecent() {
      const requestSequence = ++this.loadSequence
      const previousNodes = this.nodes
      this.loading = true
      this.loadError = ''
      this.breadcrumbs = []
      this.trashItems = []
      try {
        const response = await listRecentDriveNodes(50)
        if (requestSequence !== this.loadSequence) return
        this.nodes = preserveRecentOrder(response.data)
        this.total = this.nodes.length
        this.syncRoute()
      } catch (error) {
        if (requestSequence !== this.loadSequence) return
        const parsed = await parseDriveBlobError(error)
        if (requestSequence !== this.loadSequence) return
        this.nodes = previousNodes
        this.loadError = driveErrorMessage(parsed.code, parsed.message || '文件服务暂不可用，请稍后重试')
      } finally {
        if (requestSequence === this.loadSequence) this.loading = false
      }
    },
    async loadTrash(managePolling = true) {
      if (!this.activeSpaceId) return
      const requestSequence = ++this.loadSequence
      const previousItems = this.trashItems
      if (managePolling) this.loading = true
      this.loadError = ''
      this.nodes = []
      this.breadcrumbs = []
      try {
        const response = await listDriveTrash({ spaceId: this.activeSpaceId })
        if (requestSequence !== this.loadSequence) return
        this.trashItems = sortNodes(response.data || [], 'updated', 'desc')
        this.total = this.trashItems.length
        this.syncRoute()
        if (managePolling) {
          if (this.trashItems.some(item => item.status === 'PURGING')) this.startTrashPolling()
          else this.cancelTrashPolling()
        }
      } catch (error) {
        if (requestSequence !== this.loadSequence) return
        const parsed = await parseDriveBlobError(error)
        if (requestSequence !== this.loadSequence) return
        this.trashItems = previousItems
        this.loadError = driveErrorMessage(parsed.code, parsed.message || '文件服务暂不可用，请稍后重试')
      } finally {
        if (managePolling && requestSequence === this.loadSequence) this.loading = false
      }
    },
    handleSpaceChange(spaceId) {
      if (Number(spaceId) === Number(this.activeSpaceId) && this.activeView === 'files') return
      this.cancelTrashPolling()
      this.resetVisibleContent()
      this.activeSpaceId = spaceId
      this.activeView = 'files'
      this.parentId = 0
      this.keyword = ''
      this.pageNum = 1
      this.syncRoute('push')
      this.loadCurrentView()
    },
    handleViewChange(view) {
      if (view === this.activeView) return
      if (view === 'trash' && !this.canCleanupCurrentSpace) return
      if (this.activeView === 'trash') this.cancelTrashPolling()
      this.resetVisibleContent()
      this.activeView = view
      this.parentId = 0
      this.keyword = ''
      this.pageNum = 1
      this.syncRoute('push')
      this.loadCurrentView()
    },
    handleFolderOpen(node) {
      this.resetVisibleContent()
      if (node.spaceId) this.activeSpaceId = node.spaceId
      this.activeView = 'files'
      this.parentId = Number(node.nodeId)
      this.keyword = ''
      this.pageNum = 1
      this.syncRoute('push')
      this.loadCurrentView()
    },
    openNodeLocation(node) {
      if (!node) return
      this.resetVisibleContent()
      this.activeSpaceId = node.spaceId
      this.activeView = 'files'
      this.parentId = Number(node.parentId) || 0
      this.keyword = ''
      this.pageNum = 1
      this.syncRoute('push')
      this.loadCurrentView()
    },
    navigateToBreadcrumb(nodeId) {
      this.resetVisibleContent()
      if (this.activeView !== 'files') this.activeView = 'files'
      this.parentId = Number(nodeId) || 0
      this.keyword = ''
      this.pageNum = 1
      this.syncRoute('push')
      this.loadCurrentView()
    },
    resetVisibleContent() {
      this.clearListResults({ preserveBreadcrumbs: false })
    },
    clearListResults({ preserveBreadcrumbs = false } = {}) {
      this.loadSequence += 1
      this.nodes = []
      this.trashItems = []
      this.total = 0
      this.loadError = ''
      if (!preserveBreadcrumbs) this.breadcrumbs = []
    },
    handleSearch(value) {
      this.keyword = typeof value === 'string' ? value : ''
      this.pageNum = 1
      this.clearListResults({ preserveBreadcrumbs: true })
      this.searchTimer = clearDriveTimer(this.searchTimer, clearTimeout)
      this.searchTimer = setTimeout(() => {
        this.searchTimer = null
        this.syncRoute()
        this.loadCurrentView()
      }, 300)
    },
    handleSortChange(sort) {
      this.sortField = sort && ['name', 'size', 'updated'].includes(sort.field) ? sort.field : 'updated'
      this.sortDirection = sort && sort.direction === 'asc' ? 'asc' : 'desc'
      this.pageNum = 1
      this.clearListResults({ preserveBreadcrumbs: true })
      this.loadCurrentView()
    },
    handlePageChange(page) {
      this.pageNum = Number(page) || 1
      this.clearListResults({ preserveBreadcrumbs: true })
      this.loadCurrentView()
    },
    handlePreview(node) {
      this.openPreview(node)
    },
    handleDownload(node) {
      this.downloadNode(node)
    },
    async openPreview(node) {
      const requestSequence = ++this.previewSequence
      this.revokePreviewUrl()
      this.previewNode = node
      this.previewLoading = false
      this.previewError = ''
      this.previewText = ''
      if (!isPreviewable(node)) return

      this.previewLoading = true
      try {
        const blob = await getDriveContent(node.nodeId, 'preview')
        const extension = String(node.extension || '').toLowerCase()
        if (['txt', 'csv'].includes(extension)) {
          const text = await blob.text()
          if (requestSequence !== this.previewSequence) return
          this.previewText = text
        } else {
          if (requestSequence !== this.previewSequence) return
          this.previewObjectUrl = URL.createObjectURL(blob)
        }
      } catch (error) {
        if (requestSequence !== this.previewSequence) return
        const parsed = await parseDriveBlobError(error)
        if (requestSequence !== this.previewSequence) return
        this.previewError = driveErrorMessage(parsed.code, parsed.message)
      } finally {
        if (requestSequence === this.previewSequence) this.previewLoading = false
      }
    },
    closePreview() {
      this.previewSequence += 1
      this.revokePreviewUrl()
      this.previewNode = null
      this.previewLoading = false
      this.previewError = ''
      this.previewText = ''
    },
    revokePreviewUrl() {
      this.previewObjectUrl = releaseDriveObjectUrl(this.previewObjectUrl, URL)
    },
    async downloadNode(node) {
      if (!node) return
      try {
        const blob = await getDriveContent(node.nodeId, 'download')
        saveAs(blob, node.nodeName)
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
      }
    },
    async renameNode(node) {
      if (!node || !node.canWrite) return
      try {
        const result = await this.$prompt('请输入新名称', '重命名', {
          confirmButtonText: '保存',
          cancelButtonText: '取消',
          inputValue: node.nodeName,
          inputValidator: value => Boolean(value && String(value).trim()) || '名称不能为空'
        })
        const name = String(result.value || '').trim()
        if (!name || name === node.nodeName) return
        await renameDriveNode(node.nodeId, { name, version: node.version })
        this.$message.success('重命名成功')
        await this.loadCurrentView()
      } catch (error) {
        if (error === 'cancel' || error === 'close') return
        await this.handleMutationError(error)
      }
    },
    openMove(node) {
      if (!node || !node.canWrite) return
      this.moveNode = node
    },
    closeMove() {
      this.moveNode = null
    },
    async confirmMove(targetParentId) {
      if (!this.moveNode) return
      try {
        await moveDriveNode(this.moveNode.nodeId, {
          targetParentId,
          version: this.moveNode.version
        })
        this.closeMove()
        this.$message.success('移动成功')
        await this.loadCurrentView()
      } catch (error) {
        await this.handleMutationError(error, true)
      }
    },
    async handleMutationError(error, closeMoveDialog) {
      const parsed = await parseDriveBlobError(error)
      if (parsed.code === 'DRIVE_CONCURRENT_MODIFICATION') {
        if (closeMoveDialog) this.closeMove()
        this.$message.warning(driveErrorMessage('DRIVE_CONCURRENT_MODIFICATION', parsed.message))
        await this.loadCurrentView()
        return
      }
      this.$message.error(driveErrorMessage(parsed.code, parsed.message))
    },
    async trashNode(node) {
      if (!node || !node.canDelete) return
      try {
        await this.$confirm(
          '移入回收站后保留 30 天，期间仍占用云盘容量。是否继续？',
          '移入回收站',
          { confirmButtonText: '移入回收站', cancelButtonText: '取消', type: 'warning' }
        )
      } catch (_) {
        return
      }
      try {
        await trashDriveNode(node.nodeId, node.version)
        this.$message.success('已移入回收站')
        await this.reloadSpacesAndCurrentView()
      } catch (error) {
        await this.handleMutationError(error)
      }
    },
    async restoreTrashNode(node) {
      if (!node) return
      try {
        const response = await restoreDriveNode(node.nodeId)
        this.$message.success(response.msg || '恢复成功')
        await this.reloadSpacesAndCurrentView()
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
        await this.loadTrash()
      }
    },
    async purgeTrashNode(node, retry) {
      if (!node) return
      try {
        await this.$confirm(
          retry ? '重试清理后，已删除的内容无法恢复。是否继续？' : '彻底删除后无法撤销，是否继续？',
          retry ? '重试清理' : '彻底删除',
          { confirmButtonText: '继续清理', cancelButtonText: '取消', type: 'warning' }
        )
      } catch (_) {
        return
      }
      try {
        await purgeDriveNode(node.nodeId)
        this.$message.info('已开始清理')
        await this.reloadSpacesAndCurrentView()
        if (this.trashItems.some(item => item.status === 'PURGING')) this.startTrashPolling()
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
        await this.loadTrash()
      }
    },
    retryPurgeTrashNode(node) {
      this.purgeTrashNode(node, true)
    },
    async emptyTrash() {
      if (!this.trashItems.length) return
      try {
        await this.$confirm(
          '清空回收站后无法撤销，所有项目将进入后台清理。是否继续？',
          '清空回收站',
          { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' }
        )
      } catch (_) {
        return
      }
      try {
        await emptyDriveTrash(this.activeSpaceId)
        this.$message.info('已开始清理')
        await this.reloadSpacesAndCurrentView()
        if (this.trashItems.some(item => item.status === 'PURGING')) this.startTrashPolling()
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
        await this.loadTrash()
      }
    },
    async reloadSpacesAndCurrentView() {
      await this.loadSpaces()
      await this.loadCurrentView()
    },
    startTrashPolling() {
      if (this.activeView !== 'trash' || this.trashPollTimer != null) return
      if (!this.trashPollStartedAt) this.trashPollStartedAt = Date.now()
      const poll = async () => {
        this.trashPollTimer = null
        if (this.activeView !== 'trash') {
          this.cancelTrashPolling()
          return
        }
        if (Date.now() - this.trashPollStartedAt >= 60 * 1000) {
          this.cancelTrashPolling()
          this.$message.info('清理仍在后台继续，请稍后返回回收站查看')
          return
        }
        await this.loadTrash(false)
        await this.loadSpaces()
        if (this.activeView === 'trash' && this.trashItems.some(item => item.status === 'PURGING')) {
          this.trashPollTimer = setTimeout(poll, 2000)
        } else {
          this.cancelTrashPolling()
        }
      }
      this.trashPollTimer = setTimeout(poll, 2000)
    },
    cancelTrashPolling() {
      this.trashPollTimer = clearDriveTimer(this.trashPollTimer, clearTimeout)
      this.trashPollStartedAt = 0
    },
    async editQuota() {
      const space = this.activeSpace
      if (!space || !space.canManageQuota) return
      let result
      try {
        result = await this.$prompt('请输入新的空间额度（GB）', '调整云盘额度', {
          confirmButtonText: '保存',
          cancelButtonText: '取消',
          inputValue: (Number(space.quotaBytes || 0) / 1024 ** 3).toFixed(2),
          inputPattern: /^\d+(\.\d{1,3})?$/,
          inputErrorMessage: '请输入大于 0 的 GB 数值，最多三位小数'
        })
      } catch (_) {
        return
      }
      const gb = Number(result.value)
      const nextQuotaBytes = Math.round(gb * 1024 ** 3)
      if (!Number.isFinite(gb) || gb <= 0 || !Number.isSafeInteger(nextQuotaBytes) ||
        nextQuotaBytes < Number(space.usedBytes || 0)) {
        this.$message.error('新额度不能小于当前已使用容量')
        return
      }
      try {
        await updateDriveQuota(space.spaceId, {
          quotaBytes: nextQuotaBytes,
          version: space.version
        })
        this.$message.success('空间额度已更新')
        await this.loadSpaces(space.spaceId)
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        if (parsed.code === 'DRIVE_CONCURRENT_MODIFICATION') {
          await this.loadSpaces(space.spaceId)
          this.$message.warning('空间用量或额度已变化，请确认后重新调整')
          return
        }
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
      }
    },
    async handleCreateFolder() {
      if (!this.activeSpace || !this.activeSpace.canWrite) return
      const targetSpaceId = this.activeSpaceId
      const targetParentId = this.parentId
      try {
        const result = await this.$prompt('请输入文件夹名称', '新建文件夹', {
          confirmButtonText: '创建',
          cancelButtonText: '取消',
          inputPlaceholder: '文件夹名称',
          inputValidator: value => Boolean(value && String(value).trim()) || '文件夹名称不能为空'
        })
        const name = String(result.value || '').trim()
        if (!name) return
        await createDriveFolder({ spaceId: targetSpaceId, parentId: targetParentId, name })
        this.$message.success('文件夹已创建')
        if (this.isCurrentDestination(targetSpaceId, targetParentId)) await this.loadNodes()
      } catch (error) {
        if (error === 'cancel' || error === 'close') return
        const parsed = await parseDriveBlobError(error)
        if (parsed.code === 'DRIVE_NAME_CONFLICT') {
          this.$message.error(driveErrorMessage('DRIVE_NAME_CONFLICT', parsed.message))
          return
        }
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
      }
    },
    handleSelectFiles(files) {
      if (!this.activeSpace || !this.activeSpace.canWrite || !Array.isArray(files) || !files.length) return
      const targetSpaceId = this.activeSpaceId
      const targetParentId = this.parentId
      const targetPath = this.currentLogicalPath
      files.forEach(file => {
        const existing = this.uploadItems.find(item => ['failed', 'pending', 'uploading'].includes(item.status) &&
          matchesUploadFile(item, file, targetSpaceId, targetParentId))
        if (existing) {
          this.replaceUploadItem(existing.id, item => ({ ...item, file }))
          if (existing.status === 'failed') this.retryUpload(existing.id)
          else if (existing.status === 'pending') this.queryUpload(existing.id)
          return
        }
        this.uploadItems = this.uploadItems.concat(createUploadItem(file, targetSpaceId, targetParentId, targetPath))
      })
      this.$nextTick(() => this.pumpUploads())
    },
    openFilePickerFromEmptyState() {
      const toolbar = this.$refs.driveToolbar
      if (toolbar && typeof toolbar.openFilePicker === 'function') toolbar.openFilePicker()
    },
    pumpUploads() {
      if (this.uploadDisposed) return
      selectUploadStartCandidates(this.uploadItems, 2).forEach(item => this.startUpload(item))
    },
    async startUpload(item) {
      if (!item.file || item.status !== 'queued' || this.uploadDisposed) return
      const context = uploadContext(this)
      const firstAttempt = item.freshUpload === true
      const attemptVersion = (item.attemptVersion || 0) + 1
      const current = () => !this.uploadDisposed && context === uploadContext(this) &&
        this.uploadItems.some(value => value.id === item.id && value.attemptVersion === attemptVersion)
      const controller = new AbortController()
      this.replaceUploadItem(item.id, current => ({
        ...current,
        status: 'uploading',
        freshUpload: false,
        attemptVersion,
        error: '',
        controller
      }))
      const onUploadProgress = event => {
        if (!current()) return
        const total = Number(event && event.total) || Number(item.size) || 0
        const loaded = Number(event && event.loaded) || 0
        const progress = total > 0 ? loaded / total * 100 : 0
        this.replaceUploadItem(item.id, current => current.status === 'uploading'
          ? updateUploadProgress(current, progress)
          : current)
      }
      try {
        const response = await uploadDriveFile(item.file, item.targetSpaceId, item.targetParentId,
          onUploadProgress, controller.signal, item.operationId)
        if (!current()) return
        this.replaceUploadItem(item.id, value => applyUploadReceipt(value, response && response.data))
        const updated = this.uploadItems.find(value => value.id === item.id)
        if (updated && updated.status === 'done') {
          await this.loadSpaces()
          if (current() && this.isCurrentDestination(item.targetSpaceId, item.targetParentId)) await this.loadNodes()
        }
      } catch (error) {
        if (!current()) return
        const currentItem = this.uploadItems.find(value => value.id === item.id)
        const rejected = applyPreClaimRejection(currentItem, error, firstAttempt)
        this.replaceUploadItem(item.id, value => rejected || pendingUpload(value))
        if (!rejected) await this.queryUpload(item.id)
      } finally {
        if (current()) this.pumpUploads()
      }
    },
    async queryUpload(itemId) {
      const item = this.uploadItems.find(value => value.id === itemId)
      if (!item || !item.operationId || item.querying) return
      const context = uploadContext(this)
      const attemptVersion = item.attemptVersion || 0
      const current = () => !this.uploadDisposed && context === uploadContext(this) &&
        this.uploadItems.some(value => value.id === itemId && (value.attemptVersion || 0) === attemptVersion)
      this.replaceUploadItem(itemId, value => ({ ...value, querying: true }))
      try {
        const response = await getDriveUploadReceipt(item.operationId)
        if (!current()) return
        this.replaceUploadItem(itemId, value => applyUploadReceipt(value, response && response.data))
        const updated = this.uploadItems.find(value => value.id === itemId)
        if (updated && updated.status === 'done') {
          await this.loadSpaces()
          if (current() && this.isCurrentDestination(item.targetSpaceId, item.targetParentId)) await this.loadNodes()
        }
      } catch (_) {
        if (current()) {
          this.replaceUploadItem(itemId, value => pendingUpload(value, '暂时无法查询上传结果，请稍后重试查询'))
        }
      } finally {
        if (current()) this.replaceUploadItem(itemId, value => ({ ...value, querying: false }))
      }
    },
    replaceUploadItem(itemId, transition) {
      this.uploadItems = this.uploadItems.map(item => item.id === itemId ? transition(item) : item)
      this.persistUploadState()
    },
    persistUploadState() {
      const saved = persistUploadReceipts(this.uploadReceiptOwner || uploadActor(this), 'pc', this.uploadItems)
      this.uploadReceiptStorageUnavailable = !saved && this.uploadItems.some(item => ['uploading', 'pending'].includes(item.status))
    },
    retryUpload(itemId) {
      const item = this.uploadItems.find(candidate => candidate.id === itemId)
      if (!item || item.status !== 'failed') return
      if (!item.file) { this.$message.info('请在原目录重新选择同一个文件继续'); return }
      this.replaceUploadItem(itemId, current => ({ ...current, status: 'queued', progress: 0, error: '' }))
      this.pumpUploads()
    },
    cancelUpload(itemId) {
      const item = this.uploadItems.find(candidate => candidate.id === itemId)
      if (!item || item.status !== 'uploading') return
      if (item.controller && typeof item.controller.abort === 'function') item.controller.abort()
      this.replaceUploadItem(itemId, current => pendingUpload(current, '已停止等待，服务器上传结果仍需核对'))
      this.$nextTick(() => this.pumpUploads())
    },
    cancelAllUploads() {
      this.uploadItems.forEach(item => {
        if (item.status === 'uploading' && item.controller && typeof item.controller.abort === 'function') {
          item.controller.abort()
        }
      })
      this.uploadItems = this.uploadItems.map(item => item.status === 'uploading'
        ? pendingUpload(item) : item.status === 'queued' ? markUploadCanceled(item) : item)
      this.persistUploadState()
    },
    removeUpload(itemId) {
      const item = this.uploadItems.find(candidate => candidate.id === itemId)
      const removable = item && item.status !== 'uploading' && (
        item.status === 'queued' || item.status === 'failed' || item.status === 'rejected' || item.status === 'done' ||
        item.status === 'canceled'
      )
      if (!removable) return
      this.uploadItems = this.uploadItems.filter(candidate => candidate.id !== itemId)
      this.persistUploadState()
    },
    isCurrentDestination(spaceId, parentId) {
      return this.activeView === 'files' &&
        Number(this.activeSpaceId) === Number(spaceId) &&
        Number(this.parentId) === Number(parentId)
    },
    syncRoute(mode = 'replace') {
      if (!this.$router || !this.$route) return
      if (this.applyingRouteState) return
      const method = mode === 'push' ? 'push' : 'replace'
      this.$router[method]({
        path: this.$route.path,
        query: {
          space: this.activeSpaceId == null ? undefined : String(this.activeSpaceId),
          parent: String(this.parentId),
          view: this.activeView,
          keyword: this.keyword || undefined
        }
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.cloud-drive-page {
  --drive-accent: var(--erp-primary, #0b6b53);
  --drive-accent-dark: var(--erp-primary-hover, #075441);
  --drive-ink: var(--erp-text, #17211d);
  --drive-muted: var(--erp-text-secondary, #66736d);
  min-width: 760px;
  min-height: calc(100vh - 144px);
  position: relative;
  padding: 22px 24px 26px;
  color: var(--drive-ink);
  background: var(--erp-canvas, #f4f5f2);
}

.cloud-drive-page__header {
  min-height: 138px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 26px;
  overflow: hidden;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  background: var(--erp-surface, #ffffff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));

  &::before,
  &::after {
    display: none;
    content: none;
  }

  h1 {
    margin: 5px 0 7px;
    color: var(--drive-ink);
    font-size: 30px;
    font-weight: 700;
    letter-spacing: -0.02em;
    line-height: 1.15;
  }

  p { margin: 0; color: var(--drive-muted); font-size: 14px; line-height: 1.6; }
}

.cloud-drive-page__intro {
  z-index: 1;
  min-width: 320px;
  display: flex;
  align-items: center;
  gap: 18px;
}

.cloud-drive-page__brandmark {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #9fbfb2;
  border-radius: 14px;
  background: var(--drive-accent);
  color: #fff;
  box-shadow: 0 10px 22px rgba(11, 107, 83, 0.2);
  font-size: 27px;
}

.cloud-drive-page__eyebrow {
  color: var(--drive-accent);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.cloud-drive-page__header-tools {
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.cloud-drive-page__space-summary {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 14px 16px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  background: var(--erp-surface-raised, #fbfcfa);
  color: var(--drive-muted);
  font-size: 12px;
  box-shadow: var(--erp-shadow-subtle, 0 2px 10px rgba(23, 33, 29, 0.045));
}

.cloud-drive-page__summary-heading,
.cloud-drive-page__summary-footer,
.cloud-drive-page__usage {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cloud-drive-page__summary-heading {
  span { color: #8a96a8; font-size: 11px; }
  strong { max-width: 176px; overflow: hidden; color: #243552; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
}

.cloud-drive-page__usage {
  span { color: #52647e; font-size: 12px; font-weight: 600; }
  em { color: var(--drive-accent); font-size: 11px; font-style: normal; font-weight: 700; }
}

.cloud-drive-page__usage-track {
  height: 5px;
  display: block;
  overflow: hidden;
  border-radius: 999px;
  background: #e9edf5;

  > span {
    height: 100%;
    display: block;
    border-radius: inherit;
    background: var(--drive-accent);
    box-shadow: none;
  }
}

.cloud-drive-page__summary-footer {
  min-height: 22px;

  small { min-width: 0; overflow: hidden; color: #8794a7; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
  .el-button { flex: 0 0 auto; padding: 2px 0; color: var(--drive-accent); font-size: 11px; }
}

.cloud-drive-page__space-summary--recent {
  min-height: 86px;
  flex-direction: row;
  align-items: center;
  gap: 12px;

  .cloud-drive-page__summary-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  small { color: #8895a8; }
  strong { color: #283b59; font-size: 15px; }
  em { overflow: hidden; color: #728198; font-size: 10px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
}

.cloud-drive-page__summary-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--drive-accent);
  font-size: 20px;
}

.cloud-drive-page__settings {
  height: 42px;
  border-color: #9fbfb2;
  border-radius: 12px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--drive-accent);
  font-weight: 600;

  &:hover,
  &:focus { border-color: var(--drive-accent-dark); background: var(--drive-accent-dark); color: #fff; }
}

.cloud-drive-shell {
  display: flex;
  align-items: stretch;
  gap: 18px;
  min-height: calc(100vh - 336px);
  margin-top: 20px;
}

.cloud-drive-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  background: var(--erp-surface, #ffffff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));
}
.cloud-drive-content__alert {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 14px 16px 0;

  .el-alert { flex: 1; }
}
.cloud-drive-content__quota-alert { margin: 14px 16px 0; }
.cloud-drive-content__body {
  min-height: 420px;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0 18px 18px;
}

.cloud-drive-empty {
  min-height: 410px;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  color: #8996a9;
  text-align: center;

  strong { color: #34455f; font-size: 16px; font-weight: 600; }
  > span:not(.cloud-drive-empty__icon) { max-width: 360px; font-size: 12px; line-height: 1.6; }
}

.cloud-drive-empty__icon {
  width: 86px;
  height: 86px;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  border: 1px solid #c6ddd3;
  border-radius: 20px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--drive-accent);
  box-shadow: none;
  font-size: 38px;

  &::before {
    width: 22px;
    height: 22px;
    position: absolute;
    top: -7px;
    right: -8px;
    border: 5px solid #f4f7fb;
    border-radius: 50%;
    background: #62c8d5;
    content: '';
    box-shadow: 0 6px 14px rgba(54, 166, 181, 0.24);
  }
}

.cloud-drive-empty__actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;

  ::v-deep .el-button {
    min-width: 112px;
    height: 40px;
    border-radius: 11px;
  }
}

.cloud-drive-empty__writable {
  display: flex;
  align-items: center;
  flex-direction: column;
  color: #8996a9;
  font-size: 12px;
  line-height: 1.6;
}

.cloud-drive-pagination { padding: 14px 18px 18px; text-align: right; }

@media (max-width: 1280px) {
  .cloud-drive-page { padding-right: 18px; padding-left: 18px; }
  .cloud-drive-page__header { padding-right: 20px; padding-left: 20px; }
  .cloud-drive-page__space-summary { width: 248px; }
}

@media (max-width: 1120px) {
  .cloud-drive-shell { gap: 12px; }
  .cloud-drive-page__header { align-items: flex-start; flex-direction: column; }
  .cloud-drive-page__header-tools { width: 100%; justify-content: space-between; }
  .cloud-drive-page__space-summary { width: min(420px, calc(100% - 140px)); }
}
</style>
