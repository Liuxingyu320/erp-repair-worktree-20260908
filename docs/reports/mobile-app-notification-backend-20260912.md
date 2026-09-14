# 手机 App 消息推送后端交接（2026-09-12）

仅改动「手机App封装工作副本_2026-09-12」。未改原修复工作副本、未执行数据库迁移、未连接生产或真实推送服务、未提交代码。

## 本次完成

- 新增 IN_APP 消息与可选推送待发送记录同事务提交；独立 writer 使原生推送网络请求不被包进消息写入事务。
- 默认关闭 `erp.push.in-app.enabled`；仅开启之后本次成功插入的新消息入队，重复业务键不补推历史消息。
- 新 outbox 使用版本号领取，5 分钟过期领取可恢复；最多 12 次投递尝试，30 秒开始退避、单次最大 30 分钟；任务 24 小时过期，结束后不自动补发。
- 原签约消息保留业务键和载荷，健康证将标准 IN_APP 业务键映射到其已有 MOBILE_PUSH 业务键，共用已有 system 幂等账本。未知站内消息业务路由降为当前账号消息中心。
- 所有推送 data 包含由后端目标用户生成的 `recipientUserId`。健康证输出 `employeeId`（来自源 `userId`）和 `certificateId`；通用 `USER_NOTIFICATION` 可带 `notificationId`。不允许路由参数覆盖接收人，不转发任意 URL/path。
- APNs 支持明确选择 p8 token 或 p12 certificate，密钥首次实际投递才读取；可见通知设置 ALERT 类型、IMMEDIATE 优先级和声音。
- FCM 继续发送 notification+data，可见通知设置 Android HIGH 优先级、`erp_messages` 通道和默认声音。
- DISABLED 不再视为已投递；NO_DEVICE 在 system 幂等账本和新 outbox 记 SKIPPED。
- 原来只放在 local profile 下的推送环境变量映射已上移到公共配置段。

## 外部配置

先应用 `erp_system_notification_push_outbox_20260912.sql`。三份镜像位于 sql、docker/mysql/db、erp-system 的 resources/db/migration，已加入 bootstrap-files 清单。本轮没有执行。

- 新站内消息推送开关：`IN_APP_MOBILE_PUSH_ENABLED=true`，默认为 false。
- 调度间隔：`IN_APP_MOBILE_PUSH_DISPATCH_DELAY_MS=30000`。
- APNs 公共：`PUSH_APNS_ENABLED=true`、`APNS_BUNDLE_ID`、`APNS_ENVIRONMENT=production|sandbox`。
- p8：`APNS_AUTH_MODE=token`（默认）、`APNS_TEAM_ID`、`APNS_KEY_ID`、`APNS_PRIVATE_KEY_PATH`。
- p12：`APNS_AUTH_MODE=certificate`、`APNS_CERTIFICATE_PATH`、`APNS_CERTIFICATE_PASSWORD`（空密码允许）。必须是包含私钥的、与本 App 匹配的 APNs 推送证书；仅 App 安装签名证书不能代替。
- Android 当前提供商仍为已有 FCM：`PUSH_FCM_ENABLED=true`、`FIREBASE_PROJECT_ID`、Application Default Credentials。没有引入国内厂商 SDK，国内机型覆盖尚未验证。

## 本地验证

2026-09-12 18:17（Asia/Shanghai）最后一轮：10 个定向测试类，75 测试，0 失败、0 错误、0 跳过。由 Surefire XML 按类名唯一计数。日志：`mobile-app-notification-backend-20260912.log`。

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./mvnw -pl erp-modules/erp-system -am '-Dtest=SysUserNotificationServiceImplTest,SysUserNotificationControllerTest,PushRouteDataTest,ApnsAuthenticationTest,ApnsPushDeliveryClientTest,ApnsPushDeliveryClientWiringTest,PushDeliveryClientSpringWiringTest,FirebasePushDeliveryClientTest,InAppNotificationWriterTest,InAppPushOutboxDispatcherTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

覆盖：通知收件人隔离、健康证和通用路由白名单、p8/p12 配置选择、APNs/FCM 可见通知协议、Spring writer 事务提交/回滚边界、重复消息不回填、双通道相同载荷哈希、CAS 领取失败、崩溃回收、禁用/无设备状态区分、重试上限、过期、MyBatis 映射绑定与 SQL 镜像一致性。

## 证据范围和兼容边界

- 上述事务验证使用 Spring 代理与 mock mapper，尚未做真实 MySQL 回滚/多实例 SQL 并发验收，也未做 Android/iOS 锁屏通知真机验收。
- 新闭环覆盖实际调用 targeted IN_APP publish 的消息；没有为仅展示在待办聚合中的审批/采购事项新建业务事件，也没有改公告生产流程。
- 原有推送账本按用户和事件聚合，至少一台设备成功即视为 DELIVERED；没有新增每设备幂等和重试账本。
- Provider 返回 DELIVERED 仅证明提供商接收请求，不证明手机已展示或员工已读。进程在提供商接收后、账本落盘前崩溃时仍属于 at-least-once，可能重复。
- NO_DEVICE 保持 `accepted=true` 表示终止自动重试；现有 OA dispatcher 仍将这一已处理请求写其 SENT，但 last_result 为 NO_DEVICE。本次未修改 OA 文件，展示时不能将该状态直接解释为手机已送达。
- 历史 system SENT+DISABLED 记录重放会返回 DISABLED，保留历史账本，不触发历史群发。新增禁用状态记录走 RETRY。
- 回退时先关闭 `IN_APP_MOBILE_PUSH_ENABLED`，保留 outbox 表供审计；不会自动删除任务或回滚已有业务消息。
