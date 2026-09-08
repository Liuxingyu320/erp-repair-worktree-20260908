# ERP-NEW_2 后端契约审阅清单

> 日期：2026-08-03
> 状态：阶段 1 滚动基线，未冻结契约
> 数据来源：当前隔离工作树的前端 API、Controller、网关函数式路由、权限声明和 SQL

## 1. 覆盖结论

| 项目                         | 数量 | 结论                                                                                            |
| ---------------------------- | ---: | ----------------------------------------------------------------------------------------------- |
| 前端 API 调用                |  526 | 全部进入基线，其中 2 条为上传/下载组件间接 action                                               |
| 后端 Controller/网关端点     |  631 | 全部进入基线候选                                                                                |
| 方法与规范化路径精确候选匹配 |  523 | 只能证明路径候选存在，尚未证明请求/响应模型一致                                                 |
| 动态分派、需人工展开         |    3 | 均已定位实际分支，见第 2 节                                                                     |
| 权限标识                     |  240 | 已按前端、后端和 SQL 来源分类                                                                   |
| 证据源文件                   |  796 | 旧能力与新目标输入均已记录大小与 SHA-256，覆盖新前端 API、页面、领域、平台、Provider 与测试夹具 |

当前没有证据证明存在“前端调用但后端完全缺失”的静态端点；仍需通过版本化 OpenAPI 和契约测试验证参数、请求体、响应体、错误、日期、金额、文件和数据范围。

## 2. 三个动态 API 分派

### CR-001 统一审批动作分派

- 前端：`POST /approval/tasks/{taskId}/{action}`
- 位置：`erp-ui/src/api/approval/task.js`
- 实际 action：`approve`、`return`、`reject`
- 后端证据：`ApprovalTaskController` 分别声明 `/{id}/approve`、`/{id}/return`、`/{id}/reject`。
- 判定：不是缺失接口；生成器无法把一个动态客户端函数自动展开为三条 OpenAPI operation。
- 建议：新客户端生成三个显式类型函数，业务适配层可以再提供受限联合类型分派；禁止接受任意 action 字符串。

### CR-002 用户 PII 更新分派

- 前端：`PUT /system/user/{userId}/{afterCreate ? 'pii-after-create' : 'pii'}`
- 位置：`erp-ui/src/api/system/user.js`
- 后端证据：`SysUserController` 分别声明 `/{userId}/pii` 和 `/{userId}/pii-after-create`。
- 判定：不是缺失接口；两条端点的权限和使用窗口不同。
- 建议：新客户端必须拆成普通 PII 更新与新建后短窗口更新两个显式命令，不能用布尔参数隐藏权限差异。

### CR-003 统一待办 Provider 分派

- 前端：`GET {provider}/{endpoint}`
- 位置：`erp-ui/src/api/workbench/todo.js`
- Provider：`approval`、`inventory`、`oa`、`system`
- endpoint：`summary`、`list`
- 后端证据：四个领域均存在 `/todo/summary` 和 `/todo/list` Controller 端点。
- 判定：不是缺失接口；一个客户端函数运行时映射为八条实际契约。
- 建议：OpenAPI 层保留八条显式生成函数，工作台适配层使用固定 Provider 注册表聚合；未知 Provider 默认拒绝。

## 3. 响应封装差异

Controller 静态返回类型至少包括：

- `AjaxResult`：456 条；
- `TableDataInfo`：82 条；
- `void`：45 条；
- `ResponseEntity<Resource>`：10 条；
- `R<?>`、`R<Boolean>`、`R<String>`、`R<Long>` 和多个领域 DTO；
- `ResponseEntity<StreamingResponseBody>` 等文件流响应。

需要冻结的目标规则：

1. 普通对象、分页列表、文件流和异步任务分别定义统一契约，不强行把文件响应包进 JSON。
2. 分页适配层统一提供 `items`、`total`、`page`、`pageSize`，同时兼容旧 `rows`/`total` 输入。
3. 错误统一包含稳定业务码、用户可读消息、字段错误、冲突类型和追踪编号。
4. 页面不得直接依赖 `AjaxResult` 或 `TableDataInfo` 原始结构，必须经过领域适配层。
5. OpenAPI 生成代码只位于 `erp-ui-next/src/api/generated/`，手写业务适配位于领域或 `src/api/adapters/`。

## 4. 幂等审阅范围

静态注解结果：

| 方法   | 有 `@IdempotentSubmit` | 无 `@IdempotentSubmit` |
| ------ | ---------------------: | ---------------------: |
| POST   |                     95 |                    141 |
| PUT    |                      6 |                     48 |
| DELETE |                     16 |                     32 |
| PATCH  |                      0 |                      1 |

“无注解”不等于漏洞：查询型 POST、内部回调、登录、文件流和天然幂等的 PUT/DELETE 需要分别判断。但以下改变业务事实的命令必须逐条验证唯一请求标识、重复结果、并发冲突和事务回滚：

- 调拨提交、审批、撤回、发货、收货和差异处理；
- 采购、销售、退货、盘点和库存调整；
- OA 审批与固定资产动作；
- 人事入职、转岗、离职、健康证和签署动作；
- 文件移动、恢复、删除和配额修改。

## 5. 权限来源差异

### 前端单边引用

0 项。当前未发现只在前端出现、后端和 SQL 都无证据的已知权限标识。

### 后端单边引用

13 项，必须确认是详情读取权限、内部接口还是菜单/按钮漏配：

```text
hr:employee:regularize
hr:employee:renewal
inv:transfer:discrepancy:adjudicate
inv:transfer:discrepancy:execute
system:dept:query
system:dict:query
system:menu:query
system:notice:query
system:post:query
system:role:query
system:user:query
tool:gen:list
tool:gen:query
```

### SQL 单边引用

12 项，必须确认是角色授权集合、通配权限、历史遗留还是后端漏实现：

```text
hr:employee:add
hr:export:missing
hr:export:profile
hr:import:preview
hr:team:healthCertificate
oa:done:*
oa:done:list
oa:purchase:*
oa:todo:*
oa:todo:list
system:logininfor:query
system:salary:import
```

单边引用在核验完成前不能据此删除权限、菜单或页面。

## 6. 数据范围、日期、金额与文件

### 数据范围

- Controller 注解不足以证明实际数据范围，必须继续追踪 `resolveShopDeptId`、`ShopHeaderUtils`、`checkUserDataScope`、Mapper 条件和跨组织汇总逻辑。
- 新前端所有写操作必须显示实际操作组织；跨组织只读汇总和代表具体组织执行必须是不同能力。
- 库存列表与汇总只允许提交 `ownOnly`，具体可见组织集合必须由服务端依据当前账号和组织计算；前端不得接受任意组织 ID 作为扩权筛选参数。

### 日期时间

- 业务日期目标为无时区年月日；操作时间目标为带时区时间点，并按 `Asia/Shanghai` 展示。
- 需要在 OpenAPI 中区分 `date` 与 `date-time`，禁止仅凭 Java `Date` 或字符串猜测语义。

### 金额与数量

- 前端目标类型为十进制字符串，业务计算使用 Decimal 类库。
- 库存调拨数量、单位换算、批次成本和损失金额必须冻结精度与舍入规则，禁止由页面自行推导库存余额。
- 当前库存列表、详情和汇总响应仍可能向没有 `inv:cost:view` 的角色返回 `costPrice` 或 `totalCost`。新前端适配器已丢弃这些字段，但这不能替代服务端按权限脱敏；完成服务端修复和负向契约测试前，真实库存 Reader 不得注入生产运行时。
- 采购订单只读 Reader 已冻结 `pageNum`、`pageSize`、`orderNo`、`orderTitle`、`supplierName`、`status`、`params[beginOrderDate]` 和 `params[endOrderDate]` 的兼容映射，并对组织漂移与跨仓返回失败关闭；真实运行时仍未注入。
- 库存盘点只读 Reader 已冻结 `pageNum`、`pageSize`、`checkNo`、`status`、`params[beginCheckDate]` 和 `params[endCheckDate]` 的兼容映射，并对组织漂移、冲突组织和跨组织返回失败关闭；真实运行时仍未注入。
- 盘点列表的 `totalDiffQuantity` 可能跨商品单位聚合，新前端不把它作为业务指标；只使用可解释的 `profitItemCount` 与 `lossItemCount`。
- 库存报表 `/inventory/report/summary` 已在服务端按 `inv:cost:view` 或管理员身份保留成本字段；普通报表角色收到的 `totalStockCost`、`purchaseAmount`、`salesCost` 和 `grossMargin` 为 `null`，新 HTTP Gateway 因而可为所有报表角色读取完整的非成本经营摘要。`summary`、`stock-warning`、报表专属 `product-options` 与独立权限 `stock-warning/export` 均声明 `no-store`，前端缺失该指令即失败关闭；服务端脱敏、缓存、商品候选和安全导出负向测试已落地。真实独立环境、版本化 OpenAPI 和生产等价角色验收仍未执行。
- 库存报表经营日期参数只进入 summary 的 `params[beginTime]` / `params[endTime]`；`stock-warning` 是当前库存快照且 Mapper 不消费日期条件。新页面已明确口径并禁止向预警请求发送无效日期参数。
- 客户服务卡列表只使用 `GET /inventory/customer/service-card/list` 与 `inv:customerCard:list`；详情、照片、审计和写操作继续由独立权限隔离，列表权限不能替代这些权限。
- 客户服务卡后端要求门店上下文。新 Reader 不接受门店 ID 查询参数，只发送当前会话的 `Dept-NumId`，并在请求前拒绝组织漂移、解析后拒绝跨门店记录。
- 客户服务卡成功响应包含姓名、手机号、偏好、注意事项和预算等服务资料，Reader 要求 `Cache-Control: no-store`，缺失即失败；默认运行时只注入脱敏 Mock，真实 Reader 未注入。
- 新客户服务卡列表模型只保留服务所需字段，把照片节点转换为布尔值，并丢弃信用额度、已用额度、账期、邮箱、地址、服务记录、请求键和内部版本。旧客户财务接口和导出不进入本切片目标。
- 销售订单只读切片只使用 `GET /inventory/sales/list` 与 `inv:sales:list`；详情、保存、提交、取消、导出、配送通知和发货继续由独立权限隔离，列表权限不能替代这些权限。
- 销售订单 Reader 只发送当前门店的 `Dept-NumId`，不接受 `shopDeptId`、`scopeDeptIds` 或 `dataScope` 参数；请求前拒绝组织漂移，解析后拒绝任一跨门店记录。真实 Reader 默认未注入。
- 销售订单金额保持非负十进制字符串，业务日期固定为 `YYYY-MM-DD`；Mapper 返回的 `totalQuantity`、`deliveredQuantity` 和 `remainingQuantity` 可能跨商品单位汇总，新列表模型全部丢弃，不把它们作为履约指标。
- 当前销售列表端点没有客户服务卡同等级的显式 `Cache-Control: no-store` 证据。完成服务端缓存策略、版本化 OpenAPI 和生产等价负向测试前，兼容 Reader 不得注入生产运行时。
- 销售退货只读切片只使用 `GET /inventory/salesReturn/list` 与 `inv:salesReturn:list`；“我的退货”、详情、保存、提交、确认、取消和导出继续由独立端点或权限隔离，列表权限不能替代这些能力。
- 销售退货 Reader 只发送当前门店的 `Dept-NumId`，不接受 `shopDeptId`、`scopeDeptIds` 或 `dataScope` 参数；请求前拒绝组织漂移，解析后拒绝任一跨门店记录。真实 Reader 默认未注入。
- 销售退货金额保持非负十进制字符串，业务日期固定为 `YYYY-MM-DD`；Mapper 返回的 `totalQuantity`、`returnedQuantity` 和 `remainingQuantity` 可能跨商品单位汇总，新列表模型全部丢弃。
- 当前销售退货列表端点没有显式 `Cache-Control: no-store` 证据。完成服务端缓存策略、版本化 OpenAPI 和生产等价负向测试前，兼容 Reader 不得注入生产运行时。
- 配送通知只读切片只使用 `GET /inventory/deliveryNotice/list` 与 `inv:deliveryNotice:list`；详情、生成通知、执行发货、取消和导出继续由独立端点或权限隔离，列表权限不能替代这些能力。
- 配送通知 Reader 只发送当前销售门店或发货仓库的 `Dept-NumId`，不接受 `shopDeptId`、`warehouseId`、`scopeDeptIds` 或 `dataScope` 参数；请求前拒绝组织漂移。门店上下文解析后拒绝跨销售门店记录，仓库上下文还要求每行拥有匹配当前仓库的唯一 `warehouseId`。
- 多仓配送通知可能只返回聚合 `warehouseName` 且 `warehouseId` 为空；这不能证明逐仓授权，新 Reader 在仓库上下文对该响应失败关闭。完成响应契约拆分和逐仓负向测试前，真实 Reader 不得注入生产运行时。
- 配送通知 Mapper 返回的 `noticeQuantity`、`deliveredQuantity` 和 `remainingQuantity` 可能跨商品单位聚合，新列表模型全部丢弃；来源销售单 ID、详情、内部操作人、数据范围参数和更新字段也不进入模型。
- 当前配送通知列表端点没有显式 `Cache-Control: no-store` 证据。完成服务端缓存策略、版本化 OpenAPI 和生产等价负向测试前，兼容 Reader 不得注入生产运行时。
- OA 采购申请只读切片只使用 `GET /oa/purchase/my` 与 `oa:purchase:list`；详情、保存、提交、撤回、关闭、导出和审批动作继续由独立端点或权限隔离，列表权限不能替代这些能力。
- `selectMyPurchases` 在服务端强制使用当前登录用户作为申请人。新 Reader 不接受 `applicantId`、`shopDeptId`、`scopeDeptIds` 或 `dataScope` 查询参数，只发送当前叶子组织的 `Dept-NumId`；请求前拒绝组织漂移，解析后要求每行申请人和组织都精确匹配当前会话。
- OA 采购申请状态闭集固定为 `draft`、`submitting`、`pending`、`approved`、`returned`、`rejected`、`withdrawn`、`terminated`、`cancelled`；金额保持可空的非负十进制字符串，审批轮次为非负安全整数。审批实例 ID、行版本、审批事件键、内部操作人和原始数据范围参数不进入新列表模型。
- 当前 OA 采购申请列表端点没有显式 `Cache-Control: no-store` 证据。完成服务端缓存策略、版本化 OpenAPI 和申请人/组织负向契约测试前，兼容 Reader 不得注入生产运行时。
- 统一审批实例工作台使用 `GET /approval/instances` 与 `approval:instance:list` 读取列表，使用 `GET /approval/instances/{id}` 与 `approval:instance:query` 开放管理员详情；列表权限不能替代详情或任一命令权限。
- `ApprovalRuntimeController.list` 与 `selectInstanceList` 没有当前组织数据范围条件，SQL 种子只把审批管理菜单和 `approval:*` 权限赋给有效超级管理员。新适配器显式省略组织头，不接受 `anchorDeptId`、`applicantUserId`、`params` 或任意数据范围参数，页面明确标注管理员跨组织审计。
- `ApprovalRuntimeController.detail` 只声明登录态，Service 则允许持有 `approval:instance:query` 的管理员或实例参与者读取。Controller 注解、旧页面按钮权限和 Service 实际权限不一致；完成注解统一与管理员/非参与者/参与者负向测试前，真实详情 Reader 不得注入。
- 统一审批实例、任务、候选人、动作和回调按后端闭集校验，并验证 Long/Integer、时间、归属 ID、重复 ID、计数和状态不变量。业务/路由快照、摘要、幂等键、规则内部 ID、用户 ID、动作键与请求 ID、回调 payload/服务/代码、锁和内部审计字段全部在新详情模型中丢弃。
- 管理员终止、改派和回调重放分别使用 `POST /approval/instances/{id}/terminate`、`POST /approval/tasks/{id}/reassign` 与 `POST /approval/callbacks/{id}/replay`，并分别要求 `approval:instance:terminate`、`approval:task:reassign` 与 `approval:callback:replay`。终止只接受 `RUNNING` 实例，改派只接受 `RUNNING` 实例内的 `PENDING` 任务/候选人，重放只接受 `RETRY` 或 `DEAD` 回调。
- 三类管理员命令要求 1～500 字原因和安全 `requestId`；同一次确认与不确定结果重试必须复用同一标识，冲突复用失败关闭。命令省略组织头并必须发送 CSRF，页面不能手填内部目标 ID。
- 列表、详情和三个管理员命令的成功响应都必须声明 `Cache-Control: no-store`，缺失时新适配器失败关闭。当前后端没有该响应头证据；完成服务端缓存策略、版本化 OpenAPI、权限负向测试和跨组织数据审计前，真实 Reader/Writer 不得注入生产运行时。
- 审批流程定义只读目录只使用 `GET /approval/templates` 与 `approval:template:list`；模板详情、规则、版本、草稿、编辑、发布、停用、候选人预览和配置检查继续由独立端点或权限隔离，列表权限不能替代这些能力。
- `ApprovalDefinitionController.templates` 与 `ApprovalTemplateMapper.selectTemplateList` 是系统级模板目录，没有当前组织数据范围条件。新 Reader 显式省略组织头，只发送 `pageNum`、`pageSize`、`businessCode`、`templateName`、`businessSource`、`engineMode` 和 `templateStatus`，不发送 `includeDisabled`、`definitionMode`、`params` 或组织 ID。
- 审批模板业务代码、来源、引擎模式、定义模式和状态按当前后端常量与种子闭集校验；新列表模型只保留模板业务事实，丢弃旧适配器、回调服务、锁版本、内部操作人、时间、备注和原始参数。
- 跨组织审批模板响应必须声明 `Cache-Control: no-store`，缺失时新 Reader 失败关闭。当前列表端点没有该响应头证据；完成服务端缓存策略、版本化 OpenAPI、管理员权限负向测试和跨组织配置审计前，Reader 不得注入生产运行时。
- 审批配置检查工作台使用 `GET /approval/validation/runs`、`GET /approval/validation/runs/{id}` 与 `approval:validation:list` 读取批次和明细；`POST /approval/validation/run` 只由独立 `approval:validation:run` 权限开放，列表权限不能替代执行权限。
- 检查列表 Mapper 实际只消费 `templateId`、`ruleId`、`ruleVersionId`、`validationType` 和 `runStatus`。旧 `ValidationPanel.vue` 发送的 `businessCode` 与 `status` 无效；新 Reader 不复制这些参数。
- 旧页面执行检查发送 `businessCode`、`scopeMode`、`anchorDeptId` 和 `businessSubtype`，但当前 Controller DTO 的唯一必填字段是 `versionId`，Service 只消费版本 ID 与可选 `validationType`。新 Writer 只发送规范化 `{ versionId, validationType }`，不复制旧范围字段、模板 ID 或规则 ID。
- 执行检查会在 `REQUIRES_NEW` 事务内新增批次和问题，最终返回 `{ run, issues }`；后端只校验版本、规则和模板存在，不校验版本状态。页面不得伪造仅发布版本可检查或当前组织限定执行范围的保证。
- 当前 POST 没有业务 `requestId`、幂等注解或去重协议。页面提交中禁止重复触发；结果不确定时只允许先按精确版本刷新批次再决定是否重跑，不自动重试，也不把 HTTP 相关性请求头解释为业务幂等键。
- 执行检查必须发送 CSRF 并显式省略组织头；成功响应必须声明 `Cache-Control: no-store`。默认 Provider 只写入 ERP-NEW_2 自有可变 Mock，真实 Writer 在缓存、权限、CSRF 和生产契约门禁完成前保持未注入。
- 列表与明细属于管理员系统级跨组织审计视图；新 Reader 显式省略组织头和 CSRF。两个成功响应都必须声明 `Cache-Control: no-store`，缺失时失败关闭，真实 Reader 默认未注入。
- 新契约校验 Java Long、非负计数、时间、类型/状态/级别闭集、批次状态不变量，以及明细问题数、级别合计和 `runId` 一致性；批次快照、问题快照、内部目标 ID、内部操作人和用户 ID均被丢弃。
- 审批规则版本只读审计工作台只使用 `GET /approval/rules`、`GET /approval/rules/{id}`；路由和导航必须同时满足 `approval:template:list` 与 `approval:template:query`，规则新建/编辑、草稿保存、候选人预览、配置检查执行、版本发布、规则停用和导出均不进入本切片。
- 规则列表只发送 Mapper 实际消费的 `templateId`、`ruleCode`、`ruleName`、`scopeType`、`scopeId`、`businessSubtype` 和 `ruleStatus`；不发送组织头、CSRF、数据范围、`params` 或旧向导写命令参数。
- 详情按 `{ template, rule, versions }` 校验模板、规则、版本和当前发布版本关联，版本按版本号降序；发布前不受支持的条件字段、操作符或节点策略仍以 `supported: false` 安全可见，不把需要审计的无效草稿静默丢弃。
- 原始 `strategyConfig`、固定用户 ID、定义快照与校验和、发布人用户 ID、锁版本和内部审计字段均不进入页面模型；固定用户策略只保留去重人数，受控策略只保留安全摘要。
- 规则列表与详情属于管理员系统级跨组织审计视图；两个成功响应都必须声明 `Cache-Control: no-store`，缺失时失败关闭。真实 Reader 保持未注入，默认 Provider 只使用 ERP-NEW_2 自有脱敏 Mock。

### 文件

- 上传、下载、预览、流式响应和失败重试需要独立文件契约。
- `void` 导出端点和 `ResponseEntity<Resource>`/`StreamingResponseBody` 不得按普通 JSON 成功结构解析。

## 7. 冻结前必须完成

1. 生成版本化 OpenAPI 文件并为 526 个前端调用建立 operation 对应关系。
2. 将 CR-001～CR-003 展开为显式 operation 集合。
3. 逐项核验 13 个后端单边权限和 12 个 SQL 单边权限。
4. 为分页、错误、日期、金额、文件和权限响应定义统一前端适配模型。
5. 完成库存调拨命令的幂等、并发、数据范围和负向契约清单。
6. 用户确认任何 API 合并、废弃或不兼容修复后，才允许冻结契约。
