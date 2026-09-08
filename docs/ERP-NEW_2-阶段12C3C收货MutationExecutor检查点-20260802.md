# ERP-NEW_2 阶段 12C3C：收货 Mutation Executor 检查点

> 日期：2026-08-02
> 分支：`codex/erp-new-2-rebuild`
> 基线：`d71b0baa feat: add guarded transfer receipt mutations`
> 状态：收货原子写入源码骨架与离线验证已完成；两个服务端 Gate 继续默认关闭

## 1. 实际结果

1. 新增唯一、窄职责的 `InvTransferShipmentReceiptMutationExecutor`。它只依赖收货专用 Persistence Mapper，不持有事务、不执行读取，也不捕获数据库异常。
2. 执行器在第一条 DML 前完成全量关系预检：目标汇总库存、派生批次、目标余额、来源 allocation、serial、汇总流水、明细台账与两层明细累计必须形成完整且唯一的闭环。
3. 固定执行收货头、目标库存、目标批次、目标余额、汇总流水、allocation 审计、serial、来源累计、明细累计、库存台账、生命周期和状态日志；循环内不重新规划或发现新锁键。
4. 所有单行 insert/update 均严格要求影响行数为 1；数据库生成的收货、目标库存、派生批次、目标余额、汇总流水和 allocation 审计 ID 均要求为正数，并传递给后续依赖写入。
5. 条件更新返回 0、多行、生成键无效或唯一约束异常都会立即抛出；不存在捕获后继续，因此由外层事务统一回滚。
6. 短缺 serial 仅写不可变差异审计，不执行位置或库存移动；合格和残损 serial 才按各自目标批次、余额、库位与状态执行 guarded move。
7. `InvTransferShipmentReceiptCreationService` 保持 `Propagation.MANDATORY`，仍是唯一事务所有者；固定流程为 Gate、头锁、组织/方向校验、完整锁边界、Request Policy、纯 Planner、Mutation Executor。
8. 收货创建时间在第一条 DML 前写入 Prepared Mutation，并同时用于持久化与返回结果，避免数据库时间和响应时间不一致。
9. 外层 `InvTransferCommandExecutor` 的请求幂等边界保持不变；本阶段没有绕过、复制或另建第二套幂等入口。

## 2. 验证结果

- 本检查点扩展定向测试：46 项通过，覆盖 Planner、Mapper 绑定、锁边界、CreationService、Mutation Executor、外层命令幂等和命令事务契约。
- Mutation Executor 新增 10 项测试，覆盖固定成功顺序、生成键传递、第一处和后段失败停止、多行拒绝、生成键无效、唯一冲突原样传播、纯短缺 serial 以及首条 DML 前关系拒绝。
- 后端完整 Reactor：28 个模块全部成功；库存模块 619 项通过，0 失败、0 错误、0 跳过。
- `erp-ui-next npm run verify`：31 个测试文件、93 项测试全部通过，生产构建成功。
- `git diff --check`、受保护路径/共享连接串/Gate/事务边界静态审计通过。

## 3. 安全边界

- 两个收货服务端 Gate 仍使用配置默认值 `false`；本检查点不会令真实请求进入写入路径。
- 未进行真实数据库集成验证。当前证据只能证明异常传播、调用停止和外层事务声明，不能替代 MySQL 对真实回滚、生成键、唯一冲突、gap/next-key 锁和死锁行为的验证。
- 未使用 Docker，未启动任何服务，未连接 MySQL、Redis、Nacos 或其他数据库，未执行迁移、DDL 或 DML。
- 未安装或更新依赖，未删除旧前端，未 push、未 merge，未访问或修改受保护原项目。

## 4. 下一检查点

继续保持 Gate 关闭。后续可在纯源码范围完善调用端接线与前端收货确认交互；真实持久化验证必须等待 ERP-NEW_2 专属、非 Docker 且可证明完全隔离的数据库环境，并另立检查点记录端口、schema、账号、运行目录和回滚证据。
