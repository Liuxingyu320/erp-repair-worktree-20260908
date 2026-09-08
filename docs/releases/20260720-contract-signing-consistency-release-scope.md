# 合同签约一致性 20260720 发布边界

> 发布 ID：`contract-signing-consistency-20260720`
> 类型：独立前向候选，不覆盖或改写 20260716、20260718 发布账本
> 当前状态：本地源码、静态证据与隔离合成浏览器 UAT 候选；未发布生产、未代签、未执行真实任务硬删除

## 本轮纳入

本轮候选把已实现的合同签约一致性改动收敛为一个可单独复核的发布边界：

1. Excel 入职导入后的公司组合匹配、公司主数据及合同章硬门禁。
2. `COMPANY_FIRST` 与 `SIGNATURE_FIRST` 两种签署顺序及任务级签名样本。
3. 最终文件逐份阅读、正文哈希、签约包 root hash 与归档证据字段；初始文档
   阅读确认以 `requestId` 和签约包版本做重放/并发防护。
4. 有社保固定使用薪酬 B 版、无社保固定使用薪酬 A 版，并使旧错误预览失效。
5. HR 补资发送命令的数据库持久幂等台账，防止服务重启或重放后重复创建发送副作用。
6. 管理员批量确定公司/印章的独立权限，以及未完成任务删除后的可重试文件清理台账。
   硬删除命令另以 `requestId`、管理员、规范化载荷摘要、所有权租约和结果快照持久化；
   每个任务必须携带 `expectedVersion`，中断重放从已持久进度继续。
7. v6 模板候选的便携清单和自动渲染证据。模板仍为 `disabled`，本轮不生成注册载荷。

## 前向迁移顺序

唯一人工执行顺序由
[`contract-signing-consistency-migrations-20260720.list`](../../scripts/contract-signing-consistency-migrations-20260720.list)
定义：

1. `erp_oa_sign_salary_social_mapping_20260719.sql`
2. `erp_system_sign_candidate_phone_index_20260719.sql`
3. `erp_oa_sign_company_salary_policy_20260720.sql`
4. `erp_oa_sign_dual_sequence_evidence_20260720.sql`
5. `erp_oa_sign_onboard_send_idempotency_20260720.sql`
6. `erp_oa_sign_task_batch_finalize_20260720.sql`
7. `erp_oa_sign_file_cleanup_20260720.sql`
8. `erp_oa_sign_task_hard_delete_idempotency_20260720.sql`

每个迁移在 `sql/`、`docker/mysql/db/` 和对应模块的
`src/main/resources/db/migration/` 中有逐字节一致的镜像；SHA-256 固定在本轮 JSON
清单中。`erp_oa_sign_onboard_import_20260718.sql` 与
`erp_system_sign_profile_supplement_20260718.sql` 继续由既有 20260718 账本负责，
本轮不重复入账。

## 用户明确排除

- 员工手册正文：明确排除，不创建、不补写、不替换、不验收，不作为本轮本地完成判定的阻塞项。
- 送达地址：明确排除，不新增字段、不改模板映射、不回填当前空白。现有空地址风险继续保留，
  本轮不宣称已解决。

这两项排除不影响其余源码、迁移与渲染证据的核验。v6 候选仍保持 `disabled`，
原因是模板注册、方案发布与灰度属于需明确授权的外部动作，而非将排除项误列为本地实施阻塞。

## v6 模板证据

- v6 清单路径以清单目录为基准，不包含任何开发机绝对路径。
- 自动 QA 固定 18 份 PDF、112 页，空白页 0、非 A4 页 0、`${` 或“待 HR 确认”命中 0。
- 渲染证据只记录文件路径、哈希、大小、页数和检查结果，不记录抽取出的员工字段值。
- 自动 QA 不等于 HR、业务或法务人工视觉批准；清单明确记录
  `manualVisualApproval = not-claimed`。

## 隔离合成浏览器 UAT

- `syntheticBrowserUat = passed`：隔离浏览器跑通了公司先行完成、签名先行待公司、
  签名先行最终确认以及已完成任务硬删除保护 4 个合成场景。
- 运行记录为浏览器 console error 0，关键 API 非 200 响应 0；4 张截图的相对路径、
  大小、像素和 SHA-256 固定在
  [`20260720-contract-signing-synthetic-browser-uat.json`](./evidence/20260720-contract-signing-synthetic-browser-uat.json)。
- 该 UAT 使用合成身份与 mock API，未连接生产、未使用真实签名，因此
  `realDualFlowSignedUat` 仍为 `pending`，也不代替 HR、业务或法务签认。

## 数据库版本与本地重放

- 已有只读记录表明当前生产为 MySQL 8.0.24，因此
  `nativeMySql57Rehearsal = not-applicable-version-verified`，不再误标为 `pending`。
- 发布前仍须在获授权的只读连接上复核当时生产版本；若版本事实变化，必须重新评估迁移兼容性。
- 新的补资发送幂等迁移已在本地 `BossERP_NEW` / MySQL 8.0.45 连续执行 2 次，
  结果为 9 列、1 个唯一索引。这是本地重放验证，不代表生产迁移已执行或获得授权。
- 新的硬删除持久幂等迁移已在同一本地库连续执行 2 次，结果为 16 列、
  `uk_oa_sign_task_hard_delete_request` 唯一索引计数 1。这同样不代表生产迁移已执行或获得授权。

## 未在本地伪造的外部门禁

以下动作仍需要相应权限或真实责任人，当前发布契约只标记为待办，不伪造完成：

- 发布前需用只读权限复核生产仍为已记录的 MySQL 8.0.x 基线。
- 生产备份、迁移、模板注册、方案发布和灰度均需显式授权并留痕。
- 两种签署顺序的真实签字 UAT 必须由授权测试身份完成；本轮未代签。
- 真实签约任务硬删除会永久清理证据和文件，本轮未对真实任务执行该动作。
- HR、业务和法务审批仍由对应责任人签认。

## 回滚与止损

发布异常时先停止扩量、关闭新入口并停止创建新任务；已生成的文档、签名、哈希、事件和审计
不得删除或覆盖。数据库结构采用前向修复，不以删除历史证据或回滚已签合同作为恢复手段。
