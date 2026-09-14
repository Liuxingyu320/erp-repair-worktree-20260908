<template>
  <div class="mobile-feature-page mobile-system-page">
    <main class="mobile-feature-shell" :aria-label="feature.title">
      <section class="feature-stage mobile-system-scroll" data-mobile-scroll-root>
        <header class="feature-header mobile-system-topbar">
          <div>
            <h1>{{ feature.title }}</h1>
            <button v-if="showContextSelector" class="shop-pill" type="button" @click="goSelectShop">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.warehouse" /></svg>
              <span>{{ selectedDeptName || contextSelectorFallback }}</span>
              <svg class="chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5H7Z" /></svg>
            </button>
            <span v-else class="route-scope-pill">{{ routeScopeLabel }}</span>
          </div>
          <button class="icon-button" type="button" aria-label="刷新" @click="refresh">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.refresh" /></svg>
          </button>
        </header>

        <section v-if="routeNoticeMessage" class="glass-panel mobile-redirect-banner mobile-system-panel">
          <strong>{{ routeNoticeTitle }}</strong>
          <span>{{ routeNoticeMessage }}</span>
        </section>

        <section
          v-if="featureKey === 'oaPurchase' && oaPurchaseAvailabilityResolved && !oaPurchaseSubmissionAvailable"
          class="glass-panel mobile-redirect-banner mobile-system-panel"
          role="status"
        >
          <strong>审批规则尚未发布或采购提交尚未开放</strong>
          <span>当前仍可新建、编辑和保存采购草稿，提交与重新提交暂不可用。</span>
        </section>

        <section v-if="featureKey === 'purchase' && hasAnyPermission(['inv:purchase:receive'])" class="glass-panel mobile-redirect-banner mobile-system-panel">
          <strong>收货结果核对</strong>
          <p v-if="purchaseReceiveError" role="alert">{{ purchaseReceiveError }}</p>
          <p v-else-if="purchaseReceiveRecords.length" role="status">上一笔结果未确认时，请先核对；采购单已收完也可恢复。</p>
          <button type="button" @click="refreshPurchaseReceiveRecords">刷新待核对收货</button>
          <button v-for="record in purchaseReceiveRecords" :key="record.requestId" type="button" :disabled="purchaseReceiveBusy || !!actionLoadingKey" @click="recoverMobilePurchaseReceive(record)">核对采购单 {{ record.orderId }} 的上一笔收货</button>
        </section>

        <p v-if="feature.subtitle" class="feature-subtitle mobile-system-muted">{{ feature.subtitle }}</p>

        <section class="section-title scope-title mobile-section-title">
          <h2>数据范围</h2>
          <span v-if="activeFilterLabel" class="filter-summary">
            <span>当前</span>
            <strong>{{ activeFilterLabel }}</strong>
          </span>
        </section>

        <section v-if="showStockManagedStoreScope" class="stock-scope-panel">
          <div class="stock-scope-toggle" role="group" aria-label="库存门店范围">
            <button
              :class="{ active: stockScopeMode === 'current' }"
              type="button"
              @click="setStockScopeMode('current')"
            >
              当前门店
            </button>
            <button
              :class="{ active: stockScopeMode === 'managed' }"
              type="button"
              @click="setStockScopeMode('managed')"
            >
              管理门店
            </button>
          </div>
          <label v-if="showManagedStoreSelect" class="managed-store-select">
            <span>店铺</span>
            <select v-model.number="managedStoreId" :disabled="managedStoreLoading" @change="handleManagedStoreChange">
              <option :value="allManagedStoreValue">全部管理门店</option>
              <option v-for="store in managedStoreOptions" :key="store.deptId" :value="store.deptId">
                {{ store.deptName }}
              </option>
            </select>
          </label>
        </section>

        <section v-if="stockStatusFilters.length" class="status-filter-panel">
          <div class="status-filter-head">
            <span>库存状态</span>
          </div>
          <div class="status-filter-scroll">
            <button
              v-for="filter in stockStatusFilters"
              :key="filter.value"
              :class="{ active: filter.value === activeStockStatus }"
              type="button"
              @click="selectStockStatus(filter.value)"
            >
              {{ filter.label }}
            </button>
          </div>
        </section>

        <section v-if="searchConfig" class="glass-panel search-panel mobile-system-panel">
          <div class="search-row">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.search" /></svg>
            <input

              v-model.trim="searchKeyword"
              type="search"
              :placeholder="searchPlaceholder"
              @keyup.enter="submitSearch"
            >
            <button
              v-if="searchKeyword"
              class="search-clear"
              type="button"
              aria-label="清空搜索"
              @click="clearSearch"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.close" /></svg>
            </button>
            <button class="search-submit" type="button" aria-label="查询" @click="submitSearch">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths.search" /></svg>
            </button>
          </div>
          <div v-if="searchFieldOptions.length > 1" class="search-field-tabs">
            <button
              v-for="field in searchFieldOptions"
              :key="field.key"
              :class="{ active: field.key === activeSearchFieldKey }"
              type="button"
              @click="selectSearchField(field.key)"
            >
              {{ field.label }}
            </button>
          </div>
        </section>

        <section class="glass-panel list-panel mobile-system-panel mobile-system-panel--continuous">
          <div class="panel-head">
            <h2>{{ feature.listTitle }}</h2>
            <span>{{ panelStateLabel }}</span>
          </div>
          <div
            v-if="listState && listState.type === 'loading'"
            class="list-state mobile-data-state mobile-data-state--loading"
            role="status"
            aria-live="polite"
          >
            <strong class="mobile-data-state__title">{{ listState.title }}</strong>
            <p class="mobile-data-state__description">{{ listState.description }}</p>
          </div>
          <div
            v-else-if="listState && isBlockingListState(listState)"
            :class="['list-state', 'error', 'mobile-data-state', `mobile-data-state--${listState.type}`, listState.type]"
            role="alert"
            aria-live="assertive"
          >
            <strong class="mobile-data-state__title">{{ listState.title }}</strong>
            <p class="mobile-data-state__description">{{ listState.description }}</p>
            <button class="list-state-action mobile-button mobile-button--secondary" type="button" @click="handleListStateAction(listState.actionId)">
              {{ listStateActionLabel }}
            </button>
          </div>
          <div
            v-else-if="listState"
            :class="['list-state', 'mobile-data-state', `mobile-data-state--${listState.type}`, listState.type]"
            role="status"
            aria-live="polite"
          >
            <strong class="mobile-data-state__title">{{ listState.title }}</strong>
            <p class="mobile-data-state__description">{{ listState.description }}</p>
            <button class="list-state-action mobile-button mobile-button--secondary" type="button" @click="handleListStateAction(listState.actionId)">
              {{ listStateActionLabel }}
            </button>
          </div>
          <template v-else>
            <button
              v-for="item in displayItems"
              :key="item.code"
              class="list-row mobile-list-row"
              type="button"
              @click="openItem(item)"
            >
              <div v-if="item.imageUrls" class="mobile-list-images">
                <el-image v-if="item.imageUrls.length" :src="item.imageUrls[0]" fit="cover" style="width:64px;height:64px;border-radius:8px">
                  <span slot="error">图片不可用</span>
                </el-image>
                <small>{{ item.imageUrls.length ? item.imageUrls.length + '张图片' : '暂无图片' }}</small>
              </div>
              <div>
                <h3 class="mobile-list-row__title">{{ item.title }}</h3>
                <p class="mobile-list-row__meta">{{ item.code }}</p>
                <p class="row-detail mobile-list-row__meta">{{ item.detail }}</p>
              </div>
              <span :class="['status-dot', 'mobile-status-chip', statusChipClass(item.status), feature.tone]">{{ item.status }}</span>
            </button>
            <div v-if="hasMoreItems" class="load-more-row">
              <button type="button" :disabled="loadingMore" @click="loadMore">
                {{ loadingMore ? "加载中..." : loadMoreLabel }}
              </button>
            </div>
          </template>
        </section>

        <section v-if="primaryFeatureActions.length || overflowFeatureActions.length" class="section-title shortcut-title">
          <h2>快捷处理</h2>
        </section>

        <section v-if="primaryFeatureActions.length" class="feature-actions" aria-label="快捷处理">
          <button
            v-for="action in primaryFeatureActions"
            :key="action.label"
            :class="[
              'glass-panel',
              'action-card',
              {
                active: activeActionLabel === action.label,
                'action-card--primary': action.behavior === 'create-form'
              }
            ]"
            type="button"
            :disabled="isTopActionLoading(action)"
            @click="handleAction(action)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[action.icon]" /></svg>
            <span>{{ isTopActionLoading(action) ? "处理中..." : action.label }}</span>
          </button>
        </section>

        <details v-if="overflowFeatureActions.length" class="glass-panel feature-action-overflow">
          <summary>
            <span>更多操作</span>
            <small>{{ overflowFeatureActions.length }} 项</small>
          </summary>
          <div class="feature-actions overflow-feature-actions">
            <button
              v-for="action in overflowFeatureActions"
              :key="action.label"
              :class="['action-card', { active: activeActionLabel === action.label }]"
              type="button"
              :disabled="isTopActionLoading(action)"
              @click="handleAction(action)"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[action.icon]" /></svg>
              <span>{{ isTopActionLoading(action) ? "处理中..." : action.label }}</span>
            </button>
          </div>
        </details>

        <mobile-detail-sheet
          :feature="feature"
          :item="selectedItem"
          :detail-fields="selectedDetailFields"
          :detail-sections="selectedDetailSections"
          :detail-loading="detailLoading"
          :detail-load-failed="detailLoadFailed"
          :primary-actions="detailPrimaryActions"
          :secondary-actions="detailSecondaryActions"
          :action-loading-key="actionLoadingKey"
          :action-message="actionMessage"
          :action-message-type="actionMessageType"
          :icon-paths="iconPaths"
          @close="closeItem"
          @refresh="refreshAndClose"
          @retry-detail="retrySelectedItemDetail"
          @select-shop="goSelectShop"
          @action="handleDetailAction"
        />

        <mobile-form-sheet
          v-if="mobileFormConfig"
          :value="formSheet.data"
          :open="formSheet.open"
          :context-key="formUploadEpoch"
          :feature="feature"
          :config="mobileFormConfig"
          :title="mobileFormTitle"
          :saving="formSheet.saving"
          :error="formSheet.error"
          :validation-error="formSheet.validationError"
          :fixed-asset-precheck="formSheet.fixedAssetPrecheck"
          :icon-paths="iconPaths"
          :context="mobileFormContext"
          :submit-modes="availableMobileSubmitModes"
          @input="handleMobileFormInput"
          @close="requestCloseMobileForm"
          @submit="submitMobileForm"
        />

        <mobile-action-dialog
          :open="actionDialog.open"
          :action="actionDialog.action || {}"
          :item="actionDialog.receiveScope ? actionDialog.item : selectedItem"
          :review-fields="selectedReviewFields"
          :icon-paths="iconPaths"
          :selected-dept-id="actionDialog.receiveScope ? actionDialog.receiveScope.dept : selectedDeptId"
          @close="closeActionDialog"
          @confirm="confirmActionDialog"
        />

        <mobile-customer-record-dialog
          :open="customerRecordDialog.open"
          :customer="selectedItem"
          :saving="customerRecordDialog.saving"
          :error="customerRecordDialog.error"
          :icon-paths="iconPaths"
          @close="closeCustomerRecordDialog"
          @submit="submitCustomerServiceRecord"
        />

        <mobile-confirm-dialog
          :open="mobileConfirm.open"
          :title="mobileConfirm.title"
          :message="mobileConfirm.message"
          :confirm-text="mobileConfirm.confirmText"
          :cancel-text="mobileConfirm.cancelText"
          :icon-paths="iconPaths"
          @confirm="confirmMobileConfirm"
          @cancel="cancelMobileConfirm"
        />
      </section>

      <nav class="bottom-nav mobile-system-bottom-nav" aria-label="手机底部导航">
        <button v-for="item in navItems" :key="item.label" :class="{ active: isBottomNavItemActive(item) }" type="button" @click="openNav(item)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconPaths[item.icon]" /></svg>
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </main>
  </div>
</template>

<script>
import { acknowledgeTransferCommand } from "@/utils/request"
import { listShopTree } from "@/api/system/dept"
import { getCustomerServiceCardPhoto } from "@/api/inventory/customer"
import { listStock } from "@/api/inventory/stock"
import { getPurchaseAvailability } from "@/api/oa/purchase"
import { getSelectedDeptContext } from "@/utils/shopContext"
import { fetchMobileFeatureData, fetchMobileFeatureDetail } from "./featureService"
import { runMobileFeatureAction, saveMobileFeatureForm } from "./featureActionService"
import MobileDetailSheet from "./components/MobileDetailSheet.vue"
import MobileFormSheet from "./components/MobileFormSheet.vue"
import MobileActionDialog from "./components/MobileActionDialog.vue"
import MobileCustomerRecordDialog from "./components/MobileCustomerRecordDialog.vue"
import MobileConfirmDialog from "./components/MobileConfirmDialog.vue"

const { getPurchaseReceiveRecovery, purchaseReceiveResultMessage } = require("@/utils/purchaseReceiveRecovery")

const {
  getMobileRouteBottomNav,
  getMobileRouteAccessDecision,
  getMobileRoutePolicy,
  isMobileBottomNavItemActive,
  isMobileFeatureEnabled
} = require("../mobileNavigation")
const {
  createClearedFeatureQuery,
  filterMobileShortcutActionsForListState,
  hasMobileFeatureQueryOverrides,
  partitionMobileActions,
  prioritizeMobileFeatureActions,
  resolveMobileListStateActionLabel,
  resolveMobileListState
} = require("../mobileExperience")
const { mapMobileFeatureRows, mapMobileMineRows } = require("./featureMapper")
const { getMobileFeatureActions, partitionMobileDetailActions } = require("./featureActions")
const { MOBILE_FORM_CONFIG, getMobileFormConfig, getMobileTransferFormConfig } = require("./mobileFormConfigs")
const featureSearchConfigs = require("./featureSearchConfigs")
const { getMobileSearchConfig, getStockStatusFilters } = featureSearchConfigs
const mobileFormPayloads = require("./mobileFormPayloads")
const {
  cloneMobileEditFormSource,
  snapshotMobileFormSheet,
  restoreMobileFormSheet,
  createMobileFormData,
  buildMobileFormPayload
} = mobileFormPayloads
const mobileValidation = require("./mobileValidation")
const {
  getMobileFormValidationError: getMobileFormValidationErrorData,
  validateMobileForm: validateMobileFormData
} = mobileValidation
const mobileExportConfigs = require("./mobileExportConfigs")
const { MOBILE_EXPORT_CONFIG } = mobileExportConfigs
const { createMobileRouteLoadGuard } = require("./mobileRouteLoadGuard")
const { refreshMobileTransferAvailability } = require("./mobileTransferAvailability")
const { releaseAllMobileOverlays } = require("./components/mobileOverlayStack")
const { startMobileViewportSync, stopMobileViewportSync } = require("../mobileViewport")
const { mobileErrorMessage } = require("../mobileErrorMessage")
const { getFixedAssetPrecheckFromError } = require("./featureActionRuntime")
const {
  consumeTodoFocusQuery,
  findTodoFocusShipment,
  getTodoFocus,
  getTodoFocusActionIds,
  resolveTodoFocusResult
} = require("@/utils/todoBusinessFocus")
const {
  FEATURE_ACTION_FALLBACKS,
  MOBILE_ACTION_PERMISSIONS,
  MOBILE_FORM_CREATE_PERMISSIONS,
  MOBILE_FORM_EDIT_PERMISSIONS,
  MOBILE_FORM_SUBMIT_PERMISSIONS,
  applyContextualActionLabels,
  applyContextualMobileCopy,
  featureDefaults
} = require("./mobileFeaturePolicy")
const {
  buildApprovalRouteContext,
  buildFeatureExportQuery,
  buildFeatureRequestQuery,
  buildRouteFeatureQuery: buildMobileRouteFeatureQuery,
  normalizeManagedStoreOptions: normalizeMobileManagedStoreOptions,
  normalizeManagedStoreSelection: normalizeMobileManagedStoreSelection,
  normalizeSearchKeyword: normalizeMobileSearchKeyword,
  resolveActionLabelForQuery: resolveMobileActionLabelForQuery,
  resolveActiveFilterLabel: resolveMobileActiveFilterLabel,
  resolveManagedStoreName,
  resolveRouteNotice
} = require("./mobileFeatureListPolicy")

function withMobileRetry(factory, retries = 1, delay = 250) {
  return Promise.resolve().then(factory).catch(error => {
    if (retries <= 0) throw error
    return new Promise(resolve => window.setTimeout(resolve, delay)).then(() => {
      return withMobileRetry(factory, retries - 1, delay * 2)
    })
  })
}

export default {
  name: "MobileFeaturePage",
  components: { MobileDetailSheet, MobileFormSheet, MobileActionDialog, MobileCustomerRecordDialog, MobileConfirmDialog },
  data() {
    const selectedContext = getSelectedDeptContext()
    return {
      selectedDeptName: this.formatDeptLabel(selectedContext),
      selectedDeptId: selectedContext.deptId,
      selectedDeptType: selectedContext.deptType,
      purchaseReceiveActive: true, purchaseReceiveEpoch: 0, purchaseReceiveListEpoch: 0, purchaseReceiveBusy: false,
      purchaseReceiveRecords: [], purchaseReceiveError: "",
      stockScopeMode: "current",
      managedStoreId: 0,
      managedStoreOptions: [],
      managedStoreLoading: false,
      allManagedStoreValue: 0,
      routeLoadGuard: createMobileRouteLoadGuard(),
      todoFocus: null,
      todoFocusedAction: null,
      skipNextRouteApply: false,
      items: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      loading: false,
      loadingMore: false,
      errorMessage: "",
      refreshedAt: "",
      activeActionLabel: "",
      featureQuery: {},
      searchKeyword: "",
      activeSearchField: "",
      activeStockStatus: "",
      selectedItem: null,
      selectedItemEditSource: null,
      detailLoading: false,
      detailLoadFailed: false,
      detailRequestToken: 0,
      actionLoadingKey: "",
      actionMessage: "",
      actionMessageType: "status",
      oaPurchaseSubmissionAvailable: false,
      oaPurchaseAvailabilityResolved: false,
      oaPurchaseAvailabilityToken: 0,
      formUploadEpoch: 0,
      formSheet: {
        open: false,
        mode: "create",
        saving: false,
        error: "",
        validationError: null,
        fixedAssetPrecheck: null,
        config: null,
        data: {},
        initialData: {}
      },
      actionDialog: {
        open: false,
        action: null,
        payload: {}
      },
      customerRecordDialog: {
        open: false,
        action: null,
        saving: false,
        error: ""
      },
      mobileConfirm: {
        open: false,
        title: "确认操作",
        message: "",
        confirmText: "确定",
        cancelText: "取消"
      },
      mobileConfirmResolve: null,
      mobileConfirmReject: null,
      iconPaths: {
        bag: "M7 7V6a5 5 0 0 1 10 0v1h3v14H4V7h3Zm2 0h6V6a3 3 0 0 0-6 0v1Zm-3 2v10h12V9H6Z",
        bell: "M12 22a2.8 2.8 0 0 0 2.7-2h-5.4A2.8 2.8 0 0 0 12 22Zm7-6-2-2v-4a5 5 0 0 0-4-4.9V3h-2v2.1A5 5 0 0 0 7 10v4l-2 2v2h14v-2Z",
        cart: "M7 18a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm10 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM3 4h2l2.2 10.2A2 2 0 0 0 9.2 16H18v-2H9.2L8.8 12H18.5L21 6H7.5L7 4H3Z",
        check: "M18 4h-2.2A3 3 0 0 0 13 2h-2a3 3 0 0 0-2.8 2H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2Zm-7 0h2a1 1 0 0 1 1 1H10a1 1 0 0 1 1-1Zm5.2 7.4-5.1 5.1-3.1-3.1 1.4-1.4 1.7 1.7 3.7-3.7 1.4 1.4Z",
        cloud: "M6 5h5l2 2h5a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Zm0 2a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-5.8l-2-2H6Z",
        close: "m6.4 5 5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5Z",
        cube: "m12 2 8 4v12l-8 4-8-4V6l8-4Zm0 2.2L7 6.7l5 2.5 5-2.5-5-2.5ZM6 8.5v8.3l5 2.5V11L6 8.5Zm7 10.8 5-2.5V8.5L13 11v8.3Z",
        document: "M6 2h9l5 5v15H6V2Zm8 2v5h4l-4-5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        home: "M3 11 12 3l9 8v10h-6v-6H9v6H3V11Z",
        inbound: "M11 3h2v9l3.5-3.5 1.4 1.4L12 15.8 6.1 9.9l1.4-1.4L11 12V3ZM5 18h14v2H5v-2Z",
        logout: "M5 3h8v2H7v14h6v2H5V3Zm10.6 4.4L20.2 12l-4.6 4.6-1.4-1.4L16.4 13H10v-2h6.4l-2.2-2.2 1.4-1.4Z",
        plus: "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7V4Z",
        purchase: "M6 3h12v4h2v14H4V7h2V3Zm2 4h8V5H8v2Zm2 5h4v2h-4v4H8v-4H4v-2h4V8h2v4Z",
        refresh: "M17.7 6.3A8 8 0 1 0 20 12h-2a6 6 0 1 1-1.8-4.3L13 11h8V3l-3.3 3.3Z",
        return: "M8 7h8a5 5 0 0 1 0 10H7v-2h9a3 3 0 0 0 0-6H8v4L3 8l5-5v4Z",
        sales: "M5 4h11l3 3v13H5V4Zm10 1.5V8h2.5L15 5.5ZM8 12h8v2H8v-2Zm0 4h8v2H8v-2Z",
        search: "M10.5 4a6.5 6.5 0 0 1 5.1 10.5l4 4-1.4 1.4-4-4A6.5 6.5 0 1 1 10.5 4Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z",
        trend: "M4 18h16v2H4v-2Zm2-4 4-4 3 3 5-7 2 1.4-6.8 9.4-3.1-3.1-2.7 2.7L6 14Z",
        truck: "M3 5h12v9h2.2L20 10h1v7h-2a3 3 0 0 1-6 0H9a3 3 0 0 1-6 0H1v-2h2V5Zm2 2v7h8V7H5Zm11 7h3v-2.8L18.2 12H16v2ZM6 18a1 1 0 1 0 0-2 1 1 0 0 0 0 2Zm10 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z",
        user: "M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5Z",
        warehouse: "M3 9 12 3l9 6v12h-4v-7H7v7H3V9Zm4 0v3h10V9l-5-3.3L7 9Zm2 7h2v5H9v-5Zm4 0h2v5h-2v-5Z"
      }
    }
  },
  computed: {
    feature() {
      const routeFeature = (this.$route.meta && this.$route.meta.mobileFeature) || {}
      const feature = applyContextualMobileCopy(Object.assign({}, featureDefaults, routeFeature), this.selectedDeptType)
      const fallbackActions = FEATURE_ACTION_FALLBACKS[feature.featureKey]
      if ((!Array.isArray(routeFeature.actions) || routeFeature.actions.length === 0) && fallbackActions) {
        feature.actions = fallbackActions.slice()
      }
      feature.actions = applyContextualActionLabels(feature.actions, feature.contextActionLabels, this.selectedDeptType)
      return feature
    },
    featureKey() {
      return this.feature.featureKey || ""
    },
    featureActionItems() {
      const actions = (this.feature.actions || []).slice()
      const exportConfig = MOBILE_EXPORT_CONFIG[this.featureKey]
      const hasExportAction = actions.some(action => action && action.behavior === "export")
      const hasCreateAction = actions.some(action => action && action.behavior === "create-form")
      if (this.featureKey !== "transfer" && this.mobileFormConfig &&
        !hasCreateAction && this.canCreateMobileForm()) {
        actions.unshift({ label: this.mobileFormConfig.createLabel || "新增", icon: "plus", behavior: "create-form" })
      }
      if (exportConfig && !hasExportAction) {
        actions.push({ label: "导出", icon: "document", behavior: "export", permissions: exportConfig.permissions, placement: "more" })
      }
      return prioritizeMobileFeatureActions(actions).filter(action => this.canShowTopAction(action))
    },
    featureActionGroups() {
      return partitionMobileActions(
        filterMobileShortcutActionsForListState(this.featureActionItems, this.listState),
        4
      )
    },
    primaryFeatureActions() {
      return this.featureActionGroups.primary
    },
    overflowFeatureActions() {
      return this.featureActionGroups.overflow
    },
    userPermissions() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return Array.isArray(getters.permissions) ? getters.permissions : null
    },
    currentUserId() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return getters.id
    },
    approvalRouteContext() {
      return buildApprovalRouteContext(this.$route && this.$route.query)
    },
    featureState() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return {
        driveEnabled: getters.driveEnabled === true,
        businessFeatures: getters.businessFeatures || {}
      }
    },
    navItems() {
      const routePath = this.$route && this.$route.path
      const fallbackNav = getMobileRouteBottomNav(routePath, this.selectedDeptType, undefined, this.featureState)
      return Array.isArray(this.userPermissions)
        ? getMobileRouteBottomNav(routePath, this.selectedDeptType, this.userPermissions, this.featureState)
        : fallbackNav
    },
    routePolicy() {
      return getMobileRoutePolicy(this.$route && this.$route.path) || {}
    },
    showContextSelector() {
      return this.routePolicy.contextRequired !== false
    },
    routeScopeLabel() {
      return this.routePolicy.contextLabel || "当前账号范围"
    },
    displayItems() {
      return this.items
    },
    stateLabel() {
      return this.refreshedAt ? `已刷新 ${this.refreshedAt}` : "实时"
    },
    panelStateLabel() {
      if (this.loading && this.pageNum === 1) return "加载中"
      if (this.errorMessage) return this.errorStateLabel
      if (this.total > 0) return `共 ${this.total} 条`
      return this.stateLabel
    },
    errorStateLabel() {
      if (!this.errorMessage) return ""
      if (!this.isFeatureAllowed()) return "组织不匹配"
      if (/权限|无权|未授权|登录状态已过期/.test(this.errorMessage)) return "无权限"
      return "加载失败"
    },
    emptyListText() {
      if (this.isFeatureAllowed() === false) {
        return "当前组织不能使用该功能，请切换到有权限的店铺/仓库"
      }
      if (!this.selectedDeptId && Array.isArray(this.feature.allowedDeptTypes) && this.feature.allowedDeptTypes.length) {
        return "请先选择" + this.contextSelectorFallback.replace(/^选择/, "")
      }
      if (this.searchKeyword) {
        return "当前搜索没有匹配结果，请换个关键词或清空搜索"
      }
      if (this.activeFilterLabel || this.activeStockStatusLabel) {
        return "当前筛选没有待处理数据，可切换到全部或刷新"
      }
      return "暂无" + this.feature.listTitle
    },
    hasActiveListFilters() {
      return Boolean(
        this.normalizeSearchKeyword() ||
        hasMobileFeatureQueryOverrides(this.featureQuery, this.feature.defaultQuery) ||
        this.activeStockStatus ||
        this.stockScopeMode === "managed"
      )
    },
    canCreateListItem() {
      return this.featureActionItems.some(action => action && action.behavior === "create-form")
    },
    listState() {
      const permissionError = Boolean(
        this.errorMessage &&
        (!this.isFeatureAllowed() || /权限|无权|未授权|登录状态已过期/.test(this.errorMessage))
      )
      return resolveMobileListState({
        loading: this.loading && this.pageNum === 1,
        errorType: this.errorMessage ? (permissionError ? "permission" : "network") : "",
        errorMessage: this.errorMessage,
        itemCount: this.displayItems.length,
        hasActiveFilters: this.hasActiveListFilters,
        listTitle: this.feature.listTitle,
        canCreate: this.canCreateListItem
      })
    },
    listStateActionLabel() {
      return resolveMobileListStateActionLabel(this.listState, this.featureActionItems)
    },
    routeNotice() {
      return resolveRouteNotice(this.$route && this.$route.query)
    },
    routeNoticeTitle() {
      return this.routeNotice.title
    },
    routeNoticeMessage() {
      return this.routeNotice.message
    },
    hasMoreItems() {
      return !this.loading && this.total > this.items.length
    },
    loadMoreLabel() {
      const remainingCount = Math.max(this.total - this.items.length, 0)
      return remainingCount > 0 ? `加载更多（剩余 ${remainingCount} 条）` : "加载更多"
    },
    searchConfig() {
      return getMobileSearchConfig(this.featureKey)
    },
    searchFieldOptions() {
      return this.searchConfig && Array.isArray(this.searchConfig.fields)
        ? this.searchConfig.fields
        : []
    },
    activeSearchFieldConfig() {
      const options = this.searchFieldOptions
      if (!options.length) return null
      return options.find(field => field.key === this.activeSearchField) || options[0]
    },
    activeSearchFieldKey() {
      return this.activeSearchFieldConfig ? this.activeSearchFieldConfig.key : ""
    },
    activeSearchFieldLabel() {
      return this.activeSearchFieldConfig ? this.activeSearchFieldConfig.label : ""
    },
    searchPlaceholder() {
      if (this.activeSearchFieldConfig && this.activeSearchFieldConfig.placeholder) {
        return this.activeSearchFieldConfig.placeholder
      }
      return this.activeSearchFieldLabel ? `输入${this.activeSearchFieldLabel}搜索` : "输入关键词搜索"
    },
    stockStatusFilters() {
      return getStockStatusFilters(this.featureKey)
    },
    showStockManagedStoreScope() {
      return this.featureKey === "stock" && this.selectedDeptType === "STORE"
    },
    showManagedStoreSelect() {
      return this.showStockManagedStoreScope && this.stockScopeMode === "managed"
    },
    selectedManagedStoreName() {
      return resolveManagedStoreName({
        showManagedStoreSelect: this.showManagedStoreSelect,
        managedStoreId: this.managedStoreId,
        managedStoreOptions: this.managedStoreOptions,
        allManagedStoreValue: this.allManagedStoreValue
      })
    },
    activeStockStatusLabel() {
      const selectedStatus = this.stockStatusFilters.find(filter => filter.value === this.activeStockStatus)
      return selectedStatus && selectedStatus.value ? selectedStatus.label : ""
    },
    contextSelectorFallback() {
      const allowedDeptTypes = this.feature.allowedDeptTypes || []
      if (allowedDeptTypes.indexOf("WAREHOUSE") > -1 && allowedDeptTypes.indexOf("STORE") === -1) {
        return "选择仓库"
      }
      if (allowedDeptTypes.indexOf("STORE") > -1 && allowedDeptTypes.indexOf("WAREHOUSE") === -1) {
        return "选择店铺"
      }
      return "选择店铺/仓库"
    },
    detailActions() {
      if (!this.selectedItem) return []
      return getMobileFeatureActions(this.featureKey, this.selectedItem, {
        selectedDeptType: this.selectedDeptType,
        selectedDeptId: this.selectedDeptId,
        userId: this.currentUserId,
        permissions: this.userPermissions || [],
        ...this.approvalRouteContext
      }).filter(action => this.canShowDetailAction(action)).map(action => {
        if (this.featureKey === "oaPurchase" && action.id === "submitOaPurchase") {
          return Object.assign({}, action, {
            disabled: !this.oaPurchaseSubmissionAvailable,
            disabledReason: "审批规则尚未发布或采购提交尚未开放"
          })
        }
        return action
      })
    },
    detailActionGroups() {
      return partitionMobileDetailActions(this.detailActions, 2)
    },
    detailPrimaryActions() {
      return this.detailActionGroups.primary
    },
    detailSecondaryActions() {
      return this.detailActionGroups.secondary
    },
    selectedDetailFields() {
      if (!this.selectedItem || !Array.isArray(this.selectedItem._detailFields)) return []
      return this.selectedItem._detailFields.filter(field => {
        return field && field.value && ["单号", "状态", "摘要"].indexOf(field.label) === -1
      })
    },
    selectedDetailSections() {
      if (!this.selectedItem || !Array.isArray(this.selectedItem._detailSections)) return []
      return this.selectedItem._detailSections.filter(section => {
        return section && section.title && Array.isArray(section.rows) && section.rows.length > 0
      })
    },
    selectedReviewFields() {
      if (!this.selectedItem || !Array.isArray(this.selectedItem._reviewFields)) return []
      return this.selectedItem._reviewFields.filter(field => field && field.label && field.value)
    },
    mobileFormConfig() {
      if (this.formSheet.open && this.formSheet.config) {
        return this.formSheet.config
      }
      return getMobileFormConfig(this.featureKey) || MOBILE_FORM_CONFIG[this.featureKey] || null
    },
    mobileFormFields() {
      return this.mobileFormConfig && Array.isArray(this.mobileFormConfig.fields)
        ? this.mobileFormConfig.fields
        : []
    },
    mobileFormTitle() {
      const title = this.mobileFormConfig ? this.mobileFormConfig.title : this.feature.title
      return this.formSheet.mode === "edit" ? "编辑" + title : "新增" + title
    },
    mobileFormContext() {
      return {
        featureKey: this.featureKey,
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType,
        selectedDeptName: this.selectedDeptName,
        permissions: this.userPermissions || []
      }
    },
    availableMobileSubmitModes() {
      if (!this.mobileFormConfig) return []
      const modes = Array.isArray(this.mobileFormConfig.submitModes) && this.mobileFormConfig.submitModes.length
        ? this.mobileFormConfig.submitModes
        : [{ label: "保存", action: "save" }]
      return modes.filter(mode => mode && this.canUseMobileSubmitMode(mode.action) && !(
        this.featureKey === "transfer" && mode.action === "submit" &&
        this.formSheet.data && this.formSheet.data.transferType === "store_return" &&
        !isMobileFeatureEnabled("storeReturn", this.featureState)
      )).map(mode => {
        if (this.featureKey === "oaPurchase" && mode.action === "submit") {
          return Object.assign({}, mode, {
            disabled: !this.oaPurchaseSubmissionAvailable,
            disabledReason: "审批规则尚未发布或采购提交尚未开放"
          })
        }
        return mode
      })
    },
    activeFilterLabel() {
      return resolveMobileActiveFilterLabel({
        activeActionLabel: this.activeActionLabel,
        resolvedActionLabel: this.resolveActionLabelForQuery(this.featureQuery),
        selectedManagedStoreName: this.selectedManagedStoreName,
        activeStockStatusLabel: this.activeStockStatusLabel,
        searchKeyword: this.searchKeyword,
        activeSearchFieldLabel: this.activeSearchFieldLabel
      })
    }
  },
  watch: {
    "$store.getters.id"() { this.invalidatePurchaseReceive(); this.refreshPurchaseReceiveRecords() },
    "$route.fullPath"() {
      if (this.skipNextRouteApply) {
        this.skipNextRouteApply = false
        return
      }
      this.applyRouteFeature()
    }
  },
  created() {
    startMobileViewportSync()
    if (typeof window !== "undefined") window.addEventListener("erp:dept-changed", this.handlePurchaseReceiveContextChange)
    this.applyRouteFeature()
  },
  activated() { this.purchaseReceiveActive = true; this.refreshPurchaseReceiveRecords() },
  deactivated() { this.purchaseReceiveActive = false; this.invalidatePurchaseReceive() },
  beforeDestroy() {
    this.purchaseReceiveActive = false
    this.invalidatePurchaseReceive()
    if (typeof window !== "undefined") window.removeEventListener("erp:dept-changed", this.handlePurchaseReceiveContextChange)
    stopMobileViewportSync()
    this.routeLoadGuard.invalidate()
    releaseAllMobileOverlays()
  },
  beforeRouteLeave(to, from, next) {
    this.confirmLeaveMobileForm(next)
  },
  beforeRouteUpdate(to, from, next) {
    this.confirmLeaveMobileForm(next)
  },
  methods: {
    markRefreshed() {
      const now = new Date()
      this.refreshedAt = `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`
    },
    applyRouteFeature() {
      this.invalidatePurchaseReceive()
      this.refreshPurchaseReceiveRecords()
      this.routeLoadGuard.invalidate()
      releaseAllMobileOverlays()
      this.applySelectedContext()
      this.items = []
      this.total = 0
      this.pageNum = 1
      this.loading = false
      this.loadingMore = false
      this.errorMessage = ""
      this.selectedItem = null
      this.todoFocusedAction = null
      this.detailLoading = false
      this.actionLoadingKey = ""
      this.setActionMessage("", "status")
      this.closeMobileForm(true)
      this.closeCustomerRecordDialog(true)
      this.resetStockManagedStoreScope()
      this.todoFocus = getTodoFocus(this.featureKey, (this.$route && this.$route.query) || {})
      this.featureQuery = Object.assign({}, this.feature.defaultQuery || {}, this.buildRouteFeatureQuery())
      this.activeActionLabel = this.resolveActionLabelForQuery(this.featureQuery)
      this.activeStockStatus = ""
      this.resetSearchControls()
      this.todoFocus = getTodoFocus(this.featureKey, (this.$route && this.$route.query) || {})
      if (this.featureKey === "oaPurchase") this.loadOaPurchaseAvailability()
      else this.resetOaPurchaseAvailability()
      this.loadData()
    },
    resetOaPurchaseAvailability() {
      this.oaPurchaseAvailabilityToken += 1
      this.oaPurchaseSubmissionAvailable = false
      this.oaPurchaseAvailabilityResolved = false
    },
    loadOaPurchaseAvailability() {
      const token = ++this.oaPurchaseAvailabilityToken
      this.oaPurchaseSubmissionAvailable = false
      this.oaPurchaseAvailabilityResolved = false
      return getPurchaseAvailability().then(response => {
        if (token !== this.oaPurchaseAvailabilityToken) return
        this.oaPurchaseSubmissionAvailable = !!(response.data && response.data.enabled === true)
      }).catch(() => {
        if (token === this.oaPurchaseAvailabilityToken) this.oaPurchaseSubmissionAvailable = false
      }).finally(() => {
        if (token === this.oaPurchaseAvailabilityToken) this.oaPurchaseAvailabilityResolved = true
      })
    },
    buildRouteFeatureQuery() {
      return buildMobileRouteFeatureQuery(
        this.featureKey,
        (this.$route && this.$route.query) || {}
      )
    },
    refresh() {
      this.pageNum = 1
      if (this.featureKey === "oaPurchase") this.loadOaPurchaseAvailability()
      this.loadData()
    },
    resetStockManagedStoreScope() {
      this.stockScopeMode = "current"
      this.managedStoreId = this.allManagedStoreValue
      this.managedStoreOptions = []
      this.managedStoreLoading = false
    },
    setStockScopeMode(mode) {
      const nextMode = mode === "managed" ? "managed" : "current"
      if (nextMode === this.stockScopeMode) return
      this.stockScopeMode = nextMode
      this.pageNum = 1

      if (nextMode === "current") {
        this.managedStoreId = this.allManagedStoreValue
        this.loadData()
        return
      }

      this.loadManagedStoreOptions().then(() => {
        this.loadData()
      })
    },
    handleManagedStoreChange() {
      this.normalizeManagedStoreSelection()
      this.pageNum = 1
      this.loadData()
    },
    loadManagedStoreOptions() {
      if (!this.showStockManagedStoreScope) {
        this.managedStoreOptions = []
        this.managedStoreId = this.allManagedStoreValue
        return Promise.resolve([])
      }

      this.managedStoreLoading = true
      return listShopTree().then(res => {
        const stores = normalizeMobileManagedStoreOptions(res.data || res.rows || [])
        this.managedStoreOptions = stores
        this.normalizeManagedStoreSelection()
        return stores
      }).catch(() => {
        this.managedStoreOptions = []
        this.managedStoreId = this.allManagedStoreValue
        return []
      }).finally(() => {
        this.managedStoreLoading = false
      })
    },
    normalizeManagedStoreSelection() {
      this.managedStoreId = normalizeMobileManagedStoreSelection(
        this.managedStoreId,
        this.managedStoreOptions,
        this.allManagedStoreValue
      )
    },
    loadData(options = {}) {
      const append = options.append === true
      this.applySelectedContext()

      if (this.featureKey === "mine") {
        this.items = mapMobileMineRows({
          selectedDeptName: this.selectedDeptName,
          selectedDeptType: this.selectedDeptType,
          userName: this.$store && this.$store.getters ? (this.$store.getters.nickName || this.$store.getters.name) : ""
        })
        this.total = this.items.length
        this.errorMessage = ""
        this.markRefreshed()
        return
      }

      if (!this.featureKey) {
        this.items = []
        this.total = 0
        this.markRefreshed()
        return
      }

      if (!this.isFeatureAllowed()) {
        this.items = []
        this.total = 0
        this.errorMessage = this.getContextRestrictionMessage()
        this.loading = false
        return
      }

      if (append) {
        this.loadingMore = true
      } else {
        this.loading = true
      }
      this.errorMessage = ""
      const requestFeatureKey = this.featureKey
      const loadToken = this.routeLoadGuard.begin(this.featureKey)
      const isCurrentLoad = () => this.routeLoadGuard.isCurrent(loadToken, this.featureKey)
      const mapperContext = this.mobileMapperContext()
      const requestOptions = {
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType,
        stockScopeMode: this.stockScopeMode,
        managedStoreId: this.managedStoreId,
        query: this.buildRequestQuery(),
        pageNum: this.pageNum,
        pageSize: this.pageSize
      }
      return withMobileRetry(() => fetchMobileFeatureData(requestFeatureKey, requestOptions)).then(result => {
        if (!isCurrentLoad()) return
        const nextItems = mapMobileFeatureRows(requestFeatureKey, result.rows, mapperContext)
        this.items = append ? this.items.concat(nextItems) : nextItems
        this.total = result.total || this.items.length
        return this.handleLoadedTodoFocus(this.items, append).then(() => {
          this.markRefreshed()
        })
      }).catch(error => {
        if (!isCurrentLoad()) return
        if (append) {
          this.pageNum = Math.max(this.pageNum - 1, 1)
          this.showToastError("加载更多失败，请稍后重试")
        } else {
          this.items = []
          this.total = 0
          this.errorMessage = this.resolveMobileErrorMessage(error)
        }
      }).finally(() => {
        if (!isCurrentLoad()) return
        this.loading = false
        this.loadingMore = false
      })
    },
    consumeMobileTodoFocus() {
      if (!this.$route || !this.$router || typeof this.$router.replace !== "function") {
        return Promise.resolve()
      }
      if (!this.$route.query || !this.$route.query.todoType) return Promise.resolve()
      this.skipNextRouteApply = true
      const location = {
        path: this.$route.path,
        query: consumeTodoFocusQuery(this.$route.query || {})
      }
      return Promise.resolve().then(() => this.$router.replace(location)).catch(() => {
        this.skipNextRouteApply = false
      })
    },
    reportHandledTodoFocus() {
      if (this.$modal && typeof this.$modal.msgWarning === "function") {
        this.$modal.msgWarning("事项已处理")
      }
      if (this.$store && typeof this.$store.dispatch === "function") {
        return Promise.resolve().then(() => this.$store.dispatch("todo/refreshSummaries")).catch(() => {})
      }
      return Promise.resolve()
    },
    async handleLoadedTodoFocus(rows, append) {
      if (append || !this.todoFocus) return { status: "none", row: null, actionId: null }

      const focus = this.todoFocus
      this.todoFocus = null
      this.todoFocusedAction = null
      const result = resolveTodoFocusResult(rows, focus)
      await this.consumeMobileTodoFocus()

      if (result.status === "handled") {
        await this.reportHandledTodoFocus()
        return result
      }
      if (result.status !== "found") return result

      await Promise.resolve(this.openItem(result.row))
      if (this.detailLoadFailed) {
        await this.reportHandledTodoFocus()
        return { status: "handled", row: null, actionId: result.actionId }
      }
      const focusedRow = this.selectedItem || result.row
      let focusedShipment = null
      if (focus.shipmentId) {
        focusedShipment = findTodoFocusShipment(
          focusedRow && (focusedRow._raw || focusedRow.raw || focusedRow),
          focus
        )
        if (!focusedShipment) {
          await this.reportHandledTodoFocus()
          return { status: "handled", row: focusedRow, actionId: result.actionId }
        }
      }
      const focusedActionIds = getTodoFocusActionIds(focus)
      const action = getMobileFeatureActions(this.featureKey, focusedRow, {
        selectedDeptType: this.selectedDeptType,
        selectedDeptId: this.selectedDeptId,
        userId: this.currentUserId,
        permissions: this.userPermissions || [],
        approvalTaskId: focus.approvalTaskId || (this.approvalRouteContext && this.approvalRouteContext.approvalTaskId),
        approvalInstanceId: focus.approvalInstanceId || (this.approvalRouteContext && this.approvalRouteContext.approvalInstanceId)
      }).find(candidate => candidate && focusedActionIds.indexOf(candidate.id) > -1)

      if (!action || !this.canShowDetailAction(action)) {
        await this.reportHandledTodoFocus()
        return { status: "handled", row: focusedRow, actionId: result.actionId }
      }
      this.todoFocusedAction = focusedShipment
        ? Object.assign({}, action, { preferredShipmentId: focusedShipment.shipmentId })
        : { id: action.id }
      return { status: "found", row: focusedRow, actionId: action.id }
    },
    loadMore() {
      if (this.loadingMore || !this.hasMoreItems) return
      this.pageNum += 1
      this.loadData({ append: true })
    },
    buildRequestQuery() {
      return buildFeatureRequestQuery({
        featureQuery: this.featureQuery,
        searchKeyword: this.searchKeyword,
        activeSearchFieldConfig: this.activeSearchFieldConfig,
        activeStockStatus: this.activeStockStatus
      })
    },
    normalizeSearchKeyword() {
      return normalizeMobileSearchKeyword(this.searchKeyword)
    },
    resetSearchControls() {
      this.searchKeyword = ""
      this.activeSearchField = this.searchFieldOptions.length ? this.searchFieldOptions[0].key : ""
    },
    selectSearchField(fieldKey) {
      if (fieldKey === this.activeSearchField) return
      this.activeSearchField = fieldKey
      if (this.normalizeSearchKeyword()) {
        this.pageNum = 1
        this.loadData()
      }
    },
    submitSearch() {
      if (!this.searchConfig) return
      this.pageNum = 1
      this.loadData()
    },
    clearSearch() {
      if (!this.normalizeSearchKeyword()) return
      this.searchKeyword = ""
      this.pageNum = 1
      this.loadData()
    },
    clearListFilters() {
      this.searchKeyword = ""
      this.activeStockStatus = ""
      this.featureQuery = createClearedFeatureQuery(this.feature.defaultQuery)
      this.activeActionLabel = this.resolveActionLabelForQuery(this.featureQuery)
      this.stockScopeMode = "current"
      this.managedStoreId = this.allManagedStoreValue
      this.pageNum = 1
      this.loadData()
    },
    isBlockingListState(state) {
      return Boolean(state && [
        "session-expired",
        "permission-error",
        "context-required",
        "context-mismatch",
        "network-error"
      ].includes(state.type))
    },
    statusChipClass(status) {
      const text = String(status || "")
      if (/草稿|停用|关闭|禁用|作废|已取消/.test(text)) return "mobile-status-chip--draft"
      if (/驳回|失败|异常|退回|拒绝|拒签|超期|过期|错误|风险/.test(text)) return "mobile-status-chip--danger"
      if (/待|审批|补充|临期|确认|验收|审核|处理|签署/.test(text) && !/已/.test(text)) return "mobile-status-chip--pending"
      if (/中|识别|同步|进行|上传|加载/.test(text)) return "mobile-status-chip--processing"
      if (/完成|通过|付款|成功|已验收|已签署|正常|启用|合作/.test(text)) return "mobile-status-chip--success"
      return "mobile-status-chip--neutral"
    },
    handleListStateAction(actionId) {
      if (actionId === "relogin") {
        this.$store.dispatch("LogOut").then(() => {
          this.$router.push(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`).catch(() => {})
        }).catch(() => {
          this.$router.push("/login").catch(() => {})
        })
        return
      }
      if (actionId === "switch-context" || actionId === "select-context") {
        this.goSelectShop()
        return
      }
      if (actionId === "clear-filters") {
        this.clearListFilters()
        return
      }
      if (actionId === "create") {
        const createAction = this.featureActionItems.find(action => action && action.behavior === "create-form")
        if (createAction) {
          this.handleAction(createAction)
          return
        }
      }
      this.refresh()
    },
    selectStockStatus(status) {
      if (status === this.activeStockStatus) return
      this.activeStockStatus = status
      this.pageNum = 1
      this.loadData()
    },
    handleAction(action) {
      if (action && action.actionId) {
        this.runTopFeatureAction(action)
        return
      }
      if (action && action.behavior === "export") {
        this.handleExportAction(action)
        return
      }
      if (action && action.behavior === "create-form") {
        this.openMobileForm(action)
        return
      }
      if (action && action.behavior === "select-shop") {
        this.goSelectShop()
        return
      }
      if (action && action.behavior === "refresh") {
        this.activeActionLabel = action.label
        this.pageNum = 1
        this.loadData()
        return
      }
      if (action && action.behavior === "logout") {
        this.logoutMobile()
        return
      }
      if (action && action.path) {
        if (this.canOpenPath(action.path)) {
          this.$router.push(action.path).catch(() => {})
        }
        return
      }

      this.activeActionLabel = action && action.label ? action.label : ""
      this.featureQuery = action && action.query !== undefined
        ? Object.assign({}, action.query)
        : Object.assign({}, this.feature.defaultQuery || {})
      this.pageNum = 1
      this.loadData()
    },
    getTopActionKey(action) {
      if (!action) return ""
      return action.actionId || action.behavior || action.label || ""
    },
    isTopActionLoading(action) {
      return !!this.actionLoadingKey && this.actionLoadingKey === this.getTopActionKey(action)
    },
    handleExportAction(action) {
      const exportConfig = MOBILE_EXPORT_CONFIG[this.featureKey]
      const actionKey = this.getTopActionKey(action)

      if (!exportConfig || this.actionLoadingKey) return
      if (!this.isFeatureAllowed()) {
        this.showToastError(this.getContextRestrictionMessage())
        return
      }
      if (!this.download || !this.exportFileName) {
        this.showToastError("当前环境暂不支持导出")
        return
      }

      this.confirmExportAction(exportConfig).then(() => {
        this.actionLoadingKey = actionKey
        return Promise.resolve(this.download(
          exportConfig.url,
          this.buildExportQuery(exportConfig),
          this.exportFileName(exportConfig.name)
        )).catch(error => {
          this.showActionError(error)
        }).finally(() => {
          this.actionLoadingKey = ""
        })
      }).catch(() => {})
    },
    confirmExportAction(exportConfig) {
      const message = "确认导出当前查询条件下的" + exportConfig.name + "？"
      return this.requestMobileConfirm({
        title: "确认导出",
        message,
        confirmText: "导出",
        cancelText: "取消"
      })
    },
    buildExportQuery(exportConfig) {
      return buildFeatureExportQuery({
        requestQuery: this.buildRequestQuery(),
        showManagedStoreSelect: this.showManagedStoreSelect,
        managedStoreId: this.managedStoreId,
        allManagedStoreValue: this.allManagedStoreValue,
        exportConfig,
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType
      })
    },
    runTopFeatureAction(action) {
      if (!action || this.actionLoadingKey) return

      this.confirmDetailAction(action).then(() => {
        this.actionLoadingKey = this.getTopActionKey(action)
        this.setActionMessage("", "status")
        return runMobileFeatureAction(this.featureKey, action.actionId, this.resolveTopActionItem(action), {
          selectedDeptId: this.selectedDeptId,
          selectedDeptType: this.selectedDeptType,
          query: this.buildRequestQuery()
        }).then(res => {
          this.showActionSuccess(action, res)
          this.pageNum = 1
          this.loadData()
          acknowledgeTransferCommand(res)
        }).catch(error => {
          this.showActionError(error)
        }).finally(() => {
          this.actionLoadingKey = ""
        })
      }).catch(() => {})
    },
    resolveTopActionItem() {
      return null
    },
    resolveActionLabelForQuery(query) {
      return resolveMobileActionLabelForQuery(this.feature.actions, query)
    },
    canShowTopAction(action) {
      if (!action) return false
      if (this.errorMessage) return false
      if (!this.isFeatureAllowed()) return false
      if (!this.canUseActionInSelectedContext(action)) return false
      if (!isMobileFeatureEnabled(action.featureFlag, this.featureState)) return false
      if (action.behavior === "create-form") {
        return this.canCreateMobileForm(action)
      }
      return this.hasAnyPermission(action.permissions || MOBILE_ACTION_PERMISSIONS[action.actionId])
    },
    canShowDetailAction(action) {
      if (!action) return false
      if (!isMobileFeatureEnabled(action.featureFlag, this.featureState)) return false
      if (action.behavior === "edit-form") {
        return this.canEditMobileForm(action, this.selectedItem)
      }
      return this.hasAnyPermission(action.permissions || MOBILE_ACTION_PERMISSIONS[action.id])
    },
    canCreateMobileForm(action) {
      const config = this.resolveMobileFormConfig(action)
      if (this.featureKey === "transfer" && this.selectedDeptType !== "STORE") {
        return false
      }
      return isMobileFeatureEnabled(config && config.featureFlag, this.featureState) &&
        this.hasAnyPermission(MOBILE_FORM_CREATE_PERMISSIONS[this.featureKey])
    },
    canEditMobileForm(action, item) {
      const config = this.resolveMobileFormConfig(action, item)
      if (this.featureKey === "transfer" && this.selectedDeptType !== "STORE") {
        return false
      }
      return isMobileFeatureEnabled(config && config.featureFlag, this.featureState) &&
        this.hasAnyPermission(MOBILE_FORM_EDIT_PERMISSIONS[this.featureKey])
    },
    canUseMobileSubmitMode(submitAction) {
      const rules = MOBILE_FORM_SUBMIT_PERMISSIONS[this.featureKey]
      if (!rules) return true
      return this.hasAnyPermission(rules[submitAction])
    },
    canUseActionInSelectedContext(action) {
      const allowedDeptTypes = action && action.allowedDeptTypes
      if (!Array.isArray(allowedDeptTypes) || allowedDeptTypes.length === 0) {
        return true
      }
      return allowedDeptTypes.indexOf(this.selectedDeptType) > -1
    },
    hasAnyPermission(permissions) {
      if (!Array.isArray(permissions) || permissions.length === 0) {
        return true
      }
      const userPermissions = this.userPermissions || []
      if (userPermissions.indexOf("*:*:*") > -1) return true
      return permissions.some(permission => userPermissions.indexOf(permission) > -1)
    },
    goSelectShop() {
      this.$router.push({ path: "/select-shop", query: { redirect: this.$route.fullPath } }).catch(() => {})
    },
    logoutMobile() {
      const logout = () => {
        if (this.$store && this.$store.dispatch) {
          return this.$store.dispatch("LogOut").then(() => {
            location.href = "/index"
          })
        }
        location.href = "/index"
        return Promise.resolve()
      }

      this.requestMobileConfirm({
        title: "退出登录",
        message: "确定注销并退出系统吗？",
        confirmText: "退出",
        cancelText: "取消"
      }).then(logout).catch(() => {})
    },
    applySelectedContext() {
      const previousDeptId = this.selectedDeptId
      const previousDeptType = this.selectedDeptType
      const context = getSelectedDeptContext()
      this.selectedDeptName = this.formatDeptLabel(context)
      this.selectedDeptId = context.deptId
      this.selectedDeptType = context.deptType
      if (
        (previousDeptId || previousDeptType) &&
        (String(previousDeptId || "") !== String(this.selectedDeptId || "") || previousDeptType !== this.selectedDeptType)
      ) {
        this.resetStockManagedStoreScope()
      }
      if (this.selectedDeptType !== "STORE" && this.stockScopeMode !== "current") {
        this.resetStockManagedStoreScope()
      }
    },
    mobileMapperContext() {
      return {
        selectedDeptType: this.selectedDeptType,
        selectedDeptName: this.selectedDeptName,
        permissions: this.userPermissions || []
      }
    },
    formatDeptLabel(context) {
      if (!context.deptName) return ""
      const prefix = context.isWarehouse ? "仓库" : context.isStore ? "门店" : "组织"
      return prefix + "：" + context.deptName
    },
    isFeatureAllowed() {
      const allowedDeptTypes = this.feature.allowedDeptTypes
      if (!Array.isArray(allowedDeptTypes) || allowedDeptTypes.length === 0) {
        return true
      }
      return allowedDeptTypes.indexOf(this.selectedDeptType) > -1
    },
    getContextRestrictionMessage() {
      const allowedDeptTypes = this.feature.allowedDeptTypes || []
      if (allowedDeptTypes.indexOf("WAREHOUSE") > -1 && allowedDeptTypes.indexOf("STORE") === -1) {
        if (this.selectedDeptType === "STORE") {
          return "当前选择的是门店，" + this.feature.title + "需要切换到仓库后使用"
        }
        return "当前组织不支持" + this.feature.title + "，请切换到仓库后使用"
      }
      if (allowedDeptTypes.indexOf("STORE") > -1 && allowedDeptTypes.indexOf("WAREHOUSE") === -1) {
        if (this.selectedDeptType === "WAREHOUSE") {
          return "当前选择的是仓库，" + this.feature.title + "需要切换到门店后使用"
        }
        return "当前组织不支持" + this.feature.title + "，请切换到门店后使用"
      }
      return "当前组织不支持" + this.feature.title + "，请切换到门店或仓库后使用"
    },
    openItem(item) {
      this.todoFocusedAction = null
      this.selectedItem = item
      this.selectedItemEditSource = null
      this.setActionMessage("", "status")
      this.detailLoading = false
      this.detailLoadFailed = false

      if (!this.featureKey || this.featureKey === "mine") {
        return
      }

      return this.loadSelectedItemDetail(item)
    },
    loadSelectedItemDetail(item) {
      if (!item || !this.featureKey || this.featureKey === "mine") return Promise.resolve(item)

      const requestToken = ++this.detailRequestToken
      this.detailLoading = true
      this.detailLoadFailed = false
      this.setActionMessage("", "status")
      return withMobileRetry(() => fetchMobileFeatureDetail(this.featureKey, item, Object.assign({}, this.approvalRouteContext, { inventoryPermissions: this.userPermissions || [] }))).then(detail => {
        if (requestToken !== this.detailRequestToken || !this.selectedItem) return
        const detailRow = Object.assign({}, item._raw || {}, detail || {})
        this.selectedItemEditSource = cloneMobileEditFormSource(detailRow)
        const mappedDetail = mapMobileFeatureRows(this.featureKey, [detailRow], this.mobileMapperContext())[0]
        this.selectedItem = mappedDetail || item
        this.detailLoadFailed = false
        this.setActionMessage(detailRow._specialistSummaryOnly ? "当前岗位可处理单据；完整明细需要查询权限" : "", "status")
      }).catch(() => {
        if (requestToken !== this.detailRequestToken || !this.selectedItem) return
        this.detailLoadFailed = true
        this.setActionMessage("完整详情加载失败，写操作已暂停，请重新加载详情", "error")
      }).finally(() => {
        if (requestToken === this.detailRequestToken) this.detailLoading = false
      })
    },
    retrySelectedItemDetail() {
      if (!this.selectedItem || this.detailLoading) return Promise.resolve()
      return this.loadSelectedItemDetail(this.selectedItem)
    },
    closeItem() {
      this.detailRequestToken += 1
      this.selectedItem = null
      this.selectedItemEditSource = null
      this.todoFocusedAction = null
      this.detailLoading = false
      this.detailLoadFailed = false
      this.actionLoadingKey = ""
      this.setActionMessage("", "status")
      this.closeActionDialog()
      this.closeCustomerRecordDialog(true)
    },
    refreshAndClose() {
      this.closeItem()
      this.refresh()
    },
    handleDetailAction(action) {
      if (!action || this.actionLoadingKey || this.detailLoading) {
        if (this.detailLoading) this.setActionMessage("完整详情加载中，请稍后再处理", "status")
        return
      }
      if (this.detailLoadFailed) {
        this.setActionMessage("完整详情加载失败，请重新加载详情后再处理", "error")
        return
      }
      if (action && action.disabled) {
        const message = action.disabledReason || "当前操作暂不可用"
        this.setActionMessage(message, "status")
        this.showToastError(message)
        return
      }

      const focusedAction = this.todoFocusedAction
      this.todoFocusedAction = null
      if (focusedAction && focusedAction.id === action.id) {
        action = Object.assign({}, action, focusedAction)
      }

      if (action.behavior === "edit-form") {
        if (!this.selectedItem) return
        return this.openHydratedMobileEditForm(action)
      }

      if (action.behavior === "preview-customer-photo") {
        this.previewCustomerPhoto(action)
        return
      }

      if (action.behavior === "customer-record") {
        this.openCustomerRecordDialog(action)
        return
      }

      if (action.behavior === "preview-customer-photo") {
        this.previewCustomerPhoto(action)
        return
      }

      if (action.behavior === "customer-record") {
        this.openCustomerRecordDialog(action)
        return
      }

      if (this.shouldUseActionDialog(action)) {
        this.openActionDialog(action)
        return
      }

      this.runDetailAction(action)
    },
    previewCustomerPhoto(action) {
      const row = this.selectedItem && (this.selectedItem._raw || this.selectedItem.raw || this.selectedItem)
      const customerId = row && row.customerId
      if (!customerId || this.actionLoadingKey) return
      const popup = window.open("about:blank", "_blank")
      if (popup) popup.opener = null
      this.actionLoadingKey = action.id
      this.setActionMessage("", "status")
      getCustomerServiceCardPhoto(customerId, "preview").then(blob => {
        const target = URL.createObjectURL(blob)
        if (popup) popup.location.replace(target)
        else this.$download.saveAs(blob, "客户照片-" + customerId)
        window.setTimeout(() => URL.revokeObjectURL(target), 60000)
      }).catch(error => {
        if (popup && !popup.closed) popup.close()
        this.showActionError(error)
      }).finally(() => {
        this.actionLoadingKey = ""
      })
    },
    shouldUseActionDialog(action) {
      if (!action) return false
      return !!action.requiresComment || !!action.confirmText || [
        "qualityCheckPurchase",
        "receivePurchaseAll",
        "deliverDeliveryNoticeAll",
        "saveStockCheckInput",
        "submitStockCheck",
        "approveStockCheck",
        "returnStockCheck",
        "rejectStockCheck",
        "confirmTransferSource",
        "deliverTransferAll",
        "receiveTransferShipment"
      ].indexOf(action.id) > -1
    },
    invalidatePurchaseReceive() {
      this.purchaseReceiveEpoch += 1
      this.purchaseReceiveListEpoch += 1
      this.purchaseReceiveBusy = false
      if (this.actionDialog && this.actionDialog.action && this.actionDialog.action.id === "receivePurchaseAll") this.closeActionDialog()
      if (this.actionLoadingKey === "receivePurchaseAll") this.actionLoadingKey = ""
    },
    handlePurchaseReceiveContextChange() {
      this.invalidatePurchaseReceive()
      this.applySelectedContext()
      this.refreshPurchaseReceiveRecords()
    },
    isCurrentPurchaseReceive(operation, detail = true) {
      if (!this.purchaseReceiveActive || this.featureKey !== "purchase" || operation.epoch !== this.purchaseReceiveEpoch) return false
      if (detail && operation.detailToken !== this.detailRequestToken) return false
      try { getPurchaseReceiveRecovery().assertCurrent(operation.receiveScope); return true } catch (_) { return false }
    },
    async refreshPurchaseReceiveRecords() {
      const epoch = ++this.purchaseReceiveListEpoch
      if (this.featureKey !== "purchase" || !this.hasAnyPermission(["inv:purchase:receive"])) {
        this.purchaseReceiveRecords = []; this.purchaseReceiveError = ""; return
      }
      try {
        const records = await getPurchaseReceiveRecovery().list()
        if (this.purchaseReceiveActive && epoch === this.purchaseReceiveListEpoch) { this.purchaseReceiveRecords = records; this.purchaseReceiveError = "" }
      } catch (error) {
        if (this.purchaseReceiveActive && epoch === this.purchaseReceiveListEpoch) { this.purchaseReceiveRecords = []; this.purchaseReceiveError = error.message }
      }
    },
    async recoverMobilePurchaseReceive(record) {
      if (this.purchaseReceiveBusy || this.actionLoadingKey) return
      const recovery = getPurchaseReceiveRecovery()
      let operation
      this.purchaseReceiveBusy = true
      try {
        operation = { receiveScope: recovery.capture(), epoch: this.purchaseReceiveEpoch }
        await this.requestMobileConfirm({ title: "核对上一笔收货", message: "核对采购单 " + record.orderId + " 的原收货结果；本次新输入不会提交。" })
        if (!this.isCurrentPurchaseReceive(operation, false)) return
        const result = await runMobileFeatureAction("purchase", "receivePurchaseAll", { orderId: record.orderId }, {
          receiveScope: operation.receiveScope, receiveRecoveryOnly: true, receiveRequestId: record.requestId,
          isCurrent: () => this.isCurrentPurchaseReceive(operation, false)
        })
        if (!this.isCurrentPurchaseReceive(operation, false)) return
        this.showActionSuccess({ successText: purchaseReceiveResultMessage(result) }, { msg: purchaseReceiveResultMessage(result) })
        await recovery.acknowledge(result)
        this.refresh()
      } catch (error) {
        if ((!operation || this.isCurrentPurchaseReceive(operation, false)) && error.message !== "cancelled") this.showActionError(error)
      } finally {
        if (!operation || operation.epoch === this.purchaseReceiveEpoch) this.purchaseReceiveBusy = false
        this.refreshPurchaseReceiveRecords()
      }
    },
    async openPurchaseReceiveDialog(action) {
      if (!this.selectedItem || this.actionLoadingKey || this.detailLoading || this.detailLoadFailed) return
      const recovery = getPurchaseReceiveRecovery()
      let operation
      this.actionLoadingKey = "receivePurchaseAll"
      try {
        operation = { epoch: this.purchaseReceiveEpoch, detailToken: this.detailRequestToken,
          receiveScope: recovery.capture(), item: JSON.parse(JSON.stringify(this.selectedItem)) }
        const row = operation.item._raw || operation.item.raw || operation.item
        const head = await recovery.inspect(row.orderId, operation.receiveScope)
        if (!this.isCurrentPurchaseReceive(operation)) return
        if (head.pending) {
          await this.refreshPurchaseReceiveRecords()
          this.showActionError(new Error("上一笔收货结果待核对，请先使用页面上方的核对入口，新到货暂未提交。"))
          return
        }
        this.actionDialog = Object.assign(operation, { open: true, action, payload: {}, receiveObservedRequestId: head.observedRequestId })
      } catch (error) { if (!operation || this.isCurrentPurchaseReceive(operation)) this.showActionError(error) }
      finally { if (!operation || this.isCurrentPurchaseReceive(operation)) this.actionLoadingKey = "" }
    },
    async runPurchaseReceiveAction(action, actionPayload, operation) {
      if (this.actionLoadingKey || !operation.receiveScope || !this.isCurrentPurchaseReceive(operation)) return
      const payload = JSON.parse(JSON.stringify(actionPayload))
      this.actionLoadingKey = "receivePurchaseAll"
      try {
        const result = await runMobileFeatureAction("purchase", "receivePurchaseAll", operation.item, {
          actionPayload: payload, receiveScope: operation.receiveScope, receiveObservedRequestId: operation.receiveObservedRequestId,
          isCurrent: () => this.isCurrentPurchaseReceive(operation)
        })
        if (!this.isCurrentPurchaseReceive(operation)) return
        this.showActionSuccess(action, { msg: purchaseReceiveResultMessage(result) })
        await getPurchaseReceiveRecovery().acknowledge(result)
        if (!this.isCurrentPurchaseReceive(operation)) return
        this.closeItem()
        this.refresh()
      } catch (error) { if (this.isCurrentPurchaseReceive(operation)) this.showActionError(error) }
      finally {
        if (this.isCurrentPurchaseReceive(operation)) this.actionLoadingKey = ""
        this.refreshPurchaseReceiveRecords()
      }
    },
    openActionDialog(action) {
      if (action && action.id === "receivePurchaseAll") return this.openPurchaseReceiveDialog(action)
      this.actionDialog = {
        open: true,
        action,
        payload: {}
      }
    },
    closeActionDialog() {
      this.actionDialog = {
        open: false,
        action: null,
        payload: {}
      }
    },
    openCustomerRecordDialog(action) {
      if (!this.selectedItem) return
      this.customerRecordDialog = {
        open: true,
        action,
        saving: false,
        error: ""
      }
    },
    closeCustomerRecordDialog(force = false) {
      if (!force && this.customerRecordDialog.saving) return
      this.customerRecordDialog = {
        open: false,
        action: null,
        saving: false,
        error: ""
      }
    },
    submitCustomerServiceRecord(actionPayload) {
      const action = this.customerRecordDialog.action
      if (!action || !this.selectedItem || this.customerRecordDialog.saving) return
      this.customerRecordDialog.saving = true
      this.customerRecordDialog.error = ""
      this.actionLoadingKey = action.id
      runMobileFeatureAction(this.featureKey, action.id, this.selectedItem, {
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType,
        actionPayload: actionPayload || {}
      }).then(res => {
        this.showActionSuccess(action, res)
        this.closeCustomerRecordDialog(true)
        this.closeItem()
        this.pageNum = 1
        this.loadData()
      }).catch(error => {
        this.customerRecordDialog.error = mobileErrorMessage(error, "服务记录追加失败，请稍后重试")
      }).finally(() => {
        this.actionLoadingKey = ""
        if (this.customerRecordDialog.open) this.customerRecordDialog.saving = false
      })
    },
    requestMobileConfirm(options = {}) {
      if (this.mobileConfirmReject) {
        this.mobileConfirmReject(new Error("cancelled"))
      }
      return new Promise((resolve, reject) => {
        this.mobileConfirmResolve = resolve
        this.mobileConfirmReject = reject
        this.mobileConfirm = {
          open: true,
          title: options.title || "确认操作",
          message: options.message || "确认执行该操作？",
          confirmText: options.confirmText || "确定",
          cancelText: options.cancelText || "取消"
        }
      })
    },
    resetMobileConfirm() {
      this.mobileConfirm = {
        open: false,
        title: "确认操作",
        message: "",
        confirmText: "确定",
        cancelText: "取消"
      }
      this.mobileConfirmResolve = null
      this.mobileConfirmReject = null
    },
    confirmMobileConfirm() {
      const resolve = this.mobileConfirmResolve
      this.resetMobileConfirm()
      if (resolve) resolve()
    },
    cancelMobileConfirm() {
      const reject = this.mobileConfirmReject
      this.resetMobileConfirm()
      if (reject) reject(new Error("cancelled"))
    },
    confirmActionDialog(actionPayload) {
      const dialog = this.actionDialog
      const action = dialog.action
      this.closeActionDialog()
      if (action && action.id === "receivePurchaseAll") return this.runPurchaseReceiveAction(action, actionPayload || {}, dialog)
      return this.runDetailAction(action, actionPayload || {}, true)
    },
    runDetailAction(action, actionPayload = {}, confirmed = false) {
      if (action && action.id === "receivePurchaseAll") return this.openPurchaseReceiveDialog(action)
      if (!action || this.actionLoadingKey || this.detailLoading) {
        if (this.detailLoading) this.setActionMessage("完整详情加载中，请稍后再处理", "status")
        return
      }
      if (this.detailLoadFailed) {
        this.setActionMessage("完整详情加载失败，请重新加载详情后再处理", "error")
        return
      }

      const executeAction = () => {
        this.actionLoadingKey = action.id
        this.setActionMessage("", "status")
        return runMobileFeatureAction(this.featureKey, action.id, this.selectedItem, {
          selectedDeptId: this.selectedDeptId,
          selectedDeptType: this.selectedDeptType,
          ...this.approvalRouteContext,
          actionPayload
        }).then(res => {
          this.showActionSuccess(action, res)
          if (/^(approve|return|reject|withdraw)(StockCheck|Transfer)$/.test(action.id) && this.$store && typeof this.$store.dispatch === "function") {
            this.$store.dispatch("todo/invalidateAfterMutation").catch(() => {})
          }
          this.closeItem()
          this.refresh()
          acknowledgeTransferCommand(res)
        }).catch(error => {
          this.showActionError(error)
        }).finally(() => {
          this.actionLoadingKey = ""
        })
      }

      if (confirmed) {
        executeAction()
        return
      }

      this.confirmDetailAction(action).then(executeAction).catch(() => {})
    },
    openHydratedMobileEditForm(action) {
      const sourceItem = this.selectedItem
      if (!sourceItem) return Promise.resolve()

      if (["transfer", "replenishment"].indexOf(this.featureKey) === -1) {
        const editSource = cloneMobileEditFormSource(this.selectedItemEditSource || sourceItem)
        this.closeItem()
        this.$nextTick(() => this.openMobileForm(action, editSource))
        return Promise.resolve(editSource)
      }

      const requestToken = ++this.detailRequestToken
      let hydratedItem = null
      let availabilityStage = false
      this.detailLoading = true
      this.detailLoadFailed = false
      this.setActionMessage("正在读取完整草稿并复查来源库存", "status")

      return withMobileRetry(() => fetchMobileFeatureDetail(
        this.featureKey,
        sourceItem,
        this.approvalRouteContext
      )).then(detail => {
        if (requestToken !== this.detailRequestToken || !this.selectedItem) return null
        const rawSource = sourceItem._raw || sourceItem.raw || sourceItem
        hydratedItem = cloneMobileEditFormSource(Object.assign({}, rawSource || {}, detail || {}))
        const config = this.resolveMobileFormConfig(action, hydratedItem)
        const formData = this.buildMobileFormData(config, hydratedItem)
        availabilityStage = true
        this.setActionMessage("正在复查来源库存可用量", "status")
        if (String(formData.transferType || "") === "cross_store" &&
          String(hydratedItem.sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED") {
          return Promise.resolve(Object.assign({}, formData, {
            details: (formData.details || []).map(detailRow => Object.assign({}, detailRow, {
              availableQuantity: undefined,
              availabilityStatus: "unknown"
            }))
          }))
        }
        return refreshMobileTransferAvailability(formData, query => listStock(query, { silentError: true }))
      }).then(refreshedData => {
        if (!refreshedData || requestToken !== this.detailRequestToken || !this.selectedItem) return null
        this.selectedItemEditSource = hydratedItem
        this.detailLoadFailed = false
        this.setActionMessage("", "status")
        this.closeItem()
        this.$nextTick(() => this.openMobileForm(action, hydratedItem, refreshedData))
        return hydratedItem
      }).catch(error => {
        if (requestToken !== this.detailRequestToken || !this.selectedItem) return null
        if (availabilityStage) {
          const reason = mobileErrorMessage(error, "请检查网络后重试")
          const message = "来源库存复查失败，无法安全编辑草稿：" + reason
          this.setActionMessage(message, "error")
          this.showToastError(message)
          return null
        }
        this.detailLoadFailed = true
        const message = "草稿完整信息读取失败，请重试后再编辑"
        this.setActionMessage(message, "error")
        this.showToastError(message)
        return null
      }).finally(() => {
        if (requestToken === this.detailRequestToken) this.detailLoading = false
      })
    },
    openMobileForm(action, item, preparedData) {
      const config = this.resolveMobileFormConfig(action, item)
      if (!config) {
        this.showToastError("当前模块暂不支持手机表单")
        return
      }
      if (!this.isFeatureAllowed()) {
        this.showToastError(this.getContextRestrictionMessage())
        return
      }
      if (item ? !this.canEditMobileForm(action, item) : !this.canCreateMobileForm(action)) {
        this.showToastError(item ? "当前账号没有编辑权限" : "当前账号没有新增权限")
        return
      }

      const data = preparedData && typeof preparedData === "object"
        ? preparedData
        : this.buildMobileFormData(config, item)
      this.formUploadEpoch += 1
      this.formSheet = {
        open: true,
        mode: item ? "edit" : "create",
        saving: false,
        error: "",
        validationError: null,
        config,
        data,
        initialData: this.cloneMobileFormData(data)
      }
    },
    requestCloseMobileForm() {
      if (this.formSheet.saving) return
      const snapshot = snapshotMobileFormSheet(this.formSheet)
      this.confirmDiscardMobileForm().then(() => {
        this.closeMobileForm(true)
      }).catch(() => {
        if (snapshot.open) this.formSheet = restoreMobileFormSheet(snapshot)
      })
    },
    closeMobileForm(force = false) {
      if (!force && this.isMobileFormDirty()) {
        this.requestCloseMobileForm()
        return
      }
      this.formSheet = {
        open: false,
        mode: "create",
        saving: false,
        error: "",
        validationError: null,
        fixedAssetPrecheck: null,
        config: null,
        data: {},
        initialData: {}
      }
    },
    cloneMobileFormData(data) {
      try {
        return JSON.parse(JSON.stringify(data || {}))
      } catch (error) {
        return Object.assign({}, data || {})
      }
    },
    snapshotMobileFormData(data) {
      try {
        return JSON.stringify(data || {})
      } catch (error) {
        return ""
      }
    },
    isMobileFormDirty() {
      return this.formSheet.open &&
        this.snapshotMobileFormData(this.formSheet.data) !== this.snapshotMobileFormData(this.formSheet.initialData)
    },
    confirmDiscardMobileForm() {
      if (!this.isMobileFormDirty()) return Promise.resolve()
      return this.requestMobileConfirm({
        title: "放弃修改",
        message: "当前表单有未保存内容，确定放弃修改吗？",
        confirmText: "放弃修改",
        cancelText: "继续编辑"
      })
    },
    confirmLeaveMobileForm(next) {
      if (!this.isMobileFormDirty()) {
        next()
        return
      }
      this.confirmDiscardMobileForm().then(() => {
        this.closeMobileForm(true)
        next()
      }).catch(() => {
        next(false)
      })
    },
    buildMobileFormData(config, item) {
      return createMobileFormData(config, item, {
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType
      })
    },
    resolveMobileFormConfig(action, item) {
      if (this.featureKey !== "transfer") {
        return getMobileFormConfig(this.featureKey) || MOBILE_FORM_CONFIG[this.featureKey] || null
      }
      const row = item && (item._raw || item.raw || item)
      const transferType = action && action.transferType
        ? action.transferType
        : (row && row.transferType)
      return getMobileTransferFormConfig(transferType)
    },
    handleMobileFormInput(data) {
      if (this.formSheet.saving) return
      this.formSheet.data = data || {}
      if (this.formSheet.validationError || this.formSheet.error) {
        this.formSheet.validationError = null
        this.formSheet.error = ""
      }
      if (this.formSheet.fixedAssetPrecheck) this.formSheet.fixedAssetPrecheck = null
    },
    submitMobileForm(submitAction = "save") {
      const config = this.formSheet.config || this.mobileFormConfig
      if (!config || this.formSheet.saving) return

      if (!this.canUseMobileSubmitMode(submitAction)) {
        this.formSheet.validationError = null
        this.formSheet.error = "当前账号没有保存权限"
        this.showToastError(this.formSheet.error)
        return
      }
      if (this.featureKey === "oaPurchase" && submitAction === "submit" && !this.oaPurchaseSubmissionAvailable) {
        this.formSheet.validationError = null
        this.formSheet.error = "审批规则尚未发布或采购提交尚未开放"
        this.showToastError(this.formSheet.error)
        return
      }

      const validationError = this.getMobileFormValidationError()
      if (validationError) {
        this.formSheet.validationError = validationError
        this.formSheet.error = validationError.message
        return
      }

      this.formSheet.saving = true
      this.formSheet.error = ""
      this.formSheet.validationError = null

      const submittedSheet = this.formSheet
      const submittedFeature = this.featureKey
      const submittedData = this.snapshotMobileFormData(this.formSheet.data)
      const isCurrent = () => this.formSheet === submittedSheet && this.formSheet.open &&
        this.featureKey === submittedFeature
      const isPrecheckCurrent = () => isCurrent() && this.snapshotMobileFormData(this.formSheet.data) === submittedData
      saveMobileFeatureForm(submittedFeature, this.normalizeMobileFormPayload(config, submitAction), { isCurrent: isPrecheckCurrent }).then(res => {
        if (!isCurrent()) return
        const successText = submitAction === "submit"
          ? "保存并提交成功"
          : (this.formSheet.mode === "edit" ? "保存成功" : "新增成功")
        this.showActionSuccess({ successText }, res)
        this.closeMobileForm(true)
        this.closeItem()
        this.pageNum = 1
        this.loadData()
        acknowledgeTransferCommand(res)
      }).catch(error => {
        if (!isCurrent()) return
        const fixedAssetPrecheck = submittedFeature === "fixedAssetRepair"
          ? getFixedAssetPrecheckFromError(error) : null
        this.$set(this.formSheet, "fixedAssetPrecheck", fixedAssetPrecheck)
        this.formSheet.validationError = null
        this.formSheet.error = mobileErrorMessage(error, "保存失败，请稍后重试")
        this.showToastError(this.formSheet.error)
      }).finally(() => {
        if (this.formSheet === submittedSheet && this.formSheet.open) {
          this.formSheet.saving = false
        }
      })
    },
    validateMobileForm() {
      return validateMobileFormData(this.formSheet.config || this.mobileFormConfig, this.formSheet.data)
    },
    getMobileFormValidationError() {
      return getMobileFormValidationErrorData(this.formSheet.config || this.mobileFormConfig, this.formSheet.data)
    },
    normalizeMobileFormPayload(config, submitAction) {
      return buildMobileFormPayload(config, this.formSheet.data, {
        submitAction,
        selectedDeptId: this.selectedDeptId,
        selectedDeptType: this.selectedDeptType
      })
    },
    confirmDetailAction(action) {
      return this.requestMobileConfirm({
        title: "确认操作",
        message: action.confirmText || "确认执行该操作？",
        confirmText: "确定",
        cancelText: "取消"
      })
    },
    showActionSuccess(action, response) {
      const message = (response && response.msg) || action.successText || "操作成功"
      if (this.$modal && this.$modal.msgSuccess) {
        this.$modal.msgSuccess(message)
        return
      }
      if (this.$message && this.$message.success) {
        this.$message.success(message)
      }
    },
    showActionError(error) {
      const message = mobileErrorMessage(error, "操作失败，请稍后重试")
      this.setActionMessage(message, "error")
      this.showToastError(message)
    },
    setActionMessage(message, type = "status") {
      this.actionMessage = message
      this.actionMessageType = type === "error" ? "error" : "status"
    },
    resolveMobileErrorMessage(error) {
      const status = error && error.response ? error.response.status : ""
      const code = error && error.code ? error.code : status
      const message = mobileErrorMessage(error, "接口暂不可用，请稍后重试")
      const normalizedMessage = this.normalizeMobileErrorMessage(message)
      if (normalizedMessage) {
        return normalizedMessage
      }
      if (
        String(code) === "403" ||
        /403|没有权限|无权限|未授权|NotPermission/i.test(message)
      ) {
        return "当前账号没有该模块权限，请联系管理员开通后再使用"
      }
      if (String(code) === "401" || /401|会话|登录状态/i.test(message)) {
        return "登录状态已过期，请重新登录"
      }
      return message
    },
    normalizeMobileErrorMessage(message) {
      if (/当前用户无权选择该店铺|无权选择该店铺|没有该店铺权限|无权访问当前店铺/.test(message)) {
        return "当前账号没有该组织的业务权限，请切换有权限的门店或仓库"
      }
      if (/当前操作没有权限|没有权限|无权限|未授权|NotPermission/i.test(message)) {
        return "当前账号没有该模块权限，请联系管理员开通后再使用"
      }
      return ""
    },
    showToastError(message) {
      if (this.$modal && this.$modal.msgError) {
        this.$modal.msgError(message)
        return
      }
      if (this.$message && this.$message.error) {
        this.$message.error(message)
      }
    },
    showToastWarning(message) {
      if (!message) return
      if (this.$message && this.$message.warning) {
        this.$message.warning(message)
      }
    },
    openNav(item) {
      if (item.path && item.path !== this.$route.path && this.canOpenPath(item.path)) {
        this.$router.push(item.path).catch(() => {})
      }
    },
    isBottomNavItemActive(item) {
      return isMobileBottomNavItemActive(item && item.path, this.$route.path)
    },
    canOpenPath(path) {
      const accessDecision = getMobileRouteAccessDecision(path, this.selectedDeptType, this.userPermissions, this.featureState)
      if (accessDecision.path && accessDecision.path !== path) {
        this.showToastWarning(accessDecision.message)
        this.$router.push({
          path: accessDecision.path,
          query: {
            mobileRedirectReason: accessDecision.reason,
            mobileRedirectMessage: accessDecision.message,
            mobileRedirectFrom: path
          }
        }).catch(() => {})
        return false
      }
      return true
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-feature-page {
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  display: flex;
  justify-content: center;
  background: #edf4ef;
  color: #07130d;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
}

.mobile-feature-shell {
  position: relative;
  width: min(100%, 430px);
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background-image:
    radial-gradient(circle at 16% 12%, rgba(255, 255, 255, 0.92), transparent 24%),
    linear-gradient(180deg, rgba(249, 250, 243, 0.62), rgba(232, 242, 232, 0.54)),
    url("~@/assets/images/mobile-inventory-tea-room-bg.jpg");
  background-size: cover;
  background-position: center top;
  box-shadow: 0 22px 72px rgba(31, 45, 36, 0.14);
}

.mobile-feature-shell::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    linear-gradient(90deg, rgba(247, 250, 244, 0.62), rgba(247, 250, 244, 0.08) 58%, rgba(247, 250, 244, 0.56)),
    linear-gradient(180deg, rgba(255, 255, 255, 0.24), rgba(226, 237, 225, 0.48));
  backdrop-filter: blur(2px) saturate(112%);
  -webkit-backdrop-filter: blur(2px) saturate(112%);
  pointer-events: none;
}

.feature-stage {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  margin-bottom: 0;
  padding: 34px 22px calc(124px + env(safe-area-inset-bottom));
  -webkit-overflow-scrolling: touch;
}

.feature-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.feature-header > div {
  min-width: 0;
}

.feature-header h1 {
  margin: 0 0 14px;
  font-size: 34px;
  line-height: 1.05;
  font-weight: 900;
  letter-spacing: 0;
}

button {
  font: inherit;
  letter-spacing: 0;
}

.shop-pill,
.icon-button,
.glass-panel,
.bottom-nav {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.5));
  border: 1px solid rgba(255, 255, 255, 0.78);
  box-shadow:
    0 16px 34px rgba(31, 63, 48, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    inset 0 -1px 0 rgba(255, 255, 255, 0.36);
  backdrop-filter: blur(20px) saturate(150%);
  -webkit-backdrop-filter: blur(20px) saturate(150%);
}

.shop-pill {
  min-height: 44px;
  max-width: 270px;
  border-radius: 22px;
  padding: 9px 15px;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: #122019;
  font-size: 15px;
  font-weight: 800;
}

.shop-pill span {
  min-width: 0;
  line-height: 1.35;
  overflow-wrap: anywhere;
  white-space: normal;
}

.shop-pill svg,
.icon-button svg,
.feature-mark svg,
.action-card svg,
.bottom-nav svg {
  fill: currentColor;
}

.shop-pill svg {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  color: #0f8f74;
}

.shop-pill .chevron {
  width: 16px;
  height: 16px;
  color: #4e6258;
}

.icon-button {
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  padding: 0;
  display: grid;
  place-items: center;
  color: #102019;
}

.icon-button svg {
  width: 24px;
  height: 24px;
}

.preview-lock-banner {
  margin: -2px 0 16px;
  border-color: rgba(245, 158, 11, 0.38);
  padding: 12px 14px;
  color: #47340f;
}

.preview-lock-banner strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 900;
}

.preview-lock-banner span {
  display: block;
  margin-top: 5px;
  color: #664b10;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
}

.mobile-redirect-banner {
  margin: -2px 0 16px;
  border-color: rgba(38, 120, 242, 0.24);
  padding: 12px 14px;
  color: #173b68;
}

.mobile-redirect-banner strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
  font-weight: 900;
}

.mobile-redirect-banner span {
  display: block;
  margin-top: 5px;
  color: #355b78;
  font-size: 13px;
  line-height: 1.45;
  font-weight: 700;
}

.feature-hero {
  border-radius: 22px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 50px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.feature-mark {
  width: 48px;
  height: 48px;
  border-radius: 17px;
  display: grid;
  place-items: center;
  color: #2678f2;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.feature-mark.amber { color: #f59e0b; }
.feature-mark.teal { color: #0ea5a4; }
.feature-mark.blue { color: #2678f2; }

.feature-mark svg {
  width: 27px;
  height: 27px;
}

.feature-hero h2 {
  margin: 0 0 4px;
  font-size: 19px;
  line-height: 1.2;
  font-weight: 900;
}

.feature-hero p {
  margin: 0;
  color: #4f6559;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.section-title {
  margin: 24px 4px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scope-title {
  margin-top: 16px;
}

.shortcut-title {
  margin-top: 20px;
}

.section-title h2,
.panel-head h2 {
  margin: 0;
  font-size: 21px;
  line-height: 1.2;
  font-weight: 900;
}

.feature-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.action-card {
  min-height: 88px;
  border-radius: 22px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #122019;
  font-weight: 900;
}

.action-card.active {
  color: #0f6f5d;
  border-color: rgba(15, 143, 116, 0.28);
  background: linear-gradient(145deg, rgba(232, 250, 243, 0.82), rgba(255, 255, 255, 0.52));
}

.action-card svg {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  color: #0f8f74;
}

.feature-action-overflow {
  margin-top: 12px;
  border-radius: 20px;
  padding: 0 12px 12px;
}

.feature-action-overflow summary {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #122019;
  cursor: pointer;
  font-size: 14px;
  font-weight: 900;
}

.feature-action-overflow summary small {
  color: #4f6559;
  font-size: 13px;
  font-weight: 800;
}

.overflow-feature-actions {
  gap: 8px;
}

.feature-action-overflow:not([open]) > .overflow-feature-actions {
  display: none;
}

.overflow-feature-actions .action-card {
  min-height: 58px;
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.48);
  font-size: 13px;
}

.overflow-feature-actions .action-card svg {
  width: 23px;
  height: 23px;
}

.stock-scope-panel {
  margin-top: 14px;
}

.stock-scope-toggle {
  min-height: 44px;
  padding: 4px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  border-radius: 21px;
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(255, 255, 255, 0.74);
  box-shadow:
    0 10px 22px rgba(31, 63, 48, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.stock-scope-toggle button {
  min-width: 0;
  min-height: 44px;
  border: 0;
  border-radius: 17px;
  padding: 0 10px;
  color: #4f6559;
  background: transparent;
  font-size: 13px;
  font-weight: 900;
  line-height: 1.3;
  white-space: normal;
}

.stock-scope-toggle button.active {
  color: #0f6f5d;
  background: rgba(232, 250, 243, 0.9);
  box-shadow: 0 8px 18px rgba(15, 111, 93, 0.12);
}

.managed-store-select {
  margin-top: 10px;
  min-height: 46px;
  padding: 0 12px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(255, 255, 255, 0.72);
}

.managed-store-select span {
  color: #4f6559;
  font-size: 13px;
  font-weight: 900;
}

.managed-store-select select {
  min-width: 0;
  width: 100%;
  height: 44px;
  border: 0;
  outline: none;
  color: #122019;
  background: transparent;
  font-size: 15px;
  font-weight: 900;
}

.filter-summary {
  flex: 0 1 auto;
  min-width: 0;
  min-height: 34px;
  padding: 0 10px;
  border-radius: 15px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #122019;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.72);
  font-size: 13px;
  font-weight: 900;
  max-width: 54%;
}

.filter-summary span {
  flex: 0 0 auto;
  color: #4f6559;
}

.filter-summary strong {
  min-width: 0;
  overflow: hidden;
  color: #0f8f74;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-panel {
  margin-top: 14px;
  border-radius: 24px;
  padding: 12px;
}

.search-row {
  min-height: 46px;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  padding: 0 8px 0 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(255, 255, 255, 0.72);
}

.search-row > svg {
  width: 21px;
  height: 21px;
  color: #0f8f74;
  fill: currentColor;
}

.search-row input {
  min-width: 0;
  width: 100%;
  height: 44px;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: #122019;
  font-size: 16px;
  font-weight: 800;
}

.search-row input::placeholder {
  color: rgba(96, 116, 106, 0.82);
}

.search-clear,
.search-submit {
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 22px;
  display: grid;
  place-items: center;
  color: #0f6f5d;
  background: rgba(232, 250, 243, 0.82);
}

.search-clear {
  color: #60746a;
  background: transparent;
}

.search-clear svg,
.search-submit svg {
  width: 19px;
  height: 19px;
  fill: currentColor;
}

.search-field-tabs {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
}

.search-field-tabs button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 16px;
  color: #4f6559;
  background: rgba(255, 255, 255, 0.48);
  font-size: 13px;
  font-weight: 900;
}

.search-field-tabs button.active {
  color: #0f6f5d;
  border-color: rgba(15, 143, 116, 0.28);
  background: rgba(232, 250, 243, 0.88);
}

.status-filter-panel {
  margin-top: 14px;
}

.status-filter-head {
  margin: 0 4px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #4f6559;
  font-size: 13px;
  font-weight: 900;
}

.status-filter-scroll {
  display: flex;
  gap: 9px;
  overflow-x: auto;
  padding: 0 2px 2px;
  -webkit-overflow-scrolling: touch;
}

.status-filter-scroll button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 18px;
  color: #4f6559;
  background: rgba(255, 255, 255, 0.52);
  box-shadow:
    0 10px 22px rgba(31, 63, 48, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.status-filter-scroll button.active {
  color: #0f6f5d;
  border-color: rgba(15, 143, 116, 0.3);
  background: linear-gradient(145deg, rgba(232, 250, 243, 0.88), rgba(255, 255, 255, 0.58));
}

.list-panel {
  margin-top: 16px;
  border-radius: 26px;
  padding: 17px 16px 10px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head span {
  min-width: 0;
  max-width: 48%;
  color: #4f6559;
  font-size: 13px;
  line-height: 1.35;
  font-weight: 800;
  overflow-wrap: anywhere;
  text-align: right;
}

.list-row {
  width: 100%;
  min-height: 74px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.62);
  border-right: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 12px 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.list-row > div {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.list-row h3 {
  margin: 0 0 5px;
  font-size: 16px;
  line-height: 1.2;
  font-weight: 900;
  overflow-wrap: anywhere;
  white-space: normal;
}

.list-row p {
  margin: 0;
  color: #4f6559;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 700;
  overflow-wrap: anywhere;
  white-space: normal;
}

.list-row .row-detail {
  margin-top: 3px;
  max-width: 100%;
  color: #52665c;
  font-size: 13px;
  line-height: 1.4;
  overflow-wrap: anywhere;
  white-space: normal;
}

.load-more-row {
  padding: 14px 0 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.62);
}

.load-more-row button {
  width: 100%;
  min-height: 44px;
  border: 1px solid rgba(15, 143, 116, 0.18);
  border-radius: 18px;
  color: #0f6f5d;
  background: rgba(255, 255, 255, 0.54);
  font-size: 14px;
  font-weight: 900;
}

.load-more-row button:disabled {
  color: #60746a;
  opacity: 0.72;
}

.list-state {
  min-height: 96px;
  padding: 18px 4px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 7px;
  color: #60746a;
  font-size: 14px;
  font-weight: 800;
  border-top: 1px solid rgba(255, 255, 255, 0.62);
}

.list-state strong {
  color: #122019;
  font-size: 16px;
  line-height: 1.3;
  font-weight: 900;
}

.list-state p {
  margin: 0;
  line-height: 1.5;
}

.list-state.error {
  color: #b45309;
}

.list-state.error strong {
  color: #92400e;
}

.list-state-action {
  min-height: 44px;
  margin-top: 4px;
  padding: 0 18px;
  border: 1px solid rgba(15, 143, 116, 0.2);
  border-radius: 18px;
  color: #0f6f5d;
  background: rgba(255, 255, 255, 0.58);
  font-size: 14px;
  font-weight: 900;
}

.status-dot {
  flex: 0 0 auto;
  max-width: 104px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  border-radius: 999px;
  color: #122019;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid rgba(255, 255, 255, 0.72);
  font-size: 13px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-dot::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2678f2;
}

.status-dot.amber::before { background: #f59e0b; }
.status-dot.teal::before { background: #0ea5a4; }
.status-dot.blue::before { background: #2678f2; }

.bottom-nav {
  position: fixed;
  z-index: 8;
  left: 50%;
  bottom: max(10px, env(safe-area-inset-bottom));
  width: min(calc(100% - 34px), 396px);
  min-height: 68px;
  border-radius: 30px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  padding: 8px 8px 7px;
  transform: translateX(-50%);
}

.bottom-nav button {
  border: 0;
  background: transparent;
  color: #59675f;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0;
  min-height: 44px;
}

.bottom-nav svg {
  width: 25px;
  height: 25px;
}

.bottom-nav span {
  font-size: 13px;
  line-height: 1;
  font-weight: 800;
}

.bottom-nav .active {
  color: #0f8f74;
}

@supports (height: 100dvh) {
  .mobile-feature-page,
  .mobile-feature-shell {
    min-height: 100dvh;
    height: 100dvh;
  }
}

@media (max-width: 390px) {
  .feature-stage {
    padding-left: 16px;
    padding-right: 16px;
  }

  .feature-header h1 {
    font-size: 30px;
  }

  .feature-header {
    gap: 10px;
  }

  .shop-pill {
    max-width: 100%;
  }

  .feature-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .action-card {
    min-height: 72px;
    padding: 10px;
    gap: 8px;
    font-size: 13px;
  }

  .action-card svg {
    width: 24px;
    height: 24px;
  }
}

@media (max-width: 360px) {
  .feature-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 320px) {
  .feature-stage {
    padding-left: 12px;
    padding-right: 12px;
  }

  .feature-header h1 {
    font-size: 28px;
  }

  .icon-button {
    width: 46px;
    height: 46px;
  }

  .feature-hero {
    grid-template-columns: 44px minmax(0, 1fr);
    padding: 13px;
    gap: 10px;
  }

  .feature-mark {
    width: 44px;
    height: 44px;
  }

  .panel-head {
    align-items: flex-start;
  }

  .status-dot {
    max-width: 84px;
  }
}

@media (max-width: 360px) and (max-height: 620px) {
  .feature-stage {
    padding-top: 12px;
  }

  .feature-header {
    margin-bottom: 10px;
  }

  .feature-header h1 {
    margin-bottom: 8px;
  }

  .preview-lock-banner {
    margin-bottom: 4px;
    padding: 8px 12px;
  }

  .feature-hero {
    padding: 10px 13px;
  }

  .scope-title {
    margin: 10px 4px 6px;
  }

  .search-panel {
    margin-top: 8px;
  }

  .list-panel {
    margin-top: 6px;
    padding-top: 10px;
  }
}

/* Progressive mobile redesign: preserve the existing feature runtime and replace only presentation. */
.mobile-feature-page {
  min-height: var(--mobile-viewport-height);
  height: var(--mobile-viewport-height);
  justify-content: center;
  background: var(--mobile-color-page);
  color: var(--mobile-color-ink);
}

.mobile-feature-shell {
  width: min(100%, 480px);
  min-height: var(--mobile-viewport-height);
  height: var(--mobile-viewport-height);
  background: var(--mobile-color-page);
  background-image: none;
  box-shadow: none;
}

.mobile-feature-shell::before {
  display: none;
  background: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.feature-stage {
  height: var(--mobile-viewport-height);
  padding: 0 16px;
  padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);
  background: var(--mobile-color-page);
  scroll-padding-bottom: calc(var(--mobile-bottom-nav-total) + var(--mobile-keyboard-inset) + 16px);
}

.feature-header {
  position: relative;
  z-index: 3;
  align-items: center;
  margin: 0 -16px 14px;
  padding: calc(var(--mobile-safe-top) + 12px) 16px 12px;
  border-bottom: 1px solid var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.feature-header h1 {
  margin: 0 0 7px;
  color: var(--mobile-color-ink);
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.shop-pill,
.icon-button,
.glass-panel,
.action-card {
  border: 1px solid var(--mobile-color-line);
  background: var(--mobile-color-surface);
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.list-row .mobile-status-chip,
.status-dot.mobile-status-chip {
  border-radius: var(--mobile-radius-sm);
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}

.shop-pill {
  min-height: var(--mobile-control-height);
  padding: 8px 10px;
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-ink);
  font-size: 13px;
}

.route-scope-pill {
  min-height: var(--mobile-control-height);
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid var(--mobile-color-line);
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
  font-size: 13px;
  font-weight: 800;
}

.shop-pill svg {
  width: 18px;
  height: 18px;
  color: var(--mobile-color-primary);
}

.icon-button {
  width: 44px;
  height: 44px;
  border-radius: var(--mobile-radius-md);
  color: var(--mobile-color-ink);
}

.feature-subtitle {
  margin: 0 0 12px;
  color: var(--mobile-color-muted);
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}

.feature-hero {
  display: none;
}

.feature-mark {
  width: 40px;
  height: 40px;
  border-radius: var(--mobile-radius-sm);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-primary-soft);
  box-shadow: none;
}

.feature-mark svg {
  width: 22px;
  height: 22px;
}

.feature-hero h2 {
  font-size: 16px;
}

.feature-hero p,
.panel-head span,
.list-row p,
.list-row .row-detail {
  color: var(--mobile-color-muted);
}

.section-title {
  margin: 16px 2px 8px;
}

.section-title h2,
.panel-head h2 {
  font-size: 16px;
  letter-spacing: -0.01em;
}

.filter-summary,
.stock-scope-toggle,
.managed-store-select,
.status-filter-scroll button,
.search-row,
.search-field-tabs button,
.status-dot,
.load-more-row button,
.list-state-action {
  border-color: var(--mobile-color-line);
  background: var(--mobile-color-surface);
  box-shadow: none;
}

.stock-scope-toggle,
.managed-store-select,
.search-row {
  border-radius: var(--mobile-radius-md);
}

.stock-scope-toggle button,
.status-filter-scroll button,
.search-field-tabs button,
.load-more-row button,
.list-state-action {
  border-radius: var(--mobile-radius-sm);
}

.stock-scope-toggle button.active,
.status-filter-scroll button.active,
.search-field-tabs button.active {
  color: var(--mobile-color-primary);
  border-color: rgba(11, 107, 83, 0.28);
  background: var(--mobile-color-primary-soft);
  box-shadow: none;
}

.search-panel {
  margin-top: 10px;
  padding: 8px;
  border-radius: var(--mobile-radius-md);
}

.search-row {
  min-height: 44px;
  padding-left: 10px;
  background: var(--mobile-color-surface-soft);
}

.search-row > svg,
.search-submit {
  color: var(--mobile-color-primary);
}

.search-submit {
  border-radius: var(--mobile-radius-sm);
  background: var(--mobile-color-primary-soft);
}

.list-panel {
  margin-top: 12px;
  padding: 0;
  overflow: hidden;
  border-radius: var(--mobile-radius-md);
}

.panel-head {
  min-height: 50px;
  margin: 0;
  padding: 12px 14px;
  background: var(--mobile-color-surface-soft);
}

.list-row {
  min-height: 78px;
  padding: 13px 14px;
  border-top-color: var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.list-row h3,
.list-row p {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.list-row h3 {
  color: var(--mobile-color-ink);
  font-size: 15px;
}

.status-dot {
  max-width: 92px;
  height: 28px;
  padding: 0 9px;
  border-radius: 6px;
  color: var(--mobile-color-muted);
  font-size: 12px;
}

.status-dot::before,
.status-dot.teal::before,
.status-dot.blue::before {
  background: var(--mobile-color-primary);
}

.status-dot.amber::before {
  background: var(--mobile-color-warning);
}

.load-more-row,
.list-state {
  padding-right: 14px;
  padding-left: 14px;
  border-top-color: var(--mobile-color-line);
  background: var(--mobile-color-surface);
}

.feature-actions {
  gap: 8px;
}

.action-card,
.overflow-feature-actions .action-card {
  min-height: 56px;
  padding: 10px 12px;
  border: 1px solid var(--mobile-color-line);
  border-radius: var(--mobile-radius-md);
  background: var(--mobile-color-surface);
  box-shadow: none;
}

.action-card.active {
  color: var(--mobile-color-primary);
  border-color: rgba(11, 107, 83, 0.28);
  background: var(--mobile-color-primary-soft);
}

.action-card svg {
  width: 23px;
  height: 23px;
  color: var(--mobile-color-primary);
}

.feature-action-overflow {
  border-radius: var(--mobile-radius-md);
}

.bottom-nav {
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  min-height: var(--mobile-bottom-nav-total);
  padding: 6px var(--mobile-safe-right) var(--mobile-safe-bottom) var(--mobile-safe-left);
  transform: none;
  border: 0;
  border-top: 1px solid var(--mobile-color-line);
  border-radius: 0;
  background: var(--mobile-color-surface);
  box-shadow: 0 -8px 20px rgba(23, 33, 29, 0.05);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.bottom-nav button {
  color: var(--mobile-color-muted);
}

.bottom-nav svg {
  width: 23px;
  height: 23px;
}

.bottom-nav span {
  font-size: 13px;
}

.bottom-nav .active {
  color: var(--mobile-color-primary);
}

.bottom-nav .active::before {
  top: -6px;
}

/* Refined depth and restrained motion for mobile business lists. */
.mobile-feature-page {
  background-color: var(--mobile-color-page);
  background-image:
    radial-gradient(circle at 100% 2%, rgba(11, 107, 83, 0.11) 0, rgba(11, 107, 83, 0) 250px),
    radial-gradient(circle at 0% 66%, rgba(40, 102, 177, 0.045) 0, rgba(40, 102, 177, 0) 210px),
    linear-gradient(180deg, #f8faf7 0%, var(--mobile-color-page) 48%, #f1f4f1 100%);
}

.mobile-feature-shell,
.feature-stage {
  background: transparent;
}

.feature-header {
  position: sticky;
  top: 0;
  z-index: 30;
  margin-bottom: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 28px rgba(23, 33, 29, 0.045);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

.shop-pill {
  border-color: rgba(11, 107, 83, 0.18);
  background: linear-gradient(135deg, rgba(231, 242, 237, 0.95), rgba(255, 255, 255, 0.94));
  box-shadow: 0 4px 12px rgba(11, 107, 83, 0.07);
}

.icon-button {
  border-color: rgba(203, 211, 206, 0.8);
  box-shadow: var(--mobile-shadow-control);
}

.glass-panel,
.action-card {
  border-color: rgba(203, 211, 206, 0.82);
  box-shadow: var(--mobile-shadow-card);
}

.search-panel,
.list-panel,
.feature-action-overflow {
  overflow: hidden;
}

.search-panel {
  background: rgba(255, 255, 255, 0.94);
}

.panel-head {
  background: linear-gradient(135deg, rgba(248, 250, 247, 0.98), rgba(231, 242, 237, 0.58));
}

.action-card--primary,
.action-card--primary.active {
  border-color: rgba(11, 107, 83, 0.78);
  background: var(--mobile-gradient-primary);
  color: #fff;
  box-shadow: var(--mobile-shadow-primary);
}

.action-card--primary svg,
.action-card--primary span {
  color: #fff;
}

@media (hover: hover) and (pointer: fine) {
  .list-row:hover,
  .action-card:hover,
  .shop-pill:hover,
  .icon-button:hover {
    transform: translateY(-1px);
    filter: brightness(1.01);
  }
}

@media (prefers-reduced-motion: no-preference) {
  .feature-actions > .action-card {
    animation: mobile-surface-enter 220ms var(--mobile-ease-spring) both;
  }

  .feature-actions > .action-card:nth-child(2) { animation-delay: 32ms; }
  .feature-actions > .action-card:nth-child(3) { animation-delay: 64ms; }
  .feature-actions > .action-card:nth-child(4) { animation-delay: 96ms; }
}
</style>
