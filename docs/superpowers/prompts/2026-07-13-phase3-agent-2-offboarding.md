# Agent 2 窗口提示词：离职合同自动化

你是 ERP 合同自动化阶段 3 的 Agent 2，只负责离职场景。请直接执行完整计划，不要只给建议。

原始工作树 `/Users/liuxingyu/Desktop/备份/ERP-NEW` 含用户未提交改动，禁止清理、重置、切分支或覆盖。你的分支是 `codex/offboarding-automation-20260713`，worktree 是 `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713`。如未创建，使用 `using-git-worktrees` 技能从 `codex/phase3-multi-agent-plan-20260713` 最终 HEAD 创建；如已存在，确认分支、基点、干净状态。不得在半检出 worktree 开发，不得使用 `git reset --hard`。

开始前完整阅读：

1. `docs/superpowers/specs/2026-07-13-contract-phase3-multi-agent-delivery-design.md`
2. `docs/superpowers/plans/2026-07-13-contract-offboarding-automation.md`
3. 总控计划中 Agent 2 的文件边界、跨 Agent 契约与合并协议。

使用 `executing-plans`、`test-driven-development` 和 `verification-before-completion` 技能；不要派生子 Agent。逐 checkbox 执行：先失败测试、确认红灯、最小实现、跑绿、独立提交。所有日期规则使用服务端 Asia/Shanghai 自然日，未来最后工作日必须以固定文案拒绝且零副作用；不实现未来调度生效。

不可变业务要求：

- 权限 `hr:employee:offboard`，并且只有 `sign.hr.user-id` 配置的唯一 HR 本人可执行，管理员不绕过。
- 一次事务内锁员工，条件更新 `employee_status=离职`、`leave_date`、`sys_user.status=1`，写不可变 `OFFBOARD_CONFIRMED` action 和 PENDING Outbox；任何一步失败全回滚。
- 不删除 `sys_user_post`、角色或门店授权。
- requestId 严格幂等；同员工同最后工作日并发只有一个新 action。
- 服务端按计划列出的精确公式派生 HIGH；HIGH 必须核对结构化二次确认。
- event 为 `OFFBOARD` / `HR_LIFECYCLE_ACTION`，sourceBusinessId 是 actionId。
- OA 规则只读冻结 before/after，不回查当前员工；dedupeKey 精确为 `OFFBOARD:<employeeId>:<actionId>:<version>`。
- 所有材料停在单 HR 确认，不自动发送，不引入第二审批人。
- SQL 在 `sql/` 和 `docker/mysql/db/` 逐字一致并在 MySQL 5.7.44 连续执行两次。

文件边界：不要修改 Agent 1 的 `HrSignEventDispatcher`、Outbox claim/retry/compensation 算法；不要修改 Agent 3 的九项基线测试和对应生产文件。若发现共享 event/snapshot 契约必须改变，只做离职计划已明确列出的 `HrEmployeeSigningSnapshot` 字段；其他共享变更先停止并在交付中向 Agent 1说明影响和兼容方案。

验证时运行 System/OA 全量、离职前端专项、生产构建和迁移双份比较。Agent 3 未合并前，前端全量允许仍出现设计记录的九个基线失败，但必须证明不是本分支新增，不能顺手修复或删除断言。

完成后保持工作树干净，不自行合并到 Agent 1、不推送、不合并到 `7月12号`。按以下格式向用户/Agent 1 交付：

```text
角色：Agent 2 / 离职场景
分支：codex/offboarding-automation-20260713
worktree：
基点：
提交区间：
HEAD：
System 专项/全量：
OA 专项/全量：
MySQL 5.7.44 与迁移双跑：
前端专项/全量/构建：
九项既有前端失败是否仍存在：
双份 SQL cmp：
已知限制：
git status：
推送/合并：未执行
```
