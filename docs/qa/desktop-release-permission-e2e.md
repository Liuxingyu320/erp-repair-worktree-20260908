# 电脑端生产权限与业务 E2E 复现说明

本说明仅用于本机隔离验收环境。脚本拒绝远程地址，并只接受本地
`BossERP_NEW`、Unix Socket、`127.0.0.1:1028/prod-api` 以及精确写入
确认值 `CODEX_QA_20260728`。

## 安全边界

- 三个合成角色为普通员工、受限管理员和“隔离管理员”。隔离管理员
  不是应用硬编码的 `user_id=1` 超级管理员。
- 所有合成记录使用 `CODEX_QA_20260728_` 前缀和
  `922607280000-922607289999` ID 围栏。
- 随机密码只写入 `/tmp/codex_qa_20260728_credentials.json`，文件模式
  为 `0600`；任何日志、证据和报告都不包含密码、令牌或 Cookie。
- 合同只保存草稿，不发送、不签署、不写外部文件；任务始终暂停且使用
  `ryTask.ryNoParams`。
- 真正的内置超级管理员只由主控使用现有已登录浏览器会话做只读核验。

## 前置服务

本地端口 `8080`、`9200-9206`、`9300` 和候选前端 `1028` 必须健康。
调拨统一审批还要求：

- 9205 本地服务发现包含 `erp-approval -> http://127.0.0.1:9206`；
- 9206 本地服务发现包含 `erp-inventory -> http://127.0.0.1:9205`；
- 已在本地候选库应用仓库内幂等迁移
  `sql/erp_inventory_transfer_approval_start_outbox_20260716.sql`。

上述配置只属于验收环境，不应作为生产源码改动提交。

## 执行

```sh
ERP_QA_WRITE_APPROVAL=CODEX_QA_20260728 \
  python3 scripts/qa/desktop_release_e2e_prepare.py

ERP_QA_WRITE_APPROVAL=CODEX_QA_20260728 \
  python3 scripts/qa/desktop_release_e2e_run.py
```

无秘密证据写入 `/tmp/codex_qa_20260728_evidence.json`。八条业务链覆盖：
登录、首页/菜单、审批、库存、合同草稿、员工、暂停任务和报表。

## 精确清理

主控完成浏览器核验后再执行：

```sh
ERP_QA_WRITE_APPROVAL=CODEX_QA_20260728 \
  python3 scripts/qa/desktop_release_e2e_cleanup.py \
  --confirm CODEX_QA_20260728
```

清理脚本先注销已注册的暂停任务，再按精确用户、角色、部门、业务 ID
及审批业务 ID 删除夹具；随后验证前缀和 ID 围栏均为零。凭据与清单仅在
清理完全通过后删除，无秘密 E2E 证据保留。
