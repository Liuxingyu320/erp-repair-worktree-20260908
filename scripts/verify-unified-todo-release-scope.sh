#!/usr/bin/env bash

# Verify the explicit unified-todo release scope against the isolated base.
# This command is read-only: it never stages, deletes, restores, or rewrites files.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_COMMIT="b694659dd2c19dc87377d93eaed6bbbf9a5ac0b7"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

pass()
{
    printf '[PASS] %s\n' "$*"
}

info()
{
    printf '[INFO] %s\n' "$*"
}

ALLOWED_SCOPE="$(cat <<'ALLOWED_SCOPE_EOF'
docker/mysql/db/erp_hr_health_certificate_20260713.sql
docs/superpowers/plans/2026-07-13-unified-todo-business-scope-completion.md
docs/superpowers/plans/2026-07-14-unified-todo-production-readiness.md
docs/superpowers/verification/2026-07-13-unified-todo-business-scope-completion.md
docs/待办功能第四阶段详细实施方案-20260713.md
docs/待办功能第四阶段验收报告-20260713.md
docs/待办功能第五阶段验收报告-20260714.md
docs/本机待办联调运行手册-20260713.md
erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteFileService.java
erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/DriveBusinessFile.java
erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteFileFallbackFactory.java
erp-api/pom.xml
erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/ServiceNameConstants.java
erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoConstants.java
erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java
erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java
erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/ShopScopeService.java
erp-gateway/src/main/resources/bootstrap.yml
erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferDiscrepancyDecisions.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferTypes.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockCheckController.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferDetail.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferDiscrepancy.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferDiscrepancyDetail.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferShipment.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvReceiveItem.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvStockCheckAssignmentRequest.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvTransferDiscrepancyResolveRequest.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvStockCheckCounterCandidate.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvTransferOpsSummaryVo.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckMapper.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTodoMapper.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferDetailMapper.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferDiscrepancyMapper.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferOrderMapper.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/metric/InventoryBusinessMetrics.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/BusinessFeatureGate.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvStockCheckService.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvTransferService.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeTransferCreationService.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStateGuard.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDirectionPolicy.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessor.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReceiptProcessor.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferWorkflowResources.java
erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferWorkflowSupport.java
erp-modules/erp-inventory/src/main/resources/bootstrap.yml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckMapper.xml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDetailMapper.xml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyMapper.xml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml
erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferShipmentMapper.xml
erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvTodoControllerTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/StockCheckApprovalMapperBindingTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/BusinessFeatureGateTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImplTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckServiceImplTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTodoServiceImplTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImplTest.java
erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java
erp-modules/erp-oa/pom.xml
erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignScenarioCodes.java
erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java
erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java
erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetExceptionHandler.java
erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetRepairController.java
erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetConfig.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTemplate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaFixedAssetRepairPrecheckVo.java
erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoCandidate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/exception/OaFixedAssetValidationException.java
erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaDeptScopeMapper.java
erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetQuotaMapper.java
erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/BusinessFeatureGate.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaFixedAssetService.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java
erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java
erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetConfigMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml
erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml
erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaPurchaseControllerFeatureGateTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaTodoControllerTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/BusinessFeatureGateTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaFixedAssetServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaLaborContractServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaPurchaseServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSalaryServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskServiceImplTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java
erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java
erp-modules/erp-system/src/main/java/com/erp/system/constant/SysTodoTypes.java
erp-modules/erp-system/src/main/java/com/erp/system/controller/HrHealthCertificateController.java
erp-modules/erp-system/src/main/java/com/erp/system/controller/SysTodoController.java
erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/HrHealthCertificate.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrHealthCertificateReviewRequest.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrHealthCertificateSubmitRequest.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeQuery.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeSummaryVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrHealthCertificateCapabilityVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrHealthCertificateOpsSummaryVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrHealthCertificateVo.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysHealthCertificateTodoCandidate.java
erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysTodoCandidateRow.java
erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrHealthCertificateMapper.java
erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysTodoMapper.java
erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java
erp-modules/erp-system/src/main/java/com/erp/system/metric/HrBusinessMetrics.java
erp-modules/erp-system/src/main/java/com/erp/system/service/BusinessFeatureGate.java
erp-modules/erp-system/src/main/java/com/erp/system/service/IHrEmployeeProfileService.java
erp-modules/erp-system/src/main/java/com/erp/system/service/IHrHealthCertificateService.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeAccessService.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateAccessService.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateFeatureService.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateReminderScanner.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java
erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java
erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeCompletenessSnapshot.java
erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java
erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeePopulationPolicy.java
erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml
erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml
erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml
erp-modules/erp-system/src/test/java/com/erp/system/controller/HrHealthCertificateControllerTest.java
erp-modules/erp-system/src/test/java/com/erp/system/controller/SysTodoControllerTest.java
erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeHealthCertificateNativeMySqlTest.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeMapperBindingTest.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrHealthCertificateNativeMySqlFlowTest.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrHealthCertificateOpsMapperBindingTest.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrNativeMySqlTestSupport.java
erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java
erp-modules/erp-system/src/test/java/com/erp/system/service/BusinessFeatureGateTest.java
erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateFeatureServiceTest.java
erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateMySqlIT.java
erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateReminderScannerTest.java
erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrHealthCertificateServiceImplTest.java
erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java
erp-modules/erp-system/src/test/resources/hr-health-certificate-it-schema.sql
erp-ui/package.json
erp-ui/scripts/bundle-budget.cjs
erp-ui/scripts/check-production-bundle.cjs
erp-ui/src/api/hr/employee.js
erp-ui/src/api/hr/healthCertificate.js
erp-ui/src/api/inventory/customer.js
erp-ui/src/api/inventory/stockCheck.js
erp-ui/src/api/inventory/transfer.js
erp-ui/src/api/oa/fixedAsset.js
erp-ui/src/api/oa/signPackage.js
erp-ui/src/assets/styles/element-variables.scss
erp-ui/src/layout/components/HeaderTodo/index.vue
erp-ui/src/main.js
erp-ui/src/permission.js
erp-ui/src/plugins/download.js
erp-ui/src/plugins/element-services.js
erp-ui/src/plugins/element-ui.js
erp-ui/src/plugins/modal.js
erp-ui/src/router/index.js
erp-ui/src/store/getters.js
erp-ui/src/store/modules/todo.js
erp-ui/src/store/modules/user.js
erp-ui/src/utils/request.js
erp-ui/src/utils/signScenario.js
erp-ui/src/utils/todoAggregator.js
erp-ui/src/utils/todoBusinessFocus.js
erp-ui/src/utils/todoContextLease.js
erp-ui/src/utils/todoFilterQuery.js
erp-ui/src/utils/todoNavigator.js
erp-ui/src/utils/todoRouteResolver.js
erp-ui/src/views/hr/components/HrEmployeeList.vue
erp-ui/src/views/hr/components/HrEmployeeTransferDialog.vue
erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue
erp-ui/src/views/hr/healthCertificate/index.vue
erp-ui/src/views/inventory/sales/index.vue
erp-ui/src/views/inventory/stockCheck/index.vue
erp-ui/src/views/inventory/transfer/index.vue
erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue
erp-ui/src/views/mobile/customer/index.vue
erp-ui/src/views/mobile/drive/components/MobileDriveActionSheet.vue
erp-ui/src/views/mobile/drive/components/MobileDriveMoveSheet.vue
erp-ui/src/views/mobile/drive/components/MobileDriveRenameDialog.vue
erp-ui/src/views/mobile/drive/index.vue
erp-ui/src/views/mobile/drive/mobileDriveState.js
erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue
erp-ui/src/views/mobile/feature/components/MobileConfirmDialog.vue
erp-ui/src/views/mobile/feature/components/MobileCustomerRecordDialog.vue
erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue
erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue
erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue
erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue
erp-ui/src/views/mobile/feature/components/mobileSheet.scss
erp-ui/src/views/mobile/feature/featureActionRuntime.js
erp-ui/src/views/mobile/feature/featureActionService.js
erp-ui/src/views/mobile/feature/featureActions.js
erp-ui/src/views/mobile/feature/featureMapper.js
erp-ui/src/views/mobile/feature/featureSearchConfigs.js
erp-ui/src/views/mobile/feature/featureService.js
erp-ui/src/views/mobile/feature/index.vue
erp-ui/src/views/mobile/feature/mobileCustomerServiceRecord.js
erp-ui/src/views/mobile/feature/mobileEntityService.js
erp-ui/src/views/mobile/feature/mobileFormConfigs.js
erp-ui/src/views/mobile/feature/mobileFormPayloads.js
erp-ui/src/views/mobile/feature/mobileQuickCustomer.js
erp-ui/src/views/mobile/hr/healthCertificate/index.vue
erp-ui/src/views/mobile/mobileNavigation.js
erp-ui/src/views/mobile/mobileRouteDefinitions.js
erp-ui/src/views/mobile/signPackage/index.vue
erp-ui/src/views/mobile/todo/index.vue
erp-ui/src/views/oa/fixedAsset/config/index.vue
erp-ui/src/views/oa/fixedAsset/repair/index.vue
erp-ui/src/views/oa/signPackage/index.vue
erp-ui/src/views/oa/signTask/SignTaskConfirmDialog.vue
erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue
erp-ui/src/views/oa/signTask/index.vue
erp-ui/src/views/select-shop/index.vue
erp-ui/src/views/workbench/todo/index.vue
erp-ui/test/desktopAuditFollowupFixes.test.js
erp-ui/test/elementUiOnDemand.test.js
erp-ui/test/fixedAssetFeature.test.js
erp-ui/test/hrEmployeeTransferEffectiveDate.test.js
erp-ui/test/hrHealthCertificate.test.js
erp-ui/test/hrHealthCertificateUx.test.js
erp-ui/test/localTodoRuntimeContract.test.js
erp-ui/test/mobileAccessBoundary.test.js
erp-ui/test/mobileCloudDrive.test.js
erp-ui/test/mobileFeatureActions.test.js
erp-ui/test/mobileFeatureCompleteness.test.js
erp-ui/test/mobileFeatureComponentSplit.test.js
erp-ui/test/mobileFeatureFormConfig.test.js
erp-ui/test/mobileFeatureMapper.test.js
erp-ui/test/mobileFeatureRouteCoverage.test.js
erp-ui/test/mobileFormPayloads.test.js
erp-ui/test/mobileOaFeatureCompleteness.test.js
erp-ui/test/mobileQuickCustomer.test.js
erp-ui/test/mobileSystemFeatureCompleteness.test.js
erp-ui/test/productPickerSearchUx.test.js
erp-ui/test/productionAssetBudget.test.js
erp-ui/test/shopContextUx.test.js
erp-ui/test/signPackageModule.test.js
erp-ui/test/signTaskCenter.test.js
erp-ui/test/stockCheckApprovalUx.test.js
erp-ui/test/transferRequestFormUx.test.js
erp-ui/test/transferStockPickerPrivacy.test.js
erp-ui/test/transferWarehouseProcessingVisibility.test.js
erp-ui/test/unifiedTodoAggregation.test.js
erp-ui/test/unifiedTodoBusinessFocus.test.js
erp-ui/test/unifiedTodoBusinessScopeMigration.test.js
erp-ui/test/unifiedTodoContextLease.test.js
erp-ui/test/unifiedTodoDesktop.test.js
erp-ui/test/unifiedTodoFilterQuery.test.js
erp-ui/test/unifiedTodoMobile.test.js
erp-ui/test/unifiedTodoNavigator.test.js
erp-ui/test/unifiedTodoReleaseGate.test.js
erp-ui/test/unifiedTodoRouteResolver.test.js
erp-ui/test/unifiedTodoStore.test.js
erp-ui/vue.config.js
pom.xml
scripts/local-todo-stack.sh
scripts/unified-todo-release-20260714.list
scripts/verify-hr-health-certificate-migration.sql
scripts/verify-unified-todo-release-scope.sh
scripts/verify-unified-todo-release.sh
sql/erp_hr_health_certificate_20260713.sql
sql/erp_inventory_oe_replenishment_20260713.sql
sql/erp_inventory_performance_indexes_20260713.sql
sql/erp_inventory_transfer_discrepancy_20260713.sql
sql/erp_oa_fixed_asset_replenishment_outbox_20260713.sql
sql/erp_unified_todo_business_scope_20260713.sql
sql/erp_unified_todo_business_scope_rollback_20260713.sql
ALLOWED_SCOPE_EOF
)"

REQUIRED_FILES=(
    "erp-ui/src/utils/todoContextLease.js"
    "erp-ui/test/unifiedTodoContextLease.test.js"
    "scripts/local-todo-stack.sh"
    "erp-ui/test/localTodoRuntimeContract.test.js"
    "scripts/verify-unified-todo-release-scope.sh"
    "docs/待办功能第四阶段详细实施方案-20260713.md"
    "docs/本机待办联调运行手册-20260713.md"
    "docs/待办功能第四阶段验收报告-20260713.md"
    "docs/待办功能第五阶段验收报告-20260714.md"
)

ALLOWED_FILES=()
CHANGED_FILES=()
OUT_OF_SCOPE=()
FORBIDDEN_FILES=()

is_allowed()
{
    printf '%s\n' "${ALLOWED_SCOPE}" | grep -Fqx -- "$1"
}

is_forbidden_release_path()
{
    local path="$1"
    local name="${path##*/}"

    case "/${path}/" in
        */node_modules/*|*/dist/*|*/target/*|*/output/*)
            return 0
            ;;
    esac

    case "${name}" in
        *.log|*.pid|.env|.env.*|*.pem|*.key|*credentials*|*credential*|*secrets*|*secret*)
            return 0
            ;;
    esac

    return 1
}

cd "${ROOT_DIR}"
command -v git >/dev/null 2>&1 || fail 'git is required'
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail 'run this command inside the ERP repository'
git cat-file -e "${BASE_COMMIT}^{commit}" 2>/dev/null || fail "scope base commit is missing: ${BASE_COMMIT}"
git merge-base --is-ancestor "${BASE_COMMIT}" HEAD     || fail "HEAD is not descended from the approved scope base: ${BASE_COMMIT}"

while IFS= read -r path; do
    [[ -z "${path}" ]] || ALLOWED_FILES+=("${path}")
done <<< "${ALLOWED_SCOPE}"

while IFS= read -r path; do
    [[ -z "${path}" ]] || CHANGED_FILES+=("${path}")
done < <(
    {
        git -c core.quotePath=false diff --name-only "${BASE_COMMIT}"
        git -c core.quotePath=false ls-files --others --exclude-standard
    } | LC_ALL=C sort -u
)

info "scope base: ${BASE_COMMIT}"
info "allowed paths: ${#ALLOWED_FILES[@]}"
info "changed paths from base: ${#CHANGED_FILES[@]}"

for path in "${REQUIRED_FILES[@]}"; do
    [[ -f "${path}" ]] || fail "required release file is missing: ${path}"
    is_allowed "${path}" || fail "required release file is absent from the allowlist: ${path}"
done
pass "${#REQUIRED_FILES[@]} required release files are present"

for path in "${CHANGED_FILES[@]}"; do
    status="$(git -c core.quotePath=false status --short --untracked-files=all -- "${path}")"
    [[ -n "${status}" ]] || status="COMMITTED ${BASE_COMMIT}..HEAD"

    if is_allowed "${path}"; then
        printf '[IN SCOPE] %s :: %s\n' "${status}" "${path}"
    else
        printf '[OUT OF SCOPE] %s :: %s\n' "${status}" "${path}" >&2
        OUT_OF_SCOPE+=("${path}")
    fi

    if is_forbidden_release_path "${path}"; then
        printf '[FORBIDDEN] %s\n' "${path}" >&2
        FORBIDDEN_FILES+=("${path}")
    fi
done

(( ${#OUT_OF_SCOPE[@]} == 0 ))     || fail "release contains ${#OUT_OF_SCOPE[@]} path(s) outside the explicit allowlist"
(( ${#FORBIDDEN_FILES[@]} == 0 ))     || fail "release contains ${#FORBIDDEN_FILES[@]} generated, credential, log, or PID path(s)"
pass 'all changed paths are inside the explicit release allowlist'
pass 'no generated output, dependency tree, credential, log, or PID path is in release scope'

git diff --check "${BASE_COMMIT}" -- "${ALLOWED_FILES[@]}"     || fail 'tracked release changes contain whitespace errors'

for path in "${CHANGED_FILES[@]}"; do
    git ls-files --error-unmatch -- "${path}" >/dev/null 2>&1 && continue
    [[ -f "${path}" ]] || continue
    whitespace_output="$(git diff --no-index --check /dev/null "${path}" 2>&1 || true)"
    [[ -z "${whitespace_output}" ]] || {
        printf '%s\n' "${whitespace_output}" >&2
        fail "untracked release file contains whitespace errors: ${path}"
    }
done
pass 'git diff whitespace checks passed for tracked and untracked release files'

pass "unified-todo release scope is valid (${#CHANGED_FILES[@]} changed paths)"
