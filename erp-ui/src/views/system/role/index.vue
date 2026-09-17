<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="角色管理"
      description="配置角色权限、数据范围与成员分配，让职责边界清晰可追溯。"
      icon="el-icon-s-custom"
    />
    <div v-show="showSearch" class="search-card role-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="角色名称" prop="roleName">
        <el-input
          v-model="queryParams.roleName"
          placeholder="请输入角色名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="权限字符" prop="roleKey">
        <el-input
          v-model="queryParams.roleKey"
          placeholder="请输入权限字符"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="角色状态"
          clearable
          style="width: 240px"
        >
          <el-option
            v-for="dict in dict.type.sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card role-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          ref="addButton"
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-if="canAddRole"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-if="canEditRole"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:role:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:role:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    </div>

    <div class="table-card role-table-card">
    <div v-if="loadError" role="alert" class="system-list-error"><span>加载失败：{{ loadError }}</span> <el-button type="text" :disabled="loading" @click="getList">重新加载</el-button></div>
    <el-table v-accessible-table="'角色列表'" v-loading="loading" :data="roleList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="角色编号" prop="roleId" width="120" />
      <el-table-column label="角色名称" prop="roleName" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="权限字符" prop="roleKey" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="数据范围" align="center" min-width="150">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.dataScope === '1' ? 'danger' : 'info'">
            {{ dataScopeLabel(scope.row.dataScope) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已授权用户" prop="userCount" align="center" width="110">
        <template slot-scope="scope">{{ scope.row.userCount || 0 }} 人</template>
      </el-table-column>
      <el-table-column label="显示顺序" prop="roleSort" width="100" />
      <el-table-column label="状态" align="center" width="100">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            :aria-label="`${scope.row.status === '0' ? '停用' : '启用'}角色：${scope.row.roleName}`"
            active-value="0"
            inactive-value="1"
            :disabled="!$auth.hasPermi('system:role:edit')"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="250" class-name="small-padding fixed-width">
        <template slot-scope="scope" v-if="scope.row.roleId !== 1">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-if="canEditRole"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:role:remove']"
          >删除</el-button>
          <el-dropdown v-if="canCopyRole || $auth.hasPermi('system:role:authUser') || $auth.hasPermi('system:salary:role')" size="mini" @command="(command) => handleCommand(command, scope.row)">
            <el-button size="mini" type="text" icon="el-icon-d-arrow-right">更多</el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item v-if="canCopyRole" command="handleCopy" icon="el-icon-document-copy">复制角色</el-dropdown-item>
              <el-dropdown-item command="handleAuthUser" icon="el-icon-user"
                v-hasPermi="['system:role:authUser']">分配用户</el-dropdown-item>
              <el-dropdown-item command="handleSalaryConfig" icon="el-icon-money"
                v-hasPermi="['system:salary:role']">历史薪资方案</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
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

    <!-- 添加或修改角色配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="760px" append-to-body @close="invalidateRoleLoad">
      <el-alert
        v-if="copySource"
        :title="`复制自「${copySource.roleName}」：将复制菜单和数据范围，不复制用户授权与薪资配置。`"
        type="info"
        :closable="false"
        show-icon
        class="role-copy-alert"
      />
      <el-alert v-if="roleLoadFailed" title="角色权限加载失败，请重试后保存" type="error" :closable="false"><el-button type="text" @click="retryRoleLoad">重新加载</el-button></el-alert>
      <el-form ref="form" v-loading="roleLoading" :disabled="!roleReady || roleSaving" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item prop="roleKey">
          <span slot="label">
            <el-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasRole('admin')`)" placement="top">
              <i class="el-icon-question"></i>
            </el-tooltip>
            权限字符
          </span>
          <el-input v-model="form.roleKey" placeholder="请输入权限字符" />
        </el-form-item>
        <el-form-item label="角色顺序" prop="roleSort">
          <el-input-number v-model="form.roleSort" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in dict.type.sys_normal_disable"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单权限">
          <el-checkbox v-model="menuExpand" @change="handleCheckedTreeExpand($event, 'menu')">展开/折叠</el-checkbox>
          <el-checkbox v-model="menuNodeAll" @change="handleCheckedTreeNodeAll($event, 'menu')">全选/全不选</el-checkbox>
          <el-checkbox v-model="form.menuCheckStrictly" @change="handleCheckedTreeConnect($event, 'menu')">父子联动</el-checkbox>
          <el-tree
            class="tree-border"
            :data="menuOptions"
            show-checkbox
            ref="menu"
            node-key="id"
            :check-strictly="!form.menuCheckStrictly"
            empty-text="加载中，请稍候"
            :props="defaultProps"
          ></el-tree>
        </el-form-item>
        <el-divider content-position="left">数据权限</el-divider>
        <el-alert
          title="新增或修改角色时会同时保存数据范围，避免角色短暂处于全量权限状态。"
          type="warning"
          :closable="false"
          show-icon
          class="role-scope-alert"
        />
        <el-form-item label="权限范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%" @change="dataScopeSelectChange">
            <el-option
              v-for="item in dataScopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-show="form.dataScope === '2'" label="授权部门" prop="deptIds">
          <div class="tree-actions">
            <el-checkbox v-model="deptExpand" @change="handleCheckedTreeExpand($event, 'dept')">展开/折叠</el-checkbox>
            <el-checkbox v-model="deptNodeAll" @change="handleCheckedTreeNodeAll($event, 'dept')">全选/全不选</el-checkbox>
            <el-checkbox v-model="form.deptCheckStrictly" @change="handleCheckedTreeConnect($event, 'dept')">父子联动</el-checkbox>
          </div>
          <el-tree
            class="tree-border role-dept-tree"
            :data="deptOptions"
            show-checkbox
            ref="dept"
            node-key="id"
            :check-strictly="!form.deptCheckStrictly"
            empty-text="当前数据范围内没有可授权部门"
            :props="defaultProps"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :disabled="!roleReady || roleLoading" :loading="roleSaving" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 分配角色数据权限对话框 -->
    <el-dialog :title="title" :visible.sync="openDataScope" width="500px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" :disabled="true" />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input v-model="form.roleKey" :disabled="true" />
        </el-form-item>
        <el-form-item label="权限范围">
          <el-select v-model="form.dataScope" @change="dataScopeSelectChange">
            <el-option
              v-for="item in dataScopeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="数据权限" v-show="form.dataScope == 2">
          <el-checkbox v-model="deptExpand" @change="handleCheckedTreeExpand($event, 'dept')">展开/折叠</el-checkbox>
          <el-checkbox v-model="deptNodeAll" @change="handleCheckedTreeNodeAll($event, 'dept')">全选/全不选</el-checkbox>
          <el-checkbox v-model="form.deptCheckStrictly" @change="handleCheckedTreeConnect($event, 'dept')">父子联动</el-checkbox>
          <el-tree
            class="tree-border"
            :data="deptOptions"
            show-checkbox
            default-expand-all
            ref="dept"
            node-key="id"
            :check-strictly="!form.deptCheckStrictly"
            empty-text="加载中，请稍候"
            :props="defaultProps"
          ></el-tree>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitDataScope">确 定</el-button>
        <el-button @click="cancelDataScope">取 消</el-button>
      </div>
    </el-dialog>

    <role-wizard :visible.sync="roleWizardOpen" @created="handleRoleCreated" @closed="restoreAddButtonFocus" />

  </div>
</template>

<script>
import systemListRecovery from "@/mixins/systemListRecovery"
import { listRole, getRole, delRole, addRole, updateRole, dataScope, changeRoleStatus, deptTreeSelect } from "@/api/system/role"
import { treeselect as menuTreeselect, roleMenuTreeselect } from "@/api/system/menu"
import { confirmExportAction } from "@/utils/exportConfirm"
import RoleWizard from "./components/RoleWizard.vue"

export default {
  name: "Role",
  mixins: [systemListRecovery],
  components: { RoleWizard },
  dicts: ['sys_normal_disable'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 角色表格数据
      roleList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      roleLoadEpoch: 0,
      roleLoading: false,
      roleReady: false,
      roleLoadFailed: false,
      roleLoadTarget: null,
      roleSaving: false,
      // 是否显示弹出层（数据权限）
      openDataScope: false,
      roleWizardOpen: false,
      menuExpand: false,
      menuNodeAll: false,
      deptExpand: true,
      deptNodeAll: false,
      // 日期范围
      dateRange: [],
      // 数据范围选项
      dataScopeOptions: [
        {
          value: "1",
          label: "全部数据权限"
        },
        {
          value: "2",
          label: "自定数据权限"
        },
        {
          value: "3",
          label: "本部门数据权限"
        },
        {
          value: "4",
          label: "本部门及以下数据权限"
        },
        {
          value: "5",
          label: "仅本人数据权限"
        }
      ],
      // 菜单列表
      menuOptions: [],
      // 部门列表
      deptOptions: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        roleName: undefined,
        roleKey: undefined,
        status: undefined
      },
      // 表单参数
      form: {},
      defaultProps: {
        children: "children",
        label: "label"
      },
      // 表单校验
      rules: {
        roleName: [
          { required: true, message: "角色名称不能为空", trigger: "blur" }
        ],
        roleKey: [
          { required: true, message: "权限字符不能为空", trigger: "blur" }
        ],
        roleSort: [
          { required: true, message: "角色顺序不能为空", trigger: "blur" }
        ],
        dataScope: [
          { required: true, message: "请选择数据权限范围", trigger: "change" }
        ]
      }
    }
  },
  computed: {
    canAddRole() {
      return this.$auth.hasPermiAnd(["system:role:add", "system:role:dataScope"])
    },
    canEditRole() {
      return this.$auth.hasPermiAnd(["system:role:edit", "system:role:dataScope"])
    },
    canCopyRole() {
      return this.canAddRole && this.$auth.hasPermi("system:role:query")
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询角色列表 */
    getList() {
      const query = this.addDateRange({ ...this.queryParams }, this.dateRange)
      return this.runSystemListRequest(() => listRole(query, { silentError: true }), response => {
        if (!response || !Array.isArray(response.rows)) throw new Error("列表响应无效，请重试")
        this.roleList = response.rows
        this.total = Number(response.total) || 0
      })
    },
    /** 查询菜单树结构 */
    getMenuTreeselect() {
      return menuTreeselect().then(response => {
        this.menuOptions = response.data
        return response
      })
    },
    // 所有菜单节点数据
    getMenuAllCheckedKeys() {
      // 目前被选中的菜单节点
      let checkedKeys = this.$refs.menu.getCheckedKeys()
      // 半选中的菜单节点
      let halfCheckedKeys = this.$refs.menu.getHalfCheckedKeys()
      checkedKeys.unshift.apply(checkedKeys, halfCheckedKeys)
      return checkedKeys
    },
    // 所有部门节点数据
    getDeptAllCheckedKeys() {
      if (!this.$refs.dept) return []
      // 目前被选中的部门节点
      let checkedKeys = this.$refs.dept.getCheckedKeys()
      // 半选中的部门节点
      let halfCheckedKeys = this.$refs.dept.getHalfCheckedKeys()
      checkedKeys.unshift.apply(checkedKeys, halfCheckedKeys)
      return checkedKeys
    },
    /** 根据角色ID查询菜单树结构 */
    getRoleMenuTreeselect(roleId) {
      return roleMenuTreeselect(roleId).then(response => {
        this.menuOptions = response.menus
        return response
      })
    },
    /** 根据角色ID查询部门树结构 */
    getDeptTree(roleId) {
      return deptTreeSelect(roleId).then(response => {
        this.deptOptions = response.depts
        return response
      })
    },
    // 角色状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "启用" : "停用"
      this.$modal.confirm('确认要将角色「' + row.roleName + '」' + text + '吗？').then(function() {
        return changeRoleStatus(row.roleId, row.status)
      }).then(() => {
        this.$modal.msgSuccess(text + "成功")
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0"
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      if (this.$refs.menu != undefined) {
        this.$refs.menu.setCheckedKeys([])
      }
      this.menuExpand = false,
      this.menuNodeAll = false,
      this.deptExpand = true,
      this.deptNodeAll = false,
      this.copySource = null
      this.form = {
        roleId: undefined,
        roleName: undefined,
        roleKey: undefined,
        roleSort: 0,
        dataScope: "5",
        status: "0",
        menuIds: [],
        deptIds: [],
        menuCheckStrictly: true,
        deptCheckStrictly: true,
        remark: undefined
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.roleId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleCopy":
          this.handleCopy(row)
          break
        case "handleAuthUser":
          this.handleAuthUser(row)
          break
        case "handleSalaryConfig":
          this.handleSalaryConfig(row)
          break
        default:
          break
      }
    },
    // 树权限（展开/折叠）
    handleCheckedTreeExpand(value, type) {
      if (type == 'menu') {
        let treeList = this.menuOptions
        for (let i = 0; i < treeList.length; i++) {
          this.$refs.menu.store.nodesMap[treeList[i].id].expanded = value
        }
      } else if (type == 'dept') {
        let treeList = this.deptOptions
        for (let i = 0; i < treeList.length; i++) {
          this.$refs.dept.store.nodesMap[treeList[i].id].expanded = value
        }
      }
    },
    // 树权限（全选/全不选）
    handleCheckedTreeNodeAll(value, type) {
      if (type == 'menu') {
        this.$refs.menu.setCheckedNodes(value ? this.menuOptions: [])
      } else if (type == 'dept') {
        this.$refs.dept.setCheckedNodes(value ? this.deptOptions: [])
      }
    },
    // 树权限（父子联动）
    handleCheckedTreeConnect(value, type) {
      if (type == 'menu') {
        this.form.menuCheckStrictly = value ? true: false
      } else if (type == 'dept') {
        this.form.deptCheckStrictly = value ? true: false
      }
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.roleWizardOpen = true
    },
    handleRoleCreated(role) {
      this.getList()
      this.$modal.msgSuccess("角色创建成功")
      if (!role || !role.roleId) return
      this.$modal.confirm(
        `角色「${role.roleName}」已创建，是否现在添加成员？`,
        "角色创建完成",
        {
          confirmButtonText: "去添加成员",
          cancelButtonText: "稍后处理",
          type: "success"
        }
      ).then(() => {
        this.$router.push("/system/role-auth/user/" + role.roleId)
      }).catch(() => {})
    },
    restoreAddButtonFocus() {
      this.$nextTick(() => {
        const button = this.$refs.addButton && this.$refs.addButton.$el
        if (button && typeof button.focus === "function") button.focus()
      })
    },
    invalidateRoleLoad() {
      this.roleLoadEpoch += 1
      this.roleReady = false
      this.roleLoading = false
    },
    retryRoleLoad() {
      if (this.roleLoadTarget) this.loadRoleEditor(this.roleLoadTarget.id, this.roleLoadTarget.copy)
    },
    async loadRoleEditor(roleId, copy = false) {
      this.reset()
      const epoch = ++this.roleLoadEpoch
      this.roleLoadTarget = { id: roleId, copy }
      this.roleReady = false
      this.roleLoading = true
      this.roleLoadFailed = false
      this.open = true
      this.title = copy ? "复制角色" : "修改角色"
      try {
        const [role, menu, dept] = await Promise.all([
          getRole(roleId), roleMenuTreeselect(roleId), deptTreeSelect(roleId)
        ])
        if (epoch !== this.roleLoadEpoch || !this.open) return
        if (!role.data || !Array.isArray(menu.menus) || !Array.isArray(menu.checkedKeys) ||
          !Array.isArray(dept.depts) || !Array.isArray(dept.checkedKeys)) throw new Error("角色权限响应不完整")
        const source = role.data
        this.form = { ...source }
        if (copy) {
          this.copySource = { roleId: source.roleId, roleName: source.roleName }
          this.form = {
            ...source, roleId: undefined, roleName: `${source.roleName}（副本）`,
            roleKey: `${source.roleKey}-copy`, status: "1",
            createBy: undefined, createTime: undefined, updateBy: undefined, updateTime: undefined
          }
        }
        this.menuOptions = menu.menus
        this.deptOptions = dept.depts
        await this.$nextTick()
        if (epoch !== this.roleLoadEpoch || !this.open) return
        this.$refs.menu.setCheckedKeys([])
        ;(menu.checkedKeys || []).forEach(key => this.$refs.menu.setChecked(key, true, false))
        this.$refs.dept.setCheckedKeys(dept.checkedKeys || [])
        this.roleReady = true
      } catch (error) {
        if (epoch === this.roleLoadEpoch && this.open) this.roleLoadFailed = true
      } finally {
        if (epoch === this.roleLoadEpoch) this.roleLoading = false
      }
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      return this.loadRoleEditor(row.roleId || this.ids[0])
    },
    /** 选择角色权限范围触发 */
    dataScopeSelectChange(value) {
      if(value !== '2' && this.$refs.dept) {
        this.$refs.dept.setCheckedKeys([])
      }
    },
    /** 复制角色；用户授权和薪资配置属于独立关系，不参与复制。 */
    handleCopy(row) {
      return this.loadRoleEditor(row.roleId, true)
    },
    /** 分配用户操作 */
    handleAuthUser(row) {
      const roleId = row.roleId
      this.$router.push("/system/role-auth/user/" + roleId)
    },
    /** 角色薪资配置 */
    handleSalaryConfig(row) {
      this.$router.push({ path: "/system/salary", query: { roleId: row.roleId } })
    },
    /** 提交按钮 */
    submitForm() {
      if (!this.roleReady || this.roleLoading || this.roleSaving) return
      const epoch = this.roleLoadEpoch
      this.$refs.form.validate(valid => {
        if (!valid || !this.roleReady || epoch !== this.roleLoadEpoch || this.roleSaving) return
        const payload = { ...this.form, menuIds: this.getMenuAllCheckedKeys(),
          deptIds: this.form.dataScope === "2" ? this.getDeptAllCheckedKeys() : [] }
        if (payload.dataScope === "2" && payload.deptIds.length === 0) {
          this.$modal.msgWarning("自定义数据权限至少选择一个部门")
          return
        }
        this.roleSaving = true
        const save = payload.roleId != null ? updateRole : addRole
        save(payload).then(() => {
          this.$modal.msgSuccess(payload.roleId != null ? "修改成功" : "复制成功")
          if (epoch === this.roleLoadEpoch) this.open = false
          this.getList()
        }).catch(() => {}).finally(() => { this.roleSaving = false })
      })
    },
    dataScopeLabel(value) {
      const option = this.dataScopeOptions.find(item => item.value === value)
      return option ? option.label : "未设置"
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const roleIds = row.roleId || this.ids
      this.$modal.confirm(this.getRoleDeleteConfirmText(row)).then(function() {
        return delRole(roleIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    getRoleDeleteConfirmText(row) {
      if (row && row.roleId) {
        return `确认删除角色「${row.roleName || row.roleKey || row.roleId}」吗？`
      }
      return `确认删除选中的 ${this.ids.length} 个角色吗？`
    },
    /** 导出按钮操作 */
    handleExport() {
      confirmExportAction(this, {
        moduleName: "角色数据",
        rangeLabel: "当前查询条件下的角色数据",
        filterLabel: this.buildManagementExportFilterLabel("角色名称", this.queryParams.roleName)
      }).then(() => {
        this.download(
          'system/role/export',
          this.addDateRange({ ...this.queryParams }, this.dateRange),
          this.exportFileName('角色数据', this.dateRange)
        )
      })
    },
    buildManagementExportFilterLabel(primaryLabel, primaryValue) {
      const range = this.dateRange && this.dateRange.length === 2 ? this.dateRange.join(" 至 ") : "全部"
      return `${primaryLabel}：${primaryValue || "全部"}；日期：${range}`
    }
  }
}
</script>

<style lang="scss" scoped>
.role-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.role-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
}

.role-table-card {
  padding-bottom: 8px;
}

.role-copy-alert,
.role-scope-alert {
  margin-bottom: 16px;
}

.tree-actions {
  margin-bottom: 8px;
}

.role-dept-tree {
  max-height: 260px;
  overflow: auto;
}

</style>
