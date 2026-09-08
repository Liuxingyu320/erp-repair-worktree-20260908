<template>
  <div class="app-container hr-import-export">
    <header class="page-heading">
      <h1>人事导入导出</h1>
      <p>统一处理新入职预览导入与历史正式员工档案交换。</p>
    </header>
    <section class="onboarding-import-card">
      <div>
        <h2>入职资料导入</h2>
        <p>先生成脱敏预览，再逐行决定导入、继续、跳过或绑定已有账号。</p>
      </div>
      <el-button
        type="primary"
        icon="el-icon-upload2"
        v-hasPermi="['hr:onboarding:import:preview']"
        @click="importVisible = true"
      >开始预览导入</el-button>
    </section>

    <el-alert
      title="正式员工直接导入是过渡能力，仅用于历史员工档案维护；新入职人员请使用上方预览导入。"
      type="warning"
      :closable="false"
      show-icon
      class="legacy-alert"
    />

    <hr-employee-list
      title="历史正式员工导入导出（过渡能力）"
      subtitle="保留旧模板下载、直接导入与当前筛选导出，不等同于新的入职资料预览导入"
      source="employee"
      mode="importExport"
      show-completeness
      show-import
      show-template
      show-export
    />

    <hr-onboarding-import-dialog :visible.sync="importVisible" />
  </div>
</template>

<script>
import HrEmployeeList from "../components/HrEmployeeList"
import HrOnboardingImportDialog from "../onboarding/components/HrOnboardingImportDialog"

export default {
  name: "HrImportExport",
  components: { HrEmployeeList, HrOnboardingImportDialog },
  data() {
    return { importVisible: false }
  }
}
</script>

<style lang="scss" scoped>
.hr-import-export {
  color: #1e293b;
}

.page-heading {
  margin-bottom: 16px;

  h1 { margin: 0; font-size: 24px; }
  p { margin: 5px 0 0; color: #64748b; }
}

.onboarding-import-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
  padding: 22px 24px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;

  h2 { margin: 0; font-size: 22px; }
  p { margin: 6px 0 0; color: #64748b; }
}

.legacy-alert {
  margin-bottom: 14px;
}
</style>
