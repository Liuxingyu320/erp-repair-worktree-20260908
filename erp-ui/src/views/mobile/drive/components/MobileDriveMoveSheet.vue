<template>
  <section
    v-if="visible"
    class="detail-mask mobile-drive-move-mask"
    @click.self="$emit('close')"
  >
    <article
      ref="dialog"
      class="glass-panel mobile-drive-move-sheet"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mobile-drive-move-title"
      tabindex="-1"
    >
      <header>
        <div>
          <span>移动到</span>
          <h2 id="mobile-drive-move-title">{{ node ? node.nodeName : '选择目标文件夹' }}</h2>
        </div>
        <button ref="closeButton" type="button" aria-label="关闭移动面板" @click="$emit('close')">关闭</button>
      </header>

      <nav aria-label="目标文件夹路径">
        <button type="button" @click="goRoot">根目录</button>
        <template v-for="item in breadcrumbs">
          <span :key="'separator-' + item.nodeId" aria-hidden="true">/</span>
          <button :key="item.nodeId" type="button" @click="goToBreadcrumb(item)">{{ item.nodeName }}</button>
        </template>
      </nav>

      <div v-if="error" class="mobile-drive-move-sheet__error" role="alert">{{ error }}</div>
      <div class="mobile-drive-move-sheet__create">
        <span>没有合适目录时，可直接在当前位置创建。</span>
        <button
          type="button"
          :disabled="busy || creatingFolder || forbiddenCurrentTarget"
          @click="createFolder"
        >{{ creatingFolder ? '创建中…' : '新建文件夹' }}</button>
      </div>
      <div class="mobile-drive-move-sheet__folders" aria-label="目标文件夹">
        <div v-if="loading" class="mobile-drive-move-sheet__state" role="status">正在加载文件夹…</div>
        <button
          v-for="folder in folders"
          v-else
          :key="folder.nodeId"
          type="button"
          :disabled="isForbidden(folder)"
          :aria-label="'进入目标文件夹 ' + folder.nodeName"
          @click="enterFolder(folder)"
        >
          <span aria-hidden="true"><i class="el-icon-folder" /></span>
          <strong>{{ folder.nodeName }}</strong>
          <small>{{ isForbidden(folder) ? '不可移动到此处' : '进入' }}</small>
        </button>
        <div v-if="!loading && !folders.length" class="mobile-drive-move-sheet__state" role="status">
          当前目录没有子文件夹
        </div>
        <button
          v-if="loadedCount < total"
          type="button"
          class="mobile-drive-move-sheet__more"
          :disabled="loadingMore"
          @click="loadMore"
        >
          {{ loadingMore ? '加载中…' : '加载更多文件夹' }}
        </button>
      </div>

      <footer>
        <span>{{ isSameParentTarget ? '当前文件已在所选文件夹' : `当前位置：${currentPathLabel}` }}</span>
        <button
          type="button"
          :disabled="busy || creatingFolder || forbiddenCurrentTarget || isSameParentTarget"
          @click="confirmMove"
        >
          {{ busy ? '移动中…' : '移动到这里' }}
        </button>
      </footer>
    </article>
  </section>
</template>

<script>
import { createDriveFolder, listDriveNodes } from '@/api/drive'
import { mountMobileOverlay, releaseMobileOverlay } from '../../feature/components/mobileOverlayStack'
import { createMobileDialogFocusManager } from '../../feature/components/mobileDialogFocus'

const { driveErrorMessage, isMoveFolderLoadCurrent, parseDriveBlobError } = require('../../../drive/driveState')
const OVERLAY_CLASS = 'mobile-drive-move-sheet-open'

export default {
  name: 'MobileDriveMoveSheet',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null },
    spaceId: { type: [Number, String], default: null },
    busy: { type: Boolean, default: false }
  },
  data() {
    return {
      currentParentId: 0,
      breadcrumbs: [],
      folders: [],
      total: 0,
      loadedCount: 0,
      pageNum: 1,
      pageSize: 100,
      requestSequence: 0,
      loading: false,
      loadingMore: false,
      creatingFolder: false,
      error: '',
      focusManager: null
    }
  },
  computed: {
    currentPathLabel() {
      return this.breadcrumbs.length
        ? this.breadcrumbs.map(item => item.nodeName).join(' / ')
        : '根目录'
    },
    forbiddenCurrentTarget() {
      if (!this.node || this.node.nodeType !== 'FOLDER') return false
      const sourceId = Number(this.node.nodeId)
      return Number(this.currentParentId) === sourceId ||
        this.breadcrumbs.some(item => Number(item.nodeId) === sourceId)
    },
    isSameParentTarget() {
      return Boolean(this.node) && Number(this.currentParentId) === Number(this.node.parentId)
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.reset()
        this.openOverlay()
      } else {
        this.requestSequence += 1
        this.closeOverlay()
      }
    },
    node() {
      if (this.visible) this.reset()
    }
  },
  created() {
    this.focusManager = createMobileDialogFocusManager({
      getContainer: () => this.$refs.dialog,
      getInitialFocus: () => this.$refs.closeButton || this.$refs.dialog,
      onEscape: () => this.$emit('close')
    })
  },
  mounted() {
    if (this.visible) {
      this.reset()
      this.openOverlay()
    }
  },
  beforeDestroy() {
    this.requestSequence += 1
    this.closeOverlay()
  },
  methods: {
    openOverlay() {
      mountMobileOverlay(OVERLAY_CLASS)
      this.$nextTick(() => {
        if (this.visible && this.focusManager) this.focusManager.activate()
      })
    },
    closeOverlay() {
      if (this.focusManager) this.focusManager.deactivate()
      releaseMobileOverlay(OVERLAY_CLASS)
    },
    reset() {
      this.requestSequence += 1
      this.currentParentId = 0
      this.breadcrumbs = []
      this.folders = []
      this.total = 0
      this.loadedCount = 0
      this.pageNum = 1
      this.error = ''
      this.loadFolders(true)
    },
    isCurrentRequest(request) {
      return isMoveFolderLoadCurrent(request, {
        sequence: this.requestSequence,
        visible: this.visible,
        nodeId: this.node && this.node.nodeId,
        spaceId: this.spaceId,
        parentId: this.currentParentId,
        pageNum: this.pageNum,
        pageSize: this.pageSize
      })
    },
    async loadFolders(reset) {
      if (!this.visible || this.spaceId == null) return
      if (reset) {
        this.pageNum = 1
        this.folders = []
        this.loadedCount = 0
        this.loading = true
      } else {
        this.loadingMore = true
      }
      this.error = ''
      let loadMoreFailed = false
      const request = {
        sequence: ++this.requestSequence,
        visible: this.visible,
        nodeId: this.node && this.node.nodeId,
        spaceId: this.spaceId,
        parentId: this.currentParentId,
        pageNum: this.pageNum,
        pageSize: this.pageSize
      }
      try {
        const response = await listDriveNodes({
          spaceId: request.spaceId,
          parentId: request.parentId,
          pageNum: request.pageNum,
          pageSize: request.pageSize,
          sortField: 'name',
          sortDirection: 'asc'
        })
        if (!this.isCurrentRequest(request)) return
        const responseRows = response.rows || []
        const folders = responseRows.filter(item => item.nodeType === 'FOLDER')
        this.folders = reset ? folders : this.folders.concat(folders)
        this.loadedCount = reset ? responseRows.length : this.loadedCount + responseRows.length
        this.total = Number(response.total || 0)
        if (!responseRows.length) this.loadedCount = this.total
      } catch (error) {
        if (!this.isCurrentRequest(request)) return
        const parsed = await parseDriveBlobError(error)
        if (!this.isCurrentRequest(request)) return
        this.error = driveErrorMessage(parsed.code, parsed.message)
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
    isForbidden(folder) {
      if (!this.node || this.node.nodeType !== 'FOLDER') return false
      const sourceId = Number(this.node.nodeId)
      return Number(folder.nodeId) === sourceId ||
        (folder.ancestorIds || []).map(Number).includes(sourceId)
    },
    enterFolder(folder) {
      if (this.isForbidden(folder)) return
      this.currentParentId = Number(folder.nodeId)
      this.breadcrumbs = Array.isArray(folder.breadcrumbs)
        ? folder.breadcrumbs.slice()
        : this.breadcrumbs.concat([{ nodeId: folder.nodeId, nodeName: folder.nodeName }])
      this.loadFolders(true)
    },
    goRoot() {
      this.currentParentId = 0
      this.breadcrumbs = []
      this.loadFolders(true)
    },
    goToBreadcrumb(item) {
      const index = this.breadcrumbs.findIndex(entry => Number(entry.nodeId) === Number(item.nodeId))
      this.currentParentId = Number(item.nodeId)
      this.breadcrumbs = index >= 0 ? this.breadcrumbs.slice(0, index + 1) : []
      this.loadFolders(true)
    },
    loadMore() {
      if (this.loadingMore || this.loadedCount >= this.total) return
      this.pageNum += 1
      this.loadFolders(false)
    },
    async createFolder() {
      if (this.busy || this.creatingFolder || this.forbiddenCurrentTarget) {
        return
      }
      let result
      try {
        result = await this.$prompt('请输入文件夹名称', '新建目标文件夹', {
          confirmButtonText: '创建并进入',
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
      this.creatingFolder = true
      this.error = ''
      try {
        const response = await createDriveFolder({
          spaceId: this.spaceId,
          parentId: this.currentParentId,
          name
        })
        const folder = response && response.data
        if (folder && folder.nodeId != null) {
          this.currentParentId = Number(folder.nodeId)
          this.breadcrumbs = this.breadcrumbs.concat([{
            nodeId: folder.nodeId,
            nodeName: folder.nodeName || name
          }])
        }
        await this.loadFolders(true)
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        this.error = driveErrorMessage(parsed.code, parsed.message)
      } finally {
        this.creatingFolder = false
      }
    },
    confirmMove() {
      if (this.busy || this.creatingFolder ||
          this.forbiddenCurrentTarget || this.isSameParentTarget) return
      this.$emit('confirm', this.currentParentId)
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../feature/components/mobileSheet.scss";

.mobile-drive-move-mask {
  z-index: 10080;
  padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
}

.mobile-drive-move-sheet {
  width: min(100%, 430px);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 22px 22px 0 0;
}

.mobile-drive-move-sheet header,
.mobile-drive-move-sheet footer { flex: 0 0 auto; padding: 12px 14px; background: #fff; }
.mobile-drive-move-sheet header { min-height: 62px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid #e8edf3; }
.mobile-drive-move-sheet header div { min-width: 0; }
.mobile-drive-move-sheet header span { color: #748397; font-size: 11px; font-weight: 700; }
.mobile-drive-move-sheet h2 { margin: 3px 0 0; overflow: hidden; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-move-sheet button { min-height: 44px; border: 0; border-radius: 12px; font: inherit; font-weight: 700; }
.mobile-drive-move-sheet header button { padding: 0 12px; background: #e4f1ef; color: #256d65; }
.mobile-drive-move-sheet nav { min-height: 48px; display: flex; align-items: center; gap: 2px; padding: 2px 10px; overflow-x: auto; white-space: nowrap; border-bottom: 1px solid #e8edf3; }
.mobile-drive-move-sheet nav button { padding: 0 8px; background: transparent; color: #256d65; }
.mobile-drive-move-sheet nav span { color: #a1adbb; }
.mobile-drive-move-sheet__error { margin: 8px 12px 0; padding: 10px 12px; border-radius: 12px; background: #fff0f0; color: #b43f3f; font-size: 12px; }
.mobile-drive-move-sheet__create { min-height: 56px; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 12px; border-bottom: 1px solid #e8edf3; background: #fff; }
.mobile-drive-move-sheet__create span { color: #6f7f92; font-size: 12px; line-height: 1.4; }
.mobile-drive-move-sheet__create button { flex: 0 0 auto; padding: 0 12px; background: #e3f1ee; color: #24766d; }
.mobile-drive-move-sheet__folders { min-height: 220px; flex: 1; overflow-y: auto; padding: 8px 10px; background: #f7f9fc; }
.mobile-drive-move-sheet__folders > button { width: 100%; display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 9px; margin-bottom: 6px; padding: 5px 10px; background: #fff; color: #24534e; text-align: left; }
.mobile-drive-move-sheet__folders > button span { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 10px; background: #fff3d7; color: #c78a20; }
.mobile-drive-move-sheet__folders > button strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-move-sheet__folders > button small { color: #7d8da1; }
.mobile-drive-move-sheet__folders > button:disabled { opacity: 0.5; }
.mobile-drive-move-sheet__state { min-height: 180px; display: grid; place-items: center; color: #7d8da1; font-size: 13px; text-align: center; }
.mobile-drive-move-sheet__folders .mobile-drive-move-sheet__more { display: block; background: #e4f1ef; color: #256d65; text-align: center; }
.mobile-drive-move-sheet footer { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px)); border-top: 1px solid #e8edf3; }
.mobile-drive-move-sheet footer span { min-width: 0; overflow: hidden; color: #6f7f92; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.mobile-drive-move-sheet footer button { padding: 0 14px; background: #278a7d; color: #fff; }
.mobile-drive-move-sheet footer button:disabled { color: #7f918e; background: #dfe8e6; opacity: 1; }
</style>
