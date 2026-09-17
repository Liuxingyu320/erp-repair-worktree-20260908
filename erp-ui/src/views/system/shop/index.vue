<template>
  <div class="app-container system-management-page shop-config-page">
    <system-page-header
      title="用户组织授权"
      description="配置用户可管理的公司、组织、门店与仓库，保存前预览生效范围。"
      icon="el-icon-connection"
      tip="授权步骤：选择用户 → 勾选公司/组织/门店/仓库 → 预览并保存授权"
    />

    <div v-show="showSearch" class="search-card shop-search-card">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="queryParams.userName" placeholder="请输入用户名称" clearable style="width: 240px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="岗位" prop="postId">
          <el-select v-model="queryParams.postId" placeholder="请选择岗位" clearable filterable style="width: 180px" @change="handleQuery">
            <el-option v-for="post in postOptions" :key="post.postId" :label="post.postName" :value="post.postId" :disabled="post.status === '1'" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="用户状态" clearable style="width: 180px">
            <el-option v-for="dict in dict.type.sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <resizable-split-pane storage-key="system-user-shop-split-v2" :default-right-width="360" :min-right-width="320">
      <div slot="main">
        <div class="table-card user-card">
          <div class="panel-title user-list-title">用户列表</div>
          <el-table ref="userTable" v-loading="loading" :data="userList" highlight-current-row @current-change="handleCurrentUserChange">
            <el-table-column label="用户编号" prop="userId" width="90" align="center" />
            <el-table-column label="用户名称" prop="userName" min-width="110" align="center" :show-overflow-tooltip="true" />
            <el-table-column label="用户昵称" prop="nickName" min-width="110" align="center" :show-overflow-tooltip="true" />
            <el-table-column label="部门" prop="deptName" min-width="130" align="center" :show-overflow-tooltip="true" />
            <el-table-column label="岗位" prop="postNames" min-width="130" align="center" :show-overflow-tooltip="true" />
            <el-table-column label="组织范围" min-width="140" align="center" :show-overflow-tooltip="true">
              <template slot-scope="scope">
                <span>{{ getUserShopScopeLabel(scope.row) }}</span>
                <el-button
                  v-if="shopScopeLoadFailed"
                  type="text"
                  size="mini"
                  @click.stop="loadUserShopScopes(userList)"
                >重试</el-button>
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" width="78" align="center">
              <template slot-scope="scope">
                <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="86" align="center" fixed="right">
              <template slot-scope="scope">
                <el-button size="mini" type="text" icon="el-icon-view" @click.stop="handleViewData(scope.row)" v-hasPermi="['system:user:query']">用户详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="handlePagination" />
        </div>
      </div>

      <div slot="side">
        <div class="content-card shop-tree-card">
          <div class="shop-tree-header">
            <div>
              <div class="panel-title">组织授权</div>
              <div class="panel-subtitle">{{ currentUser ? "勾选公司/组织/门店/仓库，授权后自动包含下级业务组织" : "请选择左侧用户" }}</div>
              <div v-if="currentUser && preservedShopCount > 0" class="scope-preserved-tip">另有 {{ preservedShopCount }} 个当前账号不可编辑的授权将保留</div>
            </div>
            <el-button v-if="canSaveShopScope" type="primary" size="mini" icon="el-icon-view" :loading="previewLoading" :disabled="!currentUser || saveLoading" @click="submitShopScope">预览并保存</el-button>
          </div>
          <div v-if="!currentUser" class="empty-authorization-state">请先从左侧用户列表选择一个用户</div>
          <template v-else>
            <div class="selected-user-card">
              <div>
                <strong>{{ currentUser.nickName || currentUser.userName }}</strong>
                <span>{{ currentUser.userName }} · {{ currentUser.deptName || "未归属部门" }}</span>
              </div>
              <div class="authorization-counts">
                <el-tag size="mini">直接 {{ directAuthorizationCount }}</el-tag>
                <el-tag size="mini" type="success">继承 {{ inheritedAuthorizationCount }}</el-tag>
                <el-tag v-if="preservedShopCount" size="mini" type="warning">保留 {{ preservedShopCount }}</el-tag>
              </div>
            </div>
            <el-input v-model="filterText" size="small" clearable prefix-icon="el-icon-search" placeholder="请输入公司、组织、门店或仓库名称" class="tree-filter" />
            <div v-if="managementUxV2Enabled" class="tree-actions" aria-label="组织树快捷操作">
              <el-checkbox v-model="showSelectedOnly" @change="refreshTreeFilter">仅显示已选</el-checkbox>
              <el-button type="text" size="mini" @click="expandToMatches">展开到匹配项</el-button>
              <el-button type="text" size="mini" :disabled="!getCheckedShopIds().length" @click="clearEditableSelection">清空本次可编辑项</el-button>
            </div>
            <div class="scope-legend">
              <el-tag size="mini" type="primary">直接授权</el-tag>
              <el-tag size="mini" type="success">上级继承</el-tag>
              <el-tag v-if="preservedShopCount" size="mini" type="warning">范围外保留 {{ preservedShopCount }}</el-tag>
            </div>
            <el-tree
              ref="shopTree"
              v-loading="shopLoading"
              class="tree-border"
              :data="shopOptions"
              show-checkbox
              :check-strictly="true"
              node-key="deptId"
              default-expand-all
              :props="defaultProps"
              :filter-node-method="filterNode"
              empty-text="暂无组织，请先在部门管理中维护组织类型"
              @check="handleTreeCheck"
            >
              <span slot-scope="{ data }" class="shop-tree-node">
                <span class="shop-tree-node__name">{{ data.deptName }}</span>
                <el-tag v-if="nodeScopeKind(data) === 'direct'" size="mini" type="primary">直接</el-tag>
                <el-tag v-else-if="nodeScopeKind(data) === 'inherited'" size="mini" type="success">继承</el-tag>
              </span>
            </el-tree>
          </template>
        </div>
      </div>
    </resizable-split-pane>

    <el-dialog title="确认组织授权变更" :visible.sync="scopePreviewVisible" width="620px" append-to-body :close-on-click-modal="false">
      <div v-if="scopePreview" class="scope-preview">
        <div class="scope-preview__identity">
          <strong>{{ currentUser ? (currentUser.nickName || currentUser.userName) : '-' }}</strong>
          <span>{{ currentUser ? currentUser.userName : '-' }}</span>
        </div>
        <div class="scope-preview__metrics">
          <div><span>直接新增</span><strong>{{ scopePreview.directAddedCount || 0 }}</strong></div>
          <div><span>直接移除</span><strong>{{ scopePreview.directRemovedCount || 0 }}</strong></div>
          <div><span>生效范围增加</span><strong>{{ scopePreview.effectiveAddedCount || 0 }}</strong></div>
          <div><span>生效范围减少</span><strong>{{ scopePreview.effectiveRemovedCount || 0 }}</strong></div>
          <div><span>范围外保留</span><strong>{{ scopePreview.preservedCount || 0 }}</strong></div>
        </div>
        <el-alert v-for="(warning, index) in scopePreview.warnings || []" :key="index" :title="warning" type="warning" :closable="false" show-icon class="scope-preview__warning" />
        <p class="scope-preview__tip">保存时会再次校验授权版本；若其他管理员已修改，系统不会覆盖对方结果。</p>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="scopePreviewVisible = false">返回调整</el-button>
        <el-button type="primary" :loading="saveLoading" :disabled="!scopePreview || !scopePreview.scopeVersion" @click="confirmShopScopeSave">确认保存</el-button>
      </div>
    </el-dialog>

    <user-view-drawer ref="userViewRef" />
  </div>
</template>

<script>
import { optionselectPost } from "@/api/system/post"
import { batchUserShop, getUserShop, listUserShopUsers, shopTree, previewUserShop, updateUserShop } from "@/api/system/userShop"
import UserViewDrawer from "@/views/system/user/view"
import ResizableSplitPane from "@/components/ResizableSplitPane"

export default {
  name: "UserShop",
  components: { UserViewDrawer, ResizableSplitPane },
  dicts: ['sys_normal_disable'],
  data() {
    return {
      loading: false,
      shopTreeLoading: false,
      userScopeLoading: false,
      previewLoading: false,
      saveLoading: false,
      showSearch: true,
      total: 0,
      userList: [],
      postOptions: [],
      currentUser: null,
      shopOptions: [],
      selectedShopIds: [],
      draftShopIds: [],
      preservedShopIds: [],
      preservedShopCount: 0,
      userShopScopeMap: {},
      userShopPreservedCountMap: {},
      shopScopeLabelMap: {},
      shopScopeLoadFailed: false,
      shopScopeRequestSeq: 0,
      routeUserId: undefined,
      routeUserName: "",
      revertingCurrentUserSelection: false,
      currentScopeRequestSeq: 0,
      lastLoadedPageNum: 1,
      lastLoadedPageSize: 10,
      filterText: "",
      showSelectedOnly: false,
      checkedShopIdsSnapshot: [],
      scopePreviewVisible: false,
      scopePreview: null,
      previewedShopIds: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: undefined,
        postId: undefined,
        status: undefined
      },
      defaultProps: {
        children: "children",
        label: "deptName"
      }
    }
  },
  computed: {
    shopLoading() {
      return this.shopTreeLoading || this.userScopeLoading
    },
    managementUxV2Enabled() {
      const features = this.$store && this.$store.state && this.$store.state.user
        ? this.$store.state.user.businessFeatures
        : null
      return Boolean(features && features.systemManagementUxV2)
    },
    canSaveShopScope() {
      return Boolean(this.$auth) && this.$auth.hasPermiAnd(["system:userShop:query", "system:userShop:edit"])
    },
    directAuthorizationCount() {
      return this.normalizeShopIds(this.draftShopIds).length
    },
    inheritedAuthorizationCount() {
      const selected = new Set(this.normalizeShopIds(this.draftShopIds))
      const inherited = new Set()
      selected.forEach(id => {
        const node = this.findShopNode(this.shopOptions, id)
        this.collectAuthorizableDeptIds(node && node.children ? node.children : []).forEach(childId => {
          if (!selected.has(Number(childId))) inherited.add(Number(childId))
        })
      })
      return inherited.size
    },
    shopScopeChanges() {
      const before = new Set(this.normalizeShopIds(this.selectedShopIds))
      const after = new Set(this.normalizeShopIds(this.draftShopIds))
      return {
        added: [...after].filter(id => !before.has(id)),
        removed: [...before].filter(id => !after.has(id))
      }
    },
    shopScopeChangeSummary() {
      const added = this.shopScopeChanges.added.length
      const removed = this.shopScopeChanges.removed.length
      if (!added && !removed) return ""
      return `待保存：新增 ${added} 个直接授权，移除 ${removed} 个直接授权；越权不可编辑的 ${this.preservedShopCount} 个授权保持不变。`
    }
  },
  watch: {
    filterText(val) {
      this.$refs.shopTree && this.$refs.shopTree.filter(val)
    }
  },
  created() {
    this.applyRouteUserQuery()
    this.getList()
    this.getPostOptions()
    this.getShopTree()
    window.addEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeDestroy() {
    window.removeEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeRouteLeave(to, from, next) {
    if (!this.hasUnsavedShopScopeChanges()) {
      next()
      return
    }
    this.confirmDiscardUnsavedShopScope("离开页面将丢失未保存选择，是否继续？")
      .then(() => next()).catch(() => next(false))
  },
  methods: {
    applyRouteUserQuery() {
      const query = this.$route.query || {}
      this.routeUserId = query.userId ? Number(query.userId) : undefined
      this.routeUserName = query.userName ? String(query.userName) : ""
      if (this.routeUserName) {
        this.queryParams.userName = this.routeUserName
      }
    },
    getList() {
      this.loading = true
      return listUserShopUsers(this.queryParams).then(response => {
        this.userList = response.rows || []
        this.total = Number(response.total || 0)
        this.lastLoadedPageNum = this.queryParams.pageNum
        this.lastLoadedPageSize = this.queryParams.pageSize
        this.loadUserShopScopes(this.userList)
        this.$nextTick(() => {
          this.focusRouteUser()
        })
      }).finally(() => {
        this.loading = false
      })
    },
    getPostOptions() {
      optionselectPost().then(response => {
        this.postOptions = response.data || []
      })
    },
    getShopTree() {
      this.shopTreeLoading = true
      return shopTree().then(response => {
        this.shopOptions = this.decorateShopTree(response.data || [])
        this.refreshShopScopeLabels()
        if (this.currentUser) {
          this.$nextTick(() => {
            this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys(this.selectedShopIds)
            this.syncCheckedShopIdsSnapshot()
          })
        }
      }).finally(() => {
        this.shopTreeLoading = false
      })
    },
    handleQuery() {
      return this.runAfterDiscardConfirmation(() => {
        this.queryParams.pageNum = 1
        this.clearCurrentUserShop()
        return this.getList()
      }, "重新查询将丢失未保存的组织授权选择，是否继续？").catch(() => {})
    },
    resetQuery() {
      return this.runAfterDiscardConfirmation(() => {
        this.routeUserId = undefined
        this.routeUserName = ""
        this.resetForm("queryForm")
        this.queryParams.pageNum = 1
        this.clearCurrentUserShop()
        return this.getList()
      }, "重置查询将丢失未保存的组织授权选择，是否继续？").catch(() => {})
    },
    handlePagination() {
      return this.runAfterDiscardConfirmation(() => {
        this.clearCurrentUserShop()
        return this.getList()
      }, "翻页将丢失未保存的组织授权选择，是否继续？").catch(() => {
        this.queryParams.pageNum = this.lastLoadedPageNum
        this.queryParams.pageSize = this.lastLoadedPageSize
      })
    },
    focusRouteUser() {
      if (!this.routeUserId && !this.routeUserName) {
        return
      }
      const matchedUser = (this.userList || []).find(row => {
        if (this.routeUserId && String(row.userId) === String(this.routeUserId)) {
          return true
        }
        return this.routeUserName && row.userName === this.routeUserName
      })
      if (!matchedUser) {
        return
      }
      this.revertingCurrentUserSelection = true
      if (this.$refs.userTable) {
        this.$refs.userTable.setCurrentRow(matchedUser)
      }
      this.$nextTick(() => {
        this.revertingCurrentUserSelection = false
      })
      this.loadCurrentUserShop(matchedUser)
    },
    handleCurrentUserChange(row) {
      if (this.revertingCurrentUserSelection) {
        return
      }
      if (this.shouldConfirmDiscardShopScope(row)) {
        this.confirmDiscardUnsavedShopScope().then(() => {
          this.loadCurrentUserShop(row)
        }).catch(() => {
          this.revertCurrentUserSelection()
        })
        return
      }
      this.loadCurrentUserShop(row)
    },
    loadCurrentUserShop(row) {
      const requestSeq = ++this.currentScopeRequestSeq
      this.currentUser = row
      this.scopePreviewVisible = false
      this.scopePreview = null
      this.previewedShopIds = []
      if (!row) {
        this.userScopeLoading = false
        this.selectedShopIds = []
        this.draftShopIds = []
        this.preservedShopIds = []
        this.preservedShopCount = 0
        this.checkedShopIdsSnapshot = []
        this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys([])
        return
      }
      this.selectedShopIds = []
      this.preservedShopIds = []
      this.preservedShopCount = 0
      this.checkedShopIdsSnapshot = []
      this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys([])
      this.userScopeLoading = true
      return getUserShop(row.userId).then(response => {
        if (requestSeq !== this.currentScopeRequestSeq || !this.currentUser || this.currentUser.userId !== row.userId) {
          return
        }
        const editableShopIds = response.editableShopIds || response.shopIds || []
        this.selectedShopIds = this.normalizeShopIds(editableShopIds)
        this.draftShopIds = this.normalizeShopIds(editableShopIds)
        this.preservedShopIds = this.normalizeShopIds(response.preservedShopIds || [])
        this.preservedShopCount = Number(response.outOfScopeCount || this.preservedShopIds.length || 0)
        const fullShopIds = this.normalizeShopIds([...this.selectedShopIds, ...this.preservedShopIds])
        this.userShopScopeMap = Object.assign({}, this.userShopScopeMap, {
          [row.userId]: fullShopIds
        })
        this.userShopPreservedCountMap = Object.assign({}, this.userShopPreservedCountMap, {
          [row.userId]: this.preservedShopCount
        })
        this.refreshShopScopeLabels()
        this.$nextTick(() => {
          this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys(this.selectedShopIds)
          this.syncCheckedShopIdsSnapshot()
        })
      }).finally(() => {
        if (requestSeq === this.currentScopeRequestSeq) {
          this.userScopeLoading = false
        }
      })
    },
    shouldConfirmDiscardShopScope(row) {
      if (!this.currentUser || (row && row.userId === this.currentUser.userId)) {
        return false
      }
      return this.hasUnsavedShopScopeChanges()
    },
    hasUnsavedShopScopeChanges() {
      if (!this.currentUser) {
        return false
      }
      return !this.isSameShopSelection(this.draftShopIds, this.selectedShopIds)
    },
    confirmDiscardUnsavedShopScope(message) {
      return this.$modal.confirm(message || "当前组织授权选择尚未保存，切换用户将丢失未保存选择，是否继续？")
    },
    runAfterDiscardConfirmation(action, message) {
      if (!this.hasUnsavedShopScopeChanges()) {
        return Promise.resolve().then(action)
      }
      return this.confirmDiscardUnsavedShopScope(message).then(action)
    },
    clearCurrentUserShop() {
      this.currentScopeRequestSeq += 1
      this.userScopeLoading = false
      this.currentUser = null
      this.selectedShopIds = []
      this.preservedShopIds = []
      this.preservedShopCount = 0
      this.checkedShopIdsSnapshot = []
      this.scopePreviewVisible = false
      this.scopePreview = null
      this.previewedShopIds = []
      this.revertingCurrentUserSelection = true
      this.$refs.userTable && this.$refs.userTable.setCurrentRow()
      this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys([])
      this.$nextTick(() => {
        this.revertingCurrentUserSelection = false
      })
    },
    handleBeforeUnload(event) {
      if (!this.hasUnsavedShopScopeChanges()) {
        return undefined
      }
      event.preventDefault()
      event.returnValue = ""
      return ""
    },
    revertCurrentUserSelection() {
      this.revertingCurrentUserSelection = true
      this.$nextTick(() => {
        this.$refs.userTable && this.$refs.userTable.setCurrentRow(this.currentUser)
        this.$nextTick(() => {
          this.revertingCurrentUserSelection = false
        })
      })
    },
    submitShopScope() {
      if (!this.currentUser) {
        this.$modal.msgWarning("请先选择用户")
        return
      }
      const checkedKeys = this.getCheckedShopIds()
      const userId = this.currentUser.userId
      this.previewLoading = true
      previewUserShop(userId, checkedKeys).then(response => {
        if (!this.currentUser || this.currentUser.userId !== userId) {
          return
        }
        this.scopePreview = response.data || null
        this.previewedShopIds = this.normalizeShopIds(
          this.scopePreview && this.scopePreview.normalizedShopIds !== undefined
            ? this.scopePreview.normalizedShopIds
            : checkedKeys
        )
        this.scopePreviewVisible = Boolean(this.scopePreview && this.scopePreview.scopeVersion)
      }).finally(() => {
        this.previewLoading = false
      })
    },
    confirmShopScopeSave() {
      if (!this.currentUser || !this.scopePreview || !this.scopePreview.scopeVersion) {
        this.$modal.msgWarning("授权预览已失效，请重新预览")
        return
      }
      const userId = this.currentUser.userId
      this.saveLoading = true
      updateUserShop(userId, this.previewedShopIds, this.scopePreview.scopeVersion).then(response => {
        if (!this.currentUser || this.currentUser.userId !== userId) {
          return
        }
        this.applyAuthoritativeShopScope(response.data || {})
        this.scopePreviewVisible = false
        this.scopePreview = null
        this.previewedShopIds = []
        this.$modal.msgSuccess("组织授权保存成功")
      }).catch(error => {
        if (!this.isShopScopeConflict(error)) {
          return
        }
        this.scopePreviewVisible = false
        this.scopePreview = null
        this.previewedShopIds = []
        this.$modal.msgWarning("授权已被其他管理员修改，已为你重新加载最新结果，请核对后再次预览")
        if (this.currentUser && this.currentUser.userId === userId) {
          this.loadCurrentUserShop(this.currentUser)
        }
      }).finally(() => {
        this.saveLoading = false
      })
    },
    isShopScopeConflict(error) {
      const response = error && error.response
      const data = response && response.data ? response.data : response
      return Boolean(data && data.businessCode === "USER_SHOP_SCOPE_CONFLICT")
    },
    applyAuthoritativeShopScope(scope) {
      const normalizedIds = this.normalizeShopIds(scope.normalizedShopIds || this.previewedShopIds)
      const preservedIds = this.normalizeShopIds(scope.preservedShopIds || [])
      const fullShopIds = this.normalizeShopIds([...normalizedIds, ...preservedIds])
      this.selectedShopIds = normalizedIds
      this.preservedShopIds = preservedIds
      this.preservedShopCount = Number(scope.preservedCount || preservedIds.length || 0)
      this.checkedShopIdsSnapshot = normalizedIds.slice()
      this.userShopScopeMap = Object.assign({}, this.userShopScopeMap, {
        [this.currentUser.userId]: fullShopIds
      })
      this.userShopPreservedCountMap = Object.assign({}, this.userShopPreservedCountMap, {
        [this.currentUser.userId]: this.preservedShopCount
      })
      this.refreshShopScopeLabels()
      this.$nextTick(() => {
        this.$refs.shopTree && this.$refs.shopTree.setCheckedKeys(normalizedIds)
        this.syncCheckedShopIdsSnapshot()
      })
    },
    filterNode(value, data) {
      const normalizedValue = String(value || "").trim().toLowerCase()
      const textMatches = !normalizedValue || String(data.deptName || "").toLowerCase().indexOf(normalizedValue) !== -1
      if (!this.showSelectedOnly) {
        return textMatches
      }
      return textMatches && (this.nodeScopeKind(data) !== "none" || this.hasSelectedDescendant(data))
    },
    handleTreeCheck() {
      this.syncCheckedShopIdsSnapshot()
      this.refreshTreeFilter()
    },
    syncCheckedShopIdsSnapshot() {
      this.checkedShopIdsSnapshot = this.getCheckedShopIds()
    },
    refreshTreeFilter() {
      this.$nextTick(() => {
        this.$refs.shopTree && this.$refs.shopTree.filter(this.filterText || "")
      })
    },
    nodeScopeKind(data) {
      const selectedSet = new Set(this.checkedShopIdsSnapshot.map(Number))
      if (selectedSet.has(Number(data.deptId))) {
        return "direct"
      }
      const ancestors = data._ancestorDeptIds || []
      return ancestors.some(id => selectedSet.has(Number(id))) ? "inherited" : "none"
    },
    hasSelectedDescendant(data) {
      const selectedSet = new Set(this.checkedShopIdsSnapshot.map(Number))
      return this.collectAuthorizableDeptIds(data.children || []).some(id => selectedSet.has(Number(id)))
    },
    expandToMatches() {
      const tree = this.$refs.shopTree
      if (!tree) return
      const value = String(this.filterText || "").trim().toLowerCase()
      const matchingIds = []
      const visit = nodes => {
        ;(nodes || []).forEach(node => {
          const textMatches = !value || String(node.deptName || "").toLowerCase().indexOf(value) !== -1
          const selectionMatches = !this.showSelectedOnly || this.nodeScopeKind(node) !== "none" || this.hasSelectedDescendant(node)
          if (textMatches && selectionMatches) matchingIds.push(node.deptId)
          visit(node.children || [])
        })
      }
      visit(this.shopOptions)
      matchingIds.forEach(id => {
        let node = tree.getNode(id)
        while (node) {
          node.expanded = true
          node = node.parent
        }
      })
      if (!matchingIds.length) this.$modal.msgWarning("没有匹配的组织")
    },
    clearEditableSelection() {
      if (!this.$refs.shopTree) return
      this.$refs.shopTree.setCheckedKeys([])
      this.syncCheckedShopIdsSnapshot()
      this.refreshTreeFilter()
    },
    handleViewData(row) {
      this.$refs.userViewRef.open(row.userId)
    },
    loadUserShopScopes(rows) {
      const users = (rows || []).filter(row => row && row.userId)
      if (!users.length) {
        this.userShopScopeMap = {}
        this.shopScopeLabelMap = {}
        this.shopScopeLoadFailed = false
        return
      }
      const requestSeq = ++this.shopScopeRequestSeq
      this.shopScopeLoadFailed = false
      batchUserShop(users.map(row => row.userId)).then(response => {
        if (requestSeq !== this.shopScopeRequestSeq) {
          return
        }
        const scopeMap = {}
        const shopMap = response.data || {}
        users.forEach(row => {
          scopeMap[row.userId] = this.normalizeShopIds(shopMap[row.userId] || [])
        })
        this.userShopScopeMap = scopeMap
        this.userShopPreservedCountMap = {}
        this.shopScopeLoadFailed = false
        this.refreshShopScopeLabels()
      }).catch(() => {
        if (requestSeq !== this.shopScopeRequestSeq) {
          return
        }
        this.userShopScopeMap = {}
        this.userShopPreservedCountMap = {}
        this.shopScopeLoadFailed = true
        this.refreshShopScopeLabels()
      })
    },
    refreshShopScopeLabels() {
      const labelMap = {}
      Object.keys(this.userShopScopeMap).forEach(userId => {
        labelMap[userId] = this.formatShopScope(this.userShopScopeMap[userId])
      })
      this.shopScopeLabelMap = labelMap
    },
    getUserShopScopeLabel(row) {
      if (!row || !row.userId) {
        return "-"
      }
      if (this.shopScopeLoadFailed) {
        return "读取失败，可重试"
      }
      const shopScopeLabel = this.shopScopeLabelMap[row.userId]
      if (shopScopeLabel && shopScopeLabel !== "-") {
        return this.appendPreservedScopeCount(shopScopeLabel, row.userId)
      }
      if (Object.prototype.hasOwnProperty.call(this.userShopScopeMap, row.userId)) {
        return "未授权"
      }
      return "读取中"
    },
    appendPreservedScopeCount(label, userId) {
      const preservedCount = Number(this.userShopPreservedCountMap[userId] || 0)
      if (!preservedCount || !label || label === "-") {
        return label
      }
      return `${label}（另保留${preservedCount}个）`
    },
    getUserDeptScopeLabel(row) {
      return row && row.deptName ? row.deptName : "-"
    },
    formatShopScope(shopIds) {
      const selectedIds = this.normalizeShopIds(shopIds)
      if (!selectedIds.length) {
        return "-"
      }
      const selectedSet = new Set(selectedIds)
      const labels = this.buildShopScopeLabels(this.shopOptions, selectedSet)
      return labels.length ? labels.join("、") : "-"
    },
    buildShopScopeLabels(nodes, selectedSet) {
      return (nodes || []).reduce((result, node) => {
        if (this.isAuthorizableDept(node)) {
          if (selectedSet.has(Number(node.deptId))) {
            result.push(node.deptName)
            return result
          }
        }
        const authDeptIds = this.collectAuthorizableDeptIds([node])
        if (authDeptIds.length > 0 && authDeptIds.every(id => selectedSet.has(Number(id)))) {
          result.push(node.deptName)
          return result
        }
        result.push(...this.buildShopScopeLabels(node.children || [], selectedSet))
        return result
      }, [])
    },
    collectAuthorizableDeptIds(nodes) {
      return (nodes || []).reduce((result, node) => {
        if (this.isAuthorizableDept(node)) {
          result.push(Number(node.deptId))
        }
        result.push(...this.collectAuthorizableDeptIds(node.children || []))
        return result
      }, [])
    },
    findShopNode(nodes, deptId) {
      for (const node of nodes || []) {
        if (String(node.deptId) === String(deptId)) return node
        const child = this.findShopNode(node.children || [], deptId)
        if (child) return child
      }
      return null
    },
    shopLabelForId(deptId) {
      const node = this.findShopNode(this.shopOptions, deptId)
      return node ? node.deptName : String(deptId)
    },
    isAuthorizableDept(node) {
      return node && (
        node.deptType === "GROUP" ||
        node.deptType === "COMPANY" ||
        node.deptType === "STORE" ||
        node.deptType === "WAREHOUSE"
      )
    },
    normalizeShopIds(shopIds) {
      return Array.from(new Set((shopIds || []).map(id => Number(id)).filter(id => id > 0)))
    },
    getCheckedShopIds() {
      if (!this.$refs.shopTree) {
        return []
      }
      return this.normalizeShopIds(this.$refs.shopTree.getCheckedNodes(false, false)
        .filter(node => this.isAuthorizableDept(node))
        .map(node => node.deptId))
    },
    isSameShopSelection(leftShopIds, rightShopIds) {
      const left = this.sortShopIds(leftShopIds)
      const right = this.sortShopIds(rightShopIds)
      return left.length === right.length && left.every((id, index) => id === right[index])
    },
    sortShopIds(shopIds) {
      return this.normalizeShopIds(shopIds).sort((left, right) => left - right)
    },
    decorateShopTree(nodes, ancestorDeptIds = []) {
      return (nodes || []).map(node => {
        const item = Object.assign({}, node)
        item._ancestorDeptIds = ancestorDeptIds.slice()
        item.children = this.decorateShopTree(item.children || [], [...ancestorDeptIds, Number(item.deptId)])
        return item
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.shop-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.authorization-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.page-title {
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.authorization-steps {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}

.user-card {
  padding-bottom: 8px;
  min-height: 520px;
}

.user-list-title {
  margin: 0 0 12px 0;
}

.shop-tree-card {
  min-height: 520px;
  padding: 14px;
  overflow: hidden;
}

.shop-tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.shop-tree-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.selected-user-card {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  background: #f4f9ff;
}

.selected-user-card strong,
.selected-user-card span {
  display: block;
}

.selected-user-card span {
  margin-top: 4px;
  color: #606266;
  font-size: 12px;
}

.authorization-counts {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  justify-content: flex-end;
  gap: 5px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.panel-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.scope-preserved-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #e6a23c;
}

.tree-filter {
  margin-bottom: 8px;
}

.tree-actions,
.scope-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.tree-actions {
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 4px;
  background: #f5f7fa;
}

.shop-tree-node {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.shop-tree-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-preview__identity {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}

.scope-preview__identity span,
.scope-preview__tip {
  color: #909399;
  font-size: 12px;
}

.scope-preview__metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.scope-preview__metrics > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 6px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  text-align: center;
  background: #fafafa;
}

.scope-preview__metrics span {
  color: #606266;
  font-size: 12px;
}

.scope-preview__metrics strong {
  color: #303133;
  font-size: 20px;
}

.scope-preview__warning {
  margin-bottom: 8px;
}

.scope-preview__tip {
  margin: 12px 0 0;
}

.shop-change-summary {
  position: sticky;
  bottom: 0;
  margin-top: 10px;
  padding: 8px 10px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 18px;
  border: 1px solid #faecd8;
  border-radius: 4px;
  background: #fdf6ec;
}

.empty-authorization-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  color: #909399;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background: #fafafa;
  text-align: center;
}

@media (max-width: 991px) {
  .shop-tree-card {
    margin-top: 12px;
  }
}
</style>
