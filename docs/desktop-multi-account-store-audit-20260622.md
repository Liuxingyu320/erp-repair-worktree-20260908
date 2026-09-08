# 多账号多店铺桌面端审计记录（2026-06-22）

## 检查边界

- 本轮按“先不改代码”执行，仅新增本报告，不修改业务实现、SQL 或配置。
- 本轮使用本地网页端 `http://localhost:1026/`，前端开发服务由 `erp-ui` 执行 `npm run dev -- --port 1026` 启动。
- 数据库为本机 MySQL `BossERP_stock_state_75c59ee`，网关为 `http://localhost:8080`。
- 已实测管理员、单门店账号、仓库账号；多门店账号做了 API 和权限数据核对；并按允许范围新建了一个审计门店账号。
- 未执行有业务写入风险的最终动作：删除、导出文件落盘确认、库存调整提交、销售单提交、调拨提交、打卡提交。
- 已真实创建审计用户 `qa_audit_62201`，并在店铺授权页授权 `103` 研发部门，用于验证“新增用户 -> 授权 -> 首次登录”闭环。

## 账号与组织范围

| 账号 | 密码 | 角色 | 用户部门 | 授权组织 | 本轮用途 |
| --- | --- | --- | --- | --- | --- |
| `admin` | `admin123` | 超级管理员 | `103` | 7 个经营组织 | 全菜单、用户管理、店铺选择 |
| `qa144822s` | `Audit123` | 店长 `dz` | `103` | `103` 研发部门 | 单门店桌面端实测 |
| `qa144822w` | `Audit123` | 仓库 `ck` | `104` | `104` 仓库 | 仓库桌面端实测 |
| `qa144822m` | `Audit123` | 店长 `dz` | `103` | `103`、`108` | 多门店 API 权限核对 |
| `qa_audit_62201` | `Audit123` | 店长 `dz` | `103` | `103` 研发部门 | 本轮通过网页端新增并登录验证 |

## 数据库与运行态概览

- 本轮开始前 `sys_user` 为 24 条、`sys_user_shop` 为 42 条；创建并授权 `qa_audit_62201` 后分别为 25 条和 43 条。
- 经营组织主要包括集团/公司、门店和仓库：`100` 金英灵韵集团、`101/102/200` 公司、`103/105/106/107/108/109` 门店、`104` 仓库。
- 早前进销存审计快照：`inv_product` 162 条、`inv_product_category` 30 条、`inv_supplier` 17 条、`inv_stock` 2 条、`inv_stock_log` 5 条、`inv_customer` 0 条。
- 早前 OA 快照有合同和考勤少量数据：`oa_labor_contract` 9 条、`oa_labor_contract_event` 59 条、`oa_attendance_record` 1 条；`oa_purchase`、`oa_purchase_comment`、`oa_salary_record` 均为 0 条。
- 固定资产相关前端与后端代码存在；运行态库已有 4 张 `oa_fixed_asset%` 表和 8 个固定资产菜单/按钮，但业务数据为 0 且目前只授权 admin。非运行态 `BossERP` 曾缺固定资产表和菜单，旧结论已修正为口径漂移。
- 2026-06-23 后续复核运行态：`localhost:8080` Java 网关/服务返回 200 且 `/actuator/health` 为 UP；`localhost:1026` 曾短时无监听，后续已重新启动前端 dev server 并完成销售、调拨、固定资产页面复测。`erp-modules-system/target/classes` 已含用户配置状态 mapper，打包 jar 与 8080 运行态仍是旧口径，已列为 P2-71。
- 2026-06-23 曾误把同机非运行态 `BossERP` 当作当前库复核，看到 `sys_user=7`、`sys_user_shop=0`、`inv_product=8`、`inv_stock=0`、`inv_stock_log=0`、`inv_sales_order=9`、`inv_sales_detail=14`、`oa_dingtalk_raw_record=316`、`oa_dingtalk_raw_summary=156`、`oa_excel_import_detail=100`、`oa_attendance_record=0`。后续已确认这些数字不代表当前电脑网页端运行态，只作为环境漂移取证保留。
- 2026-06-23 继续复核系统/集成类表：运行态 `BossERP_stock_state_75c59ee` 当前 `sys_config=8`、`sys_job=3`、`sys_job_log=0`，`QRTZ_JOB_DETAILS/QRTZ_TRIGGERS=0`；工作流业务表 `wf_%` 当前不存在，Activiti 基础表只有 `ACT_RE_DEPLOYMENT=1`、`ACT_RE_PROCDEF=1`、`ACT_GE_BYTEARRAY=1`、`ACT_GE_PROPERTY=6`、`ACT_ID_PROPERTY=1`、`ACT_RU_TASK=0` 等少量引擎元数据。早前 `sys_config=19`、`sys_job=4`、`wf_instance=6`、`ACT_RU_TASK=2` 等结论来自旧/非运行态口径，已在对应问题中修正为环境漂移风险。
- 2026-06-23 对非运行态 `BossERP` 再次精确复核发现该库又有变化：`sys_user` 表共 8 条但有效未删除账号仍为 7 条，`sys_role=15`、`sys_dept=12`、`inv_product_category=11`、`inv_supplier=3`；劳动合同正式数据在该库为 `oa_labor_contract=0`、`oa_labor_contract_event=0`、`oa_company_seal_config=0`，只剩 `oa_labor_contract_template=2`。这说明同机 schema 之间存在明显漂移，涉及数据闭环的问题必须记录 datasource URL、schema 名、查询时间和快照。
- 2026-06-23 对非运行态 `BossERP` 调拨 schema 复核发现其不包含早前快照中的 `inv_transfer_approval_*`、`inv_transfer_shipment*` 和 `inv_transfer_status_log`，只剩 `inv_transfer_order/detail/receipt` 及清理备份表；后续运行态库已确认这些表实际存在，旧缺表结论已改为环境漂移问题。
- 2026-06-23 对非运行态 `BossERP` 的精确计数显示核心业务表为：`sys_user=8`、`sys_user_shop=0`、`inv_product=8`、`inv_product_category=11`、`inv_supplier=3`、`inv_customer=0`、`inv_purchase_order=0`、`inv_purchase_detail=0`、`inv_stock=0`、`inv_stock_log=0`、`inv_stock_check=0`、`inv_stock_check_detail=0`、`inv_transfer_order=0`、`inv_transfer_detail=0`、`inv_sales_order=9`、`inv_sales_detail=14`、`oa_labor_contract=0`、`oa_attendance_record=0`。这些数字不作为当前网页端运行态结论，只用于说明同机非运行态 schema 与运行态严重不一致。
- 2026-06-23 继续复核非运行态 `BossERP` 多账号基础数据：`sys_user` 总数仍为 8，其中有效未删除账号 7、已删除账号 1；`sys_user_shop` 仍为 0。该库有效组织树为 `GROUP=1`、`COMPANY=2`、`STORE=7`、`WAREHOUSE=2`，仓库节点为 `200 / 123` 和 `201 / 仓库`。运行态库的组织授权另见后续 `BossERP_stock_state_75c59ee` 记录。
- 2026-06-23 运行态 API 进一步暴露数据源口径分裂：`9201 /user/info/admin`、`/user/info/ry` 返回的昵称、部门、角色和密码哈希与直查 MySQL `BossERP.sys_user/sys_user_role` 不一致。后续电脑端页面结论必须同时标注“直查 SQL 口径”和“运行态 API 口径”，详见 P1-23。
- 2026-06-23 继续追踪后确认，当前运行态 `erp-system` 配置和 `target/classes` 均指向 `jdbc:mysql://127.0.0.1:3306/BossERP_stock_state_75c59ee`；该运行态库当前 `sys_user=25`、有效账号 25、`sys_user_shop=43`、`inv_product=162`、`inv_stock=2`、`oa_labor_contract=9`。前述 `BossERP` 中 `sys_user=8`、`sys_user_shop=0`、`inv_product=8` 等结论只代表非运行态同机 schema，不能直接作为电脑网页端运行态结论。
- 2026-06-23 本轮补充复核运行态 `BossERP_stock_state_75c59ee` 进销存和薪资/合同 schema：`inv_product=162`、`inv_sales_order=0`、`inv_sales_detail=0`、`inv_stock=2`、`inv_stock_log=5`、`inv_transfer_order=2`、`inv_transfer_detail=2`、`inv_transfer_approval_rule=1`、`inv_transfer_shipment=2`、`inv_transfer_status_log=6`、`sys_user_salary_scheme=1`、`oa_salary_config=1`、`oa_salary_record=0`、`oa_labor_contract=9`、`oa_labor_contract_event=59`、`oa_company_seal_config=1`。商品宽表字段、销售 `customer_name/shop_dept_id`、库存 `version/batch_no/warehouse_id`、调拨审批/发货批次/状态流水表均存在，P1-16、P1-18、P1-19、P1-20、P1-21、P1-22 已按运行态口径修正为“非运行态 schema 漂移/误判风险”。
- 2026-06-23 曾对非运行态 `BossERP` 做全表覆盖检查：该库共有 212 张表，报告已覆盖所有非空业务表；此前未在报告中出现的剩余表精确计数均为 0。空内部表包括 `ACT_EVT_LOG`、`ACT_HI_ATTACHMENT`、`ACT_HI_DETAIL`、`ACT_HI_ENTITYLINK`、`ACT_HI_TSK_LOG`、`ACT_ID_BYTEARRAY`、`ACT_ID_GROUP`、`ACT_ID_INFO`、`ACT_ID_MEMBERSHIP`、`ACT_ID_PRIV`、`ACT_ID_PRIV_MAPPING`、`ACT_ID_TOKEN`、`ACT_ID_USER`、`ACT_PROCDEF_INFO`、`ACT_RE_MODEL`、`ACT_RU_DEADLETTER_JOB`、`ACT_RU_ENTITYLINK`、`ACT_RU_EVENT_SUBSCR`、`ACT_RU_EXTERNAL_JOB`、`ACT_RU_HISTORY_JOB`、`ACT_RU_JOB`、`ACT_RU_SUSPENDED_JOB`、`ACT_RU_TIMER_JOB`、`FLW_RU_BATCH`、`FLW_RU_BATCH_PART`、`QRTZ_BLOB_TRIGGERS`、`QRTZ_CALENDARS`、`QRTZ_CRON_TRIGGERS`、`QRTZ_FIRED_TRIGGERS`、`QRTZ_LOCKS`、`QRTZ_PAUSED_TRIGGER_GRPS`、`QRTZ_SIMPLE_TRIGGERS`、`QRTZ_SIMPROP_TRIGGERS`、`undo_log`。空历史备份表包括 `inv_batch_clear_backup_20260607_1630`、`inv_cost_event_clear_backup_20260607_1630`、`inv_defective_stock_clear_backup_20260607_1630`、`inv_payable_clear_backup_20260607_1630`、`inv_pick_task_clear_backup_20260607_1630`、`inv_pick_task_detail_clear_backup_20260607_1630`、`inv_purchase_contract_clear_backup_20260607_1630`、`inv_purchase_order_version_clear_backup_20260607_1630`、`inv_purchase_return_clear_backup_20260607_1630`、`inv_purchase_return_detail_clear_backup_20260607_1630`、`inv_qc_hold_clear_backup_20260607_1630`、`inv_receipt_clear_backup_20260607_1630`、`inv_receipt_detail_clear_backup_20260607_1630`、`inv_shipment_clear_backup_20260607_1630`、`inv_transfer_receipt_clear_backup_20260607_1630`。
- 2026-06-24 对当前运行态 `BossERP_stock_state_75c59ee` 重新做全表名反查：报告未逐字命中的剩余表均为 0 行，集中在 Activiti 空运行/历史表 `ACT_HI_ACTINST/COMMENT/IDENTITYLINK/VARINST`、`ACT_RU_ACTINST/EXECUTION/IDENTITYLINK/VARIABLE`，以及若干空清理备份表（销售、发货通知、出库、采购退货、盘点、调拨 20260607_1640/20260608_1530 批次）。这轮未发现新的非空业务表遗漏；已有非空备份表仍按 P3-29 处理。
- 2026-06-25 再次对当前运行态做数据库对象覆盖：`SHOW FULL TABLES` 均为 `BASE TABLE`，共 160 张表，其中 67 张非空表已全部在报告中出现；未命中的 26 张表均为 0 行，集中在 Activiti 空运行/历史表和清理备份空表。当前 schema 没有 view，有 6 个状态审计触发器和 2 个存储过程；触发器已由 P2-91 覆盖，2 个动态 DDL 存储过程新增 P2-160。
- 2026-06-24 再次对照运行态与非运行态工作流口径：`erp-system/erp-inventory/erp-oa` 配置仍指向 `BossERP_stock_state_75c59ee`，该库 `wf_%` 表数为 0、`workflow:%` 菜单/权限为 0、`ACT_RU_TASK=0`、`ACT_HI_PROCINST=0`、`ACT_RE_PROCDEF=1`；同机非运行态 `BossERP` 则有 15 张 `wf_%` 表、43 个工作流菜单/权限、`ACT_RU_TASK=2`、`ACT_HI_PROCINST=7`、`ACT_RE_PROCDEF=8`。本机 `/auth/login` 后调用 `/system/menu/getRouters` 未返回 `workflow/*` 路由，继续证明工作流属于残留 schema/交付口径漂移，已补强 P1-14/P1-15 证据。

## 页面/菜单对应补充盘点

- 首次盘点时数据库 `sys_menu` 中有 54 个菜单/页面节点（`M/C`）、183 个按钮权限节点（`F`）；2026-06-23 最新运行态复核为 `sys_menu=249`，其中菜单/页面节点 `M/C=57`、按钮节点 `F=192`，带本地组件路径的页面菜单 44 个。
- 首次盘点的 42 个带 `component` 的页面菜单均能在 `erp-ui/src/views` 下找到对应 `.vue` 文件；旧/非运行态曾出现的工作流 11 个页面、薪资模板 1 个页面和考勤 Excel 导入 1 个页面不属于当前运行态菜单，但其环境漂移和交付口径问题已分别列为 P1-14、P2-73、P2-74。
- 2026-06-24 继续复核工作流页面：当前网页端路由接口不返回 `workflow/*`，但非运行态 `BossERP.sys_menu` 中仍有工作流父菜单、7 个已绑定给 admin 的工作流页面和 21 个按钮权限；当前工程没有 `erp-ui/src/views/workflow`、`erp-ui/src/api/workflow`，`erp-modules/erp-workflow` 与 `erp-api/erp-api-workflow` 目录为空。因此这组菜单若被导入或误连非运行态库，会立刻变成“菜单可见但页面/接口缺实现”的交付失败。
- 当前存在“前端文件存在但运行入口/角色授权不完整”的页面：固定资产配置、固定资产维修上报、若干详情/选择/弹窗子页面。其中固定资产运行态表和菜单已存在，但数据为空、只授权 admin，且早前直连仍 404，已单独列为问题。
- 当前隐藏或停用但有前端文件或历史配置的菜单包括：字典管理、参数设置、采购申请、采购计划、本店库存、批次管理、钉钉同步/打卡明细/打卡汇总、库存变动日志。库存变动日志虽然隐藏，但门店账号可通过路由访问并看到成本价，已单独列为问题；采购计划、本店库存、批次管理和钉钉旧入口缺少当前前端组件，另列 P3-48。
- 2026-06-23 继续从源码抽取真实权限码：仅统计 `v-hasPermi`、`hasPermi(...)`、`@RequiresPermissions(...)` 里的权限字符串，共 207 个权限码。报告已按页面、按钮组和流程覆盖标准 CRUD 权限，但仍有 138 个源码权限码没有逐字出现在报告文本中，主要集中在已检查页面的 `add/edit/remove/query/export/list/submit/confirm/receive/deliver` 等按钮组，例如分类、客户、商品、采购、采购退货、销售、销售退货、库存盘点、供应商、调拨、定时任务、固定资产、劳动合同、系统基础资料和代码生成。当前新增风险仍集中在已单列的权限树缺失或漂移、重复、无效权限问题（P2-75、P2-79、P3-79、P3-80、P3-81、P3-84）；后续若需要逐权限矩阵，应把 207 个权限码导出成附录，而不是只靠正文自然语言命中。
- 2026-06-24 复核当前运行态权限树发现，`inv:report:list`、`inv:audit:list`、`oa:salary:*`、`inv:transfer:rule:*`、`inv:transfer:records*`、`inv:transfer:approve` 和 `inv:cost:view` 已重新出现在 `sys_menu` 中；这推翻了 2026-06-23 “当前缺失”的部分口径。相关问题已改为权限树在审计期间发生回填/漂移，仍需冻结 schema 与菜单版本，详见 P2-75、P2-77。
- 2026-06-24 再次做权限码差集复核：当前数据库 `sys_menu.perms` 有 226 个唯一权限码，源码实际出现 207 个。源码需要但权限树没有节点的仍只有 `file:upload/file:delete`，已按 P2-76 处理；数据库有但源码未引用的 21 个里，采购/销售/退货/盘点 `edit`、调拨审批轨迹、系统查询和批量强退等已分别在 P2/P3 权限问题中覆盖。剩余 `inv:parent:list`、`oa:index:view`、`oa:fixedAsset:list`、`monitor:sentinel/list`、`monitor:nacos:list`、`monitor:server:list`、`tool:swagger:list` 属于父菜单、页面入口或外链菜单权限，源码不会用 `v-hasPermi`/`@RequiresPermissions` 判断；其可用性问题已按 OA 首页、固定资产授权、监控外链和系统接口菜单处理，本轮不另列新问题。

## 2026-06-23 系统侧补充盘点

- 继续使用 `http://localhost:1026/` 和 `http://localhost:8080`，2026-06-23 00:05 前端和网关均返回 200。
- 系统基础配置页补测：角色管理、菜单管理、部门管理、岗位管理、通知公告、字典管理、参数设置均能打开，未出现错误覆盖层或控制台错误；部门管理已显示“类型”列，可区分集团、公司、门店、仓库。
- 日志管理真实前端路由为 `/system/log/operlog`、`/system/log/logininfor`，两页能打开；直连 `/system/operlog`、`/system/logininfor` 会进入 404，这是父级“日志管理”路径为 `log` 后形成的嵌套路由结果。
- 系统监控补测：在线用户、定时任务能打开；Sentinel、Nacos、Admin 三个外链菜单在数据库中启用可见，但当前本地端口不可达，已单独列为问题。
- 薪资配置裸路由 `/system/salary` 本轮复测出现空白壳；带任意 query 的 `/system/salary?probe=1` 或角色入口 `/system/salary?roleId=...` 能展示 2 个薪资方案、17 个档位，但角色入口仍只显示通用员工绑定页。调拨审批配置 `/system/transfer-rules` 当前展示 1 条规则，页面字段与数据库数量对应。
- 早前 OA 补测：OA 首页、我的待办、我的已办、我的考勤、工资管理、劳动合同均能打开；采购申请菜单和按钮权限仍处于隐藏/停用状态。

## 2026-06-23 库存和主数据补充盘点

- 早前库存主数据快照中数据库共有 156 张表；本轮重点复核了进销存、仓库、OA 和系统菜单相关表。当时核心数量包括：`inv_product` 162、`inv_product_category` 30、`inv_supplier` 17、`inv_customer` 0、`inv_stock` 2、`inv_stock_log` 5、`inv_purchase_order` 0、`inv_sales_order` 0、`inv_stock_check` 1、`inv_transfer_order` 2。
- 仓库上下文补测 `/cangku/product`、`/cangku/category`、`/cangku/supplier`、`/cangku/purchase`、`/cangku/purchaseReturn`、`/cangku/stock`、`/cangku/transfer`、`/cangku/transfer-records`。当时商品、分类、供应商列表数量与数据库对应；采购和采购退货为空时页面能打开。
- 管理员在 `仓库：仓库` 上下文补测门店侧进销存路由 `/inventory/sales`、`/inventory/customer`、`/inventory/salesReturn`、`/inventory/transfer`、`/inventory/deliveryNotice`、`/inventory/transfer-records`、`/inventory/stock`、`/inventory/stockCheck`、`/inventory/report`、`/inventory/stock-log`。多数页面能按当时数据展示空态或列表，但库存页存在“组织不匹配仍显示危险操作按钮”的问题，已单独列为 P2-11。
- 直接访问 `/`、`/index`、`/cangku/product`、`/inventory/sales`、`/inventory/customer`、`/inventory/report`、`/oa/todo`、`/system/user`、`/system/log/logininfor` 均返回 HTTP 200，未发现这些业务路由在刷新/直连时服务器层 404。

## 2026-06-23 系统管理和监控按钮补充盘点

- 早前数据库系统侧数量：`sys_user` 25、`sys_role` 11、`sys_dept` 11、`sys_post` 10、`sys_dict_type` 10、`sys_dict_data` 29、`sys_config` 8、`sys_notice` 0、`sys_job` 3、`sys_job_log` 0、`sys_salary_scheme` 2、`sys_salary_scheme_item` 17、`sys_role_salary_scheme` 4、`sys_user_salary_scheme` 1。
- 补测直连页面：`/system/user`、`/system/user-auth/role/122`、`/system/role`、`/system/role-auth/user/103`、`/system/menu`、`/system/dept`、`/system/post`、`/system/notice`、`/system/dict`、`/system/dict-data/index/1`、`/system/config`、`/system/shop`、`/monitor/job-log/index/0`、`/user/profile` 能渲染主要内容，未出现错误覆盖层；`/system/salary` 裸路由复测为空白，带 query 后可渲染，另列 P2-42。
- 补测只读弹窗/副页面：用户新增、用户导入、角色新增、角色分配用户、菜单新增、部门新增、岗位新增、公告新增、字典新增、字典数据新增、参数新增、定时任务新增均能打开；薪资配置在可渲染状态下能打开新增/绑定类弹窗。未点击提交、删除、强退、执行一次、清空或导出确认。
- 2026-06-24 继续只读复核部门管理和菜单管理源码：部门类型层级、删除引用拦截和菜单写权限问题沿用既有结论；菜单管理查询表单的“状态”字段与初始化参数不一致，已新增 P3-88。
- 2026-06-24 继续只读复核字典管理和字典数据副页面：当前权限树只有 `system:dict:list/query/add/edit/remove/export`，没有独立字典项权限；`sys_dict_data` 只有主键没有同类型键值唯一约束；10 个内置字典类型均可通过页面修改类型编码或停用。已新增 P3-89、P3-90、P3-91。
- 2026-06-24 继续只读复核用户和角色状态开关：用户管理、角色管理列表的状态 switch 没有前端权限指令，但后端状态修改接口分别要求 `system:user:edit`、`system:role:edit`。当前运行态 `common/zjl` 同时持有 list/edit，所以不立即触发 403；后续拆只读角色时会暴露可点但失败的按钮，已新增 P3-104。
- 2026-06-24 继续只读复核系统参数读取边界：当前 `sys_config` 只有 8 条但包含 `sys.user.initPassword=123456`，参数详情和按键名查询接口只要求登录，普通门店、仓库和运营角色即使没有 `system:config:*` 也可按 key/id 读取，已新增 P2-111。
- 2026-06-24 继续只读复核仓库列表接口：`/system/dept/shop-tree` 会按用户授权返回组织树，但 `/system/dept/warehouse-list` 在未传 `purpose` 时会返回全量启用仓库，采购收货选择器和移动端兜底存在无参数调用，已补强 P2-01。
- 2026-06-24 继续只读复核用户批量导入闭环：用户导入模板只覆盖部门编号、账号、昵称、邮箱、手机号、性别和状态，服务层只写 `sys_user`，不会写角色、岗位或店铺/仓库授权，导入成功后也不引导补授权，已新增 P2-112。
- 2026-06-24 继续只读复核用户详情和分配角色副页面：`/system/user/{userId}`、`/system/user/authRole/{userId}` 响应体均包含 `password` BCrypt 哈希，页面不展示但浏览器网络响应可见，已补强 P2-113。
- 2026-06-24 继续只读复核个人中心接口：`/system/user/profile` 对任意登录用户返回当前账号 `password` BCrypt 哈希，页面不展示但网络响应可见，已新增 P2-114。
- 2026-06-24 继续只读复核桌面端启动用户信息接口：`/system/user/getInfo` 每次加载当前用户、角色和权限时也返回 `user.password` BCrypt 哈希，已新增 P2-115。
- 2026-06-24 继续只读复核角色授权用户副页面：角色列表隐藏超级管理员角色操作，但 `/system/role-auth/user/1` 对应接口仍可加载 admin 角色成员和候选用户，授权/取消接口源码缺少超级管理员角色保护，已新增 P2-116。
- 2026-06-24 继续只读复核角色分配用户候选列表：`/system/role/authUser/unallocatedList?roleId=103` 虽返回 `deptId` 和联系方式，但 `dept.deptName/deptType` 为空，候选真实包含集团、公司、仓库、门店和无部门用户，已补强 P2-36。
- 角色管理“更多 -> 薪资配置”会跳转到 `/system/salary?roleId=...`，但薪资页不识别 `roleId`，当前角色薪资绑定数据也没有可维护入口，已单独列为 P1-08。

## 2026-06-23 OA 页面、按钮和流程补充盘点

- OA 菜单状态：2026-06-25 运行态复核显示，OA 首页、我的待办、我的已办、我的考勤、工资管理、劳动合同和固定资产相关页面为启用可见；采购申请 `visible=1`、`status=1`，即隐藏且停用。旧口径中的“考勤 Excel 导入”当前已不在运行态菜单中，见 P2-74/P2-80。
- 当前运行态 OA 数据量：`oa_purchase=0`、`oa_purchase_comment=0`、`oa_salary_record=0`、`oa_labor_contract=9`、`oa_labor_contract_event=59`、`oa_labor_contract_template=2`、`oa_company_seal_config=1`、`oa_attendance_record=1`。
- 直连页面：`/oa/index`、`/oa/todo`、`/oa/done`、`/oa/salary` 可渲染；`/oa/attendance` 既有补测可渲染，本轮细化首次直连只剩壳层，改走 `/redirect/oa/attendance` 后成功进入考勤页，需在路由稳定性回归中继续复测；`/oa/labor-contract` 当前复测为空白壳，另列 P2-45；`/oa/purchase` 进入 404；固定资产正确动态路由是 `/oa/fixed-asset/config`、`/oa/fixed-asset/repair`，本轮可渲染，旧 `/oa/fixedAsset/*` 直连仍 404。
- 只读按钮和弹窗：考勤“上班打卡”确认框、工资“工资参数”弹窗、工资“计算本月工资”确认框能打开；劳动合同弹窗因当前路由空白未能继续作为正常项确认，源码中仍包含“验 hash”“发起签约”“详情”“模板新增/编辑”“企业章配置”弹窗。
- 早前数据与前端对应：劳动合同数据库存在 9 条合同、2 个内置模板和 1 个企业章，但当时电脑端路由空白，无法再确认页面与数据对应；待办、已办、工资列表在当时 0 条数据下展示空态。采购申请属于“代码存在但运行菜单/路由不可用”的状态；固定资产本轮确认正确 hyphen 路由可渲染，但表为空、授权单一和空数据表单体验仍纳入问题清单。
- 2026-06-24 复核 OA 采购审批当前口径：`我的待办/我的已办` 及待办审批、待办导出、已办导出已启用可见并授给 `admin,dz,yyjl,zjl`；`采购申请` 菜单已回填为 `oa/purchase/index` 和 `oa:purchase:list`，但仍停用隐藏。当前 `oa_purchase=0`、`oa_purchase_comment=0`、运行/历史任务均为 0，采购审批表现为“审批工作台可见、申请入口不可见”的半上线状态，已修正 P2-78/P2-92。
- 早前考勤细化复测：`/oa/attendance` 在 `仓库：仓库` 上下文显示“上班打卡”、“视图 我的/全部”、“搜索 / 导出”，列表列头只有日期、上下班时间、工时、迟到、早退、状态；当前运行态唯一考勤记录 `record_id=1` 归属 `shop_dept_id=104` 仓库，页面显示日期为 `2026-06-13T16:00:00.000Z`。考勤导入入口缺失另列 P2-74。
- 考勤权限数据：菜单 `3200 我的考勤` 使用 `oa:attendance:list`，按钮 `3201/3202` 分别为查询和导出；当前超级管理员、店长、运营经理、总经理均被授予 `3200/3201/3202`，店长角色下已有多个新建/审计用户。
- 采购审批流程定义当前已自动部署 1 条，`ACT_RE_PROCDEF=1`、`ACT_RE_DEPLOYMENT=1`，但 `oa_purchase=0`、运行任务和历史流程实例均为 0。旧/非运行态曾出现工作流和 OA 采购数据断链；负责人字段 `ERP` 不存在账号的问题仍列为 P2-58。
- 待办审批会写入 `oa_purchase_comment` 审批意见，数据库和 mapper 已有意见表/查询方法；但采购申请详情和已办详情都没有返回或展示审批意见，已列为 P3-39。

## 2026-06-23 系统工具、生成器和调度/流程引擎补充盘点

- 系统工具父菜单 `tool` 当前 `visible=1`、`status=1`，即隐藏且停用；子菜单“表单构建”“代码生成”“系统接口”在数据库中是启用状态，但因父菜单停用不会出现在运行菜单中。
- 2026-06-24 复核运行态权限后确认，`common / 普通角色`、`zjl / 总经理` 仍持有 `tool:build:list`、`tool:swagger:list` 和完整 `tool:gen:*` 权限，用户 `ry` 通过 `zjl` 角色也持有完整代码生成权限；报告已把旧的“生成器没有普通角色授权”口径修正为当前库证据。
- 直连 `/tool/build`、`/tool/gen` 均显示 404；`/tool/gen-edit/index/1` 作为动态隐藏路由仍可直连，显示“修改生成配置”和“提交 / 返回”，但当前 `gen_table=0`、`gen_table_column=0`。
- 代码生成后端接口均有 `tool:gen:*` 权限注解；但 `GenTableServiceImpl.selectGenTableById()` 对不存在的 `tableId` 没有空判断，直接进入 `setTableFromOptions(genTable)`。
- 系统接口地址 `http://localhost:8080/swagger-ui/index.html` 当前 HTTP 状态为 200，但响应体是业务错误 JSON：`404 NOT_FOUND "No static resource swagger-ui/index.html..."`，不能只按 HTTP 200 判断为可用。
- 调度和流程引擎库表覆盖：运行态 `sys_job=3` 且 3 条任务均为停用状态，`sys_job_log=0`；`QRTZ_JOB_DETAILS`、`QRTZ_TRIGGERS`、`QRTZ_SCHEDULER_STATE` 均为 0，`ScheduleConfig` 中 JDBC Quartz 配置仍被整体注释，QRTZ 表不是当前运行态的权威来源。旧口径中 `sys_job=4` 和启用钉钉同步任务不属于当前运行态，见 P2-83。
- 运行态没有 `wf_%` 工作流业务表，也没有工作流菜单；Activiti 只有 1 条流程部署和 1 条流程定义、运行任务为 0。旧口径中工作流菜单和 `wf_instance=6` 等数据不属于当前运行态，已在 P1-14 改为环境漂移和残留引擎数据问题。

## 2026-06-23 库存流程细化盘点

- 早前快照中继续按仓库和门店上下文补测库存盘点、发货通知、调拨记录和调拨审批配置；运行态 `inv_transfer_status_log`、`inv_transfer_approval_*` 等表仍有数据。非运行态 `BossERP` 后续缺少这些调拨必需表，已在 P1-22 改为环境漂移问题。
- 早前快照中，在 `仓库：仓库` 上下文打开 `/inventory/stockCheck` 和 `/inventory/deliveryNotice`，页面均能渲染空态；切换到 `门店：市场部门` 后，库存盘点页能看到唯一盘点单 `SC202606140001`，发货通知仍为空。本轮运行态精确计数仍为 `inv_stock_check=1`、`inv_stock_check_detail=1`；非运行态 `BossERP` 中盘点表为 0，不作为当前网页端结论。
- 早前快照中库存盘点唯一单据 `SC202606140001` 归属 `108 / 市场部门 / STORE`，状态为 `cancelled`，明细“清香铁观音”账面 5、实盘 1、盘亏 4；状态流水有 `draft -> checking -> cancelled` 两条记录。页面只显示详情动作与当时状态一致，但后端取消/删除草稿接口缺少“当前组织可写”校验，且权限树中 `inv:stockCheck:edit` 未被前后端使用，已列为 P2-60、P3-40。
- 2026-06-23 继续只读复核库存盘点详情接口：使用 admin 登录、`Dept-NumId: 108` 请求 `/inventory/stockCheck/2` 返回 `details[0].costPrice=84.0000`；前端详情弹窗不展示成本列，但接口响应仍包含成本字段。店长、驻店经理、运营经理、总经理等非管理员角色均持有 `inv:stockCheck:query` 且没有 `inv:cost:view`，已新增 P1-24。
- 早前发货通知空态与数据库一致：运行态销售单和发货通知均为 0 条，因此 `/inventory/deliveryNotice` 没有可发货数据属于当前运行态数据状态，不单独列问题；非运行态 `BossERP` 销售单和库存/发货通知断层另按 P1-16 处理。
- 早前调拨记录页 `/inventory/transfer-records` 和 `/cangku/transfer-records` 只显示已完成单 `TF202606180001`；运行态数据库另有 `partial_delivered` 单 `TF202606140001`，属于处理中状态，不作为调拨记录页空缺问题处理。非运行态 `BossERP` 的调拨主表和发货批次/审批/status log 曾出现清空或缺表，已在 P1-22 改为环境漂移问题。
- 早前快照中调拨审批配置页 `/system/transfer-rules` 显示 1 条规则“市场部门”，与当时 `inv_transfer_approval_rule` 1 条启用规则对应；本轮查看搜索、新增、编辑、预览、删除入口和列表展示，未提交保存或删除。细化源码后发现审批节点后端保存校验、规则预览入参和部分配置文案存在流程风险，已列为 P2-61、P3-41、P3-42；非运行态 `BossERP` 缺少审批规则表的问题另见 P1-22。
- 2026-06-24 继续只读复核调拨审批配置列表分页和组织过滤：当前规则仍只有 `5 / 市场部门 / scope_type=dept / scope_id=108`，规则权限只授给 `admin,zjl`；列表接口先 `startPage()`，再由服务层对 mapper 返回结果做组织范围内存过滤并重新组装普通列表。当前 1 条数据不触发分页错位，但多门店、多范围规则扩展后会让页面总数和分页不可信，已新增 P3-87。

## 2026-06-23 账号自助链路补充盘点

- 账号相关配置：`sys.account.registerUser=false`，即关闭账号自助注册；`sys.user.initPassword=123456`；`sys.account.initPasswordModify=1`，首次/初始密码策略为提醒；`sys.account.passwordValidateDays=0`，不限制密码更新周期；`sys.account.chrtype=0`，密码字符范围为任意字符。
- 浏览器补测 `/login?preview=1`：登录页能渲染账号、密码、验证码、记住密码、登录按钮；由于注册配置关闭，页面不显示“立即注册”入口；但仍展示默认 `admin/admin123`，已列为 P3-02。
- 浏览器直连 `/register`：页面仍渲染账号、密码、确认密码、验证码和“注册”按钮；后端会按注册开关拒绝，但前端没有在页面入口处提示“注册未开放”，已列为 P3-09。
- 浏览器补测 `/index`：桌面工作台能显示当前组织 `门店：市场部门`，提供切换组织、刷新状态、库存查询、销售管理、我的待办等入口；其中“采购审批/采购单”文案与当前 OA 采购申请停用状态不一致，已列为 P3-10。
- 浏览器补测 `/user/profile` 和 `/user/profile?activeTab=resetPwd`：个人中心基本资料和修改密码 tab 均能渲染，密码修改表单有旧密码、新密码、确认密码字段；本轮未提交保存资料、修改密码或上传头像。细化源码后确认用户导入、重置密码权限、改密后会话失效和头像上传请求头仍有账号自助链路问题，已列为 P1-11、P2-63、P2-86、P3-51。
- 2026-06-24 继续只读复核个人中心基本资料保存链路：后端会保存昵称、手机、邮箱、性别并刷新当前登录缓存，但前端保存成功后只回写手机号和邮箱，不刷新左侧个人信息卡或全局昵称/性别展示，已新增 P3-103。

## 2026-06-23 仓库主数据分类和供应商细化盘点

- 早前快照中分类数据：`inv_product_category` 仅 `100` 金英灵韵下有 30 条有效分类，30 条均为正常；其中 23 个分类已被商品引用，7 个分类暂无商品引用，未发现商品分类孤儿引用。
- 浏览器在 `仓库：仓库` 上下文打开 `/cangku/category`，页面当时显示“共 30 个分类，正常 30 个”，与早前数据库对应；关键词搜索“乌龙茶”后只剩 1 行，点击“重置”后恢复 30 行。
- 分类新增/编辑抽屉能打开：新增根分类时“上级分类”可选且默认为根分类，分类编码禁填并提示保存后自动生成；编辑“乌龙茶”时上级分类和分类编码禁填，名称、排序、状态、备注可维护。本轮未点击保存、删除。
- 早前快照中供应商数据：`inv_supplier` 共 17 条，其中 `100` 金英灵韵 16 条、`104` 仓库 1 条；17 条均为正常、合作中。16 条主仓供应商均被商品引用，`采购部` 暂无商品/采购/退货引用；商品供应商名称未发现孤儿引用。
- 供应商菜单和源码存在：数据库菜单 `4060` 指向 `inventory/supplier/index`，按钮权限覆盖查询、新增、修改、删除、导出；前端组件包含搜索、新增、导出、详情、编辑、状态变更、删除、供货商品详情和分页；后端删除会拦截商品、采购订单、采购退货引用。
- 2026-06-23 继续只读复核供应商详情：`/inventory/supplier/6/products` 返回供货商品完整 `InvProduct`，包含 `purchasePrice=26.50`、`costPrice=84.00`；供应商详情前端还把 `purchasePrice` 作为“采购价”列直接展示。总经理角色有 `inv:supplier:query` 但无 `inv:cost:view`，已新增 P1-27。
- 仓库主数据组织口径不清：仓库 `104` 的相关范围能看到集团 `100` 的商品、分类和供应商，但商品、分类、供应商新增保存时都会写入当前选择组织 `104`；后续同一页面会混合集团级和仓库级主数据，已列为 P2-68。
- 但本轮细化复测供应商电脑端入口不稳定：直接进入 `http://localhost:1026/cangku/supplier` 后 `#app` 正文为空，只剩 SVG sprite 和隐藏主题色控件；从分类页横向菜单尝试进入时，`供应商管理` 子菜单 DOM 存在但宽高为 0，无法形成可见点击目标，已列为 P2-16。
- 2026-06-24 继续只读复核分类/商品导入组织口径：当前 `inv_product_category` 30 条全部归属 `100 / 金英灵韵 / GROUP`，162 个商品也全部引用这些集团分类；仓库上下文可见集团分类，手工新增商品也可选择集团分类，但商品 Excel 导入按当前组织精确匹配分类名，找不到就自动在当前仓库/门店新建同名分类，已新增 P2-105。
- 2026-06-24 继续只读复核供应商改名链路：当前 16 个集团供应商名称被 44 条商品引用，商品、采购单和采购退货都按 `supplier_name` 字符串保存；供应商编辑允许修改名称且后端不阻止、不同步商品引用，已新增 P2-109。
- 2026-06-24 继续只读复核供应商编码：当前 17 个供应商的 `supplier_code` 全为空或 NULL，供应商页却把供应商编码作为筛选、列表、详情和表单字段；数据库只有普通索引，后端没有唯一或必填校验，已新增 P3-109。
- 2026-06-24 继续只读复核仓库主流程路由守卫：前端只把 `/cangku/stock`、`/cangku/transfer`、`/cangku/transfer-records` 纳入组织选择守卫，遗漏 `/cangku/product/category/supplier/purchase/purchaseReturn`，而这些接口都依赖 `Dept-NumId`，已新增 P2-110。

## 2026-06-23 仓库商品管理细化盘点

- 早前快照中商品数据质量：`inv_product` 有 162 条有效商品，全部为正常状态且都归属 `100` 金英灵韵；其中 112 条缺商品编码、5 条缺单位、119 条缺供应商、114 条缺采购价、117 条缺售价，所有主图/外包装图/干茶图/茶汤图/叶底图/补充图片 URL 字段均为空。
- 商品页 `/cangku/product` 在 `仓库：仓库` 上下文曾能渲染左侧分类树、搜索区、指标区、商品表、行级详情/编辑/删除和分页条；搜索“清香铁观音”后返回 1 条，点击分类“红茶”后返回 9 条，重置后恢复 162 条。
- 商品页非破坏性按钮补测：新增商品抽屉、导入商品弹窗、首行商品详情抽屉、编辑商品抽屉、删除确认框、导出确认框、下载模板确认框均能打开；本轮未提交保存、导入、删除、导出或下载。
- 商品详情能带出数据库字段：首行“清香铁观音”展示编码 `WLC-727511`、分类、等级、单位、采购价、售价、成本价、供应商电话、采购备注和产品描述；图文素材区域所有图片字段显示为空。
- 2026-06-24 继续只读复核商品成本写权限：总经理角色持有 `inv:product:add/edit/import`，但没有 `inv:cost:view`；前端会删除隐藏的 `purchasePrice/costPrice`，后端 `InvProductController.add/edit/importData` 和 `InvProductMapper.insert/update` 仍可接收并写入这两个字段，已新增 P1-30。
- 早前快照中只有“清香铁观音”存在业务引用，覆盖库存 2 条、库存日志 5 条、调拨明细 2 条、调拨发货明细 2 条、库存盘点明细 1 条；同分类同名称同规格重复的商品有 5 组，包括“明前龙井 125g/罐”“普洱熟茶 125g/罐”“碧螺春 250g”“武夷大红袍 150g/罐”“西湖龙井 125g/罐”。

## 2026-06-23 仓库采购管理细化盘点

- 采购数据状态：当前 `inv_purchase_order`、`inv_purchase_detail`、`inv_purchase_return`、`inv_purchase_return_detail` 均为 0 条；仓库采购页列表空态与数据库状态一致，采购退货页无可退原单也与当前采购单为空一致。
- 采购菜单和权限存在：数据库菜单启用 `/cangku/purchase`，按钮权限覆盖查询、新增、修改、删除、提交、收货、质检、导出；前端组件包含列表筛选、新建/编辑采购单、选择采购商品、详情、收货、质检、取消、删除草稿、导出；后端服务包含保存草稿、提交、收货入待检、质检入库/驳回、取消和删除草稿。
- 采购权限细化补充：`inv:purchase:edit` 虽然在权限树中启用并授给超级管理员、总经理、仓库管理员，但采购草稿编辑按钮和后端保存接口仍使用 `inv:purchase:add`，已列为 P2-66。
- 浏览器在已成功进入 `/cangku/purchase` 后，页面显示“当前采购仓库：仓库”和“当前仓库暂无采购单”；点击“新建采购单”能打开标题、采购日期、供应商、总金额、备注、采购明细、保存草稿、保存并提交表单。
- 早前点击“选择商品”能打开采购商品选择弹窗，搜索“清香铁观音”后只剩 1 条，重置后恢复 162 条；弹窗中仍能看到缺商品编码、缺供应商、缺采购价等不完整商品，其中选择器不应展示不可采购商品的问题已列 P1-07，分页/性能问题本轮另列 P2-20。
- 2026-06-23 继续只读复核采购商品选择与成本权限：采购弹窗固定展示“采购参考价”，并用商品接口返回的 `purchasePrice` 初始化采购明细 `unitPrice`；总经理角色有 `inv:product:list/query` 和 `inv:purchase:add/list/query/submit/receive/qc/export`，且账号 `ry` 授权包含 `104` 仓库，但没有 `inv:cost:view`，已新增 P1-28。
- 2026-06-23 继续只读复核采购待检入库记录：`InvPurchaseServiceImpl.receivePurchase()` 会写 `inv_inbound_record`，`qualityCheck()` 会回写 `qc_result/qc_user/qc_time/qc_remark`，但电脑端采购详情、收货和质检弹窗没有展示入库批次/质检记录台账；运行态正式表当前为 0，清理备份表仍有 3 条历史质检通过记录，已新增 P3-85。
- 2026-06-24 继续只读复核采购收货/质检状态机：前端收货确认文案提示“提交后会增加当前仓库库存”，但后端收货只写待检入库记录，质检合格或让步接收后才更新库存和库存日志；当前正式采购/入库表均为 0，先按源码和运行态菜单记录为 P2-106。
- 本轮尝试刷新 `/cangku/purchase` 后页面正文变为空，额外等待后仍无主内容和弹窗；因此导出按钮未完成浏览器确认，源码层面仅确认 `handleExport` 会先弹出“确认导出当前查询条件下的采购单数据？”再下载。

## 2026-06-23 仓库采购退货管理细化盘点

- 采购退货数据状态：当前 `inv_purchase_return=0`、`inv_purchase_return_detail=0`，且 `inv_purchase_order=0`、`inv_purchase_detail=0`，所以当前没有可创建采购退货的原采购单。
- 采购退货菜单和权限存在：数据库菜单 `4070` 指向 `inventory/purchaseReturn/index`，按钮权限覆盖查询、新增、修改、删除、提交、确认、导出；前端组件包含退货单号/原采购单号/供应商/状态筛选、新增退货单、详情、编辑、提交、确认退货、取消、导出和原采购单远程选择。
- 后端采购退货流程存在：`saveDraft` 保存草稿，`submitReturn` 提交并校验退货数量不超过原采购已收货数量和历史已退数量，`confirmReturn` 确认后扣减当前仓库库存并写库存日志，`cancelReturn` 将草稿/已提交单据改为取消。
- 源码继续确认采购退货保存/提交只读取原采购明细，不读取原采购单头校验仓库归属；如果未来存在多仓库采购单，接口可把其他仓库的原采购单退货扣到当前仓库库存，已列为 P1-13。
- 2026-06-24 继续只读复核采购退货价格权限：总经理角色持有 `inv:purchaseReturn:list/query/add/submit/confirm/export`，但没有 `inv:cost:view`；采购退货列表、表单、详情和导出会展示或导出退货金额、单价和金额，后端从原采购明细回填 `unitPrice/amount/totalAmount`，已新增 P1-33。
- 浏览器直连 `http://localhost:1026/cangku/purchaseReturn` 后正文为空，`#app` 文本长度为 0；从 `/index` 打开后仓库菜单 DOM 中存在 `/cangku/purchaseReturn` 链接，但链接宽高为 0，点击可见“仓库管理”菜单后仍没有形成可见采购退货入口。该入口问题并入 P2-19。
- 因采购退货页面本轮无法稳定打开，新增退货单弹窗未能再次用浏览器完整复测；源码确认原采购单下拉为空时没有 `empty-text` 或页面级引导，已有 P2-08 继续有效。

## 2026-06-23 仓库库存管理细化盘点

- 运行态库存数据状态：`inv_stock=2`、`inv_stock_log=5`。商品均为 `WLC-727511 / 清香铁观音`，其中仓库 `104 / 仓库` 库存 192、门店/部门 `108 / 市场部门` 库存 12；库存日志覆盖手工调整、调拨出库、调拨入库。非运行态 `BossERP` 曾出现 `inv_stock=0`、`inv_stock_log=0`，不作为当前网页端库存结论。
- 库存菜单和权限存在两套入口：`sys_menu` 中 `4040` 为进销存库存管理 `/inventory/stock`，`4450` 为仓库库存管理 `/cangku/stock`，二者都指向 `inventory/stock/index` 且使用 `inv:stock:list`；隐藏菜单 `4490` 为库存变动日志 `/inventory/stock-log`。
- 前端库存页包含分类树、商品/批次/效期/序列号/库位/库存状态筛选、仓库“当前仓库/可见库存”范围切换、库存指标卡、库存表、库存调整弹窗、库存详情弹窗、导出和变动日志入口；后端 `InvStockServiceImpl` 对列表、汇总、详情、库存调整和库存日志做了当前库存组织范围校验。
- 库存详情接口和权限点存在但电脑端库存页没有实际使用：`/inventory/stock/{stockId}` 要求 `inv:stock:query`，但库存页详情弹窗直接展示列表行数据，`getStock(stockId)` 未被引入或调用，已列为 P3-44。
- 2026-06-23 继续只读复核库存列表/详情/汇总成本权限：使用店长账号 `qa144822m / Audit123`、`Dept-NumId:108` 请求 `/inventory/stock/list`、`/inventory/stock/3` 和 `/inventory/stock/summary`，响应包含 `costPrice=84.00`、`totalCost=1008.00`，但该角色没有 `inv:cost:view`，已新增 P1-29。
- 2026-06-24 继续只读复核库存调整成本写入：驻店经理、运营经理、总经理拥有 `inv:stock:adjust` 但没有 `inv:cost:view`；库存调整确认弹窗只展示数量变化，后端会按库存/商品成本价更新 `cost_price/total_cost` 并写库存日志成本价，已新增 P1-31。
- 浏览器在 `仓库：仓库` 上下文从 `/index` 点击“库存查询”后，URL 和面包屑切换到 `/cangku/stock / 仓库管理 / 库存管理`，但主内容仍停留在“桌面工作台”，没有库存表、筛选表单或指标卡；刷新 `/cangku/stock` 后仍没有可用库存页主体，已列为 P2-25。
- 源码继续确认两个库存体验/流程问题：仓库“可见库存”模式提示只读，但顶部“库存调整”仍可点击并会按当前仓库打开调整弹窗，已列为 P2-26；从“可见库存”跳转到库存变动日志后，日志服务会强制收窄到当前库存组织，不能解释可见库存中的其他组织数量变动，已列为 P2-27。

## 2026-06-23 调拨管理主流程细化盘点

- 运行态调拨数据状态：`inv_transfer_order=2`、`inv_transfer_detail=2`、`inv_transfer_shipment=2`、`inv_transfer_shipment_detail=2`、`inv_transfer_status_log=6`、`inv_transfer_approval_instance=2`、`inv_transfer_approval_task=2`；非运行态 `BossERP` 只剩调拨主表/明细/收货预留表，详见 P1-22。
- 早前两张调拨单都为仓库 `104 / 仓库` 到门店 `108 / 市场部门` 的门店要货：`TF202606180001` 已完成，申请 7、已发 7、已收 7；`TF202606140001` 为 `partial_delivered`，申请 5、已发 1、已收 0，存在待收货批次 `TS202606180002`。
- 菜单和权限：进销存侧 `4100 /inventory/transfer` 覆盖查询、新增、修改、提交、出库、入库、取消、导出、审批；仓库侧 `4460 /cangku/transfer` 只配置查询、出库、导出；调拨记录 `4400/4465` 另配记录查询和导出，且存在尚未落到页面能力的 `inv:transfer:approval:track`。
- 2026-06-24 后续权限树复核：运行态 `sys_menu` 已重新出现 `inv:transfer:rule:*`、`inv:transfer:records*` 和 `inv:transfer:approve` 权限点；2026-06-23 一度查不到这些权限的结论已修正为权限树回填/漂移问题，见 P2-75。
- 前端调拨页包含处理中列表、发起要货弹窗、仓库库存选择器、详情弹窗、发货弹窗、按发货批次整批收货弹窗和导出确认；后端调拨服务覆盖保存草稿、提交审批、审批通过/驳回、发货扣仓库库存、按批次收货增门店库存、取消和状态流水。
- 调拨草稿保存接口把新增和修改权限合并为 `inv:transfer:add OR inv:transfer:edit`；当前角色都同时持有两者，但自定义最小权限角色会被接口层放大，已列为 P2-67。
- 浏览器早前在调拨路由间切换时曾出现正文仍停留首页或空白壳，已列为 P2-29；后续重新启动前端并设置明确组织上下文后，`/inventory/transfer`、`/cangku/transfer` 均能渲染调拨单和对应动作。但 `/cangku/transfer` 在门店上下文下仍显示门店要货动作，仓库路由没有强制仓库上下文，已列为 P2-94。
- 2026-06-23 继续只读复核调拨详情接口：使用 admin 登录、`Dept-NumId: 108` 请求 `/inventory/transfer/14` 返回发货批次 `TS202606180001`，其中 `shipments[0].details[0].costPrice=84.00`；前端调拨详情批次展开表不展示成本列，但接口响应仍包含成本字段。店长、驻店经理、运营经理、总经理均持有调拨查询/记录查询权限且没有 `inv:cost:view`，已新增 P1-25。
- 早前数据库曾命中一个部分发货待收货场景，但桌面端和移动端动作规则都只允许 `delivered/partial_received` 才显示收货动作，未覆盖后端允许的 `partial_delivered` 按批次收货，已列为 P2-28。

## 2026-06-23 门店销售链路细化盘点

- 非运行态 `BossERP` 销售链路正式表状态曾为：`inv_customer=0`、`inv_sales_order=9`、`inv_sales_detail=14`、`inv_sales_return=0`、`inv_sales_return_detail=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0`；同时商品主数据 `inv_product=8`、库存 `inv_stock=0`。该销售单已有 `delivered/noticed` 状态但库存、库存流水和发货通知为空的问题已在 P1-16 改为非运行态环境漂移；当前运行态销售正式表为空，需要另建流程数据复测。
- 菜单和权限：`/inventory/sales`、`/inventory/customer`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report` 均有启用页面菜单和按钮权限；2026-06-24 复核当前运行态时，`inv:sales:notice` 已不在 `sys_menu` 中，早前“销售发货通知”无效权限应归为权限树漂移；但仓库角色仍没有发货通知入口和执行权限，销售旧出库权限 `inv:sales:deliver` 仍停用隐藏且后端旧接口还存在。
- 前端和后端流程：销售页包含新建销售单、商品选择、保存草稿、保存并提交、生成发货通知、取消和导出；后端销售单直接出库接口已改为返回“请通过发货通知执行发货”，实际出库应走发货通知，发货通知执行会扣指定仓库库存并可能生成跨店调拨。
- 浏览器早前复测发现销售链路入口不稳定，直连多个门店销售路由曾只剩空白壳；后续重新启动前端并设置 `108 / 市场部门 / STORE` 后，销售、客户、销售退货、发货通知和报表页均可渲染空态/按钮，旧现象已按 P2-30 作为路由稳定性回归问题保留。`/inventory/customer` 在仓库上下文仍能打开新增客户弹窗，已列为 P2-31。
- 客户与销售主数据闭环仍不完整：销售单客户字段仍为自由文本，当前客户表为空，已列 P2-04；新增客户弹窗多数输入没有占位提示、只校验客户名称，已列 P3-05。本轮补充确认后端客户保存只校验已选择组织，不要求必须是门店。
- 客户编码字段没有唯一约束或后端重复校验，前端又允许留空；客户编码筛选是精确查询，未来录入重复编码后会影响客户识别和销售归并，已列为 P3-45。
- 发货通知执行职责需要重新梳理：当前仓库角色没有发货通知权限，门店/运营/总经理角色的生成和执行权限也不完全一致，发货通知执行又要求选择发货仓库；旧的 `inv:sales:deliver` 接口权限仍停用隐藏，已列为 P2-32 和 P1-10。
- 发货通知的“发货仓库”选择与当前组织树不匹配：唯一仓库 `104 / 仓库` 挂在集团下，不在任何门店子树内；但发货通知页面和 `/system/dept/warehouse-list?purpose=deliverySource` 都按销售门店子树筛选仓库，导致所有门店销售通知执行发货时都会没有可选仓库，已列为 P1-10。
- 2026-06-23 继续只读复核发货通知详情、导出和出库记录：执行发货会写 `inv_outbound_record` 和库存流水，但发货通知详情不展示实际出库记录，导出也只导出单头汇总字段，不包含商品明细或每次出库批次，已新增 P3-86。
- 2026-06-24 继续只读复核销售单发货进度字段：后端销售列表已聚合 `totalQuantity/deliveredQuantity/remainingQuantity`，明细也返回 `deliveredQuantity`，但销售列表和详情页只展示金额、状态和销售数量，不展示已发/待发进度，已新增 P3-99。
- 销售退货流程补充：运行态 `BossERP_stock_state_75c59ee` 当前正式销售单、销售明细、销售退货和销售退货明细均为 0，不能用存量单据复现实例；非运行态 `BossERP` 后续出现 9 张销售单但销售退货仍为 0，仅作为环境漂移取证。源码核查确认销售退货后端保存时没有校验原销售单门店和当前门店一致，销售退货“修改”权限也未被真实使用，已列为 P1-12、P2-64 和 P3-43。
- 2026-06-24 继续只读复核销售退货成本口径：销售出库只在库存日志写当时成本价，销售明细、出库记录和退货明细没有原出库成本快照；销售退货确认按当前库存成本入库，库存不存在时按 0 成本入库，已新增 P1-32。
- 销售管理按钮权限补充：`inv:sales:edit` 在权限树中启用并授给多类门店/运营角色，但页面编辑草稿和后端保存都使用新增权限，已列为 P2-65。

## 2026-06-23 报表中心细化盘点

- 报表中心功能模型：电脑端 `/inventory/report` 只有“汇总指标 + 低库存预警”两块，筛选项包括商品、库存组织、业务日期、预警类型，按钮为查询、重置和低库存预警刷新；页面没有图表、趋势、导出或单据钻取。
- 报表接口验证时的数据对应：运行态数据库 `inv_stock=2`，其中 `104 / 仓库` 库存 192、库存成本 16128，`108 / 市场部门` 库存 12、库存成本 1008；采购单、销售单、客户、发货通知均为 0。非运行态 `BossERP` 曾复核到 `inv_stock=0`，不作为当前报表中心结论；库存和历史备份数据清理问题另见 P3-29、P1-21。
- 权限和接口：报表页面菜单和按钮都使用 `inv:report:list`；店长、驻店经理、运营经理、总经理均有 `inv:report:list` 但没有 `inv:cost:view`。后端 `InvReportController.summary()` 只校验 `inv:report:list`，`InvReportSummary` 仍返回 `totalStockCost`、`purchaseAmount`、`grossMargin`。
- 2026-06-24 后续权限树复核：当前 `sys_menu` 已重新出现 `inv:report:list` 和 `inv:audit:list`，并授给多类业务角色；2026-06-23 一度查不到这些权限的结论已修正为权限树回填/漂移问题，见 P2-75。报表成本字段接口脱敏问题仍按 P1-09 处理，单据审计无页面入口仍按 P2-44/P2-91 处理。
- 早前运行验证：使用审计账号 `qa144822m` 重新登录并按 `Dept-NumId:108` 只读调用 `/inventory/report/summary`，接口返回 `stockItemCount=1`、`totalCurrentQuantity=12.00`、`totalStockCost=1008.00`；同一请求加 `beginTime=1900-01-01&endTime=1900-01-01` 后库存数量和成本仍不变。
- 报表口径风险：页面文案把“预估毛利”定义为“销售金额 - 采购金额”，后端 SQL 也是 `sales_amount - purchase_amount`；销售明细表没有销售成本字段，当前算法不是按已售商品成本计算毛利，采购与销售的日期范围也可能不是同一批商品。
- 低库存列表字段：页面表头含“最后入库 / 最后出库”，`inv_stock` 也有 `last_in_time / last_out_time`，但报表 mapper 的低库存查询没有 select 这两个字段，`ReportStockResult` 也没有映射 `lastInTime/lastOutTime`。

## 2026-06-23 多账号组织授权细化盘点

- 运行态授权数据状态：有效用户 25 个，`sys_user_shop` 共 43 条，其中 STORE 授权 34 条、WAREHOUSE 授权 9 条；未发现授权到集团、公司、停用组织、删除组织或不存在组织的记录。非运行态 `BossERP` 曾复核为有效未删除账号 7 个且 `sys_user_shop=0`，已按 P3-46 记录为非运行态误判风险。
- 运行态典型账号授权与数据库一致：`admin` 和 `ry` 显式授权 7 个经营组织；`qa144822s` 授权 `103 / 研发部门`；`qa144822w` 授权 `104 / 仓库`；`qa144822m` 授权 `103 / 研发部门` 和 `108 / 市场部门`；`qa_audit_62201` 授权 `103 / 研发部门`。
- 非运行态 `BossERP` 的组织名称和编号与运行态不同：例如 `103` 为 `北京1 / STORE`、`104` 为 `市场部门 / STORE`、仓库为 `200/201`，不能用来替换当前电脑端页面证据。
- 后端授权接口语义基本清楚：`/system/user/shop/tree` 返回可配置组织树，`/system/user/shop/{userId}` 返回 `shopIds/editableShopIds/preservedShopIds/outOfScopeCount`，非管理员保存时只删除自己可编辑范围内的既有授权并保留不可见授权；保存前会校验只能选择正常的 STORE/WAREHOUSE。
- 店铺/仓库授权的职责边界仍偏宽：当前 `181 / 店铺授权` 及 `1190-1192 / system:userShop:*` 只授给 `admin` 和 `zjl`，但实际账号 `ry` 通过总经理角色持有 `system:userShop:list/query/edit`、`data_scope=1`，且自身已有 7 个经营组织授权，因此可以给其他可见账号调整完整门店/仓库范围，已列为 P2-103。
- 运行态只读 API 复测正常：同一 admin 浏览器会话手动带 `Authorization` 调用 `/system/user/list` 返回 `total=25`，`/system/user/shop/tree` 返回完整授权树，`/system/user/shop/batch?userIds=1,119,120,121,122` 返回 `{1:[104,103,105,106,107,108,109],119:[103],120:[104],121:[103,108],122:[103]}`，`/system/user/shop/121` 返回 `editableShopIds=[103,108]`。非运行态 `BossERP` 与运行态 API 存在数据差异，运行 jar/源码/数据库口径不一致已在 P1-02/P1-23 和后续验证记录中说明。
- 但电脑端页面渲染不稳定：直连 `/system/shop` 只剩空白壳和隐藏“清空/确定”，没有用户列表、授权树或保存按钮，已列为 P2-33；直连 `/select-shop` 首次显示“0 个业务组织可选 / 暂无组织数据”，点击页面“刷新”后才恢复为“7 个业务组织可选”，已列为 P2-34。
- 授权列表仍存在已记录的问题：当用户没有显式 `sys_user_shop` 授权时，前端“组织范围”列会回退显示用户所属部门，已列为 P1-06；新建用户跳转授权页只带 `userName` 不带新 `userId`，已列为 P2-06。
- 存量账号配置仍有缺口：运行态 25 个有效账号均至少有 1 个角色，但 `user_id=103 / user_name=123` 缺少显式店铺/仓库授权；非运行态 `BossERP` 中 7 个有效账号全部没有 `sys_user_shop` 显式授权，按环境漂移风险列入 P3-46。
- 前端服务重启后复测：`/system/shop` 能渲染用户列表、组织授权区域和保存按钮，`/select-shop` 能直接显示 7 个业务组织；P2-33/P2-34 更准确地应归入“路由/运行态稳定性问题”，不能只按当前一次成功渲染关闭。授权列表还会把多个具体门店/仓库折叠成父级组织名，已列为 P3-47。
- 默认组织字段没有形成闭环：`sys_user_shop.is_default` 当前有 24 条 `Y`、19 条 `N`，多组织账号也都有一条默认授权，但授权页不能指定默认组织，选择组织页也不读取默认值，已列为 P3-108。

## 2026-06-23 通知公告与监控详情副页面细化盘点

- 通知公告数据状态：当前 `sys_notice=0`、`sys_notice_read=0`。只读 API `/system/notice/list` 返回 `total=0`，`/system/notice/listTop` 返回 `data=[]、unreadCount=0`，首页顶部公告入口能显示“暂无公告”，与数据库空数据一致。
- 通知公告管理页功能模型存在但当前不可用：`sys_menu` 中 `107 /system/notice` 启用，按钮权限覆盖查询、新增、修改、删除；前端 `system/notice/index.vue` 包含搜索、重置、新增、修改、删除、标题详情和“阅读用户”副弹窗。但浏览器直连 `/system/notice` 只剩空白壳和隐藏“清空/确定”，已列为 P2-35。
- 公告已读流程存在数据一致性风险：`markRead/markReadAll` 只要求登录，`sys_notice_read` 表只有 `(user_id, notice_id)` 唯一约束，没有外键；本轮未执行写入验证，但从源码看接口未校验公告是否存在或处于正常状态，已列为 P3-19。
- 2026-06-24 继续只读复核通知公告管理页、顶部公告和阅读用户副页面：当前 `sys_notice=0`、`sys_notice_read=0` 仍为空表，当前运行态没有 `sys_notification`；源码确认列表筛选、顶部“全部已读”、阅读用户权限和公告删除清理存在细节风险，已新增 P3-93、P3-94、P3-95、P3-100，并按当前运行态角色授权修正 P2-88、P3-62 证据。
- 早前监控详情副页面复测：`sys_job=3`、`sys_job_log=0`、`sys_oper_log=241`。`/monitor/job` 最终能展示 3 条停用任务，点击第一条“系统默认（无参）”能打开“任务详细”弹窗，字段包含任务编号、任务名称、任务组、状态、cron、下次执行时间、执行策略、并发、执行方法、创建人和创建时间。最新运行态精确复核为 `sys_job=3`、`sys_job_log=0`、`sys_oper_log=244`。
- 操作日志详情副弹窗复测：`/system/log/operlog` 展示 10 条当前页日志，点击第一行“详细”能打开“操作日志详细”，显示 `店铺配置 / 授权 / PUT / admin / 127.0.0.1 / /user/shop/122 / SysUserShopController.save() / 8 毫秒 / 请求参数 / 返回参数`。列表和详情仍共同存在已记录的 8 小时时间偏移问题，见 P2-09；日志权限和日志参数脱敏边界也偏宽，已列为 P2-62。
- 操作日志列表/详情分层不足：后端列表 mapper 会把 `oper_param/json_result/error_msg` 随列表行一起返回，前端“详细”弹窗只是展开列表行，没有调用按 `operId` 查询的详情接口；服务层和 mapper 虽有 `selectOperLogById`，Controller 没有暴露详情映射，已新增 P3-106。

## 2026-06-23 用户角色授权副页面细化盘点

- 非运行态 `BossERP` 的有效账号和角色绑定数据已不闭合：该库 `sys_user_role=5`，7 个有效未删除账号中有 2 个无角色账号；角色分布为超级管理员 1、店长 1、茶艺师 3。运行态库并不存在这个“2 个无角色账号”结论，当前运行态缺口是 `user_id=103 / 123` 无显式店铺/仓库授权。
- 用户分配角色页 `/system/user-auth/role/122` 直连首次曾出现空白，刷新等待后能正常渲染用户昵称、登录账号、角色表、提交和返回按钮；接口 `/system/user/authRole/122` 返回 11 个角色，`qa_audit_62201` 当前仅勾选 `103 / 店长 / dz`，与数据库 `sys_user_role` 对应。
- 角色分配用户页 `/system/role-auth/user/103` 能调用 `/system/role/authUser/allocatedList` 并展示 12 个已分配店长用户，当前 12 个都属于 `STORE` 部门；页面提供添加用户、批量取消授权、取消授权和关闭按钮。
- “添加用户”副弹窗会调用 `/system/role/authUser/unallocatedList`，店长角色候选共 13 个，包含 `GROUP`、`COMPANY`、`WAREHOUSE`、`STORE` 和无部门用户，例如 `admin`、`ry`、`12345`、`liu123456`、`123`、`dushuai`、多个仓库管理员账号。弹窗列只显示用户名、昵称、邮箱、手机、状态、创建时间，没有部门、组织类型、当前角色、店铺授权范围，已列为 P2-36。
- 用户授权角色页前端允许不勾选任何角色后提交，后端 `insertUserAuth` 会先删除旧角色再按传入角色数组插入；当前数据库没有无角色用户，本轮也未提交写入，但从源码看存在把账号变成无角色账号的流程风险，已列为 P3-20。
- 授权写操作没有独立权限点：用户授权角色接口使用 `system:user:edit`，角色分配/取消用户接口使用 `system:role:edit`；`sys_menu` 未配置 `system:user:authRole`、`system:role:authUser` 这类更细权限。用户编辑和授权写入边界过宽，已列为 P2-37。
- 角色数据权限弹窗同样没有独立权限点：页面能配置全部数据权限、自定义数据权限、本部门及以下、本部门、仅本人等范围，但前端入口和后端接口都复用 `system:role:edit`，权限树未配置 `system:role:dataScope`，已列为 P2-69。
- 角色菜单授权保存缺少后端可授予范围校验：角色编辑弹窗会按当前用户返回菜单树，但提交时后端直接按请求 `menuIds` 重建 `sys_role_menu`，没有校验每个菜单是否属于当前用户可授权菜单，已列为 P2-70。
- 2026-06-24 继续复核角色管理账号治理权限：`system:role:list/query/add/edit/remove/export` 当前完整授给 `common / 普通角色` 和 `zjl / 总经理`，有效用户 `ry` 通过总经理角色实际持有角色新增、修改、删除、导出、数据权限和分配用户入口；角色修改会重建菜单权限，数据权限会重写角色部门范围，已新增 P2-101。
- 角色权限矩阵存在菜单状态口径不一致：普通角色、店长、驻店经理、运营经理、总经理仍保留停用菜单授权；运行时权限会过滤 `status=1`，但角色编辑的已勾选 keys 不过滤停用菜单，已列为 P2-59。
- 库存盘点权限树存在一个未生效权限点：`4303 / 盘点修改 / inv:stockCheck:edit` 已授权给店长、驻店经理、运营经理、总经理，但前端录入实盘和后端 `/stockCheck/input/{checkId}` 实际都使用 `inv:stockCheck:submit`，已列为 P3-40。

## 2026-06-23 系统基础配置页细化盘点

- 数据库基础配置状态：`sys_role=11`、`sys_menu=237`、`sys_dept=11`、`sys_post=10`、`sys_dict_type=10`、`sys_dict_data=29`、`sys_config=8`。当前部门树层级符合 `GROUP -> COMPANY/WAREHOUSE -> STORE` 的业务模型，没有已落库的层级异常。
- 浏览器长等待复测后，`/system/role` 显示 11 个角色中的首屏 10 条，字段为角色编号、角色名称、权限字符、显示顺序、状态、创建时间、操作；`/system/menu` 展示 7 个顶级菜单节点；`/system/dept` 展示 11 个组织节点并显示“类型”列；`/system/post` 展示 10 个岗位；`/system/dict` 展示 10 个字典类型；`/system/dict-data/index/1` 展示“用户性别”3 条字典项；`/system/config` 展示 8 条系统参数。
- 这些基础页存在明显慢加载/初始空态：首次批量直连时多个页面 1.8 秒后 `#app` 文本仍为 0，角色/菜单页刷新 4.5 秒内还可能显示“暂无数据”，约 7.5-8 秒后才稳定出现表格数据，已列为 P3-23。
- 部门管理已把 `dept_type` 前端展示出来，但新增/编辑流程仍把“集团、公司、门店、仓库”作为普通下拉选项，没有按父级组织限制可选类型；后端新增/修改也只校验父级存在、状态和名称唯一，不校验组织类型层级，已列为 P2-38。
- 基础资料删除入口普遍没有前置引用状态：字典类型 10 个都已有字典项，岗位中 6 个已被用户引用，部门中 6 个有用户且多个有子节点，237 个菜单都已绑定角色，但页面仍显示删除按钮；后端会拦截这些删除，已列为 P3-21。参数设置的内置参数删除问题已在 P3-07 单列。
- 岗位与角色的业务称谓高度重叠，但系统没有一致性提示：用户表单并列选择“岗位”和“角色”，用户列表只展示部门/状态/配置状态，不展示岗位；数据库里已有仓库管理员绑定“程序员/店助”岗位、运营经理绑定“总经理”岗位的情况，已列为 P3-22。
- 2026-06-24 继续复核部门/组织树写权限：`system:dept:list/query/add/edit/remove` 当前完整授给 `common / 普通角色` 和 `zjl / 总经理`，有效用户 `ry` 通过总经理角色实际持有部门新增、修改、删除和排序权限；部门管理就是门店/仓库组织树，新增/修改会写 `sys_dept` 和 ancestors，已新增 P2-102。
- 2026-06-24 继续复核岗位权限边界：`system:post:list/query/add/edit/remove/export` 当前仍授给 `common / 普通角色` 和 `zjl / 总经理`，用户 `ry` 通过总经理角色实际持有岗位新增、修改、删除和导出权限；岗位又被调拨审批节点和薪资档位引用，已新增 P2-99。
- 2026-06-24 继续复核用户管理账号治理权限：`system:user:list/query/add/edit/remove/export/import/resetPwd` 当前仍完整授给 `common / 普通角色` 和 `zjl / 总经理`，用户 `ry` 通过总经理角色实际持有用户新增、修改、删除、导入、导出和重置密码权限；用户新增/编辑表单还可写岗位和角色，已新增 P2-100。
- 参数设置、字典管理在 `sys_menu` 中仍是隐藏菜单，但当前运行态 `common / 普通角色` 和 `zjl / 总经理` 仍被授权页面、查询、新增、修改、删除、导出等权限，实际用户 `ry` 通过总经理角色持有这些隐藏系统配置写权限，已按当前证据修正 P2-57。源码层面仍存在“刷新缓存”复用删除权限的问题，已列为 P3-50。
- 当前 8 条系统参数格式本身未发现异常，10 类字典的 29 条字典项也未发现同一字典类型下 `dict_value` 重复；但参数页把注册开关、密码字符范围、密码过期周期、黑名单等高影响配置都当自由文本保存，缺少类型化校验和入口联动，已列为 P3-37、P3-38。
- 2026-06-24 重新复核当前运行态参数口径：当前 `sys_config=8`，早前 P2-82 中的 19 条安全/文件/审计/PDA 参数不再属于当前运行态页面证据，P2-82 已修正为历史/非运行态参数漂移；当前仍存在 `sys.index.skinName/sys.index.sideTheme` 可编辑但桌面端不读取的问题，已新增 P3-107。

## 2026-06-23 系统监控与日志主列表细化复测

- 日志和监控数据状态：当前 MySQL 精确复核 `sys_logininfor=501`，其中成功 442、失败 59；`sys_oper_log=244`；`sys_job=3`，3 个均为停用；`sys_job_log=0`。早前 Redis 曾有 23 个 `login_tokens:*` 在线会话键且没有 `pwd_err_cnt:*` 锁定计数键；当前锁屏解锁失败不进入该计数另见 P3-49。
- 当前浏览器会话里 `/index` 能正常显示仓库工作台，`/monitor/job-log/index/0` 能渲染调度日志空态、搜索、重置、删除、清空、导出、关闭按钮；但 `/monitor/online`、`/system/log/logininfor`、`/monitor/job`、`/system/log/operlog` 直连并刷新等待 10 秒后 `#app` 文本仍为 0，已列为 P2-39。
- 调度日志页与数据库空数据对应：`sys_job_log=0`，页面显示“暂无数据 / 共 0 条”，删除按钮禁用；但“清空”和“导出”仍可点击，已列为 P3-25。
- 登录日志功能模型存在但本轮页面无法渲染：源码显示登录日志页有搜索、重置、删除、清空、解锁、导出按钮，列表字段为访问编号、用户名称、地址、登录状态、描述、访问时间。当前 Redis 没有锁定计数键，但解锁按钮只要选择任意一条登录记录就会启用，不区分该用户是否真实锁定，已列为 P3-24。
- 定时任务功能模型存在但当前主列表空白：源码和后端显示每行提供状态开关、修改、删除、更多、执行一次、调度日志；后端 `run` 只检查 Quartz 任务是否存在，不检查任务是否停用。当前 3 个任务全部为停用，如果页面可渲染，仍会暴露手动“执行一次”，已列为 P2-40。
- 在线用户页源码会展示会话编号、登录名称、主机、登录时间和“强退”按钮；后端强退直接按 tokenId 删除 Redis 登录 token，没有当前会话保护或二次身份区分。本轮因 `/monitor/online` 主页面空白未能从 UI 验证当前会话行，但源码层面存在管理员误强退自己或关键会话的风险，已列为 P2-41。
- 2026-06-24 继续只读复核定时任务和调度日志关联：当前 `sys_job_log=0`，无法做运行态历史日志复现；源码和表结构显示日志只保存任务名称、任务组和调用目标，不保存 `job_id`，从任务行打开调度日志时也只是按任务名/任务组过滤，已新增 P3-92。
- 2026-06-24 继续只读复核系统监控权限和在线用户分页：当前 Redis `login_tokens:*` 为 2 个，`sys_job=3` 且全部停用；但 `common / 普通角色` 和 `zjl / 总经理` 均被授予在线用户强退、定时任务新增/修改/删除/启停/导出及监控外链权限，其中有效用户 `ry` 绑定总经理。在线用户后端会一次性扫描全部登录 token 并返回完整 rows，前端再本地 slice 分页，已新增 P2-96、P3-96。
- 2026-06-24 继续只读复核定时任务“执行一次”权限：当前权限树没有独立 `monitor:job:run` 或执行权限节点，前端“执行一次”和后端 `/schedule/job/run` 都复用 `monitor:job:changeStatus`，已新增 P3-101。
- 2026-06-24 继续只读复核调度日志权限粒度：运行态没有任何 `monitor:jobLog:*` 或“调度日志”独立权限节点，日志列表、详情、删除、清空、导出全部复用定时任务 `monitor:job:*` 权限，已新增 P2-108。

## 2026-06-23 薪资配置与 OA 工资细化盘点

- 薪资数据库状态：非运行态 `BossERP` 曾复核 `sys_salary_scheme=2`、`sys_salary_scheme_item=12`、`sys_role_salary_scheme=0`，且 `sys_user_salary_scheme`、`oa_salary_config`、`oa_salary_record` 三张源码仍依赖的表不存在；运行态库已确认这些表存在但工资记录为空。薪资模块口径修正见 P1-18。
- 组织覆盖状态：门店 `103` 有 10 个有效用户、`107` 有 1 个、`108` 有 4 个，但 STORE 门店均没有 `oa_salary_config`；唯一工资参数配置在 `104 / 仓库 / WAREHOUSE`。这和“门店员工工资核算”的业务入口不闭环，已列为 P2-43。
- 系统薪资页面路由复测：裸 `/system/salary` 等待 5-8 秒后只剩主题色控件“清空 / 确定”，没有 tabs、表格或按钮；`/system/salary?probe=1` 和 `/system/salary?roleId=100` 能显示“薪资方案 / 档位明细 / 员工绑定”三 tab、2 个方案和员工绑定列表。裸路由空白另列 P2-42，角色入口不识别 `roleId` 继续按 P1-08 处理。
- 员工绑定页与数据库对应：页面“员工绑定”tab 只显示 `ii / 市场部门 / 无社保 / 金英门店薪资方案-无社保 / 实习生 / 第一档 / 3,300` 一条记录，与 `sys_user_salary_scheme` 当前 1 条对应；新增员工薪资绑定弹窗能打开，默认选第一个员工和第一个档位并展示档位明细预览，本轮未保存。
- 角色薪资数据存在但页面不可维护：`sys_role_salary_scheme` 中超级管理员绑定了“茶艺师 第二档 4200”和“实习生 第一档 300”，实习生角色绑定“实习生 一档 700”，茶艺师角色绑定“茶艺师1档 5000”；薪资页没有角色绑定 tab 或角色名称上下文，数据质量另列 P3-27。
- 薪资模板菜单另有一套半成品配置：`sys_menu` 启用 `4146 / 薪资模板 / system/salaryTemplate/index` 及查询、新增、修改、删除、导出按钮权限，`sys_salary_template` 当前有 2 条记录；但前端没有 `system/salaryTemplate/index.vue`，也没有 `system:salaryTemplate:*` 对应 API/Controller，已列为 P2-73。
- OA 工资页 `/oa/salary` 能渲染“我的/全部”视图、工资月份、查询、计算本月工资、工资参数、导出和工资记录表；当前 `oa_salary_record=0`，页面列表为空态，与数据库一致。本轮触发“计算本月工资”只打开覆盖提示“确认重新计算 2026-06 的工资？将覆盖已有数据。”，已取消/刷新关闭，数据库仍为 0 条工资记录。
- 2026-06-24 后续权限树复核：当前 `sys_menu` 已重新出现 `oa:salary:list/query/config/calculate/export`，其中运营经理和总经理持有工资列表、计算、配置和导出权限，店长持有 `oa:salary:query`。2026-06-23 一度查不到这些权限的结论已修正为权限树回填/漂移问题，见 P2-75；工资基础数据覆盖不足和计算闭环问题仍按 P2-43 处理。
- 2026-06-24 继续只读复核 OA 工资权限和覆盖：运营经理账号 `12345/ii/liu123456` 与总经理 `ry` 持有 `oa:salary:list/config/calculate/export/query`，店长角色 12 个有效账号仅持有 `oa:salary:query`；`ShopScopeService` 会用 `sys_user_shop` 限制非管理员选择组织，但当前唯一 `oa_salary_config` 仍落在 `104 / 仓库 / WAREHOUSE`，6 个 STORE 门店均无工资参数。门店授权覆盖中，`103` 有 12 个授权用户、`108` 有 10 个授权用户，但只有 `108` 1 人有员工薪资绑定，其余门店为 0，P2-43 继续有效。
- 工资详情权限边界不完整：页面和移动端列表都用 `oa:salary:list` 区分“全部工资”，但详情接口只要求 `oa:salary:query` 且服务层只校验店铺范围，不校验记录本人或完整列表权限；工资记录一旦生成，店长可通过详情 API 查看同店他人工资明细，已新增 P2-104。
- OA 工资参数弹窗展示唯一配置：上班时间 `17:21`、下班时间 `17:21`、日标准工时 `8.00`、迟到/早退扣款 `1.00`、缺勤扣款 `200.00`、加班费 `50.00`。数据库原值为 `17:21:12` 到 `17:21:15`，只相差 3 秒却配置 8 小时，另列 P3-26。
- 源码计算链路：`OaSalaryServiceImpl.calculateSalary` 会先按当前店铺取 `oa_salary_config`，没有配置就抛“未配置工资参数，请先配置”；再调用 `OaSalaryEmployeeMapper.selectSalaryEmployeesByShopDeptId` 取该门店有效用户，并要求每个员工有当前门店、当前月份有效的 `sys_user_salary_scheme`，否则抛“员工 [xxx] 未配置薪资方案”。当前门店配置和员工绑定覆盖不足会直接阻断工资计算。工资记录状态只会被计算服务写成 `draft`，没有确认、调整或锁定入口，已新增 P3-98。
- 2026-06-24 继续只读复核薪资档位删除依赖：当前 `sys_user_salary_scheme` 已有用户 `ii` 绑定档位 `11001`，角色薪资也有 4 条档位引用；前端薪资配置页允许直接删除档位，后端 `deleteSalarySchemeItemById` 不检查员工/角色绑定，工资计算又用 `inner join sys_salary_scheme_item` 判断员工是否有薪资方案。已新增 P2-107。

## 2026-06-23 单据审计日志、清理备份表和单号序列盘点

- 单据状态审计功能只在后端和权限树里存在：`sys_menu` 有 `4491 / 状态审计查询 / inv:audit:list`，但它是按钮权限节点，没有 `path` 和 `component`；管理员、店长、驻店经理、运营经理、总经理角色均持有该权限。后端 `InvDocumentStatusLogController` 提供 `/audit/list`，数据库 `inv_document_status_log=2`，但前端 `erp-ui/src` 没有 `/audit`、`audit/list` 或 `inv:audit:list` 调用，已列为 P2-44。
- 2026-06-24 后续权限树复核：当前 `sys_menu` 已重新出现 `4491 / 状态审计查询 / inv:audit:list`，且授给管理员、店长、驻店经理、运营经理、总经理；2026-06-23 一度查不到该权限的结论已修正为权限树回填/漂移问题。当前核心问题仍是它只有按钮权限节点，没有电脑端页面、路由或详情时间线入口。
- 早前快照中 `inv_document_status_log` 两条都来自盘点单 `SC202606140001`：状态从 `draft` 到 `checking`，再从 `checking` 到 `cancelled`，组织为 `108 / 市场部门`，操作者为 `admin`。这说明当时状态审计数据已经产生，但电脑端没有页面能把这些日志展示给业务或审计人员；当前库状态审计表名和正式表状态已在 P2-91 另行复核。
- 非运行态 `BossERP` 清理备份表大量留在业务库：当时存在 32 张 `*_clear_backup_202606*` / `*_cleanup_backup_202606*` 表，其中 17 张非空，共 172 条历史业务行，覆盖采购单、采购计划、入库记录、出库记录、发货通知、库存、库存日志、盘点单、调拨单和状态审计等。与此同时该库正式表 `inv_purchase_order=0`、`inv_purchase_detail=0`、`inv_inbound_record=0`、`inv_outbound_record=0`、`inv_stock=0`、`inv_stock_log=0`、`inv_transfer_order=0`、`inv_delivery_notice=0`，前端相关页面若误连该库会展示空态，已列为 P3-29。
- 旧/非运行态备份数据里保留了正式页面已不可见的业务痕迹：包括 `inv_delivery_notice_clear_backup_20260607_1630=2`、`inv_delivery_notice_detail_clear_backup_20260607_1630=3`、`inv_inbound_record_clear_backup_20260607_1630=1`、`inv_outbound_record_clear_backup_20260607_1630=7`、`inv_purchase_order_clear_backup_20260607_1630=1`、`inv_purchase_detail_clear_backup_20260607_1630=1`、`inv_purchase_plan_clear_backup_20260607_1630=1`、`inv_purchase_plan_detail_clear_backup_20260607_1630=1`、`inv_status_audit_clear_backup_20260607_1630=6`、`inv_stock_check_clear_backup_20260607_1630=2`、`inv_stock_check_detail_clear_backup_20260607_1630=3`、`inv_stock_clear_backup_20260607_1630=8`、`inv_stock_cleanup_backup_20260607=8`、`inv_stock_log_clear_backup_20260607_1630=50`、`inv_stock_log_cleanup_backup_20260607=50`、`inv_transfer_order_clear_backup_20260607_1630=14`、`inv_transfer_detail_clear_backup_20260607_1630=14`。这些历史行在电脑端正式业务页面没有归档入口或口径说明；当前运行态另有 20260607_1640/20260608_1530 批次备份，见 P3-29。
- 单号序列表与当前正式单据也不完全同口径：`inv_number_sequence` 保留 PO、TF、TS、SC 等 11 条序列，其中 `PO / 2026-06-06`、`TF / 2026-06-08`、`TS / 2026-06-08` 的 `current_seq` 大于 0，但当前正式单据表没有对应日期记录；这些号码只能在清理备份表里追溯，已列为 P3-30。源码显示 `TS` 为调拨发货批次号，销售发货通知使用 `DN`。
- 本轮只读核对数据库、菜单权限和源码引用，未调用单据新增、清理、状态变更或工资计算等写接口。

## 2026-06-23 劳动合同页面、合同数据和签署证据细化盘点

- 早前劳动合同数据库状态：`oa_labor_contract=9`，其中 `signed=1`、`voided=8`；`oa_labor_contract_event=59`，事件分布为 `create=9`、`send=9`、`sign=5`、`void=8`、`download=28`；模板 `oa_labor_contract_template=2`，均为启用内置模板；企业章 `oa_company_seal_config=1`，当时启用。
- 后续复核：非运行态 `BossERP` 已变为 `oa_labor_contract=0`、`oa_labor_contract_event=0`、`oa_company_seal_config=0`、`oa_labor_contract_template=2`；运行态库仍为 9 份合同、59 条事件和 1 条企业章。本节基于运行态合同证据继续保留，空合同结论已在 P1-19 改为非运行态漂移。
- 电脑端路由复测：当前浏览器同一登录态下 `/oa/salary` 和 `/oa/index` 都能渲染，但 `/oa/labor-contract` 与 `/oa/labor-contract?probe=1` 等待后只剩主题色控件“清空 / 确定”，正文文本长度为 0，没有 tab、合同表、模板表或按钮，已列为 P2-45。
- 早前数据和组织对应存在明显错位：9 条合同中 7 条的 `shop_dept_id=104 / 仓库`，但合同员工大多来自 `employee_dept_id=108 / 市场部门`；还有 `employee_id=1 / 管理员` 的合同挂在 `104 / 仓库`。劳动合同服务保存时以当前选择组织作为合同 `shop_dept_id`，前端员工选择又可以带出当前组织以外的员工，已列为 P2-46。
- 早前签署证据流程数据不完整：合同 1、2、3 有发送/签署/作废事件，但 `preview_file_hash`、`archive_file_hash`、`certificate_file_hash` 均为空，早期事件 `1-9` 的 `document_hash`、`prev_event_hash`、`event_hash` 也为空；后续事件才开始写入哈希链。详情弹窗会展示文档哈希和事件哈希，但当时存量合同无法形成完整证据链，已列为 P3-31。
- “验 hash”从 UI 看是查询按钮，但后端 `verifyContractHash` 会按全库证据哈希匹配合同、返回合同号/员工名/文件类型并追加 `verify` 事件；该接口没有按当前选择门店/仓库过滤。本轮为了不新增事件，未提交 hash 验证，源码问题已列为 P2-47。
- 合同文件按钮看起来是只读预览，但实际会先查预览信息再调用下载接口；下载接口每次都会写入 `download` 事件，早前 59 条事件里已有 28 条下载事件。页面没有提示“打开文件会写入审计事件”，对审计链口径和用户预期不够清楚，已列为 P3-32。
- 2026-06-24 继续只读复核劳动合同作废流程：运行态库仍为 `oa_labor_contract=9`，其中 `signed=1`、`voided=8`，事件分布仍含 `sign=5`、`void=8`；运营经理角色 `105` 的 `12345/ii/liu123456` 持有 `oa:laborContract:void`。前端对所有非 `voided` 合同显示“作废”，后端 `voidContract` 只跳过已作废状态，不阻止 `signed` 合同，也不要求作废原因或审批，已新增 P1-34。
- 2026-06-24 继续只读复核劳动合同事件哈希算法：当前 59 条事件中后续事件已经有 `event_hash`，但服务端计算哈希时没有覆盖事件摘要、操作者名称、备注和下载文件动作类型等详情页展示字段，已新增 P3-102。
- 劳动合同源码目录存在重复备份痕迹：`erp-ui/src/views/oa/laborContract/index.vue` 旁边还有 `index 2.vue`，模板资源也有 `templates/labor-contract 2`，编译产物下存在多个 `* 2.class`、`* 3.class`。这些重复文件当前未参与菜单路由，但会干扰后续排查和发布包清理，作为工程卫生问题记录在本节，不单独列为高优先级业务缺陷。

## 2026-06-23 固定资产配置和维修上报细化盘点

- 运行库状态：运行态 `BossERP_stock_state_75c59ee` 已存在 `oa_fixed_asset_config`、`oa_fixed_asset_quota`、`oa_fixed_asset_repair`、`oa_fixed_asset_quota_ledger` 四张表，当前均为 0 行；`sys_menu` 中已存在 `3310 / 固定资产管理`、`3311 / 固定资产配置`、`3320 / 固定资产维修上报` 及配置查询/保存/删除/异常批准/导出按钮。正确桌面路由为 `/oa/fixed-asset/config` 和 `/oa/fixed-asset/repair`，本轮可渲染；旧 `/oa/fixedAsset/*` 直连仍为 404，继续按 P1-05 处理。
- SQL 脚本和授权状态：运行态 8 个固定资产菜单/按钮当前均只授权 `role_key=admin`，没有给店长、驻店经理、运营经理或门店角色维修上报权限，已列为 P2-48。
- 配置页功能模型：前端 `fixedAsset/config/index.vue` 包含搜索、重置、刷新、新增固定资产、异常批准、导出、编辑、删除、额度卡片、新增/编辑弹窗和异常批准弹窗；API 覆盖 `/oa/fixedAsset/config/list`、`/quota`、`/save`、删除、`/exception/approve` 和导出。当前运行态表和菜单存在但数据为空；本轮打开配置页可见“新增固定资产 / 异常批准 / 导出”，新增和异常批准弹窗可打开，但空数据和零额度下仍暴露写入口，另列 P3-82。
- 维修上报功能模型：前端 `fixedAsset/repair/index.vue` 包含搜索、重置、新建上报、导出、确认上报、详情弹窗和维修上报弹窗；API 覆盖 `/oa/fixedAsset/repair/list`、`/submit`、`/{repairId}/confirm` 和导出。移动端配置中也有 `/mobile/fixed-asset-repair` 且限定 `allowedDeptTypes: ['STORE']`，但本轮目标是电脑网页端，移动端只作为权限意图参考。
- 维修上报状态闭环不完整：页面和 Excel 状态转换都包含 `draft/rejected/cancelled`，但固定资产后端目前只有普通上报、异常批准生成待确认、确认上报三类写入口，没有草稿保存、驳回、取消或撤回接口，已新增 P3-97。
- 额度流程存在读写边界风险：配置页和维修页的列表加载后都会调用 `getFixedAssetQuota`；后端 `getQuotaSummary` 在 `quotaMapper.selectQuota` 返回空时会调用 `rebuildQuota`，进而 `insertQuota` 或 `updateQuota`。这意味着 GET 额度查询在表存在时会写入/更新额度记录，已列为 P2-49。
- 异常批准流程和页面文案不一致：配置页弹窗提示“异常批准会生成一条待确认上报记录，门店核对后才能正式上报”；但后端 `approveExceptionRepair` 创建 `pending_confirm` 维修单的同时已经调用 `insertLedger` 写入额度流水，`sumUsedQuotaAmount` 会把 `advance_future_months` 计入已用额度。待门店确认前额度已经被占用，已列为 P2-50。
- 固定资产商品语义不足：运行态 `inv_product=162` 且全部 `status=0`，表结构没有固定资产标识；非运行态 `BossERP` 的 `inv_product=8` 窄表同样没有固定资产标识。配置页搜索固定资产商品时只调用 `listProduct({ status: '0' })`，不按固定资产类型、门店资产范围或专门分类过滤。样例商品都是茶叶 SKU，例如“清香铁观音”“武夷大红袍”，已列为 P3-33。
- 固定资产配置缺少唯一资产身份：运行态 `oa_fixed_asset_config` 当前 0 行，只有 `config_id` 主键和普通店铺/商品索引；前端新增弹窗和后端保存逻辑均不阻断同一店铺重复选择同一商品，额度重建按配置金额求和，已新增 P3-105。
- 维修附件体验偏弱：维修上报弹窗的“图片/附件”只是普通输入框，提示“可填写上传后的图片或附件地址，多个用逗号分隔”，没有上传、预览、必填或格式校验，已列为 P3-34。
- 本轮没有执行 SQL 脚本、没有访问会写数据的接口、没有点击固定资产保存/删除/异常批准/上报/确认/导出。

## 已验证正常

1. 管理员登录后会进入店铺/仓库选择页，集团和公司不可直接进入业务，门店和仓库可选；页面说明能区分门店经营和仓库作业。
2. 单门店账号 `qa144822s` 只看到一个业务组织 `研发部门`，进入后左侧菜单不展示系统管理、监控、仓库采购等管理员/仓库页面。
3. 仓库账号 `qa144822w` 只看到一个业务组织 `仓库`，进入后菜单集中在仓库管理，不展示门店销售、OA 薪资、劳动合同等页面。
4. 单门店账号访问 `/oa/salary`、`/oa/labor-contract` 返回 404，未暴露薪资和劳动合同页面。
5. 单门店账号的库存页没有“库存调整”按钮；仓库账号的库存页有“库存调整”按钮，符合角色差异。
6. 单门店账号的报表中心、商品列表和商品详情在页面展示层会隐藏成本/毛利字段；但后端接口仍返回部分成本字段，详见 P1-09、P1-26。
7. 门店调拨页的“发起要货”弹窗会带出当前门店，并要求选择目标仓库，流程文案比直接暴露仓库操作更清楚。
8. 新建用户 `qa_audit_62201` 在保存 `103` 研发部门授权后，可以登录、选择门店并进入工作台；菜单与店长角色一致，没有系统管理、系统监控、仓库采购等管理员/仓库页面。
9. 管理员切换到 `104` 仓库上下文后，通过当时菜单/入口路径采购管理、采购退货、库存管理、调拨管理、调拨记录页面均能打开，仓库采购页正确显示“当前采购仓库：仓库”，库存页曾在已加载状态正确显示 1 条仓库库存；仓库采购相关深链/刷新空白问题另列 P2-19，库存入口复测不稳定另列 P2-25。
10. 门店角色 `qa_audit_62201` 的客户管理、销售退货、库存盘点、发货通知、调拨记录页面均能打开，未出现前端错误覆盖层或控制台错误。
11. 管理员系统管理页中的角色、菜单、部门、岗位、通知、字典、参数、在线用户、定时任务、调拨审批配置均能打开并显示对应数据或空态；薪资配置裸路由空白、带 query 可显示的问题另列 P2-42。
12. 日志管理页面通过菜单嵌套路由 `/system/log/operlog`、`/system/log/logininfor` 可以打开，并能显示操作日志和登录日志列表。
13. OA 待办、已办、工资管理在当前空数据状态下有空态文案；劳动合同当前复测路由空白，不再按正常项确认，见 P2-45。
14. 早前仓库商品、商品分类、供应商页面分别展示 162 条商品、30 个分类、17 个供应商，与运行态数据库主数据数量对应；非运行态 `BossERP` 主数据曾变为 `inv_product=8`、`inv_product_category=11`、`inv_supplier=3`，不作为当前网页端商品页结论。
15. 仓库采购、采购退货在当前 `inv_purchase_order=0` 的状态下，通过已加载路由状态能打开并显示空态，采购页有“当前仓库暂无采购单”的业务文案；仓库采购相关深链/刷新空白问题另列 P2-19。
16. 门店侧客户、销售退货、发货通知、调拨、库存盘点、报表等路由在仓库上下文直连时没有白屏或错误覆盖层；其中上下文不匹配的按钮暴露问题已列入 P2-11。
17. 系统用户、角色、菜单、部门、岗位、公告、字典、参数、定时任务等新增弹窗均能打开并显示对应字段；薪资配置在带 query 可渲染状态下能打开员工绑定新增弹窗，但裸路由空白和角色入口失效分别列 P2-42、P1-08。
18. 早前系统监控在线用户、定时任务、调度日志页面能按当时数据展示列表或空态；最新运行态仍为 `sys_job=3` 且 3 条均停用，`sys_job_log=0` 与调度日志空态对应。旧/非运行态曾出现启用但目标缺失的钉钉同步任务，见 P2-83。
19. 劳动合同模板和企业章数据在运行态数据库中分别为 2 条和 1 条，源码包含新增/编辑模板、企业章配置、验 hash 和详情弹窗；该路由曾出现空白壳，前端重启后可渲染，但仍需按 P2-45 做直连/刷新/组织切换回归。
20. 工资管理页面的工资参数弹窗能打开，并展示上下班时间、标准工时、迟到/早退/缺勤扣款、加班费等配置字段；参数值明显不合理的问题另列 P3-26，本轮未提交保存或重新计算。
21. 当前运行态调度任务表 `sys_job` 仍为 3 条记录且均停用，调度日志为空；前端定时任务和调度日志页面与数据库状态对应。旧/非运行态曾出现 1 条启用但目标 Bean 缺失的钉钉同步任务，见 P2-83。
22. 当前运行态没有 `wf_%` 工作流业务表，Activiti 只有 1 条流程部署、1 条流程定义和少量引擎元数据，运行任务与历史流程实例均为 0；旧/非运行态出现的 `wf_instance`、`wf_leave_quota`、`wf_urge_log` 和 `ACT_RU_TASK` 非空结论不作为当前网页端运行态结论，见 P1-14。
23. 发货通知页在运行态 `inv_sales_order=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0` 的数据库状态下显示空态，筛选按钮和导出入口能渲染，未出现前端错误；非运行态销售单和发货通知断链问题见 P1-16。
24. 早前快照中，门店 `市场部门` 上下文能看到库存盘点单 `SC202606140001`，状态为“已取消”时列表只保留“详情”动作，没有继续显示“录入实盘 / 确认盘点 / 取消 / 删除草稿”等写入动作；本轮运行态盘点主表和明细仍各 1 条。
25. 早前快照中，库存盘点详情弹窗能展示盘点单号、库存组织、创建人、盘点明细、账面数量、实盘数量、差异数量和差异类型；当时明细“清香铁观音”显示盘亏 4 斤，与 `inv_stock_check_detail` 数据对应。
26. 调拨记录页能展示已完成调拨单 `TF202606180001`，详情弹窗能展示要货明细和发货/收货批次 `TS202606180001`；缺少审批轨迹和状态流水的问题另列 P2-14。
27. 早前快照中调拨审批配置页展示 1 条启用规则“市场部门”，列表字段“单据类型 / 调拨类型 / 适用范围 / 条件 / 通过方式 / 驳回动作 / 优先级 / 状态 / 更新时间”和运行态数据库 `inv_transfer_approval_rule` 记录对应；非运行态缺表状态见 P1-22。
28. 登录页在 `sys.account.registerUser=false` 时不显示“立即注册”入口；账号、密码、验证码、记住密码、登录按钮均能渲染，未出现前端错误覆盖层。
29. 桌面首页 `/index` 能读取当前组织上下文并显示 `门店：市场部门`，常用入口会按门店/仓库上下文切换库存和销售/采购路径；采购待办文案不匹配问题另列 P3-10。
30. 采购管理在已成功加载的仓库上下文中，单号、标题、状态筛选、新建采购单、导出按钮、当前仓库提示和空态文案能渲染；当前 `inv_purchase_order=0` 与列表空态对应，刷新空白问题另列 P2-19。
31. 新建采购单弹窗和选择采购商品子弹窗能打开，采购商品弹窗的商品名/供应商筛选、搜索、重置能响应；弹窗展示不完整商品和一次渲染 162 行的问题分别列 P1-07、P2-20。
32. 采购后端保存草稿、保存并提交、收货、质检、取消、删除草稿接口与仓库上下文校验逻辑完整；收货会生成待检入库记录，质检通过或让步后再写库存和库存日志。
30. 个人中心 `/user/profile` 能展示管理员账号的用户名、手机号、邮箱、部门、角色、创建日期；基本资料和修改密码 tab 均能打开，修改密码前端使用当前密码策略校验。
31. 早前仓库分类管理页 `/cangku/category` 在 `仓库：仓库` 上下文显示 30 个分类、30 个正常分类，与当时 `inv_product_category` 有效数据对应。
32. 分类管理关键词搜索和重置能在当前前端树数据内正确过滤和恢复；新增根分类和编辑分类抽屉字段、禁用状态与源码逻辑一致。
33. 早前供应商主数据与商品引用关系完整：17 个供应商中没有重名，商品供应商名称没有孤儿引用；后端供应商删除逻辑会阻止已被商品或采购历史引用的供应商被删。
34. 早前仓库商品管理页 `/cangku/product` 能渲染 162 条商品、31 个分类树节点和商品搜索区；搜索商品名称、点击分类树和重置均能按当时数据刷新结果。
35. 商品新增、详情、编辑、导入弹窗能打开并显示对应字段；导入弹窗明确支持普通模板和茶叶分类 Excel，本轮未提交任何写入。
36. 商品删除、导出、下载模板都会先出现确认框；删除确认包含商品编码和商品名，导出确认包含导出范围、筛选条件和成本敏感字段提示。
37. 商品详情能与数据库字段对应显示价格、规格、单位、供应商电话、采购备注和产品描述；管理员有 `inv:cost:view` 权限时能看到采购价和成本价。
38. 运行态 `sys_user_shop` 显式授权均指向正常启用的 STORE/WAREHOUSE 组织；多门店账号 `qa144822m` 的授权 API 返回 `[103,108]`，仓库账号 `qa144822w` 的授权 API 返回 `[104]`，与运行态数据库一致。非运行态 `BossERP` 的 `sys_user_shop=0` 仅作为环境漂移风险，见 P3-46。
39. 首页顶部公告入口在 `sys_notice=0`、`sys_notice_read=0` 时显示“暂无公告”，`/system/notice/listTop` 返回 `data=[]、unreadCount=0`，与当前数据库状态一致；通知公告管理页直连空白问题另列 P2-35。
40. 定时任务详情和操作日志详情两个列表内副弹窗能打开：任务详情与 `sys_job` 第一条记录对应，操作日志详情与 `sys_oper_log` 最新记录对应；操作日志时间偏移问题另列 P2-09。
41. 运行态 `qa_audit_62201` 的用户分配角色 API 和页面在刷新等待后能对应到数据库：页面只勾选店长角色，`sys_user_role` 也只有 `103 / 店长` 一条绑定；运行态 25 个有效账号都至少有一个角色。非运行态 `BossERP` 的 2 个无角色账号不作为当前网页端结论，见 P3-20。
42. 店长角色的已分配用户 API 返回 12 条，页面最终能显示已授权用户列表和取消授权按钮；“添加用户”副弹窗能打开并加载候选用户列表，候选组织类型混杂问题另列 P2-36。
43. 系统基础配置页在长等待后能对应当前数据库：角色 11、部门 11、岗位 10、字典类型 10、用户性别字典项 3、系统参数 8 均能在对应页面或副页面显示；慢加载/初始空态另列 P3-23。
44. 当前 `sys_dept` 树本身符合集团、公司/仓库、门店的层级模型；部门维护流程缺少后续新增/编辑保护另列 P2-38。
45. 字典、岗位、部门、菜单的后端删除保护存在，能阻止已有字典项、用户、子节点或角色绑定的数据被删除；前端删除按钮没有前置禁用或引用提示另列 P3-21。
46. 调度日志副页面 `/monitor/job-log/index/0` 能在 `sys_job_log=0` 时显示空态，删除按钮保持禁用，搜索、重置和关闭按钮可见；空态下清空/导出仍可点的问题另列 P3-25。
47. 登录日志、操作日志、在线用户和定时任务的数据源本身存在：最新运行态精确复核为 `sys_logininfor=501`、`sys_oper_log=244`、`sys_job=3`、`sys_job_log=0`；早前 Redis 有 23 个登录 token。本轮问题集中在主列表路由渲染、按钮边界和任务目标一致性，不是数据库完全缺失。

## 问题清单

### P1-01 门店账号可看到库存变动日志成本价

- 现象：`qa144822s` 没有 `inv:cost:view` 权限，但访问 `/inventory/stock-log` 时表格展示“成本价”列；库存数据导出也会把移动加权成本价和库存总成本写入 Excel。
- 证据：前端 `erp-ui/src/views/inventory/stock/log.vue:91` 对成本价列无权限判断；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockController.java:75-95` 的库存和库存变动日志导出都只校验 `inv:stock:export`，没有成本字段脱敏；`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java:89-93` 对 `costPrice`、`totalCost` 标注了 `@Excel`。
- 影响：门店角色可以通过页面和导出接触成本数据，和报表/商品页已经做的成本权限策略不一致；库存页虽然不直接展示成本列，但导出会绕过页面展示边界。
- 建议：库存列表、库存变动日志页面和导出同时按 `inv:cost:view` 控制成本字段；无权限时前端隐藏列，后端查询或导出结果脱敏/剔除。

### P1-02 用户管理“配置状态”全量显示待同步

- 现象：管理员进入 `/system/user` 后，列表的“配置状态”列显示“配置状态待同步”，无法判断用户是否缺角色或缺店铺/仓库授权。
- 证据：前端 `erp-ui/src/views/system/user/index.vue:344-363` 依赖 `setupStatus`、`roleCount`、`shopScopeCount`；本轮接口 `/system/user/list?pageNum=1&pageSize=5` 返回行数据没有这些字段。
- 复核：2026-06-23 当前工作区源码已出现 `setupStatus/roleCount/shopScopeCount` 字段、mapper 计算和前端筛选，但运行态接口仍未体现；使用 admin token 请求 `/system/user/list?pageNum=1&pageSize=5&setupStatus=missingShopScope` 返回 `total=25`，rows 仍不包含 `setupStatus`、`roleCount`、`shopScopeCount`，筛选条件也未生效。因此在当前电脑端运行服务中 P1-02 仍成立，源码改动需重新构建/部署后再复测关闭。
- 影响：创建/维护多账号时，管理员无法在列表页闭环检查“角色 + 组织授权”是否完成。
- 建议：后端列表补齐配置状态字段，或前端在没有字段时隐藏该列并给出明确的“当前版本不支持状态统计”提示。

### P1-03 新增用户默认密码与前端密码规则冲突

- 现象：新增用户弹窗默认密码为 `123456`，但前端校验要求密码长度 8-20 位；直接提交会提示“密码长度必须介于 8 和 20 之间”。
- 证据：数据库 `sys.user.initPassword = 123456`；前端 `erp-ui/src/views/system/user/index.vue:422-431` 将默认密码写入表单；`erp-ui/src/utils/passwordRule.js:35-37` 要求 8-20 位。
- 影响：管理员按默认流程无法顺利新建用户，且系统配置和安全策略互相矛盾。
- 建议：把默认初始密码配置改为满足当前规则的临时密码，或让前端基于后端密码策略提示并阻止保存不合规配置。

### P2-06 新增用户后的“去配置店铺授权”不携带新用户 ID

- 现象：本轮通过网页端创建 `qa_audit_62201` 后，点击“去配置”进入 `/system/shop?userName=qa_audit_62201`，URL 没有 `userId`。页面当前能通过用户名筛选并自动选中新用户，但不是基于后端返回的新用户 ID。
- 证据：当前源码中，用户列表行内“店铺授权”已经通过 `handleShopScope(row)` 传递 `userId/userName`，见 `erp-ui/src/views/system/user/index.vue:467-475`；但新增成功后的下一步仍在 `addUser(this.form)` 前复制 `createdUser = Object.assign({}, this.form)`，成功后调用 `confirmShopScopeAfterCreate(createdUser)`，见 `erp-ui/src/views/system/user/index.vue:500-505`。后端新增接口只返回 `toAjax(userService.insertUser(user))` 的影响行数，不返回新用户 ID，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:269-295`；`SysUserServiceImpl.insertUser()` 也只返回插入行数，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:273-283`。店铺授权页虽会读取 route query 中的 `userId/userName` 并聚焦目标用户，见 `erp-ui/src/views/system/shop/index.vue:161-177`、`:211-220`，但新增成功跳转时 `userId` 为空，只能退回按 `userName` 搜索定位。
- 影响：当前流程可继续配置，但依赖用户名回退定位，不如直接使用 `userId` 稳定；如果未来允许改名、批量导入后自动定位、或用户名查询改为模糊匹配，闭环容易退化。
- 建议：后端新增用户接口返回新用户 ID；前端用返回 ID 跳转到店铺授权页，用户名只作为展示辅助。

### P1-05 固定资产运行态表和菜单存在但路径命名不一致、空数据写入口仍暴露

- 现象：项目内存在固定资产配置和维修上报的前端视图、后端 Controller/Mapper/Service、移动端入口配置和 SQL 脚本；运行态库也已经有固定资产表和菜单，但四张业务表均为空，菜单/按钮只授权 admin。正确路由是 `/oa/fixed-asset/config`、`/oa/fixed-asset/repair`，而组件和 API 使用 `fixedAsset` 驼峰命名，旧 `/oa/fixedAsset/*` 直连会 404。
- 证据：运行态 `BossERP_stock_state_75c59ee` 中 `oa_fixed_asset_config=0`、`oa_fixed_asset_quota=0`、`oa_fixed_asset_quota_ledger=0`、`oa_fixed_asset_repair=0`；`sys_menu` 已有 `3310 / 固定资产管理`、`3311 / 固定资产配置`、`3320 / 固定资产维修上报` 及配置查询、保存、删除、异常批准、导出按钮，状态均启用可见，但 `sys_role_menu` 只给 `role_key=admin` 授权。Playwright 复核 `/oa/fixed-asset/config` 能显示“新增固定资产 / 异常批准 / 导出”和空表，`/oa/fixed-asset/repair` 能显示“新建上报 / 导出”和空表；`/oa/fixedAsset/config`、`/oa/fixedAsset/repair` 均进入 404。
- 影响：固定资产功能从数据库/菜单看像已上线，但普通门店角色没有维修上报入口，空数据下仍能打开新增、异常批准和维修上报弹窗；路由路径、组件路径和 API 路径命名不一致，会让直连排查和文档说明混乱。多账号多店铺场景下，固定资产究竟是 admin 内测功能、门店可用功能，还是待上线功能，当前交付边界不清晰。
- 建议：明确该模块是否纳入当前电脑端交付。若要上线，需要补齐角色矩阵、门店维修上报授权、固定资产样例配置、额度初始化、空数据下的按钮禁用/提示，并统一文档中的路由路径；若暂不上线，应在交付说明中标记未启用，并隐藏移动端资产报修入口或移除菜单授权。

### P1-06 店铺授权页会把用户所属部门显示成已授权组织范围

- 现象：本轮创建 `qa_audit_62201` 后，在尚未保存店铺授权时，数据库 `sys_user_shop` 对该用户为 0 条，但 `/system/shop?userName=qa_audit_62201` 左侧用户列表“组织范围”显示“研发部门”；右侧授权树实际没有任何勾选。
- 证据：数据库创建后、保存授权前查询 `sys_user_shop` 为 0；前端 `erp-ui/src/views/system/shop/index.vue:360-368` 在没有 `shopScopeLabel` 时回退到 `getUserDeptScopeLabel(row)`，即用用户所属部门显示组织范围。
- 影响：管理员容易误以为新用户已经完成门店授权，尤其是用户所属部门刚好等于要授权的门店时，会造成“看起来已授权、实际未授权”的配置闭环误判。
- 建议：组织范围列只展示 `sys_user_shop` 的显式授权；无授权时显示“未授权”。用户所属部门应作为单独列或详情信息，不应混用为授权范围。

### P1-07 采购商品选择器展示大量采购主数据缺失商品

- 现象：仓库上下文进入 `/cangku/purchase` 新建采购单，点击“选择商品”后，列表中出现商品编码、单位、供应商、采购参考价为空的商品；采购单又依赖商品自动带出供应商。
- 证据：早前数据库 `inv_product` 162 条商品中，112 条缺商品编码、5 条缺单位、119 条缺供应商名称、114 条缺采购价；页面实测能在“选择采购商品”列表看到这些空字段。后续当前商品表已变为 8 条且 schema 与源码不一致，另见 P1-20。前端 `erp-ui/src/views/inventory/purchase/index.vue:430-449` 会在添加明细时提示“以下商品未绑定供应商”，`erp-ui/src/views/inventory/purchase/index.vue:512-513` 提交时也会阻断未绑定供应商商品。
- 影响：采购员可以看到并尝试选择不可采购的商品，直到添加或提交时才被拦截；采购流程和商品主数据质量没有在入口处闭环。
- 建议：采购商品选择器只展示采购主数据完整的商品，或在列表中明确标记“缺供应商/缺采购价/不可采购”并禁选；商品管理页也应提供主数据完整性筛选。

### P1-08 角色薪资配置入口无法维护角色薪资绑定数据

- 现象：角色管理行操作中存在“薪资配置”入口，点击逻辑会跳到 `/system/salary?roleId=角色ID`；但薪资配置页只展示“薪资方案 / 档位明细 / 员工绑定”，不会识别 `roleId`，也没有角色绑定视图。数据库已有 `sys_role_salary_scheme` 4 条角色薪资绑定，页面无法查看或维护这些角色级绑定。
- 证据：2026-06-23 浏览器直连 `/system/salary?roleId=103`，页面仍停在薪资方案列表，正文不包含角色名称或角色绑定；`erp-ui/src/views/system/role/index.vue:149-150` 暴露“薪资配置”菜单项，`erp-ui/src/views/system/role/index.vue:567-569` 仅跳转到 `/system/salary?roleId=...`；`erp-ui/src/views/system/salary/index.vue:3-127` 只有三类 tab，`erp-ui/src/views/system/salary/index.vue:909-923` 初始化时没有读取路由 `roleId`；`erp-ui/src/api/system/salaryConfig.js:106-130` 和 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:170-193` 已存在角色薪资查询/保存接口。
- 影响：角色薪资配置在菜单、权限、API 和数据库层面都像已上线，但桌面端无法完成角色薪资维护；已有角色薪资数据和前端展示不对应，管理员容易误以为进入薪资配置页后已经在配置该角色。
- 建议：要么在薪资配置页增加“角色绑定”模式并根据 `roleId` 自动切换、回显和保存角色绑定；要么移除角色管理中的“薪资配置”入口，并在交付范围中说明当前只支持员工薪资绑定。

### P1-09 报表汇总接口向无成本权限角色返回库存成本和毛利

- 现象：报表中心前端会用 `inv:cost:view` 隐藏库存成本、采购金额、预估毛利卡片，但后端 `/inventory/report/summary` 只校验 `inv:report:list`，仍返回 `totalStockCost`、`purchaseAmount`、`grossMargin`。
- 证据：数据库角色授权显示店长、驻店经理、运营经理、总经理有 `inv:report:list` 但没有 `inv:cost:view`；`InvReportController.summary()` 只标注 `@RequiresPermissions("inv:report:list")`；`InvReportMapper.xml` 汇总 SQL 返回 `total_stock_cost`、`purchase_amount`、`gross_margin`。2026-06-23 使用店长 `qa144822m` token、`Dept-NumId:108` 调用接口，返回 `totalStockCost=1008.00`。
- 影响：成本字段虽然在页面卡片上隐藏，但拥有报表查询权限的非成本角色仍可通过接口或浏览器网络面板读取库存成本和毛利，和商品/报表页面的前端成本权限策略不一致。
- 建议：后端按 `inv:cost:view` 控制成本字段，非成本权限用户应返回空值或不序列化 `totalStockCost`、`purchaseAmount`、`grossMargin`；前端隐藏只能作为展示控制，不能作为权限边界。

### P1-10 发货通知仓库选择与当前组织树不匹配，执行发货无仓库可选

- 现象：发货通知执行弹窗要求选择“发货仓库”，但前端把销售单所属门店 `shopDeptId` 作为 `scopeDeptId` 传给仓库选择器；仓库列表接口对 `purpose=deliverySource` 又只返回该门店自身、子级或祖先链包含该门店的仓库。当前数据库唯一仓库 `104 / 仓库` 挂在集团 `100` 下，所有门店 `103/105/106/107/108/109` 对该仓库的 `in_store_scope` 都为 0，因此后续即使生成发货通知，执行发货弹窗也会显示“当前销售门店没有可用发货仓库”并禁用确认发货。
- 证据：`erp-ui/src/views/inventory/deliveryNotice/index.vue:87-96` 使用 `WarehouseSelect purpose="deliverySource" :scope-dept-id="deliverDetail.shopDeptId"`；`erp-ui/src/views/inventory/deliveryNotice/index.vue:100-122` 在仓库候选为空时提示无可用仓库并禁用确认。`WarehouseSelect` 在 `erp-ui/src/views/inventory/components/WarehouseSelect.vue:110-119` 把 `purpose` 和 `scopeDeptId` 传给 `/system/dept/warehouse-list`；`SysDeptServiceImpl.selectWarehouseList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:139-143` 对 `deliverySource` 调用 `isDeptInScope(scopeDeptId, warehouse)`，`isDeptInScope` 在 `:182-204` 只接受同节点、父子或祖先关系。MySQL 复核当前 `sys_dept`：唯一仓库 `104` 的 `parent_id=100、ancestors=0,100`，对所有 STORE 门店的范围判断均为 0。服务层 `InvDeliveryNoticeServiceImpl.deliverNotice` 还会在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:174-176` 对请求仓库做同类可见范围校验。
- 影响：销售主流程被设计为“销售单提交 -> 生成发货通知 -> 执行发货扣仓库库存”，但当前组织树下门店通知没有任何可选发货仓库；仓库上下文又看不到门店归属的发货通知列表。这个问题会让销售单即使成功提交和生成通知，也无法通过电脑端完成实际出库。
- 建议：重新定义“销售门店可用发货仓库”的关系，不应简单要求仓库在门店子树内。可以基于用户仓库授权、门店-仓库配送关系表、同公司/同集团相关组织，或现有 `selectRelatedDeptIds` 模型返回可发货仓库；前端下拉、后端 `deliverNotice` 校验、移动端发货、角色授权和测试用例必须使用同一套规则。

### P1-11 用户导入会绕过当前密码策略创建弱初始密码账号

- 现象：新增用户和管理员重置密码都会按当前密码策略校验 8-20 位长度，但用户导入流程不给 Excel 模板提供密码字段，新增导入用户时直接使用系统参数 `sys.user.initPassword=123456` 并加密落库。当前该初始密码只有 6 位，和全局密码最小 8 位策略冲突。
- 证据：数据库 `sys.user.initPassword=123456`，`UserConstants.PASSWORD_MIN_LENGTH=8`。`SysUserController.add()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:288-295` 会调用 `UserConstants.getPasswordPolicyError(...)` 后才加密保存；`resetPwd()` 在 `:344-358` 也同样校验。导入接口 `/user/importData` 在 `:107-116` 直接调用 `userService.importUser(...)`；`SysUserServiceImpl.importUser()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:531-538` 对新用户只读取 `sys.user.initPassword` 并加密，没有调用密码策略校验。`SysUser.password` 没有 `@Excel` 注解，导入模板字段只覆盖部门编号、用户账号、用户昵称、邮箱、手机号、性别、状态等，无法让管理员在导入文件中指定合规临时密码。
- 影响：批量创建账号时，系统会成功落库一批不满足当前策略的 `123456` 初始密码账号；结合 P2-07 的首次改密提示可取消，导入账号可能长期使用弱默认密码。多账号多店铺上线时，批量导入是最容易一次性扩大风险面的入口。
- 建议：导入前校验 `sys.user.initPassword` 必须满足当前 `sys.account.chrtype` 和长度策略，不满足时拒绝导入并提示管理员先修改系统参数；更稳妥的是导入成功后强制首次改密，不允许取消继续使用。导入结果中也应提示“使用临时密码，需要首次修改”，并引导批量配置角色和组织授权。

### P1-12 销售退货后端未校验原销售单门店，可能把跨门店原单退入当前门店库存

- 现象：销售退货前端会从当前门店的销售单列表选择原单，但后端 `/salesReturn/save`、`/salesReturn/submit` 保存时只要求当前请求选择的是门店，没有校验 `salesOrderId` 对应原销售单也属于该门店。直接调用接口时，可以用当前门店上下文创建一张关联其他门店销售明细的退货单，确认后库存会增加到当前门店。
- 证据：`InvSalesReturnServiceImpl.saveDraft()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java:55-66` 只调用 `requireStoreContext(selectedShopDeptId)` 并把 `salesReturn.shopDeptId` 设为当前门店；`normalizeReturnDetails()` 在 `:347-396` 直接按 `salesReturn.getSalesOrderId()` 调 `salesDetailMapper.selectInvSalesDetailByOrderId(...)` 重算商品和金额，没有读取 `InvSalesOrder` 或调用 `assertAndGetScopedSales`。`validateReturnQuantity()` 在 `:239-333` 也只按销售明细的 `deliveredQuantity` 校验数量。`confirmReturn()` 在 `:161-201` 通过 `stockMapper.selectInvStockByProductAndShopForUpdate(detail.getProductId(), salesReturn.getShopDeptId())` 给当前退货单门店入库。现有 `InvSalesReturnServiceImplTest` 覆盖了数量、重复明细、门店上下文和成本计算，但没有覆盖原销售单门店一致性；正式销售/退货表及对应 20260608 备份表当前均为 0 行。
- 影响：多门店场景下，前端正常路径看似按当前门店过滤原销售单，但后端仍缺少最终边界；拥有销售退货新增/提交/确认权限的账号或脚本一旦绕过前端，就可能把其他门店的已出库商品退回当前门店库存，造成库存、销售退货、库存变动日志和销售单归属错位。
- 建议：销售退货保存和提交前必须读取原销售单并校验 `sales_order.shop_dept_id == 当前门店`，且原销售单状态/明细必须满足“已出库且仍有可退数量”。校验应放在后端服务层，前端下拉过滤只能作为体验优化；补充跨门店原单、未出库原单、部分已退原单的单元测试和接口回归。

### P1-13 采购退货后端未校验原采购单仓库，可能把别仓原单退到当前仓库库存

- 现象：采购退货保存草稿和提交时，后端要求当前选择组织必须是仓库，并把采购退货单 `shopDeptId` 设置为当前仓库；但后续只按传入的 `purchaseOrderId` 读取原采购明细，没有读取原采购单头校验该采购单也属于当前仓库。
- 证据：`InvPurchaseReturnServiceImpl.saveDraft()` 在 `:54-67` 调 `requireWarehouseContext()` 并设置退货单仓库；`normalizeReturnDetails()` 在 `:342-350` 只通过 `purchaseDetailMapper.selectInvPurchaseDetailByOrderId(purchaseOrderId)` 读取原采购明细；`validateReturnQuantity()` 在 `:234-245` 也是只读原采购明细和历史退货数量；未看到按 `purchaseOrderId` 查询采购单头并校验 `purchase_order.shop_dept_id == 当前仓库` 的逻辑。`confirmReturn()` 在 `:147-181` 会校验退货单属于当前仓库，然后扣减 `purchaseReturn.getShopDeptId()` 的库存。当前正式 `inv_purchase_order/inv_purchase_return` 均为 0，备份表只有历史采购单和采购明细，不能用正式数据复现多仓库场景。
- 影响：当前运行库只有一个仓库 `104`，短期不容易触发；但多仓库上线后，拥有采购退货新增/提交权限的账号或脚本可以把 A 仓库原采购单的已收货数量作为依据，在 B 仓库创建退货并扣减 B 仓库库存，导致采购单、退货单、库存日志和供应商对账跨仓错位。
- 建议：采购退货保存、提交和确认前都应读取原采购单头，校验原采购单仓库、状态、收货/质检状态、供应商和可退数量；原采购单号、供应商和可退明细由后端统一回填。补充跨仓原采购单、未收货采购单、已全退采购单的服务测试。

### P1-14 工作流旧口径与当前运行态不一致，残留 Activiti 定义没有电脑端解释入口

- 现象：当前网页端运行态仍不暴露工作流模块，但同机非运行态 `BossERP` 已经重新出现整组工作流菜单、按钮权限、`wf_%` 业务表和运行/历史任务。两套 schema 的工作流状态完全不同，人工 SQL 或脚本一旦误查 `BossERP`，就会把残留工作流实例、待办、回调补偿和 SLA 规则误当成当前电脑端页面问题；服务若误连该库，则会出现“菜单有入口、页面/后端实现缺失、工作流实例与业务单断开”的高风险状态。
- 证据：运行态配置仍指向 `jdbc:mysql://127.0.0.1:3306/BossERP_stock_state_75c59ee`；该运行态库 `show tables like 'wf_%'` 返回空，`sys_menu` 中 `workflow:%` / `workflow/*` 菜单权限计数为 0，`ACT_RU_TASK=0`、`ACT_HI_PROCINST=0`、`ACT_RE_PROCDEF=1`。本机 `/auth/login` 成功后调用 `/system/menu/getRouters`，返回的 OA 路由只有 `oa/todo/index`、`oa/done/index` 等，没有 `workflow/*`。对照非运行态 `BossERP`，当前有 15 张 `wf_%` 表、43 个工作流菜单/权限、`ACT_RU_TASK=2`、`ACT_HI_PROCINST=7`、`ACT_RE_PROCDEF=8`；其中 `wf_instance` 有 6 条存量实例（`submitted=1`、`approved=1`、`withdrawn=4`），`wf_callback_log` 有 2 条 `pending` 且 `retry_count=21` 的“采购申请不存在”失败记录。当前工程仍没有 `erp-ui/src/views/workflow`、`erp-ui/src/api/workflow`，`erp-modules/erp-workflow` 和 `erp-api/erp-api-workflow` 目录为空。
- 影响：当前电脑端不会暴露工作流菜单，但数据库残留和运行态 schema 漂移会持续污染验收结论。非运行态库里还有待办任务和失败回调，管理员无法从当前页面查看、归档或解释；一旦菜单或数据源被回填到运行态，工作流父菜单、我的申请、待办/已办、回调补偿、催办、SLA、代理委托等入口会缺少真实前端页面和后端服务，审批、回调和追溯链路会直接中断。
- 建议：把工作流交付范围和当前运行态 schema 固定下来。当前不交付工作流时，应清理或标注 Activiti 残留部署/定义，并在巡检中确认 `wf_%` 表和 `workflow:%` 菜单不应出现在运行库；若未来恢复工作流，应先补齐前端页面、API、后端模块、业务单据映射、待办/已办、回调补偿、SLA 和存量实例迁移，再启用对应菜单。

### P1-15 非运行态 `BossERP` 无授权与运行态授权数据冲突，账号组织审计口径分裂

- 现象：同机 MySQL 中同时存在 `BossERP` 和 `BossERP_stock_state_75c59ee` 两套 ERP schema。直查 `BossERP` 时看到 `sys_user_shop=0`、7 个有效账号无显式门店/仓库授权；但当前运行态 `erp-system` 实际连接 `BossERP_stock_state_75c59ee`，该库有 25 个有效账号和 43 条显式门店/仓库授权。
- 证据：`erp-modules/erp-system/src/main/resources/bootstrap.yml`、`application-dev.yml` 和 `target/classes` 中 datasource URL 均为 `jdbc:mysql://127.0.0.1:3306/BossERP_stock_state_75c59ee`；运行态 `GET /user/info/admin` 返回的 `admin / 管理员 / 研发部门` 与 `BossERP_stock_state_75c59ee.sys_user` 一致，而非 `BossERP.sys_user` 中的 `刘星宇 / 北京1`。运行态库当前 `sys_user=25`、`sys_user_shop=43`，授权类型为 STORE 34、WAREHOUSE 9；非运行态 `BossERP` 当前 `sys_user=8`、有效账号 7、`sys_user_shop=0`。
- 影响：如果用 `BossERP` 直查 SQL 做网页端验收，会误判所有启用账号都没有组织授权；如果用运行态页面/API 验收，又会看不到 `BossERP` 中的空授权和窄表问题。多账号多店铺审计必须先统一“运行态 API、登录日志、数据库 SQL、前端页面”四者指向同一 schema，否则账号、角色、菜单、按钮和流程结论会相互冲突。
- 建议：把 `BossERP_stock_state_75c59ee` 作为当前电脑网页端运行态权威库，后续 SQL 巡检默认使用该 schema；`BossERP` 只作为非运行态漂移样本保留。若需要切换运行态库，必须先重启服务并记录 datasource URL、schema checksum、迁移版本和核心表计数，再重新执行页面巡检。

### P1-16 非运行态 `BossERP` 销售/库存数据断层，不能代表当前网页端运行态

- 现象：同机非运行态 `BossERP` 中保留 9 张销售单和 14 条销售明细，其中多张为 `delivered` 或 `noticed`，但该库 `inv_stock=0`、`inv_stock_log=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0`。当前网页端运行态实际连接 `BossERP_stock_state_75c59ee`，该运行态库 `inv_sales_order=0`、`inv_sales_detail=0`、`inv_stock=2`、`inv_stock_log=5`、`inv_delivery_notice=0`。
- 证据：非运行态 `BossERP` 中曾复核到销售单 `SO202604280001` 有 3 张不同记录且状态分别为 `delivered/cancelled/delivered`，历史库存、出库和发货通知数据只存在 `*_clear_backup_20260607_1630` 备份表中；但运行态 `BossERP_stock_state_75c59ee` 直查显示库存流水存在 5 条，其中包含 `adjustment`、`transfer_out`、`transfer_in`，且当前销售正式表为空。运行态销售字段 `customer_name/shop_dept_id/status` 也存在，不是旧结论中的运行态销售表断层。
- 影响：旧“当前销售有出库但库存为空”的结论只适用于非运行态 `BossERP`，不能用来判断当前电脑网页端销售页面。真正风险是环境口径分裂：如果服务切到 `BossERP`，销售、库存、发货通知会出现断层；如果继续使用运行态库，则需要重新创建销售样例数据后才能验证销售出库、发货通知、库存流水和报表闭环。
- 建议：后续销售链路验收必须先打印 datasource URL 和 schema 名，再在运行态库内创建或确认销售单、发货通知、库存流水成组数据。非运行态 `BossERP` 的孤立销售单应归档为环境漂移样本，不能作为当前网页端缺陷关闭依据；若要保留该库，应做一次销售/库存/发货通知一致性清理。

### P1-17 运行态启用账号和自助注册链路缺最小可用配置，登录前后没有完整性阻断

- 现象：当前运行态 `BossERP_stock_state_75c59ee` 中 25 个有效账号均至少有 1 个角色，但 `123 / user_id=103` 没有任何 `sys_user_shop` 组织授权；非运行态 `BossERP` 中另有 `123456 / user_id=100`、`user1 / user_id=101` 无角色的历史漂移样本。登录服务只校验账号状态和密码，不校验角色、岗位、部门或业务组织配置完整性；如果未来把 `sys.account.registerUser` 打开，自助注册还会直接创建没有部门、角色、岗位、店铺/仓库授权的启用账号。
- 证据：运行态库按 `sys_user` 左连 `sys_user_role/sys_user_shop` 复核，`user_id=103 / user_name=123 / role_count=1 / scope_count=0`，其余普通测试账号多为 1 个角色 + 1 到 2 个组织范围，`admin/ry` 各有 7 个组织范围；按 `register|default|role|dept|shop|store` 匹配 `sys_config`，只存在 `sys.account.registerUser=false`，没有默认角色、默认部门、默认店铺或注册审核配置。源码 `erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java:51-99` 登录流程只检查用户名/密码、IP 黑名单、用户删除/停用和密码正确性，未检查角色或组织授权。自助注册路径为 `TokenController.register -> SysLoginService.register -> RemoteUserService.registerUserInfo -> SysUserController.register -> SysUserServiceImpl.registerUser`：认证服务只设置 `userName/nickName/pwdUpdateDate/password`，系统服务在 `sys.account.registerUser=true` 时直接 `userMapper.insertUser(user)`；`SysUserMapper.insertUser` 只有传入 `deptId` 才写 `dept_id`，`registerUser` 不会插入 `sys_user_role`、`sys_user_post` 或 `sys_user_shop`。无角色用户调用 `SysMenuMapper.selectMenuTreeByUserId` 会因 `sys_user_role` 关联为空返回空菜单，组织选择页调用授权组织树时也没有默认业务组织可选。
- 影响：这类账号可以成为“能登录但没有可选业务组织或业务入口异常”的状态，管理员如果不进入数据库或配置状态列，很难区分是账号坏了、角色没配、组织没配还是权限被清空。多账号批量创建或开启自助注册时，岗位、角色、组织三者不一致会让问题更难排查；若脚本误查非运行态 `BossERP`，还会把无角色样本误报成当前运行态账号问题。
- 建议：启用账号前增加配置完整性校验：至少要求有一个启用角色、有效部门和显式业务组织授权；登录后发现缺角色或缺组织时应进入受限错误页并提示联系管理员，而不是进入空菜单/空组织流程。自助注册若要保留，应改成“申请账号/待审核”流程，注册成功先生成停用或待激活账号，由管理员补齐角色、岗位和店铺/仓库授权后再启用；用户管理页也应把“有角色但无组织”“岗位但无角色”“角色但无组织”“自助注册待配置”作为可筛选状态。

### P1-18 非运行态 `BossERP` 缺薪资/OA 工资表，运行态表存在但数据闭环仍薄弱

- 现象：非运行态 `BossERP` 中曾看到缺少 `sys_user_salary_scheme`、`oa_salary_config`、`oa_salary_record`，导致旧结论判断薪资配置和 OA 工资会 SQL 缺表。当前网页端运行态库 `BossERP_stock_state_75c59ee` 实际已经包含这些表，但工资记录仍为空，员工绑定和角色绑定覆盖很少。
- 证据：运行态库直查 `sys_user_salary_scheme=1`、`oa_salary_config=1`、`oa_salary_record=0`、`sys_salary_scheme=2`、`sys_salary_scheme_item=17`、`sys_role_salary_scheme=4`。源码 `SysSalarySchemeMapper.xml`、`SysUserSalarySchemeMapper.xml`、`OaSalaryConfigMapper.xml`、`OaSalaryRecordMapper.xml` 仍读写这些表，因此在运行态库内不是缺表问题。
- 影响：旧“当前库 SQL 缺表”不能作为电脑网页端运行态缺陷继续使用；但薪资流程仍未形成可靠闭环：25 个有效账号中只有 1 条员工薪资绑定、OA 工资记录为 0，工资配置、员工绑定、考勤、工资计算和导出还需要用运行态数据端到端复测。若脚本误查 `BossERP`，会继续把非运行态迁移缺口误报成当前页面缺陷。
- 建议：将薪资巡检脚本固定到 `BossERP_stock_state_75c59ee`，并补做运行态页面/API 冒烟：薪资方案列表、员工绑定新增/编辑、工资参数、工资计算和 `/oa/salary/list`。非运行态 `BossERP` 若保留，应补齐同版本薪资迁移或明确标记为废弃 schema。

### P1-19 非运行态 `BossERP` 劳动合同为空，运行态合同证据链仍存在

- 现象：非运行态 `BossERP` 中 `oa_labor_contract`、`oa_labor_contract_event`、`oa_company_seal_config` 为 0，只剩 2 条内置模板；但当前网页端运行态库 `BossERP_stock_state_75c59ee` 仍有正式合同、事件和企业章配置。旧“当前合同数据被清空”的结论不适用于当前运行态。
- 证据：运行态库直查 `oa_labor_contract=9`、`oa_labor_contract_event=59`、`oa_company_seal_config=1`、`oa_labor_contract_template=2`；非运行态 `BossERP` 中对应正式合同和事件为 0。此前浏览器复测劳动合同路由不稳定、合同数据范围和签署证据问题仍可作为页面/流程问题保留，但数据清空证据必须标注为非运行态 schema。
- 影响：如果按 `BossERP` 验收，会误判劳动合同业务完全丢失；如果按运行态库验收，真正需要关注的是合同页面是否稳定渲染、合同归属是否限制到员工/门店范围、验 hash 和下载事件是否可追溯，以及企业章和模板维护权限是否完整。两套库的差异会直接影响 OA 页面结论。
- 建议：后续劳动合同复测统一使用运行态 `BossERP_stock_state_75c59ee`，并在页面上核对 9 份合同、59 条事件和企业章配置是否能被正确展示、过滤和追溯。非运行态 `BossERP` 的空合同状态作为数据漂移风险记录，不作为当前网页端合同缺陷。

### P1-20 非运行态 `BossERP` 商品窄表与源码不一致，运行态商品宽表已对齐

- 现象：非运行态 `BossERP` 的 `inv_product` 表是窄表且只有 8 行，缺少 `sku`、`size`、`unit`、`supplier_name`、多图字段等源码字段；但当前网页端运行态库 `BossERP_stock_state_75c59ee` 的 `inv_product` 已是源码需要的宽表，并有 162 条商品。
- 证据：运行态库 `inv_product` 包含 `sku`、`size`、`unit`、`sale_price_250g`、`sale_price_500g`、`supplier_name`、`supplier_phone`、`supplier_remark`、`internal_tea_name`、`product_description`、`package_image_url`、`dry_tea_image_url`、`tea_soup_image_url`、`leaf_bottom_image_url`、`extra_image_url` 等字段；直接执行 `select p.product_id,p.sku,p.product_code,p.purchase_price,p.cost_price from inv_product p limit 3` 成功返回商品。旧 `Unknown column 'p.sku'` 证据来自非运行态 `BossERP`。
- 影响：当前电脑网页端商品列表和保存不应再按“运行态 SQL 缺列”定性；真正问题变成环境/迁移口径风险。如果巡检脚本、开发工具或服务配置误连 `BossERP`，商品、采购选择、销售明细、固定资产商品选择会直接 SQL 失败；如果连接运行态库，则应继续检查商品数据完整性、分页渲染、重复商品、图片字段为空和组织范围等页面问题。
- 建议：所有商品相关 SQL 巡检必须固定 `BossERP_stock_state_75c59ee` 并输出 schema 名；非运行态 `BossERP` 若仍保留，应执行同版本商品宽表迁移或从验收脚本白名单中排除。后续页面复测继续关注运行态 162 条商品的完整性和交互问题，而不是旧的缺列误判。

### P1-21 非运行态 `BossERP` 销售/库存窄表与源码不一致，运行态销售/库存主列已对齐

- 现象：非运行态 `BossERP` 中曾看到 `inv_sales_order` 缺 `customer_name`、`inv_stock` 和 `inv_stock_log` 缺 `version/batch_no/expiry_date/serial_no/location_code/location_name`，但运行态 `BossERP_stock_state_75c59ee` 的销售和库存表已包含源码 Mapper 使用的主列。
- 证据：运行态 `desc inv_sales_order` 显示 `customer_name`、`shop_dept_id`、`status` 存在；`desc inv_stock` 显示 `warehouse_id`、`batch_no`、`expiry_date`、`serial_no`、`location_code`、`location_name`、`version` 存在；`desc inv_stock_log` 显示 `warehouse_id`、`batch_no`、`expiry_date`、`serial_no`、`location_code`、`location_name` 存在。运行态样例查询 `select s.stock_id,s.warehouse_id,s.batch_no,s.cost_price,s.version from inv_stock s limit 3` 成功返回 2 条库存，`select l.log_id,l.warehouse_id,l.batch_no,l.cost_price from inv_stock_log l limit 3` 成功返回 3 条流水。
- 影响：旧销售/库存 SQL 缺列结论只适用于非运行态 `BossERP`，不适用于当前电脑网页端运行态。运行态当前销售表为空，说明还不能凭存量数据验证销售保存、发货通知和销售退货完整流程；但库存列表、库存流水和调拨库存变动至少具备可查询数据和对应列。
- 建议：后续销售/库存验收以运行态库为准，先创建或确认一组销售单、发货通知、库存流水成组数据，再检查页面和接口闭环。非运行态 `BossERP` 的窄表应单独做迁移一致性处理，并禁止巡检脚本把它当作当前网页端 schema。

### P1-22 非运行态 `BossERP` 缺调拨审批/发货批次表，运行态调拨主流程表存在

- 现象：非运行态 `BossERP` 中只剩 `inv_transfer_order`、`inv_transfer_detail`、`inv_transfer_receipt`，缺少调拨审批、发货批次和状态流水表；但当前网页端运行态 `BossERP_stock_state_75c59ee` 中这些表实际存在，并已有调拨、发货和状态流水记录。
- 证据：运行态库直查 `inv_transfer_order=2`、`inv_transfer_detail=2`、`inv_transfer_approval_rule=1`、`inv_transfer_shipment=2`、`inv_transfer_status_log=6`。运行态表清单包含 `inv_transfer_approval_rule/node/instance/task`、`inv_transfer_shipment`、`inv_transfer_shipment_detail`、`inv_transfer_status_log`；样例数据包含 `TS202606180001 received`、`TS202606180002 pending_receive`，状态流水包含 `submit`、`approve`、`deliver`、`receive`。
- 影响：旧“当前调拨主流程会 SQL 缺表失败”不适用于当前运行态。仍需保留的问题是运行态调拨流程要继续用页面复测验证：审批规则、仓库发货、门店收货、部分发货、状态轨迹和按钮权限是否合理；非运行态 `BossERP` 一旦被误连，会让调拨流程直接退化为缺表失败。
- 建议：调拨审计继续以运行态库为准，覆盖 `inv_transfer_order/detail`、审批规则/任务、发货批次、收货和状态流水的页面闭环。部署和巡检前增加 schema preflight：当运行服务连接的 schema 缺少调拨审批、发货批次或状态流水表时，禁止启用调拨提交、出库、收货和审批配置入口。

### P1-23 运行态 system 服务与直查 MySQL 账号数据不一致，审计口径分裂

- 现象：直连 MySQL `BossERP` 和运行态 `9201 /user/info/{username}` 返回的用户、部门、角色和密码口径不同。当前 auth 登录以运行态 system 服务为准，因此按 MySQL 当前库确认的普通账号密码、角色和组织授权，不能直接代表电脑端真实运行态。
- 证据：MySQL 当前 `admin` 为 `nick_name=刘星宇、dept_id=103 北京1、role=admin`，`ry` 为 `nick_name=123、dept_id=105 测试部门、role=cys`，且离线 BCrypt 显示 MySQL 中 `ry` 密码匹配 `123456`。但 `GET http://127.0.0.1:9201/user/info/admin` 加 `from-source: inner` 返回运行态 `admin` 为 `nickName=管理员、dept=103 研发部门`，密码哈希也不同；`GET /user/info/ry` 返回运行态 `ry` 为 `dept_id=100 金英灵韵、role=zjl 总经理`，密码哈希不匹配 `123456`。因此 `POST http://127.0.0.1:9200/login` 中 `admin/admin123` 当前成功，而 `ry/123456` 返回“用户不存在/密码错误”。`sys_logininfor` 直查 MySQL 最近记录仍停留在 2026-06-04，也没有记录当前 auth 登录成功。
- 影响：数据库审计和电脑端运行态页面会引用两套不同账号底座。多账号多店铺检查中，用户数量、角色权限、组织授权、密码、登录日志、菜单下发和页面按钮表现都可能相互矛盾；如果只看 MySQL，会误判 `ry` 是茶艺师且可用 `123456` 登录；如果只看运行态，会忽略当前直查库 `sys_user_shop=0`、角色绑定和组织树已经漂移。
- 建议：先统一运行服务连接的数据源和审计 SQL 数据源，并把当前网关、auth、system 的实际 datasource URL、schema 名、构建版本和迁移版本写入巡检前置快照。每轮电脑端复测前必须用同一账号同时核对 MySQL、`/system/user/getInfo`、`/system/dept/shop-tree` 和登录日志，确认四者口径一致后再关闭账号/组织授权类问题。

### P1-24 库存盘点详情接口向无成本权限角色返回成本价

- 现象：库存盘点详情弹窗没有显示成本列，但详情接口会把盘点明细 `costPrice` 返回给前端；拥有 `inv:stockCheck:query` 的门店角色可通过接口响应或浏览器开发者工具看到盘点商品成本。
- 证据：2026-06-23 使用 admin 登录、请求头 `Dept-NumId: 108` 只读调用 `GET /inventory/stockCheck/2`，响应 `details[0].costPrice=84.0000`，同一响应还返回 `checkNo=SC202606140001`、商品“清香铁观音”和盘亏数量。数据库 `inv_stock_check_detail` 当前该明细 `cost_price=84.0000`；`InvStockCheckDetailMapper.xml:20-23` 查询明细时包含 `cost_price`，`InvStockCheckServiceImpl.getCheckDetail()` 直接把 `selectInvStockCheckDetailByCheckId` 结果挂到详情返回。前端 `stockCheck/index.vue` 的详情表只展示商品、编码、账面/实盘/差异、单位和规格，没有按权限处理 `costPrice`。角色授权复核显示店长、驻店经理、运营经理、总经理均持有 `inv:stockCheck:query`，但没有 `inv:cost:view`。
- 影响：成本字段虽然没有画到页面表格里，但已经进入浏览器响应数据；门店或运营角色只要能打开盘点详情，就能拿到库存成本价。盘点单通常覆盖门店真实库存差异，多店铺场景下这会绕过成本权限边界，和已发现的库存流水/报表成本泄露形成同类风险。
- 建议：盘点详情接口按权限脱敏，不具备 `inv:cost:view` 时不要返回 `costPrice` 字段，或返回空值；后端 DTO 应区分“内部确认盘点所需成本”和“前端展示详情”。同时补回归用例：店长账号请求盘点详情时响应中不包含成本字段，管理员或授权成本角色才可见。

### P1-25 调拨详情接口向无成本权限角色返回发货批次成本价

- 现象：调拨详情弹窗的发货批次展开表只展示商品、本批发货和本批收货，但详情接口会把每个批次明细的 `costPrice` 返回给前端；拥有调拨详情权限的门店/运营角色可以在浏览器响应中看到仓库发货成本价。
- 证据：2026-06-23 使用 admin 登录、请求头 `Dept-NumId: 108` 只读调用 `GET /inventory/transfer/14`，响应中 `shipments[0].details[0].costPrice=84.00`，对应调拨单 `TF202606180001`、发货批次 `TS202606180001`、商品“清香铁观音”。数据库 `inv_transfer_shipment_detail` 当前两条明细均为 `cost_price=84.00`。`InvTransferShipmentDetailMapper.xml:18-21` 查询批次明细时包含 `cost_price`，`InvTransferServiceImpl.getTransferDetail()` 会把 `transferShipmentDetailMapper.selectByShipmentId(...)` 结果挂到调拨详情返回；前端 `transfer/index.vue:266-277` 只渲染商品、发货和收货数量，没有成本列或权限脱敏。角色授权复核显示店长、驻店经理、运营经理、总经理拥有 `inv:transfer:query` 或 `inv:transfer:records:query`，但没有 `inv:cost:view`。
- 影响：调拨批次承载仓库到门店的真实成本流转，成本价进入前端响应后，门店/运营角色即使界面看不到成本列，也能通过网络数据获取敏感成本。该风险覆盖处理中调拨和调拨记录两个入口，和库存流水、报表、库存盘点详情的成本泄露共同说明成本权限只做了局部展示控制，接口层没有统一脱敏。
- 建议：调拨详情返回 DTO 时按 `inv:cost:view` 过滤 `shipments.details.costPrice`，无权限角色响应体不应包含成本字段；如果收货入库需要成本计算，应在服务内部使用，不要把内部成本字段直接序列化给前端。补充回归用例：店长/运营经理请求调拨详情和调拨记录详情时响应不含 `costPrice`，授权成本角色才可见。

### P1-26 商品列表和详情接口向无成本权限店长返回采购价和成本价

- 现象：商品列表和商品详情页面会用 `inv:cost:view` 在前端隐藏“采购价 / 成本价”，但后端列表和详情接口没有按成本权限脱敏，店长账号仍能从接口响应中读取 `purchasePrice` 和 `costPrice`。
- 证据：2026-06-23 使用店长账号 `qa_audit_62201 / Audit123` 登录，带 `Dept-NumId: 103` 只读调用 `GET /inventory/product/list?pageNum=1&pageSize=1&status=0`，响应首行“清香铁观音”包含 `purchasePrice=26.50`、`costPrice=84.00`；同一账号调用 `GET /inventory/product/10` 详情也返回 `purchasePrice=26.50`、`costPrice=84.00`。该账号角色为店长，不具备 `inv:cost:view`。前端 `product/index.vue:555-557` 的 `canViewCostFields` 只控制页面列和详情字段展示；`InvProductController.list()` 与 `getInfo()` 直接返回 `productService` 的完整 `InvProduct`，只有导出接口在 `InvProductController.export()` 中按 `hasCostViewPermission()` 隐藏 `purchasePrice/costPrice`。
- 影响：商品采购价和成本价是所有销售、调拨、库存、报表成本口径的源头。当前普通店长虽然看不到页面上的成本列，但打开浏览器网络响应即可拿到 162 个商品的采购价和成本价；这会绕过商品页的前端权限设计，也会让“成本字段权限码不一致”问题变成实际数据泄露。
- 建议：商品列表和详情接口返回 DTO 时按成本权限剔除或置空 `purchasePrice`、`costPrice`；前端隐藏只作为展示优化，不能替代后端字段级权限。补充回归用例：无 `inv:cost:view` 的店长请求商品列表/详情时响应中不包含采购价/成本价，管理员或授权成本角色才可见。

### P1-27 供应商详情供货商品向无成本权限角色展示采购价并返回成本价

- 现象：供应商详情弹窗的“供货商品”表直接展示“采购价”，且供货商品接口返回完整商品对象，其中包含 `purchasePrice` 和 `costPrice`；拥有供应商详情权限但无成本权限的角色仍可看到供应商供货价格和接口成本价。
- 证据：2026-06-23 使用 admin token、`Dept-NumId: 104` 只读调用 `GET /inventory/supplier/6/products`，响应商品“清香铁观音”包含 `purchasePrice=26.50`、`costPrice=84.00`；对应供应商为“泉州安溪今世缘茶业有限公司”。前端 `supplier/index.vue:100-110` 的供货商品表固定展示“采购价”列，没有 `inv:cost:view` 判断；`InvSupplierController.products()` 只校验 `inv:supplier:query`，`InvSupplierServiceImpl.selectSupplierProductList()` 直接返回 `productMapper.selectInvProductListBySupplier(...)` 的完整 `InvProduct`。角色授权复核显示总经理 `zjl` 持有 `inv:supplier:query`，但没有 `inv:cost:view`。
- 影响：供应商详情本应服务于主数据维护和供货范围确认，但当前把采购价作为普通详情字段暴露，并且在网络响应中带出成本价。总经理、仓库或后续自定义供应商查询角色只要能打开供应商详情，就能获取供应商供货商品的采购成本数据；这和商品列表/详情、报表、库存盘点、调拨详情的成本权限边界不一致。
- 建议：供应商供货商品接口返回专用 DTO，并按 `inv:cost:view` 控制 `purchasePrice`、`costPrice` 等价格字段；前端“采购价”列也应按同一权限隐藏或置空。若业务确实要求供应商维护人员可见采购价，应定义独立权限点并在角色授权树中明确展示。

### P1-28 采购商品选择和采购单价字段未接入成本权限

- 现象：采购管理的“选择商品”弹窗固定展示“采购参考价”，并把商品接口返回的 `purchasePrice` 直接写入采购明细 `unitPrice`；采购详情、收货弹窗和导出字段也按采购权限展示采购总金额、进价和明细金额，没有和 `inv:cost:view` 统一。
- 证据：2026-06-23 只读复核源码和运行库：`purchase/index.vue:218` 固定显示 `purchasePrice`，`:440` 与 `:464-465` 用 `product.purchasePrice` 回填 `unitPrice/amount`；采购详情表 `:105-110` 固定展示“进价/金额”。`InvProductController.list()` 只要求 `inv:product:list`，此前 P1-26 已用店长账号验证该接口会返回“清香铁观音”的 `purchasePrice=26.50`、`costPrice=84.00`；当前 `inv_product` 仍有 162 个启用商品，样例商品 10/11/12/13/14 均有采购价和成本价。角色授权复核显示总经理 `zjl` 拥有 `inv:product:list/query`、`inv:purchase:add/list/query/submit/receive/qc/export`，账号 `ry` 的组织授权含 `104` 仓库，但该角色没有 `inv:cost:view`。当前 `inv_purchase_order/inv_purchase_detail` 为 0，所以历史采购单详情泄露没有存量单据可运行复现。
- 影响：采购页本身需要处理进价，但当前权限模型把“能创建/查看采购单”和“能查看成本/采购价”混在一起。无成本权限但有采购新增或查询的角色可以通过采购商品选择弹窗看到商品采购参考价；一旦存在采购单，还会在列表、详情、收货和导出中看到采购总金额、进价和明细金额。这会继续扩大 P1-26 的商品接口泄露面，也让 `inv:cost:view` 的成本边界在采购模块失效。
- 建议：明确采购岗位是否天然拥有采购价权限；如果不是，应在采购商品选择、采购列表、详情、收货弹窗和导出中统一接入 `inv:cost:view` 或独立的采购价权限，后端返回 DTO 时剔除 `purchasePrice/unitPrice/amount/totalAmount` 等敏感价格字段。若业务要求采购制单人必须看到进价，应在角色授权树中显式给出“采购价可见”权限，而不是靠 `inv:purchase:add/list` 隐式放行。

### P1-29 库存列表、详情和汇总接口向无成本权限店长返回成本价和库存总成本

- 现象：库存页面前端没有直接展示成本列，但库存列表、库存详情和库存汇总接口都会把成本字段返回给前端；无 `inv:cost:view` 的店长账号可以通过接口响应读取移动加权成本价和库存总成本。
- 证据：2026-06-23 使用店长账号 `qa144822m / Audit123` 登录，带 `Dept-NumId: 108` 只读请求 `GET /inventory/stock/list?pageNum=1&pageSize=1`，响应库存“清香铁观音”包含 `costPrice=84.00`、`totalCost=1008.00`；同账号请求 `GET /inventory/stock/3` 详情也返回 `costPrice=84.00`、`totalCost=1008.00`；请求 `GET /inventory/stock/summary` 返回 `totalCost=1008.00`。角色授权复核显示店长、驻店经理、运营经理、总经理均持有 `inv:stock:list/query/export`，但没有 `inv:cost:view`。源码层面 `InvStockMapper.xml` 的库存列表/详情 SQL select `s.cost_price, s.total_cost`，汇总 SQL select `sum(s.total_cost) as total_cost`；`InvStockController.list/getInfo/summary` 只校验库存权限，没有成本权限分支。
- 影响：库存页虽然不画成本列，但网络响应已经暴露门店库存成本；库存汇总接口还会把当前组织总成本直接返回。普通店长或运营角色能通过浏览器开发者工具获取门店库存成本，和库存变动日志、库存导出、报表汇总、盘点详情、调拨详情等成本泄露形成同一类接口级权限缺口。
- 建议：库存列表、库存详情和库存汇总返回 DTO 时按 `inv:cost:view` 脱敏 `costPrice`、`totalCost`、汇总 `totalCost` 等字段；库存导出和库存变动日志也应复用同一字段级权限。补充回归用例：无成本权限店长请求库存列表/详情/汇总时响应体不包含成本字段，授权成本角色才可见。

### P1-30 商品新增、编辑和导入后端未校验成本权限，可构造请求写入采购价和成本价

- 现象：商品页前端会用 `inv:cost:view` 隐藏“采购参考价 / 参考成本价”，保存前也会从普通用户 payload 中删除 `purchasePrice/costPrice`；但后端新增、编辑和导入接口只校验 `inv:product:add/edit/import`，没有再次校验成本权限。无成本权限但有商品维护权限的角色可以绕过前端，构造请求或导入模板写入采购价和成本价。
- 证据：2026-06-24 只读复核运行态权限，`总经理`角色持有 `inv:product:add`、`inv:product:edit`、`inv:product:import`，但没有 `inv:cost:view`；有效账号 `ry / ERP` 绑定总经理角色，`sys_user_shop` 授权范围为 `103,104,105,106,107,108,109`。前端 `product/index.vue:339-352` 用 `canViewCostFields` 隐藏成本输入，`:943-948` 的 `stripHiddenCostFields` 删除普通 payload 中的 `purchasePrice/costPrice`；后端 `InvProductController.add/edit/importData` 分别只要求商品新增、编辑、导入权限。`InvProductMapper.xml` 的 `insertInvProduct` 固定写入 `purchase_price/cost_price`，`updateInvProduct` 在字段非空时更新 `purchase_price/cost_price`；`InvProduct.java` 还把两个字段标注为 `@Excel`，导入模板会承载这些列。
- 影响：当前成本权限只控制了“页面上看不看、商品导出藏不藏”，没有控制“接口能不能写”。总经理这类无成本权限但可维护商品的账号，仍可能通过浏览器请求、脚本或导入 Excel 改写商品采购价/成本价，进而影响采购参考价、库存移动加权成本、报表成本和毛利等后续业务口径；这比只读泄露更严重，因为会污染成本主数据。
- 建议：商品新增、编辑和导入服务层必须做字段级写权限校验：无成本权限时忽略或拒绝 `purchasePrice/costPrice`，导入模板也应按权限隐藏或拒绝成本列。若总经理业务上确实可以维护成本，应在权限树中显式授予统一后的成本写权限，并补回归用例：无成本权限角色构造带成本字段的新增/编辑/导入请求时不能改变成本字段。

### P1-31 库存调整允许无成本权限角色改变库存总成本且确认过程不展示成本影响

- 现象：库存调整弹窗只展示商品、组织、调整方向、调整前后数量和原因，不展示本次调整对成本价、库存总成本或成本流水的影响；但后端手工调整会按库存/商品成本价更新 `inv_stock.cost_price/total_cost` 并把成本价写入 `inv_stock_log`。当前存在无 `inv:cost:view` 但有 `inv:stock:adjust` 的业务角色。
- 证据：2026-06-24 只读复核运行态权限，`驻店经理`、`运营经理`、`总经理`均持有 `inv:stock:adjust/list/query/export`，但没有 `inv:cost:view`，有效账号包括 `ll1`、`12345`、`liu123456`、`ii`、`ry`。当前库存样例“清香铁观音”在门店 `108` 库存 12、`cost_price=84.00`、`total_cost=1008.00`，仓库 `104` 库存 192、`total_cost=16128.00`。前端 `stock/index.vue` 的确认文案只拼接数量变化；提交 payload 只含商品、组织、批次/效期/库位、`adjustQuantity` 和原因。后端 `InvStockServiceImpl.adjustStock()` 对新增库存用 `resolveManualAdjustmentUnitCost()` 取商品成本价，对已有库存用现有库存成本价；正数调整调用 `addInvStockWithCost` 重算 `cost_price` 并增加 `total_cost`，负数调整调用 `deductInvStockWithCost` 扣减 `total_cost`，随后库存日志写入 `log.setCostPrice(stock.getCostPrice())`。
- 影响：无成本权限角色虽然看不到成本字段，却可以通过库存调整改变库存成本总额、报表库存成本、毛利口径和库存流水成本记录；确认弹窗没有展示成本影响，审批或复核人员也无法在电脑端判断“这次数量调整对应多少成本变化”。这会和 P1-29、P1-30 的成本字段读写边界一起造成成本主数据可见性和可写性不一致。
- 建议：库存调整需要拆分“数量调整权限”和“成本影响权限”。若无成本权限角色可调整数量，确认弹窗至少应隐藏成本但要求二次审批/原因分类，并由后端记录可追溯的成本影响；若业务要求调整人知晓并承担成本影响，应显式授予成本写/调整权限。补充回归用例：无成本权限角色执行库存调整时后端按策略拒绝、审批或记录成本影响，且前端确认文案与角色权限一致。

### P1-32 销售退货入库没有原出库成本快照，可能按当前库存成本或零成本回写库存

- 现象：销售出库时会按当时库存成本扣减 `inv_stock.total_cost`，但原销售明细、出库记录和销售退货明细都没有保存“本次出库成本价/成本金额”。销售退货确认入库时不读取原出库成本，而是读取当前门店库存成本；如果该商品当前库存不存在或成本为空，退货入库成本会按 `0` 计算。
- 证据：2026-06-24 只读复核源码和 schema：`InvSalesServiceImpl.deliverSales()` 与 `InvDeliveryNoticeServiceImpl.deliverNotice()` 出库时读取 `stock.getCostPrice()` 计算 `deductCost`，调用 `deductInvStockWithCost` 并只在 `inv_stock_log.cost_price` 写当时成本价；`InvOutboundRecord` 和 `inv_outbound_record` 只有销售单、商品、组织、数量、创建人和时间，没有成本字段。运行态 `inv_sales_detail`、`inv_sales_return_detail` 只有 `unit_price/amount` 等销售价字段，没有成本价或成本金额列。`InvSalesReturnServiceImpl.confirmReturn()` 调用 `resolveReturnStockCost(stock)`，该方法在当前库存为空或成本为空时返回 `BigDecimal.ZERO`，随后用这个成本调用 `addInvStockWithCost` 回写库存。当前正式 `inv_sales_order/inv_sales_detail/inv_sales_return/inv_sales_return_detail/inv_outbound_record` 均为 0，无法用存量单据运行复现；该结论来自当前代码与表结构。
- 影响：退货应冲回原销售出库的成本，否则毛利、库存总成本和门店库存估值会受当前库存成本波动影响。若商品已售空后发生退货，当前实现会按零成本入库，直接低估库存成本；若销售后又采购或调拨导致当前移动加权成本变化，退货会按新成本而不是原销售成本入库。无成本权限但有销售退货确认权限的店长、驻店经理、运营经理、总经理都能触发这个成本回写。
- 建议：销售出库时必须保存 COGS 快照，至少在销售明细、出库记录或成本事件中记录出库成本价和成本金额；销售退货确认应按原出库成本冲回库存，而不是按当前库存成本或零成本。补充回归用例：商品售空后退货、销售后成本变化再退货、部分出库/部分退货等场景，库存 `total_cost` 应按原出库成本恢复。

### P1-33 采购退货列表、详情和导出未接入成本权限，向无成本权限角色暴露进价和退货金额

- 现象：采购退货页面的列表直接展示“退货金额”，新增/编辑和详情明细展示“单价/金额”，导出也包含“退货金额”；这些字段来自原采购明细的进价和金额，但没有按 `inv:cost:view` 或采购价权限控制。
- 证据：2026-06-24 只读复核运行态权限，`总经理`角色持有 `inv:purchaseReturn:list/query/add/submit/confirm/export`，但没有 `inv:cost:view`。前端 `purchaseReturn/index.vue` 列表列 `totalAmount`，表单明细列 `unitPrice/amount`，详情弹窗展示 `totalAmount`、`unitPrice`、`amount`；后端 `InvPurchaseReturnController` 的 list/detail/export 只校验采购退货权限，导出用 `ExcelUtil<InvPurchaseReturn>`；`InvPurchaseReturn.java` 对 `totalAmount` 标注 `@Excel(name = "退货金额")`。`InvPurchaseReturnServiceImpl.normalizeReturnDetails()` 从原采购明细读取 `unitPrice` 并计算 `amount`、`totalAmount`。当前正式 `inv_purchase_order/inv_purchase_detail/inv_purchase_return/inv_purchase_return_detail` 为 0，不能用存量单据运行复现，该结论来自代码、权限和表结构。
- 影响：采购退货本质上反向暴露采购进价。无成本权限的总经理等角色只要能查看或导出采购退货，就能看到供应商退货金额、明细进价和退货总额；这会绕过商品/采购页成本权限治理，并让采购退货成为获取采购成本的侧路。
- 建议：采购退货列表、详情、明细和导出按统一成本权限或采购价权限脱敏 `unitPrice/amount/totalAmount`；若采购退货岗位必须看到价格，应在权限树中显式授予采购价可见权限。补充回归：无成本权限角色查看/导出采购退货时不返回价格字段，授权成本角色才可见。

### P1-34 已签署劳动合同仍可由运营经理直接作废，缺少作废原因和审批保护

- 现象：劳动合同列表对所有非 `voided` 状态显示“作废”；后端作废接口只校验 `oa:laborContract:void` 和组织范围，服务层只跳过已作废状态，不阻止 `signed` 合同，也不要求作废原因、附件、二次确认或审批。
- 证据：2026-06-24 只读复核运行态权限和源码，超级管理员与运营经理角色持有 `oa:laborContract:add/list/query/send/template:add/template:list/void`，运营经理有效账号为 `12345/ii/liu123456`。当前 `oa_labor_contract=9`，其中 `signed=1`、`voided=8`，`oa_labor_contract_event` 分布为 `create=9`、`send=9`、`sign=5`、`void=8`、`download=28`；`oa_labor_contract` 表与作废相关字段只有 `voided_time`。前端 `erp-ui/src/views/oa/laborContract/index.vue:58` 的“作废”按钮条件是状态不等于 `voided`；`OaLaborContractController.voidContract` 只要求 `oa:laborContract:void`；`OaLaborContractServiceImpl.voidContract` 在 `assertAndGetScopedContract` 后直接把状态更新为 `voided` 并记录通用“作废劳动合同”事件。本轮未调用作废接口，未改变合同数据。
- 影响：已签署劳动合同是员工和公司双方的正式凭证，运营经理或管理员可在页面上直接让有效合同进入作废状态，签署文件、下载记录和事件链仍存在但业务状态被改为无效。缺少原因、审批和签署后保护会削弱劳动合同证据链，也无法区分草稿取消、待签撤回、已签解除和异常作废。
- 建议：按合同状态拆分动作：草稿可删除或取消，待签可撤回，已签合同必须走“解除/终止/作废审批”专用流程，并要求原因、附件、审批人和不可篡改事件；服务层应拒绝普通 `void` 直接处理 `signed` 状态，前端对已签合同隐藏“作废”或改为受控的解除流程。补充回归：运营经理对 `signed` 合同直接调用作废接口时应被拒绝，审批通过的解除流程应完整记录原因和证据。

### P2-01 门店授权与可选仓库关系语义不清

- 现象：`qa144822s` 在 `sys_user_shop` 中只授权 `103`，但 API `/system/dept/warehouse-list` 返回 `104` 仓库；调拨发起弹窗可选该仓库。
- 证据：登录后店铺选择页只出现 `研发部门`；调拨页“发起要货”目标仓库下拉出现 `仓库`。补充源码复核：`SysDeptController.warehouseList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:66-71` 只要求登录后调用 `deptService.selectWarehouseList(purpose, scopeDeptId)`；`SysDeptServiceImpl.selectWarehouseList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:132-158` 先取 `deptMapper.selectWarehouseList()`，仅当 `purpose` 为 `deliverySource/replenishmentSource/currentWarehouse` 时才按范围或 `canSelectWarehouse` 过滤，`purpose` 为空则直接返回全量仓库。`WarehouseSelect` 在 `erp-ui/src/views/inventory/components/WarehouseSelect.vue:110-119` 只有传入 `purpose` 才带参数；采购收货弹窗 `erp-ui/src/views/inventory/purchase/index.vue:236-244` 未传 `purpose`，只用 `shopDeptId` 在前端过滤。移动端实体服务 `erp-ui/src/views/mobile/feature/mobileEntityService.js:108-116` 在授权组织树为空时也会无参数调用 `listWarehouseDept()` 作为仓库兜底。当前运行库只有 1 个启用仓库 `104 / 仓库`，但已有多个门店账号没有仓库授权。
- 影响：如果 `sys_user_shop` 被理解为用户可操作组织范围，则当前门店账号越过授权看到了仓库；如果这是供货关系，则模型和页面名称没有解释清楚。
- 建议：区分“用户可登录/管理的组织”和“门店可要货的供货仓库”。后者建议建业务关系表或在接口/页面命名为“可要货仓库”，不要复用授权语义。

### P2-02 OA 首页提示仍指向隐藏/不可用的采购申请

- 现象：门店账号进入 `/oa/index`，页面提示“请从左侧菜单进入：采购申请 / 我的待办 / 我的已办”，但门店实际菜单没有采购申请。
- 证据：`erp-ui/src/views/oa/index.vue:5-6` 写死采购申请入口文案；当前 `sys_menu` 中“采购申请”处于隐藏/停用状态；2026-06-23 直连 `/oa/purchase` 进入 `/404`。
- 影响：用户按页面提示找不到入口，容易误判为权限缺失或页面异常。
- 建议：OA 首页根据当前路由权限动态展示可进入功能；采购申请停用时不应出现在提示文案中。

### P3-227 OA 首页仍是采购审批静态说明，未汇总已交付 OA 模块和运行态数据

- 现象：`/oa/index` 只是静态卡片，固定写“当前已接入采购审批流程”，并让用户去“采购申请 / 我的待办 / 我的已办”。它没有读取任何 OA 运行数据，也没有展示当前已经有菜单和数据的考勤、工资、劳动合同、固定资产入口或状态。
- 证据：`erp-ui/src/views/oa/index.vue:1-15` 只有两个 `<p>` 静态文案和 `name: "OaIndex"`，没有 `api` import、`created/mounted` 请求、按钮或快捷入口。运行态 `sys_menu` 中 OA 父菜单下除 `OA首页` 外还有 `我的待办/我的已办/我的考勤/工资管理/劳动合同/固定资产管理`，采购申请当前 `visible=1/status=1` 停用隐藏；角色授权上 `admin/dz/yyjl/zjl` 均有 `oa:index:view`，但工资、合同、固定资产等模块授权范围各不相同。运行态数据有 `oa_attendance_record=1`、`oa_salary_config=1`、`sys_user_salary_scheme=1`、`oa_labor_contract=9`、`oa_labor_contract_event=59`、`oa_labor_contract_template=2`、`oa_company_seal_config=1`，而 `oa_purchase=0`、`oa_purchase_comment=0`，固定资产四张表当前为 0 行。现有 P2-02/P2-92 已记录采购入口不可用和采购审批半上线，本条聚焦 OA 首页没有把其他 OA 模块和数据状态带出来。
- 影响：用户进入 OA 首页会被引导到当前最薄弱的采购审批链路，反而看不到已有合同、考勤、工资和固定资产能力；多账号多店铺巡检时，这个首页也不能帮助判断“当前账号在当前组织下有哪些 OA 任务、是否缺工资参数、是否有合同待处理、是否有固定资产待确认”。数据库与前端展示没有形成工作台闭环。
- 建议：把 OA 首页改成真实 OA 工作台：按当前账号、角色和 `Dept-NumId` 汇总待办/已办、考勤今日状态、工资配置/工资记录、劳动合同待处理、固定资产待确认和公告；隐藏或降级停用的采购申请入口，或明确标注“采购申请未启用”。所有卡片入口按真实权限和菜单状态过滤，并给空数据展示可操作引导。

### P2-03 OA 待办/已办列表展示店铺 ID 而非店铺名称

- 现象：`/oa/todo`、`/oa/done` 表格列名为“店铺ID”，直接展示 `shopDeptId`。
- 证据：`erp-ui/src/views/oa/todo/index.vue:74`、`erp-ui/src/views/oa/done/index.vue:88` 使用 `label="店铺ID"` 和 `prop="shopDeptId"`；2026-06-23 浏览器补测两个页面表头均显示“店铺ID”。
- 影响：多店铺审批场景下，审批人无法快速判断申请来自哪个门店，只能记 ID。
- 建议：后端返回店铺名称快照，或前端基于组织字典映射为“店铺名称（ID）”。

### P2-04 销售单客户字段与客户主数据没有形成闭环

- 现象：销售单“新建销售单”弹窗的客户字段是自由文本输入，当前 `inv_customer` 表为 0 条。
- 证据：`erp-ui/src/views/inventory/sales/index.vue:114-115` 使用普通 `el-input` 绑定 `customerName`。
- 影响：销售数据会产生难以归并的客户名称，客户管理页面和销售流程无法稳定关联。
- 建议：客户字段改为客户选择器，并支持“新建客户后回填”；没有客户主数据时给出空态引导。

### P2-95 销售制单允许选择无库存商品，库存不足延后到发货才失败

- 现象：当前运行态启用商品 162 个，但只有 1 个商品存在可用库存；销售单商品选择器仍按启用商品列表展示，保存草稿、保存并提交和生成发货通知阶段都不提示库存或可发仓库，库存不足直到发货执行时才由后端拦截。
- 证据：2026-06-23 运行态库 `BossERP_stock_state_75c59ee` 统计 `inv_product.status=0` 为 162 个，`inv_stock.available_quantity>0` 的商品只有 1 个；`erp-ui/src/views/inventory/components/ProductSelect.vue:66-84` 默认只按 `status:"0"` 查询商品并展示名称/编码；`erp-ui/src/views/inventory/sales/index.vue:124-154` 只校验选择商品、数量和价格；`InvSalesServiceImpl.saveDraft/submitSales` 只校验商品存在、启用、数量和售价；`InvDeliveryNoticeServiceImpl.deliverNotice` 到执行发货时才检查 `availableQuantity` 并抛出库存不足。
- 影响：门店可以先提交一张看起来有效的销售单，并继续生成待处理发货通知，实际履约风险被推迟到仓库发货环节才暴露；多账号多店铺场景下，销售人员无法知道当前门店/关联仓库能否履约，仓库人员会收到无法完成的单据。
- 建议：销售制单时展示商品可用库存和可发仓库，零库存商品应禁选或显式标记；如果允许预售/缺货销售，提交时要进入“缺货待补/预售”状态，而不是普通已提交/待发货。生成发货通知前也应按可发仓库做库存预校验，避免把明显无法发货的销售单推进到仓库执行队列。

### P2-05 门店商品详情仍展示供应商电话和采购备注

- 现象：门店账号商品详情不会看到成本字段，但仍能看到“供应商电话”“采购备注”，其中备注可能包含采购侧信息。
- 证据：`erp-ui/src/views/inventory/product/index.vue:272-285` 供应商信息区无权限判断。
- 影响：如果供应商联系方式、采购备注属于采购/仓库侧信息，门店角色仍可接触不应展示的内容。
- 建议：明确门店是否需要供应商信息。若不需要，按角色或权限隐藏；若需要，建议只展示业务必要字段，采购备注单独权限化。

### P2-07 新建用户首次登录提示“初始密码”但可取消继续使用

- 现象：`qa_audit_62201` 使用管理员在新增弹窗中手工填写的 `Audit123` 首次登录后，接口 `getInfo` 返回 `isDefaultModifyPwd=true`；选择门店并点击进入系统时弹出“您的密码还是初始密码，请修改密码！”。点击“取消”后仍可进入 `/index`。
- 证据：`erp-ui/src/store/modules/user.js:17-20` 使用可取消的 `MessageBox.confirm`；`erp-ui/src/store/modules/user.js:23-26` 固定文案为“您的密码还是初始密码，请修改密码！”。
- 影响：如果安全策略要求首次登录强制改密，当前“取消后继续进入”不满足；如果只是建议改密，文案又像强制要求，并且管理员自定义的临时密码也被描述成“初始密码”。
- 建议：明确策略。强制改密时取消应留在选择页或退出登录；非强制时文案改为“建议修改临时密码”，并解释可稍后处理。

### P2-08 采购退货和销售退货在无原单时缺少明确空态

- 现象：早前 `inv_purchase_order`、`inv_sales_order` 均为 0 时，仓库账号打开“新增采购退货单”、门店账号打开“新增销售退货单”后，原采购单/原销售单下拉点击后没有可见选项，也没有“暂无可退货原单”的提示。当前采购单仍为 0，销售单已变为 9 条，销售退货原单下拉需要在当前数据下重新复测。
- 证据：采购退货弹窗 `erp-ui/src/views/inventory/purchaseReturn/index.vue:61-80` 使用远程 `el-select`；`erp-ui/src/views/inventory/purchaseReturn/index.vue:254-259` 只把查询结果过滤到 `orderOptions`。销售退货弹窗 `erp-ui/src/views/inventory/salesReturn/index.vue:61-80` 和 `erp-ui/src/views/inventory/salesReturn/index.vue:253-261` 是同类实现。本轮浏览器实测点击原单下拉后无可见空态。
- 影响：用户不知道是没有数据、筛选条件不对、网络未加载，还是权限不足；退货流程无法自解释。
- 建议：当原单列表为空时显示“暂无可退货采购单/销售单”，并在按钮或弹窗顶部说明需要先有已提交/已收货/已出库的原单。

### P3-147 密码有效期配置只产生一次性提醒，不会阻断过期账号继续使用

- 现象：系统参数提供 `sys.account.passwordValidateDays` 密码有效期，但后端登录流程不会按该参数拒绝过期密码；只有桌面端 `getInfo` 返回 `isPasswordExpired` 后弹出“您的密码已过期，请尽快修改密码！”。弹窗仍可取消，取消后继续进入原页面，且提示状态写在 session 中，本次会话不会反复拦截。
- 证据：当前运行态 `sys.account.passwordValidateDays=0`，表示暂不启用过期限制；25 个启用账号中 13 个 `pwd_update_date` 为空。源码 `SysUserController.passwordIsExpiration()` 只在 `getInfo` 中计算 `isPasswordExpired`，`SysLoginService.login()` 只校验用户名/密码、账号状态和黑名单，不检查密码有效期。前端 `store/modules/user.js` 对 `isPasswordExpired` 生成提示文案并调用可取消的 `MessageBox.confirm`；`select-shop/index.vue` 在取消后直接 `router.push(redirect)`；`passwordResetReminder.js` 会标记本次会话已提示。
- 影响：如果管理员把密码有效期从 0 改为 30/60/90 天，用户仍可用过期密码登录和操作系统，只是看到一次可取消提醒。多账号门店场景下，长期未改密账号、导入账号和历史测试账号无法被真正强制换密，系统参数名称给管理员的安全预期和实际执行不一致。
- 建议：把密码有效期策略定义清楚：若是强制策略，登录后只能进入改密页，取消应退出或停留在受限页，其他业务 API 在改密前应拒绝；若只是提醒，应把参数名和页面文案改成“提醒周期”，并在个人中心持续显示待处理状态。启用强制策略前应先提供账号影响预览，列出 `pwd_update_date` 为空或已过期用户。

### P3-148 通知公告富文本粘贴图片绕过前端上传校验，失败缺少组件内提示

- 现象：通知公告富文本编辑器支持工具栏选择图片和直接粘贴图片，但两条路径的校验不一致。工具栏选择会做图片类型和默认 5MB 大小校验；粘贴图片时直接构造 `FormData` 调 `/file/upload`，没有复用同一套校验，也没有为 HTTP 失败写组件内 `catch` 提示。
- 证据：公告编辑弹窗使用 `<editor v-model="form.noticeContent">`。`Editor` 组件默认 `fileSize=5`，`handleBeforeUpload` 在 `erp-ui/src/components/Editor/index.vue:160-178` 校验图片类型和大小；但粘贴监听 `handlePasteCapture` 在 `:197-207` 拦截剪贴板图片后直接调用 `insertImage(file)`，`insertImage` 在 `:211-216` 只 `axios.post(this.uploadUrl, formData, ...)` 并调用 `handleUploadSuccess(res.data)`，没有调用 `handleBeforeUpload`，也没有 `.catch`。后端通用上传 `FileUploadUtils` 在 `erp-modules/erp-file/src/main/java/com/erp/file/utils/FileUploadUtils.java:27-33` 的默认上限是 50MB，允许扩展名也按全局 `DEFAULT_ALLOWED_EXTENSION`，不是公告富文本专用规则。当前运行态 `sys_notice=0`，本轮未做写入型复现。
- 影响：管理员粘贴大图时可能绕过前端 5MB 预期，成功上传到公开文件域；如果文件服务因大小、格式或 `file:upload` 权限拒绝，粘贴路径没有稳定的组件内错误提示，用户只会感觉“图片没插进去”。通知公告本来是全员入口，发布人员在编辑阶段无法准确知道图片是否符合系统规则。
- 建议：富文本粘贴图片应复用与工具栏选择一致的类型、大小和权限失败处理；所有上传失败都要给出明确原因。后端也应支持按业务场景传入或配置允许类型和大小，例如公告图片只允许常见安全图片格式并使用比通用附件更小的上限；同时结合 P2-126，把富文本图片是否公开展示作为明确产品策略，而不是默认落到通用公开文件域。

### P2-09 操作日志和登录日志时间比数据库/在线用户少 8 小时

- 现象：在线用户页显示同一批登录时间为 `2026-06-22 21:04:54`，但登录日志页显示 `2026-06-22 13:04:54`；操作日志页也把数据库中的 `2026-06-22 21:09:11` 显示为 `2026-06-22 13:09:11`。
- 证据：2026-06-23 数据库 `NOW()` 为 `2026-06-23 00:05:48`、`UTC_TIMESTAMP()` 为 `2026-06-22 16:05:48`；`sys_logininfor` 最新记录为本地 21 点，`sys_oper_log` 最新记录为本地 21 点。浏览器实测 `/system/log/logininfor` 和 `/system/log/operlog` 显示为 13 点。`erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysLogininfor.java:39`、`erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysOperLog.java:78` 的 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` 未声明 `timezone`，而 `BaseEntity` 创建/更新时间使用了 `timezone = "GMT+8"`。
- 影响：审计日志时间是追责、排查、导出对账的基础，少 8 小时会直接影响管理员判断“谁在什么时候做了什么”。
- 建议：登录日志和操作日志字段统一按 `GMT+8` 或系统统一时区序列化；前端对已格式化字符串不要再二次时区解析，日志导出也要同步核对时区。

### P2-10 系统监控外链启用但当前环境不可达

- 现象：系统监控下的 Sentinel 控制台、Nacos 控制台、Admin 控制台在菜单中启用可见，但当前本地环境点击会进入不可达地址；页面没有提前展示服务未启动或仅开发环境可用的说明。
- 证据：数据库 `sys_menu` 中 `111`、`112`、`113` 分别指向 `http://localhost:8718`、`http://localhost:8848/nacos`、`http://localhost:9100/login`，`visible=0`、`status=0`；2026-06-23 使用 `curl` 检查三者 HTTP 状态均为 `000`。系统接口 Swagger 另见 P3-08。
- 影响：管理员从网页端进入系统监控时，会遇到“菜单可见但服务打不开”的断点，无法区分是权限、网络、服务未部署还是环境配置问题。
- 建议：外链地址改为环境变量配置，并在菜单或跳转前做健康状态提示；未部署的监控服务在当前环境应隐藏或显示“未启用”。

### P2-11 库存入口上下文不匹配时仍显示高风险操作按钮

- 现象：管理员当前选择 `仓库：仓库` 后直连 `/inventory/stock`，页面已提示“当前组织和库存入口不匹配，请选择门店后查看进销存库存”，但“查询 / 重置 / 库存调整 / 导出 / 变动日志”按钮仍全部可见且未禁用；点击“库存调整”后才再次弹出“请选择门店后查看进销存库存”错误提示。
- 证据：2026-06-23 浏览器实测 `/inventory/stock`，告警存在且 5 个操作按钮均为启用状态；点击“库存调整”后没有打开可见调整弹窗，而是出现错误提示。前端 `erp-ui/src/views/inventory/stock/index.vue:120-124` 的操作按钮只按权限显示，没有按 `isEntryContextValid` 禁用；`erp-ui/src/views/inventory/stock/index.vue:429-450` 已能判断入口和组织不匹配；`erp-ui/src/views/inventory/stock/index.vue:654-668` 和 `erp-ui/src/views/inventory/stock/index.vue:725-746` 会在执行时再拦截。
- 影响：用户已经被告知当前入口不正确，却仍看到高风险库存操作，点击后才报错，容易误以为页面可继续操作；导出、变动日志等按钮也没有在入口错误时给出一致的禁用状态。
- 建议：当 `!isEntryContextValid` 时禁用或隐藏库存调整、导出、变动日志等动作按钮，并提供“切换到门店/进入仓库库存页”的明确入口；后端提交接口也应继续校验库存组织类型和当前用户上下文。

### P2-12 OA 考勤日期序列化会影响“今日打卡”状态判断

- 现象：数据库 `oa_attendance_record.work_date=2026-06-14` 的记录，在 `/oa/attendance` 页面显示为 `2026-06-13T16:00:00.000Z`；前端查找今日记录时使用 `String(r.workDate).slice(0, 10) === today`。如果今天的工作日期也按 UTC ISO 返回，页面可能匹配到前一天，导致已经打过上班卡时仍显示“上班打卡”按钮，或者无法正确进入“下班签退”状态。
- 证据：2026-06-23 浏览器补测考勤列表出现 `2026-06-13T16:00:00.000Z`；`erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaAttendanceRecord.java:33-34` 的 `workDate` 只有 Excel 日期格式，没有 `@JsonFormat`；`erp-ui/src/views/oa/attendance/index.vue:167-181` 用字符串前 10 位判断今日记录。
- 影响：考勤是流程型功能，日期错一天会直接影响上班/下班按钮状态和用户是否能完成当日签退；后端虽然会阻止重复打卡，但前端流程会给出错误入口和错误预期。
- 建议：后端把 `workDate` 明确序列化为本地 `yyyy-MM-dd`，前端用统一日期格式化/比较函数判断今日记录；打卡按钮状态应以后端“今日考勤状态”字段为准，而不是从列表字符串推断。

### P2-52 考勤页面入口权限和全部列表权限共用

- 现象：“我的考勤”页面菜单本身使用 `oa:attendance:list`，而后端“全部记录”接口和前端“全部”视图也使用同一个权限；当前权限树虽新增了 `oa:attendance:my` 和 `oa:attendance:correct` 按钮，但源码没有使用这两个权限。个人考勤接口 `/attendance/my` 仅要求登录，不要求 `oa:attendance:my`。
- 证据：数据库 `3200 / 我的考勤` 为页面节点，`perms=oa:attendance:list`；`3206 / 我的考勤 / oa:attendance:my`、`3207 / 考勤修正 / oa:attendance:correct` 均为启用按钮，当前只授给超级管理员。`OaAttendanceController.myList()` 只标注 `@RequiresLogin`，`OaAttendanceController.list()` 才要求 `oa:attendance:list`；`erp-ui/src/views/oa/attendance/index.vue:156-158` 用 `oa:attendance:list` 决定是否显示“视图 我的/全部”。源码检索 `oa:attendance:my|oa:attendance:correct` 只命中本报告和菜单权限，不命中前端按钮或后端注解。
- 影响：角色管理员无法用现有权限树表达“只能看自己的考勤但不能看全部考勤”，因为个人视图对应的 `oa:attendance:my` 没有实际作用，页面入口又绑定管理列表权限。`oa:attendance:correct` 也没有对应补卡/更正按钮、页面或接口，用户看到“提交后请走考勤更正流程”的文案时，电脑端没有可走的流程。
- 建议：拆分并接入权限码：页面进入和个人列表使用 `oa:attendance:my` 或 `oa:attendance:view`，管理列表使用 `oa:attendance:list` 或 `oa:attendance:manage:list`，考勤更正使用 `oa:attendance:correct` 并补齐补卡/更正申请页面、审批和审计；导出继续单独授权，角色模板按普通员工、店长、运营分别配置。

### P2-53 考勤“全部”视图缺少员工和组织列

- 现象：考勤页提供“我的 / 全部”切换，但表格列只有日期、上下班时间、工时、迟到、早退、状态；进入全部视图后无法从页面判断每条记录属于哪个员工、哪个门店/仓库。
- 证据：浏览器复测 `/oa/attendance` 的表头为“日期 / 上班打卡 / 下班打卡 / 工时(h) / 迟到(分) / 早退(分) / 状态”；`erp-ui/src/views/oa/attendance/index.vue:68-75` 只渲染这些列。后端 mapper 已返回 `user_name`、`nick_name`、`dept_name`、`shop_dept_name`，但前端没有展示。
- 影响：管理视图即使查到了全部记录，店长或运营也无法在电脑端直接识别员工和组织，只能依赖导出或后端数据；多门店场景下尤其不利于排班、迟到早退追踪和异常核对。
- 建议：在 `viewScope=all` 时展示员工账号/姓名、所属部门、考勤组织列，并提供员工、组织、状态筛选；“我的”视图可以保持精简。

### P2-54 考勤导出不区分“我的/全部”视图

- 现象：页面提示“确认导出当前查询条件下的打卡记录？”，但导出参数只带分页和日期范围，不带当前“我的/全部”视图；后端导出接口始终调用 `selectAllRecords()`。
- 证据：`erp-ui/src/views/oa/attendance/index.vue:231-239` 的 `handleExport()` 没有传 `viewScope` 或 `userId`；`OaAttendanceController.export()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaAttendanceController.java:77-84` 固定导出 `selectAllRecords(record, ...)`。当前店长、运营经理、总经理角色均被授予 `oa:attendance:export`。
- 影响：用户停留在“我的”视图时点击导出，也可能导出当前组织范围内的全部考勤；这和按钮提示不一致，也会扩大考勤隐私数据的暴露面。
- 建议：导出应明确区分“导出我的考勤”和“导出全部考勤”，或者随 `viewScope` 使用对应查询；管理导出建议增加二次提示，说明会包含哪些组织和员工。

### P2-168 考勤缺签退记录仍按正常工作日进入列表和工资计算

- 现象：员工只完成上班打卡后，后端立即把考勤状态写成 `normal`；如果一直没有下班签退，`check_out_time/work_hours/late_minutes/early_minutes` 保持空值，但列表状态列仍显示“正常”，导出也会按“正常”输出。工资计算读取考勤记录时又把所有非 `leave` 状态都计为工作日，缺签退的正常记录会减少缺勤天数。
- 证据：`OaAttendanceServiceImpl.checkIn()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java:49-59` 新建记录时设置 `status="normal"`，但不设置签退时间、工时、迟到或早退；Mapper `OaAttendanceRecordMapper.xml:46-57` 会把这些空值和 `normal` 一起写入。电脑端表格 `erp-ui/src/views/oa/attendance/index.vue:68-84` 直接展示 `checkOutTime/workHours/lateMinutes/earlyMinutes/status`，其中 `status === 'normal'` 渲染为绿色“正常”，没有“未签退/缺卡”状态。工资计算 `OaSalaryServiceImpl.calculateSalary()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java:148-173` 遍历考勤时只把 `status=leave` 计请假，其余都 `workDays++`，`workHours=null` 只按 0 小时处理，缺勤天数再用 `weekdays - workDays - leaveDays` 计算。运行态 MySQL 当前 `oa_attendance_record` 只有 1 条记录，`record_id=1 / user_name=admin / work_date=2026-06-14 / check_out_time=NULL / work_hours=NULL / status=normal`，统计为 `total=1/no_checkout=1/normal_no_checkout=1/normal_zero_or_null_hours=1`。
- 影响：考勤列表给管理员的结论是“正常”，但实际只是未签退的半条记录；工资计算也没有把缺签退作为缺卡、异常或待更正处理。多店铺考勤和工资核算中，员工可能因为只打上班卡就减少缺勤天数，店长/运营导出后也难以发现“正常”记录缺少签退和工时。已有 P2-12/P3-155 记录今日按钮状态，本条专门记录状态机和工资计算口径把缺签退当正常工作日。
- 建议：考勤状态机增加 `pending_checkout/missing_checkout/abnormal` 等明确状态；上班打卡后列表展示“待签退”，跨日未签退应由定时任务或工资计算前置校验转为缺卡/异常，并要求更正审批。工资计算必须排除未完成签退的记录，或按配置计缺勤/异常扣款；导出和列表同时展示缺签退风险，不能把空签退的 `normal` 记录当正常工作日。

### P2-55 报表中心“业务日期”不作用于库存类指标

- 现象：报表中心筛选项叫“业务日期”，但库存品项、当前库存、可用库存、库存成本和低库存预警都按当前 `inv_stock` 统计，不受日期范围影响；日期只过滤采购单和销售单。
- 证据：前端 `erp-ui/src/views/inventory/report/index.vue:218` 把 `dateRange` 传给 summary 和 stock-warning 两个请求；后端 `InvReportMapper.xml:47-51` 只在 `ReportOrderScope` 中使用 `beginTime/endTime`，`ReportStockScope` 和 `selectStockWarningList` 没有日期条件。2026-06-23 使用 `qa144822m`、`Dept-NumId:108` 调用 `/inventory/report/summary`，加 `beginTime=1900-01-01&endTime=1900-01-01` 后仍返回 `stockItemCount=1`、`totalCurrentQuantity=12.00`、`totalStockCost=1008.00`。
- 影响：用户以为选择某个业务日期后整个报表都按日期变化，实际库存卡片和预警列表仍是当前快照；采购/销售金额可能按日期变动，但库存成本不变，报表横向对比容易被误读。
- 建议：把筛选标签拆成“单据日期”，并在库存指标旁明确“当前快照”；如果要按日期看库存，应基于库存流水/日结快照生成历史库存报表，而不是复用当前库存表。

### P2-56 报表中心预估毛利用销售金额减采购金额，口径不是真实毛利

- 现象：页面“预估毛利”文案为“销售金额 - 采购金额”，后端也直接用 `sales_amount - purchase_amount` 计算；这不是按已售商品成本或出库成本计算的毛利。
- 证据：`erp-ui/src/views/inventory/report/index.vue:71-73` 展示“预估毛利 / 销售金额 - 采购金额”；`InvReportMapper.xml:60-62` 计算 `(report_base.sales_amount - report_base.purchase_amount) as gross_margin`。当前 `inv_sales_detail` 只有销售数量、单价、金额，没有销售成本字段，无法从销售明细直接得出已售成本。
- 影响：同一日期范围内采购金额和销售金额不一定对应同一批商品，采购提前或滞后都会让毛利大幅失真；管理层可能把现金流式差额误认为经营毛利。
- 建议：毛利应基于销售出库时的成本快照或库存流水成本计算；在未补齐 COGS 前，应把该卡片改名为“销售采购差额”或隐藏，避免当作毛利报表使用。

### P2-57 隐藏的字典和参数菜单仍给业务角色保留写权限

- 现象：`字典管理` 和 `参数设置` 两个菜单在数据库中被配置为隐藏，但普通角色和总经理仍持有页面及按钮权限；真实用户 `ry / ERP` 绑定总经理角色，因此仍有隐藏路由下的字典、参数新增、修改、删除、导出和刷新缓存权限。
- 证据：当前 `sys_menu` 中 `105 / 字典管理 / system:dict:list`、`106 / 参数设置 / system:config:list` 均为 `visible=1`、`status=0`，页面节点授权给 `common,zjl`；`1025-1029 / system:dict:query/add/edit/remove/export` 和 `1030-1034 / system:config:query/add/edit/remove/export` 均启用并授权给 `common,zjl`。MySQL 复核 `user_id=2 / ry / zjl` 实际持有 `system:dict:add/edit/export/list/query/remove` 和 `system:config:add/edit/export/list/query/remove`。后端 `SysMenuMapper.xml:78-84` 生成用户菜单树时只过滤 `m.status=0`，不按 `visible` 排除；`SysMenuServiceImpl.buildMenus` 只是把 `visible=1` 转成前端 `hidden` 路由。前端字典和参数页面的新增、修改、删除、导出、刷新缓存按钮也都使用对应 `system:dict:*`、`system:config:*` 权限。
- 影响：隐藏菜单不会出现在侧边栏，但不等于撤销访问和写权限；总经理或普通角色模板仍可通过直达路由、历史标签页或动态路由进入系统级字典/参数配置。字典和参数会影响登录、密码策略、状态枚举、缓存和多个基础页面，多店铺业务角色不应默认拥有这类平台配置写权限。
- 建议：把“隐藏但可直达”的副页面和“业务角色不可用”的功能分开治理。若总经理不应维护系统字典/参数，应移除其 `system:dict:*`、`system:config:*` 授权或停用菜单；若确需授权，应把入口显式展示并增加风险提示和审计。

### P2-111 系统参数详情和按键名查询只要求登录，普通账号可读取初始密码等敏感配置

- 现象：系统参数页面本身需要 `system:config:*` 权限，但参数详情 `/system/config/{configId}` 和按键名查询 `/system/config/configKey/{configKey}` 后端只标注 `@RequiresLogin`；任意已登录账号只要知道 key 或 id，就可读取参数值。
- 证据：`SysConfigController.getInfo` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:66-70` 使用 `@RequiresLogin` 后直接返回 `configService.selectConfigById(configId)`；`getConfigKey` 在 `:76-80` 同样只要求登录并返回 `selectConfigByKey(configKey)`。前端 API `erp-ui/src/api/system/config.js:12-25` 暴露详情和按键名读取；用户管理页 `erp-ui/src/views/system/user/index.vue:292-297` 在创建时读取 `sys.user.initPassword`。当前运行库 `sys_config` 8 条包含 `sys.user.initPassword=123456`、`sys.account.registerUser=false`、密码修改策略、密码有效期和黑名单参数；角色矩阵显示 `ck` 6 个用户、`dz` 12 个用户、`yyjl` 3 个用户、`zdjl` 1 个用户都没有 `system:config:list/query`。
- 影响：多店铺普通账号虽然看不到参数设置菜单，也没有参数查询权限，但仍可枚举或直接读取默认初始密码、注册开关、密码策略和黑名单配置。结合新增用户、导入用户和弱初始密码风险，普通业务账号更容易判断哪些账号可能仍在使用默认密码，也会让系统安全策略对非管理角色透明化。
- 建议：把“前台安全白名单配置”和“后台参数管理”拆开。任意 `configId/configKey` 详情读取应要求 `system:config:query`；登录页或密码策略确需读取的少量键走专用白名单接口，且不要向普通账号返回明文初始密码。用户新增页如需默认密码提示，应仅限具备用户新增/管理权限的账号读取，并优先改为生成一次性初始密码或强制首次重置。

### P2-58 OA 采购审批负责人字段与登录账号不匹配，恢复流程后首节点可能无人可办

- 现象：OA 采购流程的首个“部门审批”任务会分配给当前用户部门的 `leader` 字段；当前所有已填写负责人都是 `ERP`，但系统没有登录账号 `ERP`。如果后续重新启用采购申请并提交单据，任务会进入一个不存在的办理人名下。
- 证据：`sys_dept.leader` 中 `100-109` 号组织均为 `ERP`，`LEFT JOIN sys_user ON user_name=leader` 无匹配用户。BPMN `purchase-approval.bpmn20.xml:10` 使用 `flowable:assignee="${managerApprover}"`；`OaPurchaseServiceImpl.submitPurchase` 在 `:109-117` 先默认 `admin`，但只要部门 `leader` 非空就把 `managerApprover` 改为该字符串。当前 Flowable 已部署 `oaPurchaseApproval` 1 条，`oa_purchase=0`、`ACT_RU_TASK=0`，本轮未提交写入。
- 影响：采购申请菜单当前停用时问题不会出现；但一旦产品决定恢复采购申请，提交后的第一步审批会卡在不可登录账号上，管理员在“我的待办”看不到任务，申请人也只能看到审批中，流程无法自助推进。
- 建议：部门负责人字段必须绑定有效 `sys_user.user_name` 或直接改为用户 ID；保存部门时校验负责人存在且启用。流程提交前也要校验 `managerApprover/financeApprover` 可登录、可审批，并在提交失败时给出可修复提示。不要用自由文本负责人驱动 Flowable assignee。

### P2-59 角色授权树保留停用菜单勾选，和运行时权限口径不一致

- 现象：多个角色仍绑定 `status=1` 的停用菜单或按钮权限，例如采购申请、采购查询/新增/导出、旧销售出库、系统工具、ERP 官网。运行时权限查询会过滤停用菜单，但角色编辑时仍会把这些菜单作为已勾选权限返回。
- 证据：`sys_role_menu` 中普通角色、店长、驻店经理、运营经理、总经理均有停用菜单授权；其中店长、运营经理、总经理仍绑定 `3002/3101/3102/3104` 采购申请权限，店长、驻店经理、运营经理、总经理仍绑定 `4035 / inv:sales:deliver`。这些角色当前覆盖 17 个有效用户。`SysMenuMapper.xml:106-119` 的运行时权限查询按 `m.status='0'` 过滤；但 `SysMenuMapper.xml:88-97` 的 `selectMenuListByRoleId` 没有过滤 `status`，角色编辑页 `system/role/index.vue:530-534` 会把返回的 `checkedKeys` 逐项勾选。
- 影响：角色管理员会看到或保留一批“已授权但实际不可用”的权限，无法从角色页判断这些权限是否真正生效；未来如果重新启用采购申请或旧销售出库，历史角色授权会立即复活，导致多账号权限面突然扩大。
- 建议：角色授权树应清晰区分“启用可授权”“停用不可用”“隐藏但可直达”。停用菜单默认不进入 checkedKeys，或以禁用/灰显状态展示并提示不会生效；重新启用功能时必须重新确认角色授权，不要自动复活历史绑定。

### P2-60 库存盘点取消/删除草稿后端缺少当前组织可写校验

- 现象：库存盘点前端会用当前组织隐藏或拦截非当前组织的“录入实盘 / 确认盘点 / 取消 / 删除草稿”按钮，但后端 `cancelCheck()` 和 `deleteCheck()` 只校验盘点单在当前选择组织的可见范围内，没有像创建、保存、提交、确认那样校验“只能盘点当前组织库存”。
- 证据：`InvStockCheckServiceImpl.createCheck/saveDraft/submitCheck/confirmCheck` 分别调用 `assertWritableInventoryDept(...)`；`cancelCheck()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java:337-348` 只执行 `assertAndGetScopedCheck()` 和状态校验，`deleteCheck()` 在 `:350-361` 也只执行可见性和草稿状态校验。`assertAndGetScopedCheck()` 在 `:364-372` 调用 `assertShopVisible(...)`；而 `InvBaseService.assertShopVisible()` 使用当前组织对子组织的可见范围，`assertWritableInventoryDept()` 则要求目标库存组织等于当前选择组织。前端 `erp-ui/src/views/inventory/stockCheck/index.vue:334-350` 虽然在按钮点击前调用 `ensureWritableCheck()`，但手工 API 或旧客户端不会经过这个保护。运行态当前仍有唯一盘点单 `SC202606140001` 及 1 条明细，早前状态流水为 `draft -> checking -> cancelled`，说明取消流程已是真实状态流转动作。
- 影响：多店铺或上级组织账号如果能看见下级盘点单，并且持有 `inv:stockCheck:remove`，可能绕过前端直接取消下级正在盘点的单据，或删除下级草稿盘点单；这会破坏“当前组织只能写当前组织库存”的边界，尤其影响库存差异复核和门店责任归属。
- 建议：`cancelCheck()` 和 `deleteCheck()` 也补 `assertWritableInventoryDept(check.getShopDeptId(), selectedShopDeptId, "只能盘点当前组织库存")`，并增加跨可见组织取消/删除的单元测试；前端保留现有按钮拦截，但不能把它当成最终权限边界。

### P2-61 调拨审批规则保存校验不验证真实审批岗位，坏配置会拖到提交调拨时才失败

- 现象：调拨审批配置页前端会要求填写节点名称、节点角色、岗位编码和岗位名称；但后端新增/编辑规则时只校验节点非空、顺序大于 0、指定人数大于 0，没有校验节点角色是否合法、岗位是否存在/启用、岗位编码和名称是否匹配、同一规则内节点顺序是否重复，也没有验证启用规则在目标组织下是否能解析出审批人。
- 证据：`InvTransferApprovalRuleController.add/edit` 直接把请求体交给 `ruleService.saveRule(...)`；`InvTransferApprovalRuleServiceImpl.validateRule/validateNodes` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java:266-302` 只做基础数值校验，随后 `saveRuleNodes()` 批量写入节点。真正提交调拨时，`InvTransferApprovalServiceImpl.createInstanceForSubmit` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalServiceImpl.java:105-118` 会跳过无候选人的节点，直到所有节点都无效才抛出“审批规则未配置有效审批人”；`resolveCandidates()` 在 `:675-697` 遇到空岗位编码或特殊跳过节点会直接返回空候选。当前数据库规则 `5 / 市场部门` 的两个节点 `zdjl/yyjl` 可分别解析到 `107:ll1`、`105:ii`，说明现有数据本身可用，但保存接口没有阻止后续写入不可用规则。
- 影响：管理员可能在规则页看到“保存成功”，真实门店或仓库提交调拨时才失败为“未匹配到调拨审批规则”或“审批规则未配置有效审批人”；多账号多店铺场景下，问题会被转嫁给提交单据的一线用户，且难以从规则配置页定位是哪一级节点无审批人。
- 建议：后端保存启用规则时补齐节点角色白名单、岗位存在且启用、岗位编码/名称一致、节点顺序唯一、范围组织有效和候选审批人可解析校验；保存草稿或停用规则可放宽，但启用前必须提供“预检”结果，列出每一级实际候选审批人和会被跳过的节点。

### P2-62 操作日志详情和导出权限面过宽，已记录的请求参数含个人与业务敏感信息

- 现象：操作日志详情会直接展示并支持复制完整请求参数和返回参数；数据库中已有用户、部门、调拨等操作日志保存了手机号、邮箱、角色、部门、商品、数量、调拨组织等内容。操作日志和登录日志的查看、删除、导出、解锁权限不只给超级管理员，也给了“普通角色”和“总经理”。
- 证据：`sys_oper_log` 当前 241 条，其中 59 条 `oper_param` 命中 `phonenumber/email/phone/price/cost/amount` 等敏感或业务明细关键字；样例包括用户编辑日志中的 `phonenumber=15666666666`、`email=ry@qq.com`、角色和部门对象，以及部门日志中的 `phone=15888888888`、`email=ry@qq.com`。前端 `erp-ui/src/views/system/operlog/detail.vue:76-99` 直接展示“请求参数 / 返回参数”并提供复制按钮；后端 `LogAspect` 仅固定排除 `password/oldPassword/newPassword/confirmPassword`，见 `erp-common/erp-common-log/src/main/java/com/erp/common/log/aspect/LogAspect.java:45-46`、`:151-160`、`:187-220`。菜单权限中 `system:operlog:list/query/remove/export` 和 `system:logininfor:list/query/remove/export/unlock` 已授予普通角色、总经理；当前有效用户 `ry` 绑定总经理，普通角色当前无有效用户但权限保留。
- 影响：日志页本应服务审计，但现在任何被授予普通角色或总经理的账号都可能查看、复制、导出或清空包含个人联系方式和业务操作明细的日志；多账号多店铺场景下，日志权限会绕过业务页面字段级权限和组织上下文，形成“从日志反查敏感数据”的侧路。
- 建议：操作日志详情和导出应拆成独立高权限点，例如 `system:operlog:detail`、`system:operlog:exportSensitive`；普通运维角色默认只看摘要。日志采集层增加字段级脱敏/白名单，除密码外同步脱敏手机号、邮箱、身份证、银行卡、成本、价格、工资、token、验证码等字段；清空/删除日志应只给安全管理员并强制二次确认和审计留痕。

### P3-146 操作日志和登录日志缺少稳定用户 ID 与组织上下文，审计主体只能靠用户名倒推

- 现象：登录日志只保存用户名、IP、状态、消息和时间；操作日志只保存用户名、部门名、IP、URL、请求参数和返回参数。两类日志都没有稳定 `user_id`、角色快照、当前选择门店/仓库、组织类型、会话或设备标识。多账号多店铺审计时，责任主体和操作组织只能靠 `oper_name/user_name` 以及请求参数倒推。
- 证据：当前运行态 `sys_logininfor` 有 496 条，表结构和 `SysLogininfor` 实体只有 `info_id/user_name/ipaddr/status/msg/access_time` 等字段；`SysRecordLogService.recordLogininfor()` 只写用户名、IP、消息和状态，`SysLogininforMapper.xml` 也只插入这些字段。`sys_oper_log` 当前有 244 条，`SysOperLog` 和 `SysOperLogMapper.xml` 只有 `oper_name/dept_name/oper_ip/oper_url/oper_param/json_result/status/error_msg/oper_time/cost_time` 等字段；`LogAspect` 从 `SecurityUtils.getUsername()` 写入 `operName`，没有写入用户 ID、角色、当前门店或仓库上下文。当前样例中 `oper_id=343/342/341` 的部门编辑日志 `dept_name` 为空，`oper_id=340` 店铺授权日志只能从 `oper_param=122 [103]` 倒推出被授权用户和组织。`SysUserController.edit()` 允许在编辑用户时提交并校验新的用户名，`SysUserMapper.updateUser` 会更新 `user_name`，逻辑删除后旧用户名也可被复用。
- 影响：用户改名、删除后重建同名账号、角色或组织授权变化、多组织切换后，旧日志无法稳定还原“哪个用户 ID、当时哪个角色、在哪个门店/仓库上下文操作”。权限、店铺授权、调拨、库存和工资等高风险操作虽然有通用日志，但审计人员仍要结合当前用户表和截断请求参数推断；遇到日志清空、账号重命名或参数不含 `userId/shopDeptId` 时会断链。
- 建议：登录日志和操作日志新增并写入稳定主体快照：`user_id`、用户名、昵称、角色 ID/角色键摘要、主部门 ID/名称、当前选择组织 ID/名称/类型、token/session/device 标识和客户端类型。登录日志至少保存成功登录的 `user_id`、失败账号原文、设备和 IP；操作日志写入当前组织上下文，并支持按用户 ID、组织、角色过滤。历史日志可保留用户名，但新增日志必须以 ID 为主。

### P2-63 “重置密码”独立权限点未生效，高风险动作仍走用户修改权限

- 现象：菜单权限树中存在 `1006 / 重置密码 / system:user:resetPwd`，但用户列表“更多 -> 重置密码”前端使用的是 `system:user:edit`，后端 `/user/resetPwd` 也要求 `system:user:edit`。因此角色管理员无法单独控制“能修改用户资料”和“能重置密码”两个动作。
- 证据：数据库 `sys_menu` 中用户管理按钮权限包含 `system:user:add/edit/remove/export/import/resetPwd`；普通角色和总经理都被授予 `system:user:resetPwd`。源码 `rg "system:user:resetPwd" erp-ui erp-modules` 只命中菜单/测试上下文，没有真实前端按钮或控制器注解使用。前端 `erp-ui/src/views/system/user/index.vue:88-93` 的更多菜单和重置密码项都使用 `v-hasPermi="['system:user:edit']"`；`confirmShopScopeAfterCreate()` 在 `:477-480` 也用 `system:user:edit` 判断能否进入店铺授权。后端 `SysUserController.resetPwd()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:344` 注解为 `@RequiresPermissions("system:user:edit")`。
- 影响：一个拥有用户修改权限的账号就能重置别人密码，即使角色配置界面看起来有独立的“重置密码”权限可控；反过来，只授予 `system:user:resetPwd` 也不会让按钮和接口可用。对多账号管理来说，重置密码是高风险动作，应比普通资料修改更细分。
- 建议：前端重置密码按钮和后端 `/user/resetPwd` 改用 `system:user:resetPwd`；用户资料修改继续使用 `system:user:edit`。店铺授权、角色分配也应跟 P2-37 一样使用独立权限点，避免把多个高风险授权动作都塞进“修改用户”。

### P2-64 销售退货“修改”权限未被前后端使用，草稿编辑仍走新增权限

- 现象：数据库菜单中启用了 `4083 / 销售退货修改 / inv:salesReturn:edit`，店长、驻店经理、运营经理、总经理等角色都被授予该权限；但销售退货页面编辑草稿和后端保存草稿都使用 `inv:salesReturn:add`，`inv:salesReturn:edit` 不控制任何真实按钮或接口。
- 证据：MySQL 复核销售退货权限包含 `list/query/add/edit/remove/submit/confirm/export`，角色 `103/104/105/107` 都有 `inv:salesReturn:edit`。源码 `rg "inv:salesReturn:edit|salesReturn:edit" erp-ui erp-modules sql` 只命中前端完整性测试；`erp-ui/src/views/inventory/salesReturn/index.vue:46` 草稿行“编辑”按钮使用 `v-hasPermi="['inv:salesReturn:add']"`，弹窗“保存草稿”在 `:151` 也使用 `inv:salesReturn:add`；后端 `InvSalesReturnController.save()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesReturnController.java:58-64` 注解也是 `@RequiresPermissions("inv:salesReturn:add")`。
- 影响：角色管理员以为可以把“新建退货单”和“修改已有草稿”分给不同岗位，实际只授予修改权限不会有任何效果；授予新增权限又会同时允许新建和编辑草稿。销售退货和采购退货、库存盘点一样存在权限树细分但执行不生效的问题。
- 建议：如果需要区分新增和草稿编辑，前端编辑按钮、保存已有 `returnId` 的请求和后端更新草稿逻辑应使用 `inv:salesReturn:edit`；如果不区分，就删除或隐藏 `inv:salesReturn:edit` 权限点，并同步更新角色默认授权和测试。

### P2-65 销售管理“修改”权限未被前后端使用，草稿编辑仍走新增权限

- 现象：数据库菜单中启用了 `4032 / 销售修改 / inv:sales:edit`，店长、驻店经理、运营经理、总经理等角色都被授予该权限；但销售单草稿编辑按钮、保存草稿弹窗和后端 `/sales/save` 都走 `inv:sales:add`，`inv:sales:edit` 没有控制任何真实写操作。
- 证据：MySQL 复核销售管理权限包含 `list/add/edit/remove/query/submit/export`，上述角色均持有 `inv:sales:edit`。源码 `rg "inv:sales:edit|销售修改"` 只命中前端完整性测试和菜单数据；前端 `erp-ui/src/views/inventory/sales/index.vue:57` 草稿行“编辑”按钮、`:153` “保存草稿”按钮都使用 `v-hasPermi="['inv:sales:add']"`。后端 `InvSalesController.save()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesController.java:32-38` 使用 `@RequiresPermissions("inv:sales:add")`，没有 `inv:sales:edit` 接口。
- 影响：角色配置界面看起来能把“新建销售单”和“修改草稿销售单”拆开，实际只授予修改权限不会让用户编辑；授予新增权限则同时允许新建和编辑。对门店销售岗位最小权限配置不友好，也会让权限审计误判销售单草稿谁能维护。
- 建议：按真实业务决定是否拆分新增和修改。需要拆分时，已有 `orderId` 的保存走 `inv:sales:edit`，新建走 `inv:sales:add`；不需要拆分时，停用或删除 `inv:sales:edit`，并同步清理角色授权和测试断言。

### P2-66 采购管理“修改”权限未被前后端使用，草稿编辑仍走新增权限

- 现象：数据库菜单中启用了 `4022 / 采购管理修改 / inv:purchase:edit`，超级管理员、总经理、仓库管理员都被授予该权限；但采购草稿编辑按钮和后端 `/purchase/save` 都使用 `inv:purchase:add`，`inv:purchase:edit` 没有控制真实写操作。
- 证据：MySQL 复核采购管理权限包含 `list/add/edit/remove/query/receive/submit/qc/export`，其中 `inv:purchase:edit` 授权给角色 `1/107/108`。源码 `rg "inv:purchase:edit"` 只命中前端完整性测试；前端 `erp-ui/src/views/inventory/purchase/index.vue:57` 草稿行“编辑”和 `:193` “保存草稿”都使用 `v-hasPermi="['inv:purchase:add']"`；后端 `InvPurchaseController.save()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseController.java:33-39` 使用 `@RequiresPermissions("inv:purchase:add")`。
- 影响：仓库岗位权限无法区分“能新建采购单”和“只能维护已有草稿采购单”。角色配置中显示了修改权限，但只授权该权限没有任何页面/接口效果；授权新增又会同时放开新建和编辑，和采购流程中的制单分工不一致。
- 建议：采购草稿更新应接入 `inv:purchase:edit`，或者删除/停用该权限点。若拆分新增和修改，前后端都应按 `orderId` 是否存在选择权限，并补充权限回归测试。

### P2-67 调拨保存接口用新增或修改任一权限放行，接口层未区分新建和编辑

- 现象：调拨页前端“发起要货”按钮要求 `inv:transfer:add`，草稿行“编辑”要求 `inv:transfer:edit`；但后端 `/transfer/save` 统一使用 `inv:transfer:add OR inv:transfer:edit`。因此未来创建一个只授予“调拨修改”的自定义角色时，虽然前端不会显示新建按钮，但直接调用保存接口仍可创建新调拨草稿。
- 证据：前端 `erp-ui/src/views/inventory/transfer/index.vue:29` 的“发起要货”按钮使用 `inv:transfer:add`，`:79` 的“编辑”按钮使用 `inv:transfer:edit`，`:168` 的“保存草稿”按钮同时允许 `inv:transfer:add/ edit`。后端 `InvTransferController.save()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java:83-89` 标注 `@RequiresPermissions(value = { "inv:transfer:add", "inv:transfer:edit" }, logical = Logical.OR)`，没有按 `transferId` 是否为空区分新增和修改。MySQL 复核当前超级管理员、店长、驻店经理、运营经理、总经理都同时拥有 `inv:transfer:add` 和 `inv:transfer:edit`，所以存量角色暂时看不出差异。
- 影响：权限树表达了“调拨新增”和“调拨修改”两个动作，但接口层实际把二者合并为任一即可。多账号多店铺后续一旦配置更细岗位，例如只能维护草稿不能发起要货的门店角色，就会被接口权限放大，前端按钮隐藏不能作为安全边界。
- 建议：后端保存接口按 `transferId == null` 要求 `inv:transfer:add`，按 `transferId != null` 要求 `inv:transfer:edit`；或者拆成 `/transfer/save` 和 `/transfer/update` 两个接口。前端弹窗保存按钮也应根据新增/编辑态使用对应权限，而不是一个按钮同时接受两个权限。

### P2-68 仓库主数据列表看集团数据，但新增保存写当前仓库，组织口径会混合

- 现象：在 `仓库：仓库 / 104` 上下文中，商品、分类、供应商页面能看到集团 `100 / 金英灵韵` 归属的主数据；但新增商品、分类或供应商时，后端保存会把 `shop_dept_id` 写成当前选择组织 `104`。页面没有说明这是“集团目录 + 仓库本地目录”还是只应维护一个仓库目录。
- 证据：非运行态 `BossERP` 曾显示 `inv_product=8`，其中 `100` 组织 5 条、`103` 组织 3 条，`inv_product_category=11` 中 `100` 组织 6 条、`103` 组织 5 条，`inv_supplier=3` 中 `100` 组织 2 条、`201` 组织 1 条，均没有 `104` 仓库归属数据。运行态早前快照中 `104` 仓库上下文也主要看到集团 `100` 主数据。`InvDeptScopeMapper.xml:12-19` 的 `selectRelatedDeptIds` 会让 `104` 相关范围包含自身和祖先 `100`，所以列表可见集团数据；但 `InvProductServiceImpl.saveProduct()` 在 `:55-67` 用 `resolveAndValidateShopDept(selectedShopDeptId)` 后把新商品 `shopDeptId` 设为当前选择组织，`InvProductCategoryServiceImpl.saveCategory()` 在 `:63-75` 同样写当前组织，`InvSupplierServiceImpl.saveSupplier()` 在 `:55-62` 也写当前组织。
- 影响：管理员在同一个仓库主数据页面中看到的是集团级目录，新增后却落到仓库级目录；同名商品、分类、供应商会按不同组织分裂，采购选择器、商品完整性统计、供应商引用和后续多仓库共享目录都会变得难解释。当前主数据仍没有 `104` 仓库归属行，后续新增一条到 `104` 就会出现混合口径。
- 建议：明确主数据层级。若商品/分类/供应商是集团共享目录，新增和编辑也应写集团或专门目录组织，并在页面显示“集团目录”；若允许仓库本地扩展，页面需要明确标识来源组织、提供“集团/当前仓库”筛选，并在复制/新增时说明会生成仓库本地数据。

### P2-105 商品导入按当前组织自动新建同名分类，和页面可选集团分类口径不一致

- 现象：商品页面手工新增时，分类下拉来自当前组织相关范围，仓库上下文能选择集团 `100` 的分类；但 Excel 导入按分类名称匹配时只查当前选中组织，找不到就自动在当前仓库/门店下新建同名分类。这样同一个“乌龙茶/红茶”等分类，手工建档可能引用集团分类，导入建档却可能创建本地重复分类。
- 证据：2026-06-24 MySQL 复核运行态，`inv_product_category` 30 条全部 `shop_dept_id=100`，`inv_product=162` 且商品分类引用也全部是 `product_shop=100/category_shop=100`；`104 / 仓库`、`103/108` 门店都在 `100` 祖先链下。前端商品新增下拉使用 `categoryTree()` 的相关范围分类，选项只按 `c.status !== '0'` 禁用；后端 `InvProductServiceImpl.assertAndGetProductCategory()` 也允许使用相关范围内的集团分类。但导入路径 `resolveCategoryId()` 在 `InvProductServiceImpl` 中设置 `query.setShopDeptId(shopDeptId)`，只按当前组织和分类名查找，若未命中就 `insertInvProductCategory`，把新分类 `shopDeptId` 写成当前组织并默认启用。
- 影响：同一批主数据如果一部分手工新增、一部分 Excel 导入，会出现集团分类和仓库/门店本地同名分类并存；商品筛选、报表分类汇总、分类删除引用数和后续分类合并都会被拆成两套。当前库还没有本地重复分类，但按现有导入逻辑，在 `104` 仓库或任一门店导入含“乌龙茶”的 Excel 就会自动制造重复分类。
- 建议：导入分类匹配应和页面选择同口径：优先在相关范围内按分类编码或完整路径匹配集团分类，不能只按当前组织查分类名；自动新建分类必须在导入预览中明确显示目标组织，并要求用户确认。更稳妥的是模板使用分类编码/ID/完整路径，未匹配分类作为错误行下载，不默认创建本地分类。

### P2-106 采购收货确认文案承诺立即增库存，但实际要等质检后才入账

- 现象：采购页收货确认框写着“提交后会增加当前仓库库存，并更新采购单收货进度”，上下文提示也说“采购单、收货和质检都会进入当前仓库库存”；但实际后端收货只生成 `pending` 待检入库记录并更新采购明细已收数量，不增加 `inv_stock`。只有后续质检结果为合格或让步接收时，才真正增加库存并写库存日志。
- 证据：前端 `purchase/index.vue` 的 `getReceiveConfirmMessage()` 直接提示收货提交会增加库存，收货成功 toast 也是“收货成功”；动作规则 `canReceivePurchase()` 在 `submitted` 且非 `pending` 时显示收货，`canQualityCheckPurchase()` 在 `submitted + pending` 时再显示质检。后端 `InvPurchaseServiceImpl.receivePurchase()` 只插入 `InvInboundRecord` 并把 `qcStatus` 改为 `pending`、`status` 保持 `submitted`；库存更新和 `InvStockLog` 写入在 `qualityCheck()` 的 `QC_PASSED/QC_CONCESSION` 分支里。2026-06-24 MySQL 复核当前正式 `inv_purchase_order=0`、`inv_purchase_detail=0`、`inv_inbound_record=0`，所以此问题按源码状态机确认，暂未写入测试数据。
- 影响：仓库人员提交收货后，页面告诉用户库存已经增加，但库存页和报表不会变化，直到质检完成才入账。多账号协同时，采购/仓库/质检三个岗位容易误判“已收货”和“可用库存”的关系；如果质检拒收，前端先前的库存承诺又和实际拒收入账冲突。
- 建议：把收货动作文案改为“生成待检入库记录，质检通过后才增加库存”，成功提示改为“已生成待检记录”；列表状态可单独展示“待检入库数量/已入账数量”。如果业务要求收货即占库存，应新增待检库存/冻结库存口径，并在库存页、报表和发货可用量里明确是否包含待检数量。

### P2-69 角色数据权限没有独立权限点，仍由角色修改权限控制

- 现象：角色管理“更多 -> 数据权限”弹窗可修改角色 `data_scope` 和自定义部门范围，但前端按钮和后端 `/role/dataScope` 接口都使用 `system:role:edit`；权限树只有角色列表、查询、新增、修改、删除、导出，没有独立的 `system:role:dataScope`。
- 证据：MySQL 当前角色菜单权限只有 `system:role:list/query/add/edit/remove/export`。`sys_role` 表含 `data_scope`、`menu_check_strictly`、`dept_check_strictly` 字段，当前总经理角色 `107` 为全部数据权限 `1`，其余角色多为本部门及以下 `4`，`sys_role_dept` 已存在角色 2 的 3 条自定义部门记录。前端 `erp-ui/src/views/system/role/index.vue:142-146` 的“更多/数据权限”使用 `v-hasPermi="['system:role:edit']"`，弹窗在 `:218-255` 提供权限范围和部门树，提交函数在 `:593-598` 调用 `dataScope(this.form)`。后端 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:128-137` 的 `dataScope` 方法也标注 `@RequiresPermissions("system:role:edit")`。
- 影响：拥有角色基础资料修改权限的账号，也能把角色数据范围改成全部数据权限或扩大自定义部门范围。多账号多店铺环境下，数据权限范围变更比角色名称、排序、状态更敏感，复用“角色修改”会让最小授权和审计边界不清。
- 建议：新增并启用 `system:role:dataScope` 权限点，前端“数据权限”入口和后端 `/role/dataScope` 同步改用该权限；角色基础资料编辑继续使用 `system:role:edit`，角色分配用户按 P2-37 拆成 `system:role:authUser`。

### P2-159 角色数据范围缺少后端枚举兜底，非法 `data_scope` 可能绕过数据范围过滤

- 现象：角色数据权限弹窗前端只提供 `1-5` 五个数据范围选项，但后端数据权限保存接口没有 `@Validated`，`SysRole.dataScope` 也没有枚举校验。构造请求可以把角色 `data_scope` 写成非 `1/2/3/4/5` 的值；运行时 DataScope 切面只识别这五种常量，非法值不会拼接任何数据范围 SQL，可能让该角色的数据范围退化为无过滤。
- 证据：前端数据权限下拉只列出 `1 / 全部数据权限`、`2 / 自定数据权限`、`3 / 本部门数据权限`、`4 / 本部门及以下数据权限`、`5 / 仅本人数据权限`，见 `erp-ui/src/views/system/role/index.vue:228-235`、`:300-320`。后端 `/role/dataScope` 方法没有 `@Validated`，只调用 `checkRoleAllowed/checkRoleDataScope` 后进入 `authDataScope`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:130-137`；`SysRole.dataScope` 只是普通字符串字段，没有 `@Pattern` 或白名单校验，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysRole.java:39-41`、`:134-141`；`SysRoleMapper.updateRole` 在 `erp-modules/erp-system/src/main/resources/mapper/system/SysRoleMapper.xml:124-136` 会在非空时直接写入 `data_scope`。DataScope 切面只对常量 `1-5` 分支拼 SQL，非法值会被加入 `conditions` 但不追加 `sqlString`，且 `conditions` 非空时不会走“不查询任何数据”的兜底，见 `erp-common/erp-common-datascope/src/main/java/com/erp/common/datascope/aspect/DataScopeAspect.java:79-145`、`erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/Constants.java:150-174`。运行态当前 `sys_role` 的 `data_scope` 均为合法值，未发现存量脏数据，本轮没有构造写入。
- 影响：`data_scope` 是多账号多店铺数据边界的核心字段，影响用户、部门、角色、商品、客户、供应商、采购、销售、库存、调拨等所有接入 `DataScope` 的查询。若拥有 `system:role:edit` 的账号或脚本写入非法范围，页面可能仍显示角色存在，但数据范围 SQL 不再生效，形成比“全部数据权限”更隐蔽的越权状态。结合 P2-69 和 P2-101，当前总经理/普通角色模板仍存在角色编辑能力，风险不能只依赖前端下拉。
- 建议：`/role/dataScope` 增加 `@Validated`，`SysRole.dataScope` 使用 `@Pattern("^[1-5]$")` 或服务层白名单校验；`roleSort/status/menuCheckStrictly/deptCheckStrictly` 也应补范围/枚举校验。`DataScopeAspect` 对未知 `data_scope` 应按最小权限处理为 `dept_id = 0` 并记录告警，不应静默无过滤。增加数据库巡检脚本，发现非法 `data_scope` 时阻断启动或自动降级为最小权限并提示管理员修复。

### P3-156 角色非自定义数据范围仍残留 `sys_role_dept`，切回自定义会误带旧部门

- 现象：角色数据权限弹窗会先加载部门树 checkedKeys，再根据 `dataScope` 显示或隐藏部门树。当前运行库 `common / 普通角色` 的 `data_scope=4 / 本部门及以下`，但 `sys_role_dept` 仍残留 3 条部门范围；如果管理员把该角色切换成“自定义数据权限”，页面会直接带出这些旧部门勾选，而不是从空范围或明确的当前范围开始。
- 证据：MySQL 复核 `sys_role.role_id=2 / common` 当前 `data_scope=4`、`dept_check_strictly=1`，但 `sys_role_dept` 仍有 `100 / 金英灵韵 / GROUP`、`101 / 金英灵韵 / COMPANY`、`105 / 测试部门 / STORE` 三条。前端 `erp-ui/src/views/system/role/index.vue:548-558` 打开数据权限弹窗时始终调用 `getDeptTree(row.roleId)` 并 `setCheckedKeys(res.checkedKeys)`，`:541-545` 的 `dataScopeSelectChange` 只在切到非自定义时清空，切到 `2 / 自定义数据权限` 时不会清空旧勾选。后端 `SysRoleController.deptTree` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:237-245` 直接返回 `deptService.selectDeptListByRoleId(roleId)`；`SysDeptMapper.xml:149-158` 从 `sys_role_dept` 查询 checkedKeys，不判断角色当前 `data_scope`。`DataScopeAspect` 只有在 `data_scope=2` 时才使用 `sys_role_dept`，所以这批残留当前不生效，但会在切换回自定义时被重新带入。
- 影响：数据权限维护人员看到的“本部门及以下”与数据库残留范围不一致；后续切回自定义时容易误把历史部门作为当前授权保存，造成角色数据范围扩大或偏离预期。审计脚本如果只看 `sys_role_dept`，也会误判 `common` 仍有自定义部门范围。
- 建议：数据权限保存时后端应强制保持一致性：`data_scope != 2` 时清空 `sys_role_dept`；`data_scope=2` 时要求至少选择一个有效部门并展示部门数量。`/role/deptTree/{roleId}` 只有在角色当前为自定义数据权限时才返回 checkedKeys，前端从非自定义切到自定义时应明确“沿用历史范围/清空重选”，避免无提示带入旧授权。

### P2-70 角色菜单授权保存未校验可授予菜单范围，前端树不是后端权限边界

- 现象：角色编辑弹窗会展示“菜单权限”树，树数据按当前用户权限返回；但保存角色时，后端直接按请求中的 `menuIds` 删除并重建 `sys_role_menu`，没有校验每个菜单 ID 是否属于当前用户可授权范围。
- 证据：MySQL 当前 `system:role:edit` 授给角色 `2 / 普通角色` 和 `107 / 总经理`，有效用户中只有 `ry` 通过总经理角色实际持有该权限；`sys_menu` 共 237 个菜单，角色 107 已绑定 228 个菜单，角色 2 绑定 93 个菜单。前端 `erp-ui/src/views/system/role/index.vue:193-206` 在角色新增/修改弹窗内提供菜单树，`:393-397` 通过 `roleMenuTreeselect(roleId)` 加载树，`:571-578` 提交时把 `getMenuAllCheckedKeys()` 写入 `this.form.menuIds` 后调用 `updateRole`。后端 `SysMenuController.roleMenuTreeselect()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMenuController.java:76-85` 只要求登录，并用 `menuService.selectMenuList(userId)` 返回当前用户菜单；`SysRoleController.edit()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:108-124` 只检查 `system:role:edit`、角色可见范围和名称/key 唯一性；`SysRoleServiceImpl.updateRole()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java:286-294` 删除原菜单关联后调用 `insertRoleMenu()`，而 `insertRoleMenu()` 在 `:349-364` 逐个插入请求 `menuIds`，没有可授权范围校验。
- 影响：前端菜单树只能减少误操作，不能阻止构造请求写入树外菜单。后续一旦给非超级管理员配置“角色修改”权限，就可能让该账号把自己不可见或不应授予的菜单权限授给可管理角色；多账号多店铺下，角色权限扩张会绕过岗位分工审计。
- 建议：后端保存角色菜单前，按当前用户重新计算可授权菜单集合，并拒绝任何不在集合内的 `menuIds`；同时把角色基础资料修改和菜单授权拆成独立权限，例如 `system:role:edit` 与 `system:role:menuAuth`，新增/修改角色时也要使用同一套菜单授权校验。

### P2-71 运行态系统模块仍使用旧包，和当前源码/target/classes 口径不一致

- 现象：当前工作区源码和 `target/classes` 已包含用户配置状态相关字段与 SQL，但 8080 运行态接口仍按旧包返回用户列表；同时当前常用前端端口 `1026` 无监听，后续电脑端页面复测不能默认沿用早前“前端 1026 可访问”的状态。
- 证据：`lsof -nP -iTCP:8080 -sTCP:LISTEN` 显示 Java 进程监听 8080，`/actuator/health` 返回 `UP`；`curl http://localhost:1026/` 返回连接失败。`erp-modules/erp-system/target/classes/mapper/system/SysUserMapper.xml` 已包含 `role_count`、`shop_scope_count`、`setup_status` 和 `setupStatus` 筛选；但 `unzip -p erp-modules/erp-system/target/erp-modules-system.jar BOOT-INF/classes/mapper/system/SysUserMapper.xml` 只能看到较早的 `salaryShopDeptId` 改动，没有配置状态 SQL。运行态 `/system/user/list?pageNum=1&pageSize=2&setupStatus=complete` 仍返回 `total=25`，rows 中没有 `setupStatus/roleCount/shopScopeCount`。
- 影响：当前页面审计会同时面对三套口径：工作区源码、`target/classes`、正在运行的 jar。若不先重启/重打包，前端新增列、后端 mapper 和运行接口会继续不一致，导致“源码已修但页面仍坏”或“页面表现和代码检查相反”的结论反复出现。
- 建议：建立审计/验收前置检查：明确当前运行的是哪个 jar、构建时间和 Git 版本；每次重要源码或 mapper 改动后重新打包并重启对应模块，再用接口字段或健康检查确认运行态加载了新包。前端页面复测前也应确认 dev server 端口实际监听，而不是沿用历史端口。

### P2-72 总经理角色持有全局菜单写权限，可改动路由和权限基础配置

- 现象：菜单管理是全局路由、按钮权限和侧边栏的基础配置，但当前 `system:menu:add/edit/remove` 授给了业务角色“总经理”；有效用户中 `ry` 通过总经理角色实际持有菜单新增、修改、排序和删除能力。
- 证据：MySQL 复核当前 `sys_menu` 共 249 条，其中 57 个菜单/页面节点、192 个按钮权限节点；`system:menu:add/edit/list/query/remove` 存在且授权给角色 `2 / 普通角色` 与 `107 / 总经理`，当前有效用户里 `user_id=2 / ry / role 107` 实际持有 `system:menu:*`。前端 `erp-ui/src/views/system/menu/index.vue:33-50` 提供“新增”和“保存排序”，`:103-123` 提供行内“修改 / 新增 / 删除”，`:444-527` 提交新增、修改和排序；后端 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMenuController.java` 中新增、修改、排序、删除分别受 `system:menu:add/edit/remove` 控制，`updateSort` 只按请求菜单 ID 更新顺序；`SysMenuServiceImpl.insertMenu/updateMenu/updateMenuSort` 直接写全局菜单表。删除虽有子菜单和角色绑定保护，但新增、修改、排序仍可影响全系统。
- 影响：总经理或业务管理员可以新增按钮权限、修改 `path/component/perms/status/visible/orderNum` 等全局字段，影响所有账号的路由、权限树和侧边栏，不受多店铺数据范围限制；这会把业务角色和平台权限基础设施混在一起，权限审计也会因菜单配置变化而失真。
- 建议：菜单管理写权限只授予超级管理员或平台运维角色；如果确需业务管理员参与，应拆成只读、排序、新增、修改、删除等更细权限，并对权限标识、组件路径、路由地址、状态可见性等安全字段做二次确认和操作审计。

### P2-73 旧口径薪资模板菜单和表未进入当前运行态，薪资模型边界仍不清

- 现象：旧/非运行态口径曾出现“薪资模板”页面菜单、`system:salaryTemplate:*` 按钮权限和 `sys_salary_template` 存量记录；但当前运行态已查不到该菜单和表。电脑端实际交付的是“薪资方案 / 档位 / 员工绑定”，薪资模板与薪资方案的模型边界没有解释。
- 证据：当前运行态 `show tables like 'sys_salary_template'` 返回空；按 `薪资模板`、`salaryTemplate` 检索当前 `sys_menu` 返回空。`erp-ui/src/views` 中没有 `system/salaryTemplate/index.vue`，`erp-ui/src/api` 只有 `system/salaryConfig.js` 和 `oa/salary.js` 两套薪资相关 API；`SysSalaryConfigController` 使用 `/salaryConfig` 和 `system:salary:*` 权限，未实现 `system:salaryTemplate:*`；全库 `rg "salaryTemplate|SalaryTemplate|salary_template"` 未发现业务源码实现。
- 影响：旧库和旧菜单会给验收造成“系统还有薪资模板能力”的信号，但当前运行态无法解释这套模型；管理员在薪资配置页看到的是方案和档位，无法知道历史模板数据是否已迁移、废弃或被当前方案替代。
- 建议：明确薪资模板是否废弃。若废弃，应在迁移脚本和验收说明中标注旧表/旧菜单已下线；若仍要交付，应补齐模板列表、新增、编辑、删除、导出接口与页面，并明确薪资模板、薪资方案、员工绑定三者的关系。

### P2-74 旧口径考勤 Excel 导入菜单未进入当前运行态，导入页面和接口仍缺失

- 现象：旧/非运行态口径曾启用“考勤 Excel 导入”页面菜单，权限标识为 `oa:attendance:sync`；当前运行态已查不到该菜单，也没有钉钉原始表、Excel 导入表或班次表。当前工程仍没有对应页面、前端 API 或后端导入接口，管理员无法通过电脑端批量导入或补录考勤数据。
- 证据：当前运行态按 `考勤Excel`、`dingtalk`、`oa:attendance:sync` 检索 `sys_menu` 返回空；`show tables like '%attendance%'` 只返回 `oa_attendance_record`，`show tables like '%shift%'` 返回空。`erp-ui/src/views/oa` 下没有 `dingtalk/excel.vue` 或 `oa/dingtalk/excel/index.vue`；`rg "dingtalk|oa:attendance:sync|attendance:sync|oa/dingtalk/excel"` 在 `erp-ui`、`erp-modules`、`sql` 中无命中。当前运行态只有 1 条正式考勤记录，考勤批量补录和转换仍没有电脑端入口。
- 影响：考勤数据需要从钉钉/Excel 导入或批量补录时，只能绕过电脑端直接写库或另找脚本，后续 OA 工资计算、考勤统计和门店人员管理都缺少可审计的导入流程。旧菜单存在过但当前运行态撤销，也会让验收者误判“导入能力被下线了还是未交付”。
- 建议：如果不交付考勤导入，应在菜单和数据迁移说明中明确下线原因；如果要交付，应补齐 Excel 上传、字段预览、用户/组织匹配、日期去重、错误行下载、导入幂等和权限校验，并把导入结果写入可追溯日志。

### P2-80 旧口径钉钉/Excel 考勤数据与当前运行态分裂，电脑端缺导入转换闭环

- 现象：旧/非运行态口径曾出现大量钉钉原始考勤和 Excel 导入明细数据，但当前运行态只剩 `oa_attendance_record=1`，并没有钉钉原始表、Excel 导入表、用户映射表或班次表；旧口径中的考勤 Excel 导入菜单当前也未进入运行态，电脑端没有导入入口、页面或接口实现。
- 证据：旧/非运行态曾复核到 `oa_dingtalk_raw_record=316`、`oa_dingtalk_raw_summary=156`、`oa_dingtalk_sync_log=24`、`oa_excel_import_batch=1`、`oa_excel_import_detail=100`、`oa_shift=3`，但当前运行态 `show tables like '%attendance%'` 只返回 `oa_attendance_record`，`show tables like '%shift%'` 返回空。旧口径中的 `3221 / 考勤Excel导入 / oa/dingtalk/excel` 和 `4152-4154 / oa/dingtalk/*` 当前均未进入运行态菜单，详见 P2-74 和 P3-48。
- 影响：管理员无法判断当前系统到底支持“正式考勤录入”、钉钉同步、Excel 导入还是仅保留单条正式记录；旧库中的原始数据无法在当前网页端解释，当前库也没有导入/转换能力。后续工资核算容易被误判为“没有考勤来源”，而不是“导入链路未交付或数据源已漂移”。
- 建议：明确考勤数据流水线：原始钉钉记录、Excel 明细、用户映射、班次规则和正式考勤记录之间需要有可视化状态页。至少补充“待映射 / 待转换 / 已入正式考勤 / 失败明细”四类状态，并提供重试、忽略、清理和审计日志；未交付前应在考勤页显示“存在未处理原始考勤数据”的提示。

### P2-93 班次模型在旧口径存在但未进入当前运行态，电脑端没有排班闭环

- 现象：旧/非运行态口径曾出现标准班、早班、晚班等班次数据，但当前运行态没有 `oa_shift` 表；电脑端也没有班次维护、员工排班、门店排班或班次选择页面，考勤和工资链路无法按班次解释。
- 证据：当前运行态 `show tables like '%shift%'` 返回空，按班次/排班/shift 检索 `sys_menu` 无结果；`rg "oa_shift|OaShift|shift_id|shiftId|shift_name|shiftName|班次|排班" erp-ui erp-modules erp-api sql` 仅命中移动端测试里的静态通知文案“端午排班调整”，没有实体、Mapper、Controller、API 或页面实现。旧/非运行态曾出现 `oa_shift=3`，不作为当前网页端运行态结论。
- 影响：多门店实际排班无法落到系统，迟到早退、工时、加班和工资计算只能依赖固定工资参数或手工记录，不能按员工实际班次解释。旧库里出现过的标准班/早班/晚班会让验收口径混乱：数据库曾暗示支持排班，但当前运行态和电脑端都没有闭环。
- 建议：若班次模型要交付，应补齐班次表迁移、班次维护、门店/员工排班、有效期、节假日覆盖、导入校验、考勤匹配和工资计算引用；若当前工资只按固定上下班时间计算，应下线或标注班次模型为预留，并在考勤/工资页说明班次数据未生效。

### P2-81 旧口径 OA 采购申请和流程实例断开，当前运行态只剩残留流程定义

- 现象：旧/非运行态口径曾出现 `oa_purchase` 申请、明细、审批意见和运行中流程任务互相断开的数据；当前运行态已经没有 `oa_purchase` 业务数据和运行任务，但仍保留 1 条流程部署和 1 条流程定义。电脑端采购申请主入口仍处于隐藏停用和权限码不一致状态，无法让管理员解释“流程定义存在但采购申请不可用”的关系。
- 证据：当前运行态精确复核 `oa_purchase=0`、`oa_purchase_comment=0`、`ACT_RU_TASK=0`、`ACT_HI_PROCINST=0`、`ACT_HI_TASKINST=0`，仅 `ACT_RE_DEPLOYMENT=1`、`ACT_RE_PROCDEF=1`。旧/非运行态曾复核到 `purchase_id=10/11/12/19`、`process_instance_id=NULL`、`ACT_RU_TASK` 中存在 `BUSINESS_KEY_=1/2` 且办理人包含当前用户表不存在的 `若依`，该结论不作为当前网页端运行态数据结论。采购申请菜单 `3002` 及其按钮 `3101-3104` 仍为 `status=1`、`visible=1`，详见 P2-78。
- 当前复核：当前运行态没有 `wf_%` 工作流业务表，也没有工作流页面；Activiti 残留定义没有电脑端解释入口，见 P1-14。
- 影响：当前用户看不到采购申请，也看不到流程定义的用途；如果后续恢复旧库数据或重新启用采购申请，历史断链问题会重新出现，审批人员仍可能无法从“我的待办/已办”追溯到真实采购申请、明细和审批意见。
- 建议：恢复 OA 采购前先做一次流程数据对账：`oa_purchase.process_instance_id`、Flowable/Activiti `BUSINESS_KEY_`、`oa_purchase_comment.task_id` 必须能互相追溯；无法对齐的历史任务应归档或取消。待办/已办详情页应展示申请、明细、流程实例、任务 ID、审批人和意见，并对不存在办理人给出管理员修复入口。

### P2-92 OA 待办/已办已启用给业务角色，但上游采购申请仍停用隐藏

- 现象：当前运行态已经把“我的待办 / 我的已办”及审批、导出按钮权限启用并授给店长、运营经理、总经理，但上游“采购申请”页面仍是 `status=1/visible=1` 的停用隐藏状态，且当前 `oa_purchase=0`、运行任务和历史任务均为 0。业务角色能看到一个采购审批工作台，却不能从电脑端创建或提交采购申请，审批链路处于半上线状态。
- 证据：2026-06-24 MySQL 复核当前运行态：`3003 / 我的待办 / oa:todo:list`、`3004 / 我的已办 / oa:done:list`、`3103 / 待办审批 / oa:todo:approve`、`3105 / 待办导出 / oa:todo:export`、`3106 / 已办导出 / oa:done:export` 均为 `status=0/visible=0`，并授给 `admin,dz,yyjl,zjl`；但 `3002 / 采购申请 / oa:purchase:list` 和 `3101-3104 / oa:purchase:*` 仍为 `status=1/visible=1`，只保留隐藏停用授权。当前 `oa_purchase=0`、`oa_purchase_comment=0`、`ACT_RU_TASK=0`、`ACT_HI_TASKINST=0`，只有 `ACT_RE_DEPLOYMENT=1` 和 `ACT_RE_PROCDEF=1`。前端待办页固定文案为“集中处理当前账号需要审批的采购申请”，并直接显示“通过/驳回”按钮；后端仍通过 `OaPurchaseController.todo/done/approve/export` 提供待办、已办、审批和导出接口。
- 影响：用户会看到“我的待办/我的已办”审批入口和采购审批文案，但没有可用的采购申请入口来产生流程数据，列表只能长期空白；如果通过接口或后续恢复菜单产生任务，当前按钮又会立即允许业务角色处理采购审批。多账号验收时会出现“审批台已交付、申请端未交付”的解释断层。
- 建议：先把 OA 采购审批作为一个整体交付开关处理。若交付采购审批，应启用并验收采购申请、待办、已办、审批意见、导出和流程定义的完整闭环；若不交付，应同时隐藏 OA 首页采购申请提示、首页采购审批建议、待办/已办菜单和相关接口权限，不要只开放审批工作台。

### P2-162 OA 审批工作台按钮权限和后端接口权限不自洽，恢复数据后详情/审批会出现可见但失败

- 现象：OA 已办页在已启用的“我的已办”工作台中展示“详情”按钮，但详情接口仍要求已停用的 `oa:purchase:query`；OA 待办页的“通过 / 驳回”按钮也没有按独立的 `oa:todo:approve` 权限做前端显隐，只靠后端拦截。当前运行态采购数据为 0 时不触发，但一旦恢复采购申请或导入历史任务，业务角色会看到可点击动作却可能在接口层失败。
- 证据：前端已办页 `erp-ui/src/views/oa/done/index.vue:99-102` 固定展示“详情”，调用 `getPurchaseDetail()`；`erp-ui/src/api/oa/purchase.js:43-47` 请求 `/oa/purchase/{purchaseId}`，后端 `OaPurchaseController.detail()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java:77-81` 要求 `oa:purchase:query`。当前运行态 `3002/3101-3104` 采购申请及按钮均为 `status=1/visible=1`，店长、运营经理、总经理虽保留停用授权，但 `SysMenuMapper.selectMenuPermsByUserId` 只返回 `m.status='0'` 的权限；MySQL 按相同口径复核 `dz/yyjl/zjl` 运行时只有 `oa:todo:list/approve/export` 和 `oa:done:list/export`，没有 `oa:purchase:query`。待办页 `erp-ui/src/views/oa/todo/index.vue:81-84` 的通过/驳回按钮没有 `v-hasPermi`，而 `OaPurchaseController.approve()` 在 `:84-90` 才要求 `oa:todo:approve`。当前 `oa_purchase=0`、`oa_purchase_comment=0`、`ACT_RU_TASK=0`、`ACT_HI_TASKINST=0`，本轮未构造审批数据。
- 影响：审批工作台的页面权限和接口权限没有形成自洽闭环。恢复采购申请后，店长/运营经理/总经理可以进入已办列表，但详情按钮会因为采购查询权限停用而不可用；如果后续配置只读待办角色，页面仍会显示通过/驳回按钮并在提交后失败。用户体验上表现为“有按钮但不能完成动作”，权限治理上也无法表达“只看待办/已办但不能审批”或“能看已办详情但不能发起采购申请”。
- 建议：把审批工作台权限拆清楚：已办详情应使用 `oa:done:query` 或专门的 `oa:approval:detail`，不要依赖已停用采购申请查询权限；待办通过/驳回按钮前端和后端都使用 `oa:todo:approve`，无权限时隐藏或禁用并解释原因。若采购申请继续停用，应同步停用工作台详情/审批动作或提供只读空态；若恢复采购审批，应一次性启用申请、待办、已办、详情和审批权限并补端到端回归。

### P2-94 仓库调拨路径不校验仓库上下文，门店上下文下仍显示门店要货动作

- 现象：`/cangku/transfer` 是仓库管理下的调拨路径，但在当前浏览器组织上下文为 `108 / 市场部门 / STORE` 时仍能打开，并显示“当前门店：市场部门”和“发起要货”动作；同一路径只有在切到 `104 / 仓库 / WAREHOUSE` 后才显示仓库语义和“发货”动作。
- 证据：2026-06-23 复核时，admin 设置 `selected_dept_id=108`、`selected_dept_type=STORE` 后访问 `/cangku/transfer`，面包屑为“仓库管理 / 调拨管理”，但页面文案为“当前门店：市场部门”，按钮包含“发起要货”，列表显示 `TF202606140001 / 部分发货 / 当前责任：仓库管理员`；切到 `selected_dept_id=104`、`selected_dept_type=WAREHOUSE` 后，同一路径显示“当前仓库：仓库”，按钮变为“详情 / 发货 / 导出”等仓库动作。`/inventory/transfer` 和 `/cangku/transfer` 复用同一组件，当前主要依赖 sessionStorage 组织类型决定业务语义。
- 影响：URL 和面包屑告诉用户已经进入仓库管理，但实际动作可能按门店上下文执行；仓库人员或管理员复制链接、刷新或从其他入口跳转时，容易在错误组织语义下发起要货或误判发货责任。多账号多店铺场景下，这会让“路由权限”和“当前业务组织”两套边界不一致。
- 建议：为仓库路由和门店路由增加组织类型守卫：`/cangku/*` 需要 `WAREHOUSE` 上下文，`/inventory/transfer` 需要明确允许的门店/仓库模式并在页面标题中解释；组织不匹配时跳到选择组织页或禁用写动作并给出提示。回归用例应覆盖 `STORE` 访问 `/cangku/transfer`、`WAREHOUSE` 访问 `/inventory/transfer`、刷新恢复和复制链接打开。

### P2-82 历史参数口径曾包含安全/文件/审计类配置，当前运行态已回收到 8 条但仍需防止无效参数重新上线

- 现象：早前参数设置页曾展示 19 条系统参数，其中包含 `jwt.secret`、`jwt.expiration`、`security.inner.sign.key`、`audit.retention.days`、`file.presigned.url.ttl`、`file.upload.whitelist`、`file.upload.maxSize`、`pda.scan.enabled`、`sys.user.password.maxRetryCount` 等安全、文件和集成类配置；但这些 key 多数没有进入运行链路。2026-06-24 重新复核运行态后，当前 `BossERP_stock_state_75c59ee.sys_config` 已回收到 8 条内置参数，上述安全/文件/审计/PDA key 不再出现在当前参数页。
- 证据：当前运行态 `sys_config` 仅有 `sys.index.skinName`、`sys.user.initPassword`、`sys.index.sideTheme`、`sys.account.registerUser`、`sys.login.blackIPList`、`sys.account.initPasswordModify`、`sys.account.passwordValidateDays`、`sys.account.chrtype` 8 条。源码仍显示：`JwtUtils` 读取环境变量/系统属性而不是数据库 `sys_config`；`SysPasswordService` 使用常量 `PASSWORD_MAX_RETRY_COUNT/PASSWORD_LOCK_TIME`；`FileUploadUtils` 使用代码常量和 `MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION`；`rg` 未找到 `audit.retention.days`、`file.presigned.url.ttl`、`pda.scan.enabled` 的运行引用。因此早前 19 条参数属于旧/非运行态或历史脚本口径，不再作为当前网页端运行态页面证据；当前仍存在的主框架主题参数不生效问题另列 P3-107。
- 影响：如果初始化脚本、历史库或非运行态 schema 再次把这些未接入运行链路的安全/文件/审计参数回灌到当前库，参数页会重新给管理员错误预期，例如修改 JWT 密钥、文件上传白名单或审计保留天数但实际运行不生效。当前报告也需要明确区分“旧口径曾存在”和“当前运行态仍存在”，否则验收会被非运行态数据误导。
- 建议：清理并锁定参数初始化脚本，只保留当前运行代码真实读取的 key；安全密钥、文件策略、审计保留和 PDA 开关如果要重新上线，必须先接入运行代码、缓存刷新、类型校验和回归测试。对于必须来自环境变量/启动参数的安全配置，不应放在普通参数页可编辑，或者应标注为只读说明。

### P2-83 非运行态曾启用钉钉自动同步任务，当前运行态没有该任务但缺少目标预检

- 现象：旧/非运行态口径中曾出现 `钉钉打卡自动同步` 定时任务，目标为 `oaAttendanceSyncTask.syncYesterdayAttendance()`，但当前运行态 `BossERP_stock_state_75c59ee` 只有 3 条停用的 `ryTask.*` 内置任务，没有这条钉钉自动同步任务。源码层面仍没有 `oaAttendanceSyncTask` 或 `syncYesterdayAttendance` 实现，如果后续通过页面或 SQL 重新启用该任务，仍会变成不可执行目标。
- 证据：当前 `sys_job` 只有 `job_id=1/2/3`，目标分别是 `ryTask.ryNoParams`、`ryTask.ryParams('ry')`、`ryTask.ryMultipleParams(...)`，状态均为 `1` 停用；`sys_job_log=0`，`QRTZ_JOB_DETAILS/QRTZ_TRIGGERS=0`。全仓 `rg "oaAttendanceSyncTask|syncYesterdayAttendance|AttendanceSync|DingtalkSync"` 在当前工程除本报告外无命中，`find erp-modules erp-api erp-common -iname '*Attendance*Sync*' -o -iname '*Dingtalk*Sync*'` 无结果。任务执行工具 `JobInvokeUtil` 会按 Bean 名找目标方法，找不到时才在执行后写失败日志。
- 影响：当前运行态不会自动触发这条不存在的钉钉任务；但定时任务页面/接口允许配置 invoke target，且缺少保存时 Bean/方法预检。多账号多店铺考勤链路一旦重新插入或启用旧任务，管理员会看到“任务已启用”，实际运行时才失败，无法把钉钉原始数据转成正式考勤。
- 建议：定时任务新增/修改/启用时做目标方法预检：校验 Bean 存在、方法存在、白名单通过，并在列表显示“最近运行时间/最近结果”。钉钉自动同步若仍要交付，需要先补齐 `oaAttendanceSyncTask.syncYesterdayAttendance()`、自动同步日志、失败告警和原始数据转正式考勤链路；未交付前应阻止保存这类不存在目标。

### P2-84 单号规则表与实际单号生成逻辑脱节，销售单前缀已经从 `SO` 变成 `OB`

- 现象：数据库里有 `sys_code_rule` 配置采购、销售、调拨单号规则，但当前进销存源码完全不读取这张表，而是使用 `inv_number_sequence` 加服务代码里的硬编码前缀。管理员或运维如果按 `sys_code_rule` 理解单号规则，会得到和实际新增单据不同的结果。
- 证据：MySQL 当前 `sys_code_rule` 有 3 条：采购单 `PO`、销售单 `SO`、调拨单 `TF`，`seq_length=5`；全仓 `rg` 未发现 `sys_code_rule`、`CodeRule` 或编号规则服务引用。当前源码通过 `InvNumberSequenceMapper.xml` 读写 `inv_number_sequence`，并在 `InvPurchaseServiceImpl`、`InvSalesServiceImpl`、`InvTransferServiceImpl`、`InvStockCheckServiceImpl`、`InvDeliveryNoticeServiceImpl` 等服务内传入硬编码前缀，最终格式化为 4 位流水。`InvSalesServiceImpl` 新建销售单使用 `generateOrderNo("OB")`，而当前正式销售单里既有历史 `SO202604280001/SO202605060001`，也有当前 `OB202605180001`、`OB202605220002`。
- 影响：销售、采购、调拨和发货通知的单号规则没有统一权威来源；同一业务类型在页面和导出中可能出现前缀切换，外部对账或人工识别会混乱。`sys_code_rule` 看似是可配置规则，但没有前端入口也不生效，后续如果新增“编号规则管理”页面，可能会误改一套无效配置。
- 建议：明确单号规则的唯一来源。若采用 `sys_code_rule`，应让所有单号生成服务读取该表，并补齐电脑端只读/可维护页面、长度校验、前缀冲突校验和历史前缀说明；若继续采用 `inv_number_sequence`，应删除或归档 `sys_code_rule`，并在单号审计页解释历史 `SO` 与当前 `OB` 的切换。

### P2-85 仓库主数据存在 `sys_dept` 和 `inv_warehouse` 双轨，实际运行只读组织表

- 现象：当前数据库存在单独的 `inv_warehouse` 仓库主数据表，且已有 2 条仓库记录；但电脑端仓库选择、库存、发货、盘点和报表源码都以 `sys_dept.dept_type='WAREHOUSE'` 作为仓库来源，没有读取 `inv_warehouse`。仓库编码、仓库类型、默认仓库、负责人等字段虽然落库，但当前页面和接口不生效。
- 证据：MySQL 当前 `inv_warehouse` 两条记录分别为 `200 / DEPT_200 / 123 / shop_dept_id=101 深圳总公司`、`201 / DEPT_201 / 仓库 / shop_dept_id=100 总部`；对应 `sys_dept` 中 `200/201` 也是 `WAREHOUSE` 节点。`SysDeptMapper.xml` 的 `selectWarehouseList` 直接查询 `sys_dept`，条件是 `dept_type='WAREHOUSE'`；库存、库存流水、发货通知、盘点和报表 mapper 都通过 `left join sys_dept warehouse_dept on warehouse_dept.dept_id = ...warehouse_id` 取仓库名称。全仓 `rg "inv_warehouse|warehouse_code|warehouse_type"` 未发现业务代码读取 `inv_warehouse`。
- 影响：当前仓库有两套看似权威的数据：组织树里的仓库节点才是真正生效来源，`inv_warehouse` 只是无人使用的同步残留。后续如果管理员修改部门仓库名称、迁移仓库父级或新增仓库，`inv_warehouse` 未同步时会产生脏数据；如果未来新功能改读 `inv_warehouse`，又会和现有库存/单据使用的 `sys_dept` 口径不一致。
- 建议：明确仓库主数据权威来源。若继续以 `sys_dept` 为仓库模型，应删除或归档 `inv_warehouse`，或至少加同步任务和一致性校验并标记为派生表；若 `inv_warehouse` 才是目标模型，应补齐仓库维护页面/API，把仓库选择器、库存/发货/盘点/报表 mapper 统一改为读取这张表，并保留与组织树的外键关系。

### P2-86 个人改密和管理员重置密码不会让同账号其他会话失效

- 现象：个人中心“修改密码”和用户管理“重置密码”都会更新数据库密码，但没有按用户维度清理 Redis 中该账号的其他登录 token；个人改密后只刷新当前会话缓存，管理员重置密码也不会踢掉该用户已登录会话。
- 证据：个人中心前端 `erp-ui/src/views/system/user/profile/resetPwd.vue:56-61` 成功后只提示“修改成功”，没有退出登录或重新登录提示；`SysProfileController.updatePwd` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:137-142` 调用 `resetUserPwd` 后只更新当前 `loginUser` 并 `tokenService.setLoginUser(loginUser)`。`TokenService.setLoginUser` 在 `erp-common/erp-common-security/src/main/java/com/erp/common/security/service/TokenService.java:131-177` 只按当前 `loginUser.getToken()` 刷新 `login_tokens:{token}`，`delLoginUser` 也只按传入 token 删除。管理员重置密码接口 `SysUserController.resetPwd` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:344-358` 仅调用 `userService.resetPwd(user)`，`SysUserServiceImpl.resetPwd` 在 `:388-391` 只更新密码。在线用户强退接口也只按指定 token 删除，见 `SysUserOnlineController.forceLogout` `:75-80`。当前 Redis 复核 `login_tokens:*` 为 1 条，本轮未构造多会话写入。
- 影响：如果账号密码泄露后用户自行改密，或管理员发现风险后重置密码，旧设备/旧浏览器上的已登录 token 仍可能继续访问直到过期或被人工强退。多账号多店铺、门店共用电脑和多人轮班场景下，用户会以为“改了密码就安全”，实际并没有关闭其他会话。
- 建议：密码变更成功后按 `userId` 清理该用户除当前会话外的所有 `login_tokens:*`，或强制当前会话也重新登录；管理员重置密码时应默认踢掉该用户所有在线会话，并在操作日志中记录被清理的会话数量。个人中心页面应提示“修改密码后其他设备将退出登录”或提供可选勾选项。

### P2-125 角色、菜单和店铺授权变更后不刷新在线 token，撤权最长可延迟到会话过期

- 现象：用户登录时的角色和权限集合会被写入 Redis `login_tokens:*`，后端接口鉴权直接读取 token 中缓存的 `permissions/roles`。角色菜单、角色成员、用户角色或店铺授权变更后，系统不会按受影响用户刷新或删除在线 token；已登录用户可能继续持有旧权限，直到前端重新调用 `getInfo`、token 过期或被人工强退。
- 证据：`TokenService.createToken()` 把 `LoginUser` 写入 `login_tokens:{token}`，默认有效期 `CacheConstants.EXPIRATION=720` 分钟；`HeaderInterceptor` 每次请求从 Redis 取 `LoginUser` 放入安全上下文，`AuthLogic.checkPermi/getPermiList` 使用缓存中的 `loginUser.getPermissions()` 鉴权。`SysRoleController.edit/dataScope/authUser/*`、`SysUserController.insertAuthRole`、`SysUserShopController.save` 等授权写接口只改数据库，没有调用 token 刷新或失效；`SysUserController.getInfo()` 虽会重新计算权限并刷新当前 token，但只有前端主动调用时才发生，而且只更新 `permissions`，不更新缓存里的 `roles`。当前 Redis 只读复核仍有 16 个 `login_tokens:*` 在线会话。
- 影响：管理员在角色页撤销菜单权限、取消用户角色、收窄店铺/仓库授权后，被撤权用户的旧浏览器会话仍可能继续调用原先已授权的接口；多账号多店铺场景下，这会让“权限变更已保存”的页面反馈和实际后端鉴权存在最长 12 小时的窗口差。店铺授权变化还会叠加前端已选择组织缓存，旧会话可能继续按旧组织上下文操作或看到不该再访问的菜单。
- 建议：权限、角色、菜单、数据范围和店铺/仓库授权变更后，按受影响 `user_id` 查找并清理或刷新所有在线 token；高风险撤权默认踢下线并要求重新登录，普通扩权可提示用户刷新权限。`getInfo()` 刷新缓存时同步更新 `roles` 和 `permissions`，并在授权保存结果中提示“已使 N 个在线会话重新登录/刷新权限”。相关清理动作要进入专用权限变更审计。

### P2-87 菜单管理新增/修改不校验组件路径和权限实现，能保存不可交付路由

- 现象：菜单管理页允许管理员直接填写页面组件路径和权限标识，但保存链路只做名称、外链地址、路由配置等通用校验，不检查组件文件是否存在，也不检查权限标识是否真的被前端按钮或后端接口使用。
- 证据：菜单表单在 `erp-ui/src/views/system/menu/index.vue:230-249` 用普通输入框填写 `component` 和 `perms`，`submitForm` 在 `:474-490` 只调用 `addMenu/updateMenu`。`SysMenuController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMenuController.java:91-137` 只校验菜单名称唯一、外链 `http(s)`、父级不能为自己和路由配置唯一；`SysMenuServiceImpl.insertMenu/updateMenu` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java:319-334` 直接写库，`buildMenus` 在 `:183-230` 直接把数据库 `component` 转成前端路由。2026-06-25 当前带组件路径的运行态页面菜单已复核能命中前端文件，但旧/非运行态曾保存 11 个工作流页面、1 个薪资模板页面和 1 个考勤 Excel 导入页面这类当前工程缺实现的组件路径，说明保存链路本身仍没有交付一致性兜底。
- 影响：拥有菜单管理写权限的账号可以把未交付页面保存为启用菜单，或者把按钮权限写成源码没有接入的字符串；角色授权页随后会把这些配置当成真实能力展示。多账号多店铺验收时，管理员以为“菜单已启用/权限已授权”，实际用户进入后可能白屏、404、按钮隐藏或接口 403。
- 建议：菜单新增/修改/启用时接入交付一致性校验：页面菜单的 `component` 必须命中前端组件清单，外链和 `Layout/ParentView/InnerLink` 走白名单；按钮权限必须命中源码权限清单或受控权限目录。无法交付的菜单应只能保存为草稿/停用，并在角色授权树和菜单列表显示“缺组件/缺接口/待开发”状态。

### P2-88 通知公告富文本内容缺少后端过滤并用 `v-html` 渲染，存在全员公告脚本风险

- 现象：通知公告内容使用富文本编辑器保存 HTML，详情页直接用 `v-html` 渲染；后端只对公告标题做 `@Xss` 校验，没有对 `noticeContent` 做服务端过滤或白名单净化，且网关全局 XSS 过滤明确排除了 `/system/notice`。当前运行态里 `普通角色 common` 和 `总经理 zjl` 都持有公告新增、修改、删除、查询和列表权限，其中 `ry` 绑定总经理角色，因此公告写权限并非只限超级管理员。
- 证据：公告编辑弹窗 `erp-ui/src/views/system/notice/index.vue:169-172` 使用 `<editor v-model="form.noticeContent">`；富文本组件 `erp-ui/src/components/Editor/index.vue:112-148` 使用 Quill 的 `dangerouslyPasteHTML` 并把编辑器 `innerHTML` 作为值提交。后端 `SysNotice.getNoticeTitle` 在 `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysNotice.java:54-57` 有 `@Xss` 和长度校验，但 `getNoticeContent` 在 `:72-79` 没有对应校验；`SysNoticeServiceImpl.insertNotice/updateNotice` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysNoticeServiceImpl.java:52-66` 直接转 Mapper，`SysNoticeMapper.xml:46-77` 直接插入/更新 `notice_content`。网关 `erp-gateway/src/main/resources/bootstrap.yml:139-142` 虽开启 `security.xss.enabled=true`，但 `excludeUrls` 配了 `/system/notice`；`XssFilter` 在 `erp-gateway/src/main/java/com/erp/gateway/filter/XssFilter.java:50-66` 对 GET/DELETE、非 JSON 和命中排除 URL 的请求都不清洗，只在未排除 JSON 请求体上执行 `EscapeUtil.clean`。公告详情 `erp-ui/src/layout/components/HeaderNotice/DetailView.vue:44-45` 使用 `v-html="detail.noticeContent"` 展示内容。2026-06-24 MySQL 复核当前 `sys_notice=0`、`notice_content` 为 `longblob`，`system:notice:add/edit/list/query/remove` 已授给 `common / 普通角色` 和 `zjl / 总经理`，有效用户 `ry` 绑定 `zjl`；本轮 `rg "DOMPurify|sanitize-html|filterXSS|sanitizeHtml|xss\\(" erp-ui/package.json erp-ui/package-lock.json erp-ui/src` 无命中，前端没有可见统一 HTML sanitizer。
- 影响：公告是顶部全员入口，一旦富文本内容中混入恶意脚本或危险链接，所有打开公告详情的登录用户都可能受影响；多账号多店铺场景下，这会把一个后台内容维护问题扩散到门店、仓库和运营账号。
- 建议：公告内容保存前后端都做 HTML 白名单净化，至少限制 `script/on*` 事件、危险协议、iframe/video 等高风险标签；详情页渲染前也应使用统一 sanitizer，并重新评估 `/system/notice` 从网关 XSS 排除列表放行的范围。公告富文本可保留常规格式、图片和链接，但链接应限制协议并加安全属性；把公告新增/修改权限限制在少数可信角色并记录发布审计。

### P2-128 顶部公告详情要求系统公告查询权限，多数业务账号可能只能看到标题和未读数

- 现象：顶部导航的公告入口对所有登录用户展示，并通过只要求登录的 `listTop` 接口返回公告标题和未读数；但点击公告详情会调用 `/system/notice/{noticeId}`，该接口要求 `system:notice:query`。当前多数门店、仓库、运营和驻店账号没有这个系统管理权限，后续只要发布正常公告，这些账号可能能看到公告标题、未读数并被本地标记已读，却打不开正文。
- 证据：桌面导航固定挂载 `HeaderNotice`，见 `erp-ui/src/layout/components/Navbar.vue:35-37`；顶部公告组件 `loadNoticeTop()` 调 `/system/notice/listTop`，点击条目后 `previewNotice()` 再调用详情抽屉 `open(item.noticeId)`，见 `erp-ui/src/layout/components/HeaderNotice/index.vue:69-88`。详情抽屉在只有 ID 时调用 `getNotice(id)`，见 `HeaderNotice/DetailView.vue:78-106`；后端 `SysNoticeController.listTop` 只要求 `@RequiresLogin`，但 `getInfo` 标注 `@RequiresPermissions("system:notice:query")`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:55-63`、`:89-103`。当前 `sys_notice=0`，本轮未构造公告写入；权限矩阵显示显式拥有 `system:notice:query` 的只有总经理角色 `zjl`，店长 `dz`、仓库管理员 `ck`、运营经理 `yyjl`、驻店经理 `zdjl` 等 23 个有效业务账号没有该权限。另一个方向上，管理列表接口又用 `system:notice:list` 批量返回 `cast(notice_content as char) as notice_content`，见 `SysNoticeMapper.xml:20-44`；管理页标题详情如果行数据已有 `noticeContent` 会直接使用列表对象而不再调用查询接口，见 `HeaderNotice/DetailView.vue:81-92`。这让 `system:notice:list` 和 `system:notice:query` 的正文边界同时存在“顶部详情过严、管理列表过宽”的不一致。
- 影响：公告被设计成全员触达入口，但正文读取却绑定系统公告管理查询权限，会让普通门店/仓库账号在有公告时遇到“看得到铃铛和标题，打不开内容”的断裂体验；如果点击时先标记已读，用户还可能错过真正内容。相反，后台列表接口批量返回正文又削弱了详情查询权限的意义，也会让富文本长内容在列表翻页时被无必要传输。
- 建议：把公告“阅读正文”和“后台管理查询”拆开。顶部公告详情应使用只要求登录且按公告可见范围校验的只读详情接口；后台管理的 `system:notice:query` 只控制管理页编辑/审计详情。列表接口不要返回 `notice_content`，只返回标题、类型、状态、创建人、创建时间和摘要；详情接口再按场景返回正文并记录阅读状态。

### P2-129 店铺授权页搜索或重置后不清空当前用户，可能继续保存上一用户授权

- 现象：店铺授权页左侧选择用户后，右侧组织树和“保存”按钮会绑定到 `currentUser`。但管理员在同一页重新搜索、重置筛选或翻页导致左侧用户列表刷新时，页面没有同步清空 `currentUser/selectedShopIds/preservedShopIds`，右侧面板也不显示当前目标用户账号或昵称，只保留通用文案“勾选该用户可管理的门店或仓库”。因此管理员可能以为正在处理搜索后的用户列表，却实际继续修改并保存上一位已选用户的店铺/仓库授权。
- 证据：店铺授权页右侧标题区只在 `currentUser` 存在时显示通用提示并启用保存按钮，未展示 `userId/userName/nickName`，见 `erp-ui/src/views/system/shop/index.vue:72-78`。`getList()` 在 `:169-180` 只刷新 `userList/total`、批量加载授权标签并尝试聚焦路由用户；`handleQuery()` 和 `resetQuery()` 在 `:201-209` 只是设置页码、重置查询表单并重新拉列表，没有调用 `loadCurrentUserShop(null)` 或校验当前选中用户是否还在新列表中。只有表格 `current-change` 触发 `handleCurrentUserChange()` 时才会加载新用户授权，见 `:233-245`；保存时 `submitShopScope()` 仍直接使用缓存的 `this.currentUser.userId` 调 `updateUserShop`，见 `:298-315`。后端 `SysUserShopController.save()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java:84-91` 按路径 `userId` 保存，因此前端缓存目标是谁，最终就会写到谁。
- 影响：P2-118 记录的是从用户列表跳转到 `/system/shop?userId=...` 时路由 query 不刷新；本问题发生在店铺授权页内部的搜索/重置/列表刷新流程。多账号多店铺授权属于高风险权限动作，如果目标用户身份没有被显式展示且列表上下文变化后仍允许保存，管理员很容易把授权写到上一位用户，造成错误开店、错误仓库访问或误撤授权。
- 建议：店铺授权页每次查询、重置、翻页或列表刷新后，应确认 `currentUser` 是否仍在当前结果集中；不在时立即清空右侧树、禁用保存并提示重新选择用户。右侧面板必须展示目标用户的账号、昵称、用户 ID 和当前组织摘要，保存确认框也要展示 before/after 授权差异。回归用例覆盖“选择用户 A -> 修改筛选条件只显示用户 B -> 不点击 B 直接保存”，确认前端会阻断或保存请求目标与可见当前行一致。

### P2-132 选择组织取消只清 token 不清角色权限，新账号可能复用上一账号前端身份和菜单

- 现象：用户登录后进入选择组织页，如果点击取消，页面执行前端登出 `FedLogOut` 并回到登录页。但 `FedLogOut` 只清 token，不清 Vuex 中的 `roles/permissions`、用户身份或已生成动态路由。随后同一个 SPA 会话里另一个账号登录时，路由守卫会因为 `roles.length !== 0` 跳过 `GetInfo/GenerateRoutes`，可能继续使用上一账号的前端菜单、按钮权限、头像/昵称等状态。
- 证据：选择组织页 `handleCancel()` 在 `erp-ui/src/views/select-shop/index.vue:376-384` 清除选中组织后调用 `this.$store.dispatch("FedLogOut")` 并跳转 `/login`。`FedLogOut` 在 `erp-ui/src/store/modules/user.js:157-165` 只提交 `SET_TOKEN` 和 `removeToken()`；完整 `LogOut` 在同文件 `:145-155` 也只额外清 `roles/permissions`，两者都没有清用户 ID、昵称、头像、部门和权限路由。登录页 `erp-ui/src/views/login.vue:273-295` 登录成功后直接跳 `/select-shop` 或原 redirect；`Login` action 在 `user.js:75-98` 设置新 token 前只清选中组织和重置提醒，不清旧角色权限。路由守卫在 `erp-ui/src/permission.js:107-145` 只有 `store.getters.roles.length === 0` 时才调用 `GetInfo` 和 `GenerateRoutes`；动态路由生成逻辑在 `erp-ui/src/store/modules/permission.js:31-49` 依赖这一步重新拉取。
- 影响：门店/仓库共用电脑中，A 用户取消组织选择后 B 用户登录，B 看到或可达的前端入口可能仍按 A 的角色、按钮权限和动态路由渲染；后端仍会按 B 的 token 鉴权，所以多数越权请求会失败，但页面身份、菜单、按钮、空态和可达路由会误导用户和巡检。若 B 权限更高，旧低权限状态还可能隐藏 B 应有入口。多账号验收最依赖“登录即刷新身份和权限”，该流程会让前端状态与新 token 不一致。
- 建议：`FedLogOut` 应与完整登出统一清空 `roles/permissions`、用户身份、权限路由和页签缓存，或选择组织取消直接调用完整 `LogOut`。`Login` 成功前也应先重置用户与权限模块，确保每次新 token 都强制执行 `GetInfo/GenerateRoutes`。回归用例覆盖“账号 A 登录到选择组织页取消 -> 不刷新页面账号 B 登录”，验证顶部身份、菜单、按钮、动态路由和接口 token 均为 B。

### P2-133 OA 页面没有纳入组织选择守卫，直连/新标签页会出现全局数据或接口错误

- 现象：路由守卫只把 `/inventory/`、部分 `/cangku/*` 和 `/mobile/` 判定为需要已验证组织上下文；`/oa/todo`、`/oa/done`、`/oa/attendance`、`/oa/salary`、`/oa/labor-contract`、`/oa/fixed-asset/*` 不在守卫范围。由于选中组织保存在 `sessionStorage`，同一账号在新标签页、刷新异常恢复或手动直连 OA 页面时可能没有 `Dept-NumId`。普通业务账号会在页面内收到“请先选择店铺或仓库”接口错误；管理员不带组织头访问部分 OA 列表时反而会落到全局范围。
- 证据：`erp-ui/src/permission.js:57-63` 的 `inventoryContextPrefixes` 只有 `/inventory/`、`/cangku/stock`、`/cangku/transfer`、`/cangku/transfer-records`、`/mobile/`；`shouldSelectShop()` 在 `:79-87` 只对这些路径或移动视口要求选择组织。组织上下文由 `shopContext.js:1-4` 的 `selected_dept_*` sessionStorage key 保存，请求拦截器只在 `getSelectedInventoryDeptId()` 有值时写入 `Dept-NumId`，见 `request.js:36-41`。OA Controller 都从请求头解析组织，例如 `OaBaseController.resolveShopDeptId()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaBaseController.java:9-12` 读取 `Dept-NumId`；考勤、工资、采购、劳动合同、固定资产 Controller 在各自列表、详情、保存、导出接口中继续传入该值。`AbstractShopScopeService.requireSelectedShopDept()` 在 `erp-common/erp-common-security/src/main/java/com/erp/common/security/shop/AbstractShopScopeService.java:54-60` 缺头时抛“请先选择店铺或仓库”，但 `resolveScopeDeptIds()` 在同文件 `:23-31` 对管理员且未选组织时返回空范围，导致 `appendShopScope()` 不加组织过滤。2026-06-25 只读接口验证：`admin` token 请求 `GET http://127.0.0.1:8080/oa/attendance/my?pageNum=1&pageSize=1` 不带 `Dept-NumId` 返回 `total=1` 且行数据 `shopDeptId=104 / 仓库`；门店账号 `qa144822s` 同请求不带头返回 `{"msg":"请先选择店铺或仓库","code":500}`，带 `Dept-NumId: 103` 则返回 `{"total":0,"code":200,"msg":"查询成功"}`。
- 影响：OA 是多账号多店铺的核心流程之一，待办/已办、考勤、工资、合同和固定资产都以当前门店或仓库为业务边界。缺少统一守卫会让用户从首页入口看起来正常，但直连、新标签页、刷新恢复或外部链接进入 OA 时行为不一致：普通账号看到接口错误而不是被引导选择组织，管理员可能在没有明确“全局范围”的情况下看到跨组织数据。审计时也会出现“页面可打开但数据范围不是当前组织”的误判。
- 建议：把所有依赖 `Dept-NumId` 的 OA 路由纳入统一组织选择守卫，或者为 OA 建立独立 `oaContextPrefixes` 并按页面语义校验 STORE/WAREHOUSE/全局范围。管理员若允许全局查看，应在页面顶部明确显示“全局范围”并要求显式切换，不应把缺少组织头静默解释为全局。普通账号缺组织上下文时应在进入页面前跳转 `/select-shop?redirect=...`，而不是在列表加载后暴露后端错误。回归用例覆盖新标签页直连 `/oa/attendance`、`/oa/salary`、`/oa/todo` 和固定资产页面，分别验证管理员、门店、仓库账号的数据范围和提示。

### P2-89 内部服务 `@InnerAuth` 只校验请求头，签名密钥配置没有接入

- 现象：系统参数里有“内部调用签名密钥” `security.inner.sign.key`，但当前 `@InnerAuth` 实际只校验请求头 `from-source=inner`，没有校验签名、时间戳、nonce 或密钥。网关会清除外部请求中的 `from-source` 头，但服务端内部接口自身的安全边界仍依赖可伪造的普通请求头和网络隔离。
- 证据：MySQL 复核 `security.inner.sign.key` 当前为空，备注为“服务间@InnerAuth调用签名密钥”。`InnerAuthAspect` 在 `erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/InnerAuthAspect.java:24-39` 只读取 `SecurityConstants.FROM_SOURCE` 并判断是否等于 `SecurityConstants.INNER`，随后可选校验 `user_id/username` 头；`SecurityConstants` 中这两个常量只是 `from-source` 和 `inner`。Feign 客户端 `RemoteUserService`、`RemoteConfigService`、`RemoteLogService` 调用内部接口时也只是传入 `SecurityConstants.INNER`。网关 `AuthFilter` 在 `erp-gateway/src/main/java/com/erp/gateway/filter/AuthFilter.java:87-92` 会写入用户头并移除 `from-source`，但没有给内部接口追加签名校验。受 `@InnerAuth` 保护的接口包括用户信息查询、注册、登录信息记录、参数读取和操作/登录日志写入。
- 影响：通过正常网关访问时，清除 `from-source` 可以挡住一部分外部伪造；但如果系统服务端口、内网入口、测试环境或容器网络被误暴露，攻击者只要带上 `from-source: inner` 就可能访问内部接口。多账号多店铺场景下，内部用户信息、权限集合、参数读取和日志写入都属于敏感能力，不能只依赖“没人能直连服务”作为边界。
- 建议：把 `security.inner.sign.key` 接入 `@InnerAuth`：内部调用带时间戳、nonce、请求摘要和 HMAC 签名，服务端校验签名、时钟窗口和重放；密钥必须来自环境/密钥管理或受控配置并禁止为空。网关继续清理外部 `from-source`，同时服务网络层限制业务服务端口只允许网关和注册中心访问；参数页若保留该 key，应显示当前是否已生效和轮换状态。

### P2-90 日志保留/归档口径未进入当前运行态，清空操作仍直接 `truncate` 正式日志

- 现象：当前运行态没有 `sys_audit_archive` 表，也没有 `audit.retention.days` 参数；但操作日志和登录日志页面的“清空”后端仍是直接清空正式日志表，没有归档、保留期或导出证据链。
- 证据：MySQL 精确复核当前 `sys_oper_log=244`，时间范围 `2026-06-04 17:36:09` 至 `2026-06-23 11:23:58`；`sys_logininfor=501`，时间范围 `2026-06-04 12:57:44` 至 `2026-06-25 09:47:27`；`show tables like 'sys_audit_archive'` 返回空，`sys_config` 中也查不到 audit/retention 相关参数。但 `SysOperLogMapper.cleanOperLog` 在 `erp-modules/erp-system/src/main/resources/mapper/system/SysOperLogMapper.xml:82-84` 执行 `truncate table sys_oper_log`，`SysLogininforMapper.cleanLogininfor` 在 `:50-52` 执行 `truncate table sys_logininfor`；对应 Controller 的 `/clean` 接口仍复用 `system:operlog:remove` 和 `system:logininfor:remove` 权限。旧/非运行态曾出现 `sys_audit_archive` 和 `audit.retention.days=365`，不作为当前运行态结论。
- 影响：管理员在电脑端点击“清空”会不可逆地删除登录和操作审计证据，既不生成归档记录，也不保留归档文件路径或记录数量。多账号多店铺场景下，用户授权、菜单变更、合同、销售、库存等操作都依赖日志追责，直接清空会让权限事故和业务争议无法复盘。
- 建议：把“删除/清空”和“归档/保留策略”拆开。归档表和保留参数未交付前，不应在页面或文档暗示自动归档；清空操作应改为高权限二次确认，并先生成归档记录或导出归档文件，记录操作者、时间范围、记录数、存储路径和 hash。长期建议新增自动归档任务，只归档超过保留期的日志，保留摘要查询和恢复/下载入口。

### P2-91 单据状态审计表和触发器在运行态存在，但电脑端仍没有入口

- 现象：运行态 `BossERP_stock_state_75c59ee` 已存在 `inv_document_status_log` 和 6 个状态触发器，源码审计接口读取的也是这张表；但电脑网页端仍没有单据状态审计页面、菜单入口或单据详情时间线。非运行态/旧快照中曾出现 `inv_status_audit` 口径和 `inv_document_status_log` 缺失的结论，已修正为环境漂移风险。
- 证据：MySQL `show tables like 'inv%status%'` 返回 `inv_document_status_log`、`inv_transfer_status_log` 及调拨状态备份表；`inv_document_status_log=2`，两条记录均来自盘点单 `SC202606140001`，状态为 `draft -> checking -> cancelled`。`information_schema.triggers` 当前有 `trg_inv_purchase_order_status_au`、`trg_inv_sales_order_status_au`、`trg_inv_purchase_return_status_au`、`trg_inv_sales_return_status_au`、`trg_inv_stock_check_status_au`、`trg_inv_delivery_notice_status_au`。源码 `InvDocumentStatusLogController` 提供 `/audit/list`，`InvDocumentStatusLogMapper.xml` 直接查询 `inv_document_status_log`；2026-06-24 权限树已回填 `4491 / 状态审计查询 / inv:audit:list`，但它仍是无 path/component 的按钮节点，前端没有 `/audit` 页面、没有 `audit/list` API 调用，也没有单据详情时间线。
- 影响：状态审计数据已经在数据库产生，后端也有查询能力，但业务人员无法在电脑端查看“谁把单据从什么状态改到什么状态”。盘点、采购、销售、退货、发货通知等流程的状态追溯仍依赖数据库查询；如果未来脚本或环境误连非运行态 schema，又可能重新出现缺表/双轨误判。
- 建议：保留 `inv_document_status_log` 作为当前运行态权威状态审计表，并补齐电脑端入口：可以做独立“状态审计”页面，也可以在单据详情里增加状态时间线。当前已回填的 `inv:audit:list` 应挂到真实页面或详情能力，并按组织范围过滤。发布前增加 schema preflight，确认运行服务连接的 schema 同时具备审计表、触发器和可访问入口。

### P2-160 运行态残留 `root` 定义的动态 DDL 存储过程，源码和电脑端没有闭环

- 现象：运行态 schema 除 160 张基础表和 6 个状态审计触发器外，还存在 `add_transfer_approval_column_if_missing`、`add_transfer_approval_index_if_missing` 两个存储过程。它们用于拼接并执行 `ALTER TABLE`，但当前仓库 SQL、Java、前端和报告原有正文都没有创建或调用来源，电脑端也没有任何页面说明或维护这两个数据库对象。
- 证据：MySQL `information_schema.ROUTINES` 显示这两个过程均为 `DEFINER=root@localhost`、`SECURITY_TYPE=DEFINER`、创建时间 `2026-06-06 14:41:21`；参数分别接收表名、列名/索引名和完整列/索引定义。`SHOW CREATE PROCEDURE` 中，列过程会拼接 ``alter table `{p_table_name}` add column {p_column_definition}`` 并 `prepare/execute`，索引过程会拼接 ``alter table `{p_table_name}` add {p_index_definition}`` 并执行。全仓精确检索 `add_transfer_approval_column_if_missing/add_transfer_approval_index_if_missing/CALL add_transfer_approval` 无命中；应用配置仍默认 `MYSQL_USERNAME:root` 连接 `BossERP_stock_state_75c59ee`，当前本机 `root@localhost` 具有 `ALTER` 和 `EXECUTE` 权限。
- 影响：这类迁移辅助对象停留在运行库，会让“数据库内容是否能由源码和迁移脚本复现”变得不确定；后续审计只能从 MySQL 运行态发现它们，无法从代码仓库解释其用途、执行历史或清理标准。若应用库账号继续使用 root 或保留 DDL/EXECUTE 权限，SQL 注入、误用脚本或人工误调用都可能绕过版本化迁移直接改表，破坏多账号多店铺验收依赖的稳定 schema。
- 建议：把调拨审批相关 DDL 迁移收口到版本化 SQL 或迁移工具中，辅助存储过程只允许在受控迁移窗口临时创建并在完成后删除；如果必须长期保留，应纳入数据库对象清单，记录 owner、用途、调用权限、校验 SQL 和回滚方案。应用数据库账号不能使用 `root`，应移除 DDL 和无关 `EXECUTE` 权限；发布前的 schema preflight 应同时校验表、触发器、视图、存储过程和函数清单。

### P2-75 权限树在两次运行态复核间发生回填，权限审计口径不稳定

- 现象：2026-06-23 复核时，报表中心、单据审计、OA 工资、调拨审批规则、调拨记录和调拨审批相关权限点一度在运行态权限树中缺失；2026-06-24 再次复核时，这些权限点已重新出现在 `sys_menu`。同一运行态库在审计期间发生菜单/权限回填，导致“页面能否授权、角色是否拥有权限”的结论随时间变化。
- 证据：2026-06-24 MySQL 复核当前 `sys_menu`，已存在 `4470 / inv:report:list`、`4491 / inv:audit:list`、`3210-3214 / oa:salary:list/query/calculate/config/export`、`4410-4414 / inv:transfer:rule:list/add/edit/query/remove`、`4400/4465` 调拨记录及其查询/导出权限、`4109 / inv:transfer:approve`。这些权限仍被前端和后端使用：报表静态路由和 `InvReportController` 用 `inv:report:list`，单据状态审计 Controller 用 `inv:audit:list`，调拨规则/记录/审批和 OA 工资页面、Controller 也继续使用对应权限。2026-06-23 的“启用菜单数为 0、角色授权为 0”结论不再代表当前状态，只能作为权限树漂移证据。
- 影响：多账号多店铺验收依赖角色授权矩阵。如果权限树可以在未记录迁移版本、未固定快照的情况下回填，审计结论会出现前后矛盾：前一轮判定普通角色无法授权，后一轮可能已经可授权；同时旧浏览器会话、运行 jar、前端 dev server 和直查 SQL 可能看到不同权限模型。
- 建议：冻结并标注权限树版本，发布/验收前记录 `sys_menu` 行数、权限码 checksum、迁移脚本版本和运行 jar 版本。源码中出现的 `v-hasPermi`/静态路由权限/`@RequiresPermissions` 必须由启动或验收脚本校验到可授权节点；权限树发生回填时，应同步输出迁移说明和角色授权差异，而不是让审计人员靠重复 SQL 发现变化。

### P2-76 文件上传/删除权限未进入权限树，劳动合同模板和企业章非超级管理员无法维护

- 现象：文件服务后端要求 `file:upload` 和 `file:delete` 权限，但当前数据库权限树没有任何 `file:%` 权限节点。电脑端劳动合同的模板文件上传和企业章图片上传都复用该文件服务，因此普通业务角色即使被授予劳动合同模板维护权限，也无法通过角色管理补齐上传/删除所需权限。
- 证据：MySQL 查询 `sys_menu where perms like 'file:%'` 返回 0 行；`sys_menu` 中劳动合同权限已启用 `oa:laborContract:template:add`，但这只控制页面“保存/新增模板/企业章”按钮。前端 `erp-ui/src/views/oa/laborContract/index.vue:316-338` 在模板弹窗使用 `<file-upload>`、企业章弹窗使用 `<image-upload>`；`erp-ui/src/components/FileUpload/index.vue:55-58` 默认上传地址为 `/file/upload`，删除动作调用 `erp-ui/src/api/system/file.js:3-9` 的 `/file/delete`。后端 `erp-modules/erp-file/src/main/java/com/erp/file/controller/SysFileController.java:33-58` 分别用 `@RequiresPermissions("file:upload")` 和 `@RequiresPermissions("file:delete")` 保护上传、删除接口。
- 影响：多账号场景下，除超级管理员外的劳动合同管理员无法完整维护合同模板和企业章；页面上看似有模板维护权限，实际上传时会被后端 403 拦截。删除文件失败还会被上传组件吞掉错误并继续从表单值移除，容易形成数据库记录、表单状态和真实文件存储不一致。
- 建议：把文件上传和删除纳入权限模型：要么新增可授权的 `file:upload`、`file:delete` 按钮权限并绑定到需要附件能力的业务角色；要么按业务模块改成 `oa:laborContract:template:add` 等模块权限可上传本模块文件。删除远端文件失败时前端不要静默移除表单值，应提示失败并保留原文件引用。

### P2-126 通用文件上传全部落到公开白名单，劳动合同模板和企业章可无登录直连下载

- 现象：通用文件服务把上传文件统一写入 `public` 目录并返回 `/file/public/...` URL；当前劳动合同模板和企业章图片都保存了这种公开 URL。网关又把 `/file/public/**` 放在免登录白名单里，因此拿到链接的人不需要登录、不需要组织或合同权限，就能直接下载合同模板 docx 和企业章图片。
- 证据：当前运行态 `oa_labor_contract_template` 2 条启用模板的 `template_file_url` 均为 `http://localhost:8080/file/public/...docx`，`oa_company_seal_config` 1 条启用企业章的 `seal_image_url` 为 `http://localhost:8080/file/public/...png`。`LocalSysFileServiceImpl.uploadFile` 在 `erp-modules/erp-file/src/main/java/com/erp/file/service/LocalSysFileServiceImpl.java:49-53` 写入 `${file.path}/public` 并返回 `${file.domain}${file.prefix}/public...`；`ResourcesConfig` 在 `erp-modules/erp-file/src/main/java/com/erp/file/config/ResourcesConfig.java:30-49` 把 `/file/public/**` 映射为静态资源并允许跨域 GET；`erp-gateway/src/main/resources/bootstrap.yml:143-150` 将 `/file/public/**` 加入 `security.ignore.whites`，`AuthFilter` 在 `erp-gateway/src/main/java/com/erp/gateway/filter/AuthFilter.java:49-54` 命中白名单后直接放行。当前配置 `referer.enabled=false`，即使启用，`RefererFilter` 在 `erp-modules/erp-file/src/main/java/com/erp/file/filter/RefererFilter.java:57-64` 也允许无 Referer 的直接打开。无 Authorization 的 `curl` 直连企业章图片和合同模板 URL 分别返回 `200 image/png 55761`、`200 application/vnd.openxmlformats-officedocument.wordprocessingml.document 119726`。
- 影响：合同模板和企业章属于劳动合同签署链路的业务资料，链接一旦出现在浏览器网络记录、操作日志、数据库导出或聊天转发中，就可以被无账号人员直接访问；多店铺场景也无法按当前组织、角色或合同权限限制下载。文件服务注释写着“业务私有文件必须走鉴权接口下载”，但通用上传只有上传/删除接口，没有私有下载接口，实际落地与设计边界相反。
- 建议：把文件类型分成公开资源和业务私有资源。劳动合同模板、企业章、合同附件等应存入私有目录或对象桶，只通过业务下载接口按登录用户、组织、角色和业务引用校验后返回，必要时使用短期签名 URL；`/file/public/**` 只保留真正可公开的头像/图片资源。模板和企业章保存时应记录稳定 `file_id`、文件分类、hash、上传人和引用关系，下载/预览写入日志，避免把业务资料长期暴露在公开白名单下。

### P2-127 登录页“记住密码”把可逆密码写入 Cookie，门店共用电脑容易串号

- 现象：登录页的“记住密码”会把账号和加密后的密码写入浏览器 Cookie 30 天，下次打开登录页再用前端私钥解密回填。这个实现不是只记账号，而是在客户端长期保存可还原密码。
- 证据：`erp-ui/src/views/login.vue:61-63` 和 `:140-142` 分别在桌面/移动登录表单显示“记住密码”；`:249-259` 从 Cookie 读取 `username/password/rememberMe` 并对 `password` 执行 `decrypt(password)` 后回填；`:285-288` 勾选后写入 `Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 })`。`erp-ui/src/utils/jsencrypt.js:5-15` 把 RSA 公钥和私钥都写在前端源码里，`:18-29` 同时提供 `encrypt/decrypt`，所以 Cookie 中密码只是可逆存储，不是不可还原凭据。
- 影响：门店、仓库或前台共用电脑时，上一个账号勾选“记住密码”后，下一个人打开登录页会自动带出上一账号密码，容易误登录、串用账号或把操作日志记到错误用户。任何能读取该 Cookie 和前端代码的人也能还原密码；浏览器插件、XSS 或同机调试都会放大风险。当前登录页还默认填充 `admin/admin123`，与多账号责任隔离目标不一致。
- 建议：生产环境不要在前端保存可逆密码；改成“记住账号/最近门店”，密码交给浏览器原生密码管理器或服务端安全会话。若需要长期登录，应使用服务端 refresh/session，Cookie 设置 `HttpOnly/Secure/SameSite` 并提供设备管理、退出所有设备和切换账号清理。开发便利的默认账号和记住密码应由环境变量控制，生产构建关闭。

### P2-77 成本权限已回填为 `inv:cost:view`，但全局成本权限挂在商品菜单下且授权语义不清

- 现象：2026-06-24 当前运行态权限树已不再是早前的 `inv:stock:cost:view`、`inv:product:cost:view`、`inv:export:sensitive` 三套口径，而是只保留 `4510 / 成本查看 / inv:cost:view`。但该权限挂在 `4001 商品管理` 下，实际却被商品、库存、报表、供应商、采购/退货和导出等多个模块作为全局成本可见边界使用，角色管理里看不出它的跨模块影响。
- 证据：2026-06-24 MySQL 查询成本相关权限，只返回 `4510 / parent_id=4001 / 成本查看 / inv:cost:view`；角色授权中只有 `超级管理员` 和 `仓库管理员` 持有该权限。前端 `product/index.vue` 的 `canViewCostFields`、`report/index.vue` 的 `canViewCostMetrics` 以及后端 `InvProductController` 导出脱敏都检查 `inv:cost:view`。P1-24 至 P1-33 中多处成本泄露仍显示：大量接口和导出还没有接入这个权限，所以当前问题已从“权限码不匹配”变为“全局成本权限语义不清且接入不完整”。
- 影响：管理员可能把“商品管理下面的成本查看”理解为只影响商品页，实际它应控制库存成本、采购价、报表毛利、退货金额和导出敏感字段；反过来，如果业务需要按商品成本、库存成本、报表毛利、敏感导出分开授权，当前单一 `inv:cost:view` 无法表达最小权限。审计期间成本权限从三套历史口径回填成一套，也说明角色迁移和权限说明缺少可追溯记录。
- 建议：先明确成本权限模型。若采用全局成本权限，应把 `inv:cost:view` 移到进销存或系统级“敏感字段权限”节点，并在权限名称/说明中标明覆盖商品、库存、采购、退货、报表、导出和接口响应；若需要分模块控制，则恢复分层权限并同步修改前后端判断点。无论哪种模型，都要补全 P1-24 至 P1-33 暴露的接口级脱敏和写权限校验。

### P2-78 OA 采购申请权限码已回填但页面仍停用，入口状态与审批工作台不一致

- 现象：早前采购申请菜单曾存在组件路径和权限码不一致；2026-06-24 当前运行态已回填为 `component=oa/purchase/index`、`perms=oa:purchase:list`，与后端 `/purchase/my` 权限一致。但菜单和按钮仍是 `status=1/visible=1`，店长、运营经理、总经理保留了隐藏停用授权；与此同时待办/已办审批工作台已启用给这些业务角色。
- 证据：MySQL 当前 `3002 / 采购申请 / path=purchase / component=oa/purchase/index / perms=oa:purchase:list / status=1 / visible=1`，子按钮 `3101-3104` 为 `oa:purchase:query/add/export` 且同样停用隐藏，角色授权保留 `dz,yyjl,zjl`。`OaPurchaseController.myList()` 使用 `oa:purchase:list`，`save/submit` 使用 `oa:purchase:add`，详情使用 `oa:purchase:query`，导出使用 `oa:purchase:export`；前端 `oa/purchase/index.vue` 与 API 已存在。当前运行态 `oa_purchase=0`，采购申请电脑入口不可见，待办/已办却启用可见。
- 影响：权限码错配已不再是当前主要问题，但采购申请仍没有形成可用入口；后续只要把 `3002` 启用，历史隐藏授权会直接让店长、运营经理和总经理获得采购申请新增、查询和导出能力。当前状态下用户能看到审批台，却不能发起申请，仍会误判 OA 采购审批是否交付。
- 建议：把采购申请菜单恢复或下线做成一次明确变更：恢复时启用 `3002/3101-3104` 前先确认默认角色、组织范围、审批人和审批意见展示；下线时同步收回 `oa:purchase:*` 隐藏授权，并清理 OA 首页、桌面首页和待办/已办中采购审批文案。

### P2-79 进销存细分按钮权限在审计期间发生回收，历史授权和当前运行态不一致

- 现象：早前审计曾在权限树中看到商品价格编辑、客户信用额度编辑、采购审批、质检记录、销售审批、销售发货通知等细分按钮权限，并且这些权限没有对应前端按钮或后端注解；2026-06-24 当前运行态再次复核时，这批权限已不在 `sys_menu` 中。问题从“当前有无效授权”转为“权限树在审计期间被回收，验收证据和角色模板口径发生漂移”。
- 证据：早前 MySQL 曾命中 `inv:product:price:edit`、`inv:customer:credit:edit`、`inv:purchase:approve`、`inv:purchase:qcRecord`、`inv:sales:approve`、`inv:sales:notice` 等权限；2026-06-24 对当前运行态 `BossERP_stock_state_75c59ee` 精确查询上述 6 个权限码返回 0 行，源码检索这些权限码也无运行代码命中。当前采购、销售、发货通知真实按钮仍分别使用 `inv:purchase:add/submit/receive/qc/export`、`inv:sales:add/submit/remove/query/export`、`inv:deliveryNotice:add/deliver/remove/export` 等权限。
- 影响：同一份审计报告在不同时间点看到不同权限树，说明运行态菜单和角色模板没有冻结版本；管理员和测试人员无法判断某个岗位到底应按历史细分权限配置，还是按当前实际按钮权限配置。若后续迁移或合并 SQL 再次回填这些旧权限，角色管理界面还会重新出现“授权了但不生效”的误导。
- 建议：把菜单/权限树纳入版本化迁移和验收冻结。已废弃的细分权限要在 SQL 迁移中明确删除并记录映射关系；需要保留的权限必须接入真实前端按钮和后端 `@RequiresPermissions`，并为角色模板写一张当前有效权限矩阵，避免历史权限被脚本重新导入。

### P2-13 代码生成隐藏编辑副页面可直连到空配置提交界面

- 现象：系统工具父菜单隐藏且停用，`/tool/gen` 列表页直连显示 404；但管理员直连 `/tool/gen-edit/index/1` 仍能打开“修改生成配置”，页面展示“提交 / 返回”和字段配置表，当前又没有任何 `gen_table`、`gen_table_column` 数据。
- 证据：数据库 `gen_table=0`、`gen_table_column=0`；2026-06-23 浏览器补测 `/tool/gen` 为 404，`/tool/gen-edit/index/1` 能渲染编辑页。`erp-ui/src/router/index.js:193-206` 把 `/tool/gen-edit` 配为动态隐藏路由，权限仅为 `tool:gen:edit`；`erp-ui/src/views/tool/gen/editTable.vue:156-173` 只在有 `tableId` 时请求详情，没有记录不存在时的空态/跳转；`erp-modules/erp-gen/src/main/java/com/erp/gen/service/GenTableServiceImpl.java:65-69` 对不存在的 `tableId` 没有空判断。
- 影响：一个运行菜单已停用的开发工具模块仍留下可直连的写入型副页面，且在空数据状态下给出“提交”入口；管理员会看到一个无法从列表闭环进入、也没有真实配置数据支撑的编辑流程。
- 建议：如果系统工具本环境不交付，应同步停用/移除 `/tool/gen-edit` 动态路由权限；如果要保留，应让 `/tool/gen` 可进入并在 `gen_table=0` 时展示导入引导，编辑页和后端都要对不存在的 `tableId` 返回明确 404/空态，禁用提交。

### P2-97 系统工具父菜单停用但子权限仍授给业务角色，后端接口没有按父级停用隔离

- 现象：系统工具父菜单 `3 / 系统工具` 已停用隐藏，但其子菜单和按钮权限仍是启用状态，并且授给普通角色、总经理角色和用户 `ry` 的有效角色。菜单树不会展示父菜单下的工具入口，但权限集合仍会返回启用子权限，代码生成、系统接口和表单构建的边界因此只靠前端菜单隐藏维持。
- 证据：当前运行态 `sys_menu` 中 `3 / 系统工具` 为 `status=1`、`visible=1`，而 `114 / 表单构建 / tool:build:list`、`115 / 代码生成 / tool:gen:list`、`116 / 系统接口 / tool:swagger:list` 以及 `1055-1060 / tool:gen:query/edit/remove/import/preview/code` 均为 `status=0`、`visible=0`。`common / 普通角色` 和 `zjl / 总经理` 持有 `tool:build:list`、`tool:swagger:list` 和完整 `tool:gen:*`；用户 `ry` 通过 `zjl` 持有完整 `tool:gen:*`。`SysMenuMapper.selectMenuTreeByUserId` 只过滤当前节点 `m.status=0`，不会检查父菜单状态；`selectMenuPermsByUserId` 同样只过滤当前权限节点 `m.status='0'` 和角色状态。代码生成接口 `GenController` 对列表、导入、预览、删除、同步、生成代码分别使用 `tool:gen:*` 权限注解。
- 影响：业务角色虽然看不到“系统工具”父菜单，但如果前端动态路由、隐藏子路由或接口 URL 被直接调用，后端仍会按子权限放行代码生成相关能力。多账号场景下，总经理或普通角色模板不应默认具备导入数据库表结构、生成代码、访问系统接口这类开发/运维权限；父菜单停用也没有真正表达“本模块不可用”。
- 建议：若系统工具不交付，应同时停用子菜单、按钮权限、隐藏动态路由和角色授权，并让运行时权限查询排除停用父级下的所有子权限；若要交付，应建立专门开发/运维角色，不把 `tool:*` 默认授给普通业务角色，并在菜单健康检查中标出“父级停用但子级启用/已授权”的异常。

### P2-14 调拨记录详情缺少审批轨迹和状态流水

- 现象：`/inventory/transfer-records` 和 `/cangku/transfer-records` 的列表只有“详情”动作；详情弹窗只展示主单、要货明细、发货/收货批次，没有审批规则快照、审批人、审批时间、审批意见，也没有状态流转流水。
- 证据：早前数据库快照中 `inv_transfer_order` 的 `TF202606180001` 关联 `approval_instance_id=4`；`inv_transfer_approval_task` 有两条已审批任务（`ll1`、`ii`），`inv_transfer_status_log` 对同一调拨单记录了 `submit -> approve -> deliver -> receive` 流水。另一张 `TF202606140001` 的审批实例 `3` 带有 3 级审批规则快照但任务数为 0，状态流水显示 `auto_approve`，更需要详情页解释自动通过原因。菜单权限还存在 `4402 调拨审批轨迹 inv:transfer:approval:track`。浏览器实测详情弹窗正文不包含“审批轨迹 / 审批记录 / 状态流水”；前端 `erp-ui/src/views/inventory/transfer/records.vue:66-118` 只渲染要货明细和发货/收货批次，`erp-ui/src/api/inventory/transfer.js:13-20` 只提供记录列表和调拨详情接口；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:237-247` 详情只 set `details` 和 `shipments`。运行态库当前仍有调拨审批和状态流水表；非运行态 `BossERP` 的缺表问题已按 P1-22 记录为环境漂移。
- 影响：调拨审批是多角色流程，桌面端无法在记录页复盘“谁审批、什么时候审批、按哪条规则审批、状态如何变化”；审计、异常排查和跨店/跨仓争议处理缺少前端闭环。
- 建议：补充调拨审批轨迹接口和前端按钮/Tab，至少展示审批实例、规则快照、节点、候选人、实际审批人、审批时间、意见以及 `inv_transfer_status_log` 状态流水；如果暂不交付，应移除或隐藏 `inv:transfer:approval:track` 按钮权限，避免菜单权限和页面能力不一致。

### P2-15 库存盘点日期在前端比数据库少一天

- 现象：早前快照中，数据库 `inv_stock_check.check_date` 为 `2026-06-13`，门店 `市场部门` 上下文打开库存盘点列表和详情时显示为 `2026-06-12`。
- 证据：早前 MySQL 查询 `SC202606140001` 的 `check_date=2026-06-13`；2026-06-23 浏览器实测 `/inventory/stockCheck` 列表和“盘点详情”弹窗均显示 `2026-06-12`。前端 `erp-ui/src/views/inventory/stockCheck/index.vue:39` 和 `erp-ui/src/views/inventory/stockCheck/index.vue:134` 直接绑定 `checkDate`；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheck.java:19-21` 把 MySQL `date` 字段映射为 `java.util.Date` 并仅声明 `@JsonFormat(pattern = "yyyy-MM-dd")`。本轮运行态仍有该盘点主表和明细记录各 1 条，后续可继续复测日期展示。
- 影响：盘点日期是库存差异归属日期，错一天会影响按日期筛选、导出、盘点责任归属和与库存变动日志对账；用户会以为 6 月 12 日做了盘点，但数据库真实日期是 6 月 13 日。
- 建议：日期型业务字段优先使用 `LocalDate` 或后端明确按 `GMT+8` 输出 `yyyy-MM-dd` 字符串；前端不要把纯日期当 UTC 时间二次转换。库存盘点、OA 考勤、销售/采购日期等同类字段应统一排查。

### P2-16 供应商管理桌面端入口细化复测出现空白和不可点击

- 现象：在已选择 `仓库：仓库` 后，细化复测直接进入 `/cangku/supplier`，页面正文为空，无法查看供应商列表、详情、新增、编辑、状态、删除和导出按钮；从分类页横向菜单尝试进入时，`供应商管理` 子项存在于 DOM，但宽高为 0，自动化无法点击，也不是用户可见的稳定入口。
- 证据：数据库 `sys_menu` 中 `4060` 为启用可见页面菜单，`path=supplier`、`component=inventory/supplier/index`、`perms=inv:supplier:list`；前端 `erp-ui/src/views/inventory/supplier/index.vue:1-290` 组件和按钮逻辑完整；浏览器实测 `http://localhost:1026/cangku/supplier` 时 `document.querySelector('#app').innerText` 为空，正文只剩 SVG sprite，横向菜单中供应商链接节点 `href=/cangku/supplier` 但 `getBoundingClientRect()` 宽高为 0。
- 影响：供应商是商品、采购、采购退货流程的关键主数据；入口不稳定会导致管理员无法在电脑端维护供应商和查看供货商品，前端页面与数据库 17 条供应商数据无法稳定对应。
- 建议：优先修复仓库管理横向子菜单展开/点击和 `/cangku/supplier` 深链刷新渲染问题；修复后重新完整补测供应商列表、分页、搜索、详情、新增/编辑弹窗、状态变更、删除拦截和导出确认。

### P2-17 商品管理分页条显示 10 条/页但首屏渲染全部 162 行

- 现象：仓库商品页重置到全部商品后，分页条显示“共 162 条”和页码，但表格 DOM 一次性渲染 162 行；页面指标也显示 `162 当前页正常`、`121 当前页待完善`、`16 当前页供应商`。这和前端默认 `pageSize=10`、页面上的分页条语义不一致。
- 证据：2026-06-23 浏览器实测 `/cangku/product`，`.el-table__body-wrapper tbody tr` 数量为 162，表格高度约 13130px；搜索“清香铁观音”后为 1 行，重置后恢复 162 行。源码 `erp-ui/src/views/inventory/product/index.vue:497-500` 默认 `pageSize: 10`，`erp-ui/src/views/inventory/product/index.vue:137-142` 使用分页组件，`erp-ui/src/views/inventory/product/index.vue:775-780` 直接把接口 `res.rows` 赋给表格。
- 影响：商品量继续增长后，首屏加载、滚动和行级按钮渲染都会变慢；用户看到“10 条/页”却实际要滚过全部数据，分页控件失去降低认知负担的作用。
- 建议：确认后端 `startPage()` 是否对当前网关请求生效；商品接口应只返回当前页 rows，前端指标中“当前页”应和分页结果一致。若业务要展示全量统计，应单独提供全量统计接口，不要依赖表格当前 rows。

### P2-18 商品导入允许待完善商品落库，和新增/编辑规则不一致

- 现象：普通新增/编辑商品要求选择供应商，但导入商品时供应商可以为空，导入结果只统计“待完善”数量而不阻断落库。当前商品主数据中已有 119 条缺供应商、117 条缺售价、112 条缺编码，采购商品选择器和商品页都会暴露这些不完整商品。
- 证据：前端 `erp-ui/src/views/inventory/product/index.vue:529` 将 `supplierName` 配为必填；后端导入在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:135` 调用 `applySupplierFromCatalog(product, shopDeptId, false)`，`erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:289-300` 在 `requireSupplier=false` 时允许供应商为空；`erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:167-178` 只把缺供应商/售价计入“待完善”。
- 影响：导入通道持续生成采购、销售不可直接使用的商品，后续采购页再拦截会把主数据质量问题推迟到业务制单环节。
- 建议：导入前增加预检和可下载错误清单，供应商、销售参考价、单位等关键字段缺失时默认不入正式商品库；若必须允许暂存，应进入“待完善商品”状态，并在采购/销售选择器中默认禁用。

### P2-19 仓库采购相关页面深链或刷新后出现空白页

- 现象：采购页从桌面入口进入后曾能显示列表空态和新建采购单弹窗；但在同一路由刷新 `http://localhost:1026/cangku/purchase` 后，页面正文变为空。采购退货页 `http://localhost:1026/cangku/purchaseReturn` 直连也是空白，且从首页仓库菜单看到的 `/cangku/purchaseReturn` 子链接宽高为 0，点击可见“仓库管理”菜单后仍没有形成可见入口。
- 证据：2026-06-23 浏览器实测 `/cangku/purchase` 加载状态下显示“当前采购仓库：仓库”“当前仓库暂无采购单”，并能打开“新建采购单”和“选择采购商品”；随后刷新该 URL，`bodyHead` 为空、可见 DOM 为空、`activeDialogs=[]`。同日新标签直连 `/cangku/purchaseReturn`，`domSnapshot` 只有 `generic: `、`#app.innerText` 长度为 0；回到 `/index` 后，仓库菜单 DOM 里 `/cangku/purchaseReturn` 链接存在但 `getBoundingClientRect()` 宽高为 0。源码和菜单均存在：采购页组件为 `erp-ui/src/views/inventory/purchase/index.vue`，采购退货组件为 `erp-ui/src/views/inventory/purchaseReturn/index.vue`，数据库菜单 `4020/4070` 均启用。
- 影响：用户刷新、收藏、从消息或外链直接进入采购/采购退货时会得到白屏；采购退货连稳定可见入口都无法形成，列表、搜索、新建、导出、确认退货等按钮无法在电脑端完成闭环。
- 建议：优先检查仓库管理父路由、动态路由注册、页面 keep-alive/tab 缓存和 history fallback；修复后为 `/cangku/product`、`/cangku/category`、`/cangku/supplier`、`/cangku/purchase`、`/cangku/purchaseReturn` 增加“直接打开 + 刷新 + 从首页/菜单进入”端到端用例。

### P2-20 采购商品选择弹窗显示 10 条/页但一次渲染 162 行

- 现象：在新建采购单里打开“选择采购商品”，分页条显示“共 162 条”，但弹窗表格 DOM 一次性渲染 162 行；搜索“清香铁观音”后变为 1 行，点击重置又恢复 162 行。这和商品管理页 P2-17 的分页异常同源，但发生在采购制单流程内。
- 证据：2026-06-23 浏览器实测采购商品弹窗，`rowCount=162`，分页文本为“共 162 条 12345617 前往页”；源码 `erp-ui/src/views/inventory/purchase/index.vue:211-220` 使用商品表格和分页组件，`erp-ui/src/views/inventory/purchase/index.vue:317` 将 `productQuery.pageSize` 设为 10，`erp-ui/src/views/inventory/purchase/index.vue:398-403` 直接把 `listProduct` 返回的 `res.rows` 赋给 `productList`。
- 影响：采购员在 920px 弹窗内要面对全量商品，选择体验和性能都会快速下降；本轮自动化真实点击/截图也因弹窗 DOM 过大多次超时，说明这个问题已经不只是分页语义不一致。
- 建议：先修商品接口分页，让采购选择器只接收当前页 rows；采购选择器最好增加供应商/分类/完整性筛选，并默认隐藏缺供应商、缺单位、缺采购价的不可采购商品。

### P2-21 采购单允许混选多个供应商并只把名称拼成一个字段

- 现象：新建采购单时可以在一个采购商品弹窗里选择不同供应商的商品，页面供应商字段自动显示多个供应商名称拼接值，没有提醒“按供应商拆单”；后端也接受多个供应商并写入同一个 `supplier_name` 字段。
- 证据：浏览器实测采购商品弹窗首屏同时显示“泉州安溪今世缘茶业有限公司”“武夷山市大齐茶业有限公司”“潮州市乐源发茶厂”等不同供应商商品且均可选择；前端 `erp-ui/src/views/inventory/purchase/index.vue:358-360` 用 `collectSupplierNames` 后 `join("、")` 显示供应商，`erp-ui/src/views/inventory/purchase/index.vue:543-545` 保存时把 `supplierName` 写成 `supplierDisplay`；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:126-185` 同样收集供应商并 `String.join("、", supplierNames)` 后 `order.setSupplierName(supplierSummary)`。
- 影响：采购单、采购退货、供应商对账和导出通常以单供应商为基本业务对象；混合供应商会让 `supplier_name like` 搜索、退货原单、对账付款和责任归属都变模糊，供应商名称过长时还会在保存阶段才提示拆分。
- 建议：明确采购单是否必须单供应商。若必须单供应商，选择第一个商品后限制后续只能选同供应商商品；若允许批量采购，保存前自动按供应商拆成多张采购单，并在页面给出拆单预览。

### P2-22 采购退货单供应商可手工改写且后端不按原采购单回填

- 现象：新增采购退货单选择原采购单后，页面会把原单供应商带入“供应商名称”，但该输入框仍可编辑；保存时后端直接使用请求中的 `supplierName`，没有从原采购单强制回填供应商和采购单号。
- 证据：前端 `erp-ui/src/views/inventory/purchaseReturn/index.vue:96-98` 使用可编辑 `el-input v-model="form.supplierName"`，`erp-ui/src/views/inventory/purchaseReturn/index.vue:274-283` 只在选择原单时把 `order.supplierName` 写入表单。后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:331-391` 仅用原采购明细重算商品、数量、单价和总金额，没有重置 `purchaseOrderNo` 或 `supplierName`；mapper `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseReturnMapper.xml:91-101` 插入时直接写入 `#{purchaseOrderNo}`、`#{supplierName}`，`erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseReturnMapper.xml:105-115` 更新时也允许更新 `supplier_name`。
- 影响：采购退货单可能显示为 A 采购单、B 供应商，导致供应商维度筛选、导出、对账、删除供应商引用判断和采购退货责任归属都不可信。
- 建议：前端将供应商名称和原采购单号改为只读展示；后端按 `purchaseOrderId` 查询原采购单，统一回填 `purchaseOrderNo`、`supplierName`，并校验原单属于当前仓库且状态/质检/收货状态满足退货条件。

### P2-23 采购退货原采购单候选只按状态粗过滤，未按可退数量过滤

- 现象：新增采购退货时，原采购单远程下拉只排除草稿和已取消单据；已提交但未收货、待质检、无可退数量或已全部退完的采购单仍可能出现在候选中。用户选择后才会看到“该采购单暂无可退明细”或在提交/确认时失败。
- 证据：前端 `erp-ui/src/views/inventory/purchaseReturn/index.vue:254-259` 调用 `listPurchase({ pageNum: 1, pageSize: 20, orderNo })` 后仅执行 `item.status !== "draft" && item.status !== "cancelled"`；`erp-ui/src/views/inventory/purchaseReturn/index.vue:289-304` 再按 `receivedQuantity` 构建退货明细，明细为空时只弹 warning。后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:106-115` 提交时才校验退货数量，`erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:147-180` 确认退货时才校验库存。
- 影响：原单选择器不是“可退货采购单”选择器，而是“非草稿非取消采购单”选择器；采购员会在弹窗内试错，尤其在采购单量变多后很难知道哪些原单真正可退。
- 建议：提供专门的“可退货采购单”接口，按当前仓库、已质检入库/已收货、剩余可退数量大于 0、未被其他已提交/已退货退货单占用来过滤；下拉空态显示“暂无可退货采购单，请先完成采购收货和质检”。

### P2-24 采购退货“修改”权限配置存在但页面和接口实际使用“新增”权限

- 现象：数据库菜单配置了 `inv:purchaseReturn:edit`，但采购退货草稿行的“编辑”按钮使用 `inv:purchaseReturn:add`；保存草稿接口也只要求 `inv:purchaseReturn:add`。这会让角色配置里的“采购退货修改”权限失效。
- 证据：数据库 `sys_menu` 中 `4073` 是“采购退货修改”，权限为 `inv:purchaseReturn:edit`；前端 `erp-ui/src/views/inventory/purchaseReturn/index.vue:46` 的编辑按钮使用 `v-hasPermi="['inv:purchaseReturn:add']"`，弹窗“保存草稿”按钮 `erp-ui/src/views/inventory/purchaseReturn/index.vue:151` 也使用新增权限；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseReturnController.java:58-64` 的 `/save` 端点只要求 `inv:purchaseReturn:add`，没有区分新增和修改。
- 影响：只授予“修改”权限的角色无法编辑草稿，只授予“新增”权限的角色反而能修改已有草稿；权限配置界面与真实按钮/接口行为不一致，破坏多账号最小权限模型。
- 建议：新增和修改拆分权限：新增无 `returnId` 时要求 `add`，修改已有 `returnId` 时要求 `edit`；前端编辑按钮和保存已有草稿应使用 `inv:purchaseReturn:edit`。如果业务不区分新增/修改，应删除或隐藏无效的“采购退货修改”权限菜单。

### P2-25 仓库库存入口切换和刷新后不渲染库存页主体

- 现象：在 `仓库：仓库` 上下文从桌面首页点击“库存查询”，地址变为 `/cangku/stock`，面包屑也变为“首页 / 仓库管理 / 库存管理”，但主内容仍是“桌面工作台”；刷新 `/cangku/stock` 后页面也没有库存筛选、指标卡、库存表或调整入口。
- 证据：2026-06-23 浏览器实测 `/index` 点击 node `库存查询 查看库存与预警状态` 后，`location.href=http://localhost:1026/cangku/stock`，但 `document.body` 仍包含“桌面工作台 / 快速进入业务模块 / 今日建议”，`document.querySelectorAll('.el-table').length=0`、`document.querySelectorAll('form').length=0`；刷新 `/cangku/stock` 后仍无库存主体。首页库存快捷入口由 `erp-ui/src/views/index.vue:91-97` 和 `erp-ui/src/views/index.vue:141-147` 指向 `/cangku/stock`，库存组件本身在 `erp-ui/src/views/inventory/stock/index.vue:417-424` 已支持按 `/cangku/` 推断仓库入口。
- 影响：仓库用户从首页进入库存管理会以为已经进入库存页，但实际仍停留在工作台内容；刷新或复制库存链接也无法稳定恢复页面，影响库存查询、调整、导出和日志查看。
- 建议：修复动态路由/组件渲染问题，并把 `/cangku/stock` 的直接打开、从首页快捷入口进入、从仓库菜单进入、刷新和带 `?stockEntry=warehouse` 打开都纳入回归用例；页面未加载成功时不应只改变面包屑和标签页。

### P2-26 仓库“可见库存”只读提示下仍可打开库存调整

- 现象：仓库库存页提供“当前仓库 / 可见库存”范围切换；当切到“可见库存”时提示“只读查看；库存调整请切回当前仓库范围”，但顶部“库存调整”按钮仍按权限显示，`openAdjust()` 没有根据 `ownOnly=false` 拦截，点击会按当前仓库打开调整弹窗。
- 证据：前端 `erp-ui/src/views/inventory/stock/index.vue:89-93` 提供“可见库存”模式，`erp-ui/src/views/inventory/stock/index.vue:460-465` 明确提示只读；但 `erp-ui/src/views/inventory/stock/index.vue:120-124` 的“库存调整”按钮只看 `inv:stock:adjust` 权限，`erp-ui/src/views/inventory/stock/index.vue:725-746` 的 `openAdjust` 只校验入口上下文和行组织，不校验当前是否处于可见库存模式。
- 影响：用户正在看跨组织可见库存时，顶部调整动作仍可用，容易误以为能调整当前筛选/当前可见行；实际提交会写入当前仓库组织，和“可见库存只读查看”的文案冲突。
- 建议：当 `queryParams.ownOnly === false` 时禁用或隐藏“库存调整”，并在 tooltip 中提示“切回当前仓库后可调整”；如果允许从可见库存发起调整，应要求先选择本仓库存行并清楚展示写入组织。

### P2-27 可见库存范围和变动日志范围不一致

- 现象：仓库库存页切到“可见库存”时可以查看当前仓库可见的其他组织库存，但点击“变动日志”进入 `/inventory/stock-log` 后，日志服务强制只查当前选择组织，无法解释可见库存中其他组织的库存变化。
- 证据：运行态数据库中 `inv_stock` 有仓库 `104` 库存 192 和市场部门 `108` 库存 12，`inv_stock_log` 有仓库日志 3 条、市场部门日志 2 条；非运行态 `BossERP` 曾出现 `inv_stock=0`、`inv_stock_log=0`，不能用于复现当前网页端库存。库存列表服务在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java:275-307` 中允许仓库 `ownOnly=false` 走可见范围；库存日志服务在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java:310-318` 会把 `shopDeptId` 和 `warehouseId` 都强制设为当前选择组织。前端日志页 `erp-ui/src/views/inventory/stock/log.vue:118-128` 没有库存组织/可见范围筛选，库存页“变动日志”按钮 `erp-ui/src/views/inventory/stock/index.vue:124` 也没有把当前可见范围带过去。
- 影响：仓库用户在可见库存中看到门店库存后，无法从相邻的“变动日志”解释门店库存为什么变化；库存总数、可见库存和日志对账断开。
- 建议：库存日志页增加与库存页一致的当前组织/可见库存/门店筛选，或从库存页跳转时携带范围参数；后端日志查询应按权限支持同样的可见范围，同时避免泄露成本字段。

### P2-28 部分发货单已有待收货批次但门店端不显示收货入口

- 现象：`TF202606140001` 当前状态为 `partial_delivered`，已有待收货批次 `TS202606180002`，本批发货 1、已收 0；按业务流程门店应该可以先确认已发批次收货，但前端只在整单 `delivered` 或 `partial_received` 时显示“确认收货”。
- 证据：运行态数据库中 `inv_transfer_order` + `inv_transfer_shipment` + `inv_transfer_shipment_detail` 查询命中 `TF202606140001 / partial_delivered / TS202606180002 / pending_receive / shipped=1 / received=0`。后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:435-454` 明确允许 `PARTIAL_DELIVERED`、`DELIVERED`、`PARTIAL_RECEIVED` 调用按批次收货；桌面前端 `erp-ui/src/views/inventory/transfer/index.vue:626-627` 的 `canReceive` 只允许 `delivered`、`partial_received`；移动端 `erp-ui/src/views/mobile/feature/featureActions.js:411-412` 也同样只在这两个状态暴露 `receiveTransferShipment`。非运行态 `BossERP` 缺少发货批次表时会直接缺表，已在 P1-22 标注为环境漂移。
- 影响：部分发货场景下，仓库已经扣减库存并生成待收货批次，但门店端没有收货按钮，导致已发批次不能按批次闭环；仓库库存已减少、门店库存未增加，库存和调拨流程长期悬挂。
- 建议：门店端收货动作应基于“是否存在 `pending_receive` 发货批次”和当前组织是否为目标门店，而不是只看整单状态；至少把 `partial_delivered` 纳入 `canReceive`，并在收货弹窗中只列待收货批次。

### P2-29 调拨管理入口曾出现正文仍是首页或空白壳，需纳入路由稳定性回归

- 现象：早前仓库上下文直连 `/cangku/transfer` 时页面只剩隐藏控件“清空 / 确定”，没有调拨表格；临时切到 `门店：市场部门` 后从首页点击 `/inventory/transfer` 链接，URL 和面包屑变成“进销存管理 / 调拨管理”，但正文仍停留在“桌面工作台”。后续重启前端 dev server 并设置明确组织上下文后，两个入口均能渲染调拨页，说明旧问题更像运行态/动态路由加载稳定性，而不是调拨组件完全不可用。
- 证据：早前浏览器实测 `/cangku/transfer` 时 `document.querySelectorAll('.el-table').length=0`、`forms=0`、正文仅有“清空 / 确定”；同会话从页面链接进入 `/inventory/transfer` 后，`location.href=http://localhost:1026/inventory/transfer`，面包屑为调拨管理，但正文仍包含“桌面工作台 / 快速进入业务模块 / 销售管理”。2026-06-23 复核时，`admin` 在 `108 / 市场部门 / STORE` 上下文打开 `/inventory/transfer` 能渲染 `TF202606140001`、状态“部分发货”和“发起要货/详情”等门店动作；在 `104 / 仓库 / WAREHOUSE` 上下文打开 `/cangku/transfer` 能渲染同一单据并显示“发货”按钮。
- 影响：调拨主流程是跨门店/仓库的核心流程，入口不稳定会直接阻断发起要货、继续发货、确认收货和导出；用户看到面包屑已经进入调拨，却仍在首页，会误以为系统卡住或数据丢失。
- 建议：把 `/inventory/transfer` 和 `/cangku/transfer` 的直接打开、刷新、菜单点击、首页跳转、组织切换后恢复纳入统一回归；修复路由复用/keep-alive/动态路由加载问题，确保 URL、面包屑、标签页和正文组件一致。本轮另发现仓库路由不强制仓库上下文，见 P2-94。

### P2-30 门店销售链路曾出现空白壳，仍需纳入直连和组织上下文回归

- 现象：早前销售管理、销售退货、发货通知、报表中心这些门店销售链路页面在当前浏览器会话直连后没有渲染列表、筛选、空态或按钮，只剩隐藏主题色控件“清空 / 确定”；后续重新启动前端 dev server 并设置明确门店上下文后，页面本体能正常渲染，旧空白证据应归入路由/运行态稳定性。
- 证据：早前浏览器实测 `/inventory/sales`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report`，`#app.innerText` 长度均为 0，`document.querySelectorAll('.el-table').length=0`、`forms=0`，可见按钮只剩隐藏的“清空 / 确定”；同一会话写入 `selected_dept_id=108`、`selected_dept_type=STORE`、`selected_dept_validated=1` 后，`/index` 和 `/inventory/customer` 仍为空白。2026-06-23 复核时，`admin` 在 `108 / 市场部门 / STORE` 上下文打开 `/inventory/sales`、`/inventory/customer`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report`，均能显示表格/空态和“新建销售单 / 新增客户 / 新增退货单 / 导出 / 查询 / 重置”等对应按钮，相关列表和报表 API 返回 200。
- 影响：门店销售主流程无法通过直连、刷新或上下文切换稳定进入，用户无法创建销售单、处理销售退货、查看发货通知或看报表；这和采购、库存、调拨已发现的路由稳定性问题同类，但影响的是门店销售闭环。
- 建议：把门店销售链路加入统一路由回归矩阵：选择门店后从首页进入、从侧边菜单进入、直接打开、刷新恢复、切换门店后再打开都要验证；页面无法加载时不要只留下隐藏控件，应给出明确的组织/路由错误提示。销售正式表当前为空，销售单提交、发货通知生成、销售退货原单选择仍需后续构造流程数据验证。

### P2-31 客户管理缺少门店上下文限制，仓库上下文也能新增客户

- 现象：在 `仓库：仓库` 上下文打开 `/inventory/customer`，客户列表能渲染空态，“新增客户”和“导出”按钮可见且启用，点击“新增客户”能打开客户表单；但客户主数据字段是 `shop_dept_id`，应归属门店，不应在仓库上下文写入。
- 证据：2026-06-23 浏览器实测 `/inventory/customer` 顶栏显示“仓库：仓库”，页面仍展示客户编码、客户名称、联系人、联系电话等列和“新增客户 / 导出”按钮；点击“新增客户”后弹窗显示客户名称、客户编码、联系人、联系电话、电子邮箱、客户等级、信用额度、账期、状态、地址、备注，并且“确认”按钮启用。前端 `erp-ui/src/views/inventory/customer/index.vue:26-28` 的搜索、新增、导出按钮只按权限显示，没有按 `isSelectedStore` 禁用；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceImpl.java:38-45` 保存客户时只调用 `resolveAndValidateShopDept(selectedShopDeptId)`，没有 `requireStoreContext` 或 `dept_type=STORE` 校验，会把当前选择的仓库 `104` 写入 `shop_dept_id`。
- 影响：管理员或有权限账号在仓库上下文新增客户时，会生成归属于仓库的客户主数据；后续销售单、客户列表、客户删除引用检查都按 `shop_dept_id` 过滤，客户和门店销售数据会被分到错误组织。
- 建议：客户管理和客户接口应统一要求门店上下文；仓库上下文下隐藏或禁用新增、编辑、删除、导出，并提示“请切换到门店维护客户”。后端保存、列表和导出也应校验 `selectedDeptType=STORE`，避免绕过前端写入仓库客户。

### P2-98 客户信用额度和已用额度没有业务闭环，档案接口仍可写入已用额度

- 现象：客户管理页显示“信用额度 / 已用额度”，并在“已用额度”下提示“已用额度由业务单据维护”；但当前销售流程仍只保存客户名称自由文本，不关联 `customer_id`，也没有任何销售、退货或收款流程更新 `inv_customer.credit_used`。同时客户新增/编辑接口会把请求里的 `credit_used` 写入数据库，前端禁用输入框不能阻止手工 API 修改。
- 证据：当前 `inv_customer=0`，`credit_limit/credit_used` 汇总均为 0；`sys_menu` 中按 `%credit%` 或“信用”查询返回 0 行，当前也没有独立客户信用权限。客户页 `erp-ui/src/views/inventory/customer/index.vue:109-116` 允许编辑 `creditLimit`、禁用展示 `creditUsed` 并写上述提示，提交时 `doSubmit()` 直接调用 `addCustomer(this.form)` 或 `updateCustomer(this.form)`。后端 `InvCustomerController.add/edit` 只要求 `inv:customer:add/edit`；`InvCustomerMapper.xml:56-64` 插入 `credit_used`，`:68-84` 更新时 `creditUsed != null` 就写 `credit_used`。源码检索 `creditUsed/credit_used` 除客户实体、客户 mapper、客户页面和移动端展示外没有业务单据维护逻辑；`InvSalesServiceImpl.saveDraft` 和 `InvSalesOrderMapper.xml` 仍围绕 `customerName` 字符串保存销售单。
- 影响：用户看到的是“信用额度由系统业务单据维护”的预期，但实际销售不会占用额度、退货不会释放额度，已用额度也可以被有 `inv:customer:edit` 的账号通过接口篡改。多店铺经营中，客户授信、账期和欠款风险无法被系统真实控制，报表或导出里的信用字段会成为不可信的手工数据。
- 建议：先决定客户信用是否属于本期交付。若交付，应让销售单关联 `customer_id`，在提交、发货、退货、收款/核销时按事务更新或实时计算 `credit_used`，并把信用额度调整拆成独立权限和审计日志；若暂不交付，应从桌面端列表、表单和导出中弱化或隐藏 `creditLimit/creditUsed`，至少不要提示“由业务单据维护”。

### P2-99 岗位管理全量写权限授给业务角色，修改岗位编码会影响调拨审批候选人

- 现象：岗位管理不是单纯展示字典，它同时参与用户岗位、调拨审批候选人解析和薪资档位口径；但当前 `common / 普通角色` 和 `zjl / 总经理` 都持有岗位管理全套权限，用户 `ry` 通过总经理角色实际拥有岗位新增、修改、删除、导出权限。业务角色可以修改岗位编码、岗位名称或状态，影响审批和人事口径。
- 证据：当前 `sys_menu` 中 `104 / 岗位管理 / system:post:list` 以及 `1020-1024 / system:post:query/add/edit/remove/export` 均为启用；`common` 和 `zjl` 均绑定上述 6 个权限，`zjl` 当前绑定用户 `ry`，`ry` 的有效权限包含完整 `system:post:*`。当前 `sys_post=10`、`sys_user_post=18`，调拨审批节点 `26/27` 分别保存 `post_id=9/post_code=zdjl/驻店经理` 和 `post_id=10/post_code=yyjl/运营经理`；`InvTransferApprovalCandidateMapper.selectUsersByDeptAndPostCode` 运行时通过当前 `sys_post.post_code` 匹配候选人。`SysPostController.add/edit/remove/export` 分别只依赖 `system:post:*`；`SysPostServiceImpl.updatePost` 直接 `postMapper.updatePost(post)`，没有检查岗位是否被调拨审批规则、薪资档位或用户配置引用；删除保护也只检查 `sys_user_post`。薪资档位 `sys_salary_scheme_item` 还按 `post_name` 保存岗位口径，当前存在 `超级管理员` 1 个档位但没有同名 `sys_post`。
- 影响：总经理或普通角色模板若改动“驻店经理/运营经理”的岗位编码，当前调拨审批规则仍保存旧 `post_code`，候选人查询会找不到审批人，提交调拨时才暴露流程错误。岗位名称变化还会让薪资档位、劳动合同、用户资料和审批节点出现多套口径；多账号多店铺下，岗位基础资料被业务角色误改会比普通页面数据错误影响更广。
- 建议：岗位管理写权限默认只给超级管理员或人事/平台运维角色，`common`、`zjl` 这类业务角色最多保留只读。岗位编码一旦被调拨审批规则、薪资档位、合同或用户绑定引用，应禁止修改或要求迁移向导；保存和删除前要做依赖预检，列出受影响的审批规则、薪资档位、用户和合同，并把岗位变更写入独立审计日志。

### P2-100 用户管理全量账号治理权限授给业务角色，新增/导入/删除/导出边界过宽

- 现象：用户管理是多账号多店铺的账号生命周期入口，但当前 `common / 普通角色` 和 `zjl / 总经理` 都持有用户管理全套页面和按钮权限；有效用户 `ry` 通过总经理角色实际拥有用户新增、修改、删除、导入、导出、查询和重置密码权限。业务角色不仅能维护业务数据，也能创建账号、导入账号、导出账号、删除账号，并通过新增/编辑表单给用户选择岗位和角色。
- 证据：当前 `sys_user` 共 25 个有效启用账号；`sys_menu` 中 `100 / 用户管理 / system:user:list` 及 `1000-1006 / system:user:query/add/edit/remove/export/import/resetPwd` 均为启用可见。MySQL 复核显示 `common` 和 `zjl` 均绑定 `system:user:add,edit,export,import,list,query,remove,resetPwd`，`user_id=2 / ry / zjl` 实际持有上述全部权限。前端用户页 `erp-ui/src/views/system/user/index.vue:39-51` 用这些权限显示新增、修改、删除、导入、导出按钮，`:86-93` 的行级修改、删除、重置密码、分配角色、店铺授权都挂在用户管理权限下，`:161-169` 的用户表单可选择岗位和角色。后端 `SysUserController` 对列表、导出、导入、新增、修改、删除分别使用 `system:user:list/export/import/add/edit/remove`，但 `/resetPwd`、`/changeStatus`、`/authRole` 仍复用 `system:user:edit`；`SysUserServiceImpl.deleteUserByIds()` 删除用户时会同步删除用户角色、岗位和店铺授权关联，`importUser()` 会批量插入或更新用户。
- 影响：总经理或普通角色模板一旦被实际分配，就可以绕过平台账号管理员直接扩张账号、批量导入弱配置账号、导出用户手机号/邮箱等账号资料、删除账号并清掉角色/岗位/店铺授权关联。多店铺场景下，账号治理边界会和业务管理边界混在一起；已有 P1-11、P2-37、P2-63 分别覆盖导入弱初始密码、授权写权限复用和重置密码权限码未接入，本问题记录的是角色矩阵本身把全量账号治理能力授给业务角色。
- 建议：把用户管理拆成账号管理员/安全管理员专用能力，默认从 `common`、`zjl` 等业务角色撤销 `system:user:add/edit/remove/import/export/resetPwd`，只保留必要只读查询。新增账号、编辑基础资料、启停账号、重置密码、分配角色、店铺授权、导入、导出、删除应分别使用真实前后端权限点；删除优先改成停用/离职归档流程，并在导出里增加字段级脱敏和审批/二次确认。

### P2-112 用户导入只写基础账号，不写角色、岗位和店铺授权，批量创建后账号配置不闭环

- 现象：用户导入弹窗看起来是批量创建账号入口，但模板和服务只处理 `sys_user` 基础资料；新导入账号不会同步创建角色、岗位或店铺/仓库授权。导入成功后页面只刷新用户列表，没有把管理员带到分配角色或店铺授权流程。
- 证据：`SysUser` 的 Excel 导入字段只包括 `部门编号`、`用户账号`、`用户昵称`、`用户邮箱`、`手机号码`、`用户性别`、`账号状态`，`roleIds/postIds/shopScopeCount/setupStatus` 都没有 `@Excel` 导入注解，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:30-108`。前端 `erp-ui/src/views/system/user/index.vue:192` 的导入弹窗只配置 `/system/user/importData`、模板下载和“是否更新已经存在的用户数据”；通用导入组件 `erp-ui/src/components/ExcelImportDialog/index.vue:1-17` 只有上传、更新勾选和模板链接。后端 `SysUserController.importData` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:107-116` 解析 Excel 后直接调用 `userService.importUser(...)`；`SysUserServiceImpl.importUser` 在 `:531-550` 对新增只校验部门范围、设置初始密码并 `userMapper.insertUser(user)`，对更新也只 `userMapper.updateUser(user)`，没有写 `sys_user_role`、`sys_user_post` 或 `sys_user_shop`。当前运行态仍可看到账号配置缺口样本：`user_id=103 / 123` 无显式店铺/仓库授权，另有多个有效账号 `post_count=0`。
- 影响：批量导入在多账号交付时最常用，但当前流程会产生“导入成功、实际不可用或配置不完整”的账号。没有角色时会缺菜单，没有店铺/仓库授权时会卡在组织选择或业务页，缺岗位又会影响薪资、考勤、合同或调拨审批候选人解释。管理员需要再手工逐个进入分配角色、店铺授权和岗位维护，且列表里的配置状态当前又受 P1-02 影响显示不准，排查成本高。
- 建议：导入流程要改成账号配置向导：模板或导入预览至少支持角色、岗位和店铺/仓库授权编码，导入前校验这些编码存在、启用且在当前管理员可授权范围内；导入成功后返回“已创建但待授权/待设岗位”的清单，并提供一键进入批量角色分配、店铺授权和默认组织设置。若暂不支持这些字段，导入成功提示必须明确说明账号还不能直接使用。

### P2-113 用户详情和分配角色接口把密码哈希返回到前端响应体，页面不展示但网络可见

- 现象：用户列表点击登录账号会打开“用户信息详情”抽屉，页面只展示昵称、部门、手机号、邮箱、账号、状态、岗位、角色、登录信息和备注；点击“更多 -> 分配角色”会进入授权角色副页面，页面只展示用户昵称、登录账号和角色表。但这两个接口响应体都包含目标用户的 `password` BCrypt 哈希。
- 证据：前端详情抽屉 `erp-ui/src/views/system/user/view.vue:159-168` 调用 `getUser(userId)` 并把 `res.data` 存入 `info`，模板 `:1-125` 没有展示 password；授权角色页 `erp-ui/src/views/system/user/authRole.vue:3-35` 只展示用户昵称、登录账号和角色信息，`:73-76` 把 `getAuthRole(userId)` 返回的 `response.user` 存入 `form`。API `erp-ui/src/api/system/user.js:14-20` 请求 `/system/user/{userId}`，`:114-119` 请求 `/system/user/authRole/{userId}`。后端 `SysUserController.getInfo` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:246-263`、`authRole` 在 `:378-388` 都要求 `system:user:query`，随后 `userService.selectUserById(userId)` 返回完整 `SysUser`。`SysUserMapper.xml:53-61` 的 `selectUserVo` 明确查询 `u.password`，`selectUserById` 在 `:183-185` 复用该 SQL。本轮用 admin token 只读请求 `GET /system/user/1` 和 `GET /system/user/authRole/2`，响应分别在 `data.password`、`user.password` 返回 `$2a$10$...` BCrypt 哈希；同样请求 `/system/user/list?pageNum=1&pageSize=5` 只返回 `password:null`，列表接口未见哈希值。当前角色矩阵中 `zjl / 总经理` 有实际用户 1 个且持有 `system:user:query`，`common / 普通角色` 也保留该权限。
- 影响：密码哈希不应进入浏览器响应体，即使页面不展示也会被 DevTools、代理、前端错误日志或第三方脚本看到。结合当前默认初始密码、导入弱密码和普通/总经理角色持有用户管理权限的问题，哈希暴露会增加离线撞库和横向账号风险。多账号多店铺场景下，能查看详情或进入角色分配副页面的业务账号不应得到任何密码材料。
- 建议：拆分认证内部查询和管理端详情 DTO。认证链路可以保留带密码哈希的内部 `selectUserByUserName`，但 `/system/user/{userId}`、用户列表、导出、详情抽屉和角色授权相关接口都应使用不含 `password` 的安全 DTO 或 mapper；序列化层也应对 `SysUser.password` 加只写/忽略保护，避免未来新接口复用实体时再次泄露。

### P2-114 个人中心接口也返回当前用户密码哈希，所有登录账号都能在网络响应看到

- 现象：个人中心 `/user/profile` 页面只展示当前用户的用户名、手机号、邮箱、部门、角色、创建日期、基本资料和修改密码 tab；但其接口 `/system/user/profile` 响应体同样包含当前账号的 `password` BCrypt 哈希。这个入口只要求登录，不要求用户管理查询权限。
- 证据：前端个人中心 `erp-ui/src/views/system/user/profile/index.vue:13-38` 展示用户名称、手机号码、用户邮箱、所属部门、所属角色和创建日期，`:47-52` 只挂载“基本资料/修改密码”两个 tab；`:86-90` 把 `getUserProfile()` 返回的 `response.data` 直接存为 `user`。API `erp-ui/src/api/system/user.js:74-80` 请求 `/system/user/profile`。后端 `SysProfileController.profile` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:59-68` 只使用 `@RequiresLogin`，随后调用 `userService.selectUserByUserName(username)` 并 `AjaxResult.success(user)` 返回完整用户对象；`SysUserMapper.xml:53-54` 的公共 `selectUserVo` 包含 `u.password`，`selectUserByUserName` 在 `:178-180` 复用该 SQL。本轮用 admin token 只读请求 `GET /system/user/profile`，响应 `data.password` 返回 `$2a$10$...` BCrypt 哈希。
- 影响：即使只泄露本人哈希，也不应把密码材料送到浏览器和前端运行环境。门店公用电脑、浏览器扩展、前端错误采集、代理调试或 XSS 都可能把哈希带出；如果用户弱密码或初始密码未改，离线破解风险会被放大。与 P2-113 不同，本问题影响所有能登录个人中心的账号，而不只影响具备用户管理查询权限的账号。
- 建议：个人中心接口必须使用专用 Profile DTO，只返回页面需要的 `userName/nickName/phonenumber/email/sex/avatar/dept/roleGroup/postGroup/createTime` 等字段，明确排除 `password`、登录安全字段和权限内部字段。实体层可对 `SysUser.password` 加 `@JsonIgnore` 或只写序列化保护，认证内部查询另走专用对象，避免个人中心、管理详情和未来新接口继续复用带密码哈希的通用实体。

### P2-115 桌面端启动用户信息接口返回当前用户密码哈希，进入系统即暴露到前端

- 现象：桌面端登录后会调用 `/system/user/getInfo` 获取当前用户、角色、权限、头像和密码策略提示；前端只使用用户 ID、部门、账号、昵称、头像、角色权限和 `isDefaultModifyPwd/isPasswordExpired` 等状态，但响应体里的 `user` 对象包含当前账号 `password` BCrypt 哈希。
- 证据：前端 `erp-ui/src/api/login.js:57-63` 定义 `getInfo()` 请求 `/system/user/getInfo`；`erp-ui/src/store/modules/user.js:100-125` 只把 `res.user.userId/deptId/userName/nickName/avatar`、`res.roles`、`res.permissions`、`res.pwdChrtype` 和改密提示状态写入 store/session，没有使用 password。后端 `SysUserController.getInfo` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:184-206` 只要求 `@RequiresLogin`，随后把 `SecurityUtils.getLoginUser().getSysUser()` 原样 `ajax.put("user", user)` 返回。本轮用 admin token 只读请求 `GET /system/user/getInfo`，响应 `user.password` 返回 `$2a$10$...` BCrypt 哈希，同时 `roles_count=1`、`permissions_count=1`、`pwdChrtype=0`、`isDefaultModifyPwd=false`、`isPasswordExpired=false`。
- 影响：这个接口不是用户主动打开某个详情页才触发，而是桌面端进入系统、刷新页面和恢复会话时都会走的基础接口；因此每个登录账号都会把自己的密码哈希暴露给浏览器运行环境。即使哈希只属于本人，也会增加公用电脑、浏览器扩展、前端监控、代理抓包和 XSS 场景下的凭据材料泄露面，且与 P2-114 的个人中心泄露叠加。
- 建议：`/system/user/getInfo` 改用登录态专用 DTO，只返回路由、权限、头像、昵称、部门、密码策略提示等启动所需字段；认证内部使用的 `LoginUser.sysUser.password` 不应被序列化到任何前端响应。可以在 `SysUser.password` 上加 JSON 忽略保护，并为改密校验等后端内部逻辑显式读取哈希。

### P2-116 超级管理员角色在列表页被隐藏操作，但角色授权副路由和接口仍可改成员

- 现象：角色列表中 `roleId=1 / 超级管理员` 行不会显示修改、删除、数据权限、分配用户等操作，给人的预期是超级管理员角色不可被普通角色维护流程改动；但直连 `/system/role-auth/user/1` 对应的授权用户接口仍能加载超级管理员角色成员和未授权候选用户。写接口源码没有像角色修改、数据权限、状态修改、删除那样调用 `checkRoleAllowed`，因此构造请求时存在给普通用户追加超级管理员角色、或取消 admin 用户超级管理员角色的风险。
- 证据：前端角色列表 `erp-ui/src/views/system/role/index.vue:126-153` 对 `scope.row.roleId !== 1` 才渲染“修改/删除/更多/分配用户”，但 `handleAuthUser()` 在 `:562-566` 只是跳转 `/system/role-auth/user/{roleId}`。授权用户页 `erp-ui/src/views/system/role/authUser.vue:134-150` 会按路由参数加载已分配用户，选择用户弹窗 `erp-ui/src/views/system/role/selectUser.vue:91-108` 会按同一 `roleId` 加载未分配候选。后端 `SysRoleController.allocatedList/unallocatedList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:177-197` 只要求 `system:role:list`；`cancelAuthUser/cancelAuthUserAll/selectAuthUserAll` 在 `:203-231` 只要求 `system:role:edit` 并调用对应服务。`SysRoleServiceImpl.checkRoleAllowed()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java:190-197` 会拦截超级管理员角色，但只被角色编辑、数据权限、状态和删除等路径使用；`insertAuthUsers/deleteAuthUser/deleteAuthUsers` 在 `:447-491` 只调用 `checkAuthUserScope`，而 `checkAuthUserScope` 在 `:232-250` 只校验角色数据范围和用户数据范围，不校验超级管理员角色不可操作。本轮只读请求 `/system/role/authUser/allocatedList?roleId=1` 返回 `admin` 1 个已授权用户，`/system/role/authUser/unallocatedList?roleId=1` 返回 24 个候选用户。
- 影响：超级管理员角色的保护在列表 UI 层和部分后端编辑路径上存在，但授权成员路径没有同等保护。管理员或具备角色编辑能力的账号一旦直连副路由、历史标签页或构造请求，就可能改变最高权限角色成员；这会绕过“超级管理员角色不可操作”的安全语义。多账号多店铺场景中，角色成员变更是权限体系根能力，不能只靠列表页隐藏操作入口防护。
- 建议：角色授权成员的所有读写接口都应先调用 `checkRoleAllowed(new SysRole(roleId))` 或等价的超级管理员角色保护；前端路由进入 `/system/role-auth/user/1` 时也应阻断并提示“超级管理员角色不可分配用户”。若确需超级管理员成员维护，应单独设计安全管理员流程，要求二次确认、审计 before/after、至少保留一个超级管理员账号，并禁止把普通业务账号加入 admin 角色。

### P2-117 用户分配角色副页面返回并可提交超级管理员角色，写接口缺少最高权限保护

- 现象：用户列表“分配角色”副页面会返回目标用户可选的全部角色，并且前端只按角色状态判断是否可勾选；在管理员会话下，普通用户的角色表也包含 `roleId=1 / 超级管理员`。写接口 `/system/user/authRole` 只做用户数据范围和角色数据范围校验，没有调用 `checkUserAllowed`、`checkRoleAllowed` 或“至少保留一个超级管理员账号”等最高权限保护，因此构造请求时存在给普通账号追加超级管理员角色、或从超级管理员账号移除最高权限角色的风险。
- 证据：只读请求 `/system/user/authRole/1` 返回 11 个角色，`roleId=1` 的 `flag=true`；`/system/user/authRole/2`、`/system/user/authRole/122` 同样返回 11 个角色且 `admin_role_returned=True`，其中 `roleId=1` 的 `flag=false`，说明超级管理员角色作为可选项进入普通用户授权页。前端 `erp-ui/src/views/system/user/authRole.vue:23-31` 显示角色表，`:88-90` 的 `checkSelectable(row)` 只判断 `row.status === "0"`，`:97-105` 直接把选中 `roleIds.join(",")` 提交。后端 `SysUserController.authRole` 读接口在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:378-388` 返回 `filterVisibleRoles(...)`；管理员会话下 `isCurrentOrTargetAdmin` 为 true，因此不会过滤 admin 角色。写接口 `insertAuthRole` 在 `:417-425` 只要求 `system:user:edit`，执行 `userService.checkUserDataScope(userId)`、`roleService.checkRoleDataScope(roleIds)` 后直接 `userService.insertUserAuth(userId, roleIds)`；`SysUserServiceImpl.insertUserAuth` 在 `:328-332` 会先删除该用户全部角色再插入传入角色。`checkUserAllowed` 虽能拦截超级管理员用户，但该写接口没有调用；`checkRoleDataScope` 只校验数据范围，不拦截 `roleId=1`。
- 影响：P2-116 覆盖的是“从角色侧维护超级管理员成员”的缺口，本问题是“从用户侧给任意账号分配超级管理员角色”的对称缺口。多账号多店铺场景中，用户授权角色是账号生命周期关键动作；如果普通业务账号或总经理角色持有 `system:user:edit`，就不应通过副页面或构造请求触达最高权限角色。即便当前由超级管理员操作，也缺少二次确认和至少保留一个超级管理员的安全约束，误操作会直接影响平台最高权限。
- 建议：用户授权角色写接口应在事务内保护 `roleId=1` 和目标超级管理员账号：普通授权流程不得新增或移除超级管理员角色，除非进入独立安全管理员流程。前端应对 `roleId=1` 隐藏或禁用并显示“超级管理员角色不可在此分配”，后端必须调用 `checkRoleAllowed`/`checkUserAllowed` 等价保护，并在变更前校验至少保留一个启用超级管理员账号，同时写入 before/after 权限审计。

### P2-118 店铺授权页只在创建时读取路由 query，切换目标用户时正文可能停在旧用户

- 现象：用户列表每行“店铺授权”会跳转到 `/system/shop?userId=...&userName=...`，但主内容区按 `path` 作为 `router-view` key，并且店铺授权页只在 `created()` 阶段读取 query。管理员从用户 A 的店铺授权切到用户 B 的店铺授权时，URL 和标签页可能已经变成用户 B，但页面缓存实例仍保留用户 A 的 `routeUserId/currentUser/queryParams`，正文和保存对象不会自动按新 query 重置。
- 证据：用户列表 `erp-ui/src/views/system/user/index.vue:467-475` 的 `handleShopScope(row)` 通过 `this.$router.push({ path: "/system/shop", query: { userId, userName } })` 进入店铺授权。内容区 `erp-ui/src/layout/components/AppMain.vue:4-6` 在 `<keep-alive>` 内渲染 `<router-view :key="key" />`，`:24-26` 的 `key()` 返回 `this.$route.path`，不会把 query 纳入页面实例身份。店铺授权页 `erp-ui/src/views/system/shop/index.vue:149-153` 的 watcher 只处理 `filterText`，`:154-159` 的 `created()` 才调用 `applyRouteUserQuery()`、`getList()`、`getShopTree()`；`:161-167` 读取 `this.$route.query.userId/userName` 并写入 `routeUserId/routeUserName/queryParams.userName`，`:211-231` 再用这些值聚焦并加载用户。该页面没有 `beforeRouteUpdate`、`activated()` 或 `$route.fullPath` watcher；保存时 `submitShopScope()` 在 `:298-315` 使用 `updateUserShop(this.currentUser.userId, checkedKeys)`，依赖缓存中的 `currentUser`。对照同项目其它 query 场景，代码生成页 `erp-ui/src/views/tool/gen/index.vue:227-233` 会在 `activated()` 读取 `t/pageNum`，库存页 `erp-ui/src/views/inventory/stock/index.vue:417-421` 通过 computed 读取 `stockEntry`，并在 `:531-535` 监听 `$route.fullPath` 重新加载，说明 query 敏感页面需要显式处理。
- 影响：这是多账号多店铺授权的关键流程。管理员连续处理多个用户时，地址栏、标签页和正文选中用户可能不一致；若未注意到右侧当前用户仍是旧对象，点击保存会更新旧用户的店铺/仓库授权。P3-114 记录的是标签页按 path 去重，本问题进一步影响正文状态和最终写入对象，风险不只是展示混淆。
- 建议：店铺授权页应把 route query 作为业务上下文处理：在 `beforeRouteUpdate`、`activated()` 或 `$route.fullPath` watcher 中重新执行 `applyRouteUserQuery()`，重置 `currentUser/selectedShopIds/preservedShopIds/queryParams` 并重新聚焦目标用户；有未保存授权时先提示是否放弃。也可以对 `/system/shop` 这类 query 敏感路由使用 `fullPath` 作为 `router-view` key，但要同步处理标签页身份策略。回归用例至少覆盖“用户 A 店铺授权 -> 用户 B 店铺授权 -> 保存”的连续操作，确认保存请求中的 `userId` 与 URL、页面标题和当前行一致。

### P2-119 用户导入结果把 Excel 账号原文拼进 HTML 弹窗，校验失败消息仍可能被危险渲染

- 现象：用户导入弹窗成功后会把后端返回的 `response.msg` 直接拼进 HTML 字符串，并用 `dangerouslyUseHTMLString` 展示“导入结果”。后端用户导入服务为了换行本身也返回 `<br/>` 拼接文本，并把 Excel 中的 `用户账号` 原文拼到成功、更新、已存在和失败消息里。即使 `用户账号` 命中 `@Xss` 校验失败，catch 分支仍会把该原文账号拼进失败消息，前端再按 HTML 渲染。
- 证据：通用导入组件 `erp-ui/src/components/ExcelImportDialog/index.vue:101-107` 在上传成功后执行 `this.$alert("<div ...>" + response.msg + "</div>", "导入结果", { dangerouslyUseHTMLString: true })`。用户导入接口 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:107-116` 调用 `userService.importUser(...)` 后原样 `success(message)` 返回。导入服务 `SysUserServiceImpl.importUser` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:531-564` 多处执行 `successMsg/failureMsg.append("<br/>...账号 " + user.getUserName() + "...")`；`SysUser.userName` 是 Excel 导入字段，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:35-36`，其 getter 虽有 `@Xss` 校验，见 `:157-160`，但校验异常会进入同一 catch 并继续把 `user.getUserName()` 放入返回消息。用户页面的导入按钮由 `system:user:import` 显示，见 `erp-ui/src/views/system/user/index.vue:48`，弹窗挂载在 `:192`。
- 影响：具备用户导入权限的管理员或业务角色上传恶意 Excel 时，导入失败结果本应安全提示错误行，却可能把失败字段作为 HTML 在当前登录会话中渲染。多账号多店铺场景里，用户导入通常由有较高账号权限的人操作；如果导入结果弹窗成为脚本执行点，会叠加当前会话可访问的用户、角色、店铺授权等敏感页面。即使当前只复核源码、未执行写入导入，这个组件模式也会让后续任何导入接口只要把 Excel 原文放进结果消息，就继承同类风险。
- 建议：导入结果不要用 `dangerouslyUseHTMLString` 渲染后端字符串。后端应返回结构化结果数组（行号、字段、错误码、纯文本原因），前端用文本节点和列表渲染；如果必须兼容 `<br/>`，也要先对所有来自 Excel、异常消息和数据库的内容做 HTML 转义，再只允许受控换行。补充回归用例：导入账号名包含 `<img onerror=...>`、`<script>`、HTML 实体和普通特殊字符时，弹窗只能显示文本，不能生成 DOM 节点或执行事件。

### P2-101 角色管理全量权限治理授给业务角色，角色菜单和数据范围可被业务账号改动

- 现象：角色管理决定菜单权限、数据权限和用户分配，是多账号多店铺的核心权限基础设施；但当前 `common / 普通角色` 和 `zjl / 总经理` 都持有角色管理全套页面和按钮权限，用户 `ry` 通过总经理角色实际拥有角色新增、修改、删除、导出、数据权限配置和分配用户入口。业务角色可以新增角色、修改角色菜单树、调整数据范围、停启角色、导出角色数据和删除未分配用户的角色。
- 证据：当前 `sys_role=11`，均为启用未删除；`sys_menu` 中 `101 / 角色管理 / system:role:list` 及 `1007-1011 / system:role:query/add/edit/remove/export` 均为启用可见。MySQL 复核显示 `common` 和 `zjl` 均绑定 `system:role:add,edit,export,list,query,remove`，`user_id=2 / ry / zjl` 实际持有上述全部权限；`zjl` 当前 `data_scope=1` 且绑定 228 个菜单，`common` 绑定 93 个菜单。前端角色页 `erp-ui/src/views/system/role/index.vue:64-97` 用这些权限显示新增、修改、删除、导出，`:132-148` 的行级修改、删除、数据权限和分配用户均由 `system:role:edit/remove` 控制，新增/修改弹窗 `:167-206` 直接提供菜单权限树。后端 `SysRoleController.add/edit/dataScope/changeStatus/remove` 分别使用 `system:role:add/edit/remove`，角色分配/取消用户接口也复用 `system:role:edit`；`SysRoleServiceImpl.insertRole/updateRole()` 会写入或重建 `sys_role_menu`，`authDataScope()` 会更新角色并重建 `sys_role_dept`，`deleteRoleByIds()` 会删除角色菜单和角色部门关联。
- 影响：总经理或普通角色模板一旦被实际分配，就能绕过平台权限管理员改动角色本身，进而间接扩大或收缩多个账号的菜单和数据范围。已有 P2-69、P2-70、P2-37 分别覆盖数据权限复用 edit、菜单授权保存缺少可授权范围校验和分配用户复用 edit，本问题记录的是角色矩阵本身把角色治理全量能力授给业务角色；在多店铺场景中，这会让业务管理边界和平台权限边界混在一起，任何一次误改都可能影响一批账号的可见页面、业务组织范围和敏感操作入口。
- 建议：角色管理写权限默认只给超级管理员、平台运维或安全管理员，`common`、`zjl` 等业务角色最多保留只读查询。角色基础资料、菜单授权、数据权限、分配用户、启停、导出和删除应拆成独立权限点和独立审计事件；修改角色菜单或数据范围前要展示受影响账号数量、菜单数量和组织范围差异，并要求二次确认。

### P2-102 部门组织树写权限授给业务角色，门店/仓库结构可被业务账号改动

- 现象：部门管理在当前系统里不是普通通讯录，它同时承载集团、公司、门店、仓库组织树，是用户归属、店铺授权、组织选择、库存上下文、仓库选择和数据权限的基础；但当前 `common / 普通角色` 和 `zjl / 总经理` 都持有部门管理全套页面和按钮权限，用户 `ry` 通过总经理角色实际拥有部门新增、修改、删除和保存排序权限。业务角色可以新增门店/仓库节点、迁移父级、修改组织类型、停启组织和调整组织排序。
- 证据：当前 `sys_dept` 有 11 个启用未删除节点，组织树为 `GROUP -> COMPANY/WAREHOUSE -> STORE`，其中 `104 / 仓库 / WAREHOUSE` 挂在集团下，6 个门店挂在两个公司下；`sys_menu` 中 `103 / 部门管理 / system:dept:list` 及 `1016-1019 / system:dept:query/add/edit/remove` 均为启用可见。MySQL 复核显示 `common` 和 `zjl` 均绑定 `system:dept:add,edit,list,query,remove`，`user_id=2 / ry / zjl` 实际持有上述全部权限。前端部门页 `erp-ui/src/views/system/dept/index.vue:48-59` 用 `system:dept:add/edit` 显示新增和保存排序，`:111-127` 的行级修改、新增子部门、删除分别使用 `system:dept:edit/add/remove`，新增/编辑表单 `:138-171` 可选择上级部门、部门类型和状态。后端 `SysDeptController.add/edit/updateSort/remove` 分别依赖 `system:dept:add/edit/remove`；`SysDeptServiceImpl.insertDept()` 会按父级写入 `ancestors`，`updateDept()` 会重算当前节点和子节点 ancestors，`updateDeptSort()` 会更新组织排序，删除只在有子部门或有用户时拦截。
- 影响：总经理或普通角色模板一旦被实际分配，就可以改变门店/仓库组织结构，影响 `/select-shop`、`/system/dept/shop-tree`、用户店铺授权、库存组织上下文、销售发货仓库候选、仓库库存和多门店数据权限。已有 P2-38 覆盖“部门类型没有按层级校验”，P3-21 覆盖“删除按钮没有前置引用状态”；本问题记录的是角色矩阵本身把组织树写能力授给业务角色，导致业务账号有能力制造或扩大这些组织结构问题。
- 建议：部门组织树写权限默认只给超级管理员、组织管理员或平台运维，`common`、`zjl` 等业务角色最多保留只读。新增组织、修改组织类型、迁移父级、停启组织、保存排序和删除应拆成独立权限和审批/二次确认；保存前要展示受影响用户、角色数据范围、店铺授权、库存/单据组织和仓库选择影响，组织类型层级校验应与 P2-38 一起后端强制执行。

### P2-121 部门/组织停用只检查子部门，不预检用户主部门、店铺授权和业务影响

- 现象：部门管理可以把门店、仓库或公司组织改为停用；后端只阻止“存在未停用子部门”的停用动作，不检查该组织是否已有用户主部门、店铺/仓库授权、角色数据范围或业务单据。用户新增/编辑/导入虽然前端会过滤禁用部门，但后端保存用户主部门时也只做数据范围检查，不校验部门是否启用。
- 证据：用户表单 `erp-ui/src/views/system/user/index.vue:114-115` 使用 `enabledDeptOptions`，`:311-326` 会把 `TreeSelect.disabled` 节点过滤掉；后端 `TreeSelect(SysDept)` 在 `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/TreeSelect.java:39-44` 仅把停用部门标成 disabled。用户新增/编辑 `SysUserController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:272-323` 只调用 `deptService.checkDeptDataScope(user.getDeptId())`，导入 `SysUserServiceImpl.importUser` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:531-550` 同样只做数据范围检查；`checkDeptDataScope` 在 `SysDeptServiceImpl.java:294-305` 通过 `selectDeptList` 判断可见性，`SysDeptMapper.xml:31-51` 默认只过滤 `del_flag`，不要求 `status='0'`。部门停用入口 `SysDeptController.edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:121-136` 只在 `status=1` 时检查 `selectNormalChildrenDeptById`；当前运行态 11 个组织都启用，用户主部门和 `sys_user_shop` 没有绑定停用/删除组织，但已有 `103 / 研发部门` 10 个用户、`104 / 仓库` 7 个用户、`108 / 市场部门` 4 个用户，说明停用这些节点会影响真实账号。
- 影响：管理员停用一个已有用户或授权的门店/仓库后，组织选择树和店铺授权查询会按启用状态过滤，账号可能突然失去可选业务组织；但用户主部门仍保留在 `sys_user.dept_id`，数据权限、本部门/本部门及以下口径、用户列表和后续账号编辑会出现“主部门存在但不可选”的状态。多店铺场景下，这会让组织停用从基础资料动作变成批量账号和业务上下文故障，且页面没有影响清单或迁移入口。
- 建议：组织停用前必须做影响预检：列出主部门用户数、店铺/仓库授权用户数、角色数据范围引用、库存/单据/薪资/考勤/合同等业务数据和下游组织选择影响；有绑定账号或业务数据时默认禁止直接停用，改走迁移/归档向导。用户新增、编辑和导入后端也应校验主部门存在、未删除且启用，已有用户绑定停用部门时列表和详情应给出明确风险提示。

### P2-103 店铺/仓库授权写权限授给总经理角色，可调整其他账号经营组织范围

- 现象：店铺授权页是多账号多店铺的核心授权入口，保存动作会直接改写用户可管理的门店/仓库范围；当前总经理角色持有店铺授权页面、查询和保存权限，实际用户 `ry` 通过总经理角色可以进入页面并保存其他账号的组织授权。服务层虽然限制非超级管理员只能分配自己已有的门店/仓库，但 `ry` 自身已授权全部 7 个经营组织，且总经理角色是全数据范围，因此在当前运行态下可以覆盖其他账号的完整经营组织范围。
- 证据：当前 `sys_user_shop=43`，`admin` 和 `ry` 都显式授权 `103 / 研发部门`、`104 / 仓库`、`105 / 测试部门`、`106 / 财务部门`、`107 / 运维部门`、`108 / 市场部门`、`109 / 财务部门` 共 7 个经营组织；`sys_role` 中 `zjl / 总经理 / data_scope=1`，绑定用户只有 `ry`。`sys_menu` 中 `181 / 店铺授权 / system:userShop:list`、`1190 / system:userShop:query`、`1191 / system:userShop:edit`、`1192 / system:userShop:list` 均启用，并授权给 `admin` 和 `zjl`；`ry` 实际持有 `system:userShop:list/query/edit`。前端 `erp-ui/src/views/system/shop/index.vue:78` 的保存按钮使用 `system:userShop:edit`，`:304-306` 调用 `updateUserShop(this.currentUser.userId, checkedKeys)`；API `erp-ui/src/api/system/userShop.js` 以 `PUT /system/user/shop/{userId}` 保存。后端 `SysUserShopController.tree/getInfo/batch/save` 分别使用 `system:userShop:list/query/edit`，保存前只调用 `userService.checkUserDataScope(userId)`；`SysUserShopServiceImpl.saveUserShops()` 对非管理员只校验 `checkAssignableShopScope(operatorUserId, ..., uniqueDeptIds)`，即所选组织是否已经属于操作人，没有像用户修改/删除那样额外调用 `checkUserAllowed` 保护超级管理员目标账号。
- 影响：总经理账号可以把任意可见用户授予或移除自己已有的门店/仓库范围，实际等同于调整这些账号后续的组织选择、库存上下文、单据可见范围和门店/仓库业务入口。多店铺场景里，这属于平台账号/组织授权职责，而不应跟经营管理角色混在一起；已有 P2-100 记录用户管理全量账号治理，P2-37 记录用户/角色授权写权限复用，本问题专门记录 `system:userShop:*` 独立店铺授权入口本身的角色矩阵和目标保护边界不足。
- 建议：店铺/仓库授权写权限默认只给超级管理员、账号管理员或组织授权管理员，`zjl` 等经营角色最多保留只读查询或只看本范围授权。保存授权时应禁止非超级管理员修改超级管理员和安全管理员目标账号，禁止普通经营角色给其他账号扩权到完整组织集合；如果确实允许区域/总经理代管，应拆出“本组织范围授权”和“全量组织授权”两类权限，并在保存前展示变更前后组织差异、受影响账号、是否移除默认组织和二次确认，同时写入权限变更 before/after 日志。

### P2-166 店铺授权页允许无确认清空用户全部门店/仓库授权

- 现象：店铺授权页右侧组织树可以全部不勾选后直接点击“保存”，前端不会提示“将清空该用户全部组织授权”，也不要求至少保留一个门店或仓库。对超级管理员保存时，后端会先删除该用户所有 `sys_user_shop` 记录；空数组仍被当作成功返回。
- 证据：前端 `erp-ui/src/views/system/shop/index.vue:72-78` 的右侧卡片只显示“组织授权”和普通保存按钮，`submitShopScope()` 在 `:298-315` 直接用 `getCheckedShopIds()` 结果调用 `updateUserShop(this.currentUser.userId, checkedKeys)`，`getCheckedShopIds()` 在 `:421-428` 会在无勾选节点时返回空数组；API `erp-ui/src/api/system/userShop.js:29-34` 将该数组原样 PUT 到 `/system/user/shop/{userId}`。后端 `SysUserShopController.save()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java:84-91` 只做数据范围检查后进入服务层；`SysUserShopServiceImpl.saveUserShops()` 在 `:164-178` 规范化数组后，超级管理员路径先执行 `deleteUserShopByUserId(userId)`，再插入新绑定；`insertUserShopBindings()` 在 `:194-199` 对空数组返回 `1`，即成功。Mapper `erp-modules/erp-system/src/main/resources/mapper/system/SysUserShopMapper.xml:62-64` 的删除语句是按用户删除全部授权。本轮只读 MySQL 复核当前 `sys_user_shop=43`、默认授权 `Y=24`，已有 `user_id=103 / user_name=123 / scope_count=0` 的启用账号样本，未调用写接口。
- 影响：管理员误清空后，账号仍可能保持启用和角色完整，但失去全部业务组织；登录后组织选择、OA、库存、销售、调拨等依赖 `Dept-NumId` 的页面会表现为空组织、空数据或后端错误。清空组织是一种高风险撤权/停用业务访问动作，当前没有目标用户、授权前后差异、是否清空全部、默认组织变化和在线会话影响的二次确认；与 P1-17 的无组织账号可登录结果问题叠加，会制造“账号看似正常但业务入口异常”的配置事故。
- 建议：普通保存前至少要求保留一个可用门店/仓库；若业务允许清空，应改成独立“清空组织授权/停用业务访问”高危动作，二次确认目标账号、昵称、用户 ID、当前授权、清空后影响和在线会话处理。后端也应拒绝普通保存接口提交空数组，或要求 `allowEmpty=true + reason` 并写入权限变更 before/after 审计。

### P2-32 发货通知执行权限未覆盖仓库角色，销售发货相关权限仍有新旧混用

- 现象：销售单提交后前端主流程是“生成发货通知 -> 执行发货”，执行发货会扣仓库库存；但当前仓库角色没有 `inv:deliveryNotice:*` 权限和发货通知菜单入口，门店/运营/管理角色之间的生成、执行权限也不一致。早前命中的 `inv:sales:notice` 当前已从运行态权限树消失，但后端旧销售出库接口仍要求停用隐藏的 `inv:sales:deliver` 后直接返回“请通过发货通知执行发货”。
- 证据：2026-06-24 MySQL 复核当前运行态显示，`4202 / inv:deliveryNotice:add` 授给 `admin/dz/yyjl/zdjl/zjl`，`4203 / inv:deliveryNotice:deliver` 授给 `admin/yyjl/zdjl/zjl`，仓库角色没有发货通知权限；`4035 / inv:sales:deliver` 为 `status=1`、`visible=1`，没有当前角色授权；`inv:sales:notice` 查询返回 0 行。`InvSalesController.deliver()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesController.java:75-80` 仍要求 `inv:sales:deliver` 后直接返回提示；销售页 `erp-ui/src/views/inventory/sales/index.vue:59` 生成通知按钮使用 `inv:deliveryNotice:add`，发货通知页执行按钮使用 `inv:deliveryNotice:deliver`。
- 影响：如果实际职责是仓库发货，当前仓库角色仍无法进入发货通知处理；如果实际职责是门店或运营发货，不同业务角色又会出现“能生成但不能执行”或“能执行但没有仓库候选”的割裂。旧 `inv:sales:deliver` 接口保留在后端和权限树中，也让管理员难以判断销售出库到底走哪条入口。
- 建议：明确发货通知执行角色。如果由仓库发货，应给仓库角色配置 `inv:deliveryNotice:list/query/deliver/export` 和相应菜单入口；如果由门店/运营执行，应在页面文案中说明“由门店/运营选择授权仓库发货”。清理停用旧销售出库节点，并移除或内部化已经只返回提示的 `/sales/deliver/{orderId}` 旧接口权限。

### P2-164 跨店销售发货通知要求选择仓库，但自动跨店调拨又要求来源为门店

- 现象：跨店销售发货通知执行时，页面和后端都把发货来源定义为“发货仓库”；但销售单全部发完后，后端会把该仓库 ID 作为 `cross_store` 调拨单的来源，再进入调拨服务的异店调货校验。调拨服务要求 `cross_store` 来源必须是门店，导致同一跨店销售发货流程前后组织类型契约冲突。
- 证据：发货通知执行弹窗使用 `WarehouseSelect purpose="deliverySource"`，提示“请选择发货仓库”，并只保留 `deptType === "WAREHOUSE"` 的选项，见 `erp-ui/src/views/inventory/deliveryNotice/index.vue:86-97` 和 `erp-ui/src/views/inventory/components/WarehouseSelect.vue:101-104`。后端 `InvDeliveryNoticeServiceImpl.deliverNotice()` 先对 `warehouseId` 执行 `assertDeptType(warehouseId, DEPT_TYPE_WAREHOUSE, "发货仓库不合法")`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:174-176`；但跨店发货完成后 `createCrossStoreTransferForReceipt()` 又把同一个 `warehouseId` 写入 `fromDeptId/fromWarehouseId`，并设置 `transferType="cross_store"`，见同文件 `:435-448`。随后 `InvTransferServiceImpl.createDeliveredCrossStoreTransfer()` 调用 `saveDraft(..., false)`，而 `validateTransferCreationRules()` 对 `cross_store` 执行 `assertDeptType(sourceDeptId, DEPT_TYPE_STORE, "异店调货来源必须为门店")`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:178-181`、`:577-580`。当前运行态正式销售单和发货通知均为 0，无法用存量单据回放；现有 `inv_transfer_order` 两条都是 `warehouse` 类型，不覆盖该跨店销售分支。
- 影响：跨店销售单如果按页面要求选择真实仓库发货，完成最后一次发货时会在自动生成跨店调拨单阶段失败，用户已经填写发货数量并确认扣库存后才看到错误；事务会回滚库存扣减，但跨店销售发货和目标门店收货链路无法闭环。该问题还会让 P2-32 中“到底由仓库、门店还是运营执行发货”的职责不清变成真实流程阻断。
- 建议：先统一跨店销售的业务模型：如果发货源必须是仓库，就不要生成 `transferType=cross_store` 的异店调货单，改用仓库到门店的调拨/发货通知收货模型；如果业务要求来源门店发货，发货通知选择器就不应命名和校验为仓库，并应只允许来源门店或其可发库存组织。自动生成调拨前要复用同一套组织类型校验，并在确认框中明确“扣减哪个组织库存、生成哪类收货单、由哪个目标门店收货”。

### P2-165 调拨审批电脑端只支持通过，不支持后端已有的驳回和审批意见

- 现象：调拨处理中页对 `submitted` 状态只显示一个“审批”按钮，点击后直接确认“审批通过”，请求只发送 `action:"approve"`；页面没有“驳回”动作，也没有审批意见输入。后端审批 DTO、服务层和数据库任务表已经支持 `reject` 与 `comment`，但电脑端用户无法使用。
- 证据：前端行按钮只有 `@click="handleApprove(scope.row)"`，见 `erp-ui/src/views/inventory/transfer/index.vue:81`；`handleApprove()` 固定调用 `approveTransfer({ transferId: row.transferId, action: "approve" })`，见同文件 `:968-970`，确认文案也只写“确认审批通过要货单”，见 `:1127-1134`。后端 `InvTransferApprovalRequest` 包含 `transferId/taskId/action/comment`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvTransferApprovalRequest.java:5-8`；`InvTransferApprovalServiceImpl.approve()` 在 `:168-175` 按 `approve/reject` 分支处理，`:333-341` 和 `:393-421` 分别把通过/驳回意见写入任务和状态日志。`InvTransferApprovalTaskMapper.xml:84-92` 会更新 `comment`。当前运行态 `inv_transfer_approval_task` 两条已审批任务的 `comment` 均为 `NULL`，审批实例的 `reject_action` 为 `back_to_draft`，但该驳回动作在桌面端没有入口。
- 影响：审批人遇到错误要货单时，电脑端不能按流程驳回，只能审批通过或让发起人/管理员走其他取消路径；这会绕开规则中的 `reject_action`，让“审批拒绝退回草稿/关闭单据”的配置形同无效。即使审批通过，任务表也无法沉淀审批意见，后续 P2-14 所述审批轨迹补齐后仍会看到空意见，跨店/跨仓争议只能靠操作时间猜测原因。
- 建议：把调拨审批做成真正的审批弹窗：展示当前节点、候选岗位/候选人、调拨单摘要和规则快照摘要，提供“通过 / 驳回”动作，驳回时强制填写意见，通过时允许填写意见；请求按动作发送 `action` 和 `comment`，必要时传当前 `taskId`。若短期不交付驳回能力，应隐藏或下线规则里的 `reject_action` 配置，并在文案中明确调拨审批当前只能通过，避免规则页承诺和审批页能力不一致。

### P2-33 店铺授权页直连后空白，但授权接口正常返回数据

- 现象：直连 `/system/shop` 后页面只剩空白壳和隐藏的“清空 / 确定”控件，没有用户列表、组织授权树、保存按钮或空态提示。
- 证据：2026-06-23 浏览器实测 `/system/shop`，`#app.innerText` 长度为 0，`document.querySelectorAll('.el-table').length=0`，`forms=0`，树节点为 0；同一 admin 会话手动带 `Authorization` 调用 `/system/user/list` 返回 `total=25`，`/system/user/shop/tree` 返回完整授权树，`/system/user/shop/batch?userIds=1,119,120,121,122` 返回 `{1:[104,103,105,106,107,108,109],119:[103],120:[104],121:[103,108],122:[103]}`，`/system/user/shop/121` 返回 `shopIds=[103,108]`、`editableShopIds=[103,108]`。菜单 `181 /system/shop` 和前端 `erp-ui/src/views/system/shop/index.vue` 均存在。
- 复核：2026-06-23 重新启动前端 dev server 后再次直连 `/system/shop`，页面能显示 1 个用户表、1 个筛选表单、保存按钮和 25 条用户数据，说明该问题不是后端授权接口缺失，而是运行态/路由加载稳定性问题；仍需覆盖直连、刷新、菜单点击和新建用户跳转场景，不能只按重启后一次成功关闭。
- 影响：管理员无法通过电脑端稳定维护用户门店/仓库授权，尤其会阻断新建用户后的授权闭环、现有多门店用户授权复查和深链/刷新场景下的日常维护。接口有数据但页面空白，也会让排查方向误判成“没有授权数据”。
- 建议：把 `/system/shop` 的直接打开、刷新、菜单点击、新建用户跳转纳入路由稳定性回归；页面加载不到动态路由或权限数据时应显示明确错误/重试，而不是空白壳。系统授权页不应依赖当前库存组织上下文，至少要能在未选择门店/仓库或仓库上下文下稳定加载。

### P2-34 组织选择页首次进入显示 0 个组织，点击刷新后才恢复授权树

- 现象：直连 `/select-shop` 首次显示“0 个业务组织可选 / 暂无组织数据”，当前选择区却残留“仓库 当前仓库可操作采购、入库、发货、仓库库存和盘点”，点击页面“刷新”后才恢复为“7 个业务组织可选”并展示完整组织树。
- 证据：2026-06-23 浏览器实测首次进入 `/select-shop` 时树节点为空、正文包含“0 个业务组织可选 / 暂无组织数据”；点击页面“刷新”后出现 `金英灵韵`、`杭州名田`、`上海沐茶`、`仓库` 等授权节点。相同 token 手动带 `Authorization` 调用 `/system/dept/shop-tree` 返回 code 200 和完整组织树，说明后端授权数据可用。
- 复核：2026-06-23 重新启动前端 dev server 后再次直连 `/select-shop`，页面直接显示“7 个业务组织可选”、5 个树节点和刷新/退出/清除/进入系统按钮；当前复测已恢复，但早前首次空树说明加载顺序或旧运行态仍可能把用户带到错误空态。
- 影响：登录或切换组织后的第一屏可能误导用户以为账号没有任何门店/仓库授权；新用户、单门店账号和仓库账号都可能卡在选择组织页，需要知道手动刷新才能进入系统。页面还同时显示“无组织”和残留当前仓库，状态语义冲突。
- 建议：选择组织页应以 `/system/dept/shop-tree` 成功返回作为组织列表唯一依据；在 token/路由未就绪时显示加载态或自动重试，不应提前落入“暂无组织数据”。清空旧选择状态应发生在新树加载成功之后，并结合 P3-03 为单组织账号自动预选或直接进入。

### P2-35 通知公告管理页直连空白，无法维护公告和阅读用户

- 现象：直连 `/system/notice` 后页面只剩空白壳和隐藏的“清空 / 确定”控件，没有公告搜索区、公告表格、新增按钮、阅读用户按钮或空态文案。
- 证据：2026-06-23 浏览器实测 `/system/notice`，`#app.innerText` 长度为 0，`.el-table` 数量为 0，表单数量为 0，可见按钮只有隐藏主题色控件“清空 / 确定”，无 console error。数据库 `sys_notice=0`、`sys_notice_read=0`，只读 API `/system/notice/list` 返回 `total=0`，`/system/notice/listTop` 返回 `data=[]、unreadCount=0`；首页顶部公告入口能显示“暂无公告”。源码 `erp-ui/src/views/system/notice/index.vue:1-183` 已包含搜索、新增、修改、删除、标题详情和阅读用户弹窗，菜单 `107 /system/notice` 和按钮权限 `system:notice:add/edit/remove/query` 均启用。
- 复核：2026-06-23 重新启动前端 dev server 后直连 `/system/notice`，页面能显示 1 个筛选表单、1 个表格、新增/修改/删除按钮和“暂无数据”空态；当前复测已恢复，问题应继续作为路由/运行态稳定性回归项，而不是后端数据缺失。
- 影响：管理员和总经理无法通过电脑端发布或维护通知公告，也无法查看公告阅读用户；当前顶部公告空态虽然正确，但一旦需要发布公告，管理入口不可用会阻断完整流程。
- 建议：把 `/system/notice` 加入系统管理路由稳定性回归，修复直连、刷新、菜单点击后的组件加载；空数据时应展示可操作空态和“新增公告”，而不是空白壳。阅读用户副弹窗也需要在有公告数据后补测搜索、分页和已读时间。

### P2-36 角色分配用户候选列表不区分组织类型，店长角色可选到仓库/集团/公司/无部门用户

- 现象：进入 `103 / 店长` 的“分配用户”副页面并打开“添加用户”，候选列表包含管理员、总经理、公司用户、仓库用户和无部门用户；页面没有展示部门、组织类型、当前角色或店铺授权范围，管理员只能凭用户名判断是否适合分配店长角色。
- 证据：`/system/role/authUser/unallocatedList?roleId=103` 当前返回 13 个候选，响应对象包含 `deptId/email/phonenumber/status/createTime`，也带 `dept` 对象和 `password:null` 占位，但 `dept.deptName/deptType` 全为空；其中 2 个候选返回了完整邮箱和手机号。MySQL 对照同一批候选的真实部门类型分布为 `GROUP=1`、`COMPANY=1`、`WAREHOUSE=7`、`STORE=3`、`NO_DEPT=1`，可见 `admin`、`ry`、`12345`、`liu123456`、`123`、`dushuai` 等非门店用户；其中多数已有其他角色和店铺/仓库授权范围，例如 `ry` 是总经理并授权 7 个经营组织，多个仓库候选已是仓库管理员。已分配给店长角色的 12 个用户当前全部为 `STORE` 部门，但页面和接口没有把这种差异展示给管理员。前端 `erp-ui/src/views/system/role/selectUser.vue:27-42` 和已分配页 `erp-ui/src/views/system/role/authUser.vue:62-77` 只展示用户名、昵称、邮箱、手机、状态、创建时间；后端 mapper `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:160-175` 的候选 SQL 只 select `u.dept_id/u.email/u.phonenumber`，没有 select `d.dept_name/d.dept_type`，也没有返回当前角色或 `sys_user_shop` 授权摘要。`sys_role` 表结构仅有 `role_name/role_key/data_scope` 等字段，没有角色适用组织类型；`SysRoleServiceImpl.checkAuthUserScope` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java:232-250` 只校验角色和用户数据范围，没有校验角色适用的组织类型。
- 影响：店长、仓库管理员、运营等角色可能被分配给不匹配的组织用户，导致菜单、按钮和业务流程职责错配；多账号多店铺场景下尤其容易把仓库用户误授为门店角色，或把管理员/总经理混入普通店长角色。页面还把邮箱、手机号放在最显眼的候选列，却不给部门名称、组织类型、现有角色和授权范围，管理员既看到了隐私字段，又缺少真正用于判断授权是否合理的信息。
- 建议：为角色补充“适用组织类型/角色族”元数据，分配前按 `dept_type` 和店铺授权范围过滤不兼容用户；列表至少展示部门、组织类型、当前角色、已授权店铺/仓库，并对跨组织类型授权要求二次确认或禁止提交。联系方式默认应脱敏或收起，除非当前授权动作确实需要用手机号/邮箱识别用户。

### P3-153 角色“添加用户”弹窗有分页多选，但没有跨页已选状态和选择清理提示

- 现象：角色分配用户的“添加用户”副弹窗默认分页大小为 10，当前店长角色未分配候选已有 13 人；弹窗支持多选和分页，但没有跨页已选计数、已选用户清单、清空选择或“仅当前页有效”的提示。管理员在第一页勾选后翻页继续选人时，容易误以为前一页选择会被稳定保留。
- 证据：`erp-ui/src/views/system/role/selectUser.vue:27-50` 的候选表格有 selection 列和分页，但没有 `row-key`、`:reserve-selection="true"` 或已选用户摘要；`:80-86` 默认 `pageSize=10`，`:91-95` 打开弹窗只设置 `roleId` 并拉列表，没有重置 `userIds` 或调用 `clearSelection()`；`:99-108` 每次 `selection-change` 都用当前表格 selection 覆盖 `userIds`；`:121-132` 直接把 `userIds.join(",")` 提交给 `authUserSelectAll`。运行态 `roleId=103 / 店长` 的未分配候选为 13 人，已经超过一页。
- 影响：多账号批量授权时，管理员需要在多页候选中挑人，但页面没有清楚告诉他“已选了哪些人、跨页是否保留、提交会包含哪些用户”。这会导致漏授权、重复打开弹窗补授权，或把上次残留/当前页选择误当作完整选择；在门店、仓库、运营角色批量维护时体验不稳定。
- 建议：如果要支持跨页批量选择，表格应设置稳定 `row-key` 和 `reserve-selection`，弹窗顶部展示已选人数和已选用户清单，并提供清空选择；打开和关闭弹窗时显式重置 `userIds` 和表格 selection。若只支持当前页选择，应隐藏跨页误导，文案写明“仅选择当前页用户”，提交确认中列出用户账号和角色名称。

### P2-37 用户/角色编辑权限同时包含授权写操作，缺少独立授权权限边界

- 现象：用户分配角色和角色分配/取消用户都被归入普通编辑权限，系统没有单独的“授权角色/分配用户”权限点。
- 证据：`SysUserController.authRole` 写接口使用 `@RequiresPermissions("system:user:edit")`，`SysRoleController.insertAuthUser`、`cancelAuthUser`、`cancelAuthUserAll` 使用 `@RequiresPermissions("system:role:edit")`；`sys_menu` 当前只有 `system:user:add/edit/remove/export/import/resetPwd` 和 `system:role:add/edit/remove/export` 等按钮权限，没有 `system:user:authRole`、`system:role:authUser`。数据库中 `普通角色` 和 `总经理` 都配置了用户/角色管理编辑类权限，虽然普通角色当前没有用户绑定。
- 影响：只要被授予用户或角色编辑权限，就可能同时具备调整账号角色关系的能力；这比修改用户资料或角色基础信息风险更高，容易越过最小权限边界。
- 建议：新增独立权限码，例如 `system:user:authRole`、`system:role:authUser`、`system:role:cancelAuthUser`，并在菜单按钮、前端指令和后端注解同时拆分；上线前重新核对普通角色、运营、总经理等角色是否确实需要授权写权限。

### P2-38 部门类型没有按组织层级校验，后续可维护出不合理的门店/仓库树

- 现象：部门管理页面已经展示 `GROUP / COMPANY / STORE / WAREHOUSE` 类型，但新增和编辑时四种类型始终都可选，系统没有根据上级组织限制可选类型，也没有说明仓库应挂在集团还是公司、门店只能挂在公司等规则。
- 证据：当前数据库部门树本身是合理的：`GROUP` 下挂 `COMPANY` 和 `WAREHOUSE`，`COMPANY` 下挂 `STORE`。但前端 `erp-ui/src/views/system/dept/index.vue:238-243` 固定提供四个类型选项，`reset()` 默认 `deptType="STORE"`；新增弹窗只按所点行设置 `parentId`，不会按父级类型过滤选项。后端 `SysDeptController.add/edit` 只校验名称唯一、父级不为自己、停用子部门等通用规则；`SysDeptServiceImpl.insertDept` 只校验父级存在、父级启用、数据权限和空类型默认门店，没有层级合法性校验。
- 影响：管理员后续可能创建“门店下面的仓库”“仓库下面的门店”“集团下面直接挂门店”等结构，组织选择页、仓库列表、门店授权、库存上下文和多店铺数据范围都会被错误组织树放大影响。
- 建议：明确组织层级约束并前后端同时校验，例如根节点只允许集团，集团下允许公司/仓库，公司下允许门店，门店和仓库不允许再挂业务组织；弹窗应根据父节点类型动态收窄部门类型选项，并在迁移/编辑时校验已有子节点是否兼容。

### P2-39 系统监控和日志多个主列表直连刷新后空白，只有调度日志副页能稳定渲染

- 现象：在当前管理员浏览器会话中，首页 `/index` 正常，调度日志副页面 `/monitor/job-log/index/0` 能显示空态；但 `/monitor/online`、`/system/log/logininfor`、`/monitor/job`、`/system/log/operlog` 直连并刷新等待 10 秒后正文仍为空，页面没有表格、搜索区、按钮或错误提示。
- 证据：2026-06-23 浏览器/CDP 复测上述 4 个主列表，`#app.innerText` 长度均为 0，无可见按钮和表头；同一会话访问 `/index` 可显示“仓库：仓库”，访问 `/monitor/job-log/index/0` 可显示调度日志空态，说明不是整体登录失效。早前数据库仍有 `sys_logininfor=463`、`sys_oper_log=241`、`sys_job=3`，Redis 有 23 个 `login_tokens:*`，数据源并不为空。
- 当前复核：最新运行态日志/任务数量为 `sys_logininfor=501`、`sys_oper_log=244`、`sys_job=3`、`sys_job_log=0`；本段保留早前浏览器路由空白取证，旧/非运行态启用钉钉同步任务另见 P2-83。
- 影响：管理员无法稳定进入在线用户、登录日志、操作日志和定时任务主列表，审计追踪、任务启停和在线会话管理都会被阻断；更糟的是页面没有错误提示，用户只能看到空白。
- 建议：把监控和日志主列表纳入路由稳定性专项回归，覆盖菜单进入、直连、刷新、从副页面返回四种路径；路由组件加载失败时应显示可诊断错误或重试，而不是空白。

### P2-40 停用的定时任务仍暴露“执行一次”，后端手动执行不校验任务状态

- 现象：当前 3 个内置定时任务全部为停用状态，但定时任务行级“更多”菜单源码仍会显示“执行一次”；后端手动执行只检查 Quartz 任务是否存在，不检查任务是否停用。
- 证据：数据库 `sys_job.status=1` 三条，分别为系统默认无参、有参、多参任务；前端 `erp-ui/src/views/monitor/job/index.vue:140-146` 不按状态隐藏“执行一次”，`handleRun` 只弹确认后调用 `runJob`。后端 `SysJobController.run` 调用 `jobService.run(job)`，`SysJobServiceImpl.run` 在 `scheduler.checkExists(jobKey)` 为真时直接 `scheduler.triggerJob(jobKey, dataMap)`，没有判断 `properties.getStatus()` 是否为停用。本轮遵守只读审计，未点击执行一次。
- 当前复核：最新运行态仍为 3 条系统默认任务且均为停用；本段“停用任务可手动执行”的源码问题仍成立。旧/非运行态曾出现启用的 `钉钉打卡自动同步` 目标缺失风险，另见 P2-83。
- 影响：管理员可能认为停用任务不会运行，却仍能通过手动执行触发任务；这会让“停用”语义不完整，也可能误触发清理、同步、通知等后台任务。
- 建议：停用任务行隐藏或禁用“执行一次”，并在后端 `run` 中拒绝停用任务；如果业务需要允许手动执行停用任务，应把按钮文案改为“临时执行停用任务”并加强确认文案。

### P2-41 在线用户强退缺少当前会话保护和会话身份提示

- 现象：在线用户页源码对每一行都显示“强退”，后端按 tokenId 直接删除 Redis 登录 token，没有看到当前会话保护、当前用户标记或关键会话二次确认。
- 证据：早前 Redis 查询有 23 个 `login_tokens:*`；前端 `erp-ui/src/views/monitor/online/index.vue:48-56` 对所有行显示强退按钮，`handleForceLogout` 只确认用户名后调用 `forceLogout(row.tokenId)`；后端 `SysUserOnlineController.forceLogout` 直接 `redisService.deleteObject(CacheConstants.LOGIN_TOKEN_KEY + tokenId)`。本轮因 `/monitor/online` 主列表空白未能从 UI 截到当前行，但源码和后端路径没有自保护逻辑。
- 影响：管理员可能误强退自己的当前浏览器会话，或者在多端登录时无法区分哪个 token 对应哪个设备/组织上下文；多账号多店铺测试时尤其容易把正在审计的会话踢掉。
- 建议：在线用户列表标记“当前会话”，禁用当前 token 的强退按钮；列表增加登录设备、组织上下文或最近活动时间；强退他人时提示被强退账号、IP、登录时间和会话编号。

### P2-96 普通角色和总经理持有系统监控及调度任务破坏性权限

- 现象：当前运行态把系统监控外链、在线用户强退、定时任务新增/修改/删除/启停/导出等运维级权限授给了 `common / 普通角色` 和 `zjl / 总经理`；其中有效账号 `ry` 绑定总经理角色。定时任务虽然当前只有 3 条停用内置任务，但这些权限允许业务角色维护后台任务和强退在线会话。
- 证据：MySQL 复核 `sys_role_menu + sys_menu` 显示 `common` 与 `zjl` 均持有 `monitor:online:list/forceLogout/query/batchLogout`、`monitor:job:list/query/add/edit/remove/changeStatus/export`、`monitor:sentinel:list`、`monitor:nacos:list`、`monitor:server:list`。`sys_user_role` 显示 `user_id=2 / ry / ERP` 绑定 `zjl`。前端定时任务页 `erp-ui/src/views/monitor/job/index.vue:42-92` 暴露新增、修改、删除、导出、日志按钮，`:114-146` 暴露状态开关、执行一次和调度日志；后端 `SysJobController` 对新增、修改、启停、执行一次、删除分别使用上述 `monitor:job:*` 权限。在线用户强退前端和后端见 P2-41。
- 影响：总经理或后续绑定普通角色的账号可强退他人会话、启停或删除调度任务、修改 cron 和调用目标，影响考勤同步、日志清理、库存预警等后台能力。多账号多店铺场景下，业务角色拿到系统运维权限会让权限边界和责任审计失真。
- 建议：把系统监控运维权限收回到超级管理员/运维角色；业务管理角色最多保留只读监控入口或经过脱敏的在线用户查看。定时任务的新增、修改、删除、启停、执行一次、导出要分成独立权限并默认不授给业务角色；角色模板应把监控外链和强退能力从 `common/zjl` 移除。

### P2-108 调度日志查询、导出和清空复用定时任务权限，审计证据没有独立保护

- 现象：调度日志副页面没有独立权限模型。能查看定时任务列表/详情的账号也能查看调度日志列表/详情；能删除定时任务的账号也能删除或清空调度日志；能导出定时任务的账号也能导出调度日志。运行态权限树没有任何“调度日志”页面或按钮权限节点。
- 证据：MySQL 复核 `sys_menu` 中 `perms like 'monitor:job%log%'`、菜单名包含“调度日志/任务日志”的权限节点数量为 0；`monitor:job:query/remove/export` 只作为 `1049/1052/1054` 定时任务按钮存在，并授给 `common,zjl`。前端 `monitor/job/log.vue` 的删除、清空、导出、详情按钮分别使用 `monitor:job:remove/export/query`；后端 `SysJobLogController.list/getInfo/remove/clean/export` 分别要求 `monitor:job:list/query/remove/remove/export`。当前 `sys_job_log=0`，但权限边界已经由代码和菜单确定。
- 影响：调度日志是排查后台任务执行、失败原因和人工清理行为的审计证据。当前模型无法只允许查看任务而禁止清空日志，也无法只允许导出日志而禁止导出任务配置；一旦业务角色持有任务删除权限，就同时具备销毁调度日志的能力。多店铺环境里，考勤同步、日志清理、库存预警等任务出问题时，审计证据可能被过宽权限清掉。
- 建议：新增独立 `monitor:jobLog:list/query/remove/clean/export` 权限节点，调度日志页面和 `SysJobLogController` 全部改用日志权限；“清空全部日志”应单独高危授权并二次确认，默认只给超级管理员或运维角色。定时任务删除权限不应隐含调度日志删除能力。

### P2-109 供应商改名不会同步或保护商品引用，采购链路仍按名称字符串匹配

- 现象：供应商档案可以直接编辑供应商名称，但商品、采购单和采购退货都保存 `supplier_name` 字符串，没有 `supplier_id` 作为稳定外键。供应商被商品引用后如果改名，既不会同步商品上的旧供应商名称，也没有服务层禁止或迁移引用。
- 证据：运行态 `inv_supplier` 17 条，16 个集团供应商名称当前被 44 条商品引用，例如“武夷山市大齐茶业有限公司”被 8 条商品引用、“御龙坊茶业”被 6 条商品引用。`inv_product` 领域对象只有 `supplierName/supplierPhone`，没有 `supplierId`；`InvProductServiceImpl.applySupplierFromCatalog()` 通过 `selectInvSupplierByNameAndShop` 或 `selectActiveSupplierByNameInDeptChain` 按名称查供应商，采购保存时 `InvPurchaseServiceImpl` 也收集商品 `supplierName` 后写入采购单。供应商编辑页 `erp-ui/src/views/inventory/supplier/index.vue` 允许修改 `supplierName`，后端 `InvSupplierServiceImpl.saveSupplier()` 对已有供应商只调用 `assertAndGetScopedSupplier()` 后 `updateInvSupplier(supplier)`，没有检查旧名称是否被 `inv_product`、采购单或采购退货引用，也没有同步这些表。删除保护虽然会按当前供应商名称查商品和历史单据，但改名后旧商品仍保留旧名称，保护口径随之失效。
- 影响：一旦管理员把已引用供应商改名，商品详情、采购商品选择、供应商详情供货商品、采购单供应商汇总、采购退货供应商筛选和供应商删除保护都会出现同一供应商的旧名/新名分裂。多店铺共享主数据下，这会让采购员以为商品供应商不存在或不可用，也会让供应商对账和历史追溯依赖人工识别名称。
- 建议：供应商引用改为稳定 `supplier_id`，商品、采购单和退货单保存供应商 ID 与名称快照；短期内至少在修改供应商名称前检查商品、采购单、采购退货引用并阻止直接改名，提供“供应商更名/合并”迁移流程，展示受影响商品数和历史单据数并写入审计日志。

### P2-120 供应商暂停、终止或停用后，采购制单仍只按商品上的供应商名称放行

- 现象：供应商档案有“合作中 / 暂停 / 终止”和“正常 / 停用”两套状态，商品维护选择供应商时也只允许正常且合作中的供应商；但采购制单保存和提交时只重新读取商品，并使用商品上保存的 `supplierName` 字符串，不再回查供应商当前是否仍正常合作。供应商后续被暂停、终止或停用后，已绑定该供应商名称的启用商品仍可能继续被采购。
- 证据：供应商页面表单允许编辑 `cooperationStatus` 和 `status`，见 `erp-ui/src/views/inventory/supplier/index.vue:163-176`，列表也提供状态下拉变更，见 `:255-269`。商品页加载供应商选项时传 `status: "0"`、`cooperationStatus: "0"`，见 `erp-ui/src/views/inventory/product/index.vue:683-688`；后端 `InvProductServiceImpl.applySupplierFromCatalog()` 也通过 `selectInvSupplierByNameAndShop` / `selectActiveSupplierByNameInDeptChain` 校验供应商正常且合作中，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:289-310`。但采购商品选择器只按商品 `status: "0"` 查询，并只校验商品是否有 `supplierName`，见 `erp-ui/src/views/inventory/purchase/index.vue:400-410`、`:430-448`、`:512-513`；后端 `InvPurchaseServiceImpl.applyCatalogProductsForDraft()` 只校验商品状态和 `supplierName` 非空后写入采购单，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:126-186`，该服务没有重新查 `InvSupplierMapper` 或供应商状态。MySQL 当前 17 个供应商均为 `status=0/cooperation_status=0`，`inv_purchase_order=0`、`inv_purchase_return=0`，所以这是状态变更后的流程控制缺口，不是当前存量单据异常。
- 影响：供应商状态会退化成列表展示字段，不能真正阻止采购继续向暂停、终止或停用供应商下单。多账号场景中，管理员刚停用问题供应商，采购员或另一个浏览器会话仍可能用已绑定商品保存/提交采购单；供应商风控、淘汰和暂停合作流程无法闭环，后续对账时也难解释为什么停用后仍产生采购。
- 建议：采购商品选择器应只展示当前仍正常合作的供应商商品，后端保存草稿和提交采购单前必须再次按稳定 `supplier_id` 或过渡期按供应商名称+组织回查供应商状态并阻断非合作供应商。供应商状态变更时应展示受影响商品数和草稿采购单，提供同步停用商品、冻结采购或保留历史单据快照的选择，并补充“停用供应商后不能新建/提交采购单”的回归测试。

### P2-110 仓库主流程路由守卫漏掉商品、分类、供应商和采购路径，未选组织时先进入页面再报错

- 现象：电脑端仓库管理下的商品、分类、供应商、采购和采购退货页面都需要当前仓库/门店组织上下文，但路由守卫只把 `/cangku/stock`、`/cangku/transfer`、`/cangku/transfer-records` 纳入库存组织选择拦截。用户登录后若没有已验证组织，直接打开 `/cangku/product`、`/cangku/category`、`/cangku/supplier`、`/cangku/purchase` 或 `/cangku/purchaseReturn`，不会先跳到选择组织页。
- 证据：运行态菜单 `4001/4010/4020/4060/4070` 分别配置商品、分类、采购、供应商、采购退货页面，组件都在 `inventory/*/index` 下。`erp-ui/src/permission.js:57-63` 的 `inventoryContextPrefixes` 只有 `/inventory/`、`/cangku/stock`、`/cangku/transfer`、`/cangku/transfer-records`、`/mobile/`；`shouldSelectShop()` 只对这些前缀且 `!hasValidatedSelectedDeptContext()` 时跳 `/select-shop`。请求拦截器 `erp-ui/src/utils/request.js:36-41` 只有存在 `getSelectedInventoryDeptId()` 时才带 `Dept-NumId`；后端 `InvBaseService.requireSelectedShopDept()` 在缺 header 时抛“请先选择店铺或仓库”，商品/分类/供应商列表通过 `appendRelatedShopScope()` 调用该校验，采购和采购退货服务还要求 `requireWarehouseContext()`。商品页 `created()` 会立即 `loadCategories()` 和 `getList()`，采购页虽然在非仓库上下文下不查列表，但仍停留在业务页并显示“当前组织不能操作采购”提示，而不是统一进入组织选择流程。
- 影响：多账号首次登录、清空组织缓存、复制深链或刷新仓库主流程页面时，用户会先看到空表、错误提示或业务页禁用状态，而不是明确选择门店/仓库；这会放大 P2-16/P2-19/P2-25 记录的仓库路由稳定性问题，也让自动化巡检误判为接口无数据或页面坏掉。对普通门店/仓库账号来说，“必须先选组织”这一核心流程没有在所有仓库入口保持一致。
- 建议：把 `/cangku/` 作为整体纳入组织选择守卫，或显式列全 `/cangku/product`、`/cangku/category`、`/cangku/supplier`、`/cangku/purchase`、`/cangku/purchaseReturn` 等仓库主流程路径；守卫应在 `Dept-NumId` 未验证时统一跳 `/select-shop`，并按路径需要校验 `WAREHOUSE` 或允许的组织类型。页面内部的禁用提示保留为二级保护，不能替代路由层选择组织。

### P2-42 薪资配置裸路由空白，带任意 query 才能渲染页面

- 现象：管理员直连 `/system/salary` 时页面只剩主题色控件“清空 / 确定”，没有薪资方案、档位、员工绑定 tab，也没有表格或按钮；但访问 `/system/salary?probe=1` 或角色入口 `/system/salary?roleId=100` 能渲染薪资配置页。
- 证据：2026-06-23 浏览器复测 `/system/salary`，等待 5-8 秒后 `#app` 仅有 `.theme-picker` 子节点，正文文本为“清空 确定”，console 无错误；同会话访问 `/system/user`、`/system/role`、`/oa/salary` 均正常。数据库菜单 `180` 的页面路径为 `salary`、组件为 `system/salary/index`，运行菜单链接 href 为 `/system/salary`，不会自带 query。带 `?probe=1` 时页面显示 2 个方案和三类 tab。
- 复核：2026-06-23 重新启动前端 dev server 后直连 `/system/salary`，页面能显示“薪资方案 / 档位明细 / 员工绑定”三类 tab、3 个表格、2 个方案和新增/导出/档位/编辑/删除按钮；旧空白表现更像运行态/动态路由缓存问题。角色入口 `roleId` 上下文不生效仍按 P1-08 处理。
- 影响：系统管理菜单、地址栏直达、刷新恢复、书签和常规深链都可能进入空白壳；管理员会误以为薪资配置页坏了或没有权限。更隐蔽的是带 query 能正常显示，容易让问题只在菜单点击或刷新场景暴露。
- 建议：把 `/system/salary` 的菜单进入、直连、刷新、带 query、角色入口五种路径纳入同一回归；检查动态路由、keep-alive、query 初始化和组件加载条件，确保裸路由也能稳定渲染，加载失败时显示错误/重试而不是空白。

### P2-43 OA 工资计算依赖的门店工资参数和员工薪资绑定几乎未覆盖，流程无法闭环

- 现象：OA 工资管理能打开并提供“计算本月工资”，但当前工资计算所需基础数据只覆盖仓库参数和 1 个员工绑定；实际门店员工几乎都没有工资参数或薪资绑定。
- 证据：早前数据库 `oa_salary_config` 只有 1 条，配置在 `104 / 仓库 / WAREHOUSE`；STORE 门店 `103` 有 10 个有效用户、`107` 有 1 个、`108` 有 4 个，但均没有工资参数。25 个有效用户只有 `105 / ii` 有启用 `sys_user_salary_scheme`，其余 24 个有效用户没有员工薪资绑定。`OaSalaryServiceImpl.calculateSalary` 会先按目标组织取 `oa_salary_config`，没有则抛“未配置工资参数，请先配置”；随后取门店员工并要求每个员工有有效 `sys_user_salary_scheme`，否则抛“员工 [xxx] 未配置薪资方案”。
- 当前运行态复核：`sys_user_salary_scheme`、`oa_salary_config`、`oa_salary_record` 表存在，但员工绑定和工资记录覆盖不足；非运行态 `BossERP` 缺表问题已在 P1-18 改为环境漂移。2026-06-24 复核权限回填后，运营经理和总经理可执行工资列表、配置、计算和导出，但当前 6 个 STORE 门店仍无工资参数，`103/105/106/107/109` 门店授权用户的薪资绑定均为 0，`108` 也只有 1 个授权用户有绑定。本段继续保留“覆盖不足”结论。
- 影响：工资管理页面虽然有计算按钮，但绝大多数门店/员工无法真正完成月工资计算；管理员直到点击计算后才会遇到“未配置工资参数”或“员工未配置薪资方案”，不利于按门店批量补齐配置。
- 建议：工资管理页前置显示当前组织工资参数状态、可计算员工数、缺薪资绑定员工数；薪资配置页提供按门店/角色/岗位批量生成员工绑定的流程。计算前做 dry-run 预检，列出缺配置门店和缺绑定员工，不要直接进入覆盖计算确认。

### P2-157 薪资员工绑定保存接口不校验员工与核算门店关系，前端门店筛选可被绕过

- 现象：薪资配置“员工绑定”弹窗会按核算门店筛选员工，给管理员的感觉是只能把员工绑定到其所属或授权门店；但保存接口接收 `shopDeptId` 后只校验用户数据范围、薪资方案和档位归属，没有重新校验该门店是否为员工主部门/店铺授权范围，也没有校验当前操作者是否有该 `shopDeptId` 的薪资维护范围。
- 证据：前端 `erp-ui/src/views/system/salary/index.vue:346-354` 选择核算门店，`:976-982` 在门店变化后调用 `loadSalaryUsers({ shopDeptId })` 并把不在结果中的员工重置；后端下拉接口 `SysSalaryConfigController.users()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:91-100` 把 `salaryShopDeptId` 传给用户查询，`SysUserMapper.xml:128-137` 通过 `u.dept_id = shopDeptId OR sys_user_shop.dept_id = shopDeptId` 过滤员工。但保存接口 `SysSalaryConfigController.saveUserBindings()` 在 `:216-223` 只调用 `userService.checkUserDataScope(userId)` 后转入保存；`SysSalaryConfigServiceImpl.saveUserSalarySchemes()` 在 `:254-291` 会先 `deleteUserSalarySchemeByUserId(userId)`，随后只检查 `shopDeptId` 非空、`schemeId/itemId` 非空、档位属于方案，并批量写入 `sys_user_salary_scheme`。OA 工资计算的员工口径在 `OaSalaryEmployeeMapper.xml:51-62` 同样按 `u.dept_id` 或 `sys_user_shop` 判断门店员工，且在 `:23-27` 按 `uss.shop_dept_id` 取薪资绑定。当前运行态 `sys_user_shop` 显示 `105 / ii` 只授权 `108`，现有 `sys_user_salary_scheme` 也正好是 `user_id=105 / shop_dept_id=108`，未发现存量脏绑定；问题在于后端没有防止被构造请求写入不一致绑定。
- 影响：具备薪资绑定保存权限的账号可以绕过页面下拉，把员工绑定写到非所属、非授权或当前操作者不可维护的门店/仓库；由于保存前会整批删除该用户原绑定，构造请求还可能覆盖掉原有正确绑定。错误绑定短期内可能不参与工资计算，但会让薪资配置页与 OA 工资员工口径分裂；一旦员工后续被授权到该门店，历史错误绑定会直接进入工资计算，造成跨店铺工资口径污染。
- 建议：保存员工薪资绑定时在后端做三重校验：`shopDeptId` 必须是启用的 `STORE/WAREHOUSE`，必须在当前操作者可维护组织范围内，且目标员工必须满足 `sys_user.dept_id = shopDeptId` 或存在 `sys_user_shop(userId, shopDeptId)`。校验应在删除原绑定前完成，失败时不改动旧数据；页面也要在提交前展示员工、核算门店、方案、档位和将被覆盖的旧绑定数量。

### P2-161 员工薪资绑定复用“角色薪资配置”权限，授权语义和真实能力不一致

- 现象：薪资配置页“员工绑定”tab 的新增、编辑、移除按钮全部受 `system:salary:role` 控制；后端员工薪资绑定查询、批量查询和保存接口也使用同一个 `system:salary:role` 权限。但运行态菜单里这个权限节点名称是“角色薪资配置”，角色薪资绑定页面又没有真正上线。
- 证据：前端 `erp-ui/src/views/system/salary/index.vue:150-199` 对员工绑定新增、编辑、移除使用 `v-hasPermi="['system:salary:role']"`；`erp-ui/src/api/system/salaryConfig.js:132-155` 的用户薪资绑定接口调用 `/system/salaryConfig/user/*`；后端 `SysSalaryConfigController.userBindings/userBindingsBatch/saveUserBindings` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:196-218` 均标注 `@RequiresPermissions("system:salary:role")`，其中保存员工绑定的日志标题是“员工薪资配置”。运行态 `sys_menu` 中 `1186 / 角色薪资配置 / system:salary:role` 已授给 `admin/common/zjl`，当前 `sys_role_salary_scheme=4`、`sys_user_salary_scheme=1`，而角色管理跳转 `/system/salary?roleId=...` 仍只进入通用薪资页，无法维护角色绑定。
- 影响：权限管理员在角色授权树里看到的是“角色薪资配置”，实际放开的却包含员工个人薪资绑定维护能力，容易把可改员工工资方案、档位和核算门店的高敏权限错误授出。反过来，如果只想允许维护员工绑定，也无法从权限树中准确表达；角色薪资和员工薪资的审计日志、授权说明、回归用例都会混在一个权限点里。
- 建议：拆分 `system:salary:user` 和 `system:salary:role` 两类权限，员工绑定 tab 和 `/system/salaryConfig/user/*` 只使用员工薪资权限，角色薪资页和 `/system/salaryConfig/role/*` 只使用角色薪资权限；菜单名称、按钮文案、日志标题和角色默认授权同步调整。若当前版本暂不交付角色薪资维护，应停用“角色薪资配置”权限节点，避免授权树显示一个实际能力不匹配的高敏按钮。

### P2-104 OA 工资详情接口按店铺范围放行，店长查询权限可查看同店他人工资明细

- 现象：电脑工资列表页和移动端工资列表都用 `oa:salary:list` 区分“全部工资”，没有该权限时只走“我的工资”；但工资详情接口 `/oa/salary/{salaryId}` 只要求 `oa:salary:query`，服务层只校验当前选择组织是否覆盖记录的 `shop_dept_id`，不校验工资记录是否属于当前登录人，也不要求 `oa:salary:list`。
- 证据：2026-06-24 MySQL 复核当前运行态 `sys_menu`，`3211 / 工资查询 / oa:salary:query` 授给 `admin,dz,yyjl,zjl`，而 `3210 / oa:salary:list`、`3212 / oa:salary:calculate`、`3213 / oa:salary:export`、`3214 / oa:salary:config` 未授给 `dz`；12 个店长角色有效账号仅持有 `oa:salary:query`。前端 `erp-ui/src/views/oa/salary/index.vue` 在 `getList()` 中只有 `canViewAllSalary()` 为真才调用 `listAllSalary`，移动端 `featureService.js` 也按 `oa:salary:list` 选择全部或个人列表；但 `erp-ui/src/api/oa/salary.js` 仍暴露 `getSalary(salaryId)`，移动端详情会调用它。后端 `OaSalaryController.detail()` 只标注 `@RequiresPermissions("oa:salary:query")`；`OaSalaryServiceImpl.assertAndGetScopedSalary()` 对非管理员只做 `deptScopeMapper.countDeptInScope(rootDeptId, db.getShopDeptId())`，随后返回包含 `base_salary`、各类扣款、加班费和 `total_salary` 的完整记录。当前 `oa_salary_record=0`，无法用存量工资单复现，但 `calculateSalary()` 会按门店和月份生成这些明细字段。
- 影响：一旦运营经理或总经理生成工资记录，店长账号只要拿到同店工资 `salaryId`，就可以绕过“我的/全部”列表边界查看同店其他员工的基本工资、扣款、加班费和实发工资。工资属于高敏感个人数据，不能只依赖前端列表隐藏来隔离。
- 建议：详情接口应拆成“本人详情”和“管理详情”：本人详情只允许当前登录人的工资记录，管理详情才要求 `oa:salary:list` 或新的 `oa:salary:detailAll`；服务层对无管理权限的请求必须追加 `user_id = currentUserId`，并补两名同店员工的权限回归测试。若店长不应查看他人工资，应从 `dz` 移除当前全局 `oa:salary:query`，或把该权限语义改为“查询本人”。

### P2-107 薪资档位删除不检查员工/角色绑定，会留下悬空薪资配置

- 现象：薪资配置页的“档位明细”可以直接删除某个薪资档位，但后端删除档位前不检查该档位是否已被员工薪资绑定或角色薪资绑定引用。删除后 `sys_user_salary_scheme` / `sys_role_salary_scheme` 会保留旧 `item_id`，页面员工绑定会失去岗位、档位、工资合计等信息，OA 工资计算也会把该员工判断为未配置薪资方案。
- 证据：当前运行态 `sys_user_salary_scheme` 有 `relation_id=2 / user_id=105 / ii / shop_dept_id=108 / scheme_id=10001 / item_id=11001`，`sys_role_salary_scheme` 有 4 条角色到档位的绑定；两张表只有普通索引，没有外键约束。前端 `erp-ui/src/views/system/salary/index.vue` 的 `handleDeleteSchemeItem` 只弹“确认删除档位 [...] 吗？”后调用 `delSalaryItem(row.itemId)`。后端 `SysSalaryConfigServiceImpl.deleteSalarySchemeItemById()` 直接 `itemMapper.deleteSalarySchemeItemById(itemId)`，`SysSalarySchemeItemMapper.xml` 直接 `delete from sys_salary_scheme_item where item_id = #{itemId}`。OA 工资计算在 `OaSalaryEmployeeMapper.xml` 中通过 `sys_user_salary_scheme inner join sys_salary_scheme_item` 统计 `salary_scheme_count` 和基础工资；档位被删后，绑定行仍存在但 inner join 不命中，`OaSalaryServiceImpl.calculateSalary()` 会抛“员工 [xxx] 未配置薪资方案”。本轮未执行删除，只做只读复核。
- 影响：管理员在清理测试档位或重复档位时，可能把已绑定员工/角色的档位删掉，导致工资配置页面和真实计算口径分裂。多门店工资计算时，某些员工看起来仍有绑定记录，但计算会失败或工资基础数据为空，排查难度高。
- 建议：删除档位前必须检查员工绑定和角色绑定，存在引用时禁止删除并列出受影响员工、门店、角色；若要废弃档位，应提供“停用/迁移档位”流程，把绑定批量迁移到新档位并记录审计。数据库层也应补充外键或至少补充一致性巡检，发现悬空 `item_id` 时阻断工资计算前置校验。

### P2-122 薪资方案删除会级联清掉员工和角色薪资绑定，确认前没有影响预览

- 现象：薪资配置页可以直接删除薪资方案，后端删除方案时会同时删除该方案下的档位、角色薪资绑定和员工薪资绑定。页面确认框只显示方案名称，没有展示会影响哪些员工、门店和角色，也没有提供停用或迁移方案。
- 证据：当前运行态两个薪资方案均有引用：`10001 / 金英门店薪资方案-无社保` 有 11 个档位、1 条员工绑定和 3 条角色绑定；`10002 / 金英门店薪资方案-有社保` 有 6 个档位和 1 条角色绑定。员工绑定为 `105 / ii / 108 市场部门 / scheme_id=10001 / item_id=11001`，角色绑定包括超级管理员、茶艺师、实习生等角色。前端 `erp-ui/src/views/system/salary/index.vue:794-804` 只弹“确认删除薪资方案 [方案名] 吗？”后调用 `delSalaryScheme(row.schemeId)`。后端 `SysSalaryConfigServiceImpl.deleteSalarySchemeByIds()` 在 `:132-140` 先按 `schemeId` 删除 `sys_salary_scheme_item`，再调用 `deleteRoleSalarySchemeBySchemeIds` 和 `deleteUserSalarySchemeBySchemeIds` 删除角色/员工绑定，最后删除方案。本轮未执行删除，只做只读复核。
- 影响：管理员清理测试方案或误点删除时，会把薪资方案、档位和现有角色/员工绑定一次性清掉，工资计算会从“已有绑定但档位异常”变成“员工未配置薪资方案”。多店铺工资核算本来绑定覆盖就很低，删除方案会进一步破坏工资参数、员工绑定、角色默认档位和合同薪资快照之间的解释关系。
- 建议：薪资方案被员工、角色、合同或工资记录引用时默认禁止删除，只允许停用；确需删除应先进入迁移向导，展示受影响员工、门店、角色、档位和历史工资/合同引用，要求选择替代方案或确认归档。删除确认框应显示影响数量，后端也必须做引用校验，不能只依赖前端提示。

### P2-123 劳动合同内置模板可直接编辑和停用，已有合同引用也没有影响预览

- 现象：劳动合同“模板与企业章”页会标出内置模板，但编辑按钮仍允许修改模板名称、社保口径、版本、模板文件 URL 和状态；后端保存模板时也没有保护 `built_in=Y` 的系统内置模板，已有合同引用时不做影响预览。
- 证据：运行态 `oa_labor_contract_template` 当前有 2 个内置模板，均为 `built_in=Y/status=0`；其中 `template_id=1 / 杭州劳动合同有社保版名田` 已被 9 份合同引用，状态分布为 1 份 `signed`、8 份 `voided`，`template_id=2 / 杭州劳动合同无社保版名田` 暂无引用。角色权限中超级管理员和运营经理都持有 `oa:laborContract:template:add`，运营经理当前有 3 个用户。前端 `erp-ui/src/views/oa/laborContract/index.vue:71-123` 展示模板表格并对每行提供编辑按钮，`:302-328` 的模板弹窗允许编辑关键字段和状态，`:795-804` 的 `handleEditTemplate` 直接复制行数据后调用保存。API `erp-ui/src/api/oa/laborContract.js:68-82` 通过 `POST /oa/laborContract/template` 保存模板；后端 `OaLaborContractController.saveTemplate()` 只要求 `oa:laborContract:template:add`，`OaLaborContractServiceImpl.saveTemplate()` 对新增/更新都直接落库，`OaLaborContractTemplateMapper.xml` 的更新语句也允许改 `template_name/social_type/template_version/template_file_url/status/built_in`。本轮未修改模板，只做只读复核。
- 影响：运营经理或管理员可以直接改动系统内置合同模板，导致后续生成的合同文件与历史合同仍引用同一个 `template_id`，但模板语义已经变化；如果停用已被草稿或后续流程引用的模板，`requireActiveTemplate` 会在发起签约时返回“合同模板已停用”，用户只能在流程中途发现问题。劳动合同属于法律证据链，模板版本、文件和引用关系不稳定会影响合同解释、追责和审计。
- 建议：内置模板默认只读，修改应走“复制新版模板/版本升级”流程，旧模板继续服务历史合同；停用或替换模板前先展示引用合同数、状态分布和最近使用时间，有草稿/待签/已签引用时默认阻断或要求迁移/归档。后端应禁止普通模板保存接口修改 `built_in=Y` 的关键字段，至少拆出专用权限、变更原因和审计记录。

### P2-124 劳动合同发送后未冻结企业章配置，签署归档可能使用被替换的新章

- 现象：劳动合同发送时会记录当时企业章图片 hash，但签署归档时又重新读取“当前启用企业章”生成归档 DOCX/PDF。若员工打开并确认的是旧章预览，签署前运营人员替换或新增启用企业章，最终归档文件可能使用新章，而合同记录仍保留发送时的旧章 hash。
- 证据：前端企业章入口和保存按钮都使用 `oa:laborContract:template:add`，见 `erp-ui/src/views/oa/laborContract/index.vue:91-92`、`:332-349`、`:818-824`；当前超级管理员和运营经理持有该权限，运营经理有 3 个用户。后端 `OaLaborContractController.saveSeal()` 在 `:53-59` 允许保存企业章，`OaLaborContractServiceImpl.saveSealConfig()` 在 `:103-119` 可新增或更新企业章；`OaCompanySealConfigMapper.xml:27-37` 允许改名称、图片 URL 和状态，`:40-45` 只按 `status='0' order by seal_id desc limit 1` 取最新启用章。发送合同在 `OaLaborContractServiceImpl:190-207` 计算并保存 `seal_image_hash`；但签署在 `:300-307` 重新 `requireActiveSeal()` 并生成归档，`:309-323` 只更新归档文件、签名和归档 hash，不会校验或更新 `seal_image_hash`。`assertFrozenDocumentMatches()` 在 `:440-455` 只校验预览版本和预览 PDF hash，不校验归档生成时的企业章是否仍等于发送时冻结 hash。运行态 9 份合同中 6 份已有同一个非空 `seal_image_hash`、3 份为空，合同表没有 `seal_id`、章名或章文件 URL 快照字段。本轮未替换企业章，只做只读复核。
- 影响：劳动合同证据链会出现“预览确认的章、数据库记录的章 hash、最终归档文件里的章”三者可能不一致。员工和管理员事后通过 hash 验证能发现文件 hash，但难以解释归档文件为何使用了另一个企业章；如果企业章停用或替换发生在待签期间，签署流程还可能在员工侧失败或生成与发送版本不一致的归档。
- 建议：发送合同时冻结企业章快照，至少保存 `seal_id`、章名、章图片 URL、章图片 hash 和章版本；签署归档必须使用发送时冻结的章文件，而不是当前启用章。企业章替换或停用前展示待签/已签/作废合同影响数量，有待签合同时禁止直接替换，或要求重新发送合同并重置员工确认版本。

### P2-44 单据状态审计有后端、权限和数据，但电脑端没有入口

- 现象：数据库已有单据状态审计日志，后端也提供审计列表接口，但电脑网页端没有对应页面、路由、API 调用或菜单入口。
- 证据：2026-06-24 `sys_menu` 中 `4491 / 状态审计查询 / inv:audit:list` 为按钮权限节点，`path` 为空、`component` 为空；管理员、店长、驻店经理、运营经理、总经理角色都持有该权限。后端 `InvDocumentStatusLogController` 提供 `GET /audit/list` 并要求 `@RequiresPermissions("inv:audit:list")`，`InvDocumentStatusLogMapper.xml` 从 `inv_document_status_log` 查询状态变化记录；当前运行态该表内有盘点单 `SC202606140001` 的 2 条状态日志，且存在 6 个状态触发器。但 `erp-ui/src` 中没有 `audit/list`、`/audit` 或 `inv:audit:list` 引用；2026-06-23 权限缺失结论已按 P2-75 修正为权限树漂移，运行态状态审计表和触发器复核见 P2-91。
- 影响：盘点单、调拨单、采购单等状态流转即使写入了审计日志，业务人员和管理员也无法在电脑端查询；权限树里出现一个实际不可见的功能，会让角色授权和审计追踪都失真。
- 建议：在报表中心或库存单据详情中补齐“状态审计”页面/时间线，并复用后端 `shopDeptId` 范围过滤；如果近期不上线，应停用该按钮权限并补充说明，避免把不可见能力继续分配给角色。

### P2-45 劳动合同路由曾空白且非运行态合同数据清空，电脑端合同维护需继续回归

- 现象：电脑端 `/oa/labor-contract` 和 `/oa/labor-contract?probe=1` 曾无法渲染劳动合同页面，只有主题色控件“清空 / 确定”，没有签约单 tab、合同列表、模板与企业章 tab、详情或按钮；后续前端重启后页面恢复。运行态库仍有 9 条合同、59 条事件和 1 条企业章配置，非运行态 `BossERP` 则已清空正式合同、事件和企业章，只剩 2 个模板。
- 证据：2026-06-23 同一浏览器登录态下，`/oa/salary` 能显示工资管理表格和按钮，`/oa/index` 能显示 OA 首页文案；但直连 `/oa/labor-contract` 与带 query 路由等待后 `#app` 正文文本长度为 0，按钮只有“清空 / 确定”，console 无错误。运行态数据库 `oa_labor_contract=9`、`oa_labor_contract_event=59`、`oa_labor_contract_template=2`、`oa_company_seal_config=1`，数据源并不为空；非运行态 `BossERP` 后续精确复核为 `oa_labor_contract=0`、`oa_labor_contract_event=0`、`oa_company_seal_config=0`、`oa_labor_contract_template=2`。
- 复核：2026-06-23 重新启动前端 dev server 后直连 `/oa/labor-contract`，页面能显示签约单/模板与企业章 tab、2 个表格、合同列表、验 hash 和发起签约按钮；页面同时弹出“加载企业章失败，请稍后重试或联系管理员”，但后端 `/oa/laborContract/seal` 和企业章图片 URL 都返回 200。旧空白表现已恢复，企业章错误提示需要单独继续定位请求时序或前端错误处理。
- 当前复核：运行态 `BossERP_stock_state_75c59ee` 的合同数据没有清空；“合同和企业章清空”只适用于非运行态 `BossERP`，应作为环境漂移风险记录。路由空白/恢复问题仍会影响模板维护、合同发起和证据链查看的稳定性，企业章加载提示也需要单独定位。
- 影响：管理员和运营经理即使持有 `oa:laborContract:*` 权限，也可能在直连、刷新或组织切换后遇到空白壳，无法稳定维护签约单、查看证据链、打开模板企业章或发起签约；这会影响劳动合同模块的日常使用和数据核对。
- 建议：把 `/oa/labor-contract` 纳入 OA 路由稳定性专项，覆盖菜单点击、直连、刷新、带 query、组织切换后恢复；页面加载失败时显示可诊断错误，不要退化成空白壳。

### P2-46 劳动合同可把门店员工合同挂到仓库组织，合同范围和员工归属不一致

- 现象：劳动合同使用当前选择组织作为合同 `shop_dept_id`，但员工选择和后端保存没有校验员工是否属于该组织，导致门店员工的劳动合同被挂到仓库。
- 证据：9 条合同中 7 条 `shop_dept_id=104 / 仓库`，但合同员工大多是 `employee_id=104 / l`、`employee_dept_id=108 / 市场部门`；还有 `employee_id=1 / 管理员`、`employee_dept_id=103 / 研发部门` 的合同也挂到仓库。前端 `handleEmployeeChange` 只回填员工部门字段，不设置或校验合同组织；后端 `saveContract` 通过 `shopScopeService.resolveRequiredShopDept(selectedShopDeptId...)` 直接把当前组织写成合同组织，没有校验员工部门和合同组织关系。
- 影响：多店铺隔离会被破坏：仓库上下文能产生和看到门店员工劳动合同，门店上下文又可能看不到本店员工历史合同；工资、合同、员工授权和审计报表的组织口径不一致。
- 建议：劳动合同发起时必须按员工归属门店或明确的合同主体组织落库，前后端都校验员工是否属于当前可操作组织；员工选择器应只显示当前组织可签约员工，并在列表中展示员工部门和合同归属组织的差异提示。

### P2-47 劳动合同验 hash 按全库匹配且会写事件，缺少店铺范围过滤

- 现象：“验 hash”按钮权限是 `oa:laborContract:query`，看起来像只读查询；但后端会按全库证据哈希查合同并追加 `verify` 事件，而且没有按当前选择门店/仓库过滤。
- 证据：前端 `submitVerifyHash` 调用 `POST /oa/laborContract/verify`；后端 `OaLaborContractController.verify` 只要求 `oa:laborContract:query`，没有传入 `resolveShopDeptId(request)`；`OaLaborContractServiceImpl.verifyContractHash` 调用 `selectOaLaborContractByEvidenceHash`，mapper 只按 `preview_file_hash / archive_file_hash / certificate_file_hash / contract_file_hash` 全库匹配，命中后返回合同 ID、合同号、员工姓名、文件类型，并调用 `recordEvent(..., \"verify\", ...)` 写事件。
- 影响：拥有查询权限的角色可以用一个 hash 探测并识别不属于当前组织范围的合同；同时用户以为只是验证文件，却会改变合同事件链，给“只读核验”和“审计留痕”的边界造成混淆。
- 建议：验 hash 接口增加组织范围或员工归属过滤，返回结果前校验当前用户有权查看该合同；UI 明确提示“验证将写入审计事件”。如果希望 hash 验证作为纯只读工具，应把审计事件写入独立查询日志，而不是合同证据链。

### P2-158 劳动合同列表接口批量返回身份证、文件 URL 和签署环境等详情字段

- 现象：电脑端劳动合同列表只展示合同 ID、员工、手机号、岗位、店铺、社保、期限、工资、状态和时间等摘要列，但后端 `/oa/laborContract/list` 使用同一个详情实体和 `baseColumns` 查询，分页列表响应会把员工身份证号、合同文件 URL、证据 hash、签署 IP、User-Agent、第三方签约 URL/回调载荷等详情字段一起返回到浏览器。
- 证据：前端表格在 `erp-ui/src/views/oa/laborContract/index.vue:32-58` 只展示摘要列，身份证只在详情弹窗 `:245-257` 展示。后端 `OaLaborContractController.list()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java:88-95` 直接 `getDataTable(list)` 返回 `OaLaborContract` 实体列表；`OaLaborContractMapper.xml:70-87` 的 `baseColumns` 包含 `employee_id_card`、`base_salary/total_salary`、`preview_file_url/archive_file_url/pdf_file_url/signature_file_url`、各类文件 hash、`signer_ip/signer_user_agent`、`provider_sign_url/provider_callback_payload` 等字段，列表查询 `:193-219` 直接 `select <include refid="baseColumns"/>`。运行态权限中 `oa:laborContract:list/query/send/add/void/template:*` 均授给 `admin` 和 `yyjl`，运营经理当前有 `12345/liu123456/ii` 3 个账号；运行态 9 条合同中已有身份证号、手机号、工资合计、合同文件标记、签署 IP 和 Safari User-Agent 存量数据。
- 影响：列表权限等同于批量详情权限。运营经理打开合同列表或浏览器网络面板，就能一次性拿到同组织合同的身份证、工资、合同文件地址和签署环境信息；这些信息本应按详情权限、文件下载权限或证据链权限分层返回。多店铺场景下，列表接口越宽，越容易把合同高敏感字段通过缓存、日志、前端错误上报或二次导出扩散。
- 建议：拆分劳动合同列表 DTO 和详情 DTO。列表接口只返回表格展示所需的摘要字段，身份证至少脱敏，文件 URL、签署 IP/User-Agent、第三方签约 URL、回调载荷、证据 hash 等只在详情/文件/证据链接口按独立权限返回；导出也应按字段敏感级别做授权和脱敏，不能复用全量实体。

### P2-167 劳动合同可保存并签署无效身份证和手机号

- 现象：劳动合同“发起签约”把身份证号和手机号作为合同证据字段保存，但前端和后端都只做必填/长度校验，没有身份证、手机号格式校验；合同从草稿发送、员工签署到 `signed` 状态时也不会重新校验身份字段。运行态 9 条劳动合同里已有 8 条身份证号格式不合法、6 条手机号格式不合法，其中 `contract_id=9 / status=signed` 的身份证号和手机号都为 `1`。
- 证据：前端发起签约表单 `erp-ui/src/views/oa/laborContract/index.vue:147-153` 允许身份证号和手机号输入到 32 位，校验规则 `:423-432` 对 `employeeIdCard/employeePhone` 只有 `required`，`submitContract()` 在 `:729-737` 校验通过后直接调用 `saveLaborContract` 并提示“已保存”。后端 `OaLaborContract.java:51-58` 同样只有 `@NotBlank` 和 `@Size(max=32)`；`OaLaborContractServiceImpl.saveContract()` 在 `:130-153` 计算薪资后直接 `insertOaLaborContract`，Mapper `OaLaborContractMapper.xml:97-114` 原样写入 `employee_id_card/employee_phone`。发送签署 `OaLaborContractServiceImpl.java:182-214` 只校验草稿状态、模板和印章并生成合同文件；员工签署 `:280-326` 只校验本人、待签署状态、确认文案、签名和文件 hash，未重验身份证/手机号。运行态 MySQL 显示 `employee_id_card`、`employee_phone` 均为 `varchar(32)`，`oa_labor_contract` 当前 `total=9/bad_id=8/bad_phone=6`，且 `signed` 分组 `bad_id=1/bad_phone=1`。
- 影响：劳动合同文件、签署归档、证据 hash 和事件链会把错误身份信息固化为正式合同证据；员工和运营经理看到的是保存/发送/签署成功，而不是“身份信息不可用于签约”。多账号多店铺下，一旦把错误身份字段写入合同归属组织，后续作废、下载、核验 hash 和审计追溯都只能看到已经污染的合同快照，无法区分测试数据、误填数据和真实合同。
- 建议：身份证号、手机号校验要在前端、后端和导入/接口层共用同一规则，发送签署前必须再次校验合同证据字段；员工候选应优先从员工档案读取已校验身份信息，缺失或无效时禁止发送并给出治理入口。存量合同列表增加“身份信息异常”标记，已签署异常合同要走作废/重签或人工确认流程，并在审计事件中记录治理动作。

### P2-163 运营经理有劳动合同新增权限但缺员工和薪资选项依赖，发起签约会卡在空下拉

- 现象：劳动合同页“发起签约”按钮只按 `oa:laborContract:add` 显示，但表单初始化还隐性依赖 `oa:laborContract:template:list`、`system:user:list` 和 `system:salary:query`。当前运营经理角色能看到并点击“发起签约”，却没有用户列表和薪资查询权限，员工账号下拉不会加载，薪资方案和档位也不会加载，流程会停在必填员工无法选择的位置。
- 证据：前端 `erp-ui/src/views/oa/laborContract/index.vue:27-28` 用 `oa:laborContract:add` 显示“发起签约”，`:445-456` 计算 `canLoadLaborContractEmployees` 必须同时有 `oa:laborContract:add` 和 `system:user:list`，`canLoadSalarySchemes` 必须有 `system:salary:query`，`:477-482` 只有满足这些条件才加载员工和薪资方案；`:607-621` 的员工选项实际调用 `listUser({ pageNum: 1, pageSize: 200, status: "0" })`，对应 `/system/user/list`，后端 `SysUserController.list()` 要求 `system:user:list`；`:622-633` 调用 `/system/salaryConfig/options`，后端 `SysSalaryConfigController.options()` 要求 `system:salary:query`，档位接口也要求同一权限。后端劳动合同保存 `OaLaborContractController.save()` 只要求 `oa:laborContract:add`。运行态权限矩阵显示 `yyjl / 运营经理` 有 `oa:laborContract:add/list/query/send/template:add/template:list/void`，但 `system:user:list=0`、`system:salary:query=0`；当前运营经理账号为 `100 / 12345`、`101 / liu123456`、`105 / ii`，且分别已有组织授权。本轮未创建、保存或发送合同，只做只读复核。
- 影响：权限树看起来已经授予运营经理“劳动合同新增”，实际页面无法完成新增合同。管理员为了让运营经理能选员工，容易被迫授予完整 `system:user:list` 和薪资查询权限，从而扩大用户管理和薪资配置可见范围；如果不补权限，运营经理在多店铺签约场景只能看到入口却无法推进，错误也不是“缺少员工候选权限”这种可操作提示。
- 建议：给劳动合同模块提供专用候选接口和权限，例如 `oa:laborContract:employee:list`、`oa:laborContract:salary:query`，只返回发起签约需要的员工账号、姓名、手机号、部门、社保口径和薪资快照，不复用完整用户管理列表权限；角色保存时应校验 `oa:laborContract:add` 的依赖权限，前端在缺依赖时禁用“发起签约”并明确提示缺少哪项配置，后端也要按合同组织校验员工和薪资档位范围。

### P2-48 固定资产脚本只给超级管理员授权，门店维修上报流程缺角色闭环

- 现象：固定资产模块的业务文案和移动端配置都指向“门店固定资产报修”，但 SQL 脚本只把固定资产菜单和按钮权限授权给超级管理员。
- 证据：`sql/erp_oa_fixed_asset_20260619.sql` 创建 `3310 / 固定资产管理`、`3311 / 固定资产配置`、`3320 / 固定资产维修上报` 及按钮权限后，`INSERT IGNORE INTO sys_role_menu` 只插入 `role_id=1` 的菜单授权。前端维修页按钮包含 `oa:fixedAsset:repair:add`、`oa:fixedAsset:repair:confirm`、`oa:fixedAsset:repair:export`；移动端 `/mobile/fixed-asset-repair` 标记 `allowedDeptTypes: ['STORE']`。
- 影响：即使执行 SQL 上线模块，普通门店角色也看不到维修上报入口，门店不能自行报修；只有超级管理员能配置和上报，和“门店按当前可用额度提交维修上报”的业务说明不一致。
- 建议：先定义固定资产角色矩阵：配置/异常批准给运营或管理员，维修新增给店长/驻店经理，待确认给门店负责人或被授权门店角色；SQL、菜单、移动端入口和测试账号一并补齐。

### P2-49 固定资产额度 GET 查询会自动写入额度记录，读写边界不清

- 现象：固定资产配置页和维修页加载列表后都会调用额度查询接口，但后端额度查询在没有额度记录时会自动创建或更新 `oa_fixed_asset_quota`。
- 证据：前端 `fixedAsset/config/index.vue` 和 `fixedAsset/repair/index.vue` 的 `getList()` 后调用 `loadQuota()`，API 为 `GET /oa/fixedAsset/config/quota`；后端 `OaFixedAssetConfigController.quota` 调用 `getQuotaSummary`，服务层在 `quotaMapper.selectQuota(...)` 返回空时直接调用 `rebuildQuota`，`rebuildQuota` 会 `insertQuota` 或 `updateQuota`。
- 影响：用户只是打开页面或刷新列表，就可能在数据库里生成/更新年度额度记录；这会让只读查看、数据初始化、审计日志和接口幂等语义混在一起。当前环境缺表时，这个 GET 入口也会直接暴露 SQL 缺表错误。
- 建议：把额度重建拆成显式初始化/重算动作，GET 只读返回当前状态；页面可提示“未初始化额度”，由有权限角色点击“初始化/重算额度”并记录操作人和原因。

### P2-50 固定资产异常批准在门店确认前已占用额度

- 现象：异常批准弹窗说明“门店核对后才能正式上报”，但后端在生成待确认维修单时已经写入额度流水，透支未来额度会立刻计入已用额度。
- 证据：前端配置页异常批准弹窗文案为“异常批准会生成一条待确认上报记录，门店核对后才能正式上报”；后端 `approveExceptionRepair` 创建 `status=pending_confirm` 维修单后立即调用 `insertLedger(repair, year, repair.getExceptionType(), estimatedAmount)`；`sumUsedQuotaAmount` 会把 `normal_submit` 和 `advance_future_months` 计入已用额度。`confirmApprovedRepair` 只把状态改为 `submitted`，不再写额度流水。
- 影响：门店尚未确认、甚至可能长期不处理的异常维修单，会提前占用可用额度；如果后续取消/驳回流程没有同步反冲，额度会和真实上报状态不一致。
- 建议：待确认阶段写入 pending 类型流水或不写额度流水，只有门店确认后才占用额度；若业务要求批准即占用，应把页面文案改成“批准即占用额度，门店仅确认明细”，并补充取消/释放额度流程。

### P2-51 固定资产维修上报后端不校验商品是否已配置为固定资产

- 现象：维修上报页面前端会从固定资产配置表加载可报修资产，但后端 `submitRepair` 只校验额度，不校验 `productId` 是否是当前店铺启用的固定资产配置。
- 证据：前端 `loadAssets()` 调用 `listFixedAssetConfigs({ status: '0' })` 作为资产下拉；后端 `submitRepair` 只执行 `resolveWritableShop`、`getQuotaSummary` 和金额校验，然后直接 `repairMapper.insertRepair(repair)` 和 `insertLedger`，没有查询 `oa_fixed_asset_config` 校验该 `productId/shopDeptId/status` 是否存在。
- 影响：绕过前端或接口调用时，可以对任意商品提交固定资产维修上报并占用额度；配置表的“固定资产”准入规则无法成为后端真实约束。
- 建议：`submitRepair` 和 `approveExceptionRepair` 都应校验商品属于当前店铺启用固定资产配置，并回填配置快照；缺配置时返回“请先维护固定资产配置”。

### P3-09 注册关闭时仍可直达注册表单

- 现象：系统配置 `sys.account.registerUser=false`，登录页也不显示“立即注册”；但直接访问 `/register` 仍看到账号、密码、确认密码、验证码和“注册”按钮，没有提示“当前系统未开放注册”。
- 证据：2026-06-23 浏览器实测 `/login?preview=1` 不包含“立即注册”，直连 `/register` 显示完整注册表单。前端 `erp-ui/src/router/index.js:50-54` 把 `/register` 放在公共路由，`erp-ui/src/views/register.vue:1-60` 无注册开关判断；后端 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:157-161` 会在 `sys.account.registerUser` 不为 `true` 时返回“当前系统没有开启注册功能！”。
- 影响：用户从旧链接或手工输入进入注册页，会先填写表单和验证码，提交后才知道注册不可用；多账号创建流程已经由管理员用户管理承接，直达注册页容易造成入口混乱。
- 建议：注册关闭时前端 `/register` 直接显示“注册未开放，请联系管理员创建账号”，并隐藏注册表单和验证码；或在路由守卫中按配置跳回登录页。

### P3-10 桌面首页仍提示处理采购待办但采购申请已停用

- 现象：桌面首页 `/index` 的“审批流转”和“今日建议”仍写“集中处理采购等审批事项”“集中完成待审批采购单”，但当前 OA 采购申请菜单已隐藏且停用，`oa_purchase=0`。
- 证据：数据库 `sys_menu` 中“采购申请” `visible=1`、`status=1`，`oa_purchase=0`；2026-06-23 浏览器实测首页仍显示采购审批文案。前端 `erp-ui/src/views/index.vue:148-155` 固定“审批流转 / 我的待办 / 集中处理采购等审批事项”，`erp-ui/src/views/index.vue:197-201` 固定“处理待办审批 / 集中完成待审批采购单”。
- 影响：首页是登录后第一屏，用户会被引导去处理一个当前交付范围内已停用且无数据的采购申请流程；这和 OA 首页的采购申请提示问题同源，会放大“功能到底是否上线”的疑惑。
- 建议：首页建议项和审批卡片按当前菜单权限、模块开关和待办数据动态生成；采购申请停用时应改成通用“查看我的待办”或隐藏采购单相关文案。

### P3-11 个人中心把手机和邮箱设为必填但多数账号为空

- 现象：个人中心“基本资料”表单要求手机号码和邮箱必填；但当前 7 个有效未删除账号中 5 个邮箱为空、5 个手机号为空。已有空联系方式账号如果只想修改昵称或性别，会被前端强制要求补手机和邮箱。
- 证据：数据库 `sys_user` 统计 `effective_users=7`、`empty_email=5`、`empty_phone=5`；前端 `erp-ui/src/views/system/user/profile/userInfo.vue:42-57` 将 `email`、`phonenumber` 都设为 required；后端 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:84-97` 允许空值，只在非空时校验唯一性。
- 影响：用户管理创建账号时并不强制手机号/邮箱，个人中心却强制补齐，规则前后不一致；多账号批量创建后，用户首次维护个人信息会被迫填写可能不存在或不想提供的联系方式。
- 建议：与用户管理的必填策略保持一致。若手机号/邮箱是业务必填，应在新增用户时也强制；若不是必填，个人中心只校验格式和唯一性，不应强制填写。

### P3-103 个人中心保存昵称和性别后，页面本地信息不会同步刷新

- 现象：个人中心“基本资料”允许修改用户昵称、手机号、邮箱和性别。保存成功后，前端只把手机号和邮箱写回当前页面的 `user` 对象，没有同步昵称和性别，也没有重新调用个人信息接口或刷新顶部用户昵称；用户看到“修改成功”后，左侧个人信息卡、顶部头像菜单等位置仍可能显示旧昵称，必须刷新或重新登录才会看到完整变化。
- 证据：`erp-ui/src/views/system/user/profile/userInfo.vue` 的表单提交成功后只执行 `this.user.phonenumber = this.form.phonenumber` 和 `this.user.email = this.form.email`，未写回 `nickName/sex`，也未通知 Vuex 用户信息刷新。后端 `SysProfileController.updateProfile()` 会把 `nickName/email/phonenumber/sex` 全部写入当前用户对象并调用 `tokenService.setLoginUser(loginUser)`；`SysUserServiceImpl.updateUserProfile()` 最终走 `SysUserMapper.updateUser`，其中 `nick_name` 和 `sex` 都会在非空时更新。说明后端保存口径包含昵称/性别，前端成功态展示口径不完整。本轮未提交写库，只做源码复核。
- 影响：个人中心作为普通用户自助维护入口，保存成功后显示旧昵称会让用户误以为没有保存，或重复提交；多账号验收时，管理员/门店员工切换账号后也更难判断当前身份展示是否来自最新资料。
- 建议：保存成功后统一刷新 `/system/user/profile` 和 `/getInfo`，或至少同步回写 `nickName/sex/phonenumber/email` 并更新 Vuex 中的昵称、头像等展示字段；保存按钮应在刷新完成后再提示成功，避免“成功但页面仍旧”的反馈。

### P3-12 分类删除没有按商品引用提前禁用或显示引用数

- 现象：分类页会禁用存在子分类的删除按钮，但不会提前禁用已被商品引用的分类；当前 30 个分类中 23 个已有商品引用，页面仍展示可点击删除入口，需要用户点到确认/后端拦截后才知道不可删。
- 证据：数据库统计 `inv_product_category` 中 23 个分类 `product_count>0`；前端 `erp-ui/src/views/inventory/category/index.vue:79-88` 仅按 `hasChildren(scope.row)` 禁用删除；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java:110-121` 才按商品引用抛出“分类已被商品引用，不能删除”。
- 影响：分类主数据维护时，管理员无法在列表上判断哪些分类可删、哪些只能停用；误点删除会进入确认和错误提示链路，不够人性化。
- 建议：列表补充“商品数/引用数”或“可删除”状态；对 `product_count>0` 的分类直接禁用删除并提示“已被商品引用，请停用或先调整商品分类”。

### P3-236 商品分类备注新增可写但编辑不会更新，页面给了可维护字段却保存无效

- 现象：商品分类新增/编辑抽屉提供“备注”文本域，编辑分类时也会回显并提交 `remark`；但后端更新 SQL 没有更新 `remark` 字段。结果是新建分类时备注可以写入，后续在编辑页修改备注会提示“保存成功”却不会落库，刷新后仍是旧备注或空备注。
- 证据：前端 `erp-ui/src/views/inventory/category/index.vue:141-143` 提供备注输入，`:315-326` 编辑时把 `data.remark` 写回表单，`:347-353` 保存时只剔除 `categoryCode`，会随表单提交 `remark`。服务层更新分支 `InvProductCategoryServiceImpl.saveCategory()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java:90-102` 最终调用 `categoryMapper.updateInvProductCategory(category)`。Mapper 新增 SQL 在 `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductCategoryMapper.xml:67-74` 插入 `remark`，但更新 SQL `:77-88` 只更新名称、编码、父级、祖先、排序、状态和更新时间，没有 `remark = #{remark}`。MySQL 只读统计当前有效分类 `30` 条、非空备注 `0` 条，当前没有可用样例能在页面上发现该问题，只能从保存链路判断。
- 影响：分类备注通常用于解释分类口径、导入治理说明或商品归类规则。管理员在页面上修改后收到成功提示但数据不变，会误以为说明已维护完成；多店铺/仓库共用集团分类时，分类治理备注无法被后续人员可靠修正。
- 建议：若分类备注要交付维护能力，`updateInvProductCategory` 应同步更新 `remark`，并补一条“新增带备注、编辑修改备注、详情/列表重新读取”的回归测试；若备注暂不交付，应从抽屉移除该字段或明确只读，避免给出保存成功但不生效的交互。

### P3-13 供应商删除缺少引用状态前置展示

- 现象：供应商源码中的列表操作始终展示“删除”入口，删除确认文案只问是否删除供应商，没有提前说明该供应商是否已被商品或采购历史引用；当前 17 个供应商中 16 个已被商品引用。
- 证据：数据库统计 `inv_supplier` 中 16 个主仓供应商存在商品引用；前端 `erp-ui/src/views/inventory/supplier/index.vue:51-68` 列表操作包含详情、编辑、状态、删除，但列表没有引用数或禁删状态；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSupplierServiceImpl.java:75-92` 会在删除时按商品、采购订单、采购退货引用抛错。
- 影响：供应商管理即使入口修复，管理员仍要点进详情或尝试删除后才知道是否可删；大量已引用供应商更适合“停用”而不是删除，当前列表没有把这一点前置。
- 建议：供应商列表增加供货商品数/历史单据引用提示；对有引用的供应商隐藏或禁用删除，主推“状态改为暂停/终止”或“停用”。

### P3-237 供应商备注详情可见但编辑不会更新，已有 16 条备注无法在页面维护

- 现象：供应商详情会展示“备注”，新增/编辑弹窗也提供备注文本域；但供应商更新 SQL 没有更新 `remark` 字段。当前运行态 17 个供应商中 16 个已有非空备注，管理员在页面编辑供应商备注后会收到“操作成功”，但刷新详情仍是旧备注。
- 证据：前端 `erp-ui/src/views/inventory/supplier/index.vue:83-85` 在详情弹窗展示 `detailForm.remark`，`:183-184` 提供备注输入，`:219-247` 编辑时读取供应商详情并把整个 `form` 传给 `updateSupplier(this.form)`。服务层 `InvSupplierServiceImpl.saveSupplier()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSupplierServiceImpl.java:64-69` 更新分支直接调用 `supplierMapper.updateInvSupplier(supplier)`。Mapper 新增 SQL `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSupplierMapper.xml:84-93` 插入 `remark`，查询 resultMap 和列表详情 SQL 也映射 `remark`，但更新 SQL `:96-110` 只更新名称、编码、联系人、电话、邮箱、地址、结算方式、合作状态、启用状态和更新时间，没有 `remark = #{remark}`。MySQL 只读统计当前 `inv_supplier=17`、非空备注 `16`，样例备注包括“含专票、含运费、含袋子”等采购结算信息。
- 影响：供应商备注承载采购结算、发票、运费、分装等实际协作信息，属于采购/仓库维护供应商档案的重要字段。页面允许编辑但不落库，会让管理员误以为供应商说明已修正，后续采购和商品维护仍看到旧备注；多账号协作时尤其容易造成采购口径不一致。
- 建议：`updateInvSupplier` 应同步更新 `remark`，并补“打开供应商编辑、修改备注、保存、重新打开详情”的回归测试；同时检查导出是否需要包含供应商备注。若备注属于只读历史导入信息，应把编辑弹窗中的备注改成只读或移除，避免保存成功但字段不变。

### P3-238 客户备注新增可写但编辑不会更新，客户档案备注字段保存链路不完整

- 现象：客户新增/编辑弹窗提供“备注”文本域，客户查询 SQL 和数据库表也都有 `remark` 字段；但客户更新 SQL 没有更新 `remark`。当前运行态客户表为空，暂时不会暴露存量数据修改失败，但一旦门店开始录入客户，后续在编辑页修改客户备注会提示“操作成功”却不会落库。
- 证据：前端 `erp-ui/src/views/inventory/customer/index.vue:138-140` 提供备注输入，`:170-184` 编辑时读取客户详情并把整个 `form` 提交给 `updateCustomer(this.form)`；API `erp-ui/src/api/inventory/customer.js:18-20` 走 `/inventory/customer/update`。后端 `InvCustomerController.edit()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java:57-64` 调用 `customerService.saveCustomer()`，服务层更新分支 `InvCustomerServiceImpl.saveCustomer()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceImpl.java:47-52` 直接调用 `customerMapper.updateInvCustomer(customer)`。Mapper resultMap 和查询 SQL 映射 `remark`，新增 SQL `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerMapper.xml:56-65` 插入 `remark`，但更新 SQL `:68-84` 只更新客户名称、编码、联系人、电话、邮箱、地址、信用额度、已用额度、账期、等级、状态和更新时间，没有 `remark = #{remark}`。MySQL 只读统计当前 `inv_customer=0`，表结构存在 `remark varchar(500)`。
- 影响：客户备注通常用于记录账期例外、客户偏好、对账说明或销售跟进事项。页面允许维护但编辑不生效，会在客户开始录入后造成“保存成功但资料仍旧”的体验；多门店销售协作时，备注无法修正会直接影响客户沟通和信用管理。
- 建议：`updateInvCustomer` 应同步更新 `remark`，并补“新增客户带备注、编辑备注、重新打开编辑/列表读取”的回归测试；如果客户备注不进入当前交付范围，应从表单移除或明确只读。客户表当前为空，修复后可先用测试客户验证，再开放门店录入。

### P3-14 商品待完善指标不可点击，也没有完整性筛选

- 现象：商品页指标能显示 `121 当前页待完善`，但该指标不能点击，搜索区也没有“缺编码 / 缺供应商 / 缺售价 / 缺图片”等完整性筛选；管理员只能靠人工滚动 162 行查找缺失字段。
- 证据：浏览器实测 `/cangku/product` 重置后显示 `121 当前页待完善`；前端 `erp-ui/src/views/inventory/product/index.vue:55-80` 的搜索条件只有商品名称、编码、等级、供应商、状态和按钮，`erp-ui/src/views/inventory/product/index.vue:544-552` 只计算指标值，没有绑定下钻动作。
- 影响：商品主数据缺失规模很大，但页面没有提供修复队列；管理员难以按“先补供应商、再补售价、再补图片”的顺序治理数据。
- 建议：把“待完善”做成可点击筛选，并增加完整性筛选条件和缺失字段标签；列表可增加“缺失项”列，支持按供应商/价格/编码/图片缺失批量定位。

### P3-15 商品图文素材字段全为空，页面只支持手填 URL

- 现象：商品详情有图文素材区，新增/编辑也提供主图、外包装图、干茶图、茶汤图、叶底图、补充图片字段，但当前 162 条商品所有图片 URL 字段均为空；页面只提供 URL 输入框，没有上传、预览或图片缺失状态。
- 证据：数据库统计 `image_url`、`package_image_url`、`dry_tea_image_url`、`tea_soup_image_url`、`leaf_bottom_image_url`、`extra_image_url` 非空数量均为 0；浏览器查看“清香铁观音”详情时图片字段均显示 `-`。前端 `erp-ui/src/views/inventory/product/index.vue:368-389` 只提供多个 URL 输入框，详情区 `erp-ui/src/views/inventory/product/index.vue:238-268` 也只显示 URL 文本。
- 影响：商品页名义上支持图文素材，但业务上无法直观看到商品图片，维护人员也没有上传和校验入口；用于销售展示、采购验货或门店识别时不够人性化。
- 建议：提供文件上传/图片预览组件，支持主图必填或缺图标记；导入模板也应说明图片字段要求，后台可校验 URL 可访问性。

### P3-239 商品备注详情可见但编辑不会更新，162 条存量备注无法在页面修正

- 现象：商品详情会展示“备注”，商品编辑抽屉也提供“备注”文本域；保存时前端会提交 `remark`，新增 SQL 也会写入 `remark`。但商品更新 SQL 没有更新 `remark` 字段。当前运行态 162 条商品的 `remark` 全部非空，多数为导入行号说明，管理员在页面上修改或清空商品备注会提示“保存成功”，刷新后仍是旧备注。
- 证据：前端 `erp-ui/src/views/inventory/product/index.vue:185-188` 在详情展示 `form.remark`，`:324-325` 在编辑表单中提供备注输入，`:809-817` 编辑时读取商品详情写入 `form`，`:830-856` 保存时把 `form` 复制为 payload 传给 `updateProduct`，只剔除 `productCode` 和隐藏成本字段。后端 `InvProductController.edit()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:66-73` 调用 `productService.saveProduct()`，服务层更新分支 `InvProductServiceImpl.saveProduct()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:71-84` 最终调用 `productMapper.updateInvProduct(product)`。Mapper 新增 SQL `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml:136-153` 插入 `remark`，但更新 SQL `:156-189` 更新商品名称、分类、价格、供应商、图文、状态等字段，没有 `remark = #{remark}`。MySQL 只读统计当前有效商品 `162` 条、非空 `remark` 为 `162` 条，样例为 `Excel row 4/5/6`。
- 影响：商品备注是商品主数据治理、导入来源、内部说明或门店识别补充信息的一部分。当前所有商品都有备注，页面却不能修正这些备注，会让导入行号等临时说明长期留在正式档案里；多账号维护商品时，用户看到保存成功但备注不变，会误判资料已被修正。
- 建议：`updateInvProduct` 应同步更新 `remark`，并补“打开商品编辑、修改备注、保存、重新打开详情”的回归测试；如果备注只想保留导入来源，应改名为只读“导入备注/来源说明”，另设可维护的业务备注字段，避免把不可编辑的系统信息伪装成可编辑字段。

### P3-240 采购单和销售单草稿备注新增可写但编辑不会更新

- 现象：采购单和销售单新增/编辑表单都有“备注”文本域，新增草稿时会把 `remark` 写入订单表；但编辑已有草稿再保存时，采购单和销售单的更新 SQL 都不会更新 `remark`。当前运行态正式采购单和销售单均为 0，暂时没有存量样例，但一旦用户先保存草稿、再补充或修改备注，页面会提示“已保存草稿/提交成功”，实际订单备注仍是旧值。
- 证据：采购页 `erp-ui/src/views/inventory/purchase/index.vue:147-148` 提供备注输入，`:477-485` 编辑时读取详情回填表单，`:505-520` 保存草稿或提交时提交 payload；销售页 `erp-ui/src/views/inventory/sales/index.vue:120-121` 提供备注输入，`:261-264` 编辑时读取详情回填表单，`:268-277` 保存或提交时提交 `this.form`。服务层采购 `InvPurchaseServiceImpl.saveDraft()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:88-105` 编辑草稿时调用 `purchaseOrderMapper.updateInvPurchaseOrder(order)`；销售 `InvSalesServiceImpl.saveDraft()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java:96-112` 编辑草稿时调用 `salesOrderMapper.updateInvSalesOrder(order)`。两个 Mapper 的新增 SQL 分别在 `InvPurchaseOrderMapper.xml:93-104`、`InvSalesOrderMapper.xml:106-115` 插入 `remark`，但更新 SQL `InvPurchaseOrderMapper.xml:107-118`、`InvSalesOrderMapper.xml:118-128` 都只更新标题、供应商/客户、金额、日期、状态和更新时间，没有 `remark = #{remark}`。MySQL 只读统计当前 `inv_purchase_order=0`、`inv_sales_order=0`，两表结构均存在 `remark varchar(500)`。
- 影响：订单备注通常记录采购原因、供应商沟通、客户特殊要求或内部说明，属于草稿阶段最容易反复补充的信息。新增能保存、编辑不能更新会让操作员误以为备注已补齐；后续收货、质检、发货、取消和状态审计如果依赖订单备注，就会继续使用旧说明。
- 建议：采购单和销售单的更新 SQL 都应同步更新 `remark`，并补“新增草稿带备注、编辑草稿修改备注、保存/提交后详情读取”的回归测试。若提交后不允许改备注，应在提交后锁定字段；但草稿编辑阶段必须做到表单字段和落库字段一致。

### P3-241 调拨审批配置挂在系统管理下，和库存调拨业务入口割裂

- 现象：调拨审批规则页面的真实组件、接口和权限都属于库存调拨业务，但电脑端菜单把它放在“系统管理 / 调拨审批配置”下，调拨管理、仓库管理或调拨记录页面内没有规则配置入口。管理员排查“调拨提交为什么要审批 / 为什么匹配不到规则”时，需要从业务调拨流跳到系统管理树下找配置页。
- 证据：运行态 `sys_menu` 显示 `4410 / 调拨审批配置` 的 `parent_id=1`，父菜单是 `系统管理`，path 为 `transfer-rules`，组件是 `inventory/transfer/rules`，权限是 `inv:transfer:rule:list`；同一运行态中 `4100 / 进销存管理 / 调拨管理` 和 `4460 / 仓库管理 / 调拨管理` 都复用 `inventory/transfer/index`，但不包含审批规则子入口。前端规则页 `erp-ui/src/views/inventory/transfer/rules.vue:25-26`、`:61-63` 使用 `inv:transfer:rule:list/add/edit/query/remove` 控制搜索、新增、编辑、预览、删除；`rg` 反查 `erp-ui/src/views/inventory/transfer`、首页、布局和静态路由，除规则页自身外没有“审批规则/调拨规则/transfer-rules”的业务入口链接。当前这些规则权限只授给 `admin` 和 `zjl` 两类角色。
- 影响：这不是接口错误，但会让多账号多店铺验收口径割裂：调拨发起人、仓库发货人和审批规则维护人看到的是同一套调拨流程，却分别散落在“进销存管理 / 仓库管理 / 系统管理”三棵菜单中。总经理或管理员在调拨详情、待审批、调拨记录页发现规则问题时，页面没有引导到配置；角色授权时也容易把业务流程配置误认为纯系统参数，导致规则维护权限、业务审批权限和调拨查看权限难以一起验收。
- 建议：把调拨审批配置作为调拨管理的业务配置入口治理：可以保留系统管理里的配置总入口，但在“进销存管理 / 调拨管理”或“仓库管理 / 调拨管理”下增加受 `inv:transfer:rule:list` 控制的“审批规则”入口，或在调拨详情/提交审批失败/规则预览处提供只读跳转。菜单、面包屑和角色授权说明要统一称为“调拨审批规则”，并在角色矩阵中明确“谁能发起调拨、谁能审批、谁能配置规则”。

### P3-242 调拨审批规则编辑按钮只看 edit 权限，但打开编辑依赖 query 详情权限

- 现象：调拨审批配置列表的“编辑”按钮只判断 `inv:transfer:rule:edit`，但点击后会先调用规则详情接口，而详情接口要求 `inv:transfer:rule:query`。当前默认角色 `admin/zjl` 同时持有两项权限，所以现有数据不会立刻触发；但权限模型允许后续配置出“有编辑按钮、接口无权限、弹窗打不开”的组合。
- 证据：前端行内编辑按钮在 `erp-ui/src/views/inventory/transfer/rules.vue:61` 只使用 `v-hasPermi="['inv:transfer:rule:edit']"`；`openForm(row)` 在 `:437-445` 点击编辑时调用 `getTransferApprovalRule(row.ruleId)`，API 映射到 `GET /inventory/transfer/rule/{ruleId}`，见 `erp-ui/src/api/inventory/transferApprovalRule.js:8-10`。后端 `InvTransferApprovalRuleController.getInfo()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java:41-45` 要求 `inv:transfer:rule:query`，而实际更新接口 `edit()` 在 `:57-63` 才要求 `inv:transfer:rule:edit`。运行态角色授权显示 `admin` 和 `zjl` 都拥有 `list/query/add/edit/remove` 全套规则权限，当前没有只授 edit 的样例角色。
- 影响：调拨审批规则是高风险流程配置，角色管理员可能想把“能修改规则”和“能查看规则详情/预览规则”拆开，或者在最小权限治理时漏勾 query。此时页面会显示编辑入口，但第一步读取详情就被后端拒绝，用户只能看到失败提示，难以判断是缺查询权限还是编辑功能坏了；同时也削弱了 query 权限语义，因为它实际是编辑流程的隐式前置依赖。
- 建议：编辑入口应同时表达 `edit + query` 依赖，或后端提供受 `inv:transfer:rule:edit` 保护的编辑加载接口；如果产品定义“编辑天然包含读取详情”，则详情接口可允许 query 或 edit 任一权限，并在角色配置说明中写清“修改规则需要读取规则详情”。权限回归应补一个只授 edit、不授 query 的角色用例，确保页面给出明确禁用或缺权限提示。

### P3-243 劳动合同草稿编辑按钮只看 add 权限，但打开编辑依赖 query 详情权限

- 现象：劳动合同签约单列表中，草稿行的“编辑”按钮只判断 `oa:laborContract:add`，但点击编辑会先读取合同详情接口，而详情接口要求 `oa:laborContract:query`。当前运行态 9 份合同里没有草稿，且默认有劳动合同新增权限的 `admin/yyjl` 同时也有查询权限，所以现有样例不会立刻触发；但权限模型允许后续配置出“能新增/编辑草稿、不能读取详情”的断裂组合。
- 证据：前端 `erp-ui/src/views/oa/laborContract/index.vue:54-56` 中详情按钮用 `oa:laborContract:query`，草稿编辑按钮却用 `oa:laborContract:add`；`handleEdit(row)` 在 `:664-672` 点击编辑时调用 `getLaborContract(row.contractId)` 读取详情。API `erp-ui/src/api/oa/laborContract.js:12-17` 映射到 `GET /oa/laborContract/{contractId}`；后端 `OaLaborContractController.detail()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java:97-101` 要求 `oa:laborContract:query`，而保存草稿接口 `save()` 在 `:70-76` 要求 `oa:laborContract:add`。运行态权限矩阵显示 `admin` 和 `yyjl` 当前都同时拥有 `add/query/list`，但 `oa_labor_contract` 当前状态为 `signed=1`、`voided=8`、`draft=0`。
- 影响：劳动合同草稿编辑和发起签约同属一个维护流程，角色管理员看到“合同新增”权限时会自然理解为可新建和编辑草稿。若后续只给某个门店运营新增权限而漏给查询权限，页面会出现草稿编辑按钮但弹窗无法加载，用户很难判断是草稿坏了、接口无权限，还是需要额外勾选查询权限。多店铺权限回归时，这类隐式依赖会让“新增合同”和“查看合同详情”的权限边界不清晰。
- 建议：劳动合同草稿编辑入口应同时判断 `oa:laborContract:add + oa:laborContract:query`，或提供受 `add` 保护的草稿编辑加载接口；如果产品定义“草稿编辑天然包含读取详情”，则详情接口可允许 add 或 query 任一权限，但必须继续按合同组织范围过滤。角色配置说明和自动化用例应覆盖只授 add、不授 query 的角色，页面应给出明确缺权限提示而不是显示可点但失败的按钮。

### P3-244 系统基础资料编辑按钮只看 edit 权限，但打开编辑普遍依赖 query 详情权限

- 现象：用户、角色、岗位、部门、菜单、字典类型/字典项和通知公告等系统基础资料页，行内或顶部“修改/数据权限”入口主要只判断 `*:edit` 权限；但点击后会先调用对应详情接口，而详情接口要求 `*:query`。当前运行态 `common/zjl` 两个默认角色都同时拥有 edit 和 query，所以现有角色不会立刻触发；但权限树允许后续配置出“有修改按钮、无详情权限、弹窗打不开”的组合。
- 证据：前端示例包括用户页 `erp-ui/src/views/system/user/index.vue:84-87` 的修改按钮用 `system:user:edit`，`:433-446` 调 `getUser(userId)`；角色页 `erp-ui/src/views/system/role/index.vue:132-146` 的修改/数据权限入口用 `system:role:edit`，`:521-527`、`:549-553` 调 `getRole`；岗位、部门、菜单、字典类型、字典项和公告页也分别在 `post/index.vue:57-58`、`dept/index.vue:111-112`、`menu/index.vue:107-108`、`dict/index.vue:141-142`、`dict/data.vue:121-122`、`notice/index.vue:114-115` 只看 edit 后再调详情。后端这些详情接口分别要求 `system:user:query`、`system:role:query`、`system:post:query`、`system:dept:query`、`system:menu:query`、`system:dict:query`、`system:notice:query`，见对应 Controller 的 `getInfo()` 注解。MySQL 复核当前这些 edit/query 权限节点均启用，`common` 和 `zjl` 当前都成对持有；参数设置详情读取无权限注解的问题已由 P2-111 单独记录，不并入本条。
- 影响：系统基础资料是多账号多店铺的权限、组织、岗位和公告底座，角色管理员很容易把“修改”和“查询”理解成可独立授权。若最小权限配置时只勾 edit，页面会展示修改入口但第一步读取详情失败；若为了让编辑可用而必须额外授 query，又没有在角色矩阵里说明依赖，权限验收会变成靠经验补勾。公告、用户、角色这类页面还涉及正文、密码哈希、角色菜单和组织范围等敏感数据，读写权限边界不应隐式混在按钮失败里。
- 建议：系统基础资料编辑入口应统一表达 `edit + query` 依赖，或后端把“编辑前读取详情”改成允许 edit 权限访问的专用加载接口并返回最小编辑字段。角色配置页和权限说明要标出“修改需要详情读取”，并为用户、角色、岗位、部门、菜单、字典和公告补只授 edit、不授 query 的回归用例；参数详情读取则按 P2-111 收紧为后台查询权限或专用白名单接口。

### P3-245 新增用户按钮只看 add 权限，但打开新增弹窗依赖 user query 接口

- 现象：用户管理页“新增”按钮只判断 `system:user:add`，但点击新增后并不是直接打开空表单，而是先调用用户详情接口 `/system/user/` 加载角色和岗位选项。该接口要求 `system:user:query`。当前运行态 `common/zjl` 都同时持有 add/query，所以现有默认角色不会立刻触发；但权限模型允许后续配置出“能新增用户、不能打开新增弹窗”的组合。
- 证据：前端新增按钮在 `erp-ui/src/views/system/user/index.vue:39` 使用 `v-hasPermi="['system:user:add']"`；`handleAdd()` 在 `:422-431` 调用 `getUser()`，再把 `response.posts/response.roles` 写入新增表单的岗位和角色选项。API `erp-ui/src/api/system/user.js:14-19` 在没有 `userId` 时请求 `/system/user/`；后端 `SysUserController.getInfo()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:246-263` 对 `@GetMapping(value = { "/", "/{userId}" })` 统一要求 `system:user:query`，并返回全量角色和岗位选项。真正新增保存接口 `SysUserController.add()` 在 `:269-295` 才要求 `system:user:add`。MySQL 复核当前 `system:user:list/query/add` 均启用，`common` 和 `zjl` 目前同时持有三项权限。
- 影响：新用户创建是多账号多店铺验收的核心路径。角色管理员若按字面只给账号管理员 `system:user:add`，页面会显示新增按钮但弹窗加载前就被详情接口拒绝；如果为了新增可用而额外授 `system:user:query`，又会同时打开用户详情读取面，和最小化账号资料读取权限冲突。这个问题和 P3-244 的“编辑前详情依赖”不同，发生在创建流程的第一步，用户还没进入表单。
- 建议：新增用户应使用专用的 `create-options` 接口，受 `system:user:add` 保护，只返回新增表单需要的启用角色、岗位和必要默认值；或前端新增按钮同时判断 `system:user:add + system:user:query` 并在权限矩阵中明确依赖。返回的角色/岗位选项应按当前管理员可授权范围过滤，并避免复用会返回用户详情和密码哈希字段的通用查询接口。

### P3-246 角色分配用户副页面只看 edit 路由权限，但首屏和选人列表依赖 role list 接口

- 现象：角色列表“更多 -> 分配用户”、隐藏路由 `/system/role-auth/user/:roleId` 以及副页面里的“添加用户 / 批量取消授权 / 取消授权”都按 `system:role:edit` 放行；但副页面创建后立即加载已授权用户列表，“添加用户”弹窗又加载未授权候选列表，这两个读取接口后端要求 `system:role:list`。当前运行态 `common/zjl` 同时持有 list/edit，所以现有默认角色暂不触发；但权限模型允许配置出“能维护角色授权、不能读取授权列表”的断裂组合。
- 证据：隐藏路由在 `erp-ui/src/router/index.js:124-133` 配置 `permissions: ['system:role:edit']`；角色列表“分配用户”入口在 `erp-ui/src/views/system/role/index.vue:142-148` 也使用 `system:role:edit`。授权用户页 `erp-ui/src/views/system/role/authUser.vue:28-48` 的添加用户和批量取消授权按钮、`:78-86` 的取消授权按钮均按 `system:role:edit` 展示；页面 `created()` 在 `:134-150` 读取 `roleId` 后立即调用 `allocatedUserList()`，选择用户弹窗 `erp-ui/src/views/system/role/selectUser.vue:90-108` 打开时调用 `unallocatedUserList()`。前端 API 分别请求 `/system/role/authUser/allocatedList` 和 `/system/role/authUser/unallocatedList`，见 `erp-ui/src/api/system/role.js:68-84`；后端 `SysRoleController.allocatedList/unallocatedList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:177-197` 要求 `system:role:list`，而取消/批量取消/批量选择授权在 `:203-231` 才要求 `system:role:edit`。MySQL 复核当前只有 `common / 普通角色` 和 `zjl / 总经理` 命中这两项权限，且二者都同时拥有 `system:role:list` 与 `system:role:edit`。
- 影响：角色分配用户是多账号多店铺权限治理的核心副流程。若后续按最小权限只给授权操作员 `system:role:edit`，页面入口和写按钮都会出现，但首屏列表或选人弹窗会被后端拒绝，用户无法判断是缺少角色列表权限还是页面异常；若为了解决再补 `system:role:list`，又会扩大角色列表读取面。该问题和 P2-37 的“授权写操作缺少独立权限点”不同，本条记录的是副页面入口权限和读取依赖没有对齐。
- 建议：角色分配用户副页面要统一入口和列表依赖：要么入口、路由和按钮同时要求 `system:role:edit + system:role:list`，并在缺少读取权限时禁用或说明原因；要么新增 `system:role:authUser` 专用权限，并让已授权/未授权列表查询、授权、取消授权都使用该权限或读写子权限。角色配置页应明确展示“分配用户需要哪些读取依赖”，并补只授 edit、不授 list 的回归用例。

### P3-247 用户分配角色副页面只看 edit 路由权限，但首屏授权角色读取依赖 user query 接口

- 现象：用户列表“更多 -> 分配角色”和隐藏路由 `/system/user-auth/role/:userId` 都按 `system:user:edit` 放行；但副页面创建后会立即调用 `/system/user/authRole/{userId}` 读取目标用户和角色列表，该接口后端要求 `system:user:query`。当前运行态 `common/zjl` 同时持有 query/edit，所以现有默认角色暂不触发；但权限模型允许配置出“有用户授权入口、首屏读取被拒绝”的组合。
- 证据：隐藏路由在 `erp-ui/src/router/index.js:110-119` 配置 `permissions: ['system:user:edit']`；用户列表“分配角色”下拉项在 `erp-ui/src/views/system/user/index.vue:88-93` 也使用 `system:user:edit`，点击后 `handleAuthRole()` 在 `:461-465` 跳转到 `/system/user-auth/role/{userId}`。授权角色页 `erp-ui/src/views/system/user/authRole.vue:69-85` 在 `created()` 中调用 `getAuthRole(userId)`，把响应 `user/roles` 写入基本信息和角色表；提交按钮 `:107-114` 再调用 `updateAuthRole()`。前端 API 在 `erp-ui/src/api/system/user.js:114-128` 分别请求 `GET /system/user/authRole/{userId}` 和 `PUT /system/user/authRole`；后端 `SysUserController.authRole()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:378-388` 要求 `system:user:query`，而保存授权 `insertAuthRole()` 在 `:416-424` 才要求 `system:user:edit`。MySQL 复核当前只有 `common / 普通角色` 和 `zjl / 总经理` 命中这两项权限，且二者都同时拥有 `system:user:query` 与 `system:user:edit`。
- 影响：用户分配角色是创建新账号后的关键闭环。若后续按最小权限只给账号管理员 `system:user:edit` 用于角色授权，页面入口会出现，但副页面首屏无法加载目标用户、可选角色和已选角色，管理员会把缺查询权限误判为授权页故障；若为了解决再补 `system:user:query`，又会扩大用户详情读取面，并叠加 P2-113 中该接口返回密码哈希的问题。该问题和 P2-37 的“授权写操作缺少独立权限点”不同，本条记录的是入口权限和首屏读取依赖没有对齐。
- 建议：用户分配角色副页面要统一入口和读取依赖：要么入口、路由和提交前置同时要求 `system:user:edit + system:user:query`，并在缺少读取权限时禁用或提示；要么新增 `system:user:authRole` 专用权限，并提供受该权限保护的最小授权加载接口，只返回目标用户必要展示字段、可授权角色和已选角色。角色配置页应明确写出“分配角色需要哪些读取依赖”，并补只授 edit、不授 query 的回归用例。

### P3-248 薪资配置页面入口只看 salary list，但首屏初始化依赖 salary query 和 salary role

- 现象：系统管理里的“薪资配置”页面入口使用 `system:salary:list`，但页面初始化不是只加载薪资方案列表，而是同时请求可绑定员工、薪资方案选项和核算门店树；这些接口分别要求 `system:salary:role` 和 `system:salary:query`。当前运行态 `admin/common/zjl` 同时持有 list/query/role，所以默认角色暂不触发；但权限模型允许后续配置出“能进入薪资配置页面、首屏初始化接口被拒绝”的组合。
- 证据：运行态 `sys_menu` 中 `180 / 薪资配置` 的页面权限是 `system:salary:list`，另有 `1180 / 薪资查询 / system:salary:query`、`1186 / 角色薪资配置 / system:salary:role`、`1187 / 薪资列表 / system:salary:list`。前端 `erp-ui/src/views/system/salary/index.vue:909-922` 的 `initPage()` 首屏 `Promise.all` 同时调用 `loadSalaryUsers()`、`salarySchemeOptions()`、`salaryShopTree()`；`loadSalaryUsers()` 在 `:924-928` 调用 `/system/salaryConfig/users`，薪资方案选项和门店树 API 分别在 `erp-ui/src/api/system/salaryConfig.js:20-43` 请求 `/system/salaryConfig/options` 和 `/system/salaryConfig/shopTree`。后端 `SysSalaryConfigController.list()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:58-64` 要求 `system:salary:list`，但 `options()` 在 `:84-89` 要求 `system:salary:query`，`users()` 和 `shopTree()` 在 `:91-108` 要求 `system:salary:role`。MySQL 复核当前命中这些权限的 `admin / 超级管理员`、`common / 普通角色`、`zjl / 总经理` 都三项成对持有。
- 影响：薪资配置同时包含方案、档位和员工绑定，属于高敏感人事薪资配置。若角色管理员只授页面列表权限，菜单可见但页面首屏会在员工/门店/方案选项加载时失败；若为了让页面可用而补 `system:salary:role`，又会打开员工薪资绑定维护能力，并叠加 P2-161 中“角色薪资配置”权限语义和员工绑定实际能力不一致的问题。权限依赖没有在入口处表达，会让薪资只读、方案维护、员工绑定维护三类角色难以拆分。
- 建议：薪资配置页按 tab 拆分初始化和权限：进入页面只用 `system:salary:list` 加载方案列表；档位/方案详情再按 `system:salary:query` 加载；员工绑定 tab 只有具备员工薪资绑定权限时才请求员工和门店树。若页面必须一次性初始化全部 tab，入口权限和角色配置说明就要显式要求 `system:salary:list + system:salary:query + system:salary:user/role`，并补只授 list、不授 query/role 的回归用例。

### P3-249 薪资方案编辑和档位入口没有显式表达 salary query 依赖

- 现象：薪资方案列表的“编辑”按钮只判断 `system:salary:edit`，但点击后会先读取薪资方案详情接口，该接口要求 `system:salary:query`；同一列表里的“档位”入口和档位页“刷新档位”没有任何前端权限判断，但会读取方案档位接口，该接口同样要求 `system:salary:query`。当前运行态 `admin/common/zjl` 同时持有 query/edit，所以默认角色暂不触发；但权限模型允许配置出“能看到编辑或档位入口、读取接口被拒绝”的组合。
- 证据：前端 `erp-ui/src/views/system/salary/index.vue:54-58` 的方案行操作中，“编辑”使用 `v-hasPermi="['system:salary:edit']"`，而“档位”按钮没有 `v-hasPermi`；`:766-775` 的 `handleEditScheme()` 点击编辑后调用 `getSalaryScheme(row.schemeId)`，API 在 `erp-ui/src/api/system/salaryConfig.js:12-17` 请求 `GET /system/salaryConfig/{schemeId}`。档位入口 `selectScheme()` 在 `erp-ui/src/views/system/salary/index.vue:822-825` 切到档位 tab 后调用 `loadSchemeItems()`，`:827-837` 再调用 `listSalaryItems(schemeId)`；API 在 `erp-ui/src/api/system/salaryConfig.js:72-77` 请求 `GET /system/salaryConfig/{schemeId}/items`。后端 `SysSalaryConfigController.getInfo()` 和 `items()` 分别在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:77-81`、`:136-140` 标注 `system:salary:query`，而保存方案和保存档位接口在 `:119-125`、`:153-159` 才要求 `system:salary:edit`。MySQL 复核当前 `admin / 超级管理员`、`common / 普通角色`、`zjl / 总经理` 都同时拥有 `system:salary:query` 与 `system:salary:edit`。
- 影响：薪资方案和档位属于工资计算的基础数据，管理员可能希望拆成“只读查看档位”“维护方案资料”“维护工资档位”不同角色。当前页面却把编辑入口和档位读取依赖隐藏在接口失败里：只授 edit 时按钮可见但编辑弹窗打不开，只授 list 时“档位”入口可见但明细接口拒绝。结合 P3-248 的首屏初始化依赖，薪资配置页的只读、查询、编辑和员工绑定权限边界都不够清晰。
- 建议：方案编辑入口应显式要求 `system:salary:edit + system:salary:query`，或后端提供受 edit 保护的最小编辑加载接口；“档位/刷新档位”入口应至少按 `system:salary:query` 控制，缺少查询权限时不显示或禁用并提示。若产品要拆出“档位维护”能力，应新增 `system:salary:itemQuery/add/edit/remove` 等更细权限，并补只授 edit、不授 query、只授 list、不授 query 的回归用例。

### P3-250 员工薪资绑定“移除”确认不提示最后一条绑定会让员工失去薪资方案

- 现象：薪资配置页员工绑定 tab 的“移除”只提示“确认移除该员工的薪资绑定吗？”，没有显示员工、门店、方案、档位、是否最后一条有效绑定，也没有提示移除后工资计算会如何变化。当前运行态 `sys_user_salary_scheme` 只有 `user_id=105 / ii / shop_dept_id=108 / scheme_id=10001 / item_id=11001` 这一条员工薪资绑定，移除它会让该员工没有任何薪资方案。
- 证据：前端 `erp-ui/src/views/system/salary/index.vue:1036-1055` 的 `handleUnbindRule` 在确认后从 `this.userBindings` 过滤掉当前 `relationId`，再调用 `saveUserSalary(row.userId, bindings)`；后端 `SysSalaryConfigServiceImpl.saveUserSalarySchemes()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSalaryConfigServiceImpl.java:254-260` 先 `deleteUserSalarySchemeByUserId(userId)`，当剩余列表为空时直接返回成功。工资计算链路又要求员工在当前门店、当前月份存在有效 `sys_user_salary_scheme`，否则会抛“员工 [xxx] 未配置薪资方案”。本轮只读复核，未执行移除。
- 影响：管理员以为只是在删除一行关系，实际可能把员工最后一条薪资绑定清空；后续 OA 工资计算才暴露“未配置薪资方案”。这个问题和 P2-157 的“保存接口缺少员工门店关系校验”不同，重点是移除操作的确认信息和最后绑定风险没有被前端流程显式呈现。
- 建议：员工薪资绑定移除要做成有上下文的确认，至少展示员工、核算门店、方案、档位、生效日期和移除后剩余有效绑定数；如果这是员工最后一条有效薪资绑定，应阻止或要求先选择替代方案，并在后端保存前校验“最后有效绑定”策略，避免泛化确认后让工资计算链路失效。

### P3-251 定时任务任务名称可点详情，但入口没有表达 monitor:job:query 依赖

- 现象：定时任务列表的“任务名称”以链接样式展示，点击会打开任务详情弹窗；但该链接没有任何前端权限判断，页面入口和列表接口只要求 `monitor:job:list`。当前运行态 `common/zjl` 默认同时持有 `monitor:job:list` 和 `monitor:job:query`，所以默认账号暂不触发；但权限树允许只授列表权限，届时用户能看到可点击详情入口，点击后详情接口会被后端拒绝。
- 证据：前端 `erp-ui/src/views/monitor/job/index.vue:102-105` 把任务名称渲染为 `<a>` 并直接 `@click="handleView(scope.row)"`，没有 `v-hasPermi`；`:402-407` 的 `handleView` 调用 `getJob(row.jobId)` 后打开 `JobDetail` 弹窗。后端 `erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java:46-52` 的列表接口要求 `monitor:job:list`，`:71-75` 的详情接口要求 `monitor:job:query`。运行态 `sys_menu` 中 `110 / 定时任务` 是 `monitor:job:list`，`1049 / 任务查询` 是 `monitor:job:query`，MySQL 复核当前只有 `common / 普通角色` 和 `zjl / 总经理` 同时持有这两项权限。
- 影响：定时任务属于高风险运维能力，列表、详情、修改、执行、日志应有清晰的最小权限边界。当前详情入口把 `list` 和 `query` 的依赖隐藏在接口失败里，后续配置只读任务列表角色时会出现“页面给了可点入口但详情打不开”的体验；如果为了避免失败而把 `query` 统一补给列表角色，又会扩大任务目标、cron、执行策略等配置详情的可见范围。
- 建议：任务名称详情入口应显式按 `monitor:job:query` 控制；没有查询权限时展示普通文本或禁用链接并给出权限提示。若产品希望列表角色也可看基础详情，应拆出列表摘要 DTO 和受 `list` 保护的只读摘要弹窗，把完整调用目标、调度策略、创建更新信息留给 `query` 权限，并补只授 `list`、不授 `query` 的回归用例。

### P3-252 OA 待办/已办页面提供标题、申请人和状态筛选，但后端任务查询未应用这些条件

- 现象：OA“我的待办”页面提供标题、申请人筛选，“我的已办”页面提供标题、申请人、状态筛选，并把这些条件传给 `/oa/purchase/todo`、`/oa/purchase/done`；但后端最终查询待办/已办采购单时只按工作流任务 ID 或流程实例 ID 和组织范围过滤，没有应用标题、申请人或状态条件。当前运行态 `oa_purchase=0`、`ACT_RU_TASK=0`、`ACT_HI_TASKINST=0` 不触发；恢复采购审批或导入历史流程后，页面搜索会看似可用但结果不收敛。
- 证据：前端 `erp-ui/src/views/oa/todo/index.vue:43-53` 定义标题和申请人筛选，`:163-167` 调用 `listTodoPurchases(this.queryParams)`；`erp-ui/src/views/oa/done/index.vue:47-63` 定义标题、申请人、状态筛选，`:174-178` 调用 `listDonePurchases(this.queryParams)`。API 在 `erp-ui/src/api/oa/purchase.js:27-40` 把 params 传到后端。后端 `OaPurchaseController.todoList/doneList` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java:60-74` 分别调用服务层，服务层 `selectDonePurchases()` 在 `OaPurchaseServiceImpl.java:188-217` 只收集已办流程实例并设置 `params.procIds`。Mapper `OaPurchaseMapper.xml:124-156` 的 `selectTodoPurchaseListByTaskIds` 和 `selectDonePurchaseListByProcessIds` 只判断 `current_task_id/process_instance_id` 和 `params.scopeDeptIds`，没有 `title/applicantName/status` 条件；相比之下普通采购列表在 `:85-102` 至少有 `title/status` 过滤。
- 影响：审批人按标题、申请人或已通过/已驳回筛选时，可能仍看到当前任务范围下的全部记录；导出接口也复用同一服务方法，导出的待办/已办 Excel 会和页面筛选预期不一致。多店铺审批恢复后，审批工作台会让用户误以为筛选条件已经生效，影响待办定位、已办复盘和导出交付。
- 建议：待办和已办 mapper 应复用采购单筛选条件，至少支持 `title`、`applicantName/applicantId`、`status`，并确保导出和列表口径一致；如果部分条件只能在工作流层过滤，应在服务层先按任务/实例范围再按采购字段二次过滤，并返回准确分页总数。页面顶部统计也应明确是全量统计还是当前页统计，避免和筛选结果混淆。

### P3-149 商品主图字段存在于页面和数据库，但导入模板/导出漏掉主图 URL

- 现象：商品新增/编辑和详情都显示“主图 URL”，数据库也有 `inv_product.image_url` 字段；但商品导入模板、普通 Excel 导入和导出都不会包含主图 URL。外包装图、干茶图、茶汤图、叶底图、补充图片都有 Excel 字段，唯独主图被批量维护链路遗漏。
- 证据：当前运行态 `inv_product` 表包含 `image_url`，且 162 条商品的主图和其他图片字段均为空。前端 `erp-ui/src/views/inventory/product/index.vue:246-247` 在详情显示“主图 URL”，`:372-373` 在表单中编辑 `form.imageUrl`。后端 `InvProduct` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvProduct.java:84` 定义 `imageUrl`，但没有 `@Excel` 注解；同文件 `:86-99` 的 `packageImageUrl/dryTeaImageUrl/teaSoupImageUrl/leafBottomImageUrl/extraImageUrl` 均有 `@Excel(name = "...图片链接")`。商品导入模板和导出都通过 `InvProductController` 中的 `ExcelUtil<InvProduct>` 生成或解析，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:85-130`。
- 影响：管理员若通过批量导入补齐 162 个商品的图文素材，无法在模板里维护主图；若先在页面手工填了主图，再导出给供应商或做批量更新，主图字段也不会出现在 Excel 中。库存页 `erp-ui/src/views/inventory/stock/index.vue:921-922` 会优先使用 `imageUrl` 作为商品图片，这会让“主图”成为页面重要字段却无法批量治理。
- 建议：给 `imageUrl` 增加明确的 Excel 字段，例如“主图链接”，并在导入模板中和其他图片字段一起展示；如果主图只允许系统上传生成，应从普通 URL 批量导入中移除并用图片上传/文件 ID 统一维护。导出、导入、模板和页面标签要保持同一字段集，避免主图在批量更新中丢失。

### P3-16 同分类同名称同规格商品存在重复，缺少清理和合并入口

- 现象：商品库中存在同分类、同名称、同规格重复商品，例如“明前龙井 125g/罐”3 条、“普洱熟茶 125g/罐”3 条、“碧螺春 250g”3 条、“武夷大红袍 150g/罐”2 条、“西湖龙井 125g/罐”2 条；部分重复商品还有编码为空的记录。
- 证据：数据库按 `category_id + product_name + spec` 分组查询返回 5 组重复；商品页当前只提供名称/编码/等级/供应商/状态搜索，没有重复检测、相似商品提示或合并入口。后端导入 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:253-267` 会按编码或自然键查找已有商品，但历史数据已经存在重复。
- 影响：采购、销售、库存选择商品时容易选到不同但看起来相同的商品，库存和价格被拆散；缺编码的重复记录尤其难以追踪。
- 建议：先提供重复商品检测报表和人工合并流程；清理后再考虑对同店铺、同分类、同名称、同规格建立唯一约束或导入强校验。

### P3-17 商品删除没有按业务引用提前禁用或显示引用数

- 现象：首行“清香铁观音”已有库存、库存日志、调拨和盘点引用，但商品列表仍显示可点击“删除”，确认框只提示“确认删除商品【WLC-727511 / 清香铁观音】？”，没有提前说明该商品已被引用、实际只能停用。
- 证据：数据库统计该商品有库存 2 条、库存日志 5 条、调拨明细 2 条、调拨发货明细 2 条、库存盘点明细 1 条引用；浏览器实测删除确认框只有编码/名称。前端 `erp-ui/src/views/inventory/product/index.vue:866-875` 统一展示删除确认并在失败后格式化错误；后端 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductServiceImpl.java:91-99` 才按引用抛出“商品已被库存或历史单据引用，不能删除，请停用商品”。
- 影响：管理员要点到删除确认甚至后端错误才知道商品不可删；对已有业务引用商品，更合理的动作是停用或查看引用。
- 建议：商品列表增加引用数/是否可删除状态；对有引用商品禁用删除并提示“已被库存或历史单据引用，请停用”，同时提供“查看引用”入口。

### P3-18 采购草稿行会同时出现“删除草稿”和“取消”

- 现象：采购列表行级按钮对草稿单会显示“编辑”“提交”“删除草稿”，同时 `canCancel` 规则也允许草稿状态显示“取消”；当前数据库没有草稿采购单，所以这是源码推导出的空数据下潜在体验问题。
- 证据：前端 `erp-ui/src/views/inventory/purchase/index.vue:57-62` 对 `status === 'draft'` 显示编辑、提交、删除草稿，同时 `canCancel(scope.row)` 也显示取消；`erp-ui/src/views/inventory/purchase/purchaseActionRules.js:33-38` 中 `canCancelPurchase` 明确允许 `draft` 或 `submitted` 且无质检、无收货的单据取消。后端也区分了取消和删除：`cancelPurchase` 把单据改成 `cancelled`，`deletePurchase` 只允许 `draft/cancelled` 并删除明细。
- 影响：草稿单还没有形成正式采购流程，“删除草稿”和“取消”两个红色动作并列会让用户不清楚哪个是撤销、哪个是作废、哪个会保留记录。
- 建议：草稿状态只保留“编辑 / 提交 / 删除草稿”；已提交且未收货单据再显示“取消”。如果业务要求草稿也可作废，应把按钮文案改成“作废草稿”并解释会保留记录。

### P3-19 公告已读接口没有校验公告是否存在或启用，存在孤儿已读记录风险

- 现象：顶部公告和移动公告会调用已读接口，但后端 `markRead`、`markReadAll` 只要求登录，不校验 `noticeId` 是否存在、是否正常启用或是否已被删除。
- 证据：`erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:108-129` 直接把 `noticeId/ids` 传给已读服务；`erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml:15-18` 和 `:50-55` 使用 `insert ignore into sys_notice_read` 写入；`sys_notice_read` 当前表结构只有主键和 `uk_user_notice(user_id, notice_id)`，没有指向 `sys_notice.notice_id` 的外键。本轮遵守只读审计，未执行构造非法 `noticeId` 的写入验证。
- 影响：客户端异常、旧链接或手工 API 调用可能写入不存在/已删除公告的已读记录；后续阅读用户统计、删除清理和数据审计会出现无来源记录。虽然当前 `sys_notice_read=0`，但流程边界不严谨。
- 建议：`markRead/markReadAll` 写入前校验公告存在且 `status='0'`，批量接口过滤无效 ID 并返回明确提示；数据库层可考虑增加外键或定期清理孤儿已读记录。

### P3-20 用户分配角色页可以提交空角色集合，存在把用户变成无角色账号的风险

- 现象：用户分配角色页的角色表可以全部取消勾选，提交前没有“至少保留一个角色”校验，也没有把移除全部角色解释为禁用或停权流程。
- 证据：前端 `erp-ui/src/views/system/user/authRole.vue:97-111` 直接把选中角色 `join(",")` 后提交；后端 `SysUserServiceImpl.insertUserAuth` 会先删除该用户原有角色，再调用 `insertUserRole` 插入新角色，而 `insertUserRole` 在 `roleIds` 为空时不会插入任何绑定。当前数据库 7 个有效未删除账号中已有 2 个无角色账号；本轮未执行空角色提交。
- 影响：管理员误操作可能让账号失去全部菜单和权限，用户下次登录表现为“没有功能可用”而不是明确的停用状态；排查时也难以区分是账号禁用、组织授权缺失还是角色被清空。
- 建议：前端提交前要求至少保留一个启用角色；如果业务允许“移除全部角色”，应提供单独的停权动作、强确认文案和审计日志。后端也应在事务内保护空角色提交，避免先删后空插入。

### P3-104 用户和角色状态开关没有前端权限控制，只读授权时会出现可点但失败

- 现象：用户管理和角色管理列表中的“状态”开关始终渲染，只要能看到列表就能点击启用/停用确认；开关本身没有 `v-hasPermi` 或禁用态。后端状态修改接口需要编辑权限，所以一旦角色只授予列表/查询、不授予编辑，页面会显示可操作开关，但提交时被后端拒绝。
- 证据：`erp-ui/src/views/system/user/index.vue` 的用户状态列直接渲染 `el-switch` 并调用 `handleStatusChange(scope.row)`，没有 `v-hasPermi="['system:user:edit']"`；`erp-ui/src/views/system/role/index.vue` 的角色状态列同样直接渲染 `el-switch` 并调用 `changeRoleStatus`。后端 `SysUserController.changeStatus()` 标注 `@RequiresPermissions("system:user:edit")`，`SysRoleController.changeStatus()` 标注 `@RequiresPermissions("system:role:edit")`。当前运行态 `system:user:list/edit` 和 `system:role:list/edit` 都授给 `common,zjl`，所以当前角色矩阵未形成“只读但可点”的复现样本；问题来自前后端权限语义不一致。
- 影响：后续按最小权限治理时，给审计员、人事只读或门店管理只读角色开放用户/角色列表，会出现明显的人机不一致：页面暗示可以启停账号或角色，实际保存失败。用户状态、角色状态都是高影响动作，不应以“点了再失败”作为权限反馈。
- 建议：状态开关按编辑权限包裹或禁用，并给只读角色显示纯文本状态；若需要单独控制启停，新增 `system:user:changeStatus`、`system:role:changeStatus` 权限，前端开关和后端接口都使用同一权限码。

### P3-154 用户状态停用没有“不能停用自己”保护，且不会清理被停用用户在线 token

- 现象：用户管理列表支持直接把账号状态切成停用；删除用户接口明确禁止删除当前用户，但状态修改接口没有同样的“当前用户不能停用自己”保护。账号被停用后，登录流程会拒绝新登录，但已有 Redis 登录 token 不会被清理，当前会话仍可能继续调用接口直到 token 过期。
- 证据：前端 `erp-ui/src/views/system/user/index.vue:69-72` 对所有列表行渲染状态 `el-switch`，行操作区才在 `:84-97` 排除 `userId=1`；`handleStatusChange` 在 `:333-342` 只确认“启用/停用”并调用 `changeUserStatus`，没有当前用户判断。API `erp-ui/src/api/system/user.js:61-70` 调用 `PUT /system/user/changeStatus`。后端 `SysUserController.remove` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:331-338` 明确返回“当前用户不能删除”，但 `changeStatus` 在 `:364-372` 只执行 `checkUserAllowed`、`checkUserDataScope` 和 `updateUserStatus`；`SysUserServiceImpl.checkUserAllowed` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:229-245` 只保护超级管理员用户，`updateUserStatus` 在 `:341-343` 只更新状态，mapper 在 `SysUserMapper.xml:254-256` 只写 `sys_user.status/update_time`。登录流程 `SysLoginService.login` 在 `erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java:94-98` 只在新登录时拦截停用账号；运行态请求由 `HeaderInterceptor:35-43` 和 `TokenService.getLoginUser/verifyToken/refreshToken` 从 `login_tokens:*` 读取并刷新缓存，没有重新检查数据库状态。
- 影响：拥有 `system:user:edit` 的非超级管理员可能把自己停用，页面提示停用成功后当前浏览器仍处于登录态，后续再登录才被拦截；管理员停用其他账号时也不能确保该账号立刻下线。多账号多店铺场景下，这会让“账号已停用”的后台状态和真实在线权限窗口不一致，离职、异常账号或误操作停用都难以及时收口。
- 建议：后端状态修改应像删除接口一样拒绝停用当前用户，或要求转交给另一名管理员并立即退出当前会话；当用户状态改为停用时，应按 userId 清理该用户所有 `login_tokens:*` 并在页面提示已清理的在线会话数。状态开关建议拆成独立 `system:user:changeStatus` 权限，确认框展示账号、当前状态、在线会话和影响范围。

### P3-21 系统基础资料删除按钮没有前置引用状态，多个页面显示必然失败的删除操作

- 现象：字典、岗位、部门、菜单管理页面都显示删除按钮，但当前大量数据已经被字典项、用户、子节点或角色引用，点击后才会被后端拒绝。
- 证据：当前 `sys_dict_type` 10 个字典类型全部已有字典项，`sys_user_post` 引用 6 个岗位，6 个部门已有用户且多个部门存在子节点，`sys_role_menu` 覆盖 237 个菜单。浏览器实测字典类型、岗位、部门、菜单列表均显示行内删除；前端 `system/dict/index.vue:151-157`、`system/post/index.vue:112-117`、`system/dept/index.vue:121-128`、`system/menu/index.vue:117-123` 没有按引用状态禁用。后端分别在 `SysDictTypeServiceImpl.deleteDictTypeByIds`、`SysPostServiceImpl.deletePostByIds`、`SysDeptController.remove`、`SysMenuController.remove` 中拦截已分配/有子节点/有用户/已分配角色的数据。
- 影响：管理员需要点删除、确认、再看失败提示才能理解哪些基础资料不能删；系统基础资料是高影响配置，当前交互会诱导尝试危险操作，也不利于维护人员判断“应该停用还是清理引用”。
- 建议：列表增加引用状态或引用数，例如字典项数、用户数、子节点数、角色绑定数；对已引用项禁用删除并显示原因，批量删除时提前过滤并说明。系统参数的内置删除问题继续按 P3-07 单独处理。

### P3-22 岗位和角色含义重叠但缺少一致性提示，用户列表也看不到岗位

- 现象：用户新增/编辑表单同时配置“岗位”和“角色”，岗位名又大量复用角色名，例如店长、驻店经理、运营经理、总经理；但用户列表不展示岗位，也没有校验岗位与角色、组织类型是否匹配。
- 证据：当前 `sys_user_post=18`，存在仓库管理员绑定“程序员”或“店助”岗位、运营经理绑定“总经理”岗位、实习生角色绑定“程序员”岗位等不一致情况。前端 `erp-ui/src/views/system/user/index.vue:160-164` 在用户表单中选择岗位，`erp-ui/src/views/system/user/index.vue:67-79` 的列表列只展示部门、手机号、状态、配置状态和创建时间，不展示岗位；岗位选项来自 `getUser()` 的 `response.posts`，没有按部门类型或角色过滤。
- 影响：管理员无法在用户列表上发现岗位和角色不一致，后续如果岗位参与薪资、考勤、合同或审批，会出现“角色权限是一套、岗位身份又是另一套”的解释成本；多账号批量创建时尤其容易复制错岗位。
- 建议：先明确岗位和角色的边界：角色用于权限，岗位用于人事/薪资或展示。用户列表和详情应展示岗位；用户保存时至少提示岗位与角色/部门类型明显不匹配的情况，或提供“按角色推荐岗位”的默认值。

### P3-143 用户保存岗位只在前端置灰停用项，后端没有校验岗位状态

- 现象：岗位管理支持把岗位状态改为停用；用户新增/编辑表单会把 `status == 1` 的岗位选项置灰，但用户保存接口没有再次校验 `postIds` 对应岗位是否启用。构造请求或批量保存时仍可把停用岗位写入用户岗位关系。
- 证据：前端用户表单 `erp-ui/src/views/system/user/index.vue:162-164` 通过 `:disabled="item.status == 1"` 置灰停用岗位；用户新增/编辑接口 `SysUserController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:272-323` 只校验部门范围、角色范围、账号唯一和密码/联系方式，没有调用岗位状态校验。`SysUserServiceImpl.insertUser/updateUser` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:273-317` 保存用户后直接调用 `insertUserPost`；`insertUserPost` 在 `:422-437` 逐个把 `postIds` 写入 `sys_user_post`，不读取 `sys_post.status`。当前运行态 `sys_post` 10 条均为启用，`disabled_assigned=0`，所以这是流程兜底风险而非存量脏数据。
- 影响：岗位停用在页面上看起来能阻止继续分配，但安全边界实际只在前端。多账号创建、接口调用或后续导入扩展一旦传入停用岗位，会产生“账号启用但人事/审批身份已停用”的不一致；已有绑定停用岗位的用户在保存其他资料时也可能被继续保留。
- 建议：后端新增用户、编辑用户、批量导入和未来批量授权都应校验岗位存在、未删除且启用；前端可保留置灰展示已有停用绑定，但保存时要求迁移到启用岗位或明确走“保留历史岗位”的受控流程。用户详情和列表也应提示已绑定停用岗位。

### P3-144 用户保存和分配角色只在前端禁选停用项，后端没有校验角色状态

- 现象：角色管理支持停用角色；用户新增/编辑弹窗和“分配角色”副页面都会把停用角色禁选，但后端保存用户角色和授权角色时只校验数据范围，不校验 `sys_role.status`。构造请求时仍可把停用角色写入 `sys_user_role`。
- 证据：前端用户表单 `erp-ui/src/views/system/user/index.vue:168-170` 用 `:disabled="item.status == 1"` 禁选停用角色；用户授权角色页 `erp-ui/src/views/system/user/authRole.vue:103-105` 的 `checkSelectable` 也只在前端要求 `row.status === "0"`。后端 `SysUserController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:272-323` 只调用 `roleService.checkRoleDataScope(user.getRoleIds())`，`insertAuthRole` 在 `:416-424` 也只检查目标用户数据范围和角色数据范围；`SysUserServiceImpl.insertUser/updateUser/insertUserAuth` 最终都调用 `insertUserRole`，该方法在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:446-460` 直接批量写入传入 `roleIds`。登录菜单权限会在 `SysPermissionServiceImpl.getMenuPermission` 和 `SysMenuMapper.selectMenuTreeByUserId/selectMenuPermsByUserId` 过滤停用角色，但 `SysPermissionServiceImpl.getRolePermission` 通过 `SysRoleMapper.selectRolePermissionByUserId` 只过滤 `del_flag`，不会过滤 `status`，因此停用角色键仍可能进入前端 `roles` 集合。当前运行态 11 个角色均为启用，`disabled_role_bindings=0`，所以这是后端兜底风险而非现有脏数据。
- 影响：管理员停用角色后，页面语义是该角色不应再分配和生效；但接口仍能写入停用角色，用户列表的角色绑定、授权页面和前端 `hasRole` 判断可能出现“角色已停用但仍被认为拥有该角色身份”的混乱。多账号多店铺里，这会让账号配置状态和权限排查更难解释：菜单权限可能不生效，角色身份却还在响应中出现。
- 建议：用户新增、编辑、分配角色、批量导入和批量授权都应在后端统一校验角色存在、未删除且启用；停用角色时应提示当前绑定用户数，并提供迁移、移除或保留历史绑定的明确处理。`getRolePermission` 和所有前端角色判断来源也应过滤停用角色，避免停用角色键继续影响 `hasRole` 类逻辑。

### P3-23 系统基础配置页直连后会短暂空白或显示“暂无数据”，缺少清晰加载态

- 现象：角色、菜单、部门、岗位、字典、参数等基础页直连时，页面会先出现空白或空表状态，等待较久后才显示真实数据；字典数据副页面也会先出现“暂无数据”，长等待后才恢复 3 条用户性别字典项。
- 证据：2026-06-23 浏览器批量直连 `/system/role`、`/system/menu`、`/system/dept`、`/system/post`、`/system/dict`、`/system/config`，1.8 秒时多个页面 `#app` 文本长度仍为 0；刷新后角色/菜单在 4.5 秒内曾显示“暂无数据”；长等待 7.5-8 秒后才稳定显示角色 11、菜单顶级 7、部门 11、岗位 10、字典 10、参数 8，且无 console error。
- 影响：管理员会误以为基础配置表为空或页面坏了，尤其是字典、参数、菜单这类高风险配置页；如果在数据未稳定前点击刷新、返回或切换标签，会放大“直连白屏/空数据”的误判。
- 建议：所有系统基础页应在接口完成前保持明确加载态，不应先渲染“暂无数据”；路由直连和刷新恢复需要统一等待动态路由、权限和列表接口就绪。必要时记录接口耗时并检查是否有不必要的串行加载。

### P3-24 登录日志“解锁”不展示账号锁定状态，当前无锁定用户时仍可对任意日志解锁

- 现象：登录日志页源码中只要单选任意一条登录日志，就会启用“解锁”按钮；页面没有显示该用户是否已被密码错误策略锁定，也不区分成功登录、失败登录或未锁定账号。
- 证据：当前 Redis 查询没有 `pwd_err_cnt:*` 锁定计数键，但 `sys_logininfor` 中有 501 条历史登录日志、59 条失败日志。前端 `erp-ui/src/views/system/logininfor/index.vue:80-89` 的“解锁”只按 `single` 单选状态启用，`handleUnlock` 直接用所选日志的 `userName` 调用 `/system/logininfor/unlock/{userName}`；后端 `SysLogininforController.unlock` 直接删除 `PWD_ERR_CNT_KEY + userName`，不存在时也会返回成功。
- 影响：管理员可能对未锁定用户执行“解锁成功”，但实际没有任何状态变化；安全运维人员无法从页面判断谁被锁、为什么被锁、还剩几次错误机会。
- 建议：登录日志页或用户列表增加锁定状态/错误次数；“解锁”只在用户存在锁定计数时启用，并把确认文案改为“清除该用户密码错误计数”。无锁定账号应提示“当前未锁定”。

### P3-106 操作日志列表接口返回详情参数，列表权限等同批量详情权限

- 现象：操作日志列表页表格只展示日志编号、模块、类型、人员、IP、状态、时间和耗时，但列表接口实际把请求参数、返回参数和异常信息随每一行一起返回。前端点击“详细”时没有按日志 ID 再查详情，只是把列表行传给详情弹窗展示。
- 证据：运行态 `sys_oper_log=244`，最新样例日志的 `oper_param` 长度包括 228、226、168、60 等，内容覆盖部门、用户、店铺授权和重置密码等操作对象。`SysOperLogMapper.xml` 的 `selectOperLogVo` 同时查询 `oper_param`、`json_result`、`error_msg`，`SysOperlogController.list()` 只要求 `system:operlog:list` 就返回 `selectOperLogList()`。前端 `operlog/index.vue` 的 `getList()` 直接 `this.list = response.rows`，`handleDetail(row)` 只执行 `this.detailRow = row`；`operlog/detail.vue` 再格式化展示 `form.operParam` 和 `form.jsonResult`。服务层和 mapper 虽有 `selectOperLogById`，但 `SysOperlogController` 没有对应 `GET /operlog/{operId}` 详情映射，`erp-ui/src/api/system/operlog.js` 也没有详情查询方法。本轮未触发删除、清空或导出。
- 影响：只要有列表权限，当前页所有操作日志详情参数都会进入浏览器响应和内存，即使用户没有逐条点开详情。后续即使补 `system:operlog:detail` 权限，也无法生效，因为敏感字段已经在列表接口批量返回；这会放大 P2-62 已记录的日志敏感字段问题。
- 建议：操作日志列表接口改为摘要 DTO，只返回列表列需要的字段；新增 `GET /system/operlog/{operId}` 详情接口并使用独立 `system:operlog:detail` 权限，详情接口再按字段级规则返回或脱敏 `operParam/jsonResult/errorMsg`。前端详情按钮应按 `operId` 拉取详情，不再依赖列表行里的完整参数。

### P3-25 调度日志为空时仍显示可点击的“清空”和“导出”

- 现象：`sys_job_log=0` 时，调度日志副页面显示“暂无数据 / 共 0 条”，删除按钮禁用，但“清空”和“导出”按钮仍为启用状态。
- 证据：2026-06-23 浏览器复测 `/monitor/job-log/index/0`，页面表格为空，按钮状态为删除 disabled、清空 enabled、导出 enabled；前端 `erp-ui/src/views/monitor/job/log.vue:75-93` 没有按 `total===0` 禁用清空或导出，`handleClean` 和 `handleExport` 会继续走确认或导出流程。
- 影响：管理员在空数据页仍能触发一个没有实际效果的清空确认或空文件导出，和“删除”按钮的禁用逻辑不一致。
- 建议：当 `total=0` 时禁用清空和导出，或把空导出文案明确为“当前无调度日志可导出”；其他日志页也可复用同样规则。

### P3-145 调度日志清空直接截断正式表，缺少保留期和归档闭环

- 现象：调度日志用于追踪定时任务执行和失败原因，但“清空”动作没有时间范围、任务范围、归档结果或保留期参数，后端直接清空 `sys_job_log` 正式表。当前运行态日志为 0，尚未造成存量证据丢失。
- 证据：`SysJobLogController.clean()` 使用 `DELETE /job/log/clean` 调用 `jobLogService.cleanJobLog()`；`SysJobLogMapper.xml:70-72` 执行 `truncate table sys_job_log`。MySQL 复核 `sys_job_log=0`，没有匹配 `%job%archive%` 或 `%audit_archive%` 的归档表，`sys_config` 中也没有 `job/retention/audit` 相关保留配置。前端 `monitor/job/log.vue:257-264` 仅弹出“是否确认清空所有调度日志数据项？”后调用 `cleanJobLog()`。
- 影响：一旦后续接入考勤同步、库存预警、自动清理或第三方同步等任务，调度日志会成为追责和排障证据。直接 truncate 会把所有任务、所有时间段的成功/失败记录一次性不可逆删除；即使按 P2-108 拆了权限，仍缺少“清空前可归档、清空后可追溯”的生命周期设计。
- 建议：把调度日志清理改为按保留期和时间范围处理，清空前生成归档记录/文件并记录操作者、时间范围、数量、hash 或存储路径；页面显示当前记录数和筛选范围，空态禁用清空。自动清理任务只能处理超过保留期的数据，且必须保留清理审计事件。

### P3-26 工资参数允许明显不合理的上下班时间与标准工时组合

- 现象：OA 工资参数弹窗显示上班时间 `17:21`、下班时间 `17:21`，但日标准工时为 `8.00` 小时；数据库原始值为 `17:21:12` 到 `17:21:15`，实际只有 3 秒间隔。
- 证据：`oa_salary_config` 唯一记录为 `shop_dept_id=104`、`work_start_time=17:21:12`、`work_end_time=17:21:15`、`work_hours_per_day=8.00`；浏览器打开 `/oa/salary` 的“工资参数”弹窗，输入框展示 `17:21`、`17:21`、`8.00`。前端 `erp-ui/src/views/oa/salary/index.vue:140-148` 只做必填校验，后端保存配置也没有校验下班时间晚于上班时间或时间差与标准工时关系。
- 影响：工资计算虽然使用 `workHoursPerDay` 计算加班，但上下班时间会影响用户对考勤规则的理解，也可能被后续考勤/迟到/早退逻辑复用；当前值像测试误填，页面没有提示异常。
- 建议：保存工资参数时校验下班时间必须晚于上班时间，并提示时间跨度与日标准工时明显不一致的情况；已有异常配置在页面标红或弹出修复提示。默认值应保持 `09:00-18:00 / 8 小时` 这类可理解组合。

### P3-27 薪资档位和角色薪资绑定存在测试/异常数据，页面缺少质量提示

- 现象：薪资档位中存在重复或明显异常记录，例如同一方案下“实习生 第一档 西安市”既有 `3300` 又有 `300`，还有 `gradeName=123`、`totalSalary=0`、`实习生 一档 700`、以及“超级管理员 第一档 西安市 3300”。角色薪资绑定也把超级管理员绑定到“茶艺师 第二档 4200”和“实习生 第一档 300”。
- 证据：`sys_salary_scheme_item` 当前 17 条中，`12008` 为实习生第一档总薪资 `300`，`12011` 为实习生 `123` 总薪资 `0`，`12012` 为实习生一档总薪资 `700`，`12007` 为超级管理员岗位档位；`sys_role_salary_scheme` 中 `role_id=1 / 超级管理员` 绑定 `12002 / 茶艺师 第二档 4200` 和 `12008 / 实习生 第一档 300`。薪资配置页只按普通表格展示，不标识重复、异常低薪或角色岗位不匹配。
- 影响：管理员无法分辨哪些薪资档位是正式标准、哪些是测试数据；新增员工绑定弹窗会默认拿第一个员工和第一个档位，容易把异常档位继续绑定到员工或角色，进而影响 OA 工资计算。
- 建议：增加薪资数据质量校验：同方案同岗位同档位同地区不允许重复，工资合计为 0 或明显低于最低标准时要求二次确认，角色绑定应校验角色名/岗位名是否匹配或至少显示风险提示；清理已有测试档位和异常角色绑定。

### P3-28 薪资菜单权限包含导入和重复列表权限，但页面没有对应能力

- 现象：数据库薪资菜单配置了 `system:salary:import` 和重复的 `system:salary:list` 按钮权限，但电脑端薪资配置页没有导入按钮或导入接口调用，权限配置和实际页面能力不一致。
- 证据：`sys_menu` 中 `1185 / 薪资导入 / system:salary:import`、`1187 / 薪资列表 / system:salary:list` 都挂在薪资配置下，角色权限里管理员、普通角色、总经理均持有 `system:salary:import`；前端 `erp-ui/src/views/system/salary/index.vue:29-32` 只显示“新增方案 / 导出”，`erp-ui/src/api/system/salaryConfig.js` 也没有 import 方法。页面菜单 `180` 本身已使用 `system:salary:list`，再配置一个按钮级 `system:salary:list` 会造成权限语义重复。
- 影响：管理员在角色权限树里会看到一个实际不可用的“薪资导入”能力，给角色授权后页面仍没有导入入口；重复列表权限也会让权限审计难以判断到底哪个节点控制页面访问。
- 建议：如果薪资导入属于当前范围，补齐导入按钮、模板、预检和后端接口；否则移除/停用 `system:salary:import`。列表权限应只保留页面级或按钮级一种语义，并同步清理角色默认授权。

### P3-29 清理备份表留在正式业务库，前端空态和数据库内容口径不一致

- 现象：采购、入库、库存和调拨相关正式表在电脑端表现为空或少量当前数据，但数据库里仍有大量 `*_clear_backup_202606*` 历史表保留业务行，且没有前端说明、归档入口或运维说明。
- 证据：运行态 `BossERP_stock_state_75c59ee` 当前也有多张非空清理备份表：`inv_inbound_record_clear_backup_20260607_1640=3`、`inv_inbound_record_clear_backup_20260608_1530=1`、`inv_purchase_order_clear_backup_20260607_1640=3`、`inv_purchase_order_clear_backup_20260608_1530=1`、`inv_purchase_detail_clear_backup_20260607_1640=4`、`inv_purchase_detail_clear_backup_20260608_1530=1`、`inv_stock_check_clear_backup_20260608_1530=1`、`inv_stock_check_detail_clear_backup_20260608_1530=1`、`inv_stock_clear_backup_20260607_1640=3`、`inv_stock_clear_backup_20260608_1530=2`、`inv_stock_log_clear_backup_20260607_1640=3`、`inv_stock_log_clear_backup_20260608_1530=6`、`inv_transfer_order_clear_backup_20260608_1530=2`、`inv_transfer_detail_clear_backup_20260608_1530=2`、`inv_transfer_shipment_clear_backup_20260608_1530=2`、`inv_transfer_shipment_detail_clear_backup_20260608_1530=2`、`inv_transfer_status_log_clear_backup_20260608_1530=5`、`inv_transfer_approval_instance_clear_backup_20260608_1530=2`。样例显示备份采购单 `PO202606060001/PO202606060002/PO202606070001`、库存备份中存在 `total_cost=-100.00` 的异常成本、调拨备份 `TF202606070001/TF202606080001` 和对应状态流水。非运行态 `BossERP` 也曾存在 32 张清理/清空备份表、17 张非空、共 172 条历史业务行，说明该类问题不是单一 schema 偶发。
- 影响：按“所有数据库内容”审计时，业务库并不干净；报表、导出、BI、数据迁移和权限审计容易把清理备份表误认为正式数据，或者相反在前端完全看不到历史单据。备份行还包含成本、门店、仓库、商品和操作记录，长期留在正式 schema 里会增加数据治理风险。
- 建议：把清理备份迁移到单独归档库或统一归档表，建立保留周期、来源说明和访问权限；如果必须在业务库保留，应增加只读归档查询入口或数据字典说明，并清理已发现的异常备份行。

### P3-30 单号序列保留已清理单据的号码，但电脑端无法追溯

- 现象：`inv_number_sequence` 保留了已使用的采购、调拨、调拨发货批次和盘点单号序列，但对应正式单据部分已经不存在，只能从清理备份表追溯。
- 证据：`inv_number_sequence` 当前有 11 条，包含 `PO / 2026-06-06 current_seq=2`、`TF / 2026-06-08 current_seq=1`、`TS / 2026-06-08 current_seq=1` 等；这些日期的正式采购单、调拨单或调拨发货批次数量为 0，但备份表中能看到历史单据。电脑端没有单号序列管理、号码占用说明或归档单据跳转。
- 影响：后续新建单据时号码跳号本身可以接受，但管理员无法区分“正常跳号、已清理历史单据、异常占号、生成失败”四种情况；排查单据缺号或对账时只能查数据库。
- 建议：给单号生成规则增加只读运维页或审计说明，至少展示业务类型、日期、当前序号、最近正式单据、归档/清理状态；清理正式单据时同步写入可追溯的归档索引。

### P3-31 劳动合同存量证据链哈希不完整，详情页无法证明完整链路

- 现象：劳动合同详情页会展示预览哈希、归档哈希、完成证明哈希和事件哈希链，但当前存量合同中早期记录没有完整哈希，事件链也从中途才开始。
- 证据：合同 1、2、3 的 `preview_file_hash`、`archive_file_hash`、`certificate_file_hash` 都为空，仅有 `contract_file_hash`；`oa_labor_contract_event` 的 `event_id=1-9` 中 `document_hash`、`prev_event_hash`、`event_hash` 均为空，`event_id=10` 之后才开始有事件哈希。当前 59 条事件包含 create/send/sign/void/download，但前半段无法和后续 hash 链完整串起来。
- 影响：用户在详情页看到哈希字段，会以为所有合同都具备可校验证据链；实际早期合同无法证明从创建、发送、签署、作废到下载的完整链路，法律证据和内部审计都不够稳。
- 建议：对存量合同做一次证据链迁移或标注：可补算的补齐文件哈希和事件哈希，无法补算的在详情页明确显示“历史记录未生成完整证据链”；不要用同一套 UI 混合展示完整链和不完整链。

### P3-32 劳动合同文件/验 hash 等读操作会写事件，但页面没有提示

- 现象：用户在劳动合同页点击“文件”或执行“验 hash”时，行为看起来像只读查看/验证，但后端都会追加合同事件。
- 证据：当前 `oa_labor_contract_event=59`，其中 `download=28`；`downloadContractFile` 在输出文件后调用 `recordEvent(..., \"download\", ...)`。`verifyContractHash` 命中 hash 后也调用 `recordEvent(..., \"verify\", ...)`。本轮为了不新增事件，只打开了页面和源码/数据库，未点击文件下载或提交 hash 验证。
- 影响：审计事件链会因为普通查看文件和验证动作持续增长，用户没有预期；如果审计链用于证明签署过程，混入大量查看/下载/验证事件会降低主流程可读性。
- 建议：页面在文件打开、下载、hash 验证前提示“该操作将记录审计事件”；详情页把签署主流程事件和访问/验证事件分组展示，避免下载记录淹没创建、发送、签署、作废等关键节点。

### P3-102 劳动合同事件哈希没有覆盖事件摘要和文件动作类型，证据链保护字段不完整

- 现象：劳动合同详情页同时展示事件摘要、操作者、文档哈希、前序哈希和事件哈希，但事件哈希计算没有覆盖全部展示字段。后续事件虽然大多有 `event_hash`，但摘要、操作者名称、备注以及“下载的是 preview-pdf/archive-pdf/certificate”这类动作细节并不在哈希 payload 内。
- 证据：2026-06-24 MySQL 复核运行态 `oa_labor_contract_event=59`，事件分布为 `download=28`、`create=9`、`send=9`、`void=8`、`sign=5`；除 P3-31 已记录的早期空 hash 外，后续下载、签署、作废事件均展示 `event_summary/operator_name/document_hash/prev_event_hash/event_hash/create_time`。前端合同详情表直接展示 `eventSummary`、`operatorName`、`clientIp`、`documentHash`、`prevEventHash`、`eventHash` 和 `createTime`。后端 `OaLaborContractServiceImpl.calculateEventHash()` 只拼接 `contractId/eventType/operatorId/clientIp/userAgent/documentVersion/documentHash/prevEventHash/createTime/requestId`，没有包含 `eventSummary/operatorName/createBy/remark`；文件下载的 `kind` 只出现在 `eventSummary` 里。
- 影响：用户看到“事件哈希”会以为这一行事件详情整体不可篡改，但实际摘要或动作文字被改动时，当前 hash 不会变化。劳动合同证据链用于解释签署、作废、下载和验证行为，若 hash 没覆盖展示字段，后续审计或法律证据核对时只能证明部分技术字段，不能完整证明页面展示的事件语义。
- 建议：重新定义事件哈希规范，把事件摘要、操作者标识和名称、文件动作类型、备注、文件哈希、前序哈希、时间和请求标识全部纳入规范化 payload；详情页应显示“哈希覆盖字段”或在历史事件上标注“旧版哈希字段不完整”。若要保持操作者名称可随用户改名，应至少保存并哈希当时的登录名/用户 ID 快照。

### P3-33 固定资产商品没有独立资产标识，配置页会从普通商品池选择

- 现象：固定资产配置页把“固定资产商品”作为远程商品选择器，但商品主数据没有固定资产类型或资产标识，实际会从全部启用商品里搜索。
- 证据：`inv_product` 当前 162 条全部 `status=0`，表结构没有 `asset_type`、`is_fixed_asset` 等字段；前端 `searchProducts` 调用 `listProduct({ pageNum: 1, pageSize: 20, productName: keyword, status: '0' })`，没有固定资产过滤。样例启用商品包括“清香铁观音”“武夷大红袍”“正岩大红袍”等茶叶 SKU。
- 影响：上线后管理员可能把普通销售商品配置成固定资产，资产额度、维修上报和库存商品混用，后续报表和审计口径会混乱。
- 建议：商品主数据增加固定资产标识、资产分类或专用资产表；固定资产配置页只允许选择符合资产类型且属于当前店铺/组织范围的资产商品。

### P3-105 固定资产配置缺少同店铺同商品唯一约束，重复配置会放大额度

- 现象：固定资产配置页允许同一店铺反复选择同一个商品保存为多条固定资产配置，数据库也没有 `(shop_dept_id, product_id)` 唯一约束或启用状态下的重复校验。额度重建按店铺汇总所有启用配置金额，重复配置会把同一资产金额重复计入年度额度。
- 证据：运行态 `BossERP_stock_state_75c59ee.oa_fixed_asset_config` 当前 0 行；表结构只有 `PRIMARY(config_id)`、`idx_oa_fixed_asset_config_shop(shop_dept_id,status)` 和 `idx_oa_fixed_asset_config_product(product_id)`，没有店铺+商品唯一键。SQL 脚本 `sql/erp_oa_fixed_asset_20260619.sql` 同样只创建这两个普通索引。前端 `fixedAsset/config/index.vue` 的新增/编辑弹窗通过 `listProduct({ status: '0' })` 远程选择商品，保存前只校验店铺、商品、数量、资产金额和比例，不检查当前店铺是否已有同商品配置。后端 `OaFixedAssetServiceImpl.saveConfig()` 只解析店铺、计算资产金额后插入或更新，然后调用 `rebuildQuota()`；`rebuildQuota()` 使用 `configMapper.sumAssetAmountByShop(shopDeptId)` 汇总启用配置的 `asset_amount`。本轮未写入测试数据。
- 影响：管理员在空数据初始化或迁移导入时容易重复配置同一资产商品，页面仍显示多条“固定资产明细”，额度卡片的资产总金额、年度额度和月度额度也会被放大。维修上报可用额度会变得不可信，后续异常批准和额度占用口径都会偏大。
- 建议：保存固定资产配置时按 `shop_dept_id + product_id` 做唯一校验；如果同商品确实有多台设备，应增加资产编号、序列号或资产实例维度，而不是重复同一商品配置。额度重建前也应做重复配置巡检，发现重复时阻断或提示合并。

### P3-34 固定资产维修附件只能手填 URL，缺少上传和预览

- 现象：维修上报弹窗的“图片/附件”字段只是普通输入框，要求用户填写上传后的地址，多个地址用逗号分隔。
- 证据：`fixedAsset/repair/index.vue` 中 `imageUrls` 使用 `<el-input>`，placeholder 为“可填写上传后的图片或附件地址，多个用逗号分隔”；页面没有 `image-upload`、`file-upload`、预览、格式校验或必填提示。
- 影响：门店报修通常需要现场照片或票据，手填 URL 门槛高、容易填错，也无法确认附件是否可打开；维修审核人员很难基于页面完成判断。
- 建议：改为图片/文件上传组件，支持多附件、缩略图预览、删除、大小和格式校验；详情页展示可点击预览的附件列表。

### P3-03 只有一个业务组织时选择页没有自动展开或预选具体门店

- 现象：`qa_audit_62201` 只有 1 个业务组织，登录后选择组织页顶部显示“1 个业务组织可选”，但树默认只展开到顶层组织，具体的“研发部门”需要手动展开公司节点后才能看到并选择。
- 证据：本轮浏览器实测首次登录选择组织页；`erp-ui/src/views/select-shop/index.vue:272-286` 只默认展开第一层节点，只有预览模式才自动选择第一个叶子节点。
- 影响：单门店/单仓库用户仍要展开层级才能进入系统，和“只有 1 个可选业务组织”的事实不匹配。
- 建议：当可选业务组织只有 1 个时，自动展开到该叶子节点并预选；或者直接显示一个“进入研发部门”主按钮。

### P3-04 调拨记录页时间未格式化为本地显示

- 现象：仓库上下文进入 `/cangku/transfer-records`，调拨记录列表的提交时间、审核通过、完成时间、归档时间直接显示 `2026-06-18T04:17:22.000Z` 这类 ISO 字符串。
- 证据：本轮浏览器实测仓库调拨记录页；前端 `erp-ui/src/views/inventory/transfer/records.vue:53-56` 直接绑定 `submittedTime`、`approvedTime`、`receivedTime`、`archivedTime`，没有使用 `parseTime` 或统一时间格式化。
- 影响：和其他列表页的 `yyyy-MM-dd HH:mm:ss` 风格不一致，也会让用户误解时区。
- 建议：列表和详情统一使用本地时间格式化函数，并确认后端返回时区语义。

### P3-05 新增客户表单提示和校验偏弱

- 现象：门店账号进入客户管理并点击“新增客户”，表单中客户名称、客户编码、联系人、联系电话、电子邮箱、地址、备注等输入框基本没有占位提示；校验只要求客户名称。
- 证据：浏览器实测“新增客户”弹窗；前端 `erp-ui/src/views/inventory/customer/index.vue:68-123` 多数 `el-input` 未配置 `placeholder`，`erp-ui/src/views/inventory/customer/index.vue:157-160` 只配置了 `customerName` 必填校验。
- 影响：客户主数据是销售单客户选择器的基础，但当前录入约束弱，容易产生重复客户、无联系方式客户或无编码客户。
- 建议：补充客户编码生成/唯一性提示、手机号/邮箱格式校验、联系人建议必填，并增加空客户主数据时的录入引导。

### P3-06 OA 考勤和劳动合同列表显示原始 ISO 时间

- 现象：`/oa/attendance` 的考勤日期显示 `2026-06-13T16:00:00.000Z`；`/oa/labor-contract` 的发送时间、签署时间显示 `2026-06-14T10:23:24.000Z` 这类原始 ISO 字符串。
- 证据：2026-06-23 浏览器补测上述页面；前端 `erp-ui/src/views/oa/attendance/index.vue:69` 直接绑定 `workDate`，`erp-ui/src/views/oa/laborContract/index.vue:50-51` 直接绑定 `sentTime`、`signedTime`；劳动合同详情弹窗中的事件时间已是 `yyyy-MM-dd HH:mm:ss`，同一页面内列表和详情格式不一致。
- 影响：OA 页面和系统日志、调拨记录一样存在时间展示不统一问题，用户需要自行理解 UTC/本地时间，容易误判考勤日期和合同签署时间。
- 建议：OA 列表统一使用本地时间格式化；日期型字段只显示日期，时间型字段显示 `yyyy-MM-dd HH:mm:ss`，并和后端时区序列化策略保持一致。

### P3-35 考勤组织口径在“店铺”和“仓库”之间不清晰

- 现象：管理员当前组织为 `仓库：仓库` 时，`/oa/attendance` 仍显示“上班打卡”；数据库唯一考勤记录也落在 `shop_dept_id=104`，而 `104` 的 `dept_type=WAREHOUSE`。但表字段、Excel 注解和页面语义仍叫“考勤店铺”。
- 证据：浏览器复测页面顶部显示 `仓库：仓库`，考勤页显示“未打卡 / 上班打卡”；MySQL 查询 `oa_attendance_record.record_id=1` 的 `shop_dept_id=104`、组织名称“仓库”、类型 `WAREHOUSE`；`OaAttendanceRecord.shopDeptName` 的 Excel 名称为“考勤店铺”，`AbstractShopScopeService.requireSelectedShopDept()` 的错误提示为“请先选择店铺或仓库”，但非管理员店铺范围校验只允许 `dept_type='STORE'`。
- 影响：如果仓库人员也需要考勤，页面和导出字段不应继续只叫“店铺”；如果考勤只针对门店，管理员仓库上下文下显示打卡并落库会产生组织口径错误的历史记录。
- 建议：明确考勤是否支持仓库。支持时统一改为“考勤组织”，列表和确认框展示当前组织类型；不支持时前后端在仓库上下文禁用打卡，并迁移或标注已有仓库考勤记录。

### P3-155 考勤卡片按当前组织列表判断“今日已打卡”，但后端按账号全局唯一拦截

- 现象：考勤页左侧“我的考勤”卡片是否显示“上班打卡 / 下班签退”是从当前列表里查今天记录推导出来的；而列表请求会跟随当前选择的门店/仓库过滤。后端新增打卡时却只按 `user_id + work_date` 查重，不包含 `shop_dept_id`。多组织账号如果已在组织 A 打过卡，再切到组织 B，页面可能显示“未打卡”和“上班打卡”按钮，提交后才被后端提示“今日已打卡，请勿重复操作”。
- 证据：前端 `erp-ui/src/views/oa/attendance/index.vue:145-153` 按当前视图加载考勤列表后调用 `findTodayRecord/loadTodayRecord`，`:167-181` 只在返回 rows 中用 `workDate` 字符串前 10 位查找今日记录；这些请求都会由 `request.js:38-40` 自动带上当前 sessionStorage 中的 `Dept-NumId`。后端 `OaAttendanceServiceImpl.checkIn` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaAttendanceServiceImpl.java:41-44` 先解析当前选择组织，再用 `recordMapper.selectByUserIdAndWorkDate(userId, today)` 查重；mapper `OaAttendanceRecordMapper.xml:104-107` 的 SQL 只判断 `r.user_id = #{param1} and r.work_date = #{param2}`，没有 `shop_dept_id`。当前运行库样例显示 `admin` 已有一条 `2026-06-14`、`shop_dept_id=104 / 仓库` 的考勤记录，且 `admin/ry` 均授权 7 个经营组织，具备多组织切换场景。
- 影响：多门店/多仓库管理员或跨店人员会看到一个看似可执行的打卡按钮，实际提交才失败；如果用户不理解“考勤是账号当天全局一次”还是“每个组织一次”，会把失败误认为当前门店没有配置、网络异常或权限问题。页面状态和后端规则不一致，也会影响后续考勤更正、工资计算和门店工时解释。
- 建议：先明确考勤唯一性口径。若按账号每天全局唯一，前端卡片应调用独立的“今日考勤状态”接口或不带组织过滤查询本人今日记录，并在卡片中显示已打卡组织；切换组织时仍展示“今日已在 X 打卡”，不再显示可提交按钮。若按组织每天唯一，则后端查重、唯一约束和工资计算应同时纳入 `shop_dept_id`，并在列表和导出中明确组织维度。

### P3-203 考勤打卡和签退写接口只要求登录，菜单权限无法控制打卡能力

- 现象：电脑端“我的考勤”页面入口和“全部”视图使用 `oa:attendance:list`，但真正产生考勤记录和更新签退时间的 `/oa/attendance/checkIn`、`/oa/attendance/checkOut` 两个写接口只要求登录。当前权限树也没有单独的“上班打卡/下班签退”按钮权限。这样角色管理员即使不给某个角色考勤菜单或考勤查询权限，该登录账号仍可在有组织上下文时直连接口打卡或签退。
- 证据：页面按钮本身没有 `v-hasPermi`，只按 `todayRecord` 显示“上班打卡/下班签退”，见 `erp-ui/src/views/oa/attendance/index.vue:15-31`；前端 API 直接调用 `POST /oa/attendance/checkIn` 和 `POST /oa/attendance/checkOut`，见 `erp-ui/src/api/oa/attendance.js:4-10`。后端 `OaAttendanceController.checkIn/checkOut` 分别只标注 `@RequiresLogin`、`@IdempotentSubmit` 和操作日志，未要求 `oa:attendance:my/list/query` 或独立写权限，见 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaAttendanceController.java:31-50`。当前运行态 `sys_menu` 只有 `oa:attendance:list/query/export` 三个考勤权限，没有打卡/签退权限节点；角色矩阵显示 `common/sxs/cys/dianzhu/zdjl/yyzj/ck` 等角色没有任何 `oa:attendance:*` 权限，但只要能登录并选择组织，接口层没有用权限点区分其是否允许考勤写入。
- 影响：如果产品策略是“所有可登录业务账号都允许考勤”，前端菜单不应把考勤入口只挂在 `oa:attendance:list` 上；如果产品策略是“只有部分岗位/门店角色允许网页端打卡”，现在权限树无法表达和执行这个边界。多账号多店铺上线时，仓库、驻店、实习或临时账号是否能网页端打卡会变成隐式规则，审计人员只能从接口行为推断。
- 建议：明确打卡能力的产品策略并落到权限模型。若所有登录用户都可打卡，应把个人考勤入口改用真实个人权限或默认个人入口，并保留管理列表权限只控制“全部”。若需要按角色控制，应新增并使用 `oa:attendance:checkIn`、`oa:attendance:checkOut` 或统一 `oa:attendance:write`，前端按钮和后端接口都按该权限判断；同时在角色模板中明确哪些岗位可网页端打卡、哪些只能查看或由管理员维护。

### P3-205 字典项删除操作日志会被记录为字典类型删除

- 现象：字典数据副页面删除的是具体字典项，前端确认文案也写“字典项编号”，但后端删除接口的操作日志标题写成了“字典类型”。后续一旦删除字典项，操作日志按系统模块筛选或导出时会把字典项删除混到字典类型删除里，而按“字典数据”又查不到这次删除。
- 证据：字典项表格行内和顶部删除按钮调用 `handleDelete`，确认文案为“是否确认删除字典项编号为...的数据项？”，见 `erp-ui/src/views/system/dict/data.vue:60-69`、`:384-393`；前端 API 删除接口为 `DELETE /system/dict/data/{dictCode}`，见 `erp-ui/src/api/system/dict/data.js:46-51`。后端 `SysDictDataController.remove` 实际删除字典项并调用 `dictDataService.deleteDictDataByIds(dictCodes)`，但注解是 `@Log(title = "字典类型", businessType = BusinessType.DELETE)`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictDataController.java:116-122`。日志切面会直接把注解标题写入 `sys_oper_log.title`，见 `erp-common/erp-common-log/src/main/java/com/erp/common/log/aspect/LogAspect.java:142-148`，操作日志查询也按 `title like` 过滤，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysOperLogMapper.xml:42-44`。运行态当前 `sys_dict_data=29`、`sys_dict_type=10`，`common/zjl` 持有 `system:dict:remove`；当前字典日志样本只有导出记录，尚未出现删除样本，但源码链路已确定删除会错写模块标题。
- 影响：字典类型和字典项属于不同风险面。删除字典类型会影响整个字典，删除字典项只影响某个枚举值；日志标题混淆后，管理员复盘“谁删除了字典类型”时可能看到字典项删除，复盘“谁删除了字典数据”时又漏掉真正的字典项删除。多账号审计和导出留档都会因此失真。
- 建议：把 `SysDictDataController.remove` 的日志标题改为“字典数据”或“字典项数据”，并回归新增、修改、删除、导出四类字典项操作的 `title/business_type/oper_url/oper_param` 是否一致；如保留“字典类型”作为父模块，应在日志里增加子模块字段或业务对象类型，不能只靠标题混用。

### P3-36 报表中心低库存列表有最后入出库列但接口不返回字段

- 现象：低库存预警表格展示“最后入库 / 最后出库”两列，但报表接口没有返回 `lastInTime/lastOutTime`，后续一旦出现预警行，这两列会为空。
- 证据：前端 `erp-ui/src/views/inventory/report/index.vue:120-121` 绑定 `lastInTime`、`lastOutTime`；`inv_stock` 表当前两条记录都有 `last_in_time`，仓库记录还有 `last_out_time`；`InvStockMapper.xml` 的库存列表已 select 并映射这两个字段，但 `InvReportMapper.xml` 的 `selectStockWarningList` 未 select `s.last_in_time/s.last_out_time`，`ReportStockResult` 也没有映射。
- 影响：预警列表本来应帮助判断商品多久未入库、是否近期已出库，但关键时间列会空白，用户无法直接判断补货优先级。
- 建议：报表低库存查询复用库存列表的字段映射，至少返回 `lastInTime/lastOutTime`；如果不打算展示，应从表头移除这两列。

### P3-37 系统参数值缺少类型化校验，错误值会直接影响登录和账号策略

- 现象：参数设置页把所有参数键值都作为普通多行文本录入，只校验必填；后端新增/修改只校验参数键名唯一，未按参数 key 校验布尔值、枚举值、正整数、IP 黑名单格式或初始密码策略。
- 证据：当前 `sys_config` 包含 `sys.account.registerUser=false`、`sys.account.initPasswordModify=1`、`sys.account.passwordValidateDays=0`、`sys.account.chrtype=0`、`sys.login.blackIPList=''` 等会影响注册、改密、密码规则和登录黑名单的参数。前端 `erp-ui/src/views/system/config/index.vue:165-167` 对 `configValue` 使用普通 textarea，`erp-ui/src/views/system/config/index.vue:229-239` 只做必填；后端 `SysConfigController.add/edit` 只调用 `checkConfigKeyUnique`，`SysConfigServiceImpl.updateConfig` 保存后直接写 Redis 缓存。`SysUserController` 和 `SysLoginService` 会在登录、注册、用户新增、改密链路直接读取这些值。
- 影响：管理员误填 `sys.account.chrtype`、`passwordValidateDays` 或 `registerUser` 后，系统可能静默回到默认规则、关闭过期提醒或改变注册行为；页面保存成功并刷新缓存，直到用户登录、注册或重置密码时才暴露结果，排查成本高。
- 建议：为内置参数建立参数 schema：布尔值用开关，密码范围用枚举，过期周期用数字输入并校验范围，黑名单用可解析的 IP/网段列表；后端按 `configKey` 做同样校验并拒绝无效值，保存前展示“影响登录/账号策略”的风险提示。

### P3-38 注册开关没有驱动登录页入口，开启后仍缺少自助注册入口

- 现象：参数 `sys.account.registerUser` 用于控制后端是否允许注册，但登录页没有读取这个参数；当前关闭时入口不显示是因为 `register` 默认就是 `false`，不是因为页面真正同步了数据库配置。
- 证据：数据库当前 `sys.account.registerUser=false`；前端 `erp-ui/src/views/login.vue:203` 把 `register` 初始化为 `false`，模板在 `erp-ui/src/views/login.vue:63`、`erp-ui/src/views/login.vue:154-155` 只按该本地值显示“立即注册”，但 `created()` 和 methods 中没有调用 `/system/config/configKey/sys.account.registerUser` 或其他注册开关接口。后端 `SysUserController.register` 只在 `configService.selectConfigByKey("sys.account.registerUser")` 等于 `true` 时允许注册，`/auth/register` 和 `/auth/passwordPolicy` 本身存在。
- 影响：如果管理员在参数页把自助注册改为 `true`，后端会允许注册，但桌面登录页仍没有“立即注册”入口；如果保持关闭，直达 `/register` 又会显示完整表单并在提交后才失败。注册开关没有形成“登录入口、注册页面、后端允许注册”三处一致的用户体验。
- 建议：登录页加载注册开关并决定是否展示入口；注册页进入时同样读取开关，关闭时显示不可注册说明，开启时展示表单。后端继续作为最终校验，前端只负责让入口语义和配置一致。

### P3-107 主框架皮肤和侧边栏主题参数可编辑，但桌面端不读取 `sys_config`

- 现象：参数设置页当前仍展示并允许修改 `sys.index.skinName` 和 `sys.index.sideTheme`，备注写着皮肤样式和侧边栏主题可选值；但电脑网页端主框架主题实际来自前端静态配置和浏览器本地 `layout-setting`，不会读取数据库参数。管理员修改这两个参数并刷新缓存后，桌面端布局主题不会随数据库配置变化。
- 证据：运行态 `sys_config` 当前包含 `sys.index.skinName=skin-blue`、`sys.index.sideTheme=theme-dark`，两条均为内置参数。前端 `erp-ui/src/settings.js` 把 `sideTheme` 写死为 `theme-dark`；`erp-ui/src/store/modules/settings.js` 初始化时使用 `localStorage.getItem('layout-setting')` 或静态 `sideTheme`，没有调用 `getConfigKey('sys.index.sideTheme')`。`erp-ui/src/api/system/config.js` 虽提供 `getConfigKey(configKey)`，但源码检索 `sys.index.skinName/sys.index.sideTheme/configKey/sys.index` 没有运行调用；`skinName` 在桌面端也没有实际引用。本轮未修改参数值。
- 影响：参数页给平台管理员的预期是“修改主框架皮肤/侧边栏主题会影响系统界面”，实际每个浏览器会按本地设置或打包默认值显示。多账号、多门店验收时，数据库、管理员参数页和用户浏览器看到的主题来源不一致，刷新缓存也不会解释为什么不生效。
- 建议：明确主框架主题是全局参数还是用户个人偏好。若是全局参数，前端启动时应读取 `sys.index.sideTheme/skinName` 并在缓存刷新或重新登录后生效；若是个人偏好，应从系统参数页移除或改为只读说明，把设置保存到用户偏好或本地设置，并在参数备注中说明“不控制桌面端主题”。

### P3-108 用户店铺授权有默认组织字段，但授权页和选择组织页都不使用

- 现象：`sys_user_shop` 有 `is_default` 字段，当前 43 条授权中 24 条为 `Y`、19 条为 `N`，多组织账号也都有一条默认授权；但店铺授权页不能指定默认组织，组织选择页也不会按默认组织预选或排序。默认值只是保存时第一个勾选组织的副产物。
- 证据：运行态 `sys_user_shop` 主键为 `(user_id, dept_id)`，字段包含 `is_default`；admin、ry、12345、qa144822m 等多组织账号的默认组织均为 `103 / 研发部门`，其他授权为 `N`。前端 `system/shop/index.vue` 保存时只把 `checkedKeys` 作为 `Long[]` 传给 `updateUserShop(userId, shopDeptIds)`，没有默认组织控件或参数。后端 `SysUserShopController.save()` 也只接收 `Long[] shopDeptIds`；`SysUserShopServiceImpl.insertUserShopBindings()` 用数组第一个元素写 `isDefault=Y`，其余写 `N`。组织选择页 `select-shop/index.vue` 调 `/system/dept/shop-tree`，只使用本地缓存 `selectedDeptId` 或预览模式首个叶子节点；`SysDeptMapper.selectShopAuthTreeListByShopIds()` 只查 `sys_dept` 并按 `parent_id/order_num` 排序，未读取 `sys_user_shop.is_default`。
- 影响：多门店账号虽然数据库里看似有默认组织，但用户登录后无法依赖这个默认值直接进入常用门店/仓库；管理员也无法明确设置“默认进入哪个组织”。当前默认组织可能只是树顺序或勾选顺序决定，和用户真实常用组织不一致。多账号多店铺验收时，`is_default` 字段会给人一种默认组织已生效的错觉。
- 建议：明确 `is_default` 的业务含义。若要保留，应在店铺授权页提供“设为默认”单选或首选组织控件，后端保存默认组织并保证每个用户最多一个默认；`/system/dept/shop-tree` 或专门接口应返回默认组织，选择组织页在无本地缓存时自动预选/展开默认组织。若不需要默认组织，应删除字段或标注为预留，避免审计和排查误判。

### P3-166 非管理员保存组织授权时可能产生多个默认组织

- 现象：店铺授权支持非管理员只编辑自己可分配范围内的组织，并保留目标用户已有但当前账号不可编辑的授权；但保存可编辑授权时会重新把本次提交的第一条写成 `is_default=Y`。如果被保留的不可编辑授权原本也是默认组织，保存后同一用户可能同时保留旧默认组织，并新增一个新的默认组织。
- 证据：前端 `erp-ui/src/views/system/shop/index.vue:76` 只提示“另有 N 个当前账号不可编辑的授权将保留”，`:256-270` 读取 `editableShopIds/preservedShopIds` 后只勾选可编辑组织；`:298-315` 保存时只提交当前勾选的可编辑组织，并把保留组织拼回列表显示。后端 `SysUserShopServiceImpl.saveUserShops()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java:156-178` 对非管理员只删除 `selectEditableExistingDeptIds()` 返回的可编辑旧授权，然后 `insertUserShopBindings()`；`:194-210` 又把本次插入列表第一项固定写成 `isDefault=Y`。`SysUserShopMapper.xml:66-80` 的删除只覆盖可编辑 `dept_id`，`:89-94` 批量插入新授权；运行态 `sys_user_shop` 表只有主键 `(user_id, dept_id)` 和 `dept_id` 索引，没有 “每个用户最多一个 `is_default=Y`” 的唯一约束。当前运行态 43 条授权暂未出现多个默认组织，`duplicate_default_users=0`，所以本轮未构造写入复现。
- 影响：区域/店长级账号如果未来获得部分组织授权能力，给多组织用户调整自己范围内的门店时，可能无意中让目标用户出现两个默认组织。虽然当前选择组织页还不读取默认值，但一旦按 P3-108 修复默认组织预选，这类历史数据会让“默认进入哪个门店/仓库”变得不确定；报表、移动端和后续自动跳转也可能各自选到不同默认组织。
- 建议：保存组织授权时应把默认组织作为独立字段处理：保留项里已有默认组织时，不要自动给本次新增第一项再写 `Y`；允许修改默认组织时必须在前端明确选择，并在后端一次性把同用户其他授权置为 `N`。数据库层应增加等价唯一约束或保存事务校验，保证每个用户最多一个默认组织。

### P3-167 用户组织授权查询接口向非管理员返回不可编辑组织的具体 ID

- 现象：店铺授权页对非管理员的可编辑范围做了区分，页面文案只提示“另有 N 个当前账号不可编辑的授权将保留”；但详情接口和批量接口仍把目标用户不可编辑的组织 `deptId` 返回给前端。具备 `system:userShop:query` 的部分范围管理员虽然不能编辑这些组织，却能从网络响应看到目标用户还有哪些店铺/仓库授权。
- 证据：`SysUserShopController.getInfo()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java:51-63` 返回 `shopIds/editableShopIds/preservedShopIds/outOfScopeCount`，其中 `preservedShopIds` 是不可编辑授权的具体 ID；`SysUserShopServiceImpl.selectUserShopScope()` 在 `:74-107` 对非管理员逐个判断后把不可编辑 ID 放入 `preservedShopIds`。同 Controller 的 `/batch` 在 `:69-78` 只做目标用户数据范围检查，然后调用 `selectShopDeptIdsByUserIds()`；该服务方法在 `:51-71` 没有接收操作人 ID，也不会裁剪到可编辑范围，而是按 `SysUserShopMapper.selectUserShopsByUserIds` 返回目标用户全部正常 STORE/WAREHOUSE 授权 ID。前端 `system/shop/index.vue:256-266` 只需要 `outOfScopeCount` 展示保留数量，`:382-404` 又只能用当前账号可见 `shopOptions` 生成名称；运行态当前只有 `admin` 和 `ry` 持有 `system:userShop:list/query/edit`，且二者都有 7 个经营组织全量授权，暂没有部分范围管理员样本。
- 影响：多店铺场景下，用户的门店/仓库授权范围本身就是组织边界信息。页面已经选择用“保留 N 个”弱化不可编辑范围，但接口仍暴露具体 ID，会让区域管理员推断目标用户参与了哪些其他门店、仓库或跨区业务；这些 ID 还可与部门接口、日志或历史导出交叉还原为组织名称。若后续拆出区域授权员角色，这会削弱数据最小化和组织隔离。
- 建议：对非管理员的授权查询接口只返回 `editableShopIds` 和 `outOfScopeCount`；确需展示保留项时也只展示数量或脱敏摘要，不返回具体 `deptId`。批量接口应接收当前操作者上下文并按可编辑范围裁剪，或改为返回 `{editableShopIds, outOfScopeCount}` 结构；只有超级管理员或全量组织授权管理员才能获取完整 `shopIds/preservedShopIds`。

### P3-168 代码生成导入保存缺少重复表兜底，可能生成同一库表多份配置

- 现象：代码生成“导入表”弹窗列表会排除已导入的表，但真正的导入保存接口只按表名从 `information_schema.tables` 重新查询并插入 `gen_table/gen_table_column`，没有再次排除已导入表。数据库 `gen_table.table_name` 也没有唯一约束；如果绕过前端、并发点击或接口重放，同一张数据库表可能被重复导入为多份生成配置。
- 证据：导入弹窗 `erp-ui/src/views/tool/gen/importTable.vue:85-91` 只调用列表接口渲染当前可选表；列表 SQL `GenTableMapper.xml:80-98` 有 `table_name NOT IN (select table_name from gen_table)`，但保存用的 `selectDbTableListByNames` 在 `:100-107` 只过滤 `qrtz_%/gen_%` 并按入参表名查询，没有排除 `gen_table` 已有记录。`GenController.importTableSave()` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:109-115` 直接把查询结果交给 `importGenTable()`；`GenTableServiceImpl.importGenTable()` 在 `:160-184` 循环插入 `gen_table` 和列配置，没有查重。运行态 `SHOW CREATE TABLE gen_table` 只有 `PRIMARY KEY (table_id)`，没有 `table_name` 唯一约束；`gen_table_column` 也没有 `(table_id, column_name)` 唯一约束。当前库 `gen_table=0/gen_table_column=0`，所以暂未出现重复配置。
- 影响：一旦系统工具入口恢复或有权限账号直接调用导入接口，重复配置会让代码生成列表出现多条同名表；而预览、同步、自定义路径生成和批量下载都通过 `selectGenTableByName(tableName)` 按表名取配置，重复后可能取到不确定配置或触发查询异常。用户会看到同一数据库表有多份“生成配置”，但无法判断哪一份是最新、哪一份会被生成、同步或删除。
- 建议：导入保存接口必须以后端和数据库为准做幂等校验：`gen_table.table_name` 增加唯一约束或保存前加锁查重，`gen_table_column` 增加 `(table_id, column_name)` 约束；`selectDbTableListByNames` 也要排除已导入表并对重复入参去重。重复导入时应返回“已存在并跳转编辑/同步”的明确提示，而不是插入第二份配置；生成、同步、预览接口也应按 `tableId` 定位配置，避免按表名在重复数据中取一条。

### P3-169 在线用户搜索框是自由输入，但后端只做完全相等匹配

- 现象：在线用户页提供“登录地址”和“用户名称”两个搜索框，交互上像普通后台列表的查询输入；但后端只做完全相等匹配，不支持 IP 片段、用户名片段或大小写/空格容错。运维人员输入 `127`、`admin` 的一部分、昵称或常见模糊条件时会得到空结果，只能知道完整 IP 和完整登录账号才能查到会话。
- 证据：前端 `erp-ui/src/views/monitor/online/index.vue:5-18` 两个输入框 placeholder 分别为“请输入登录地址”“请输入用户名称”，`handleQuery()` 在 `:101-104` 直接把输入条件传给 `/system/online/list`。后端 `SysUserOnlineController.list()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserOnlineController.java:43-69` 扫描 Redis token 后调用 `selectOnlineByIpaddr/selectOnlineByUserName/selectOnlineByInfo`；服务实现 `SysUserOnlineServiceImpl` 在 `:25-63` 对 `ipaddr` 和 `userName` 都使用 `StringUtils.equals(...)` 完全相等判断。当前 Redis 只读快照有 16 个 `login_tokens:*`，均为 `userName=admin`、`ipaddr=127.0.0.1`；本轮直接取 Redis key 作为网关 Bearer token 调用列表接口返回“令牌已过期或验证不正确”，因此未把该调用作为接口返回证据，结论以源码和 Redis 样本为准。
- 影响：在线用户本来是排查门店共用电脑、重复登录和误操作会话的工具；实际搜索过窄会让管理员很难按 IP 段、账号前缀或可记住的用户名片段定位会话。配合 P3-96 的全量 Redis 扫描和 P2-41 的强退确认不足，用户可能在一堆相同账号、相同 IP 的会话里反复翻页查找，误强退概率更高。
- 建议：明确搜索语义并让前后端一致。若按模糊查询，应对 IP 和用户名做 `contains`/前缀匹配，并在后端完成筛选和分页；若坚持精确匹配，placeholder 要改为“完整登录地址 / 完整登录账号”，并在空结果里提示“当前仅支持精确匹配”。更适合运维的做法是增加账号下拉、IP 前缀、最近登录时间和当前会话筛选。

### P3-170 定时任务状态开关缺少前端权限控制，只读任务角色会看到可点开关

- 现象：定时任务列表的“状态”列直接渲染启用/停用开关，没有按 `monitor:job:changeStatus` 做前端隐藏或禁用；而后端状态修改接口要求该权限。如果后续拆出只读任务查看角色，页面会出现“看得到、点得动、提交后被后端拒绝”的开关。
- 证据：前端 `erp-ui/src/views/monitor/job/index.vue:114-121` 的 `el-switch` 只有 `v-model`、`active-value/inactive-value` 和 `@change`，没有 `v-hasPermi` 或禁用判断；`handleStatusChange()` 在 `:383-392` 弹确认后直接调用 `changeJobStatus(row.jobId, row.status)`，失败才把状态切回去。后端 `SysJobController.changeStatus()` 在 `erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java:153-160` 使用 `@RequiresPermissions("monitor:job:changeStatus")`。当前运行态 `common / 普通角色` 和 `zjl / 总经理` 同时持有 `monitor:job:list/query/changeStatus`，暂没有“只有列表没有启停”的角色样本，所以本轮按潜在只读角色体验和权限一致性记录。
- 影响：定时任务启停影响后台自动任务，是比普通列表状态更高风险的操作。当前表格里的新增、修改、删除、导出等按钮会按权限显示，但状态开关不跟随同一权限模型，容易在权限矩阵扩展时产生误导；用户点击后再被 403/失败回滚，也会造成任务状态是否已经改变的不确定感。
- 建议：定时任务状态开关应按 `monitor:job:changeStatus` 显示或禁用；无权限用户展示只读状态标签。保留后端权限作为最终兜底，同时在启停确认框展示任务名称、调用目标、当前状态和将要变更到的状态，避免把高风险后台任务操作做成普通列表开关。

### P3-171 日志状态共用 `sys_common_status`，页面筛选和导出状态文案不一致

- 现象：登录日志、操作日志和调度日志页面都使用 `sys_common_status` 作为状态筛选和标签字典。当前运行态该字典是“0=成功、1=失败”，适合登录日志；但操作日志导出定义是“0=正常、1=异常”，调度日志导出定义是“0=正常、1=失败”。同一条操作日志在页面上会按“成功/失败”理解，导出后又按“正常/异常”理解。
- 证据：运行态 MySQL `sys_dict_data` 中 `sys_common_status` 为 `成功/失败`，`sys_normal_disable` 才是 `正常/停用`；`sys_logininfor` 当前 `status=0` 442 条、`status=1` 59 条，消息分别包含“登录成功/退出成功”和“密码输入错误”等。登录日志前端 `erp-ui/src/views/system/logininfor/index.vue:23-35`、`:111-114` 使用 `sys_common_status`，与 `SysLogininfor.java:26-28` 的 `0=成功,1=失败` 一致；操作日志前端 `erp-ui/src/views/system/operlog/index.vue:47-60`、`:131-134` 也使用同一字典，但 `SysOperLog.java:69-71` 导出转换为 `0=正常,1=异常`，当前 `sys_oper_log` 为 `status=0` 221 条、`status=1` 23 条；调度日志前端 `erp-ui/src/views/monitor/job/log.vue:29-42`、`:120-123` 继续使用同一字典，而 `SysJobLog.java:39-41` 导出转换为 `0=正常,1=失败`。当前 `sys_job_log=0`，调度日志问题以源码口径记录。
- 影响：管理员在页面筛选“失败”操作日志时，导出的同一批数据会显示为“异常”；页面、导出、Excel 二次分析和审计口径不一致，会让日志复盘人员难以确认是业务操作失败、系统异常，还是普通成功状态。后续如果字典管理员按登录日志语义修改 `sys_common_status`，还会连带改变操作日志和调度日志页面标签。
- 建议：日志状态不要复用一个含糊的通用字典。拆成 `sys_login_status=成功/失败`、`sys_oper_status=正常/异常`、`sys_job_log_status=正常/失败`，或统一修改实体导出转换、页面字典和数据库字典，保证同一状态值在页面、筛选、详情和导出中使用同一套文案。字典管理页应标注这些系统内置字典的引用范围，避免误改影响多处日志。

### P3-172 登录日志解锁用 GET 拼接用户名，状态变更接口语义和账号名兼容性不足

- 现象：登录日志“解锁”会变更 Redis 中的密码错误计数，但前端把用户名直接拼到 URL，并用 GET 请求调用 `/system/logininfor/unlock/{userName}`。该动作不是只读查询，却使用了可被浏览器、代理和日志系统当作普通读取的 GET；如果账号名包含空格、斜杠、问号、井号等 URL 特殊字符，还可能出现请求路径截断或路由不匹配。
- 证据：前端 `erp-ui/src/views/system/logininfor/index.vue:232-239` 在确认后调用 `unlockLogininfor(username)`，`erp-ui/src/api/system/logininfor.js:20-25` 直接拼接 `url: '/system/logininfor/unlock/' + userName` 且 `method: 'get'`，没有 `encodeURIComponent` 或请求体。后端 `SysLogininforController.unlock()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLogininforController.java:77-83` 使用 `@GetMapping("/unlock/{userName}")`，执行 `redisService.deleteObject(CacheConstants.PWD_ERR_CNT_KEY + userName)` 后直接返回成功。用户新增和注册链路只校验用户名长度与唯一性：用户页规则 `erp-ui/src/views/system/user/index.vue:268-271`、注册页 `erp-ui/src/views/register.vue:95-98` 仅限制 2 到 20 位；登录/注册服务 `SysLoginService.java:65-70`、`:165-169` 也只按长度判断；`SysUser.userName` 没有格式注解。当前运行态账号名均为字母、数字或下划线，`sys_oper_log` 中也没有“账户解锁”记录，因此本轮未做写接口复现。
- 影响：账号安全运维动作容易被误认为只读访问，审计、网关、缓存和重放防护的语义都不清楚。多账号批量导入或开放注册后，一旦出现带 URL 特殊字符的账号，登录失败日志可以记录该账号，但从登录日志页解锁可能失败或解锁错误目标；即使当前账号名都较简单，也会把未来账号命名规则和安全接口耦合在路径字符串上。
- 建议：解锁改为 `POST /system/logininfor/unlock` 或 `DELETE /system/logininfor/password-error-count`，请求体传 `{ userName }` 或更稳妥地传稳定 `userId`，前端用请求体而不是路径拼接；后端返回“已清除错误计数 / 当前未锁定 / 用户不存在”的明确状态，并把账号名、操作者、锁定计数和结果写入操作日志。若产品要求账号只能用字母数字下划线，应在用户新增、导入、注册和后端统一校验，而不是让 URL 路由隐式决定。

### P3-173 日志导出确认只展示部分筛选条件，实际导出范围可能比用户看到的更窄或更宽

- 现象：登录日志、操作日志和调度日志都已接入统一导出确认，但确认框的“筛选条件”只展示一个主字段和日期。用户如果同时筛了 IP、状态、操作类型、操作人、任务组或执行状态，确认框不会展示这些条件；实际导出请求却会携带完整 `queryParams`。这会让导出前最后一步无法准确核对文件范围。
- 证据：登录日志页面有登录地址、用户名称、状态、登录时间筛选，见 `erp-ui/src/views/system/logininfor/index.vue:5-48`；但 `handleExport()` 在 `:241-258` 传给 `confirmExportAction` 的 `filterLabel` 只包含“登录账号”和日期，实际下载参数却是 `this.addDateRange({ ...this.queryParams }, this.dateRange)`，后端 `SysLogininforController.export()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLogininforController.java:50-57` 会按 mapper 中的 `ipaddr/status/userName/beginTime/endTime` 查询。操作日志页面有操作地址、系统模块、操作人员、类型、状态、操作时间筛选，见 `erp-ui/src/views/system/operlog/index.vue:5-72`；但导出确认 `:277-294` 只展示“系统模块”和日期，后端按 `operIp/title/businessType/status/operName/beginTime/endTime` 查询。调度日志有任务名称、任务组名、执行状态、执行时间筛选，见 `erp-ui/src/views/monitor/job/log.vue:5-53`；导出确认 `:266-283` 只展示“任务名称”和日期，后端按 `jobName/jobGroup/status/invokeTarget/beginTime/endTime` 查询。运行态样本中登录日志有 5 个 IP、成功/失败两种状态，操作日志有 6 类业务类型和正常/异常两种状态，说明这些被遗漏的筛选项不是空字段。
- 影响：导出确认本来是防止误导出、误外发的最后检查点；现在用户可能看到“登录账号：全部；日期：全部”，但实际还筛了失败状态或某个 IP，也可能看到“系统模块：用户管理”，却没有意识到还筛了异常状态、授权类型或操作人员。审计人员后续拿到 Excel 时，也难以从确认动作判断用户当时真实导出了哪个范围。
- 建议：日志类导出确认应由每个页面构造完整筛选摘要：登录日志展示登录地址、账号、状态、时间；操作日志展示 IP、模块、操作人、类型、状态、时间；调度日志展示任务名、任务组、执行状态、时间。更稳妥的做法是在确认框中展示即将导出的记录数和主要筛选条件，导出操作日志里也保存筛选摘要，方便追溯导出范围。

### P3-174 日志删除确认只展示编号，缺少被删日志的主体和时间摘要

- 现象：登录日志、操作日志和调度日志的删除确认都只展示选中的日志编号，不展示用户、IP、模块、操作人、任务名、状态或时间。用户在多选删除审计证据前，只能看到一串 ID，无法核对这批日志到底对应哪些登录、操作或任务执行记录。
- 证据：登录日志表格本身有访问编号、用户名称、地址、登录状态、描述、访问时间，见 `erp-ui/src/views/system/logininfor/index.vue:106-121`，但 `handleSelectionChange()` 只保存 `infoId`，`handleDelete()` 在 `:200-217` 只弹“是否确认删除访问编号为...”后调用 `delLogininfor(infoIds)`；后端 `SysLogininforController.remove()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysLogininforController.java:60-65` 按 ID 数组物理删除。操作日志表格有日志编号、模块、类型、人员、IP、状态、时间和耗时，见 `erp-ui/src/views/system/operlog/index.vue:119-145`，但 `handleDelete()` 在 `:247-263` 只展示日志编号；后端 `SysOperlogController.remove()` 在 `:55-60` 也是按 ID 物理删除。调度日志表格有日志编号、任务名称、任务组、调用目标、日志信息、执行状态和执行时间，见 `erp-ui/src/views/monitor/job/log.vue:108-129`，但 `handleDelete()` 在 `:235-251` 只展示调度日志编号；后端 `SysJobLogController.remove()` 在 `erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobLogController.java:69-77` 按 ID 删除。运行态当前 `sys_logininfor=501`、`sys_oper_log=244`，最新样例分别是 `qa144822s / 127.0.0.1 / 登录成功 / 2026-06-25 09:47:27` 和 `部门管理 / admin / /dept / 2026-06-23 11:23:58`；`sys_job_log=0`，调度日志按源码模型记录。
- 影响：日志删除是破坏审计证据的动作，编号对普通管理员几乎没有业务含义。多选删除时，用户容易误删相邻或跨页选中的日志；删除后再靠操作日志追溯也只能看到 ID 数组，缺少被删日志的关键摘要，难以判断是否误删了某个登录失败、授权变更或任务异常记录。
- 建议：删除确认框至少展示删除数量和前几条摘要：登录日志显示账号、IP、状态、访问时间；操作日志显示模块、操作人、URL、状态、操作时间；调度日志显示任务名、任务组、状态、执行时间。超过 N 条时要求输入“确认删除”或二次确认，并把被删日志的摘要、数量和筛选/选中来源写入保留审计记录；日志类删除最好改为软删除或归档删除，而不是直接物理删除正式表。

### P3-175 系统基础页导出确认只展示一个筛选项，遗漏状态、编码和配置状态

- 现象：用户、角色、岗位、参数、字典类型和字典项页面都已接入统一导出确认，但确认框里的“筛选条件”只展示一个主字段，部分页面再加日期；实际导出请求会携带完整 `queryParams`。管理员如果筛了手机号、用户状态、配置状态、权限字符、岗位编码、参数键名、系统内置、字典类型、字典标签或状态，最后确认框不会展示这些条件。
- 证据：用户页搜索字段包含用户名称、手机号、状态、配置状态和创建时间，见 `erp-ui/src/views/system/user/index.vue:8-27`，但导出确认 `:528-543` 只展示“用户名称”和日期，实际下载参数是 `this.addDateRange({ ...this.queryParams }, this.dateRange)`；后端 `SysUserController.export()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:96-104` 调 `selectUserList(user)`，mapper 会按 `userName/status/phonenumber/postId/setupStatus/deptId/beginTime/endTime` 过滤，见 `SysUserMapper.xml:92-124`。角色页有角色名称、权限字符、状态和创建时间筛选，见 `erp-ui/src/views/system/role/index.vue:5-48`，导出确认 `:621-636` 只展示角色名称和日期，后端 `SysRoleController.export()` `:63-70` 与 `SysRoleMapper.xml:39-52` 会按 `roleName/status/roleKey/beginTime/endTime` 过滤。岗位页有岗位编码、岗位名称和状态筛选，见 `erp-ui/src/views/system/post/index.vue:5-30`，导出确认 `:309-321` 只展示岗位名称，后端 `SysPostController.export()` `:51-58` 与 `SysPostMapper.xml:28-35` 会按 `postCode/status/postName` 过滤。参数页、字典类型页和字典项页同样分别只展示参数名称、字典名称、字典类型，但后端和 mapper 会按参数键名/系统内置/日期、字典类型/状态/日期、字典标签/状态继续过滤，见 `erp-ui/src/views/system/config/index.vue:5-42`、`:337-352`，`system/dict/index.vue:5-48`、`:368-383`，`system/dict/data.vue:4-30`、`:396-408`。运行态当前 `sys_user=25`、`sys_role=11`、`sys_post=10`、`sys_config=8`、`sys_dict_type=10`、`sys_dict_data=29`，其中用户配置状态已有 `missingShopScope=1` 样本，说明这些筛选并非纯装饰字段。
- 影响：系统基础资料导出常用于账号治理、角色盘点、岗位/参数/字典迁移。确认框如果只展示“用户名称：全部；日期：全部”，用户看不到实际还筛了“未授权店铺/仓库”或某个手机号；岗位、参数、字典导出也可能因为隐藏的编码、状态或类型筛选导出比预期更窄。后续排查 Excel 来源时，操作日志只记录导出动作，无法从用户看到的确认文案还原真实范围。
- 建议：系统基础页导出确认要由每个页面构造完整筛选摘要。用户页展示用户名称、手机号、状态、配置状态、部门和创建时间；角色页展示角色名称、权限字符、状态、创建时间；岗位页展示岗位编码、岗位名称、状态；参数页展示参数名称、参数键名、系统内置、创建时间；字典类型和字典项展示名称、类型、标签和状态。导出操作日志也应保存筛选摘要和导出记录数，便于审计追溯。

### P3-176 部门负责人和联系方式只在编辑弹窗可见，组织责任台账不可直接核对

- 现象：部门管理表单维护了负责人、联系电话和邮箱，但电脑端组织树列表、搜索条件和删除/排序等操作区都不展示这些字段，也不能按负责人或电话检索。多店铺管理员想核对“某门店/仓库当前负责人和联系方式”时，只能逐个点进修改弹窗查看，列表页无法直接完成组织责任盘点。
- 证据：部门页搜索区只有部门名称、状态和类型，见 `erp-ui/src/views/system/dept/index.vue:5-35`；列表列只有部门名称、类型、排序、状态、创建时间和操作，见 `:75-131`；新增/修改弹窗才出现负责人、联系电话、邮箱输入框，见 `:183-196`。前端校验只对邮箱格式和手机号格式做限制，负责人没有用户选择器或存在性校验，见 `:253-279`。后端 `SysDeptController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:100-136` 只校验部门名、自身父级和停用子部门，`SysDeptMapper.xml:187-228` 直接保存 `leader/phone/email` 字符串。运行态当前 `sys_dept` 有效组织 11 个，其中 10 个填写 `leader=ERP/phone=15888888888/email=ry@qq.com`，1 个为空；`leader` 与 `sys_user.user_name` 无匹配、与 `nick_name` 有 10 条匹配。P2-58 已单独记录 OA 采购审批使用负责人导致任务可能无人可办，本项记录的是电脑端组织台账展示和查询闭环不完整。
- 影响：负责人和联系方式看起来是组织主数据的一部分，但电脑端列表无法直接审阅、筛选或导出这些责任信息。多账号多店铺交接、门店联系人盘点、仓库责任核对时，管理员容易遗漏空负责人、过期手机号或同一联系人被复制到多个组织的问题；如果后续继续把 `leader` 用作审批、通知或运营责任来源，台账质量也很难在页面上提前发现。
- 建议：部门管理列表至少展示负责人和联系电话，搜索区支持负责人/电话筛选，导出也应包含负责人、电话、邮箱和组织类型。若负责人会参与流程，应改为绑定 `sys_user.user_id/user_name` 的用户选择器，并校验该用户启用且具备对应组织授权；若只是备注联系人，页面文案应明确为“联系人”，避免用户误以为它就是审批责任人。

### P3-177 系统基础资料备注字段可维护，但导出台账普遍不包含备注

- 现象：角色、岗位、字典类型、字典项和参数设置都支持在新增/编辑弹窗维护备注，且运行态这些基础资料多数已经有备注；但导出实体没有给 `remark` 加 Excel 列。角色和岗位列表还不展示备注，用户只能进入编辑弹窗确认备注内容；字典和参数虽然列表显示备注，但导出的台账仍缺备注。
- 证据：角色列表列为角色编号、名称、权限字符、排序、状态、创建时间和操作，不含备注，见 `erp-ui/src/views/system/role/index.vue:104-128`，但角色弹窗有备注输入框 `:208-210`；岗位列表同样不含备注，见 `erp-ui/src/views/system/post/index.vue:86-105`，弹窗有备注输入框 `:153-155`。字典类型、字典项和参数列表分别展示备注列，见 `erp-ui/src/views/system/dict/index.vue:114-130`、`system/dict/data.vue:93-110`、`system/config/index.vue:109-121`，对应弹窗也都可编辑备注。导出都走 `ExcelUtil`：角色、岗位、字典类型、字典项和参数实体的 `@Excel` 注解只覆盖主键、名称、编码、状态等字段，`BaseEntity.remark` 只出现在 `toString()`，没有导出列，见 `SysRole.java:23-50`、`:224-240`，`SysPost.java:21-39`、`:109-122`，`SysDictType.java:21-35`、`:85-94`，`SysDictData.java:21-53`、`:165-173`，`SysConfig.java:20-38`、`:100-108`。运行态当前非空备注包括 `sys_role=10/11`、`sys_post=8/10`、`sys_dict_type=10/10`、`sys_dict_data=29/29`、`sys_config=8/8`，说明备注不是空字段。
- 影响：备注通常承载角色用途、标准门店岗位说明、字典含义、参数取值说明等治理信息。管理员在线上页面看过备注后导出台账，Excel 却只剩名称、编码和状态，会丢失“为什么这样配置”的解释；角色/岗位列表不显示备注还会让多账号治理时无法直接区分标准角色、测试角色和临时角色。后续做权限盘点、岗位清理、字典迁移或参数审计时，需要重新回到页面逐条打开编辑弹窗或查数据库。
- 建议：系统基础资料导出统一补充备注列，至少覆盖角色、岗位、字典类型、字典项和参数。角色、岗位列表也应增加备注列或详情抽屉，长文本用 tooltip 展示；如果备注会包含敏感运维说明，则按导出权限或字段级权限做脱敏，而不是直接从所有导出台账中删除。

### P3-178 代码生成元信息只校验必填，非法包名和业务名可进入生成路径、权限码和菜单 SQL

- 现象：代码生成编辑页允许维护实体类名、包路径、模块名、业务名和功能名，但前端只做必填校验，后端也只有 `@NotBlank` 和树表/主子表特定字段校验，没有校验 Java 包名、类名、模块名、业务名、权限前缀和生成文件名的安全格式。即使当前 `gen_table=0`，业务角色已经持有导入、编辑、预览和生成代码权限；一旦导入任意表，保存异常元信息后，预览和 zip 下载都会按这些值生成代码文件、Vue 路径、菜单 SQL 和权限码。
- 证据：基础信息表单 `erp-ui/src/views/tool/gen/basicInfoForm.vue:15-21` 可编辑 `className/functionAuthor`，规则 `:43-55` 只要求必填；生成信息表单 `erp-ui/src/views/tool/gen/genInfoForm.vue:25-68` 可编辑 `packageName/moduleName/businessName/functionName`，规则 `:272-287` 同样只要求必填。提交时 `editTable.vue:176-197` 合并表单和字段后直接调用 `updateGenTable`；后端 `GenController.editSave` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:121-128` 只调用 `validateEdit`，而 `GenTableServiceImpl.validateEdit` `:430-460` 只检查树表和主子表字段。实体 `GenTable.java:37-61` 对这些字段只有 `@NotBlank`，没有 `@Pattern/@Size`；`GenTableMapper.xml:177-200` 会直接保存 `class_name/package_name/module_name/business_name/function_name/gen_path`。生成阶段 `VelocityUtils.prepareContext` `:43-68` 把这些值放入模板上下文，`getFileName` `:199-276` 用 `packageName/moduleName/businessName/className` 拼出 Java、MyBatis、Vue 和 SQL 文件名，`getPermissionPrefix` `:366-368` 用 `moduleName:businessName` 拼权限前缀；模板 `vm/sql/sql.vm:3-22` 还会把 `functionName/moduleName/businessName/permissionPrefix` 写入菜单 SQL。运行态当前 `gen_table=0/gen_table_column=0`，但 `common / 普通角色` 和 `zjl / 总经理` 均持有 `tool:gen:edit/import/preview/code`。
- 影响：这不是单纯的“自定义路径”问题；走 zip 下载时也会产生非法目录、非法 Java 包名、不可编译类名、异常 Vue 路径、异常权限码或不可直接执行的菜单 SQL。多账号场景下，普通业务角色如果误入或直接调用生成器，可以把一份看似成功下载的代码包变成不可用或污染权限命名的产物；后续开发人员再导入这些 SQL/代码时，问题来源难以从页面上识别。
- 建议：代码生成保存接口增加服务端格式校验：`className` 必须是合法 Java 类名，`packageName` 必须是合法 Java 包名，`moduleName/businessName` 仅允许受控的英文、数字、下划线或短横线并禁止 `.`、`/`、`\`、空格和路径穿越片段，`functionName` 限制长度和 SQL 单引号等特殊字符。预览和生成前再次校验并展示具体错误；若系统工具不交付给业务账号，应先移除 `common/zjl` 的 `tool:gen:*` 权限。

### P3-228 代码生成删除确认只展示表编号，缺少表名和生成配置摘要

- 现象：代码生成列表恢复可用后，删除生成配置的确认框只展示 `tableId` 编号，不展示表名、表描述、实体类、生成方式、生成路径或将删除的字段配置数量。用户在删除前无法确认这条配置对应哪张业务表，也看不到删除会同步清掉 `gen_table_column` 字段配置。
- 证据：前端 `erp-ui/src/views/tool/gen/index.vue:72-81` 顶部“删除”和 `:116-122` 行内“删除”都调用 `handleDelete`；`handleDelete()` 在 `:326-334` 只取 `row.tableId || this.ids`，确认文案为“是否确认删除表编号为 ... 的数据项？”，没有使用 `tableName/tableComment/className/genType/genPath`。后端 `GenController.remove()` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:131-140` 只按 `tableIds` 调用 `deleteGenTableByIds`；`GenTableServiceImpl.deleteGenTableByIds()` 在 `:147-153` 直接删除 `gen_table` 并同步删除 `gen_table_column`，`GenTableMapper.xml:203-208` 和 `GenTableColumnMapper` 也都是按 ID 物理删除。当前运行态 `gen_table=0/gen_table_column=0`，所以没有存量生成配置被误删；但 `common / 普通角色` 与 `zjl / 总经理` 当前仍持有 `tool:gen:remove`。
- 影响：代码生成配置不是普通列表记录，它会影响预览、同步数据库、zip 下载、自定义路径生成和菜单 SQL。若后续恢复系统工具或已有账号直接进入生成器，用户删除时只看到数字 ID，无法判断是否删错了某张正式业务表的生成配置；删除后字段配置一并物理清掉，后续只能重新导入或从备份/日志倒查。
- 建议：代码生成删除确认应展示删除数量和前几条摘要：表名、表描述、实体类、生成方式、生成路径和字段配置数量；批量删除超过阈值时要求二次确认。后端删除操作日志应记录被删配置摘要，必要时改为软删除或提供恢复入口。若系统工具不交付，应继续停用入口并移除业务角色的 `tool:gen:remove`。

### P3-253 代码生成顶部“生成”和行内“生成代码”对自定义路径配置行为不一致

- 现象：代码生成列表行内“生成代码”会读取当前行 `genType`，当配置为自定义路径时调用 `/code/gen/genCode/{tableName}`；但顶部工具栏“生成”按钮没有行对象，只拿选中的 `tableNames` 数组，因此始终走 `/code/gen/batchGenCode?tables=...` 的 zip 下载分支。当前运行态 `gen_table=0` 暂不触发，但一旦导入生成配置，同一条 `genType=1` 配置从行内入口和顶部入口点击会出现不同结果。
- 证据：顶部按钮 `erp-ui/src/views/tool/gen/index.vue:40-47` 直接绑定 `@click="handleGenTable"`，行内按钮 `:124-131` 绑定 `@click="handleGenTable(scope.row)"`。`handleGenTable()` 在 `:252-265` 通过 `row.tableName || this.tableNames` 取表名，只有 `row.genType === "1"` 才调用 `genCode(row.tableName)`，否则统一下载 `batchGenCode` zip；顶部按钮传入的是点击事件或无业务行对象，不包含 `genType/genPath`。后端 `GenController.genCode()` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:169-179` 是自定义路径写入入口，`batchGenCode()` 在 `:197-205` 是 zip 下载入口。MySQL 当前 `gen_table=0/gen_table_column=0`，`tool:gen:code` 和 `tool:gen:edit` 权限节点仍启用。
- 影响：用户在编辑页选择“自定义路径”后，会认为“生成”就是按配置生成；但列表顶部批量入口会无视该配置，转而下载 zip。多账号协作时，一个人按行内入口验证的是本地写入，另一个人勾选同一条记录按顶部入口验证的是 zip 下载，操作日志同样记录“代码生成”，排查时不容易判断到底走了哪条生成路径。若后续允许本地覆盖生成，这个差异还会让批量生成无法按配置执行。
- 建议：顶部“生成”应在选中单条时复用该行完整对象，按 `genType` 明确分流；选中多条时应展示生成方式摘要，若包含自定义路径配置则要求用户选择“统一下载 zip”或“逐条按配置生成”，并在确认框和操作日志中记录最终生成方式。若当前环境不允许自定义路径，应彻底隐藏该选项并把两个入口都固定为 zip 下载。

### P3-254 代码生成基本信息“表名称”占位文案误写成“仓库名称”

- 现象：代码生成编辑页“基本信息”里的字段标签是“表名称”，但输入框占位提示写成“请输入仓库名称”。这个页面维护的是 `gen_table.table_name`，不是业务仓库名称。
- 证据：`erp-ui/src/views/tool/gen/basicInfoForm.vue:5-7` 中 `<el-form-item label="表名称" prop="tableName">` 内部的 `<el-input>` 使用 `placeholder="请输入仓库名称"`；同一表单校验规则在 `:43-46` 又提示“请输入表名称”。运行态当前 `gen_table=0`，但 `/tool/gen-edit/index/:tableId` 隐藏副页面和代码生成组件仍存在，且 `tool:gen:edit` 权限节点启用。
- 影响：虽然这是低风险文案问题，但代码生成本身已经是开发/运维类高风险工具，错误占位会让用户误以为这里要填写业务仓库名称，尤其是在本系统同时存在大量仓库、库存和调拨页面时更容易混淆。若后续恢复生成器列表，编辑生成配置时的第一项关键字段就给出错误语义，不利于培训和验收。
- 建议：占位统一改为“请输入表名称”，并顺手复核代码生成编辑页其他提示文案是否仍沿用通用模板或仓库模块旧文案；系统工具若不交付给业务角色，应保持入口停用并移除业务角色授权。

### P3-255 业务页商品选择器隐式依赖商品管理列表和详情权限

- 现象：销售制单、库存调整、报表中心和库存变动日志的商品筛选/选择控件都复用 `ProductSelect`，但该控件固定调用商品列表和商品详情接口。当前运行态角色里，`zdjl / 驻店经理` 有销售新增/提交、库存调整、库存日志和报表查询等业务权限，却没有 `inv:product:list` 或 `inv:product:query`；该角色进入这些页面后，商品下拉会因为商品接口无权限而无法正常加载或回显。
- 证据：`erp-ui/src/views/inventory/components/ProductSelect.vue:13-14` 在聚焦和远程搜索时触发 `searchProduct`，`:66-74` 固定调用 `listProduct(...)`，`:89-95` 对已有值调用 `getProduct(productId)`。这些接口在 `InvProductController` 中分别要求 `inv:product:list` 和 `inv:product:query`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:41-54`。该选择器被销售明细使用，见 `erp-ui/src/views/inventory/sales/index.vue:126-130`；被库存调整使用，见 `erp-ui/src/views/inventory/stock/index.vue:230-234`；被报表中心商品筛选使用，见 `erp-ui/src/views/inventory/report/index.vue:5-10`；也被库存变动日志筛选使用，见 `erp-ui/src/views/inventory/stock/log.vue:5-10`。MySQL 复核当前运行态：`zdjl` 的 `inv:product:list/query` 授权计数为 0，但 `inv:sales:add`、`inv:sales:submit`、`inv:stock:adjust`、`inv:stock:log`、`inv:report:list` 五类业务权限均启用。
- 影响：这类角色可以看到业务页面和动作入口，却在选择商品这一步卡住，用户会误以为商品为空、搜索失败或当前组织没有商品。更糟的是，不同业务页对商品只需要不同粒度的数据：销售只需要可售商品摘要，库存调整需要当前组织可调整商品，报表筛选只需要商品 ID/名称/编码；全部绑到商品管理的列表/详情权限，会让角色矩阵只能在“给完整商品管理权限”和“业务页控件不可用”之间二选一。P3-136 已记录库存日志额外逐行查商品详情，本条聚焦多个业务页共用商品选择器的入口权限依赖。
- 建议：拆出业务场景商品候选接口和权限，例如销售商品候选、库存调整商品候选、报表商品筛选候选，只返回 ID、名称、编码、规格、单位、状态和必要库存摘要；或者在角色授权和前端入口上明确联动要求 `inv:product:list/query`，缺依赖时禁用商品选择器并提示需要补授哪个权限。商品详情读取不应作为普通筛选控件的默认依赖，成本字段仍需按 `inv:cost:view` 脱敏。

### P3-256 通用文件上传前端扩展名校验大小写敏感，合法大写后缀会被拒

- 现象：通用 `FileUpload` 组件提示允许 `docx/pdf/xlsx` 等小写扩展名，但上传前只取文件名最后一段后缀并直接和 `fileType` 数组做大小写敏感比较。用户选择 `合同模板.DOCX`、`授权书.PDF` 这类合法文件时，前端会在请求发出前提示“文件格式不正确”，即使后端通用上传本身按忽略大小写校验可以接受该扩展名。
- 证据：`erp-ui/src/components/FileUpload/index.vue:151-160` 的 `handleBeforeUpload` 使用 `const fileExt = fileName[fileName.length - 1]`，随后 `this.fileType.indexOf(fileExt) >= 0`，没有 `trim()` 或 `toLowerCase()`。劳动合同模板弹窗在 `erp-ui/src/views/oa/laborContract/index.vue:316-317` 使用 `<file-upload ... :file-type="['docx']" />`，通用组件默认类型也都是小写 `doc/docx/xls/xlsx/ppt/pptx/txt/pdf`。后端 `FileUploadUtils.isAllowedExtension()` 在 `erp-modules/erp-file/src/main/java/com/erp/file/utils/FileUploadUtils.java:174-180` 使用 `str.equalsIgnoreCase(extension)`，说明服务端规则并不要求用户手工把后缀改成小写。本轮只读复核未实际上传文件。
- 影响：劳动合同模板、后续业务附件或运维文档上传时，Windows/Office 导出的 `DOCX/PDF/XLSX` 文件会被前端误拒。用户只能靠重命名文件后缀绕过，容易误判为模板格式错误、系统不支持当前文件，增加合同模板配置和多账号上线初始化成本；前后端校验口径不一致也会让问题定位落到文件服务或权限上。
- 建议：`FileUpload` 应把提取到的扩展名和配置的 `fileType` 统一规范为小写后再比较，并去掉首尾空格；必要时结合 MIME 做兜底提示，但最终仍以后端校验为准。劳动合同模板、通用附件、模板下载/上传回归用例应覆盖 `.docx/.DOCX/.pdf/.PDF` 等大小写组合，错误提示要明确是扩展名不在允许范围而不是文件损坏。

### P3-257 通用上传组件网络失败后不复位计数，用户重试成功也不会写回表单值

- 现象：通用文件和图片上传组件都用 `number` 记录本轮待上传数量，用 `uploadList.length === number` 判断是否把成功文件写回表单；但浏览器层上传失败只弹“上传失败，请重试”并关闭 loading，不减少 `number`、不清空 `uploadList`。一旦模板文件或企业章图片上传遇到网络错误、网关错误或文件服务不可达，用户在同一个弹窗里再次选择文件，即使第二次上传成功，组件也可能因为计数不相等而不把 URL 写回 `v-model`，表单继续认为没有上传。
- 证据：`FileUpload.handleBeforeUpload()` 在 `erp-ui/src/components/FileUpload/index.vue:176-178` 打开 loading 并 `this.number++`；`handleUploadError()` 在 `:184-188` 只提示失败和关闭 loading；成功分支在 `:190-199` 只有后端返回非 200 时才 `this.number--`，真正浏览器错误不会回退；`uploadedSuccessfully()` 在 `:217-224` 只有 `this.uploadList.length === this.number` 才拼入 `fileList` 并 `$emit("input", ...)`。`ImageUpload` 同样在 `erp-ui/src/components/ImageUpload/index.vue:184-185` 增加计数，`:221-224` 的失败分支不复位，`:226-233` 也按成功数等于计数才回填。劳动合同模板和企业章分别在 `erp-ui/src/views/oa/laborContract/index.vue:316-338` 使用这两个组件，模板保存还要求 `templateFileUrl` 必填，企业章保存也要求 `sealImageUrl` 非空，见同文件 `:805-824`。
- 影响：这会把一次临时网络失败变成当前弹窗的持续失败状态。合同管理员可能看到第二次上传请求已经成功返回，但页面仍提示“请上传模板文件”或“请填写企业章名称并上传图片”，只能关闭页面、刷新或重新打开组件来恢复。多账号多店铺上线初始化时，合同模板和企业章属于高频配置项；这种失败后重试不生效会被误判成文件权限、文件格式或后端保存问题。
- 建议：`handleUploadError` 应按当前失败文件回退 `number`，必要时清空本轮 `uploadList` 并关闭 loading；单文件上传失败后应允许下一次成功立刻 `$emit("input")`。批量上传要明确“部分成功/部分失败”的处理策略：要么成功项即时入列、失败项提示重试；要么整批回滚并清空临时状态。劳动合同模板、企业章和后续业务附件上传回归应覆盖首次网络失败后原弹窗内重试成功的场景。

### P3-258 用户导入弹窗没有错误回调和业务状态判断，失败可能卡住或被当成成功刷新

- 现象：用户导入弹窗复用 `ExcelImportDialog`，上传进度开始后会把 `isUploading` 置为 true 并禁用上传控件，但组件没有绑定 `on-error`。网络失败、网关失败或后端非 2xx 响应时，弹窗不会把 `isUploading` 复位，也不会给出明确的导入失败提示，用户通常只能关闭重开。另一方面，只要浏览器层上传成功进入 `handleSuccess`，组件不检查 `response.code`，会直接关闭弹窗、弹“导入结果”并触发父页面刷新；后端若返回业务失败 JSON，也会被当成完成态处理。
- 证据：`erp-ui/src/components/ExcelImportDialog/index.vue:3` 的 `<el-upload>` 只绑定 `:on-progress` 和 `:on-success`，没有 `:on-error`；`:96-99` 的 `handleProgress()` 只设置 `isUploading=true`；`:100-108` 的 `handleSuccess(response)` 无论 `response.code` 是多少都执行 `this.visible=false`、`this.isUploading=false`、按 HTML 弹出 `response.msg` 并 `$emit('success')`。用户管理页在 `erp-ui/src/views/system/user/index.vue:192` 挂载该组件，`@success="getList"` 会刷新用户列表；导入入口在同页 `:48` 受 `system:user:import` 控制。本轮未执行导入写入，只按前端控制流确认失败状态处理缺口。
- 影响：批量创建用户是多账号初始化的关键流程。文件过大、token 失效、权限异常、网关超时或服务端校验失败时，管理员要么看不到明确失败原因并被困在禁用状态，要么看到一个“导入结果”弹窗和列表刷新，误以为导入已完成。叠加 P2-112 的“导入后还要补角色/岗位/店铺授权”和 P2-119 的 HTML 渲染风险，当前用户导入流程缺少可靠的失败、重试和结果状态边界。
- 建议：`ExcelImportDialog` 应增加 `on-error`，在 HTTP/网络失败时复位 `isUploading`、保留或清理当前文件并给出明确重试提示；`handleSuccess` 必须判断 `response.code`，只有成功或明确的结构化导入结果才关闭弹窗并触发父页面刷新。导入结果建议返回结构化成功/失败行数、错误行下载链接和纯文本错误列表；用户导入回归应覆盖权限失败、网络失败、业务校验失败、部分成功和全部成功。

### P3-259 当前组织请求头会隐式覆盖系统用户/角色列表筛选，左侧组织树和列表口径不一致

- 现象：前端请求拦截器会把当前选择的门店/仓库 `Dept-NumId` 请求头带到所有接口，而不只带到库存或 OA 业务接口。系统用户列表、用户导出、角色已分配/未分配用户列表的后端收到该请求头后，会在非超级管理员场景把查询条件 `deptId` 强制改成当前请求头组织。用户管理页左侧组织树仍按 `/system/user/deptTree` 加载可见部门，点击其他组织节点也会把 `queryParams.deptId` 发出，但后端会被请求头覆盖，页面表现为“树选中了别的组织，列表仍只按当前门店/仓库过滤”。
- 证据：`erp-ui/src/utils/request.js:36-41` 在有 token 时对全部请求写入 `Dept-NumId = getSelectedInventoryDeptId()`；用户管理页左侧组织树点击只设置 `queryParams.deptId = data.id` 后调用列表，见 `erp-ui/src/views/system/user/index.vue:3` 和 `:328-332`，重置也只清 `queryParams.deptId` 和树选中状态，见 `:392-398`，不会清理会话里的当前组织。后端 `SysUserController.list/export` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:86-102` 调用 `shopDeptFilterSupport.applyTo(user, request)`，`SysRoleController.allocatedList/unallocatedList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:177-196` 同样调用；`SysShopDeptFilterSupport.applyTo()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/support/SysShopDeptFilterSupport.java:26-35` 对非管理员读取 `Dept-NumId` 后校验授权并 `user.setDeptId(deptId)`。同一个用户管理组织树 `/system/user/deptTree` 只调用 `deptService.selectDeptTreeList(dept)`，见 `SysUserController.java:427-435`，没有同样按请求头收窄。MySQL 当前运行态样本显示 `ry / zjl` 不是超级管理员，但拥有 7 个经营组织授权，同时持有 `system:user:list`、`system:user:export` 和 `system:role:list`。
- 影响：多账号巡检时，管理员看到的是一棵可点击的组织树和用户/角色候选筛选器，但真实列表可能一直被顶部当前组织上下文限制；导出也会带着同一个隐式过滤。非超级管理员在用户治理、角色分配、店铺授权前置排查时，可能误以为其他组织没有用户、候选用户不足，或者重置搜索后仍拿不到全范围结果。这个问题会干扰“创建新用户 -> 分配角色 -> 授权店铺/仓库”的闭环判断，尤其当前 `ry` 这类多组织系统管理账号已经具备触发条件。
- 建议：系统管理类账号治理页面应显式区分“当前业务组织上下文”和“管理筛选组织”。如果用户列表/角色候选确实要受当前组织限制，页面顶部和左侧树都必须同步显示“当前仅查看 X 组织”，组织树只展示可切换的同一范围，并提供跳转选择组织的入口；如果系统管理页应按数据权限和左侧树筛选，则请求拦截器应允许这些接口不带 `Dept-NumId`，或后端不要用业务组织请求头覆盖显式查询参数。用户列表导出、角色分配候选和店铺授权前置列表要使用同一套口径，并补非管理员多组织账号的回归用例。

### P3-260 岗位列表展示排序字段，但后端列表没有按岗位排序返回

- 现象：岗位管理表格展示“岗位排序”，新增/编辑弹窗也要求填写岗位顺序；但岗位列表接口对应 SQL 没有 `ORDER BY post_sort`，页面只能依赖数据库默认返回顺序。当前运行态已经存在相同或交错的排序值，实际列表可能按 `post_id` 或物理顺序展示，和用户维护“岗位顺序”的预期不一致。
- 证据：前端 `erp-ui/src/views/system/post/index.vue:92-93` 展示 `postSort` 列，`:146-148` 用 `el-input-number` 维护岗位顺序，`:224-229` 直接把 `/system/post/list` 返回的 `response.rows` 赋给 `postList`，没有前端排序。后端 `SysPostController.list()` 调用 `postService.selectPostList(post)`，`SysPostMapper.xml` 的 `selectPostList` 只有 `postCode/status/postName` 条件，没有 `order by post_sort, post_id`。MySQL 当前 `sys_post=10`，其中 `post_id=1 / 程序员 / post_sort=1`、`post_id=5 / 实习生 / post_sort=1`、`post_id=2 / 测试员 / post_sort=2`、`post_id=6 / 茶艺师 / post_sort=2`；无排序查询返回时 `post_id=5` 会排在 `post_id=2` 后面，页面顺序不等同于岗位排序。
- 影响：岗位顺序通常用于让管理员按组织习惯查看岗位层级或岗位配置优先级。当前页面给了可维护字段，却不能保证列表、导出或岗位选择框按同一顺序展示；岗位又参与用户配置、调拨审批候选和薪资档位口径，排序表现不稳定会增加基础资料治理和验收对账成本。
- 建议：岗位列表、导出和岗位选择框统一按 `post_sort asc, post_id asc` 或明确业务排序返回；如果允许相同排序值，应在页面说明并用岗位编号兜底稳定排序。回归用例应覆盖两个相同排序、交错 `post_id` 的岗位，确认列表、导出和用户新增/编辑岗位选择顺序一致。

### P3-261 多个进销存列表有筛选条件但没有一键重置，跨页查找效率不一致

- 现象：客户、供应商、销售、销售退货和采购退货等列表页都有 3 到 4 个筛选条件，但搜索区只提供“搜索/新增/导出”等按钮，没有一键“重置”。用户切换客户、供应商、单据状态或编码筛选后，只能逐个清空输入框和下拉框，再重新搜索。
- 证据：`erp-ui/src/views/inventory/customer/index.vue:4-31` 提供客户名称、客户编码、客户等级、状态筛选，`:159-167` 只有 `getList()`，没有 `resetQuery` 或重置按钮；供应商页 `erp-ui/src/views/inventory/supplier/index.vue:4-32` 提供供应商名称、编码、合作状态、状态筛选，`:215-220` 同样只有列表查询；销售页 `erp-ui/src/views/inventory/sales/index.vue:12-39` 提供单号、标题、状态筛选，方法区只有 `getList()`；采购退货、销售退货页面的脚本同样无 `resetQuery`。同目录下分类、商品、库存、库存盘点、调拨、发货通知和报表页面已经有“重置”按钮或 `resetQuery`，说明当前交互口径不一致。当前运行态 `inv_customer=0`、`inv_supplier=17`、`inv_sales_order=0`，客户/销售空表时影响较轻，但供应商列表已有 17 条，后续客户和销售录入后会直接影响日常查找。
- 影响：多账号多店铺审计经常需要按组织、状态、名称、编码反复切换筛选；没有重置按钮会让用户误以为列表仍然为空，实际只是残留了旧筛选条件。供应商、客户和销售链路还会影响新增、编辑、删除引用检查和导出范围判断，一键清空能力缺失会降低排查效率，也让同一套进销存页面的交互学习成本变高。
- 建议：进销存列表统一搜索区规范：所有带 2 个以上筛选条件的列表都提供“搜索 / 重置”，`resetQuery` 应重置 `pageNum=1`、清空全部查询字段并重新加载列表；导出确认也应读取重置后的真实筛选摘要。回归用例覆盖客户、供应商、销售、采购退货、销售退货在设置多个筛选条件后点击重置能恢复默认列表。

### P3-262 单据状态审计只保存操作者用户名，缺少稳定主体和会话上下文

- 现象：运行态状态审计表和 6 个触发器已经能记录单据状态变化，但操作者只保存 `operator_name` 字符串，来源也是业务表的 `update_by/create_by`。表中没有 `user_id`、昵称、角色、当前选择组织类型、请求来源、客户端或会话标识，后端审计查询接口也只能返回这个字符串。
- 证据：`inv_document_status_log` 当前字段为 `log_id/document_type/document_id/document_no/shop_dept_id/previous_status/new_status/action/operator_name/operate_time/remark`，没有稳定主体字段；当前两条样例均为 `operator_name=admin`。6 个触发器 `trg_inv_*_status_au` 写入操作者时均使用 `coalesce(nullif(NEW.update_by, ''), nullif(NEW.create_by, ''), '')`。业务服务在销售、采购、退货、盘点、发货通知等状态变更时普遍只把 `updateBy` 设置为 `SecurityUtils.getUsername()`。后端 `InvDocumentStatusLog` 实体只有 `operatorName`，`InvDocumentStatusLogMapper.xml` 和 `/audit/list` 也只查询 `operator_name`、组织 ID/名称和状态字段。本轮只读复核未构造状态写入。
- 影响：多账号多店铺场景下，状态审计需要回答“哪个账号、哪个角色、在什么组织上下文、通过哪个页面/接口把单据改成这个状态”。当前只靠用户名字符串，无法和登录会话、角色快照、当前 `Dept-NumId`、客户端来源或操作日志稳定关联；如果后续账号改名、同名复用、脚本补数据或后台任务改状态，审计人员只能看到一个文本操作者，难以确认真实责任主体。
- 建议：状态审计写入时补稳定主体快照：至少包含 `operator_user_id`、`operator_user_name`、`operator_nick_name`、角色键摘要、当前选择组织 ID/名称/类型、请求来源或客户端、trace/request ID。若继续用数据库触发器写入，可由应用在事务内设置会话变量供触发器读取；更推荐在服务层写结构化状态事件，并由触发器只做兜底。后续状态审计页面、单据详情时间线和导出应按用户 ID、组织和动作来源过滤，而不是只显示用户名。

### P3-263 字典项默认值字段有真实数据和导出定义，但电脑端不可见也不可维护

- 现象：运行态 `sys_dict_data.is_default` 已经为多个字典类型维护默认项，例如用户性别默认“男”、系统开关默认“正常”、任务分组默认“默认”；后端实体和 Excel 导出也把“是否默认”定义成字典项字段。但电脑端“字典数据”副页面只展示字典编号、标签、键值、排序、状态、备注和创建时间，新增/编辑弹窗同样没有“是否默认”控件，管理员无法在网页端核对或调整这些默认项。
- 证据：浏览器打开 `http://127.0.0.1:1025/system/dict-data/index/1`，页面截图 `docs/audit-screenshots/20260625/system-dict-data-default-field.png` 显示用户性别 3 条字典项仅有“字典项编号/字典标签/字典键值/字典排序/状态/备注/创建时间/操作”列。MySQL 当前 `sys_dict_data=29`，其中 `sys_user_sex`、`sys_normal_disable`、`sys_job_status`、`sys_job_group`、`sys_notice_type`、`sys_yes_no` 等类型均有 `is_default='Y'` 的样例。后端 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysDictData.java` 对 `isDefault` 标注了 `@Excel(name = "是否默认", readConverterExp = "Y=是,N=否")`，`SysDictDataMapper.xml` 的查询、插入和更新 SQL 都读写 `is_default`；但 `erp-ui/src/views/system/dict/data.vue` 的列表列、表单项和重置表单字段都没有 `isDefault`，只在后端导出里可见。本轮未新增或修改字典项。
- 影响：默认字典项通常会影响新建用户、状态默认值、通知类型、任务分组等表单初始化语义。当前页面让管理员能维护字典标签、键值、排序和状态，却看不到哪一项是默认，导出文件又会出现一个网页端无法解释的“是否默认”列；多账号巡检或基础资料治理时，网页端显示内容与数据库/导出内容不一致，容易把默认值问题误判为代码固定逻辑或缓存问题。
- 建议：字典数据列表和新增/编辑弹窗补“是否默认”字段，并明确每个字典类型是否允许 0 个、1 个或多个默认项；保存时后端应按 `dict_type` 校验默认项唯一性或允许多默认的例外规则，导出、详情抽屉和字典缓存保持同一口径。如果默认项已不再被业务使用，应从实体、导出和数据库数据中清理或标注为历史字段，不要继续保留不可见字段。

### P3-264 库存变动日志有操作人字段和真实数据，但电脑端列表不展示也不能按操作人筛选

- 现象：库存流水表和后端导出都保留了操作人字段，当前运行态 5 条库存流水分别由 `admin`、`dushuai`、`ll1` 写入；但电脑端库存变动日志页只展示商品、库存组织、批次/效期/库位、变动类型、业务单号、数量、成本价、备注和时间，没有“操作人”列，也没有按操作人筛选。页面能看到某个库存组织发生了调拨出库或库存调整，却不能直接判断是哪一个账号触发。
- 证据：浏览器选择 `仓库：仓库` 后进入 `http://127.0.0.1:1025/inventory/stock-log`，截图 `docs/audit-screenshots/20260625/inventory-stock-log-missing-operator.png` 显示列表列为商品编码、商品名称、库存组织、批次号、效期、序列号、库位、变动类型、业务单号等，未展示操作人。MySQL 当前 `inv_stock_log` 字段包括 `create_by`，样例为 `log_id=7/8 create_by=admin`、`log_id=9/11 create_by=dushuai`、`log_id=10 create_by=ll1`。后端 `InvStockLog` 实体把 `createBy` 标注为 `@Excel(name = "操作人")`，`InvStockLogMapper.xml` 的列表 SQL 也返回 `l.create_by`；库存调整、采购质检、销售出库、退货、盘点、调拨等写流水路径均使用 `SecurityUtils.getUsername()` 设置 `createBy`。前端 `erp-ui/src/views/inventory/stock/log.vue` 的查询表单没有 `createBy`，表格列也没有操作人。本轮只读复核，未触发库存写入。
- 影响：多账号多店铺场景下，库存流水是最关键的库存责任证据。当前页面能看到仓库库存少了 7 或 1、市场部门库存增加了 7，但不能直接看到是 `dushuai` 还是 `ll1` 操作；导出才有操作人，日常网页端排查需要额外导出或查库。更深一层，库存流水也只保存用户名字符串，没有用户 ID、角色、当前组织上下文和会话来源，一旦账号改名、同名复用或后台任务写入，库存责任链会比页面展示更难追溯。
- 建议：库存变动日志页面补“操作人”列和操作人筛选，并在导出确认中展示操作人筛选摘要；后端列表条件支持 `create_by` 或稳定 `operator_user_id`。后续库存流水模型应和状态审计一起补稳定主体字段：用户 ID、用户名、昵称、角色摘要、当前选择组织、客户端/请求来源和 trace/request ID；页面和导出优先展示稳定主体，用户名只作辅助文本。

### P3-265 密码最后更新时间参与安全策略，但用户管理页不可见也不能筛选

- 现象：`sys_user.pwd_update_date` 会参与初始密码提醒和密码有效期判断，运行态已有 25 个启用账号中的 13 个为空；但电脑端用户管理列表、查询区、字段列设置、详情抽屉和导出字段都没有“密码最后更新时间 / 初始密码未修改 / 密码过期状态”。管理员能看到“配置状态”是否分配角色和店铺，却看不到账号密码治理状态，只能逐个登录或查库判断。
- 证据：MySQL 只读统计当前 `sys_user` 启用账号 `25` 个，`pwd_update_date is null` 为 `13` 个、`login_date is null` 为 `2` 个；`user_id=103 / user_name=123` 同时 `login_date=NULL`、`pwd_update_date=NULL`。系统参数当前 `sys.account.initPasswordModify=1`、`sys.account.passwordValidateDays=0`。`SysUserController.getInfo()` 使用 `user.getPwdUpdateDate()` 计算 `isDefaultModifyPwd` 和 `isPasswordExpired`，`initPasswordIsModify()` 在 `pwdUpdateDate == null` 时触发初始密码提醒，`passwordIsExpiration()` 在启用有效期且为空时直接视为过期。`SysUserMapper.xml` 的详情 SQL `selectUserVo` 查询 `u.pwd_update_date`，但 `selectUserList` 不返回该字段；`SysUser.java` 只给最后登录 IP/时间加了 `@Excel`，`pwdUpdateDate` 没有导出注解。浏览器打开 `http://127.0.0.1:1025/system/user` 并点击用户 `123`，截图 `docs/audit-screenshots/20260625/user-detail-missing-pwd-update-date.png` 显示详情抽屉只有创建/更新/最后登录信息，没有密码更新时间或初始密码状态；前端 `erp-ui/src/views/system/user/index.vue` 的搜索字段和表格列也没有该字段，`erp-ui/src/views/system/user/view.vue` 详情只展示最后登录 IP/时间。本轮只读复核，未新增、修改或重置账号密码。
- 影响：多账号多店铺验收时，账号安全治理需要先找出“从未改初始密码、密码时间为空、如果开启有效期会立即过期”的账号。当前页面没有这个维度，管理员无法在用户管理页批量筛选 13 个风险账号，也无法在导出文件中交给负责人处理。启用密码有效期前也缺少影响预览，容易让一批历史账号登录后才发现提示或阻断策略；门店共用电脑场景下，风险账号会长期隐藏在普通“正常”状态里。
- 建议：用户管理列表、查询区、详情抽屉和导出补“密码最后更新时间”“初始密码未修改”“密码是否过期/策略命中”字段；后端 `selectUserList/export` 返回 `pwd_update_date` 并支持 `pwdUpdateDate is null`、过期天数和是否初始密码筛选。启用 `sys.account.passwordValidateDays` 前提供账号影响预览，列出会被提醒或阻断的用户；重置密码、导入用户和新增用户后也应明确写入或保留密码治理状态，避免管理员只能靠 SQL 排查。

### P3-266 考勤打卡来源字段只写固定 WEB，不能支撑地点和设备核验

- 现象：考勤打卡确认框会提示“来源：WEB 网页端”，前端提交字段名却是 `checkInLocation/checkOutLocation`，后端也把它保存到 `check_in_location/check_out_location`。当前实现实际只写固定字符串 `WEB`，没有真实地点、设备、浏览器、IP 或定位授权状态；列表、全部视图和导出也不展示这些来源/地点字段。
- 证据：浏览器进入 `http://127.0.0.1:1025/oa/attendance` 后只打开“上班打卡”确认框并取消，截图 `docs/audit-screenshots/20260625/oa-attendance-web-source-only-confirm.png` 显示确认文案为“账号：admin / 来源：WEB 网页端”。前端 `erp-ui/src/views/oa/attendance/index.vue` 在打卡和签退时分别提交 `checkInLocation: "WEB"`、`checkOutLocation: "WEB"`，确认文案同样写固定来源；表格列只有日期、上班打卡、下班打卡、工时、迟到、早退和状态。后端 `OaAttendanceServiceImpl.checkIn/checkOut` 只把传入的 `location` 原样写入 `record.setCheckInLocation/CheckOutLocation`，没有读取客户端 IP、UA、设备 ID 或浏览器定位。`OaAttendanceRecord` 虽有 `checkInLocation/checkOutLocation` 字段，但没有 `@Excel` 注解；Mapper 会查询和保存这两个字段，导出不会包含它们。MySQL 当前唯一考勤记录 `record_id=1` 的 `check_in_location=''`、`check_out_location=NULL`，说明既有数据也没有可核验来源。本轮只打开确认框并点击取消，没有提交打卡或签退。
- 影响：考勤记录需要回答“谁在什么组织、通过什么设备或位置完成打卡/签退”。现在确认框让用户以为系统记录了网页端来源，但数据库要么为空、要么只能写固定 `WEB`，对异常打卡、共用电脑、代打卡或后续考勤更正没有证明力。管理员导出考勤时也看不到来源/地点字段，只能用通用操作日志间接查 IP，且日志和考勤记录没有稳定关联。多店铺场景下，这会削弱考勤、工资计算和员工责任追踪的可信度。
- 建议：先把字段语义改清楚：如果只是渠道，应命名并展示为“打卡渠道=网页端/移动端/导入”，不要叫 location；如果要做地点核验，应记录并展示可审计的地点或设备信息，例如 IP、UA、设备 ID、定位结果、定位授权状态和采集失败原因。考勤列表、详情、导出和更正流程都应展示打卡/签退渠道与来源证据；历史空值要以“未记录”标注，不能和真实 `WEB` 来源混在一起。

### P3-267 供应商合作状态快捷切换复用完整编辑接口，缺少独立状态权限和差异审计

- 现象：供应商列表行内有“状态”下拉，可把合作状态改为合作中、暂停或终止；但这个看起来像状态快捷动作的入口只复用 `inv:supplier:edit`，确认框也只展示供应商名称和目标状态。确认后前端把整行供应商对象传给完整编辑接口，而不是只提交 `supplierId + cooperationStatus` 的状态动作。
- 证据：供应商页操作列的状态下拉使用 `v-hasPermi="['inv:supplier:edit']"`，见 `erp-ui/src/views/inventory/supplier/index.vue:55-67`；`handleCooperationStatusChange()` 在 `:255-269` 确认后调用 `updateSupplier(Object.assign({}, row, { cooperationStatus }))`。前端 API `erp-ui/src/api/inventory/supplier.js:24-25` 调用 `POST /inventory/supplier/update`，后端 `InvSupplierController.edit()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSupplierController.java:66-72` 仍是普通 `@RequiresPermissions("inv:supplier:edit")` 和 `@Log(title="供应商管理", businessType=UPDATE)`；`InvSupplierServiceImpl.saveSupplier()` 更新分支只做范围校验后调用 `supplierMapper.updateInvSupplier(supplier)`，见 `:55-69`。Mapper 更新 SQL 会写入名称、编码、联系人、电话、邮箱、地址、结算方式、合作状态和启用状态等所有非空字段，见 `InvSupplierMapper.xml:96-110`。操作日志切面只按注解写 `title/businessType` 并保存请求参数，见 `LogAspect.java:142-159`，`sys_oper_log` 也没有状态动作、变更前后值或原因字段，见 `SysOperLogMapper.xml:31-33`。MySQL 当前 `inv_supplier=17`，17 条均为 `cooperation_status=0/status=0`，其中 16 个供应商被 44 条商品引用；权限树只有 `inv:supplier:list/query/add/edit/remove/export`，没有供应商状态专用权限。本轮浏览器直达 `/cangku/supplier` 仍为空白、`/inventory/supplier` 进入 404，已由 P2-16 覆盖入口问题；本条未触发任何状态修改。
- 影响：角色无法只被授予“暂停/恢复供应商”能力，必须拿到完整供应商编辑权限；拿到该权限后也能修改名称、联系人、结算方式和启用状态。多账号并发时，如果 A 管理员先打开列表，B 管理员随后修改了联系方式或结算方式，A 再点“状态”会把旧行对象一并提交，可能用列表旧值覆盖非目标字段。审计上也只能看到一条泛化的“供应商管理 UPDATE”和整包参数，不能直接按“供应商暂停/终止/恢复”、变更前后状态、原因或受影响商品数追踪责任。
- 建议：把合作状态切换拆成独立接口和权限，例如 `inv:supplier:status`，请求只允许 `supplierId`、目标 `cooperationStatus`、原因和版本号；后端锁定或校验当前版本后只更新 `cooperation_status/update_by/update_time`，并记录变更前后状态、原因、当前组织、操作者和受影响商品数。前端确认框展示供应商编码/名称、当前状态到目标状态、关联商品数量和采购影响摘要；操作日志标题或状态事件应区分“供应商合作状态变更”，不要继续混在完整编辑日志里。

### P3-268 用户登录账号唯一性只靠服务层校验，数据库缺少唯一约束

- 现象：用户管理、注册和导入链路都把 `user_name` 当作唯一登录账号，但当前 `sys_user` 表只有 `PRIMARY(user_id)`，没有对有效账号 `user_name` 做数据库唯一约束。现有 25 个账号暂时都是唯一值，但并发新增、注册、导入脚本或直接 SQL 仍可能绕过服务层查询写出重复登录账号。
- 证据：用户列表和新增弹窗把“用户名称”作为登录账号展示和录入，见 `erp-ui/src/views/system/user/index.vue:61-64`、`:133-139`，前端规则只校验必填和长度，见 `:267-271`。后端新增、修改和注册分别在 `SysUserController.add/edit/register` 调 `checkUserNameUnique`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:157-166`、`:276-295`、`:310-323`；导入链路在 `SysUserServiceImpl.importUser()` 中先 `selectUserByUserName`，不存在就直接 `insertUser`，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:525-538`。唯一性检查只是 `checkUserNameUnique()` 查一条有效用户，见 `SysUserServiceImpl.java:176-184` 和 `SysUserMapper.xml:188-190`；登录态用户信息接口 `selectUserByUserName` 按 `u.user_name = #{userName} and u.del_flag = '0'` 查询且不加唯一兜底，见 `SysUserMapper.xml:178-180`。MySQL 只读复核 `SHOW INDEX FROM sys_user` 仅有主键，`total_users=25 / distinct_names=25 / active_users=25 / active_distinct_names=25`，当前无重复但缺少数据库保护。
- 影响：多账号多店铺验收中，登录账号是认证、菜单权限、店铺授权、用户导入更新、日志主体和“去配置店铺授权”跳转的共同身份。若出现两个有效用户同名，登录和内部 `/user/info/{username}` 可能查询到多行而失败，或让权限、角色和组织范围落到不可预期的用户；用户列表能看到重复账号但后续重置密码、分配角色、店铺授权、日志追责都很难判断目标。即使页面正常拦截，数据库层没有约束也会让批量导入和迁移脚本成为高风险入口。
- 建议：为有效用户登录账号增加数据库级唯一兜底。若允许逻辑删除后复用账号，可使用“仅 `del_flag='0'` 生效”的生成列/函数索引或等价约束；迁移前先输出重复账号巡检和清理清单。新增、注册和导入继续保留服务层友好提示，但要捕获数据库唯一冲突并返回明确错误；店铺授权跳转、日志、会话和审计也应逐步以稳定 `user_id` 为主，`user_name` 只作为展示快照。

### P3-269 角色权限字符唯一性只靠服务层校验，数据库缺少唯一约束

- 现象：角色管理把 `role_key` 作为权限字符展示、筛选、编辑和前端角色判断依据，但 `sys_role` 表同样只有 `PRIMARY(role_id)`，没有对有效角色的 `role_key` 或 `role_name` 做数据库唯一约束。当前 11 个有效角色暂时没有重复权限字符，但并发保存、脚本导入或直接 SQL 可以写出两个同名/同权限字符角色。
- 证据：角色列表和表单都展示并维护“权限字符”，见 `erp-ui/src/views/system/role/index.vue:14-21`、`:107-110`、`:172-180`，前端规则只校验必填，见 `:341-347`。后端 `SysRoleController.add/edit` 只调用 `checkRoleNameUnique` 和 `checkRoleKeyUnique` 后保存，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:90-124`；服务层唯一校验在 `SysRoleServiceImpl.java:156-183`，底层 SQL 只是 `where r.role_name=... limit 1` 和 `where r.role_key=... limit 1`，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysRoleMapper.xml:86-94`。角色权限集合会把用户绑定角色的 `roleKey` 拆分后放入 `Set`，见 `SysRoleServiceImpl.selectRolePermissionByUserId()` `:99-112`；新增/编辑 SQL 仍直接写 `role_key` 和菜单关联，见 `SysRoleMapper.xml:96-139`。MySQL 只读复核 `SHOW INDEX FROM sys_role` 仅有主键，当前 `total_roles=11 / active_roles=11 / active_distinct_role_keys=11 / active_distinct_role_names=11`，暂无重复但缺数据库保护。
- 影响：角色权限字符是 `hasRole`、菜单授权、数据范围和管理员理解角色能力的核心身份。若出现两个有效角色共用 `role_key`，前端 `roles` 集合会把它们折叠成同一个权限字符，但它们可能拥有不同菜单、数据权限和用户绑定；管理员在列表、导出、分配用户和数据权限弹窗中会看到两个看似相同的角色身份，却无法判断哪个真正控制了某个账号的能力。多账号多店铺权限验收会因此把“角色名相同/权限字符相同”误判成同一权限包。
- 建议：为有效角色的 `role_key` 增加数据库唯一兜底，并评估 `role_name` 是否也需要在有效角色内唯一。若允许逻辑删除后复用，采用仅对 `del_flag='0'` 生效的生成列或等价约束；迁移前输出重复角色名称和权限字符巡检。新增、编辑和脚本导入都要捕获唯一冲突，角色列表也应展示菜单数量、数据范围和已授权用户数，便于管理员识别权限字符背后的实际能力差异。

### P3-270 部门同级名称唯一性只靠服务层校验，数据库缺少组织树身份兜底

- 现象：部门/组织管理把同一父级下的部门名称作为组织树可读身份使用，新增和修改时也会提示“部门名称已存在”；但 `sys_dept` 表只有 `PRIMARY(dept_id)`，没有 `(parent_id, dept_name)` 对有效组织的唯一约束。当前运行态同父级下没有重名组织，但并发保存、脚本导入或直接 SQL 可以写出两个同父级同名门店/仓库/公司。
- 证据：部门管理列表和弹窗展示、维护“部门名称”，见 `erp-ui/src/views/system/dept/index.vue:84`、`:146-148`，前端规则只校验必填，见 `:257-259`。后端 `SysDeptController.add/edit` 只在保存前调用 `checkDeptNameUnique`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:103-110`、`:119-136`；服务层 `checkDeptNameUnique()` 按 `deptName + parentId` 查一条有效组织，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:276-286`；Mapper SQL 只是 `where dept_name=#{deptName} and parent_id=#{parentId} and del_flag='0' limit 1`，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml:182-185`，新增和编辑 SQL 仍直接写 `dept_name/parent_id`，见 `:187-233`。MySQL 只读复核 `SHOW CREATE TABLE sys_dept` 只有主键，当前有效组织共 11 个，按 `parent_id, dept_name` 分组暂无重复；同名“财务部门”分别在不同父级 `101/102` 下存在，这是合法跨父级重名样本。
- 影响：组织树是登录后选择门店/仓库、用户主部门、店铺授权、角色数据权限、仓库选择、工资和 OA 范围的共同入口。同父级若出现两个同名门店或仓库，页面树、授权弹窗、导出和日志中的“部门名称”会失去唯一解释，管理员容易给错组织授权或把业务数据归到错误门店；而很多日志、确认框和列表只展示组织名称或路径不完整，后续追责必须靠 `dept_id` 查库才能分辨。
- 建议：为有效组织增加数据库级同父级名称唯一兜底，例如 `(parent_id, dept_name, active_flag)` 的生成列/唯一索引，允许逻辑删除后复用但禁止有效组织重名。保存接口继续保留服务层友好提示，同时捕获唯一冲突；组织选择、店铺授权、角色数据权限和导出中应展示完整路径或 `dept_id` 辅助信息，避免跨父级同名和同父级异常重名时只能靠人工猜测。

### P3-271 菜单名称和路由身份唯一性只靠服务层校验，数据库缺少菜单树兜底

- 现象：菜单管理把同级菜单名称、同级路由地址和页面路由名称当作唯一身份使用，前端提示路由名称要“保证唯一性”，后端新增/修改也会拦截重复；但 `sys_menu` 表只有 `PRIMARY(menu_id)`，没有对同级 `menu_name/path` 或页面 `route_name` 做数据库唯一约束。当前启用目录/页面暂时没有重复路由名或同级路径，但并发保存、初始化脚本、迁移 SQL 或直接改库仍可以写出冲突菜单。
- 证据：菜单表单维护“菜单名称、路由名称、路由地址、组件路径、权限字符”，见 `erp-ui/src/views/system/menu/index.vue:186-225`、`:230-249`，其中路由名称 tooltip 明确要求保证唯一性，见 `:191-198`；前端规则只校验 `menuName/orderNum/path` 必填，见 `:363-372`，提交时直接调用 `addMenu/updateMenu`，见 `:475-490`。后端 `SysMenuController.add/edit` 只在保存前调用 `checkMenuNameUnique` 和 `checkRouteConfigUnique`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMenuController.java:94-109`、`:118-137`；服务层按 `menuName + parentId`、同级 `path`、根目录 `path` 和全局 `routeName/path` 组合做查询循环，见 `SysMenuServiceImpl.java:381-431`；Mapper SQL 只是普通查询，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysMenuMapper.xml:131-138`。MySQL 只读复核当前 `sys_menu=249`，`SHOW INDEX FROM sys_menu` 仅有主键；启用目录/页面按 `parent_id,path` 和 `coalesce(nullif(route_name,''), path)` 分组暂无重复。
- 影响：菜单路由身份是动态路由、顶部菜单、侧边栏、标签页、面包屑、顶部搜索、角色授权树和按钮权限配置的共同基础。若数据库被写入两个同级相同路径或相同路由名，前端动态路由可能出现跳转到错误组件、缓存/标签页互相覆盖、搜索结果指向不稳定、角色授权树难以解释的问题；多账号多店铺验收会把同一权限或同一路由在不同角色下的表现误判成账号权限差异，而不是菜单基础数据冲突。
- 建议：为菜单基础身份增加数据库级兜底：同一父级下菜单名称唯一，同一父级下启用目录/页面路由地址唯一，页面路由身份 `route_name` 或其 fallback 值全局唯一；若允许停用菜单保留历史配置，应明确状态维度或迁移白名单。保存接口继续保留友好提示，同时捕获数据库唯一冲突；菜单导入、初始化 SQL 和发布前巡检也要输出重复路由、重复名称和重复权限码清单。

### P3-272 薪资方案没有唯一身份兜底，员工绑定下拉只靠方案名称区分

- 现象：薪资配置把“方案名称 + 社保口径 + 生效日期”作为薪资方案的主要识别信息，但新增/编辑只校验必填和长度，没有阻止重复方案名称或同名同社保口径方案。当前运行态两个方案暂时不重名，但数据库没有唯一约束，页面员工绑定下拉还只显示 `schemeName`，一旦出现重名方案，管理员在绑定、删除和导出时很难分辨目标。
- 证据：薪资方案表单只维护方案名称、社保口径、生效日期和状态，见 `erp-ui/src/views/system/salary/index.vue:211-239`；前端规则只校验必填，见 `:522-527`，保存时直接调用 `addSalaryScheme/updateSalaryScheme`，见 `:777-790`。员工绑定弹窗的薪资方案下拉 `el-option` 标签只用 `scheme.schemeName`，见 `:365-373`，删除方案确认框也只展示方案名称，见 `:794-796`。后端 `SysSalaryConfigController.add/edit` 只调用保存服务，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysSalaryConfigController.java:110-125`；`SysSalaryConfigServiceImpl.insertSalaryScheme/updateSalaryScheme` 只设置状态、创建/更新人后直接写库，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSalaryConfigServiceImpl.java:99-127`；`SysSalarySchemeMapper.xml:65-97` 也没有重复查询。MySQL 只读复核 `sys_salary_scheme` 只有主键和普通 `status/social_type` 索引，当前两条方案 `金英门店薪资方案-无社保 / 无社保`、`金英门店薪资方案-有社保 / 有社保` 暂无重复名称或同名同社保口径组合。
- 影响：薪资方案会被员工绑定、角色薪资绑定、OA 工资计算和劳动合同薪资选项引用。若后续脚本、并发保存或页面误操作写出两个同名方案，员工绑定下拉只能看到相同名称，删除确认也只提示相同名称；管理员可能把员工绑定到错误方案，或删除/停用错误方案并触发 P2-122 中的级联清理风险。多店铺工资验收时，重名方案还会让导出和操作日志只显示名称，无法快速判断员工使用的是哪个方案版本。
- 建议：明确薪资方案唯一规则，至少在有效方案内约束 `scheme_name + social_type`，必要时把生效日期纳入版本维度并在页面显示“方案名称 / 社保口径 / 生效日期 / ID”。新增和编辑保存前做服务层友好校验，数据库增加唯一兜底或发布前重复巡检；员工绑定下拉、删除确认、角色绑定和导出都应展示社保口径和生效日期，避免只靠名称选择。

### P3-280 员工薪资绑定结束日期进入工资计算但电脑端不可维护

- 现象：`sys_user_salary_scheme` 有 `end_date` 字段，OA 工资计算也按 `end_date` 判断某条员工薪资绑定是否仍有效；但电脑端“员工绑定”列表和新增/编辑弹窗只展示、维护“生效日期”，没有结束日期列或输入框。管理员新增绑定时，前端会把 `endDate` 固定提交为空字符串；已有绑定即使从数据库或接口里带出结束日期，也只能被保存时原样带回，无法在页面上查看或修改。
- 证据：员工绑定表格 `erp-ui/src/views/system/salary/index.vue:164-183` 展示员工、核算门店、社保口径、方案、岗位、档位、地区、工资合计、生效日期、优先级和备注，没有结束日期列；绑定弹窗 `:390-403` 只有 `effectiveDate` 日期选择和优先级输入，没有 `endDate` 控件。保存时 `saveUserRuleBinding()` 在 `:1008-1034` 会把旧绑定的 `binding.endDate || ""` 透传，并把新绑定的 `endDate` 写成 `this.ruleForm.endDate || ""`，但 `ruleForm.endDate` 没有页面输入来源。后端 `SysSalaryConfigServiceImpl.saveUserSalarySchemes()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysSalaryConfigServiceImpl.java:252-291` 不校验结束日期；Mapper `SysUserSalarySchemeMapper.xml:95-124` 会写入 `end_date`。OA 工资员工候选 `OaSalaryEmployeeMapper.xml:21-50` 明确按 `uss.end_date is null or '' or >= concat(#{salaryMonth}, '-01')` 判断有效绑定。运行态当前 `sys_user_salary_scheme=1`，唯一记录 `effective_date=2025-12-28/end_date=''`，`with_end_date=0`。
- 影响：薪资绑定已经按有效期建模，但电脑端只能新增永久有效绑定，不能给调岗、转店、离职、社保口径变更或临时薪资设置结束日期。后续管理员只能依赖更高优先级或更晚生效日期覆盖旧绑定，旧绑定仍会长期留在有效集合里，工资计算的 `salary_scheme_count`、绑定列表和历史追溯都会变得难解释。多门店工资核算时，员工跨店调动尤其需要“某店某档位到哪天失效”的闭环。
- 建议：员工薪资绑定列表和弹窗补“结束日期/有效期至”，并在保存时校验结束日期不得早于生效日期；工资计算前置检查应提示同一员工、同一核算门店、同一月份命中的多条有效绑定及其优先级。若暂不支持到期失效，应移除或忽略 `end_date` 并在工资计算口径中明确“只按优先级和生效日期选择最新绑定”。

### P3-281 采购单和销售单申请人字段会入库和导出，但电脑端列表详情不可见

- 现象：采购单、销售单服务在新增时都会写入 `applicant_id/applicant_name/applicant_dept_id`，列表 SQL 也能连出申请人姓名和申请部门，导出实体还把“申请人账号 / 申请人姓名 / 申请部门”作为 Excel 字段；但电脑端采购管理和销售管理的列表、详情弹窗只展示单号、标题、供应商/客户、金额、状态、时间和明细，不展示申请人或申请部门。
- 证据：采购保存 `InvPurchaseServiceImpl.saveDraft()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:68-76` 设置当前用户 ID、账号和部门；销售保存 `InvSalesServiceImpl.saveDraft()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java:76-84` 做同样写入。采购/销售 Mapper 的列表查询都返回 `applicant_name/applicant_nick_name/applicant_dept_name`，见 `InvPurchaseOrderMapper.xml:25-41`、`InvSalesOrderMapper.xml:25-42`；实体 `InvPurchaseOrder` 和 `InvSalesOrder` 对这些字段标注了 `@Excel`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvPurchaseOrder.java:44-55`、`erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java:54-65`。但采购页表格和详情 `erp-ui/src/views/inventory/purchase/index.vue:37-103` 只展示采购单号、标题、供应商、总金额、状态、质检状态、创建时间和采购明细；销售页 `erp-ui/src/views/inventory/sales/index.vue:44-105` 只展示销售单号、标题、客户、总金额、状态、创建时间和销售明细。运行态当前 `inv_purchase_order=0`、`inv_sales_order=0`，所以本轮未构造业务单据，只按当前 schema 和源码闭环验证。
- 影响：多账号多店铺验收时，采购单和销售单的责任主体不能只靠导出 Excel 或操作日志追溯。页面不显示申请人，会让管理员在列表处理提交、收货、质检、生成发货通知、取消等动作时看不到是谁发起的单据；同一门店多人制单时，业务复盘、权限排查、异常单据沟通都要跳出页面查库或导出，体验和审计闭环不一致。
- 建议：采购单、销售单列表和详情补“申请人 / 申请部门 / 创建账号”字段，并支持按申请人筛选；如果页面空间有限，列表可默认展示申请人账号或姓名，详情展示完整账号、姓名、部门和创建时间。导出、详情和列表字段口径应一致，后续“我的采购单/我的销售单”入口若上线，也应复用同一主体字段和筛选逻辑。

### P3-273 用户手机号和邮箱唯一性只靠部分入口校验，导入和数据库缺少兜底

- 现象：用户新增、修改和个人中心保存都会提示“手机号码已存在 / 邮箱账号已存在”，页面也把手机号、邮箱作为账号联系信息展示和筛选；但 `sys_user` 表没有手机号或邮箱唯一索引，批量导入用户时也只按登录账号判断是否存在，不校验手机号/邮箱是否已被其他有效账号使用。
- 证据：`SysUserController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:280-286`、`:314-320` 调用 `checkPhoneUnique/checkEmailUnique`；个人中心 `SysProfileController.updateProfile()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:89-95` 也做同样校验。唯一查询只在 mapper 中按 `phonenumber/email + del_flag=0 limit 1` 查一条，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:192-198`，服务层再排除当前 `userId`。但导入流程 `SysUserServiceImpl.importUser()` 只用 `selectUserByUserName` 判断账号是否存在，随后 `BeanValidators.validateWithException` 和 `insertUser/updateUser`，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:529-550`，没有调用手机号/邮箱唯一校验。MySQL 复核当前 `sys_user` 只有 `PRIMARY(user_id)`，25 个有效账号中非空手机号 2 个、非空邮箱 2 个且暂无重复；现状干净但数据库和导入入口都不能阻止后续重复。
- 影响：批量建号是多账号交付的主要入口，导入后可能出现多个有效账号共用同一手机号或邮箱。后续用户搜索、角色分配候选、公告阅读用户、日志导出、个人中心联系信息、离职交接和账号核验都会把联系方式当成用户线索，重复联系方式会让管理员无法判断该联系信息到底属于哪个账号；页面新增/编辑阻止重复但导入允许重复，也会造成“电脑端规则前后不一致”。
- 建议：统一账号联系方式唯一规则：如果手机号和邮箱是唯一联系身份，就为有效账号增加数据库唯一约束或等价生成列索引，并让新增、编辑、个人中心、导入和注册共用同一套唯一校验；导入时同一 Excel 内部也要先查重并给出行级错误。若业务允许多人共用门店电话或公共邮箱，应取消“已存在”类唯一提示，把页面改为允许重复但展示“共享联系方式”风险，并在用户搜索和导出中增加账号 ID、部门、角色和授权组织辅助识别。

### P3-274 薪资方案导出确认漏掉社保口径筛选，反而展示页面不存在的状态筛选

- 现象：薪资配置页的薪资方案列表可以按“方案名称”和“社保口径”筛选，导出请求也会把当前查询条件提交给后端；但导出确认文案只展示“方案名称”和“状态”，其中“状态”在页面筛选区并不存在，真实会影响导出范围的“社保口径”反而不显示。
- 证据：前端 `erp-ui/src/views/system/salary/index.vue:8-27` 的筛选区只有 `schemeName` 和 `socialType`，`schemeQueryParams` 也只初始化 `pageNum/pageSize/schemeName/socialType`，见同文件 `:503-508`；`handleExportScheme()` 会把 `{ ...this.schemeQueryParams }` 提交到 `system/salaryConfig/export`，见 `:806-814`。但 `buildSalaryExportFilterLabel()` 在 `:816-821` 拼接的是 `方案名称` 和 `状态：this.schemeQueryParams.status || "全部"`，没有拼接 `socialType`。后端 `SysSalaryConfigController.export()` 调用 `salaryConfigService.selectSalarySchemeExportList(scheme)`，`SysSalarySchemeMapper.xml:43-48` 会按 `social_type = #{socialType}` 过滤，说明社保口径是真实导出条件。MySQL 当前两条薪资方案分别为“无社保”和“有社保”，社保口径筛选会改变导出结果。
- 影响：薪资方案导出包含档位、基本工资、工资合计等敏感薪资字段，确认框应该让管理员准确知道导出范围。当前用户选择“有社保”后，确认框仍会显示“状态：全部”，看不到“社保口径：有社保”；用户可能误以为导出的是全部方案，或反过来在没有意识到社保口径筛选仍生效的情况下导出不完整台账。多门店工资验收、薪资方案交接和外发文件审批时，确认文案和真实导出范围不一致会增加误导风险。
- 建议：薪资方案导出确认应和真实查询参数一致：展示“方案名称 / 社保口径 / 当前页或全部查询结果 / 敏感字段”，移除不存在的状态筛选；若后续补状态筛选，也要在页面、查询参数、后端过滤和确认文案中同步。导出工具建议对每个模块的确认字段做单元或快照测试，避免页面筛选项变更后确认文案继续引用旧字段。

### P3-275 调拨处理中导出确认只展示单号，漏掉真实生效的状态和类型筛选

- 现象：调拨处理中页已经接入统一导出确认，页面筛选区支持“单号 / 状态 / 类型”，导出请求也会提交这三个查询条件；但确认框的筛选说明只展示“筛选单号”和当前组织，不展示“状态”和“类型”。用户如果筛选了“待发货、部分发货、门店要货、异店调货”等条件，导出前最后一步看不到这些真实范围。
- 证据：前端 `erp-ui/src/views/inventory/transfer/index.vue:12-24` 提供 `queryParams.orderNo/status/transferType` 三个筛选，导出时 `handleExport()` 把 `{ ...this.queryParams }` 提交到 `inventory/transfer/export`，见 `:1118-1125`。但 `getTransferExportConfirmMessage()` 只拼接当前组织和 `orderNo`，见 `:1144-1151`，没有 `status` 或 `transferType`。后端 `InvTransferController.export()` 调用 `transferService.selectTransferList(transfer, resolveShopDeptId(request))`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java:153-160`；Mapper `InvTransferOrderMapper.xml:55-68` 会按 `order_no`、`status` 和 `transfer_type` 过滤。当前运行态 `inv_transfer_order` 有 `TF202606140001 / warehouse / partial_delivered` 和 `TF202606180001 / warehouse / received` 两个状态样本，状态过滤会改变导出范围。
- 影响：调拨处理中数据用于跨门店/仓库协作和对账，状态和类型是判断“待谁处理、是否还能发货/收货、是否属于异店调货”的关键条件。确认框只显示单号会让用户误以为没有其他筛选，或无法在导出前核对自己是否只导出了某一类状态；多账号多店铺验收时，调拨 Excel 的来源范围难以追溯，容易把漏导、少导或状态筛选残留误判成数据库缺单。
- 建议：调拨处理中导出确认应展示完整筛选摘要：当前组织、调拨单号、状态中文名、类型中文名和导出范围；`filterLabel` 不要再塞入“确认导出...”整段文案，避免统一确认框里重复出现标题。调拨记录页也应迁移到同一套确认摘要，确保处理中和归档记录的导出范围都能在确认框和操作日志中还原。

### P3-276 发货通知导出确认漏掉通知单号和客户筛选，状态也展示为英文码

- 现象：发货通知页筛选区支持“通知单号 / 销售单号 / 客户 / 状态”，导出请求会携带完整查询条件；但导出确认框只展示“状态”和“销售单号”，漏掉同样会影响结果的通知单号、客户筛选，并且状态直接展示 `pending/delivering/completed/cancelled` 这类英文码而不是页面上的中文状态。
- 证据：前端 `erp-ui/src/views/inventory/deliveryNotice/index.vue:5-20` 提供 `queryParams.noticeNo/salesOrderNo/customerName/status` 四个筛选，导出按钮在 `:234-237` 把 `{ ...this.queryParams }` 提交到 `inventory/deliveryNotice/export`。但 `getDeliveryNoticeExportConfirmMessage()` 在 `:258-264` 只拼接 `this.queryParams.status` 和 `this.queryParams.salesOrderNo`，没有 `noticeNo/customerName`，也没有调用 `statusLabel()` 转中文。后端 `InvDeliveryNoticeController.export()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvDeliveryNoticeController.java:80-87` 调用 `deliveryNoticeService.selectNoticeList(...)`；Mapper `InvDeliveryNoticeMapper.xml:60-68` 会按 `notice_no/sales_order_no/customer_name/status/shopDeptId/scopeDeptIds` 过滤。当前运行态 `inv_delivery_notice=0`、`inv_delivery_notice_detail=0`，所以本轮没有用存量单据复现导出文件，但页面参数和后端过滤链路已经能确认真实范围与确认文案不一致。
- 影响：发货通知是销售出库前的关键台账，通知单号和客户通常比销售单号更接近日常对账和客服沟通口径。用户如果按客户或通知单号筛选后导出，确认框看不到这些条件，容易误以为导出的是全部客户或全部通知；状态显示英文码也降低最后确认的可读性。多门店销售发货验收时，这会让导出范围和人工认知不一致，后续追溯 Excel 来源更困难。
- 建议：发货通知导出确认应展示完整筛选摘要：当前组织、通知单号、销售单号、客户、状态中文名和导出范围；状态统一复用 `statusLabel()`，并把通知单号、客户和组织范围写入操作日志或导出审计摘要。等正式销售/发货通知有数据后，应补一条按客户、通知单号、状态组合筛选后导出的回归用例。

### P3-277 固定资产配置和维修上报导出没有确认，资产金额与故障说明会直接下载

- 现象：固定资产配置页和固定资产维修上报页都有“导出”按钮，也都有店铺、商品/资产、状态等筛选条件；但点击导出会直接调用下载接口，没有任何确认框，也不会展示当前筛选范围、组织范围或导出字段风险。固定资产导出内容包含资产金额、资产单价、预计维修金额和故障说明等线下台账敏感信息。
- 证据：固定资产配置页导出按钮见 `erp-ui/src/views/oa/fixedAsset/config/index.vue:14`，筛选项为店铺、商品和状态，见 `:18-42`；`handleExport()` 在 `:378-380` 直接执行 `this.download("oa/fixedAsset/config/export", { ...this.queryParams }, ...)`，没有 `$modal.confirm` 或 `confirmExportAction`。维修上报页导出按钮见 `erp-ui/src/views/oa/fixedAsset/repair/index.vue:12`，筛选项为店铺、资产和状态，见 `:15-33`；`handleExport()` 在 `:273-275` 同样直接下载。后端 `OaFixedAssetConfigController.export()` 和 `OaFixedAssetRepairController.export()` 分别在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaFixedAssetConfigController.java:82-89`、`OaFixedAssetRepairController.java:65-72` 按当前查询条件导出。实体 `OaFixedAssetConfig` 对店铺、商品编码、商品名称、数量、资产单价、资产金额、状态标注 `@Excel`，见 `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetConfig.java:17-38`；`OaFixedAssetRepair` 对店铺、固定资产、预计维修金额、故障说明、状态标注 `@Excel`，见 `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetRepair.java:20-47`。当前运行态 `oa_fixed_asset_config=0`、`oa_fixed_asset_repair=0`，本轮没有实际下载文件，但按钮到后端导出的链路已经确认。
- 影响：固定资产配置和维修上报是店铺资产台账的一部分，导出文件会暴露资产金额、维修预算和故障描述；如果没有最后确认，管理员在空表或旧筛选残留状态下点击导出，会直接生成文件。多店铺场景下，用户也看不到这次导出是当前店铺、全部可见店铺，还是某个状态范围，后续很难追溯导出文件是否覆盖了正确资产范围。
- 建议：固定资产配置和维修上报导出应统一接入导出确认，展示当前组织/可见组织范围、店铺、商品或资产、状态中文名、导出记录数和敏感字段提示；空数据时应提示“当前筛选无记录，是否仍导出空模板/空文件”。导出操作日志建议保存筛选摘要和记录数，方便资产台账外发追溯。

### P3-278 表单构建上传组件默认使用第三方演示接口，生成代码会把文件提交到外部地址

- 现象：表单构建的“上传”组件默认 `action` 指向 `https://jsonplaceholder.typicode.com/posts/`，不是本系统的 `/file/upload` 或可配置的后端上传接口。用户把上传组件拖入表单后，页面预览和复制/导出的 Vue 代码都会沿用这个外部地址，右侧属性面板也没有默认提示“这是演示地址，请替换为内网接口”。
- 证据：上传组件默认配置在 `erp-ui/src/utils/generator/config.js:374-389`，其中 `action: 'https://jsonplaceholder.typicode.com/posts/'`、`auto-upload: true`、`name: 'file'`。渲染器 `erp-ui/src/utils/generator/render.js:111-123` 会把组件配置透传给 `el-upload`；生成器 `erp-ui/src/utils/generator/js.js:60-64` 会把该地址写入 `${vModel}Action`，模板生成器 `erp-ui/src/utils/generator/html.js:258-272` 再通过 `:action="${el.vModel}Action"` 绑定到 `el-upload`。当前运行态菜单中 `3 / 系统工具` 为 `status=1`、`visible=1`，但 `114 / 表单构建` 仍是 `status=0`、`visible=0`、`perms=tool:build:list`，且 `common / 普通角色`、`zjl / 总经理` 均持有该权限；这与 P3-54 的“半交付工具”边界问题叠加。
- 影响：上传控件是最容易承载身份证、合同附件、资产照片、报销凭证等敏感文件的组件。即使当前系统工具父菜单被停用，一旦后续恢复入口、测试人员通过组件页验证，或用户复制生成代码到业务页面而忘记改 `action`，文件就会被提交到第三方演示站点，既不经过本系统 `file:upload` 权限，也不会进入文件表、MinIO 存储、操作日志或删除闭环。多账号多店铺验收时，这会造成“页面能上传、数据库和文件中心没有记录”的错位。
- 建议：表单构建上传组件的默认 `action` 应改为本系统受权限保护的上传地址或留空并强制配置；属性面板要明确展示上传地址、权限要求、文件字段名和存储去向，复制/导出前对外部 URL 给出强提示或禁止导出。若表单构建不交付，应同步停用子菜单、权限和页面组件；若交付，应让上传组件复用 `FileUpload/ImageUpload` 的大小、类型、权限、失败重试和审计规则。

### P3-279 商品导入失败行仍按成功态提示并关闭弹窗

- 现象：商品导入接口会统计成功、更新、失败和待完善数量，但不管失败行数是多少都返回成功响应；前端收到成功响应后固定弹“成功”样式提示、关闭导入弹窗并刷新商品列表。若 Excel 全部失败，管理员看到的仍是成功态流程，只能在一条短暂 toast 里读到 `失败X条`。
- 证据：商品导入弹窗 `erp-ui/src/views/inventory/product/index.vue:433-464` 只提供文件选择、更新模式和确认导入；`doImport()` 在 `:920-936` 提交 `FormData` 后执行 `this.$modal.msgSuccess(res && res.msg ? res.msg : res || "导入成功")`、`this.importOpen = false`、刷新列表和分类。后端 `InvProductController.importData()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:99-122` 对茶叶 Excel 和普通模板都调用 `productService.importProduct(...)` 后 `return success(message)`；`InvProductServiceImpl.importProduct()` 在 `:120-178` 对空商品名、重复商品、异常行都会增加 `failCount` 并把错误拼到 `<br/>第N行...`，最终仍返回 `"成功导入" + successCount + "条，更新" + updateCount + "条，失败" + failCount + "条..."` 字符串，没有把全失败或部分失败变成业务失败/部分成功结构。当前运行态有 `inv_product=162`、`inv_product_category=30`，且 `admin`、`ck`、`zjl` 角色持有 `inv:product:import`，本轮未执行导入写入。
- 影响：商品主数据批量建档时，管理员难以区分“全部成功”“部分成功”“全部失败”和“成功但待完善”。导入弹窗关闭后，失败行无法在原上下文内修正或下载错误文件；列表刷新也可能让用户误以为数据已经完整入库。该问题不同于 P2-105 的分类匹配口径、P2-18 的待完善落库和 P1-30 的成本字段权限，本条聚焦导入结果状态和错误恢复流程。
- 建议：导入接口返回结构化结果 `{successCount, updateCount, failCount, completionCount, errors[]}`，`failCount > 0` 时前端使用“部分成功/失败”状态而不是成功 toast；全失败时保持弹窗打开并提供错误行下载或逐行错误列表，部分成功时明确展示已入库数量、失败数量、待完善数量和下一步处理入口。后端也应避免用 `<br/>` 拼接错误文本，统一返回纯文本字段。

### P3-229 调拨审批规则可物理删除已被历史审批实例引用的规则

- 现象：调拨审批配置页的删除动作只按规则 ID 物理删除审批规则和节点，不检查该规则是否已经被调拨审批实例或审批任务引用。当前运行态已经出现 `inv_transfer_approval_instance.rule_id=3` 指向不存在规则的孤儿历史实例，说明历史规则删除会让配置表和流程历史之间断开引用。
- 证据：前端 `erp-ui/src/views/inventory/transfer/rules.vue:585-591` 的删除确认只展示规则名称，确认后调用 `delTransferApprovalRule(row.ruleId)`；后端 `InvTransferApprovalRuleController.remove()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java:66-72` 调用 `ruleService.deleteRuleById(...)`。服务实现 `InvTransferApprovalRuleServiceImpl.deleteRuleById()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java:95-101` 只校验规则可见，然后先删 `inv_transfer_approval_node`、再删 `inv_transfer_approval_rule`；`InvTransferApprovalRuleMapper.xml:135-137` 是物理删除，没有查询 `inv_transfer_approval_instance/task` 引用，也没有外键约束。MySQL 当前 `inv_transfer_approval_rule=1`、`inv_transfer_approval_instance=2`、`inv_transfer_approval_task=2`，其中 `orphan_instances=1`；实例 `3` 保存 `rule_id=3 / rule_name=门店要货顺序审批 / rule_snapshot=...`，但当前规则表已无 `rule_id=3`。
- 影响：审批实例虽然保留了规则快照，但配置页无法再告诉管理员“这条历史调拨当时命中了哪条可维护规则、规则为何已删除、删除人是谁、是否仍有同类规则”。如果未来删除的是有待审批任务的规则，配置人员可能以为规则已下线，但运行中的调拨实例仍按旧快照继续流转；排查“为什么这张调拨走了旧审批链”时，只能查快照 JSON 和日志，页面没有清晰入口。
- 建议：调拨审批规则删除前先统计历史实例、运行中实例和待审批任务；有运行中实例或待审批任务时禁止删除，只允许停用。只有无引用规则才允许物理删除；有历史引用但无运行中任务的规则应优先软删除或归档，并在删除确认中展示引用数量、最近调拨单号和影响说明。历史实例详情应能展示规则已删除状态和原规则快照。

### P3-230 系统参数键名只有服务层唯一校验，数据库缺少唯一约束

- 现象：系统参数页和后端会把 `config_key` 当成运行配置唯一键使用，但唯一性只靠新增/编辑接口中的服务层查询拦截，`sys_config` 表本身没有 `config_key` 唯一约束。当前运行态 8 条参数暂时没有重复键名，但数据库层无法阻止并发写入、直接 SQL、导入脚本或异常迁移写出重复 `config_key`。
- 证据：参数编辑弹窗 `erp-ui/src/views/system/config/index.vue:162-166` 允许维护参数键名和值；`SysConfigController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:96-122` 只调用 `checkConfigKeyUnique` 后保存。服务层 `SysConfigServiceImpl.selectConfigByKey()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java:62-78` 会先按 `sys_config:` + `configKey` 读取 Redis，未命中时按键名查库并缓存；`checkConfigKeyUnique()` `:192-200` 也只查一条。Mapper `erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml:67-70` 使用 `where config_key = #{configKey} limit 1`，表结构当前 `SHOW INDEX FROM sys_config` 只有 `PRIMARY(config_id)`，没有 `config_key` 唯一索引；只读复核 `COUNT(*)=8 / COUNT(DISTINCT config_key)=8`，说明现状干净但缺数据库兜底。
- 影响：`sys.account.registerUser`、`sys.account.chrtype`、`sys.account.initPasswordModify`、`sys.account.passwordValidateDays`、`sys.user.initPassword` 等参数会直接影响注册、密码规则、初始密码和改密提醒。一旦同一个键名出现多行，参数列表会显示多条配置，运行态却只按 `limit 1` 或 Redis 中最后加载的值生效，管理员看到的表格、缓存刷新结果和实际登录/注册行为可能不一致，多账号验收会很难判断到底是哪一条参数在控制系统。
- 建议：为 `sys_config.config_key` 增加数据库唯一约束，迁移前先跑重复键名巡检并给出清理脚本；新增/编辑继续保留友好的服务层提示，但并发或脚本写入必须由唯一索引兜底。内置参数建议锁定键名，缓存加载时如果发现重复 key 应告警并拒绝覆盖，参数列表增加“重复键名巡检”或健康检查入口。

### P3-231 个人中心修改密码成功后仍保留旧密码和新密码输入值

- 现象：个人中心“修改密码”提交成功后只弹出“修改成功”，表单中的旧密码、新密码、确认密码不会清空，也不会自动退出或跳转重新登录。三个输入框都启用了 `show-password`，如果用户曾点击显示密码，成功后页面仍可能继续显示或保留这三段密码内容。
- 证据：修改密码表单 `erp-ui/src/views/system/user/profile/resetPwd.vue:3-10` 绑定 `user.oldPassword/newPassword/confirmPassword`，并给三个 `el-input` 都设置 `type="password" show-password`；提交成功逻辑在 `:56-61` 只调用 `updateUserPwd(...)` 后 `this.$modal.msgSuccess("修改成功")`，没有 `resetFields()`、清空 `this.user`、关闭 tab 或重新登录。后端 `SysProfileController.updatePwd()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:111-145` 保存成功后只更新当前 token 缓存并返回 success，也不会指示前端清理表单或重新认证。本轮未提交改密，只做源码和运行态密码策略参数只读复核。
- 影响：门店共用电脑或管理员代操作场景下，用户改密成功后如果离开页面，浏览器内存和当前表单仍保留旧密码与新密码；旁人可通过页面、浏览器调试或误点“显示密码”看到敏感输入。用户也可能误以为还没保存而重复点击保存，触发“新密码不能与旧密码相同”之类的二次错误，改密流程反馈不干净。
- 建议：修改密码成功后立即清空三个密码字段并重置校验状态；如果安全策略要求重新登录，应提示“密码已修改，请重新登录”并清理当前会话。即使不强制退出，也应清空表单、关闭显示密码状态，并在页面说明其他设备会话处理策略，与 P2-86 的会话失效建议保持一致。

### P3-232 调拨记录详情只展示发货批次头，不展示批次内商品明细

- 现象：调拨记录页的详情弹窗有“发货与收货批次”表格，但只展示批次号、状态、发货人、发货时间、收货人和收货时间；不展开每个批次下的商品、本批发货数量、本批收货数量。处理中调拨页的详情弹窗已经有批次展开明细，两处同一调拨详情接口的展示深度不一致。
- 证据：调拨记录页 `erp-ui/src/views/inventory/transfer/records.vue:97-117` 的批次表只渲染 `shipmentRows` 的批次头字段，`:152-154` 虽然把 `detail.shipments` 作为数据源，但没有 `type="expand"` 或子表展示 `scope.row.details`。处理中调拨页 `erp-ui/src/views/inventory/transfer/index.vue:265-289` 对同一 `detail.shipments` 使用展开列，子表展示商品、本批发货、本批收货。后端 `InvTransferServiceImpl.getTransferDetail()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:237-247` 会为每个 shipment 设置 `details`；运行态当前 `TF202606180001` 对应 `TS202606180001`，`inv_transfer_shipment_detail` 有商品“清香铁观音”发货 7、收货 7，`TF202606140001` 的 `TS202606180002` 也有发货 1、收货 0 的待收货明细。
- 影响：调拨记录是完成或归档后的对账入口。多商品、多批次或部分发货场景下，只看批次头无法确认每个批次发了哪些商品、各发了多少、收了多少；用户只能把上方总要货明细和批次头时间人工拼在一起，无法直接复核“这批 TS 单对应哪些商品明细”。这会放大 P2-14 的审批/状态轨迹缺口和 P3-131 的库存流水批次来源缺口。
- 建议：调拨记录详情的批次表应与处理中调拨详情保持一致，提供展开行或内嵌子表，展示批次内商品、编码、计划数量、本批发货、本批收货、单位、差异数量和必要的成本脱敏状态。导出调拨记录时也应支持批次明细导出，至少让 `TF` 调拨单、`TS` 发货批次和商品明细能在同一台账中对齐。

### P3-179 选择组织预览模式会把演示组织写入真实会话上下文

- 现象：选择组织页支持 `/select-shop?preview=1` 预览模式，并在页面内写死了“云岫茶室 / 青炉茶社”等演示组织。当前开发环境路由守卫会把这个预览页加入免登录/免权限白名单；已登录用户进入预览模式后点击“进入系统”，页面会把演示门店或仓库写入真实 `sessionStorage`，并标记为已验证组织上下文。随后普通业务 API 请求会自动携带这个演示 `Dept-NumId`。
- 证据：路由守卫 `erp-ui/src/permission.js:16-39` 把 `/select-shop?preview=1` 识别为 `isAllowedLocalPreview`，只要求 `NODE_ENV !== 'production'`；有 token 时 `:91-100` 会先判断白名单并直接 `next()`。选择组织页 `erp-ui/src/views/select-shop/index.vue:186-206` 内置 `1001/1002/3001/2001/2002/3002` 等演示组织，`:253-258` 在预览模式下直接使用这些数据，`:282-285` 会自动选中第一个叶子节点，`:302-318` 确认时仍调用 `setSelectedDept(...)`。`shopContext.js:136-156` 会把该组织写入 `selected_dept_id/name/type`，且门店/仓库类型会写 `selected_dept_validated=1`；请求拦截器 `erp-ui/src/utils/request.js:36-41` 之后会把它作为 `Dept-NumId` 请求头发送。MySQL 复核运行态 `sys_dept` 与 `sys_user_shop` 中这些演示 `dept_id` 均不存在。
- 影响：在本地电脑端审计或演示环境中，测试人员可以把当前账号切到数据库不存在的“云岫茶室 总店”等组织，顶部组织名、工作台上下文和后续接口请求会出现前端看似已选中、后端返回空数据/无权限/组织不存在的错位。多账号多店铺验收最依赖组织上下文准确，预览数据进入真实会话会污染复测结论，尤其容易把“账号无数据”误判成“当前门店无业务数据”。如果构建环境变量误配为非生产，这个入口还可能出现在类生产部署中。
- 建议：预览模式不要写入真实 `shopContext`；使用独立的 `preview_selected_dept_*` 存储或只在纯静态预览页面内消费，不允许真实 API 请求带演示 `Dept-NumId`。已登录用户进入预览模式前应提示会切换到演示态，退出时清理预览上下文；生产构建应彻底关闭预览白名单和演示组织数据。多账号验收脚本开始前应先清理 `selected_dept_*` 并用 `/system/dept/shop-tree` 重新验证组织。

### P3-180 用户列表不能按最后登录或从未登录筛选，账号活跃度只能逐个详情或离线导出核对

- 现象：用户管理页已经有账号基础配置状态筛选，但没有最后登录时间、最后登录 IP、从未登录、长期未登录等活跃度筛选或列表列。管理员想清理长期不用账号、核对门店共用账号是否近期登录，只能逐个点击用户名打开详情抽屉，或导出后在 Excel 里离线筛选。
- 证据：运行态 `sys_user` 有 `login_ip/login_date/pwd_update_date`，登录链路会通过 `SysUserController.recordlogin -> SysUserServiceImpl.updateLoginInfo -> SysUserMapper.updateLoginInfo` 更新最后登录 IP 和时间；当前 25 个有效账号中 23 个有 `login_date/login_ip`，启用账号里有 2 个从未登录、1 个超过 7 天未登录。用户列表搜索区只有用户名称、手机号码、状态、配置状态和创建时间，见 `erp-ui/src/views/system/user/index.vue:8-31`；表格列配置只有用户编号、用户名称、用户昵称、部门、手机号、状态、配置状态和创建时间，见 `:65-98`、`:260-268`。详情抽屉 `erp-ui/src/views/system/user/view.vue:101-113` 会展示最后登录 IP/时间，`SysUser` 也把这两个字段标为导出字段，但 `SysUserMapper.selectUserList` 只支持 `userName/status/phonenumber/postId/setupStatus/deptId/createTime` 等条件，没有按登录时间或空登录时间筛选。
- 影响：多账号多店铺上线后，账号治理不只看“是否有角色/组织授权”，还要看账号是否真实使用、是否长期闲置、是否有异常登录 IP。当前页面无法直接拉出从未登录、超过 N 天未登录或近期从陌生 IP 登录的账号，停用、回收和复核都依赖人工逐条点详情或导出后处理；门店公用电脑和离职账号排查容易漏掉。
- 建议：用户列表增加“最后登录时间 / 最后登录 IP / 从未登录 / N 天未登录”列和筛选，默认可把登录列放入列设置中隐藏但可勾选；后端 `selectUserList` 支持 `loginDateBegin/loginDateEnd/neverLogin/inactiveDays/loginIp` 等条件并可排序。用户详情里可增加最近登录日志入口，停用或删除账号前也展示最近登录时间、在线会话和组织授权摘要。

### P3-181 用户详情抽屉把昵称标成“用户名称”，和列表、导出里的账号/昵称口径不一致

- 现象：用户列表中“用户名称”列展示的是登录账号 `userName`，旁边另有“用户昵称”列；点击账号进入详情抽屉后，详情第一项也叫“用户名称”，但实际展示的是 `nickName`，后面才用“登录账号”展示 `userName`。同一个字段在列表、详情和导出里使用了不同中文口径。
- 证据：用户列表 `erp-ui/src/views/system/user/index.vue:61-66` 中“用户名称”绑定 `scope.row.userName`，“用户昵称”绑定 `prop="nickName"`；详情抽屉 `erp-ui/src/views/system/user/view.vue:7-10` 的“用户名称”展示 `info.nickName`，`:35-39` 的“登录账号”展示 `info.userName`。实体导入/导出注解中 `SysUser.userName` 名称是“用户账号”，`nickName` 名称是“用户昵称”，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:34-40`；Mapper 也明确把 `user_name` 映射到 `userName`、`nick_name` 映射到 `nickName`，见 `SysUserMapper.xml:7-12`。运行态当前 25 个有效账号中 19 个 `user_name <> nick_name`，例如 `admin / 管理员`、`ry / ERP`、`dushuai / 杜帅`。
- 影响：多账号排查、分配角色、店铺授权、重置密码和删除账号时，管理员会在列表看到“用户名称=账号”，进入详情后又看到“用户名称=昵称”，容易把登录账号和显示名混在一起。账号和昵称不一致是常态，尤其门店员工可能用中文昵称，错认字段会增加授权、停用或重置密码对象选错的风险。
- 建议：统一字段命名：`userName` 在电脑端统一叫“登录账号”或“用户账号”，`nickName` 统一叫“用户昵称”或“显示名称”。用户列表、详情抽屉、搜索框、导出确认和 Excel 表头要使用同一套文案；详情第一项应改为“用户昵称”，列表“用户名称”建议改为“登录账号”，减少账号治理场景的歧义。

### P3-182 用户列表隐藏 admin 行内删除，但批量选择仍可选中超级管理员并到提交后才失败

- 现象：用户管理列表对 `userId=1` 的超级管理员隐藏了行内“修改 / 删除 / 更多”操作，看起来不能操作 admin；但表格左侧复选框没有禁用 admin 行，顶部批量“删除”按钮只要任意选中用户就可点击。选中 admin 后确认框只提示“确认删除选中的 N 个用户吗？”，没有提前说明其中包含受保护账号。
- 证据：前端 `erp-ui/src/views/system/user/index.vue:58-59` 的选择列是普通 `el-table-column type="selection"`，没有 `:selectable` 或行级禁用逻辑；`:84-96` 只在操作列用 `v-if="scope.row.userId !== 1"` 隐藏 admin 行内操作。顶部删除按钮 `:45` 只依赖 `multiple`，`handleSelectionChange()` 只保存 `selection.map(item => item.userId)`，`:512-525` 批量删除确认文案只展示选中数量。后端 `SysUserController.remove()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:331-338` 先拦截当前用户 ID，`SysUserServiceImpl.deleteUserByIds()` 在 `:490-496` 再逐个调用 `checkUserAllowed(new SysUser(userId))`；`checkUserAllowed()` 会对 `userId=1` 或带 admin 角色的用户抛“不允许操作超级管理员用户”。运行态当前 `sys_user` 中 `user_id=1 / admin / 管理员` 有效存在，本轮未调用删除接口。
- 影响：前端把 admin 行内操作藏起来，但批量选择仍给了一个可提交的删除流程，管理员要到确认之后、后端报错时才知道这批选择包含不可删对象。如果同时选了 admin 和普通测试账号，整个批量删除会失败，用户需要回到列表重新识别并取消 admin；多账号清理或导入测试账号回收时，这会制造误操作和返工。
- 建议：选择列对 `userId=1`、当前登录用户和其他受保护账号使用 `selectable` 禁选，并用 tooltip 或状态标签说明“受保护账号不可删除”；批量删除确认框列出前几条账号摘要，并在提交前过滤或阻断受保护用户。后端继续保留 `checkUserAllowed` 兜底，但前端不要把必然失败的批量操作暴露给管理员。

### P3-183 用户导出台账不包含角色、岗位、店铺/仓库授权和配置状态，无法离线完成账号盘点

- 现象：用户管理页已经在列表侧引入了配置状态、角色数量和店铺/仓库授权数量，用于判断账号是否配置完整；但“导出用户数据”仍只基于 `SysUser` 的 Excel 注解导出用户基础资料、部门和最后登录信息，不包含角色、岗位、店铺/仓库授权、默认组织、配置状态或角色/组织数量。
- 证据：`SysUserController.export()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:96-104` 调 `userService.selectUserList(user)` 后直接 `new ExcelUtil<SysUser>(SysUser.class)` 导出。`SysUser` 只有 `userId/userName/nickName/email/phonenumber/sex/status/loginIp/loginDate/dept.deptName/dept.leader` 等字段有 `@Excel`，`roles/roleIds/postIds/roleCount/shopScopeCount/setupStatus` 没有导出注解，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:26-108`。列表 SQL 已计算 `role_count/shop_scope_count/setup_status`，见 `SysUserMapper.xml:63-87`；前端也提供“配置状态”筛选，见 `erp-ui/src/views/system/user/index.vue:20-24`。运行态当前有 25 个有效用户、25 条 `sys_user_role`、18 条 `sys_user_post`、43 条 `sys_user_shop`，样本中 `admin/ry` 各有 7 个组织授权，`user_id=103 / 123` 角色数 1 但组织授权数 0。
- 影响：多账号多店铺验收最需要一张离线台账核对“账号 -> 角色 -> 岗位 -> 门店/仓库 -> 默认组织 -> 是否完整”。当前导出只能看到基础账号资料和部门，管理员导出后仍要再进角色分配页、店铺授权页或直接查数据库才能完成盘点；如果用这份 Excel 做交接或审计，会漏掉无组织授权、无岗位、超范围多门店授权等关键风险。
- 建议：提供“账号配置台账”导出，至少包含角色名称/角色键、岗位、授权门店/仓库名称与数量、默认组织、配置状态、最后登录时间和是否受保护账号；字段较多时可拆成用户基础表、用户角色表、用户岗位表、用户组织授权表四个 Sheet。导出确认中明确这是账号治理台账，并按账号管理权限控制下载。

### P3-184 用户列表只隐藏 admin 行内操作，顶部修改和状态开关仍可进入受保护账号失败流程

- 现象：用户管理列表对 `userId=1` 的超级管理员隐藏了行内“修改 / 删除 / 更多”操作，但同一行仍显示状态开关；如果通过复选框选中 admin，顶部“修改”按钮也会按普通单选流程打开编辑弹窗。P3-182 已记录批量删除问题，本条聚焦顶部修改和状态启停两个仍可进入的失败入口。
- 证据：前端 `erp-ui/src/views/system/user/index.vue:84-96` 只在操作列用 `v-if="scope.row.userId !== 1"` 隐藏 admin 行内按钮；顶部“修改”按钮在 `:41-42` 只受 `single` 控制，`handleUpdate()` 在 `:433-446` 直接使用 `row.userId || this.ids` 拉取详情并打开编辑弹窗，没有受保护账号前置判断。状态列 `:69-72` 对所有行渲染 `el-switch`，`handleStatusChange()` 在 `:333-342` 确认后调用 `changeUserStatus(row.userId, row.status)`。后端编辑和状态接口分别在 `SysUserController.edit()` 的 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:301-323`、`changeStatus()` 的 `:364-372` 调用 `userService.checkUserAllowed(user)`；`SysUserServiceImpl.checkUserAllowed()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:229-245` 会对超级管理员用户抛“不允许操作超级管理员用户”。运行态当前 `user_id=1 / admin / 管理员 / status=0` 有效存在，本轮未调用修改或启停接口。
- 影响：页面一边用隐藏行内操作传达“admin 不可维护”，一边又允许管理员通过工具栏修改或状态开关进入必然失败的流程。账号治理时，用户会先填表、确认启停或等待接口报错，才知道该账号受保护；这和 P3-154 的自停/在线 token 风险、P3-182 的批量删除失败一起，说明用户列表缺少统一的保护账号交互模型。
- 建议：前端集中封装 `isProtectedUser(row)`，对超级管理员、当前登录用户和安全管理员等受保护账号禁用选择、顶部修改、状态开关、重置密码、角色分配和店铺授权等入口，并用状态标签或 tooltip 说明原因；后端继续保留 `checkUserAllowed` 作为兜底。状态启停还应和 P3-154 建议联动，补充当前用户不可停用和停用后清理在线会话。

### P3-185 店铺授权页入口使用独立 userShop 权限，但首屏用户列表依赖用户管理列表权限

- 现象：菜单树把“店铺授权 / 店铺配置查询 / 店铺配置修改 / 店铺配置列表”拆成 `system:userShop:*` 独立权限，页面保存按钮也只判断 `system:userShop:edit`；但页面创建时先调用用户管理的 `listUser()` 加载左侧用户列表。只给店铺授权权限、不给用户管理列表权限的账号会看到店铺授权菜单或组织树权限正常，但左侧用户列表接口会被 `system:user:list` 拦截，导致无法选择用户和保存授权。
- 证据：店铺授权页 `erp-ui/src/views/system/shop/index.vue:104-108` 导入 `listUser` 和 `userShop` API，`:154-158` 在创建时调用 `getList()`、`getPostOptions()`、`getShopTree()`；`getList()` 在 `:169-178` 使用 `listUser(this.queryParams)` 获取左侧用户列表。`listUser()` 对应 `GET /system/user/list`，见 `erp-ui/src/api/system/user.js:4-11`；后端 `SysUserController.list()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:83-93` 要求 `@RequiresPermissions("system:user:list")`。店铺授权自己的接口则在 `SysUserShopController` 中分别使用 `system:userShop:list/query/edit`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java:41-91`。运行态菜单中 `181/1190/1191/1192` 是独立 `system:userShop:*` 节点，`100/1000` 是用户管理 `system:user:list/query` 节点；当前 `zjl` 同时持有两组权限，所以现有总经理账号暂不触发，但权限树允许后续只勾选店铺授权节点。
- 影响：多账号多店铺实施时很容易单独创建“店铺授权员/组织授权员”角色，只授予 `system:userShop:*`。按当前依赖，这个角色页面入口、树和保存权限看似完整，实际首屏用户列表会失败，保存按钮也因为没有 `currentUser` 而不可用。管理员会把它误判为页面坏了或数据为空，而不是权限配置缺了一项；权限最小化也被迫额外授予完整用户列表权限。
- 建议：店铺授权页应使用专门的授权候选用户接口，例如 `GET /system/user/shop/candidates`，权限归 `system:userShop:list/query`，只返回授权所需的账号、昵称、状态、部门和组织摘要；或者在菜单/角色配置中明确把 `system:user:list` 作为店铺授权页依赖并自动联动。后端候选接口仍要复用用户数据范围和组织授权范围校验，避免为了页面可用性扩大用户管理权限。

### P3-186 用户配置状态只统计角色和组织授权，缺少岗位完整性判断

- 现象：用户管理页的“配置状态”筛选和标签只有“未分配角色 / 未授权店铺/仓库 / 已完成”三种状态，后端 `setupStatus` 也只计算角色数和店铺/仓库授权数。账号即使没有任何岗位，只要有角色和组织授权，就会被标成“已完成”，不能在列表中直接筛出。
- 证据：前端配置状态筛选只提供 `missingRole/missingShopScope/complete`，见 `erp-ui/src/views/system/user/index.vue:19-24`；列表列只展示用户、部门、手机号、状态、配置状态和创建时间，不展示岗位，见 `:61-80`；`setupStatusLabel()` 在 `:344-349` 也只根据 `roleCount/shopScopeCount` 判断。后端 `SysUserMapper.selectUserList` 在 `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:63-127` 只计算 `role_count`、`shop_scope_count` 和 `setup_status`，没有联表 `sys_user_post` 或输出 `post_count`。`SysUser` 只有 `postIds/postId`，没有 `postCount` 或 `missingPost` 配置状态字段，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:86-108`。运行态当前 25 个有效账号中，按现有规则 `complete=24`、`missingShopScope=1`；其中 7 个 `complete` 账号岗位数为 0，样本包括 `codex_184407_multi`、`dushuai`、`qa144822w`、`qa_audit_62201`，均有角色和组织授权但无岗位。
- 影响：岗位在本系统里会影响薪资、考勤、合同和调拨审批候选人解释。管理员使用“配置状态=已完成”做账号交付或巡检时，会把无岗位账号当成配置完整；后续薪资绑定、审批候选、岗位台账或人事口径出问题时，只能再查用户编辑弹窗或数据库。P3-183 覆盖的是导出台账不含岗位，本条记录的是页面实时配置状态本身漏掉岗位维度。
- 建议：把岗位纳入账号配置完整性模型：列表 SQL 增加 `post_count`，前端增加“未分配岗位”状态和筛选；如果岗位不是所有账号必填，应按角色、部门类型或账号类型定义必填规则，并在状态标签里区分“可选未配置”和“必填缺失”。账号配置台账和导出也要同步使用同一套完整性口径。

### P3-187 用户详情抽屉不展示店铺/仓库授权和配置状态，账号画像不能在详情页闭环

- 现象：用户列表点击账号会打开“用户信息详情”抽屉，抽屉已展示基础资料、岗位、角色、创建/更新信息和最后登录；但不展示该用户的店铺/仓库授权、授权数量、默认组织或配置状态。管理员看单个账号详情时，无法直接判断账号能管理哪些门店/仓库、是否缺组织授权、是否属于多组织账号。
- 证据：详情抽屉 `erp-ui/src/views/system/user/view.vue:5-123` 只渲染基本信息、岗位、角色、创建/更新、最后登录和备注；`:129-168` 只调用 `getUser(userId)`，没有调用 `getUserShop` 或 `batchUserShop`。`getUser()` 对应 `GET /system/user/{userId}`，见 `erp-ui/src/api/system/user.js:14-20`；后端 `SysUserController.getInfo()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:246-263` 只返回 `data/postIds/roleIds/roles/posts`，店铺授权需要走独立 `GET /system/user/shop/{userId}`，见 `erp-ui/src/api/system/userShop.js:11-16`。运行态当前 `sys_user_shop=43`，样本 `admin/ry` 各有 7 个授权组织，`12345` 有 4 个，`ux_store_multi`、`codex_184407_multi` 等有 2 个；这些授权都无法在用户详情抽屉中看到。
- 影响：多账号多店铺排查时，详情页应是单个账号画像入口。当前管理员能看到账号是谁、岗位和角色是什么，但最关键的经营组织范围缺席，仍要跳转“店铺授权”页或查数据库核对。对多门店账号、缺授权账号和受保护账号，详情页无法给出“配置完整/缺组织/授权过宽”的即时判断，和 P3-183 的离线台账缺口、P3-186 的配置状态缺口一起削弱账号治理闭环。
- 建议：用户详情抽屉增加“账号配置”区块，展示配置状态、角色数量、岗位数量、授权门店/仓库数量、默认组织和授权组织摘要；多组织时支持展开全部或跳转到店铺授权页。后端可复用 `SysUserShopController.getInfo()` 或提供聚合详情 DTO，保证详情页、列表配置状态和账号配置台账使用同一套授权口径。

### P3-188 用户分配角色页不展示数据范围、状态和授权影响，勾选角色风险不可见

- 现象：用户“分配角色”副页面的角色表只显示角色编号、角色名称、权限字符和创建时间；页面会按角色状态禁选停用角色，但不展示状态，也不展示数据范围、备注、菜单数量、已授权用户数或本次变更影响。管理员只能根据角色名称和权限字符判断是否勾选，无法直观看到某个角色会带来“全部数据权限”还是本部门范围、会开放多少菜单。
- 证据：前端 `erp-ui/src/views/system/user/authRole.vue:19-35` 的角色表只有序号、选择框、角色编号、角色名称、权限字符和创建时间；`:103-105` 的 `checkSelectable(row)` 使用 `row.status === "0"` 判断能否勾选，但状态列没有渲染；`:108-114` 提交时也没有确认摘要。后端 `SysUserController.authRole()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:378-388` 返回 `roleService.selectRolesByUserId(userId)` 的角色列表；`SysRoleMapper.xml:24-31` 查询已包含 `data_scope/status/remark`，`SysRole` 也映射 `dataScope/status/remark/flag`。运行态角色差异很大：`zjl / 总经理` 为 `data_scope=1` 且绑定 228 个菜单，`dz / 店长` 绑定 76 个菜单、12 个用户，`yyjl / 运营经理` 绑定 90 个菜单；这些影响在分配角色页都不可见。
- 影响：多账号多店铺场景中，角色不是普通标签，而是菜单权限、数据范围和业务职责的组合。用户分配角色页缺少影响提示，管理员在给新账号或测试账号授权时容易把全量数据角色、空菜单测试角色或不合适的经营角色当成普通角色勾选；一旦保存，还会叠加 P2-125 的在线 token 不刷新问题，授权影响不能及时被页面解释或收口。
- 建议：用户分配角色页增加角色状态、数据范围、人读说明、菜单数量、已授权用户数和备注列；提交前展示本次新增/移除角色、数据范围变化和高风险角色提示。对于 `data_scope=1`、菜单数量过大、超级管理员或系统维护类角色，应要求二次确认或改走安全管理员流程，并把确认摘要写入权限变更审计。

### P3-189 角色授权用户页取消授权不识别最后一个角色，会把账号变成无角色状态

- 现象：角色“分配用户”副页面可以对单个用户取消当前角色授权，也可以批量取消选中用户的当前角色授权。确认文案只展示用户账号或选中人数，不提示这些用户是否只有当前这一个角色，也不提示取消后会没有菜单/业务入口。P3-20 已覆盖“用户侧分配角色页提交空角色集合”，本条记录的是角色侧单个/批量取消授权的另一条入口。
- 证据：前端 `erp-ui/src/views/system/role/authUser.vue:62-88` 的已分配用户表只展示用户名、昵称、邮箱、手机、状态、创建时间和取消按钮，没有角色数量或最后角色标识；`:176-195` 的单个/批量取消确认分别只提示“当前角色授权”和选中人数。API `erp-ui/src/api/system/role.js:86-101` 调用 `/system/role/authUser/cancel` 和 `/system/role/authUser/cancelAll`；后端 `SysRoleController.java:203-220` 只要求 `system:role:edit` 后调用 `deleteAuthUser/deleteAuthUsers`。`SysRoleServiceImpl.java:232-250` 的 `checkAuthUserScope` 只校验角色和用户数据范围，`:447-468` 随后直接删除绑定；`SysUserRoleMapper.xml:34-42` 的 SQL 是按 `user_id + role_id` 或 `role_id + userIds` 删除 `sys_user_role`，没有至少保留一个角色的校验。运行态当前 25 个启用账号都只有 1 个角色，分布为 `店长=12`、`仓库管理员=6`、`运营经理=3`、`超级管理员=1`、`实习生=1`、`驻店经理=1`、`总经理=1`。
- 影响：管理员在角色页清理成员时，可能一键取消一批只有当前角色的门店/仓库账号，导致这些账号仍处于启用状态但没有任何菜单和权限。多账号多店铺场景下，这会表现为“能登录但无功能可用”，和 P3-187 的详情页授权缺口、P3-188 的授权影响不可见、P2-125 的在线 token 延迟刷新叠加后，不容易在操作当下发现。
- 建议：角色侧取消授权前计算受影响用户的角色数量，单个和批量确认都要标记“取消后将无角色”的账号；启用账号如果业务上必须至少有一个角色，后端 `deleteAuthUser/deleteAuthUsers` 应禁止删到 0 角色，或要求选择替代角色/停用账号后再执行。已分配用户表也应增加角色数量、组织授权摘要和配置状态，避免管理员只按用户名批量操作。

### P3-190 角色列表只隐藏超级管理员行内操作，状态开关和顶部按钮仍可进入失败流程

- 现象：角色管理列表对 `roleId=1 / 超级管理员` 隐藏了行内“修改 / 删除 / 更多”操作，但同一行仍显示状态开关，也仍可通过复选框选中后点击顶部“修改”或“删除”。后端会拒绝超级管理员角色的修改、停用和删除，所以这些入口最终变成“页面允许进入、提交后失败”的受保护对象流程。
- 证据：前端 `erp-ui/src/views/system/role/index.vue:105-118` 的选择列和状态 switch 对所有角色渲染，`:126-153` 只在行内操作列用 `scope.row.roleId !== 1` 隐藏修改、删除和更多；顶部修改/删除按钮在 `:68-88` 只按是否单选/多选启用。`handleSelectionChange()` 在 `:461-465` 直接收集所有选中 `roleId`，`handleUpdate()` 会按选中的 `roleId` 拉取菜单树和角色详情，`handleDelete()` 在 `:604-618` 只展示选中数量确认。后端 `SysRoleController.edit/dataScope/changeStatus` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java:108-149` 会调用 `checkRoleAllowed`，删除路径 `SysRoleServiceImpl.deleteRoleByIds()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java:421-438` 也会调用 `checkRoleAllowed`；`checkRoleAllowed()` 在 `:190-207` 会根据 `role_key=admin` 抛出“不允许操作超级管理员角色”。运行态当前 `sys_role` 中 `role_id=1 / role_key=admin / status=0`，且只有 `admin` 一个成员。
- 影响：页面一边隐藏行内操作传达“超级管理员角色不可维护”，一边又让管理员通过状态开关和顶部按钮进入同一个受保护对象的失败路径。角色管理是权限根配置页，这类不一致会让操作员先打开弹窗、切换状态或确认删除后才知道不可操作；它和 P2-116 的超级管理员角色成员保护缺口、P3-104 的状态开关权限问题属于不同入口，说明受保护角色缺少统一的前端交互模型。
- 建议：角色列表应把超级管理员角色作为受保护对象统一处理：选择列禁选或勾选后提示原因，状态显示为只读标签，顶部修改/删除对受保护角色禁用，行内操作位显示“系统内置角色”说明。后端继续保留 `checkRoleAllowed` 兜底，但前端不要让管理员进入必然失败的维护流程；若未来支持维护最高权限角色，应改走独立安全管理员流程。

### P3-191 角色列表不展示数据范围、菜单数量和已授权用户数，权限矩阵风险只能逐个打开

- 现象：角色管理列表只显示角色编号、角色名称、权限字符、显示顺序、状态和创建时间，不展示数据范围、菜单数量、已授权用户数或是否为空权限角色。管理员需要逐个打开“修改”“数据权限”“分配用户”才能判断一个角色到底影响多少账号、开放多少菜单、是否拥有全部数据权限。
- 证据：前端 `erp-ui/src/views/system/role/index.vue:104-125` 的表格列没有 `dataScope`、菜单数量或用户数量；数据权限只在弹窗中显示，见 `:218-255`。后端角色列表查询 `SysRoleMapper.xml:22-31` 已返回 `data_scope`，但没有联查 `sys_role_menu` 或 `sys_user_role` 聚合数量；`SysRole` 的 Excel 字段包含角色编号、名称、权限、排序、数据范围和状态，但没有菜单数量、已授权用户数或组织范围摘要，见 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysRole.java:23-49`。运行态角色差异很大：`总经理/zjl` 为全部数据权限 `data_scope=1` 且有 228 个菜单、1 个用户；`店长/dz` 有 76 个菜单、12 个用户；`仓库管理员/ck` 有 49 个菜单、6 个用户；`实习生/茶艺师/店助/运营总监` 当前菜单数为 0 或用户数为 0，这些首屏都不可见。
- 影响：角色管理页是权限巡检的主入口，但首屏无法区分高风险全量数据角色、空菜单角色、无人使用角色和大规模授权角色。多账号多店铺检查时，管理员不能快速找出“全量数据权限 + 大菜单数”“有用户但无菜单”“有菜单但无人用”等治理对象，只能靠离线 SQL 或逐个进入子页面；这会降低权限盘点效率，也会让 P2-101 中的角色写权限过宽风险更难被发现。
- 建议：角色列表增加数据范围、人读说明、菜单数量、已授权用户数、启用用户数和空权限标识；高风险组合如 `data_scope=1`、菜单数量过大、用户数为 0、菜单数为 0 应用标签或筛选器提示。导出角色台账时也应包含这些聚合字段，方便离线权限矩阵审计。

### P3-192 部门编辑迁移父级不校验新父级状态，和新增部门规则不一致

- 现象：新增部门时后端会拒绝把新节点挂到停用父级下，但编辑部门时“上级部门”下拉来自排除自身/子节点后的部门列表，没有只保留启用父级；提交修改后服务层只重算 `ancestors` 和子级 `ancestors`，没有校验新父级是否启用。若存在停用公司、门店或仓库节点，管理员可把启用组织迁移到停用父级下，或者因为当前节点为启用状态触发祖先自动启用，造成“编辑迁移”和“新增”规则不一致。
- 证据：前端部门弹窗 `erp-ui/src/views/system/dept/index.vue:138-140` 使用 `treeselect` 选择上级部门；修改入口 `:365-378` 调用 `listDeptExcludeChild(row.deptId)` 生成候选父级，提交 `:381-390` 直接调用 `updateDept(this.form)`。接口 `erp-ui/src/api/system/dept.js:29-35` 调用 `/system/dept/list/exclude/{deptId}`；后端 `SysDeptController.excludeChild()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:77-83` 使用 `selectDeptList(new SysDept())`，而 `SysDeptMapper.xml:31-51` 默认只过滤 `del_flag`，只有传入 `status` 时才过滤状态。新增链路 `SysDeptServiceImpl.insertDept()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:325-335` 会校验父级存在且状态正常；编辑链路 `SysDeptController.edit()` 在 `:119-136` 只校验数据范围、名称唯一、自身父级和停用子部门，`SysDeptServiceImpl.updateDept()` 在 `:351-369` 只按新父级重算祖先并在当前部门启用时调用 `updateParentDeptStatusNormal()`，没有拒绝停用父级。运行态当前 `sys_dept` 的 11 个有效组织均为 `status=0`，因此本项是源码流程风险而非现有脏数据。
- 影响：组织树是用户主部门、店铺/仓库授权、组织选择、库存上下文和业务单据范围的基础。停用组织通常代表不能继续作为有效业务节点；编辑迁移却允许绕过新增规则，会让禁用节点继续承载启用子树，或在编辑启用部门时隐式重新启用上级节点，后续组织选择、授权树和业务范围解释会混乱。和 P2-38 的类型层级、P2-121 的停用影响预检是相邻但不同的规则缺口。
- 建议：部门编辑保存时和新增使用同一父级校验：新父级必须存在、在数据范围内、状态正常，并且组织类型层级合法；如确需把节点迁入停用父级，应先显式启用父级并展示影响范围。`list/exclude` 作为父级候选应默认过滤停用部门或标为不可选，后端不能靠前端过滤兜底。

### P3-193 岗位和字典类型选择框接口只要求登录，绕过系统基础资料查看权限

- 现象：岗位管理和字典管理页面本身分别需要 `system:post:list/query`、`system:dict:list/query` 等权限，但两个选择框接口 `/system/post/optionselect`、`/system/dict/type/optionselect` 只要求登录即可返回全量岗位或字典类型。相比之下，角色选择框 `/system/role/optionselect` 已要求 `system:role:query`，同类基础资料读取口径不一致。
- 证据：前端岗位选择框 API `erp-ui/src/api/system/post.js:12-17` 调用 `/system/post/optionselect`，字典类型选择框 API `erp-ui/src/api/system/dict/type.js:54-59` 调用 `/system/dict/type/optionselect`。后端 `SysPostController.optionselect()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysPostController.java:123-130` 标注 `@RequiresLogin` 后直接返回 `postService.selectPostAll()`；`SysPostMapper.xml:40-42` 的 `selectPostAll` 不过滤状态，且 `selectPostVo` 返回岗位编码、名称、状态和备注。`SysDictTypeController.optionselect()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictTypeController.java:124-132` 同样只要求登录并返回 `dictTypeService.selectDictTypeAll()`，`SysDictTypeMapper.xml:44-46` 不过滤状态。角色选择框作为对照，在 `SysRoleController.optionselect()` 中使用 `@RequiresPermissions("system:role:query")`。运行态当前 `sys_post=10`、`sys_dict_type=10` 且全部启用；除超级管理员绕过和 `zjl/ry` 等显式授权外，23 个启用业务账号没有有效 `system:post:list` 或 `system:dict:list` 权限。
- 影响：岗位编码会被调拨审批候选人、用户岗位、薪资和合同等链路引用；字典类型则是状态、筛选和标签的系统配置入口。页面权限隐藏了岗位/字典管理，但接口允许普通登录账号直连拿到完整基础配置，破坏最小可见范围，也会让“无系统基础资料权限”的业务角色仍能枚举内部岗位编码、字典编码和治理备注。若未来停用或新增敏感岗位/字典类型，选择框接口还会继续暴露全量状态。
- 建议：岗位选择框接口至少要求 `system:post:query` 或提供专用只读权限 `system:post:option`; 字典类型选择框要求 `system:dict:query` 或拆出字典只读权限。确需给业务页使用的候选接口应按业务场景返回最小字段、只返回启用项，并避免暴露备注等治理信息；角色、岗位、字典三类基础资料读取权限应统一口径。

### P3-235 岗位编码和名称唯一性只靠服务层校验，数据库没有唯一约束兜底

- 现象：岗位新增和修改会提示“岗位名称已存在 / 岗位编码已存在”，页面和服务层都把 `post_name/post_code` 当成唯一基础资料；但 `sys_post` 表只有 `post_id` 主键，没有 `post_code` 或 `post_name` 唯一索引。当前运行态 10 个岗位没有重复值，但并发请求、脚本导入或直接 SQL 仍可能写出重复岗位编码或名称。
- 证据：`SysPostController.add()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysPostController.java:77-88` 先调用 `checkPostNameUnique/checkPostCodeUnique`，修改接口同样在 `:96-112` 做唯一性判断；服务层 `SysPostServiceImpl.checkPostNameUnique/checkPostCodeUnique` 在 `:82-108` 只查一条记录后比较 `postId`，mapper 在 `SysPostMapper.xml:65-73` 使用 `where post_name=#{postName} limit 1` 和 `where post_code=#{postCode} limit 1`。MySQL `SHOW CREATE TABLE sys_post` 显示只有 `PRIMARY KEY (post_id)`，没有其他唯一索引；只读统计当前 `COUNT(*)=10 / COUNT(DISTINCT post_code)=10 / COUNT(DISTINCT post_name)=10`。调拨审批候选人解析又按岗位编码匹配，见 `InvTransferApprovalCandidateMapper.xml:5-15` 的 `p.post_code = #{postCode}`。
- 影响：岗位是用户岗位、调拨审批候选人、薪资档位和劳动合同岗位名称的基础口径。数据库允许重复后，页面看似阻止重复但底层数据可能被脚本或异常并发写坏；重复 `post_code` 会让调拨审批按一个编码匹配到多组岗位绑定用户，重复 `post_name` 会让薪资和合同里的岗位文字失去唯一解释。P2-99 已覆盖“业务角色可修改岗位且缺依赖预检”，本条补充的是唯一性缺少数据库兜底。
- 建议：迁移前先巡检并清理重复岗位编码/名称，然后至少为 `sys_post.post_code` 增加唯一约束；若业务要求岗位名称也唯一，则同步加 `post_name` 唯一约束，否则应明确“编码唯一、名称可重复”的页面文案。新增/编辑继续保留服务层友好提示，但数据库必须拦截并发、脚本和迁移写入；调拨审批和薪资等下游应优先引用稳定 `post_id` 或在岗位变更时做影响预检。

### P3-194 新增定时任务提交“正常”状态但后端强制暂停，新增结果缺少明确提示

- 现象：定时任务新增弹窗不展示“状态”字段，前端重置表单时却把 `status` 设为 `0 / 正常` 并随新增请求提交；后端保存新增任务时又无条件把状态改成 `1 / 暂停`。管理员点击“新增成功”后，实际得到的是暂停任务，需要再回到列表手动启用；页面没有说明“新任务默认暂停，需要启用后才进入调度”。
- 证据：前端新增/编辑弹窗的状态字段只在 `form.jobId !== undefined` 时展示，见 `erp-ui/src/views/monitor/job/index.vue:211-220`；`reset()` 在 `:337-348` 把 `status` 初始化为 `"0"`，`submitForm()` 在 `:439-454` 新增时直接调用 `addJob(this.form)`，API `erp-ui/src/api/monitor/job.js:20-27` 把表单完整提交到 `/schedule/job`。后端 `SysJobServiceImpl.insertJob()` 在 `erp-modules/erp-job/src/main/java/com/erp/job/service/SysJobServiceImpl.java:199-209` 保存前执行 `job.setStatus(ScheduleConstants.Status.PAUSE.getValue())`，随后 `SysJobMapper.xml:83-109` 把这个状态写入 `sys_job`。运行态当前 3 条定时任务均为 `status=1 / 暂停`。
- 影响：默认暂停本身可以作为安全策略，但当前交互没有把策略讲清楚，甚至前端提交值和后端落库值相反。管理员新增任务后看到“新增成功”容易误以为任务已经按 cron 运行；在考勤同步、日志清理、库存预警等场景恢复时，会出现“任务已创建但没人知道还没启用”的交付误解。它和 P2-40 的“停用后仍可执行一次”、P3-101 的“执行一次权限复用”是同一页面的不同状态语义问题。
- 建议：新增任务弹窗明确显示“新任务默认暂停，保存后需手动启用”提示，或提供受控的“保存后立即启用”选项并二次确认；前端不要提交与后端强制规则相反的 `status=0`。新增成功提示也应区分“已保存为暂停任务”和“已启用调度”，列表首屏可对新建暂停任务给出下一步启用入口。

### P3-195 系统参数列表和导出明文展示初始密码等敏感配置值

- 现象：参数设置页把所有 `config_value` 当作普通“参数键值”列展示，并在导出实体中同样标注为 Excel 字段。当前运行态 8 条参数里包含 `sys.user.initPassword=123456`、注册开关、密码有效期、密码字符范围和登录黑名单配置；具备参数列表或导出权限的业务角色可以直接在页面或 Excel 中看到这些值。P2-111 已记录“普通登录账号可按 key/id 读取参数”的接口权限问题，本条记录的是参数管理页本身缺少敏感值掩码和导出分级。
- 证据：前端参数表格 `erp-ui/src/views/system/config/index.vue:105-106` 直接渲染 `label="参数键值"` / `prop="configValue"`，编辑弹窗 `:164-166` 也把参数值作为普通 textarea。后端列表和导出共用 `SysConfigMapper.xml` 的 `selectConfigVo`，其中直接查询 `config_value`；`SysConfigController.list/export` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:40-59` 分别返回列表和导出 Excel。实体 `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java:30-31` 对 `configValue` 标注 `@Excel(name = "参数键值")`。当前 MySQL 复核 `sys_config` 包含 `sys.user.initPassword=123456`，`common / 普通角色` 与 `zjl / 总经理` 均持有 `system:config:list/query/export` 三类权限。
- 影响：系统参数页本应是配置治理入口，但当前把初始密码和安全策略值当作普通台账字段暴露。多账号多店铺场景下，拥有参数查看或导出权限的非安全管理员可以下载初始密码和账号策略，再结合用户导入弱初始密码、首次改密可取消、业务角色持有用户管理权限等问题，扩大默认密码和安全策略被滥用的风险。即使保留参数页权限，也不代表所有参数值都应在列表和导出中明文出现。
- 建议：给参数增加敏感级别或按 `configKey` 建保护清单，列表默认对初始密码、密钥、黑名单、密码策略等值做掩码，详情查看和导出敏感值使用独立权限与二次确认；导出应支持“脱敏台账”和“完整配置备份”两种模式，完整备份只给安全管理员并写入审计。长期建议把初始密码改成一次性生成或不可回读的临时密码策略，而不是普通可读配置值。

### P3-196 内置系统参数可修改键名，数据库配置与运行代码契约会被改断

- 现象：当前 8 条系统参数全部标记为 `config_type=Y / 系统内置`，后端删除时会拦截内置参数，但编辑弹窗仍允许修改“参数键名”，后端保存也只校验唯一性。运行代码却按固定字符串读取 `sys.account.registerUser`、`sys.account.chrtype`、`sys.account.initPasswordModify`、`sys.account.passwordValidateDays`、`sys.user.initPassword` 等 key；一旦管理员把内置参数 key 改名，数据库里仍显示一条“内置参数”，但登录、注册、改密提醒、密码策略或导入初始密码会读不到原 key。
- 证据：参数编辑弹窗 `erp-ui/src/views/system/config/index.vue:159-166` 对 `form.configKey` 使用普通可编辑输入框，没有按 `configType=Y` 禁用。`SysConfigController.edit()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:112-122` 只调用 `checkConfigKeyUnique` 后更新；`SysConfigServiceImpl.updateConfig()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java:116-129` 发现 key 变化时删除旧缓存并写入新 key，没有阻止内置 key 改名。删除链路 `deleteConfigByIds()` 在 `:138-146` 才会检查 `configType=Y` 并抛出“内置参数不能删除”。运行代码中，注册开关在 `SysUserController.register()` `:154-160` 固定读取 `sys.account.registerUser`，密码字符范围在 `SysUserController.getSysAccountChrtype()` `:209-217` 和 `SysLoginService.PASSWORD_POLICY_CONFIG_KEY` 固定读取 `sys.account.chrtype`，导入初始密码在 `SysUserServiceImpl.importUser()` `:533-536` 固定读取 `sys.user.initPassword`。当前 MySQL 复核 8 条参数均为 `config_type=Y`，`common / 普通角色` 与 `zjl / 总经理` 均持有 `system:config:edit`。
- 影响：内置参数 key 是代码和数据库之间的运行契约，不是普通展示名称。页面允许改 key 会让管理员以为只是整理参数编码，实际可能让注册开关回到默认关闭、密码策略回到默认任意字符、初始密码读取为空或提醒策略失效。多账号多店铺场景下，这类问题会表现为“参数页显示已配置，但登录/注册/导入行为不按配置执行”，排查时很难从页面直接看出是 key 被改断。
- 建议：`configType=Y` 的内置参数应禁止修改 `configKey` 和内置标记，只允许修改名称、备注和受 schema 控制的值；后端 `edit` 必须校验内置 key 保护清单，不能只依赖前端。若确需迁移参数 key，应提供专门迁移流程，展示所有代码引用和影响范围，迁移后同时更新旧 key 兼容、缓存、测试和参数说明。

### P3-197 系统内置标记可编辑，能绕过内置参数删除保护

- 现象：参数设置页在编辑弹窗中允许把“系统内置”从 `Y / 是` 改成 `N / 否`，后端更新语句也会保存 `config_type`。删除接口的保护只在删除时读取当前 `configType`，因此具备参数修改和删除权限的账号可以先把内置参数改成非内置，再删除本应受保护的账号注册、密码策略、初始密码或登录黑名单配置。P3-07 已记录“内置参数仍显示删除按钮但后端会拒绝”，本条记录的是通过编辑内置标记绕过该拒绝的流程缺口。
- 证据：前端 `erp-ui/src/views/system/config/index.vue:168-175` 对 `form.configType` 渲染普通单选组，没有在 `configType=Y` 时只读。`SysConfigMapper.xml` 的 `updateConfig` 在 `:92-103` 包含 `config_type = #{configType}`，说明编辑提交会落库内置标记。`SysConfigServiceImpl.updateConfig()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java:116-129` 保存前没有保护内置标记；`deleteConfigByIds()` 在 `:138-146` 才用当前 `config.getConfigType()` 判断是否禁止删除。当前 MySQL 复核 `sys_config.config_type` 为 `Y=8`，且 `common / 普通角色` 与 `zjl / 总经理` 同时持有 `system:config:edit` 和 `system:config:remove`。
- 影响：内置参数的不可删除保护依赖一个可被同一页面普通编辑流程修改的字段，保护边界不成立。多账号多店铺场景下，业务角色如果保留参数写权限，就可能删除注册开关、密码策略、黑名单、初始密码等基础配置，后续登录、导入、注册或密码提示会回退到默认值、空值或异常状态；管理员看到的是“参数被删除”，很难知道是先改内置标记再删掉造成的。
- 建议：后端把内置标记作为系统保护字段处理：`configType=Y` 的记录不允许在普通编辑接口修改 `configType`，删除时也应基于不可变保护清单或数据库约束判断，而不是信任当前行的可编辑字段。前端对内置参数把“系统内置”显示为只读标签；如确需把内置参数转为自定义参数，应走专门迁移/下线流程，展示影响范围并要求安全管理员二次确认。

### P3-198 菜单新增和上级菜单选择不禁止按钮作为父级，脏权限树可再次写入

- 现象：菜单管理表格对目录、页面和按钮三类节点都显示“新增”操作；点击按钮行新增时会把该按钮 `menuId` 直接作为 `parentId`。新增/修改弹窗的“上级菜单”下拉也来自完整菜单树，没有过滤 `menu_type=F` 的按钮节点。后端新增和修改只校验名称、外链、自身父级和路由唯一，不校验父级菜单类型，因此虽然当前运行库已经没有按钮作为父级的存量脏数据，但页面仍允许再次写入“按钮下面挂菜单/按钮”的结构。P3-80 记录的是旧调拨菜单的存量按钮挂按钮和重复权限，本条记录的是菜单保存链路没有防止同类脏数据再次出现。
- 证据：前端菜单表格行操作 `erp-ui/src/views/system/menu/index.vue:108-116` 对所有行渲染“新增”，没有 `scope.row.menuType !== 'F'` 之类限制；`handleAdd(row)` 在 `:437-446` 直接把 `this.form.parentId = row.menuId`。上级菜单下拉 `getTreeselect()` 在 `:405-412` 使用 `listMenu()` 的完整返回值构建树，没有过滤按钮节点。`SysMenuController.add/edit()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMenuController.java:91-137` 只校验菜单名称唯一、外链地址、上级不能选自己和路由配置唯一，随后调用 `menuService.insertMenu/updateMenu`；`SysMenuServiceImpl.insertMenu/updateMenu` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java:319-334` 直接写库。当前 MySQL 复核 `sys_menu` 中启用按钮节点 `F/status=0` 为 188 个，按钮作为父级的子节点数量为 0。
- 影响：菜单树是路由、角色授权和按钮权限的基础数据结构。按钮作为父级没有清晰产品语义，会让角色授权树、菜单树展示、权限统计和自动化审计再次出现 P3-80 那种“按钮下挂按钮/重复能力”的混乱。当前数据虽然干净，但拥有 `system:menu:add/edit` 的账号仍可从页面制造同类结构；后续多账号验收会把这个问题误判成某个业务菜单单独配置错误，而不是菜单保存规则缺失。
- 建议：前端行内“新增”在 `menuType=F` 时禁用或隐藏，并在上级菜单下拉中过滤按钮节点；后端新增/修改必须校验 `parentId` 对应菜单存在且 `menu_type != F`，并且按节点类型限制层级，例如目录下可建目录/菜单，页面下可建按钮，按钮下禁止建任何节点。菜单发布前增加结构校验脚本，阻断 `F` 类型作为父级、启用重复权限码、父级停用但子级启用等异常。

### P3-199 个人中心资料保存缺少服务端联系方式格式校验兜底

- 现象：个人中心“基本资料”前端会校验手机号和邮箱格式，但后端保存接口只校验手机号/邮箱唯一性，不触发 `SysUser` 上已有的邮箱格式和长度校验，也没有服务端手机号格式校验。构造请求可以绕过浏览器表单，把不符合手机或邮箱格式的联系方式写入当前账号。P3-11 记录的是个人中心把联系方式设为必填但存量多数为空，本条记录的是资料保存接口缺少服务端格式兜底。
- 证据：个人中心表单 `erp-ui/src/views/system/user/profile/userInfo.vue:42-57` 在前端要求邮箱格式和手机号正则。后端 `SysProfileController.updateProfile()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:74-104` 使用 `@RequestBody SysUser user`，没有 `@Validated` 或 `@Valid`；方法内只设置 `nickName/email/phonenumber/sex`，随后调用 `checkPhoneUnique/checkEmailUnique` 做唯一性判断。`SysUser.getEmail()` 在 `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:170-172` 有 `@Email` 和长度注解，但个人中心更新不会触发；`SysUser.getPhonenumber()` 在 `:182-183` 只有 11 位长度限制，没有手机号格式注解。`SysUserMapper.xml:234-251` 会把 `email/phonenumber` 直接更新到 `sys_user`，数据库字段分别是 `email varchar(50)`、`phonenumber varchar(11)`。本轮只读 MySQL 复核当前 25 个启用账号没有异常格式手机号/邮箱，但有 23 个手机号和 23 个邮箱为空，未做写入复现。
- 影响：联系方式会进入用户列表、个人中心、角色分配候选、通知阅读用户、日志和导出等多个账号治理页面。前端校验只能约束正常页面操作，不能防止接口调用、脚本或未来移动端复用接口时写入 `12345`、`abc` 这类格式错误数据；后续通知、联系员工、账号核验和离线台账都会使用脏联系方式。多账号多店铺场景下，错误联系方式还会让“账号配置完整性”看起来已填写，实际不可用。
- 建议：个人中心 `updateProfile` 增加 `@Validated` 或专用 Profile DTO，并在服务端统一校验昵称长度、邮箱格式、手机号格式和唯一性；用户管理新增/编辑、导入和个人中心应复用同一套联系方式规则。若联系方式不是必填，则允许为空但不允许错误格式；若必填，应在用户新增、导入、个人中心和配置状态中统一执行必填策略。

### P3-200 401 无权限页没有接入真实权限失败链路，返回动作容易回到受限页面

- 现象：项目有 `/401` 公共错误页，但当前路由守卫、动态路由和请求拦截器都没有把“菜单无权限 / API 无权限”导到该页面。未登录直连 `/401` 会因为白名单只有 `/login`、`/register` 被转到登录页；已登录但没有菜单权限时，动态路由只把无法匹配的路径挂到 `/404`；接口返回 `401` 时弹登录过期确认并跳 `/index`，其他非 200 错误只弹错误提示。因此 401 页主要是可直连的静态页面，不是权限失败闭环的一部分。页面文案还写“请不要进行非法操作”，返回按钮默认 `router.go(-1)`，可能把用户带回刚才的受限或不存在页面。
- 证据：`erp-ui/src/router/index.js:63-70` 注册 `/404` 和 `/401` 常量路由；`erp-ui/src/permission.js:14` 白名单只有 `/login`、`/register`，`:147-155` 未登录非白名单跳登录；`erp-ui/src/store/modules/permission.js:42` 对无法匹配路由统一 `* -> /404`；`erp-ui/src/utils/request.js:94-121` 对 `401` 只弹会话过期确认并登出，对其他非 200 错误只显示通知，没有 `router.push('/401')`；`erp-ui/src/views/error/401.vue:11-16` 使用“非法操作”文案和回首页链接，`:39-44` 默认回退上一页。
- 影响：多账号权限验收时，用户缺菜单、缺按钮或接口无权限时不会进入明确的“无权限 / 联系管理员 / 需要哪些权限”的页面，而是混杂成登录过期、404 或临时错误提示。测试人员和管理员难以区分“菜单没配置”“接口权限不足”“路由不存在”“会话过期”。返回上一页还可能形成无权限页和受限页之间的循环，体验不友好。
- 建议：把 401 页纳入统一权限失败链路：未授权路由、动态路由过滤后的访问、后端 403 或无权限业务码应携带来源、目标权限和联系管理员提示跳到 `/401`；会话过期继续走登录过期弹窗。401 页文案改为中性说明，不使用“非法操作”；返回按钮优先回首页或回可访问入口，避免默认回到失败页面，同时保留 404 只处理真实不存在路由。

### P3-201 桌面首页快捷入口在权限过滤为空时回退展示全部功能

- 现象：桌面首页“快速进入业务模块”会先按当前账号侧边栏路由过滤快捷入口，但过滤后只剩“选择组织”或没有业务入口时，代码会回退展示全部预置快捷入口，包括商品资料、库存查询、销售管理、采购管理、我的待办。运行态当前 `sxs / 实习生` 角色没有任何启用菜单节点，用户 `123 / user_id=103` 绑定该角色且可用；这类零菜单或极窄权限账号进入首页时，页面仍可能显示一组看似可进入的业务入口。
- 证据：首页快捷入口配置在 `erp-ui/src/views/index.vue:91-98`；`visibleRoutePaths` 从 `sidebarRouters` 提取当前账号可见路由，见 `:158-175`；`isQuickLinkVisible()` 在 `:217-220` 会按 `storeOnly/warehouseOnly` 和 `visibleRoutePaths` 过滤；但 `availableQuickLinks()` 在 `:177-182` 中当过滤结果 `links.length <= 1` 时直接返回全部 `quickLinks`。运行态 MySQL 复核 `role_key=sxs` 的 `enabled_menu_nodes=0/visible_menu_nodes=0`，用户 `user_id=103 / user_name=123` 绑定 `sxs`，同样没有启用或可见菜单节点。
- 影响：首页是多账号验收最容易到达的页面。对零菜单、待配置、无业务权限或仅允许选择组织的账号，系统应明确提示“暂无可用功能 / 联系管理员补授权”，而不是展示销售、采购、库存、待办等入口。当前回退会让用户点击后进入 404、空白页、组织选择或无权限提示，管理员也容易把问题误判成某个业务菜单坏了，而不是账号权限未配置完整。
- 建议：首页快捷入口不要在过滤为空时回退到全部入口；应展示空态和账号配置引导，列出当前账号、当前组织、已授权菜单数量和“联系管理员”提示。`summaryCards` 和 `guideItems` 也应使用同一套权限过滤和模块启用状态，只有真实可访问的库存、销售、采购、待办入口才显示；零菜单账号应与 P1-17 的账号完整性校验联动。

### P3-202 定时任务顶部“日志”按钮未传任务行，点击会触发前端异常

- 现象：定时任务页工具栏有一个和新增、修改、删除、导出并列的“日志”按钮，但该按钮没有绑定选中任务，也没有打开全量日志页的兜底参数。点击时会直接调用 `handleJobLog` 且不传 `row`，方法内部又立即读取 `row.jobId`，因此会出现 `Cannot read properties of undefined` 一类前端异常，而不是进入调度日志页面或提示先选择任务。行内“更多 -> 调度日志”会传入当前行，语义和顶部按钮不一致。
- 证据：顶部按钮在 `erp-ui/src/views/monitor/job/index.vue:84-92` 使用 `@click="handleJobLog"`，没有传入 `scope.row` 或全量日志标记；行内下拉在 `:140-146` 通过 `handleCommand(command, scope.row)` 传入当前行，并在 `:367-378` 调用 `this.handleJobLog(row)`；`handleJobLog(row)` 在 `:418-421` 直接执行 `const jobId = row.jobId || 0` 后跳转 `/monitor/job-log/index/`。调度日志页 `erp-ui/src/views/monitor/job/log.vue:197-205` 已支持路由参数 `jobId=0` 时加载全量日志，说明顶部按钮如果要作为全量日志入口应显式跳 `/monitor/job-log/index/0`。P3-92 记录的是调度日志缺少 `job_id` 只能按任务名追溯，本条记录的是顶部日志按钮本身的空参数崩溃。
- 影响：监控类页面通常用于排查任务执行失败和审计调度行为，顶部“日志”按钮会让管理员理解为“查看调度日志”。当前点击即前端异常，会打断排查路径；同时用户无法判断这是查看全部日志、查看选中任务日志，还是必须从行内更多菜单进入。多账号验收时，这类工具栏按钮故障容易被误判为调度日志页不可用，实际是入口语义和参数处理缺失。
- 建议：明确顶部“日志”入口语义。若它是全量日志入口，应直接跳转 `/monitor/job-log/index/0` 并命名为“全部日志”；若它是行级入口，应像修改、删除一样依赖单选任务，未选时禁用或提示先选择一条任务，并把选中行传给 `handleJobLog`。同时 `handleJobLog` 本身应对空 `row` 做兜底，避免工具栏、快捷键或未来复用时再次触发前端异常。

### P3-39 OA 已办详情不展示审批意见，审批结果难以回溯

- 现象：待办审批弹窗要求填写审批意见，后端也会把意见写入 `oa_purchase_comment`；但采购申请详情和已办详情只展示标题、金额、状态、说明，不展示审批人、通过/驳回动作、意见和审批时间。
- 证据：`OaPurchaseServiceImpl.approvePurchase` 在 `:267-275` 写入 `OaPurchaseComment`；`OaPurchaseCommentMapper.xml:27-31` 已有 `selectOaPurchaseCommentsByPurchaseId`，但 `OaPurchaseServiceImpl.getPurchaseDetail` 只返回 `assertAndGetScopedPurchase` 的 `OaPurchase`，没有加载 comments。前端 `erp-ui/src/views/oa/done/index.vue:114-134` 的详情弹窗只显示标题、金额、状态、说明；`erp-ui/src/views/oa/purchase/index.vue:154-159` 的详情 alert 也是同样字段。
- 影响：审批通过或驳回后，申请人和审批人都无法在电脑端看到历史意见，只能知道最终状态；驳回原因、审批依据和责任人不能闭环，后续采购争议或复盘缺少页面证据。
- 建议：采购详情接口返回审批意见列表，已办页和我的申请详情增加审批时间线，展示节点、审批人、动作、意见、时间和任务 ID；导出已办时也应包含审批意见摘要。

### P3-40 库存盘点“修改”权限未被前后端使用

- 现象：数据库菜单中启用了 `4303 / 盘点修改 / inv:stockCheck:edit`，并且超级管理员、店长、驻店经理、运营经理、总经理都被授予该权限；但库存盘点页面没有任何按钮使用 `inv:stockCheck:edit`，后端控制器也没有任何接口要求该权限。
- 证据：MySQL 查询显示库存盘点权限包含 `list/query/add/edit/submit/confirm/remove/export`，上述 5 类角色均完整授权。源码 `rg "inv:stockCheck:edit" erp-ui erp-modules sql` 无结果；前端“录入实盘”按钮和弹窗提交按钮使用 `inv:stockCheck:submit`，见 `erp-ui/src/views/inventory/stockCheck/index.vue:53`、`:126`；后端录入接口 `/stockCheck/input/{checkId}` 也要求 `inv:stockCheck:submit`，见 `InvStockCheckController.java:58-65`。
- 影响：角色管理员以为可以单独控制“修改盘点草稿/实盘数量”和“提交盘点”的人员，实际 `edit` 授权没有效果，录入实盘会直接走提交权限并把草稿推进到盘点中。权限树、按钮名称和状态流转语义不一致，后续按最小权限配置角色时容易误配。
- 建议：明确库存盘点是否需要“保存草稿修改”和“提交盘点”两个动作。需要拆分时，草稿日期/备注/实盘数量保存使用 `inv:stockCheck:edit`，提交到 `checking` 使用 `inv:stockCheck:submit`；不需要拆分时，删除或隐藏 `inv:stockCheck:edit` 权限点，避免继续误导授权。

### P3-41 调拨审批规则预览没有使用真实调拨场景

- 现象：调拨审批配置列表的“预览”按钮不会让用户选择真实发货组织、收货组织、仓库、调拨类型或数量，而是用规则自身的 `scopeId` 同时作为 `fromDeptId` 和 `toDeptId`，并把规则条件值当作本次预览数量。
- 证据：前端 `erp-ui/src/views/inventory/transfer/rules.vue:593-599` 构造预览 payload：`transferType` 只在规则不是 `all` 时带入，`fromDeptId=row.scopeId`、`toDeptId=row.scopeId`、`totalQuantity=row.conditionValue`。后端 `/transfer/rule/preview` 只按这份伪造调拨单调用 `previewRule()` 和 `matchRule()`。当前规则 `5 / 市场部门` 的 `scopeId=108`，因此页面预览验证的是 `108 -> 108`，不是实际常见的 `104 / 仓库 -> 108 / 市场部门`；如果条件操作符是 `>`，用条件值本身预览还会在边界值上误判未匹配。
- 影响：“预览成功”只能说明这条规则在一个自造场景下会被匹配，不能证明真实仓库出库、异店调货、数量阈值和审批候选人都可用；管理员容易在上线规则前获得错误安全感，最终仍由调拨提交人遇到失败。
- 建议：把预览改成独立弹窗，要求选择调拨类型、发货仓库/门店、收货门店和样例数量；返回匹配规则、命中原因、每级审批岗位、候选审批人、是否会自动通过或跳过节点。列表上的快捷预览可以改名为“按范围试算”，避免暗示已完成真实流程校验。

### P3-42 调拨审批范围和节点角色选项语义不清

- 现象：规则表单的“适用范围”只提供“全部 / 部门 / 区域”，但后端已经区分 `from_dept`、`to_dept`、`region`、`area`；同时节点“审批范围”提供“区域经理 / 运营经理”，后端却没有针对这两个值的独立解析语义。
- 证据：前端 `erp-ui/src/views/inventory/transfer/rules.vue:310-314` 的 `scopeTypeOptions` 没有发货方部门、收货方部门和 region 选项；后端 `InvTransferApprovalRuleServiceImpl` 在 `:26-31` 定义了 `SCOPE_FROM_DEPT/SCOPE_TO_DEPT/SCOPE_REGION/SCOPE_AREA`，`matchesScope()` 在 `:392-424` 中分别匹配。前端 `nodeRoleOptions` 在 `erp-ui/src/views/inventory/transfer/rules.vue:336-342` 包含 `area_manager`、`operator_manager`；但 `InvTransferApprovalServiceImpl.resolveCandidateScopeDeptId/resolveCandidateDeptIds` 在 `:700-729` 只特殊处理 `from_leader` 和 `to_leader`，其他值都按目标组织祖先链查找岗位用户。
- 影响：管理员无法在页面上清楚表达“只对发货方为某门店生效”或“只对收货方为某门店生效”；选择“区域经理 / 运营经理”也可能误以为系统会按区域或运营线自动找负责人，实际只是按目标组织祖先链和岗位编码查人。审批配置语义和真实执行不一致，会增加规则误配概率。
- 建议：要么前端完整暴露后端支持的范围类型，并用业务文案说明“发货方 / 收货方 / 区域”；要么后端移除未交付语义。节点角色也应只保留真实实现的选项，或为区域经理、运营经理补明确的组织和岗位解析规则。

### P3-43 销售退货原单候选没有按“可退数量”过滤，用户会选到不可退原单

- 现象：新增销售退货时，原销售单远程下拉只排除 `draft/cancelled` 状态，没有直接过滤“已出库数量大于 0 且剩余可退数量大于 0”的原单。用户可能选到已提交但未发货、已通知但未发货、或已全部退完的销售单，载入后才看到“该销售单暂无可退明细”。
- 证据：`erp-ui/src/views/inventory/salesReturn/index.vue:253-261` 调 `listSales({ pageNum: 1, pageSize: 20, orderNo })` 后只执行 `.filter(item => item.status !== "draft" && item.status !== "cancelled")`；`buildReturnDetails()` 在 `:290-306` 用明细 `deliveredQuantity` 作为 `maxReturnQuantity` 并过滤 `maxReturnQuantity > 0`。后端销售列表 mapper 已经返回 `delivered_quantity/remaining_quantity` 聚合字段，但前端候选没有使用这些字段，也没有后端专用“可退销售单”接口。
- 影响：当前正式销售单为空时下拉没有空态已列 P2-08；后续有销售单后，用户仍会被不可退原单干扰，需要选中后才知道没有可退明细。对门店操作员来说，这会把一个应该前置的业务规则拖到表单中后段提示，退货效率和可理解性都差。
- 建议：增加“可退销售单”接口或在销售列表查询参数中支持 `returnable=true`，后端按门店、状态、已出库数量和历史已退数量过滤；前端下拉显示“已出库 / 已退 / 可退”数量，并在无可退原单时直接显示空态文案。

### P3-234 退货“我的”接口按空的 create_by 过滤，申请人自己的退货单会查不到

- 现象：销售退货和采购退货都暴露了“我的退货单”接口，但服务层只把当前用户写到 `applicantId`，mapper 却按 `createBy` 查询。正常请求没有传 `createBy` 时，SQL 条件会变成 `r.create_by = null`，即使该用户创建过退货单也查不到自己的记录。
- 证据：前端 API 已定义 `listMySalesReturn()` 和 `listMyPurchaseReturn()`，见 `erp-ui/src/api/inventory/salesReturn.js:9`、`erp-ui/src/api/inventory/purchaseReturn.js:9`。后端 `/inventory/salesReturn/my` 和 `/inventory/purchaseReturn/my` 分别调用 `selectMyReturns()`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesReturnController.java:42-48`、`erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseReturnController.java:42-48`。服务层只设置 `salesReturn.setApplicantId(SecurityUtils.getUserId())`、`purchaseReturn.setApplicantId(SecurityUtils.getUserId())`，见 `InvSalesReturnServiceImpl.java:132-136`、`InvPurchaseReturnServiceImpl.java:137-142`；但 mapper 的个人查询条件是 `where r.create_by = #{createBy}`，见 `InvSalesReturnMapper.xml:70-76`、`InvPurchaseReturnMapper.xml:70-76`。MySQL 只读核对显示当前 `inv_sales_return=0`、`inv_purchase_return=0`，但两张表都同时存在 `applicant_id` 和 `create_by` 字段。
- 影响：当前电脑端列表页主要使用全量列表接口，所以这个问题不一定马上暴露在主列表；但一旦新增“我的退货单”页签、移动端入口或个人工作台待办复用该接口，用户会看到空列表，误以为自己没有提交过退货申请。接口名称、服务层意图和数据库过滤字段不一致，也会让后续权限拆分和个人范围审计变得不可靠。
- 建议：统一“我的”接口的身份字段，优先按 `r.applicant_id = #{applicantId}` 查询，并继续叠加门店/仓库范围过滤；如果业务定义确实要按创建人用户名过滤，则服务层必须显式设置 `createBy=SecurityUtils.getUsername()`，并在接口命名和字段文档中说明。销售退货和采购退货都应补覆盖测试：当前申请人能看到自己的单据，不能看到其他人的单据。

### P3-44 库存管理“查询”权限和详情接口未被电脑端详情使用

- 现象：数据库同时配置了 `4041 / 4451 库存管理查询 / inv:stock:query`，后端也有 `/inventory/stock/{stockId}` 详情接口；但电脑端库存页打开详情弹窗时没有调用详情接口，也没有任何按钮使用 `inv:stock:query`，详情内容直接来自列表行。
- 证据：MySQL 查询显示 `inv:stock:query` 已授权给超级管理员、店长、驻店经理、运营经理、总经理和仓库管理员。`erp-ui/src/api/inventory/stock.js:13-16` 定义了 `getStock(stockId)`；但 `erp-ui/src/views/inventory/stock/index.vue:317` 只引入 `listStock/getStockSummary/adjustStock`，`getList()` 在 `:667-674` 调列表接口，`openStockDetail()` 在 `:924-927` 直接 `this.detailStock = row`。后端详情接口见 `InvStockController.java:51-55`，要求 `inv:stock:query`。
- 影响：角色管理员会以为“库存列表”和“库存详情查询”能分开授权，实际电脑端详情跟随列表权限和列表返回字段走；后续如果按最小权限拆分查询权限，`inv:stock:query` 不会影响页面详情能力，也容易让详情展示依赖过宽的列表字段。
- 建议：如果库存详情要单独授权，前端详情入口应使用 `v-hasPermi="['inv:stock:query']"` 并调用 `getStock(stockId)` 获取服务端详情；如果不需要拆分，则清理或合并 `inv:stock:query` 权限点，避免继续误导角色配置。

### P3-45 客户编码不是唯一身份，重复编码会继续削弱客户主数据闭环

- 现象：客户档案表有“客户编码”字段，客户列表也支持按客户编码精确筛选，但前端新增客户时允许客户编码为空，后端保存没有重复校验，数据库也没有唯一约束。
- 证据：`SHOW CREATE TABLE inv_customer` 显示 `customer_code` 只有普通索引 `KEY idx_icus_code (customer_code)`，不是唯一索引；`InvCustomerServiceImpl.saveCustomer()` 在 `:38-53` 只解析当前组织并插入/更新，没有查询同门店重复编码；前端 `erp-ui/src/views/inventory/customer/index.vue:73-75` 的客户编码输入没有必填或格式校验，`rules` 在 `:160` 只要求客户名称；列表 mapper 在 `InvCustomerMapper.xml:41` 对客户编码做精确等值查询。
- 影响：当前 `inv_customer=0`，还没有存量重复；但一旦门店开始录入客户，多个客户可以共用空编码或相同编码。后续即使把销售单客户字段从自由文本改成客户选择器，客户编码也不能作为可靠识别键，导出、搜索、去重和客户销售归并仍会不稳定。
- 建议：明确客户编码规则。若客户编码是业务身份，应按 `shop_dept_id + customer_code` 做非空唯一校验并支持自动生成；若允许空编码，则页面和导出不要把它当唯一识别字段，至少用手机号、客户名称、所属门店和创建时间辅助去重。

### P3-109 供应商编码全为空且没有唯一规则，列表精确筛选没有可用身份字段

- 现象：供应商管理页把“供应商编码”作为搜索项、列表列、详情字段和表单字段，但当前运行态 17 个供应商的编码全部为空或 NULL。后端仍按编码做精确筛选，数据库和服务层都没有必填或唯一规则。
- 证据：MySQL 复核 `inv_supplier` 得到 `supplier_count=17`、`blank_code_count=17`、`distinct_nonblank_code_count=0`；`SHOW CREATE TABLE inv_supplier` 显示 `supplier_code` 只有普通索引 `KEY idx_isup_code (supplier_code)`，不是唯一索引。前端 `erp-ui/src/views/inventory/supplier/index.vue` 在筛选、表格、详情和新增/编辑弹窗都展示 `supplierCode`，但 `rules` 只校验 `supplierName`；后端 `InvSupplierServiceImpl.saveSupplier()` 不校验编码，`InvSupplierMapper.xml` 插入/更新可写空编码，列表查询又用 `s.supplier_code = #{supplierCode}` 做精确匹配。
- 影响：用户看到“供应商编码”会以为它是稳定身份或可搜索字段，但当前没有任何真实编码可用。后续采购、商品、供应商对账或外部系统导入要按编码识别供应商时，只能退回名称匹配；结合 P2-109 的供应商名称字符串引用，重名、改名和空编码会一起削弱供应商主数据治理。
- 建议：明确供应商编码是否是业务身份字段。若是，应按 `shop_dept_id + supplier_code` 做非空唯一校验并支持自动生成/导入前预检；先清洗当前 17 条空编码供应商。若不是，应从搜索和导出中弱化该字段，不要让用户依赖空编码做精确筛选。

### P3-204 客户和供应商编辑按钮只看修改权限，但打开弹窗还隐式依赖查询权限

- 现象：客户管理和供应商管理的行内“编辑”按钮分别只判断 `inv:customer:edit`、`inv:supplier:edit`，但点击编辑时前端会先调用详情接口加载完整表单；详情接口分别要求 `inv:customer:query`、`inv:supplier:query`。因此权限树可以配置出“有修改权限、没有查询权限”的角色：页面会显示编辑按钮，点击后弹窗无法正常加载或接口报无权限，用户只能看到一个前端可点、后端拒绝的断裂流程。
- 证据：客户页编辑按钮见 `erp-ui/src/views/inventory/customer/index.vue:56`，只使用 `v-hasPermi="['inv:customer:edit']"`；`openForm(row)` 在 `:170-172` 会调用 `getCustomer(row.customerId)`，API 对应 `GET /inventory/customer/{customerId}`，见 `erp-ui/src/api/inventory/customer.js:8-10`；后端 `InvCustomerController.getInfo()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java:41-45` 要求 `inv:customer:query`。供应商页同样如此：编辑按钮在 `erp-ui/src/views/inventory/supplier/index.vue:54` 只看 `inv:supplier:edit`，`openForm(row)` 在 `:219-221` 调 `getSupplier(row.supplierId)`，API 在 `erp-ui/src/api/inventory/supplier.js:8-10` 请求详情，后端 `InvSupplierController.getInfo()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSupplierController.java:42-46` 要求 `inv:supplier:query`。当前运行态默认角色中店长/驻店/运营/总经理的客户权限、仓库管理员/总经理的供应商权限都同时授了 query 和 edit，所以现有角色暂不触发；问题在于权限模型允许后续最小权限配置时拆出失败组合。
- 影响：角色管理员以为“修改”权限足以完成编辑，实际还必须额外勾选“查询”；如果只给某个门店主管供应商或客户修改权限而漏掉查询权限，会得到一个看似有权限但无法打开的流程。多账号多店铺配置中，这类隐式依赖会增加权限排查成本，也会削弱 `query` 权限本身的语义：它到底是“详情查看”，还是“编辑前置读取”没有说明。
- 建议：把编辑入口的权限依赖表达清楚。前端编辑按钮应同时要求 `edit + query`，或后端提供受 `edit` 保护的编辑加载接口；角色配置页和权限说明也要标明“修改客户/供应商需要读取详情”。如果希望“编辑”天然包含读取，应把详情接口允许 `query` 或 `edit` 任一权限，并在审计日志里记录编辑前后数据。

### P3-46 非运行态空授权数据会误导配置状态和多账号巡检脚本

- 现象：非运行态 `BossERP` schema 中 7 个有效账号均为启用状态且 `sys_user_shop=0`，但运行态 `BossERP_stock_state_75c59ee` 中 25 个有效账号有 43 条显式授权。若巡检脚本或人工 SQL 不指定运行态 schema，会把非运行态空授权误报成当前网页端账号配置缺失。
- 证据：直查 `BossERP` 时 `admin`、`ry`、`123456`、`user1`、`123456789`、`liu123`、`li123456` 七个有效账号 `shop_scope_count` 均为 0；直查运行态库 `BossERP_stock_state_75c59ee` 时 `admin`、`ry` 均有 7 个经营组织授权，`qa144822m` 有 2 个门店授权，`qa144822s`、`qa144822w`、`qa_audit_62201` 等账号也各有明确授权。运行态 `/user/info/ry` 返回的是 `zjl` 角色和 `100 金英灵韵`，与 `BossERP` 中 `cys / 105 测试部门` 不同。
- 影响：配置状态、缺授权筛选、角色权限矩阵和多账号页面实测会被错误数据源污染；同一账号在两套 schema 中密码、角色、部门不同，导致“能否登录、能看到哪些菜单、是否缺门店授权”的结论不可复现。
- 建议：巡检脚本必须显式传入运行态 schema，并在输出中打印 datasource URL、schema 名和 `sys_user/sys_user_shop` 计数。非运行态 schema 的空授权可以保留为环境漂移风险，但不应作为当前网页端账号配置缺陷关闭或修复。

### P3-47 店铺授权列表把多组织授权折叠成父级名称，范围不够可读

- 现象：店铺授权页“组织范围”列会把用户已授权的多个具体门店/仓库折叠成父级组织名。早前有授权数据时，admin、ry 都有 7 个具体经营组织授权，列表只显示“金英灵韵”；用户 `12345` 有 4 个具体门店授权，也显示为“金英灵韵”。
- 证据：早前前端复测 `/system/shop` 用户列表中 admin、ry、12345 的组织范围列均显示“金英灵韵”。MySQL 当时查询运行态 `sys_user_shop` 显示 admin 和 ry 实际授权 `104 / 仓库`、`103/105/106/107/108/109` 六个门店/仓库共 7 个经营组织；`12345` 授权 `103/105/106/107` 四个门店。源码 `erp-ui/src/views/system/shop/index.vue:389-404` 的 `buildShopScopeLabels` 在某父节点下所有可授权子节点都被选中时仍直接 `result.push(node.deptName)`，不会显示具体子组织或数量。
- 影响：管理员在列表页无法区分“授权整个集团/父级组织”和“授权了该父级下全部具体门店/仓库”；排查多账号多店铺权限时，需要点进详情或查数据库才知道真实范围，对 7 个组织这类全量授权尤其容易误读。
- 建议：组织范围列显示“金英灵韵（7 个组织）”并提供 tooltip/详情展开，至少列出具体门店/仓库；父级汇总名应明确是展示压缩，不代表授权到了不可操作的集团节点。

### P3-48 停用的采购计划、本店库存、批次管理和钉钉旧入口仍保留缺失组件路径

- 现象：数据库保留多组停用隐藏菜单和按钮权限，包括采购计划、本店库存、批次管理、钉钉同步、打卡明细、打卡汇总以及旧采购申请记录；这些菜单当前没有角色授权，但 `component` 指向的前端文件不存在，源码中也没有对应权限使用。
- 证据：`sys_menu` 中 `4095 / 采购计划 / inventory/purchasePlan/index`、`4140 / 本店库存 / inventory/myStoreStock/index`、`4138 / 批次管理 / inventory/batch/index`、`4152-4154 / oa/dingtalk/*` 均为 `status=1`、`visible=1`；对应 `erp-ui/src/views/...` 文件全部缺失，`rg` 未发现 `purchasePlan`、`myStoreStock`、`inv:batch`、`oa/dingtalk/sync` 等业务源码。MySQL 复核这些父子菜单 `sys_role_menu` 授权数均为 0。
- 影响：当前不会出现在普通运行菜单里，但数据库内容缺少“废弃/待开发/待迁移”说明。以后如果管理员或脚本误把这些菜单改为启用，前端会直接进入缺组件路由；如果做权限清单或交付验收，也会把这些历史菜单误认为仍在交付范围。
- 建议：为停用菜单增加明确治理口径：确定废弃的菜单和按钮应从当前运行库移除或迁移到归档 SQL；待开发功能应保留产品说明、组件占位页和禁用原因。批量启用菜单前增加组件存在性校验，防止缺文件菜单重新上线。

### P3-49 锁屏解锁没有进入登录失败计数和审计闭环

- 现象：桌面端 `/lock` 页面提示“系统已锁定，请输入密码解锁”，但锁屏状态只保存在浏览器 `localStorage`，后端 `/auth/unlockscreen` 只校验当前登录用户密码；解锁失败不会复用登录失败次数、账号锁定和登录日志机制。
- 证据：前端 `erp-ui/src/store/modules/lock.js:1-29` 用 `screen-lock`、`screen-lock-path` 写入本地状态；路由守卫 `erp-ui/src/permission.js:91-104` 只按 `store.getters.isLock` 跳转 `/lock`；`erp-ui/src/views/lock.vue:87-103` 调用 `unlockScreen(password)`，接口定义为 `erp-ui/src/api/login.js:40-46` 的 `/auth/unlockscreen`；后端 `TokenController.unlockScreen` 要求 `@RequiresLogin` 后调用 `SysLoginService.unlock`，而 `erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java:132-152` 只做空密码和 `matchesPassword` 校验。普通登录密码校验 `SysPasswordService.validate` 则会写 `pwd_err_cnt:*` 并调用 `recordLogininfor` 记录失败，见 `erp-auth/src/main/java/com/erp/auth/service/SysPasswordService.java:42-70`。
- 影响：用户会把锁屏理解成保护离席电脑的安全功能，但当前更像前端便利遮罩；本地锁定状态可被浏览器本地状态影响，连续输错解锁密码也不会进入账户锁定或管理员登录日志视图。多账号共用电脑或门店前台场景下，安全预期和实际能力不一致。
- 建议：产品上明确锁屏定位。若要作为安全功能，应把锁屏状态和失败次数放到服务端会话/Redis 中，解锁失败写入 `sys_logininfor` 并复用账号锁定策略；若只作为便利遮罩，页面文案应改成“本机界面已隐藏”，并建议离席时退出登录。

### P3-207 锁屏状态下刷新页面会先进入锁屏页，用户身份展示可能为空

- 现象：锁屏页展示头像和昵称，让用户确认当前被锁定的是哪个账号；但锁定状态只从 `localStorage` 初始化，页面刷新后 Vuex 用户信息会回到空值。路由守卫在发现 `isLock=true` 时会先跳 `/lock`，不会先执行 `GetInfo` 拉取当前用户资料，因此刷新后的锁屏页可能只有密码框，没有清晰的账号身份提示。
- 证据：锁屏状态初始化只读 `localStorage.getItem('screen-lock')` 和 `screen-lock-path`，见 `erp-ui/src/store/modules/lock.js:1-9`；用户模块初始 `nickName/avatar/name/id/deptId` 均为空，只有 `GetInfo` 成功后才写入，见 `erp-ui/src/store/modules/user.js:33-42`、`:101-117`。路由守卫在有 token 时先取 `isLock`，若 `isLock && to.path !== '/lock'` 就直接 `next({ path: '/lock' })`，而 `GetInfo` 分支在后面才执行，见 `erp-ui/src/permission.js:91-110`。锁屏页本身只从 Vuex getters 读取 `avatar/nickName`，没有兜底调用用户信息接口，见 `erp-ui/src/views/lock.vue:12-20`、`:56-58`。当前报告遵守只读审计，未通过浏览器写入锁屏状态复现；结论来自刷新初始化路径和组件依赖关系。
- 影响：门店前台或共用电脑被锁屏后，用户刷新浏览器或浏览器崩溃恢复时，页面可能无法显示“当前锁定账号是谁”。多人共用电脑时，员工不知道应输入谁的密码，容易误以为是自己的会话或选择“退出重新登录”；如果头像为空还会削弱锁屏的可信度。该问题和 P3-49 的失败计数不同，属于锁屏状态恢复和账号识别体验不闭环。
- 建议：锁屏页进入前应确保有最小用户身份信息：可在锁屏守卫中先调用只读 `GetInfo`，或在锁定时把 `userId/userName/nickName/avatar` 的安全展示快照随 `screen-lock` 一起保存；刷新恢复时显示“当前锁定账号：xxx”，token 失效则直接引导重新登录。若采用本地快照，应在退出登录、登录成功和用户切换时清理，避免跨账号残留。

### P3-208 顶部全屏和布局大小控件缺少统一中文提示，公共按钮可理解性偏弱

- 现象：桌面顶部右侧的全屏按钮是纯图标点击区，没有 tooltip、可访问名称或键盘提示；相邻的“布局大小”虽然有中文 tooltip，但下拉选项仍是 `Default/Medium/Small/Mini`，切换成功提示也是英文 `Switch Size Success`。用户需要猜测全屏图标含义，且同一组公共控件的反馈语言不一致。
- 证据：导航栏把 `<screenfull id="screenfull" class="right-menu-item hover-effect" />` 直接挂到右侧菜单，只给 `size-select` 外层加了 `el-tooltip content="布局大小"`，见 `erp-ui/src/layout/components/Navbar.vue:29-32`、`:74-86`。全屏组件模板只有可点击的 `svg-icon`，点击后仅调用 `screenfull.toggle()`，不支持时才提示“你的浏览器不支持全屏”，见 `erp-ui/src/components/Screenfull/index.vue:1-4`、`:24-30`。布局大小组件的选项文本是英文，切换后刷新当前路由并提示 `Switch Size Success`，见 `erp-ui/src/components/SizeSelect/index.vue:18-22`、`:32-51`；大小偏好通过 `app` store 写入 Cookie `size`，Element 初始化也读取该 Cookie，见 `erp-ui/src/store/modules/app.js:10`、`:34-36`、`:53-54` 和 `erp-ui/src/main.js:59-60`。
- 影响：门店前台、仓库或财务用户在电脑网页端操作时，顶部公共按钮是跨页面高频控件；纯图标全屏缺少解释会降低可发现性，英文选项和英文成功提示会打断中文后台的一致性。布局大小又会保存在当前浏览器 Cookie 中，共用电脑换账号后可能沿用上一位用户的显示偏好，但页面没有告知“已保存到本机浏览器”，容易被误解为账号级设置或系统异常。
- 建议：为全屏按钮补中文 tooltip、`aria-label/title` 和键盘触发语义，切换后可给出“已进入全屏/已退出全屏”的轻提示；布局大小选项和成功提示统一改为中文，例如“默认/中等/小/迷你”“布局大小已切换”。如继续使用浏览器 Cookie 持久化，应在设置入口或提示中说明这是本机浏览器偏好，并提供恢复默认入口。

### P3-209 用户管理页列显隐设置没有接入记忆能力，刷新后排查视图会丢失

- 现象：用户管理页右上角提供“显隐列”按钮，管理员可以临时隐藏用户编号、手机号、状态、配置状态、创建时间等列；但刷新页面或重新进入后，列显隐状态会回到默认值。通用右侧工具栏组件已经实现了按 `storageKey` 保存和恢复列设置的能力，但用户管理页没有传入该 key。
- 证据：用户页只以 `<right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>` 挂载工具栏，没有传 `storage-key/storageKey`，见 `erp-ui/src/views/system/user/index.vue:53`；表格列的显示完全依赖本页 `columns.*.visible`，包括 `用户编号/用户名称/用户昵称/部门/手机号码/状态/配置状态/创建时间`，见 `erp-ui/src/views/system/user/index.vue:60-83`、`:256-264`。通用 `RightToolbar` 在 `erp-ui/src/components/RightToolbar/index.vue:81-85` 定义 `storageKey`，在 `created()` 中只有 `this.storageKey` 存在时才从 `cache.local.getJSON` 恢复列设置，见 `:112-129`；列变更后 `saveStorage()` 也会在 `!this.storageKey` 时直接返回，见 `:220-231`。全仓检索当前只有用户管理页给 `RightToolbar` 传了 `:columns`，没有任何页面传 `storageKey`。
- 影响：多账号多店铺验收时，用户管理页是反复核对账号配置状态、手机号、角色/组织缺口和创建时间的核心入口。管理员如果为了排查只保留“账号、部门、配置状态、创建时间”等列，刷新、返回或换账号后设置会丢失，需要反复调整；共用电脑上也无法明确区分“本机偏好”和“系统默认视图”。这会降低批量账号巡检效率，并让列设置按钮看起来像支持个人偏好但实际只是一时生效。
- 建议：用户管理页传入稳定的 `storage-key`，例如 `system-user-columns`，让列显隐状态按当前浏览器持久化；若列设置属于账号级偏好，则应落到用户配置接口而不是本地缓存。显隐列菜单可增加“恢复默认列”动作，并在列配置新增字段时做版本兼容，避免旧缓存隐藏新关键列。

### P3-210 字典项修改只清 Vuex 缓存，已打开页面的字典标签不会自动同步

- 现象：字典管理页新增、修改或删除字典项后，只把该字典类型从前端 Vuex 缓存中移除；已经打开的用户、角色、部门、岗位、店铺、日志等页面会把字典数据复制到各自组件本地 `dict.type` 中，并且这些页面可能被标签页 `keep-alive` 保留。这样字典数据库已更新，但已打开页面里的状态标签、筛选下拉和字典文案仍可能保持旧值，直到刷新页面或重新创建组件。
- 证据：全局字典请求先从 `store.getters.dict` 查缓存，命中后直接返回缓存数据；未命中才调用 `/system/dict/data/type/{dictType}` 并 `dict/setDict`，见 `erp-ui/src/components/DictData/index.js:27-35`。每个使用 `dicts` 的页面在 `Dict.init()` 中初始化自己的 `dict.type[type]` 数组，`loadDict()` 只把请求结果 `splice` 到当前组件实例的本地数组，见 `erp-ui/src/utils/dict/Dict.js:23-42`、`:64-80`。字典数据页新增、修改、删除后只执行 `this.$store.dispatch('dict/removeDict', this.queryParams.dictType)`，见 `erp-ui/src/views/system/dict/data.vue:367-392`；Vuex 的 `REMOVE_DICT/CLEAN_DICT` 也只改 `state.dict`，没有通知已创建的组件重新 `reloadDict`，见 `erp-ui/src/store/modules/dict.js:13-25`。主内容区使用 `<keep-alive :include="cachedViews">` 保留已打开页面实例，见 `erp-ui/src/layout/components/AppMain.vue:3-6`。当前至少 19 个桌面页面声明 `dicts`，其中 10 个页面使用 `sys_normal_disable`。
- 影响：多账号多店铺验收里，字典常用于账号状态、角色状态、组织状态、日志结果、任务状态等基础枚举。管理员修改字典标签、颜色或停用某个字典项后，另一个已打开的标签页仍显示旧状态，会造成“数据库已改、前端仍旧”的对应关系断裂；共用电脑和多标签巡检时尤其容易误判当前配置是否生效。已有 P3-50 记录的是刷新缓存权限粒度，本问题记录的是前端本地字典副本没有刷新机制。
- 建议：字典更新后要有前端全局失效和同步机制：`dict/removeDict/cleanDict` 应能广播版本变化，所有已创建的 `Dict` 实例按类型执行 `reloadDict`，或页面在激活/路由进入时比对字典版本并刷新本地 `dict.type`。如果只支持刷新后生效，字典管理页成功提示必须说明“已打开页面需刷新后生效”。同时给 `dict/setDict` 做按 key 替换，避免并发加载时产生重复缓存。

### P3-211 通用分页翻页后滚动目标不匹配，固定内容区长列表不会稳定回到顶部

- 现象：通用分页组件默认在切换页码或每页条数后执行自动滚动，意图是让用户回到新列表顶部；但当前桌面布局把主内容区放在 `.app-main` 这个独立滚动容器里，分页组件调用的滚动工具只操作 `document.documentElement/body`。因此在长列表、固定头部和标签页布局下，翻页后用户可能仍停留在内容区底部或旧滚动位置，需要手动回到新页列表顶部。
- 证据：分页组件 `handleSizeChange/handleCurrentChange` 都在 `autoScroll=true` 时调用 `scrollTo(0, 800)`，见 `erp-ui/src/components/Pagination/index.vue:87-100`；`autoScroll` 默认值为 `true`，见 `:55-58`。`scrollTo` 的 `move()` 只设置 `document.documentElement.scrollTop`、`document.body.parentNode.scrollTop` 和 `document.body.scrollTop`，见 `erp-ui/src/utils/scroll-to.js:19-27`。但桌面布局在有固定顶部栏时把 `.main-container` 设为 `height: 100vh; overflow: hidden`，见 `erp-ui/src/layout/index.vue:81-84`；真实滚动发生在 `.fixed-header + .app-main`，该容器设置 `overflow-y: auto` 和固定高度，见 `erp-ui/src/layout/components/AppMain.vue:65-70`、`:86-90`。当前全仓有 71 处 `<pagination>` 调用，未发现页面显式传 `auto-scroll=false` 或指定滚动容器。
- 影响：商品、用户、日志、角色授权、店铺授权、库存、调拨、工资、公告阅读用户等长列表翻页时，用户期望看到新页第一行，但实际可能停在分页条附近或旧滚动位置。多账号多店铺排查经常需要连续翻页核对账号、组织和日志，滚动反馈不稳定会降低效率，也容易让用户误以为翻页没有生效或仍在旧数据段。已有 P2-17/P2-20 记录的是商品接口/弹窗一次性渲染全量 rows，本问题记录的是分页组件自身滚动目标与当前布局不匹配。
- 建议：分页组件应支持滚动目标容器，默认优先寻找最近的 `.app-main`、弹窗 body 或表格外层滚动容器并滚到列表顶部；也可以由页面传入 `scroll-container` 或 `scroll-target`。长表页面翻页成功后应让焦点或可视区域回到表格标题/首行，并给加载状态。弹窗内分页默认不要滚动整页，应滚动弹窗内容区或表格 body。

### P3-212 任务行进入调度日志后点击重置会丢失任务限定条件

- 现象：定时任务列表从某一任务行进入 `/monitor/job-log/index/:jobId` 后，调度日志页会先按该任务的名称和任务组过滤；但搜索区的“重置”按钮会把这两个由路由任务带入的条件一并清空。页面 URL 仍然像是某个任务的日志页，实际列表会变成全部调度日志或更宽范围日志。
- 证据：调度日志页的搜索表单把 `任务名称/jobName`、`任务组名/jobGroup`、`执行状态/status` 都作为普通 `queryForm` 字段，见 `erp-ui/src/views/monitor/job/log.vue:5-43`。`created()` 在路由 `jobId != 0` 时调用 `getJob(jobId)`，只把 `response.data.jobName/jobGroup` 写入 `queryParams` 后执行 `getList()`，没有单独保存不可重置的任务上下文，见同文件 `:196-205`。`resetQuery()` 直接 `this.resetForm("queryForm")` 再 `handleQuery()`，见 `:229-234`；后端 `SysJobLogMapper.selectJobLogList` 也只按 `jobName/jobGroup/status/invokeTarget/create_time` 过滤，`sys_job_log` 本身没有 `job_id` 过滤字段，见 `erp-modules/erp-job/src/main/resources/mapper/job/SysJobLogMapper.xml:20-47`。当前运行态 `sys_job_log=0`，本轮未通过写日志复现；结论来自只读源码路径和后端查询条件。
- 影响：管理员从某个任务查看日志时，会把当前页面理解为“这个任务的日志”。如果为了清空执行状态或时间范围点击重置，任务名称和任务组也被清掉，后续删除、清空、导出或查看详情都可能基于全量日志认知而不是当前任务认知。多任务名称相似或任务组复用时，这会削弱日志排查和审计导出的可信度。
- 建议：任务日志副页面应把路由 `jobId` 转成明确的固定上下文：后端日志表最好保存并按 `job_id` 过滤；前端至少保留 `routeJobName/routeJobGroup`，重置时只清状态和时间等临时筛选，重新回填任务名称和任务组。页面标题或筛选区应展示“当前任务：xxx / 任务组：xxx”，若用户手动改任务条件，应明确说明已切换为全局日志查询。

### P3-213 404 页面作为兜底错误页但不展示失败路径，且固定宽度布局在窄屏会溢出

- 现象：电脑端访问不存在路由、停用旧路由或菜单路径配置错误时会进入 `/404`，但页面只展示“找不到网页”和一段通用说明，没有显示当前失败路径、可能原因、返回上一页、联系管理员或复制诊断信息。页面主容器固定 `1200px` 宽并用绝对定位居中，在较窄浏览器窗口或缩放场景下容易横向溢出。
- 证据：路由兜底在动态路由生成后统一追加 `{ path: '*', redirect: '/404', hidden: true }`，见 `erp-ui/src/store/modules/permission.js:42`；常量路由注册 `/404`，见 `erp-ui/src/router/index.js:63-65`。404 页面模板只渲染图片、`404错误!`、`message()` 返回的 `找不到网页！`、固定说明和 `router-link to="/"` 的“返回首页”，没有读取 `$route.fullPath`、来源路由、菜单名或权限信息，见 `erp-ui/src/views/error/404.vue:1-33`。同文件样式把 `.wscn-http404` 固定为 `width: 1200px; padding: 0 50px`，外层 `.wscn-http404-container` 使用 `position:absolute; top:40%; left:50%; transform: translate(-50%,-50%)`，见 `:41-55`；没有媒体查询或响应式降级。报告中 P3-200 覆盖的是 401 无权限页未接入权限失败链路，本问题单独记录真实不存在/旧路由/菜单错配时的 404 兜底体验。
- 影响：多账号多店铺验收经常会遇到菜单路径漂移、停用旧入口、权限过滤后路由不存在等情况。用户看到当前 404 时无法判断是自己没权限、菜单配置错、旧链接过期还是页面未交付；测试人员也不能直接从错误页复制失败路径给管理员。窄屏或浏览器缩放下页面溢出会让“返回首页”按钮和说明区域可见性变差，进一步降低错误恢复效率。
- 建议：404 页应展示当前访问路径、来源入口和中性原因说明，并提供“返回上一页 / 返回首页 / 复制诊断信息 / 联系管理员”动作。布局改为响应式容器，图片和说明在窄屏下纵向排列，避免固定 1200px 导致横向溢出；对从菜单进入的 404，可提示“菜单配置可能失效”，并把菜单名、路径、组件路径写入诊断信息。

### P3-214 标签页持久化按浏览器缓存恢复，切换账号后可能带出上一账号的标签上下文

- 现象：布局设置中开启“持久化标签页”后，电脑端会把已打开标签写入浏览器本地缓存；但登录、退出和前端登出流程不会按账号清理或隔离这份缓存。门店前台、仓库、财务等共用电脑切换账号时，新账号可能恢复上一账号留下的页面标签、路由 query 和页面标题。
- 证据：默认配置 `tagsViewPersist` 为 `false`，但设置抽屉提供开关并保存到本机 `layout-setting`，见 `erp-ui/src/settings.js:28-30`、`erp-ui/src/layout/components/Settings/index.vue:141-149`、`:267-288`。`TagsView.initTags()` 在该开关为真时调用 `tagsView/loadPersistedViews`，见 `erp-ui/src/layout/components/TagsView/index.vue:211-214`。标签 store 使用固定本地键 `tags-view-visited`，保存 `path/fullPath/name/title/query/meta`，见 `erp-ui/src/store/modules/tagsView.js:4-18`、`:10-13`；恢复时直接遍历缓存并 `ADD_VISITED_VIEW`，见同文件 `:262-266`，没有校验当前账号、角色、组织、店铺或当前动态路由。登录流程只清理已选店铺和锁屏状态，退出流程只清 token/roles/permissions，见 `erp-ui/src/store/modules/user.js:76-93`、`:145-164`；全仓检索 `tags-view-visited` 的清理点只在设置抽屉关闭持久化或重置设置时出现。
- 影响：这不会绕过后端权限，但会泄露上一账号正在使用的页面名称、路径和部分 query，例如店铺授权页可能带有 `userId/userName`。权限更小的新账号点击旧标签时可能进入 404/401、空白或权限失败页面，用户会误判为菜单配置异常、账号权限异常或数据丢失。多账号多店铺验收里，这属于浏览器级偏好和账号级工作台状态混在一起的问题。
- 建议：标签页持久化应按账号隔离，缓存键至少包含当前 `userId`，更严格时包含租户/组织/店铺或权限版本；退出、切换账号和选择组织时清理非当前账号标签。恢复标签前应与当前动态路由和权限菜单求交集，过滤无权限、停用、旧路径和敏感 query；需要跨账号保留的只是布局偏好，不应保留上一账号的业务标签上下文。也可以在设置文案中明确“保存到本机浏览器”，并提供“清空已保存标签”动作。

### P3-215 关闭页签时不会同步关闭持久化标签，重新开启会恢复旧业务标签

- 现象：布局设置抽屉里“持久化标签页”依赖“开启页签”，当用户关闭“开启页签”后，持久化开关只是被禁用，真实值仍可能保持为开启。保存配置后页面不显示标签栏，但本地 `tags-view-visited` 不会被清理；之后用户重新开启页签时，旧标签会再次恢复。
- 证据：设置抽屉模板把“开启页签”绑定到 `tagsView`，把“持久化标签页”绑定到 `tagsViewPersist` 并仅使用 `:disabled="!tagsView"` 禁用，见 `erp-ui/src/layout/components/Settings/index.vue:63-70`。`tagsView` 的 setter 只提交 `settings/changeSetting`，没有同步把 `tagsViewPersist` 改成 `false`，见同文件 `:152-161`；`saveSetting()` 只有在 `!this.tagsViewPersist` 时删除 `tags-view-visited`，即关闭 `tagsView` 但 `tagsViewPersist=true` 时不会清缓存，见 `:267-288`。`Layout` 只在 `needTagsView` 为真时挂载 `<tags-view>`，见 `erp-ui/src/layout/index.vue:5-8`；`TagsView.initTags()` 一旦重新挂载且 `tagsViewPersist` 为真，就会恢复本地标签，见 `erp-ui/src/layout/components/TagsView/index.vue:211-214`。
- 影响：用户关闭页签通常会理解为“不要保留这组工作标签”，但当前实现只是临时隐藏标签栏，旧业务上下文仍留在本机浏览器。多账号多店铺巡检时，管理员可能为了简化界面关闭页签，后续再打开时又看到历史店铺授权、用户资料、日志或库存页面标签，容易误以为系统自动记住了当前账号任务，也会叠加 P3-214 的跨账号缓存风险。
- 建议：父子开关要保持一致：关闭 `tagsView` 时自动关闭 `tagsViewPersist` 并提示是否清空已保存标签，或至少在保存时按 `!tagsView || !tagsViewPersist` 清理 `tags-view-visited`。重新开启页签时若存在旧缓存，应明确询问“恢复上次标签 / 清空后继续”，并在设置文案里说明这是本机浏览器级状态。

### P3-216 面包屑多级路径解析不支持连字符，多个隐藏副页面会丢失正确层级

- 现象：电脑端面包屑组件对超过两段的路径不直接使用 Vue Router 的 `matched`，而是用正则拆路径后到动态路由树里递归匹配。该正则只识别 `\w`，遇到 `user-auth`、`role-auth`、`dict-data`、`job-log`、`stock-log`、`gen-edit`、`fixed-asset` 等带连字符的路径时会把一段拆成多段，导致面包屑无法稳定显示真实父级和当前副页面。
- 证据：`Breadcrumb.getBreadcrumb()` 在 `pathNum > 2` 时使用 `const reg = /\/\w+/gi` 生成 `pathList`，再调用 `getMatched(pathList, this.$store.getters.defaultRoutes, matched)`，见 `erp-ui/src/components/Breadcrumb/index.vue:32-52`；匹配逻辑只比较 `item.path == pathList[0]` 或路由名小写，见同文件 `:63-70`。当前桌面动态隐藏路由包含多条连字符路径：`/system/user-auth`、`/system/role-auth`、`/system/dict-data`、`/monitor/job-log`、`/inventory/stock-log`、`/tool/gen-edit`，见 `erp-ui/src/router/index.js:108-206`；报告中已验证这些副页面可直连或作为功能入口存在。只读 Node 复核同一正则得到：`/system/user-auth/role/122 -> ['/system','/user','/role','/122']`，`/monitor/job-log/index/0 -> ['/monitor','/job','/index','/0']`，`/inventory/stock-log -> ['/inventory','/stock']`，`/oa/fixed-asset/config -> ['/oa','/fixed','/config']`，均不是实际路由段。
- 影响：用户在分配角色、分配用户、字典数据、调度日志、库存变动日志、代码生成编辑、固定资产配置/维修等副页面中，会看到不完整或错误的面包屑层级。多账号多店铺审计时，这些副页面通常由按钮或标签页进入，面包屑本应帮助用户确认“当前在维护哪个主模块的哪类副流程”；层级错乱会降低返回判断和问题定位效率，也会让 URL、标签页和面包屑三者继续不一致。P3-01 记录的是菜单标题与面包屑命名不一致，本问题记录的是面包屑路径解析算法对连字符路由不兼容。
- 建议：面包屑优先使用 `this.$route.matched`，对隐藏副路由通过 `meta.activeMenu` 或路由配置补充父级标题，而不要自行用正则拆 URL。若确需自定义匹配，按 `/` 分段并保留完整段名，支持连字符、动态参数和空子路径；同时为 `user-auth/role`、`role-auth/user`、`dict-data/index`、`job-log/index`、`stock-log`、`gen-edit/index`、`fixed-asset/*` 增加面包屑回归用例。

### P3-217 内链 iframe 固定高度不跟随页签和窗口变化，外链页容易出现二层滚动或底部遮挡

- 现象：电脑端菜单支持把 `http(s)` 地址作为内链嵌入 iframe，但 iframe 容器高度在组件初始化时用 `document.documentElement.clientHeight - 94.5` 写死。当前主布局固定头部高度分为无页签 `96px`、有页签 `130px`，并且用户可以在设置里开关页签、固定 Header、底部版权和浏览器窗口尺寸；iframe 高度不会随这些状态重新计算。
- 证据：后端 `SysMenuServiceImpl.buildMenus()` 会为内链菜单设置 `component=InnerLink` 和 `meta.link`，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java:215-227`；非根节点内链也会在 `getComponent()` 中返回 `InnerLink`，见同文件 `:497-507`。前端 `AppMain` 遇到 `$route.meta.link` 时不渲染普通 `router-view`，而是把路由加入 `tagsView/iframeViews`，见 `erp-ui/src/layout/components/AppMain.vue:1-10`、`:37-41`；`IframeToggle` 再渲染 `InnerLink`，见 `erp-ui/src/layout/components/IframeToggle/index.vue:1-9`。但 `InnerLink` 的高度只在 `data()` 中计算一次 `document.documentElement.clientHeight - 94.5 + "px;"`，见 `erp-ui/src/layout/components/InnerLink/index.vue:23-27`，没有监听 resize、页签开关、固定头部或版权变化。当前 AppMain 在固定头部下实际使用 `height: calc(100vh - 96px)`，开启页签后使用 `height: calc(100vh - 130px)`，见 `erp-ui/src/layout/components/AppMain.vue:65-90`。
- 影响：系统监控控制台、系统接口或后续任何内链业务页面即使目标服务可用，也可能在 ERP 外层容器里出现额外滚动条、底部内容被遮住、加载遮罩位置不准或窗口缩放后高度不更新。多账号多店铺场景下，管理员常在系统监控、接口文档和业务系统之间切换排查问题，iframe 页面不稳定会让用户误判为目标系统卡住或页面未加载完成。本问题不同于 P2-10/P3-08 的外链目标不可达，记录的是内链容器本身对当前桌面布局不适配。
- 建议：iframe 容器高度应由 CSS 跟随 `.app-main` 可用空间，例如 `height: 100%` 或 `calc(100vh - var(--header-height))`，并统一从布局状态计算 header/tags/footer 高度。监听窗口 resize、页签开关、固定 Header 和版权开关变化；加载遮罩应有超时/失败状态，iframe 加载失败或被目标站点禁止嵌入时给出“在新窗口打开 / 复制地址 / 联系管理员”的恢复动作。

### P3-218 锁屏和布局设置本地缓存解析没有容错，坏缓存会让桌面端启动失败

- 现象：电脑端锁屏状态和布局设置都保存在浏览器本地缓存，但 Vuex 模块初始化时直接对缓存字符串做 `JSON.parse`，没有 `try/catch`、版本校验或失败后清理。只要 `screen-lock` 或 `layout-setting` 被浏览器插件、手工调试、旧版本残留或异常写入破坏，应用还没渲染到登录页、首页或“重置配置”按钮就可能抛错。
- 证据：锁屏 store 在模块顶层读取 `localStorage.getItem('screen-lock')` 并直接 `JSON.parse(... || 'false')`，见 `erp-ui/src/store/modules/lock.js:1-9`；布局设置 store 在模块顶层执行 `const storageSetting = JSON.parse(localStorage.getItem('layout-setting')) || ''`，见 `erp-ui/src/store/modules/settings.js:1-21`。这两个模块都在 Vuex store 创建前被 `erp-ui/src/store/index.js:3-23` 同步 import，`main.js` 再把 store 注入根 Vue 实例，见 `erp-ui/src/main.js:10-18`、`:65-70`。设置抽屉虽然有“重置配置”，但它依赖应用先成功启动，见 `Settings/index.vue:290-294`；而保存配置也是手工拼 JSON 字符串写入 `layout-setting`，见 `Settings/index.vue:267-288`。当前报告未写入坏缓存复现，本条来自启动路径和异常边界的只读代码审计。
- 影响：共用电脑、浏览器异常恢复或版本升级后，单个本地缓存坏值就可能让用户看到白屏或只有外层空壳，且无法在页面内清除缓存。多账号多店铺验收时，这类问题会被误判为后端挂了、账号权限坏了或菜单路由失败；门店/仓库一线用户也很难知道需要清浏览器本地存储。
- 建议：封装安全的本地 JSON 读取工具，对 `screen-lock`、`layout-setting`、`tags-view-visited` 等缓存统一做 `try/catch`、schema/version 校验和默认值回退；解析失败时删除对应 key，并给出一次性提示“本机布局设置已重置”。设置保存应使用 `JSON.stringify` 生成缓存内容，避免手工拼接字符串；启动失败兜底页或登录页也应提供“清除本机缓存后重试”的恢复入口。

### P3-219 布局设置保存反馈不可靠，loading 会立即关闭且没有保存成功提示

- 现象：布局设置抽屉的“保存配置”按钮会显示“正在保存到本地，请稍候...”，但保存逻辑把 `setTimeout(this.$modal.closeLoading(), 1000)` 写成了立即调用 `closeLoading()`，并没有 1 秒后的延迟反馈；保存完成后也没有成功提示、失败提示或刷新说明。用户很难判断配置是已经保存、需要刷新，还是保存无效。
- 证据：设置抽屉按钮在 `erp-ui/src/layout/components/Settings/index.vue:108-109` 暴露“保存配置 / 重置配置”。`saveSetting()` 在同文件 `:267-288` 先调用 `this.$modal.loading(...)`，再同步写 `layout-setting`，最后执行 `setTimeout(this.$modal.closeLoading(), 1000)`；由于传入 `setTimeout` 的是 `closeLoading()` 的返回值，遮罩会立即关闭。全局 modal 插件 `erp-ui/src/plugins/modal.js:70-82` 的 `loading()` 创建 Element Loading，`closeLoading()` 直接 `loadingInstance.close()`，没有返回回调函数。对照多数业务表单保存都会调用 `this.$modal.msgSuccess(...)`，例如系统配置、字典、岗位、菜单、用户、店铺授权等页面；设置抽屉保存没有同类成功提示。当前只读审计未点击写入本地设置，结论来自前端控制流。
- 影响：设置抽屉已经存在多项非立即可感知或需刷新才确认的配置，例如页签、持久化标签、底部版权、导航模式和主题。保存反馈不稳定会放大 P3-115、P3-214、P3-215、P3-218 这些布局设置问题：用户点了保存却不知道哪些设置已落本机、哪些要刷新、哪些其实不生效。多账号共用电脑时，本地偏好会影响后续账号，缺少明确保存结果也会增加排查成本。
- 建议：保存配置应使用明确的同步结果反馈：本地写入成功后 `msgSuccess("布局设置已保存")`，需要刷新才能完整生效的项要提示“刷新后生效”；失败时捕获 `localStorage` 异常并给出可恢复提示。若保留 loading，应写成 `setTimeout(() => this.$modal.closeLoading(), 1000)` 或直接在同步写入后关闭；重置配置也应使用函数回调而不是字符串 `setTimeout`，并说明会刷新页面。

### P3-220 根应用全局挂载隐藏 ThemePicker，空白路由会残留“清空/确定”噪声

- 现象：根组件在所有页面外层全局挂载一个隐藏的主题色选择器。多个路由渲染失败或只剩空壳时，页面文本/自动化巡检会反复只看到主题色控件的“清空 / 确定”，而不是明确的路由失败、权限失败、组织上下文缺失或空数据状态。它不一定是空白页成因，但会污染失败状态的可见线索和排障判断。
- 证据：`erp-ui/src/App.vue:1-5` 在 `<router-view />` 之外固定渲染 `<theme-picker />`，并在 `:17-18` 仅用 `#app .theme-picker { display: none; }` 隐藏触发器；真实设置抽屉已经在 `erp-ui/src/layout/components/Settings/index.vue:55` 单独渲染可用的 `<theme-picker @change="themeChange" />`。`erp-ui/src/components/ThemePicker/index.vue:1-7` 的组件本体是 Element `el-color-picker`，其下拉层使用 `popper-class="theme-picker-dropdown"`，样式只在 `:167-168` 隐藏部分链接按钮。报告中已有多处运行态记录：`/system/shop`、`/system/notice`、`/system/salary`、`/oa/labor-contract`、销售链路和调拨链路等失败状态都曾只剩隐藏主题色控件“清空 / 确定”，其中薪资页记录 `#app` 仅有 `.theme-picker` 子节点。
- 影响：多账号多店铺巡检时，页面失败后的唯一文本会变成主题色控件，容易把真实问题误读成业务页空数据、按钮隐藏或权限缺失；截图沟通也会出现“为什么页面只有清空/确定”的二次解释成本。对自动化验收来说，简单依赖正文文本长度、按钮文本或 `#app` 子节点判断页面是否正常，会被这个全局隐藏控件干扰，导致空白路由、菜单加载失败和组织上下文失败更难分类。
- 建议：根应用不应全局挂载隐藏 ThemePicker；主题色选择器只放在设置抽屉等真实可操作位置。若需要启动时应用主题，应抽成无 UI 的 theme service 或 store action，只注入样式，不渲染 `el-color-picker` 控件。空白/异常路由应由路由错误页、加载失败页或组织上下文提示承接，并在自动化巡检中把主题色控件从业务正文判定中排除。

### P3-221 通用下载方法吞掉后端错误，页面无法可靠判断导出是否真的成功

- 现象：电脑端大多数导出、模板下载和部分移动端导出都走全局 `this.download(...)`。这个公共方法在后端返回 JSON 错误时会弹出错误消息，但不会把 Promise 标记为失败；如果后端错误响应不是精确的 `application/json` MIME，还可能被当成普通文件保存。调用页面很难区分“文件已成功下载”和“后端返回了错误但公共方法已处理完”。
- 证据：`erp-ui/src/main.js:15` 引入 `download` 并在 `:33` 挂到 `Vue.prototype.download`。`erp-ui/src/utils/request.js:143-168` 的 `download()` 调用 `service.post(... responseType: 'blob')` 后，`:152-160` 在 `!isBlob` 时解析 JSON 并 `Message.error(errMsg)`，但没有 `throw` 或 `return Promise.reject(...)`，随后 `:162` 直接关闭 loading 并结束 `then`。`blobValidate()` 只判断 `data.type !== 'application/json'`，见 `erp-ui/src/utils/common.js:234-237`，对 `application/json;charset=utf-8`、`text/plain`、`text/html` 等错误响应不稳。待办/已办采购页已经把导出 loading 绑定在返回 Promise 的 `finally` 上，见 `erp-ui/src/views/oa/todo/index.vue:206-211`、`erp-ui/src/views/oa/done/index.vue:208-213`；移动端导出还包了一层 `Promise.resolve(this.download(...)).catch(...)`，见 `erp-ui/src/views/mobile/feature/index.vue:778-787`。全局检索显示客户、销售、采购、库存、工资、日志、用户、角色、字典、固定资产等页面都直接使用 `this.download(...)`。
- 影响：导出是数据离开系统的关键流程。当前实现会让页面层无法根据 Promise 结果做“下载成功/失败/重试/保留 loading/刷新状态”的一致处理；例如待办、已办导出即使后端返回业务错误，也只会进入 `finally` 清 loading，后续页面无法知道是否需要提示用户重试。多账号多店铺场景下，导出失败常见原因包括组织上下文缺失、权限不足、筛选范围过大或后端超时；如果公共下载把这些错误吞掉或把错误体保存成文件，会增加一线用户和管理员判断成本。
- 建议：`download()` 在解析到业务错误时应 `return Promise.reject(new Error(errMsg))`，并把 loading 关闭放进 `finally`；MIME 判断应使用 `data.type && data.type.indexOf('application/json') >= 0` 或优先读取响应头，并兼容 JSON charset、纯文本错误和 HTML 网关错误。页面层应统一处理下载成功、失败和重试状态，必要时显示“未生成文件”的明确反馈；移动端和待办/已办这类依赖 Promise 状态的页面应补回归用例。

### P3-222 全局重复提交判定缺少账号和组织维度，快速重试或切换上下文可能被误拦截

- 现象：前端通用请求拦截器会对所有 `POST/PUT` 做 1 秒内重复提交拦截，但判定键只包含请求 URL 和请求体，不包含当前账号、token、`Dept-NumId`、请求方法、组织类型或请求是否已经失败/完成。多账号、多店铺/仓库和共用浏览器场景下，相同表单数据在不同组织、不同账号或失败后快速重试时，可能被前端直接拦截为“数据正在处理，请勿重复提交”，请求不会到达后端。
- 证据：`erp-ui/src/utils/request.js:36-41` 会把当前 token 和 `getSelectedInventoryDeptId()` 写入 `Authorization`、`Dept-NumId` 请求头；但重复提交对象只保存 `url/data/time`，见同文件 `:50-55`，比较时也只看 `s_data/requestObj.data`、`s_url/requestObj.url` 和 `time < interval`，见 `:62-75`。拦截状态存放在单个 `sessionStorage` key `sessionObj`，`cache.session.getJSON/setJSON` 见 `erp-ui/src/plugins/cache.js:19-29`；登录流程只清理选中组织，不清理 `sessionObj`，见 `erp-ui/src/store/modules/user.js:83-92`，退出流程也只清 token/角色/权限，见 `:146-153`。全仓只发现登录接口显式设置 `repeatSubmit: false`，见 `erp-ui/src/api/login.js:4-13`；静态统计 `erp-ui/src` 中有 113 个 `POST/PUT` 方法声明，覆盖商品、采购、销售、库存、调拨、工资、考勤、合同、固定资产、用户、角色、字典、菜单等页面。
- 影响：重复点击保护本应防止同一用户同一动作被连续提交，但当前实现会把身份和组织上下文不同的请求也看成同一请求。比如同一账号在门店/仓库间切换、管理员代多个用户执行相同表单保存、请求因权限/组织缺失失败后立即重试，都可能得到前端统一的“数据正在处理”提示，而不是后端真实校验结果。多账号多店铺验收时，这会让用户误以为上一笔请求仍在处理，也会让自动化巡检误判为接口防重或后端幂等生效。
- 建议：重复提交判定键至少纳入 `method + url + normalizedData + currentUserId/tokenHash + Dept-NumId + deptType`，并在登录、退出、切换组织时清理当前防重状态。更稳妥的是按具体按钮/表单维护 in-flight 状态，只有同一账号、同一组织、同一动作的未完成请求才拦截；失败响应应立即释放重试，真正的业务幂等应由后端通过请求幂等 key、业务唯一约束和状态机保证。

### P3-223 Cron 生成器反解析 `星期#第几周` 时会把周几和第几周倒置

- 现象：定时任务新增/修改弹窗提供“Cron 表达式生成器”，支持周规则“第 N 周的星期 X”。生成器新建表达式时输出 `星期#第几周`，但编辑已有表达式时反解析逻辑把 `#` 左侧写进“第几周”，把右侧写进“星期几”。管理员只要打开生成器查看并调整同一表达式，就可能把原本“每月第 1 个星期一”变成“每月第 2 个星期日”等不同调度时间。
- 证据：定时任务表单在 `erp-ui/src/views/monitor/job/index.vue:200-248` 通过“生成表达式”打开 `<crontab :expression="expression">`。周组件的 UI 字段是 `average01=第几周`、`average02=星期几`，输出时返回 `this.average02 + '#' + this.average01`，见 `erp-ui/src/components/Crontab/week.vue:40-47`、`:184-188`。但主组件反解析 `#` 时把 `indexArr[0]` 赋给 `average01`、`indexArr[1]` 赋给 `average02`，见 `erp-ui/src/components/Crontab/index.vue:249-267`。只读脚本模拟 `2#1` 得到 UI 为“第 2 周 / 星期日”，再次生成会变成 `1#2`；后端 `CronUtils` 使用 Quartz 标准表达式校验，`SysJobController.add/edit` 只会判断表达式是否语法有效，见 `erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java:79-124`。当前运行态 `sys_job` 3 条内置任务分别是 `0/10 * * * * ?`、`0/15 * * * * ?`、`0/20 * * * * ?`，暂未命中 `#` 样本。
- 影响：这是低频但高隐蔽性的调度配置风险。`#` 语法通常用于月度任务，例如每月第一个工作日附近的同步、结算、通知或巡检；表达式仍然是合法 Cron，保存时不会被后端拒绝，只有实际执行日期错了才会暴露。多账号多店铺系统里，定时任务常影响考勤同步、库存巡检、通知和数据清理，调度日期被悄悄改错会让业务人员难以追溯是“任务没执行”还是“表达式被生成器改了”。
- 建议：修正 `Crontab/index.vue` 对 `#` 的反解析：`indexArr[0]` 应写入星期选择 `average02`，`indexArr[1]` 应写入第几周 `average01`；同时为 `2#1`、`6#3`、`1L`、`?` 与日/周互斥等周规则增加前端单元或组件回归。生成器“确定”前应调用和后端一致的 Cron 校验，并显示最近 5 次运行时间与后端 Quartz 计算结果一致。

### P3-224 系统基础资料状态枚举和排序范围主要靠前端控件，后端缺少统一兜底

- 现象：岗位、字典类型、字典数据和系统参数这些基础资料页在前端用单选、下拉或数字控件限制状态、系统内置和排序值；但后端实体没有对 `status/configType/isDefault` 做枚举校验，也没有对 `postSort/dictSort` 做非负校验。构造请求可以绕过前端控件写入无法被字典标签正常解释的状态值或负排序。
- 证据：岗位新增/编辑接口使用 `@Validated @RequestBody SysPost`，但 `SysPost` 只对 `postCode/postName/postSort` 做必填/长度/非空校验，`status` 没有 `@Pattern`，`postSort` 没有 `@Min(0)`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysPostController.java:74-108`、`erp-modules/erp-system/src/main/java/com/erp/system/domain/SysPost.java:37-80`。字典类型新增/编辑接口同样只校验名称和类型格式，`status` 没有枚举约束，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictTypeController.java:72-98`、`erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysDictType.java:33-79`。字典数据新增/编辑接口中 `dictSort/isDefault/status` 也缺少范围或枚举校验，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictDataController.java:92-110`、`erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysDictData.java:25-53`、`:147-154`。系统参数新增/编辑只校验键名和值，`configType` 没有 `Y/N` 约束，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:96-122`、`erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java:36-93`。运行态当前 `sys_post/sys_dict_type/sys_dict_data/sys_config` 暂无异常状态、负排序或异常内置标记样本，本轮没有构造写入。
- 影响：这些字段驱动列表标签、筛选、缓存、导出转换、删除保护和页面可用性。存量暂时干净不代表接口安全；一旦通过接口、脚本或异常前端版本写入 `status=2`、`configType=X`、负排序或多个默认字典项，前端会出现空标签、筛选失真、排序异常或内置保护绕过。多账号多店铺环境里，基础资料一旦被异常状态污染，会影响用户、岗位、字典、参数、调拨审批、薪资和日志等多个页面的数据库与前端展示对应关系。
- 建议：系统基础资料实体和服务层统一增加枚举/范围校验：`status` 只允许 `0/1`，`configType/isDefault` 只允许 `Y/N`，排序字段必须大于等于 0；保存前对字典默认项做同类型唯一默认约束。数据库可补充 `CHECK` 或迁移脚本巡检异常值，导入、脚本和接口保存都走同一套校验。前端保留控件限制，但不能作为唯一边界。

### P3-225 部门组织树同级排序值已有重复，店铺选择和授权树顺序不稳定

- 现象：部门管理页把“排序”作为组织树顺序的主要字段，组织选择页、店铺授权树和部门列表也按 `parent_id + order_num` 排序；但同一父节点下没有唯一约束或保存前校验，当前运行库已经有同级重复排序。管理员看到的公司/仓库顺序会依赖数据库返回的非稳定并列顺序，而不是一个明确的组织顺序。
- 证据：运行态 `sys_dept` 中 `parent_id=100` 下已有两组重复排序：`order_num=1` 同时包含 `101 / 金英灵韵 / COMPANY` 与 `200 / 杭州名田 / COMPANY`，`order_num=2` 同时包含 `102 / 上海沐茶 / COMPANY` 与 `104 / 仓库 / WAREHOUSE`。部门管理页排序列是可编辑数字框，见 `erp-ui/src/views/system/dept/index.vue:90-93`；保存排序只收集变更后的 `deptIds/orderNums` 并提交，见 `erp-ui/src/views/system/dept/index.vue:410-430`。后端 `SysDeptController.updateSort()` 只把逗号字符串拆成数组后调用服务，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java:140-150`；`SysDeptServiceImpl.updateDeptSort()` 只校验数组长度、数据范围和逐条更新 `order_num`，没有检查同父级唯一或重新规范化排序，见 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java:410-433`。部门列表、门店列表、仓库列表、授权组织树均按 `d.parent_id, d.order_num` 排序，没有 `dept_id` 或类型作为稳定兜底，见 `erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml:31-75`。
- 影响：多店铺系统里，组织顺序会出现在登录后的组织选择、店铺授权、用户所属部门和仓库选择等多个入口。重复排序会让“默认看到哪个公司/仓库在前”不明确，尤其 `104 / 仓库` 与公司节点同级且排序并列时，管理员无法判断这是刻意排序还是历史脏数据。结合 P3-108 默认组织只取保存数组第一项的问题，组织树顺序不稳定还可能影响用户对默认组织和授权优先级的理解。
- 建议：部门组织树排序应按同一父节点做唯一或规范化处理。前端保存排序前提示同级重复并提供“一键重新编号”；后端保存时按 `parent_id + order_num` 校验冲突，或接收完整同级节点顺序后统一重排为连续值。所有组织树查询增加稳定兜底排序，如 `parent_id, order_num, dept_id`，并提供一次迁移脚本清理当前重复排序。

### P3-226 库存主数据状态和分类排序主要靠前端控件，后端缺少枚举/范围兜底

- 现象：商品、分类、供应商、客户等库存主数据页面都把状态当作固定枚举展示，供应商还多一套“合作中/暂停/终止”，分类还有排序字段；但保存接口主要依赖前端单选、下拉和数字输入限制，后端实体没有状态枚举校验，服务层也没有统一白名单。构造请求可以把这些字段写成前端无法解释的值。
- 证据：分类页只在前端提供 `正常/停用` 和 `orderNum >= 0` 输入，见 `erp-ui/src/views/inventory/category/index.vue:119-132`；商品、客户、供应商页面也分别用 `status` 单选或下拉，供应商另有 `cooperationStatus` 下拉，见 `erp-ui/src/views/inventory/product/index.vue:318-321`、`erp-ui/src/views/inventory/customer/index.vue:127-130`、`erp-ui/src/views/inventory/supplier/index.vue:163-175`。后端 `InvProductCategory.status/orderNum`、`InvProduct.status`、`InvCustomer.status`、`InvSupplier.status/cooperationStatus` 都没有 `@Pattern/@Min` 等约束；`InvProductCategoryServiceImpl.saveCategory` 和 `InvProductServiceImpl.applyDefaultProductStatus` 只在空值时默认 `0`，不校验非法值。Mapper 更新会在字段非空时直接写入 `status/cooperation_status/order_num`，例如 `InvProductCategoryMapper.xml`、`InvProductMapper.xml`、`InvCustomerMapper.xml`、`InvSupplierMapper.xml`。MySQL 当前存量仍正常：`inv_product_category.status=0` 30 条且 `order_num` 为 10-300、`inv_product.status=0` 162 条、`inv_supplier.status=0/cooperation_status=0` 17 条、`inv_customer=0`。
- 影响：非法状态不会马上表现为 SQL 错误，却会让页面和流程判断分裂：列表标签通常把非 `0` 都显示成“停用”，筛选只提供 `0/1`，供应商状态可能显示“未知”，采购/销售又按 `status == 0` 判断是否可用。分类排序若被写成负数或极端值，也会影响商品、库存和分类维护树的显示顺序。多账号维护主数据时，这类脏值会让另一个账号看到“停用/未知/排序异常”，但无法从页面判断是谁写入以及该如何修复。
- 建议：库存主数据保存接口统一做后端枚举和范围校验：`status` 只允许 `0/1`，`cooperationStatus` 只允许 `0/1/2`，分类 `orderNum` 设定非负和合理上限；服务层保存前清洗空白并拒绝非法值，数据库巡检或约束同步兜底。列表和详情遇到历史非法状态时应显示“异常状态”并提供治理入口，而不是默认为停用。

### P3-50 字典和参数“刷新缓存”复用删除权限，权限粒度不符合实际动作

- 现象：字典管理和参数设置页面都有“刷新缓存”按钮，但前端按钮权限和后端接口权限都复用删除权限；想允许管理员刷新缓存，就必须同时授予删除字典/参数的能力。
- 证据：`erp-ui/src/views/system/dict/index.vue:100-108` 的“刷新缓存”使用 `v-hasPermi="['system:dict:remove']"`，`erp-ui/src/views/system/config/index.vue:95-103` 的“刷新缓存”使用 `v-hasPermi="['system:config:remove']"`；对应 API `erp-ui/src/api/system/dict/type.js:46-51` 和 `erp-ui/src/api/system/config.js:54-59` 都用 `DELETE /refreshCache`。后端 `SysDictTypeController.refreshCache` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictTypeController.java:115-121` 要求 `system:dict:remove`，`SysConfigController.refreshCache` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysConfigController.java:140-146` 要求 `system:config:remove`。当前 MySQL 复核 `system:dict:*`、`system:config:*` 仍授权给 `common,zjl`，实际用户 `ry` 通过总经理角色同时持有刷新缓存和删除权限。
- 影响：刷新缓存是运维型低破坏动作，删除是破坏性写动作；两者绑定会让最小权限配置失真。以后如果给门店运维或业务管理员开放缓存刷新，就会顺带开放删除入口；如果拒绝删除权限，页面又无法提供“刷新缓存”自助能力。
- 建议：新增独立权限 `system:dict:refresh`、`system:config:refresh`，前端按钮和后端接口都改用该权限；接口方法可改为 `POST /refreshCache` 或保持 DELETE 但权限语义必须独立。角色授权时把刷新缓存、删除数据、导出数据分开配置。

### P3-51 个人中心头像上传用 FormData 但请求头写成 urlencoded，上传链路不稳定

- 现象：个人中心头像弹窗会裁剪图片并提交 `FormData`，但头像上传 API 手动把 `Content-Type` 设为 `application/x-www-form-urlencoded`；后端接口却按 `MultipartFile avatarfile` 接收。请求体格式和接口声明不一致，头像上传在浏览器/axios 处理差异下容易失败。
- 证据：`erp-ui/src/views/system/user/profile/userAvatar.vue:134-138` 通过 `new FormData()` 追加 `avatarfile` 后调用 `uploadAvatar(formData)`；`erp-ui/src/api/system/user.js:104-111` 对 `/system/user/profile/avatar` 使用 `POST`，但 headers 写成 `application/x-www-form-urlencoded`。项目当前 axios 版本为 `0.30.3`，全局默认 `Content-Type` 为 `application/json;charset=utf-8`；同项目商品导入接口 `erp-ui/src/api/inventory/product.js:30` 明确使用 `multipart/form-data`。后端 `SysProfileController.avatar` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java:151-168` 要求登录并读取 `@RequestParam("avatarfile") MultipartFile file`，随后调用文件服务上传。2026-06-25 复核运行库：25 个有效用户的 `sys_user.avatar` 均为空，`sys_menu` 中 `file:%` 权限节点为 0，且当前 schema 不存在 `sys_file_meta/sys_file_version/sys_file_download_log` 文件生命周期表。
- 影响：用户点击头像上传时可能看到“上传图片异常/文件服务异常”或请求无法被后端解析，且页面没有解释是请求格式问题；多账号上线后，普通用户自助维护头像的体验不稳定。头像 URL 又只落在 `sys_user.avatar`，当前没有文件元数据可从电脑端追踪来源、大小或清理状态。
- 建议：头像上传 API 不要手动写 `application/x-www-form-urlencoded`，应让浏览器自动设置 multipart boundary 或显式使用 `multipart/form-data`；后端保留图片格式和大小校验。头像上传结果建议和文件元数据/清理策略打通，至少能追踪当前头像文件是否仍存在。

### P3-52 通知公告只能全局发布，缺少按门店、角色或用户定向的收件范围

- 现象：通知公告新增/编辑只维护标题、类型、状态和内容；顶部公告接口返回全部正常公告，没有按当前账号所属门店、角色或指定用户过滤。系统可以发布全局公告，但页面和数据模型没有“发布给谁”的字段，也没有在管理页标明公告会推给全体登录用户。
- 证据：公告表 `sys_notice` 当前字段只有 `notice_id/title/type/content/status/create/update/remark`，没有 `target_scope`、`dept_id`、`role_id`、`user_id` 或收件人关联表；字典只区分 `通知/公告` 和 `正常/关闭`。新增公告弹窗 `erp-ui/src/views/system/notice/index.vue:137-177` 只包含公告标题、公告类型、状态和内容。顶部公告 `SysNoticeController.listTop` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:90-102` 调用 `selectNoticeListWithReadStatus(userId, 5)`，对应 SQL `erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml:20-46` 仅按 `n.status='0'` 查全部正常公告。HeaderNotice 在 `erp-ui/src/layout/components/HeaderNotice/index.vue:69-94` 直接加载这批公告并支持“全部已读”。
- 影响：多账号多店铺场景下，仓库公告、某门店停业通知、运营角色通知或某用户待办提醒都只能变成全员可见公告，容易误发给无关门店/角色；阅读用户列表也只能统计“谁读过”，无法区分“应该读的人”和“无关但看到了的人”。
- 建议：保留全局公告能力，但增加明确收件范围：全体、角色、部门/门店/仓库、指定用户四类至少要能表达一种或多种；顶部未读数、公告列表、已读用户和“全部已读”都按收件范围过滤。管理页应显示“全体公告/门店公告/角色公告”等范围标签，新增公告默认范围要有清晰提示，避免管理员误以为公告只发给当前组织。

### P3-53 代码生成导入表列表会暴露流程引擎表和清理备份表，缺少业务表白名单

- 现象：系统工具父菜单当前停用隐藏，但 `common / 普通角色`、`zjl / 总经理` 和用户 `ry` 的有效角色仍持有完整 `tool:gen:*` 权限；“导入表”弹窗的数据源只排除 `qrtz_%` 和 `gen_%`，会把当前数据库里的 Activiti/Flowable 内部表、清理备份表等非交付业务表一起列为可导入对象。
- 证据：当前库 `gen_table=0`、`gen_table_column=0`，但按 `GenTableMapper.selectDbTableList` 同口径查询可导入表仍有 147 张，其中包括 39 张 `ACT_%`、2 张 `FLW_%` 和 38 张 `*_backup_*` / `*_clear_backup_*` / `*_cleanup_backup_*` 表。SQL 在 `erp-modules/erp-gen/src/main/resources/mapper/generator/GenTableMapper.xml:80-98` 只过滤 `table_name NOT LIKE 'qrtz\_%'` 和 `NOT LIKE 'gen\_%'`；导入保存 `:100-107` 同样只按这两个前缀过滤。前端导入弹窗 `erp-ui/src/views/tool/gen/importTable.vue:27-44` 只展示表名、表描述、创建/更新时间，没有标识系统表、流程表、备份表或正式业务表；后端导入接口 `GenController.importTableSave` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:106-115` 直接按所选表导入。
- 影响：一旦代码生成入口可达或接口被有权限账号直接调用，业务角色可能把流程引擎内部表、清理备份表或历史归档表导入生成器，生成无意义甚至危险的管理页面/API；也会让“当前正式业务库到底哪些表属于交付功能”更加混乱。
- 建议：代码生成只允许导入经过白名单或模块前缀确认的正式业务表，例如 `sys_*`、`inv_*`、`oa_*` 中明确可生成的表；默认排除 `ACT_%`、`FLW_%`、`QRTZ_%`、`gen_%`、`*_backup_*`、`*_log` 等内部/归档/流水表。导入弹窗应显示表类型标签和风险提示，恢复系统工具前先清理或归档当前备份表。

### P3-110 代码生成自定义路径可保存任意服务器路径，运行开关与页面能力不一致

- 现象：代码生成编辑页允许把“生成代码方式”切到“自定义路径”，并填写磁盘绝对路径或选择 `/` 恢复默认基础路径；当前配置 `gen.allowOverwrite=false` 时后端会拒绝真正写入本地文件，但页面仍允许保存 `gen_type/gen_path`，列表页点击“生成代码”后才会触发后端错误。若后续把覆盖开关打开，后端会按数据库中的 `gen_path` 拼接模板文件名写服务器本地文件，没有项目根目录白名单、路径归一化或危险路径提示。
- 证据：前端 `erp-ui/src/views/tool/gen/genInfoForm.vue:96-143` 提供 `genType=0/1` 和 `genPath` 输入，提示“填写磁盘绝对路径，若不填写，则生成到当前Web项目下”，并可把路径设为 `/`；`erp-ui/src/views/tool/gen/index.vue:252-265` 在 `row.genType === "1"` 时调用 `/code/gen/genCode/{tableName}` 并提示“成功生成到自定义路径”。后端 `GenController.genCode` 在 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:169-180` 只检查 `GenConfig.isAllowOverwrite()`，当前 `erp-modules/erp-gen/src/main/resources/application-dev.yml:29-33` 为 `allowOverwrite: false`；`GenTableServiceImpl.validateEdit` 在 `:430-455` 只校验树表/主子表必填项，不校验 `genType/genPath`；`GenTableMapper.xml:187-188` 会保存 `gen_type/gen_path`；真正写入时 `GenTableServiceImpl.generatorCode` 在 `:242-270` 调用 `FileUtils.writeStringToFile(new File(path), ...)`，`getGenPath` 在 `:545-552` 对 `/` 使用 `System.getProperty("user.dir") + /src/...`，否则直接 `genPath + File.separator + VelocityUtils.getFileName(...)`。当前运行态 `gen_table=0/gen_table_column=0`、`sys_oper_log` 中 `title='代码生成'` 为 0 条，但 `common / 普通角色` 和 `zjl / 总经理` 均持有完整 `tool:gen:code/edit/import/list/preview/query/remove`。
- 影响：当前表现是页面能力和运行配置不一致，管理员可保存一个无法执行的“自定义路径”配置，点击生成后才失败；如果后续为了让页面功能可用而开启 `allowOverwrite`，拥有代码生成权限的业务角色就可能把代码生成到服务端工程目录或其他可写目录，带来部署文件污染、误覆盖和审计解释困难。多账号环境里，代码生成这种开发工具不应和普通业务角色共用权限模板。
- 建议：若当前环境不交付代码生成本地写入，应在前端隐藏“自定义路径”选项，并在编辑保存时拒绝 `genType=1`；若确需支持，应只允许写入受控工作目录，后端对 `genPath` 做绝对路径归一化、目录白名单、父目录逃逸检查、覆盖预览、二次确认和独立审计，同时把 `tool:gen:code` 从普通角色、总经理等业务角色中移除。

### P3-142 代码生成同步表结构会直接改字段生成配置，但没有差异预览和独立高危权限

- 现象：代码生成列表行操作里的“同步”会按真实数据库表结构更新 `gen_table_column`，新增缺失字段、更新已有字段，并删除数据库中已不存在的字段配置；但前端确认框只写“确认要强制同步某表结构吗？”，没有展示将新增、删除或覆盖哪些字段，也没有把同步动作从普通“生成修改”权限里拆出来。
- 证据：前端行按钮 `同步` 使用 `v-hasPermi="['tool:gen:edit']"`，见 `erp-ui/src/views/tool/gen/index.vue:123-129`；确认文案和 API 调用在 `:267-274`，只传 `tableName` 给 `/code/gen/synchDb/{tableName}`。后端 `GenController.synchDb()` 同样要求 `@RequiresPermissions("tool:gen:edit")`，见 `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java:183-191`。服务层 `GenTableServiceImpl.synchDb()` 会读取当前 `gen_table` 配置和 `information_schema.columns`，对已有列调用 `updateGenTableColumn`，对新列 `insertGenTableColumn`，对数据库已删除的列调用 `deleteGenTableColumns`，见 `erp-modules/erp-gen/src/main/java/com/erp/gen/service/GenTableServiceImpl.java:283-330`。`GenTableColumnMapper.xml` 的更新语句会覆盖 `column_comment/java_type/column_type/java_field/is_insert/is_edit/is_list/is_query/is_required/query_type/html_type/dict_type/sort` 等字段，见 `:92-111`。当前运行态 `gen_table=0/gen_table_column=0`，所以暂时没有存量配置被同步覆盖；但 `common / 普通角色` 和 `zjl / 总经理` 均持有 `tool:gen:edit`。
- 影响：如果后续导入业务表并手工调整字段显示、查询方式、字典、必填、排序等生成配置，点击同步会直接改变这些配置，甚至删除已经从数据库移除的字段配置；用户无法在最后一步看到差异和影响范围，也无法只授予“编辑生成配置”而禁止“同步数据库结构”。多账号协作时，另一个账号同步一次表结构，可能让已经调好的生成页面配置被覆盖，问题只能从操作日志和字段表变化中倒查。
- 建议：同步表结构应拆出独立 `tool:gen:sync` 权限，并在执行前展示差异预览：新增字段、删除字段、类型变化、注释变化、将保留或覆盖的查询/字典/必填/显示类型。确认后再写 `gen_table_column`，同时记录字段级同步审计；没有差异时应提示“无需同步”。如果代码生成不属于当前交付范围，应禁用同步按钮和接口，不要只依赖父菜单隐藏。

### P3-54 表单构建只有本地复制/下载能力，菜单状态和交付闭环不清

- 现象：系统工具父菜单 `tool` 当前隐藏停用，但子菜单“表单构建”在数据库中仍是启用可见状态；页面组件源码存在，功能却只在浏览器内拖拽组件并复制/下载 Vue 代码，没有服务端保存、共享、回溯或按钮级权限控制。
- 证据：`sys_menu` 中 `3 / 系统工具` 为 `status=1`、`visible=1`，子菜单 `114 / 表单构建` 为 `status=0`、`visible=0`、`component=tool/build/index`、`perms=tool:build:list`，且当前 `common / 普通角色`、`zjl / 总经理` 均持有 `tool:build:list`；浏览器直连 `/tool/build` 实测为 404。前端 `erp-ui/src/views/tool/build/index.vue:81-88` 只提供“导出vue文件 / 复制代码 / 清空”三个动作，没有 `v-hasPermi`；`mounted()` 使用 `ClipboardJS` 复制生成代码，`execDownload()` 通过 `Blob` 和 `saveAs` 下载本地文件，`generateCode()` 只按当前画布生成 Vue 代码。生成类型弹窗 `erp-ui/src/views/tool/build/CodeTypeDialog.vue:17-29` 仅让用户选择“页面/弹窗”和文件名；`rg "tool:build"` 未发现后端 Controller、接口或按钮权限实现。
- 影响：如果系统工具不属于当前交付范围，启用的子菜单和存在的页面组件会让交付边界变模糊；如果要交付表单构建，当前能力又不能保存表单模板、按账号/角色/组织共享、重新打开历史设计、记录导出审计或把生成结果接入菜单和权限。多账号协作场景下，用户只能靠本机复制/下载文件流转，无法形成可管理流程。
- 建议：先明确表单构建是否交付。若不交付，应与父菜单一起停用子菜单、路由和权限，并在交付说明中标记为开发工具；若交付，应补齐表单模板保存、归属范围、共享/复制、预览、历史版本、导出审计和按钮权限，并把生成页面接入菜单、组件路径和权限校验闭环。

### P3-233 表单构建生成校验规则时会执行用户填写的正则表达式

- 现象：表单构建右侧属性面板允许给字段添加“正则校验”，表达式是普通输入框；用户点击“复制代码”或“导出vue文件”时，生成器会在当前后台页面内执行这段表达式，而不是把它安全地当作正则字符串或正则字面量处理。
- 证据：`erp-ui/src/views/tool/build/RightPanel.vue:489-508` 对 `activeData.regList` 渲染“表达式/错误提示”输入框，并通过 `addReg()` `:731-735` 追加任意 `pattern/message`；默认手机号模板在 `erp-ui/src/utils/generator/drawingDefault.js:27-30` 使用字符串形式的正则。代码生成器 `erp-ui/src/utils/generator/js.js:140-144` 遍历 `conf.regList` 时调用 `eval(item.pattern)`，随后拼接到生成代码；`erp-ui/src/views/tool/build/index.vue:214-223` 的复制和 `:282-285/:326-332` 的导出都会触发 `generateCode()`。结合 P3-54，表单构建没有服务端模板保存、审核或导出审计，当前风险完全发生在浏览器端生成流程。
- 影响：如果后续恢复表单构建入口，或测试人员通过路由/组件方式进入页面，输入一个非预期表达式就可能在已登录 ERP 前端上下文中执行 JavaScript；即使不是恶意输入，错误正则也会让复制/导出流程失败。多账号多店铺验收时，这类开发工具如果继续保留给普通角色或总经理角色，会让“生成代码”动作变成不可控的前端脚本执行点，问题也不会进入后端操作日志。
- 建议：移除 `eval`，把正则表达式作为受控数据处理：前端先用白名单格式校验 `/pattern/flags` 或分离 `pattern/flags` 字段，再用安全解析和异常提示生成代码；错误提示文案也要转义后拼接，避免破坏生成代码。若表单构建不交付，应和父菜单一起停用路由、权限和页面组件；若交付，应增加正则校验预览、生成失败提示、导出审计和角色授权边界。

### P3-55 角色仓库授权表有数据但前后端没有运行闭环

- 现象：当前数据库保留 `sys_role_warehouse` 角色仓库授权记录，但角色管理页没有仓库授权入口，后端仓库选择和店铺/仓库权限校验也不读取这张表；仓库范围仍只按用户级 `sys_user_shop` 判断。
- 证据：MySQL 复核 `sys_role_warehouse` 当前 3 条记录：`9005 / 驻店经理` 授权仓库 `200 / 123` 和 `201 / 仓库`，`9010 / 店助` 授权 `201 / 仓库`，对应 `sys_dept` 均为正常 `WAREHOUSE`。全仓 `rg "sys_role_warehouse|role_warehouse|RoleWarehouse"` 只命中 SQL 和本报告，没有业务源码引用。角色管理页 `erp-ui/src/views/system/role/index.vue:113-137` 的“更多”只提供“数据权限 / 分配用户 / 薪资配置”，新增/修改角色表单也只有菜单权限树；后端 `SysRoleController` 只处理角色、菜单、数据权限和分配用户。仓库选择服务 `SysDeptServiceImpl.canSelectWarehouse` 在 `:161-177` 只调用 `userShopService.checkUserShopScope(userId, warehouseDeptId, false)`，而 `SysUserShopServiceImpl.checkUserShopScope` 只查 `sys_user_shop`。
- 影响：管理员或初始化 SQL 会以为“驻店经理/店助”这类角色已经绑定了可操作仓库，但实际运行时不生效；在当前 `sys_user_shop=0` 的库里，这张表也不能补上用户仓库授权。多账号多店铺/多仓库上线时，角色、用户授权和仓库选择会出现三套口径，排查“为什么这个角色看不到仓库”只能靠查源码。
- 建议：明确仓库授权的唯一模型。若以后只采用用户级 `sys_user_shop`，应删除或归档 `sys_role_warehouse`，并把现有 3 条角色仓库关系迁移为用户级授权或产品说明；若角色仓库授权仍要保留，应补齐角色管理页仓库授权、后端保存接口、角色与用户授权合并规则，并让仓库选择、库存、发货、调拨和报表范围统一读取同一套授权。

### P3-56 文件元数据、版本和下载日志能力没有进入当前运行库和上传/删除链路

- 现象：当前运行库没有 `sys_file_meta`、`sys_file_version`、`sys_file_download_log` 这类文件生命周期表；电脑端上传、删除和富文本图片上传都只围绕 URL 字符串工作，文件服务不会落元数据、版本、业务归属、下载日志或引用关系。
- 证据：2026-06-25 复核当前运行库 `BossERP_stock_state_75c59ee` 共 160 张表，`information_schema.tables` 中 `sys_file_meta/sys_file_version/sys_file_download_log` 命中 0 张；`sys_menu` 中 `file:%` 权限节点为 0。全仓 `rg "sys_file_meta|sys_file_version|sys_file_download_log|FileVersion|DownloadLog"` 仅命中本报告，没有运行源码接入。`SysFileController.upload` 在 `erp-modules/erp-file/src/main/java/com/erp/file/controller/SysFileController.java:33-47` 只调用 `sysFileService.uploadFile(file)` 并返回 `SysFile.name/url`；`SysFile` API 对象只有 `name` 和 `url` 两个字段。`LocalSysFileServiceImpl.uploadFile` 和 `MinioSysFileServiceImpl.uploadFile` 只写本地/Minio 对象并拼 URL，删除接口也只按 URL 删除对象。前端 `FileUpload`、`ImageUpload`、`Editor` 和劳动合同模板/企业章页面都保存 URL 字符串；`FileUpload` 与 `ImageUpload` 的 `deleteRemoteFile` 还会 `catch(() => {})` 吞掉删除失败。
- 影响：劳动合同模板、企业章、头像、富文本图片和后续生成页面的附件都无法统一追踪“谁上传、属于哪个业务单、当前版本、是否被引用、是否已下载、是否可删除”。删除失败时前端仍可能移除表单值，数据库业务记录和真实对象存储会分叉；审计、清理孤儿文件、合同证据链和下载留痕都缺少可用底座。
- 建议：要么明确这些文件生命周期表不交付并从运行库/参数说明中下线，要么把 `/file/upload`、`/file/delete`、富文本图片上传和业务附件保存全部接入文件元数据模型。上传应返回稳定 `file_id` 和 URL，业务表保存 `file_id` 或建立关联表；删除前检查引用，删除失败不能静默移除表单值；下载/预览应写 `sys_file_download_log`，模板替换应写版本记录。

### P3-150 文件删除接口未检查真实删除结果，不存在或未命中的文件也返回成功

- 现象：通用文件删除接口只校验 URL 扩展名是否合法，随后调用存储服务删除文件，但不检查文件是否真实存在、是否命中当前存储前缀、是否删除成功。前端组件又会在删除请求结束后直接从表单值移除文件，因此页面、业务表和真实文件存储容易出现“页面已删、文件仍在”的错位。
- 证据：`SysFileController.delete` 在 `erp-modules/erp-file/src/main/java/com/erp/file/controller/SysFileController.java:56-68` 调用 `sysFileService.deleteFile(fileUrl)` 后直接 `return R.ok()`；`LocalSysFileServiceImpl.deleteFile` 在 `erp-modules/erp-file/src/main/java/com/erp/file/service/LocalSysFileServiceImpl.java:63-66` 调用 `FileUtils.deleteFile(localFilePath + localFile)`，但没有读取返回值。`FileUtils.deleteFile` 在 `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/file/FileUtils.java:97-106` 对不存在文件返回 `false`。前端 `FileUpload` 和 `ImageUpload` 分别在 `erp-ui/src/components/FileUpload/index.vue:203-216`、`erp-ui/src/components/ImageUpload/index.vue:206-220` 中无论删除是否真实成功，都会在 `finally` 里移除本地列表，且 `catch(() => {})` 吞掉异常。本轮使用 admin token 非破坏性请求不存在的 `http://localhost:8080/file/public/qa-delete-probe-not-exist-20260624.png`，`DELETE /file/delete` 返回 `HTTP 200` 和 `{"code":200,"data":null,"msg":null}`。
- 影响：劳动合同模板、企业章、头像或公告图片被用户从页面删除时，用户会收到成功状态或至少看到表单值消失，但对象可能仍留在公开 `/file/public/**` 下；如果该 URL 已被外部记录，仍可继续访问。反过来，如果编辑弹窗里移除已有附件后又点击取消，组件已经尝试远端删除，但业务记录不会同步保存，可能继续引用已删除或待删除文件。多账号审计时也无法判断“谁删除了哪个文件、是否真实删除、是否仍被引用”，与 P2-126 的公开文件问题叠加后会增加残留敏感附件风险。
- 建议：文件删除服务应返回明确结果并在 Controller 中处理：文件不存在、URL 不属于当前存储域、存储删除失败都应返回失败或幂等但可区分的状态；前端只有在后端确认删除或明确选择“仅从当前表单解绑”时才移除值。删除前应检查业务引用，删除后写文件审计日志；对公开业务文件还要补孤儿文件巡检和清理工具。

### P3-57 API 应用和 Webhook 表存在，但没有电脑端管理和投递实现

- 现象：数据库已有 `sys_api_app`、`sys_webhook`、`sys_webhook_delivery` 三张系统集成表，字段覆盖应用密钥、限流、IP 白名单、Webhook 回调地址、重试次数和投递响应；但当前电脑端没有菜单或页面维护这些配置，后端也没有开放 API 应用鉴权、Webhook 事件注册或投递重试实现。
- 证据：MySQL 复核三张表当前均为 0 行；表结构中 `sys_api_app` 包含 `app_key/app_secret/rate_limit/ip_whitelist`，`sys_webhook` 包含 `event_type/callback_url/secret/retry_times/status`，`sys_webhook_delivery` 包含 `payload/status/response_code/response_body/attempt`。`sys_menu` 中按 API、应用、Webhook、回调、开放、集成等关键词只命中“系统接口”和已有工作流回调补偿菜单，没有 API 应用或 Webhook 管理入口。全仓 `rg "sys_api_app|sys_webhook|sys_webhook_delivery|ApiApp|Webhook|app_secret|callback_url|webhook_delivery"` 除本报告和两份劳动合同 SQL 的第三方回调原文字段外，没有运行源码命中；源码中也未发现按 `callback_url/retry_times` 做投递的服务或任务。
- 影响：数据库会给运维和产品一个“已经有开放 API / Webhook 集成底座”的信号，但页面、接口、签名、限流、白名单、投递日志和重试都没有交付。后续如果对接第三方系统、合同签署回调、库存/销售事件推送或移动端外部应用，容易误以为只要插入配置表即可使用，实际没有任何运行链路。
- 建议：明确这些集成表是否属于交付范围。不交付时应从运行库和系统参数说明中下线或标记为预留；交付时需要补齐电脑端 API 应用管理、密钥生成/轮换、IP 白名单、限流、Webhook 事件目录、回调签名、投递队列、失败重试、投递日志查询和权限审计，并与 `@InnerAuth`/外部 API 鉴权模型区分清楚。

### P3-58 旧口径 OA 采购申请存在 `received` 状态，恢复入口前需先统一枚举

- 现象：旧/非运行态口径曾出现 3 条状态为 `received` 的 OA 采购申请；当前运行态 `oa_purchase=0`，但源码页面、已办/待办页面和后端导出注解仍只认识 `draft/submitted/approved/rejected`。后续若恢复旧数据或重新启用 OA 采购入口，这些存量状态会在列表和详情里显示英文原值，状态筛选和导出也无法给出一致中文含义。
- 证据：当前运行态精确复核 `oa_purchase=0`、`oa_purchase_comment=0`。旧/非运行态曾复核到 `purchase_id=10/11/12` 状态为 `received`，`purchase_id=19` 状态为 `approved`。`erp-ui/src/views/oa/purchase/index.vue:8-14` 的状态筛选只有草稿、审批中、已通过、已驳回，`:31-38` 对未识别状态直接显示 `scope.row.status`；`erp-ui/src/views/oa/done/index.vue:183-190` 只把 `approved/rejected` 转成中文；`erp-ui/src/views/oa/todo/index.vue:181-185` 只把 `submitted` 转成审批中。后端 `OaPurchaseServiceImpl` 只定义并写入 `draft/submitted/approved/rejected`，`OaPurchase.java` 的 Excel 状态转换也只覆盖这 4 个值。
- 影响：恢复旧数据或重新启用 OA 采购时，管理员会看到“received”这种非业务中文状态，无法判断是已收货、已通过后的历史状态，还是脏数据；导出报表也可能保留英文状态。更重要的是，状态口径已经从“审批”混入“收货”，后续恢复 OA 采购时会继续放大流程状态和库存状态的边界混乱。
- 建议：恢复 OA 采购前先做状态迁移和口径确认：若 `received` 是历史审批完成态，应迁移为 `approved` 或新增明确中文状态并补齐前端/导出/查询选项；若它代表库存收货，应拆到库存单据或明细状态，不应落在 OA 审批申请主状态。建议给 OA 采购建立状态枚举测试，避免新状态只出现在数据库或某一个页面中。

### P3-59 定时任务目标白名单只有代码常量，当前运行态没有可维护表或页面

- 现象：定时任务新增/修改会校验调用目标是否在白名单内，但白名单只写在代码常量里。当前运行态数据库没有 `sys_job_whitelist` 这类可维护表，也没有电脑端白名单管理页；管理员在“定时任务”页面只能手填调用目标字符串，看不到当前允许的包、Bean、方法或参数签名。
- 证据：2026-06-25 重新按当前运行态 `BossERP_stock_state_75c59ee` 复核，`information_schema.tables` 中 `sys_job_whitelist=0`，直接 `SHOW CREATE TABLE sys_job_whitelist` 返回表不存在；全仓 `rg "sys_job_whitelist|JobWhitelist|whitelist"` 除本报告外没有 mapper、controller、前端页面、服务或建表脚本引用。`SysJobController.add/edit` 在 `erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java:82-145` 校验 Cron、RMI/LDAP/HTTP 和 `JOB_ERROR_STR` 后调用 `ScheduleUtils.whiteList(job.getInvokeTarget())`；`ScheduleUtils.whiteList` 在 `erp-modules/erp-job/src/main/java/com/erp/job/util/ScheduleUtils.java:128-140` 只判断 `Constants.JOB_WHITELIST_STR` 和 `Constants.JOB_ERROR_STR`，`Constants.JOB_WHITELIST_STR` 当前写死为 `com.erp.job.task`。当前 `sys_job` 3 条内置 `ryTask.*` 均为停用状态，`sys_job_log=0`。旧/非运行态曾出现启用的 `oaAttendanceSyncTask.syncYesterdayAttendance()` 目标 Bean 不存在，另见 P2-83。
- 影响：调度安全边界不可运营化。新增业务同步任务时，管理员既不能在页面上批准具体 Bean/方法，也无法看到为什么某个目标字符串被拒绝；开发人员只能改代码常量并重新发布。多账号多店铺巡检时也无法通过数据库或页面证明“当前允许执行哪些任务方法”，定时任务页面呈现的是可配置表单，实际关键安全规则却不可见。
- 建议：明确目标白名单交付形态。若要运营可维护，应增加白名单管理页、启用/停用状态、参数签名校验、审计日志和保存前预检，并让 `ScheduleUtils.whiteList` 读取启用白名单，同时保留危险包/危险协议硬拦截；若坚持代码常量，应在定时任务页展示当前代码白名单、目标 Bean/方法预检结果和“需发版变更白名单”的说明，避免管理员误以为只靠页面就能完成任务接入。

### P3-60 权限变更专用审计缺席，角色、菜单、店铺授权只能落到通用操作日志

- 现象：权限变更没有独立审计链路。当前运行库里没有 `sys_permission_change_log` 专用权限变更表，仓库中也没有对应 mapper、service、controller 或前端页面；角色菜单授权、角色分配用户、用户授权角色、店铺授权和菜单修改都只依赖 `sys_oper_log` 通用操作日志。通用日志能看到请求参数，却不能稳定还原“改之前是什么、改之后是什么、影响了哪些账号/角色/门店”。
- 证据：2026-06-24 直连 MySQL 执行 `show tables like '%permission%'`、`show tables like '%change%'` 均无结果，`show tables like '%log%'` 只看到 `sys_oper_log/sys_logininfor/sys_job_log` 及库存、流程日志表；`select count(*) from sys_oper_log` 为 244。全仓 `rg "sys_permission_change_log|permission_change|PermissionChange|before_value|after_value|target_type|change_type"` 除本报告外无引用，`.sql` 文件中也没有建表脚本。高风险权限写入口仍只标注通用 `@Log`：`SysUserController.insertAuthRole` 使用 `BusinessType.GRANT` 后调用 `userService.insertUserAuth` 并先删后插用户角色，`SysRoleController.authUser/*` 使用 `BusinessType.GRANT` 变更角色用户，`SysUserShopController.save` 使用 `BusinessType.GRANT` 保存店铺授权，`SysRoleServiceImpl.updateRole/insertRoleMenu` 会删除并重建角色菜单，`SysMenuController.edit` 修改菜单权限基础数据。`BusinessType.GRANT` 的枚举序号为 4，当前 `sys_oper_log` 样例包括 `oper_id=340/店铺配置/business_type=4/oper_param=122 [103]`，用户新增日志只保存 `roleIds`，角色编辑日志保存当次 `menuIds` 大数组；`LogAspect` 只把请求参数截断写入 `oper_param`、把返回值截断写入 `json_result`，不会读取或保存变更前快照。
- 影响：多账号多店铺最关键的安全变更是“谁给谁加了角色、开放了哪些菜单、授权了哪些门店/仓库、是否把数据范围扩大到全部”。只靠通用操作日志，审计人员需要从截断的 JSON 请求参数和当前库状态倒推历史，遇到日志清空、参数截断、菜单名称变更或角色复用时很难复原责任链。
- 建议：补建并接入权限变更专用审计模型，把角色菜单、角色用户、用户角色、店铺授权、菜单权限标识/状态/组件变更都写入 `sys_permission_change_log` 或等价表，保存标准化 before/after 快照和影响范围摘要；权限变更日志应有独立查询页、导出权限和保留策略，不能被普通操作日志清空一起抹掉。

### P3-61 认证提供方和登录设备表存在，但登录页和认证服务没有接入

- 现象：数据库有 `sys_auth_provider` 和 `sys_login_device` 两张登录安全相关表，字段覆盖第三方认证配置、登录设备名称、设备类型、IP、最近登录时间、token 和状态；但电脑端登录页只支持账号、密码、验证码，认证服务也只走本地用户名密码。当前没有第三方认证配置页、登录设备列表、设备撤销或设备信任流程。
- 证据：MySQL 当前 `sys_auth_provider=0`、`sys_login_device=0`；`sys_auth_provider` 字段包括 `provider_type/provider_name/enabled/config_json/sort_order`，`sys_login_device` 字段包括 `user_id/device_name/device_type/login_ip/last_login_time/token_id/status`。`erp-ui/src/views/login.vue` 的 `loginForm` 只有 `username/password/rememberMe/code/uuid`，`erp-ui/src/api/login.js` 只向 `/auth/login` 发送 `username/password/code/uuid`。后端 `TokenController.login` 直接调用 `sysLoginService.login(form.getUsername(), form.getPassword())`，`SysLoginService.login` 完成用户名密码、IP 黑名单、用户状态和密码校验后只写 `sys_logininfor` 并返回 token。全仓 `rg "sys_auth_provider|sys_login_device|AuthProvider|LoginDevice|provider_type|device_name"` 未发现运行 mapper、service、controller 或前端管理页。
- 影响：表结构给出“可配置第三方登录/设备管理”的信号，但运行链路没有这些能力。多账号多店铺、门店共用电脑和多设备登录场景下，管理员无法查看某账号在哪些设备登录、撤销某台设备、设置设备信任，也无法接入企业微信/钉钉/OAuth/SSO 这类统一身份入口。已有在线用户页只能按 token/IP 强退，不能形成设备维度的账号安全闭环。
- 建议：确认这两张表是否属于交付范围。若要交付，应补齐认证提供方管理、登录页第三方入口、回调校验、绑定本地账号、设备指纹/设备名称记录、登录设备列表和设备撤销；若不交付，应从运行库下线或标注为预留，避免管理员以为系统已经支持 SSO 或设备管理。

### P3-62 当前运行态没有个人消息模型，顶部消息和移动端只能读取全局公告

- 现象：电脑端顶部铃铛、移动端消息动作和通知公告管理都只围绕 `sys_notice/sys_notice_read` 工作；当前运行态库没有 `sys_notification` 个人消息表，也没有消息中心、个人收件箱、业务事件消息生产者或个人消息已读接口。
- 证据：2026-06-24 MySQL 复核当前运行态 `BossERP_stock_state_75c59ee`，`information_schema.tables` 只返回 `sys_notice`、`sys_notice_read`，没有 `sys_notification`；直接 `show columns from sys_notification` 返回表不存在。全仓 `rg "sys_notification|SysNotification|source_type|read_status"` 除本报告外没有运行 mapper、service、controller 或前端页面命中。电脑端 `HeaderNotice` 只调用 `listNoticeTop/markNoticeRead/markNoticeReadAll`，对应 API 走 `/system/notice/listTop`、`/system/notice/markRead`、`/system/notice/markReadAll`；后端 `SysNoticeController` 和 `SysNoticeReadMapper` 只读写 `sys_notice`、`sys_notice_read`。移动端消息动作同样复用这套全局公告接口。
- 影响：当前交付模型只有全局公告，没有按个人收件的系统消息。审批待办、调拨异常、工资计算失败、合同签署提醒、库存预警这类应按用户发送的消息无法落到个人收件箱；多账号多店铺场景下，个人待办和门店事件提醒会被迫混入全员公告或完全不可见。
- 建议：明确消息模型边界。若要交付个人消息，应补齐 `sys_notification` 或等价消息表、消息生产服务、个人消息列表、未读数、已读/全部已读、按业务来源跳转详情、清理策略和权限；顶部铃铛应能同时或分 tab 展示个人消息与全局公告。若不交付个人消息，应在产品说明和数据库文档中明确当前只有全局公告，避免把个人提醒能力误认为已实现。

### P3-63 临时授权表存在，但没有权限委托、到期回收或运行时合并逻辑

- 现象：数据库有 `sys_temp_authorization` 表，字段覆盖授权人、被授权人、角色、仓库、部门、生效时间、过期时间、原因和状态；但电脑端没有临时授权管理页，后端权限、菜单、数据范围和店铺/仓库选择也没有读取这张表。即使运维手工插入记录，运行时权限也不会临时生效或到期回收。
- 证据：MySQL 当前 `sys_temp_authorization=0`，字段包括 `grantor_id/grantee_id/role_id/warehouse_id/dept_id/effective_time/expire_time/reason/status`。全仓 `rg "sys_temp_authorization|TempAuthorization|grantor_id|grantee_id|effective_time|expire_time|临时授权|授权委托"` 除本报告和无关文本外没有 mapper、service、controller、前端页面或权限合并逻辑命中。当前用户权限仍来自 `sys_user_role/sys_role_menu/sys_user_shop` 等固定关系，工作流委托权限也只以菜单权限字符串形式出现在历史工作流菜单里，没有运行模块承接。
- 影响：多账号多店铺里常见的“店长请假临时授权给店助”“仓库负责人临时代办调拨/盘点”“某门店临时支援其他门店”都无法通过系统闭环表达。管理员只能直接改角色或店铺授权，事后再手工改回，既没有授权原因、有效期、审批记录，也没有自动回收；权限事故复盘时也无法区分长期授权和临时授权。
- 建议：确认临时授权是否交付。若交付，应补齐申请/审批/撤销页面，运行时把有效期内的 `role_id/dept_id/warehouse_id` 合并到权限、菜单、数据范围和组织选择，并用定时任务或登录时校验处理过期回收；若不交付，应下线或标注该表为预留，避免管理员以为已有权限委托能力。

### P3-64 自定义报表定义表存在，但没有报表管理、执行和权限闭环

- 现象：数据库有 `sys_report` 表，字段覆盖报表名称、编码、分类、SQL、参数结构和状态，像是用于自定义报表或可配置查询；但电脑端没有报表定义管理页，也没有读取 `sys_report` 的后端执行服务。当前“报表中心”只交付库存模块的固定汇总和低库存预警，不支持管理员配置、发布、禁用或授权自定义报表。
- 证据：MySQL 当前 `sys_report=0`，字段包括 `report_name/report_code/category/query_sql/params_schema/status`。全仓 `rg "sys_report|SysReport|report_code|params_schema|query_sql|自定义报表|报表定义"` 除本报告外没有 mapper、service、controller 或前端页面命中。现有报表路由 `erp-ui/src/router/index.js:180-189` 固定到 `/inventory/report`，前端 API `erp-ui/src/api/inventory/report.js:3-10` 只调用 `/inventory/report/summary` 和 `/inventory/report/stock-warning`；后端 `InvReportController` 只暴露 `summary/stock-warning`，`InvReportMapper.xml` 中 SQL 也是固定库存、采购、销售汇总和库存预警查询，没有接入 `sys_report`。
- 影响：数据库给管理员和运维传递了“系统支持配置化报表”的信号，但实际无法通过页面维护或执行。若后续有人直接向 `sys_report.query_sql` 写入 SQL，也没有参数白名单、租户/店铺范围拼接、字段脱敏、执行超时、导出权限、审计日志和禁用状态校验；多账号多店铺场景下，报表最容易越过门店和成本权限边界。
- 建议：明确 `sys_report` 是否属于交付功能。若交付，应补齐报表定义管理、参数 schema 校验、SQL 安全策略、数据范围注入、成本/敏感字段权限、执行审计、导出控制和禁用状态；若当前只交付库存固定报表，应下线或标注该表为预留，避免让用户误以为已有自定义报表平台。

### P3-65 告警规则表存在，但没有指标采集、规则评估和通知闭环

- 现象：数据库有 `sys_alert_rule` 表，字段覆盖规则名称、指标名、阈值、比较符、持续分钟数、接收人和启用状态，像是系统级监控告警配置；但电脑端没有告警规则管理页，后端没有读取该表的规则评估任务，也没有把触发结果发送到公告、个人消息、短信或外部 Webhook。当前库存低库存预警只是业务查询，不是这张系统告警规则表的执行结果。
- 证据：MySQL 当前 `sys_alert_rule=0`，字段包括 `rule_name/metric_name/threshold/comparison/duration_minutes/alert_receiver/enabled`。精确检索 `rg "sys_alert_rule|AlertRule|alert_receiver|metric_name|duration_minutes"` 在前端、后端、API 和 SQL 中没有运行代码命中。`sys_menu` 的“系统监控”下只有在线用户、定时任务、Sentinel、Nacos、Admin 控制台，没有告警规则菜单；`erp-ui/src/views/monitor` 目录也只有 `online` 和 `job` 相关页面。库存报表和库存列表的“预警”基于 `inv_product.safety_stock_min` 与库存数量计算，未读取 `sys_alert_rule`。
- 影响：运维或管理员会以为系统已经有可配置告警平台，但实际没有采集指标、判断持续时间、触发去重、通知接收人、告警恢复和历史查询。多店铺场景下，库存异常、定时任务失败、登录爆破、接口错误率、库存单据失败等事件无法进入统一告警，问题只能靠人工查看各个列表或日志。
- 建议：明确告警规则是否交付。若交付，应增加规则管理页、指标目录、采集来源、周期评估任务、触发/恢复状态、接收人解析、通知渠道、告警历史和权限控制；若不交付，应下线或标注 `sys_alert_rule` 为预留，避免把普通库存预警误解为系统告警能力。

### P3-66 链路追踪日志表存在，但没有请求 Trace 写入和查询页面

- 现象：数据库有 `sys_trace_log` 表，字段覆盖 trace、span、服务名、请求地址、方法、状态码、耗时、用户和请求时间；但网关、认证服务和各业务模块没有向该表写入调用链，也没有电脑端链路追踪查询页。当前日志管理只提供操作日志和登录日志，不能按一次请求串起网关、认证、系统、库存、OA 等服务的调用过程。
- 证据：MySQL 当前 `sys_trace_log=0`，字段包括 `trace_id/span_id/service_name/request_uri/method/status_code/duration_ms/user_id/request_time`。全仓 `rg "sys_trace_log|TraceLog|trace_id|span_id|duration_ms|链路追踪|调用链"` 没有运行代码命中。进一步检索 `trace/tracing/sleuth/zipkin/skywalking/opentelemetry/MDC/requestId` 只命中幂等请求身份、劳动合同事件 `requestId` 和普通请求头判断，没有通用 trace 过滤器、MDC 注入、OpenTelemetry/SkyWalking/Sleuth 配置或 trace 查询接口。`sys_menu` 日志相关菜单只有操作日志、登录日志和库存日志，没有链路追踪页面。
- 影响：多服务架构下，库存单据、OA 审批、登录、店铺切换、定时任务等问题跨服务失败时，只靠操作日志/登录日志无法还原完整请求路径、耗时瓶颈和失败位置。`sys_trace_log` 暗示已有 trace 能力，但实际排障仍要分散查各服务日志，管理员也无法在电脑端按 traceId、用户、接口或耗时筛查异常。
- 建议：若交付链路追踪，应在网关和服务入口生成/透传 traceId，写入 MDC 和统一拦截器，按服务、接口、状态、耗时落库或对接专业 tracing 系统，并提供查询页面、保留策略和脱敏规则；若不交付，应下线或标注 `sys_trace_log` 为预留，避免把普通日志管理误认为链路追踪能力。

### P3-67 调度锁表存在，但定时任务没有使用分布式锁或集群 JobStore

- 现象：数据库有 `sys_scheduler_lock` 表，字段覆盖锁名、持有人、锁到期时间和创建时间；但定时任务模块没有读取或更新这张表。当前调度服务启动时会从 `sys_job` 加载任务到 Quartz Scheduler，单任务的“不允许并发”只依赖 Quartz 的 `@DisallowConcurrentExecution`，没有跨服务实例的数据库锁或 Redis 锁闭环。
- 证据：MySQL 当前 `sys_scheduler_lock=0`，字段包括 `lock_name/lock_holder/lock_until/create_time`。全仓 `rg "sys_scheduler_lock|SchedulerLock|scheduler_lock|lock_until|locked_by|locked_at|调度锁|分布式锁"` 没有运行代码命中。`SysJobServiceImpl.init` 在 `erp-modules/erp-job/src/main/java/com/erp/job/service/SysJobServiceImpl.java:36-44` 启动时 `scheduler.clear()` 后遍历 `jobMapper.selectJobAll()` 并调用 `ScheduleUtils.createScheduleJob`；`ScheduleUtils` 在 `erp-modules/erp-job/src/main/java/com/erp/job/util/ScheduleUtils.java:35-90` 根据 `concurrent` 选择普通 Job 或 `QuartzDisallowConcurrentExecution`。`erp-modules/erp-job/src/main/java/com/erp/job/config/ScheduleConfig.java:1-57` 整个 Quartz JDBC JobStore/集群配置都处于注释状态，其中 `isClustered=true` 和 `QRTZ_` 表前缀并未生效。
- 影响：如果 `erp-job` 按高可用部署多个实例，每个实例都可能加载同一批 `sys_job` 并独立触发任务；`@DisallowConcurrentExecution` 只能约束同一 Scheduler 内部的同一 Job，不等于跨实例分布式锁。考勤同步、日志清理、库存定时检查、告警评估这类任务重复跑会带来重复写入、重复通知、清理竞争或状态覆盖。
- 建议：明确调度部署模型。若只支持单实例，应在部署文档和监控页明确限制，并下线或标注 `sys_scheduler_lock` 为预留；若支持多实例，应启用 Quartz JDBC 集群 JobStore 或接入 `sys_scheduler_lock`/Redis 分布式锁，所有任务执行前统一抢锁、续期、释放和记录失败原因，并在调度日志中展示锁命中情况。

### P3-68 差旅补贴表存在，但没有申请、审批或薪资结算入口

- 现象：数据库有 `fin_travel_subsidy` 表，字段覆盖员工、店铺、流程实例、目的地、起止日期、天数、交通方式、日补贴、补贴金额和状态；但电脑端没有差旅补贴、报销或财务补贴页面，后端也没有围绕该表的申请、审批、计算或结算服务。现有薪资和劳动合同页面里的“社保补贴/通勤补贴”与这张差旅补贴表无关。
- 证据：MySQL 当前 `fin_travel_subsidy=0`，字段包括 `user_id/shop_dept_id/instance_id/destination/start_date/end_date/days/transport/daily_subsidy/subsidy_amount/status`。全仓 `rg "fin_travel_subsidy|TravelSubsidy|travel_subsidy|差旅补贴|差旅"` 没有运行代码命中；`sys_menu` 中按“差旅/补贴/报销/travel”检索也没有菜单。宽泛检索“补贴”只命中薪资方案和劳动合同的 `socialSubsidy/commuteSubsidy`，没有财务差旅补贴流程。
- 影响：门店员工外出支援、采购出差、仓库调拨协同等场景下，差旅补贴无法通过系统提交、审批、结算到工资或导出给财务；管理员看到表结构会误以为已有差旅补贴能力。若后续手工写入数据，也没有多店铺权限范围、流程实例校验、金额规则、重复申请校验和工资结算引用。
- 建议：确认差旅补贴是否交付。若交付，应补齐申请页、审批流、目的地/日期/天数校验、补贴标准配置、门店/员工权限、工资结算或财务导出、状态流转和审计；若不交付，应下线或标注 `fin_travel_subsidy` 为预留，避免与现有社保/通勤补贴混淆。

### P3-69 假期、补卡和加班表存在，但考勤工资只按打卡记录推导

- 现象：数据库有 `oa_holiday`、`oa_leave_correction`、`oa_overtime`、`oa_overtime_record` 四张考勤扩展表，字段覆盖节假日、补卡/考勤更正、加班申请、工作流实例和加班记录；但电脑端没有假期维护、补卡申请、加班申请或加班审批页面，后端也没有这些表的实体、Mapper、Controller 或服务。当前工资计算仍只按 `oa_attendance_record` 的状态和工时推导请假、缺勤和加班。
- 证据：MySQL 当前 `oa_holiday=0`、`oa_leave_correction=0`、`oa_overtime=0`、`oa_overtime_record=0`；字段包括 `holiday_date/holiday_type/status`、`attendance_date/original_checkin/corrected_checkin/reason/status/workflow_instance_id`、`ot_date/start_time/end_time/ot_hours/status/workflow_instance_id`、`overtime_date/hours/status/instance_id`。精确检索 `rg "oa_holiday|oa_leave_correction|oa_overtime|oa_overtime_record|OaHoliday|LeaveCorrection|OaOvertime"` 没有运行代码命中。`sys_menu` 中只有历史工作流菜单“休假管理”指向 `workflow/leaveQuota/index`，当前工程没有该页面；OA 考勤 API 只有打卡、列表和导出，OA 工资 API 只有配置、列表、计算和导出。`OaSalaryServiceImpl.calculateSalary` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSalaryServiceImpl.java:148-204` 只读取 `OaAttendanceRecord`：`status=leave` 计请假，其余计工作日，超出标准工时才算加班。
- 影响：节假日不参与应出勤天数，员工补卡/更正无法走审批，加班也不能由申请单确认后进入工资。多店铺考勤里，店长常见的补卡审批、节假日排班和加班确认都落不到系统流程，工资计算会把缺失打卡推成缺勤，或把超时打卡直接推成加班，缺少人工确认和审批依据。
- 建议：考勤工资流程需要明确数据来源。若这些表属于交付范围，应补齐假期日历、补卡/更正申请、加班申请/审批、工作流实例绑定、工资计算引用和导出审计；若当前只交付简单打卡工资，应下线或标注这些表为预留，并在工资计算页面提示“请假/加班来自打卡记录推导，不来自审批单”。

### P3-70 采购合同和采购订单版本表存在，但采购页面没有合同台账或版本历史

- 现象：数据库有 `inv_purchase_contract` 和 `inv_purchase_order_version` 表，字段覆盖采购合同编号、供应商、金额、有效期、付款条款、附件、审批流程实例，以及采购订单版本号、金额快照、变更摘要；但电脑端采购页面只支持采购单列表、新建、提交、收货、质检、取消和导出，没有合同台账、合同附件、合同审批、采购单版本历史或变更对比入口。
- 证据：MySQL 当前 `inv_purchase_contract=0`、`inv_purchase_order_version=0`；合同字段包括 `contract_no/contract_name/supplier_id/total_amount/signed_date/effective_date/expiry_date/status/workflow_instance_id/settlement_method/payment_terms/attachment_urls`，版本字段包括 `order_id/version_no/total_amount/snapshot_json/change_summary`。精确检索 `rg "inv_purchase_contract|inv_purchase_order_version|PurchaseContract|PurchaseOrderVersion|snapshot_json|change_summary"` 在前端、后端、API 和 SQL 中没有运行代码命中。现有采购页面 `erp-ui/src/views/inventory/purchase/index.vue:37-63` 的列表列和操作只围绕采购单号、标题、供应商、金额、状态、质检状态、详情、编辑、提交、收货、质检、删除/取消；采购 API `erp-ui/src/api/inventory/purchase.js:3-45` 也只有保存、提交、列表、详情、收货、质检、取消和删除草稿。
- 影响：采购合同的签订、付款条款、附件、审批和执行状态无法在系统闭环中管理；采购单被修改后也没有版本快照或变更摘要可追溯。多店铺采购里，供应商合同和订单变更往往是财务、仓库和店铺责任划分的依据，当前只能靠外部文件或口头记录，后续审计难以证明“什么时候谁改了采购金额和明细”。
- 建议：若采购合同和订单版本属于交付范围，应补齐合同台账、附件引用、审批状态、合同到采购单关联、采购单保存/提交时版本快照、变更对比、版本查询和权限；若当前只交付轻量采购单，应下线或标注这两张表为预留，并在采购页面避免暗示已有合同/版本追溯能力。

### P3-71 销售报价、合同、发运和应收应付表存在，但没有销售商务闭环

- 现象：数据库有 `inv_sales_quote`、`inv_sales_quote_detail`、`inv_sales_contract`、`inv_shipment`、`inv_receivable`、`inv_payable` 六张销售/结算相关表，字段覆盖报价单、报价明细、销售合同、物流发运、应收和应付；但电脑端没有销售报价、销售合同、发运管理、应收账款或应付账款页面，后端也没有围绕这些表的服务。当前可见销售链路只覆盖客户、销售单、销售退货、发货通知和供应商。
- 证据：MySQL 当前 `inv_sales_quote=0`、`inv_sales_quote_detail=0`、`inv_sales_contract=0`、`inv_shipment=0`、`inv_receivable=0`、`inv_payable=0`；字段包括 `quote_no/customer_id/total_amount/valid_until/status`、`contract_no/quote_id/customer_id/total_amount/effective_date/expiry_date/workflow_instance_id/attachment_urls`、`shipment_no/pick_id/order_id/carrier_name/tracking_no/ship_date/sign_time/status`、`amount/received_amount/invoice_no/due_date/status` 和 `amount/paid_amount/invoice_no/due_date/status`。精确检索 `rg "inv_sales_quote|inv_sales_contract|inv_receivable|inv_payable|inv_shipment|SalesQuote|SalesContract|InvReceivable|InvPayable|InvShipment"` 在前端、后端、API 和 SQL 中没有运行代码命中；菜单按“报价/合同/发运/应收/应付/quote/contract/shipment/receivable/payable”只命中 OA 劳动合同，没有库存销售商务入口；`erp-ui/src/views/inventory` 只存在 `customer`、`sales`、`salesReturn`、`deliveryNotice`、`supplier` 等页面。
- 影响：销售从报价、合同签署、履约发运、开票、收款到应收账龄的链路无法在系统内表达；采购侧应付也没有与采购单、发票和付款状态闭环。多店铺经营中，不同门店客户报价、合同附件、物流签收和回款状态是经营和财务口径的核心，当前只能散落在销售单/发货通知之外，数据库表也不会自动产生或被页面消费。
- 建议：明确是否交付销售商务与结算模块。若交付，应补齐报价单、合同台账、合同审批/附件、发运跟踪、应收/应付账款、发票号、到期日、收付款状态、门店权限和导出审计；若当前只交付轻量销售/库存流程，应下线或标注这些表为预留，并避免让用户误以为已有销售合同和财务账款能力。

### P3-72 供应商评分表存在，但供应商页面没有履约评价或自动评分

- 现象：数据库有 `inv_supplier_score` 表，字段覆盖供应商、评分周期、按期交付率、质量合格率、订单数和总评分；但供应商电脑端只提供基础资料、合作状态、详情和供货商品列表，没有供应商评分、履约趋势、质量评价或评分规则。后端供应商接口也只覆盖列表、详情、供货商品、增删改和导出。
- 证据：MySQL 当前 `inv_supplier_score=0`，字段包括 `supplier_id/period/delivery_rate/quality_rate/total_orders/total_score/create_time`。精确检索 `rg "inv_supplier_score|SupplierScore|supplier_score|delivery_rate|quality_rate|total_score"` 在前端、后端、API 和 SQL 中没有业务代码命中。`sys_menu` 供应商相关菜单只有 `供应商管理` 及查询、新增、修改、删除、导出按钮；`erp-ui/src/views/inventory/supplier/index.vue:32-70` 列表列只有编码、名称、联系人、电话、邮箱、结算方式、合作状态和启用状态，详情 `:75-111` 只展示基础信息和供货商品；`InvSupplier` 领域对象也没有评分字段，`InvSupplierController` 只暴露 `/list`、`/{supplierId}`、`/{supplierId}/products`、新增、修改、删除和导出。
- 影响：采购页面和供应商页面无法根据历史到货及时率、质检结果或订单量评估供应商表现，合作状态只能人工改为合作中/暂停/终止，没有评分依据。多店铺采购里，同一供应商在不同门店或仓库的履约质量差异无法沉淀，后续采购决策和供应商淘汰缺少系统证据。
- 建议：若保留 `inv_supplier_score`，应定义评分周期和计算口径，把采购到货、质检结果、退货、延期和订单量接入评分生成任务，并在供应商列表/详情展示评分趋势、评分明细和门店范围；若不交付供应商绩效，应下线或标注该表为预留。

### P3-73 库位、拣货、质检冻结和残次品表存在，但仓储执行没有主数据和台账闭环

- 现象：数据库有 `inv_location`、`inv_pick_task`、`inv_pick_task_detail`、`inv_qc_hold`、`inv_defective_stock` 表，字段覆盖库位主数据、拣货任务、质检冻结和残次品库存；但电脑端没有库位管理、拣货任务、质检冻结台账或残次品处理页面，后端也没有这些表的实体、Mapper、Controller 或服务。当前库存页只把库位编码/名称当作自由文本录入和筛选，采购质检也只更新采购单的 `qcStatus`。
- 证据：MySQL 当前 `inv_location=0`、`inv_pick_task=0`、`inv_pick_task_detail=0`、`inv_qc_hold=0`、`inv_defective_stock=0`；字段包括 `location_code/location_name/warehouse_id/area/shelf/layer/position/capacity/current_load/status`、`pick_no/warehouse_id/shop_dept_id/status/picker_id`、`order_id/product_id/pick_qty/location_id/batch_id`、`source_type/source_id/quantity/qc_result/hold_until/status/inspector_id`、`defect_reason/status`。精确检索 `rg "inv_location|inv_pick_task|inv_pick_task_detail|inv_defective_stock|inv_qc_hold|InvLocation|InvPickTask|PickTask|DefectiveStock|QcHold"` 在前端、后端、API 和 SQL 中没有运行代码命中。菜单按库位、拣货、残次、不良、冻结、质检只命中采购单上的“采购质检/质检记录”按钮权限；`erp-ui/src/views/inventory/stock/index.vue:80-84` 和 `:256-260` 只是输入 `locationCode/locationName`，没有从 `inv_location` 选择或校验；采购页 `erp-ui/src/views/inventory/purchase/index.vue:48-60` 只展示并触发采购单质检。
- 影响：仓库货架、容量、库区、拣货路径、冻结待检库存和残次品库存都无法形成可操作台账。多店铺多仓库场景下，库存数量可能存在，但库位容量、拣货任务和质检冻结状态不可追踪；仓库人员只能手填库位文本，质检不合格也难以沉淀到残次品或冻结库存，后续发货、调拨和盘点都会缺少实物定位依据。
- 建议：若交付仓储执行能力，应补齐库位主数据、库位选择、容量校验、拣货任务生成/分配/完成、质检冻结、残次品转入/处理、状态流水和权限；若当前只做简化库存，应下线或标注这些表为预留，并在库存页明确库位只是文本标签，不是受控库位主数据。

### P3-74 成本事件表存在，但库存成本变动没有前后值审计

- 现象：数据库有 `inv_cost_event` 表，字段覆盖成本事件类型、来源单据、数量、单位成本、总成本、变动前加权成本、变动后加权成本和事件时间；但当前采购入库、销售出库、调拨、盘点、退货和库存调整都没有写入这张表。电脑端也没有成本事件查询或成本重算/追溯页面。
- 证据：MySQL 当前 `inv_cost_event=0`，字段包括 `event_type/document_type/document_id/document_no/quantity/unit_cost/total_cost/before_cost/after_cost/event_time`。窄检索 `rg "inv_cost_event|CostEvent|before_cost|after_cost"` 在前端、后端、API 和 SQL 中没有运行代码命中。当前成本变化由 `InvStockMapper.addInvStockWithCost` / `deductInvStockWithCost` 直接更新 `inv_stock.cost_price/total_cost`；采购入库、销售出库、销售退货、采购退货、调拨、盘点和库存调整服务都计算 `incomingCost` 或 `deductCost` 后调用库存 Mapper。`inv_stock_log` 只有 `cost_price` 字段，没有 `before_cost/after_cost`，只能看到当时成本价，无法还原加权成本变化过程。
- 影响：多店铺多仓库下，库存成本是毛利、报表、退货、调拨和盘点的敏感口径。当前能看到库存现值和部分流水成本价，但缺少每一次成本事件的前后成本、来源单据和计算结果；一旦成本异常，无法按商品、仓库、门店和单据追溯“哪次入库/出库/调拨改变了加权成本”。
- 建议：若保留 `inv_cost_event`，应在所有成本变动入口统一写入成本事件，记录 before/after、数量、单据、门店/仓库、操作人和事件时间，并提供成本事件查询、导出、权限和重算校验；若不交付成本事件审计，应下线或标注为预留，避免把库存流水误认为完整成本审计。

### P3-75 调拨收货单表存在，但当前收货流程没有差异收货和差异审批

- 现象：数据库有 `inv_transfer_receipt` 表，字段覆盖调拨收货单号、实收、拒收、损耗、是否差异、差异审批流程实例和差异状态；但当前调拨收货流程没有写入这张表，也没有电脑端“调货收货管理”页面。源码中的调拨收货设计围绕发货批次 `inv_transfer_shipment`，并要求本期按发货批次全量收货，不能记录拒收、损耗或差异审批；当前库又缺少发货批次表，详见 P1-22。
- 证据：MySQL 当前 `inv_transfer_receipt=0`，字段包括 `receipt_no/transfer_id/from_warehouse_id/to_warehouse_id/total_received_qty/total_rejected_qty/total_loss_qty/has_diff/diff_workflow_instance_id/diff_status/receipt_by/receipt_time`。全仓 `rg "inv_transfer_receipt|TransferReceipt|diff_workflow_instance_id|diff_status|收货差异|差异审批"` 除本报告外没有 mapper、service、controller 或前端页面命中。`sys_menu` 保留停用隐藏的 `4144 调货收货管理 / inventory/cross-store-transfer/receive`，但 `erp-ui/src/views/inventory` 下没有 `cross-store-transfer` 组件。当前 API 只有 `/inventory/transfer/receive/{transferId}` 和 `/inventory/transfer/shipment/receive/{shipmentId}`；后端 `InvTransferServiceImpl.receiveTransferShipment` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:467-492` 明确校验显式收货数量必须等于待收数量，否则抛出“本期必须按发货批次全量收货”。
- 影响：门店实际收货常见的少收、破损、拒收、运输损耗无法在系统里形成收货单和差异审批。仓库已发、门店少收时，当前流程只能全量收货或失败，库存、责任和审批都没有落点；`inv_transfer_receipt` 表暗示已有 RC 收货单和差异审批能力，但运行链路完全绕开它。
- 建议：若保留 `inv_transfer_receipt`，应补齐调货收货页面、按批次差异收货、拒收/损耗录入、差异审批、库存入账规则、状态流水和导出；若当前只支持全量批次收货，应下线或标注该表为预留，并在收货弹窗明确“不支持差异收货”。

### P3-76 售后工单表存在，但电脑端只有销售退货流程

- 现象：数据库有 `inv_after_sales_order` 表，字段覆盖售后单号、原销售单、客户、售后类型、原因、状态和工作流实例；但电脑端没有售后工单、售后审批、换货、维修、补发或赔付页面和接口。当前库存模块只有销售退货页面，流程只围绕退货单保存、提交、确认退货和取消。
- 证据：MySQL 当前 `inv_after_sales_order=0`，字段包括 `as_order_no/original_order_id/customer_id/as_type/as_reason/status/workflow_instance_id/create_by/create_time`。精确检索 `rg "inv_after_sales_order|AfterSales|after_sales|as_order_id|as_order_no|workflow_instance_id"` 在前端、后端、API 和 SQL 中没有运行代码命中。菜单按“售后/退货/after/return”只命中采购退货和销售退货，未命中售后工单；`erp-ui/src/views/inventory` 只有 `salesReturn/index.vue`、`purchaseReturn/index.vue` 和 `sales/index.vue`，没有 after-sales 页面。`erp-ui/src/api/inventory/salesReturn.js` 只提供销售退货列表、我的、详情、保存、提交、确认退货和取消。
- 影响：售后工单与普通销售退货混在一起会丢失售后类型、客户诉求、处理方案、审批、责任归属和补偿记录。多店铺场景下，门店售后、换货、维修、补发、赔付等流程无法用系统闭环表达；数据库表又暗示已有售后工单和工作流实例，容易让管理员误判功能范围。
- 建议：若保留 `inv_after_sales_order`，应补齐售后工单页面、售后类型配置、处理动作、工作流审批、销售退货/换货/补发/赔付关联、状态流转和权限；若只交付销售退货，应下线或标注该表为预留，并在页面文案上避免把“销售退货”称作完整售后。

### P3-77 批次主数据表存在，但库存批次只是自由文本字段

- 现象：数据库有 `inv_batch` 批次主表，字段覆盖批次号、商品、店铺/仓库、生产日期、效期、初始数量、当前数量、状态、供应商和来源单据；但当前电脑端没有批次管理页面，后端没有 `inv_batch` 的实体、Mapper、Service 或 Controller。库存页面只在 `inv_stock` 和 `inv_stock_log` 上展示、筛选和手填 `batchNo/expiryDate/serialNo`，没有受控批次台账和批次状态。
- 证据：MySQL 当前 `inv_batch=0`，字段包括 `batch_no/product_id/shop_dept_id/warehouse_id/production_date/expiry_date/initial_quantity/current_quantity/status/supplier_id/source_type/source_id`；`sys_menu` 中 `4138 批次管理 inventory/batch/index inv:batch:list`、`4139 批次查询 inv:batch:query` 均为 `status=1/visible=1` 且角色授权数为 0。精确检索 `rg "inv:batch|inventory/batch|批次管理"` 在前端、后端、API 和 SQL 中没有运行代码命中。库存页 `erp-ui/src/views/inventory/stock/index.vue:66-84`、`:199-206`、`:241-260` 只把批次、效期、序列号和库位作为输入字段；`InvStockMapper.xml` 也只读写 `inv_stock.batch_no/expiry_date/serial_no/location_code/location_name`。
- 影响：同一商品在不同批次、供应商、来源单据和效期下无法形成独立生命周期。多店铺多仓库场景中，批次过期、批次关闭、批次追溯、先到期先出和问题批次召回都无法依赖 `inv_batch` 执行，只能靠人工输入的文本批次号推断，容易出现同名批次、错填效期和库存数量与批次主表不一致。
- 建议：若交付批次管理，应让采购入库、调拨入库、库存调整、销售出库和退货统一创建/扣减 `inv_batch`，提供批次台账、状态流转、效期预警、来源追溯、门店/仓库权限和导出；若只保留库存字段，应下线或标注 `inv_batch` 为预留，并在库存页明确批次号只是手工标签，不是受控批次主数据。

### P3-78 采购计划和通用收货单表存在，但采购主流程没有使用

- 现象：数据库有 `inv_purchase_plan`、`inv_purchase_plan_detail`、`inv_receipt` 和 `inv_receipt_detail`，字段覆盖采购计划、建议采购量、日均销量、转采购单、通用收货单和收货明细的实收/拒收/批次/库位；但当前电脑端没有采购计划页面，也没有收货单台账。采购主流程只提供采购单保存、提交、收货、质检、取消和删除草稿，采购收货直接写 `inv_inbound_record` 待检入库记录，不写 `inv_receipt/inv_receipt_detail`。
- 证据：MySQL 当前 `inv_purchase_plan=0`、`inv_purchase_plan_detail=0`、`inv_receipt=0`、`inv_receipt_detail=0`；计划明细字段包括 `current_stock/safety_min/suggested_qty/avg_daily_sales/purchase_order_id`，收货单字段包括 `receipt_no/order_type/order_id/warehouse_id/supplier_id/total_received/total_rejected/status/receipt_by/receipt_time`，收货明细字段包括 `ordered_qty/received_qty/rejected_qty/batch_id/location_id`。精确检索 `rg "inv_purchase_plan|inv_purchase_plan_detail|PurchasePlan|purchasePlan|inv:purchasePlan|inv_receipt|inv_receipt_detail|InvReceipt|ReceiptDetail|receipt_no|total_rejected"` 在前端、后端、API 和 SQL 中没有运行代码命中。`sys_menu` 中采购计划 `4095-4099` 均为 `status=1/visible=1`；运行中的采购 API 只有 `/inventory/purchase/save`、`submit`、`list`、`my`、详情、`receive`、`qc`、取消和删除草稿。`InvPurchaseServiceImpl.receivePurchase` 在收货时创建 `InvInboundRecord` 并设置 `qcResult=pending`，没有创建通用收货单。
- 影响：低库存预警不能一键生成采购计划，安全库存、日均销量和建议采购量不能转采购单；采购收货也没有独立收货单号、明细实收/拒收、批次库位、收货人和收货时间台账。多店铺多仓库场景中，补货计划、收货批次、拒收数量和后续质检只能散落在采购单明细和入库记录里，采购计划与收货单两类数据库模型实际不会被用户看到或消费。
- 建议：若保留这些表，应补齐低库存生成采购计划、采购计划审核/删除、转采购单、收货单生成、实收/拒收汇总、质检关联、门店/仓库权限和导出；若当前只交付轻量采购单和待检入库记录，应下线或标注这些表为预留，并清理停用菜单，避免后续误启用。

### P3-79 查询和批量强退权限码在权限树中启用，但源码使用另一套权限

- 现象：数据库启用了参数、操作日志、登录日志、在线用户的“查询”按钮权限，以及在线用户“批量强退”权限；但当前前后端实际使用的是页面级 `list` 权限和单条 `forceLogout` 权限。这些 `query` / `batchLogout` 权限在源码中没有任何真实判断点。
- 证据：MySQL 当前 `sys_menu` 中 `1030 参数查询 system:config:query`、`1039 操作查询 system:operlog:query`、`1042 登录查询 system:logininfor:query`、`1046 在线查询 monitor:online:query`、`1047 批量强退 monitor:online:batchLogout` 均为 `status=0/visible=0`；同一页面父菜单使用 `system:config:list`、`system:operlog:list`、`system:logininfor:list`、`monitor:online:list`。精确检索源码只发现 `SysConfigController.list` 用 `system:config:list`、`SysOperlogController.list` 用 `system:operlog:list`、`SysLogininforController.list` 用 `system:logininfor:list`、`SysUserOnlineController.list` 用 `monitor:online:list`，在线强退前端和后端都只使用 `monitor:online:forceLogout`。`rg "system:config:query|system:operlog:query|system:logininfor:query|monitor:online:query|monitor:online:batchLogout"` 在前端、后端、API 和 common 源码中没有命中。当前这些权限角色授权数为 0，但仍会出现在权限树中。
- 影响：管理员后续给角色勾选“查询”或“批量强退”时，会以为已经授权对应动作，实际接口和按钮仍不认这些权限；反过来，真正能查询列表的是父级 `list` 权限，真正能强退的是 `forceLogout`。这会让最小权限配置、角色回归测试和权限审计结果不可信。
- 建议：统一权限命名：要么把前后端查询按钮和接口改用 `query`，批量强退补齐前端多选和后端批量接口；要么删除/停用这些无效按钮权限，只保留实际生效的 `list` 与 `forceLogout`，并在权限树中避免重复表达同一动作。

### P3-80 历史调拨权限树曾出现按钮挂按钮和重复授权节点

- 现象：早前运行库中，调拨管理权限树曾把 `4109 / 仓库出库修改` 挂在另一个按钮 `4107 / 调拨取消` 下，并与正常的 `4103 / 调拨修改` 共用 `inv:transfer:edit`。这会让角色授权树里同一能力出现两个节点，且按钮节点成为父级没有清晰产品语义。
- 当前复核：2026-06-25 重新直查当前运行态 `sys_menu`，`4109` 已变为 `调拨审批 / parent_id=4100 / perms=inv:transfer:approve`，`4107` 仍为 `调拨取消 / inv:transfer:remove`，`button_parent_count=0`，旧的 `4109 / 仓库出库修改 / inv:transfer:edit / parent_id=4107` 存量脏数据已不存在。当前仍有启用重复权限码，例如库存、调拨、调拨记录、报表、薪资和店铺授权相关权限；菜单管理保存链路仍未阻止按钮作为父级，详见 P3-198 和 P3-165。
- 影响：历史脏数据说明菜单树缺少发布级结构校验；如果后续通过菜单管理或 SQL 再次写入按钮父级、重复权限或父级停用子级启用，角色授权树、权限统计和页面入口都会再次变得不可解释。多账号验收时，这类问题会被误判成单个业务模块配置错，而不是菜单基础数据治理缺口。
- 建议：保留发布前菜单结构巡检：`menu_type=F` 不能作为父级，启用权限码重复必须有白名单说明，父级停用时子级权限应同步停用或标记不可授权。调拨管理当前无需再按旧 `4109 / inv:transfer:edit` 清理，但仍应把 P3-198 的保存链路校验补齐，防止同类脏数据复发。

### P3-81 店铺配置列表权限同时作为页面入口和按钮权限

- 现象：店铺配置菜单把 `system:userShop:list` 同时配置在页面节点 `181 店铺配置` 和按钮节点 `1192 店铺配置列表` 上，但电脑端页面没有一个受 `system:userShop:list` 单独控制的“列表”按钮。这个权限实际既代表页面入口，又代表组织树接口访问。
- 证据：MySQL 当前 `181 店铺配置 menu_type=C perms=system:userShop:list`，其子按钮 `1190 店铺配置查询 system:userShop:query`、`1191 店铺配置修改 system:userShop:edit`、`1192 店铺配置列表 system:userShop:list` 均启用，且 `181` 与 `1192` 都只授给超级管理员。前端 `erp-ui/src/views/system/shop/index.vue:29-30` 的搜索/重置按钮没有 `v-hasPermi`，`:78` 的保存按钮使用 `system:userShop:edit`；页面创建时 `:154-159` 调用用户列表、岗位选项和 `getShopTree()`。后端 `SysUserShopController.tree()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java:41-45` 使用 `system:userShop:list`，但该权限没有对应独立按钮。
- 影响：角色授权时管理员会看到“店铺配置”页面和“店铺配置列表”按钮两个同名能力，实际勾选任意一个都会产生同一个权限码，难以表达“能进页面但不能加载组织树”或“能加载组织树但不显示页面”。这会让多账号最小权限配置和审计脚本对列表/查询/修改三类动作的判断变得不可靠。
- 建议：保留一种清晰语义：页面入口使用 `system:userShop:list` 时，应删除或停用 `1192 店铺配置列表`；如果需要按钮级列表权限，则页面节点不要复用同一权限码，并在前端列表加载入口明确使用对应权限。店铺配置权限树应只保留 `list/query/edit` 三个互不重复的授权点。

### P3-82 固定资产无资产配置和零额度时仍暴露上报入口

- 现象：运行态固定资产配置、额度和维修上报表均为空，但配置页仍显示“新增固定资产 / 异常批准 / 导出”，维修页仍显示“新建上报 / 导出”。维修上报弹窗提示“预计维修金额不能超过当前可用额度：¥0.00”，但默认预计金额为 `0.01`，并且“上报”按钮仍可见。
- 证据：运行态 `oa_fixed_asset_config=0`、`oa_fixed_asset_quota=0`、`oa_fixed_asset_repair=0`、`oa_fixed_asset_quota_ledger=0`。2026-06-23 Playwright 复核 `/oa/fixed-asset/config` 时，新建固定资产弹窗可打开，必填项包括店铺和固定资产商品，数量默认 `1.00`、资产价格 `0.00`、比例 `20.00`；异常批准弹窗金额默认 `0.01`、批准类型为 `advance_future_months`、月份 `1`。`/oa/fixed-asset/repair` 的新建上报弹窗显示当前可用额度 `¥0.00`，商品选择器提示“选择本店铺已配置资产”，附件仍是手填 URL 字段。
- 影响：空数据状态下用户能进入明显无法成功的流程，且默认金额已经超过可用额度；普通门店用户不知道应先配置资产、初始化额度还是申请异常批准。固定资产模块刚上线或新店铺首次使用时，这会造成表单提交失败、咨询成本和错误数据尝试。
- 建议：当当前店铺没有固定资产配置或可用额度为 0 时，维修上报和异常批准应禁用或给出“先配置固定资产/初始化额度”的空态引导；默认金额不能超过可用额度，资产选择器应明确显示“暂无已配置资产”。异常批准如果允许零额度场景发起，也应把业务含义写清楚，并在提交前做专门确认。

### P3-97 固定资产维修上报状态枚举多于真实流程，驳回和取消没有写入口

- 现象：固定资产维修上报页筛选项、详情展示和导出状态转换都把维修单描述成有 `草稿 / 待确认上报 / 已上报 / 已驳回 / 已取消` 多状态流程，但电脑端当前只能新建上报、把异常批准单确认上报、查询和导出，没有草稿保存、驳回、取消、撤回或重新提交入口。
- 证据：前端 `erp-ui/src/views/oa/fixedAsset/repair/index.vue:24-27` 的状态筛选包含 `pending_confirm/submitted/rejected/cancelled`，`:281-287` 的 `statusLabel` 还包含 `draft`；行操作只在 `status === 'pending_confirm'` 时显示“确认上报”，没有驳回或取消按钮。后端 `OaFixedAssetRepairController` 只有 `/list`、`/{repairId}`、`/submit`、`/{repairId}/confirm`、`/export`；`OaFixedAssetServiceImpl` 只定义并写入 `pending_confirm` 和 `submitted`，`confirmApprovedRepair()` 也只是把 `pending_confirm` 改成 `submitted`。领域对象 `OaFixedAssetRepair` 的 Excel 转换却声明 `draft=草稿,pending_confirm=待确认上报,submitted=已上报,rejected=已驳回,cancelled=已取消`。当前运行态 `oa_fixed_asset_repair=0`，尚无存量状态能证明这些枚举来自历史数据。
- 影响：用户会以为维修上报有可撤回、可取消或可驳回的审批闭环，但真正流程只有“提交”和“确认提交”。异常批准单如果门店核对发现不对，只能不确认，无法驳回给管理员或取消释放额度；普通上报一旦提交也没有撤回或作废路径。后续报表按“已驳回/已取消”筛选时会长期空白，流程状态和页面文案不一致。
- 建议：先明确维修上报真实状态机。若只交付轻量上报，应移除草稿、驳回、取消状态筛选和导出转换；若需要完整流程，应补草稿保存、取消/撤回、管理员驳回、重新提交、额度流水反冲和状态审计，并把按钮权限拆成 `submit/confirm/reject/cancel` 等真实动作。

### P3-159 固定资产维修“详情”只展开列表行，查询接口和权限点没有被电脑端使用

- 现象：固定资产维修上报列表有“详情”按钮，后端也提供 `/oa/fixedAsset/repair/{repairId}` 详情接口和 `oa:fixedAsset:repair:query` 权限点，但电脑端点击详情只是把当前列表行复制到弹窗，不请求详情接口；详情按钮本身也没有 `v-hasPermi="['oa:fixedAsset:repair:query']"`。
- 证据：`erp-ui/src/views/oa/fixedAsset/repair/index.vue:81` 的详情按钮直接调用 `showDetail(scope.row)`，`:269-272` 的 `showDetail(row)` 只执行 `this.detail = Object.assign({}, row)` 和打开弹窗；`erp-ui/src/api/oa/fixedAsset.js:57-62` 定义了 `getFixedAssetRepair(repairId)`，但桌面维修页没有导入或调用它。后端 `OaFixedAssetRepairController.java:31-44` 分别声明列表权限 `oa:fixedAsset:repair:list` 和详情权限 `oa:fixedAsset:repair:query`；`OaFixedAssetRepairMapper.xml:47-68` 中列表和详情当前都使用同一组 `baseColumns`。运行态 `oa_fixed_asset_config/repair/quota/quota_ledger` 均为 0 行，维修上报 `list/query/add/confirm/export` 权限当前只授给 `admin`，其中 `3321 维修上报查询` 在电脑端没有真实按钮闭环。
- 影响：权限树显示存在“维修上报查询”能力，但桌面端并不真正使用它；后续若配置“可看列表但不可看详情”的角色，用户仍会看到详情按钮和列表中已带出的详情字段。列表接口也会被迫长期返回故障说明、附件、额度等完整字段，无法稳定拆成摘要列表和详情页；如果后端详情接口后续补充附件、额度流水或审计信息，电脑端也拿不到。
- 建议：维修详情按钮应加 `oa:fixedAsset:repair:query` 权限控制，并在点击时调用 `getFixedAssetRepair(repairId)` 获取详情；列表接口改成摘要 DTO，只返回表格必需字段，完整故障说明、附件、额度快照、确认信息和后续审计信息放到详情接口。没有查询权限时，应只展示列表摘要或禁用详情入口。

### P3-98 OA 工资记录只有草稿生成，没有确认、调整和锁定闭环

- 现象：工资记录表和导出模型都存在 `draft/confirmed` 状态语义，Mapper 也预留了奖金、扣款、总工资和状态更新 SQL，但电脑端工资管理当前只有查询、计算、工资参数和导出，没有工资确认、人工调整、锁定或撤销确认入口。
- 证据：`oa_salary_record.status` 默认值为 `draft`，当前运行态 `oa_salary_record=0`；`OaSalaryServiceImpl.calculateSalary()` 生成每条工资时固定 `sr.setStatus("draft")`。`OaSalaryRecord` 的 Excel 状态转换声明 `draft=草稿,confirmed=已确认`，`OaSalaryRecordMapper.updateOaSalaryRecord()` 可以更新 `other_bonus/other_deduction/total_salary/status`，但源码检索没有 Controller、Service 或前端调用该更新方法。`erp-ui/src/views/oa/salary/index.vue` 表格只展示工资明细金额，不展示状态列，也没有行操作。
- 影响：工资计算后记录会长期停在草稿状态，业务人员无法在电脑端完成复核确认、奖金扣款调整、锁定发薪版本或撤销确认。导出动作会把草稿工资直接导出，且再次点击“计算本月工资”会按店铺和月份覆盖已有记录，缺少“已确认后禁止覆盖”的保护。
- 建议：补齐工资确认流程：计算后进入草稿，允许有权限人员调整其他奖金/扣款并重算实发工资，确认后状态变为 `confirmed` 并禁止普通重算覆盖；如需重算已确认工资，应走撤销确认或重算审批。页面应展示状态列、确认/撤销/调整按钮和覆盖风险提示，后端按状态加锁。

### P3-151 OA 工资页“我的/全部”视图没有传给导出，停留“我的”也会导出全部工资

- 现象：工资页列表会按 `viewScope` 在“我的”和“全部”之间切换，但导出按钮不带这个视图状态；后端导出接口固定调用 `selectAllRecords()`。因此有工资导出权限的管理账号即使当前停留在“我的”视图，也会导出当前组织范围内全部员工工资，和页面所见范围不一致。
- 证据：`erp-ui/src/views/oa/salary/index.vue:5-18` 提供“我的/全部”切换和导出按钮，`:170-178` 的 `getList()` 会按 `viewScope === "all" && canViewAllSalary()` 在 `listAllSalary` 和 `listMySalary` 之间选择，但 `:233-238` 的 `handleExport()` 只传 `queryParams/salaryMonth`，没有传 `viewScope` 或当前用户。`OaSalaryController.export()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSalaryController.java:90-98` 要求 `oa:salary:export` 后固定调用 `salaryService.selectAllRecords(record, ...)`；`OaSalaryServiceImpl.selectMyRecords()` 在 `:84-88` 会设置当前 `userId`，`selectAllRecords()` 在 `:91-96` 只追加组织范围；`OaSalaryRecordMapper.xml:89-104` 只有 `record.userId` 存在时才按个人过滤。运行态权限矩阵显示 `admin/yyjl/zjl` 均有 `oa:salary:list/export`，`dz` 只有 `oa:salary:query`。
- 影响：工资属于强敏感数据。用户在“我的”视图看到的是个人工资，导出确认也只写“当前查询条件”，但实际导出会扩大到全部工资；多店铺或门店工资核算时容易把他人工资误导出、误外发。若未来拆出有 `oa:salary:export` 但无 `oa:salary:list` 的角色，后端还会让“导出”绕过 `list` 的管理语义。
- 建议：导出接口应显式接收 `scope=my/all` 并按权限分支：`my` 只导出当前用户工资，`all` 必须要求 `oa:salary:list` 或管理导出权限。前端导出确认要展示“导出我的工资/导出全部工资”和当前组织、月份、记录数；默认跟随当前视图，不要隐式扩大范围。

### P3-83 前端视图目录保留未路由旧页面和重复副本，页面验收口径容易混乱

- 现象：桌面端视图和 API 目录里存在当前路由未引用的旧页面/副本文件，例如旧首页 `index_v1.vue`、字典详情抽屉 `system/dict/detail.vue`、劳动合同重复副本 `oa/laborContract/index 2.vue` 和 `api/oa/laborContract 2.js`；移动端合同目录也有 `index 2.vue`。这些文件不一定影响当前运行页面，但会干扰“每个页面、每个副页面、每个接口功能”的验收清单。
- 证据：静态检查 `erp-ui/src/views` 共 93 个 `.vue` 文件；剔除移动端、图表和明显内部组件后，检查 66 个桌面可路由/副页面文件，有 11 个未被报告路径直接命中。继续用 `rg` 追踪后确认：`monitor/job/detail.vue`、`system/notice/ReadUsers.vue`、`system/user/view.vue`、`tool/gen/basicInfoForm.vue`、`tool/gen/genInfoForm.vue` 是被主页面 import 的副组件，`system/dict/data.vue` 是动态隐藏路由，`401/404` 是公共错误页；但 `index_v1.vue`、`system/dict/detail.vue`、`oa/laborContract/index 2.vue` 当前没有路由或 import 命中。`cmp` 还确认 `oa/laborContract/index.vue` 与 `index 2.vue` 不完全相同，`oa/laborContract.js` 与 `oa/laborContract 2.js` 也不完全相同，且 `rg "laborContract 2"` 未发现运行 import。
- 影响：后续测试人员按文件清单验收时，会把未路由旧页面当作漏测页面；按菜单/路由验收时，又会忽略这些历史文件可能携带旧交互、旧权限或旧字段。重复副本和未引用组件也会增加维护成本，发布包审查时难以判断哪个页面才是权威实现。
- 建议：建立“运行路由/菜单 -> 前端组件 -> 前端 API -> 后端接口”页面清单，把未路由旧文件标注为废弃并移出运行目录；需要保留的副组件要被主页面显式 import 并纳入页面清单。重复副本如 `index 2.vue`、`laborContract 2.js` 应比较差异后删除或迁移有效变更，避免验收和发布口径继续分裂。

### P3-84 启用权限码仍有 15 组重复，角色授权树不能稳定表达入口差异

- 现象：当前运行态 `sys_menu` 中仍存在 15 组启用权限码重复，不只限于已单列的调拨编辑和店铺授权列表。重复项覆盖薪资、库存、调拨、调拨记录和报表等多个页面；同一个权限码既可能是页面入口，又可能是按钮，或者同一组件在“进销存管理”和“仓库管理”两棵菜单下各有一套节点。
- 证据：2026-06-23 使用脚本比对当前运行库 `sys_menu` 与源码权限，数据库共有 226 个权限码、其中 221 个启用；前端 `v-hasPermi` 权限码 160 个、后端 `@RequiresPermissions` 权限码 206 个，前端按钮权限均能找到后端注解。进一步统计启用重复权限码得到 15 组：`system:salary:list`、`system:userShop:list`、`inv:stock:list/query/adjust/log/export`、`inv:transfer:list/query/deliver/export`、`inv:transfer:records/records:query/records:export`、`inv:report:list`。其中 `inv:stock:list` 同时存在于 `4040 进销存库存管理` 和 `4450 仓库库存管理`，`inv:stock:log` 同时作为两个按钮和 `4490 库存变动日志` 页面权限，`inv:report:list` 同时作为报表页面和“报表查询”按钮权限。
- 影响：角色配置时无法稳定区分“能看到哪个入口”和“能执行哪个按钮/查询动作”；审计脚本按权限码去重后会丢失菜单树位置，按菜单节点统计又会把一个能力算成多个授权。多账号多店铺验收时，门店角色、仓库角色和管理角色看到的入口差异会被重复权限码掩盖，后续清理某一菜单分支也可能误伤同权限码下的另一分支。
- 建议：建立启用权限码唯一性校验，至少要求同一 `perms` 在启用状态下只能对应一个明确语义；如果一个组件确实需要出现在两个菜单父级下，应拆分路由入口权限和动作权限，或在验收矩阵中显式标注“同权限多入口”。页面级 `list` 与按钮级查询/导出/日志入口不要复用同一权限码，库存、调拨、报表和薪资菜单应优先清理重复节点。

### P3-152 菜单“是否缓存”显示为缓存，但 21 个启用页面路由名与组件名不匹配导致 keep-alive 实际失效

- 现象：菜单管理页的“是否缓存”提示写明需要匹配组件 `name`，当前运行态 47 个启用页面菜单全部配置为 `is_cache=0 / 缓存`，但其中 21 个页面的后端路由名和前端组件 `name` 不一致，实际不会进入 `<keep-alive>` 缓存。数据库、菜单页面和真实前端行为不对应。
- 证据：`erp-ui/src/views/system/menu/index.vue:266-276` 把 `isCache=0` 展示为“缓存”，`:421-431` 新增菜单默认也是 `isCache:"0"`；`SysMenuServiceImpl.buildMenus()` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java:188-194` 把 `is_cache=1` 转为 `meta.noCache=true`，`getRouteName()` 在 `:457-460` 用 `route_name` 或 `path` 首字母大写生成路由名；`AppMain.vue:4-6` 用 `<keep-alive :include="cachedViews">`，`tagsView.js:56-60` 只把 `route.name` 放入缓存列表。运行态比对 `sys_menu.route_name/path/component` 与 Vue `export default name` 后，21 个启用缓存页面不匹配，例如 `4001 商品管理 route=Product` 但组件名 `InvProduct`、`4020 采购管理 route=Purchase` 但组件名 `InvPurchase`、`4100 调拨管理 route=Transfer` 但组件名 `InvTransfer`、`4410 调拨审批配置 route=Transfer-rules` 但组件名 `InvTransferApprovalRule`、`181 店铺授权 route=SystemUserShop` 但组件名 `UserShop`。后端保存只校验同级 `path` 和全局 `route_name` 唯一性，`SysMenuServiceImpl.checkRouteConfigUnique()` 在 `:400-431` 没有校验 `route_name` 与组件 `name` 是否能让缓存生效。
- 影响：用户在商品、采购、销售、调拨、库存盘点、发货通知等页面做筛选或编辑到一半，切到其它页再回来时可能被重新加载，和“页面已缓存”的配置预期不一致；管理员在菜单管理里也看不出哪些页面缓存真实生效。多账号多店铺巡检时，这会让标签页切换、组织切换后恢复和副页面返回列表的体验不稳定，问题还会被误判为某个业务页面单独丢状态。
- 建议：建立路由缓存一致性校验：启用 `is_cache=0` 的页面必须保证 `route_name` 与 Vue 组件 `name` 一致，或由构建脚本自动生成可匹配的 route name；不需要缓存的页面应明确配置 `is_cache=1 / 不缓存`。菜单保存时增加校验或至少提示“当前路由名与组件名不一致，缓存不会生效”，并对现有 21 个页面做一次批量修正。

### P3-85 采购待检入库记录有质检字段但电脑端没有批次台账

- 现象：采购收货后会生成 `inv_inbound_record` 待检入库记录，质检通过、让步或拒收后会回写质检结果、质检人、质检时间和质检备注，但电脑端没有“采购入库/质检记录”列表，采购单详情也不展示每次收货批次和质检轨迹。
- 证据：`InvPurchaseServiceImpl.receivePurchase()` 在收货时创建 `InvInboundRecord` 并设置 `qcResult=pending`；`qualityCheck()` 通过 `selectPendingInvInboundRecordByOrderId()` 读取待检记录后更新 `qcResult/qcUser/qcTime/qcRemark`。`InvInboundRecordMapper.xml` 具备按采购单查询入库记录的 SQL，但前端 `purchase/index.vue` 的详情、收货、质检弹窗只显示采购明细、收货数量和质检表单，没有调用或展示入库记录。运行态正式 `inv_inbound_record=0`，但 `inv_inbound_record_clear_backup_20260607_1640` 仍有 3 条历史记录，样例包括 `PO202606060001/PO202606060002/PO202606070001`、仓库 `104`、数量 `1/20/29`、`qc_result=passed`、`qc_user=admin`、`qc_time`。
- 影响：分批收货、重复质检、拒收后重新收货时，页面只能看到采购单当前质检状态，无法从电脑端追溯每批到货数量、质检结果、质检人、时间和备注。多仓库多账号下，采购、仓库和财务对账只能依赖数据库或日志，不能在业务页面完成收货/质检闭环核对。
- 建议：在采购详情中增加“收货/质检记录”副表，或提供独立的采购入库记录页面，按仓库、采购单、商品、质检状态、质检人和时间筛选并支持导出；若不交付独立台账，至少把最近收货批次、质检人、质检时间和备注展示到采购单详情时间线。

### P3-86 发货通知执行写出库记录但详情和导出没有出库台账

- 现象：发货通知执行后会写 `inv_outbound_record` 和库存流水，但电脑端发货通知详情只展示通知明细的通知数量、已发数量和未发数量，不展示每次实际出库记录；发货通知导出也只导出通知单头和汇总数量，不包含商品级发货明细或出库批次。
- 证据：`InvDeliveryNoticeServiceImpl.deliverNotice()` 在执行发货时创建 `InvOutboundRecord`，字段包括销售单、商品、发货仓库、数量、操作人和时间，同时写 `InvStockLog`；`InvOutboundRecordMapper.xml` 只有 `selectInvOutboundRecordByOrderId` 和插入 SQL，但全仓检索未发现前端或服务把该查询结果挂到发货通知详情。前端 `deliveryNotice/index.vue` 的详情弹窗只渲染 `detail.details` 中的商品、通知数量、已发数量、未发数量。导出接口 `InvDeliveryNoticeController.export()` 只调用 `selectNoticeList()` 并用 `ExcelUtil<InvDeliveryNotice>` 导出；`InvDeliveryNotice` 的 `@Excel` 字段是通知单号、销售单号、客户、状态、库存组织、发货仓库和通知/已发/待发汇总，`InvDeliveryNoticeDetail` 没有导出注解。当前运行态 `inv_delivery_notice=0`、`inv_outbound_record=0`、销售出库库存流水为 0，所以需要有销售样例后再做运行复现。
- 影响：销售单如果分多次、从不同仓库或不同时间执行发货，业务人员只能看到通知明细累计已发数量，无法在电脑端追溯每次是谁从哪个仓库发了多少；导出给仓库或财务对账时也缺少商品明细和出库批次。多门店多仓库下，这会削弱发货通知、库存流水和销售单之间的可核对性。
- 建议：发货通知详情增加“出库记录/发货批次”副表，展示出库记录 ID、商品、仓库、数量、操作人、时间和关联库存流水；发货通知导出应支持明细导出，至少包含每个商品的通知数量、已发数量、未发数量，已完成通知还应能导出实际出库记录。若继续只导出单头汇总，页面和导出确认文案应明确“不含商品明细”。

### P3-99 销售单页面没有展示后端已计算的已发货和待发货数量

- 现象：销售单列表和详情页只展示销售单号、标题、客户、金额、状态、销售数量、单价和金额；不展示整单总数量、已发货数量、待发货数量，也不在明细中展示每个商品的已发货数量。发货通知支持部分发货并会把通知状态置为 `delivering`，但销售页只能看到销售单仍是“已通知”，无法判断已经发了多少。
- 证据：`InvSalesOrderMapper.xml` 已在列表查询里按销售明细聚合 `total_quantity/delivered_quantity/remaining_quantity`，并映射到 `InvSalesOrder.totalQuantity/deliveredQuantity/remainingQuantity`；`InvSalesDetailMapper.xml` 也返回每条明细的 `delivered_quantity`。`InvSalesOrder` 对这三个聚合字段还加了 `@Excel`，导出可以带出已发货/待发货数量。但前端 `sales/index.vue` 的列表列只有销售单号、标题、客户、总金额、状态、创建时间和操作；详情表只有商品、规格、单位、数量、单价和金额，没有使用 `deliveredQuantity/remainingQuantity`。当前运行态正式 `inv_sales_order=0`、`inv_sales_detail=0`、`inv_delivery_notice=0`、`inv_outbound_record=0`，所以该结论来自源码和 schema 对照，暂未用存量单据复现。
- 影响：门店销售或运营在销售页无法直接区分“已生成通知但尚未发货”“部分发货中”和“全部发完前的剩余数量”，只能跳到发货通知或导出 Excel 才能确认。多账号协同下，销售、仓库和管理层对同一销售单的履约进度会割裂，尤其是部分发货、缺货补发和多次发货场景。
- 建议：销售列表增加“总数量 / 已发货 / 待发货”列或进度条；销售详情明细增加“已发 / 待发”列，并在 `noticed` 状态下显示“已通知未发货 / 部分发货中”的状态提示。导出字段和页面字段保持一致，避免只有 Excel 能看见履约进度。

### P3-87 调拨审批规则列表先分页再按组织内存过滤，多范围规则下分页和总数不可信

- 现象：调拨审批配置列表接口会先执行通用 `startPage()` 分页，再在服务层按当前选择组织过滤规则范围；过滤结果被重新组装成普通 `ArrayList` 返回。当前只有 1 条规则时页面不会暴露问题，但规则数量增加、且包含多个组织范围时，用户可能看到空页、总数偏小或漏掉后续页中本应可见的规则。
- 证据：`InvTransferApprovalRuleController.list()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferApprovalRuleController.java:32-38` 先调用 `startPage()`，再调用 `ruleService.selectRuleList(...)`。`InvTransferApprovalRuleServiceImpl.selectRuleList()` 在 `:46-57` 先取 `ruleMapper.selectRuleList(rule)`，再循环 `isRuleScopeVisible(...)` 做组织范围过滤，并把可见项加入新的 `ArrayList`。`InvTransferApprovalRuleMapper.xml:36-50` 的列表 SQL 只按规则名称、类型、范围类型、条件、策略、状态和创建时间过滤，没有把当前选择组织范围下推到 SQL。2026-06-24 当前数据仍只有 `rule_id=5 / 市场部门 / scope_type=dept / scope_id=108`，规则权限授给 `admin,zjl`，因此本轮没有构造写入更多规则验证分页错位。
- 影响：调拨审批规则是多门店、多仓库流程配置入口，列表分页如果不可信，管理员或总经理会误以为某些门店没有规则、规则数量不对，或者在搜索/翻页时漏看可管理规则；后续排查“调拨提交未匹配审批规则”时，配置页本身不能提供可靠的规则清单。
- 建议：把组织范围过滤下推到 mapper SQL，或先按当前选择组织取完整可见规则再做正确分页和 total 计算。列表接口应在多条不同 `scope_type/scope_id` 规则下补回归：第一页、第二页、搜索、状态筛选和总数都只基于当前用户可见范围计算。

### P3-88 菜单管理状态筛选字段初始化为 visible，和表单 status 不一致

- 现象：菜单管理列表顶部只提供“菜单名称”和“状态”两个筛选项，状态筛选的表单字段是 `status`，但页面初始化的 `queryParams` 却是 `menuName` 和 `visible`。当前用户选择状态后会临时生成 `queryParams.status` 并传给接口，初始参数和页面语义不一致。
- 证据：`erp-ui/src/views/system/menu/index.vue:13-21` 的状态下拉框使用 `prop="status"` 和 `v-model="queryParams.status"`；同文件 `:356-359` 初始化 `queryParams` 时写的是 `visible: undefined`，没有 `status`；`getList()` 在 `:385-388` 直接把 `queryParams` 传给 `listMenu`，`resetQuery()` 在 `:440-442` 只调用通用 `resetForm("queryForm")`。菜单 API `erp-ui/src/api/system/menu.js:4-9` 原样把参数作为 GET query 发送；后端 `SysMenuMapper.xml:42-47` 同时支持 `visible` 和 `status` 过滤，但当前页面没有“显示/隐藏”筛选项。
- 影响：菜单管理是权限、路由和按钮配置的基础设施，`visible` 和 `status` 分别代表“显示/隐藏”和“正常/停用”，初始化字段写错会让前端状态、重置逻辑和接口参数口径含混。后续如果补显示隐藏筛选、写自动化用例或排查菜单状态问题，容易把“隐藏”和“停用”混为一谈。
- 建议：把菜单管理查询参数初始化为 `{ menuName: undefined, status: undefined }`；如果产品上也需要按“显示状态”筛选，应单独增加可见性下拉框并绑定 `visible`，不要让隐藏参数留在当前状态筛选表单里。

### P3-89 字典类型和字典项共用一套 system:dict 权限，无法分开授权

- 现象：字典管理页和字典数据副页面都使用 `system:dict:*` 同一组权限，权限树无法表达“只能维护字典项，不能改字典类型”或“只能查看字典类型，不能新增/删除具体字典项”。字典类型行内“列表”入口还使用 `system:dict:edit` 才显示，查看字典项和修改字典类型在前端语义上也混在一起。
- 证据：当前数据库 `sys_menu` 中 `system:dict:%` 只有 6 个权限：`system:dict:list/query/add/edit/remove/export`，没有 `system:dictData:*` 或字典项专用权限。隐藏动态路由 `/system/dict-data/index/:dictId` 在 `erp-ui/src/router/index.js:138-147` 只要求 `system:dict:list`。字典类型页 `erp-ui/src/views/system/dict/index.vue:142-149` 的“修改”和“列表”都使用 `system:dict:edit`；字典数据页 `erp-ui/src/views/system/dict/data.vue:46-78`、`:117-129` 的新增、修改、删除、导出继续复用 `system:dict:add/edit/remove/export`。后端 `SysDictTypeController` 和 `SysDictDataController` 也分别在类型接口和数据接口上复用同一组注解。
- 影响：字典类型编码是系统级配置，字典项只是选项值维护，两者风险不同。后续如果把字典维护开放给非超级管理员，无法只授权业务人员维护某个字典的选项；一旦给 `system:dict:edit/remove`，就同时放开类型修改、字典项修改/删除和缓存刷新等能力，最小权限配置不清晰。
- 建议：拆出字典项独立权限，例如 `system:dictData:list/query/add/edit/remove/export`，并把字典类型“列表/查看数据”入口改为查看或列表权限。字典类型编辑、字典项维护、缓存刷新和导出应分别授权；角色树中也要把“字典类型”和“字典项数据”作为不同业务语义展示。

### P3-90 字典项缺少同类型键值唯一校验，后续可能出现重复枚举值

- 现象：当前 10 类字典的 29 个字典项没有发现同一 `dict_type` 下 `dict_value` 重复，但页面和后端没有阻止后续新增或修改出重复键值。重复后，同一个状态值可能对应多个标签、样式或排序。
- 证据：当前 MySQL `show index from sys_dict_data` 只显示主键 `dict_code`，没有 `(dict_type, dict_value)` 唯一索引；`select dict_type,dict_value,count(*) ... having count(*)>1` 当前返回 0 行。前端 `erp-ui/src/views/system/dict/data.vue:149-159` 只校验标签、键值和排序必填，未做同类型唯一检查。`SysDictDataController.add/edit` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDictDataController.java:92-110` 直接调用服务保存；`SysDictDataServiceImpl.insertDictData/updateDictData` 在 `:82-109` 只写入并刷新缓存。`SysDictDataMapper.xml:49-52` 的 `selectDictLabel` 直接按 `dict_type + dict_value` 查标签，没有处理重复结果。
- 影响：状态、性别、是否、通知类型、操作类型等字典被大量页面和导出复用。一旦某类字典出现重复键值，列表标签、筛选项、导出枚举和后端标签转换会不稳定，管理员在页面上也很难判断哪个重复项真正生效。多账号验收时，同一个状态码在不同页面可能显示出不同文案。
- 建议：在数据库增加 `(dict_type, dict_value)` 唯一约束，后端新增/修改前做同类型键值唯一校验，前端保存失败时提示“该字典类型下键值已存在”。如果业务确实需要同值多标签，应引入命名空间或额外生效条件，不能用同一键值承载多个含义。

### P3-91 内置字典类型可被改名或停用，缺少代码引用保护

- 现象：当前 `sys_dict_type` 里的 10 个字典类型都是系统页面直接引用的基础字典，但字典管理页允许直接修改 `dictType` 和状态；数据库也没有“系统内置/不可改编码”的字段。管理员如果把 `sys_normal_disable`、`sys_yes_no`、`sys_oper_type` 等编码改名或停用，页面仍按旧编码加载字典。
- 证据：当前 `sys_dict_type` 包含 `sys_user_sex`、`sys_show_hide`、`sys_normal_disable`、`sys_job_status`、`sys_job_group`、`sys_yes_no`、`sys_notice_type`、`sys_notice_status`、`sys_oper_type`、`sys_common_status`，且表结构只有 `dict_id/dict_name/dict_type/status/...`，没有内置标记。字典类型编辑弹窗 `erp-ui/src/views/system/dict/index.vue:174-193` 把 `dictType` 和状态都作为普通可编辑字段，提交时 `:337-346` 直接调用 `updateType(this.form)`。后端 `SysDictTypeServiceImpl.updateDictType` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDictTypeServiceImpl.java:191-203` 会把旧类型下的字典项级联改成新 `dictType` 并刷新新缓存，但不会检查前端/后端代码中仍硬编码引用旧类型。源码中多处 `dicts: ['sys_normal_disable']`、`dict.type.sys_yes_no`、`dict.type.sys_oper_type` 等直接引用这些编码。
- 影响：字典编码不是普通展示文案，而是页面状态、筛选、标签和导出枚举的运行契约。改名或停用内置字典后，菜单、用户、角色、部门、岗位、参数、通知、日志、定时任务等页面会出现筛选空白、标签缺失或状态解释错误；这种故障不容易从字典管理页反推。
- 建议：为 `sys_dict_type` 增加内置标记或保护清单，内置字典的 `dictType` 不允许修改，状态不允许停用，最多允许改展示名称和备注。保存前应校验该 `dictType` 是否被源码或配置引用；若允许自定义字典，应和系统内置字典在页面上明确分组。

### P3-92 调度日志不保存 job_id，任务改名或同名任务会让日志追溯不可靠

- 现象：从定时任务列表点击“调度日志”时，路由带的是 `jobId`，但调度日志表和日志查询接口没有 `job_id` 字段，前端实际是先按 `jobId` 查当前任务，再把当前任务名称和任务组塞进日志查询条件。当前 `sys_job_log=0` 不暴露问题；一旦任务改名、改组、删除重建，或后续存在同名同组任务，调度日志副页面就不能稳定追溯“这个任务编号”的历史执行记录。
- 证据：`erp-ui/src/views/monitor/job/index.vue:418-421` 从任务行跳转 `/monitor/job-log/index/{jobId}`。`erp-ui/src/views/monitor/job/log.vue:197-203` 读取路由 `jobId` 后调用 `getJob(jobId)`，只把 `response.data.jobName` 和 `response.data.jobGroup` 写入查询参数；`listJobLog()` 最终只按这些参数请求列表。MySQL `show columns from sys_job_log` 显示字段只有 `job_log_id/job_name/job_group/invoke_target/job_message/status/exception_info/start_time/end_time/create_time`，没有 `job_id`；`SysJobLog` domain 同样没有任务编号字段。`AbstractQuartzJob.after()` 在 `erp-modules/erp-job/src/main/java/com/erp/job/util/AbstractQuartzJob.java:74-94` 写日志时只设置 `jobName/jobGroup/invokeTarget`；`SysJobLogMapper.xml:28-39` 的日志列表 SQL 也只能按 `job_name/job_group/invoke_target/status` 过滤。当前 `sys_job` 有 3 条停用任务且无同名同组重复，`sys_job_log=0`。
- 影响：调度日志是定时任务排障和审计的唯一页面。若管理员把“系统默认（无参）”改名，历史日志会留在旧名称下，从当前任务行进入日志看不到历史；若删除后重建同名任务，新任务会混入旧任务日志。多店铺环境中，考勤同步、日志清理、库存预警等任务一旦失败，页面无法按稳定任务 ID 还原执行历史。
- 建议：给 `sys_job_log` 增加 `job_id` 并在执行日志写入时保存当前任务编号；从任务行打开调度日志时优先按 `job_id` 精确过滤，任务名称、任务组和调用目标只作为展示快照。任务改名、改组、删除重建时也应保留历史日志和当前任务的关系说明。

### P3-93 通知公告筛选字段口径不一致，状态列无法筛选

- 现象：通知公告列表展示“状态”列，查询参数也初始化了 `status`，但搜索区没有状态筛选，后端列表 SQL 也不按 `status` 过滤；相反，搜索区“类型”绑定 `queryParams.noticeType`，后端支持按 `notice_type` 过滤，但 `queryParams` 初始化里没有 `noticeType`。
- 证据：`erp-ui/src/views/system/notice/index.vue:21-29` 提供“类型”下拉并绑定 `queryParams.noticeType`，`:90-93` 展示状态列，`:217-223` 初始化查询参数时只有 `noticeTitle/createBy/status`，没有 `noticeType`。`erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeMapper.xml:30-42` 只按 `noticeTitle/noticeType/createBy` 过滤，没有 `status` 条件。当前运行态 `sys_notice=0`，本轮只做源码和表结构复核，未构造状态样例写入验证。
- 影响：管理员能看到公告状态，却不能按“正常/关闭”筛选；关闭公告不会出现在顶部公告，但管理页只能混在全部公告里人工识别。类型筛选虽然后端支持，但前端初始化字段缺失会让重置和表单状态更难追踪，和其他系统列表的查询模型不一致。
- 建议：查询参数、搜索区和 SQL 条件保持同一口径：补齐 `noticeType` 初始化，新增状态筛选并在 mapper 中增加 `status` 过滤；若状态不允许筛选，则移除无效 `status` 查询参数并在列表上给关闭公告更明显的标签。

### P3-94 顶部公告“全部已读”只处理下拉中的 5 条，未读数会被前端误清零

- 现象：顶部公告接口只返回最多 5 条正常公告，但未读数统计的是所有正常公告；前端点击“全部已读”时只把当前下拉列表里的公告 ID 发给后端，然后立即把 `unreadCount` 置为 0。若当前用户有超过 5 条未读公告，界面会显示全部已读，数据库仍保留未读。
- 证据：`SysNoticeController.listTop` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:90-102` 调用 `selectNoticeListWithReadStatus(userId, 5)`，并另行返回 `selectUnreadCount(userId)`；`SysNoticeReadMapper.xml:20-24` 的未读数统计所有 `status='0'` 公告，`:31-47` 的顶部列表 `limit #{limit}`。前端 `erp-ui/src/layout/components/HeaderNotice/index.vue:90-96` 在 `markAllRead()` 中只用 `this.noticeList.map(n => n.noticeId).join(',')` 调 `markNoticeReadAll(ids)`，随后本地 `this.unreadCount = 0`。当前 `sys_notice=0`、`sys_notice_read=0`，本轮没有写入 6 条公告做破坏性复现。
- 影响：用户会以为所有公告都已读，刷新后未读数又可能回跳，形成状态不可信；公告收件范围补齐后，这个问题还会影响门店/角色定向公告的阅读追踪。
- 建议：把“全部已读”改成后端按当前用户和可见范围直接标记所有未读正常公告，不依赖前端传入顶部 5 条 ID；或者文案改成“当前列表已读”，未读数仍以服务端返回为准，成功后重新拉取 `listTop`。

### P3-206 顶部公告只有最近 5 条快照，普通业务账号没有公告历史入口

- 现象：桌面顶部铃铛面向所有登录用户展示，但弹层只显示最近 5 条正常公告，没有“查看更多”“公告中心”或分页入口。多数门店、仓库、运营账号也没有 `/system/notice` 管理列表权限；后续一旦正常公告超过 5 条，普通业务账号只能看到最新 5 条，无法回看较早公告或定位未读历史公告。
- 证据：`Navbar.vue` 在桌面端固定挂载 `<header-notice>`，见 `erp-ui/src/layout/components/Navbar.vue:35-37`；顶部公告模板只有标题、“全部已读”、空态和 `noticeList` 循环项，没有任何历史入口或路由跳转，见 `erp-ui/src/layout/components/HeaderNotice/index.vue:3-18`。前端 `loadNoticeTop()` 只调用 `listNoticeTop()`，见 `HeaderNotice/index.vue:69-78`；后端 `SysNoticeController.listTop` 固定调用 `selectNoticeListWithReadStatus(userId, 5)`，`SysNoticeReadMapper.xml` 按 `notice_id desc limit #{limit}` 返回，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:90-102`、`erp-modules/erp-system/src/main/resources/mapper/system/SysNoticeReadMapper.xml:31-47`。当前运行态 `sys_notice=0`、`sys_notice_read=0`，未做写入型造数；角色矩阵显示 `system:notice:list/query/add/edit/remove` 只授给 `common` 和 `zjl`，其中 `common` 当前无有效用户，`zjl` 只有 `ry`，店长 `dz` 12 人、仓库管理员 `ck` 6 人、运营经理 `yyjl` 3 人、驻店经理 `zdjl` 1 人、实习生 `sxs` 1 人均没有公告列表权限。
- 影响：顶部铃铛在多账号多店铺场景下承担“所有员工接收公告”的入口，但它现在只是一个短列表快照。门店员工如果错过某条旧公告，既不能从铃铛翻页，也不能进入只读公告中心补看；未读数若大于 5，用户也无法逐条确认隐藏的未读公告。全员制度、门店 SOP、库存/合同/薪资通知这类公告会缺少可追溯阅读入口。
- 建议：为普通登录用户增加只读“公告中心/查看更多”入口，支持分页、未读筛选、类型筛选和详情阅读，权限应按公告可见范围而不是 `system:notice:list` 管理权限控制；顶部铃铛保留最近公告预览和未读数，点击更多进入只读公告历史。若产品只想保留 5 条预览，也应在未读数旁提示还有更多未读并提供可访问路径。

### P3-95 阅读用户副页面复用公告列表权限并展示手机号，权限颗粒偏粗

- 现象：公告列表行内“阅读用户”按钮和后端阅读用户接口都复用 `system:notice:list`，弹窗会展示登录名、姓名、部门、手机号和阅读时间；没有独立的“查看阅读用户/查看手机号”权限，也没有按组织范围过滤或脱敏。
- 证据：`erp-ui/src/views/system/notice/index.vue:103-109` 的“阅读用户”按钮使用 `v-hasPermi="['system:notice:list']"`；`SysNoticeController.readUsersList` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysNoticeController.java:132-143` 同样只要求 `system:notice:list`。弹窗 `erp-ui/src/views/system/notice/ReadUsers.vue:28-38` 固定展示“手机号码”，`SysNoticeReadMapper.xml:66-86` 查询 `u.phonenumber`。2026-06-24 MySQL 复核 `system:notice:list` 已授给 `common / 普通角色` 和 `zjl / 总经理`，没有单独的 `system:notice:readUsers` 权限节点。
- 影响：只要能进入公告列表，就能查看所有已读人员的联系方式和阅读轨迹；多店铺场景下，门店或普通业务角色可能通过全局公告阅读记录看到其他组织人员信息。
- 建议：拆分 `system:notice:readUsers` 或 `system:notice:readDetail` 权限，阅读用户列表按当前账号可见组织过滤；手机号默认脱敏，只给具备用户联系方式查看权限的角色显示完整号码。统计阅读人数可以保留在列表权限内，但明细和联系方式应单独授权。

### P3-100 公告删除和已读记录清理不在同一事务，失败时可能丢阅读轨迹

- 现象：删除公告时，后端先删除 `sys_notice_read` 已读记录，再删除 `sys_notice` 公告本体；这两个动作分别在 Controller 中顺序调用，服务方法没有事务包裹。若公告删除阶段失败或返回失败，已读记录已经被清掉，公告仍可能保留，阅读用户副页面和顶部未读状态会失去原始阅读轨迹。
- 证据：`SysNoticeController.remove()` 先调用 `noticeReadService.deleteByNoticeIds(noticeIds)`，再调用 `noticeService.deleteNoticeByIds(noticeIds)`；`SysNoticeReadServiceImpl.deleteByNoticeIds()`、`SysNoticeServiceImpl.deleteNoticeByIds()` 和 Controller `remove()` 都没有 `@Transactional`。`SysNoticeReadMapper.xml` 直接 `delete from sys_notice_read where notice_id in (...)`，`SysNoticeMapper.xml` 再 `delete from sys_notice where notice_id in (...)`。当前运行态 `sys_notice=0`、`sys_notice_read=0`，本轮未执行破坏性删除复现；结论来自源码流程和事务边界检查。
- 影响：公告删除是会影响全员通知和阅读审计的操作。失败时若只丢了已读记录，管理员再打开“阅读用户”会误以为没人读过；后续审计公告是否触达到用户、是否已读确认、删除前后责任追踪都会不可靠。
- 建议：把公告删除和已读清理放到同一个服务层事务中，先锁定或确认公告存在，再删除公告与已读记录；任一步失败都整体回滚。若需要保留审计，删除前应写入公告删除审计或软删除公告，让已读记录与公告删除状态能一起追溯。

### P3-111 顶部菜单搜索高亮使用 `v-html`，菜单数据一旦被绕写会变成全局渲染点

- 现象：桌面端顶部搜索弹窗为了高亮关键字，把菜单标题和路径都交给 `v-html` 渲染；高亮函数只转义搜索关键字里的正则字符，没有先把原始菜单标题/路径做 HTML 转义。当前运行库菜单名和路径没有发现可疑 HTML，且正常 `/system/menu` 写入会经过网关 JSON XSS 过滤，因此这是组件防线不足，不是已污染数据。
- 证据：`erp-ui/src/layout/components/Navbar.vue:27` 在所有桌面页引入 `<search id="header-search">`。`erp-ui/src/components/HeaderSearch/index.vue:46-47` 对 `item.title.join(' / ')` 和 `item.path` 使用 `v-html="highlightText(...)"`；`:232-241` 的 `highlightText` 仅 `escapeRegExp(this.search)` 后做 `text.replace(reg, '<span class="highlight">$1</span>')`，没有对 `text` 本身转义。菜单数据来自 `getRouters()`，`permission.js:36-47` 把后端路由写入 `defaultRoutes`，`HeaderSearch.generateRoutes` 再使用 `router.meta.title` 和 `router.path`；后端 `SysMenuServiceImpl.buildMenus` 在 `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysMenuServiceImpl.java:188-225` 直接把 `sys_menu.menu_name/path` 放入 `RouterVo/MetaVo`。`SysMenu.getMenuName/getPath` 只有必填和长度校验，没有 `@Xss`；`SysMenuController.add/edit` 的业务校验只检查名称唯一、外链 URL、父子关系和路由配置唯一。2026-06-24 MySQL 复核 `sys_menu=249`、启用 242 条，`menu_name/path` 中可疑 HTML 计数为 0；`common / 普通角色` 和 `zjl / 总经理` 仍持有 `system:menu:add/edit/list/query`。网关 `XssFilter` 对普通 JSON 写入有缓解，但组件自身仍依赖上游清洗和数据库不被导入/脚本绕写。
- 影响：顶部搜索挂在主框架所有页面，用户只要打开搜索弹窗并输入命中关键字，就可能渲染被污染的菜单标题或路径。多账号多店铺环境中，菜单由后台配置、迁移脚本和权限角色共同维护，前端组件不应把可配置文本当作可信 HTML。
- 建议：`HeaderSearch` 改成安全高亮：先把菜单标题/路径按文本转义，或拆分为文本节点和高亮 `<span>` 片段渲染，避免把完整字符串交给 `v-html`。后端 `SysMenu.menuName/path/routeName` 也应补 `@Xss` 或白名单校验；菜单导入/初始化脚本要做同样净化。若保留 `v-html`，必须限定唯一入口并配套单元测试覆盖 `<img onerror>`、`<script>`、HTML 实体和正则特殊字符。

### P3-112 菜单路由参数保存时不校验 JSON，顶部菜单、侧边栏和搜索解析失败会影响入口跳转

- 现象：菜单管理允许在页面菜单上填写“路由参数”，说明要求输入 JSON 字符串，但新增/修改保存没有校验 JSON 格式；当前桌面顶部菜单、侧边栏和顶部搜索拿到 `route.query` 后都会直接 `JSON.parse`。当前运行库只有 2 条 query，均为合法 JSON，所以不是现有脏数据；但一旦后台维护或脚本导入写入非法 query，对应菜单入口会在渲染或点击时异常。
- 证据：`sys_menu.query` 字段为 `varchar(255)`；2026-06-24 MySQL 复核 `sys_menu=249`、页面菜单 48 条、非空 query 2 条，`json_valid(query)=0` 的数量为 0，现有两条分别是 `4040 / 库存管理 / {"stockEntry":"store"}` 和 `4450 / 库存管理 / {"stockEntry":"warehouse"}`。菜单表单 `erp-ui/src/views/system/menu/index.vue:254-263` 只提供普通输入框和提示示例，表单规则 `:363-372` 只校验 `menuName/orderNum/path`，`submitForm` 在 `:475-490` 直接提交。`SysMenu.getQuery` 在 `erp-modules/erp-system/src/main/java/com/erp/system/domain/SysMenu.java:147-155` 没有校验注解，`SysMenuController.add/edit` 在 `:94-137` 只校验名称唯一、外链、父级和路由唯一，`SysMenuMapper.xml:149/173/192` 直接写入 `query`。当前 `Navbar` 在 `erp-ui/src/layout/components/Navbar.vue:63-65` 引入的是 `TopBar`，`TopBar` 在 `erp-ui/src/layout/components/TopBar/index.vue:1-8` 复用 `SidebarItem` 渲染顶部菜单；`SidebarItem.resolvePath` 在 `erp-ui/src/layout/components/Sidebar/SidebarItem.vue:116-118` 渲染路由链接时直接 `JSON.parse(routeQuery)`，所以顶部菜单和侧边栏都会走同一个解析点。顶部搜索 `HeaderSearch.change` 在 `erp-ui/src/components/HeaderSearch/index.vue:140-141` 选择结果时也直接 `JSON.parse(query)`。
- 影响：菜单 query 是动态路由能力的一部分，当前已用于区分门店库存和仓库库存入口。若管理员、总经理或初始化脚本保存了 `{stockEntry:store}`、空数组、半截 JSON 等错误值，顶部菜单点击、侧边栏渲染或顶部搜索选择都可能失败；用户看到的是菜单入口异常，而不是明确的“路由参数格式错误”。
- 建议：菜单保存前后端都校验 `query` 必须为空或合法 JSON object，并限制 key/value 类型；前端输入框提供 JSON 校验提示和格式化预览。`SidebarItem`、`HeaderSearch` 解析失败时应降级为无 query 跳转或显示可读错误，不能让单个菜单配置破坏主导航。库存这类依赖 `stockEntry` 的页面还应把允许值收敛为枚举。

### P3-113 顶部搜索打开外链缺少 `noopener`，与顶部菜单和侧边栏处理不一致

- 现象：当前桌面顶部菜单使用 `TopBar -> SidebarItem -> Link` 渲染，外链最终会生成带 `rel="noopener"` 的 `<a>`；但顶部搜索遇到 `http(s)` 菜单时仍直接 `window.open(url, "_blank")`，没有 `noopener/noreferrer`。同一个外链菜单，从顶部菜单点击和从顶部搜索选择的安全打开方式不一致。
- 证据：当前 `Navbar` 在 `erp-ui/src/layout/components/Navbar.vue:63-65` 引入 `<top-bar>`，`TopBar` 在 `erp-ui/src/layout/components/TopBar/index.vue:1-8` 复用 `SidebarItem`，`SidebarItem.vue:4-8` 通过 `AppLink` 渲染无子菜单入口；`erp-ui/src/layout/components/Sidebar/Link.vue:29-35` 对外链返回 `href`、`target='_blank'` 和 `rel='noopener'`。顶部搜索 `HeaderSearch.change` 在 `erp-ui/src/components/HeaderSearch/index.vue:135-138` 对外链执行 `window.open(p.substr(...), '_blank')`。2026-06-24 MySQL 复核当前 `sys_menu` 有 5 条 `http(s)` 外链：`ERP官网` 停用，`Sentinel控制台`、`Nacos控制台`、`Admin控制台`、`系统接口` 启用；这些外链可用性问题已分别在 P2-10、P3-08 记录，本问题只记录不同入口的打开方式不一致。
- 影响：用户从顶部搜索打开同一外链时，行为和顶部菜单/侧边栏不一致；如果后续把外链改成第三方系统、文档站或供应商后台，新窗口仍能持有 opener 关系，带来跳回原 ERP 页或篡改原窗口的风险。多门店环境里外链通常是监控、接口或运维入口，更应该保持一致的安全打开方式。
- 建议：把外链打开封装成统一 helper，所有顶部菜单、顶部搜索、侧边栏和业务附件预览都走同一逻辑；`window.open` 使用 `noopener,noreferrer` 特性并主动置空 `opener`，或统一用带 `rel="noopener noreferrer"` 的链接组件。外链菜单还应结合 P2-10/P3-08 做健康状态和环境配置提示。

### P3-114 标签页按 `path` 去重，不同 query 的业务上下文会被静默合并

- 现象：桌面端顶部标签页显示、选中、更新和关闭都把 `path` 当成唯一身份，没有把 `fullPath` 或关键 query 纳入标签身份。当前系统已有多个同一路径靠 query 表达上下文的入口，例如用户管理跳转店铺授权会带 `userId/userName`，个人中心可带 `activeTab`，代码生成返回列表会带 `pageNum/t`；这些上下文在标签栏中只能保留成一个标签。
- 证据：`erp-ui/src/settings.js:23-30` 默认开启 `tagsView`，且用户可在设置抽屉开启标签持久化。`TagsView` 模板在 `erp-ui/src/layout/components/TagsView/index.vue:10-16` 使用 `:key="tag.path"`，`isActive()` 在 `:161-163` 只比较 `route.path === this.$route.path`，`moveToCurrentTag()` 在 `:228-236` 也是先按 `tag.to.path` 找标签，再在 `fullPath` 不同时更新已有标签。`tagsView` store 的新增、删除、关闭其他、关闭左右和更新逻辑均按 path 匹配，见 `erp-ui/src/store/modules/tagsView.js:39-46`、`:62-70`、`:80-85`、`:105-153`。实际 query 场景包括：用户列表“店铺授权”在 `erp-ui/src/views/system/user/index.vue:467-475` 跳转 `/system/shop?userId=...&userName=...`，店铺授权页 `erp-ui/src/views/system/shop/index.vue:161-167` 读取 query 后过滤并聚焦目标用户；个人中心 `erp-ui/src/views/system/user/profile/index.vue:79-81` 读取 `activeTab`；代码生成列表 `erp-ui/src/views/tool/gen/index.vue:227-232` 读取 `t/pageNum`，编辑页关闭时 `erp-ui/src/views/tool/gen/editTable.vue:210-213` 带 query 返回列表；库存页 `erp-ui/src/views/inventory/stock/index.vue:417-421` 也支持从 query 读取 `stockEntry`。
- 影响：管理员不能同时打开两个用户的店铺授权标签做对照；第二次进入 `/system/shop?userId=...` 会覆盖同一个“店铺配置/店铺授权”标签的上下文。类似地，个人中心不同 tab、代码生成列表返回页码和库存入口参数也会被当作同一个标签处理。用户看到的是“标签还在”，但标签标题没有暴露当前 query 上下文，刷新、关闭、关闭其他和返回时都只能围绕最后一次写入的上下文操作，不适合多账号多店铺场景下的并行核对。
- 建议：为标签页定义统一身份策略：需要并行保留的业务上下文使用 `fullPath` 或 `path + 稳定 query 白名单` 作为 key；只是刷新用的临时 query（如 `t`）不要进入标签身份或持久化。店铺授权、用户授权、角色授权、代码生成编辑、库存入口等路由应声明是否允许多实例标签，并在标签标题中显示用户、角色、表名或库存入口等上下文。`ADD/UPDATE/DEL/active/closeLeft/closeRight/persist` 必须使用同一套身份函数，避免新增、选中和关闭口径不一致。

### P3-115 布局设置提供左侧/混合菜单，但电脑端实际始终是顶部导航

- 现象：用户菜单里的“布局设置”抽屉提供“左侧菜单 / 混合菜单 / 顶部菜单”三种导航模式，并且保存配置时会把 `navType` 写入本地 `layout-setting`；但电脑端主布局并不按 `navType` 渲染左侧菜单或混合菜单，刷新后也会强制回到 `navType=3` 顶部菜单。设置项给了用户可切换的预期，实际不闭环。
- 证据：设置抽屉 `erp-ui/src/layout/components/Settings/index.vue:6-25` 渲染三种菜单导航选项，`handleNavType()` 在 `:254-260` 会把 `navType` 写入 Vuex，`saveSetting()` 在 `:272-286` 也把 `"navType":${this.navType}` 写入 `layout-setting`。但设置 store 初始化在 `erp-ui/src/store/modules/settings.js:6-13` 读取了 `storageSetting` 后仍把 `navType` 硬编码为 `3`，没有使用 `storageSetting.navType` 或 `defaultSettings.navType`。桌面布局 `erp-ui/src/layout/index.vue:4-8` 只在 `device==='mobile'&&!sidebar.hide` 时渲染 `<sidebar>`，而 `<navbar>` 和其中的顶部菜单行在桌面端固定存在；`Navbar.vue:63-65` 对 `device!=='mobile'` 固定渲染 `<top-bar>`。`Settings` 对 `navType` 的 watcher 只切换 `sidebar.hide` 和 `SET_SIDEBAR_ROUTERS`，见 `Settings/index.vue:220-237`，但桌面端没有按该状态渲染左侧菜单。
- 影响：管理员或普通用户尝试把电脑端改成左侧菜单、混合菜单以适应更多菜单层级时，会看到设置按钮可点、保存也没有明确失败，但刷新或重新登录后仍是顶部菜单。多模块 ERP 菜单较多，顶部只显示有限入口并把其余塞进“更多菜单”，布局设置失效会降低可发现性，也会让用户误以为是自己没有保存成功。
- 建议：明确产品策略。若电脑端只支持顶部导航，应移除左侧/混合菜单选项，或置灰并说明当前版本不支持；若要支持三种布局，应让 `settings.js`、`settings` store、`Layout`、`Navbar`、`Sidebar` 和 `TopBar` 都按同一个 `navType` 渲染，并把本地保存、刷新恢复、重置、移动端断点和菜单高亮纳入回归。

### P3-116 部分导出按钮未使用统一导出确认，敏感数据外发提示不一致

- 现象：项目已经有统一导出确认工具，可以提示导出模块、导出范围、筛选条件、敏感字段和“只发送给有权限人员”的外发提醒；但不少电脑端页面仍使用简短确认，甚至定时任务页直接下载。工资、考勤、客户、供应商、库存、任务配置等导出按钮的提示口径不一致，用户无法稳定确认当前导出的范围、筛选条件和敏感字段。
- 证据：统一工具 `erp-ui/src/utils/exportConfirm.js:5-19` 会组装 `确认导出{模块}`、`导出范围`、`筛选条件`、可选 `敏感字段` 和外发提醒，`:22-34` 暴露 `confirmExportAction`。部分高风险页面已经接入，例如用户导出 `erp-ui/src/views/system/user/index.vue:528-536`、操作日志导出 `erp-ui/src/views/system/operlog/index.vue:278-290`、商品导出 `erp-ui/src/views/inventory/product/index.vue:893-901`，其中商品还会传入 `sensitiveFields`。但 OA 工资 `erp-ui/src/views/oa/salary/index.vue:233-237`、OA 考勤 `erp-ui/src/views/oa/attendance/index.vue:231-238`、客户 `erp-ui/src/views/inventory/customer/index.vue:192-194`、库存 `erp-ui/src/views/inventory/stock/index.vue:841-844`、供应商 `erp-ui/src/views/inventory/supplier/index.vue:271-273` 仍只弹“确认导出当前查询条件下...”这类简短文案，没有列出筛选条件或敏感字段；定时任务 `erp-ui/src/views/monitor/job/index.vue:469-473` 直接 `this.download('schedule/job/export', ...)`，没有任何确认。全局检索 `handleExport/this.download/confirmExportAction` 也显示导出实现同时存在统一确认、普通确认和无确认三种口径。
- 影响：导出是数据离开系统的关键按钮。工资、考勤、客户、供应商、库存、销售/采购和任务配置等文件一旦下载，就可能在线下流转；如果页面只给一句泛化确认，用户看不到当前筛选条件、日期范围、组织范围或是否含联系方式、工资、成本、调用目标等敏感字段。多账号多店铺场景下，不同页面导出提示不一致，会让管理员误以为所有导出都经过同等风险提示。
- 建议：所有电脑端导出统一走 `confirmExportAction` 或同等封装，并要求每个页面提供 `moduleName/rangeLabel/filterLabel`；涉及工资、考勤、客户联系方式、供应商联系方式、库存成本、操作日志、任务调用目标等字段时填入 `sensitiveFields`。空数据导出、全量导出和跨组织导出应有更明确的禁用或二次确认；定时任务导出至少要补确认并提示会包含调用目标字符串。

### P3-117 导入模板下载接口只要求登录，和前端导入权限口径不一致

- 现象：电脑端用户导入、商品导入页面都把“导入/下载模板”入口放在导入权限后面，但后端模板下载接口只要求登录，不要求对应导入权限。没有 `system:user:import` 或 `inv:product:import` 的账号虽然看不到按钮，仍可携带登录 token 直连模板接口下载模板。
- 证据：用户页导入按钮使用 `v-hasPermi="['system:user:import']"`，见 `erp-ui/src/views/system/user/index.vue:48`，导入弹窗配置 `template-action="/system/user/importTemplate"`，见 `:192`；后端 `SysUserController.importData` 要求 `system:user:import`，见 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java:107-110`，但 `importTemplate` 只标注 `@RequiresLogin`，见 `:119-124`。商品页“导入”和“下载模板”均使用 `inv:product:import`，见 `erp-ui/src/views/inventory/product/index.vue:77-79`，下载时请求 `inventory/product/importTemplate`，见 `:938-940`；后端商品导入数据接口要求 `inv:product:import`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvProductController.java:99-103`，但商品模板接口同样只要求 `@RequiresLogin`，见 `:125-130`。测试也把当前边界写成“至少要求登录”：`SysControllerAuthBoundaryTest.java:18-29` 断言用户模板 `RequiresLogin`，`InvControllerAuthBoundaryTest.java:14-18` 断言商品模板 `RequiresLogin`。2026-06-24 MySQL 复核当前 25 个启用账号中，24 个没有有效 `system:user:import`，17 个没有有效 `inv:product:import`。
- 影响：模板下载不直接写数据，但它是导入流程的一部分，会暴露可导入字段、字段枚举、商品成本/采购价等模板列结构，也会造成“页面按钮被权限隐藏，但接口可直连”的权限体验不一致。后续若模板中加入更多字段、示例数据或说明，未授权账号也会得到这些导入规范；排查权限问题时，管理员会看到前端和后端口径不一致。
- 建议：模板下载应和导入动作使用同一权限，用户模板用 `system:user:import`，商品模板用 `inv:product:import`；若产品决定模板可公开给所有登录用户，应在前端也提供只读入口并明确“仅模板可下载，导入需权限”，不要让权限隐藏和后端放行处于不一致状态。相关测试应改成断言模板接口要求对应权限，而不是只要求登录。

### P3-118 销售/采购退货确认框没有展示单据身份和库存影响，容易误操作

- 现象：销售退货和采购退货列表里的“确认退货”“取消”都是高影响状态动作，但确认框只显示“确认执行退货？将增加/扣减对应商品的库存。”或“确认取消该退货单？”。用户在列表连续处理多张退货单时，看不到退货单号、原单号、客户/供应商、当前门店/仓库、退货金额、商品数量或库存增减方向，无法在最后一步核对自己点的是哪一单。
- 证据：销售退货行操作在 `erp-ui/src/views/inventory/salesReturn/index.vue:45-49` 暴露详情、提交、确认退货和取消，详情弹窗本来已展示退货单号、原销售单号、客户、金额和明细，见 `:157-179`；但 `handleConfirm/handleCancel` 只弹泛化文案，见 `:359-368`。采购退货同样在 `erp-ui/src/views/inventory/purchaseReturn/index.vue:45-49` 暴露确认退货和取消，详情弹窗展示退货单号、原采购单号、供应商、金额和明细，见 `:157-179`，但确认和取消文案也只在 `:357-366` 使用泛化提示。后端确认接口分别由 `InvSalesReturnController.confirm` 和 `InvPurchaseReturnController.confirm` 调用服务层，服务层会真实增加销售退货库存、扣减采购退货库存并写 `inv_stock_log`，见 `InvSalesReturnServiceImpl.confirmReturn()` `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java:141-214`、`InvPurchaseReturnServiceImpl.confirmReturn()` `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:147-216`。2026-06-24 MySQL 复核当前正式 `inv_sales_return=0`、`inv_purchase_return=0`，但退货确认/删除权限节点已启用，当前库存和库存日志分别为 `inv_stock=2`、`inv_stock_log=5`。
- 影响：退货确认会改变库存和成本流水，取消会终止单据后续处理；泛化确认框只能防止误点一次按钮，不能帮助用户核对单据身份和业务影响。多门店、多仓库或同客户/同供应商多张退货单并行时，用户可能在错误组织或错误原单上执行确认，后续只能靠库存日志和单据审计倒查。
- 建议：退货确认和取消统一使用高风险动作确认组件，至少展示退货单号、原单号、客户/供应商、当前门店/仓库、退货总金额、商品行数、合计数量和库存影响方向；确认前可要求点击详情或在确认框中展示明细摘要。确认成功后的 toast 也应带单据号，例如“SRxxx 已退货入库 / PRxxx 已退货出库”，方便用户和日志对照。

### P3-119 库存盘点确认没有展示盈亏汇总，最后一步难以核对调整影响

- 现象：库存盘点详情弹窗能看到每个商品的账面数量、实盘数量、差异数量和差异类型，但列表点击“确认盘点”时，确认框只显示“确认盘点单 [单号] 并记录盘盈/盘亏差异？”。确认前没有汇总盘盈几项、盘亏几项、净调整数量、影响组织或关键明细，用户无法在真正写库存前判断这次盘点会增减哪些库存。
- 证据：库存盘点行操作在 `erp-ui/src/views/inventory/stockCheck/index.vue:52-56` 暴露详情、录入实盘、确认盘点、取消和删除草稿；详情弹窗在 `:131-160` 展示盘点单号、库存组织、账面数量、实盘数量、差异数量和差异类型。但 `handleConfirm()` 在 `:325-332` 只用单号拼接确认文案，确认成功 toast 也只有“确认成功”。后端 `InvStockCheckController.confirm` 调用 `InvStockCheckServiceImpl.confirmCheck()`，服务层在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java:213-325` 按每行 `diffQty` 执行盘盈加库存或盘亏扣库存，并写 `inv_stock_log`。2026-06-24 MySQL 复核当前唯一盘点单 `SC202606140001` 状态为 `cancelled`，明细为“清香铁观音”账面 `5.00`、实盘 `1.00`、差异 `-4.00/loss`、成本价 `84.0000`，库存盘点确认和取消/删除权限节点均启用。
- 影响：盘点确认是把实盘差异落到库存账的关键动作。只展示单号会让用户必须记住或另开详情核对差异，遇到多商品、多门店或盘点差异较大时，确认前缺少“本次将盘盈/盘亏什么”的最后防线。错误确认后，库存数量、成本金额和库存流水都会变化，回退成本较高。
- 建议：确认盘点前先读取详情并展示摘要：库存组织、盘点日期、商品行数、盘盈行数/数量、盘亏行数/数量、净调整数量和前几条最大差异；存在盘亏或成本影响时用更明确的风险文案。确认成功提示也应带盘点单号和调整摘要，方便用户与库存流水对账。

### P3-120 采购质检确认没有展示待检数量和入库/回退影响

- 现象：采购质检弹窗只让用户选择质检结果和备注，提交前确认框只显示采购单号、质检结果，以及“影响后续采购入库和追溯”。它没有展示当前待检批次、待检商品数量、入库仓库、合格/让步会增加多少库存、不合格会回退多少已收货数量。
- 证据：采购质检入口 `openQualityCheck()` 在 `erp-ui/src/views/inventory/purchase/index.vue:647-654` 只把 `orderId/orderNo/qcResult/qcRemark` 放进表单；`submitQualityCheck()` 在 `:655-669` 提交的也只有 `qcResult/qcRemark`。确认文案 `getQualityCheckConfirmMessage()` 在 `:683-690` 只展示采购单号和质检结果，没有读取采购详情或待检入库记录。后端 `InvPurchaseController.qualityCheck()` 要求 `inv:purchase:qc`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseController.java:87-101`；服务层 `InvPurchaseServiceImpl.qualityCheck()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:325-449` 会读取 `pending` 待检入库记录，合格/让步接收时增加库存并写 `purchase_in` 库存流水，不合格时调用 `rollbackRejectedPendingReceipts` 回退待检收货数量并更新质检结果。2026-06-24 MySQL 复核当前正式 `inv_purchase_order=0`、`inv_purchase_detail=0`、`inv_inbound_record=0`，但清理备份 `inv_inbound_record_clear_backup_20260607_1640=3`，样例包含 `PO202606060001` 数量 `1.00`、`PO202606060002` 数量 `20.00`、`PO202606070001` 数量 `29.00`，且当前 `inv:purchase:qc` 权限节点启用。
- 影响：质检是采购库存入账的真正分界点，合格/让步会把待检数量计入可用库存，不合格会撤销本批待检收货进度。只展示“质检结果”会让用户无法在最后一步确认本次影响的商品、数量和仓库；如果误点“合格”或“不合格”，库存和采购进度都会被改写，之后又缺少独立质检台账可供页面回看。
- 建议：质检确认前应读取并展示待检入库摘要：采购单号、仓库、待检批次数、商品行数、待检总数量、最大数量明细，以及本次结果对库存和已收货数量的影响。合格/让步文案应明确“将增加库存”，不合格文案应明确“将回退待检收货数量”；提交成功提示带采购单号、结果和数量摘要。

### P3-121 生成发货通知确认只显示销售单号，缺少待发摘要和客户信息

- 现象：销售列表中“生成发货通知”会把销售单推进到发货流程，但最后确认框只有“确认对销售单 [单号] 生成发货通知?”。用户看不到客户、销售金额、商品行数、总数量、已发数量、待发数量或本次将生成的发货通知数量，无法在生成前核对这是不是正确销售单。
- 证据：销售页行操作在 `erp-ui/src/views/inventory/sales/index.vue:56-60` 暴露详情、提交、生成发货通知和取消；详情弹窗已展示单号、标题、客户、金额、状态、销售日期和明细，见 `:67-102`。但 `doCreateDeliveryNotice()` 在 `:289-296` 只用 `row.orderNo` 拼确认文案，没有读取详情或使用列表已返回的 `totalQuantity/deliveredQuantity/remainingQuantity`。后端 `InvDeliveryNoticeController.create()` 要求 `inv:deliveryNotice:add`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvDeliveryNoticeController.java:49-56`；服务层 `InvDeliveryNoticeServiceImpl.createNotice()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:70-143` 会锁销售单、读取明细、按所有未出库数量生成 `inv_delivery_notice_detail`，并把销售单状态更新为 `noticed`。`InvSalesOrder` 已定义 `totalQuantity/deliveredQuantity/remainingQuantity` 导出字段，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java:33-40`。2026-06-24 MySQL 复核当前正式 `inv_sales_order=0`、`inv_sales_detail=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0`，但 `inv:deliveryNotice:add` 权限节点已启用。
- 影响：生成发货通知不直接扣库存，但它会把销售单从销售侧推进到仓库发货队列，并生成后续可执行发货的明细。多客户、多单据或部分发货场景下，只靠单号确认容易误把错误销售单推给仓库；而当前页面又不展示已发/待发进度，用户很难在同一界面判断本次通知覆盖哪些未发商品。
- 建议：生成发货通知前显示摘要确认：销售单号、客户、销售金额、商品行数、总数量、已发数量、待发数量、预计生成通知明细行数，并提示“生成后销售单状态变为已通知，需到发货通知执行出库”。确认成功提示应返回发货通知号并提供跳转详情/发货通知列表入口。

### P3-122 销售单取消缺少取消原因和业务摘要，状态审计只能记录泛化变更

- 现象：销售列表中草稿和已提交销售单都可以点击“取消”，但最后确认框只有“确认取消销售单 [单号]?”。用户看不到客户、金额、商品数量、当前状态、当前门店，也不能填写取消原因；后端取消接口只接收 `orderId`，状态审计触发器只能记录泛化 `status_update` 和原销售单备注，不能说明这次取消为什么发生。
- 证据：销售行操作在 `erp-ui/src/views/inventory/sales/index.vue:56-60` 暴露详情、提交、生成发货通知和取消，详情弹窗已有单号、标题、客户、金额、状态、销售日期和明细，见 `:67-102`；但 `doCancel()` 在 `:298-302` 只用 `row.orderNo` 拼确认文案并调用 `cancelSales(row.orderId)`。后端 `InvSalesController.cancel()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesController.java:83-90` 是 `DELETE /sales/{orderId}`，没有请求体或取消原因参数；`InvSalesServiceImpl.cancelSales()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java:392-400` 只校验可取消状态并把状态改为 `cancelled`。`InvSalesOrder` 没有 `cancelReason/cancelBy/cancelTime` 字段，`InvSalesOrderMapper.updateInvSalesOrder` 也只更新状态和更新时间，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvSalesOrder.java:15-67`、`erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesOrderMapper.xml:118-129`。运行态 MySQL 复核当前 `inv_sales_order=0`、`inv_sales_detail=0`、`inv_delivery_notice=0`、`inv_document_status_log=2`；`inv_sales_order` 只有 `remark` 列、没有 `%reason%` 字段；`trg_inv_sales_order_status_au` 和 `trg_inv_delivery_notice_status_au` 存在，但销售触发器在 `sql/erp_inventory_status_audit_20260612.sql:55-70` 写入固定 action `status_update` 和 `coalesce(NEW.remark,'')`。`sys_menu` 中 `4033 / 销售删除 / inv:sales:remove` 与 `4491 / 状态审计查询 / inv:audit:list` 均为启用按钮权限。
- 影响：取消不会直接扣减库存，但它会终止销售单后续提交、发货通知和出库流程。多账号处理同一门店销售时，如果取消没有业务原因、没有最后摘要、审计也只写“状态从 submitted 到 cancelled”，后续店长、仓库或运营只能看到结果，无法判断是客户取消、录错单、重复单、缺货、价格错误还是误操作。
- 建议：销售取消改成高风险动作弹窗，展示销售单号、客户、金额、商品行数/总数量、当前状态和门店，并要求填写取消原因；接口改为接收结构化取消请求，保存 `cancel_reason/cancel_by/cancel_time` 或写入独立业务事件。状态审计 action 应区分 `cancel`，并把取消原因带入单据详情时间线或状态审计页面。

### P3-123 发货通知部分发货后没有关闭或取消剩余数量的出口

- 现象：发货通知支持录入部分发货数量，未全部发完时会进入 `delivering / 发货中`；该状态下页面仍允许继续“执行发货”，但不再显示“取消”，后端取消接口也只允许 `pending / 待发货`。如果客户取消剩余商品、库存长期不足或业务决定不再补发，电脑端没有“关闭剩余 / 取消未发 / 终止发货”的动作，通知和销售单只能继续卡在发货中/已通知状态，或被迫继续补发。
- 证据：发货通知列表状态筛选包含 `pending/delivering/completed/cancelled`，见 `erp-ui/src/views/inventory/deliveryNotice/index.vue:14-20`；行操作中“执行发货”对 `pending` 和 `delivering` 都显示，`canDeliver()` 在 `:273-275` 返回 `row.status === "pending" || row.status === "delivering"`，而“取消”按钮只在 `scope.row.status === 'pending'` 时出现，见 `:47-48`。执行发货提交时允许只提交大于 0 的部分明细，见 `:197-224`；后端 `InvDeliveryNoticeServiceImpl.deliverNotice()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:296-309` 根据是否全部发完把通知更新为 `completed` 或 `delivering`。同一服务的 `cancelNotice()` 在 `:341-363` 调用 `InvStateGuard.requirePendingForCancel()`，而 `InvStateGuard` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStateGuard.java:84-91` 明确发货允许 `pending/delivering`、取消只允许 `pending`。全仓检索 `close/closed/cancelRemaining/取消剩余/关闭发货` 未发现发货通知关闭剩余的前端按钮、API 或后端方法；当前权限树也只有 `inv:deliveryNotice:list/query/add/deliver/remove/export`，没有 close 或 cancel-remaining 权限。2026-06-24 MySQL 复核当前正式 `inv_delivery_notice=0`、`inv_delivery_notice_detail=0`、`inv_outbound_record=0`，没有存量部分发货样例可运行复现，但该流程缺口由当前状态机和按钮条件直接决定。
- 影响：部分发货是常见业务场景，尤其在多门店、多仓库、缺货补发时更容易出现。当前系统能记录“已经发了一部分”，却没有正常业务出口处理“剩余不发了”；销售、仓库和运营看到的单据会长期处于中间态，报表和待办也难以判断这是缺货待补、客户取消、人工遗留还是异常挂单。为了把流程走完而继续发货还可能造成不必要的库存扣减。
- 建议：补齐部分发货后的终止动作，例如“关闭剩余”或“取消未发数量”，要求填写原因并展示剩余商品、数量、客户和销售单号；后端要把剩余数量、关闭原因、操作人和时间写入发货通知或业务事件，并同步重算销售单状态，例如已部分履约、已关闭或客户取消剩余。权限上应拆出 `inv:deliveryNotice:close`，不要复用普通取消权限；详情和导出要能展示已发数量、关闭剩余数量和关闭原因。

### P3-124 出库记录没有关联发货通知或通知明细，后续台账无法精确回链

- 现象：发货通知执行会写 `inv_outbound_record`，但出库记录只保存销售单、商品、组织/仓库、数量、操作人和时间，没有 `notice_id`、`notice_no`、`notice_detail_id` 或库存流水 ID。多次发货、多个通知、同一销售单同一商品分批发货时，后续无法从出库记录稳定回到具体发货通知和通知明细。
- 证据：`InvOutboundRecord` 只有 `outboundId/salesOrderId/orderNo/productId/shopDeptId/quantity/createBy/createTime/remark` 字段，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvOutboundRecord.java:6-36`；`InvOutboundRecordMapper.xml` 的查询和插入也只处理这些字段，`selectInvOutboundRecordByOrderId` 只能按销售单 ID 查，见 `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvOutboundRecordMapper.xml:5-31`。`InvDeliveryNoticeServiceImpl.deliverNotice()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:233-242` 写出库记录时只设置销售单、商品、`warehouseId` 到 `shopDeptId` 和数量；旧销售直接出库路径 `InvSalesServiceImpl.deliverSales()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java:253-262` 也写同一张表且同样没有通知关联。2026-06-24 MySQL 复核 `inv_outbound_record` 表结构只有 `outbound_id/sales_order_id/order_no/product_id/shop_dept_id/quantity/create_by/create_time/remark`，当前 `inv_outbound_record=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0`、销售出库库存流水为 0。
- 影响：即使后续修复 P3-86，把出库记录展示到发货通知详情或导出里，也只能按销售单、商品和时间推测关联；部分发货、补发、多通知拆分或同仓多次操作时，仓库、门店、销售和财务无法证明某一条出库来自哪个发货通知、哪个通知明细，影响对账、追责和审计闭环。
- 建议：`inv_outbound_record` 增加发货通知级关联键，例如 `notice_id`、`notice_no`、`notice_detail_id`，并补充 `warehouse_id` 与 `stock_log_id` 或业务来源字段；发货通知执行时写入这些字段，旧销售直接出库路径也要明确来源类型。详情、导出、库存流水和状态审计应基于同一关联链展示，避免只靠销售单号推断。

### P3-125 销售发货库存流水只记录销售单号，无法回到具体发货通知

- 现象：库存流水页面和导出可以看到销售出库的通用业务单号，但发货通知执行写入 `inv_stock_log` 时仍只保存销售单 ID 和销售单号，不保存发货通知号、通知明细、出库记录 ID 或来源明细行。库存账本能说明“某销售单出库了”，但不能说明“哪张发货通知、哪条通知明细触发了这次库存扣减”。
- 证据：库存流水页面 `erp-ui/src/views/inventory/stock/log.vue:52-95` 展示商品、库存组织、批次/效期/序列号/库位、变动类型、业务单号、数量、成本价、备注和时间，没有发货通知号、通知明细或业务详情入口；导出调用 `inventory/stock/log/export`，见 `:164-167`。后端 `InvStockController.exportLog()` 用 `ExcelUtil<InvStockLog>` 导出库存流水，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvStockController.java:87-94`；`InvStockLog` 只有通用 `businessType/businessId/businessNo` 和备注字段，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockLog.java:52-82`。`InvDeliveryNoticeServiceImpl.deliverNotice()` 写库存流水时 `businessType="sales"`、`businessId=notice.getSalesOrderId()`、`businessNo=notice.getSalesOrderNo()`、备注为“销售出库-发货通知”，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvDeliveryNoticeServiceImpl.java:255-271`；旧销售直接出库路径 `InvSalesServiceImpl.deliverSales()` 写 `businessType="outbound"`、`businessId=orderId`、`businessNo=order.getOrderNo()`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java:275-291`。2026-06-24 MySQL 复核 `inv_stock_log` 表也只有 `business_id/business_no/remark` 等通用列，没有 notice/outbound/detail 关联列；当前库存流水 5 条均为调整或调拨，销售类流水为 0，暂无运行样例可回放。
- 影响：库存流水是仓库、门店、财务核对库存数量和成本的核心账本。销售单被拆成多张发货通知、部分发货、补发或同一商品多次发货时，库存流水只能按销售单号和备注模糊定位；如果销售单与发货通知详情、出库记录、库存流水三边出现差异，无法从库存账本直接追到造成扣减的通知明细。
- 建议：销售发货写库存流水时同时保存 `notice_id`、`notice_no`、`notice_detail_id`、`outbound_id` 或统一 `source_type/source_id/source_detail_id`；库存流水页面和导出增加来源单据列与详情跳转。直销出库、发货通知出库、调拨出库等来源要用可枚举来源类型区分，避免只靠销售单号和备注判断。

### P3-126 采购退货确认扣库存后没有出库台账或库存流水入口

- 现象：采购退货确认会扣减仓库库存并写 `inv_stock_log`，但采购退货详情只展示退货单头和明细累计数量，导出也只导出退货单头汇总字段；页面没有展示本次确认实际扣减了哪些库存流水，也没有独立采购退货出库记录表。用户只能看到退货单状态变成已退货，不能在采购退货详情里核对库存账本。
- 证据：采购退货详情弹窗 `erp-ui/src/views/inventory/purchaseReturn/index.vue:157-179` 只展示退货单号、原采购单号、供应商、金额、状态、申请人和退货明细，明细只有 `数量/已退/单价/金额`；确认按钮在 `:48`，确认后调用 `confirmPurchaseReturn(row.returnId)`，见 `:357-360`。后端 `InvPurchaseReturnServiceImpl.confirmReturn()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:170-204` 扣减库存并写 `InvStockLog`，`businessType="purchase_return"`、`businessId=returnId`、`businessNo=returnNo`、备注“采购退货出库”；详情服务 `getReturnDetail()` 只挂载 `purchaseReturnDetailMapper.selectInvPurchaseReturnDetailByReturnId(returnId)`，见 `:119-125`，明细 mapper 也只查 `inv_purchase_return_detail` 的商品、数量、单价、金额、已退数量，见 `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvPurchaseReturnDetailMapper.xml:20-31`。`InvPurchaseReturnController.export()` 只用 `ExcelUtil<InvPurchaseReturn>` 导出单头，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseReturnController.java:96-103`；MySQL 复核当前没有 `inv_purchase_return_outbound_record` 等退货出库记录表，正式 `inv_purchase_return=0`、`inv_purchase_return_detail=0`、采购退货库存流水为 0。
- 影响：采购退货会真实减少仓库库存和成本金额，是仓库、采购、财务对供应商退货的关键凭证。当前详情无法直接证明某次确认产生了哪些库存流水、扣了哪些商品和仓库库存；多次编辑、部分退货或后续补退时，只能到库存流水页按业务单号另查，详情和导出不能自洽。
- 建议：采购退货详情增加“库存扣减/出库流水”副表，展示库存流水 ID、商品、仓库、数量、成本价、操作人、时间和备注；导出应支持明细/流水级导出。若不新增独立出库记录表，至少要在 `inv_stock_log` 中保存可回跳的 `source_type=purchase_return/source_id/source_detail_id`，并在采购退货详情按这些字段加载库存流水。

### P3-127 销售退货确认入库后没有入库台账或库存流水入口

- 现象：销售退货确认会把商品加回门店库存并写 `inv_stock_log`，但销售退货详情只展示退货单头和明细累计数量，导出也只导出退货单头汇总字段；页面没有展示本次确认实际产生的库存流水，也没有销售退货入库记录表。用户只能看到退货单状态变成已退货，不能在销售退货详情里核对入库账本。
- 证据：销售退货详情弹窗 `erp-ui/src/views/inventory/salesReturn/index.vue:157-179` 只展示退货单号、原销售单号、客户、金额、状态、申请人和退货明细，明细只有 `数量/已退/单价/金额`；确认按钮在 `:48`，确认后调用 `confirmSalesReturn(row.returnId)`，见 `:359-362`。后端 `InvSalesReturnServiceImpl.confirmReturn()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java:161-202` 新增或增加库存并写 `InvStockLog`，`businessType="sales_return"`、`businessId=returnId`、`businessNo=returnNo`、备注“销售退货入库”；详情服务 `getReturnDetail()` 只挂载 `salesReturnDetailMapper.selectInvSalesReturnDetailByReturnId(returnId)`，见 `:117-121`，明细 mapper 也只查 `inv_sales_return_detail` 的商品、数量、单价、金额、已退数量，见 `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesReturnDetailMapper.xml:20-31`。`InvSalesReturnController.export()` 只用 `ExcelUtil<InvSalesReturn>` 导出单头，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvSalesReturnController.java:96-103`；MySQL 复核当前没有销售退货专用入库记录表，通用 `inv_inbound_record` 只在采购收货/质检链路使用，正式 `inv_sales_return=0`、`inv_sales_return_detail=0`、销售退货库存流水为 0。
- 影响：销售退货入库会改变门店可售库存和成本，是门店、客服、仓库和财务核对售后退货的关键凭证。当前详情无法直接证明哪次确认产生了哪些库存流水、入了哪些商品和门店库存；如果退货后库存数量或成本异常，业务人员需要离开单据到库存流水页手工按退货单号查，流程断裂。
- 建议：销售退货详情增加“退货入库/库存流水”副表，展示库存流水 ID、商品、门店/仓库、数量、成本价、操作人、时间和备注；导出支持明细/流水级导出。若不新增独立入库记录表，至少在 `inv_stock_log` 中保存可回跳的 `source_type=sales_return/source_id/source_detail_id`，并在销售退货详情按这些字段加载库存流水。

### P3-128 采购质检入库库存流水只记录采购单号，无法回到待检入库记录

- 现象：采购收货会按采购明细生成待检入库记录，质检合格或让步接收后才增加库存并写库存流水；但这条库存流水只记录采购单 ID 和采购单号，不记录本次质检对应的 `inbound_id`、采购明细 ID、质检结果或收货批次。采购单多次收货、分批质检或同商品多批入库时，库存账只能按采购单号模糊定位，不能回到具体待检入库记录。
- 证据：`InvInboundRecord` 字段包含 `inboundId/purchaseOrderId/purchaseDetailId/orderNo/productId/shopDeptId/warehouseId/quantity/qcResult/qcUser/qcTime/qcRemark`，mapper 也支持按采购单读取待检记录；`InvPurchaseServiceImpl.receivePurchase()` 在收货时插入 `InvInboundRecord` 并设置 `purchaseDetailId`、`warehouseId`、`quantity` 和 `qcResult=pending`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseServiceImpl.java:288-300`。`qualityCheck()` 读取待检记录后在合格/让步分支写 `InvStockLog`，但只设置 `movementType=purchase_in`、`businessType="purchase"`、`businessId=orderId`、`businessNo=order.getOrderNo()`、数量和备注，见 `:342-399`。`InvStockLog` 与 `InvStockLogMapper.xml` 只映射 `business_type/business_id/business_no` 等通用字段，没有 `inbound_id/source_detail_id/qc_result`；MySQL 复核当前 `inv_inbound_record=0`，采购入库库存流水为 0，表结构同样没有回链字段。
- 影响：采购单、待检入库、质检结果和最终库存流水不能形成一条稳定凭证链。仓库或财务核对分批到货、分批质检、供应商争议和库存异常时，无法从某条库存增加直接证明它来自哪条待检记录和哪次质检。
- 建议：采购质检入库写库存流水时保存 `inbound_id`、`purchase_detail_id`、`qc_result`，或统一使用 `source_type=purchase_inbound/source_id/source_detail_id`；采购单详情、待检入库台账和库存流水页面都应能沿同一关联链互相回跳。

### P3-129 库存盘点确认库存流水只挂盘点单，无法回到盘点明细行

- 现象：库存盘点明细表有独立 `detail_id`，盘点确认时也是逐明细行计算盘盈/盘亏并写库存流水；但写出的库存流水只保存盘点单 ID 和盘点单号，不保存 `check_detail_id`、原账面数、实盘数或差异类型。多商品盘点完成后，库存流水只能回到整张盘点单，不能精确回到产生该流水的盘点明细行。
- 证据：`InvStockCheckDetail` 和 `InvStockCheckDetailMapper.xml` 映射 `detail_id/check_id/product_id/book_qty/actual_qty/diff_qty/diff_type/cost_price`；`InvStockCheckServiceImpl.confirmCheck()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java:229-318` 遍历每条盘点明细，按 `diffQty` 调整库存并写 `InvStockLog`，但只设置 `businessType="stock_check"`、`businessId=checkId`、`businessNo=check.getCheckNo()`、数量和备注，没有写明细 ID。MySQL 复核当前 `inv_stock_check_detail` 存在样例 `detail_id=2/check_no=SC202606140001/book_qty=5/actual_qty=1/diff_qty=-4`，`inv_stock_log` 表结构没有 `check_detail_id/source_detail_id/book_qty/actual_qty/diff_type` 字段，当前正式盘点流水为 0。
- 影响：盘点是库存账和实物账对齐的关键凭证。后续仓库、店铺或财务查看某条盘亏/盘盈流水时，无法直接定位到盘点明细里的账面数、实盘数和差异类型；多行盘点、同商品多批次盘点或盘点争议时，只能人工按商品和数量反查，审计链不稳定。
- 建议：盘点确认写库存流水时保存 `check_detail_id`、`book_qty`、`actual_qty`、`diff_type`，或统一使用 `source_type=stock_check/source_id/source_detail_id`；库存流水详情、盘点详情和导出应支持从流水回跳到具体盘点明细。

### P3-130 手工库存调整没有独立调整单号，库存流水业务号为空

- 现象：库存调整弹窗提示“提交后会立即生成库存流水，不可直接撤回”，但手工调整没有独立调整单或调整编号，后端写 `inv_stock_log` 时 `businessType="stock"`，没有设置 `businessId` 和 `businessNo`。库存流水里只能看到调整数量、操作人、时间和备注原因，无法按调整单号汇总、审批、复核或回跳到一张调整凭证。
- 证据：前端 `stock/index.vue` 的确认文案只展示商品、库存组织、调整前后数量和原因，见 `erp-ui/src/views/inventory/stock/index.vue:786-799`；请求 DTO `InvStockAdjustRequest` 只有商品、组织、批次/效期/序列号/库位、调整数量和原因，没有调整单号、原因类型或附件字段。`InvStockServiceImpl.adjustStock()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockServiceImpl.java:167-181` 写库存流水时设置 `movementType=adjustment`、`businessType="stock"`、数量和备注，但不设置 `businessId/businessNo`。MySQL 当前已有两条手工调整流水 `log_id=7/8`，`movement_type=adjustment`、`business_type=stock`、`business_id=NULL`、`business_no=NULL`，备注均为 `1`。
- 影响：手工库存调整直接改变库存数量和成本，是高风险操作。没有调整单号和结构化凭证时，管理者只能在流水里按时间和备注筛选，无法形成“申请-确认-流水-导出”的闭环；多账号多店铺场景下，调整原因也难以统一分类和追责。
- 建议：新增库存调整单或至少生成调整编号，库存流水写入 `businessId/businessNo` 并保存调整原因类型、说明、附件和操作上下文；库存调整页面应提供调整记录列表、详情、导出和从流水回跳到调整凭证的入口。

### P3-131 调拨出入库库存流水只记录调拨单号，无法回到发货批次和批次明细

- 现象：调拨发货会生成 `TS` 发货批次和批次明细，收货也按发货批次整批入库；但调拨出库和调拨入库写入 `inv_stock_log` 时只保存调拨单 ID 和调拨单号，不保存 `shipment_id`、`shipment_no`、`shipment_detail_id` 或 `transfer_detail_id`。库存流水页面只能看到 `TF...` 业务单号和备注，无法直接定位到哪一批 `TS...` 发货/收货造成了这条库存变动。
- 证据：`InvTransferServiceImpl.deliverTransfer()` 在 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java:291-362` 先创建 `InvTransferShipment` 和 `InvTransferShipmentDetail`，但写 `transfer_out` 库存流水时只调用 `writeStockLog(..., businessType="transfer", businessId=locked.getTransferId(), businessNo=locked.getOrderNo(), ...)`，见 `:334-337`。`receiveTransferShipment()` 在 `:475-505` 按 `InvTransferShipmentDetail` 收货后调用 `addTargetStockAndLog()`，该方法最终写 `transfer_in/cross_store_transfer` 流水时同样只传 `order.getTransferId()` 和 `order.getOrderNo()`，见 `:821-866`。`InvStockLogMapper.xml` 只查询和插入 `business_type/business_id/business_no` 等通用字段，没有批次或明细来源列。MySQL 复核当前 `TF202606180001` 对应 `TS202606180001`，`TF202606140001` 对应待收货 `TS202606180002`；库存流水 `log_id=9/10/11` 的 `business_no` 均为 `TF...`，没有 `TS...` 或明细 ID。
- 影响：调拨是多仓库、多门店之间的真实库存流转。部分发货、多批次发货或一张调拨单多商品时，库存流水不能直接证明某次仓库扣减或门店入库来自哪个发货批次和哪条批次明细；仓库、门店和财务对账时必须人工按时间、商品和数量反查，容易错配。
- 建议：调拨出库和入库写库存流水时保存 `shipment_id`、`shipment_no`、`shipment_detail_id`、`transfer_detail_id`，或统一使用 `source_type=transfer_shipment/source_id/source_detail_id`；库存流水、调拨详情和调拨记录导出都应支持从流水回跳到具体发货/收货批次。

### P3-132 报表中心没有导出或明细钻取，汇总指标无法自助核对

- 现象：报表中心只提供查询、重置和低库存预警刷新，页面展示汇总指标和低库存预警表，但没有导出按钮，也不能从“当前库存、库存成本、采购金额、销售金额、预估毛利、低库存行”钻取到对应库存、采购单、销售单或库存流水明细。用户看到汇总数字后，必须离开报表中心到多个业务页面手工对账。
- 证据：`erp-ui/src/views/inventory/report/index.vue` 只 import `getReportSummary/listStockWarning`，页面按钮只有查询、重置和预警刷新，见 `:34-35`、`:81`、`:129`；同文件没有 `download`、`handleExport`、`router.push` 或明细链接。前端 API `erp-ui/src/api/inventory/report.js` 只有 `/inventory/report/summary` 和 `/inventory/report/stock-warning` 两个 GET；后端 `InvReportController` 也只提供 `summary` 与 `stockWarning`，没有导出、趋势或明细接口。MySQL 复核当前权限树只有 `4470 报表中心 / inv:report:list` 和 `4471 报表查询 / inv:report:list`，没有 `inv:report:export`、`inv:report:detail` 或类似权限；运行态已有库存 2 条、库存流水 5 条、调拨状态流水 6 条，但报表中心不能从汇总直接跳到这些明细。
- 影响：报表是管理者核对多店铺库存和经营数据的入口。当前汇总数一旦异常，用户没有页面内路径确认是哪条库存、单据或流水造成差异；低库存列表也不能导出给采购或门店补货跟进，导致报表更像只读看板，难以承担审计、复盘和日常运营分发。
- 建议：为报表中心补导出和明细钻取：汇总卡片可跳转到带相同筛选条件的库存、采购、销售或库存流水页面，低库存预警支持导出；权限上拆出 `inv:report:export` 和必要的明细查看权限，并沿用 `inv:cost:view` 控制成本字段。

### P3-133 单据状态审计接口只查通用状态表，调拨状态流水被排除在外

- 现象：权限树里有“状态审计查询” `inv:audit:list`，后端也有 `/audit/list`，但该接口只查询 `inv_document_status_log`。调拨流程的提交、审批、发货和收货状态写在独立 `inv_transfer_status_log`，不会出现在通用状态审计接口里。即使后续补上 P2-44/P2-91 的前端入口，当前接口也无法形成全单据状态审计。
- 证据：`InvDocumentStatusLogController.list()` 只调用 `documentStatusLogService.selectDocumentStatusLogList`，`InvDocumentStatusLogServiceImpl` 最终走 `InvDocumentStatusLogMapper.selectInvDocumentStatusLogList`；该 mapper 的 SQL 只从 `inv_document_status_log` 查询。调拨侧 `InvTransferStatusLogMapper.xml` 只提供 `insertLog` 写入 `inv_transfer_status_log`，`InvTransferServiceImpl.submitTransfer/deliverTransfer/receiveTransferShipment/writeStatusLog`、`InvTransferApprovalServiceImpl.writeStatusLog` 和销售自动调拨 `InvSalesServiceImpl.writeTransferStatusLog` 都会写这张调拨专用表。MySQL 当前 `inv_document_status_log=2`，两条均为盘点单 `SC202606140001`；`inv_transfer_status_log=6`，包含 `auto_approve/submit/approve/deliver/receive`，但 `rg` 未发现前端或通用审计接口读取调拨状态表。权限树同时存在 `4491 状态审计查询 inv:audit:list` 和 `4402 调拨审批轨迹 inv:transfer:approval:track`，两者都不是可直接展示完整流水的页面入口。
- 影响：全局状态审计和调拨状态审计会继续分裂；管理员以为“状态审计查询”覆盖采购、销售、盘点和调拨，但调拨关键状态不会出现在通用审计结果里。跨店铺调拨责任链需要到调拨详情或数据库另查，和 P2-14 的页面缺口叠加后，调拨审批、发货、收货的审计闭环仍不完整。
- 建议：统一状态审计模型：要么将调拨状态同步写入 `inv_document_status_log`，要么让 `/audit/list` 聚合 `inv_transfer_status_log` 并标准化 `document_type=transfer`、组织范围、操作人、状态、批次号和原因字段；前端状态审计页和调拨详情时间线应共用同一来源。

### P3-134 采购退货和销售退货取消缺少取消原因，状态审计只能留下泛化变更

- 现象：采购退货和销售退货的草稿/已提交单都可以取消，但取消动作只弹“确认取消该退货单？”并调用 `DELETE /purchaseReturn/{returnId}` 或 `DELETE /salesReturn/{returnId}`。用户不能填写取消原因，后端也没有 `cancel_reason/cancel_time` 或独立业务事件，状态审计触发器最终只能记录 `status_update` 和单据原备注，无法说明退货为什么被终止。
- 证据：采购退货页面 `handleCancel()` 与销售退货页面 `handleCancel()` 分别在 `erp-ui/src/views/inventory/purchaseReturn/index.vue:363-367`、`erp-ui/src/views/inventory/salesReturn/index.vue:365-369` 使用固定确认文案并只传 `returnId`；两个 API 的取消函数都是 DELETE 无请求体。后端 `InvPurchaseReturnController.remove()`、`InvSalesReturnController.remove()` 调用 `cancelReturn(returnId, selectedShopDeptId)`；服务层 `InvPurchaseReturnServiceImpl.cancelReturn()` 和 `InvSalesReturnServiceImpl.cancelReturn()` 都只校验状态并把 `status` 改为 `cancelled`、设置 `updateBy`。`InvPurchaseReturn`、`InvSalesReturn` 实体和 mapper 只有 `remark`，运行态 `inv_purchase_return/inv_sales_return` 均无 `%cancel%` 或 `%reason%` 字段。MySQL 当前两个退货表正式数据为 0，退货状态触发器存在，但 `trg_inv_purchase_return_status_au`、`trg_inv_sales_return_status_au` 固定写 `action='status_update'`，备注取 `NEW.remark`，当前 `inv_document_status_log` 中也没有采购退货或销售退货样例。
- 影响：退货取消会终止后续入库/出库和财务对账，原因可能是客户撤回、供应商拒收、录错单、库存不足或组织选错。当前只能看到状态从草稿/已提交变成已取消，审计人员无法区分业务原因和误操作；多账号、多门店并行处理退货时，问题单据只能靠操作日志和人工沟通补充背景。
- 建议：退货取消改为结构化动作：取消确认框展示退货单号、原单号、客户/供应商、组织、金额和数量，并要求填写取消原因；接口改为接收取消请求，保存 `cancel_reason/cancel_by/cancel_time` 或写入退货状态事件。状态审计 action 应区分 `cancel`，并把取消原因展示在退货详情时间线和状态审计页面。

### P3-135 采购退货和销售退货备注新增可写但编辑不更新，详情和导出也不可见

- 现象：采购退货和销售退货新增/编辑表单都有“备注”文本域。新增退货单时新增 SQL 会写入 `remark`，但编辑已有草稿再保存或提交时，两个退货更新 SQL 都不会更新 `remark`；同时退货详情弹窗不展示备注，导出也不会导出备注字段。用户录入或补充的退货说明、异常背景、供应商/客户沟通信息，既可能编辑不落库，也无法在电脑端详情和导出台账中正常看见。
- 证据：两个退货页面表单都包含 `v-model="form.remark"`，见 `erp-ui/src/views/inventory/purchaseReturn/index.vue:101-102`、`erp-ui/src/views/inventory/salesReturn/index.vue:101-102`；保存/提交会分别走 `buildPayload()` 后调用 `savePurchaseReturn/submitPurchaseReturn`、`saveSalesReturn/submitSalesReturn`，见采购退货 `:307-324`、销售退货 `:309-326`。服务层编辑草稿分支分别在 `InvPurchaseReturnServiceImpl.saveDraft()` 的 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvPurchaseReturnServiceImpl.java:77-100` 和 `InvSalesReturnServiceImpl.saveDraft()` 的 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java:76-99` 调用更新 mapper。两个 Mapper 的新增 SQL `InvPurchaseReturnMapper.xml:91-102`、`InvSalesReturnMapper.xml:93-104` 都插入 `remark`，但更新 SQL `InvPurchaseReturnMapper.xml:105-115`、`InvSalesReturnMapper.xml:107-117` 只更新标题、客户/供应商、金额、日期、状态和更新时间，没有 `remark = #{remark}`。两个详情弹窗 `erp-ui/src/views/inventory/purchaseReturn/index.vue:157-179`、`erp-ui/src/views/inventory/salesReturn/index.vue:157-179` 也只展示单号、原单号、客户/供应商、金额、状态、申请人、创建时间和明细，没有 `detailForm.remark`。导出接口仍使用 `ExcelUtil<InvPurchaseReturn>` 和 `ExcelUtil<InvSalesReturn>`，而 `BaseEntity.remark` 没有 `@Excel` 注解。MySQL 当前正式退货表 `inv_purchase_return=0`、`inv_sales_return=0`，暂无存量备注样例。
- 影响：备注是退货流程里解释异常、沟通背景和处理约定的低成本字段。当前新增后如果再补充或修正备注会出现“保存成功但备注不变”；即使新增备注已经落库，业务人员在详情页和导出表里也看不到，后续出现争议时，导出的退货台账缺少业务说明。
- 建议：退货更新 SQL 必须补充 `remark = #{remark}`，并用新增草稿、编辑备注、提交、重新打开详情四步做回归；详情弹窗增加备注展示，导出字段补充备注列。如果备注用于取消原因或异常说明，应拆分为结构化原因字段，避免把创建备注、取消原因和处理意见混在同一个不可见文本里。

### P3-136 库存流水列表已返回商品信息，前端仍逐商品请求详情

- 现象：库存流水后端列表已经联表返回 `product_code/product_name`，前端表格也优先使用行数据里的商品编码和名称；但页面加载后仍会把当前页每个未缓存的 `productId` 拿去调用商品详情接口。这个额外请求对列表展示不是必需的，且会把库存流水页和商品详情权限、商品详情成本字段响应绑定在一起。
- 证据：`InvStockLogMapper.xml` 在 `selectInvStockLogList` 中 `left join inv_product p` 并返回 `p.product_code`、`p.product_name`，`InvStockLog` 也有 `productCode/productName` 字段；库存流水表格的 `productCode(row)`、`productName(row)` 会先读 `row.productCode/row.productName`。但 `erp-ui/src/views/inventory/stock/log.vue:153-159` 每次 `getList()` 后都会调用 `hydrateProducts(this.list)`，`hydrateProducts()` 在 `:180-188` 对当前页唯一 `productId` 调用 `getProduct(productId)`。当前运行态库存流水 5 条都已能由列表 SQL 返回商品信息；角色授权里驻店经理持有 `inv:stock:log` 但没有 `inv:product:query`，这些额外商品详情请求会失败后被静默忽略；其他拥有商品查询但无成本权限的角色则会触发 P1-26 已记录的商品详情成本字段返回。
- 影响：库存流水页会产生不必要的 N+1 商品详情请求，数据量增加时拖慢列表并放大接口压力；权限上也让“能看库存流水”隐式依赖“能查商品详情”，或者在有商品详情权限时额外暴露商品详情响应。列表所需商品名/编码已经在库存流水接口中具备，继续补查详情会让页面行为和后端数据契约不清晰。
- 建议：库存流水列表直接使用 `InvStockLogMapper` 返回的 `productCode/productName`，移除逐行 `getProduct` 补查；若确实需要商品详情，应增加显式详情入口并按 `inv:product:query`、`inv:cost:view` 做权限和字段脱敏。

### P3-137 库存流水提供批次和库位筛选，但多数业务流水不会写这些字段

- 现象：库存流水页面提供批次、效期、序列号、库位编码和库位名称筛选，并在表格中展示这些列；但采购入库、销售出库、采购退货、销售退货、调拨出入库和库存盘点确认写库存流水时都没有设置这些字段。用户按批次或库位筛选库存流水时，只能查到极少数手工调整写入的元数据，不能覆盖主要出入库业务。
- 证据：前端 `erp-ui/src/views/inventory/stock/log.vue:13-32` 提供批次/效期/序列号/库位筛选，`:62-72` 展示对应列；`InvStockLogMapper.xml` 也支持 `batch_no/expiry_date/serial_no/location_code/location_name` 条件。写入侧只有 `InvStockServiceImpl.adjustStock()` 调用 `applyStockLogMetadata()`，把调整请求或当前库存上的批次/效期/库位复制到日志；`InvPurchaseServiceImpl.qualityCheck()`、`InvSalesServiceImpl.deliverSales()`、`InvDeliveryNoticeServiceImpl.deliverNotice()`、`InvPurchaseReturnServiceImpl.confirmReturn()`、`InvSalesReturnServiceImpl.confirmReturn()`、`InvTransferServiceImpl.writeStockLog()`、`InvStockCheckServiceImpl.confirmCheck()` 写 `InvStockLog` 时均没有设置批次、效期、序列号或库位。MySQL 当前 `inv_stock_log` 5 条中 `batch_no/serial_no/location_code/location_name` 非空数均为 0，`expiry_date` 只有 1 条，3 条调拨流水和 1 条手工调整没有任何批次/库位信息。
- 影响：页面看起来支持按批次和库位追溯库存变动，但关键采购、销售、退货、调拨、盘点流水实际不会进入这些筛选维度。仓库或门店按批次、序列号、库位追责时，可能误以为没有流水；库存页字段、库存流水字段和各业务写入逻辑没有形成一致的数据契约。
- 建议：统一库存流水来源字段规范：所有会改变库存的服务都应从当前库存、出入库明细、调拨批次或盘点明细带入批次、效期、序列号和库位；如果当前业务不支持这些维度，应在库存流水页隐藏或标注筛选限制，避免把空字段当成可用追溯能力。

### P3-138 库存状态和扣减校验按可用库存计算，但列表、详情和调整确认不展示可用/锁定数量

- 现象：库存主页面的状态标签按 `availableQuantity` 判断“缺货 / 低库存 / 正常”，后端库存调整扣减也会校验调整后的可用库存不能为负；但库存表和详情弹窗只展示“当前库存”和状态，没有行级“锁定库存 / 可用库存”。调整弹窗的“调整前 / 调整后”也按当前库存预览，用户看不到本次负数调整是否会被锁定库存拦截。
- 证据：库存列表只渲染商品、批次/库位、`当前库存` 和 `状态`，见 `erp-ui/src/views/inventory/stock/index.vue:184-219`；详情弹窗只展示 `当前库存`、状态、最后入库和最后出库，见 `erp-ui/src/views/inventory/stock/index.vue:293-310`。同文件 `stockStatusLabel()` 在 `:945-950` 用 `availableQuantityValue(row)` 判定缺货/低库存，`adjustBeforeQuantity/adjustAfterQuantity` 在 `:497-507` 只用 `currentQuantity` 预览。后端 `InvStockServiceImpl.adjustStock()` 在 `:118-130` 同时读取 `currentQuantity` 和 `availableQuantity`，并在 `newAvailableQty < 0` 时抛出“可用库存不足”。数据库 `inv_stock` 已有 `locked_quantity`、`available_quantity` 字段，导出实体 `InvStock` 也给“锁定库存 / 可用库存”加了 `@Excel`，当前运行态两条库存样例虽锁定量为 0，但字段契约已经存在。
- 影响：一旦销售发货、调拨或其他流程锁定库存，用户可能看到当前库存仍为正数，但状态显示缺货或负数调整被后端拒绝，页面却没有解释是哪部分被锁定。多账号协作时，门店、仓库和管理层难以区分“账面有货”“可发有货”和“已被其他单据占用”，库存状态、调整确认和导出台账口径不一致。
- 建议：库存列表、详情和调整确认框补充当前库存、锁定库存、可用库存三列或三项；状态 tooltip 说明按可用库存和安全库存判断。负数库存调整预览应同步展示调整后可用库存，并在可用库存不足时前端提前禁用或给出明确原因；导出字段和页面字段保持一致。

### P3-139 固定资产年度申报比例是店铺年度额度字段，却藏在资产明细保存表单里

- 现象：固定资产配置页的“年度申报比例”显示在额度卡片里，数据库也存放在 `oa_fixed_asset_quota` 的店铺+年份记录上；但修改入口放在“新增/编辑固定资产”弹窗内，和单条资产明细一起保存。用户看起来是在维护某个资产商品，实际会重算并覆盖该店铺当年的年度维修额度比例。
- 证据：前端额度卡展示 `quota.annualRepairRatio`，新增/编辑固定资产弹窗也绑定 `form.annualRepairRatio`，见 `erp-ui/src/views/oa/fixedAsset/config/index.vue:51-52`、`:134-135`、`:287-296`。后端 `OaFixedAssetConfig` 有 `annualRepairRatio` 字段，但 `oa_fixed_asset_config` 表和 `OaFixedAssetConfigMapper.xml` 不保存/查询该字段；运行态表结构显示比例实际在 `oa_fixed_asset_quota.annual_repair_ratio`，且唯一键是 `(shop_dept_id, quota_year)`。`OaFixedAssetServiceImpl.saveConfig()` 保存任一资产配置后调用 `rebuildQuota(shopDeptId, currentYear(), config.getAnnualRepairRatio())`，`rebuildQuota()` 会把该比例写入或更新额度表。
- 影响：年度申报比例是店铺年度额度策略，不是资产明细属性。把它放在资产新增/编辑表单里，会让管理员在调整数量、价格或状态时顺手改动整店额度比例；反过来，想单独调整年度比例也必须借助某条资产保存。多店铺场景下，额度策略、资产明细和维修额度计算的责任边界不清晰。
- 建议：把年度申报比例从资产明细表单中拆出来，做成店铺+年度额度配置；资产配置只维护资产数量、单价、状态和备注。额度比例调整应有单独权限、确认文案、变更原因和历史记录；保存资产明细时只重算资产总额，不隐式修改年度比例。

### P3-140 固定资产额度流水只用于汇总计算，电脑端没有明细查询或导出

- 现象：固定资产页面展示“已用额度、未来已透支额度、当前可用额度”等汇总数字，但没有额度流水列表、钻取、详情或导出。`oa_fixed_asset_quota_ledger` 记录哪张维修单占用了多少额度、占用类型和创建人，电脑端只能看到汇总后的金额，无法核对每一笔额度占用来自哪里。
- 证据：运行态库有 `oa_fixed_asset_quota_ledger`，字段包括 `repair_id/shop_dept_id/quota_year/movement_type/amount/create_by/create_time/remark`；`OaFixedAssetQuotaLedgerMapper.xml` 只有 `sumUsedQuotaAmount` 和 `insertLedger`，接口 `OaFixedAssetConfigController`、`OaFixedAssetRepairController` 只提供配置列表、维修列表、额度 summary、异常批准、确认和导出，没有 ledger list/export。前端配置页只展示额度卡片，维修页只展示额度卡片和维修单列表，见 `erp-ui/src/views/oa/fixedAsset/config/index.vue:45-70`、`erp-ui/src/views/oa/fixedAsset/repair/index.vue:37-54`。当前运行态固定资产四张表均为 0 行，但代码路径在普通上报和异常批准时都会写额度流水。
- 影响：维修额度是固定资产模块的核心约束。没有流水查询时，用户看到“已用额度”或“未来已透支额度”异常，只能反查维修上报列表，且无法区分普通占用、透支未来额度和特殊追加额度的汇总来源。后续如果补取消、驳回或额度反冲，也缺少页面核对每笔占用/释放的基础。
- 建议：增加固定资产额度流水页或在额度卡片上支持钻取，展示维修单号、店铺、年份、占用类型、金额、操作人、时间、备注和关联维修详情；导出固定资产维修或配置时也应可附带额度流水。`sumUsedQuotaAmount` 的口径应在页面上解释清楚，特殊追加额度是否计入已用额度要有独立列。

### P3-160 固定资产配置编辑可改店铺，但只重算新店铺额度，原店铺额度会残留

- 现象：固定资产配置编辑弹窗允许修改“店铺”，保存时后端也会更新 `oa_fixed_asset_config.shop_dept_id`；但服务层只按保存后的新店铺调用 `rebuildQuota`，没有同步重算原店铺额度。如果一条资产配置从 A 店铺改到 B 店铺，A 店铺的资产总额和年度额度可能继续保留旧值。
- 证据：前端编辑弹窗复用新增表单，`erp-ui/src/views/oa/fixedAsset/config/index.vue:105-109` 的店铺选择器在编辑时仍可改，`:287-301` 的 `openConfigForm(row)` 直接把列表行复制进 `form`，`:344-355` 保存整个 `form`。后端 `OaFixedAssetServiceImpl.saveConfig()` 在 `:87-103` 先按请求中的 `shopDeptId` 解析可写店铺并更新配置；更新前虽然调用 `selectConfigById(configId, selectedShopDeptId)` 做权限校验，但没有保存旧 `shopDeptId`。随后 `:105` 只执行 `rebuildQuota(shopDeptId, currentYear(), config.getAnnualRepairRatio())`，`OaFixedAssetConfigMapper.xml:75-88` 又允许更新 `shop_dept_id`。当前运行态固定资产四张表均为 0 行、配置权限只授给 `admin`，所以本轮未构造写入数据，只按源码和表结构确认流程缺口。
- 影响：多店铺初始化或资产归属调整时，管理员从编辑表单迁移资产到另一个店铺，会让新店铺额度增加，但原店铺额度不一定扣回；维修可用额度、未来透支额度和额度卡片会与真实资产配置不一致。叠加 P3-139 的年度比例入口问题，跨店铺编辑还可能把当前页面额度比例写到目标店铺，造成额度策略串店。
- 建议：资产配置编辑时要么禁止修改店铺，只允许删除后在目标店铺重新新增；要么在服务层记录旧 `shopDeptId`，当归属变化时同时重算旧店铺和新店铺额度，并在保存确认里展示“将影响两个店铺额度”。年度比例应从资产配置表单拆出，避免跨店铺迁移时顺带覆盖目标店铺额度策略。

### P3-161 固定资产商品选择提示可按编码搜索，但实际只按商品名称查询

- 现象：固定资产配置和异常批准弹窗的商品选择器提示“输入商品名称或编码”，下拉项也展示“商品名称 / 商品编码”；但远程搜索只把输入内容传给 `productName`，没有传 `productCode`。用户按页面提示输入商品编码时，真实接口会按商品名称模糊查询，找不到对应商品。
- 证据：`erp-ui/src/views/oa/fixedAsset/config/index.vue:110-123` 和 `:165-168` 的商品选择器展示商品名称/编码并提示“输入商品名称或编码”；但 `searchProducts(keyword)` 在 `:316-323` 调用 `listProduct({ pageNum: 1, pageSize: 20, productName: keyword, status: "0" })`。后端商品列表 Mapper 支持 `productName` 模糊和 `productCode` 精确两个独立条件，见 `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml:69-83`。当前运行态 `inv_product` 中有 50 条非空商品编码，样例 `WLC-727511 / 清香铁观音` 按 `product_code='WLC-727511'` 可命中 1 条，但 `product_name like '%WLC-727511%'` 命中 0 条。
- 影响：固定资产初始化时管理员通常会拿设备台账或商品编码去查找资产商品；页面明确说支持编码，但实际按编码输入没有结果，会让用户误以为商品不存在，转而新建重复商品或改用名称人工筛选。多店铺、多商品场景下，重名商品和相似名称会增加误选风险，固定资产配置、额度计算和维修上报会因此挂到错误商品。
- 建议：商品选择器应支持同一个关键字同时匹配 `productName/productCode`，或在前端识别编码并传 `productCode`；后端也可以提供统一 `keyword` 查询，按名称模糊、编码精确/前缀匹配返回。若暂不支持编码搜索，应把 placeholder 改成“输入商品名称”，避免页面承诺和接口行为不一致。

### P3-162 固定资产配置查询接口和权限点没有被电脑端使用

- 现象：固定资产配置后端提供 `/oa/fixedAsset/config/{configId}` 详情接口，权限树也有 `oa:fixedAsset:config:query`，但电脑端配置页没有详情按钮；点击“编辑”也只是把列表行复制到弹窗，不调用详情接口。配置查询权限在桌面端没有真实按钮或副页面承载。
- 证据：`erp-ui/src/api/oa/fixedAsset.js:11-15` 定义了 `getFixedAssetConfig(configId)`，但 `erp-ui/src/views/oa/fixedAsset/config/index.vue:200` 只导入 `approveFixedAssetException/deleteFixedAssetConfig/getFixedAssetQuota/listFixedAssetConfigs/saveFixedAssetConfig`，没有导入或调用 `getFixedAssetConfig`。列表行操作只有“编辑/删除”，见 `:89-93`；`openConfigForm(row)` 在 `:287-301` 直接 `Object.assign` 当前列表行并打开编辑弹窗。后端 `OaFixedAssetConfigController.java:33-46` 分别声明列表权限 `oa:fixedAsset:config:list` 和详情权限 `oa:fixedAsset:config:query`；`OaFixedAssetConfigMapper.xml:36-56` 中列表和详情当前都使用同一组 `baseColumns`。运行态固定资产配置、额度、维修和额度流水表均为 0 行，`3312 固定资产配置查询` 当前只授给 `admin`，但电脑端没有对应入口。
- 影响：管理员在权限树中能看到“固定资产配置查询”，但实际桌面端无法表达“能看列表但不能看详情/编辑前先查详情”。列表接口被迫承担编辑弹窗所需字段，后续如果给详情接口补充资产编号、附件、历史额度、变更记录或更多审计字段，电脑端编辑/详情都拿不到；如果把列表 DTO 精简，又会悄悄破坏当前编辑弹窗。
- 建议：固定资产配置页应补齐详情入口或让编辑动作先按 `configId` 调用 `getFixedAssetConfig`，并按 `oa:fixedAsset:config:query` 控制查看详情；列表 DTO 只保留表格摘要字段，详情接口返回完整资产配置、额度策略和变更信息。若当前不需要独立查询权限，应删除或停用 `config:query` 节点，避免权限树表达空能力。

### P3-163 固定资产维修页“待确认上报”卡片只统计当前页列表

- 现象：固定资产维修上报页顶部有“当前可用额度 / 已用额度 / 月度释放额度 / 待确认上报”四个汇总卡片，前三个来自后端额度汇总接口，第四个“待确认上报”却只用当前分页表格 `list` 计算。翻页、搜索商品或筛选状态后，卡片数字会变成当前页、当前筛选结果里的待确认条数，而不是当前店铺真实待确认总数。
- 证据：`erp-ui/src/views/oa/fixedAsset/repair/index.vue:37-53` 渲染四个汇总卡片，其中前三个读取 `quota.availableQuotaAmount/usedQuotaAmount/monthlyQuotaAmount`，第四个读取 `pendingConfirmCount`；`:177-180` 的 `pendingConfirmCount()` 实现是 `this.list.filter(item => item.status === "pending_confirm").length`。同页 `:207-212` 的 `getList()` 把分页接口返回的 `res.rows` 放入 `list`，并把 `res.total` 放入分页总数；后端 `OaFixedAssetRepairController.list()` 在 `:31-37` 先 `startPage()` 再返回分页 `TableDataInfo`，Mapper 还会按当前查询 `status/productName/shopDeptId` 过滤。当前运行态 `oa_fixed_asset_repair=0`，没有存量数据触发错数，但代码路径已经决定该卡片不是全量统计。
- 影响：异常批准生成的 `pending_confirm` 维修单需要门店及时确认，这个卡片天然像待办总数。若有多页维修记录或用户筛选“已上报”，卡片可能显示 0，误导管理员以为没有待确认单；如果分页只展示当前页，也可能低估待处理数量。多店铺场景下，门店或管理员依赖这个卡片处理待办，会漏看其他页或其他筛选条件下的待确认上报。
- 建议：后端提供独立的维修上报统计接口，按当前店铺/组织范围返回待确认总数，并与额度汇总同一范围；或者在列表接口返回 `pendingConfirmTotal`。如果产品只想表达当前页数量，卡片文案应改成“当前页待确认”，并在筛选状态不是全部时隐藏或说明“受当前筛选影响”。

### P3-164 固定资产配置删除不检查维修单和额度流水引用，历史资产关系会断开

- 现象：固定资产配置页允许管理员直接删除资产配置，前端只弹出“确认删除该固定资产配置？”的通用确认；后端删除时物理删除配置记录并重算额度，没有检查该店铺/商品是否已有维修单、异常批准或额度流水引用，也没有停用/退役流程和影响预览。
- 证据：`erp-ui/src/views/oa/fixedAsset/config/index.vue:89-95` 的行操作包含“删除”，并受 `oa:fixedAsset:config:delete` 控制；`:357-364` 的 `removeConfig(row)` 确认后直接调用 `deleteFixedAssetConfig(row.configId)`。后端 `OaFixedAssetServiceImpl.deleteConfigById()` 在 `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java:109-117` 读取配置后调用 `configMapper.deleteConfigById(configId)`，随后只对 `config.getShopDeptId()` 执行 `rebuildQuota`。`OaFixedAssetConfigMapper.xml:91-93` 是 `delete from oa_fixed_asset_config where config_id = #{configId}` 物理删除；建表脚本和运行态表结构显示 `oa_fixed_asset_repair` 只保存 `shop_dept_id/product_id/product_name` 等维修快照，`oa_fixed_asset_quota_ledger` 只保存 `repair_id/shop_dept_id/quota_year/movement_type/amount` 等流水字段，二者都没有 `config_id` 外键或配置快照。当前运行态固定资产配置、维修和额度流水表均为 0 行，问题来自现有删除链路和表关系设计。
- 影响：一旦资产配置已产生维修上报、异常批准或额度流水，后续删除配置会移除原始资产定义并重算年度额度；历史维修单虽然还能按商品/店铺展示，但无法还原当时的资产数量、单价、启停状态和配置身份。若之后用同一商品重新新增配置，历史维修和新配置也难以区分，审计、额度追溯和资产生命周期都会变得不可靠。
- 建议：固定资产配置默认应走停用/退役，而不是物理删除；删除前按店铺、商品和配置身份检查维修单与额度流水引用，有引用时阻止删除或转为归档状态，并展示受影响维修单、额度流水和年度额度变化。后续维修单和额度流水应保存 `config_id` 或关键配置快照，保证历史记录能追溯到当时的资产配置。

### P3-165 固定资产配置金额和年度比例只靠前端限制，后端可写入异常额度

- 现象：固定资产配置弹窗用数字控件限制数量、单价、资产金额和年度申报比例，但后端实体只校验店铺和商品必填，没有校验数量必须大于 0、金额不能为负、年度申报比例必须在 0-100 之间；数据库表也没有 `CHECK` 约束。构造请求可以绕过前端控件写入负数或超范围比例，随后额度重算会把异常值写入年度额度。
- 证据：前端 `erp-ui/src/views/oa/fixedAsset/config/index.vue:125-135` 对 `assetQuantity/assetUnitPrice/assetAmount/annualRepairRatio` 分别设置了 `:min`、`:max` 和精度；同页 `rules` 只做必填。后端 `OaFixedAssetConfig.java:14-41` 只有 `shopDeptId/productId` 两个 `@NotNull`，`assetQuantity/assetUnitPrice/assetAmount/annualRepairRatio` 没有 `@DecimalMin`、`@DecimalMax` 或自定义校验。`OaFixedAssetServiceImpl.saveConfig()` 在 `:85-105` 直接 `resolveAssetAmount(config)` 后保存，并把 `config.getAnnualRepairRatio()` 传给 `rebuildQuota()`；`:236-252` 中额度按 `assetTotal * ratio / 100` 计算后写入年度额度。`sql/erp_oa_fixed_asset_20260619.sql:3-35` 和运行态 `oa_fixed_asset_config/oa_fixed_asset_quota` 表结构只有普通 decimal 字段，没有正数或比例范围约束；当前运行态固定资产配置表为 0 行，本轮没有构造写入。
- 影响：页面正常操作时看似受控，但接口层可以写入负数量、负金额或超过 100% 的年度比例，导致固定资产总金额、年度额度、月度释放额度和当前可用额度变成异常值。多店铺场景下，一个异常配置就会污染该店铺整年的维修额度，后续维修上报可能被错误放行或错误拦截，且管理员从页面上很难判断是数据录入问题还是额度规则问题。
- 建议：后端保存固定资产配置时必须校验 `assetQuantity > 0`、`assetUnitPrice >= 0`、`assetAmount >= 0`，且资产金额应与数量和单价一致或明确只允许系统计算；年度申报比例应校验在 0-100 之间，并最好移到独立额度配置接口。数据库层补充 `CHECK` 或保存前统一规范化，额度重算前也应拒绝负资产总额和超范围比例。

### P3-141 客户删除引用检查按客户名称匹配，改名或同名客户会让删除保护失真

- 现象：客户删除时后端会检查销售历史、销售退货和发货通知是否引用了该客户，但检查条件是当前客户名称 + 店铺，而不是稳定的 `customer_id`。销售单、退货单和发货通知本身也只保存 `customer_name`，所以客户改名、同名客户或历史单据手工录入相同名称都会影响删除判断。
- 证据：客户删除确认框只显示客户名称，见 `erp-ui/src/views/inventory/customer/index.vue:187-190`。后端 `InvCustomerServiceImpl.deleteCustomerByIds()` 在删除前调用 `salesOrderMapper.countByCustomerNameAndShop(customer.getCustomerName(), customer.getShopDeptId())`，见 `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceImpl.java:56-68`。`InvSalesOrderMapper.xml` 的 `countByCustomerNameAndShop` 分别按 `inv_sales_order.customer_name`、`inv_sales_return.customer_name`、`inv_delivery_notice.customer_name` 和 `shop_dept_id` 计数，没有 `customer_id`。运行态当前 `inv_customer=0`、`inv_sales_order=0`，但表结构也显示销售单只有 `customer_name` 字段，没有 `customer_id`。
- 影响：如果客户 A 已有销售历史后被改名，删除客户 A 时可能查不到旧名称历史单据而放行；如果客户 B 与历史自由文本客户同名，则可能被错误拦截删除。多门店、多账号录入客户时，这会让“客户已被销售历史单据引用”的提示既可能漏报，也可能误报，客户主数据清理和停用策略不可靠。
- 建议：销售、退货和发货通知应保存 `customer_id` 及客户名称快照，删除引用检查按 `customer_id` 判断；客户改名只改变主数据和新单据选择展示，不改变历史快照。过渡期内应禁止已有历史客户改名或在改名时同步写客户别名/历史名称表，并在删除前展示命中的历史单据摘要。

### P3-101 定时任务“执行一次”复用状态修改权限，缺少独立执行授权

- 现象：定时任务的启用/停用开关和“执行一次”共用 `monitor:job:changeStatus` 权限；权限树里没有独立的 `monitor:job:run`、`monitor:job:execute` 或类似执行权限节点。角色管理员无法配置“能启停任务但不能手动执行”或“只能查看/执行指定任务”的最小权限。
- 证据：MySQL 复核当前 `monitor:job:%` 权限只有 `list/query/add/edit/remove/changeStatus/export`，没有 run/execute 节点；`rg "monitor:job:run|job:run|执行一次权限"` 除本报告外没有前后端权限判断命中。前端 `erp-ui/src/views/monitor/job/index.vue` 中状态开关调用 `changeJobStatus`，“执行一次”下拉项也用 `v-hasPermi="['monitor:job:changeStatus']"` 并调用 `runJob`。后端 `SysJobController.changeStatus()` 和 `SysJobController.run()` 同样都标注 `@RequiresPermissions("monitor:job:changeStatus")`。当前运行态 `sys_job` 3 条任务均为停用，`common/zjl` 仍持有 `monitor:job:changeStatus`。
- 影响：“执行一次”会绕过 cron 计划立即触发后台任务，风险高于普通启停。现在只要账号能改任务状态，就能手动触发任务；如果后续接入考勤同步、日志清理、库存预警、消息推送等任务，运维最小权限和事故责任边界会不清晰。P2-40 记录的是停用任务仍可执行，本问题记录的是执行动作没有独立授权。
- 建议：新增独立 `monitor:job:run` 或 `monitor:job:execute` 权限，前端下拉项和后端 `/schedule/job/run` 都改用该权限；`monitor:job:changeStatus` 只控制启用/停用。执行前应显示任务名、调用目标、当前状态和影响范围，并与 P2-40 一起约束停用任务的执行语义。

### P3-96 在线用户接口全量扫描 Redis，分页只在前端本地切片

- 现象：在线用户列表的后端接口不接收或处理 `pageNum/pageSize`，每次请求都会扫描所有 `login_tokens:*`，把匹配结果完整返回给前端；前端再用 `list.slice((pageNum-1)*pageSize, pageNum*pageSize)` 做本地分页。当前 Redis 只有 2 个登录 token，暂时不明显，但多账号、多门店和门店共用电脑场景下会放大性能与数据暴露面。
- 证据：`SysUserOnlineController.list` 在 `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserOnlineController.java:41-69` 直接 `redisService.keys(CacheConstants.LOGIN_TOKEN_KEY + "*")`，循环读取每个 `LoginUser` 后 `getDataTable(userOnlineList)` 返回完整 rows，没有 `startPage()` 或服务端分页。`BaseController.getDataTable` 在 `erp-common/erp-common-core/src/main/java/com/erp/common/core/web/controller/BaseController.java:81-88` 会把传入 list 原样设置为 `rows`。前端 `erp-ui/src/views/monitor/online/index.vue:30-33` 对 `list` 做本地 slice，`:61` 的分页也只改本地 `pageNum/pageSize`。在线用户 API `erp-ui/src/api/monitor/online.js:3-9` 仅转发查询参数，页面查询参数只有 `ipaddr/userName`。
- 影响：在线用户数量多时，任何列表翻页或筛选都会读取和传输全部会话；拥有列表权限的角色也会一次拿到所有在线会话编号、账号、IP 和登录时间，不能靠分页减少暴露范围。
- 建议：后端改为游标/分页查询 Redis 登录 token，至少按 `pageNum/pageSize` 返回当前页 rows；筛选也应在服务端完成并返回真实 total。前端不要保留全量 token 列表，强退后重新拉取当前页即可。

### P3-07 内置系统参数仍显示可删除操作

- 现象：`/system/config` 当前 8 条参数全部是系统内置参数，但每一行仍显示“删除”按钮，顶部选择后也会启用删除；实际后端会拒绝删除内置参数。
- 证据：数据库 `sys_config.config_type` 8 条均为 `Y`；浏览器实测参数列表每行都有“修改 / 删除”；`erp-ui/src/views/system/config/index.vue:127-142` 未按 `configType` 隐藏或禁用行内删除，`erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java:138-146` 会在删除时抛出“内置参数不能删除”。
- 影响：管理员会看到一个当前数据状态下必然失败的操作，点击确认后才得到错误反馈；对“内置参数”和“自定义参数”的可操作边界不直观。
- 建议：前端对 `configType=Y` 的参数隐藏或禁用删除按钮，并在 tooltip 或标签中说明“内置参数不可删除”；批量删除时也应过滤或提示已选内置参数。

### P3-08 系统接口菜单目标 HTTP 200 但内容不可用

- 现象：系统工具下“系统接口”菜单配置指向 `http://localhost:8080/swagger-ui/index.html`。当前直连该地址 HTTP 状态为 200，但页面内容不是 Swagger UI，而是业务错误 JSON；实际 Swagger 静态页面在 `/webjars/swagger-ui/index.html` 可以打开，但各服务 `/v3/api-docs` 当前返回错误或需要 token 后仍无文档数据，菜单入口、静态 UI 和 API docs 数据三者没有形成可用闭环。
- 证据：数据库 `sys_menu` 的“系统接口”路径为 `http://localhost:8080/swagger-ui/index.html`，`is_frame=0`、`perms=tool:swagger:list`，且 `common / 普通角色`、`zjl / 总经理` 均持有该权限。2026-06-24 `curl http://localhost:8080/swagger-ui/index.html` 返回 `http_code=200/content_type=application/json`，响应体为 `{"code":500,"msg":"404_NOT_FOUND ... No static resource swagger-ui/index.html ..."}`；`/webjars/swagger-ui/index.html` 返回 Swagger UI HTML；未带 token 请求 `/system/v3/api-docs` 返回 `401 令牌不能为空`，带 admin token 请求 `/system/v3/api-docs`、`/code/v3/api-docs`、`/oa/v3/api-docs`、`/inventory/v3/api-docs` 仍返回 `No static resource v3/api-docs`。网关和各模块配置均为 `springdoc.api-docs.enabled: ${SPRINGDOC_API_DOCS_ENABLED:false}`，网关白名单也未放行 `/*/v3/api-docs`，测试 `IgnoreWhitePropertiesTest` 明确断言 v3 api docs 不应匿名。
- 影响：单纯健康检查 HTTP 200 会误判系统接口可用；如果后续启用系统工具菜单，管理员点击后看到的是接口错误而不是文档页面。即使改到 `/webjars/swagger-ui/index.html`，当前 API docs 数据仍不可用，业务角色还持有系统接口权限，会造成“有入口、有权限、无内容”的交付错觉。
- 建议：先决定接口文档是否属于当前交付范围。若不交付，应停用系统接口菜单和 `tool:swagger:list` 授权，并在部署说明中明确 API docs 关闭；若交付，应统一菜单地址、Swagger UI 配置和服务 `/v3/api-docs` 生成开关，文档入口应要求登录并只授予开发/运维角色。外链可用性检查应同时校验 HTTP 状态、内容类型、页面关键字和 API docs 数据，不应只看 200。

### P3-01 菜单名与面包屑命名不一致

- 现象：门店侧左侧菜单为“商品资料 / 商品管理”，但进入商品管理页后面包屑显示“仓库管理 / 商品管理”。
- 证据：浏览器实测 `/cangku/product` 页面出现该面包屑。
- 影响：门店用户容易误以为自己进入了仓库模块。
- 建议：同一路由在不同角色下使用一致的业务命名，或将商品资料从仓库模块拆出独立父级。

### P3-02 登录页仍展示默认管理员账号密码

- 现象：登录页默认填充/展示 `admin` 和 `admin123`。
- 证据：本轮浏览器登录页实测可见。
- 影响：生产环境会形成弱安全暗示，也容易让测试账号信息进入截图或演示。
- 建议：开发环境可保留便利入口，但生产构建应隐藏默认账号密码；也可以通过环境变量控制是否预填。

## 后续建议顺序

1. 先修商品列表/详情、商品新增/编辑/导入、供应商详情、采购商品选择和采购单价字段、采购退货金额/单价字段、库存列表/详情/汇总、库存调整、销售退货成本回写、库存流水、库存盘点详情、调拨详情和报表里的成本字段泄露或写入越权，以及已签劳动合同直接作废、用户配置状态同步、店铺授权“组织范围”误导、采购商品主数据缺失、角色薪资配置入口失效、发货通知无可用仓库、用户导入弱初始密码、销售退货原单门店边界、采购退货原单仓库边界这几类 P1 问题。
2. 再修新增用户闭环：初始密码配置、创建后返回新用户 ID、首次登录改密策略和单组织自动选择。
3. 梳理“用户组织授权”和“业务供货仓库”的模型边界，避免后续多门店/多仓库扩展时权限语义混乱。
4. 固定资产模块需要产品/运维确认上线状态：运行态表、菜单和 hyphen 路由已存在，下一步重点不是补 SQL，而是补角色矩阵、样例数据/额度初始化、空数据禁用写入口和路由/API 命名说明；若不交付则应从当前范围中明确移除。
5. 系统日志时区、调拨记录时间、OA 时间展示应统一处理，避免审计、考勤、签约和调拨数据出现不同时间语义。
6. OA 考勤日期应优先修成“后端明确返回今日状态 + 前端只做展示”，避免页面按钮状态由 UTC 字符串截断推断。
7. 采购退货/销售退货原单空态、客户表单校验属于流程可理解性和数据质量问题，建议跟随采购/销售链路一起修。
8. OA 首页、待办/已办店铺名称、客户选择器、供应商信息展示属于体验和数据闭环问题，建议随多账号多店铺流程一起改。
9. 监控外链需要按部署环境配置和健康状态控制；未启用服务不应作为可点击功能暴露给管理员。
10. 库存页应把“当前选择的组织类型”和“当前路由入口类型”作为统一动作前置条件，列表、按钮、弹窗、提交接口保持同一套校验语义。
11. 系统配置页应把内置参数的不可删除规则前置到按钮状态，避免让管理员点击一个必然失败的操作。
12. 系统工具若不属于当前交付范围，应同时停用父菜单、子菜单、动态副路由和外链；若属于交付范围，应补齐代码生成列表入口、空态和 Swagger 实际地址。
13. 调拨记录应补齐审批轨迹和状态流水展示，否则数据库中的审批任务、审批实例和状态日志无法在桌面端闭环审计。
14. 库存盘点日期、OA 考勤日期等纯日期字段应优先统一为本地日期字符串，避免 UTC 转换导致业务日期错一天。
15. 账号自助入口要统一配置语义：注册关闭时不要显示可填写的注册表单；首页和 OA 首页都要按实际启用模块生成待办/采购提示；个人中心与用户管理应使用同一套联系方式必填规则。
16. 先修复仓库主数据入口稳定性，特别是 `/cangku/supplier` 深链刷新和仓库横向子菜单可见点击；再补测供应商按钮和弹窗全流程。
17. 分类、供应商、商品等主数据删除前应统一前置展示引用状态，能停用就不鼓励删除，避免管理员靠后端报错理解业务约束。
18. 商品管理应优先修复分页和导入预检：分页要真正限制首屏 rows，导入要避免把缺供应商、缺售价、缺编码的商品直接放入正式可选商品库。
19. 商品主数据建议建立治理队列：按缺供应商、缺售价、缺编码、缺图片、重复商品、已有业务引用分组处理，并在采购/销售选择器中禁用明显不可用商品。
20. 仓库采购和供应商深链刷新空白应作为同一类路由稳定性问题处理，修复后必须补测直接打开、刷新、从首页入口跳转、从侧边菜单跳转四种入口。
21. 采购制单流程应先明确“一张采购单是否只能对应一个供应商”，再调整商品选择器、保存校验、采购退货、供应商对账和导出字段；同时修复采购选择器全量渲染和不可采购商品默认可选的问题。
22. 采购退货应以原采购单作为唯一权威来源：供应商、原采购单号、可退数量、质检/入库状态和历史已退数量都应由后端统一校验并回填，前端只做只读展示和数量录入。
23. 采购退货权限需要和角色配置对齐，特别是新增、修改、提交、确认、取消/删除的真实接口权限；当前无效的 `inv:purchaseReturn:edit` 不应继续暴露给管理员配置。
24. 仓库库存入口需要按“首页快捷入口、仓库菜单、深链直达、刷新恢复”四条路径统一修复；只更新 URL 和面包屑但正文仍是工作台属于高优先级路由问题。
25. 库存可见范围、库存调整、库存变动日志和导出需要使用同一套组织范围语义：可见库存只读时禁用调整，日志能解释同范围库存变化，成本字段继续独立受 `inv:cost:view` 控制。
26. 调拨主流程要按“发货批次状态”打通，而不是只按整单状态判断动作；部分发货后已有 `pending_receive` 批次时，目标门店必须能先确认该批次收货。
27. 调拨入口要和库存、采购同类路由问题一起修复并回归：`/inventory/transfer`、`/cangku/transfer` 的直连、刷新、菜单点击、首页跳转都必须保证 URL、面包屑、标签页和正文组件一致，并且仓库路径必须强制仓库上下文或明确阻断门店动作。
28. 门店销售链路需要纳入同一套路由稳定性修复：`/inventory/sales`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report`、`/inventory/customer` 的直接打开、刷新、组织切换恢复都要有端到端用例；当前页面可渲染空态还不足以证明流程闭环，销售退货还必须补后端原单门店一致性校验。
29. 客户主数据必须绑定门店上下文，前后端都应拒绝仓库上下文新增/编辑客户；销售单客户字段也应同步改为客户选择器或“新建客户后回填”。
30. 销售发货职责、可用仓库模型和权限需要重新收口：先定义门店销售单可以从哪些仓库发货，再确认是仓库角色、门店角色还是运营角色执行发货通知，并同步清理旧 `inv:sales:deliver` 和重复停用 `inv:deliveryNotice:*` 节点，避免同一流程保留多套互相矛盾的出库入口。
31. 用户组织授权页要作为多账号多店铺核心回归入口单列验证：`/system/shop` 的直连、刷新、菜单进入、新建用户跳转和授权保存都必须稳定，且不能依赖当前选中的库存组织上下文。
32. 选择组织页要修复首次空树问题：`/system/dept/shop-tree` 成功返回前保持加载/重试状态，避免先显示“0 个业务组织”；单组织账号可结合自动预选减少进入系统前的额外操作。
33. 通知公告管理页要纳入系统管理副页面回归：先保证空数据下能渲染列表空态和新增入口，再补测新增公告、标题详情、顶部公告已读、阅读用户搜索分页和删除后已读记录清理。
34. 用户角色授权要作为独立权限域重构：角色分配候选必须展示并校验部门、组织类型、当前角色和店铺/仓库授权范围，店长、仓库管理员、运营等角色不能只靠人工从用户名判断。
35. 授权写操作应从用户/角色普通编辑权限中拆出来，并增加空角色提交保护；权限码、前端按钮、后端注解、审计日志和角色默认授权需要一起回归。
36. 部门管理需要把组织类型层级作为硬规则处理：前端按父级动态过滤类型，后端保存时校验层级，回归时覆盖新增、编辑、迁移父级、停用父级和组织选择页。
37. 系统基础资料删除要统一前置引用状态：菜单、部门、岗位、字典、参数都应在列表上说明为什么不可删，并优先提供停用、查看引用或调整引用入口。
38. 岗位和角色的职责要重新梳理：如果岗位用于人事，应在用户列表、详情、导入和薪资/考勤模块中可见；如果只是历史字段，应减少和角色重复的配置入口。
39. 系统基础配置页要做直连/刷新加载态专项回归，特别是角色、菜单、部门、岗位、字典、参数和字典数据副页面，避免先出现空白或“暂无数据”。
40. 系统监控和日志主列表要优先修复直连/刷新空白：在线用户、登录日志、操作日志、定时任务必须能稳定从菜单、深链、刷新和副页面返回进入。
41. 定时任务“停用”和“执行一次”的语义需要收口：停用任务默认不能被手动执行，除非页面和后端都明确这是临时执行并加强确认。
42. 登录/在线运维动作要增加状态感知：解锁需要展示锁定状态，强退需要标记当前会话，日志空数据页需要禁用无意义清空/导出。
43. 薪资配置要和其他系统路由一起做直连/刷新专项回归，特别是 `/system/salary` 裸路由、带 query、角色入口、菜单点击和返回路径。
44. OA 工资计算上线前应先补齐门店工资参数、员工薪资绑定覆盖和计算预检；页面应在点击覆盖计算前列出缺配置门店、缺绑定员工和异常工资参数。
45. 薪资基础数据需要清洗：清理测试档位、异常低薪/零薪档位、重复档位、角色与岗位明显不匹配的绑定，并同步整理薪资权限树中的导入/列表权限。
46. 单据状态审计需要补齐电脑端入口：报表中心独立查询页或单据详情时间线都可以，但要复用现有 `inv:audit:list` 权限和组织范围过滤；不上线时应停用该权限。
47. 清理备份表应从正式业务库治理出去：迁移到归档库/归档表，标注来源、保留周期和访问权限，并清理明显异常的历史成本、部门名称和状态数据。
48. 单号序列需要可追溯：当正式单据被清理或归档后，序列表应能关联归档索引，避免只剩跳号数字而无法在电脑端解释号码来源。
49. 劳动合同路由要作为 OA 高优先级回归：`/oa/labor-contract` 的菜单点击、直连、刷新、带 query、组织切换恢复都必须能稳定显示签约单、模板与企业章。
50. 劳动合同组织口径需要和员工归属强绑定：发起签约时按员工所属门店或明确合同主体落库，禁止在仓库上下文给门店员工生成合同，历史错位合同需要迁移或标注。
51. 劳动合同签署后状态变更、证据链和 hash 验证要收口：已签合同不能继续走普通“作废”，必须拆成受控解除/终止流程；hash 验证必须做组织范围过滤，读操作写事件要有明确提示，存量不完整证据链要补算或在详情页标明不可完整校验。
52. 固定资产模块上线前先定义角色矩阵、配置样例资产和额度初始化流程；门店维修上报不能只授权超级管理员，移动端/电脑端权限要同口径，零额度/无资产配置时要给出明确空态而不是暴露必然失败的上报入口。
53. 固定资产额度计算要拆分读写：GET 额度查询只返回状态，初始化、重算、异常批准和确认上报应分别有明确写操作、审计日志和反冲流程。
54. 固定资产维修上报必须以后端配置表为准校验可报修资产，普通商品不能绕过前端直接占用固定资产维修额度。
55. 固定资产商品主数据需要独立资产标识或资产分类，配置页不要从 162 个普通启用商品里直接挑“固定资产”。
56. 固定资产报修附件应改为上传/预览组件，避免门店用户手填 URL。
57. OA 考勤权限要拆分“个人考勤页面”和“全部考勤管理”，不能继续用 `oa:attendance:list` 同时代表页面入口和管理列表；已有的 `oa:attendance:my`、`oa:attendance:correct` 必须接入真实个人列表和考勤更正流程，或从权限树移除。
58. 考勤全部视图应展示员工、部门和考勤组织，并提供员工/组织/状态筛选，否则多门店管理账号无法从页面识别记录归属。
59. 考勤导出要跟随“我的/全部”视图或拆成两个明确按钮，避免用户在个人视图下导出当前组织全部考勤。
60. 考勤组织口径要确认是否包含仓库：包含则统一称为“考勤组织”，不包含则仓库上下文应禁用打卡并清理历史仓库考勤记录。
61. 报表中心成本字段必须由后端按 `inv:cost:view` 做字段级权限控制，不能只靠前端隐藏卡片。
62. 报表中心日期筛选要拆清楚：库存类指标是当前快照，采购/销售金额是单据日期；不要用一个“业务日期”暗示所有指标都按历史日期统计。
63. 预估毛利上线前应补销售出库成本快照或 COGS 汇总口径；未补齐前不要把“销售金额 - 采购金额”命名为毛利。
64. 低库存预警列表要补齐最后入库/最后出库字段映射，或删除这两列，避免空列误导补货判断。
65. 字典和参数管理的隐藏菜单要和角色授权一起治理：不希望业务角色使用时撤销 `system:dict:*`、`system:config:*`，希望使用时就显式展示入口并强化审计。
66. 系统参数要从自由文本升级为按 key 类型化配置：布尔开关、枚举、数字范围、IP/网段列表分别使用对应控件，前后端同时校验。
67. 注册开关要同时驱动登录页入口、注册页状态和后端允许注册，避免“配置开启但无入口”或“配置关闭但仍能填写表单”的前端/后端不一致。
68. OA 采购审批恢复前必须先清理部门负责人配置：负责人要绑定真实可登录账号，并在提交采购申请前校验下一节点审批人有效。
69. OA 采购详情要补审批意见时间线，把 `oa_purchase_comment` 的动作、意见、审批人和时间展示到“我的申请”和“我的已办”详情中。
70. 角色授权树要和菜单状态同步：停用菜单不应继续作为普通已勾选权限保存，隐藏菜单要明确是否仍可直达，重新启用功能时必须重新确认角色矩阵。
71. 库存盘点取消和删除草稿必须和创建、录入、提交、确认使用同一套当前组织可写校验，不能只靠前端隐藏按钮或后端可见范围判断。
72. 库存盘点权限点要清理无效的 `inv:stockCheck:edit`，或把“保存草稿修改”和“提交盘点”拆成真实两个接口/按钮，避免角色配置看起来可细分但实际无效。
73. 发货通知的发货仓库候选和后端校验要统一改成真实配送/授权关系，不能继续按“仓库必须是销售门店子节点”过滤；修复后要用当前 `104 / 仓库` 给 `108 / 市场部门` 销售通知执行发货做回归。
74. 调拨审批规则启用前必须做后端预检：节点角色、岗位、节点顺序、范围组织和实际候选审批人都要校验，不能只依赖前端表单必填。
75. 调拨审批“预览”要改成按真实调拨场景试算，输入发货/收货组织、调拨类型和数量，并返回匹配规则、候选审批人、跳过节点和自动通过原因。
76. 调拨审批配置项要统一前后端语义：发货方/收货方范围、区域范围、区域经理和运营经理这些词必须有明确执行规则，否则应隐藏或改名。
77. 操作日志要按审计角色重新分层：摘要、详情、导出、删除/清空、账户解锁分开授权，并对请求参数和返回参数做字段级脱敏，避免日志页成为绕过业务权限的数据出口。
78. 用户导入、用户新增、重置密码、注册、个人改密必须使用同一套密码策略；导入所用初始密码配置不合规时应拒绝导入，而不是批量创建弱密码账号。
79. 用户管理高风险动作要拆权限：修改资料、重置密码、分配角色、店铺授权、导入、导出、删除分别由真实前后端权限点控制，权限树中未被使用的按钮权限要清理或接入。
80. 销售退货必须以后端原销售单为唯一权威来源：门店归属、原单状态、已出库数量、历史已退数量和退货入库组织都要在服务层校验，前端下拉过滤不能作为权限边界。
81. 销售退货权限需要和真实动作对齐：新增、修改草稿、提交、确认退货、取消、导出分别检查有效权限；无效的 `inv:salesReturn:edit` 要么接入草稿编辑，要么清理。
82. 销售退货原单选择器应只展示可退销售单，并显示已出库、已退、可退数量；没有可退原单时直接给出空态和下一步引导。
83. 销售管理权限同样需要清理无效 `inv:sales:edit`，或把销售单草稿编辑从新增权限中拆出，避免角色配置误导门店岗位分工。
84. 采购管理权限同样需要清理无效 `inv:purchase:edit`，或把采购单草稿编辑从新增权限中拆出，避免仓库岗位分工只能靠新增权限兜底。
85. 调拨保存接口要按新增/修改状态分别校验 `inv:transfer:add` 和 `inv:transfer:edit`，不能用 OR 放行；前端按钮隐藏必须有后端权限边界兜底。
86. 库存详情权限要和页面行为对齐：要么详情弹窗调用后端详情接口并检查 `inv:stock:query`，要么移除未生效的库存查询权限点，避免最小权限配置失真。
87. 客户编码需要明确身份规则：作为业务唯一号时做同门店唯一校验和自动生成；不作为唯一号时，前端搜索、导出和销售选择器都不能依赖它做唯一识别。
88. 采购退货必须以后端原采购单为权威来源：仓库归属、供应商、状态、已收货数量、质检结果、历史已退数量和本次退货库存组织都要在服务层校验，不能只信任前端传入的 `purchaseOrderId` 和明细。
89. 商品、分类、供应商主数据要先确定是集团共享还是仓库本地；列表可见范围、保存落库组织、编码生成、导入和供应商引用必须使用同一套组织口径。
90. 角色管理高风险授权动作要拆权限：数据权限、分配用户、菜单权限和基础资料修改应分别授权，不能都复用 `system:role:edit`。
91. 角色菜单授权必须以后端可授权菜单集合为准，保存前校验 `menuIds` 全部在当前账号可授权范围内；前端菜单树只用于展示，不能作为权限边界。
92. 启用账号要建立配置完整性准入：部门、角色、店铺/仓库授权缺一项时，用户列表必须可筛出并提示，登录流程也应给出明确阻断原因。
93. 审计和验收前必须固定运行态版本：后端 jar、`target/classes`、源码和前端 dev server 要么一致，要么在报告中明确哪个是权威口径。
94. 店铺授权列表的组织范围要显示具体授权数量和明细展开，避免把多个门店/仓库压缩成父级组织名后误导管理员。
95. 菜单管理属于全局权限基础设施，写权限应限制在超级管理员/平台运维；业务总经理最多保留只读或受控排序能力，不能直接修改权限标识、组件路径和路由状态。
96. 启用菜单要增加交付一致性闸门：每个 `component` 必须存在前端 Vue 文件，每个按钮权限必须有真实接口和权限注解；工作流这类整组缺实现的模块要么补齐，要么下线菜单和存量待办入口。
97. 薪资模板需要和薪资方案模型合并或明确分工；未实现前不要把 `system:salaryTemplate:*` 菜单和权限暴露给管理员。
98. 考勤 Excel 导入要么补齐可审计的上传、预览、校验和导入流程，要么停用菜单；工资核算依赖考勤数据时，不能只保留一个无实现的导入口。
99. 权限树要和源码权限保持一致：所有仍在前端按钮、静态路由或后端 `@RequiresPermissions` 中使用的权限，必须在 `sys_menu.perms` 中有可授权节点；否则应同步下线源码入口或恢复权限菜单。
100. 文件上传/删除要进入可授权模型：劳动合同模板、企业章等业务附件不能只依赖不可分配的 `file:upload`/`file:delete`，否则普通业务角色拿到页面维护权限也无法完成上传。
101. 成本字段权限码必须先定模型：当前运行态已回填为单一 `inv:cost:view`，但它挂在商品菜单下却承担全局成本字段边界；要么改成明确的全局敏感字段权限并补齐所有接口脱敏，要么按商品、库存、报表、采购价、导出等模块拆分权限并同步前后端判断。
102. 采购申请如果要恢复上线，必须把菜单状态、默认角色授权和审批工作台一起交付：当前组件路径和 `oa:purchase:list` 已回填，但页面仍停用隐藏；启用前要确认申请、提交、待办、已办、审批意见和导出都能端到端跑通。
103. 停用菜单也要治理：采购计划、本店库存、批次管理、钉钉旧入口这类缺组件的历史菜单，应删除/归档或补交付说明，避免未来误启用。
104. 进销存按钮权限要清理到真实动作，并用版本化迁移固定结果：商品价格编辑、客户信用额度、采购审批、质检记录、销售审批、销售发货通知等历史权限如果不接入前后端，就不应重新导入或授权给业务角色。
105. 多账号多店铺测试必须先固定运行态 schema：当前网页端应以 `BossERP_stock_state_75c59ee` 的 `sys_user/sys_user_shop` 为准，所有脚本和人工 SQL 都要避免误查同机非运行态 `BossERP`，否则会把已有 43 条授权误判为 0。
106. 正式销售、库存、库存流水和发货通知数据要先做一致性治理；保留已出库销售单时必须能在库存流水、发货通知详情、发货通知导出和 `inv_outbound_record` 出库记录中追溯，不能让正式库存表为空，也不能只在发货通知单头保留汇总数量。
107. 考勤数据链路要把钉钉原始记录、Excel 明细、用户映射和正式考勤记录串起来；正式考勤为空时，应提示仍有未处理原始数据，而不是让工资核算误判为没有考勤。
108. OA 采购申请恢复前必须先对齐业务表和流程表：申请 ID、流程实例 ID、任务 ID、审批意见和办理人都要能互相追溯，悬挂到不存在账号的任务要归档或修复。
109. 启用账号必须有最小可用配置：角色、部门/岗位和业务组织授权要形成一致闭环；有岗位无角色、无角色/无组织却启用的账号应阻断登录或进入明确错误页，自助注册也应先进入待审核/待配置状态。
110. 薪资模块要先补齐当前源码依赖的数据库表，再验收页面；`sys_user_salary_scheme`、`oa_salary_config`、`oa_salary_record` 缺失时不要开放薪资配置和 OA 工资入口。
111. 工作流存量实例要和业务表做专门对账：`wf_instance.form_data.purchaseId` 指向已删除/不存在申请、实例表单为空、运行任务悬挂、回调补偿反复失败时，应先归档或修复；同时把 `BossERP` 与 `BossERP_stock_state_75c59ee` 的工作流菜单、`wf_%` 表和 Activiti 表做 schema 级清理，再恢复待办、已办、回调补偿和 SLA 入口。
112. 参数设置页只能展示真实生效的配置；JWT 密钥、内部调用签名、文件上传限制、审计保留、PDA 开关等 key 要么接入运行代码并可验证生效，要么改为只读说明或下线，避免管理员修改无效参数。
113. 启用定时任务必须先校验目标 Bean/方法存在，并展示最近运行结果；钉钉自动同步这类影响工资考勤的任务不能只在 `sys_job` 中设为启用，还必须有 `sys_job_log` 和业务同步日志闭环。
114. 用户管理角色矩阵要按账号治理重新设计：普通业务角色和总经理不应默认拥有新增、导入、删除、导出、重置密码、角色分配和店铺授权能力；需要新建账号管理员/安全管理员职责，并为每个高风险动作配置独立权限、审计日志和二次确认。
115. 劳动合同正式数据清理必须有归档和审计闭环；当前合同/事件/企业章清空但模板保留时，应先确认数据重置来源，再恢复样例数据或明确该环境不验收合同历史追溯。
116. 商品主数据必须先对齐数据库迁移和源码字段：`inv_product` 宽表字段缺失时，不应继续验收仓库商品、采购商品选择、销售明细、导入导出或固定资产商品选择。
117. 销售和库存核心表也要纳入同一轮 schema 对账：`customer_name`、库存批次/库位展示字段和 `version` 缺失时，不应继续验收销售、库存流水、盘点、调拨或发货通知页面。
118. 单号规则要只保留一套权威来源；`sys_code_rule` 和 `inv_number_sequence` 并存但只有后者生效时，应删除无效配置或把所有生成逻辑统一接入配置表。
119. 仓库主数据也要只保留一套权威来源；`sys_dept` 和 `inv_warehouse` 双轨存在时，必须明确谁负责仓库名称、编码、负责人、默认仓库和业务选择范围。
120. 锁屏功能要么升级成服务端会话安全能力，要么在文案上明确只是本机遮罩；解锁失败应进入登录失败计数、账号锁定和登录日志审计闭环，避免门店共用电脑场景下安全预期失真。
121. 字典和参数的缓存刷新要从删除权限中拆出来；刷新缓存、删除数据、导出数据应是三个独立授权动作，避免为了让用户刷新缓存而被迫给破坏性删除权限。
122. 个人中心头像上传要统一成标准 multipart 请求，并和文件元数据/清理策略衔接；用户自助头像这类低风险资料维护不应因为请求头错误或缺少文件追踪而变成不稳定流程。
123. 密码变更必须联动会话治理：个人改密和管理员重置密码后，应按账号清理其他在线 token 或强制重新登录，并在页面和操作日志中明确提示影响范围。
124. 菜单保存不能只校验表单格式：启用页面必须校验组件存在，按钮权限必须校验前后端实现存在，并把缺组件、缺接口、未接入权限作为菜单列表和角色授权树的显式交付状态。
125. 通知公告需要补齐收件范围模型：全局公告要明确标识为全体可见，门店/仓库/角色/用户公告要能定向发布，并让未读数、阅读用户和全部已读都按收件范围计算。
126. 通知公告富文本必须做双端净化：服务端保存前按白名单过滤 HTML，前端展示前统一 sanitize；`/system/notice` 不应整段绕过网关 XSS 过滤，公告发布权限和发布审计也要按全员影响范围收紧。
127. 代码生成导入表需要业务白名单，本地写入也要和运行开关一致：默认排除流程引擎、调度、生成器、备份/归档、流水日志等内部表；`allowOverwrite=false` 时隐藏/禁止自定义路径，开启时必须做目录白名单、路径归一化和覆盖审计。
128. 表单构建要么作为开发工具整体下线，要么补齐服务端模板保存、共享范围、历史版本、预览、导出审计和权限闭环；不能只保留本地复制/下载代码的半交付入口。
129. 仓库授权模型要收口到一套权威来源：废弃 `sys_role_warehouse` 就迁移/清理存量记录，保留角色仓库授权就补齐前端维护、后端保存和所有仓库范围判断的统一读取逻辑。
130. 文件上传要从“保存 URL 字符串”升级为可审计文件生命周期模型：元数据、业务引用、版本、下载日志、删除引用检查和失败提示必须闭环，否则合同、公告、头像和附件都会缺少清理和追溯依据。
131. 内部服务认证不能只靠 `from-source=inner` 请求头：`@InnerAuth` 应接入签名密钥、时间戳、nonce 和重放防护，网关清理外部头只能作为一层防护，不能替代服务端签名校验。
132. API 应用和 Webhook 集成表要么明确下线为预留表，要么补齐管理页、鉴权、限流、白名单、事件目录、投递队列、重试和日志查询；不能只保留空表让第三方集成能力看起来已经存在。
133. 日志保留策略必须接入真实归档流程：操作日志/登录日志清空前应生成归档记录和文件，自动任务按 `audit.retention.days` 处理过期日志，普通删除权限不能等同于不可逆清空全部审计证据。
134. 单据状态审计要先统一表模型和写入来源：当前运行态以 `inv_document_status_log` 和触发器为主，旧/备份口径里的 `inv_status_audit` 若保留就需要迁移或标注归档；权限、前端页面和单据详情时间线也必须指向同一套审计表，否则前端入口、审计接口和历史备份会继续分裂。
135. OA 采购申请状态要先做存量迁移和枚举收口：`received` 这类库存语义状态不能继续停留在 OA 审批主状态中，页面、导出、服务常量和数据库存量值必须统一，否则恢复采购申请入口后会继续显示英文原值并误导流程判断。
136. 定时任务目标白名单要可解释：若要运营配置，就交付白名单表、管理页、启用状态、参数签名校验和审计日志；若只保留代码常量，就在任务页展示当前允许包、Bean/方法预检和“需发版变更白名单”的说明，避免手填目标字符串但关键规则不可见。
137. 权限变更审计要从通用操作日志中拆出来：当前运行库和仓库缺少专用权限变更审计链路，角色菜单、角色用户、用户角色、店铺授权和菜单权限配置变更都应写入 `sys_permission_change_log` 或等价表的 before/after 快照，并提供独立查询、导出和保留策略。
138. 登录安全表要么接入真实流程，要么明确下线：`sys_auth_provider` 和 `sys_login_device` 若保留，应补齐第三方认证入口、认证提供方管理、登录设备记录、设备撤销和信任设备策略；否则不要让数据库暗示已支持 SSO 或设备管理。
139. 个人消息和全局公告要分清模型：`sys_notification` 若保留，应接入消息生产、个人收件箱、未读数、已读状态和业务详情跳转；若当前只交付 `sys_notice` 全局公告，应下线或标注个人消息表为预留。
140. 临时授权要么形成完整委托流程，要么下线预留表：`sys_temp_authorization` 若保留，必须接入申请、审批、运行时权限合并、到期回收和审计；不能让管理员误以为系统已有临时代理授权能力。
141. 自定义报表能力不能只停留在数据表：`sys_report` 若保留，必须有定义管理、参数校验、SQL 安全、数据范围注入、敏感字段权限、执行审计和导出控制；若只交付库存固定报表，应下线或标注该表为预留。
142. 告警规则要形成从指标到通知的闭环：`sys_alert_rule` 若保留，应补齐指标采集、规则评估、持续时间判断、触发/恢复记录、接收人和通知渠道；若不交付，应下线或标注为预留，不要让系统监控看起来已有告警平台。
143. 链路追踪不能只保留空表：`sys_trace_log` 若保留，应接入 traceId 生成/透传、MDC 或 tracing 组件、请求耗时采集、异常状态记录、查询页面和保留策略；若不交付，应下线或标注为预留，避免排障人员误判已有调用链能力。
144. 定时任务高可用要有明确锁模型：`sys_scheduler_lock` 若保留，应接入所有任务执行前的抢锁、续期、释放和失败记录；若依赖 Quartz 集群，应启用 JDBC JobStore；若只支持单实例，应在页面和部署文档中明确，避免多实例重复执行任务。
145. 差旅补贴要么接入完整业务闭环，要么下线预留表：`fin_travel_subsidy` 若保留，应补齐申请、审批、补贴标准、门店/员工权限、工资或财务结算、状态流转和审计；不能让它与薪资里的社保/通勤补贴混在一起。
146. 考勤工资要统一请假、补卡、假期和加班来源：`oa_holiday`、`oa_leave_correction`、`oa_overtime`、`oa_overtime_record` 若保留，应接入页面、审批、工资计算和审计；否则应明确当前工资只按打卡记录推导，避免员工以为有审批单就会影响工资。
147. 采购合同和订单版本要接入采购主流程：`inv_purchase_contract`、`inv_purchase_order_version` 若保留，应支持合同台账、附件、审批、采购单关联、版本快照、变更对比和审计；若只做轻量采购单，应下线或标注为预留。
148. 销售商务和财务结算表要么形成闭环，要么标注预留：`inv_sales_quote`、`inv_sales_contract`、`inv_shipment`、`inv_receivable`、`inv_payable` 若保留，应接入报价、合同、发运、开票、收付款、账龄、门店权限和审计；不能只靠销售单/发货通知替代。
149. 供应商绩效不能只保留评分表：`inv_supplier_score` 若保留，应接入采购到货、质检、退货、延期、订单量和门店范围，生成周期评分并在供应商列表/详情中展示；若不交付，应下线或标注为预留。
150. 仓储执行表要么接入真实作业，要么下线预留：`inv_location`、`inv_pick_task`、`inv_qc_hold`、`inv_defective_stock` 若保留，应补齐库位主数据、拣货任务、质检冻结、残次品处理和状态流水；不能让自由文本库位替代受控库位和仓储作业闭环。
151. 成本事件审计要覆盖所有库存成本变动入口：`inv_cost_event` 若保留，应在采购、销售、退货、调拨、盘点和调整时写入 before/after 成本、来源单据、门店/仓库和操作人，并提供查询导出；不能只在库存流水里留一个当时成本价。
152. 调拨收货要明确是否支持差异：`inv_transfer_receipt` 若保留，应接入调货收货页面、差异数量、拒收/损耗、差异审批、库存入账和状态流水；若当前只支持发货批次全量收货，应下线或标注为预留，避免门店以为可以做差异收货。
153. 售后工单要和销售退货分清边界：`inv_after_sales_order` 若保留，应接入售后类型、处理动作、工作流审批、退换补赔关联和状态流水；若只交付销售退货，应下线或标注为预留，避免把退货单误当成完整售后闭环。
154. 批次主数据不能被手工文本字段替代：`inv_batch` 若保留，应接入入库、调拨、调整、销售、退货和效期预警，形成批次生命周期台账；若只使用 `inv_stock.batch_no`，应下线或标注批次主表为预留。
155. 采购计划、收货单和待检入库记录台账要么接入主流程，要么标注预留：`inv_purchase_plan`、`inv_purchase_plan_detail`、`inv_receipt`、`inv_receipt_detail` 若保留，应从低库存预警生成计划、转采购单并形成收货单/收货明细台账；当前实际使用的 `inv_inbound_record` 也应在采购详情或独立页面展示收货批次、质检人、质检时间和备注，不能只用采购单当前状态替代完整收货/质检闭环。
156. 权限树里的按钮权限必须和源码判断点一致：`system:*:query`、`monitor:online:query`、`monitor:online:batchLogout` 这类无效权限要么接入真实前后端动作，要么停用/删除，避免角色授权误判。
157. 菜单权限数据要增加结构校验：`F` 类型按钮不能作为父级，启用权限码重复必须有明确白名单和业务语义说明；历史调拨 `4109 / inv:transfer:edit` 脏节点当前已清理，但保存链路仍要防止按钮挂按钮、父级停用子级启用和无效重复权限再次写入。
158. 店铺配置权限要分清页面入口和动作权限：`system:userShop:list` 不应同时存在于 `181 店铺配置` 页面和 `1192 店铺配置列表` 按钮，避免角色树出现两个同码节点。
159. OA 待办/已办权限必须和采购申请交付状态一致：当前审批工作台已启用给业务角色，但采购申请仍停用隐藏；要么补齐采购申请入口和流程数据闭环，要么同步隐藏待办/已办、首页采购审批文案和相关接口权限。
160. 调拨主流程必须先做 schema preflight：`inv_transfer_approval_*`、`inv_transfer_status_log`、`inv_transfer_shipment*` 等源码必需表缺失时，应拒绝启用提交、发货、收货、详情和审批配置入口，不能让用户在流程中途才遇到 SQL 缺表。
161. 多账号多店铺验收前必须冻结数据库快照和迁移版本：本报告中采购、盘点、调拨、合同等表多次从“有数据”变成“清空或缺表”，后续每轮页面验证都应先记录精确表计数、schema checksum、运行 jar 版本和前端构建版本，否则页面结论会被环境漂移推翻。
162. 班次和排班模型要么接入考勤/工资主流程，要么下线或标注预留：`oa_shift` 已有标准班、早班、晚班时，必须能在电脑端维护、分配给员工/门店、参与迟到早退和工时计算，否则多店铺排班会停留在数据库孤岛。
163. 当前运行态数据源和审计 SQL 数据源必须先对齐：`/auth/login`、`/system/user/getInfo`、`/system/dept/shop-tree`、`sys_user/sys_user_role/sys_user_shop` 和 `sys_logininfor` 必须指向同一快照，否则普通账号菜单、组织授权和按钮可见性无法做真实运行态判断。
164. 前端页面清单要以运行路由和菜单为权威来源：清理 `index_v1.vue`、`system/dict/detail.vue`、`oa/laborContract/index 2.vue`、`oa/laborContract 2.js` 等未路由/重复视图和 API 副本，保留的副组件必须在页面清单中标明所属主页面和触发入口，避免按文件或按菜单两种验收口径互相打架。
165. 权限矩阵要按“唯一权限码 + 唯一业务语义”治理：库存、调拨、报表、薪资等同权限多入口场景要么拆出明确入口权限，要么在菜单验收矩阵中声明共享语义；不能让启用重复权限码继续混在角色授权树里。
166. 销售制单要前置库存可售性判断：商品选择器至少显示可用库存和可发仓库，零库存商品默认不可选；若业务允许缺货销售，必须有独立预售/缺货状态和补货闭环，不能把无法发货的单据按普通已提交销售单推进到发货通知。
167. 所有成本字段必须做接口级脱敏和写权限控制，不能只靠前端不展示。商品列表/详情、商品新增/编辑/导入、供应商详情、采购商品选择、采购单价/金额字段、采购退货单价/金额字段、库存列表/详情/汇总、库存调整、销售退货成本回写、库存流水、库存盘点详情、调拨详情、报表汇总和导出接口都应统一走成本权限判断；无 `inv:cost:view` 或独立成本写权限时，响应体、Excel、详情弹窗、网络数据和写入请求中都不应包含或改变采购价、成本价、进价、总成本或毛利字段。
168. 字典类型和字典项权限要拆开：维护系统字典类型、维护某类字典项、只读查看字典项、刷新缓存和导出应是不同权限点，不能全部复用 `system:dict:*`。
169. 字典项要按 `dict_type + dict_value` 做唯一约束和保存前校验；当前库虽然没有重复值，但后续页面新增/编辑不能允许同一类型下出现两个相同键值。
170. 内置字典类型要增加保护：被代码硬编码引用的 `sys_normal_disable`、`sys_yes_no`、`sys_oper_type` 等不能直接改编码或停用，页面上应标注“系统内置”，只允许改名称/备注或通过受控迁移变更。
171. 调度日志必须按稳定任务 ID 关联：`sys_job_log` 应保存 `job_id`，任务行进入日志副页面应按 `job_id` 精确过滤，任务名称、任务组和调用目标只作为执行时快照，避免改名、重建或同名任务导致日志串联或断档。
172. 通知公告管理页的筛选模型要和数据库字段对齐：`noticeType/status/createBy/noticeTitle` 在前端初始化、搜索表单、重置逻辑和 mapper SQL 中必须保持一致，特别是关闭公告要能被筛出。
173. 顶部公告“全部已读”必须由服务端按当前用户全部可见公告处理，不能只提交前端下拉里的 5 条 ID 后把未读数本地清零；若只处理当前列表，按钮文案和未读数刷新逻辑必须明确。
174. 公告阅读用户明细要拆出独立权限并做手机号脱敏，列表权限只能看公告本身和阅读人数，不能默认看到跨组织人员联系方式和阅读轨迹。
175. 系统监控权限要从业务角色中剥离：在线用户强退、调度任务新增/修改/删除/启停/执行一次和监控外链访问默认只给超级管理员或专门运维角色，总经理和普通角色不应默认持有。
176. 在线用户列表要服务端分页和最小化返回：后端按页读取 token、按权限脱敏会话编号/IP/登录时间，前端只保存当前页数据，避免本地分页暴露全量在线会话。
177. 系统工具权限要按父级状态和角色边界一起收口：父菜单停用时，子菜单、按钮、隐藏动态路由、外链和后端权限都应不可用；`tool:*` 不应默认授给普通角色或总经理角色，恢复交付时也应放入专门开发/运维角色。
178. 客户信用字段要么接入销售/回款真实业务闭环，要么从桌面端弱化展示：`credit_used` 不能既提示由业务单据维护，又允许客户档案接口直接写入；授信调整必须有独立权限、审计和计算来源。
179. 岗位管理要按基础主数据治理：岗位编码被调拨审批、薪资、合同和用户绑定引用后不得被普通业务角色随意改动；岗位新增、修改、删除、导出应拆给专门人事/平台角色，并在保存前显示依赖影响。
180. 角色管理要从业务角色中剥离：角色新增、菜单授权、数据权限、分配用户、启停、删除和导出都是平台权限治理动作，应拆给安全管理员/平台运维并记录 before/after 差异，不能默认授给普通角色或总经理。
181. 部门组织树写权限要从业务角色中剥离：门店、仓库、公司和集团节点的新增、修改、迁移父级、停启、排序和删除都应由组织管理员/平台运维维护，并在保存前预览受影响的用户授权、库存组织、数据权限和业务单据范围。
182. 店铺/仓库授权写权限要从经营角色中剥离：`system:userShop:edit` 应默认只给账号管理员或组织授权管理员；若允许总经理代管，也必须限制只能维护本授权范围内的普通账号，禁止操作超级管理员/安全管理员，并把授权前后差异写入专用权限变更日志。
183. 固定资产维修上报状态机要和页面/导出一致：没有驳回、取消、草稿保存和额度反冲能力时，不应在筛选、详情和 Excel 中展示这些状态；若要保留这些状态，必须补齐对应按钮权限、后端接口、状态审计和额度流水反冲。
184. 工资详情权限必须按“本人”和“管理”拆分：没有工资列表/管理权限的账号只能查询自己的工资详情，不能仅凭同店组织范围和 `oa:salary:query` 查看他人基本工资、扣款、加班费和实发工资。
185. 工资计算后必须有确认、调整和锁定闭环：草稿工资可以调整，确认工资应防止普通重算覆盖，导出前要让用户明确当前导出的是草稿还是已确认工资。
186. 商品导入的分类匹配要和页面选择同口径：导入不能因为当前组织没有同名分类就自动创建本地分类；应按分类编码/完整路径匹配集团共享分类，未匹配时进入错误行或预览确认。
187. 采购收货和质检的库存入账时点要讲清楚：当前收货只生成待检入库记录，库存要等质检合格或让步接收后才增加；页面文案、成功提示、列表状态和报表口径都应避免暗示“收货提交即增加可用库存”。
188. 销售单页面要展示履约进度：后端已经计算总数量、已发货数量和待发货数量，电脑端销售列表、详情和导出应同口径展示，部分发货时要让用户一眼看到已发和待发。
189. 公告删除必须用服务层事务保护公告本体和已读记录清理：删除公告、清理 `sys_notice_read`、写删除审计或软删除状态应同一事务完成，不能先清阅读轨迹再独立删除公告。
190. 定时任务“启停”和“立即执行”要拆权限：`monitor:job:changeStatus` 只控制状态开关，手动触发后台任务应使用独立 `monitor:job:run/execute` 权限，并在执行前展示任务目标、当前状态和影响范围。
191. 劳动合同事件哈希要覆盖详情页展示的关键语义字段：事件摘要、下载文件类型、操作者快照、备注、文档哈希、前序哈希和请求标识都应纳入统一 payload；历史事件应标注旧版哈希覆盖范围。
192. 薪资档位删除要做引用保护：被 `sys_user_salary_scheme` 或 `sys_role_salary_scheme` 引用的档位不能直接删除；应提供停用、迁移绑定和影响预览，工资计算前也要巡检悬空 `item_id`。
193. 个人中心资料保存后要刷新当前用户展示状态：昵称、手机号、邮箱、性别、头像和顶部用户菜单应来自同一份最新 `/profile/getInfo` 口径，避免“保存成功但页面仍旧”的错觉。
194. 用户和角色状态开关要和后端权限一致：只读角色看到纯文本状态，具备编辑或独立启停权限的角色才显示 switch；不要让列表权限用户看到可点但会失败的启停控件。
195. 固定资产配置要建立唯一资产身份：同店铺同商品不能重复配置；若同商品有多台设备，应使用资产编号、序列号或资产实例建模，并让额度计算按资产实例明确累计。
196. 操作日志要拆分列表摘要和详情数据：列表接口不得批量返回 `operParam/jsonResult/errorMsg`，详情应走独立接口和独立权限，并在详情层做字段级脱敏。
197. 主框架主题参数要明确权威来源：`sys.index.skinName/sys.index.sideTheme` 要么接入桌面端启动配置并可验证生效，要么从参数页移除或标注为不控制当前桌面端主题。
198. 用户组织授权默认值要闭环：`sys_user_shop.is_default` 要么成为可设置、可返回、可预选的真实默认组织，要么从运行库和页面说明中移除/标注预留，避免管理员误以为默认门店已经生效。
199. 调度日志权限必须独立于任务配置权限：查看、导出、删除、清空调度日志应使用 `monitor:jobLog:*` 这类独立权限，不能复用 `monitor:job:*`；清空全部日志要作为高危审计动作单独授权和记录。
200. 供应商引用要从名称字符串升级为稳定 ID；已被商品或历史单据引用的供应商改名必须走迁移/合并流程，不能在普通编辑里静默改名。
201. 供应商编码要和客户编码一样定义身份规则：若作为业务身份，应非空唯一并自动生成；若只是选填展示字段，应从精确筛选和对账口径中弱化。
202. 仓库管理路由守卫要覆盖完整 `/cangku/*` 主流程：商品、分类、供应商、采购、采购退货、库存、调拨和调拨记录都应在缺少已验证组织时统一进入选择组织页，并按页面语义校验仓库或门店类型。
203. 系统参数读取要拆公开白名单和后台管理权限：任意 `configId/configKey` 查询必须要求 `system:config:query`，登录页、密码策略等前台必要配置应走专用白名单接口，且不要向普通账号返回明文初始密码。
204. 仓库列表接口要默认按授权过滤：无 `purpose` 时也只能返回当前用户可选仓库，采购、调拨、发货、移动端等组件必须传明确业务目的；全量仓库查询应只开放给具备组织管理权限的后台场景。
205. 用户导入要形成账号配置闭环：模板、预览和保存服务应支持角色、岗位、店铺/仓库授权和默认组织，或在导入成功后强制进入批量授权向导；不能只写 `sys_user` 后提示导入成功。
206. 用户详情和管理端用户 DTO 必须剔除密码哈希：认证内部查询和管理端详情查询要拆开，`/system/user/{userId}`、列表、导出、角色授权和店铺授权相关响应都不得返回 `password` 字段。
207. 个人中心必须使用专用安全 DTO：`/system/user/profile` 只返回页面实际展示和编辑需要的资料字段，任何登录账号的浏览器响应体都不得包含 `password` 哈希、内部权限字段或其他认证材料。
208. 桌面端启动用户信息接口必须最小化返回：`/system/user/getInfo` 不得原样序列化 `LoginUser.sysUser`，只返回前端路由、权限、头像、昵称、部门和密码策略提示所需字段，密码哈希必须留在服务端内部。
209. 超级管理员角色成员维护必须后端强保护：角色分配/取消用户接口、授权用户列表和直连副路由都要拒绝 `roleId=1`，除非走独立安全管理员流程并具备二次确认、审计和至少保留一个超级管理员账号的约束。
210. 用户分配角色同样必须保护超级管理员角色：普通用户授权页不得返回或提交 `roleId=1`，移除/新增最高权限角色必须走独立安全管理员流程，并校验至少保留一个启用超级管理员账号。
211. 顶部菜单搜索不要用 `v-html` 渲染可配置菜单标题/路径：高亮应基于文本节点或先转义后拼接，菜单后端字段和初始化脚本也要做 XSS 校验，避免全局搜索成为绕写数据的展示点。
212. 菜单路由参数必须保存前校验和使用时容错：`sys_menu.query` 只能为空或合法 JSON object，顶部菜单、侧边栏、顶部搜索和依赖 query 的库存入口都要在解析失败时给出明确提示，不能让一个配置错误破坏主导航。
213. 外链打开方式要统一：顶部菜单、顶部搜索、侧边栏和业务预览都应使用 `noopener/noreferrer`，并把外链健康状态、环境地址和权限边界一起纳入菜单验收。
214. 标签页身份要区分业务上下文：店铺授权、个人中心 tab、代码生成返回页码、库存入口等带 query 的路径应明确哪些需要独立标签、哪些只是临时参数；新增、选中、刷新、关闭、关闭左右和持久化都要使用同一套 `fullPath` 或白名单 query 身份策略。
215. 布局设置要和真实桌面导航能力一致：如果只支持顶部导航，就移除或禁用左侧/混合菜单选项；如果支持三种导航，`navType` 必须能保存、刷新恢复，并驱动 `Layout/Navbar/Sidebar/TopBar` 的实际渲染。
216. 店铺授权页必须在 query 切换时重置目标用户和授权状态：连续从用户 A 进入用户 B 的 `/system/shop?userId=...` 时，URL、表格当前行、右侧授权对象和保存请求 `userId` 必须保持一致，并在未保存切换时给出明确确认。
217. 电脑端导出确认要统一：所有导出按钮应使用同一套范围、筛选、敏感字段和外发提醒文案，工资、考勤、客户、供应商、库存、任务配置等导出不得只用泛化确认或无确认直接下载。
218. 用户导入结果必须按纯文本或结构化列表展示：后端不要拼接含 Excel 原文的 HTML，前端不要用 `dangerouslyUseHTMLString` 渲染导入返回消息，恶意账号名校验失败时也只能显示为文本。
219. 导入模板下载权限要和导入动作一致：用户、商品等模板接口若只对有导入权限的人可见，后端也必须要求同一权限；若允许所有登录用户下载模板，前端要明确展示这个产品策略。
220. 退货确认和取消要展示单据身份与库存影响：销售退货、采购退货这类会增减库存的按钮不能只弹泛化确认，应在最后确认框展示退货单号、原单号、客户/供应商、组织、金额、数量和库存方向。
221. 库存盘点确认要展示盈亏摘要：确认前应汇总盘盈/盘亏行数、数量、净调整和最大差异明细，让用户在写库存流水前能核对本次调整影响。
222. 采购质检确认要展示待检数量和结果影响：合格/让步会增加库存，不合格会回退待检收货数量，最后确认框必须展示仓库、待检批次、商品行数和数量摘要。
223. 生成发货通知前要展示待发摘要：客户、金额、商品行数、总数量、已发和待发数量都应进入确认框，成功后返回发货通知号并引导到发货通知列表。
224. 销售取消要补齐原因和审计上下文：取消前展示客户、金额、数量、状态和门店，要求填写取消原因，并把取消原因、操作人、取消时间写入销售单或业务事件，让状态审计能区分真实取消动作。
225. 发货通知要支持部分发货后的业务关闭：当通知进入 `delivering` 后，除了继续发货，还应能关闭或取消剩余未发数量，并记录关闭原因、剩余数量和操作人，避免缺货或客户取消时单据长期挂在中间态。
226. 出库记录要补发货通知级关联键：发货通知执行生成的出库台账应保存 `notice_id/notice_no/notice_detail_id`、实际 `warehouse_id` 和库存流水关联，保证详情、导出、库存流水和审计能从同一条链路回到具体通知明细。
227. 销售出库库存流水要补来源明细：发货通知出库写 `inv_stock_log` 时不能只写销售单号，应写通知 ID、通知明细、出库记录或统一来源字段，并在库存流水页面/导出展示可回跳的来源单据。
228. 采购退货详情要展示库存扣减台账：确认退货后应在详情和导出中展示库存流水或出库明细，包含商品、仓库、数量、成本、操作人和时间，并支持从采购退货明细回到对应库存流水。
229. 销售退货详情要展示退货入库台账：确认退货后应在详情和导出中展示库存流水或入库明细，包含商品、门店/仓库、数量、成本、操作人和时间，并支持从销售退货明细回到对应库存流水。
230. 采购质检入库库存流水要绑定待检入库记录：采购质检通过或让步接收写 `inv_stock_log` 时，应保存 `inbound_id`、采购明细 ID、质检结果或统一来源字段，并在采购详情、待检入库台账和库存流水之间支持回跳。
231. 库存盘点流水要绑定盘点明细：盘盈/盘亏写库存流水时，应保存盘点明细 ID、账面数、实盘数和差异类型，支持从库存流水回到具体盘点明细行。
232. 手工库存调整要形成独立凭证：库存调整应生成调整单号或调整记录，库存流水写入 `business_id/business_no`，并提供调整记录详情、导出和从流水回跳的入口。
233. 调拨库存流水要绑定发货批次：调拨出库和收货入库写库存流水时，应保存发货批次 ID/编号、批次明细 ID 和调拨明细 ID，支持从库存流水回到具体 `TS` 发货/收货批次。
234. 报表中心要支持导出和明细钻取：汇总指标应能跳到对应库存、采购、销售或库存流水明细，低库存预警应能导出，并按 `inv:cost:view` 控制成本字段。
235. 单据状态审计要覆盖调拨状态流水：`/audit/list` 不能只查 `inv_document_status_log`，应同步写入或聚合 `inv_transfer_status_log`，让调拨提交、审批、发货、收货与采购、销售、盘点等状态变化使用同一套审计入口、权限和组织范围过滤。
236. 退货取消要补齐取消原因和审计事件：采购退货、销售退货取消时应要求填写原因，保存取消人、取消时间和原因，并让状态审计 action 区分 `cancel`，不能只留下泛化 `status_update`。
237. 退货备注要形成保存和可见闭环：采购退货、销售退货表单允许填写备注时，新增和编辑 SQL 都应更新 `remark`，详情页和导出也应展示备注；如果备注承担取消原因或异常说明，应拆出结构化字段和状态事件。
238. 库存流水列表不要逐行补查商品详情：既然日志接口已返回商品编码和名称，前端应直接使用行数据；需要商品详情时用显式入口和独立权限，避免 N+1 请求和成本字段响应叠加。
239. 库存流水批次和库位字段要统一写入契约：页面展示和筛选批次、效期、序列号、库位时，采购、销售、退货、调拨、盘点和调整都应按同一规则写入这些字段；暂不支持时应隐藏或标注限制。
240. 库存主页面要统一当前、锁定和可用库存口径：列表、详情、调整确认和导出都应展示或解释当前库存、锁定库存、可用库存，状态和扣减预览按可用库存计算时要让用户看见原因。
241. 固定资产年度申报比例要从资产明细表单中拆出，按店铺+年度作为额度策略单独维护，并提供独立权限、确认文案、原因和变更历史。
242. 固定资产额度流水要可查可导出：已用额度、透支额度和特殊追加额度都应能钻取到 `oa_fixed_asset_quota_ledger` 明细，并关联维修单、操作人和时间。
243. 客户引用关系要使用稳定客户 ID：销售单、销售退货和发货通知保存 `customer_id` 与客户名称快照，删除/停用检查按 ID 命中历史单据，避免改名或同名客户导致误拦截或漏拦截。
244. 供应商状态要贯穿采购制单：供应商暂停、终止或停用后，采购商品选择器和后端保存/提交都应阻断继续采购，并在状态变更时展示受影响商品、草稿采购单和处理选项。
245. 代码生成同步表结构要做差异预览和独立授权：同步前展示新增、删除、覆盖字段和保留配置，确认后再写 `gen_table_column`，并把该动作从普通编辑权限中拆成 `tool:gen:sync`。
246. 用户保存岗位必须以后端校验为准：新增、编辑、导入和批量授权都要拒绝停用岗位；已有用户绑定停用岗位时列表/详情要提示，并提供迁移到启用岗位的处理入口。
247. 用户保存和分配角色必须以后端校验为准：新增、编辑、授权、导入和批量授权都要拒绝停用角色；停用角色前展示绑定用户数和影响范围，停用后角色键不得再进入前端 `roles` 集合。
248. 部门/组织停用必须做影响预检：停用前列出主部门用户、店铺授权、角色数据范围、库存单据和薪资考勤合同引用；有绑定时默认走迁移/归档向导，用户保存和导入后端也要拒绝停用主部门。
249. 调度日志要补保留和归档生命周期：`sys_job_log` 清理不能直接 `truncate` 正式表，应按保留期、任务/时间范围和高危确认执行，清理前生成归档记录或文件并保留清理审计，让后台任务事故仍可追溯。
250. 薪资方案删除必须先做影响预检：已有员工绑定、角色绑定、合同快照或工资记录引用时默认只能停用，确需删除要先迁移绑定并展示受影响员工、门店、角色和档位数量，后端不能直接级联清掉 `sys_user_salary_scheme/sys_role_salary_scheme`。
251. 劳动合同内置模板要版本冻结：`built_in=Y` 模板默认只读，修改通过复制新版或版本升级完成；停用、替换文件或改关键字段前必须展示引用合同数、状态分布、最近使用时间和迁移方案，后端也要阻断普通保存接口直接改内置模板。
252. 企业章要随合同发送版本冻结：发送时保存章 ID、章名、章文件 URL、章图片 hash 和章版本，签署归档必须使用冻结章；替换或停用企业章前展示待签/已签/作废合同影响数量，有待签引用时禁止直接替换或要求重新发送合同。
253. 操作/登录日志要保存稳定主体和组织上下文：新增日志以 `user_id` 为主，同时写入用户名快照、角色摘要、主部门、当前选择组织、会话/设备和客户端信息；列表和导出支持按用户 ID、组织和角色过滤，避免账号改名或同名复用后审计断链。
254. 密码有效期要有真实执行策略：`sys.account.passwordValidateDays` 若表示强制过期，过期账号只能进入改密页且其他业务操作应被阻断；若只是提醒，应改名和改文案，并持续展示待改密状态。启用前要提供过期账号影响预览。
255. 权限和组织授权变更必须联动在线会话：角色菜单、角色成员、用户角色、数据权限和店铺/仓库授权保存后，应按受影响用户刷新或清理所有 `login_tokens:*`，并在页面提示已处理的在线会话数量，避免撤权延迟到 token 自然过期。
256. 业务文件要从公开白名单中拆出：劳动合同模板、企业章、合同附件等不能保存为长期 `/file/public/**` 直连 URL，应进入私有文件域，通过业务下载接口或短期签名 URL 按登录用户、组织、角色和引用关系校验，并记录下载/预览日志。
257. 富文本图片上传要统一选择和粘贴路径：粘贴图片也必须走同一套类型、大小、权限失败和错误提示逻辑，后端按公告图片场景限制允许类型/大小，避免一条路径 5MB、一条路径通用 50MB 的体验差异。
258. 商品图片字段要统一页面和批量链路：主图、外包装、干茶、茶汤、叶底、补充图片在表单、详情、导入模板、普通导入、导出和库存预览中必须是同一字段集；若主图只能系统上传生成，应明确从 Excel 链路中排除并提供替代维护入口。
259. 登录页记住密码要改为只记账号或安全会话：生产环境不得把可逆密码写入 JS 可读 Cookie；共享门店电脑切换账号、退出登录和选择组织时应清理保存信息，并通过 HttpOnly/Secure/SameSite 会话或浏览器原生密码管理实现长期登录。
260. 文件删除要返回真实结果并区分“删除对象”和“解绑表单引用”：后端必须检查对象是否存在、是否属于当前存储域、是否真实删除成功和是否仍被业务引用；前端不能在删除失败或不确定时直接移除表单 URL，公开业务附件还需要孤儿文件巡检和清理日志。
261. OA 工资导出要跟随当前视图范围：导出“我的”时只导出当前用户记录；导出“全部”时必须同时具备管理列表/导出权限，并在确认框展示组织、月份、范围和记录数，避免用户在个人视图误导出全部工资。
262. 菜单缓存配置要做可执行校验：`is_cache=0` 的页面必须让后端 `route_name` 与 Vue 组件 `name` 能被 `<keep-alive>` 命中；不需要缓存的页面明确标为“不缓存”，菜单保存和发布前脚本要输出失效缓存页面清单。
263. 角色添加用户弹窗要明确跨页选择规则：支持跨页选择时保留 `row-key/reserve-selection`、展示已选用户清单和清空入口；不支持时必须写明“仅当前页有效”，提交确认列出即将授权的账号和角色。
264. 用户启停要联动在线会话治理：禁止普通后台流程停用当前登录用户；停用任意用户时按 userId 清理其所有在线 token，并在确认和结果中展示受影响会话数，避免“账号已停用但旧会话仍可操作”。
265. 考勤“今日状态”要和唯一性规则一致：如果一个账号每天只能打一次卡，卡片状态必须跨组织查询本人今日记录并显示已打卡组织；如果允许按门店/仓库分别打卡，后端查重、数据库唯一约束、工资计算和导出都要纳入 `shop_dept_id`。
266. 角色数据范围表要和 `data_scope` 保持强一致：非自定义范围保存后清空 `sys_role_dept`，自定义范围必须选择至少一个有效部门；前端切换到自定义时不要无提示复用历史 checkedKeys，应显示“沿用历史范围/清空重选”的明确选择。
267. 薪资员工绑定保存必须服务端校验门店关系：`shopDeptId` 要在当前操作者可维护范围内，且目标员工必须属于该门店/仓库主部门或 `sys_user_shop` 授权；所有校验通过后才能删除旧绑定并写入新绑定，避免前端筛选被构造请求绕过。
268. 劳动合同列表接口必须改用摘要 DTO：列表只返回表格需要的合同摘要字段，身份证、合同文件 URL、证据 hash、签署 IP/User-Agent、第三方签约 URL 和回调载荷等高敏字段必须放到详情、文件下载或证据链接口里按独立权限返回，并默认脱敏。
269. 固定资产维修详情按钮要真正调用详情接口：电脑端“详情”应受 `oa:fixedAsset:repair:query` 控制，并请求 `/oa/fixedAsset/repair/{repairId}`；列表与详情 DTO 拆开，列表只返回摘要字段，故障说明、附件、额度快照和审计信息放到详情接口。
270. 固定资产配置归属变化要双边重算额度：如果允许编辑店铺，保存时必须同时重算原店铺和新店铺的资产总额、年度额度和月度释放额度；更稳妥的做法是禁止编辑归属，要求通过停用/删除后在目标店铺重新新增，并把额度影响写进确认文案和审计记录。
271. 固定资产商品选择器要兑现“名称或编码”搜索：前端应传统一 `keyword` 或同时传 `productName/productCode`，后端支持名称模糊和编码精确/前缀匹配；暂不支持编码时应修改 placeholder，避免用户按编码搜索资产商品却返回空结果。
272. 固定资产配置查询权限要接到真实详情链路：配置页详情或编辑前加载应调用 `/oa/fixedAsset/config/{configId}` 并受 `oa:fixedAsset:config:query` 控制；列表和详情 DTO 拆开，列表只返回摘要，完整资产配置、额度策略和变更记录放到详情接口。
273. 固定资产维修待确认数量要用后端总量：维修页顶部卡片应调用独立统计接口或由列表接口返回 `pendingConfirmTotal`，按当前组织范围统计所有待确认维修单；若只统计当前页，应在文案中明确“当前页待确认”并提示受搜索和状态筛选影响。
274. 固定资产配置删除要做引用预检和归档：已有维修单或额度流水引用时禁止物理删除，改为停用/退役，并保存配置快照或 `config_id`；删除/退役前展示受影响维修单、额度流水和年度额度变化。
275. 固定资产配置金额和比例要以后端校验为准：保存接口应校验数量、单价、金额和年度比例范围，拒绝负资产、超范围比例和金额不一致；数据库层也应增加约束，额度重算前不得接受异常资产总额或比例。
276. 用户组织授权默认值必须保持单一：非管理员部分保存时要识别保留授权中的默认组织，不能再给本次提交第一项自动写默认；默认组织修改应显式选择并由后端事务保证同用户只有一个 `is_default=Y`。
277. 用户组织授权查询要最小化返回：非管理员只能拿到可编辑组织 ID 和不可编辑数量，不应返回不可编辑授权的具体 `deptId`；批量接口也要按操作者范围裁剪或返回脱敏结构，完整授权 ID 仅开放给超级管理员/全量授权管理员。
278. 代码生成导入保存必须具备后端幂等和唯一约束：`gen_table.table_name`、`gen_table_column(table_id,column_name)` 要防重复，导入保存接口排除已导入表并去重；生成、同步和预览尽量按 `tableId` 操作，避免同名表多份配置导致取数不确定。
279. 在线用户搜索要明确精确还是模糊：如果页面是自由输入搜索，后端应支持 IP/账号片段匹配并服务端分页；如果只支持精确匹配，输入框和空结果提示必须写明“完整登录地址/完整登录账号”，避免运维按常规模糊查询误判无人在线。
280. 定时任务状态开关要跟随启停权限：列表开关按 `monitor:job:changeStatus` 显示或禁用，无权限时改为只读状态标签；启停确认框应展示任务名、调用目标、当前状态和目标状态，避免只读角色看到可点开关或误以为后台任务已经变更。
281. 日志状态字典要按业务语义拆分：登录日志、操作日志、调度日志分别使用独立状态字典，或统一页面字典、后端导出转换和数据库字典；同一状态值在列表、筛选、详情和导出中不能一处显示“成功/失败”、另一处显示“正常/异常”。
282. 登录日志解锁要改成明确的状态变更接口：不要用 GET 和路径用户名执行解锁，改为 POST/DELETE 请求体传 `userName` 或 `userId`，返回“已解锁/未锁定/用户不存在”的结构化结果；同时统一账号名格式校验或对所有路径参数做编码，避免特殊账号名破坏解锁链路。
283. 日志导出确认要展示完整筛选摘要：登录日志至少展示登录地址、账号、状态、时间；操作日志展示 IP、模块、操作人、类型、状态、时间；调度日志展示任务名、任务组、执行状态、时间，并尽量展示导出记录数和把筛选摘要写入导出操作日志。
284. 日志删除确认要展示被删对象摘要：删除前显示数量和前几条日志的账号/IP/模块/任务名/状态/时间，超过阈值要求二次确认；删除审计里保留被删日志摘要和数量，优先改为软删除或归档删除，避免只靠一串 ID 执行不可逆删除。
285. 系统基础页导出确认要展示完整筛选摘要：用户、角色、岗位、参数、字典类型和字典项导出前必须列出所有生效筛选条件、导出记录数和敏感字段提示，不能只展示一个主字段；导出操作日志应同步保存筛选摘要，便于追溯 Excel 的真实范围。
286. 部门负责人和联系方式要能在电脑端组织树中直接核对：列表、搜索、导出至少覆盖负责人、电话、邮箱；如果负责人会驱动审批或通知，应绑定真实启用账号和组织授权，不应继续只靠自由文本。
287. 系统基础资料导出要保留备注治理信息：角色、岗位、字典类型、字典项和参数导出都应补备注列；角色、岗位列表也要能直接查看备注，避免治理说明只能留在编辑弹窗或数据库里。
288. 通知公告正文权限要拆清楚：顶部公告详情应提供面向所有可见公告接收人的只读接口，后台管理列表不要批量返回富文本正文；`system:notice:query` 只控制管理场景，不应阻断普通业务账号阅读公告正文。
289. 代码生成元信息要做服务端格式校验：实体类名、包路径、模块名、业务名、功能名和生成路径都应有明确白名单、长度限制和错误提示；生成预览、zip 下载、本地生成和菜单 SQL 输出前复用同一套校验，避免非法路径、非法权限码或不可编译代码包流出。
290. 选择组织预览模式要和真实会话隔离：演示组织不能写入 `selected_dept_*` 或触发真实 API 请求头，生产构建应关闭 `preview=1` 白名单；多账号验收前统一清理本地组织上下文并重新从授权树验证。
291. 用户管理要补账号活跃度治理入口：列表、筛选、排序和导出确认中增加最后登录时间、最后登录 IP、从未登录和 N 天未登录条件；停用、删除或回收账号前展示最近登录、在线会话、角色和组织授权摘要，避免长期闲置账号只能靠人工导出排查。
292. 用户账号和昵称文案要统一：`userName` 全端统一叫“登录账号/用户账号”，`nickName` 全端统一叫“用户昵称/显示名称”；列表、详情、搜索、导出确认、Excel 表头和日志主体字段使用同一口径，避免管理员在授权、改密、停用和删除时认错账号。
293. 用户批量删除要前置保护受控账号：列表复选框禁选超级管理员、当前登录用户和安全管理员等保护账号，批量确认展示账号摘要并提示被过滤/阻断对象；后端继续兜底拒绝，但前端不要让管理员进入必然失败的删除提交。
294. 用户导出应提供账号配置台账：除基础资料外，补充角色、岗位、授权门店/仓库、默认组织、配置状态、最近登录和保护账号标记；必要时拆成多 Sheet，保证离线审计也能完整核对多账号多店铺授权闭环。
295. 用户管理受保护账号要使用统一交互模型：超级管理员、当前登录用户和安全管理员等账号应在选择列、顶部修改、状态开关、改密、角色分配和店铺授权入口统一禁用或提示原因；后端继续兜底拒绝，但前端不要让管理员进入必然失败的维护流程。
296. 店铺授权页的权限依赖要自洽：不要让 `system:userShop:*` 页面依赖 `system:user:list` 才能加载用户；应提供店铺授权候选用户接口，或在角色配置中显式联动所需用户列表权限，并限制返回字段到授权所需范围。
297. 用户配置状态要纳入岗位维度：列表和后端 `setupStatus` 增加 `post_count/missingPost`，或按角色、部门类型定义岗位是否必填；“已完成”不能只代表有角色和组织授权，还要和账号实际岗位要求一致。
298. 用户详情要形成账号画像闭环：详情抽屉增加配置状态、角色数量、岗位数量、授权门店/仓库数量、默认组织和授权摘要；多组织账号支持展开全部或跳转到店铺授权页，避免单账号排查仍要跨页面或查库。
299. 用户分配角色页要展示授权影响：角色表增加状态、数据范围、人读说明、菜单数量、已授权用户数和备注；提交前列出新增/移除角色和高风险角色提示，全量数据或系统维护类角色应二次确认并进入权限变更审计。
300. 角色侧取消授权要保护最后角色：单个/批量取消前展示每个账号的角色数量和“取消后将无角色”标记；后端禁止把启用账号删到 0 角色，或要求先选择替代角色/停用账号，并把批量取消影响写入权限变更审计。
301. 超级管理员角色要使用统一受保护交互：列表选择列、顶部修改/删除、状态开关、行内操作和授权副路由都应一致禁用或展示原因；后端继续兜底拒绝，但前端不要把内置最高权限角色放进必然失败的普通维护流程。
302. 角色列表和导出台账要补授权影响摘要：展示数据范围、菜单数量、已授权用户数、启用用户数、空权限标识和高风险标签，让管理员不用逐个打开弹窗或查 SQL 才能完成角色矩阵盘点。
303. 部门编辑迁移父级要复用新增父级校验：父级存在、启用、类型层级合法；父级候选过滤或禁选停用节点，不能通过编辑把启用组织挂到停用父级或隐式重新启用祖先。
304. 系统基础资料选择框接口要和页面查看权限一致：岗位、字典、角色等候选列表至少使用对应查询权限或专用只读权限，业务场景候选接口只返回启用项和必要字段，避免无基础资料权限账号直连枚举全量配置。
305. 新增定时任务要把默认暂停策略前置说明：弹窗和成功提示明确“保存后为暂停状态，需要手动启用”，或提供受控的保存后启用选项；前端提交状态值应与后端落库规则一致。
306. 系统参数值要做敏感分级展示：初始密码、密钥、黑名单、密码策略等参数在列表和普通导出中默认掩码，完整查看或完整备份使用独立权限、二次确认和审计；初始密码优先改成一次性临时密码或不可回读策略。
307. 内置系统参数要锁定运行契约字段：`configType=Y` 的参数禁止修改 `configKey` 和内置标记，只允许按 schema 修改值；如需迁移 key，必须走带影响预览、兼容旧 key、缓存刷新和回归测试的专门流程。
308. 内置参数删除保护不能依赖可编辑字段：删除时应基于不可变保护清单、数据库约束或代码常量判断，普通编辑接口不得把 `configType=Y` 改成 `N`；内置参数下线必须走安全管理员审批和影响预览。
309. 菜单树保存要强制父子类型规则：按钮节点不能作为父级，前端上级菜单选择过滤按钮，后端按 `parentId` 复查父级类型并阻断非法层级；菜单发布脚本同步检查 `F` 父级、重复权限码和父级停用子级启用。
310. 个人资料联系方式校验必须以后端为准：个人中心、用户管理、导入和移动端复用同一套 DTO/校验器，允许为空时只允许空或合法格式，必填时在所有入口和配置状态中统一判定，避免前端绕过后写入无效手机号或邮箱。
311. 公共 401 页要接入真实权限失败闭环：路由无权限和接口 403 / 无权限业务码统一进入中性无权限页，展示来源、所需权限、当前账号/组织和联系管理员引导；会话过期与页面不存在要分别走登录过期和 404，返回动作不要默认回到失败页面。
312. 首页快捷入口要以真实权限过滤结果为准：过滤为空时展示“暂无可用功能/账号待配置”的空态和联系管理员引导，不得回退展示全部预置入口；快捷卡片、建议项和摘要卡统一按当前菜单、组织类型和模块交付状态生成。
313. 定时任务顶部日志入口要明确语义：作为全量日志入口时直接跳 `/monitor/job-log/index/0` 并命名为“全部日志”；作为行级入口时必须先选择一条任务、未选禁用或提示，且 `handleJobLog` 对空 `row` 做兜底，避免工具栏点击触发前端异常。
314. 店铺授权页要在列表上下文变化时锁定目标用户：搜索、重置、翻页或刷新后，如果当前用户不在新结果集中就清空右侧授权树并禁用保存；右侧面板和保存确认必须展示目标账号、昵称、用户 ID、当前授权摘要和 before/after 差异，避免把上一用户授权误保存。
315. 考勤打卡写入能力要有明确权限边界：若所有登录用户都能网页端打卡，应把个人考勤入口改成真实个人入口；若按角色控制，则新增并接入打卡/签退写权限，前端按钮和后端 `checkIn/checkOut` 都不能只靠登录态放行。
316. 客户和供应商编辑权限要显式包含详情读取：编辑按钮要同时判断 `edit + query`，或详情接口允许 `edit` 作为编辑前置读取；权限说明必须写清“修改需要读取详情”，避免配置出有编辑按钮但详情接口拒绝的角色。
317. 字典项删除日志要使用真实业务模块标题：`SysDictDataController.remove` 应记录为“字典数据/字典项数据”删除，并回归字典项新增、修改、删除、导出的标题、操作类型、URL 和参数；操作日志筛选和导出不能把字典项删除混入字典类型删除。
318. 顶部公告要补只读历史入口：普通登录用户应能从铃铛进入“公告中心/查看更多”，分页查看自己可见的公告、筛未读和打开详情；这个入口不能复用后台 `system:notice:list` 管理权限，否则门店/仓库账号只能看最近 5 条快照。
319. 锁屏刷新恢复要保留当前账号身份提示：路由守卫进入 `/lock` 前要先有最小 `userId/userName/nickName/avatar`，或锁定时保存安全展示快照；刷新后锁屏页必须能明确显示被锁账号，退出登录和重新登录时清理快照，避免共用电脑场景下不知道该输入谁的密码。
320. 顶部公共控件要统一中文和可访问提示：全屏按钮补 tooltip、`aria-label` 和键盘触发，布局大小下拉和成功提示改为中文，并说明大小设置会保存到当前浏览器，避免门店共用电脑沿用上个账号的显示偏好。
321. 用户管理列设置要接入持久化：给 `RightToolbar` 传稳定 `storage-key` 或使用账号级偏好接口保存列显隐状态，并提供“恢复默认列”，让多账号巡检时的配置状态、手机号、创建时间等排查视图不会刷新后丢失。
322. 字典前端缓存要支持全局失效同步：字典项新增、修改、删除或刷新缓存后，已打开且被 `keep-alive` 保留的页面也要刷新本地 `dict.type`；若暂不自动同步，成功提示必须说明已打开页面需刷新后生效，并避免 `dict/setDict` 产生重复缓存。
323. 分页组件要滚动正确容器：翻页和切换每页条数后，应滚动当前 `.app-main`、弹窗内容区或表格外层到列表顶部，而不是固定滚动 `document/body`；弹窗分页不要影响整页滚动，长列表翻页后应能清楚看到新页第一行。
324. 调度日志任务副页面要保留任务上下文：从任务行进入日志时，应按 `job_id` 或固定的 `routeJobName/routeJobGroup` 锁定当前任务；重置只清临时筛选，不应把任务名称和任务组清成全局日志，页面标题和导出确认也要明确当前任务范围。
325. 404 兜底页要能帮助排障和恢复：展示当前失败路径、来源菜单或入口、可能原因和复制诊断信息动作，并提供返回上一页/首页/联系管理员；布局改为响应式，避免固定 1200px 在窄屏或缩放下溢出。
326. 标签页持久化要按账号和权限隔离：`tags-view-visited` 不应作为全浏览器共享工作台状态恢复到任意账号；登录/退出/切换组织时清理或切换到当前用户缓存，恢复前按当前动态路由和权限过滤旧标签，并避免保留上一账号的敏感 query。
327. 关闭页签时要同步处理持久化状态：`tagsView=false` 应自动关闭或清理 `tagsViewPersist/tags-view-visited`，重新开启页签前明确询问是否恢复旧标签，避免“看似关闭页签、实际仍保留旧业务上下文”。
328. 面包屑要支持连字符和隐藏副路由：优先使用 `this.$route.matched` 与 `meta.activeMenu` 生成层级，或按 `/` 保留完整段名匹配，不要用 `/\w+/` 拆路径；为用户授权、角色授权、字典数据、调度日志、库存流水、代码生成编辑和固定资产页面补回归。
329. 内链 iframe 高度要跟随真实布局：iframe 容器使用 `.app-main` 可用高度或统一布局变量，不要写死 `clientHeight - 94.5`；窗口缩放、页签开关、固定 Header 和底部版权变化时同步重算，并为加载失败/禁止嵌入提供新窗口打开和错误提示。
330. 本地 JSON 缓存要有解析容错和版本校验：`screen-lock`、`layout-setting`、`tags-view-visited` 等缓存读取失败时应自动清理并回退默认值，保存时使用 `JSON.stringify`，页面提供“清除本机缓存后重试”恢复入口，避免坏缓存导致整站启动白屏。
331. 布局设置保存要给稳定反馈：保存成功、需要刷新、保存失败都要有明确提示；loading 关闭使用回调函数或直接关闭，不能把 `closeLoading()` 立即调用后传给 `setTimeout`；重置配置也要用函数回调并说明会刷新页面。
332. 主题色应用要拆分 UI 和启动逻辑：根应用不要全局挂载隐藏 `ThemePicker`；启动时用无 UI 的 theme service 注入主题样式，真实颜色选择器只在设置抽屉里渲染。空白/异常路由要显示明确失败状态，自动化巡检也要排除主题控件噪声，避免“清空/确定”掩盖业务页失败原因。
333. 通用下载方法要把失败状态返回给页面：后端返回业务错误、JSON charset、纯文本或 HTML 错误时不能被当成成功文件；解析到错误后应 reject，loading 放到 `finally`，调用页统一展示下载成功/失败/重试状态，保证导出和模板下载流程能被自动化和用户可靠判断。
334. 重复提交防护要纳入账号和组织上下文：判定键不能只看 URL 和请求体，应包含当前用户、token 摘要、`Dept-NumId`、组织类型、请求方法和动作身份；登录、退出、切换组织时清理防重状态，失败响应立即释放重试，业务幂等交给后端唯一约束和状态机兜底。
335. 前端登出要完整清理用户与权限状态：`FedLogOut`、完整登出和登录前重置应统一清空 token、角色、按钮权限、用户身份、动态路由、页签缓存和组织上下文；每次新 token 登录都必须重新执行 `GetInfo/GenerateRoutes`，防止同一浏览器会话中账号 A 的前端菜单和身份残留到账号 B。
336. OA 路由要统一进入组织选择守卫：所有依赖 `Dept-NumId` 的 OA 页面都应在缺少已验证组织时跳转选择组织；管理员全局范围必须显式展示和确认，普通账号不应在页面内才收到“请先选择店铺或仓库”。新标签页、刷新恢复、菜单进入和外链直连都要覆盖回归。
337. Cron 生成器要保证反解析和生成规则双向一致：周规则 `weekday#nth` 的左侧是星期、右侧是第几周，打开已有表达式不能把两者倒置；保存前用后端同源 Quartz 校验并展示后端计算的最近运行时间，避免合法但语义错误的调度被保存。
338. 系统基础资料保存要统一做后端枚举和范围兜底：岗位、字典、参数等页面的 `status/configType/isDefault/postSort/dictSort` 不能只靠前端控件限制；服务层和数据库巡检都要拒绝非法状态、异常内置标记、负排序和同类型多默认字典项，保证数据库值能被前端标签、筛选和导出稳定解释。
339. 部门组织树排序要按同级唯一或统一重排治理：组织选择、店铺授权和部门管理查询都应有稳定兜底顺序；保存排序时禁止同一父节点下重复 `order_num`，或接收完整同级顺序后自动重排为连续值，并迁移清理当前 `parent_id=100` 下的重复排序。
340. 角色数据范围必须后端白名单校验：`data_scope` 只能为 `1-5`，非法值在 DataScope 切面中按最小权限处理并告警；角色排序、状态和严格勾选字段也要补范围/枚举校验，避免构造请求把核心数据权限写成前端无法解释的状态。
341. 数据库迁移辅助存储过程要从运行态收口：`add_transfer_approval_*` 这类动态 DDL 过程只应在受控迁移脚本中临时使用；发布后清理或纳入版本化对象清单，应用库账号禁止使用 `root`，并移除 DDL 和无关 `EXECUTE` 权限。
342. 库存主数据保存要以后端枚举和范围校验为准：商品、分类、供应商、客户的 `status`，供应商 `cooperationStatus` 和分类 `orderNum` 都要拒绝非法值；历史异常状态应在列表中显式标记并提供治理入口。
343. OA 首页要从静态说明升级为真实工作台：按当前账号、角色、组织和菜单状态汇总待办/已办、考勤、工资、劳动合同、固定资产等模块的关键状态；停用或隐藏的采购申请不要作为默认入口，空数据和缺配置要给可操作引导。
344. 薪资权限要拆清员工和角色两条线：员工薪资绑定使用独立 `system:salary:user` 权限，角色薪资绑定继续使用 `system:salary:role`；菜单名、按钮权限、后端接口注解、操作日志标题和角色默认授权必须一致，避免“角色薪资配置”实际放开员工个人薪资维护能力。
345. OA 审批工作台按钮权限要和接口权限对齐：已办详情使用 `oa:done:query` 或独立审批详情权限，待办通过/驳回前后端都用 `oa:todo:approve`；采购申请停用时不要让已办详情依赖停用的 `oa:purchase:query`，恢复采购审批时要把申请、待办、已办、详情和审批动作一起做端到端验收。
346. 劳动合同“发起签约”权限要包含真实表单依赖：不要让 `oa:laborContract:add` 只控制按钮却依赖完整用户管理和薪资配置权限才能选人；应提供劳动合同专用员工候选和薪资快照接口，角色授权时联动校验依赖，缺依赖时前端禁用按钮并说明缺少哪项能力。
347. 代码生成删除确认要展示业务摘要：删除生成配置前显示表名、表描述、实体类、生成方式、生成路径和字段配置数量，批量删除要展示数量和前几条摘要；操作日志记录被删配置摘要，避免只靠表编号执行不可逆删除。
348. 调拨审批规则删除要做引用预检：有运行中实例或待审批任务时禁止删除，只允许停用；有历史实例引用时优先软删除/归档，并在确认框展示引用数量、最近调拨单号和规则快照入口。
349. 系统参数键名要建立数据库唯一约束：`sys_config.config_key` 不能只靠服务层查询保证唯一，迁移前清理重复键，迁移后用唯一索引兜底；缓存加载和健康检查也要能发现重复键名，避免参数页显示多行但运行态只生效其中一行。
350. 个人中心改密成功后要清理敏感输入：保存成功应立即清空旧密码、新密码、确认密码和校验状态；若策略要求重新登录，应给出明确提示并退出当前会话，避免共用电脑上密码输入值继续留在页面里。
351. 调拨记录详情要展示批次商品明细：归档/完成后的调拨记录应和处理中详情一样支持展开 `TS` 发货批次，展示每批商品、发货数量、收货数量和差异；导出台账也应能把 `TF` 调拨单、`TS` 批次和商品明细对齐。
352. 表单构建生成正则规则不能使用 `eval`：正则表达式应按受控字符串或拆分字段解析，复制/导出前做格式校验和错误提示，生成代码时对正则和错误文案都做安全转义；如果表单构建不交付，则同步停用路由、权限和页面组件。
353. 退货“我的”接口要统一个人身份字段：销售退货和采购退货的 `/my` 查询应按 `applicant_id` 匹配当前用户，或由服务层显式设置并文档化 `create_by` 查询语义；同时补个人范围测试，确认申请人能看到自己的退货单且看不到他人单据。
354. 岗位主数据唯一性要有数据库兜底：`sys_post.post_code` 至少建立唯一约束，`post_name` 是否唯一要形成明确业务规则；新增/编辑保留服务层提示，迁移前清理重复值，并让调拨审批、薪资和合同等下游尽量引用稳定岗位 ID 或在岗位变更时做影响预检。
355. 商品分类备注保存要闭环：如果备注字段继续展示为可编辑，新增和编辑 SQL 都必须写入 `remark`，详情、列表和导出也要统一读取；同时补回归用例验证分类备注从新增到二次编辑都能刷新可见。若备注不属于当前交付范围，应从编辑抽屉移除或改成只读说明。
356. 供应商备注维护要能覆盖存量数据：`updateInvSupplier` 必须更新 `remark`，并用当前已有非空备注供应商做回归验证，确保编辑后详情和列表重新读取一致；导出字段也要按采购治理需要决定是否包含备注。若备注只允许系统导入写入，应在页面明确只读。
357. 客户备注保存链路要补齐：`updateInvCustomer` 必须更新 `remark`，并用测试客户验证新增备注、二次编辑备注、重新打开编辑读取三步一致；客户表当前为空，建议在正式录入前先修复，避免门店开始建档后再出现批量备注无法维护。
358. 商品备注要能真正维护：`updateInvProduct` 必须更新 `remark`，并用当前已有备注商品做回归验证，确保编辑后详情、列表和导出读取一致；若现有 `Excel row` 备注只是导入来源，应拆成只读来源字段或迁移清理，不要作为可编辑商品备注继续展示。
359. 采购单和销售单草稿备注要随编辑保存：`updateInvPurchaseOrder` 和 `updateInvSalesOrder` 都应更新 `remark`，并补新增草稿、编辑备注、保存草稿、提交后详情读取的回归用例；提交后是否允许改备注要有明确状态规则，但草稿阶段不能出现“成功保存但备注不变”。
360. 跨店销售发货和自动收货单模型要统一组织类型：发货通知选择器、后端 `warehouseId` 校验、自动生成调拨单的 `transferType`、来源/目标组织类型和目标门店收货入口必须使用同一套契约；跨店销售端到端回归至少覆盖选择真实仓库发货、全部发完、自动生成收货单、目标门店确认收货四步。
361. 调拨审批规则入口要和调拨业务流放在一起治理：系统管理可以保留总配置，但调拨管理、仓库调拨、调拨详情和提交审批失败提示应能引导到审批规则；角色矩阵同步拆清“发起调拨 / 审批调拨 / 配置规则”三类能力。
362. 调拨审批规则编辑权限要显式包含详情读取依赖：编辑按钮应同时检查 `inv:transfer:rule:edit` 和 `inv:transfer:rule:query`，或让详情接口允许编辑权限作为前置读取；权限说明和回归用例要覆盖只授 edit、不授 query 的角色组合。
363. 劳动合同草稿编辑权限要显式包含详情读取依赖：草稿编辑按钮应同时检查 `oa:laborContract:add` 和 `oa:laborContract:query`，或提供受新增权限保护的草稿编辑加载接口；角色矩阵要写清新增、草稿编辑、详情查看和发送签署之间的依赖。
364. 系统基础资料编辑权限要统一处理详情读取依赖：用户、角色、岗位、部门、菜单、字典和公告的修改入口应同时判断 `edit + query`，或提供受 edit 保护的最小编辑加载接口；权限矩阵和回归用例必须覆盖只授 edit、不授 query 的角色组合，避免系统管理页显示可点但详情加载失败。
365. 用户新增表单加载要拆出专用接口：不要用 `system:user:query` 的通用详情接口加载新增表单选项；新增前置数据应由 `system:user:add` 保护的最小 `create-options` 接口返回，并按当前管理员可授权范围过滤角色和岗位。
366. 角色分配用户副页面要统一入口和读取依赖：不要让 `/system/role-auth/user/:roleId` 和“分配用户/添加用户”只看 `system:role:edit`，而首屏和候选列表必须再靠 `system:role:list`；应改成显式 `edit + list` 依赖，或拆出 `system:role:authUser` 专用权限并覆盖已授权/未授权列表和授权写入接口。
367. 用户分配角色副页面要统一入口和读取依赖：不要让 `/system/user-auth/role/:userId` 和“分配角色”只看 `system:user:edit`，而首屏角色授权读取必须再靠 `system:user:query`；应改成显式 `edit + query` 依赖，或拆出 `system:user:authRole` 专用权限和最小授权加载接口。
368. 薪资配置页面要按 tab 拆分初始化权限：页面入口 `system:salary:list` 只加载方案列表，方案选项/档位详情按 `system:salary:query`，员工绑定和核算门店树按独立员工薪资权限加载；不要让只读列表入口隐式依赖 `system:salary:role`，也不要为了页面可用而扩大员工薪资维护权限。
369. 薪资方案编辑和档位查看要显式表达查询依赖：方案编辑按钮不要只看 `system:salary:edit` 后再调用 `system:salary:query` 详情接口，“档位/刷新档位”也不能无权限判断就请求档位明细；按 `query/edit` 组合或更细的档位权限拆分入口、接口和回归用例。
370. 员工薪资绑定移除要做成有上下文的确认：弹窗至少展示员工、核算门店、方案、档位、生效日期和移除后剩余有效绑定数；如果这是员工最后一条有效薪资绑定，应阻止或要求先选择替代方案，并在后端保存前校验“最后有效绑定”策略，避免泛化确认后让工资计算链路失效。
371. 定时任务列表和详情权限要显式分层：任务名称详情入口按 `monitor:job:query` 控制，没有查询权限时展示普通文本或禁用提示；列表接口只返回摘要字段，完整调用目标、cron、执行策略、创建更新信息由详情接口返回，并补只授 `monitor:job:list` 不授 `monitor:job:query` 的回归用例。
372. OA 待办/已办筛选条件要真实进入后端查询和导出：`title/applicantName/status` 至少要在采购单 mapper 层生效，分页总数和导出结果必须与页面筛选一致；若任务 ID 或流程实例来自工作流查询，应在业务表查询阶段继续追加采购字段条件，并增加有待办、已办、已通过、已驳回样本的回归用例。
373. 代码生成的多个生成入口要共享同一套生成方式判断：顶部批量“生成”、行内“生成代码”、zip 下载和自定义路径写入必须在确认框、执行接口和操作日志中明确最终方式；选中多条且存在 `genType=1` 时，应要求用户选择统一 zip 下载或逐条按配置生成，避免同一配置在不同入口下行为不一致。
374. 代码生成编辑页文案要按真实对象复核：`gen_table.table_name` 对应“表名称”，不要沿用仓库模块占位；系统工具恢复交付前应统一检查表名称、实体类、模块名、业务名、上级菜单、自定义路径等提示，避免开发工具页面混入业务模块旧文案。
375. 商品候选能力要按业务场景拆分：销售、库存调整、报表筛选、库存日志筛选不应默认依赖完整商品管理列表/详情权限；提供最小商品候选接口，或在角色矩阵中显式联动 `inv:product:list/query`，并在缺依赖时给出清楚提示。
376. 通用上传组件的前端扩展名校验要和后端保持同一口径：后缀、允许列表和提示文案统一大小写规范化，劳动合同模板、业务附件和文档上传用例覆盖 `.DOCX/.PDF/.XLSX` 等常见大写后缀，避免合法文件被浏览器端误拒。
377. 通用上传组件要把失败、部分成功和重试状态做成可恢复流程：网络失败时回退计数、清理临时成功列表或明确保留成功项，单文件重试成功必须立即写回表单；劳动合同模板、企业章和业务附件都要增加“首次失败后原弹窗内重试”的回归用例。
378. 用户导入弹窗要把 HTTP 失败、业务失败、部分成功和成功完成分清楚：`on-error` 复位上传状态并允许重试，`on-success` 检查业务 `code` 后再决定是否关闭和刷新列表，导入结果用结构化行级反馈和错误文件下载替代单段 HTML 消息。
379. 系统管理页要显式处理当前组织请求头：用户列表、用户导出、角色分配候选和店铺授权前置列表要统一“按当前业务组织过滤”或“按左侧管理组织树过滤”的口径；若保留当前组织限制，树和页面顶部必须提示真实范围，若不保留则这些接口不要被全局 `Dept-NumId` 隐式覆盖。
380. 岗位列表、导出和岗位选择框要按稳定业务排序返回：统一使用 `post_sort asc, post_id asc` 或明确的业务排序规则，相同排序值也必须有兜底顺序；用户新增/编辑、调拨审批候选配置和薪资岗位选择应与岗位管理列表保持一致。
381. 进销存列表筛选区要统一提供“搜索 / 重置”：客户、供应商、销售、采购退货、销售退货等带多个筛选条件的页面应补 `resetQuery`，清空全部查询条件、回到第一页并刷新列表，避免旧筛选残留造成空表误判或导出范围误判。
382. 单据状态审计要保存稳定主体和会话上下文：除 `operator_name` 外写入操作者用户 ID、昵称、角色摘要、当前选择组织、客户端/请求来源和 trace/request ID；状态审计页面和导出按这些字段过滤，避免只靠用户名字符串追责。
383. 字典项默认值要在电脑端和数据库之间闭环：字典数据列表、详情、导出和新增/编辑弹窗都应展示并维护 `is_default`；后端按 `dict_type` 校验默认项规则，并补默认项唯一性/多默认例外的回归用例，避免网页端看不到导出和数据库里的默认语义。
384. 库存变动日志要展示并筛选操作主体：电脑端列表和导出确认补“操作人/操作账号”字段，后端支持按操作人筛选；库存流水表后续补用户 ID、角色、当前组织和请求来源等稳定主体快照，避免多账号库存责任只能靠用户名字符串和离线导出追溯。
385. 用户管理要补密码治理视图：列表、详情、导出和筛选条件统一展示 `pwd_update_date`、初始密码未修改和密码过期状态；启用密码有效期前提供影响预览，支持一键筛出密码更新时间为空或会立即过期的账号。
386. 考勤打卡来源字段要重新定义并可见：固定 `WEB` 应作为渠道字段展示，真实地点/设备/IP/UA/定位状态应作为来源证据单独记录；列表、详情、导出和更正流程都要能看到打卡/签退来源，历史空值标记为未记录。
387. 供应商合作状态变更要拆出专用动作：新增 `inv:supplier:status` 权限和最小状态接口，只提交供应商 ID、目标状态、原因和版本号；后端只更新合作状态并记录前后值、原因、操作者、当前组织和受影响商品数，前端确认框也要展示这些影响摘要。
388. 登录账号唯一性必须由数据库兜底：`sys_user.user_name` 对有效账号增加唯一约束或等价生成列索引，新增、注册、导入和迁移脚本都要捕获唯一冲突；后续授权跳转、日志、会话和审计以稳定 `user_id` 为主，`user_name` 只作为展示快照。
389. 角色权限字符唯一性必须由数据库兜底：`sys_role.role_key` 对有效角色增加唯一约束，必要时同步约束 `role_name`；角色列表补菜单数量、数据范围和授权用户数，让重复或相近角色能力可以被管理员直接识别。
390. 部门组织同级名称唯一性必须由数据库兜底：`sys_dept` 对有效组织增加 `parent_id + dept_name` 唯一约束或等价生成列索引；组织树、授权树和导出中展示完整路径或 ID 辅助信息，避免同名门店/仓库导致授权和业务归属误判。
391. 菜单路由身份唯一性必须由数据库兜底：`sys_menu` 对同级菜单名称、同级启用路由地址和全局页面路由身份增加唯一约束或等价生成列索引；菜单导入、初始化 SQL 和发布前巡检要输出重复路由、重复名称和重复权限码清单。
392. 薪资方案唯一身份要在页面、服务和数据库三层闭合：有效方案至少约束 `scheme_name + social_type`，若按生效日期做版本管理则下拉、删除确认、导出和日志都展示“名称 / 社保口径 / 生效日期 / ID”，避免员工绑定和方案删除只靠名称辨识。
393. 调拨审批页要补齐后端已支持的动作和意见：审批弹窗提供“通过 / 驳回”和审批意见，驳回意见必填，通过意见可选，并把当前节点、候选审批人、规则快照摘要和调拨单影响展示给审批人；如果短期只允许通过，应下线 `reject_action` 配置和驳回后端入口，避免配置页承诺与电脑端流程不一致。
394. 用户联系方式唯一规则要覆盖所有入口：手机号和邮箱若作为唯一联系方式，新增、编辑、个人中心、注册和批量导入都必须共用同一校验，数据库增加有效账号唯一兜底，导入时输出行级重复错误；若允许共享联系方式，则取消唯一错误提示，并在列表、导出和候选弹窗里用账号 ID、部门、角色和授权组织辅助识别。
395. 薪资方案导出确认要和真实筛选条件一致：确认框展示方案名称、社保口径、导出范围和薪资敏感字段，移除页面不存在的状态筛选；后续新增或删除筛选项时，同步更新前端确认文案、后端过滤和导出回归用例。
396. 调拨导出确认要展示完整状态和类型范围：处理中和记录页都应列出当前组织、单号、状态、调拨类型和记录数，确认文案与实际 `orderNo/status/transferType` 后端过滤保持一致，避免用户导出后才发现状态筛选残留或类型范围不完整。
397. 发货通知导出确认要覆盖完整查询口径：展示当前组织、通知单号、销售单号、客户、状态中文名和导出范围，确认文案与实际 `noticeNo/salesOrderNo/customerName/status` 后端过滤保持一致；导出操作日志也应保存这些筛选摘要，便于多门店发货台账追溯。
398. 固定资产导出要补确认和敏感字段提示：固定资产配置、维修上报和后续额度流水导出统一展示组织范围、店铺、资产/商品、状态、记录数和敏感字段，空数据导出也要提示当前筛选无记录；操作日志保存筛选摘要和记录数，方便资产台账外发追溯。
399. 表单构建上传组件不能默认指向第三方演示站点：默认上传地址应使用本系统受权限保护的上传接口或强制留空配置；复制/导出前对外部 URL 做拦截或强提示，并复用文件中心的权限、大小/类型校验、失败重试、存储记录和操作审计闭环。
400. 店铺授权清空要改成高危显式动作：普通保存至少保留一个可用门店/仓库；若允许清空全部授权，应要求二次确认目标账号、当前授权、清空后影响和原因，后端拒绝无标记空数组或要求 `allowEmpty=true + reason`，并记录授权变更 before/after 审计。
401. 商品导入结果要区分全部成功、部分成功和全部失败：后端返回结构化行级结果，失败行不再被包装成成功响应；前端在部分失败时展示错误列表或错误文件下载和继续修正入口，全失败时保持弹窗打开并禁止用成功 toast 掩盖失败。
402. 劳动合同签署前要做身份字段强校验：身份证号、手机号、员工姓名、合同主体组织和合同证据快照必须在保存、发送签署、员工签署三个节点统一校验；无效存量合同要在列表和详情标记风险，并通过作废重签或人工确认治理。
403. 考勤缺签退要进入异常状态和工资前置校验：上班打卡后应显示待签退，跨日未签退转为缺卡/异常或要求更正审批；工资计算不能把 `check_out_time/work_hours` 为空的 `normal` 记录计作完整工作日，列表和导出也要突出缺签退风险。
404. 员工薪资绑定有效期要在前端闭环：列表、弹窗、保存校验和工资计算都要展示并维护生效日期与结束日期；同一员工同一门店同一月份命中多条绑定时必须提示冲突和优先级，不能让 `end_date` 只存在于数据库和工资 SQL 里。
405. 采购单和销售单主列表要补申请人闭环：列表、详情、筛选和导出统一展示申请人账号/姓名、申请部门和创建账号；多人同店制单时，提交、收货、质检、发货通知、取消等动作都应能在页面上直接看到责任主体，不能只靠导出或查库追溯。

## 本轮验证方式

- 2026-06-25 继续只读复核采购单和销售单申请人字段闭环，检查采购/销售保存服务写入 `applicant_id/applicant_name/applicant_dept_id`、两个 Mapper 列表查询返回申请人姓名和部门、`InvPurchaseOrder/InvSalesOrder` 导出注解，以及采购页、销售页列表和详情弹窗字段；用 MySQL 复核当前 `inv_purchase_order=0`、`inv_sales_order=0`，未构造业务单据；确认申请人字段会入库和导出但电脑端主单列表/详情不可见，已新增 P3-281。
- 2026-06-25 继续只读复核员工薪资绑定有效期闭环，检查薪资配置员工绑定表格、绑定弹窗、`saveUserRuleBinding` payload、`SysSalaryConfigServiceImpl.saveUserSalarySchemes`、`SysUserSalarySchemeMapper.batchUserSalaryScheme` 和 `OaSalaryEmployeeMapper.selectSalaryEmployeesByShopDeptId`；用 MySQL 复核当前 `sys_user_salary_scheme=1`、唯一绑定 `effective_date=2025-12-28/end_date=''` 且工资计算 SQL 会按 `end_date` 判断有效期；确认结束日期进入工资计算但电脑端不可查看或维护，未执行保存写入，已新增 P3-280。
- 2026-06-25 继续只读复核考勤缺签退状态和工资计算口径，检查考勤页状态列、上班/下班按钮、`OaAttendanceServiceImpl.checkIn/checkOut/calculateAttendance`、`OaAttendanceRecordMapper` 写入字段、`OaAttendanceRecord` 导出注解和 `OaSalaryServiceImpl.calculateSalary`；用 MySQL 复核当前 `oa_attendance_record=1` 且唯一记录 `check_out_time/work_hours/late_minutes/early_minutes` 为空但 `status=normal`，统计 `normal_no_checkout=1`；确认缺签退记录仍按正常展示并会被工资计算计为工作日，未执行打卡或工资写入，已新增 P2-168。
- 2026-06-25 继续只读复核劳动合同身份字段校验，检查发起签约表单、`contractRules/submitContract`、`OaLaborContract` 校验注解、`OaLaborContractServiceImpl.saveContract/sendContract/signContract`、合同 Mapper 写入字段，并用 MySQL 复核当前 `oa_labor_contract=9`、身份证号格式异常 8 条、手机号格式异常 6 条、`signed` 合同中仍有 1 条身份证和手机号异常；确认劳动合同可保存并签署无效身份字段，未执行任何合同写入，已新增 P2-167。
- 2026-06-25 继续只读复核商品导入失败态，检查商品导入弹窗 `doImport`、商品导入 API、`InvProductController.importData`、`InvProductServiceImpl.importProduct` 的 `failCount` 汇总和返回状态，并用 MySQL 复核当前 `inv_product=162`、`inv_product_category=30`、`admin/ck/zjl` 持有 `inv:product:import`；确认失败行仍通过成功响应和成功 toast 关闭弹窗，未执行导入写入，已新增 P3-279。
- 2026-06-25 继续只读复核店铺授权清空保存链路，检查 `erp-ui/src/views/system/shop/index.vue` 的保存按钮、`submitShopScope/getCheckedShopIds`、`erp-ui/src/api/system/userShop.js`、`SysUserShopController.save`、`SysUserShopServiceImpl.saveUserShops/insertUserShopBindings` 和 `SysUserShopMapper.deleteUserShopByUserId`，并用 MySQL 复核当前 `sys_user_shop=43`、已有 `user_id=103 / 123` 处于 `scope_count=0`；确认空勾选保存会被当作成功清空授权，未调用写接口，已新增 P2-166。
- 2026-06-25 继续只读复核表单构建上传组件默认地址，检查 `erp-ui/src/utils/generator/config.js` 的上传默认配置、`render.js` 组件配置透传、`js.js` 生成 `${vModel}Action`、`html.js` 生成 `el-upload :action`，并用 MySQL 复核当前 `tool/build` 父菜单停用但 `tool:build:list` 仍授给 `common/zjl`；确认上传组件默认指向 `https://jsonplaceholder.typicode.com/posts/`，页面预览和复制/导出的 Vue 代码都会沿用该外部地址，已新增 P3-278。
- 2026-06-25 继续只读复核固定资产配置和维修上报导出按钮，检查两个页面的导出按钮、筛选项和 `handleExport`，对照 `OaFixedAssetConfigController.export`、`OaFixedAssetRepairController.export`、两个导出实体的 `@Excel` 字段和 Mapper 查询条件，并用 MySQL 复核当前 `oa_fixed_asset_config=0`、`oa_fixed_asset_repair=0`；确认两个导出都会直接下载且包含资产金额、预计维修金额、故障说明等台账字段，没有导出确认或敏感字段提示，已新增 P3-277。
- 2026-06-25 继续只读复核发货通知导出确认范围，检查 `erp-ui/src/views/inventory/deliveryNotice/index.vue` 的筛选项、`handleExport/getDeliveryNoticeExportConfirmMessage/statusLabel`、`InvDeliveryNoticeController.export`、`InvDeliveryNoticeServiceImpl.selectNoticeList` 和 `InvDeliveryNoticeMapper.xml` 的 `noticeNo/salesOrderNo/customerName/status` 过滤，并用 MySQL 复核当前 `inv_delivery_notice=0`、`inv_delivery_notice_detail=0`；确认导出请求会按通知单号、销售单号、客户和状态过滤，但确认框只展示状态英文码和销售单号，已新增 P3-276。
- 2026-06-25 继续只读复核调拨处理中导出确认范围，检查 `erp-ui/src/views/inventory/transfer/index.vue` 的筛选项、`handleExport/getTransferExportConfirmMessage`、`InvTransferController.export`、`InvTransferServiceImpl.selectTransferList` 和 `InvTransferOrderMapper.xml` 的 `orderNo/status/transferType` 过滤，并用 MySQL 复核当前 `inv_transfer_order` 存在 `partial_delivered/received` 两个状态样本；确认导出请求会按状态和类型过滤，但确认框只展示单号和当前组织，已新增 P3-275。
- 2026-06-25 继续只读复核薪资方案导出确认文案，检查 `erp-ui/src/views/system/salary/index.vue` 的筛选区、`schemeQueryParams`、`handleExportScheme/buildSalaryExportFilterLabel`，对照 `SysSalaryConfigController.export` 和 `SysSalarySchemeMapper.xml` 的 `socialType` 后端过滤，并用 MySQL 确认当前两条薪资方案分属“无社保/有社保”；确认导出请求会按社保口径过滤，但确认框漏显社保口径并展示页面不存在的状态筛选，已新增 P3-274。
- 2026-06-25 继续只读复核用户手机号和邮箱唯一性闭环，检查用户新增/编辑、个人中心、批量导入、`SysUserServiceImpl.checkPhoneUnique/checkEmailUnique/importUser`、`SysUserMapper.checkPhoneUnique/checkEmailUnique` 和运行态 `sys_user` 索引与重复统计；确认页面和部分接口把手机号/邮箱当唯一值，但导入不校验、数据库也无唯一约束，当前存量 25 个有效账号暂无重复，已新增 P3-273。
- 2026-06-25 继续只读复核调拨审批任务和前端审批动作，检查 `erp-ui/src/views/inventory/transfer/index.vue`、`erp-ui/src/api/inventory/transfer.js`、`InvTransferApprovalRequest`、`InvTransferApprovalServiceImpl`、`InvTransferApprovalTaskMapper.xml`，并用 MySQL 复核运行态 `inv_transfer_order/inv_transfer_approval_instance/inv_transfer_approval_task/inv_transfer_approval_node` 样例；确认后端和任务表支持 `reject/comment`，但电脑端只发 `action=approve` 且无审批意见输入，已新增 P2-165。
- 2026-06-25 继续只读复核薪资方案唯一身份闭环，检查薪资配置页方案表单、员工绑定薪资方案下拉、删除确认、`SysSalaryConfigController.add/edit`、`SysSalaryConfigServiceImpl.insertSalaryScheme/updateSalaryScheme`、`SysSalarySchemeMapper.insert/update/selectSalarySchemeOptions`，并用 MySQL 复核当前 `sys_salary_scheme` 只有主键和普通 `status/social_type` 索引、两条方案暂无重复名称或同名同社保口径组合；确认薪资方案没有唯一身份兜底且员工绑定下拉只显示方案名称，已新增 P3-272。
- 2026-06-25 继续只读复核菜单名称和路由身份唯一性，检查菜单管理页菜单名称、路由名称、路由地址、组件路径、权限字符表单和校验规则，检查 `SysMenuController.add/edit`、`SysMenuServiceImpl.checkMenuNameUnique/checkRouteConfigUnique`、`SysMenuMapper.checkMenuNameUnique/selectMenusByPathOrRouteName`，并用 MySQL 复核当前 `sys_menu=249`、只有 `PRIMARY(menu_id)`、启用目录/页面暂无重复同级 `path` 或重复 `route_name/path` fallback；确认菜单基础身份唯一性只靠服务层查询，数据库缺少菜单树唯一约束，已新增 P3-271。
- 2026-06-25 继续只读复核部门同级名称唯一性，检查部门管理页名称列、部门弹窗和校验规则，检查 `SysDeptController.add/edit`、`SysDeptServiceImpl.checkDeptNameUnique/insertDept/updateDept`、`SysDeptMapper.checkDeptNameUnique/insertDept/updateDept`，并用 MySQL 复核当前 `sys_dept` 只有 `PRIMARY(dept_id)`、有效组织 11 个、同父级暂无重复 `dept_name`，但跨父级存在合法同名“财务部门”；确认同父级名称唯一只靠服务层查询，数据库缺少有效组织唯一约束，已新增 P3-270。
- 2026-06-25 继续只读复核角色权限字符唯一性，检查角色管理页“权限字符”搜索、列表、表单和校验规则，检查 `SysRoleController.add/edit`、`SysRoleServiceImpl.checkRoleNameUnique/checkRoleKeyUnique/selectRolePermissionByUserId`、`SysRoleMapper.checkRoleNameUnique/checkRoleKeyUnique/insertRole/updateRole`，并用 MySQL 复核当前 `sys_role` 只有 `PRIMARY(role_id)`、有效角色 11 个且暂无重复 `role_key/role_name`；确认角色权限字符唯一性只靠服务层查询，数据库缺少有效角色唯一约束，已新增 P3-269。
- 2026-06-25 继续只读复核用户登录账号唯一性，检查用户管理页登录账号输入和规则、`SysUserController.add/edit/register/importData`、`SysUserServiceImpl.checkUserNameUnique/importUser`、`SysUserMapper.selectUserByUserName/checkUserNameUnique/insertUser`，并用 MySQL 复核当前 `sys_user` 只有 `PRIMARY(user_id)`、`total_users=25`、有效账号 25 个且暂无重复 `user_name`；确认登录账号唯一性只靠服务层查询，数据库缺少有效账号唯一约束，已新增 P3-268。
- 2026-06-25 继续只读复核供应商合作状态快捷动作，浏览器直达 `/cangku/supplier` 仍为空白、`/inventory/supplier` 进入 404，确认该入口问题已由 P2-16 覆盖，本轮未触发状态修改；随后检查 `erp-ui/src/views/inventory/supplier/index.vue` 的状态下拉和 `updateSupplier(Object.assign({}, row, { cooperationStatus }))` 调用、`supplier.js`、`InvSupplierController.edit`、`InvSupplierServiceImpl.saveSupplier`、`InvSupplierMapper.xml` 更新 SQL、`LogAspect` 和 `SysOperLogMapper.xml`，并用 MySQL 复核当前 `inv_supplier=17`、17 条均正常合作、16 个供应商被 44 条商品引用、权限树没有 `inv:supplier:status`；确认合作状态快捷切换复用完整编辑权限和完整编辑接口，缺少独立状态权限、最小 payload、差异审计和原因记录，已新增 P3-267。
- 2026-06-25 继续只读复核 OA 考勤打卡来源字段，浏览器进入 `/oa/attendance` 后只打开“上班打卡”确认框并取消，截图保存为 `docs/audit-screenshots/20260625/oa-attendance-web-source-only-confirm.png`；使用 MySQL 复核当前唯一 `oa_attendance_record` 的 `check_in_location` 为空、`check_out_location` 为 `NULL`；检查 `erp-ui/src/views/oa/attendance/index.vue`、`OaAttendanceController`、`OaAttendanceServiceImpl`、`OaAttendanceRecord` 和 `OaAttendanceRecordMapper.xml`；确认页面只写固定 `WEB` 渠道，字段名和表字段却叫 location，且列表/导出不可见，已新增 P3-266。
- 2026-06-25 继续只读复核用户管理密码治理字段闭环，使用浏览器进入 `/system/user` 并点击用户 `123` 打开详情抽屉，截图保存为 `docs/audit-screenshots/20260625/user-detail-missing-pwd-update-date.png`；使用 MySQL 复核启用账号 `25` 个、`pwd_update_date` 为空 `13` 个、从未登录 `2` 个，并确认 `sys.account.initPasswordModify=1`、`sys.account.passwordValidateDays=0`；检查 `SysUserController.getInfo/initPasswordIsModify/passwordIsExpiration`、`SysUserMapper.xml`、`SysUser.java`、`erp-ui/src/views/system/user/index.vue` 和 `erp-ui/src/views/system/user/view.vue`；确认密码最后更新时间参与策略但用户管理列表/详情/导出不可见也不能筛选，已新增 P3-265。
- 2026-06-25 继续只读复核库存变动日志操作主体链路，使用浏览器选择 `仓库：仓库` 后进入 `/inventory/stock-log`，截图保存为 `docs/audit-screenshots/20260625/inventory-stock-log-missing-operator.png`；使用 MySQL 复核 `inv_stock_log.create_by` 当前有 `admin/dushuai/ll1` 样例；检查 `InvStockLog` 实体、`InvStockLogMapper.xml`、`InvStockController.logList/exportLog`、库存调整/采购/销售/退货/盘点/调拨写流水路径和 `erp-ui/src/views/inventory/stock/log.vue`；确认库存流水有操作人字段和导出定义，但电脑端列表不展示也不能按操作人筛选，已新增 P3-264。
- 2026-06-25 使用浏览器进入 `1025` 前端并打开 `/system/dict` 与 `/system/dict-data/index/1`，截图保存为 `docs/audit-screenshots/20260625/system-dict-data-default-field.png`；同时用 MySQL 复核 `sys_dict_data.is_default` 当前 29 条字典项的默认值分布，检查 `SysDictData` 实体、`SysDictDataMapper.xml`、`SysDictDataController` 和 `erp-ui/src/views/system/dict/data.vue`；确认字典项默认值字段有数据库、后端和导出定义，但电脑端列表/表单不可见也不可维护，已新增 P3-263。
- 2026-06-25 继续只读复核单据状态审计主体字段，检查运行态 `inv_document_status_log` 字段、6 个 `trg_inv_*_status_au` 触发器、`InvDocumentStatusLog` 实体、`InvDocumentStatusLogMapper.xml`、`/audit/list` Controller 以及销售/采购/退货/盘点/发货通知状态变更服务中的 `SecurityUtils.getUsername()` 写入链路；确认状态审计只保存 `operator_name` 字符串，缺少稳定用户 ID、角色、当前组织和会话上下文，已新增 P3-262。
- 2026-06-25 继续只读复核系统用户/角色列表的当前组织请求头链路，检查 `request.js` 全局写入 `Dept-NumId`、用户管理页左侧 `TreePanel` 节点筛选、`SysShopDeptFilterSupport` 对非管理员查询参数的覆盖、`SysUserController` 与 `SysRoleController` 的列表调用，并用 MySQL 确认当前 `ry / zjl` 同时具备 7 个经营组织授权和 `system:user:list/export`、`system:role:list` 权限；确认当前业务组织头会让系统管理列表与左侧组织树筛选口径不一致，已新增 P3-259。
- 2026-06-25 继续只读复核用户导入弹窗失败状态，检查 `ExcelImportDialog` 的 `el-upload` 事件绑定、`isUploading`、`handleSuccess` 和用户管理页挂载方式；确认组件没有 `on-error` 且不判断 `response.code`，失败可能卡住或被当成完成态刷新，已新增 P3-258。
- 2026-06-25 继续只读复核通用上传失败后的状态恢复，检查 `FileUpload/ImageUpload` 的 `number/uploadList/handleUploadError/uploadedSuccessfully` 计数逻辑，以及劳动合同模板和企业章弹窗的必填保存条件；确认浏览器层上传失败不会复位计数，后续原弹窗内重试成功也可能不写回表单值，已新增 P3-257。
- 2026-06-25 继续只读复核通用文件上传扩展名校验，检查 `FileUpload` 的 `handleBeforeUpload`、劳动合同模板上传挂载点和后端 `FileUploadUtils.isAllowedExtension`；确认前端大小写敏感而后端忽略大小写，合法大写后缀会在浏览器端被误拒，已新增 P3-256。
- 2026-06-25 继续只读复核业务页共用商品选择器权限依赖，检查 `ProductSelect.vue` 的 `listProduct/getProduct` 调用、销售制单、库存调整、报表中心和库存变动日志中的商品选择器挂载点、`InvProductController` 的 `inv:product:list/query` 权限注解，并用 MySQL 确认当前 `zdjl / 驻店经理` 没有商品列表/详情权限但持有销售新增/提交、库存调整、库存日志和报表查询权限；确认业务页商品候选和商品管理权限耦合，已新增 P3-255。
- 2026-06-25 继续只读复核系统工具代码生成生成入口与基本信息文案，检查 `tool/gen/index.vue` 顶部“生成”、行内“生成代码”、`handleGenTable` 分支、`GenController.genCode/batchGenCode`、`GenTableServiceImpl.generatorCode/downloadCode`、`basicInfoForm.vue` 占位文案，并用 MySQL 确认当前 `gen_table=0/gen_table_column=0`、`tool:gen:code/edit` 权限节点启用；确认顶部生成入口无视 `genType=1` 走 zip 下载，且“表名称”占位误写为“仓库名称”，已新增 P3-253、P3-254。
- 2026-06-25 继续只读复核 OA 待办/已办筛选链路，检查 `oa/todo/index.vue`、`oa/done/index.vue`、`api/oa/purchase.js`、`OaPurchaseController.todoList/doneList/exportTodo/exportDone`、`OaPurchaseServiceImpl.selectTodoPurchases/selectDonePurchases`、`OaPurchaseMapper.selectTodoPurchaseListByTaskIds/selectDonePurchaseListByProcessIds` 和运行态 `oa_purchase/ACT_RU_TASK/ACT_HI_TASKINST` 空数据状态；确认页面传入标题、申请人和状态筛选，但待办/已办 mapper 未应用这些条件，已新增 P3-252。
- 2026-06-25 继续只读复核定时任务任务名称详情入口权限依赖，检查 `monitor/job/index.vue` 的任务名称链接、`handleView/getJob`、`monitor/job/detail.vue`、`SysJobController.list/getInfo` 和运行态 `monitor:job:list/query` 角色授权；确认当前 `common/zjl` 成对持有暂不触发，但权限模型允许只授 list 后任务名称仍可点而详情接口拒绝，已新增 P3-251。
- 2026-06-25 继续只读复核薪资员工绑定移除链路，检查 `handleUnbindRule/saveUserSalary`、`SysSalaryConfigServiceImpl.saveUserSalarySchemes`、当前 `sys_user_salary_scheme` 运行态绑定和 OA 工资计算的“未配置薪资方案”校验；确认移除最后一条绑定时前端只给泛化确认、后端会删除该员工全部旧绑定并接受空列表，已新增 P3-250。
- 2026-06-25 继续只读复核薪资方案编辑和档位入口权限依赖，检查薪资配置页方案行操作、`handleEditScheme/selectScheme/loadSchemeItems`、`salaryConfig.js` 的方案详情和档位接口、`SysSalaryConfigController.getInfo/items/edit/editItem` 权限注解，并用 MySQL 对照当前 `admin/common/zjl` 的 `system:salary:query/edit` 授权；确认默认角色成对持有暂不触发，但权限模型允许只授 edit 后编辑入口可见而详情接口拒绝、只授 list 后档位入口可见而档位接口拒绝，已新增 P3-249。
- 2026-06-25 继续只读复核薪资配置页面首屏权限依赖，检查运行态 `sys_menu` 的 `system:salary:list/query/role` 节点和角色授权、薪资配置页 `initPage/loadSalaryUsers/refreshSchemeOptions`、`salaryConfig.js` API、`SysSalaryConfigController.list/options/users/shopTree` 权限注解；确认当前 `admin/common/zjl` 三项权限成对持有暂不触发，但权限模型允许只授 list 后页面入口可见而首屏 options/users/shopTree 接口拒绝，已新增 P3-248。
- 2026-06-25 继续只读复核用户分配角色副页面权限依赖，检查隐藏路由 `/system/user-auth/role/:userId`、用户列表“分配角色”入口、授权角色页首屏加载和提交、`user.js` API、`SysUserController.authRole/insertAuthRole` 权限注解，并用 MySQL 对照当前 `common/zjl` 的 `system:user:query/edit` 授权；确认默认角色成对持有暂不触发，但权限模型允许只授 edit 后页面入口可见而授权角色读取接口拒绝，已新增 P3-247。
- 2026-06-25 继续只读复核角色分配用户副页面权限依赖，检查隐藏路由 `/system/role-auth/user/:roleId`、角色列表“分配用户”入口、授权用户页按钮、选择用户弹窗、`role.js` API、`SysRoleController.allocatedList/unallocatedList/authUser` 权限注解，并用 MySQL 对照当前 `common/zjl` 的 `system:role:list/edit` 授权；确认默认角色成对持有暂不触发，但权限模型允许只授 edit 后页面入口可见而已授权/未授权列表接口拒绝，已新增 P3-246。
- 2026-06-25 继续只读复核用户新增弹窗权限依赖，检查用户页新增按钮、`handleAdd()`、`getUser()` API、`SysUserController.getInfo/add` 权限注解和运行态 `system:user:list/query/add` 角色授权；确认当前 `common/zjl` 均同时持有 add/query 暂不触发，但权限模型允许只授 add 后新增按钮可见而 `/system/user/` 前置选项接口拒绝，已新增 P3-245。
- 2026-06-25 继续只读复核系统基础资料编辑权限依赖，检查用户、角色、岗位、部门、菜单、字典类型、字典项和通知公告页面的修改/数据权限按钮、详情加载方法、对应 API 与 Controller `getInfo()` 权限注解，并用 MySQL 对照运行态 `system:*:edit/query` 权限节点和 `common/zjl` 角色授权；确认当前默认角色成对持有暂不触发，但权限模型允许只授 edit 后按钮可见而详情接口拒绝，已新增 P3-244。
- 2026-06-25 继续只读复核劳动合同草稿编辑权限依赖，检查劳动合同列表按钮、`handleEdit`、劳动合同 API、`OaLaborContractController.detail/save`、`OaLaborContractServiceImpl.saveContract` 和运行态 `oa:laborContract:*` 角色授权及合同状态分布；确认当前没有草稿且 `admin/yyjl` 均同时具备 add/query/list，但权限模型允许只授 add 后显示草稿编辑入口而详情接口拒绝，已新增 P3-243。
- 2026-06-25 继续只读复核调拨审批规则编辑按钮权限依赖，检查 `transfer/rules.vue` 编辑按钮、`openForm` 详情加载、`transferApprovalRule.js`、`InvTransferApprovalRuleController.getInfo/edit` 和运行态 `inv:transfer:rule:*` 角色授权；确认当前 `admin/zjl` 均有全套权限暂不触发，但权限模型允许只授 edit 后编辑入口显示而详情接口拒绝，已新增 P3-242。
- 2026-06-25 继续只读复核调拨审批配置菜单归属，使用 MySQL 核对 `sys_menu` 中 `4410 / 调拨审批配置` 挂在 `1 / 系统管理` 下，组件为 `inventory/transfer/rules`、权限为 `inv:transfer:rule:*`，且当前仅 `admin/zjl` 授权；检查 `transfer/rules.vue` 的搜索、新增、编辑、预览、删除按钮和 `erp-ui/src/views/inventory/transfer`、首页、布局、静态路由中的 `transfer-rules/调拨规则/审批规则` 命中，确认调拨业务页没有配置入口引导，已新增 P3-241。
- 2026-06-25 继续只读复核采购单、销售单、采购退货和销售退货的数量/单价/金额校验链路，检查四个电脑端表单的数量控件、保存 payload、`InvPurchaseServiceImpl.applyCatalogProductsForDraft`、`InvSalesServiceImpl.applyCatalogProductsForDraft`、两个退货服务的 `normalizeReturnDetails/validateReturnQuantity`、明细实体、运行态明细表数值列和 CHECK 约束；确认采购/销售保存会在后端校验数量大于 0、单价不小于 0 并重算金额，退货保存会以后端原单明细重算单价/金额且提交/确认会校验不超过已收/已发数量。数据库层当前没有 CHECK 约束，但该点已有“后端兜底/原单权威”类建议覆盖，本轮未新增重复问题。
- 2026-06-25 继续只读复核采购退货/销售退货备注保存闭环，检查两个退货表单备注输入、保存/提交 payload、`InvPurchaseReturnServiceImpl.saveDraft`、`InvSalesReturnServiceImpl.saveDraft`、两个 return mapper 新增/更新 SQL、详情弹窗和导出实体字段，并用 MySQL 核对当前 `inv_purchase_return=0`、`inv_sales_return=0`；确认新增 SQL 写 `remark` 但编辑草稿更新 SQL 不写 `remark`，且详情/导出仍不可见，已修正 P3-135。
- 2026-06-25 继续只读复核采购单/销售单备注保存闭环，检查采购和销售表单备注输入、编辑回填、保存/提交 payload、`InvPurchaseServiceImpl.saveDraft`、`InvSalesServiceImpl.saveDraft`、`InvPurchaseOrderMapper.xml` 和 `InvSalesOrderMapper.xml` 新增/更新 SQL，并用 MySQL 核对当前 `inv_purchase_order=0`、`inv_sales_order=0` 且两表均存在 `remark` 字段；确认新增写 `remark` 但草稿编辑更新 SQL 不写 `remark`，已新增 P3-240。
- 2026-06-25 继续只读复核商品备注保存闭环，检查 `/cangku/product` 详情备注、编辑备注输入、`getProduct/updateProduct` 保存 payload、`InvProductController.edit`、`InvProductServiceImpl.saveProduct` 更新分支、`InvProductMapper.xml` 新增/更新 SQL，并用 MySQL 核对当前 `inv_product=162` 且非空 `remark=162`；确认新增链路写 `remark` 但更新 SQL 不写 `remark`，已新增 P3-239。
- 2026-06-25 继续只读复核客户备注保存闭环，检查 `/inventory/customer` 备注输入、`getCustomer/updateCustomer` API、`InvCustomerController.edit`、`InvCustomerServiceImpl.saveCustomer` 更新分支、`InvCustomerMapper.xml` 新增/查询/更新 SQL，并用 MySQL 核对当前 `inv_customer=0` 且表结构存在 `remark varchar(500)`；确认新增和查询链路支持 `remark` 但更新 SQL 不写 `remark`，已新增 P3-238。
- 2026-06-25 继续只读复核供应商备注保存闭环，检查 `/cangku/supplier` 详情备注、编辑备注输入、`updateSupplier` 保存 payload、`InvSupplierServiceImpl.saveSupplier` 更新分支、`InvSupplierMapper.xml` 新增/查询/更新 SQL，并用 MySQL 核对当前 `inv_supplier=17` 且非空备注 `16`；确认新增和查询链路支持 `remark` 但更新 SQL 不写 `remark`，已新增 P3-237。
- 2026-06-25 继续只读复核商品分类备注保存闭环，检查 `/cangku/category` 页面备注输入、编辑回显、保存 payload、`InvProductCategoryServiceImpl.saveCategory` 更新分支、`InvProductCategoryMapper.xml` 新增/更新 SQL，并用 MySQL 核对当前 `inv_product_category` 有效分类 `30` 条且非空备注 `0` 条；确认新增 SQL 写 `remark` 但更新 SQL 不写，已新增 P3-236。
- 2026-06-25 继续只读复核低覆盖桌面副页面和角色授权副页面，检查 `index_v1.vue`、`system/dict/detail.vue`、`monitor/job/detail.vue`、劳动合同重复副本、定时任务列表/详情/调度日志详情、角色分配用户页和选择用户弹窗，并对照 `SysJobController/SysJobLogController/SysRoleController/SysRoleServiceImpl/SysUserMapper/SysUserRoleMapper` 与运行态 `sys_job/sys_job_log/sys_user_role/sys_role/sys_user`；确认未路由/重复文件已由 P3-83 覆盖，定时任务状态开关权限已由 P3-170 覆盖，角色分配候选、超级管理员角色保护、跨页选择、最后角色和停用角色兜底已分别由 P2-36、P2-116、P3-153、P3-189、P3-144 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核岗位管理唯一性闭环，检查 `system/post` 页面保存表单、`SysPostController.add/edit`、`SysPostServiceImpl.checkPostNameUnique/checkPostCodeUnique`、`SysPostMapper` 唯一查询、调拨审批候选人按 `post_code` 匹配逻辑，并用 MySQL 核对当前 `sys_post=10` 且岗位编码/名称均无重复但表结构只有 `PRIMARY(post_id)`；确认岗位唯一语义只靠服务层，已新增 P3-235。
- 2026-06-25 继续只读复核销售退货/采购退货“我的”接口，检查前端 `listMySalesReturn/listMyPurchaseReturn`、后端 `/my` Controller、`selectMyReturns` 服务层、两个 return mapper 个人查询 SQL，并用 MySQL 核对当前 `inv_sales_return=0`、`inv_purchase_return=0` 且两表都存在 `applicant_id/create_by` 字段；确认服务层写 `applicantId` 但 mapper 按空 `createBy` 过滤，已新增 P3-234。
- 2026-06-25 继续只读复核登录/注册与选择组织链路，检查 `login.vue` 记住密码、`register.vue` 注册表单、`TokenController/SysLoginService/SysUserController` 注册开关闭环、`select-shop/index.vue` 组织选择/取消/预览模式、`shopContext/request/permission` 的组织上下文与 `Navbar` 当前组织展示；确认记住密码、注册关闭直达表单、注册开关入口不联动、自助注册账号缺角色/组织、选择组织取消残留权限、OA/仓库路由守卫遗漏、预览组织污染、重复提交和标签页缓存等风险均已由既有问题覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核表单构建正则校验生成流程，检查 `RightPanel.vue` 的正则表达式输入、`drawingDefault.js` 默认正则、`js.js` 规则生成、`index.vue` 复制/导出触发点，并对照 P3-54 的无后端保存/审计结论；确认复制或导出时会执行 `eval(item.pattern)`，已新增 P3-233。
- 2026-06-25 继续只读复核调拨记录详情批次展示，检查 `transfer/records.vue`、处理中调拨详情 `transfer/index.vue`、`transfer.js`、`InvTransferController.records/detail`、`InvTransferServiceImpl.getTransferDetail` 和运行态 `inv_transfer_order/detail/shipment/shipment_detail`；确认后端返回 `shipments.details` 且处理中详情可展开商品明细，但调拨记录详情只展示批次头，已新增 P3-232。
- 2026-06-25 继续只读复核个人中心修改密码链路，检查 `profile/resetPwd.vue` 三个密码输入框、提交成功逻辑、`passwordRule.js`、`updateUserPwd` API、`SysProfileController.updatePwd` 和运行态密码策略参数；确认改密成功只提示“修改成功”但不清空旧密码/新密码/确认密码输入值，已新增 P3-231。
- 2026-06-25 继续只读复核登录日志和操作日志删除/清空/导出/详情链路，检查两个页面表格列、删除确认、清空确认、详情弹窗、API、Controller、Mapper 物理删除和运行态 `sys_logininfor/sys_oper_log` 样例；确认删除确认只展示编号、清空直接截断、详情字段随列表返回、权限面偏宽、时区和主体快照等风险已由 P2-09、P2-62、P2-90、P3-106、P3-146、P3-171、P3-172、P3-173、P3-174 覆盖，本轮不重复新增日志问题。
- 2026-06-25 继续只读复核系统参数键名唯一性，检查 `system/config/index.vue`、`SysConfigController.add/edit`、`SysConfigServiceImpl.selectConfigByKey/checkConfigKeyUnique/loadingConfigCache`、`SysConfigMapper.xml` 和运行态 `sys_config` 索引/重复键统计；确认当前 `COUNT(*)=8 / COUNT(DISTINCT config_key)=8` 且暂无重复键，但 `sys_config` 只有 `PRIMARY(config_id)`、没有 `config_key` 唯一索引，已新增 P3-230。
- 2026-06-25 继续只读复核锁屏/解锁链路，检查 `lock.vue`、`store/modules/lock.js`、`permission.js`、`login.js`、`TokenController.unlockScreen`、`SysLoginService.unlock`，并用 Redis 复核当前无 `pwd_err_cnt:*`、`login_tokens:*` 为 3；确认锁屏本地状态、刷新身份缺失、坏缓存解析和失败计数/审计问题已由 P3-49、P3-207、P3-218 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核调拨审批配置删除与历史实例引用，检查 `transfer/rules.vue` 删除确认、`transferApprovalRule.js`、`InvTransferApprovalRuleController.remove`、`InvTransferApprovalRuleServiceImpl.deleteRuleById`、规则/节点 mapper 和运行态 `inv_transfer_approval_rule/node/instance/task`；确认当前已有 1 个审批实例引用已不存在的 `rule_id=3`，规则删除缺少实例/任务引用预检，已新增 P3-229。
- 2026-06-25 继续只读复核桌面默认首页 `/index`，检查 `views/index.vue`、旧版 `index_v1.vue`、静态路由、首页快捷入口权限过滤、组织上下文展示和运行态 `sys_config/sys_user/inv_product/inv_stock/inv_sales_order/inv_purchase_order/inv_transfer_order` 计数；确认首页当前仍是入口型工作台，采购待办文案、快捷入口过滤空结果回退和仓库库存入口路由问题已分别由 P3-10、P3-201、P2-25 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核桌面端页面覆盖差集，重新统计 `erp-ui/src/views` 共 93 个 `.vue` 文件，其中 73 个已被报告路径直接命中；剩余 20 个主要是移动端组件、旧首页图表组件和表单构建内部组件。当前路由 `/index` 明确导入 `views/index.vue`，旧 `index_v1.vue` 及 `dashboard/*Chart.vue` 只被旧首页引用；表单构建子组件 `DraggableItem/IconsDialog/TreeNodeDialog/CodeTypeDialog/RightPanel` 的风险已由 P2-97、P3-54、P3-233 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核通知公告管理、顶部公告、详情抽屉和阅读用户副页面，检查 `notice/index.vue`、`ReadUsers.vue`、`HeaderNotice/index.vue`、`DetailView.vue`、通知公告 API、`SysNoticeController`、`SysNoticeReadServiceImpl`、`SysNoticeMapper.xml`、`SysNoticeReadMapper.xml`，并用 MySQL 复核当前运行态 `sys_notice=0`、`sys_notice_read=0`、`sys_notice_read` 存在 `uk_user_notice(user_id, notice_id)` 唯一约束且无 `sys_notification` 表；确认已读重复写入已有唯一键兜底，其他风险已由 P2-35、P2-88、P2-128、P3-19、P3-52、P3-62、P3-93、P3-94、P3-95、P3-100、P3-148、P3-206 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核系统工具代码生成删除链路，检查 `tool/gen/index.vue` 删除按钮和确认文案、`api/tool/gen.js`、`GenController.remove`、`GenTableServiceImpl.deleteGenTableByIds`、`GenTableMapper.deleteGenTableByIds`、字段配置删除链路，并用 MySQL 对照 `gen_table/gen_table_column` 与 `tool:gen:*` 角色授权；确认当前无存量生成配置，但删除确认只展示表编号且会物理删除字段配置，已新增 P3-228。
- 2026-06-25 继续只读复核劳动合同发起签约权限依赖，检查劳动合同页 `发起签约/employeeOptions/schemeOptions`、用户列表和薪资配置 API、`OaLaborContractController.save`、`SysUserController.list`、`SysSalaryConfigController.options/items`，并用 MySQL 对照运行态 `sys_menu/sys_role_menu/sys_user_role/sys_user_shop`；确认运营经理当前有劳动合同新增/发送/模板权限，但没有 `system:user:list` 和 `system:salary:query`，发起签约会卡在员工和薪资选项不可加载，已新增 P2-163。
- 2026-06-25 继续只读复核固定资产异常批准、普通维修上报和确认上报链路，检查 `fixedAsset/config/index.vue`、`fixedAsset/repair/index.vue`、`api/oa/fixedAsset.js`、`OaFixedAssetConfigController`、`OaFixedAssetRepairController`、`OaFixedAssetServiceImpl.submitRepair/approveExceptionRepair/confirmApprovedRepair/insertLedger` 和运行态 `oa_fixed_asset%` 空表状态；确认普通上报与异常批准都未校验商品必须属于当前店铺启用固定资产配置，异常批准在门店确认前已写额度流水，维修详情/统计/状态机/额度读写边界等风险已由 P2-48 到 P2-51、P3-82、P3-97、P3-139 到 P3-140、P3-159 到 P3-165 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核 OA 待办/已办审批工作台按钮权限，检查 `oa/todo/index.vue`、`oa/done/index.vue`、`api/oa/purchase.js`、`OaPurchaseController.detail/approve`、`OaPurchaseServiceImpl`、`SysMenuMapper.selectMenuPermsByUserId` 和运行态 `sys_menu/sys_role_menu/oa_purchase/oa_purchase_comment/ACT_RU_TASK/ACT_HI_TASKINST`；确认已办详情调用停用采购查询权限控制的详情接口，待办通过/驳回按钮前端未接 `oa:todo:approve`，已新增 P2-162。
- 2026-06-25 继续只读复核薪资配置权限语义，检查员工绑定 tab 的 `v-hasPermi`、`salaryConfig.js` 用户薪资接口、`SysSalaryConfigController` 员工/角色绑定权限注解和运行态 `sys_menu/sys_role_menu/sys_role_salary_scheme/sys_user_salary_scheme`；确认员工薪资绑定查询和保存复用 `system:salary:role`，而菜单名为“角色薪资配置”且角色薪资页未真正上线，已新增 P2-161。
- 2026-06-25 继续只读复核系统监控、固定资产、通用上传删除和字典标签共用链路：检查 `/monitor/online`、`/monitor/job`、`/monitor/job-log`、`oa/fixedAsset/config`、`oa/fixedAsset/repair`、`FileUpload/ImageUpload`、`SysFileController`、本地/Minio 文件删除实现、`DictTag/DictData` 和运行态字典/固定资产/调度任务数据。确认本轮发现的风险均已由 P2-10、P2-39、P2-40、P2-41、P2-48、P2-49、P2-50、P2-76、P2-96、P2-108、P3-56、P3-150、P3-159 到 P3-165、P3-169 到 P3-171、P3-202、P3-210、P3-212、P3-223 等既有编号覆盖；本轮不重复新增问题，只补强 P3-150 的取消编辑后附件引用断链场景。
- 2026-06-25 继续只读复核运行态全表覆盖和日志计数，使用 MySQL 动态 SQL 对 `BossERP_stock_state_75c59ee` 160 张基础表逐表执行精确 `COUNT(*)`，确认当前仍为 67 张非空表且非空表名均已在本报告出现；本轮未发现新的非空业务表遗漏。同时更新运行态日志口径为 `sys_logininfor=501`、成功 442、失败 59，`sys_oper_log=244`、`sys_job=3`、`sys_job_log=0`。
- 2026-06-25 继续只读复核 OA 首页工作台口径，检查 `erp-ui/src/views/oa/index.vue`、运行态 OA 菜单和角色授权，并用 MySQL 对照 `oa_purchase/oa_attendance_record/oa_salary_config/sys_user_salary_scheme/oa_labor_contract/oa_labor_contract_event/oa_labor_contract_template/oa_company_seal_config/oa_fixed_asset%` 数据量；确认 `/oa/index` 仍是采购审批静态说明，没有读取或汇总已有考勤、工资、劳动合同、固定资产模块状态，已新增 P3-227。
- 2026-06-25 继续只读复核库存主数据状态和分类排序兜底，检查分类、商品、供应商、客户页面控件，`InvProductCategory/InvProduct/InvSupplier/InvCustomer` 实体、保存服务和 mapper，并用 MySQL 统计当前状态分布；确认当前存量 `inv_product_category/inv_product/inv_supplier` 状态均为合法 `0`、客户表为空，但后端缺少 `status/cooperationStatus/orderNum` 白名单和范围校验，已新增 P3-226。
- 2026-06-25 继续只读复核运行态数据库对象覆盖，检查 `SHOW FULL TABLES`、逐表 `COUNT(*)`、`information_schema.TRIGGERS/ROUTINES/PARAMETERS`、应用 datasource 配置和全仓过程名调用；确认当前 160 张表均为基础表、67 张非空表已被报告覆盖、未命中的 26 张表均为 0 行，6 个状态触发器已由 P2-91 覆盖，2 个 `root@localhost` 动态 DDL 存储过程未在源码或 SQL 中出现，已新增 P2-160。
- 2026-06-25 继续只读复核菜单层级和重复权限数据，检查按钮父级、父菜单停用子菜单启用、启用重复权限码、重复组件路径和隐藏/停用菜单授权；确认当前运行态 `button_parent_count=0`，旧 `4109 / 仓库出库修改 / inv:transfer:edit / parent_id=4107` 已不在当前库，P3-80 已修正为历史脏数据记录，当前仍保留 P3-198 的菜单保存链路结构校验风险和 P3-165 的重复权限治理风险。
- 2026-06-25 继续只读复核权限码差集和菜单组件对应关系，抽取前端 `v-hasPermi/hasPermi`、后端 `@RequiresPermissions`、运行态 `sys_menu.perms` 和 `sys_menu.component`；确认当前带组件路径的运行态菜单均能在 `erp-ui/src/views` 找到对应文件，数据库启用但源码未使用的 21 个权限码与报告顶部权限差集记录一致，其中重置密码、采购/销售/退货/盘点修改权限、调拨轨迹、系统查询/在线查询/批量强退、薪资导入、系统工具、监控外链和系统接口等均已有对应问题覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核用户组织授权默认值唯一性，检查 `sys_user_shop` 表结构、重复授权、多个默认组织统计、店铺授权页 `editableShopIds/preservedShopIds` 保存逻辑、`SysUserShopController.save`、`SysUserShopServiceImpl.saveUserShops/insertUserShopBindings` 和 `SysUserShopMapper`；确认当前运行态没有重复授权或多个默认组织脏数据，相关风险已由 P3-108、P3-166、P3-167 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核角色数据范围枚举兜底，检查角色数据权限弹窗、`SysRoleController.dataScope`、`SysRole.dataScope`、`SysRoleServiceImpl.authDataScope`、`SysRoleMapper.updateRole`、`DataScopeAspect` 和运行态 `sys_role.data_scope` 分布；确认当前 11 个角色均为合法 `1/4`，但后端缺少 `1-5` 白名单且非法值会让 DataScope 切面不拼接范围 SQL，已新增 P2-159。
- 2026-06-25 继续只读复核在线用户强退和批量强退权限链路，检查在线用户页面按钮、`api/monitor/online.js`、`SysUserOnlineController`、运行态 `monitor:online:*` 菜单授权和既有报告条目；确认 `monitor:online:query/batchLogout` 仍是权限树启用但源码未使用的按钮权限，单条强退仍由 `monitor:online:forceLogout` 控制，相关风险已由 P2-41、P2-96、P3-79、P3-96、P3-169 覆盖，本轮不重复新增问题。
- 2026-06-25 继续只读复核部门组织树排序链路，检查部门管理页排序输入和保存、`SysDeptController.updateSort`、`SysDeptServiceImpl.updateDeptSort`、`SysDeptMapper` 组织树排序 SQL，并用 MySQL 统计运行态同级重复排序；确认 `parent_id=100` 下 `order_num=1/2` 各有 2 个有效组织，组织树查询仅按 `parent_id, order_num` 排序且无稳定兜底，已新增 P3-225。
- 2026-06-25 继续只读复核系统基础资料状态枚举和排序范围，检查岗位、字典类型、字典数据、系统参数的前端控件、Controller `@Validated` 入口、实体校验注解和运行态 `sys_post/sys_dict_type/sys_dict_data/sys_config` 异常值统计；确认当前存量无非法状态、负排序或异常内置标记，但后端缺少统一枚举/范围兜底，已新增 P3-224。
- 2026-06-25 继续只读复核岗位管理与用户岗位绑定闭环，检查 `system/post` 页面、用户新增/编辑岗位选择器、`SysPostController/SysPostServiceImpl/SysPostMapper`、`SysUserController.getInfo`、`SysUserServiceImpl.insertUserPost`、调拨审批候选人 mapper 和运行态 `sys_post/sys_user_post/inv_transfer_approval_node`；确认运行态 `sys_post=10`、`sys_user_post=18`，`qa_audit_62201` 等 7 个有效账号无岗位，调拨审批节点仍保存 `zdjl/yyjl` 两个岗位编码。本轮未新增重复问题，岗位写权限、岗位编码引用风险、停用岗位绑定、配置状态缺岗位和岗位选择框权限已分别由 P2-99、P2-61、P3-143、P3-186、P3-193 覆盖。
- 2026-06-25 继续只读复核新增用户后的店铺/仓库授权闭环，检查用户页 `handleShopScope/confirmShopScopeAfterCreate/submitForm`、店铺授权页 `applyRouteUserQuery/focusRouteUser/submitShopScope`、后端 `SysUserController.add` 和 `SysUserServiceImpl.insertUser`，并用 MySQL 复核运行态 `qa_audit_62201` 当前为 `user_id=122`、角色 `103 / 店长 / dz`、主部门 `103 / 研发部门`、显式授权 `103 / 研发部门 / STORE / default=Y`；确认当前行内授权已能带 `userId`，但新增成功后的“去配置”仍拿不到后端生成的新用户 ID，已补强 P2-06 证据。
- 2026-06-25 继续只读复核运行库全表和文件生命周期口径：对 `BossERP_stock_state_75c59ee` 160 张表逐表计数，并和报告全文做表名反查；确认本轮未发现新的非空业务表遗漏，同时复核当前 schema 不存在 `sys_file_meta/sys_file_version/sys_file_download_log`，25 个有效用户 `sys_user.avatar` 均为空、`file:%` 菜单权限为 0，已把 P3-51、P3-56 中旧的“文件元数据表 0 行”证据修正为当前“表不存在/未接入”口径。
- 2026-06-25 继续只读复核定时任务 Cron 生成器周规则，检查 `monitor/job/index.vue` 的生成器入口、`Crontab/week.vue` 的 `average02#average01` 生成逻辑、`Crontab/index.vue` 对 `#` 的反解析分支和后端 `SysJobController/CronUtils` 校验；用静态脚本模拟 `2#1` 会回显为第 2 周星期日并再生成 `1#2`，且 MySQL 当前 `sys_job` 3 条内置任务暂无 `#` 样本，已新增 P3-223。
- 2026-06-25 继续只读复核 OA 页面组织上下文链路，检查 `permission.js` 路由守卫、`shopContext/request` 组织头、`OaBaseController`、`AbstractShopScopeService` 和考勤/工资/采购/合同/固定资产 Controller；使用 admin 与 `qa144822s` token 对 `/oa/attendance/my` 做不带/带 `Dept-NumId` 的只读请求，确认 OA 路由未纳入组织选择守卫，管理员缺组织头可返回全局数据、普通账号缺组织头返回后端错误，已新增 P2-133。
- 2026-06-25 继续只读复核选择组织取消后的前端登出链路，检查 select-shop `handleCancel`、`FedLogOut`、`Login`、路由守卫 `roles.length` 和 `GenerateRoutes`；确认 `FedLogOut` 只清 token 不清角色/权限/动态路由，新账号同 SPA 登录可能复用上一账号前端身份和菜单，已新增 P2-132。
- 2026-06-25 继续只读复核全局重复提交防护链路，检查 `request.js` 的 `Authorization/Dept-NumId` 请求头、`sessionObj` 判定键、`cache.session`、登录/退出流程和全仓 `POST/PUT` 调用；确认 113 个写类请求默认共用只含 `url/data/time` 的防重状态，且只有登录接口显式关闭重复提交拦截，已新增 P3-222。
- 2026-06-25 继续只读复核通用下载失败处理链路，检查全局 `Vue.prototype.download`、`request.js` 的 `download()`、`blobValidate()`、待办/已办采购导出 loading、移动端导出 Promise 包装、导入模板下载和全仓 `this.download(...)` 调用；确认公共下载方法在业务错误分支只弹消息不 reject，且 MIME 判断只精确识别 `application/json`，已新增 P3-221。
- 2026-06-25 继续只读复核全局主题色控件挂载链路，检查 `App.vue` 根组件、`ThemePicker/index.vue`、设置抽屉中的真实主题选择器，以及报告内多处空白路由只剩“清空 / 确定”的记录；确认根应用全局隐藏 ThemePicker 会在路由失败状态下留下诊断噪声，已新增 P3-220。
- 2026-06-25 继续只读复核布局设置保存反馈链路，检查 `Settings/index.vue` 的“保存配置/重置配置”按钮、`saveSetting/resetSetting`、全局 `modal.loading/closeLoading` 插件和常规业务表单保存提示；确认保存配置的 loading 立即关闭且没有成功/失败/刷新说明，已新增 P3-219。
- 2026-06-25 继续只读复核锁屏和布局设置本地缓存解析链路，检查 `lock.js` 的 `screen-lock` 初始化、`settings.js` 的 `layout-setting` 初始化、Vuex store 同步 import、根应用创建顺序以及设置抽屉重置入口；确认本地 JSON 坏值没有解析容错，应用启动前就可能抛错，已新增 P3-218。
- 2026-06-25 继续只读复核内链 iframe 容器，检查 `SysMenuServiceImpl.buildMenus/getComponent/isInnerLink` 对 `InnerLink` 和 `meta.link` 的生成、`AppMain` 的 iframe 分支、`IframeToggle` 渲染逻辑、`InnerLink` 固定高度计算，以及 `AppMain` 在固定头部和页签开启时的真实高度；确认内链 iframe 高度不跟随页签、窗口和布局状态变化，已新增 P3-217。
- 2026-06-25 继续只读复核桌面面包屑多级路径解析，检查 `Breadcrumb/index.vue` 的 `pathNum > 2` 分支、`/\/\w+/gi` 正则和 `getMatched` 递归匹配逻辑，并对照动态隐藏路由 `user-auth/role`、`role-auth/user`、`dict-data/index`、`job-log/index`、`stock-log`、`gen-edit/index` 以及固定资产 `fixed-asset` 路径；用 Node 只读模拟确认连字符路径会被拆成错误片段，已新增 P3-216。
- 2026-06-25 继续只读复核布局设置中页签和持久化页签的父子开关闭环，检查 `Settings/index.vue` 的 `tagsView/tagsViewPersist` 模板绑定、computed setter、`saveSetting()` 清缓存条件，`Layout` 挂载 `<tags-view>` 的条件，以及 `TagsView.initTags()` 的恢复入口；确认关闭页签不会同步关闭持久化标签或清理旧标签缓存，已新增 P3-215。
- 2026-06-25 继续只读复核标签页持久化的多账号边界，检查 `settings.js` 默认值、设置抽屉保存的 `layout-setting`、`TagsView.initTags()` 恢复入口、`tagsView` store 的 `tags-view-visited` 保存/恢复字段，以及 `user.js` 登录/退出流程；确认标签页缓存是浏览器级固定 key，切换账号后不会按当前用户或权限过滤，已新增 P3-214。
- 2026-06-25 继续只读复核公共 404 兜底错误页，检查常量路由、动态路由 catch-all、`error/404.vue` 的模板文案、返回首页动作和固定宽度样式，并对照既有 P3-200 的 401 权限页问题；确认 404 页不展示失败路径或诊断信息，且固定 1200px 布局在窄屏/缩放场景下容易溢出，已新增 P3-213。
- 2026-06-25 继续只读复核调度日志任务副页面重置链路，检查 `monitor/job/log.vue` 的路由 `jobId` 初始化、搜索表单字段、`resetQuery/getList` 和 `SysJobLogMapper.selectJobLogList` 后端过滤条件；确认从任务行进入日志后点击重置会清空任务名称和任务组，导致页面从任务日志范围退化为全局日志范围，已新增 P3-212。
- 2026-06-25 继续只读复核通用分页滚动链路，检查 `Pagination` 默认 `autoScroll`、`scroll-to` 的 document/body 滚动实现、桌面 `Layout/AppMain` 固定头部下的独立滚动容器，以及全仓分页调用；确认通用分页翻页后滚动目标与 `.app-main`/弹窗内容区不匹配，已新增 P3-211。
- 2026-06-25 继续只读复核前端字典缓存同步链路，检查 `DictData` 请求缓存、`Dict.init/loadDict` 的组件本地字典副本、字典数据页新增/修改/删除后的 `dict/removeDict`、Vuex `dict` 模块和 `AppMain keep-alive`；确认字典更新只清 Vuex 缓存，不会自动刷新已打开页面的本地字典数组，已新增 P3-210。
- 2026-06-25 继续只读复核用户管理列显隐偏好，检查 `RightToolbar` 的 `storageKey` 恢复/保存逻辑、用户管理页工具栏挂载方式、用户表格列 `columns.*.visible` 和全仓 `RightToolbar` 调用；确认用户页有显隐列功能但未传记忆 key，刷新后列设置只会回到默认值，已新增 P3-209。
- 2026-06-25 继续只读复核顶部全屏和布局大小控件，检查 `Navbar` 的顶部控件挂载、`Screenfull` 图标按钮、`SizeSelect` 下拉标签/成功提示、`app` store Cookie 持久化和 Element 初始化读取；确认全屏缺少可理解提示且布局大小反馈仍为英文，已新增 P3-208。
- 2026-06-25 继续只读复核锁屏刷新恢复链路，检查 `lock.js` 的本地锁定状态、`permission.js` 的路由守卫顺序、`user.js` 的用户信息初始化和 `GetInfo` 写入、`getters.js`、`lock.vue` 的头像/昵称读取，以及既有锁屏问题覆盖；确认锁屏刷新会先进入 `/lock` 且不会先拉取用户身份，已新增 P3-207。
- 2026-06-25 继续只读复核顶部公告历史入口，检查 `Navbar/HeaderNotice` 模板和加载逻辑、`SysNoticeController.listTop`、`SysNoticeReadMapper.selectNoticeListWithReadStatus/selectUnreadCount`、公告路由/管理列表权限和运行态角色授权矩阵；确认顶部公告只有最近 5 条预览且无更多入口，多数业务角色没有公告历史列表权限，已新增 P3-206。
- 2026-06-25 继续只读复核字典项删除审计链路，检查字典数据副页面删除按钮和确认文案、字典数据 API、`SysDictDataController.remove`、`LogAspect` 写入标题逻辑、`SysOperLogMapper` 按标题筛选逻辑，并用 MySQL 复核运行态 `sys_dict_data/sys_dict_type` 数量、`system:dict:remove` 授权和现有字典日志样本；确认字典项删除会被记录为“字典类型”删除，已新增 P3-205。
- 2026-06-25 继续只读复核客户/供应商编辑权限依赖，检查客户页和供应商页编辑按钮、`openForm` 调详情接口、两个前端 API、`InvCustomerController.getInfo`、`InvSupplierController.getInfo` 和运行态角色授权矩阵；确认默认角色暂同时具备 query/edit，但权限模型允许只授 edit 后编辑弹窗加载失败，已新增 P3-204。
- 2026-06-25 继续只读复核 OA 考勤打卡/签退权限边界，检查考勤页按钮、考勤 API、`OaAttendanceController.checkIn/checkOut`、运行态 `sys_menu` 的 `oa:attendance:*` 节点和角色授权矩阵；确认写入考勤的两个接口只要求登录且没有对应按钮权限，已新增 P3-203。
- 2026-06-25 继续只读复核店铺授权页页内搜索/重置链路，检查 `system/shop/index.vue` 右侧目标展示、`getList/handleQuery/resetQuery/handleCurrentUserChange/loadCurrentUserShop/submitShopScope` 和 `SysUserShopController.save`；确认列表刷新不清空缓存的 `currentUser`，保存仍按上一用户 `userId` 写入，已新增 P2-129。
- 2026-06-25 继续只读复核定时任务顶部日志入口，检查 `monitor/job/index.vue` 顶部按钮、行内下拉、`handleCommand/handleJobLog` 和调度日志路由参数处理；确认顶部“日志”按钮未传任务行且方法直接读取 `row.jobId`，已新增 P3-202。
- 2026-06-25 继续只读复核桌面首页快捷入口权限过滤，检查 `index.vue` 的 `quickLinks/visibleRoutePaths/availableQuickLinks/isQuickLinkVisible`，并用 MySQL 复核运行态 `sxs` 角色和用户 `123` 的启用菜单数量均为 0；确认过滤结果为空时首页会回退展示全部预置入口，已新增 P3-201。
- 2026-06-25 继续只读复核公共 401/404 错误页链路，检查常量路由、白名单、动态路由 catch-all、请求拦截器和 401 页面返回文案，确认 `/401` 未接入真实菜单/API 权限失败路径，已新增 P3-200。
- 2026-06-25 继续只读复核个人中心资料保存联系方式校验，检查 `profile/userInfo.vue` 前端规则、`SysProfileController.updateProfile`、`SysUser` 邮箱/手机号注解、`SysUserMapper.updateUser` 和运行态 `sys_user` 联系方式格式分布；确认当前无异常格式存量，但个人中心后端不触发 Bean Validation 且手机号无服务端格式规则，已新增 P3-199。
- 2026-06-25 继续只读复核菜单父级类型保存链路，检查菜单表格行内新增按钮、`handleAdd/getTreeselect/submitForm`、`SysMenuController.add/edit`、`SysMenuServiceImpl.insertMenu/updateMenu`，并用 MySQL 复核当前启用按钮节点数量和按钮作为父级的存量数量；确认当前无存量按钮父级脏数据，但页面和后端仍允许再次写入，已新增 P3-198。
- 2026-06-25 继续只读复核系统参数内置标记保护链路，检查参数编辑弹窗 `configType` 单选、`SysConfigMapper.updateConfig` 对 `config_type` 的更新、`SysConfigServiceImpl.updateConfig/deleteConfigByIds` 的保存和删除保护逻辑，并用 MySQL 复核当前 8 条参数均为内置且 `common/zjl` 同时持有参数修改和删除权限；确认可先改内置标记再绕过删除保护，已新增 P3-197。
- 2026-06-25 继续只读复核内置系统参数键名维护链路，检查参数编辑弹窗 `configKey` 输入、`SysConfigController.edit`、`SysConfigServiceImpl.updateConfig/deleteConfigByIds`、运行代码对 `sys.account.registerUser/sys.account.chrtype/sys.user.initPassword` 等 key 的硬编码读取，并用 MySQL 复核当前 8 条参数均为内置且 `common/zjl` 持有 `system:config:edit`；确认内置参数可被改 key，已新增 P3-196。
- 2026-06-25 继续只读复核系统参数值展示和导出链路，检查参数设置页面表格列、编辑弹窗、`SysConfigController.list/export`、`SysConfigMapper.selectConfigVo`、`SysConfig.configValue` Excel 注解，并用 MySQL 复核运行态 `sys_config` 当前 8 条参数和 `system:config:list/query/export` 角色授权；确认参数页和导出会明文展示 `sys.user.initPassword=123456` 等敏感配置值，已新增 P3-195。
- 2026-06-25 继续只读复核定时任务新增状态链路，检查新增弹窗状态字段显示条件、`reset/submitForm/addJob`、`SysJobController.add`、`SysJobServiceImpl.insertJob`、`SysJobMapper.insertJob` 和运行态 `sys_job` 状态分布；确认前端新增表单提交正常状态但后端强制保存为暂停，页面缺少默认暂停提示，已新增 P3-194。
- 2026-06-25 继续只读复核系统基础资料选择框读取边界，检查岗位、字典类型、角色的 optionselect 前端 API、Controller 权限注解、Mapper 全量查询和运行态角色矩阵；确认岗位/字典类型选择框只要求登录且返回全量基础资料，而角色选择框要求 `system:role:query`，已新增 P3-193。
- 2026-06-25 继续只读复核部门编辑父级迁移链路，检查部门表单上级部门下拉、`handleUpdate/submitForm`、`SysDeptController.excludeChild/edit`、`SysDeptServiceImpl.insertDept/updateDept/updateParentDeptStatusNormal`、`SysDeptMapper.selectDeptList` 和运行态 `sys_dept` 状态分布；确认新增部门会拒绝停用父级，但编辑迁移父级不校验新父级状态，已新增 P3-192。
- 2026-06-25 继续只读复核角色列表授权影响展示，检查角色表格列、数据权限弹窗、`SysRoleMapper.selectRoleVo`、`SysRole` Excel 注解，并用 MySQL 聚合 `sys_role_menu/sys_user_role` 的菜单数和用户数；确认角色列表不展示数据范围、菜单数量、已授权用户数或空权限角色标识，已新增 P3-191。
- 2026-06-25 继续只读复核角色管理受保护角色交互，检查角色列表选择列、顶部修改/删除、状态开关、行内操作隐藏、`handleSelectionChange/handleUpdate/handleDelete/handleStatusChange`、`SysRoleController.edit/changeStatus/remove` 和 `SysRoleServiceImpl.checkRoleAllowed/deleteRoleByIds`，并用 MySQL 确认 `role_id=1 / role_key=admin` 当前启用且只有 admin 一个成员；确认超级管理员角色行内操作被隐藏，但状态开关和顶部按钮仍可进入后端必然拒绝的失败流程，已新增 P3-190。
- 2026-06-25 继续只读复核角色授权用户取消授权链路，检查 `role/authUser.vue` 的已授权用户表、单个/批量取消确认、角色授权 API、`SysRoleController.cancelAuthUser/cancelAuthUserAll`、`SysRoleServiceImpl.checkAuthUserScope/deleteAuthUser/deleteAuthUsers`、`SysUserRoleMapper` 删除 SQL，并用 MySQL 统计当前 25 个启用账号均只有 1 个角色；确认角色侧取消授权不会识别最后一个角色，已新增 P3-189。
- 2026-06-25 继续只读复核用户分配角色页授权影响展示，检查 `user/authRole.vue` 角色表列、选择和提交逻辑、`SysUserController.authRole`、`SysRoleMapper.selectRoleVo`、`SysRole` 字段以及运行态 `sys_role/sys_role_menu/sys_user_role` 样本；确认页面不展示角色状态、数据范围、备注、菜单数量或已授权用户数，已新增 P3-188。
- 2026-06-25 继续只读复核用户详情账号画像闭环，检查用户详情抽屉、用户详情 API、店铺授权 API、`SysUserController.getInfo` 和运行态 `sys_user_shop` 多组织样本；确认详情抽屉已展示岗位和角色，但不展示店铺/仓库授权、默认组织或配置状态，已新增 P3-187。
- 2026-06-25 继续只读复核用户配置状态与岗位关系，检查用户列表配置状态筛选、表格列、`setupStatusLabel`、`SysUserMapper.selectUserList`、`SysUser` 字段和运行态 `sys_user_role/sys_user_post/sys_user_shop` 样本；确认 7 个有角色和组织授权但无岗位的账号会按现有规则落入“已完成”，已新增 P3-186。
- 2026-06-25 继续只读复核店铺授权页权限依赖，检查 `system/shop/index.vue` 的 `listUser/getShopTree/updateUserShop` 调用、用户列表 API、`SysUserController.list`、`SysUserShopController` 权限注解、运行态 `sys_menu` 的 `system:userShop:*` 和 `system:user:list/query` 节点及当前角色授权；确认店铺授权入口和保存按钮使用独立 `userShop` 权限，但首屏用户列表仍依赖用户管理列表权限，已新增 P3-185。
- 2026-06-25 继续只读复核用户列表受保护账号交互，检查顶部“修改”按钮、状态列 `el-switch`、行内操作隐藏、`handleUpdate/handleStatusChange`、`SysUserController.edit/changeStatus`、`SysUserServiceImpl.checkUserAllowed` 和运行态 `user_id=1/admin` 样本；确认 admin 行内操作被隐藏，但顶部修改和状态开关仍可进入后端必然拒绝的失败流程，已新增 P3-184。
- 2026-06-25 继续只读复核用户导出台账字段，检查用户导出 Controller、`SysUser` Excel 注解、`SysUserMapper.selectUserList` 计算字段、用户页面配置状态筛选和运行态 `sys_user_role/sys_user_post/sys_user_shop` 数量；确认导出不包含角色、岗位、店铺/仓库授权和配置状态，已新增 P3-183。
- 2026-06-25 继续只读复核用户列表批量删除和超级管理员保护，检查用户表格选择列、行内操作隐藏、删除确认文案、`SysUserController.remove`、`SysUserServiceImpl.deleteUserByIds/checkUserAllowed`、`SysUser.isAdmin` 与运行态 `user_id=1/admin` 样本；确认 admin 行内删除被隐藏但批量选择仍可选中并到后端才失败，已新增 P3-182。
- 2026-06-25 继续只读复核用户管理账号/昵称字段命名，检查用户列表、用户详情抽屉、`SysUser` Excel 注解、`SysUserMapper` 字段映射和运行态 `sys_user.user_name/nick_name` 样本；确认列表、详情和导出口径对 `userName/nickName` 命名不一致，已新增 P3-181。
- 2026-06-25 继续只读复核用户管理活跃度字段闭环，检查 `sys_user.login_ip/login_date/pwd_update_date` 数据分布、登录信息更新链路、用户列表筛选和列配置、详情抽屉、导出注解与 `SysUserMapper.selectUserList` 条件；确认详情和导出已有最后登录字段，但列表不能按最后登录、从未登录或长期未登录做账号治理，已新增 P3-180。
- 2026-06-25 继续只读复核选择组织预览模式，检查 `permission.js` 预览白名单、`select-shop/index.vue` 演示组织和确认逻辑、`shopContext.js` 会话写入、`request.js` 的 `Dept-NumId` 请求头，并用 MySQL 对照演示 `dept_id=1001/1002/3001/2001/2002/3002` 在 `sys_dept/sys_user_shop` 均不存在；确认预览模式会把演示组织写进真实会话上下文，已新增 P3-179。
- 2026-06-25 继续只读复核代码生成元信息校验链路，检查 `basicInfoForm.vue`、`genInfoForm.vue`、`editTable.vue`、`GenController.editSave`、`GenTableServiceImpl.validateEdit`、`GenTable` 校验注解、`GenTableMapper.xml`、`VelocityUtils.prepareContext/getFileName/getPermissionPrefix` 和生成模板引用；使用 MySQL 对照当前 `gen_table=0/gen_table_column=0` 以及 `common/zjl` 的 `tool:gen:edit/import/preview/code` 授权，确认生成元信息只校验必填且会进入生成路径、权限码和菜单 SQL，已新增 P3-178。
- 2026-06-25 继续只读复核顶部公告详情权限和管理列表正文返回边界，检查 `Navbar/HeaderNotice/DetailView`、公告 API、`SysNoticeController.listTop/getInfo`、`SysNoticeMapper.selectNoticeVo`、`SysNoticeReadMapper.selectNoticeListWithReadStatus`，并用 MySQL 对照 `system:notice:query` 授权角色和当前有效业务账号；确认顶部公告列表只要求登录但详情要求系统公告查询权限，且后台列表按 `system:notice:list` 批量返回正文，已新增 P2-128。
- 2026-06-25 继续只读复核系统基础资料备注字段闭环，检查角色、岗位、字典类型、字典项和参数页面列表列、编辑弹窗、导出入口、实体 `@Excel` 注解，并用 MySQL 统计 `sys_role/sys_post/sys_dict_type/sys_dict_data/sys_config` 非空备注数量；确认备注可维护且运行态有值，但导出台账不包含备注，已新增 P3-177。
- 2026-06-25 继续只读复核部门负责人和联系方式闭环，检查部门管理页搜索区、列表列、编辑弹窗、前端校验、`SysDeptController.add/edit`、`SysDeptMapper.xml` 保存字段，并用 MySQL 对照 `sys_dept.leader/phone/email` 与 `sys_user.user_name/nick_name` 匹配关系；确认电脑端负责人和联系方式只在编辑弹窗可见，已新增 P3-176。
- 2026-06-25 继续只读复核系统基础页导出确认范围，检查用户、角色、岗位、参数、字典类型和字典项页面的筛选字段、`handleExport` 传入的 `filterLabel`、实际下载参数、对应后端 export 接口和 mapper 查询条件，并用 MySQL 统计运行态基础表数量及用户配置状态样本；确认确认框只展示单个主字段或主字段+日期，已新增 P3-175。
- 2026-06-25 继续只读复核日志删除确认链路，检查登录日志、操作日志、调度日志表格列、选择逻辑、删除确认文案、三个后端删除接口和 mapper 物理删除语句，并用 MySQL 抽样当前登录日志/操作日志最新记录；确认删除确认只展示 ID，已新增 P3-174。
- 2026-06-25 继续只读复核日志导出确认范围，检查 `exportConfirm.js`、登录日志/操作日志/调度日志前端筛选项、`handleExport` 传入的 `filterLabel`、实际 `download` 参数、三个后端导出接口和 mapper 查询条件，并用 MySQL 抽样当前登录日志 IP/状态、操作日志类型/状态分布；确认确认框只展示单个主字段和日期，已新增 P3-173。
- 2026-06-25 继续只读复核登录日志解锁接口语义，检查登录日志页面 `handleUnlock`、登录日志 API、`SysLogininforController.unlock`、用户新增/注册/登录用户名校验和运行态账号名、账户解锁操作日志；确认当前无特殊账号名和解锁日志样本，但解锁动作使用 GET 且直接拼接用户名到 URL，已新增 P3-172。
- 2026-06-25 继续只读复核日志状态字典口径，使用 MySQL 查询 `sys_common_status/sys_normal_disable` 字典、`sys_logininfor/sys_oper_log/sys_job_log` 状态分布，并检查登录日志、操作日志、调度日志前端字典引用及 `SysLogininfor/SysOperLog/SysJobLog` 导出转换；确认操作日志和调度日志复用登录日志式 `sys_common_status` 会造成页面筛选与导出状态文案不一致，已新增 P3-171。
- 2026-06-25 继续只读复核定时任务状态开关权限口径，检查 `monitor/job/index.vue` 的状态列和 `handleStatusChange`、调度 API、`SysJobController.changeStatus` 以及当前 `monitor:job:list/query/changeStatus` 授权；确认当前运行态暂无 list-only 任务角色，但前端开关未按后端启停权限隐藏或禁用，已新增 P3-170。
- 2026-06-25 继续只读复核在线用户搜索链路，检查在线用户页面输入框、`SysUserOnlineController.list`、`SysUserOnlineServiceImpl.selectOnlineByIpaddr/selectOnlineByUserName/selectOnlineByInfo` 和当前 Redis `login_tokens:*` 样本；确认当前 16 个在线 token 均为 `admin / 127.0.0.1`，但后端搜索只做完全相等匹配，已新增 P3-169。
- 2026-06-25 继续只读复核代码生成导入保存幂等性，检查导入弹窗、`GenController.importTableSave`、`GenTableServiceImpl.importGenTable/generatorCode/synchDb`、`GenTableMapper.selectDbTableList/selectDbTableListByNames/selectGenTableByName` 和运行态 `gen_table/gen_table_column` 表结构；确认当前库暂无生成配置，但后端保存接口和数据库缺少同表唯一兜底，已新增 P3-168。
- 2026-06-25 继续只读复核用户组织授权查询数据最小化，检查 `userShop` 前端 API、店铺授权页 `loadCurrentUserShop/loadUserShopScopes/formatShopScope`、`SysUserShopController.getInfo/batch`、`SysUserShopServiceImpl.selectUserShopScope/selectShopDeptIdsByUserIds` 和运行态 `system:userShop:*` 授权用户；确认当前无部分范围管理员样本，但接口会向非管理员返回不可编辑组织的具体 `deptId`，已新增 P3-167。
- 2026-06-25 继续只读复核用户组织授权非管理员保存边界，检查店铺授权页 `editableShopIds/preservedShopIds` 展示与保存、`SysUserShopServiceImpl.saveUserShops/insertUserShopBindings/selectEditableExistingDeptIds`、`SysUserShopMapper` 删除/插入和运行态 `sys_user_shop` 表结构；确认当前库暂无重复默认组织，但非管理员部分保存可能在保留旧默认组织时新增第二个默认值，已新增 P3-166。
- 2026-06-25 继续只读复核固定资产配置数值校验链路，检查配置弹窗数字控件、前端必填规则、`OaFixedAssetConfig` 校验注解、`OaFixedAssetServiceImpl.saveConfig/rebuildQuota`、固定资产建表脚本和运行态表结构；确认数量、金额和年度比例只靠前端限制，后端和数据库没有范围兜底，已新增 P3-165。
- 2026-06-25 继续只读复核固定资产配置删除引用链路，检查配置页删除按钮、`removeConfig`、`OaFixedAssetServiceImpl.deleteConfigById`、`OaFixedAssetConfigMapper.deleteConfigById`、固定资产建表脚本和运行态表结构；确认删除是物理删除并只重算额度，没有检查维修单/额度流水引用，也没有 `config_id` 外键或配置快照，已新增 P3-164。
- 2026-06-25 继续只读复核固定资产维修页待确认统计卡片，检查维修页汇总卡、`pendingConfirmCount`、分页列表加载、维修列表 Controller 和 Mapper；确认“待确认上报”卡片只统计当前分页 `rows`，且会受当前搜索/状态筛选影响，不是当前组织范围待确认总量，已新增 P3-163。
- 2026-06-25 继续只读复核固定资产配置查询接口和权限链路，检查固定资产配置列表行操作、`openConfigForm`、固定资产 API、`OaFixedAssetConfigController.list/getInfo`、`OaFixedAssetConfigMapper.selectConfigList/selectConfigById` 和运行态固定资产配置权限/表数据；确认电脑端没有详情按钮，编辑只复制列表行且未调用 `getFixedAssetConfig`，`oa:fixedAsset:config:query` 没有真实桌面入口，已新增 P3-162。
- 2026-06-25 继续只读复核固定资产商品选择器搜索口径，检查固定资产配置和异常批准弹窗的商品选择器、`searchProducts`、商品列表 API、`InvProductMapper.selectInvProductList` 和运行态 `inv_product` 编码样例；确认页面提示可按商品名称或编码搜索，但实际只传 `productName`，按样例编码 `WLC-727511` 查询商品名命中 0 条、按商品编码命中 1 条，已新增 P3-161。
- 2026-06-25 继续只读复核固定资产配置编辑归属和额度重算链路，检查固定资产配置编辑弹窗、`openConfigForm/saveConfig`、固定资产 API、`OaFixedAssetConfigController.save`、`OaFixedAssetServiceImpl.saveConfig/rebuildQuota`、`OaFixedAssetConfigMapper.updateConfig` 和运行态固定资产表/权限；确认编辑可修改店铺且 Mapper 允许更新 `shop_dept_id`，但服务层只重算新店铺额度，没有重算原店铺，已新增 P3-160。
- 2026-06-25 继续只读复核固定资产维修详情按钮链路，检查维修上报列表按钮、详情弹窗、`showDetail`、固定资产 API、`OaFixedAssetRepairController.list/getInfo`、`OaFixedAssetRepairMapper.selectRepairList/selectRepairById` 和运行态固定资产维修权限/表数据；确认电脑端详情按钮只复制列表行，未调用详情接口且未受 `oa:fixedAsset:repair:query` 控制，已新增 P3-159。
- 2026-06-25 继续只读复核劳动合同列表字段暴露，检查劳动合同列表前端表格、详情弹窗、`OaLaborContractController.list/detail`、`OaLaborContractMapper.baseColumns/selectOaLaborContractList`、`OaLaborContract` 字段和运行态 `oa_labor_contract/sys_role_menu/sys_user_role/sys_user_shop`；确认列表接口批量返回身份证、工资、合同文件 URL、签署 IP/User-Agent、第三方签约 URL/回调载荷等详情字段，已新增 P2-158。
- 2026-06-25 继续只读复核薪资员工绑定门店校验链路，检查薪资配置前端员工绑定弹窗、`salaryUserOptions/saveUserSalary`、`SysSalaryConfigController.users/saveUserBindings`、`SysUserMapper.selectUserList`、`SysSalaryConfigServiceImpl.saveUserSalarySchemes`、`SysUserSalarySchemeMapper`、`OaSalaryEmployeeMapper` 和运行态 `sys_user/sys_user_shop/sys_user_salary_scheme`；确认下拉接口会按门店筛员工，但保存接口不校验员工与 `shopDeptId` 的关系，也不在删除旧绑定前验证操作者可维护该门店，已新增 P2-157。
- 2026-06-25 继续只读复核角色数据权限弹窗与 `sys_role_dept` 一致性，检查 `role/index.vue` 的 `handleDataScope/dataScopeSelectChange/submitDataScope`、`SysRoleController.deptTree/dataScope`、`SysRoleServiceImpl.authDataScope/insertRoleDept`、`SysDeptServiceImpl.selectDeptListByRoleId`、`SysDeptMapper.selectDeptListByRoleId`、`DataScopeAspect` 和运行态 `sys_role/sys_role_dept`；确认 `common` 角色当前不是自定义范围但仍残留 3 条部门 checkedKeys，切回自定义会误带旧部门，已新增 P3-156。
- 2026-06-25 继续只读复核 OA 考勤多组织今日状态链路，检查 `shopContext/request` 的 `Dept-NumId` 传递、考勤页 `getList/findTodayRecord/loadTodayRecord`、`OaAttendanceController.checkIn/my/list`、`OaAttendanceServiceImpl.checkIn/selectMyRecords/selectAllRecords`、`OaAttendanceRecordMapper.selectByUserIdAndWorkDate` 和运行态 `oa_attendance_record/sys_user_shop`；确认前端按当前组织列表推导今日状态，但后端按账号+日期全局查重，已新增 P3-155。
- 2026-06-24 继续只读复核用户状态启停链路，检查用户列表状态开关、`SysUserController.remove/changeStatus`、`SysUserServiceImpl.checkUserAllowed/updateUserStatus`、`SysUserMapper.updateUserStatus`、`SysLoginService.login`、`HeaderInterceptor` 和 `TokenService`；确认删除接口禁止当前用户但停用接口没有自停保护，且状态停用只影响新登录不清理既有 Redis token，已新增 P3-154。
- 2026-06-24 继续只读复核角色分配用户“添加用户”副弹窗，检查 `role/authUser.vue`、`role/selectUser.vue`、角色 API 和运行态 `sys_user_role/sys_role/sys_dept`；确认当前店长未分配候选 13 人超过默认页大小 10，而选择弹窗没有 `row-key/reserve-selection`、跨页已选摘要或显式清理选择状态，已新增 P3-153。
- 2026-06-24 继续只读复核菜单“是否缓存”与前端 keep-alive 链路，检查菜单管理 `isCache` 表单、`SysMenuServiceImpl.buildMenus/getRouteName/checkRouteConfigUnique`、`AppMain.vue`、`tagsView.js` 和运行态 `sys_menu`；用脚本比对 47 个启用页面的 `route_name/path/component` 与 Vue 组件 `name`，确认 21 个配置为缓存的页面实际无法命中 keep-alive，已新增 P3-152。
- 2026-06-24 继续只读复核 OA 工资“我的/全部”视图和导出链路，检查工资页 `viewScope/getList/handleExport`、工资 API、`OaSalaryController.export`、`OaSalaryServiceImpl.selectMyRecords/selectAllRecords` 和工资记录 mapper；使用 MySQL 确认运行态 `admin/yyjl/zjl` 均有 `oa:salary:list/export`、`dz` 只有 `oa:salary:query`，确认列表会按视图分支但导出固定走全部范围，已新增 P3-151。
- 2026-06-24 继续只读复核通用文件删除链路，检查 `SysFileController.delete`、`LocalSysFileServiceImpl.deleteFile`、`FileUtils.deleteFile`、`FileUpload/ImageUpload` 删除处理，并用 admin token 对不存在的 `/file/public/qa-delete-probe-not-exist-20260624.png` 做非破坏性删除请求；确认后端在真实未删除文件时仍返回 200，已新增 P3-150。
- 2026-06-24 继续只读复核登录页记住密码链路，检查桌面/移动登录表单、`getCookie/handleLogin`、`jsencrypt` 公私钥和 Cookie 写入逻辑；确认“记住密码”会把可逆密码保存 30 天且私钥在前端源码中，已新增 P2-127。
- 2026-06-24 继续只读复核商品图文素材字段和批量维护链路，检查运行态 `inv_product` 图片字段空值、商品表单/详情、库存图片 fallback、`InvProduct` Excel 注解、商品导入模板、导入和导出 Controller；确认页面和数据库有 `imageUrl/image_url`，但导入模板/普通导入/导出漏掉主图 URL，已新增 P3-149。
- 2026-06-24 继续只读复核通知公告富文本图片上传路径，检查 `Editor` 组件的工具栏上传、粘贴上传、`FileUploadUtils` 默认大小/扩展名和当前 `sys_notice` 数据；确认粘贴图片没有复用 `handleBeforeUpload` 的 5MB/类型校验且缺少组件内失败 catch，已新增 P3-148。
- 2026-06-24 继续只读复核通用文件上传公开访问边界，检查 `LocalSysFileServiceImpl`、`ResourcesConfig`、`RefererFilter`、`FilterConfig`、网关 `security.ignore.whites`、劳动合同模板/企业章上传组件和运行态 `oa_labor_contract_template/oa_company_seal_config` URL；使用无 Authorization 的 `curl` 直连当前企业章图片和劳动合同模板均返回 200，确认业务文件落在公开白名单，已新增 P2-126。
- 2026-06-24 继续只读复核权限变更后的在线 token 刷新链路，检查 `TokenService`、`HeaderInterceptor`、`AuthLogic`、`SysUserController.getInfo/insertAuthRole`、`SysRoleController` 授权写接口、`SysUserShopController.save`、Redis `login_tokens:*` 数量和相关源码引用；确认角色/菜单/店铺授权变更不会按受影响用户刷新或清理在线 token，已新增 P2-125。
- 2026-06-24 继续只读复核密码有效期和改密提醒链路，检查 `sys_config`、`sys_user.pwd_update_date` 分布、`SysLoginService.login`、`SysUserController.getInfo/passwordIsExpiration`、`store/modules/user.js`、`passwordResetReminder.js` 和组织选择页提醒跳转；确认密码有效期只产生可取消提醒，没有强制过期阻断，已新增 P3-147。
- 2026-06-24 继续只读复核操作日志和登录日志主体字段，检查 `sys_logininfor/sys_oper_log` 表结构、运行态日志样例、`SysLogininfor/SysOperLog` 实体、`SysRecordLogService.recordLogininfor`、`LogAspect`、两个日志 mapper，以及用户编辑/用户名唯一性逻辑；确认当前日志缺少稳定用户 ID、角色和当前组织上下文，已新增 P3-146。
- 2026-06-24 继续只读复核劳动合同企业章冻结链路，检查企业章弹窗、保存权限、`saveSealConfig`、`selectActiveSealConfig`、`sendContract`、`signContract`、`assertFrozenDocumentMatches`、合同 mapper 和运行态 `oa_company_seal_config/oa_labor_contract/sys_role_menu/sys_user_role`；确认发送时只保存章图片 hash，签署归档重新取当前启用章且合同表没有章配置快照，已新增 P2-124。
- 2026-06-24 继续只读复核劳动合同内置模板维护链路，检查模板列表/编辑弹窗、`saveLaborContractTemplate`、`OaLaborContractController.saveTemplate`、`OaLaborContractServiceImpl.saveTemplate`、模板 mapper 和运行态 `oa_labor_contract_template/oa_labor_contract/sys_role_menu/sys_user_role`；确认内置模板可直接编辑/停用且模板 1 已被 9 份合同引用，已新增 P2-123。
- 2026-06-24 继续只读复核薪资方案删除链路，检查薪资配置前端 `handleDeleteScheme`、`SysSalaryConfigServiceImpl.deleteSalarySchemeByIds`、员工/角色薪资绑定 mapper 和运行态 `sys_salary_scheme/sys_salary_scheme_item/sys_user_salary_scheme/sys_role_salary_scheme`；确认两个薪资方案均有绑定，删除方案会级联删除档位、员工绑定和角色绑定，已新增 P2-122。
- 2026-06-24 继续只读复核调度日志清空链路，检查 `SysJobLogController.clean`、`SysJobLogMapper.xml cleanJobLog`、调度日志前端 `handleClean` 和运行态 `sys_job_log/sys_config`；确认当前日志表为 0 且没有调度日志归档表或保留期配置，但清空动作会直接 `truncate table sys_job_log`，已新增 P3-145。
- 2026-06-24 继续只读复核部门/组织停用和用户主部门链路，检查用户部门树过滤、`TreeSelect.disabled`、`SysUserController.add/edit`、`SysUserServiceImpl.importUser`、`SysDeptController.edit`、`SysDeptServiceImpl.checkDeptDataScope`、`SysDeptMapper.xml`、`SysUserShopServiceImpl` 和运行态 `sys_dept/sys_user/sys_user_shop`；确认当前无停用组织存量脏数据，但停用组织动作不预检用户/授权影响，用户保存主部门后端也不校验启用状态，已新增 P2-121。
- 2026-06-24 继续只读复核角色停用分配链路，检查用户新增/编辑角色选择器、用户分配角色副页面、`SysUserController.add/edit/insertAuthRole`、`SysUserServiceImpl.insertUser/updateUser/insertUserAuth/insertUserRole`、`SysPermissionServiceImpl`、`SysRoleMapper.xml`、`SysMenuMapper.xml` 和运行态 `sys_role/sys_user_role`；确认当前无停用角色绑定脏数据，但后端保存/授权不校验角色启用状态，且停用角色键可能仍进入前端 `roles` 集合，已新增 P3-144。
- 2026-06-24 继续只读复核岗位停用分配链路，检查岗位管理接口、用户新增/编辑岗位选择器、`SysUserController.add/edit`、`SysUserServiceImpl.insertUser/updateUser/insertUserPost` 和运行态 `sys_post/sys_user_post`；确认当前无停用岗位存量脏数据，但用户保存后端没有岗位启用状态兜底，已新增 P3-143。
- 使用 MySQL 查询核对 `sys_user`、`sys_user_shop`、`sys_dept`、`sys_menu`、`sys_config` 和业务表行数。
- 使用数据库菜单 `component` 与 `erp-ui/src/views` 文件路径做运行菜单和前端视图对应检查。
- 使用登录接口、`/getInfo`、`/getRouters`、`/system/dept/warehouse-list` 对比不同账号权限和路由。
- 使用浏览器实测管理员、门店、仓库三类账号的登录、店铺选择、菜单、库存、商品、调拨、OA、用户管理页面。
- 使用浏览器真实创建 `qa_audit_62201`，保存 `103` 研发部门授权，并用该账号完成首次登录和菜单核对。
- 使用管理员仓库上下文补测采购、采购退货、库存、调拨、调拨记录页面；使用门店角色补测客户、销售退货、库存盘点、发货通知、调拨记录页面。
- 2026-06-23 使用浏览器补测系统日志、在线用户、定时任务、薪资配置、调拨审批配置、OA 待办/已办/考勤/工资/劳动合同页面。
- 2026-06-23 使用 `curl` 验证 Sentinel、Nacos、Admin、Swagger 外链可达性；使用 MySQL 比对日志表时间、菜单状态和 OA/薪资/调拨配置数据量。
- 2026-06-24 继续只读复核系统接口菜单，使用 MySQL 检查 `116 / 系统接口 / tool:swagger:list` 菜单和 `common/zjl` 授权；使用匿名和 admin token 分别请求 `/swagger-ui/index.html`、`/webjars/swagger-ui/index.html`、`/system/v3/api-docs`、`/code/v3/api-docs`、`/oa/v3/api-docs`、`/inventory/v3/api-docs`，并检查网关 `springdoc`、白名单和 `SpringDocConfig`，确认菜单地址错误且 API docs 当前未形成可用闭环，已补强 P3-08。
- 2026-06-23 使用浏览器补测仓库商品、分类、供应商、采购、采购退货、库存、调拨、调拨记录，以及门店侧库存、库存盘点、报表、发货通知等页面；使用 MySQL 比对进销存核心表数量。
- 2026-06-23 使用浏览器补测系统管理和系统监控页面的列表、按钮、只读弹窗、副页面；使用 MySQL 比对系统表、薪资绑定表和定时任务表数量。
- 2026-06-23 使用浏览器补测 OA 首页、待办、已办、考勤、工资、劳动合同、采购申请直连、固定资产直连，以及考勤、工资、劳动合同的只读确认框/弹窗；使用 MySQL 比对 OA 表数量和菜单状态。
- 2026-06-24 继续只读复核劳动合同作废流程，使用 MySQL 比对劳动合同角色授权、合同状态和事件分布；检查劳动合同前端“作废”按钮、`OaLaborContractController.voidContract` 和 `OaLaborContractServiceImpl.voidContract`，确认已签合同没有服务层作废保护、原因字段或审批闭环。本轮未调用作废接口。
- 2026-06-24 继续只读复核劳动合同事件哈希覆盖范围，使用 MySQL 统计 `oa_labor_contract_event` 事件类型、空 hash 分布和最新事件样例；检查劳动合同详情表、`OaLaborContractServiceImpl.recordEvent/calculateEventHash`，确认当前事件哈希没有覆盖事件摘要、操作者名称、备注和下载文件动作类型，已新增 P3-102。
- 2026-06-24 继续只读复核运行态/非运行态工作流口径，使用 MySQL 对照 `BossERP_stock_state_75c59ee` 与 `BossERP` 的 `wf_%` 表、`workflow:%` 菜单、`ACT_RU_TASK`、`ACT_HI_PROCINST`、`ACT_RE_PROCDEF`、`wf_instance`、`wf_callback_log`；使用 `/auth/login` 和 `/system/menu/getRouters` 确认当前网页端路由不返回 `workflow/*`；检查 `erp-ui/src/views/workflow`、`erp-ui/src/api/workflow`、`erp-modules/erp-workflow`、`erp-api/erp-api-workflow`，确认工作流仍是 schema 漂移/残留实现问题，已补强 P1-14。
- 2026-06-24 使用 MySQL 按当前运行态 `BossERP_stock_state_75c59ee` 复核非空表清单，确认非空数据主要集中在系统配置/日志、商品库存、调拨审批链、劳动合同/工资/考勤和多批清理备份表；继续用报告正文反查 `P3-29`、调拨详情、系统日志、薪资绑定、岗位绑定和 Activiti 元数据记录，未发现新的非空业务表大类缺口。
- 2026-06-24 使用 MySQL 导出当前运行态全表名并与本报告做逐表反查；对未命中的 28 张表逐表 `count(*)`，确认全部为 0 行，属于 Activiti 空表或空清理备份表，因此本轮只补覆盖说明，不新增问题。
- 2026-06-24 使用 MySQL 导出当前 `sys_menu.perms`，并用 `rg` 从前端/后端源码抽取权限码做差集；复核数据库 226 个权限码、源码 207 个权限码，以及 21 个数据库有而源码未引用、2 个源码需要而权限树缺失的差异项，确认新增缺口仍归入既有权限问题。
- 2026-06-23 使用 MySQL 细化 `oa_purchase`、`oa_purchase_comment`、`ACT_RE_PROCDEF`、`ACT_RE_DEPLOYMENT`、`ACT_RU_TASK`、`ACT_HI_TASKINST`、`sys_dept.leader` 与 `sys_user.user_name` 的对应关系；检查 `oa/todo`、`oa/done`、`oa/purchase` 前端、采购 API、`OaPurchaseController`、`OaPurchaseServiceImpl`、`OaPurchaseMapper.xml`、`OaPurchaseCommentMapper.xml` 和 `purchase-approval.bpmn20.xml`。
- 2026-06-23 使用浏览器补测系统工具直连路由 `/tool/build`、`/tool/gen`、`/tool/gen-edit/index/1` 和系统接口地址；使用 MySQL 比对 `gen_*`、`QRTZ_*`、`ACT_*`、`FLW_*`、`sys_job`、`sys_job_log` 数据状态。
- 2026-06-23 继续细化代码生成导入表来源，使用 MySQL 按 `GenTableMapper.selectDbTableList` 同口径统计当前可导入表，检查 `GenTableMapper.xml`、导入表弹窗和 `GenController.importTableSave`，确认当前过滤规则只排除 `qrtz_%` 和 `gen_%`，未排除流程引擎表、清理备份表或其他内部表。
- 2026-06-24 继续复核系统工具授权边界，使用 MySQL 检查 `sys_menu` 父子状态、`common/zjl/ry` 的 `tool:*` 权限和 `GenTableMapper.selectDbTableList` 同口径导入表范围；确认当前可导入表为 147 张，其中 `ACT_%` 39 张、`FLW_%` 2 张、备份类 38 张，并确认 `SysMenuMapper.selectMenuPermsByUserId` 不会因父菜单停用而排除子权限。
- 2026-06-24 继续只读复核代码生成本地写入模式，检查 `genInfoForm.vue`、`tool/gen/index.vue`、`GenController.genCode/batchGenCode/synchDb`、`GenTableServiceImpl.validateEdit/generatorCode/getGenPath`、`GenTableMapper.xml` 和 `application-dev.yml`；使用 MySQL 复核 `gen_table=0`、`gen_table_column=0`、`title='代码生成'` 操作日志为 0，且 `common/zjl` 均持有完整 `tool:gen:*`，确认当前页面允许保存任意自定义生成路径但运行配置拒绝覆盖，开启覆盖后缺少路径白名单，已新增 P3-110。
- 2026-06-24 继续只读复核代码生成同步表结构按钮，检查 `tool/gen/index.vue` 的同步按钮和确认文案、`erp-ui/src/api/tool/gen.js`、`GenController.synchDb`、`GenTableServiceImpl.synchDb`、`GenTableColumnMapper.xml`、`gen_table/gen_table_column` 表结构和 `sys_oper_log` 代码生成操作记录；确认同步会直接增删/覆盖字段生成配置，但没有差异预览和独立高危权限，已新增 P3-142。
- 2026-06-23 继续细化表单构建页面，检查 `sys_menu` 中 `tool/build` 父子菜单状态、`tool:build:list` 角色授权、`erp-ui/src/views/tool/build/index.vue` 和生成类型弹窗，确认页面为纯前端设计、复制、下载能力，没有服务端保存、共享、审计或按钮级权限。
- 2026-06-23 继续细化角色仓库授权，使用 MySQL 比对 `sys_role_warehouse`、`sys_role`、`sys_dept` 当前关系，使用 `rg` 检查角色仓库表运行引用，并检查角色管理页、`SysRoleController`、`SysDeptServiceImpl.canSelectWarehouse` 和 `SysUserShopServiceImpl.checkUserShopScope`，确认当前仓库选择只按用户级 `sys_user_shop`，不读取角色仓库授权表。
- 2026-06-23 继续细化文件生命周期链路；本轮 MySQL 连接 `127.0.0.1:3306` 失败，未新增运行库计数证据，改用此前报告中 `sys_file_meta/sys_file_version/sys_file_download_log=0` 的记录和当前源码复核。检查 `SysFileController`、`SysFile`、`RemoteFileService`、本地/Minio 文件服务、`FileUpload`、`ImageUpload`、`Editor` 和劳动合同附件页面，确认上传/删除只围绕 URL 字符串，没有接入文件元数据、版本、下载日志或引用检查。
- 2026-06-23 继续细化内部调用认证，MySQL 复核 `security.inner.sign.key` 为空；检查 `InnerAuthAspect`、`SecurityConstants`、`RemoteUserService`、`RemoteConfigService`、`RemoteLogService`、`SysUserController`、`SysConfigController`、`SysOperlogController`、`SysLogininforController` 和网关 `AuthFilter`，确认当前 `@InnerAuth` 只校验 `from-source=inner` 请求头，网关会清理外部同名头但内部接口没有签名密钥、时间戳、nonce 或重放防护。
- 2026-06-23 继续细化系统集成表，使用 MySQL 复核 `sys_api_app`、`sys_webhook`、`sys_webhook_delivery` 均为空并查看字段结构；按 API、应用、Webhook、回调、开放、集成等关键词检查 `sys_menu`，并用 `rg` 检索前端、后端、API 和公共模块，确认当前没有 API 应用管理页、Webhook 管理页、开放 API 鉴权、事件注册、投递队列、失败重试或投递日志查询实现。
- 2026-06-23 继续细化审计保留和归档链路，使用 MySQL 复核 `sys_oper_log`、`sys_logininfor`、`sys_audit_archive` 行数和时间范围，检查 `audit.retention.days` 参数、`sys_audit_archive` 表结构、操作日志/登录日志前端清空按钮、`SysOperlogController`、`SysLogininforController`、`SysOperLogMapper.cleanOperLog` 和 `SysLogininforMapper.cleanLogininfor`，确认当前清空操作直接 `truncate` 正式日志表，没有归档记录、归档文件或保留期处理。
- 2026-06-23 继续细化单据状态审计双轨，使用 MySQL 检查 `inv%status%` 表、旧/备份口径中的 `inv_status_audit`、当前运行态 `inv_document_status_log`、`trg_inv_%status%` 触发器和 `inv:audit:list` 菜单权限；检查 `InvDocumentStatusLogController`、`InvDocumentStatusLogMapper.xml` 和 `sql/erp_inventory_status_audit_20260612.sql`，确认当前运行态 `inv_document_status_log=2` 且存在 6 个状态触发器，但电脑端仍没有审计页面或单据详情时间线入口。
- 2026-06-23 使用浏览器切换 `仓库：仓库` 和 `门店：市场部门` 上下文，补测库存盘点、发货通知、调拨记录、调拨审批配置页面和详情弹窗；使用 MySQL 比对 `inv_stock_check`、`inv_delivery_notice`、`inv_sales_order`、`inv_transfer_order`、`inv_transfer_approval_*`、`inv_transfer_status_log`。
- 2026-06-23 使用浏览器补测 `/login?preview=1`、`/register`、`/index`、`/user/profile`、`/user/profile?activeTab=resetPwd`；使用 MySQL 比对账号注册开关、用户联系方式完整性、OA 采购申请菜单状态和 `oa_purchase` 数据量。
- 2026-06-24 继续源码和 MySQL 复核自助注册后账号完整性：确认 `sys.account.registerUser=false` 且没有默认角色、默认部门、默认店铺或注册审核配置；检查 `register.vue`、`login.vue`、`TokenController.register`、`SysLoginService.register`、`SysUserController.register`、`SysUserServiceImpl.registerUser`、`SysUserMapper.insertUser`、`SysMenuMapper.selectMenuTreeByUserId` 和选择组织页，确认注册开启后只会落基础账号，不会写角色、岗位或店铺/仓库授权，已补强 P1-17。
- 2026-06-23 使用浏览器在 `仓库：仓库` 上下文细化补测 `/cangku/category` 的搜索、重置、新增根分类抽屉、编辑分类抽屉；使用 MySQL 比对 `inv_product_category`、`inv_product` 分类引用、`inv_supplier`、供应商商品/采购历史引用；检查分类和供应商前端组件、菜单配置、后端删除保护逻辑，并复测 `/cangku/supplier` 入口渲染状态。
- 2026-06-23 使用 MySQL 细化商品、分类、供应商 `shop_dept_id` 分布和 `sys_dept` 祖先链；检查 `InvDeptScopeMapper.selectRelatedDeptIds`、`InvProductServiceImpl.saveProduct`、`InvProductCategoryServiceImpl.saveCategory`、`InvSupplierServiceImpl.saveSupplier`，确认仓库上下文列表可见集团主数据但新增保存会写当前仓库。
- 2026-06-24 继续只读复核供应商改名和商品引用链路，使用 MySQL 统计当前 16 个集团供应商名称被 44 条商品引用；检查供应商编辑页、`InvSupplierServiceImpl.saveSupplier/deleteSupplierByIds`、`InvProductServiceImpl.applySupplierFromCatalog`、商品/采购/采购退货 mapper，确认引用按 `supplier_name` 字符串匹配且改名没有同步或保护，已新增 P2-109。
- 2026-06-24 继续只读复核供应商编码规则，使用 MySQL 统计 `inv_supplier` 17 条 `supplier_code` 全为空或 NULL；检查供应商页、`InvSupplierServiceImpl` 和 `InvSupplierMapper.xml`，确认供应商编码未必填、未唯一且列表按编码精确查询，已新增 P3-109。
- 2026-06-24 继续只读复核仓库主流程路由守卫，使用 MySQL 核对商品、分类、采购、供应商、采购退货菜单均启用；检查 `permission.js`、`shopContext.js`、请求拦截器、商品/分类/供应商/采购/采购退货页面和 `InvBaseService`，确认大部分 `/cangku/*` 页面缺少组织选择守卫但接口必需 `Dept-NumId`，已新增 P2-110。
- 2026-06-24 继续只读复核系统参数读取边界，使用 MySQL 核对 `sys_config` 8 条含 `sys.user.initPassword=123456`，并检查 `SysConfigController`、系统用户页默认密码读取、系统参数 API 和角色权限矩阵，确认参数详情/按键名查询只要求登录，已新增 P2-111。
- 2026-06-24 继续只读复核仓库列表接口默认范围，使用 MySQL 确认当前启用仓库 1 个且部分门店账号无仓库授权；检查 `SysDeptController.warehouseList`、`SysDeptServiceImpl.selectWarehouseList`、`WarehouseSelect`、采购收货弹窗和移动端实体服务，确认无 `purpose` 时返回全量仓库，已补强 P2-01。
- 2026-06-23 使用浏览器在 `仓库：仓库` 上下文细化补测 `/cangku/product` 的搜索、分类筛选、重置、新增抽屉、导入弹窗、详情抽屉、编辑抽屉、删除确认、导出确认、模板确认；使用 MySQL 比对 `inv_product` 完整性、图片字段、重复商品和业务引用；检查商品前端组件、后端导入和删除保护逻辑。
- 2026-06-23 使用浏览器在 `仓库：仓库` 上下文细化补测 `/cangku/purchase` 的列表空态、新建采购单弹窗、选择采购商品弹窗、商品搜索/重置，以及刷新后的路由渲染状态；使用 MySQL 比对 `inv_purchase_order`、`inv_purchase_detail`、`inv_purchase_return`、`inv_purchase_return_detail` 数据量；检查采购前端组件、采购动作规则、后端保存/提交/收货/质检/取消/删除流程和采购订单 mapper 聚合字段。
- 2026-06-23 使用浏览器在 `仓库：仓库` 上下文细化补测 `/cangku/purchaseReturn` 的直连渲染和首页仓库菜单入口状态；由于直连正文为空、菜单链接宽高为 0，本轮未能完成新增退货单弹窗按钮实测。使用 MySQL 比对采购退货菜单权限和 `inv_purchase_return`、`inv_purchase_return_detail` 数据量；检查采购退货前端组件、API、后端保存/提交/确认/取消/导出流程、原采购单候选逻辑和 mapper 字段写入逻辑。
- 2026-06-23 继续细化采购退货服务层，检查 `InvPurchaseReturnServiceImpl.saveDraft/submitReturn/confirmReturn/normalizeReturnDetails/validateReturnQuantity`、采购明细 mapper 和服务测试；确认采购退货当前只按 `purchaseOrderId` 读取原采购明细，没有读取原采购单头校验原采购单仓库归属。
- 2026-06-23 使用浏览器在 `仓库：仓库` 上下文细化补测 `/cangku/stock` 的首页快捷入口、URL/面包屑、刷新状态；使用 MySQL 比对 `inv_stock`、`inv_stock_log` 和库存菜单权限；检查库存前端组件、库存调整弹窗逻辑、库存日志页、后端库存范围校验和库存/日志 mapper 范围条件。
- 2026-06-23 使用浏览器在仓库/门店上下文细化补测 `/inventory/transfer`、`/cangku/transfer` 的入口渲染、仓库部分发货行和门店上下文路由状态；使用 MySQL 比对 `inv_transfer_*`、`inv_transfer_approval_*`、`inv_transfer_status_log`；检查调拨前端 `canShip`、`canReceive`、移动端动作规则和后端 `receiveShipment` 批次收货逻辑。
- 2026-06-23 使用浏览器细化补测门店销售链路 `/inventory/sales`、`/inventory/customer`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report` 的直连渲染、空白壳状态、仓库上下文客户页按钮和新增客户弹窗；临时写入浏览器会话门店上下文 `108 / 市场部门 / STORE` 后复测 `/index` 和客户页恢复状态；截图捕获因 `Page.captureScreenshot` 超时未成功。使用 MySQL 比对 `inv_customer`、`inv_sales_order`、`inv_sales_return`、`inv_delivery_notice`、销售/客户/报表菜单权限、角色权限和仓库/门店组织类型；检查销售、客户、销售退货、发货通知、报表前端组件和后端服务流程。
- 2026-06-23 使用 MySQL 查看 `inv_customer` 表结构和客户表当前行数；检查客户页面、`InvCustomerServiceImpl` 和 `InvCustomerMapper.xml`，确认客户编码当前只有普通索引、前端允许留空且后端没有同门店重复编码校验。
- 2026-06-24 继续细化客户信用字段，使用 MySQL 复核当前 `inv_customer=0`、`credit_limit/credit_used` 汇总为 0、运行态无 `%credit%` 权限节点；检查客户页面、客户 API、`InvCustomerController`、`InvCustomerServiceImpl`、`InvCustomerMapper.xml`、销售服务和销售 mapper，确认 `credit_used` 当前没有业务单据维护逻辑但客户档案接口可写入。
- 2026-06-23 使用 MySQL 细化 `inv_sales_order`、`inv_sales_detail`、`inv_sales_return`、`inv_sales_return_detail` 及对应 `*_clear_backup_20260608_1530` 表，确认正式表和备份表当前均为空；检查销售退货前端、销售退货 API、`InvSalesReturnController`、`InvSalesReturnServiceImpl`、销售/退货 mapper、`InvSalesReturnServiceImplTest` 和销售退货菜单权限，确认销售退货后端缺少原销售单门店一致性校验，且 `inv:salesReturn:edit` 未被前后端实际使用。
- 2026-06-23 使用 MySQL 细化销售管理 `sys_menu` 和 `sys_role_menu`；检查销售管理前端、销售 API、`InvSalesController`、`InvSalesServiceImpl`、`InvSalesOrder` 和销售 mapper，确认当前销售单草稿编辑和保存都使用 `inv:sales:add`，`inv:sales:edit` 未被前后端实际使用。
- 2026-06-23 使用 MySQL 细化采购管理 `sys_menu` 和 `sys_role_menu`；检查采购管理前端、采购 API、`InvPurchaseController` 和采购服务流程，确认当前采购单草稿编辑和保存都使用 `inv:purchase:add`，`inv:purchase:edit` 未被前后端实际使用。
- 2026-06-23 使用 MySQL 细化调拨新增/修改角色授权；检查调拨管理前端、调拨 API、`InvTransferController` 和 `InvTransferServiceImpl.saveDraft`，确认当前前端新增/编辑按钮分开授权，但后端 `/transfer/save` 使用 `inv:transfer:add OR inv:transfer:edit` 放行。
- 2026-06-23 使用 MySQL 细化库存管理 `inv:stock:list/query/adjust/log/export` 菜单和角色授权；检查库存 API、`InvStockController` 和库存页 `openStockDetail`，确认当前电脑端库存详情没有调用 `/inventory/stock/{stockId}`，`inv:stock:query` 未被页面详情实际使用。
- 2026-06-23 使用 MySQL 细化 `sys_dept` 门店与仓库层级关系，确认唯一仓库 `104` 不在任何门店子树内；检查发货通知页面、`WarehouseSelect`、`SysDeptServiceImpl.selectWarehouseList`、`InvDeliveryNoticeController`、`InvDeliveryNoticeServiceImpl` 和 `InvDeliveryNoticeServiceImplTest`，确认发货通知仓库候选和后端校验当前都按销售门店子树过滤。
- 2026-06-23 使用 MySQL 细化 `inv_transfer_approval_rule`、`inv_transfer_approval_node`、`inv_transfer_approval_instance`、`inv_transfer_approval_task`、`sys_user_shop`、`sys_user_post`、`sys_post`；检查调拨审批配置页、审批规则 controller/service/mapper 和调拨提交审批服务，确认当前规则节点能解析到 `ll1`、`ii`，但规则保存校验、预览 payload、范围类型和节点角色语义仍存在配置风险。
- 2026-06-24 继续细化岗位主数据权限，使用 MySQL 复核 `sys_post=10`、`sys_user_post=18`、调拨审批节点 `zdjl/yyjl`、薪资档位岗位名称和 `common/zjl/ry` 的 `system:post:*` 权限；检查 `SysPostController`、`SysPostServiceImpl`、`SysPostMapper.xml` 和 `InvTransferApprovalCandidateMapper.xml`，确认岗位修改不做审批/薪资依赖预检，岗位候选人运行时按当前 `sys_post.post_code` 匹配。
- 2026-06-24 继续细化调拨审批配置列表分页，使用 MySQL 复核当前规则和角色授权；检查 `InvTransferApprovalRuleController.list`、`InvTransferApprovalRuleServiceImpl.selectRuleList/isRuleScopeVisible` 和 `InvTransferApprovalRuleMapper.selectRuleList`，确认当前组织范围过滤发生在分页查询之后，已新增 P3-87。
- 2026-06-24 继续细化店铺/仓库授权写权限，使用 MySQL 复核 `sys_user_shop=43`、`admin/ry` 的 7 个经营组织授权、`zjl` 的 `data_scope=1` 和 `system:userShop:list/query/edit` 授权；检查 `system/shop/index.vue`、`api/system/userShop.js`、`SysUserShopController`、`SysUserShopServiceImpl` 和 `SysUserShopMapper.xml`，确认非管理员保存只限制在操作人已有组织内，但当前总经理账号已拥有全量经营组织且保存接口没有单独保护超级管理员目标账号，已新增 P2-103。
- 2026-06-24 继续只读复核 `sys_user_shop` 默认组织字段，使用 MySQL 统计 `is_default` 分布和多组织账号默认值；检查店铺授权页、组织选择页、`userShop` API、`SysUserShopController`、`SysUserShopServiceImpl`、`SysUserShopMapper.xml` 和部门 `shop-tree` 查询，确认默认组织字段只在保存时按数组首项写入，前端不能设置且选择组织页不读取，已新增 P3-108。
- 2026-06-23 使用浏览器/CDP 细化补测 `/system/shop`、`/select-shop` 的直连渲染、空白壳和首次空树状态；使用同一 admin token 调用 `/system/user/list`、`/system/user/shop/tree`、`/system/user/shop/batch`、`/system/user/shop/121`、`/system/dept/shop-tree`；使用 MySQL 比对 `sys_user_shop`、`sys_dept`、`sys_menu`、`sys_role_menu`；检查用户组织授权前端、组织选择页、`SysUserShopController`、`SysUserShopServiceImpl`、`SysUserShopMapper.xml` 和部门 `shop-tree` 逻辑。
- 2026-06-23 使用浏览器/CDP 细化补测 `/system/notice`、`/index` 顶部公告入口、`/monitor/job`、`/system/log/operlog`；使用同一 admin token 调用 `/system/notice/list`、`/system/notice/listTop`、`/system/notice/readUsers/list`、`/schedule/job/list`、`/schedule/job/1`、`/system/operlog/list`；使用 MySQL 比对 `sys_notice`、`sys_notice_read`、`sys_job`、`sys_job_log`、`sys_oper_log`、`sys_menu`；检查通知公告管理页、阅读用户弹窗、HeaderNotice、任务详情弹窗、操作日志详情弹窗和公告已读 mapper。
- 2026-06-23 继续细化通知公告发布范围，检查 `sys_notice` 表结构、公告类型/状态字典、公告新增弹窗、HeaderNotice、`SysNoticeController.listTop/markReadAll` 和 `SysNoticeReadMapper.selectNoticeListWithReadStatus/selectUnreadCount`，确认当前正常公告按全局公告处理，没有门店、角色或用户收件范围。
- 2026-06-23 继续细化通知公告富文本安全链路，检查公告编辑器、`SysNotice` 字段校验、`SysNoticeMapper` 保存逻辑、HeaderNotice 详情页 `v-html` 渲染和 `system:notice:*` 当前角色授权，确认公告标题有 XSS 校验但公告内容没有后端净化，详情页直接渲染保存的 HTML。
- 2026-06-24 继续复核通知公告富文本/XSS 边界：检查 `Editor`、`NoticeDetailView`、`SysNoticeController`、`SysNoticeServiceImpl`、`SysNoticeMapper.xml`、网关 `bootstrap.yml` 和 `XssFilter`，并用 MySQL 复核当前 `sys_notice=0`、`notice_content=longblob`、`common/zjl` 仍持有 `system:notice:add/edit/list/query/remove`；确认 `/system/notice` 被网关 XSS 排除，公告内容保存和展示链路仍缺少统一 HTML sanitizer。
- 2026-06-24 继续细化通知公告运行态口径和删除事务边界：使用 MySQL 复核当前运行态只有 `sys_notice/sys_notice_read`、没有 `sys_notification`，并检查 `SysNoticeController.remove`、`SysNoticeReadServiceImpl.deleteByNoticeIds`、`SysNoticeServiceImpl.deleteNoticeByIds`、`SysNoticeReadMapper.xml` 和 `SysNoticeMapper.xml`，确认公告已读清理和公告删除不在同一事务内，已修正 P3-62 并新增 P3-100。
- 2026-06-24 继续只读复核通知公告管理、顶部公告和阅读用户副页面；使用 MySQL 比对 `sys_notice/sys_notice_read` 行数、`sys_notice_read` 索引、`system:notice:*` 菜单授权和用户角色绑定；检查 `notice/index.vue`、`ReadUsers.vue`、`HeaderNotice/index.vue`、`SysNoticeController`、`SysNoticeMapper.xml`、`SysNoticeReadMapper.xml`，确认状态筛选缺失、顶部“全部已读”只提交当前 5 条 ID、阅读用户明细复用列表权限并返回手机号。
- 2026-06-23 使用浏览器/CDP 细化补测 `/system/user-auth/role/122`、`/system/role-auth/user/103` 和“选择用户”副弹窗；使用同一 admin token 调用 `/system/user/authRole/122`、`/system/role/authUser/allocatedList`、`/system/role/authUser/unallocatedList`、`/system/role/103`；使用 MySQL 比对 `sys_user_role`、`sys_user`、`sys_role`、`sys_dept`、`sys_menu`；检查用户授权角色页、角色授权用户页、候选用户弹窗、`SysUserController`、`SysRoleController`、`SysUserServiceImpl`、`SysRoleServiceImpl` 和用户 mapper。
- 2026-06-23 使用 MySQL 细化 `sys_role_menu` 与 `sys_menu.visible/status` 的交叉状态，统计停用/隐藏菜单在各角色和有效用户中的覆盖；检查 `SysMenuMapper.xml`、`SysMenuServiceImpl`、`SysMenuController.roleMenuTreeselect` 和角色编辑页 `system/role/index.vue`，确认运行时权限会过滤停用菜单但角色编辑 checkedKeys 不过滤停用菜单。
- 2026-06-23 使用 MySQL 细化角色管理菜单权限、`sys_role.data_scope`、`sys_role_dept`；检查角色管理页数据权限弹窗和 `SysRoleController.dataScope`，确认当前角色数据权限修改前后端都复用 `system:role:edit`，没有独立 `system:role:dataScope` 权限点。
- 2026-06-23 使用 MySQL 细化持有 `system:role:edit` 的角色和有效用户、`sys_menu` 总数、角色菜单数量；检查角色编辑菜单树、`SysMenuController.roleMenuTreeselect`、`SysRoleController.edit` 和 `SysRoleServiceImpl.updateRole/insertRoleMenu`，确认角色菜单授权保存未校验请求 `menuIds` 是否属于当前用户可授权范围。
- 2026-06-24 继续细化角色管理全量授权，使用 MySQL 复核 `common/zjl/ry` 的 `system:role:*` 有效权限、当前 11 个启用角色、角色菜单数和角色用户数；检查角色管理页按钮、角色新增/修改菜单树、数据权限弹窗、分配用户入口、`SysRoleController` 权限注解和 `SysRoleServiceImpl.insertRole/updateRole/authDataScope/deleteRoleByIds`，确认普通角色和总经理角色拥有真实的角色新增、修改、删除、导出、数据范围和用户分配治理入口，已新增 P2-101。
- 2026-06-23 使用 MySQL 细化 `system:menu:*` 授权角色和有效用户、当前 `sys_menu` 页面/按钮节点数量；检查菜单管理页 `system/menu/index.vue`、菜单 API、`SysMenuController` 和 `SysMenuServiceImpl`，确认总经理角色实际持有全局菜单新增、修改、排序和删除能力。
- 2026-06-24 继续细化菜单管理查询字段，检查 `system/menu/index.vue` 查询表单、`queryParams`、`resetQuery/getList`、菜单 API 和 `SysMenuMapper.selectMenuList`，确认页面状态筛选绑定 `status`，但初始化参数保留 `visible`，已新增 P3-88。
- 2026-06-23 使用 MySQL 细化工作流菜单、`wf_*` 表、`ACT_*` 表、`sys_salary_template`、薪资模板权限、考勤 Excel 导入菜单和 `oa_attendance_record`；使用 `find`/`rg` 对比 `erp-ui/src/views`、`erp-ui/src/api`、`erp-modules/erp-workflow`、`erp-api/erp-api-workflow`、系统薪资 Controller/API 和考勤导入相关关键字，确认工作流 11 个页面、薪资模板页面和考勤 Excel 导入页面均缺少当前工程实现。
- 2026-06-23 曾使用 MySQL 细化源码仍在使用但当时 `sys_menu` 缺失的权限点，确认 `inv:report:list`、`inv:audit:list`、`oa:salary:*`、`inv:transfer:rule:*`、`inv:transfer:records*`、`inv:transfer:approve` 在当时权限树和角色授权中均为 0；2026-06-24 复核当前运行态时这些权限已重新出现在 `sys_menu`，已将 P2-75 从“当前缺失”修正为权限树回填/漂移问题。
- 2026-06-23 使用 MySQL 细化 `file:%` 权限、劳动合同模板维护权限和角色授权；检查劳动合同模板/企业章弹窗、通用 FileUpload/ImageUpload 组件、文件删除 API 和 `SysFileController`，确认文件上传/删除后端权限没有进入当前权限树。
- 2026-06-23 曾使用源码权限抽取结果与当时 `sys_menu.perms` 做差集，确认成本相关权限与源码 `inv:cost:view` 不一致；2026-06-24 复核当前运行态时成本权限已回填为 `4510 / 成本查看 / inv:cost:view`，且仅授予超级管理员和仓库管理员，已将 P2-77 修正为全局成本权限挂载位置和授权语义不清。
- 2026-06-23 使用 MySQL 细化采购申请菜单和 `oa:purchase*`/`inv:purchase-request*` 权限；检查 OA 采购申请前端和 `OaPurchaseController`，当时确认菜单、前端按钮和后端列表权限码不一致。2026-06-24 复核当前运行态时组件路径和 `oa:purchase:list` 已回填，剩余问题改为采购申请停用而审批工作台已启用。
- 2026-06-23 使用 MySQL 细化采购计划、本店库存、批次管理、钉钉同步/打卡明细/打卡汇总等停用菜单状态和角色授权；使用文件存在性检查与 `rg` 确认这些菜单对应前端组件和权限源码缺失。
- 2026-06-23 使用 MySQL 细化启用但源码未使用的进销存按钮权限及角色授权；使用 `rg` 检查商品价格编辑、客户信用额度、采购审批、质检记录、销售审批和销售发货通知权限，确认这些权限没有接入当时前端按钮或后端注解。2026-06-24 复核当前运行态时，上述历史权限码已从 `sys_menu` 中消失，报告按权限树漂移修正。
- 2026-06-23 曾再次使用 MySQL 复核非运行态 `BossERP` 快照，确认 `sys_user=7`、`sys_user_shop=0`、所有启用账号 `shop_scopes=0`，并比对 `sys_role_warehouse` 与该库账号角色绑定；后续运行态库已确认 `sys_user=25`、`sys_user_shop=43`，非运行态空授权不能作为当前网页端结论。
- 2026-06-23 曾使用 MySQL 复核非运行态 `BossERP` 正式进销存数据，确认 `inv_sales_order=9`、`inv_sales_detail=14`，其中存在已出库数量和重复销售单号；同时确认 `inv_stock=0`、`inv_stock_log=0`、`inv_delivery_notice=0`、`inv_delivery_notice_detail=0`，历史库存/出库/发货通知只在 `*_clear_backup_20260607_1630` 表中保留。后续已确认当前网页端运行态不是该 schema。
- 2026-06-25 重新使用 MySQL 复核当前考勤链路表，确认运行态只保留 `oa_attendance_record=1`，没有 `oa_shift`、钉钉原始表、Excel 导入表或用户映射表；早前 `oa_shift=3` 和钉钉/Excel 原始数据属于旧/非运行态口径，考勤转换闭环按 P2-80/P2-93 继续跟踪。
- 2026-06-23 使用 MySQL 复核当前 `oa_purchase`、`oa_purchase_comment`、`ACT_RU_TASK`、`ACT_HI_PROCINST`、`ACT_RE_DEPLOYMENT` 和 `ACT_RE_PROCDEF`，确认当前运行态 `oa_purchase=0`、`oa_purchase_comment=0`、运行/历史流程为 0，仅保留 1 条部署和 1 条流程定义；旧口径中的采购申请业务数据和待办任务不作为当前网页端运行态结论。
- 2026-06-23 使用 MySQL 复核当前启用用户的角色、岗位、部门、显式组织授权和启用菜单权限数量；检查 `SysLoginService.login`，确认登录阶段不校验角色、组织授权或岗位一致性。
- 2026-06-23 使用 MySQL 复核当前薪资相关表结构和表数量；检查 `SysSalaryConfigController`、`SysSalarySchemeMapper.xml`、`SysUserSalarySchemeMapper.xml`、`OaSalaryEmployeeMapper.xml`、`OaSalaryConfigMapper.xml`、`OaSalaryRecordMapper.xml`，确认当前运行态 `sys_user_salary_scheme=1`、`oa_salary_config=1`、`oa_salary_record=0`，表存在但员工绑定和工资记录覆盖不足。
- 2026-06-23 使用 MySQL 复核系统/集成类表行数和工作流实例数据，确认当前运行态没有 `wf_%` 业务表，Activiti 仅有 `ACT_RE_DEPLOYMENT=1`、`ACT_RE_PROCDEF=1`、`ACT_GE_BYTEARRAY=1`、`ACT_GE_PROPERTY=6`、`ACT_ID_PROPERTY=1` 等少量引擎元数据，`ACT_RU_TASK=0`、`ACT_HI_PROCINST=0`、`ACT_HI_TASKINST=0`；旧口径的工作流业务实例和待办数据已改列为环境漂移风险。
- 2026-06-23 使用提升权限后的 MySQL 单连接动态 SQL 对当前运行态 `BossERP_stock_state_75c59ee` 的 160 张表逐表执行精确 `count(*)`，其中 67 张表非空；将非空表名与本报告做自动差集比对后得到 `MISSING_COUNT=0`，最终“运行态非空表均已在报告出现”的覆盖复核已通过。
- 2026-06-23 普通沙箱内 MySQL 仍会返回 `ERROR 2003`，提升权限后 `select 1` 与自动差集脚本均可正常执行；后续若继续做数据库页级复测，应优先使用已批准的 `mysql --protocol=TCP` 本机连接方式，避免把沙箱网络限制误判为数据库不可用。
- 2026-06-23 使用 `rg --files` 和静态 import 检查 `erp-ui/src/views` 下 93 个 `.vue` 文件；剔除移动端、图表和明显内部组件后，对 66 个桌面可路由/副页面文件与本报告路径做差集，并进一步用 `rg` 追踪 import/路由，确认 `index_v1.vue`、`system/dict/detail.vue`、`oa/laborContract/index 2.vue` 属于未路由或重复副本；继续检查 `erp-ui/src/api` 下 42 个 API 文件，确认 `oa/laborContract 2.js` 是未 import 的重复 API 副本，已并入 P3-83。
- 2026-06-23 继续按 `erp-ui/src/router/index.js` 复核桌面常量路由和隐藏动态路由：`/login`、`/register`、`/select-shop`、`/401`、`/404`、`/index`、`/lock`、`/user/profile`、`/system/user-auth/role/:userId`、`/system/role-auth/user/:roleId`、`/system/dict-data/index/:dictId`、`/monitor/job-log/index/:jobId`、`/inventory/stock-log`、`/inventory/report`、`/tool/gen-edit/index/:tableId` 均已在报告中有页面验证、源码问题或作为公共错误/跳转页归类；未发现新的桌面隐藏路由漏项。
- 2026-06-23 使用 `rg --files -g '*Controller.java'` 复核后端 Controller 覆盖：当前仓库共有 46 个 `*Controller.java`，除 `BaseController`、`OaBaseController` 这类基础父类外，认证、系统、文件、代码生成、库存、调度、OA、固定资产等业务 Controller 均已在本报告对应章节或问题中出现；方法级映射粗筛未发现新的电脑端业务 Controller 漏项。未带权限注解的映射只出现在 `TokenController` 的 `login/register/passwordPolicy`，已分别被登录、注册开关和密码策略问题覆盖。
- 2026-06-23 继续比对前端 API 与后端 Controller URL：按 `erp-gateway/src/main/resources/bootstrap.yml` 中 `/auth`、`/system`、`/code`、`/schedule`、`/oa`、`/file`、`/inventory` 的 `StripPrefix=1` 规则，将 `erp-ui/src/api` 中 290 个 `url:` 请求映射到对应服务内 Controller 路径；修正初版脚本误把网关前缀当作 Controller 前缀后，290 个前端请求 URL 均能匹配到后端 Controller 映射，未发现新的“前端 API 调用无后端接口”漏项。
- 2026-06-23 使用静态脚本抽取 `erp-modules` 等后端 mapper 目录下 150 个 MyBatis XML 文件中的 `from/join/update/insert into/delete from` 表名，得到 69 张 `sys_`、`gen_`、`oa_`、`inv_`、`ACT_` 等实际读写表；69 张均已在本报告出现，未发现“后端 Mapper 读写但报告完全未覆盖”的表名漏项。
- 2026-06-23 使用静态脚本抽取 `sql/` 目录 27 个迁移/备份 SQL 文件中的 `create table/alter table/insert into/update/from/join` 表名，得到 37 张相关表；37 张均已在本报告出现，未发现“SQL 脚本定义或迁移但报告完全未覆盖”的表名漏项。
- 2026-06-23 使用 Playwright 重新登录 admin，复测 `/index -> /select-shop -> /index -> /cangku/stock`：首页未选组织时能提示“未选择组织”，选择 `仓库` 后顶部、工作台和库存页均同步显示 `仓库：仓库`；仓库库存页显示 1 条库存、数量 192，库存调整弹窗可打开并提示“仓库调整将写入当前仓库库存”，未提交写入，浏览器控制台无 warning/error。
- 2026-06-23 使用静态脚本抽取桌面端 `.vue` 的 `v-hasPermi`、后端 Java 的 `@RequiresPermissions`，并与运行态 `sys_menu` 交叉比对；确认前端 160 个按钮权限均能匹配后端注解，同时发现 15 组启用权限码重复，已新增 P3-84。
- 2026-06-23 继续细化销售制单到发货通知链路，使用 MySQL 复核运行态启用商品 162 个、正库存商品 1 个、客户/销售单/发货通知均为 0；检查销售页、商品选择器、`InvSalesServiceImpl` 和 `InvDeliveryNoticeServiceImpl`，确认销售保存/提交和生成发货通知不做库存可售性校验，库存不足延后到发货执行阶段才失败，已新增 P2-95。
- 2026-06-23 继续细化库存盘点详情成本权限：使用 `/auth/login` 获取 token 后，以 `Dept-NumId: 108` 只读请求 `/inventory/stockCheck/2`，确认响应明细包含 `costPrice=84.0000`；使用 MySQL 复核店长、驻店经理、运营经理、总经理均持有 `inv:stockCheck:query` 且无 `inv:cost:view`，检查 `InvStockCheckDetailMapper.xml`、`InvStockCheckServiceImpl` 和盘点详情前端，已新增 P1-24。
- 2026-06-23 继续细化调拨详情成本权限：使用 `/auth/login` 获取 token 后，以 `Dept-NumId: 108` 只读请求 `/inventory/transfer/14`，确认响应发货批次明细包含 `costPrice=84.00`；使用 MySQL 复核 `inv_transfer_shipment_detail` 两条明细均有成本价，且店长、驻店经理、运营经理、总经理拥有调拨查询/记录查询权限但无 `inv:cost:view`，检查 `InvTransferShipmentDetailMapper.xml`、`InvTransferServiceImpl.getTransferDetail` 和调拨详情前端，已新增 P1-25。
- 2026-06-23 继续细化商品列表/详情成本权限：使用店长账号 `qa_audit_62201` 登录后，以 `Dept-NumId: 103` 只读请求 `/inventory/product/list?pageNum=1&pageSize=1&status=0` 和 `/inventory/product/10`，确认响应包含 `purchasePrice=26.50`、`costPrice=84.00`；检查 `InvProductController.list/getInfo/export` 和商品前端 `canViewCostFields`，确认只有前端展示和导出做了成本控制，列表/详情接口未脱敏，已新增 P1-26。
- 2026-06-23 继续细化供应商详情成本权限：使用 admin token 以 `Dept-NumId: 104` 只读请求 `/inventory/supplier/6` 和 `/inventory/supplier/6/products`，确认供货商品响应包含 `purchasePrice=26.50`、`costPrice=84.00`；使用 MySQL 复核总经理角色有 `inv:supplier:query` 但无 `inv:cost:view`，检查供应商详情前端、`InvSupplierController.products()`、`InvSupplierServiceImpl.selectSupplierProductList()` 和商品 mapper，已新增 P1-27。
- 2026-06-23 继续细化采购商品选择和采购单价成本权限：使用 MySQL 复核总经理角色同时持有 `inv:product:list/query`、`inv:purchase:add/list/query/submit/receive/qc/export` 且无 `inv:cost:view`，账号 `ry` 授权含 `104` 仓库；运行态 `inv_product` 仍有 162 个启用商品，样例商品返回 `purchase_price/cost_price`，但 `inv_purchase_order/inv_purchase_detail` 当前为 0。检查采购页商品选择、采购详情/收货弹窗、`InvProductController.list`、`InvPurchaseController`、`InvPurchaseServiceImpl` 和采购 mapper，确认采购参考价、进价、金额字段未接入成本权限，已新增 P1-28。
- 2026-06-23 继续细化库存列表/详情/汇总成本权限：使用店长账号 `qa144822m` 登录后，以 `Dept-NumId:108` 只读请求 `/inventory/stock/list?pageNum=1&pageSize=1`、`/inventory/stock/3` 和 `/inventory/stock/summary`，确认响应包含 `costPrice=84.00`、`totalCost=1008.00`；使用 MySQL 复核店长、驻店经理、运营经理、总经理拥有 `inv:stock:list/query/export` 且无 `inv:cost:view`，检查 `InvStockController`、`InvStockMapper.xml` 和库存前端详情展示，已新增 P1-29。
- 2026-06-24 继续细化商品成本写权限：使用 MySQL 复核总经理角色拥有 `inv:product:add/edit/import` 且无 `inv:cost:view`，有效账号 `ry` 绑定总经理并授权 7 个经营组织；检查商品前端 `canViewCostFields/stripHiddenCostFields`、`InvProductController.add/edit/importData`、`InvProductMapper.xml` 和 `InvProduct.java` Excel 注解，确认成本字段只在前端被删除，后端仍可被构造请求或导入写入，已新增 P1-30。
- 2026-06-24 继续细化库存调整成本写入：使用 MySQL 复核驻店经理、运营经理、总经理拥有 `inv:stock:adjust` 且无 `inv:cost:view`，当前库存样例 `cost_price=84.00`、门店总成本 `1008.00`、仓库总成本 `16128.00`；检查库存调整弹窗 payload/确认文案、`InvStockAdjustRequest`、`InvStockServiceImpl.adjustStock` 和 `InvStockMapper.addInvStockWithCost/deductInvStockWithCost`，确认无成本权限调整人可触发库存总成本变化，已新增 P1-31。
- 2026-06-24 继续细化销售退货成本回写：使用 MySQL 复核销售明细、销售退货明细和出库记录表均无出库成本快照字段，正式销售/退货/出库表当前为 0；检查 `InvSalesServiceImpl.deliverSales`、`InvDeliveryNoticeServiceImpl.deliverNotice`、`InvOutboundRecord/Mapper` 和 `InvSalesReturnServiceImpl.confirmReturn/resolveReturnStockCost`，确认销售退货按当前库存成本或零成本入库，已新增 P1-32。
- 2026-06-24 继续细化采购退货价格权限：使用 MySQL 复核总经理角色拥有 `inv:purchaseReturn:list/query/add/submit/confirm/export` 且无 `inv:cost:view`，当前正式采购/采购退货表为 0；检查采购退货前端列表/表单/详情、`InvPurchaseReturnController.export`、`InvPurchaseReturn.java` Excel 注解和 `InvPurchaseReturnServiceImpl.normalizeReturnDetails`，确认采购退货金额、单价和明细金额未接入成本权限，已新增 P1-33。
- 2026-06-23 继续细化采购待检入库记录台账：使用 MySQL 复核正式 `inv_inbound_record=0`、`inv_inbound_record_clear_backup_20260607_1640=3`，样例历史记录包含采购单号、商品、仓库、数量、`qc_result/qc_user/qc_time`；检查 `InvInboundRecord`、`InvInboundRecordMapper.xml`、`InvPurchaseServiceImpl.receivePurchase/qualityCheck` 和采购前端详情/收货/质检弹窗，确认待检入库记录只在服务内部读写，电脑端没有收货批次和质检轨迹展示，已新增 P3-85。
- 2026-06-23 继续细化发货通知详情、导出和销售出库记录：使用 MySQL 复核当前 `inv_delivery_notice=0`、`inv_outbound_record=0`、销售出库库存流水为 0；检查 `InvDeliveryNoticeServiceImpl.deliverNotice`、`InvOutboundRecord/Mapper`、`InvDeliveryNoticeController.export`、`InvDeliveryNotice/Detail` 和发货通知前端详情/导出，确认执行发货写出库记录但电脑端详情和导出没有出库记录或商品明细台账，已新增 P3-86。
- 2026-06-24 继续细化销售单发货进度展示：使用 MySQL 复核正式 `inv_sales_order/inv_sales_detail/inv_delivery_notice/inv_outbound_record` 当前均为 0；检查 `InvSalesOrderMapper.xml`、`InvSalesDetailMapper.xml`、`InvSalesOrder` Excel 注解、`InvSalesController.export` 和销售页 `sales/index.vue` 列表/详情，确认后端已返回总数量、已发货、待发货和明细已发数量，但电脑端销售页没有展示这些履约进度字段，已新增 P3-99。
- 2026-06-23 使用脚本仅从 `v-hasPermi`、`hasPermi(...)`、`@RequiresPermissions(...)` 抽取真实权限码，排除 CSS 伪类和普通字符串后得到 207 个源码权限码；逐字比对本报告发现 138 个标准按钮权限未以原始字符串出现，但对应页面/按钮组已在页面章节或问题中覆盖，需在后续附录化权限矩阵时再逐码展开。
- 2026-06-23 使用 MySQL 复核当前 8 条 `sys_config`；使用 `rg` 和源码检查 `JwtUtils`、`SysPasswordService`、`FileUploadUtils`、`MimeTypeUtils`、参数设置页和配置服务，确认多条安全/文件/审计/PDA 参数没有按 `sys_config` 进入运行链路。
- 2026-06-23 使用 MySQL 复核当前 `sys_job`、`sys_job_log` 和 `QRTZ_*`，确认运行态 `sys_job=3` 且均停用、`sys_job_log=0`、`QRTZ_JOB_DETAILS/QRTZ_TRIGGERS=0`；使用 `rg`、`find` 和源码检查 `JobInvokeUtil`、`ScheduleUtils`、`SysJobServiceImpl`、`AbstractQuartzJob`，确认旧/非运行态曾出现的 `oaAttendanceSyncTask.syncYesterdayAttendance()` 在当前工程没有可调用目标，若重新插入或启用会失败。
- 2026-06-23 曾再次使用 MySQL 精确复核非运行态 `BossERP` 关键表，确认 `sys_user` 表共 8 条但有效未删除账号 7 条，`sys_role=15`、`sys_dept=12`，并确认该非运行态库劳动合同正式合同/事件/企业章已清空而 2 条内置合同模板仍保留。后续已确认当前网页端运行态不是该 schema。
- 2026-06-23 曾使用 MySQL 复核非运行态 `BossERP` 的 `inv_product`、`inv_product_category`、`inv_supplier` 表结构和样例数据；检查 `InvProductMapper.xml`、`InvProduct.java` 和商品前端组件，确认该非运行态库缺少源码仍在读写的商品宽表字段，并用只读 SQL 验证 `p.sku` 等字段会触发缺列错误。后续运行态库已确认商品宽表字段存在。
- 2026-06-23 曾使用 MySQL 复核非运行态 `BossERP` 的 `inv_sales_order`、`inv_sales_detail`、`inv_purchase_order`、`inv_purchase_detail`、`inv_stock`、`inv_stock_log` 表结构；检查 `InvSalesOrderMapper.xml`、`InvStockMapper.xml`、`InvStockLogMapper.xml`，并用只读 SQL 验证 `o.customer_name`、`s.version`、`l.batch_no` 会触发缺列错误。后续运行态库已确认销售/库存主列存在。
- 2026-06-23 使用 MySQL 复核 `sys_code_rule` 与 `inv_number_sequence`；使用 `rg` 检查编号规则引用，并检查采购、销售、调拨、盘点、发货通知服务的单号生成代码，确认 `sys_code_rule` 未进入运行链路且销售单前缀已从历史 `SO` 切到当前 `OB`。
- 2026-06-23 使用 MySQL 精确复核 `inv_warehouse` 与 `sys_dept` 仓库节点；使用 `rg` 和源码检查仓库选择、库存、库存流水、发货通知、盘点、报表 mapper，确认当前运行链路以 `sys_dept` 仓库节点为准，没有读取 `inv_warehouse`。
- 2026-06-23 使用 MySQL 复核当前有效用户联系方式空值和 `sys_user_shop` 授权状态；检查锁屏页 `/lock`、本地锁屏状态、路由守卫、解锁 API、`TokenController.unlockScreen`、`SysLoginService.unlock` 和 `SysPasswordService.validate`，确认锁屏解锁失败未进入登录失败计数或登录日志闭环。
- 2026-06-24 再次使用 MySQL 复核 `system:dict:*`、`system:config:*` 当前角色授权，确认 `105 / 字典管理`、`106 / 参数设置` 仍为隐藏菜单但页面和按钮权限均授给 `common,zjl`，实际用户 `ry` 通过总经理角色持有字典和参数新增、修改、删除、导出及刷新缓存权限；检查字典管理、参数设置页面、前端 API、`SysDictTypeController.refreshCache` 和 `SysConfigController.refreshCache`，确认“刷新缓存”当前复用删除权限，已修正 P2-57 的旧授权口径。
- 2026-06-24 继续细化字典管理和字典数据副页面，使用 MySQL 复核 `sys_menu` 字典权限、`sys_dict_type`、`sys_dict_data` 索引和重复键值；检查 `system/dict/index.vue`、`system/dict/data.vue`、`system/dict/detail.vue`、动态路由、字典 API、`SysDictTypeController`、`SysDictDataController`、`SysDictTypeServiceImpl`、`SysDictDataServiceImpl` 和 `SysDictDataMapper.xml`，确认字典类型/字典项权限未拆分、字典项无同类型键值唯一校验、内置字典类型缺少改名/停用保护，已新增 P3-89、P3-90、P3-91。
- 2026-06-23 使用 MySQL 复核 `sys_user.avatar`、`sys_file_meta` 和 `file:%` 权限节点；检查个人中心头像组件、头像上传 API、请求拦截器、`SysProfileController.avatar`、文件服务上传/删除实现，确认头像上传前端 FormData 与请求头声明不一致。
- 2026-06-23 使用 Redis 复核当前 `login_tokens:*` 数量；检查个人中心改密页、`SysProfileController.updatePwd`、管理员 `SysUserController.resetPwd`、`TokenService` 和在线用户强退接口，确认改密/重置密码只更新密码或当前 token，没有按用户清理其他在线会话。
- 2026-06-23 使用 MySQL 复核当前启用页面菜单 `component`，并检查菜单管理表单、`SysMenuController.add/edit`、`SysMenuServiceImpl.insertMenu/updateMenu/buildMenus`，确认新增/修改菜单只做名称、外链、路由唯一性等通用校验，不校验组件文件或权限实现。
- 2026-06-23 使用 MySQL 细化 `sys_user`、`sys_user_role`、`sys_user_shop` 和当前工作区 `SysUserMapper.xml` 的配置状态计算逻辑；使用 admin token 只读请求 `/system/user/list?pageNum=1&pageSize=5&setupStatus=missingShopScope`，确认运行态仍未返回 `setupStatus/roleCount/shopScopeCount`；后续运行态库复核确认 25 个有效账号均至少有 1 个角色，其中 `user_id=103 / 123` 缺少显式店铺/仓库授权。
- 2026-06-23 使用 `lsof`、`curl`、`rg` 和 `unzip -p` 对比运行态 8080、前端 1026、`target/classes` 与 `target/erp-modules-system.jar`，确认当前系统模块运行 jar 未包含用户配置状态 mapper，而工作区源码和 `target/classes` 已包含相关 SQL。
- 2026-06-23 重新启动前端 dev server 到 1026 后，使用浏览器/CDP 复测 `/system/user`、`/system/shop`、`/select-shop`：确认用户管理页仍显示“配置状态待同步”，店铺授权页和组织选择页当前可渲染；同时比对 `sys_user_shop` 和 `system/shop/index.vue` 组织范围格式化逻辑，确认多组织授权会被折叠成父级名称。
- 2026-06-23 使用 MySQL 细化库存盘点 `sys_menu`、`sys_role_menu`、`inv_stock_check`、`inv_stock_check_detail`、`inv_document_status_log`；检查库存盘点前端按钮和弹窗、`InvStockCheckController`、`InvStockCheckServiceImpl`、`InvBaseService`、`InvStateGuard`、库存盘点 mapper 和现有单元测试，确认取消/删除草稿接口校验与其他盘点写操作不一致，且 `inv:stockCheck:edit` 未被前后端使用。
- 2026-06-23 使用浏览器/CDP 长等待复测 `/system/role`、`/system/menu`、`/system/dept`、`/system/post`、`/system/dict`、`/system/dict-data/index/1`、`/system/config` 的直连渲染、表头、首屏行、按钮和 console；使用 MySQL 比对 `sys_role`、`sys_menu`、`sys_dept`、`sys_post`、`sys_user_post`、`sys_dict_type`、`sys_dict_data`、`sys_config`、`sys_role_menu`；检查系统基础页前端组件、部门类型选项、用户岗位字段、字典/岗位/部门/菜单删除保护和部门保存逻辑。
- 2026-06-24 继续细化部门组织树全量授权，使用 MySQL 复核 `common/zjl/ry` 的 `system:dept:*` 有效权限、当前 11 个启用组织节点和部门菜单权限；检查部门管理页新增/修改/保存排序/删除按钮、组织类型表单、`SysDeptController` 权限注解和 `SysDeptServiceImpl.insertDept/updateDept/updateDeptSort/deleteDeptById`，确认普通角色和总经理角色拥有真实的门店/仓库组织树新增、修改、排序和删除治理入口，已新增 P2-102。
- 2026-06-23 继续用 MySQL 细化 `sys_post`、`sys_user_post`、`sys_dict_type`、`sys_dict_data`、`sys_config`、字典/参数菜单、总经理角色授权和 `ry` 用户绑定；检查参数设置页、字典页、登录页、注册页、菜单路由生成、`SysConfigController`、`SysConfigServiceImpl`、`SysUserController`、`SysLoginService`、`UserConstants` 和密码规则前端工具，确认系统参数、注册开关和隐藏菜单授权的前后端对应关系。
- 2026-06-23 使用浏览器/CDP 复测 `/monitor/online`、`/system/log/logininfor`、`/monitor/job`、`/monitor/job-log/index/0`、`/system/log/operlog` 的直连、刷新、表头、按钮和空态；使用 MySQL 比对 `sys_logininfor`、`sys_oper_log`、`sys_job`、`sys_job_log`；使用 `redis-cli` 比对 `login_tokens:*`、`pwd_err_cnt:*`；检查在线用户、登录日志、定时任务、调度日志、操作日志前端组件和 `SysUserOnlineController`、`SysLogininforController`、`SysJobController`、`SysJobServiceImpl`、`SysJobLogController`。
- 2026-06-24 继续细化定时任务与调度日志关联，使用 MySQL 复核 `sys_job`、`sys_job_log` 字段、索引、当前任务和日志数量；检查 `monitor/job/index.vue`、`monitor/job/log.vue`、调度 API、`SysJobController`、`SysJobLogController`、`SysJobServiceImpl`、`AbstractQuartzJob`、`SysJobLog` 和 `SysJobLogMapper.xml`，确认日志不保存 `job_id`，任务行日志副页面实际按当前任务名和任务组追溯，已新增 P3-92。
- 2026-06-24 继续细化系统监控权限和在线用户分页；使用 MySQL 复核 `monitor:%` 菜单、`sys_role_menu`、`sys_user_role` 和当前 `sys_job`，使用 Redis 复核 `login_tokens:*` 数量；检查在线用户前端/API、`SysUserOnlineController`、`SysUserOnlineServiceImpl`、`BaseController.getDataTable`、定时任务前端/API 和 `SysJobController`，确认 `common/zjl` 持有监控破坏性权限且在线用户接口全量扫描 Redis 后由前端分页。
- 2026-06-24 继续细化定时任务“执行一次”权限边界：使用 MySQL 复核 `monitor:job:%` 权限树、`common/zjl` 授权和当前 3 条停用任务；检查定时任务前端、调度 API、`SysJobController.changeStatus/run` 和 `SysJobServiceImpl.run`，确认“执行一次”没有独立权限点而是复用 `monitor:job:changeStatus`，已新增 P3-101。
- 2026-06-24 继续只读复核调度日志权限粒度，使用 MySQL 检查 `sys_menu` 中无 `monitor:jobLog:*` 或调度日志独立权限节点，并核对 `monitor:job:query/remove/export` 授权给 `common,zjl`；检查调度日志前端、`jobLog` API 和 `SysJobLogController`，确认日志列表/详情/删除/清空/导出复用定时任务权限，已新增 P2-108。
- 2026-06-23 继续使用 MySQL 细化 `sys_oper_log` 请求参数内容和 `sys_role_menu` 日志权限授权；检查 `LogAspect`、`Log` 注解、`SysOperlogController`、`SysOperLogMapper.xml`、操作日志详情组件，确认明文密码字段已被排除，但手机号、邮箱、部门/角色对象和业务明细仍会进入日志详情与导出权限面。
- 2026-06-24 继续只读复核操作日志列表/详情接口分层，使用 MySQL 抽样 `sys_oper_log` 最新记录的 `oper_param/json_result` 长度；检查 `SysOperlogController`、`ISysOperLogService`、`SysOperLogServiceImpl`、`SysOperLogMapper.xml`、`erp-ui/src/api/system/operlog.js` 和操作日志列表/详情组件，确认列表接口批量返回详情参数，前端详情只展开列表行，已新增 P3-106。
- 2026-06-23 使用 MySQL 细化 `sys_config` 初始密码和密码策略参数、用户管理菜单权限、普通角色/总经理授权；检查用户管理页、导入弹窗、用户 API、`SysUserController`、`SysUserServiceImpl.importUser`、`SysUser` Excel 注解和密码策略测试，确认用户导入会使用不合规初始密码且 `system:user:resetPwd` 未被前后端实际使用。
- 2026-06-24 继续只读复核当前运行态系统参数，使用 MySQL 确认 `BossERP_stock_state_75c59ee.sys_config` 当前只有 8 条内置参数；检查参数设置页、`erp-ui/src/settings.js`、`store/modules/settings.js`、`api/system/config.js` 和 `sys.index.skinName/sys.index.sideTheme` 源码引用，确认早前 19 条安全/文件/审计参数不在当前运行态，已修正 P2-82；当前主框架主题参数仍未被桌面端读取，已新增 P3-107。
- 2026-06-24 继续细化用户管理全量授权，使用 MySQL 复核 `common/zjl/ry` 的 `system:user:*` 有效权限和当前 25 个启用账号；检查用户管理页按钮、用户新增/编辑角色岗位表单、`SysUserController` 权限注解、`SysUserServiceImpl.deleteUserByIds/importUser`，确认普通角色和总经理角色拥有真实的用户新增、修改、删除、导入、导出、重置密码和授权入口，已新增 P2-100。
- 2026-06-24 继续只读复核用户批量导入闭环，使用 MySQL 统计有效账号角色/组织/岗位配置缺口；检查 `SysUser` Excel 注解、用户导入弹窗、通用 Excel 导入组件、`SysUserController.importData` 和 `SysUserServiceImpl.importUser`，确认导入只写基础账号、不写角色/岗位/店铺授权，已新增 P2-112。
- 2026-06-24 继续只读复核用户详情和分配角色响应字段，使用 admin token 请求 `/system/user/1`、`/system/user/authRole/2` 确认响应 `data.password/user.password` 包含 BCrypt 哈希；同时请求 `/system/user/list?pageNum=1&pageSize=5` 确认列表仅有 `password:null`，未返回哈希值。检查用户详情抽屉、授权角色页、用户 API、`SysUserController.getInfo/authRole` 和 `SysUserMapper.selectUserVo/selectUserById`，确认详情页和分配角色副页面不展示但网络响应返回密码哈希，已补强 P2-113。
- 2026-06-24 继续只读复核个人中心响应字段，使用 admin token 请求 `/system/user/profile` 确认响应 `data.password` 包含 BCrypt 哈希；检查个人中心页面、个人资料 API、`SysProfileController.profile` 和 `SysUserMapper.selectUserVo/selectUserByUserName`，确认个人中心不展示但所有登录用户都可在网络响应看到自己的密码哈希，已新增 P2-114。
- 2026-06-24 继续只读复核桌面端启动用户信息接口，使用 admin token 请求 `/system/user/getInfo` 确认响应 `user.password` 包含 BCrypt 哈希；检查登录 API、`store/modules/user.js` 和 `SysUserController.getInfo`，确认前端只需要基础用户展示、权限和密码策略状态，但后端原样返回 `LoginUser.sysUser`，已新增 P2-115。
- 2026-06-24 继续只读复核角色授权用户副页面，使用 admin token 请求 `/system/role/authUser/allocatedList?roleId=1` 返回 admin 1 个已授权用户、`/system/role/authUser/unallocatedList?roleId=1` 返回 24 个候选用户；检查角色列表隐藏 roleId=1 操作、授权用户页、选择用户弹窗、`SysRoleController.authUser/*` 和 `SysRoleServiceImpl.checkRoleAllowed/checkAuthUserScope/insertAuthUsers/deleteAuthUsers`，确认超级管理员角色写保护没有覆盖授权成员路径，已新增 P2-116。本轮未调用任何授权写接口。
- 2026-06-24 继续只读复核用户分配角色副页面，使用 admin token 请求 `/system/user/authRole/1`、`/system/user/authRole/2`、`/system/user/authRole/122`，确认普通用户授权页也返回 `roleId=1 / 超级管理员` 可选项；检查 `authRole.vue`、`SysUserController.authRole/insertAuthRole`、`SysUserServiceImpl.checkUserAllowed/insertUserAuth` 和 `SysRoleServiceImpl.checkRoleDataScope`，确认用户侧授权写接口没有超级管理员角色保护，已新增 P2-117。本轮未调用任何授权写接口。
- 2026-06-24 继续只读复核角色分配候选列表字段，使用 admin token 请求 `/system/role/authUser/allocatedList?roleId=103` 和 `/system/role/authUser/unallocatedList?roleId=103`，确认已分配 12 人均为 STORE，但响应 `dept.deptName/deptType` 全为空；候选 13 人真实分布为 `GROUP=1/COMPANY=1/WAREHOUSE=7/STORE=3/NO_DEPT=1`，接口返回完整邮箱/手机号但不返回部门名称、组织类型、现有角色或店铺授权摘要。使用 MySQL 对照 `sys_user/sys_dept/sys_user_role/sys_user_shop/sys_role`，并检查 `role/selectUser.vue`、`role/authUser.vue`、`SysUserMapper.selectAllocatedList/selectUnallocatedList` 和 `SysRoleServiceImpl.checkAuthUserScope`，已补强 P2-36。
- 2026-06-24 继续只读复核用户和角色状态开关权限，检查用户管理、角色管理前端 `el-switch`、`handleStatusChange`、`changeRoleStatus`、`SysUserController.changeStatus`、`SysRoleController.changeStatus` 和当前 `system:user:list/edit`、`system:role:list/edit` 授权，确认前端状态开关未按编辑权限隐藏或禁用，已新增 P3-104。
- 2026-06-23 使用浏览器复测 `/system/salary`、`/system/salary?probe=1`、`/system/salary?roleId=100`、`/oa/salary`、员工绑定 tab、员工绑定新增弹窗、工资参数弹窗和工资计算确认框；使用 MySQL 比对 `sys_salary_scheme`、`sys_salary_scheme_item`、`sys_role_salary_scheme`、`sys_user_salary_scheme`、`oa_salary_config`、`oa_salary_record`；检查薪资配置前端、角色页跳转、薪资 API、`SysSalaryConfigController`、`OaSalaryServiceImpl` 和 `OaSalaryEmployeeMapper.xml`。
- 2026-06-23 使用 MySQL 盘点 `*_clear_backup_202606*` 表、`inv_number_sequence`、`inv_document_status_log` 和当前正式库存单据表数量；检查 `sys_menu` 中 `inv:audit:list` 权限、角色授权、`InvDocumentStatusLogController`、`InvDocumentStatusLogServiceImpl`、`InvDocumentStatusLogMapper.xml`，并用 `rg` 确认电脑端没有对应审计页面/API 调用。
- 2026-06-23 使用浏览器复测 `/oa/labor-contract`、`/oa/labor-contract?probe=1`、`/oa/salary`、`/oa/index` 的渲染状态和控制台日志；使用 MySQL 比对 `oa_labor_contract`、`oa_labor_contract_event`、`oa_labor_contract_template`、`oa_company_seal_config`、劳动合同菜单权限和角色授权；检查劳动合同前端、API、`OaLaborContractController`、`OaLaborContractServiceImpl`、`OaLaborContractDocumentService`、合同/事件/模板/企业章 mapper 和重复备份文件。
- 2026-06-23 使用浏览器复测固定资产正确路由 `/oa/fixed-asset/config`、`/oa/fixed-asset/repair` 以及旧 `/oa/fixedAsset/*` 的 404 状态；早前曾误以非运行态 `BossERP` 口径判断固定资产缺表缺菜单，后续运行态 `BossERP_stock_state_75c59ee` 已确认 `oa_fixed_asset%` 四表和 8 个固定资产菜单/按钮存在但均为空且只授权 admin。进一步打开固定资产新增、异常批准和维修上报弹窗，确认零额度/无配置资产时仍暴露写入口。
- 2026-06-24 继续细化固定资产维修上报状态流程，使用 MySQL 复核 `oa_fixed_asset_repair` 表结构和当前 0 行状态分布；检查维修上报页面状态筛选、详情标签、导出状态转换、`OaFixedAssetRepairController`、`OaFixedAssetServiceImpl` 和维修 mapper，确认当前只有 `submit/confirm` 写入口，没有草稿、驳回、取消或撤回流程，已新增 P3-97。
- 2026-06-24 继续只读复核固定资产配置唯一性，使用 MySQL 对照运行态 `oa_fixed_asset_config` 表结构和索引；检查固定资产配置前端保存逻辑、`OaFixedAssetServiceImpl.saveConfig/rebuildQuota`、`OaFixedAssetConfigMapper.sumAssetAmountByShop` 和 SQL 建表脚本，确认同店铺同商品可重复配置且额度会按重复配置求和，已新增 P3-105。
- 2026-06-24 继续细化 OA 工资详情权限，使用 MySQL 复核 `oa:salary:*` 菜单授权、店长角色有效账号和 `oa_salary_record` 当前 0 行状态；检查工资电脑端列表、移动端列表/详情、工资 API、`OaSalaryController.detail`、`OaSalaryServiceImpl.assertAndGetScopedSalary`、工资 mapper 和实体字段，确认详情接口只按店铺范围放行而不校验本人或管理列表权限，已新增 P2-104。
- 2026-06-24 继续细化 OA 工资状态闭环，使用 `rg` 检索 `draft/confirmed/updateOaSalaryRecord` 和工资状态字段；使用 MySQL 复核 `oa_salary_record.status` 默认 `draft` 且当前记录 0 行；检查工资页面、工资 API、`OaSalaryController`、`OaSalaryServiceImpl.calculateSalary`、工资 mapper 和 `OaSalaryRecord` Excel 注解，确认当前没有工资确认、调整、锁定或撤销确认入口，已新增 P3-98。
- 2026-06-24 继续只读复核薪资配置档位删除依赖，使用 MySQL 对照 `sys_salary_scheme_item`、`sys_user_salary_scheme`、`sys_role_salary_scheme` 现有绑定和表结构；检查薪资配置前端删除按钮、`SysSalaryConfigController.removeItem`、`SysSalaryConfigServiceImpl.deleteSalarySchemeItemById`、`SysSalarySchemeItemMapper.deleteSalarySchemeItemById`、`OaSalaryEmployeeMapper` 和工资计算服务，确认删除档位不检查员工/角色引用，已新增 P2-107。
- 2026-06-24 继续只读复核个人中心基本资料保存链路，检查 `profile/userInfo.vue`、个人资料 API、`SysProfileController.updateProfile`、`SysUserServiceImpl.updateUserProfile` 和 `SysUserMapper.updateUser`，确认后端保存昵称/性别但前端成功态只同步手机号/邮箱，已新增 P3-103。
- 2026-06-23 重新启动 `erp-ui` 前端 dev server 到 `localhost:1026` 后，使用 admin token 和 sessionStorage 分别设置 `108 / 市场部门 / STORE`、`104 / 仓库 / WAREHOUSE` 上下文，复测 `/inventory/sales`、`/inventory/customer`、`/inventory/salesReturn`、`/inventory/deliveryNotice`、`/inventory/report`、`/inventory/transfer`、`/cangku/transfer`、`/inventory/transfer-records`、`/cangku/stock`、`/system/transfer-rules` 的渲染、按钮和空态/列表数据。
- 2026-06-23 使用浏览器从 `/redirect/oa/attendance` 进入 `/oa/attendance`，复测仓库上下文下的“上班打卡”、“我的/全部”视图和“导出”入口；使用 MySQL 比对 `oa_attendance_record`、`oa_salary_config`、`sys_menu`、`sys_role_menu`、`sys_user_role`、`sys_user_shop` 和 `sys_dept`；检查考勤前端、API、`OaAttendanceController`、`OaAttendanceServiceImpl`、考勤 mapper 和组织范围服务。
- 2026-06-23 使用浏览器从 `/redirect/inventory/report` 进入报表中心，读取汇总卡片、筛选项和低库存表头；使用 MySQL 比对 `inv_stock`、`inv_product.safety_stock_min`、销售/采购/客户/发货通知表数量、报表菜单权限和成本权限；使用 `qa144822m` 登录后只读调用 `/inventory/report/summary`、`/inventory/report/stock-warning`，验证非成本权限 summary 返回成本字段及日期筛选不影响库存指标；检查 `InvReportController`、`InvReportServiceImpl`、`InvReportMapper.xml`、`InvReportSummary` 和报表前端。
- 2026-06-23 继续使用 MySQL 复核 `sys_dict_type/sys_dict_data`、`oa_purchase` 当前状态分布和业务 `status` 列；检查 OA 采购申请、待办、已办前端状态筛选/标签、`OaPurchaseServiceImpl` 状态常量和 `OaPurchase.java` 导出枚举，确认 `received` 存量状态不在 OA 采购当前页面、导出和服务状态口径中。
- 2026-06-25 重新按当前运行态复核定时任务目标白名单口径，使用 MySQL 确认 `BossERP_stock_state_75c59ee` 中不存在 `sys_job_whitelist` 表、`sys_job=3` 且 3 条 `ryTask.*` 均为停用、`sys_job_log=0`；检查 `SysJobController.add/edit`、`ScheduleUtils.whiteList`、`Constants.JOB_WHITELIST_STR` 和全仓 `sys_job_whitelist|JobWhitelist` 引用，确认目标白名单当前只有代码常量，没有可维护表或电脑端管理入口，已修正 P3-59 的旧表存在口径。
- 2026-06-24 继续使用 MySQL 复核权限变更审计口径：当前运行库 `show tables like '%permission%'`、`show tables like '%change%'` 均无结果，`sys_oper_log` 244 条中只有通用角色/用户/店铺配置操作参数样例；检查 `BusinessType`、`LogAspect`、`SysUserController.insertAuthRole`、`SysRoleController.authUser/*`、`SysUserShopController.save`、`SysUserServiceImpl.insertUserAuth`、`SysRoleServiceImpl.updateRole/insertRoleMenu/deleteAuthUsers/insertAuthUsers` 和 `SysMenuController.edit`，确认权限变更当前只进入通用操作日志，没有专用 before/after 权限变更表或写入链路。
- 2026-06-23 继续使用 MySQL 复核 `sys_auth_provider`、`sys_login_device` 表结构和行数；检查登录页 `loginForm`、登录 API、`TokenController.login/logout`、`SysLoginService.login` 和登录日志写入链路，确认当前只支持本地账号密码验证码登录，没有第三方认证提供方、登录设备记录、设备撤销或设备信任管理入口。
- 2026-06-24 重新复核运行态个人消息口径：MySQL `information_schema.tables` 只返回 `sys_notice/sys_notice_read`，直接 `show columns from sys_notification` 返回表不存在；检查电脑端 HeaderNotice、移动端消息动作、`/system/notice/listTop`、`SysNoticeController` 和 `SysNoticeReadMapper`，确认当前顶部消息和移动端消息动作只围绕全局公告和公告已读表工作，没有个人消息模型。
- 2026-06-24 继续复核顶部菜单搜索：检查 `Navbar`、`HeaderSearch`、`permission.js`、`SysMenuServiceImpl.buildMenus`、`SysMenu` 校验和 `SysMenuController.add/edit`，并用 MySQL 统计当前 `sys_menu=249`、启用 242 条、`menu_name/path` 可疑 HTML 计数为 0、`common/zjl` 持有 `system:menu:add/edit/list/query`；确认搜索高亮用 `v-html` 渲染菜单标题/路径且没有先转义原始文本，已新增 P3-111。
- 2026-06-24 继续复核菜单路由参数：使用 MySQL 确认当前 `sys_menu.query` 非空 2 条且 `invalid_query_count=0`，两条均为库存入口 `stockEntry`；检查菜单表单、`SysMenu`、`SysMenuController.add/edit`、`SysMenuMapper.xml`、`Navbar`、`TopBar`、`SidebarItem.resolvePath` 和 `HeaderSearch.change`，确认保存时没有 JSON 校验，当前顶部菜单/侧边栏/顶部搜索入口直接 `JSON.parse` 且缺少解析失败容错，已修正 P3-112 的运行态组件证据。
- 2026-06-24 继续复核当前顶部菜单和顶部搜索外链打开方式：MySQL `sys_menu` 有 5 条 `http(s)` 外链、其中 4 条启用；检查 `Navbar`、`TopBar`、`SidebarItem`、`Sidebar/Link.vue`、`HeaderSearch` 和 `validate.isHttp/isExternal`，确认当前顶部菜单和侧边栏外链经 `Link.vue` 带 `rel=noopener`，但顶部搜索仍直接 `window.open(..., "_blank")`，已修正 P3-113。
- 2026-06-24 继续复核桌面标签页身份策略：检查 `TagsView/index.vue`、`store/modules/tagsView.js`、`plugins/tab.js` 和设置项，确认新增、选中、更新、删除、关闭左右/其他均主要按 `path` 匹配；同时对照 `/system/shop?userId/userName`、`/user/profile?activeTab`、`/tool/gen?pageNum/t` 和库存 `stockEntry` 等 query 场景，确认不同 query 上下文无法在标签栏中并行保留，已新增 P3-114。
- 2026-06-24 继续复核布局设置抽屉：检查 `Settings/index.vue`、`store/modules/settings.js`、`layout/index.vue`、`Navbar.vue`、`Sidebar/index.vue` 和 `TopBar/index.vue`，确认页面提供左侧/混合/顶部三种导航选项且保存 `navType`，但 store 初始化硬编码 `navType=3`，桌面端布局固定渲染顶部菜单且侧边栏只在移动端渲染，已新增 P3-115。
- 2026-06-24 继续复核店铺授权 query 切换链路：检查用户列表 `handleShopScope`、内容区 `AppMain`、店铺授权页 `created/applyRouteUserQuery/focusRouteUser/submitShopScope`，并对照代码生成页 `activated()` 和库存页 `$route.fullPath` watcher，确认 `/system/shop?userId=...` 在同一路径切换时正文不会自动按新 query 重置，保存仍依赖缓存中的 `currentUser.userId`，已新增 P2-118。
- 2026-06-24 继续复核电脑端导出按钮一致性：检查 `exportConfirm.js`、用户/操作日志/商品等已接入统一确认的页面，以及 OA 工资、考勤、客户、库存、供应商和定时任务等仍使用简短确认或无确认的页面，确认导出按钮提示口径不一致，已新增 P3-116。
- 2026-06-24 继续复核电脑端导入按钮和模板下载闭环：检查用户导入弹窗、通用 `ExcelImportDialog`、用户导入服务、商品导入按钮、用户/商品模板接口和对应权限测试，并用 MySQL 统计当前启用账号中无导入权限的账号数量；确认用户导入结果 HTML 渲染风险和模板下载接口只要求登录的问题，已新增 P2-119、P3-117。本轮未执行任何导入写入。
- 2026-06-23 继续使用 MySQL 复核 `sys_temp_authorization` 表结构和行数；使用 `rg` 检查临时授权/权限委托相关表名、字段名、前后端类名和中文关键词，确认当前没有临时授权管理页、申请审批接口、运行时角色/部门/仓库权限合并或到期回收链路。
- 2026-06-23 继续使用 MySQL 复核 `sys_report` 表结构和行数；使用 `rg` 检查自定义报表表名、字段名、前后端类名和中文关键词，并对照现有库存报表路由、前端 API、`InvReportController` 和 `InvReportMapper.xml`，确认当前只交付固定库存报表，没有报表定义管理、动态执行服务或 `sys_report` 权限闭环。
- 2026-06-23 继续使用 MySQL 复核 `sys_alert_rule` 表结构和行数、系统监控菜单；使用 `rg` 检查告警规则表名、字段名和类名，并对照库存预警与监控页面目录，确认当前没有告警规则管理、指标采集、规则评估、通知发送或告警历史链路。
- 2026-06-23 继续使用 MySQL 复核 `sys_trace_log` 表结构和行数、日志菜单；使用 `rg` 检查 trace 表名、字段名、类名和 tracing 组件关键词，确认当前没有通用 traceId 生成/透传、链路日志写入、链路追踪查询页或跨服务调用链排障闭环。
- 2026-06-23 继续使用 MySQL 复核 `sys_scheduler_lock` 表结构和行数；检查 `SysJobServiceImpl.init`、`ScheduleUtils`、`QuartzDisallowConcurrentExecution` 和被注释的 `ScheduleConfig`，并用 `rg` 检查调度锁/分布式锁关键词，确认当前调度锁表没有运行代码接入，Quartz JDBC 集群 JobStore 也未启用。
- 2026-06-23 继续使用 MySQL 复核 `fin_travel_subsidy` 表结构和行数；使用 `rg` 检查差旅补贴表名、类名和中文关键词，并检索菜单中的差旅/补贴/报销入口，确认当前没有差旅补贴申请、审批、结算或薪资引用链路，现有补贴字段只属于薪资/劳动合同。
- 2026-06-23 继续使用 MySQL 复核 `oa_holiday`、`oa_leave_correction`、`oa_overtime`、`oa_overtime_record` 表结构和行数；使用 `rg` 精确检查表名/类名，检索假期/补卡/加班/请假菜单，并对照 OA 考勤 API、工资 API 和 `OaSalaryServiceImpl.calculateSalary`，确认当前没有假期维护、补卡审批、加班审批或工资计算引用这些扩展表。
- 2026-06-23 继续使用 MySQL 复核 `inv_purchase_contract`、`inv_purchase_order_version` 表结构和行数；使用 `rg` 精确检查采购合同/采购订单版本表名、类名和快照字段，并对照采购页面、采购 API 和采购菜单，确认当前没有合同台账、合同审批、合同附件、采购单版本历史或变更对比链路。
- 2026-06-23 继续使用 MySQL 复核 `inv_sales_quote`、`inv_sales_quote_detail`、`inv_sales_contract`、`inv_shipment`、`inv_receivable`、`inv_payable` 表结构和行数；使用 `rg` 精确检查销售报价/合同/发运/应收应付表名和类名，并检索报价、合同、发运、应收、应付菜单及库存销售页面目录，确认当前没有销售商务或财务结算闭环。
- 2026-06-23 继续使用 MySQL 复核 `inv_supplier_score` 表结构和行数、供应商菜单；使用 `rg` 精确检查供应商评分表名、类名和评分字段，并对照供应商页面、供应商 API、`InvSupplier` 和 `InvSupplierController`，确认当前没有供应商履约评价、自动评分或评分展示链路。
- 2026-06-23 继续使用 MySQL 复核 `inv_location`、`inv_pick_task`、`inv_pick_task_detail`、`inv_qc_hold`、`inv_defective_stock` 表结构和行数；使用 `rg` 精确检查库位、拣货、质检冻结和残次品表名/类名，并对照库存页库位输入、采购质检入口和库存菜单，确认当前没有受控库位主数据、拣货任务、质检冻结台账或残次品处理链路。
- 2026-06-23 继续使用 MySQL 复核 `inv_cost_event` 表结构和行数、`inv_stock_log` 成本字段；使用 `rg` 精确检查成本事件表名、类名和 before/after 成本字段，并对照 `InvStockMapper` 成本更新 SQL 与采购、销售、退货、调拨、盘点、库存调整服务，确认当前没有成本事件写入或成本事件查询闭环。
- 2026-06-23 继续使用 MySQL 复核 `inv_transfer_receipt` 表结构和行数、调拨收货相关菜单；使用 `rg` 精确检查调拨收货单、差异审批字段和中文关键词，并对照调拨收货 API、缺失的 `cross-store-transfer` 前端组件和 `InvTransferServiceImpl.receiveTransferShipment`，确认当前没有调拨收货单、差异收货或差异审批闭环。
- 2026-06-23 继续使用 MySQL 复核 `inv_after_sales_order` 表结构和行数、售后/退货菜单；使用 `rg` 精确检查售后表名、类名和工作流字段，并对照销售退货页面和销售退货 API，确认当前没有售后工单、售后审批或退换补赔闭环。
- 2026-06-23 继续使用 MySQL 复核 `inv_batch` 表结构和行数、批次管理菜单状态和角色授权；使用 `rg` 精确检查批次菜单、权限和表名，并对照库存页、库存调整 DTO 与 `InvStockMapper`，确认当前只有库存字段级批次/效期输入，没有受控批次主数据或批次生命周期台账。
- 2026-06-23 继续使用 MySQL 复核 `inv_purchase_plan`、`inv_purchase_plan_detail`、`inv_receipt`、`inv_receipt_detail` 表结构和行数、采购计划/收货菜单；使用 `rg` 精确检查采购计划、通用收货单/明细表名、权限和类名，并对照采购 API、采购页面、`InvPurchaseController` 与 `InvPurchaseServiceImpl.receivePurchase`，确认当前采购计划和通用收货单没有接入采购主流程。
- 2026-06-23 继续使用 MySQL 复核工作流扩展表 `wf_delegate_rule`、`wf_delegate_log`、`wf_cc_log`、`wf_add_sign_log`、`wf_transfer_log`、`wf_field_permission`、`wf_sla_log` 和工作流菜单按钮；使用 `rg` 精确检查这些扩展表名和类名，并对照 `erp-ui/src/views`、`erp-ui/src/api`、`erp-modules/erp-workflow`、`erp-api/erp-api-workflow`，确认委托、转办、加签、抄送、字段权限和 SLA 处置没有当前工程实现。
- 2026-06-23 继续使用 MySQL 复核当前启用权限码、参数/日志/在线用户菜单和角色授权；使用 `rg` 精确检查 `system:config:query`、`system:operlog:query`、`system:logininfor:query`、`monitor:online:query`、`monitor:online:batchLogout` 与实际 `list/forceLogout` 权限源码，确认查询和批量强退按钮权限在当前工程没有判断点。
- 2026-06-23 继续使用 MySQL 复核 `sys_menu` 中按钮父级和启用权限码重复情况；检查 `4100-4112` 调拨菜单、`sys_role_menu` 授权和调拨页 `v-hasPermi` 使用点，确认 `4109 仓库出库修改` 是挂在 `4107 调拨取消` 按钮下的启用重复 `inv:transfer:edit` 节点。
- 2026-06-23 继续使用 MySQL 复核 `system:userShop:list` 重复菜单节点和角色授权；检查店铺配置页、店铺授权 API 与 `SysUserShopController`，确认 `1192 店铺配置列表` 没有独立前端按钮判断，且与 `181 店铺配置` 复用同一权限码。
- 2026-06-24 继续复核 OA 采购审批工作台，使用 MySQL 核对 `3002-3106` 菜单状态、角色授权、`oa_purchase/oa_purchase_comment` 和 Flowable 运行/历史任务行数；检查 `oa/todo`、`oa/done`、采购申请 API、`OaPurchaseController`、`OaPurchaseServiceImpl` 和采购/审批意见 mapper，确认当前待办/已办已启用给业务角色，但采购申请仍停用隐藏且无运行任务，已修正 P2-78/P2-92。
- 2026-06-24 继续只读复核销售退货和采购退货确认/取消动作，检查两个退货页面行操作、详情弹窗、确认框文案、后端 confirm/remove 接口和服务层库存写入；使用 MySQL 复核正式退货表当前为 0、库存和库存日志已有数据、退货确认/删除权限节点启用，确认当前高风险确认框没有展示单据身份和库存影响摘要，已新增 P3-118。
- 2026-06-24 继续只读复核库存盘点确认动作，检查盘点列表、详情弹窗、确认框文案、`InvStockCheckController.confirm` 和 `InvStockCheckServiceImpl.confirmCheck`；使用 MySQL 复核当前盘点明细样例为账面 5、实盘 1、盘亏 4，确认服务层会按差异写库存和流水，但前端最后确认没有汇总盈亏影响，已新增 P3-119。
- 2026-06-24 继续只读复核采购质检确认动作，检查采购质检弹窗、确认文案、`InvPurchaseController.qualityCheck` 和 `InvPurchaseServiceImpl.qualityCheck`；使用 MySQL 复核正式采购/待检入库当前为 0、清理备份待检/质检记录仍有 3 条样例，确认当前质检确认没有展示待检批次、数量和合格/不合格对库存或收货进度的影响，已新增 P3-120。
- 2026-06-24 同步复核调拨发货/收货和发货通知执行确认文案：调拨确认已展示调拨单号、要货门店、发货仓库、品项数、数量和库存增减方向；发货通知确认已展示通知号、销售单号、客户、发货仓库、发货数量和扣减库存提示。本轮不就这两个确认框另列重复问题，后续只继续检查其权限、状态机和数据闭环。
- 2026-06-24 继续只读复核销售单生成发货通知动作，检查销售列表、详情弹窗、生成发货通知确认框、`InvDeliveryNoticeController.create`、`InvDeliveryNoticeServiceImpl.createNotice` 和销售/发货通知表数量；确认后端会按未出库数量创建通知明细并把销售单改为 `noticed`，但前端确认框只展示销售单号，已新增 P3-121。
- 2026-06-24 继续只读复核销售单取消动作，检查销售列表、详情弹窗、取消确认框、`InvSalesController.cancel`、`InvSalesServiceImpl.cancelSales`、`InvSalesOrder`、销售 mapper、状态审计触发器和运行态表结构；确认当前取消只改状态、没有取消原因字段或请求参数，状态审计只能记录泛化变更，已新增 P3-122。
- 2026-06-24 继续只读复核发货通知部分发货状态机，检查发货通知列表按钮、执行发货弹窗、`canDeliver`、取消按钮条件、`InvDeliveryNoticeServiceImpl.deliverNotice/cancelNotice`、`InvStateGuard` 和发货通知权限节点；确认系统支持 `delivering` 继续发货，但没有关闭或取消剩余未发数量的业务出口，已新增 P3-123。
- 2026-06-24 继续只读复核发货通知出库记录回链，检查 `InvOutboundRecord`、`InvOutboundRecordMapper.xml`、`InvDeliveryNoticeServiceImpl.deliverNotice`、`InvSalesServiceImpl.deliverSales` 和运行态 `inv_outbound_record` 表结构；确认出库记录没有 `notice_id/notice_detail_id` 等发货通知关联键，已新增 P3-124。
- 2026-06-24 继续只读复核销售发货库存流水来源链，检查库存流水页面、`InvStockController.exportLog`、`InvStockLog`、`InvStockLogMapper.xml`、`InvDeliveryNoticeServiceImpl.deliverNotice`、`InvSalesServiceImpl.deliverSales` 和运行态 `inv_stock_log` 表结构；确认销售发货库存流水只记录销售单业务号，无法回到具体发货通知或通知明细，已新增 P3-125。
- 2026-06-24 继续只读复核采购退货出库台账，检查采购退货列表/详情/导出、`InvPurchaseReturnController.export`、`InvPurchaseReturnServiceImpl.getReturnDetail/confirmReturn`、采购退货明细 mapper 和运行态表清单；确认采购退货确认会写库存流水但详情和导出没有出库/库存流水台账，且没有独立采购退货出库记录表，已新增 P3-126。
- 2026-06-24 继续只读复核销售退货入库台账，检查销售退货列表/详情/导出、`InvSalesReturnController.export`、`InvSalesReturnServiceImpl.getReturnDetail/confirmReturn`、销售退货明细 mapper、通用入库记录使用点和运行态表清单；确认销售退货确认会写库存流水但详情和导出没有入库/库存流水台账，且没有独立销售退货入库记录表，已新增 P3-127。
- 2026-06-24 继续只读复核采购质检入库库存流水来源链，检查 `InvInboundRecord`、`InvInboundRecordMapper.xml`、`InvPurchaseServiceImpl.receivePurchase/qualityCheck`、`InvStockLog/InvStockLogMapper.xml` 和运行态 `inv_inbound_record/inv_stock_log` 表结构；确认质检入库库存流水只记录采购单号，无法回到具体待检入库记录，已新增 P3-128。
- 2026-06-24 继续只读复核库存盘点流水来源链，检查 `InvStockCheckDetail`、`InvStockCheckDetailMapper.xml`、`InvStockCheckServiceImpl.confirmCheck`、`InvStockLog/InvStockLogMapper.xml` 和运行态 `inv_stock_check_detail/inv_stock_log` 表结构；确认盘点确认逐明细写流水但不保存盘点明细 ID，已新增 P3-129。
- 2026-06-24 继续只读复核手工库存调整凭证链，检查库存调整弹窗、`InvStockAdjustRequest`、`InvStockServiceImpl.adjustStock` 和运行态 `inv_stock_log` 样例；确认手工调整库存流水 `business_id/business_no` 为空，缺少独立调整单号和结构化调整凭证，已新增 P3-130。
- 2026-06-24 继续只读复核调拨库存流水来源链，检查 `InvTransferServiceImpl.deliverTransfer/receiveTransferShipment/addTargetStockAndLog/writeStockLog`、`InvTransferShipmentDetailMapper.xml`、`InvStockLogMapper.xml` 和运行态 `inv_transfer_shipment_detail/inv_stock_log` 样例；确认调拨出入库库存流水只记录调拨单号，无法回到具体 `TS` 发货批次和批次明细，已新增 P3-131。
- 2026-06-24 继续只读复核报表中心使用闭环，检查报表页面按钮、报表 API、`InvReportController`、`sys_menu` 报表权限和运行态库存/流水数据；确认报表中心没有导出或明细钻取，汇总指标无法自助核对，已新增 P3-132。
- 2026-06-24 继续只读复核单据状态审计覆盖范围，检查 `InvDocumentStatusLogController/Service/Mapper`、`InvTransferStatusLogMapper`、`InvTransferServiceImpl.writeStatusLog`、`InvTransferApprovalServiceImpl.writeStatusLog`、`InvSalesServiceImpl.writeTransferStatusLog`、`sys_menu` 权限和运行态 `inv_document_status_log/inv_transfer_status_log` 样例；确认通用状态审计接口不包含调拨状态流水，已新增 P3-133。
- 2026-06-24 继续只读复核采购退货和销售退货取消分支，检查两个退货页面 `handleCancel`、API DELETE 调用、`InvPurchaseReturnController.remove`、`InvSalesReturnController.remove`、两个服务层 `cancelReturn`、退货实体/mapper、运行态退货表结构和退货状态触发器；确认退货取消没有取消原因、取消时间和独立状态事件，已新增 P3-134。
- 2026-06-24 继续只读复核采购退货和销售退货备注字段闭环，检查两个退货表单 `remark`、详情弹窗、`getReturnDetail` 服务、退货 mapper、导出 Controller、实体 `@Excel` 注解和运行态正式/备份退货表备注样例；当日先确认备注可录入和返回但详情、导出不可见，后续 2026-06-25 复核又补充确认草稿编辑更新 SQL 不写 `remark`，P3-135 已按完整保存闭环修正。
- 2026-06-24 继续只读复核库存流水列表商品信息来源，检查 `InvStockLogMapper.xml` 联表字段、`InvStockLog` 实体、库存流水前端 `getList/hydrateProducts/productName/productCode`、商品详情权限和运行态库存流水样例；确认库存流水接口已返回商品编码/名称但前端仍逐商品请求详情，已新增 P3-136。
- 2026-06-24 继续只读复核库存流水批次/效期/序列号/库位字段闭环，检查库存流水筛选和列、`InvStockLogMapper.xml` 条件、`InvStockServiceImpl.applyStockLogMetadata`、采购/销售/退货/调拨/盘点写流水路径和运行态 `inv_stock_log` 样例；确认多数业务流水不会写入这些筛选字段，已新增 P3-137。
- 2026-06-24 继续只读复核库存主页面当前/锁定/可用库存口径，检查库存列表和详情列、库存状态计算、库存调整预览、`InvStockServiceImpl.adjustStock` 可用库存扣减校验、`InvStock` 导出注解和运行态 `inv_stock` 样例；确认库存状态和扣减校验按可用库存计算，但页面行级展示和调整确认不展示可用/锁定数量，已新增 P3-138。
- 2026-06-24 继续只读复核固定资产年度申报比例和额度流水闭环，检查固定资产配置页额度卡片/新增编辑弹窗、`OaFixedAssetConfig`、`OaFixedAssetConfigMapper.xml`、`OaFixedAssetQuota/QuotaLedger` 表结构、`OaFixedAssetServiceImpl.saveConfig/getQuotaSummary/insertLedger`、两个固定资产 Controller 和前端维修页；确认年度申报比例实际是店铺年度额度字段却藏在资产明细保存表单中，且额度流水只用于汇总计算，没有电脑端明细查询或导出，已新增 P3-139、P3-140。
- 2026-06-24 继续只读复核客户删除引用检查，检查客户管理前端删除确认、`InvCustomerServiceImpl.deleteCustomerByIds`、`InvSalesOrderMapper.countByCustomerNameAndShop`、客户/销售单实体和运行态 `inv_customer/inv_sales_order` 表结构；确认删除保护按客户名称+门店匹配销售、退货和发货通知历史，而不是按稳定客户 ID，已新增 P3-141。
- 2026-06-24 继续只读复核供应商状态治理，检查供应商页面状态/合作状态变更、商品页供应商选项加载、`InvProductServiceImpl.applySupplierFromCatalog`、采购商品选择器、`InvPurchaseServiceImpl.applyCatalogProductsForDraft` 和运行态供应商/采购表计数；确认商品维护会校验正常合作供应商，但采购制单保存/提交不再回查供应商当前状态，已新增 P2-120。
- 2026-06-24 继续细化商品分类和商品导入组织口径，使用 MySQL 复核 `inv_product_category` 全部归属集团 `100`、商品分类引用全部为 `product_shop=100/category_shop=100`；检查分类页、商品页分类下拉、`InvProductCategoryServiceImpl`、`InvProductServiceImpl.assertAndGetProductCategory/resolveCategoryId` 和分类 mapper，确认手工新增可使用相关范围集团分类，但导入只按当前组织匹配分类名并会自动新建本地同名分类，已新增 P2-105。
- 2026-06-24 继续细化采购收货/质检状态机，使用 MySQL 复核 `4025 采购收货`、`4483 采购质检` 已启用并授给 `admin/ck/zjl`，正式 `inv_purchase_order/inv_purchase_detail/inv_inbound_record` 当前均为 0；检查采购页确认文案、`purchaseActionRules.js`、`InvPurchaseServiceImpl.receivePurchase/qualityCheck` 和 `InvInboundRecordMapper.xml`，确认收货只生成待检记录，质检合格或让步接收后才写库存和库存日志，已新增 P2-106。
- 2026-06-23 曾继续使用 MySQL 复核非运行态 `BossERP` 的 `inv_transfer%` 表清单和调拨菜单状态；用源码 SQL 表引用反查该库缺表，并检查 `InvTransferServiceImpl`、调拨审批/发货批次/status log Mapper 和 SQL 迁移脚本，确认该非运行态库缺少调拨审批、发货批次和状态流水必需表。后续运行态库已确认这些表存在且有数据。
- 2026-06-23 继续使用 MySQL 精确复核采购、库存、库存盘点、调拨、销售、劳动合同和考勤核心表计数；确认非运行态 `BossERP` 中采购、库存、库存盘点和调拨数据曾被清空或缺表，但当前运行态 `BossERP_stock_state_75c59ee` 仍有 `inv_stock=2`、`inv_stock_log=5`、`inv_stock_check=1`、`inv_stock_check_detail=1`、`inv_transfer_order=2`、`inv_transfer_detail=2`、`inv_transfer_status_log=6`，报告中相关结论已按运行态/非运行态口径拆分。
- 2026-06-23 使用 `rg` 反查报告中 `inv_product=162`、`inv_stock=2`、`inv_sales_order=0`、`oa_labor_contract=9` 等运行态数量，并继续用 MySQL 复核商品/分类/供应商组织分布、销售单状态、发货通知和劳动合同相关表；后续已确认 `inv_product=8`、`inv_sales_order=9`、`oa_labor_contract=0` 等是非运行态 `BossERP` 口径，不能作为当前网页端运行态结论。
- 2026-06-23 继续使用 `rg` 反查把非运行态空授权、低账号数、无角色样本、库存清空、合同清空、盘点清空误写成当前运行态的残留表述，并按运行态 `BossERP_stock_state_75c59ee` 与非运行态 `BossERP` 分别修正。运行态当前仍以 `sys_user=25`、`sys_user_shop=43`、`inv_stock=2`、`inv_stock_check=1`、`oa_labor_contract=9` 为准；非运行态空授权和窄表/清空数据只作为环境漂移取证。
- 2026-06-25 重新使用 MySQL 精确复核工作流表、考勤原始数据、同步日志、Excel 导入和班次相关表；当前运行态没有 `oa_shift`、钉钉原始表、Excel 导入表或用户映射表。使用 `rg` 检查班次/排班关键字和 `sys_menu` 班次入口，确认班次模型只存在于旧/非运行态口径，当前电脑端没有班次维护、排班、后端实体/API 或考勤工资引用闭环。
- 2026-06-23 继续使用 MySQL `show tables` 与报告全文做表名覆盖对照，针对未命中的 Activiti/Flowable 内部表、Quartz 明细表、`undo_log` 和 `*_clear_backup_20260607_1630` 备份表执行精确 `count(*)`，确认这些剩余未覆盖表当前均为 0 行，并已补充到数据库概览。
- 2026-06-23 继续抽取源码中的 `v-hasPermi`、`hasPermi` 和 `@RequiresPermissions` 权限字符串，与当前启用 `sys_menu.perms` 做差集；重点复核 `inv:deliveryNotice:add/deliver`、`inv:sales:deliver`、`inv:sales:notice` 的菜单状态和角色授权。2026-06-24 再次复核当前运行态时，`inv:sales:notice` 已不在权限树，`inv:sales:deliver` 仍为停用隐藏旧权限，仓库角色仍无发货通知权限。
- 2026-06-23 继续复核考勤权限差集，检查 `oa:attendance:my`、`oa:attendance:correct`、`oa:attendance:list/query/export` 的菜单状态、角色授权、前端考勤页面和 `OaAttendanceController`，确认个人考勤接口仅要求登录，全部视图仍由 `oa:attendance:list` 控制，`oa:attendance:my/correct` 当前没有前后端判断点。
- 2026-06-23 曾继续复核直查 `BossERP` 的用户、角色、组织授权和组织树，确认该非运行态 schema 中 `sys_user=8`、有效未删除账号 7、删除账号 1、`sys_user_shop=0`、有效组织 `GROUP=1/COMPANY=2/STORE=7/WAREHOUSE=2`，且仓库节点为 `200 / 123` 和 `201 / 仓库`；后续已确认该 schema 不是当前网页端运行态数据源。
- 2026-06-23 继续复核运行态认证链路，先用 `curl` 确认 `POST http://127.0.0.1:9200/login` 对 `admin/admin123` 返回 token、对 `ry/123456` 返回“用户不存在/密码错误”；再调用 `9201 /user/info/admin` 和 `/user/info/ry` 的内部接口，对比 MySQL `BossERP.sys_user/sys_user_role`，确认运行态 system 服务中的 admin/ry 部门、昵称、角色和密码哈希均与直查 `BossERP` 不一致。
- 2026-06-23 继续检查运行配置和候选 schema，确认 `erp-system` 的 `bootstrap.yml/application-dev.yml/target/classes` 均指向 `BossERP_stock_state_75c59ee`；直查该运行态库得到 `sys_user=25`、有效账号 25、`sys_user_shop=43`、授权类型 STORE 34/WAREHOUSE 9、`inv_product=162`、`inv_stock=2`、`oa_labor_contract=9`，并且 `admin/ry` 的部门、角色和密码哈希与运行态 `/user/info` 返回一致。
- 2026-06-23 本轮继续用 MySQL 复核运行态 `BossERP_stock_state_75c59ee`：确认 `inv_product` 商品宽表字段存在且样例查询成功，`inv_sales_order` 有 `customer_name/shop_dept_id/status`，`inv_stock` 有 `warehouse_id/batch_no/version` 且 2 条库存，`inv_stock_log` 有 5 条流水，`inv_transfer_approval_rule=1`、`inv_transfer_shipment=2`、`inv_transfer_status_log=6`，`sys_user_salary_scheme=1`、`oa_salary_config=1`、`oa_labor_contract=9`、`oa_labor_contract_event=59`。已将 P1-16 到 P1-22 中由非运行态 `BossERP` 造成的缺表/缺列/清空误判改为环境漂移风险。
- 2026-06-25 继续只读复核单号序列、销售发货通知和自动跨店调拨链路，检查 `inv_number_sequence`、当前正式销售/发货通知/调拨样例、`InvDeliveryNoticeServiceImpl`、`InvTransferServiceImpl`、发货通知页面和 `WarehouseSelect`。确认单号规则/序列追溯风险已由 P2-84/P3-30 覆盖；另发现跨店销售发货通知要求 `WAREHOUSE` 来源但自动生成 `cross_store` 调拨又要求来源为 `STORE`，已新增 P2-164。
- 2026-06-25 继续只读复核尾部覆盖差集，使用当前运行态 `sys_menu`、67 张非空表、93 个桌面/移动 Vue 视图和报告全文做交叉命中；重点复查“进销存管理/仓库管理”重复挂载的库存/调拨/调拨记录、Sentinel/Nacos/Admin/Swagger 外链、固定资产配置与维修、OA 待办/已办、`inv_number_sequence` 当前 11 条序列及正式单据号样例。确认这些候选风险分别已由 P2-10、P2-48 到 P2-51、P2-92、P2-96、P2-162、P3-30、P3-80、P3-165、P3-198 等既有编号覆盖；本轮不重复新增问题。
- 2026-06-25 继续只读复核岗位管理排序闭环，检查 `system/post` 前端列表和表单、`SysPostController.list/export/optionselect`、`SysPostMapper.selectPostList/selectPostAll` 以及运行态 `sys_post=10/sys_user_post=18` 样本；确认岗位页面展示并维护 `post_sort`，但列表 SQL 没有稳定按 `post_sort` 返回，已新增 P3-260。
- 2026-06-25 继续只读复核进销存筛选区一致性，检查客户、供应商、销售、采购退货、销售退货、分类、商品、库存、库存盘点、调拨、发货通知和报表页面的搜索/重置实现，并用 MySQL 对照当前 `inv_customer=0`、`inv_supplier=17`、`inv_sales_order=0`。确认多个主数据/单据列表有多条件筛选但缺少一键重置，已新增 P3-261。
- 使用源码定位关键问题的字段、权限判断和跳转逻辑。
