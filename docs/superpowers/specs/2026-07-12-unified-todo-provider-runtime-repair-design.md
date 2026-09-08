# 统一待办 Provider 运行态修复设计

## 目标

恢复 `7月12号` 分支中 OA、库存统一待办的摘要与列表接口，使工作台能够区分真实的零待办和 provider 故障，并防止相同的排序规则、漏迁移问题再次静默进入运行环境。

## 已确认根因

1. OA 待办事实查询把 `utf8mb4_0900_ai_ci` 的 OA 业务表文本与 `utf8mb4_general_ci` 的 `sys_dept.dept_name/dept_type` 放进同一个 `UNION ALL`，MySQL 8 返回错误 1271。
2. 库存待办查询依赖 `inv_stock_check_approval_instance` 和 `inv_stock_check_approval_task`，当前运行库没有执行 `erp_inventory_stock_check_approval_20260710.sql`，连同盘点审批字段和备份表也全部缺失。
3. 库存业务表同样使用 `utf8mb4_0900_ai_ci`，因此补齐审批表后，库存 `UNION ALL` 还会在组织名称和类型列上遇到与 OA 相同的排序规则冲突。
4. 现有测试验证 Mapper 绑定和迁移脚本文本，但没有在真实 MySQL 模式下验证运行库的 schema 与查询执行结果。
5. 第一轮运行态修复组织列后，真实接口进一步证明库存的 `title` 和 `summary` 输出位也混入了 `sys_dept.dept_name`：风险标题为 `utf8mb4_general_ci`，采购质检摘要被 MySQL 推导为 `utf8mb4_bin`，其余库存事实多为 `utf8mb4_0900_ai_ci`，仍会在 `UNION ALL` 报错 1271。
6. MySQL 5.7 会把包含 `COUNT(DISTINCT ...)` 的两条风险摘要 `CONCAT` 推导成 `binary`；这两条表达式必须先 `CONVERT(... USING utf8mb4)`，才能应用 `utf8mb4_general_ci`。MySQL 8 不会暴露这个兼容性差异。
7. 审查时在干净克隆库重复执行迁移，发现旧确认权限的停用备注会在每次执行时重复追加；脚本虽然不报错，但数据内容不幂等。
8. 只修改 `CREATE TABLE IF NOT EXISTS` 的尾部排序规则不能修复已由旧版脚本创建成 `utf8mb4_0900_ai_ci` 的审批表，因此升级路径也必须显式收敛既有表。

## 方案选择

采用“查询边界归一化 + 显式迁移排序规则 + 克隆库验证”的方案。

- OA Mapper 的每个 `dept_name`、`dept_type` 输出表达式显式使用 `utf8mb4_general_ci`；库存 Mapper 的每个 `title`、`summary`、`dept_name`、`dept_type` 输出表达式都显式使用同一排序规则。该排序规则兼容项目 Dockerfile 中的 MySQL 5.7，也能在当前 MySQL 8 运行库中消除 0900/general/bin 混用。
- 盘点审批迁移创建的新表显式声明 `COLLATE=utf8mb4_general_ci`，并仅在既有审批表排序规则不一致时执行条件式 `CONVERT TO CHARACTER SET`，覆盖新装和旧版升级两条路径。
- 旧确认权限的停用备注使用内容 guard；已经包含停用标记时不更新备注和更新时间，重复执行不会积累重复文案。
- 不对全部 OA、库存表执行整表字符集转换，避免大范围锁表、数据重写和回滚成本。
- 不在查询失败时跳过待办类型；provider 仍然保持 fail-closed，防止漏待办被伪装成零待办。

## 代码与测试改动

### OA

- 在 `OaTodoMapper.xml` 的空事实、组织事实和个人事实分支中统一 `dept_name/dept_type` 的 collation。
- 在 `OaTodoMapperBindingTest` 中断言所有这两个输出列都带显式 collation，防止后续分支遗漏。

### 库存

- 在 `InvTodoMapper.xml` 的空事实及全部十五类事实中统一 `title/summary/dept_name/dept_type` 的 collation。
- 两条风险聚合摘要在 collation 前显式转换为 `utf8mb4`，避免 MySQL 5.7 的 binary 表达式错误 1253。
- 在 `InvTodoMapperBindingTest` 中分别断言四个文本输出位的总投影数和显式归一化数，防止风险标题或混合摘要重新引入冲突。
- 在普通版和 Docker 版盘点审批迁移中为两个新表指定相同 collation，并为既有审批表添加条件式排序规则收敛；两份 SQL 必须继续保持字节一致。
- 旧确认权限备注改为重复安全更新。
- 在 `StockCheckApprovalSqlSourceTest` 中锁定新表 collation、既有表收敛和备注幂等要求。

## 数据库实施

1. 使用一致性逻辑备份创建当前运行库的验证克隆。
2. 在从原始 dump 新建的干净克隆库上执行修改后的盘点审批迁移两次，验证首次迁移和重复执行均成功，且停用备注只有一份、第二次不改动该菜单的更新时间。
3. 将克隆库的既有审批表模拟转换为 `utf8mb4_0900_ai_ci`，再次执行迁移并校验两表自动恢复为 `utf8mb4_general_ci`。
4. 校验审批表、字段、唯一索引、备份表和历史状态分类结果。
5. 在应用当前运行库前生成带时间戳的完整逻辑备份。
6. 对当前运行库执行迁移，随后重启 `7月12号` 分支的 OA、库存服务。
7. 通过网关分别验证 OA、库存 `summary/list` 均返回业务码 200，再验证工作台不再把这两个来源标记为未知。

## 失败处理与回滚

- 克隆库迁移失败时停止，不触碰当前运行库。
- 当前库迁移失败时停止服务验证并保留错误日志，不继续叠加修复；使用迁移前逻辑备份恢复。
- 服务重启后接口仍失败时，保留已确认的 schema 状态，重新采集单一 provider 错误，不修改前端 fail-closed 逻辑。
- 不终止或替换其他工作树占用的系统服务进程；本次提交只修改 `7月12号` 分支，系统服务端口冲突单独报告。

## 完成标准

- 新增回归测试先失败、实施后通过。
- OA、库存模块目标测试和相关统一待办测试全部通过。
- 克隆库迁移可重复执行，停用备注不重复，旧版 0900 审批表能够收敛；当前库有可恢复备份且迁移成功。
- OA、库存四个运行态接口都返回业务码 200，不再出现排序规则冲突或缺表错误。
- 所有源码、测试和迁移改动提交到 `7月12号` 分支。
