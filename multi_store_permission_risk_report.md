# 多店铺多权限多用户项目问题评审报告

审计日期：2026-06-12  
审计方式：基于当前仓库的静态代码阅读与关键点 grep，不包含运行时渗透测试、压测、完整自动化测试。  
项目范围：后端 Spring Cloud 微服务、库存/OA/system/file 模块、Gateway、Vue 前端、Docker/Nacos 配置与 SQL 脚本。

## 1. 执行摘要

该项目当前更接近“单企业多门店管理系统”，不是严格意义上的 SaaS 多租户系统。核心问题集中在五类：

1. 安全默认值不足：文件服务白名单过大、JWT secret 硬编码、Nacos/MySQL/Redis 暴露与弱配置。
2. 权限模型割裂：`@DataScope`、`Dept-NumId`、`sys_user_shop`、超级管理员判断各自独立，缺少统一授权边界。
3. 门店/仓库隔离不完整：部分接口返回全量组织/仓库树，部分库存/系统查询信任前端 header。
4. 数据一致性薄弱：缺少数据库外键、后端幂等保护不足、库存并发主要依赖悲观锁。
5. 产品架构限制：没有租户模型、总部主数据池、统一审批/审计/通知/财务体系。

总体判断：如果只用于单公司内网、小规模多门店，当前架构可以运行，但需要先补 P0/P1 安全与隔离问题；如果目标是多公司 SaaS、加盟连锁或跨区域总部管控，则现有数据模型和权限模型需要重构。

## 2. P0 高危问题

### P0-1 文件服务被 Gateway 白名单放行，上传/删除接口缺少权限注解

证据：

- `erp-gateway/src/main/resources/bootstrap.yml:116-127` 将 `/file/upload,/file/delete` 和 `/file/**` 路由到 file 服务。
- `erp-gateway/src/main/resources/bootstrap.yml:143-149` 将 `/file/**` 加入认证白名单。
- `erp-modules/erp-file/src/main/java/com/erp/file/controller/SysFileController.java:32-55` 的 `upload`、`delete` 没有 `@RequiresPermissions`。

影响：

- 如果生产配置沿用该白名单，攻击者可能无需登录即可上传文件或尝试删除文件。
- 文件接口通常是高风险入口，可能引发垃圾文件写入、存储耗尽、恶意文件托管、越权删除等问题。

建议：

1. 白名单只保留必要的静态访问路径，不应覆盖上传和删除。
2. `upload`、`delete` 增加明确权限，例如 `file:upload`、`file:delete`。
3. 上传接口增加文件类型、大小、后缀、MIME、内容扫描和业务归属校验。
4. 删除接口不能只靠 URL，应按文件 ID + 创建人/业务归属校验。

### P0-2 JWT secret 硬编码，所有环境共用风险高

证据：

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/TokenConstants.java:18`：`SECRET = "abcdefghijklmnopqrstuvwxyz"`。
- `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java:18-40` 直接使用该 secret 创建和解析 token。

影响：

- 源码泄露或被多人共享后，token 签名密钥即泄露。
- 不同环境共用密钥会造成开发、测试、生产之间的安全边界失效。

建议：

1. 将 JWT secret 放到环境变量、Nacos 加密配置或密钥管理系统。
2. 每个环境使用不同 secret。
3. 支持密钥轮换，保留短时间多 key 验签窗口。
4. 重新评估 token 过期时间和刷新机制。

### P0-3 超级管理员和超级权限路径过多

证据：

- `erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/UserConstants.java:84-87`：`userId == 1` 即管理员。
- `erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthLogic.java:28-32`：`*:*:*` 和 `admin` 是超级权限/超级角色标识。
- `erp-common/erp-common-security/src/main/java/com/erp/common/security/auth/AuthLogic.java:355-371`：拥有 `*:*:*` 或 `admin` 可匹配所有权限/角色。
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMobileController.java:56`：移动端 profile 中 `SecurityUtils.isAdmin()` 或 `*:*:*` 都视为 admin。

影响：

- 只要用户 ID、角色标识或权限字符串任一处被错误配置，就可能获得全局权限。
- 管理员不可完全通过业务配置撤销，审计和治理困难。
- 超级管理员绕过数据范围过滤和门店校验，容易掩盖真实权限缺陷。

建议：

1. 移除 `userId == 1` 作为业务权限判断。
2. 保留一个统一的“平台管理员”角色，但必须可配置、可撤销、可审计。
3. 限制 `*:*:*` 只能授予极少数平台角色，并增加变更审计。
4. 所有 admin bypass 逻辑集中到一个权限服务中，禁止散落在各模块。
5. 管理员判断改为角色驱动，例如由 `sys_role` 中受控角色标识或独立管理员授权表决定，禁止依赖自增主键 ID。

### P0-4 基础设施默认配置不适合生产

证据：

- `docker/nacos/conf/application.properties:23-27`：Nacos auth disabled，且存在固定 secret key。
- `docker/docker-compose.yml:16-19` 暴露 Nacos 端口。
- `docker/docker-compose.yml:27-28` 暴露 MySQL 3306。
- `docker/docker-compose.yml:41-43` MySQL root password 为 `password`。
- `docker/docker-compose.yml:49-50` 暴露 Redis 6379。
- `docker/docker-compose.yml:76-99` 暴露 Gateway 和多个后端服务端口。

影响：

- 如果该 compose 或类似配置用于服务器，配置中心、数据库、缓存、微服务均可能被直接访问。
- 配置中心一旦被访问，可能泄露所有服务配置和密钥。

建议：

1. 生产环境关闭 Nacos 匿名访问，启用强口令和服务端身份认证。
2. MySQL、Redis、Nacos、内部微服务只暴露到内网网络，不直接对公网开放。
3. 默认密码和 token 全部改为环境注入。
4. Redis 开启认证和网络隔离。

### P0-5 仓库列表接口返回范围过大，组织树授权边界需要持续校验

证据：

- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:53-66`：`/shop-tree`、`/warehouse-list` 不要求 `system:dept:list` 权限。
- `erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml:62-76`：查询启用仓库和全量店铺/仓库树。
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMobileController.java:68-74`：移动端仓库 options 使用 `selectShopTree(new SysDept())`。
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:343-345`：`selectShopTree()` 实际委托 `selectAuthorizedShopTree(SecurityUtils.getUserId(), SecurityUtils.isAdmin())`。
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java:74-85`：非管理员按 `sys_user_shop` 授权门店过滤，管理员返回全量树。

影响：

- `/shop-tree` 当前在 service 层已经按用户授权过滤，不能简单定性为全量泄露。
- `/warehouse-list` 当前直接调用 `selectWarehouseList()`，没有看到用户门店授权校验，普通用户可能看到超出授权范围的仓库。
- 移动端仓库 options 依赖授权树生成选项，当前风险低于 `/warehouse-list`，但仍建议保留回归测试，防止后续改动绕过 service 层授权。

建议：

1. `/warehouse-list` 必须改为基于当前用户授权门店/仓库过滤。
2. `/shop-tree` 保持复用 `selectAuthorizedShopTree()`，并增加测试覆盖普通用户、管理员、无授权用户三种情况。
3. 对移动端和桌面端统一使用同一个 scope 过滤服务，避免新增接口绕过授权树。

### P0-6 库存 transferSource 查询可能越权查看任意仓库库存

证据：

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java:335-356`：`applyTransferSourceScope` 只校验当前选择的是门店、source 是仓库，没有校验该门店是否可向该仓库要货或用户是否有权看该仓库。

影响：

- 用户只要知道仓库 ID，就可能查询非授权仓库库存。
- 库存数量、预警状态、仓库结构属于敏感经营数据。

建议：

1. source 仓库必须在当前门店可见/可要货范围内。
2. 增加 `store -> warehouse` 授权关系或复用现有调拨/供货关系。
3. 所有库存查询统一走同一个库存可见性校验函数。

## 3. P1 重要问题

### P1-1 `@DataScope` 与 `Dept-NumId` 是两套互不联动的权限体系

证据：

- `erp-common/erp-common-datascope/src/main/java/com/erp/common/datascope/aspect/DataScopeAspect.java:42-55`：`@DataScope` 基于角色数据范围过滤，管理员跳过。
- `rg "@DataScope"` 结果显示主要使用在 system 模块的用户、部门、角色服务。
- 库存模块大量使用 `Dept-NumId` 和 `InvBaseService` 手工校验，例如 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvBaseService.java:19-72`。

影响：

- system 角色中配置“本部门/自定义部门数据权限”，不会自然限制库存、OA 等业务模块。
- 业务模块依赖开发者手写过滤，一旦漏写就越权。

建议：

1. 将用户授权门店、角色数据范围、业务 scope 合并为统一授权模型。
2. 设计统一 `ShopScopeService`，所有模块通过它获取可见门店/仓库集合。
3. 禁止 controller/service 直接信任前端传入的 `Dept-NumId`。

### P1-2 Gateway 只校验 token，URL 权限完全依赖下游服务

证据：

- `erp-gateway/src/main/java/com/erp/gateway/filter/AuthFilter.java:48-83`：只校验白名单、JWT、Redis 登录态，并设置用户 header，没有校验 URL 对应权限。

影响：

- 如果下游 controller 漏注解，或者某个微服务未正确引入 security AOP，该服务接口会缺少业务权限保护。
- 与移动端 controller 缺少注解、文件服务白名单问题叠加后，风险更明显。

建议：

1. 保留下游注解鉴权，同时在 Gateway 增加基础的路由权限兜底。
2. 对所有 controller 做权限注解扫描，CI 中阻止新增未授权敏感接口。
3. 白名单必须集中审查，禁止通配覆盖业务写接口。

### P1-3 移动端 Controller 缺少声明式权限注解

证据：

- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMobileController.java:46-74`：`/profile`、`/options/warehouses` 没有 `@RequiresPermissions`。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvMobileController.java:15-34`：`/mobile/options/{type}`、`/mobile/workbench/summary` 没有 `@RequiresPermissions`。

影响：

- 目前 Gateway 仍会校验 token，部分 service 也会做门店校验，所以不是匿名裸奔。
- 但新增移动端接口时，开发者容易沿用“无注解”模式，逐步形成权限黑洞。

建议：

1. 对移动端接口补充最小权限注解。
2. 对通用 options 接口按 type 做权限映射。
3. 对工作台 summary 明确 required permission 或至少 required login + shop scope。

### P1-4 system 用户/角色查询信任 `Dept-NumId` header

证据：

- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:103-113`：将 header 中 `Dept-NumId` 直接写入 `user.deptId`。
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:200-210`：相同逻辑。

影响：

- 如果下游查询没有再次验证 `sys_user_shop`，用户可通过改 header 查询其他部门用户或角色相关数据。
- system 模块和门店授权关系之间边界不清。

建议：

1. 设置 deptId 前先验证当前用户是否有权访问该 dept。
2. 不要在 controller 内直接信任 header，应通过统一上下文解析器返回已验证的 selectedDept。
3. system 查询也要接入门店授权模型。

### P1-5 调拨审批规则、候选人和门店范围没有统一边界

证据：

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java:31-68`：审批规则增删改查只有权限码，没有门店上下文约束。
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml:36-49`：规则列表没有 shop scope。
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml:57-78`：匹配规则可命中全局、区域、片区规则。
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalCandidateMapper.xml:5-14`：候选人按 `sys_user.dept_id + post_code` 查，不按 `sys_user_shop` 查。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java:161-169`：审批方法接收 `selectedShopDeptId`，但主审批校验没有使用它。

影响：

- 审批规则容易成为全局配置，缺少门店/公司归属。
- 候选人模型和多店授权模型不一致，可能导致跨店审批或审批人漏选。

建议：

1. 审批规则增加归属范围：租户/公司/区域/门店。
2. 候选人计算基于 `sys_user_shop` 或统一授权关系，而不是只看用户主部门。
3. 审批操作校验当前 selectedDept 是否与调拨单 from/to 范围匹配。

### P1-6 前端门店上下文存在跨 Tab 冲突

证据：

- `erp-ui/src/utils/shopContext.js:1-18`：`selected_dept_id/name/type` 存在 `localStorage`。
- `erp-ui/src/utils/shopContext.js:46-61`：`selected_dept_validated` 存在 `sessionStorage`。
- `erp-ui/src/utils/request.js:36-41`：每个请求从 `localStorage` 读取 `Dept-NumId` header。

影响：

- Tab A 选择门店 A，Tab B 选择门店 B 后，Tab A 继续操作会带上 B 的 `Dept-NumId`。
- 用户界面可能显示 A 店上下文，但实际请求操作 B 店。

建议：

1. 门店上下文统一使用 `sessionStorage`，实现每个 Tab 独立。
2. 或使用 BroadcastChannel 明确同步所有 Tab，并在 UI 上强制刷新上下文。
3. 后端操作日志记录 selectedDept，便于追查。

### P1-7 后端幂等保护不足，前端防重复提交只能防单 Tab

证据：

- `erp-ui/src/utils/request.js:62-75`：只在 session cache 中保存最后一次请求。

影响：

- 多 Tab、刷新重试、网络抖动、移动端重复点击等场景仍可重复提交。
- 采购、销售、调拨、盘点等写操作可能产生重复单据或重复库存变更。

建议：

1. 关键写接口增加后端幂等键，例如 `Idempotency-Key`。
2. 单据创建使用业务唯一号 + 唯一索引兜底。
3. 库存变更必须保证“单据状态流转 + 库存流水”原子性。

### P1-8 库存并发主要依赖悲观锁，缺少乐观锁/版本字段

证据：

- 库存相关 mapper 中多处 `for update`，例如 `InvStockMapper.xml:149,164`。
- `rg "@Version"` 未发现乐观锁注解。

影响：

- 悲观锁能保证一致性，但高并发下容易造成锁等待、慢事务和连接池压力。
- 复杂调拨/退货/盘点流程如果事务过长，会放大阻塞。

建议：

1. 核心库存表增加 `version` 字段或采用条件更新：`where quantity >= ? and version = ?`。
2. 对高频扣减使用乐观锁 + 有限重试。
3. 保留必要的 `for update`，但缩短事务范围。

### P1-9 数据库缺少显式外键约束

证据：

- 在 `sql`、`erp-modules`、`erp-common` 范围内未发现业务 SQL 的 `FOREIGN KEY` / `REFERENCES` 约束定义。
- `sql/tea_management_import_20260608.sql:6-8` 存在对商品、分类、供应商的全表 `DELETE`。

影响：

- 应用层漏校验或导入脚本误操作会制造孤儿数据。
- 业务数据删除后，库存流水、调拨明细、订单明细可能引用不存在的主记录。

建议：

1. 至少为关键业务链路增加 FK 或软 FK 校验：订单-明细、调拨-发货、库存-商品、流水-库存。
2. 历史数据先清洗，再分阶段加约束。
3. 数据导入脚本禁止直接全表 delete，改成临时表校验 + 事务 + 备份 + 灰度执行。

## 4. P2 架构和维护性问题

### P2-1 当前不是严格 SaaS 多租户架构

证据：

- `rg "tenant_id|tenantId"` 在主要业务代码和 SQL 中未发现租户字段。
- 现有隔离主要依赖 `shopDeptId`、`Dept-NumId` 和用户门店授权。

影响：

- 不支持多公司之间的天然数据隔离。
- 不支持租户级配置、租户级运维、租户级备份恢复。
- 如果未来做 SaaS，需要较大规模重构。

建议：

1. 明确产品定位：单公司多门店，还是多租户 SaaS。
2. 如果是 SaaS，引入 `tenant_id`，并在所有业务表、查询、缓存 key、消息、文件路径中贯穿。
3. 管理员也要区分平台管理员、租户管理员、门店管理员。

### P2-2 商品、客户、供应商没有总部共享主数据池

证据：

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvProduct.java:101`：商品有 `shopDeptId`。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvCustomer.java:45`：客户有 `shopDeptId`。
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSupplier.java:39`：供应商有 `shopDeptId`。
- 对应 mapper 中大量按 `shop_dept_id` 过滤。

影响：

- 同一商品、客户、供应商跨多店使用时需要重复建档。
- 商品编码、价格、供应商资料、客户资料容易不一致。
- 总部难以统一维护基础资料。

建议：

1. 引入总部主数据表：`product_master`、`customer_master`、`supplier_master`。
2. 门店表只维护门店差异：价格、上下架、库存、门店客户标签等。
3. 支持总部共享、区域共享、门店私有三种范围。

### P2-3 移动端和桌面端形成两套平行前端

现状：

- 桌面端：动态菜单 + 独立业务页面。
- 移动端：硬编码路由 + 通用 feature 页面 + 独立配置和服务层。

影响：

- 新增功能需要同时维护桌面端和移动端。
- 移动端通用引擎文件过大后，调试和权限控制都会变难。

建议：

1. 抽出共用业务 schema 和 API permission mapping。
2. 移动端不要用一个超大通用页面承载所有业务。
3. 权限、字段、表单、列表配置尽量由后端或共享配置生成。

### P2-4 OA 与库存模块重复实现门店 scope 校验

现状：

- 库存模块有 `InvBaseService`。
- OA 模块多个 service 中存在类似的门店校验逻辑。

影响：

- 校验规则容易不一致。
- 后续修改授权模型时，需要多处同步修改。

建议：

1. 建立公共的 `ShopScopeService` 或 `DeptScopeService`。
2. OA、库存、system 都只调用统一服务。
3. 将“选择门店是否合法”“用户可见哪些门店”“仓库是否可见”分成明确接口。

### P2-5 重复文件和死代码风险

证据：

- `erp-ui/src/views/system/salary/index.vue`
- `erp-ui/src/views/system/salary/index 2.vue`

影响：

- 旧文件容易误改、误引用、误打包。
- 后续开发者无法判断哪个页面是主版本。

建议：

1. 确认 `index 2.vue` 未被路由或动态 import 使用后删除。
2. system API 层未使用 export 需要用引用扫描逐个确认，不能直接批量删除。

### P2-6 前端权限指令只是界面隐藏，不能作为安全边界

证据：

- `erp-ui/src/directive/permission/hasPermi.js:17-23`：`v-hasPermi` 只判断权限数组并移除 DOM。
- `erp-ui/src/directive/permission/hasRole.js:17-23`：`v-hasRole` 只判断角色数组并移除 DOM。

影响：

- 前端权限能改善用户体验，但不能阻止用户通过开发者工具、直接调用 API、构造请求等方式访问后端。
- 如果后端 controller 漏权限注解，前端隐藏按钮无法形成有效保护。

建议：

1. 前端权限仅用于菜单和按钮展示。
2. 所有敏感接口必须以后端权限注解、门店 scope 校验、审计日志作为真实安全边界。
3. CI 增加 controller 权限注解扫描，避免依赖前端隐藏按钮。

## 5. P3 产品能力缺口

这些问题不一定是代码漏洞，但会影响项目向企业级 ERP、连锁总部、SaaS 平台演进。

### P3-1 审批能力割裂

校正说明：

- “无审批流引擎”这个说法不准确。OA 模块引入了 Flowable，`erp-modules/erp-oa/pom.xml:18-76` 可以确认，且存在 `purchase-approval.bpmn20.xml`。
- 真正的问题是审批体系不统一：OA 采购走 Flowable，库存调拨走自定义审批规则，其他采购退货、销售退货、盘点等流程覆盖不足。

建议：

1. 统一审批入口、审批任务、审批日志、审批配置。
2. 明确哪些单据必须审批，哪些可配置审批。
3. 将库存调拨审批迁移或适配到统一工作流模型。

### P3-2 审计覆盖不一致

校正说明：

- “业务单据无操作审计”不能绝对化。库存调拨存在 `InvTransferStatusLog` 和审批日志相关表。
- 真实问题是审计覆盖不一致，很多单据缺少统一状态流转日志和字段级变更记录。

建议：

1. 建立统一业务审计表：单据 ID、单据类型、动作、前状态、后状态、操作人、时间、备注、快照。
2. 关键字段变更记录前后值。
3. 审计日志不可被普通业务删除。

### P3-3 财务能力不足

现状风险：

- 缺少应收、应付、收支流水、资金账户。
- 销售收入和库存成本没有形成完整利润核算。
- 仪表盘图表不等于 BI。

建议：

1. 增加应收/应付模块。
2. 单据与资金流水关联。
3. 建立门店、商品、客户、供应商维度利润报表。

### P3-4 消息通知体系不足

现状风险：

- 公告偏被动查看。
- 审批、库存预警、调拨、采购到货等没有统一消息中心。

建议：

1. 建立站内信/待办中心。
2. 支持企业微信、钉钉、邮件、短信等可插拔通知。
3. 业务事件统一发布，通知订阅按角色/门店配置。

### P3-5 批量操作和运营效率不足

现状风险：

- 多数列表缺少批量审批、批量确认、批量删除、批量导入校验等能力。
- 多门店场景下，单条操作会降低运营效率。

建议：

1. 高价值列表优先补批量操作。
2. 批量操作必须配合权限、幂等、审计和失败明细。

## 6. 外部报告中需要修正的表述

1. “无真正多租户”  
   这不是绝对 bug。如果产品定位是单企业多门店，这是当前架构选择；如果目标是 SaaS 多公司，则是根本架构缺陷。

2. “商品/客户/供应商无法跨门店共享”  
   结论成立，但属于产品模型限制。是否要改取决于是否需要总部统一主数据。

3. “新增移动端 Controller 无任何权限注解”  
   结论成立，但不能等同于匿名访问。Gateway 仍会校验 token，部分 service 有门店校验。风险在于缺少声明式授权和后续扩展容易漏防护。

4. “Gateway 仅验证 Token 不验证权限”  
   结论成立，但这是许多微服务架构常见设计。问题在于当前下游注解不完整，且存在白名单过大。

5. “无审批流引擎”  
   不准确。OA 模块存在 Flowable。准确说法是审批能力割裂、覆盖不完整。

6. “业务单据无操作审计”  
   过于绝对。调拨有状态/审批日志。准确说法是统一审计体系缺失，覆盖不一致。

7. “system API 层 11 个未使用 export 死代码”  
   本次未逐个确认。应通过引用扫描、构建、页面回归后再删除。

## 7. 建议整改路线

### 第一阶段：先止血，处理 P0

1. 调整 Gateway 文件白名单，保护上传/删除接口。
2. 移除 JWT 硬编码 secret，改为环境/配置中心注入。
3. 生产 Docker/Nacos/MySQL/Redis 改为安全默认配置。
4. 修复 `/warehouse-list` 仓库列表越权返回，并为 `/shop-tree` 授权过滤增加回归测试。
5. 修复库存 transferSource 仓库越权查询。
6. 收敛超级管理员判断，改为角色驱动，减少 bypass 路径。

### 第二阶段：统一权限和门店隔离

1. 建立统一 `ShopScopeService`。
2. 所有 controller 从统一上下文读取已验证的 selectedDept。
3. system、inventory、OA 统一接入 `sys_user_shop` / 角色数据范围。
4. 移动端补权限注解和 type-to-permission 映射。

### 第三阶段：补数据一致性

1. 核心业务表增加唯一索引、软外键或真实外键。
2. 单据创建和库存变更增加后端幂等。
3. 库存扣减引入乐观锁或条件更新。
4. 导入脚本改为临时表校验和可回滚流程。

### 第四阶段：产品架构升级

1. 明确是否要做 SaaS，多租户则引入 `tenant_id`。
2. 引入总部主数据池，门店只维护差异数据。
3. 统一审批、统一审计、统一消息中心。
4. 增加财务、BI、批量操作能力。

## 8. 优先级总表

| 编号 | 问题 | 优先级 | 类型 |
|---|---|---:|---|
| P0-1 | 文件服务白名单 + 上传/删除无权限 | P0 | 安全 |
| P0-2 | JWT secret 硬编码 | P0 | 安全 |
| P0-3 | 超级管理员/超级权限路径过多 | P0 | 权限 |
| P0-4 | Nacos/MySQL/Redis/Docker 默认配置不安全 | P0 | 运维安全 |
| P0-5 | 仓库列表范围过大，组织树授权需测试兜底 | P0 | 数据隔离 |
| P0-6 | transferSource 可疑仓库库存越权 | P0 | 数据隔离 |
| P1-1 | `@DataScope` 与 `Dept-NumId` 割裂 | P1 | 架构 |
| P1-2 | Gateway 无 URL 权限兜底 | P1 | 权限 |
| P1-3 | 移动端接口缺权限注解 | P1 | 权限 |
| P1-4 | system 查询信任 header | P1 | 数据隔离 |
| P1-5 | 调拨审批范围/候选人不统一 | P1 | 业务权限 |
| P1-6 | 前端门店上下文跨 Tab 冲突 | P1 | 业务正确性 |
| P1-7 | 后端幂等不足 | P1 | 数据一致性 |
| P1-8 | 库存并发主要依赖悲观锁 | P1 | 性能/一致性 |
| P1-9 | 缺少外键约束 | P1 | 数据完整性 |
| P2-1 | 非 SaaS 多租户 | P2 | 架构 |
| P2-2 | 无总部主数据共享池 | P2 | 产品模型 |
| P2-3 | 移动端/桌面端两套体系 | P2 | 维护性 |
| P2-4 | OA/库存重复 scope 校验 | P2 | 维护性 |
| P2-5 | 重复文件和潜在死代码 | P2 | 维护性 |
| P2-6 | 前端权限指令仅 DOM 隐藏 | P2 | 权限边界 |
| P3-1 | 审批能力割裂 | P3 | 产品能力 |
| P3-2 | 审计覆盖不一致 | P3 | 产品能力 |
| P3-3 | 财务能力不足 | P3 | 产品能力 |
| P3-4 | 消息通知体系不足 | P3 | 产品能力 |
| P3-5 | 批量操作不足 | P3 | 产品能力 |
