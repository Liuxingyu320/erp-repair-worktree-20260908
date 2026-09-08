# 统一待办业务口径收口实施记录

> 实施日期：2026-07-13  
> 当前状态：代码、迁移/回滚脚本和自动化回归已完成；未对目标生产数据库执行迁移，未代替业务人员进行真实角色 UI 验收。

## 1. 已完成的业务收口

1. OA 采购申请退出统一待办：删除 `OA_PURCHASE_APPROVAL` 和 `OA_PURCHASE_RETURNED`，桌面/移动待办路由同步移除，写入接口由 `feature.oa.purchase.enabled` 以缺省关闭方式保护。
2. 门店要货归口到库存调拨：`warehouse`、`store_return`、`cross_store` 共用 `inv_transfer_order` 事实源和 5 类阶段待办，不因“仓库管理/进销存”入口不同重复提醒。
3. 库存盘点补齐真实责任人、截止时间和写入权限校验；执行待办以同一类型表达待执行、即将到期和已逾期，避免重复计数。
4. 签约任务 5 类状态并入 OA 唯一聚合链路，修复 `type` 精确过滤和前端 `rows` 结果读取。
5. 健康证待审核和驳回重提进入 system/HR 待办，路由能精确带入审核状态或证件记录。
6. 三个 provider 统一支持 `TodoQuery.type`，非法类型值会被契约校验拒绝。

## 2. 最终进入统一待办的 33 类事项

### inventory（18 类）

| 业务 | 待办类型 | 说明 |
| --- | --- | --- |
| 调拨 | `INV_TRANSFER_APPROVAL` | 调拨待审批 |
| 调拨 | `INV_TRANSFER_DELIVER` | 来源仓/门店待发货 |
| 调拨 | `INV_TRANSFER_RECEIVE` | 目标仓/门店按发货批次待收货 |
| 调拨 | `INV_TRANSFER_DISCREPANCY` | 收货差异待处理 |
| 调拨 | `INV_TRANSFER_RETURNED` | 审批退回后由原发起人修改重提 |
| 盘点 | `INV_STOCK_CHECK_EXECUTE` | 指定盘点人的待执行/即将到期/已逾期三态 |
| 盘点 | `INV_STOCK_CHECK_APPROVAL` | 盘点差异待审批 |
| 盘点 | `INV_STOCK_CHECK_RETURNED` | 盘点驳回后修改重提 |
| 盘点 | `INV_STOCK_CHECK_RESTART` | 盘点作废后重新获取快照并复盘 |
| 库存风险 | `INV_OUT_OF_STOCK` | 缺货 |
| 库存风险 | `INV_LOW_STOCK` | 低库存 |
| 库存采购 | `INV_PURCHASE_QC` | 采购到货质检，不属于 OA 采购申请 |
| 库存采购 | `INV_PURCHASE_RECEIVE` | 采购收货，不属于 OA 采购申请 |
| 销售 | `INV_SALES_NOTICE_CREATE` | 销售发货通知待创建 |
| 销售 | `INV_DELIVERY_EXECUTE` | 待执行发货 |
| OE | `INV_OE_REPLENISHMENT` | OE 补货处理 |
| 退货 | `INV_PURCHASE_RETURN_CONFIRM` | 采购退货待确认 |
| 退货 | `INV_SALES_RETURN_CONFIRM` | 销售退货待确认 |

### OA（8 类）

| 业务 | 待办类型 | 说明 |
| --- | --- | --- |
| 固定资产 | `OA_FIXED_ASSET_REPLENISHMENT_DEAD` | 补货通知死信/异常处理 |
| 合同 | `OA_LABOR_CONTRACT_SIGN` | 员工劳动合同待签 |
| 签约包 | `OA_SIGN_PACKAGE_SIGN` | 签约包待签 |
| 签约任务 | `OA_SIGN_NEEDS_DATA` | 待补资料/重新校验 |
| 签约任务 | `OA_SIGN_HR_CONFIRM` | 待 HR 确认 |
| 签约任务 | `OA_SIGN_SEND_FAILED` | 任务发送失败或通知死信待重试 |
| 签约任务 | `OA_SIGN_REFUSED` | 拒签待处理 |
| 签约任务 | `OA_SIGN_EXPIRED` | 签约过期待处理 |

### system/HR（7 类）

| 业务 | 待办类型 | 说明 |
| --- | --- | --- |
| 员工资料 | `HR_PROFILE_INCOMPLETE` | 资料不完整 |
| 入职 | `HR_ONBOARDING_CONFIRM` | 入职信息待确认 |
| 合同 | `HR_CONTRACT_DUE` | 合同临期/到期 |
| 离职 | `HR_OFFBOARD_ACCOUNT` | 离职账号待处理 |
| 健康证 | `HR_HEALTH_CERT_DUE` | 健康证临期/过期 |
| 健康证 | `HR_HEALTH_CERT_REVIEW` | HR 待审核 |
| 健康证 | `HR_HEALTH_CERT_RETURNED` | 员工被驳回后修改重提 |

## 3. 明确没有进入统一待办的内容

| 内容 | 原因/处理 |
| --- | --- |
| OA 采购申请审批、驳回 | 按本次业务决策正式退役，不是遗漏；门店要货改用调拨。 |
| 公告未读、站内消息、推送 | 保持消息与业务待办分开，不计入统一待办总数。 |
| 调拨发货/收货超时 SLA 提醒 | 项目当前没有可靠的业务时限配置，本次不用创建时间臆造逾期规则；需业务确认 SLA 后另行增加。 |
| 无 `counter_user_id` 或无 `deadline` 的历史活动盘点 | 不伪造责任人，迁移脚本输出治理清单；人工分配真实盘点人和截止时间后才进入个人待办。 |
| 开关关闭时无法处理的健康证驳回重提 | `feature.hr.health-certificate.enabled=false` 时不生成无法执行的个人待办。 |
| OA 采购表、历史流程和审计记录的物理删除 | 为保留审计证据暂不删除；稳定发布周期后另做归档/物理清理方案。 |

## 4. 数据库发布与回滚

- 正向脚本：`sql/erp_unified_todo_business_scope_20260713.sql`
- Docker 镜像脚本：`docker/mysql/db/erp_unified_todo_business_scope_20260713.sql`
- 回滚脚本：`sql/erp_unified_todo_business_scope_rollback_20260713.sql`
- 正向脚本与 Docker 副本已逐字节校验一致。
- 已在临时 MySQL 8 克隆库验证“正向连续执行两次 + 回滚连续执行两次”：未重复备份、未误停库存采购、菜单与角色授权能恢复。
- 正向脚本在 OA 采购存在运行任务/流程或配置冲突时主动阻断，不隐藏尚未处理的流程。

## 5. 验证结果

| 范围 | 结果 |
| --- | --- |
| common 待办契约 | 12 项通过 |
| inventory 待办/盘点/调拨 | 176 项通过 |
| OA 待办/签约/OA 采购开关 | 80 项通过 |
| system/HR 待办/健康证 | 27 项通过，1 项原生 MySQL 条件测试按既有条件跳过 |
| 前端全量 | 182 个测试文件通过，0 失败 |
| 生产构建 | 通过；入口包 1.471 MiB，低于 1.500 MiB 门禁 |
| SQL | MySQL 8 幂等正向/回滚验证通过；正向镜像一致 |

## 6. 上线前仍必须完成

1. 在目标环境执行脚本的只读预检，确认 OA 采购 `submitted` 单据、Flowable 运行任务和运行实例均为 0。
2. 处理脚本输出的无盘点人/无截止时间活动盘点清单。
3. 按门店、仓库、运营总监、指定盘点人、HR、员工、非候选管理员完成真实账号 UI 验收。
4. 发布后观察一个完整工作日：三个 provider 成功率/耗时、重复键、无法跳转、无责任人盘点和 OA 采购写入调用数。
