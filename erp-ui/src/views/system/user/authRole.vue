<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="分配角色"
      description="为当前用户配置职责角色；已有关联的停用角色可解除，不能新增关联。"
      icon="el-icon-s-check"
      tip="保存后请确认用户重新登录或刷新权限状态。"
    />
    <div class="content-card auth-role-user-card">
    <h4 class="form-header h4">基本信息</h4>
    <el-form ref="form" :model="form" label-width="80px">
      <el-row>
        <el-col :span="8" :offset="2">
          <el-form-item label="用户昵称" prop="nickName">
            <el-input v-model="form.nickName" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="8" :offset="2">
          <el-form-item label="登录账号" prop="userName">
            <el-input v-model="form.userName" disabled />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    </div>

    <div class="table-card auth-role-table-card">
    <h4 class="form-header h4">角色信息</h4>
    <div v-if="loadError" role="alert">加载失败：{{ loadError }} <el-button type="text" :disabled="loading || saving" @click="getList">重新加载</el-button></div>
    <div v-if="submitError" role="alert">{{ submitError }}</div>
    <el-table v-loading="loading" :row-key="getRowKey" @row-click="clickRow" ref="table" @selection-change="handleSelectionChange" :data="roles.slice((pageNum-1)*pageSize,pageNum*pageSize)">
      <el-table-column label="序号" type="index" align="center">
        <template slot-scope="scope">
          <span>{{ (pageNum - 1) * pageSize + scope.$index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column type="selection" :reserve-selection="true" :selectable="checkSelectable" width="55" />
      <el-table-column label="角色编号" align="center" prop="roleId" />
      <el-table-column label="角色名称" align="center" prop="roleName" />
      <el-table-column label="状态" align="center"><template slot-scope="scope"><span>{{ scope.row.status === '0' ? '正常' : initiallyAssigned(scope.row) ? '停用（原关联可解除）' : '停用（不可新增）' }}</span></template></el-table-column>
      <el-table-column label="权限字符" align="center" prop="roleKey" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="pageNum" :limit.sync="pageSize" />
    </div>

    <div class="content-card system-form-actions">
        <el-button type="primary" :loading="saving" :disabled="loading || !!loadError || !loaded" @click="submitForm()" v-hasPermi="['system:user:edit']">提交</el-button>
        <el-button :disabled="saving" @click="close()">返回</el-button>
    </div>
  </div>
</template>

<script>

import { getAuthRole, updateAuthRole } from "@/api/system/user"
import systemListRecovery from "@/mixins/systemListRecovery"
export default {
  name: "AuthRole",
  mixins: [systemListRecovery],
  data() {
    return { loading: true, saving: false, loaded: false, submitError: "", loadedUserId: "",
      total: 0, pageNum: 1, pageSize: 10, roleIds: [], originalRoleIds: [], roles: [], form: {}, selectionSyncing: false }
  },
  watch: {
    "$route.params.userId": { immediate: true, handler() { this.getList() } }
  },
  methods: {
    getList() {
      const userId = this.$route.params && this.$route.params.userId
      this.loaded = false
      this.saving = false
      this.submitError = ""
      return this.runSystemListRequest(() => {
        if (!/^[1-9][0-9]*$/.test(String(userId || ''))) throw new Error("用户不存在，请返回重试")
        return getAuthRole(userId, { silentError: true })
      }, response => {
        if (!response || !response.user || String(response.user.userId) !== String(userId) || !Array.isArray(response.roles)) throw new Error("用户角色响应不一致，请重新加载")
        const ids = response.roles.map(role => String(role.roleId))
        if (ids.some(id => !/^[1-9][0-9]*$/.test(id)) || new Set(ids).size !== ids.length) throw new Error("角色数据无效，请重新加载")
        this.selectionSyncing = true
        this.form = response.user
        this.roles = response.roles
        this.total = this.roles.length
        this.pageNum = 1
        this.originalRoleIds = this.roles.filter(role => role.flag).map(role => String(role.roleId))
        this.roleIds = this.roles.filter(role => role.flag).map(role => role.roleId)
        this.loadedUserId = String(userId)
        const requestId = this.listRequestId
        this.$nextTick(() => {
          if (!this.listPageActive || requestId !== this.listRequestId || this.loadedUserId !== String(this.$route.params.userId)) return
          if (this.$refs.table) {
            this.$refs.table.clearSelection()
            this.roles.filter(role => role.flag).forEach(role => this.$refs.table.toggleRowSelection(role, true))
          }
          this.selectionSyncing = false
          this.loaded = true
        })
      })
    },
    initiallyAssigned(row) { return this.originalRoleIds.includes(String(row.roleId)) },
    clickRow(row) { if (this.checkSelectable(row)) this.$refs.table.toggleRowSelection(row) },
    handleSelectionChange(selection) {
      if (this.selectionSyncing || this.loading || this.saving || !this.loaded) return
      this.roleIds = selection.filter(row => row.status === "0" || this.initiallyAssigned(row)).map(row => row.roleId)
    },
    getRowKey(row) { return String(row.roleId) },
    checkSelectable(row) {
      return !this.loading && !this.saving && !this.loadError && this.loaded && this.isLoadedListContextCurrent() && (row.status === "0" || this.initiallyAssigned(row))
    },
    async submitForm() {
      if (this.saving || this.loading || this.loadError || !this.loaded || this.loadedUserId !== String(this.$route.params.userId)) return
      if (!this.requireLoadedListContext()) return
      const userId = this.form.userId, roleIds = this.roleIds.slice().join(",")
      const requestId = this.listRequestId, context = this.listContext()
      const current = () => this.listPageActive && requestId === this.listRequestId && context === this.listContext() && String(this.form.userId) === String(userId)
      this.saving = true
      this.submitError = ""
      try {
        await updateAuthRole({ userId, roleIds })
        if (!current()) return
        this.$modal.msgSuccess("授权成功")
        this.$tab.closeOpenPage({ path: "/system/user" })
      } catch (error) {
        if (current()) this.submitError = error && error.message || "保存失败，已保留选择，请重试"
      } finally {
        if (requestId === this.listRequestId) this.saving = false
      }
    },
    close() { if (!this.saving) this.$tab.closeOpenPage({ path: "/system/user" }) }
  }
}
</script>
