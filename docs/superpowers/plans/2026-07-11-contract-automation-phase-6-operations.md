# 合同自动化阶段6：运营报表与持续治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让唯一HR和运维能持续看到签约质量、自动化效果、失败原因、文件完整性与恢复能力，并在异常扩大前关闭自动发送。

**Architecture:** 业务明细仍以任务、文件证据和通知outbox为事实源；每日聚合表只保存不含个人敏感信息的指标。独立完整性抽检按风险和随机样本重算hash，健康检查暴露积压与配置状态。临时文件可清理，已签文件和证书永不由清理任务删除。备份恢复通过清单hash和演练报告验证。

**Tech Stack:** Java 17, Spring Boot 4 Actuator/Micrometer, Scheduling, MyBatis XML, MySQL 8, Vue 2, ECharts 6, JUnit 5, Node source tests, shell verification commands.

---

## Task 1: 建立每日指标和完整性抽检记录

**Files:**
- Create: `sql/erp_oa_sign_operations_20260711.sql`
- Create: `docker/mysql/db/erp_oa_sign_operations_20260711.sql`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignDailyMetric.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignIntegrityAudit.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignOperationsMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignOperationsMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignOperationsMigrationTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 每日指标唯一维度为 `metric_date + scenario + shop_dept_id + plan_version_id`。
- [ ] 指标列固定为：任务量、资料完整数、唯一方案命中数、人工降级数、发送成功/失败数、拒签数、逾期数、签署完成数、签署总秒数、自动发送数、自动转人工数。
- [ ] 不保存姓名、手机号、证件号、薪资、文件路径、签名或原始hash到聚合表。
- [ ] 抽检记录保存 `audit_id`、`package_id`、`document_id`、`audit_type`、`result`、`reason_code`、`checked_time`、`expected_hash_masked`、`actual_hash_masked`；只保留hash前6后6位用于运维定位。
- [ ] 双份SQL一致，迁移幂等；运行mapper测试后提交 `feat: persist signing operations metrics`。

## Task 2: 每日聚合并支持可重算

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDailyMetricService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignDailyMetricScheduler.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignDailyMetricServiceTest.java`

- [ ] 每日01:20聚合前一自然日，时区固定Asia/Shanghai；先算到内存/临时结果，再按唯一键upsert。
- [ ] 重跑同一天结果覆盖为事实源当前值，不累加旧聚合，保证幂等。
- [ ] 资料完整率 = 通过全部基础校验任务 / VALIDATING任务；方案匹配率 = 唯一方案命中任务 / VALIDATING任务。
- [ ] 发送成功率 = 进入PENDING_SIGN任务 / 进入SENDING任务；签署时长只统计sentTime和signedTime均非空的任务。
- [ ] 人工降级按reasonCode聚合；同任务多个原因在“任务数”只算一次，在“原因分布”分别计数。
- [ ] 提供管理员重算方法 `rebuild(LocalDate from, LocalDate to)`，单次最多31天。
- [ ] 运行聚焦测试后提交 `feat: aggregate daily signing metrics`。

## Task 3: 建立完整性抽检和临时文件清理

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignIntegrityAuditService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignIntegrityAuditScheduler.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTempFileCleanupService.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignIntegrityAuditServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTempFileCleanupServiceTest.java`

- [ ] 每日03:10抽检：过去24小时全部已签文件 + 历史已签文件1%随机样本 + 所有最近失败后恢复的任务。
- [ ] 对阅读PDF、已签PDF、签名、证书和事件链分别重算；任一不一致记录 `MISMATCH` 并立即产生运维异常，不能自动重写hash。
- [ ] 连续两次抽检文件缺失时自动关闭全局自动发送开关；关闭动作写SYSTEM事件和高优先级HR消息。
- [ ] 临时清理只扫描配置的 `sign-package.storage.temp-path`，删除超过24小时且不属于运行中任务的目录。
- [ ] 清理服务硬性拒绝路径位于正式root、包含 `signed`、`certificate` 或有证据表引用的文件。
- [ ] 使用临时目录测试删除边界和符号链接；运行聚焦测试后提交 `feat: audit evidence and clean staging files`。

## Task 4: 暴露运营指标、异常队列和健康状态

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignOperationsQuery.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignOperationsSummary.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignFailureQueueItem.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignOperationsService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignOperationsServiceImpl.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignOperationsController.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/actuator/OaSignAutomationHealthIndicator.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignOperationsServiceImplTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignOperationsControllerTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/actuator/OaSignAutomationHealthIndicatorTest.java`

- [ ] 接口固定为：

```text
GET  /signOperations/summary
GET  /signOperations/trend
GET  /signOperations/failure-reasons
GET  /signOperations/failure-queue
POST /signOperations/failure-queue/{taskId}/retry
POST /signOperations/metrics/rebuild
GET  /actuator/health
```

- [ ] 报表查看要求 `oa:signTask:report`，重试/重算要求 `oa:signTask:admin`；业务HR默认可看汇总和任务原因，但不可看技术栈、原始payload和hash。
- [ ] Health DOWN条件：证据抽检MISMATCH、自动发送开启但HR配置缺失、文件存储不可写、READY/SENDING最老任务超过30分钟。
- [ ] Health DEGRADED条件：通知outbox DEAD>0、HR事件outbox积压>15分钟、推送关闭或不可用；推送降级不能把签约核心健康直接置DOWN。
- [ ] Micrometer指标固定前缀 `erp.signing.`，至少暴露task.created、task.failed、send.success、send.failed、sign.completed、notification.retry、evidence.mismatch和queue.oldest.seconds。
- [ ] 运行聚焦测试后提交 `feat: expose signing operations health`。

## Task 5: 实现HR运营看板

**Files:**
- Create: `erp-ui/src/api/oa/signOperations.js`
- Create: `erp-ui/src/views/oa/contractCenter/ContractOperationsTab.vue`
- Modify: `erp-ui/src/views/oa/contractCenter/index.vue`
- Create: `erp-ui/test/signOperationsDashboard.test.js`

- [ ] 在合同中心增加“运营报表”页签，仅有report权限时显示。
- [ ] 卡片固定展示：任务量、资料完整率、唯一匹配率、发送成功率、拒签率、逾期率、平均签署时长、自动发送占比。
- [ ] 趋势图支持日期、场景、门店、方案版本过滤；默认最近30天，最大单次范围366天。
- [ ] 降级原因显示业务中文和数量；点击进入已过滤任务列表，不展示原始hash。
- [ ] 异常队列区分可重试与需人工修复；只有admin权限显示“重试”。
- [ ] 空数据明确显示“该时间范围暂无签约数据”，不把0%显示成100%。
- [ ] 运行 `node test/signOperationsDashboard.test.js && npm test && npm run build:prod` 后提交 `feat: add signing operations dashboard`。

## Task 6: 建立备份、恢复和灾难演练手册

**Files:**
- Create: `scripts/backup_signing_evidence.sh`
- Create: `scripts/verify_signing_backup.sh`
- Create: `docs/runbooks/contract-signing-operations.md`
- Create: `erp-ui/test/signingOperationsRunbook.test.js`

- [ ] 备份脚本只接收环境变量：

```text
SIGN_DB_HOST
SIGN_DB_PORT
SIGN_DB_NAME
SIGN_DB_USER
MYSQL_PWD
SIGN_FILE_ROOT
SIGN_BACKUP_ROOT
```

- [ ] 使用 `mysqldump --single-transaction` 备份签约任务、事件、方案版本、包、文档、证据、通知和旧劳动合同表；文件使用只读复制。
- [ ] 生成 `manifest.sha256`，包含数据库dump和所有备份文件相对路径；禁止把密码写入命令行日志或manifest。
- [ ] 验证脚本运行 `sha256sum -c manifest.sha256`，再检查数据库dump包含全部目标表。
- [ ] 手册写明：关闭自动发送、停止dispatcher、数据库恢复、文件恢复、权限恢复、hash抽检、恢复dispatcher、逐步开关的顺序。
- [ ] 每季度在隔离环境恢复一次；随机选择10个已签包验证详情、下载、证书和hash。演练报告不包含员工敏感明细。
- [ ] 脚本测试只使用临时目录和假dump命令；运行 `node erp-ui/test/signingOperationsRunbook.test.js` 后提交 `docs: add signing backup and recovery runbook`。

## Task 7: 模板、方案和失败原因持续治理

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignGovernanceService.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignGovernanceServiceTest.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignOperationsController.java`
- Modify: `erp-ui/src/views/oa/contractCenter/ContractOperationsTab.vue`

- [ ] 每周输出：30天未使用方案、模板hash变化、多个活跃方案重叠、占位符缺失、连续高人工降级原因、长期未处理拒签/失败任务。
- [ ] 任何已发布版本异常只停用新匹配，不修改已发送任务引用的版本。
- [ ] HR可从治理项跳到模板/方案或任务详情；技术问题只向admin展示堆栈引用号。
- [ ] 连续7天人工换方案>0时，对对应方案版本自动关闭live开关但保留影子模式。
- [ ] 连续7天发送成功率<99%或验真率<100%时自动关闭相应门店/方案开关，并记录SYSTEM事件。
- [ ] 运行聚焦测试后提交 `feat: govern signing plans from production outcomes`。

## Task 8: 最终全链路验收

- [ ] 运行：

```bash
mvn -pl erp-api/erp-api-system,erp-api/erp-api-oa -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
npm run app:sync
npm run app:verify
```

- [ ] 验收五类场景、单HR确认、员工签署、拒签、逾期、通知失败、重复事件、并发发送、文件篡改、旧合同查询和开关熔断。
- [ ] 硬性指标：错误合同0、重复任务0、通知重复0、新文件验真100%、自动任务解释率100%、应用/方案/模板版本追溯100%。
- [ ] 运行一次备份和隔离恢复；恢复后的随机10个签约包全部可下载且验真结论一致。
- [ ] 关闭全局自动发送，确认所有未发送任务转HR确认且已发送合同继续签署；再按阶段4门禁重新开启一个低风险范围。
- [ ] 更新总路线图所有阶段状态，并记录最终应用提交、数据库脚本、方案版本和移动端构建号对应关系。
