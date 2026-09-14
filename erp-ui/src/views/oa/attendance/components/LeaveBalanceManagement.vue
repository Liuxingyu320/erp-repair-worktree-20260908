<template>
  <section v-if="canRead" aria-label="员工假期额度管理">
    <p>在当前组织内查找员工，查看额度来源和流水。调整会留下原因与操作记录。</p>
    <div class="balance-search"><input v-model.trim="keyword" maxlength="64" placeholder="姓名、账号或工号" :disabled="busy" @keydown.enter.prevent="search(1)"><button type="button" :disabled="busy" @click="search(1)">查找员工</button></div>
    <p v-if="error" role="alert">{{ error }}</p>
    <ul class="employee-options"><li v-for="employee in employees" :key="employee.userId"><span>{{ employee.userName }} · {{ employee.account }} · {{ employee.deptName }}</span><button type="button" :disabled="busy" @click="selectEmployee(employee)">{{ userId === employee.userId ? '已选' : '选择' }}</button></li></ul>
    <div v-if="employees.length"><button type="button" :disabled="busy || page <= 1" @click="search(page - 1)">上一页</button><span>第 {{ page }} 页</span><button type="button" :disabled="busy || page * 20 >= total" @click="search(page + 1)">下一页</button></div>
    <h3 v-if="selectedName">{{ selectedName }}</h3>
    <leave-balance-summary v-if="userId" :key="userId" ref="summary" :user-id="userId" @balance="receivedBalance" />
    <template v-if="balance">
      <div v-if="canAdjust" class="bucket-list"><div v-for="bucket in balance.buckets" :key="bucket.bucketId">
        <span>{{ bucket.periodYear || '' }} · 到期 {{ bucket.expiresOn || '待核对' }} · 规则第 {{ bucket.ruleVersion }} 版</span>
        <button type="button" :disabled="busy || !!attempt || !!bucket.sourceProblem || bucket.sourceType === 'OVERTIME' || bucket.expiryState !== 'OPEN'" @click="openAdjustment(bucket)">调整该笔额度</button>
      </div></div>
      <button type="button" :disabled="busy" @click="loadLedger(false)">查看额度流水</button>
      <ul><li v-for="item in ledger" :key="item.ledgerId">{{ item.createdAt }} · {{ actionLabel(item.action) }} · {{ ledgerAmount(item) }} · {{ item.reason || '系统按规则处理' }}</li></ul>
      <button v-if="ledger.length && ledgerHasMore" type="button" :disabled="busy" @click="loadLedger(true)">更早流水</button>
    </template>
    <el-dialog title="调整假期额度" :visible.sync="adjustOpen" width="520px" append-to-body :close-on-click-modal="false">
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="adjustBucket">
        <p>{{ selectedName }} · 到期 {{ adjustBucket.expiresOn }}；规则第 {{ adjustBucket.ruleVersion }} 版。</p>
        <p v-if="adjustBucket.displayUnit === 'DAYS'">本笔额度每天按 {{ adjustBucket.minutesPerDay }} 分钟换算。</p>
        <label>调整数量（{{ unitLabel(adjustBucket.displayUnit) }}，减少填负数）<input v-model.trim="amount" inputmode="decimal" :disabled="busy || !!attempt"></label>
        <label>调整原因<textarea v-model.trim="reason" maxlength="500" rows="3" :disabled="busy || !!attempt" /></label>
        <p v-if="attempt">已保留原调整请求，请核对同一次操作结果。</p>
        <button type="button" :disabled="busy" @click="attempt ? sendAdjustment() : confirmAdjustment()">{{ attempt ? '核对同一次调整结果' : '确认调整' }}</button>
      </template>
    </el-dialog>
    <button v-if="attempt && !adjustOpen" type="button" :disabled="busy" @click="sendAdjustment">核对上次调整结果</button>
  </section>
</template>

<script>
import LeaveBalanceSummary from './LeaveBalanceSummary.vue'
import { listBalanceEmployeeOptions } from '@/api/oa/attendanceLeaveBalanceOptions'
import { adjustEmployeeLeaveBalance, getEmployeeLeaveBalanceLedger } from '@/api/oa/attendanceLeaveBalance'
import { getSelectedDeptId } from '@/utils/shopContext'
const { exactId, decimal, dataOf, listOf, clone, unitLabel, formatUnits, requestId, permission, persistAttempt, restoreAttempt, clearAttempt, definiteRejection } = require('@/utils/leaveBalanceUi')
export default {
  name: 'LeaveBalanceManagement', components: { LeaveBalanceSummary },
  data() { return { keyword: '', employees: [], page: 1, total: 0, userId: '', selectedName: '', balance: null, balanceScope: '', adjustScope: '', employeeScope: '', ledger: [], ledgerHasMore: false, busy: false, sequence: 0, error: '', adjustOpen: false, adjustBucket: null, amount: '', reason: '', attempt: null } },
  computed: { canRead() { return permission(this, 'read') }, canAdjust() { return permission(this, 'adjust') } },
  watch: { '$store.getters.id'() { this.reset() }, '$route.fullPath'() { this.reset() } },
  created() { this._deptHandler = () => this.reset(); if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this._deptHandler); this.attempt = restoreAttempt(this.scope(), 'adjustment') },
  beforeDestroy() { this.reset(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this._deptHandler) },
  methods: {
    scope() { return JSON.stringify([getSelectedDeptId(), this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath]) },
    current(scope, seq) { return !this._isDestroyed && scope === this.scope() && seq === this.sequence },
    reset() { this.sequence++; this.employees = []; this.userId = ''; this.selectedName = ''; this.balance = null; this.ledger = []; this.busy = false; this.error = ''; this.adjustOpen = false; this.adjustBucket = null; this.attempt = restoreAttempt(this.scope(), 'adjustment') },
    async search(nextPage) {
      if (this.busy || !this.canRead) return
      const scope = this.scope(), seq = ++this.sequence; this.busy = true; this.error = ''; this.employees = []
      try {
        const data = dataOf(await listBalanceEmployeeOptions({ ownerDeptId: exactId(getSelectedDeptId()), keyword: this.keyword, pageNum: nextPage, pageSize: 20 }))
        if (!this.current(scope, seq)) return
        if (!Array.isArray(data.rows) || !Number.isSafeInteger(data.total) || data.total < 0 || data.rows.length > 20) throw Error('员工候选结果无法确认')
        this.employees = data.rows.map(row => ({ ...row, userId: exactId(row.userId) })); this.employeeScope = scope; this.total = data.total; this.page = nextPage
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '员工读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    selectEmployee(row) { if (this.busy || !this.employees.includes(row) || this.employeeScope !== this.scope()) return; this.userId = exactId(row.userId); this.selectedName = row.userName; this.balance = null; this.ledger = []; this.adjustOpen = false },
    receivedBalance(balance) { this.balance = balance; this.balanceScope = this.scope(); this.ledger = []; this.ledgerHasMore = false },
    async loadLedger(more) {
      if (this.busy || !this.canRead || !this.balance) return
      const scope = this.scope(), seq = ++this.sequence, userId = this.userId, typeId = exactId(this.balance.leaveTypeId)
      this.busy = true; this.error = ''
      try {
        const before = more && this.ledger.length ? exactId(this.ledger[this.ledger.length - 1].ledgerId) : undefined
        const rows = listOf(await getEmployeeLeaveBalanceLedger(exactId(userId), typeId, before, { silentError: true })).map(row => ({ ...row, ledgerId: exactId(row.ledgerId) }))
        if (!this.current(scope, seq) || userId !== this.userId || !this.balance || typeId !== String(this.balance.leaveTypeId)) return
        const combined = more ? this.ledger.concat(rows) : rows
        if (new Set(combined.map(r => r.ledgerId)).size !== combined.length) throw Error('流水分页重复，请重新读取')
        this.ledger = combined; this.ledgerHasMore = rows.length === 100
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '流水读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    openAdjustment(bucket) { if (this.busy || this.attempt || !this.canAdjust || !this.balance || this.balanceScope !== this.scope() || !this.balance.buckets.includes(bucket) || bucket.sourceType === 'OVERTIME' || bucket.expiryState !== 'OPEN') return; this.adjustBucket = clone(bucket); this.adjustScope = this.scope(); this.amount = ''; this.reason = ''; this.adjustOpen = true },
    async confirmAdjustment() {
      if (this.busy || this.attempt || !this.canAdjust || !this.adjustBucket || this.adjustScope !== this.scope() || !this.balance) return
      const scope = this.scope(), seq = this.sequence, userId = exactId(this.userId), bucket = clone(this.adjustBucket)
      try {
        const amount = decimal(this.amount, true), reason = this.reason.trim()
        if (Number(amount) === 0 || !reason) throw Error('请填写非零调整数量及原因')
        const body = { leaveTypeId: exactId(this.balance.leaveTypeId), bucketId: exactId(bucket.bucketId), bucketVersion: exactId(bucket.rowVersion, true), ruleId: exactId(bucket.ruleId), ruleVersion: bucket.ruleVersion, displayUnit: bucket.displayUnit, minutesPerDay: bucket.minutesPerDay, amount, reason, clientRequestId: requestId() }
        if (!Number.isSafeInteger(body.ruleVersion) || body.ruleVersion < 1 || !unitLabel(body.displayUnit)) throw Error('本笔额度规则快照不完整，请刷新核对')
        this.busy = true; await this.$confirm(`调整 ${this.selectedName} 的额度 ${amount} ${unitLabel(body.displayUnit)}？`, '确认额度调整')
        if (!this.current(scope, seq) || userId !== this.userId || !this.adjustOpen || !this.balance || String(this.balance.leaveTypeId) !== body.leaveTypeId) return
        this.attempt = { scope, userId, body: clone(body) }; persistAttempt(scope, 'adjustment', this.attempt); this.busy = false; return this.sendAdjustment()
      } catch (error) { if (this.current(scope, seq) && error && error.message) this.error = error.message }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async sendAdjustment() {
      if (this.busy || !this.attempt || !this.canAdjust) return
      if (this.attempt.scope !== this.scope()) { this.error = '账号或组织已变化，请切回原操作页面核对'; return }
      const scope = this.scope(), seq = ++this.sequence, attempt = clone(this.attempt); this.busy = true; this.error = ''
      try {
        persistAttempt(scope, 'adjustment', attempt)
        const result = dataOf(await adjustEmployeeLeaveBalance(exactId(attempt.userId), clone(attempt.body)))
        if (!this.current(scope, seq)) return
        exactId(result.commandId)
        if (exactId(result.bucketId) !== attempt.body.bucketId || result.commandKey !== 'ADJUST|' + this.$store.getters.id + '|' + attempt.body.clientRequestId) throw Error('额度调整结果无法确认')
        clearAttempt(scope, 'adjustment'); this.attempt = null; this.adjustOpen = false; this.adjustBucket = null
        if (this.$refs.summary && this.userId === attempt.userId) await this.$refs.summary.loadBalance()
      } catch (error) { if (this.current(scope, seq)) { this.error = error.message || '调整结果未确认，请用原请求核对'; if (definiteRejection(error)) { clearAttempt(scope, 'adjustment'); this.attempt = null; this.adjustOpen = false } } }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    ledgerAmount(item) { const bucket = this.balance && this.balance.buckets.find(bucket => String(bucket.bucketId) === String(item.bucketId)); return bucket ? formatUnits(item.units, bucket.displayUnit, bucket.minutesPerDay) : '待核对' },
    unitLabel,
    actionLabel(action) { return { GRANT: '累积', ADJUST: '调整', RESERVE: '审批占用', CONSUME: '使用', RELEASE: '释放', EXPIRE: '到期', CARRY: '结转', OVERTIME: '加班转休', OVERTIME_REVERSE: '撤销转休' }[action] || '额度变动' }
  }
}
</script>

<style scoped>
.balance-search,.bucket-list>div,.employee-options li{display:flex;align-items:center;gap:12px;margin:10px 0;flex-wrap:wrap}input,textarea{max-width:100%;padding:7px;border:1px solid #cbd5e1;border-radius:5px}button{padding:7px 10px;background:white;border:1px solid #94a3b8;border-radius:5px}button:disabled{opacity:.5}.employee-options{padding:0;list-style:none}[role=alert]{color:#a33817}label{display:block;margin:14px 0}textarea{display:block;width:100%}
</style>
