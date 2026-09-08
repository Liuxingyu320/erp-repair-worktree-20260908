# 合同签约 Release A 发布清单

> 发布 ID：`contract-signing-release-a-20260716`
> 执行策略：`manual-phased`
> 当前状态：开发完成候选；未获得 UAT/法务/业务签认和原生 MySQL 5.7 证据前不得开放生产。

## 发布边界

Release A 只交付人工签约闭环：生命周期任务、截止时间、拒签/过期/替代版本、不可变文件策略、主体/印章、通知 Outbox 和移动推送幂等账。自动发送、统一历史合同中心和报表属于后续 Release，不随本批次开启。

用户确认的角色边界是强制发布契约：

- 保留旧“签约包”路由、角色和菜单 `4520–4526`，不删除、不禁用。
- 仅持有旧签约包权限的角色只可补齐 OA 父节点 `3000`，不得因签约包权限获得任务中心入口 `9650`。
- `9650` 只能由迁移前完整匹配旧签约任务入口 `4600` 的角色获得；历史冲突 ID 或子权限不能作为授权证据。

## 受控迁移

唯一顺序由 [contract-signing-migrations-20260716.list](../../scripts/contract-signing-migrations-20260716.list) 定义，哈希由 [contract-signing-release-20260716.json](../../scripts/contract-signing-release-20260716.json) 定义。每个 SQL 在 `sql/` 和 `docker/mysql/db/` 必须逐字节一致。

| 步骤 | 迁移 | SHA-256 | 执行前最低检查 |
| --- | --- | --- | --- |
| 1 | `erp_oa_sign_menu_permission_repair_20260716.sql` | `a46dcc124db641bc609c063879e440ca1cd1520ed9e489a0575a7ab4d41025e8` | `sys_menu/sys_role/sys_role_menu/sys_user_role/sys_sign_hr_*`、`sys_config`、`oa_sign_task`、改派审计表和通知 Outbox 及 manifest 列/类型/唯一索引指纹完整；Outbox `payload_json` 为非空 JSON 类型且载荷有效；OA 根节点 `3000` 完整；`9650–9669` 无异主占用 |
| 2 | `erp_oa_sign_package_lifecycle_20260716.sql` | `9ca9f8845d1741fea7c1ef203e01ad05379ff9c20ba1583f9b3535e8fec45742` | `oa_sign_package` 和 `oa_sign_task` 及 manifest 列指纹完整，包含双向关联和 `employee_id/shop_dept_id/plan_version_id`；活跃数据截止策略有可审批来源 |
| 3 | `erp_oa_sign_document_policy_snapshot_20260716.sql` | `b4eeabaa02f606258777d09c3a753b45c3be9cf0e18ea1e84d2d5a959c66455a` | 模板、方案版本模板、签约包文件表及列指纹完整 |
| 4 | `erp_system_user_push_delivery_20260717.sql` | `0ffbe86c227144306a0835afafb157a2e2cec7f62f6d82211580c0ecc2646a6d` | `sys_user_notification` 及其 `user/channel/business_key/status` 列完整 |

这是生产维护窗口的手工分阶段账本。Docker 新库 bootstrap 的依赖顺序只用于干净环境，不能代替生产执行记录。

## 交付顺序

1. 冻结发布文件，运行 `bash scripts/verify-contract-signing-release.sh --static`。
2. 对签约、菜单/角色授权、通知、公司主体/印章表做可恢复备份，并生成独立 SHA-256 清单。
3. 在隔离库按上表顺序执行两次，两次均通过后才可进入生产维护窗口。
4. 生产只执行一次已锁定哈希的 SQL，每步记录开始/结束时间、执行人、数据库版本和结果。
5. 发布后端，先由专用非管理员 HR 复核角色和两个组织越权矩阵；管理员成功不计入验收。
6. 权限验收通过后再发布前端。首批只开一个低风险组织和测试/内部范围，至少观察 3 个完整人工样本后再扩大。

## 默认关闭项

下列值必须在候选制品、Nacos/环境变量和运行时配置中逐项复核，未显式审批时全部为 `false`：

- `oa.sign.expiry.enabled`
- `oa.sign.reminder.enabled`
- `oa.sign.emergency-create.enabled`
- `VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED`
- 方案版本 `autoSendCondition.enabled`

Release A 不开启自动发送。过期/提醒调度器即使在后续经审批开启，也必须在生命周期迁移和 UAT 完成后单独留痕。

## 发布阻断项

- 如生产为 MySQL 5.7，必须在获授权的原生、非虚拟化 5.7 验收库重复迁移和核心查询。当前仓库只能声明该项为 `external-pending`；MySQL 8、Docker 或静态检查不能代替。
- 专用非管理员 HR、正式模板/方案、法律主体/有效印章、法务和业务签认缺一不可开放。
- UAT 必须达到错误合同 0、重复任务 0、重复通知 0、新文件验真率 100%。

## 回滚原则

第一动作是停止扩量，关闭签约任务新入口/应急建包/过期与提醒调度，并撤回本次灰度角色的新 `9650–9669` 映射。保留 `4520–4526` 旧签约包角色/路由，不重新启用已知冲突的 `4600–4611` 节点。

已发送、已阅读、已签、拒签、过期、替代版本、通知幂等和推送账本全部保留。不删除业务记录/文件/事件，不在存在业务数据时 `DROP` 列或表；优先前向修复。详细操作见 [contract-signing-operations.md](../runbooks/contract-signing-operations.md)。
