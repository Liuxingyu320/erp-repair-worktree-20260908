<template>
  <section class="attendance-module leave-management">
    <el-tabs v-model="innerTab" type="card">
      <el-tab-pane v-if="can('oa:attendance:leave:list')" label="请假申请" name="requests">
        <el-card shadow="never" class="oa-filter-card">
          <el-form :inline="true" size="small" @submit.native.prevent>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部状态">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="query.dates" type="daterange" value-format="yyyy-MM-dd" start-placeholder="开始日期" end-placeholder="结束日期" />
            </el-form-item>
            <el-form-item><el-button type="primary" icon="el-icon-search" @click="loadRequests">查询</el-button></el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="oa-table-card table-card">
          <el-table v-loading="requestLoading" :data="requests" size="small" empty-text="暂无请假申请">
            <el-table-column label="申请人" prop="userName" width="120" />
            <el-table-column label="请假类型" prop="leaveTypeName" width="120" />
            <el-table-column label="请假时间" min-width="260">
              <template slot-scope="scope">{{ dateTime(scope.row.startTime) }} 至 {{ dateTime(scope.row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="时长" width="100"><template slot-scope="scope">{{ minutesText(scope.row.totalMinutes) }}</template></el-table-column>
            <el-table-column label="原因" prop="reason" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template slot-scope="scope"><el-tag :type="statusTag(scope.row.status)" size="mini">{{ statusLabel(scope.row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template slot-scope="scope"><el-button type="text" size="mini" @click="openDetail(scope.row.leaveRequestId)">详情</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="can('oa:attendance:leave:type:list')" label="请假类型" name="types">
        <div class="type-toolbar">
          <p>新类型保存后默认停用，完成规则复核后再启用。</p>
          <el-button v-hasPermi="['oa:attendance:leave:type:add']" type="primary" size="small" icon="el-icon-plus" @click="openTypeCreate">新建类型</el-button>
        </div>
        <el-table v-loading="typeLoading" :data="types" size="small" empty-text="暂无请假类型">
          <el-table-column label="类型" min-width="150"><template slot-scope="scope"><strong>{{ scope.row.typeName }}</strong><div class="cell-subtitle">{{ scope.row.typeCode }}</div></template></el-table-column>
          <el-table-column label="计量" width="95"><template slot-scope="scope">{{ unitLabel(scope.row.unitMode) }}</template></el-table-column>
          <el-table-column label="薪资规则" width="100"><template slot-scope="scope">{{ payLabel(scope.row.payPolicy) }}</template></el-table-column>
          <el-table-column label="最小/步长" width="125"><template slot-scope="scope">{{ scope.row.minMinutes }} / {{ scope.row.stepMinutes }} 分</template></el-table-column>
          <el-table-column label="附件" min-width="150"><template slot-scope="scope">{{ attachmentPolicy(scope.row) }}</template></el-table-column>
          <el-table-column label="审批" width="80"><template slot-scope="scope">{{ scope.row.approvalRequired === false ? '自动' : '需要' }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template slot-scope="scope"><el-tag :type="enabled(scope.row) ? 'success' : 'info'" size="mini">{{ enabled(scope.row) ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template slot-scope="scope">
              <el-button v-hasPermi="['oa:attendance:leave:type:edit']" type="text" size="mini" @click="openTypeEdit(scope.row)">编辑</el-button>
              <el-button v-hasPermi="['oa:attendance:leave:type:edit', 'oa:attendance:leave:type:remove']" type="text" size="mini" @click="toggleType(scope.row)">{{ enabled(scope.row) ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="请假详情" :visible.sync="detailOpen" width="720px" append-to-body>
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请单号">{{ detail.leaveRequestNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.userName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.leaveTypeName }}</el-descriptions-item>
          <el-descriptions-item label="开始">{{ dateTime(detail.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束">{{ dateTime(detail.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="总时长">{{ minutesText(detail.totalMinutes) }}</el-descriptions-item>
          <el-descriptions-item label="附件数">{{ (detail.attachments || []).length }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.attachments && detail.attachments.length" class="detail-attachments">
          <strong>私有附件</strong>
          <el-button v-for="file in detail.attachments" :key="file.attachmentId" size="mini" icon="el-icon-paperclip" @click="downloadAttachment(file)">{{ file.originalName }}</el-button>
        </div>
        <el-alert title="审批动作由统一审批待办执行；本页只展示服务端真实申请和附件，不提供本地伪审批。" type="info" :closable="false" show-icon class="detail-alert" />
      </template>
    </el-dialog>

    <el-dialog :title="typeForm.leaveTypeId ? '编辑请假类型' : '新建请假类型'" :visible.sync="typeDialog" width="700px" append-to-body>
      <el-form ref="typeForm" :model="typeForm" :rules="typeRules" label-width="125px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="类型编码" prop="typeCode"><el-input v-model.trim="typeForm.typeCode" :disabled="Boolean(typeForm.leaveTypeId)" maxlength="32" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="类型名称" prop="typeName"><el-input v-model.trim="typeForm.typeName" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计量方式"><el-select v-model="typeForm.unitMode"><el-option label="按分钟" value="MINUTE" /><el-option label="半天" value="HALF_DAY" /><el-option label="按天" value="DAY" /><el-option label="混合" value="MIXED" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="薪资规则"><el-select v-model="typeForm.payPolicy"><el-option label="带薪" value="PAID" /><el-option label="无薪" value="UNPAID" /><el-option label="按比例" value="POLICY" /></el-select></el-form-item></el-col>
          <el-col v-if="typeForm.payPolicy === 'POLICY'" :span="12"><el-form-item label="带薪比例"><el-input-number v-model="typeForm.paidRatio" :min="0" :max="1" :step="0.1" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="最少分钟"><el-input-number v-model="typeForm.minMinutes" :min="1" :max="525600" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="递增步长"><el-input-number v-model="typeForm.stepMinutes" :min="1" :max="1440" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单次上限"><el-input-number v-model="typeForm.maxMinutesPerRequest" :min="typeForm.minMinutes || 1" :max="525600" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="附件阈值"><el-input-number v-model="typeForm.attachmentThresholdMinutes" :min="1" :max="525600" placeholder="留空不按时长要求" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="允许跨天"><el-switch v-model="typeForm.allowCrossDay" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="始终需附件"><el-switch v-model="typeForm.attachmentRequired" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="需要审批"><el-switch v-model="typeForm.approvalRequired" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <span slot="footer"><el-button size="small" @click="typeDialog = false">取消</el-button><el-button type="primary" size="small" :loading="typeSaving" @click="saveType">保存</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import {
  changeAttendanceLeaveTypeStatus,
  createAttendanceLeaveType,
  getAttendanceLeave,
  getAttendanceLeaveAttachmentContent,
  listAttendanceLeaveTypes,
  listShopAttendanceLeaves,
  updateAttendanceLeaveType
} from '@/api/oa/attendanceV2'

const { dataOf } = require('@/views/mobile/attendance/attendancePunchPolicy')

const emptyType = () => ({
  leaveTypeId: null, typeCode: '', typeName: '', unitMode: 'MINUTE', payPolicy: 'UNPAID', paidRatio: 0,
  balanceRequired: false, attachmentRequired: false, attachmentThresholdMinutes: null,
  minMinutes: 30, stepMinutes: 30, maxMinutesPerRequest: 43200,
  allowCrossDay: true, approvalRequired: true, sortNo: 0, status: 'DISABLED', rowVersion: null
})

export default {
  name: 'AttendanceLeaveManagement',
  props: {
    shopContext: { type: Object, default: () => ({}) },
    businessId: { type: [String, Number], default: '' }
  },
  data() {
    return {
      innerTab: 'requests', requestLoading: false, requests: [],
      query: { status: '', dates: [] }, detail: null, detailOpen: false,
      typeLoading: false, typeSaving: false, types: [], typeDialog: false, typeForm: emptyType(),
      typeRules: { typeCode: [{ required: true, message: '请输入类型编码', trigger: 'blur' }], typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }] },
      statusOptions: [
        { value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTING', label: '提交中' }, { value: 'PENDING', label: '审批中' },
        { value: 'APPROVED', label: '已通过' }, { value: 'REJECTED', label: '已驳回' }, { value: 'RETURNED', label: '已退回' }, { value: 'CANCELLED', label: '已撤回' }
      ]
    }
  },
  watch: {
    'shopContext.deptId'() { this.loadRequests() },
    businessId(value) { if (value) this.openDetail(value) }
  },
  created() {
    if (this.can('oa:attendance:leave:list')) this.loadRequests()
    if (this.can('oa:attendance:leave:type:list')) this.loadTypes()
    if (this.businessId) this.openDetail(this.businessId)
    if (!this.can('oa:attendance:leave:list') && this.can('oa:attendance:leave:type:list')) this.innerTab = 'types'
  },
  methods: {
    can(permission) { return !this.$auth || typeof this.$auth.hasPermi !== 'function' ? false : this.$auth.hasPermi(permission) },
    rows(response) { const payload = dataOf(response); return Array.isArray(payload) ? payload : [] },
    loadRequests() {
      if (!this.shopContext.isStore || !this.shopContext.deptId || !this.can('oa:attendance:leave:list')) { this.requests = []; return Promise.resolve() }
      const params = { shopId: this.shopContext.deptId, status: this.query.status || undefined }
      if (this.query.dates && this.query.dates.length === 2) [params.dateFrom, params.dateTo] = this.query.dates
      this.requestLoading = true
      return listShopAttendanceLeaves(params).then(response => { this.requests = this.rows(response) })
        .catch(error => { this.requests = []; this.$modal.msgError(error.message || '请假申请加载失败') })
        .finally(() => { this.requestLoading = false })
    },
    openDetail(id) {
      if (!id) return
      return getAttendanceLeave(id).then(response => { this.detail = dataOf(response); this.detailOpen = true })
        .catch(error => { this.$modal.msgError(error.message || '请假详情加载失败') })
    },
    loadTypes() {
      this.typeLoading = true
      return listAttendanceLeaveTypes({}).then(response => { this.types = this.rows(response) })
        .catch(error => { this.types = []; this.$modal.msgError(error.message || '请假类型加载失败') })
        .finally(() => { this.typeLoading = false })
    },
    openTypeCreate() { this.typeForm = emptyType(); this.typeDialog = true },
    openTypeEdit(row) { this.typeForm = Object.assign(emptyType(), row); this.typeDialog = true },
    saveType() {
      this.$refs.typeForm.validate(valid => {
        if (!valid || this.typeSaving) return
        this.typeSaving = true
        const request = this.typeForm.leaveTypeId ? updateAttendanceLeaveType(this.typeForm.leaveTypeId, this.typeForm) : createAttendanceLeaveType(this.typeForm)
        request.then(() => { this.$modal.msgSuccess('请假类型已保存'); this.typeDialog = false; return this.loadTypes() })
          .catch(error => { this.$modal.msgError(error.message || '请假类型保存失败') })
          .finally(() => { this.typeSaving = false })
      })
    },
    toggleType(row) {
      const status = this.enabled(row) ? 'DISABLED' : 'ENABLED'
      return this.$modal.confirm(`确认${status === 'ENABLED' ? '启用' : '停用'}请假类型“${row.typeName}”？`).then(() => changeAttendanceLeaveTypeStatus(row.leaveTypeId, { status, rowVersion: row.rowVersion }))
        .then(() => { this.$modal.msgSuccess('请假类型状态已更新'); return this.loadTypes() })
        .catch(error => { if (error !== 'cancel' && error !== 'close') this.$modal.msgError(error.message || '状态更新失败') })
    },
    downloadAttachment(file) {
      return getAttendanceLeaveAttachmentContent(this.detail.leaveRequestId, file.attachmentId).then(blob => {
        const url = URL.createObjectURL(blob); const link = document.createElement('a')
        link.href = url; link.download = file.originalName || `leave-${file.attachmentId}`; link.click()
        setTimeout(() => URL.revokeObjectURL(url), 60000)
      }).catch(error => { this.$modal.msgError(error.message || '附件读取失败') })
    },
    enabled(row) { return String(row.status || '').toUpperCase() === 'ENABLED' },
    statusLabel(value) { return { DRAFT: '草稿', SUBMITTING: '提交中', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', RETURNED: '已退回', CANCELLED: '已撤回' }[String(value || '').toUpperCase()] || '未知' },
    statusTag(value) { const key = String(value || '').toUpperCase(); return key === 'APPROVED' ? 'success' : ['REJECTED', 'RETURNED'].includes(key) ? 'danger' : ['PENDING', 'SUBMITTING'].includes(key) ? 'warning' : 'info' },
    dateTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' },
    minutesText(value) { const minutes = Number(value || 0); return minutes ? `${(minutes / 60).toFixed(minutes % 60 ? 1 : 0)} 小时` : '-' },
    unitLabel(value) { return { MINUTE: '分钟', HALF_DAY: '半天', DAY: '天', MIXED: '混合' }[value] || value },
    payLabel(value) { return { PAID: '带薪', UNPAID: '无薪', POLICY: '按比例' }[value] || value },
    attachmentPolicy(row) { return row.attachmentRequired ? '始终必传' : row.attachmentThresholdMinutes ? `≥ ${row.attachmentThresholdMinutes} 分必传` : '非必传' }
  }
}
</script>

<style scoped>
.table-card { margin-top: 16px; }
.type-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.type-toolbar p { margin: 0; color: #6b7280; }
.cell-subtitle { margin-top: 4px; color: #8492a6; font-size: 12px; }
.detail-attachments { display: flex; align-items: center; gap: 8px; margin-top: 16px; flex-wrap: wrap; }
.detail-alert { margin-top: 16px; }
</style>
