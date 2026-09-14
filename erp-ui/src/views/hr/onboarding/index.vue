<template>
  <div class="app-container hr-onboarding-page">
    <div class="hr-onboarding-heading">
      <h2>入职管理</h2>
      <div class="hr-onboarding-heading__actions">
        <el-button icon="el-icon-upload2" @click="importVisible = true" v-hasPermi="['hr:onboarding:import:preview']">
          导入
        </el-button>
        <el-button type="primary" icon="el-icon-circle-plus-outline" @click="openCreate" v-hasPermi="['hr:onboarding:add']">
          新建入职
        </el-button>
      </div>
    </div>

    <div class="hr-onboarding-workbench-scroll">
      <div class="hr-onboarding-workbench hr-onboarding-workbench--reference">
        <hr-onboarding-list-pane
          :query="query"
          :rows="rows"
          :total="total"
          :loading="listLoading"
          :error="listError"
          :selected-id="selectedOnboardingId"
          :options="formOptions"
          :can-select="canSelect"
          :can-query="canQuery"
          @query-change="handleQueryChange"
          @select="selectOnboarding"
          @retry="loadList"
        />

        <section class="hr-onboarding-detail-shell">
          <div v-if="!selectedOnboardingId" class="empty-selection">
            <div class="empty-selection__icon"><i class="el-icon-user" /></div>
            <h3>{{ canQuery ? "选择一条入职记录" : "暂无详情查看权限" }}</h3>
            <p v-if="canQuery">从左侧列表查看资料完整度、缺失项、风险与操作记录。</p>
            <p v-else>你仍可使用左侧筛选和分页；如需查看人员详情，请联系管理员开通权限。</p>
            <el-button type="primary" plain icon="el-icon-plus" @click="openCreate" v-hasPermi="['hr:onboarding:add']">
              新建入职
            </el-button>
          </div>
          <div v-else-if="detailLoading" class="detail-state detail-state--loading" v-loading="true">
            <span>正在加载入职详情…</span>
          </div>
          <div v-else-if="detailError" class="detail-state detail-state--error">
            <i class="el-icon-warning-outline" />
            <h3>详情加载失败</h3>
            <p>{{ detailError }}</p>
            <el-button type="primary" plain @click="retryDetail">重试</el-button>
          </div>
          <hr-onboarding-detail-pane
            v-else-if="detail"
            :detail="detail"
            :linked-employee-profile="linkedEmployeeProfile"
            @action="handleAction"
          />
        </section>
      </div>
    </div>

    <hr-onboarding-create-dialog
      :visible.sync="createVisible"
      :options="formOptions"
      @created="handleCreated"
    />
    <hr-onboarding-edit-drawer
      :visible.sync="editVisible"
      :detail="detail || {}"
      :options="formOptions"
      @saved="handleRecordChanged"
      @version-conflict="handleVersionConflict"
    />
    <hr-onboarding-confirm-dialog
      :visible.sync="confirmVisible"
      :detail="detail || {}"
      @confirmed="handleConfirmed"
      @version-conflict="handleVersionConflict"
    />
    <hr-onboarding-import-dialog
      :visible.sync="importVisible"
      @completed="handleImportCompleted"
    />
  </div>
</template>

<script>
import {
  cancelHrOnboarding,
  getHrOnboarding,
  getHrOnboardingFormOptions,
  listHrOnboarding,
  markHrOnboardingReady,
  restoreHrOnboarding,
  returnHrOnboardingToDraft
} from "@/api/hr/onboarding"
import HrOnboardingListPane from "./components/HrOnboardingListPane"
import HrOnboardingDetailPane from "./components/HrOnboardingDetailPane"
import HrOnboardingCreateDialog from "./components/HrOnboardingCreateDialog"
import HrOnboardingEditDrawer from "./components/HrOnboardingEditDrawer"
import HrOnboardingConfirmDialog from "./components/HrOnboardingConfirmDialog"
import HrOnboardingImportDialog from "./components/HrOnboardingImportDialog"
import { isOnboardingVersionConflict } from "./onboardingFieldConfig"
import { checkPermi } from "@/utils/permission"
import { getHrEmployee } from "@/api/hr/employee"
import { getSelectedDeptId } from "@/utils/shopContext"
const { createUiOperationScope } = require("@/utils/uiOperationScope")

const { normalizePositiveDecimalId } = require("@/utils/positiveDecimalId")
const positiveId = value => normalizePositiveDecimalId(value) || undefined
const defaultQuery = (route = {}) => ({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  status: ["DRAFT", "READY", "CONFIRMED", "CANCELLED"].includes(String(route.status || "").toUpperCase())
    ? String(route.status).toUpperCase()
    : "DRAFT",
  expectedEntryDateFrom: undefined,
  expectedEntryDateTo: undefined,
  targetDeptId: positiveId(route.targetDeptId || route.contextDeptId),
  targetStoreId: undefined,
  employeeCategory: undefined,
  ownerUserId: undefined,
  onboardingId: undefined
})

export default {
  name: "HrOnboarding",
  components: {
    HrOnboardingListPane,
    HrOnboardingDetailPane,
    HrOnboardingCreateDialog,
    HrOnboardingEditDrawer,
    HrOnboardingConfirmDialog,
    HrOnboardingImportDialog
  },
  data() {
    const route = this.$route && this.$route.query ? this.$route.query : {}
    return {
      query: defaultQuery(route),
      rows: [],
      total: 0,
      listLoading: false,
      listError: "",
      listRequestSequence: 0,
      selectedOnboardingId: undefined,
      detail: null,
      linkedEmployeeProfile: null,
      detailLoading: false,
      detailError: "",
      detailRequestSequence: 0,
      formOptions: {},
      createVisible: false,
      editVisible: false,
      confirmVisible: false,
      importVisible: false,
      confirmRefreshPending: false,
      actionLoading: false,
      pendingRouteOnboardingId: undefined
    }
  },
  created() {
    if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this.handleContextChanged)
    this.applyRouteDeepLink()
    this.loadFormOptions()
    this.loadList()
  },
  beforeDestroy() {
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.handleContextChanged)
    this.operationScope().deactivate()
    this.listRequestSequence += 1
    this.detailRequestSequence += 1
  },
  deactivated() { this.operationScope().deactivate(); this.actionLoading = false },
  activated() { this.operationScope().activate() },
  watch: {
    "$store.state.user.sessionRevision"() { this.handleContextChanged() },
    "$route.fullPath"() { this.operationScope().invalidate(); this.actionLoading = false },
    confirmVisible(value) {
      this.handleConfirmVisibilityChange(value)
    },
    "$route.query.onboardingId"(value, previous) {
      if (value === previous) return
      if (!this.validRouteOnboardingId() && !this.query.onboardingId) return
      this.applyRouteDeepLink()
      this.loadList()
    }
  },
  computed: {
    canQuery() {
      return checkPermi(["hr:onboarding:query"])
    },
    canSelect() {
      return this.canQuery
    }
  },
  methods: {
    operationScope() {
      if (!this._operationScope) this._operationScope = createUiOperationScope(() => {
        const user = this.$store && this.$store.state && this.$store.state.user || {}
        return { actor: String(user.id || ""), session: user.sessionRevision || 0,
          dept: String(getSelectedDeptId() || ""), route: this.$route && this.$route.fullPath }
      })
      return this._operationScope
    },
    handleContextChanged() {
      this.operationScope().invalidate()
      this.clearSelection()
      this.editVisible = false; this.confirmVisible = false
      return this.loadList()
    },
    loadFormOptions() {
      return getHrOnboardingFormOptions()
        .then(response => { this.formOptions = response.data || {} })
        .catch(() => { this.formOptions = {} })
    },
    loadList() {
      const requestSequence = ++this.listRequestSequence
      this.listLoading = true
      this.listError = ""
      return listHrOnboarding({ ...this.query })
        .then(response => {
          if (requestSequence !== this.listRequestSequence) return
          this.rows = Array.isArray(response.rows) ? response.rows : []
          this.total = Number(response.total) || 0
          if (this.selectedOnboardingId && !this.rows.some(row => positiveId(row.onboardingId) === this.selectedOnboardingId)) {
            this.clearSelection()
          }
          const routeId = this.pendingRouteOnboardingId
          this.pendingRouteOnboardingId = undefined
          const routeRow = routeId && this.rows.find(row => positiveId(row.onboardingId) === routeId)
          if (routeRow) return this.selectOnboarding(routeRow)
          if (this.canSelect && this.rows.length && !this.selectedOnboardingId) return this.autoSelectFirstRow()
        })
        .catch(error => {
          if (requestSequence !== this.listRequestSequence) return
          this.rows = []
          this.total = 0
          this.clearSelection()
          this.listError = (error && error.message) || "暂时无法获取入职记录"
        })
        .finally(() => {
          if (requestSequence === this.listRequestSequence) this.listLoading = false
        })
    },
    applyRouteDeepLink() {
      const onboardingId = this.validRouteOnboardingId()
      this.pendingRouteOnboardingId = onboardingId
      if (onboardingId) this.query = { ...this.query, pageNum: 1, onboardingId, status: undefined }
      else if (this.query.onboardingId) {
        const route = this.$route && this.$route.query ? this.$route.query : {}
        const routeDefaults = defaultQuery(route)
        this.query = {
          ...this.query,
          pageNum: 1,
          onboardingId: undefined,
          status: routeDefaults.status,
          targetDeptId: routeDefaults.targetDeptId
        }
      }
      return onboardingId
    },
    validRouteOnboardingId() {
      const raw = this.$route && this.$route.query ? this.$route.query.onboardingId : undefined
      return positiveId(Array.isArray(raw) ? raw[0] : raw)
    },
    removeRouteOnboardingId() {
      if (!this.$route || !this.$route.query || this.$route.query.onboardingId === undefined || !this.$router) return
      const routeQuery = { ...this.$route.query }
      delete routeQuery.onboardingId
      Promise.resolve(this.$router.replace({ query: routeQuery })).catch(() => {})
    },
    handleQueryChange(patch) {
      this.clearSelection()
      this.pendingRouteOnboardingId = undefined
      this.query = { ...this.query, ...patch, onboardingId: undefined }
      this.removeRouteOnboardingId()
      return this.loadList()
    },
    selectOnboarding(row) {
      if (!this.canQuery || !this.canSelect || !row || !positiveId(row.onboardingId)) return
      this.operationScope().invalidate("state-action")
      this.operationScope().invalidate("state-confirm")
      this.actionLoading = false
      this.selectedOnboardingId = positiveId(row.onboardingId)
      this.detail = null
      return this.loadDetail(positiveId(row.onboardingId))
    },
    autoSelectFirstRow() {
      return this.rows.length ? this.selectOnboarding(this.rows[0]) : Promise.resolve(null)
    },
    loadDetail(onboardingId) {
      onboardingId = positiveId(onboardingId)
      if (!onboardingId) return Promise.resolve(null)
      const requestSequence = ++this.detailRequestSequence
      this.detailLoading = true
      this.detailError = ""
      this.linkedEmployeeProfile = null
      return getHrOnboarding(onboardingId)
        .then(response => {
          if (requestSequence !== this.detailRequestSequence || onboardingId !== this.selectedOnboardingId) return
          const detail = response.data || null
          if (detail && positiveId(detail.onboardingId) !== onboardingId) throw new Error("入职记录响应不匹配")
          this.detail = detail
          if (!this.detail) this.detailError = "未找到该入职记录，记录可能已被删除或超出当前数据范围"
          if (this.detail) return this.loadLinkedEmployeeProfile(this.detail, requestSequence, onboardingId)
          return null
        })
        .catch(error => {
          if (requestSequence !== this.detailRequestSequence || onboardingId !== this.selectedOnboardingId) return
          this.detail = null
          this.detailError = (error && error.message) || "暂时无法获取入职详情"
        })
        .finally(() => {
          if (requestSequence === this.detailRequestSequence && onboardingId === this.selectedOnboardingId) {
            this.detailLoading = false
          }
        })
    },
    loadLinkedEmployeeProfile(detail, requestSequence, onboardingId) {
      const linkedUserId = positiveId(detail && detail.linkedUserId)
      if (!linkedUserId || !checkPermi(["hr:employee:query"])) {
        return Promise.resolve(null)
      }
      return getHrEmployee(detail.linkedUserId).then(response => {
        if (requestSequence !== this.detailRequestSequence || onboardingId !== this.selectedOnboardingId) return null
        this.linkedEmployeeProfile = response.data || null
        return this.linkedEmployeeProfile
      }).catch(() => {
        if (requestSequence === this.detailRequestSequence && onboardingId === this.selectedOnboardingId) {
          this.linkedEmployeeProfile = null
        }
        return null
      })
    },
    retryDetail() {
      if (this.selectedOnboardingId) this.loadDetail(this.selectedOnboardingId)
    },
    clearSelection() {
      this.operationScope().invalidate("state-action")
      this.operationScope().invalidate("state-confirm")
      this.actionLoading = false
      this.detailRequestSequence += 1
      this.selectedOnboardingId = undefined
      this.detail = null
      this.linkedEmployeeProfile = null
      this.detailLoading = false
      this.detailError = ""
    },
    openCreate() {
      this.createVisible = true
    },
    handleCreated(created) {
      const onboardingId = positiveId(created && created.onboardingId)
      this.createVisible = false
      return this.loadList().then(() => {
        if (onboardingId) return this.selectOnboarding({ onboardingId })
        return null
      })
    },
    handleRecordChanged(updated) {
      this.editVisible = false
      if (updated && positiveId(updated.onboardingId) === this.selectedOnboardingId) this.detail = updated
      return this.loadList().then(() => {
        if (this.selectedOnboardingId) return this.loadDetail(this.selectedOnboardingId)
        return null
      })
    },
    handleConfirmed() {
      this.confirmRefreshPending = true
      return Promise.resolve()
    },
    handleImportCompleted() {
      return this.loadList()
    },
    handleConfirmVisibilityChange(value) {
      if (value) return Promise.resolve(null)
      return this.flushConfirmRefresh()
    },
    flushConfirmRefresh() {
      if (!this.confirmRefreshPending) return Promise.resolve(null)
      this.confirmRefreshPending = false
      const activeId = this.selectedOnboardingId
      return this.loadList().then(() => {
        if (activeId && this.selectedOnboardingId === activeId && this.rows.some(row => positiveId(row.onboardingId) === activeId)) {
          return this.loadDetail(activeId)
        }
        return null
      })
    },
    handleVersionConflict({ onboardingId } = {}) {
      this.editVisible = false
      this.confirmVisible = false
      return this.refreshAfterVersionConflict(onboardingId)
    },
    refreshAfterVersionConflict(onboardingId) {
      const activeId = positiveId(onboardingId || this.selectedOnboardingId)
      if (activeId !== this.selectedOnboardingId) return Promise.resolve(null)
      return this.loadList().then(() => {
        if (activeId && activeId === this.selectedOnboardingId && this.rows.some(row => positiveId(row.onboardingId) === activeId)) {
          this.selectedOnboardingId = activeId
          return this.loadDetail(activeId)
        }
        return null
      })
    },
    actionAllowed(key) {
      return Boolean(this.detail && Array.isArray(this.detail.allowedActions) && this.detail.allowedActions.includes(key))
    },
    performStateAction(request, payload, successMessage) {
      if (!this.detail || this.actionLoading) return Promise.resolve(null)
      const onboardingId = positiveId(this.detail.onboardingId)
      if (!onboardingId || onboardingId !== this.selectedOnboardingId || payload.version !== this.detail.version) return Promise.resolve(null)
      const scope = this.operationScope()
      const target = { onboardingId, version: payload.version }
      const operation = scope.begin("state-action", target)
      const current = () => scope.isCurrent(operation, { onboardingId: this.selectedOnboardingId,
        version: this.detail && this.detail.version })
      const stillSelected = () => scope.isCurrent(operation) && onboardingId === this.selectedOnboardingId
      this.actionLoading = true
      return request(onboardingId, { ...payload })
        .then(response => {
          if (!current()) return null
          const updated = response && response.data ? response.data : response
          if (!updated || positiveId(updated.onboardingId) !== onboardingId) throw new Error("操作结果暂未确认，请刷新当前记录核对")
          this.detail = updated
          if (this.$message) this.$message.success(successMessage)
          return this.loadList().then(() => {
            if (stillSelected()) return this.loadDetail(onboardingId)
            return updated
          })
        })
        .catch(error => {
          if (!stillSelected()) return null
          if (isOnboardingVersionConflict(error)) {
            if (this.$message) this.$message.warning("入职单已更新，正在加载最新版本")
            return this.refreshAfterVersionConflict(onboardingId)
          }
          if (this.$message) this.$message.error((error && error.message) || "操作失败，请刷新后重试")
          return null
        })
        .finally(() => { if (stillSelected()) this.actionLoading = false })
    },
    handleAction(value) {
      const action = typeof value === "string" ? value : value && value.key
      if (!this.actionAllowed(action)) return Promise.resolve(null)
      if (action === "EDIT") {
        this.editVisible = true
        return Promise.resolve()
      }
      if (action === "CONFIRM") {
        this.confirmVisible = true
        return Promise.resolve()
      }
      const version = this.detail.version
      if (action === "MARK_READY") {
        return this.performStateAction(markHrOnboardingReady, { version }, "已标记为资料就绪")
      }
      if (action === "RETURN_TO_DRAFT") {
        return this.performStateAction(returnHrOnboardingToDraft, { version }, "已退回草稿")
      }
      if (action === "RESTORE") {
        return this.performStateAction(restoreHrOnboarding, { version }, "已恢复入职单")
      }
      if (action === "CANCEL") {
        const target = { onboardingId: positiveId(this.detail.onboardingId), version }
        const scope = this.operationScope(), confirmation = scope.begin("state-confirm", target)
        return this.$prompt("请输入取消原因", "取消入职", {
          confirmButtonText: "确认取消",
          cancelButtonText: "暂不取消",
          inputType: "textarea",
          inputValidator: input => Boolean(input && input.trim()),
          inputErrorMessage: "取消原因不能为空"
        }).then(({ value: reason }) => {
          if (!scope.isCurrent(confirmation, { onboardingId: this.selectedOnboardingId,
              version: this.detail && this.detail.version }) || !this.actionAllowed("CANCEL")) return null
          return this.performStateAction(cancelHrOnboarding, { version, reason: reason.trim() }, "已取消入职单")
        }).catch(() => null)
      }
      return Promise.resolve(null)
    }
  }
}
</script>

<style lang="scss" scoped>
.hr-onboarding-page {
  color: #1e293b;
}

.hr-onboarding-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    font-size: 24px;
    line-height: 1.4;
  }

}

.hr-onboarding-heading__actions {
  display: flex;
  align-items: center;
  gap: 12px;

  .el-button + .el-button { margin-left: 0; }
}

.hr-onboarding-workbench {
  display: grid;
  grid-template-columns: minmax(390px, 35%) minmax(0, 65%);
  height: calc(100vh - 180px);
  min-height: 560px;
  min-width: 960px;
  border: 1px solid #e2e8f0;
  border-radius: 0;
  background: #fff;
}

.hr-onboarding-workbench-scroll {
  max-width: 100%;
  overflow-x: auto;
  margin-right: -20px;
  margin-left: -20px;
  padding-bottom: 0;
}

.hr-onboarding-detail-shell {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}

.empty-selection,
.detail-state {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
  color: #64748b;

  h3 {
    margin: 14px 0 6px;
    color: #334155;
  }

  p {
    max-width: 420px;
    margin: 0 0 18px;
    line-height: 1.7;
  }
}

.empty-selection__icon {
  width: 68px;
  height: 68px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: #e0edff;
  color: #2563eb;
  font-size: 30px;
}

.detail-state--loading {
  min-height: 320px;
}

.detail-state--error > i {
  color: #f59e0b;
  font-size: 42px;
}

@media (max-width: 1180px) {
  .hr-onboarding-workbench {
    grid-template-columns: minmax(360px, 37%) minmax(0, 63%);
  }
}
</style>
