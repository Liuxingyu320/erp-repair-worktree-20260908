# 合同签约一致性 20260720 发布清单

> 发布 ID：`contract-signing-consistency-20260720`
> 执行策略：`manual-phased-forward-only`
> 当前状态：候选；隔离合成浏览器 UAT 已通过，未发布生产、未代签，真实 UAT 与外部门禁仍未完成

## 受控文件

- 机器可读清单：
  [`contract-signing-consistency-release-20260720.json`](../../scripts/contract-signing-consistency-release-20260720.json)
- 精确源码边界：
  [`contract-signing-consistency-release-files-20260720.list`](../../scripts/contract-signing-consistency-release-files-20260720.list)
- 前向迁移顺序：
  [`contract-signing-consistency-migrations-20260720.list`](../../scripts/contract-signing-consistency-migrations-20260720.list)
- 静态门禁：
  [`verify-contract-signing-consistency-release.sh`](../../scripts/verify-contract-signing-consistency-release.sh)
- 发布边界：
  [20260720-contract-signing-consistency-release-scope.md](./20260720-contract-signing-consistency-release-scope.md)
- v6 渲染 QA 证据：
  [20260720-contract-signing-v6-render-qa.json](./evidence/20260720-contract-signing-v6-render-qa.json)
- 隔离合成浏览器 UAT 证据：
  [20260720-contract-signing-synthetic-browser-uat.json](./evidence/20260720-contract-signing-synthetic-browser-uat.json)

## 迁移摘要

迁移名称、顺序、三份镜像路径和 SHA-256 只由机器可读清单固定。发布执行人必须在维护窗口前
再次运行静态门禁，禁止凭本文复制旧哈希或从临时目录选择 SQL。

执行顺序为：薪酬人工映射历史步骤、候选人手机号索引、20260720 公司/薪酬强制策略、双流程
证据字段、补资发送持久幂等、批量定稿权限、文件清理台账、任务硬删除持久幂等台账。
后者必须在文件清理台账之后执行。前两项中的薪酬历史步骤随后由 20260720 强制策略前向纠正；
不得调换顺序或只选择最终 UPDATE 执行。

## 证据结论与限制

静态门禁验证源码清单存在、迁移顺序、三份 SQL 镜像逐字节一致、SHA-256、一套便携 v6 清单
以及 18 份 PDF / 112 页自动 QA 证据。它不代表生产数据库已迁移，也不代表真实员工已完成
两种流程签字 UAT。

隔离合成浏览器 UAT 覆盖 4 个关键场景，记录 console error 0 且关键 API 非 200 响应 0。
由于其使用合成身份和 mock API，机器清单同时固定
`syntheticBrowserUat = passed` 与 `realDualFlowSignedUat = pending`，禁止将两者混为同一验收结论。

已记录的生产数据库版本为 MySQL 8.0.24，故 MySQL 5.7 原生演练标记为
`not-applicable-version-verified`；发布前仍必须用只读权限复核实时版本。补资发送幂等迁移在本地
MySQL 8.0.45 连续执行两次通过。硬删除持久幂等迁移也在同一本地库连续执行两次，
结果为 16 列且请求编号唯一索引计数为 1。两项都是本地重放证据，不代表生产执行完成。

员工手册正文和送达地址均为用户明确排除项，内容未修改，不纳入本轮本地完成判定。
v6 候选继续禁用，直到模板注册、方案发布和灰度获得单独授权。

本轮未发布生产、未代签、未删除真实任务，也未替 HR、业务、法务、DBA 或运维生成签认。
