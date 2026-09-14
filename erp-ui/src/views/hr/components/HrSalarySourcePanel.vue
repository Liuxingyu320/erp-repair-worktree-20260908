<template>
  <section class="hr-drawer-section" v-loading="loading">
    <h3>合同工资来源</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-else-if="source">
      <el-tag :type="source.status === 'CONFIRMED' ? 'success' : 'warning'">{{ statusText }}</el-tag>
      <el-descriptions v-if="source.sourceId" :column="2" size="small" border style="margin-top:10px">
        <el-descriptions-item label="来源">{{ typeText }}</el-descriptions-item>
        <el-descriptions-item :label="source.sourceType === 'ONBOARD_EXCEL' ? '同步日期' : '适用日期'">{{ source.effectiveDate }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ source.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ source.confirmedAt }}</el-descriptions-item>
        <el-descriptions-item label="来源批次 / 行">{{ source.batchId || '-' }} / {{ source.rowId || source.businessId }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="source.status !== 'CONFIRMED'">在“批量处理入职合同”生成合同时，会自动同步合同工资。</p>
      <div v-for="contract in source.contracts || []" :key="contract.packageId" style="margin-top:10px">
        合同 {{ contract.packageId }}：{{ contractStatus(contract.status) }}
        <el-button v-hasPermi="['oa:signTask:query']" type="text" @click="openContract(contract)">查看来源合同</el-button>
      </div>
      <p>金额在“入职合同工资”中按权限查看。</p>
    </template>
  </section>
</template>
<script>
import request from '@/utils/request'
export default {
  name: 'HrSalarySourcePanel',
  props: { employeeId: [String, Number], visible: Boolean },
  data() { return { source: null, loading: false, error: '', version: 0 } },
  computed: {
    statusText() { return { CONFIRMED: '合同工资已同步', MISSING: '尚未同步合同工资', REVIEW_REQUIRED: '合同工资待同步' }[this.source.status] },
    typeText() { return { ONBOARD_EXCEL: '入职合同工资', REGULARIZATION: '正式转正', TRANSFER: '正式调岗' }[this.source.sourceType] || this.source.sourceType }
  },
  watch: { employeeId: { immediate: true, handler() { this.load() } }, visible() { this.load() } },
  beforeDestroy() { this.version++ },
  methods: {
    contractStatus(status) {
      return { DRAFT: '待发送', PENDING_SIGN: '待员工签署', PART_VIEWED: '员工已查看',
        PENDING_COMPANY: '待公司处理', PENDING_FINAL_CONFIRM: '待员工确认最终合同',
        SIGNED: '已签署', VOID: '已作废', VOIDED: '已作废', EXPIRED: '已过期' }[status] || status
    },
    openContract(contract) { this.$router.push({ path: '/oa/sign-task', query: { taskId: contract.taskId } }) },
    async load() {
      const version = ++this.version
      this.source = null; this.error = ''; this.loading = false
      if (!this.visible || !this.employeeId) return
      this.loading = true
      try {
        const result = await request({ url: `/system/hr/employee/${encodeURIComponent(this.employeeId)}/salary-source`, method: 'get', silentError: true })
        if (version === this.version) this.source = result.data
      } catch (error) { if (version === this.version) this.error = error.message || '工资来源读取失败' }
      finally { if (version === this.version) this.loading = false }
    }
  }
}
</script>
