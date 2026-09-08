<template>
  <div class="app-container position-config-page">
    <header class="page-heading">
      <div><h2>岗位入职配置</h2><p>按岗位和人员类别设置确认入职时的角色、数据范围与默认资料。</p></div>
      <el-button type="primary" icon="el-icon-plus" :disabled="!optionsReady" @click="openCreate" v-hasPermi="['hr:onboarding:config']">新增配置</el-button>
    </header>

    <el-alert v-if="optionsError" class="options-error" type="warning" :closable="false" :title="optionsError">
      <el-button slot="default" type="text" :loading="optionsLoading" @click="loadOptions">重试加载配置选项</el-button>
    </el-alert>

    <el-card shadow="never">
      <el-form inline size="small">
        <el-form-item label="岗位"><el-select v-model="query.postId" clearable filterable><el-option v-for="item in posts" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="人员类别"><el-select v-model="query.employeeCategory" clearable><el-option v-for="item in employeeCategories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable><el-option label="启用" value="0" /><el-option label="停用" value="1" /></el-select></el-form-item>
        <el-button type="primary" size="small" @click="search">查询</el-button><el-button size="small" @click="resetQuery">重置</el-button>
      </el-form>
      <el-table v-loading="loading" :data="rows" row-key="configId" @row-click="openDetail">
        <el-table-column label="岗位" min-width="150"><template slot-scope="scope">{{ optionLabel(posts, scope.row.postId) }}</template></el-table-column>
        <el-table-column label="人员类别" min-width="130"><template slot-scope="scope">{{ optionLabel(employeeCategories, scope.row.employeeCategory) }}</template></el-table-column>
        <el-table-column label="角色" min-width="180"><template slot-scope="scope">{{ roleLabels(scope.row.roleIds) }}</template></el-table-column>
        <el-table-column label="数据范围" min-width="130"><template slot-scope="scope">{{ optionLabel(dataScopeStrategies, scope.row.dataScopeStrategy) }}</template></el-table-column>
        <el-table-column prop="jobGrade" label="默认职级" width="110" />
        <el-table-column label="账号" width="100"><template slot-scope="scope">{{ scope.row.accountEnabled ? "启用" : "不启用" }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template slot-scope="scope"><el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">{{ scope.row.status === "0" ? "启用" : "停用" }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" :disabled="!optionsReady" @click.stop="openEdit(scope.row)" v-hasPermi="['hr:onboarding:config']">编辑</el-button>
            <el-button v-if="scope.row.status === '0'" type="text" class="danger-link" @click.stop="disable(scope.row)" v-hasPermi="['hr:onboarding:config']">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-if="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadList" />
    </el-card>

    <el-drawer title="配置详情" :visible.sync="detailVisible" size="480px" append-to-body>
      <div v-loading="detailLoading" class="detail-body" v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="岗位">{{ optionLabel(posts, detail.postId) }}</el-descriptions-item>
          <el-descriptions-item label="人员类别">{{ optionLabel(employeeCategories, detail.employeeCategory) }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabels(detail.roleIds) }}</el-descriptions-item>
          <el-descriptions-item label="数据范围">{{ optionLabel(dataScopeStrategies, detail.dataScopeStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="合同规则">{{ ruleSummary(detail.contractTypeMode, detail.defaultContractType, 'contractType') }}</el-descriptions-item>
          <el-descriptions-item label="社保规则">{{ ruleSummary(detail.socialTypeMode, detail.defaultSocialType, 'socialType') }}</el-descriptions-item>
          <el-descriptions-item label="试用期规则">{{ ruleSummary(detail.probationPeriodMode, detail.defaultProbationPeriod, 'probationPeriod') }}</el-descriptions-item>
          <el-descriptions-item label="默认职级">{{ detail.jobGrade || "未配置" }}</el-descriptions-item>
          <el-descriptions-item label="启用账号">{{ detail.accountEnabled ? "是" : "否" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status === "0" ? "启用" : "停用" }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <el-dialog :title="form.configId ? '编辑岗位入职配置' : '新增岗位入职配置'" :visible.sync="dialogVisible"
      :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" width="760px"
      custom-class="position-config-dialog" append-to-body>
      <el-alert v-if="conflictMessage" type="warning" :closable="false" :title="conflictMessage" class="conflict-alert" />
      <el-form ref="form" :model="form" :rules="rules" label-width="132px">
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="岗位" prop="postId"><el-select v-model="form.postId" filterable style="width:100%"><el-option v-for="item in posts" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="人员类别" prop="employeeCategory"><el-select v-model="form.employeeCategory" style="width:100%"><el-option v-for="item in employeeCategories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="角色" prop="roleIds"><el-select v-model="form.roleIds" multiple filterable style="width:100%"><el-option v-for="item in roles" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-row :gutter="18"><el-col :span="12"><el-form-item label="数据范围" prop="dataScopeStrategy"><el-select v-model="form.dataScopeStrategy" style="width:100%"><el-option v-for="item in dataScopeStrategies" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="默认职级"><el-input v-model.trim="form.jobGrade" maxlength="32" /></el-form-item></el-col></el-row>

        <div v-for="rule in ruleFields" :key="rule.modeKey" class="rule-row">
          <el-form-item :label="rule.label" :prop="rule.modeKey">
            <el-radio-group v-model="form[rule.modeKey]">
              <el-radio-button v-for="mode in ruleModes" :key="mode.value" :label="mode.value">{{ modeLabel(mode.value) }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form[rule.modeKey] !== 'NOT_APPLICABLE'" label="默认值" :prop="rule.defaultKey">
            <el-select v-model="form[rule.defaultKey]" clearable style="width:100%"><el-option v-for="item in dictionaryOptions(rule.dictionaryKey)" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          </el-form-item>
        </div>

        <el-row :gutter="18"><el-col :span="12"><el-form-item label="启用账号" prop="accountEnabled"><el-switch v-model="form.accountEnabled" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="状态"><el-radio-group v-if="form.configId" v-model="form.status"><el-radio label="0">启用</el-radio><el-radio label="1">停用</el-radio></el-radio-group><span v-else class="muted">新配置创建后默认启用</span></el-form-item></el-col></el-row>
        <el-form-item label="备注"><el-input v-model.trim="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <span slot="footer"><el-button :disabled="saving" @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit" v-hasPermi="['hr:onboarding:config']">保存</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import {
  createHrOnboardingPositionConfig, disableHrOnboardingPositionConfig, getHrOnboardingPositionConfig,
  getHrOnboardingPositionConfigOptions, listHrOnboardingPositionConfigs, updateHrOnboardingPositionConfig
} from "@/api/hr/onboarding"

const RULE_MODES = ["REQUIRED", "OPTIONAL", "NOT_APPLICABLE"]
const defaultQuery = () => ({ pageNum: 1, pageSize: 10, postId: undefined, employeeCategory: undefined, status: undefined })
const defaultForm = () => ({
  configId: undefined, postId: undefined, employeeCategory: undefined, roleIds: [], dataScopeStrategy: "TARGET_DEPT",
  contractTypeMode: "OPTIONAL", defaultContractType: undefined, socialTypeMode: "OPTIONAL", defaultSocialType: undefined,
  probationPeriodMode: "OPTIONAL", defaultProbationPeriod: undefined, jobGrade: "", accountEnabled: true,
  status: "0", version: undefined, remark: ""
})
const errorBody = error => (error && error.response && error.response.data) || (error && error.data) || error || {}

export default {
  name: "HrPositionConfig",
  data() {
    return {
      query: defaultQuery(), rows: [], total: 0, loading: false, options: {}, detail: null,
      detailVisible: false, detailLoading: false, dialogVisible: false, saving: false, conflictMessage: "", form: defaultForm(),
      listRequestSequence: 0, optionsRequestSequence: 0, detailRequestSequence: 0, editRequestSequence: 0,
      lastRouteFilterSignature: "",
      optionsLoading: false, optionsLoaded: false, optionsError: "",
      ruleFields: [
        { label: "合同规则", modeKey: "contractTypeMode", defaultKey: "defaultContractType", dictionaryKey: "contractType" },
        { label: "社保规则", modeKey: "socialTypeMode", defaultKey: "defaultSocialType", dictionaryKey: "socialType" },
        { label: "试用期规则", modeKey: "probationPeriodMode", defaultKey: "defaultProbationPeriod", dictionaryKey: "probationPeriod" }
      ],
      rules: {
        postId: [{ required: true, message: "请选择岗位", trigger: "change" }], employeeCategory: [{ required: true, message: "请选择人员类别", trigger: "change" }],
        dataScopeStrategy: [{ required: true, message: "请选择数据范围", trigger: "change" }]
      }
    }
  },
  computed: {
    posts() { return this.options.posts || [] }, roles() { return this.options.roles || [] },
    employeeCategories() { return this.options.employeeCategories || [] },
    ruleModes() { return this.options.ruleModes && this.options.ruleModes.length ? this.options.ruleModes : RULE_MODES.map(value => ({ label: { REQUIRED: "必填", OPTIONAL: "可选", NOT_APPLICABLE: "不适用" }[value], value })) },
    dataScopeStrategies() { return this.options.dataScopeStrategies || [] },
    optionsReady() { return this.optionsLoaded && !this.optionsLoading && !this.optionsError }
  },
  created() { this.loadOptions(); this.applyRouteFilters(); this.loadList() },
  activated() { return this.refreshRouteFilters() },
  watch: {
    "$route.query": {
      deep: true,
      handler() { return this.refreshRouteFilters() }
    }
  },
  beforeDestroy() { this.listRequestSequence += 1; this.optionsRequestSequence += 1; this.detailRequestSequence += 1; this.editRequestSequence += 1 },
  methods: {
    loadOptions() {
      const sequence = ++this.optionsRequestSequence
      this.optionsLoading = true; this.optionsError = ""
      return getHrOnboardingPositionConfigOptions().then(response => {
        if (sequence !== this.optionsRequestSequence) return
        this.options = response.data || {}; this.optionsLoaded = true
      }).catch(() => {
        if (sequence !== this.optionsRequestSequence) return
        this.options = {}; this.optionsLoaded = false
        this.optionsError = "岗位配置选项加载失败，请重试后再新增或编辑。"
      }).finally(() => { if (sequence === this.optionsRequestSequence) this.optionsLoading = false })
    },
    loadList() {
      const sequence = ++this.listRequestSequence
      this.loading = true
      return listHrOnboardingPositionConfigs({ ...this.query }).then(response => {
        if (sequence !== this.listRequestSequence) return
        this.rows = response.rows || []; this.total = Number(response.total) || 0
      }).catch(error => {
        if (sequence !== this.listRequestSequence) return
        this.rows = []; this.total = 0
        this.$message.error((error && error.message) || "配置加载失败")
      })
        .finally(() => { if (sequence === this.listRequestSequence) this.loading = false })
    },
    routeFilters() {
      const routeQuery = (this.$route && this.$route.query) || {}
      const postNumber = Number(routeQuery.postId)
      const postId = Number.isSafeInteger(postNumber) && postNumber > 0 && String(postNumber) === String(routeQuery.postId) ? postNumber : undefined
      const category = typeof routeQuery.employeeCategory === "string" ? routeQuery.employeeCategory.trim() : ""
      return { postId, employeeCategory: category && category.length <= 64 ? category : undefined }
    },
    routeFilterSignature() { return JSON.stringify(this.routeFilters()) },
    applyRouteFilters() {
      const filters = this.routeFilters()
      this.lastRouteFilterSignature = JSON.stringify(filters)
      this.query = { ...this.query, ...filters, pageNum: 1 }
      return filters
    },
    refreshRouteFilters() {
      const filters = this.routeFilters()
      const signature = JSON.stringify(filters)
      const hasDeepLink = filters.postId !== undefined || filters.employeeCategory !== undefined
      const currentMatches = this.query.postId === filters.postId && this.query.employeeCategory === filters.employeeCategory
      if (signature === this.lastRouteFilterSignature && (!hasDeepLink || currentMatches)) return Promise.resolve(null)
      this.applyRouteFilters()
      return this.loadList()
    },
    search() { this.query.pageNum = 1; return this.loadList() }, resetQuery() { this.query = defaultQuery(); return this.loadList() },
    openCreate() {
      if (this.saving) return
      if (!this.optionsReady) { this.$message.warning(this.optionsError || "请先加载岗位配置选项"); return }
      this.editRequestSequence += 1; this.form = defaultForm(); this.conflictMessage = ""; this.dialogVisible = true
    },
    openDetail(row) {
      if (!row || !row.configId) return
      const sequence = ++this.detailRequestSequence
      this.detailVisible = true; this.detailLoading = true; this.detail = null
      return getHrOnboardingPositionConfig(row.configId).then(response => { if (sequence === this.detailRequestSequence) this.detail = response.data || null })
        .catch(error => { if (sequence === this.detailRequestSequence) { this.detail = null; this.$message.error((error && error.message) || "配置详情加载失败") } })
        .finally(() => { if (sequence === this.detailRequestSequence) this.detailLoading = false })
    },
    openEdit(row) {
      if (this.saving || !row || !row.configId) return Promise.resolve(null)
      if (!this.optionsReady) { this.$message.warning(this.optionsError || "请先加载岗位配置选项"); return Promise.resolve(null) }
      const sequence = ++this.editRequestSequence
      this.conflictMessage = ""
      return getHrOnboardingPositionConfig(row.configId).then(response => {
        if (sequence !== this.editRequestSequence) return
        this.form = { ...defaultForm(), ...(response.data || {}), roleIds: [...((response.data && response.data.roleIds) || [])] }; this.dialogVisible = true
      }).catch(error => { if (sequence === this.editRequestSequence) this.$message.error((error && error.message) || "配置详情加载失败") })
    },
    buildConfigPayload() {
      const payload = { ...this.form, roleIds: [...(this.form.roleIds || [])] }
      if (payload.contractTypeMode === "NOT_APPLICABLE") payload.defaultContractType = null
      if (payload.socialTypeMode === "NOT_APPLICABLE") payload.defaultSocialType = null
      if (payload.probationPeriodMode === "NOT_APPLICABLE") payload.defaultProbationPeriod = null
      delete payload.configId
      if (this.form.configId === undefined) delete payload.version
      return payload
    },
    submit() {
      if (this.saving) return Promise.resolve(null)
      this.saving = true
      return new Promise(resolve => this.$refs.form.validate(resolve)).then(valid => {
        if (!valid) return null
        this.conflictMessage = ""
        const payload = this.buildConfigPayload()
        const request = this.form.configId ? updateHrOnboardingPositionConfig(this.form.configId, payload) : createHrOnboardingPositionConfig(payload)
        return request.then(() => { this.dialogVisible = false; this.$message.success("配置已保存"); return this.loadList() })
          .catch(error => {
            if (errorBody(error).errorCode === "POSITION_CONFIG_VERSION_CONFLICT" && this.form.configId) return this.refreshAfterConflict(this.form.configId)
            this.$message.error(errorBody(error).msg || errorBody(error).message || "配置保存失败"); return null
          })
      }).finally(() => { this.saving = false })
    },
    refreshAfterConflict(configId) {
      return getHrOnboardingPositionConfig(configId).then(response => {
        this.form = { ...defaultForm(), ...(response.data || {}), roleIds: [...((response.data && response.data.roleIds) || [])] }
        this.conflictMessage = "配置已被其他人修改，已刷新为最新版本，请核对后重新保存。"
        this.$message.warning(this.conflictMessage)
      }).catch(() => {
        this.conflictMessage = "配置发生版本冲突，最新配置刷新失败，请关闭窗口后重新打开。"
        this.$message.error(this.conflictMessage)
      })
    },
    disable(row) { return this.$confirm("停用后，新入职确认将不再使用该配置。", "停用配置", { type: "warning" }).then(() => disableHrOnboardingPositionConfig(row.configId, row.version)).then(() => { this.$message.success("配置已停用"); return this.loadList() }).catch(error => { if (error === "cancel" || error === "close") return; if (errorBody(error).errorCode === "POSITION_CONFIG_VERSION_CONFLICT") { this.$message.warning("配置已更新，请刷新后重试"); return this.loadList() } this.$message.error(errorBody(error).msg || "停用失败") }) },
    dictionaryOptions(key) { return (this.options.dictionaryDefaults && this.options.dictionaryDefaults[key]) || [] },
    optionLabel(options, value) { const option = (options || []).find(item => String(item.value) === String(value)); return option ? option.label : (value ? "未登记中文名称" : "—") },
    roleLabels(roleIds) { const labels = (roleIds || []).map(id => this.optionLabel(this.roles, id)); return labels.length ? labels.join("、") : "未配置" },
    modeLabel(mode) { return { REQUIRED: "必填", OPTIONAL: "可选", NOT_APPLICABLE: "不适用" }[mode] || "未知规则模式" },
    ruleSummary(mode, value, key) { if (mode === "NOT_APPLICABLE") return "不适用"; return `${this.modeLabel(mode)} · ${this.optionLabel(this.dictionaryOptions(key), value)}` }
  }
}
</script>

<style lang="scss" scoped>
.position-config-page { min-height:calc(100vh - 84px); background:#f6f8fb; }
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:18px; }
.page-heading h2 { margin:0 0 6px; color:#172033; }.page-heading p { margin:0; color:#64748b; }
.danger-link { color:#b45353; }.detail-body { padding:0 22px; }.conflict-alert { margin-bottom:16px; }
.rule-row { margin:8px 0 18px; padding:14px 12px 2px; border:1px solid #e5eaf2; border-radius:8px; background:#fafbfc; }
::v-deep .position-config-dialog { max-height:90vh; margin-top:5vh !important; display:flex; flex-direction:column; }
::v-deep .position-config-dialog .el-dialog__body { overflow-y:auto; padding-bottom:14px; }
::v-deep .position-config-dialog .el-dialog__footer { flex-shrink:0; padding-top:14px; border-top:1px solid #e5e7eb; background:#fff; }
</style>
