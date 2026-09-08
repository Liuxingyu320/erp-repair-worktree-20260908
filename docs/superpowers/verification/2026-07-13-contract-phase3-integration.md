# ERP 合同自动化阶段 3 集成验收记录

- 日期：2026-07-13（Asia/Shanghai）
- 角色：Agent 1 / 唯一集成负责人
- 分支：`codex/phase3-integration-outbox-20260713`
- worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713`
- 集成基点：`d330cb56b053e00206436a65e1193eee2a0e506d`
- Agent 2 交付：`dcaff0504fd3b0cbc1d02417779690aaa0788320`
- Agent 2 合并提交：`4fe238998f895fc0399d74f2fd8e706cda65e073`
- Agent 3 交付：`95809004b0554321c25e9270efc288756458d37e`
- Agent 3 合并提交：`7fb0c5a06d98f97de5702b777fc505b8b6f3f971`

## 集成结论

Outbox 分发与补偿、离职自动化、OA 离职签约规则和前端九项基线已经按规定顺序合并到唯一集成分支。Agent 2 合并时仅 `SysHrLifecycleActionMapper.java` 与对应 XML 发生冲突，逐段保留了离职锁定查询和 Outbox 补偿查询；没有采用整文件 ours/theirs。Agent 3 无冲突合并。

合并后的 System/OA 组合专项、System 非 Docker 全量、OA 全量、前端全量、生产构建、密钥扫描和 Capacitor 同步均通过。未推送远端，未合并到 `7月12号`，未触碰原始脏工作树。

## Outbox 交付

- Dispatcher 定时领取 `PENDING/RETRY`，claim/mark 使用 `REQUIRES_NEW` 与 status/version 乐观条件；网络调用在事务外执行。
- 复用 `RemoteSignTaskService.publishEvent(event, SecurityConstants.INNER)` 与 OA `/signTask/inner/events`。
- 408/425/429/5xx、连接和超时按 1/5/30/120/360 分钟退避，之后保持 360 分钟；确定的其他 4xx 和非法 payload 进入 `DEAD`。
- OA 成功但 taskId 非正数不会标记 `SENT`。
- 补偿只读取不可变 lifecycle action 与 Outbox，不扫描员工当前表；排除 `RENEWAL_DECISION`；同 action/version 依赖唯一键只补一次。
- 日志和 `last_error` 只保存安全摘要，不记录完整 payload 或敏感快照。
- Agent 1 Outbox 独立提交区间：`1278bbb3..37b7254d`；独立验证记录为 `2026-07-13-hr-sign-event-outbox-delivery.md`。

## 真实验证结果

Java 使用 OpenJDK 17；前端使用 Node `v24.14.0`。

| 范围 | 命令摘要 | 结果 |
| --- | --- | --- |
| Agent 1 Outbox 单元专项 | `HrSignEventOutboxServiceTest,HrSignEventDispatcherTest,HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest` | 65 项通过 |
| Agent 2 + Outbox System 组合 | `HrSignEventOutboxServiceTest,HrSignEventDispatcherTest,HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest,HrOffboardingConfirmRequestTest,HrOffboardingLifecycleTest,HrLifecycleActionMigrationTest` | 86 项，0 失败，0 错误，0 跳过 |
| Agent 2 + Outbox OA 组合 | `OffboardSignScenarioRuleTest,OffboardSignPersistenceContractTest,OaSignTaskOrchestratorTest,OaSignDocumentServiceTest` | 86 项，0 失败，0 错误，0 跳过 |
| System 非 Docker 全量 | `-Dtest="*,!HrEmployeeTransferTransactionTest,!HrOnboardingTransactionTest,!HrOffboardingTransactionTest,!HrSignEventOutboxMySql57Test"` | System 模块 692 项，0 失败，0 错误，0 跳过 |
| OA 全量 | `mvn -pl erp-modules/erp-oa -am test` | OA 模块 618 项，0 失败，0 错误，0 跳过 |
| 离职 + 九项前端专项 | 10 个 `node test/*.test.js` 入口 | 全部退出码 0 |
| 前端全量 | `npm test` | 143 个测试入口，0 失败 |
| 前端密钥扫描 | `node scripts/scan-frontend-secrets.cjs` | 通过 |
| 生产构建 | `npm run build:prod` | 退出码 0；存在入口 1.68 MiB 高于建议 1.66 MiB 的警告 |
| Capacitor 同步 | `npm run app:sync` | Android/iOS 同步成功；`Capacitor bundled web assets match dist` |
| SQL 镜像一致性 | `cmp sql/erp_hr_offboarding_automation_20260713.sql docker/mysql/db/erp_hr_offboarding_automation_20260713.sql` | 退出码 0 |
| 差异与凭据边界 | `git diff --check` 及生成物/凭据路径扫描 | 通过，无敏感文件进入差异 |

`npm test` 中的 `dockerScripts.test.js` 仅检查脚本文本契约，没有执行 Docker、Testcontainers 或 Colima。为严格遵守用户后续禁令，没有再次执行会间接包含该测试的 `npm run app:verify`；其前端全量部分已由上表 `npm test` 覆盖，原生资产一致性部分已由 `npm run app:sync` 末尾的校验覆盖。

## MySQL 5.7 与用户禁令

目标数据库版本为 MySQL `5.7.44`。在本轮最终组合验证阶段，**按用户要求禁止启动 Colima，Docker/Testcontainers 验证未执行**；没有执行真实 MySQL 迁移连续双跑，也没有运行四个 Testcontainers 事务/并发类。

禁令下达前的 TDD 红灯遗留报告显示，`HrSignEventOutboxMySql57Test` 曾因测试 SQL 迁移器未识别 `DELIMITER` 而失败。该失败发生于 04:12--04:13，修正换行解析的当前提交 `4d0e81f0` 完成于 04:14；由于随后用户禁止容器验证，当前修正只有源码编译证据，没有 MySQL 5.7.44 运行态复验证据。历史红灯不计作当前 HEAD 通过。

## 最终迁移与部署顺序

1. 确认阶段 1、阶段 2以及既有基础迁移已部署，至少包括生命周期 `erp_hr_lifecycle_action_20260711.sql` 和方案版本 `erp_oa_sign_plan_version_20260711.sql`。
2. 执行调岗迁移 `sql/erp_hr_transfer_effective_date_20260713.sql`。
3. Agent 1 未新增 Outbox 增量 SQL；现有 lifecycle migration 已包含所需 Outbox 表、状态、version、due 索引和 action/version 唯一键，因此本步无脚本。
4. 执行离职迁移 `sql/erp_hr_offboarding_automation_20260713.sql`；Docker 初始化镜像中的同名文件与其逐字一致。
5. 部署 OA，使 `/signTask/inner/events` 和离职签约规则先就绪。
6. 部署 System，启用 lifecycle/Outbox 生产、Dispatcher 和补偿扫描。
7. 部署前端，随后发布需要的 Android/iOS 原生壳资源；生产发布环境补齐未提交的推送凭据。

应用版本回滚时保留新增列、权限和 Outbox 数据，不执行破坏性降级 SQL。获准使用 MySQL 5.7.44 后，应按 `lifecycle -> transfer -> offboarding -> offboarding` 连续执行并校验行数、权限和唯一键不重复。

## 风险与未执行项

- 缺少当前 HEAD 的真实 MySQL 5.7.44 证据：并发 claim、旧 version 拒绝、陈旧 `SENDING` 恢复、唯一 action/version、调岗/离职事务回滚和迁移双跑仍需补验。
- OA 网络中断、服务进程重启后的真实端到端恢复未执行；当前证据来自 Dispatcher/补偿/OA 幂等单元和组合测试。
- `npm run app:verify` 按用户禁令跳过；`app:sync` 已证明同步资产一致，但 release 推送凭据在本机不存在，仅产生预期警告。
- 生产前端入口约 1.68 MiB，略高于 1.66 MiB 建议值，属于性能容量风险，不阻塞本次功能验收。
- 未进行远端推送、生产部署或与 `7月12号` 的主分支合并；这些动作必须再次取得用户明确授权。
