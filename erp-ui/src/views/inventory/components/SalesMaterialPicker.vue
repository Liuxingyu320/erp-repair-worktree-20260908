<template>
  <el-dialog title="批量选择销售物料" :visible="open" width="min(820px, 96vw)" append-to-body :close-on-click-modal="false" :before-close="close">
    <el-radio-group v-model="type" size="small" :disabled="confirming" @change="search"><el-radio-button label="product">商品</el-radio-button><el-radio-button label="gift">礼盒</el-radio-button></el-radio-group>
    <el-input v-model="keyword" aria-label="搜索物料名称或编码" placeholder="名称或编码" clearable :disabled="confirming" @keyup.enter.native="search" @clear="search" style="width:260px;margin:12px"><el-button slot="append" icon="el-icon-search" @click="search" /></el-input>
    <div v-if="recent.length" class="mb12">最近使用：<el-button v-for="item in recent" :key="item.type + ':' + item.id" size="mini" :disabled="confirming" @click="selectRecent(item)">{{ item.label }}</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button type="text" @click="load">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" border size="small" max-height="420">
      <el-table-column label="选择" width="70"><template slot-scope="scope"><el-checkbox :value="!!selected[keyOf(scope.row)]" :aria-label="'选择 ' + scope.row.itemName" :disabled="confirming" @change="value => toggle(scope.row, value)" /></template></el-table-column>
      <el-table-column label="物料名称" prop="itemName" min-width="160"/><el-table-column label="编码" prop="itemCode" min-width="140"/><el-table-column label="单位" prop="unit" width="80"/><el-table-column label="当前售价" prop="salesPrice" width="100"/>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="page" :limit.sync="pageSize" @pagination="load"/>
    <div>已选 {{ Object.keys(selected).length }} 项；切换类别或翻页保留选择。</div>
    <div slot="footer"><el-button :disabled="confirming" @click="close">取消</el-button><el-button type="primary" :loading="confirming" :disabled="!Object.keys(selected).length" @click="confirm">添加所选物料</el-button></div>
  </el-dialog>
</template>
<script>
import { listProduct, getProduct } from '@/api/inventory/product'
import { listGift, getGift } from '@/api/inventory/gift'
import InventoryItemSelect from './InventoryItemSelect.vue'
export default {
  name: 'SalesMaterialPicker',
  props: { open: Boolean, contextKey: String, recent: { type: Array, default: () => [] } },
  data: () => ({ type: 'product', keyword: '', page: 1, pageSize: 20, total: 0, rows: [], selected: {}, loading: false, confirming: false, error: '', sequence: 0, epoch: 0 }),
  watch: {
    open(value) { this.epoch += 1; this.sequence += 1; this.confirming = false; if (value) { this.selected = {}; this.search() } },
    contextKey() { this.epoch += 1; this.sequence += 1; this.selected = {}; this.$emit('close') }
  },
  beforeDestroy() { this.epoch += 1; this.sequence += 1 },
  methods: {
    keyOf: item => item.itemType + ':' + item.itemId,
    normalize(raw, type) { return InventoryItemSelect.methods.normalizeItem.call({ normalizedType: type, hasValue: value => value !== undefined && value !== null && value !== '' }, raw) },
    close() { if (!this.confirming) this.$emit('close') },
    search() { this.page = 1; return this.load() },
    load() {
      const sequence = ++this.sequence, epoch = this.epoch, type = this.type
      const current = () => this.open && sequence === this.sequence && epoch === this.epoch
      const query = { pageNum: this.page, pageSize: this.pageSize, status: '0', keyword: this.keyword || undefined }
      this.loading = true; this.error = ''; this.rows = []
      return (type === 'gift' ? listGift : listProduct)(query, { silentError: true }).then(res => { if (current()) { this.rows = (res.rows || []).map(raw => this.normalize(raw, type)); this.total = res.total || 0 } })
        .catch(error => { if (current()) this.error = error.message || '物料加载失败' }).finally(() => { if (current()) this.loading = false })
    },
    toggle(item, selected) { if (selected) this.$set(this.selected, this.keyOf(item), item); else this.$delete(this.selected, this.keyOf(item)) },
    async latest(item) {
      const res = await (item.itemType === 'gift' ? getGift : getProduct)(item.itemId, { silentError: true })
      if (!res.data || String(res.data.status) !== '0') throw new Error('物料已停用或无权读取，请重新选择')
      return this.normalize(res.data, item.itemType)
    },
    async selectRecent(item) {
      const epoch = this.epoch
      try { const latest = await this.latest({ itemId: item.id, itemType: item.type }); if (this.open && epoch === this.epoch) this.toggle(latest, true) }
      catch (error) { if (this.open && epoch === this.epoch) this.error = error.message }
    },
    async confirm() {
      if (this.confirming) return
      const selected = Object.values(this.selected), epoch = this.epoch
      if (selected.length > 50) { this.error = '每次最多添加 50 项，请分批选择'; return }
      this.confirming = true
      try {
        const items = await Promise.all(selected.map(item => this.latest(item)))
        if (this.open && epoch === this.epoch) this.$emit('confirm', items)
      } catch (error) { if (this.open && epoch === this.epoch) this.error = error.message || '最新价格核对失败，选择已保留' }
      finally { if (epoch === this.epoch) this.confirming = false }
    }
  }
}
</script>
