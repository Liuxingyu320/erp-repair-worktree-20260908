<template>
  <aside class="onboarding-list-pane">
    <el-tabs v-model="activeStatus" class="status-tabs" stretch @tab-click="handleStatusChange">
      <el-tab-pane v-for="status in statusTabs" :key="status.value" :name="status.value">
        <template slot="label">
          <span class="status-tab-label">
            {{ status.label }}
            <span v-if="statusCount(status.value) !== null" class="status-count">{{ statusCount(status.value) }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="onboarding-filters">
      <div class="onboarding-search-row">
        <el-input
          v-model.trim="filters.keyword"
          clearable
          prefix-icon="el-icon-search"
          placeholder="搜索姓名 / 手机号 / 岗位 / 部门"
          @keyup.enter.native="submitFilters"
          @clear="submitFilters"
        />
        <el-popover v-model="filterVisible" placement="bottom-end" width="430" trigger="click" popper-class="hr-onboarding-filter-popover">
          <div class="advanced-filter-panel">
            <div class="advanced-filter-panel__title">
              <strong>高级筛选</strong>
              <span>组合条件会应用到当前状态</span>
            </div>
            <el-date-picker
              v-model="filters.expectedEntryDateRange"
              class="filter-wide"
              type="daterange"
              value-format="yyyy-MM-dd"
              range-separator="至"
              start-placeholder="预计入职起始"
              end-placeholder="预计入职截止"
            />
            <div class="filter-grid">
              <el-select v-model="filters.targetDeptId" clearable filterable placeholder="公司 / 组织">
                <el-option v-for="item in optionList('organizations')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="filters.targetStoreId" clearable filterable placeholder="门店">
                <el-option v-for="item in optionList('stores')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="filters.employeeCategory" clearable placeholder="员工类别">
                <el-option v-for="item in optionList('employeeCategories')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="filters.ownerUserId" clearable filterable placeholder="入职负责人">
                <el-option v-for="item in optionList('owners')" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
            <div class="advanced-filter-panel__actions">
              <el-button size="small" @click="resetFilters">重置</el-button>
              <el-button size="small" type="primary" @click="applyFilters">应用筛选</el-button>
            </div>
          </div>
          <el-button slot="reference" class="filter-trigger" icon="el-icon-s-operation" :type="hasAdvancedFilters ? 'primary' : ''" plain />
        </el-popover>
      </div>
      <el-alert
        v-if="!canQuery"
        class="query-permission-cue"
        title="你可以筛选入职记录，但暂无详情查看权限"
        type="info"
        :closable="false"
        show-icon
      />
    </div>

    <div class="result-count">共 {{ total }} 条</div>

    <div class="onboarding-list-state">
      <div v-if="loading" class="list-message" v-loading="true"><span>正在加载入职记录…</span></div>
      <div v-else-if="error" class="list-message list-message--error">
        <i class="el-icon-warning-outline" />
        <strong>列表加载失败</strong>
        <span>{{ error }}</span>
        <el-button size="mini" type="primary" plain @click="$emit('retry')">重试</el-button>
      </div>
      <el-empty v-else-if="!rows.length" description="当前筛选条件下暂无入职记录" />
      <div v-else class="onboarding-rows">
        <button
          v-for="row in rows"
          :key="row.onboardingId"
          type="button"
          class="onboarding-row"
          :class="{ 'is-selected': row.onboardingId === selectedId, 'is-disabled': !canSelect }"
          :disabled="!canSelect"
          :title="canSelect ? '查看入职详情' : '暂无详情查看权限'"
          @click="handleSelect(row)"
        >
          <div class="onboarding-row__top">
            <span class="onboarding-row__name">{{ row.employeeName || "未命名员工" }}</span>
            <el-tag size="mini" :type="statusFor(row.status).type">{{ statusFor(row.status).label }}</el-tag>
          </div>
          <div class="onboarding-row__summary">
            <span>{{ row.positionName || "岗位未填写" }}</span>
            <i />
            <span>{{ organizationLabel(row) }}</span>
          </div>
          <div class="onboarding-row__date">入职日期：{{ row.expectedEntryDate || "待定" }}</div>
          <i class="el-icon-arrow-right onboarding-row__arrow" />
        </button>
      </div>
    </div>

    <footer class="onboarding-list-footer">
      <el-pagination
        small
        background
        layout="prev, pager, next, sizes"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 30, 50]"
        :total="total"
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </footer>
  </aside>
</template>

<script>
import { ONBOARDING_STATUS_TABS, statusMeta } from "../onboardingFieldConfig"

const SUPPORTED_STATUS_FILTERS = ["DRAFT", "READY", "CONFIRMED", "CANCELLED"]

const createFilters = query => ({
  keyword: query.keyword || "",
  expectedEntryDateRange: query.expectedEntryDateFrom || query.expectedEntryDateTo
    ? [query.expectedEntryDateFrom || "", query.expectedEntryDateTo || ""]
    : [],
  targetDeptId: query.targetDeptId,
  targetStoreId: query.targetStoreId,
  employeeCategory: query.employeeCategory,
  ownerUserId: query.ownerUserId
})

export default {
  name: "HrOnboardingListPane",
  props: {
    query: { type: Object, required: true },
    rows: { type: Array, default: () => [] },
    total: { type: Number, default: 0 },
    loading: Boolean,
    error: { type: String, default: "" },
    selectedId: { type: [Number, String], default: undefined },
    options: { type: Object, default: () => ({}) },
    canSelect: { type: Boolean, default: false },
    canQuery: { type: Boolean, default: false }
  },
  data() {
    return {
      statusTabs: ONBOARDING_STATUS_TABS.filter(item => SUPPORTED_STATUS_FILTERS.includes(item.value)),
      activeStatus: this.query.status || "DRAFT",
      filters: createFilters(this.query),
      filterVisible: false,
      statusCounts: {}
    }
  },
  computed: {
    hasAdvancedFilters() {
      return Boolean(
        (this.filters.expectedEntryDateRange && this.filters.expectedEntryDateRange.length) ||
        this.filters.targetDeptId || this.filters.targetStoreId ||
        this.filters.employeeCategory || this.filters.ownerUserId
      )
    }
  },
  watch: {
    query: {
      deep: true,
      handler(value) {
        this.activeStatus = value.status || "DRAFT"
        this.filters = createFilters(value)
      }
    },
    total: {
      immediate: true,
      handler(value) {
        if (this.activeStatus) this.$set(this.statusCounts, this.activeStatus, Number(value) || 0)
      }
    }
  },
  methods: {
    optionList(key) {
      return Array.isArray(this.options[key]) ? this.options[key] : []
    },
    statusCount(status) {
      return Object.prototype.hasOwnProperty.call(this.statusCounts, status) ? this.statusCounts[status] : null
    },
    handleStatusChange() {
      this.$emit("query-change", { status: this.activeStatus, pageNum: 1 })
    },
    submitFilters() {
      const range = this.filters.expectedEntryDateRange || []
      this.$emit("query-change", {
        keyword: this.filters.keyword,
        expectedEntryDateFrom: range[0] || undefined,
        expectedEntryDateTo: range[1] || undefined,
        targetDeptId: this.filters.targetDeptId || undefined,
        targetStoreId: this.filters.targetStoreId || undefined,
        employeeCategory: this.filters.employeeCategory || undefined,
        ownerUserId: this.filters.ownerUserId || undefined,
        pageNum: 1
      })
    },
    applyFilters() {
      this.filterVisible = false
      this.submitFilters()
    },
    resetFilters() {
      this.filters = { ...createFilters({}), keyword: this.filters.keyword }
      this.applyFilters()
    },
    changePage(pageNum) {
      this.$emit("query-change", { pageNum })
    },
    changePageSize(pageSize) {
      this.$emit("query-change", { pageNum: 1, pageSize })
    },
    handleSelect(row) {
      if (!this.canSelect || !this.canQuery) return
      this.$emit("select", row)
    },
    statusFor(status) {
      return statusMeta(status)
    },
    organizationLabel(row) {
      return row.storeName || row.deptLevel3Name || row.deptLevel2Name || row.deptLevel1Name || row.companyName || "组织未填写"
    }
  }
}
</script>

<style lang="scss" scoped>
.onboarding-list-pane {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #e4e7ed;
  background: #fff;
}

.status-tabs {
  flex-shrink: 0;
  padding: 4px 22px 0;

  ::v-deep .el-tabs__header { margin: 0; }
  ::v-deep .el-tabs__nav-wrap::after { height: 1px; background: #edf0f5; }
  ::v-deep .el-tabs__item { height: 58px; line-height: 58px; color: #303746; font-size: 15px; }
  ::v-deep .el-tabs__item.is-active { color: #5146e5; font-weight: 600; }
  ::v-deep .el-tabs__active-bar { height: 3px; border-radius: 3px; background: #5146e5; }
}

.status-tab-label { display: inline-flex; align-items: center; gap: 8px; }
.status-count {
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #f0f1f5;
  color: #7c8494;
  font-size: 12px;
  font-weight: 500;
}
.is-active .status-count { background: #eeedff; color: #584df0; }

.onboarding-filters { padding: 20px 22px 10px; }
.onboarding-search-row { display: grid; grid-template-columns: minmax(0, 1fr) 42px; gap: 12px; }
.filter-trigger { width: 42px; padding: 12px 0; }
.query-permission-cue { margin-top: 10px; }
.result-count { flex-shrink: 0; padding: 2px 22px 10px; color: #687083; font-size: 13px; }

.onboarding-list-state { flex: 1; min-height: 0; overflow-y: auto; scrollbar-width: thin; }
.list-message {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #687083;
}
.list-message--error i { color: #f59e0b; font-size: 30px; }
.onboarding-rows { padding: 0 22px 12px; }

.onboarding-row {
  position: relative;
  width: 100%;
  min-height: 96px;
  display: block;
  padding: 14px 44px 12px 18px;
  border: 1px solid #e5e8ef;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;

  & + & { margin-top: 8px; }
  &:hover { border-color: #bcb7ff; background: #fbfbff; }
  &.is-selected {
    border-color: #6558f5;
    background: #f8f8ff;
    box-shadow: 0 0 0 1px rgba(101, 88, 245, 0.08);
  }
  &.is-disabled { cursor: not-allowed; opacity: 0.72; }
}

.onboarding-row__top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.onboarding-row__name { color: #242936; font-size: 15px; font-weight: 600; }
.onboarding-row__summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  color: #5f6675;
  font-size: 13px;

  i { width: 3px; height: 3px; border-radius: 50%; background: #aeb4c0; }
}
.onboarding-row__date { margin-top: 7px; color: #7a8292; font-size: 13px; }
.onboarding-row__arrow { position: absolute; right: 16px; top: 50%; color: #9aa1ad; transform: translateY(-50%); }

.onboarding-list-footer {
  flex-shrink: 0;
  min-height: 62px;
  display: flex;
  align-items: center;
  padding: 9px 16px;
  border-top: 1px solid #edf0f5;
  background: #fff;

  .el-pagination { width: 100%; padding: 0; white-space: normal; }
}

.advanced-filter-panel__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
  color: #303746;

  span { color: #8b93a2; font-size: 12px; }
}
.filter-wide { width: 100%; }
.filter-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }
.advanced-filter-panel__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.advanced-filter-panel__actions .el-button + .el-button { margin-left: 0; }

@media (max-width: 1280px) {
  .status-tabs { padding-left: 14px; padding-right: 14px; }
  .status-tabs ::v-deep .el-tabs__item { font-size: 13px; }
  .onboarding-filters, .onboarding-rows { padding-left: 14px; padding-right: 14px; }
  .result-count { padding-left: 14px; }
}
</style>
