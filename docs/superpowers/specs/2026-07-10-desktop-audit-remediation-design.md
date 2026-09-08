# 电脑端审计问题分批整改设计

## 背景

本设计承接以下既有审计与设计，不重新发明已经确认的业务方向：

- `docs/desktop-multi-account-store-audit-20260622.md`
- `docs/desktop-incremental-audit-20260710.md`
- `docs/superpowers/specs/2026-07-08-hr-personnel-management-design.md`
- `docs/superpowers/specs/2026-07-09-hr-personnel-workbench-optimization-design.md`

2026-07-10 电脑端增量审计确认了四类问题：

1. 安全边界仍有危险 HTML 渲染、JavaScript 可读令牌和安全响应头缺口。
2. 人事搜索采用服务端分页后再过滤当前页，数量、分页和实际结果不一致。
3. 仓库深链缺少统一组织上下文守卫，用户会停留在无法继续的业务页。
4. 入职管理、公共弹窗、桌面适配和时间展示仍有流程与体验缺口。

既有人事设计已经明确要求手工新增待入职人员、Excel 导入、确认入职和取消入职；当前代码只交付了共享列表和部分档案维护。因此入职问题是既定方案未落完，不另起一套候选人系统。

## 目标

- 先关闭能扩大管理员会话风险的 XSS 渲染链路。
- 让 HR 搜索、任务队列、页头数量和分页使用同一个服务端结果集。
- 让所有需要业务组织的电脑端深链在进入页面前获得正确上下文。
- 建立可复用的桌面弹窗焦点、错误提示和响应式尺寸规范。
- 完成“新增待入职档案 → 补资料 → 确认/取消 → 绑定账号”的入职闭环。
- 将电脑端会话逐步迁移到 HttpOnly Cookie，并同时落地 CSRF 与 CSP。
- 每个发布批次可独立验证、发布、观察和回滚。

## 非目标

- 本轮不升级 Vue 2 到 Vue 3。
- 本轮不重做所有系统管理页面和所有历史弹窗，只先覆盖审计确认的高频业务弹窗并提供迁移规范。
- 本轮不重构整个用户、角色和组织权限模型。
- 本轮不改变手机端业务交互设计，但所有公共认证和请求层改动必须通过 Android、iOS 和手机 Web 冒烟。
- 本轮不自动给新员工授予高权限角色。
- 本轮不使用正则表达式自制 HTML sanitizer。

## 方案选择

采用“分批完整整改”方案，分成四个可独立发布的批次：

| 批次 | 主目标 | 可独立回滚 |
| --- | --- | --- |
| R1 | XSS 止血、HR 准确分页、组织守卫 | 是 |
| R2 | 公共弹窗、采购桌面适配、时间一致性 | 是 |
| R3 | 入职管理完整闭环 | 是，数据库迁移只做向前兼容字段 |
| R4 | HttpOnly Cookie、CSRF、CSP 和安全响应头 | 是，通过双通道认证和配置开关回退 |

不采用单一大版本，避免安全、HR 数据模型、公共交互和认证同时变化导致回归范围不可控。

## 总体架构

整改后形成六个边界明确的能力单元：

1. `安全 HTML 边界`：后端保存前白名单净化，前端渲染前再次净化，普通文本不进入 HTML 解析器。
2. `HR 查询边界`：HR 专用 Query/Mapper/DTO 负责筛选、分页和统计，前端不再改变结果数量。
3. `组织上下文策略`：路由需求配置决定页面允许的组织类型，选择组织页负责修复上下文。
4. `桌面弹窗规范`：统一焦点栈、焦点限制、错误字段关联、尺寸和滚动行为。
5. `档案优先入职模型`：未入职人员先保存档案，确认入职后再创建或绑定系统账号。
6. `浏览器会话边界`：网关统一读取 HttpOnly Cookie、验证 CSRF，并向内部服务补齐原有认证头。

这些单元不相互绕过：页面不能自行处理 HTML 白名单、不能在当前页过滤服务端分页结果、不能自行判断组织类型、不能自行保存长期令牌。

## R1：安全止血、准确分页与组织守卫

### 1. 公告富文本安全边界

#### 后端净化

在 `erp-common-core` 建立唯一 HTML 净化入口：

- 建议文件：`erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/html/HtmlSanitizer.java`
- 后端使用 OWASP Java HTML Sanitizer 的 `PolicyFactory` 建立白名单策略；前端使用 DOMPurify 作为渲染前纵深防御。依赖版本由项目 dependency management 和锁文件固定，不使用浮动版本。
- 允许业务确实需要的段落、标题、强调、列表、表格、图片和链接。
- 删除 `script`、`iframe`、`object`、`embed`、SVG 脚本能力、事件属性、危险协议和未知标签。
- 图片只允许相对上传路径或部署配置中的受信任资源域名。
- 链接只允许 `http`、`https`；外链补安全属性。

`SysNoticeServiceImpl.insertNotice` 和 `updateNotice` 在 Mapper 之前调用净化器。数据库只保存净化后的 HTML。不能简单取消 `/system/notice` 的网关排除并依赖通用 `EscapeUtil.clean`，因为通用转义会破坏正常富文本语义。

历史数据先提供只读扫描：输出公告 ID、命中的危险标签/属性和净化前后摘要。扫描不自动覆盖历史公告；管理员确认后再执行批量净化。

#### 前端防御

前端新增唯一 `sanitizeRichHtml()` 工具，公告详情传给 `v-html` 前再次净化。任何新增 `v-html` 必须通过该工具，并由静态测试约束。

前端净化是纵深防御，不代替服务端保存前净化。

#### 允许内容

- 文本、段落、换行、标题。
- 粗体、斜体、下划线。
- 有序和无序列表。
- 基础表格。
- 本系统上传图片。
- 安全 HTTP/HTTPS 链接。

#### 必须删除的内容

- `<script>`、事件属性和 `javascript:` URL。
- iframe、object、embed。
- 能触发脚本的 SVG/MathML 内容。
- 未在图片域名或路径白名单内的资源。
- CSS 表达式、危险样式和未知协议。

### 2. Excel 导入结果改为结构化数据

后端不再拼接包含 Excel 原文的 HTML 字符串。统一返回：

```json
{
  "created": 5,
  "updated": 2,
  "failed": 1,
  "rows": [
    {
      "rowNumber": 8,
      "status": "failed",
      "identifier": "原始账号或工号",
      "message": "用户账号格式不正确"
    }
  ]
}
```

`ExcelImportDialog` 使用普通 Vue 文本节点和表格展示结果，删除 `dangerouslyUseHTMLString`。兼容旧接口期间，旧字符串必须作为纯文本展示，不能重新解释其中的 `<br/>`。

失败清单可复制或下载，但导出内容也是纯文本/结构化数据。

### 3. 菜单搜索安全高亮

`HeaderSearch.highlightText()` 改为返回文本片段数组：

```js
[
  { text: "仓库", matched: true },
  { text: "管理", matched: false }
]
```

模板用 `v-for` 渲染文本节点和高亮 `<span>`，删除菜单标题与路径上的 `v-html`。搜索关键字只参与文本分段，不参与 HTML 拼接。

### 4. HR 专用查询模型

#### 问题边界

当前 `HrEmployeeList.vue` 将服务端返回的当前页 `rows` 再交给 `filteredRows`，但分页仍绑定服务端 `total`。通用 `SysUser` 查询又没有完整承接 `keyword`、`deptKeyword`、完整度和账号状态条件，导致“当前页 0 条、总数仍 152 条”。

#### 新查询对象

新增 `HrProfileQuery`，至少承载：

- `keyword`
- `nickName`
- `employeeNo`
- `phonenumber`
- `deptId`
- `deptKeyword`
- `employeeStatus`
- `employeeCategory`
- `completenessStatus`
- `accountStatus`
- `activeTask`
- 日期范围和分页参数

新增 HR 专用 Mapper 查询，不再把 HR 工作台继续堆到通用 `SysUserMapper.selectUserList`。

#### SQL 规则

- `keyword` 同时匹配姓名、账号、手机号和工号。
- 部门/门店按稳定组织 ID 和授权范围过滤，名称关键字只作为辅助查询。
- 账号状态在 SQL 中区分正常、停用和未绑定。
- 待入职、资料不完整、合同临期、离职账号未停用在服务端形成可分页条件。
- 先应用完整 WHERE 条件，再由 PageHelper 计算 total。
- 列表 DTO 返回页面需要的完整度、风险标签和账号摘要，前端不再重新决定行是否存在。

#### 指标接口

任务卡不再用当前页 10 行计算，新增独立 summary 接口：

```http
GET /system/hr/employee/summary
```

返回当前用户数据范围内的全量指标：

```json
{
  "total": 152,
  "incomplete": 10,
  "onboarding": 0,
  "contractDue": 0,
  "offboardingRisk": 0
}
```

#### 前端规则

- 删除影响结果数量的 `filteredRows`。
- 列表直接渲染接口 `rows`。
- 页头和分页统一使用接口 `total`。
- 搜索、筛选、任务卡变化时回到第 1 页并重新请求。
- 前端只允许改变视觉排序、列显隐和展开状态，不能改变业务结果数量。

### 5. 组织上下文策略

用声明式规则替换零散前缀数组：

```js
const desktopContextRules = [
  { prefix: "/cangku/purchase", allowed: ["WAREHOUSE"] },
  { prefix: "/cangku/purchaseReturn", allowed: ["WAREHOUSE"] },
  { prefix: "/cangku/stock", allowed: ["WAREHOUSE"] },
  { prefix: "/cangku/transfer", allowed: ["STORE", "WAREHOUSE"] },
  { prefix: "/inventory/sales", allowed: ["STORE"] },
  { prefix: "/inventory/stock", allowed: ["STORE"] },
  { prefix: "/cangku/", allowed: ["STORE", "WAREHOUSE"] }
]
```

匹配使用“最长前缀优先”，避免 `/cangku/` 通用规则覆盖采购的仓库专用规则。

处理结果：

- 没有已验证组织：进入 `/select-shop`。
- 组织类型不符合：进入 `/select-shop` 并带 `requiredDeptType`。
- 组织类型符合：进入目标页面。
- 原地址通过 `redirect` 保留，选对组织后返回原流程。

示例：

```text
/select-shop?redirect=/cangku/purchase&requiredDeptType=WAREHOUSE
```

选择组织页显示“采购管理需要选择仓库”，优先展示或过滤仓库节点；门店不能确认进入仓库专用页面。

页面内的 `ensureWarehouseContext`/`ensureStoreContext` 继续保留为二级保护，并提供“立即切换仓库/门店”按钮。路由守卫改善体验，不替代后端的组织与权限校验。

## R2：公共弹窗、桌面适配与时间一致性

### 1. 弹窗焦点能力

建立两个复用单元：

- `erp-ui/src/directive/dialog/focusTrap.js`
- `erp-ui/src/mixins/accessibleDialog.js`

职责：

1. 打开前记录触发元素。
2. `opened` 后聚焦显式指定的首字段、弹窗标题或第一个可操作元素。
3. Tab/Shift+Tab 在最上层弹窗内循环。
4. 嵌套弹窗使用焦点栈，关闭子弹窗后回到父弹窗。
5. 关闭最外层弹窗后回到原触发元素。
6. Esc、关闭图标和取消按钮采用同一关闭路径。
7. 弹窗标题具有稳定 ID，并作为 `aria-labelledby`。

表单错误规则：

- 无效字段设置 `aria-invalid="true"`。
- 错误文本具有稳定 ID。
- 输入控件通过 `aria-describedby` 指向错误文本。
- 校验失败后聚焦第一个错误字段。

第一轮覆盖销售、采购、采购物料选择、调拨详情、发货、收货、库存调整和 HR 详情/编辑。其余历史弹窗按同一接口逐步迁移。

### 2. 弹窗尺寸规范

在全局 Element UI 样式中提供：

- `erp-dialog--sm`
- `erp-dialog--md`
- `erp-dialog--lg`
- `erp-dialog--xl`

宽弹窗保持业务建议宽度，同时设置：

```scss
max-width: calc(100vw - 32px);
max-height: calc(100vh - 32px);
```

弹窗标题和底部操作区保持可见，只让正文滚动。禁止弹窗把整个页面撑出屏幕。

采购明细的操作列固定在右侧；物料编码进入物料名称副标题；规格和单位允许压缩。1280×720 下用户不横向滚动也能删除当前明细、保存草稿和提交。

### 3. 时间格式

建立统一 `formatBusinessTime()`：

- 日期字段只显示 `yyyy-MM-dd`。
- 时间字段显示 `yyyy-MM-dd HH:mm:ss`。
- 后端带时区的 ISO 时间转换为系统配置时区，默认 `Asia/Shanghai`。
- 空值统一显示 `-`。
- 列表、详情和导出采用同一时间语义。

首批覆盖调拨列表/详情/批次、调拨记录、OA 考勤、劳动合同和操作日志。

OA 待办默认隐藏店铺 ID、任务 ID，改为店铺名称、申请人、当前节点和等待时长；内部 ID 保留在详情和复制入口中。

## R3：入职管理闭环

### 1. 档案优先模型

不创建停用系统账号来代表候选人。继续既有“先档案、后账号”方向。

调整 `sys_user_profile`：

- `user_id` 允许为空；既有绑定行保持不变。
- 增加 `employee_name` 和 `phonenumber`，用于未绑定账号阶段。
- 增加 `onboarding_status`。
- 增加 `expected_entry_date`。
- 增加 `onboarding_owner_id/name`。
- 增加 `cancel_reason`。
- 增加 `confirmed_by/time`。
- 为入职状态、预计入职日期和手机号增加查询索引。

MySQL 唯一索引允许多个 `NULL user_id`，既有 `uk_user_profile_user_id` 可保留。迁移脚本从 `sys_user` 回填已有档案的姓名、手机号，不删除或重建既有档案。

数据权威规则：

- 未绑定账号时，档案中的姓名和手机号是权威值。
- 绑定账号后，`sys_user.nick_name/phonenumber` 是登录与通讯权威值；档案保存与账号绑定服务在同一事务内同步快照。
- 列表查询通过 `COALESCE(u.nick_name, p.employee_name)` 和 `COALESCE(u.phonenumber, p.phonenumber)` 返回统一字段。
- 未绑定档案通过 `profile_id` 更新，不能再依赖 `where user_id = ?`。

### 2. 状态机

```text
待填写 -> 待补资料 -> 待确认 -> 已入职
   \--------------------------> 取消入职
```

规则：

- 完整度由后端计算，不能由前端直接提交百分比。
- 资料未达到入职必填口径时不能确认。
- 已入职不能退回待填写。
- 已取消不能直接确认，必须先恢复并记录原因。
- 同一个 `profile_id` 只能成功确认一次。

### 3. 接口

```http
POST /system/hr/onboarding
PUT /system/hr/onboarding/{profileId}
GET /system/hr/onboarding/{profileId}
GET /system/hr/onboarding/list
POST /system/hr/onboarding/{profileId}/confirm
POST /system/hr/onboarding/{profileId}/cancel
POST /system/hr/onboarding/import/preview
POST /system/hr/onboarding/import/confirm
```

确认入职在一个事务内完成：

1. `select ... for update` 锁定档案。
2. 重新计算入职必填完整度。
3. 检查工号、手机号、用户名冲突。
4. 根据 HR 选择绑定已有账号或创建账号。
5. 创建/绑定账号并复制组织、岗位、姓名和手机号。
6. 回填 `user_id`，更新员工状态、入职状态、确认人和确认时间。
7. 写入独立的入职确认审计事件。

创建账号时不能自动授予高权限角色。可授角色必须来自配置的入职角色白名单，并同时校验操作者是否有权分配。没有合适角色或组织范围时，账号保持停用并进入“待分配权限”任务，不能静默创建一个可登录但权限不明确的账号。

### 4. 页面

入职空态提供：

- 新增待入职人员。
- 导入待入职名单。
- 下载/查看入职模板。

新增表单分三步：

1. 基础身份。
2. 组织岗位和预计入职日期。
3. 合同、社保、联系人和资料检查。

列表动作：

- 编辑资料。
- 查看缺失项。
- 确认入职。
- 取消入职。

确认弹窗展示姓名、手机号、工号、组织、岗位、账号处理方式、允许授予的基础角色、资料缺失项和操作影响。取消必须填写原因。

### 5. Excel 两步导入

预检不写业务数据。预检返回：

- 新增数量。
- 更新数量。
- 失败数量。
- 缺失必填项。
- 重复工号/手机号。
- 无法匹配的组织路径。
- 预计创建或更新的档案 ID。

确认接口携带预检批次 ID 和校验摘要；后端重新校验批次未过期、文件摘要未变化、操作者一致后才写入。

## R4：浏览器会话、CSRF 与安全响应头

### 1. 双通道迁移

认证迁移必须独立发布，保留配置开关：

- 桌面 Web 使用 HttpOnly Cookie。
- 原生或遗留客户端在迁移期可继续使用 Authorization Header。
- 网关优先读取 Cookie，其次读取 Header。
- 网关认证成功后向内部服务补齐原有 Authorization Header，避免所有下游服务同批重写。

Web 登录成功后：

- 设置 `ERP-SESSION` HttpOnly Cookie。
- 返回过期时间和用户会话摘要，不向 Web JavaScript 暴露长期 access token。
- Cookie 名为 `ERP-SESSION`，设置 `Path=/`，不设置宽泛 Domain，保持 Host-only。
- 首版固定使用 `SameSite=Lax`；只有完成外部跳转、移动 Web 和企业内嵌入口回归后才单独评估 `Strict`，不在本轮动态切换。
- `Secure` 只在 HTTPS 生产配置启用，本地 HTTP 开发关闭。

### 2. 前端启动与会话探测

删除浏览器依赖 `getToken()` 判断是否登录的逻辑，新增：

```http
GET /auth/session
```

应用启动时只探测一次会话：

- 成功：Vuex 设置 `sessionChecked=true`、`authenticated=true`，继续 `GetInfo`。
- 401：清理前端用户/权限/组织状态并进入登录页。
- 网络失败：显示可重试错误，不把网络失败误判为退出登录。

Vuex 只保存会话状态，不保存真实会话令牌。图片、文件、Excel 和编辑器上传组件停止自行拼接 Authorization Token，统一使用同源 Cookie。

### 3. CSRF

Cookie 认证启用前必须同时完成 CSRF：

- 登录时生成与会话绑定的随机 CSRF Token，并通过 `ERP-XSRF-TOKEN` 非 HttpOnly、`SameSite=Lax` Cookie 交给同源前端读取；它不包含登录身份，泄露后不能单独建立会话。
- 前端可读的 CSRF 值只用于请求校验，不能作为会话令牌。
- POST、PUT、PATCH、DELETE 发送 `X-CSRF-Token`。
- 网关校验请求头、会话绑定值和允许的 Origin。
- Origin 白名单来自部署配置，不能直接信任请求 Host。
- 登录、退出、刷新和文件上传明确各自策略。
- CSRF 失败返回 403 和稳定错误码，不伪装成登录过期。

### 4. 退出与兼容

退出必须：

- 删除 Redis 登录会话。
- 返回过期的 `ERP-SESSION` Cookie。
- 清理 CSRF Cookie/状态。
- 清理前端用户、角色、权限、动态路由和组织上下文。

双通道期间记录 Cookie/Header 使用率。所有受支持客户端迁移完成后再关闭浏览器 Header Token 回退。

### 5. CSP 与响应头

先删除：

- 字符串形式 `setTimeout`。
- 代码生成器的 `eval(item.pattern)`。

然后以 `Content-Security-Policy-Report-Only` 部署，收集关键页面违规；稳定后转强制模式。

首版 Report-Only 策略固定为：

```text
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';
object-src 'none';
base-uri 'self';
frame-ancestors 'none';
img-src 'self' data: blob:;
font-src 'self' data:;
connect-src 'self';
form-action 'self';
```

`style-src 'unsafe-inline'` 是为 Element UI 2 的运行时内联样式保留的临时、已记录例外；`script-src` 不允许 `unsafe-inline` 和 `unsafe-eval`。强制 CSP 上线前根据 Report-Only 数据补充确有需要的受信任图片、文件和 API 域名，禁止使用 `*`。

同时配置：

- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`
- `X-Frame-Options: DENY`，并保留 CSP `frame-ancestors 'none'`

真实生产 CDN、Ingress 或负载均衡可能已有响应头，验收以生产运行态响应为准，避免同一响应头在多层产生冲突。

## 错误处理

### HTML 净化失败

- 保存失败并返回稳定错误码，不能保存未净化原文。
- 日志记录公告 ID、操作者和规则命中摘要，不记录完整敏感正文。

### HR 查询失败

- 页面保留筛选条件并提供重试。
- 不能用旧 total 配新 rows。
- summary 失败不阻断列表，但指标卡显示“暂不可用”，不能回退到当前页计算。

### 组织选择失败

- 获取组织树失败时保留目标 redirect 和 requiredDeptType。
- 重试成功后继续原流程。
- 没有符合类型的授权组织时显示“无可用仓库/门店权限”，提供返回首页和联系管理员说明。

### 入职确认失败

- 冲突、资料缺失和权限不足返回结构化原因。
- 事务失败不留下半创建账号或半绑定档案。
- 重复请求返回原确认结果或稳定的“已确认”状态，不重复创建账号。

### 会话迁移失败

- Cookie 模式通过配置开关回退到 Header 模式。
- 网络故障不自动清空本地业务草稿。
- CSRF 失败和会话失效使用不同错误码和用户提示。

## 测试策略

### R1

- 后端 sanitizer 单元测试覆盖脚本、事件属性、危险协议、SVG、图片域名和正常富文本。
- 公告新增/修改服务测试断言 Mapper 收到净化后的 HTML。
- Excel 导入结果测试断言 Excel 原文只作为数据字段返回。
- 前端测试断言公告详情只有统一净化入口，导入弹窗没有 `dangerouslyUseHTMLString`，菜单搜索没有 `v-html`。
- HR Mapper/Controller 测试覆盖无匹配、匹配位于原第 2 页、组合筛选和数据范围。
- 浏览器断言页头数量、分页 total、接口 total 和可见行一致。
- 路由测试覆盖无组织、错误组织类型、正确组织类型、redirect 恢复和最长前缀优先。

### R2

- 弹窗焦点测试覆盖打开、Tab 循环、嵌套弹窗、Esc、关闭后恢复和首个错误字段。
- 1280×720、1366×768、1440×900、1920×1080 截图回归。
- 时间格式测试覆盖 UTC ISO、本地字符串、日期字段、空值和非法值。

### R3

- 数据迁移测试确认既有档案不丢失、`user_id` 可空、已有唯一约束仍有效。
- 入职服务测试覆盖新增、编辑、资料不完整、重复工号、重复手机号、绑定已有账号、创建账号、重复确认和取消原因。
- 并发测试确认同一 profile 只创建一个账号。
- 导入预检测试确认不写数据，确认接口才写入。
- 权限测试确认 HR 不能授予白名单外或超出自身授权范围的角色。

### R4

- 网关测试覆盖 Cookie、Header、Cookie 优先级、过期会话和内部 Header 补齐。
- CSRF 测试覆盖合法请求、缺头、错误头、错误 Origin、GET 豁免和文件上传。
- 浏览器测试确认 `document.cookie` 读不到会话令牌。
- 登录、刷新、退出、锁屏、图片上传、文件上传、Excel 导入、下载和移动端冒烟。
- CSP Report-Only 观察关键流程无未处理脚本违规后再转强制。

## 发布计划

### R1 发布门槛

- 已确认的恶意 HTML 样例无法生成可执行 DOM。
- HR 无匹配时 rows、total 和页面全部为 0。
- `/cangku/purchase`、`/cangku/purchaseReturn` 无组织时进入正确组织选择流程。

### R2 发布门槛

- 键盘用户可完成销售、采购、调拨和库存调整主流程。
- 1280×720 下关键操作不被裁切。
- 调拨列表和详情时间语义一致。

### R3 发布门槛

- 手工新增和 Excel 导入都能产生未绑定待入职档案。
- 确认入职只能成功一次并完整绑定账号。
- 取消入职保留原因和历史档案。

### R4 发布门槛

- Web JavaScript 无法读取会话令牌。
- 跨站写请求被拒绝，正常同源流程不受影响。
- 关键页面在强制 CSP 下无阻断。
- 受支持的桌面、手机 Web、Android 和 iOS 登录/上传/退出流程通过。

## 观察与回滚

每个批次上线后观察：

- 前端运行时错误率。
- 401、403、CSRF 和组织上下文错误数量。
- HR 查询耗时、慢 SQL 和分页总数异常。
- 公告净化命中数量。
- 入职确认失败原因分布。
- Cookie/Header 认证使用比例和 CSP 报告。

回滚原则：

- R1/R2 可直接回滚应用代码；新增测试和净化扫描不影响数据。
- R3 数据库只增加向前兼容字段并放宽 `user_id` 可空，应用回滚时不删除新增字段；回滚前阻止创建新的未绑定档案。
- R4 保留配置化双通道；Cookie 模式异常时切回 Header，但不能通过关闭 CSRF 或放宽 Origin 作为长期修复。

## 工作量估算

| 模块 | 预计人日 |
| --- | ---: |
| XSS 与导入安全 | 3～4 |
| HR 搜索和分页 | 2～3 |
| 组织守卫与切换 CTA | 1～2 |
| 弹窗与桌面适配 | 4～5 |
| 入职完整流程 | 6～8 |
| Cookie、CSRF、CSP | 6～8 |
| 联调、回归与发布 | 4～6 |
| 合计 | 26～36 |

两名前后端开发并行约 3～4 周；单人顺序实施约 5～7 周。R4 必须作为独立发布批次，不与 R3 数据模型迁移同日上线。

## 设计结论

本轮采用 R1 → R2 → R3 → R4 的分批完整整改方案。R1 先解决安全渲染、HR 结果正确性和组织流程卡点；R2 把高频桌面弹窗和笔记本尺寸变成统一规范；R3 落完既有人事设计中缺失的入职闭环；R4 在兼容手机和遗留客户端的前提下迁移浏览器会话并启用 CSP。每批都有独立验收和回滚边界，不以一次性大版本换取表面上的“全部完成”。
