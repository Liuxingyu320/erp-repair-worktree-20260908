<template>
  <el-table
    v-loading="loading"
    class="drive-node-list"
    :data="nodes"
    row-key="nodeId"
    height="100%"
  >
    <el-table-column label="名称" min-width="280">
      <template slot-scope="scope">
        <div class="drive-node-name">
          <span class="drive-node-name__icon" :class="iconClass(scope.row)" aria-hidden="true" />
          <el-tooltip :content="scope.row.nodeName" placement="top" :open-delay="500">
            <button
              v-if="scope.row.nodeType === 'FOLDER'"
              type="button"
              class="drive-node-name__button"
              :aria-label="nodeAriaLabel(scope.row)"
              @click="openNode(scope.row)"
            >
              <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
              <small v-if="(keyword || mode === 'recent') && nodeContext(scope.row)" class="drive-node-name__path">
                {{ nodeContext(scope.row) }}
              </small>
            </button>
            <button
              v-else-if="isPreviewable(scope.row)"
              type="button"
              class="drive-node-name__button"
              :aria-label="nodeAriaLabel(scope.row)"
              @click="openNode(scope.row)"
            >
              <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
              <small v-if="(keyword || mode === 'recent') && nodeContext(scope.row)" class="drive-node-name__path">
                {{ nodeContext(scope.row) }}
              </small>
            </button>
            <span v-else class="drive-node-name__static">
              <span class="drive-node-name__text">{{ scope.row.nodeName }}</span>
              <small v-if="(keyword || mode === 'recent') && nodeContext(scope.row)" class="drive-node-name__path">
                {{ nodeContext(scope.row) }}
              </small>
            </span>
          </el-tooltip>
        </div>
      </template>
    </el-table-column>

    <el-table-column label="大小" width="120" align="right">
      <template slot-scope="scope">
        {{ scope.row.nodeType === 'FOLDER' ? '—' : formatBytes(scope.row.sizeBytes) }}
      </template>
    </el-table-column>

    <el-table-column label="修改时间" width="180">
      <template slot-scope="scope">{{ formatDriveDateTime(scope.row.updateTime || scope.row.createTime) }}</template>
    </el-table-column>

    <el-table-column label="操作" width="260" align="right" fixed="right">
      <template slot-scope="scope">
        <template v-if="scope.row.nodeType === 'FILE'">
          <el-button
            v-if="isPreviewable(scope.row)"
            type="text"
            :aria-label="'预览 ' + scope.row.nodeName"
            @click="$emit('preview', scope.row)"
          >预览</el-button>
          <el-button
            type="text"
            :aria-label="'下载 ' + scope.row.nodeName"
            @click="$emit('download', scope.row)"
          >下载</el-button>
          <el-button
            v-if="mode === 'recent'"
            type="text"
            :aria-label="'打开所在位置 ' + scope.row.nodeName"
            @click="$emit('open-location', scope.row)"
          >所在位置</el-button>
        </template>
        <el-button
          v-else
          type="text"
          :aria-label="'打开文件夹 ' + scope.row.nodeName"
          @click="$emit('open-folder', scope.row)"
        >打开</el-button>
        <el-dropdown
          v-if="canManage(scope.row)"
          class="drive-node-list__more"
          trigger="click"
          @command="handleAction($event, scope.row)"
        >
          <el-button type="text" :aria-label="'更多操作 ' + scope.row.nodeName">
            更多<i class="el-icon-arrow-down el-icon--right" aria-hidden="true" />
          </el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item v-if="scope.row.canWrite" command="rename">重命名</el-dropdown-item>
            <el-dropdown-item v-if="scope.row.canWrite" command="move">移动</el-dropdown-item>
            <el-dropdown-item v-if="scope.row.canDelete" command="trash" divided>移入回收站</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </template>
    </el-table-column>
  </el-table>
</template>

<script>
const { formatBytes, formatDriveDateTime, isPreviewable } = require('../driveState')

export default {
  name: 'DriveNodeList',
  props: {
    nodes: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    keyword: { type: String, default: '' },
    mode: { type: String, default: 'files' }
  },
  methods: {
    formatBytes,
    formatDriveDateTime,
    isPreviewable,
    openNode(node) {
      this.$emit(node.nodeType === 'FOLDER' ? 'open-folder' : 'preview', node)
    },
    canManage(row) {
      return Boolean(row.canWrite || row.canDelete)
    },
    handleAction(action, row) {
      if (action === 'rename') this.$emit('rename', row)
      if (action === 'move') this.$emit('move', row)
      if (action === 'trash') this.$emit('trash', row)
    },
    nodeAriaLabel(node) {
      return node.nodeType === 'FOLDER' ? `打开文件夹 ${node.nodeName}` : `预览文件 ${node.nodeName}`
    },
    nodeContext(node) {
      if (this.mode === 'recent') {
        return [node.spaceName, node.logicalPath].filter(Boolean).join(' · ')
      }
      return node.logicalPath || ''
    },
    iconClass(node) {
      if (node.nodeType === 'FOLDER') return 'el-icon-folder drive-node-name__icon--folder'
      if (isPreviewable(node)) return 'el-icon-document drive-node-name__icon--preview'
      return 'el-icon-document drive-node-name__icon--file'
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-node-list {
  width: 100%;
  border-radius: 12px;

  ::v-deep .el-table__header-wrapper th {
    height: 46px;
    border-bottom-color: #e8ecf3;
    background: #f8f9fc;
    color: #7d899b;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.03em;
  }

  ::v-deep .el-table__body td { height: 64px; border-bottom-color: #edf0f5; }
  ::v-deep .el-table__row:hover > td { background: #f8faff !important; }
  ::v-deep .el-table::before { background-color: transparent; }
  ::v-deep .el-button--text { color: #5264d7; font-weight: 500; }
}
.drive-node-name { min-width: 0; display: flex; align-items: center; gap: 11px; }
.drive-node-name__icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 18px;
}
.drive-node-name__icon--folder { background: #fff6df; color: #d9961e; }
.drive-node-name__icon--preview { background: #eaf4ff; color: #438bd1; }
.drive-node-name__icon--file { background: #eff2f6; color: #718096; }
.drive-node-name__button {
  min-width: 0;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 4px 5px;
  overflow: hidden;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #293a54;
  font-weight: 500;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover { color: #465bd5; background: #f1f3ff; }
  &:focus-visible { outline: 3px solid rgba(64, 158, 255, 0.3); }
}
.drive-node-name__static {
  min-width: 0;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 4px 5px;
  overflow: hidden;
  color: #293a54;
  font-weight: 500;
}
.drive-node-name__text { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drive-node-name__path { max-width: 100%; overflow: hidden; color: #929fb2; font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.drive-node-list__more { margin-left: 10px; }
</style>
