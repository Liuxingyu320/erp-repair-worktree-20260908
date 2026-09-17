<template>
  <div class="app-container system-management-page">
    <system-page-header
      :title="roleHeaderTitle"
      :description="roleHeaderDescription"
      icon="el-icon-user-solid"
      :tip="roleHeaderTip"
    />
    <div v-show="showSearch" class="search-card role-member-search-card">
     <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="用户名称" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户昵称" prop="nickName">
        <el-input
          v-model="queryParams.nickName"
          placeholder="请输入用户昵称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card role-member-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="openSelectUser"
          v-hasPermi="['system:role:authUser']"
        >添加用户</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-circle-close"
          size="mini"
          :disabled="multiple"
          @click="cancelAuthUserAll"
          v-hasPermi="['system:role:authUser']"
        >批量取消授权</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-close"
          size="mini"
          @click="handleClose"
        >关闭</el-button>
      </el-col>
      <el-col v-if="roleLoadError" :span="1.5">
        <el-button type="text" size="mini" :disabled="loading" @click="loadRole(queryParams.roleId)">重试角色信息</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    </div>

    <div class="table-card role-member-table-card">
    <div v-if="loadError" role="alert" class="system-list-error"><span>加载失败：{{ loadError }}</span> <el-button type="text" :disabled="loading" @click="getList">重新加载</el-button></div>
    <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="用户名称" prop="userName" :show-overflow-tooltip="true" />
      <el-table-column label="用户昵称" prop="nickName" :show-overflow-tooltip="true" />
      <el-table-column label="部门" prop="dept.deptName" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-circle-close"
            @click="cancelAuthUser(scope.row)"
            v-hasPermi="['system:role:authUser']"
          >取消授权</el-button>
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
    </div>
    <select-user ref="select" :roleId="queryParams.roleId" @ok="handleQuery" />
  </div>
</template>

<script>
import systemListRecovery from "@/mixins/systemListRecovery"
import { allocatedUserList, authUserCancel, authUserCancelAll, getRole } from "@/api/system/role"
import selectUser from "./selectUser"

export default {
  mixins: [systemListRecovery],
  name: "AuthUser",
  dicts: ['sys_normal_disable'],
  components: { selectUser },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中用户组
      userIds: [],
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 用户表格数据
      userList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        roleId: undefined,
        userName: undefined,
        nickName: undefined
      },
      role: null,
      roleLoadError: ""
    }
  },
  computed: {
    roleHeaderTitle() {
      if (this.role && this.role.roleName) return `${this.role.roleName} · 角色成员`
      return "角色成员"
    },
    roleHeaderDescription() {
      if (this.roleLoadError) return this.roleLoadError
      if (!this.role) return "正在读取角色信息。"
      const status = this.role.status === "1" ? "已停用" : "正常"
      const scope = this.roleDataScopeLabel(this.role.dataScope)
      return `当前角色：${this.role.roleName} · 状态 ${status} · 数据范围 ${scope}`
    },
    roleHeaderTip() {
      const name = this.role && this.role.roleName ? `「${this.role.roleName}」` : "当前角色"
      return `成员变更会影响其菜单权限和数据访问范围。正在配置${name}。`
    }
  },
  watch: {
    "$route.params.roleId": { immediate: true, handler(roleId) {
      this.queryParams.roleId = roleId
      this.userIds = []
      this.role = null
      this.roleLoadError = ""
      if (roleId) {
        this.loadRole(roleId)
        this.getList()
      }
      else { this.loading = false; this.loadError = "角色不存在，请返回重试" }
    } }
  },
  methods: {
    loadRole(roleId) {
      this.roleLoadError = ""
      return getRole(roleId).then(response => {
        this.role = (response && response.data) || null
        if (!this.role || !this.role.roleName) {
          this.roleLoadError = "角色信息暂未加载，可重试后继续分配成员。"
        }
      }).catch(() => {
        this.role = null
        this.roleLoadError = "角色信息暂未加载，可重试后继续分配成员。"
      })
    },
    roleDataScopeLabel(value) {
      return {
        "1": "全部数据",
        "2": "自定义部门",
        "3": "本部门",
        "4": "本部门及以下",
        "5": "仅本人"
      }[value] || "未设置"
    },
    roleConfirmName() {
      return this.role && this.role.roleName ? this.role.roleName : "当前角色"
    },
    /** 查询授权用户列表 */
    getList() {
      const query = { ...this.queryParams }
      return this.runSystemListRequest(() => allocatedUserList(query, { silentError: true }), response => {
        if (!response || !Array.isArray(response.rows)) throw new Error("列表响应无效，请重试")
        this.userList = response.rows
        this.total = Number(response.total) || 0
      })
    },
    // 返回按钮
    handleClose() {
      const obj = { path: "/system/role" }
      this.$tab.closeOpenPage(obj)
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.userIds = selection.map(item => item.userId)
      this.multiple = !selection.length
    },
    /** 打开授权用户表弹窗 */
    openSelectUser() {
      this.$refs.select.show()
    },
    /** 取消授权按钮操作 */
    cancelAuthUser(row) {
      const roleId = this.queryParams.roleId
      this.$modal.confirm(`确认取消用户「${row.userName || row.nickName || row.userId}」在角色「${this.roleConfirmName()}」的授权吗？`).then(function() {
        return authUserCancel({ userId: row.userId, roleId: roleId })
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("取消授权成功")
      }).catch(() => {})
    },
    /** 批量取消授权按钮操作 */
    cancelAuthUserAll() {
      const roleId = this.queryParams.roleId
      const userIds = this.userIds.join(",")
      this.$modal.confirm(`确认取消选中的 ${this.userIds.length} 个用户在角色「${this.roleConfirmName()}」的授权吗？`).then(function() {
        return authUserCancelAll({ roleId: roleId, userIds: userIds })
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("取消授权成功")
      }).catch(() => {})
    }
  }
}
</script>
