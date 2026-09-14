<template>
  <el-dialog title="核定加班转调休" :visible="open" width="680px" append-to-body :close-on-click-modal="false" @close="$emit('close')">
    <p>从已日结加班中核定转休分钟数。已用于早退抵扣、转休或已生成工资的来源由系统重新校验。</p>
    <p v-if="error" class="transfer-error" role="alert">{{ error }}</p>
    <div class="transfer-controls">
      <label>日结日期 <input v-model="date" type="date" :disabled="busy || !!attempt"></label>
      <label>员工 <input v-model.trim="keyword" maxlength="64" :disabled="busy || !!attempt" placeholder="姓名或账号"></label>
      <button type="button" :disabled="busy || !!attempt" @click="loadSources(1)">查找日结来源</button>
    </div>
    <ul class="source-list"><li v-for="row in sources" :key="row.dayResultId">
      <span>{{ row.businessDate }} · {{ row.userName }} · 实际 {{ row.workedMinutes }} / 应出勤 {{ row.scheduledMinutes }} 分钟</span>
      <button type="button" :disabled="busy || !!attempt" @click="chooseSource(row)">{{ sourceId === row.dayResultId ? '已选' : '选择' }}</button>
    </li></ul>
    <div v-if="sources.length" class="transfer-controls"><button type="button" :disabled="busy || page <= 1 || !!attempt" @click="loadSources(page - 1)">上一页</button><span>第 {{ page }} 页</span><button type="button" :disabled="busy || page * 20 >= total || !!attempt" @click="loadSources(page + 1)">下一页</button></div>
    <label>转入假种 <select v-model="typeId" :disabled="busy || !!attempt" @change="loadContext"><option value="" disabled>请选择</option><option v-for="type in types" :key="type.leaveTypeId" :value="type.leaveTypeId">{{ type.typeName }}</option></select></label>
    <template v-if="context">
      <p><strong>{{ context.source.userName }} · {{ context.source.businessDate }}</strong></p>
      <p>加班 {{ context.rawOvertimeMinutes }} 分钟，抵扣早退 {{ context.offsetMinutes }} 分钟，已转休 {{ context.transferredMinutes }} 分钟。</p>
      <p v-if="context.status !== 'READY'" class="transfer-error">{{ context.reason || '来源或政策待核对' }}</p>
      <p v-else>当前最多可核定 <strong>{{ context.availableMinutes }}</strong> 分钟。</p>
      <label>本次转休分钟 <input v-model="minutes" type="number" min="1" step="1" :max="context.availableMinutes" :disabled="busy || !!attempt || context.status !== 'READY'"></label>
      <label>核定原因 <textarea v-model.trim="reason" maxlength="500" :disabled="busy || !!attempt" rows="2" /></label>
      <button type="button" :disabled="busy || !!attempt || context.status !== 'READY'" @click="confirmTransfer">确认核定</button>
      <details v-if="context.history.length" open><summary>该日结来源的核定记录</summary><ul>
        <li v-for="item in context.history" :key="item.transferId">
          <span>{{ item.action === 'REVERSE' ? '撤销' : '核定' }} {{ item.transferMinutes }} 分钟 · {{ item.createTime }} · {{ item.reason }}</span>
          <button v-if="item.action === 'APPLY' && !item.reversed" type="button" :disabled="busy || !!attempt" @click="reverse(item)">撤销本次核定</button>
        </li>
      </ul></details>
    </template>
    <p v-if="attempt" role="status">本次操作结果尚未确认，已保留原请求。请用同一次请求核对，避免重复分配。</p>
    <button v-if="attempt" type="button" :disabled="busy" @click="sendAttempt">核对同一次操作结果</button>
    <span slot="footer"><el-button :disabled="busy" @click="$emit('close')">关闭</el-button></span>
  </el-dialog>
</template>

<script>
import { getOvertimeTransferContext, confirmOvertimeTransfer, reverseOvertimeTransfer } from '@/api/oa/attendanceOvertimeTransfer'
import { listBalanceTypeOptions, listBalanceOvertimeSources } from '@/api/oa/attendanceLeaveBalanceOptions'
import { getSelectedDeptId } from '@/utils/shopContext'
const { exactId, dataOf, listOf, clone, requestId, permission, persistAttempt, restoreAttempt, clearAttempt, definiteRejection } = require('@/utils/leaveBalanceUi')
export default {
  name: 'OvertimeTransferDialog',
  props: { open: Boolean, source: { type: Object, default: () => ({}) } },
  data() { return { date: '', keyword: '', sources: [], page: 1, total: 0, sourceId: '', types: [], typeId: '', context: null, contextScope: '', sourcesScope: '', minutes: '', reason: '', busy: false, error: '', sequence: 0, attempt: null } },
  watch: { open(value) { this.reset(); if (value) this.initialize() }, source() { this.reset(); if (this.open) this.initialize() }, '$store.getters.id'() { this.reset() }, '$route.fullPath'() { this.reset() } },
  created() { this._deptHandler = () => this.reset(); if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this._deptHandler); if (this.open) this.initialize() },
  beforeDestroy() { this.reset(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this._deptHandler) },
  methods: {
    scope() { return JSON.stringify([getSelectedDeptId(), this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath]) },
    current(scope, seq) { return this.open && !this._isDestroyed && scope === this.scope() && seq === this.sequence },
    reset() { this.sequence++; this.contextScope = ''; this.sourcesScope = ''; this.context = null; this.sources = []; this.sourceId = ''; this.attempt = null; this.busy = false; this.error = '' },
    async initialize() {
      if (!permission(this, 'convert')) { this.error = '当前账号没有核定转休权限'; return }
      const scope = this.scope(), seq = ++this.sequence; this.busy = true
      this.date = String(this.source.businessDate || '').slice(0, 10); this.keyword = this.source.userName || ''; this.minutes = ''; this.reason = ''
      try {
        const rows = listOf(await listBalanceTypeOptions()).map(t => ({ ...t, leaveTypeId: exactId(t.leaveTypeId) }))
        if (!this.current(scope, seq)) return
        this.types = rows; this.typeId = (rows.find(t => t.typeCode === 'COMPENSATORY') || rows[0] || {}).leaveTypeId || ''
        const stored = restoreAttempt(scope, 'overtime')
        if (stored) { this.attempt = stored; this.sourceId = exactId(stored.sourceId); this.typeId = exactId(stored.typeId); this.reason = stored.body.reason; this.minutes = stored.body.transferMinutes || ''; return this.loadContext() }
        try { this.sourceId = exactId(this.source.dayResultId) } catch (_) { this.sourceId = '' }
        if (this.sourceId && this.typeId) return this.loadContext()
        if (this.date) return this.loadSources(1)
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '转休上下文读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async loadSources(nextPage) {
      if (this.attempt) return
      const scope = this.scope(), seq = ++this.sequence; this.busy = true; this.error = ''; this.context = null; this.sourceId = ''; this.sources = []
      try {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(this.date)) throw Error('请选择日结日期')
        const data = dataOf(await listBalanceOvertimeSources({ ownerDeptId: exactId(getSelectedDeptId()), dateFrom: this.date, dateTo: this.date, keyword: this.keyword, pageNum: nextPage, pageSize: 20 }))
        if (!this.current(scope, seq)) return
        if (!Array.isArray(data.rows) || !Number.isSafeInteger(data.total) || data.total < 0 || data.rows.length > 20) throw Error('日结候选结果无法确认')
        const rows = data.rows.map(row => ({ ...row, dayResultId: exactId(row.dayResultId), rowVersion: exactId(row.rowVersion, true) }))
        if (new Set(rows.map(r => r.dayResultId)).size !== rows.length) throw Error('日结候选记录重复')
        this.sources = rows; this.sourcesScope = scope; this.page = nextPage; this.total = data.total
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '日结来源读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    chooseSource(row) { if (this.busy || this.attempt || !this.sources.includes(row) || this.sourcesScope !== this.scope()) return; this.sourceId = exactId(row.dayResultId); this.loadContext() },
    async loadContext() {
      if (!this.sourceId || !this.typeId) return
      const scope = this.scope(), seq = ++this.sequence, sourceId = this.sourceId, typeId = this.typeId; this.busy = true; this.context = null; this.error = ''
      try {
        const data = dataOf(await getOvertimeTransferContext(exactId(sourceId), exactId(typeId), { silentError: true }))
        if (!this.current(scope, seq) || sourceId !== this.sourceId || typeId !== this.typeId) return
        if (!data.source || exactId(data.source.dayResultId) !== sourceId || exactId(data.source.shopId) !== exactId(getSelectedDeptId()) || !Array.isArray(data.history)) throw Error('日结上下文不匹配')
        exactId(data.source.rowVersion, true)
        data.history.forEach(item => exactId(item.transferId))
        this.context = data; this.contextScope = scope
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '日结上下文读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async confirmTransfer() {
      if (this.busy || this.attempt || !permission(this, 'convert') || !this.context || this.contextScope !== this.scope() || this.context.status !== 'READY') return
      const scope = this.scope(), seq = this.sequence, context = this.context
      try {
        const minutes = Number(this.minutes), reason = this.reason.trim()
        if (!Number.isSafeInteger(minutes) || minutes <= 0 || minutes > context.availableMinutes) throw Error('核定分钟数须为当前可用范围内的正整数')
        if (!reason) throw Error('请填写核定原因')
        const body = { sourceDayResultId: exactId(context.source.dayResultId), sourceVersion: exactId(context.source.rowVersion, true), leaveTypeId: exactId(this.typeId), transferMinutes: minutes, clientRequestId: requestId(), reason }
        this.busy = true
        await this.$confirm(`核定 ${context.source.userName} ${context.source.businessDate} 的 ${minutes} 分钟加班转调休？`, '核定转休')
        if (!this.current(scope, seq) || context !== this.context) return
        this.attempt = { scope, action: 'apply', sourceId: body.sourceDayResultId, typeId: body.leaveTypeId, body: clone(body) }
        persistAttempt(scope, 'overtime', this.attempt); this.busy = false; return this.sendAttempt()
      } catch (error) { if (this.current(scope, seq) && error && error.message) this.error = error.message }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async reverse(item) {
      if (this.busy || this.attempt || !permission(this, 'convert') || !this.context || this.contextScope !== this.scope() || !this.context.history.includes(item) || item.action !== 'APPLY' || item.reversed) return
      const scope = this.scope(), seq = this.sequence
      try {
        this.busy = true
        const result = await this.$prompt('请填写撤销原因；已使用或审批占用的额度不能撤销。', '撤销核定', { inputValidator: value => !!String(value || '').trim() || '请填写原因', inputPattern: /^[\s\S]{1,500}$/ })
        if (!this.current(scope, seq)) return
        this.attempt = { scope, action: 'reverse', transferId: exactId(item.transferId), sourceId: this.sourceId, typeId: this.typeId, body: { clientRequestId: requestId(), reason: result.value.trim() } }
        persistAttempt(scope, 'overtime', this.attempt); this.busy = false; return this.sendAttempt()
      } catch (error) { if (this.current(scope, seq) && error && error.message) this.error = error.message }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async sendAttempt() {
      if (this.busy || !this.attempt || !permission(this, 'convert')) return
      if (this.attempt.scope !== this.scope()) { this.error = '账号或组织已变化，请切回原操作页面核对'; return }
      const scope = this.scope(), seq = ++this.sequence, attempt = clone(this.attempt); this.busy = true; this.error = ''
      try {
        persistAttempt(scope, 'overtime', attempt)
        const response = attempt.action === 'apply' ? await confirmOvertimeTransfer(clone(attempt.body)) : await reverseOvertimeTransfer(exactId(attempt.transferId), clone(attempt.body))
        if (!this.current(scope, seq)) return
        const data = dataOf(response); exactId(data.transferId)
        if (String(data.sourceDayResultId) !== attempt.sourceId || data.clientRequestId !== attempt.body.clientRequestId) throw Error('核定结果无法确认')
        clearAttempt(scope, 'overtime'); this.attempt = null; this.minutes = ''; this.reason = ''; this.$emit('changed'); return this.loadContext()
      } catch (error) { if (this.current(scope, seq)) { this.error = error.message || '结果未确认，请用原请求核对'; if (definiteRejection(error)) { clearAttempt(scope, 'overtime'); this.attempt = null; this.context = null } } }
      finally { if (this.current(scope, seq)) this.busy = false }
    }
  }
}
</script>

<style scoped>
.transfer-controls{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0}label{display:block;margin:10px 0}input,select,textarea{padding:7px;max-width:100%;border:1px solid #cbd5e1;border-radius:5px}textarea{display:block;width:100%}button{padding:6px 10px;border:1px solid #94a3b8;background:white;border-radius:5px}button:disabled{opacity:.5}.transfer-error{color:#a33817}.source-list{padding-left:0;list-style:none}li{margin:10px 0;display:flex;justify-content:space-between;gap:12px}
</style>
