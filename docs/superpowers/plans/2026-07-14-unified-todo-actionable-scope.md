# 统一待办“本人可处理”范围修正实施方案

> 日期：2026-07-14
> 状态：方案已确认，待执行
> 业务口径：统一待办只展示当前账号此刻能够处理的事项，不展示其他人的专属任务，也不把其他门店的公共任务全部拉入。
> 环境约束：不得使用虚拟机、Docker 或 Testcontainers；验证使用本机进程、现有 MySQL 或独立测试 schema。

## 1. 目标与验收口径

统一待办最终固定为一个“可处理”口径：

1. 明确归属当前账号的专属待办，在本人仍有目标组织权限和业务操作权限时，跨当前所选门店显示。
2. 通过岗位、角色或部门形成的公共待办，只显示当前所选门店/仓库中当前账号有权处理的事项。
3. 全局运维类异常待办没有门店归属时，只对具备明确处理权限的账号显示。
4. 其他人的专属任务、其他门店的公共任务、已处理任务、状态已变化任务、权限不足任务均不显示。
5. 顶部角标、最近待办、电脑端列表、手机端列表和分类统计必须使用同一事实集合。
6. 点击跨门店专属待办时，沿用现有“临时切换目标门店，返回后恢复原门店”的上下文租约机制。

验收示例：用户 U 有门店 A、B 权限，当前选择 A。

| 事项 | 是否显示 | 原因 |
| --- | --- | --- |
| 明确指派给 U 的门店 A 任务 | 显示 | 本人可处理 |
| 明确指派给 U 的门店 B 任务 | 显示 | 本人专属任务跨门店 |
| 明确指派给其他人的门店 A/B 任务 | 不显示 | 不属于本人 |
| U 可处理的门店 A 公共角色任务 | 显示 | 当前门店公共待办 |
| U 可处理的门店 B 公共角色任务 | 不显示 | 不是当前门店 |
| 指派给 U、但 U 已失去门店 B 权限的任务 | 不显示 | 当前已无法处理，另做孤儿任务治理 |
| 没有选择门店时的本人专属任务 | 显示 | 不依赖当前门店 |
| 没有选择门店时的门店公共任务 | 不显示 | 缺少明确执行组织 |

## 2. 当前项目问题定位

当前实现只有 `current_org` 和 `all_authorized` 两种整批查询范围，无法表达“本人跨门店 + 公共任务当前门店”。

- `erp-ui/src/store/modules/todo.js` 在存在所选门店时把顶部汇总固定为 `current_org`。
- `erp-ui/src/utils/todoFilterQuery.js` 把桌面端和手机端默认范围设为 `current_org`。
- `erp-ui/src/views/workbench/todo/index.vue` 与 `erp-ui/src/views/mobile/todo/index.vue` 允许切换“当前组织/全部授权组织”。
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml` 对候选人为本人、创建人为本人、盘点人为本人的任务继续叠加当前组织条件。
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml` 对 `assigned_hr_user_id = 当前用户` 的任务继续叠加当前组织条件。
- `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml` 已把健康证退回按本人处理，但公共 HR 候选集合仍由整批 `scopeMode` 决定。
- 当前 mapper 测试主要断言 SQL 中同时存在“本人条件”和“组织条件”，没有验证跨门店本人任务，因此会在错误业务口径下继续通过。

现状结果是：

- 选择 `current_org` 会漏掉本人在其他门店的专属待办。
- 选择 `all_authorized` 会把其他门店的公共队列一起拉入。
- 页面“本人审批跨组织”的说明与部分后端 SQL 实际结果不一致。

## 3. 待办类型范围矩阵

### 3.1 本人专属、跨当前门店显示

这些任务必须同时满足“本人归属条件 + 目标组织仍在本人授权范围 + 类型所需操作权限”。

| Provider | 类型 | 本人归属事实 | 组织规则 |
| --- | --- | --- | --- |
| inventory | `INV_TRANSFER_APPROVAL` | 当前审批节点 `candidate_user_ids` 包含当前 `userId` | 目标组织属于本人授权范围 |
| inventory | `INV_STOCK_CHECK_APPROVAL` | 当前审批节点 `candidate_user_ids` 包含当前 `userId` | 目标组织属于本人授权范围 |
| inventory | `INV_TRANSFER_RETURNED` | 原单 `create_by = 当前 username` | 目标组织属于本人授权范围 |
| inventory | `INV_STOCK_CHECK_EXECUTE` | `counter_user_id = 当前 userId` | 目标组织属于本人授权范围 |
| inventory | `INV_STOCK_CHECK_RETURNED` | `counter_user_id/submitted_user_id = 当前 userId` | 目标组织属于本人授权范围 |
| inventory | `INV_STOCK_CHECK_RESTART` | `counter_user_id/submitted_user_id = 当前 userId` | 目标组织属于本人授权范围 |
| oa | `OA_LABOR_CONTRACT_SIGN` | `employee_id = 当前 userId` | 个人签署，不受当前门店限制 |
| oa | `OA_SIGN_PACKAGE_SIGN` | `employee_id = 当前 userId` | 个人签署，不受当前门店限制 |
| oa | `OA_SIGN_NEEDS_DATA` | `assigned_hr_user_id = 当前 userId` | 任务门店属于本人授权范围 |
| oa | `OA_SIGN_HR_CONFIRM` | `assigned_hr_user_id = 当前 userId` | 任务门店属于本人授权范围 |
| oa | `OA_SIGN_SEND_FAILED` | `assigned_hr_user_id = 当前 userId`，通知异常同时校验 payload 中的 HR 用户 | 任务门店属于本人授权范围 |
| oa | `OA_SIGN_REFUSED` | `assigned_hr_user_id = 当前 userId` | 任务门店属于本人授权范围 |
| oa | `OA_SIGN_EXPIRED` | `assigned_hr_user_id = 当前 userId` | 任务门店属于本人授权范围 |
| system | `HR_HEALTH_CERT_RETURNED` | `certificate.user_id = 当前 userId` | 本人资料退回，不受当前门店限制 |

### 3.2 当前门店公共待办

这些任务没有唯一个人所有者，依赖当前账号的岗位/菜单/动作权限；仅显示当前所选门店或仓库。

- inventory：采购质检、采购收货、销售出库通知创建、出库执行、调拨发货、调拨收货、调拨差异、OE 补货、采购退货确认、销售退货确认、缺货、低库存。
- system/HR：档案不完整、入职确认、合同到期、离职账号、健康证到期、健康证审核。
- 若当前组织为空、失效或不在当前账号授权范围，上述公共待办返回空集合，但不能影响本人专属待办。

### 3.3 全局权限队列

- `OA_FIXED_ASSET_REPLENISHMENT_DEAD` 当前没有可用门店归属字段，保留为全局运维异常队列。
- 只有同时具备 `oa:fixedAsset:outbox:list` 与 `oa:fixedAsset:outbox:replay` 的账号才可看见和处理。
- 后续若补充组织归属字段，再迁移为当前组织公共待办；本次不新增数据库字段。

## 4. 技术方案

### 4.1 引入统一范围 `actionable`

在共享契约中新增 `TodoConstants.SCOPE_ACTIONABLE = "actionable"`，将它作为统一待办的唯一默认业务范围。

兼容策略：

1. 后端第一版继续接受旧客户端传入的 `current_org`、`all_authorized` 或空值。
2. 三个 Provider 在校验阶段统一把这些旧值归一为 `actionable`，旧客户端不报错，但不再获得“全部门店公共任务”。
3. 新前端只发送 `actionable`，并移除“当前组织/全部授权组织”切换入口。
4. 至少保留一个发布周期的旧参数兼容；确认没有旧客户端后再考虑删除旧常量。

这样可以保证业务规则由服务端掌控，不能通过手工修改查询参数重新拉取其他门店公共待办。

涉及文件：

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoConstants.java`
- `erp-common/erp-common-core/src/main/java/com/erp/common/core/domain/todo/TodoQuery.java`
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTodoServiceImpl.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysTodoServiceImpl.java`

### 4.2 后端拆分两类组织过滤器

不能再使用一个 `scopeMode` 片段套住全部分支。每个 mapper 明确使用下列一种规则：

1. `DirectOwnerAuthorizedOrganizationScope`
   - 使用 `authorizedScopeDeptIds`。
   - 只用于已经有明确 `userId/username/assignee/candidate` 条件的专属任务。
   - 授权列表为空时 fail-closed，返回空。
2. `SharedCurrentOrganizationScope`
   - 使用 `currentScopeDeptIds`。
   - 只用于岗位/角色公共任务。
   - 当前门店为空、无效或未授权时 fail-closed，返回空。
3. `GlobalPersonalOrOperatorScope`
   - 仅限本人合同/签署、本人健康证退回和固定资产全局异常等没有组织过滤依据的分支。
   - 必须具备明确本人条件或成组操作权限，不能只因管理员身份直接显示个人任务。

库存 mapper 调整：

- 六类本人候选/创建/盘点任务改用 `DirectOwnerAuthorizedOrganizationScope`。
- 十二类公共执行/风险任务改用 `SharedCurrentOrganizationScope`。
- 所有分支输出统一的 `scope_mode = 'actionable'`。
- 保留当前 `TodoFacts` 复用结构，使 count、recent、list 从同一 SQL 事实集合生成。
- 不用一个大 `OR` 混合本人和公共范围，继续保持各类型 `UNION ALL` 分支，避免破坏索引选择和去重语义。

OA mapper 调整：

- 五类 HR 签约任务及通知失败分支，移除当前组织过滤，替换为本人授权组织过滤。
- 本人劳动合同与签署包继续仅按 `employee_id` 归属。
- 固定资产异常继续仅按成组权限启用。
- 所有分支输出统一的 `scope_mode = 'actionable'`。

System mapper/service 调整：

- 员工档案、入离职、合同和到期风险候选集合固定使用当前组织范围。
- 健康证审核固定使用当前组织范围。
- 健康证退回继续只按当前 `userId`，不因当前门店为空而丢失。
- 当前组织无效时公共集合为空，不应让整个 summary/list 请求失败或吞掉个人任务。

涉及文件：

- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml`
- `erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml`
- 三个 Provider 对应 mapper 接口与 service（仅在参数或归一化需要时调整）。

### 4.3 “能处理”由四层条件共同保证

每条待办必须通过以下四层；不新增逐条远程调用或 N+1 权限探测：

1. 状态事实：SQL 只选取当前确实待处理的状态和当前有效审批节点。
2. 人员事实：专属任务必须匹配当前用户 ID、用户名、候选人或指派人。
3. 组织事实：专属任务校验目标组织仍在授权范围；公共任务校验当前组织。
4. 动作权限：`resolveEnabledTypes()` 必须拥有该类型对应的列表权限和处理权限，前端 `requiredPermission` 与目标路由保持一致。

现有服务中的权限组合继续保留，例如调拨审批要求 `approve + list`，盘点执行要求 `submit + list`，签约 HR 各状态要求对应的 confirm/retry/revalidate 权限。

前端路由可用性不参与后端逐条计数，但通过静态路由覆盖测试保证所有可启用类型都有可访问处理页面；权限或路由在加载后变化时，沿用现有点击失败后刷新待办的机制。

### 4.4 前端统一成“我的可处理待办”

桌面端、手机端与顶部角标统一改为：

- 内部默认 `scopeMode = 'actionable'`。
- 移除“当前组织/全部授权组织”选择控件及相关筛选标签。
- 旧 URL 中的 `scope=current_org` 或 `scope=all_authorized` 自动规范化为 `scope=actionable`，不能恢复旧查询语义。
- 顶部 summary、页面 summary、分页 list 使用相同的 `scopeMode` 和 `contextDeptId`。
- 保留 `contextDeptId` 在缓存键中，因为当前门店公共待办仍随门店变化；本人专属部分虽相同，也不能单独缓存后与公共统计错误拼接。
- 门店切换时取消旧请求、丢弃旧响应并重新拉取，继续使用现有 request sequence/context version 保护。

文案统一为：

- 有当前门店：`本人专属待办跨已授权门店 · 公共待办：当前门店（门店名）`。
- 无当前门店：`本人专属待办正常显示 · 选择门店后显示公共待办`。
- 不再出现“全部授权组织”“本人审批跨组织但执行当前组织”等容易误解的表述。

涉及文件：

- `erp-ui/src/utils/todoFilterQuery.js`
- `erp-ui/src/store/modules/todo.js`
- `erp-ui/src/views/workbench/todo/index.vue`
- `erp-ui/src/views/mobile/todo/index.vue`
- `erp-ui/src/layout/components/HeaderTodo/index.vue`
- 如请求参数契约需要同步：`erp-ui/src/api/workbench/todo.js`

### 4.5 跨门店点击与上下文恢复

现有实现已经具备正确基础，本次只做回归，不重写：

- `erp-ui/src/utils/todoRouteResolver.js` 将库存和 HR 签约处理页标记为 `organizationBound`。
- `erp-ui/src/utils/todoNavigator.js` 在跨门店点击前创建上下文租约，再临时调用 `setSelectedDept`。
- `erp-ui/src/utils/todoContextLease.js` 保存原组织和返回路由，回到待办页时恢复。
- `erp-ui/src/permission.js` 在门店必选路由检查前执行恢复。

新增验收必须覆盖：当前门店 A 点击本人门店 B 待办后切到 B；处理页请求携带 B；返回待办页恢复 A；用户在处理页主动改到 C 时不强制覆盖用户选择。

## 5. 分阶段实施步骤

### 阶段 0：建立失败用例和变更边界

目标：先用测试准确复现当前错误，防止只改文案或只改某一个 Provider。

1. 在 inventory/OA mapper 测试中新增“本人任务不得引用 current scope”断言，当前代码应先失败。
2. 新增三门店矩阵测试：A/B 授权、C 未授权、当前 A。
3. 新增前端默认范围测试，要求顶部、桌面、手机都发送 `actionable`。
4. 记录当前相关文件 diff；当前工作区存在大量并行改动，实施时只处理本方案显式文件，禁止 `git add .` 或覆盖无关修改。

完成门：失败原因准确指向当前组织叠加过滤或旧前端范围，而不是测试夹具/环境问题。

### 阶段 1：共享契约和后端兼容入口

目标：后端先能接受新前端参数，同时旧前端不报错。

1. 新增 `SCOPE_ACTIONABLE`，设置 `TodoQuery` 默认范围。
2. 三个 Provider 把空值及两个旧值归一为 `actionable`。
3. 非法第三方值仍返回“组织范围不支持”，不能默默接受任意值。
4. 更新 common/controller/service 契约测试。

完成门：使用 `actionable/current_org/all_authorized/空值` 调用时都进入同一安全范围；非法值失败。

### 阶段 2：三个 Provider 的事实范围修正

顺序：inventory -> OA -> system。

1. Inventory 先拆分专属与公共组织片段，逐分支替换并运行 mapper/service 测试。
2. OA 把 HR 明确指派任务改为授权组织范围，保留员工本人和全局异常分支。
3. System 把共享员工集合、健康审核固定到当前组织，并验证健康证退回不受当前门店影响。
4. 对 summary count、recent、list 使用完全相同的事实集合做断言。
5. 对每个类型确认 `requiredPermission`、路由与实际处理接口权限一致。

完成门：三门店矩阵全部满足第 1 节；不同接口间无数量或可见性差异。

### 阶段 3：桌面端、手机端和顶部入口统一

1. 更新筛选规范化，旧范围 URL 转成 `actionable`。
2. 移除桌面和手机端组织范围切换控件。
3. 顶部汇总、最近待办、桌面分页、手机分页全部发送 `actionable`。
4. 更新范围说明、ARIA 文案、空状态和激活筛选标签。
5. 门店变化时验证旧缓存不会短暂覆盖新门店结果。

完成门：三个入口显示同一总数；没有任何用户入口能切到“全部门店公共任务”。

### 阶段 4：跨门店处理与状态回收

1. 回归现有导航和上下文租约测试。
2. 使用本人门店 B 的调拨审批、盘点执行或 HR 签约任务验证临时切店。
3. 完成、驳回或提交后立即刷新顶部 summary 与当前列表。
4. 模拟任务被其他人先处理：处理接口返回不可操作后刷新，待办从列表消失。
5. 模拟权限撤销：刷新后不再显示；不能仅在点击时才提示无权限。

完成门：不存在“看得见但点不开/处理不了”的稳定状态；并发状态变化能通过刷新收敛。

### 阶段 5：性能、发布与观察

1. 对 inventory 和 OA 的本人跨组织分支执行 `EXPLAIN`，重点检查审批状态、任务状态和组织条件是否先缩小数据集。
2. 比较修改前后 summary/list 响应时间；不能以全表扫描换取可见性正确。
3. 先发布三个后端 Provider，再发布前端；新前端不能先于支持 `actionable` 的后端。
4. 使用真实普通门店账号、跨店审批账号、HR、仓库账号分别验收。
5. 观察按 Provider/type 的数量、接口错误率和 P95 延迟；发现异常先回滚前端，再回滚后端。

完成门：真实账号矩阵通过、无越权、无数量漂移、性能在当前基线容许范围内。

## 6. 自动化测试改造清单

### 6.1 后端

需要更新或新增：

- `erp-common/erp-common-core/src/test/java/com/erp/common/core/domain/todo/TodoContractTest.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvTodoMapperBindingTest.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTodoServiceImplTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaTodoMapperBindingTest.java`
- `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysTodoMapperBindingTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysTodoServiceImplTest.java`
- 三个 Todo controller 测试中的范围参数用例。

核心断言：

1. 专属分支包含本人归属条件与授权组织条件，不包含当前组织片段。
2. 公共分支包含当前组织片段，不使用全部授权范围。
3. 其他用户的专属任务即使在当前门店也不出现。
4. 本人在其他已授权门店的专属任务出现。
5. 本人在未授权门店的指派任务不出现。
6. 无动作权限时 Provider 不启用对应类型。
7. summary、recent 和 list 对同一筛选条件使用相同结果集合。

针对性命令：

```bash
mvn -pl erp-common/erp-common-core,erp-modules/erp-system,erp-modules/erp-oa,erp-modules/erp-inventory -am \
  -Dtest=TodoContractTest,SysTodoMapperBindingTest,SysTodoServiceImplTest,OaTodoMapperBindingTest,OaTodoServiceImplTest,InvTodoMapperBindingTest,InvTodoServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### 6.2 前端

需要更新或新增：

- `erp-ui/test/unifiedTodoFilterQuery.test.js`
- `erp-ui/test/unifiedTodoStore.test.js`
- `erp-ui/test/unifiedTodoDesktop.test.js`
- `erp-ui/test/unifiedTodoMobile.test.js`
- `erp-ui/test/unifiedTodoAggregation.test.js`
- `erp-ui/test/unifiedTodoNavigator.test.js`
- `erp-ui/test/unifiedTodoContextLease.test.js`
- `erp-ui/test/unifiedTodoRouteResolver.test.js`

核心断言：

1. 所有入口默认并固定发送 `actionable`。
2. 旧 URL 的两个 scope 值被规范化，页面不再提供旧范围控件。
3. 顶部、桌面和手机文案不再承诺错误的组织范围。
4. 门店切换后旧请求结果被丢弃。
5. 跨门店任务可切换、失败可回滚、返回可恢复。
6. 所有后端可启用待办类型都有桌面或手机处理路由。

针对性命令：

```bash
cd erp-ui
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoFilterQuery.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoStore.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoDesktop.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoMobile.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoNavigator.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoContextLease.test.js
/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node test/unifiedTodoRouteResolver.test.js
```

完成针对性测试后运行：

```bash
cd erp-ui
npm test
npm run build:prod
```

## 7. 真实角色验收矩阵

测试数据至少准备门店 A、门店 B、未授权门店 C，以及当前用户 U、其他用户 V。

| 场景 | 预期 |
| --- | --- |
| U 当前 A，U 的 A/B 调拨审批 | 两条都显示 |
| U 当前 A，V 的 A 调拨审批 | 不显示 |
| U 当前 A，U 的 C 调拨审批 | 不显示 |
| U 当前 A，A 的公共收货/发货任务 | 有对应权限时显示 |
| U 当前 A，B 的公共收货/发货任务 | 不显示 |
| U 当前 A，U 的 B 盘点执行/退回 | 显示，点击临时切到 B |
| U 当前 A，分配给 U 的 B 签约 HR 任务 | 显示，点击临时切到 B |
| U 没有选择门店，本人合同/健康证退回 | 显示 |
| U 没有选择门店，门店公共任务 | 不显示 |
| 删除 U 的处理权限后刷新 | 对应类型全部消失 |
| 任务由 V 抢先处理后 U 刷新 | 顶部和列表同时消失 |
| 从 B 处理页返回待办中心 | 恢复原门店 A |

对每个场景同时核对：顶部角标、最近五条、分类数量、分页总数、桌面端和手机端。

## 8. 风险与处理

### 8.1 已指派但权限被撤销的孤儿任务

业务要求是不向本人展示无法处理的任务，但任务不能永久无人处理。第一阶段先从个人待办中隐藏，并输出可审计的按类型/组织统计；管理员重新分配页面或定时治理任务作为独立 P1，不混入本次范围修正。

禁止为解决孤儿任务而让管理员在“我的待办”中看到所有人的专属任务。

### 8.2 旧客户端兼容

后端先发布且归一旧参数，旧客户端只会看到新的安全结果，不会 400。新前端发布后使用 `actionable`。回滚时先回滚前端，再回滚后端。

### 8.3 数量不一致

禁止在前端仅过滤列表而保留后端旧统计。所有范围修正必须发生在 Provider 的共同事实集合中，再由该集合生成 count/recent/list。

### 8.4 SQL 性能

本人跨门店会扩大专属分支扫描范围。通过状态条件、候选/指派条件和授权组织条件共同收敛；保持按类型分支，不构造跨全部类型的大 OR。若真实数据 `EXPLAIN` 显示退化，再单独评估索引或候选人关系表，不在未验证前新增索引。

### 8.5 当前工作区风险

当前实施分支存在大量未提交并行改动，且本方案涉及的 mapper、service、页面和测试本身已有修改。执行时必须：

1. 修改前保存相关文件 diff 快照。
2. 用 `apply_patch` 做小范围编辑。
3. 每阶段运行 `git diff --check -- <显式文件>`。
4. 不恢复、不覆盖、不暂存无关文件。
5. 不创建或使用虚拟机、Docker、Testcontainers。

## 9. 发布与回滚顺序

发布：

1. common 契约与三个后端 Provider 一起发布，先支持 `actionable` 和旧参数归一。
2. 验证旧前端请求已返回新的安全结果。
3. 发布新前端，移除组织范围切换并统一文案。
4. 清理浏览器旧缓存，执行真实角色矩阵。
5. 观察至少一个完整业务高峰周期后，再决定是否删除旧参数常量。

回滚：

1. 先回滚前端到仍发送旧参数的版本。
2. 再回滚三个后端 Provider/common 契约。
3. 本次不涉及数据库结构或数据迁移，无数据库回滚脚本。
4. 回滚后立即复核是否重新出现“本人跨门店任务缺失/全部公共任务混入”，并保留事件记录。

## 10. 完成定义

以下条件全部满足才算完成：

- 所有 Provider 都以服务端 `actionable` 规则为权威，不可通过 URL 改回全部公共任务。
- 本人专属任务跨当前门店显示，但不越过本人授权组织和动作权限。
- 公共任务只显示当前门店；无当前门店时公共集合为空。
- 其他人的专属任务永不进入当前用户 summary/recent/list。
- 顶部、桌面、手机、分类统计和分页总数一致。
- 跨门店点击、失败回滚和返回恢复测试通过。
- 针对性后端测试、前端测试、完整前端测试和生产构建通过。
- 完成真实角色三门店矩阵验收。
- 未使用虚拟机、Docker 或 Testcontainers，未覆盖工作区其他修改。
