# 多账号多店铺电脑端深度审计问题汇总（2026-06-20）

## 检查范围

- 前端地址：`http://localhost:1025/`
- 后端网关：`http://localhost:8080`
- 运行库：`BossERP_stock_state_75c59ee`
- 检查方式：真实浏览器矩阵走查 + API 权限/菜单抽取 + 运行库查询 + 前端源码扫描。
- 代码改动：未修改业务代码。本文件为本次新增审计记录。
- 破坏性动作：没有真实提交发货、收货、库存调整、退货确认、盘点确认、工资计算、日志清空、任务执行等动作，只检查到页面、按钮、弹窗、确认文案和源码调用点。

## 本次创建的临时账号

| 账号 | 用户ID | 角色 | 授权组织 | 用途 |
| --- | ---: | --- | --- | --- |
| `qa144822s` | 119 | `dz` 店长 | `103 研发部门` 门店 | 门店单账号 |
| `qa144822w` | 120 | `ck` 仓库管理员 | `104 仓库` | 仓库单账号 |
| `qa144822m` | 121 | `dz` 店长 | `103 研发部门`、`108 市场部门` | 多门店账号 |

## 覆盖摘要

- API 抽取：管理员、门店、仓库、多门店账号的 `getInfo`、`getRouters`、`shop-tree`。
- 浏览器走查：25 个账号/页面组合，覆盖首页、选择组织、商品、库存、销售、销售退货、调拨、发货通知、报表、采购、供应商、采购退货、工资、劳动合同、用户管理、店铺配置、调拨审批配置、操作日志、定时任务。
- 源码扫描：运行库启用的 42 个电脑端菜单组件均存在；扫描按钮、弹窗、确认框、导出、删除/清空等高风险入口。
- 已验证较好的点：仓库账号直达 `/inventory/sales` 会进 404；多门店账号切换 `103` 和 `108` 后库存数据确实按组织变化；页面顶部已有当前组织提示。

## P1 高优先级问题

### P1-01 用户名创建/保存规则与登录规则不一致

现象：运行库里已有 `codex_184407_warehouse`，但用户名 22 位，登录时报“用户名不在指定范围”。同一系统中，用户实体允许 30 位，新增输入框允许 30 位，但登录服务只允许 20 位。

证据：
- `erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java:66`
- `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java:159`
- `erp-ui/src/views/system/user/index.vue:134`
- `erp-ui/src/views/system/user/index.vue:269`

建议：统一用户新增、导入、编辑、注册、登录的用户名长度。已有超过登录上限的账号需要迁移、禁用或提示修复。

### P1-02 门店店长角色权限过宽，页面也暴露高风险按钮

现象：门店账号 `qa144822s` 登录后拥有 `inv:stock:adjust`、`inv:transfer:deliver`、`inv:transfer:receive`、`inv:transfer:approve`、`inv:deliveryNotice:deliver`、`oa:salary:calculate` 等权限。浏览器实测门店库存页显示“库存调整”，工资页显示“计算本月工资”。

影响：门店店长可见或具备超出岗位职责的库存、调拨、薪资能力。即使部分流程有组织上下文校验，权限码本身偏宽，后端接口仍需要更细的角色拆分和责任节点校验。

建议：拆分店长、门店收货、库存调整、调拨审批、仓库发货、薪资计算等角色。普通门店店长默认不应拥有库存调整、调拨出库、调拨审批、工资计算。

### P1-03 门店账号可见采购价、成本价和毛利类数据

现象：门店账号进入商品管理页可看到“采购价”“成本价”；报表中心还能看到“库存成本”“采购金额”“销售金额”“预估毛利”。本次未发现专门的成本查看权限控制。

证据：
- `erp-ui/src/views/inventory/product/index.vue:111`
- `erp-ui/src/views/inventory/product/index.vue:116`
- `erp-ui/src/views/inventory/report/index.vue:47`
- `erp-ui/src/views/inventory/report/index.vue:62`
- `erp-ui/src/views/inventory/report/index.vue:72`

建议：新增成本/毛利查看权限。无权限时隐藏列、卡片和导出字段，或者显示脱敏值。

### P1-04 用户管理“配置状态”运行时失真

现象：浏览器实测管理员进入用户管理，已有角色和店铺授权的用户仍显示“未分配角色”。API `/system/user/list` 返回的行里没有 `setupStatus`、`roleCount`、`shopScopeCount`，前端 fallback 把缺失字段当成 0。

证据：
- 数据库计算：`qa144822s/qa144822w/qa144822m` 都是 `complete`。
- API 行数据：这 3 个用户返回 `roles: []`，且没有 `setupStatus/roleCount/shopScopeCount`。
- 前端 fallback：`erp-ui/src/views/system/user/index.vue:343`
- Mapper 已写字段：`erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:63`

建议：确认运行中的 system 服务是否加载了最新 mapper/domain；接口必须返回配置状态字段。前端不要把缺失字段直接判定为未配置，应显示“状态未知/请刷新”。

### P1-05 采购收货、采购质检、调拨发货、调拨收货缺少提交前二次确认

现象：这些动作会改变库存或推进状态，但提交按钮校验通过后直接调用接口。调拨弹窗展示了明细，但点击“确认发货/确认收货”没有再次汇总单号、组织、商品数量和库存影响。

证据：
- `erp-ui/src/views/inventory/purchase/index.vue:603`
- `erp-ui/src/views/inventory/purchase/index.vue:637`
- `erp-ui/src/views/inventory/purchase/index.vue:653`
- `erp-ui/src/views/inventory/purchase/index.vue:659`
- `erp-ui/src/views/inventory/transfer/index.vue:1000`
- `erp-ui/src/views/inventory/transfer/index.vue:1010`
- `erp-ui/src/views/inventory/transfer/index.vue:1064`
- `erp-ui/src/views/inventory/transfer/index.vue:1078`

建议：所有会写库存的动作统一确认结构：单号、当前组织、目标组织、商品行数、总数量、库存方向、不可撤销/后续影响。

## P2 中优先级问题

### P2-01 退货确认文案仍过粗

采购退货和销售退货已有确认框，但只写“扣减/增加对应商品库存”，没有展示退货单号、原单、仓库/门店、供应商/客户、数量汇总。

证据：
- `erp-ui/src/views/inventory/purchaseReturn/index.vue:359`
- `erp-ui/src/views/inventory/salesReturn/index.vue:361`

### P2-02 初始密码提醒会打断临时账号验收

临时账号首次进入首页、选择组织、库存、工资等页面时会弹“您的密码还是初始密码，请修改密码”。安全策略本身合理，但弹窗只给“取消/确定”，没有解释稍后处理是否安全，也没有在新增用户完成时提示管理员该账号登录后会被提醒。

建议：新增用户成功页显示“首次登录需改密”；弹窗增加“稍后处理”语义，避免审计/培训账号误以为必须立即跳转。

### P2-03 导出确认文案不统一

源码扫描显示多个页面仍使用“确认导出当前查询条件下的数据”类文案，缺少当前组织、时间范围、敏感字段、预计条数。涉及商品、销售、退货、客户、供应商、工资、待办、已办、日志等。

建议：导出统一展示组织范围、筛选条件、敏感字段说明和数据使用提醒。

### P2-04 系统监控/日志/任务等高风险动作确认仍偏模板化

操作日志清空、登录日志清空/解锁、定时任务立即执行/启停仍使用“数据项”“调用目标字符串”等模板语气，用户很难确认后果。

证据：
- `erp-ui/src/views/system/operlog/index.vue:260`
- `erp-ui/src/views/system/operlog/index.vue:269`
- `erp-ui/src/views/system/logininfor/index.vue:224`
- `erp-ui/src/views/monitor/job/index.vue:386`
- `erp-ui/src/views/monitor/job/index.vue:396`

建议：清空类要求输入“清空”；定时任务执行前展示任务名、调用目标、执行参数、是否会影响业务数据。

### P2-05 系统工具父菜单隐藏但子菜单启用

运行库中 `系统工具` 父菜单状态/可见性为禁用隐藏，但 `表单构建`、`代码生成`、`系统接口` 子菜单仍启用。入口关系不一致，授权和排查时容易误判。

建议：父子菜单状态保持一致。若工具不用，全部禁用；若要保留，启用父菜单并限制管理员可见。

### P2-06 监控/工具外链硬编码 localhost

运行库菜单中存在 `http://localhost:8718`、`http://localhost:8848/nacos`、`http://localhost:9100/login`、`http://localhost:8080/swagger-ui/index.html`。部署到其他机器时会打开用户自己电脑的 localhost。

建议：改为环境配置或网关相对地址，并按环境和权限显示。

### P2-07 劳动合同员工选择缺少门店/组织信息

劳动合同发起签约会加载用户列表作为员工选项，但员工选择缺少“姓名 + 部门/门店 + 岗位”的强上下文，门店账号容易选错同名员工或跨组织员工。

证据：`erp-ui/src/views/oa/laborContract/index.vue:530`

### P2-08 报表中心查询权限复用页面进入权限

报表页查询按钮仍用 `inv:report:list`，页面进入和数据查询未拆分。后续如果要允许看页面但限制查询范围，会比较难做。

证据：`erp-ui/src/views/inventory/report/index.vue:34`

## P3 低优先级体验问题

1. 门店上下文下左侧把“仓库管理”重命名为“商品资料”，面包屑仍显示“仓库管理/商品管理”，培训和排查时容易混淆。
2. 多个隐藏弹窗在 DOM 中常驻，浏览器/自动化读取按钮时会混入未打开弹窗按钮；对无障碍树和自动化测试不友好。
3. 部分页面空态仍只有“暂无数据”，需要区分无权限、未选组织、筛选为空、接口失败、真实无业务数据。
4. 控制台出现几条泛化 `error` 日志，来源在商品/劳动合同权限或请求失败场景，不利于定位真实错误。

## 2026-06-20 17:05 补充逐页走查

本轮复用上表 3 个 QA 账号，并将其密码统一重置为本机临时测试密码。内置 Browser 插件在本会话因 sandbox metadata 缺字段无法连接，改用 Chrome DevTools Protocol + 网关 API 登录做桌面端真实渲染检查；共覆盖 54 个账号/页面组合，并对商品、采购、库存、调拨、盘点、工资、劳动合同、用户、店铺、日志、定时任务等高风险页面做弹窗/确认文案抽检。未点击最终提交、删除、发货、收货、打卡、签退、工资计算、日志清空等会真实写入的确认按钮。

### P1-06 门店店长还能打开工资参数和劳动合同发起表单

现象：门店账号 `qa144822s` 在 `/oa/salary` 可见“计算本月工资”“工资参数”“导出”；点击“工资参数”能打开包含上班时间、扣款、加班费等全局/组织工资参数的保存表单。`/oa/labor-contract` 页面同时出现“当前操作没有权限”提示，但“发起签约”“验 hash”按钮仍可见并能打开表单。

影响：普通门店角色对薪资规则、合同签署入口的可见性过高，且“无权限提示 + 可打开表单”的组合会让用户误判自己能继续办理合同或工资配置。

证据：
- `erp-ui/src/views/oa/salary/index.vue:16`
- `erp-ui/src/views/oa/salary/index.vue:17`
- `erp-ui/src/views/oa/salary/index.vue:196`
- `erp-ui/src/views/oa/salary/index.vue:204`
- `erp-ui/src/views/oa/salary/index.vue:219`
- `erp-ui/src/views/oa/laborContract/index.vue:27`
- `erp-ui/src/views/oa/laborContract/index.vue:28`
- `erp-ui/src/views/oa/laborContract/index.vue:550`

建议：工资计算、工资参数、合同发起、合同模板/企业章拆成 HR/财务/总部专用权限；普通店长默认只看本人或本店只读工资摘要，不显示可写按钮。若接口权限不足，按钮应隐藏或置灰并给出“请联系 HR 开通权限”，不要先弹泛化“当前操作没有权限”再允许开表单。

### P2-09 考勤打卡/签退是直接写入动作，缺少确认和上下文提示

现象：门店账号进入 `/oa/attendance` 直接看到“上班打卡”。源码中 `handleCheckIn` 和 `handleCheckOut` 直接调用接口写入 `WEB` 位置，没有确认当前日期、账号、门店/组织、是否可撤销，也没有二次确认。

影响：误点会生成考勤记录，需要后台修正；对排班、多门店轮岗、代操作场景不友好。

证据：
- `erp-ui/src/views/oa/attendance/index.vue:16`
- `erp-ui/src/views/oa/attendance/index.vue:184`
- `erp-ui/src/views/oa/attendance/index.vue:193`

建议：打卡前展示“账号、当前组织、日期时间、来源 WEB、提交后如需更正请走考勤更正流程”；下班签退同理。若定位/IP/设备信息不可用，应明确显示“无定位，仅记录网页端”。

### P2-10 选择组织页把集团/公司也当成可选上下文

现象：门店账号选择组织页显示“3 个可选”：`ERP科技`、`深圳总公司`、`研发部门`；仓库账号显示 `ERP科技` 和 `仓库`。但库存、采购、调拨实际只接受门店/仓库上下文，选择集团/公司后业务页仍会再要求有效库存组织。

证据：
- `erp-ui/src/utils/shopContext.js:20`
- `erp-ui/src/views/select-shop/index.vue:292`
- `erp-ui/src/views/select-shop/index.vue:302`

建议：对非管理员账号，集团/公司节点只作为路径展示，不计入“可选”数量，也不要允许作为默认进入业务工作台。若确实允许选择公司，应进入只读汇总页，并把进销存按钮置灰说明“需选择具体门店/仓库”。

### P2-11 库存盘点和库存调整仍露出技术 ID

现象：库存盘点列表和详情显示“店铺ID”“仓库ID”；新增盘点弹窗虽显示“库存组织”，但列表/详情仍回到 ID。库存调整弹窗显示“研发部门ID: 103”或“仓库ID: 104”，不是完整的人类可读组织路径。

证据：
- `erp-ui/src/views/inventory/stockCheck/index.vue:40`
- `erp-ui/src/views/inventory/stockCheck/index.vue:41`
- `erp-ui/src/views/inventory/stockCheck/index.vue:139`
- `erp-ui/src/views/inventory/stockCheck/index.vue:140`
- `erp-ui/src/views/inventory/stock/index.vue:237`
- `erp-ui/src/views/inventory/stock/index.vue:239`

建议：统一显示“组织类型 + 组织名称 + 上级路径”，ID 放到 tooltip 或详情技术信息中。盘点详情建议显示“门店：研发部门 / 仓库：仓库”，不要让一线用户靠数字判断库存归属。

### P2-12 新增业务单表单的上下文和后果提示仍不够

现象：采购单、销售单、采购退货、发起要货、库存盘点等新增表单可以打开，但表单内主要是字段录入，没有强提示“当前组织、目标组织、库存影响、审批路径、保存草稿 vs 保存并提交的区别”。发起要货弹窗甚至显示“要货门店 ID 103”，而不是门店名称。

抽检证据：
- 仓库采购：`/cangku/purchase` 打开“新建采购单”，底部只有“保存草稿/保存并提交”。
- 门店销售：`/inventory/sales` 打开“新建销售单”，未解释提交后是否锁库存或生成发货通知。
- 门店调拨：`/inventory/transfer` 打开“发起要货”，显示“要货门店 ID 103”。
- 仓库调拨：`/cangku/transfer` “发货”弹窗展示单号、门店、仓库和数量，但点击“确认发货”前仍建议增加统一不可逆确认摘要。

建议：所有新增/提交类表单顶部加“当前组织/目标组织/单据后果”摘要；底部按钮拆成“保存草稿”和“提交审批/提交并影响库存”，并在最终提交前用同一套二次确认组件展示数量、金额、库存方向和不可撤销说明。

### P2-13 导出和模板下载确认仍不统一

现象：进销存部分页面有确认框，但仍偏模板化，例如“确认导出当前查询条件下的库存数据？”；调拨导出已较好地展示当前组织和数据使用提醒。系统用户导出、定时任务导出则直接下载；商品“下载模板”也直接下载，没有说明模板版本、适用导入类型或字段口径。

证据：
- `erp-ui/src/views/inventory/product/index.vue:859`
- `erp-ui/src/views/inventory/product/index.vue:899`
- `erp-ui/src/views/system/user/index.vue:519`
- `erp-ui/src/views/monitor/job/index.vue:470`

建议：抽一个统一导出/下载确认组件。业务数据导出展示组织范围、筛选条件、敏感字段、预计条数；系统配置导出展示模块名和用途；模板下载展示模板版本、适用场景和是否含示例数据。下载模板可以不用强确认，但应有轻量说明弹层或下拉菜单。

### P2-14 用户导入缺少角色/组织授权闭环提示

现象：用户导入弹窗只提示上传文件、是否更新已存在用户、下载模板。它没有提示导入后仍需配置角色、店铺/仓库授权、初始密码规则，也没有导入完成后的“去配置授权/筛选未配置用户”闭环。

证据：
- `erp-ui/src/views/system/user/index.vue:192`
- `erp-ui/src/views/system/user/index.vue:472`
- `erp-ui/src/views/system/user/index.vue:519`

建议：导入弹窗增加说明：“导入只创建用户基础资料，角色和组织授权需继续配置”；导入成功后展示本次新增/更新/失败数量，并提供“查看未配置用户”“批量配置角色”“批量配置店铺/仓库”。

### P3-05 操作日志详情弹窗存在 Vue prop mutation 警告

现象：打开操作日志“详细”时控制台出现 `Avoid mutating a prop directly`，来源为 `OperlogDetail` 的 `:visible.sync="visible"`。这不会直接阻断用户，但会污染调试台，掩盖真实运行错误。

证据：`erp-ui/src/views/system/operlog/detail.vue:2`

建议：把弹窗内部可见状态改为 computed getter/setter 或本地 `innerVisible`，通过 `update:visible` 向父组件同步。

## 2026-06-20 17:55 补充逐页逐按钮走查

本轮继续复用 `qa144822s`、`qa144822w`、`qa144822m` 三个 QA 账号，并通过管理员接口把这 3 个临时账号重置为本轮临时测试密码。内置 Browser 插件仍因 `codex/sandbox-state-meta` 缺少 `sandboxPolicy` 无法初始化，改用 Playwright + 本机 Chrome 独立临时上下文继续桌面端真实渲染。覆盖 112 个账号/组织/页面组合：管理员系统页、管理员仓库/门店上下文、门店单账号、仓库单账号、多门店账号 `103 研发部门` 与 `108 市场部门`。未执行删除、清空、提交、发货、收货、库存调整、打卡、签退、作废、工资计算等最终写入动作，只打开到弹窗或确认框。

### P2-15 劳动合同页对无权限/异常响应处理不干净，曾直接暴露 SQL 与 mapper 信息

现象：门店账号进入 `/oa/labor-contract` 时，本轮一次浏览器实测在页面正文出现 `当前操作没有权限` 后直接拼出 MySQL 错误、`OaCompanySealConfigMapper.xml`、`select ... limit 1 LIMIT ?` 等后端细节。随后复跑同一账号同一组织未稳定复现 SQL 文本，但门店上下文仍出现 `pageerror: error`，并继续显示 `验 hash`、`发起签约` 等按钮。

影响：普通门店用户可能看到技术栈、SQL 片段和 mapper 路径；即使偶发，也说明劳动合同页对权限不足、模板/企业章加载失败和后端异常没有统一降级。

证据：
- 浏览器实测：`qa144822m` + `103 研发部门` + `/oa/labor-contract` 页面正文出现 `OaCompanySealConfigMapper.xml` 与 `limit 1 LIMIT ?`。
- `erp-ui/src/views/oa/laborContract/index.vue:446` 同时触发 `getList()`、`getTemplates()`、`loadEmployees()`、`loadSchemes()`、`loadSeal()`。
- `erp-ui/src/views/oa/laborContract/index.vue:539`
- `erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaLaborContractController.java:61`
- `erp-modules/erp-oa/src/main/resources/mapper/oa/OaCompanySealConfigMapper.xml:40`

建议：劳动合同页按权限分块加载，门店普通角色没有模板/企业章权限时不要请求相关接口，也不要显示相关 tab/按钮。所有接口失败统一显示“暂无权限查看合同配置，请联系 HR/管理员”，禁止把后端异常原文展示给用户。后端同时检查 `selectActiveSealConfig` 这类单条查询是否会被分页上下文污染，避免出现双重 `LIMIT`。

### P2-16 店铺配置菜单名和实际任务不一致，首次进入缺少操作引导

现象：管理员进入 `/system/shop`，面包屑和菜单是“店铺配置”，但页面实际是“用户列表 + 组织授权”工作台；右侧提示“请选择左侧用户”，左侧第一行用户已可见但没有明确“点击用户行后再勾选组织”的流程提示。点击“详情”打开的是用户详情，不是店铺详情。

影响：管理员容易把该页理解为维护门店资料，而不是给用户配置门店/仓库授权；新手也容易先点“详情”，却不知道真正的授权动作需要选中用户行。

证据：
- `erp-ui/src/views/system/shop/index.vue:1`
- `erp-ui/src/views/system/shop/index.vue:36`
- `erp-ui/src/views/system/shop/index.vue:69`

建议：菜单改名为“店铺授权”或“用户组织授权”。页面顶部增加简短流程状态：`1 选择用户 -> 2 勾选门店/仓库 -> 3 保存授权`；未选用户时右侧置空并说明“请先点击左侧用户行”，详情按钮改为“用户详情”。

### 已有问题的新增证据

- `P1-03` 补充：门店只读账号在 `/cangku/product` 商品列表和商品详情弹窗仍能看到 `采购价`、`成本价`、`采购参考价`、`参考成本价`、供应商等信息。多门店账号在 `103` 和 `108` 两个门店上下文下结果一致。成本权限不仅要控制表格列，也要控制详情抽屉、导出字段和供应商关联信息。
- `P2-11/P2-12` 补充：`/inventory/stockCheck` 列表和详情仍显示 `店铺ID`、`仓库ID`；`/inventory/transfer` 的“发起要货”弹窗在 `103` 和 `108` 都显示 `要货门店 ID xxx`，没有显示门店名称和组织路径。
- `P2-13` 补充：本轮实测发现 `角色管理`、`岗位管理`、`字典管理`、`参数设置`、`操作日志`、`登录日志` 等系统页导出会直接下载文件；商品“下载模板”也直接下载。导出/下载的确认策略仍需要按“业务数据、系统配置、模板文件”统一设计。

## 建议处理顺序

1. 修复用户名规则不一致、用户配置状态失真、成本/毛利泄露。
2. 拆分门店店长角色权限，移除库存调整、调拨出入库/审批、工资计算、工资参数、合同发起等默认权限。
3. 给采购收货/质检、调拨发货/收货、打卡/签退、工资计算等写入动作补统一确认和后果说明。
4. 统一导出、模板下载、删除、清空、任务执行确认文案。
5. 整理系统工具父子菜单、外链配置、空态、组织/岗位上下文展示。
6. 调整选择组织页的可选节点语义，盘点/库存调整/调拨表单全部改为组织名称和业务路径优先，技术 ID 只放辅助信息。
