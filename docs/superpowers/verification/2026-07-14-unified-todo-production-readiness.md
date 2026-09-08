# 统一待办生产发布收口验证记录

> 验证日期：2026-07-14  
> 工作区：`/Users/liuxingyu/Desktop/备份/ERP-NEW`  
> 基线：分支 `7月13号`，HEAD `b694659d`  
> 安全边界：真实当前库只执行读取预检；所有正向和回滚写入均仅作用于临时克隆库。

## 1. 最终待办范围

- inventory：18 类，含调拨 5 类、盘点 4 类。
- OA：8 类，不含 OA 采购申请/驳回。
- system/HR：7 类，含健康证到期、待审核和驳回重提。
- 合计：33 类。

明确未进入：OA 采购待办、公告/站内消息/推送、未定义 SLA 的调拨超时提醒、无真实盘点人或截止时间的历史盘点。

## 2. 真实当前库只读预检

库：`BossERP_stock_state_75c59ee`，MySQL `8.0.45`。

| 门禁 | 结果 |
| --- | ---: |
| 17 个基础表 | 通过 |
| 9 个仓储导航锚点 | 通过 |
| inventory 采购入口 | 启用，已保护 |
| OA `submitted` | 18，阻断 |
| OA 运行任务 | 18，阻断 |
| OA 运行实例 | 18，阻断 |
| 需后续治理的活动盘点 | 23，信息项 |

预检按设计退出码 1。未执行 OA 审批/驳回、未修改菜单、未写入配置、未修改盘点。

## 3. 发布门禁与脚本修正

新增：

- `scripts/unified-todo-release-20260714.list`：锁定 7 个脚本的唯一支持顺序。
- `scripts/verify-unified-todo-release.sh`：支持 `--static`、`--checksums`、`--preflight`、`--apply`。
- `erp-ui/test/unifiedTodoReleaseGate.test.js`：锁定顺序、MySQL 8、OA 三重阻断、inventory 采购保护、迁移镜像和回滚快照契约。

执行必须同时满足：

- 显式指定非系统数据库；
- MySQL 主版本不低于 8；
- `ERP_UNIFIED_TODO_APPLY_CONFIRMATION=APPLY_UNIFIED_TODO_20260714`；
- 成功获取命名锁 `erp:unified-todo:20260714`；
- OA 三项活动事项全部为 0。

演练发现并修复两个真实 SQL 缺陷：

1. 性能索引动态 SQL 原为无效语法 `ADD INDEX (...) ALGORITHM=...`，已改为 `ADD INDEX (...), ALGORITHM=...`。
2. 仓储角色快照在第二次正向执行时会吸收第一次新增的 17 条授权；已改为菜单与角色快照在同一事务中一次性冻结。

同时将仓储菜单回滚从硬编码近似恢复改为逐字段快照恢复，包括 `icon`、`query`、`route_name`、`update_by`、`update_time` 和 `remark`。

## 4. MySQL 8 克隆库演练

临时库：`codex_unified_todo_release_20260714`，由当前库 454 个表的结构以及 `sys_config/sys_menu/sys_role/sys_role_menu` 的真实基线数据重建。

正向连续执行两次，两次均成功。第二次执行后：

| 断言 | 结果 |
| --- | ---: |
| 盘点主表前置字段 | 8/8 |
| 盘点明细复盘字段 | 4/4 |
| 盘点责任/复盘索引 | 2/2 |
| 收货/质检表 | 4/4 |
| 入库质量字段 | 7/7 |
| 仓储高频索引 | 5/5 |
| 健康证表 | 1/1 |
| OA 功能开关 | `false` |
| 盘点临期阈值 | `24` 小时 |
| 健康证功能开关 | `false` |
| OA 旧菜单活动项/授权 | 0/0 |
| inventory 采购菜单 4020 | `visible=0,status=0` |
| 可见调拨组件路由 | 1 |

正向快照：

- 43 个仓储菜单原值哈希：`423cbe862a3b5964764de7e59220d6edb2dbeb0dc60e2ff33ad20337a0c12fcd`
- 250 条仓储角色授权原值哈希：`46577557dd249259980848822b5c725debe7ebeaa09b12406aa914931b79371d`

按逆序执行统一待办回滚和仓储导航回滚，再各执行第二次，均成功。回滚后：

- 43 个菜单逐字段哈希与发布前相同。
- 250 条仓储角色授权哈希与发布前相同。
- 9 个 OA 菜单状态哈希与发布前相同：`ea9199ea2b14181f39cd72b520815c9349033ecf97b130ed557e086bc29d1830`。
- 45 条 OA 角色授权哈希与发布前相同：`624b3a612a1532e747cac505c7e695117ca49db17568c65df75fe04c113a213f`。
- 本批次新建的 OA/盘点配置安全移除；两个健康证配置保留。
- 盘点 8 个字段、4 个收货/质检表、5 个索引和健康证表均保留，无破坏性删除。

## 5. 发布包 SHA-256

| 顺序 | 文件 | SHA-256 |
| ---: | --- | --- |
| 1 | `erp_inventory_purchase_return_item_20260713.sql` | `43d98ca23345e4f677eebdc7f572d9a75187bf6b0b101677872253a9ed6570e6` |
| 2 | `erp_inventory_stock_check_scope_20260713.sql` | `d4a9b62627f5a85d80a181edd8c21a8df5c51ea3dde7c512d3c7c8852b110f` |
| 3 | `erp_inventory_receipt_quality_20260713.sql` | `5d778e38bfc573fc0d4df7f8563220b8381de9f07c3b163eea99ea476b4e5df3` |
| 4 | `erp_inventory_performance_indexes_20260713.sql` | `1c3c7dc8d650351ab111c3f4b69c5e687cd3d303fb705cd75eed9a36658caccd` |
| 5 | `erp_inventory_warehouse_navigation_20260713.sql` | `25e164f8ea10b235d02d6d90d33d38f9c8d97d6383c878b63cd15e073a835223` |
| 6 | `erp_hr_health_certificate_20260713.sql` | `28e4245da13d5fca87b69248d98ea7b198349328bda9ce658b82ae87b75903f1` |
| 7 | `erp_unified_todo_business_scope_20260713.sql` | `ca7a90939d5b731a1cc802d7251c78b0900f910165a60e6665a39a58dcbde576` |

回滚：

- 仓储导航：`e611b8358ed61bea69ab26c614aa67f16e3b395d3c6970b820db19109ef9b5a6`
- 统一待办：`c9619e7064853ad74d3010d32cf57c791a7a32a21de1484a4b161649f56d94ea`

## 6. 自动化回归

| 范围 | 结果 |
| --- | --- |
| common 待办契约 | 12 项通过 |
| inventory 待办/盘点/调拨 | 176 项通过 |
| OA 待办/签约/采购开关 | 83 项通过 |
| system/HR 待办/健康证 | 27 项通过，其中 1 项原生 MySQL 条件测试按既有条件跳过 |
| 前端全量 | 183/183 通过 |
| 前端生产构建 | 通过；入口包 1.471 MiB / 1.500 MiB |
| SQL 静态契约 | 通过 |
| IT 后缀迁移契约 | 通过 |

前端首次全量回归暴露 `docker/copy.sh` 的历史打包入口名丢失。已保留新的版本化、manifest 驱动 SQL 复制逻辑，同时恢复 `copy_sql_files` 稳定入口；单测和全量回归均已通过。

系统 Homebrew Node 因缺少 `libsimdjson.30.dylib` 无法启动；验证使用 Codex 工作区隔离 Node `v24.14.0`，未修改用户 Homebrew 环境。

演练完成后已删除临时库 `codex_unified_todo_release_20260714`，信息架构中剩余数量为 0。

## 7. 剩余人工决策

1. 业务负责人逐单决定 18 张 OA 活动采购是正常完成还是驳回结束；不得由发布脚本代替决策。
2. 业务负责人对组织 1245 的 21 张超过 7 天草稿和 2 张已作废盘点执行取消、真实指派或保留治理工单。
3. OA 三项活动数归零后，重新执行只读预检；未通过时不得使用 `--apply`。
4. 在目标环境用真实门店、仓库、盘点人、审批人、HR 和员工账号完成 UI 验收。
