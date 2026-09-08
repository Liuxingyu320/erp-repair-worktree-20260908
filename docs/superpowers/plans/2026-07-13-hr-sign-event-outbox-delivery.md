# HR 签约事件 Outbox 最终投递 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 System 模块补齐 `sys_hr_sign_event_outbox` 的可靠领取、OA 投递、无限退避重试、永久错误死信和缺失 Outbox 补偿，使已确认的人事生命周期 action 最终且幂等地生成一个 OA canonical task。

**Architecture:** 调度器只负责批量读取、短事务抢占和远程调用；`HrSignEventOutboxService` 用 `REQUIRES_NEW` 提交每次状态转换，网络调用不持有数据库事务。补偿扫描器只读取不可变 lifecycle action，通过 `HrSignEventPayloadFactory` 重建缺失 payload，并依靠 `(action_id,event_version)` 唯一键消除竞争。OA 仍使用现有 `RemoteSignTaskService.publishEvent` 和 `/signTask/inner/events`。

**Tech Stack:** Java 17、Spring Boot、Spring Scheduling、MyBatis、OpenFeign、Jackson、JUnit 5、Mockito、AssertJ、Testcontainers MySQL 5.7.44。

---

## 0. 边界与固定契约

- 执行分支：`codex/phase3-integration-outbox-20260713`。
- 执行 worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713`。
- `ErpSystemApplication` 已有 `@EnableScheduling`，禁止重复添加。
- 现有 mapper 的 `claimForSending`、`markSent`、`markRetry`、`markDead` 已使用 `status + version` 乐观条件；保留该语义。
- 成功响应必须同时满足 `R.isSuccess(response)`、`data != null` 和 `data > 0`。
- `408`、`425`、`429`、所有 `5xx`、空响应、连接失败和超时均重试；其他确定 `4xx` 才进入 `DEAD`。
- 退避分钟为 `1, 5, 30, 120, 360`；第六次及以后继续 `360` 分钟，不因次数耗尽进入 `DEAD`。
- 日志和 `last_error` 只能包含 actionId/outboxId、异常类型和稳定错误码，不能包含 payload、快照、姓名、证件、手机号、地址或薪资。
- 本计划预计不需要数据库迁移；若测试证明现有列或索引不足，先记录证据并与设计契约核对，不能顺手改表。

## Task 1: 为短事务 Outbox 状态服务写失败测试

**Files:**

- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxServiceTest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventOutboxService.java`

- [ ] **Step 1: 写状态服务测试**

测试至少覆盖：批量上限被限制为 `100`；抢占成功后本地 row 变为 `SENDING` 且 version `+1`；抢占失败不修改 row；成功、重试、死信都使用抢占后的 version；状态更新影响行数不是 `1` 时抛稳定异常；错误摘要最多 `1000` 字符。

核心测试结构：

```java
@ExtendWith(MockitoExtension.class)
class HrSignEventOutboxServiceTest
{
    @Mock private SysHrSignEventOutboxMapper mapper;
    private HrSignEventOutboxService service;

    @BeforeEach
    void setUp()
    {
        service = new HrSignEventOutboxService(mapper);
    }

    @Test
    void claimCommitsOptimisticVersionLocally()
    {
        SysHrSignEventOutbox row = row("RETRY", 4L);
        when(mapper.claimForSending(11L, "RETRY", 4L)).thenReturn(1);

        assertThat(service.claim(row)).isTrue();
        assertThat(row.getStatus()).isEqualTo("SENDING");
        assertThat(row.getVersion()).isEqualTo(5L);
    }

    @Test
    void markRetryNeverSilentlyLosesAStateTransition()
    {
        SysHrSignEventOutbox row = row("SENDING", 5L);
        when(mapper.markRetry(eq(11L), eq(5L), eq(1), any(Date.class),
                eq(503), eq("REMOTE_HTTP_503"))).thenReturn(0);

        assertThatThrownBy(() -> service.markRetry(row, 1, new Date(),
                503, "REMOTE_HTTP_503"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("签约事件发件箱状态已变化，请等待下一轮调度");
    }
}
```

- [ ] **Step 2: 运行测试并确认因类不存在而失败**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventOutboxServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 编译失败，明确指出 `HrSignEventOutboxService` 不存在；不能是环境或其他测试失败。

- [ ] **Step 3: 实现最小短事务服务**

核心实现必须保持以下形态：

```java
@Service
public class HrSignEventOutboxService
{
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ERROR_LENGTH = 1000;
    private final SysHrSignEventOutboxMapper mapper;

    public List<SysHrSignEventOutbox> selectDue(Date dueTime,
            Date staleSendingBefore, int limit)
    {
        if (dueTime == null || staleSendingBefore == null)
            throw new ServiceException("签约事件调度时间不能为空");
        return mapper.selectDueOutboxes(dueTime, staleSendingBefore,
                Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean claim(SysHrSignEventOutbox row)
    {
        if (row == null || row.getOutboxId() == null || row.getVersion() == null
                || StringUtils.isBlank(row.getStatus())) return false;
        int affected = mapper.claimForSending(
                row.getOutboxId(), row.getStatus(), row.getVersion());
        if (affected != 1) return false;
        row.setStatus("SENDING");
        row.setVersion(row.getVersion() + 1);
        return true;
    }
}
```

`markSent`、`markRetry`、`markDead` 都加 `REQUIRES_NEW`，调用 mapper 后用同一个 `assertUpdated` 检查影响行数，再同步本地 row 的 status、version、retryCount、nextRetryTime、lastHttpStatus、lastError、remoteTaskId。

- [ ] **Step 4: 运行测试确认通过**

重复 Step 2 命令。Expected: `HrSignEventOutboxServiceTest` 全绿。

- [ ] **Step 5: 提交状态服务**

```bash
git add \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventOutboxService.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxServiceTest.java
git commit -m "feat: add transactional hr sign outbox state service"
```

## Task 2: 实现 Outbox dispatcher 和错误分类

**Files:**

- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventDispatcherTest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java`

- [ ] **Step 1: 先写 dispatcher 失败测试**

覆盖以下行为：

1. `dispatchDue()` 使用当前时刻、5 分钟陈旧阈值、100 条上限。
2. 未抢占成功不调用 OA。
3. 合法 JSON + `R.ok(positiveTaskId)` 标记 `SENT`，HTTP 状态记 `200`。
4. 成功码但 taskId 为 null/0/负数进入重试。
5. `400/401/403/404/409/422` 进入 `DEAD`；`408/425/429` 重试。
6. `500/502/503/504`、null response、Feign 异常、超时和普通连接异常重试。
7. 非法 JSON 进入 `DEAD`，错误码固定为 `INVALID_PAYLOAD`。
8. 重试次数从 `0` 依次得到 `1/5/30/120/360/360` 分钟。
9. 捕获日志不得包含测试 payload 中的 `310101199001010019` 或 `13800000000`。
10. `markSent/markRetry/markDead` 自身报告乐观锁冲突时，不得再把该状态异常分类成远程失败并尝试第二次状态转换。

固定时钟测试结构：

```java
private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

@BeforeEach
void setUp()
{
    dispatcher = new HrSignEventDispatcher(outboxService, remoteSignTaskService,
            objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
}

@Test
void rateLimitUsesFirstBackoffInsteadOfDeadLetter()
{
    SysHrSignEventOutbox row = dueRow(validPayload(), 0);
    when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
    when(outboxService.claim(row)).thenReturn(true);
    when(remoteSignTaskService.publishEvent(any(), eq(SecurityConstants.INNER)))
            .thenReturn(R.fail(429, "too many requests"));

    dispatcher.dispatchDue();

    verify(outboxService).markRetry(row, 1,
            Date.from(NOW.plus(1, ChronoUnit.MINUTES)),
            429, "REMOTE_HTTP_429");
    verify(outboxService, never()).markDead(any(), any(), any());
}
```

- [ ] **Step 2: 运行测试确认红灯**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventDispatcherTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 仅因 `HrSignEventDispatcher` 不存在或行为未实现而失败。

- [ ] **Step 3: 实现调度入口和单条处理**

核心实现：

```java
@Service
public class HrSignEventDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(HrSignEventDispatcher.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360 };

    @Scheduled(fixedDelayString = "${hr.sign.outbox.dispatch-fixed-delay-ms:30000}")
    public void dispatchDue()
    {
        Instant now = clock.instant();
        List<SysHrSignEventOutbox> rows = outboxService.selectDue(
                Date.from(now), Date.from(now.minus(5, ChronoUnit.MINUTES)), 100);
        for (SysHrSignEventOutbox row : rows) dispatchOne(row, now);
    }

    private void scheduleRetry(SysHrSignEventOutbox row, Instant now,
            Integer httpStatus, String errorCode)
    {
        int previous = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int delay = RETRY_MINUTES[Math.min(previous, RETRY_MINUTES.length - 1)];
        outboxService.markRetry(row, previous + 1,
                Date.from(now.plus(delay, ChronoUnit.MINUTES)),
                httpStatus, errorCode);
    }

    static boolean isPermanentHttpStatus(int status)
    {
        return status >= 400 && status < 500
                && status != 408 && status != 425 && status != 429;
    }
}
```

`dispatchOne` 的顺序固定为：claim → Jackson 反序列化 `HrSignBusinessEvent` → `publishEvent(event, SecurityConstants.INNER)` → 分类响应 → 状态更新。反序列化异常只写 `INVALID_PAYLOAD`；远程响应错误只写 `REMOTE_HTTP_<code>`、`REMOTE_EMPTY_TASK_ID`、`REMOTE_UNAVAILABLE` 或 `REMOTE_TIMEOUT`。不要把 `response.getMsg()` 原样存入数据库或日志。

不要用一个包住全部流程的宽泛 `catch (RuntimeException)`：分别只包住 payload 解析和远程调用；`markSent/markRetry/markDead` 在这些 catch 之外执行。否则一个乐观锁冲突会被误分类成 OA 不可用，并触发第二次无效状态更新。

- [ ] **Step 4: 运行 dispatcher 测试**

重复 Step 2。Expected: 所有分类和退避测试通过。

- [ ] **Step 5: 对照现有 Feign fallback 做一次契约测试**

更新 `HrSignEventDispatcherTest`，模拟 `RemoteSignTaskFallbackFactory` 的 `R.fail(responseCode, ...)` 返回值，确认 HTTP code 没有被误当成业务成功码。再次运行测试。

- [ ] **Step 6: 提交 dispatcher**

```bash
git add \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventDispatcherTest.java
git commit -m "feat: dispatch hr sign events with durable retries"
```

## Task 3: 从不可变 action 重建标准业务事件

**Files:**

- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventPayloadFactoryTest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventPayloadFactory.java`

- [ ] **Step 1: 写各 action 类型的 payload factory 测试**

覆盖映射表：

| actionType | scenario | 特有 attributes |
| --- | --- | --- |
| `ONBOARD_CONFIRMED` | `ONBOARD` | `actionType` |
| `REGULARIZATION_CONFIRMED` | `REGULARIZE` | `actionType,sourceActionId,sourceActionVersion` |
| `TRANSFER_CONFIRMED` | `TRANSFER` | `effectiveDate,actualConfirmTime,historicalSupplement,riskLevel,historicalReason` |
| `RENEWAL_CONFIRMED` | `RENEWAL` | `decision=RENEW,oldContractEndDate,oldRenewalCount,newRenewalCount` |
| `RENEWAL_DECLINED` | `RENEWAL` | `decision=DECLINE,oldContractEndDate,oldRenewalCount,newRenewalCount` |
| `OFFBOARD_CONFIRMED` | `OFFBOARD` | 通用 action 元数据、`effectiveDate,historicalSupplement,riskLevel,historicalReason`；离职业务字段从 after snapshot 读取 |

还要验证：`RENEWAL_DECISION` 和未知 action 被拒绝；损坏快照以 factory 专用 `InvalidLifecycleActionException` 被拒绝；`eventId` 稳定为 `HR-ACTION:<actionId>:<version>`；sourceType 固定 `HR_LIFECYCLE_ACTION`；sourceBusinessId 是十进制 actionId；occurredTime 优先 actualConfirmTime、否则 createTime。

示例断言：

```java
HrSignBusinessEvent event = factory.fromAction(transferAction);

assertThat(event.getEventId()).isEqualTo("HR-ACTION:91:1");
assertThat(event.getScenario()).isEqualTo("TRANSFER");
assertThat(event.getSourceType()).isEqualTo("HR_LIFECYCLE_ACTION");
assertThat(event.getSourceBusinessId()).isEqualTo("91");
assertThat(event.getAttributes()).containsEntry("sourceActionId", 91L)
        .containsEntry("sourceActionVersion", 1L)
        .containsEntry("effectiveDate", "2026-07-12")
        .containsEntry("historicalSupplement", true);
```

- [ ] **Step 2: 运行测试确认 factory 尚不存在**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventPayloadFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 实现严格映射和公共 envelope**

核心 switch：

```java
String scenario = switch (action.getActionType())
{
    case "ONBOARD_CONFIRMED" -> "ONBOARD";
    case "REGULARIZATION_CONFIRMED" -> "REGULARIZE";
    case "TRANSFER_CONFIRMED" -> "TRANSFER";
    case "RENEWAL_CONFIRMED", "RENEWAL_DECLINED" -> "RENEWAL";
    case "OFFBOARD_CONFIRMED" -> "OFFBOARD";
    default -> throw new ServiceException("生命周期动作不能生成签约事件");
};
```

公共 attributes 至少包含：

```java
attributes.put("actionType", action.getActionType());
attributes.put("sourceActionId", action.getActionId());
attributes.put("sourceActionVersion", action.getVersion());
```

历史判断使用 `Asia/Shanghai`：

```java
LocalDate operationDate = LocalDate.ofInstant(occurred.toInstant(),
        ZoneId.of("Asia/Shanghai"));
boolean historical = action.getEffectiveDate() != null
        && action.getEffectiveDate().isBefore(operationDate);
```

续签的 old/new count 与 old end date必须从 before/after snapshot 推导，不能从当前员工表读取。使用 Jackson writer 关闭 `WRITE_DATES_AS_TIMESTAMPS`，保证与现有生命周期 payload 日期格式一致。

- [ ] **Step 4: 运行 factory 测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventPayloadFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventPayloadFactory.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventPayloadFactoryTest.java
git commit -m "feat: rebuild sign events from lifecycle actions"
```

## Task 4: 补偿缺失 Outbox，且不扫描员工当前状态

**Files:**

- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventCompensationScannerTest.java`

- [ ] **Step 1: 写 mapper 与 scanner 的失败测试**

测试必须证明：

- 查询只返回 `business_status='CONFIRMED'` 且 action 类型在允许列表、同时不存在同 action/version Outbox 的记录。
- `RENEWAL_DECISION` 不会进入补偿列表。
- 扫描上限为 100，按 actionId 升序。
- 插入 payload 的状态为 `PENDING`、retryCount `0`、version `0`。
- 两个补偿线程竞争时，一个插入、另一个捕获唯一键并读取既有行，不产生失败告警。
- 单个 action 快照损坏会插入唯一 `DEAD` tombstone（payload `{}`、lastError `INVALID_ACTION_PAYLOAD`），不会每五分钟无限重复，也不会阻止同批次后续 action。
- scanner 从不依赖 `SysUserProfileMapper` 或员工表。

- [ ] **Step 2: 运行测试确认红灯**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventCompensationScannerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 增加缺口查询**

Java 方法：

```java
List<SysHrLifecycleAction> selectConfirmedActionsWithoutOutbox(
        @Param("limit") int limit);
```

MyBatis SQL：

```xml
<select id="selectConfirmedActionsWithoutOutbox"
        resultMap="SysHrLifecycleActionResult">
    select <include refid="actionColumns"/>
    from sys_hr_lifecycle_action a
    where a.business_status = 'CONFIRMED'
      and a.action_type in (
        'ONBOARD_CONFIRMED', 'REGULARIZATION_CONFIRMED',
        'TRANSFER_CONFIRMED', 'RENEWAL_CONFIRMED',
        'RENEWAL_DECLINED', 'OFFBOARD_CONFIRMED'
      )
      and not exists (
        select 1
        from sys_hr_sign_event_outbox o
        where o.action_id = a.action_id
          and o.event_version = a.version
      )
    order by a.action_id asc
    limit #{limit}
</select>
```

这里使用 `not exists`，让现有未限定前缀的 `actionColumns` 只处于单表外层作用域，避免与 Outbox 的 `action_id/version/create_time` 产生歧义。

- [ ] **Step 4: 实现独立补偿事务**

`HrSignEventCompensationService.ensureOutbox(action)` 必须使用 `REQUIRES_NEW + READ_COMMITTED`；这样并发 insert 等待唯一键赢家提交后，catch 分支的普通 SELECT 能看到赢家，不会被 MySQL 默认 repeatable-read 的旧快照遮蔽。核心逻辑：

```java
@Transactional(propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public SysHrSignEventOutbox ensureOutbox(SysHrLifecycleAction action)
{
    SysHrSignEventOutbox existing = mapper.selectByActionAndEventVersion(
            action.getActionId(), action.getVersion());
    if (existing != null) return existing;

    SysHrSignEventOutbox row;
    try
    {
        HrSignBusinessEvent event = payloadFactory.fromAction(action);
        row = pending(action, writeJson(event));
    }
    catch (HrSignEventPayloadFactory.InvalidLifecycleActionException invalid)
    {
        row = dead(action, "{}", "INVALID_ACTION_PAYLOAD");
    }
    try
    {
        if (mapper.insertOutbox(row) != 1)
            throw new ServiceException("补偿签约事件发件箱写入失败");
        return row;
    }
    catch (DuplicateKeyException competition)
    {
        SysHrSignEventOutbox winner = mapper.selectByActionAndEventVersion(
                action.getActionId(), action.getVersion());
        if (winner != null) return winner;
        throw competition;
    }
}
```

`pending` 和 `dead` 共用同一 insert-or-select 竞争处理；DEAD tombstone 的 `retryCount=0/version=0/nextRetryTime=null`，日志只记录 actionId/actionType 和稳定错误码。

- [ ] **Step 5: 实现定时 scanner**

```java
@Scheduled(
    initialDelayString = "${hr.sign.outbox.compensation-initial-delay-ms:60000}",
    fixedDelayString = "${hr.sign.outbox.compensation-fixed-delay-ms:300000}")
public void compensateMissingOutboxes()
{
    List<SysHrLifecycleAction> actions =
            actionMapper.selectConfirmedActionsWithoutOutbox(100);
    for (SysHrLifecycleAction action : actions)
    {
        try { compensationService.ensureOutbox(action); }
        catch (RuntimeException failure)
        {
            log.error("补偿签约事件失败，actionId={}, actionType={}, errorType={}",
                    action.getActionId(), action.getActionType(),
                    failure.getClass().getSimpleName());
        }
    }
}
```

日志不可传入 `failure` 对象作为最后参数，否则堆栈 message 可能携带 payload 内容；只记录异常类名。

- [ ] **Step 6: 运行测试并提交**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

git add \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrLifecycleActionMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysHrLifecycleActionMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventCompensationScannerTest.java
git commit -m "feat: compensate missing hr sign event outboxes"
```

## Task 5: 用真实 MySQL 5.7 验证抢占、陈旧恢复和唯一键

**Files:**

- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java`
- Create: `erp-modules/erp-system/src/test/resources/application-hr-sign-outbox-it.yml`
- Reuse: `erp-modules/erp-system/src/test/resources/hr-transfer-it-schema.sql`
- Reuse: `sql/erp_hr_lifecycle_action_20260711.sql`

- [ ] **Step 1: 写 Testcontainers 失败测试**

容器固定：

```java
@Container
static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
        .withDatabaseName("hr_sign_outbox_it")
        .withUsername("hr_outbox_it")
        .withPassword("hr_outbox_it_password")
        .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
        .withCommand("--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_unicode_ci");
```

测试至少覆盖：

- 两个线程用同一个 `(outboxId,status,version)` claim，影响行数总和恰好为 1。
- 成功 claim 后只有新 version 能 `markSent`。
- `SENDING.update_time` 早于 5 分钟会重新出现在 due 查询，较新的不会。
- 相同 `(action_id,event_version)` 插入第二行触发唯一键；补偿 service 返回第一行。
- due 排序为 `coalesce(next_retry_time,create_time),outbox_id`。
- 数据库版本查询 `select version()` 以 `5.7.44` 开头。

- [ ] **Step 2: 运行测试确认未实现或配置缺失**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventOutboxMySql57Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: 完成测试配置，运行到全绿**

配置只扫描 `SysHrLifecycleActionMapper` 和 `SysHrSignEventOutboxMapper` 所在包，使用 `DataSourceTransactionManager` 和生产 mapper XML。迁移在 `@BeforeAll` 中执行现有生命周期 SQL，不复制生产 DDL 到测试断言里。

- [ ] **Step 4: 提交 MySQL 证据**

```bash
git add \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java \
  erp-modules/erp-system/src/test/resources/application-hr-sign-outbox-it.yml
git commit -m "test: verify hr sign outbox on mysql 5.7"
```

## Task 6: 回归现有生命周期 payload 与 OA 内部接口

**Files:**

- Modify only if a real regression is found: existing lifecycle/remote API tests

- [ ] **Step 1: 运行所有 Outbox 专项**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventOutboxServiceTest,HrSignEventDispatcherTest,HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest,HrSignEventOutboxMySql57Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 2: 回归五类生命周期事件生产者**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserSignHrLifecycleTest,HrEmployeeTransferLifecycleTest,HrEmployeeTransferTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 入职、续签、转正、调岗现有测试仍通过，Outbox payload 未改变。

- [ ] **Step 3: 回归 OA 内部 controller 契约**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTaskInternalControllerTest,OaSignTaskOrchestratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `/signTask/inner/events` 仍返回 canonical taskId，重复事件返回相同 taskId。

## Task 7: 全量验证、记录和集成交接

**Files:**

- Create: `docs/superpowers/verification/2026-07-13-hr-sign-event-outbox-delivery.md`

- [ ] **Step 1: 运行 System 全量**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am test
```

- [ ] **Step 2: 检查代码质量和敏感日志**

```bash
git diff --check codex/phase3-multi-agent-plan-20260713..HEAD
rg -n "payloadJson|beforeSnapshot|afterSnapshot|idNumber|phone|salary" \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java
```

Expected: `git diff --check` 无输出；搜索结果只能出现在反序列化变量或禁止输出的测试断言中，不能位于日志模板参数。

- [ ] **Step 3: 写验证记录**

记录真实命令、测试数量、MySQL 版本、状态机覆盖、退避结果、敏感日志检查、分支和提交。明确说明“未推送、尚未合并 Agent 2/3”。

- [ ] **Step 4: 提交验证记录**

```bash
git add docs/superpowers/verification/2026-07-13-hr-sign-event-outbox-delivery.md
git commit -m "docs: record hr sign outbox verification"
```

- [ ] **Step 5: 执行总控计划的合并职责**

回到 `docs/superpowers/plans/2026-07-13-contract-phase3-multi-agent-delivery.md` 的 Task 3，审核并合并 Agent 2；再执行 Task 4 合并 Agent 3。没有用户明确授权时，不推送、不合并到 `7月12号`。
