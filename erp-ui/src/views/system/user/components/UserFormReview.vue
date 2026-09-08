<template>
  <div class="user-form-review">
    <el-alert
      :title="isCreate ? '请确认账号、组织和权限范围；创建后临时密码只显示一次。' : '以下仅展示本次实际发生的关键变更。'"
      :type="highRiskChanges.length ? 'warning' : 'info'"
      :closable="false"
      show-icon
    />
    <el-descriptions :column="2" border class="review-summary">
      <el-descriptions-item label="姓名">{{ form.nickName || "-" }}</el-descriptions-item>
      <el-descriptions-item label="登录账号">{{ form.userName || "-" }}</el-descriptions-item>
      <el-descriptions-item label="归属部门">{{ departmentLabel }}</el-descriptions-item>
      <el-descriptions-item label="账号状态">{{ form.status === "0" ? "正常" : "停用" }}</el-descriptions-item>
      <el-descriptions-item label="岗位">{{ postLabels }}</el-descriptions-item>
      <el-descriptions-item label="角色与数据范围">{{ roleLabels }}</el-descriptions-item>
      <el-descriptions-item label="直属主管">{{ supervisorLabel }}</el-descriptions-item>
      <el-descriptions-item label="员工状态">{{ profile.employeeStatus || "-" }}</el-descriptions-item>
      <el-descriptions-item label="首次改密">{{ isCreate || form.mustChangePassword === "1" ? "必须修改" : "无需强制修改" }}</el-descriptions-item>
    </el-descriptions>
    <div v-if="!isCreate" class="change-list">
      <h4>本次关键变更（{{ changes.length }}）</h4>
      <el-empty v-if="!changes.length" description="关键配置没有变化" :image-size="72" />
      <el-table v-else :data="changes" size="mini" border>
        <el-table-column label="项目" prop="label" width="130" />
        <el-table-column label="原值" prop="before" show-overflow-tooltip />
        <el-table-column label="新值" prop="after" show-overflow-tooltip />
        <el-table-column label="风险" width="90" align="center">
          <template slot-scope="scope"><el-tag size="mini" :type="scope.row.risk ? 'danger' : 'info'">{{ scope.row.risk ? "高" : "一般" }}</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
export default {
  name: "UserFormReview",
  props: {
    form: { type: Object, required: true },
    original: { type: Object, default: null },
    roleOptions: { type: Array, default: () => [] },
    postOptions: { type: Array, default: () => [] },
    deptOptions: { type: Array, default: () => [] }
  },
  computed: {
    profile() { return this.form.profile || {} },
    isCreate() { return !this.form.userId },
    departmentLabel() { return this.findDeptLabel(this.deptOptions, this.form.deptId) || "-" },
    postLabels() { return this.labelsByIds(this.postOptions, this.form.postIds, "postId", "postName") || "未选择（可稍后配置）" },
    roleLabels() {
      const scope = { "1": "全部", "2": "自定义", "3": "本部门", "4": "本部门及以下", "5": "仅本人" }
      const selected = this.roleOptions.filter(item => (this.form.roleIds || []).some(id => String(id) === String(item.roleId)))
      return selected.map(item => `${item.roleName}·${scope[item.dataScope] || "未设置"}`).join("；") || "未选择"
    },
    supervisorLabel() { return this.profile.directSupervisor || "-" },
    changes() {
      if (!this.original) return []
      const originalProfile = this.original.profile || {}
      const items = [
        this.change("归属部门", this.findDeptLabel(this.deptOptions, this.original.deptId), this.departmentLabel, true),
        this.change("角色", this.labelsByIds(this.roleOptions, this.original.roleIds, "roleId", "roleName"), this.labelsByIds(this.roleOptions, this.form.roleIds, "roleId", "roleName"), true),
        this.change("岗位", this.labelsByIds(this.postOptions, this.original.postIds, "postId", "postName"), this.postLabels, false),
        this.change("账号状态", this.original.status === "0" ? "正常" : "停用", this.form.status === "0" ? "正常" : "停用", true),
        this.change("员工状态", originalProfile.employeeStatus, this.profile.employeeStatus, true),
        this.change("直属主管", originalProfile.directSupervisor, this.profile.directSupervisor, true),
        this.change("手机号", this.original.phonenumber, this.form.phonenumber, false),
        this.change("邮箱", this.original.email, this.form.email, false)
      ]
      return items.filter(Boolean)
    },
    highRiskChanges() { return this.changes.filter(item => item.risk) }
  },
  methods: {
    normalize(value) { return value === undefined || value === null || value === "" ? "-" : String(value) },
    change(label, before, after, risk) {
      const left = this.normalize(before)
      const right = this.normalize(after)
      return left === right ? null : { label, before: left, after: right, risk }
    },
    labelsByIds(options, ids, idKey, labelKey) {
      return (options || []).filter(item => (ids || []).some(id => String(id) === String(item[idKey]))).map(item => item[labelKey]).join("、")
    },
    findDeptLabel(nodes, id) {
      for (const node of nodes || []) {
        if (String(node.id) === String(id)) return node.deptPath || node.label
        const child = this.findDeptLabel(node.children, id)
        if (child) return child
      }
      return ""
    }
  }
}
</script>

<style lang="scss" scoped>
.review-summary { margin-top: 16px; }
.change-list { margin-top: 18px; }
.change-list h4 { margin: 0 0 10px; }
</style>
