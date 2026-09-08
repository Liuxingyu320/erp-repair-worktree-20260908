# 多账号多店铺电脑端页面按钮流程审查（2026-06-20）

## 检查结论

本轮按电脑网页端逐页、逐按钮、副流程方式检查了当前 ERP 项目。未修改业务代码；仅重置了 3 个已有 QA 账号的测试密码用于登录验证，未新增业务单据，未点击最终提交、删除、清空、发货、收货、打卡、签退、工资计算、库存调整确认等会真实写入的动作。

运行环境：

- 前端：`http://localhost:1026/`，原计划 `1025` 端口启动，但 Vue CLI 自动使用了 `1026`。
- 后端网关：`http://127.0.0.1:8080`。
- 浏览器：内置 Browser 插件初始化失败，错误为 `codex/sandbox-state-meta: missing field sandboxPolicy`；已降级为 Playwright + Chromium 临时上下文。
- 原始巡检结果：`/tmp/erp_desktop_audit_20260620.json`，共 154 个页面/账号/组织组合。

覆盖账号：

| 账号 | 角色 | 授权组织 | 菜单路由数 | 本轮用途 |
| --- | --- | --- | ---: | --- |
| `admin` | `admin` | 全部 | 42 | 管理员门店/仓库上下文 |
| `qa144822s` | `dz` 店长 | `103 研发部门` | 20 | 单门店账号 |
| `qa144822w` | `ck` 仓库管理员 | `104 仓库` | 10 | 单仓库账号 |
| `qa144822m` | `dz` 店长 | `103 研发部门`、`108 市场部门` | 20 | 多门店切换 |

## 已验证较好的点

1. 单仓库账号只看到仓库管理菜单，未看到门店销售/OA 菜单。
2. 多门店账号切换 `103 研发部门`、`108 市场部门` 后，首页组织上下文会同步变化。
3. 商品管理的采购价、成本价已通过 `inv:cost:view` 控制，普通门店账号本轮未看到采购价/成本价列；对应代码在 `erp-ui/src/views/inventory/product/index.vue:111`、`:116`、`:555`。
4. 库存调整已增加较完整的二次确认摘要，包含商品、组织、调整方向、调整前后数量和不可撤回提示；对应代码在 `erp-ui/src/views/inventory/stock/index.vue:783` 到 `:832`。
5. 用户组织授权页已从“店铺配置”语义改善为“用户组织授权”，并有步骤提示；对应代码在 `erp-ui/src/views/system/shop/index.vue:5` 到 `:78`。

## P1 高优先级问题

### P1-01 店长角色权限过宽，普通门店账号暴露高风险入口

现象：`qa144822s`、`qa144822m` 的角色都是 `dz`，但 API 权限包含 `inv:stock:adjust`、`inv:transfer:deliver`、`inv:transfer:receive`、`inv:transfer:approve`、`inv:deliveryNotice:deliver`、`oa:salary:calculate`、`oa:salary:config`、`oa:laborContract:add`、`oa:laborContract:send`、`oa:laborContract:void` 等。

浏览器实测：

- `/inventory/stock` 显示并可打开“库存调整”弹窗。
- `/oa/salary` 显示“计算本月工资”“工资参数”“导出”，并可打开工资参数表单。
- `/oa/labor-contract` 显示“发起签约”“验 hash”，并可打开合同发起表单。
- `/oa/attendance` 显示“上班打卡”。

代码证据：

- `erp-ui/src/views/inventory/stock/index.vue:122`
- `erp-ui/src/views/oa/salary/index.vue:16` 到 `:18`
- `erp-ui/src/views/oa/laborContract/index.vue:27` 到 `:28`
- `erp-ui/src/views/oa/attendance/index.vue:16` 到 `:23`

建议：拆分店长、门店收货、库存调整、调拨审批、仓库发货、薪资计算、合同管理等角色。普通门店店长默认只保留销售、要货、门店库存查看/盘点等能力；高风险接口后端也要按组织类型和岗位职责二次校验。

### P1-02 门店账号可见工资、合同敏感数据和可写表单

现象：门店账号进入工资页能看到工资字段结构，包括基本工资、迟到扣款、缺勤扣款、加班费、实发工资；合同页能看到工资合计、合同期限、手机号、岗位，并能打开“发起签约”表单，表单中包含身份证号、薪资方案、薪资档位、基本工资、津贴等字段。

影响：即使列表当前为空，页面能力和字段结构已经暴露敏感 HR/薪资信息。一线门店账号容易误以为可以维护全局工资参数或发起劳动合同。

代码证据：

- `erp-ui/src/views/oa/salary/index.vue:24` 到 `:44`
- `erp-ui/src/views/oa/salary/index.vue:55` 到 `:117`
- `erp-ui/src/views/oa/laborContract/index.vue:32` 到 `:60`
- `erp-ui/src/views/oa/laborContract/index.vue:524` 到 `:553`

建议：工资和劳动合同拆为总部 HR/财务权限；门店角色最多查看本人或本店只读摘要。无权限时不要展示可写按钮或敏感字段结构。

### P1-03 报表中心成本/毛利指标未与成本权限保持一致

现象：普通门店账号 `/inventory/report` 未显示商品采购价/成本价列，但仍能看到“库存成本”“采购金额”“销售金额”“预估毛利”等经营指标。

代码证据：

- `erp-ui/src/views/inventory/report/index.vue:47` 到 `:49`
- `erp-ui/src/views/inventory/report/index.vue:62` 到 `:74`
- 查询按钮仅使用 `inv:report:list`：`erp-ui/src/views/inventory/report/index.vue:34`

建议：新增或复用 `inv:cost:view` 类成本查看权限。无成本权限时隐藏库存成本、采购金额、预估毛利和导出中的成本/毛利字段，或改为脱敏摘要。

## P2 中优先级问题

### P2-01 采购收货/质检、调拨发货/收货缺少最终二次确认摘要

现象：库存调整已经有清晰确认框，但采购收货、采购质检、调拨发货、调拨收货仍是校验通过后直接调用接口。

代码证据：

- 采购收货：`erp-ui/src/views/inventory/purchase/index.vue:603` 到 `:637`
- 采购质检：`erp-ui/src/views/inventory/purchase/index.vue:653` 到 `:659`
- 调拨发货：`erp-ui/src/views/inventory/transfer/index.vue:1004` 到 `:1014`
- 调拨收货：`erp-ui/src/views/inventory/transfer/index.vue:1068` 到 `:1082`

建议：复用库存调整的确认模式，展示单号、当前组织、目标组织、商品行数、总数量、库存方向、提交后影响和是否可撤回。

### P2-02 考勤打卡/签退是直接写入动作，缺少上下文确认

现象：`/oa/attendance` 的“上班打卡”“下班签退”直接调用接口，写入 `WEB` 位置，没有确认当前账号、组织、日期时间和后续更正方式。

代码证据：

- `erp-ui/src/views/oa/attendance/index.vue:184` 到 `:207`

建议：打卡/签退前弹轻量确认，显示账号、当前组织、时间、来源 `WEB`；成功后展示“如需更正请走考勤更正流程”。

### P2-03 选择组织页把集团/公司计入“可选”数量

现象：单门店账号选择组织页显示“3 个可选”，包括集团、公司、门店；单仓库账号显示“2 个可选”，包括集团、仓库；多门店账号显示“5 个可选”，包括集团、公司和门店。页面已有说明“公司/组织适合系统管理和汇总查看”，但业务账号仍可能误点非库存组织。

代码证据：

- `erp-ui/src/utils/shopContext.js:20` 到 `:25` 允许 `STORE`、`WAREHOUSE`、`COMPANY`、`GROUP` 都作为 selectable。
- `erp-ui/src/views/select-shop/index.vue:302` 到 `:311` 只要类型 selectable 就保存。

建议：对普通业务账号，把集团/公司作为路径节点展示，不计入“可选”数量；可点击进入时也应进入只读汇总工作台，并禁用进销存写入入口。

### P2-04 组织上下文展示仍有技术 ID 和类型误导

现象：

- 调拨“发起要货”表单显示 `组织ID：103/108`，不是门店名称和路径。
- 库存调整弹窗显示 `研发部门ID: 103`、`市场部门ID: 108`。
- 盘点详情里 `108 市场部门` 被展示为 `仓库：市场部门（ID 108）`，但 API 组织树里 `108` 是 `STORE`。

代码证据：

- 调拨表单：`erp-ui/src/views/inventory/transfer/index.vue:95` 到 `:98`
- 调拨 ID 文案：`erp-ui/src/views/inventory/transfer/index.vue:454` 到 `:458`
- 库存调整组织 ID：`erp-ui/src/views/inventory/stock/index.vue:237` 到 `:240`
- 盘点组织标签：`erp-ui/src/views/inventory/stockCheck/index.vue:138`、`:365` 到 `:367`

建议：统一展示“组织类型 + 组织名称 + 上级路径”，技术 ID 放在 tooltip 或详情的技术信息区。修正 `formatInventoryDeptLabel` 的类型推断，不能因为库存字段存在就把门店标成仓库。

### P2-05 系统清空/任务执行确认仍偏模板化

现象：操作日志、登录日志清空仍用“数据项”模板语气；定时任务启停/立即执行只提示任务名，缺少调用目标、参数和业务影响。

代码证据：

- `erp-ui/src/views/system/operlog/index.vue:259` 到 `:270`
- `erp-ui/src/views/system/logininfor/index.vue:214` 到 `:225`
- `erp-ui/src/views/monitor/job/index.vue:384` 到 `:397`

建议：清空类动作要求输入“清空”；定时任务执行前展示任务名、调用目标、cron、参数、是否影响库存/工资/合同等业务数据。

### P2-06 门店商品页存在 `pageerror: error`

现象：`qa144822s` 和 `qa144822m` 在 `/cangku/product` 页面均出现 `pageerror: error`。页面能正常渲染，但控制台异常会掩盖真实问题，也会影响自动化测试稳定性。

实测路径：

- `qa144822s` + `103 研发部门` + `/cangku/product`
- `qa144822m` + `103 研发部门` + `/cangku/product`
- `qa144822m` + `108 市场部门` + `/cangku/product`

建议：定位该页面未捕获的 Promise reject 或权限失败分支。若是无权限/空数据，应降级为页面提示，不应抛全局 `pageerror`。

## P3 低优先级体验问题

### P3-01 父级 `/system/log` 直达 404

现象：管理员直达 `/system/log` 会进入 404，但子页 `/system/log/operlog`、`/system/log/logininfor` 正常。

建议：父级日志菜单增加默认重定向到操作日志，或保持不可点击并在面包屑中不作为可访问页面。

### P3-02 用户导入缺少导入后授权闭环提示

现象：用户导入弹窗只显示上传、是否更新已有用户、下载模板，没有说明导入后还要配置角色和门店/仓库授权。

代码证据：

- `erp-ui/src/views/system/user/index.vue:192`

建议：导入成功后展示新增/更新/失败数量，并提供“去配置角色”“去配置组织授权”“筛选未配置用户”入口。

## 建议处理顺序

1. 先收窄 `dz` 店长角色权限，隐藏门店账号的库存调整、工资参数、工资计算、合同发起、合同作废、调拨发货/收货等高风险入口。
2. 给报表中心成本/毛利指标补权限控制，并复查导出字段。
3. 给采购收货/质检、调拨发货/收货、考勤打卡/签退补最终确认摘要。
4. 统一组织上下文显示，不再让业务用户看到裸 ID 或错误的门店/仓库类型。
5. 清理 `/cangku/product` 的 `pageerror`，再补一个普通门店账号的页面级回归用例。
6. 最后整理父级菜单重定向、导入后授权闭环、日志/任务确认文案。
