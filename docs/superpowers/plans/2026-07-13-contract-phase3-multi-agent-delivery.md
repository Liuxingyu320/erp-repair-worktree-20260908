# 合同自动化阶段 3 三窗口交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让三个独立 Codex 窗口从同一规划基点并行完成 HR 签约事件 Outbox、离职合同自动化和九项前端基线治理，最后由 Agent 1 在唯一集成分支完成合并与全量验收。

**Architecture:** `codex/phase3-integration-outbox-20260713` 同时是 Agent 1 的实现分支和唯一集成分支；Agent 2、Agent 3 从同一规划分支 HEAD 建独立 worktree。两个执行分支只通过 Git 提交向 Agent 1 交付，Agent 1 按“离职 → 前端基线”的顺序合并并运行组合门禁，不触碰原始脏工作树和 `7月12号`。

**Tech Stack:** Git worktrees、Java 17、Spring Boot、MyBatis、OpenFeign、MySQL 5.7.44/Testcontainers、Vue 2、Node.js 24.14.0、npm 11、Capacitor 8、Colima/Docker。

---

## 0. 不可变输入与执行文件

- 功能代码基线：`6766dc43bd82067d5d835e7923bb7fcc302cf07f`。
- 规划分支：`codex/phase3-multi-agent-plan-20260713`。
- 设计：`docs/superpowers/specs/2026-07-13-contract-phase3-multi-agent-delivery-design.md`。
- Agent 1 子计划：`docs/superpowers/plans/2026-07-13-hr-sign-event-outbox-delivery.md`。
- Agent 2 子计划：`docs/superpowers/plans/2026-07-13-contract-offboarding-automation.md`。
- Agent 3 子计划：`docs/superpowers/plans/2026-07-13-frontend-baseline-gates-remediation.md`。
- 三个窗口必须从规划分支最终 HEAD 建分支，不能直接从 `6766dc43` 建分支，否则会缺少本计划和统一契约。

## Task 1: 建立三个隔离 worktree

**Files:**

- Verify only: `.git` worktree metadata
- Do not modify: `/Users/liuxingyu/Desktop/备份/ERP-NEW` 中用户现有工作树

- [ ] **Step 1: 在规划 worktree 确认基点干净**

Run:

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-multi-agent-plan-20260713
git status --short --branch
git rev-parse --verify codex/phase3-multi-agent-plan-20260713
git merge-base --is-ancestor 6766dc43bd82067d5d835e7923bb7fcc302cf07f HEAD
```

Expected: 第一条只有分支头且没有文件状态；后两条退出码为 `0`。

- [ ] **Step 2: 创建 Agent 1 worktree 和集成分支**

```bash
git worktree add -b codex/phase3-integration-outbox-20260713 \
  /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713 \
  codex/phase3-multi-agent-plan-20260713
```

Expected: 创建成功且检出 `codex/phase3-integration-outbox-20260713`。若出现 `mmap failed: Operation timed out`，停止，不在半检出 worktree 中开发；先运行 `git worktree remove` 清理该未使用 worktree，再由窗口使用 `using-git-worktrees` 技能重建。

- [ ] **Step 3: 创建 Agent 2 worktree 和分支**

```bash
git worktree add -b codex/offboarding-automation-20260713 \
  /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713 \
  codex/phase3-multi-agent-plan-20260713
```

- [ ] **Step 4: 创建 Agent 3 worktree 和分支**

```bash
git worktree add -b codex/frontend-baseline-gates-20260713 \
  /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713 \
  codex/phase3-multi-agent-plan-20260713
```

- [ ] **Step 5: 校验三方基点完全一致**

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713 rev-parse HEAD
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713 rev-parse HEAD
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713 rev-parse HEAD
```

Expected: 三条命令输出同一个提交号。

## Task 2: 并行执行三个子计划

**Files:**

- Agent 1: `docs/superpowers/plans/2026-07-13-hr-sign-event-outbox-delivery.md`
- Agent 2: `docs/superpowers/plans/2026-07-13-contract-offboarding-automation.md`
- Agent 3: `docs/superpowers/plans/2026-07-13-frontend-baseline-gates-remediation.md`

- [ ] **Step 1: Agent 1 执行 Outbox 子计划**

Agent 1 使用 `docs/superpowers/prompts/2026-07-13-phase3-agent-1-integration-outbox.md`，逐任务提交。Agent 1 在 Agent 2/3 交付前不得修改其所有权文件。

- [ ] **Step 2: Agent 2 执行离职子计划**

Agent 2 使用 `docs/superpowers/prompts/2026-07-13-phase3-agent-2-offboarding.md`。交付信息必须包括提交区间、System/OA/前端专项测试、MySQL 5.7 结果、迁移双份比较和工作树状态。

- [ ] **Step 3: Agent 3 执行前端门禁子计划**

Agent 3 使用 `docs/superpowers/prompts/2026-07-13-phase3-agent-3-frontend-baseline.md`。交付信息必须包括九个失败的根因对照、每次独立提交、142 个测试文件结果、构建与 Capacitor 校验结果。

- [ ] **Step 4: Agent 1 在等待期间完成自己的专项门禁**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventOutboxServiceTest,HrSignEventDispatcherTest,HrSignEventPayloadFactoryTest,HrSignEventCompensationScannerTest,HrSignEventOutboxMySql57Test \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 专项全部通过；真实数据库测试报告 MySQL `5.7.44`。

## Task 3: Agent 1 审核 Agent 2 交付并合并离职分支

**Files:**

- Review: Agent 2 提交区间内全部文件
- Modify on conflict only: Agent 1/Agent 2 共享的 `HrSignBusinessEvent`、`HrEmployeeSigningSnapshot`、生命周期 mapper/service、迁移包装过程

- [ ] **Step 1: 确认 Agent 2 分支干净且基点正确**

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713 status --short --branch
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/offboarding-automation-20260713 merge-base --is-ancestor codex/phase3-multi-agent-plan-20260713 HEAD
git log --oneline --decorate codex/phase3-multi-agent-plan-20260713..codex/offboarding-automation-20260713
```

Expected: 无未提交文件；祖先检查退出 `0`；提交按子计划分组。

- [ ] **Step 2: 审核差异而不先合并**

```bash
git diff --check codex/phase3-multi-agent-plan-20260713..codex/offboarding-automation-20260713
git diff --stat codex/phase3-multi-agent-plan-20260713..codex/offboarding-automation-20260713
git diff --name-only codex/phase3-multi-agent-plan-20260713..codex/offboarding-automation-20260713
```

Expected: `git diff --check` 无输出；没有 Agent 1 Outbox dispatcher 文件，也没有 Agent 3 基线修复文件。

- [ ] **Step 3: 合并离职分支**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
git merge --no-ff codex/offboarding-automation-20260713 \
  -m "merge: integrate offboarding contract automation"
```

Expected: 生成一个合并提交。若冲突，逐段理解双方意图；禁止整文件选择 `ours`/`theirs`。

- [ ] **Step 4: 运行合并后的离职与 Outbox 组合专项**

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrSignEventDispatcherTest,HrSignEventCompensationScannerTest,HrOffboardingLifecycleTest,HrOffboardingTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OffboardSignScenarioRuleTest,OffboardSignPersistenceContractTest,OaSignTaskOrchestratorTest,OaSignDocumentServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 全部通过，且补偿构造出的 `OFFBOARD` 事件能够由 OA 规则接受。

## Task 4: Agent 1 审核 Agent 3 交付并合并前端分支

**Files:**

- Review: Agent 3 提交区间内全部文件
- Do not accept: `erp-ui/dist/**`、`erp-ui/android/app/src/main/assets/public/**`、`erp-ui/ios/App/App/public/**`、签名或推送凭据

- [ ] **Step 1: 校验 Agent 3 分支和差异边界**

```bash
git -C /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/frontend-baseline-gates-20260713 status --short --branch
git diff --check codex/phase3-multi-agent-plan-20260713..codex/frontend-baseline-gates-20260713
git diff --name-only codex/phase3-multi-agent-plan-20260713..codex/frontend-baseline-gates-20260713
```

Expected: 分支干净；只出现子计划声明的前端文件、九项测试及验证文档。

- [ ] **Step 2: 明确拒绝生成物和凭据**

```bash
git diff --name-only codex/phase3-multi-agent-plan-20260713..codex/frontend-baseline-gates-20260713 \
  | rg '(^|/)(dist|public)/(index\.html|static/)|google-services\.json|GoogleService-Info\.plist|\.jks$|\.keystore$'
```

Expected: 无输出。若有输出，停止合并并让 Agent 3 修正提交历史。

- [ ] **Step 3: 合并前端分支**

```bash
git merge --no-ff codex/frontend-baseline-gates-20260713 \
  -m "merge: close frontend baseline gates"
```

- [ ] **Step 4: 运行离职前端专项与九项基线专项**

```bash
cd erp-ui
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
node --version
node test/hrOffboardingAutomation.test.js
node test/headerNoticeReadAll.test.js
node test/laborContractModule.test.js
node test/mobileAppShell.test.js
node test/mobileAuthEntryPages.test.js
node test/mobileInventoryWorkbench.test.js
node test/mobileProductionDataIsolation.test.js
node test/mobileProfileMaintenance.test.js
node test/mobileProgressiveRedesign.test.js
node test/unifiedTodoBusinessFocus.test.js
```

Expected: Node 输出 `v24.14.0`；十个专项文件全部退出 `0`。

## Task 5: 演练迁移双份一致性与 MySQL 5.7 幂等性

**Files:**

- Verify: `sql/erp_hr_offboarding_automation_20260713.sql`
- Verify: `docker/mysql/db/erp_hr_offboarding_automation_20260713.sql`
- Verify if created by Agent 1: `sql/erp_hr_sign_event_outbox_delivery_20260713.sql`
- Verify if created by Agent 1: `docker/mysql/db/erp_hr_sign_event_outbox_delivery_20260713.sql`

- [ ] **Step 1: 比较每份双写迁移**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
cmp sql/erp_hr_offboarding_automation_20260713.sql \
  docker/mysql/db/erp_hr_offboarding_automation_20260713.sql
```

如果 Agent 1 新增 Outbox 迁移，再运行：

```bash
cmp sql/erp_hr_sign_event_outbox_delivery_20260713.sql \
  docker/mysql/db/erp_hr_sign_event_outbox_delivery_20260713.sql
```

Expected: 所有 `cmp` 退出码为 `0`。

- [ ] **Step 2: 启动一次性 MySQL 5.7.44 实例**

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
docker run --rm -d --name erp-phase3-mysql57 \
  -e MYSQL_ROOT_PASSWORD=erp_phase3_root \
  -e MYSQL_DATABASE=erp_phase3 \
  mysql:5.7.44 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

Expected: 返回容器 ID。用以下有界轮询等待就绪：

```bash
for attempt in {1..30}
do
  if docker exec erp-phase3-mysql57 mysqladmin ping \
      -uroot -perp_phase3_root --silent
  then
    break
  fi
  sleep 2
done
docker exec erp-phase3-mysql57 mysqladmin ping \
  -uroot -perp_phase3_root --silent
```

最后一条必须退出 `0`，否则停止迁移演练并保留容器日志作为证据。

- [ ] **Step 3: 装载自包含测试基线和前置迁移**

Agent 2 的 MySQL 测试 schema 包含本链路所需 System/OA 基线表和 `sync_sign_hr_permissions_with_plan()` 前置过程。按下面的固定顺序装载：

```bash
docker exec -i erp-phase3-mysql57 mysql -uroot -perp_phase3_root erp_phase3 \
  < erp-modules/erp-system/src/test/resources/hr-offboarding-it-schema.sql
docker exec -i erp-phase3-mysql57 mysql -uroot -perp_phase3_root erp_phase3 \
  < sql/erp_hr_lifecycle_action_20260711.sql
docker exec -i erp-phase3-mysql57 mysql -uroot -perp_phase3_root erp_phase3 \
  < sql/erp_hr_transfer_effective_date_20260713.sql
```

Expected: 三条命令退出 `0`。

- [ ] **Step 4: 连续执行新增迁移两次**

```bash
docker exec -i erp-phase3-mysql57 mysql -uroot -perp_phase3_root erp_phase3 \
  < sql/erp_hr_offboarding_automation_20260713.sql
docker exec -i erp-phase3-mysql57 mysql -uroot -perp_phase3_root erp_phase3 \
  < sql/erp_hr_offboarding_automation_20260713.sql
```

Expected: 两次都退出 `0`；`hr:employee:offboard` 菜单、唯一 HR 授权和 OA 新列各只有一份。

- [ ] **Step 5: 查询幂等结果**

```bash
docker exec erp-phase3-mysql57 mysql -N -uroot -perp_phase3_root erp_phase3 \
  -e "select count(*) from sys_menu where perms='hr:employee:offboard'; select count(*) from information_schema.columns where table_schema='erp_phase3' and table_name='oa_sign_package' and column_name in ('offboarding_type','salary_settlement_status','asset_handover_status','non_compete_decision','compensation_amount','compensation_note');"
```

Expected: 第一行 `1`，第二行 `6`。

- [ ] **Step 6: 删除专用测试容器**

```bash
docker rm -f erp-phase3-mysql57
```

只删除本计划创建且名称精确匹配的容器。

## Task 6: 运行最终组合门禁

**Files:**

- Create: `docs/superpowers/verification/2026-07-13-contract-phase3-integration.md`

- [ ] **Step 1: System 全量（真实 MySQL 5.7）**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am test
```

Expected: 退出 `0`，记录实际测试总数和 MySQL 版本，不能沿用旧数字。

- [ ] **Step 2: OA 全量**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am test
```

Expected: 退出 `0`，记录实际测试总数。

- [ ] **Step 3: 前端全量、密钥扫描和生产构建**

```bash
cd erp-ui
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
npm test
node scripts/scan-frontend-secrets.cjs
npm run build:prod
```

Expected: 合并 Agent 2 新增的 `hrOffboardingAutomation.test.js` 后应为 143 个 Node 测试文件全部通过；以脚本实际输出为最终记录。密钥扫描和生产构建退出 `0`。

- [ ] **Step 4: 生成并校验 Capacitor 受控副本**

```bash
npm run app:sync
npm run app:verify
```

Expected: Android 与 iOS `public/index.html` 由同步步骤生成且与 `dist` 静态引用一致；这些目录仍被 Git 忽略。缺少正式推送凭据只允许产生非 release 警告，不得伪造凭据。

- [ ] **Step 5: 验证 OA 停机恢复和双层幂等**

在测试环境执行一次当天调岗和一次当天离职：先停止 OA，确认 System 事务仍成功且 Outbox 为 `RETRY`；恢复 OA 后等待调度，确认每个 action 对应一个 `SENT` Outbox 和一个 OA canonical task。重复触发 dispatcher 后 taskId 不变。

Expected evidence:

```text
System action count = 1
Outbox row count = 1
Outbox status = SENT
OA canonical task count = 1
remote_task_id = OA task_id
OA task status in (NEEDS_DATA, WAITING_HR_CONFIRM, NO_ACTION)
no automatic send event
```

- [ ] **Step 6: 写最终验证记录**

验证文档必须写明：分支与提交、运行时版本、每条命令、真实测试数量、迁移双跑、OA 停机恢复结果、调岗/离职真实链路、未执行项及原因。不能写“同上”或复用旧结果。

- [ ] **Step 7: 提交最终验证记录**

```bash
git add docs/superpowers/verification/2026-07-13-contract-phase3-integration.md
git commit -m "docs: record phase 3 integration verification"
```

## Task 7: 最终自审与用户交付

**Files:**

- Verify: entire integration branch diff

- [ ] **Step 1: 检查工作树、空白错误和敏感文件**

```bash
cd /Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/phase3-integration-outbox-20260713
git status --short --branch
git diff --check codex/phase3-multi-agent-plan-20260713..HEAD
git diff --name-only codex/phase3-multi-agent-plan-20260713..HEAD \
  | rg 'google-services\.json|GoogleService-Info\.plist|\.jks$|\.keystore$|(^|/)dist/'
```

Expected: 工作树干净；`git diff --check` 和敏感文件搜索无输出。

- [ ] **Step 2: 审阅提交拓扑**

```bash
git log --graph --oneline --decorate \
  codex/phase3-multi-agent-plan-20260713..HEAD
```

Expected: Agent 1 的任务提交、两个明确 merge commit、最终验证提交均可识别。

- [ ] **Step 3: 向用户交付，不擅自推送或合并**

交付必须包含：集成分支名、HEAD、worktree、Agent 2/3 分支提交、System/OA/前端实际测试数、MySQL 版本、迁移结果、真实链路结果和“尚未推送、尚未合并到 `7月12号`”。只有用户再次明确授权后才执行推送或主分支合并。
