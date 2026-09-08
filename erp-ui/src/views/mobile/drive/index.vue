<template>
  <div class="mobile-drive-page mobile-system-page">
    <main class="mobile-drive-shell" aria-label="企业云盘">
      <header class="mobile-drive-header">
        <button class="mobile-drive-icon-button" type="button" aria-label="返回" @click="goBack">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7-1.5 1.5L5 12l8.5-8.5L15 5Z" /></svg>
        </button>
        <div>
          <span>云端工作空间</span>
          <h1>企业云盘</h1>
        </div>
        <span class="mobile-drive-header__spacer" aria-hidden="true" />
      </header>

      <section class="mobile-drive-space-card">
        <label for="mobile-drive-space">文件空间</label>
        <select
          id="mobile-drive-space"
          v-model="activeSpaceId"
          aria-label="选择云盘空间"
          :disabled="loading || !spaces.length"
          @change="handleSpaceChange"
        >
          <option v-for="space in spaces" :key="space.spaceId" :value="space.spaceId">
            {{ space.spaceName }} · {{ formatMobileBytes(space.usedBytes) }} / {{ formatMobileBytes(space.quotaBytes) }}
          </option>
        </select>
        <p v-if="activeSpace">
          已使用 {{ formatMobileBytes(activeSpace.usedBytes) }}，
          剩余 {{ formatMobileBytes(getMobileRemainingQuotaBytes(activeSpace)) }}，
          总额度 {{ formatMobileBytes(activeSpace.quotaBytes) }}
        </p>
        <p v-if="activeSpace && activeSpace.quotaSourceLabel" class="mobile-drive-space-card__source">
          额度来源：{{ activeSpace.quotaSourceLabel }}
        </p>
        <div v-if="activeSpace && activeSpace.overQuota" class="mobile-drive-space-card__over" role="alert">
          当前已超额，需清理 {{ formatMobileBytes(activeSpace.overQuotaBytes) }} 后才能继续上传
        </div>
        <div v-if="activeSpace && activeSpace.writeBlockedMessage" class="mobile-drive-space-card__blocked" role="alert">
          {{ activeSpace.writeBlockedMessage }}。现有文件仍可查看和下载，修复预算后会自动恢复上传。
        </div>
      </section>

      <nav v-if="canCleanupCurrentSpace" class="mobile-drive-view-tabs" aria-label="云盘视图">
        <button
          type="button"
          :aria-current="activeView === 'files' ? 'page' : null"
          :class="{ 'is-active': activeView === 'files' }"
          @click="switchView('files')"
        >文件</button>
        <button
          type="button"
          :aria-current="activeView === 'trash' ? 'page' : null"
          :class="{ 'is-active': activeView === 'trash' }"
          @click="switchView('trash')"
        >回收站</button>
      </nav>

      <nav v-if="activeView === 'files'" class="mobile-drive-breadcrumbs" aria-label="云盘路径">
        <button type="button" :aria-current="parentId === 0 ? 'page' : null" @click="navigateToFolder(0, -1)">根目录</button>
        <template v-for="(folder, index) in folderStack">
          <span :key="'separator-' + folder.nodeId" aria-hidden="true">/</span>
          <button
            :key="folder.nodeId"
            type="button"
            :aria-current="index === folderStack.length - 1 ? 'page' : null"
            @click="navigateToFolder(folder.nodeId, index)"
          >
            {{ folder.nodeName }}
          </button>
        </template>
      </nav>

      <section v-if="activeView === 'files'" class="mobile-drive-search" role="search">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.5 4a6.5 6.5 0 0 1 5.1 10.5l4 4-1.4 1.4-4-4A6.5 6.5 0 1 1 10.5 4Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z" /></svg>
        <input
          v-model="keyword"
          type="search"
          aria-label="搜索当前空间的文件和文件夹"
          placeholder="搜索当前空间的文件和文件夹"
          @input="handleSearchInput"
          @keyup.enter="runSearch"
        >
        <button v-if="keyword" type="button" aria-label="清空搜索" @click="clearSearch">清除</button>
      </section>

      <section v-if="activeView === 'files' && activeSpace && activeSpace.canWrite" class="mobile-drive-upload" aria-label="文件操作">
        <div class="mobile-drive-upload__actions">
          <button type="button" :disabled="uploading" @click="createFolderInCurrent">新建文件夹</button>
          <button type="button" :disabled="uploading" @click="openFilePicker(false)">选择文件</button>
          <button type="button" :disabled="uploading" @click="openFilePicker(true)">拍照上传</button>
        </div>
        <input
          ref="fileInput"
          class="mobile-drive-hidden-input"
          type="file"
          accept=".doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.txt,.csv,.jpg,.jpeg,.png,.gif,.webp,.heic,.heif,.zip,.rar,.7z"
          hidden
          tabindex="-1"
          aria-hidden="true"
          @change="handleFileSelection($event, false)"
        >
        <input
          ref="cameraInput"
          class="mobile-drive-hidden-input"
          type="file"
          accept="image/*"
          capture="environment"
          hidden
          tabindex="-1"
          aria-hidden="true"
          @change="handleFileSelection($event, true)"
        >

        <div v-if="uploadState" class="mobile-drive-upload__status" role="status" aria-live="polite">
          <div>
            <strong>{{ uploadState.displayName || uploadState.file && uploadState.file.name }}</strong>
            <span v-if="uploadState.status === 'uploading'">正在上传 {{ uploadState.progress }}%</span>
            <span v-else-if="uploadState.status === 'done'">上传完成</span>
            <span v-else-if="uploadState.status === 'canceled'">已取消上传</span>
            <span v-else>{{ uploadState.error || '上传失败' }}</span>
          </div>
          <div v-if="uploadState.status === 'uploading'" class="mobile-drive-upload__progress" aria-hidden="true">
            <span :style="{ transform: 'scaleX(' + uploadState.progress / 100 + ')' }" />
          </div>
          <button v-if="uploadState.status === 'uploading'" type="button" @click="cancelUpload">取消上传</button>
          <button v-else-if="uploadState.status === 'failed'" type="button" @click="retryUpload">重试上传</button>
        </div>
      </section>

      <div
        v-if="pageBlockingState"
        :class="['mobile-drive-error', 'mobile-data-state', `mobile-data-state--${pageBlockingState.type}`]"
        role="alert"
      >
        <strong class="mobile-data-state__title">{{ pageBlockingState.title }}</strong>
        <span class="mobile-data-state__description">{{ pageBlockingState.description }}</span>
        <button class="mobile-button mobile-button--secondary" type="button" @click="handlePageBlockingAction(pageBlockingState.actionId)">
          {{ pageBlockingState.actionLabel }}
        </button>
      </div>

      <div v-else-if="lastTrashedNode" class="mobile-drive-undo" role="status" aria-live="polite">
        <span>“{{ lastTrashedNode.nodeName }}”已移入回收站</span>
        <button
          type="button"
          :disabled="restoringNodeId != null"
          @click="restoreTrashedNode(lastTrashedNode)"
        >{{ restoringNodeId != null ? '恢复中…' : '撤销删除' }}</button>
      </div>

      <section v-if="!pageBlockingState" class="mobile-drive-content" aria-label="云盘文件">
        <template v-if="activeView === 'trash'">
          <div v-if="trashLoading" class="mobile-drive-state" role="status">正在加载回收站…</div>
          <ul v-else-if="trashItems.length" class="mobile-drive-list">
            <li v-for="node in trashItems" :key="node.nodeId" class="mobile-drive-row">
              <div class="mobile-drive-row__main mobile-drive-row__main--static">
                <span class="mobile-drive-row__icon" :class="{ 'is-folder': node.nodeType === 'FOLDER' }" aria-hidden="true">
                  <i :class="node.nodeType === 'FOLDER' ? 'el-icon-folder' : 'el-icon-document'" />
                </span>
                <span class="mobile-drive-row__copy">
                  <strong>{{ node.nodeName }}</strong>
                  <small>保留至 {{ node.retentionUntil || node.expireTime || '30 天后' }}</small>
                </span>
              </div>
              <button
                type="button"
                class="mobile-drive-row__action"
                :disabled="restoringNodeId != null"
                @click="restoreTrashedNode(node)"
              >{{ restoringNodeId === node.nodeId ? '恢复中' : '恢复' }}</button>
            </li>
          </ul>
          <div v-else class="mobile-drive-state" role="status">
            回收站为空，删除的文件会在这里保留 30 天
          </div>
        </template>

        <template v-else>
        <div v-if="loading && pageNum === 1" class="mobile-drive-state" role="status">正在加载文件…</div>

        <ul v-else-if="nodes.length" class="mobile-drive-list">
          <li v-for="node in nodes" :key="node.nodeId" class="mobile-drive-row">
            <button
              v-if="node.nodeType === 'FOLDER'"
              type="button"
              class="mobile-drive-row__main"
              :aria-label="'打开文件夹 ' + node.nodeName"
              @click="openFolder(node)"
            >
              <span class="mobile-drive-row__icon is-folder" aria-hidden="true">
                <i class="el-icon-folder" />
              </span>
              <span class="mobile-drive-row__copy">
                <strong>{{ node.nodeName }}</strong>
                <small v-if="keyword && node.logicalPath">{{ node.logicalPath }}</small>
                <small v-else>文件夹</small>
              </span>
            </button>
            <button
              v-else-if="isMobilePreviewable(node)"
              type="button"
              class="mobile-drive-row__main"
              :aria-label="'预览 ' + node.nodeName"
              @click="openPreview(node)"
            >
              <span class="mobile-drive-row__icon" aria-hidden="true">
                <i class="el-icon-document" />
              </span>
              <span class="mobile-drive-row__copy">
                <strong>{{ node.nodeName }}</strong>
                <small v-if="keyword && node.logicalPath">{{ node.logicalPath }}</small>
                <small v-else>{{ formatMobileBytes(node.sizeBytes) }}</small>
              </span>
            </button>
            <div v-else class="mobile-drive-row__main mobile-drive-row__main--static">
              <span class="mobile-drive-row__icon" aria-hidden="true">
                <i class="el-icon-document" />
              </span>
              <span class="mobile-drive-row__copy">
                <strong>{{ node.nodeName }}</strong>
                <small v-if="keyword && node.logicalPath">{{ node.logicalPath }}</small>
                <small v-else>{{ formatMobileBytes(node.sizeBytes) }}</small>
              </span>
            </div>
            <button
              v-if="canManageNode(node)"
              type="button"
              class="mobile-drive-row__action"
              :aria-label="'更多操作 ' + node.nodeName"
              @click="openNodeActions(node)"
            >更多</button>
            <button
              v-else-if="node.nodeType === 'FILE'"
              type="button"
              class="mobile-drive-row__action"
              :disabled="downloadingNodeId === node.nodeId"
              :aria-label="'下载 ' + node.nodeName"
              @click="downloadNode(node)"
            >
              {{ downloadingNodeId === node.nodeId ? '处理中' : '下载' }}
            </button>
          </li>
        </ul>

        <div v-else-if="!loading" class="mobile-drive-state mobile-data-state mobile-data-state--empty mobile-data-state--compact" role="status">
          <strong class="mobile-data-state__title">{{ keyword ? '没有符合当前条件的记录' : '还没有文件' }}</strong>
          <p class="mobile-data-state__description">
            {{ keyword ? '没有找到匹配文件，可清除搜索后重试。' : '当前文件夹为空，可上传文件或新建文件夹。' }}
          </p>
          <div v-if="!keyword && activeSpace && activeSpace.canWrite" class="mobile-drive-empty-actions">
            <button class="mobile-button mobile-button--secondary" type="button" :disabled="uploading" @click="createFolderInCurrent">新建文件夹</button>
            <button class="mobile-button mobile-button--primary" type="button" :disabled="uploading" @click="openFilePicker(false)">上传文件</button>
          </div>
          <button v-else-if="keyword" class="mobile-button mobile-button--secondary" type="button" @click="clearSearch">清除搜索</button>
        </div>

        <button
          v-if="hasMore"
          type="button"
          class="mobile-drive-load-more"
          :disabled="loadingMore"
          @click="loadMore"
        >
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
        </template>
      </section>

      <MobileDriveActionSheet
        :visible="Boolean(actionNode)"
        :node="actionNode"
        @close="closeNodeActions"
        @download="downloadFromActions"
        @rename="openRenameFromActions"
        @move="openMoveFromActions"
        @trash="openTrashFromActions"
      />

      <MobileDriveRenameDialog
        :visible="Boolean(renameNode)"
        :node="renameNode"
        :busy="operationBusy"
        :error="mutationError"
        @close="closeRename"
        @confirm="confirmRename"
      />

      <MobileDriveMoveSheet
        :visible="Boolean(moveNode)"
        :node="moveNode"
        :space-id="moveNode ? moveNode.spaceId : null"
        :busy="operationBusy"
        @close="closeMove"
        @confirm="confirmMove"
      />

      <MobileConfirmDialog
        :open="Boolean(trashTarget)"
        :busy="operationBusy"
        title="移入回收站"
        message="移入回收站后保留 30 天，期间仍占用云盘容量。"
        confirm-text="移入回收站"
        :icon-paths="dialogIconPaths"
        @cancel="closeTrashConfirm"
        @confirm="confirmTrash"
      />

      <div v-if="previewNode" class="mobile-drive-preview" role="dialog" aria-modal="true" :aria-label="previewNode.nodeName">
        <section class="mobile-drive-preview__card">
          <header>
            <strong>{{ previewNode.nodeName }}</strong>
            <button type="button" aria-label="关闭预览" @click="closePreview">关闭</button>
          </header>
          <div class="mobile-drive-preview__body">
            <div v-if="previewLoading" class="mobile-drive-preview__state" role="status">正在准备预览…</div>
            <div v-else-if="previewError" class="mobile-drive-preview__state is-error" role="alert">{{ previewError }}</div>
            <pre v-else-if="previewKind === 'text'">{{ previewText }}</pre>
            <template v-else-if="previewObjectUrl && previewKind === 'image'">
              <img :src="previewObjectUrl" :alt="previewNode.nodeName">
              <p>可双指缩放查看；如显示异常，请下载原文件</p>
            </template>
            <template v-else-if="previewObjectUrl && previewKind === 'pdf'">
              <iframe :src="previewObjectUrl" title="文件预览" />
              <p>可在上方直接浏览；如显示异常，请下载原文件</p>
            </template>
            <div v-else class="mobile-drive-preview__state">
              <strong>此文件暂不支持在线预览</strong>
              <span>当前设备无法内嵌预览，请下载后查看</span>
            </div>
          </div>
          <footer>
            <button type="button" @click="closePreview">关闭</button>
            <button type="button" :disabled="downloadingNodeId != null" @click="downloadNode(previewNode)">下载文件</button>
          </footer>
        </section>
      </div>
    </main>
  </div>
</template>

<script>
import {
  createDriveFolder,
  getDriveContent,
  getDriveNode,
  listDriveNodes,
  listDriveSpaces,
  listDriveTrash,
  moveDriveNode,
  renameDriveNode,
  restoreDriveNode,
  trashDriveNode,
  uploadDriveFile
} from '@/api/drive'
import MobileConfirmDialog from '@/views/mobile/feature/components/MobileConfirmDialog.vue'
import MobileDriveActionSheet from './components/MobileDriveActionSheet.vue'
import MobileDriveMoveSheet from './components/MobileDriveMoveSheet.vue'
import MobileDriveRenameDialog from './components/MobileDriveRenameDialog.vue'

const {
  clearDriveTimer,
  driveErrorMessage,
  isDriveRequestCanceled,
  parseDriveBlobError,
  releaseDriveObjectUrl
} = require('@/views/drive/driveState')
const {
  canStartMobileUpload,
  clearMobileUploadForNode,
  createMobileUploadState,
  formatMobileBytes,
  getMobilePreviewKind,
  getMobileRemainingQuotaBytes,
  getMobileFolderParentId,
  isMobileUploadDestinationCurrent,
  isMobilePreviewable,
  markMobileUploadCanceled,
  markMobileUploadDone,
  markMobileUploadFailed,
  popMobileFolder,
  prepareCapturedFile,
  pushMobileFolder,
  renameMobileUploadNode,
  saveMobileBlob,
  selectInitialDriveSpace,
  updateMobileUploadProgress
} = require('./mobileDriveState')

export default {
  name: 'MobileCloudDrive',
  components: {
    MobileConfirmDialog,
    MobileDriveActionSheet,
    MobileDriveMoveSheet,
    MobileDriveRenameDialog
  },
  data() {
    return {
      spaces: [],
      activeSpaceId: null,
      activeView: 'files',
      parentId: 0,
      folderStack: [],
      nodes: [],
      pageNum: 1,
      pageSize: 50,
      total: 0,
      trashItems: [],
      trashLoading: false,
      restoringNodeId: null,
      lastTrashedNode: null,
      keyword: '',
      loading: false,
      loadingMore: false,
      errorMessage: '',
      previewNode: null,
      previewObjectUrl: '',
      previewText: '',
      previewKind: 'unsupported',
      previewLoading: false,
      previewError: '',
      previewSequence: 0,
      uploadState: null,
      uploadSequence: 0,
      uploadController: null,
      downloadingNodeId: null,
      actionNode: null,
      renameNode: null,
      moveNode: null,
      trashTarget: null,
      operationBusy: false,
      mutationError: '',
      dialogIconPaths: {
        close: 'M6.4 5 5 6.4l5.6 5.6L5 17.6 6.4 19l5.6-5.6 5.6 5.6 1.4-1.4-5.6-5.6L19 6.4 17.6 5 12 10.6 6.4 5Z'
      },
      searchTimer: null,
      loadSequence: 0,
      trashSequence: 0
    }
  },
  computed: {
    activeSpace() {
      return this.spaces.find(space => Number(space.spaceId) === Number(this.activeSpaceId)) || null
    },
    hasMore() {
      return this.nodes.length < this.total
    },
    uploading() {
      return Boolean(this.uploadState && this.uploadState.status === 'uploading')
    },
    canCleanupCurrentSpace() {
      return Boolean(this.activeSpace && (
        this.activeSpace.canCleanup == null
          ? this.activeSpace.canWrite
          : this.activeSpace.canCleanup
      ))
    },
    pageBlockingState() {
      const message = String(this.errorMessage || '')
      if (!message) return null
      if (/登录状态已过期|登录已过期|会话失效|请重新登录|未登录|401/i.test(message)) {
        return {
          type: 'session-expired',
          title: '登录状态已过期',
          description: message,
          actionId: 'relogin',
          actionLabel: '重新登录'
        }
      }
      if (/权限|无权|未授权|Forbidden|403/i.test(message)) {
        return {
          type: 'permission-error',
          title: '你没有查看此内容的权限',
          description: message,
          actionId: 'go-home',
          actionLabel: '返回有效入口'
        }
      }
      return {
        type: 'network-error',
        title: '暂时无法加载数据',
        description: message,
        actionId: 'retry',
        actionLabel: '重试'
      }
    }
  },
  created() {
    this.initialize()
  },
  beforeDestroy() {
    this.disposeTransientState()
  },
  beforeRouteLeave(to, from, next) {
    this.disposeTransientState()
    next()
  },
  methods: {
    formatMobileBytes,
    getMobileRemainingQuotaBytes,
    isMobilePreviewable,
    disposeTransientState() {
      this.searchTimer = clearDriveTimer(this.searchTimer, clearTimeout)
      this.loadSequence += 1
      this.trashSequence += 1
      this.previewSequence += 1
      this.cancelActiveUpload(false)
      this.uploadSequence += 1
      this.releasePreviewUrl()
    },
    async initialize() {
      await this.loadSpaces()
      if (this.activeSpaceId != null) await this.loadCurrentView()
    },
    async loadSpaces(preferredSpaceId) {
      try {
        const response = await listDriveSpaces()
        this.spaces = Array.isArray(response.data) ? response.data : []
        const requestedSpaceId = preferredSpaceId == null ? this.activeSpaceId : preferredSpaceId
        const preferred = this.spaces.find(space => Number(space.spaceId) === Number(requestedSpaceId))
        const selected = preferred || selectInitialDriveSpace(this.spaces)
        this.activeSpaceId = selected ? selected.spaceId : null
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.errorMessage = driveErrorMessage(parsed.code, parsed.message)
      }
    },
    async retryLoad() {
      this.errorMessage = ''
      if (!this.spaces.length || this.activeSpaceId == null) await this.loadSpaces()
      if (this.activeSpaceId != null) await this.loadCurrentView()
    },
    handlePageBlockingAction(actionId) {
      if (actionId === 'relogin') {
        this.$store.dispatch('LogOut').then(() => {
          this.$router.push(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`).catch(() => {})
        }).catch(() => {
          this.$router.push('/login').catch(() => {})
        })
        return
      }
      if (actionId === 'go-home') {
        this.goBack()
        return
      }
      this.retryLoad()
    },
    loadCurrentView() {
      return this.activeView === 'trash'
        ? this.loadTrash()
        : this.loadNodes(true)
    },
    switchView(view) {
      if (view === this.activeView) return
      if (view === 'trash' && !this.canCleanupCurrentSpace) return
      this.loadSequence += 1
      this.trashSequence += 1
      this.activeView = view
      this.errorMessage = ''
      this.loadCurrentView()
    },
    isCurrentTrashRequest(request) {
      return request.sequence === this.trashSequence &&
        this.activeView === 'trash' &&
        Number(request.spaceId) === Number(this.activeSpaceId)
    },
    async loadTrash() {
      if (this.activeSpaceId == null || !this.canCleanupCurrentSpace) {
        this.trashItems = []
        return
      }
      const request = {
        sequence: ++this.trashSequence,
        spaceId: this.activeSpaceId
      }
      this.trashLoading = true
      this.errorMessage = ''
      try {
        const response = await listDriveTrash({
          spaceId: request.spaceId
        })
        if (!this.isCurrentTrashRequest(request)) return
        this.trashItems = Array.isArray(response.data) ? response.data : []
      } catch (error) {
        if (!this.isCurrentTrashRequest(request)) return
        const parsed = await parseDriveBlobError(error)
        if (!this.isCurrentTrashRequest(request)) return
        this.errorMessage = driveErrorMessage(parsed.code, parsed.message)
      } finally {
        if (this.isCurrentTrashRequest(request)) this.trashLoading = false
      }
    },
    async fetchFolderStack(parentId) {
      if (Number(parentId) === 0) return []
      const response = await getDriveNode(parentId)
      const breadcrumbs = response.data && Array.isArray(response.data.breadcrumbs)
        ? response.data.breadcrumbs
        : []
      return breadcrumbs.map(folder => ({
        nodeId: Number(folder.nodeId),
        nodeName: folder.nodeName == null ? '' : String(folder.nodeName)
      }))
    },
    isCurrentRequest(request) {
      return request.sequence === this.loadSequence &&
        this.activeView === 'files' &&
        Number(request.spaceId) === Number(this.activeSpaceId) &&
        Number(request.parentId) === Number(this.parentId) &&
        request.keyword === this.keyword &&
        request.pageNum === this.pageNum
    },
    async loadNodes(reset) {
      if (this.activeSpaceId == null) return
      if (reset) {
        this.pageNum = 1
        this.clearNodeResults()
      }
      const request = {
        sequence: ++this.loadSequence,
        spaceId: this.activeSpaceId,
        parentId: this.parentId,
        keyword: this.keyword,
        pageNum: this.pageNum
      }
      if (reset) this.loading = true
      else this.loadingMore = true
      this.errorMessage = ''
      let loadMoreFailed = false
      try {
        const breadcrumbs = reset ? await this.fetchFolderStack(request.parentId) : this.folderStack
        if (!this.isCurrentRequest(request)) return
        const response = await listDriveNodes({
          spaceId: request.spaceId,
          parentId: request.parentId,
          keyword: request.keyword || undefined,
          pageNum: request.pageNum,
          pageSize: this.pageSize
        })
        if (!this.isCurrentRequest(request)) return
        const rows = Array.isArray(response.rows) ? response.rows : []
        this.folderStack = breadcrumbs
        this.nodes = reset ? rows : this.nodes.concat(rows)
        this.total = Number(response.total || 0)
      } catch (error) {
        if (!this.isCurrentRequest(request)) return
        const parsed = await parseDriveBlobError(error)
        if (!this.isCurrentRequest(request)) return
        this.errorMessage = driveErrorMessage(parsed.code, parsed.message)
        loadMoreFailed = !reset
      } finally {
        if (this.isCurrentRequest(request)) {
          this.loading = false
          this.loadingMore = false
          if (!reset && loadMoreFailed) {
            this.pageNum = Math.max(1, request.pageNum - 1)
          }
        }
      }
    },
    clearNodeResults() {
      this.nodes = []
      this.total = 0
      this.errorMessage = ''
    },
    canManageNode(node) {
      return Boolean(node && (node.canWrite || node.canDelete))
    },
    openNodeActions(node) {
      if (!this.canManageNode(node)) return
      this.mutationError = ''
      this.actionNode = node
    },
    closeNodeActions() {
      this.actionNode = null
    },
    downloadFromActions() {
      const node = this.actionNode
      this.closeNodeActions()
      if (node) this.$nextTick(() => this.downloadNode(node))
    },
    openRenameFromActions() {
      const node = this.actionNode
      this.closeNodeActions()
      if (node && node.canWrite) {
        this.$nextTick(() => { this.renameNode = node })
      }
    },
    openMoveFromActions() {
      const node = this.actionNode
      this.closeNodeActions()
      if (node && node.canWrite) {
        this.$nextTick(() => { this.moveNode = node })
      }
    },
    openTrashFromActions() {
      const node = this.actionNode
      this.closeNodeActions()
      if (node && node.canDelete) {
        this.$nextTick(() => { this.trashTarget = node })
      }
    },
    closeRename() {
      if (this.operationBusy) return
      this.renameNode = null
      this.mutationError = ''
    },
    closeMove() {
      if (this.operationBusy) return
      this.moveNode = null
      this.mutationError = ''
    },
    closeTrashConfirm() {
      if (this.operationBusy) return
      this.trashTarget = null
    },
    async confirmRename(name) {
      const node = this.renameNode
      const nextName = String(name || '').trim()
      if (!node || !node.canWrite || !nextName || this.operationBusy) return
      if (nextName === node.nodeName) {
        this.renameNode = null
        return
      }
      this.operationBusy = true
      this.mutationError = ''
      try {
        await renameDriveNode(node.nodeId, { name: nextName, version: node.version })
        this.uploadState = renameMobileUploadNode(
          this.uploadState,
          node.nodeId,
          nextName
        )
        this.renameNode = null
        this.$message.success('重命名成功')
        await this.loadNodes(true)
      } catch (error) {
        await this.handleMobileMutationError(error, 'rename')
      } finally {
        this.operationBusy = false
      }
    },
    async confirmMove(targetParentId) {
      if (!this.moveNode || !this.moveNode.canWrite || this.operationBusy) return
      if (Number(targetParentId) === Number(this.moveNode.parentId)) {
        this.moveNode = null
        this.$message.info('文件已经在该位置')
        return
      }
      this.operationBusy = true
      this.mutationError = ''
      try {
        await moveDriveNode(this.moveNode.nodeId, {
          targetParentId,
          version: this.moveNode.version
        })
        this.uploadState = clearMobileUploadForNode(
          this.uploadState,
          this.moveNode.nodeId
        )
        this.moveNode = null
        this.$message.success('移动成功')
        await this.loadNodes(true)
      } catch (error) {
        await this.handleMobileMutationError(error, 'move')
      } finally {
        this.operationBusy = false
      }
    },
    async confirmTrash() {
      const node = this.trashTarget
      if (!node || !node.canDelete || this.operationBusy) return
      this.operationBusy = true
      try {
        await trashDriveNode(node.nodeId, node.version)
        this.uploadState = clearMobileUploadForNode(
          this.uploadState,
          node.nodeId
        )
        this.lastTrashedNode = { ...node }
        this.trashTarget = null
        this.$message.success('已移入回收站')
        await this.loadSpaces(this.activeSpaceId)
        await this.loadNodes(true)
      } catch (error) {
        await this.handleMobileMutationError(error, 'trash')
      } finally {
        this.operationBusy = false
      }
    },
    async restoreTrashedNode(node) {
      if (!node || this.restoringNodeId != null) return
      this.restoringNodeId = node.nodeId
      try {
        const response = await restoreDriveNode(node.nodeId)
        if (this.lastTrashedNode &&
            Number(this.lastTrashedNode.nodeId) === Number(node.nodeId)) {
          this.lastTrashedNode = null
        }
        this.$message.success(response.msg || '恢复成功')
        await this.loadSpaces(this.activeSpaceId)
        await this.loadCurrentView()
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
        if (this.activeView === 'trash') await this.loadTrash()
      } finally {
        this.restoringNodeId = null
      }
    },
    async handleMobileMutationError(error, source) {
      const parsed = await parseDriveBlobError(error)
      const message = driveErrorMessage(parsed.code, parsed.message)
      if (parsed.code === 'DRIVE_CONCURRENT_MODIFICATION') {
        this.renameNode = null
        this.moveNode = null
        this.trashTarget = null
        this.$message.warning(message)
        await this.loadNodes(true)
        return
      }
      if (source === 'rename') this.mutationError = message
      else this.$message.error(message)
    },
    handleSpaceChange() {
      if (this.activeView === 'trash' && !this.canCleanupCurrentSpace) {
        this.activeView = 'files'
      }
      this.parentId = 0
      this.folderStack = []
      this.nodes = []
      this.keyword = ''
      this.pageNum = 1
      this.lastTrashedNode = null
      this.loadCurrentView()
    },
    openFolder(node) {
      const wasSearching = Boolean(this.keyword)
      this.parentId = Number(node.nodeId)
      this.keyword = ''
      this.pageNum = 1
      this.folderStack = wasSearching ? [] : pushMobileFolder(this.folderStack, node)
      this.loadNodes(true)
    },
    navigateToFolder(nodeId, index) {
      this.parentId = Number(nodeId) || 0
      this.folderStack = index < 0 ? [] : this.folderStack.slice(0, index + 1)
      this.keyword = ''
      this.pageNum = 1
      this.loadNodes(true)
    },
    goBack() {
      if (this.activeView === 'trash') {
        this.switchView('files')
        return
      }
      if (this.parentId !== 0) {
        this.folderStack = popMobileFolder(this.folderStack)
        this.parentId = getMobileFolderParentId(this.folderStack)
        this.keyword = ''
        this.pageNum = 1
        this.loadNodes(true)
        return
      }
      if (this.$router && typeof this.$router.back === 'function') this.$router.back()
      else if (this.$router) this.$router.push('/mobile/mine').catch(() => {})
    },
    handleSearchInput() {
      this.pageNum = 1
      this.searchTimer = clearDriveTimer(this.searchTimer, clearTimeout)
      this.searchTimer = setTimeout(() => {
        this.searchTimer = null
        this.loadNodes(true)
      }, 300)
    },
    runSearch() {
      this.searchTimer = clearDriveTimer(this.searchTimer, clearTimeout)
      this.pageNum = 1
      this.loadNodes(true)
    },
    clearSearch() {
      this.keyword = ''
      this.runSearch()
    },
    loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.pageNum += 1
      this.loadNodes(false)
    },
    openFilePicker(camera) {
      if (this.uploading) return
      const input = camera ? this.$refs.cameraInput : this.$refs.fileInput
      if (input) input.click()
    },
    async createFolderInCurrent() {
      if (!this.activeSpace || !this.activeSpace.canWrite ||
          this.operationBusy) return
      let result
      try {
        result = await this.$prompt('请输入文件夹名称', '新建文件夹', {
          confirmButtonText: '创建',
          cancelButtonText: '取消',
          inputPlaceholder: '文件夹名称',
          inputValidator: value => Boolean(value && String(value).trim()) ||
            '文件夹名称不能为空'
        })
      } catch (_) {
        return
      }
      const name = String(result.value || '').trim()
      if (!name) return
      this.operationBusy = true
      try {
        await createDriveFolder({
          spaceId: this.activeSpaceId,
          parentId: this.parentId,
          name
        })
        this.$message.success('文件夹已创建')
        await this.loadNodes(true)
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
      } finally {
        this.operationBusy = false
      }
    },
    handleFileSelection(event, camera) {
      const input = event && event.target
      const selected = input && input.files && input.files[0]
      if (input) input.value = ''
      if (!selected || !canStartMobileUpload(this.uploadState)) return
      const file = camera ? prepareCapturedFile(selected) : selected
      this.uploadSelectedFile(file)
    },
    async uploadSelectedFile(file, originalTarget) {
      if (!file || !canStartMobileUpload(this.uploadState)) return
      const uploadSequence = ++this.uploadSequence
      const controller = new AbortController()
      this.uploadController = controller
      const targetSpaceId = originalTarget ? originalTarget.targetSpaceId : this.activeSpaceId
      const targetParentId = originalTarget ? originalTarget.targetParentId : this.parentId
      this.uploadState = createMobileUploadState(file, targetSpaceId, targetParentId)
      const onUploadProgress = event => {
        if (uploadSequence !== this.uploadSequence || !this.uploadState ||
          this.uploadState.file !== file || this.uploadState.status !== 'uploading') return
        this.uploadState = updateMobileUploadProgress(this.uploadState, event)
      }
      try {
        const response = await uploadDriveFile(
          file,
          targetSpaceId,
          targetParentId,
          onUploadProgress,
          controller.signal
        )
        if (uploadSequence !== this.uploadSequence) return
        this.uploadState = markMobileUploadDone(
          this.uploadState,
          response && response.data
        )
        await this.loadSpaces()
        if (uploadSequence !== this.uploadSequence) return
        if (isMobileUploadDestinationCurrent(this.uploadState, this.activeSpaceId, this.parentId)) {
          await this.loadNodes(true)
        }
      } catch (error) {
        if (uploadSequence !== this.uploadSequence) return
        if (isDriveRequestCanceled(error)) {
          this.uploadState = markMobileUploadCanceled(this.uploadState)
          return
        }
        const parsed = await parseDriveBlobError(error)
        if (uploadSequence !== this.uploadSequence) return
        this.uploadState = markMobileUploadFailed(
          this.uploadState,
          driveErrorMessage(parsed.code, parsed.message)
        )
      } finally {
        if (uploadSequence === this.uploadSequence && this.uploadController === controller) {
          this.uploadController = null
        }
      }
    },
    cancelUpload() {
      this.cancelActiveUpload()
    },
    cancelActiveUpload(markCanceled = true) {
      if (this.uploadController && typeof this.uploadController.abort === 'function') {
        this.uploadController.abort()
      }
      this.uploadController = null
      if (markCanceled && this.uploadState && this.uploadState.status === 'uploading') {
        this.uploadState = markMobileUploadCanceled(this.uploadState)
      }
    },
    retryUpload() {
      if (!this.uploadState || this.uploadState.status !== 'failed') return
      const failed = this.uploadState
      this.uploadSelectedFile(failed.file, {
        targetSpaceId: failed.targetSpaceId,
        targetParentId: failed.targetParentId
      })
    },
    async openPreview(node) {
      const requestSequence = ++this.previewSequence
      this.releasePreviewUrl()
      this.previewNode = node
      this.previewKind = getMobilePreviewKind(node)
      this.previewText = ''
      this.previewError = ''
      this.previewLoading = false
      if (this.previewKind === 'unsupported') return
      this.previewLoading = true
      try {
        const blob = await getDriveContent(node.nodeId, 'preview')
        if (this.previewKind === 'text') {
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
    releasePreviewUrl() {
      this.previewObjectUrl = releaseDriveObjectUrl(this.previewObjectUrl, URL)
    },
    closePreview() {
      this.previewSequence += 1
      this.releasePreviewUrl()
      this.previewNode = null
      this.previewKind = 'unsupported'
      this.previewText = ''
      this.previewError = ''
      this.previewLoading = false
    },
    async downloadNode(node) {
      if (!node || this.downloadingNodeId != null) return
      this.downloadingNodeId = node.nodeId
      try {
        const blob = await getDriveContent(node.nodeId, 'download')
        await saveMobileBlob(blob, node.nodeName)
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.$message.error(driveErrorMessage(parsed.code, parsed.message))
      } finally {
        this.downloadingNodeId = null
      }
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-drive-page {
  min-height: 100vh;
  overflow-x: hidden;
  background: #eef4fb;
  color: #15243a;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.mobile-drive-shell {
  width: min(100%, 430px);
  min-height: 100vh;
  margin: 0 auto;
  padding: 18px 16px calc(32px + env(safe-area-inset-bottom));
  background: var(--mobile-color-page, #f4f5f2);
}

.mobile-drive-header { min-height: 64px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mobile-drive-header div { flex: 1; text-align: center; }
.mobile-drive-header span { color: #6c8db7; font-size: 10px; font-weight: 700; letter-spacing: 0.16em; }
.mobile-drive-header h1 { margin: 3px 0 0; font-size: 23px; }
.mobile-drive-icon-button,
.mobile-drive-header__spacer { width: 44px; height: 44px; flex: 0 0 44px; }
.mobile-drive-icon-button { min-height: 44px; display: grid; place-items: center; border: 0; border-radius: 14px; background: #fff; color: #35577f; box-shadow: 0 8px 24px rgba(49, 83, 121, 0.1); }
.mobile-drive-icon-button svg { width: 21px; fill: currentColor; }

.mobile-drive-space-card,
.mobile-drive-content { margin-top: 14px; border: 1px solid rgba(135, 163, 196, 0.24); border-radius: 20px; background: rgba(255, 255, 255, 0.92); box-shadow: 0 14px 36px rgba(55, 83, 117, 0.08); }
.mobile-drive-space-card { padding: 16px; }
.mobile-drive-space-card label { display: block; margin-bottom: 8px; color: #617792; font-size: 12px; font-weight: 700; }
.mobile-drive-space-card select { width: 100%; min-height: 44px; padding: 0 38px 0 12px; border: 1px solid #d8e2ef; border-radius: 12px; background: #f9fbfe; color: #20344d; font: inherit; }
.mobile-drive-space-card p { margin: 9px 2px 0; color: #8190a3; font-size: 11px; }
.mobile-drive-space-card__source { color: #58779d !important; }
.mobile-drive-space-card__over { margin-top: 10px; padding: 9px 10px; border-radius: 11px; background: #fff0f0; color: #b83f3f; font-size: 12px; line-height: 1.5; }
.mobile-drive-space-card__blocked { margin-top: 10px; padding: 9px 10px; border-radius: 11px; background: #fff7e8; color: #9a6414; font-size: 12px; line-height: 1.5; }

.mobile-drive-view-tabs { min-height: 48px; display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 10px; padding: 4px; border-radius: 14px; background: #dfeae8; }
.mobile-drive-view-tabs button { min-height: 44px; border: 0; border-radius: 11px; color: #617b77; background: transparent; font: inherit; font-weight: 700; }
.mobile-drive-view-tabs button.is-active { color: #216c64; background: #fff; box-shadow: 0 4px 12px rgba(27, 91, 83, 0.1); }

.mobile-drive-breadcrumbs { min-height: 44px; display: flex; align-items: center; gap: 2px; margin-top: 10px; overflow-x: auto; white-space: nowrap; scrollbar-width: none; }
.mobile-drive-breadcrumbs button { min-height: 44px; padding: 0 9px; border: 0; background: transparent; color: #3e638f; font: inherit; }
.mobile-drive-breadcrumbs span { color: #a2afbf; }

.mobile-drive-search { min-height: 50px; display: flex; align-items: center; gap: 8px; margin-top: 6px; padding: 3px 6px 3px 13px; border: 1px solid #d9e4f0; border-radius: 16px; background: #fff; }
.mobile-drive-search svg { width: 20px; flex: 0 0 20px; fill: #7f92a8; }
.mobile-drive-search input { min-width: 0; min-height: 44px; flex: 1; border: 0; outline: 0; background: transparent; font: inherit; }
.mobile-drive-search button { min-height: 44px; padding: 0 10px; border: 0; background: transparent; color: #3971b2; }

.mobile-drive-upload { margin-top: 10px; padding: 10px; border: 1px solid rgba(135, 163, 196, 0.24); border-radius: 16px; background: rgba(255, 255, 255, 0.92); }
.mobile-drive-upload__actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.mobile-drive-upload__actions button { min-height: 44px; border: 0; border-radius: 12px; background: #eaf3ff; color: #2e70b7; font-weight: 700; }
.mobile-drive-upload__actions button:last-child { background: #3278c5; color: #fff; }
.mobile-drive-hidden-input { position: absolute; width: 1px; height: 1px; overflow: hidden; opacity: 0; pointer-events: none; }
.mobile-drive-upload__status { position: relative; min-height: 52px; display: flex; align-items: center; gap: 9px; margin-top: 9px; padding: 8px 9px; overflow: hidden; border-radius: 12px; background: #f5f8fc; }
.mobile-drive-upload__status > div:first-child { min-width: 0; display: flex; flex: 1; flex-direction: column; gap: 3px; }
.mobile-drive-upload__status strong,
.mobile-drive-upload__status span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-upload__status strong { font-size: 12px; }
.mobile-drive-upload__status span { color: #7f8fa3; font-size: 11px; }
.mobile-drive-upload__status button { min-height: 44px; border: 0; background: transparent; color: #bd4141; font-weight: 700; }
.mobile-drive-upload__progress { position: absolute; right: 0; bottom: 0; left: 0; height: 3px; background: #dbe7f5; }
.mobile-drive-upload__progress span { width: 100%; height: 100%; display: block; background: #3479c6; transform: scaleX(0); transform-origin: left center; transition: transform 180ms cubic-bezier(.22,1,.36,1); }

.mobile-drive-error { min-height: 48px; display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 10px; padding: 8px 12px; border-radius: 14px; background: #fff0f0; color: #ba4545; font-size: 13px; }
.mobile-drive-error button { min-height: 44px; border: 0; background: transparent; color: #a83232; font-weight: 700; }
.mobile-drive-undo { min-height: 52px; display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 10px; padding: 6px 8px 6px 12px; border: 1px solid rgba(39, 138, 125, 0.2); border-radius: 14px; background: #e8f4f1; color: #235f58; font-size: 13px; }
.mobile-drive-undo span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-undo button { min-height: 44px; flex: 0 0 auto; padding: 0 11px; border: 0; border-radius: 11px; color: #fff; background: #278a7d; font-weight: 700; }
.mobile-drive-content { min-height: 310px; overflow: hidden; }
.mobile-drive-list { margin: 0; padding: 0; list-style: none; }
.mobile-drive-row { min-width: 0; min-height: 68px; display: flex; align-items: center; gap: 8px; padding: 7px 9px 7px 13px; border-bottom: 1px solid #edf1f6; }
.mobile-drive-row__main { min-width: 0; min-height: 52px; display: flex; align-items: center; gap: 11px; flex: 1; padding: 4px 0; border: 0; background: transparent; color: inherit; text-align: left; }
.mobile-drive-row__main--static { cursor: default; }
.mobile-drive-row__icon { width: 34px; height: 34px; display: grid; flex: 0 0 34px; place-items: center; border-radius: 11px; background: #eaf2fc; color: #3477bd; font-size: 18px; }
.mobile-drive-row__icon.is-folder { background: #fff3d7; color: #c78a20; }
.mobile-drive-row__copy { min-width: 0; display: flex; flex: 1; flex-direction: column; gap: 4px; }
.mobile-drive-row__copy strong,
.mobile-drive-row__copy small { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-row__copy strong { font-size: 14px; }
.mobile-drive-row__copy small { color: #8c9aac; font-size: 11px; }
.mobile-drive-row__action { min-width: 58px; min-height: 44px; padding: 0 8px; border: 0; border-radius: 12px; background: #edf5ff; color: #2e70b7; font-weight: 700; }
.mobile-drive-state { min-height: 260px; display: grid; place-items: center; padding: 24px; color: #8291a4; font-size: 13px; text-align: center; }
.mobile-drive-load-more { width: calc(100% - 24px); min-height: 44px; margin: 12px; border: 0; border-radius: 13px; background: #eef5fd; color: #3474b8; font-weight: 700; }

.mobile-drive-preview { position: fixed; z-index: 3000; inset: 0; display: flex; align-items: flex-end; justify-content: center; padding: 16px 12px calc(12px + env(safe-area-inset-bottom)); background: rgba(15, 27, 43, 0.56); }
.mobile-drive-preview__card { width: min(100%, 430px); max-height: 88vh; display: flex; flex-direction: column; overflow: hidden; border-radius: 22px; background: #fff; box-shadow: 0 28px 80px rgba(5, 16, 30, 0.3); }
.mobile-drive-preview header,
.mobile-drive-preview footer { min-height: 60px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 14px; }
.mobile-drive-preview header { border-bottom: 1px solid #edf1f6; }
.mobile-drive-preview header strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-preview button { min-height: 44px; padding: 0 14px; border: 0; border-radius: 12px; background: #edf4fd; color: #316da9; font-weight: 700; }
.mobile-drive-preview footer { justify-content: flex-end; border-top: 1px solid #edf1f6; }
.mobile-drive-preview footer button:last-child { background: #3379c7; color: #fff; }
.mobile-drive-preview__body { min-height: 280px; flex: 1; overflow: auto; padding: 14px; background: #f7f9fc; }
.mobile-drive-preview__body img { width: 100%; max-height: 58vh; display: block; object-fit: contain; border-radius: 12px; background: #e9eef5; }
.mobile-drive-preview__body iframe { width: 100%; min-height: 52vh; border: 0; border-radius: 12px; background: #fff; }
.mobile-drive-preview__body pre { min-height: 240px; margin: 0; overflow: auto; color: #27384d; font: 13px/1.7 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-wrap; word-break: break-word; }
.mobile-drive-preview__body p { margin: 10px 2px 0; color: #8391a3; font-size: 11px; text-align: center; }
.mobile-drive-preview__state { min-height: 250px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: #7f8fa3; text-align: center; }
.mobile-drive-preview__state.is-error { color: #bd4747; }

button:focus-visible,
select:focus-visible,
input:focus-visible { outline: 3px solid rgba(39, 138, 125, 0.28); outline-offset: 2px; }
button:disabled { opacity: 0.55; }

.mobile-drive-page { background: #eef4f3; color: #183b3a; }
.mobile-drive-shell { background: var(--mobile-color-page); }
.mobile-drive-header span { color: var(--mobile-color-muted); font-size: 12px; }
.mobile-drive-space-card,
.mobile-drive-upload,
.mobile-drive-content { border-color: var(--mobile-color-line); border-radius: var(--mobile-radius-lg); background: var(--mobile-color-surface); box-shadow: none; }
.mobile-drive-space-card label,
.mobile-drive-space-card p,
.mobile-drive-upload__status span,
.mobile-drive-row__copy small,
.mobile-drive-preview__body p { color: var(--mobile-color-muted); font-size: 13px; }
.mobile-drive-space-card select,
.mobile-drive-search { border-color: var(--mobile-color-line-strong); background: var(--mobile-color-surface); }
.mobile-drive-row { border-bottom-color: var(--mobile-color-line); }
.mobile-drive-icon-button,
.mobile-drive-search button,
.mobile-drive-breadcrumbs button { color: #286f67; }
.mobile-drive-row__action,
.mobile-drive-load-more,
.mobile-drive-preview button { color: #256d65; background: #e4f1ef; }
.mobile-drive-upload__actions button { color: #256d65; background: #e4f1ef; }
.mobile-drive-upload__actions button:last-child,
.mobile-drive-preview footer button:last-child { color: #fff; background: #278a7d; }
.mobile-drive-upload__progress span { background: #278a7d; }
.mobile-drive-row__icon { color: #28776e; background: #e4f1ef; }

/* Component-library polish adapted to a dense, touch-first file workspace. */
.mobile-drive-page {
  background-color: var(--mobile-color-page);
  background-image:
    radial-gradient(circle at 100% 0%, rgba(40, 102, 177, 0.08), transparent 250px),
    radial-gradient(circle at 0% 68%, rgba(11, 107, 83, 0.08), transparent 240px);
}

.mobile-drive-shell {
  background: transparent;
}

.mobile-drive-header {
  position: sticky;
  z-index: 12;
  top: 0;
  margin: -18px -16px 14px;
  padding: calc(12px + var(--mobile-safe-top, env(safe-area-inset-top, 0px))) 16px 10px;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(203, 211, 206, 0.68);
  box-shadow: 0 8px 24px rgba(23, 33, 29, 0.035);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

.mobile-drive-space-card,
.mobile-drive-upload,
.mobile-drive-content {
  border-color: rgba(203, 211, 206, 0.76);
  background: var(--mobile-gradient-surface, linear-gradient(180deg, #fff, #fdfefd));
  box-shadow: var(--mobile-shadow-card, 0 10px 28px rgba(23, 33, 29, 0.055));
}

.mobile-drive-space-card {
  background:
    linear-gradient(135deg, rgba(231, 242, 237, 0.72), rgba(255, 255, 255, 0.96) 58%),
    #fff;
}

.mobile-drive-search {
  border-color: rgba(203, 211, 206, 0.8);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--mobile-shadow-control, 0 4px 12px rgba(23, 33, 29, 0.045));
}

.mobile-drive-view-tabs {
  border: 1px solid rgba(11, 107, 83, 0.1);
  background: rgba(231, 242, 237, 0.82);
}

.mobile-drive-view-tabs button.is-active {
  color: var(--mobile-color-primary);
  background: #fff;
  box-shadow: 0 5px 14px rgba(11, 107, 83, 0.1);
}

.mobile-drive-upload__actions button:last-child,
.mobile-drive-preview footer button:last-child {
  background: var(--mobile-gradient-primary, linear-gradient(135deg, #0b6b53, #128064));
  box-shadow: var(--mobile-shadow-primary, 0 10px 24px rgba(11, 107, 83, 0.22));
}

.mobile-drive-row__main:not(.mobile-drive-row__main--static):active {
  background: var(--mobile-color-primary-soft);
}

.mobile-drive-preview {
  background: rgba(20, 31, 27, 0.46);
  backdrop-filter: blur(5px) saturate(112%);
  -webkit-backdrop-filter: blur(5px) saturate(112%);
}

.mobile-drive-preview__card {
  border: 1px solid rgba(203, 211, 206, 0.72);
  box-shadow: 0 28px 80px rgba(16, 35, 28, 0.3);
}

@media (prefers-reduced-motion: no-preference) {
  .mobile-drive-space-card,
  .mobile-drive-upload,
  .mobile-drive-content {
    animation: mobile-surface-enter var(--mobile-duration-slow, 260ms) var(--mobile-ease-spring, cubic-bezier(.22,1,.36,1)) both;
  }
}

@media (max-width: 390px) {
  .mobile-drive-upload__actions { grid-template-columns: 1fr; }
}
</style>
