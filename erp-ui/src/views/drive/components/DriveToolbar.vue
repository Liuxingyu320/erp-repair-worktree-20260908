<template>
  <section class="drive-toolbar" aria-label="文件操作栏">
    <div class="drive-toolbar__breadcrumbs" aria-label="当前位置">
      <template v-if="activeView === 'files'">
        <button type="button" class="drive-breadcrumb" @click="$emit('navigate', 0)">
          <span class="drive-breadcrumb__home" aria-hidden="true"><i class="el-icon-house" /></span>
          根目录
        </button>
        <template v-for="item in breadcrumbs">
          <span :key="'separator-' + item.nodeId" class="drive-breadcrumb__separator" aria-hidden="true"><i class="el-icon-arrow-right" /></span>
          <button
            :key="item.nodeId"
            type="button"
            class="drive-breadcrumb"
            :title="item.nodeName"
            @click="$emit('navigate', item.nodeId)"
          >
            {{ item.nodeName }}
          </button>
        </template>
      </template>
      <div v-else class="drive-toolbar__view-title">
        <span class="drive-toolbar__view-icon" aria-hidden="true"><i :class="activeViewMeta.icon" /></span>
        <span>
          <strong>{{ activeViewMeta.label }}</strong>
          <small>{{ activeViewMeta.description }}</small>
        </span>
      </div>
    </div>

    <div class="drive-toolbar__controls">
      <el-input
        v-if="activeView === 'files'"
        v-model="searchValue"
        class="drive-toolbar__search"
        clearable
        aria-label="搜索当前空间的文件和文件夹"
        placeholder="搜索当前空间的文件和文件夹"
        prefix-icon="el-icon-search"
        @input="emitSearch"
        @clear="emitSearch"
        @keyup.enter.native="emitSearch"
      />

      <el-select
        v-if="activeView === 'files'"
        :value="sortField"
        class="drive-toolbar__sort"
        aria-label="排序字段"
        @change="changeSortField"
      >
        <el-option label="最近更新" value="updated" />
        <el-option label="名称" value="name" />
        <el-option label="大小" value="size" />
      </el-select>
      <el-button
        v-if="activeView === 'files'"
        class="drive-toolbar__direction"
        :icon="sortDirection === 'asc' ? 'el-icon-sort-up' : 'el-icon-sort-down'"
        :aria-label="sortDirection === 'asc' ? '切换为降序' : '切换为升序'"
        @click="toggleDirection"
      />

      <template v-if="activeView === 'files' && canWrite">
        <el-button icon="el-icon-folder-add" @click="$emit('create-folder')">新建文件夹</el-button>
        <el-button type="primary" icon="el-icon-upload2" @click="openFilePicker">上传文件</el-button>
        <input
          ref="fileInput"
          class="drive-toolbar__file-input"
          type="file"
          multiple
          accept=".doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.txt,.csv,.jpg,.jpeg,.png,.gif,.webp,.heic,.heif,.zip,.rar,.7z"
          hidden
          tabindex="-1"
          aria-hidden="true"
          @change="selectFiles"
        >
      </template>
    </div>
  </section>
</template>

<script>
export default {
  name: 'DriveToolbar',
  props: {
    breadcrumbs: { type: Array, default: () => [] },
    keyword: { type: String, default: '' },
    sortField: { type: String, default: 'updated' },
    sortDirection: { type: String, default: 'desc' },
    canWrite: { type: Boolean, default: false },
    activeView: { type: String, default: 'files' }
  },
  computed: {
    activeViewMeta() {
      if (this.activeView === 'recent') {
        return { label: '最近使用', description: '所有可访问空间', icon: 'el-icon-time' }
      }
      if (this.activeView === 'trash') {
        return { label: '回收站', description: '已删除的文件与文件夹', icon: 'el-icon-delete' }
      }
      return { label: '根目录', description: '', icon: 'el-icon-house' }
    }
  },
  data() {
    return { searchValue: this.keyword }
  },
  watch: {
    keyword(value) {
      if (value !== this.searchValue) this.searchValue = value
    }
  },
  methods: {
    emitSearch() {
      this.$emit('search', this.searchValue)
    },
    changeSortField(value) {
      this.$emit('sort-change', { field: value, direction: this.sortDirection })
    },
    toggleDirection() {
      this.$emit('sort-change', {
        field: this.sortField,
        direction: this.sortDirection === 'asc' ? 'desc' : 'asc'
      })
    },
    openFilePicker() {
      if (this.$refs.fileInput) this.$refs.fileInput.click()
    },
    selectFiles(event) {
      const files = Array.from((event.target && event.target.files) || [])
      if (files.length) this.$emit('select-files', files)
      if (event.target) event.target.value = ''
    }
  }
}
</script>

<style lang="scss" scoped>
.drive-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 76px;
  padding: 15px 18px;
  border-bottom: 1px solid var(--erp-border, #dde2de);
  background: var(--erp-surface-raised, #fbfcfa);
}

.drive-toolbar__breadcrumbs {
  min-width: 180px;
  display: flex;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
}

.drive-breadcrumb {
  max-width: 170px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 7px;
  overflow: hidden;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #44536a;
  font-size: 13px;
  font-weight: 600;
  font: inherit;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;

  &:hover { color: var(--erp-primary, #0b6b53); background: var(--erp-primary-soft, #e7f2ed); }
  &:focus-visible { outline: 3px solid rgba(11, 107, 83, 0.22); }
}

.drive-breadcrumb__home,
.drive-toolbar__view-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--erp-primary, #0b6b53);
  font-size: 15px;
}

.drive-breadcrumb__separator { color: #c2cad7; font-size: 11px; }
.drive-toolbar__view-title {
  display: flex;
  align-items: center;
  gap: 10px;

  > span:last-child { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
  strong { color: #33435d; font-size: 13px; }
  small { color: #8c98aa; font-size: 10px; }
}
.drive-toolbar__controls { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.drive-toolbar__search { width: 244px; }
.drive-toolbar__sort { width: 118px; }
.drive-toolbar__direction { width: 40px; padding-right: 0; padding-left: 0; }
.drive-toolbar__file-input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }

::v-deep .drive-toolbar__search .el-input__inner,
::v-deep .drive-toolbar__sort .el-input__inner {
  height: 40px;
  border-color: #dfe5ef;
  border-radius: 11px;
  background: #f9fafc;
  color: #3f4e66;
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
}

::v-deep .drive-toolbar__search .el-input__inner:focus,
::v-deep .drive-toolbar__sort .el-input__inner:focus {
  border-color: var(--erp-primary, #0b6b53);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.18);
}

::v-deep .drive-toolbar__controls > .el-button {
  height: 40px;
  border-color: #dfe5ef;
  border-radius: 11px;
  color: #4b5a70;
  font-weight: 500;
}

::v-deep .drive-toolbar__controls > .el-button--primary {
  border-color: var(--erp-primary, #0b6b53);
  background: var(--erp-primary, #0b6b53);
  color: #fff;
  box-shadow: 0 7px 16px rgba(11, 107, 83, 0.18);
}

@media (max-width: 1280px) {
  .drive-toolbar__search { width: 210px; }
  ::v-deep .drive-toolbar__controls > .el-button { padding-right: 13px; padding-left: 13px; }
}

@media (max-width: 1120px) {
  .drive-toolbar { align-items: flex-start; flex-direction: column; }
  .drive-toolbar__controls { width: 100%; justify-content: flex-start; flex-wrap: wrap; }
}
</style>
