<template>
  <el-drawer
    title="云盘设置"
    :visible="visible"
    size="92%"
    direction="rtl"
    append-to-body
    :wrapper-closable="!saving"
    :before-close="close"
    custom-class="drive-quota-drawer"
  >
    <div class="quota-center">
      <header class="quota-center__header">
        <div><span>容量与策略</span><h2>云盘额度管理中心</h2><p>统一管理物理容量、组织盘预算、岗位额度和个人例外。</p></div>
        <el-button icon="el-icon-refresh" :loading="loading" @click="loadAll">刷新数据</el-button>
      </header>
      <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="quota-center__error" />
      <el-alert v-if="status && !status.quotaPolicyEnabled" type="warning" title="岗位/个人额度运行开关尚未开启；可先完成配置和影响预览，灰度发布时再开启。" show-icon :closable="false" class="quota-center__error" />
      <el-alert v-if="status && !status.organizationSyncEnabled" type="warning" title="组织自动建盘运行开关尚未开启；自动规则会保存，但不会批量建盘。" show-icon :closable="false" class="quota-center__error" />
      <el-alert v-if="status && !status.capacityReservationEnabled" type="warning" title="上传容量预占尚未开启；当前不能安全启用全局容量强制拦截。" show-icon :closable="false" class="quota-center__error" />
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="容量总览" name="capacity">
          <DriveCapacityPanel :key="'capacity-' + refreshKey" :value="capacity" :reservation-enabled="Boolean(status && status.capacityReservationEnabled)" :saving="previewing || saving" @save-request="previewSave" />
        </el-tab-pane>
        <el-tab-pane label="组织盘" name="organizations">
          <DriveOrganizationQuotaPanel :key="'org-' + refreshKey" :rows="organizations" :rules="organizationRules" :sync-enabled="Boolean(status && status.organizationSyncEnabled)" :loading="loading" :saving="previewing || saving" :reconciling="reconciling" @save-request="previewSave" @reconcile="reconcile" />
        </el-tab-pane>
        <el-tab-pane label="岗位额度" name="posts">
          <DrivePostQuotaPanel :key="'post-' + refreshKey" :policies="policies" :post-options="postOptions" :loading="loading" :saving="previewing || saving" @save-request="previewSave" />
        </el-tab-pane>
        <el-tab-pane label="个人例外" name="users">
          <DriveUserQuotaPanel :key="'user-' + refreshKey" :users="users" :policies="policies" :total="userTotal" :loading="userLoading" :saving="previewing || saving" @search="loadUsers" @save-request="previewSave" />
        </el-tab-pane>
      </el-tabs>
    </div>
    <DriveQuotaImpactDialog :visible="impactVisible" :impact="impact" :saving="saving" @cancel="cancelImpact" @confirm="confirmSave" />
  </el-drawer>
</template>

<script>
import {
  deleteDrivePersonalPolicy,
  getDriveAdminStatus,
  getDriveCapacity,
  listDriveOrganizations,
  listDriveOrganizationTypeRules,
  listDrivePersonalPolicies,
  listDriveQuotaPosts,
  listDriveQuotaUsers,
  previewDriveQuotaImpact,
  reconcileDriveOrganizations,
  saveDriveOrganization,
  saveDriveOrganizationsBatch,
  saveDriveOrganizationTypeRule,
  saveDrivePersonalPolicy,
  updateDriveCapacity
} from '@/api/drive/admin'
import DriveCapacityPanel from './DriveCapacityPanel.vue'
import DriveOrganizationQuotaPanel from './DriveOrganizationQuotaPanel.vue'
import DrivePostQuotaPanel from './DrivePostQuotaPanel.vue'
import DriveQuotaImpactDialog from './DriveQuotaImpactDialog.vue'
import DriveUserQuotaPanel from './DriveUserQuotaPanel.vue'

const { driveErrorMessage, parseDriveBlobError } = require('../../driveState')

export default {
  name: 'DriveQuotaCenterDrawer',
  components: { DriveCapacityPanel, DriveOrganizationQuotaPanel, DrivePostQuotaPanel, DriveQuotaImpactDialog, DriveUserQuotaPanel },
  props: { visible: { type: Boolean, default: false } },
  data() {
    return {
      activeTab: 'capacity', loading: false, userLoading: false, previewing: false,
      saving: false, reconciling: false, error: '', status: null, capacity: null, organizations: [],
      organizationRules: [], policies: [], postOptions: [], users: [], userTotal: 0, impactVisible: false, impact: null,
      pending: null, refreshKey: 0
    }
  },
  watch: { visible(value) { if (value) this.loadAll() } },
  methods: {
    close(done) {
      if (this.saving) return
      this.cancelImpact()
      this.$emit('update:visible', false)
      if (typeof done === 'function') done()
    },
    async loadAll() {
      this.loading = true
      this.error = ''
      try {
        const [status, capacity, policies, posts, organizations, organizationRules] = await Promise.all([
          getDriveAdminStatus(), getDriveCapacity(), listDrivePersonalPolicies(), listDriveQuotaPosts(), listDriveOrganizations(), listDriveOrganizationTypeRules()
        ])
        this.status = status.data || null
        this.capacity = capacity.data || null
        this.policies = Array.isArray(policies.data) ? policies.data : []
        this.postOptions = Array.isArray(posts.data) ? posts.data : []
        this.organizations = Array.isArray(organizations.data) ? organizations.data : []
        this.organizationRules = Array.isArray(organizationRules.data) ? organizationRules.data : []
        await this.loadUsers({ pageNum: 1, pageSize: 100 })
      } catch (error) {
        await this.showError(error)
      } finally {
        this.loading = false
      }
    },
    async loadUsers(query) {
      this.userLoading = true
      try {
        const response = await listDriveQuotaUsers(query || { pageNum: 1, pageSize: 100 })
        this.users = Array.isArray(response.data) ? response.data : []
        this.userTotal = Number(response.total || this.users.length)
      } catch (error) {
        await this.showError(error)
      } finally {
        this.userLoading = false
      }
    },
    async previewSave(operation) {
      if (!operation || !operation.impact) return
      this.previewing = true
      this.error = ''
      try {
        const response = await previewDriveQuotaImpact(operation.impact)
        this.pending = operation
        this.impact = response.data || null
        this.impactVisible = true
      } catch (error) {
        await this.showError(error)
      } finally {
        this.previewing = false
      }
    },
    cancelImpact() {
      if (this.saving) return
      this.impactVisible = false
      this.impact = null
      this.pending = null
    },
    async confirmSave() {
      if (!this.pending || !this.impact) return
      this.saving = true
      try {
        const data = { ...this.pending.data, impactHash: this.impact.impactHash }
        if (this.pending.kind === 'capacity') await updateDriveCapacity(data)
        else if (this.pending.kind === 'organization') await saveDriveOrganization(this.pending.deptId, data)
        else if (this.pending.kind === 'organization-batch') await saveDriveOrganizationsBatch(data)
        else if (this.pending.kind === 'organization-rule') {
          await saveDriveOrganizationTypeRule(this.pending.deptType, data)
          if (data.autoEnable && data.status === 'ACTIVE' && this.status && this.status.organizationSyncEnabled) await reconcileDriveOrganizations()
        }
        else if (this.pending.kind === 'policy') await saveDrivePersonalPolicy(this.pending.subjectType, this.pending.subjectId, data)
        else if (this.pending.kind === 'policy-delete') await deleteDrivePersonalPolicy(this.pending.subjectType, this.pending.subjectId, data)
        this.$message.success('云盘额度配置已保存')
        this.impactVisible = false
        this.impact = null
        this.pending = null
        this.refreshKey += 1
        await this.loadAll()
        this.$emit('changed')
      } catch (error) {
        await this.showError(error)
      } finally {
        this.saving = false
      }
    },
    async reconcile() {
      if (!this.status || !this.status.organizationSyncEnabled) { this.$message.warning('请先在运行环境开启组织盘同步开关'); return }
      this.reconciling = true
      try {
        const response = await reconcileDriveOrganizations()
        const result = response.data || {}
        const budgetBlocked = Number(result.budgetBlockedCount || 0)
        const hierarchyInvalid = Array.isArray(result.budgetViolations) && result.budgetViolations.some(item => item && item.hierarchyInvalid)
        if (result.failed || budgetBlocked) {
          const details = []
          if (result.failed) details.push(`${result.failed} 个同步失败`)
          if (budgetBlocked) details.push(hierarchyInvalid ? `${budgetBlocked} 个因组织层级异常暂时只读` : `${budgetBlocked} 个因上级预算不足暂时只读`)
          this.$message.warning(`已同步 ${result.succeeded || 0} 个组织；${details.join('，')}`)
        } else this.$message.success(`已同步 ${result.succeeded || 0} 个组织，未发现预算问题`)
        await this.loadAll()
        this.$emit('changed')
      } catch (error) {
        await this.showError(error)
      } finally {
        this.reconciling = false
      }
    },
    async showError(error) {
      const parsed = await parseDriveBlobError(error)
      this.error = driveErrorMessage(parsed.code, parsed.message || '云盘额度配置暂不可用')
      this.$message.error(this.error)
    }
  }
}
</script>

<style lang="scss">
.drive-quota-drawer .el-drawer__header { margin-bottom: 0; padding: 18px 24px; border-bottom: 1px solid #edf0f5; color: #243447; }
.drive-quota-drawer .el-drawer__body { overflow: auto; }
.quota-center { min-width: 760px; padding: 22px 24px 40px; }
.quota-center__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.quota-center__header span { color: #3e82d7; font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.quota-center__header h2 { margin: 5px 0; color: #1f2d3d; }
.quota-center__header p { margin: 0; color: #8795a8; }
.quota-center__error { margin-bottom: 12px; }
@media (max-width: 900px) { .drive-quota-drawer { width: 100% !important; } .quota-center { min-width: 720px; } }
</style>
