<template>
  <div class="app-container system-management-page">
    <system-page-header
      title="登录日志"
      description="查看登录结果与异常访问记录，及时处理账号锁定和安全风险。"
      icon="el-icon-key"
    />
    <div v-show="showSearch" class="search-card logininfor-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="登录地址" prop="ipaddr">
        <el-input
          v-model="queryParams.ipaddr"
          placeholder="请输入登录地址"
          clearable
          style="width: 240px;"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="用户名称" prop="userName">
        <el-input
          v-model="queryParams.userName"
          placeholder="请输入用户名称"
          clearable
          style="width: 240px;"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="登录状态"
          clearable
          style="width: 240px"
        >
          <el-option
            v-for="dict in dict.type.sys_common_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="登录时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd HH:mm:ss"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="['00:00:00', '23:59:59']"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="content-card logininfor-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:logininfor:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-tooltip :content="unlockTooltip" placement="top">
          <span>
            <el-button
              type="primary"
              plain
              icon="el-icon-unlock"
              size="mini"
              :loading="lockStateLoading"
              :disabled="unlockDisabled"
              @click="handleUnlock"
              v-hasPermi="['system:logininfor:unlock']"
            >解锁</el-button>
          </span>
        </el-tooltip>
      </el-col>
      <el-col v-if="!single" :span="4">
        <el-tag v-if="lockStateLoading" size="small" type="info">正在查询锁定状态</el-tag>
        <el-tag v-else-if="lockState && lockState.locked" size="small" type="danger">
          已锁定 · 剩余 {{ formatRemaining(lockState.remainingSeconds) }}
        </el-tag>
        <el-tag v-else-if="lockState" size="small" type="success">当前未锁定</el-tag>
        <el-tag v-else size="small" type="warning">锁定状态不可用</el-tag>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['system:logininfor:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    </div>

    <div class="table-card logininfor-table-card">
    <el-table ref="tables" v-accessible-table="'登录日志列表'" v-loading="loading" :data="list" @selection-change="handleSelectionChange" :default-sort="defaultSort" @sort-change="handleSortChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="访问编号" align="center" prop="infoId" />
      <el-table-column label="用户名称" align="center" prop="userName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
      <el-table-column label="地址" align="center" prop="ipaddr" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="登录状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_common_status" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="描述" align="center" prop="msg" :show-overflow-tooltip="true" />
      <el-table-column label="访问时间" align="center" prop="accessTime" sortable="custom" :sort-orders="['descending', 'ascending']" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.accessTime) }}</span>
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
  </div>
</template>

<script>
import { list, delLogininfor, getLoginLockState, unlockLogininfor } from "@/api/system/logininfor"
import { confirmExportAction } from "@/utils/exportConfirm"

export default {
  name: "Logininfor",
  dicts: ['sys_common_status'],
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
      // 选择用户名
      selectName: "",
      // 当前选择用户的实时锁定状态
      lockState: null,
      lockStateLoading: false,
      lockStateRequestId: 0,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 表格数据
      list: [],
      // 日期范围
      dateRange: [],
      // 默认排序
      defaultSort: { prop: "accessTime", order: "descending" },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ipaddr: undefined,
        userName: undefined,
        status: undefined
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    unlockDisabled() {
      return this.single || this.lockStateLoading || !this.lockState ||
        !this.lockState.locked || !this.lockState.canUnlock
    },
    unlockTooltip() {
      if (this.single) return '请选择一条登录日志'
      if (this.lockStateLoading) return '正在查询该账号的实时锁定状态'
      if (!this.lockState) return '无法确认锁定状态，暂不可解锁'
      if (!this.lockState.locked) return '该账号当前未被锁定'
      return `已连续失败 ${this.lockState.retryCount} 次，可执行解锁`
    }
  },
  methods: {
    /** 查询登录日志列表 */
    getList() {
      this.loading = true
      list(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
          this.list = response.rows
          this.total = response.total
          this.loading = false
        }
      )
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
      this.queryParams.pageNum = 1
      this.$refs.tables.sort(this.defaultSort.prop, this.defaultSort.order)
    },
    /** 多选框选中数据 */
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.infoId)
      this.single = selection.length!=1
      this.multiple = !selection.length
      this.selectName = selection.length === 1 ? selection[0].userName : ""
      this.lockState = null
      const requestId = ++this.lockStateRequestId
      if (selection.length === 1) {
        this.fetchLockState(this.selectName, requestId)
      } else {
        this.lockStateLoading = false
      }
    },
    /** 排序触发事件 */
    handleSortChange(column, prop, order) {
      this.queryParams.orderByColumn = column.prop
      this.queryParams.isAsc = column.order
      this.getList()
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const infoIds = row.infoId || this.ids
      this.$modal.confirm('是否确认删除访问编号为"' + infoIds + '"的数据项？').then(function() {
        return delLogininfor(infoIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 查询所选账号的实时锁定状态 */
    fetchLockState(userName, requestId = ++this.lockStateRequestId) {
      if (!userName) return Promise.resolve()
      this.lockStateLoading = true
      return getLoginLockState(userName).then(response => {
        if (requestId === this.lockStateRequestId && userName === this.selectName) {
          this.lockState = response.data || null
        }
      }).catch(() => {
        if (requestId === this.lockStateRequestId) this.lockState = null
      }).finally(() => {
        if (requestId === this.lockStateRequestId) this.lockStateLoading = false
      })
    },
    /** 解锁按钮操作 */
    handleUnlock() {
      const username = this.selectName
      if (this.unlockDisabled) return
      this.$modal.confirm('确认解除用户"' + username + '"的登录锁定吗？').then(function() {
        return unlockLogininfor(username)
      }).then(response => {
        this.$modal.msgSuccess(response.msg || ("用户" + username + "已解锁"))
        return this.fetchLockState(username)
      }).catch(() => {})
    },
    formatRemaining(seconds) {
      const total = Math.max(Number(seconds) || 0, 0)
      const minutes = Math.floor(total / 60)
      const rest = total % 60
      return minutes > 0 ? `${minutes}分${rest}秒` : `${rest}秒`
    },
    /** 导出按钮操作 */
    handleExport() {
      confirmExportAction(this, {
        moduleName: "登录日志",
        rangeLabel: "当前查询条件下的登录日志",
        filterLabel: this.buildManagementExportFilterLabel("登录账号", this.queryParams.userName)
      }).then(() => {
        this.download(
          'system/logininfor/export',
          this.addDateRange({ ...this.queryParams }, this.dateRange),
          this.exportFileName('登录日志', this.dateRange)
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
.logininfor-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.logininfor-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
}

.logininfor-table-card {
  padding-bottom: 8px;
}
</style>
