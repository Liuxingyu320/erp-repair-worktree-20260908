<template>
  <el-dialog
    class="drive-move-dialog"
    :visible="visible"
    title="移动到"
    width="560px"
    append-to-body
    @close="$emit('close')"
  >
    <div class="drive-move-dialog__path">
      <el-button
        type="text"
        icon="el-icon-house"
        aria-label="返回空间根目录"
        @click="goRoot"
      >根目录</el-button>
      <template v-for="item in breadcrumbs">
        <span :key="'separator-' + item.nodeId" aria-hidden="true">/</span>
        <el-button :key="item.nodeId" type="text" @click="goToBreadcrumb(item)">
          {{ item.nodeName }}
        </el-button>
      </template>
    </div>

    <div v-if="error" class="drive-move-dialog__error" role="alert">{{ error }}</div>

    <div v-loading="loading" class="drive-move-dialog__folders" role="listbox" aria-label="目标文件夹">
      <div class="drive-move-folder is-root">
        <el-radio v-model="targetParentId" :label="0">空间根目录</el-radio>
      </div>

      <div v-for="folder in folders" :key="folder.nodeId" class="drive-move-folder">
        <el-radio
          v-model="targetParentId"
          :label="folder.nodeId"
          :disabled="isForbidden(folder)"
        >
          <i class="el-icon-folder" aria-hidden="true" />
          {{ folder.nodeName }}
        </el-radio>
        <el-button
          type="text"
          :disabled="isForbidden(folder)"
          :aria-label="'进入文件夹 ' + folder.nodeName"
          @click="enterFolder(folder)"
        >进入</el-button>
      </div>

      <el-button
        v-if="loadedCount < total"
        class="drive-move-dialog__more"
        type="text"
        :loading="loadingMore"
        @click="loadMore"
      >加载更多文件夹</el-button>

      <div v-if="!loading && !folders.length" class="drive-move-dialog__empty" role="status">
        当前目录没有子文件夹
      </div>
    </div>

    <template slot="footer">
      <span v-if="isSameParentTarget" class="drive-move-dialog__same-parent">当前文件已在所选文件夹</span>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button
        type="primary"
        :disabled="targetParentId == null || isSameParentTarget"
        @click="confirmMove"
      >移动到这里</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { listDriveNodes } from '@/api/drive'

const { driveErrorMessage, isMoveFolderLoadCurrent, parseDriveBlobError } = require('../driveState')

export default {
  name: 'DriveMoveDialog',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null },
    spaceId: { type: [Number, String], default: null }
  },
  data() {
    return {
      currentParentId: 0,
      targetParentId: 0,
      breadcrumbs: [],
      folders: [],
      total: 0,
      loadedCount: 0,
      pageNum: 1,
      pageSize: 100,
      requestSequence: 0,
      loading: false,
      loadingMore: false,
      error: ''
    }
  },
  computed: {
    isSameParentTarget() {
      return Boolean(this.node) && Number(this.targetParentId) === Number(this.node.parentId)
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.reset()
      } else {
        this.requestSequence += 1
        this.loading = false
        this.loadingMore = false
      }
    },
    node() {
      if (this.visible) this.reset()
    }
  },
  beforeDestroy() {
    this.requestSequence += 1
  },
  methods: {
    reset() {
      this.requestSequence += 1
      this.currentParentId = 0
      this.targetParentId = 0
      this.breadcrumbs = []
      this.folders = []
      this.total = 0
      this.loadedCount = 0
      this.pageNum = 1
      this.error = ''
      this.loadFolders(true)
    },
    isCurrentFolderRequest(request) {
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
      if (!this.visible || !this.spaceId) return
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
        if (!this.isCurrentFolderRequest(request)) return
        const responseRows = response.rows || []
        const rows = responseRows.filter(node => node.nodeType === 'FOLDER')
        this.folders = reset ? rows : this.folders.concat(rows)
        this.loadedCount = reset ? responseRows.length : this.loadedCount + responseRows.length
        this.total = Number(response.total || 0)
        if (!responseRows.length) this.loadedCount = this.total
      } catch (error) {
        const parsed = await parseDriveBlobError(error)
        if (!this.isCurrentFolderRequest(request)) return
        this.error = driveErrorMessage(parsed.code, parsed.message)
        loadMoreFailed = !reset
      } finally {
        if (this.isCurrentFolderRequest(request)) {
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
      const ancestorIds = (folder.ancestorIds || []).map(Number)
      return Number(folder.nodeId) === sourceId || ancestorIds.includes(sourceId)
    },
    enterFolder(folder) {
      if (this.isForbidden(folder)) return
      this.currentParentId = Number(folder.nodeId)
      this.targetParentId = Number(folder.nodeId)
      this.breadcrumbs = Array.isArray(folder.breadcrumbs)
        ? folder.breadcrumbs.slice()
        : this.breadcrumbs.concat([{ nodeId: folder.nodeId, nodeName: folder.nodeName }])
      this.loadFolders(true)
    },
    goRoot() {
      this.currentParentId = 0
      this.targetParentId = 0
      this.breadcrumbs = []
      this.loadFolders(true)
    },
    goToBreadcrumb(item) {
      const index = this.breadcrumbs.findIndex(entry => Number(entry.nodeId) === Number(item.nodeId))
      this.currentParentId = Number(item.nodeId)
      this.targetParentId = Number(item.nodeId)
      this.breadcrumbs = index >= 0 ? this.breadcrumbs.slice(0, index + 1) : []
      this.loadFolders(true)
    },
    loadMore() {
      if (this.loadingMore || this.loadedCount >= this.total) return
      this.pageNum += 1
      this.loadFolders(false)
    },
    confirmMove() {
      if (this.targetParentId == null || this.isSameParentTarget) return
      this.$emit('confirm', this.targetParentId)
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-move-dialog__path { display: flex; align-items: center; gap: 4px; min-height: 38px; overflow-x: auto; white-space: nowrap; }
.drive-move-dialog__error { margin-bottom: 10px; padding: 9px 12px; border-radius: 7px; background: #fff1f1; color: #c94b4b; }
.drive-move-dialog__folders { min-height: 270px; max-height: 420px; overflow-y: auto; border: 1px solid #e7ebf0; border-radius: 10px; }
.drive-move-folder { min-height: 48px; display: flex; align-items: center; justify-content: space-between; padding: 8px 13px; border-bottom: 1px solid #edf0f5; }
.drive-move-folder.is-root { background: #f7faff; }
.drive-move-dialog__more { width: 100%; padding: 14px; }
.drive-move-dialog__empty { padding: 70px 20px; color: #8a97a8; text-align: center; }
.drive-move-dialog__same-parent { margin-right: 12px; color: #8a97a8; font-size: 13px; }
</style>
