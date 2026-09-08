# 合同签约下一阶段基线记录

> 记录时间：2026-07-14  
> 状态：阶段 0 已完成代码/数据库保护；生产数据库版本和专用合同 HR 仍待正式环境确认  
> 约束：未使用虚拟机、Docker 或 Testcontainers

## 代码基线

- 项目目录：`/Users/liuxingyu/Desktop/备份/ERP-NEW`
- 分支：`7月13号`
- HEAD：`b694659dd2c19dc87377d93eaed6bbbf9a5ac0b7`
- 首次状态检查：303 个已修改、5 个已删除、360 个未跟踪，共 668 条状态记录。
- 改动主要分布：`erp-modules` 380 条、`erp-ui` 177 条、SQL 两个目录 28 条，其余为文档、脚本和检查产物。
- 阶段 1 的核心文件已经包含未提交改造，其中 `OaSignScenarioCodes.java` 和 `OaSignPackagePreflightValidator.java` 尚未被 Git 跟踪。因此不能直接从 HEAD 建立 worktree，否则会丢失当前真实合同基线。
- 本阶段不执行 `git reset`、`git clean`、批量覆盖或自动提交；只对计划列明文件做逐项差异检查。

阶段 1 修改前关键文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `OaSignTemplateType.java` | `ea833c46a5a56644158af86c393951e151bbc8e388261bb8a51f4aed5ab755cc` |
| `OaSignScenarioCodes.java` | `85bfca6792937ec37cc5f6d8d542bc1661da80c86d5e6349e7c2c5670cc79c9e` |
| `OaSignTemplateServiceImpl.java` | `1f4f08033d70ae8934588c85e2363130e412d89e4af67717cbd422caaa3c31a7` |
| `OaSignPlanVersionServiceImpl.java` | `1847b1c041b6ab1e4685b839089b560ba4a1651c8ef29eaff3fd0274d34583e5` |
| `OaSignPackagePreflightValidator.java` | `8b9b55b9f5cc606660f3607da45cad59e795362fc5be928820dabfd337a45fa7` |
| `erp-ui/src/views/oa/signPackage/index.vue` | `7ec6c34a9c25c70a749c82c8f9a824e5c6c2692629fd873c58338232c6210269` |
| `erp-ui/test/signPackageModule.test.js` | `1d91d7832749210c3be8ba2a0b446df81a16535946b444f90cba725eb9edd90a` |

## 工具链基线

- Java：Homebrew OpenJDK 17.0.18。
- Maven：3.9.14。
- Node：24.14.0。
- MySQL：8.0.45。
- Maven 默认环境错误地使用 Java 25.0.2；后续命令必须显式使用：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn <arguments>
```

## 数据库保护

- 源库：`BossERP_stock_state_75c59ee`。
- 源库大小：约 25.08 MiB。
- 完整阶段前备份：`/Users/liuxingyu/Desktop/入离调转20260702-改造版-20260713/05_本地数据库备份/BossERP_stock_state_75c59ee-contract-phase0-before-20260714.sql`
- 备份大小：9,489,068 字节。
- 备份权限：`600`。
- 备份 SHA-256：`6853880d96390f11c21272458233d00b62211d2bbcf1b856e6a1dc5c2680cfd9`。
- 本机隔离 UAT 库：`BossERP_sign_uat_20260714`。
- 源库和 UAT 库均为 454 张表；导入错误 0。
- UAT 关键数据：33 个模板、0 个方案、6 个签约包、43 个包内文件、0 个生命周期动作、0 个 HR 签约事件 Outbox。
- 后续结构脚本和场景联调先在 UAT 库执行，不直接向源业务库写测试动作。

## 运行状态

- 2026-07-13 最终验收记录：合同 Java 524 项、前端 176 项通过，36 个唯一归档文件哈希一致，OA 健康为 `UP`。
- 本记录创建时 9204 端口未监听，属于服务未启动；代码修改完成后需使用 Java 17 启动并生成新的健康记录。

## 尚未完成的阶段 0 外部确认

- 项目资料同时出现 MySQL 5.7 和 MySQL 8 目标口径。本机只能证明 MySQL 8.0.45；若生产为 5.7，必须在授权的非虚拟化 5.7 测试库补迁移和查询兼容验证。
- 当前 `sign.hr.user-id=1` 指向通用管理员。生产上线前必须指定专用合同 HR，并验证账号、权限和数据范围。

