# ERP-NEW_2 阶段 12C3B：收货 Guarded Mapper 检查点

> 日期：2026-08-02
> 分支：`codex/erp-new-2-rebuild`
> 基线：`cfbd0b8d feat: add transfer receipt prepared mutation skeleton`
> 状态：收货不可变审计 insert 与条件 mutation 契约已完成；唯一 Mutation Executor 尚未接线，Gate 保持关闭

## 1. 实际结果

1. Prepared Mutation 已补齐目标汇总库存、目标余额和 allocation 的旧值、新值及版本快照，并新增稳定排序的库存流水与台账写意图。
2. 新增 `InvTransferReceiptGeneratedId`，仅用于承接 MyBatis 生成主键；Prepared Mutation 本身仍保持不可变。
3. 收货专用 Persistence Mapper 已覆盖收货头、目标库存、派生批次、目标余额、库存流水、allocation 审计、serial 审计、台账和状态日志 insert，以及 allocation、serial、发货明细、调拨明细与两层生命周期的 guarded update。
4. 新目标库存和余额使用带正数及守恒条件的 `INSERT ... SELECT`；已存在记录使用旧值、版本与上限条件更新。所有契约均要求后续 Executor 严格断言影响行数为 1。
5. 发货明细和调拨明细按锁定旧累计值及数量上限更新；发货单生命周期还校验 `inventory_write_version = 'V2_DETAIL'`，调拨单生命周期校验旧状态与旧版本。
6. 收货台账不写 `shipment_allocation_id`：该列已有出库台账唯一约束。入库的合格/残损双行通过 `receipt_allocation_id + receipt_disposition` 唯一维度追溯来源，避免与既有出库记录冲突。
7. 部分收货后的同一发货单可再次进入收货；当前发货单完成但同一调拨单仍有其他未收明细时，发货单为 `received`，调拨单保持 `partial_received`。
8. Planner 输入守卫会拒绝空的调拨明细全量快照、不可创建状态、非法请求指纹/计划版本、缺失对账批次及非法版本，确保第一条 DML 前事实完整。

## 2. 验证结果

- 后端完整离线 Reactor：609 项通过，0 失败、0 错误、0 跳过。
- 本检查点定向测试：26 项通过，覆盖 Mapper 绑定与 SQL 守卫、只锁服务、共享 Composer 和纯 Mutation Planner。
- `erp-ui-next npm run verify`：Prettier、ESLint、类型检查、OpenAPI 门禁、93 项测试及生产构建全部通过。
- `git diff --check` 通过。

## 3. 安全边界

- Mapper 仅声明和绑定；CreationService 仍不会调用这些 mutation，两个服务端 Gate 保持默认关闭。
- 未进行真实数据库集成验证；MyBatis 多参数生成键、数据库约束与事务回滚仍须在 ERP-NEW_2 独占本地数据库可用后另行验证。
- 未使用 Docker，未启动任何服务，未连接 MySQL、Redis、Nacos 或其他数据库，未执行迁移、DDL 或 DML。
- 未安装或更新依赖，未删除旧前端，未 push、未 merge，未访问或修改受保护原项目。

## 4. 下一检查点

12C3C 将实现唯一、窄职责的 `InvTransferShipmentReceiptMutationExecutor`。执行前先解析并验证全部键关系，执行中按确定顺序逐条调用 Mapper；任一影响行数非 1、生成键无效或唯一约束异常都必须直接抛出，由外层事务整体回滚，禁止捕获后继续或形成部分成功。
