<template>
  <section aria-label="组织盘额度">
    <div class="rule-grid" aria-label="组织类型自动建盘规则">
      <article v-for="rule in rules" :key="rule.deptType" class="rule-card">
        <div><strong>{{ typeLabel(rule.deptType) }}</strong><el-tag size="mini" :type="rule.autoEnable && rule.status === 'ACTIVE' ? 'success' : 'info'">{{ rule.autoEnable && rule.status === 'ACTIVE' ? '自动建盘' : '仅手动' }}</el-tag></div>
        <p>新组织默认 {{ formatBytes(rule.defaultQuotaBytes) }}；{{ rule.requireActiveMember ? '需有直属成员' : '无成员也创建' }}</p>
        <el-button type="text" @click="openRule(rule)">配置规则</el-button>
      </article>
    </div>
    <el-alert title="自动规则只作用于还没有独立配置的组织；已配置组织不会被批量覆盖。" type="info" :closable="false" show-icon class="rule-note" />
    <el-alert
      v-if="budgetBlockedCount"
      :title="budgetBlockedTitle"
      type="warning"
      :closable="false"
      show-icon
      class="rule-note"
    />
    <div class="panel-toolbar">
      <div class="panel-toolbar__filters">
        <el-input v-model.trim="keyword" clearable prefix-icon="el-icon-search" placeholder="搜索组织名称" />
        <el-select v-model="typeFilter" clearable placeholder="全部类型"><el-option v-for="type in ['GROUP','COMPANY','STORE','WAREHOUSE']" :key="type" :label="typeLabel(type)" :value="type" /></el-select>
      </div>
      <div><el-button :disabled="!selectedRows.length" @click="openBatch">批量配置（{{ selectedRows.length }}）</el-button><el-button icon="el-icon-refresh" :disabled="!syncEnabled" :loading="reconciling" :title="syncEnabled ? '' : '运行开关开启后可立即同步'" @click="$emit('reconcile')">立即同步组织</el-button></div>
    </div>
    <el-table ref="table" :data="filteredRows" v-loading="loading" row-key="deptId" default-expand-all :tree-props="{ children: 'children' }" max-height="560" @selection-change="selectedRows=$event">
      <el-table-column type="selection" width="44" :reserve-selection="true" :selectable="canSelect" />
      <el-table-column label="组织" min-width="180">
        <template slot-scope="scope">
          <strong>{{ scope.row.deptName }}</strong><br><small>{{ typeLabel(scope.row.deptType) }}</small>
          <small v-if="scope.row.budgetBlocked" class="budget-block">{{ budgetSummary(scope.row) }}</small>
        </template>
      </el-table-column>
      <el-table-column label="直属成员" prop="activeMemberCount" width="90" />
      <el-table-column label="状态" width="140">
        <template slot-scope="scope"><el-tag :type="scope.row.budgetBlocked ? 'warning' : (scope.row.canManage === false ? 'danger' : (scope.row.enabled ? 'success' : 'info'))">{{ organizationState(scope.row) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="组织盘额度" min-width="130"><template slot-scope="scope">{{ formatBytes(scope.row.quotaBytes) }}</template></el-table-column>
      <el-table-column label="额度来源" min-width="115"><template slot-scope="scope">{{ sourceLabel(scope.row.configSource) }}</template></el-table-column>
      <el-table-column label="已使用" min-width="130">
        <template slot-scope="scope"><span :class="{ danger: scope.row.overQuota }">{{ formatBytes(scope.row.usedBytes) }}</span></template>
      </el-table-column>
      <el-table-column label="组织树预算" min-width="130"><template slot-scope="scope">{{ scope.row.treeBudgetBytes == null ? '未设置' : formatBytes(scope.row.treeBudgetBytes) }}</template></el-table-column>
      <el-table-column label="写入模式" min-width="140"><template slot-scope="scope">{{ writeModeLabel(scope.row.memberWriteMode) }}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right"><template slot-scope="scope"><el-button type="text" :disabled="scope.row.canManage === false" @click="open(scope.row)">配置</el-button></template></el-table-column>
    </el-table>

    <el-dialog title="配置组织盘" :visible.sync="dialogVisible" width="600px" append-to-body :close-on-click-modal="false">
      <el-form v-if="selected" label-width="130px" @submit.native.prevent>
        <el-form-item label="组织"><strong>{{ selected.deptName }}</strong>（{{ typeLabel(selected.deptType) }}）</el-form-item>
        <el-alert v-if="selected.budgetBlocked" :title="budgetDetail(selected)" type="warning" :closable="false" show-icon class="dialog-alert" />
        <el-form-item label="启用组织盘"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="组织盘额度（GiB）"><el-input v-model.trim="form.quotaGiB" /></el-form-item>
        <el-form-item label="组织树预算（GiB）"><el-input v-model.trim="form.treeBudgetGiB" placeholder="不限制时留空" /></el-form-item>
        <el-form-item label="成员写入模式">
          <el-select v-model="form.memberWriteMode" style="width:100%">
            <el-option label="仅有组织盘管理权限的人可写" value="PERMISSION_ONLY" />
            <el-option label="该组织直属成员均可写" value="ALL_DIRECT_MEMBERS" />
            <el-option label="所有普通请求只读" value="READ_ONLY" />
          </el-select>
          <p v-if="form.memberWriteMode === 'ALL_DIRECT_MEMBERS'" class="form-help">保存后预计 {{ selected.activeMemberCount || 0 }} 名直属成员获得写权限。</p>
        </el-form-item>
        <el-form-item label="生命周期">
          <el-radio-group v-model="form.lifecycleStatus">
            <el-radio label="ACTIVE">正常</el-radio><el-radio label="READ_ONLY">只读</el-radio><el-radio label="ARCHIVED">归档</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert v-if="lowerThanUsed" type="warning" :closable="false" show-icon title="新额度低于当前用量；不会删除文件，但会停止新增，清理到额度内后自动恢复。" />
        <el-form-item label="调整原因" class="reason-field"><el-input v-model.trim="form.reason" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">预览影响</el-button></div>
    </el-dialog>

    <el-dialog title="配置自动建盘规则" :visible.sync="ruleDialogVisible" width="560px" append-to-body :close-on-click-modal="false">
      <el-form v-if="selectedRule" label-width="150px" @submit.native.prevent>
        <el-form-item label="组织类型"><strong>{{ typeLabel(selectedRule.deptType) }}</strong></el-form-item>
        <el-form-item label="自动建盘"><el-switch v-model="ruleForm.autoEnable" /></el-form-item>
        <el-form-item label="要求直属成员"><el-switch v-model="ruleForm.requireActiveMember" /></el-form-item>
        <el-form-item label="新组织默认额度"><el-input v-model.trim="ruleForm.quotaGiB"><template slot="append">GiB</template></el-input></el-form-item>
        <el-form-item label="规则状态"><el-radio-group v-model="ruleForm.status"><el-radio label="ACTIVE">启用</el-radio><el-radio label="DISABLED">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="调整原因"><el-input v-model.trim="ruleForm.reason" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="ruleDialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRule">预览影响</el-button></div>
    </el-dialog>

    <el-dialog title="批量配置组织盘" :visible.sync="batchDialogVisible" width="580px" append-to-body :close-on-click-modal="false">
      <el-alert :title="`已选择 ${selectedRows.length} 个组织；每个组织现有的组织树预算保持不变。`" type="info" :closable="false" show-icon />
      <el-form label-width="140px" class="batch-form" @submit.native.prevent>
        <el-form-item label="启用组织盘"><el-switch v-model="batchForm.enabled" /></el-form-item>
        <el-form-item label="统一额度（GiB）"><el-input v-model.trim="batchForm.quotaGiB" /></el-form-item>
        <el-form-item label="快速档位"><el-button v-for="level in [5, 10, 20, 40]" :key="level" size="mini" @click="batchForm.quotaGiB=String(level)">{{ level }} GiB</el-button></el-form-item>
        <el-form-item label="成员写入模式"><el-select v-model="batchForm.memberWriteMode" style="width:100%"><el-option label="仅有组织盘管理权限的人可写" value="PERMISSION_ONLY" /><el-option label="各组织直属成员均可写" value="ALL_DIRECT_MEMBERS" /><el-option label="所有普通请求只读" value="READ_ONLY" /></el-select></el-form-item>
        <el-form-item label="生命周期"><el-radio-group v-model="batchForm.lifecycleStatus"><el-radio label="ACTIVE">正常</el-radio><el-radio label="READ_ONLY">只读</el-radio><el-radio label="ARCHIVED">归档</el-radio></el-radio-group></el-form-item>
        <el-alert v-if="batchOverCount" :title="`新额度会使 ${batchOverCount} 个组织盘超额；不删文件，但会停止新增。`" type="warning" :closable="false" show-icon />
        <el-form-item label="调整原因" class="reason-field"><el-input v-model.trim="batchForm.reason" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="batchDialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitBatch">预览影响</el-button></div>
    </el-dialog>
  </section>
</template>

<script>
const { formatBytes } = require('../../driveState')
const { bytesToGiB, gibToBytes } = require('../../quotaState')

export default {
  name: 'DriveOrganizationQuotaPanel',
  props: {
    rows: { type: Array, default: () => [] }, loading: { type: Boolean, default: false },
    rules: { type: Array, default: () => [] }, syncEnabled: { type: Boolean, default: false }, saving: { type: Boolean, default: false }, reconciling: { type: Boolean, default: false }
  },
  data() { return { keyword: '', typeFilter: '', selectedRows: [], dialogVisible: false, selected: null, form: {}, ruleDialogVisible: false, selectedRule: null, ruleForm: {}, batchDialogVisible: false, batchForm: {} } },
  computed: {
    filteredRows() {
      const keyword = this.keyword.toLowerCase()
      const matches = this.rows.filter(row => (!this.typeFilter || row.deptType === this.typeFilter) && (!keyword || String(row.deptName || '').toLowerCase().includes(keyword)))
      if (this.typeFilter || keyword) return matches.map(row => ({ ...row }))
      const index = new Map(this.rows.map(row => [Number(row.deptId), { ...row, children: [] }]))
      const roots = []
      index.forEach(row => {
        const parent = index.get(Number(row.parentId))
        if (parent && Number(row.parentId) !== Number(row.deptId)) parent.children.push(row)
        else roots.push(row)
      })
      return roots
    },
    lowerThanUsed() {
      const bytes = gibToBytes(this.form.quotaGiB, false)
      return bytes != null && this.selected && bytes < Number(this.selected.usedBytes || 0)
    },
    batchOverCount() {
      const bytes = gibToBytes(this.batchForm.quotaGiB, false)
      return bytes == null ? 0 : this.selectedRows.filter(row => Number(row.usedBytes || 0) > bytes).length
    },
    budgetBlockedCount() {
      return this.rows.filter(row => row && row.budgetBlocked).length
    },
    budgetBlockedTitle() {
      const hierarchyInvalid = this.rows.some(row => row && row.budgetViolation && row.budgetViolation.hierarchyInvalid)
      return hierarchyInvalid
        ? `检测到组织目录层级异常，${this.budgetBlockedCount} 个组织盘已安全切换为只读；文件仍可下载和清理，修复组织层级后自动恢复。`
        : `当前有 ${this.budgetBlockedCount} 个组织盘因上级组织树预算不足暂时只读；文件可继续下载和清理，修复预算后自动恢复写入。`
    }
  },
  methods: {
    formatBytes,
    typeLabel(value) { return { GROUP: '集团', COMPANY: '公司', STORE: '门店', WAREHOUSE: '仓库' }[value] || '其他组织' },
    lifecycleLabel(value) { return { ACTIVE: '正常', READ_ONLY: '只读', ARCHIVED: '已归档' }[value] || '未知状态' },
    organizationState(row) { if (row.delFlag !== '0') return '组织已删除'; if (row.directoryStatus !== '0') return '组织已停用'; if (row.budgetBlocked) return row.budgetViolation && row.budgetViolation.hierarchyInvalid ? '层级异常·只读' : '预算不足·只读'; return row.enabled ? this.lifecycleLabel(row.lifecycleStatus) : '未启用' },
    budgetSummary(row) {
      const violation = row && row.budgetViolation
      if (violation && violation.hierarchyInvalid) return violation.violationMessage || '组织目录层级异常'
      return violation ? `上级 ${violation.budgetDeptName || violation.budgetDeptId} 超出 ${formatBytes(violation.exceededBytes)}` : '上级预算不足'
    },
    budgetDetail(row) {
      const violation = row && row.budgetViolation
      if (!violation) return '上级组织树预算不足，当前暂时只读。'
      if (violation.hierarchyInvalid) return violation.violationMessage || '组织目录层级异常，为防止额度越界，组织盘暂时只读。'
      return `${violation.budgetDeptName || violation.budgetDeptId} 预算 ${formatBytes(violation.budgetBytes)}，已分配 ${formatBytes(violation.allocatedBytes)}，超出 ${formatBytes(violation.exceededBytes)}。请上调预算或降低下级额度。`
    },
    writeModeLabel(value) { return { PERMISSION_ONLY: '权限人员可写', ALL_DIRECT_MEMBERS: '直属成员可写', READ_ONLY: '只读' }[value] || '未知写入模式' },
    sourceLabel(value) { return { TYPE_DEFAULT: '类型默认', MANUAL: '单独配置', MIGRATED: '原配置迁移' }[value] || '待配置' },
    canSelect(row) { return row && row.canManage !== false },
    open(row) {
      this.selected = row
      this.form = {
        enabled: Boolean(row.enabled), quotaGiB: String(bytesToGiB(row.quotaBytes)),
        treeBudgetGiB: row.treeBudgetBytes == null ? '' : String(bytesToGiB(row.treeBudgetBytes)),
        memberWriteMode: row.memberWriteMode || 'PERMISSION_ONLY',
        lifecycleStatus: row.lifecycleStatus === 'DISABLED' ? 'ACTIVE' : (row.lifecycleStatus || 'ACTIVE'),
        reason: ''
      }
      this.dialogVisible = true
    },
    openRule(rule) {
      this.selectedRule = rule
      this.ruleForm = { autoEnable: Boolean(rule.autoEnable), requireActiveMember: Boolean(rule.requireActiveMember), quotaGiB: String(bytesToGiB(rule.defaultQuotaBytes)), status: rule.status || 'ACTIVE', reason: '' }
      this.ruleDialogVisible = true
    },
    openBatch() {
      if (!this.selectedRows.length) return
      if (this.selectedRows.length > 200) { this.$message.error('一次最多批量配置 200 个组织'); return }
      this.batchForm = { enabled: true, quotaGiB: '10', memberWriteMode: 'PERMISSION_ONLY', lifecycleStatus: 'ACTIVE', reason: '' }
      this.batchDialogVisible = true
    },
    submitBatch() {
      const quotaBytes = gibToBytes(this.batchForm.quotaGiB, false)
      if (quotaBytes == null || !this.selectedRows.length) { this.$message.error('请选择组织并输入有效额度'); return }
      if (!this.batchForm.reason) { this.$message.error('请填写调整原因'); return }
      const targets = this.selectedRows.map(row => ({ deptId: row.deptId, version: Number(row.version || 0) }))
      const data = { targets, enabled: this.batchForm.enabled, quotaBytes, memberWriteMode: this.batchForm.memberWriteMode, lifecycleStatus: this.batchForm.lifecycleStatus, reason: this.batchForm.reason }
      this.$emit('save-request', { kind: 'organization-batch', data, impact: { changeType: 'ORGANIZATION_BATCH', targets, enabled: data.enabled, quotaBytes: data.quotaBytes, memberWriteMode: data.memberWriteMode, lifecycleStatus: data.lifecycleStatus } })
    },
    submitRule() {
      const defaultQuotaBytes = gibToBytes(this.ruleForm.quotaGiB, false)
      if (defaultQuotaBytes == null) { this.$message.error('请输入有效 GiB 数值，最多三位小数'); return }
      if (!this.ruleForm.reason) { this.$message.error('请填写调整原因'); return }
      const data = { autoEnable: this.ruleForm.autoEnable, requireActiveMember: this.ruleForm.requireActiveMember, defaultQuotaBytes, status: this.ruleForm.status, version: Number(this.selectedRule.version || 0), reason: this.ruleForm.reason }
      this.$emit('save-request', { kind: 'organization-rule', deptType: this.selectedRule.deptType, data, impact: { changeType: 'ORGANIZATION_TYPE_RULE', subjectType: this.selectedRule.deptType, enabled: data.autoEnable, requireActiveMember: data.requireActiveMember, quotaBytes: data.defaultQuotaBytes, ruleStatus: data.status, version: data.version } })
    },
    submit() {
      const quotaBytes = gibToBytes(this.form.quotaGiB, false)
      const treeBudgetBytes = this.form.treeBudgetGiB === '' ? null : gibToBytes(this.form.treeBudgetGiB, true)
      if (quotaBytes == null || (this.form.treeBudgetGiB !== '' && treeBudgetBytes == null)) { this.$message.error('请输入有效 GiB 数值，最多三位小数'); return }
      if (!this.form.reason) { this.$message.error('请填写调整原因'); return }
      const data = {
        enabled: this.form.enabled, quotaBytes, treeBudgetBytes,
        memberWriteMode: this.form.memberWriteMode, lifecycleStatus: this.form.lifecycleStatus,
        version: Number(this.selected.version || 0), reason: this.form.reason
      }
      this.$emit('save-request', {
        kind: 'organization', deptId: this.selected.deptId, data,
        impact: { changeType: 'ORGANIZATION', subjectId: this.selected.deptId, ...data }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.panel-toolbar { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-toolbar__filters { display: flex; gap: 8px; flex: 1; }
.panel-toolbar__filters .el-select { width: 150px; }
.rule-grid { display: grid; grid-template-columns: repeat(4, minmax(160px, 1fr)); gap: 10px; margin-bottom: 10px; }
.rule-card { padding: 13px; border: 1px solid #e6ebf2; border-radius: 10px; background: #fafcff; }
.rule-card > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rule-card p { min-height: 34px; margin: 9px 0 3px; color: #718096; font-size: 12px; line-height: 1.45; }
.rule-note { margin-bottom: 14px; }
.panel-toolbar .el-input { max-width: 360px; }
.batch-form { margin-top: 18px; }
small, .form-help { color: #8795a8; }
.budget-block { display: block; margin-top: 4px; color: #b87918; line-height: 1.4; }
.dialog-alert { margin-bottom: 16px; }
.form-help { margin: 6px 0 0; font-size: 12px; }
.danger { color: #d93025; font-weight: 600; }
.reason-field { margin-top: 18px; }
@media (max-width: 1100px) { .rule-grid { grid-template-columns: repeat(2, minmax(220px, 1fr)); } }
</style>
