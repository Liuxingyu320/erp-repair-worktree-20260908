<template>
  <!-- 授权用户 -->
  <el-dialog title="选择用户" :visible.sync="visible" width="800px" top="5vh" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" :disabled="saving">
      <el-form-item label="用户名称" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户昵称" prop="nickName">
        <el-input
          v-model="queryParams.nickName"
          placeholder="请输入用户昵称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-row>
      <div v-if="loadError" role="alert">加载失败：{{ loadError }} <el-button type="text" :disabled="loading || saving" @click="getList">重新加载</el-button></div>
      <div v-if="submitError" role="alert">{{ submitError }}</div>
      <section aria-label="已选用户" class="selected-users">
        <span>已选 {{ selectedUsers.length }} 人</span>
        <el-button type="text" :disabled="saving || !selectedUsers.length" @click="clearSelectedUsers">清空选择</el-button>
        <div><el-tag v-for="user in selectedUsers" :key="getRowKey(user)" :closable="!saving" @close="removeSelectedUser(user)">{{ user.nickName || user.userName }} · {{ user.dept && user.dept.deptName || '未设置部门' }} · {{ user.userName }}</el-tag></div>
      </section>
      <el-table v-loading="loading" :row-key="getRowKey" @row-click="clickRow" ref="table" :data="userList" @selection-change="handleSelectionChange" height="260px">
        <el-table-column type="selection" :reserve-selection="true" :selectable="canSelectUser" width="55"></el-table-column>
        <el-table-column label="用户名称" prop="userName" :show-overflow-tooltip="true" />
        <el-table-column label="用户昵称" prop="nickName" :show-overflow-tooltip="true" />
        <el-table-column label="部门" prop="dept.deptName" :show-overflow-tooltip="true" />
        <el-table-column label="状态" align="center" prop="status">
          <template slot-scope="scope">
            <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status"/>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-row>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" :loading="saving" :disabled="loading || !!loadError || !selectedUsers.length" @click="handleSelectUser" v-hasPermi="['system:role:authUser']">确 定</el-button>
      <el-button :disabled="saving" @click="visible = false">取 消</el-button>
    </div>
  </el-dialog>
</template>

<script>

import { unallocatedUserList, authUserSelectAll } from "@/api/system/role"
import systemListRecovery from "@/mixins/systemListRecovery"
export default {
  mixins: [systemListRecovery],
  dicts: ['sys_normal_disable'],
  props: { roleId: { type: [Number, String] } },
  data() {
    return {
      visible: false, loading: false, saving: false, submitError: "", dialogGeneration: 0,
      userIds: [], selectedUsers: [], total: 0, userList: [],
      queryParams: { pageNum: 1, pageSize: 10, roleId: undefined, userName: undefined, nickName: undefined }
    }
  },
  watch: {
    roleId() { if (this.visible) this.show() },
    visible(value) {
      if (!value) { this.dialogGeneration += 1; this.invalidateSystemList() }
    }
  },
  methods: {
    show() {
      this.dialogGeneration += 1
      this.listPageActive = true
      this.saving = false
      this.submitError = ""
      this.clearSelectedUsers()
      this.userList = []
      this.queryParams = { pageNum: 1, pageSize: 10, roleId: this.roleId, userName: undefined, nickName: undefined }
      this.visible = true
      return this.getList()
    },
    getRowKey(row) { return String(row.userId) },
    canSelectUser() { return !this.saving && !this.loading && !this.loadError && this.isLoadedListContextCurrent() },
    clickRow(row) { if (this.canSelectUser()) this.$refs.table.toggleRowSelection(row) },
    handleSelectionChange(selection) {
      if (this.saving) return
      this.selectedUsers = selection.slice()
      this.userIds = selection.map(item => item.userId)
    },
    clearSelectedUsers() {
      if (this.saving) return
      if (this.$refs.table) this.$refs.table.clearSelection()
      this.selectedUsers = []
      this.userIds = []
    },
    removeSelectedUser(user) {
      if (this.saving) return
      this.$refs.table.toggleRowSelection(user, false)
    },
    getList() {
      if (!this.visible || this.saving) return Promise.resolve(null)
      if (this.listLoadedContext && !this.isLoadedListContextCurrent()) this.clearSelectedUsers()
      const query = { ...this.queryParams }
      return this.runSystemListRequest(() => unallocatedUserList(query, { silentError: true }), response => {
        if (!response || !Array.isArray(response.rows)) throw new Error("列表响应无效，请重试")
        const ids = response.rows.map(user => String(user.userId))
        if (ids.some(id => !/^[1-9][0-9]*$/.test(id)) || new Set(ids).size !== ids.length) throw new Error("用户数据无效，请重试")
        this.userList = response.rows
        this.total = Number(response.total) || 0
      })
    },
    handleQuery() { if (!this.saving) { this.queryParams.pageNum = 1; return this.getList() } },
    resetQuery() { if (!this.saving) { this.resetForm("queryForm"); return this.handleQuery() } },
    async handleSelectUser() {
      if (this.saving || this.loading || this.loadError || !this.visible) return
      if (!this.requireLoadedListContext()) return
      if (!this.userIds.length) { this.$modal.msgError("请选择要分配的用户"); return }
      const generation = this.dialogGeneration, requestId = this.listRequestId, context = this.listContext()
      const roleId = this.queryParams.roleId, userIds = this.userIds.slice().join(",")
      this.saving = true
      this.submitError = ""
      const current = () => this.visible && this.listPageActive && generation === this.dialogGeneration && requestId === this.listRequestId && context === this.listContext() && String(roleId) === String(this.roleId)
      try {
        const response = await authUserSelectAll({ roleId, userIds })
        if (!current()) return
        this.$modal.msgSuccess(response.msg || "授权成功")
        this.visible = false
        this.$emit("ok")
      } catch (error) {
        if (current()) this.submitError = error && error.message || "分配失败，已保留选择，请重试"
      } finally {
        if (generation === this.dialogGeneration && requestId === this.listRequestId) this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.selected-users { margin: 0 0 12px; }
.selected-users .el-tag { margin: 0 6px 6px 0; }
</style>
