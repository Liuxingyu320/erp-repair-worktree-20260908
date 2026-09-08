# Grok 代码审核修复执行方案

> 日期：2026-08-05
> 分支：`codex/erp-product-optimization`
> 核对基线：`b408b705` 及当前未提交工作区
> 目标：修复已证实的安全、库存、审批一致性和签约并发问题，并阻止 Grok 按原报告中不准确的库存语义误改
> 本文用途：可直接交给 Grok 逐任务实施；每个任务必须独立测试、独立汇报，不得一次性做 623 文件级无差别改写

## 一、先纠正原审核口径

Grok 开始改代码前，必须先接受下表口径。原报告不是逐字执行清单。

| 原编号 | 处理结论 | 正确修复口径 |
| --- | --- | --- |
| 1 入职密码 | P0，确认 | 新账号必须以 `TEMPORARY` 凭证状态入库，写 24 小时过期时间，并继续复用现有登录强制改密链路。 |
| 2 签约包 prepare 无锁 | P1，确认 | 人工和系统准备路径统一使用 `package_id + expectedVersion + allowedStatus` CAS；原 #10 合并到本项。 |
| 3 销售误用 `purchase_id` | P0，确认且范围更大 | 正式发货通知路径也在误用。新记录的 `purchase_id` 必须为空，来源改为“发货通知 + 实际源仓”；同时修复多源仓合并和成本取错仓。 |
| 4 RESHIP 二次扣源仓 | 原因描述错误，但仍是 P0 | 纯短少补发本来就应再次从源仓扣库存；严禁先把短少退回源仓。真实问题是短少、拒收、残损的物理去向和成本处置没有逐项台账。 |
| 5 WRITE_OFF / ACCEPT_ACTUAL 无台账 | P0，确认 | 增加成本化差异处置台账；只有真实库存移动才写库存流水，不能对已出库的短少再做一次负库存调整。 |
| 6 报销孤儿审批 | P0，确认且是系统性问题 | 同类问题同时存在于报销、OA 采购、调拨、盘点四套审批 start outbox；必须统一补偿，不只修报销。 |
| 7 遗留销售调拨不可收货 | P1，代码存在但当前 Controller 已硬拒绝 | 删除 `deliverSales/createTransferForReceipt` 遗留实现或使服务层也明确硬失败，避免以后被内部调用复活。 |
| 8 Inner 仅认请求头 | P1 发布门禁 | 标准 compose 的 loopback 映射能缓解，但 ECS host 网络和部署脚本仍可能暴露业务端口；先封网络，再上服务凭证或 mTLS。 |
| 9 撤回依赖异步回调 | P2 运维项 | 保留异步事实模型，增加超时可见性与对账，不把远端调用成功直接当成本地终态。 |
| 10 通用 update 无 version | 合并 #2 | 审计全部调用点，生命周期写入收敛到专用 CAS mapper。 |
| 11 文件异常原文返回 | P1，小修 | 前端只返回稳定错误码/通用文案，完整异常只写服务端日志。 |
| 12 确认不预留库存 | 产品决策门 | 本轮不擅自引入预留模型；先决定“确认是否构成库存承诺”。 |
| 13 INVALIDATED 当成功消化 | 不改业务语义 | 审批回调已完成、业务因库存快照变化而失效是合法终态；增加指标、待办和运维筛选即可。 |
| 14 拒签缺幂等注解 | 不修改 | 请求体已有强制 `requestId`、事件幂等和 version 状态迁移，不能再用注解误判为缺幂等。 |
| 15 发货审批校验偏本地 | 不单独修改 | 原生回调已有实例、轮次和事件校验；真正边界是 Inner 鉴权。 |
| 16 `releaseOnSuccess=true` | 不修改现有 9 处 | 当前调用都另有持久幂等键。只保留回归测试，不能机械改注解。 |
| 17 岗位配置缺失仍确认 | 非安全 Bug | 当前账号会 fail-disabled，且已有 `ACCOUNT_CONFIGURATION_MISSING` 风险、完整度列表和筛选；后续可加待办，但不能把它混入 P0。 |

原报告还漏掉一个必须一并修复的 P0：发货通知允许销售明细来自多个仓库，但最终用“最后一次发货仓库”创建一张调拨单，并按该仓重查所有物料成本。结果可能把 A 仓货记到 B 仓，甚至因 B 仓无该物料把成本写成 0。

## 二、目标不变量

完成后必须同时满足以下不变量：

- [ ] 所有管理员/HR 签发的新账号密码，要么是 `TEMPORARY + expiresAt`，要么不允许登录；不存在“口头称一次性、数据库却是 ACTIVE”的状态。
- [ ] `inv_transfer_order.purchase_id` 只引用真实采购单；销售来源不再把销售单 ID 填进采购外键。
- [ ] 一个跨店发货通知按实际 `warehouse_id` 至多生成一张待收货调拨单；多仓通知生成多张，不允许跨仓混单。
- [ ] 销售出库时冻结仓库和成本；后建调拨/发货批次不得重查当前库存成本，更不得把查不到成本降为 0。
- [ ] 每个差异明细的短少、拒收、残损数量都恰好对应一个处置；成本取原发货批次快照，库存流水只反映真实库存移动。
- [ ] 远端审批已创建但本地无法永久关联时，只能进入补偿状态；带 `remote_instance_id` 的失败记录永远禁止普通重放或重新 start。
- [ ] 补偿撤回必须能通过业务回调完成，即使业务表尚未写入 `approval_instance_id`；回调不得永远卡在 `APPROVAL_LINK_PENDING`。
- [ ] 签约包生命周期更新全部携带期望 version；冲突请求不落事件、不覆盖状态，生成文件有回滚清理。
- [ ] 外部请求伪造 `from-source: inner` 不能直接调用 Inner 接口；业务端口不暴露公网，应用层还要校验服务凭证或 mTLS 身份。
- [ ] API 不向客户端返回本机路径、对象存储内部信息、数据库或 IO 原始异常。

## 三、实施顺序与提交边界

建议按以下顺序实施，每个任务单独形成一个可审查补丁。前一任务的门禁不通过，不进入后一任务。

```text
G0 基线保护
  ├─ P0-A 入职临时凭证
  ├─ P0-B 销售来源、多仓与成本快照
  ├─ P0-C 调拨差异处置台账
  └─ P0-D 审批孤儿补偿
       ├─ P1-A 签约包 CAS 收口
       ├─ P1-B Inner 网络与服务鉴权
       └─ P1-C 文件错误脱敏
            └─ P2 可观测性和产品决策项
```

当前工作区约有 1358 条 modified/deleted/untracked 状态，全部视为用户已有改动：

- 禁止 `git reset --hard`、`git clean`、`git checkout --`、批量格式化和覆盖式生成。
- 每个任务开始前记录 `git status --short`，结束后只汇报本任务 allowlist 内文件。
- 已执行的历史迁移只作为证据，禁止回改；所有数据库修复使用 2026-08-05 前向迁移。
- 每个迁移在 `sql/` 与 `docker/mysql/db/` 保存同名、字节一致的两份文件，并更新 `docker/mysql/bootstrap-files.list`。
- 不得因为现有测试是源码字符串断言，就把它当成库存守恒或事务行为测试。

## 四、G0：建立失败基线和保护门

### Task G0-1：先补能复现问题的失败测试

在修改实现前，先让以下场景变成红测：

- 入职新建账号的 `credentialState`、过期时间和确认结果过期提示。
- 同一签约包两个准备请求只有一个 CAS 成功。
- 销售发货生成调拨时 `purchaseId == null`，来源为通知 ID。
- 一张通知含两个源仓时生成两张调拨单，并分别使用各自冻结成本。
- 纯短少 RESHIP 不给源仓加库存，只释放待补发数量。
- 混合差异在没有逐项处置时禁止结案。
- 远端 start 成功、本地永久失败后不进入普通 `FAILED` 重放，而进入补偿。
- 伪造 `from-source: inner`、无服务签名时在 enforce 模式被拒绝。
- 文件服务异常响应不包含测试注入的路径或异常原文。

### Task G0-2：保留当前绿色基线

当前已验证的聚焦基线是 167 个 Java 测试，以及现有前端差异源码测试。Grok 修改前后都要保存命令和结果；如果基线之外已有失败，要区分“原有失败”和“本任务新增失败”，禁止通过删除测试或放宽断言恢复绿色。

## 五、P0-A：入职账号统一为临时凭证

**Files:**

- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/support/TemporaryCredentialPolicy.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/support/TemporaryCredentialPolicyTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingConfirmationService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrOnboardingConfirmResult.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrOnboardingConfirmationServiceTest.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`
- Modify: `erp-ui/src/views/hr/onboarding/components/HrOnboardingConfirmDialog.vue`
- Modify: `erp-ui/src/views/mobile/hr/onboarding/detail.vue`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`
- Modify: `erp-ui/test/mobileHrOnboarding.test.js`

**实现要求：**

- [ ] `TemporaryCredentialPolicy` 是 24 小时过期策略的唯一来源，基于注入的 `Clock` 计算，禁止在两个 Service 中各写一份 `Instant.now().plus(...)`。
- [ ] `HrOnboardingConfirmationService.createNew` 在 `insertUser` 前设置：
  - `credentialState = SysUser.CREDENTIAL_STATE_TEMPORARY`
  - `temporaryPasswordExpiresAt = policy.expiresAt(clock)`
  - `pwdUpdateDate = null`
- [ ] 保留当前高熵随机密码，不退回固定默认密码；密码生成失败必须使整个入职确认事务回滚。
- [ ] `SysUserServiceImpl.insertUserWithTemporaryCredential`、重置和导入路径改为复用同一过期策略，避免后续再次漂移。
- [ ] `HrOnboardingConfirmResult` 增加 `oneTimePasswordExpiresAt`；只有首次创建新账号时返回，绑定已有账号或幂等重放不得再次返回密码。
- [ ] PC/移动端明确展示“首次登录必须改密”和过期时间；关闭弹窗后清空内存中的密码和过期字段，不写 localStorage、日志或埋点。
- [ ] 不新增数据库迁移：现有 `credential_state` 和 `temporary_password_expires_at` 已具备承载能力。

**验收：**

- 固定 Clock 下断言到期时间精确为创建时间 +24h。
- 新账号使用临时密码登录后只允许进入强制改密流程；改密后状态转 ACTIVE 且临时过期时间清空。
- 过期临时密码被拒绝；绑定已有账号不改变其现有密码状态。

## 六、P0-B：修复销售来源、多源仓和成本快照

**Files:**

- Create: `sql/erp_inventory_sales_delivery_transfer_consistency_20260805.sql`
- Create: `docker/mysql/db/erp_inventory_sales_delivery_transfer_consistency_20260805.sql`
- Modify: `docker/mysql/bootstrap-files.list`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferTypes.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNoticeDetail.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOutboundRecord.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvDeliveryNoticeDetailMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeDetailMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvOutboundRecordMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOutboundRecordMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferOrderMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvSalesService.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvSalesServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java`
- Modify: `erp-ui/test/newBusinessMigrationRelease.test.js`

### Task P0-B1：前向迁移与历史数据保护

- [ ] 在 `inv_delivery_notice_detail` 增加物理快照列 `warehouse_id` 和 `delivered_cost_amount`。`delivered_cost_amount` 对历史已发记录保持 NULL，不能用 0 冒充未知成本；未发记录初始化为 0。
- [ ] 在 `inv_outbound_record` 增加 `notice_id`、`notice_detail_id`、`cost_price`、`cost_amount`，用于未来每次部分出库的精确审计。
- [ ] 从 `inv_sales_detail.warehouse_id` 回填通知明细仓库；无法唯一回填的记录输出预检结果并阻止发布，禁止猜仓。
- [ ] 对 `source_business_type='sales_delivery'` 的历史调拨只清空错误的 `purchase_id`；保留其旧销售单来源 ID，不在没有确定映射时伪造 notice ID。
- [ ] 新来源类型固定为 `sales_delivery_notice`。增加普通索引 `(source_business_type, source_business_id, from_warehouse_id)`。
- [ ] 为新来源增加数据库唯一门禁，推荐使用仅在 `source_business_type='sales_delivery_notice'` 时生成非 NULL 的 generated dedupe key，再建唯一索引；不要用全表唯一约束误伤其他业务来源。
- [ ] 对“跨店 + DELIVERING + delivered_qty > 0 + delivered_cost_amount IS NULL”的历史半成品输出阻断清单。没有可信成本回填前，应用必须 fail-closed，不能取当前库存成本补洞。
- [ ] 两份迁移必须 `cmp` 一致，并加入 MySQL 5.7 集成测试和 bootstrap 清单。

### Task P0-B2：发货时冻结仓库与成本

- [ ] 创建通知明细时把销售明细的 `warehouseId` 写入通知明细，之后校验通知快照，不再依赖可被修改的销售明细 join 值。
- [ ] 每次部分发货在锁定库存后取得当批 `currentCostPrice`，原子累计：
  - `delivered_qty += batchQty`
  - `delivered_cost_amount += batchQty * currentCostPrice`
- [ ] 同一事务写入带 notice/detail/cost 的 outbound record 和现有销售出库库存日志。
- [ ] 请求仓库必须等于每条通知明细冻结仓库；一批请求混入其他仓明细时整批回滚。
- [ ] 更新操作必须校验未发数量，保留通知行锁；不能用普通覆盖 update 让并发请求丢失累计成本。

### Task P0-B3：按源仓拆分待收货调拨

- [ ] 通知全部发完后，按通知明细的冻结 `warehouse_id` 分组；每个仓库创建一张调拨单和一个 `PENDING_RECEIVE` shipment。
- [ ] 新调拨单字段固定为：
  - `purchase_id = NULL`
  - `source_business_type = 'sales_delivery_notice'`
  - `source_business_id = noticeId`
  - `from_warehouse_id = 当前分组仓库`
- [ ] 去重查询固定使用 `source_business_type + source_business_id + from_warehouse_id`。已存在记录只有在目标组织和明细快照一致时可幂等返回，否则报来源冲突。
- [ ] 每条调拨明细的冻结成本为 `delivered_cost_amount / delivered_qty`，金额使用已累计的精确成本额；统一舍入规则并保证明细金额汇总一致。
- [ ] `createDeliveredCrossStoreTransfer` 使用传入并持久化的冻结 `costPrice/amount` 创建 shipment detail；删除 line 343 一类的“按当前源仓重查成本，查不到返回 0”逻辑。
- [ ] 返回文案保持现有 String API 兼容，可用 `、` 拼接多张调拨单号；不要为了本修复破坏前端接口。
- [ ] 兼容查询继续识别旧 `sales_delivery`，但所有新建入口只允许 `sales_delivery_notice`。

### Task P0-B4：退役遗留销售出库服务路径

- [ ] 删除 `IInvSalesService.deliverSales` 和 `InvSalesServiceImpl.deliverSales/createTransferForReceipt`，或让服务层方法无条件抛出“请通过发货通知执行发货”。
- [ ] Controller 现有硬拒绝继续保留，编译期搜索确保没有内部调用者。
- [ ] 删除 `selectByPurchaseId` 在销售路径的所有使用；采购路径可以保留。

**验收矩阵：**

| 场景 | 预期 |
| --- | --- |
| 销售单 ID 与采购单 ID 相同 | 销售调拨仍正常创建，`purchase_id` 为 NULL。 |
| 单仓、一次发完 | 一张调拨、一份 shipment，成本等于出库时成本。 |
| 单仓、两次部分发货且成本变化 | 一张调拨，使用加权实际出库成本，不用最终时点成本。 |
| 两仓、各一条明细 | 两张调拨，每张只含本仓明细和本仓成本。 |
| 最后发货仓无另一物料库存 | 不影响另一仓明细成本，不出现静默 0 成本。 |
| 同一通知重试建单 | 返回原调拨；不重复创建。 |
| 历史半成品成本未知 | 明确阻断并列入运维清单，不猜值。 |

## 七、P0-C：建立差异逐项处置和成本台账

**Files:**

- Create: `sql/erp_inventory_transfer_discrepancy_disposition_20260805.sql`
- Create: `docker/mysql/db/erp_inventory_transfer_discrepancy_disposition_20260805.sql`
- Modify: `docker/mysql/bootstrap-files.list`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferDiscrepancyDisposition.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvTransferDiscrepancyResolutionItem.java`
- Create: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferDiscrepancyDispositionMapper.java`
- Create: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyDispositionMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvTransferDiscrepancyResolveRequest.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferDiscrepancy.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferDiscrepancyDecisions.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferWorkflowResources.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessor.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReceiptProcessor.java`
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvTransferDiscrepancyMapper.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyMapper.xml`
- Create: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessorTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/integration/NewBusinessMigrationsMySqlIT.java`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/featureActionRuntime.js`
- Modify: `erp-ui/test/transferDiscrepancyFlow.test.js`

### Task P0-C1：数据模型

- [ ] 新建 `inv_transfer_discrepancy_disposition`，至少保存：差异主/明细/发货明细 ID、类别 `SHORTAGE/REJECTED/DAMAGED`、决定、数量、原发货 `cost_price`、金额、库存影响、来源/目标位置、责任方、说明、附件、request_id、操作人、时间和 version。
- [ ] 幂等键覆盖 `discrepancy_id + request_id + discrepancy_detail_id + category`；同 request 重放返回既有结果，不能重复退库或重复核销。
- [ ] `InvTransferDiscrepancyResolveRequest` 增加必填 `requestId` 和逐项 `items[]`。每项包含 detailId、category、decision、quantity。
- [ ] 每个 detail/category 的处理数量之和必须精确等于该类别差异量；多处理、少处理、重复类别或跨差异 detail 全部拒绝。
- [ ] 为兼容旧客户端，只有“单一类别、单一合法决定”的旧 header decision 可临时映射；混合差异必须返回明确升级提示，不能整单套一个决定。

### Task P0-C2：固定业务语义

| 类别 | 允许决定 | 库存与台账语义 |
| --- | --- | --- |
| SHORTAGE | `RESHIP` | 不给源仓加库存；记录原在途短少成本处置，仅减少调拨明细已发数量，后续补发会再次真实扣源仓。 |
| SHORTAGE | `ACCEPT_ACTUAL` / `WRITE_OFF` | 不再补发；记录成本化在途损失，不做第二次库存扣减。 |
| REJECTED | `RETURN_SOURCE` | 按原 shipment cost 把实物加回源仓，并写真实库存流水和 disposition。 |
| REJECTED | `PENDING_QC` / `WRITE_OFF` | QC 保持未结；WRITE_OFF 记录目标侧实物处置和成本，不把它加入可售库存。 |
| DAMAGED | `PENDING_QC` | 建立待质检保管记录，差异保持 `PENDING_QC`。 |
| DAMAGED | `RETURN_SOURCE` / `WRITE_OFF` | 只有经历 QC 或有明确处置依据后允许；退回才加源仓，核销只记 disposition。 |

额外硬规则：

- [ ] `RESHIP` 只能处理 SHORTAGE；如果同单还有 rejected/damaged，必须在同一请求中分别指定退回、QC 或核销。
- [ ] `ACCEPT_ACTUAL` 只用于“接受较少实收”的短少；拒收和残损不能用这个名字掩盖物理去向。
- [ ] `WRITE_OFF` 和非补发的短少必须使用 `InvTransferShipmentDetail.costPrice`，不得查当前成本。
- [ ] `InvStockLog` 只记录真实 stock 数量变化；无库存变化的损失写 disposition，不伪造第二次出库流水。
- [ ] 差异只有在全部类别都有终态 disposition 时才可 `RESOLVED`；存在 QC_HOLD 时保持 `PENDING_QC`。

### Task P0-C3：后端行为测试

至少覆盖：

- 10 件发货、8 件接收、2 件短少 RESHIP：源仓不增加；deliveredQuantity 减 2；补发 2 时源仓再扣 2。
- 10 件发货、8 接收、1 拒收、1 残损：必须分别处置，不能用一个 header decision 直接关单。
- RETURN_SOURCE 重放同一 requestId：源仓只增加一次。
- WRITE_OFF：源仓和目标可售库存都不变，但存在数量、成本、原因、操作人完整台账。
- PENDING_QC 后再 WRITE_OFF/RETURN_SOURCE：只能从合法前态迁移，version 冲突时零副作用。
- 守恒断言：每个 shipment detail 的 `accepted + shortage + rejected + damaged = shipped`，且每个非 accepted 数量恰好一个处置归宿。

前端必须改成逐行类别处置，显示数量、冻结成本、决定和附件要求；不得只保留一个整单下拉框。

## 八、P0-D：四类审批 start outbox 的孤儿补偿

**数据库与公共 API：**

- Create: `sql/erp_approval_start_outbox_compensation_20260805.sql`
- Create: `docker/mysql/db/erp_approval_start_outbox_compensation_20260805.sql`
- Modify: `docker/mysql/bootstrap-files.list`
- Modify: `erp-api/erp-api-approval/src/main/java/com/erp/approval/api/domain/ApprovalInstanceSnapshot.java`
- Modify: `erp-api/erp-api-approval/src/main/java/com/erp/approval/api/domain/ApprovalWithdrawRequest.java` only if a stable optional compensation request key is needed; do not break existing callers.

**OA：**

- Modify both reimbursement and purchase `ApprovalStartOutbox` domain, mapper Java/XML, service, dispatcher, controller, VO/query/summary and tests.
- Modify `OaReimbursementServiceImpl.applyApprovalCallback` and `OaPurchaseServiceImpl.applyApprovalCallback` so a compensation WITHDRAW callback can be accepted before `approval_instance_id` is linked.

**Inventory：**

- Modify both transfer and stock-check `ApprovalStartOutbox` domain, mapper Java/XML, service, dispatcher, controller, VO/query/summary and tests.
- Modify `InventoryUnifiedApprovalService` compensation callback handling for transfer and stock check.

### Task P0-D1：统一状态机

四张 outbox 表和四套实现统一增加状态：

```text
REMOTE_SUCCEEDED
  ├─ 本地 finalize 成功 -> SUCCEEDED
  └─ 本地永久失败 -> COMPENSATION_PENDING
       -> COMPENSATING
          ├─ 远端 WITHDRAW 回调完成、本地恢复成功 -> COMPENSATED
          ├─ 远端已关闭但本地无法安全恢复 -> COMPENSATION_REQUIRED
          └─ 远端不能撤回/身份不匹配/超重试 -> COMPENSATION_REQUIRED
```

- [ ] 远端 start 之前的永久错误仍可用 `FAILED`；只要 `remote_instance_id != null`，就永远禁止普通 replay 和再次调用 start。
- [ ] `PermanentFailure` 捕获后改为 `markCompensationPending`，保留远端实例、轮次、状态和原始脱敏错误。
- [ ] 增加补偿重试次数、下次重试时间、最后错误、开始/完成时间；领取和状态迁移使用 outbox version CAS。
- [ ] 运维列表和 summary 能区分 `FAILED`、`COMPENSATING`、`COMPENSATED`、`COMPENSATION_REQUIRED`。

### Task P0-D2：撤回前验证远端身份

- [ ] 先调用 `getInstance`，严格匹配 instanceId、businessCode、businessId、businessRound 和 applicantId；任何不一致都不得撤回，直接进入人工补偿。
- [ ] 远端 `RUNNING` 才发 withdraw；`WITHDRAWING/WITHDRAWN` 视为已发起补偿并等待/核对回调；`APPROVED/REJECTED/RETURNED/TERMINATED` 不自动改写，进入人工补偿。
- [ ] withdraw 使用原 `ApprovalStartRequest.applicantId`，原因包含业务类型、outboxId 和错误码，但不包含敏感业务正文。
- [ ] 网络失败可退避重试；审批已有人工同意而不允许撤回时不得循环轰炸。

### Task P0-D3：解决“补偿回调仍卡 link pending”

这是本修复最容易漏掉的部分。现有 OA/Inventory 回调在业务为 submitting/submitted、`approval_instance_id == null` 时直接返回 `APPROVAL_LINK_PENDING`；若只调用远端 withdraw，审批中心会永远停在 `WITHDRAWING`。

- [ ] 回调收到 `WITHDRAW` 时，先按业务 ID/轮次查对应 start outbox。
- [ ] 如果 outbox 处于 `COMPENSATION_PENDING/COMPENSATING`，且 `remote_instance_id` 与 callback instance 完全一致，则允许走补偿回调分支，不要求业务表已经关联 instance。
- [ ] 在同一本地事务中：记录 callback event key、将业务恢复到现有撤回语义、把 outbox 标记为 `COMPENSATED`。
  - 报销/OA 采购沿用现有 `withdrawn` 状态。
  - 调拨/盘点沿用现有 `draft` 状态。
- [ ] 如果业务记录已删除或版本/轮次已被其他合法流程改变，仍应确认远端撤回回调以便审批实例结束，但 outbox 标为 `COMPENSATION_REQUIRED`，不得覆盖新业务事实。
- [ ] 重复回调必须幂等；普通 APPROVE/REJECT 回调不能借补偿分支绕过 instance 关联校验。

### Task P0-D4：补偿验收

四种业务都要覆盖同一契约测试：

1. remote start 成功并先固化 instance。
2. 本地 finalize 抛 PermanentFailure。
3. outbox 进入 COMPENSATION_PENDING，普通 replay 被拒绝且 start 不再调用。
4. dispatcher 验证 snapshot 后只调用一次 withdraw。
5. WITHDRAW callback 在业务尚未关联 instance 时被补偿分支接受。
6. 远端最终 WITHDRAWN；业务进入 withdrawn/draft；outbox COMPENSATED。
7. 身份不匹配、已有审批动作或业务已进入新轮次时进入 COMPENSATION_REQUIRED，并在运维列表可见。

## 九、P1-A：签约包生命周期更新统一 CAS

**Files:**

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 新增专用 `prepareDocumentsWithVersion` mapper，WHERE 至少包含 `package_id`、`version = expectedVersion` 和允许的当前状态/确认状态；SET 中 `version = version + 1`。
- [ ] `preparePackageDocuments` 与 `preparePackageDocumentsForSystem` 都从已读取包取得 expectedVersion，使用同一 CAS 方法。
- [ ] CAS 失败抛统一并发提示；事务回滚，不能写 `PACKAGE_PREPARED` 事件。
- [ ] 复用 `OaSignPackageFileLifecycle` 的事务回滚清理，确保竞争失败产生的新文件不会留成孤儿。
- [ ] 审计 `updateOaSignPackage` 现有调用点（当前重点在约 431、490、1087、1843 行）。生命周期/签名/最终确认写入改为专用状态方法；通用 update 只允许非生命周期元数据，最好从 mapper 接口移除或缩窄字段。
- [ ] 已经使用 `markSentWithVersion`、`updateStatusWithVersion` 的路径保持现有 CAS，不做回退。

**并发验收：** 两个请求读取同一 version 后同时准备，恰好一个成功；失败方不覆盖 documentVersion、确认状态或最终文件快照，不新增业务事件，文件清理测试通过。

## 十、P1-B：Inner 接口双层加固

### Task P1-B1：立即封闭网络暴露

**Files:**

- Modify: `docker/deploy.sh`
- Modify: `docker/docker-compose.ecs-host.yml`
- Modify: `docker/run-erp-service.sh`
- Modify: `docker/.env.example`
- Modify: `docs/ALIYUN_ECS_DEPLOYMENT_RUNBOOK.md`
- Create: `scripts/verify-inner-port-exposure.sh`
- Modify: `scripts/test_aliyun_deployment_contract.py`

- [ ] 生产脚本不得开放 9200、9201、9203、9204、9205、9206、9300、3306、6379、8848 等内部端口到公网；`docker/deploy.sh port` 必须明确为本地调试且生产命令 fail-closed。
- [ ] 单机 ECS host 网络部署让业务服务绑定 `127.0.0.1`；如果未来跨主机，改为私网地址 + 安全组服务身份/VPC 白名单，不能绑定 `0.0.0.0` 后只靠文档约定。
- [ ] 当前 `docker/run-erp-service.sh` 对多数服务委托给仓库中不存在的 `run-erp-service-legacy.sh`。必须同步修正实际发布制品中的 runner，或把受控 runner 纳入仓库；在启动参数来源不可审计前不得宣称端口绑定已修复。
- [ ] 发布脚本检查 `ss -ltnp`，发现内部端口监听 `0.0.0.0` 或 `[::]` 直接失败。
- [ ] 从非受信网络执行外部探测，确认只有 Nginx/Gateway 暴露；把探测结果作为发布证据。
- [ ] 先在测试环境验证 Nacos 注册地址和 Feign 调用，再切换生产绑定，避免因 loopback 改动造成服务发现失效。

### Task P1-B2：用服务凭证替代纯请求头信任

**Files（建议结构，Grok 可按现有公共模块命名调整）：**

- Modify: `erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/InnerAuthAspect.java`
- Create: common-security 下的 internal request signer、validator、nonce replay guard 和 body caching filter
- Modify: `erp-gateway/src/main/java/com/erp/gateway/filter/AuthFilter.java`
- Create/Modify: Feign 请求拦截器及其自动配置
- Add focused tests under `erp-common/erp-common-security` and `erp-gateway`

- [ ] `from-source: inner` 只作为兼容路由标记，不再构成认证。
- [ ] 内部请求携带 caller service、key id、timestamp、nonce、body SHA-256 和 HMAC signature；canonical string 必须包含 HTTP method、规范化 path/query、时间、nonce 和 body hash。
- [ ] 接收端使用常量时间比较签名，时间窗建议 5 分钟；nonce 在 Redis `SET NX EX` 防重放。
- [ ] 密钥从环境/Nacos 注入，不入库、不进 Git、不打印；生产缺密钥时 fail-closed。
- [ ] 网关剥离所有外部传入的 Inner 认证头，不只剥 `from-source`。
- [ ] 采用 `legacy -> observe -> enforce` 灰度：observe 只记录缺签名指标，enforce 才拒绝；网络隔离必须在整个过渡期先行有效。
- [ ] 如果团队决定使用 mTLS，则可替代 HMAC，但必须验证客户端证书服务身份、轮换和吊销；不能把“已上 HTTPS”当成 mTLS。

**验收：** 伪造单头拒绝；有效签名接受；过期、篡改 body、重放 nonce、未知 caller/key 全部拒绝；外部通过网关注入内部签名头仍被剥离。

## 十一、P1-C：文件接口错误脱敏

**Files:**

- Modify: `erp-modules/erp-file/src/main/java/com/erp/file/controller/SysFileController.java`
- Modify: `erp-modules/erp-file/src/test/java/com/erp/file/controller/SysFileControllerTest.java`

- [ ] `upload`/`uploadInner` 异常统一返回稳定错误码和“文件上传失败”，不返回 `e.getMessage()`。
- [ ] `delete` 对非法路径返回业务级固定文案；其他异常返回“文件删除失败”。
- [ ] 服务端日志保留 stack trace，但日志字段不得主动拼接文件内容、凭证或未脱敏私有 URL。
- [ ] 测试注入 `/var/.../secret`、数据库错误和对象存储错误，断言响应中均不存在原文。

## 十二、P2：可观测性与明确不自动实施的事项

### 可实施

- [ ] 普通审批撤回：增加 `withdrawing` 超时指标和运维筛选；远端成功但本地回调超过阈值时告警，不直接伪造本地 withdrawn。
- [ ] `INVALIDATED`：在审批/盘点运维页单独统计，创建重新盘点待办；审批中心把 callback 消化为成功的语义保持不变。
- [ ] 入职岗位配置缺失：复用现有 System Todo，为 `accountConfigurationStatus=MISSING` 增加账号配置待办；账号继续禁用，不能重复发临时密码。

### 必须先有产品决定

调拨源店确认是否预留库存有两种合法模型，Grok 不得自行选择：

1. **确认即承诺**：增加 `reserved_quantity/locked_quantity`，确认时原子预留，取消、改仓、发货和关单时完整释放。
2. **确认仅表达意向**：不预留，UI 明确“以实际发货库存为准”，发货继续行锁校验。

没有业务负责人结论前，本轮只保留现状和测试，不新增半套预留字段。

### 明确禁止的机械修改

- 不给移动拒签接口机械叠加 `@IdempotentSubmit`。
- 不把所有 `releaseOnSuccess=true` 全局替换成 false。
- 不让每次审批回调都远程查询审批中心，以掩盖本地数据问题。
- 不把 INVALIDATED 改成审批中心失败重试。
- 不把短少 RESHIP 实现为“先加回源仓，再次发货”。

## 十三、验证命令与发布门

Grok 应根据每个任务运行聚焦测试，最后执行组合门禁。Java 使用 JDK 17。

```bash
./mvnw -pl erp-modules/erp-system -am \
  -Dtest=TemporaryCredentialPolicyTest,HrOnboardingConfirmationServiceTest,SysUserServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl erp-modules/erp-inventory -am \
  -Dtest=InvDeliveryNoticeServiceImplTest,InvTransferServiceImplTest,InvSalesServiceImplTest,InvTransferDiscrepancyProcessorTest,NewBusinessMigrationsMySqlIT \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl erp-modules/erp-oa,erp-modules/erp-inventory,erp-modules/erp-approval -am \
  -Dtest=OaReimbursementApprovalStartDispatcherTest,OaReimbursementApprovalStartOutboxServiceTest,OaPurchaseApprovalStartDispatcherTest,OaPurchaseApprovalStartOutboxServiceTest,InvTransferApprovalStartDispatcherTest,InvTransferApprovalStartOutboxServiceTest,InvStockCheckApprovalStartDispatcherTest,InvStockCheckApprovalStartOutboxServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl erp-modules/erp-oa -am \
  -Dtest=OaSignPackageServiceImplTest,OaMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl erp-modules/erp-file,erp-gateway -am \
  -Dtest=SysFileControllerTest,AuthFilterSessionModeTest,AuthFilterInvalidTokenTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

node erp-ui/test/hrOnboardingWorkbench.test.js
node erp-ui/test/mobileHrOnboarding.test.js
node erp-ui/test/transferDiscrepancyFlow.test.js
node erp-ui/test/transferPartialDeliveryReceive.test.js
node erp-ui/test/newBusinessMigrationRelease.test.js

cmp sql/erp_inventory_sales_delivery_transfer_consistency_20260805.sql \
    docker/mysql/db/erp_inventory_sales_delivery_transfer_consistency_20260805.sql
cmp sql/erp_inventory_transfer_discrepancy_disposition_20260805.sql \
    docker/mysql/db/erp_inventory_transfer_discrepancy_disposition_20260805.sql
cmp sql/erp_approval_start_outbox_compensation_20260805.sql \
    docker/mysql/db/erp_approval_start_outbox_compensation_20260805.sql
```

发布前还必须完成：

- [ ] 在隔离 MySQL 5.7 库从当前生产结构执行三份前向迁移，并重复执行验证幂等性。
- [ ] 查询所有历史 `sales_delivery` 调拨和半完成发货通知；未知仓库/成本清单为 0，或已有人工签字的修复清单。
- [ ] 对差异流程做库存与成本对账，不只看状态成功。
- [ ] 对四类审批分别做一次远端成功 + 本地永久失败的故障注入。
- [ ] 从公网/非受信网探测内部端口，并在 enforce 模式做伪造 Inner 请求测试。
- [ ] 全量编译和仓库既有 release tests 通过；不得删除旧断言来换绿色。

## 十四、Grok 每个任务的交付格式

Grok 每完成一个任务，必须返回：

1. 实际修改文件列表，说明是否超出本文 allowlist。
2. 状态机、事务边界和数据迁移摘要。
3. 新增/修改测试及真实执行结果，不得只说“理论上通过”。
4. 对历史数据的预检结果和无法自动修复的记录。
5. `git diff --check` 结果及剩余风险。
6. 不得提交、推送或清理用户工作区，除非用户另行明确授权。

整体验收不是“8 个编号都改了”，而是本文第二节的十条不变量全部有代码、数据库约束、行为测试和发布证据支撑。
