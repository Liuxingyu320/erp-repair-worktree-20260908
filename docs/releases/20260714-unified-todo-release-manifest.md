# 统一待办与调拨/盘点发布清单

> 发布批次：`UNIFIED_TODO_SCOPE_20260713_V1`  
> 基线提交：`b694659dd2c1`  
> 编制日期：2026-07-14  
> 状态：历史发布链仅保留仓库/健康证 6 个迁移；OA 采购与 Flowable 切换已由统一审批发布流程取代。

## 1. 业务边界

- 本历史发布链不再处理 OA 采购或 Flowable；相关切换使用 `scripts/verify-unified-approval-cutover.sh`。
- 门店要货只使用库存调拨，仓库与进销存菜单不再生成两套待办。
- 调拨进入 5 类待办；盘点进入 4 类待办，其中执行类动态表达待执行/即将到期/已逾期。
- 最终统一待办共 33 类：inventory 18、OA 8、system/HR 7。
- 进销存采购、质检、收货和采购退货不属于 OA 退役范围。

## 2. 显式代码范围

以下路径是本批次的核心发布输入。当前工作区含有其他未提交功能，因此同文件中仍必须做 hunk 级审查，不能使用 `git add .`。

### 共享待办契约

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`
- `erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java`

### inventory：调拨和盘点

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockCheckController.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvStockCheckAssignmentRequest.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvStockCheckCounterCandidate.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckMapper.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTodoMapper.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvStockCheckService.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckMapper.xml`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvStockCheckServiceImplTest.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTodoServiceImplTest.java`

### OA：采购退役与签约待办

- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoCandidate.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/BusinessFeatureGate.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/BusinessFeatureGateTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java`

### system/HR：健康证待办

- `erp-modules/erp-system/src/main/java/com/erp/system/constant/SysTodoTypes.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysTodoCandidateRow.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysHealthCertificateTodoCandidate.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysTodoMapper.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java`
- `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml`
- `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrHealthCertificateController.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrHealthCertificate.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/mapper/HrHealthCertificateMapper.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrHealthCertificateService.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateAccessService.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateFeatureService.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateReminderScanner.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrHealthCertificateServiceImpl.java`
- `erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateMapper.xml`

### 前端聚合、跳转和业务页

- `erp-ui/src/layout/components/HeaderTodo/index.vue`
- `erp-ui/src/router/index.js`
- `erp-ui/src/utils/todoAggregator.js`
- `erp-ui/src/utils/todoBusinessFocus.js`
- `erp-ui/src/utils/todoContextLease.js`
- `erp-ui/src/utils/todoNavigator.js`
- `erp-ui/src/utils/todoRouteResolver.js`
- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/views/mobile/todo/index.vue`
- `erp-ui/src/api/inventory/stockCheck.js`
- `erp-ui/src/views/inventory/stockCheck/index.vue`
- `erp-ui/src/api/hr/healthCertificate.js`
- `erp-ui/src/views/hr/healthCertificate/index.vue`
- `erp-ui/src/views/mobile/hr/healthCertificate/index.vue`
- `erp-ui/src/views/oa/signTask/index.vue`
- `erp-ui/test/unifiedTodoAggregation.test.js`
- `erp-ui/test/unifiedTodoBusinessFocus.test.js`
- `erp-ui/test/unifiedTodoBusinessScopeMigration.test.js`
- `erp-ui/test/unifiedTodoContextLease.test.js`
- `erp-ui/test/unifiedTodoDesktop.test.js`
- `erp-ui/test/unifiedTodoMobile.test.js`
- `erp-ui/test/unifiedTodoNavigator.test.js`
- `erp-ui/test/unifiedTodoReleaseGate.test.js`
- `erp-ui/test/unifiedTodoRouteResolver.test.js`
- `erp-ui/test/unifiedTodoStore.test.js`
- `erp-ui/test/hrHealthCertificate.test.js`
- `erp-ui/test/hrHealthCertificateUx.test.js`

## 3. 数据库发布包

唯一支持的顺序位于 `scripts/unified-todo-release-20260714.list`：

1. `sql/erp_inventory_purchase_return_item_20260713.sql`
2. `sql/erp_inventory_stock_check_scope_20260713.sql`
3. `sql/erp_inventory_receipt_quality_20260713.sql`
4. `sql/erp_inventory_performance_indexes_20260713.sql`
5. `sql/erp_inventory_warehouse_navigation_20260713.sql`
6. `sql/erp_hr_health_certificate_20260713.sql`

回滚与门禁：

- `sql/erp_inventory_warehouse_navigation_rollback_20260713.sql`
- `scripts/verify-unified-todo-release.sh`
- `erp-ui/test/unifiedTodoReleaseGate.test.js`

`sql/erp_hr_health_certificate_20260713.sql` 与 `docker/mysql/db/` 中的部署副本逐字节一致。`sql/erp_unified_todo_business_scope_20260713.sql` 仅作历史审计，不得再由本链路执行。

## 4. 事务测试后缀迁移

下列四组为有意的 Surefire `*Test` -> Failsafe `*IT` 迁移，不是误删：

- `HrEmployeeTransferTransactionTest.java` -> `HrEmployeeTransferTransactionIT.java`
- `HrOffboardingTransactionTest.java` -> `HrOffboardingTransactionIT.java`
- `HrOnboardingTransactionTest.java` -> `HrOnboardingTransactionIT.java`
- `HrSignEventOutboxMySql57Test.java` -> `HrSignEventOutboxMySql57IT.java`

`scripts/verify-new-business-it-contract.sh` 校验新旧文件除类名/自引用外内容一致，并校验 `pom.xml` 的 `new-business-mysql-it` Failsafe profile。

## 5. 明确排除

- `erp-ui/node_modules`、`target/`、`.codex-runs/`、`.playwright-cli/`、截图和临时测试数据。
- 与统一待办无关的人事、云盘、客服、礼盒、OE 等其他工作区改动。
- OA 采购数据清理与 Flowable 物理删除（仅由统一审批切换脚本和独立破坏性清理脚本管理）。
- 对 23 张历史活动盘点伪造责任人或截止时间。

## 6. 当前发布门

- 代码门：已通过。后端专项 297 项通过、1 项条件跳过；前端 183/183 通过；生产构建通过。
- 数据库门：MySQL 8.0+、14 个仓库/健康证前置表、9 个菜单锚点、inventory 采购入口正常。
- OA/Flowable 业务门已从本历史 runner 移除，必须单独通过统一审批切换门禁。
