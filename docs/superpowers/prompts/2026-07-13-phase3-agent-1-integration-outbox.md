# Agent 1 窗口提示词：Outbox 与唯一集成负责人

你是 ERP 合同自动化阶段 3 的 Agent 1，同时是唯一集成负责人。请直接执行，不要只复述计划。

仓库原始工作树是 `/Users/liuxingyu/Desktop/备份/ERP-NEW`，其中有用户自己的未提交改动，禁止清理、重置、切分支或覆盖。你的目标分支是 `codex/phase3-integration-outbox-20260713`，目标 worktree 是 `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713`。如果该 worktree 尚不存在，使用 `using-git-worktrees` 技能从 `codex/phase3-multi-agent-plan-20260713` 的最终 HEAD 创建；如果已经存在，先确认它在正确分支且干净。遇到半检出或 `mmap failed` 时停止在该目录开发，安全重建，不要用 `git reset --hard`。

开始前完整阅读：

1. `docs/superpowers/specs/2026-07-13-contract-phase3-multi-agent-delivery-design.md`
2. `docs/superpowers/plans/2026-07-13-contract-phase3-multi-agent-delivery.md`
3. `docs/superpowers/plans/2026-07-13-hr-sign-event-outbox-delivery.md`

使用 `executing-plans`、`test-driven-development` 和 `verification-before-completion` 技能；不要再派生子 Agent。严格按 Outbox 子计划的 checkbox 顺序执行，每一任务先写失败测试、确认预期红灯、做最小实现、跑绿、独立提交。不要修改 Agent 2 的离职专属文件或 Agent 3 的九项前端文件。

不可变要求：

- 复用现有 `RemoteSignTaskService.publishEvent(event, SecurityConstants.INNER)` 和 OA `/signTask/inner/events`。
- 网络调用不持有业务事务；claim/mark 使用 `REQUIRES_NEW` 和 status/version 乐观条件。
- 退避为 1/5/30/120/360 分钟，之后持续 360；408/425/429/5xx/连接/超时重试，确定的其他 4xx 和非法 payload 才 DEAD。
- OA 成功但 taskId 非正数不能 SENT。
- 补偿只扫描不可变 lifecycle action + Outbox，不扫描员工当前表；排除 `RENEWAL_DECISION`。
- 日志和 last_error 不得含完整 payload 或敏感快照。
- 不新增调度框架，`ErpSystemApplication` 已有 `@EnableScheduling`。

完成 Outbox 计划后，先提交验证记录并报告 Outbox 分支 HEAD。随后检查 Agent 2 `codex/offboarding-automation-20260713` 和 Agent 3 `codex/frontend-baseline-gates-20260713` 是否都已有最终验证提交且 worktree 干净；未交付时停在“等待集成”检查点，不猜测或代做它们的工作。两者就绪后，继续执行总控计划：先审核/合并 Agent 2、跑 System/OA 组合专项；再审核/合并 Agent 3、跑前端专项；最后跑迁移双写/双跑、System/OA/前端全量、生产构建和 Capacitor 同步，写最终验证记录。

禁止推送远端、禁止合并到 `7月12号`、禁止触碰原始脏工作树；只有用户再次明确授权才可以做这些动作。

每次阶段性交付按以下格式报告：

```text
角色：Agent 1 / 集成负责人
分支：
worktree：
HEAD：
已完成任务：
新增/修改文件：
测试命令与真实结果：
MySQL 版本与结果：
Agent 2/3 集成状态：
未完成或阻断：
git status：
推送/主分支合并：未执行
```
