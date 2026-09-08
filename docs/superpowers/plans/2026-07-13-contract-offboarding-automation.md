# 离职合同自动化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让唯一 HR 在最后工作日到达后原子确认员工离职、停用账号、冻结离职前后快照并写 `OFFBOARD` Outbox；OA 根据离职类型、资料状态和服务端风险生成离职材料草稿或单 HR 补资料任务，前端提供可审计的结构化确认入口。

**Architecture:** System 是离职业务日、员工状态、账号状态、风险和幂等的唯一权威；一次事务锁定员工档案，条件更新 profile/account，追加 `OFFBOARD_CONFIRMED` action 和 Outbox。OA 的 `OffboardSignScenarioRule` 只读取冻结 event，不回查当前员工；匹配已发布方案版本并把材料停在 HR 确认。Vue 仅镜像日期和风险提示，失败重试复用 requestId，不能直接创建 OA 包。

**Tech Stack:** Java 17、Spring Boot、Jakarta Validation、MyBatis、Jackson、MySQL 5.7.44/Testcontainers、OA 签约规则/方案版本、Vue 2、Element UI、Node.js 24 源码契约测试。

---

## 0. 固定业务契约

- 执行分支：`codex/offboarding-automation-20260713`。
- 执行 worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713`。
- 权限机器码：`hr:employee:offboard`，仅 `sign.hr.user-id` 指向的唯一 HR 可执行；管理员身份不绕过。
- System actionType：`OFFBOARD_CONFIRMED`；event scenario：`OFFBOARD`；event sourceType：`HR_LIFECYCLE_ACTION`。
- OA dedupeKey：`OFFBOARD:<employeeId>:<actionId>:<actionVersion>`。
- 未来最后工作日固定错误：`未来最后工作日的离职暂不能确认，请在最后工作日操作`，零副作用，不建未来任务。
- 当天或历史日期确认后立即把 `employee_status` 设为 `离职`、`leave_date` 设为最后工作日、`sys_user.status` 设为 `1`。
- 保留 `sys_user_post` 和组织关系作为历史证据；离职事务不删除岗位、角色或门店授权。
- 低风险仅为：`VOLUNTARY_EXPECTED + COMPLETED 工资 + COMPLETED 资产 + NOT_APPLICABLE 竞业 + 0 补偿 + 空补偿说明 + 非历史`。任一不满足即 `HIGH` 并要求同一 HR 结构化二次确认。
- 本轮不自动发送，`sign.automation.global.enabled` 保持关闭。

## Task 1: 定义离职 DTO、枚举和冻结快照字段

**Files:**

- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingType.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingCompletionStatus.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingNonCompeteDecision.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingRiskConfirmation.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingConfirmRequest.java`
- Modify: `erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/domain/dto/HrOffboardingConfirmRequestTest.java`

- [ ] **Step 1: 写 DTO validation 失败测试**

测试使用 Jakarta `Validator`，覆盖空 requestId、未来判断之外的字段约束、空 reason、负补偿、超过两位小数、过长说明和嵌套确认校验。枚举值必须精确为：

```java
public enum HrOffboardingType
{
    VOLUNTARY_EXPECTED,
    VOLUNTARY_UNEXPECTED,
    TERMINATION,
    DISCIPLINARY_TERMINATION,
    DISPUTED_TERMINATION
}

public enum HrOffboardingCompletionStatus
{
    COMPLETED,
    PENDING
}

public enum HrOffboardingNonCompeteDecision
{
    NOT_APPLICABLE,
    REQUIRED,
    PENDING
}
```

- [ ] **Step 2: 运行测试确认类型不存在**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOffboardingConfirmRequestTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 实现请求 DTO**

请求字段和约束保持如下：

```java
public class HrOffboardingConfirmRequest
{
    @NotBlank @Size(max = 64) private String requestId;
    @NotNull private LocalDate lastWorkingDate;
    @NotNull private HrOffboardingType offboardingType;
    @NotBlank @Size(max = 500) private String reason;
    @NotNull private HrOffboardingCompletionStatus salarySettlementStatus;
    @NotNull private HrOffboardingCompletionStatus assetHandoverStatus;
    @NotNull private HrOffboardingNonCompeteDecision nonCompeteDecision;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2)
    private BigDecimal compensationAmount;
    @Size(max = 500) private String compensationNote;
    @Valid private HrOffboardingRiskConfirmation riskConfirmation;
}
```

结构化确认必须包含：`confirmed, employeeId, employeeName, offboardingType, lastWorkingDate, operationDate, salarySettlementStatus, assetHandoverStatus, nonCompeteDecision, compensationAmount, riskStatement, reason`。固定 statement：

```text
我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用
```

- [ ] **Step 4: 扩展签约快照**

在 `HrEmployeeSigningSnapshot` 增加完整 getter/setter：

```java
private String accountStatus;
private String offboardingType;
private String leaveReason;
private String salarySettlementStatus;
private String assetHandoverStatus;
private String nonCompeteDecision;
private BigDecimal compensationAmount;
private String compensationNote;
```

这些字段属于冻结 event 契约；除 `accountStatus` 外不写回员工主表。before 中离职业务字段为 null，after 中固定为请求的规范化值。

- [ ] **Step 5: 运行 DTO 测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOffboardingConfirmRequestTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingType.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingCompletionStatus.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingNonCompeteDecision.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingRiskConfirmation.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/dto/HrOffboardingConfirmRequest.java \
  erp-api/erp-api-oa/src/main/java/com/erp/oa/api/domain/HrEmployeeSigningSnapshot.java \
  erp-modules/erp-system/src/test/java/com/erp/system/domain/dto/HrOffboardingConfirmRequestTest.java
git commit -m "feat: define offboarding lifecycle contract"
```

## Task 2: 增加权限化 controller 与服务端业务日

**Files:**

- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/IHrLifecycleService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeControllerContractTest.java`

- [ ] **Step 1: 先写 controller 契约失败断言**

断言精确路径、权限和脱敏审计：

```java
assertThat(source).contains("@GetMapping(\"/offboard/business-date\")")
        .contains("@PostMapping(\"/{userId}/offboard\")")
        .contains("@RequiresPermissions(\"hr:employee:offboard\")")
        .contains("isSaveRequestData = false")
        .contains("isSaveResponseData = false");
```

- [ ] **Step 2: 运行 controller 测试确认红灯**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeControllerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 扩展 service 接口**

```java
Long confirmOffboarding(Long employeeId, HrOffboardingConfirmRequest request,
        Long operatorUserId, String operatorName, boolean operatorAdmin,
        String operatorIp, String operatorUserAgent);

LocalDate offboardingBusinessDate();
```

- [ ] **Step 4: 实现 controller 入口**

```java
@RequiresPermissions("hr:employee:offboard")
@GetMapping("/offboard/business-date")
public AjaxResult offboardingBusinessDate()
{
    return success(Map.of("businessDate",
            hrLifecycleService.offboardingBusinessDate()));
}

@Log(title = "员工离职确认", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
@RequiresPermissions("hr:employee:offboard")
@PostMapping("/{userId}/offboard")
public AjaxResult confirmOffboarding(@PathVariable Long userId,
        @Valid @RequestBody HrOffboardingConfirmRequest request,
        HttpServletRequest servletRequest)
{
    Long actionId = hrLifecycleService.confirmOffboarding(userId, request,
            SecurityUtils.getUserId(), SecurityUtils.getUsername(),
            SecurityUtils.isAdmin(), IpUtils.getIpAddr(servletRequest),
            servletRequest == null ? null : servletRequest.getHeader("User-Agent"));
    return success(Map.of("actionId", actionId));
}
```

- [ ] **Step 5: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeControllerContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-system/src/main/java/com/erp/system/service/IHrLifecycleService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/HrEmployeeControllerContractTest.java
git commit -m "feat: expose offboarding confirmation endpoint"
```

## Task 3: 用 TDD 实现离职事务、风险与幂等

**Files:**

- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingLifecycleTest.java`

- [ ] **Step 1: 写生命周期失败测试**

覆盖以下边界，使用固定 `Clock` 和 mocked mapper：

- 非配置 HR、管理员但非配置 HR都拒绝。
- 未来最后工作日先拒绝，所有 mapper `never()`。
- 员工不存在、待入职、已离职、账号已停用拒绝。
- 员工已有计划 leaveDate 且与请求不同拒绝。
- 当天标准主动离职风险 `LOW`，不要求确认。
- 历史、非预期主动、辞退、违纪、争议、工资待结、资产待交、竞业 REQUIRED/PENDING、非零补偿、非空补偿说明分别派生 `HIGH`。
- 任一 HIGH 没有确认、statement 不精确、员工/日期/状态/金额不匹配均拒绝。
- 成功调用顺序包含 action、profile 条件更新、account 条件停用、Outbox；不调用 `deleteUserPostByUserId`。
- 相同 requestId + 相同 payload + 当前状态与 after 一致返回旧 actionId。
- 相同 requestId 但 payload 或当前状态不一致拒绝。

核心未来日期断言：

```java
assertThatThrownBy(() -> service.confirmOffboarding(9L, futureRequest,
        88L, "配置HR", false, "10.0.0.8", "agent"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("未来最后工作日的离职暂不能确认，请在最后工作日操作");
verifyNoInteractions(profileMapper, actionMapper, outboxMapper);
```

- [ ] **Step 2: 运行测试确认行为缺失**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOffboardingLifecycleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 让签约快照读取账号状态**

在 `selectSigningSnapshotByUserIdForUpdate` 增加：

```sql
u.status as accountStatus,
```

保持 `u.del_flag='0'` 和 `for update`。不要另做无锁账号查询。

- [ ] **Step 4: 增加条件更新 mapper**

Profile 方法：

```java
int updateOffboardingProfile(
        @Param("snapshot") HrEmployeeSigningSnapshot snapshot,
        @Param("expectedEmployeeStatus") String expectedEmployeeStatus,
        @Param("expectedLeaveDate") LocalDate expectedLeaveDate,
        @Param("operatorName") String operatorName);
```

SQL 使用 MySQL null-safe 等于：

```xml
<update id="updateOffboardingProfile">
    update sys_user_profile
    set employee_status = '离职',
        leave_date = #{snapshot.leaveDate},
        update_by = #{operatorName},
        update_time = sysdate()
    where user_id = #{snapshot.employeeId}
      and employee_status = #{expectedEmployeeStatus}
      and leave_date &lt;=&gt; #{expectedLeaveDate}
</update>
```

Account 方法与 SQL：

```java
int disableUserForOffboarding(@Param("userId") Long userId,
        @Param("expectedStatus") String expectedStatus,
        @Param("operatorName") String operatorName);
```

```xml
<update id="disableUserForOffboarding">
    update sys_user
    set status = '1', update_by = #{operatorName}, update_time = sysdate()
    where user_id = #{userId}
      and status = #{expectedStatus}
      and del_flag = '0'
</update>
```

Action mapper 增加同员工/同最后工作日锁定查询，过滤 `OFFBOARD_CONFIRMED` 并 `for update`。

- [ ] **Step 5: 实现 normalize、日期和风险派生**

风险代码固定，便于 OA 和审计解释：

```java
if (historical) risks.add("HISTORICAL_OFFBOARDING");
if (request.getOffboardingType() != HrOffboardingType.VOLUNTARY_EXPECTED)
    risks.add("NON_STANDARD_OFFBOARDING_TYPE");
if (request.getSalarySettlementStatus() != HrOffboardingCompletionStatus.COMPLETED)
    risks.add("SALARY_SETTLEMENT_PENDING");
if (request.getAssetHandoverStatus() != HrOffboardingCompletionStatus.COMPLETED)
    risks.add("ASSET_HANDOVER_PENDING");
if (request.getNonCompeteDecision() != HrOffboardingNonCompeteDecision.NOT_APPLICABLE)
    risks.add("NON_COMPETE_REVIEW_REQUIRED");
if (request.getCompensationAmount().signum() > 0
        || trim(request.getCompensationNote()) != null)
    risks.add("COMPENSATION_REVIEW_REQUIRED");
```

`offboardingBusinessDate()` 与调岗一样使用 `LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE)`；不要使用 JVM 默认时区。

- [ ] **Step 6: 实现原子事务**

方法使用 `@Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)`。顺序固定：

```java
requireConfiguredHr(operatorUserId);
normalizeAndValidateOffboardingRequest(employeeId, request);
LocalDate operationDate = offboardingBusinessDate();
rejectFuture(request.getLastWorkingDate(), operationDate);
profileMapper.lockSigningProfileByUserId(employeeId);
HrEmployeeSigningSnapshot before =
        profileMapper.selectSigningSnapshotByUserIdForUpdate(employeeId);
// scope check
// selectByRequestIdForUpdate；命中时先进入严格 replay
// 仅新 requestId 再校验 employee/account 状态、同日 action 和风险
HrEmployeeSigningSnapshot after = objectMapper.convertValue(
        before, HrEmployeeSigningSnapshot.class);
applyOffboarding(after, request);
// insert immutable action
// conditionally update profile and account; each must affect exactly 1 row
// insert PENDING outbox; failure rolls back all prior writes
```

顺序不能交换：同 requestId 的成功重放发生时，当前快照本来就应是 `employeeStatus=离职/accountStatus=1`；因此必须先查 existing action 并进入 replay，再对“新的 requestId”拒绝已离职或已停用状态。不同 requestId 仍不得利用 replay 路径。

`applyOffboarding` 必须设置：

```java
after.setEmployeeStatus("离职");
after.setAccountStatus("1");
after.setLeaveDate(request.getLastWorkingDate());
after.setOffboardingType(request.getOffboardingType().name());
after.setLeaveReason(request.getReason());
after.setSalarySettlementStatus(request.getSalarySettlementStatus().name());
after.setAssetHandoverStatus(request.getAssetHandoverStatus().name());
after.setNonCompeteDecision(request.getNonCompeteDecision().name());
after.setCompensationAmount(request.getCompensationAmount());
after.setCompensationNote(request.getCompensationNote());
```

Action 设置 `sourceType=HR_OFFBOARDING`、`sourceBusinessId=requestId`、effectiveDate=lastWorkingDate、actualConfirmTime、risk codes、结构化确认 JSON、requestId、IP/User-Agent。event envelope 使用 actionId/version，attributes 至少有 `actionType,sourceActionId,sourceActionVersion,effectiveDate,actualConfirmTime,historicalSupplement,riskLevel,historicalReason`。

- [ ] **Step 7: 实现严格 replay**

重放必须校验：action 类型/source/requestId/employee/version、冻结 after 与请求逐字段相等、当前 profile 的 employeeStatus/leaveDate 与 after 一致、当前 accountStatus 为 `1`。只返回原 actionId，不补写缺失 Outbox；缺失 Outbox 由 Agent 1 补偿扫描器负责。

- [ ] **Step 8: 运行生命周期测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOffboardingLifecycleTest,HrEmployeeTransferLifecycleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingLifecycleTest.java
git commit -m "feat: confirm offboarding atomically"
```

## Task 4: 在 MySQL 5.7 验证回滚、并发和幂等

**Files:**

- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java`
- Create: `erp-modules/erp-system/src/test/resources/application-hr-offboarding-it.yml`
- Create: `erp-modules/erp-system/src/test/resources/hr-offboarding-it-schema.sql`

- [ ] **Step 1: 复制调岗事务测试结构并先写失败用例**

使用 `mysql:5.7.44`，固定上海业务日。覆盖：

- 未来日期 profile/account/action/outbox 均为零变更。
- 当天确认 employee/profile/action/outbox 恰好各一次，岗位关联仍存在。
- 历史日期没有结构化确认失败，有正确确认成功且 risk HIGH。
- Outbox insert trigger 失败回滚 profile 和 account。
- account 条件更新返回 0 时整体回滚。
- 同 requestId 两线程阻塞同一 profile row，返回同 actionId。
- 不同 requestId 同员工同最后工作日只有一个成功。

- [ ] **Step 2: 运行测试并确认红灯**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrOffboardingTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 完成真实 schema/事务测试并跑绿**

不要用 H2 替代；事务测试读取生产 mapper XML。`hr-offboarding-it-schema.sql` 必须自包含 System 基线表、唯一 HR 状态/授权表、`sync_sign_hr_permissions_with_plan()` 前置过程，以及一张含现有列的最小 `oa_sign_package` 表，使 Task 5 能真实执行 transfer/offboarding 迁移。Task 4 先验证事务；迁移连续执行两次的断言在 Task 5 加入，不能在迁移文件尚未创建时伪造通过。断言 `select version()` 以 `5.7.44` 开头。

- [ ] **Step 4: 提交事务测试**

```bash
git add \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java \
  erp-modules/erp-system/src/test/resources/application-hr-offboarding-it.yml \
  erp-modules/erp-system/src/test/resources/hr-offboarding-it-schema.sql
git commit -m "test: verify offboarding transaction on mysql 5.7"
```

## Task 5: 增加双份幂等迁移、权限和 OA 持久化列

**Files:**

- Create: `sql/erp_hr_offboarding_automation_20260713.sql`
- Create: `docker/mysql/db/erp_hr_offboarding_automation_20260713.sql`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrLifecycleActionMigrationTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignPersistenceContractTest.java`

- [ ] **Step 1: 先写迁移源码契约测试**

断言双份文件逐字一致、包含 `hr:employee:offboard`、包装过程调用链、六个 OA 列、MySQL 5.7 动态 DDL、没有 `ADD COLUMN IF NOT EXISTS`。

- [ ] **Step 2: 运行测试确认迁移不存在**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OffboardSignPersistenceContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 编写 MySQL 5.7 迁移**

新增列：

```sql
offboarding_type varchar(32) DEFAULT NULL,
salary_settlement_status varchar(16) DEFAULT NULL,
asset_handover_status varchar(16) DEFAULT NULL,
non_compete_decision varchar(32) DEFAULT NULL,
compensation_amount decimal(16,2) DEFAULT NULL,
compensation_note varchar(500) DEFAULT NULL
```

权限菜单只在不存在 `perms='hr:employee:offboard'` 时创建。包装过程：

```sql
DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_offboarding;
DELIMITER $$
CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()
BEGIN
    CALL sync_sign_hr_permissions_with_transfer();
    -- INSERT IGNORE 唯一 HR role_menu 与 grant
END$$
DELIMITER ;
CALL sync_sign_hr_permissions_with_offboarding();
```

把 `SysConfigMapper.syncSignHrPermissions` 的 callable 更新为该新包装过程。不要删除 transfer 包装过程，旧测试和部署仍可直接调用它。

- [ ] **Step 4: 复制为 docker 双份并比较**

使用 `apply_patch` 创建两份相同内容后运行：

```bash
cmp sql/erp_hr_offboarding_automation_20260713.sql \
  docker/mysql/db/erp_hr_offboarding_automation_20260713.sql
```

- [ ] **Step 5: 把新增迁移接入真实 MySQL 双跑测试**

此时迁移文件已经存在；在 `HrOffboardingTransactionTest.migrate()` 中按 `lifecycle -> transfer -> offboarding -> offboarding` 执行，并断言 permission/menu/六列不重复。不要在 Task 4 提前引用尚不存在的迁移。

- [ ] **Step 6: 运行迁移测试和 MySQL 事务测试**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrLifecycleActionMigrationTest,HrOffboardingTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 7: 提交迁移**

```bash
git add \
  sql/erp_hr_offboarding_automation_20260713.sql \
  docker/mysql/db/erp_hr_offboarding_automation_20260713.sql \
  erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrLifecycleActionMigrationTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignPersistenceContractTest.java
git commit -m "feat: add offboarding schema and unique hr permission"
```

## Task 6: 实现 OFFBOARD 方案规则

**Files:**

- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OffboardSignScenarioRule.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignScenarioRuleTest.java`

- [ ] **Step 1: 写规则失败测试**

覆盖：envelope、dedupeKey、标准低风险、五类离职、每个资料状态风险、历史标记、无方案、非法 rule JSON、多方案冲突、缺模板快照、模板场景错误、只有无需员工签署材料返回 `NO_ACTION`、重复事件决定一致。

事件转换必须验证：before `employeeStatus != 离职` 且 `accountStatus=0`；after `employeeStatus=离职`、`accountStatus=1`、leaveDate=effectiveDate；除离职字段外的身份/组织/岗位/合同/薪资字段不变。

- [ ] **Step 2: 运行测试确认类不存在**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OffboardSignScenarioRuleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 实现 envelope 与 dedupeKey**

```java
@Override
public String dedupeKey(HrSignBusinessEvent event)
{
    List<String> reasons = new ArrayList<>();
    validateEnvelope(event, reasons);
    if (!reasons.isEmpty())
        throw new ServiceException("离职事件不一致: " + String.join(",", reasons));
    return "OFFBOARD:" + event.getEmployeeId() + ":"
            + event.getSourceBusinessId() + ":"
            + event.getSourceEventVersion();
}
```

attributes 强校验 `actionType=OFFBOARD_CONFIRMED`、sourceActionId/version、effectiveDate、historicalSupplement、riskLevel。历史标记必须等于 `effectiveDate < occurredTime 的上海自然日`。

OA 必须从 after snapshot 用与 System 相同的公式重新派生 `LOW/HIGH`，并与 attributes.riskLevel 比较；不一致返回 `RISK_LEVEL_MISMATCH` 的 `NEEDS_DATA`，不能信任调用方自行降级风险。

- [ ] **Step 4: 实现 rule JSON 匹配**

允许字段仅为：

```text
offboardingTypes
salarySettlementStatuses
assetHandoverStatuses
nonCompeteDecisions
riskLevels
historicalSupplement
```

数组为空或字段缺失表示通配；未知字段、非数组、未知枚举或非法 boolean 使该候选无效并最终返回 `PLAN_RULE_INVALID`。匹配零个为 `PLAN_NOT_FOUND`，多个为 `PLAN_CONFLICT`。

示例已发布规则：

```json
{
  "offboardingTypes": ["VOLUNTARY_EXPECTED"],
  "salarySettlementStatuses": ["COMPLETED"],
  "assetHandoverStatuses": ["COMPLETED"],
  "nonCompeteDecisions": ["NOT_APPLICABLE"],
  "riskLevels": ["LOW"],
  "historicalSupplement": false
}
```

- [ ] **Step 5: 构造 draft package**

`draftPackage` 必须冻结 employee、dept/shop、post、entry/contract/leave date、reason、offboardingType、settlement、assets、noncompete、compensation，状态 `draft`、confirmStatus `WAITING_HR`、planVersionId。风险由 decision 写入 task，不允许自动发送。

- [ ] **Step 6: 运行规则测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OffboardSignScenarioRuleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/rule/OffboardSignScenarioRule.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignScenarioRuleTest.java
git commit -m "feat: add offboarding sign scenario rule"
```

## Task 7: 持久化离职材料并补齐文档占位符

**Files:**

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignPersistenceContractTest.java`

- [ ] **Step 1: 先扩展模板和 persistence 失败测试**

新增模板机器码：

```text
OFFBOARD_CONFIRMATION
OFFBOARD_HANDOVER
OFFBOARD_SETTLEMENT
OFFBOARD_CONFIDENTIALITY_NONCOMPETE
OFFBOARD_TERMINATION_NOTICE
```

保留 `OFFBOARD_LEAVE_CERTIFICATE`。前四类 employeeSignRequired 为 `true`；termination notice 和 leave certificate 为 `false`。

模板占位符固定如下，避免 UI fallback、模板校验和文档 values 各自命名：

| templateType | employeeSignRequired | requiredPlaceholders |
| --- | --- | --- |
| `OFFBOARD_CONFIRMATION` | true | `employeeName,employeeIdCard,entryDate,leaveDate,offboardingType,leaveReason,signDate` |
| `OFFBOARD_HANDOVER` | true | `employeeName,leaveDate,postName,assetHandoverStatus,signDate` |
| `OFFBOARD_SETTLEMENT` | true | `employeeName,employeeIdCard,leaveDate,salarySettlementStatus,compensationAmount,compensationNote,signDate` |
| `OFFBOARD_CONFIDENTIALITY_NONCOMPETE` | true | `employeeName,employeeIdCard,leaveDate,nonCompeteDecision,signDate` |
| `OFFBOARD_TERMINATION_NOTICE` | false | `employeeName,employeeIdCard,entryDate,leaveDate,offboardingType,leaveReason,companyName,signDate` |
| `OFFBOARD_LEAVE_CERTIFICATE` | false | 保留现有 `employeeName,employeeIdCard,entryDate,leaveDate,postName,companyName,signDate` |

- [ ] **Step 2: 运行失败测试**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTemplateTypeTest,OffboardSignPersistenceContractTest,OaSignTaskOrchestratorTest,OaSignDocumentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 扩展 package domain 和 mapper 的所有路径**

`OaSignPackage` 添加六个迁移字段及 getter/setter。`OaSignPackageMapper.xml` 同时更新 resultMap、baseColumns、insert、普通 update、`updateDraftOaSignPackage`；漏掉任一路径都由 persistence contract test 失败。

- [ ] **Step 4: 让 task 冻结 OFFBOARD 业务日期**

把 orchestrator 的 transfer-only 分支改成：

```java
if ("TRANSFER".equalsIgnoreCase(task.getScenario())
        || "OFFBOARD".equalsIgnoreCase(task.getScenario()))
{
    task.setBusinessEffectiveDate(lifecycleEffectiveDate(event));
    Object historical = event.getAttributes() == null ? null
            : event.getAttributes().get("historicalSupplement");
    task.setHistoricalSupplement(
            historical instanceof Boolean value ? value : null);
}
```

把 `transferEffectiveDate` 重命名为 `lifecycleEffectiveDate`，不改变调岗行为。

- [ ] **Step 5: 补齐文档值映射**

在 `OaSignDocumentService` 的 values map 增加：

```java
values.put("offboardingType", value(signPackage.getOffboardingType()));
values.put("leaveReason", value(signPackage.getLeaveReason()));
values.put("salarySettlementStatus", value(signPackage.getSalarySettlementStatus()));
values.put("assetHandoverStatus", value(signPackage.getAssetHandoverStatus()));
values.put("nonCompeteDecision", value(signPackage.getNonCompeteDecision()));
values.put("compensationAmount", money(signPackage.getCompensationAmount()));
values.put("compensationNote", value(signPackage.getCompensationNote()));
```

模板 requiredPlaceholders 必须与这些 key 精确一致。

- [ ] **Step 6: 运行 OA 专项并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTemplateTypeTest,OffboardSignScenarioRuleTest,OffboardSignPersistenceContractTest,OaSignTaskOrchestratorTest,OaSignDocumentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTemplateType.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/constant/OaSignTemplateTypeTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDocumentServiceTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/rule/OffboardSignPersistenceContractTest.java
git commit -m "feat: persist offboarding sign packages and documents"
```

## Task 8: 增加唯一 HR 离职操作界面

**Files:**

- Modify: `erp-ui/src/api/hr/employee.js`
- Create: `erp-ui/src/views/hr/components/HrOffboardingDialog.vue`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Modify: `erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Create: `erp-ui/test/hrOffboardingAutomation.test.js`

- [ ] **Step 1: 先写 Node 源码与行为失败测试**

参考 `hrEmployeeTransferEffectiveDate.test.js` 的 SFC VM harness，覆盖：

- API 路径和 `silentError:true`。
- 列表/详情只在 `hr:employee:offboard` 下出现入口，已离职员工不显示。
- 读取服务端业务日，纯 `yyyy-MM-dd` 字符串比较，不使用 `new Date()`。
- 未来日期禁用并显示固定文案。
- 标准低风险直接提交，riskConfirmation 为 null。
- 每个高风险条件先打开结构化确认，payload 含精确 statement 和所有摘要字段。
- 后端拒绝不 emit `confirmed`，requestId 保留供重试。
- 关闭再打开生成新 requestId。
- 成功刷新列表、详情和 `todo/refreshSummaries`。
- 普通 HR UI 不出现 hash。

- [ ] **Step 2: 运行测试确认文件不存在**

```bash
cd erp-ui
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
node test/hrOffboardingAutomation.test.js
```

- [ ] **Step 3: 增加 API**

```js
export const getHrOffboardingBusinessDate = () => request({
  url: "/system/hr/employee/offboard/business-date",
  method: "get"
})

export const confirmHrEmployeeOffboarding = (userId, data) => request({
  url: `/system/hr/employee/${userId}/offboard`,
  method: "post",
  data,
  silentError: true
})
```

- [ ] **Step 4: 实现对话框状态和风险镜像**

`emptyModel`：

```js
const emptyModel = () => ({
  lastWorkingDate: "",
  offboardingType: "VOLUNTARY_EXPECTED",
  reason: "",
  salarySettlementStatus: "COMPLETED",
  assetHandoverStatus: "COMPLETED",
  nonCompeteDecision: "NOT_APPLICABLE",
  compensationAmount: 0,
  compensationNote: ""
})
```

前端高风险镜像：

```js
isHighRisk() {
  return this.isHistorical ||
    this.model.offboardingType !== "VOLUNTARY_EXPECTED" ||
    this.model.salarySettlementStatus !== "COMPLETED" ||
    this.model.assetHandoverStatus !== "COMPLETED" ||
    this.model.nonCompeteDecision !== "NOT_APPLICABLE" ||
    Number(this.model.compensationAmount || 0) > 0 ||
    Boolean(String(this.model.compensationNote || "").trim())
}
```

该判断只决定是否显示确认弹窗；服务器仍重新派生并逐字段核对。

高风险弹窗必须展示员工、离职类型、最后工作日、工资结算、资产交接、竞业决定、补偿金额/说明和全部风险代码；只有勾选确认且填写 1–500 字风险原因后才能调用 API。

- [ ] **Step 5: 构造结构化 payload**

```js
riskConfirmation: this.isHighRisk ? {
  confirmed: true,
  employeeId: this.employee.userId,
  employeeName: this.employeeName,
  offboardingType: this.model.offboardingType,
  lastWorkingDate: this.model.lastWorkingDate,
  operationDate: this.businessDate,
  salarySettlementStatus: this.model.salarySettlementStatus,
  assetHandoverStatus: this.model.assetHandoverStatus,
  nonCompeteDecision: this.model.nonCompeteDecision,
  compensationAmount: Number(this.model.compensationAmount || 0),
  riskStatement: "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用",
  reason: this.riskReason
} : null
```

- [ ] **Step 6: 集成列表、详情和 OA 展示**

`HrEmployeeList` 新增 dialog 状态和 `openOffboarding/handleOffboardingConfirmed`；成功后关闭、提示“离职已确认，合同任务正在生成”、刷新列表/详情，并 dispatch `todo/refreshSummaries`。`HrProfileDetailDrawer` 新增 `allowOffboarding` prop 和 `offboard` emit。`SignTaskDetailDrawer` 显示 OFFBOARD 业务生效日、历史补录和风险；签约包页面 fallback 列出六类离职模板及新增 placeholders。

- [ ] **Step 7: 跑前端专项和调岗回归**

```bash
node test/hrOffboardingAutomation.test.js
node test/hrEmployeeTransferEffectiveDate.test.js
node test/signTaskCenter.test.js
```

Expected: 三项退出 `0`。

- [ ] **Step 8: 提交前端**

```bash
git add \
  erp-ui/src/api/hr/employee.js \
  erp-ui/src/views/hr/components/HrOffboardingDialog.vue \
  erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue \
  erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue \
  erp-ui/src/views/oa/signPackage/index.vue \
  erp-ui/test/hrOffboardingAutomation.test.js
git commit -m "feat: add unique hr offboarding workflow"
```

## Task 9: 全量验证与 Agent 1 交付

**Files:**

- Create: `docs/superpowers/verification/2026-07-13-contract-offboarding-automation.md`

- [ ] **Step 1: System 专项和全量**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am test
```

- [ ] **Step 2: OA 专项和全量**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am test
```

- [ ] **Step 3: 前端专项、全量和生产构建**

```bash
cd erp-ui
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
node test/hrOffboardingAutomation.test.js
npm test
node scripts/scan-frontend-secrets.cjs
npm run build:prod
```

Agent 3 尚未合并时，`npm test` 可以仍显示设计文档列出的九个既有失败；必须记录为“基线失败，非本分支新增”，同时确保 `hrOffboardingAutomation.test.js` 和构建通过。不能修改 Agent 3 所有权文件。

- [ ] **Step 4: 比较迁移并检查文件边界**

```bash
cd ..
cmp sql/erp_hr_offboarding_automation_20260713.sql \
  docker/mysql/db/erp_hr_offboarding_automation_20260713.sql
git diff --check codex/phase3-multi-agent-plan-20260713..HEAD
git diff --name-only codex/phase3-multi-agent-plan-20260713..HEAD
```

- [ ] **Step 5: 写并提交验证记录**

写明各模块真实测试数量、MySQL 5.7.44、未来零副作用、历史风险、并发、回滚、OA 场景矩阵、前端专项、九项既有失败（若仍存在）、未推送状态。

```bash
git add docs/superpowers/verification/2026-07-13-contract-offboarding-automation.md
git commit -m "docs: record offboarding automation verification"
```

- [ ] **Step 6: 向 Agent 1 交付**

发送：分支 `codex/offboarding-automation-20260713`、起止提交、worktree 路径、每条测试真实结果、迁移比较结果和 `git status --short --branch`。不得自行合并到 Agent 1 或 `7月12号`，不得推送远端。
