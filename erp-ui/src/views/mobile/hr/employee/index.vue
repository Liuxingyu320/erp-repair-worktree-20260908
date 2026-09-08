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
          <button type="button" :aria-label="`编辑资料：${employeeAriaName(row)}`" @click="openEditor(row)" v-hasPermi="['hr:employee:edit']">编辑资料</button>
          <button v-if="offboardAccountOnly && row.userId && row.accountEnabled === true" type="button" class="danger" :aria-label="`停用账号：${employeeAriaName(row)}`" @click="disableAccount(row)" v-hasPermi="['system:user:edit']">停用账号</button>
        </div>
      </article>
    </section>
    <div v-else-if="!loading" class="empty-state">{{ emptyText }}</div>
    <button v-if="hasMore" type="button" class="load-more" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</button>

    <mobile-hr-profile-editor :visible="editorOpen" :detail="editing" :saving="saving" @close="editorOpen=false" @save="saveProfile" />
  </main>
</template>

<script>
import { getHrEmployee, listHrEmployees, updateHrEmployee } from "@/api/hr/employee"
import { changeUserStatus } from "@/api/system/user"
import { getSelectedDeptContext } from "@/utils/shopContext"
import MobileHrProfileEditor from "../components/MobileHrProfileEditor"
import { mobileHrErrorMessage } from "../mobileHrError"

export default {
  name: "MobileHrEmployee",
  components: { MobileHrProfileEditor },
  data() {
    return { rows: [], total: 0, pageNum: 1, pageSize: 10, loading: false, message: "", keyword: "", editorOpen: false, editing: null, saving: false, routeFocusHandled: false }
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
    emptyText() { return this.$route.query.todoType ? "事项已处理" : "暂无员工档案" }
  },
  created() { this.reload() },
  watch: { "$route.fullPath"() { this.routeFocusHandled = false; this.reload() } },
  methods: {
    profile(row) { return (row && row.profile) || row || {} },
    employeeAriaName(row) { return String(row && row.employeeName || "").trim() || "该员工" },
    deptText(row) { return [row && row.departmentName, row && row.positionName].filter(Boolean).join(" / ") || "组织岗位待补充" },
    query() {
      return {
        pageNum: this.pageNum,
        pageSize: this.pageSize,
        keyword: this.keyword || undefined,
        contractDue: this.contractDue || undefined,
        offboardAccountOnly: this.offboardAccountOnly || undefined,
        employeeStatus: this.$route.query.employeeStatus || undefined,
        deptId: this.$route.query.deptId || this.$route.query.contextDeptId || undefined
      }
    },
    reload() { this.pageNum = 1; this.rows = []; return this.load() },
    loadMore() { if (!this.loading && this.hasMore) { this.pageNum += 1; this.load() } },
    load() {
      this.loading = true
      this.message = ""
      return listHrEmployees(this.query()).then(response => {
        const next = response.rows || []
        this.rows = this.pageNum === 1 ? next : this.rows.concat(next)
        this.total = Number(response.total || this.rows.length)
        const userId = String(this.$route.query.userId || "")
        if (userId && this.pageNum === 1 && !this.routeFocusHandled) {
          this.routeFocusHandled = true
          const focused = this.rows.find(row => String(row.userId || "") === userId)
          if (focused) this.openEditor(focused)
          else { this.message = "事项已处理"; this.$store.dispatch("todo/refreshSummaries").catch(() => {}) }
        }
      }).catch(() => { this.message = "员工档案加载失败，请稍后重试" }).finally(() => { this.loading = false })
    },
    openEditor(row) {
      const userId = Number(row && row.userId)
      if (!Number.isSafeInteger(userId) || userId <= 0) return Promise.resolve(null)
      this.message = ""
      return getHrEmployee(userId).then(response => {
        this.editing = response.data || null
        this.editorOpen = Boolean(this.editing)
        return this.editing
      }).catch(error => {
        this.message = mobileHrErrorMessage(error, "员工档案加载失败，请重试")
        return null
      })
    },
    saveProfile(payload) {
      this.saving = true
      return updateHrEmployee(payload.userId, payload.patch).then(() => {
        this.$message.success("员工资料已保存")
        this.editorOpen = false
        return Promise.all([this.reload(), this.$store.dispatch("todo/refreshSummaries")])
      }).catch(error => {
        this.message = mobileHrErrorMessage(error, "员工资料保存失败，请重试")
      }).finally(() => { this.saving = false })
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
