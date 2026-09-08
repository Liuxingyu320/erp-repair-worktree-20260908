<template>
  <div class="app-container approval-manage-page">
    <div class="approval-page-header">
      <div>
        <span class="eyebrow">统一审批中心</span>
        <h2>审批管理</h2>
        <p>集中配置审批规则、监控运行实例并检查组织覆盖；员工仍从工作台处理个人待办。</p>
      </div>
      <el-tag type="success" effect="plain">有限配置 · 全程审计</el-tag>
    </div>

    <approval-load-error
      v-if="templateLoadError"
      title="审批模板加载失败"
      :error="templateLoadError"
      :loading="templateLoading"
      class="page-load-error"
      @retry="retryTemplates"
    />

    <el-tabs v-model="activeTab" type="border-card" class="manage-tabs">
      <el-tab-pane name="definition">
        <span slot="label"><i class="el-icon-setting"></i> 流程配置</span>
        <flow-configuration
          v-if="visitedTabs.definition"
          :templates="templates"
          :template-loading="templateLoading"
          :template-load-error="templateLoadError"
          @refresh-templates="loadTemplates"
          @changed="loadTemplates"
        />
      </el-tab-pane>
      <el-tab-pane name="monitor">
        <span slot="label"><i class="el-icon-view"></i> 运行监控</span>
        <runtime-monitor v-if="visitedTabs.monitor" :templates="templates" />
      </el-tab-pane>
      <el-tab-pane name="validation">
        <span slot="label"><i class="el-icon-circle-check"></i> 配置检查</span>
        <validation-panel v-if="visitedTabs.validation" :templates="templates" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { listApprovalTemplates } from '@/api/approval/definition'
import FlowConfiguration from './components/FlowConfiguration'
import RuntimeMonitor from './components/RuntimeMonitor'
import ValidationPanel from './components/ValidationPanel'
import ApprovalLoadError from './components/ApprovalLoadError'
import { formatApprovalLoadError, unwrapRows } from './components/approvalUi'

export default {
  name: 'ApprovalManage',
  components: { ApprovalLoadError, FlowConfiguration, RuntimeMonitor, ValidationPanel },
  data() {
    return {
      activeTab: 'definition',
      visitedTabs: { definition: true, monitor: false, validation: false },
      templates: [],
      templateLoading: false,
      templateLoadError: null
    }
  },
  watch: {
    activeTab(value) {
      if (Object.prototype.hasOwnProperty.call(this.visitedTabs, value)) {
        this.$set(this.visitedTabs, value, true)
      }
    }
  },
  created() { this.loadTemplates() },
  methods: {
    loadTemplates() {
      this.templateLoading = true
      this.templateLoadError = null
      return listApprovalTemplates({ includeDisabled: true }, { silentError: true }).then(response => {
        this.templates = unwrapRows(response).rows
        return response
      }).catch(error => {
        this.templates = []
        this.templateLoadError = formatApprovalLoadError(
          error,
          '审批模板服务暂不可用；其他页签仍可独立检查，服务恢复后可直接重试。'
        )
        return { error }
      }).finally(() => { this.templateLoading = false })
    },
    retryTemplates() {
      return this.loadTemplates()
    }
  }
}
</script>

<style lang="scss" scoped>
.approval-manage-page { min-height: calc(100vh - 84px); background: #f5f7fa; }
.approval-page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 14px; padding: 18px 22px; border: 1px solid #e4e7ed; border-radius: 12px; background: linear-gradient(120deg, #fff, #f0fdfa); }
.approval-page-header h2 { margin: 4px 0 6px; color: #172033; font-size: 24px; }
.approval-page-header p { margin: 0; color: #606266; }
.eyebrow { color: #0f766e; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.page-load-error { margin-bottom: 12px; }
.manage-tabs { border-radius: 12px; overflow: hidden; box-shadow: none; }
.manage-tabs ::v-deep > .el-tabs__header .el-tabs__item { height: 48px; line-height: 48px; }
.manage-tabs ::v-deep > .el-tabs__content { padding: 14px; background: #f7f9fc; }
</style>
