<template>
  <section class="drive-trash" aria-label="回收站">
    <header class="drive-trash__header">
      <div>
        <strong>回收站</strong>
        <span>删除的项目保留 30 天，期间仍会占用云盘容量。</span>
      </div>
      <el-button
        type="danger"
        plain
        icon="el-icon-delete"
        :disabled="!items.length || loading || !canCleanup || hasPurgingItems"
        @click="$emit('empty')"
      >清空回收站</el-button>
    </header>

    <ul v-if="items.length" v-loading="loading" class="drive-trash__list">
      <li v-for="item in items" :key="item.nodeId" class="drive-trash-item">
        <span class="drive-trash-item__icon" aria-hidden="true">
          <i :class="item.nodeType === 'FOLDER' ? 'el-icon-folder' : 'el-icon-document'" />
        </span>
        <div class="drive-trash-item__copy">
          <strong :title="item.nodeName">{{ item.nodeName }}</strong>
          <span>{{ item.nodeType === 'FOLDER' ? '文件夹' : formatBytes(item.sizeBytes) }}</span>
        </div>

        <div class="drive-trash-item__state" :role="item.status === 'PURGE_FAILED' ? 'alert' : 'status'">
          <span v-if="item.status === 'PURGING'" class="is-purging">
            <i class="el-icon-loading" aria-hidden="true" /> 正在清理
          </span>
          <span v-else-if="item.status === 'PURGE_FAILED'" class="is-failed">
            <i class="el-icon-warning-outline" aria-hidden="true" /> 清理失败
          </span>
          <span v-else>等待处理</span>
        </div>

        <div class="drive-trash-item__actions">
          <template v-if="item.status === 'TRASHED'">
            <el-button
              type="text"
              :disabled="!canWrite"
              :aria-label="'恢复 ' + item.nodeName"
              @click="$emit('restore', item)"
            >恢复</el-button>
            <el-button
              type="text"
              class="is-danger"
              :disabled="!canCleanup"
              :aria-label="'彻底删除 ' + item.nodeName"
              @click="$emit('purge', item)"
            >彻底删除</el-button>
          </template>
          <template v-else-if="item.status === 'PURGE_FAILED'">
            <el-button
              type="text"
              class="is-danger"
              :disabled="!canCleanup"
              :aria-label="'重试清理 ' + item.nodeName"
              @click="$emit('retry-purge', item)"
            >重试清理</el-button>
          </template>
          <el-button v-else-if="item.status === 'PURGING'" type="text" disabled>处理中</el-button>
        </div>
      </li>
    </ul>

    <div v-else-if="!loading" class="drive-trash__empty" role="status">
      <i class="el-icon-delete" aria-hidden="true" />
      <strong>回收站为空</strong>
      <span>这里没有等待恢复或清理的项目。</span>
    </div>
  </section>
</template>

<script>
const { formatBytes } = require('../driveState')

export default {
  name: 'DriveTrashView',
  props: {
    items: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    canWrite: { type: Boolean, default: false },
    canCleanup: { type: Boolean, default: false }
  },
  computed: {
    hasPurgingItems() {
      return this.items.some(item => item.status === 'PURGING')
    }
  },
  methods: { formatBytes }
}
</script>

<style lang="scss" scoped>
.drive-trash { min-height: 410px; }
.drive-trash__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 8px;
  border-bottom: 1px solid #edf0f5;

  div { display: flex; flex-direction: column; gap: 4px; }
  strong { color: #2c3e50; font-size: 16px; }
  span { color: #8794a5; font-size: 12px; }
}
.drive-trash__list { min-height: 250px; margin: 0; padding: 0; list-style: none; }
.drive-trash-item { min-height: 68px; display: flex; align-items: center; gap: 13px; padding: 11px 8px; border-bottom: 1px solid #edf0f5; }
.drive-trash-item__icon { width: 32px; color: #7b8ba0; text-align: center; font-size: 22px; }
.drive-trash-item__copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.drive-trash-item__copy strong { overflow: hidden; color: #33475f; text-overflow: ellipsis; white-space: nowrap; }
.drive-trash-item__copy span { color: #929eac; font-size: 12px; }
.drive-trash-item__state { width: 112px; color: #8a97a8; font-size: 12px; }
.drive-trash-item__state .is-purging { color: #347dcc; }
.drive-trash-item__state .is-failed { color: #cc4c4c; }
.drive-trash-item__actions { width: 148px; text-align: right; }
.drive-trash-item__actions .is-danger { color: #d34a4a; }
.drive-trash__empty { min-height: 360px; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: #8a97a8; }
.drive-trash__empty i { margin-bottom: 5px; color: #c3cfdd; font-size: 44px; }
.drive-trash__empty strong { color: #536276; }
</style>
