<template>
  <section class="validation-panel">
    <el-card shadow="never" class="validation-overview">
      <div class="overview-copy">
        <span class="eyebrow">发布前门禁</span>
        <h3>配置完整性检查</h3>
        <p>检查规则冲突、必选节点缺人、岗位权限、组织覆盖和业务回调。错误未清零前不能发布。</p>
      </div>
      <el-button v-hasPermi="['approval:validation:run']" type="primary" icon="el-icon-cpu" @click="openRunDialog">立即检查</el-button>
    </el-card>

    <el-card shadow="never">
      <el-form :model="query" inline size="small" class="validation-filter" @submit.native.prevent>
        <el-form-item label="业务类型">
          <el-select v-model="query.businessCode" clearable placeholder="全部业务">
            <el-option v-for="item in templates" :key="item.businessCode" :label="item.templateName" :value="item.businessCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查结果">
          <el-select v-model="query.status" clearable placeholder="全部结果">
            <el-option label="检查中" value="RUNNING" />
            <el-option label="通过" value="PASSED" />
            <el-option label="未通过" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>

      <approval-load-error
        v-if="validationLoadError"
        title="配置检查记录加载失败"
        :error="validationLoadError"
        :loading="loading"
        class="panel-load-error"
        @retry="retryValidationRuns"
      />

      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column label="检查批次" min-width="150"><template slot-scope="scope">{{ scope.row.runNo || scope.row.runId || scope.row.id }}</template></el-table-column>
        <el-table-column label="业务" min-width="150"><template slot-scope="scope">{{ scope.row.templateName || businessLabel(scope.row.businessCode) }}</template></el-table-column>
        <el-table-column label="范围" min-width="140"><template slot-scope="scope">{{ scopeLabel(scope.row) }}</template></el-table-column>
        <el-table-column label="结果" width="110"><template slot-scope="scope"><el-tag :type="statusType(scope.row.runStatus || scope.row.status)" size="mini">{{ statusLabel(scope.row.runStatus || scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="错误" prop="errorCount" width="80" align="center" />
        <el-table-column label="警告" prop="warningCount" width="80" align="center" />
        <el-table-column label="发起人" min-width="110"><template slot-scope="scope">{{ scope.row.startedByName || scope.row.createByName || scope.row.createBy || '-' }}</template></el-table-column>
        <el-table-column label="检查时间" width="165"><template slot-scope="scope">{{ scope.row.startedTime || scope.row.createTime || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template slot-scope="scope"><el-button v-hasPermi="['approval:validation:list']" type="text" size="mini" @click="openDetail(scope.row)">查看问题</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </el-card>

    <el-dialog title="执行配置检查" :visible.sync="runVisible" width="620px" append-to-body :close-on-click-modal="false">
      <el-form ref="runForm" :model="runForm" :rules="runRules" label-width="110px">
        <el-form-item label="业务类型" prop="businessCode">
          <el-select v-model="runForm.businessCode" placeholder="请选择业务" style="width:100%">
            <el-option v-for="item in templates" :key="item.businessCode" :label="item.templateName" :value="item.businessCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查范围">
          <el-radio-group v-model="runForm.scopeMode">
            <el-radio label="ALL">全部有效组织</el-radio>
            <el-radio label="ANCHOR">指定组织预检</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="runForm.scopeMode === 'ANCHOR'" label="组织编号" prop="anchorDeptId">
          <el-input v-model.trim="runForm.anchorDeptId" placeholder="请输入门店或组织编号" />
        </el-form-item>
        <el-form-item label="业务子类型">
          <el-input v-model.trim="runForm.businessSubtype" maxlength="64" placeholder="选填；为空检查全部子类型" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button :disabled="runLoading" @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="runLoading" @click="submitRun">开始检查</el-button>
      </div>
    </el-dialog>

    <el-dialog title="配置检查结果" :visible.sync="detailVisible" width="980px" append-to-body>
      <div v-loading="detailLoading">
        <el-alert
          v-if="detail"
          :title="detailSummary"
          :type="Number(detail.errorCount || 0) > 0 ? 'error' : 'success'"
          :closable="false"
          show-icon
          class="detail-alert"
        />
        <el-table :data="issues" border size="small">
          <el-table-column label="级别" width="90"><template slot-scope="scope"><el-tag :type="severityType(scope.row.severity)" size="mini">{{ severityLabel(scope.row.severity) }}</el-tag></template></el-table-column>
          <el-table-column label="问题类型" min-width="150"><template slot-scope="scope">{{ validationIssueLabel(scope.row.issueType || scope.row.issueCode) }}</template></el-table-column>
          <el-table-column label="业务/范围" min-width="160"><template slot-scope="scope">{{ businessLabel(scope.row.businessCode) }} / {{ scope.row.scopeName || scope.row.scopeId || '全部' }}</template></el-table-column>
          <el-table-column label="问题说明" min-width="260"><template slot-scope="scope">{{ scope.row.message || scope.row.issueMessage || '-' }}</template></el-table-column>
          <el-table-column label="修复建议" prop="suggestion" min-width="220" />
        </el-table>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { getApprovalValidationRun, listApprovalValidationRuns, runApprovalValidation } from '@/api/approval/validation'
import ApprovalLoadError from './ApprovalLoadError'
import { businessCodeLabel, entityId, formatApprovalLoadError, statusLabel, statusType, toArray, unwrapData, unwrapRows, validationIssueLabel } from './approvalUi'

export default {
  name: 'ApprovalValidationPanel',
  components: { ApprovalLoadError },
  props: { templates: { type: Array, default: () => [] } },
  data() {
    const validateAnchor = (rule, value, callback) => {
      if (this.runForm.scopeMode === 'ANCHOR' && !String(value || '').trim()) callback(new Error('请输入指定组织编号'))
      else callback()
    }
    return {
      loading: false,
      validationLoadError: null,
      rows: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10, businessCode: '', status: '' },
      runVisible: false,
      runLoading: false,
      runForm: { businessCode: '', scopeMode: 'ALL', anchorDeptId: '', businessSubtype: '' },
      runRules: {
        businessCode: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
        anchorDeptId: [{ validator: validateAnchor, trigger: 'blur' }]
      },
      detailVisible: false,
      detailLoading: false,
      detail: null
    }
  },
  computed: {
    issues() { return toArray(this.detail && (this.detail.issues || this.detail.issueList)) },
    detailSummary() {
      if (!this.detail) return ''
      return `检查完成：${Number(this.detail.errorCount || 0)} 个错误，${Number(this.detail.warningCount || 0)} 个警告`
    }
  },
  created() { this.load() },
  methods: {
    statusLabel,
    statusType,
    validationIssueLabel,
    businessLabel(code) {
      const template = this.templates.find(item => item.businessCode === code)
      return template ? template.templateName : businessCodeLabel(code)
    },
    scopeLabel(row) {
      if (row.scopeMode === 'ALL') return '全部有效组织'
      return row.scopeName || row.anchorDeptName || row.anchorDeptId || '指定范围'
    },
    severityLabel(value) { return { ERROR: '错误', WARNING: '警告', INFO: '提示' }[String(value || '').toUpperCase()] || (value ? '未知级别' : '-') },
    severityType(value) { return { ERROR: 'danger', WARNING: 'warning', INFO: 'info' }[String(value || '').toUpperCase()] || 'info' },
    load() {
      this.loading = true
      this.rows = []
      this.total = 0
      this.validationLoadError = null
      const params = Object.keys(this.query).reduce((result, key) => {
        if (this.query[key] !== '') result[key] = this.query[key]
        return result
      }, {})
      return listApprovalValidationRuns(params, { silentError: true }).then(response => {
        const page = unwrapRows(response)
        this.rows = page.rows
        this.total = page.total
        return response
      }).catch(error => {
        this.validationLoadError = formatApprovalLoadError(
          error,
          '配置检查服务暂不可用；当前状态未知，不会按“检查通过”处理。'
        )
        return { error }
      }).finally(() => { this.loading = false })
    },
    retryValidationRuns() { return this.load() },
    search() { this.query.pageNum = 1; return this.load() },
    reset() { this.query = { pageNum: 1, pageSize: 10, businessCode: '', status: '' }; return this.load() },
    openRunDialog() {
      this.runForm = { businessCode: this.query.businessCode || '', scopeMode: 'ALL', anchorDeptId: '', businessSubtype: '' }
      this.runVisible = true
      this.$nextTick(() => this.$refs.runForm && this.$refs.runForm.clearValidate())
    },
    submitRun() {
      this.$refs.runForm.validate(valid => {
        if (!valid) return
        this.runLoading = true
        const payload = {
          businessCode: this.runForm.businessCode,
          scopeMode: this.runForm.scopeMode,
          anchorDeptId: this.runForm.scopeMode === 'ANCHOR' ? this.runForm.anchorDeptId : undefined,
          businessSubtype: this.runForm.businessSubtype || undefined
        }
        runApprovalValidation(payload).then(response => {
          this.$modal.msgSuccess('配置检查已完成')
          this.runVisible = false
          this.load()
          const value = unwrapData(response)
          const result = value && value.run ? { ...value.run, issues: value.issues || value.issueList || [] } : value
          if (entityId(result, ['runId', 'id'])) this.openDetail(result)
        }).finally(() => { this.runLoading = false })
      })
    },
    openDetail(row) {
      this.detailVisible = true
      this.detailLoading = true
      return getApprovalValidationRun(entityId(row, ['runId', 'id'])).then(response => {
        const value = unwrapData(response)
        this.detail = value && value.run ? { ...value.run, issues: value.issues || value.issueList || [] } : value
      }).finally(() => { this.detailLoading = false })
    }
  }
}
</script>

<style lang="scss" scoped>
.validation-panel { display: flex; flex-direction: column; gap: 12px; }
.validation-overview { border-radius: 12px; background: linear-gradient(125deg, #f0fdfa, #eff6ff); }
.validation-overview ::v-deep .el-card__body { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.overview-copy h3 { margin: 4px 0 8px; color: #0f172a; font-size: 20px; }
.overview-copy p { margin: 0; color: #64748b; }
.eyebrow { color: #0f766e; font-size: 12px; font-weight: 700; }
.validation-filter ::v-deep .el-form-item { margin-bottom: 12px; }
.panel-load-error { margin-bottom: 12px; }
.detail-alert { margin-bottom: 14px; }
</style>
