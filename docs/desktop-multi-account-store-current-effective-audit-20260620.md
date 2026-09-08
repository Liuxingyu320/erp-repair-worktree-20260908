# 多账号多店铺电脑端当前有效审计报告（2026-06-20）

## 检查边界

- 检查对象：电脑网页端、多账号、多门店/仓库上下文、数据库菜单/角色/用户授权、页面按钮、弹窗、副流程和关键业务流。
- 前端：`http://localhost:1026/`
- 网关：`http://localhost:1026/dev-api` -> `http://127.0.0.1:8080`
- 运行库：`BossERP_stock_state_75c59ee`
- 账号：`admin`、`qa144822s`（店长/单门店 103）、`qa144822w`（仓库管理员/仓库 104）、`qa144822m`（店长/多门店 103、108）。
- 本轮没有修改业务代码，没有执行真实发货、收货、库存调整、盘点确认、工资计算、合同发送、日志清空、任务执行等最终写入动作。
- 内置 Browser 插件仍无法初始化：`codex/sandbox-state-meta: missing field sandboxPolicy`，本轮使用 `playwright-cli` 和 API/SQL/源码复核。

## 当前已校正的旧结论

以下旧报告中的问题在当前源码或当前运行页面中已经不成立：

1. 商品采购价/成本价已接入 `inv:cost:view`。普通门店账号商品列表、详情和商品导出不再直接暴露采购价/成本价。
   - 前端：`erp-ui/src/views/inventory/product/index.vue:111`、`:116`、`:207`、`:223`
   - 后端导出：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:85`
2. 报表中心成本指标已接入 `inv:cost:view`。实时复核 `qa144822s` 进入 `/inventory/report` 时只显示当前库存、库存预警、库存品项、销售金额，没有库存成本、采购金额、预估毛利。
   - 前端：`erp-ui/src/views/inventory/report/index.vue:46`、`:61`、`:71`、`:164`
3. 普通门店账号当前不能进入工资管理和劳动合同路由，`/oa/salary`、`/oa/labor-contract` 实时返回 404。
4. 普通门店账号库存页当前不再显示“库存调整”按钮。实时复核 `/inventory/stock` 只显示查询、重置、导出、变动日志。
5. 选择组织页现在只把门店/仓库计入“业务组织可选”，公司/集团只用于展开路径。
   - `erp-ui/src/views/select-shop/index.vue:302`
6. 采购收货、采购质检、调拨发货、调拨收货、考勤打卡/签退已有二次确认摘要。
   - `erp-ui/src/views/inventory/purchase/index.vue:636`
   - `erp-ui/src/views/inventory/transfer/index.vue:1013`
   - `erp-ui/src/views/oa/attendance/index.vue:184`

## 仍需处理的问题

### P1-01 库存变动日志仍暴露成本价

现象：`qa144822s` 没有 `inv:cost:view`，但实时访问 `/inventory/stock-log` 时表头仍包含 `成本价`。如果该门店后续产生库存流水，用户会看到成本价；导出也会带出该字段。

证据：

- 运行权限：`qa144822s` 当前敏感权限只有 `inv:transfer:approve`、`oa:salary:query`，没有 `inv:cost:view`。
- 前端列未加成本权限：`erp-ui/src/views/inventory/stock/log.vue:91`
- 后端日志导出未隐藏成本列：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockController.java:87`

建议：库存流水列表和导出统一复用 `inv:cost:view`。无权限时隐藏 `costPrice` 列，接口返回或导出层也应脱敏，避免只靠前端隐藏。

### P1-02 仓库列表接口和选店树授权口径不一致

现象：数据库中 `qa144822s` 只授权了 `103 研发部门`，选店树也只显示集团/公司/研发部门；但调用 `/system/dept/warehouse-list` 及 `purpose=replenishmentSource`、`deliverySource`、`currentWarehouse` 时仍返回 `104 仓库`。

证据：

- DB 查询：`qa144822s -> 103 研发部门 STORE`，无 `104` 授权。
- 实测 API：`/system/dept/warehouse-list?purpose=replenishmentSource&scopeDeptId=103` 返回 `104:仓库`。
- 选店树使用授权树：`erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:451`
- 仓库列表先取全量仓库：`erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml:62`

建议：明确“门店可向哪些仓库要货”和“用户可选择哪些业务组织”是不是同一套授权。如果要货仓库是业务关系，不应复用 `sys_user_shop` 的字面授权；需要单独的门店-仓库供货关系表并在前后端一致展示。若必须按用户授权过滤，则运行服务和源码需要重新核对，当前运行结果与源码预期不一致。

### P1-03 店长角色仍保留调拨审批和工资查询权限码

现象：`dz` 店长角色当前仍有 `inv:transfer:approve`、`oa:salary:query`。当前页面已经隐藏工资路由，调拨审批后端也有候选人校验，但角色权限语义仍偏宽。

证据：

- DB 角色菜单：`dz -> inv:transfer:approve`、`dz -> oa:salary:query`
- 调拨列表审批按钮只看单据状态和权限码：`erp-ui/src/views/inventory/transfer/index.vue:81`
- 后端审批入口权限：`erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java:101`

建议：普通店长默认不授予审批权限码；审批资格由审批规则候选人控制，菜单权限只授予“审批人/区域经理/总部”等角色。工资查询权限也应从店长默认角色移除，避免 API 直连或未来菜单恢复后暴露薪资数据。

### P2-01 用户导入缺少创建后授权闭环

现象：手工新增用户已有“是否现在配置门店/仓库”的下一步提示，但用户导入弹窗只有上传、覆盖更新和下载模板，没有提示导入后必须配置角色、店铺/仓库授权，也没有导入成功后的“筛选未配置用户/批量授权”入口。

证据：

- 导入弹窗：`erp-ui/src/components/ExcelImportDialog/index.vue:1`
- 用户页调用导入弹窗：`erp-ui/src/views/system/user/index.vue:192`
- 新增用户已有授权提示：`erp-ui/src/views/system/user/index.vue:477`

建议：用户导入成功后展示新增/更新/失败数量，并提供“查看未分配角色”“查看未授权店铺/仓库”“批量配置角色”“批量配置组织授权”。

### P2-02 OA 首页引导了可能不存在的采购申请入口

现象：OA 首页固定文案提示“请从左侧菜单进入：采购申请 / 我的待办 / 我的已办”，但当前运行菜单中普通店长只看到 OA 首页、我的待办、我的已办、我的考勤，未看到采购申请入口。

证据：`erp-ui/src/views/oa/index.vue:5`

建议：OA 首页按实际下发菜单动态展示入口。未启用采购申请时不要在首页文案中引导用户去找不存在的菜单。

### P2-03 部分系统高风险确认仍偏模板化

现象：操作日志/登录日志清空仍使用“数据项”模板语气；定时任务启停和立即执行只提示任务名，没有调用目标、参数、cron、影响范围。

证据：

- 操作日志清空：`erp-ui/src/views/system/operlog/index.vue:269`
- 登录日志清空：`erp-ui/src/views/system/logininfor/index.vue:224`
- 定时任务立即执行：`erp-ui/src/views/monitor/job/index.vue:395`

建议：清空类动作要求输入“清空”；定时任务执行前展示任务名、调用目标、cron、参数和可能影响的业务模块。

### P2-04 导出确认和字段敏感提示仍不统一

现象：商品、报表、调拨等部分页面已有较完整导出确认，但考勤、库存日志、系统配置类导出仍缺少当前组织、筛选条件、敏感字段说明。

证据：

- 考勤导出文案：`erp-ui/src/views/oa/attendance/index.vue:231`
- 库存日志导出按钮：`erp-ui/src/views/inventory/stock/log.vue:46`
- 通用导入模板下载仍直接下载：`erp-ui/src/components/ExcelImportDialog/index.vue:92`

建议：统一导出/下载确认组件。业务数据导出展示组织范围、筛选条件、敏感字段、数据使用提醒；模板下载展示模板版本、适用模块和字段口径。

### P3-01 仓库选择下拉只有仓库名，缺少路径和用途解释

现象：`WarehouseSelect` 选项只显示 `deptName`。未来多仓库同名、跨公司仓库或门店-仓库供货关系复杂时，用户难以确认目标仓库。

证据：`erp-ui/src/views/inventory/components/WarehouseSelect.vue:139`

建议：显示“仓库名 + 上级公司/路径 + 用途标签”，例如 `仓库 / 金英灵韵 / 当前可要货`。

### P3-02 前端构建体积偏大

现象：`npm run build:prod` 通过，但出现体积 warning：入口 `app` 合计约 `1.66 MiB`，`chunk-3061e5c5` 约 `1.02 MiB`，`chunk-elementUI` 约 `751 KiB`。

影响：电脑端首次加载和低带宽环境下体验会变慢。

建议：后续按路由拆包复查大 chunk 来源，延迟加载不常用模块，压缩或替换大图资源。

## 当前表现较好的点

1. `sys_user_shop`、选店页和前端请求头已从 `localStorage` 风险切到 `sessionStorage` 上下文，跨 Tab 污染风险降低。
2. 普通门店账号和仓库账号菜单明显分离：门店侧没有仓库采购/供应商入口，仓库侧没有门店销售/客户/OA 入口。
3. 商品成本、报表成本、商品导出成本已接入 `inv:cost:view`。
4. 多门店账号切换 `103` 和 `108` 后，首页和库存页上下文能同步变化。
5. 高风险库存动作已有更具体的确认摘要，比早期模板确认明显改善。

## 验证记录

- `find test -maxdepth 1 -type f -name '*.test.js' -exec node {} \\;`：通过，输出覆盖 `desktopAuditRemediation`、`inventoryReportApi`、`userShopNavigationUx`、`desktopContextUx`、`warehouseScopeUx`、`managementBacklogCompletion`、`p0UxHardening` 等现有审计脚本。
- `npm run build:prod`：通过，有构建体积 warning。
- `playwright-cli` 实时复核：
  - `qa144822s /inventory/report`：未显示库存成本、采购金额、预估毛利。
  - `qa144822s /inventory/stock`：未显示库存调整。
  - `qa144822s /oa/salary`、`/oa/labor-contract`：返回 404。
  - `qa144822s /inventory/stock-log`：仍显示成本价列。
- SQL/API 复核：
  - `qa144822s` 仅授权 `103 STORE`。
  - `dz` 仍有 `inv:transfer:approve`、`oa:salary:query`。
  - `warehouse-list` 仍返回 `104 仓库`。

## 建议处理顺序

1. 先修复库存变动日志成本价列和导出脱敏。
2. 统一仓库列表、选店树、门店-仓库供货关系三者的授权口径。
3. 收窄 `dz` 店长角色中残留的审批/薪资查询权限。
4. 补齐用户导入后的角色和组织授权闭环。
5. 清理 OA 首页、系统高风险确认、导出确认、仓库选择下拉等体验问题。
6. 最后处理构建体积和首屏性能。
