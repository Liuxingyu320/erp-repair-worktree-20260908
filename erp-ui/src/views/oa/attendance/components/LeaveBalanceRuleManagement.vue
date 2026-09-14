<template>
  <section v-if="allowed" aria-label="假期额度政策配置">
    <p>HR 按公司和档案工作地维护规则。员工的适用规则由系统匹配；保存草稿后需要单独发布。</p>
    <p v-if="error" class="rule-error" role="alert">{{ error }}</p>
    <div class="rule-toolbar"><button type="button" :disabled="busy" @click="load">刷新规则与映射</button><button type="button" :disabled="busy || !companies.length || !types.length || !!unknownWrite" @click="newRule">新建额度规则</button><button type="button" :disabled="busy || !companies.length || !!unknownWrite" @click="newLocation">新增工作地映射</button></div>
    <p v-if="unknownWrite" role="status">上次{{ unknownWrite }}结果尚未确认。请刷新列表核对记录，当前表单保留，系统不会自动重复创建。</p>
    <table class="rule-table"><thead><tr><th>规则</th><th>公司 / 工作地</th><th>生效期间</th><th>版本 / 状态</th><th>操作</th></tr></thead><tbody>
      <tr v-for="rule in rules" :key="rule.ruleId"><td>{{ rule.name }}</td><td>{{ companyName(rule.legalEntityId) }} / {{ rule.locationCode }}</td><td>{{ rule.effectiveFrom }} 至 {{ rule.effectiveTo }}</td><td>{{ rule.version }} / {{ rule.status === 'PUBLISHED' ? '已发布' : '草稿' }}</td><td>
        <button type="button" :disabled="busy || !!unknownWrite" @click="editRule(rule)">{{ rule.status === 'PUBLISHED' ? '创建下一版本' : '编辑草稿' }}</button>
        <button v-if="rule.status === 'DRAFT'" type="button" :disabled="busy || !!unknownWrite" @click="publish(rule)">复核并发布</button>
      </td></tr>
    </tbody></table>
    <h3>档案工作地映射</h3>
    <table class="rule-table"><thead><tr><th>公司</th><th>档案工作地原文</th><th>工作地规则组</th><th>维护原因</th><th>操作</th></tr></thead><tbody><tr v-for="mapping in locations" :key="mapping.mappingId"><td>{{ companyName(mapping.legalEntityId) }}</td><td>{{ mapping.workLocation }}</td><td>{{ mapping.locationCode }}</td><td>{{ mapping.reason }}</td><td><button type="button" :disabled="busy || !!unknownWrite" @click="editLocation(mapping)">编辑</button></td></tr></tbody></table>
    <el-dialog :title="form.ruleId ? '编辑额度规则草稿' : form.previousRuleId ? '创建规则下一版本' : '新建额度规则草稿'" :visible.sync="ruleOpen" width="780px" append-to-body :close-on-click-modal="false">
      <form class="rule-form" @submit.prevent="saveRule">
        <p v-if="error" class="rule-error" role="alert">{{ error }}</p>
        <p v-if="unknownWrite" role="status">保存结果尚未确认，原内容已保留。请关闭此窗口并刷新列表核对，勿重复创建。</p>
        <fieldset :disabled="busy || !!unknownWrite">
          <label>规则名称<input v-model.trim="form.name" maxlength="128" required></label>
          <label>公司<select v-model="form.legalEntityId" :disabled="!!form.ruleId || !!form.previousRuleId" required><option value="" disabled>请选择</option><option v-for="company in companies" :key="company.legalEntityId" :value="company.legalEntityId">{{ company.legalEntityName }}</option></select></label>
          <label>假种<select v-model="form.leaveTypeId" :disabled="!!form.ruleId || !!form.previousRuleId" required><option value="" disabled>请选择</option><option v-for="type in types" :key="type.leaveTypeId" :value="type.leaveTypeId">{{ type.typeName }}</option></select></label>
          <label>工作地规则组<input v-model.trim="form.locationCode" maxlength="64" required placeholder="与下方工作地映射中的规则组保持一致"></label>
          <label>生效日期<input v-model="form.effectiveFrom" type="date" required></label><label>结束日期<input v-model="form.effectiveTo" type="date" required></label>
          <label>匹配优先级（0至10000）<input v-model="form.priority" type="number" min="0" max="10000" step="1" required></label>
          <label v-for="field in settings" :key="field.key">{{ field.label }}
            <select v-if="field.options" v-model="form.config[field.key]" required @change="changedSetting(field.key)"><option value="" disabled>请明确选择</option><option v-for="option in field.options" :key="option[0]" :value="option[0]">{{ option[1] }}</option></select>
            <input v-else v-model.trim="form.config[field.key]" :type="field.integer ? 'number' : 'text'" :inputmode="field.integer ? 'numeric' : 'decimal'" :required="field.key !== 'minutesPerDay' || form.config.unit === 'DAYS'">
          </label>
          <label v-if="form.config.calculation === 'FIXED'">每年固定额度（{{ unitLabel(form.config.unit) }}）<input v-model.trim="form.config.amount" inputmode="decimal" required></label>
        </fieldset>
        <fieldset v-if="form.config.calculation === 'TENURE'" :disabled="busy || !!unknownWrite"><legend>年资档位（从0年连续覆盖，最后上限留空）</legend>
          <div v-for="(tier,index) in form.tiers" :key="index" class="rule-tier"><label>满几年<input v-model="tier.minYears" type="number" min="0" step="1" required></label><label>不满几年<input v-model="tier.maxYearsExclusive" type="number" min="1" step="1"></label><label>额度（{{ unitLabel(form.config.unit) }}）<input v-model.trim="tier.amount" inputmode="decimal" required></label><button type="button" @click="form.tiers.splice(index,1)">移除</button></div>
          <button type="button" @click="form.tiers.push({minYears:'',maxYearsExclusive:'',amount:''})">添加档位</button>
        </fieldset>
        <p v-if="form.config.leaveCategory === 'COMPENSATORY'">调休只接受主管核定的已日结加班，不按固定数额或年资发放。</p>
        <button type="submit" :disabled="busy || !!unknownWrite">保存草稿</button>
      </form>
    </el-dialog>
    <el-dialog title="档案工作地映射" :visible.sync="locationOpen" width="540px" append-to-body :close-on-click-modal="false">
      <form class="rule-form" @submit.prevent="saveLocation"><p v-if="error" class="rule-error" role="alert">{{ error }}</p><p v-if="unknownWrite" role="status">保存结果尚未确认，原内容已保留。请关闭此窗口并刷新列表核对。</p><fieldset :disabled="busy || !!unknownWrite">
        <label>公司<select v-model="locationForm.legalEntityId" :disabled="!!locationForm.mappingId" required><option value="" disabled>请选择</option><option v-for="company in companies" :key="company.legalEntityId" :value="company.legalEntityId">{{ company.legalEntityName }}</option></select></label>
        <label>员工档案的工作地原文<input v-model.trim="locationForm.workLocation" maxlength="160" required></label>
        <label>对应工作地规则组<input v-model.trim="locationForm.locationCode" maxlength="64" required></label>
        <label>维护原因<textarea v-model.trim="locationForm.reason" maxlength="500" required rows="3" /></label>
      </fieldset><button type="submit" :disabled="busy || !!unknownWrite">保存映射</button></form>
    </el-dialog>
  </section>
</template>

<script>
import { listLeaveBalanceRules, getLeaveBalanceRule, createLeaveBalanceRule, updateLeaveBalanceRule, publishLeaveBalanceRule, listLeaveBalanceLocations, createLeaveBalanceLocation, updateLeaveBalanceLocation } from '@/api/oa/attendanceLeaveBalance'
import { listBalanceCompanyOptions, listBalanceTypeOptions } from '@/api/oa/attendanceLeaveBalanceOptions'
import { getSelectedDeptId } from '@/utils/shopContext'
const { exactId, decimal, dataOf, listOf, clone, unitLabel, permission, persistAttempt, restoreAttempt, clearAttempt, definiteRejection } = require('@/utils/leaveBalanceUi')
const SETTINGS = [
  { key: 'leaveCategory', label: '额度来源类别', options: [['ANNUAL','年假'],['COMPENSATORY','主管核定调休'],['OTHER','其他已配置额度']] },
  { key: 'calculation', label: '计算方式', options: [['FIXED','每年固定额度'],['TENURE','按年资档位'],['SOURCE_ONLY','仅按核定来源']] },
  { key: 'unit', label: '额度单位', options: [['DAYS','天'],['HOURS','小时'],['MINUTES','分钟']] },
  { key: 'minutesPerDay', label: '每天折合分钟（按天必填）' },
  { key: 'tenureBasis', label: '年资依据', options: [['WORK_START','首次参加工作日期'],['ENTRY','本公司入职日期']] },
  { key: 'tenureAt', label: '年资核算时点', options: [['PERIOD_START','当年开始'],['AS_OF_DATE','计算当天']] },
  { key: 'proration', label: '入职当年折算', options: [['NONE','不按日折算'],['CALENDAR_DAYS','按当年日历天折算']] },
  { key: 'grantTiming', label: '发放方式', options: [['UPFRONT','按规则提前发放'],['EARNED_DAILY','按已服务天数累积']] },
  { key: 'grantStepMinutes', label: '额度计算步长（分钟）' },
  { key: 'rounding', label: '步长舍入方式', options: [['DOWN','向下'],['HALF_UP','四舍五入'],['UP','向上']] },
  { key: 'expiryMonthsAfterYear', label: '年末后保留月数（0至120）', integer: true },
  { key: 'carryLimit', label: '到期可结转上限（同额度单位，0表示不结转）' },
  { key: 'carryExpiryMonths', label: '结转后保留月数（0至120）', integer: true }
]
const emptyRule = () => ({ ruleId: '', previousRuleId: '', legalEntityId: '', leaveTypeId: '', ownerDeptId: '', rowVersion: '', name: '', locationCode: '', effectiveFrom: '', effectiveTo: '', priority: '', config: Object.fromEntries(SETTINGS.map(field => [field.key,'']).concat([['amount','']])), tiers: [] })
const integer = (value, max) => { const text = String(value); if (!/^(0|[1-9]\d*)$/.test(text) || !Number.isSafeInteger(Number(text)) || Number(text) > max) throw Error('请填写范围内的整数'); return Number(text) }
export default {
  name: 'LeaveBalanceRuleManagement',
  data() { return { companies: [], types: [], rules: [], locations: [], busy: false, sequence: 0, error: '', ruleOpen: false, locationOpen: false, form: emptyRule(), locationForm: {}, unknownWrite: '', unknownPayload: null, loadedScope: '', formScope: '', locationScope: '' } },
  computed: { allowed() { return permission(this, 'rule') }, settings() { return SETTINGS } },
  watch: { '$store.getters.id'() { this.reset(); this.load() }, '$route.fullPath'() { this.reset() } },
  created() { this._deptHandler = () => { this.reset(); this.load() }; if (typeof window !== 'undefined') window.addEventListener('erp:dept-changed', this._deptHandler); const pending = restoreAttempt(this.scope(),'rule-write'); if(pending){this.unknownWrite=pending.kind==='rule'?'规则保存':'工作地映射保存';this.unknownPayload=pending} this.load() },
  beforeDestroy() { this.reset(); if (typeof window !== 'undefined') window.removeEventListener('erp:dept-changed', this._deptHandler) },
  methods: {
    scope() { return JSON.stringify([getSelectedDeptId(), this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath]) },
    current(scope, seq) { return !this._isDestroyed && scope === this.scope() && seq === this.sequence },
    reset() { this.sequence++; this.rules = []; this.locations = []; this.companies = []; this.types = []; this.busy = false; this.ruleOpen = false; this.locationOpen = false; this.error = ''; this.loadedScope = ''; this.formScope = ''; this.locationScope = ''; this.unknownPayload = restoreAttempt(this.scope(),'rule-write'); this.unknownWrite = this.unknownPayload ? '保存' : '' },
    async load() {
      if (!this.allowed || this.busy) return
      const scope = this.scope(), seq = ++this.sequence; this.busy = true; this.error = ''
      try {
        const results = await Promise.all([listBalanceCompanyOptions(exactId(getSelectedDeptId())), listBalanceTypeOptions(), listLeaveBalanceRules({ silentError: true }), listLeaveBalanceLocations({ silentError: true })])
        if (!this.current(scope, seq)) return
        const companies = listOf(results[0]).map(row => ({ ...row, legalEntityId: exactId(row.legalEntityId) })), types = listOf(results[1]).map(row => ({ ...row, leaveTypeId: exactId(row.leaveTypeId) }))
        const rules = listOf(results[2]).map(row => ({ ...row, ruleId: exactId(row.ruleId), rowVersion: exactId(row.rowVersion, true) })), locations = listOf(results[3]).map(row => ({ ...row, mappingId: exactId(row.mappingId), rowVersion: exactId(row.rowVersion, true) }))
        this.companies = companies; this.types = types; this.rules = rules; this.locations = locations; this.loadedScope = scope
        if (this.unknownWrite) this.reconcileUnknown()
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '政策读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    companyName(id) { return (this.companies.find(c => String(c.legalEntityId) === String(id)) || {}).legalEntityName || '所属公司' },
    newRule() { if (this.busy || this.unknownWrite || this.loadedScope !== this.scope()) return; this.formScope = this.scope(); this.form = emptyRule(); this.form.ownerDeptId = exactId(getSelectedDeptId()); this.ruleOpen = true },
    async editRule(row) {
      if (this.busy || this.unknownWrite || !this.rules.includes(row) || this.loadedScope !== this.scope()) return
      const scope = this.scope(), seq = ++this.sequence; this.busy = true; this.error = ''
      try {
        const rule = dataOf(await getLeaveBalanceRule(exactId(row.ruleId), { silentError: true }))
        if (!this.current(scope, seq)) return
        if (exactId(rule.ruleId) !== String(row.ruleId) || !rule.config || !Array.isArray(rule.tiers)) throw Error('规则详情无法确认')
        this.form = clone(rule); this.formScope = scope
        if (rule.status === 'PUBLISHED') { this.form.previousRuleId = exactId(rule.ruleId); this.form.ruleId = ''; this.form.rowVersion = '' }
        this.ruleOpen = true
      } catch (error) { if (this.current(scope, seq)) this.error = error.message || '规则读取失败' }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    changedSetting(key) {
      if (key === 'leaveCategory' && this.form.config.leaveCategory === 'COMPENSATORY') this.form.config.calculation = 'SOURCE_ONLY'
      if (this.form.config.calculation !== 'TENURE') this.form.tiers = []
      if (this.form.config.calculation === 'SOURCE_ONLY') this.form.config.amount = ''
    },
    rulePayload() {
      const source = this.form, body = { ownerDeptId: exactId(source.ownerDeptId), legalEntityId: exactId(source.legalEntityId), leaveTypeId: exactId(source.leaveTypeId), name: String(source.name || '').trim(), locationCode: String(source.locationCode || '').trim(), effectiveFrom: source.effectiveFrom, effectiveTo: source.effectiveTo, priority: integer(source.priority,10000), config: {}, tiers: [] }
      if (!body.name || !body.locationCode || !/^\d{4}-\d{2}-\d{2}$/.test(body.effectiveFrom) || !/^\d{4}-\d{2}-\d{2}$/.test(body.effectiveTo) || body.effectiveFrom > body.effectiveTo) throw Error('请完整填写规则名称、工作地和生效区间')
      for (const field of SETTINGS) {
        const value = source.config[field.key]
        if (field.options) { if (!field.options.some(option => option[0] === value)) throw Error('请配置' + field.label); body.config[field.key] = value }
        else if (field.key === 'minutesPerDay' && (value == null || value === '') && source.config.unit !== 'DAYS') body.config[field.key] = null
        else body.config[field.key] = field.integer ? integer(value,120) : decimal(value)
      }
      if (body.config.leaveCategory === 'COMPENSATORY' && body.config.calculation !== 'SOURCE_ONLY') throw Error('调休只接受主管核定来源')
      if (body.config.calculation === 'FIXED') body.config.amount = decimal(source.config.amount)
      if (body.config.calculation === 'TENURE') body.tiers = source.tiers.map(tier => ({ minYears: integer(tier.minYears,1000), maxYearsExclusive: tier.maxYearsExclusive == null || tier.maxYearsExclusive === '' ? null : integer(tier.maxYearsExclusive,1000), amount: decimal(tier.amount) }))
      if (source.ruleId) body.rowVersion = exactId(source.rowVersion,true)
      if (source.previousRuleId) body.previousRuleId = exactId(source.previousRuleId)
      return body
    },
    async saveRule() {
      if (this.busy || this.unknownWrite || !this.allowed || this.formScope !== this.scope()) return
      const scope = this.scope(), seq = ++this.sequence
      let body, id, familyId
      try { body = this.rulePayload(); id = this.form.ruleId ? exactId(this.form.ruleId) : ''; familyId = body.previousRuleId ? exactId(this.form.familyId) : null } catch (error) { this.error = error.message; return }
      this.busy = true; this.error = ''
      let issued = false
      const pending = {scope,kind:'rule',id,familyId,body:clone(body),existingIds:this.rules.map(row=>String(row.ruleId))}
      try {
        persistAttempt(scope,'rule-write',pending); issued = true
        const result = dataOf(await (id ? updateLeaveBalanceRule(id, clone(body)) : createLeaveBalanceRule(clone(body))))
        if (!this.current(scope, seq)) return
        exactId(result.ruleId); exactId(result.rowVersion,true)
        if (result.status !== 'DRAFT' || id && String(result.ruleId) !== id) throw Error('规则保存结果无法确认')
        clearAttempt(scope,'rule-write'); this.form = clone(result); this.ruleOpen = false; this.busy = false; return this.load()
      } catch (error) { if (this.current(scope, seq)) { this.error = error.message || '规则保存结果未确认'; if(!issued||definiteRejection(error)){clearAttempt(scope,'rule-write')}else{this.unknownWrite = '规则保存'; this.unknownPayload = pending} } }
      finally { if (this.current(scope, seq)) this.busy = false }
    },
    async publish(row) {
      if (this.busy || this.unknownWrite || !this.allowed || !this.rules.includes(row) || this.loadedScope !== this.scope() || row.status !== 'DRAFT') return
      const scope = this.scope(), seq = ++this.sequence, id = exactId(row.ruleId), version = exactId(row.rowVersion,true); this.busy = true; this.error = ''
      try {
        const rule = dataOf(await getLeaveBalanceRule(id, { silentError:true }))
        if (!this.current(scope,seq) || exactId(rule.ruleId) !== id || exactId(rule.rowVersion,true) !== version || rule.status !== 'DRAFT') throw Error('规则已变化，请刷新后复核')
        const config = rule.config || {}
        await this.$confirm(this.reviewRuleText(rule), '复核并发布政策', { customClass: 'leave-rule-publish-confirm' })
        if (!this.current(scope,seq)) return
        const result = dataOf(await publishLeaveBalanceRule(id,version))
        if (!this.current(scope,seq)) return
        if (exactId(result.ruleId) !== id || result.status !== 'PUBLISHED') throw Error('发布结果无法确认')
        this.busy = false; return this.load()
      } catch (error) { if (this.current(scope,seq) && error && error.message) this.error = error.message }
      finally { if (this.current(scope,seq)) this.busy = false }
    },
    newLocation() { if (this.busy || this.unknownWrite || this.loadedScope !== this.scope()) return; this.locationScope = this.scope(); this.locationForm = { ownerDeptId:exactId(getSelectedDeptId()),legalEntityId:'',workLocation:'',locationCode:'',reason:'' }; this.locationOpen = true },
    editLocation(row) { if (this.busy || this.unknownWrite || !this.locations.includes(row) || this.loadedScope !== this.scope()) return; this.locationScope = this.scope(); this.locationForm = clone(row); this.locationOpen = true },
    async saveLocation() {
      if (this.busy || this.unknownWrite || !this.allowed || this.locationScope !== this.scope()) return
      const scope = this.scope(), seq = ++this.sequence, form = this.locationForm
      let body,id
      try { body = {ownerDeptId:exactId(form.ownerDeptId),legalEntityId:exactId(form.legalEntityId),workLocation:String(form.workLocation||'').trim(),locationCode:String(form.locationCode||'').trim(),reason:String(form.reason||'').trim()}; if (!body.workLocation || !body.locationCode || !body.reason) throw Error('请完整填写工作地、规则组和原因'); id = form.mappingId ? exactId(form.mappingId) : ''; if (id) body.rowVersion=exactId(form.rowVersion,true) } catch(error) { this.error=error.message;return }
      this.busy=true;this.error=''
      let issued = false
      const pending={scope,kind:'location',id,body:clone(body),existingIds:this.locations.map(row=>String(row.mappingId))}
      try {
        persistAttempt(scope,'rule-write',pending); issued = true
        const result=dataOf(await (id?updateLeaveBalanceLocation(id,clone(body)):createLeaveBalanceLocation(clone(body))))
        if(!this.current(scope,seq))return
        exactId(result.mappingId);exactId(result.rowVersion,true)
        if(id&&String(result.mappingId)!==id)throw Error('映射保存结果无法确认')
        clearAttempt(scope,'rule-write');this.locationOpen=false;this.busy=false;return this.load()
      } catch(error) { if(this.current(scope,seq)){this.error=error.message||'映射保存结果未确认';if(!issued||definiteRejection(error)){clearAttempt(scope,'rule-write')}else{this.unknownWrite='工作地映射保存';this.unknownPayload=pending}} }
      finally {if(this.current(scope,seq))this.busy=false}
    },
    reconcileUnknown() {
      const attempt=this.unknownPayload;if(!attempt||attempt.scope!==this.scope()||this.loadedScope!==this.scope())return
      const candidates=attempt.kind==='rule'?this.rules:this.locations
      const same=(a,b)=>String(a==null?'':a)===String(b==null?'':b)
      const keys=attempt.kind==='rule'?['ownerDeptId','legalEntityId','leaveTypeId','name','locationCode','effectiveFrom','effectiveTo','priority']:['ownerDeptId','legalEntityId','workLocation','locationCode','reason']
      const canonical=value=>{if(Array.isArray(value))return value.map(canonical);if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).filter(k=>k!=='ruleId'&&value[k]!=null).sort().map(k=>[k,canonical(value[k])]));const text=String(value==null?'':value);return /^-?\d+(\.\d+)?$/.test(text)?text.replace(/(\.\d*?)0+$/,'$1').replace(/\.$/,''):text}
      const matches=candidates.filter(row=>{const id=String(row[attempt.kind==='rule'?'ruleId':'mappingId']);return(attempt.id?id===attempt.id:!(attempt.existingIds||[]).includes(id))&&keys.every(key=>same(row[key],attempt.body[key]))&&(!attempt.body.previousRuleId||attempt.familyId&&same(row.familyId,attempt.familyId))&&(attempt.kind!=='rule'||JSON.stringify(canonical(row.config))===JSON.stringify(canonical(attempt.body.config))&&JSON.stringify(canonical(row.tiers))===JSON.stringify(canonical(attempt.body.tiers)))})
      if(matches.length===1){clearAttempt(this.scope(),'rule-write');this.error='已在服务端找到对应记录，请重新打开并复核配置。';this.unknownWrite='';this.unknownPayload=null;this.ruleOpen=false;this.locationOpen=false}
    },
    reviewRuleText(rule) {
      const config=rule.config||{}
      const lines=[`发布「${rule.name}」第${rule.version}版？`,`${this.companyName(rule.legalEntityId)} / ${rule.locationCode}`,`${rule.effectiveFrom} 至 ${rule.effectiveTo}；匹配优先级 ${rule.priority}`]
      SETTINGS.forEach(field=>{const option=field.options&&field.options.find(o=>o[0]===config[field.key]);lines.push(field.label+'：'+(option?option[1]:config[field.key]==null?'未配置':config[field.key]))})
      if(config.calculation==='FIXED')lines.push('固定额度：'+config.amount+' '+unitLabel(config.unit))
      ;(rule.tiers||[]).forEach(t=>lines.push('年资 '+t.minYears+' 至 '+(t.maxYearsExclusive==null?'后续所有年资':t.maxYearsExclusive+' 年（不含）')+'：'+t.amount+' '+unitLabel(config.unit)))
      lines.push('发布后将参与员工额度匹配。');return lines.join('\n')
    },
    unitLabel
  }
}
</script>

<style scoped>
.rule-toolbar{display:flex;gap:10px;flex-wrap:wrap;margin:12px 0}.rule-table{width:100%;border-collapse:collapse;margin:16px 0}.rule-table td,.rule-table th{padding:9px;border-bottom:1px solid #e2e8f0;text-align:left}.rule-form fieldset{border:0;padding:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.rule-form label{display:flex;flex-direction:column;gap:5px}.rule-form input,.rule-form select,.rule-form textarea{width:100%;padding:7px;border:1px solid #cbd5e1;border-radius:5px}.rule-form button{margin:10px 0}button{padding:6px 9px;border:1px solid #94a3b8;background:white;border-radius:5px}button:disabled{opacity:.5}.rule-error{color:#a33817}.rule-tier{display:flex;gap:8px;grid-column:1/-1;align-items:center}@media(max-width:600px){.rule-form fieldset{grid-template-columns:1fr}.rule-table{display:block;overflow:auto}}
</style>

<style>
.leave-rule-publish-confirm .el-message-box__message {white-space:pre-line;max-height:65vh;overflow:auto}
</style>
