# 手机App与第三方签名接入

## 当前交付边界

现有手机网页已接入Capacitor Android/iOS工程，App使用相同ERP账号和业务接口。消息功能包含系统通知注册、账号绑定/解绑、冷启动点击、手机消息中心和权限状态。新站内消息可通过默认关闭的事务队列发送；已有签约通知继续复用原队列，健康证路由已补齐。

这不是已通过真机通知验收的正式版本。当前没有第三方签名/推送配置，也没有已核验的生产HTTPS入口。普通审批、公告等若尚未产生“我的消息”记录，仍须接入业务消息生产端；封装不会自动把网页上的所有数字转成系统通知。

## 给服务商的需求

可以直接把这一段发给候选服务商：

> 我们需要给现有ERP提供Android和iOS安装与锁屏通知。App技术为Capacitor，当前应用标识为com.erp.mobile。iOS请明确分发方式、维护和续签安排，并支持此应用的生产APNs通知权限，提供匹配的APNs p12证书（含私钥）或p8配置，或说明你们的推送API与SDK。仅提供安装签名无法满足需求。Android需要说明对华为、荣耀、小米、OPPO、vivo等员工机型的离线推送覆盖和所需厂商资质。请先确认这些能力再报价。

Apple手动“信任”针对有效签名的组织内部企业App。第三方可以代开发和配置，但分发方式仍要匹配使用主体。Apple列有App Store、定制App和组织内部企业分发等方式；采用其他公司的共享企业签名不能作为正式长期运行承诺。

官方参考：

- [Apple企业App安装](https://support.apple.com/zh-cn/118254)
- [Apple企业开发者计划](https://developer.apple.com/programs/enterprise/)
- [Apple业务App分发](https://developer.apple.com/business/)

## 网络接入

用户当前网址：`http://8.152.199.39`。本轮候选包使用 `https://8.152.199.39`，该HTTPS入口尚未通过连通性验证，启用前无法完成App登录/接口访问。

需要在原服务器前端配置系统信任的TLS证书并验证 `/prod-api`、文件预览/下载与上传。可以使用正式域名，也可以评估IP证书；Let’s Encrypt现已提供短期IP证书，但必须配置自动续期。没有为本次任务申请证书或修改服务器。

- [Let’s Encrypt IP证书](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)
- [Certbot IP证书与自动续期说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)

网关还需要按实际部署配置允许原生来源 `capacitor://localhost`（iOS）和 `https://localhost`（当前Android配置）的CORS与相应请求头；先核对运行配置，不使用通配符替代来源校验。当前Bearer认证方案保持不变。

## 苹果推送配置

第三方签名包必须保持或协调变更Bundle ID；当前前端、原生工程与后端默认均为 `com.erp.mobile`。如果第三方更换应用标识，必须统一修改并重新构建，不能仅在签名工具里替换。

后端 `erp-system` 从外部运行环境读取以下值，证书和密钥不进入前端、仓库或安装包：

| 项目 | 配置 |
| --- | --- |
| 启用 | `PUSH_APNS_ENABLED=true` |
| 应用标识 | `APNS_BUNDLE_ID=com.erp.mobile` |
| 正式通道 | `APNS_ENVIRONMENT=production` |
| p12证书模式 | `APNS_AUTH_MODE=certificate`、`APNS_CERTIFICATE_PATH`、`APNS_CERTIFICATE_PASSWORD` |
| p8密钥模式 | `APNS_AUTH_MODE=token`、`APNS_TEAM_ID`、`APNS_KEY_ID`、`APNS_PRIVATE_KEY_PATH` |

p12应为该App的APNs推送证书，不能拿安装签名证书替代。两种认证模式显式选择，缺失或无效配置不会降级成无认证发送。

收到最终签名IPA后，在macOS运行：

```sh
python3 scripts/verify_ios_push_ipa.py /absolute/path/signed-app.ipa \
  --bundle-id com.erp.mobile --team-id 服务商提供的TeamID \
  --environment production
```

检查结果仍不能代替手机安装、Apple在线状态和锁屏推送验收。企业/Ad Hoc的有效期、安装对象范围及续签安排须由服务商明确。

## 安卓推送配置

当前实现使用FCM，客户端配置文件为 `erp-ui/android/app/google-services.json`，包名必须匹配。后端需要 `PUSH_FCM_ENABLED=true`、`FIREBASE_PROJECT_ID` 和通过外部环境提供的Google服务账号；默认通知频道是 `erp_messages`。

FCM依赖Google Play服务。本轮没有接入国内厂商SDK，因此未安装Google服务的机型尚不在推送验收范围。选定国内聚合推送服务后，还需要实际接入其SDK和服务端发送适配，不能只换一个配置文件。

- [Capacitor推送协议](https://capacitorjs.com/docs/apis/push-notifications)
- [Firebase Android依赖](https://firebase.google.com/docs/android/android-play-services)

## 新站内消息自动推送

1. 在发布前核对已有通知表及迁移基线，在隔离库验证新增 `sql/erp_system_notification_push_outbox_20260912.sql` 后再发布生产迁移。
2. 服务端及客户端推送配置就绪后启用 `IN_APP_MOBILE_PUSH_ENABLED=true`。默认每30秒调度。
3. 只为开启后新创建的站内消息保存推送任务；不会扫描或群发历史消息。
4. 无设备会明确跳过，禁用/临时故障按策略重试，超过次数或有效期结束。服务商接受发送并不等于手机已经展示。
5. 回滚先关闭新开关并恢复旧服务/客户端包，保留新增表和投递记录。

现有多设备投递是按用户事件聚合：至少一台设备成功即认为该事件已被服务商接受，尚未实现每台设备独立的重试账本。旧OA队列的SENT须结合last_result判断，NO_DEVICE不能解释成手机收到。

## 本地重新打包

使用Node 22～24；Android使用JDK21及API36工具链，iOS使用Xcode。先将真实HTTPS API origin放入进程环境，再运行：

```sh
npm run app:package:android
npm run app:package:ios
```

命令在 `erp-ui` 目录执行。生成的测试APK、未签名APK、未签名IPA和哈希清单位于 `erp-ui/build/mobile-packages/<timestamp>`。源码发生变更会阻止生成完成清单。测试APK使用Debug签名；未签名IPA不能直接安装到iPhone。

## 必须补做的验收

- Android代表机型与iPhone：前台、退后台、锁屏、网络恢复后通知；用户关闭系统通知的提示和恢复。
- 点击通知冷启动、已登录跳转、未登录后跳转、切换账号及退出账号隔离。
- 与实际生产同版本MySQL验证新消息/队列事务回滚、双实例领取及崩溃恢复。
- 正式HTTPS登录、手机页面主要业务、拍照定位、文件预览和下载。
- 第三方签名包续签、推送证书轮换、失效提醒和回滚。
