# 统一待办与调拨/盘点发布收口详细方案

> 日期：2026-07-14
> 范围：OA 采购退役、门店要货调拨归口、库存盘点提醒、签约与健康证待办、数据库发布、真实角色验收。
> 状态：待执行的发布收口方案；不表示已对目标库执行写操作。
> 前置实施方案：`docs/superpowers/plans/2026-07-13-unified-todo-business-scope-completion.md`

## 1. 结论

当前待办代码已收口为 33 类（inventory 18、OA 8、system/HR 7），自动化测试和生产构建已通过。但当前项目不能直接发布，原因不在待办类型代码，而在发布基线和数据库前置条件：

1. 当前配置库仍有 18 张 OA 采购处于 `submitted`，且存在 18 个 Flowable 运行任务和 18 个运行实例；退役脚本会正确阻断。
2. 当前库存盘点表没有 `counter_user_id`、`counter_name`、`deadline`，待办 mapper 与当前库结构不匹配。
3. 《仓库管理发布与回滚手册》列出的 5 个应用必需迁移在当前库中均未执行。
4. `hr_employee_health_certificate` 尚未建立；`SysTodoMapper` 会引用该表，因此必须先迁移再发布 system 服务。
5. 2026-07-14 00:21 CST 快照中，当前工作区有 668 个状态变更，不具备可追溯的发布输入。

因此发布必须按“工作区收口 -> OA 活动流程处置 -> 同版库演练 -> 目标库迁移 -> 应用发布 -> 历史盘点治理 -> 角色验收 -> 观察”的顺序执行。

## 2. 当前项目真实基线

### 2.1 代码与工作区

| 项目 | 当前值 |
| --- | --- |
| 分支 | `7月13号` |
| HEAD | `b694659d` |
| 已修改 | 303 |
| 已删除 | 5 |
| 未跟踪 | 360 |
| 总状态变更 | 668（2026-07-14 00:21 CST 快照） |
| 待办类型 | inventory 18 + OA 8 + system/HR 7 = 33 |

已确认为有意迁移到 Maven Failsafe 的事务集成测试：

- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrEmployeeTransferTransactionTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOffboardingTransactionTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingTransactionTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventOutboxMySql57Test.java`

上述 4 个文件分别由同名 `*IT.java` 取代，逐字符对比证明只改了类名/自引用；`pom.xml` 的 `new-business-mysql-it` Failsafe profile 会执行 `**/*IT.java`。`erp-ui/node_modules` 仍是本地依赖状态，排除于发布清单。

本次已完成但仍未跟踪的关键交付物：

- `sql/erp_unified_todo_business_scope_20260713.sql`
- `sql/erp_unified_todo_business_scope_rollback_20260713.sql`
- `docker/mysql/db/erp_unified_todo_business_scope_20260713.sql`
- `sql/erp_hr_health_certificate_20260713.sql`
- `docker/mysql/db/erp_hr_health_certificate_20260713.sql`
- `erp-ui/test/unifiedTodoBusinessScopeMigration.test.js`
- 统一待办实施方案和验证记录

### 2.2 当前配置库

当前各服务本地配置指向 `BossERP_stock_state_75c59ee`，已连接的实例为 MySQL `8.0.45`。本方案将它作为当前项目验证库，不默认它就是生产库。

| 检查项 | 真实结果 | 影响 |
| --- | ---: | --- |
| OA 采购 `submitted` | 18 | 阻断 OA 采购退役 |
| OA 采购运行任务 | 18 | 不得直接隐藏或删除 |
| OA 采购运行实例 | 18 | 不得直接删 `ACT_*` |
| 当前任务指派 | `admin` 14、`ERP` 4 | 需由有权账号完成/驳回 |
| OA 历史其他状态 | draft 12、rejected 2、approved 1 | 保留历史，不物理删除 |
| OA 旧菜单仍启用 | 9 | 迁移后应全部停用 |
| inventory 采购菜单 `4020` | 启用 | 必须保留，不属于 OA 退役 |
| 活动盘点 | draft 21、invalidated 2 | 迁移后需人工取舍/指派 |
| 7 天以上盘点草稿 | 21 | 不应自动进入个人待办 |
| 活动盘点组织 | 全部为 `1245` | 需由该组织业务责任人确认 |
| 盘点范围/责任人字段 | 0/8 存在 | 需先执行盘点前置迁移 |
| 收货/逐行质检新表 | 0/4 存在 | 当前分支的采购收货代码不可直接发布 |
| 仓储高频索引 | 0/5 存在 | 需按窗口评估并执行 |
| 健康证表 | 不存在 | system 待办发布阻断 |
| 签约任务表/通知 outbox | 存在 | 签约待办的结构前置已满足 |
| 签约任务数 | 0 | 上线验收需准备可回滚测试数据 |
| `feature.oa.purchase.enabled` | 不存在 | 新代码按 fail-closed 关闭 |
| `todo.stock-check.due-soon.hours` | 不存在 | 新代码回退 24 小时，迁移后应显式写入 |

### 2.3 环境差异

- 当前本地配置库和 `docker-compose.ecs-host.yml` 使用 MySQL 8。
- `docker/docker-compose.yml` 的自定义 MySQL 镜像仍使用 MySQL 5.7，但本轮多个仓储迁移明确以 MySQL 8 编写。
- ECS 编排只挂载 `docker/mysql/seed/00_BossERP_stock_state_75c59ee.sql`，不会自动执行 `docker/mysql/db/` 的增量迁移。
- 默认 Docker MySQL 镜像会按文件名顺序执行 `docker/mysql/db/*.sql`；不能简单复制 5 个仓储脚本，因为 `performance_indexes` 按字母顺序会早于 `stock_check_scope`，将在 `deadline` 字段建立前失败。

发布基线建议统一为 MySQL 8.0；默认 MySQL 5.7 Docker 组合应另行升级或通过兼容验证，不在未测试时声称两者同时可发布。

## 3. 最终业务目标

1. OA 不再提供采购申请、采购审批和相关待办；历史单据、审批意见和 Flowable 历史保留。
2. 门店要货只通过 `inv_transfer_order.transfer_type='warehouse'`，门店返仓为 `store_return`，异店调货为 `cross_store`。
3. 调拨只有一套业务事实和 5 类待办：审批、发货、收货、差异、驳回重提。
4. 盘点有 4 类待办：执行、审批、驳回、重盘；执行类型内部表达待执行/即将到期/逾期，同一盘点不重复计数。
5. 签约任务 5 类和健康证审核/驳回 2 类进入统一聚合链路。
6. inventory 正常采购、采购质检、采购收货和采购退货不受 OA 退役影响。
7. 公告、站内消息和推送不并入业务待办数。

## 4. 发布阻断项与优先级

| 优先级 | 阻断项 | 处理原则 |
| --- | --- | --- |
| P0 | 18 个 OA 采购运行流程 | 使用现有业务服务逐单完成/驳回，不删业务表或 `ACT_*` |
| P0 | 盘点、收货质检、健康证结构缺失 | 先迁移后启动新服务 |
| P0 | 668 个工作区变更混在同一分支（快照值） | 先形成可审查发布输入，不使用 `git add .` |
| 已解决 | 4 个 system 事务测试从 `*Test` 迁移为 `*IT` | 内容除类名外一致，由 Failsafe 的 `new-business-mysql-it` profile 执行 |
| P1 | 23 张活动盘点无真实责任人/截止时间 | 取消测试数据或真实指派，不从 `create_by` 伪造用户 ID |
| P1 | 签约任务当前为 0 | 准备可回滚验收数据，验证 5 种状态和权限 |
| P1 | 真实角色验收未执行 | 七类角色逐一签字 |
| P2 | 调拨发货/收货 SLA 未定义 | 不阻断本次发布；业务确认时限后另立需求 |

## 5. 分阶段执行方案

### 阶段 0：形成可追溯发布输入

目标：从当前脏工作区中分离可审查的统一待办/仓储发布集，不覆盖其他人的修改。

1. 记录分支、HEAD、完整 `git status` 和测试时间点。
2. 建立显式文件清单，至少分为：
   - 共享待办契约；
   - inventory 调拨/盘点/provider；
   - OA 采购退役/签约 provider；
   - system/HR 健康证 provider；
   - 前端路由、精确聚焦与页面；
   - SQL、测试和发布文档。
3. 对同文件内的非本任务改动进行 hunk 级审查；不得因为文件在清单中就将其中的其他功能一起发布。
4. 由文件所有者决定 4 个已删事务测试的去留：
   - 若为误删，恢复并运行；
   - 若已被新测试取代，记录新老覆盖对照；
   - 不接受“全量测试通过”作为删除事务测试的唯一理由。
5. 将 `.codex-runs/`、`.playwright-cli/`、临时截图、构建输出和本地 `node_modules` 排除在发布集外。
6. 核对所有未跟踪的 SQL、DTO、VO、mapper 和测试，避免本地可运行但提交后缺文件。

验证：

```bash
git status --short
git diff --check -- <显式发布文件清单>
git diff --name-only -- <显式发布文件清单>
git ls-files --others --exclude-standard -- <显式发布文件清单>
```

完成门：发布审查人能说明每个文件属于哪个业务包；没有待确认的跟踪文件删除。

### 阶段 1：在旧入口关闭前处置 OA 活动流程

目标：使 18 个运行流程通过正常业务动作结束，然后再退役入口。

1. 先暂停新增 OA 采购：在现有发布版本中撤销 `oa:purchase:add` 或进入短维护窗口，但保留现有审批人处理待办的能力。
2. 导出 18 张单据的申请人、组织、金额、原因、当前任务和创建时间，由业务负责人标记：
   - 真实且必须完成：走完当前流程；
   - 已被调拨取代/测试数据：由当前候选人使用驳回动作结束；
   - 无法判定：保留并阻断发布。
3. 使用现有 `POST /oa/purchase/approve` 业务接口执行 `approve` 或 `reject`；请求必须包含匹配的 `purchaseId`、`taskId`、`action`和处置说明。
4. `admin` 负责当前指派给 `admin` 的 14 个任务；`ERP` 或实际候选角色处理其余 4 个，不用管理员越权直接改表。
5. 每处理一批都对账 `oa_purchase.current_task_id`、`ACT_RU_TASK`、`ACT_RU_EXECUTION`和审批意见。
6. 全部归零后才允许设置 `feature.oa.purchase.enabled=false`。不得先发布 fail-closed 代码后再发现旧待办无法处理。

完成门：

```sql
SELECT COUNT(*) FROM oa_purchase WHERE LOWER(status) = 'submitted';

SELECT COUNT(DISTINCT t.ID_)
FROM oa_purchase p
JOIN ACT_RU_TASK t ON t.ID_ = p.current_task_id
WHERE LOWER(p.status) = 'submitted';

SELECT COUNT(DISTINCT e.PROC_INST_ID_)
FROM ACT_RU_EXECUTION e
WHERE e.PROC_DEF_ID_ LIKE 'oaPurchaseApproval:%';
```

三个结果必须全部为 0。

### 阶段 2：同版 MySQL 8 克隆库迁移演练

目标：在与目标环境同版的 MySQL 8 克隆库中证明顺序、幂等性、菜单保护和回滚可用。

应用必需迁移顺序：

1. `sql/erp_inventory_purchase_return_item_20260713.sql`
2. `sql/erp_inventory_stock_check_scope_20260713.sql`
3. `sql/erp_inventory_receipt_quality_20260713.sql`
4. `sql/erp_inventory_performance_indexes_20260713.sql`
5. `sql/erp_inventory_warehouse_navigation_20260713.sql`
6. `sql/erp_hr_health_certificate_20260713.sql`
7. `sql/erp_unified_todo_business_scope_20260713.sql`

不可交换的依赖：

- `stock_check_scope` 必须早于 `performance_indexes`，因为后者使用 `deadline`。
- 健康证脚本必须早于新 system 服务启动。
- 统一待办收口脚本必须在 OA 运行流程全部归零之后执行。
- 仓储导航迁移要先于 UI 真实路由验收，否则库存中的 4100 和仓储中的 4460 仍会重复可见。

演练要求：

1. 先备份克隆库结构与相关业务表行数。
2. 在克隆库中先保留 OA 活动数据运行统一待办脚本，确认其按预期阻断。
3. 使用已经业务确认的终态快照重建演练库，按顺序运行七个脚本。
4. 可重复脚本连续执行两次，验证第二次不重复建表、建索引、备份菜单或授权。
5. 在克隆库执行：
   - `sql/erp_inventory_warehouse_navigation_rollback_20260713.sql`；
   - `sql/erp_unified_todo_business_scope_rollback_20260713.sql`。
6. 确认菜单和角色授权能精确恢复，不删新的业务事实表。
7. 为所有发布 SQL 生成 SHA-256 清单，目标环境按校验和执行，不依赖目录自动字母排序。

数据库验收必须满足：

- 盘点主表 8 个前置字段、明细 4 个复盘字段全部存在。
- `idx_stock_check_counter_due(counter_user_id,status,deadline)` 列序完全一致。
- 4 个收货/质检表、7 个入库质量字段和 5 个高频索引存在。
- `hr_employee_health_certificate` 表、两个配置和健康证权限菜单存在。
- `feature.hr.health-certificate.enabled=false`，不默认开放新受理。
- `feature.oa.purchase.enabled=false`、`todo.stock-check.due-soon.hours=24`。
- OA 旧采购菜单和其角色授权为 0 个活动项。
- `inventory/purchase/index`、`inv:purchase:*` 仍为启用，不被 OA 脚本命中。
- 调拨 `inventory/transfer/index` 只有一个可见组件路由。

完成门：演练报告包含脚本校验和、开始/结束时间、所有验收查询结果、回滚结果和执行人。

### 阶段 3：目标环境维护窗口

目标：严格按演练过的校验和和顺序完成数据库与应用发布。

1. 入窗前再执行 OA 三个归零查询；任一非 0 立即取消窗口。
2. 暂停采购收货、质检、调拨发货/收货、盘点提交和健康证审核，等待在途请求完成。
3. 生成全库结构备份和关键表数据备份，记录备份 ID、大小、校验和与恢复演练结果。
4. 按阶段 2 顺序执行 7 个 SQL，每个脚本完成后立即停下验收，不将错误带入下一脚本。
5. 重启或刷新依赖 `sys_config` 的服务配置缓存；不粗暴清空全部 Redis。
6. 发布后端：
   - `erp-common/erp-common-core`；
   - `erp-modules/erp-system`；
   - `erp-modules/erp-inventory`；
   - `erp-modules/erp-oa`。
7. 分别确认三个 provider 的 `/summary` 和 `/list` 可用后，再发布 gateway 和前端。
8. 前端发布后清理带版本号的 CDN/浏览器旧资源，不依赖用户手动清缓存。
9. 先放开一个仓库、一个门店、一个 HR 和一个员工账号冒烟，通过后再全量恢复。

完成门：无 mapper 加载错误、无缺表/缺列错误、三 provider 均能独立返回摘要和列表。

### 阶段 4：历史盘点治理

目标：只让有真实盘点人和截止时间的活动盘点进入个人待办。

1. `erp_inventory_stock_check_scope_20260713.sql` 只将已有 `create_by` 写入 `counter_name` 快照，不回填 `counter_user_id` 和 `deadline`；保持此安全边界。
2. 对当前组织 `1245` 的 21 张草稿和 2 张已失效盘点逐单分类：
   - QA/重复/无业务价值：使用已有取消能力关闭，保留操作日志；
   - 仍需执行：业务负责人指定真实用户和未来截止时间；
   - 无法判定：保持不进入个人待办，继续列入治理工单。
3. 真实指派使用 `PUT /inventory/stockCheck/{checkId}/assignment`，而不是直接 UPDATE。
4. 候选人必须账号有效、具有目标组织，并拥有 `inv:stockCheck:submit`。
5. 指派后由本人验证输入和提交；非指定人员应被服务端拒绝。

完成门：

```sql
SELECT COUNT(*)
FROM inv_stock_check
WHERE LOWER(status) IN ('draft', 'rejected', 'invalidated')
  AND (counter_user_id IS NULL OR deadline IS NULL);
```

用于正式执行的活动盘点必须为 0；明确保留的治理异常必须有工单号且不进入个人待办。

### 阶段 5：七类真实角色验收

| 角色 | 必须看到 | 权限/数据条件 | 必须看不到或不能做 |
| --- | --- | --- | --- |
| 门店要货人 | 门店要货调拨、本人被驳回的调拨 | `inv:transfer:list` + `add/edit` | OA 采购入口、他人驳回件 |
| 仓库发货人 | 来源仓待发货 | `inv:transfer:list` + `deliver`，组织为调出方 | 非本仓发货、OA 采购 |
| 目标收货人 | 每个 `pending_receive` 批次一条待收 | `inv:transfer:list` + `receive`，组织为调入方 | 非本组织批次、同批次重复待办 |
| 运营审批人 | 自己是候选人的调拨/盘点审批 | `approve` + `list` 且任务候选匹配 | 仅因是管理员就出现非候选审批 |
| 指定盘点人 | 待执行、临期、逾期、驳回、重盘 | `inv:stockCheck:list` + `submit`，`counter_user_id=当前用户` | 他人盘点、非本人代录 |
| HR | 分配给本人的 5 类签约任务、授权组织健康证待审 | 配置 HR 身份 + 签约动作权限；健康证 `list/query/review` | 其他 HR 的任务、越组织数据 |
| 员工本人 | 劳动合同/签约包待签、本人健康证驳回 | 业务归属本人；健康证 `self:edit/self:submit` 且开关开启 | 他人合同、他人健康证 |

每个角色都要验收：

1. 桌面首页数、顶部数、统一待办中心列表总数一致。
2. 移动端总数与桌面一致，但某 provider 故障时不将已知数据误显示为 0。
3. 点击待办只打开详情/对应记录，不自动通过、重试或提交。
4. 事项处理后立即刷新，已处理的深链给出“事项已处理”，不跳错记录。
5. 分别验收 `current_org` 和 `all_authorized`，不允许当前门店上下文污染个人事项。

签约当前为 0 行，验收数据必须通过现有服务或专用 fixture 创建，验收完成后按记录回滚，不向业务表手写不完整行。

完成门：七类角色的“可见、不可见、可处理、不可越权”全部签字。

### 阶段 6：自动化、数据库与性能门禁

后端：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"

mvn -pl erp-common/erp-common-core -am \
  -Dtest=TodoContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl erp-modules/erp-inventory -am \
  -Dtest='InvTodo*Test,InvStockCheck*Test,InvTransfer*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl erp-modules/erp-oa -am \
  -Dtest='OaTodo*Test,OaSignTodoProviderTest,OaSignTaskServiceImplTest,OaPurchaseServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl erp-modules/erp-system -am \
  -Dtest='SysTodo*Test,HrHealthCertificate*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

前端：

```bash
cd erp-ui
NODE=/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node
NPM=/opt/homebrew/lib/node_modules/npm/bin/npm-cli.js

$NODE scripts/run-node-tests.cjs unifiedTodoBusinessScopeMigration.test.js
$NODE $NPM test
$NODE $NPM run build:prod
```

数据库与性能：

1. 使用与目标库同规模数据运行三 provider 的 `summary/list`，覆盖 `type/category/source/scope/keyword/page` 组合。
2. 验证摘要总数、分类数和列表总数对账；检查重复 `todoKey`。
3. 对 inventory、OA、system provider 分别注入超时，确认其他来源和上次已知数不会被清零。
4. 《仓库管理发布与回滚手册》当前仍写“15 类待办”，应修订为当前 33 类基线后重做 P95。
5. 目标门禁：三 provider P95 < 1s，无单次超时导致整个统一待办不可用。

完成门：专项测试、前端全量、生产构建、真实 MySQL SQL 和角色验收同时通过。

### 阶段 7：发布观察

观察期至少一个完整工作日，前 2 小时保持高频监控。

监控项：

- inventory/OA/system provider 成功率、P50/P95/P99、超时和 SQL 异常。
- `summary.total`、分类合计和列表总数差异。
- 重复 `todoKey`、路由无法解析、点击后已处理比例。
- 盘点逾期数、无责任人/无截止时间活动盘点数。
- OA 采购 `save/submit/approve/close` 被调用次数；退役后应为 0。
- 调拨审批/发货/收货/差异/驳回在桌面和移动的重复数。
- 签约失败任务与通知 `DEAD` outbox 对账差异。
- 健康证审核数与管理页相同过滤数的差异。

发布成功门：无数据丢失、无越权、无重复待办、无 provider 持续异常，且 OA 采购写入调用持续为 0。

## 6. 回滚策略

### 6.1 原则

1. 数据库新表/新字段是加法迁移，应用回滚时保留，不盲目 DROP。
2. 业务事实、审批历史、签名证据、盘点明细和库存流水不删除。
3. OA 采购已作为业务决策退役，应用回滚不等于重新开放 OA 采购。
4. 先回滚有问题的单个 provider/前端版本，不将三个 provider 绑定为一次全量回滚。

### 6.2 具体动作

- 仓储菜单导航需恢复时，使用 `sql/erp_inventory_warehouse_navigation_rollback_20260713.sql`。
- 统一待办菜单/配置需恢复时，使用 `sql/erp_unified_todo_business_scope_rollback_20260713.sql`，但不应默认重开 OA 采购。
- 若回滚到不识别 `feature.oa.purchase.enabled` 的旧代码，仍应保持 OA 菜单和新增权限关闭；只有明确业务审批才允许恢复。
- 健康证应用需回滚时，将 `feature.hr.health-certificate.enabled=false`，保留表与已有记录。
- 性能索引仅在确认导致明显写入回归且已评审后单独删除，不与业务代码回滚捆绑。

立即回滚触发条件：

- 任一用户能看到或处理越组织/非本人事项。
- 调拨/盘点写操作出现库存不平或重复扣增。
- provider 持续不可用且降级不能隔离。
- mapper 缺表/缺列、菜单授权大面积丢失、待办数持续重复。

## 7. 还不应在本次自动实施的事项

1. 调拨发货/收货 SLA：项目当前没有组织、`transfer_type`、节假日和开始时点口径，不用单据创建时间臆造逾期提醒。
2. OA 采购表和 Flowable 历史物理删除：稳定运行至少一个发布周期后，另做数据保留期和归档方案。
3. 将公告、消息和推送并入待办：它们仍是消息中心口径。
4. 对历史盘点批量填充创建人 ID 和统一截止时间：这会伪造责任关系。

## 8. 交付物

1. 显式发布文件清单和工作区取舍记录。
2. OA 18 个活动流程的处置清单和三个归零查询记录。
3. 七个 SQL 的发布顺序、SHA-256、克隆库执行日志和验收结果。
4. 23 张活动盘点的取消/指派/工单处置表。
5. 七类角色的桌面/移动验收记录。
6. 自动化测试、生产构建、真实 MySQL 和 P95 报告。
7. 发布后一个工作日的观察记录和最终签字。

## 9. 完成定义

只有同时满足以下条件，才能将本项目标记为已发布完成：

1. 发布输入可追溯，已删测试和未跟踪关键文件全部有明确结论。
2. OA 采购 submitted、运行任务、运行实例全为 0。
3. 七个必需迁移在目标库按审批顺序完成，所有验收查询通过。
4. OA 采购菜单、移动入口、两类待办和写入入口关闭，inventory 采购正常。
5. 调拨只有一套可见入口和一套 5 类待办。
6. 指定盘点人可看到待执行/临期/逾期，非指定人不能代录。
7. 签约 5 类、健康证 2 类、其余待办合计 33 类在摘要、列表、桌面和移动口径一致。
8. 七类真实角色验收通过，无越权、重复待办和错误跳转。
9. 一个完整工作日内无持续 provider 异常、无库存不平，OA 采购写调用为 0。
