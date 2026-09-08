<template>
  <aside v-if="items.length" class="drive-upload-queue" aria-label="文件上传队列">
    <header class="drive-upload-queue__header">
      <div>
        <span>上传任务</span>
        <strong>{{ activeCount ? `${activeCount} 个进行中` : `${items.length} 个任务` }}</strong>
      </div>
      <i class="el-icon-upload2" aria-hidden="true" />
    </header>

    <ul class="drive-upload-queue__list" aria-live="polite">
      <li v-for="item in items" :key="item.id" class="drive-upload-item" role="status">
        <div class="drive-upload-item__main">
          <div class="drive-upload-item__title" :title="item.name">{{ item.name }}</div>
          <div class="drive-upload-item__meta">
            <span>{{ formatBytes(item.size) }}</span>
            <span :title="item.targetPath">上传到 {{ item.targetPath || '根目录' }}</span>
          </div>
          <el-progress
            v-if="item.status === 'uploading'"
            :percentage="item.progress"
            :stroke-width="5"
            :show-text="false"
          />
          <div v-if="item.error" class="drive-upload-item__error" role="alert">{{ item.error }}</div>
        </div>

        <div class="drive-upload-item__status">
          <span :class="statusClass(item.status)">{{ statusLabel(item.status, item.progress) }}</span>
          <el-button
            v-if="item.status === 'uploading'"
            type="text"
            :aria-label="'取消上传 ' + item.name"
            @click="$emit('cancel', item.id)"
          >取消</el-button>
          <el-button
            v-if="item.status === 'failed'"
            type="text"
            :aria-label="'重试上传 ' + item.name"
            @click="$emit('retry', item.id)"
          >重试</el-button>
          <el-button
            v-if="item.status !== 'uploading'"
            type="text"
            :aria-label="'移除上传任务 ' + item.name"
            @click="$emit('remove', item.id)"
          >移除</el-button>
        </div>
      </li>
    </ul>
  </aside>
</template>

<script>
const { formatBytes } = require('../driveState')

export default {
  name: 'DriveUploadQueue',
  props: {
    items: { type: Array, default: () => [] }
  },
  computed: {
    activeCount() {
      return this.items.filter(item => item.status === 'uploading').length
    }
  },
  methods: {
    formatBytes,
    statusLabel(status, progress) {
      return {
        queued: '等待中',
        uploading: `上传中 ${progress}%`,
        done: '已完成',
        failed: '上传失败',
        canceled: '已取消'
      }[status] || '等待中'
    },
    statusClass(status) {
      return `drive-upload-item__status-label is-${status}`
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-upload-queue {
  position: fixed;
  right: 28px;
  bottom: 24px;
  z-index: 1800;
  width: 390px;
  max-height: min(460px, calc(100vh - 120px));
  overflow: hidden;
  border: 1px solid #dfe6ef;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 52px rgba(31, 45, 61, 0.2);
}
.drive-upload-queue__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 18px;
  background: #f6f9fd;
  color: #526173;

  div { display: flex; flex-direction: column; gap: 3px; }
  span { color: #8a97a8; font-size: 12px; }
  strong { color: #26384e; }
  i { color: #3f82d5; font-size: 24px; }
}
.drive-upload-queue__list { max-height: 370px; margin: 0; padding: 0; overflow-y: auto; list-style: none; }
.drive-upload-item { display: flex; gap: 12px; padding: 13px 16px; border-top: 1px solid #edf0f5; }
.drive-upload-item__main { min-width: 0; flex: 1; }
.drive-upload-item__title { overflow: hidden; color: #26384e; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.drive-upload-item__meta { display: flex; gap: 8px; margin: 5px 0 8px; overflow: hidden; color: #8a97a8; font-size: 11px; }
.drive-upload-item__meta span:last-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drive-upload-item__error { margin-top: 5px; color: #d34a4a; font-size: 12px; }
.drive-upload-item__status { flex: 0 0 76px; display: flex; align-items: flex-end; flex-direction: column; gap: 2px; }
.drive-upload-item__status-label { color: #8492a6; font-size: 12px; }
.drive-upload-item__status-label.is-uploading { color: #3277cc; }
.drive-upload-item__status-label.is-done { color: #3a9b69; }
.drive-upload-item__status-label.is-failed { color: #d34a4a; }
.drive-upload-item__status-label.is-canceled { color: #7f8c9d; }

@media (max-width: 900px) {
  .drive-upload-queue { right: 16px; bottom: 16px; width: min(390px, calc(100vw - 32px)); }
}
</style>
