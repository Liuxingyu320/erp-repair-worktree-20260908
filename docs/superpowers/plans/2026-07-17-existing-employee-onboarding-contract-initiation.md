# 已入职/导入员工首次发起入职合同改造实施方案

> 日期：2026-07-17
> 分支：`codex/erp-product-optimization`
> 范围：ERP 后端、Web 前端、自动化测试和业务说明；不修改 Docker。

## 1. 目标

为已经导入档案、处于试用/正式/已入职状态，但系统中没有有效签约记录的员工，提供可审计、可预览、可批量执行的“首次发起入职合同”流程。

本次改造必须保证：

1. 员工状态与合同状态完全独立，任何代码都不得用“已入职、试用、正式”推断“已签合同”。
2. 发起前读取 OA 中真实签约包/签约任务状态，已有记录时执行发送草稿、催签、盖章、重试、重发或查看，不重复创建首次合同。
3. 对系统外纸质合同、第三方电子合同设置人工核验闸门，避免把“系统无记录”直接等同于“现实中未签”。
4. 支持最多 100 人的批量预览、创建正常入职签约任务和发送；单人失败不回滚其他人的成功结果。
5. 批量发送具备幂等与并发保护，重试不会重复生成文件、重复建任务或重复发送。

## 2. 状态模型与处理规则

员工状态继续来自员工档案，仅用于说明在职阶段。合同状态只根据 OA 签约包、签约任务和最终签署文件证据计算。

| OA 事实状态 | 合同展示状态 | 建议动作 | 是否允许创建新的首次合同 |
| --- | --- | --- | --- |
| 无入职签约记录 | 未发起 | 首次发起 | 是，且需人工确认无系统外合同 |
| 任务为 `READY_TO_SEND` 且绑定 `draft` 包 | 草稿未发送 | 发送已有任务 | 否 |
| 只有历史 `draft` 包、没有任务 | 历史记录待核验 | 人工核验/迁移 | 否 |
| `pending_sign` / `part_viewed` | 员工签署中 | 查看或催签 | 否 |
| `pending_company` | 待公司盖章 | 进入盖章 | 否 |
| `pending_final_confirm` | 待员工最终确认 | 查看或提醒 | 否 |
| `signed` 且有最终文件 | 已签署 | 查看/下载已签合同 | 否 |
| `refused` / `expired` / `voided` | 异常终态 | 审核后重发/替换 | 否，不走首次发起 |
| `failed` | 发送失败 | 重试已有任务/包 | 否 |
| 状态服务不可用或证据冲突 | 状态暂不可用 | 人工核查 | 否，禁止降级为未发起 |

## 3. 用户流程

### 3.1 入口

- 正常入口放在员工档案列表和员工详情中，名称为“处理入职合同”或“批量处理入职合同”。
- 现有签约包页面的自由建包、应急批量建包继续作为受功能开关和应急原因控制的兜底入口，不替代正常流程。
- 已有签约任务的员工跳转到签约任务中心继续处理。

### 3.2 批量流程

1. HR 从员工档案中明确勾选员工，最多 100 人。
2. 前端提交员工 ID 进行预览；后端返回员工状态、合同状态、建议动作、匹配方案、缺失资料和不可执行原因。
3. HR 必须主动勾选“已核验不存在已签纸质合同或未同步的第三方电子合同”；不得默认勾选。
4. 后端仅为合同状态“未发起”且资料完整的员工创建版本化 `ONBOARD` 签约任务；任务编排器生成并冻结方案版本和签约包，已有任务/包直接复用或分流。
5. HR 可选择“创建签约任务”或“创建任务并发送”。批量发送由后端逐项调用签约任务发送服务，前端不得并发调用多次单份发送接口。
6. 结果逐人展示；失败项可重试，成功项与已发送项不得重复处理。

## 4. 后端改造

### 4.1 员工签约候选资料

修改：

- `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SignCandidateUser.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java`
- `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysUserListVo.java`
- `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`

补齐员工状态、法人主体、试用期、基础工资、岗位工资、外勤补助、绩效工资、工资合计和工资版本映射。保留账户启用、岗位精确匹配、组织权限和最多 500 条的既有限制。

员工列表的 `userId` 必须按十进制字符串返回，避免超过 JavaScript 安全整数后在选人、预览和发送前已发生精度丢失。

### 4.2 真实合同状态查询与重复保护

修改：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml`

新增“按员工批量查询最新入职签约任务”和“最新入职签约包”查询。查询跨方案版本并兼容任务中心上线前无 `task_id` 的历史签约包，以事实上的既有入职签约记录阻止重复首次任务，不能只检查当前方案或仅检查未完成记录。

新增 `sql/erp_oa_sign_onboard_open_guard_20260717.sql`：使用 MySQL 5.7 可用的 STORED 生成列和唯一索引，从数据库层保证每个员工最多一个非终态 `ONBOARD` 任务。迁移执行前若发现存量重复任务必须 fail closed，由人工根据签约证据处理，不自动删除或取消任务。代码捕获唯一键竞争后只返回已存在的任务，不以新事件快照推进其状态。

该迁移单独登记在 `scripts/existing-employee-onboard-contract-migrations-20260717.list`，不并入已冻结且要求 Docker 镜像副本的 20260716 Release A 清单。

### 4.3 预览和正常签约任务创建

修改：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaExistingEmployeeOnboardService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaExistingEmployeeOnboardPreviewRequest.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaExistingEmployeeOnboardInitiateRequest.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardPreviewRow.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardInitiateItem.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaExistingEmployeeOnboardInitiateResult.java`

新增正常业务接口：

```text
POST /signTask/onboard/batch/preview
POST /signTask/onboard/batch/initiate
```

接口首版复用 `oa:signTask:send` 权限，同时校验当前合同经办人和门店数据范围，不新增菜单权限 SQL。发起接口要求说明原因和系统外合同核验字段，不受应急功能开关控制。现有签约包 `/batch/preview` 与 `/batch/createDrafts` 保持应急语义和功能开关。

预览行包含：既有包/任务 ID、合同状态编码、建议动作、是否可执行、资料缺失原因。状态服务异常时 fail closed，不得降级为“未发起”。

发起时由服务端重新读取受保护的员工签约快照，构造：

```text
scenario = ONBOARD
sourceType = HR_PROFILE_CONTRACT_INITIATION
sourceBusinessId = employeeId
sourceEventVersion = 合同开始日期 yyyyMMdd
beforeSnapshot = 当前员工签约快照
afterSnapshot = 当前员工签约快照
```

然后调用 `OaSignTaskOrchestrator.orchestrate(event)`，复用 `OnboardSignScenarioRule`、已发布不可变方案版本、签约包文档生成、任务状态机及通知发件箱。生成的包必须具有 `taskId` 和 `planVersionId`，不得带手工应急包标记。

### 4.4 代码归一化和方案路由

同时修复仍被应急批量流程使用的旧匹配边界：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignBatchServiceImpl.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackagePreflightValidator.java`

系统主数据使用规范编码：

- 用工类型：`LABOR_CONTRACT`、`SERVICE_CONTRACT`
- 社保类型：`SOCIAL_INSURED`、`SOCIAL_UNINSURED`

OA 批量流程兼容历史中文值，但匹配前统一归一化。方案路由与生命周期规则保持一致：劳动合同+参保为 A1-A3；劳动合同+不参保为 A4-A6；劳务合同+不参保为 B1-B3。

模板必填占位符驱动资料校验：模板需要地址、绩效工资等字段时才阻断对应缺失项，避免无关字段误伤其他方案。

### 4.5 正常任务级批量发送

新增/修改：

- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskBatchSendRequest.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignTaskBatchSendItem.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignTaskBatchSendResult.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaExistingEmployeeOnboardService.java`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`

新增：

```text
POST /signTask/batch/send
```

请求包含 `requestId` 和最多 100 个签约任务 ID。服务层不设置覆盖整批的事务，而是逐任务调用现有 `IOaSignTaskService.send()` 并捕获异常，返回成功、已发送、失败和可重试明细。

每项请求号由批次请求号、任务 ID 和已持久化的发送尝试次数确定性派生。同一尝试内的网络重放保持不变；明确发送失败后尝试次数增加，同一批次才能产生新的发送请求号并真正重试。现有任务事件 `request_id` 唯一键、任务 `dedupe_key` 和通知发件箱业务键共同保证幂等。状态分流为：

- `READY_TO_SEND`：调用任务发送服务；
- `SENDING`：返回处理中；
- `PENDING_SIGN`、`VIEWED` 及更后状态：返回 `ALREADY_SENT`；
- `FAILED + SEND_FAILED`：由批量服务用独立、确定性的重试请求号恢复到 `READY_TO_SEND` 后再发送；
- `NEEDS_DATA`、其他失败、拒签、过期、取消和状态冲突：停止并返回明确建议，不自动绕过状态机。

`/signPackage/batch/send` 如另行提供，只能用于 `taskId = null` 的手工应急草稿，不能用于本流程。

## 5. 前端改造

修改/新增：

- `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- `erp-ui/src/views/hr/components/HrOnboardContractBatchDialog.vue`
- `erp-ui/src/api/oa/signTask.js`
- `erp-ui/src/utils/signDictionary.js`
- `erp-ui/src/views/oa/signTask/index.vue`

实现内容：

- 员工状态与合同状态并列展示；合同状态请求失败显示“合同状态暂不可用”，并禁用发起。
- 员工详情中原“入职合同”资料区改名为“用工合同资料”，避免将档案字段误读为已签证据。
- 批量弹窗显示状态、建议动作、方案匹配、缺失字段和逐人执行结果。
- 只有拥有 `oa:signTask:send` 权限时显示入口；后端仍强制校验当前配置的合同经办人和门店范围。
- 员工批量弹窗只把 `READY_TO_SEND` / `SEND_EXISTING` 任务传给统一批量发送接口；非可发送状态不得作为新任务重复创建。
- 支持仅重试失败项，`ALREADY_SENT` 按成功展示。

## 6. 测试与验证

### 6.1 后端测试

新增或扩展：

- `OaExistingEmployeeOnboardServiceTest`：资料快照、规范编码、状态分流、任务编排、跨方案/legacy 包重复保护、逐项失败隔离。
- `OaSignTaskServiceImplTest`：任务发送幂等、文档/事件/通知只产生一次。
- `OaSignTaskControllerTest`：预览、发起、核验字段、100 人限制、权限、批量发送。
- `OaMapperBindingTest`：最新入职任务及历史入职签约包映射。
- `OaSignTaskManualInitiationAuditTest`：系统外合同核验、操作人、请求号和发起原因进入不可变任务事件，不写入员工可见/可编辑的签约包备注。
- `OaSignTaskMigrationTest`：并发 guard 迁移的生成列、唯一索引、重复数据拒绝和幂等结构。
- system 候选用户映射和 JSON 合约测试：法人主体、试用期、工资字段完整映射，并保证大整数员工 ID 按字符串输出。

### 6.2 前端测试

新增或扩展：

- 员工状态和合同状态互不推断。
- 状态接口失败时禁止发起。
- 已有草稿、发送中、已签、拒签、过期、失败状态不重复首次创建。
- 最多 100 人、明确选择、核验框不得默认选中。
- 创建并发送必须具有任务发送权限，并通过当前合同经办人和组织范围校验。
- 批量结果和仅重试失败项。
- 更新旧的“禁止批量发送”断言，使其只禁止无预览、无审计、前端并发的直接发送方式。

### 6.3 执行命令

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw -pl erp-modules/erp-system -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=SignCandidateReadinessTest,SysUserMapperSourceTest,SysUserListVoJsonTest test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./mvnw -pl erp-modules/erp-oa -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=OaExistingEmployeeOnboardServiceTest,OaExistingEmployeeOnboardControllerTest,OaExistingEmployeeOnboardMapperBindingTest,OaSignTaskManualInitiationAuditTest,OaMapperBindingTest,OaSignTaskServiceImplTest,OaSignTaskOrchestratorTest,OaSignTaskStateMachineTest,OaSignBatchServiceImplTest,OaSignPackagePreflightValidatorTest test

cd erp-ui
node test/hrExistingEmployeeOnboardContract.test.js
npm run build:stage

cd ..
bash scripts/verify-existing-employee-onboard-contract-release.sh
git diff --check
```

本次验证结果：OA 162 项、System 11 项全部通过；前端专项测试与 staging 构建通过；非 Docker 发布契约 4 项通过；`git diff --check` 通过。独立 MySQL 8 实例已验证迁移可重放、重复开放任务会 fail closed、终态会释放唯一约束；上线前仍须在预发布原生 MySQL 5.7 环境演练。

## 7. 兼容性、发布与回滚

- 新增一个仅用于并发约束的 STORED 生成列及唯一索引；业务数据仍复用现有签约包、任务、去重键和通知发件箱。本次不修改 Docker。
- 新接口为增量接口，现有应急建包、单个任务发送和手工签约包接口保留，降低前端灰度风险。
- 发布顺序：备份并检查存量重复任务 → 执行 `erp_oa_sign_onboard_open_guard_20260717.sql` → 部署后端接口与状态保护 → 部署前端员工入口 → UAT。
- 回滚时先隐藏员工档案入口，再回滚新增接口；已创建的草稿、已发送任务和签署证据不得物理删除。
- UAT 至少覆盖：无记录首次发起、已有草稿、签署中、已签、拒签、过期、发送失败、系统外合同核验、批量部分失败和重复提交。

## 8. 验收标准

1. 导入为“已入职/试用/正式”的员工在无 OA 记录时显示“合同未发起”，而不是“已签”。
2. 已有任何入职签约记录的员工不会因方案变化或重复点击被再次创建首次合同。
3. 已有草稿可安全发送；签署中可催签；已签可查看；异常终态进入重发/处理，不重新入职。
4. 批量任务部分失败时，其他成功项保持成功，并可只重试失败项。
5. 重复请求或并发请求不会重复生成文档、任务或通知。
6. 所有目标后端与前端测试通过，且改动不包含 Docker 文件。
