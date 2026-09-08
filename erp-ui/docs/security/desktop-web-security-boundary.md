# 电脑端 Web 安全边界与迁移契约

状态：前端 P1 缓解已实施；下列服务端工作尚未实施，不应宣称为已完成。

## 1. 当前会话边界（SEC-01）

当前登录接口在响应体返回 `access_token`，前端将其保存在 JavaScript 可读取的 Cookie 中，并通过
`Authorization: Bearer ...` 发送。前端已统一设置根路径、`SameSite=Lax`，且仅在 HTTPS 页面设置
`Secure`。这能减少部分误配置和跨站发送，但不能阻止同源 XSS 读取 Token，也不等同于 `HttpOnly`。

前端在后端迁移完成前不得：

- 将普通 Cookie 标注为“HttpOnly”或声称脚本不可读；
- 在未获得服务端确认时切换到 Cookie 鉴权或启用跨域凭据；
- 用隐藏按钮、路由守卫或前端权限数组替代接口授权；
- 虚构 CSRF Token 或仅依靠 `SameSite` 宣称消除 CSRF。

## 2. 目标会话接口契约

后端应提供一个可灰度的 HttpOnly 会话模式，建议契约如下。

### 登录

`POST /auth/login`

- 成功时由服务端设置主机会话 Cookie；生产属性至少为
  `HttpOnly; Secure; SameSite=Lax; Path=/`。若使用 `__Host-` 前缀，不得设置 `Domain`。
- 响应体不再包含可供 JavaScript 持久化的访问 Token。
- 响应可包含非敏感的过期秒数和 `sessionTransport: "http_only_cookie"`，用于前端能力判断。
- 登录、刷新与高风险操作应轮换会话标识，旧标识立即失效。

### 刷新

`POST /auth/refresh`

- 仅依据受保护会话 Cookie 刷新并轮换 Cookie。
- 不在响应体返回 Token。
- 复用、过期或被撤销的刷新会话必须失败关闭，并留下服务端审计记录。

### 登出

`DELETE /auth/logout`（若保留现有方法，也必须保证语义一致）

- 服务端撤销当前会话和关联刷新会话。
- 使用与设置时一致的 `Path`、`Domain`、`SameSite` 属性清除 Cookie。
- 重复登出应幂等，不依赖前端回传明文 Token。

### CSRF 与跨域

- 所有改变状态的方法必须验证可信 `Origin`，并采用服务端签发、服务端验证的 CSRF 机制。
- 如采用 Token 机制，可由 `GET /auth/csrf` 返回短期、会话绑定的非敏感 CSRF Token；前端仅在
  `POST`、`PUT`、`PATCH`、`DELETE` 中通过约定头（例如 `X-CSRF-Token`）回传。
- 同域反向代理优先。若确需跨域 Cookie，后端 CORS 必须精确允许受信 Origin、允许凭据且禁止
  通配 Origin；随后前端才可为该受信 API 启用 `withCredentials`。
- 当前 Bearer Header 模式不应提前增加伪 CSRF 参数。

### 失败语义

- 未认证统一返回 HTTP 401；权限不足统一返回 HTTP 403。
- 不以 HTTP 200 加业务文案表示认证或授权失败。
- 响应和日志不得返回 Token、密码、证件号、银行卡号等敏感原值。

## 3. 灰度与回滚

1. 后端先在功能开关下同时支持旧 Bearer 模式与新 Cookie 模式，并返回明确的
   `sessionTransport` 能力值。
2. 新前端仅在能力值存在时停止持久化响应 Token，改用 Cookie 会话和服务端 CSRF 契约。
3. 观察登录、刷新、登出、并发标签页、401/403、跨域与移动 WebView 指标。
4. 稳定后停止向新客户端返回 Token，最后关闭旧 Bearer 模式。
5. 回滚只能恢复已审计的旧模式，不能同时长期接受两种凭据而无优先级、撤销和审计规则。

## 4. 授权边界（SEC-04）

前端权限指令和路由只负责界面体验，并已改为上下文异常时失败关闭。每个读取、修改、导出、下载、
删除接口仍必须在后端独立校验：

- 登录状态；
- 操作权限；
- 资源归属；
- 部门、门店或签署范围；
- 对象当前状态和版本。

本轮只读核查确认劳动合同、签约包、HR 敏感字段及通用文件控制器已有对应权限、归属或范围检查。
这些检查必须由后端测试持续覆盖，不能因前端已隐藏按钮而移除。

## 5. HR 敏感字段与导出（SEC-05）

前端只读展示已隐藏明确的 Excel 导入技术元数据，同时保留业务备注和编辑能力。后端仍需：

- 将 `businessRemark` 与 `importAuditMetadata` 分库存储或至少在 DTO 层分离；
- 普通列表、详情和普通导出只返回 `businessRemark`；
- 导入人、外部系统标识、源工作表、批次、行号和文件哈希仅进入受控审计查询；
- 敏感字段导出继续使用独立权限、用途说明、审计事件、最小字段集和短期文件；
- 导出文件不得通过永久公开静态 URL 暴露。

在后端完成字段拆分前，普通服务端导出仍可能包含原始 `remark`，这是未消除的后端风险。

## 6. 受保护文件与响应头（SEC-02/SEC-03）

前端已拒绝主动协议、跨域授权请求和伪装文件，但后端仍应：

- 以不可预测资源 ID 的受保护下载接口替代敏感文件的公开路径；
- 每次下载重新校验权限、归属和数据范围；
- 返回准确的 `Content-Type`、安全的 `Content-Disposition`，并设置
  `X-Content-Type-Options: nosniff`；
- 对上传文件校验扩展名、MIME、文件魔数、大小和存储路径，下载时不要信任原始文件名；
- HTML 错误响应不得以 200 或文档 MIME 返回。

## 7. 部署层防护

以下属于网关或部署配置，不在本次前端修改范围：

- 逐步启用基于 nonce/hash 的 `Content-Security-Policy`；当前页面含内联启动脚本，需先迁移再强制；
- 配置 `frame-ancestors`（或兼容的点击劫持防护）；
- 配置 `X-Content-Type-Options: nosniff`、合适的 `Referrer-Policy` 和 HTTPS/HSTS；
- 为静态资源使用完整性、固定来源和最小缓存暴露策略。

部署前必须先在报告模式验证 CSP，不能直接上线一个会阻断登录或运行时脚本的策略。
