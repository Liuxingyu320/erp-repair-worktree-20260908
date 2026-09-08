<template>
  <el-dialog
    class="drive-preview-dialog"
    :visible="visible"
    :title="node ? node.nodeName : '文件预览'"
    width="min(920px, 88vw)"
    append-to-body
    destroy-on-close
    @close="$emit('close')"
  >
    <div v-loading="loading" class="drive-preview-dialog__body">
      <div v-if="error" class="drive-preview-dialog__message is-error" role="alert">
        <i class="el-icon-warning-outline" aria-hidden="true" />
        <span>{{ error }}</span>
      </div>

      <div v-else-if="node && (!node.canPreview || previewKind === 'unsupported')" class="drive-preview-dialog__message">
        <i class="el-icon-document" aria-hidden="true" />
        <strong>此文件暂不支持在线预览</strong>
        <span>可以下载后使用本地应用查看。</span>
      </div>

      <img
        v-else-if="previewKind === 'image' && objectUrl"
        class="drive-preview-dialog__image"
        :src="objectUrl"
        :alt="node ? node.nodeName : '图片预览'"
      >
      <iframe
        v-else-if="previewKind === 'pdf' && objectUrl"
        class="drive-preview-dialog__frame"
        :src="objectUrl"
        title="文件预览"
      />
      <template v-else-if="previewKind === 'text'">
        <pre>{{ textContent }}</pre>
      </template>

      <div v-else-if="!loading" class="drive-preview-dialog__message">
        <i class="el-icon-loading" aria-hidden="true" />
        <span>正在准备预览</span>
      </div>
    </div>

    <template slot="footer">
      <el-button @click="$emit('close')">关闭</el-button>
      <el-button
        v-if="node"
        type="primary"
        icon="el-icon-download"
        @click="$emit('download', node)"
      >下载文件</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'DrivePreviewDialog',
  props: {
    visible: { type: Boolean, default: false },
    node: { type: Object, default: null },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
    objectUrl: { type: String, default: '' },
    textContent: { type: String, default: '' }
  },
  computed: {
    previewKind() {
      const extension = String(this.node && this.node.extension || '').toLowerCase()
      if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(extension)) return 'image'
      if (extension === 'pdf') return 'pdf'
      if (['txt', 'csv'].includes(extension)) return 'text'
      return 'unsupported'
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-preview-dialog__body {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  border-radius: 10px;
  background: #f5f7fa;
}
.drive-preview-dialog__message {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  padding: 36px;
  color: #7e8b9c;
  text-align: center;

  i { color: #99a8ba; font-size: 42px; }
  strong { color: #3d4f64; font-size: 16px; }
  &.is-error { color: #c84c4c; }
}
.drive-preview-dialog__image { max-width: 100%; max-height: 68vh; object-fit: contain; }
.drive-preview-dialog__frame { width: 100%; height: 68vh; border: 0; background: #fff; }
pre {
  width: 100%;
  min-height: 420px;
  margin: 0;
  padding: 22px;
  overflow: auto;
  color: #2f4054;
  font: 13px/1.7 Consolas, Monaco, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
