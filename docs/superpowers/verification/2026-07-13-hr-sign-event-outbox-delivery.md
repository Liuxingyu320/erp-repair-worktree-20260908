# HR 签约事件 Outbox 交付验证

**日期：** 2026-07-13

**角色：** Agent 1 / 集成负责人

**分支：** `codex/phase3-integration-outbox-20260713`

**基点：** `d330cb56b053e00206436a65e1193eee2a0e506d`

**实现 HEAD（验证记录提交前）：** `4d0e81f055088460e649f86732c43da4ed747768`

**worktree：** `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713`

## 1. 实现提交

```text
1278bbb3 feat: add transactional hr sign outbox state service
6e913dc3 feat: dispatch hr sign events with durable retries
a2c8def0 feat: rebuild sign events from lifecycle actions
f14371ff feat: compensate missing hr sign event outboxes
4d0e81f0 test: add hr sign outbox mysql 5.7 coverage
```

实现内容：

- `HrSignEventOutboxService` 使用 `REQUIRES_NEW` 完成 claim、SENT、RETRY、DEAD 的短事务状态转换，并保留 status/version 乐观条件。
- `HrSignEventDispatcher` 每 30 秒读取最多 100 条到期记录；网络调用位于短事务之外；陈旧超过 5 分钟的 `SENDING` 原行可再次 claim。
- Dispatcher 复用 `RemoteSignTaskService.publishEvent(event, SecurityConstants.INNER)`。正数 taskId 才能 SENT。
- 退避固定为 1、5、30、120、360 分钟，第六次及以后继续 360 分钟。
- 408、425、429、5xx、空响应、连接和超时重试；确定的其他 4xx 与非法 JSON 进入 DEAD。
- `HrSignEventPayloadFactory` 只从不可变 lifecycle action 和冻结 before/after snapshot 重建事件，不读取员工当前表。
- `HrSignEventCompensationScanner` 只扫描终态 lifecycle action 与 Outbox 缺口，排除 `RENEWAL_DECISION`；同 action/version 依赖唯一键只补一次。
- 损坏快照写入唯一 DEAD tombstone，避免每五分钟重复补偿。
- 现有 `RENEWAL_DECLINED` 的数据库状态是 `DECLINED`，缺口查询对此终态做了显式兼容；其他允许动作要求 `CONFIRMED`。
- 未新增调度框架，未修改 `ErpSystemApplication`，复用既有 `@EnableScheduling`。
- 现有表结构、唯一键和到期索引足够，本分支没有新增数据库迁移。

## 2. 运行时

```text
OpenJDK 17.0.18
Apache Maven 3.9.14
```

所有已执行 Maven 测试命令均显式设置：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
```

## 3. 已执行验证

### 3.1 Outbox 单元专项

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventOutboxServiceTest,HrSignEventDispatcherTest,HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实结果：退出码 `0`；`65` 项通过，`0` 失败，`0` 错误，`0` 跳过。

覆盖：批量边界、乐观抢占、状态更新冲突、taskId 正数门禁、永久/暂时 HTTP 分类、连接/超时、无限退避、非法 payload、陈旧 SENDING 查询参数、缺口补偿、唯一键竞争回读、DEAD tombstone 和敏感日志隔离。

### 3.2 生命周期事件生产者回归

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserSignHrLifecycleTest,HrOnboardingLifecycleTest,HrRenewalLifecycleTest,HrRegularizationLifecycleTest,HrEmployeeTransferLifecycleTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实结果：退出码 `0`；`87` 项通过，`0` 失败，`0` 错误，`0` 跳过。

### 3.3 Mapper 与迁移静态契约

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrLifecycleActionMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实结果：退出码 `0`；`6` 项通过，`0` 失败，`0` 错误，`0` 跳过。

### 3.4 OA 内部接口与 canonical task 幂等

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTaskInternalControllerTest,OaSignTaskOrchestratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实结果：退出码 `0`；`58` 项通过，`0` 失败，`0` 错误，`0` 跳过。

### 3.5 MySQL 测试源码编译

```bash
mvn -pl erp-modules/erp-system -am test-compile
```

真实结果：退出码 `0`。`HrSignEventOutboxMySql57Test` 和独立 Spring/MyBatis profile 编译成功。

### 3.6 代码质量与敏感日志

```bash
git diff --check codex/phase3-multi-agent-plan-20260713..HEAD
rg -n "payloadJson|beforeSnapshot|afterSnapshot|idNumber|phone|salary" \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java
```

真实结果：`git diff --check` 无输出；敏感字段搜索无输出。Dispatcher/Scanner 日志只使用 outboxId、actionId、actionType、稳定错误码和异常类名，不输出异常 message、完整 payload 或快照。

## 4. 未执行验证

按用户要求禁止启动 Colima，Docker/Testcontainers 验证未执行。

因此以下门禁保持未验证，不能从单元测试结果推断为通过：

- `HrSignEventOutboxMySql57Test` 的 MySQL 5.7.44 并发 claim、旧 version 拒绝、陈旧 SENDING、唯一键和 due 排序运行结果。
- `HrEmployeeTransferTransactionTest`、`HrOnboardingTransactionTest` 等依赖 Testcontainers 的 System 事务测试。
- `mvn -pl erp-modules/erp-system -am test` System 全量，因为默认测试集包含 Testcontainers。
- OA 停机后 action 仍提交、恢复后恰好创建一个 canonical task 的真实服务中断恢复链路。
- 调岗/离职真实唯一 HR 测试环境端到端链路。

## 5. 当前集成状态

- Agent 2 `codex/offboarding-automation-20260713`：尚未审核或合并。
- Agent 3 `codex/frontend-baseline-gates-20260713`：尚未审核或合并。
- 未推送远端。
- 未合并到 `7月12号`。
- 原始脏工作树未修改、未清理、未切分支。
