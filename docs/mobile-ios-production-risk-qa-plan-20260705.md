# 苹果网页端上线/性能/极端场景验收方案

## 目标

在发布前用 Xcode iOS Simulator + Safari 真实页面验证苹果网页端移动工作台，覆盖页面级、功能级、按钮级、弹窗级和真实流转级风险，并补充类生产包、接口性能、极端输入、并发重复动作和上线回滚门禁。

本方案禁止依赖 MCP。页面验证必须使用真实 iOS Simulator Safari 页面、真实构建包和真实后端接口。

## 验收环境

- 前端：`npm run build:prod` 生成的 `erp-ui/dist`
- 后端：待上线版本服务
- 类生产代理：静态包 history fallback + `/prod-api` 反代后端
- 页面端：Xcode iOS Simulator Safari
- 接口端：Node/fetch 或同等压测脚本
- 数据库：独立 QA 数据前缀，禁止直接操作生产真实单据做破坏性并发

## 执行命令

本地类生产验收：

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW/erp-ui
npm run build:prod
```

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
PORT=19025 API_ORIGIN=http://127.0.0.1:8080 node scripts/mobile-ios-prod-static-server.cjs
```

另开一个终端执行：

```bash
cd /Users/liuxingyu/Desktop/备份/ERP-NEW
UI_ORIGIN=http://127.0.0.1:19025 \
API_ORIGIN=http://127.0.0.1:8080 \
DB=BossERP_stock_state_75c59ee \
node scripts/mobile-ios-prod-risk-qa.cjs
```

预发/生产影子环境验收时，只替换 `UI_ORIGIN`、`API_ORIGIN` 和 `DB`。如果预发前端已经是真实 Nginx/CDN 地址，可以不启动本地静态服务，直接把 `UI_ORIGIN` 指向预发地址。

## 角色和设备矩阵

至少覆盖 5 个端/角色：

| 角色 | 设备 | 组织上下文 | 重点 |
| --- | --- | --- | --- |
| 门店管理员 | iPhone 17 Pro | STORE | 销售、库存、我的、通知 |
| 仓库管理员 | iPhone 17 Pro Max | WAREHOUSE | 入库、出库、盘点、调拨 |
| 茶艺师 | iPhone 17e | STORE | 考勤、受限权限、移动工作台 |
| 运营/审批 | iPhone Air | STORE | 审批、OA、权限边界 |
| 无业务组织账号 | iPad mini | 无 STORE/WAREHOUSE | 组织选择、无权限提示、账号页 |

## 页面和流转覆盖

发布前必须至少跑三层检查：

1. 全量移动端回归
   - 页面直达、刷新、返回、底部导航、组织切换。
   - 功能按钮、筛选、搜索、导出、刷新。
   - 表单打开、字段填充、提交、取消。
   - 确认弹窗、动作弹窗、详情弹层、空状态。
   - 销售、采购、退货、发货、盘点、调拨、OA、考勤、工资、通知。

2. 类生产包 iOS Safari 冒烟
   - 使用 `dist`，不走 dev server。
   - 检查 `/mobile/...` 直达路由必须返回前端页面。
   - 检查 `/prod-api` 代理、登录态 cookie、组织上下文 sessionStorage。
   - 截图必须来自 WebDriver 当前页面，不能用模拟器旧标签页截图替代。

3. 真实业务流转
   - QA 前缀种子数据。
   - 覆盖销售生成发货、仓库可见并出库、盘点录入/确认、调拨审批/发货/收货、退货确认、通知已读、考勤打卡。
   - 每一步记录前后状态和截图。

## 性能门禁

核心移动接口并发压测建议阈值：

| 指标 | 门禁 |
| --- | ---: |
| 错误率 | 0% |
| p95 | < 800ms |
| max | < 2000ms |
| 401/403 | 只能出现在权限/登录测试 |
| 500 | 不允许出现在正常路径 |

核心接口：

- `/system/mobile/profile`
- `/inventory/mobile/workbench/summary`
- `/inventory/sales/list`
- `/inventory/stock/list`
- `/inventory/mobile/options/product`
- `/inventory/deliveryNotice/list`
- `/inventory/stockCheck/list`
- `/inventory/transfer/processing`
- `/inventory/mobile/transfer-approval/todo`

数据量要分三档验证：

- 当前数据量
- 10 倍列表数据
- 100 倍列表数据

如果 10 倍或 100 倍数据无法在本地快速构造，需要在预发库执行脱敏批量数据脚本后再测。

## 极端场景

必须覆盖：

| 场景 | 预期 |
| --- | --- |
| 非法/过期 token | 受控 401，不白屏 |
| 缺少业务组织 | 受控空结果或组织选择，不 500 |
| 超大分页 | 不超时、不拖垮服务 |
| 最大 UI 搜索关键字 | 正常返回 |
| 恶意超长直接 GET | 被网关边界拒绝，前端不能产生这种请求 |
| 重复审批/发货/收货/盘点确认 | 只允许一次成功，其他请求受控失败，不能双写库存 |
| 移动 Safari 刷新/返回 | 登录态和组织上下文保持一致 |
| 权限不匹配直达 | 重定向到可用工作台并显示提示 |

## 上线门禁

只有以下条件同时满足才允许上线：

- `npm run build:prod` 成功。
- 全量移动端页面/按钮/弹窗/真实流转通过。
- 类生产 iOS Safari 冒烟通过。
- 核心接口压力错误率 0%，p95 不超过门禁。
- 极端场景通过。
- 数据库变更脚本可回滚或确认不可逆风险。
- 前端 dist、后端 jar、数据库上线前备份完成。
- Nginx history fallback 和 `/prod-api` 配置已在预发验证。
- 静态资源缓存策略不会导致旧 JS 加载新接口。

## 回滚方案

1. 前端回滚
   - 恢复上一版 `dist`。
   - 清理或刷新 CDN/Nginx 静态缓存。

2. 后端回滚
   - 恢复上一版 jar。
   - 重启对应服务。
   - 检查网关路由和服务注册状态。

3. 数据库回滚
   - 优先执行可逆 SQL。
   - 如果 SQL 不可逆，使用上线前备份恢复或补偿脚本。

4. 回滚后复验
   - 登录。
   - 移动工作台。
   - 销售/库存/出库核心路径。
   - 最近 30 分钟错误日志。

## 本轮本地类生产验收结果

本轮执行日期：2026-07-05。

结果：

- iOS Safari 类生产包：23/23 PASS。
- 核心移动 API 压力：10 个端点，12 并发，每端点 48 次，0 失败。
- 极端场景：6/6 PASS。
- 发现并修复：移动端实体搜索超长关键字会触发 HTTP 414。已在 UI 输入框和请求构造层统一限制为 80 字。

最新一次仓库脚本执行证据：

- 报告：`/tmp/erp-risk-qa-RISK-20260705114029/risk-report.md`
- 截图：`/tmp/erp-risk-qa-RISK-20260705114029`

前一轮临时脚本证据：

- 报告：`/tmp/erp-risk-qa-RISK-20260705111722/risk-report.md`
- 截图：`/tmp/erp-risk-qa-RISK-20260705111722`
- 关键截图：
  - `/tmp/erp-risk-qa-RISK-20260705111722/admin_store_prod_mobile_sales.png`
  - `/tmp/erp-risk-qa-RISK-20260705111722/admin_warehouse_prod_mobile_outbound.png`

## 当前剩余风险

- 本轮是本地类生产环境，不等同正式公网生产环境。
- 真实 Nginx/CDN/TLS、真实公网网络、真实生产数据量还需要在预发或生产影子环境复跑。
- 当前生产构建仍有资源体积 warning，建议后续单独做包体积优化。
- 真实生产上线前必须复核慢查询日志、网关 4xx/5xx、服务 CPU/内存和数据库连接池。
