<template>
  <div class="sign-package-record-panel">
    <el-empty v-if="!signPackage" description="请先回到“签约包”页签，选择一份签约包查看合同" />
    <template v-else>
      <div class="contract-summary">
        <div>
          <strong>{{ signPackage.employeeNameSnapshot || '未命名员工' }}的签约合同</strong>
          <span>签约包编号：{{ signPackage.packageId }}</span>
        </div>
        <el-tag size="small" :type="packageStatusType">{{ packageStatusLabel(signPackage) }}</el-tag>
      </div>

      <el-table :data="signPackage.documents || []" border size="small" empty-text="暂无签约合同">
        <el-table-column label="合同名称" prop="documentName" min-width="220" show-overflow-tooltip />
        <el-table-column label="签署状态" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="documentStatus(scope.row).type">{{ documentStatus(scope.row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="员工确认" width="100">
          <template slot-scope="scope">{{ employeeConfirmationLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="合同文件" min-width="240">
          <template slot-scope="scope">
            <el-button
              v-if="scope.row.finalPdfUrl"
              type="text"
              size="mini"
              @click="$emit('open-final', scope.row)"
            >查看最终合同</el-button>
            <el-button
              v-else-if="scope.row.signedPdfUrl"
              type="text"
              size="mini"
              @click="$emit('open-signed', scope.row)"
            >查看已签文件</el-button>
            <el-button
              v-else-if="scope.row.reviewPdfUrl || scope.row.generatedPdfUrl || scope.row.generatedFileUrl"
              type="text"
              size="mini"
              @click="$emit('open-original', scope.row)"
            >查看合同</el-button>
            <el-button
              v-if="scope.row.certificateFileUrl"
              type="text"
              size="mini"
              @click="$emit('open-certificate', scope.row)"
            >签署证明</el-button>
            <span v-if="!hasDocumentFile(scope.row)">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-collapse v-if="(signPackage.events || []).length" class="event-history">
        <el-collapse-item name="events">
          <template slot="title">
            <span>操作记录（{{ signPackage.events.length }} 条）</span>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="event in signPackage.events"
              :key="event.eventId || [event.eventType, event.createTime, event.operatorName].join('-')"
              :timestamp="event.createTime"
              placement="top"
              size="small"
            >
              {{ eventTypeLabel(event.eventType) }} · {{ operatorDisplayName(event) }}
            </el-timeline-item>
          </el-timeline>
        </el-collapse-item>
      </el-collapse>
    </template>
  </div>
</template>

<script>
import {
  signPackageEventLabel,
  signPackageStatusLabel
} from '@/utils/signDictionary'

export default {
  name: 'SignPackageRecordPanel',
  props: {
    signPackage: { type: Object, default: null }
  },
  computed: {
    packageStatusType() {
      const status = this.signPackage && this.signPackage.status
      if (status === 'signed') return 'success'
      if (['refused', 'expired', 'voided', 'failed'].includes(status)) return 'danger'
      return 'warning'
    }
  },
  methods: {
    packageStatusLabel(signPackage) {
      const confirmationStatus = this.finalConfirmationStatus()
      if (signPackage && signPackage.status === 'pending_company' && confirmationStatus === 'PREPARED_NOT_SENT') {
        return '最终文件已生成，待发送'
      }
      if (signPackage && signPackage.status === 'pending_company' && confirmationStatus === 'WAITING_COMPANY') {
        return '唯一签名已完成，待选公司和印章'
      }
      return signPackageStatusLabel(signPackage && signPackage.status)
    },
    eventTypeLabel(value) {
      return signPackageEventLabel(value)
    },
    operatorDisplayName(event) {
      if (event.operatorDisplayName) return event.operatorDisplayName
      if (event.operatorRole === 'SYSTEM' || event.operatorName === 'system') return '系统'
      if (event.operatorName && event.operatorName === this.signPackage.employeePhoneSnapshot) {
        return this.signPackage.employeeNameSnapshot || '员工'
      }
      return event.operatorName || '未知用户'
    },
    documentStatus(row) {
      if (row.finalPdfUrl) {
        const confirmationStatus = this.finalConfirmationStatus()
        if (confirmationStatus === 'PREPARED_NOT_SENT') return { label: '已生成待发送', type: 'warning' }
        if (confirmationStatus === 'PENDING') return { label: '待员工确认', type: 'warning' }
        if (confirmationStatus === 'CONFIRMED') return { label: '已完成', type: 'success' }
        return { label: '最终文件已生成', type: 'warning' }
      }
      if (row.signed === 'Y' || row.signedPdfUrl) return { label: '已签署', type: 'success' }
      if (row.readConfirmed === 'Y') return { label: '待签署', type: 'warning' }
      return { label: '待阅读', type: 'info' }
    },
    employeeConfirmationLabel(row) {
      if (!row.finalPdfUrl) return row.readConfirmed === 'Y' ? '已阅读' : '未阅读'
      const confirmationStatus = this.finalConfirmationStatus()
      if (confirmationStatus === 'PREPARED_NOT_SENT') return '未发送'
      if (confirmationStatus === 'CONFIRMED') return '已确认'
      if (confirmationStatus === 'PENDING') return row.finalReadConfirmed === 'Y' ? '已打开' : '待打开'
      return row.finalReadConfirmed === 'Y' ? '已打开' : '待确认'
    },
    finalConfirmationStatus() {
      return String(this.signPackage && this.signPackage.finalConfirmationStatus || '').trim().toUpperCase()
    },
    hasDocumentFile(row) {
      return !!(row.finalPdfUrl || row.signedPdfUrl || row.reviewPdfUrl || row.generatedPdfUrl || row.generatedFileUrl || row.certificateFileUrl)
    }
  }
}
</script>

<style scoped>
.contract-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #f8fafc;
}
.contract-summary strong { margin-right: 16px; color: #303133; font-size: 15px; }
.contract-summary span { color: #909399; font-size: 13px; }
.event-history { margin-top: 16px; }
.event-history ::v-deep .el-collapse-item__header { padding: 0 12px; color: #606266; }
.event-history ::v-deep .el-collapse-item__content { padding: 12px 20px 0; }
</style>
