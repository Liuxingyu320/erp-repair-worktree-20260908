<template>
  <ul v-if="visibleItems.length" class="upload-progress-queue" aria-label="文件上传进度">
    <li v-for="item in visibleItems" :key="item.uid">
      <div class="upload-progress-heading"><span :title="item.name">{{ item.name }}</span><span role="status">{{ statusText(item) }}</span></div>
      <el-progress v-if="item.status === 'uploading'" :percentage="item.progress" :stroke-width="6" />
      <div class="upload-progress-actions">
        <el-button v-if="item.status === 'uploading'" size="mini" @click="$emit('cancel', item)">取消上传</el-button>
        <el-button v-else size="mini" @click="$emit('retry', item)">重试</el-button>
        <el-button v-if="item.status !== 'uploading'" size="mini" type="text" @click="$emit('dismiss', item)">移除</el-button>
      </div>
    </li>
  </ul>
</template>

<script>
export default {
  name: 'UploadQueue',
  props: { items: { type: Array, default: () => [] } },
  computed: { visibleItems() { return this.items.filter(item => !['succeeded', 'committed', 'removed', 'retried'].includes(item.status)) } },
  methods: {
    statusText(item) {
      if (item.status === 'canceled') return '已取消'
      if (item.status === 'failed') return item.message || '上传未完成，请重试或移除'
      return item.progress ? '上传中 ' + item.progress + '%' : '准备上传'
    }
  }
}
</script>

<style scoped>
.upload-progress-queue { list-style: none; padding: 0; margin: 8px 0; }
.upload-progress-queue li { padding: 8px; border: 1px solid #dcdfe6; border-radius: 4px; margin-bottom: 6px; }
.upload-progress-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; margin-bottom: 6px; }
.upload-progress-heading > span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-progress-actions { text-align: right; margin-top: 4px; }
</style>
