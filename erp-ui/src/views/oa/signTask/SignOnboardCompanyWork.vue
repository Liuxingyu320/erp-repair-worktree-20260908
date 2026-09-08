<template>
  <el-card v-if="loading || loadError || totalCount > 0" shadow="never" class="onboard-company-card table-card oa-table-card">
    <div slot="header" class="company-card-header">
      <div>
        <div class="company-card-title">
          <h2>Excel 签名优先 · 待选公司与印章</h2>
          <el-tag size="mini" type="warning">{{ totalCount }} 项</el-tag>
        </div>
        <p>员工已在同一入职签约包完成事实确认和唯一一次签名；请在此选择公司与印章并生成最终合同草稿，预览无误后再另行发送最终文件。</p>
      </div>
      <div class="company-card-actions">
        <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" size="small" icon="el-icon-s-claim" :disabled="selectedKeys.length === 0" @click="openDialog">
          批量选择公司并盖章（{{ selectedKeys.length }}）
        </el-button>
      </div>
    </div>

    <el-alert v-if="loadError" :title="loadError" type="warning" show-icon :closable="false" class="company-card-alert" />

    <el-table v-loading="loading" :data="items" row-key="workKey" border size="small" max-height="360" empty-text="当前没有 Excel 签名优先待处理记录">
      <el-table-column width="52" align="center" fixed="left">
        <template slot="header">
          <el-checkbox
            :value="allSelected"
            :indeterminate="selectionIndeterminate"
            :disabled="eligibleItems.length === 0"
            aria-label="选择当前可处理的 Excel 签名优先记录"
            @change="toggleAll"
          />
        </template>
        <template slot-scope="scope">
          <el-tooltip :content="disabledReason(scope.row)" placement="top" :disabled="isSelectable(scope.row)">
            <span class="work-checkbox-wrap">
              <el-checkbox
                :value="selectedKeys.includes(scope.row.workKey)"
                :disabled="!isSelectable(scope.row)"
                :aria-label="`选择${scope.row.employeeName || '该员工'}的签名优先记录`"
                @change="toggleRow(scope.row, $event)"
              />
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="125">
        <template slot-scope="scope">
          <div class="source-cell">
            <el-tag size="mini" effect="plain">Excel 签名优先</el-tag>
            <small>{{ scope.row.batchNo || `批次 ${scope.row.batchId}` }}</small>
            <small>Excel 第 {{ scope.row.sourceRowNumber || scope.row.rowId }} 行</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="员工" width="130">
        <template slot-scope="scope">
          <div class="employee-cell">
            <strong>{{ scope.row.employeeName || `员工 #${scope.row.employeeId}` }}</strong>
            <small>{{ scope.row.signatureCaptured ? '签约包已确认并完成唯一签名' : '签名证据待确认' }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="签约组织" min-width="150">
        <template slot-scope="scope">{{ scope.row.shopDeptName || `组织 #${scope.row.shopDeptId}` }}</template>
      </el-table-column>
      <el-table-column label="套餐" min-width="170">
        <template slot-scope="scope">
          <div class="source-cell">
            <strong>{{ scope.row.planName || '套餐待确认' }}</strong>
            <small>{{ templateSummary(scope.row) }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前公司建议" min-width="190">
        <template slot-scope="scope">
          <div class="source-cell">
            <span>{{ scope.row.matchedLegalEntityName || '待 HR 选择' }}</span>
            <small>{{ scope.row.recommendedSealId ? '已有建议印章' : '印章待选择' }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="待处理项" min-width="220">
        <template slot-scope="scope">
          <div class="issue-tags">
            <el-tag v-for="issue in visibleIssues(scope.row)" :key="`${scope.row.workKey}:${issue}`" size="mini" :type="issue.startsWith('COMPANY_') ? 'warning' : 'danger'" effect="plain">
              {{ issueLabel(issue) }}
            </el-tag>
            <el-tag v-if="!visibleIssues(scope.row).length" size="mini" type="success" effect="plain">可进入公司与印章预检</el-tag>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <sign-onboard-company-work-dialog
      :visible.sync="dialogVisible"
      :items="selectedItems"
      @completed="handleCompleted"
      @open-task="handleOpenTask"
    />
  </el-card>
</template>

<script>
import { listOnboardSignCompanyWork } from '@/api/oa/signTask'
import SignOnboardCompanyWorkDialog from './SignOnboardCompanyWorkDialog'
const { signBusinessText } = require('@/utils/signDisplayText')

export default {
  name: 'SignOnboardCompanyWork',
  components: { SignOnboardCompanyWorkDialog },
  props: {
    focusPackageId: { type: [String, Number], default: '' }
  },
  data() {
    return {
      loading: false,
      loadError: '',
      items: [],
      totalCount: 0,
      selectedKeys: [],
      dialogVisible: false,
      handledFocusPackageId: ''
    }
  },
  computed: {
    eligibleItems() {
      return this.items.filter(item => this.isSelectable(item))
    },
    selectedItems() {
      return this.items.filter(item => this.selectedKeys.includes(item.workKey))
    },
    allSelected() {
      return this.eligibleItems.length > 0 && this.eligibleItems.every(item => this.selectedKeys.includes(item.workKey))
    },
    selectionIndeterminate() {
      const count = this.eligibleItems.filter(item => this.selectedKeys.includes(item.workKey)).length
      return count > 0 && count < this.eligibleItems.length
    }
  },
  created() {
    this.refresh()
  },
  watch: {
    focusPackageId(value, oldValue) {
      const normalized = String(value || '').trim()
      if (normalized === String(oldValue || '').trim()) return
      this.handledFocusPackageId = ''
      if (!normalized || this.loading) return
      if (!this.openFocusedPackage()) this.refresh()
    }
  },
  methods: {
    refresh() {
      if (this.loading) return
      this.loading = true
      this.loadError = ''
      return listOnboardSignCompanyWork().then(response => {
        const data = response.data || {}
        const rows = Array.isArray(data.items) ? data.items : []
        this.items = rows.map(item => Object.assign({}, item, {
          sourceType: item.sourceType || 'ONBOARD_IMPORT_ROW',
          workKey: `${String(item.batchId || '')}:${String(item.rowId || '')}`
        }))
        this.totalCount = Number(data.totalCount) || this.items.length
        if (!this.dialogVisible) this.selectedKeys = []
        this.openFocusedPackage()
      }).catch(error => {
        this.items = []
        this.totalCount = 0
        const status = error && error.response && Number(error.response.status)
        this.loadError = status === 404 ? ''
          : signBusinessText(error && error.message, '签名优先待处理记录加载失败')
      }).finally(() => {
        this.loading = false
      })
    },
    isSelectable(item) {
      return !!item && item.sourceType === 'ONBOARD_IMPORT_ROW' && item.signatureCaptured === true &&
        /^[1-9]\d{0,18}$/.test(String(item.batchId || '')) &&
        /^[1-9]\d{0,18}$/.test(String(item.rowId || '')) && Number(item.version) > 0
    },
    disabledReason(item) {
      if (!item || item.signatureCaptured !== true) return '员工尚未完成签约包确认与唯一一次签名'
      return '记录标识或版本无效，请刷新后重试'
    },
    toggleRow(item, checked) {
      if (!this.isSelectable(item)) return
      if (!checked) {
        this.selectedKeys = this.selectedKeys.filter(key => key !== item.workKey)
        return
      }
      if (this.selectedKeys.includes(item.workKey)) return
      if (this.selectedKeys.length >= 20) {
        this.$message.warning('单次最多处理20条 Excel 签名优先记录')
        return
      }
      this.selectedKeys = this.selectedKeys.concat(item.workKey)
    },
    toggleAll(checked) {
      if (!checked) {
        this.selectedKeys = []
        return
      }
      this.selectedKeys = this.eligibleItems.slice(0, 20).map(item => item.workKey)
      if (this.eligibleItems.length > 20) this.$message.warning('单次最多处理20条，已选择前20项')
    },
    openDialog() {
      if (!this.selectedItems.length) {
        this.$message.warning('请先选择待选公司盖章记录')
        return
      }
      this.dialogVisible = true
    },
    openFocusedPackage() {
      const packageId = String(this.focusPackageId || '').trim()
      if (!/^[1-9]\d{0,18}$/.test(packageId) || packageId === this.handledFocusPackageId) return false
      const item = this.items.find(row => String(row.packageId || '') === packageId)
      if (!item) return false
      this.handledFocusPackageId = packageId
      if (!this.isSelectable(item)) {
        this.$message.warning('该签约包状态已变化，请刷新后重试')
        return true
      }
      this.selectedKeys = [item.workKey]
      this.dialogVisible = true
      return true
    },
    handleCompleted(payload) {
      this.selectedKeys = []
      this.$emit('completed', payload)
      this.refresh()
    },
    handleOpenTask(taskId) {
      this.dialogVisible = false
      this.$emit('open-task', taskId)
    },
    visibleIssues(item) {
      return Array.from(new Set([].concat(item.errorCodes || [], item.missingFields || []))).slice(0, 8)
    },
    issueLabel(code) {
      const labels = {
        COMPANY_SELECTION_INVALID: '合同公司待选择',
        COMPANY_MATCH_REQUIRES_HR: '公司匹配待 HR 确认',
        COMPANY_MASTER_DATA_INCOMPLETE: '公司主数据不完整',
        COMPANY_SEAL_REQUIRES_HR: '合同印章待选择',
        COMPANY_SEAL_INVALID: '合同印章无效',
        EXISTING_OPEN_ONBOARD_TASK: '已有进行中的入职签约任务',
        noExternalContractConfirmation: '无外部已签合同确认',
        historicalSupplementReason: '历史补签原因',
        warningConfirmationReason: '黄色预警核对原因'
      }
      return labels[code] || String(code || '未知待处理项')
    },
    templateSummary(item) {
      const names = Array.isArray(item && item.templateNames) ? item.templateNames : []
      return names.length ? names.join('、') : '模板待确认'
    }
  }
}
</script>

<style lang="scss" scoped>
.onboard-company-card { margin-bottom: 14px; border: 1px solid #f2d39b; }
.company-card-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.company-card-title { display: flex; align-items: center; gap: 10px; }
.company-card-title h2 { margin: 0; color: #7c4a08; font-size: 17px; }
.company-card-header p { margin: 5px 0 0; color: #64748b; font-size: 12px; }
.company-card-actions { display: flex; align-items: center; gap: 8px; }
.company-card-alert { margin-bottom: 12px; }
.work-checkbox-wrap { display: inline-flex; align-items: center; justify-content: center; min-width: 24px; min-height: 24px; }
.source-cell, .employee-cell { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.source-cell strong, .employee-cell strong { color: #334155; }
.source-cell small, .employee-cell small { color: #64748b; font-size: 11px; line-height: 1.4; }
.issue-tags { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 4px; }
.issue-tags .el-tag { max-width: 100%; height: auto; line-height: 1.35; white-space: normal; }
@media (max-width: 760px) {
  .company-card-header { align-items: flex-start; flex-direction: column; }
  .company-card-actions { width: 100%; flex-wrap: wrap; }
}
</style>
