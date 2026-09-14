<template>
  <main class="mobile-hr-page mobile-system-page">
    <header class="mobile-hr-header">
      <button type="button" aria-label="返回" @click="$router.back()"><i class="el-icon-arrow-left" aria-hidden="true" /></button>
      <div><small>人事待办</small><h1>{{ pageTitle }}</h1></div>
      <button type="button" aria-label="刷新员工档案" @click="reload"><i class="el-icon-refresh" aria-hidden="true" /></button>
    </header>

    <div class="filter-chips">
      <span>{{ contextLabel }}</span><span v-if="affectedCount">待处理 {{ affectedCount }} 人</span>
    </div>
    <form class="employee-search" role="search" @submit.prevent="reload">
      <input v-model.trim="keyword" type="search" aria-label="搜索员工档案" placeholder="姓名 / 手机 / 员工号 / 岗位工号" />
      <button type="submit" :disabled="loading">搜索</button>
    </form>
    <p v-if="message" class="status-message">{{ message }}</p>
    <section v-if="rows.length" class="employee-list">
      <article v-for="row in rows" :key="row.userId" class="employee-card">
        <div class="employee-card-head">
          <div><h2>{{ row.employeeName || '-' }}</h2><p>{{ row.employeeNo || '-' }} · {{ row.positionNo || '-' }} · {{ row.phoneNumberMasked || '-' }}</p></div>
          <span>{{ row.employeeStatus || '-' }}</span>
        </div>
        <p>{{ deptText(row) }}</p>
        <p v-if="row.contractEndDate">合同到期：{{ row.contractEndDate }}</p>
        <div class="card-actions">
          <button type="button" :aria-label="`办理转正：${employeeAriaName(row)}`" @click="openLifecycle(row, 'REGULARIZE')" v-hasPermi="['hr:employee:regularize']">办理转正</button>
          <button type="button" :aria-label="`办理续签：${employeeAriaName(row)}`" @click="openLifecycle(row, 'RENEWAL')" v-hasPermi="['hr:employee:renewal']">办理续签</button>
          <button type="button" :aria-label="`编辑资料：${employeeAriaName(row)}`" @click="openEditor(row)" v-hasPermi="['hr:employee:edit']">编辑资料</button>
          <button v-if="offboardAccountOnly && row.userId && row.accountEnabled === true" type="button" class="danger" :aria-label="`停用账号：${employeeAriaName(row)}`" @click="disableAccount(row)" v-hasPermi="['system:user:edit']">停用账号</button>
        </div>
      </article>
    </section>
    <div v-else-if="!loading" class="empty-state">{{ emptyText }}</div>
    <button v-if="hasMore" type="button" class="load-more" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>

    <hr-employee-lifecycle-dialog :visible.sync="lifecycleOpen" :employee-id="lifecycleEmployeeId" :scenario="lifecycleScenario" @confirmed="handleLifecycleConfirmed" />
    <mobile-hr-profile-editor :visible="editorOpen" :detail="editing" :saving="saving" @close="closeEditor" @save="saveProfile" />
  </main>
</template>

<script>
import { getHrEmployee, listHrEmployees, updateHrEmployee } from "@/api/hr/employee"
import { changeUserStatus } from "@/api/system/user"
import { getSelectedDeptContext, getSelectedDeptId } from "@/utils/shopContext"
import MobileHrProfileEditor from "../components/MobileHrProfileEditor"
import { mobileHrErrorMessage } from "../mobileHrError"

const { normalizePositiveDecimalId } = require("@/utils/positiveDecimalId")

export default {
  name: "MobileHrEmployee",
  components: { MobileHrProfileEditor, HrEmployeeLifecycleDialog: () => import("@/views/hr/components/HrEmployeeLifecycleDialog") },
  data() {
    return {
      lifecycleOpen: false,
      lifecycleEmployeeId: "",
      lifecycleScenario: "REGULARIZE",
      rows: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      loading: false,
      message: "",
      keyword: "",
      editorOpen: false,
      editing: null,
      saving: false,
      routeFocusHandled: false,
      editorReadEpoch: 0,
      profileSaveSequence: 0,
      listReadEpoch: 0,
      editorTargetId: null,
      pageInactive: false,
      deptListenerBound: false
    }
  },
  computed: {
    offboardAccountOnly() { return this.$route.query.offboardAccountOnly === true || this.$route.query.offboardAccountOnly === "true" },
    contractDue() { return this.$route.query.contractDue === true || this.$route.query.contractDue === "true" },
    affectedCount() { return Number(this.$route.query.affectedCount || this.$route.query.count || 0) },
    pageTitle() { return this.offboardAccountOnly ? "离职账号处理" : this.contractDue ? "合同到期提醒" : "员工档案" },
    contextLabel() {
      const scopedDeptId = this.$route.query.deptId || this.$route.query.contextDeptId
      if (!scopedDeptId) return "数据范围：当前权限"
      const context = getSelectedDeptContext()
      return `数据范围：${context.deptName || "指定组织"}`
    },
    hasMore() { return this.rows.length < this.total },
    emptyText() {
      if (this.message) return "员工档案暂未加载"
      if (this.$route.query.userId !== undefined) return "未找到指定员工档案"
      return this.$route.query.todoType ? "事项已处理" : "暂无员工档案"
    }
  },
  created() {
    this.bindDeptListener()
    this.reload()
  },
  activated() {
    if (!this.pageInactive) return
    this.pageInactive = false
    this.bindDeptListener()
    this.reload()
  },
  deactivated() {
    this.pageInactive = true
    this.invalidatePageReads()
  },
  beforeDestroy() {
    this.pageInactive = true
    this.invalidatePageReads()
    this.unbindDeptListener()
  },
  watch: {
    "$store.state.user.sessionRevision"() { this.closeEditor(); this.reload() },
    "$route.fullPath"() {
      this.routeFocusHandled = false
      this.closeEditor()
      this.reload()
    }
  },
  methods: {
    profile(row) { return (row && row.profile) || row || {} },
    employeeAriaName(row) { return String(row && row.employeeName || "").trim() || "该员工" },
    deptText(row) { return [row && row.departmentName, row && row.positionName].filter(Boolean).join(" / ") || "组织岗位待补充" },
    query() {
      return {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        userId: normalizePositiveDecimalId(this.$route.query.userId) || undefined,
        keyword: this.keyword || undefined,
        contractDue: this.contractDue || undefined,
        offboardAccountOnly: this.offboardAccountOnly || undefined,
        employeeStatus: this.$route.query.employeeStatus || undefined,
        deptId: this.$route.query.deptId || this.$route.query.contextDeptId || undefined
      }
    },
    liveDeptId() {
      return getSelectedDeptId()
    },
    sameDeptId(deptId) {
      return String(this.liveDeptId() || "") === String(deptId || "")
    },
    isCurrentListRead(listEpoch, deptId) {
      return !this.pageInactive && listEpoch === this.listReadEpoch && this.sameDeptId(deptId)
    },
    isCurrentEditorRead(epoch, userId, deptId) {
      return !this.pageInactive
        && epoch === this.editorReadEpoch
        && this.editorTargetId === userId
        && this.sameDeptId(deptId)
    },
    invalidateEditorReads() {
      this.profileSaveSequence += 1
      this.saving = false
      this.editorReadEpoch += 1
    },
    invalidateListReads() {
      this.listReadEpoch += 1
      this.loading = false
    },
    invalidatePageReads() {
      this.invalidateEditorReads()
      this.invalidateListReads()
    },
    closeEditor() {
      this.invalidateEditorReads()
      this.editorTargetId = null
      this.editorOpen = false
    },
    bindDeptListener() {
      if (typeof window === "undefined" || this.deptListenerBound) return
      window.addEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = true
    },
    unbindDeptListener() {
      if (typeof window === "undefined" || !this.deptListenerBound) return
      window.removeEventListener("erp:dept-changed", this.handleDeptChanged)
      this.deptListenerBound = false
    },
    handleDeptChanged() {
      this.invalidatePageReads()
      if (this.pageInactive) return
      this.routeFocusHandled = false
      this.reload()
    },
    reload() {
      if (this.pageInactive) return Promise.resolve()
      this.listReadEpoch += 1
      this.pageNum = 1
      this.rows = []
      this.total = 0
      return this.load()
    },
    loadMore() { if (!this.loading && this.hasMore) return this.load(this.pageNum + 1) },
    load(requestedPage = this.pageNum) {
      if (this.pageInactive) return Promise.resolve()
      const listEpoch = this.listReadEpoch
      const deptId = this.liveDeptId()
      const params = { ...this.query(), pageNum: requestedPage }
      if (this.$route.query.userId !== undefined && !params.userId) {
        this.loading = false
        this.message = "员工标识无效，请返回后重新选择员工"
        return Promise.resolve()
      }
      this.loading = true
      this.message = ""
      return listHrEmployees(params, { silentError: true }).then(response => {
        if (!this.isCurrentListRead(listEpoch, deptId)) return
        const next = response.rows || []
        this.rows = requestedPage === 1 ? next : this.rows.concat(next)
        this.total = Number(response.total || this.rows.length)
        this.pageNum = requestedPage
        const userId = params.userId
        if (userId && requestedPage === 1 && !this.routeFocusHandled) {
          const focused = this.rows.find(row => normalizePositiveDecimalId(row.userId) === userId)
          if (focused) return this.openEditor(focused, true)
          this.message = "未找到指定员工，请核对当前组织或返回待办刷新"
        }
      }).catch(() => {
        if (!this.isCurrentListRead(listEpoch, deptId)) return
        this.message = "员工档案加载失败，请稍后重试"
      }).finally(() => {
        if (listEpoch === this.listReadEpoch) this.loading = false
      })
    },
    openLifecycle(row, scenario) {
      if (this.pageInactive) return
      const id = normalizePositiveDecimalId(row && row.userId)
      if (!id) return
      this.lifecycleEmployeeId = id
      this.lifecycleScenario = scenario
      this.lifecycleOpen = true
    },
    handleLifecycleConfirmed() {
      this.reload()
      if (this.$store && this.$store.dispatch) this.$store.dispatch("todo/refreshSummaries").catch(() => null)
    },
    openEditor(row, routeFocus = false) {
      if (this.pageInactive) return Promise.resolve(null)
      const userId = normalizePositiveDecimalId(row && row.userId)
      if (!userId) {
        this.closeEditor()
        this.message = "员工标识无效，请返回后重新选择员工"
        return Promise.resolve(null)
      }
      this.routeFocusHandled = true
      this.invalidateEditorReads()
      const epoch = this.editorReadEpoch
      const deptId = this.liveDeptId()
      this.editorTargetId = userId
      this.message = ""
      return getHrEmployee(userId, { silentError: true }).then(response => {
        if (!this.isCurrentEditorRead(epoch, userId, deptId)) return null
        this.editing = response.data || null
        this.editorOpen = Boolean(this.editing)
        if (!this.editing) {
          if (routeFocus) this.routeFocusHandled = false
          this.message = "未找到指定员工，请核对当前组织或返回待办刷新"
        }
        return this.editing
      }).catch(error => {
        if (!this.isCurrentEditorRead(epoch, userId, deptId)) return null
        if (routeFocus) this.routeFocusHandled = false
        this.message = mobileHrErrorMessage(error, "员工档案加载失败，请重试")
        return null
      })
    },
    saveProfile(payload) {
      const userId = normalizePositiveDecimalId(payload && payload.userId)
      if (this.saving || !this.editorOpen || !userId || userId !== this.editorTargetId) return Promise.resolve(null)
      const epoch = this.editorReadEpoch, deptId = this.liveDeptId(), sequence = ++this.profileSaveSequence
      const user = this.$store && this.$store.state && this.$store.state.user || {}
      const actor = String(user.id || ""), session = user.sessionRevision || 0
      const current = () => {
        const currentUser = this.$store && this.$store.state && this.$store.state.user || {}
        return this.editorOpen && this.isCurrentEditorRead(epoch, userId, deptId) && sequence === this.profileSaveSequence &&
          actor === String(currentUser.id || "") && session === (currentUser.sessionRevision || 0)
      }
      const patch = JSON.parse(JSON.stringify(payload.patch || {}))
      this.saving = true
      return updateHrEmployee(userId, patch).then(() => {
        if (!current()) return null
        this.$message.success("员工资料已保存")
        this.closeEditor()
        return Promise.all([this.reload(), this.$store.dispatch("todo/refreshSummaries")])
      }).catch(error => {
        if (current()) this.message = mobileHrErrorMessage(error, "员工资料保存失败，请重试")
      }).finally(() => { if (current()) this.saving = false })
    },
    disableAccount(row) {
      return this.$modal.confirm(`确认停用 ${row.employeeName || '该员工'} 的账号？`).then(() => changeUserStatus(row.userId, "1")).then(() => {
        this.$message.success("账号已停用")
        return Promise.all([this.reload(), this.$store.dispatch("todo/refreshSummaries")])
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.mobile-hr-page{min-height:100vh;padding:14px 14px calc(24px + env(safe-area-inset-bottom));background:#f1f5f9;color:#0f172a}.mobile-hr-header{display:grid;grid-template-columns:42px 1fr 42px;align-items:center;gap:8px}.mobile-hr-header button{height:38px;border:0;border-radius:12px;background:#fff;font-size:22px}.mobile-hr-header h1{margin:2px 0;font-size:22px}.mobile-hr-header small{color:#64748b}.filter-chips{display:flex;gap:8px;overflow:auto;margin:14px 0 10px}.filter-chips span{white-space:nowrap;padding:7px 10px;border-radius:999px;background:#dbeafe;color:#1d4ed8;font-size:13px}.employee-search{display:grid;grid-template-columns:minmax(0,1fr) 72px;gap:8px;margin-bottom:12px}.employee-search input,.employee-search button{height:42px;border-radius:11px}.employee-search input{min-width:0;padding:0 12px;border:1px solid #cbd5e1;background:#fff;color:#0f172a;font-size:14px}.employee-search button{border:0;background:#2563eb;color:#fff;font-weight:600}.employee-list{display:grid;gap:10px}.employee-card{padding:14px;border-radius:16px;background:#fff;box-shadow:0 4px 14px rgba(15,23,42,.06)}.employee-card-head{display:flex;justify-content:space-between;gap:12px}.employee-card h2{margin:0 0 4px;font-size:17px}.employee-card p{margin:5px 0;color:#64748b;font-size:13px}.employee-card-head>span{height:24px;padding:3px 8px;border-radius:999px;background:#fef3c7;color:#92400e;font-size:12px}.card-actions{display:flex;gap:8px;margin-top:12px}.card-actions button,.load-more{height:40px;border:0;border-radius:10px;background:#2563eb;color:#fff;font-weight:600}.card-actions button{flex:1}.card-actions .danger{background:#dc2626}.load-more{width:100%;margin-top:14px}.empty-state,.status-message{padding:24px;text-align:center;color:#64748b}.status-message{padding:10px;border-radius:10px;background:#fff7ed;color:#9a3412}
.mobile-hr-page{background:var(--mobile-color-page);color:var(--mobile-color-ink)}
.mobile-hr-header{grid-template-columns:44px 1fr 44px}
.mobile-hr-header button{width:44px;height:44px;color:var(--mobile-color-primary);background:var(--mobile-color-surface);font-size:20px}
.filter-chips span{background:var(--mobile-color-primary-soft);color:var(--mobile-color-primary)}
.employee-search input{height:44px;border-color:var(--mobile-color-line-strong);color:var(--mobile-color-ink);font-size:16px}
.employee-search button{height:44px;background:var(--mobile-color-primary)}
.employee-card{border:1px solid var(--mobile-color-line);border-radius:var(--mobile-radius-lg);box-shadow:none}
.card-actions button,.load-more{min-height:44px;height:44px;background:var(--mobile-color-primary)}
.card-actions .danger{color:var(--mobile-color-danger);border:1px solid rgba(196,50,43,.3);background:var(--mobile-color-danger-soft)}
.mobile-hr-page button:focus-visible,.mobile-hr-page input:focus-visible{outline:3px solid rgba(11,107,83,.24);outline-offset:2px}
</style>
