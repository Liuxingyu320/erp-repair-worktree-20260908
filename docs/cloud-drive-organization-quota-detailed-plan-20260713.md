# 云盘组织盘与岗位额度详细实施计划（2026-07-13）

## 1. 计划目标

本方案基于当前项目的实际代码、数据库结构和本机数据快照，解决以下问题：

1. 组织盘是否真正跟随公司组织树动态创建、改名、调动和停用。
2. 如何按岗位统一设定个人盘额度，同时允许给个别人设置例外额度。
3. 如何设定每个组织盘自己的总额度，以及公司/集团下属组织盘的总预算。
4. 如何避免所有逻辑额度之和超过真实存储容量。
5. 如何让管理员批量配置、预览影响、查看超额用户，并让普通使用者清楚知道额度来源。
6. 在不删除现有文件、不破坏旧接口、不使用虚拟机或 Docker 的前提下安全上线。

实施状态（2026-07-13）：第一阶段完整包已经落地到代码和迁移脚本；目标环境尚未自动执行迁移，也未自动开启运行开关。以下第 2 节保留为实施前基线，便于对照改造前后的差异。

一期完成后的全局上传容量硬预占、组织换父级预算漂移保护、规模化对账和上线验收，见 `docs/cloud-drive-remaining-issues-detailed-plan-20260713.md`。

当前已完成：

- 组织目录动态建盘、改名、停用只读、删除归档与定时/手动对账。
- 全员、岗位、个人例外三层个人盘额度，支持临时例外到期回落。
- 每组织额度、组织树预算、公共/个人/组织三个容量池与 `WARN / BLOCK` 模式。
- 保存前影响预览、哈希防陈旧确认、乐观锁、容量配置行锁、审计原因和数据范围控制。
- 桌面管理中心、组织树与批量配置、岗位选择器、个人搜索、额度来源和超额清理提示。
- SQL 连续执行两遍的本机 MySQL 8.0 幂等验证、文件服务 237 项测试、前端全仓 161 项测试和生产构建。

为避免上线即批量建盘，迁移中的四类自动规则以及 `DRIVE_QUOTA_POLICY_ENABLED`、`DRIVE_ORGANIZATION_SYNC_ENABLED` 均保持默认关闭，必须在目标环境完成容量确认后灰度开启。

## 2. 已核实的项目现状

### 2.1 当前组织盘只有“直属组织半动态”

现有逻辑位于：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveSpaceService.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveActorResolver.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveIdentityMapper.xml`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveAuthorizationService.java`

当前行为是：

- 用户打开云盘时，系统按实时 `sys_user.dept_id` 懒创建 `DEPARTMENT:<deptId>`。
- 用户调动后，下一次请求会读取数据库里的新组织，不依赖令牌中的旧部门；这一点是动态的。
- 只处理用户直属组织，不会根据 `sys_dept.ancestors` 自动列出或管理公司下属组织盘。
- 新组织没有成员访问时不会提前建盘；组织改名只在当前直属成员界面临时显示新名称，持久化名称不会主动同步。
- 组织停用、删除、合并没有明确的只读、归档和迁移流程。
- 任意有效组织类型都可能被直属成员触发建盘，没有按 `GROUP / COMPANY / STORE / WAREHOUSE` 设定启用规则。
- 组织盘写入仍要求 `drive:department:manage`，普通有访问权限的成员默认只能读。

结论：当前不是“公司下级组织树完整动态盘”，而是“本人直属组织按访问懒创建”。

### 2.2 当前额度只有空间级固定值

现有逻辑位于：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/config/DriveProperties.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/service/DriveQuotaService.java`
- `erp-modules/erp-file/src/main/resources/mapper/drive/DriveSpaceMapper.xml`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/controller/DriveSpaceController.java`
- `erp-ui/src/views/drive/index.vue`

当前默认值是：

- 单文件最大 100 MB。
- 个人盘 2 GiB。
- 组织盘 20 GiB。
- 公司公共盘 SQL 初始值 100 GiB。

当前限制是：

- `drive_space.quota_bytes` 是唯一生效额度。
- 没有岗位额度表，没有个人例外额度表，也没有公司下属组织预算。
- 配置文件默认值只影响以后懒创建的新空间，不会更新已经存在的空间。
- `drive.company-quota` 已配置但运行时未真正用于现有公司公共盘，公共盘额度实际由 SQL 和数据库记录决定。
- 当前页面只能调整正在查看的一个空间，无法集中管理全部岗位、人员和组织。
- 当前接口禁止把额度降到已使用量以下，因此不能形成“已超额、停止新增、保留原文件”的安全状态。
- `DriveQuotaService.reserve()` 和数据库条件更新已经具备并发安全性，应继续保留。

### 2.3 当前组织与岗位数据可以直接支撑新方案

项目已经具备：

- `sys_dept.parent_id`、`sys_dept.ancestors` 和 `sys_dept.dept_type` 组织树。
- 组织类型：`GROUP`、`COMPANY`、`STORE`、`WAREHOUSE`。
- `sys_user_post` 用户岗位关系。
- `sys_post` 岗位状态、编码、名称和排序。
- 角色数据范围 `sys_role.data_scope` 与自定义组织范围 `sys_role_dept`。

本机当前数据快照：

- 155 个有效用户，全部有直属组织。
- 23 个有效岗位；154 人有 1 个岗位，1 人没有岗位，当前没有多岗位用户。
- 有有效直属成员的组织共 74 个，其中 10 个 `COMPANY`、64 个 `STORE`。
- 当前仅有 3 条云盘空间：公司公共盘、1 个个人盘、1 个组织盘。
- 如果仍按个人 2 GiB、74 个组织都按 20 GiB、公共盘 100 GiB 计算，理论逻辑额度为 1,890 GiB，已经接近 1.85 TiB。

这说明不能简单地把所有有效组织直接按 20 GiB 全量建盘；必须先建立容量预算和影响预览。

## 3. 确定的目标规则

### 3.1 普通用户看到哪些盘

普通用户默认看到：

1. 自己的个人盘。
2. 公司公共盘。
3. 自己当前直属组织的组织盘。

同时满足以下规则：

- 用户调动后，旧组织盘访问立即失效，新组织盘在下一次请求即时生效。
- 没有直属组织的用户仍可使用个人盘和公司公共盘。
- 有 `drive:department:manage` 且角色数据范围覆盖下级组织的管理者，可以看到并管理范围内的多个组织盘。
- 数据范围必须由后端根据 `sys_role.data_scope`、`sys_role_dept` 和 `sys_dept.ancestors` 计算，不能相信前端传入的组织 ID。
- 数据库内部继续保留 `space_type=DEPARTMENT` 和 `DEPARTMENT:<deptId>`，前端和用户文案统一显示“组织盘”，避免高风险的数据类型迁移。

每个组织盘同时提供可配置的成员写入模式：

- `PERMISSION_ONLY`：保持当前兼容行为，只有具备 `drive:department:manage` 且在组织范围内的人可写。
- `ALL_DIRECT_MEMBERS`：该组织的直属有效成员可写，适合门店内部协作；上级组织成员不会因此自动获得写权限。
- `READ_ONLY`：除专用迁移流程外任何普通请求都不可写。

迁移时全部保持 `PERMISSION_ONLY`，不能在未检查角色和文件内容的情况下自动扩大写权限。管理员可在组织盘配置中逐个或批量切换。

### 3.2 哪些组织自动有组织盘

按组织类型建立可配置规则，初始建议为：

| 组织类型 | 默认自动启用 | 自动启用条件 | 建议默认额度 |
| --- | --- | --- | ---: |
| `GROUP` | 否 | 由公司公共盘承担集团共享 | 不单独建盘 |
| `COMPANY` | 是 | 组织有效且至少有 1 名直属有效成员，或管理员手动启用 | 40 GiB |
| `STORE` | 是 | 组织有效且至少有 1 名直属有效成员，或管理员手动启用 | 10 GiB |
| `WAREHOUSE` | 否 | 管理员按实际文件协作需求手动启用 | 20 GiB |

这些数值是推荐初值，不在迁移时强制套用到现有空间。上线前必须根据真实物理容量在管理页预览后启用。

### 3.3 个人盘额度优先级

个人盘有效额度按以下固定顺序解析：

```text
个人例外额度 > 岗位额度 > 全员默认额度
```

细则：

- 全员默认额度初始保持 2 GiB。
- 岗位策略按 `post_id` 绑定，不按岗位名称模糊匹配，防止岗位改名后失效。
- 个人例外额度可设置失效时间；到期后自动回落到岗位或全员默认额度。
- 用户没有岗位时使用全员默认额度。
- 将来出现多岗位时，先按岗位策略的显式 `priority` 取最高优先级；同优先级时取较大额度，并在影响预览中明确标记来源，绝不依赖查询顺序。
- 管理页必须显示每个人的“有效额度、来源、岗位、已使用量、是否超额”，避免管理员只看到配置值而不知道最终结果。

建议提供但不自动套用的岗位额度档位：

| 档位 | 建议额度 | 适用示例 |
| --- | ---: | --- |
| 一线基础 | 1 GiB | 日常只保存少量文档和图片 |
| 标准岗位 | 2 GiB | 当前默认，普通办公岗位 |
| 主管/店长 | 3 GiB | 需要保存门店报表与培训材料 |
| 区域/职能管理 | 5 GiB | 跨组织资料较多 |
| 高资料岗位 | 10 GiB | 经批准的大文件或管理岗位 |

迁移脚本不能按“店长、经理”等中文名称自行猜测档位；必须由管理员按岗位 ID 批量选择后应用。

### 3.4 组织盘额度与公司下属组织预算

每个组织配置两个不同概念：

- `组织盘额度`：该组织自己的盘最多可占用多少。
- `组织树预算`：该公司/组织及其全部下级组织盘额度之和最多可分配多少。

预算校验规则：

- 启用或调高任一组织盘时，检查它所在的所有上级预算。
- 同时检查全局组织盘额度池。
- 超出预算时不允许保存，并返回具体是哪个上级组织、已分配多少、还差多少。
- 调低额度可以低于当前已使用量，但必须二次确认并填写原因；原文件不删除，空间进入“超额”状态，禁止新增文件，允许清理、下载、重命名和移动。

### 3.5 共享组织盘不在第一阶段做“个人占用上限”

本方案第一阶段设置的是：

- 每个人自己的个人盘总额度。
- 每个组织共享盘的总额度。

不把“某个人在共享组织盘内最多上传多少”混入第一阶段，原因是当前 `drive_node` 只有创建人用户名，没有不可变的创建人用户 ID 和成员用量台账。若直接实现，会在人员改名、调动、文件转移、回收站清理时产生错误扣减。

如果业务确认确实需要，第二阶段再新增：

- `drive_node.creator_user_id`。
- `drive_space_member_usage` 共享盘成员用量表。
- 上传时同时原子预占空间额度和成员额度，物理清理时同时释放。
- 文件所有权转移、离职和组织调动的明确归属规则。

## 4. 容量建议与计算方法

### 4.1 不直接假设物理容量

系统目前无法从代码中确认生产存储的真实可用容量，因此不应直接把 2 TiB 写成事实。管理中心必须先配置：

- 物理可用容量 `C`。
- 安全保留比例，建议 20%。
- 可分配容量 `A = C × 80%`。
- 公共盘、个人盘、组织盘三个逻辑额度池。

保存时校验：

```text
公共盘池 + 个人盘池 + 组织盘池 <= 可分配容量 A
```

### 4.2 如果生产可用容量至少为 2 TiB

可以采用以下初始分配：

- 安全保留：20%。
- 公共盘池：约可分配容量的 10%，建议先保留 160 GiB，现有公司公共盘仍保持 100 GiB。
- 个人盘池：约 25%，建议 400 GiB。
- 组织盘池：约 65%，建议约 1.04 TiB。

按当前数据和建议组织额度估算：

- 10 个 `COMPANY × 40 GiB = 400 GiB`。
- 64 个 `STORE × 10 GiB = 640 GiB`。
- 组织盘合计 1,040 GiB。
- 155 人暂按 2 GiB 合计 310 GiB。
- 公司公共盘保持 100 GiB。
- 当前建议逻辑分配合计约 1,450 GiB。
- 预留 20% 后要求物理可用容量至少约 1,812.5 GiB，因此 2 TiB 才有合理余量。

如果真实容量低于该数值，必须先降低组织自动启用范围或岗位额度，不能带着超预算配置上线。

## 5. 数据库设计

新增独立迁移：

- `sql/erp_cloud_drive_organization_quota_20260713.sql`
- `docker/mysql/db/erp_cloud_drive_organization_quota_20260713.sql`

第二个路径仅保持现有部署脚本镜像和测试契约，本次实施不启动 Docker、虚拟机或 Testcontainers。

### 5.1 `drive_personal_quota_policy`

保存全员、岗位和个人策略：

| 字段 | 用途 |
| --- | --- |
| `policy_id` | 主键 |
| `subject_type` | `GLOBAL / POST / USER` |
| `subject_id` | 全员用 0，岗位用 `post_id`，个人用 `user_id` |
| `quota_bytes` | 个人盘总额度 |
| `priority` | 多岗位决策优先级，个人和全员可固定为 0 |
| `expire_time` | 个人临时例外到期时间，可空 |
| `status` | `ACTIVE / DISABLED` |
| `version` | 乐观锁 |
| 通用审计字段 | 创建人、更新人、时间、备注/调整原因 |

约束与索引：

- 唯一键 `(subject_type, subject_id)`，每个对象只有一条当前策略。
- 索引 `(status, expire_time)` 支持有效策略解析。
- `quota_bytes > 0`、类型与 ID 合法性由服务层同时校验，兼容项目可能使用的不同 MySQL 小版本。

### 5.2 `drive_org_type_rule`

保存组织类型的自动建盘规则：

| 字段 | 用途 |
| --- | --- |
| `dept_type` | `GROUP / COMPANY / STORE / WAREHOUSE`，唯一 |
| `auto_enable` | 是否自动启用 |
| `require_active_member` | 是否必须有直属有效成员才自动建盘 |
| `default_quota_bytes` | 新组织默认额度 |
| `status`、`version` | 状态和乐观锁 |
| 通用审计字段 | 变更追踪 |

### 5.3 `drive_org_space_config`

保存每个组织的实际配置：

| 字段 | 用途 |
| --- | --- |
| `config_id` | 主键 |
| `dept_id` | 对应 `sys_dept.dept_id`，唯一 |
| `enabled` | 是否启用组织盘 |
| `quota_bytes` | 该组织盘的实际总额度 |
| `tree_budget_bytes` | 本组织及全部下级组织盘额度总预算，可空 |
| `member_write_mode` | `PERMISSION_ONLY / ALL_DIRECT_MEMBERS / READ_ONLY` |
| `lifecycle_status` | `ACTIVE / READ_ONLY / ARCHIVED` |
| `config_source` | `TYPE_DEFAULT / MANUAL / MIGRATED` |
| `version` | 乐观锁 |
| 通用审计字段 | 变更原因和操作者 |

不直接外键级联删除 `sys_dept`，避免删除组织时连带丢失文件配置；生命周期由业务服务显式处理。

### 5.4 `drive_capacity_config`

保存全局容量池，固定一条主记录：

| 字段 | 用途 |
| --- | --- |
| `config_id` | 固定为 1 |
| `physical_capacity_bytes` | 经运维确认的物理可用容量，可在首次迁移时为空 |
| `reserve_percent` | 建议 20 |
| `public_pool_bytes` | 公司公共盘额度池 |
| `personal_pool_bytes` | 全部有效用户个人盘有效额度之和上限 |
| `organization_pool_bytes` | 全部启用组织盘额度之和上限 |
| `enforcement_mode` | `WARN / BLOCK` |
| `version` | 乐观锁 |
| 通用审计字段 | 变更原因和操作者 |

首次迁移用 `WARN`，真实容量确认、影响预览通过后切换为 `BLOCK`。

### 5.5 `drive_space` 保持为运行时额度账本

不把上传热路径改成每次跨多张策略表计算。采用：

- 策略表保存“为什么是这个额度”。
- `drive_space.quota_bytes` 保存已经解析和物化的有效额度。
- 首次建盘、用户打开空间、上传前、策略保存后都会进行幂等对账。
- 上传仍使用现有 `used_bytes + bytes <= quota_bytes` 条件更新，保留并发安全。

建议给 `drive_space` 增加：

- `quota_source_type`：`GLOBAL / POST / USER / ORG_TYPE / ORG_OVERRIDE / COMPANY`。
- `quota_source_id`：来源对象 ID，可空。
- `quota_synced_time`：最后对账时间。

“是否超额”直接由 `used_bytes > quota_bytes` 计算，不重复存一个容易失真的布尔值。

## 6. 后端实现设计

### 6.1 领域模型与 Mapper

新增文件：

- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DrivePersonalQuotaPolicy.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveOrganizationTypeRule.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveOrganizationSpaceConfig.java`
- `erp-modules/erp-file/src/main/java/com/erp/file/drive/domain/DriveCapacityConfig.java`
- 对应 `mapper/*.java`。
- 对应 `src/main/resources/mapper/drive/*.xml`。

修改：

- `DriveSpace.java` 和 `DriveSpaceMapper.xml` 增加额度来源、按组织集合查询、名称/状态同步和策略物化更新。
- `DriveIdentity.java` 与 `DriveIdentityMapper.xml` 增加 `parentId`、`ancestors`、`deptType`，继续只接受有效用户和有效组织。
- Mapper 参数全部使用绑定变量；组织范围只从后端身份和数据库角色计算。

### 6.2 有效个人额度解析

新增：

- `DriveEffectiveQuotaService.java`
- `DrivePersonalQuotaPolicyService.java`

处理流程：

1. 查询有效个人例外；存在且未过期则直接使用。
2. 查询用户有效岗位及岗位策略，按 `priority desc, quota_bytes desc, policy_id asc` 解析。
3. 没有岗位策略则使用全员默认。
4. 将结果和来源物化到个人 `drive_space`。
5. 用户打开空间和上传前再次轻量校验，确保岗位或个人策略变更后不必重新登录。
6. 修改全员/岗位策略后，按 500 人一批对已经存在的个人盘同步；没有建盘的用户只计入容量影响，首次访问时再建盘。

### 6.3 动态组织盘同步

新增：

- `DriveOrganizationCatalogMapper.java/.xml`
- `DriveOrganizationProvisioningService.java`
- `DriveOrganizationReconcileService.java`
- `DriveOrganizationSyncTask.java`

同步触发点：

- 用户列出空间时：即时同步本人直属组织。
- 管理员保存组织配置时：即时创建或更新目标盘。
- 后台任务：建议每 10 分钟幂等扫描一次组织变化。
- 管理页面提供“立即同步”操作，便于组织调整后人工确认。

生命周期规则：

- 新有效组织：按组织类型规则建立配置；满足自动启用条件后建盘。
- 组织改名：普通列表直接使用实时 `sys_dept.dept_name`，后台同时更新 `drive_space.space_name`。
- 用户调动：旧组织访问在下一次请求失效；新组织立即解析。
- 组织停用：已有盘转 `READ_ONLY`，成员可下载和清理，不能新增；不删除任何对象。
- 组织删除：转 `ARCHIVED`，仅管理员可查，必须人工选择保留、迁移或最终清理。
- 组织合并：必须通过显式迁移操作处理目标盘和重名文件，不能自动拼接目录。

需要修改：

- `DriveSpaceService.java`：从固定最多 3 个空间改为个人盘、公共盘、可访问组织盘列表。
- `DriveAuthorizationService.java`：区分可读状态和可写状态，支持组织数据范围。
- `DriveConstants.java`：增加生命周期、额度来源和审计动作常量。
- `DriveErrorCodes.java`：增加策略冲突、容量池不足、上级预算不足、配置版本冲突等稳定错误码。

### 6.4 组织数据范围

新增 `DriveOrganizationScopeService.java`，按项目现有角色数据范围解析：

- 管理员：全部组织。
- `data_scope=1`：全部有效组织。
- `data_scope=2`：`sys_role_dept` 明确授权组织。
- `data_scope=3`：本组织。
- `data_scope=4`：本组织及 `ancestors` 包含本组织的下级组织。
- `data_scope=5`：共享组织盘场景按本组织处理，不扩大到其他人或其他组织。

普通成员即使没有管理数据范围，也始终可以读自己的直属组织盘。写入默认继续要求 `drive:department:manage`；只有目标组织明确配置为 `ALL_DIRECT_MEMBERS` 时，其直属有效成员才可写，避免本次改造意外放大权限。

### 6.5 容量池与影响预览

新增：

- `DriveCapacityService.java`
- `DriveQuotaImpactService.java`

每次修改配置先计算：

- 受影响用户或组织数量。
- 修改前后逻辑分配总量。
- 对公共、个人、组织三个池的影响。
- 哪些空间修改后会 `used_bytes > quota_bytes`。
- 是否超过任何上级 `tree_budget_bytes`。
- 是否超过全局可分配容量。

管理员确认后提交 `impactHash + version + reason`。后端重新计算并校验哈希；组织、人员或用量已经变化时拒绝旧确认，要求重新预览。

配置应用事务中锁定容量配置和目标策略/组织配置，防止两个管理员同时把同一额度池分配两次。

### 6.6 调低额度的行为

保留现有 `DriveQuotaService.reserve()` 和 `release()`。

新增策略物化专用更新，不复用当前“额度不得低于已使用量”的人工单空间更新限制：

- 调低后原文件、回收站文件和用量都不变。
- 因为 `reserveQuota` 条件不满足，新增文件自然被阻止。
- 允许下载、重命名、移动、删除和清空回收站。
- 页面显示“已超额，需要清理 X GiB”。
- 清理到额度以内后自动恢复上传，无需管理员再次操作。

### 6.7 旧接口兼容

保留：

- `GET /file/drive/spaces`
- `PUT /file/drive/spaces/{spaceId}/quota`

旧额度更新接口不再直接改一列，而是按空间类型转换为：

- 个人盘：创建/更新该用户的个人例外策略。
- 组织盘：创建/更新该组织的手动额度配置。
- 公司公共盘：在公共盘池校验后更新公共盘额度。

这样旧前端和新管理中心同时存在时不会产生两套额度来源。

`DriveSpaceVo` 增加但不暴露内部组织 ID：

- `quotaSourceLabel`，例如“岗位：店长”“个人临时额度”“组织单独配置”。
- `overQuota`。
- `overQuotaBytes`。
- `usagePercent`。
- `canOpenQuotaCenter`。

## 7. 管理接口设计

新增 `DriveAdminController.java`，统一前缀 `/drive/admin`，全部要求：

```text
drive:access + drive:quota:manage
```

接口建议：

| 方法与路径 | 用途 |
| --- | --- |
| `GET /drive/admin/quota/overview` | 容量池、分配量、实际使用量、告警数量 |
| `GET /drive/admin/quota/capacity` | 查询物理容量与三个额度池 |
| `PUT /drive/admin/quota/capacity` | 保存容量配置，带版本和原因 |
| `GET /drive/admin/quota/personal-policies` | 查询全员和岗位策略及受影响人数 |
| `PUT /drive/admin/quota/personal-policies/{subjectType}/{subjectId}` | 保存全员、岗位或个人策略 |
| `DELETE /drive/admin/quota/personal-policies/{subjectType}/{subjectId}` | 删除岗位/个人策略并回落到下一层 |
| `GET /drive/admin/quota/users` | 按姓名、组织、岗位、超额状态查询个人有效额度 |
| `GET /drive/admin/organizations` | 返回有数据范围控制的组织树、盘状态、额度和预算 |
| `PUT /drive/admin/organizations/{deptId}` | 启停、改额度、改组织树预算、归档/恢复 |
| `POST /drive/admin/organizations/batch` | 按组织类型或勾选组织批量配置 |
| `POST /drive/admin/quota/impact` | 预览任一拟议变更的容量和超额影响 |
| `POST /drive/admin/organizations/reconcile` | 手动触发幂等组织同步 |

控制要求：

- 列表分页并限制最大页大小。
- 所有写接口使用乐观锁、影响哈希和调整原因。
- 返回稳定错误码，不向前端暴露 SQL、物理存储键或内部异常。
- 继续使用 `drive_operation_log`，新增容量、岗位策略、个人例外、组织配置和组织同步动作，记录前后摘要但不记录敏感内容。

## 8. 前端交互方案

### 8.1 不改现有动态菜单结构

当前云盘 `9600` 是页面菜单，`9601~9603` 是功能权限。为了避免把现有页面菜单改成目录并影响所有角色路由，本次不新增独立菜单。

在 `erp-ui/src/views/drive/index.vue` 顶部增加“云盘设置”按钮，仅 `drive:quota:manage` 可见，打开全屏抽屉式管理中心。

### 8.2 新增组件

建议新增：

- `erp-ui/src/api/drive/admin.js`
- `erp-ui/src/views/drive/components/quota/DriveQuotaCenterDrawer.vue`
- `erp-ui/src/views/drive/components/quota/DriveCapacityPanel.vue`
- `erp-ui/src/views/drive/components/quota/DriveOrganizationQuotaPanel.vue`
- `erp-ui/src/views/drive/components/quota/DrivePostQuotaPanel.vue`
- `erp-ui/src/views/drive/components/quota/DriveUserQuotaPanel.vue`
- `erp-ui/src/views/drive/components/quota/DriveQuotaImpactDialog.vue`

管理中心包含四个页签：

1. **容量总览**：物理容量、安全保留、三个额度池、逻辑分配、实际使用、70/85/95/100% 告警。
2. **组织盘**：左侧组织树，右侧启用状态、组织盘额度、组织树预算、已使用量、来源和批量设置。
3. **岗位额度**：岗位、人数、当前策略、预计增减额度、优先级；支持按档位批量设置。
4. **个人例外**：按姓名/组织/岗位搜索，显示有效额度来源，支持临时额度及到期时间。

### 8.3 易用性要求

- 所有额度统一以 GiB 输入，后端仍以字节保存；输入最多三位小数并检查安全整数范围。
- 每次保存先显示影响预览，不直接弹一句“是否确定”。
- 影响预览必须包含“多少人变化、总分配增减、多少空间会超额、是否超过预算”。
- 批量设置支持组织类型筛选和勾选，不允许只靠岗位/组织名称字符串匹配。
- 成员写入模式必须用清晰文案展示预计获得写权限的人数，切换到“直属成员可写”前进行影响确认。
- 普通云盘侧边栏显示实际使用/额度；超额时显示红色提示和需要清理的容量。
- 普通用户可以看到“额度来自岗位/个人例外/组织配置”，但看不到内部策略 ID。
- 移动端本期只显示有效额度、来源和超额提示，不提供复杂管理中心。
- 组织盘数量较多的管理者，侧边栏按“直属组织 / 可管理下级组织”分组，并支持搜索，避免一次平铺几十个盘。

## 9. 有序实施步骤与检查点

### 阶段 0：冻结兼容基线

操作：

- 记录当前 `drive_space` 数量、各类型额度和用量总和。
- 记录有效用户、岗位、有效组织及有直属成员组织数量。
- 固定现有 `/file/drive/**` 契约和现有自动化测试结果。
- 确认真实物理可用容量；未确认时只允许 `WARN`，不能开启自动全量建盘。
- 检查并保留工作区已有 OA、HR、库存等无关改动，只修改云盘范围文件。

通过标准：所有基线查询有结果快照，当前测试通过，没有使用虚拟机、Docker 或 Testcontainers。

### 阶段 1：新增迁移与持久化模型

操作：

- 先增加 SQL 源测试，确认迁移幂等、索引和菜单权限不冲突。
- 新增四张配置表和 `drive_space` 三个来源字段。
- 增加领域对象、Mapper、XML 绑定和 Mapper 测试。
- 迁移只新增结构，不改现有节点，不批量创建新空间。

通过标准：迁移可重复执行；现有空间、节点、用量和对象数量完全不变；Mapper 测试通过。

### 阶段 2：个人额度策略与容量计算

操作：

- 实现全员、岗位、个人优先级解析。
- 实现到期回落、多岗位确定性规则、容量池计算和影响预览。
- 实现策略结果到 `drive_space` 的幂等物化。
- 旧单空间额度接口转换为策略写入。

通过标准：无岗位、单岗位、多岗位、个人例外、例外过期、并发更新、额度降低到已使用量以下均有测试。

### 阶段 3：动态组织盘与生命周期

操作：

- 实现组织类型规则、组织配置、直属成员条件和即时/定时同步。
- 实现名称同步、调动切换、停用只读、删除归档。
- 实现组织级成员写入模式，迁移默认保持现有权限行为。
- 实现组织盘额度和所有上级组织树预算校验。
- 实现角色数据范围内的下级组织盘访问。

通过标准：新建、改名、调动、停用、删除、恢复、预算超限和并发启用均有自动化测试；任何流程都不自动删除文件。

### 阶段 4：管理 API 与审计

操作：

- 完成 `/drive/admin/**` 查询、预览、保存、批量和同步接口。
- 加入权限、数据范围、分页、版本、影响哈希和变更原因。
- 扩展操作日志动作和安全摘要。

通过标准：普通用户、组织管理者、全局额度管理员、管理员四类身份分别做正反权限测试；直接猜 ID 不能越权。

### 阶段 5：桌面管理中心与普通用户提示

操作：

- 实现容量、组织、岗位、个人四个页签。
- 接入影响预览、批量配置、超额提示和额度来源。
- 调整组织盘侧边栏分组，保持现有文件浏览、上传、回收站和移动端功能不回退。

通过标准：管理员可在一个页面完成完整配置；普通用户能理解自己的额度；键盘、焦点、错误态和小屏布局通过检查。

### 阶段 6：数据回填与灰度启用

回填规则：

1. 全员默认策略取现有个人盘最常见额度；没有个人盘时用 2 GiB。
2. 已存在个人盘若额度不同于全员默认，回填为个人例外，避免迁移后额度变化。
3. 已存在组织盘回填 `MIGRATED` 配置并保持原额度。
4. 公司公共盘继续保持当前数据库额度。
5. 不自动按岗位名称创建策略。
6. 组织类型规则先写入推荐值但保持自动启用关闭。
7. 管理员确认真实容量和影响预览后，先启用 1 个测试公司/门店，再按组织类型分批启用。

灰度顺序：

- 管理员测试账号。
- 1 个低用量组织。
- 5 个不同类型组织。
- 全部已有成员的 `STORE`。
- 有直属成员的 `COMPANY`。
- `WAREHOUSE` 仅手动启用。

通过标准：每批观察至少一个完整业务验证窗口，额度、权限、用量和存储健康无异常后再扩大。

### 阶段 7：最终回归与交付

自动化命令：

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
mvn -pl erp-modules/erp-file -am test
mvn -pl erp-modules/erp-system -Dtest=CloudDriveMenuSqlSourceTest,SysMenuServiceImplTest,SysUserControllerAuthRoleScopeTest test
mvn -pl erp-gateway -Dtest=FileDriveRouteConfigTest test

cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm test
npm run build:prod

cd /Users/liuxingyu/Desktop/备份/ERP-NEW
git diff --check
```

数据库验证使用本机 MySQL 测试库，不使用虚拟机或容器：

```bash
mysql --defaults-extra-file="$HOME/.my.cnf" ERP_DRIVE_TEST < sql/erp_cloud_drive_20260711.sql
mysql --defaults-extra-file="$HOME/.my.cnf" ERP_DRIVE_TEST < sql/erp_cloud_drive_organization_quota_20260713.sql
```

不得在未确认的生产库直接执行上述示例命令。

真实浏览器验收：

- 岗位额度修改后，用户不重新登录即可在下次打开/上传时看到新额度。
- 个人例外覆盖岗位，过期后自动回落。
- 用户调动后不能再访问旧组织盘，可以访问新组织盘。
- 公司管理者只能看到角色数据范围内的下级组织盘。
- 组织改名即时显示；停用后只读；删除后普通用户不可见且文件仍在。
- 调低到已使用量以下时不删文件、上传被阻止、清理后自动恢复。
- 超过个人池、组织池、公共池或任一上级组织树预算时保存被准确拒绝。
- 桌面与移动现有上传、下载、预览、移动、回收站功能无回退。

## 10. 测试文件规划

后端新增或扩展：

- `DriveEffectiveQuotaServiceTest.java`
- `DrivePersonalQuotaPolicyServiceTest.java`
- `DriveQuotaImpactServiceTest.java`
- `DriveCapacityServiceTest.java`
- `DriveOrganizationProvisioningServiceTest.java`
- `DriveOrganizationReconcileServiceTest.java`
- `DriveOrganizationScopeServiceTest.java`
- `DriveAdminControllerTest.java`
- `DriveSpaceServiceTest.java`
- `DriveAuthorizationServiceTest.java`
- `DriveQuotaServiceTest.java`
- `DriveMapperBindingTest.java`
- `CloudDriveSqlSourceTest.java`

前端新增或扩展：

- `erp-ui/test/cloudDriveQuotaManagement.test.js`
- `erp-ui/test/cloudDriveDesktop.test.js`
- `erp-ui/test/cloudDriveState.test.js`
- `erp-ui/test/mobileCloudDrive.test.js`

必须覆盖的故障注入：

- 两个管理员并发修改同一策略。
- 预览后组织人数或用量发生变化。
- 批量同步中一个组织失败。
- 本地存储不可写或 MinIO 不可用。
- 策略同步时空间正好发生上传。
- 组织树存在停用父节点、删除节点或异常 ancestors。
- 额度值溢出、负数、0、超出物理容量和版本过期。

## 11. 发布开关、兼容与回滚

新增配置开关：

- `drive.quota-policy-enabled=false`
- `drive.organization-sync-enabled=false`
- `drive.organization-sync-cron=0 */10 * * * *`

上线顺序：

1. 先发布加表和兼容代码，两个新开关均关闭。
2. 配置管理中心并完成影响预览。
3. 开启岗位/个人策略，仍保持组织自动同步关闭。
4. 小范围开启组织同步。
5. 最后将容量模式从 `WARN` 切换为 `BLOCK`。

回滚方式：

- 关闭两个新开关即可回到旧的空间读取路径。
- 新表和新增列先保留，不在紧急回滚中删除。
- `drive_space.quota_bytes` 始终保存最后生效额度，旧版本上传校验仍可工作。
- 新增的 `READ_ONLY / ARCHIVED` 对旧版本表现为不可访问，属于保守失败，不会误开放写入。
- 发布前保存 `drive_space`、四张新配置表和菜单权限快照；需要数据回退时按审计前值恢复，不回滚 `drive_node` 或物理对象。
- 当前旧接口保持存在，因此前后端可分别回滚。

## 12. 主要风险与控制

| 风险 | 控制措施 |
| --- | --- |
| 自动给 74 个组织建盘导致逻辑额度暴增 | 容量配置、影响预览、默认关闭自动启用、分批灰度 |
| 岗位调整后已有个人盘仍是旧额度 | 打开/上传前轻量对账，策略保存后批量物化 |
| 多岗位结果不确定 | 显式 priority 和固定排序，界面展示最终来源 |
| 组织调动后还能访问旧盘 | 每次请求使用实时 `sys_user.dept_id`，组织范围后端计算 |
| 降额误删文件 | 只阻止新增，不自动删除，必须二次确认和记录原因 |
| 上级管理员越权访问其他公司 | 复用角色数据范围与 ancestors，增加 IDOR 反向测试 |
| 组织删除造成配置或文件级联丢失 | 不做外键级联删除，转归档并人工处置 |
| 两个管理员同时分完同一容量池 | 配置行锁、乐观锁、影响哈希重新校验 |
| 配置文件与数据库额度来源不一致 | 配置仅作为无策略时的引导默认，数据库策略为运行时权威；修复未使用的 `companyQuota` |
| 工作区已有其他业务改动被覆盖 | 仅编辑云盘相关文件，逐文件检查 diff，不做 reset/checkout |

## 13. 完成定义

只有同时满足以下条件才算完成：

- 新组织、改名、调动、停用和归档流程均能按规则动态生效。
- 全员、岗位、个人三层额度优先级稳定，用户可以看到最终额度来源。
- 每个组织盘有独立额度，公司/集团可以设置下属组织树总预算。
- 三个全局额度池和 20% 安全保留可配置，超预算不能保存。
- 调低额度不删除任何文件，超额空间清理后能自动恢复上传。
- 普通用户、组织管理者、额度管理员和管理员之间不存在越权。
- 原有云盘 API、桌面端、移动端和上传原子额度校验保持兼容。
- SQL 可重复执行，自动化、生产构建、真实浏览器和本机 MySQL 验证全部通过。
- 未使用虚拟机、Docker 或 Testcontainers；未残留测试服务、测试数据或临时开启的功能开关。

## 14. 推荐实施范围

建议立即实施本方案的第一阶段完整包：

1. 动态组织盘。
2. 全员/岗位/个人三层个人盘额度。
3. 每组织额度、组织树预算和全局容量池。
4. 管理中心、影响预览、超额状态和审计。

“共享组织盘内每个上传人的个人贡献上限”作为独立第二阶段，只有业务明确需要后再做成员用量台账，避免为了看似完整而引入错误计费。
