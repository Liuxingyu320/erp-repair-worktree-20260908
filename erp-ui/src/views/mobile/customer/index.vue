<template>
  <div class="mobile-customer-page mobile-system-page">
    <main class="customer-shell" aria-label="客户服务卡">
      <header class="customer-topbar">
        <button type="button" aria-label="返回门店工作台" @click="goBack">
          <i class="el-icon-arrow-left" aria-hidden="true" />
        </button>
        <div>
          <h1>客户服务卡</h1>
          <p>{{ context.deptName || "当前门店" }}</p>
        </div>
        <button type="button" aria-label="切换门店" @click="goSelectShop">
          <i class="el-icon-sort" aria-hidden="true" />
        </button>
      </header>

      <section class="customer-hero">
        <span class="hero-icon" aria-hidden="true">茶</span>
        <div>
          <h2>记住每位客户的喜好</h2>
          <p>仅显示当前门店客户，员工可按权限维护并追加服务记录。</p>
        </div>
      </section>

      <form class="customer-search" role="search" @submit.prevent="search">
        <label>
          <span class="sr-only">搜索客户</span>
          <input v-model.trim="keyword" type="search" placeholder="姓名、电话、茶喜好或注意事项">
        </label>
        <select v-model="status" aria-label="客户状态" @change="search">
          <option value="0">正常客户</option>
          <option value="">全部客户</option>
          <option value="1">已归档</option>
        </select>
        <button type="submit">查询</button>
      </form>

      <section class="customer-list" aria-live="polite">
        <div class="list-heading">
          <div>
            <h2>本店客户</h2>
            <p>共 {{ total }} 位</p>
          </div>
          <button type="button" :disabled="loading" @click="refresh">
            {{ loading ? "刷新中" : "刷新" }}
          </button>
        </div>

        <div v-if="loading && !rows.length" class="customer-state" role="status">
          <strong>正在读取客户服务卡</strong>
          <p>请稍候…</p>
        </div>
        <div v-else-if="error && !rows.length" class="customer-state error" role="alert">
          <strong>加载失败</strong>
          <p>{{ error }}</p>
          <button type="button" @click="refresh">重新加载</button>
        </div>
        <div v-else-if="!rows.length" class="customer-state">
          <strong>暂无符合条件的客户</strong>
          <p>{{ canAdd ? "可点击下方按钮创建客户服务卡。" : "可调整搜索条件后重试。" }}</p>
        </div>

        <template v-else>
          <button
            v-for="row in rows"
            :key="row.customerId"
            class="customer-card"
            type="button"
            @click="openCustomer(row)"
          >
            <span class="customer-avatar">{{ avatarText(row) }}</span>
            <span class="customer-card-body">
              <span class="customer-card-title">
                <strong>{{ row.customerName || "未命名客户" }}</strong>
                <em :class="{ archived: row.status !== '0' }">{{ row.status === "0" ? "正常" : "已归档" }}</em>
              </span>
              <span class="customer-phone">{{ row.contactPhone || "未填写手机号" }}</span>
              <span class="customer-preference">{{ row.teaPreferences || row.preferenceTags || "尚未记录茶喜好" }}</span>
              <span v-if="row.cautions" class="customer-caution">注意：{{ row.cautions }}</span>
            </span>
            <span class="customer-budget">{{ budgetText(row) }}</span>
          </button>
        </template>

        <button v-if="hasMore" class="load-more" type="button" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? "加载中…" : "加载更多" }}
        </button>
      </section>

      <button v-if="canAdd" class="customer-create" type="button" @click="openForm()">
        <i class="el-icon-plus" aria-hidden="true" />新建客户服务卡
      </button>
    </main>

    <section v-if="selected" class="customer-detail-mask" @click.self="closeCustomer">
      <article class="customer-detail" role="dialog" aria-modal="true" aria-labelledby="mobile-customer-detail-title">
        <header>
          <div>
            <small>{{ selected.customerCode || "客户服务卡" }}</small>
            <h2 id="mobile-customer-detail-title">{{ selected.customerName }}</h2>
          </div>
          <button type="button" aria-label="关闭客户详情" @click="closeCustomer"><i class="el-icon-close" aria-hidden="true" /></button>
        </header>

        <div class="customer-detail-body">
          <div v-if="detailLoading" class="detail-state" role="status">正在加载完整服务记录…</div>
          <div v-if="detailError" class="detail-state error" role="alert">{{ detailError }}</div>

          <section v-if="selected.photoNodeId" class="customer-photo">
            <img v-if="photoUrl" :src="photoUrl" :alt="selected.customerName + '客户照片'">
            <button v-else type="button" :disabled="photoLoading" @click="loadPhoto">
              {{ photoLoading ? "照片加载中…" : "查看受控客户照片" }}
            </button>
            <p v-if="photoError" role="alert">{{ photoError }}</p>
          </section>

          <dl class="customer-profile">
            <div><dt>手机号</dt><dd>{{ selected.contactPhone || "-" }}</dd></div>
            <div><dt>联系人</dt><dd>{{ selected.contactPerson || "-" }}</dd></div>
            <div><dt>喜欢的茶</dt><dd>{{ selected.teaPreferences || "-" }}</dd></div>
            <div><dt>偏好标签</dt><dd>{{ selected.preferenceTags || "-" }}</dd></div>
            <div><dt>服务偏好</dt><dd>{{ selected.brewingServicePreferences || "-" }}</dd></div>
            <div><dt>注意事项</dt><dd>{{ selected.cautions || "-" }}</dd></div>
            <div><dt>人均预算</dt><dd>{{ budgetText(selected) }}</dd></div>
            <div><dt>最近到店</dt><dd>{{ selected.lastVisitDate || "-" }}</dd></div>
            <div><dt>创建人</dt><dd>{{ selected.createBy || "-" }}</dd></div>
            <div><dt>最后修改人</dt><dd>{{ selected.updateBy || selected.createBy || "-" }}</dd></div>
            <div><dt>最后修改时间</dt><dd>{{ dateTimeText(selected.updateTime || selected.createTime) }}</dd></div>
          </dl>

          <section class="service-history">
            <div class="service-history-title">
              <h3>历史服务记录</h3>
              <span>{{ serviceRecords.length }} 条</span>
            </div>
            <article v-for="record in serviceRecords" :key="record.recordId || record.requestKey || record.serviceDate">
              <div>
                <strong>{{ record.teaServed || "到店服务" }}</strong>
                <time>{{ record.serviceDate }}</time>
              </div>
              <p>{{ record.serviceNote || record.preferenceSnapshot || "无服务备注" }}</p>
              <small>{{ record.serviceUserName || record.createBy || "-" }} · {{ record.partySize || "-" }} 人 · {{ moneyText(record.consumptionAmount) }}</small>
            </article>
            <p v-if="!serviceRecords.length" class="empty-history">暂无服务记录</p>
          </section>
        </div>

        <footer>
          <button v-if="canEdit && selected.status === '0'" type="button" @click="openForm(selected)">编辑服务卡</button>
          <button v-if="canAddRecord && selected.status === '0'" class="primary" type="button" @click="openRecord">追加服务记录</button>
          <button v-if="!canEdit && !canAddRecord" class="primary" type="button" @click="closeCustomer">关闭</button>
        </footer>
      </article>
    </section>

    <mobile-form-sheet
      :value="formSheet.data"
      :open="formSheet.open"
      :feature="feature"
      :config="formConfig"
      :title="formSheet.mode === 'edit' ? '编辑客户服务卡' : '新建客户服务卡'"
      :saving="formSheet.saving"
      :error="formSheet.error"
      :validation-error="formSheet.validationError"
      :icon-paths="iconPaths"
      :context="formContext"
      @input="handleFormInput"
      @close="closeForm"
      @submit="submitForm"
    />

    <mobile-customer-record-dialog
      :open="recordDialog.open"
      :customer="selected"
      :saving="recordDialog.saving"
      :error="recordDialog.error"
      :icon-paths="iconPaths"
      @close="closeRecord"
      @submit="submitRecord"
    />
  </div>
</template>

<script>
import {
  addCustomerServiceRecord,
  createCustomerServiceCard,
  getCustomerServiceCardCapabilities,
  getCustomerServiceCard,
  getCustomerServiceCardPhoto,
  listCustomerServiceCards,
  updateCustomerServiceCard
} from "@/api/inventory/customer"
import { getSelectedDeptContext } from "@/utils/shopContext"
import MobileCustomerRecordDialog from "@/views/mobile/feature/components/MobileCustomerRecordDialog.vue"
import MobileFormSheet from "@/views/mobile/feature/components/MobileFormSheet.vue"

const { getMobileFormConfig } = require("@/views/mobile/feature/mobileFormConfigs")
const { createMobileFormData, buildMobileFormPayload } = require("@/views/mobile/feature/mobileFormPayloads")
const { getMobileFormValidationError } = require("@/views/mobile/feature/mobileValidation")
const { buildCustomerServiceRecordPayload } = require("@/views/mobile/feature/mobileCustomerServiceRecord")
const { mobileErrorMessage } = require("../mobileErrorMessage")

const PAGE_SIZE = 20

export default {
  name: "MobileCustomerServiceCard",
  components: { MobileCustomerRecordDialog, MobileFormSheet },
  data() {
    return {
      context: getSelectedDeptContext(),
      keyword: "",
      status: "0",
      rows: [],
      total: 0,
      pageNum: 1,
      loading: false,
      loadingMore: false,
      writeEnabled: false,
      error: "",
      selected: null,
      detailLoading: false,
      detailError: "",
      photoLoading: false,
      photoError: "",
      photoUrl: "",
      listRequestSeq: 0,
      detailRequestSeq: 0,
      photoRequestSeq: 0,
      formSheet: {
        open: false,
        mode: "create",
        saving: false,
        error: "",
        validationError: null,
        data: {}
      },
      recordDialog: { open: false, saving: false, error: "" },
      feature: { title: "客户服务卡", heading: "客户服务卡", featureKey: "customer" },
      iconPaths: {
        close: "m6.4 5 5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5Z"
      }
    }
  },
  computed: {
    permissions() {
      const getters = this.$store && this.$store.getters ? this.$store.getters : {}
      return Array.isArray(getters.permissions) ? getters.permissions : []
    },
    customerWriteEnabled() {
      return this.writeEnabled === true
    },
    canAdd() {
      return this.customerWriteEnabled && this.hasPermission("inv:customerCard:add")
    },
    canEdit() {
      return this.customerWriteEnabled && this.hasPermission("inv:customerCard:edit")
    },
    canAddRecord() {
      return this.customerWriteEnabled && this.hasPermission("inv:customerCard:record:add")
    },
    hasMore() {
      return this.rows.length < this.total
    },
    serviceRecords() {
      return this.selected && Array.isArray(this.selected.serviceRecords)
        ? this.selected.serviceRecords
        : []
    },
    formConfig() {
      return getMobileFormConfig("customer")
    },
    formContext() {
      return {
        featureKey: "customer",
        selectedDeptId: this.context.deptId,
        selectedDeptType: this.context.deptType,
        selectedDeptName: this.context.deptName,
        permissions: this.permissions
      }
    }
  },
  created() {
    this.loadCapabilities()
    this.loadRows(false)
  },
  beforeDestroy() {
    this.listRequestSeq += 1
    this.detailRequestSeq += 1
    this.releasePhoto()
    this.unlockBody()
  },
  methods: {
    loadCapabilities() {
      return getCustomerServiceCardCapabilities().then(response => {
        this.writeEnabled = !!(response && response.data && response.data.writeEnabled === true)
      }).catch(() => {
        this.writeEnabled = false
      })
    },
    hasPermission(permission) {
      return this.permissions.includes("*:*:*") || this.permissions.includes(permission)
    },
    loadRows(append) {
      const requestSeq = ++this.listRequestSeq
      const requestedPage = this.pageNum
      if (append) this.loadingMore = true
      else this.loading = true
      this.error = ""
      return listCustomerServiceCards({
        pageNum: this.pageNum,
        pageSize: PAGE_SIZE,
        keyword: this.keyword || undefined,
        status: this.status || undefined
      }).then(response => {
        if (requestSeq !== this.listRequestSeq) return
        const nextRows = Array.isArray(response && response.rows) ? response.rows : []
        this.rows = append ? this.rows.concat(nextRows) : nextRows
        this.total = Number(response && response.total) || this.rows.length
      }).catch(error => {
        if (requestSeq !== this.listRequestSeq) return
        if (append) this.pageNum = Math.max(1, requestedPage - 1)
        this.error = this.errorMessage(error, "客户服务卡加载失败，请稍后重试")
      }).finally(() => {
        if (requestSeq !== this.listRequestSeq) return
        this.loading = false
        this.loadingMore = false
      })
    },
    search() {
      this.pageNum = 1
      this.rows = []
      this.loadRows(false)
    },
    refresh() {
      this.pageNum = 1
      this.loadRows(false)
    },
    loadMore() {
      if (!this.hasMore || this.loadingMore) return
      this.pageNum += 1
      this.loadRows(true)
    },
    openCustomer(row) {
      this.detailRequestSeq += 1
      this.releasePhoto()
      this.selected = Object.assign({}, row)
      this.detailError = ""
      this.lockBody()
      this.loadDetail(row.customerId)
    },
    loadDetail(customerId) {
      if (!customerId) return Promise.resolve()
      const requestSeq = ++this.detailRequestSeq
      this.detailLoading = true
      this.detailError = ""
      return getCustomerServiceCard(customerId).then(response => {
        if (requestSeq !== this.detailRequestSeq || !this.selected ||
          Number(this.selected.customerId) !== Number(customerId)) return
        if (response && response.data && Number(response.data.customerId) === Number(customerId)) {
          this.selected = response.data
        }
      }).catch(error => {
        if (requestSeq !== this.detailRequestSeq) return
        this.detailError = this.errorMessage(error, "客户详情加载失败")
      }).finally(() => {
        if (requestSeq !== this.detailRequestSeq) return
        this.detailLoading = false
      })
    },
    closeCustomer() {
      if (this.formSheet.open || this.recordDialog.open) return
      this.detailRequestSeq += 1
      this.selected = null
      this.detailError = ""
      this.releasePhoto()
      this.unlockBody()
    },
    loadPhoto() {
      if (!this.selected || !this.selected.customerId || this.photoLoading) return
      const customerId = this.selected.customerId
      const requestSeq = ++this.photoRequestSeq
      this.photoLoading = true
      this.photoError = ""
      getCustomerServiceCardPhoto(customerId, "preview").then(blob => {
        if (requestSeq !== this.photoRequestSeq || !this.selected ||
          Number(this.selected.customerId) !== Number(customerId)) return
        if (this.photoUrl) URL.revokeObjectURL(this.photoUrl)
        this.photoUrl = URL.createObjectURL(blob)
      }).catch(error => {
        if (requestSeq !== this.photoRequestSeq) return
        this.photoError = this.errorMessage(error, "客户照片加载失败")
      }).finally(() => {
        if (requestSeq !== this.photoRequestSeq) return
        this.photoLoading = false
      })
    },
    releasePhoto() {
      this.photoRequestSeq += 1
      if (this.photoUrl) URL.revokeObjectURL(this.photoUrl)
      this.photoUrl = ""
      this.photoError = ""
      this.photoLoading = false
    },
    openForm(customer) {
      if (customer ? !this.canEdit : !this.canAdd) return
      const data = createMobileFormData(this.formConfig, customer || {}, {
        selectedDeptId: this.context.deptId,
        selectedDeptType: this.context.deptType
      })
      data.requestKey = this.requestKey("customer-card")
      this.formSheet = {
        open: true,
        mode: customer ? "edit" : "create",
        saving: false,
        error: "",
        validationError: null,
        data
      }
    },
    handleFormInput(data) {
      this.formSheet.data = data || {}
      this.formSheet.error = ""
      this.formSheet.validationError = null
    },
    closeForm() {
      if (!this.formSheet.saving) this.formSheet.open = false
    },
    submitForm() {
      if (this.formSheet.saving) return
      const validationError = getMobileFormValidationError(this.formConfig, this.formSheet.data)
      if (validationError) {
        this.formSheet.validationError = validationError
        this.formSheet.error = validationError.message
        return
      }
      const payload = buildMobileFormPayload(this.formConfig, this.formSheet.data, {
        submitAction: "save",
        selectedDeptId: this.context.deptId,
        selectedDeptType: this.context.deptType
      })
      const customerId = payload.customerId
      delete payload.customerId
      this.formSheet.saving = true
      this.formSheet.error = ""
      const request = customerId
        ? updateCustomerServiceCard(customerId, payload)
        : createCustomerServiceCard(payload)
      request.then(response => {
        this.formSheet.open = false
        this.$modal.msgSuccess(customerId ? "客户服务卡已更新" : "客户服务卡已创建")
        this.refresh()
        if (customerId && this.selected) this.loadDetail(customerId)
        else if (response && response.data && response.data.customerId) this.openCustomer(response.data)
      }).catch(error => {
        this.formSheet.error = this.errorMessage(error, "客户服务卡保存失败")
      }).finally(() => {
        this.formSheet.saving = false
      })
    },
    openRecord() {
      if (!this.canAddRecord || !this.selected || this.selected.status !== "0") return
      this.recordDialog = { open: true, saving: false, error: "" }
    },
    closeRecord() {
      if (!this.recordDialog.saving) this.recordDialog.open = false
    },
    submitRecord(form) {
      if (!this.selected || this.recordDialog.saving) return
      let payload
      try {
        payload = buildCustomerServiceRecordPayload(form)
      } catch (error) {
        this.recordDialog.error = error.message
        return
      }
      const customerId = this.selected.customerId
      this.recordDialog.saving = true
      this.recordDialog.error = ""
      addCustomerServiceRecord(customerId, payload).then(() => {
        this.recordDialog.open = false
        this.$modal.msgSuccess("服务记录已追加")
        this.loadDetail(customerId)
        this.refresh()
      }).catch(error => {
        this.recordDialog.error = this.errorMessage(error, "服务记录追加失败")
      }).finally(() => {
        this.recordDialog.saving = false
      })
    },
    avatarText(row) {
      return String(row && row.customerName || "客").trim().slice(0, 1)
    },
    budgetText(row) {
      const min = row && row.budgetMin
      const max = row && row.budgetMax
      if (min !== undefined && min !== null && max !== undefined && max !== null) {
        return "¥" + min + " - ¥" + max + "/人"
      }
      const value = min !== undefined && min !== null ? min : max
      return value !== undefined && value !== null ? "约 ¥" + value + "/人" : "预算未记录"
    },
    moneyText(value) {
      return value === undefined || value === null || value === "" ? "金额未记录" : "¥" + value
    },
    dateTimeText(value) {
      const text = String(value || "").trim().replace("T", " ")
      return text ? text.slice(0, 16) : "-"
    },
    requestKey(prefix) {
      return prefix + "-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10)
    },
    errorMessage(error, fallback) {
      return mobileErrorMessage(error, fallback)
    },
    lockBody() {
      if (typeof document !== "undefined") document.body.classList.add("mobile-customer-detail-open")
    },
    unlockBody() {
      if (typeof document !== "undefined") document.body.classList.remove("mobile-customer-detail-open")
    },
    goBack() {
      this.$router.push("/mobile/store").catch(() => {})
    },
    goSelectShop() {
      this.$router.push({ path: "/select-shop", query: { redirect: "/mobile/customer" } }).catch(() => {})
    }
  }
}
</script>

<style scoped lang="scss">
.mobile-customer-page { min-height: var(--mobile-viewport-height, 100dvh); background: var(--mobile-color-page, #f4f5f2); color: var(--mobile-color-ink, #17211d); }
.customer-shell { width: min(760px, 100%); min-height: 100vh; box-sizing: border-box; margin: 0 auto; padding: 0 14px 104px; }
.customer-topbar { position: sticky; z-index: 20; top: 0; display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; gap: 10px; margin: 0 -14px; padding: max(12px, env(safe-area-inset-top)) 14px 12px; border-bottom: 1px solid var(--mobile-color-line, #dde2de); background: var(--mobile-color-surface, #fff); backdrop-filter: none; }
.customer-topbar button { width: 44px; height: 44px; border: 0; border-radius: 14px; background: #fff; color: #176b58; box-shadow: 0 6px 18px rgba(25, 79, 61, .09); font-size: 20px; font-weight: 800; }
.customer-topbar div { min-width: 0; text-align: center; }
.customer-topbar h1 { margin: 0; font-size: 18px; }
.customer-topbar p { overflow: hidden; margin: 3px 0 0; color: #6a7973; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.customer-hero { display: none; }
.hero-icon { display: grid; width: 54px; height: 54px; flex: 0 0 auto; place-items: center; border-radius: 18px; background: #117c66; color: #fff; font-size: 24px; font-weight: 900; }
.customer-hero h2 { margin: 0 0 6px; font-size: 18px; }
.customer-hero p { margin: 0; color: #52635c; font-size: 13px; line-height: 1.6; }
.customer-search { display: grid; grid-template-columns: minmax(0, 1fr) 108px 58px; gap: 8px; padding: 10px; border: 1px solid #e0e8e4; border-radius: 18px; background: #fff; box-shadow: 0 10px 28px rgba(31, 65, 53, .06); }
.customer-search input, .customer-search select { width: 100%; height: 44px; box-sizing: border-box; border: 1px solid #dce5e1; border-radius: 12px; padding: 0 11px; background: #f9fbfa; color: #17211d; font: inherit; }
.customer-search button { border: 0; border-radius: 12px; background: #137b66; color: #fff; font-weight: 800; }
.customer-list { margin-top: 18px; }
.list-heading { display: flex; align-items: center; justify-content: space-between; padding: 0 2px 10px; }
.list-heading h2 { margin: 0; font-size: 18px; }
.list-heading p { margin: 3px 0 0; color: #718078; font-size: 12px; }
.list-heading button { border: 0; background: transparent; color: #147861; font-weight: 800; }
.customer-card { position: relative; display: grid; width: 100%; grid-template-columns: 50px minmax(0, 1fr); gap: 12px; margin-bottom: 10px; padding: 14px; border: 1px solid #e0e8e4; border-radius: 18px; background: #fff; color: inherit; text-align: left; box-shadow: 0 8px 24px rgba(31, 65, 53, .055); }
.customer-avatar { display: grid; width: 50px; height: 50px; place-items: center; border-radius: 12px; background: var(--mobile-color-primary, #0b6b53); color: #fff; font-size: 20px; font-weight: 700; }
.customer-card-body { display: grid; min-width: 0; gap: 5px; padding-right: 74px; }
.customer-card-title { display: flex; min-width: 0; align-items: center; gap: 8px; }
.customer-card-title strong { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.customer-card-title em { flex: 0 0 auto; border-radius: 999px; padding: 2px 7px; background: #e6f7f0; color: #11735d; font-size: 10px; font-style: normal; font-weight: 800; }
.customer-card-title em.archived { background: #f0f1f1; color: #69736f; }
.customer-phone, .customer-preference, .customer-caution { overflow: hidden; color: #64736d; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.customer-caution { color: #a15a22; }
.customer-budget { position: absolute; right: 14px; bottom: 15px; color: #176e5b; font-size: 11px; font-weight: 800; }
.customer-state { padding: 34px 20px; border: 1px dashed #ccd9d4; border-radius: 18px; background: rgba(255,255,255,.72); text-align: center; }
.customer-state p { margin: 7px 0 0; color: #6f7c77; font-size: 13px; }
.customer-state button { margin-top: 12px; border: 0; border-radius: 10px; padding: 9px 14px; background: #157c66; color: #fff; }
.customer-state.error { border-color: #f1c6c1; color: #a2382d; }
.load-more { width: 100%; min-height: 44px; border: 1px solid #d8e3df; border-radius: 14px; background: #fff; color: #18705e; font-weight: 800; }
.customer-create { position: fixed; z-index: 18; right: max(18px, calc((100vw - 760px) / 2 + 18px)); bottom: max(22px, env(safe-area-inset-bottom)); display: flex; min-height: 48px; align-items: center; gap: 7px; border: 0; border-radius: 12px; padding: 0 18px; background: var(--mobile-color-primary, #0b6b53); color: #fff; box-shadow: var(--mobile-shadow-float, 0 8px 20px rgba(23, 33, 29, .08)); font-weight: 700; }
.customer-create span { font-size: 22px; }
.customer-detail-mask { position: fixed; z-index: 10040; inset: 0; display: flex; align-items: flex-end; justify-content: center; background: rgba(23, 33, 29, .34); backdrop-filter: none; }
.customer-detail { display: flex; width: min(760px, 100%); max-height: min(92vh, 900px); flex-direction: column; overflow: hidden; border-radius: 24px 24px 0 0; background: #f8faf9; box-shadow: 0 -22px 55px rgba(7, 33, 24, .22); }
.customer-detail > header { display: flex; align-items: center; justify-content: space-between; padding: 18px; border-bottom: 1px solid #e2e9e6; background: #fff; }
.customer-detail > header small { color: #74817c; }
.customer-detail > header h2 { margin: 4px 0 0; font-size: 21px; }
.customer-detail > header button { width: 44px; height: 44px; border: 0; border-radius: 13px; background: #eff3f1; color: #46534e; font-size: 20px; }
.customer-detail-body { min-height: 0; flex: 1 1 auto; overflow-y: auto; padding: 16px; }
.detail-state { margin-bottom: 12px; border-radius: 12px; padding: 10px 12px; background: #e8f4ef; color: #176d59; font-size: 13px; }
.detail-state.error { background: #fff0ee; color: #a33b30; }
.customer-photo { margin-bottom: 14px; overflow: hidden; border-radius: 18px; background: #eaf0ed; text-align: center; }
.customer-photo img { display: block; width: 100%; max-height: 320px; object-fit: cover; }
.customer-photo button { min-height: 100px; border: 0; background: transparent; color: #15735f; font-weight: 900; }
.customer-photo p { margin: 0; padding: 9px; color: #a33b30; font-size: 12px; }
.customer-profile { display: grid; gap: 1px; overflow: hidden; margin: 0; border: 1px solid #e0e8e4; border-radius: 16px; background: #e0e8e4; }
.customer-profile div { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 10px; padding: 12px; background: #fff; }
.customer-profile dt { color: #6d7974; font-size: 12px; }
.customer-profile dd { margin: 0; color: #24322d; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.service-history { margin-top: 18px; }
.service-history-title { display: flex; align-items: center; justify-content: space-between; }
.service-history-title h3 { margin: 0; font-size: 16px; }
.service-history-title span { color: #75817c; font-size: 12px; }
.service-history article { margin-top: 10px; border: 1px solid #e1e8e5; border-radius: 15px; padding: 13px; background: #fff; }
.service-history article div { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.service-history time { color: #77847e; font-size: 11px; }
.service-history article p { margin: 8px 0; color: #4f5f58; font-size: 13px; line-height: 1.55; }
.service-history article small { color: #75817c; }
.empty-history { padding: 20px; border: 1px dashed #d6dfdb; border-radius: 14px; color: #78847f; text-align: center; }
.customer-detail > footer { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 12px 16px max(12px, env(safe-area-inset-bottom)); border-top: 1px solid #e0e8e4; background: #fff; }
.customer-detail > footer button { min-height: 46px; border: 1px solid #d6e0dc; border-radius: 13px; background: #fff; color: #31423b; font-weight: 900; }
.customer-detail > footer button.primary { border-color: transparent; background: #137c66; color: #fff; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
@media (max-width: 440px) {
  .customer-search { grid-template-columns: minmax(0, 1fr) 58px; }
  .customer-search select { grid-column: 1 / -1; grid-row: 2; }
  .customer-search button { grid-column: 2; grid-row: 1; }
}
</style>

<style>
body.mobile-customer-detail-open { overflow: hidden; }
</style>
