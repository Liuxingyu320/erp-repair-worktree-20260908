<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="部门管理"
      description="管理公司、区域、部门、门店与仓库层级，维护负责人及业务归属。"
      icon="el-icon-office-building"
      tip="组织层级会影响人员归属、数据范围与业务单据。"
    />
    <div v-show="showSearch" class="search-card dept-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="部门名称" prop="deptName">
        <el-input
          v-model="queryParams.deptName"
          placeholder="请输入部门名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="部门状态" clearable>
          <el-option
            v-for="dict in dict.type.sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="deptType">
        <el-select v-model="queryParams.deptType" placeholder="部门类型" clearable>
          <el-option
            v-for="item in deptTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card dept-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['system:dept:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-check"
          size="mini"
          :disabled="!hasPendingSortChanges || sortSaving"
          :loading="sortSaving"
          @click="handleSaveSort"
          v-hasPermi="['system:dept:edit']"
        >{{ hasPendingSortChanges ? `保存排序（已修改 ${pendingSortCount} 项）` : "保存排序" }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-sort"
          size="mini"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="handleSortRefresh"></right-toolbar>
    </el-row>
    <el-alert v-if="sortSaveError" :title="sortSaveError" type="warning" :closable="false" show-icon class="sort-save-alert" />
    </div>

    <div class="table-card dept-table-card">
    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="deptList"
      row-key="deptId"
      :default-expand-all="isExpandAll"
      :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
    >
      <el-table-column prop="deptName" label="部门名称" width="260"></el-table-column>
      <el-table-column prop="deptType" label="类型" width="110">
        <template slot-scope="scope">
          <el-tag :type="deptTypeTagType(scope.row.deptType)" size="mini">{{ deptTypeLabel(scope.row.deptType, scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderNum" label="排序" width="200">
        <template slot-scope="scope">
          <el-input-number
            v-model="scope.row.orderNum"
            controls-position="right"
            :min="0"
            :max="999999"
            size="mini"
            style="width: 88px"
            :class="{ 'sort-value-dirty': isSortItemDirty(scope.row) }"
          />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_normal_disable" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="合同公司" min-width="190" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.legalEntityName">{{ scope.row.legalEntityName }}</span>
          <span v-else class="text-muted">继承上级或待配置</span>
        </template>
      </el-table-column>
      <el-table-column label="负责人" min-width="150">
        <template slot-scope="scope">
          <span v-if="scope.row.leaderUserId">{{ scope.row.leader || "已绑定" }}</span>
          <el-tag v-else-if="scope.row.leader" type="warning" size="mini">待确认：{{ scope.row.leader }}</el-tag>
          <span v-else class="text-muted">未配置</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="200">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['system:dept:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAdd(scope.row)"
            v-hasPermi="['system:dept:add']"
          >新增下级</el-button>
          <el-button
            v-if="scope.row.parentId != 0"
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['system:dept:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <!-- 添加或修改部门对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="24" v-if="form.parentId !== 0">
            <el-form-item label="上级部门" prop="parentId">
              <treeselect v-model="form.parentId" :options="deptOptions" :normalizer="normalizer" placeholder="选择上级部门" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="合同公司">
              <el-select v-model="form.legalEntityId" clearable filterable placeholder="不选时继承最近上级部门的公司" style="width:100%">
                <el-option v-for="company in legalEntityOptions" :key="company.legalEntityId" :label="company.legalEntityName" :value="company.legalEntityId" />
              </el-select>
              <div class="company-binding-tip">员工首次签名后，合同会按此部门向上查找最近配置的公司，再匹配该公司的合同印章。</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="部门名称" prop="deptName">
              <el-input v-model="form.deptName" placeholder="请输入部门名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="部门类型" prop="deptType">
              <el-select v-model="form.deptType" placeholder="请选择部门类型" @change="handleLeaderRequirementChange">
                <el-option
                  v-for="item in deptTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <div class="dept-type-tip">“公司 / 组织”兼容现有层级；集团直属节点显示为公司，下级区域、运营单元和职能部门显示为组织。新增时选此项可继续建立下一级组织。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门状态">
              <el-radio-group v-model="form.status" @change="handleLeaderRequirementChange">
                <el-radio
                  v-for="dict in dict.type.sys_normal_disable"
                  :key="dict.value"
                  :label="dict.value"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="负责人" prop="leaderUserId">
              <el-select
                ref="leaderSelect"
                v-model="form.leaderUserId"
                filterable
                remote
                clearable
                reserve-keyword
                :remote-method="searchLeaderOptions"
                :loading="leaderOptionsLoading"
                placeholder="按姓名、工号或组织搜索"
                style="width: 100%"
                @visible-change="handleLeaderSelectVisible"
                @change="handleLeaderChange"
              >
                <el-option
                  v-for="item in leaderOptions"
                  :key="item.userId"
                  :label="leaderOptionLabel(item)"
                  :value="item.userId"
                />
              </el-select>
              <div v-if="!form.leaderUserId && form.leader" class="leader-pending-tip">
                待确认负责人：{{ form.leader }}，请从员工账号中重新选择
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="11" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listDept, getDept, delDept, addDept, updateDept, updateDeptSort, listDeptExcludeChild, listDeptLeaderOptions } from "@/api/system/dept"
import { listLegalEntityOptions } from "@/api/system/legalEntity"
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import pendingSortGuard from "@/mixins/pendingSortGuard"

export default {
  name: "Dept",
  mixins: [pendingSortGuard],
  dicts: ['sys_normal_disable'],
  components: { Treeselect },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 显示搜索条件
      showSearch: true,
      // 表格树数据
      deptList: [],
      // 部门树选项
      deptOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否展开，默认全部展开
      isExpandAll: true,
      // 重新渲染表格状态
      refreshTable: true,
      sortIdField: "deptId",
      sortListField: "deptList",
      leaderOptions: [],
      leaderOptionsLoading: false,
      legalEntityOptions: [],
      leaderSearchTimer: null,
      routeFocusHandled: false,
      deptTypeOptions: [
        { label: "集团", value: "GROUP", tagType: "info" },
        { label: "公司 / 组织", value: "COMPANY", tagType: "" },
        { label: "门店", value: "STORE", tagType: "success" },
        { label: "仓库", value: "WAREHOUSE", tagType: "warning" }
      ],
      // 查询参数
      queryParams: {
        deptName: undefined,
        status: undefined,
        deptType: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        parentId: [
          { required: true, message: "上级部门不能为空", trigger: "blur" }
        ],
        deptName: [
          { required: true, message: "部门名称不能为空", trigger: "blur" }
        ],
        orderNum: [
          { required: true, message: "显示排序不能为空", trigger: "blur" }
        ],
        deptType: [
          { required: true, message: "部门类型不能为空", trigger: "change" }
        ],
        leaderUserId: [
          {
            validator: (rule, value, callback) => {
              if (this.isLeaderRequired(this.form) && !value) {
                callback(new Error("启用的公司或部门必须选择负责人"))
                return
              }
              callback()
            },
            trigger: "change"
          }
        ],
        email: [
          {
            type: "email",
            message: "请输入正确的邮箱地址",
            trigger: ["blur", "change"]
          }
        ],
        phone: [
          {
            pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/,
            message: "请输入正确的手机号码",
            trigger: "blur"
          }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.loadLegalEntityOptions()
  },
  mounted() {
    window.addEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeDestroy() {
    if (this.leaderSearchTimer) {
      clearTimeout(this.leaderSearchTimer)
      this.leaderSearchTimer = null
    }
    window.removeEventListener("beforeunload", this.handleBeforeUnload)
  },
  beforeRouteLeave(to, from, next) {
    if (this.changedSortItems.length === 0) {
      next()
      return
    }
    this.$modal.confirm(`还有 ${this.changedSortItems.length} 项排序未保存，确定离开吗？`)
      .then(() => next())
      .catch(() => next(false))
  },
  computed: {
    changedSortItems() {
      const items = []
      const collect = list => {
        ;(list || []).forEach(item => {
          if (String(this.originalOrders[item.deptId]) !== String(item.orderNum)) {
            items.push({ id: item.deptId, orderNum: item.orderNum })
          }
          if (item.children && item.children.length) collect(item.children)
        })
      }
      collect(this.deptList)
      return items
    }
  },
  methods: {
    /** 查询部门列表 */
    getList() {
      this.loading = true
      return listDept(this.queryParams).then(response => {
        this.deptList = this.handleTree(response.data, "deptId")
        this.recordOriginalOrders(this.deptList)
        this.handleRouteFocus()
      }).finally(() => {
        this.loading = false
        this.handleRouteFocus()
      })
    },
    /** 转换部门数据结构 */
    normalizer(node) {
      if (node.children && !node.children.length) {
        delete node.children
      }
      return {
        id: node.deptId,
        label: node.deptName,
        children: node.children
      }
    },
    deptTypeLabel(value, row) {
      if (value === "COMPANY" && row) {
        return this.isNestedOrganization(row) ? "组织" : "公司"
      }
      const item = this.deptTypeOptions.find(option => option.value === value)
      return item ? item.label : "未分类"
    },
    isNestedOrganization(row) {
      const ancestorIds = String(row && row.ancestors || "")
        .split(",")
        .map(value => value.trim())
        .filter(value => value && value !== "0")
      return ancestorIds.length > 1
    },
    deptTypeTagType(value) {
      const item = this.deptTypeOptions.find(option => option.value === value)
      return item ? item.tagType : "info"
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        deptId: undefined,
        parentId: undefined,
        deptName: undefined,
        orderNum: undefined,
        deptType: "STORE",
        legalEntityId: undefined,
        leader: undefined,
        leaderUserId: undefined,
        phone: undefined,
        email: undefined,
        status: "0"
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      return this.runAfterPendingSortConfirmation(
        () => this.getList(),
        "搜索将丢失尚未保存的部门排序，是否继续？"
      ).catch(() => {})
    },
    /** 重置按钮操作 */
    resetQuery() {
      return this.runAfterPendingSortConfirmation(() => {
        this.resetForm("queryForm")
        return this.getList()
      }, "重置查询将丢失尚未保存的部门排序，是否继续？").catch(() => {})
    },
    loadLegalEntityOptions() {
      return listLegalEntityOptions().then(response => {
        this.legalEntityOptions = response.data || []
      })
    },
    /** 新增按钮操作 */
    handleAdd(row) {
      return this.runAfterPendingSortConfirmation(
        () => this.openDeptEditor(row),
        "打开部门编辑将可能在提交后刷新列表并丢失未保存排序，是否继续？"
      ).catch(() => {})
    },
    openDeptEditor(row) {
      this.reset()
      if (row != undefined) {
        this.form.parentId = row.deptId
      }
      this.open = true
      this.title = row && row.deptName ? `添加“${row.deptName}”的下级组织` : "添加部门"
      this.loadLeaderOptions("")
      listDept().then(response => {
        this.deptOptions = this.handleTree(response.data, "deptId")
      })
    },
    /** 展开/折叠操作 */
    toggleExpandAll() {
      this.refreshTable = false
      this.isExpandAll = !this.isExpandAll
      this.$nextTick(() => {
        this.refreshTable = true
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row, focusLeader = false) {
      return this.runAfterPendingSortConfirmation(
        () => this.openDeptUpdate(row, focusLeader),
        "打开部门编辑将可能在提交后刷新列表并丢失未保存排序，是否继续？"
      ).catch(() => {})
    },
    openDeptUpdate(row, focusLeader = false) {
      this.reset()
      return getDept(row.deptId).then(response => {
        this.form = response.data
        this.ensureCurrentLeaderOption()
        this.loadLeaderOptions("")
        this.open = true
        this.title = "修改部门"
        if (focusLeader) {
          this.$nextTick(() => {
            if (this.$refs.leaderSelect) this.$refs.leaderSelect.focus()
          })
        }
        listDeptExcludeChild(row.deptId).then(response => {
          this.deptOptions = this.handleTree(response.data, "deptId")
          if (this.deptOptions.length == 0) {
            const noResultsOptions = { deptId: this.form.parentId, deptName: this.form.parentName, children: [] }
            this.deptOptions.push(noResultsOptions)
          }
        })
      })
    },
    handleRouteFocus() {
      if (this.routeFocusHandled || !this.$route || !this.$route.query.deptId) return
      const deptId = Number(this.$route.query.deptId)
      if (!Number.isSafeInteger(deptId) || deptId <= 0) return
      this.routeFocusHandled = true
      this.handleUpdate({ deptId }, true)
    },
    isLeaderRequired(dept) {
      if (!dept || dept.status === "1") return false
      return ["GROUP", "STORE", "WAREHOUSE"].indexOf(String(dept.deptType || "").toUpperCase()) === -1
    },
    handleLeaderRequirementChange() {
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.validateField("leaderUserId")
      })
    },
    leaderOptionLabel(option) {
      return [option.employeeName, option.employeeNo || "未编工号", option.deptName || "未分配组织"].join(" / ")
    },
    ensureCurrentLeaderOption() {
      if (!this.form.leaderUserId || !this.form.leader) return
      if (this.leaderOptions.some(item => item.userId === this.form.leaderUserId)) return
      this.leaderOptions.push({
        userId: this.form.leaderUserId,
        employeeName: this.form.leader,
        employeeNo: "",
        deptId: undefined,
        deptName: ""
      })
    },
    loadLeaderOptions(keyword) {
      this.leaderOptionsLoading = true
      return listDeptLeaderOptions({ keyword: keyword || undefined }).then(response => {
        const selected = this.leaderOptions.find(item => item.userId === this.form.leaderUserId)
        const values = Array.isArray(response.data) ? response.data.slice() : []
        if (selected && !values.some(item => item.userId === selected.userId)) values.unshift(selected)
        this.leaderOptions = values
      }).finally(() => {
        this.leaderOptionsLoading = false
      })
    },
    searchLeaderOptions(keyword) {
      if (this.leaderSearchTimer) clearTimeout(this.leaderSearchTimer)
      this.leaderSearchTimer = setTimeout(() => this.loadLeaderOptions(keyword), 250)
    },
    handleLeaderSelectVisible(visible) {
      if (visible && this.leaderOptions.length === 0) this.loadLeaderOptions("")
    },
    handleLeaderChange(userId) {
      const selected = this.leaderOptions.find(item => item.userId === userId)
      this.form.leader = selected ? selected.employeeName : undefined
      if (!userId && this.isLeaderRequired(this.form)) {
        this.$modal.msgWarning("启用的公司或部门必须选择负责人")
      }
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          const payload = Object.assign({}, this.form)
          if (!payload.leaderUserId) payload.leader = undefined
          if (this.form.deptId != undefined) {
            updateDept(payload).then(() => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addDept(payload).then(() => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 保存排序 */
    handleSaveSort() {
      if (!this.hasPendingSortChanges || !this.validatePendingSortChanges()) return Promise.resolve()
      this.sortSaving = true
      return updateDeptSort(this.buildSortChangeRequest()).then(() => {
        this.$modal.msgSuccess("排序保存成功")
        this.originalOrders = {}
        this.recordOriginalOrders(this.deptList)
      }).catch(error => {
        this.handleSortSaveFailure(error)
      }).finally(() => {
        this.sortSaving = false
      })
    },
    /** 恢复当前列表中的全部未保存排序 */
    handleUndoSort() {
      const restore = list => {
        ;(list || []).forEach(item => {
          if (Object.prototype.hasOwnProperty.call(this.originalOrders, item.deptId)) {
            item.orderNum = this.originalOrders[item.deptId]
          }
          if (item.children && item.children.length) restore(item.children)
        })
      }
      restore(this.deptList)
      this.$modal.msgSuccess("已撤销未保存的排序修改")
    },
    handleBeforeUnload(event) {
      if (this.changedSortItems.length === 0) return
      event.preventDefault()
      event.returnValue = ""
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      return this.runAfterPendingSortConfirmation(() => {
        return this.$modal.confirm('是否确认删除名称为"' + row.deptName + '"的数据项？').then(() => {
          return delDept(row.deptId)
        }).then(() => {
          this.getList()
          this.$modal.msgSuccess("删除成功")
        })
      }, "删除部门成功后将刷新列表并丢失未保存排序，是否继续？").catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.dept-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.dept-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
}

.dept-table-card {
  padding-bottom: 8px;
}

.leader-pending-tip {
  margin-top: 6px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 18px;
}

.company-binding-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.dept-type-tip {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.text-muted {
  color: #909399;
}

.sort-save-alert {
  margin-top: 8px;
}

::v-deep .sort-value-dirty .el-input__inner {
  color: #b26a00;
  background: #fff7e6;
  border-color: #e6a23c;
  font-weight: 600;
}
</style>
