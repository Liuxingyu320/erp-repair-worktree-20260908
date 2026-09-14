<template>
  <section v-if="allowed" class="leave-balance-summary" aria-label="假期额度">
    <div class="balance-toolbar">
      <strong>{{ userId ? '员工假期额度' : '我的假期额度' }}</strong>
      <select v-model="typeId" :disabled="loading" aria-label="额度假种" @change="loadBalance">
        <option value="" disabled>请选择假期类型</option>
        <option v-for="type in types" :key="type.leaveTypeId" :value="type.leaveTypeId">{{ type.typeName }}</option>
      </select>
      <button type="button" :disabled="loading || !typeId" @click="loadBalance">刷新</button>
      <button v-if="canRecalculate" type="button" :disabled="loading || !typeId" @click="recalculate">按已发布规则更新额度</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取额度…</p>
    <template v-if="balance">
      <p v-if="balance.status !== 'READY'" class="balance-attention">{{ balance.reason || '额度规则尚未匹配，请联系 HR 核对公司、工作地和政策' }}</p>
      <div class="balance-totals">
        <span>可用 <strong>{{ display(balance.availableUnits) }}</strong></span>
        <span>审批占用 <strong>{{ display(balance.reservedUnits) }}</strong></span>
        <span>已使用 <strong>{{ display(balance.consumedUnits) }}</strong></span>
      </div>
      <p v-if="balance.ruleName">适用规则：{{ balance.ruleName }}（第 {{ balance.ruleVersion }} 版）</p>
      <p v-if="balance.displayUnit === 'DAYS'">本规则每个工作日按 {{ balance.minutesPerDay }} 分钟换算。</p>
      <details v-if="balance.buckets.length"><summary>额度来源与有效期</summary>
        <ul><li v-for="bucket in balance.buckets" :key="bucket.bucketId">
          <span>{{ sourceLabel(bucket.sourceType) }} · {{ bucket.periodYear || '' }} · 到期 {{ bucket.expiresOn || '待核对' }}</span>
          <span>累计 {{ format(bucket.grantedUnits, bucket.displayUnit, bucket.minutesPerDay) }}；占用 {{ format(bucket.reservedUnits, bucket.displayUnit, bucket.minutesPerDay) }}；已使用 {{ format(bucket.consumedUnits, bucket.displayUnit, bucket.minutesPerDay) }}</span>
          <strong v-if="bucket.sourceProblem">{{ bucket.sourceProblem }}</strong>
        </li></ul>
      </details>
    </template>
  </section>
</template>

<script>
import { getMyLeaveBalance, getEmployeeLeaveBalance, recalculateMyLeaveBalance, recalculateEmployeeLeaveBalance } from '@/api/oa/attendanceLeaveBalance'
import { listBalanceTypeOptions } from '@/api/oa/attendanceLeaveBalanceOptions'
import { getSelectedDeptId } from '@/utils/shopContext'
const { exactId, listOf, dataOf, formatUnits, permission } = require('@/utils/leaveBalanceUi')
export default {
  name: 'LeaveBalanceSummary',
  props: { userId: { type: [String, Number], default: '' }, initialTypeId: { type: [String, Number], default: '' } },
  data() { return { types: [], typeId: '', balance: null, loading: false, error: '', sequence: 0 } },
  computed: {
    allowed() { return permission(this, this.userId ? 'read' : 'self') },
    canRecalculate() { return permission(this, this.userId ? 'adjust' : 'self') }
  },
  watch: {
    userId() { this.invalidate(); this.loadTypes() },
    initialTypeId(value) { if (this.types.some(t => String(t.leaveTypeId) === String(value))) { this.typeId = String(value); this.loadBalance() } },
    '$store.getters.id'() { this.invalidate(); this.loadTypes() },
    '$route.fullPath'() { this.invalidate() }
  },
  created() { this._deptHandler = () => { this.invalidate(); this.loadTypes() }; if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this._deptHandler); this.loadTypes() },
  beforeDestroy() { this.invalidate(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this._deptHandler) },
  methods: {
    scope() { return JSON.stringify([getSelectedDeptId(), this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath, this.userId]) },
    current(scope, seq) { return !this._isDestroyed && scope === this.scope() && seq === this.sequence },
    invalidate() { this.sequence++; this.balance = null; this.loading = false; this.error = ''; this.$emit('balance', null) },
    async loadTypes() {
      if (!this.allowed) return
      const scope = this.scope(), seq = ++this.sequence; this.loading = true
      try {
        const types = listOf(await listBalanceTypeOptions()).map(row => ({ ...row, leaveTypeId: exactId(row.leaveTypeId) }))
        if (!this.current(scope, seq)) return
        this.types = types
        const candidate = this.initialTypeId || this.typeId
        this.typeId = types.some(t => String(t.leaveTypeId) === String(candidate)) ? String(candidate) : types.length ? types[0].leaveTypeId : ''
        if (this.typeId) return this.loadBalance()
        this.error = '尚无启用的假期类型，请联系 HR'
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '假期类型读取失败' }
      finally { if (this.current(scope, seq)) this.loading = false }
    },
    checkedBalance(response, typeId) {
      const balance = dataOf(response)
      exactId(balance.userId); exactId(balance.leaveTypeId)
      if (String(balance.leaveTypeId) !== typeId || this.userId && String(balance.userId) !== String(this.userId) || !this.userId && String(balance.userId) !== String(this.$store.getters.id) || !Array.isArray(balance.buckets)) throw Error('额度读取结果不匹配')
      for (const bucket of balance.buckets) { exactId(bucket.bucketId); exactId(bucket.rowVersion, true) }
      return balance
    },
    async loadBalance() {
      this.balance = null; this.$emit('balance', null); this.error = ''
      if (!this.allowed || !this.typeId) return
      const scope = this.scope(), seq = ++this.sequence; this.loading = true
      try {
        const typeId = exactId(this.typeId), userId = this.userId ? exactId(this.userId) : ''
        const response = await (userId ? getEmployeeLeaveBalance(userId, typeId, { silentError: true }) : getMyLeaveBalance(typeId, { silentError: true }))
        if (!this.current(scope, seq) || this.typeId !== typeId) return
        this.balance = this.checkedBalance(response, typeId); this.$emit('balance', this.balance)
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '额度读取失败' }
      finally { if (this.current(scope, seq)) this.loading = false }
    },
    async recalculate() {
      if (this.loading || !this.typeId || !this.canRecalculate) return
      const scope = this.scope(), seq = ++this.sequence, typeId = exactId(this.typeId); this.loading = true; this.error = ''
      try {
        await (this.userId ? recalculateEmployeeLeaveBalance(exactId(this.userId), typeId) : recalculateMyLeaveBalance(typeId))
        if (this.current(scope, seq)) return this.loadBalance()
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '额度更新结果未确认，请刷新核对' }
      finally { if (this.current(scope, seq)) this.loading = false }
    },
    display(value) { return formatUnits(value, this.balance && this.balance.displayUnit, this.balance && this.balance.minutesPerDay) },
    format: formatUnits,
    sourceLabel(type) { return { ANNUAL: '按政策累积', RULE: '按规则累积', ANNUAL_RULE: '年假规则', OVERTIME: '主管核定加班', OVERTIME_TRANSFER: '主管核定加班', CARRY: '结转', ADJUSTMENT: 'HR 调整' }[type] || '额度记录' }
  }
}
</script>

<style scoped>
.leave-balance-summary{padding:16px;border:1px solid #dce3ed;border-radius:10px;margin:12px 0;background:#f8fafc;overflow-wrap:anywhere}.balance-toolbar,.balance-totals{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.balance-toolbar select,.balance-toolbar button{min-height:32px;max-width:100%;border:1px solid #cbd5e1;border-radius:5px;padding:5px 8px;background:white}.balance-totals{margin:14px 0}.balance-totals strong{font-size:19px;margin-left:8px}.balance-attention,[role=alert]{color:#a04414}li{margin:8px 0}li span{display:block}
</style>
