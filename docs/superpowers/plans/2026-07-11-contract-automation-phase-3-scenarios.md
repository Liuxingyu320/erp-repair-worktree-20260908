# 合同自动化阶段3：五类场景自动草稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让入职、续签、转正、调岗和离职的人事业务动作可靠地产生可解释、去重的签约草稿；本阶段所有草稿都由唯一HR确认后发送。

**Architecture:** 系统模块把人事动作记录为不可变的 `sys_hr_lifecycle_action`，并通过本地outbox调用OA内部事件接口；OA按业务事件版本和场景规则创建任务。已发布方案冻结模板清单和hash，规则层只读取标准化快照。事件补偿扫描修复暂时调用失败，不通过定时扫描猜测任意档案改动。

**Tech Stack:** Java 17, Spring Boot 4 Scheduling, Spring Cloud OpenFeign, MyBatis XML, MySQL 8 JSON, Jackson, JUnit 5, Vue 2, Element UI, Node source tests.

---

## Task 1: 创建OA共享API和可靠人事事件outbox

**Files:**
- Modify: `erp-api/pom.xml`
- Create: `erp-api/erp-api-oa/pom.xml`
- Modify: `erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/ServiceNameConstants.java`
- Create: `erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrSignBusinessEvent.java`
- Create: `erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java`
- Create: `erp-api/erp-api-oa/src/main/java/com/erp/oa/api/RemoteSignTaskService.java`
- Create: `erp-api/erp-api-oa/src/main/java/com/erp/oa/api/factory/RemoteSignTaskFallbackFactory.java`
- Create: `erp-api/erp-api-oa/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `erp-modules/erp-system/pom.xml`
- Modify: `erp-modules/erp-oa/pom.xml`
- Create: `sql/erp_hr_lifecycle_action_20260711.sql`
- Create: `docker/mysql/db/erp_hr_lifecycle_action_20260711.sql`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysHrLifecycleAction.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysHrSignEventOutbox.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrSignEventOutboxMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysHrSignEventOutboxMapper.xml`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrLifecycleActionMigrationTest.java`

- [ ] 新模块 `erp-api-oa` 只依赖 `erp-common-core`；在 `ServiceNameConstants` 增加 `OA_SERVICE = "erp-oa"`。
- [ ] 事件契约固定字段：`eventId`、`scenario`、`employeeId`、`sourceType`、`sourceBusinessId`、`sourceEventVersion`、`occurredTime`、`operatorUserId`、`beforeSnapshot`、`afterSnapshot`、`attributes`。
- [ ] 快照显式列出员工、组织、法律主体、岗位、职级、地点、合同代码、社保代码、日期和薪资字段；禁止只传任意JSON字符串。
- [ ] `sys_hr_lifecycle_action` 保存动作类型、员工、前后快照JSON、生效日期、业务状态、风险字段、requestId、version和操作审计；`operatorType` 固定使用 `HUMAN/SYSTEM`，系统动作的 `operatorUserId` 可空；requestId唯一。
- [ ] `sys_hr_sign_event_outbox` 保存actionId/eventVersion/payload/status/retry；唯一索引 `(action_id, event_version)`。
- [ ] 同一事务更新员工资料、插入action和outbox；禁止提交事务后直接Feign调用。
- [ ] 运行：

```bash
cmp sql/erp_hr_lifecycle_action_20260711.sql docker/mysql/db/erp_hr_lifecycle_action_20260711.sql
mvn -pl erp-api/erp-api-oa -am -DskipTests install
mvn -pl erp-modules/erp-system -am -Dtest=HrLifecycleActionMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 提交：

```bash
git add -- erp-api/pom.xml erp-api/erp-api-oa/pom.xml \
  erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrSignBusinessEvent.java \
  erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java \
  erp-api/erp-api-oa/src/main/java/com/erp/oa/api/RemoteSignTaskService.java \
  erp-api/erp-api-oa/src/main/java/com/erp/oa/api/factory/RemoteSignTaskFallbackFactory.java \
  erp-api/erp-api-oa/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/ServiceNameConstants.java \
  erp-modules/erp-system/pom.xml erp-modules/erp-oa/pom.xml \
  sql/erp_hr_lifecycle_action_20260711.sql docker/mysql/db/erp_hr_lifecycle_action_20260711.sql \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/SysHrLifecycleAction.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/SysHrSignEventOutbox.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrSignEventOutboxMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml \
  erp-modules/erp-system/src/main/resources/mapper/system/SysHrSignEventOutboxMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrLifecycleActionMigrationTest.java
git commit -m "feat: publish hr lifecycle events through outbox"
```

## Task 2: 冻结并发布不可变签约方案版本

**Files:**
- Create: `sql/erp_oa_sign_plan_version_20260711.sql`
- Create: `docker/mysql/db/erp_oa_sign_plan_version_20260711.sql`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersion.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPlanVersionMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanVersionService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImplTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 新表冻结：场景、门店、法律主体、规则JSON、默认值、签署期限、提醒策略、自动发送条件、发布状态、发布人/时间、versionHash。
- [ ] 模板快照冻结：templateId、templateVersion、templateType、sourceFileHash、sortOrder、employeeSignRequired、签名/企业章定位JSON、匹配条件JSON。
- [ ] 写失败测试覆盖：模板文件缺失、hash不一致、必需占位符缺失、零模板、重复模板类型、无签名策略、未配置法律主体时发布失败。
- [ ] 发布接口为 `POST /signPackage/plan/{planId}/publish`；每次发布创建新版本号，不更新历史版本。
- [ ] versionHash按排序后的方案字段和模板快照计算；相同内容重复发布返回现有版本，不产生重复行。
- [ ] 已发布版本一律不可物理删除或编辑，只能停用新匹配；任务与签约包引用继续保留历史版本。
- [ ] 运行：

```bash
cmp sql/erp_oa_sign_plan_version_20260711.sql docker/mysql/db/erp_oa_sign_plan_version_20260711.sql
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignPlanVersionServiceImplTest,OaMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 提交：

```bash
git add -- sql/erp_oa_sign_plan_version_20260711.sql docker/mysql/db/erp_oa_sign_plan_version_20260711.sql \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersion.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPlanVersionTemplate.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPlanVersionMapper.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPlanVersionMapper.xml \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPlanVersionService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImplTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java
git commit -m "feat: publish immutable signing plan versions"
```

## Task 3: 建立通用事件接收和场景决策框架

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignDraftDecision.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OaSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskInternalController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskInternalControllerTest.java`

- [ ] 内部接口 `POST /signTask/inner/events` 使用 `@InnerAuth`；先用 `scenario` 选择唯一规则，再计算dedupeKey。
- [ ] 决策结构固定：

```java
public class OaSignDraftDecision {
    private Action action;
    private Long planVersionId;
    private String riskLevel;
    private List<String> reasonCodes;
    private OaSignPackage draftPackage;

    public enum Action { CREATE_DRAFT, NEEDS_DATA, NO_ACTION }
}
```

- [ ] 方案匹配必须恰好一条；零条返回 `PLAN_NOT_FOUND`，多条返回 `PLAN_CONFLICT`，均进入 `NEEDS_DATA` 或人工确认，不能任选第一条。
- [ ] 处理顺序：插入dedupe任务 → VALIDATING → 运行规则 → 创建版本化签约包/文件 → WAITING_HR_CONFIRM。任一步失败保留任务和错误。
- [ ] 所有五类场景在本阶段都必须落到 `WAITING_HR_CONFIRM`；即使自动开关误开也不能进入 `READY_TO_SEND`。
- [ ] 同一事件重复投递返回已有taskId；更高eventVersion创建新任务或使未完成旧任务失效，规则在各场景任务中明确。
- [ ] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaSignTaskOrchestratorTest,OaSignTaskInternalControllerTest test`。
- [ ] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignDraftDecision.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OaSignScenarioRule.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskInternalController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskInternalControllerTest.java
git commit -m "feat: orchestrate versioned hr signing events"
```

## Task 4: 接入确认入职并生成A1-A6/B1-B3草稿

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOnboardingConfirmRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrLifecycleService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrOnboardingControllerTest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OnboardSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OnboardSignScenarioRuleTest.java`

- [ ] `POST /hr/onboarding/{userId}/confirm` 要求 `hr:onboarding:confirm`、数据范围、requestId和完整入职字段。
- [ ] 仅 `待入职` 可确认；有试用期时状态变为 `试用`，否则变为 `在职`。员工档案更新、action和outbox同事务。
- [ ] event dedupeKey固定为 `ONBOARD:<employeeId>:<actionId>:<actionVersion>`。
- [ ] 规则要求手机号、证件、地址、组织、岗位、职级、法律主体、标准合同/社保代码、入职/合同/薪资日期合法。
- [ ] 延续现有九类方案，但匹配使用代码：

```text
LABOR_CONTRACT + SOCIAL_INSURED + 2-4/5-6/7-8 -> A1/A2/A3
LABOR_CONTRACT + SOCIAL_UNINSURED + 2-4/5-6/7-8 -> A4/A5/A6
SERVICE_CONTRACT + SOCIAL_UNINSURED + 2-4/5-6/7-8 -> B1/B2/B3
```

- [ ] `SERVICE_CONTRACT + SOCIAL_INSURED`、实习、外包、派遣、待确认进入人工原因，不猜方案。
- [ ] 草稿创建后状态 `WAITING_HR_CONFIRM`；同一确认请求重放不重复修改员工状态或生成任务。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-system -am -Dtest=HrOnboardingLifecycleTest,HrOnboardingControllerTest test
mvn -pl erp-modules/erp-oa -am -Dtest=OnboardSignScenarioRuleTest,OaSignTaskOrchestratorTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOnboardingConfirmRequest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/IHrLifecycleService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingLifecycleTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrOnboardingControllerTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OnboardSignScenarioRule.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OnboardSignScenarioRuleTest.java
git commit -m "feat: create signing drafts on onboarding confirmation"
```

## Task 5: 接入续签决策和30天扫描

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrRenewalDecisionRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrContractRenewalScanner.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrContractRenewalScannerTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRenewalLifecycleTest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/RenewalSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/RenewalSignScenarioRuleTest.java`

- [ ] 每日02:15扫描未离职员工，合同结束日在今天至30天内且没有未完成续签决策时创建 `RENEWAL_DECISION` action；扫描本身不发 `RENEWAL_CONFIRMED` 事件、不生成合同。
- [ ] 阈值读取 `sign.renewal.decision-days`，缺失时30；运行时固定Asia/Shanghai，并以LocalDate比较。
- [ ] HR接口 `POST /hr/employee/{userId}/renewal/confirm` 可选择续签或不续签。续签必须提供新起止日期、合同代码、期限、法律主体和requestId。
- [ ] 不续签产生 `NO_ACTION` 终态和记录，不生成签约包；续签后增加renewalCount并产生事件。
- [ ] dedupeKey固定为 `RENEWAL:<employeeId>:<oldContractEndDate>:<newRenewalCount>`。
- [ ] 规则冻结旧/新合同字段；本阶段无论风险高低都停在 `WAITING_HR_CONFIRM`。
- [ ] 日期重叠、倒置、法律主体缺失、存在未完成续签任务时拒绝。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-system -am -Dtest=HrContractRenewalScannerTest,HrRenewalLifecycleTest test
mvn -pl erp-modules/erp-oa -am -Dtest=RenewalSignScenarioRuleTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrRenewalDecisionRequest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrContractRenewalScanner.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrContractRenewalScannerTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRenewalLifecycleTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/RenewalSignScenarioRule.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/RenewalSignScenarioRuleTest.java
git commit -m "feat: create renewal decisions and signing drafts"
```

## Task 6: 接入转正确认

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrRegularizationRequest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrRegularizationLifecycleTest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/RegularizeSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/RegularizeSignScenarioRuleTest.java`

- [ ] `POST /hr/employee/{userId}/regularize` 只允许 `试用`，要求实际转正日期、转正后岗位/职级/薪资快照和requestId；更新状态为 `正式`。
- [ ] dedupeKey固定为 `REGULARIZE:<employeeId>:<actionId>:<actionVersion>`。
- [ ] 规则比较前后岗位、职级、薪资和组织；按已发布方案生成转正确认书、岗位职责或薪资确认材料。
- [ ] 没有需要员工确认的文件时任务进入 `NO_ACTION`，但保留决策原因；任何薪资变化标记 `REVIEW_REQUIRED`。
- [ ] 无论风险均由HR确认；批量确认不包含有薪资变化的转正任务。
- [ ] 运行聚焦system和OA测试后提交 `feat: create drafts on employee regularization`。

## Task 7: 接入调岗确认并识别高风险变化

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrEmployeeTransferRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrTransferRiskConfirmation.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferLifecycleTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/TransferSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/TransferSignScenarioRuleTest.java`
- Create: `erp-ui/src/views/hr/components/HrEmployeeTransferDialog.vue`
- Create: `sql/erp_hr_transfer_effective_date_20260713.sql`
- Create: `docker/mysql/db/erp_hr_transfer_effective_date_20260713.sql`

- [x] `POST /hr/employee/{userId}/transfer` 要求生效日期、目标门店/部门/岗位/职级/地点/薪资/法律主体和requestId。
- [x] 服务端读取并冻结旧值，禁止客户端伪造beforeSnapshot；更新资料、action和outbox同事务。
- [x] 比较门店、部门、岗位、职级、地点、薪资、法律主体和汇报关系；全无变化返回错误，不创建动作。
- [x] 只有需要员工确认的变化才创建签约草稿；仅内部汇报关系微调可以 `NO_ACTION`。
- [x] 跨法律主体、跨城市、降职、降薪和生效日已过由服务端标记 `HIGH`，并展示具体reasonCode。
- [ ] 自定义补偿尚无权威员工档案字段，当前不得从备注或前端自由文本推断；引入稳定字段后再加入风险派生。
- [x] dedupeKey固定为 `TRANSFER:<employeeId>:<actionId>:<actionVersion>`；始终由唯一HR逐份确认。
- [x] 运行聚焦system、OA、前端和MySQL 5.7事务测试；结果记录在[调岗日期验证记录](../verification/2026-07-13-contract-transfer-effective-date.md)。

### Task 7A：调岗日期、生效与历史补录稳定口径（2026-07-13）

- [x] 日期权威值由后端 `Asia/Shanghai` 业务时钟按自然日产生；前端先读取 `/hr/employee/transfer/business-date`，只比较 `yyyy-MM-dd`，不读取浏览器本地“今天”。
- [x] 未来日期返回“未来日期的调岗暂不能确认，请在生效当天操作”，并在锁员工、写动作、更新档案和写outbox之前结束；不保存待生效任务，不增加扫描器、延迟队列或定时生效流程。
- [x] 当天日期在一个事务中锁定员工档案，冻结before/after快照，更新 `sys_user`、`sys_user_profile`、`sys_user_post`，写 `sys_hr_lifecycle_action` 和 `sys_hr_sign_event_outbox`。
- [x] 过去日期标记“高风险补录”，由同一个HR完成结构化二次确认；服务端逐项核对员工、原/新组织岗位、生效日、真实操作日、固定风险说明和补录原因，不增加第二审批人。
- [x] `effective_date` 保存业务生效日，`actual_confirm_time` 保存真实操作时间；`risk_confirmation_json` 和 `historical_reason` 保存历史补录确认内容，操作人、IP、User-Agent继续落在生命周期动作审计列。
- [x] `request_id` 唯一、action/version outbox唯一；同员工行锁序列化并发确认，相同请求重放返回原action，不同请求对相同生效日只能落一个动作；任一步失败整体回滚。
- [x] OA任务冻结before/after JSON、`business_effective_date` 和历史标识；TRANSFER规则使用业务生效日匹配已发布且启用的方案，缺资料进入唯一HR任务中心，无需员工确认时进入 `NO_ACTION`。
- [x] 员工列表和档案详情提供“确认调岗”；历史补录有警告色二次确认，任务详情展示“历史调岗补录”，普通HR页面不展示文件hash。

验收边界：昨天、今天、明天、月末、年末、闰日、服务端与前端设备时区不同；未来拒绝零业务副作用；历史确认缺失或被篡改必须拒绝；同requestId跨自然日重试仍按原确认日幂等返回。

尚存风险：法律主体目前没有独立主数据选择器，目标主体ID/编码/名称仍由唯一HR填写并进入高风险规则；上线前应以脱敏生产数据核对主体编码。移动端当前没有员工调岗入口，因此本次只交付桌面端，不存在需要同步修改的移动入口。调岗不支持未来预约生效是明确业务边界，不应作为缺陷通过后台任务绕开。

## Task 8: 接入确认离职和异常离职队列

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingConfirmRequest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingLifecycleTest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OffboardSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignScenarioRuleTest.java`

- [ ] `POST /hr/employee/{userId}/offboard` 要求离职类型、最后工作日、离职原因、工资结算、资产交接、竞业决定、补偿金额和requestId。
- [ ] 确认后employeeStatus变为 `离职`、leaveDate写入、账号status变为禁用；三者与action/outbox同事务。
- [ ] 规则按离职类型选择离职确认、交接、结算、保密竞业提醒或解除终止材料。
- [ ] 拒绝确认预期、辞退、违纪解除、争议、定制赔偿、竞业待定、工资/资产未完成均标记 `HIGH` 并进入异常离职筛选；仍可生成草稿但不能批量确认。
- [ ] dedupeKey固定为 `OFFBOARD:<employeeId>:<actionId>:<actionVersion>`；始终HR逐份确认。
- [ ] 运行聚焦system和OA测试后提交 `feat: create drafts on employee offboarding`。

## Task 9: 分发人事outbox并做补偿扫描

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/ErpSystemApplication.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventDispatcherTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventCompensationScannerTest.java`

- [ ] system应用启用Scheduling。dispatcher每30秒领取100条，使用 `PENDING/RETRY + version` 条件更新。
- [ ] 调用OA返回taskId后标记 `SENT`；连接/超时/5xx按1、5、30、120、360分钟重试；4xx契约错误进入 `DEAD`。
- [ ] 每15分钟扫描有action但缺outbox或outbox长期SENDING的记录，补建/释放；不通过比较整个员工表猜测业务动作。
- [ ] 同一action/version重放必须返回同一taskId，不能生成重复草稿。
- [ ] 运行聚焦测试后提交 `feat: retry hr signing business events`。

## Task 10: 补齐HR操作界面

**Files:**
- Modify: `erp-ui/src/api/hr/employee.js`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Create: `erp-ui/src/views/hr/components/HrOnboardingConfirmDialog.vue`
- Create: `erp-ui/src/views/hr/components/HrRegularizationDialog.vue`
- Create: `erp-ui/src/views/hr/components/HrEmployeeTransferDialog.vue`
- Create: `erp-ui/src/views/hr/components/HrOffboardingDialog.vue`
- Create: `erp-ui/src/views/hr/components/HrRenewalDecisionDialog.vue`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Create: `erp-ui/test/hrLifecycleSigningActions.test.js`

- [ ] 只向具备相应权限的唯一HR显示“确认入职、确认转正、确认调岗、确认离职、续签决定”动作。
- [ ] 每个对话框提交前显示会产生的签约任务说明，不承诺自动发送。
- [ ] 字段校验与后端一致；requestId在打开对话框时生成，提交失败重试沿用同一个ID，关闭重开生成新ID。
- [ ] 成功后刷新员工列表和签约任务todo；不在前端直接创建签约包。
- [ ] 运行：

```bash
cd erp-ui
node test/hrLifecycleSigningActions.test.js
node test/hrWorkbenchUx.test.js
npm test
npm run build:prod
```

- [ ] 提交 `feat: add hr lifecycle signing actions`。

## Task 11: 阶段3五场景验收

- [ ] 运行：

```bash
mvn -pl erp-api/erp-api-oa -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
```

- [ ] 为五类场景各执行标准、资料缺失、高风险和重复请求用例；确认任务数、签约包数和事件数符合预期。
- [ ] 断开OA后执行人事动作，再恢复OA；任务最终创建一次且人事资料更新不丢失。
- [ ] 修改已发布模板文件后重放事件，任务必须因hash异常进入人工处理，不能使用变化后的文件冒充旧版本。
- [ ] 五类任务全部停在 `WAITING_HR_CONFIRM`，任何一条自动进入 `READY_TO_SEND` 都视为阶段失败。
- [ ] 唯一HR完成一次确认后发送；不存在第二审批人字段、按钮或状态。
- [ ] 保持 `sign.automation.global.enabled=false`，进入阶段4前导出五场景匹配和降级原因清单。
