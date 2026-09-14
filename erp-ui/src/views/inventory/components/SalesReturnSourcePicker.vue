<template>
  <section class="sales-source-picker" aria-label="查找可退原销售单">
    <div class="source-filters">
      <label>客户<input v-model.trim="query.customerName" type="search" maxlength="100" placeholder="客户名称" @keydown.enter.prevent="search"></label>
      <label>订单号<input v-model.trim="query.orderNo" type="search" maxlength="100" placeholder="销售订单号" @keydown.enter.prevent="search"></label>
      <label>开始日期<input v-model="query.startDate" type="date"></label>
      <label>结束日期<input v-model="query.endDate" type="date"></label>
    </div>
    <div class="source-actions">
      <button type="button" @click="search">{{ loading ? '重新搜索' : '搜索' }}</button>
      <button type="button" @click="reset">重置</button>
    </div>
    <p v-if="loading" role="status">正在查询可退原单…</p>
    <div v-else-if="loadError" role="alert"><p>{{ loadError }}</p><button type="button" @click="loadPage">重试本页</button></div>
    <template v-else>
      <p v-if="!rows.length">没有符合条件的可退原单，请调整客户、订单号或日期。</p>
      <ul v-else class="source-orders">
        <li v-for="row in rows" :key="row.orderId">
          <button type="button" :disabled="!canSelect" @click="choose(row)">
            <strong>{{ row.orderNo || row.orderId }}</strong>
            <span>{{ row.customerName || '未命名客户' }}</span>
            <small>{{ String(row.orderDate || '').slice(0, 10) }} · 选择此原单</small>
          </button>
        </li>
      </ul>
      <p v-if="rows.length && !canSelect" role="status">筛选条件或当前单据已变化，请重新搜索。</p>
    </template>
    <nav class="source-pages" aria-label="可退原单分页">
      <button type="button" :disabled="loading || query.pageNum <= 1" @click="changePage(query.pageNum - 1)">上一页</button>
      <span>第 {{ query.pageNum }} 页 · 共 {{ total }} 单</span>
      <button type="button" :disabled="loading || query.pageNum * query.pageSize >= total" @click="changePage(query.pageNum + 1)">下一页</button>
    </nav>
  </section>
</template>

<script>
import { listSalesReturnSourceOrders } from "@/api/inventory/salesReturn"
import { getSelectedDeptId } from "@/utils/shopContext"
const emptyQuery = () => ({ pageNum: 1, pageSize: 20, orderNo: "", customerName: "", startDate: "", endDate: "" })
function exactId(value) {
  if (typeof value === "number" && !Number.isSafeInteger(value)) return null
  if (typeof value !== "number" && typeof value !== "string") return null
  const id = String(value)
  return /^[1-9]\d{0,18}$/.test(id) && (id.length < 19 || id <= "9223372036854775807") ? id : null
}
export default {
  name: "SalesReturnSourcePicker",
  props: { contextKey: { type: String, default: "" }, active: { type: Boolean, default: true } },
  data() { return { query: emptyQuery(), rows: [], total: 0, loading: false, loadError: "", sequence: 0, loadedScope: "", loadedQuery: "", disposed: false } },
  computed: {
    canSelect() {
      return this.isSelectionCurrent()
    }
  },
  watch: {
    contextKey() { this.resetContext() },
    active(value) { this.invalidate(); if (value) this.search() },
    "$store.getters.id"() { this.resetContext() },
    "$route.fullPath"() { this.resetContext() }
  },
  created() { if (this.active) this.loadPage() },
  mounted() { if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this.resetContext) },
  beforeDestroy() {
    this.disposed = true; this.invalidate()
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.resetContext)
  },
  methods: {
    scope() {
      return JSON.stringify([this.contextKey, getSelectedDeptId(),
        this.$store && this.$store.getters && this.$store.getters.id, this.$route && this.$route.fullPath])
    },
    invalidate() { this.sequence += 1; this.rows = []; this.total = 0; this.loading = false; this.loadedScope = ""; this.loadedQuery = "" },
    resetContext() { this.invalidate(); this.loadError = ""; this.query = emptyQuery() },
    search() { this.query.pageNum = 1; return this.loadPage() },
    reset() { this.query = emptyQuery(); return this.loadPage() },
    changePage(page) { if (!Number.isSafeInteger(page) || page < 1 || this.loading) return; this.query.pageNum = page; return this.loadPage() },
    async loadPage() {
      if (!this.active || this.disposed) return
      const query = { ...this.query }, snapshot = JSON.stringify(query), scope = this.scope(), sequence = ++this.sequence
      this.rows = []; this.loadedScope = ""; this.loadedQuery = ""; this.loadError = ""
      if ((query.startDate && !/^\d{4}-\d{2}-\d{2}$/.test(query.startDate)) ||
          (query.endDate && !/^\d{4}-\d{2}-\d{2}$/.test(query.endDate)) ||
          (query.startDate && query.endDate && query.startDate > query.endDate)) {
        this.loading = false; this.loadError = "请核对开始和结束日期"; return
      }
      this.loading = true
      const current = () => sequence === this.sequence && !this.disposed && this.active && scope === this.scope() && snapshot === JSON.stringify(this.query)
      try {
        const response = await listSalesReturnSourceOrders(query, { silentError: true })
        if (!current()) return
        const rawTotal = response && response.total
        if (!response || !Array.isArray(response.rows) || response.rows.length > query.pageSize ||
            !(typeof rawTotal === "number" || (typeof rawTotal === "string" && /^\d+$/.test(rawTotal))) ||
            !Number.isSafeInteger(Number(rawTotal)) || Number(rawTotal) < 0) throw Error("原单分页响应不完整，请重试")
        const rows = response.rows.map(row => ({ ...row, orderId: exactId(row.orderId) }))
        if (rows.some(row => !row.orderId) || new Set(rows.map(row => row.orderId)).size !== rows.length) throw Error("原单编号无效，请重新加载")
        this.rows = rows; this.total = Number(rawTotal); this.loadedScope = scope; this.loadedQuery = snapshot
        if (!rows.length && this.total > 0 && query.pageNum > Math.ceil(this.total / query.pageSize)) {
          this.query.pageNum = Math.ceil(this.total / query.pageSize); return this.loadPage()
        }
      } catch (error) {
        if (current()) { this.rows = []; this.loadError = error && error.message ? error.message : "可退原单加载失败，请重试" }
      } finally { if (sequence === this.sequence) this.loading = false }
    },
    isSelectionCurrent() {
      return this.active && !this.disposed && !this.loading && !this.loadError &&
        this.loadedScope === this.scope() && this.loadedQuery === JSON.stringify(this.query)
    },
    choose(row) {
      if (!this.isSelectionCurrent() || !this.rows.includes(row)) return
      this.loadedScope = ""
      this.$emit("select", { ...row })
    }
  }
}
</script>

<style scoped>
.sales-source-picker { min-width: 0; color: #303133; }
.source-filters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.source-filters label { display: flex; flex-direction: column; gap: 5px; font-size: 13px; }
.source-filters input { box-sizing: border-box; min-width: 0; width: 100%; min-height: 38px; border: 1px solid #c0c4cc; border-radius: 5px; padding: 6px; }
.source-actions, .source-pages { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-top: 12px; }
.sales-source-picker button { cursor: pointer; min-height: 38px; padding: 7px 12px; border: 1px solid #c0c4cc; border-radius: 5px; background: #fff; color: #303133; }
.sales-source-picker button:disabled { cursor: default; opacity: .55; }
.sales-source-picker button:focus-visible, .source-filters input:focus-visible { outline: 2px solid #409eff; outline-offset: 2px; }
.source-orders { max-height: 340px; overflow-y: auto; margin: 12px 0; padding: 0; list-style: none; }
.source-orders li + li { margin-top: 8px; }
.source-orders button { display: flex; flex-direction: column; align-items: flex-start; gap: 5px; width: 100%; text-align: left; overflow-wrap: anywhere; }
.source-orders small { color: #606266; }
.source-pages { font-size: 12px; }
</style>

