# 手机端调拨方向与入口修复实施方案

> 日期：2026-07-28
> 状态：待实施
> 范围：手机端调拨入口、新建/编辑表单、调拨动作方向、服务端方向校验、跨店销售自动生成在途调拨
> 依据：当前工作区源码、调拨业务确认文档、现有手机端测试和本轮代码核查结果

## 1. 目标

本方案解决以下问题：

1. 手机端不再把“门店要货”和“异店调货”放进一个没有组织类型约束的通用表单。
2. 门店可以从明确入口发起门店要货、门店返仓和异店调货；仓库只处理发货、返仓收货和差异，不发起门店要货。
3. 调拨类型一旦创建便保持不变，编辑草稿不能把异店调货改成门店要货，或把返仓改成其他方向。
4. 页面选择器、提交载荷和服务端校验使用同一方向矩阵，非法组合在页面和接口两端都被阻止。
5. 跨店销售自动生成的在途收货记录保留“业务来源门店”和“实际发货仓库”两层含义，不再把仓库直接写成异店调货的来源门店。
6. 历史链接、统一待办跳转和已有调拨记录保持兼容，不增加新的数据库类型值。

## 2. 最终业务口径

### 2.1 人工发起调拨

| 类型值 | 页面名称 | 发起上下文 | 业务来源 | 业务目标 | 发货方 | 收货方 | 审批锚点 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `warehouse` | 门店要货 | 当前门店 | 选择的仓库 | 当前门店 | 来源仓库 | 当前门店 | 目标门店 |
| `store_return` | 门店返仓 | 当前门店 | 当前门店 | 选择的仓库 | 当前门店 | 目标仓库 | 来源门店 |
| `cross_store` | 异店调货 | 当前目标门店 | 选择的来源门店 | 当前门店 | 来源门店 | 当前门店 | 目标门店 |

约束：

- 人工调拨只能在 `STORE` 上下文新建和编辑。
- `warehouse` 的来源必须是 `WAREHOUSE`，目标必须是当前 `STORE`。
- `store_return` 的来源必须是当前 `STORE`，目标必须是 `WAREHOUSE`。
- `cross_store` 的来源和目标都必须是 `STORE`，两者不能相同，目标必须是当前门店。
- 仓库上下文可以处理与当前仓库有关的发货、返仓收货和差异，但不能看到“新建调拨”。
- 调拨类型在新建入口确定；编辑时只读，不能通过修改类型改变库存方向。

### 2.2 跨店销售自动生成的在途收货

跨店销售自动生成记录继续使用 `transfer_type='cross_store'`，但必须区分：

- `fromDeptId`：销售业务来源门店；
- `fromWarehouseId`：实际扣减库存、执行发货的仓库或库存组织；
- `toDeptId`：目标门店；
- `sourceBusinessType`：固定为可信服务端写入的销售来源标识，例如 `sales_delivery`；
- `sourceBusinessId`：销售单 ID。

该记录由销售/发货服务内部创建，直接进入已发货待收货状态，不开放给公共 `/inventory/transfer/save` 或 `/submit` 模拟创建。客户端提交 `sourceBusinessType` 不能获得内部流程豁免。

手机端展示时仍归入异店流转记录，但应增加“跨店销售在途”来源说明，并在详情中分别展示业务来源门店和实际发货仓库，避免用户把仓库误认为异店调货来源。

## 3. 当前问题

### 3.1 入口分配错误

- 门店工作台只有 `/mobile/replenishment` 的“补货申请”，该入口固定为 `warehouse`，没有异店调货直接入口。
- 仓库工作台进入 `/mobile/transfer`，通用页面又会按 `inv:transfer:add` 自动增加“新建调拨”。
- 结果是门店找不到异店调货，仓库却能进入一个最终会被后端拒绝的新建流程。

### 3.2 表单没有体现业务方向

`erp-ui/src/views/mobile/feature/mobileFormConfigs.js` 当前对通用调拨使用：

```text
fromDeptId -> entity=dept
toDeptId   -> entity=dept
```

两端都可以选择门店或仓库，页面不能阻止以下无效组合：

- 门店要货：门店 → 门店；
- 门店要货：仓库 → 仓库；
- 异店调货：仓库 → 门店；
- 异店调货：门店 → 仓库。

服务端虽会拒绝部分组合，但用户只能在保存或提交后看到错误。

### 3.3 默认值没有随业务类型重建

当前载荷逻辑只区分 `store_return` 与“其他类型”：

- 门店上下文：非返仓统一把当前门店设为目标；
- 仓库上下文：非返仓统一把当前仓库设为来源。

因此仓库用户切换到 `cross_store` 时，当前仓库仍可能作为调出方；类型变化也不会清理已经选择的无效组织和明细。

### 3.4 列表缺少类型辨识

手机调拨处理中列表主要展示“来源 → 目标”，详情没有稳定展示调拨类型。仓库→门店和门店→门店记录混在一起时，用户难以判断这是门店要货、异店调货还是跨店销售自动在途。

### 3.5 自动跨店销售存在服务端方向冲突

- `InvTransferDirectionPolicy` 要求 `cross_store` 的来源和目标都是门店。
- `InvDeliveryNoticeServiceImpl` 当前把实际 `warehouseId` 同时写入 `fromDeptId/fromWarehouseId`，再设置 `transferType='cross_store'`。
- 该链路会把仓库当作业务来源门店，并可能在保存阶段被“异店调货来源必须为门店”拒绝。
- `InvSalesServiceImpl` 另一条自动生成链路使用来源门店，两个实现口径不一致。

### 3.6 现有测试固化了部分错误行为

现有手机测试覆盖了：

- 仓库工作台进入通用调拨页；
- 仓库上下文新建表单默认当前仓库为来源；
- 通用调拨表单使用两个组织选择器。

这些断言证明当前实现自洽，但没有验证业务方向矩阵，需要先调整测试契约再改代码。

## 4. 推荐设计

### 4.1 一个调拨页面，两套上下文行为

继续复用 `/mobile/transfer` 和同一调拨事实源，但根据上下文改变入口和动作：

#### 门店上下文

- 工作台增加“调拨管理”入口。
- 页面提供三个明确的新建动作：
  - 门店要货；
  - 异店调货；
  - 门店返仓（受现有 `storeReturn` 功能开关控制）。
- 每个新建动作直接打开固定类型表单，不再先打开通用表单后让用户切类型。
- 列表可按“全部、门店要货、异店调货、门店返仓、待发货、待收货”筛选。

#### 仓库上下文

- 工作台继续显示“调拨处理”。
- 页面不展示任何新建动作。
- 主要筛选为“待发货、返仓待收货、差异待处理、全部流转”。
- 只在当前仓库是业务动作责任方时展示发货、收货或差异动作。

#### 旧入口兼容

- `/mobile/replenishment` 至少保留一个发布周期。
- 旧入口继续固定过滤 `transferType=warehouse`，并复用新的“门店要货”表单配置。
- 统一待办、收藏链接和历史通知无需立即修改；确认无旧客户端流量后再决定是否重定向到 `/mobile/transfer?transferType=warehouse`。

### 4.2 使用类型化表单，不使用可变通用表单

在 `mobileFormConfigs.js` 中将调拨表单拆成可复用的类型化配置，或提供配置工厂：

```text
resolveTransferFormConfig(transferType, mode, context)
```

建议字段矩阵：

| 类型 | 来源字段 | 目标字段 | 明细选择 |
| --- | --- | --- | --- |
| 门店要货 | 仓库选择器，`purpose=replenishmentSource` | 当前门店只读 | 复用现有仓库库存/缺货要货选择器 |
| 门店返仓 | 当前门店只读 | 仓库选择器 | 当前门店可用库存；保留返仓原因和货况 |
| 异店调货 | 来源门店选择器 | 当前门店只读 | 来源门店可用库存，只允许商品、礼盒 |

共同规则：

- `transferType` 固定且只读。
- 当前上下文组织字段不可手改。
- 来源组织变化时清空明细，避免从 A 店选择的库存提交到 B 店。
- 编辑历史草稿时根据记录原始 `transferType` 选择表单，不能根据当前入口猜测类型。
- `fromDeptId/fromWarehouseId/toDeptId/toWarehouseId` 由类型化载荷函数统一生成，不保留与当前类型冲突的旧字段。
- OE 继续只能通过维修上报生成内部补货调拨，手机人工调拨只允许商品、礼盒。

### 4.3 组织和库存选择器

复用现有实体服务，补齐类型明确的选择方式：

- 仓库：`entity='warehouse'`；
- 门店：`entity='store'`；
- 异店调货来源库存：新增或复用按 `fromDeptId` 查询的来源库存实体；
- 当前门店：`context-dept`。

异店来源门店选项应：

- 只返回用户业务范围内可见的 `STORE`；
- 排除当前目标门店；
- 不返回停用门店；
- 后端仍重复校验，不能只依赖前端过滤。

### 4.4 新建和编辑配置解析

`erp-ui/src/views/mobile/feature/index.vue` 需要把表单配置从“只按 featureKey 解析”调整为：

```text
新建：featureKey + action.transferType + selectedDeptType
编辑：featureKey + row.transferType + selectedDeptType
```

同时增加以下门禁：

- `transfer` 的新建权限除了检查 `inv:transfer:add`，还必须要求 `selectedDeptType === 'STORE'`。
- 仓库上下文不自动注入“新建调拨”。
- 编辑草稿必须属于当前门店允许编辑的方向，并继续由后端校验创建人、组织范围和状态。
- `featureActions.js` 中把门店要货编辑判断显式限制为 `transferType === 'warehouse'`，不能把所有非返仓类型都当成补货。

### 4.5 列表、详情和动作

保留当前正确的方向规则：

- 待发货：按 `fromDeptId/fromWarehouseId` 匹配当前组织；
- 待收货：按 `toDeptId/toWarehouseId` 匹配当前组织。

补充展示：

- 列表卡片增加调拨类型；
- 详情增加“业务类型、业务来源、实际发货仓库、目标门店”；
- 自动销售在途增加“跨店销售在途”标识；
- 发货确认文案从固定“调出仓库”改为按类型显示“来源仓库”或“来源门店”；
- 仓库上下文对门店返仓显示“返仓收货”，门店上下文对异店来源显示“异店发货”。

动作判定必须同时满足：

1. 主单状态允许；
2. 当前组织是该动作方向的责任方；
3. 当前账号拥有对应权限；
4. 服务端再次校验组织类型、组织 ID 和数据范围。

### 4.6 服务端方向策略

将 `InvTransferDirectionPolicy` 的“业务组织”和“库存位置”校验拆开：

- 业务方向使用 `fromDeptId/toDeptId`；
- 实际库存扣减、发货批次使用 `fromWarehouseId/toWarehouseId`；
- 人工门店要货要求当前门店等于 `toDeptId`；
- 人工门店返仓要求当前门店等于 `fromDeptId`；
- 人工异店调货要求当前门店等于 `toDeptId`，且来源、目标均为不同门店。

不要通过客户端可写的 `sourceBusinessType` 绕过人工校验。推荐在服务内部引入不可由请求控制的创建模式，例如：

```text
MANUAL
OE_REPLENISHMENT
SALES_DELIVERY_IN_TRANSIT
```

公共 `save/submit` 永远使用 `MANUAL`；`createDeliveredCrossStoreTransfer` 由内部服务显式使用 `SALES_DELIVERY_IN_TRANSIT`。

自动销售在途链路统一为：

- `InvSalesServiceImpl` 和 `InvDeliveryNoticeServiceImpl` 使用同一构造方法；
- `fromDeptId` 写来源销售门店；
- `fromWarehouseId` 写实际发货仓库；
- `toDeptId` 写目标门店；
- 服务端写入 `sourceBusinessType/sourceBusinessId`；
- 创建后直接生成待收货发货批次；
- 不进入人工调拨审批，不允许客户端伪造。

### 4.7 历史数据兼容

实施前只读统计：

- `warehouse` 但来源不是仓库或目标不是门店；
- `store_return` 但来源不是门店或目标不是仓库；
- `cross_store` 但来源/目标组织类型不是门店；
- `cross_store` 且 `fromDeptId` 为仓库、同时关联销售单或发货通知的记录；
- 仍处于处理中状态的异常方向记录数量。

处理原则：

- 不在应用启动时自动改历史数据。
- 能从销售单、发货通知唯一反推业务来源门店的未完成记录，使用带备份表和回滚脚本的数据修复 SQL。
- 已完成记录优先保持原始审计事实，通过详情补充“历史销售在途记录”说明；只有业务明确要求时才回填。
- 数据修复脚本必须先生成备份快照，并以明确的 `transfer_id` 范围更新，不能全表模糊改写。

## 5. 预计修改范围

### 5.1 手机端

- `erp-ui/src/views/mobile/mobileNavigation.js`
  - 门店增加调拨入口；
  - 仓库保留处理入口。
- `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
  - 增加门店/仓库上下文文案和筛选；
  - 配置显式的新建类型动作。
- `erp-ui/src/views/mobile/feature/index.vue`
  - 按动作或记录类型解析表单；
  - 仓库上下文禁止自动新建；
  - 兼容旧补货入口。
- `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
  - 拆分三类调拨表单；
  - 固定调拨类型和组织方向。
- `erp-ui/src/views/mobile/feature/mobileFormPayloads.js`
  - 按类型重建方向字段；
  - 清理冲突和过期字段。
- `erp-ui/src/views/mobile/feature/mobileEntityService.js`
  - 补齐异店来源门店和来源库存查询。
- `erp-ui/src/views/mobile/feature/featureActions.js`
  - 修正草稿编辑类型判断和动作方向。
- `erp-ui/src/views/mobile/feature/featureMapper.js`
  - 显示调拨类型、业务来源和实际发货仓库。
- `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`
  - 仅在配置解析或字段联动确有需要时增加类型化字段变更处理；优先通过固定类型表单减少通用联动。

### 5.2 服务端

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDirectionPolicy.java`
  - 分离业务组织与库存位置方向；
  - 增加人工异店调货当前门店约束和同店拦截。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java`
  - 引入可信内部创建模式；
  - 公共保存/提交与系统生成使用不同校验入口。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java`
  - 修正跨店销售记录的业务来源门店和实际仓库字段。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java`
  - 与发货通知链路复用同一自动在途构造规则。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferTypes.java`
  - 如需要，仅补充来源业务常量或创建模式辅助；不新增数据库 `transfer_type`。

### 5.3 测试

- `erp-ui/test/mobileContextProfiles.test.js`
- `erp-ui/test/mobileFeatureFormConfig.test.js`
- `erp-ui/test/mobileFormPayloads.test.js`
- `erp-ui/test/mobileFeatureActions.test.js`
- 建议新增 `erp-ui/test/mobileTransferDirection.test.js`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImplTest.java`
- 如拆出独立策略，新增 `InvTransferDirectionPolicyTest`

当前工作区存在大量与本任务无关的未提交修改，实施时必须逐文件检查差异，禁止重置、覆盖或顺带整理无关内容。

## 6. 实施顺序

### 阶段一：先固化业务契约

1. 新增前端方向矩阵测试，覆盖三类人工调拨的固定类型、来源实体、目标实体和当前门店。
2. 修改工作台测试：
   - 门店可进入调拨管理；
   - 仓库可进入调拨处理；
   - 仓库不能出现新建动作。
3. 新增后端方向测试：
   - 门店要货只允许仓库 → 当前门店；
   - 门店返仓只允许当前门店 → 仓库；
   - 异店调货只允许其他门店 → 当前门店；
   - 来源门店与目标门店相同必须拒绝；
   - 仓库上下文不能人工创建门店要货或异店调货。
4. 新增销售自动在途测试，证明业务来源门店和实际发货仓库可以不同，且公共接口不能伪造内部创建模式。

阶段验收：新增测试在旧实现上准确失败，失败原因对应已确认问题。

### 阶段二：收口服务端方向

1. 引入服务内部创建模式，公共保存/提交固定为人工模式。
2. 按 `fromDeptId/toDeptId` 校验业务方向，按 warehouse 字段执行库存动作。
3. 收紧人工异店调货的当前目标门店和不同门店约束。
4. 统一两条跨店销售自动生成链路。
5. 保持现有发货、分批收货、状态日志和待办类型不变。

阶段验收：调拨与发货通知目标单测通过；非法请求均在服务端被拒绝。

### 阶段三：重构手机类型化表单

1. 建立类型化配置解析器。
2. 复用现有门店要货表单作为 `warehouse` 基础。
3. 增加门店返仓和异店调货固定表单。
4. 按类型生成载荷并清理冲突字段。
5. 编辑草稿按记录原始类型打开，类型保持只读。

阶段验收：三类载荷单测逐字段通过；切换入口或编辑历史草稿不会改变 `transferType`。

### 阶段四：调整入口、列表和动作

1. 门店工作台增加调拨管理入口。
2. 仓库调拨页隐藏新建，只保留处理动作。
3. 增加上下文专属筛选和文案。
4. 列表/详情显示类型和自动销售在途来源。
5. 保持待发货按来源、待收货按目标的现有正确规则。

阶段验收：门店和仓库手机视口完成端到端人工验证。

### 阶段五：兼容与数据预检

1. 验证 `/mobile/replenishment`、统一待办深链和旧通知链接。
2. 执行异常方向只读统计。
3. 只有发现未完成异常数据时才编写修复和回滚 SQL。
4. 记录数据修复前后数量、单号和状态，不把历史修复混入前端发布步骤。

阶段验收：旧入口可用，历史正常单据不受影响，异常数据有明确处理清单。

### 阶段六：全量验证

前端目标测试：

```bash
cd erp-ui
node test/mobileContextProfiles.test.js
node test/mobileFeatureFormConfig.test.js
node test/mobileFormPayloads.test.js
node test/mobileFeatureActions.test.js
node test/mobileTransferDirection.test.js
```

前端全量测试与构建：

```bash
npm --prefix erp-ui run test
npm --prefix erp-ui run build:prod
```

后端目标测试：

```bash
mvn -pl erp-modules/erp-inventory -am \
  -Dtest=InvTransferServiceImplTest,InvDeliveryNoticeServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

如新增独立方向策略测试，将其加入 `-Dtest` 列表。目标测试通过后，再按发布窗口决定是否执行 inventory 模块全量测试。

## 7. 验收清单

### 门店

- 能从工作台进入调拨管理。
- 新建门店要货时只选择来源仓库，目标固定当前门店。
- 新建异店调货时只选择其他来源门店，目标固定当前门店。
- 新建门店返仓时来源固定当前门店，只选择目标仓库。
- 三类表单都不能更改类型。
- 来源变化后旧明细被清空。
- 异店调货不能选择当前门店作为来源。
- 待发货只在当前门店为来源时出现，待收货只在当前门店为目标时出现。

### 仓库

- 能进入调拨处理，但看不到“新建调拨”。
- 门店要货审批后，只有来源仓库可以发货。
- 门店返仓发货后，只有目标仓库可以收货。
- 仓库不能通过直链或构造请求人工创建门店要货、返仓或异店调货。

### 自动跨店销售

- 发货通知完成后能成功生成在途收货记录。
- 记录的业务来源是门店，实际发货位置是仓库。
- 目标门店能正常收货。
- 该记录不进入人工调拨审批。
- 手机详情显示“跨店销售在途”，不会把发货仓库展示成来源门店。
- 重复回调或重复发货不会生成重复调拨记录。

### 接口安全与兼容

- 非法方向即使绕过前端也被服务端拒绝。
- 客户端伪造 `sourceBusinessType` 不能进入系统生成模式。
- 旧 `/mobile/replenishment` 链接继续可用。
- 统一待办跳转、发货批次、收货批次、调拨记录和导出不回归。

## 8. 风险与缓解

### 风险一：业务组织与库存位置长期混用

缓解：本次明确 `Dept` 表达业务来源/目标，`Warehouse` 字段表达实际库存位置；方向策略和页面文案分别使用，不再用一个“解析后 ID”同时承担两种语义。

### 风险二：历史异常 `cross_store` 数据

缓解：先统计、后决定；不自动全表修复。未完成且来源可唯一反推的记录使用可回滚 SQL，已完成记录优先保留审计事实。

### 风险三：新旧手机入口重复

缓解：旧补货路由保留兼容但不再作为第二套业务实现；所有表单和载荷最终调用同一类型化配置与同一调拨 API。

### 风险四：仓库角色同时拥有新增权限

缓解：前端按上下文隐藏只是体验优化，服务端必须以当前组织类型拒绝人工创建，不能依赖角色菜单配置。

### 风险五：自动销售链路使用客户端字段绕过校验

缓解：内部创建模式只能由 Java 服务调用栈传递，不从请求体读取；`sourceBusinessType` 仅作为审计数据，不能作为授权依据。

## 9. 回滚策略

- 前端入口、类型化表单和展示调整均可按文件回滚；旧 `/mobile/replenishment` 在整个发布周期保留，可作为临时兼容入口。
- 服务端保留原有三个 `transfer_type`，不做枚举迁移，因此代码回滚不会遇到未知类型值。
- 如执行历史数据修复，必须先落备份表并提供按 `transfer_id` 恢复原字段的回滚 SQL。
- 发布后若新建入口异常，可临时关闭门店调拨新建动作，但不能放宽服务端方向校验或恢复仓库人工创建。

## 10. 非本次范围

- 不新增第四种数据库调拨类型。
- 不重做桌面端调拨页面；仅接受共享服务端方向校验带来的必要适配。
- 不重做调拨审批引擎、发货批次或差异处理模型。
- 不自动清洗全部历史调拨数据。
- 不调整 OE 维修补货入口和内部生成规则。
