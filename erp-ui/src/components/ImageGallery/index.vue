<template>
  <div class="image-gallery" aria-label="图片">
    <template v-if="urls.length">
      <el-image v-for="(url, index) in urls" :key="url" :src="url" fit="contain"
        :alt="`第${index + 1}张图片`" :preview-src-list="urls" :initial-index="index" :z-index="5000">
        <span slot="error" class="image-gallery__error">图片暂不可用</span>
      </el-image>
    </template>
    <span v-else class="image-gallery__empty">暂无图片</span>
  </div>
</template>
<script>
const { imageUrls } = require("@/utils/imageGallery")
export default {
  name: "ImageGallery",
  props: { value: { type: [String, Array, Object], default: "" } },
  computed: { urls() { return imageUrls(this.value) } }
}
</script>
<style scoped>
.image-gallery { display: flex; flex-wrap: wrap; gap: 10px; padding: 8px 0; }
.image-gallery .el-image { width: 112px; height: 112px; border-radius: 8px; background: #f4f5f7; }
.image-gallery__error { display: grid; place-items: center; height: 100%; color: #606266; font-size: 12px; }
.image-gallery__empty { color: #909399; font-size: 14px; }
</style>
