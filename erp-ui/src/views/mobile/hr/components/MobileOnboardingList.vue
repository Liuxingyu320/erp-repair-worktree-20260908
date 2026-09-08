<template>
  <section class="mobile-onboarding-list" :aria-label="listLabel">
    <p v-if="items.length && !canSelect" class="list-permission-note" role="note">暂无详情查看权限，仅可浏览入职任务。</p>
    <div v-if="!items.length && loading" class="onboarding-list-state" role="status" aria-live="polite" aria-busy="true">
      <span class="state-spinner" aria-hidden="true"></span>
      <strong>正在加载入职任务</strong>
    </div>

    <div v-else-if="!items.length" class="onboarding-list-state is-empty">
      <strong>暂无{{ selectedStatusLabel }}入职任务</strong>
      <p>可以刷新查看最新任务。</p>
      <button type="button" :disabled="loading" @click="refresh">刷新</button>
    </div>

    <ul v-else class="onboarding-rows" role="list">
      <li v-for="(item, index) in items" :key="rowKey(item, index)" class="onboarding-row" role="listitem">
        <button
          class="onboarding-row-button"
          type="button"
          :disabled="!canSelect"
          :aria-disabled="!canSelect ? 'true' : null"
          :aria-label="rowAriaLabel(item)"
          @click="selectItem(item)"
        >
          <span class="row-primary">
            <strong>{{ item.employeeName || item.name || "未命名员工" }}</strong>
            <span :class="['status-badge', statusTone(item.status)]">{{ statusLabel(item.status) }}</span>
          </span>
          <span class="row-meta">
            <span>{{ item.phoneNumberMasked || "手机未填写" }}</span>
            <span>{{ positionLabel(item) }}</span>
          </span>
          <span class="row-meta">
            <span>{{ organizationLabel(item) }}</span>
            <span>预计 {{ item.expectedEntryDate || "待定" }}</span>
          </span>
          <span class="row-footer">
            <span>{{ missingLabel(item) }}</span>
            <i v-if="canSelect" class="el-icon-arrow-right" aria-hidden="true" />
          </span>
        </button>
      </li>
    </ul>

    <div v-if="items.length" class="onboarding-list-actions" aria-live="polite">
      <span v-if="loading" role="status" aria-busy="true">正在加载更多…</span>
      <button v-else-if="hasMore" type="button" :disabled="loading" @click="loadMore">加载更多</button>
      <span v-else>已经到底了</span>
      <button class="refresh-button" type="button" :disabled="loading" @click="refresh">刷新列表</button>
    </div>
  </section>
</template>

<script>
const STATUS_LABELS = {
  DRAFT: "待完善",
  READY: "待确认",
  CONFIRMED: "已入职",
  CANCELLED: "已取消"
}

export default {
  name: "MobileOnboardingList",
  props: {
    items: { type: Array, default: () => [] },
    selectedStatus: { type: String, default: "" },
    loading: { type: Boolean, default: false },
    hasMore: { type: Boolean, default: false },
    canSelect: { type: Boolean, default: true }
  },
  computed: {
    selectedStatusLabel() {
      return this.selectedStatus ? (STATUS_LABELS[this.selectedStatus] || "当前") : ""
    },
    listLabel() {
      return this.selectedStatusLabel ? `${this.selectedStatusLabel}入职任务` : "入职任务"
    }
  },
  methods: {
    selectItem(item) {
      if (this.canSelect) this.$emit("select", item)
    },
    loadMore() {
      if (!this.loading && this.hasMore) this.$emit("load-more")
    },
    refresh() {
      if (!this.loading) this.$emit("refresh")
    },
    rowKey(item, index) {
      return item && item.onboardingId ? item.onboardingId : `onboarding-row-${index}`
    },
    statusLabel(status) {
      return STATUS_LABELS[status] || "状态待确认"
    },
    statusTone(status) {
      return {
        DRAFT: "is-draft",
        READY: "is-ready",
        CONFIRMED: "is-confirmed",
        CANCELLED: "is-cancelled"
      }[status] || ""
    },
    positionLabel(item) {
      return item.targetRoleName || item.positionName || item.targetPostName || item.postName || "岗位未填写"
    },
    organizationLabel(item) {
      return item.targetOrganizationName || item.storeName || item.deptLevel3Name ||
        item.deptLevel2Name || item.deptLevel1Name || item.companyName || "组织未填写"
    },
    missingCount(item) {
      if (Number.isFinite(Number(item.missingCount))) return Math.max(0, Number(item.missingCount))
      return Array.isArray(item.missingOnboardingFields) ? item.missingOnboardingFields.length : 0
    },
    missingLabel(item) {
      const count = this.missingCount(item)
      return count > 0 ? `缺少 ${count} 项资料` : "资料完整"
    },
    rowAriaLabel(item) {
      const name = item.employeeName || item.name || "未命名员工"
      const maskedPhone = item.phoneNumberMasked || "手机未填写"
      const entryDate = item.expectedEntryDate || "日期待定"
      return [
        name,
        maskedPhone,
        this.positionLabel(item),
        this.organizationLabel(item),
        `预计入职 ${entryDate}`,
        this.statusLabel(item.status),
        this.missingLabel(item)
      ].join("，")
    }
  }
}
</script>

<style lang="scss" scoped>
.mobile-onboarding-list {
  overflow: hidden;
  border: 1px solid rgba(35, 104, 98, 0.1);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
}

.onboarding-rows {
  padding: 0;
  margin: 0;
  list-style: none;
}
.list-permission-note { margin: 0; padding: 12px 16px; color: #765f5b; font-size: 13px; background: #fff6f3; border-bottom: 1px solid #f0dfda; }
.onboarding-row-button:disabled { cursor: default; opacity: 0.82; }

.onboarding-row + .onboarding-row {
  border-top: 1px solid #e8efee;
}

.onboarding-row-button {
  display: block;
  width: 100%;
  min-height: 44px;
  padding: 16px;
  color: #18343b;
  text-align: left;
  border: 0;
  background: transparent;
}

.onboarding-row-button:active {
  background: #eff7f5;
}

.onboarding-row-button:focus-visible {
  outline: 3px solid rgba(22, 117, 111, 0.28);
  outline-offset: -3px;
}

.row-primary,
.row-meta,
.row-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 12px;
}

.row-primary strong {
  min-width: 0;
  font-size: 17px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.row-meta {
  margin-top: 7px;
  color: #667d7b;
  font-size: 13px;
  line-height: 1.45;
}

.row-meta span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.row-meta span:last-child {
  text-align: right;
}

.row-footer {
  margin-top: 11px;
  color: #287970;
  font-size: 13px;
  font-weight: 600;
}

.status-badge {
  flex: 0 0 auto;
  padding: 4px 9px;
  color: #5d7472;
  font-size: 13px;
  border-radius: 999px;
  background: #edf2f1;
}

.status-badge.is-draft { color: #93651d; background: #fff4d9; }
.status-badge.is-ready { color: #126c66; background: #dff2ef; }
.status-badge.is-confirmed { color: #246c43; background: #e3f3e8; }
.status-badge.is-cancelled { color: #8b4e49; background: #f8e8e6; }

.onboarding-list-state {
  padding: 34px 20px;
  color: #637b79;
  text-align: center;
}

.onboarding-list-state strong,
.onboarding-list-state p {
  display: block;
}

.onboarding-list-state p {
  margin: 7px 0 0;
  font-size: 14px;
}

.onboarding-list-state button,
.onboarding-list-actions button {
  min-height: 44px;
  padding: 0 18px;
  color: #146e68;
  font-weight: 600;
  border: 1px solid #b9dcd7;
  border-radius: 14px;
  background: #edf8f6;
}

.onboarding-list-state button {
  margin-top: 16px;
}

.state-spinner {
  display: block;
  width: 24px;
  height: 24px;
  margin: 0 auto 12px;
  border: 3px solid #d8ece9;
  border-top-color: #16756f;
  border-radius: 50%;
  animation: list-spin 0.8s linear infinite;
}

.onboarding-list-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 62px;
  padding: 9px 14px;
  color: #718482;
  font-size: 13px;
  border-top: 1px solid #e8efee;
}

.onboarding-list-actions .refresh-button {
  color: #5e7472;
  border-color: transparent;
  background: transparent;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@keyframes list-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 320px) {
  .onboarding-row-button { padding: 14px 12px; }
  .row-meta { gap: 8px; }
}

@media (prefers-reduced-motion: reduce) {
  .state-spinner { animation: none; }
}
</style>
