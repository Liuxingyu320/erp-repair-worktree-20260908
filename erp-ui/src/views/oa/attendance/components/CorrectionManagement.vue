<template>
  <section class="attendance-module correction-management">
    <el-card shadow="never" class="oa-filter-card">
      <el-form :inline="true" size="small" @submit.native.prevent>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部状态"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="query.dates" type="daterange" value-format="yyyy-MM-dd" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
        <el-form-item><el-button type="primary" icon="el-icon-search" @click="load">查询</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never" class="oa-table-card table-card">
      <el-table v-loading="loading" :data="rows" size="small" empty-text="暂无补卡申请">
        <el-table-column label="申请人" prop="userName" width="120" />
        <el-table-column label="业务日" prop="businessDate" width="110" />
        <el-table-column label="更正类型" width="130"><template slot-scope="scope">{{ correctionLabel(scope.row.correctionType) }}</template></el-table-column>
        <el-table-column label="目标卡" width="90"><template slot-scope="scope">{{ punchLabel(scope.row.targetPunchType) }}</template></el-table-column>
        <el-table-column label="申请时间" min-width="165"><template slot-scope="scope">{{ dateTime(scope.row.requestedPunchTime) }}</template></el-table-column>
        <el-table-column label="原因" prop="reason" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template slot-scope="scope"><el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" @click="openDetail(scope.row.correctionRequestId)">详情</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="补卡与考勤更正详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请单号">{{ detail.correctionRequestNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.userName }}</el-descriptions-item>
          <el-descriptions-item label="业务日">{{ detail.businessDate }}</el-descriptions-item>
          <el-descriptions-item label="更正类型">{{ correctionLabel(detail.correctionType) }}</el-descriptions-item>
          <el-descriptions-item label="目标卡">{{ punchLabel(detail.targetPunchType) }}</el-descriptions-item>
          <el-descriptions-item label="原事件 ID">{{ detail.originalPunchEventId || '无（缺卡补录）' }}</el-descriptions-item>
          <el-descriptions-item label="原打卡时间">{{ dateTime(detail.originalPunchTime) }}</el-descriptions-item>
          <el-descriptions-item label="申请更正时间" :span="2">{{ dateTime(detail.requestedPunchTime) }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
        </el-descriptions>
        <el-alert title="原打卡事件和证据不会被覆盖；审批通过后由服务端生成更正结果。审批动作在统一待办执行。" type="info" :closable="false" show-icon class="detail-alert" />
      </template>
    </el-dialog>
  </section>
</template>

<script>
import { getAttendanceCorrection, listShopAttendanceCorrections } from '@/api/oa/attendanceV2'
const { dataOf } = require('@/views/mobile/attendance/attendancePunchPolicy')

export default {
  name: 'AttendanceCorrectionManagement',
  props: { shopContext: { type: Object, default: () => ({}) }, businessId: { type: [String, Number], default: '' } },
  data() {
    return {
      loading: false, rows: [], detail: null, detailOpen: false, query: { status: '', dates: [] },
      statusOptions: [
        { value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTING', label: '提交中' }, { value: 'PENDING', label: '审批中' },
        { value: 'APPROVED', label: '已通过' }, { value: 'REJECTED', label: '已驳回' }, { value: 'RETURNED', label: '已退回' }
      ]
    }
  },
  watch: { 'shopContext.deptId'() { this.load() }, businessId(value) { if (value) this.openDetail(value) } },
  created() { this.load(); if (this.businessId) this.openDetail(this.businessId) },
  methods: {
    load() {
      if (!this.shopContext.isStore || !this.shopContext.deptId) { this.rows = []; return Promise.resolve() }
      const params = { shopId: this.shopContext.deptId, status: this.query.status || undefined }
      if (this.query.dates && this.query.dates.length === 2) [params.dateFrom, params.dateTo] = this.query.dates
      this.loading = true
      return listShopAttendanceCorrections(params).then(response => { const payload = dataOf(response); this.rows = Array.isArray(payload) ? payload : [] })
        .catch(error => { this.rows = []; this.$modal.msgError(error.message || '补卡申请加载失败') })
        .finally(() => { this.loading = false })
    },
    openDetail(id) { if (!id) return; return getAttendanceCorrection(id).then(response => { this.detail = dataOf(response); this.detailOpen = true }).catch(error => { this.$modal.msgError(error.message || '补卡详情加载失败') }) },
    correctionLabel(value) { return { MISSING_PUNCH: '缺卡补录', WRONG_TIME: '时间更正', WRONG_TYPE: '打卡类型更正', OTHER: '其他更正' }[value] || value || '-' },
    punchLabel(value) { return String(value || '').toUpperCase() === 'OUT' ? '下班卡' : '上班卡' },
    statusLabel(value) { return { DRAFT: '草稿', SUBMITTING: '提交中', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', RETURNED: '已退回', CANCELLED: '已撤回' }[String(value || '').toUpperCase()] || '未知' },
    statusTag(value) { const key = String(value || '').toUpperCase(); return key === 'APPROVED' ? 'success' : ['REJECTED', 'RETURNED'].includes(key) ? 'danger' : ['PENDING', 'SUBMITTING'].includes(key) ? 'warning' : 'info' },
    dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
  }
}
</script>

<style scoped>
.table-card { margin-top: 16px; }.detail-alert { margin-top: 16px; }
</style>
