<template>
  <div class="tree-sidebar" :class="{ collapsed: collapsed, resizing: isResizing, 'no-initial-transition': isLoadingFromStorage}" :style="{ width: sidebarWidth + 'px' }">
    <!-- 右侧拖动条 -->
    <div
      v-if="!collapsed"
      class="resize-handle"
      :class="{ active: isResizing }"
      role="separator"
      tabindex="0"
      aria-label="调整组织树宽度"
      aria-orientation="vertical"
      :aria-valuemin="minWidth"
      :aria-valuemax="maxWidth"
      :aria-valuenow="sidebarWidth"
      @mousedown="startResize"
      @touchstart="startResize"
      @keydown="handleResizeKeydown"
    />
    <div class="tree-header">
      <span class="tree-title" v-show="!collapsed">
        <i :class="titleIconClass" aria-hidden="true"></i> {{ title }}
      </span>
      <div class="tree-actions" v-show="!collapsed">
        <el-tooltip :content="isExpandedAll ? '收起全部' : '展开全部'" placement="right">
          <button
            type="button"
            class="tree-action-icon"
            :aria-label="isExpandedAll ? '收起全部组织节点' : '展开全部组织节点'"
            :aria-pressed="isExpandedAll ? 'true' : 'false'"
            @click="toggleExpandAll"
          >
            <i :class="isExpandedAll ? 'el-icon-arrow-down' : 'el-icon-arrow-up'" aria-hidden="true" />
          </button>
        </el-tooltip>
        <el-tooltip content="刷新" placement="right">
          <button type="button" class="tree-action-icon" aria-label="刷新组织树" @click="handleRefresh">
            <i class="el-icon-refresh" aria-hidden="true" />
          </button>
        </el-tooltip>
        <slot name="actions"></slot>
      </div>
    </div>
    
    <!-- 侧边栏展开/收起按钮 -->
    <div class="collapse-button-container">
      <el-tooltip :content="collapsed ? '展开' : '收起'" placement="right">
        <button
          type="button"
          class="collapse-button"
          :aria-label="collapsed ? '展开组织树' : '收起组织树'"
          :aria-expanded="collapsed ? 'false' : 'true'"
          :aria-controls="treeRegionId"
          @click="toggleCollapsed"
        >
          <i :class="collapsed ? 'el-icon-d-arrow-right' : 'el-icon-d-arrow-left'" aria-hidden="true" />
        </button>
      </el-tooltip>
    </div>

    <div class="tree-search" v-show="!collapsed" v-if="showSearch">
      <el-input v-model="searchKeyword" :aria-label="searchPlaceholder" :placeholder="searchPlaceholder" clearable size="small" prefix-icon="el-icon-search" @input="onSearch" />
      <div v-if="searchKeyword" class="tree-search-summary" role="status" aria-live="polite">匹配 {{ searchHitCount }} 项</div>
    </div>

    <div :id="treeRegionId" class="tree-wrap" v-show="!collapsed">
      <el-tree 
        ref="treeRef" 
        :data="treeData" 
        :props="treeProps" 
        :expand-on-click-node="expandOnClickNode"
        :filter-node-method="filterNodeMethod"
        :default-expand-all="defaultExpandAll"
        :default-expanded-keys="defaultExpandedKeys"
        :node-key="nodeKey"
        :check-strictly="checkStrictly"
        :show-checkbox="showCheckbox"
        @node-click="onNodeClick"
        @check="onCheck"
        @node-expand="onNodeExpand"
        @node-collapse="onNodeCollapse"
      >
        <span class="tree-node" slot-scope="{ node, data }">
          <slot name="node" :node="node" :data="data">
            <i :class="nodeIconClass(data)" class="node-icon" />
            <span class="node-label" :title="node.label">{{ node.label }}</span>
          </slot>
        </span>
      </el-tree>
    </div>
  </div>
</template>

<script>
export default {
  name: "TreeSidebar",
  props: {
    // 树形数据
    treeData: {
      type: Array,
      default: () => []
    },
    // 标题
    title: {
      type: String,
      default: '树形结构'
    },
    // 标题图标类名
    titleIconClass: {
      type: String,
      default: 'el-icon-office-building'
    },
    // 是否显示搜索框
    showSearch: {
      type: Boolean,
      default: true
    },
    // 搜索框占位符
    searchPlaceholder: {
      type: String,
      default: '请输入名称'
    },
    // 是否默认收起侧边栏
    defaultCollapsed: {
      type: Boolean,
      default: false
    },
    // 树配置项
    treeProps: {
      type: Object,
      default: () => ({
        children: "children",
        label: "label"
      })
    },
    // 节点唯一标识字段
    nodeKey: {
      type: String,
      default: 'id'
    },
    // 是否在点击节点时展开或收起
    expandOnClickNode: {
      type: Boolean,
      default: false
    },
    // 是否显示复选框
    showCheckbox: {
      type: Boolean,
      default: false
    },
    // 是否严格的遵循父子不互相关联
    checkStrictly: {
      type: Boolean,
      default: false
    },
    // 是否默认展开所有节点
    defaultExpandAll: {
      type: Boolean,
      default: false
    },
    // 默认展开的节点的key数组
    defaultExpandedKeys: {
      type: Array,
      default: () => []
    },
    // 默认宽度
    defaultWidth: {
      type: Number,
      default: 220
    },
    // 收起时的宽度
    collapsedWidth: {
      type: Number,
      default: 20
    },
    // 最小宽度
    minWidth: {
      type: Number,
      default: 180
    },
    // 最大宽度
    maxWidth: {
      type: Number,
      default: 400
    },
    // 本地存储的宽度key
    storageKey: {
      type: String,
      default: 'tree-sidebar-width'
    },
    // 是否启用本地存储宽度
    enableStorage: {
      type: Boolean,
      default: true
    },
    // 自定义过滤方法
    filterMethod: {
      type: Function,
      default: null
    }
  },
  data() {
    return {
      searchKeyword: "",
      collapsed: this.defaultCollapsed,
      sidebarWidth: this.defaultCollapsed ? this.collapsedWidth : this.defaultWidth,
      isResizing: false,
      startX: 0,
      startWidth: 0,
      saveWidthTimer: null,
      rafId: null,
      isLoadingFromStorage: false,
      expandedAll: this.defaultExpandAll,
      searchHitCount: 0
    };
  },
  computed: {
    treeRegionId() {
      return `tree-sidebar-region-${this._uid}`;
    },
    // 计算当前是否全部展开
    isExpandedAll: {
      get() {
        return this.expandedAll;
      },
      set(val) {
        this.expandedAll = val;
      }
    }
  },
  watch: {
    collapsed(newVal, oldVal) {
      if (newVal !== oldVal) {
        this.handleCollapseChange(newVal);
        this.$emit("collapsed-change", newVal);
      }
    },
    // 监听内部展开状态变化，触发实际树的展开/收起
    expandedAll(newVal) {
      this.$nextTick(() => {
        if (newVal) {
          this.expandAllNodes();
        } else {
          this.collapseAllNodes();
        }
      });
      this.$emit("expanded-all-change", newVal);
    },
    // 监听搜索关键词
    searchKeyword(val) {
      if (this.$refs.treeRef) {
        this.$refs.treeRef.filter(val);
        this.updateSearchHitCount(val);
        this.expandSearchMatchedNodes(val);
        this.$emit("search", val);
      }
    },
    defaultExpandedKeys: {
      handler() {
        this.$nextTick(() => {
          this.applyDefaultExpandedKeys();
        });
      },
      deep: true
    },
    treeData() {
      this.$nextTick(() => {
        if (this.expandedAll) {
          this.expandAllNodes();
        } else {
          this.applyDefaultExpandedKeys();
        }
        if (this.searchKeyword && this.$refs.treeRef) {
          this.$refs.treeRef.filter(this.searchKeyword);
          this.updateSearchHitCount(this.searchKeyword);
          this.expandSearchMatchedNodes(this.searchKeyword);
        }
      });
    }
  },
  mounted() {
    this.isLoadingFromStorage = true
    if (!this.collapsed && this.enableStorage) {
      const savedWidth = this.getSavedWidth();
      if (savedWidth !== null) {
        this.sidebarWidth = savedWidth;
      }
    }
    this.$nextTick(() => {
      this.isLoadingFromStorage = false
    })
    if (this.expandedAll) {
      this.$nextTick(() => {
        this.expandAllNodes();
      });
    }
  },
  beforeDestroy() {
    this.cleanup();
  },
  methods: {
    nodeIconClass(data) {
      const deptType = data && data.deptType ? String(data.deptType).toUpperCase() : "";
      if (deptType === "GROUP" || deptType === "COMPANY") {
        return "el-icon-folder";
      }
      if (deptType === "WAREHOUSE") {
        return "el-icon-box";
      }
      if (data && data.children && data.children.length) {
        return "el-icon-folder";
      }
      return "el-icon-document";
    },
    // 节点过滤方法
    filterNodeMethod(value, data, node) {
      if (this.filterMethod) {
        return this.filterMethod(value, data, node);
      }
      if (!value) return true;
      return data.label && data.label.indexOf(value) !== -1;
    },
    // 清理定时器和动画帧
    cleanup() {
      if (this.rafId) {
        cancelAnimationFrame(this.rafId);
        this.rafId = null;
      }
      if (this.saveWidthTimer) {
        clearTimeout(this.saveWidthTimer);
        this.saveWidthTimer = null;
      }
    },
    // 处理收起/展开状态变化
    handleCollapseChange(isCollapsed) {
      if (isCollapsed) {
        this.saveWidthToStorage();
        this.sidebarWidth = this.collapsedWidth;
      } else {
        const savedWidth = this.getSavedWidth();
        this.sidebarWidth = savedWidth !== null ? savedWidth : this.defaultWidth;
      }
    },
    // 获取保存的宽度
    getSavedWidth() {
      if (!this.enableStorage) {
        return null;
      }
      try {
        const savedWidth = localStorage.getItem(this.storageKey);
        if (savedWidth) {
          const width = parseInt(savedWidth, 10);
          if (!isNaN(width) && width >= this.minWidth && width <= this.maxWidth) {
            return width;
          }
        }
      } catch (error) {
        console.warn(`Failed to load sidebar width from storage with key ${this.storageKey}:`, error);
      }
      return null;
    },
    // 保存宽度到本地存储
    saveWidthToStorage() {
      if (this.collapsed || !this.enableStorage) return;
      try {
        localStorage.setItem(this.storageKey, this.sidebarWidth.toString());
      } catch (error) {
        console.warn(`Failed to save sidebar width to storage with key ${this.storageKey}:`, error);
      }
    },
    // 切换侧边栏收起/展开状态
    toggleCollapsed() {
      this.collapsed = !this.collapsed;
    },
    // 切换展开/折叠所有节点
    toggleExpandAll() {
      this.isExpandedAll = !this.isExpandedAll;
    },
    // 展开所有节点
    expandAllNodes() {
      if (!this.$refs.treeRef) return;
      const allNodes = this.getAllNodes(this.$refs.treeRef.root);
      allNodes.forEach(node => {
        if (node.expanded !== undefined && !node.expanded) {
          node.expanded = true;
        }
      });
    },
    // 获取所有节点
    getAllNodes(rootNode) {
      const nodes = [];
      const traverse = (node) => {
        if (!node) return;
        nodes.push(node);
        if (node.childNodes && node.childNodes.length) {
          node.childNodes.forEach(child => traverse(child));
        }
      };
      traverse(rootNode);
      return nodes;
    },
    // 收起所有节点
    collapseAllNodes() {
      if (!this.$refs.treeRef) return;
      const allNodes = this.getAllNodes(this.$refs.treeRef.root);
      allNodes.forEach(node => {
        if (node.expanded !== undefined && node.expanded) {
          node.expanded = false;
        }
      });
    },
    applyDefaultExpandedKeys() {
      if (!this.$refs.treeRef || this.expandedAll || !this.defaultExpandedKeys.length) return;
      const expandedKeys = new Set(this.defaultExpandedKeys);
      const allNodes = this.getAllNodes(this.$refs.treeRef.root);
      allNodes.forEach(node => {
        if (node.level > 0 && expandedKeys.has(node.key) && node.expanded !== undefined) {
          node.expanded = true;
        }
      });
    },
    updateSearchHitCount(value) {
      if (!this.$refs.treeRef || !value) {
        this.searchHitCount = 0;
        return;
      }
      const allNodes = this.getAllNodes(this.$refs.treeRef.root);
      this.searchHitCount = allNodes.filter(node => {
        return node.level > 0 && this.filterNodeMethod(value, node.data, node);
      }).length;
    },
    expandSearchMatchedNodes(value) {
      if (!this.$refs.treeRef || !value) return;
      const allNodes = this.getAllNodes(this.$refs.treeRef.root);
      allNodes.forEach(node => {
        if (node.level <= 0 || !this.filterNodeMethod(value, node.data, node)) {
          return;
        }
        let current = node;
        while (current && current.level > 0) {
          if (current.expanded !== undefined) {
            current.expanded = true;
          }
          current = current.parent;
        }
      });
    },
    // 处理刷新操作
    handleRefresh() {
      this.$emit("refresh");
    },
    // 节点点击事件
    onNodeClick(data, node, e) {
      this.$emit("node-click", data, node, e);
    },
    // 复选框选中事件
    onCheck(data, checkedInfo) {
      this.$emit("check", data, checkedInfo);
    },
    // 节点展开事件
    onNodeExpand(data, node, e) {
      this.$emit("node-expand", data, node, e);
    },
    // 节点折叠事件
    onNodeCollapse(data, node, e) {
      this.$emit("node-collapse", data, node, e);
    },
    // 搜索处理
    onSearch() {
      // 搜索逻辑已在 watch 中处理
    },
    // 设置当前选中的节点
    setCurrentKey(key) {
      if (this.$refs.treeRef) {
        this.$refs.treeRef.setCurrentKey(key);
      }
    },
    // 获取当前选中的节点
    getCurrentNode() {
      if (this.$refs.treeRef) {
        return this.$refs.treeRef.getCurrentNode();
      }
      return null;
    },
    // 获取当前选中的节点的key
    getCurrentKey() {
      if (this.$refs.treeRef) {
        return this.$refs.treeRef.getCurrentKey();
      }
      return null;
    },
    // 设置选中的节点keys（复选框）
    setCheckedKeys(keys) {
      if (this.$refs.treeRef && this.showCheckbox) {
        this.$refs.treeRef.setCheckedKeys(keys);
      }
    },
    // 获取选中的节点keys（复选框）
    getCheckedKeys() {
      if (this.$refs.treeRef && this.showCheckbox) {
        return this.$refs.treeRef.getCheckedKeys();
      }
      return [];
    },
    // 获取选中的节点（复选框）
    getCheckedNodes() {
      if (this.$refs.treeRef && this.showCheckbox) {
        return this.$refs.treeRef.getCheckedNodes();
      }
      return [];
    },
    // 清空搜索
    clearSearch() {
      this.searchKeyword = "";
      if (this.$refs.treeRef) {
        this.$refs.treeRef.filter("");
      }
    },
    // 过滤树
    filter(value) {
      this.searchKeyword = value;
    },
    // 开始调整大小
    startResize(e) {
      e.preventDefault();
      e.stopPropagation();
      this.isResizing = true;
      this.startX = e.type === 'mousedown' ? e.clientX : e.touches[0].clientX;
      this.startWidth = this.sidebarWidth;
      
      if (e.type === 'mousedown') {
        document.addEventListener('mousemove', this.handleResizeMove);
        document.addEventListener('mouseup', this.stopResize);
      } else {
        document.addEventListener('touchmove', this.handleResizeMove, { passive: false });
        document.addEventListener('touchend', this.stopResize);
      }
      this.disableUserSelect();
    },
    // 处理调整大小移动
    handleResizeMove(e) {
      if (!this.isResizing) return;
      if (this.rafId) {
        cancelAnimationFrame(this.rafId);
      }
      this.rafId = requestAnimationFrame(() => {
        e.preventDefault();
        e.stopPropagation();
        const clientX = e.type === 'mousemove' ? e.clientX : e.touches[0].clientX;
        const deltaX = clientX - this.startX;
        const newWidth = this.startWidth + deltaX;
        const clampedWidth = Math.max(this.minWidth, Math.min(this.maxWidth, newWidth));
        if (Math.abs(clampedWidth - this.sidebarWidth) >= 1) {
          this.sidebarWidth = clampedWidth;
        }
      });
    },
    handleResizeKeydown(e) {
      const key = e.key;
      let nextWidth = this.sidebarWidth;
      if (key === "ArrowLeft") {
        nextWidth -= 16;
      } else if (key === "ArrowRight") {
        nextWidth += 16;
      } else if (key === "Home") {
        nextWidth = this.minWidth;
      } else if (key === "End") {
        nextWidth = this.maxWidth;
      } else {
        return;
      }
      e.preventDefault();
      this.sidebarWidth = Math.max(this.minWidth, Math.min(this.maxWidth, nextWidth));
      this.saveWidthToStorage();
      this.$emit("resize", this.sidebarWidth);
    },
    // 停止调整大小
    stopResize() {
      if (!this.isResizing) return;
      this.isResizing = false;
      if (this.rafId) {
        cancelAnimationFrame(this.rafId);
        this.rafId = null;
      }
      this.startX = 0;
      this.startWidth = 0;
      document.removeEventListener('mousemove', this.handleResizeMove);
      document.removeEventListener('mouseup', this.stopResize);
      document.removeEventListener('touchmove', this.handleResizeMove);
      document.removeEventListener('touchend', this.stopResize);
      this.enableUserSelect();
      this.saveWidthToStorage();
    },
    // 禁用用户选择
    disableUserSelect() {
      document.body.style.userSelect = 'none';
      document.body.style.webkitUserSelect = 'none';
      document.body.style.mozUserSelect = 'none';
      document.body.style.msUserSelect = 'none';
    },
    // 启用用户选择
    enableUserSelect() {
      document.body.style.userSelect = '';
      document.body.style.webkitUserSelect = '';
      document.body.style.mozUserSelect = '';
      document.body.style.msUserSelect = '';
    },
    // 重置宽度到默认值
    resetWidth() {
      this.sidebarWidth = this.defaultWidth;
      this.saveWidthToStorage();
    },
    // 获取当前宽度
    getCurrentWidth() {
      return this.sidebarWidth;
    },
    // 设置宽度
    setWidth(width) {
      if (typeof width === 'number' && width >= this.minWidth && width <= this.maxWidth) {
        this.sidebarWidth = width;
        if (!this.collapsed) {
          this.saveWidthToStorage();
        }
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.tree-sidebar {
  flex-shrink: 0;
  width: 220px;
  height: 100%;
  min-height: 0;
  background: var(--erp-surface, #fff);
  border-right: 1px solid var(--erp-border, #dde2de);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  transition: none;
  
  &.collapsed {
    width: 42px;
  }
  
  &.resizing {
    transition: none;
    
    * {
      pointer-events: none !important;
    }
  }
  
  &.no-initial-transition {
    transition: none;
  }
}

.resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 20;
  background: transparent;
  
  &:hover {
    background: rgba(11, 107, 83, 0.2);
  }

  &:focus-visible {
    width: 8px;
    outline: 2px solid var(--erp-primary, #0b6b53);
    outline-offset: -2px;
    background: rgba(11, 107, 83, 0.22);
  }

  &:focus-visible {
    width: 8px;
    outline: 2px solid #4f46e5;
    outline-offset: -2px;
    background: rgba(79, 70, 229, 0.22);
  }
  
  &.active {
    background: rgba(11, 107, 83, 0.4);
  }
}

.collapse-button-container {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 20px;
  background: var(--erp-surface, #fff);
  border-radius: 0 4px 4px 0;
  box-shadow: 0 1px 4px rgba(23, 33, 29, 0.12);
  transition: none;
  
  .tree-sidebar.collapsed & {
    right: 0;
    background: var(--erp-surface-muted, #f8f8f5);
    border-radius: 0 4px 4px 0;
  }
  
  .tree-sidebar.resizing & {
    pointer-events: none;
  }
}

.collapse-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 30px;
  padding: 0;
  border: 0;
  font-size: 14px;
  color: var(--erp-text-muted, #8a948f);
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  transition: none;
  
  &:hover {
    color: var(--erp-primary, #0b6b53);
    background: var(--erp-primary-soft, #e7f2ed);
  }
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  height: 40px;
  border-bottom: 1px solid var(--erp-border, #dde2de);
  background: var(--erp-surface-muted, #f8f8f5);
  flex-shrink: 0;

  .tree-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--erp-text, #17211d);
    white-space: nowrap;
    overflow: hidden;
    display: flex;
    align-items: center;
    gap: 5px;

    i {
      color: var(--erp-primary, #0b6b53);
      font-size: 14px;
    }
  }

  .tree-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }
}

.tree-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  font-size: 14px;
  color: var(--erp-text-muted, #8a948f);
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  transition: none;

  &:hover {
    color: var(--erp-primary, #0b6b53);
    background: var(--erp-primary-soft, #e7f2ed);
  }
}

.tree-search {
  padding: 10px 10px 4px;
  flex-shrink: 0;
}

.tree-search-summary {
  margin-top: 5px;
  color: var(--erp-text-muted, #8a948f);
  font-size: 12px;
  line-height: 18px;
}

.tree-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 6px 12px;
  
  .tree-sidebar.resizing & {
    overflow: hidden;
  }

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #cbd3ce;
    border-radius: 4px;
    
    &:hover {
      background: #aeb9b3;
    }
  }

  ::v-deep .el-tree-node__content {
    height: 32px;
    border-radius: 4px;
    margin-bottom: 1px;

    &:hover {
      background: #f3f8f5;
    }
  }

  ::v-deep .el-tree-node.is-current > .el-tree-node__content {
    background: var(--erp-primary-soft, #e7f2ed);
    color: var(--erp-primary, #0b6b53);
    font-weight: 600;

    .node-icon {
      color: var(--erp-primary, #0b6b53) !important;
    }
  }
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  overflow: hidden;

  .node-icon {
    font-size: 14px;
    color: #f5a623;
    flex-shrink: 0;
  }

  .node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

::v-deep .el-icon-document.node-icon {
  color: var(--erp-text-muted, #8a948f) !important;
}
</style>
