<template>
  <el-dialog
    title="批量处理 Excel 签名优先记录"
    :visible="visible"
    width="94%"
    custom-class="sign-onboard-company-work-dialog"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    :before-close="handleBeforeClose"
    @closed="resetState"
  >
    <div v-loading="loading" class="onboard-company-workbench">
      <el-alert
        title="员工已完成事实确认并留存本任务签名。本步骤由 HR 确定合同公司与有效印章；预检通过后才会生成正式合同草稿，不会在本步骤自动发送。"
        type="info"
        show-icon
        :closable="false"
        class="work-alert"
      />

      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="work-alert" />

      <template v-if="rows.length">
        <div class="work-summary">
          <span>已选 <strong>{{ rows.length }}</strong> 项</span>
          <el-tag size="mini" type="success">本地校验通过 {{ localReadyCount }}</el-tag>
          <el-tag v-if="localBlockedCount" size="mini" type="danger">待处理 {{ localBlockedCount }}</el-tag>
          <el-tag size="mini" effect="plain">来源：Excel 签名优先</el-tag>
        </div>

        <div v-if="!hasResults" class="batch-apply-bar">
          <span class="batch-label">批量应用</span>
          <el-select v-model="bulkLegalEntityId" filterable clearable size="small" placeholder="选择合同公司" class="bulk-select" @change="handleBulkCompanyChoice">
            <el-option
              v-for="company in legalEntities"
              :key="companyId(company)"
              :label="companyLabel(company)"
              :value="companyId(company)"
              :disabled="!companyReady(company)"
            />
          </el-select>
          <el-button size="small" :loading="bulkLoading" :disabled="!bulkLegalEntityId" @click="applyCompanyToAll">应用公司到全部</el-button>
          <el-select v-model="bulkSealId" filterable clearable size="small" placeholder="选择该公司合同章" class="bulk-select" :disabled="!bulkLegalEntityId || !bulkSealOptions.length">
            <el-option v-for="seal in bulkSealOptions" :key="sealId(seal)" :label="sealLabel(seal)" :value="sealId(seal)" />
          </el-select>
          <el-button size="small" :disabled="!bulkSealId" @click="applySealToSameCompany">应用印章到同公司</el-button>
        </div>

        <el-table :data="rows" row-key="workKey" border size="small" max-height="510" class="work-table">
          <el-table-column label="员工 / 来源" width="165" fixed="left">
            <template slot-scope="scope">
              <div class="stack-cell">
                <strong>{{ scope.row.employeeName || `员工 #${scope.row.employeeId}` }}</strong>
                <small>{{ scope.row.batchNo || `批次 ${scope.row.batchId}` }}</small>
                <small>Excel 第 {{ scope.row.sourceRowNumber || scope.row.rowId }} 行</small>
                <el-tag size="mini" effect="plain">Excel 签名优先</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="组织 / 套餐" min-width="180">
            <template slot-scope="scope">
              <div class="stack-cell">
                <span>{{ scope.row.shopDeptName || `组织 #${scope.row.shopDeptId}` }}</span>
                <strong>{{ scope.row.planName || '套餐待确认' }}</strong>
                <small>{{ templateSummary(scope.row) }}</small>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="合同公司" min-width="235">
            <template slot-scope="scope">
              <el-select
                v-model="scope.row.legalEntityId"
                filterable
                placeholder="请选择合同公司"
                size="small"
                style="width: 100%"
                :disabled="busy || rowSucceeded(scope.row)"
                @change="handleCompanyChange(scope.row)"
              >
                <el-option
                  v-for="company in companyOptions(scope.row)"
                  :key="companyId(company)"
                  :label="companyLabel(company)"
                  :value="companyId(company)"
                  :disabled="!companyReady(company)"
                />
              </el-select>
              <div v-if="selectedCompany(scope.row)" class="company-meta">
                <small>信用代码：{{ selectedCompany(scope.row).unifiedSocialCreditCode || '未维护' }}</small>
                <small>法定代表人：{{ selectedCompany(scope.row).legalRepresentative || '未维护' }}</small>
              </div>
              <p v-if="companyMismatch(scope.row)" class="field-warning">与原自动匹配公司不一致，需填写改选说明</p>
            </template>
          </el-table-column>

          <el-table-column label="合同印章" min-width="190">
            <template slot-scope="scope">
              <el-select
                v-model="scope.row.sealId"
                filterable
                clearable
                placeholder="请选择有效合同印章"
                size="small"
                style="width: 100%"
                :loading="isSealLoading(scope.row.legalEntityId)"
                :disabled="!scope.row.legalEntityId || busy || rowSucceeded(scope.row)"
                @change="markEdited"
              >
                <el-option v-for="seal in sealsForCompany(scope.row.legalEntityId)" :key="sealId(seal)" :label="sealLabel(seal)" :value="sealId(seal)" />
              </el-select>
              <p v-if="scope.row.legalEntityId && !isSealLoading(scope.row.legalEntityId) && !sealsForCompany(scope.row.legalEntityId).length" class="field-error">
                该公司没有可用的有效合同印章
              </p>
            </template>
          </el-table-column>

          <el-table-column label="业务确认" min-width="280">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.noExternalContractConfirmed" :disabled="busy || rowSucceeded(scope.row)" class="fact-confirm" @change="markEdited">
                确认不存在已签纸质合同或第三方电子合同
              </el-checkbox>
              <el-input
                v-if="requiresHistoricalReason(scope.row)"
                v-model.trim="scope.row.historicalReason"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="请填写历史补签原因"
                :disabled="busy || rowSucceeded(scope.row)"
                class="reason-input"
                @input="markEdited"
              />
              <el-input
                v-if="requiresWarningReason(scope.row)"
                v-model.trim="scope.row.warningReason"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                :placeholder="companyMismatch(scope.row) ? '请说明改选公司的原因' : '请填写黄色预警核对原因'"
                :disabled="busy || rowSucceeded(scope.row)"
                class="reason-input"
                @input="markEdited"
              />
            </template>
          </el-table-column>

          <el-table-column label="预检 / 阻断" min-width="245">
            <template slot-scope="scope">
              <div v-if="rowBlockers(scope.row).length" class="blocker-list">
                <el-tag v-for="(blocker, index) in rowBlockers(scope.row)" :key="`${scope.row.workKey}:${index}`" size="mini" type="danger" effect="plain">
                  {{ blockerLabel(blocker) }}
                </el-tag>
              </div>
              <el-tag v-else size="mini" type="success" effect="plain">{{ preflightPassed ? '服务端预检通过' : '可发起服务端预检' }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="处理结果" min-width="180" fixed="right">
            <template slot-scope="scope">
              <template v-if="scope.row.result">
                <el-tag size="mini" :type="resultTagType(scope.row.result)">{{ resultLabel(scope.row.result) }}</el-tag>
                <p class="result-message">{{ scope.row.resultMessage || '-' }}</p>
                <el-button
                  v-if="executionDone && rowSucceeded(scope.row) && scope.row.taskId"
                  v-hasPermi="['oa:signTask:send']"
                  type="text"
                  size="mini"
                  @click="openPreparedTask(scope.row.taskId)"
                >预览并发送最终文件</el-button>
              </template>
              <span v-else class="muted-copy">待预检</span>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="hasResults"
          :title="resultSummaryText"
          :type="executionDone ? (failureCount ? 'warning' : 'success') : (preflightPassed ? 'success' : 'warning')"
          show-icon
          :closable="false"
          class="result-summary"
        />

        <el-checkbox v-if="preflightPassed && !executionDone" v-model="confirmed" :disabled="busy" class="final-confirmation">
          我已核对员工事实确认结果、合同公司、有效印章及补签/预警说明，确认生成正式合同草稿
        </el-checkbox>
      </template>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button :disabled="busy" @click="closeDialog">{{ executionDone ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!preflightPassed && !executionDone" type="primary" :loading="previewLoading" :disabled="!canPreview" @click="runPreview">
        服务端预检（{{ rows.length }}）
      </el-button>
      <el-button v-else-if="!executionDone" type="primary" icon="el-icon-s-claim" :loading="executing" :disabled="!confirmed" @click="confirmAndExecute">
        批量生成正式合同草稿（{{ rows.length }}）
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { syncOnboardSalaryBeforeGenerate } from "@/utils/onboardSalarySync"
import {
  executeOnboardSignCompanyWork,
  getOnboardSignCompanyWorkOptions,
  previewOnboardSignCompanyWork
} from '@/api/oa/signTask'
const { signBusinessText } = require('@/utils/signDisplayText')

const SUCCESS_RESULTS = ['GENERATED', 'REUSED', 'SUCCESS']
const LOCAL_CONFIRMATION_FIELDS = ['noExternalContractConfirmation', 'historicalSupplementReason', 'warningConfirmationReason']
const COMPANY_ERROR_PREFIX = 'COMPANY_'

export default {
  name: 'SignOnboardCompanyWorkDialog',
  props: {
    visible: { type: Boolean, default: false },
    items: { type: Array, default: () => [] }
  },
  data() {
    return {
      loading: false,
      previewLoading: false,
      executing: false,
      bulkLoading: false,
      loadError: '',
      rows: [],
      legalEntities: [],
      sealOptionsByCompany: {},
      recommendedSealByCompany: {},
      sealLoadingIds: [],
      bulkLegalEntityId: '',
      bulkSealId: '',
      requestId: '',
      preflightPassed: false,
      executionDone: false,
      confirmed: false,
      lastSummary: null,
      salarySyncState: {}
    }
  },
  computed: {
    busy() {
      return this.loading || this.previewLoading || this.executing || this.bulkLoading
    },
    localReadyCount() {
      return this.rows.filter(row => this.localBlockers(row).length === 0).length
    },
    localBlockedCount() {
      return this.rows.length - this.localReadyCount
    },
    canPreview() {
      return this.rows.length > 0 && this.localBlockedCount === 0 && !this.busy && !this.executionDone
    },
    hasResults() {
      return this.rows.some(row => !!row.result)
    },
    successCount() {
      return this.rows.filter(row => this.rowSucceeded(row)).length
    },
    failureCount() {
      return this.rows.filter(row => row.result && !this.rowSucceeded(row) && row.result !== 'READY').length
    },
    resultSummaryText() {
      if (this.executionDone) return `处理完成：成功 ${this.successCount} 项，未成功 ${this.failureCount} 项。未成功项会保留在待处理区。`
      if (this.preflightPassed) return `服务端预检通过 ${this.rows.length} 项，请完成核对确认后生成正式合同草稿。`
      const blocked = this.rows.filter(row => row.result && row.result !== 'READY').length
      return `服务端预检未通过：${blocked} 项仍有阻断，请按逐项结果修正后重新预检。`
    },
    bulkSealOptions() {
      return this.sealsForCompany(this.bulkLegalEntityId)
    }
  },
  watch: {
    visible(value) {
      if (value) this.initialize()
    }
  },
  methods: {
    initialize() {
      const source = (this.items || []).slice(0, 20)
      this.rows = source.map(item => this.createRow(item))
      this.loading = true
      this.loadError = ''
      this.seedSealOptions(source)
      return getOnboardSignCompanyWorkOptions().then(response => {
        const data = response.data || {}
        this.legalEntities = this.mergeCompanies(data.legalEntities || [], source)
        const entityId = this.normalizeId(data.legalEntityId)
        if (entityId && Array.isArray(data.seals)) this.$set(this.sealOptionsByCompany, entityId, data.seals.slice())
        return Promise.all(this.uniqueSelectedCompanyIds().map(id => this.ensureCompanySeals(id)))
      }).catch(error => {
        this.loadError = signBusinessText(error && error.message, '无法加载公司与印章选项，请关闭后刷新重试')
      }).finally(() => {
        this.rows.forEach(row => {
          if (!row.sealId) row.sealId = this.resolveRecommendedSeal(row.legalEntityId, row.recommendedSealId)
        })
        this.loading = false
      })
    },
    createRow(item) {
      return Object.assign({}, item, {
        workKey: `${this.normalizeId(item.batchId)}:${this.normalizeId(item.rowId)}`,
        batchId: this.normalizeId(item.batchId),
        rowId: this.normalizeId(item.rowId),
        employeeId: this.normalizeId(item.employeeId),
        legalEntityId: this.normalizeId(item.matchedLegalEntityId),
        originalLegalEntityId: this.normalizeId(item.matchedLegalEntityId),
        sealId: this.normalizeId(item.recommendedSealId),
        noExternalContractConfirmed: item.noExternalContractConfirmed === true,
        historicalReason: String(item.historicalReason || ''),
        warningReason: String(item.warningReason || ''),
        serverBlockers: [],
        result: '',
        resultMessage: ''
      })
    },
    seedSealOptions(items) {
      ;(items || []).forEach(item => {
        const entityId = this.normalizeId(item.matchedLegalEntityId)
        if (!entityId || !Array.isArray(item.sealCandidates) || !item.sealCandidates.length) return
        this.$set(this.sealOptionsByCompany, entityId, item.sealCandidates.slice())
        if (item.recommendedSealId) this.$set(this.recommendedSealByCompany, entityId, this.normalizeId(item.recommendedSealId))
      })
    },
    mergeCompanies(globalOptions, items) {
      const result = []
      const seen = new Set()
      const append = company => {
        const id = this.companyId(company)
        if (!id || seen.has(id)) return
        seen.add(id)
        result.push(company)
      }
      ;(globalOptions || []).forEach(append)
      ;(items || []).forEach(item => (item.companyCandidates || []).forEach(append))
      return result
    },
    uniqueSelectedCompanyIds() {
      return Array.from(new Set(this.rows.map(row => this.normalizeId(row.legalEntityId)).filter(Boolean)))
    },
    companyOptions(row) {
      const local = Array.isArray(row && row.companyCandidates) ? row.companyCandidates : []
      return this.mergeCompanies(this.legalEntities, [{ companyCandidates: local }])
    },
    companyId(company) {
      return this.normalizeId(company && (company.legalEntityId || company.id))
    },
    companyName(company) {
      return String(company && (company.legalEntityName || company.name) || '未命名公司')
    },
    companyLabel(company) {
      const missing = this.companyMissingFields(company)
      return this.companyName(company) + (missing.length ? `（缺${missing.join('、')}）` : '')
    },
    companyMissingFields(company) {
      if (!company) return []
      if (Array.isArray(company.missingMasterFields) && company.missingMasterFields.length) return company.missingMasterFields
      const missing = []
      if (!String(company.legalEntityName || '').trim()) missing.push('公司全称')
      if (!String(company.unifiedSocialCreditCode || '').trim()) missing.push('统一社会信用代码')
      if (!String(company.registeredAddress || '').trim()) missing.push('注册地址')
      if (!String(company.legalRepresentative || '').trim()) missing.push('法定代表人')
      return missing
    },
    companyReady(company) {
      return !!company && company.selectable !== false && this.companyMissingFields(company).length === 0
    },
    selectedCompany(row) {
      const id = this.normalizeId(row && row.legalEntityId)
      return this.companyOptions(row).find(company => this.companyId(company) === id) || null
    },
    companyMismatch(row) {
      return !!row && !!row.legalEntityId && !!row.originalLegalEntityId &&
        this.normalizeId(row.legalEntityId) !== this.normalizeId(row.originalLegalEntityId)
    },
    sealId(seal) {
      return this.normalizeId(seal && (seal.sealId || seal.id))
    },
    sealLabel(seal) {
      const suffix = seal && (seal.defaultSeal === true || seal.default === true || seal.isDefault === 'Y') ? '（默认）' : ''
      return String(seal && (seal.sealName || seal.name) || '未命名印章') + suffix
    },
    sealsForCompany(legalEntityId) {
      return this.sealOptionsByCompany[this.normalizeId(legalEntityId)] || []
    },
    isSealLoading(legalEntityId) {
      return this.sealLoadingIds.includes(this.normalizeId(legalEntityId))
    },
    ensureCompanySeals(legalEntityId) {
      const id = this.normalizeId(legalEntityId)
      if (!id || this.sealsForCompany(id).length || this.isSealLoading(id)) return Promise.resolve()
      this.sealLoadingIds = this.sealLoadingIds.concat(id)
      return getOnboardSignCompanyWorkOptions(id).then(response => {
        const data = response.data || {}
        this.$set(this.sealOptionsByCompany, id, Array.isArray(data.seals) ? data.seals.slice() : [])
        if (data.recommendedSealId) this.$set(this.recommendedSealByCompany, id, this.normalizeId(data.recommendedSealId))
        if (Array.isArray(data.legalEntities)) this.legalEntities = this.mergeCompanies(this.legalEntities.concat(data.legalEntities), [])
      }).finally(() => {
        this.sealLoadingIds = this.sealLoadingIds.filter(value => value !== id)
      })
    },
    resolveRecommendedSeal(legalEntityId, rowRecommendation) {
      const id = this.normalizeId(legalEntityId)
      const seals = this.sealsForCompany(id)
      const recommended = this.normalizeId(this.recommendedSealByCompany[id] || rowRecommendation)
      if (recommended && seals.some(seal => this.sealId(seal) === recommended)) return recommended
      const defaultSeal = seals.find(seal => seal.defaultSeal === true || seal.default === true || seal.isDefault === 'Y')
      if (defaultSeal) return this.sealId(defaultSeal)
      return seals.length === 1 ? this.sealId(seals[0]) : ''
    },
    handleCompanyChange(row) {
      row.sealId = ''
      this.markEdited()
      return this.ensureCompanySeals(row.legalEntityId).then(() => {
        row.sealId = this.resolveRecommendedSeal(row.legalEntityId, '')
      }).catch(error => {
        this.$message.error(signBusinessText(error && error.message, '合同印章加载失败'))
      })
    },
    handleBulkCompanyChoice() {
      this.bulkSealId = ''
      if (this.bulkLegalEntityId) this.ensureCompanySeals(this.bulkLegalEntityId).catch(() => {})
    },
    applyCompanyToAll() {
      if (!this.bulkLegalEntityId) return
      const company = this.legalEntities.find(item => this.companyId(item) === this.normalizeId(this.bulkLegalEntityId))
      if (!this.companyReady(company)) {
        this.$message.warning('该公司主数据不完整，不能用于正式合同')
        return
      }
      this.bulkLoading = true
      return this.ensureCompanySeals(this.bulkLegalEntityId).then(() => {
        this.rows.forEach(row => {
          row.legalEntityId = this.normalizeId(this.bulkLegalEntityId)
          row.sealId = this.resolveRecommendedSeal(row.legalEntityId, '')
        })
        this.markEdited()
      }).catch(error => {
        this.$message.error(signBusinessText(error && error.message, '批量加载印章失败'))
      }).finally(() => {
        this.bulkLoading = false
      })
    },
    applySealToSameCompany() {
      if (!this.bulkLegalEntityId || !this.bulkSealId) return
      let count = 0
      this.rows.forEach(row => {
        if (this.normalizeId(row.legalEntityId) !== this.normalizeId(this.bulkLegalEntityId)) return
        row.sealId = this.normalizeId(this.bulkSealId)
        count += 1
      })
      this.markEdited()
      this.$message.success(`已将印章应用到 ${count} 个同公司记录`)
    },
    requiresHistoricalReason(row) {
      return !!row && (row.historicalSupplement === true || (row.missingFields || []).includes('historicalSupplementReason'))
    },
    requiresWarningReason(row) {
      return !!row && (this.companyMismatch(row) || (row.warningCodes || []).length > 0 ||
        (row.missingFields || []).includes('warningConfirmationReason'))
    },
    localBlockers(row) {
      const blockers = []
      if (!row.signatureCaptured) blockers.push('SIGNATURE_CONFIRMATION_INCOMPLETE')
      if (!row.legalEntityId) blockers.push('请选择合同公司')
      const company = this.selectedCompany(row)
      if (row.legalEntityId && !company) blockers.push('所选公司已不在可用范围')
      if (company && !this.companyReady(company)) blockers.push(`公司主数据待补：${this.companyMissingFields(company).join('、')}`)
      if (row.legalEntityId && !row.sealId) blockers.push('请选择有效合同印章')
      if (!row.noExternalContractConfirmed) blockers.push('请确认无外部已签合同')
      if (this.requiresHistoricalReason(row) && !String(row.historicalReason || '').trim()) blockers.push('请填写历史补签原因')
      if (this.requiresWarningReason(row) && !String(row.warningReason || '').trim()) blockers.push(this.companyMismatch(row) ? '请填写改选公司原因' : '请填写黄色预警核对原因')
      ;(row.errorCodes || []).filter(code => !String(code || '').startsWith(COMPANY_ERROR_PREFIX)).forEach(code => blockers.push(code))
      ;(row.missingFields || []).filter(field => !LOCAL_CONFIRMATION_FIELDS.includes(field)).forEach(field => blockers.push(field))
      return Array.from(new Set(blockers))
    },
    rowBlockers(row) {
      return Array.from(new Set(this.localBlockers(row).concat(row.serverBlockers || [])))
    },
    markEdited() {
      this.requestId = ''
      this.salarySyncState = {}
      this.preflightPassed = false
      this.confirmed = false
      this.lastSummary = null
      this.rows.forEach(row => {
        row.serverBlockers = []
        row.result = ''
        row.resultMessage = ''
      })
    },
    payload() {
      if (!this.requestId) this.requestId = this.createRequestId()
      return {
        requestId: this.requestId,
        items: this.rows.map(row => ({
          batchId: row.batchId,
          rowId: row.rowId,
          version: row.version,
          legalEntityId: this.normalizeId(row.legalEntityId),
          sealId: this.normalizeId(row.sealId),
          noExternalContractConfirmed: row.noExternalContractConfirmed === true,
          historicalReason: String(row.historicalReason || '').trim() || null,
          warningReason: String(row.warningReason || '').trim() || null
        }))
      }
    },
    runPreview() {
      if (!this.canPreview) {
        this.$message.warning('请先处理所有本地校验项')
        return
      }
      this.previewLoading = true
      return previewOnboardSignCompanyWork(this.payload()).then(response => {
        const data = response.data || {}
        this.lastSummary = data
        this.applyResults(data.items || [])
        this.preflightPassed = this.rows.length > 0 && this.rows.every(row => row.result === 'READY')
        if (!this.preflightPassed) this.confirmed = false
      }).catch(error => {
        this.$message.error(signBusinessText(error && error.message, '服务端预检失败，请刷新后重试'))
      }).finally(() => {
        this.previewLoading = false
      })
    },
    confirmAndExecute() {
      if (!this.preflightPassed || !this.confirmed) return
      return this.$confirm(
        `即将为 ${this.rows.length} 位员工固定公司与印章并生成正式合同草稿。草稿生成后还需预览确认再发送，是否继续？`,
        '二次确认',
        { confirmButtonText: '确认生成', cancelButtonText: '返回核对', type: 'warning' }
      ).then(() => this.execute()).catch(reason => {
        if (reason !== 'cancel' && reason !== 'close') this.$message.error(signBusinessText(reason && reason.message, '无法完成二次确认'))
      })
    },
    execute() {
      this.executing = true
      const payload = this.payload()
      return syncOnboardSalaryBeforeGenerate(this.rows, this.salarySyncState).then(() => executeOnboardSignCompanyWork(payload)).then(response => {
        const data = response.data || {}
        this.lastSummary = data
        this.applyResults(data.items || [])
        this.executionDone = true
        if (this.successCount > 0) this.$emit('completed', { successCount: this.successCount, response: data })
      }).catch(error => {
        this.$message.error(signBusinessText(error && error.message, '正式合同草稿生成失败，本次请求号已保留，可安全重试'))
      }).finally(() => {
        this.executing = false
      })
    },
    applyResults(items) {
      const byKey = new Map()
      ;(items || []).forEach(item => byKey.set(`${this.normalizeId(item.batchId)}:${this.normalizeId(item.rowId)}`, item))
      this.rows.forEach(row => {
        const item = byKey.get(row.workKey)
        if (!item) return
        row.result = String(item.result || item.status || 'FAILED').toUpperCase()
        row.resultMessage = String(item.message || '')
        row.serverBlockers = Array.isArray(item.blockers) ? item.blockers.slice() : []
        if (/^[1-9]\d{0,18}$/.test(this.normalizeId(item.taskId))) row.taskId = this.normalizeId(item.taskId)
        if (/^[1-9]\d{0,18}$/.test(this.normalizeId(item.packageId))) row.packageId = this.normalizeId(item.packageId)
      })
    },
    openPreparedTask(taskId) {
      const normalized = this.normalizeId(taskId)
      if (!/^[1-9]\d{0,18}$/.test(normalized)) return
      this.$emit('open-task', normalized)
    },
    rowSucceeded(row) {
      return !!row && SUCCESS_RESULTS.includes(String(row.result || '').toUpperCase())
    },
    resultTagType(result) {
      const normalized = String(result || '').toUpperCase()
      if (SUCCESS_RESULTS.includes(normalized) || normalized === 'READY') return 'success'
      if (normalized === 'BLOCKED') return 'warning'
      return 'danger'
    },
    resultLabel(result) {
      return { READY: '预检通过', GENERATED: '草稿已生成', REUSED: '已生成', BLOCKED: '仍有阻断', FAILED: '处理失败' }[String(result || '').toUpperCase()] || String(result || '未知结果')
    },
    blockerLabel(code) {
      const labels = {
        SIGNATURE_CONFIRMATION_INCOMPLETE: '员工事实确认或签名证据不完整',
        EMPLOYEE_FACTS_CHANGED: '员工事实已变更，需重新确认',
        ROW_VERSION_CHANGED: '导入行已变更，请刷新',
        COMPANY_NOT_CONTRACT_READY: '合同公司主数据不完整',
        SEAL_NOT_CONTRACT_READY: '合同印章无效、过期或不属于所选公司',
        EXISTING_OPEN_ONBOARD_TASK: '已有进行中的入职签约任务，无需重复生成',
        PLAN_MISSING: '签约套餐或方案未准备完成',
        noExternalContractConfirmation: '无外部已签合同确认',
        historicalSupplementReason: '历史补签原因',
        warningConfirmationReason: '黄色预警核对原因'
      }
      return labels[code] || String(code || '未知阻断')
    },
    templateSummary(row) {
      const names = Array.isArray(row && row.templateNames) ? row.templateNames : []
      return names.length ? names.join('、') : '模板待确认'
    },
    normalizeId(value) {
      return value === null || value === undefined || value === '' ? '' : String(value)
    },
    createRequestId() {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
      return `onboard-company-work-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    handleBeforeClose(done) {
      if (!this.busy) done()
    },
    closeDialog() {
      if (!this.busy) this.$emit('update:visible', false)
    },
    resetState() {
      this.loading = false
      this.previewLoading = false
      this.executing = false
      this.bulkLoading = false
      this.loadError = ''
      this.rows = []
      this.legalEntities = []
      this.sealOptionsByCompany = {}
      this.recommendedSealByCompany = {}
      this.sealLoadingIds = []
      this.bulkLegalEntityId = ''
      this.bulkSealId = ''
      this.requestId = ''
      this.salarySyncState = {}
      this.preflightPassed = false
      this.executionDone = false
      this.confirmed = false
      this.lastSummary = null
    }
  }
}
</script>

<style lang="scss" scoped>
.onboard-company-workbench { min-height: 200px; }
.work-alert { margin-bottom: 12px; }
.work-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; color: #475569; }
.batch-apply-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; padding: 12px; border: 1px solid #dbeafe; border-radius: 10px; background: #f8fbff; }
.batch-label { color: #1e3a5f; font-weight: 600; }
.bulk-select { width: 240px; }
.stack-cell, .company-meta { display: flex; flex-direction: column; gap: 4px; }
.stack-cell strong { color: #334155; }
.stack-cell small, .company-meta small { color: #64748b; line-height: 1.4; }
.company-meta { margin-top: 5px; }
.fact-confirm { display: flex; align-items: flex-start; white-space: normal; }
.reason-input { margin-top: 7px; }
.field-warning { margin: 5px 0 0; color: #e6a23c; font-size: 11px; }
.field-error { margin: 5px 0 0; color: #f56c6c; font-size: 11px; }
.blocker-list { display: flex; align-items: flex-start; flex-direction: column; gap: 5px; }
.blocker-list .el-tag { max-width: 100%; height: auto; padding: 3px 7px; line-height: 1.35; white-space: normal; }
.result-message { margin: 5px 0 0; color: #606266; font-size: 11px; line-height: 1.4; }
.muted-copy { color: #909399; font-size: 12px; }
.result-summary { margin-top: 12px; }
.final-confirmation { display: flex; align-items: flex-start; margin-top: 16px; white-space: normal; }
::v-deep .work-table .el-table__cell { vertical-align: top; }
::v-deep .fact-confirm .el-checkbox__label, ::v-deep .final-confirmation .el-checkbox__label { line-height: 1.45; }
@media (max-width: 760px) { .bulk-select { width: 100%; } }
</style>
