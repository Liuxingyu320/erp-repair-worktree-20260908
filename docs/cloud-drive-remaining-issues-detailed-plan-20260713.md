# 云盘剩余问题详细实施计划（2026-07-13）

> 实施状态（2026-07-13）：阶段 0～5 的 P0 已完成。上传容量预占、失败恢复、
> 组织预算漂移/层级异常只读保护、桌面与移动端提示及原生 MySQL 并发验证均已落地；
> 阶段 6～8 的 P1/P2 仍按本文边界作为后续扩容增强，不影响本轮上线。

## 1. 计划结论

本计划基于当前项目已经落地的云盘一期代码，不重复实现已有功能。下一轮建议按以下顺序执行：

1. **P0-A：上传前全局容量硬预占**。解决 `BLOCK` 当前只约束配置、不原子约束每次上传的问题。
2. **P0-B：组织换父级后的预算漂移保护**。组织树变化导致预算失效时，受影响组织盘自动进入“预算阻断写入”，修复后自动恢复，不删文件。
3. **P0-C：本机全链路验收与灰度上线准备**。在不使用虚拟机、Docker、Testcontainers 的前提下验证迁移、并发、权限、桌面和移动端流程。
4. **P1：批量额度异步对账、组织查询优化、管理权限拆分和前端按需加载**。
5. **P2：共享组织盘内个人贡献额度、断点续传和百万节点搜索专项**。这些不并入本轮 P0，避免扩大上线风险。

本轮实施完成定义是：任何已受管上传都不能使“已提交用量 + 未完成上传预占”超过可分配容量；组织目录任意换父级后，预算违规空间不能继续写入；所有清理、下载和原文件保留能力不受影响。

## 2. 当前项目事实与剩余缺口

### 2.1 已经完成，不再重做

当前项目已经具备：

- 全员、岗位、个人例外三层个人盘额度，优先级为 `USER > POST > GLOBAL`。
- 每组织额度、组织树预算、公共/个人/组织三个容量池。
- 动态组织建盘、改名、停用只读、删除归档、定时和手动对账。
- 配置影响预览、影响哈希、版本控制、容量配置行锁、原因和操作审计。
- 管理中心四个页签、组织树、批量配置、岗位选择、个人搜索、额度来源和超额提示。
- SQL 源文件与部署镜像、后端和前端自动化测试、生产构建基线。

现有详细设计见：

- `docs/cloud-drive-organization-quota-detailed-plan-20260713.md`
- `sql/erp_cloud_drive_organization_quota_20260713.sql`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveAdminController.java`
- `erp-ui/src/views/drive/components/quota/DriveQuotaCenterDrawer.vue`

### 2.2 P0-A：全局容量尚未进入上传原子链路

已经核实的调用链：

```text
DriveUploadService
  -> DriveQuotaService.preflight()       只做单空间快照预检
  -> DriveStorageProvider.put()          先写物理对象
  -> DriveUploadPersistence.persist()
       -> DriveQuotaService.reserve()    事务内只占单空间额度
       -> DriveNodeMapper.insertNode()
```

相关文件：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveQuotaService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveCapacityService.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveSpaceMapper.xml`

当前 `DriveCapacityService` 会汇总实际用量，也会在配置切换为 `BLOCK` 时拒绝不合法的容量设置，但上传热路径没有锁定全局容量配置，也没有计入并发上传中的未完成对象。因此存在以下边界：

- 两个空间都还有自己的额度时，并发上传可能共同越过全局可分配容量。
- 对象已经写入后，数据库提交才可能失败，用户浪费上传时间。
- 进程在对象写入后、节点提交前退出时，物理对象和全局容量之间没有持久预占关系。
- 补偿删除失败的对象没有计入 `actualUsedBytes`，继续上传可能进一步占满真实存储。

### 2.3 P0-B：组织树预算只在配置变更时校验

当前 `DriveOrganizationBudgetService` 在组织配置、类型规则或自动建盘时根据当时的 `sys_dept.ancestors` 校验预算；`SysDeptServiceImpl.updateDept()` 可以在系统服务中更新组织父级和所有子级 ancestors，但不会通知文件服务。

相关文件：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationBudgetService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationProvisioningService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationReconcileService.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`

因此，组织盘配置本身未变化但组织被移动到另一个预算树下时，新的上级预算可能立即超分，现有同步只会继续同步名称、状态和额度，不会把这种漂移作为单独状态反馈给管理员或写入链路。

### 2.4 P1：当前规模可用，但扩容后会出现长事务和全量查询

- `DrivePersonalQuotaPolicyService.savePolicy()` 和 `deletePolicy()` 在策略事务内调用 `reconcileExistingUsers()`，全员策略在用户量较大时会形成长事务。
- `DriveEffectiveQuotaService.listEffectiveUsers()` 会加载全部有效用户上下文、全部策略和全部个人空间，再在 Java 中分页筛选。
- `DriveSpaceService.appendDynamicOrganizationSpaces()` 会对所有可读组织逐个调用 `syncOrganization()`；管理者范围大时会形成多次数据库往返。
- `DriveOrganizationMapper.selectAllOrganizations()` 对每个组织使用相关子查询统计直属成员。
- `DriveQuotaCenterDrawer` 在云盘页面中静态引入，使普通用户也进入管理中心组件打包链路。
- `/drive/admin/**` 统一使用 `drive:quota:manage`，容量、个人策略、组织配置没有做到职责分离。

这些不阻塞当前约 155 名有效用户、74 个有成员组织的运行，但应在组织和人员规模继续增长前处理。

## 3. 关键技术决策

### 3.1 使用“持久上传预占账本”，不只做提交前 SUM 校验

只在 `DriveUploadPersistence.persist()` 里锁容量配置并查询 `SUM(drive_space.used_bytes)`，可以保证已提交节点不越界，但不能覆盖对象写入到数据库提交之间的时间，也不能覆盖进程退出或补偿删除失败的物理对象。

本计划采用更完整的方案：

```text
上传前短事务：锁容量配置 -> 计算已提交 + 未完成预占 -> 建立预占记录
对象写入：不持有数据库事务
提交短事务：锁容量配置 -> 锁预占 -> 锁父目录/空间 -> 节点和用量提交 -> 删除预占
失败清理：确认对象不存在后才释放预占；删除失败则保留为清理失败并继续占容量
```

这样即使服务进程崩溃，预占记录仍在；过期任务会先清理对象，再释放容量。全局容量的安全口径固定为：

```text
capacityAccountedBytes = committedUsedBytes + pendingReservationBytes
```

### 3.2 `WARN` 也建立预占，`BLOCK` 才拒绝超限

如果只在 `BLOCK` 建预占，上传可能在 `WARN` 状态开始，随后管理员切换为 `BLOCK`，该上传仍是未追踪对象。为消除这个切换竞态，容量预占开关启用后，`WARN` 和 `BLOCK` 都写预占账本：

- `WARN`：允许超限，但显示告警并记录指标。
- `BLOCK`：`已提交 + 未完成预占 + 本次文件` 超过可分配容量时，在写对象前拒绝。

### 3.3 不把远程文件服务调用塞入组织主数据事务

不修改 `SysDeptServiceImpl.updateDept()` 去同步调用文件服务，原因是系统服务和文件服务是两个进程，远程失败会把组织编辑绑定到文件服务可用性上，也无法形成真正的跨服务数据库事务。

本计划在文件服务中使用实时组织目录派生“预算阻断”状态：

- 组织换父级后，下一次空间列表、写入校验或对账都使用最新 ancestors。
- 预算违规时不修改管理员保存的 `lifecycle_status`，只派生 `budgetBlocked=true`。
- 受影响空间禁止普通写入，但允许读取、下载、移入回收站和彻底清理。
- 调整组织位置、上级预算或下级额度后，重新计算通过即自动恢复，无需人工解除只读。

### 3.4 锁顺序统一，流式上传期间不持有数据库锁

涉及上传预占和提交的数据库锁顺序固定为：

```text
drive_capacity_config -> drive_upload_reservation -> drive_node(parent) -> drive_space
```

对象存储流式写入期间不持有上述数据库事务和行锁，避免一个慢上传阻塞全部上传。所有加减法使用溢出安全计算和“剩余量比较”，不直接使用可能溢出的 `used + bytes` Java 表达式。

## 4. 实施范围和顺序

| 阶段 | 优先级 | 结果 | 是否进入本轮首批实现 |
| --- | --- | --- | --- |
| 0. 基线与保护 | P0 | 固定数据、接口、测试和工作区边界 | 是 |
| 1. 上传预占迁移 | P0 | 新增可恢复的上传容量账本 | 是 |
| 2. 全局容量原子链路 | P0 | 上传前拒绝、提交原子、失败可恢复 | 是 |
| 3. 组织预算漂移保护 | P0 | 换父级后自动阻断/恢复写入 | 是 |
| 4. 管理端提示与可观测性 | P0 | 管理员和普通用户都能理解阻断原因 | 是 |
| 5. 原生 MySQL 并发与全链路验收 | P0 | 无虚拟机的上线证据 | 是 |
| 6. 异步额度物化 | P1 | 消除全员策略长事务 | P0 后实施 |
| 7. 查询、权限和包体优化 | P1 | 大规模可用和最小权限 | P0 后实施 |
| 8. 成员贡献额度/断点续传/大搜索 | P2 | 独立产品增强 | 不并入本轮 |

## 5. 阶段 0：基线与保护

### 5.1 操作

- 仅编辑云盘、云盘迁移、云盘菜单权限和云盘测试文件。
- 不整理、暂存、回滚或覆盖当前工作区内 OA、HR、库存、待办等其他改动。
- 记录目标库以下基线：
  - 三类 `drive_space` 数量、`quota_bytes` 和 `used_bytes` 汇总。
  - `drive_node` 文件数与 `SUM(size_bytes)`。
  - 当前 `drive_capacity_config`、组织配置、策略数量。
  - 物理存储根目录或专用 MinIO bucket 的真实容量口径。
- 确认 `physical_capacity_bytes` 表示云盘专属可用容量，而不是整台主机磁盘名义容量。
- 对比物理存储已占用量与 `drive_space.used_bytes`；若存在历史孤儿对象或其他业务共用占用，先清理，或从可配置物理容量中扣除差额，差额未解释前不得开启 `BLOCK`。
- 保持 `DRIVE_QUOTA_POLICY_ENABLED=false`、`DRIVE_ORGANIZATION_SYNC_ENABLED=false` 和新增的容量预占开关默认关闭，直到迁移和验证完成。

### 5.2 验收

- 当前文件服务、系统菜单、网关和前端测试基线全部通过。
- `git diff --check` 无冲突标记或空白错误。
- 未使用虚拟机、Docker、Testcontainers。

## 6. 阶段 1：上传预占数据库迁移

### 6.1 修改文件

- `sql/erp_cloud_drive_organization_quota_20260713.sql`
- `docker/mysql/db/erp_cloud_drive_organization_quota_20260713.sql`
- `erp-modules/erp-file/src/test/java/com/erp/file/drive/sql/CloudDriveSqlSourceTest.java`

### 6.2 新增 `drive_upload_reservation`

建议字段：

| 字段 | 用途 |
| --- | --- |
| `reservation_id` | 不可猜测的上传预占标识，主键 |
| `space_id` | 目标空间 |
| `storage_key` | 目标对象键，唯一；用于崩溃后清理 |
| `reserved_bytes` | 本次保守预占字节数 |
| `status` | `RESERVED / CLEANING / CLEANUP_FAILED` |
| `expire_time` | 允许清理的最早时间 |
| `retry_count` | 清理失败重试次数 |
| `next_retry_time` | 下次允许重试时间 |
| `version` | 认领和状态切换的乐观版本 |
| 审计字段 | 创建、更新时间和安全错误摘要 |

索引和约束：

- 主键 `reservation_id`。
- 唯一键 `storage_key`，避免同一对象键被重复预占。
- 索引 `(status, expire_time, reservation_id)`，支持过期扫描。
- 索引 `(space_id, status)`，支持空间诊断。
- `reserved_bytes > 0` 由服务层和测试共同保证，保持 MySQL 5.7 兼容。

成功提交或确认对象已删除后直接删除预占行；只有未完成和清理失败记录保留，避免表无限增长。

### 6.3 迁移要求

- 保持 additive、幂等、MySQL 5.7 兼容。
- 不更新或删除现有 `drive_node`、`drive_space.used_bytes`。
- SQL 源文件和 `docker/mysql/db` 镜像保持字节一致；镜像仅用于部署契约，本轮不启动 Docker。
- 迁移重复执行两次后表、索引和数据不重复。

## 7. 阶段 2：全局容量原子链路

### 7.1 新增文件

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveUploadReservation.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveUploadReservationMapper.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveUploadReservationMapper.xml`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadReservationService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadReservationPersistence.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/task/DriveUploadReservationCleanupTask.java`
- 对应测试类。

事务方法放在独立 Spring Bean 中，避免同类内部调用导致 `@Transactional` 不生效。

### 7.2 修改文件

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveUploadPersistence.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveCapacityService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveCapacityMapper.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveCapacityMapper.xml`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveSpaceMapper.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveSpaceMapper.xml`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveCapacityOverviewVo.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveProperties.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveFeatureGuard.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveConstants.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/constant/DriveErrorCodes.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/metric/DriveMetrics.java`
- `erp-modules/erp-file/src/main/resources/application-dev.yml`
- `erp-modules/erp-file/src/main/resources/application-local.yml`

### 7.3 上传前预占

`DriveUploadService` 在文件类型、大小、空间权限和单空间快照额度预检通过后：

1. 生成 `storageKey` 和 `reservationId`。
2. 调用独立事务 `reserveBeforeStorage(spaceId, storageKey, bytes, actor)`。
3. 事务锁定固定的 `drive_capacity_config` 行。
4. 单条聚合查询得到所有状态空间的 `committedUsedBytes`。
5. 聚合 `RESERVED / CLEANING / CLEANUP_FAILED` 得到 `pendingReservationBytes`。
6. `BLOCK` 下验证：
   - 物理容量已配置。
   - 三个池和逻辑分配仍合法。
   - `bytes <= allocatable - committed - pending`。
7. 写入预占记录并提交短事务。
8. 事务提交后才调用 `DriveStorageProvider.put()`。

新增开关建议为：

```text
DRIVE_CAPACITY_RESERVATION_ENABLED=false
DRIVE_UPLOAD_RESERVATION_TIMEOUT_MINUTES=120
DRIVE_UPLOAD_RESERVATION_CLEANUP_CRON=0 */5 * * * *
```

开关关闭仅用于兼容迁移发布窗口；正式启用 `BLOCK` 前必须开启该开关。

### 7.4 成功提交

将 `DriveUploadPersistence.persist()` 调整为接收并验证 `reservationId`：

1. 锁容量配置行。
2. `SELECT ... FOR UPDATE` 锁预占行，核对空间、对象键、字节数和状态。
3. 锁定并重新验证父目录。
4. 调用现有 `DriveQuotaService.reserve()` 原子占用单空间额度。
5. 插入 `drive_node`。
6. 删除预占行。
7. 同一数据库事务提交。

预占从 pending 变为 `drive_space.used_bytes` 的过程在一个事务中完成，全局核算总量不出现空窗，也不重复计算。

同名唯一键冲突时整个提交事务回滚，预占仍然存在，当前最多 10 次的同名重试可继续复用同一个物理对象和预占。

### 7.5 失败和崩溃恢复

- 对象写入失败：检查并删除可能存在的对象；只有确认对象不存在后才删除预占。
- 数据库提交失败：沿用现有补偿删除；确认删除成功后释放预占。
- 对象删除或 `exists()` 检查失败：把预占标记为 `CLEANUP_FAILED`，继续计入全局容量，不向客户端泄露对象键。
- 进程退出：预占保持 `RESERVED`；超时任务认领为 `CLEANING`，在数据库事务外删除对象，成功后再删除预占。
- 清理任务重复执行必须幂等；多实例只能有一个任务通过版本条件认领同一记录。
- 任务在认领后进程退出时，超过认领超时的 `CLEANING` 可以被重新认领；`CLEANUP_FAILED` 按有上限的退避时间重试。
- 清理失败按现有指标模式记录低基数错误类型，不记录文件名、用户名或对象键。

### 7.6 容量总览和配置保存

`DriveCapacityOverviewVo` 增加：

- `pendingUploadBytes`：未完成和清理失败预占。
- `capacityAccountedBytes`：已提交用量加预占。
- `staleReservationCount`、`cleanupFailedReservationCount`。

容量告警和 `BLOCK` 保存校验改用 `capacityAccountedBytes`。管理页明确区分：

- 已提交文件用量。
- 上传中/待清理预占。
- 当前可继续上传容量。

管理员不能把物理可分配容量降到 `已提交 + 未完成预占` 以下，也不能在存在超限预占时切换到 `BLOCK`。

容量预占开关关闭时，后端必须拒绝把容量模式切换为 `BLOCK`；预占开关开启但迁移表或固定容量配置缺失时上传失败关闭，不能静默退回旧逻辑。

### 7.7 P0-A 自动化覆盖

新增或扩展：

- `DriveUploadReservationServiceTest.java`
- `DriveUploadReservationPersistenceTest.java`
- `DriveUploadReservationCleanupTaskTest.java`
- `DriveUploadServiceTest.java`
- `DriveCapacityServiceTest.java`
- `DriveMapperBindingTest.java`
- `CloudDriveSqlSourceTest.java`

必须覆盖：

- `BLOCK` 在写对象前拒绝容量不足，`storage.put()` 未被调用。
- `WARN` 允许但仍建立预占并产生告警。
- 两个不同空间并发争抢最后容量时只能一个成功预占。
- 配置从 `WARN` 切换为 `BLOCK` 时，已经开始的上传仍被 pending 计入。
- 单空间额度不足时全局预占和对象都能清理。
- 对象写入失败、节点提交失败、同名重试、进程模拟退出、过期回收。
- 删除对象失败时预占不释放，恢复后只释放一次。
- 已归档、只读、停用和遗留空间的 `used_bytes` 全部计入 committed。
- 长整型边界不溢出。

## 8. 阶段 3：组织换父级后的预算漂移保护

### 8.1 新增模型

新增：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveOrganizationBudgetViolationVo.java`

建议字段：

- `budgetDeptId`、`budgetDeptName`。
- `budgetBytes`、`allocatedBytes`、`exceededBytes`。
- `affectedDeptIds`。

### 8.2 预算服务改造

修改：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationBudgetService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/mapper/DriveOrganizationMapper.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveOrganizationMapper.xml`

新增三个明确入口：

1. `detectViolations()`：一次加载组织快照和配置快照，返回全部违规预算根及受影响组织。
2. `violationForDept(deptId)`：判断目标组织是否位于任一违规预算树内。
3. `requireWriteAllowed(deptId)`：违规时抛出稳定错误码 `DRIVE_ORG_BUDGET_EXCEEDED`，消息说明具体上级、超出容量和处理方式。

配置保存仍保留现有“保存前拒绝超预算”；新检测专门处理外部组织目录变化形成的漂移。

### 8.3 派生写入阻断，不覆盖生命周期配置

修改：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveSpaceService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveSpaceVo.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveOrganizationQuotaVo.java`

规则：

- `requireWritableSpace()` 对组织盘追加实时预算校验。
- 空间列表一次计算阻断集合，避免每个组织盘重复全表查询。
- 违规组织盘返回 `canWrite=false`，同时增加 `writeBlockedCode` 和用户可读提示。
- 不把 `drive_space.status` 或 `drive_org_space_config.lifecycle_status` 改成 `READ_ONLY`，避免覆盖人工配置。
- `requireCleanupSpace()` 不受预算阻断影响，仍允许有权限人员清理容量。
- 下载、预览、列表保持可用。
- 预算修复后下一次列表和写入校验自动恢复。

### 8.4 对账结果不再把预算漂移伪装成普通同步失败

修改：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationProvisioningService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationReconcileService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/vo/DriveOrganizationReconcileVo.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/task/DriveOrganizationSyncTask.java`

对账先生成一份统一预算快照，再执行名称和生命周期同步。返回结果区分：

- 技术同步失败组织。
- 预算违规根。
- 因预算被阻断的组织数量和 ID。

预算违规不阻止组织名称、停用或归档状态同步，也不自动改额度或移动文件。

### 8.5 P0-B 自动化覆盖

新增或扩展：

- `DriveOrganizationBudgetServiceTest.java`
- `DriveOrganizationProvisioningServiceTest.java`
- `DriveOrganizationReconcileServiceTest.java`
- `DriveSpaceServiceTest.java`
- `DriveAuthorizationServiceTest.java`

必须覆盖：

- 一个有额度的子组织从预算充足的公司移动到预算不足的公司。
- 整个子树移动，所有受影响下级被识别且无关组织不受影响。
- 同时存在多层预算时，返回最近和全部违规预算根。
- 组织停用、删除、归档和禁用配置不计入逻辑分配的规则保持一致。
- 违规时上传、新建、重命名、移动和恢复被拒绝；下载与清理允许。
- 上调预算、降低下级额度或移回原父级后自动恢复写入。
- ancestors 异常、循环或缺失时保守失败并记录安全告警，不产生无限循环。

## 9. 阶段 4：管理端提示与使用便利性

### 9.1 后端接口

修改：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveAdminController.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveCapacityService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveOrganizationConfigService.java`

接口保持原路径兼容，只扩展返回字段：

- `/drive/admin/status` 增加容量预占开关、超时和清理任务状态。
- `/drive/admin/quota/capacity` 增加 pending、accounted、清理失败数量。
- `/drive/admin/organizations` 增加预算阻断状态和具体违规根摘要。
- `/drive/admin/organizations/reconcile` 返回技术失败和预算违规两个独立部分。

### 9.2 前端

修改：

- `erp-ui/src/api/drive/admin.js`
- `erp-ui/src/views/drive/components/quota/DriveCapacityPanel.vue`
- `erp-ui/src/views/drive/components/quota/DriveOrganizationQuotaPanel.vue`
- `erp-ui/src/views/drive/components/quota/DriveQuotaCenterDrawer.vue`
- `erp-ui/src/views/drive/components/DriveSpaceSidebar.vue`
- `erp-ui/src/views/drive/index.vue`
- `erp-ui/src/views/mobile/drive/index.vue`
- `erp-ui/src/views/drive/driveState.js`
- `erp-ui/test/cloudDriveQuotaManagement.test.js`
- `erp-ui/test/cloudDriveDesktop.test.js`
- `erp-ui/test/mobileCloudDrive.test.js`

交互要求：

- 容量总览显示“已提交”“上传中/待清理”“安全计入总量”“剩余可上传”。
- 存在 `CLEANUP_FAILED` 时显示明确告警和数量，不暴露对象键。
- 组织行显示“因上级预算不足暂时只读”，点开可看到违规上级、预算、已分配和超出量。
- 普通用户侧边栏和移动端显示阻断原因，不只把上传按钮静默变灰。
- 预算阻断时保留“下载”和“清理空间”，隐藏会产生普通写入的操作。
- 对账完成提示分别报告同步失败和预算违规，不能显示“全部同步成功”但实际仍有预算问题。

## 10. 阶段 5：无虚拟机验证和灰度发布

### 10.1 自动化回归

从项目根目录执行：

```bash
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-file -am -DskipTests package
mvn -pl erp-modules/erp-system -Dtest=CloudDriveMenuSqlSourceTest,SysMenuServiceImplTest,SysUserControllerAuthRoleScopeTest test
mvn -pl erp-gateway -Dtest=FileDriveRouteConfigTest test

cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test
npm run build:prod

cd /Users/liuxingyu/Desktop/备份/ERP-NEW
git diff --check
```

前端构建使用 `package.json` 支持的 Node 版本，不通过虚拟机解决环境问题。若旧 `thread-loader` 在本机 Node 上挂起，应在项目构建配置中形成可复现修复，不能只保留未记录的临时命令。

### 10.2 本机 MySQL 迁移和并发验证

- 创建唯一命名的临时测试库，不接触生产库。
- 导入云盘基础迁移和本次组织额度迁移。
- 连续执行本次迁移两遍。
- 验证表、索引、默认开关、已有用量和对象元数据不变。
- 使用两个独立数据库连接并发预占最后一段容量，验证 `BLOCK` 只有一个事务成功。
- 模拟服务退出后留下 `RESERVED`，再运行过期清理，验证对象和预占都被幂等清除。
- 测试结束删除临时测试库和临时存储目录。

### 10.3 本机真实服务验收

项目本地路由端口为：网关 `8080`、认证 `9200`、系统 `9201`、文件服务 `9300`、前端开发服务通常为 `1025`。使用已有本机 MySQL、Redis和本地文件存储启动，不启动 Nacos、MinIO 容器或虚拟机；文件服务和网关使用项目已有 `local` profile。

验收顺序：

1. 迁移已执行，所有新开关关闭，验证旧云盘流程不回退。
2. 开启 `DRIVE_CAPACITY_RESERVATION_ENABLED`，容量模式仍为 `WARN`。
3. 验证上传成功、取消、对象写入失败、数据库失败和过期清理。
4. 设定小容量，在两个用户、两个空间并发上传，验证 pending 和 committed 总量。
5. 切换 `BLOCK`，验证超限在上传开始前被拒绝。
6. 移动测试组织到预算不足父级，验证桌面和移动端只读提示、清理能力和自动恢复。
7. 验证管理员数据范围，不能查看或修复范围外组织。
8. 清理测试节点、预占、临时对象和测试组织变化，恢复运行开关。

### 10.4 灰度上线顺序

1. 备份并记录四张既有配置表、`drive_space` 和新增预占表快照。
2. 先执行 additive 迁移，不开新功能。
3. 发布兼容代码，保持容量预占、岗位策略和组织同步开关关闭。
4. 确认物理容量口径和历史对象用量；物理存储与账本差额未解释时容量模式保持 `WARN`。
5. 开启容量预占，观察 pending 是否能正常归零、清理失败是否为零。
   开启后先等待旧版本未预占上传完成，再允许切换 `BLOCK`。
6. 开启岗位策略；先管理员账号，再小范围用户。
7. 开启组织同步；先一个低用量组织，再按类型分批。
8. 最后切换 `BLOCK`。

## 11. P1：批量额度异步物化

### 11.1 问题

当前策略保存事务会同步遍历现有个人盘。用户数量较大时，管理员请求时间、容量配置行锁时间和回滚成本都会增大。

### 11.2 方案

新增持久任务表 `drive_quota_reconcile_job`，策略保存和任务写入在同一事务：

- 任务保存 `subject_type`、`subject_id`、策略版本、状态、游标、重试次数和下次执行时间。
- 同一对象的旧待执行任务由新版本合并或作废。
- 工作者每批处理 200 个已存在个人盘，每批独立事务。
- 单个用户失败不阻断整批，达到重试上限后进入 `DEAD` 并告警。
- 用户打开个人盘或上传前仍即时对账，因此后台延迟不影响个人正确性。
- 到期的个人例外由定时扫描生成同类任务，避免长期不登录用户的物化额度一直停留在旧值。

修改范围：

- `DrivePersonalQuotaPolicyService.java`
- `DriveEffectiveQuotaService.java`
- 新增 job domain、mapper、worker 和对应 SQL/测试。

验收：全员策略影响 10 万用户时，管理请求只提交策略和任务，不在一个事务更新 10 万空间；任务可暂停、重试、续跑且最终幂等。

## 12. P1：组织和用户查询优化

### 12.1 组织目录

- 把 `selectAllOrganizations()` 的逐组织直属成员相关子查询改为一次聚合 `LEFT JOIN`。
- 普通用户列空间时只即时同步本人直属组织；可管理下级组织依赖定时/手动对账，不在每次 GET 逐个写数据库。
- 对账任务按组织 ID 分批，避免一次加载和处理全部组织。
- 管理列表继续一次加载组织、配置、空间和类型规则后在内存合并，不恢复 N+1。

### 12.2 用户额度列表

- 将关键词、组织、岗位、超额条件和分页下推到 Mapper。
- 先分页取得用户 ID，再批量查询该页岗位、策略和空间，保持多岗位确定性规则。
- 容量总览使用专用聚合，不复用带页面展示字段的全员列表。

验收数据规模：1 万、10 万用户和 1 千、1 万组织；记录查询次数、执行计划和 p95，不在没有基准证据时引入外部搜索服务。

## 13. P1：管理权限拆分

建议拆为：

- `drive:capacity:manage`：物理容量、池和 `WARN/BLOCK`。
- `drive:personal-quota:manage`：全员、岗位和个人例外。
- `drive:organization-quota:manage`：组织类型规则、组织额度、预算和对账。

兼容策略：

- 保留现有 `drive:quota:manage` 作为过渡期总权限。
- 后端授权方法接受“对应新权限或旧总权限”，避免升级后现有角色突然失权。
- 新菜单 ID 在实施时先查询目标库冲突，不能在计划中硬编码未核实的 ID。
- 对已拥有旧权限菜单的角色，迁移按原授权复制新权限；管理员继续自动获得全部权限。
- 管理中心按权限显示页签，直接调用无权接口仍由后端拒绝。

涉及：

- `DriveConstants.java`
- `DriveAuthorizationService.java`
- `DriveAdminController.java`
- `DriveActorResolver.java`
- 云盘菜单 SQL 及系统菜单测试。
- `erp-ui/src/views/drive/index.vue` 和额度管理中心组件。

## 14. P1：前端包体和管理中心加载

- 将 `DriveQuotaCenterDrawer` 改为异步组件，仅拥有管理权限且首次打开设置时加载。
- 容量、组织、岗位、个人四个面板留在同一管理 chunk，普通云盘浏览不下载管理代码。
- 保持影响预览、错误解析和权限控制不变。
- 生产构建比较修改前后的 app entry 和管理 chunk，不能只为了消除提示把阈值调大。

涉及：

- `erp-ui/src/views/drive/index.vue`
- `erp-ui/src/views/drive/components/quota/DriveQuotaCenterDrawer.vue`
- 必要时调整 `erp-ui/vue.config.js`。

## 15. P2 独立增强，不纳入本轮 P0/P1

### 15.1 共享组织盘内个人贡献额度

只有业务明确需要“某个人在共享组织盘最多上传多少”时再实施。必须先增加不可变的创建人用户 ID 和成员用量账本，明确人员改名、调动、离职、文件移动和所有权转移规则。不能用当前创建人用户名做计费键。

### 15.2 分片和断点续传

单独建立上传会话、分片状态、完成幂等和临时对象回收。它需要与本计划的全局容量预占整合：上传会话创建时预占，取消或过期清理后释放，完成时转为已提交用量。

### 15.3 大规模全文搜索

先在 1 万、10 万、100 万节点上测量当前查询；只有不达标时才选择 MySQL ngram、独立索引表或搜索服务。权限和空间范围必须先过滤。

## 16. 回滚策略

- 容量预占代码回滚前先把容量模式切回 `WARN`；若服务异常到无法安全切换，则临时关闭 `DRIVE_ENABLED` 阻止新上传。
- 停止新上传、等待正在上传的请求结束，并清理或接管全部未完成预占后，再关闭 `DRIVE_CAPACITY_RESERVATION_ENABLED` 和回滚文件服务代码；`CLEANUP_FAILED` 未解决前不得直接删除记录。
- `DRIVE_QUOTA_POLICY_ENABLED` 和 `DRIVE_ORGANIZATION_SYNC_ENABLED` 可以独立关闭，不要求回滚配置数据。
- 新表和新增字段保留，不在紧急回滚中 `DROP`。
- `drive_space.used_bytes` 和 `drive_node` 仍是已提交文件权威账本，不因控制面回滚而修改。
- 预算阻断是派生状态，回滚代码后不会留下被覆盖的生命周期配置。
- 前后端接口只新增字段，旧客户端忽略新字段即可；不删除旧 API。

## 17. 最终完成定义

以下条件全部满足才算剩余问题处理完成：

- `BLOCK` 模式下，并发上传的“已提交 + 未完成预占”永不超过可分配容量。
- 上传在写对象前即可发现全局容量不足。
- 对象写入后进程退出或补偿删除失败时，容量仍被保守计入并可由任务恢复。
- 组织任意换父级后，新的预算违规在下一次列表、写入或对账中被识别。
- 预算违规组织盘不能普通写入，但原文件不删，下载和授权清理可用；修复后自动恢复。
- 容量管理页、组织管理页、桌面和移动端都展示准确的阻断原因。
- 配额策略大批量物化不再依赖单个长事务（P1 完成后）。
- 管理权限满足最小权限且兼容现有角色（P1 完成后）。
- SQL 可重复执行，所有自动化、生产构建、本机 MySQL 并发和真实浏览器流程通过。
- 未使用虚拟机、Docker 或 Testcontainers；没有残留测试库、测试对象、测试进程或临时开启的运行开关。
