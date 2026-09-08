<template>
  <div :class="['select-shop-page', isMobileViewport ? 'mobile-shop-page is-mobile-web mobile-system-page' : 'is-desktop-web']">
    <main v-if="!isMobileViewport" class="desktop-shop-shell desktop-tea-backdrop" aria-label="选择当前组织">
      <section class="desktop-shop-visual" aria-hidden="true">
        <div class="desktop-shop-emblem">
          <svg viewBox="0 0 24 24">
            <path d="M4 8.4 12 3l8 5.4v9.2L12 21l-8-3.4V8.4Zm8-2.8L6.8 9 12 11.6 17.2 9 12 5.6ZM6 10.7v5.6l5 2.2v-5.6l-5-2.2Zm7 7.8 5-2.2v-5.6l-5 2.2v5.6Z" />
          </svg>
        </div>
        <h1>选择当前组织</h1>
        <p>库存、采购、调拨需要选择具体门店或仓库；公司/组织用于展开下级和汇总查看。</p>
        <div class="desktop-shop-flow">
          <span>登录完成</span>
          <i></i>
          <span>选择上下文</span>
          <i></i>
          <span>进入系统</span>
        </div>
      </section>

      <section class="desktop-shop-card glass-panel">
        <div class="desktop-shop-head">
          <div>
            <h1>选择组织</h1>
            <p>请选择当前操作组织</p>
            <div class="business-context-note">库存、采购、调拨需要选择具体门店或仓库；公司/组织仅用于展开下级和汇总查看。</div>
          </div>
          <span class="desktop-shop-count">{{ businessDeptCount }} 个业务组织可选</span>
        </div>

        <div class="desktop-shop-toolbar">
          <el-input
            v-model="filterText"
            clearable
            label="搜索组织"
            placeholder="搜索公司、组织、店铺或仓库"
            prefix-icon="el-icon-search"
          />
          <el-button class="refresh-icon-btn" size="small" icon="el-icon-refresh" @click="fetchDeptTree">刷新</el-button>
        </div>

        <div class="desktop-shop-body">
          <el-tree
            v-if="deptTree.length > 0"
            ref="deptTree"
            v-loading="loading"
            :data="deptTree"
            :props="treeProps"
            node-key="deptId"
            :highlight-current="true"
            :expand-on-click-node="false"
            :default-expanded-keys="defaultExpandedKeys"
            :filter-node-method="filterNode"
            :render-content="renderTreeNode"
            class="desktop-shop-tree"
            @node-click="handleNodeClick"
          />
          <div v-else class="empty-wrap desktop-empty-wrap">
            <el-empty :description="emptyDescription" />
            <el-button type="primary" plain size="mini" @click="fetchDeptTree">重新获取</el-button>
          </div>
        </div>

        <div class="desktop-shop-footer">
          <div class="desktop-current-info selection-summary" :class="selectedDeptToneClass">
            <span class="selection-summary__icon" aria-hidden="true"><i :class="selectedDeptIconClass" /></span>
            <div class="selection-summary__copy">
              <span>当前选择</span>
              <strong>{{ selectedDeptName || "未选择" }}</strong>
              <small v-if="selectedDeptTypeLabel">{{ selectedDeptTypeLabel }}</small>
              <em v-if="selectedDeptPath">{{ selectedDeptPath }}</em>
              <small v-if="selectionBusinessHint" class="selection-business-hint">{{ selectionBusinessHint }}</small>
            </div>
          </div>
          <div>
            <el-button class="desktop-exit-btn" size="small" @click="handleCancel">退出</el-button>
            <el-button class="desktop-clear-btn" size="small" plain @click="handleClearSelection">清除</el-button>
            <el-button class="desktop-enter-btn" type="primary" size="small" @click="handleConfirm">进入系统</el-button>
          </div>
        </div>
      </section>
    </main>

    <main v-else class="mobile-shop-shell tea-room-backdrop" aria-label="选择当前组织">
      <section class="shop-stage shop-layout">
        <div class="shop-card-stack">
          <header class="shop-hero">
            <div class="shop-hero-copy">
              <div class="shop-brand-mark" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M4 8.4 12 3l8 5.4v9.2L12 21l-8-3.4V8.4Zm8-2.8L6.8 9 12 11.6 17.2 9 12 5.6ZM6 10.7v5.6l5 2.2v-5.6l-5-2.2Zm7 7.8 5-2.2v-5.6l-5 2.2v5.6Z" />
                </svg>
              </div>
              <div>
                <h1>选择组织</h1>
                <p>当前账号</p>
                <div class="business-context-note mobile-business-context-note">库存、采购、调拨建议选择门店或仓库</div>
              </div>
            </div>
            <div class="shop-count">
              <strong>{{ businessDeptCount }}</strong>
              <span>业务组织可选</span>
            </div>
          </header>

          <section class="glass-panel search-panel">
            <div class="mobile-search-row">
              <el-input
                v-model="filterText"
                clearable
                label="搜索组织"
                placeholder="搜索公司、组织、店铺或仓库"
                prefix-icon="el-icon-search"
                class="filter-input liquid-input"
              />
              <el-button
                class="refresh-icon-btn mobile-refresh-btn"
                aria-label="刷新组织列表"
                title="刷新组织列表"
                icon="el-icon-refresh"
                circle
                @click="fetchDeptTree"
              />
            </div>
          </section>

          <section v-if="mobileNoBusinessAccess" class="glass-panel mobile-no-business-panel">
            <strong>当前账号暂无移动端可用门店或仓库权限</strong>
            <span>手机端业务工作台需要门店或仓库授权，请联系管理员分配后再进入。</span>
          </section>

          <section class="glass-panel shop-tree-panel">
            <div class="panel-head">
              <h2>组织列表</h2>
              <span>{{ loading ? "加载中" : selectedDeptName || "未选择" }}</span>
            </div>
            <el-tree
              v-if="deptTree.length > 0"
              ref="deptTree"
              v-loading="loading"
              :data="deptTree"
              :props="treeProps"
              node-key="deptId"
              :highlight-current="true"
              :expand-on-click-node="false"
              :default-expanded-keys="defaultExpandedKeys"
              :filter-node-method="filterNode"
              :render-content="renderTreeNode"
              class="liquid-tree"
              @node-click="handleNodeClick"
            />
            <div v-else class="empty-wrap">
              <el-empty :description="emptyDescription" />
              <el-button type="primary" plain size="mini" @click="fetchDeptTree">重新获取</el-button>
            </div>
          </section>

          <section class="glass-panel selected-panel selection-summary">
            <div class="current-info">
              <span class="current-label">当前选择</span>
              <strong>{{ selectedDeptName || "未选择" }}</strong>
              <small v-if="selectedDeptTypeLabel" class="current-type">{{ selectedDeptTypeLabel }}</small>
              <span v-if="selectedDeptPath" class="current-path">{{ selectedDeptPath }}</span>
              <small v-if="selectionBusinessHint" class="selection-business-hint">{{ selectionBusinessHint }}</small>
            </div>
            <div class="footer-actions">
              <el-button size="mini" class="cancel-btn" @click="handleCancel">退出</el-button>
              <el-button size="mini" plain class="clear-btn" @click="handleClearSelection">清除</el-button>
              <el-button type="primary" class="enter-btn" :disabled="mobileNoBusinessAccess" @click="handleConfirm">进入工作台</el-button>
            </div>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>

<script>
import { listShopTree } from "@/api/system/dept"
import { MessageBox } from "@/plugins/element-services"
import { requiresInventoryContext } from "@/utils/desktopContextPolicy"
import { clearSelectedDept, findBusinessDeptNodes, findUniqueBusinessDept, getSelectedDeptId, getSelectedDeptType, isSelectableDeptType, isValidInventoryDeptType, setSelectedDept } from "@/utils/shopContext"
import { consumePendingPasswordResetReminder, getPasswordResetRedirect } from "@/utils/passwordResetReminder"
import { getMobileHomePath } from "@/views/mobile/mobileNavigation"
const { isMobileClient } = require("@/utils/clientPlatform")
const {
  resolveNoBusinessContextRedirect,
  resolveShopEntryRedirect
} = require("./shopEntryRouting")
const {
  PROFILE_COMPLETION_PATH,
  getProfileCompletionErrorFields,
  isProfileCompletionRequiredError
} = require("@/utils/profileCompletion")

export default {
  name: "SelectShop",
  data() {
    return {
      loading: false,
      isMobileViewport: false,
      filterText: "",
      deptTree: [],
      selectedDeptId: getSelectedDeptId(),
      selectedDeptName: "",
      selectedDeptType: getSelectedDeptType(),
      selectedDeptPath: "",
      emptyDescription: "暂无组织数据",
      defaultExpandedKeys: [],
      treeProps: {
        label: "deptName",
        children: "children"
      }
    }
  },
  computed: {
    userPermissions() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return Array.isArray(getters.permissions) ? getters.permissions : []
    },
    businessDeptCount() {
      return findBusinessDeptNodes(this.deptTree).length
    },
    selectedDeptTypeLabel() {
      if (this.selectedDeptType === "COMPANY" && this.selectedDeptPath) {
        return this.selectedDeptPath.split(" / ").length > 2 ? "组织" : "公司"
      }
      return this.getDeptTypeLabel(this.selectedDeptType)
    },
    selectionBusinessHint() {
      return this.getSelectionBusinessHint(this.selectedDeptType)
    },
    selectedDeptToneClass() {
      return {
        STORE: "is-store",
        WAREHOUSE: "is-warehouse",
        COMPANY: "is-company",
        GROUP: "is-group"
      }[this.selectedDeptType] || "is-empty"
    },
    selectedDeptIconClass() {
      return this.getDeptTypeIconClass(this.selectedDeptType)
    },
    mobileNoBusinessAccess() {
      return this.isMobileViewport && !this.loading && this.deptTree.length > 0 && this.businessDeptCount === 0
    }
  },
  watch: {
    filterText(val) {
      this.$refs.deptTree && this.$refs.deptTree.filter(val)
    }
  },
  created() {
    this.updateViewport()
    this.fetchDeptTree()
  },
  mounted() {
    window.addEventListener("resize", this.updateViewport)
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.updateViewport)
  },
  methods: {
    updateViewport() {
      this.isMobileViewport = isMobileClient()
    },
    fetchDeptTree() {
      this.loading = true
      listShopTree({ silentError: true }).then(res => {
        this.deptTree = res.data || []
        this.emptyDescription = this.deptTree.length > 0 ? "暂无可选组织" : "当前账号无可选组织"
        this.prepareTreeAfterLoad()
      }).catch(error => {
        if (isProfileCompletionRequiredError(error)) {
          const missingFields = getProfileCompletionErrorFields(error)
          clearSelectedDept()
          this.$store.commit("SET_PROFILE_COMPLETION_REQUIRED", true)
          this.$store.commit("SET_PROFILE_MISSING_FIELDS", missingFields)
          this.$router.replace({
            path: PROFILE_COMPLETION_PATH,
            query: { redirect: this.$route.fullPath }
          }).catch(() => {})
          return
        }
        this.emptyDescription = "获取可选组织失败"
        this.$message.error("获取可选组织失败，请稍后重试")
      }).finally(() => {
        this.loading = false
      })
    },
    prepareTreeAfterLoad() {
      const contextFreeRedirect = resolveNoBusinessContextRedirect({
        isMobileViewport: this.isMobileViewport,
        businessDeptCount: this.businessDeptCount,
        requestedRedirect: this.$route.query.redirect,
        requestedRedirectRequiresContext: requiresInventoryContext(this.$route.query.redirect),
        permissions: this.userPermissions
      })
      if (contextFreeRedirect) {
        clearSelectedDept()
        this.$message.info("当前账号无需选择业务组织，正在进入已授权功能")
        this.$router.replace(contextFreeRedirect).catch(() => {})
        return
      }
      if (this.deptTree.length > 0) {
        this.defaultExpandedKeys = [this.deptTree[0].deptId]
      }
      this.$nextTick(() => {
        if (this.selectedDeptId) {
          const applied = this.applySelectedDept(this.selectedDeptId)
          if (!applied) {
            this.handleInvalidCachedDept()
          }
        } else {
          const autoDept = this.findAutoBusinessDept()
          if (autoDept) {
            this.applyAutoBusinessDept(autoDept)
            return
          }
        }
      })
    },
    filterNode(value, data) {
      if (!value) return true
      return data.deptName && data.deptName.indexOf(value) > -1
    },
    handleNodeClick(data) {
      const currentNode = this.$refs.deptTree.getNode(data.deptId)
      if (currentNode && this.hasBusinessChildren(data)) {
        currentNode.expanded = true
      }
      if (!isValidInventoryDeptType(data.deptType)) {
        const autoDept = findUniqueBusinessDept(data.children || [])
        if (autoDept) {
          this.applyAutoBusinessDept(autoDept)
          return
        }
        this.selectedDeptId = ""
        this.selectedDeptName = ""
        this.selectedDeptType = data.deptType || ""
        this.selectedDeptPath = currentNode ? this.getNodePath(currentNode).join(" / ") : data.deptName
        this.$message.info("公司/组织仅用于展开下级；请选择具体门店或仓库。")
        return
      }
      this.selectedDeptId = data.deptId
      this.selectedDeptName = data.deptName
      this.selectedDeptType = data.deptType || ""
      this.selectedDeptPath = currentNode ? this.getNodePath(currentNode).join(" / ") : data.deptName
    },
    handleConfirm() {
      if (!this.selectedDeptId) {
        this.$message.warning("请先选择公司、组织、店铺或仓库")
        return
      }
      if (!isSelectableDeptType(this.selectedDeptType)) {
        this.$message.warning("请选择有效组织")
        return
      }
      if (!isValidInventoryDeptType(this.selectedDeptType)) {
        this.$message.warning("请选择具体门店或仓库进入业务工作台")
        return
      }
      setSelectedDept(this.selectedDeptId, this.selectedDeptName, this.selectedDeptType)
      const redirect = this.getConfirmRedirect()
      this.showPendingPasswordResetReminderAfterSelection(redirect)
    },
    getConfirmRedirect() {
      return resolveShopEntryRedirect({
        isMobileViewport: this.isMobileViewport,
        deptType: this.selectedDeptType,
        permissions: this.userPermissions,
        requestedRedirect: this.$route.query.redirect
      })
    },
    showPendingPasswordResetReminderAfterSelection(redirect) {
      const reminder = consumePendingPasswordResetReminder()
      if (!reminder) {
        this.$router.push(redirect).catch(() => {})
        return
      }
      MessageBox.confirm(reminder.message, "安全提示", { confirmButtonText: "确定", cancelButtonText: "取消", type: "warning" }).then(() => {
        this.$router.push(getPasswordResetRedirect()).catch(() => {})
      }).catch(() => {
        this.$router.push(redirect).catch(() => {})
      })
    },
    handleClearSelection() {
      clearSelectedDept()
      this.clearSelectionState()
      this.$message.success("已清除当前选择")
    },
    handleInvalidCachedDept() {
      const hadCachedDept = !!this.selectedDeptId
      clearSelectedDept()
      this.clearSelectionState()
      if (hadCachedDept) {
        this.$message.warning("当前选择已不在授权范围内，请重新选择")
      }
    },
    clearSelectionState() {
      this.selectedDeptId = ""
      this.selectedDeptName = ""
      this.selectedDeptType = ""
      this.selectedDeptPath = ""
      if (this.$refs.deptTree) {
        this.$refs.deptTree.setCurrentKey(null)
      }
    },
    handleCancel() {
      clearSelectedDept()
      this.$store.dispatch("FedLogOut").then(() => {
        this.$router.push("/login").catch(() => {})
      })
    },
    getFallbackRedirect() {
      if (this.isMobileViewport) {
        return getMobileHomePath(this.selectedDeptType, this.userPermissions)
      }
      return "/"
    },
    applySelectedDept(deptId) {
      if (!this.$refs.deptTree) {
        return false
      }
      const currentNode = this.$refs.deptTree.getNode(deptId)
      if (!currentNode || !currentNode.data) {
        return false
      }
      this.$refs.deptTree.setCurrentKey(deptId)
      this.selectedDeptId = currentNode.data.deptId
      this.selectedDeptName = currentNode.data.deptName
      this.selectedDeptType = currentNode.data.deptType || ""
      this.selectedDeptPath = this.getNodePath(currentNode).join(" / ")
      this.expandToNode(currentNode)
      this.scrollToCurrentNode()
      return true
    },
    findAutoBusinessDept() {
      return findUniqueBusinessDept(this.deptTree)
    },
    applyAutoBusinessDept(dept) {
      if (!dept || !dept.deptId) {
        return false
      }
      const applied = this.applySelectedDept(dept.deptId)
      if (applied && isValidInventoryDeptType(dept.deptType)) {
        setSelectedDept(dept.deptId, dept.deptName, dept.deptType)
      }
      return applied
    },
    countTreeNodes(nodes) {
      return nodes.reduce((total, item) => {
        const children = item.children || []
        return total + 1 + this.countTreeNodes(children)
      }, 0)
    },
    countBusinessDeptNodes(nodes) {
      return findBusinessDeptNodes(nodes).length
    },
    findFirstLeaf(nodes) {
      for (const item of nodes) {
        if (!item.children || item.children.length === 0) {
          return item
        }
        const child = this.findFirstLeaf(item.children)
        if (child) {
          return child
        }
      }
      return null
    },
    getNodePath(node) {
      const path = []
      let current = node
      while (current && current.level > 0) {
        if (current.data && current.data.deptName) {
          path.unshift(current.data.deptName)
        }
        current = current.parent
      }
      return path
    },
    expandToNode(node) {
      let current = node
      while (current) {
        if (current.expanded !== undefined) {
          current.expanded = true
        }
        current = current.parent
      }
    },
    scrollToCurrentNode() {
      this.$nextTick(() => {
        const el = this.$el.querySelector(".liquid-tree .el-tree-node.is-current, .desktop-shop-tree .el-tree-node.is-current")
        if (el && typeof el.scrollIntoView === "function") {
          el.scrollIntoView({ block: "center", behavior: "smooth" })
        }
      })
    },
    renderTreeNode(h, { node, data }) {
      const typeLabel = this.getDeptTypeLabel(data.deptType, data, node)
      const typeClass = this.getDeptTypeClass(data.deptType, data, node)
      const children = [
        h("span", { class: "context-tree-identity" }, [
          h("span", { class: ["context-tree-icon", typeClass] }, [h("i", { class: this.getDeptTypeIconClass(data.deptType, data, node) })]),
          h("span", { class: "context-tree-name" }, node.label)
        ])
      ]
      if (typeLabel) {
        children.push(h("span", { class: ["context-tree-type", typeClass] }, typeLabel))
      }
      if (this.hasBusinessChildren(data)) {
        children.push(h("span", { class: "context-tree-children" }, (data.children || []).length + " 个下级"))
      }
      return h("span", { class: "context-tree-node" }, children)
    },
    getDeptTypeIconClass(deptType, data, node) {
      if (deptType === "COMPANY" && data && node && this.isNestedOrganization(data, node)) {
        return "el-icon-connection"
      }
      return {
        STORE: "el-icon-s-shop",
        WAREHOUSE: "el-icon-box",
        COMPANY: "el-icon-office-building",
        GROUP: "el-icon-coin"
      }[deptType] || "el-icon-connection"
    },
    hasBusinessChildren(data) {
      return !!(data && data.children && data.children.length)
    },
    getSelectionBusinessHint(deptType) {
      if (deptType === "STORE") {
        return "当前门店可操作销售、要货、门店库存和收货。"
      }
      if (deptType === "WAREHOUSE") {
        return "当前仓库可操作采购、入库、发货、仓库库存和盘点。"
      }
      if (deptType === "COMPANY" || deptType === "GROUP") {
        return "公司/组织用于展开下级；请选择具体门店或仓库进入业务工作台。"
      }
      return ""
    },
    getDeptTypeLabel(deptType, data, node) {
      if (deptType === "COMPANY") {
        return this.isNestedOrganization(data, node) ? "组织" : "公司"
      }
      const labelMap = {
        STORE: "店铺",
        WAREHOUSE: "仓库",
        GROUP: "集团"
      }
      return labelMap[deptType] || ""
    },
    isNestedOrganization(data, node) {
      const parentType = node && node.parent && node.parent.data && node.parent.data.deptType
        ? String(node.parent.data.deptType).toUpperCase()
        : ""
      if (parentType) {
        return parentType !== "GROUP"
      }
      const ancestorIds = String(data && data.ancestors || "")
        .split(",")
        .map(value => value.trim())
        .filter(value => value && value !== "0")
      return ancestorIds.length > 1
    },
    getDeptTypeClass(deptType, data, node) {
      if (deptType === "COMPANY" && this.isNestedOrganization(data, node)) {
        return "is-org"
      }
      return {
        STORE: "is-store",
        WAREHOUSE: "is-warehouse",
        COMPANY: "is-company",
        GROUP: "is-group"
      }[deptType] || "is-org"
    }
  }
}
</script>

<style lang="scss" scoped>
.select-shop-page {
  min-height: 100vh;
  background: #edf4ef;
  display: flex;
  justify-content: center;
  color: #07130d;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  overflow-x: hidden;
}

.select-shop-page.is-desktop-web {
  align-items: stretch;
  background: #f3f0ea;
  color: #242320;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

.desktop-tea-backdrop {
  position: relative;
  background:
    linear-gradient(90deg, rgba(255, 253, 249, 0.97) 0%, rgba(255, 253, 249, 0.88) 42%, rgba(255, 253, 249, 0.68) 64%, rgba(255, 253, 249, 0.9) 100%),
    url("~@/assets/images/desktop-login-tea-room-bg.jpg") center center / cover no-repeat;
}

.desktop-tea-backdrop::before {
  display: none;
  content: none;
}

.desktop-shop-shell {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(340px, 480px) minmax(480px, 560px);
  gap: 76px;
  align-items: center;
  justify-content: center;
  padding: 52px;
}

.desktop-shop-visual {
  position: relative;
  z-index: 1;
  color: #242320;
  text-shadow: none;
}

.desktop-shop-emblem {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  margin-bottom: 24px;
  color: #242320;
  background: rgba(255, 253, 249, 0.78);
  border: 1px solid #ded9d0;
  box-shadow: 0 12px 28px rgba(55, 50, 44, 0.08);
}

.desktop-shop-emblem svg {
  width: 34px;
  height: 34px;
  fill: currentColor;
}

.desktop-shop-visual h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.12;
  font-weight: 800;
  letter-spacing: 0;
}

.desktop-shop-visual p {
  width: min(460px, 100%);
  margin: 18px 0 0;
  color: #78736d;
  font-size: 17px;
  line-height: 1.8;
}

.desktop-shop-flow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 30px;
}

.desktop-shop-flow span {
  height: 34px;
  display: inline-flex;
  align-items: center;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 253, 249, 0.72);
  border: 1px solid #ded9d0;
  color: #5e5953;
  font-size: 13px;
  font-weight: 700;
}

.desktop-shop-flow i {
  width: 18px;
  height: 1px;
  display: block;
  background: #b8b1a8;
}

.desktop-shop-card {
  position: relative;
  z-index: 1;
  width: min(560px, 100%);
  max-height: calc(100vh - 88px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  background: rgba(255, 253, 249, 0.97);
  border: 1px solid #ded9d0;
  box-shadow: 0 24px 64px rgba(55, 50, 44, 0.14);
}

.desktop-shop-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 30px 18px;
  border-bottom: 1px solid #ded9d0;
}

.desktop-shop-head h1 {
  margin: 0;
  color: #242320;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 900;
  letter-spacing: 0;
}

.desktop-shop-head p {
  margin: 8px 0 0;
  color: #78736d;
  font-size: 14px;
  font-weight: 700;
}

.business-context-note {
  margin-top: 8px;
  color: #6b655f;
  font-size: 12px;
  line-height: 1.6;
  font-weight: 700;
}

.mobile-business-context-note {
  color: #60746a;
  font-size: 11px;
}

.desktop-shop-count {
  flex: 0 0 auto;
  height: 34px;
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
  border-radius: 999px;
  color: #4a4641;
  background: #eeebe5;
  border: 1px solid #ded9d0;
  font-size: 13px;
  font-weight: 800;
}

.desktop-shop-toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 30px;
  border-bottom: 1px solid #ded9d0;
}

.desktop-shop-toolbar ::v-deep .el-input__inner {
  height: 46px;
  line-height: 46px;
  border: 1px solid #c9c3ba;
  border-radius: 10px;
  background: #faf7f2;
  color: #242320;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 1px 2px rgba(55, 50, 44, 0.025);
}

.desktop-shop-toolbar ::v-deep .el-input__inner:focus {
  border-color: #252421;
  box-shadow: 0 0 0 3px rgba(37, 36, 33, 0.15);
  background: #fffdfa;
}

.refresh-icon-btn {
  height: 46px;
  border-radius: 10px;
  border-color: #c9c3ba;
  background: #fffdfa;
  color: #5e5953;
  font-weight: 800;
}

.refresh-icon-btn:hover,
.refresh-icon-btn:focus {
  color: #242320;
  border-color: #9f9890;
  background: #eeebe5;
}

.desktop-shop-body {
  min-height: 330px;
  max-height: min(48vh, 430px);
  overflow: auto;
  padding: 14px 30px 18px;
}

::v-deep .desktop-shop-tree {
  background: transparent;
  color: #242320;
  border: 0;
  padding: 2px 0;
}

::v-deep .desktop-shop-tree .el-tree-node__content {
  height: 46px;
  border-radius: 10px;
  margin-bottom: 7px;
  background: #fffdfa;
  border: 1px solid #ebe6de;
  box-shadow: none;
  font-size: 15px;
  font-weight: 800;
  transition: background var(--motion-duration-fast) var(--motion-ease-standard), border-color var(--motion-duration-fast) var(--motion-ease-standard);
}

::v-deep .desktop-shop-tree .el-tree-node__content:hover {
  background: #f4f0e9;
  border-color: #d2ccc3;
}

::v-deep .desktop-shop-tree .el-tree-node.is-current > .el-tree-node__content {
  background: #eeebe5;
  color: #242320;
  border-color: #bdb6ac;
  box-shadow: inset 3px 0 0 #252421;
}

::v-deep .desktop-shop-tree .el-tree-node__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

::v-deep .context-tree-node {
  min-width: 0;
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

::v-deep .context-tree-identity {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

::v-deep .context-tree-icon {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #5e5953;
  background: #f3efe8;
  border: 1px solid #ded9d0;
  border-radius: 9px;
}

::v-deep .context-tree-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

::v-deep .context-tree-type {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  color: #6b655f;
  background: #f3efe8;
  border: 1px solid #ded9d0;
  font-size: 12px;
  line-height: 1;
  font-weight: 800;
}

::v-deep .context-tree-type.is-store {
  color: #5e5953;
  background: #f3efe8;
  border-color: #ded9d0;
}

::v-deep .context-tree-type.is-warehouse {
  color: #5e5953;
  background: #f3efe8;
  border-color: #ded9d0;
}

::v-deep .context-tree-children {
  flex: 0 0 auto;
  color: #8d877f;
  font-size: 12px;
  font-weight: 800;
}

.desktop-shop-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 30px 22px;
  border-top: 1px solid #ded9d0;
  background: #faf7f2;
}

.desktop-shop-footer > div:last-child {
  flex: 0 0 auto;
  display: flex;
  gap: 10px;
}

.desktop-shop-footer ::v-deep .el-button {
  height: 42px;
  border-radius: 14px;
  font-weight: 800;
}

.desktop-shop-footer ::v-deep .el-button--primary {
  border: none;
  background: #252421;
  box-shadow: 0 8px 18px rgba(37, 36, 33, 0.2);
}

.desktop-current-info {
  min-width: 0;
  max-width: 270px;
}

.selection-summary {
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.selection-summary__copy {
  min-width: 0;
}

.desktop-current-info .selection-summary__icon {
  display: inline-flex;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #6b655f;
  font-size: 16px;
  background: #f3efe8;
  border: 1px solid #ded9d0;
  border-radius: 11px;
}

.desktop-current-info span,
.desktop-current-info small,
.desktop-current-info em {
  display: block;
  color: #78736d;
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
}

.desktop-current-info strong {
  display: block;
  margin: 4px 0;
  color: #242320;
  font-size: 18px;
  line-height: 1.25;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.desktop-current-info em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.desktop-current-info .selection-business-hint,
.selection-business-hint {
  margin-top: 6px;
  color: #5e5953;
  line-height: 1.45;
  white-space: normal;
}

.desktop-empty-wrap {
  border-color: rgba(120, 115, 109, 0.35);
}

@media (max-width: 1080px) and (min-width: 769px) {
  .desktop-shop-shell {
    grid-template-columns: minmax(260px, 340px) minmax(430px, 500px);
    gap: 40px;
    padding: 40px 32px;
  }

  .desktop-shop-visual h1 {
    font-size: 34px;
  }

  .desktop-shop-visual p {
    font-size: 15px;
  }

  .desktop-shop-flow {
    gap: 8px;
  }

  .desktop-shop-flow span {
    padding: 0 11px;
  }

  .desktop-shop-card {
    max-height: calc(100vh - 64px);
  }
}

@media (max-width: 768px) {
  .select-shop-page.is-desktop-web {
    overflow-y: auto;
  }

  .desktop-shop-shell {
    display: block;
    min-height: 100vh;
    padding: 20px 16px;
    box-sizing: border-box;
  }

  .desktop-shop-visual {
    display: none;
  }

  .desktop-shop-card {
    width: 100%;
    max-height: none;
    margin: 0 auto;
    border-radius: 20px;
  }

  .desktop-shop-head {
    flex-direction: column;
    gap: 12px;
    padding: 22px 20px 16px;
  }

  .desktop-shop-head h1 {
    font-size: 24px;
  }

  .desktop-shop-toolbar {
    grid-template-columns: minmax(0, 1fr) auto;
    padding: 14px 20px;
  }

  .desktop-shop-body {
    min-height: 220px;
    max-height: clamp(220px, calc(100vh - 360px), 430px);
    padding: 12px 20px 14px;
  }

  .desktop-shop-footer {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
    padding: 16px 20px 18px;
  }

  .desktop-current-info {
    max-width: none;
  }

  .desktop-shop-footer > div:last-child {
    justify-content: flex-end;
  }
}

.mobile-shop-shell {
  position: relative;
  width: min(100%, 480px);
  min-height: var(--mobile-viewport-height, 100dvh);
  overflow-x: hidden;
  overflow-y: visible;
  box-shadow: none;
  background: var(--mobile-color-page, #f4f5f2);
}

/* Class retained for entry-page contract tests; warm-neutral shell without photo glass. */
.tea-room-backdrop {
  background-color: var(--mobile-color-page, #f4f5f2);
  background-image: none;
  /* asset path retained: mobile-inventory-tea-room-bg.jpg */
}

.mobile-shop-shell::before,
.mobile-shop-shell::after {
  display: none;
  content: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.shop-stage {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  padding: clamp(28px, 5vh, 48px) 26px 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shop-card-stack {
  width: 100%;
  max-width: 388px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.shop-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 0;
}

.shop-hero-copy {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 13px;
}

.shop-brand-mark {
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--mobile-color-primary, #0b6b53);
  background: var(--mobile-color-primary-soft, #e7f2ed);
  border: 1px solid rgba(11, 107, 83, 0.18);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.shop-brand-mark svg {
  width: 30px;
  height: 30px;
  fill: currentColor;
}

.shop-hero h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.04;
  font-weight: 900;
  letter-spacing: 0;
}

.shop-hero p {
  margin: 9px 0 0;
  color: #52645b;
  font-size: 16px;
  font-weight: 800;
}

.shop-count {
  width: 72px;
  height: 72px;
  border-radius: 25px;
  display: grid;
  place-items: center;
  padding: 10px;
  text-align: center;
  color: #0f8f74;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.8), rgba(255, 255, 255, 0.48));
  border: 1px solid rgba(255, 255, 255, 0.78);
  box-shadow:
    0 16px 32px rgba(31, 68, 53, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(18px) saturate(145%);
  -webkit-backdrop-filter: blur(18px) saturate(145%);
}

.shop-count strong,
.shop-count span {
  display: block;
  line-height: 1;
}

.shop-count strong {
  font-size: 25px;
  font-weight: 900;
}

.shop-count span {
  color: #53675d;
  font-size: 13px;
  font-weight: 800;
}

.glass-panel {
  background: var(--mobile-color-surface, #fff);
  border: 1px solid var(--mobile-color-line, #dde2de);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.search-panel {
  border-radius: 16px;
  padding: 12px;
  margin-bottom: 0;
}

.mobile-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
  align-items: center;
}

.filter-input {
  width: 100%;
}

::v-deep .liquid-input .el-input__inner {
  height: 48px;
  line-height: 48px;
  border: 1px solid var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  background: var(--mobile-color-surface, #fff);
  color: var(--mobile-color-ink, #17211d);
  font-size: 16px;
  font-weight: 500;
  box-shadow: none;
}

::v-deep .liquid-input .el-input__inner:focus {
  border-color: var(--mobile-color-primary, #0b6b53);
  box-shadow: 0 0 0 3px rgba(11, 107, 83, 0.2);
  background: #fff;
}

::v-deep .liquid-input .el-input__icon {
  color: #6d8177;
}

.mobile-refresh-btn {
  width: 48px;
  padding: 0;
  font-size: 17px;
}

.mobile-no-business-panel {
  border-radius: 22px;
  padding: 14px 16px;
  color: #26362f;
}

.mobile-no-business-panel strong,
.mobile-no-business-panel span {
  display: block;
}

.mobile-no-business-panel strong {
  font-size: 15px;
  line-height: 1.35;
  font-weight: 900;
}

.mobile-no-business-panel span {
  margin-top: 6px;
  color: #61756b;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 700;
}

.shop-tree-panel {
  border-radius: 28px;
  padding: 17px 15px 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 4px 13px;
}

.panel-head h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
}

.panel-head span {
  min-width: 0;
  color: #60746a;
  font-size: 13px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

::v-deep .liquid-tree {
  background: transparent;
  color: #16241c;
  max-height: min(324px, 35vh);
  overflow: auto;
  border: 0;
  padding: 2px 0;
}

::v-deep .liquid-tree .el-tree-node__content {
  height: 48px;
  border-radius: 17px;
  margin-bottom: 7px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
  font-size: 15px;
  font-weight: 800;
  transition: transform var(--motion-duration-fast) var(--motion-ease-standard);
}

::v-deep .liquid-tree .el-tree-node__content:hover {
  background: rgba(255, 255, 255, 0.72);
  transform: translateY(-1px);
}

::v-deep .liquid-tree .el-tree-node.is-current > .el-tree-node__content {
  background: linear-gradient(135deg, rgba(15, 143, 116, 0.24), rgba(47, 128, 237, 0.2));
  color: #07130d;
  box-shadow: inset 0 0 0 1px rgba(15, 143, 116, 0.28), 0 10px 18px rgba(31, 68, 53, 0.12);
}

::v-deep .liquid-tree .el-tree-node__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-panel {
  position: sticky;
  bottom: max(16px, env(safe-area-inset-bottom));
  border-radius: 28px;
  padding: 16px;
  z-index: 2;
}

.selected-panel.selection-summary {
  display: block;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.current-info {
  min-width: 0;
  margin-bottom: 15px;
}

.current-label,
.current-type,
.current-path {
  display: block;
  color: #60746a;
  font-size: 13px;
  font-weight: 800;
}

.current-info strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-path {
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-actions {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 72px 72px minmax(0, 1fr);
  gap: 9px;
}

.footer-actions ::v-deep .el-button {
  width: 100%;
  min-width: 0;
  margin: 0;
  padding-right: 8px;
  padding-left: 8px;
  white-space: nowrap;
}

.footer-actions ::v-deep .el-button + .el-button {
  margin-left: 0;
}

.clear-btn,
.cancel-btn {
  height: 48px;
  border-color: var(--mobile-color-line-strong, #cbd3ce);
  border-radius: 12px;
  color: var(--mobile-color-ink, #17211d);
  background: var(--mobile-color-surface, #fff);
  font-weight: 600;
}

.clear-btn:hover,
.clear-btn:focus,
.cancel-btn:hover,
.cancel-btn:focus {
  color: var(--mobile-color-primary, #0b6b53);
  border-color: rgba(11, 107, 83, 0.28);
  background: var(--mobile-color-primary-soft, #e7f2ed);
}

.enter-btn {
  height: 48px;
  border: 1px solid var(--mobile-color-primary, #0b6b53);
  border-radius: 12px;
  background: var(--mobile-color-primary, #0b6b53);
  box-shadow: none;
  font-size: 16px;
  font-weight: 700;
}

.enter-btn:hover,
.enter-btn:focus {
  background: var(--mobile-color-primary-strong, #075441);
  border-color: var(--mobile-color-primary-strong, #075441);
  transform: none;
}

.empty-wrap {
  border: 1px dashed rgba(122, 145, 132, 0.35);
  border-radius: 20px;
  padding: 10px 0 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

@supports (height: 100dvh) {
  .select-shop-page,
  .mobile-shop-shell {
    min-height: 100dvh;
  }
}

@media (max-width: 480px) {
  .mobile-shop-shell {
    width: 100%;
    box-shadow: none;
  }

  .shop-stage {
    padding: clamp(26px, 4.5vh, 42px) 22px 28px;
  }

  .shop-card-stack {
    max-width: none;
  }

  .shop-hero h1 {
    font-size: 35px;
  }
}

@media (max-width: 360px) {
  .shop-stage {
    padding-left: 16px;
    padding-right: 16px;
  }

  .shop-hero h1 {
    font-size: 31px;
  }

  .shop-count {
    width: 64px;
    height: 64px;
    border-radius: 22px;
  }

  .footer-actions {
    grid-template-columns: 64px 64px minmax(0, 1fr);
  }
}

/* Organization entry keeps the brand atmosphere while staying task-focused. */
.mobile-shop-page,
.mobile-shop-shell.tea-room-backdrop {
  background-color: var(--mobile-color-page, #f4f5f2);
  background-image:
    radial-gradient(circle at 94% 3%, rgba(11, 107, 83, 0.16) 0, rgba(11, 107, 83, 0) 250px),
    radial-gradient(circle at 0% 64%, rgba(40, 102, 177, 0.06) 0, rgba(40, 102, 177, 0) 220px),
    linear-gradient(180deg, #fbfcf9 0%, #f4f6f3 54%, #eef2ee 100%);
}

.shop-brand-mark {
  border-color: rgba(11, 107, 83, 0.18);
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(214, 235, 226, 0.94));
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.7),
    0 10px 24px rgba(11, 107, 83, 0.12);
}

.shop-count {
  border-color: rgba(11, 107, 83, 0.14);
  border-radius: 20px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(231, 242, 237, 0.86));
  box-shadow: var(--mobile-shadow-card, 0 10px 28px rgba(23, 33, 29, 0.07));
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.glass-panel {
  border-color: rgba(203, 211, 206, 0.82);
  box-shadow: var(--mobile-shadow-card, 0 10px 28px rgba(23, 33, 29, 0.055));
}

.shop-tree-panel,
.selected-panel {
  border-radius: 18px;
}

.selected-panel {
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.64),
    0 18px 38px rgba(23, 33, 29, 0.1);
}

::v-deep .liquid-tree .el-tree-node__content {
  border-color: rgba(203, 211, 206, 0.72);
  border-radius: 12px;
  background: rgba(248, 250, 247, 0.86);
  box-shadow: none;
}

::v-deep .liquid-tree .el-tree-node.is-current > .el-tree-node__content {
  border-color: rgba(11, 107, 83, 0.3);
  background: linear-gradient(135deg, rgba(231, 242, 237, 0.98), rgba(214, 235, 226, 0.8));
  color: var(--mobile-color-primary-strong, #075441);
  box-shadow: inset 3px 0 0 var(--mobile-color-primary, #0b6b53);
}

.enter-btn {
  background: var(--mobile-gradient-primary, linear-gradient(135deg, #0b6b53, #128064));
  box-shadow: var(--mobile-shadow-primary, 0 10px 24px rgba(11, 107, 83, 0.22));
}

@media (prefers-reduced-motion: no-preference) {
  .shop-hero {
    animation: mobile-topbar-enter 200ms var(--mobile-ease-spring, cubic-bezier(0.22, 1, 0.36, 1)) both;
  }

  .search-panel,
  .shop-tree-panel,
  .selected-panel {
    animation: mobile-surface-enter 240ms var(--mobile-ease-spring, cubic-bezier(0.22, 1, 0.36, 1)) both;
  }

  .shop-tree-panel { animation-delay: 36ms; }
  .selected-panel { animation-delay: 72ms; }
}

/* Desktop-only refinement aligned with the selected BossERP dashboard. */
@media screen and (min-width: 769px) {
  .select-shop-page.is-desktop-web {
    color: #1d1d1f;
    background: #ffffff;
    font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "PingFang SC", "Helvetica Neue", Arial, sans-serif;
  }

  .desktop-tea-backdrop {
    background:
      radial-gradient(circle at 12% 18%, rgba(82, 117, 101, 0.13), transparent 27%),
      radial-gradient(circle at 78% 12%, rgba(105, 96, 128, 0.11), transparent 25%),
      linear-gradient(90deg, rgba(255, 253, 249, 0.985) 0%, rgba(250, 252, 252, 0.92) 42%, rgba(250, 248, 253, 0.66) 67%, rgba(255, 251, 245, 0.84) 100%),
      url("~@/assets/images/desktop-login-tea-room-bg.jpg") center center / cover no-repeat;
  }

  .desktop-shop-shell {
    grid-template-columns: minmax(330px, 440px) minmax(480px, 560px);
    gap: clamp(48px, 5vw, 76px);
  }

  .desktop-shop-visual {
    color: #1d1d1f;
  }

  .desktop-shop-emblem {
    color: #6444c2;
    border-color: #d9ccfb;
    background: linear-gradient(145deg, #eef6ff 0%, #f5f0ff 100%);
    box-shadow: 0 12px 30px rgba(116, 86, 216, 0.14);
  }

  .desktop-shop-visual h1 {
    color: #1d1d1f;
    font-weight: 760;
    letter-spacing: -0.035em;
  }

  .desktop-shop-visual p {
    color: #56595f;
  }

  .desktop-shop-flow span {
    color: #515154;
    border-color: #d2d2d7;
    background: rgba(255, 255, 255, 0.84);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  }

  .desktop-shop-flow span:nth-of-type(1) {
    color: #0b7748;
    border-color: #bfead3;
    background: rgba(236, 251, 243, 0.98);
  }

  .desktop-shop-flow span:nth-of-type(2) {
    color: #6444c2;
    border-color: #d9ccfb;
    background: rgba(245, 240, 255, 0.98);
  }

  .desktop-shop-flow span:nth-of-type(3) {
    color: #9f5d00;
    border-color: #f2d39b;
    background: rgba(255, 247, 232, 0.98);
  }

  .desktop-shop-flow i {
    background: linear-gradient(90deg, #bfead3, #c9e0ff, #d9ccfb, #f2d39b);
  }

  .desktop-shop-card {
    border-color: #d6d9df;
    background: rgba(255, 255, 255, 0.985);
    box-shadow: 0 26px 68px rgba(38, 46, 56, 0.16);
  }

  .desktop-shop-head {
    background: #ffffff;
  }

  .desktop-shop-head,
  .desktop-shop-toolbar {
    border-bottom-color: #e5e5ea;
  }

  .desktop-shop-head h1,
  .desktop-current-info strong {
    color: #1d1d1f;
  }

  .desktop-shop-head h1 {
    font-weight: 760;
    letter-spacing: -0.03em;
  }

  .desktop-shop-head p,
  .business-context-note,
  .desktop-current-info span,
  .desktop-current-info small,
  .desktop-current-info em {
    color: #56595f;
  }

  .desktop-shop-count {
    color: #6444c2;
    border-color: #d9ccfb;
    background: linear-gradient(135deg, #eef6ff 0%, #f5f0ff 100%);
    box-shadow: 0 4px 11px rgba(116, 86, 216, 0.1);
  }

  .desktop-shop-toolbar ::v-deep .el-input__inner {
    color: #1d1d1f;
    border-color: #b9b9bf;
    background: #ffffff;
    box-shadow: none;
  }

  .desktop-shop-toolbar {
    background: #ffffff;
  }

  .desktop-shop-toolbar ::v-deep .el-input__inner::placeholder {
    color: #6f737a;
    -webkit-text-fill-color: #6f737a;
    opacity: 1;
  }

  .desktop-shop-toolbar ::v-deep .el-input__inner:hover {
    border-color: #8e8e93;
  }

  .desktop-shop-toolbar ::v-deep .el-input__inner:focus {
    border-color: #1473e6;
    background: #ffffff;
    box-shadow: 0 0 0 3px rgba(20, 115, 230, 0.2);
  }

  .refresh-icon-btn {
    color: #0b5fc2;
    border-color: #c9e0ff;
    background: #eef6ff;
    box-shadow: 0 1px 2px rgba(20, 115, 230, 0.08);
  }

  .refresh-icon-btn:hover,
  .refresh-icon-btn:focus {
    color: #ffffff;
    border-color: #1473e6;
    background: #1473e6;
  }

  .refresh-icon-btn ::v-deep .el-icon-refresh {
    transition: transform 180ms ease;
  }

  .refresh-icon-btn:hover ::v-deep .el-icon-refresh,
  .refresh-icon-btn:focus ::v-deep .el-icon-refresh {
    transform: rotate(90deg);
  }

  ::v-deep .desktop-shop-tree {
    color: #1d1d1f;
  }

  ::v-deep .desktop-shop-tree .el-tree-node__content {
    color: #1d1d1f;
    border-color: #e5e5ea;
    background: #ffffff;
    transition: color 180ms ease, border-color 180ms ease, background 180ms ease, box-shadow 180ms ease, transform 180ms ease;
  }

  ::v-deep .desktop-shop-tree .el-tree-node__content:hover {
    color: #0b5fc2;
    border-color: #c9e0ff;
    background: linear-gradient(90deg, #eef6ff 0%, #f5f0ff 100%);
    box-shadow: 0 6px 14px rgba(46, 60, 73, 0.075);
    transform: translateX(2px);
  }

  ::v-deep .desktop-shop-tree .el-tree-node.is-current > .el-tree-node__content {
    color: #0b5fc2;
    border-color: #c9e0ff;
    background: #eef6ff;
    box-shadow: inset 3px 0 0 #1473e6;
  }

  ::v-deep .context-tree-icon {
    color: #6444c2;
    border-color: #d9ccfb;
    background: #f5f0ff;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
    transition: transform 180ms ease, box-shadow 180ms ease;
  }

  ::v-deep .desktop-shop-tree .el-tree-node__content:hover .context-tree-icon {
    box-shadow: 0 4px 10px rgba(48, 60, 73, 0.1);
    transform: scale(1.06);
  }

  ::v-deep .context-tree-icon.is-store {
    color: #0b7748;
    border-color: #bfead3;
    background: #ecfbf3;
  }

  ::v-deep .context-tree-icon.is-warehouse {
    color: #0b5fc2;
    border-color: #c9e0ff;
    background: #eef6ff;
  }

  ::v-deep .context-tree-icon.is-company,
  ::v-deep .context-tree-icon.is-org {
    color: #6444c2;
    border-color: #d9ccfb;
    background: #f5f0ff;
  }

  ::v-deep .context-tree-icon.is-group {
    color: #9f5d00;
    border-color: #f2d39b;
    background: #fff7e8;
  }

  ::v-deep .context-tree-type,
  ::v-deep .context-tree-type.is-org {
    color: #6444c2;
    border-color: #d9ccfb;
    background: #f5f0ff;
  }

  ::v-deep .context-tree-type.is-store {
    color: #0b7748;
    border-color: #bfead3;
    background: #ecfbf3;
  }

  ::v-deep .context-tree-type.is-warehouse {
    color: #0b5fc2;
    border-color: #c9e0ff;
    background: #eef6ff;
  }

  ::v-deep .context-tree-type.is-company {
    color: #6444c2;
    border-color: #d9ccfb;
    background: #f5f0ff;
  }

  ::v-deep .context-tree-type.is-group {
    color: #9f5d00;
    border-color: #f2d39b;
    background: #fff7e8;
  }

  ::v-deep .context-tree-children {
    color: #666b73;
  }

  .desktop-shop-footer {
    border-top-color: #e5e5ea;
    background: #ffffff;
  }

  .desktop-shop-footer ::v-deep .el-button {
    color: #2c2c2e;
    border-color: #b9b9bf;
    background: #ffffff;
    border-radius: 8px;
    font-weight: 600;
  }

  .desktop-shop-footer ::v-deep .el-button:hover,
  .desktop-shop-footer ::v-deep .el-button:focus {
    color: #0b5fc2;
    border-color: #1473e6;
    background: #eef6ff;
  }

  .desktop-shop-footer ::v-deep .desktop-exit-btn {
    color: #b8324b;
    border-color: #f3c3cc;
    background: #fff0f3;
  }

  .desktop-shop-footer ::v-deep .desktop-exit-btn:hover,
  .desktop-shop-footer ::v-deep .desktop-exit-btn:focus {
    color: #a5263e;
    border-color: #cf3f5a;
    background: #ffe5eb;
  }

  .desktop-shop-footer ::v-deep .desktop-clear-btn {
    color: #9f5d00;
    border-color: #f2d39b;
    background: #fff7e8;
  }

  .desktop-shop-footer ::v-deep .desktop-clear-btn:hover,
  .desktop-shop-footer ::v-deep .desktop-clear-btn:focus {
    color: #8a4f00;
    border-color: #c47400;
    background: #ffefcf;
  }

  .desktop-shop-footer ::v-deep .el-button--primary {
    color: #ffffff;
    border-color: #1473e6;
    background: linear-gradient(135deg, #1473e6 0%, #7456d8 100%);
    box-shadow: 0 7px 16px rgba(20, 115, 230, 0.24);
  }

  .desktop-shop-footer ::v-deep .el-button--primary:hover,
  .desktop-shop-footer ::v-deep .el-button--primary:focus {
    color: #ffffff;
    border-color: #0b5fc2;
    background: linear-gradient(135deg, #0b5fc2 0%, #6444c2 100%);
    box-shadow: 0 9px 20px rgba(20, 115, 230, 0.3);
    transform: translateY(-1px);
  }

  .desktop-shop-footer ::v-deep .el-button {
    transition: color 180ms ease, border-color 180ms ease, background 180ms ease, box-shadow 180ms ease, transform 180ms ease;
  }

  .desktop-shop-footer ::v-deep .el-button:not(.el-button--primary):hover,
  .desktop-shop-footer ::v-deep .el-button:not(.el-button--primary):focus {
    transform: translateY(-1px);
  }

  .selection-summary__icon {
    color: #6444c2 !important;
    border-color: #d9ccfb !important;
    background: #f5f0ff !important;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
  }

  .selection-summary.is-store .selection-summary__icon {
    color: #0b7748 !important;
    border-color: #bfead3 !important;
    background: #ecfbf3 !important;
  }

  .selection-summary.is-warehouse .selection-summary__icon {
    color: #0b5fc2 !important;
    border-color: #c9e0ff !important;
    background: #eef6ff !important;
  }

  .selection-summary.is-company .selection-summary__icon {
    color: #6444c2 !important;
    border-color: #d9ccfb !important;
    background: #f5f0ff !important;
  }

  .selection-summary.is-group .selection-summary__icon {
    color: #9f5d00 !important;
    border-color: #f2d39b !important;
    background: #fff7e8 !important;
  }

  .desktop-current-info .selection-business-hint,
  .selection-business-hint {
    color: #515154;
  }
}

@keyframes desktop-shop-card-enter {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.992);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media screen and (min-width: 769px) and (prefers-reduced-motion: no-preference) {
  .desktop-shop-card {
    animation: desktop-shop-card-enter 240ms cubic-bezier(0.22, 1, 0.36, 1) both;
  }
}

@media (prefers-reduced-motion: reduce) {
  .desktop-shop-card,
  ::v-deep .desktop-shop-tree .el-tree-node__content,
  ::v-deep .context-tree-icon,
  .desktop-shop-footer ::v-deep .el-button,
  .refresh-icon-btn ::v-deep .el-icon-refresh {
    animation: none !important;
    transition: none !important;
    transform: none !important;
  }
}
</style>
