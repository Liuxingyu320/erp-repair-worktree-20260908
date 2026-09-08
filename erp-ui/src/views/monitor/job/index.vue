<template>
  <div class="app-container">
    <div v-show="showSearch" class="search-card job-search-card">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="任务名称" prop="jobName">
        <el-input
          v-model="queryParams.jobName"
          placeholder="请输入任务名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="任务组名" prop="jobGroup">
        <el-select v-model="queryParams.jobGroup" placeholder="请选择任务组名" clearable>
          <el-option
            v-for="dict in dict.type.sys_job_group"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="任务状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择任务状态" clearable>
          <el-option
            v-for="dict in dict.type.sys_job_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div v-if="jobListError" class="job-list-error" role="alert">
      <i class="el-icon-warning-outline"></i>
      <div class="job-list-error__copy">
        <strong>定时任务列表加载失败</strong>
        <span>{{ jobListError.message }}</span>
        <small v-if="jobListErrorDiagnostic">{{ jobListErrorDiagnostic }}</small>
      </div>
      <el-button
        type="primary"
        plain
        icon="el-icon-refresh"
        :loading="loading"
        @click="retryJobList"
      >重试</el-button>
    </div>

    <div class="content-card job-toolbar-card">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          :disabled="writeActionsDisabled"
          @click="handleAdd"
          v-hasPermi="['monitor:job:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single || writeActionsDisabled"
          @click="handleUpdate"
          v-hasPermi="['monitor:job:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple || writeActionsDisabled"
          @click="handleDelete"
          v-hasPermi="['monitor:job:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          :disabled="writeActionsDisabled"
          @click="handleExport"
          v-hasPermi="['monitor:job:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-s-operation"
          size="mini"
          @click="handleJobLog"
          v-hasPermi="['monitor:job:query']"
        >日志</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    </div>

    <div class="table-card job-table-card">
    <el-table v-loading="loading" :data="jobList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="任务编号" width="100" align="center" prop="jobId" />
      <el-table-column label="任务名称" align="center" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <a class="link-type" style="cursor:pointer" @click="handleView(scope.row)">{{ scope.row.jobName }}</a>
        </template>
      </el-table-column>
      <el-table-column label="任务组名" align="center" prop="jobGroup">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_job_group" :value="scope.row.jobGroup"/>
        </template>
      </el-table-column>
      <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" />
      <el-table-column label="cron执行表达式" align="center" prop="cronExpression" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="0"
            inactive-value="1"
            :disabled="writeActionsDisabled"
            @change="handleStatusChange(scope.row)"
            v-hasPermi="['monitor:job:changeStatus']"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            :disabled="writeActionsDisabled"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['monitor:job:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            :disabled="writeActionsDisabled"
            @click="handleDelete(scope.row)"
            v-hasPermi="['monitor:job:remove']"
          >删除</el-button>
          <el-dropdown size="mini" @command="(command) => handleCommand(command, scope.row)" v-hasPermi="['monitor:job:changeStatus', 'monitor:job:query']">
            <el-button size="mini" type="text" icon="el-icon-d-arrow-right">更多</el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="handleRun" icon="el-icon-caret-right" :disabled="writeActionsDisabled"
                v-hasPermi="['monitor:job:changeStatus']">执行一次</el-dropdown-item>
              <el-dropdown-item command="handleJobLog" icon="el-icon-s-operation"
                v-hasPermi="['monitor:job:query']">调度日志</el-dropdown-item>
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

    <!-- 添加或修改定时任务对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
              <el-input v-model="form.jobName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务分组" prop="jobGroup">
              <el-select v-model="form.jobGroup" placeholder="请选择任务分组">
                <el-option
                  v-for="dict in dict.type.sys_job_group"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item prop="invokeTarget">
              <span slot="label">
                调用方法
                <el-tooltip placement="top">
                  <div slot="content">
                    Bean调用示例：ryTask.ryParams('ry')
                    <br />Class类调用示例：com.example.quartz.task.RyTask.ryParams('ry')
                    <br />参数说明：支持字符串，布尔类型，长整型，浮点型，整型
                  </div>
                  <i class="el-icon-question"></i>
                </el-tooltip>
              </span>
              <el-input v-model="form.invokeTarget" placeholder="请输入调用目标字符串" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="cron表达式" prop="cronExpression">
              <el-input v-model="form.cronExpression" placeholder="请输入cron执行表达式">
                <template slot="append">
                  <el-button type="primary" @click="handleShowCron">
                    生成表达式
                    <i class="el-icon-time el-icon--right"></i>
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="form.jobId !== undefined">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio
                  v-for="dict in dict.type.sys_job_status"
                  :key="dict.value"
                  :label="dict.value"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行策略" prop="misfirePolicy">
              <el-radio-group v-model="form.misfirePolicy" size="small">
                <el-radio-button label="1">立即执行</el-radio-button>
                <el-radio-button label="2">执行一次</el-radio-button>
                <el-radio-button label="3">放弃执行</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否并发" prop="concurrent">
              <el-radio-group v-model="form.concurrent" size="small">
                <el-radio-button label="0">允许</el-radio-button>
                <el-radio-button label="1">禁止</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" :disabled="writeActionsDisabled" @click="submitForm">确 定</el-button>
        <el-button :disabled="submitLoading" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="Cron表达式生成器" :visible.sync="openCron" append-to-body destroy-on-close class="scrollbar">
      <crontab @hide="openCron=false" @fill="crontabFill" :expression="expression"></crontab>
    </el-dialog>

    <!-- 任务日志详细 -->
    <job-detail :visible.sync="openView" :row="form" type="job" />
  </div>
</template>

<script>
import { listJob, getJob, delJob, addJob, updateJob, runJob, changeJobStatus } from "@/api/monitor/job"
import JobDetail from './detail'
import Crontab from '@/components/Crontab'

export default {
  components: { Crontab, JobDetail },
  name: "Job",
  dicts: ['sys_job_group', 'sys_job_status'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 列表错误由页面承接，避免接口失败后只留下永久 Loading
      jobListError: null,
      // 写操作与表单提交共用互斥状态，避免重复执行
      actionLoading: false,
      submitLoading: false,
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
      // 定时任务表格数据
      jobList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详细弹出层
      openView: false,
      // 是否显示Cron表达式弹出层
      openCron: false,
      // 传入的表达式
      expression: "",
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        jobName: undefined,
        jobGroup: undefined,
        status: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        jobName: [
          { required: true, message: "任务名称不能为空", trigger: "blur" }
        ],
        invokeTarget: [
          { required: true, message: "调用目标字符串不能为空", trigger: "blur" }
        ],
        cronExpression: [
          { required: true, message: "cron执行表达式不能为空", trigger: "blur" }
        ]
      }
    }
  },
  computed: {
    writeActionsDisabled() {
      return this.loading || !!this.jobListError || this.actionLoading || this.submitLoading
    },
    jobListErrorDiagnostic() {
      if (!this.jobListError) return ''
      const parts = []
      if (this.jobListError.status) parts.push(`状态码：${this.jobListError.status}`)
      if (this.jobListError.requestId) parts.push(`请求标识：${this.jobListError.requestId}`)
      return parts.join(' · ')
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询定时任务列表 */
    getList() {
      this.loading = true
      this.jobListError = null
      return listJob(this.queryParams, { silentError: true }).then(response => {
        this.jobList = Array.isArray(response.rows) ? response.rows : []
        this.total = Number(response.total || 0)
        return response
      }).catch(error => {
        this.jobList = []
        this.total = 0
        this.ids = []
        this.single = true
        this.multiple = true
        this.jobListError = this.normalizeJobListError(error)
        return { error }
      }).finally(() => {
        this.loading = false
      })
    },
    retryJobList() {
      return this.getList()
    },
    normalizeJobListError(error) {
      const response = error && error.response && typeof error.response === 'object' ? error.response : {}
      const data = response.data && typeof response.data === 'object' ? response.data : {}
      const headers = response.headers && typeof response.headers === 'object' ? response.headers : {}
      const responseStatus = Number(response.status)
      const errorCode = Number(error && error.code)
      const status = Number.isFinite(responseStatus) && responseStatus >= 400
        ? responseStatus
        : Number.isFinite(errorCode) && errorCode >= 400
          ? errorCode
          : responseStatus
      const header = name => headers[name] || (typeof headers.get === 'function' ? headers.get(name) : '')
      const requestId = data.requestId || data.traceId || header('x-request-id') || header('x-trace-id') || ''
      let message = error && error.message ? String(error.message).trim() : ''
      if (!message || message === '系统未知错误，请反馈给管理员') {
        message = '调度服务暂不可用，请稍后重试；服务恢复后无需刷新整页。'
      }
      return {
        message,
        status: Number.isFinite(status) && status > 0 ? status : '',
        requestId: String(requestId || '').trim()
      }
    },
    // 任务组名字典翻译
    jobGroupFormat(row, column) {
      return this.selectDictLabel(this.dict.type.sys_job_group, row.jobGroup)
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        jobId: undefined,
        jobName: undefined,
        jobGroup: undefined,
        invokeTarget: undefined,
        cronExpression: undefined,
        misfirePolicy: 1,
        concurrent: 1,
        status: "0"
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
      this.resetForm("queryForm")
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.jobId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleRun":
          this.handleRun(row)
          break
        case "handleView":
          this.handleView(row)
          break
        case "handleJobLog":
          this.handleJobLog(row)
          break
        default:
          break
      }
    },
    // 任务状态修改
    handleStatusChange(row) {
      if (this.writeActionsDisabled) return
      let text = row.status === "0" ? "启用" : "停用"
      this.actionLoading = true
      this.$modal.confirm('确认要"' + text + '""' + row.jobName + '"任务吗？').then(() => {
        return changeJobStatus(row.jobId, row.status)
      }).then(() => {
        this.$modal.msgSuccess(text + "成功")
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0"
      }).finally(() => { this.actionLoading = false })
    },
    /* 立即执行一次 */
    handleRun(row) {
      if (this.writeActionsDisabled) return
      this.actionLoading = true
      this.$modal.confirm('确认要立即执行一次"' + row.jobName + '"任务吗？').then(() => {
        return runJob(row.jobId, row.jobGroup)
      }).then(() => {
        this.$modal.msgSuccess("执行成功")
      }).catch(() => {}).finally(() => { this.actionLoading = false })
    },
    /** 任务详细信息 */
    handleView(row) {
      getJob(row.jobId).then(response => {
        this.form = response.data
        this.openView = true
      })
    },
    /** cron表达式按钮操作 */
    handleShowCron() {
      this.expression = this.form.cronExpression
      this.openCron = true
    },
    /** 确定后回传值 */
    crontabFill(value) {
      this.form.cronExpression = value
    },
    /** 任务日志列表查询 */
    handleJobLog(row) {
      const jobId = row.jobId || 0
      this.$router.push('/monitor/job-log/index/' + jobId)
    },
    /** 新增按钮操作 */
    handleAdd() {
      if (this.writeActionsDisabled) return
      this.reset()
      this.open = true
      this.title = "添加任务"
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      if (this.writeActionsDisabled) return
      this.reset()
      const jobId = row.jobId || this.ids
      getJob(jobId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改任务"
      })
    },
    /** 提交按钮 */
    submitForm() {
      if (this.writeActionsDisabled) return
      this.submitLoading = true
      this.$refs["form"].validate(valid => {
        if (!valid) {
          this.submitLoading = false
          return
        }
        const operation = this.form.jobId != undefined ? updateJob(this.form) : addJob(this.form)
        const successMessage = this.form.jobId != undefined ? "修改成功" : "新增成功"
        operation.then(() => {
          this.$modal.msgSuccess(successMessage)
          this.open = false
          return this.getList()
        }).finally(() => { this.submitLoading = false })
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      if (this.writeActionsDisabled) return
      const jobIds = row.jobId || this.ids
      this.actionLoading = true
      this.$modal.confirm('是否确认删除定时任务编号为"' + jobIds + '"的数据项？').then(() => {
        return delJob(jobIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {}).finally(() => { this.actionLoading = false })
    },
    /** 导出按钮操作 */
    handleExport() {
      if (this.writeActionsDisabled) return
      this.actionLoading = true
      return this.download('schedule/job/export', {
        ...this.queryParams
      }, this.exportFileName('定时任务')).finally(() => { this.actionLoading = false })
    }
  }
}
</script>

<style lang="scss" scoped>
.job-search-card {
  margin-bottom: 12px;
  padding-bottom: 8px;
}

.job-list-error {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid #fbc4c4;
  border-radius: 10px;
  background: #fef0f0;
  color: #606266;
}

.job-list-error > i {
  color: #f56c6c;
  font-size: 24px;
}

.job-list-error__copy {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.job-list-error__copy strong { color: #c45656; }
.job-list-error__copy small { color: #909399; }

.job-toolbar-card {
  margin-bottom: 12px;
  padding-bottom: 2px;
}

.job-table-card {
  padding-bottom: 8px;
}
</style>
