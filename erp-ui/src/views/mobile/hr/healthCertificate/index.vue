<template>
  <div class="mobile-business-page mobile-system-page">
    <health-certificate-page />
  </div>
</template>

<script>
import HealthCertificatePage from "@/views/hr/healthCertificate/index.vue"

export default {
  name: "MobileHrHealthCertificate",
  components: { HealthCertificatePage }
}
</script>

<style scoped lang="scss">
.mobile-business-page {
  min-height: var(--mobile-viewport-height);
  padding: 0 0 calc(20px + var(--mobile-safe-bottom));
  background: var(--mobile-color-page);
}

.mobile-business-page ::v-deep .health-certificate-page {
  max-width: 680px;
  margin: 0 auto;
  padding: 14px;
  background: transparent;
}

.mobile-business-page ::v-deep .page-heading {
  align-items: center;
  gap: 10px;
}

.mobile-business-page ::v-deep .page-heading > div {
  min-width: 0;
}

.mobile-business-page ::v-deep .page-heading h2 {
  color: var(--mobile-color-ink);
  font-size: 22px;
}

.mobile-business-page ::v-deep .page-heading p {
  color: var(--mobile-color-muted);
  line-height: 1.5;
}

.mobile-business-page ::v-deep .el-button,
.mobile-business-page ::v-deep .el-input__inner,
.mobile-business-page ::v-deep .el-select .el-input__inner,
.mobile-business-page ::v-deep .el-date-editor {
  min-height: 44px;
}

.mobile-business-page ::v-deep .el-button--mini {
  padding-top: 10px;
  padding-bottom: 10px;
  border-radius: var(--mobile-radius-md);
}

.mobile-business-page ::v-deep .el-button--primary {
  border-color: var(--mobile-color-primary);
  background: var(--mobile-color-primary);
}

.mobile-business-page ::v-deep .el-button.is-disabled {
  border-color: var(--mobile-color-line);
  color: var(--mobile-color-subtle);
  background: var(--mobile-color-surface-soft);
}

.mobile-business-page ::v-deep .health-ops-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mobile-business-page ::v-deep .health-ops-cell {
  min-width: 0;
  border-radius: var(--mobile-radius-md);
}

.mobile-business-page ::v-deep .el-tabs--border-card {
  border-color: var(--mobile-color-line);
  border-radius: var(--mobile-radius-lg);
  box-shadow: none;
  overflow: hidden;
}

.mobile-business-page ::v-deep .el-tabs__item {
  min-height: 44px;
  height: auto;
  padding-top: 11px;
  padding-bottom: 11px;
  line-height: 1.35;
  white-space: normal;
}

.mobile-business-page ::v-deep .el-tabs__item.is-active {
  color: var(--mobile-color-primary);
}

.mobile-business-page ::v-deep .el-table__header-wrapper,
.mobile-business-page ::v-deep .el-table__fixed,
.mobile-business-page ::v-deep .el-table__fixed-right {
  display: none;
}

.mobile-business-page ::v-deep .el-table,
.mobile-business-page ::v-deep .el-table__body-wrapper,
.mobile-business-page ::v-deep .el-table__body,
.mobile-business-page ::v-deep .el-table__body tbody {
  width: 100% !important;
  overflow: visible;
}

.mobile-business-page ::v-deep .el-table::before {
  display: none;
}

.mobile-business-page ::v-deep .el-table__row {
  width: 100%;
  display: block;
  margin-bottom: 12px;
  padding: 8px 12px;
  box-sizing: border-box;
  border: 1px solid var(--mobile-color-line);
  border-radius: var(--mobile-radius-md);
  background: var(--mobile-color-surface);
}

.mobile-business-page ::v-deep .el-table__row td {
  width: 100% !important;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  align-items: center;
  box-sizing: border-box;
  padding: 8px 0;
  border: 0;
  text-align: left;
}

.mobile-business-page ::v-deep .el-table__row td::before {
  color: var(--mobile-color-muted);
  font-size: 12px;
  font-weight: 700;
}

.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(1)::before { content: "证件编号"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(2)::before { content: "办理日期"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(3)::before { content: "生效日期"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(4)::before { content: "到期日期"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(5)::before { content: "状态"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(6)::before { content: "附件"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(7)::before { content: "审核说明"; }
.mobile-business-page ::v-deep .mine-health-table .el-table__row td:nth-child(8)::before { content: "操作"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(1)::before { content: "员工"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(2)::before { content: "当前组织"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(3)::before { content: "证件编号"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(4)::before { content: "办理日期"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(5)::before { content: "生效日期"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(6)::before { content: "到期日期"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(7)::before { content: "状态"; }
.mobile-business-page ::v-deep .admin-health-table .el-table__row td:nth-child(8)::before { content: "操作"; }

.mobile-business-page ::v-deep .el-table__row td:last-child .cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-business-page ::v-deep .el-table__row td:last-child .el-button {
  margin: 0;
  padding-right: 10px;
  padding-left: 10px;
  border: 1px solid var(--mobile-color-line);
  color: var(--mobile-color-primary);
  background: var(--mobile-color-surface-soft);
}

.mobile-business-page ::v-deep .filter-bar {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.mobile-business-page ::v-deep .filter-bar .el-form-item {
  width: 100%;
  margin: 0;
}

.mobile-business-page ::v-deep .filter-bar .el-form-item__content,
.mobile-business-page ::v-deep .filter-bar .el-select {
  width: 100%;
}

.mobile-business-page ::v-deep .el-dialog {
  width: calc(100vw - 24px) !important;
  max-width: 620px;
  max-height: calc(var(--mobile-viewport-height) - 24px);
  margin: 12px auto !important;
  display: flex;
  flex-direction: column;
}

.mobile-business-page ::v-deep .el-dialog__body {
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
}

.mobile-business-page ::v-deep .el-dialog__footer {
  padding: 10px 14px max(14px, var(--mobile-safe-bottom));
}

@media (max-width: 380px) {
  .mobile-business-page ::v-deep .health-ops-grid {
    grid-template-columns: 1fr;
  }

  .mobile-business-page ::v-deep .el-table__row td {
    grid-template-columns: 76px minmax(0, 1fr);
  }
}
</style>

<style lang="scss">
@media (max-width: 760px) {
  .health-certificate-start-recovery-dialog,
  .health-certificate-mine-dialog,
  .health-certificate-review-dialog,
  .health-approval-dialog {
    width: calc(100vw - 24px) !important;
    max-width: 620px;
    max-height: calc(100dvh - 24px);
    margin: 12px auto !important;
    display: flex;
    flex-direction: column;
  }

  .health-approval-dialog {
    max-width: 920px;
  }

  .health-certificate-start-recovery-dialog .el-dialog__body,
  .health-certificate-mine-dialog .el-dialog__body,
  .health-certificate-review-dialog .el-dialog__body,
  .health-approval-dialog .el-dialog__body {
    min-height: 0;
    overflow-y: auto;
    padding: 14px;
  }

  .health-certificate-start-recovery-dialog .el-dialog__footer,
  .health-certificate-mine-dialog .el-dialog__footer,
  .health-certificate-review-dialog .el-dialog__footer,
  .health-approval-dialog .el-dialog__footer {
    padding: 10px 14px max(14px, env(safe-area-inset-bottom));
  }

  .health-certificate-mine-dialog .el-form-item__label,
  .health-certificate-review-dialog .el-form-item__label {
    width: auto !important;
    float: none;
    padding-bottom: 7px;
    line-height: 1.35;
    text-align: left;
  }

  .health-certificate-mine-dialog .el-form-item__content,
  .health-certificate-review-dialog .el-form-item__content {
    margin-left: 0 !important;
  }

  .health-certificate-mine-dialog .el-row {
    margin-right: 0 !important;
    margin-left: 0 !important;
  }

  .health-certificate-mine-dialog .el-col-12 {
    width: 100%;
    padding-right: 0 !important;
    padding-left: 0 !important;
  }

  .health-certificate-mine-dialog .el-input__inner,
  .health-certificate-review-dialog .el-input__inner,
  .health-certificate-mine-dialog .el-button,
  .health-certificate-review-dialog .el-button,
  .health-approval-dialog .el-button {
    min-height: 44px;
  }

  .health-approval-dialog .el-descriptions__body,
  .health-approval-dialog .approval-table-scroll {
    overflow-x: auto;
  }
}
</style>
