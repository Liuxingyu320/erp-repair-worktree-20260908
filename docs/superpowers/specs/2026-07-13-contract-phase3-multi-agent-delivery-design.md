# 合同自动化阶段 3 多窗口交付设计

**日期：** 2026-07-13

**功能代码基线：** `6766dc43bd82067d5d835e7923bb7fcc302cf07f`（调岗场景已完成）

**规划分支：** `codex/phase3-multi-agent-plan-20260713`

**决策：** 使用三个 Codex 窗口，采用“一个集成负责人兼 Outbox 执行者、一个离职场景执行者、一个前端基线治理执行者”的组织方式。三个执行分支共享同一个只含规划文档增量的协调基点；只有集成负责人可以合并执行分支。

## 1. 背景与现状

调岗场景提交 `6766dc43` 已经提供：

- System 权威业务日、未来拒绝、当天原子生效、历史高风险补录、结构化二次确认、审计、幂等和真实 MySQL 5.7 并发控制。
- OA `TRANSFER` 规则、冻结前后快照、方案版本匹配、草稿或补资料任务、历史业务标识。
- 桌面 HR 调岗入口、服务端业务日、历史风险确认、任务详情展示。
- System 621 项和 OA 600 项测试通过；前端调岗专项与生产构建通过。

阶段 3 仍存在三个相互关联但可独立提交的问题域：

1. **人事事件 Outbox 尚未真正分发。** `SysHrSignEventOutboxMapper` 已有领取、抢占、成功、重试和死信 SQL，`RemoteSignTaskService` 与 OA 内部事件接口也已存在，但没有 `HrSignEventDispatcher` 和补偿扫描器。人事动作能落 Outbox，却没有完整的 System → OA 最终投递闭环。
2. **离职场景尚未实现。** 数据模型已有 `leaveDate`，OA 签约包已有通用离职日期/原因字段和一类离职证明模板，但没有离职确认 DTO、生命周期事务、`OFFBOARD` 规则、专用权限和 HR 操作界面。
3. **前端全量门禁仍有九个基线失败。** 当前 142 个 Node 测试文件中 133 个通过、9 个失败；这些失败与调岗代码无关，但会妨碍最终阶段 3 全量验收。

## 2. 目标

本轮多窗口交付结束时必须达到：

- 人事动作 Outbox 可以可靠、幂等地送达 OA，连接中断后能够退避重试和补偿恢复。
- 离职确认能够原子更新员工状态、离职日期和账号状态，并生成统一 `OFFBOARD` 业务事件。
- OA 能根据离职类型、资料状态和服务端派生风险生成离职材料草稿或单 HR 补资料任务。
- 桌面唯一 HR 能完成离职确认；不增加第二审批人，不自动发送合同。
- 前端九个基线失败经过逐项根因分析后关闭，不能通过删除断言或提交敏感/本机构建产物伪造绿色结果。
- 三个执行分支在集成分支完成组合测试、MySQL 5.7 验证、迁移幂等验证和真实环境门禁记录。

## 3. 非目标

- 不在本轮开启阶段 4 自动发送，`sign.automation.global.enabled` 保持关闭。
- 不新增多级 HR、HR 负责人或第二审批人。
- 不把未来离职确认实现为定时生效队列；离职确认只能在最后工作日到达后执行。
- 不在 Agent 3 的前端门禁治理中改写调岗或离职业务契约。
- 不由任何执行 Agent 直接合并到 `7月12号`、推送远端或修改原工作树。
- 不在补偿扫描器中通过比较整个员工表猜测业务事件。
- 不把 Android/iOS 密钥、签名文件、本机缓存或无关生成目录提交到 Git。

## 4. 方案比较与选择

### 方案 A：单窗口串行执行

优点是没有跨分支合并冲突。缺点是 Outbox、离职、前端移动端治理和最终验收全部串行，单个上下文会同时容纳 System、OA、Vue、Capacitor 和数据库细节，执行周期长且容易在长上下文中遗漏边界。

### 方案 B：三个平级窗口自由并行

并发度最高，但没有唯一合并责任人。三个 Agent 可能使用不同基点、修改同一迁移包装过程、重复调整测试或各自声称全量通过，最终冲突和验收责任不明确。

### 方案 C：一个集成负责人加两个独立执行者

这是选定方案。Agent 1 一边实现关键 Outbox 基础设施，一边维护集成分支和最终测试记录；Agent 2 专注离职业务；Agent 3 专注既有前端门禁。文件所有权基本不重叠，并且只有 Agent 1 处理合并、冲突和最终验收。

## 5. 分支与 worktree 拓扑

不可变功能基线是 `6766dc43`。规划分支在该提交之上只增加设计、实施计划和窗口提示词。规划文档最终提交后，三个执行分支必须从同一个规划分支 HEAD 创建，确保三方看到相同说明，而代码基线仍然等价于 `6766dc43`。

```text
6766dc43  调岗完成提交
    |
    +-- codex/phase3-multi-agent-plan-20260713
            |
            +-- codex/phase3-integration-outbox-20260713   Agent 1
            +-- codex/offboarding-automation-20260713      Agent 2
            +-- codex/frontend-baseline-gates-20260713     Agent 3
```

每个窗口使用独立 worktree。任何 Agent 都不得切换、清理或重置其他 worktree。执行 Agent 只提交本任务文件，并在交付时报告提交号、测试命令、真实结果、未完成项和工作树状态。

## 6. Agent 1：Outbox 与集成负责人

### 6.1 文件所有权

Agent 1 主要拥有：

- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventDispatcher.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrSignEventCompensationScanner.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventDispatcherTest.java`
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/HrSignEventCompensationScannerTest.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysHrSignEventOutboxMapper.java`
- `erp-modules/erp-system/src/main/resources/mapper/system/SysHrSignEventOutboxMapper.xml`
- Outbox 所需的专用配置类、配置项、MySQL 5.7 集成测试和最终验证文档。

`ErpSystemApplication` 已经启用 `@EnableScheduling`，Agent 1 必须复用，不能重复引入另一套调度框架。

### 6.2 分发状态机

分发器每次最多读取 100 条到期记录，按 `next_retry_time/create_time/outbox_id` 排序。合法状态转换为：

```text
PENDING --claim--> SENDING --2xx + taskId--> SENT
RETRY   --claim--> SENDING --2xx + taskId--> SENT
SENDING --stale--> SENDING --重新领取后成功--> SENT
SENDING --网络/超时/429/5xx--> RETRY
SENDING --确定的4xx业务契约错误--> DEAD
```

抢占、完成、重试和死信更新必须同时匹配 `outbox_id + status + version`。没有抢占成功的线程不得调用 OA。OA 返回成功但缺少正数 taskId 时不得标记 `SENT`。

推荐退避序列为 1、5、30、120、360 分钟；超过最后一级后继续使用 360 分钟，除非明确的 4xx 契约错误进入 `DEAD`。`429` 属于可重试状态，不进入 `DEAD`。

分发器通过现有 `RemoteSignTaskService.publishEvent(event, SecurityConstants.INNER)` 调用 OA。反序列化失败属于不可恢复的本地事件契约错误，记录脱敏错误类型后进入 `DEAD`。日志不能输出员工快照、身份证、电话、地址、薪资或完整 payload。

### 6.3 补偿边界

补偿扫描器只根据生命周期动作表和 Outbox 表工作：

- 有已确认生命周期 action、但缺少相同 `action_id + version` Outbox 时，使用冻结 action 快照重建一次事件。
- 长期 `SENDING` 由现有到期查询重新领取，不另建第二条 Outbox。
- 已经 `SENT` 的记录不重发；OA 端仍以场景 dedupeKey 返回相同 taskId，构成双层幂等。
- 补偿插入依赖数据库唯一键，捕获重复键后读取既有记录，不把竞争当成业务失败。
- 不扫描员工当前状态反推动作，因为当前档案可能已被后续动作改变。

### 6.4 集成责任

Agent 1 是唯一合并负责人。它不得在 Agent 2/3 交付前修改其专属文件；合并冲突只能在完整理解双方意图后解决，不能直接选择 ours/theirs。每次合并后先运行受影响模块专项，再运行全量。

## 7. Agent 2：离职合同自动化

### 7.1 文件所有权

Agent 2 主要拥有：

- 新建 `HrOffboardingConfirmRequest` 及必要的结构化高风险确认 DTO。
- 修改 `HrEmployeeProfileController`、`IHrLifecycleService`、`HrLifecycleServiceImpl`。
- 增加离职所需的条件更新 Mapper 方法，避免通用全量更新覆盖并发修改。
- 新建 `OffboardSignScenarioRule` 及对应 System、OA、MySQL 5.7 测试。
- 新建 `HrOffboardingDialog.vue`，修改员工列表、档案详情、HR API 和离职相关前端测试。
- 新建双份一致且可重复执行的离职迁移 SQL，更新阶段 3 路线图和验证记录。

Agent 2 不修改 Outbox 调度器、领取/重试算法或前端九项基线文件。

### 7.2 离职确认入口

`POST /hr/employee/{userId}/offboard` 至少接收：

- `requestId`
- 离职类型
- 最后工作日
- 离职原因
- 工资结算状态
- 资产交接状态
- 竞业决定
- 补偿金额及补偿说明
- 高风险确认内容（仅高风险时）

唯一 HR 权限使用独立机器码 `hr:employee:offboard`。管理员不代替业务 HR。服务端使用 `Asia/Shanghai` 自然日；最后工作日晚于服务端业务日时拒绝确认，不修改员工、不停用账号、不写 action/Outbox，也不创建待未来自动执行的任务。

### 7.3 事务与幂等

确认成功时在一个事务中：

1. 校验唯一 HR、员工范围、员工当前状态和请求字段。
2. 锁定员工档案并读取冻结 before 快照。
3. 以服务端主数据派生 after 快照，将 employeeStatus 设为“离职”、写 leaveDate，并将账号状态设为停用。
4. 保存 `OFFBOARD_CONFIRMED` 生命周期动作、业务生效日、实际确认时间、风险等级、结构化确认、请求 ID、IP 和 User-Agent。
5. 写入唯一 `sys_hr_sign_event_outbox`。
6. 任一步失败整体回滚。

相同 requestId 重放返回原 actionId，不重复禁用、不重复写 Outbox。不同 requestId 对同一员工同一离职日的并发确认只能有一次成功。重放时必须核对当前数据库状态仍与原 after 快照一致。

### 7.4 风险派生

风险等级完全由服务端根据结构化字段派生。以下任一条件标记 `HIGH`：

- 非预期主动离职、辞退、违纪解除或存在劳动争议。
- 工资结算未完成。
- 资产交接未完成。
- 竞业决定为待定或需要执行竞业限制。
- 存在非零补偿金额或自定义补偿说明。
- 最后工作日早于实际确认日，属于历史离职补录。

高风险仍可生成草稿，但必须由同一个唯一 HR 逐份处理，不能进入批量确认。历史补录和其他高风险确认都由服务端核对结构化内容，不仅依赖前端弹窗。

### 7.5 OA 规则

场景机器码固定为 `OFFBOARD`，dedupeKey 固定为 `OFFBOARD:<employeeId>:<actionId>:<actionVersion>`。规则读取已发布且启用的方案版本，根据离职类型及风险选择：

- 离职确认材料；
- 工作/资产交接材料；
- 工资与补偿结算材料；
- 保密和竞业提醒；
- 解除或终止劳动关系材料。

资料缺失时创建 `NEEDS_DATA` 单 HR 任务，不能静默跳过。方案不匹配或多方案冲突进入可解释的人工处理原因。所有离职任务停在 HR 确认状态；本轮不自动发送。

### 7.6 前端

桌面员工列表和档案详情仅向具备 `hr:employee:offboard` 的唯一 HR 展示“确认离职”。对话框展示离职业务字段、会生成的材料说明和风险结果，不展示 Hash。requestId 在打开对话框时生成，失败重试复用，关闭重开后更新。

未来最后工作日禁用确认并展示明确提示。高风险提交前展示员工、离职类型、最后工作日、工资/资产/竞业/补偿摘要和风险说明。成功后刷新员工列表、档案详情和统一待办；前端不直接创建 OA 签约包。

## 8. Agent 3：前端基线门禁治理

### 8.1 文件边界

Agent 3 只处理当前九个失败及它们直接依赖的生产文件和测试：

| 失败测试 | 责任边界 |
| --- | --- |
| `headerNoticeReadAll.test.js` | HeaderNotice 全部已读异步流程 |
| `laborContractModule.test.js` | 移动合同页链接和表单触控尺寸 |
| `mobileAppShell.test.js` | Capacitor Android 静态资源同步契约 |
| `mobileAuthEntryPages.test.js` | 选店页硬编码预览组织 |
| `mobileInventoryWorkbench.test.js` | 仓库工作台相机扫描能力文案 |
| `mobileProductionDataIsolation.test.js` | 生产入口测试数据隔离 |
| `mobileProfileMaintenance.test.js` | 移动头像操作触控尺寸 |
| `mobileProgressiveRedesign.test.js` | 移动页面滚动根和 viewport 生命周期 |
| `unifiedTodoBusinessFocus.test.js` | 调拨退回移动编辑动作 |

Agent 3 不修改 `HrEmployeeTransferDialog.vue`、离职 DTO/规则、生命周期 Service、OA 编排器或数据库迁移。

### 8.2 修复原则

每个失败必须先在 Node 24 环境单独复现，并判断：

1. 生产代码真实缺陷：修改最小生产代码并保留测试。
2. 测试与已批准业务契约不一致：提供代码/文档证据后收窄测试，不能只删除断言。
3. 缺少受控生成步骤：修复生成或校验流程；不能提交密钥、绝对路径、本机缓存和无关原生构建产物。

一次只修复一类根因，每类单独提交。最终必须运行 142 个前端测试文件、生产密钥扫描、生产构建和适用的 Capacitor 校验。

## 9. 跨 Agent 接口契约

三个 Agent 共享以下不可变契约：

- System 业务事件类型继续使用 `HrSignBusinessEvent`。
- action 的 `sourceBusinessId` 与 Outbox 的 `actionId/eventVersion` 不改变既有语义。
- OA 内部入口继续使用 `/signTask/inner/events` 和 `RemoteSignTaskService`，不新增重复 HTTP 接口。
- OA 任务幂等由场景规则 dedupeKey 和持久化唯一约束保证。
- Outbox 日志和错误信息不得记录完整员工快照或敏感字段。
- 所有新迁移在 `sql/` 和 `docker/mysql/db/` 保持逐字一致并支持重复执行。
- 单 HR 只负责业务确认；系统管理员仍只查看技术日志和证据。

Agent 发现接口需要变化时必须先通知 Agent 1，说明原因、影响文件和兼容方案；未经协调不得单方面更改共享 DTO、Outbox 状态或 OA 内部接口。

## 10. 合并协议

1. Agent 1 先完成 Outbox 分发与补偿提交，并保持集成分支干净。
2. Agent 2 完成离职分支后，报告提交号和 System/OA/前端专项结果。
3. Agent 3 完成基线治理后，报告每个失败的根因、提交号和前端全量结果。
4. Agent 1 首先合并离职分支，运行 System、OA、MySQL 5.7 和离职专项。
5. Agent 1 再合并前端基线分支，运行前端全量、生产构建和原生壳校验。
6. Agent 1 运行最终组合验证并新增统一验收记录。
7. 只有获得用户明确授权后，才把集成分支合并到 `7月12号` 或推送远端。

合并冲突由 Agent 1 逐段理解后解决。禁止使用 `git reset --hard`、整文件 ours/theirs 或删除测试来快速消除冲突。

## 11. 数据库与部署顺序

最终部署迁移顺序为：

1. 已有阶段 1、阶段 2、生命周期、方案版本迁移。
2. 调岗迁移 `erp_hr_transfer_effective_date_20260713.sql`。
3. Agent 1 如需增加 Outbox 索引或状态字段，执行 Outbox 增量迁移。
4. Agent 2 离职增量迁移，包括权限、审计字段或签约包字段。
5. 部署 OA，再部署 System，最后部署前端和必要的原生壳资源。

在测试环境中每份新增迁移连续执行两次，确认 MySQL 5.7.44 无错误且数据行数、权限授权和唯一键不重复。应用版本回滚时保留向后兼容的新增列和 Outbox 数据，不做破坏性降级 SQL。

## 12. 验证门禁

### Agent 1 门禁

- Dispatcher 与补偿扫描专项全部通过。
- 覆盖并发抢占、陈旧 `SENDING`、OA 连接失败、429、4xx、5xx、退避、死信、成功重放和敏感日志隔离。
- 真实 MySQL 5.7 验证 version 条件更新和唯一 Outbox。

### Agent 2 门禁

- 覆盖未来离职零副作用、当天离职成功、历史高风险、无权限、重复请求、并发、Outbox 失败整体回滚。
- OA 覆盖标准、缺资料、高风险、方案缺失、方案冲突、无需材料和重复事件。
- 前端覆盖未来禁用、高风险确认、结构化 payload、失败不伪造成功和刷新待办。

### Agent 3 门禁

- 九个失败逐项专项通过。
- `npm test` 全量通过，不再保留未解释失败。
- `npm run build:prod` 和前端密钥扫描通过。
- 原生校验如因签名/推送凭据缺失而无法完成，必须明确列为部署门禁，不能伪造凭据。

### 最终组合门禁

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am test

cd erp-ui
npm test
npm run build:prod
```

此外必须完成：

- `git diff --check`。
- 所有双份迁移 SQL 逐字比较。
- MySQL 5.7 迁移重复执行。
- OA 停机时确认人事动作仍提交且 Outbox 重试；OA 恢复后任务恰好创建一次。
- 调岗与离职各执行一次真实唯一 HR 测试环境业务链路。
- 所有签约任务停在单 HR 确认状态，没有自动发送。

## 13. 失败恢复与停止条件

- 任一 Agent 发现基点不是规划分支最终 HEAD，应停止开发并重建正确 worktree，不在错误基点继续提交。
- 出现共享接口冲突时，执行 Agent 停止修改该接口，由 Agent 1 决定兼容方案。
- 测试发现原有业务缺陷但超出分配边界时，只记录最小复现，不顺手扩展任务。
- 三次修复仍无法解决同一根因时停止试错，提交证据并重新评估设计。
- 最终全量测试未通过时不得合并到 `7月12号`；基线失败必须由 Agent 3 关闭或由用户明确接受为上线阻断项。
- 测试环境缺少真实 HR、模板或通知终端时，自动化部分可以完成，但真实端到端门禁保持未完成状态。

## 14. 交付物

最终交付必须包含：

- 三个执行分支及各自提交记录。
- Agent 1 的 Outbox/补偿实现和验证记录。
- Agent 2 的离职 System、OA、前端、SQL、测试和场景文档。
- Agent 3 的九项根因与修复对照表。
- 集成分支的迁移顺序、全量测试数量、MySQL 版本、真实链路结果和已知限制。
- 明确的分支名、提交号、worktree 路径，以及“未推送/未合并”或已获授权的实际状态。
