# 新增业务灰度、告警与对账手册

本手册只在完整发布门禁和真实角色 UAT 通过后执行。第一阶段限定一个门店和一个仓库；第二阶段加入门店 B、仓库 B 验证隔离；两阶段都通过后才能讨论全量。

## 1. 监控规则上线

候选规则位于 `ops/monitoring/new-business-alert-rules.yml`，包含当前死信、最老待投递、重试积压、健康证提醒失败、客户卡跨店拒绝/乐观锁冲突、OE 需求超时、调拨差异超时和对账任务失效告警。

在监控主机先执行严格门禁；该命令在 `promtool` 缺失时会直接失败：

```bash
bash scripts/verify-new-business-monitoring.sh --require-promtool
```

发布操作上必须把同一 SHA-256 的候选文件加入 Prometheus `rule_files` 并热加载。使用已授权的本地代理或受控连接，直接把 alert rules API 响应投影为最小证据；不得把认证头或包含活动告警实例的原始响应落盘：

```bash
(
  set -euo pipefail
  mkdir -p output/gray
  umask 077
  target=output/gray/prometheus-rules-alerts.json
  temporary="${target}.tmp"
  trap 'rm -f "$temporary"' EXIT
  rm -f "$temporary"
  curl --fail --silent --show-error --get \
    "${PROMETHEUS_URL:?}/api/v1/rules" \
    --data-urlencode 'type=alert' \
    | jq --arg group 'erp-new-business-20260714' \
      '{status, data: {groups: [.data.groups[]
        | select(.name == $group)
        | {name, interval, rules: [.rules[]
          | {name, type, health, state, query, duration,
             labels: {severity: .labels.severity, release: .labels.release}}]}]}}' \
      > "$temporary"
  mv "$temporary" "$target"
  sha256sum "$target"
)
```

`jq` 投影只保留组间隔、验证字段和静态 `severity` / `release` 路由标签，不保留活动告警实例、注释、值或其他标签。不得保存 Cookie、Authorization 头、Token、Prometheus 配置或投影前的原始 API 响应。API 必须返回 `status=success`，唯一目标组的 `interval` 必须为 60 秒，并恰好包含以下 10 个有序告警；每条都是 `type=alerting`、`health=ok`、`state=inactive`，且 `query`、`duration`、`severity`、`release` 与固定候选契约一致：

- `ErpNewBusinessOeOutboxDead`
- `ErpNewBusinessOeOutboxStalled`
- `ErpNewBusinessOeOutboxRetryBacklog`
- `ErpNewBusinessCustomerScopeDeniedSpike`
- `ErpNewBusinessCustomerOptimisticConflictSpike`
- `ErpNewBusinessHealthReminderFailure`
- `ErpNewBusinessReconciliationFailed`
- `ErpNewBusinessReconciliationStale`
- `ErpNewBusinessOeDemandStalled24h`
- `ErpNewBusinessTransferDiscrepancyOverdue24h`

证据中的 `candidateRulesSha256` 只绑定本地候选 YAML；Prometheus Rules API 不返回源文件 SHA，因此最小 API 投影只能证明上述 10 条告警的必需运行语义，不能单独证明服务端加载文件与候选 YAML 字节级相同。完整文件 SHA 的发布追溯仍必须由监控主机部署日志/变更记录闭合。页面截图、`promtool` 文本输出或手写的“加载成功”文件都不是合格 API 证据；仅在代码库中存在 YAML 也不算接入完成。

OA 服务每分钟刷新以下当前状态指标：

- `erp_oa_oe_outbox_dead`
- `erp_oa_oe_outbox_age`
- `erp_oa_oe_outbox_pending`
- `erp_oa_oe_outbox_retry`
- `erp_oa_oe_outbox_sending`

## 2. 只读对账任务

为只读数据库账号准备仓库外的 MySQL client options 文件并执行 `chmod 600`：

```ini
[client]
host=127.0.0.1
port=3306
user=erp_reconciliation_ro
password=由凭据系统下发的只读密码
```

运行时只传文件路径和明确数据库名，密码不会进入命令行或发布摘要：

```bash
export ERP_NEW_BUSINESS_MYSQL_DEFAULTS_FILE=/secure/erp/reconciliation.cnf
export ERP_NEW_BUSINESS_MYSQL_DATABASE=erp_business
export ERP_NEW_BUSINESS_TEXTFILE_DIR=/var/lib/node_exporter/textfile_collector
scripts/run-new-business-reconciliation.sh
```

SQL 会在只读会话中执行。原始行只写临时文件并在退出时删除，归档 JSON 仅包含稳定异常代码和数量；textfile 指标使用临时文件加原子替换，避免 Prometheus 读取半份结果。

解析器固定整份 `scripts/new-business-daily-reconciliation.sql` 的 SHA-256，并把 `reconciliationSqlSha256` 写入脱敏 summary。任一查询被注释、追加恒假条件或其他语义漂移都会在生成 summary 前失败；灰度门禁还会重新核对候选 SQL 及前后两份 summary 中的该 SHA。

生产建议每小时运行一次以便及时发现 24 小时超时，至少每日归档一次摘要。若最近一次结果有异常或超过 26 小时没有运行，告警保持触发。定时任务失败不能用空结果覆盖上一次告警。

## 3. 功能开关顺序

七个开关都应在迁移后保持 `false`。通过系统参数管理逐项开启，并从 `sys_oper_log` 记录中保存操作审计 ID、操作人、时间、范围、原因和变更单号：

1. `feature.hr.health-certificate.enabled`
2. `feature.hr.team.enabled`
3. `feature.inventory.transfer-discrepancy.enabled`
4. `feature.inventory.store-return.enabled`
5. `feature.inventory.oe-replenishment.enabled`
6. `feature.oa.oe-replenishment.enabled`
7. `feature.inventory.customer-service-card.enabled`

调拨差异必须先于返仓；库存 OE 接收必须先于 OA 上报；客户服务卡最后开放。数据库迁移脚本不得代替这次独立、可审计的配置变更。

## 4. 灰度观察与停止条件

第一阶段只给门店 A、仓库 A 的灰度角色开放菜单和权限。对账为零、无死信、无数量不守恒并完成业务验收后，第二阶段加入门店 B、仓库 B，专门复验跨店/跨仓负向用例。

出现任一情况立即停止扩大范围：

- 发件箱 `DEAD > 0` 或最老待投递超过 15 分钟；
- OE 需求超过 24 小时无进展；
- 调拨差异超过 24 小时未结或数量不守恒；
- 客户卡跨店拒绝、乐观锁冲突持续异常升高；
- 健康证提醒连续失败；
- 对账任务失败、过期或任一异常计数大于零。

## 5. 关闭入口但完成在途业务演练

回滚演练先关闭新建入口，再验证在途流程继续：

- 健康证历史查询和已提交审核继续；
- OA OE 新上报先关闭，已有发件箱仍投递；
- 库存 OE 和调拨新建关闭时，已有发货、收货和差异仍处理；
- 客户服务卡新写入关闭后仍可按权限只读查询。

演练结束再次执行只读对账，异常必须为零。不得通过删除新表、差异事实、发件箱或历史记录来制造“回滚成功”。

## 6. 灰度完成门禁

复制 `scripts/qa/new-business-gray-evidence.example.json`，填写两阶段结果、七次唯一配置审计、上述 Prometheus API 告警加载证据及其 SHA-256、灰度前后两份独立对账和回滚演练证据。示例中的 `../../output/gray/...` 相对路径以位于 `scripts/qa` 的本地证据 JSON 为基准。前后对账必须声明同一份固定 `reconciliationSqlSha256`，各自包含固定的 19 个 issue code 且全部为 0，完成时间必须先后有序。然后执行：

```bash
export ERP_NEW_BUSINESS_UAT_EVIDENCE="$PWD/scripts/qa/new-business-uat-evidence.local.json"
export ERP_NEW_BUSINESS_GRAY_EVIDENCE="$PWD/scripts/qa/new-business-gray-evidence.local.json"
scripts/verify-new-business-gray-gate.sh
```

门禁会校验候选提交、本地候选告警规则 SHA、Prometheus API 状态、目标组的唯一性与间隔、10 个告警的顺序、健康/非活动状态、表达式、持续时间及路由标签、两阶段顺序、七个开关依赖顺序与唯一审计 ID、19 项前后对账零异常、完成时间顺序、回滚演练和多方签字。外部监控没有真正加载、存在 pending/firing 告警、真实灰度未执行或证据哈希不匹配时，不能标记阶段 4 完成。
