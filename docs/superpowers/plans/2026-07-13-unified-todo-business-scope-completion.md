# 统一待办业务口径收口详细实施方案

> 日期：2026-07-13  
> 状态：代码实施完成，待目标环境数据库发布与真实角色验收  
> 依据：当前工作区源码、现有 SQL、菜单种子、统一待办一期方案、仓库检查报告、签约任务中心方案及本轮专项测试结果
> 实施记录：`docs/superpowers/verification/2026-07-13-unified-todo-business-scope-completion.md`

## 1. 目标与最终业务口径

本方案解决四件事：

1. OA 不再承担采购申请；门店要货只通过库存调拨完成。
2. 仓储作业与原进销存中的调拨入口归并为同一套调拨事实、状态和待办，不产生重复提醒。
3. 补齐库存盘点的执行人提醒、截止前提醒和逾期提醒，并保证提醒对象真的有权处理该盘点单。
4. 把已经存在但尚未接入统一待办的签约任务、健康证审核/驳回事项接入同一聚合链路。

最终业务边界：

- **移除的是 OA 采购申请**，即 `oa_purchase`、OA 采购审批页面及相关待办。
- **不移除库存采购管理**，`inv_purchase_order` 的采购、质检、收货、采购退货继续保留。
- 门店向仓库要货使用 `inv_transfer_order.transfer_type='warehouse'`。
- 门店返仓使用 `transfer_type='store_return'`，异店调货使用 `transfer_type='cross_store'`。
- 公告未读、站内消息和推送仍与业务待办分开统计，不混入统一待办总数。
- 本期不凭单据创建时间臆造调拨 SLA；调拨超时提醒必须在业务明确发货/收货时限后另行增加。

## 2. 当前项目真实状态

### 2.1 统一待办架构

当前前端分别调用三个提供方：

```text
/inventory/todo  -> erp-inventory
/oa/todo         -> erp-oa
/system/todo     -> erp-system / HR
```

共同契约位于：

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoItem.java`
- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoSummary.java`
- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`
- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoKeys.java`

统一待办不是落库队列表，而是按各业务表当前状态实时计算。因此移除一种待办不需要删除“待办数据”，只需要停止提供方查询该类型；业务历史数据仍由原业务表保留。

### 2.2 当前类型数量

当前工作区共有 27 个统一待办类型：

| 提供方 | 当前数量 | 说明 |
| --- | ---: | --- |
| inventory | 17 | 含调拨 5 类、盘点 3 类、采购销售执行、退货、OE 和库存风险 |
| oa | 5 | 其中 2 类是应移除的 OA 采购待办 |
| system | 5 | 员工资料、入职、合同、离职账号、健康证到期 |

当前后端 69 项统一待办专项测试、前端 7 组聚合/跳转测试通过。这证明当前实现自洽，同时也证明 OA 采购仍被正式纳入当前契约，并未满足新的业务口径。

### 2.3 已确认的真实缺口

1. `OaTodoTypes`、`OaTodoServiceImpl` 和 `OaTodoMapper.xml` 仍包含 `OA_PURCHASE_APPROVAL`、`OA_PURCHASE_RETURNED`。
2. 桌面静态路由 `/oa/todo/process`、移动 `/mobile/oa-purchase`、`/mobile/oa-todo`、`/mobile/oa-done` 仍存在。
3. OA 首页仍写“采购申请 / 我的待办 / 我的已办”。
4. `inv_stock_check` 已有 `counter_user_id`、`counter_name`、`deadline` 和索引 `idx_stock_check_counter_due`，但统一待办没有查询这些字段。
5. 盘点页面只提交 `counterName` 文本，没有提交真实的 `counterUserId`；服务端又默认把 `counterUserId` 设为当前创建人，存在“页面写张三、实际待办归李四”的风险。
6. 盘点录入/提交只校验组织和权限码，没有校验当前用户是否为 `counter_user_id`。
7. `OaSignTodoProvider` 已定义 5 类 HR 签约待办，但当前统一 `OaTodoServiceImpl` 没有调用它；它形成了第二套未接通的待办投影。
8. 签约任务页调用 `listTodos({ type: 'OA_SIGN_SEND_FAILED' })`，但 `TodoQuery` 当前没有 `type` 字段，该过滤条件实际被忽略；页面又按 `response.data` 读取，而统一列表返回的是 `rows`。
9. 健康证已有 `DRAFT -> PENDING_REVIEW -> APPROVED/REJECTED` 完整状态流，但统一待办只有到期风险，没有待审核和驳回重提。
10. 工作区在待办、调拨、签约和健康证相关文件上已有大量未提交修改；实施时不能覆盖、重置或混入无关改动。

## 3. 目标待办矩阵

### 3.1 数量变化

| 阶段 | 变化 | 类型总数 |
| --- | --- | ---: |
| 当前 | 当前工作区 | 27 |
| P0 收口 | 删除 OA 采购 2 类，新增盘点执行 1 类 | 26 |
| P1 完整接入 | 再新增签约 HR 5 类、健康证流程 2 类 | 33 |

“待盘点、即将到期、已逾期”是同一业务类型的三个显示状态，不应建立三种类型，否则同一盘点单在阈值切换时容易重复计数或产生不稳定键。

### 3.2 调拨：保留 5 类，不再按菜单建立第二套提醒

| 类型 | 触发事实 | 提醒对象/范围 | 跳转动作 | 消失条件 |
| --- | --- | --- | --- | --- |
| `INV_TRANSFER_APPROVAL` | 主单 `submitted`，审批实例 `running`，当前任务 `pending` | 当前任务候选审批人；管理员也不能绕过候选校验 | 打开调拨详情，用户再显式通过/驳回 | 当前任务完成、实例结束或主单离开 `submitted` |
| `INV_TRANSFER_DELIVER` | `approved/reserved/partial_delivered` | 调出组织且有 `inv:transfer:deliver` 的用户 | 调拨发货 | 主单进入 `delivered/partial_received/discrepancy/received/closed/cancelled` |
| `INV_TRANSFER_RECEIVE` | 发货批次 `pending_receive` | 调入组织且有 `inv:transfer:receive` 的用户；每个批次一条 | 指定批次收货 | 该批次不再 `pending_receive` |
| `INV_TRANSFER_DISCREPANCY` | 主单 `discrepancy` 且差异单为开放/待质检 | 有差异处理权限的责任组织用户 | 打开差异处理 | 差异闭环或主单关闭 |
| `INV_TRANSFER_RETURNED` | 最新审批实例 `rejected`，主单回到可修改状态 | 原创建人 | 修改并重新提交 | 重新提交、关闭或取消 |

实施要求：

- 五类 SQL 继续覆盖全部 `transfer_type`，不能复制为“仓库调拨待办”和“进销存调拨待办”。
- 标题按 `transfer_type` 使用业务语言：`门店要货待审批/待发货/待收货`、`门店返仓…`、`异店调货…`。
- 路由优先使用迁移后的 `/cangku/transfer`，保留 `/inventory/transfer` 作为一个发布周期的兼容别名。
- 执行 `erp_inventory_warehouse_navigation_20260713.sql` 后，菜单 4460 是唯一可见调拨作业入口；旧 4100 只保留回滚用途。

### 3.3 库存盘点：形成 4 类闭环

| 类型 | 触发事实 | 对象 | 类别/优先级 | 跳转动作 |
| --- | --- | --- | --- | --- |
| `INV_STOCK_CHECK_EXECUTE`（新增） | `status='draft'`、`counter_user_id=当前用户`、组织在授权范围 | 指定盘点人 | execution；按截止时间动态计算 | 打开盘点单并进入实盘录入 |
| `INV_STOCK_CHECK_APPROVAL` | `pending_approval` + 当前审批任务候选人 | 运营总监候选审批人 | approval；超审批阈值 urgent | 先看详情，再显式通过/驳回 |
| `INV_STOCK_CHECK_RETURNED` | `rejected` | 当前 `counter_user_id`；旧数据可回退 `submitted_user_id` | returned / important | 修改实盘并重新提交 |
| `INV_STOCK_CHECK_RESTART` | `invalidated` | 当前 `counter_user_id`；旧数据可回退 `submitted_user_id` | returned / important | 重新获取库存快照并盘点 |

`INV_STOCK_CHECK_EXECUTE` 的显示状态与优先级：

```text
deadline < now
  -> status=overdue, title=盘点已逾期, priority=urgent

now <= deadline <= now + todo.stock-check.due-soon.hours
  -> status=due_soon, title=盘点即将到期, priority=important

deadline > now + threshold
  -> status=pending, title=待执行盘点, priority=normal
```

约束：

- 新建盘点必须选择真实 `counterUserId` 和截止时间，不再接受只有姓名的自由文本。
- `counterName` 由后端根据用户 ID 写姓名快照，忽略客户端伪造值。
- 候选盘点人必须账号有效、拥有目标库存组织、并具备 `inv:stockCheck:submit`。
- 盘点录入、提交和失效重盘必须再次校验当前用户是指定盘点人；管理员或主管代办只能通过明确的管理权限，不允许仅凭列表权限代录。
- `draft` 只进入执行待办；`rejected` 只进入退回待办；`invalidated` 只进入重盘待办，三者互斥。
- 没有截止时间或没有盘点人的历史草稿不能悄悄进入个人待办，必须在迁移预检中人工分配或取消。

推荐新增配置：

```text
todo.stock-check.due-soon.hours=24
```

读取失败、空值或越界时回退 24 小时；允许范围建议为 1～168 小时。

### 3.4 OA：移除采购，保留真正的 OA 待办

删除统一待办中的：

```text
OA_PURCHASE_APPROVAL
OA_PURCHASE_RETURNED
```

保留：

```text
OA_FIXED_ASSET_REPLENISHMENT_DEAD
OA_LABOR_CONTRACT_SIGN
OA_SIGN_PACKAGE_SIGN
```

OA 采购退役必须与库存采购严格隔离：任何 `inv:purchase:*` 权限、`/inventory/purchase` 路由、采购质检/收货待办都不能受到影响。

### 3.5 签约任务：接入 5 类 HR 待办

| 类型 | `oa_sign_task.status` | 权限 | 类别/优先级 | 动作 |
| --- | --- | --- | --- | --- |
| `OA_SIGN_NEEDS_DATA` | `NEEDS_DATA` | `oa:signTask:list` + `revalidate` | execution / normal | 补资料并重新校验 |
| `OA_SIGN_HR_CONFIRM` | `WAITING_HR_CONFIRM` | `list` + `confirm` | approval / important | 确认签约资料和文件 |
| `OA_SIGN_SEND_FAILED` | `FAILED`，以及通知 outbox `DEAD` | `list` + `retry` | risk / important | 重试任务或通知 |
| `OA_SIGN_REFUSED` | `REFUSED` | `list` + `retry` | risk / urgent | 处理拒签 |
| `OA_SIGN_EXPIRED` | `EXPIRED` | `list` + `retry` | risk / urgent | 处理逾期合同 |

实现要求：

- `oa_sign_task` 是签约业务事实源，当前统一 `OaTodoMapper` 是唯一聚合入口。
- 不把 `OaSignTodoProvider` 的内存列表直接与已分页的 `OaTodoMapper` 结果拼接，否则 OA 分页总数、摘要和最近列表会不一致。
- 将这 5 类迁移进 `OaTodoMapper.xml` 的统一 union；完成后删除或降级旧 `OaSignTodoProvider`，不得长期维护两套规则。
- HR 任务必须同时满足 `assigned_hr_user_id=当前用户`、HR 身份校验、动作权限和组织范围。
- `OA_SIGN_SEND_FAILED` 可包含“任务失败”和“通知死信”两个动作，但必须用不同 `routeType` 形成稳定键；同一通知失败按任务聚合，避免一条 outbox 重试记录生成多条重复待办。
- 跳转统一到 `/oa/sign-task?taskId=...`；签约任务页已经支持按 `taskId` 自动打开详情。

### 3.6 健康证：新增审核和驳回重提

| 类型 | 触发事实 | 对象 | 类别/优先级 | 动作 |
| --- | --- | --- | --- | --- |
| `HR_HEALTH_CERT_REVIEW` | `review_status='PENDING_REVIEW'` | 有台账、详情和审核权限的 HR，按授权组织分组 | approval / important；超审批阈值 urgent | 打开人事审核页并筛选待审核 |
| `HR_HEALTH_CERT_RETURNED` | `review_status='REJECTED'`、`user_id=当前用户` | 被驳回员工本人 | returned / important | 打开“我的健康证”并定位记录 |

约束：

- 健康证新受理开关关闭时，已有待审核记录仍允许 HR 审核，所以 `HR_HEALTH_CERT_REVIEW` 继续存在。
- `HR_HEALTH_CERT_RETURNED` 只有在 `feature.hr.health-certificate.enabled=true`、员工可实际编辑和重新提交时才作为可执行待办；否则不能生成一个点进去无法处理的待办。
- 现有 `HR_HEALTH_CERT_DUE` 继续保留，但跳转后必须自动进入管理页并应用“临期或过期”筛选，不能只打开一个未筛选页面。

## 4. 技术设计

### 4.1 共享查询契约补齐 `type`

给 `TodoQuery` 增加可选 `type`：

```java
private String type;
```

规则：

- 归一化为空时不筛选。
- 非空值必须匹配 `^[A-Z][A-Z0-9_]{0,63}$`。
- inventory、oa、system 三个提供方都按 `type` 过滤，不能只有 OA 生效。
- 前端统一聚合接口可以不展示类型筛选控件，但业务页面可以精确获取某一类待办。

这样可修复签约任务页当前传 `type` 却被静默忽略的问题，也为专项对账提供稳定接口。

### 4.2 inventory 提供方

在 `InventoryTodoUnion` 中新增 `INV_STOCK_CHECK_EXECUTE` 分支，并给三个 mapper 方法增加 `stockCheckDueSoonHours` 参数。

查询必须包含：

```text
s.status = 'draft'
s.counter_user_id = #{userId}
s.deadline is not null
组织范围与当前 scopeMode 一致
```

标题、状态和优先级在 SQL 中一次性计算，列表、最近事项和统计复用同一个 union，避免三套阈值逻辑。

### 4.3 盘点指派与写权限

当前 `/stockCheck/input/{checkId}` 同时更新实盘数据、盘点人和截止时间，职责过宽。拆分为：

```text
PUT  /inventory/stockCheck/{checkId}/assignment
POST /inventory/stockCheck/input/{checkId}
POST /inventory/stockCheck/submit/{checkId}
```

- assignment：`inv:stockCheck:edit`，只允许草稿/驳回/失效待重盘状态，行锁后更新盘点人和截止时间。
- input：`inv:stockCheck:submit`，只允许指定盘点人录入实盘/复盘数量，不再修改指派信息。
- submit：同样校验指定盘点人，继续使用现有库存快照、差异审批和事务逻辑。

新增候选人查询应返回最小展示字段，不暴露身份证、手机号等无关人事信息。候选人查询必须在服务端验证账号、组织授权和 `inv:stockCheck:submit` 权限，不能只靠前端下拉框过滤。

### 4.4 OA 提供方统一单一事实源

从 `OaTodoServiceImpl` 移除采购后：

- `OaCurrentUserTaskFinder` 不再是统一待办构造器依赖；若历史 OA 采购只读页仍需它，可由采购服务继续使用。
- `OaTodoMapper` 方法不再需要 `authorizedTaskIds` 参数。
- `OaTodoMapper.xml` 删除两个采购 union 分支，加入五个签约任务分支。
- 对通知死信需要额外路由信息时，使用 OA 内部候选 DTO，由 service 明确写入 `routeParams.taskId` 和 `notificationBusinessKey`；不把任意数据库 JSON 直接映射成前端路由参数。

### 4.5 system 提供方合并健康证流程

当前 `SysTodoServiceImpl` 在 Java 中将员工候选记录按 `deptId + type` 分组并分页。健康证流程继续沿用这一模式：

- 新增轻量候选查询，只取证件 ID、用户 ID、当前组织、审核状态、创建/更新时间和驳回原因。
- 待审核按组织分组，`affectedCount` 与健康证管理页的相同筛选总数必须一致。
- 驳回重提按证件 ID 返回个人事项，不按组织聚合。
- 合并现有 HR 分组项和个人项后统一排序，再进行内存分页，不能先 PageHelper 截断候选数据。

### 4.6 前端路由与精确聚焦

调整 `todoRouteResolver.js`：

```text
删除 OA_PURCHASE_APPROVAL / OA_PURCHASE_RETURNED
新增 INV_STOCK_CHECK_EXECUTE -> stockCheck / mobile stock-check
新增 OA_SIGN_*             -> /oa/sign-task
新增 HR_HEALTH_CERT_REVIEW  -> healthCertificate 管理页
新增 HR_HEALTH_CERT_RETURNED-> healthCertificate 我的记录
```

调整 `todoBusinessFocus.js`：

- `INV_STOCK_CHECK_EXECUTE` 使用 `checkId`，动作 `saveStockCheckInput`。
- 签约任务直接使用 `taskId` 打开已有抽屉，不自动执行重试、确认或发送。
- 健康证审核打开管理页并筛选；健康证驳回打开本人页并定位记录，不自动提交。

所有写操作继续遵守现有“详情先行”整改方向：从待办点击只打开业务详情，用户明确选择动作后才出现确认层。

## 5. OA 采购安全退役方案

不建议直接删除表和 Flowable 历史。采用“先停入口、再验证清空、最后清理代码”的两阶段退役。

### 5.1 发布前预检

至少核对：

```sql
SELECT status, COUNT(*) FROM oa_purchase GROUP BY status;

SELECT COUNT(*)
FROM oa_purchase p
JOIN ACT_RU_TASK t ON t.ID_ = p.current_task_id
WHERE p.status = 'submitted';

SELECT COUNT(*)
FROM ACT_RU_EXECUTION e
WHERE e.PROC_DEF_ID_ LIKE 'oaPurchaseApproval:%';
```

准入条件：

- 没有 `submitted` 采购申请。
- 没有 OA 采购运行任务或运行实例。
- 若存在历史记录，已确认保留的查询/导出责任人和保留期限。

任何活动实例非零都应阻断退役，不得删除 `ACT_*` 数据或通过隐藏待办让流程无人处理。应由业务负责人明确完成、驳回、关闭或作废，并留下操作日志。

### 5.2 第一阶段：立即停用

1. 新增 `feature.oa.purchase.enabled=false`，缺失或配置服务不可用时按关闭处理。
2. `save/submit/approve/close` 写入口统一执行 feature gate；历史只读查询是否保留由预检结果决定。
3. 从统一待办类型、mapper、service、前端路由矩阵和测试中删除两类 OA 采购待办。
4. 通过幂等 SQL 停用菜单 3002/3003/3004 及 3101～3106，并先备份 `sys_role_menu`；迁移使用 component/perms 双重识别并校验当前 ID，不能只假设所有环境 ID 完全一致。
5. 删除桌面静态 `/oa/todo/process` 和移动 OA 采购/待办/已办路由。
6. OA 首页改为合同、签约、考勤、固定资产等当前真实功能说明。

### 5.3 第二阶段：代码清理

经过至少一个稳定发布周期且活动实例持续为 0 后：

- 删除桌面 `views/oa/purchase`、`views/oa/todo`、`views/oa/done` 及 `api/oa/purchase.js`。
- 删除移动 `oaPurchase/oaTodo/oaDone` 的路由、表单、映射、动作、导出和导航分支。
- 删除不再需要的 OA 采购写控制器、服务和 BPMN 自动部署资源。
- 表、历史审批和审计记录不在应用发布中物理删除；如需归档，另做带备份和保留期的 DBA 方案。

## 6. 数据迁移与历史治理

新增并镜像：

```text
sql/erp_unified_todo_business_scope_20260713.sql
docker/mysql/db/erp_unified_todo_business_scope_20260713.sql
sql/erp_unified_todo_business_scope_rollback_20260713.sql
```

迁移内容：

1. 先把 `todo.stock-check.due-soon.hours`、`feature.oa.purchase.enabled` 的发布前配置状态（含“不存在”）写入带发布批次号的备份表，再插入但不覆盖管理员已有值的 `todo.stock-check.due-soon.hours=24`。
2. 插入但不覆盖已有值的 `feature.oa.purchase.enabled=false`；应用层仍以缺失即关闭的 fail-closed 规则为准。
3. 备份并停用 OA 采购相关菜单及角色映射；菜单、角色映射和配置备份都以发布批次号加业务键建立唯一约束，保证脚本重复执行不重复备份。
4. 验证 `counter_user_id`、`deadline`、`idx_stock_check_counter_due` 已存在；本方案不重复建一套盘点表。
5. 输出活动盘点治理清单，不自动批量伪造负责人或截止时间。

盘点预检：

```sql
SELECT status, COUNT(*)
FROM inv_stock_check
GROUP BY status;

SELECT check_id, check_no, shop_dept_id, create_by, create_time,
       counter_user_id, counter_name, deadline, status
FROM inv_stock_check
WHERE status IN ('draft', 'rejected', 'invalidated')
  AND (counter_user_id IS NULL OR deadline IS NULL)
ORDER BY create_time, check_id;

SELECT COUNT(*) AS stale_draft_count
FROM inv_stock_check
WHERE status='draft' AND create_time < DATE_SUB(NOW(), INTERVAL 7 DAY);
```

历史处理原则：

- 能确认继续执行的草稿，由业务负责人选择真实盘点人和新截止时间。
- 测试、重复或已失效草稿按现有取消接口逐单取消，保留操作日志。
- 无法确认责任人的记录保持不进入个人待办，并作为发布阻断清单处理，不能把创建人姓名直接当作责任人 ID。
- 不物理删除已有审批轮次或盘点明细。

## 7. 预计修改文件

### 7.1 共享契约

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`
- `erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java`

### 7.2 inventory 与盘点

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTodoTypes.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTodoMapper.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockCheckController.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckMapper.java`
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckMapper.xml`
- 新增盘点指派 DTO/候选人 VO，以及对应服务端校验测试
- `erp-ui/src/views/inventory/stockCheck/index.vue`
- `erp-ui/src/utils/todoRouteResolver.js`
- `erp-ui/src/utils/todoBusinessFocus.js`
- inventory 待办、盘点服务、路由和聚焦测试

### 7.3 OA 采购退役与签约接入

- `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaTodoTypes.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaTodoMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/BusinessFeatureGate.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java`
- `erp-ui/src/router/index.js`
- `erp-ui/src/views/oa/index.vue`
- `erp-ui/src/views/oa/signTask/index.vue`
- `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- `erp-ui/src/views/mobile/mobileNavigation.js`
- `erp-ui/src/views/mobile/feature/` 中 OA 采购专用映射、动作、表单、导出分支
- OA mapper/service/controller、签约任务、路由、移动功能测试

### 7.4 system / HR 健康证

- `erp-modules/erp-system/src/main/java/com/erp/system/constant/SysTodoTypes.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysTodoMapper.java`
- `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java`
- 健康证待办候选 VO/mapper 测试
- `erp-ui/src/views/hr/healthCertificate/index.vue`
- `erp-ui/src/views/mobile/hr/healthCertificate/index.vue`
- system 待办、健康证路由和前端交互测试

### 7.5 SQL 与发布资料

- 新增三份迁移/回滚 SQL，并保持 `sql/` 与 `docker/mysql/db/` 正向脚本完全一致
- 更新待办类型清单、角色验收矩阵和发布记录

## 8. 分阶段实施步骤

### 阶段 0：冻结真实基线

1. 当前工作区相关目标文件已有未提交改动，先由所有者形成一个可追溯基线提交或明确允许在现状上继续；不得使用 `reset --hard`、`checkout --` 或 stash 覆盖用户修改。
2. 保存当前 27 类型清单、三个 provider 摘要和列表样本。
3. 运行当前专项测试并记录通过数。
4. 对目标数据库执行 OA 采购、Flowable 活动实例、盘点草稿和菜单路由只读预检。

完成门：目标文件的起点、数据库活动事项和测试基线全部可追溯。

### 阶段 1：先写契约测试

1. 给 `TodoQuery.type` 写共享契约测试。
2. 写失败测试，要求 OA provider 不再包含两类采购。
3. 写失败测试，要求 inventory provider 包含 `INV_STOCK_CHECK_EXECUTE`，并覆盖截止前一秒、阈值边界、逾期和互斥状态。
4. 写失败测试，要求五类签约任务和两类健康证流程进入正确 provider。
5. 写前端失败测试，要求删除 OA 采购待办路由、增加盘点/签约/健康证精确跳转。

完成门：测试失败原因只来自目标能力尚未实现，而不是编译或环境基线破坏。

### 阶段 2：P0 OA 采购停用

1. 增加 fail-closed feature gate 并默认关闭。
2. 删除统一待办中的两个 OA 采购类型和 Flowable 待办依赖。
3. 清理桌面/移动待办路由、首页文案和 OA 采购入口。
4. 增加幂等菜单迁移、备份表和验收查询。
5. 证明 inventory 采购管理、质检、收货和退货测试完全不受影响。

完成门：任意账号都不能新建或审批 OA 采购；统一待办不再返回 OA 采购；库存采购照常运行。

### 阶段 3：调拨入口与待办文案收口

1. 运行或复核仓储导航归并迁移，确认 `inventory/transfer/index` 只有一个可见组件路由。
2. 保留 5 类调拨查询条件，按 `transfer_type` 改成真实业务标题。
3. 验证门店要货、返仓、异店调货在审批、发货、分批收货、差异和退回各阶段都只出现一次。
4. 桌面/移动待办跳转优先进入仓储作业入口，同时兼容旧收藏链接一个发布周期。

完成门：菜单、待办、详情使用同一调拨单，不因入口不同重复计数。

### 阶段 4：先修盘点责任人，再开放执行待办

1. 增加真实候选盘点人查询和选择控件。
2. 拆分 assignment 与 input 接口，后端从用户 ID生成姓名快照。
3. 在创建、改派、录入、提交、重盘时增加责任人、组织、账号状态和权限校验。
4. 将 rejected/invalidated 待办对象改为当前 `counter_user_id`，旧数据才回退 `submitted_user_id`。
5. 治理所有活动盘点中责任人或截止时间为空的数据。

完成门：不存在“姓名和用户 ID 不一致”的活动盘点，非指定盘点人不能代录。

### 阶段 5：新增盘点执行和期限提醒

1. 增加类型、配置和 mapper 参数。
2. 在同一 union 中实现 pending/due_soon/overdue 三态。
3. 服务层权限要求 `inv:stockCheck:list + inv:stockCheck:submit`。
4. 前端精确打开盘点单，只进入实盘录入，不自动提交。
5. 成功提交后触发 todo store 刷新；无差异完成、进入审批、取消都会立即移除执行待办。

完成门：同一盘点单任一时刻最多出现一条盘点执行/退回/重盘事项，数量与真实状态一致。

### 阶段 6：接通签约任务

1. 把 5 类类型加入 `OaTodoTypes`。
2. 将 `OaSignTodoProvider` 的状态、权限和 HR 身份规则迁入统一 OA provider。
3. 处理任务失败和通知死信的稳定键、去重和 routeParams。
4. 增加 `TodoQuery.type` 全 provider 过滤。
5. 修复签约任务页读取 `rows`，按 `taskId` 精确打开。
6. 完成后移除第二套 provider/VO，或明确改成统一 provider 内部辅助组件。

完成门：OA 摘要、OA 列表、桌面/移动总数和签约任务页对同一账号完全一致。

### 阶段 7：接通健康证流程

1. 增加待审核组织分组和员工驳回个人事项。
2. 复用现有健康证权限、数据范围和 feature gate。
3. 给健康证页面增加 route query 初始化、记录定位和已处理回退。
4. 审核/重提成功后刷新 unified todo。

完成门：待审核总数等于管理页相同筛选总数；驳回员工只看到自己的记录且能直接处理。

### 阶段 8：全量回归和真实 UI 验收

1. 三个 provider 的 summary、list、分页、type/category/source/scope/keyword 过滤全部回归。
2. 验证 provider 单独故障时前端仍保留其他来源和上次成功值。
3. 桌面 1440×900、1024×768；移动 390×844、430×932 验收。
4. 用门店、仓库、运营总监、盘点人、HR、普通员工、非候选管理员分别验证。
5. 所有写操作执行“详情 -> 显式选择动作 -> 确认”，不得从待办直接默认审批通过。

完成门：自动化、生产构建、真实数据库查询和角色 UI 矩阵全部通过。

## 9. 测试与验证命令

### 9.1 后端专项

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
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

### 9.2 前端专项

本机 Homebrew Node 当前缺少 `libsimdjson.30.dylib`，应使用项目工作区已验证可用的 bundled Node：

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
NODE=/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node

$NODE scripts/run-node-tests.cjs \
  unifiedTodoAggregation.test.js \
  unifiedTodoBusinessFocus.test.js \
  unifiedTodoDesktop.test.js \
  unifiedTodoMobile.test.js \
  unifiedTodoNavigator.test.js \
  unifiedTodoRouteResolver.test.js \
  unifiedTodoContextLease.test.js \
  signTaskCenter.test.js \
  hrHealthCertificate.test.js \
  transferStoreReturnFlow.test.js \
  transferDiscrepancyFlow.test.js
```

全前端门禁和构建可通过 bundled Node 调用现有 npm CLI：

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
NODE=/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node
NPM=/opt/homebrew/lib/node_modules/npm/bin/npm-cli.js
$NODE $NPM test
$NODE $NPM run build:prod
```

### 9.3 SQL 与原生数据库

```bash
cmp \
  /Users/liuxingyu/Desktop/备份/ERP-NEW/sql/erp_unified_todo_business_scope_20260713.sql \
  /Users/liuxingyu/Desktop/备份/ERP-NEW/docker/mysql/db/erp_unified_todo_business_scope_20260713.sql
```

迁移必须在隔离克隆库连续执行两次，第二次不得重复插入配置、菜单备份或角色映射。之后使用真实 MySQL 执行完整 inventory/OA/system provider SQL，不能只依赖 XML 绑定测试或 H2。

## 10. 角色验收矩阵

| 角色 | 必须看到 | 必须看不到/不能做 |
| --- | --- | --- |
| 门店要货人 | 自己退回的门店要货、目标门店待收货 | OA 采购申请；非本人审批 |
| 仓库人员 | 来源仓待发货、差异处理（有权限时） | 非来源仓发货；OA 采购 |
| 运营总监 | 自己是候选人的调拨/盘点审批 | 仅因是管理员而出现的非候选审批 |
| 指定盘点人 | 待盘点、临期、逾期、退回、重盘 | 其他盘点人的执行事项；非本人代录 |
| HR | 分配给自己的签约任务、授权组织健康证待审核 | 其他 HR 的签约任务、越范围员工健康证 |
| 员工本人 | 待签合同、自己的健康证驳回 | 他人签约包、他人健康证 |
| 非候选管理员 | 风险/管理事项按权限显示 | 绕过候选人校验的审批事项 |

## 11. 发布门禁、观察和回滚

### Gate A：OA 采购退役

- `oa_purchase.status='submitted'` 为 0。
- OA 采购 Flowable 运行任务和运行实例为 0。
- OA 采购菜单和移动入口不可见，写接口返回 feature disabled。
- inventory 采购全链路回归通过。

### Gate B：盘点执行提醒

- 所有活动 `draft/rejected/invalidated` 盘点都有真实责任人和截止时间，或已形成明确人工治理清单并不开放提醒。
- 指定盘点人拥有目标组织和提交权限。
- 阈值边界、跨时区和状态互斥测试通过。

### Gate C：签约与健康证

- 五类签约任务在统一 OA provider 中有且只有一套统计。
- 健康证待审核数与管理页对账一致。
- feature gate 关闭时不生成无法处理的员工重提待办。

### 观察指标

发布后至少观察一个完整工作日：

- 三个 provider 的成功率、耗时和超时数。
- `summary.total` 与各类别合计、列表总数差异。
- 重复 `todoKey`、无法解析路由、目标事项已处理的比例。
- 盘点逾期数量、无责任人活动盘点数。
- OA 采购写接口被调用次数；正常应为 0。
- 签约失败/死信待办与 outbox 实际 DEAD 数量差异。

### 回滚

- 代码回滚后运行 `erp_unified_todo_business_scope_rollback_20260713.sql` 恢复 OA 菜单角色映射和配置快照。
- 回滚不删除新配置，必要时恢复发布前值；不覆盖管理员在发布期间合法修改的其他配置。
- 盘点责任人和截止时间属于业务数据，回滚时不清空。
- OA 采购 feature gate 默认保持关闭；只有确认旧应用版本必须处理未完成历史流程时，才经审批临时开启。
- 不回滚或删除业务历史、Flowable 历史、签署证据、盘点审批记录。

## 12. 完成定义

本方案完成必须同时满足：

1. OA 采购申请在菜单、桌面、移动、统一待办和写接口上全部停用，库存采购不受影响。
2. 门店要货只通过 `warehouse` 调拨发起，调拨只有一套可见菜单和一套待办事实。
3. 调拨 5 类提醒覆盖门店要货、返仓和异店调货，且每个业务动作只出现一次。
4. 指定盘点人能看到待盘点、即将到期和逾期状态，非指定人员不能代录。
5. 盘点审批、退回、重盘与执行提醒互斥并能在业务状态变化后立即消失。
6. 五类 HR 签约任务和两类健康证流程进入统一待办，摘要、列表、桌面和移动数量一致。
7. 所有跳转都能定位真实业务记录；已处理事项给出“事项已处理”并刷新，不打开错误动作。
8. SQL 可重复执行、正向脚本镜像一致、回滚可用，自动化和真实角色 UI 验收全部通过。
