<template>
  <el-dialog
    :title="wizardTitle"
    :visible="visible"
    width="1280px"
    top="4vh"
    append-to-body
    :close-on-click-modal="false"
    :before-close="close"
  >
    <el-steps :active="activeStep" finish-status="success" align-center class="wizard-steps">
      <el-step title="业务模板" />
      <el-step title="适用范围" />
      <el-step title="节点参数" />
      <el-step title="候选人预览" />
      <el-step title="配置检查" />
      <el-step title="发布版本" />
    </el-steps>

    <div v-loading="saving" class="wizard-content">
      <section v-show="activeStep === 0" class="wizard-section">
        <el-alert
          :title="isTransfer ? '调拨审批使用固定四节点模板，顺序和节点数量不可修改。' : '每条规则必须选择一个受控业务模板，不能自由编排流程图。'"
          type="info"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="isActiveRule"
          title="当前规则已发布，本次只编辑新版本的节点参数；如需调整规则名称、组织范围或业务子类型，请新建适用规则。"
          type="warning"
          :closable="false"
          show-icon
          class="active-rule-alert"
        />
        <el-form ref="basicForm" :model="form" :rules="basicRules" label-width="110px" class="step-form">
          <el-form-item label="业务模板">
            <el-input :value="templateLabel" disabled />
          </el-form-item>
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model.trim="form.ruleName" :disabled="isActiveRule" maxlength="128" show-word-limit placeholder="例如：全部门店采购审批" />
          </el-form-item>
          <el-form-item label="引擎模式">
            <el-tag :type="String(template.engineMode).toUpperCase() === 'NATIVE' ? 'success' : 'info'">{{ engineLabel }}</el-tag>
            <span class="inline-help">兼容模式只统一展示配置和运行信息，原生模式由统一引擎执行。</span>
          </el-form-item>
        </el-form>
      </section>

      <section v-show="activeStep === 1" class="wizard-section">
        <el-form ref="scopeForm" :model="form" :rules="scopeRules" label-width="110px" class="step-form">
          <el-form-item label="组织范围" prop="scopeType">
            <el-radio-group v-model="form.scopeType" :disabled="isActiveRule">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="AREA">区域</el-radio-button>
              <el-radio-button label="STORE">门店</el-radio-button>
            </el-radio-group>
            <div class="field-help">匹配精度固定为：门店 &gt; 区域 &gt; 全部，同精度冲突将阻止发布。</div>
          </el-form-item>
          <el-form-item v-if="form.scopeType !== 'ALL'" label="范围编号" prop="scopeId">
            <el-input v-model.trim="form.scopeId" :disabled="isActiveRule" placeholder="请输入区域或门店组织编号" />
          </el-form-item>
          <el-form-item v-if="form.scopeType !== 'ALL'" label="范围名称">
            <el-input v-model.trim="form.scopeName" :disabled="isActiveRule" maxlength="128" placeholder="选填，用于配置审计和识别" />
          </el-form-item>
          <el-form-item label="业务子类型">
            <el-select v-if="isTransfer" v-model="form.businessSubtype" :disabled="isActiveRule" clearable placeholder="全部子类型" style="width:100%">
              <el-option label="门店要货" value="warehouse" />
              <el-option label="门店返仓" value="store_return" />
              <el-option label="门店调货" value="store" />
              <el-option label="OE 补货" value="oe" />
            </el-select>
            <el-input v-else v-model.trim="form.businessSubtype" :disabled="isActiveRule" maxlength="64" placeholder="为空表示全部子类型" />
            <div class="field-help">指定子类型优先于全部子类型；调拨可选择门店要货、门店返仓、门店调货或 OE 补货。</div>
          </el-form-item>
          <el-form-item label="版本说明">
            <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明本版本调整内容" />
          </el-form-item>
        </el-form>
      </section>

      <section v-show="activeStep === 2" class="wizard-section">
        <el-alert
          v-if="isTransfer"
          title="固定链路：四级负责人 → 三级负责人 → 运营总监 → 总经理。四级、三级缺人可跳过并告警；运营总监、总经理缺人阻止提交。"
          type="success"
          :closable="false"
          show-icon
          class="node-alert"
        />
        <div v-else class="node-toolbar">
          <span>只配置模板允许开放的节点参数；已发布版本不可直接修改。</span>
          <el-button type="primary" plain size="small" icon="el-icon-plus" @click="addNode">新增节点</el-button>
        </div>
        <el-table :data="form.nodes" border size="small" class="node-table">
          <el-table-column label="顺序" prop="nodeOrder" width="65" align="center" />
          <el-table-column label="节点名称" min-width="145">
            <template slot-scope="scope"><el-input v-model.trim="scope.row.nodeName" :disabled="isTransfer" size="mini" maxlength="64" /></template>
          </el-table-column>
          <el-table-column label="审批人来源" min-width="180">
            <template slot-scope="scope">
              <el-select v-model="scope.row.strategyType" :disabled="isTransfer" size="mini" style="width:100%" @change="strategyTypeChanged(scope.row)">
                <el-option label="责任岗位" value="RESPONSIBILITY_POST" />
                <el-option label="组织负责人" value="ORG_LEADER" />
                <el-option label="权限持有人" value="PERMISSION_HOLDER" />
                <el-option label="固定用户" value="FIXED_USERS" />
                <el-option label="业务策略（高级）" value="BUSINESS_STRATEGY" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="策略编码/岗位" min-width="190">
            <template slot-scope="scope"><el-input v-model.trim="scope.row.strategyCode" :disabled="isTransfer || scope.row.strategyType === 'PERMISSION_HOLDER' || scope.row.strategyType === 'FIXED_USERS'" size="mini" maxlength="64" /></template>
          </el-table-column>
          <el-table-column v-if="!isTransfer" label="策略参数" min-width="230">
            <template slot-scope="scope">
              <el-input
                v-model.trim="scope.row.strategyParam"
                size="mini"
                :placeholder="strategyParamPlaceholder(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="多人方式" min-width="135">
            <template slot-scope="scope">
              <el-select v-model="scope.row.approvalMode" :disabled="isTransfer" size="mini" style="width:100%">
                <el-option label="唯一最优人" value="UNIQUE_BEST" />
                <el-option label="任一人通过" value="ANY_ONE" />
                <el-option label="所有人通过" value="ALL" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="缺人处理" min-width="120">
            <template slot-scope="scope">
              <el-select v-model="scope.row.missingPolicy" :disabled="isTransfer" size="mini" style="width:100%">
                <el-option label="阻止提交" value="BLOCK" />
                <el-option label="跳过并告警" value="SKIP_WARN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="!isTransfer" label="排序" width="130" align="center">
            <template slot-scope="scope">
              <el-button type="text" size="mini" :disabled="scope.$index === 0" @click="moveNode(scope.$index, -1)">上移</el-button>
              <el-button type="text" size="mini" :disabled="scope.$index === form.nodes.length - 1" @click="moveNode(scope.$index, 1)">下移</el-button>
              <el-button type="text" size="mini" class="danger-text" @click="removeNode(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="isTransfer" class="transfer-rule-grid">
          <div><strong>四级负责人</strong><span>店长优先，无店长时店长助理</span></div>
          <div><strong>三级负责人</strong><span>高于店长、低于运营总监与总经理，岗位排序最接近店长</span></div>
          <div><strong>运营总监</strong><span>责任范围内必须存在运营总监岗位</span></div>
          <div><strong>总经理</strong><span>责任范围内必须存在总经理岗位；总经理不可发起</span></div>
        </div>
      </section>

      <section v-show="activeStep === 3" class="wizard-section">
        <el-form ref="previewForm" :model="previewForm" :rules="previewRules" inline size="small" class="preview-form">
          <el-form-item label="锚点组织编号" prop="anchorDeptId"><el-input v-model.trim="previewForm.anchorDeptId" placeholder="门店或组织编号" /></el-form-item>
          <el-form-item label="模拟申请人编号"><el-input v-model.trim="previewForm.applicantId" placeholder="选填，用于自审跳过校验" /></el-form-item>
          <el-form-item label="业务子类型">
            <el-select v-if="isTransfer" v-model="previewForm.businessSubtype" clearable placeholder="默认使用规则子类型">
              <el-option label="门店要货" value="warehouse" />
              <el-option label="门店返仓" value="store_return" />
              <el-option label="门店调货" value="store" />
              <el-option label="OE 补货" value="oe" />
            </el-select>
            <el-input v-else v-model.trim="previewForm.businessSubtype" placeholder="默认使用规则子类型" />
          </el-form-item>
          <el-form-item><el-button type="primary" icon="el-icon-view" :loading="previewLoading" @click="loadPreview">预览审批人</el-button></el-form-item>
        </el-form>
        <el-alert v-if="previewResult && previewBlocked" :title="previewBlockedReason" type="error" :closable="false" show-icon class="preview-alert" />
        <el-alert v-for="(warning, index) in previewWarnings" :key="index" :title="warning" type="warning" :closable="false" show-icon class="preview-alert" />
        <el-table v-if="previewResult" :data="previewNodes" border size="small">
          <el-table-column label="顺序" prop="nodeOrder" width="65" align="center" />
          <el-table-column label="节点" prop="nodeName" min-width="150" />
          <el-table-column label="解析结果" width="120">
            <template slot-scope="scope"><el-tag :type="scope.row.blocking ? 'danger' : scope.row.skipped ? 'warning' : 'success'" size="mini">{{ scope.row.blocking ? '阻断' : scope.row.skipped ? '跳过' : '已匹配' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="候选人" min-width="230"><template slot-scope="scope">{{ previewCandidateNames(scope.row) }}</template></el-table-column>
          <el-table-column label="说明" min-width="260"><template slot-scope="scope">{{ scope.row.reason || scope.row.message || '-' }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="填写锚点组织后预览每个节点的最终候选人" :image-size="90" />
      </section>

      <section v-show="activeStep === 4" class="wizard-section">
        <div class="check-toolbar">
          <div><h3>发布前配置检查</h3><p>检查当前版本及其组织覆盖；任何错误都会阻止进入发布。</p></div>
          <el-button type="primary" icon="el-icon-cpu" :loading="validationLoading" @click="runValidation">执行检查</el-button>
        </div>
        <el-alert v-if="validationResult" :title="validationSummary" :type="validationPassed ? 'success' : 'error'" :closable="false" show-icon class="preview-alert" />
        <el-table v-if="validationResult" :data="validationIssues" border size="small">
          <el-table-column label="级别" width="90"><template slot-scope="scope"><el-tag :type="issueType(scope.row.severity)" size="mini">{{ issueLabel(scope.row.severity) }}</el-tag></template></el-table-column>
          <el-table-column label="检查项" min-width="150"><template slot-scope="scope">{{ validationIssueLabel(scope.row.issueType || scope.row.issueCode) }}</template></el-table-column>
          <el-table-column label="问题说明" min-width="280"><template slot-scope="scope">{{ scope.row.message || scope.row.issueMessage || '-' }}</template></el-table-column>
          <el-table-column label="修复建议" prop="suggestion" min-width="240" />
        </el-table>
        <el-empty v-else description="尚未执行配置检查" :image-size="90" />
      </section>

      <section v-show="activeStep === 5" class="wizard-section publish-section">
        <div class="publish-summary">
          <i :class="validationPassed ? 'el-icon-circle-check success-icon' : 'el-icon-circle-close error-icon'"></i>
          <h3>{{ validationPassed ? '可以发布新版本' : '配置检查尚未通过' }}</h3>
          <p>{{ validationPassed ? '发布后版本不可修改，运行中的实例永久保留此版本与候选人快照。' : '返回配置检查并修复所有错误后再发布。' }}</p>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="业务模板">{{ templateLabel }}</el-descriptions-item>
          <el-descriptions-item label="规则名称">{{ form.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="适用范围">{{ scopeSummary }}</el-descriptions-item>
          <el-descriptions-item label="业务子类型">{{ businessSubtypeLabel(form.businessSubtype) }}</el-descriptions-item>
          <el-descriptions-item label="节点数量">{{ form.nodes.length }}</el-descriptions-item>
          <el-descriptions-item label="草稿版本">{{ currentVersionNo ? `V${currentVersionNo}` : '新版本' }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </div>

    <div slot="footer" class="wizard-footer">
      <el-button :disabled="saving" @click="close">取消</el-button>
      <div>
        <el-button v-if="activeStep > 0" :disabled="saving" @click="activeStep -= 1">上一步</el-button>
        <el-button v-if="activeStep >= 2 && activeStep < 5" :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button v-if="activeStep < 5" type="primary" :disabled="activeStep === 4 && !validationPassed" :loading="saving" @click="next">下一步</el-button>
        <el-button v-else v-hasPermi="['approval:template:publish']" type="success" :disabled="!validationPassed" :loading="publishing" @click="publish">发布新版本</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import {
  createApprovalRule,
  createApprovalRuleDraft,
  publishApprovalVersion,
  previewApprovalCandidates,
  updateApprovalRule,
  updateApprovalVersion
} from '@/api/approval/definition'
import { runApprovalValidation } from '@/api/approval/validation'
import { businessSubtypeLabel, entityId, toArray, unwrapData, validationIssueLabel } from './approvalUi'

const TRANSFER_NODES = Object.freeze([
  { nodeOrder: 1, nodeCode: 'L4_MANAGER', nodeName: '四级负责人', strategyType: 'BUSINESS_STRATEGY', strategyCode: 'TRANSFER_LEVEL4_MANAGER', approvalMode: 'UNIQUE_BEST', requiredCount: 1, missingPolicy: 'SKIP_WARN', selfPolicy: 'SKIP_SELF_AND_LOWER', returnAllowed: '1', rejectAllowed: '1' },
  { nodeOrder: 2, nodeCode: 'L3_MANAGER', nodeName: '三级负责人', strategyType: 'BUSINESS_STRATEGY', strategyCode: 'TRANSFER_LEVEL3_MANAGER', approvalMode: 'UNIQUE_BEST', requiredCount: 1, missingPolicy: 'SKIP_WARN', selfPolicy: 'SKIP_SELF_AND_LOWER', returnAllowed: '1', rejectAllowed: '1' },
  { nodeOrder: 3, nodeCode: 'OPERATIONS_DIRECTOR', nodeName: '运营总监', strategyType: 'BUSINESS_STRATEGY', strategyCode: 'TRANSFER_OPERATIONS_DIRECTOR', approvalMode: 'UNIQUE_BEST', requiredCount: 1, missingPolicy: 'BLOCK', selfPolicy: 'SKIP_SELF_AND_LOWER', returnAllowed: '1', rejectAllowed: '1', strategyConfig: JSON.stringify({ postCode: 'yyzj' }) },
  { nodeOrder: 4, nodeCode: 'GENERAL_MANAGER', nodeName: '总经理', strategyType: 'BUSINESS_STRATEGY', strategyCode: 'TRANSFER_GENERAL_MANAGER', approvalMode: 'UNIQUE_BEST', requiredCount: 1, missingPolicy: 'BLOCK', selfPolicy: 'BLOCK', returnAllowed: '1', rejectAllowed: '1', strategyConfig: JSON.stringify({ postCode: 'zjl', applicantForbidden: true }) }
])

const APPROVAL_PERMISSION_BY_BUSINESS = Object.freeze({
  OA_PURCHASE: 'oa:todo:approve',
  OA_REIMBURSEMENT: 'oa:reimbursement:approve',
  INV_TRANSFER: 'inv:transfer:approve',
  INV_STOCK_CHECK: 'inv:stockCheck:approve',
  HR_HEALTH_CERTIFICATE: 'hr:healthCertificate:review'
})

function copy(value) {
  return value === undefined || value === null ? value : JSON.parse(JSON.stringify(value))
}

function blankNode(order) {
  return {
    nodeOrder: order,
    nodeCode: `NODE_${order}`,
    nodeName: `审批节点${order}`,
    strategyType: 'RESPONSIBILITY_POST',
    strategyCode: 'RESPONSIBILITY_POST',
    approvalMode: 'UNIQUE_BEST',
    requiredCount: 1,
    missingPolicy: 'BLOCK',
    selfPolicy: 'SKIP',
    returnAllowed: '1',
    rejectAllowed: '1',
    strategyParam: ''
  }
}

function defaultNodes(businessCode) {
  if (businessCode === 'INV_TRANSFER') return copy(TRANSFER_NODES)
  if (businessCode === 'OA_REIMBURSEMENT') {
    return [
      { ...blankNode(1), nodeCode: 'DEPARTMENT_LEADER', nodeName: '部门负责人', strategyType: 'ORG_LEADER', strategyCode: 'ORG_LEADER', selfPolicy: 'SKIP' },
      { ...blankNode(2), nodeCode: 'FINANCE_LEADER', nodeName: '财务负责人', strategyType: 'PERMISSION_HOLDER', strategyCode: 'PERMISSION_HOLDER', approvalMode: 'ANY_ONE', selfPolicy: 'BLOCK', strategyParam: 'oa:reimbursement:finance:approve' }
    ]
  }
  if (businessCode === 'OA_PURCHASE') {
    return [
      { ...blankNode(1), nodeCode: 'DEPT_MANAGER', nodeName: '部门负责人', strategyType: 'ORG_LEADER', strategyCode: 'DEPARTMENT_LEADER' },
      { ...blankNode(2), nodeCode: 'FINANCE_MANAGER', nodeName: '财务负责人', strategyType: 'RESPONSIBILITY_POST', strategyCode: 'FINANCE_MANAGER', strategyParam: '' }
    ]
  }
  return [blankNode(1)]
}

function responseEntity(response, fallbackKey) {
  const value = unwrapData(response)
  if (value && typeof value === 'object') return value
  return value !== undefined && value !== null && value !== '' ? { [fallbackKey]: value } : {}
}

function normalizedRuleDetail(response) {
  const value = unwrapData(response)
  const detail = value && typeof value === 'object' ? value : {}
  return {
    rule: detail.rule && typeof detail.rule === 'object' ? detail.rule : detail,
    versions: Array.isArray(detail.versions) ? detail.versions : []
  }
}

function parseStrategyConfig(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return { ...value }
  if (!String(value || '').trim()) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch (error) {
    return {}
  }
}

function uiNode(value) {
  const node = { ...blankNode(value && value.nodeOrder || 1), ...(value || {}) }
  const config = parseStrategyConfig(node.strategyConfig)
  if (node.strategyType === 'BUSINESS_STRATEGY' && node.strategyCode === 'PERMISSION_HOLDER') {
    node.strategyType = 'PERMISSION_HOLDER'
  }
  if (node.strategyType === 'RESPONSIBILITY_POST') node.strategyParam = config.postCode || ''
  else if (node.strategyType === 'PERMISSION_HOLDER') node.strategyParam = config.permissionKey || ''
  else if (node.strategyType === 'FIXED_USERS') node.strategyParam = Array.isArray(config.userIds) ? config.userIds.join(',') : ''
  else if (node.strategyType === 'ORG_LEADER') node.strategyParam = config.deptType || ''
  else node.strategyParam = Object.keys(config).length ? JSON.stringify(config) : ''
  node.returnAllowed = '1'
  node.rejectAllowed = '1'
  return node
}

function generatedRuleCode(businessCode) {
  const business = String(businessCode || 'APPROVAL').toUpperCase().replace(/[^A-Z0-9_]/g, '_')
  const stamp = Date.now().toString(36).toUpperCase()
  const random = Math.random().toString(36).slice(2, 10).toUpperCase()
  return `RULE_${business}_${stamp}_${random}`.slice(0, 64)
}

export default {
  name: 'ApprovalRuleWizard',
  props: {
    visible: { type: Boolean, default: false },
    template: { type: Object, default: () => ({}) },
    rule: { type: Object, default: () => ({}) },
    version: { type: Object, default: () => ({}) }
  },
  data() {
    const validateScope = (rule, value, callback) => {
      if (this.form.scopeType !== 'ALL' && (!/^\d+$/.test(String(value || '')) || Number(value) <= 0)) callback(new Error('请输入有效的适用范围编号'))
      else callback()
    }
    return {
      activeStep: 0,
      saving: false,
      publishing: false,
      form: this.emptyForm(),
      ruleId: undefined,
      versionId: undefined,
      currentVersionNo: undefined,
      ruleLockVersion: undefined,
      versionLockVersion: undefined,
      ruleStatus: '',
      basicRules: { ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }] },
      scopeRules: {
        scopeType: [{ required: true, message: '请选择组织范围', trigger: 'change' }],
        scopeId: [{ validator: validateScope, trigger: 'blur' }]
      },
      previewForm: { anchorDeptId: '', applicantId: '', businessSubtype: '' },
      previewRules: { anchorDeptId: [{ required: true, message: '请输入锚点组织编号', trigger: 'blur' }] },
      previewLoading: false,
      previewResult: null,
      validationLoading: false,
      validationResult: null
    }
  },
  computed: {
    isTransfer() { return this.template.businessCode === 'INV_TRANSFER' },
    isActiveRule() { return String(this.ruleStatus || '').toUpperCase() === 'ACTIVE' },
    templateLabel() { return this.template.templateName || this.template.businessName || '审批模板' },
    engineLabel() { return String(this.template.engineMode || '').toUpperCase() === 'NATIVE' ? '统一引擎' : '兼容现有引擎' },
    wizardTitle() { return this.ruleId ? '配置审批规则新版本' : '新建审批规则' },
    previewNodes() { return toArray(this.previewResult && (this.previewResult.nodes || this.previewResult.nodeResults)) },
    previewWarnings() { return toArray(this.previewResult && this.previewResult.warnings).map(item => typeof item === 'string' ? item : item.message).filter(Boolean) },
    previewBlocked() { return !!(this.previewResult && (this.previewResult.blocked || this.previewResult.blocking)) || this.previewNodes.some(item => item.blocking) },
    previewBlockedReason() { return this.previewResult && (this.previewResult.blockedReason || this.previewResult.reason) || '存在必选节点未找到候选人，当前配置不能提交。' },
    validationIssues() { return toArray(this.validationResult && (this.validationResult.issues || this.validationResult.issueList)) },
    validationPassed() {
      if (!this.validationResult) return false
      const errorCount = Number(this.validationResult.errorCount || 0)
      const status = String(this.validationResult.runStatus || this.validationResult.status || '').toUpperCase()
      const finishedAndPassed = status === 'PASSED' || status === 'SUCCESS'
      return finishedAndPassed && errorCount === 0 && !this.validationIssues.some(item => String(item.severity || '').toUpperCase() === 'ERROR')
    },
    validationSummary() {
      if (!this.validationResult) return ''
      return `配置检查${this.validationPassed ? '通过' : '未通过'}：${Number(this.validationResult.errorCount || 0)} 个错误，${Number(this.validationResult.warningCount || 0)} 个警告`
    },
    scopeSummary() {
      if (this.form.scopeType === 'ALL') return '全部组织'
      return `${this.form.scopeType === 'AREA' ? '区域' : '门店'}：${this.form.scopeName || this.form.scopeId}`
    }
  },
  watch: {
    visible(value) { if (value) this.initialize() }
  },
  methods: {
    businessSubtypeLabel,
    validationIssueLabel,
    emptyForm() {
      return { ruleCode: '', ruleName: '', scopeType: 'ALL', scopeId: '', scopeName: '', businessSubtype: '', remark: '', nodes: [] }
    },
    initialize() {
      const sourceRule = copy(this.rule || {}) || {}
      const sourceVersion = copy(this.version || sourceRule.draftVersion || sourceRule.currentDraftVersion || {}) || {}
      this.ruleId = entityId(sourceRule, ['ruleId', 'id'])
      this.versionId = entityId(sourceVersion, ['versionId', 'id'])
      this.currentVersionNo = sourceVersion.versionNo
      this.ruleLockVersion = sourceRule.lockVersion
      this.versionLockVersion = sourceVersion.lockVersion
      this.ruleStatus = sourceRule.ruleStatus || sourceRule.status || ''
      const existingNodes = toArray(sourceVersion.nodes || sourceRule.nodes)
      this.form = {
        ruleCode: sourceRule.ruleCode || '',
        ruleName: sourceRule.ruleName || `${this.template.templateName || ''}审批规则`,
        scopeType: sourceRule.scopeType || 'ALL',
        scopeId: sourceRule.scopeId === undefined || sourceRule.scopeId === null ? '' : String(sourceRule.scopeId),
        scopeName: sourceRule.scopeName || '',
        businessSubtype: sourceRule.businessSubtype || '',
        remark: sourceVersion.remark || sourceRule.remark || '',
        nodes: this.isTransfer
          ? copy(TRANSFER_NODES)
          : (existingNodes.length ? existingNodes : defaultNodes(this.template.businessCode)).map(uiNode)
      }
      this.reindexNodes()
      this.previewForm = { anchorDeptId: '', applicantId: '', businessSubtype: this.form.businessSubtype }
      this.previewResult = null
      this.validationResult = null
      this.activeStep = 0
      this.$nextTick(() => ['basicForm', 'scopeForm', 'previewForm'].forEach(ref => this.$refs[ref] && this.$refs[ref].clearValidate()))
    },
    close(done) {
      if (this.saving || this.publishing) return
      this.$emit('update:visible', false)
      if (typeof done === 'function') done()
    },
    addNode() { this.form.nodes.push(blankNode(this.form.nodes.length + 1)); this.validationResult = null },
    strategyTypeChanged(node) {
      node.strategyParam = ''
      if (node.strategyType === 'PERMISSION_HOLDER') {
        node.strategyCode = 'PERMISSION_HOLDER'
        node.strategyParam = APPROVAL_PERMISSION_BY_BUSINESS[this.template.businessCode] || ''
      } else if (node.strategyType === 'FIXED_USERS') {
        node.strategyCode = 'FIXED_USERS'
      } else if (node.strategyType === 'ORG_LEADER') {
        node.strategyCode = 'DEPARTMENT_LEADER'
      } else if (node.strategyType === 'RESPONSIBILITY_POST') {
        node.strategyCode = 'RESPONSIBILITY_POST'
      } else {
        node.strategyCode = ''
      }
      this.validationResult = null
    },
    strategyParamPlaceholder(node) {
      return {
        RESPONSIBILITY_POST: '必填：实际岗位编码，如 cwjl',
        PERMISSION_HOLDER: '必填：审批权限标识',
        FIXED_USERS: '必填：用户编号，多个用逗号分隔',
        ORG_LEADER: '选填：限定组织类型',
        BUSINESS_STRATEGY: '选填：JSON对象'
      }[node.strategyType] || '请输入策略参数'
    },
    removeNode(index) {
      if (this.form.nodes.length <= 1) return this.$modal.msgWarning('审批流程至少保留一个节点')
      this.form.nodes.splice(index, 1)
      this.reindexNodes()
      this.validationResult = null
    },
    moveNode(index, offset) {
      const target = index + offset
      if (target < 0 || target >= this.form.nodes.length) return
      const nodes = this.form.nodes.slice()
      const current = nodes[index]
      nodes.splice(index, 1)
      nodes.splice(target, 0, current)
      this.form.nodes = nodes
      this.reindexNodes()
      this.validationResult = null
    },
    reindexNodes() { this.form.nodes.forEach((node, index) => { node.nodeOrder = index + 1; if (!node.nodeCode) node.nodeCode = `NODE_${index + 1}` }) },
    validateNodes() {
      if (!this.form.nodes.length) { this.$modal.msgError('至少配置一个审批节点'); return false }
      const invalid = this.form.nodes.find(item => !String(item.nodeName || '').trim() || !String(item.strategyCode || '').trim())
      if (invalid) { this.$modal.msgError(`请完整配置第${invalid.nodeOrder}个审批节点`); return false }
      if (this.isTransfer && (this.form.nodes.length !== 4 || this.form.nodes.some((item, index) => item.strategyCode !== TRANSFER_NODES[index].strategyCode))) {
        this.form.nodes = copy(TRANSFER_NODES)
        this.$modal.msgError('调拨固定四节点不可删除、替换或调整顺序')
        return false
      }
      if (!this.isTransfer) {
        for (const node of this.form.nodes) {
          try {
            this.strategyConfig(node)
          } catch (error) {
            this.$modal.msgError(`第${node.nodeOrder}个节点：${error.message}`)
            return false
          }
        }
      }
      return true
    },
    strategyConfig(node) {
      const type = node.strategyType
      const parameter = String(node.strategyParam || '').trim()
      if (type === 'RESPONSIBILITY_POST') {
        if (!parameter) throw new Error('责任岗位必须填写实际岗位编码')
        return JSON.stringify({ postCode: parameter })
      }
      if (type === 'PERMISSION_HOLDER') {
        if (!parameter) throw new Error('权限持有人必须填写审批权限标识')
        const required = APPROVAL_PERMISSION_BY_BUSINESS[this.template.businessCode]
        const reimbursementPermissions = [
          'oa:reimbursement:approve',
          'oa:reimbursement:finance:approve'
        ]
        const allowed = this.template.businessCode === 'OA_REIMBURSEMENT'
          ? reimbursementPermissions.includes(parameter)
          : !required || parameter === required
        if (!allowed) throw new Error(`权限标识必须为 ${required}`)
        return JSON.stringify({ permissionKey: parameter })
      }
      if (type === 'FIXED_USERS') {
        const rawIds = parameter.split(/[,，\s]+/).filter(Boolean)
        if (!rawIds.length || rawIds.some(value => !/^\d+$/.test(value) || Number(value) <= 0)) {
          throw new Error('固定用户必须填写有效的正整数用户编号')
        }
        return JSON.stringify({ userIds: Array.from(new Set(rawIds.map(Number))) })
      }
      if (type === 'ORG_LEADER') return JSON.stringify(parameter ? { deptType: parameter } : {})
      if (!parameter) return JSON.stringify({})
      let parsed
      try {
        parsed = JSON.parse(parameter)
      } catch (error) {
        throw new Error('高级业务策略参数必须是JSON对象')
      }
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('高级业务策略参数必须是JSON对象')
      return JSON.stringify(parsed)
    },
    nodePayload(node) {
      const permissionHolder = node.strategyType === 'PERMISSION_HOLDER'
      return {
        nodeOrder: node.nodeOrder,
        nodeCode: node.nodeCode,
        nodeName: node.nodeName,
        strategyType: permissionHolder ? 'BUSINESS_STRATEGY' : node.strategyType,
        strategyCode: permissionHolder ? 'PERMISSION_HOLDER' : node.strategyCode,
        strategyConfig: this.isTransfer ? node.strategyConfig : this.strategyConfig(node),
        approvalMode: node.approvalMode,
        requiredCount: node.requiredCount,
        missingPolicy: node.missingPolicy,
        selfPolicy: node.selfPolicy,
        returnAllowed: '1',
        rejectAllowed: '1'
      }
    },
    next() {
      if (this.activeStep === 0) return this.$refs.basicForm.validate(valid => { if (valid) this.activeStep += 1 })
      if (this.activeStep === 1) return this.$refs.scopeForm.validate(valid => { if (valid) this.activeStep += 1 })
      if (this.activeStep === 2) {
        if (!this.validateNodes()) return
        return this.persistDraft(false).then(() => { this.activeStep += 1 })
      }
      if (this.activeStep === 3) {
        if (!this.previewResult) return this.$modal.msgWarning('请先预览一个具体组织的审批候选人')
        this.activeStep += 1
        return
      }
      if (this.activeStep === 4 && this.validationPassed) this.activeStep += 1
    },
    rulePayload() {
      if (!this.form.ruleCode) this.form.ruleCode = generatedRuleCode(this.template.businessCode)
      return {
        templateId: entityId(this.template, ['templateId', 'id']),
        ruleCode: this.form.ruleCode,
        ruleName: this.form.ruleName,
        scopeType: this.form.scopeType,
        scopeId: this.form.scopeType === 'ALL' ? null : Number(this.form.scopeId),
        scopeName: this.form.scopeType === 'ALL' ? null : this.form.scopeName,
        businessSubtype: this.form.businessSubtype || null,
        expectedLockVersion: this.ruleLockVersion,
        remark: this.form.remark
      }
    },
    versionPayload() {
      return {
        expectedLockVersion: this.versionLockVersion,
        conditions: toArray(this.version.conditions).map(item => ({
          conditionOrder: item.conditionOrder,
          fieldCode: item.fieldCode,
          operatorCode: item.operatorCode,
          valueType: item.valueType,
          valueText: item.valueText,
          remark: item.remark
        })),
        nodes: this.form.nodes.map(node => this.nodePayload(node)),
        remark: this.form.remark
      }
    },
    ensureIdentifiers() {
      let sequence = Promise.resolve()
      if (!this.ruleId) {
        sequence = sequence.then(() => createApprovalRule(this.rulePayload())).then(response => {
          const detail = normalizedRuleDetail(response)
          const created = detail.rule
          this.ruleId = entityId(created, ['ruleId', 'id'])
          this.ruleLockVersion = created.lockVersion
          this.ruleStatus = created.ruleStatus || created.status || 'DRAFT'
          this.form.ruleCode = created.ruleCode || this.form.ruleCode
          const draft = detail.versions.find(item => String(item.versionStatus || item.status).toUpperCase() === 'DRAFT')
          if (draft) {
            this.versionId = entityId(draft, ['versionId', 'id'])
            this.currentVersionNo = draft.versionNo
            this.versionLockVersion = draft.lockVersion
          }
          if (!this.ruleId) throw new Error('创建规则成功但未返回 ruleId')
        })
      }
      return sequence.then(() => {
        if (this.versionId) return undefined
        return createApprovalRuleDraft(this.ruleId, { sourceVersionId: entityId(this.version, ['versionId', 'id']) }).then(response => {
          const draft = responseEntity(response, 'versionId')
          this.versionId = entityId(draft, ['versionId', 'id'])
          this.currentVersionNo = draft.versionNo
          this.versionLockVersion = draft.lockVersion
          if (!this.versionId) throw new Error('创建草稿成功但未返回 versionId')
        })
      })
    },
    persistDraft(showMessage = true) {
      if (!this.validateNodes()) return Promise.reject(new Error('节点配置不完整'))
      this.saving = true
      return this.ensureIdentifiers().then(() => {
        if (this.isActiveRule) return undefined
        return updateApprovalRule(this.ruleId, this.rulePayload()).then(response => {
          const savedRule = normalizedRuleDetail(response).rule
          if (savedRule.lockVersion !== undefined) this.ruleLockVersion = savedRule.lockVersion
        })
      }).then(() => updateApprovalVersion(this.versionId, this.versionPayload()))
        .then(response => {
          const saved = responseEntity(response, 'versionId')
          this.currentVersionNo = saved.versionNo || this.currentVersionNo
          if (saved.lockVersion !== undefined) this.versionLockVersion = saved.lockVersion
          if (showMessage) this.$modal.msgSuccess('草稿已保存')
          this.$emit('saved', { ruleId: this.ruleId, versionId: this.versionId })
          return saved
        }).finally(() => { this.saving = false })
    },
    saveDraft() { return this.persistDraft(true).catch(() => {}) },
    loadPreview() {
      this.$refs.previewForm.validate(valid => {
        if (!valid) return
        this.previewLoading = true
        this.persistDraft(false).then(() => previewApprovalCandidates(this.versionId, {
          anchorDeptId: this.previewForm.anchorDeptId,
          applicantId: this.previewForm.applicantId || undefined,
          businessSubtype: this.previewForm.businessSubtype || this.form.businessSubtype || undefined,
          variables: {}
        })).then(response => { this.previewResult = unwrapData(response) }).finally(() => { this.previewLoading = false })
      })
    },
    previewCandidateNames(node) {
      const candidates = toArray(node.candidates || node.candidateList)
      if (candidates.length) return candidates.map(item => item.userName || item.candidateName || item.userId).filter(Boolean).join('、') || '未找到'
      return toArray(node.candidateDisplayNames).join('、') || '未找到'
    },
    runValidation() {
      this.validationLoading = true
      return this.persistDraft(false).then(() => runApprovalValidation({
        businessCode: this.template.businessCode,
        templateId: entityId(this.template, ['templateId', 'id']),
        ruleId: this.ruleId,
        versionId: this.versionId,
        scopeMode: 'VERSION',
        anchorDeptId: this.previewForm.anchorDeptId || undefined,
        businessSubtype: this.form.businessSubtype || undefined
      })).then(response => {
        const value = unwrapData(response)
        this.validationResult = value && value.run
          ? { ...value.run, issues: value.issues || value.issueList || [] }
          : value
      }).finally(() => { this.validationLoading = false })
    },
    issueLabel(value) { return { ERROR: '错误', WARNING: '警告', INFO: '提示' }[String(value || '').toUpperCase()] || (value ? '未知级别' : '-') },
    issueType(value) { return { ERROR: 'danger', WARNING: 'warning', INFO: 'info' }[String(value || '').toUpperCase()] || 'info' },
    publish() {
      if (!this.validationPassed || !this.versionId) return
      this.$confirm('发布后当前版本不可修改，运行实例会永久使用此版本及候选人快照。确认发布？', '发布审批版本', {
        confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning'
      }).then(() => {
        this.publishing = true
        return publishApprovalVersion(this.versionId, {
          expectedLockVersion: this.versionLockVersion,
          remark: this.form.remark
        }).then(() => {
          this.$modal.msgSuccess('审批规则新版本已发布')
          this.$emit('published', { ruleId: this.ruleId, versionId: this.versionId })
          this.$emit('update:visible', false)
        }).finally(() => { this.publishing = false })
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.wizard-steps { margin: 0 10px 24px; }
.wizard-content { min-height: 430px; max-height: 63vh; overflow-y: auto; padding: 4px 12px; }
.wizard-section { max-width: 1200px; margin: 0 auto; }
.step-form { max-width: 760px; margin: 24px auto 0; }
.active-rule-alert { margin-top: 12px; }
.inline-help, .field-help { margin-left: 10px; color: #8492a6; font-size: 12px; }
.field-help { margin: 5px 0 0; }
.node-alert, .preview-alert { margin-bottom: 14px; }
.node-toolbar, .check-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 14px; color: #606266; }
.check-toolbar h3 { margin: 0 0 5px; }
.check-toolbar p { margin: 0; color: #8492a6; }
.danger-text { color: #f56c6c; }
.transfer-rule-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
.transfer-rule-grid div { display: flex; flex-direction: column; gap: 5px; padding: 12px; border: 1px solid #d9ecff; border-radius: 8px; background: #f5faff; }
.transfer-rule-grid span { color: #606266; font-size: 12px; line-height: 1.6; }
.preview-form { padding: 14px 14px 0; border-radius: 8px; background: #f7f9fc; }
.publish-section { max-width: 820px; }
.publish-summary { padding: 30px 20px; text-align: center; }
.publish-summary i { font-size: 48px; }
.publish-summary h3 { margin: 12px 0 8px; }
.publish-summary p { color: #606266; }
.success-icon { color: #67c23a; }
.error-icon { color: #f56c6c; }
.wizard-footer { display: flex; justify-content: space-between; }
</style>
