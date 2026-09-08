# 合同自动化阶段2：任务中心、单HR确认与通知 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立一个HR即可完整处理的签约任务中心，支持任务幂等、一次确认、失败重试、业务待办、定向站内消息和Android/iOS移动推送。

**Architecture:** `oa_sign_task` 是业务编排事实源，现有签约包是文件和签署执行器。任务状态通过乐观锁条件更新，事件表保留hash链；待办直接从仍需处理的任务计算。通知采用OA outbox，一行一个渠道，系统模块负责定向站内消息和设备推送。单HR由系统配置 `sign.hr.user-id` 指定，同一人补资料、确认、发送，不建立第二级审核。

**Tech Stack:** Java 17, Spring Boot 4 Scheduling, OpenFeign, MyBatis XML, MySQL 8, Firebase Admin Java SDK 9.9.0, Pushy 0.15.6, Vue 2, Element UI, Capacitor Push Notifications 8.4.1, JUnit 5, Node source tests.

---

## Task 1: 建立任务、事件和通知outbox表

**Files:**
- Create: `sql/erp_oa_sign_task_center_20260711.sql`
- Create: `docker/mysql/db/erp_oa_sign_task_center_20260711.sql`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTaskEvent.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignNotificationOutbox.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskEventMapper.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignNotificationOutboxMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskEventMapper.xml`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignNotificationOutboxMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java`

- [ ] 写失败测试，要求SQL双份一致、唯一索引和所有mapper绑定完整。
- [ ] `oa_sign_task` 至少包含：

```sql
CREATE TABLE oa_sign_task (
  task_id bigint NOT NULL AUTO_INCREMENT,
  task_no varchar(40) NOT NULL,
  scenario varchar(20) NOT NULL,
  employee_id bigint NOT NULL,
  shop_dept_id bigint DEFAULT NULL,
  legal_entity_id bigint DEFAULT NULL,
  assigned_hr_user_id bigint NOT NULL,
  source_type varchar(40) NOT NULL,
  source_business_id varchar(64) NOT NULL,
  source_event_version varchar(64) NOT NULL,
  dedupe_key varchar(180) NOT NULL,
  status varchar(32) NOT NULL,
  automation_level varchar(20) NOT NULL DEFAULT 'MANUAL',
  risk_level varchar(20) NOT NULL DEFAULT 'NORMAL',
  plan_version_id bigint DEFAULT NULL,
  package_id bigint DEFAULT NULL,
  confirmed_by bigint DEFAULT NULL,
  confirmed_time datetime DEFAULT NULL,
  confirmed_snapshot_hash varchar(64) DEFAULT NULL,
  sign_deadline datetime DEFAULT NULL,
  failure_code varchar(64) DEFAULT NULL,
  failure_detail varchar(1000) DEFAULT NULL,
  retry_count int NOT NULL DEFAULT 0,
  next_retry_time datetime DEFAULT NULL,
  version bigint NOT NULL DEFAULT 0,
  created_time datetime NOT NULL,
  sent_time datetime DEFAULT NULL,
  completed_time datetime DEFAULT NULL,
  cancelled_time datetime DEFAULT NULL,
  PRIMARY KEY (task_id),
  UNIQUE KEY uk_oa_sign_task_no (task_no),
  UNIQUE KEY uk_oa_sign_task_dedupe (dedupe_key),
  KEY idx_oa_sign_task_hr_status (assigned_hr_user_id, status, created_time),
  KEY idx_oa_sign_task_employee (employee_id, scenario, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] `oa_sign_task_event` 保存 `from_status`、`to_status`、`operator_type`、`operator_user_id`、`reason_code`、`reason_detail`、`request_id`、`ip_address`、`user_agent`、`prev_event_hash`、`event_hash`、`created_time`；`request_id` 非空时唯一。
- [ ] `oa_sign_notification_outbox` 保存 `channel`、`recipient_user_id`、`business_key`、`payload_json`、`status`、`retry_count`、`next_retry_time`、`last_result`、`last_error`、`version`；唯一索引 `(channel, recipient_user_id, business_key)`。
- [ ] `oa_sign_package` 新增可空 `task_id`、`confirm_status`、`plan_version_id`；唯一索引 `uk_oa_sign_package_task(task_id)`。
- [ ] 历史签约包不反向生成任务；新字段保持NULL，旧流程继续可用。
- [ ] 运行：

```bash
cmp sql/erp_oa_sign_task_center_20260711.sql docker/mysql/db/erp_oa_sign_task_center_20260711.sql
mvn -pl erp-modules/erp-oa -am -Dtest=OaSignTaskMigrationTest,OaMapperBindingTest test
```

- [ ] 提交：

```bash
git add -- sql/erp_oa_sign_task_center_20260711.sql docker/mysql/db/erp_oa_sign_task_center_20260711.sql \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTask.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignTaskEvent.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignNotificationOutbox.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignTaskEventMapper.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignNotificationOutboxMapper.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignTaskEventMapper.xml \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignNotificationOutboxMapper.xml \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java \
  erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaMapperBindingTest.java
git commit -m "feat: add signing task persistence"
```

## Task 2: 实现显式状态机、事件链与乐观锁

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignScenario.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTaskStatus.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignOperatorType.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskEventService.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/OaSignTaskStateMachineTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskEventServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskOptimisticUpdateTest.java`

- [ ] 写失败测试固定允许转换：

```text
NEW -> VALIDATING
VALIDATING -> NEEDS_DATA | DRAFT_CREATED | NO_ACTION | FAILED
NEEDS_DATA -> VALIDATING | CANCELLED
DRAFT_CREATED -> WAITING_HR_CONFIRM | READY_TO_SEND | CANCELLED
WAITING_HR_CONFIRM -> READY_TO_SEND | NEEDS_DATA | CANCELLED
READY_TO_SEND -> SENDING | WAITING_HR_CONFIRM | CANCELLED
SENDING -> PENDING_SIGN | FAILED
FAILED -> VALIDATING | READY_TO_SEND | CANCELLED
PENDING_SIGN -> VIEWED | SIGNED | REFUSED | EXPIRED | CANCELLED
VIEWED -> SIGNED | REFUSED | EXPIRED | CANCELLED
```

- [ ] 所有未列出的转换抛出 `ServiceException("不允许从 " + from + " 变更为 " + to)`。
- [ ] Mapper条件更新必须返回1：

```sql
UPDATE oa_sign_task
SET status = #{toStatus},
    version = version + 1,
    failure_code = #{failureCode},
    failure_detail = #{failureDetail},
    next_retry_time = #{nextRetryTime}
WHERE task_id = #{taskId}
  AND status = #{fromStatus}
  AND version = #{version}
```

- [ ] 返回0时重新读取；若已达到相同终态则幂等返回，否则抛出“任务已被其他操作更新”。
- [ ] 事件hash输入使用稳定字段顺序和UTF-8，不直接hash任意Map序列化结果；事件插入与状态更新同事务。
- [ ] `operator_type` 只允许 `SYSTEM`、`HR`、`EMPLOYEE`、`ADMIN`。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTaskStateMachineTest,OaSignTaskEventServiceTest,OaSignTaskOptimisticUpdateTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignScenario.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignTaskStatus.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/constant/OaSignOperatorType.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/OaSignTaskStateMachine.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskEventService.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/OaSignTaskStateMachineTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskEventServiceTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskOptimisticUpdateTest.java
git commit -m "feat: enforce signing task state transitions"
```

## Task 3: 实现单HR配置、任务接口和一次确认

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskConfirmRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskRetryRequest.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignTaskDetail.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTaskService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationSettingsService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskServiceImplTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java`

- [ ] `OaSignAutomationSettingsService` 通过现有 `RemoteConfigService` 读取 `sign.hr.user-id`；缺失、非数字或用户不存在时创建任务失败为 `CONFIG_HR_MISSING`，不得通知任意管理员。
- [ ] 新增接口：

```text
GET  /signTask/list
GET  /signTask/{taskId}
POST /signTask/{taskId}/revalidate
POST /signTask/{taskId}/confirm
POST /signTask/{taskId}/send
POST /signTask/{taskId}/retry
POST /signTask/{taskId}/cancel
```

- [ ] 列表/详情要求 `oa:signTask:list/query`，修改/确认/发送/重试分别要求同名权限；所有接口继续按 `OaBaseController.resolveShopDeptId` 和任务 `shop_dept_id` 校验范围。
- [ ] 自动任务的 `assigned_hr_user_id` 固定为配置用户；只有该用户或具有 `oa:signTask:admin` 的系统管理员可处理。公司只有一个HR，不实现领取、转派或双人审核。
- [ ] 确认请求固定为：

```java
@NotBlank
private String documentVersion;

@NotBlank
private String snapshotHash;

@NotBlank
private String confirmText;

@NotBlank
private String requestId;
```

- [ ] 确认短语精确等于 `确认本次签约资料和文件无误`；服务端重新计算员工、组织、日期、薪资、方案版本、模板清单和阅读PDF的规范化摘要，与请求和当前任务摘要一致后才更新。
- [ ] 同一HR可以补资料、生成草稿、确认和发送；只写一次 `confirmed_by/time/snapshot_hash`。任何摘要字段或文档版本变化，清空确认并回到 `WAITING_HR_CONFIRM`。
- [ ] `send` 只调用阶段1已加固的签约包发送能力，成功后任务进入 `PENDING_SIGN`；失败进入 `FAILED` 并保留可解释错误。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignTaskServiceImplTest,OaSignTaskControllerTest,OaSignPackageServiceImplTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskConfirmRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/dto/OaSignTaskRetryRequest.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignTaskDetail.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/IOaSignTaskService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignAutomationSettingsService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageServiceImpl.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTaskServiceImplTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaSignTaskControllerTest.java
git commit -m "feat: add single hr signing confirmation"
```

## Task 4: 暴露签约待办Provider

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoItem.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoSummary.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaTodoController.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaTodoControllerTest.java`

- [ ] 按 [ERP统一待办中心设计](../specs/2026-07-10-unified-todo-center-design.md) 的契约写失败测试。
- [ ] HR待办类型固定：`OA_SIGN_NEEDS_DATA`、`OA_SIGN_HR_CONFIRM`、`OA_SIGN_SEND_FAILED`、`OA_SIGN_REFUSED`、`OA_SIGN_EXPIRED`。
- [ ] 员工待办继续使用 `OA_SIGN_PACKAGE_SIGN`，事实源为签约包 `pending_sign/part_viewed` 且employeeId为本人。
- [ ] 打开任务不消除待办；状态离开对应集合才消除。列表必须同时校验本人、权限和组织范围。
- [ ] 新增 `GET /todo/summary` 和 `GET /todo/list`，不把公告未读数混入业务待办。
- [ ] 运行 `mvn -pl erp-modules/erp-oa -am -Dtest=OaSignTodoProviderTest,OaTodoControllerTest test`。
- [ ] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoItem.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaTodoSummary.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTodoProvider.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaTodoController.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignTodoProviderTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/controller/OaTodoControllerTest.java
git commit -m "feat: expose signing tasks as business todos"
```

## Task 5: 增加系统定向消息与设备令牌接口

**Files:**
- Create: `sql/erp_system_user_notification_20260711.sql`
- Create: `docker/mysql/db/erp_system_user_notification_20260711.sql`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/UserNotificationCommand.java`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/UserNotificationResult.java`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteNotificationService.java`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteNotificationFallbackFactory.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserNotification.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserDeviceToken.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserNotificationMapper.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserDeviceTokenMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserNotificationMapper.xml`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserDeviceTokenMapper.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserNotificationService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserNotificationServiceImpl.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserNotificationController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserNotificationServiceImplTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserNotificationControllerTest.java`

- [ ] SQL创建 `sys_user_notification` 和 `sys_user_device_token`。消息唯一索引 `(user_id, business_key)`；设备唯一索引 `(platform, token_hash)`，数据库保存原token供发送，但日志和响应只显示后6位。
- [ ] 内部接口 `POST /user-notification/inner/publish` 使用 `@InnerAuth`；命令字段固定为 `channel`、`recipientUserId`、`businessKey`、`title`、`body`、`routeType`、`routeParams`。
- [ ] 登录用户接口：

```text
GET    /user-notification/list
GET    /user-notification/unread-count
POST   /user-notification/{id}/read
POST   /user-notification/device-token
DELETE /user-notification/device-token
```

- [ ] 用户只能读/删自己的消息和令牌。注册令牌时服务端使用当前登录用户ID，不接受请求体userId。
- [ ] 同一设备换账号时令牌原子转绑当前用户；退出时禁用当前账号对应令牌。
- [ ] 保留现有 `SysNotice` 公告接口；定向消息不写公告表，也不继承公告的全员可见语义。
- [ ] 运行：

```bash
cmp sql/erp_system_user_notification_20260711.sql docker/mysql/db/erp_system_user_notification_20260711.sql
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserNotificationServiceImplTest,SysUserNotificationControllerTest test
```

- [ ] 提交：

```bash
git add -- sql/erp_system_user_notification_20260711.sql docker/mysql/db/erp_system_user_notification_20260711.sql \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/UserNotificationCommand.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/UserNotificationResult.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/RemoteNotificationService.java \
  erp-api/erp-api-system/src/main/java/com/erp/system/api/factory/RemoteNotificationFallbackFactory.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserNotification.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserDeviceToken.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserNotificationMapper.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserDeviceTokenMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserNotificationMapper.xml \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserDeviceTokenMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserNotificationService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserNotificationServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserNotificationController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserNotificationServiceImplTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserNotificationControllerTest.java
git commit -m "feat: add targeted user notifications"
```

## Task 6: 实现Android FCM和iOS APNs推送适配器

**Files:**
- Modify: `erp-modules/erp-system/pom.xml`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/config/PushNotificationProperties.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/PushDeliveryClient.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/DisabledPushDeliveryClient.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/FirebasePushDeliveryClient.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/push/ApnsPushDeliveryClient.java`
- Modify: `erp-modules/erp-system/src/main/resources/bootstrap.yml`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/push/FirebasePushDeliveryClientTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/push/ApnsPushDeliveryClientTest.java`

- [ ] 加入官方当前Java依赖：

```xml
<dependency>
    <groupId>com.google.firebase</groupId>
    <artifactId>firebase-admin</artifactId>
    <version>9.9.0</version>
</dependency>
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy</artifactId>
    <version>0.15.6</version>
</dependency>
```

Firebase版本与ADC配置以 [Firebase Admin setup](https://firebase.google.com/docs/admin/setup) 为准；Android发送使用 [Firebase Admin FCM send](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk)。iOS使用Pushy的token认证，并遵循 [Apple APNs HTTP/2要求](https://developer.apple.com/documentation/usernotifications/sending-notification-requests-to-apns)。

- [ ] 配置固定为：

```yaml
push:
  fcm:
    enabled: ${PUSH_FCM_ENABLED:false}
    project-id: ${FIREBASE_PROJECT_ID:}
  apns:
    enabled: ${PUSH_APNS_ENABLED:false}
    team-id: ${APNS_TEAM_ID:}
    key-id: ${APNS_KEY_ID:}
    bundle-id: ${APNS_BUNDLE_ID:com.erp.mobile}
    private-key-path: ${APNS_PRIVATE_KEY_PATH:}
    environment: ${APNS_ENVIRONMENT:production}
```

- [ ] FCM凭证只从Application Default Credentials读取；非Google服务器通过 `GOOGLE_APPLICATION_CREDENTIALS` 指向服务账号文件。仓库不保存JSON密钥。
- [ ] APNs私钥只从 `APNS_PRIVATE_KEY_PATH` 读取；仓库不保存 `.p8`。生产和sandbox端点由environment精确选择。
- [ ] 未启用或配置不完整时返回 `DISABLED`，站内消息仍成功；临时网络/5xx为可重试，令牌无效/未注册为永久失败并禁用token。
- [ ] payload只含任务ID、routeType等非敏感路由信息，不包含身份证、薪资、合同正文或hash。
- [ ] 单元测试使用mock Firebase/Pushy边界，不调用公网。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=FirebasePushDeliveryClientTest,ApnsPushDeliveryClientTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-system/pom.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/config/PushNotificationProperties.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/PushDeliveryClient.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/DisabledPushDeliveryClient.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/FirebasePushDeliveryClient.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/push/ApnsPushDeliveryClient.java \
  erp-modules/erp-system/src/main/resources/bootstrap.yml \
  erp-modules/erp-system/src/test/java/com/erp/system/service/push/FirebasePushDeliveryClientTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/push/ApnsPushDeliveryClientTest.java
git commit -m "feat: deliver mobile notifications through fcm and apns"
```

## Task 7: 实现OA outbox去重和重试

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/ErpOaApplication.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationDispatcher.java`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationOutboxServiceTest.java`
- Create: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationDispatcherTest.java`

- [ ] 在OA应用增加 `@EnableScheduling`；调度器每30秒领取到期记录，每批最多100条。
- [ ] 业务事务只插入outbox，不在主事务内调用system服务。每个事件分别创建 `IN_APP` 和 `MOBILE_PUSH` 行。
- [ ] 业务键固定：

```text
SIGN_NEEDS_DATA:<taskId>:<sourceEventVersion>
SIGN_WAITING_HR:<taskId>:<documentVersion>
SIGN_SENT:<packageId>:<documentVersion>
SIGN_REMINDER:<packageId>:<deadline>:24H
SIGN_REFUSED:<packageId>:<eventId>
SIGN_EXPIRED:<packageId>:<documentVersion>
SIGN_COMPLETED:<packageId>:<documentVersion>
```

- [ ] 领取使用 `status/version` 条件更新为 `SENDING`；并发两个实例时只有一个成功。
- [ ] 可重试退避固定为 `1, 5, 30, 120, 360` 分钟；第5次后进入 `DEAD` 并给HR产生 `OA_SIGN_SEND_FAILED` 待办。
- [ ] 参数/权限/无效用户是永久失败；超时、连接失败、HTTP 429/5xx是可重试。
- [ ] 相同业务键重复插入不报错、不重复推送。
- [ ] 运行：

```bash
mvn -pl erp-modules/erp-oa -am \
  -Dtest=OaSignNotificationOutboxServiceTest,OaSignNotificationDispatcherTest test
```

- [ ] 提交：

```bash
git add -- erp-modules/erp-oa/src/main/java/com/erp/oa/ErpOaApplication.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationDispatcher.java \
  erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignTaskServiceImpl.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationOutboxServiceTest.java \
  erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationDispatcherTest.java
git commit -m "feat: dispatch signing notifications reliably"
```

## Task 8: 注册移动设备并处理推送点击

**Files:**
- Modify: `erp-ui/package.json`
- Modify: `erp-ui/package-lock.json`
- Create: `erp-ui/src/api/system/userNotification.js`
- Create: `erp-ui/src/services/pushRegistration.js`
- Modify: `erp-ui/src/store/modules/user.js`
- Modify: `erp-ui/src/main.js`
- Modify: `erp-ui/ios/App/App/AppDelegate.swift`
- Create: `erp-ui/ios/App/App/App.entitlements`
- Modify: `erp-ui/ios/App/App.xcodeproj/project.pbxproj`
- Modify: `erp-ui/android/app/src/main/AndroidManifest.xml`
- Modify: `erp-ui/scripts/verify-capacitor-sync.cjs`
- Create: `erp-ui/test/mobilePushRegistration.test.js`

- [ ] 安装官方Capacitor插件并同步：

```bash
cd erp-ui
npm install --save-exact @capacitor/push-notifications@8.4.1
npx cap sync
```

实现以 [Capacitor Push Notifications](https://capacitorjs.com/docs/apis/push-notifications) 为准。

- [ ] 写失败测试覆盖：仅原生平台注册、Android 13请求权限、拒绝权限不循环弹窗、注册成功上传token、登出禁用token、推送点击跳到员工签约或HR任务。
- [ ] `pushRegistration.js` 只在 `GetInfo` 成功且已获得用户ID后初始化；Web端立即返回。重复初始化先移除旧listener。
- [ ] 上传结构固定：

```js
{
  platform: Capacitor.getPlatform() === "ios" ? "IOS" : "ANDROID",
  token: registration.value,
  appId: "com.erp.mobile",
  deviceName: navigator.userAgent.slice(0, 200)
}
```

- [ ] iOS在Xcode工程启用Push Notifications capability，并在AppDelegate把注册成功/失败事件转发给Capacitor；Android已有 `google-services.json` 条件加载，测试/生产部署时分别注入正确文件。
- [ ] 不提交 `google-services.json`、`GoogleService-Info.plist`、APNs `.p8` 或服务账号JSON；CI在缺失时允许Web构建，但原生发布门必须明确失败。
- [ ] 点击payload只解析白名单 `routeType=OA_SIGN_PACKAGE_SIGN|OA_SIGN_HR_TASK` 和数字ID，禁止把服务端字符串当任意URL打开。
- [ ] 运行：

```bash
cd erp-ui
node test/mobilePushRegistration.test.js
npm test
npm run app:sync
npm run app:verify
```

- [ ] 使用一台Android和一台iOS测试设备完成注册、前台通知、后台通知、点击跳转、换账号转绑和登出禁用。
- [ ] 提交：

```bash
git add -- erp-ui/package.json erp-ui/package-lock.json erp-ui/src/api/system/userNotification.js \
  erp-ui/src/services/pushRegistration.js erp-ui/src/store/modules/user.js erp-ui/src/main.js \
  erp-ui/ios/App/App/AppDelegate.swift erp-ui/ios/App/App/App.entitlements \
  erp-ui/ios/App/App.xcodeproj/project.pbxproj \
  erp-ui/android/app/src/main/AndroidManifest.xml \
  erp-ui/scripts/verify-capacitor-sync.cjs erp-ui/test/mobilePushRegistration.test.js
git commit -m "feat: register mobile devices for signing push"
```

## Task 9: 实现单HR签约任务页面和定向消息入口

**Files:**
- Create: `erp-ui/src/api/oa/signTask.js`
- Create: `erp-ui/src/views/oa/signTask/index.vue`
- Create: `erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue`
- Create: `erp-ui/src/views/oa/signTask/SignTaskConfirmDialog.vue`
- Create: `erp-ui/src/views/system/userNotification/index.vue`
- Modify: `erp-ui/src/layout/components/HeaderNotice/index.vue`
- Create: `erp-ui/test/signTaskCenter.test.js`
- Create: `erp-ui/test/targetedUserNotification.test.js`
- Modify: `sql/erp_oa_sign_task_center_20260711.sql`
- Modify: `docker/mysql/db/erp_oa_sign_task_center_20260711.sql`

- [ ] 页面只提供一个HR工作区，不显示“提交负责人审核”“复核人”“批准人”或任务转派。
- [ ] 顶部指标固定：待补资料、待HR确认、发送失败、已查看未签、即将逾期、员工拒签、本月已完成。
- [ ] 详情顺序固定为：任务基本信息 → 员工与组织资料 → 合同与薪资字段 → 匹配方案 → 文件清单和预览 → 系统校验结果 → 确认发送。
- [ ] 校验显示绿色“可发送”、黄色“需确认”、红色“不能发送”；HR不用查看hash。技术证据折叠沿用阶段1权限。
- [ ] 确认对话框展示员工、场景、合同类型、期限、文件清单、截止时间和风险；输入精确短语后一次确认。
- [ ] HeaderNotice保留公告图标，并增加独立“我的消息”未读数；业务待办数字来自todo store，不与任何消息未读数相加。
- [ ] 菜单SQL创建“合同签约中心”和一组单HR权限，默认只赋给配置的HR角色/用户所在角色；不得创建HR专员、HR负责人两套角色。
- [ ] 运行：

```bash
cd erp-ui
node test/signTaskCenter.test.js
node test/targetedUserNotification.test.js
npm test
npm run build:prod
```

- [ ] 提交：

```bash
git add -- erp-ui/src/api/oa/signTask.js erp-ui/src/views/oa/signTask/index.vue \
  erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue \
  erp-ui/src/views/oa/signTask/SignTaskConfirmDialog.vue \
  erp-ui/src/views/system/userNotification/index.vue \
  erp-ui/src/layout/components/HeaderNotice/index.vue \
  erp-ui/test/signTaskCenter.test.js erp-ui/test/targetedUserNotification.test.js \
  sql/erp_oa_sign_task_center_20260711.sql docker/mysql/db/erp_oa_sign_task_center_20260711.sql
git commit -m "feat: add single hr contract task center"
```

## Task 10: 阶段2端到端验证与开关

- [ ] 配置测试环境唯一HR：

```text
sign.hr.user-id=<测试HR真实用户ID>
sign.automation.global.enabled=false
push.fcm.enabled=false
push.apns.enabled=false
```

- [ ] 运行：

```bash
mvn -pl erp-api/erp-api-system -am -DskipTests install
mvn -pl erp-modules/erp-system -am test
mvn -pl erp-modules/erp-oa -am test
cd erp-ui
npm test
npm run build:prod
npm run app:verify
```

- [ ] 人工创建一条测试任务：缺资料 → 补资料 → 校验 → 生成草稿 → HR一次确认 → 发送 → 员工阅读 → 签署；每个状态只有一条事件。
- [ ] 重放同一个业务键、确认requestId、发送requestId和通知键，数据库行数不得增加。
- [ ] 停止system服务，确认OA outbox重试且主任务不回滚；恢复后站内消息恰好一条。
- [ ] 使用假推送客户端验证退避序列；再用真实Android/iOS设备各发一次无敏感内容的测试消息。
- [ ] 关闭所有push配置，站内消息和待办仍正常；关闭OA调度器时业务任务仍可人工处理。
- [ ] 确认自动发送总开关仍为false，再进入阶段3。
