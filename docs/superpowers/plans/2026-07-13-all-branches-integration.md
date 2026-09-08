# 7月13号全分支整合计划

## 目标与边界

- 以 `codex/july12-integration-staging` 为基线创建并维护 `7月13号`。
- 让所有有效本地分支提交最终都成为 `7月13号` 的祖先。
- 只合并各分支已经提交的内容；不暂存、不还原、不覆盖其他工作树中的未提交改动。
- 保留当前较新的合同自动化、HR 生命周期、移动端和统一待办实现；冲突时组合仍缺能力，不用旧实现整文件覆盖新实现。

## 合并顺序

1. 基础与旧分叉：`codex/desktop-audit-remediation-r1`、`codex/hr-personnel-management`。
2. 新 HR/待办修复：`codex/hr-usability-implementation`、`codex/unified-todo-remediation-20260713`。
3. 调拨链：`codex/transfer-approval-config-validation`、`codex/transfer-four-level-approval-20260713`、`codex/ux-flow-break-remediation`。
4. 独立云盘链：`codex/cloud-drive-p0-improvements`。

## 验证

- 每组完成后运行对应 Maven 模块测试和前端定向测试。
- 最终运行 `mvn clean -DskipTests package`、`npm --prefix erp-ui run test`、`npm --prefix erp-ui run build:prod`。
- Docker 可用时运行 Testcontainers 集成测试；不可用时记录环境错误并保证普通测试和完整编译通过。
- 使用 `git merge-base --is-ancestor` 验证所有有效分支均已纳入 `7月13号`。

## 回退与保护

- 每条来源分支使用独立 merge commit，便于逐组审阅和 revert。
- 不删除来源分支或工作树，不移动 `7月12号` 与 staging。
- 任一业务语义无法组合时停止在该 merge commit 前，不影响此前已完成组。
