<template>
  <el-dialog
    title="批量选择公司并盖章"
    :visible="visible"
    width="94%"
    custom-class="sign-task-batch-finalize-dialog"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!operationLocked"
    :show-close="!operationLocked"
    :before-close="handleBeforeClose"
    @closed="handleClosed"
  >
    <div v-loading="previewLoading" class="batch-finalize-workbench">
      <el-alert
        title="只处理员工已在同一签约包完成唯一一次签名、当前处于“待选公司与印章”的任务。本步骤只生成最终合同，不会自动发送；预览无误后还需 HR 另行发送最终文件。"
        type="info"
        show-icon
        :closable="false"
        class="workbench-alert"
      />

      <el-alert
        v-if="previewError"
        :title="previewError"
        type="error"
        show-icon
        :closable="false"
        class="workbench-alert"
      >
        <el-button type="text" size="mini" @click="loadPreview">重新预检</el-button>
      </el-alert>

      <template v-if="rows.length">
        <div class="preview-summary">
          <span>共 <strong>{{ rows.length }}</strong> 项</span>
          <el-tag size="mini" type="success">可提交 {{ readyCount }}</el-tag>
          <el-tag v-if="blockedCount" size="mini" type="danger">阻断 {{ blockedCount }}</el-tag>
          <el-tag v-if="mismatchCount" size="mini" type="warning">需说明改选 {{ mismatchCount }}</el-tag>
          <el-button type="text" size="mini" :disabled="operationLocked" @click="loadPreview">刷新预检</el-button>
        </div>

        <div v-if="pendingRows.length" class="batch-apply-bar">
          <span class="batch-apply-label">批量应用</span>
          <el-select
            v-model="bulkLegalEntityId"
            filterable
            clearable
            size="small"
            placeholder="先选择合同公司"
            class="batch-company-select"
            :disabled="operationLocked"
            @change="handleBulkCompanyChoice"
          >
            <el-option
              v-for="company in legalEntities"
              :key="companyId(company)"
              :label="companyOptionLabel(company)"
              :value="companyId(company)"
              :disabled="company.selectable === false"
            />
          </el-select>
          <el-button size="small" :disabled="operationLocked || !bulkLegalEntityId" @click="applyCompanyToAll">
            应用公司到全部
          </el-button>
          <el-select
            v-model="bulkSealId"
            filterable
            clearable
            size="small"
            placeholder="选择同公司印章"
            class="batch-seal-select"
            :disabled="operationLocked || !bulkLegalEntityId || !bulkSealOptions.length"
          >
            <el-option
              v-for="seal in bulkSealOptions"
              :key="sealId(seal)"
              :label="sealName(seal)"
              :value="sealId(seal)"
            />
          </el-select>
          <el-button size="small" :disabled="operationLocked || !bulkSealId" @click="applySealToSameCompany">
            应用印章到同公司
          </el-button>
        </div>

        <el-table
          :data="rows"
          row-key="taskId"
          border
          size="small"
          class="finalize-table"
          max-height="500"
        >
          <el-table-column label="员工 / 任务" width="150" fixed="left">
            <template slot-scope="scope">
              <div class="employee-summary">
                <strong>{{ scope.row.employeeName || '未命名员工' }}</strong>
                <small>任务 {{ scope.row.taskId }}</small>
                <small>签约包 {{ scope.row.packageId }}</small>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="匹配建议" min-width="190">
            <template slot-scope="scope">
              <div class="recommendation-cell">
                <small>Excel 建议</small>
                <strong>{{ recommendationText(scope.row) }}</strong>
                <span v-if="automaticCandidateText(scope.row)">
                  部门自动匹配：{{ automaticCandidateText(scope.row) }}
                </span>
                <span v-else>部门未唯一匹配公司</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="合同公司" min-width="230">
            <template slot-scope="scope">
              <el-select
                v-model="scope.row.legalEntityId"
                filterable
                placeholder="请选择合同公司"
                size="small"
                style="width: 100%"
                :disabled="operationLocked || rowSucceeded(scope.row)"
                @change="handleCompanyChange(scope.row)"
              >
                <el-option
                  v-for="company in companyOptions(scope.row)"
                  :key="companyId(company)"
                  :label="companyOptionLabel(company)"
                  :value="companyId(company)"
                  :disabled="company.selectable === false"
                />
              </el-select>
              <div v-if="selectedCompany(scope.row)" class="company-meta">
                <span>信用代码：{{ selectedCompany(scope.row).unifiedSocialCreditCode || '未维护' }}</span>
                <span>法定代表人：{{ selectedCompany(scope.row).legalRepresentative || '未维护' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="合同印章" min-width="190">
            <template slot-scope="scope">
              <el-alert
                v-if="!requiresSeal(scope.row)"
                title="该套餐无需盖章"
                type="info"
                :closable="false"
                class="inline-alert"
              />
              <template v-else>
                <el-select
                  v-model="scope.row.sealId"
                  filterable
                  clearable
                  placeholder="请选择有效合同印章"
                  size="small"
                  style="width: 100%"
                  :disabled="!scope.row.legalEntityId || operationLocked || rowSucceeded(scope.row)"
                  @change="markEdited"
                >
                  <el-option
                    v-for="seal in availableSeals(scope.row)"
                    :key="sealId(seal)"
                    :label="sealName(seal)"
                    :value="sealId(seal)"
                  />
                </el-select>
                <p v-if="scope.row.legalEntityId && !availableSeals(scope.row).length" class="field-error">
                  该公司没有本任务可用的有效合同印章
                </p>
              </template>
            </template>
          </el-table-column>

          <el-table-column label="改选原因" min-width="220">
            <template slot-scope="scope">
              <el-input
                v-if="recommendationMismatch(scope.row)"
                v-model.trim="scope.row.correctionReason"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="所选公司与建议不一致，请填写原因"
                :disabled="operationLocked || rowSucceeded(scope.row)"
                @input="markEdited"
              />
              <span v-else class="matched-copy">与建议一致，无需填写</span>
            </template>
          </el-table-column>

          <el-table-column label="预检 / 校验" min-width="240">
            <template slot-scope="scope">
              <div v-if="rowBlockers(scope.row).length" class="blocker-list">
                <el-tag
                  v-for="(message, index) in rowBlockers(scope.row)"
                  :key="`${scope.row.taskId}:blocker:${index}`"
                  size="mini"
                  type="danger"
                  effect="plain"
                >{{ message }}</el-tag>
              </div>
              <el-tag v-else size="mini" type="success" effect="plain">预检通过</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="执行结果" min-width="180" fixed="right">
            <template slot-scope="scope">
              <template v-if="scope.row.result">
                <el-tag :type="resultTagType(scope.row.result)" size="mini">
                  {{ resultLabel(scope.row.result) }}
                </el-tag>
                <p class="result-message">{{ scope.row.resultMessage || '-' }}</p>
              </template>
              <span v-else class="pending-result">待执行</span>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="hasExecutionResults"
          :title="executionSummaryText"
          :type="executionFailureCount ? 'warning' : 'success'"
          show-icon
          :closable="false"
          class="execution-summary"
        />

        <el-checkbox
          v-if="pendingRows.length"
          v-model="confirmed"
          :disabled="operationLocked || blockedCount > 0"
          class="finalize-confirmation"
        >
          我已逐项核对合同公司、有效印章与改选原因，确认生成最终合同；本步骤不发送，预览后另行发送最终文件
        </el-checkbox>
      </template>

      <el-alert v-if="commandUnknown" title="批量生成结果待核对，已冻结原公司、印章与请求号；不会自动重新生成。" type="warning" :closable="false">
        <p>{{ recoveryMessage }}</p>
        <el-button type="text" :disabled="submitting || confirming || checking" @click="checkUnknownResult">核对原任务</el-button>
        <el-button type="text" :disabled="submitting || confirming || checking || !checkedUnknown" @click="retryOriginalCommand">重试原未确认项</el-button>
      </el-alert>
      <el-empty v-else-if="!previewLoading && !previewError" description="没有可批量处理的任务" />
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button :disabled="operationLocked" @click="closeDialog">{{ hasExecutionResults ? '关闭' : '取消' }}</el-button>
      <el-button
        v-if="pendingRows.length"
        type="primary"
        icon="el-icon-s-claim"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="confirmAndSubmit"
      >批量生成正式合同（{{ pendingRows.length }}）</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { getSignTask, finalizeSignTaskBatch, previewSignTaskBatchFinalize } from '@/api/oa/signTask'
import { getSignPackage } from '@/api/oa/signPackage'
import { getSelectedSignScopeDeptId } from '@/utils/signScopeContext'
const { createUiOperationScope } = require('@/utils/uiOperationScope')
const { signBusinessText } = require('@/utils/signDisplayText')

const SUCCESS_RESULTS = ['SUCCESS', 'FINALIZED', 'ALREADY_FINALIZED', 'COMPLETED', 'PENDING_FINAL_CONFIRM']

export default {
  name: 'SignTaskBatchFinalizeDialog',
  props: {
    visible: { type: Boolean, default: false },
    taskIds: { type: Array, default: () => [] }
  },
  data() {
    return {
      previewLoading: false,
      confirming: false,
      checking: false,
      commandUnknown: false,
      checkedUnknown: false,
      recoveryMessage: '',
      pendingCommand: null,
      submitting: false,
      previewError: '',
      legalEntities: [],
      rows: [],
      bulkLegalEntityId: '',
      bulkSealId: '',
      confirmed: false,
      requestId: '',
      responseSummary: null
    }
  },
  computed: {
    operationLocked() { return this.submitting || this.confirming || this.commandUnknown || this.checking },
    pendingRows() { return this.rows.filter(row => !this.rowSucceeded(row)) },
    readyCount() {
      return this.pendingRows.filter(row => this.rowBlockers(row).length === 0).length
    },
    blockedCount() {
      return this.pendingRows.length - this.readyCount
    },
    mismatchCount() {
      return this.rows.filter(row => this.recommendationMismatch(row)).length
    },
    bulkSealOptions() {
      if (!this.bulkLegalEntityId) return []
      const result = []
      const seen = new Set()
      this.rows.forEach(row => {
        const shadow = Object.assign({}, row, { legalEntityId: this.bulkLegalEntityId })
        this.availableSeals(shadow).forEach(seal => {
          const id = this.sealId(seal)
          if (id && !seen.has(id)) {
            seen.add(id)
            result.push(seal)
          }
        })
      })
      return result
    },
    canSubmit() {
      return this.pendingRows.length > 0 && !this.previewLoading && !this.operationLocked &&
        this.blockedCount === 0 && this.confirmed
    },
    hasExecutionResults() {
      return this.rows.some(row => !!row.result)
    },
    executionSuccessCount() {
      return this.rows.filter(row => this.rowSucceeded(row)).length
    },
    executionFailureCount() {
      return this.rows.filter(row => row.result && !this.rowSucceeded(row)).length
    },
    executionSummaryText() {
      const summary = this.responseSummary || {}
      const reportedSuccess = (Number(summary.successCount) || 0) + (Number(summary.finalizedCount) || 0) +
        (Number(summary.alreadyFinalizedCount) || 0)
      const reportedFailure = (Number(summary.failureCount) || 0) + (Number(summary.failedCount) || 0) +
        (Number(summary.blockedCount) || 0)
      const success = Math.max(reportedSuccess, this.executionSuccessCount)
      const failed = Math.max(reportedFailure, this.executionFailureCount)
      return `批量处理完成：成功 ${success} 项，未成功 ${failed} 项。失败项未被静默跳过，可在列表刷新后重新处理。`
    }
  },
  watch: {
    visible(value) { this.resetState(); if (value) this.schedulePreview() },
    taskIds: { deep: true, handler() { if (this.visible) { this.resetState(); this.schedulePreview() } } },
    '$store.state.user.sessionRevision'() { this.resetState(); if (this.visible) this.schedulePreview() }
  },
  created() { window.addEventListener('erp:sign-scope-changed', this.handleScopeChange) },
  beforeDestroy() { window.removeEventListener('erp:sign-scope-changed', this.handleScopeChange); this.operationScope().deactivate() },
  deactivated() { this.operationScope().deactivate(); this.resetState() },
  activated() { this.operationScope().activate(); if (this.visible) this.loadPreview() },
  methods: {
    schedulePreview() {
      const revision = (this._previewSchedule || 0) + 1
      this._previewSchedule = revision
      this.$nextTick(() => { if (this.visible && revision === this._previewSchedule) this.loadPreview() })
    },
    operationIdentity() {
      return JSON.stringify({ actor: this.$store && this.$store.getters.id,
        session: this.$store && this.$store.state.user.sessionRevision,
        dept: getSelectedSignScopeDeptId(), tasks: this.normalizedTaskIds(), visible: this.visible })
    },
    handleScopeChange() { this.resetState(); if (this.visible) this.schedulePreview() },
    operationScope() {
      if (!this._batchScope) this._batchScope = createUiOperationScope(() => this.operationIdentity())
      return this._batchScope
    },
    loadPreview() {
      if (this.operationLocked || !this.visible) return
      const taskIds = this.normalizedTaskIds()
      this.operationScope().invalidate()
      const token = this.operationScope().begin('preview')
      const current = () => this.visible && this.operationScope().isCurrent(token)
      this.rows = []; this.legalEntities = []; this.previewError = ''
      if (!taskIds.length || taskIds.length > 20) {
        this.previewError = !taskIds.length ? '没有选中的待选公司盖章任务' : '单次最多处理20个签约任务'
        this.previewLoading = false
        return
      }
      this.previewLoading = true
      this.responseSummary = null
      this.requestId = ''
      this.confirmed = false
      return previewSignTaskBatchFinalize({ taskIds }).then(response => {
        if (!current()) return
        const data = response.data || {}
        const items = Array.isArray(data.items) ? data.items : []
        const ids = items.map(item => this.normalizeId(item.taskId))
        if (ids.length !== taskIds.length || new Set(ids).size !== ids.length || ids.some(id => !taskIds.includes(id))) {
          throw Error('预检返回任务与当前批次不一致，请重新预检')
        }
        this.legalEntities = this.mergeCompanyCandidates(items)
        this.rows = items.map(item => this.createRow(item))
      }).catch(error => {
        if (!current()) return
        this.rows = []; this.legalEntities = []
        this.previewError = signBusinessText(error && error.message, '批量预检失败，请刷新任务列表后重试')
      }).finally(() => { if (current()) this.previewLoading = false })
    },
    createRow(item) {
      const recommendedId = this.normalizeId(item && item.recommendedLegalEntityId)
      const candidates = Array.isArray(item && item.companyCandidates) ? item.companyCandidates.slice() : []
      const departmentCandidate = candidates.find(candidate => candidate && candidate.departmentCandidate === true) || null
      const row = Object.assign({}, item, {
        taskId: this.normalizeId(item && item.taskId),
        packageId: this.normalizeId(item && item.packageId),
        recommendedCompany: item && (item.recommendedCompany || item.excelRecommendedCompany),
        automaticCandidate: item && item.automaticCandidate || departmentCandidate,
        companyCandidates: candidates,
        previewLegalEntityId: recommendedId,
        legalEntityId: recommendedId,
        sealId: '',
        correctionReason: '',
        blockerMessages: this.normalizeBlockers(item && (item.blockingReasons || item.blockers)),
        result: '',
        resultMessage: ''
      })
      row.sealId = this.resolveRecommendedSeal(row)
      return row
    },
    mergeCompanyCandidates(items) {
      const result = []
      const byId = new Map()
      ;(items || []).forEach(item => {
        const candidates = Array.isArray(item && item.companyCandidates) ? item.companyCandidates : []
        candidates.forEach(candidate => {
          const id = this.companyId(candidate)
          if (!id) return
          const existing = byId.get(id)
          if (!existing) {
            const copy = Object.assign({}, candidate)
            byId.set(id, copy)
            result.push(copy)
          } else if (existing.selectable !== true && candidate.selectable === true) {
            Object.assign(existing, candidate)
          }
        })
      })
      return result
    },
    normalizedTaskIds() {
      const seen = new Set()
      return (this.taskIds || []).map(this.normalizeId).filter(taskId => {
        if (!/^[1-9]\d{0,18}$/.test(taskId) || seen.has(taskId)) return false
        seen.add(taskId)
        return true
      })
    },
    normalizeId(value) {
      if (value === null || value === undefined || value === '') return ''
      return String(value)
    },
    normalizeBlockers(blockers) {
      if (!Array.isArray(blockers)) return []
      return blockers.map(blocker => {
        if (typeof blocker === 'string') return blocker.trim()
        if (!blocker) return ''
        return String(blocker.message || blocker.detail || blocker.label || blocker.code || '').trim()
      }).filter(Boolean)
    },
    companyId(company) {
      return this.normalizeId(company && (company.legalEntityId || company.companyId || company.id))
    },
    companyName(company) {
      return String(company && (company.legalEntityName || company.companyName || company.name) || '未命名公司')
    },
    companyOptionLabel(company) {
      const flags = []
      if (company && company.excelCandidate === true) flags.push('Excel匹配')
      if (company && company.departmentCandidate === true) flags.push('部门绑定')
      if (company && company.selectable === false) flags.push('不可用')
      return this.companyName(company) + (flags.length ? `（${flags.join('、')}）` : '')
    },
    candidateCompanyId(candidate) {
      if (!candidate || typeof candidate !== 'object') return ''
      return this.companyId(candidate)
    },
    selectedCompany(row) {
      const selectedId = this.normalizeId(row && row.legalEntityId)
      return this.companyOptions(row).find(company => this.companyId(company) === selectedId) || null
    },
    companyOptions(row) {
      const candidates = Array.isArray(row && row.companyCandidates) ? row.companyCandidates : []
      return candidates.length ? candidates : this.legalEntities
    },
    recommendationText(row) {
      const recommendation = row && row.recommendedCompany
      if (typeof recommendation === 'string') return recommendation.trim() || '未提供'
      return recommendation ? this.companyName(recommendation) : '未提供'
    },
    automaticCandidateText(row) {
      const candidate = row && row.automaticCandidate
      return candidate ? this.companyName(candidate) : ''
    },
    recommendationMismatch(row) {
      if (!row || !row.legalEntityId) return false
      const recommendation = row.recommendedCompany
      const recommendationId = typeof recommendation === 'object' ? this.companyId(recommendation) : ''
      if (recommendationId) return recommendationId !== this.normalizeId(row.legalEntityId)
      const recommendedName = this.recommendationText(row)
      if (recommendedName && recommendedName !== '未提供') {
        const selected = this.selectedCompany(row)
        return !selected || this.normalizeName(this.companyName(selected)) !== this.normalizeName(recommendedName)
      }
      const automaticId = this.candidateCompanyId(row.automaticCandidate)
      return !!automaticId && automaticId !== this.normalizeId(row.legalEntityId)
    },
    normalizeName(value) {
      return String(value || '').replace(/\s+/g, '').toLowerCase()
    },
    requiresSeal(row) {
      return !!row && row.companySealRequired !== false && row.companySealRequired !== 'N'
    },
    sealId(seal) {
      return this.normalizeId(seal && (seal.sealId || seal.id))
    },
    sealName(seal) {
      const suffix = seal && (seal.isDefault === 'Y' || seal.default === true || seal.defaultSeal === true) ? '（默认）' : ''
      return String(seal && (seal.sealName || seal.name) || '未命名印章') + suffix
    },
    sealCompanyId(seal) {
      return this.normalizeId(seal && (seal.legalEntityId || seal.companyId))
    },
    availableSeals(row) {
      if (!row || !row.legalEntityId) return []
      const selectedId = this.normalizeId(row.legalEntityId)
      const rowSeals = Array.isArray(row.seals) ? row.seals : []
      const selectedCompany = this.selectedCompany(row)
      const companySeals = selectedCompany && Array.isArray(selectedCompany.availableContractSeals)
        ? selectedCompany.availableContractSeals
        : (selectedCompany && Array.isArray(selectedCompany.seals) ? selectedCompany.seals : [])
      const combined = rowSeals.concat(companySeals)
      const seen = new Set()
      return combined.filter(seal => {
        const id = this.sealId(seal)
        if (!id || seen.has(id)) return false
        const ownerId = this.sealCompanyId(seal)
        const nestedForSelectedCompany = companySeals.includes(seal)
        const allowed = ownerId ? ownerId === selectedId
          : (nestedForSelectedCompany || selectedId === this.normalizeId(row.previewLegalEntityId))
        if (allowed) seen.add(id)
        return allowed
      })
    },
    resolveRecommendedSeal(row) {
      if (!this.requiresSeal(row)) return ''
      const options = this.availableSeals(row)
      const company = this.selectedCompany(row)
      const recommended = this.normalizeId(company && company.recommendedSealId || row.recommendedSealId)
      if (recommended && options.some(seal => this.sealId(seal) === recommended)) return recommended
      const defaultSeal = options.find(seal => seal.isDefault === 'Y' || seal.default === true || seal.defaultSeal === true)
      if (defaultSeal) return this.sealId(defaultSeal)
      return options.length === 1 ? this.sealId(options[0]) : ''
    },
    handleCompanyChange(row) {
      if (this.operationLocked) return
      row.sealId = this.resolveRecommendedSeal(row)
      row.correctionReason = this.recommendationMismatch(row) ? row.correctionReason : ''
      this.markEdited()
    },
    handleBulkCompanyChoice() {
      if (this.operationLocked) return
      this.bulkSealId = ''
    },
    applyCompanyToAll() {
      if (this.operationLocked || !this.bulkLegalEntityId) return
      let applied = 0
      this.rows.forEach(row => {
        if (this.rowSucceeded(row)) return
        const candidate = this.companyOptions(row).find(company =>
          this.companyId(company) === this.normalizeId(this.bulkLegalEntityId) && company.selectable !== false)
        if (!candidate) return
        row.legalEntityId = this.bulkLegalEntityId
        row.sealId = this.resolveRecommendedSeal(row)
        if (!this.recommendationMismatch(row)) row.correctionReason = ''
        applied += 1
      })
      this.markEdited()
      if (!applied) this.$message.warning('所选公司不适用于当前任务')
      else if (applied < this.rows.length) this.$message.warning(`已应用到 ${applied} 项，其余任务无该公司可用候选`)
    },
    applySealToSameCompany() {
      if (this.operationLocked || !this.bulkLegalEntityId || !this.bulkSealId) return
      let applied = 0
      this.rows.forEach(row => {
        if (this.rowSucceeded(row) || this.normalizeId(row.legalEntityId) !== this.normalizeId(this.bulkLegalEntityId)) return
        if (this.availableSeals(row).some(seal => this.sealId(seal) === this.normalizeId(this.bulkSealId))) {
          row.sealId = this.normalizeId(this.bulkSealId)
          applied += 1
        }
      })
      this.markEdited()
      if (!applied) this.$message.warning('没有任务可使用该印章')
      else this.$message.success(`已将印章应用到 ${applied} 个同公司任务`)
    },
    rowBlockers(row) {
      const messages = (row && row.blockerMessages ? row.blockerMessages : []).slice()
      if (!row || !row.legalEntityId) messages.push('请选择合同公司')
      const company = this.selectedCompany(row)
      if (row && row.legalEntityId && !company) messages.push('所选公司不在本任务的可用范围内')
      if (company && company.selectable === false) {
        const missing = Array.isArray(company.missingMasterFields) ? company.missingMasterFields : []
        messages.push(missing.length ? `公司主数据待补：${missing.join('、')}` : '所选公司当前不可用')
      }
      if (row && this.requiresSeal(row)) {
        if (row.legalEntityId && !this.availableSeals(row).length) messages.push('所选公司没有本任务可用的有效合同印章')
        else if (!row.sealId) messages.push('请选择合同印章')
      }
      if (row && this.recommendationMismatch(row) && !String(row.correctionReason || '').trim()) {
        messages.push('所选公司与建议不一致，请填写改选原因')
      }
      return Array.from(new Set(messages))
    },
    markEdited() {
      if (this.operationLocked) return
      this.operationScope().invalidate('confirm')
      this.pendingRows.forEach(row => { row.result = ''; row.resultMessage = '' })
      this.requestId = ''; this.pendingCommand = null; this.confirmed = false
    },
    commandItems() {
      return this.pendingRows.map(row => ({ taskId: row.taskId, packageId: row.packageId,
        expectedTaskVersion: row.taskVersion, expectedPackageVersion: row.packageVersion,
        legalEntityId: this.normalizeId(row.legalEntityId),
        sealId: this.requiresSeal(row) ? this.normalizeId(row.sealId) : null,
        correctionReason: String(row.correctionReason || '').trim() || null }))
    },
    confirmAndSubmit() {
      if (!this.canSubmit) return
      const scope = this.operationScope(), items = this.commandItems()
      const token = scope.begin('confirm', items)
      this.confirming = true
      return this.$confirm(
        `即将为 ${items.length} 位员工固定合同公司并生成最终合同。本步骤不会发送，生成后还需预览并另行发送最终文件。是否继续？`,
        '二次确认', { confirmButtonText: '确认生成', cancelButtonText: '返回核对', type: 'warning' }
      ).then(() => {
        if (!scope.isCurrent(token, this.commandItems())) return
        this.confirming = false
        const command = Object.freeze({ requestId: this.createRequestId(), items: Object.freeze(items.map(item => Object.freeze(item))) })
        this.pendingCommand = command; this.requestId = command.requestId
        return this.submitBatch(command)
      }).catch(reason => {
        if (scope.isCurrent(token) && reason !== 'cancel' && reason !== 'close') this.$message.error(signBusinessText(reason && reason.message, '无法完成二次确认'))
      }).finally(() => { if (scope.isCurrent(token)) this.confirming = false })
    },
    submitBatch(command) {
      if (this.submitting || !command || command !== this.pendingCommand || !this.visible) return
      const scope = this.operationScope(), token = scope.begin('submit')
      const current = () => this.visible && scope.isCurrent(token) && command === this.pendingCommand
      this.submitting = true; this.checkedUnknown = false
      return finalizeSignTaskBatch(command).then(response => {
        if (!current()) return
        const data = response.data || {}
        const results = Array.isArray(data.items) ? data.items : (Array.isArray(data.results) ? data.results : [])
        const ids = results.map(row => this.normalizeId(row.taskId))
        const complete = ids.length === command.items.length && new Set(ids).size === ids.length && command.items.every(row => ids.includes(row.taskId))
        this.applyExecutionResults(results, command)
        this.responseSummary = null
        this.commandUnknown = !complete || this.rows.some(row => row.result === 'UNKNOWN')
        if (this.commandUnknown) this.recoveryMessage = '逐项结果不完整，请核对原任务；不会将缺失结果当成失败后新建请求。'
        else { this.pendingCommand = null; this.requestId = ''; this.confirmed = false }
        const successCount = this.rows.filter(row => this.rowSucceeded(row)).length
        if (successCount) this.$emit('completed', { successCount, response: data })
      }).catch(error => {
        if (!current()) return
        // A transport/5xx failure does not prove that no per-item transaction committed.
        const status = error && error.response && error.response.status
        this.commandUnknown = !status || status >= 500 || Number(error && error.code) >= 500
        this.recoveryMessage = this.commandUnknown ? '请求结果暂时无法确认，请先核对原任务，再显式重试原请求。' : signBusinessText(error && error.message, '批量生成被拒绝，请检查权限或状态')
        if (!error || !error.notified) this.$message.error(this.recoveryMessage)
        if (!this.commandUnknown) { this.pendingCommand = null; this.requestId = ''; this.confirmed = false }
      }).finally(() => { if (current() || (scope.isCurrent(token) && !this.pendingCommand)) this.submitting = false })
    },
    applyExecutionResults(results, command) {
      const byTaskId = new Map(results.map(item => [this.normalizeId(item && item.taskId), item || {}]))
      command.items.forEach(snapshot => {
        const row = this.rows.find(row => row.taskId === snapshot.taskId)
        if (!row || JSON.stringify(this.itemSnapshot(row)) !== JSON.stringify(snapshot)) return
        const result = byTaskId.get(snapshot.taskId)
        row.result = result ? String(result.result || result.status || (result.success === true ? 'SUCCESS' : 'FAILED')).toUpperCase() : 'UNKNOWN'
        row.resultMessage = result ? String(result.message || result.failureReason || result.detail || '') : '结果缺失，待核对'
      })
    },
    itemSnapshot(row) {
      return { taskId: row.taskId, packageId: row.packageId, expectedTaskVersion: row.taskVersion,
        expectedPackageVersion: row.packageVersion, legalEntityId: this.normalizeId(row.legalEntityId),
        sealId: this.requiresSeal(row) ? this.normalizeId(row.sealId) : null,
        correctionReason: String(row.correctionReason || '').trim() || null }
    },
    checkUnknownResult() {
      const command = this.pendingCommand
      if (!this.commandUnknown || !command || this.checking || this.submitting) return
      const scope = this.operationScope(), token = scope.begin('check')
      const current = () => this.visible && scope.isCurrent(token) && command === this.pendingCommand
      this.checking = true; this.checkedUnknown = false
      return Promise.all(command.items.map(item => Promise.all([getSignTask(item.taskId), getSignPackage(item.packageId)]).then(([task, pack]) => {
        const actualTask = task.data && task.data.task || {}, actualPackage = pack.data || {}
        if (String(actualTask.taskId) !== item.taskId || String(actualPackage.packageId) !== item.packageId) throw Error('核对返回的任务或签约包不匹配')
        return `任务 ${item.taskId}：${signBusinessText(actualTask.status, '状态待确认')}，签约包 ${signBusinessText(actualPackage.status, '状态待确认')}`
      }))).then(messages => {
        if (!current()) return
        this.recoveryMessage = messages.join('；') + '。当前状态不能单独证明本次请求成功；重试只使用原请求号及原载荷。'
        this.checkedUnknown = true
      }).catch(error => { if (current()) this.recoveryMessage = signBusinessText(error && error.message, '结果核对失败，请稍后重试核对') })
        .finally(() => { if (current()) this.checking = false })
    },
    retryOriginalCommand() {
      if (!this.commandUnknown || !this.checkedUnknown || this.submitting || this.checking || !this.pendingCommand) return
      const old = this.pendingCommand
      const items = old.items.filter(item => !this.rows.some(row => row.taskId === item.taskId && this.rowSucceeded(row)))
      const command = Object.freeze({ requestId: old.requestId, items: Object.freeze(items) })
      this.pendingCommand = command
      return this.submitBatch(command)
    },
    rowSucceeded(row) {
      return !!row && SUCCESS_RESULTS.includes(String(row.result || '').toUpperCase())
    },
    resultTagType(result) {
      const normalized = String(result || '').toUpperCase()
      if (SUCCESS_RESULTS.includes(normalized)) return 'success'
      if (normalized === 'CONFLICT' || normalized === 'SKIPPED') return 'warning'
      return 'danger'
    },
    resultLabel(result) {
      return {
        SUCCESS: '已生成',
        FINALIZED: '已生成',
        ALREADY_FINALIZED: '已处理',
        COMPLETED: '已生成',
        PENDING_FINAL_CONFIRM: '待员工确认',
        CONFLICT: '状态已变更',
        SKIPPED: '未处理',
        FAILED: '处理失败',
        UNKNOWN: '结果待核对'
      }[String(result || '').toUpperCase()] || String(result || '未知结果')
    },
    createRequestId() {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `sign-batch-finalize-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    handleBeforeClose(done) {
      if (this.operationLocked) return
      this.resetState(); done()
    },
    closeDialog() {
      if (!this.operationLocked) { this.resetState(); this.$emit('update:visible', false) }
    },
    handleClosed() { if (!this.visible) this.resetState() },
    resetState() {
      this._previewSchedule = (this._previewSchedule || 0) + 1
      this.operationScope().invalidate()
      this.confirming = false; this.checking = false; this.commandUnknown = false; this.checkedUnknown = false
      this.pendingCommand = null; this.recoveryMessage = ''
      this.previewLoading = false
      this.submitting = false
      this.previewError = ''
      this.legalEntities = []
      this.rows = []
      this.bulkLegalEntityId = ''
      this.bulkSealId = ''
      this.confirmed = false
      this.requestId = ''
      this.responseSummary = null
    }
  }
}
</script>

<style lang="scss" scoped>
.batch-finalize-workbench { min-height: 190px; }
.workbench-alert { margin-bottom: 12px; }
.preview-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; color: #475569; }
.preview-summary strong { color: #1e293b; }
.batch-apply-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; padding: 12px; border: 1px solid #dbeafe; border-radius: 10px; background: #f8fbff; }
.batch-apply-label { margin-right: 4px; color: #1e3a5f; font-weight: 600; }
.batch-company-select { width: 250px; }
.batch-seal-select { width: 220px; }
.employee-summary, .recommendation-cell, .company-meta { display: flex; flex-direction: column; gap: 3px; }
.employee-summary strong { color: #1f2937; }
.employee-summary small, .recommendation-cell small { color: #94a3b8; }
.recommendation-cell strong { color: #334155; font-size: 13px; }
.recommendation-cell span, .company-meta span { color: #64748b; font-size: 11px; line-height: 1.45; }
.company-meta { margin-top: 6px; }
.inline-alert { padding: 5px 8px; }
.field-error { margin: 5px 0 0; color: #f56c6c; font-size: 11px; line-height: 1.4; }
.matched-copy, .pending-result { color: #909399; font-size: 12px; }
.blocker-list { display: flex; align-items: flex-start; flex-direction: column; gap: 5px; }
.blocker-list .el-tag { max-width: 100%; height: auto; padding: 3px 7px; line-height: 1.35; white-space: normal; }
.result-message { margin: 6px 0 0; color: #606266; font-size: 11px; line-height: 1.45; }
.execution-summary { margin-top: 12px; }
.finalize-confirmation { display: flex; align-items: flex-start; margin-top: 16px; white-space: normal; }
::v-deep .finalize-confirmation .el-checkbox__label { line-height: 1.5; }
::v-deep .finalize-table .el-table__cell { vertical-align: top; }
@media (max-width: 760px) {
  .batch-company-select, .batch-seal-select { width: 100%; }
  .batch-apply-bar .el-button { margin-left: 0; }
}
</style>
