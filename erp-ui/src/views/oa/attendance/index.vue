<template>
  <div class="app-container oa-workspace-page oa-attendance-page">
    <section class="oa-page-hero attendance-hero">
      <div class="oa-hero__copy">
        <span class="oa-hero__eyebrow">OA 协同 · 考勤 V2</span>
        <div class="oa-hero__title-row">
          <span class="oa-hero__icon"><i class="el-icon-date" /></span>
          <div>
            <h1>考勤中心</h1>
            <p>先定义班次和现场围栏，再给员工发布排班；无已发布排班时无法打卡。</p>
          </div>
        </div>
      </div>
      <div class="oa-hero__aside">
        <span>当前业务组织</span>
        <strong>{{ contextLabel }}</strong>
        <el-tag :type="shopContext.isStore ? 'success' : 'warning'" size="mini" effect="dark">
          {{ shopContext.isStore ? '可进行门店排班' : '排班前需切换门店' }}
        </el-tag>
      </div>
    </section>

    <el-alert
      title="旧网页按钮打卡已下线"
      description="新考勤只接受已发布排班下的现场定位拍照。管理端不再提供 WEB 上下班按钮。"
      type="info"
      show-icon
      :closable="false"
      class="attendance-migration-alert"
    />

    <el-card shadow="never" class="attendance-center-card">
      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane v-if="can('oa:attendance:shift:list')" label="班次管理" name="shift">
          <shift-management />
        </el-tab-pane>
        <el-tab-pane v-if="can('oa:attendance:site:list')" label="考勤地点" name="site">
          <attendance-site-management :shop-context="shopContext" />
        </el-tab-pane>
        <el-tab-pane v-if="can('oa:attendance:schedule:list') && can('oa:attendance:site:list')" label="周排班" name="schedule">
          <weekly-schedule :shop-context="shopContext" />
        </el-tab-pane>
        <el-tab-pane v-if="can('oa:attendance:day:list')" label="每日结果" name="day">
          <day-result-management :shop-context="shopContext" />
        </el-tab-pane>
        <el-tab-pane v-if="canRequestReview" label="请假与补卡" name="requests">
          <el-tabs v-if="!requestMode" v-model="requestSubTab">
            <el-tab-pane v-if="can('oa:attendance:leave:list') || can('oa:attendance:leave:type:list')" label="请假" name="leave">
              <leave-management :shop-context="shopContext" />
            </el-tab-pane>
            <el-tab-pane v-if="can('oa:attendance:correction:list')" label="补卡" name="correction">
              <correction-management :shop-context="shopContext" />
            </el-tab-pane>
          </el-tabs>
          <leave-management
            v-else-if="requestMode === 'leave'"
            :shop-context="shopContext"
            :business-id="todoBusinessId"
          />
          <correction-management v-else :shop-context="shopContext" :business-id="todoBusinessId" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import { getSelectedDeptContext } from '@/utils/shopContext'
import AttendanceSiteManagement from './components/AttendanceSiteManagement.vue'
import CorrectionManagement from './components/CorrectionManagement.vue'
import DayResultManagement from './components/DayResultManagement.vue'
import LeaveManagement from './components/LeaveManagement.vue'
import ShiftManagement from './components/ShiftManagement.vue'
import WeeklySchedule from './components/WeeklySchedule.vue'

const requestTodoTypes = Object.freeze({
  OA_ATTENDANCE_LEAVE_APPROVAL: 'leave',
  OA_ATTENDANCE_CORRECTION_APPROVAL: 'correction'
})

export default {
  name: 'OaAttendanceV2',
  components: { AttendanceSiteManagement, CorrectionManagement, DayResultManagement, LeaveManagement, ShiftManagement, WeeklySchedule },
  data() {
    return {
      activeTab: 'shift',
      shopContext: getSelectedDeptContext() || {},
      requestMode: '',
      requestSubTab: 'leave',
      deptChangeHandler: null
    }
  },
  computed: {
    contextLabel() {
      const context = this.shopContext || {}
      if (context.deptName) return context.deptName
      return context.isStore ? '当前门店' : '未选择门店'
    },
    canRequestReview() {
      return this.can('oa:attendance:leave:list') || this.can('oa:attendance:leave:type:list') || this.can('oa:attendance:leave:approve') ||
        this.can('oa:attendance:correction:list') || this.can('oa:attendance:correction:approve') ||
        Boolean(this.requestMode)
    },
    todoBusinessId() {
      return this.$route && this.$route.query ? this.$route.query.businessId : ''
    },
  },
  watch: {
    '$route.query': {
      deep: true,
      handler() {
        this.applyRouteFocus()
      }
    }
  },
  created() {
    this.applyRouteFocus()
    if (!this.can('oa:attendance:leave:list') && !this.can('oa:attendance:leave:type:list') && this.can('oa:attendance:correction:list')) {
      this.requestSubTab = 'correction'
    }
    this.deptChangeHandler = () => {
      this.shopContext = getSelectedDeptContext() || {}
    }
    if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this.deptChangeHandler)
  },
  beforeDestroy() {
    if (typeof window !== 'undefined' && this.deptChangeHandler) window.removeEventListener('erp:dept-changed', this.deptChangeHandler)
  },
  methods: {
    can(permission) {
      return !this.$auth || typeof this.$auth.hasPermi !== 'function' || this.$auth.hasPermi(permission)
    },
    applyRouteFocus() {
      const query = this.$route && this.$route.query ? this.$route.query : {}
      const todoMode = requestTodoTypes[String(query.todoType || '')]
      const requested = String(query.tab || todoMode || '')
      this.requestMode = ''
      if (requested === 'leave' || requested === 'correction') {
        this.requestMode = requested
        this.activeTab = 'requests'
        return
      }
      if (['shift', 'site', 'schedule', 'day', 'requests'].indexOf(requested) > -1) this.activeTab = requested
    },
    handleTabClick(tab) {
      if (!this.$router || !this.$route || tab.name === 'requests') return
      const query = Object.assign({}, this.$route.query || {}, { tab: tab.name })
      delete query.todoType
      delete query.businessId
      this.$router.replace({ path: this.$route.path, query }).catch(() => undefined)
    }
  }
}
</script>

<style scoped>
.attendance-migration-alert { margin: 16px 0; }
.attendance-center-card { border-radius: 14px; }
</style>
