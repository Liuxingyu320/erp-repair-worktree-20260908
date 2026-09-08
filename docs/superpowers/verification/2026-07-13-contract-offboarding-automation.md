# 离职合同自动化验证记录

- 验证日期：2026-07-13（Asia/Shanghai）
- 角色：Agent 2 / 离职场景
- 分支：`codex/offboarding-automation-20260713`
- 基点：`d330cb56b053e00206436a65e1193eee2a0e506d`
- worktree：`/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713`

## System

- 离职纯单元/源码契约专项：32 tests，0 failures，0 errors，0 skipped。
- 非 Docker 全量：627 tests，0 failures，0 errors，0 skipped。
- 非 Docker 全量明确排除了 `HrEmployeeTransferTransactionTest`、`HrOffboardingTransactionTest`、`HrOnboardingTransactionTest`，干净 surefire 报告目录中没有这三个类的报告。
- 事务测试源码覆盖未来日期零副作用、当天原子确认、历史高风险、Outbox/账号失败回滚、条件更新失败、同/不同 requestId 并发和岗位关系保留。
- 按用户要求禁止启动 Colima，Docker/Testcontainers 验证未执行。因此本轮最终验证没有启动 MySQL 5.7.44，也没有执行真实数据库迁移双跑。

## OA

- 离职专项：97 tests，0 failures，0 errors，0 skipped。
- OA 全量：618 tests，0 failures，0 errors，0 skipped。
- 覆盖 OFFBOARD 信封与去重键、五类离职、全部风险来源、上海自然日历史标记、风险等级防伪、方案规则矩阵、模板快照、NO_ACTION、重复事件确定性、六类模板、所有 mapper 路径、任务业务日期和文档占位符。
- OA 仅消费冻结 before/after 快照，不查询当前员工；草稿状态为 `draft`，确认状态为 `WAITING_HR`，未增加自动发送路径或第二审批人。

## 前端

- `hrOffboardingAutomation.test.js`：通过。
- 调岗专项 `hrEmployeeTransferEffectiveDate.test.js`：通过。
- 任务中心专项 `signTaskCenter.test.js`：通过。
- 员工主档 harness 回归 `hrEmployeeMasterFields.test.js`：通过。
- 完整 Node 门禁：143 个测试文件运行，9 个失败；失败与 Agent 3 设计基线逐项一致，本分支没有新增失败：
  - `headerNoticeReadAll.test.js`
  - `laborContractModule.test.js`
  - `mobileAppShell.test.js`
  - `mobileAuthEntryPages.test.js`
  - `mobileInventoryWorkbench.test.js`
  - `mobileProductionDataIsolation.test.js`
  - `mobileProfileMaintenance.test.js`
  - `mobileProgressiveRedesign.test.js`
  - `unifiedTodoBusinessFocus.test.js`
- 前端密钥扫描：通过。
- `npm run build:prod`：退出 0；存在既有 entrypoint size warning，无编译错误。

## 迁移与边界

- `cmp sql/erp_hr_offboarding_automation_20260713.sql docker/mysql/db/erp_hr_offboarding_automation_20260713.sql`：退出 0，双份 SQL 逐字一致。
- 源码契约验证 MySQL 5.7 动态 DDL、唯一权限、包装过程调用链和六个 OA 列；真实 MySQL 执行按用户新增约束跳过。
- `git diff --check codex/phase3-multi-agent-plan-20260713..HEAD`：无输出。
- 差异未包含 Agent 1 的 `HrSignEventDispatcher` 或 Outbox claim/retry/compensation 算法文件。
- 差异未包含 Agent 3 九项基线测试及其对应生产文件。
- 未推送，未合并到 Agent 1，未合并到 `7月12号`。

## 已知限制

- 按用户新增约束，最终验证无法提供 Docker/Testcontainers、MySQL 5.7.44 和真实迁移连续执行两次的当轮证据。
- 九项既有前端基线失败由 Agent 3 独立治理；本分支未修改相关断言或生产文件。
- 离职只支持最后工作日当天或历史补录立即生效，不实现未来调度生效。
