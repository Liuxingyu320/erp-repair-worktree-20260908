# ERP-NEW_2 阶段 12C3A：收货 Prepared Mutation 骨架检查点

> 日期：2026-08-02
> 分支：`codex/erp-new-2-rebuild`
> 基线：`3ae369ff feat: add transfer receipt lock validation boundary`
> 状态：完整锁定快照和纯 Prepared Mutation 已完成；mutation Mapper 与执行器尚未接线

## 1. 实际结果

1. 固定锁网关不再只返回 Composer 结果，而是返回 `InvTransferShipmentReceiptLockedBoundary`：收货规划、来源 allocation、目标汇总库存、来源/派生批次、目标库位/余额与 serial 均转换为不可变值快照。
2. 调拨明细锁从“只锁 ID”升级为锁定数量、已发和已收快照，使 12C3B 能对调拨明细累计执行旧值/上限条件更新。
3. 网关逐字段核对 Planning Mapper 与已锁 allocation、发货明细、调拨明细和来源位置，防止两个投影仅 ID 相同但数量、成本或版本不同。
4. 新增纯 `InvTransferShipmentReceiptMutationPlanner`，在第一条 DML 前一次性生成目标汇总、派生批次、目标余额、allocation 审计、serial 处置、明细累计和生命周期的完整写意图。
5. Prepared Mutation 按 item、派生批次、余额、allocation、serial 和 detail 的稳定键排序；相同事实的输入顺序变化不会改变输出。
6. 合格和残损成本只使用锁定来源成本并保留六位小数；短缺 serial 保持来源位置及 `shipped`，纯短缺不会生成目标库存、批次、余额或明细累计动作。
7. 生命周期已纯计算：残损/短缺为 `discrepancy`，无差异且剩余为 `partial_received`，全部合格为 `received`。

## 2. 验证结果

- 后端完整离线 Reactor：606 项通过，0 失败、0 错误、0 跳过。
- Planner 定向测试：6 项通过，覆盖三类处置、已存在目标复用、部分收货、纯短缺、输入顺序无关和库存守恒拒绝。
- `erp-ui-next npm run verify`：Prettier、ESLint、类型检查、OpenAPI 门禁、31 个测试文件中的 93 项测试及生产构建全部通过。
- `git diff --check` 通过；新代码未包含共享连接串、真实秘密、受保护路径或开启 Gate 的配置。

## 3. 明确未做

- 未增加或调用收货 insert/update Mapper；CreationService 仍在权威请求校验后硬拒绝 12C3 写入。
- 未创建收货头、目标库存、派生批次、余额、流水或生命周期记录。
- 未开启 Gate，未接前端 Writer 或按钮。
- 未使用 Docker，未启动服务，未连接 MySQL、Redis、Nacos 或任何数据库，未执行迁移、DDL、DML。
- 未安装或更新依赖，未删除旧前端，未 push、未 merge，未访问或修改受保护原项目。

## 4. 下一检查点

12C3B 将为 Prepared Mutation 增加收货专用记录与 guarded Mapper 契约。所有单行写必须返回 1、生成键必须为正数、唯一冲突必须直接传播；随后 12C3C 才允许由唯一的窄 Mutation Executor 调用这些方法。
