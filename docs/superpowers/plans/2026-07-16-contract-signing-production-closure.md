# 合同签约生产化收口与后续演进详细实施计划

> 日期：2026-07-16  
> 当前分支：`codex/erp-product-optimization`  
> 代码基线：`b694659d` 及当前未提交工作区  
> 首要目标：先交付“人工签约可正式上线”，再建设自动发送、统一合同中心和运营治理  
> 自动发送策略：本计划 Release A 全程关闭；未通过影子门禁前不得开启

## 一、结论与实施边界

当前项目已经具备入职、转正、调岗、续签、离职五场景，以及草稿、阅读、首次手写签名、公司与印章选择、最终确认、文件证据和验真等主体能力。但它仍不能直接按“生产级已完善”放量。

本次以当前代码、迁移、前端和本机 `BossERP_NEW` 只读回查为准，生产化阻断按优先级为：

| 优先级 | 当前事实 | 影响 |
| --- | --- | --- |
| P0 | 签约中心与历史人事迁移复用了 `sys_menu` 4600～4611；当前库中 4601～4604 已被人事菜单覆盖 | 唯一 HR 角色实际缺少签约查询、重新校验、发送、公司盖章等关键权限；管理员账号会掩盖该问题 |
| P0 | 模板和不可变方案版本保存了签名/印章位置，但签约包文件没有冻结该快照，两次 PDF 生成均传入空位置 | 生产实际总走追加签署页；最终化还会把员工签名复用到所有员工可见文件 |
| P0 | `signDeadlineDays` 只被保存到方案版本，没有在发送时写入任务和签约包 | “即将逾期”指标有模型但没有可靠事实；无法产生真实过期状态 |
| P0 | 任务枚举、指标、待办和通知幂等键包含 `REFUSED/EXPIRED`，但没有员工拒签接口、过期扫描器和签约包状态生产者 | 页面只能展示未来占位文案，异常流程不能闭环 |
| P0 | 通知 Outbox 和调度器已存在，但草稿缺资料、首次签名待 HR、最终合同待员工确认、提醒、拒签、过期等事件未接入 | 关键责任人不知道何时需要处理，流程依赖人工盯页面 |
| P0 | 当前没有非管理员专用 HR + 测试员工下的完整真实 UAT | 单元测试通过不能证明权限、文件、通知、浏览器和数据库组合路径可上线 |
| P1 | 签约相关实现分布在大量未提交和未跟踪文件中 | 当前工作区不能直接作为可复现发布候选 |
| P1 | `erp-ui/test/newBusinessMigrationRelease.test 2.js` 是旧版 Finder 副本，当前全量测试入口会主动拒绝该文件 | 签约聚焦测试可通过，但 `npm test` 仍不是绿色基线 |
| P1 | 自动发送、统一合同中心和运营治理计划文件存在，但关键实现文件仍不存在 | 这些能力应在人工闭环发布后独立实施和灰度 |

Release A 只解决人工生产闭环。它不包含自动发送、不迁移旧劳动合同、不在本轮把 Vue 2 整体升级，也不把技术验真等同于法务对签署效力的认定。

## 二、目标状态与里程碑

| 里程碑 | 交付结果 | 预计工程量 | 外部等待 |
| --- | --- | --- | --- |
| G0 决策与基线封板 | 范围、权限、签章策略、法务定位和发布候选可追溯 | 1～2 人日 | HR/法务确认 |
| Release A 人工生产闭环 | 专用 HR 可完成五场景人工签约；拒签、过期、通知和验真闭环 | 8～13 人日 | 模板法审、真实 UAT |
| Release B 自动发送 | 入职/续签影子判断与小范围自动发送 | 6～8 人日 | 至少 7 个完整自然日影子观察 |
| Release C 统一合同中心 | 新签约包与旧劳动合同统一只读查询、下载、验真 | 5～8 人日 | 数据量与权限 UAT |
| Release D 运营治理 | 指标、异常队列、完整性抽检、备份恢复和治理看板 | 5～7 人日 | 观察窗口与恢复演练 |

工程量是当前代码基线下的顺序估算，不包含法务修改合同正文、生产审批等待和外部电子签服务采购。

依赖顺序固定为：

```text
G0 基线与决策
  └─ A1 菜单/权限修复
      ├─ A2 截止、拒签、过期
      ├─ A3 签章位置与文件策略
      └─ A4 通知闭环
          └─ A5 前端人工闭环
              └─ A6 真实 UAT
                  └─ A7 Release A 发布
                      ├─ Release B 自动发送
                      └─ Release C 统一合同中心
                          └─ Release D 运营治理
```

## 三、跨阶段强制约束

- [ ] 当前工作树已有大量用户改动；禁止 `git reset --hard`、`git clean`、覆盖回退和无差别格式化。
- [ ] 每个迁移同时维护 `sql/` 与 `docker/mysql/db/` 两份同名文件，并以 `cmp` 做字节一致性门禁。
- [ ] 已执行的历史迁移不回改语义；生产修复使用 2026-07-16 前向迁移，历史脚本只作为问题证据。
- [ ] 所有签约状态变化使用“当前状态 + version”条件更新；外部可重试动作必须携带 `requestId`。
- [ ] 已发送文件、首次签名、最终文件、印章快照、证书和事件链只追加，不原地覆盖。
- [ ] 通知失败不能回滚签约业务事务；通知通过 Outbox 重试并保留 DEAD 记录。
- [ ] 推送标题、正文、路由和运维日志不得包含身份证号、手机号、薪资、签名图片、合同正文或原始文件哈希。
- [ ] 所有自动化配置缺失时按 `false`；Release A 发布包不得包含开启自动发送的 SQL 或配置。
- [ ] 技术证据权限继续与业务操作权限隔离；技术审计账号不得因查看证据获得签约写操作。
- [ ] 首轮 UAT 只使用隔离测试员工、测试模板和测试方案，不使用真实员工制造合同动作。

## 四、G0：决策冻结与可复现基线

### Task G0-1：冻结签约发布范围

**产物：**

- Create: `docs/releases/20260716-contract-signing-release-scope.md`
- Create: `scripts/contract/verify_contract_signing_scope.py`
- Create: `scripts/contract/test_verify_contract_signing_scope.py`

- [ ] 记录当前签约相关 modified/deleted/untracked 文件、SHA-256、所属阶段和是否进入 Release A。
- [ ] 将未跟踪的公司印章组件、签约工具函数、测试和迁移逐项纳入范围；不得从 HEAD 误判其不存在。
- [ ] 对比 `newBusinessMigrationRelease.test 2.js` 与当前正式测试；把仍有效的旧断言并入正式文件后移除副本，不能直接通过放宽重复文件门禁绕过。
- [ ] Release A 使用显式 allowlist，只包含 OA/System 必需类、前端签约路径、双份迁移、验证脚本和运行手册。
- [ ] 制品、备份、员工数据、签名图片、上传目录、数据库 dump 和测试凭据全部排除。
- [ ] 基线封板后每个任务只暂存自己的 allowlist 文件，不能把其他业务域一起提交。

### Task G0-2：冻结四项业务决策

**产物：**

- Create: `docs/decisions/2026-07-16-contract-signing-release-decisions.md`

- [ ] 法务明确当前能力定位：内部确认凭证，还是需要满足更高等级电子签署要求。技术团队不自行宣称法律效力。
- [ ] HR/法务逐模板确认：是否需员工签名、是否需公司印章、签名/印章采用正文坐标还是追加确认页。
- [ ] 明确最终确认阶段是否允许拒签；本计划推荐允许，并保留首次签名与最终拒签两段不可变证据。
- [ ] 明确拒签/过期后的处理：旧版本永久终态，只能创建有来源关联的新任务/新版本，禁止把终态原地改回待签。
- [ ] 生产主入口固定为生命周期任务生成的签约包；自由新建/按岗位批量建包默认关闭，仅保留受控应急权限。
- [ ] 确认生产数据库版本、专用合同 HR、法律主体、部门绑定、印章有效期和法务审批人。

**G0 完成门：** 范围清单可复验，四项决策有责任人和结论，签约专项与项目总门禁均为绿色，才进入代码修改。

## 五、Release A：人工签约生产闭环

### Task A1：前向迁移签约菜单并重建唯一 HR 权限（P0）

**Files:**

- Create: `sql/erp_oa_sign_menu_permission_repair_20260716.sql`
- Create: `docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`
- Modify: `erp-ui/test/signTaskCenter.test.js`
- Create: `scripts/contract/verify_menu_id_ownership.py`
- Create: `scripts/contract/test_verify_menu_id_ownership.py`
- Create: `scripts/contract/menu_id_conflicts_20260716.json`

预留并由迁移执行前再次检查的签约专属范围为 9650～9669；当前仓库和 `BossERP_NEW` 均未使用该范围。建议固定映射：

| ID | 权限/用途 | 唯一 HR 默认授权 |
| --- | --- | --- |
| 9650 | 合同签约中心 / `oa:signTask:list` | 是 |
| 9651 | `oa:signTask:query` | 是 |
| 9652 | `oa:signTask:revalidate` | 是 |
| 9653 | `oa:signTask:send` | 是 |
| 9654 | `oa:signTask:retry` | 是 |
| 9655 | `oa:signTask:cancel` | 是 |
| 9656 | `oa:signTask:technicalEvidence` | 否，独立授权 |
| 9657 | `oa:signPackage:list` | 是 |
| 9658 | `oa:signPackage:query` | 是 |
| 9659 | `oa:signPackage:add` | 是；A5 拆分任务草稿权限后再收紧为应急授权 |
| 9660 | `oa:signPackage:send` | 是 |
| 9661 | `oa:signPackage:void` | 是 |
| 9662 | `oa:signPackage:template` | 是，仅模板/方案 |
| 9663 | `oa:signTask:remind` | 是 |
| 9664 | `oa:signTask:resolveRefusal` | 是 |
| 9665 | `oa:signTask:resolveExpiry` | 是 |
| 9666 | `oa:signCompany:list` | 是 |
| 9667 | `oa:signCompany:edit` | 按上线职责独立授权 |
| 9668 | `oa:signSeal:list` | 是 |
| 9669 | `oa:signSeal:edit` | 按上线职责独立授权 |

- [ ] 前向迁移先检查 9650～9669；任一 ID 已被不同业务占用必须 `SIGNAL` 失败，禁止静默覆盖。
- [ ] 新建签约根菜单和权限节点，不复用 4600～4611，也不改写当前人事菜单。
- [ ] 仅当旧 4600 节点的 component/perms 明确属于签约中心时将其停用；不能按数字范围删除人事节点。
- [ ] 把原来确实拥有签约入口的角色映射到新根菜单；缺失业务权限只为托管的唯一 HR 角色按白名单补齐，不能借迁移扩大其他角色权限。
- [ ] 重新定义 `sync_sign_hr_permissions()`，只管理签约专属节点；删除已退休的 `oa:signTask:confirm`。
- [ ] 重新定义 `sync_sign_hr_permissions_with_plan()`，模板权限由新基础过程管理，不再硬编码旧 4611。
- [ ] 重新定义 transfer/offboarding 包装过程，继续按权限字符串管理现有生命周期权限，但不得把旧冲突 ID 当作签约权限。
- [ ] 清理 `sys_sign_hr_menu_grant` 中已被其他业务复用的旧签约 ID，再按新节点重建当前托管角色授权。
- [ ] 保留 9656 技术证据的独立授权；切换唯一 HR 时不得自动授予或回收非本功能所有的管理员权限。
- [ ] 增加仓库级菜单 ID 所有权扫描，发现同一 ID 对应不同 `perms/component` 时发布静态门禁直接失败。
- [ ] 在隔离库双跑迁移；核对专用 HR 拥有完整业务权限且无管理员通配符，普通员工和其他 HR 均不能越权。

**验收：** 专用非管理员 HR 能看到任务详情、重新校验、发送、选择公司与印章、最终化、撤回和重试；管理员不作为唯一验收账号。

### Task A2：写入真实截止时间并实现拒签、过期和替代版本

**Files:**

- Create: `sql/erp_oa_sign_package_lifecycle_20260716.sql`
- Create: `docker/mysql/db/erp_oa_sign_package_lifecycle_20260716.sql`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignPackageStatus.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignPackageRefuseRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignExceptionResolutionRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageLifecycleService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageExpiryScheduler.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskWorkflowService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignPackageService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTaskService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignResponseSanitizer.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageLifecycleServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageExpirySchedulerTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignPackageControllerTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java`

- [ ] 发送事务以同一个秒级 `sentTime` 计算任务和签约包截止时间；天数来自冻结方案版本，按日历日处理，发送前不消耗签署期限。
- [ ] 任务路径缺少冻结方案或截止天数时 fail-closed，不能回退为无截止时间；应急手工包必须显式记录策略来源。
- [ ] 新增包状态 `refused`、`expired`，并让任务的 `PENDING_SIGN/VIEWED/PENDING_FINAL_CONFIRM` 可以进入 `REFUSED/EXPIRED`。
- [ ] 员工拒签请求固定携带 `requestId`、`documentVersion`、结构化原因码和说明；重复请求返回同一终态，不产生重复事件/通知。
- [ ] 过期扫描器按批次领取到期记录，使用 status/version CAS；`PENDING_COMPANY` 是 HR 责任阶段，不按员工签署期限自动过期。
- [x] 代码和仓库配置均将过期调度器默认保持关闭（`OA_SIGN_EXPIRY_ENABLED=false`），避免迁移/UAT 前误改业务状态。
- [ ] 生产 Nacos 在生命周期迁移、专用 HR UAT 和回滚演练通过后显式设置 `oa.sign.expiry.enabled=true`，并保存一次真实过期扫描证据。
- [ ] 拒签/过期后旧文件、首次签名、最终文件和事件全部只读；员工不能继续阅读确认、签名或最终确认。
- [ ] HR 处置只能关闭或创建替代任务/包；新记录保存 `reissueOfTaskId/reissueOfPackageId`，旧记录保存替代目标，禁止原地复活。
- [ ] 延期只能发生在到期前并写审计事件；已过期记录必须创建新版本。
- [ ] 列表、指标、待办和详情使用同一事实状态，不再由页面推断。

### Task A3：冻结并实际应用签名/印章位置策略

**Files:**

- Create: `sql/erp_oa_sign_document_policy_snapshot_20260716.sql`
- Create: `docker/mysql/db/erp_oa_sign_document_policy_snapshot_20260716.sql`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackageDocument.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageDocumentMapper.xml`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlacementPolicyService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignedPdfService.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageServiceImplTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignedPdfServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 包内文件生成时冻结 `signaturePositionJson` 和 `companySealPositionJson`，之后模板修改不能改变已生成包。
- [ ] 位置策略只接受明确模式：追加确认页，或包含页码/x/y/宽/高的固定坐标；未知字段、越界、无效页码和半套配置一律阻断发布/发送。
- [ ] 员工首次签名只处理 `employeeSignRequired=Y` 的文件，并应用该文件冻结的员工签名位置。
- [ ] 最终化重新渲染全部员工可见文件以冻结公司主体；员工签名只复用到 `employeeSignRequired=Y` 文件，不能复制到员工手册等只读文件。
- [ ] PDF 服务支持“仅签名、仅印章、签名+印章”三种合法组合；追加证据页只绘制实际存在的图像和文案。
- [ ] 公司印章范围以 G0 法务矩阵为准；若并非所有员工可见文件都盖章，新增显式 `companySealRequired` 快照，禁止用“坐标为空”推断业务含义。
- [ ] 每个正式模板至少用一个真实 PDF 渲染测试验证页码、边界、中文字体、签名方向、印章比例和最终哈希。
- [ ] 迁移不回填历史已签记录的位置；历史记录保持 `LEGACY_APPEND_ONLY` 或原验真结论，不能伪造新坐标证据。

### Task A4：补齐通知、提醒和失败恢复

**Files:**

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationDispatcher.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaTodoServiceImpl.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignReminderScheduler.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationOutboxServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationDispatcherTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignReminderSchedulerTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaTodoServiceImplTest.java`

必须覆盖的业务事件：

| 事件 | 接收人 | 路由 |
| --- | --- | --- |
| 资料缺失/草稿待处理 | 专用 HR | 签约任务详情 |
| 包已发送 | 员工 | 我的签约详情 |
| 员工完成首次签名 | 专用 HR | 公司与印章处理 |
| 最终合同已生成 | 员工 | 最终合同确认 |
| 截止前提醒 | 员工 | 当前待签/待最终确认包 |
| 员工拒签 | 专用 HR | 拒签处置详情 |
| 合同过期 | 专用 HR + 员工 | 过期只读详情 |
| 最终完成 | 专用 HR + 员工 | 归档/验真详情 |

- [ ] 每个接收人、渠道、业务事件使用稳定幂等键；重跑调度不重复通知。
- [ ] IN_APP 与 MOBILE_PUSH 独立重试；推送失败时站内消息仍可成功。
- [ ] 首次签名和最终合同生成必须在相应状态事务内写 Outbox，不在事务提交前直接远程调用。
- [ ] 提醒策略从冻结方案版本读取；没有期限或策略时不猜测默认提醒。
- [ ] DEAD 通知在 HR 抽屉可见并可按 `businessKey` 重新入队；重试不重新执行业务状态变化。
- [ ] 对 System 通知服务超时、重复派发、OA 重启、两个实例并发领取做失败注入测试。

### Task A5：前端人工闭环、异常处置与权限拆分

**Files:**

- Modify: `erp-ui/src/api/oa/signPackage.js`
- Modify: `erp-ui/src/api/oa/signTask.js`
- Modify: `erp-ui/src/views/oa/signTask/index.vue`
- Modify: `erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue`
- Modify: `erp-ui/src/views/oa/signPackage/index.vue`
- Modify: `erp-ui/src/views/oa/signPackage/CompanySealManagement.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Modify: `erp-ui/src/utils/signDictionary.js`
- Modify: `erp-ui/src/utils/todoRouteResolver.js`
- Modify: `erp-ui/src/utils/todoMutationMatcher.js`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLegalEntityController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java`
- Modify: `erp-ui/test/signTaskCenter.test.js`
- Modify: `erp-ui/test/signPackageModule.test.js`
- Modify: `erp-ui/test/signPackageEvidenceUx.test.js`
- Modify: `erp-ui/test/signPackageFileGate.test.js`
- Modify: `erp-ui/test/signPackageRequestFailure.test.js`
- Create: `erp-ui/test/signManualClosure.test.js`
- Create: `erp-ui/test/signPackageRefusal.test.js`
- Create: `erp-ui/test/signDeadlineUx.test.js`
- Create: `erp-ui/test/signCompanySealManagement.test.js`

- [ ] 任务抽屉统一显示当前责任人、下一动作、签署截止、公司/印章就绪、最近通知状态和结构化失败原因。
- [ ] `REFUSED/EXPIRED` 不再只有“线下处理”文案；展示原因、时间、冻结版本、事件和“关闭/创建替代版本”动作。
- [ ] 移动端在允许状态提供拒签入口，要求原因分类、说明和二次确认；拒签后只读查看旧证据。
- [ ] 移动端显示截止倒计时/已过期状态；过期或拒签后签名与最终确认按钮完全不可用。
- [ ] 桌面端、移动端、待办和消息统一复用状态/场景/事件字典，不各自维护另一套映射。
- [ ] 所有 mutation 防重复点击；不确定响应沿用原 `requestId` 查询/重试，不能生成新动作。
- [ ] `todoMutationMatcher.js` 覆盖 finalize、final-confirm、revalidate、send、retry、notification-retry、cancel、refuse、resolve 和 remind。
- [ ] 公司主体、部门绑定、印章查看、印章编辑从 `oa:signPackage:template` 拆成独立权限；按钮和后端接口都校验，不能只靠前端隐藏。
- [ ] 生产默认隐藏自由新建和按岗位批量建包；如果启用应急入口，页面明确显示“非生命周期任务来源”并要求原因。
- [ ] 不继续扩张 2000+ 行 `signPackage/index.vue`；把公司/印章、签署记录和异常处置拆成独立组件后再进入统一中心阶段。

### Task A6：建立可重复的真实 UAT

**Files:**

- Create: `scripts/qa/prepare_contract_signing_uat.py`
- Create: `scripts/qa/run_contract_signing_api_uat.py`
- Create: `scripts/qa/verify_contract_signing_evidence.py`
- Create: `scripts/qa/contract-signing-uat.example.json`
- Create: `docs/releases/contract-signing-uat-runbook.md`
- Create: `docs/releases/contract-signing-release-checklist.md`

- [ ] 在隔离数据库准备：专用非管理员 HR、至少两个组织范围、测试员工、正式候选模板、五场景方案、公司主体和有效印章。
- [ ] 每个法律主体至少覆盖劳动合同和劳务合同；五场景各完成 1 条人工闭环。
- [ ] 主成功路径固定为：任务生成 → 补资料/校验 → 发送 → 员工逐文件加载/阅读 → 首签 → HR 选主体/印章 → 员工逐份读取最终合同 → 最终确认 → 下载/验真。
- [ ] 失败路径覆盖：跨组织越权、错误主体、印章过期/哈希不一致、模板坐标越界、文件缺失/篡改、重复点击、网络超时、通知服务不可用、并发扫描、拒签、过期、替代版本。
- [ ] 浏览器同时覆盖桌面 HR 和 390×844 员工视口；检查控制台错误、焦点返回、加载失败提示和深链恢复。
- [ ] 所有证据 JSON 记录应用提交、数据库迁移哈希、方案版本、模板哈希、测试账号角色和验收人，但不保存签名图片、合同正文或员工敏感值。
- [ ] 若生产仍为 MySQL 5.7，必须在获授权的非虚拟化 5.7 验收库重复迁移和核心查询；MySQL 8 的通过不能替代该门禁。

**Release A UAT 完成门：**

- 错误合同 0；重复任务 0；重复通知 0。
- 新文件验真率 100%；签名/印章位置人工逐页通过。
- 专用 HR 完整业务权限 100%，技术证据与跨组织越权 0。
- 五场景、拒签、过期、替代版本和通知失败恢复全部有证据。
- HR、法务、测试/业务代表完成签字确认；测试人与法务审核人不能只有同一个人。

### Task A7：Release A 发布、灰度与回滚

**Files:**

- Create: `scripts/verify-contract-signing-release.sh`
- Create: `scripts/test_contract_signing_release_contract.py`
- Create: `scripts/contract-signing-release-20260716.json`
- Create: `scripts/contract-signing-release-files-20260716.list`
- Create: `scripts/contract-signing-migrations-20260716.list`
- Create: `docs/releases/20260716-contract-signing-release-manifest.md`
- Create: `docs/runbooks/contract-signing-operations.md`

- [ ] 发布静态门禁校验 allowlist、迁移顺序/依赖/哈希、双份 SQL、菜单 ID 所有权、关键类、前端 API 和所有自动开关为 false。
- [ ] 迁移顺序固定为：菜单/权限修复 → 生命周期/截止 → 文件策略快照 → 后端 → 前端；每步都有前置结构检查。
- [ ] 数据库迁移前备份签约、菜单、角色授权、通知和法律主体/印章相关表，并生成 SHA-256 清单。
- [ ] 先在隔离库双跑迁移，再在生产维护窗执行一次；不得用 Docker bootstrap 代替生产迁移账本。
- [ ] 后端就绪后用专用 HR 验证权限矩阵，再发布前端；管理员成功不能作为权限验收。
- [ ] 首批只开放一个低风险组织和测试/内部范围，自动发送保持关闭；观察至少 3 个完整人工样本后再扩展。
- [ ] 第一回滚动作是关闭签约入口/应急建包开关和调度器，不删除任何业务记录或证据。
- [ ] 菜单回滚只撤销新角色映射并恢复旧路由可见性；不能重新启用已知冲突的 4600～4611 签约权限节点。
- [ ] 已发送、已阅读、已签、拒签和过期记录全部保留；数据库字段采用向前兼容修复，不在存在业务数据时 DROP。

## 六、Release B：入职/续签自动发送

实施入口继续使用现有 [阶段4计划](./2026-07-11-contract-automation-phase-4-auto-send.md)，但必须以 Release A 完成作为新前置门。

- [ ] 创建自动化决策表、纯资格判断服务、影子决策、门禁报告、范围化开关和独立自动发送处理器。
- [ ] `REGULARIZE/TRANSFER/OFFBOARD` 永久拒绝自动发送；只允许标准入职和标准续签进入资格判断。
- [ ] 开关层级固定为全局 → 场景 → 组织 → 法律主体 → 方案版本，任一级缺失均为 false。
- [ ] 影子模式连续运行至少 7 个完整自然日；错误方案 0、重复任务 0、验真 100%、人工换方案 0、发送成功率 ≥99%。
- [ ] 首次 live 只开一个组织、一个主体、一个已发布标准入职方案；续签使用独立观察窗口。
- [ ] 关闭开关后未发送任务回到人工确认，已发送合同继续完成，不撤回证据。

## 七、Release C：统一合同中心

实施入口继续使用现有 [阶段5计划](./2026-07-11-contract-automation-phase-5-unified-center.md)。

- [ ] 后端以 `SIGN_PACKAGE/LEGACY_LABOR_CONTRACT` 建立只读统一投影；禁止前端分别分页后拼接。
- [ ] 列表由服务端统一过滤、排序和分页；移动端不再固定拉 50 条后客户端分组。
- [ ] 详情、下载、验真按来源委托原服务；旧合同不搬表、不改文件、不伪装为新证据。
- [ ] 桌面端整合任务、合同档案、模板/方案/主体/印章和自动化设置；复用现有组件，不复制业务逻辑。
- [ ] 移动端统一新旧合同入口；旧路由和旧推送 payload 至少兼容一个发布周期。
- [ ] 权限拆为 list/query/file/verify/export，跨员工、跨组织和伪造 sourceType 均不得泄露记录存在性。

## 八、Release D：运营报表与持续治理

实施入口继续使用现有 [阶段6计划](./2026-07-11-contract-automation-phase-6-operations.md)。

- [ ] 建立不含个人敏感字段的每日指标、失败原因、拒签/过期、通知 DEAD 和自动化效果聚合。
- [ ] 每日抽检新签文件、历史样本和恢复后任务；发现哈希不一致只告警和熔断，绝不重写期望哈希。
- [ ] 健康检查覆盖证据异常、待发送积压、HR 配置、文件存储、通知 DEAD 和事件 Outbox。
- [ ] 运营看板默认只读；重试、重算、导出和完整性审计使用独立权限和审计日志。
- [ ] 建立数据库+文件一致性备份、manifest 哈希和隔离恢复演练；每季度随机验证至少 10 个已签包。
- [ ] 连续不达标自动关闭对应自动发送范围，但保留影子判断和全部历史决策。

## 九、验证命令

后端签约聚焦门禁使用 Java 17 和 headless 模式，避免 PDFBox/AWT 在 macOS 字体扫描时导致非业务退出：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -Djava.awt.headless=true -Dsurefire.failIfNoSpecifiedTests=false \
  -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTaskMigrationTest,OaMapperBindingTest,OaSignTaskStateMachineTest,OaSignPackageServiceImplTest,OaSignedPdfServiceTest,OaSignNotificationOutboxServiceTest,OaSignNotificationDispatcherTest test
```

每个 Release A 任务完成后运行 OA 模块全量：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
./mvnw -Djava.awt.headless=true -pl erp-modules/erp-oa -am test
```

前端聚焦和全量门禁：

```bash
cd erp-ui
node test/signTaskCenter.test.js
node test/signPackageModule.test.js
node test/signPackageEvidenceUx.test.js
node test/signPackageFileGate.test.js
node test/signPackageRequestFailure.test.js
node test/contractUsabilityAudit.test.js
node test/unifiedTodoRouteResolver.test.js
node test/unifiedTodoMutationRefresh.test.js
node test/targetedUserNotification.test.js
node test/mobilePushRegistration.test.js
npm test
npm run build:prod
```

SQL、源码和发布门禁：

```bash
cmp sql/erp_oa_sign_menu_permission_repair_20260716.sql docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql
cmp sql/erp_oa_sign_package_lifecycle_20260716.sql docker/mysql/db/erp_oa_sign_package_lifecycle_20260716.sql
cmp sql/erp_oa_sign_document_policy_snapshot_20260716.sql docker/mysql/db/erp_oa_sign_document_policy_snapshot_20260716.sql
python3 scripts/contract/test_verify_menu_id_ownership.py
python3 scripts/contract/test_verify_contract_signing_scope.py
bash scripts/verify-contract-signing-release.sh --static
git diff --check
```

最终项目级门禁继续执行：

```bash
./mvnw -T 1C -Djava.awt.headless=true \
  -pl erp-modules/erp-system,erp-modules/erp-oa,erp-modules/erp-inventory,erp-modules/erp-file,erp-modules/erp-approval \
  -am test
cd erp-ui && npm test && npm run build:prod
bash scripts/verify-new-business-release.sh --static
```

所有命令必须退出码为 0；测试不能通过删除断言、放宽条件、`|| true` 或 skip 新业务路径变绿。

当前命令基线（2026-07-16）：上述后端聚焦命令实测 86 项通过、0 失败；列出的 10 个前端聚焦测试均通过，但 `npm test` 当前会因 `newBusinessMigrationRelease.test 2.js` 被重复文件保护门禁拒绝，必须在 G0 按内容审查后收口。

## 十、风险与处置

| 风险 | 预防 | 触发后的第一动作 |
| --- | --- | --- |
| 脏工作区混入其他业务 | allowlist、哈希清单、分任务暂存 | 停止发布，重新生成 scope diff |
| 菜单 ID 再冲突 | 9650～9669 预检 + 仓库扫描 + DB `SIGNAL` | 中止迁移，不覆盖现有菜单 |
| 专用 HR 仍靠管理员权限通过 | 非管理员账号做权限矩阵 UAT | 回滚角色映射，修正过程后重跑 |
| 签名/印章错页或错文件 | 文件级策略快照、越界校验、逐页 UAT | 停止发送；已发记录保留并人工处置 |
| 拒签/过期原地复活 | 终态不可逆、替代记录关联 | 关闭异常处置入口，保留事件并向前修复 |
| 推送失败阻塞签约 | Outbox 分渠道重试 | 降级站内消息，业务事务不回滚 |
| PDFBox 本机非业务崩溃 | Java 17 + `java.awt.headless=true` | 区分环境退出与测试断言失败后重跑 |
| 生产数据库版本不一致 | 发布前确认 5.7/8.x 并在同版本复验 | 阻断发布，不以静态兼容替代实库验证 |
| 法务模板未定稿 | 草案保持停用、版本哈希审批 | 禁止发布方案和发送合同 |
| 自动发送过早开启 | 默认 false、发布静态扫描、7 天影子门 | 关闭全局/场景开关，转人工处理 |

## 十一、最终完成定义

只有同时满足以下条件，签约功能才可标记为“人工生产闭环已完善”：

- [ ] 9650～9669 签约菜单无冲突，唯一 HR 非管理员账号完成全部业务动作；技术证据仍独立授权。
- [ ] 五场景正式模板、方案、法律主体、部门绑定和印章均有版本与审批记录。
- [ ] 截止时间在发送事务真实写入；提醒、拒签、过期和替代版本都有业务生产者和不可变事件。
- [ ] 员工签名只出现在必签文件，签名/印章位置按冻结策略真实生成并逐页验收。
- [ ] 首次签名通知 HR、最终合同通知员工、拒签/过期/完成通知均可投递、重试和人工恢复。
- [ ] 五场景成功路径、异常路径、权限、并发、文件篡改和通知故障的真实 UAT 全部通过。
- [ ] OA、前端、项目总测试、生产构建、双份 SQL、菜单所有权、发布静态门禁和 `git diff --check` 全部通过。
- [ ] 发布 manifest 能对应应用提交、迁移哈希、方案版本、模板哈希和验收证据；当前工作区未混入范围外文件。
- [ ] 自动发送仍关闭；其开启必须单独满足 Release B 的影子观察门。

完成 Release A 后，再更新 [统一合同签约总路线图](./2026-07-11-unified-contract-signing-roadmap.md) 的阶段状态；不得仅因代码合并或单元测试通过提前标记完成。
