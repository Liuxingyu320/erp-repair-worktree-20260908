# 2026-07-16 合同签约 Release A 范围冻结

基线提交：b694659dd2c19dc87377d93eaed6bbbf9a5ac0b7
分支：codex/erp-product-optimization
捕获时间：2026-07-16T22:16:51+08:00
状态：G0-1 基线已冻结；后续任务只能暂存显式白名单文件

## 结论

原始签约候选脏文件共 179 个：修改 107、删除 6、未跟踪 66。A1 审查显式追加了历史菜单冲突精确指纹和发布决策台账；修订后冻结 inventory 仍为 181 个文件。A2—A6 完成后，Release A 源码白名单扩展为 213 个精确路径，另有 5 个受控的 Docker/System push/UAT 公共基础文件作为精确扩展；二者的并集才是 A7 发布文件门禁边界。

删除项的 SHA-256 取自 HEAD:<path>，修改/未跟踪项取自当前工作区。这样既不会把删除误记为空文件，也不会把未跟踪的公司印章组件、签约工具函数、测试或迁移误判为不存在。

Release A 只允许 OA/System 必需类、签约前端路径、成对 SQL、验证脚本与发布/运维文档。自动发送、旧合同统一中心、截图、草案生成工具和单边历史迁移不进入本次发布。

## 旧测试副本审查

erp-ui/test/newBusinessMigrationRelease.test 2.js 受 .gitignore 的 * 2.* 规则忽略；正式文件 erp-ui/test/newBusinessMigrationRelease.test.js 当前为未跟踪文件。逐断言对照结论是：旧文件的 8 组断言全部已被正式文件保留或加强，正式文件还新增签名 manifest、SHA-256、依赖闭包、危险迁移排除、统一审批 seed 和总门禁顺序检查。

因此旧副本没有独有的有效断言需要回填。完成 G0-1 内容审查后，执行批次已删除该被忽略副本，项目全量测试只运行正式文件。旧/正式文件哈希和逐组映射见下方机读块，作为删除依据保留。

## 发布边界

- 白名单是精确文件列表，不接受目录或 glob 放宽。
- 所有 sql/erp_oa_sign_*.sql 必须同时存在 docker/mysql/db/ 同名副本；两份同时不存在表示后续计划文件，只有一份存在或字节不一致即失败。
- 制品、备份、员工数据、签名/印章图片、上传目录、数据库 dump 和测试凭据由 denylist 阻断。
- 三份仅存在于 sql/ 的旧脚本被记录为 HOLD_UNPAIRED_MIGRATION，不得进入 Release A。
- OaSignAutomationSettings 延后至 Release B；移动旧合同与劳动合同旧入口延后至 Release C。
- SysUserServiceImpl 中签约权限与系统管理改动混杂，已记录但不在 Release A 白名单，必须拆分或逐行复核后重新冻结。
- 后续暂存后运行 python3 scripts/contract/verify_contract_signing_scope.py --mode staged；白名单/双份迁移静态复验使用 --mode static；精确状态与哈希复验使用默认 --mode baseline。

## A2—A7 精确扩展

G0 冻结后新增的截止/拒签/过期、文件策略、提醒 Outbox、移动推送幂等账、UAT 和 A7 门禁文件不改写原始 inventory；它们以 `releaseAAllowlist` 追加项和 `releaseAExactExtensions` 记录。`releaseAExactExtensions` 只允许下面 5 个精确路径，不是目录或 glob 放宽。

- System push 迁移必须与 OA 三个 Release A 迁移一起受 A7 manifest 哈希和双份一致性校验。
- `docker/mysql/bootstrap-files.list` 和 `scripts/verify-docker-mysql-bootstrap.sh` 只保障新建环境的显式 bootstrap；它们不得代替生产维护窗口迁移账本。
- 原 `scripts/contract/verify_contract_signing_scope.py --mode staged` 仍只识别 G0 核心白名单；A7 暂存/发布必须再运行 `scripts/verify-contract-signing-release.sh --static`，由后者对精确扩展失败关闭。

## 机读范围

下面 JSON 是唯一机器真源。inventory 每项同时记录路径、Git 状态、SHA-256、哈希来源、阶段和 Release A 决策。

<!-- CONTRACT_SIGNING_SCOPE_JSON_BEGIN -->
{
  "schemaVersion": 1,
  "releaseId": "contract-signing-release-a-20260716",
  "baseline": {
    "commit": "b694659dd2c19dc87377d93eaed6bbbf9a5ac0b7",
    "branch": "codex/erp-product-optimization",
    "capturedAt": "2026-07-16T22:16:51+08:00"
  },
  "manifestControlFile": "docs/releases/20260716-contract-signing-release-scope.md",
  "controlFiles": [
    "docs/releases/20260716-contract-signing-release-scope.md",
    "scripts/contract/menu_id_conflicts_20260716.json",
    "scripts/contract/test_verify_contract_signing_scope.py",
    "scripts/contract/test_verify_menu_id_ownership.py",
    "scripts/contract/verify_contract_signing_scope.py",
    "scripts/contract/verify_menu_id_ownership.py"
  ],
  "controlFileHashes": {
    "scripts/contract/menu_id_conflicts_20260716.json": "0c3aa0313a358f83b2fa433d64555ac1947eb653becf4565d3e7181d9b5e59b6",
    "scripts/contract/test_verify_contract_signing_scope.py": "a0e997aefb8447b81c423697e0f42ea0c7b5db18eddd6ec9eb810de9db5d226d",
    "scripts/contract/test_verify_menu_id_ownership.py": "27fed027781576e61cf5fc5cd6c714e474c6bc831abc989843ff3e83731dc602",
    "scripts/contract/verify_contract_signing_scope.py": "7a9a7614656f28ec0e43b7bdd18049c7f1a7ec9c4621b45d0268336d81715b16",
    "scripts/contract/verify_menu_id_ownership.py": "fd6f359f624cdbbcdf57c7117e7239925409de91c2a91652f6496f439fbaec92"
  },
  "releaseAAllowlist": [
    "docker/mysql/db/erp_oa_sign_document_policy_snapshot_20260716.sql",
    "docker/mysql/db/erp_oa_sign_final_confirmation_20260714.sql",
    "docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql",
    "docker/mysql/db/erp_oa_sign_package_lifecycle_20260716.sql",
    "docker/mysql/db/erp_oa_sign_package_renewal_snapshot_20260714.sql",
    "docker/mysql/db/erp_oa_sign_template_scenarios_20260714.sql",
    "docs/decisions/2026-07-16-contract-signing-release-decisions.md",
    "docs/releases/20260716-contract-signing-release-manifest.md",
    "docs/releases/20260716-contract-signing-release-scope.md",
    "docs/releases/contract-signing-release-checklist.md",
    "docs/releases/contract-signing-uat-runbook.md",
    "docs/runbooks/contract-signing-operations.md",
    "erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java",
    "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java",
    "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignFileEvidenceType.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignPackageStatus.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignScenarioCodes.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTaskStatus.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaCompanySealConfig.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFinalConfirmation.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFinalConfirmationDocument.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTemplate.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchCreateDraftsRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentHashRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentReadRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignExceptionResolutionRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignFinalConfirmRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageFinalizeRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageRefuseRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageSignRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskConfirmRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskNotificationRetryRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskRetryRequest.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignCompanyOptions.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignReminderCandidate.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignTaskDetail.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaCompanySealConfigMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignFinalConfirmationMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaHrRenewalGuardMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignNotificationOutboxMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTaskService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaCurrentUserTaskFinder.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignCompanyService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignHrAccessService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationDispatcher.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageExpiryScheduler.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageLifecycleService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlacementPolicyService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignReminderScheduler.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignResponseSanitizer.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskEventService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskWorkflowService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTemplateServiceImpl.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignVerificationService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java",
    "erp-modules/erp-oa/src/main/resources/bootstrap.yml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaCompanySealConfigMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaHrRenewalGuardMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignFinalConfirmationMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignNotificationOutboxMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignScenarioCodesTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignEvidenceMigrationTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMetricsMapperTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/OaSignTaskStateMachineTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignCompanyServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFileStorageServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFinalConfirmationFlowTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationDispatcherTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationOutboxServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageExpirySchedulerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageLifecycleServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackagePreflightValidatorTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlacementPolicyServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignReminderSchedulerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskWorkflowServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTemplateServiceImplTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLegalEntityController.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserPushDelivery.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserPushDeliveryMapper.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysLegalEntityMapper.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysLegalEntityServiceImpl.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserNotificationServiceImpl.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/push/PushDeliveryClient.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java",
    "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml",
    "erp-modules/erp-system/src/main/resources/mapper/system/SysLegalEntityMapper.xml",
    "erp-modules/erp-system/src/main/resources/mapper/system/SysUserPushDeliveryMapper.xml",
    "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml",
    "erp-modules/erp-system/src/test/java/com/erp/system/controller/SysLegalEntityControllerTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferLifecycleTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRegularizationLifecycleTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57IT.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserNotificationServiceImplTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/push/ApnsPushDeliveryClientTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/push/FirebasePushDeliveryClientTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysConfigServiceSignHrPermissionTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserSignHrLifecycleTest.java",
    "erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeFieldRegistryTest.java",
    "erp-ui/src/api/oa/signPackage.js",
    "erp-ui/src/api/oa/signTask.js",
    "erp-ui/src/utils/signDictionary.js",
    "erp-ui/src/utils/signDisplayText.js",
    "erp-ui/src/utils/signPackageFileGate.js",
    "erp-ui/src/utils/signPlaceholder.js",
    "erp-ui/src/utils/signScenario.js",
    "erp-ui/src/utils/signTemplateType.js",
    "erp-ui/src/utils/todoMutationMatcher.js",
    "erp-ui/src/utils/todoRouteResolver.js",
    "erp-ui/src/views/mobile/signPackage/index.vue",
    "erp-ui/src/views/oa/signPackage/CompanySealManagement.vue",
    "erp-ui/src/views/oa/signPackage/SignPackageExceptionPanel.vue",
    "erp-ui/src/views/oa/signPackage/SignPackageRecordPanel.vue",
    "erp-ui/src/views/oa/signPackage/index.vue",
    "erp-ui/src/views/oa/signTask/SignTaskConfirmDialog.vue",
    "erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue",
    "erp-ui/src/views/oa/signTask/index.vue",
    "erp-ui/test/contractUsabilityAudit.test.js",
    "erp-ui/test/signCompanySealManagement.test.js",
    "erp-ui/test/signDeadlineUx.test.js",
    "erp-ui/test/signManualClosure.test.js",
    "erp-ui/test/signPackageEvidenceUx.test.js",
    "erp-ui/test/signPackageFileGate.test.js",
    "erp-ui/test/signPackageModule.test.js",
    "erp-ui/test/signPackageRefusal.test.js",
    "erp-ui/test/signPackageRequestFailure.test.js",
    "erp-ui/test/signTaskCenter.test.js",
    "erp-ui/test/signTaskExceptionResolution.test.js",
    "erp-ui/test/unifiedTodoMutationRefresh.test.js",
    "erp-ui/test/unifiedTodoRouteResolver.test.js",
    "scripts/contract-signing-migrations-20260716.list",
    "scripts/contract-signing-release-20260716.json",
    "scripts/contract-signing-release-files-20260716.list",
    "scripts/contract/menu_id_conflicts_20260716.json",
    "scripts/contract/test_verify_contract_signing_scope.py",
    "scripts/contract/test_verify_menu_id_ownership.py",
    "scripts/contract/verify_contract_signing_scope.py",
    "scripts/contract/verify_menu_id_ownership.py",
    "scripts/qa/contract-signing-uat.example.json",
    "scripts/qa/prepare_contract_signing_uat.py",
    "scripts/qa/run_contract_signing_api_uat.py",
    "scripts/qa/verify_contract_signing_evidence.py",
    "scripts/test_contract_signing_release_contract.py",
    "scripts/test_contract_signing_uat.py",
    "scripts/verify-contract-signing-release.sh",
    "sql/erp_oa_sign_document_policy_snapshot_20260716.sql",
    "sql/erp_oa_sign_final_confirmation_20260714.sql",
    "sql/erp_oa_sign_menu_permission_repair_20260716.sql",
    "sql/erp_oa_sign_package_lifecycle_20260716.sql",
    "sql/erp_oa_sign_package_renewal_snapshot_20260714.sql",
    "sql/erp_oa_sign_template_scenarios_20260714.sql"
  ],
  "releaseAExactExtensions": [
    "docker/mysql/bootstrap-files.list",
    "docker/mysql/db/erp_system_user_push_delivery_20260717.sql",
    "scripts/qa/contract_signing_uat_common.py",
    "scripts/verify-docker-mysql-bootstrap.sh",
    "sql/erp_system_user_push_delivery_20260717.sql"
  ],
  "denylist": [
    {
      "category": "artifacts",
      "pattern": "*.jar",
      "reason": "编译或部署 JAR 不是源码发布范围"
    },
    {
      "category": "artifacts",
      "pattern": "*.class",
      "reason": "编译中间产物"
    },
    {
      "category": "artifacts",
      "pattern": "*.pyc",
      "reason": "Python 字节码"
    },
    {
      "category": "artifacts",
      "pattern": "*/target/*",
      "reason": "Maven 构建目录"
    },
    {
      "category": "artifacts",
      "pattern": "*/dist/*",
      "reason": "前端构建目录"
    },
    {
      "category": "artifacts",
      "pattern": "*/node_modules/*",
      "reason": "前端依赖目录"
    },
    {
      "category": "artifacts",
      "pattern": "*.tar.gz",
      "reason": "归档制品"
    },
    {
      "category": "artifacts",
      "pattern": "*.zip",
      "reason": "归档制品"
    },
    {
      "category": "backups",
      "pattern": "* 2.*",
      "reason": "重复后缀或 Finder 副本"
    },
    {
      "category": "backups",
      "pattern": "*.bak",
      "reason": "编辑器备份"
    },
    {
      "category": "backups",
      "pattern": "*.orig",
      "reason": "补丁备份"
    },
    {
      "category": "backups",
      "pattern": "*backup*",
      "reason": "备份文件或备份目录"
    },
    {
      "category": "database-dumps",
      "pattern": "*dump*.sql",
      "reason": "数据库 dump"
    },
    {
      "category": "database-dumps",
      "pattern": "*.sql.gz",
      "reason": "压缩数据库 dump"
    },
    {
      "category": "database-dumps",
      "pattern": "*.dump",
      "reason": "数据库 dump"
    },
    {
      "category": "database-dumps",
      "pattern": "docker/mysql/seed/*.sql",
      "reason": "本地种子数据库可能含业务数据"
    },
    {
      "category": "employee-data",
      "pattern": "*employee-data*",
      "reason": "员工数据导出或导入包"
    },
    {
      "category": "employee-data",
      "pattern": "*employee_data*",
      "reason": "员工数据导出或导入包"
    },
    {
      "category": "employee-data",
      "pattern": "*员工数据*",
      "reason": "员工数据文件"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "*.png",
      "reason": "截图、签名或印章位图"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "*.jpg",
      "reason": "截图、签名或印章位图"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "*.jpeg",
      "reason": "截图、签名或印章位图"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "*.webp",
      "reason": "截图、签名或印章位图"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "uploadPath/*",
      "reason": "运行时上传目录"
    },
    {
      "category": "signatures-and-uploads",
      "pattern": "*/uploadPath/*",
      "reason": "运行时上传目录"
    },
    {
      "category": "test-credentials",
      "pattern": ".env",
      "reason": "本地环境凭据"
    },
    {
      "category": "test-credentials",
      "pattern": ".env.*",
      "reason": "本地环境凭据"
    },
    {
      "category": "test-credentials",
      "pattern": "*/.env",
      "reason": "本地环境凭据"
    },
    {
      "category": "test-credentials",
      "pattern": "*/.env.*",
      "reason": "本地环境凭据"
    },
    {
      "category": "test-credentials",
      "pattern": "*.auth.json",
      "reason": "浏览器或 API 测试认证信息"
    },
    {
      "category": "test-credentials",
      "pattern": "*.local.json",
      "reason": "本地测试配置"
    },
    {
      "category": "test-credentials",
      "pattern": "*credential*.json",
      "reason": "凭据文件"
    }
  ],
  "discovery": {
    "pathRegexes": [
      "(?:sql|docker/mysql/db)/erp_oa_sign_[^/]+\\.sql",
      "erp-api/erp-api-oa/.*/[^/]*(?:Sign|Signing)[^/]*\\.java",
      "erp-modules/erp-oa/.*/(?:OaSign|OaCompanySeal)[^/]*\\.(?:java|xml)",
      "erp-modules/erp-system/.*/(?:HrSign|SysLegalEntity)[^/]*\\.(?:java|xml)",
      "erp-ui/src/api/oa/sign[^/]*\\.js",
      "erp-ui/src/utils/sign[^/]*\\.js",
      "erp-ui/src/views/(?:mobile/contract|mobile/signPackage|oa/laborContract|oa/signPackage|oa/signTask)/.*",
      "erp-ui/test/(?:sign[^/]*|contractUsabilityAudit)\\.test\\.js",
      "scripts/contract/.*",
      "docs/audit-screenshots/contracts-[^/]+/.*",
      "docs/(?:verification|superpowers/plans)/[^/]*contract[^/]*\\.md",
      "docs/[^/]*合同[^/]*\\.md"
    ],
    "extraPaths": [
      "docs/decisions/2026-07-16-contract-signing-release-decisions.md",
      "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java",
      "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java",
      "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java",
      "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java",
      "erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java",
      "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaCurrentUserTaskFinder.java",
      "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java",
      "erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml",
      "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java",
      "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java",
      "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java",
      "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java",
      "erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java",
      "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml",
      "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferLifecycleTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRegularizationLifecycleTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysConfigServiceSignHrPermissionTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserSignHrLifecycleTest.java",
      "erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeFieldRegistryTest.java",
      "erp-ui/src/utils/todoMutationMatcher.js",
      "erp-ui/src/utils/todoRouteResolver.js",
      "erp-ui/test/unifiedTodoMutationRefresh.test.js",
      "erp-ui/test/unifiedTodoRouteResolver.test.js"
    ]
  },
  "inventory": [
    {
      "path": "docker/mysql/db/erp_oa_sign_final_confirmation_20260714.sql",
      "status": "U",
      "sha256": "c1209b88f37371cbc8a7b32c7ecf1cdb0997712b143eff192218d94cd55746e1",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql",
      "status": "U",
      "sha256": "5a9c37f42360851e98f122c624415fff38e63a51bf072ac2f65bb25d22b4cd7a",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "docker/mysql/db/erp_oa_sign_package_renewal_snapshot_20260714.sql",
      "status": "U",
      "sha256": "206e7251bcb3e848ef6e414d79e223f0b34474aef8e6183e4943576e10b1536a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "docker/mysql/db/erp_oa_sign_template_scenarios_20260714.sql",
      "status": "U",
      "sha256": "1fd6a0c45d9fdcf3e7e29be4fb0548bb3a545a4ce84d8c24f08164443cf8f62a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/01-login.png",
      "status": "U",
      "sha256": "1f57ce4f3277d539677692a91f8b95d647ff44f6e92380a8649c59762ad9af11",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/02-select-organization.png",
      "status": "U",
      "sha256": "fd1cfd3fba0521aaaaf4753d857a77f3869981e507bc506aa37ab06d975330cb",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/03-dashboard.png",
      "status": "U",
      "sha256": "8947141ab3f014f081feae8bc23c842a6dab6c6f9a03b9eed42b88111aa3c6b1",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/04-sign-task-center.png",
      "status": "U",
      "sha256": "c2dc2dd0dfc65d7f32bd7cbb3683b62eb987a59d446b916461f43bd2b12af2ec",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/05-sign-package-list.png",
      "status": "U",
      "sha256": "5e0fda71776d6240e5a81d55a05ead6f33369fae841028ebd6085b483e86cca3",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/06-sign-plan.png",
      "status": "U",
      "sha256": "7d1023e3dceffa7f97ac20816acce17bf20ff3642d562a86e512be750fe65fd6",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/07-plan-renewal-scenario.png",
      "status": "U",
      "sha256": "00a62f5d6bf9e62dd2a2b18a48a326c581539fb2905f1d519014bd8aa08d65b5",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/08-template-management.png",
      "status": "U",
      "sha256": "dfc73ad0ce1452b90a6a6837a34b1771ca9fb8ec297aa542c38f9dadcda5c290",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/09-contract-verification.png",
      "status": "U",
      "sha256": "2bb6f77591166e3ca88a637157418e3849fcca4adac0fe1fcdb2e99051165cf7",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/10-mobile-sign-package.png",
      "status": "U",
      "sha256": "dd056af06f06091fdf8a53bd03f67d63e1b0d577dd5b1fa9f4fc867bb7a59c7f",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/11-mobile-contract-list.png",
      "status": "U",
      "sha256": "ae961070d74dad7117ed267c82096694db55fd5571afe8409cc7b88574d8528e",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/12-mobile-contract-detail.png",
      "status": "U",
      "sha256": "2d2e847c13ff6705cf368432f7508774f36a6069322846fe411039fe9b55a1cb",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/13-mobile-word-fallback-fixed.png",
      "status": "U",
      "sha256": "2a9d5df1a8198445d3d0d007748427673b7a5fedebd09e4519bd96d897a6e25c",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/14-renewal-fields-fixed.png",
      "status": "U",
      "sha256": "ce95355c39742e0eeb6c00a1c2529fe7eaa57d79e055ee3631854c0ae8be373b",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/15-contract-verification-fixed.png",
      "status": "U",
      "sha256": "23f83a4e70c1cdf0e1417f55f25dab2c159c59d73148bed5b817d141a69ae585",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/audit-screenshots/contracts-20260713/16-sign-record-guide-fixed.png",
      "status": "U",
      "sha256": "967becbd85ed12de315733d213bd20e13f20f221bcad7b9ab3e673e30d3b13f4",
      "hashSource": "WORKTREE",
      "stage": "EXCLUDED_EVIDENCE",
      "releaseA": false,
      "decision": "浏览器截图属于审计证据，不进入生产发布包"
    },
    {
      "path": "docs/decisions/2026-07-16-contract-signing-release-decisions.md",
      "status": "U",
      "sha256": "0c3de0080fde02349efc2538b304aa13f6b4c8ac66c43104aebc58f8cc9e6d1a",
      "hashSource": "WORKTREE",
      "stage": "G0",
      "releaseA": true,
      "decision": "生产发布决策与未决阻断项的保守台账"
    },
    {
      "path": "docs/superpowers/plans/2026-07-16-contract-signing-production-closure.md",
      "status": "U",
      "sha256": "2d3e4b1d2c7cac4b0d572735c11792281e6c0342dc272679a6f9dd8e711480cf",
      "hashSource": "WORKTREE",
      "stage": "G0_CONTEXT",
      "releaseA": false,
      "decision": "计划/历史验证上下文，不作为 Release A 运行制品"
    },
    {
      "path": "docs/verification/2026-07-14-contract-next-stage-baseline.md",
      "status": "U",
      "sha256": "c28064fa679ee4d1b8175241eb42d70826d79d3db849b298ce5cc27c4c3935b8",
      "hashSource": "WORKTREE",
      "stage": "G0_CONTEXT",
      "releaseA": false,
      "decision": "计划/历史验证上下文，不作为 Release A 运行制品"
    },
    {
      "path": "docs/verification/2026-07-14-contract-stage1-implementation.md",
      "status": "U",
      "sha256": "bdc65fd7538fbfd3b608baa98cf60458a68c6db2fb956468e3fdb31ee839b957",
      "hashSource": "WORKTREE",
      "stage": "G0_CONTEXT",
      "releaseA": false,
      "decision": "计划/历史验证上下文，不作为 Release A 运行制品"
    },
    {
      "path": "docs/合同功能检查与优化方案-20260713.md",
      "status": "U",
      "sha256": "b372efdd60226bc289a657a0516c760f6b699c192f812baca4ab8fcd0ab0e853",
      "hashSource": "WORKTREE",
      "stage": "G0_CONTEXT",
      "releaseA": false,
      "decision": "计划/历史验证上下文，不作为 Release A 运行制品"
    },
    {
      "path": "erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java",
      "status": "M",
      "sha256": "e72d481f6af2070019d954642ac1a111ab7b3cd33c0c6f9e335f0133a9627c65",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java",
      "status": "M",
      "sha256": "9cc43b88a39fcaf6156da909f807c5ef5ac745d9c8879c2e80d0e3e185f1af5d",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约员工快照依赖"
    },
    {
      "path": "erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java",
      "status": "M",
      "sha256": "37d0e9f790d709157099c8ccfc92521fb6f33001a3079d748bffc00703ad4277",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约档案字段依赖"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignFileEvidenceType.java",
      "status": "M",
      "sha256": "d5311066b3c2f4c5485948163ab0fad2d7940e306046e1d467c6d624bdad0b79",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignPackageStatus.java",
      "status": "M",
      "sha256": "c24f1a7ddbb027d323b4bc7b910eba44e6b6843830e098f46987f2d31b6d700b",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignScenarioCodes.java",
      "status": "U",
      "sha256": "bda119c206f4c51c7d21ce9107f68c4a3d7f15e972b935c8d6935ce347bc2078",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTaskStatus.java",
      "status": "M",
      "sha256": "7ce82c4b6cdec554aff54539158c9a230f9a39bd5585d9874def172cbd49de6c",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java",
      "status": "M",
      "sha256": "12bf79fb74fea7305900b9ccabddc3469f57f41e850f229d2780d89c01e7b03b",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java",
      "status": "M",
      "sha256": "84c903249e231bbdc4d943928ef739f7c5508fc22cc4a3b1722076db02298a54",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "签约待办类型共享类"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java",
      "status": "M",
      "sha256": "e64a15c31952bc8181df424b21046a0310a42d9224c0c28ca99abf15238453e3",
      "hashSource": "WORKTREE",
      "stage": "A2/A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java",
      "status": "M",
      "sha256": "10e9c533b78a5618de09797b3cc6596a3920ed7906b56986b2b8f4be87496b04",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaCompanySealConfig.java",
      "status": "M",
      "sha256": "1064e52590589c83cbd1e23e6f80bf593d358344b19070139df8a914144f9514",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFinalConfirmation.java",
      "status": "U",
      "sha256": "38e5ef4c9f0fdb21f5e1cc2d9395cdc4be9529a9c3068443eed781e8d108d827",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignFinalConfirmationDocument.java",
      "status": "U",
      "sha256": "4afc51d6fc13c4643a2b7bc95b3b09cdaa5a518776435cc8b3351f170869a062",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java",
      "status": "M",
      "sha256": "aa2b639550927b3c75f0617c13b12e4208868cd5212b5d297e30801375d7573c",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java",
      "status": "M",
      "sha256": "49979affa9523ea4853c2fddaaa457453c6cd3477bbe9e8e8c7f617e4452a63a",
      "hashSource": "WORKTREE",
      "stage": "A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java",
      "status": "M",
      "sha256": "c16e50ee57be7ff841afb1eb43d723fd69ceeb76970380138dfd6fa78d369ed6",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java",
      "status": "M",
      "sha256": "b184ac34da1bcb20bdfa348e132c091d874aa6607f41d371574931ac8722b839",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTemplate.java",
      "status": "M",
      "sha256": "4f229bc194fc77c16e8f305b94f34d7c3a1a85fc3af3bff89db34536dc2b9edd",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignBatchPreviewRow.java",
      "status": "M",
      "sha256": "8464a0371d7f1e810e25534b1ea914efbc14ada77c6b75afe64785d55e3d9dbf",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentHashRequest.java",
      "status": "M",
      "sha256": "b57a066dd059e1860a3800d41754ede4b4fe9b295dce4f9437c9fc1731d3e688",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignDocumentReadRequest.java",
      "status": "M",
      "sha256": "b34bbf84891627ea318e560a87a1d73bc5861749db212429ec8d97a76ceb8cc5",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignFinalConfirmRequest.java",
      "status": "U",
      "sha256": "d5b4ca6dc7046b6253ed1957561fbe045b2cc5a0270f089c6a7807501f1120ba",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageFinalizeRequest.java",
      "status": "U",
      "sha256": "f513ac88f6e2de19f96f8396fa68e7f26cac13edf048077411d426c6085f53cc",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageSignRequest.java",
      "status": "M",
      "sha256": "204d0d93312574b05d0d059367750c4312c839ce7fc47487326a374de03f5b47",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskConfirmRequest.java",
      "status": "M",
      "sha256": "0640acd14f8a9a6860240c2b47bca2f89a5c6a0c045b4d49a8b9a4c7c6824bd8",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskNotificationRetryRequest.java",
      "status": "M",
      "sha256": "34dd08d162960e164f1f6cb5ac9bd58cd74d9c4bab593a0093c832e736f9b1ce",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskRetryRequest.java",
      "status": "M",
      "sha256": "9cdc3b05d16e2c77b2a517005beff787502dfdced2007c651e10969081a55f02",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignCompanyOptions.java",
      "status": "U",
      "sha256": "01a624376375d62f9d21c6a9cc99fb231d6d189dcd6c53a433ce3e583f979f91",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaCompanySealConfigMapper.java",
      "status": "M",
      "sha256": "6f9287fda58b64459eac80901134d8216b9b4e4f8e0246c10fc3da5677fac20f",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignFinalConfirmationMapper.java",
      "status": "U",
      "sha256": "8a9203e8c1d1352c828e5a6d786a57e8b4e31880f1e1aade3518abad3ded782c",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java",
      "status": "M",
      "sha256": "9545cc1fc410cde366645ed21afa1bae83183c9aa91e06bb9ef6339bb95816b2",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java",
      "status": "M",
      "sha256": "7d0cd0724d559a899baf85617d67041ca39ce52e350d259dfec4d323f107e3d7",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "签约待办查询共享 Mapper"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java",
      "status": "M",
      "sha256": "5eff748ea6a64901363689065e487bfc754a9df60b22f8381d54b61a3377487d",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java",
      "status": "M",
      "sha256": "2f167b1762836fc843a1d492df09fcc7f5c92ef08d63e0b56bdc665e5dc12ec6",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaCurrentUserTaskFinder.java",
      "status": "D",
      "sha256": "247383e48f950e7d9dd766af617d0cf718cd98af83079c56bedccc1a780ce1e6",
      "hashSource": "HEAD",
      "stage": "A4",
      "releaseA": true,
      "decision": "签约任务查找器已删除并由统一待办替代"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationSettingsService.java",
      "status": "M",
      "sha256": "18bf9e3183f983f53633ab18253e5111b01b5bc54c673daaf8c71e9498d2668f",
      "hashSource": "WORKTREE",
      "stage": "B",
      "releaseA": false,
      "decision": "自动发送属于 Release B，Release A 保持关闭"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java",
      "status": "M",
      "sha256": "e6849989036e435c2de381ee5d4f5846b12780a172390ae945ab8060080b3597",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignCompanyService.java",
      "status": "U",
      "sha256": "8ce884509d5ce51b8d49b69cac408e06f3cb3414261c15bd871d68609d52fb22",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java",
      "status": "M",
      "sha256": "17151b0d4654386b1842e0abd405a2272ac09e003485464831a2b450f6814792",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignFileStorageService.java",
      "status": "M",
      "sha256": "7f0fa075a79114dff949b65459d92e27432bae4bc20269a4f728f22adc6786ef",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignHrAccessService.java",
      "status": "M",
      "sha256": "a3b844ba2190ff2b23b3bfb6eeb886becc92dcbf6b84dcf03454581ceaf8f0f4",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java",
      "status": "U",
      "sha256": "b09b0caf52c29acc8ff18a3c64098aea94d9a45ba928e9bf1a98b26f7bca5ff7",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java",
      "status": "M",
      "sha256": "92cf14c286c16c8fed2ffd7b14137966be94fca6095fe82ad7138819dda6304c",
      "hashSource": "WORKTREE",
      "stage": "A3/A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanServiceImpl.java",
      "status": "M",
      "sha256": "e5da5cc8720e2236f479f71f93cfbb6a724173dd31050fb444055c8b2433b101",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java",
      "status": "M",
      "sha256": "a891eb837058887fb300e2ce3c8e543aa246dc866f78bb7cde929962ea05d22a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignResponseSanitizer.java",
      "status": "M",
      "sha256": "a1c9bddddb51c5c4818bb724795b72ce5ea2402020301443be41f2f36da8e7da",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskEventService.java",
      "status": "M",
      "sha256": "a9f9aaa94efddfb8865461b25258d49612f89b1e884a67e54e3825bbc34ef29a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java",
      "status": "M",
      "sha256": "91e79b6d3cc143ff3408585b9cb2203a591ff2c01f3ffd22470f8ea998c406a9",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java",
      "status": "M",
      "sha256": "8804942601898687eed5df5f402068ba6c97bbfcf04ae8ca123c1b84f993ec88",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskWorkflowService.java",
      "status": "M",
      "sha256": "925f808867d7f34d6580e9d4a18750b5bfa8cb26d38dd068e51f56c0b62b7f47",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTemplateServiceImpl.java",
      "status": "M",
      "sha256": "5884f2edc97477feae3dbddc035843818d444e959c1edcb1691b12d112e3ff77",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java",
      "status": "M",
      "sha256": "49b8b3443524bc95ae629bc2e0fe73d1bfc9a46056fb78b27d0c6c05723f4ee3",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignVerificationService.java",
      "status": "M",
      "sha256": "57511abbd87df1a4c3b9b4a2cba214135c0c0bfccb33462df97dcf434527b603",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java",
      "status": "M",
      "sha256": "8f702cae0be397caecd22f4c40c922a35c48936de9cb47addad0cbe6129d1f1d",
      "hashSource": "WORKTREE",
      "stage": "A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java",
      "status": "M",
      "sha256": "d64f4c5e5d718f5709de2c991c83f36ea1a85971fc7da8cbe6f324f3766f670a",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaCompanySealConfigMapper.xml",
      "status": "M",
      "sha256": "7f40cc078eb6c1e936b1433ec5c03f46a71461afe2b0bc2107c0df213616f4b5",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignFinalConfirmationMapper.xml",
      "status": "U",
      "sha256": "6ec903b9d92b12b25ccb5ff968f95f16d535a3aea3331c5d7fc33729aa25a28a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml",
      "status": "M",
      "sha256": "4882b30153376bfc4b47c0657c050ae0afbda4a5c1c8d3579cab6ed33d7d2ca9",
      "hashSource": "WORKTREE",
      "stage": "A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml",
      "status": "M",
      "sha256": "e6912c92dcd3d782bad3c5caabf4dc2f61cf4955d3831c4c12924ee816dea1ff",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanMapper.xml",
      "status": "M",
      "sha256": "55beea47d471a89bd2411975dc82224e4a40b0fe0f32ba43c0fe7ffa457d1253",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml",
      "status": "M",
      "sha256": "d8b3ae0a7d7efca70bf07adc45fd44ed17a979817b167829d2182f545df4526f",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml",
      "status": "M",
      "sha256": "614c672b07772acc7a02cd515697067e21474b291755da74540bf0ebf3ef51fe",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTemplateMapper.xml",
      "status": "M",
      "sha256": "55b95ed56dbf9a476a474f672b3715cb3ee89b6eaa457de71bf0b52cdf52571b",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml",
      "status": "M",
      "sha256": "61198dd75ef94a9ce02c4f2f30ef626cae2138d725aeb8f06795fc454d7fccc2",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "签约待办 SQL"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignScenarioCodesTest.java",
      "status": "U",
      "sha256": "6b66a900b5276971d383e6265f3f73bb4656d4c9627ab73fbe2adffd0d43bcb1",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java",
      "status": "M",
      "sha256": "dac403402c4cbca9f790454f3f1e733e2328edbca7bbf84ae012d93650315e80",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaIdempotentSubmitAnnotationTest.java",
      "status": "M",
      "sha256": "06764e51ab3d501167c04b2cdb2e0d9efbe2cfa230f9260879eb5bfd0baf8729",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "签约变更接口幂等契约"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java",
      "status": "M",
      "sha256": "dcb044aecd122cda96f01cf99fede9233fddcc22f1180681a044ccfc8e95a3f6",
      "hashSource": "WORKTREE",
      "stage": "A2",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java",
      "status": "M",
      "sha256": "ffafcc7001dc1d8c3ac25978ce7496b14c014e6659700594c1e6aa34a50a8541",
      "hashSource": "WORKTREE",
      "stage": "A1/A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignEvidenceMigrationTest.java",
      "status": "M",
      "sha256": "abd584b47704801b68337c49ae74865c0782a678a63e17a20a7db7d259d5ca35",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMetricsMapperTest.java",
      "status": "M",
      "sha256": "7ca5853d49e2d2326e95c1bda774d1dff726ee3d04526cb46ba1f7a25ad4ec33",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java",
      "status": "M",
      "sha256": "ccebf2a82a40d71e3b6edff1bbe1e8c85d388d6cf39f5428876a6ea089d5f7a6",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java",
      "status": "M",
      "sha256": "b5a8a27fbd6c9dfdca3377cb5601f965b9a56763f57244c05e6f350efcee463a",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "签约待办映射测试"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/OaSignTaskStateMachineTest.java",
      "status": "M",
      "sha256": "cbdc8b068a84cef4f820cdd8fce6b0c91c1a9cbc1ea5d3f3edd65f29d80175f2",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignAutomationSettingsServiceTest.java",
      "status": "M",
      "sha256": "c633d1d2be1245b3d6e37f2b37bd3c2e2220e6828146e3508b9b7774600ae7d6",
      "hashSource": "WORKTREE",
      "stage": "B",
      "releaseA": false,
      "decision": "自动发送属于 Release B，Release A 保持关闭"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignBatchServiceImplTest.java",
      "status": "M",
      "sha256": "7fdc07f55bde68a244ef3697e3f14cddc82ce574025857d971668a98ab3b0640",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignCompanyServiceTest.java",
      "status": "U",
      "sha256": "9bd66362d3986a9d00049613d548d1325d2694aab6c03c0b20f5ce8a247561da",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java",
      "status": "M",
      "sha256": "e029001e3451ea068a127c668ae5f41812e0a1ddedb0164ba8d7c18fb305a5a8",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFileStorageServiceTest.java",
      "status": "M",
      "sha256": "6d742baf62208b5dba7aa8948a1e11b9f2eda8c1d28e4c940d254f06268277d5",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignFinalConfirmationFlowTest.java",
      "status": "U",
      "sha256": "b6b298c0090965ceb64064247e4c2d863067b3deb4093d7dcb020b8e13e6a0f0",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackagePreflightValidatorTest.java",
      "status": "U",
      "sha256": "c8c47cf6bedee3d43f567a9ef3ef55221038082178cca1dc0f201320f36fdc07",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java",
      "status": "M",
      "sha256": "ea6d5b248a7ea646e0eebaedee78d17e915417c94e74cecdea4142df8fa44568",
      "hashSource": "WORKTREE",
      "stage": "A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanServiceImplTest.java",
      "status": "M",
      "sha256": "0153c73b6d1d8722d7e9706af1e0483f115dc5ffb05de5067504838196fffc82",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImplTest.java",
      "status": "M",
      "sha256": "0baefea6cbaf22c3db1f7dae6ead8fbf723c9eb600a4458021a1fd65319722d6",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java",
      "status": "M",
      "sha256": "75f9a4148a119c86cf52040a26814284031ad521d0f0584e5c569dc1006dee9d",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskServiceImplTest.java",
      "status": "M",
      "sha256": "6fec1ede7039acc5615f2b3c0645fc85748a8a2ce1903d7891c7ea67b6b3fa18",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskWorkflowServiceTest.java",
      "status": "M",
      "sha256": "0fea15ef7c5aec462d1e29888cc18a9154330462d52a58041f895670850db5d2",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTemplateServiceImplTest.java",
      "status": "M",
      "sha256": "3e162bfee2e5db2f01ffa83e49b2438ab6023f2724a7f527ba86dce9b040b39a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java",
      "status": "M",
      "sha256": "87309445ea2935441a659c6da2e680e0577c4300005347a0201504befde33ccf",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java",
      "status": "M",
      "sha256": "98fc7a9bc0937f79eca337f0e30acbe08284ceb7d27c4cd3103b79120b4c5b4f",
      "hashSource": "WORKTREE",
      "stage": "A3",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java",
      "status": "M",
      "sha256": "5c8385f64f2788d40b7a84dc59b3effc1cdc6a8822866b9e4ade13950f358dca",
      "hashSource": "WORKTREE",
      "stage": "A4",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java",
      "status": "M",
      "sha256": "a83ccf6c3318e9dec12ad2a59520f4344b73d660c127635c0e77b0a6d1594944",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约资料维护入口"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLegalEntityController.java",
      "status": "U",
      "sha256": "72ca1ce41a24f96bd65d173ff70d94a04131668e5c72ec73fa6ed417b8decefb",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysLegalEntityMapper.java",
      "status": "U",
      "sha256": "aafa21cfd3d8129ccd890a235c371167da01165f0685ccdbf8bbc4abf4b74d49",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java",
      "status": "M",
      "sha256": "7c6aa31784e814ec3df5d78e9b5db6c122a2a261ea1409de2be4764eb31b7809",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约快照读取依赖"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java",
      "status": "M",
      "sha256": "5704c74b9d9f4a2d46fd61445591b14fcf775c3b2e9ec83b274182e21335cf05",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约资料规范化"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java",
      "status": "M",
      "sha256": "def967b5ce7ff99194860e4cac721aaa1d702709dfe1402d7620d1f9f585d3c4",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "五场景任务事件源"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysLegalEntityServiceImpl.java",
      "status": "U",
      "sha256": "cba77f1fccf6cace26070f0e7e6df779dbb459583a4f09b342540a594065b75d",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java",
      "status": "M",
      "sha256": "a4393c47fddae8b93f96784a3cb854f21077e58b21dbb3c38437cd7956c9c0a7",
      "hashSource": "WORKTREE",
      "stage": "HOLD_MIXED_SYSTEM",
      "releaseA": false,
      "decision": "签约权限改动与系统管理改动混杂，Release A 前需拆分或逐行复核"
    },
    {
      "path": "erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java",
      "status": "M",
      "sha256": "b1d920f827f83a9fd0717550c1338529018a9be773a11e61e74d5748835e5e8d",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "合同资料字段治理"
    },
    {
      "path": "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml",
      "status": "M",
      "sha256": "162c6adf9aadc4803563d2ee1ee2210b15d7c8e35b41c09cd6248a29805012b8",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-modules/erp-system/src/main/resources/mapper/system/SysLegalEntityMapper.xml",
      "status": "U",
      "sha256": "13bc6618e487fb247c562d24b6e58ff109c9b56b497fa3af2731fb740516272f",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml",
      "status": "M",
      "sha256": "1a8cb4dbedbc776ddcb05ef98e2200fb4eb3915a4dd3721dae7552529e14636b",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "签约快照字段映射"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferLifecycleTest.java",
      "status": "M",
      "sha256": "b308f61c85524549a69cc85928ee4766dc35e0e16e5ba48fddde5e50ffb7ed1a",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "调岗签约生命周期"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionIT.java",
      "status": "U",
      "sha256": "a6c38420cf3e20a5532cda6693ea636fca4f8a5106956e20815e68c619129648",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "调岗原生数据库替代测试"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java",
      "status": "D",
      "sha256": "f773755b7b8daee032aae95c977927df10340b9beb3f026cacab0e497491f4b7",
      "hashSource": "HEAD",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "旧 Testcontainers 测试删除快照"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionIT.java",
      "status": "U",
      "sha256": "cf0d0a43924b51b7e2ef2031caa35a330871c43eb7d05c08db9015442345b3e8",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "离职原生数据库替代测试"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java",
      "status": "D",
      "sha256": "fd90504eb710a3727208160b8ebd1f1c8fd9fa62dac0a2e624b2498a99992d08",
      "hashSource": "HEAD",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "旧 Testcontainers 测试删除快照"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java",
      "status": "M",
      "sha256": "370e3418eb69a7f0597041444a80cac5c895decb351723bfba8c8a79688e055e",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "入职签约生命周期"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionIT.java",
      "status": "U",
      "sha256": "9239c9b4eaf8723f942e73e2fe24f302332cd1ed742a16640d7e7d659bb0da4c",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "入职原生数据库替代测试"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java",
      "status": "D",
      "sha256": "e094a398c40d5c320fdcb6cbc0e5411cec63c39e2c7010777c914c3451a1cdbc",
      "hashSource": "HEAD",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "旧 Testcontainers 测试删除快照"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRegularizationLifecycleTest.java",
      "status": "M",
      "sha256": "0acd14a856f0688fd22b118a91db3e2d3595df483ab99beeb9ba6c8c9e82524e",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "转正签约生命周期"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57IT.java",
      "status": "U",
      "sha256": "bc4f7efa03061760dbf6a1b4f66d7ad8ab63431d5eb3c7295289dba7094a8a02",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java",
      "status": "D",
      "sha256": "87f744fabe888d9c65bee83a7ee4db9806387c05743b7d51a341f25117f0e80b",
      "hashSource": "HEAD",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysConfigServiceSignHrPermissionTest.java",
      "status": "M",
      "sha256": "ad9af7d032e665f732ecffd6f5774e89c7d934232eedc4fae4fd85d731d7cf6b",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "唯一签约 HR 权限测试"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java",
      "status": "M",
      "sha256": "4bb5061c9d722a53074a96a89f755bdf36dce86c4a154018bed507c2e33f1f58",
      "hashSource": "WORKTREE",
      "stage": "HOLD_MIXED_SYSTEM",
      "releaseA": false,
      "decision": "测试覆盖多业务域，不能随签约包整文件带入"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserSignHrLifecycleTest.java",
      "status": "M",
      "sha256": "9addb6e2fa5d2888eb1efe27edbdab45f61de79509c8378aeafbf2d905323a5a",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "唯一签约 HR 生命周期测试"
    },
    {
      "path": "erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeFieldRegistryTest.java",
      "status": "M",
      "sha256": "b9654c32c0173020e813121c1be7e0d0d94766b414d98f4d42f5af0668c15df8",
      "hashSource": "WORKTREE",
      "stage": "A0_SYSTEM_BASELINE",
      "releaseA": true,
      "decision": "合同字段治理测试"
    },
    {
      "path": "erp-ui/src/api/oa/signPackage.js",
      "status": "M",
      "sha256": "55517a84ac3fb3d3f72455b970775a4108eb103dd22ced7e334ac22e5b08813b",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/api/oa/signTask.js",
      "status": "M",
      "sha256": "6063d0138628898ccb6cd79029d0386a312b0730dbdb0945a7c4af92cc164caa",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/utils/signDictionary.js",
      "status": "U",
      "sha256": "ef887e46856ac657f740bf64bbaa8b5720a271c7390219856b046b8f5ca089d1",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/utils/signDisplayText.js",
      "status": "U",
      "sha256": "17d52094c74eadad630659f7d8b8406ee7a823c344b322b2412f6db13706c156",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/utils/signPackageFileGate.js",
      "status": "U",
      "sha256": "ad3f9e334c1016a7068b132fd6b18d9bf458bd7b4e7dc85e7614c12ae791f102",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/utils/signPlaceholder.js",
      "status": "U",
      "sha256": "ced31d5982d3c281d70b3e67eea0efe154894ef467156dcbb6bc91422c5fa9b3",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/utils/signScenario.js",
      "status": "U",
      "sha256": "dce704fb9d303dc12380218741e70226a5b67d59538963d502219f34558b04f7",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/utils/signTemplateType.js",
      "status": "U",
      "sha256": "1b4a1bbf172023ee1590894c14dcaf5ce1dc72ca5ef1e2d89f749df4539697f5",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/utils/todoMutationMatcher.js",
      "status": "M",
      "sha256": "bb33dc31cacf4a3d2146723c13f5d4e2e2f7cf42194fc5e8c982ab0f234e7399",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/utils/todoRouteResolver.js",
      "status": "M",
      "sha256": "3e0f5971609b14f728262808a6081571eaad0b19aa4aecd6aec39ae5f5828c1a",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/views/mobile/contract/index.vue",
      "status": "M",
      "sha256": "47244f690133dd81dcabdf255f6394bcec0c5afca223a90b75af7b51353823c3",
      "hashSource": "WORKTREE",
      "stage": "C",
      "releaseA": false,
      "decision": "旧合同统一中心属于 Release C"
    },
    {
      "path": "erp-ui/src/views/mobile/signPackage/index.vue",
      "status": "M",
      "sha256": "76784186eed43de6d6a7695042b2b4459d4dae1fc6ef4c30b2700e5494b14930",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/views/oa/laborContract/index.vue",
      "status": "M",
      "sha256": "2a652165a1c7c28df1177b540cb5481f12be403a08e2eafb8ed9d2823005ef1d",
      "hashSource": "WORKTREE",
      "stage": "C",
      "releaseA": false,
      "decision": "旧合同统一中心属于 Release C"
    },
    {
      "path": "erp-ui/src/views/oa/signPackage/CompanySealManagement.vue",
      "status": "U",
      "sha256": "5a17675ca1191f75efd7ac30e3503fc8c80d5544f7718928b01061ae0501ed29",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/views/oa/signPackage/index.vue",
      "status": "M",
      "sha256": "ec306e8558e59d0e2071c31e258bcdc908f34ad4892faef7ac0797283d0e6d53",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/views/oa/signTask/SignTaskConfirmDialog.vue",
      "status": "D",
      "sha256": "773d8a8c6b65c19ff595a01c3b14c3721ed032b4510cb56a97fd4554a8f9e1c7",
      "hashSource": "HEAD",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue",
      "status": "M",
      "sha256": "0c1e1bdb254e2e14c3f5bca60d0532457f3355e880546bce4b61da51e84be29b",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/src/views/oa/signTask/index.vue",
      "status": "M",
      "sha256": "db1a2d1a313ce2abec58ca761b0add348faf08c189ea3e0bd460269d44ec4cb2",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/contractUsabilityAudit.test.js",
      "status": "U",
      "sha256": "c8901822ef04eac21183617a204b6f2d3ab093ad85fba991c7c277cc52e656c1",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "erp-ui/test/signPackageEvidenceUx.test.js",
      "status": "M",
      "sha256": "b82ae024578eb209b5bc55093509349832af93731346898d856ebbb6beca745b",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/signPackageFileGate.test.js",
      "status": "U",
      "sha256": "ef8254bd08efdcaf5b5388c123db4b478413dd95e6adddcf26887785b41783e3",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/signPackageModule.test.js",
      "status": "M",
      "sha256": "d197a257b4bb84779c247959f5780e2fe82da1517bdf75bab792ebf354480988",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/signPackageRequestFailure.test.js",
      "status": "U",
      "sha256": "8cabfb45f80472ab3e0126c1b1958ce026d385cb88110e8921b25434b075b91c",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/signTaskCenter.test.js",
      "status": "M",
      "sha256": "a52e9c02831393c766a03371e6cc74167542bcfa9fdf1ec68b08a4fc0b9e99ad",
      "hashSource": "WORKTREE",
      "stage": "A1/A5",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "erp-ui/test/unifiedTodoMutationRefresh.test.js",
      "status": "M",
      "sha256": "241e2eef012e2ee7e81fbcf70ad75a9f8d424cbbcb700d039f87163cd252b4eb",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "签约 mutation 刷新契约"
    },
    {
      "path": "erp-ui/test/unifiedTodoRouteResolver.test.js",
      "status": "M",
      "sha256": "73ec42acde6708b586741a69ac83ca943d647fe7d61451fcd2c26b71e35af016",
      "hashSource": "WORKTREE",
      "stage": "A5",
      "releaseA": true,
      "decision": "签约待办路由契约"
    },
    {
      "path": "scripts/contract/generate_sign_template_drafts_20260714.py",
      "status": "U",
      "sha256": "43568ac44544ceda9a165b77df7914c9c8f1cd9ec62e63a7f98e10de9b318c86",
      "hashSource": "WORKTREE",
      "stage": "G0_TOOL_HOLD",
      "releaseA": false,
      "decision": "草案生成工具会写外部交付目录，不进入生产包"
    },
    {
      "path": "scripts/contract/menu_id_conflicts_20260716.json",
      "status": "U",
      "sha256": "eecfe5b66ac15e1e2edde389bd25755db9ce15cf16f9b9ae00d08e5b5976ed4d",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "历史菜单冲突精确指纹；新增或第三方所有者必须失败"
    },
    {
      "path": "scripts/contract/test_verify_menu_id_ownership.py",
      "status": "U",
      "sha256": "de9ee98b45086fabe7e72637b7a0ba3fa9685a25dcde808594cce982de3bc235",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "scripts/contract/verify_menu_id_ownership.py",
      "status": "U",
      "sha256": "7185047f0d4e31f25d68493d64338f338281a05a6641bec9873c682a4c5d3622",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "sql/erp_oa_sign_final_confirmation_20260714.sql",
      "status": "U",
      "sha256": "c1209b88f37371cbc8a7b32c7ecf1cdb0997712b143eff192218d94cd55746e1",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "sql/erp_oa_sign_menu_permission_repair_20260716.sql",
      "status": "U",
      "sha256": "5a9c37f42360851e98f122c624415fff38e63a51bf072ac2f65bb25d22b4cd7a",
      "hashSource": "WORKTREE",
      "stage": "A1",
      "releaseA": true,
      "decision": "生产收口计划显式文件"
    },
    {
      "path": "sql/erp_oa_sign_package_renewal_snapshot_20260714.sql",
      "status": "U",
      "sha256": "206e7251bcb3e848ef6e414d79e223f0b34474aef8e6183e4943576e10b1536a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    },
    {
      "path": "sql/erp_oa_sign_package_shop_name_backfill_20260713.sql",
      "status": "U",
      "sha256": "2a638b06c150c7d2676c14c24707e3f8b113f2f1da9c9ec205c0f3f0e1eba7e3",
      "hashSource": "WORKTREE",
      "stage": "HOLD_UNPAIRED_MIGRATION",
      "releaseA": false,
      "decision": "缺少 docker/mysql/db 字节一致副本"
    },
    {
      "path": "sql/erp_oa_sign_template_delivery_policy_20260713.sql",
      "status": "U",
      "sha256": "47574bee91a8cc207208fc8f1a26ca8c6f2b5be951ffc5b20b0fc74d80738309",
      "hashSource": "WORKTREE",
      "stage": "HOLD_UNPAIRED_MIGRATION",
      "releaseA": false,
      "decision": "缺少 docker/mysql/db 字节一致副本"
    },
    {
      "path": "sql/erp_oa_sign_template_onboard_v2_20260713.sql",
      "status": "U",
      "sha256": "e7b2ea09cad017b0ad9c8cf6a41b5669b4d6d2d3c4b2187ba91092097a3af093",
      "hashSource": "WORKTREE",
      "stage": "HOLD_UNPAIRED_MIGRATION",
      "releaseA": false,
      "decision": "缺少 docker/mysql/db 字节一致副本"
    },
    {
      "path": "sql/erp_oa_sign_template_scenarios_20260714.sql",
      "status": "U",
      "sha256": "1fd6a0c45d9fdcf3e7e29be4fb0548bb3a545a4ce84d8c24f08164443cf8f62a",
      "hashSource": "WORKTREE",
      "stage": "A0_EXISTING_BASELINE",
      "releaseA": true,
      "decision": "人工签约现有必需实现"
    }
  ],
  "migrationPairs": [
    {
      "source": "sql/erp_oa_sign_document_policy_snapshot_20260716.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_document_policy_snapshot_20260716.sql"
    },
    {
      "source": "sql/erp_oa_sign_final_confirmation_20260714.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_final_confirmation_20260714.sql"
    },
    {
      "source": "sql/erp_oa_sign_menu_permission_repair_20260716.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql"
    },
    {
      "source": "sql/erp_oa_sign_package_lifecycle_20260716.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_package_lifecycle_20260716.sql"
    },
    {
      "source": "sql/erp_oa_sign_package_renewal_snapshot_20260714.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_package_renewal_snapshot_20260714.sql"
    },
    {
      "source": "sql/erp_oa_sign_template_scenarios_20260714.sql",
      "deploy": "docker/mysql/db/erp_oa_sign_template_scenarios_20260714.sql"
    }
  ],
  "duplicateTestReview": {
    "legacyPath": "erp-ui/test/newBusinessMigrationRelease.test 2.js",
    "canonicalPath": "erp-ui/test/newBusinessMigrationRelease.test.js",
    "legacyGitState": "IGNORED_BY_*_2.*",
    "canonicalGitState": "U",
    "legacySha256": "46685e152f53498ec1d0ab457412bc5ed8f35e97d140737a65528a129897fe22",
    "canonicalSha256": "6b5e376c929c2675bf47af224b0a4b93721f1e03bcaa25c106253227d2687af0",
    "allAssertionsSuperseded": true,
    "resolution": "REMOVED_BEFORE_PROJECT_WIDE_TEST",
    "supersessionEvidence": [
      {
        "id": "canonical-migration-order",
        "canonicalEvidence": "正式测试用签名 manifest 的 18 条 migrations 驱动 release list，并增加依赖顺序校验；旧 9 条均仍在 manifest 中。"
      },
      {
        "id": "unique-migration-list",
        "canonicalEvidence": "正式测试保留 new Set(releaseList).size 唯一性断言。"
      },
      {
        "id": "source-deploy-byte-identity",
        "canonicalEvidence": "正式测试同时比较 sql、Docker bootstrap 与 packaged release 三份内容，并校验 manifest SHA-256。"
      },
      {
        "id": "feature-flags-default-false",
        "canonicalEvidence": "正式测试覆盖旧 7 个开关及新增开关，要求 SQL 写入 false、禁止 true，并要求 manifest 声明。"
      },
      {
        "id": "runner-fail-closed",
        "canonicalEvidence": "正式测试保留 set -Eeuo pipefail 断言。"
      },
      {
        "id": "runner-global-lock",
        "canonicalEvidence": "正式测试保留 GET_LOCK 与 RELEASE_LOCK 断言。"
      },
      {
        "id": "runner-copy-drift-guard",
        "canonicalEvidence": "正式测试保留 cmp -s 断言，且逐迁移增加三份字节一致校验。"
      },
      {
        "id": "runner-explicit-database",
        "canonicalEvidence": "正式测试保留 ERP_NEW_BUSINESS_MYSQL_DATABASE is required 断言。"
      }
    ],
    "additionalCanonicalCoverage": [
      "18 条迁移数量与签名 manifest 哈希",
      "依赖闭包与危险迁移/Bootstrap 排除",
      "统一审批 12 张表及 fail-closed seed",
      "总发布门禁调用顺序"
    ]
  }
}
<!-- CONTRACT_SIGNING_SCOPE_JSON_END -->
