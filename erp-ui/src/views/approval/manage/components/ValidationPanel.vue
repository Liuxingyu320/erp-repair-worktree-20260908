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
          <el-select v-model="query.templateId" @change="search" clearable placeholder="全部业务">
            <el-option v-for="item in templates" :key="item.templateId" :label="item.templateName" :value="item.templateId" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查结果">
          <el-select v-model="query.runStatus" @change="search" clearable placeholder="全部结果">
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
        <el-table-column label="业务" min-width="150"><template slot-scope="scope">{{ templateLabel(scope.row.templateId) }}</template></el-table-column>
        <el-table-column label="规则版本" min-width="140"><template slot-scope="scope">{{ scopeLabel(scope.row) }}</template></el-table-column>
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
      <el-alert v-if="choicesError" :title="choicesError" type="error" :closable="false" show-icon />
      <el-form ref="runForm" :model="runForm" :rules="runRules" label-width="110px">
        <el-form-item label="业务模板" prop="templateId">
          <el-select v-model="runForm.templateId" :disabled="runLoading" placeholder="请选择业务" style="width:100%" @change="selectTemplate()">
            <el-option v-for="item in templates" :key="item.templateId" :label="item.templateName" :value="item.templateId" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批规则" prop="ruleId">
          <el-select v-model="runForm.ruleId" :disabled="runLoading || choicesLoading || !runForm.templateId" :loading="choicesLoading" placeholder="请选择要检查的规则" style="width:100%" @change="selectRule()">
            <el-option v-for="item in rules" :key="item.ruleId" :label="item.ruleName || item.ruleCode" :value="item.ruleId" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则版本" prop="versionId">
          <el-select v-model="runForm.versionId" :disabled="runLoading || versionsLoading || !runForm.ruleId" :loading="versionsLoading" placeholder="请选择要检查的版本" style="width:100%" @change="versionChanged">
            <el-option v-for="item in versions" :key="item.versionId" :label="versionLabel(item)" :value="item.versionId" />
          </el-select>
        </el-form-item>
        <el-alert v-if="runForm.ruleId && !versionsLoading && !versions.length && !choicesError" title="该规则暂无版本，请先在流程配置中创建草稿版本。" type="info" :closable="false" />
        <el-alert v-if="runForm.versionId" :title="'将检查：' + selectedVersionLabel + '，范围沿用该规则的配置。'" type="info" :closable="false" />
        <el-button v-if="choicesError" type="text" @click="retryChoices">重新加载规则与版本</el-button>
      </el-form>
      <div slot="footer">
        <el-button :disabled="runLoading" @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="runLoading" :disabled="!runForm.versionId || choicesLoading || versionsLoading" @click="submitRun">开始检查</el-button>
      </div>
    </el-dialog>

    <el-dialog title="配置检查结果" :visible.sync="detailVisible" width="980px" append-to-body>
      <div v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
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
import { getApprovalTemplate, getApprovalRule } from '@/api/approval/definition'
import { getSelectedDeptId } from '@/utils/shopContext'
import { createUiOperationScope } from '@/utils/uiOperationScope'
import ApprovalLoadError from './ApprovalLoadError'
import { businessCodeLabel, entityId, formatApprovalLoadError, statusLabel, statusType, toArray, unwrapData, unwrapRows, validationIssueLabel } from './approvalUi'

const blankQuery = () => ({ pageNum: 1, pageSize: 10, templateId: '', runStatus: '' })
const blankRun = () => ({ templateId: '', ruleId: '', versionId: '' })
export default {
  name: 'ApprovalValidationPanel',
  components: { ApprovalLoadError },
  props: { templates: { type: Array, default: () => [] } },
  data() {
    return {
      loading: false, validationLoadError: null, rows: [], total: 0, query: blankQuery(),
      runVisible: false, runLoading: false, runForm: blankRun(), rules: [], versions: [],
      choicesLoading: false, versionsLoading: false, choicesError: '',
      runRules: {
        templateId: [{ required: true, message: '请选择业务模板', trigger: 'change' }],
        ruleId: [{ required: true, message: '请选择审批规则', trigger: 'change' }],
        versionId: [{ required: true, message: '请选择规则版本', trigger: 'change' }]
      },
      detailVisible: false, detailLoading: false, detail: null, detailError: ''
    }
  },
  computed: {
    issues() { return toArray(this.detail && (this.detail.issues || this.detail.issueList)) },
    selectedVersionLabel() { return this.versionLabel(this.versions.find(v => String(v.versionId) === String(this.runForm.versionId)) || {}) },
    detailSummary() {
      if (!this.detail) return ''
      return `检查完成：${Number(this.detail.errorCount || 0)} 个错误，${Number(this.detail.warningCount || 0)} 个警告`
    }
  },
  watch: {
    '$store.getters.id'() { this.contextChanged() },
    '$store.state.user.sessionRevision'() { this.contextChanged() },
    runVisible(value) { if (!value) this.closeRun() },
    detailVisible(value) { if (!value) { this.operations().invalidate('detail'); this.detailLoading = false } }
  },
  created() { this.load() },
  mounted() { window.addEventListener('erp:dept-changed', this.contextChanged) },
  activated() { this.operations().activate() },
  deactivated() { this.operations().deactivate(); this.contextChanged() },
  beforeDestroy() { this.operations().deactivate(); window.removeEventListener('erp:dept-changed', this.contextChanged) },
  methods: {
    statusLabel, statusType, validationIssueLabel,
    operations() {
      if (!this._validationOperations) this._validationOperations = createUiOperationScope(() => ({
        userId: this.$store && this.$store.getters.id,
        session: this.$store && this.$store.state.user.sessionRevision,
        deptId: getSelectedDeptId()
      }))
      return this._validationOperations
    },
    contextChanged() {
      this.operations().invalidate()
      this.loading = this.runLoading = this.choicesLoading = this.versionsLoading = this.detailLoading = false
      this.runVisible = this.detailVisible = false
      this.rows = []; this.total = 0; this.detail = null
    },
    closeRun() {
      ;['run', 'rules', 'versions'].forEach(lane => this.operations().invalidate(lane))
      this.runLoading = this.choicesLoading = this.versionsLoading = false
    },
    templateLabel(id) { const item = this.templates.find(t => String(t.templateId) === String(id)); return item ? item.templateName : '模板 ' + (id || '-') },
    businessLabel(code) { const item = this.templates.find(t => t.businessCode === code); return item ? item.templateName : businessCodeLabel(code) },
    scopeLabel(row) { return '版本 ' + (row.ruleVersionId || row.versionId || '-') },
    versionLabel(version) { return `V${version.versionNo || '-'} · ${statusLabel(version.versionStatus || version.status)}` },
    severityLabel(value) { return { ERROR: '错误', WARNING: '警告', INFO: '提示' }[String(value || '').toUpperCase()] || (value ? '未知级别' : '-') },
    severityType(value) { return { ERROR: 'danger', WARNING: 'warning', INFO: 'info' }[String(value || '').toUpperCase()] || 'info' },
    load() {
      const scope = this.operations(), query = { ...this.query }, op = scope.begin('list', query)
      this.loading = true; this.rows = []; this.total = 0; this.validationLoadError = null
      const params = Object.keys(query).reduce((result, key) => { if (query[key] !== '') result[key] = query[key]; return result }, {})
      return listApprovalValidationRuns(params, { silentError: true }).then(response => {
        if (!scope.isCurrent(op, this.query)) return
        const page = unwrapRows(response); this.rows = page.rows; this.total = page.total
        return response
      }).catch(error => {
        if (scope.isCurrent(op, this.query)) this.validationLoadError = formatApprovalLoadError(error, '配置检查服务暂不可用；当前状态未知，不会按“检查通过”处理。')
        return { error }
      }).finally(() => { if (scope.isCurrent(op, this.query)) this.loading = false })
    },
    retryValidationRuns() { return this.load() },
    search() { this.query.pageNum = 1; return this.load() },
    reset() { this.query = blankQuery(); return this.load() },
    openRunDialog(selection = {}) {
      this.closeRun()
      this.runForm = { ...blankRun(), templateId: selection.templateId || this.query.templateId || '' }
      this.rules = []; this.versions = []; this.choicesError = ''; this.runVisible = true
      this.$nextTick(() => this.$refs.runForm && this.$refs.runForm.clearValidate())
      return this.runForm.templateId ? this.selectTemplate(selection) : Promise.resolve()
    },
    selectTemplate(selection = {}) {
      const scope = this.operations(), id = this.runForm.templateId, op = scope.begin('rules', id)
      scope.invalidate('versions'); scope.invalidate('run')
      this.runForm.ruleId = ''; this.runForm.versionId = ''; this.rules = []; this.versions = []
      this.choicesError = ''; this.choicesLoading = !!id; this.versionsLoading = false
      if (!id) return Promise.resolve()
      return getApprovalTemplate(id, { silentError: true }).then(response => {
        if (!this.runVisible || !scope.isCurrent(op, this.runForm.templateId)) return
        this.rules = toArray(unwrapData(response).rules)
        const requested = this.rules.find(r => String(r.ruleId) === String(selection.ruleId))
        const rule = requested || (this.rules.length === 1 ? this.rules[0] : null)
        if (rule) { this.runForm.ruleId = rule.ruleId; return this.selectRule(selection) }
        if (!this.rules.length) this.choicesError = '此模板还没有审批规则，请先在流程配置中创建规则。'
      }).catch(error => {
        if (scope.isCurrent(op, this.runForm.templateId)) this.choicesError = formatApprovalLoadError(error, '规则加载失败，请重试。').message
      }).finally(() => { if (scope.isCurrent(op, this.runForm.templateId)) this.choicesLoading = false })
    },
    selectRule(selection = {}) {
      const scope = this.operations(), id = this.runForm.ruleId, op = scope.begin('versions', id)
      scope.invalidate('run'); this.runForm.versionId = ''; this.versions = []; this.choicesError = ''; this.versionsLoading = !!id
      if (!id) return Promise.resolve()
      return getApprovalRule(id, { silentError: true }).then(response => {
        if (!this.runVisible || !scope.isCurrent(op, this.runForm.ruleId)) return
        const detail = unwrapData(response)
        if (!detail.rule || String(detail.rule.templateId) !== String(this.runForm.templateId) || String(detail.rule.ruleId) !== String(id)) throw new Error('规则与所选模板不一致，请重新选择')
        this.versions = toArray(detail.versions).filter(v => v.versionId && String(v.ruleId) === String(id))
        const requested = this.versions.find(v => String(v.versionId) === String(selection.versionId))
        const version = requested || (this.versions.length === 1 ? this.versions[0] : null)
        if (version) this.runForm.versionId = version.versionId
        if (selection.versionId && !requested) this.choicesError = '指定版本已不可用，请核对后重新选择版本。'
      }).catch(error => {
        if (scope.isCurrent(op, this.runForm.ruleId)) this.choicesError = formatApprovalLoadError(error, '版本加载失败，请重试。').message
      }).finally(() => { if (scope.isCurrent(op, this.runForm.ruleId)) this.versionsLoading = false })
    },
    versionChanged() { this.operations().invalidate('run'); this.runLoading = false },
    retryChoices() { return this.selectTemplate({ ...this.runForm }) },
    submitRun() {
      if (this.runLoading || this.choicesLoading || this.versionsLoading) return Promise.resolve()
      const scope = this.operations(), selection = { ...this.runForm }, op = scope.begin('run', selection)
      this.runLoading = true
      return new Promise(resolve => this.$refs.runForm.validate(resolve)).then(valid => {
        if (!valid || !this.runVisible || !scope.isCurrent(op, this.runForm)) return
        if (!this.versions.some(v => String(v.versionId) === String(selection.versionId))) throw new Error('请选择有效的规则版本')
        return runApprovalValidation({ versionId: selection.versionId, validationType: 'MANUAL' }).then(response => {
          if (!this.runVisible || !scope.isCurrent(op, this.runForm)) return
          this.$modal.msgSuccess('配置检查已完成'); this.runVisible = false; this.load()
          const value = unwrapData(response), result = value && value.run ? { ...value.run, issues: value.issues || value.issueList || [] } : value
          if (entityId(result, ['runId', 'id'])) this.openDetail(result)
        })
      }).catch(error => {
        if (scope.isCurrent(op, this.runForm)) this.$modal.msgError(formatApprovalLoadError(error, '检查未完成，请保留当前选择并重试。').message)
      }).finally(() => { if (scope.isCurrent(op, this.runForm)) this.runLoading = false })
    },
    openDetail(row) {
      const scope = this.operations(), id = entityId(row, ['runId', 'id']), op = scope.begin('detail', id)
      this.detailVisible = true; this.detailLoading = true; this.detail = null; this.detailError = ''
      return getApprovalValidationRun(id, { silentError: true }).then(response => {
        if (!this.detailVisible || !scope.isCurrent(op)) return
        const value = unwrapData(response); this.detail = value && value.run ? { ...value.run, issues: value.issues || value.issueList || [] } : value
      }).catch(error => {
        if (scope.isCurrent(op)) this.detailError = formatApprovalLoadError(error, '检查结果加载失败，请重新打开。').message
      }).finally(() => { if (scope.isCurrent(op)) this.detailLoading = false })
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
