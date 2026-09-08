<template>
  <el-drawer
    title="简略资料"
    :visible="visible"
    size="520px"
    append-to-body
    :before-close="close"
  >
    <div class="hr-summary-drawer" v-if="profile">
      <div class="hr-summary-head">
        <div>
          <h3>{{ profile.employeeName || '-' }}</h3>
          <p>{{ profile.employeeNo || '未填工号' }} · {{ profile.positionNames || '未填岗位' }}</p>
        </div>
        <el-tag :type="statusType(profile.onboardingStatus)" size="small">{{ onboardingStatusLabel(profile.onboardingStatus) }}</el-tag>
      </div>

      <el-descriptions :column="2" border size="small" class="hr-summary-section">
        <el-descriptions-item label="手机号">{{ profile.phoneNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人员类别">{{ employeeCategoryLabel(profile.employeeCategory) }}</el-descriptions-item>
        <el-descriptions-item label="所属公司">{{ profile.companyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ profile.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="直属主管">{{ profile.directSupervisor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="员工状态">{{ profile.employeeStatus || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="hr-completeness-block">
        <div class="hr-section-title">资料完整度</div>
        <el-progress :percentage="completeness.profileCompleteness || 0" :status="progressStatus" />
        <div class="hr-missing-list" v-if="missingFields.length">
          <el-tag v-for="field in missingFields" :key="field" type="warning" size="mini">{{ field }}</el-tag>
        </div>
        <el-empty v-else description="必填资料已齐全" :image-size="72" />
      </div>

      <div class="hr-account-strip">
        <span>账号状态</span>
        <strong>{{ summary.accountBound ? '已绑定账号' : '未绑定账号' }}</strong>
      </div>

      <div class="hr-drawer-actions">
        <el-button type="primary" size="small" icon="el-icon-document" @click="$emit('detail', summary)">详细资料</el-button>
        <el-button size="small" icon="el-icon-edit" @click="$emit('edit', summary)">编辑</el-button>
        <el-button size="small" icon="el-icon-warning-outline" @click="$emit('missing', summary)">缺失字段</el-button>
        <el-button size="small" icon="el-icon-user" @click="$emit('account', summary)">账号</el-button>
        <el-button size="small" icon="el-icon-download" @click="$emit('export', summary)">导出</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
export default {
  name: 'HrProfileSummaryDrawer',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    summary: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    profile() {
      return this.summary && this.summary.profile ? this.summary.profile : null
    },
    completeness() {
      return this.summary && this.summary.completeness ? this.summary.completeness : {}
    },
    missingFields() {
      return this.completeness.missingOnboardingFields || this.completeness.missingProfileFields || []
    },
    progressStatus() {
      return this.missingFields.length ? 'warning' : 'success'
    }
  },
  methods: {
    onboardingStatusLabel(status) {
      const labels = { DRAFT: '草稿', READY: '待确认', CONFIRMED: '已入职', CANCELLED: '已取消', PENDING: '待处理' }
      return labels[String(status || '').toUpperCase()] || (/[^\x00-\x7F]/.test(String(status || '')) ? status : '未开始')
    },
    employeeCategoryLabel(value) {
      const labels = { FULL_TIME: '全职', PART_TIME: '兼职', INTERN: '实习生', LABOR_DISPATCH: '劳务派遣', OUTSOURCED: '外包人员' }
      return labels[String(value || '').toUpperCase()] || (/[^\x00-\x7F]/.test(String(value || '')) ? value : '未填写')
    },
    close() {
      this.$emit('update:visible', false)
    },
    statusType(status) {
      if (status === '已入职') return 'success'
      if (status === '取消入职') return 'info'
      if (status === '待补资料') return 'warning'
      return 'primary'
    }
  }
}
</script>

<style scoped>
.hr-summary-drawer {
  padding: 0 24px 24px;
}
.hr-summary-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.hr-summary-head h3 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}
.hr-summary-head p {
  margin: 0;
  color: #606266;
}
.hr-summary-section {
  margin-bottom: 18px;
}
.hr-section-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
}
.hr-completeness-block {
  margin: 18px 0;
}
.hr-missing-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.hr-account-strip {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  margin-bottom: 18px;
  background: #f5f7fa;
  border-radius: 6px;
}
.hr-drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
