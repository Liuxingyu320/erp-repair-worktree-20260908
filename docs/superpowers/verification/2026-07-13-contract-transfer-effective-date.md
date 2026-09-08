# 合同自动化——调岗生效日期验收记录

**日期：** 2026-07-13（Asia/Shanghai）

**分支：** `codex/contract-transfer-effective-date-20260713`

**基线：** `3d2f7b5b merge: integrate contract signing automation`

**结论：** 调岗日期权威校验、当天原子生效、历史高风险补录、生命周期 Outbox、OA 调岗规则、冻结签约快照、唯一 HR 权限和桌面端交互均已实现。System 与 OA 全量测试、MySQL 5.7 事务/并发测试、调岗前端专项和生产构建通过。前端全量仍有 9 个基线失败，均位于本分支未修改的既有通知、移动端或原生同步文件，因此不将前端全量描述为通过。

## 已交付业务口径

- 业务自然日由后端按 `Asia/Shanghai` 计算，并通过业务日期接口提供给前端；浏览器不以设备时区推导“今天”。
- 未来生效日期返回“未来日期的调岗暂不能确认，请在生效当天操作”，且在获取锁和任何写入前拒绝；不更新员工、不写生命周期动作/Outbox、不生成 OA 任务或通知。
- 当天调岗在一个事务内锁定员工档案，更新部门、岗位、组织和签约字段，保存 before/after 快照、动作、审计信息和合同事件 Outbox。
- 历史调岗要求同一个唯一 HR 提交结构化二次确认；服务端逐项核对员工、原/新组织岗位、生效日、操作日、固定风险说明和补录原因。业务生效日期与实际确认时间分别持久化。
- `requestId` 重放返回原动作；同员工同生效日的不同请求稳定冲突。真实 MySQL 行锁验证相同请求并发只生成一个动作和一个 Outbox。
- OA 统一使用 `TRANSFER` 场景规则；在已发布且启用的方案版本中匹配调岗模板。无需员工确认的纯汇报线变更可跳过，需要资料但缺失时进入单 HR 任务中心；历史标识和业务生效日期冻结到任务及签约包。
- 沿用已有事件链、任务中心、通知 Outbox 和文件证据链；未增加审批人、定时生效任务、延迟队列或未来调岗扫描器。

## 后端自动化验证

### System 全量与真实 MySQL 5.7

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-system -am test
```

结果：`erp-system` 621 项测试通过，0 失败、0 错误、0 跳过。

其中调岗专项包括：

- `HrEmployeeTransferLifecycleTest`：14 项，覆盖昨天/今天/明天、月末、年末、闰日、服务端与设备时区差异、历史确认、无权限、审计和幂等。
- `HrEmployeeTransferTransactionTest`：5 项，使用官方 `mysql:5.7.44` 镜像，覆盖 Outbox 故障整体回滚、同请求并发重放、不同请求并发冲突、提交状态重建、权限迁移和运行时同步。
- 集成测试将 `erp_hr_transfer_effective_date_20260713.sql` 连续执行两次，验证 MySQL 5.7 迁移幂等；权限包装过程再次执行后仍只有一条角色授权和一条受管授权记录。

### OA 全量

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn -pl erp-modules/erp-oa -am test
```

结果：`erp-oa` 600 项测试通过，0 失败、0 错误、0 跳过。

其中 `TransferSignScenarioRuleTest` 10 项、`TransferSignPersistenceContractTest` 2 项，覆盖未来事件防御性拒绝、冻结前后快照、历史生效日、缺资料任务、无需签约跳过、方案版本与模板匹配、持久化字段和兼容性。

## 前端验证

运行时：Node.js `24.14.0`、npm `11.11.0`，满足仓库 `>=22 <25` 约束。

### 合同与调岗专项

```bash
cd erp-ui
node scripts/run-node-tests.cjs \
  contractAutomationBaseline.test.js \
  hrEmployeeMasterFields.test.js \
  hrEmployeeTransferEffectiveDate.test.js \
  signPackageModule.test.js \
  signTaskCenter.test.js
```

结果：5 个测试文件通过，0 失败。覆盖未来禁用与文案、当天确认、历史风险标记和二次确认、结构化请求、后端失败不伪造成功、HR 页面不显示 Hash、员工/任务刷新和调岗模板业务元数据。

### 全量 Node 测试

```bash
cd erp-ui
npm test
```

结果：142 个测试文件运行，133 个通过，9 个失败。以下 9 项的测试及其断言输入文件相对基线 `3d2f7b5b` 均未被本分支修改：

- `headerNoticeReadAll.test.js`：既有 HeaderNotice “全部已读”异步断言。
- `laborContractModule.test.js`：既有移动合同页 44px 触控目标断言。
- `mobileAppShell.test.js`：工作树缺少已同步的 Android `public/index.html` 产物。
- `mobileAuthEntryPages.test.js`：既有选店页预览组织数据断言。
- `mobileInventoryWorkbench.test.js`：既有仓库相机扫描能力文案断言。
- `mobileProductionDataIsolation.test.js`：既有选店页 `previewDeptTree` 断言。
- `mobileProfileMaintenance.test.js`：既有移动头像 44px 触控目标断言。
- `mobileProgressiveRedesign.test.js`：既有移动工作台滚动根节点断言。
- `unifiedTodoBusinessFocus.test.js`：既有移动调拨退回编辑动作断言。

本次曾触发的员工列表测试沙箱和签约模板占位符两项回归已经修复，并包含在上述 5/5 专项通过结果中。

### 生产构建

```bash
cd erp-ui
npm run build:prod
```

结果：前端密钥扫描通过，Vue 生产构建成功。保留一个既有性能告警：`app` 入口约 1.68 MiB，略高于 1.66 MiB 推荐值；不影响本次构建产物生成。

## 静态与迁移验证

```bash
git diff --check
cmp -s \
  sql/erp_hr_transfer_effective_date_20260713.sql \
  docker/mysql/db/erp_hr_transfer_effective_date_20260713.sql
```

结果：diff 格式检查通过；部署目录和 Docker 初始化目录中的两份迁移 SQL 逐字一致。

## 数据库变更与上线顺序

新迁移增加：

- `sys_hr_lifecycle_action.actual_confirm_time`、`risk_confirmation_json`、`historical_reason`。
- `oa_sign_task.before_snapshot_json`、`after_snapshot_json`、`business_effective_date`、`historical_supplement`。
- `oa_sign_package.transfer_effective_date`、`historical_supplement`、`before_dept_name_snapshot`、`before_post_name_snapshot`。
- `hr:employee:transfer` 权限及 `sync_sign_hr_permissions_with_transfer()` 包装过程；包装过程先调用既有 `sync_sign_hr_permissions_with_plan()`，再同步调岗权限。

建议上线顺序：

1. 备份数据库，并确认阶段 1、阶段 2、生命周期动作和签约方案版本迁移已经完成。
2. 执行 `sql/erp_hr_transfer_effective_date_20260713.sql`；该脚本依赖既有 `sync_sign_hr_permissions_with_plan()`，因此必须晚于签约方案版本迁移。
3. 先部署 System 与 OA 服务，再部署前端静态资源；部署后重新保存或核对唯一 HR 配置，使运行时权限同步走新包装过程。
4. 用真实唯一 HR 账号分别验收当天调岗和历史补录，并核对员工档案、动作、Outbox、OA 任务/签约包和通知业务键。

## 已知限制与上线门禁

- 按已确认口径，未来调岗不会保存待生效任务，也不会自动调度；HR 必须在生效当天操作。
- 当前代码库只有桌面 HR 员工档案调岗入口，移动端没有同类入口，因此没有新增一套重复的移动调岗流程。
- 系统没有权威的“自定义薪资项”结构化字段，本次只冻结现有基础工资、岗位工资、驻外补贴、绩效工资、工资总额和薪资版本，不推断不存在的薪资项。
- 法人主体仍来源于现有员工档案字段，没有可供强制选择的统一法人主数据表；服务端会冻结并比较 ID、编码和名称，但上线前应核对目标数据质量。
- 尚未使用真实 HR/员工账号、真实模板文件和通知终端完成端到端签署；这是部署环境门禁，不由自动化测试替代。
- 阶段 3 本次只交付“调岗”切片，不代表其他未完成场景、阶段 4 自动发送、阶段 5 统一合同中心或阶段 6 运营治理已经完成。
- 前端全量的 9 个基线失败和入口体积告警仍需另行治理；它们未被纳入本次调岗范围，也未被描述为通过。
