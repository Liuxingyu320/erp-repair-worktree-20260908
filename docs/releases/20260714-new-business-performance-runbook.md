# 新增业务结构与性能验收手册

> 这是阶段 5 门禁，不阻塞首次单店单仓灰度。只有阶段 4 真实灰度及回滚演练通过后，才执行大页面、大服务的结构拆分。

## 1. 前端分块预算

`npm run build:prod` 现在同时校验：

- `app` 同步入口不超过 1.5 MiB；
- 健康证、我的团队、客户服务卡、OE 补货、调拨工作台及对应移动页的每个关键异步页面不超过 192 KiB；
- 按 Vue 源模块定位实际 chunk，不依赖每次构建都会变的哈希文件名；
- 页面未被拆成异步分块、统计报告缺失或任一分块超额时构建失败。

紧凑结果会写入 `erp-ui/dist/build-budget.json`，完整 Webpack 报告默认在校验后删除。

## 2. 性能数据边界

1. 性能库只使用合成或脱敏数据，不复制生产客户照片、手机号、健康证附件或其他个人信息。
2. 只从生产库取脱敏汇总行数；每个性能表的测试行数必须为参考行数的 80%~125%。
3. API 探测只执行五个声明的 GET 列表路径，不保存响应体、登录 Token 或 storageState 路径。
4. `EXPLAIN`、慢查询和行数证据只保存执行计划、查询摘要和聚合数字，删除 SQL 字面参数与结果行。

## 3. 采集前后 P95

复制 `scripts/qa/new-business-uat.example.json` 为本地配置，按 UAT 手册准备九个真实角色的只读 storageState 和四个组织上下文。设置同样的环境确认变量后，在优化前的基线提交执行：

```bash
python3 scripts/qa/run_new_business_performance_probe.py \
  --config /secure/erp-uat/new-business-uat.local.json \
  --phase before \
  --output output/new-business-performance/load-before.json
```

灰度稳定后完成结构/查询优化，再在最终候选提交、同一数据快照上执行：

```bash
python3 scripts/qa/run_new_business_performance_probe.py \
  --config /secure/erp-uat/new-business-uat.local.json \
  --phase after \
  --output output/new-business-performance/load-after.json
```

每个场景默认预热 5 次、采集 50 次；任一 HTTP/业务错误会使本次探测失败。接口预算为：调拨工作台 P95 最高 1500 ms，其余四项最高 1200 ms；即使仍在预算内，候选版也不得无说明地比基线回退超过 10% 或 50 ms。

## 4. EXPLAIN、慢查询与索引决策

对健康证到期列表、客户卡搜索、OE 待处理列表、调拨工作台和差异列表，分别保留优化前后：

- 脱敏 `EXPLAIN FORMAT=JSON` 输出；
- 不含 SQL 参数和结果行的慢查询 digest/无慢查询结论；
- 对应 API 探测文件。

没有优化前 `EXPLAIN` 或慢查询证据时，`indexChanges` 必须为空。如果增删改索引，每项变更都必须指向优化前证据的 SHA-256，不允许仅凭经验加索引。

## 5. 最终门禁

复制 `scripts/qa/new-business-performance-evidence.example.json` 到 `output/`，填写真实提交、文件路径、SHA-256、P95 和签字。然后执行：

```bash
export ERP_NEW_BUSINESS_UAT_EVIDENCE=/absolute/output/uat-evidence.json
export ERP_NEW_BUSINESS_GRAY_EVIDENCE=/absolute/output/gray-evidence.json
export ERP_NEW_BUSINESS_PERFORMANCE_EVIDENCE=/absolute/output/performance-evidence.json
./scripts/verify-new-business-performance-gate.sh
```

该命令会先重新验证 UAT、监控规则、两阶段灰度和回滚演练，再验证数据量、文件哈希、前后提交、真实探测 P95、索引依据和后端/数据库/QA 签字。任一外部证据缺失时都不得标记阶段 5 完成。
