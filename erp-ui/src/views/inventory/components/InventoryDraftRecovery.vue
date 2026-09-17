<template>
  <div v-if="records.length || error" class="mb12">
    <el-alert v-if="error" :title="error" type="warning" :closable="false"><el-button type="text" @click="refresh">重新核对</el-button></el-alert>
    <el-alert v-for="record in records" :key="record.requestId" type="warning" :closable="false" show-icon
      :title="'上次' + (record.action === 'submit' ? '保存并提交' : '保存草稿') + '的结果尚未确认：' + (record.payload.orderTitle || record.payload.returnTitle || '未命名单据')">
      <span>将按上次内容核对同一笔操作。当前输入保留。</span>
      <el-button type="text" :loading="busy === record.requestId" :disabled="!!busy" @click="recover(record)">核对上次保存</el-button>
    </el-alert>
  </div>
</template>
<script>
import { inventoryDraftRecovery } from '@/api/inventory/draftRecovery'
export default {
  name: 'InventoryDraftRecovery',
  props: { feature: { type: String, required: true } },
  data: () => ({ records: [], busy: '', error: '', sequence: 0, inactive: false }),
  watch: {
    feature() { this.refresh() },
    '$store.getters.id'() { this.refresh() },
    '$store.state.user.sessionRevision'() { this.refresh() }
  },
  mounted() { window.addEventListener('erp:dept-changed', this.refresh); window.addEventListener('erp:draft-recovery-changed', this.refresh); this.refresh() },
  activated() { this.inactive = false; this.refresh() },
  deactivated() { this.inactive = true; this.sequence += 1 },
  beforeDestroy() { this.inactive = true; this.sequence += 1; window.removeEventListener('erp:dept-changed', this.refresh); window.removeEventListener('erp:draft-recovery-changed', this.refresh) },
  methods: {
    refresh() {
      const sequence = ++this.sequence
      this.records = []; this.error = ''
      return inventoryDraftRecovery.pending(this.feature).then(records => { if (sequence === this.sequence && !this.inactive) this.records = records })
        .catch(() => { if (sequence === this.sequence && !this.inactive) this.error = '选择原业务组织后可核对上次保存；浏览器存储不可用时无法保存草稿' })
    },
    recover(record) {
      if (this.busy) return
      const sequence = this.sequence
      this.busy = record.requestId
      return inventoryDraftRecovery.recover(record).then(response => {
        if (this.inactive || sequence !== this.sequence) return
        this.$emit('recovered', { record, response })
        this.$modal.msgSuccess('已核对原单：' + (response.data.orderNo || response.data.returnNo || response.data.orderId || response.data.returnId))
        return this.refresh()
      }).catch(error => { if (!this.inactive && sequence === this.sequence) this.error = error.message || '结果仍待核对，请稍后重试' })
        .finally(() => { this.busy = '' })
    }
  }
}
</script>
