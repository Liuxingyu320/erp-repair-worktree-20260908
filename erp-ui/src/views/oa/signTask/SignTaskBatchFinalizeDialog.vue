<template>
  <el-dialog
    title="批量选择公司并盖章"
    :visible="visible"
    width="94%"
    custom-class="sign-task-batch-finalize-dialog"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="handleBeforeClose"
    @closed="resetState"
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
          <el-button type="text" size="mini" :disabled="submitting" @click="loadPreview">刷新预检</el-button>
        </div>

        <div v-if="!hasExecutionResults" class="batch-apply-bar">
          <span class="batch-apply-label">批量应用</span>
          <el-select
            v-model="bulkLegalEntityId"
            filterable
            clearable
            size="small"
            placeholder="先选择合同公司"
            class="batch-company-select"
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
          <el-button size="small" :disabled="!bulkLegalEntityId" @click="applyCompanyToAll">
            应用公司到全部
          </el-button>
          <el-select
            v-model="bulkSealId"
            filterable
            clearable
            size="small"
            placeholder="选择同公司印章"
            class="batch-seal-select"
            :disabled="!bulkLegalEntityId || !bulkSealOptions.length"
          >
            <el-option
              v-for="seal in bulkSealOptions"
              :key="sealId(seal)"
              :label="sealName(seal)"
              :value="sealId(seal)"
            />
          </el-select>
          <el-button size="small" :disabled="!bulkSealId" @click="applySealToSameCompany">
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
                :disabled="submitting || rowSucceeded(scope.row)"
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
                  :disabled="!scope.row.legalEntityId || submitting || rowSucceeded(scope.row)"
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
                :disabled="submitting || rowSucceeded(scope.row)"
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
          v-if="!hasExecutionResults"
          v-model="confirmed"
          :disabled="submitting || blockedCount > 0"
          class="finalize-confirmation"
        >
          我已逐项核对合同公司、有效印章与改选原因，确认生成最终合同；本步骤不发送，预览后另行发送最终文件
        </el-checkbox>
      </template>

      <el-empty v-else-if="!previewLoading && !previewError" description="没有可批量处理的任务" />
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button :disabled="submitting" @click="closeDialog">{{ hasExecutionResults ? '关闭' : '取消' }}</el-button>
      <el-button
        v-if="!hasExecutionResults"
        type="primary"
        icon="el-icon-s-claim"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="confirmAndSubmit"
      >批量生成正式合同（{{ rows.length }}）</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { finalizeSignTaskBatch, previewSignTaskBatchFinalize } from '@/api/oa/signTask'
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
    readyCount() {
      return this.rows.filter(row => this.rowBlockers(row).length === 0).length
    },
    blockedCount() {
      return this.rows.length - this.readyCount
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
      return this.rows.length > 0 && !this.previewLoading && !this.submitting &&
        !this.hasExecutionResults && this.blockedCount === 0 && this.confirmed
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
    visible(value) {
      if (value) this.loadPreview()
    }
  },
  methods: {
    loadPreview() {
      if (this.previewLoading || this.submitting) return
      const taskIds = this.normalizedTaskIds()
      if (!taskIds.length) {
        this.previewError = '没有选中的待选公司盖章任务'
        this.rows = []
        return
      }
      if (taskIds.length > 20) {
        this.previewError = '单次最多处理20个签约任务'
        this.rows = []
        return
      }
      this.previewLoading = true
      this.previewError = ''
      this.responseSummary = null
      this.requestId = ''
      this.confirmed = false
      return previewSignTaskBatchFinalize({ taskIds }).then(response => {
        const data = response.data || {}
        const items = Array.isArray(data.items) ? data.items : []
        this.legalEntities = this.mergeCompanyCandidates(items)
        this.rows = items.map(item => this.createRow(item))
        if (!this.rows.length) this.previewError = '服务端未返回可处理任务，请刷新列表后重试'
      }).catch(error => {
        this.rows = []
        this.legalEntities = []
        this.previewError = signBusinessText(error && error.message, '批量预检失败，请刷新任务列表后重试')
      }).finally(() => {
        this.previewLoading = false
      })
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
      row.sealId = this.resolveRecommendedSeal(row)
      row.correctionReason = this.recommendationMismatch(row) ? row.correctionReason : ''
      this.markEdited()
    },
    handleBulkCompanyChoice() {
      this.bulkSealId = ''
    },
    applyCompanyToAll() {
      if (!this.bulkLegalEntityId) return
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
      if (!this.bulkLegalEntityId || !this.bulkSealId) return
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
      this.requestId = ''
      this.confirmed = false
    },
    confirmAndSubmit() {
      if (!this.canSubmit) {
        this.$message.warning('请先处理所有阻断项并完成核对确认')
        return
      }
      const sealCount = this.rows.filter(row => this.requiresSeal(row)).length
      return this.$confirm(
        `即将为 ${this.rows.length} 位员工固定合同公司，其中 ${sealCount} 项将加盖公司印章并生成最终合同。本步骤不会发送，生成后还需预览并另行发送最终文件。是否继续？`,
        '二次确认',
        { confirmButtonText: '确认生成', cancelButtonText: '返回核对', type: 'warning' }
      ).then(() => this.submitBatch()).catch(reason => {
        if (reason !== 'cancel' && reason !== 'close') {
          this.$message.error(signBusinessText(reason && reason.message, '无法完成二次确认'))
        }
      })
    },
    submitBatch() {
      if (this.submitting) return
      if (!this.requestId) this.requestId = this.createRequestId()
      const items = this.rows.map(row => ({
        taskId: row.taskId,
        packageId: row.packageId,
        expectedTaskVersion: row.taskVersion,
        expectedPackageVersion: row.packageVersion,
        legalEntityId: this.normalizeId(row.legalEntityId),
        sealId: this.requiresSeal(row) ? this.normalizeId(row.sealId) : null,
        correctionReason: String(row.correctionReason || '').trim() || null
      }))
      this.submitting = true
      return finalizeSignTaskBatch({ requestId: this.requestId, items }).then(response => {
        const data = response.data || {}
        const results = Array.isArray(data.items) ? data.items
          : (Array.isArray(data.results) ? data.results : [])
        this.responseSummary = data.summary || data
        this.applyExecutionResults(results)
        const reportedSuccess = (Number(this.responseSummary.successCount) || 0) +
          (Number(this.responseSummary.finalizedCount) || 0) +
          (Number(this.responseSummary.alreadyFinalizedCount) || 0)
        const successCount = Math.max(reportedSuccess, this.executionSuccessCount)
        if (successCount > 0) this.$emit('completed', { successCount, response: data })
        if (!results.length) {
          this.$message.warning('服务端未返回逐项结果，请刷新列表确认处理状态')
        }
      }).catch(error => {
        this.$message.error(signBusinessText(error && error.message, '批量生成正式合同失败，本次请求号已保留，可安全重试'))
      }).finally(() => {
        this.submitting = false
      })
    },
    applyExecutionResults(results) {
      const byTaskId = new Map()
      results.forEach(item => byTaskId.set(this.normalizeId(item && item.taskId), item || {}))
      this.rows.forEach(row => {
        const result = byTaskId.get(this.normalizeId(row.taskId))
        if (!result) return
        row.result = String(result.result || result.status || (result.success === true ? 'SUCCESS' : 'FAILED')).toUpperCase()
        row.resultMessage = String(result.message || result.failureReason || result.detail || '')
      })
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
        FAILED: '处理失败'
      }[String(result || '').toUpperCase()] || String(result || '未知结果')
    },
    createRequestId() {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `sign-batch-finalize-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    handleBeforeClose(done) {
      if (this.submitting) return
      done()
    },
    closeDialog() {
      if (!this.submitting) this.$emit('update:visible', false)
    },
    resetState() {
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
