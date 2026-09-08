# 2026-07-14 新增业务发布清单

> 状态：候选内容和分阶段门禁已定义；静态与本机统一门禁已通过，但当前工作区不是干净候选提交，尚未执行真实 MySQL、真实角色 UAT、灰度、性能门禁或生产发布。  
> 机器清单：[`scripts/new-business-release-20260714.json`](../../scripts/new-business-release-20260714.json)  
> 迁移顺序：[`scripts/new-business-migrations-20260713.list`](../../scripts/new-business-migrations-20260713.list)

## 发布范围

本版本仅为以下已经完成的业务闭环建立发布契约：

- 员工健康证与我的团队；
- OE 超额自购参考、额度内上报与仓库补货；
- 门店要货、返仓与调拨差异；
- 门店隔离的客户服务卡；
- 上述业务所需的受控附件、功能开关、指标和只读对账。

云盘容量、人事主数据治理、合同签署、统一待办等同一工作区中的其他改动不自动视为本清单的一部分；如发布包必须包含它们，应分别附上各自实施结果和门禁证据。

## 迁移与备份

8 个迁移的顺序、SHA-256、新增表和既有备份表由机器清单固定。发布前必须执行：

```bash
./scripts/verify-new-business-release-scope.sh --candidate
./scripts/verify-new-business-migrations.sh
mvn -Pnew-business-mysql-it verify
```

生产数据库写入必须显式提供数据库名、版本清单和迁移确认参数。发布助手不得扫描并执行 `sql/` 或 `docker/mysql/db/` 中的其他 SQL。

## 功能开关

迁移后以下开关保持 `false`：

1. `feature.hr.health-certificate.enabled`
2. `feature.hr.team.enabled`
3. `feature.inventory.store-return.enabled`
4. `feature.inventory.transfer-discrepancy.enabled`
5. `feature.inventory.oe-replenishment.enabled`
6. `feature.oa.oe-replenishment.enabled`
7. `feature.inventory.customer-service-card.enabled`

建议启用顺序：健康证 → 我的团队；调拨差异 → 门店返仓；库存 OE → OA OE；最后启用客户服务卡。

## 候选产物记录

以下内容由统一发布门禁生成，不能预填或人工猜测：

| 项目 | 值 |
|---|---|
| 候选 Git 提交 | 待生成 |
| 后端/前端产物 SHA-256 | 待生成 |
| MySQL 5.7 IT | 待执行 |
| MySQL 8.0 IT | 待执行 |
| 权限和主数据准备报告 | 待目标库执行 |
| 两门店、两仓库角色验收 | 待测试环境执行 |
| 生产备份目录 | 未发布 |
| 新 release/previous 路径 | 未发布 |

## 分阶段门禁与证据状态

| 阶段 | 门禁 | 当前状态 |
|---|---|---|
| 静态契约 | `./scripts/verify-new-business-release.sh --static` | 已通过：范围、迁移、Docker 显式初始化、IT、准备报告、对账、监控、UAT、灰度和性能契约均通过 |
| 本机回归 | `./scripts/verify-new-business-release.sh --local` | 已通过：后端四模块全量回归、前端 183 个测试、密钥扫描、生产构建及产物哈希完成 |
| 干净候选 | `./scripts/verify-new-business-release-scope.sh --candidate` | 待完成；已在当前脏工作区验证会按设计拒绝放行，不能冒充候选提交 |
| 完整发布 | `./scripts/verify-new-business-release.sh --full` | 待执行：要求干净候选、Docker、MySQL 5.7/8.0 Failsafe、全量回归、发布包及真实角色 UAT 证据 |
| 两阶段灰度 | `./scripts/verify-new-business-gray-gate.sh` | 待真实环境执行：要求同一候选提交的 UAT 证据、监控规则实际加载、单店单仓到双店双仓、零异常对账及回滚演练 |
| 灰度后性能 | `./scripts/verify-new-business-performance-gate.sh` | 待真实环境执行；已验证缺少 UAT/灰度/性能证据时会按设计拒绝。证据齐全后会先重验 UAT 和灰度，再校验同一脱敏数据快照的前后 P95、慢 SQL/执行计划、索引依据和签字 |

本机门禁的绿色结果只证明当前文件快照在本机可验证范围内通过，不能替代干净候选、真实数据库和真实环境证据。运行产生的认证配置、截图、响应摘要和证据文件必须留在已忽略的 `output/` 下，不得提交令牌、客户照片、手机号或健康证附件。

## 回滚边界

- 先关闭新业务入口，再回滚前后端版本。
- 在途审核、发件箱、调拨收货和差异处理继续运行。
- 新增表和字段默认保留，不在紧急回滚中执行破坏性删除。
- 数据库恢复必须使用本次发布记录的备份文件，并单独取得明确授权。
