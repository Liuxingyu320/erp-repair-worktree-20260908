# 合同签约 Release A 运维手册

本手册覆盖人工签约 Release A 的备份、迁移、后端/前端切换、灰度、观测和前向回退。生产中的每个命令都必须由已授权的发布人根据实际主机、数据库和密钥管理约定执行；不在命令行、文档或证据 JSON 中写入密码、token、签名图片、合同正文或员工敏感值。

## 1. 发布前置门

1. 冻结提交和制品哈希，运行 `bash scripts/verify-contract-signing-release.sh --static`，保留完整输出。
2. 确认 UAT 证据验签通过，错误合同/重复任务/重复通知均为 0，新文件验真率为 100%。
3. 确认专用非管理员 HR、至少两个组织范围、正式候选模板/五场景方案、法律主体和有效印章均有可追溯审批。
4. 记录生产 MySQL 精确版本。如为 MySQL 5.7，先在获授权的原生、非虚拟化 5.7 验收库完成双跑和核心查询；该项是当前尚需外部执行的强制门禁。MySQL 8、Docker/Testcontainers 或静态证据不能代替。
5. 检查 Nacos、环境变量和前端构建参数：`oa.sign.expiry.enabled=false`、`oa.sign.reminder.enabled=false`、`oa.sign.emergency-create.enabled=false`、`VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED=false`，自动发送条件为 false。

## 2. 备份和哈希证据

在迁移前创建只读快照目录，目录名至少包含发布 ID、UTC 时间和数据库主机别名。使用受控的 MySQL option file 或密钥管理器，不把凭据放在 shell 历史。至少备份：

- 菜单/角色/经办人配置：`sys_menu`、`sys_role`、`sys_role_menu`、`sys_user_role`、`sys_sign_hr_state`、`sys_sign_hr_menu_grant`、包含 `sign.hr.user-id` 的 `sys_config`。
- 签约业务/证据：`oa_sign_task`、`oa_sign_package`、`oa_sign_package_document`、`oa_sign_task_event`、`oa_sign_notification_outbox`、最终确认及其文件映射表。
- 模板/方案：`oa_sign_template`、`oa_sign_plan`、`oa_sign_plan_version`、`oa_sign_plan_version_template`。
- 主体/印章：`sys_legal_entity`、`sys_dept`、`oa_company_seal_config`。
- 通知：`sys_user_notification`、已存在时的 `sys_user_push_delivery`。

备份须使用单事务/一致性快照选项，并将 `SHOW CREATE TABLE`、`SELECT VERSION()`、各表行数和迁移前角色—菜单映射导出到同一证据目录。对目录中每个文件生成 SHA-256，再对哈希清单本身生成 SHA-256；将该值写入发布单，不把备份或数据 dump 提交到仓库。

特别保留迁移前 `4520–4526`、完整旧任务入口 `4600` 和当前角色的关系快照，以便证明 package-only 角色没有被额外授予 `9650`。

## 3. 迁移执行

使用 [contract-signing-migrations-20260716.list](../../scripts/contract-signing-migrations-20260716.list) 的顺序，不使用 glob，不改名，不从 `docker/mysql/bootstrap-files.list` 代执。每个 SQL 执行前：

1. 复算 `sql/` 和 `docker/mysql/db/` 的 SHA-256，必须同时等于 manifest。
2. 在 `information_schema` 核对 manifest 中该步的 `requiresTables/requiresColumns/requiresColumnDefinitions/requiresIndexes`；数量、类型或索引不同立即停止。菜单/权限步还必须证明 `oa_sign_notification_outbox.payload_json` 是非空 JSON 类型且全部载荷有效，因为 HR 改派会按 payload 路由重定向未完成通知。
3. 记录执行人、目标 schema、开始时间、SQL 哈希和执行结果。
4. 执行后立即跑该 SQL 内置断言和下一步前置检查；任一失败都不继续。

顺序固定为：菜单/权限修复 → 生命周期/截止 → 文件策略快照 → System push 幂等账 → 后端 → 专用非管理员 HR 权限验收 → 前端。

隔离库必须连续跑两遍同一哈希的迁移，第二遍用于验证幂等和断言。生产维护窗口只按经审批的账本执行一次；不通过重复盲跑来掩盖第一次失败。

## 4. 角色与权限验证

后端就绪后、前端发布前，用专用非管理员 HR 执行权限矩阵。必须核对：

- 迁移前只持有精确旧签约包 `4520` 的角色，迁移后保留 `4520–4526`，可补齐 OA 父节点 `3000`，但没有 `9650`。
- 只有迁移前完整匹配旧任务入口 `4600` 的角色才获得 `9650`。不以 ID 范围、同名菜单、子权限或 package 权限推断。
- 专用 HR 可访问自己的组织范围，不可访问第二组织；返回不得泄露记录是否存在。
- 公司主体、印章查看/编辑和技术证据均是独立权限，不因具有任务中心入口自动获得。

管理员成功不能作为任何一项权限验收的替代。

## 5. 灰度与观测

首批只给一个低风险组织的测试/内部员工开放。所有自动开关保持 false，每个样本由 HR 人工选择方案、复核主体/印章并发送。至少观察 3 个完整样本，每个都覆盖文件加载/阅读、首签、主体/印章、最终文件阅读、最终确认、下载和验真。

持续监控：

- 任务/签约包状态不一致、无截止策略的活跃记录、拒签/过期原地复活、缺少替代链接。
- 文件哈希/版本/策略快照不一致；发现时只告警并阻断/熔断，不改写预期哈希。
- 通知 Outbox 的 `RETRY/DEAD/SENDING` 积压、分渠道结果和幂等键冲突。
- `sys_user_push_delivery` 的 `RETRY/DEAD`、长时间 `SENDING` 和同 business key 载荷哈希冲突。持久账本降低常规超时重复，但不宣称第三方 provider 具有端到端 exactly-once；provider 已成功而本地未标记 `SENT` 的崩溃窗口仍需人工核对。

任一错误合同、任务/通知重复、越权或哈希不一致都立即停止扩量。

## 6. 停止扩量与回退

回退第一动作是隔离新流量，而不是删除数据：

1. 停止扩量，关闭应急建包、过期和提醒调度器；确认自动发送仍为 false。
2. 从本次灰度角色撤回新 `9650–9669` 映射，不删除菜单表行，不批量撤回其他角色。
3. 保留旧签约包角色及 `4520–4526`。不重新启用已知冲突的 `4600–4611` 签约节点；如需兼容入口，只恢复保留的 `4520` 旧签约包路由可见性。
4. 已经进入签约的包由 HR/法务按当前证据人工处置；不把终态改回待签，不重写文件或哈希。
5. 保留已发送、已阅读、已签、拒签、过期、替代版本、事件、通知 Outbox、用户通知和 push ledger。
6. 数据库只做向前兼容修复。存在业务数据时不 `DROP` 列/索引/表，不用迁移前整表 dump 覆盖维护窗口后的新证据。

只有在前向修复不可行、业务负责人/法务/DBA 共同批准且能证明不会丢失新证据时，才能从经 SHA-256 验证的备份做定向恢复。

## 7. 交接记录

交接必须记录：发布 ID/提交/制品哈希、数据库版本、备份 manifest 哈希、四个 SQL 哈希与执行时间、后端/前端版本、专用 HR 角色 ID（不记录凭据）、灰度组织、3 个样本证据 ID、监控结果、异常与决策人。测试人和法务审核人不能只是同一个人。
