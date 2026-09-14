<template>
  <section class="customer-service-history">
    <div class="history-heading"><h4>历史服务记录</h4><span>{{ total == null ? '数量待确认' : '共 ' + total + ' 条' }}</span></div>
    <el-form inline size="mini" @submit.native.prevent="search">
      <el-form-item><el-input v-model="keyword" clearable maxlength="100" placeholder="搜索茶饮、偏好或备注" @clear="search" /></el-form-item>
      <el-form-item><el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px" /></el-form-item>
      <el-form-item><el-button native-type="submit" :disabled="loading">筛选</el-button><el-button :disabled="loading" @click="search">刷新</el-button></el-form-item>
    </el-form>
    <p v-if="newAvailable" class="history-tip">服务资料已更新，当前浏览位置已保留。<el-button type="text" @click="search">刷新记录</el-button></p>
    <article v-for="record in records" :key="String(record.recordId)">
      <div><strong>{{ record.teaServed || '到店服务' }}</strong><time>{{ record.serviceDate }}</time></div>
      <p>{{ record.serviceNote || record.preferenceSnapshot || '无服务备注' }}</p>
      <small>{{ record.serviceUserName || record.createBy || '-' }} · {{ record.partySize || '-' }} 人 · {{ record.consumptionAmount == null ? '金额未记录' : '¥' + record.consumptionAmount }}</small>
    </article>
    <p v-if="error" role="alert">{{ error }} <el-button type="text" :disabled="loading" @click="loadMore">重试</el-button></p>
    <p v-else-if="!loading && total === 0">暂无符合条件的服务记录</p>
    <el-button v-if="hasMore && !error" :loading="loading" @click="loadMore">加载更早记录</el-button>
    <p v-else-if="loading" role="status">正在加载历史记录…</p>
  </section>
</template>

<script>
import { listCustomerServiceRecords } from '@/api/inventory/customer'
const { getSelectedDeptId } = require('@/utils/shopContext')
const { createUiOperationScope } = require('@/utils/uiOperationScope')
export default {
  name: 'CustomerServiceHistory',
  props: { customerId: [String, Number], active: { type: Boolean, default: true }, refreshToken: [String, Number] },
  data() { return { records: [], total: null, hasMore: true, keyword: '', dateRange: [], appliedFilter: {}, cursor: {}, loading: false, error: '', revision: 0, newAvailable: false } },
  watch: {
    customerId() { this.search() },
    active(value) { if (value) this.search(); else this.reset() },
    refreshToken() { if (this.records.length > 20) this.newAvailable = true; else this.search() },
    '$store.getters.id'() { this.search() },
    '$store.getters.token'() { this.search() }
  },
  created() { this._contextChanged = () => this.search(); window.addEventListener('erp:dept-changed', this._contextChanged); this.search() },
  beforeDestroy() { window.removeEventListener('erp:dept-changed', this._contextChanged); this.scope().deactivate() },
  methods: {
    scope() {
      if (!this._scope) this._scope = createUiOperationScope(() => ({
        actorId: String(this.$store.getters.id || ''), deptId: String(getSelectedDeptId() || ''),
        customerId: String(this.customerId || ''), active: this.active, revision: this.revision
      }))
      return this._scope
    },
    reset() {
      this.revision += 1; this.scope().invalidate(); this.records = []; this.total = null; this.hasMore = true
      this.cursor = {}; this.loading = false; this.error = ''; this.newAvailable = false
    },
    search() {
      this.reset(); this.appliedFilter = { keyword: this.keyword.trim(), dateFrom: this.dateRange && this.dateRange[0], dateTo: this.dateRange && this.dateRange[1] }
      return this.loadMore()
    },
    async loadMore() {
      if (!this.active || !this.customerId || this.loading || !this.hasMore) return
      const scope = this.scope(), operation = scope.begin('records'), customerId = String(this.customerId)
      const query = { ...this.appliedFilter, ...this.cursor, pageSize: 20 }
      this.loading = true; this.error = ''
      try {
        const response = await listCustomerServiceRecords(customerId, query)
        if (!scope.isCurrent(operation)) return
        const page = response && response.data
        if (!page || !Array.isArray(page.records) || page.records.some(row => String(row.customerId) !== customerId || !/^\d+$/.test(String(row.recordId || ''))) || !Number.isSafeInteger(Number(page.total)) || Number(page.total) < 0 || page.total == null)
          throw Error('历史记录响应未确认')
        if (!/^\d+$/.test(String(page.snapshotMaxRecordId)) || (page.hasMore === true && (!page.records.length ||
          !/^\d+$/.test(String(page.nextRecordId || '')) || !/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(String(page.nextServiceDate || '')) ||
          (String(page.nextRecordId) === String(this.cursor.beforeRecordId) && page.nextServiceDate === this.cursor.beforeServiceDate))))
          throw Error('历史记录翻页位置未确认')
        const seen = new Set(this.records.map(row => String(row.recordId)))
        this.records = this.records.concat(page.records.filter(row => !seen.has(String(row.recordId))))
        this.total = Number(page.total); this.hasMore = page.hasMore === true
        this.cursor = { snapshotMaxRecordId: page.snapshotMaxRecordId, beforeServiceDate: page.nextServiceDate, beforeRecordId: page.nextRecordId }
      } catch (error) {
        if (scope.isCurrent(operation)) this.error = '历史记录加载失败，已保留当前记录和翻页位置。'
      } finally { if (scope.isCurrent(operation)) this.loading = false }
    }
  }
}
</script>

<style scoped>
.history-heading { display:flex; justify-content:space-between; align-items:center; }
.history-heading span,.history-tip,small { color:#606266; }
article { padding:14px 0; border-bottom:1px solid #e5e7eb; }
article div { display:flex; justify-content:space-between; gap:12px; }
article p { white-space:pre-wrap; line-height:1.6; }
time { color:#606266; font-size:12px; }
.customer-service-history>.el-button { margin-top:12px; }
</style>
