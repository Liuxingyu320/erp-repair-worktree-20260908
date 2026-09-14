<template>
  <el-drawer
    ref="profileDrawer"
    :title="drawerTitle"
    :visible.sync="drawerVisible"
    size="820px"
    custom-class="hr-profile-detail-shell"
    append-to-body
    @opened="syncDrawerAccessibility"
  >
    <div slot="title" class="hr-drawer-title">
      <span class="hr-drawer-title__icon"><i class="el-icon-user" /></span>
      <span class="hr-drawer-title__copy">
        <small>员工资料</small>
        <strong>{{ drawerTitle }}</strong>
        <span>身份、组织岗位与档案完整度</span>
      </span>
    </div>

    <div v-if="detail" class="hr-profile-drawer">
      <section class="hr-profile-hero">
        <div class="hr-profile-avatar" aria-hidden="true">{{ employeeInitial }}</div>
        <div class="hr-profile-identity">
          <span class="hr-profile-eyebrow">员工 · {{ profileValue(detail, "employeeNo") }}</span>
          <h2>{{ employeeName }}</h2>
          <div class="hr-profile-meta">
            <span><i class="el-icon-office-building" />{{ organizationPath(detail) }}</span>
            <span><i class="el-icon-s-custom" />{{ detail.postNames || profileValue(detail, "positionNames") }}</span>
            <span><i class="el-icon-mobile-phone" />{{ detail.phonenumber || "-" }}</span>
          </div>
        </div>
        <span class="hr-account-badge" :class="{ 'is-disabled': !accountEnabled }">
          <i :class="accountEnabled ? 'el-icon-circle-check' : 'el-icon-warning-outline'" />
          {{ accountText }}
        </span>
      </section>

      <div class="hr-drawer-actions">
        <el-button size="mini" type="primary" plain @click="$emit('regularize', detail)" v-hasPermi="['hr:employee:regularize']">办理转正</el-button>
        <el-button size="mini" type="success" plain @click="$emit('renewal', detail)" v-hasPermi="['hr:employee:renewal']">办理续签</el-button>
        <el-button size="mini" type="primary" icon="el-icon-edit" @click="$emit('edit', detail)" v-hasPermi="['hr:employee:edit']">编辑档案</el-button>
        <el-button
          v-if="showOnboardContractActions"
          size="mini"
          type="success"
          plain
          icon="el-icon-s-claim"
          :disabled="!canProcessOnboardContract"
          @click="handleOnboardContract"
          v-hasPermi="['oa:signTask:send']"
        >准备入职合同</el-button>
        <el-button v-if="allowTransfer" size="mini" type="warning" plain icon="el-icon-sort" @click="$emit('transfer', detail)" v-hasPermi="['hr:employee:transfer']">确认调岗</el-button>
        <el-button v-if="allowOffboarding" size="mini" type="danger" plain icon="el-icon-circle-close" @click="$emit('offboard', detail)" v-hasPermi="['hr:employee:offboard']">确认离职</el-button>
        <el-button size="mini" icon="el-icon-document" @click="$emit('export', detail)">导出该员工资料</el-button>
        <el-button size="mini" icon="el-icon-refresh" @click="$emit('refresh')">刷新</el-button>
      </div>

      <section class="hr-drawer-section hr-health-section">
        <div class="hr-section-heading">
          <span class="hr-section-heading__icon is-health"><i class="el-icon-first-aid-kit" /></span>
          <div>
            <h3>当前健康证</h3>
            <p>用于健康证状态确认与到期提醒</p>
          </div>
        </div>
        <p v-if="detail.healthCertificateNextValidFrom" class="hr-health-renewal">已通过的续证将于 {{ detail.healthCertificateNextValidFrom }} 生效；生效前不替换当前有效证。</p>
        <div class="hr-health-grid">
          <div class="hr-health-item is-status">
            <span class="hr-health-item__icon"><i class="el-icon-medal" /></span>
            <div>
              <small>证件状态</small>
              <el-tag :type="healthCertificateType" size="mini">{{ healthCertificateLabel }}</el-tag>
            </div>
          </div>
          <div class="hr-health-item">
            <span class="hr-health-item__icon"><i class="el-icon-paperclip" /></span>
            <div>
              <small>附件</small>
              <strong>{{ detail.healthCertificateAttachmentPresent ? "已绑定受控附件" : "未绑定附件" }}</strong>
            </div>
          </div>
          <div class="hr-health-item">
            <span class="hr-health-item__icon"><i class="el-icon-date" /></span>
            <div>
              <small>办理日期</small>
              <strong>{{ detail.healthCertificateIssuedDate || "-" }}</strong>
            </div>
          </div>
          <div class="hr-health-item" :class="{ 'is-warning': healthCertificateType === 'warning' || healthCertificateType === 'danger' }">
            <span class="hr-health-item__icon"><i class="el-icon-time" /></span>
            <div>
              <small>到期日期</small>
              <strong>{{ detail.healthCertificateExpiresOn || "-" }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="hr-drawer-section">
        <div class="hr-section-heading">
          <span class="hr-section-heading__icon"><i class="el-icon-data-board" /></span>
          <div>
            <h3>资料概览</h3>
            <p>快速核对当前组织岗位与用工信息</p>
          </div>
        </div>
        <el-row :gutter="10">
          <el-col :span="12">
            <div class="hr-info-block">
              <div class="hr-info-block__heading">
                <span><i class="el-icon-office-building" /></span>
                <strong>工作信息</strong>
              </div>
              <strong>{{ organizationPath(detail) }}</strong>
              <p>{{ detail.postNames || profileValue(detail, "positionNames") }} · {{ profileValue(detail, "jobGrade") }}</p>
              <dl>
                <div><dt>岗位工号</dt><dd>{{ profileValue(detail, "positionNo") }}</dd></div>
                <div><dt>直属主管</dt><dd>{{ profileValue(detail, "directSupervisor") }}</dd></div>
              </dl>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="hr-info-block">
              <div class="hr-info-block__heading is-contract">
                <span><i class="el-icon-document-checked" /></span>
                <strong>用工合同资料</strong>
              </div>
              <strong>{{ profileValue(detail, "entryDate") }} 入职</strong>
              <p>{{ displayProfileValue(detail, "contractType") }} · {{ displayProfileValue(detail, "socialType") }}</p>
              <dl>
                <div><dt>合同到期</dt><dd>{{ profileValue(detail, "contractEndDate") }}</dd></div>
                <div><dt>员工状态</dt><dd>{{ profileValue(detail, "employeeStatus") }}</dd></div>
              </dl>
            </div>
          </el-col>
        </el-row>
      </section>

      <section class="hr-drawer-section">
        <div class="hr-section-heading">
          <span class="hr-section-heading__icon is-coverage"><i class="el-icon-data-analysis" /></span>
          <div>
            <h3>档案完整度与缺失资料</h3>
            <p>区分可直接补充项与系统派生项</p>
          </div>
          <el-button v-if="actionableMissingCount" class="hr-section-heading__action" type="text" icon="el-icon-edit" @click="$emit('edit', detail)">去补充资料</el-button>
        </div>
        <div class="hr-missing-summary">
          <div class="hr-coverage-overview">
            <div class="hr-coverage-score" :class="completionClass">
              <strong>{{ completionPercent }}%</strong>
              <span>档案覆盖度</span>
            </div>
            <div class="hr-coverage-main">
              <div class="hr-coverage-heading">
                <strong>已填 {{ detail.profileCompletedFieldCount || 0 }} / 适用 {{ detail.profileApplicableFieldCount || 0 }}</strong>
                <span>{{ detail.profileNotApplicableFieldCount || 0 }} 项不适用</span>
              </div>
              <el-progress :percentage="completionPercent" :stroke-width="10" :show-text="false" />
              <p v-if="missingFieldGroups.length">待补 {{ actionableMissingCount }} 项资料<span v-if="derivedMissingCount">，另有 {{ derivedMissingCount }} 项需通过其他入口处理</span></p>
              <p v-else>当前员工档案资料已完整</p>
            </div>
          </div>
          <template v-if="missingFieldGroups.length">
            <div v-if="readOnlyMissingGuidance.length" class="hr-missing-guidance">
              <div class="hr-missing-guidance__title">
                <i class="el-icon-info" />
                <strong>需要通过其他入口处理</strong>
              </div>
              <p v-for="item in readOnlyMissingGuidance" :key="`${item.type}-${item.key}`">
                <span>{{ item.label }}</span>：{{ item.source }}
              </p>
            </div>
            <div class="hr-missing-groups">
              <div v-for="group in missingFieldGroups" :key="group.title" class="hr-missing-group">
                <div class="hr-missing-group__heading">
                  <strong>{{ group.title }}</strong>
                  <span>{{ group.actionableFields.length }} 项待补</span>
                </div>
                <div v-if="group.actionableFields.length" class="hr-missing-tags">
                  <el-tag v-for="field in group.actionableFields" :key="field.label" size="mini" type="danger" effect="plain">{{ field.label }}</el-tag>
                </div>
                <div v-if="group.derivedFields.length" class="hr-derived-fields">
                  <span>系统自动生成项</span>
                  <span>{{ group.derivedFields.map(field => field.label).join("、") }}</span>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="hr-complete-copy"><i class="el-icon-circle-check" />资料已完整</div>
        </div>
      </section>

      <hr-salary-source-panel :employee-id="detail.userId" :visible="visible" />
      <section class="hr-drawer-section hr-detail-section">
        <div class="hr-section-heading">
          <span class="hr-section-heading__icon is-detail"><i class="el-icon-document" /></span>
          <div>
            <h3>详细资料</h3>
            <p>按资料主题分组查看完整员工档案</p>
          </div>
        </div>
        <el-tabs v-model="activeTab" class="hr-detail-tabs">
          <el-tab-pane
            v-for="group in detailGroups"
            :key="group.title"
            :label="group.title"
            :name="group.title"
          >
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item v-for="field in group.fields" :key="field[0]" :label="field[1]">
                <hr-sensitive-field-value
                  v-if="isSensitiveField(field[0])"
                  :masked-value="sensitiveMaskedValue(field[0])"
                  :field="field[0]"
                  :employee-id="detail.userId"
                />
                <template v-else>{{ displayProfileValue(detail, detailDisplayKey(field[0])) }}</template>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
    <div v-else class="hr-drawer-empty">
      <span><i class="el-icon-user" /></span>
      <strong>正在加载员工档案</strong>
      <p>请稍候，系统正在整理员工资料。</p>
    </div>
  </el-drawer>
</template>

<script>
import HrSalarySourcePanel from "./HrSalarySourcePanel"
import HrSensitiveFieldValue from "./HrSensitiveFieldValue"
import { DETAIL_GROUPS, SENSITIVE_FIELDS, groupMissingProfileFields, profileFieldLabel, resolveMissingProfileFields } from "./hrFieldConfig"
import { signingOptionsForKey, signingProfileLabel } from "./signingProfileOptions"

const DETAIL_GROUP_TITLES = ["基础信息", "组织岗位", "身份户籍", "教育信息", "用工合同", "社保公积金", "联系人与银行", "数据记录"]

function normalizePositiveDecimalId(value) {
  if (value === undefined || value === null) return ""
  const text = String(value).trim()
  if (!/^\d+$/.test(text)) return ""
  return text.replace(/^0+/, "")
}

export default {
  name: "HrProfileDetailDrawer",
  components: { HrSensitiveFieldValue, HrSalarySourcePanel },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    detail: {
      type: Object,
      default: null
    },
    missingFields: {
      type: Array,
      default: () => []
    },
    completionPercent: {
      type: Number,
      default: 0
    },
    profileValue: {
      type: Function,
      required: true
    },
    profileRawValue: {
      type: Function,
      required: true
    },
    deptName: {
      type: Function,
      required: true
    },
    allowTransfer: {
      type: Boolean,
      default: true
    },
    allowOffboarding: {
      type: Boolean,
      default: true
    },
    showOnboardContractActions: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      activeTab: DETAIL_GROUP_TITLES[0],
      detailGroups: DETAIL_GROUPS
    }
  },
  computed: {
    drawerVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit("update:visible", value)
      }
    },
    drawerTitle() {
      return this.detail ? `员工档案：${this.detail.nickName || this.detail.userName || this.profileValue(this.detail, "employeeName")}` : "员工档案"
    },
    employeeName() {
      if (!this.detail) return "-"
      return this.detail.nickName || this.detail.userName || this.profileValue(this.detail, "employeeName")
    },
    employeeInitial() {
      const chars = Array.from(String(this.employeeName || "员").trim())
      return chars[0] || "员"
    },
    completionClass() {
      const value = Number(this.completionPercent || 0)
      if (value >= 80) return "is-complete"
      if (value >= 50) return "is-progress"
      return "is-risk"
    },
    accountEnabled() {
      return this.detail && this.detail.status === "0"
    },
    accountText() {
      if (!this.detail || !this.detail.userId) return "未绑定账号"
      return this.accountEnabled ? "账号正常" : "账号停用"
    },
    canProcessOnboardContract() {
      return !!(this.showOnboardContractActions && this.detail &&
        normalizePositiveDecimalId(this.detail.userId))
    },
    healthCertificateLabel() {
      const status = this.detail && this.detail.healthCertificateStatus
      return { NOT_YET_EFFECTIVE: "当前无有效证，续证待生效", INVALID_DATES: "日期待核对", VALID: "有效", EXPIRING: "即将到期", EXPIRED: "已过期", NOT_SUBMITTED: "未提交" }[status] || "未提交"
    },
    healthCertificateType() {
      const status = this.detail && this.detail.healthCertificateStatus
      return { VALID: "success", EXPIRING: "warning", EXPIRED: "danger", NOT_SUBMITTED: "info" }[status] || "info"
    },
    missingFieldGroups() {
      return groupMissingProfileFields(this.missingResolution.actionable)
    },
    missingResolution() {
      return resolveMissingProfileFields(this.missingFields)
    },
    readOnlyMissingGuidance() {
      const described = [...this.missingResolution.workflow, ...this.missingResolution.derived]
        .map(item => ({ ...item, label: profileFieldLabel(item.key), type: "described" }))
      const unknown = this.missingResolution.unknown.map(key => ({
        key,
        label: `待确认字段：${key}`,
        source: "请联系系统管理员确认历史字段配置",
        type: "unknown"
      }))
      return [...described, ...unknown]
    },
    actionableMissingCount() {
      return this.missingFieldGroups.reduce((total, group) => total + group.actionableFields.length, 0)
    },
    derivedMissingCount() {
      return this.readOnlyMissingGuidance.length
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.activeTab = DETAIL_GROUP_TITLES[0]
      }
    }
  },
  methods: {
    handleOnboardContract() {
      if (!this.canProcessOnboardContract) return
      this.$emit("onboard-contract", this.detail)
    },
    syncDrawerAccessibility() {
      const component = this.$refs.profileDrawer
      const dialog = component && component.$el && component.$el.querySelector(".el-drawer[role='dialog']")
      if (!dialog) return
      // Element UI 2 gives every drawer the same aria-labelledby id. Removing that
      // duplicate reference lets its existing, correct aria-label (the title prop) win.
      dialog.removeAttribute("aria-labelledby")
    },
    displayProfileValue(detail, key) {
      const options = signingOptionsForKey(key)
      if (options) return signingProfileLabel(options, this.profileRawValue(detail, key))
      return this.profileValue(detail, key)
    },
    organizationPath(row) {
      const values = [
        this.profileRawValue(row, "companyName"),
        row && (row.departmentName || this.deptName(row)),
        this.profileRawValue(row, "storeName")
      ].filter(value => value !== undefined && value !== null && value !== "" && value !== "-")
      const unique = Array.from(new Set(values.map(value => String(value))))
      return unique.length ? unique.join(" / ") : "未填写"
    },
    detailDisplayKey(key) {
      return {
        deptId: "departmentName",
        postIds: "postNames",
        directSupervisorUserId: "directSupervisor"
      }[key] || key
    },
    isSensitiveField(key) {
      return SENSITIVE_FIELDS.includes(key)
    },
    sensitiveMaskedValue(key) {
      if (!this.detail) return ""
      const maskedKey = `${key}Masked`
      if (this.detail[maskedKey] !== undefined && this.detail[maskedKey] !== null) {
        return this.detail[maskedKey]
      }
      return this.profileRawValue(this.detail, key)
    }
  }
}
</script>

<style lang="scss">
.hr-profile-detail-shell {
  max-width: calc(100vw - 24px);
  background: var(--erp-canvas, #f4f5f2);
  box-shadow: -22px 0 60px rgba(23, 33, 29, 0.18);

  .el-drawer__header {
    min-height: 78px;
    flex: 0 0 auto;
    align-items: center;
    margin-bottom: 0;
    padding: 14px 22px;
    color: var(--erp-text, #17211d);
    background: #ffffff;
    border-bottom: 1px solid var(--erp-border, #dde2de);
    box-shadow: 0 4px 18px rgba(23, 33, 29, 0.035);
  }

  .el-drawer__header > :first-child {
    min-width: 0;
    flex: 1;
  }

  .el-drawer__close-btn {
    width: 38px;
    height: 38px;
    flex: 0 0 38px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-left: 14px;
    border-radius: 11px;
    color: #718096;
    transition: color var(--motion-duration-fast) var(--motion-ease-standard), background var(--motion-duration-fast) var(--motion-ease-standard);
  }

  .el-drawer__close-btn:hover {
    color: var(--erp-primary, #0b6b53);
    background: var(--erp-primary-soft, #e7f2ed);
  }

  .el-drawer__body {
    min-height: 0;
    flex: 1;
    overflow-x: hidden;
    overflow-y: auto;
    background: var(--erp-canvas, #f4f5f2);
    scrollbar-color: #aeb8b3 transparent;
    scrollbar-width: thin;
  }

  .el-drawer__body::-webkit-scrollbar { width: 7px; }
  .el-drawer__body::-webkit-scrollbar-track { background: transparent; }
  .el-drawer__body::-webkit-scrollbar-thumb { border-radius: 8px; background: #aeb8b3; }
}

@media (max-width: 700px) {
  .hr-profile-detail-shell {
    width: 100% !important;
    max-width: none;
  }
}
</style>

<style lang="scss" scoped>
.hr-drawer-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hr-drawer-title__icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #9fbfb2;
  border-radius: 13px;
  background: var(--erp-primary, #0b6b53);
  color: #fff;
  box-shadow: 0 10px 22px rgba(11, 107, 83, 0.2);
  font-size: 19px;
}

.hr-drawer-title__copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  small {
    color: var(--erp-primary, #0b6b53);
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.16em;
  }

  strong {
    overflow: hidden;
    color: #1d2b42;
    font-size: 15px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  > span {
    color: #8a96a8;
    font-size: 10px;
  }
}

.hr-profile-drawer {
  --hr-drawer-accent: var(--erp-primary, #0b6b53);
  --hr-drawer-ink: var(--erp-text, #17211d);
  --hr-drawer-muted: var(--erp-text-secondary, #66736d);
  padding: 20px 22px 34px;
}

.hr-profile-hero {
  min-height: 136px;
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 22px;
  overflow: hidden;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-lg, 16px);
  background: var(--erp-surface, #ffffff);
  box-shadow: var(--erp-shadow-card, 0 8px 22px rgba(23, 33, 29, 0.055));

  &::before,
  &::after {
    display: none;
    content: none;
  }
}

.hr-profile-avatar {
  width: 66px;
  height: 66px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.88);
  border-radius: 20px;
  background: var(--hr-drawer-accent);
  color: #fff;
  box-shadow: 0 12px 26px rgba(11, 107, 83, 0.2);
  font-size: 27px;
  font-weight: 700;
}

.hr-profile-identity {
  min-width: 0;
  z-index: 1;

  h2 {
    margin: 4px 0 9px;
    color: var(--hr-drawer-ink);
    font-size: 24px;
    font-weight: 750;
    letter-spacing: -0.02em;
    line-height: 1.2;
  }
}

.hr-profile-eyebrow {
  color: var(--hr-drawer-accent);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.hr-profile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;

  span {
    min-width: 0;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #68778d;
    font-size: 11px;
  }

  i { color: #7685dc; }
}

.hr-account-badge {
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: start;
  padding: 8px 10px;
  border: 1px solid #c9eadc;
  border-radius: 10px;
  background: #edf8f4;
  color: #278a69;
  font-size: 10px;
  font-weight: 650;

  &.is-disabled {
    color: #718096;
    border-color: #dde3eb;
    background: #f3f5f8;
  }
}

.hr-drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(23, 33, 29, 0.045);

  ::v-deep .el-button {
    height: 34px;
    margin-left: 0;
    padding: 0 13px;
    border-radius: 9px;
    font-size: 11px;
    font-weight: 600;
  }

  ::v-deep .el-button--primary {
    border-color: var(--hr-drawer-accent);
    background: var(--hr-drawer-accent);
    box-shadow: 0 6px 14px rgba(11, 107, 83, 0.18);
  }
}

.hr-drawer-section {
  margin-bottom: 12px;
  padding: 18px;
  border: 1px solid var(--erp-border, #dde2de);
  border-radius: var(--erp-radius-md, 12px);
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(23, 33, 29, 0.05);
}

.hr-section-heading {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 14px;

  > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }

  h3 {
    margin: 0;
    color: #26354d;
    font-size: 14px;
    font-weight: 700;
  }

  p {
    margin: 0;
    color: #919cad;
    font-size: 10px;
  }
}

.hr-section-heading__icon {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: var(--erp-primary-soft, #e7f2ed);
  color: var(--hr-drawer-accent);
  font-size: 16px;

  &.is-health { color: #2b909a; background: #eaf8f9; }
  &.is-coverage { color: #b47a17; background: #fff6e4; }
  &.is-detail { color: #6c55c7; background: #f2edff; }
}

.hr-section-heading__action {
  margin-left: auto;
  border-radius: 8px;
  color: var(--hr-drawer-accent);
  font-size: 11px;
  font-weight: 600;
}

.hr-health-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.hr-health-item {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px;
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  background: #f8f9fc;

  > div { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
  small { color: #98a2b2; font-size: 9px; }
  strong { overflow: hidden; color: #47566e; font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }

  &.is-warning {
    border-color: #f2dfba;
    background: #fffaf1;
  }

  ::v-deep .el-tag { align-self: flex-start; border-radius: 7px; font-weight: 600; }
}

.hr-health-item__icon {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #eef1ff;
  color: #6071db;
  font-size: 14px;
}

.hr-info-block {
  min-height: 184px;
  padding: 14px;
  border: 1px solid #e6eaf1;
  border-radius: 14px;
  background: linear-gradient(145deg, #fafbfe, #fff);

  > strong {
    display: block;
    margin: 12px 0 5px;
    overflow: hidden;
    color: #2d3d55;
    font-size: 14px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  > p { margin: 0 0 10px; color: #788599; font-size: 11px; }

  dl { margin: 0; padding-top: 9px; border-top: 1px dashed #e2e7ef; }
  dl div { display: flex; justify-content: space-between; gap: 12px; padding: 4px 0; font-size: 10px; }
  dt { flex: 0 0 auto; color: #9aa4b3; }
  dd { margin: 0; overflow: hidden; color: #5e6c80; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
}

.hr-info-block__heading {
  display: flex;
  align-items: center;
  gap: 8px;

  > span {
    width: 28px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 9px;
    background: #eef1ff;
    color: #6071da;
  }

  > strong { color: #536176; font-size: 11px; }

  &.is-contract > span { color: #248e75; background: #eaf8f3; }
}

.hr-missing-summary { min-width: 0; }

.hr-coverage-overview {
  display: grid;
  grid-template-columns: 118px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  padding: 14px;
  border: 1px solid #e7eaf1;
  border-radius: 14px;
  background: linear-gradient(135deg, #fafbfe, #fff);
}

.hr-coverage-score {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 3px;
  border-radius: 12px;
  background: #fff0f2;
  color: #c55260;

  strong { font-size: 27px; line-height: 1; }
  span { font-size: 9px; font-weight: 600; }

  &.is-progress { color: #b57c1c; background: #fff7e8; }
  &.is-complete { color: #278a69; background: #edf8f4; }
}

.hr-coverage-main {
  min-width: 0;
  display: flex;
  justify-content: center;
  flex-direction: column;

  ::v-deep .el-progress-bar__outer { background: #e8ecf3; }
  ::v-deep .el-progress-bar__inner { background: var(--hr-drawer-accent); }

  > p { margin: 8px 0 0; color: #7e899b; font-size: 10px; }
}

.hr-coverage-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #8c97a8;
  font-size: 9px;

  strong { color: #4b5a71; font-size: 11px; }
}

.hr-missing-guidance {
  margin-top: 10px;
  padding: 11px 12px;
  border: 1px solid #dce7f3;
  border-radius: 12px;
  background: #f4f8fc;
  color: #68778a;
  font-size: 10px;

  p { margin: 4px 0 0; line-height: 1.55; }
  p span { color: #44546b; font-weight: 650; }
}

.hr-missing-guidance__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
  color: #5264a5;
}

.hr-missing-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-top: 10px;
}

.hr-missing-group {
  min-width: 0;
  padding: 11px;
  border: 1px solid #eceef3;
  border-radius: 12px;
  background: #fbfbfd;
}

.hr-missing-group__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;

  strong { color: #4a586d; font-size: 11px; }
  span { color: #a36c74; font-size: 9px; }
}

.hr-missing-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;

  ::v-deep .el-tag {
    height: 23px;
    padding: 0 7px;
    border-color: #f2cfd4;
    border-radius: 7px;
    background: #fff7f8;
    color: #c45b68;
    line-height: 21px;
    font-weight: 500;
  }
}

.hr-derived-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dde2ea;
  color: #929cac;
  font-size: 9px;
  line-height: 1.5;

  span:first-child { color: #657287; font-weight: 600; }
}

.hr-complete-copy {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 10px;
  padding: 16px;
  border-radius: 12px;
  background: #edf8f4;
  color: #278a69;
  font-size: 12px;
  font-weight: 650;
}

.hr-detail-section { padding-bottom: 14px; }

.hr-detail-tabs {
  ::v-deep .el-tabs__header { margin: 0 0 12px; }
  ::v-deep .el-tabs__nav-wrap::after { height: 1px; background: #e8ebf1; }
  ::v-deep .el-tabs__item { height: 38px; padding: 0 13px; color: #748196; font-size: 10px; font-weight: 600; line-height: 38px; }
  ::v-deep .el-tabs__item.is-active { color: var(--hr-drawer-accent); }
  ::v-deep .el-tabs__active-bar { height: 3px; border-radius: 3px 3px 0 0; background: var(--hr-drawer-accent); }
  ::v-deep .el-descriptions__table { overflow: hidden; border-radius: 12px; }
  ::v-deep .el-descriptions-item__label.is-bordered-label { width: 112px; padding: 10px 12px; color: #788599; background: #f7f8fb; font-size: 10px; font-weight: 600; }
  ::v-deep .el-descriptions-item__content { padding: 10px 12px; color: #394960; font-size: 11px; line-height: 1.55; }
}

.hr-drawer-empty {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 40px;
  color: #8c98aa;
  text-align: center;

  > span {
    width: 62px;
    height: 62px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 14px;
    border-radius: 20px;
    background: #e9edff;
    color: #6575dc;
    font-size: 25px;
  }

  strong { color: #46556b; font-size: 15px; }
  p { margin: 7px 0 0; font-size: 11px; }
}

@media (max-width: 720px) {
  .hr-profile-drawer { padding: 14px 12px 24px; }
  .hr-profile-hero { grid-template-columns: auto 1fr; padding: 16px; }
  .hr-account-badge { grid-column: 1 / -1; justify-self: start; }
  .hr-health-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hr-missing-groups { grid-template-columns: 1fr; }
  .hr-coverage-overview { grid-template-columns: 92px minmax(0, 1fr); }
  .hr-info-block { margin-bottom: 10px; }
}
</style>
