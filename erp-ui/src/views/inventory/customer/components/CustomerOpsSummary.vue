<template>
  <section v-if="canAudit" class="customer-ops-grid">
    <div>
      <span>本实例跨店拒绝</span>
      <strong class="danger">{{ Number(summary.scopeDeniedCount || 0) }}</strong>
    </div>
    <div>
      <span>本实例乐观锁冲突</span>
      <strong class="warning">{{ Number(summary.optimisticConflictCount || 0) }}</strong>
    </div>
    <div>
      <span>本实例旧客户接口调用</span>
      <strong>{{ Number(summary.legacyApiCallCount || 0) }}</strong>
    </div>
  </section>
</template>

<script>
import { getCustomerServiceCardOpsSummary } from '@/api/inventory/customer'

export default {
  name: 'CustomerOpsSummary',
  data() {
    return { summary: {} }
  },
  computed: {
    canAudit() {
      const permissions = this.$store && this.$store.getters
        ? this.$store.getters.permissions || []
        : []
      return permissions.includes('*:*:*') || permissions.includes('inv:customerCard:audit')
    }
  },
  created() {
    if (this.canAudit) this.load()
  },
  methods: {
    load() {
      return getCustomerServiceCardOpsSummary().then(response => {
        this.summary = response.data || {}
      }).catch(() => {
        this.summary = {}
      })
    }
  }
}
</script>

<style scoped lang="scss">
.customer-ops-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.customer-ops-grid > div {
  border: 1px solid #d4dae0;
  border-radius: 10px;
  background: #fcfcfd;
  padding: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, .025), 0 7px 20px rgba(0, 0, 0, .035);
}
.customer-ops-grid span {
  display: block;
  color: #555b61;
  font-size: 12px;
}
.customer-ops-grid strong {
  display: block;
  margin-top: 6px;
  color: var(--erp-primary, #0b6b53);
  font-size: 20px;
}
.customer-ops-grid strong.warning { color: #e6a23c; }
.customer-ops-grid strong.danger { color: #f56c6c; }
@media (max-width: 760px) {
  .customer-ops-grid { grid-template-columns: 1fr; }
}
</style>
