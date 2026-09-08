# 合同自动化阶段4：入职与续签自动发送 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在证据链、任务状态和五场景草稿稳定后，以影子模式验证规则，并只对低风险标准入职和标准续签逐门店开启自动发送。

**Architecture:** 自动化资格判断是一个无副作用的纯决策服务，每次判断都持久化输入摘要、结论和原因。影子模式记录“如果开启会怎样”，但任务仍进入HR确认。实际模式必须同时通过全局、场景、门店、法律主体和方案版本五级开关，合格任务才从草稿进入待发送；调岗、离职、转正永远被规则层拒绝自动发送。发送由独立调度器领取并使用阶段2状态机执行。

**Tech Stack:** Java 17, Spring Boot 4 Scheduling, MyBatis XML, MySQL 8, System Config, JUnit 5, Vue 2, Element UI, Node source tests.

---

## Task 1: 持久化自动化决策和发布指标

**Files:**
- Create: `sql/erp_oa_sign_auto_send_20260711.sql`
- Create: `docker/mysql/db/erp_oa_sign_auto_send_20260711.sql`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignAutomationDecision.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignAutomationDecisionMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignAutomationDecisionMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignAutoSendMigrationTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 新表字段固定为：`decision_id`、`task_id`、`mode`、`eligible`、`reason_codes`、`input_snapshot_hash`、`plan_version_id`、`document_version`、`application_version`、`decided_time`、`sent_result`、`manual_plan_changed`。
- [ ] `mode` 只允许 `SHADOW`、`LIVE`；唯一索引 `(task_id, mode, input_snapshot_hash)`，相同输入重复判断不增加记录。
- [ ] SQL插入配置默认值，全部关闭：

```text
sign.automation.global.enabled=false
sign.automation.shadow.enabled=false
sign.automation.onboard.enabled=false
sign.automation.renewal.enabled=false
sign.automation.renewal.min-sign-days=7
```

- [ ] 门店、法律主体和方案版本开关不存在时按false，不在SQL中批量预置true。
- [ ] 运行双份SQL比较和mapper测试后提交 `feat: persist signing automation decisions`。

## Task 2: 实现纯自动发送资格判断

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaAutoSendEligibility.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAutoSendEligibilityService.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaAutoSendEligibilityServiceTest.java`

- [ ] 使用表驱动测试固定所有拒绝原因：

```text
SCENARIO_NOT_AUTOMATABLE
GLOBAL_SWITCH_OFF
SCENARIO_SWITCH_OFF
SHOP_SWITCH_OFF
LEGAL_ENTITY_SWITCH_OFF
PLAN_VERSION_SWITCH_OFF
PLAN_NOT_PUBLISHED
PLAN_NOT_STANDARD
RISK_NOT_NORMAL
MANUAL_OVERRIDE
CUSTOM_TEMPLATE
PLAN_CONFLICT
TEMPLATE_HASH_MISMATCH
DOCUMENT_HASH_MISMATCH
DUPLICATE_ACTIVE_TASK
INVALID_EFFECTIVE_DATE
UNSUPPORTED_EMPLOYMENT
UNSUPPORTED_SOCIAL_TYPE
RENEWAL_ENTITY_CHANGED
RENEWAL_CONTRACT_TYPE_CHANGED
RENEWAL_SPECIAL_TERMS
RENEWAL_TOO_LATE
```

- [ ] 资格服务只读取传入的冻结任务/方案/文件/配置快照，不更新任务、不发通知、不访问当前HR编辑表。
- [ ] 入职必须是九类标准匹配、`risk=NORMAL`、无人工覆盖、无自定义条款、已发布标准方案且全部证据hash通过。
- [ ] 续签在上述基础上要求继续在职、法律主体和合同类型不变、无特殊岗位/薪资条款，距离旧合同结束日至少 `min-sign-days`。
- [ ] `REGULARIZE`、`TRANSFER`、`OFFBOARD` 第一条即返回 `SCENARIO_NOT_AUTOMATABLE`；任何配置都不能改变。
- [ ] 所有开关都为true但存在一个拒绝原因时仍不可自动发送；返回完整reasonCodes供HR解释。
- [ ] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaAutoSendEligibilityServiceTest test` 后提交 `feat: evaluate standard auto send eligibility`。

## Task 3: 接入影子模式，不改变人工流程

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationDecisionService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignAutomationDecisionServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java`

- [ ] 当 `sign.automation.shadow.enabled=true` 时，入职/续签草稿生成后运行资格判断并保存 `SHADOW` 决策。
- [ ] 影子模式无论eligible真假都把任务置为 `WAITING_HR_CONFIRM`，不调用send，不发员工通知。
- [ ] HR确认时记录是否更换推荐方案、修改文件、补自定义条款；任一动作把 `manual_plan_changed=true` 或产生对应差异原因。
- [ ] 相同snapshot重复校验只保留一条决策；资料变化产生新snapshot和新决策，旧记录只读。
- [ ] 运行聚焦测试后提交 `feat: record shadow auto send decisions`。

## Task 4: 提供模拟检查和7天发布门

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignAutomationSimulation.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignAutomationGateReport.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationReportService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignAutomationReportServiceTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java`
- Modify: `erp-ui/src/api/oa/signTask.js`
- Create: `erp-ui/src/views/oa/signTask/SignAutomationSettings.vue`
- Create: `erp-ui/test/signAutomationSettings.test.js`

- [ ] 新增接口：

```text
POST /signTask/automation/simulate
GET  /signTask/automation/gate-report?from=yyyy-MM-dd&to=yyyy-MM-dd
PUT  /signTask/automation/settings
```

- [ ] 设置接口要求 `oa:signTask:config`；后端禁止直接开启live，除非最近连续7个完整自然日同时满足：

```text
错误方案命中 = 0
重复任务 = 0
文件验真通过率 = 100%
HR人工更换推荐方案 = 0
发送成功率 >= 99%
```

- [ ] 没有足够7天数据、某天样本为空或指标缺失都视为不通过，不以0错误替代有效观察。
- [ ] 模拟页展示将自动发送、将转人工、资料不完整、方案冲突数量及每条原因；HR先确认模拟结果才能提交开关。
- [ ] 前端不能仅靠禁用按钮保护；直接调用设置接口也必须被服务端发布门拒绝。
- [ ] 运行后端聚焦测试和 `node test/signAutomationSettings.test.js`，提交 `feat: gate signing automation with shadow metrics`。

## Task 5: 实现live决策和独立自动发送处理器

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutoSendProcessor.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskOrchestrator.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDocumentService.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignAutoSendProcessorTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskOrchestratorTest.java`

- [ ] 只有live资格通过时允许 `DRAFT_CREATED -> READY_TO_SEND`；保存 `LIVE` 决策、`confirm_status=AUTO_APPROVED`，confirmedBy保持NULL并记录SYSTEM事件。
- [ ] 处理器每15秒领取最多50条 `READY_TO_SEND`，使用status/version条件更新为 `SENDING`。
- [ ] 领取成功后再次检查总开关和任务输入摘要；开关关闭或摘要变化时回到 `WAITING_HR_CONFIRM`，不能继续发送。
- [ ] 调用阶段2发送服务，成功进入 `PENDING_SIGN` 并创建员工通知；暂时失败按任务重试策略处理，不重新生成新文档版本。
- [ ] 不可重试失败进入 `FAILED` 并产生HR待办。每次自动动作的operatorType为SYSTEM。
- [ ] 并发两个处理器、重复调度和进程在发送后崩溃的测试都只能产生一个包状态变化、一个发送事件和一组去重通知。
- [ ] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaSignAutoSendProcessorTest,OaSignTaskOrchestratorTest test` 后提交 `feat: send eligible onboarding and renewal tasks`。

## Task 6: 增加即时熔断和范围化开关

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationSettingsService.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutoSendProcessor.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignAutoSendProcessorTest.java`
- Modify: `erp-ui/src/views/oa/signTask/SignAutomationSettings.vue`
- Modify: `erp-ui/test/signAutomationSettings.test.js`

- [ ] 开关层级固定为：全局 → 场景 → 门店 → 法律主体 → 方案版本；每一级都必须为true。
- [ ] 设置页面默认先选一个门店和一个方案版本，不提供“一次开启全部门店”快捷操作。
- [ ] 关闭全局开关后，新草稿立即转人工；尚未领取的 `READY_TO_SEND` 条件回退为 `WAITING_HR_CONFIRM`。
- [ ] 已经 `PENDING_SIGN/VIEWED` 的合同不撤回、不失效；员工继续签署。
- [ ] 增加运维只读接口显示最后一次配置刷新时间；配置服务不可用时按全部false处理。
- [ ] 运行聚焦后端/前端测试后提交 `feat: fail closed signing automation switches`。

## Task 7: 影子运行和逐步放量

- [ ] 部署阶段4代码但保持live开关全关，只打开：

```text
sign.automation.shadow.enabled=true
```

- [ ] 连续运行至少7个完整自然日；每天导出gate report，不修改历史决策。
- [ ] 对每条eligible影子任务由HR照常确认；系统记录HR是否换方案、改资料或改文件。
- [ ] 任一硬指标不通过，修复原因后重新开始7天窗口，不拼接修复前后窗口。
- [ ] 通过后只开启一个低风险门店、一个法律主体、一个已发布标准入职方案；续签仍关闭。
- [ ] 观察至少20个有效样本或7天（取较长者），再逐个扩大入职方案范围。
- [ ] 入职稳定后，以相同流程开启一个标准续签方案；不因入职通过而跳过续签独立影子报告。
- [ ] 每次放量前保存配置快照、gate report和回滚负责人；不保存员工敏感明细到代码仓库。

## Task 8: 阶段4故障和回滚演练

- [ ] 运行：

```bash
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
```

- [ ] 模拟以下故障：配置服务超时、PDF转换失败、文件hash变化、system通知服务不可用、数据库死锁、两个OA实例并发领取。
- [ ] 每种故障均验证：错误合同发送数0、重复任务0、重复通知0、失败原因可解释、可人工接管。
- [ ] 人工在发送前修改员工关键字段，确认旧自动决策失效并转HR确认。
- [ ] 开启全局后立即关闭，确认未发送任务在一个调度周期内停止；已发送合同正常完成。
- [ ] 验证转正、调岗、离职在所有开关为true时仍不能自动发送。
- [ ] 回滚只关闭开关和处理器；不删除决策、任务、文件、事件或通知记录。
