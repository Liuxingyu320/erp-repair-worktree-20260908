<template>
  <section class="flow-configuration">
    <el-card shadow="never" class="template-card">
      <div slot="header" class="card-heading">
        <div><strong>选择业务模板</strong><small>模板限定可配置范围，避免自由流程设计带来的维护风险。</small></div>
        <el-button size="small" icon="el-icon-refresh" :loading="templateLoading || loading" @click="$emit('refresh-templates')">刷新</el-button>
      </div>
      <div v-loading="templateLoading" class="template-grid">
        <button
          v-for="item in templates"
          :key="item.businessCode"
          type="button"
          class="template-option"
          :class="{ active: selectedBusinessCode === item.businessCode }"
          @click="selectTemplate(item)"
        >
          <span class="template-icon"><i :class="templateIcon(item.businessCode)"></i></span>
          <span class="template-copy">
            <strong>{{ item.templateName || item.businessName }}</strong>
            <small>{{ item.businessDescription || '受控审批模板' }}</small>
          </span>
          <span class="template-tags">
            <el-tag :type="String(item.engineMode).toUpperCase() === 'NATIVE' ? 'success' : 'info'" size="mini">{{ engineLabel(item.engineMode) }}</el-tag>
            <el-tag :type="statusType(item.templateStatus || item.status)" size="mini">{{ statusLabel(item.templateStatus || item.status) }}</el-tag>
          </span>
        </button>
        <el-empty v-if="!templateLoading && !templateLoadError && !templates.length" description="暂无审批业务模板" :image-size="70" />
      </div>
    </el-card>

    <el-card shadow="never">
      <div slot="header" class="card-heading">
        <div>
          <strong>{{ selectedTemplate ? `${selectedTemplate.templateName || selectedTemplate.businessName} · 适用规则` : '适用规则' }}</strong>
          <small>已发布版本不可修改；调整时复制为新草稿，通过检查后再发布。</small>
        </div>
        <el-button
          v-hasPermi="['approval:template:edit']"
          type="primary"
          size="small"
          icon="el-icon-plus"
          :disabled="!selectedTemplate"
          @click="openNewRule"
        >新建适用规则</el-button>
      </div>

      <approval-load-error
        v-if="rulesLoadError"
        title="审批规则加载失败"
        :error="rulesLoadError"
        :loading="loading"
        class="panel-load-error"
        @retry="retryRules"
      />
      <el-alert
        v-else-if="ruleDetailFailureCount > 0"
        :title="`${ruleDetailFailureCount} 条规则的详情暂不可用，当前显示列表基础信息。`"
        type="warning"
        :closable="false"
        show-icon
        class="panel-load-error"
      />

      <el-table v-loading="loading" :data="rules" size="small">
        <el-table-column label="规则名称" prop="ruleName" min-width="180" />
        <el-table-column label="组织范围" min-width="160"><template slot-scope="scope">{{ scopeText(scope.row) }}</template></el-table-column>
        <el-table-column label="业务子类型" min-width="125"><template slot-scope="scope">{{ businessSubtypeLabel(scope.row.businessSubtype) }}</template></el-table-column>
        <el-table-column label="当前发布版本" width="135"><template slot-scope="scope">{{ versionText(scope.row) }}</template></el-table-column>
        <el-table-column label="草稿" width="105">
          <template slot-scope="scope"><el-tag v-if="draftVersion(scope.row)" type="warning" size="mini">V{{ draftVersion(scope.row).versionNo || '新' }}</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="状态" width="95"><template slot-scope="scope"><el-tag :type="statusType(scope.row.ruleStatus || scope.row.status)" size="mini">{{ statusLabel(scope.row.ruleStatus || scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="165" />
        <el-table-column label="操作" width="350" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-if="draftVersion(scope.row)"
              v-hasPermi="['approval:template:edit']"
              type="text"
              size="mini"
              :loading="copyingRuleId === ruleId(scope.row)"
              @click="continueDraft(scope.row)"
            >继续配置</el-button>
            <el-button
              v-else
              v-hasPermi="['approval:template:edit']"
              type="text"
              size="mini"
              :loading="copyingRuleId === ruleId(scope.row)"
              @click="copyAsDraft(scope.row)"
            >复制新版本</el-button>
            <el-button v-if="scope.row.currentVersionId" v-hasPermi="['approval:validation:run']" type="text" size="mini"
              @click="$emit('check-version', { templateId: scope.row.templateId, ruleId: ruleId(scope.row), versionId: scope.row.currentVersionId })">检查当前版本</el-button>
            <el-button
              v-if="String(scope.row.ruleStatus || scope.row.status).toUpperCase() !== 'DISABLED'"
              v-hasPermi="['approval:template:edit']"
              type="text"
              size="mini"
              class="danger-text"
              @click="disableRule(scope.row)"
            >停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadRules" />
    </el-card>

    <rule-wizard
      :visible.sync="wizardVisible"
      :template="wizardTemplate"
      :rule="wizardRule"
      :version="wizardVersion"
      @saved="loadRules"
      @published="handlePublished"
    />
  </section>
</template>

<script>
import { createApprovalRuleDraft, disableApprovalRule, getApprovalRule, listApprovalRules } from '@/api/approval/definition'
import ApprovalLoadError from './ApprovalLoadError'
import RuleWizard from './RuleWizard'
import { businessSubtypeLabel, entityId, formatApprovalLoadError, statusLabel, statusType, unwrapData, unwrapRows } from './approvalUi'

function ruleDetail(value, fallback = {}) {
  const detail = value && typeof value === 'object' ? value : {}
  const rule = detail.rule && typeof detail.rule === 'object' ? detail.rule : fallback
  const versions = Array.isArray(detail.versions) ? detail.versions : Array.isArray(fallback.versions) ? fallback.versions : []
  return { ...fallback, ...rule, template: detail.template || fallback.template, versions }
}

export default {
  name: 'ApprovalFlowConfiguration',
  components: { ApprovalLoadError, RuleWizard },
  props: {
    templates: { type: Array, default: () => [] },
    templateLoading: { type: Boolean, default: false },
    templateLoadError: { type: Object, default: null }
  },
  data() {
    return {
      selectedBusinessCode: '',
      loading: false,
      rulesLoadError: null,
      ruleDetailFailureCount: 0,
      rules: [],
      total: 0,
      query: { pageNum: 1, pageSize: 10 },
      wizardVisible: false,
      wizardTemplate: {},
      wizardRule: {},
      wizardVersion: {},
      copyingRuleId: undefined
    }
  },
  computed: {
    selectedTemplate() { return this.templates.find(item => item.businessCode === this.selectedBusinessCode) || null }
  },
  watch: {
    templates: {
      immediate: true,
      handler(items) {
        if (!items || !items.length) {
          this.selectedBusinessCode = ''
          this.rules = []
          this.total = 0
          return
        }
        if (!items.some(item => item.businessCode === this.selectedBusinessCode)) {
          this.selectedBusinessCode = items[0].businessCode
          this.rules = []
          this.total = 0
        }
        this.$nextTick(this.loadRules)
      }
    }
  },
  methods: {
    statusLabel,
    statusType,
    businessSubtypeLabel,
    ruleId(row) { return entityId(row, ['ruleId', 'id']) },
    templateIcon(code) {
      return { OA_PURCHASE: 'el-icon-shopping-cart-full', OA_REIMBURSEMENT: 'el-icon-wallet', INV_TRANSFER: 'el-icon-sort', INV_STOCK_CHECK: 'el-icon-finished', HR_HEALTH_CERTIFICATE: 'el-icon-first-aid-kit' }[code] || 'el-icon-s-check'
    },
    engineLabel(mode) { return String(mode || '').toUpperCase() === 'NATIVE' ? '统一引擎' : '兼容引擎' },
    selectTemplate(item) {
      if (item.businessCode === this.selectedBusinessCode) return
      this.selectedBusinessCode = item.businessCode
      this.query.pageNum = 1
      this.rules = []
      this.total = 0
      this.loadRules()
    },
    loadRules() {
      if (!this.selectedTemplate) {
        this.rules = []
        this.total = 0
        this.rulesLoadError = null
        this.ruleDetailFailureCount = 0
        return Promise.resolve()
      }
      this.loading = true
      this.rules = []
      this.total = 0
      this.rulesLoadError = null
      this.ruleDetailFailureCount = 0
      return listApprovalRules({
        templateId: entityId(this.selectedTemplate, ['templateId', 'id']),
        businessCode: this.selectedTemplate.businessCode,
        pageNum: this.query.pageNum,
        pageSize: this.query.pageSize
      }, { silentError: true }).then(response => {
        const page = unwrapRows(response)
        this.total = page.total
        return Promise.all(page.rows.map(row => {
          const id = this.ruleId(row)
          if (!id) return Promise.resolve({ row, detailFailed: false })
          return getApprovalRule(id, { silentError: true })
            .then(detailResponse => ({ row: ruleDetail(unwrapData(detailResponse), row), detailFailed: false }))
            .catch(() => ({ row, detailFailed: true }))
        })).then(results => {
          this.ruleDetailFailureCount = results.filter(item => item.detailFailed).length
          this.rules = results.map(item => item.row)
        })
      }).catch(error => {
        this.rulesLoadError = formatApprovalLoadError(
          error,
          '审批规则服务暂不可用；未返回的数据不会被当作空规则。'
        )
        return { error }
      }).finally(() => { this.loading = false })
    },
    retryRules() {
      return this.loadRules()
    },
    scopeText(row) {
      const scope = String(row.scopeType || 'ALL').toUpperCase()
      if (scope === 'ALL') return '全部组织'
      return `${scope === 'AREA' ? '区域' : '门店'}：${row.scopeName || row.scopeId || '-'}`
    },
    versionText(row) {
      const versions = Array.isArray(row.versions) ? row.versions : []
      const version = versions.find(item => String(item.versionId) === String(row.currentVersionId)) ||
        row.currentVersion || row.publishedVersion || {}
      const versionNo = version.versionNo || (row.currentVersionId ? row.latestVersionNo : undefined)
      return versionNo
        ? `V${versionNo}`
        : '尚未发布'
    },
    draftVersion(row) {
      const versions = Array.isArray(row.versions) ? row.versions : []
      const draft = versions.find(item => String(item.versionStatus || item.status).toUpperCase() === 'DRAFT')
      if (draft) return draft
      if (row.draftVersion && typeof row.draftVersion === 'object') return row.draftVersion
      if (row.currentDraftVersion && typeof row.currentDraftVersion === 'object') return row.currentDraftVersion
      if (row.draftVersionId) return { versionId: row.draftVersionId, versionNo: row.draftVersionNo }
      return null
    },
    openWizard(rule, version) {
      this.wizardTemplate = this.selectedTemplate || {}
      this.wizardRule = rule || {}
      this.wizardVersion = version || {}
      this.wizardVisible = true
    },
    openNewRule() { this.openWizard({}, {}) },
    continueDraft(row) {
      const id = this.ruleId(row)
      this.copyingRuleId = id
      return getApprovalRule(id).then(response => {
        const detail = ruleDetail(unwrapData(response), row)
        this.openWizard(detail, this.draftVersion(detail))
      }).finally(() => { this.copyingRuleId = undefined })
    },
    copyAsDraft(row) {
      const id = this.ruleId(row)
      this.$confirm('将当前已发布版本复制为新草稿？旧版本及运行实例不会受到影响。', '复制新版本', {
        confirmButtonText: '确认复制', cancelButtonText: '取消', type: 'info'
      }).then(() => {
        this.copyingRuleId = id
        return getApprovalRule(id).then(detailResponse => {
          const detail = ruleDetail(unwrapData(detailResponse), row)
          const existingDraft = this.draftVersion(detail)
          if (existingDraft) {
            this.openWizard(detail, existingDraft)
            return null
          }
          const current = detail.versions.find(item => String(item.versionId) === String(detail.currentVersionId)) || {}
          const sourceVersionId = detail.currentVersionId || entityId(current, ['versionId', 'id'])
          if (!sourceVersionId) throw new Error('当前规则没有可复制的已发布版本')
          return createApprovalRuleDraft(id, { sourceVersionId })
        })
          .then(response => {
            if (!response) return undefined
            const value = unwrapData(response)
            const createdDraft = value && value.version ? value.version : value
            return getApprovalRule(id).then(detailResponse => {
              const detail = ruleDetail(unwrapData(detailResponse), row)
              this.openWizard(detail, this.draftVersion(detail) || createdDraft)
              this.loadRules()
            })
          }).finally(() => { this.copyingRuleId = undefined })
      }).catch(() => {})
    },
    disableRule(row) {
      this.$prompt('请输入停用原因，已发布版本和历史实例仍会保留。', '停用审批规则', {
        confirmButtonText: '确认停用', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: value => String(value || '').trim().length >= 2 || '停用原因至少2个字符'
      }).then(({ value }) => disableApprovalRule(this.ruleId(row), {
        expectedLockVersion: row.lockVersion,
        remark: String(value).trim()
      })).then(() => {
        this.$modal.msgSuccess('规则已停用')
        this.loadRules()
      }).catch(() => {})
    },
    handlePublished() { this.loadRules(); this.$emit('changed') }
  }
}
</script>

<style lang="scss" scoped>
.flow-configuration { display: flex; flex-direction: column; gap: 12px; }
.template-card { border-radius: 12px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.card-heading div { display: flex; flex-direction: column; gap: 4px; }
.card-heading small { color: #8492a6; font-size: 12px; }
.panel-load-error { margin-bottom: 12px; }
.template-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; min-height: 90px; }
.template-option { display: flex; align-items: center; gap: 10px; min-width: 0; padding: 14px; border: 1px solid var(--erp-border, #dde2de); border-radius: 10px; background: #fff; color: inherit; text-align: left; cursor: pointer; transition: border-color var(--motion-duration-fast) var(--motion-ease-standard), box-shadow var(--motion-duration-fast) var(--motion-ease-standard); }
.template-option:hover, .template-option.active { border-color: #8fb3a4; box-shadow: 0 5px 16px rgba(23, 33, 29, .08); }
.template-option.active { background: var(--erp-primary-soft, #e7f2ed); }
.template-icon { display: grid; place-items: center; flex: 0 0 38px; height: 38px; border-radius: 10px; background: var(--erp-primary-soft, #e7f2ed); color: var(--erp-primary, #0b6b53); font-size: 18px; }
.template-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 4px; }
.template-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.template-copy small { color: #909399; }
.template-tags { display: flex; flex-direction: column; gap: 4px; }
.danger-text { color: #f56c6c; }
@media (max-width: 1200px) { .template-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
