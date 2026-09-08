<template>
  <article class="onboarding-detail-pane">
    <div class="detail-scroll">
      <header class="detail-identity">
        <div class="detail-avatar" aria-hidden="true">{{ employeeInitial }}</div>
        <div class="detail-identity__body">
          <div class="detail-title-line">
            <h2>{{ detail.employeeName || "未命名员工" }}</h2>
            <el-tag size="small" :type="status.type">{{ status.label }}</el-tag>
          </div>
          <div class="detail-contact-line">
            <span><i class="el-icon-phone-outline" />{{ detail.phoneNumberMasked || "手机未填写" }}</span>
            <i class="contact-separator" />
            <span><i class="el-icon-message" />{{ detail.email || "邮箱未填写" }}</span>
            <i class="contact-separator" />
            <span><i class="el-icon-postcard" />身份证：{{ detail.idNumberMasked || "未填写" }}</span>
          </div>
        </div>
      </header>

      <section class="detail-facts">
        <div class="detail-fact">
          <i class="el-icon-date" />
          <div><span>入职日期</span><strong>{{ detail.expectedEntryDate || "待填写" }}</strong></div>
        </div>
        <div class="detail-fact">
          <i class="el-icon-office-building" />
          <div><span>目标组织</span><strong>{{ organizationLabel }}</strong></div>
        </div>
        <div class="detail-fact">
          <i class="el-icon-suitcase" />
          <div><span>目标岗位</span><strong>{{ detail.positionName || "待填写" }}</strong></div>
        </div>
      </section>

      <section class="onboarding-stage-card">
        <div class="completion-summary">
          <div>
            <span>入职必填完成度</span>
            <strong>{{ completionPercent }}%</strong>
            <small>已填 {{ detail.onboardingCompletedFieldCount || 0 }}/{{ detail.onboardingRequiredFieldCount || 0 }}</small>
          </div>
          <div v-if="linkedEmployeeProfile" class="profile-coverage-summary">
            <span>档案覆盖度</span>
            <strong>{{ safePercent(linkedEmployeeProfile.profileCompletionPercent) }}%</strong>
            <small>已填 {{ linkedEmployeeProfile.profileCompletedFieldCount || 0 }}/适用 {{ linkedEmployeeProfile.profileApplicableFieldCount || 0 }}</small>
          </div>
        </div>
        <div class="onboarding-stage-track">
          <div
            v-for="(step, index) in stageSteps"
            :key="step"
            class="onboarding-stage"
            :class="{ 'is-active': index + 1 <= currentStage, 'is-current': index + 1 === currentStage }"
          >
            <div class="stage-marker-row">
              <span class="stage-marker">{{ index + 1 }}</span>
              <i v-if="index < stageSteps.length - 1" class="stage-line" />
            </div>
            <span class="stage-label">{{ step }}</span>
          </div>
        </div>
      </section>

      <section class="missing-materials-section">
        <div class="section-title">
          <h3>缺失资料</h3>
          <span>
            {{ missingFieldCount ? `当前需补 ${missingFieldCount} 项` : "当前无确认入职阻塞项" }}
            <template v-if="postEntryFieldCount"> · 另有 {{ postEntryFieldCount }} 项可后续完善（不影响确认入职）</template>
          </span>
        </div>
        <div v-if="!materialRows.length" class="section-empty">
          <i class="el-icon-circle-check" /> 当前没有缺失资料
        </div>
        <div v-else class="missing-materials-card">
          <div v-for="row in materialRows" :key="row.key" class="missing-material-row">
            <div class="material-group-title">
              <span class="material-group-icon"><i :class="row.icon" /></span>
              <strong>{{ row.label }}</strong>
            </div>
            <div class="material-fields">
              <div v-for="field in row.fields" :key="`${row.key}-${field.key}`" class="material-field">
                <i class="material-field__dot" />
                <span>{{ field.label || field.key }}</span>
                <el-tag class="missing-badge" size="mini" :type="row.postEntry ? 'info' : 'danger'" effect="plain">
                  {{ row.postEntry ? "可后续完善" : "缺失" }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="readinessBlockingCodes.length" class="detail-section detail-section--blocking">
        <div class="section-title">
          <h3>流程阻塞原因</h3>
          <span>处理后即可继续标记资料就绪</span>
        </div>
        <div class="risk-list">
          <el-alert
            v-for="code in readinessBlockingCodes"
            :key="code"
            :title="blockerLabel(code)"
            type="error"
            :closable="false"
            show-icon
          />
        </div>
      </section>

      <section v-if="riskCodes.length" class="detail-section detail-section--secondary">
        <div class="section-title"><h3>账号配置风险</h3></div>
        <div class="risk-list">
          <el-alert
            v-for="risk in riskCodes"
            :key="risk"
            :title="riskLabel(risk)"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </section>

      <section v-if="presentedOperationLogs.length" class="detail-section detail-section--secondary">
        <div class="section-title">
          <h3>操作记录</h3>
          <span>最近操作优先</span>
        </div>
        <el-timeline>
          <el-timeline-item
            v-for="(log, index) in visibleOperationLogs"
            :key="`${log.time}-${log.title}-${index}`"
            :timestamp="log.time"
            placement="top"
          >
            <div class="activity-title">
              <strong>{{ log.title }}</strong>
              <el-tag size="mini" :type="log.tagType" effect="plain">{{ log.operator }}</el-tag>
            </div>
            <p>{{ log.description }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-button
          v-if="presentedOperationLogs.length > 5"
          class="activity-toggle"
          type="text"
          @click="showAllLogs = !showAllLogs"
        >{{ showAllLogs ? "收起较早记录" : `查看全部 ${presentedOperationLogs.length} 条记录` }}</el-button>
      </section>
    </div>

    <footer class="sticky-action-footer">
      <div class="secondary-actions">
        <el-button
          v-for="action in secondaryActions"
          :key="action.key"
          :type="action.type"
          :plain="action.plain"
          size="small"
          v-hasPermi="[action.permission]"
          @click="$emit('action', action.key)"
        >{{ action.label }}</el-button>
      </div>
      <div class="primary-action-area">
        <span v-if="!confirmAction">{{ confirmDisabledReason }}</span>
        <el-button
          class="primary-confirm-action"
          type="primary"
          :disabled="!confirmAction"
          v-hasPermi="['hr:onboarding:confirm']"
          @click="confirmAction && $emit('action', 'CONFIRM')"
        >确认入职</el-button>
      </div>
    </footer>
  </article>
</template>

<script>
import {
  ACCOUNT_RISK_LABELS,
  READINESS_BLOCKER_LABELS,
  employeeInitial,
  missingFieldGroups,
  missingGroupLabel,
  onboardingStage,
  postEntryMissingFieldGroups,
  presentOnboardingLog,
  statusMeta,
  visibleOnboardingActions
} from "../onboardingFieldConfig"

const stageSteps = ["资料收集", "审核中", "待到岗", "试用期", "已入职"]
const groupIcon = label => {
  if (/身份|基础|个人|证件/.test(label)) return "el-icon-user"
  if (/组织|岗位|部门|门店/.test(label)) return "el-icon-office-building"
  if (/合同|社保|公积金|用工/.test(label)) return "el-icon-circle-check"
  return "el-icon-document"
}

export default {
  name: "HrOnboardingDetailPane",
  props: {
    detail: { type: Object, required: true },
    linkedEmployeeProfile: { type: Object, default: null }
  },
  data() {
    return { stageSteps, showAllLogs: false }
  },
  computed: {
    status() {
      return statusMeta(this.detail.status)
    },
    employeeInitial() {
      return employeeInitial(this.detail.employeeName)
    },
    organizationLabel() {
      return this.detail.storeName || this.detail.deptLevel3Name || this.detail.deptLevel2Name ||
        this.detail.deptLevel1Name || this.detail.companyName || "待填写"
    },
    onboardingMissingGroups() {
      return missingFieldGroups(this.detail.missingOnboardingFields)
    },
    profileMissingGroups() {
      return postEntryMissingFieldGroups(this.detail.missingProfileFields, this.detail.missingOnboardingFields)
    },
    materialRows() {
      const rows = []
      const append = (groups, postEntry) => groups.forEach((group, index) => {
        const label = missingGroupLabel(group.group)
        const existing = rows.find(item => item.label === label && item.postEntry === postEntry)
        if (existing) existing.fields.push(...group.fields)
        else rows.push({
          key: `${postEntry ? "post" : "now"}-${label}-${index}`,
          label,
          icon: groupIcon(label),
          fields: [...group.fields],
          postEntry
        })
      })
      append(this.onboardingMissingGroups, false)
      append(this.profileMissingGroups, true)
      return rows
    },
    missingFieldCount() {
      return this.materialRows.filter(row => !row.postEntry)
        .reduce((total, row) => total + row.fields.length, 0)
    },
    postEntryFieldCount() {
      return this.materialRows.filter(row => row.postEntry)
        .reduce((total, row) => total + row.fields.length, 0)
    },
    completionPercent() {
      return this.safePercent(this.detail.onboardingCompletionPercent)
    },
    currentStage() {
      return onboardingStage(this.detail.status)
    },
    riskCodes() {
      return Array.isArray(this.detail.accountConfigurationRiskCodes)
        ? this.detail.accountConfigurationRiskCodes
        : []
    },
    readinessBlockingCodes() {
      return Array.isArray(this.detail.readinessBlockingCodes)
        ? this.detail.readinessBlockingCodes
        : []
    },
    operationLogs() {
      return Array.isArray(this.detail.operationLogs) ? this.detail.operationLogs : []
    },
    presentedOperationLogs() {
      return this.operationLogs.map(presentOnboardingLog).filter(Boolean).reverse()
    },
    visibleOperationLogs() {
      return this.showAllLogs ? this.presentedOperationLogs : this.presentedOperationLogs.slice(0, 5)
    },
    visibleActions() {
      return visibleOnboardingActions(this.detail.allowedActions)
    },
    confirmAction() {
      return this.visibleActions.find(action => action.key === "CONFIRM") || null
    },
    confirmDisabledReason() {
      if (this.confirmAction) return ""
      if (this.detail.status === "CONFIRMED") return "该员工已完成入职"
      if (this.detail.status === "CANCELLED") return "入职单已取消，恢复后才能继续"
      if (this.detail.status === "DRAFT") {
        if (this.missingFieldCount) return `请先补齐 ${this.missingFieldCount} 项资料，并标记资料就绪`
        if (this.readinessBlockingCodes.length) return `请先处理 ${this.readinessBlockingCodes.length} 项组织配置问题`
        return "请先标记资料就绪"
      }
      if (this.riskCodes.length) return "请先处理账号配置风险"
      return "当前状态暂不能确认入职"
    },
    secondaryActions() {
      return this.visibleActions.filter(action => action.key !== "CONFIRM")
    }
  },
  watch: {
    "detail.onboardingId"() {
      this.showAllLogs = false
    }
  },
  methods: {
    safePercent(value) {
      const number = Number(value)
      if (!Number.isFinite(number)) return 0
      return Math.round(Math.min(100, Math.max(0, number)))
    },
    riskLabel(code) {
      return ACCOUNT_RISK_LABELS[code] || "其他账号风险"
    },
    blockerLabel(code) {
      return READINESS_BLOCKER_LABELS[code] || "流程暂被未知配置问题阻塞"
    }
  }
}
</script>

<style lang="scss" scoped>
.onboarding-detail-pane {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.detail-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 38px 34px 24px;
  scrollbar-width: thin;
}

.detail-identity { display: flex; align-items: center; gap: 22px; }
.detail-avatar {
  flex: 0 0 62px;
  width: 62px;
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #8d86ee;
  color: #fff;
  font-size: 28px;
  font-weight: 400;
}
.detail-identity__body { min-width: 0; }
.detail-title-line { display: flex; align-items: center; gap: 14px; }
.detail-title-line h2 { margin: 0; color: #222733; font-size: 23px; line-height: 1.35; }
.detail-contact-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 9px;
  color: #626a79;
  font-size: 13px;

  span { display: inline-flex; align-items: center; gap: 7px; }
  span > i { color: #4f5868; font-size: 16px; }
}
.contact-separator { width: 1px; height: 14px; background: #d8dce3; }

.detail-facts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 34px;
  padding: 0 4px;
}
.detail-fact {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 2px 28px;
  border-right: 1px solid #e3e6ec;

  &:first-child { padding-left: 0; }
  &:last-child { border-right: 0; }
  > i { color: #303746; font-size: 24px; }
  div { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
  span { color: #767e8d; font-size: 13px; }
  strong { overflow: hidden; color: #252b36; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
}

.onboarding-stage-card {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  align-items: stretch;
  margin-top: 32px;
  border: 1px solid #e0e4eb;
  border-radius: 8px;
  background: #fff;
}
.completion-summary {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
  padding: 20px 22px;
  border-right: 1px solid #e0e4eb;

  > div { display: flex; flex-direction: column; }
  span { color: #4e5665; font-size: 13px; }
  strong { margin-top: 7px; color: #5148e5; font-size: 26px; line-height: 1; }
  small { margin-top: 5px; color: #7e8695; font-size: 11px; }
}
.profile-coverage-summary { padding-top: 12px; border-top: 1px solid #e0e4eb; }
.onboarding-stage-track { min-width: 0; display: grid; grid-template-columns: repeat(5, 1fr); padding: 20px 16px 14px 32px; }
.onboarding-stage { min-width: 0; color: #8b92a0; }
.stage-marker-row { display: flex; align-items: center; }
.stage-marker {
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eef0f4;
  color: #6d7481;
  font-size: 13px;
}
.stage-line { flex: 1; height: 1px; margin: 0 8px; background: #dfe3ea; }
.stage-label { display: block; margin-top: 10px; margin-left: -13px; color: #737b8a; font-size: 12px; text-align: center; }
.onboarding-stage.is-active .stage-marker { background: #5148e5; color: #fff; }
.onboarding-stage.is-active .stage-line { background: #5148e5; }
.onboarding-stage.is-current .stage-marker { box-shadow: 0 0 0 4px #eeedff; }

.missing-materials-section { margin-top: 26px; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 12px; }
.section-title h3 { margin: 0; color: #282e38; font-size: 16px; }
.section-title > span { color: #7e8695; font-size: 13px; }
.missing-materials-card { overflow: hidden; border: 1px solid #e0e4eb; border-radius: 8px; background: #fff; }
.missing-material-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  align-items: stretch;
  min-height: 100px;
  padding: 0 24px;

  & + & { border-top: 1px solid #e6e9ee; }
}
.material-group-title { display: flex; align-items: center; gap: 13px; padding-right: 20px; border-right: 1px solid #e6e9ee; }
.material-group-title strong { color: #3b424e; font-size: 14px; }
.material-group-icon {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0efff;
  color: #5b50ec;
  font-size: 22px;
}
.material-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: center; gap: 14px 28px; padding: 18px 22px; }
.material-field { min-width: 0; display: flex; align-items: center; gap: 9px; color: #5d6573; font-size: 13px; }
.material-field > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.material-field__dot { flex: 0 0 4px; width: 4px; height: 4px; border-radius: 50%; background: #c3c8d1; }
.missing-badge { flex-shrink: 0; }
.section-empty { padding: 34px 0; border: 1px solid #e0e4eb; border-radius: 8px; color: #697181; text-align: center; }
.section-empty i { color: #22a06b; }

.detail-section--secondary { margin-top: 18px; padding: 18px; border: 1px solid #e0e4eb; border-radius: 8px; background: #fff; }
.detail-section--blocking { margin-top: 18px; padding: 18px; border: 1px solid #f5c2c0; border-radius: 8px; background: #fffafa; }
.risk-list { display: grid; gap: 8px; }
.el-timeline p { margin: 5px 0 0; color: #697181; }
.activity-title { display: flex; align-items: center; gap: 8px; }
.activity-toggle { margin-left: 28px; }

.sticky-action-footer {
  flex-shrink: 0;
  z-index: 2;
  min-height: 78px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 34px;
  border-top: 1px solid #e5e8ee;
  background: rgba(255, 255, 255, 0.98);
}
.secondary-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.secondary-actions .el-button + .el-button { margin-left: 0; }
.primary-confirm-action { min-width: 230px; height: 44px; font-size: 15px; }
.primary-action-area {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;

  > span { max-width: 240px; color: #909399; font-size: 12px; text-align: right; }
}

@media (max-width: 1280px) {
  .detail-scroll { padding: 28px 24px 20px; }
  .detail-contact-line { gap: 8px; }
  .detail-fact { padding-left: 18px; padding-right: 18px; }
  .onboarding-stage-card { grid-template-columns: 120px minmax(0, 1fr); }
  .onboarding-stage-track { padding-left: 18px; padding-right: 8px; }
  .missing-material-row { grid-template-columns: 150px minmax(0, 1fr); padding: 0 16px; }
  .material-fields { padding-left: 16px; padding-right: 8px; gap: 12px; }
  .sticky-action-footer { padding-left: 24px; padding-right: 24px; }
  .primary-confirm-action { min-width: 190px; }
}
</style>
